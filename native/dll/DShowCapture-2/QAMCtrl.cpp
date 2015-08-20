/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "stdafx.h"
#include <bdatypes.h>
#include "DShowCapture.h"
#include "../../include/guids.h"
#include "../../include/sage_DShowCaptureDevice.h"
#include "DShowUtilities.h"
#include "FilterGraphTools.h"
#include "../../include/iTSSplitter.h"
extern FilterGraphTools graphTools;
#include "QAMCtrl.h"
#include "Channel.h"

TV_TYPE GetTVType( DShowCaptureInfo *pCapInfo );

#ifdef WIN32  
#include <time.h>
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}
#endif

#ifdef REMOVE_LOG	
#define flog(x) {}
#else
#define flog(x)   _flog x
#endif

extern int flog_enabled;

static void _flog( char* logname, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	
	if ( !flog_enabled ) return;

	fp = fopen( logname, "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );
	fprintf( fp, "%02d/%02d/%d %02d:%02d:%02d  ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
	ltm.tm_hour, ltm.tm_min, ltm.tm_sec );  
	va_start(args, cstr);
	vfprintf( fp, cstr, args );
	va_end(args);
	fclose( fp );
}


static void _flog_check()
{
	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
	}
}

#define QAM_NOT_SUPPORT -1
#define QAM_UNINITILIZED  0
#define QAM_CLOSE  1 
#define QAM_READY  2

typedef struct{
	int  state;
	long lPhysical;
	long lMod;
	long lInversion;
} QAM_TUNING_PARM;

typedef struct {
	char TAG[16];                        //common TAG
	int  state;
	void *env;
	void *pCapInfo;
	QAM_TUNING_PARM tuning_data;
	void* pPrivateData;
} QAM_CTRL_HEADER;

typedef enum
{
	FCV1236D_LG3302,
	LGTDVSH002F_LG3302,
	LGTDVSH062F_LG3303
}TunerID_t;

typedef enum
{
	QAM64_MODE	= 0x00,
	QAM256_MODE	= 0x01,
	VSBMODE		= 0x03,
}DTVDemodeMode_t;

static const GUID PROPSETID_NIMTUNER_PROPERTIES = 
{ 0x87f6acbc, 0xbe46, 0x4ea5, { 0x90, 0x12, 0x1d, 0x21, 0x1c, 0x47, 0x3f, 0x71 } };

typedef enum 
{
	INIM_DMODE_TUNER_ID			= 0,		// Get Only
	INIM_DMODE_SWRESET			= 1,		// Set Only
	INIM_DMODE_MODE				= 2,		// Set Only
	INIM_DMODE_SSINVERSION		= 3,		// Set Only
	INIM_DMODE_SNR_REGISTER		= 4			// Get Only

} KSPROPERTY_NIMTUNER_PROPERTIES;
static int GetTunerDeviceID( JNIEnv *env, DShowCaptureInfo* pCapInfo, IBaseFilter* pBDATuner );
HRESULT  tuneATSCQAMChannel( DShowCaptureInfo* pCapInfo, long lPhysicalChannel, unsigned long lFrequency, long lModulation );
HRESULT OnAirQAMTune( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhyCh, long lMode, long lInversion, bool* bLocked );

BOOL FilterProperty( IBaseFilter* pFilter,REFGUID pGuid,int fGetSet,int nIndex, PBYTE pInBuffer, int nInSize, PBYTE pOutBuffer, int nOutSize)
{
	IKsPropertySet* pKPS=NULL;
	DWORD dwSupport;
	BOOL bOk = FALSE;

	if(!pFilter)
	{
		return FALSE;
	}
	if (FAILED(pFilter->QueryInterface(IID_IKsPropertySet, (void**)&pKPS)))
	{
		return bOk;
	}

	if(fGetSet)
	{
		if (pKPS->QuerySupported(pGuid, nIndex, &dwSupport) == S_OK)
		{
			if (dwSupport & KSPROPERTY_SUPPORT_SET)
			{
				if (pKPS->Set(pGuid, nIndex,pInBuffer, nInSize, pOutBuffer, nOutSize) == S_OK)
				{
					bOk = TRUE;
				}
			}
		}
	}
	else
	{
		DWORD dwReturned;

		if (pKPS->QuerySupported(pGuid, nIndex, &dwSupport) == S_OK)
		{
			if (dwSupport & KSPROPERTY_SUPPORT_GET)
			{
				if (pKPS->Get(pGuid, nIndex,pInBuffer, nInSize, pOutBuffer, nOutSize, &dwReturned) == S_OK)
				{
					bOk = TRUE;
				}
			}
		}
	}
	if(pKPS)
	{
		pKPS->Release();
	}
	return bOk;
}


BOOL OnAirQAMTunerInterface( IBaseFilter* pTuner, DWORD fGetSet,DWORD fCommand,DWORD* pdwValue )
{
	if( pTuner )  
	{
		return FilterProperty( pTuner
					,PROPSETID_NIMTUNER_PROPERTIES 
					,fGetSet
					,fCommand
					,(BYTE*)pdwValue
					,sizeof(DWORD)
					,(BYTE*)pdwValue
					,sizeof(DWORD));
	}
	return FALSE;
}

#define ONAIRSOLUTION_MFG   "OnAirSolution"
#define FUSION_MFG			"Dvico"
#define HAUPPAUGE_MFG		"Hauppauge"

const char* OnAirSolutionUSB = "OnAirUSBHDTV";
const char* HauppaugeQAM = "HauppaugeQAM";
const char* BDAQAM = "BDA QAM";

BOOL OnAirQAMSupportCheck( JNIEnv *env, DShowCaptureInfo* pCapInfo, void** pData );
BOOL SearchMatchFilter( JNIEnv *env, CLSID clsid, char* capFiltName, int CapFiltNum, char* foundName, int nameSize  );

static BOOL OnAirQAMSupportCheck( JNIEnv *env, DShowCaptureInfo* pCapInfo, void** pData )
{
	BOOL Supported = FALSE;
	char TunerName[256];
	LPWSTR pTunerDevName=NULL;
	HRESULT hr;

	if ( !SearchMatchFilter( env, AM_KSCATEGORY_TVTUNER, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, TunerName, sizeof(TunerName) ) )
		return FALSE;

	*pData = NULL;
	graphTools.strCopyA2W( pTunerDevName, TunerName, strlen(TunerName)  ) ;
	CComPtr <IBaseFilter> piTVTunerFilter;
	hr = graphTools.AddFilterByDevicePath( pCapInfo->pGraph, &piTVTunerFilter, pTunerDevName, L"Fake Tuner for BDA" );
	if ( hr == S_OK )
	{
		IAMTVTuner *pTvTuner = NULL;	
		DWORD lComData;
		hr = piTVTunerFilter->QueryInterface(IID_IAMTVTuner,(void **)&pTvTuner);
		if ( pTvTuner )
		{
			if ( OnAirQAMTunerInterface( piTVTunerFilter, 0,INIM_DMODE_TUNER_ID,&lComData) )
			{
				Supported = lComData==LGTDVSH062F_LG3303;
				if ( Supported )
				{
					*pData = new char[256];
					strncpy( (char*)*pData, TunerName, 256 );
				}
			}
			SAFE_RELEASE( pTvTuner );
		}
		hr = graphTools.RemoveFilter( pCapInfo->pGraph, piTVTunerFilter );
	}

	SAFE_DELETE( pTunerDevName );
	return Supported;
}

static void OnAirQAMInitTuner( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
		AMTunerModeType newMode;
		TunerInputType  tuneType;
		if ( !stricmp( pCapInfo->TuningMode, "Air" ) )
			tuneType = TunerInputAntenna;
		else
		if ( !stricmp( pCapInfo->TuningMode, "Cable" ) )
			tuneType = TunerInputCable;
		else
			tuneType = TunerInputCable;

		IAMTVTuner* pTunerProps = NULL;
		HRESULT hr = pCapInfo->pTVTuner->QueryInterface(IID_IAMTVTuner, (void**)&pTunerProps);
		if (SUCCEEDED(hr))
		{
			HRESULT ccHr = S_OK;
			QAM_CTRL_HEADER*  pQAMCtrl = (QAM_CTRL_HEADER*)pCapInfo->pQAMCtrlData;
			bool bLocked= false;
			hr = pTunerProps->get_Mode(&newMode);
			newMode = (AMTunerModeType)16;  //ATSC model
			hr = pTunerProps->put_CountryCode(1);
			hr = pTunerProps->put_TuningSpace(0);
			hr = pTunerProps->put_Mode(newMode);
			hr = pTunerProps->put_InputType(0, tuneType);
			SAFE_RELEASE(pTunerProps);
			if ( env != NULL )
				slog((env, "QAM:OnAirQAM initialize '%s'(%d) %s-%d 0x%x\r\n", pCapInfo->TuningMode, tuneType,
					pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, pQAMCtrl ) );

			if ( pQAMCtrl != NULL && pQAMCtrl->tuning_data.state == 1 )
			{
				OnAirQAMTune( env, pCapInfo, pQAMCtrl->tuning_data.lPhysical, 
					          pQAMCtrl->tuning_data.lMod, pQAMCtrl->tuning_data.lInversion, &bLocked );
				pQAMCtrl->tuning_data.state = 0;
			}

		}
}    

static HRESULT OnAirQAMSSetup( JNIEnv *env, DShowCaptureInfo* pCapInfo, void* pData )
{
	char* TunerName;
	LPWSTR pTunerDevName=NULL;
	HRESULT hr;

	if ( pCapInfo == NULL || pData == NULL || *((char*)pData) == 0x0 )
		return E_FAIL;

	if ( pCapInfo->pTVTuner != NULL )
		return S_OK;

	TunerName = (char*)pData;
	graphTools.strCopyA2W( pTunerDevName, TunerName, strlen(TunerName)  ) ;
	CComPtr <IBaseFilter> piTVTunerFilter;
	hr = graphTools.AddFilterByDevicePath( pCapInfo->pGraph, &piTVTunerFilter, pTunerDevName, L"USB HDTV-GT Tuner" );
	if ( hr == S_OK )
	{
		int DeviceID;
		pCapInfo->pTVTuner = piTVTunerFilter;
		DeviceID = GetTunerDeviceID( env, pCapInfo, pCapInfo->pTVTuner );
		if ( pCapInfo->pTVTuner ) pCapInfo->pTVTuner->AddRef();
		if ( env != NULL )
			slog((env, "QAM:OnAirQAM is setup, add %s (devieceid:%d) for %s-%d\r\n", TunerName, DeviceID, 
			   pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		OnAirQAMInitTuner( env, pCapInfo );

	}
	SAFE_DELETE( pTunerDevName );
	return S_OK;
}

HRESULT OnAirQAMScanSignal( JNIEnv *env, DShowCaptureInfo* pCapInfo, unsigned long lPhyCh, long *lMode, long* lInversion, bool* bLocked )
{
	HRESULT  hr;
	DWORD lComData;
	IBaseFilter* pTuner;
	IAMTVTuner *pTvTuner = NULL;
	long signal = 0;
	*bLocked = false;

	if ( pCapInfo == NULL || pCapInfo->pTVTuner == NULL )
		return E_FAIL;

	pTuner = pCapInfo->pTVTuner;
	hr = pTuner->QueryInterface(IID_IAMTVTuner,(void **)&pTvTuner);
	if ( pTvTuner )
	{
		TunerInputType  oldType;
		TunerInputType  tuneType;
		if ( !stricmp( pCapInfo->TuningMode, "Air" ) )
			tuneType = TunerInputAntenna;
		else
		if ( !stricmp( pCapInfo->TuningMode, "Cable" ) )
			tuneType = TunerInputCable;
		else
			tuneType = TunerInputCable;

		if ( SUCCEEDED( pTvTuner->get_InputType( 0, &oldType ) ) )
		{
			if ( oldType != tuneType )
				pTvTuner->put_InputType( 0, tuneType );
		}

		DWORD SPECINV = 0;
		OnAirQAMTunerInterface( pTuner, 0,INIM_DMODE_TUNER_ID,&lComData);
		if( lComData==LGTDVSH062F_LG3303 )
		{
			lComData = VSBMODE;
			OnAirQAMTunerInterface( pTuner, 1, INIM_DMODE_MODE, &lComData );
			hr = pTvTuner->AutoTune(lPhyCh, &signal);
			if( signal != 1 )
			{
				SPECINV = 1;
				lComData = SPECINV;
				OnAirQAMTunerInterface( pTuner,1,INIM_DMODE_SSINVERSION,&lComData);
				lComData = QAM256_MODE;
				OnAirQAMTunerInterface( pTuner,1,INIM_DMODE_MODE,&lComData);
				pTvTuner->SignalPresent( &signal );
				if(signal)
					goto loc_break;

				SPECINV = 0;
				lComData = SPECINV;
				OnAirQAMTunerInterface( pTuner,1,INIM_DMODE_SSINVERSION,&lComData);
				lComData = QAM256_MODE;
				OnAirQAMTunerInterface( pTuner,1,INIM_DMODE_MODE,&lComData);
				pTvTuner->SignalPresent(&signal);
				if( signal )
					goto loc_break;

				SPECINV = 1;
				lComData = SPECINV;
				OnAirQAMTunerInterface( pTuner,1,INIM_DMODE_SSINVERSION,&lComData);
				lComData = QAM64_MODE;
				OnAirQAMTunerInterface( pTuner,1,INIM_DMODE_MODE,&lComData);
				pTvTuner->SignalPresent(&signal);
				*lMode = lComData;
				*lInversion = SPECINV;
				if( signal )
					goto loc_break;

				SPECINV = 0;
				lComData = SPECINV;
				OnAirQAMTunerInterface( pTuner,1,INIM_DMODE_SSINVERSION,&lComData);
				lComData = QAM64_MODE;
				OnAirQAMTunerInterface( pTuner,1,INIM_DMODE_MODE,&lComData);
				pTvTuner->SignalPresent( &signal );
				if(*&signal)
					goto loc_break;


			}
		}
loc_break:
		if ( signal && lComData != VSBMODE )
		{
			*lInversion = SPECINV;
			if ( lComData == QAM256_MODE )
				*lMode = BDA_MOD_256QAM;
			else
			if ( lComData == QAM64_MODE )
				*lMode = BDA_MOD_64QAM;
			else 
				*lMode = -1;
			//freq is locked
		} else
		{
			*lMode = 0;
			*lInversion = 0;
		}
	} 
	flog(("native.log", "QAM:OnAirQAM scan tune ch:%d, mode:%d(%d), inversion:%d, signal:%d. \n", lPhyCh,
			    *lMode, lComData, *lInversion, signal ) );
	*bLocked = (signal != 0);
	SAFE_RELEASE( pTvTuner )
	return S_OK;
}

HRESULT OnAirQAMTune( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhyCh, long lMode, long lInversion, bool* bLocked )
{
	HRESULT hr = S_OK;
	IBaseFilter* pTuner;
	IAMTVTuner *pTvTuner = NULL;	
	*bLocked = false;

	if ( pCapInfo == NULL || pCapInfo->pTVTuner == NULL )
		return E_FAIL;

	pTuner = pCapInfo->pTVTuner;
	hr = pTuner->QueryInterface(IID_IAMTVTuner,(void **)&pTvTuner);
	if ( pTvTuner == NULL )
		return E_FAIL;

	TunerInputType  oldType;
	TunerInputType  tuneType;
	if ( !stricmp( pCapInfo->TuningMode, "Air" ) )
		tuneType = TunerInputAntenna;
	else
	if ( !stricmp( pCapInfo->TuningMode, "Cable" ) )
		tuneType = TunerInputCable;
	else
		tuneType = TunerInputCable;

	if ( SUCCEEDED( pTvTuner->get_InputType( 0, &oldType ) ) )
	{
		if ( oldType != tuneType )
			pTvTuner->put_InputType( 0, tuneType );
	}

	DWORD lComData;
	long lSignal = 0;
	if ( lMode == 0 )
	{
		lComData = VSBMODE;
		OnAirQAMTunerInterface( pTuner, 1, INIM_DMODE_MODE, &lComData );
		hr = pTvTuner->AutoTune(lPhyCh, &lSignal);
		flog(( "native.log", "QAM:OnAirQAM tune VSB mod, signal:%d. \n", lSignal ) );
	} else
	{
		lComData = VSBMODE;
		OnAirQAMTunerInterface( pTuner, 1, INIM_DMODE_MODE, &lComData );
		hr = pTvTuner->AutoTune(lPhyCh, &lSignal);
		
		lComData = lInversion;
		OnAirQAMTunerInterface( pTuner,1,INIM_DMODE_SSINVERSION,&lComData);

		lComData = lMode;
		if ( lComData == BDA_MOD_256QAM)
			lComData = QAM256_MODE;
		else
		if ( lComData == BDA_MOD_64QAM )
			lComData = QAM64_MODE;
		else 
			lComData = -1;
		OnAirQAMTunerInterface( pTuner,1,INIM_DMODE_MODE,&lComData );
		pTvTuner->SignalPresent(&lSignal);
		flog(("native.log", "QAM:OnAirQAM tune ch:%d, mode:%d(%d), inversion:%d, signal:%d. \n", lPhyCh,
			    lMode, lComData, lInversion, lSignal ) );
	}

	*bLocked = lSignal != 0;;
	SAFE_RELEASE( pTvTuner );
	return hr;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////
static BOOL HauppaugeQAMSupportCheck( JNIEnv *env, DShowCaptureInfo* pCapInfo, void** pData )
{
	HRESULT hr;
	IPin *pTunerPin; 
	IKsPropertySet* pKPS=NULL;
	DWORD type_support = 0;

	if ( pCapInfo->pBDATuner == NULL )
	{
		if ( !strncmp( "Hauppauge WinTV", pCapInfo->videoCaptureFilterName, 15 ) )
			return true;

		if ( env ) slog((env, "QAM: Unknown QAM device %s-%d \r\n", 
			pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return false;
	}

	TV_TYPE BDATVType = GetTVType( pCapInfo );  //Nova DVB-T reports supporting QAM, confused
	if ( BDATVType == DVBT || BDATVType == DVBS || BDATVType == DVBS )
	{
		if ( env ) slog((env, "QAM: wrong BDA type %s %s-%d \r\n", pCapInfo->tvType,
			pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return false;
	}

	pTunerPin = FindPin( pCapInfo->pBDATuner, PINDIR_OUTPUT, NULL, NULL );
	if ( pTunerPin == NULL )
	{
		if ( env ) slog((env, "QAM: BDATuner pin for QAM not found %s-%d \r\n", 
			pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return FALSE;
	}

	hr = pTunerPin->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
	if ( FAILED( hr )) 
	{
		SAFE_RELEASE( pTunerPin );
		if ( env ) slog((env, "QAM: Failed to query QAM interface for QAM not found hr=0x%x %s-%d \r\n", hr,
			pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return FALSE;
	}

	hr = pKPS->QuerySupported( KSPROPSETID_BdaDigitalDemodulator, KSPROPERTY_BDA_MODULATION_TYPE, &type_support  );
	SAFE_RELEASE( pKPS );
	SAFE_RELEASE( pTunerPin );

	if ( env != NULL )
		slog((env, "QAM: Hauppauge QAM interface %s (%d) hr=0x%x at %s-%d \r\n", type_support ? "found":"not found", type_support, 
					 hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

	if ( type_support )
		return true;

	return ( type_support != 0 );
}

static HRESULT HauppaugeQAMSSetup( JNIEnv *env, DShowCaptureInfo* pCapInfo, void* pData )
{
	HRESULT hr;
	IPin *pTunerPin; 
	IKsPropertySet* pKPS=NULL;
	DWORD type_support = 0;

	if ( pCapInfo->pBDATuner == NULL )
	{
		return E_FAIL;
	}

	ModulationType modType;
	KSP_NODE instance_data;
	TV_TYPE BDATVType = GetTVType( pCapInfo ); 
	if ( BDATVType == ATSC || BDATVType == UNKNOWN || BDATVType == QAM )
	{
		 if ( stricmp( pCapInfo->TuningMode, "Cable" ) )
			modType = BDA_MOD_8VSB;
		else
			modType = BDA_MOD_256QAM;
	} else
	{
		flog(("native.log", "QAM: Not ATSC tuner for QAM (%s) %s-%d \n", pCapInfo->tvType,
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return E_FAIL;
	}

	pTunerPin = FindPin( pCapInfo->pBDATuner, PINDIR_OUTPUT, NULL, NULL);
	if ( pTunerPin == NULL )
	{
		flog(("native.log", "QAM: Hauppauge tuner pin not found %s-%d \n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return E_FAIL;
	}

	hr = pTunerPin->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
	if ( FAILED( hr )) 
	{
		flog(("native.log", "QAM: Hauppauge tuner pin KPS not found %s-%d \n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		SAFE_RELEASE( pTunerPin );
		return E_FAIL;
	}

	hr = pKPS->QuerySupported( KSPROPSETID_BdaDigitalDemodulator, KSPROPERTY_BDA_MODULATION_TYPE, &type_support  );
	if ( type_support == 0 )
	{
		flog(("native.log", "QAM: Hauppauge tuner doesn't not support QAM %s-%d (%d) \n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, modType ) );
		SAFE_RELEASE( pKPS );
		SAFE_RELEASE( pTunerPin );
		return E_FAIL;
	}

	hr = pKPS->Set( KSPROPSETID_BdaDigitalDemodulator, KSPROPERTY_BDA_MODULATION_TYPE, 
		            &instance_data,  sizeof(instance_data), (void*)&modType, sizeof(ULONG) );

	SAFE_RELEASE( pKPS );
	SAFE_RELEASE( pTunerPin );

	if ( env != NULL )
		slog((env, "QAM: Hauppauge QAM Setup mod: %d for %s %s, (%d, hr=0x%x) at %s-%d \r\n", 
					modType, pCapInfo->tvType, pCapInfo->TuningMode, type_support, hr,
					pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
	else
		flog(("native.log", "QAM: Hauppauge QAM Setup mod:%d for %s %s, (%d, hr=0x%x) at %s-%d \r\n", 
					modType, pCapInfo->tvType, pCapInfo->TuningMode, type_support, hr,
					pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

	return hr;
}


HRESULT OnHauppaugeQAMTune( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhyCh, unsigned long lFreq, long lMod, bool* bLocked )
{
	HRESULT hr = S_OK;
	IPin *pTunerPin; 
	IKsPropertySet* pKPS=NULL;
	DWORD type_support = 0;
	*bLocked = false;

	if ( pCapInfo == NULL || pCapInfo->pBDATuner == NULL )
		return E_FAIL;

	pTunerPin = FindPin( pCapInfo->pBDATuner, PINDIR_OUTPUT, NULL, NULL);
	if ( pTunerPin == NULL )
		return E_FAIL;

	hr = pTunerPin->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
	if ( FAILED( hr )) 
	{
		SAFE_RELEASE( pTunerPin );
		return E_FAIL;
	}

	hr = pKPS->QuerySupported( KSPROPSETID_BdaDigitalDemodulator, KSPROPERTY_BDA_MODULATION_TYPE, &type_support  );
	if ( type_support == 0 )
	{
		SAFE_RELEASE( pKPS );
		SAFE_RELEASE( pTunerPin );
		return E_FAIL;
	}

	ModulationType modType = (ModulationType)lMod;
	KSP_NODE instance_data;
	hr = pKPS->Set( KSPROPSETID_BdaDigitalDemodulator, KSPROPERTY_BDA_MODULATION_TYPE, 
		            &instance_data, sizeof(instance_data), (void*)&modType, sizeof(ULONG) );

	flog(("native.log", "QAM: Hauppauge QAM tuning %d mod:%d (%d, hr=0x%x) at %s-%d \r\n", lFreq, lMod, type_support, hr,
					pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

    tuneATSCQAMChannel( pCapInfo, lPhyCh, lFreq, lMod  );

	*bLocked = SageCheckLocked( pCapInfo ) > 0;
	SAFE_RELEASE( pKPS );
	SAFE_RELEASE( pTunerPin );
	return hr;
}

HRESULT HauppaugeQAMScanSignal( JNIEnv *env, DShowCaptureInfo* pCapInfo, unsigned long lPhyCh,  unsigned long  lFreq, long *lMode, bool* bLocked )
{
	HRESULT hr = S_OK;
	*lMode = BDA_MOD_256QAM;
	hr = OnHauppaugeQAMTune( env, pCapInfo, lPhyCh, lFreq, BDA_MOD_256QAM, bLocked );
	//hr = OnHauppaugeQAMTune( env, pCapInfo, lPhyCh, lFreq, BDA_MOD_8VSB, bLocked );
	return hr;

	*lMode = BDA_MOD_64QAM;
	hr = OnHauppaugeQAMTune( env, pCapInfo, lPhyCh, lFreq, BDA_MOD_64QAM, bLocked );
	return hr;

}

/*
static int in = 0;
static unsigned long tab[]= { 777250000,783250000, 789250000, 795250000, 633250000, 0 };
SetupQAM( env, pCapInfo, tab[in++], 0 );
if ( in >5 ) in = 0;
return hr;

*/
#define MAX_BDA_TUNER_NUM 16
BOOL SearchMatchFilter( JNIEnv *env, CLSID clsid, char* capFiltName, int CapFiltNum,  char* foundName, int nameSize  )
{
	IEnumMoniker *pEnum = NULL;
	LPWSTR pName;
	int hasMoreFilter = 0;
	char enumName[256]={0};
	int  nTunerNum = -1;
	//CLSID CapClassidCategory;
    DEVICE_DRV_INF  enumDrvInfo;
	DEVICE_DRV_INF  devDrvInfo={0};
	char* captureArray[MAX_BDA_TUNER_NUM]={0};
	char devName[256]={0};
	CLSID CapClassidCategory;
	BOOL found = FALSE;
	int nTunerIndex;
	HRESULT hr;

	if ( foundName == NULL || nameSize == 0 )
		return FALSE;

	try {
		IBaseFilter* pFilter = NULL;
		HRESULT hr = FindFilterByName(&pFilter, AM_KSCATEGORY_CAPTURE,
					capFiltName, CapFiltNum, devName, sizeof(devName) );
		CapClassidCategory = AM_KSCATEGORY_CAPTURE;
		if ( FAILED( hr ) )
		{
			hr = FindFilterByName(&pFilter, KSCATEGORY_BDA_RECEIVER_COMPONENT,
					capFiltName, CapFiltNum, devName, sizeof(devName) );
			CapClassidCategory = KSCATEGORY_BDA_RECEIVER_COMPONENT;
		}

		//if ( FAILED( hr ) )
		//{
		//	hr = FindFilterByName(&pFilter, KSCATEGORY_BDA_NETWORK_TUNER,
		//			capFiltName, CapFiltNum, devName, sizeof(devName) );
		//	CapClassidCategory = KSCATEGORY_BDA_NETWORK_TUNER;
		//}

		SAFE_RELEASE(pFilter);
		if ( FAILED( hr ) )
			return FALSE;

		GetDeviceInfo( devName, &devDrvInfo);
		nTunerIndex = GetTunerNum( env, devName, CapClassidCategory, &devDrvInfo );
		if ( env != NULL )
			slog((env, "QAM:Search BDA capture device:%s-%d (%s) on location:%s [state%d, index:%d] .\r\n",  capFiltName, CapFiltNum,
				devName, devDrvInfo.hardware_loc, devDrvInfo.state, nTunerIndex ) );

	} catch (...)
	{
			return FALSE;
	}

	if ( devDrvInfo.state <= 0 )
		return FALSE;

	hr = graphTools.EnumFilterPathFirst( clsid, &pEnum, &pName ); 
	while ( hr == S_OK )
	{
			int length = wcslen(pName);
			length =length > sizeof(enumName)? sizeof(enumName) : length;
			memset( enumName, 0x0, sizeof(enumName) );
			wcstombs( enumName, pName, length );
			delete pName;
			GetDeviceInfo( enumName, &enumDrvInfo );
			if ( env != NULL )
				slog((env, "enum device found:%s; loc:%s\r\n",  enumName, enumDrvInfo.hardware_loc ) );
			if ( enumDrvInfo.state != 0 && enumDrvInfo.hardware_loc[0] != 0 )
			{
				if ( !strcmp( enumDrvInfo.hardware_loc, devDrvInfo.hardware_loc ) && strlen(enumDrvInfo.hardware_loc) )
				{
					/*
					if ( hasMoreFilter >= 1 )
					{
						//dual tuner BDA board, two tuner share the same bus or device. we need further matching by order )
						if ( nTunerNum < 0 )
							nTunerNum = GetTunerNum( env, devName, CapClassidCategory, devDrvInfo.hardware_loc );
						if ( hasMoreFilter == nTunerNum && foundName != NULL)
							strncpy( foundName, enumName, nameSize );
						slog((env, "checking device: %d-%d for %s.\r\n",  nTunerNum, hasMoreFilter, enumName) );
					} else
					{
						if ( foundName != NULL )
							strncpy( foundName, enumName, nameSize );
					}*/
					if ( hasMoreFilter < MAX_BDA_TUNER_NUM )
					{
						captureArray[hasMoreFilter] = new char[256];
						strncpy( captureArray[hasMoreFilter], enumName, 256 );
						if ( env != NULL )
							slog((env, "QAM:BDA tuner found:(%d) %s on %s.\r\n", hasMoreFilter, enumName, devDrvInfo.hardware_loc ) );
						hasMoreFilter++;
					}
				}
			}
			hr = graphTools.EnumFilterPathNext( pEnum, &pName  );
	}
	if ( pEnum != NULL )
		graphTools.EnumFilterPathClose( pEnum );
	
	if ( hasMoreFilter > 0 )
	{
		if ( nTunerIndex>= 0 && nTunerIndex<MAX_BDA_TUNER_NUM && captureArray[nTunerIndex] != NULL )
		{
			strncpy( foundName, captureArray[nTunerIndex], nameSize );
			if ( env != NULL )
				slog((env, "QAM:matched tuner is found %s on location:%s\r\n", foundName, devDrvInfo.hardware_loc ));
			found = TRUE;
		} else
		{
			if ( env != NULL )
				slog((env, "QAM: no matched tuner was on location:%s, index:%d (%s-%d).\r\n", 
					devDrvInfo.hardware_loc,  nTunerIndex, capFiltName, CapFiltNum ));
		}
	}

	for ( int i = 0; i<MAX_BDA_TUNER_NUM; i++ )
	  if ( captureArray[i] ) delete captureArray[i];

	return found;		

}


static BOOL IsQAMSupported( JNIEnv *env, DShowCaptureInfo* pCapInfo, void** pData, const char** DeviceTag )
{
	if ( pCapInfo == NULL || pCapInfo->bdaCaptureFilterName[0] == 0x0  ) 
		return FALSE;
	HRESULT hr;
	DEVICE_DRV_INF  VideoCaptureDrvInfo={0};
	*pData = NULL;

	if ( pCapInfo != NULL && pCapInfo->pQAMCtrlData != NULL )
	{
		QAM_CTRL_HEADER* pQAMCtrl = (QAM_CTRL_HEADER*)pCapInfo->pQAMCtrlData;
		if ( !strcmp( pQAMCtrl->TAG, BDAQAM ) )
		{
			if ( env != NULL )
				slog( (env,"QAM:It's a BDA QAM %s-%d\r\n", 
					pCapInfo->bdaCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

			return TRUE;
		}
	}

	hr = GetDeviceInfo( pCapInfo->bdaCaptureFilterName, &VideoCaptureDrvInfo );
	if ( hr != S_OK || VideoCaptureDrvInfo.state == 0 )
	{
		if ( env != NULL )
			slog( (env,"QAM:Can't read capture drv info for QAM detect %s-%d\r\n", 
				pCapInfo->bdaCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return FALSE;
	}
	if ( env != NULL )
		slog( (env,"QAM:Got capture drv info for QAM mfg:'%s' %s-%d\r\n", VideoCaptureDrvInfo.mfg,
		                pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

	if ( !strnicmp( VideoCaptureDrvInfo.mfg, ONAIRSOLUTION_MFG, strlen(ONAIRSOLUTION_MFG) ) )
	{
		if ( OnAirQAMSupportCheck( env, pCapInfo, pData ) )
		{
			*DeviceTag = OnAirSolutionUSB;
			if ( env != NULL ) slog( (env,"QAM:supported tag:%s\r\n", *DeviceTag ));
			return TRUE;
		} else
		{
			if ( env != NULL ) slog( (env,"QAM:not supported %s-%d\r\n",
					pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
			return FALSE;
		}
	} else
	if ( !strnicmp( VideoCaptureDrvInfo.mfg, FUSION_MFG, strlen(FUSION_MFG) ) )
	{
			if ( env != NULL ) slog( (env,"QAM:not supported yet %s-%d\r\n",
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
			return FALSE;

	} else
	if ( !strnicmp( VideoCaptureDrvInfo.mfg, HAUPPAUGE_MFG, strlen(HAUPPAUGE_MFG) ) )
	{
		if ( HauppaugeQAMSupportCheck( env, pCapInfo, pData ) )
		{
			*DeviceTag = HauppaugeQAM;
			if ( env != NULL )	slog( (env,"QAM:supported tag:%s\r\n", *DeviceTag ));
			return TRUE;
		} else
		{
			if ( env != NULL )	slog( (env,"QAM:not supported %s-%d\r\n",
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
			return FALSE;
		}
	}

	return FALSE;
}

int SetupQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	const char* DeviceTag;
	void* pQAMData = NULL;
	QAM_CTRL_HEADER* pQAMCtrl = NULL;

//_flog_check();

	if ( pCapInfo == NULL )
		return 0; 

	if ( pCapInfo->pQAMCtrlData != NULL )
	{
		pQAMCtrl = (QAM_CTRL_HEADER*)pCapInfo->pQAMCtrlData;

		if ( pQAMCtrl->state == QAM_READY )
			return 1;
		if ( pQAMCtrl->state == QAM_CLOSE )
			return 0;
		if ( pQAMCtrl->state == QAM_NOT_SUPPORT )
			return 0;
	} else
	{
		pQAMCtrl = (QAM_CTRL_HEADER* )new char[ sizeof(QAM_CTRL_HEADER) ];
		memset( pQAMCtrl, 0x0, sizeof( QAM_CTRL_HEADER) );
		pCapInfo->pQAMCtrlData = pQAMCtrl; 
		pQAMCtrl->pCapInfo = pCapInfo;
		pQAMCtrl->state = QAM_UNINITILIZED;
	}
	BOOL support = IsQAMSupported( env, pCapInfo, &pQAMData, &DeviceTag );
	if ( !support ) 
	{
		pQAMCtrl->state = QAM_NOT_SUPPORT;
		SAFE_DELETE(pQAMData);
		SAFE_DELETE(pCapInfo->pQAMCtrlData);
		return 0;
	}

	if ( !strcmp( DeviceTag, OnAirSolutionUSB ) )
	{
		strncpy( pQAMCtrl->TAG, OnAirSolutionUSB, sizeof(pQAMCtrl->TAG) );
		if ( OnAirQAMSSetup( env, pCapInfo, pQAMData ) == S_OK )
			pQAMCtrl->state = QAM_READY;
		
	} else
	if ( !strcmp( DeviceTag, HauppaugeQAM ) )
	{
		strncpy( pQAMCtrl->TAG, HauppaugeQAM, sizeof(pQAMCtrl->TAG) );
		if ( HauppaugeQAMSSetup( env, pCapInfo, pQAMData ) == S_OK )
			pQAMCtrl->state = QAM_READY;
		
	} else
	{
		pQAMCtrl->state = QAM_NOT_SUPPORT;
		if ( env != NULL ) slog(( env, "QAM:Unknow QAM device TAG %s\r\n", DeviceTag ) );
	}

	SAFE_DELETE( pQAMData );
	return 0;
}

//Vista provides new interface for QAM access
int SetupBDAQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	QAM_CTRL_HEADER* pQAMCtrl = NULL;

	if ( pCapInfo == NULL )
		return 0; 

	if ( pCapInfo->pQAMCtrlData != NULL )
	{
		pQAMCtrl = (QAM_CTRL_HEADER*)pCapInfo->pQAMCtrlData;
		
		if ( env != NULL ) 	slog(( env, "QAM:Setup '%s' as BDA QAM \r\n", pQAMCtrl->TAG ) );

		strncpy( pQAMCtrl->TAG, BDAQAM, sizeof(pQAMCtrl->TAG) );
		if ( pQAMCtrl->state == QAM_READY )
			return 1;
		if ( pQAMCtrl->state == QAM_CLOSE )
			return 0;
		if ( pQAMCtrl->state == QAM_NOT_SUPPORT )
			return 0;
	} else
	{
		pQAMCtrl = (QAM_CTRL_HEADER* )new char[ sizeof(QAM_CTRL_HEADER) ];
		memset( pQAMCtrl, 0x0, sizeof( QAM_CTRL_HEADER) );
		pCapInfo->pQAMCtrlData = pQAMCtrl; 
		pQAMCtrl->pCapInfo = pCapInfo;
		pQAMCtrl->state = QAM_READY;
		strncpy( pQAMCtrl->TAG, BDAQAM, sizeof(pQAMCtrl->TAG) );
		if ( env != NULL ) slog(( env, "QAM:Setup BDA QAM\r\n") );
	}

	return 0;
}

void ReleaseQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	QAM_CTRL_HEADER* pQAMCtrl = NULL;
	if ( pCapInfo == NULL || pCapInfo->pQAMCtrlData == NULL )
		return;

	pQAMCtrl = (QAM_CTRL_HEADER*)pCapInfo->pQAMCtrlData;

if ( env ) slog(( env, "QAM:CtrlData.... 0x%x\r\n", pCapInfo->pQAMCtrlData ) );

	if ( env != NULL )	slog( (env,"QAM: QAM Ctrl relesed %s-%d\r\n", 
		    pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

	delete pCapInfo->pQAMCtrlData;
	pCapInfo->pQAMCtrlData = NULL;
}

int TuneQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhysical, unsigned long lFreq, long lMod, long lInversion, bool* bLocked )
{
	HRESULT hr = E_FAIL;
	QAM_CTRL_HEADER* pQAMCtrl = NULL;

	if ( pCapInfo == NULL )
		return 0;

	if (  pCapInfo->pQAMCtrlData == NULL )
	{
		pQAMCtrl = (QAM_CTRL_HEADER* )new char[ sizeof(QAM_CTRL_HEADER) ];
		memset( pQAMCtrl, 0x0, sizeof( QAM_CTRL_HEADER) );
		pCapInfo->pQAMCtrlData = pQAMCtrl; 
		pQAMCtrl->pCapInfo = pCapInfo;
		pQAMCtrl->state = QAM_UNINITILIZED;
		if ( env != NULL )
			slog( (env,"QAM:QAM ctrl block is allocled 0x%x\r\n", pQAMCtrl ) );
		else
			flog(("native.log", "QAM:QAM ctrl block is allocled 0x%x\n", pQAMCtrl ) );
	}
	pQAMCtrl = (QAM_CTRL_HEADER*)pCapInfo->pQAMCtrlData;
	pQAMCtrl->tuning_data.lPhysical = lPhysical;
	pQAMCtrl->tuning_data.lMod = lMod;
	pQAMCtrl->tuning_data.lInversion = lInversion;
	pQAMCtrl->tuning_data.state = 1;

	if ( pQAMCtrl->state == QAM_CLOSE )
		return 0;

	if ( pQAMCtrl->state == QAM_UNINITILIZED )
	{
		SetupQAM( env, pCapInfo );
		if ( ( pCapInfo->pQAMCtrlData == NULL ) || ( pQAMCtrl->state == QAM_UNINITILIZED ) )
		{
			flog(("native.log", "QAM: QAM Initialized failed (%d).\n", pQAMCtrl->state ) );
			return 0;
		}
		
	}

	if ( !strcmp( pQAMCtrl->TAG, OnAirSolutionUSB ) )
	{
		hr = OnAirQAMTune( env, pCapInfo, lPhysical, lMod, lInversion, bLocked );
		if ( hr == S_OK ) pQAMCtrl->tuning_data.state = 0;

	} else
	if ( !strcmp( pQAMCtrl->TAG, HauppaugeQAM ) )
	{
		hr = OnHauppaugeQAMTune( env, pCapInfo, lPhysical, lFreq, lMod, bLocked );
		if ( hr == S_OK ) pQAMCtrl->state = QAM_READY;
		if ( hr == S_OK ) pQAMCtrl->tuning_data.state = 0;

	} else
	if ( !strcmp( pQAMCtrl->TAG, BDAQAM ) )
	{
		//normal ATSC QAM tune
		tuneATSCQAMChannel( pCapInfo, lPhysical, lFreq, lMod  );
		*bLocked = SageCheckLocked( pCapInfo ) > 0;

	} else
	{
		flog(("native.log", "QAM: QAM unknow tag %s\n", pQAMCtrl->TAG ) );
		return 0;
	}

	if( env != NULL )
		slog( (env,"QAM: tuned QAM ch:%d frq:%d mod:%d inv:%d  hr:%x %s-%d\r\n", lPhysical, lFreq, lMod, lInversion, hr,
		    pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
	else
		flog( ("native.log", "QAM: tuned QAM ch:%d frq:%d mod:%d inv:%d  hr:%x %s-%d\n", lPhysical, lFreq, lMod, lInversion, hr,
		    pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

	return 1;
}

int ScanChannelQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhysical, unsigned long lFreq, long* lMod, long* lInversion, bool* bLocked )
{
	HRESULT hr = E_FAIL;
	if ( pCapInfo == NULL || pCapInfo->pQAMCtrlData == NULL )
		return 0;

	QAM_CTRL_HEADER*  pQAMCtrl = (QAM_CTRL_HEADER*)pCapInfo->pQAMCtrlData;
	if ( pQAMCtrl->state == QAM_CLOSE )
		return 0;

	if ( !strcmp( pQAMCtrl->TAG, OnAirSolutionUSB ) )
	{
		hr = OnAirQAMScanSignal( env, pCapInfo, lPhysical, lMod, lInversion, bLocked );
	} else
	if ( !strcmp( pQAMCtrl->TAG, HauppaugeQAM ) )
	{
		*lInversion = 0;
		hr = HauppaugeQAMScanSignal( env, pCapInfo, lPhysical, lFreq, lMod, bLocked );
		if ( hr == S_OK )	pQAMCtrl->state = QAM_READY;
	} else
	//if ( !strcmp( pQAMCtrl->TAG, BDAQAM ) ) //Network provider QAM interface
	{
		if ( *lMod == 0 || *lMod == -1 )
			*lMod = BDA_MOD_256QAM;
		hr = tuneATSCQAMChannel( pCapInfo, lPhysical, lFreq, *lMod  );
		*bLocked = SageCheckLocked( pCapInfo ) > 0;
	}

	if ( env != NULL )
		slog( (env,"QAM:Scan channel ch:%d frq:%d mod:%d inv:%d locked:%d hr:%x %s-%d\r\n", 
			    lPhysical, lFreq, *lMod, *lInversion, *bLocked, hr,
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
	return 1;

}

int QAMTunerType( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	HRESULT hr = E_FAIL;
	if ( pCapInfo == NULL || pCapInfo->pQAMCtrlData == NULL )
	{
		if ( env != NULL )
			slog( (env,"QAM: not initailze %s-%d\r\n",  pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return 0;
	}

	QAM_CTRL_HEADER*  pQAMCtrl = (QAM_CTRL_HEADER*)pCapInfo->pQAMCtrlData;
	if ( pQAMCtrl->state == QAM_CLOSE )
	{
		if ( env != NULL )
			slog( (env,"QAM: not setup %s-%d\r\n",  pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return 0;
	}

	if ( !strcmp( pQAMCtrl->TAG, OnAirSolutionUSB ) )
	{
		//slog( (env,"QAM: type:1 %s-%d\r\n",  pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return 1;
	} else
	if ( !strcmp( pQAMCtrl->TAG, HauppaugeQAM ) )
	{
		return HauppaugeQAMSupportCheck( env, pCapInfo, NULL );
	}

	return 0;
}

#include <streams.h>
#include <ks.h>
#include <ksproxy.h>
static int GetTunerDeviceID( JNIEnv *env, DShowCaptureInfo* pCapInfo, IBaseFilter* pBDATuner  )
{
	HRESULT hr;
    IKsPin* pKsPin = NULL;
    KSMULTIPLE_ITEM *pmi;
	IPin *pPin;
	int DeviceID = -1; 

	if ( pBDATuner == NULL )
	{
		return -1;
	}

	hr = GetPin( pBDATuner, PINDIR_OUTPUT, 0, &pPin );
	if (FAILED(hr)) 
	{
		if ( env ) slog (( "BDA Tuner Pin is not ready %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                                    pCapInfo->videoCaptureFilterNum ));
		return -1;
	}

    hr = pPin->QueryInterface(IID_IKsPin, (void **)&pKsPin);
    if (FAILED(hr)) 
	{
		if ( env ) slog (( "BDA Tuner Pin interface is not found %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                                    pCapInfo->videoCaptureFilterNum ));
		pPin->Release();
        return -1;  // Pin does not support IKsPin.
	}

    hr = pKsPin->KsQueryMediums(&pmi);
    pKsPin->Release();
	pPin->Release();
    if (FAILED(hr))
	{
		if ( env ) slog (( "BDA Tuner mediums is not retrieved %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                                    pCapInfo->videoCaptureFilterNum ));
        return -1;  // Pin does not support mediums.
	}

    if (pmi->Count) 
    {
        REGPINMEDIUM *pTemp = (REGPINMEDIUM*)(pmi + 1);
       	DeviceID = pTemp->dw1;
    }        

    CoTaskMemFree(pmi);
	//slog(( "QAM:tuner deviceid %d %s-%d\r\n", DeviceID,	 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	return DeviceID;
}

