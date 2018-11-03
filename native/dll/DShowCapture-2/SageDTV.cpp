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

// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)
#pragma warning(disable: 4702)
#ifndef _WIN64
  #define _USE_32BIT_TIME_T
#endif

#include "stdafx.h"
#include <bdatypes.h>
#include "Time.h"
#include "Channel.h"
#include "SageDTV.h"
#include "DShowCapture.h"
#include "../../include/guids.h"
#include "../../include/sage_DShowCaptureDevice.h"
#include "DShowUtilities.h"
#include "FilterGraphTools.h"
#include "../ax/TSSplitter2.0/iTSSplitter.h"
#include "TunerPlugin.h"

#include "CaptureMsg.h"
#include "QAMCtrl.h"    
#include "CAMCtrl.h"  
#include "DiSeqC.h"
#include <time.h>
#include "uniapi.h"

#if( _MSC_VER <= 800 )
#pragma pack(1)  
#else
#include <pshpack1.h>
#endif

int GetCfgVal( char* Str, char* Name, int MaxNameLen, int* Val, char* Ext, int MaxExtLen );
extern FilterGraphTools graphTools;
HRESULT SendDiSEQCCmd( DShowCaptureInfo* pCapInfo, unsigned long freq, int polarisation, unsigned short sat_no );
void _ClearPIDMap(  DShowCaptureInfo *pCapInfo );

#ifdef WIN32   
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}
#endif

int flog_enabled=false;


#ifdef REMOVE_LOG	
#define flog(x) {}
#else
#define flog(x)   _flog x
#endif

void enable_tssplitter_log()
{
	flog_enabled = true;
}

void disable_tssplitter_log()
{
	flog_enabled = true;
}

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
	fprintf( fp, "%02d/%02d/%d %02d:%02d:%02d ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
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

bool epg_log_enabled = false;
static void _epg_log_check()
{
	FILE* fp = fopen( "EPG_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		epg_log_enabled = true; 
		fclose( fp );
	} else
		epg_log_enabled = false;
}

int SageNativeLogCheck(  )
{
	_flog_check();
	if ( flog_enabled )
		enable_channel_log();
	else
		disable_channel_log();
	return flog_enabled;
}

void EnableNativeLog( bool Enable  )
{
	flog_enabled = true;
	enable_channel_log();
}

void DisableNativeLog( bool Enable  )
{
	flog_enabled = false;
	disable_channel_log();
}

int InitSageDTV( DShowCaptureInfo* pCapInfo )
{
	unsigned long delay_time = 500;
	char file_name[_MAX_PATH];
	char buf[512], name[64], ext[512-64]; int val;

	pCapInfo->EPG_post_counter = 0;
	pCapInfo->EPG_post_flow_rate_ctrl = 4;
	pCapInfo->EPG_post_flow_rate_ctrl = GetRegistryDword(HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\DirectShow", "EPGFlowRateCtrl", 4 );

	_epg_log_check();
	SPRINTF( file_name, sizeof(file_name), "%s.par", pCapInfo->videoCaptureFilterName );
	if ( pCapInfo == NULL || pCapInfo->channel == NULL )
		return 0;

	FILE* fd = fopen( file_name, "rt" );
	if ( fd == NULL )
		flog(( "native.log", "'%s' is not exist.\r\n", file_name ));
	else
		flog(( "native.log", "'%s' is found.\r\n", file_name ));
	if ( fd != NULL )
	{
		while ( !feof(fd) )
		{
			memset( buf, 0, sizeof(buf) );
			if ( fgets( buf, sizeof(buf), fd ) )
			{
				if ( buf[0] == '#' ) continue;	
				name[0] = 0x0; val = 0; ext[0] = 0x0;
				if ( GetCfgVal( buf, name, sizeof(name), &val, ext, sizeof(ext) ) )
				{
					flog(( "native.log", "   %s %d '%s'\r\n", name, val, ext ));
					if ( !strcmp( name, "delay" ) && val > 0 )
						delay_time = val;
				}
			}
		}
		fclose( fd );
	}

	flog(( "native.log", "Set parser delay time %d on '%s'.\r\n", delay_time, pCapInfo->videoCaptureFilterName ));
	parserDelayTime( (CHANNEL_DATA*)pCapInfo->channel,  delay_time );
	return 1;
}

HRESULT  SetupTunerFreq( DShowCaptureInfo* pCapInfo, long freq, long bandwidth, int pol )
{
	HRESULT hr;

	//Get IID_IBDA_Topology
	if ( pCapInfo->pBDATuner == NULL )
		return E_FAIL;

	CComPtr <IBDA_Topology> bdaNetTop;
	if (FAILED(hr = pCapInfo->pBDATuner->QueryInterface(&bdaNetTop)))
	{
		flog( ("native.log","Failed get tuner bdaNetTop hr=0x%x \r\n",hr) );
	    return hr;
	}

	ULONG NodeTypes;
	ULONG NodeType[32];
	ULONG Interfaces;
	GUID Interface[32];
	CComPtr <IUnknown> iNode;

	if (FAILED(hr = bdaNetTop->GetNodeTypes(&NodeTypes, 32, NodeType)))
	{
		bdaNetTop.Release();
		flog( ("native.log","Failed get tuner NodeTypes hr=0x%x \r\n",hr) );
	    return hr;
	}

	for ( ULONG i=0 ; i<NodeTypes ; i++ )
	{
		hr = bdaNetTop->GetNodeInterfaces(NodeType[i], &Interfaces, 32, Interface);
		if (hr == S_OK)
		{
			for ( ULONG j=0 ; j<Interfaces ; j++ )
			{
				if (Interface[j] == IID_IBDA_FrequencyFilter )
				{
					hr = bdaNetTop->GetControlNode(0, 1, NodeType[i], &iNode);
					if (hr == S_OK)
					{
						CComPtr <IBDA_FrequencyFilter> pFrequencyFilter;

						hr = iNode.QueryInterface(&pFrequencyFilter);
						if (hr == S_OK)
						{
							if ( FAILED( hr =  pFrequencyFilter->put_FrequencyMultiplier(1000) ) )
								flog( ("native.log" ,"Failed setup tuner frequency multiplier hr=0x%x \r\n", hr) );

							if ( FAILED(hr = pFrequencyFilter->put_Frequency(freq) ) )
								flog( ("native.log","Failed setup tuner frequency %d hr=0x%x \r\n", freq, hr) );

							if ( bandwidth != 0 && bandwidth != -1 )
							{
								hr =  pFrequencyFilter->put_Bandwidth( bandwidth );
								flog( ("native.log","Failed setup tuner bandwidth %d hr=0x%x \r\n", bandwidth, hr) );
							}

							if ( pol != -1 )
							{
								if ( FAILED ( hr = pFrequencyFilter->put_Polarity( (Polarisation)pol ) ) )
									flog( ("native.log","Failed setup tuner polarity %d hr=0x%x \r\n", pol, hr) );
							}

							pFrequencyFilter.Release();
						}
						iNode.Release();
					}
					break;
				}
			}
		}
	}
	bdaNetTop.Release();

	return S_OK;
}

HRESULT  SetupTunerModulator( DShowCaptureInfo* pCapInfo, 
		int modulation, int symrate, int innerFEC, int innerFECRate, int outerFEC, int outerFECRate, int invserion )
{
	HRESULT hr;

	//Get IID_IBDA_Topology
	if ( pCapInfo->pBDATuner == NULL )
		return E_FAIL;

	CComPtr <IBDA_Topology> bdaNetTop;
	if (FAILED(hr = pCapInfo->pBDATuner->QueryInterface(&bdaNetTop)))
	{
		flog( ("native.log","Failed get tuner bdaNetTop hr=0x%x \r\n",hr) );
	    return hr;
	}

	ULONG NodeTypes;
	ULONG NodeType[32];
	ULONG Interfaces;
	GUID Interface[32];
	CComPtr <IUnknown> iNode;

	if (FAILED(hr = bdaNetTop->GetNodeTypes(&NodeTypes, 32, NodeType)))
	{
		bdaNetTop.Release();
		flog( ("native.log","Failed get tuner NodeTypes hr=0x%x \r\n",hr) );
	    return hr;
	}

	for ( ULONG i=0 ; i<NodeTypes ; i++ )
	{
		hr = bdaNetTop->GetNodeInterfaces(NodeType[i], &Interfaces, 32, Interface);
		if (hr == S_OK)
		{
			for ( ULONG j=0 ; j<Interfaces ; j++ )
			{
				if (Interface[j] == IID_IBDA_DigitalDemodulator )
				{
					hr = bdaNetTop->GetControlNode(0, 1, NodeType[i], &iNode);
					if (hr == S_OK)
					{
						CComPtr <IBDA_DigitalDemodulator> pDigitalDemodulator;

						hr = iNode.QueryInterface( &pDigitalDemodulator );
						if (hr == S_OK)
						{
							if ( modulation != -1 )
							{
								if ( FAILED( hr =  pDigitalDemodulator->put_ModulationType((ModulationType*)&modulation)  ) )
									flog( ("native.log","Failed setup tuner frequency ModulationType %d hr=0x%x \r\n", modulation, hr) );
							}
							if ( symrate != 0 && symrate!= -1 )
							{
								if ( FAILED(hr = pDigitalDemodulator->put_SymbolRate( (unsigned long*)&symrate ) ) )
									flog( ("native.log","Failed setup tuner SymbolRate %d hr=0x%x \r\n",symrate, hr) );
							}

							if ( innerFEC != -1 )
							{
								if ( FAILED( hr =  pDigitalDemodulator->put_InnerFECMethod((FECMethod*)&innerFEC ) ) )
									flog( ("native.log","Failed setup tuner InnerFECMethod %d hr=0x%x \r\n", innerFEC, hr) );
							}

							if ( innerFECRate != -1 )
							{
								if ( FAILED( hr =  pDigitalDemodulator->put_InnerFECRate((BinaryConvolutionCodeRate*)&innerFECRate ) ) )
									flog( ("native.log","Failed setup tuner InnerFECRate %d hr=0x%x \r\n", innerFECRate, hr) );
								else
									flog( ("native.log","Setup tuner InnerFECRate 0x%x hr=0x%x \r\n", innerFECRate, hr) );
							}

							if ( outerFEC != -1 )
							{
								if ( FAILED( hr =  pDigitalDemodulator->put_OuterFECMethod((FECMethod*)&outerFEC ) ) )
									flog( ("native.log","Failed setup tuner OuterFECMethod %d hr=0x%x \r\n", innerFEC, hr) );
							}

							if ( outerFECRate != -1 )
							{
								if ( FAILED( hr =  pDigitalDemodulator->put_OuterFECRate((BinaryConvolutionCodeRate*)&outerFECRate ) ) )
									flog( ("native.log","Failed setup tuner OuterFECRate %d hr=0x%x \r\n", outerFECRate, hr) );
							}

							if ( invserion > 0 )
							{
								if ( FAILED( hr = pDigitalDemodulator->put_SpectralInversion((SpectralInversion*)&invserion) ) ) 
									flog( ("native.log","Failed setup tuner SpectralInversion %d, hr=0x%x.\r\n", invserion, hr) );
							}

							pDigitalDemodulator.Release();
						}
						iNode.Release();
					}
					break;
				}
			}
		}
	}
	bdaNetTop.Release();

	return S_OK;
}


HRESULT  SetupTunerLNBInfo( DShowCaptureInfo* pCapInfo, long low, long hi, long sw )
{
	HRESULT hr;

	//Get IID_IBDA_Topology
	if ( pCapInfo->pBDATuner == NULL )
		return E_FAIL;

	CComPtr <IBDA_Topology> bdaNetTop;
	if (FAILED(hr = pCapInfo->pBDATuner->QueryInterface(&bdaNetTop)))
	{
		flog( ("native.log","Failed get tuner bdaNetTop hr=0x%x \r\n",hr) );
	    return hr;
	}

	ULONG NodeTypes;
	ULONG NodeType[32];
	ULONG Interfaces;
	GUID Interface[32];
	CComPtr <IUnknown> iNode;

	if (FAILED(hr = bdaNetTop->GetNodeTypes(&NodeTypes, 32, NodeType)))
	{
		bdaNetTop.Release();
		flog( ("native.log","Failed get tuner NodeTypes hr=0x%x \r\n",hr) );
	    return hr;
	}

	for ( ULONG i=0 ; i<NodeTypes ; i++ )
	{
		hr = bdaNetTop->GetNodeInterfaces(NodeType[i], &Interfaces, 32, Interface);
		if (hr == S_OK)
		{
			for ( ULONG j=0 ; j<Interfaces ; j++ )
			{
				if (Interface[j] == IID_IBDA_LNBInfo )
				{
					hr = bdaNetTop->GetControlNode(0, 1, NodeType[i], &iNode);
					if (hr == S_OK)
					{
						CComPtr <IBDA_LNBInfo> pLNBInfo;

						hr = iNode.QueryInterface(&pLNBInfo);
						if (hr == S_OK)
						{
							if ( FAILED( hr =  pLNBInfo->put_LocalOscilatorFrequencyLowBand(low) ) )
								flog( ("native.log" ,"Failed setup tuner LocalOscilatorFrequencyLowBand:%d hr=0x%x \r\n", low, hr) );

							if ( FAILED( hr =  pLNBInfo->put_LocalOscilatorFrequencyHighBand(hi) ) )
								flog( ("native.log" ,"Failed setup tuner LocalOscilatorFrequencyHighBand:%d hr=0x%x \r\n", hi, hr ) );

							if ( FAILED( hr =  pLNBInfo->put_HighLowSwitchFrequency(sw) ) )
								flog( ("native.log" ,"Failed setup tuner HighLowSwitchFrequency:%d hr=0x%x \r\n", sw, hr) );

							pLNBInfo.Release();
						}
						iNode.Release();
					}
					break;
				}
			}
		}
	}
	bdaNetTop.Release();

	return S_OK;
}

#include "..\..\..\third_party\SiliconDust\hdhomerun_bda_interface.h"
const IID IID_IHDHomeRun_ProgramFilter = STATIC_IID_IHDHomeRun_ProgramFilter;

HRESULT  SetupTunerProgramFilter( DShowCaptureInfo* pCapInfo, unsigned int ProgramId )
{
	HRESULT hr;

	CComPtr <IBDA_Topology> bdaNetTop;
	BOOL bBadNetTopFound = FALSE;

	//Get IID_IBDA_Topology
	if ( pCapInfo->pBDATuner == NULL && pCapInfo->pBDACapture == NULL )
		return E_FAIL;
	
	if ( pCapInfo->pBDATuner && !FAILED(hr = pCapInfo->pBDATuner->QueryInterface(&bdaNetTop)) )
	{
		bBadNetTopFound = TRUE;
	}

	if ( !bBadNetTopFound )
	{
		if ( pCapInfo->pBDACapture && !FAILED(hr = pCapInfo->pBDACapture->QueryInterface(&bdaNetTop)) )
			bBadNetTopFound = TRUE;
	}

	if ( !bBadNetTopFound )
		return E_FAIL;

	ULONG NodeTypes;
	ULONG NodeType[32];
	ULONG Interfaces;
	GUID Interface[32];
	CComPtr <IUnknown> iNode;

	if (FAILED(hr = bdaNetTop->GetNodeTypes(&NodeTypes, 32, NodeType)))
	{
		bdaNetTop.Release();
		flog( ("native.log","Failed get tuner NodeTypes hr=0x%x \r\n",hr) );
	    return hr;
	}

	for ( ULONG i=0 ; i<NodeTypes ; i++ )
	{
		hr = bdaNetTop->GetNodeInterfaces(NodeType[i], &Interfaces, 32, Interface);
		if (hr == S_OK)
		{
			for ( ULONG j=0 ; j<Interfaces ; j++ )
			{
				//SiliconDust Propritery API
				if ( Interface[j] == IID_IHDHomeRun_ProgramFilter )
				{
					hr = bdaNetTop->GetControlNode(0, 1, NodeType[i], &iNode);
					if (hr == S_OK)
					{
						CComPtr <IHDHomeRun_ProgramFilter> pProgramFilter;

						hr = iNode.QueryInterface(&pProgramFilter);
						if (hr == S_OK)
						{
							hr = pProgramFilter->put_ProgramNumber( ProgramId ); 
							if ( hr != S_OK )
								flog( ("native.log" ,"HDHR: Failed put HDHR program filter programId:%d hr=0x%x \r\n", ProgramId, hr) );
							else
							if ( hr == S_OK )
								flog( ("native.log" ,"HDHR:  Program %d is put into a HDHR program filter is called hr=0x%x \r\n", ProgramId, hr) );
							pProgramFilter.Release();
						}
						iNode.Release();
					}
					break;
				}
			}
		}
	}
	bdaNetTop.Release();

	return S_OK;
}



HRESULT  tuneATSCChannel( DShowCaptureInfo* pCapInfo, ATSC_FREQ* atsc, int bDryRun )
{
    HRESULT hr = S_OK;
	long lPhysicalChannel;
	long lFrequency;

	if ( pCapInfo->pDebugSrcSink != NULL )  //when debugsrc is on, always bypass tune.
		return 1;

	//its's a virtual tuner, skip;
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
	{
		pCapInfo->dwTuneState = 1;
		return 1;
	}

	if ( bDryRun ) return 1;

	lPhysicalChannel = atsc->physical_ch;
	lFrequency = atsc->frequency/1000;
    // create tune request
	CComPtr <ITuneRequest> pTuneRequest;
	if (FAILED(hr = graphTools.CreateATSCTuneRequest(
		                      pCapInfo->pTuningSpace,  pTuneRequest, 
							  -1, -1, lPhysicalChannel, lFrequency )))
	{
		flog( ("native.log", "Failed to create a ATSC Tune Request for channe %d, hr=0x%x.\n", lPhysicalChannel, hr ) ); 
		return -1;
	}
	try
	{

		if (FAILED(hr = pCapInfo->pTuner->put_TuneRequest(pTuneRequest)))
		{
			flog( ("native.log", "Cannot submit ATSC tune request for channel %d, hr=0x%x.\n",  lPhysicalChannel, hr ) );
			return -1;
		}
		else
		{
			flog( ("native.log", "Changed ATSC Channel %d (%d)\n", lPhysicalChannel, lFrequency ) );
			pCapInfo->dwTuneState = 1;
			
		}
	}
	catch (...)
	{
		flog( ("native.log", "Exception for changing ATSC Channel %d, hr=0x%x\n", lPhysicalChannel, hr ) );
		return -1;
	}

    return 1;
}

HRESULT  tuneATSCQAMChannel( DShowCaptureInfo* pCapInfo, long lPhysicalChannel, unsigned long lFrequency, long lModulation )
{
    HRESULT hr = S_OK;

	if ( pCapInfo->pDebugSrcSink != NULL )  //when debugsrc is on, always bypass tune.
		return 1;
	//its's a virtual tuner, skip;
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
		return 1;


	lFrequency /= 1000;

    // create tune request
	CComPtr <ITuneRequest> pTuneRequest;
	if (FAILED(hr = graphTools.CreateQAMTuneRequest(
		                      pCapInfo->pTuningSpace,  pTuneRequest, 
							  lPhysicalChannel, lFrequency, lModulation )))
	{
		flog( ("native.log", "Failed to create a QAM Tune Request for channe %d frq:%d mod:%d hr=0x%x.\n", lPhysicalChannel, lFrequency, lModulation, hr ) ); 
		return -1;
	}
	try
	{
		if (FAILED(hr = pCapInfo->pTuner->Validate(pTuneRequest)))
		{
			flog( ("native.log", "Validate QAM tune request for channel %d, frq:%d mod:%d failed hr=0x%x.\n",  lPhysicalChannel, lFrequency, lModulation, hr ) );
		}

		if (FAILED(hr = pCapInfo->pTuner->put_TuneRequest(pTuneRequest)))
		{
			flog( ("native.log", "Cannot submit QAM tune request for channel %d, frq:%d mod:%d hr=0x%x.\n",  lPhysicalChannel, lFrequency, lModulation, hr ) );
			return -1;
		}
		else
		{
			//IBDA_DeviceControl*	BDA_DeviceControl;
	 		//hr = pCapInfo->pBDATuner->QueryInterface( IID_IBDA_DeviceControl, (void **) &BDA_DeviceControl);
			//if ( hr == S_OK )
			//{
			//	hr = BDA_DeviceControl->CheckChanges();
			//	if ( hr == S_OK )
			//		hr = BDA_DeviceControl->CommitChanges();
			//}
			flog( ("native.log", "Changed QAM Channel %d frq:%d mod:%d \n", lPhysicalChannel, lFrequency, lModulation ) );
			pCapInfo->dwTuneState = 1;
		}
	}
	catch (...)
	{
		flog( ("native.log", "Exception for changing QAM Channel %d, hr=0x%x\n", lPhysicalChannel, hr ) );
		return -1;
	}

    return 1;
}

//extern "C" { int  SwitchCamChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId ); }
int TuneQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhysical, unsigned long freq, long lMod, long lInversion, bool* bLocked );
int ScanChannelQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhysical, unsigned long freq, long* lMod, long* lInversion, bool* bLocked );
int  tuneQAMChannel( DShowCaptureInfo* pCapInfo, QAM_FREQ* qam, int bDryRun  )
{
    HRESULT hr = S_OK;
	int ret;
	bool bLocked=false;
	if ( pCapInfo->pDebugSrcSink != NULL )  //when debugsrc is on, always bypass tune.
		return 1;

	//its's a virtual tuner, skip;
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
	{
		pCapInfo->dwTuneState = 1;
		return 1;
	}


	if ( qam == NULL )
		return 0;

	if ( bDryRun ) return 1;


	//SetupTunerFreq(  pCapInfo, qam->frequency/1000, -1, -1 );  //Silcondust HDHR
	SetupTunerModulator(  pCapInfo, qam->modulation, -1, -1, -1, -1, -1, qam->inversal );

	if ( qam->modulation > 0 || qam->inversal > 0 )
	{
		ret = TuneQAM( NULL, pCapInfo, qam->physical_ch, qam->frequency, (long)qam->modulation, (long)qam->inversal, &bLocked );
	}
	else
	{
		long mod, inv;
		mod = qam->modulation;
		inv = qam->inversal;
		ret = ScanChannelQAM( NULL, pCapInfo, qam->physical_ch, qam->frequency, &mod, &inv, &bLocked );
		qam->modulation = (unsigned char)mod;
		qam->inversal   = (unsigned char)inv;
		SetupTunerModulator(  pCapInfo, qam->modulation, -1, -1, -1, -1, -1, qam->inversal );
	}

    return bLocked;
}


int tuneDVBTFrequency( DShowCaptureInfo* pCapInfo, DVB_T_FREQ* dvbt, int bDryRun  )
{
    HRESULT hr = S_OK;
	long freq, band;

	freq = dvbt->frequency/1000;  //BDA driver in KHz
	band = dvbt->bandwidth;

	if ( pCapInfo->pDebugSrcSink != NULL )  //when debugsrc is on, always bypass tune.
		return 1;
	//its's a virtual tuner, skip;
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
	{
		pCapInfo->dwTuneState = 1;
		return 1;
	}

	if ( bDryRun ) return 1;

    // create tune request
	CComPtr <ITuneRequest> pTuneRequest;

	if (FAILED(hr = graphTools.CreateDVBTTuneRequest(
								pCapInfo->pTuningSpace, pTuneRequest, freq, band )))

	{
		flog( ("native.log", "Failed to create a DVB-T Tune Request for freq %d band :%d, hr=0x%x.\n", freq, band, hr ) ); 
		return -1;
	}
	try
	{
		SetupTunerFreq( pCapInfo, freq*1000, band, -1 );
		if (FAILED(hr = pCapInfo->pTuner->put_TuneRequest(pTuneRequest)))
		{
			flog( ("native.log", "Cannot submit DVB-T tune request for freq %d band :%d, hr=0x%x.\n", freq, band, hr ) );
			return -1;
		}
		else
		{
			flog( ("native.log", "Successful Changing DVB-T freq %d band :%d \n", freq, band ) );
			pCapInfo->dwTuneState = 1;
		}
	}
	catch (...)
	{
		flog( ("native.log", "Exception for changing DVB-T freq %d band :%d, hr=0x%x\n", freq, band, hr ) );
		return -1;
	}

	return 1;
}

int tuneDVBCFrequency( DShowCaptureInfo* pCapInfo, DVB_C_FREQ* dvbc, int bDryRun  )
{
    HRESULT hr = S_OK;
	long freq, symrate, modulation;
	int innerFEC, innerFECRate, outerFEC, outerFECRate;

	if ( pCapInfo->pDebugSrcSink != NULL )  //when debugsrc is on, always bypass tune.
		return 1;
	//its's a virtual tuner, skip;
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
	{
		pCapInfo->dwTuneState = 1;
		return 1;
	}

	if ( bDryRun ) return 1;

	freq       = dvbc->frequency/1000;  //BDA driver in KHz
	symrate    = dvbc->symbol_rate;
	modulation = dvbc->modulation;
	innerFEC = -1;
	if ( dvbc->fec_inner != 255 )
		innerFEC     = dvbc->fec_inner;
	innerFECRate = -1;
	if ( dvbc->fec_inner_rate != 255 )
		innerFECRate = dvbc->fec_inner_rate;
	outerFEC  = -1;
	if ( dvbc->fec_outer != 255 )
		outerFEC     = dvbc->fec_outer;
	outerFECRate = -1;
	if ( dvbc->fec_outer_rate != 255 )
		outerFECRate = dvbc->fec_outer_rate;
	


	
	/* ** FEC rate:
	1 Indicates that the rate is 1/2.  
	2 Indicates that the rate is 2/3.  
	3 Indicates that the rate is 3/4.  
	4 Indicates that the rate is 3/5.  
	5 Indicates that the rate is 4/5.  
	6 Indicates that the rate is 5/6.  
	7 Indicates that the rate is 5/11. 
	8 Indicates that the rate is 7/8.  
	9 Indicates the maximum rate.   
     ** FEC of inner/outer:
	1 The FEC is a Viterbi Binary Convolution.        
	2 The FEC is Reed-Solomon 204/188 (outer FEC). 
	3 The FEC is the maximum.                             
	*/

    // create tune request
	CComPtr <ITuneRequest> pTuneRequest;
	if (FAILED(hr = graphTools.CreateDVBCTuneRequest(
								pCapInfo->pTuningSpace, pTuneRequest, freq, symrate, modulation,
								innerFEC, innerFECRate, outerFEC, outerFECRate )))

	{
		flog( ("native.log", "Failed to create a DVB-T Tune Request for freq %d symrate :%d, hr=0x%x.\n", freq, symrate, hr ) ); 
		return -1;
	}
	try
	{
		SetupTunerFreq( pCapInfo, freq*1000, -1, -1 );
		SetupTunerModulator( pCapInfo, modulation, symrate, innerFEC, innerFECRate, outerFEC, outerFECRate, -1 );

		if (FAILED(hr = pCapInfo->pTuner->put_TuneRequest(pTuneRequest)))
		{
			flog( ("native.log", "Cannot submit DVB-C tune request for freq %d symrate :%d, hr=0x%x.\n", freq, symrate, hr ) );
			return -1;
		}
		else
		{
			flog( ("native.log", "Successful changing DVB-C freq:%d symrate:%d mod:%d fec_in:%d fec_in_rate:%d fec_out:%d fec_out_rate:%d hr=0x%x\n", 
			             freq, symrate, modulation, innerFEC, innerFECRate, outerFEC, outerFECRate, hr ) );
			pCapInfo->dwTuneState = 1;
		}
	}
	catch (...)
	{
		flog( ("native.log", "Exception for changing DVB-C freq:%d symrate:%d mod:%d fec_in:%d fec_in_rate:%d fec_out:%d fec_out_rate:%d hr=0x%x\n", 
			             freq, symrate, modulation, innerFEC, innerFECRate, outerFEC, outerFECRate, hr ) );
		return -1;
	}

	return 1;
}

static char* fec_rate[]= { "fec-1/2", "fec-2/3", "fec-3/4", "fec-3/5", "fec-4/5", "fec-5/6", "fec-5/11", "fec-7/8", "fec-1/4", 
                           "fec-1/3", "fec-2/5", "fec-6/7", "fec-8/9",  "fec-9/10", "max", 0 };
static char* fec[]= { "Viterbi Binary Convolution", "RS 204/188", "LDPC", "BCH", "RS 147/130", "max", 0 };
int IsFireDTV( DShowCaptureInfo* pCapInfo );
int IsHauppaugeDiSEQCSupport( DShowCaptureInfo* pCapInfo );
int tuneDVBSFrequency( DShowCaptureInfo* pCapInfo, DVB_S_FREQ* dvbs, int bDryRun  )
{
    HRESULT hr = S_OK;
	char *in_fec_rate="default";
	char *in_fec="default";
	char *out_fec_rate="default";
	char *out_fec="default";


	long freq, symrate, pol, modulation;
	int innerFEC, innerFECRate, outerFEC, outerFECRate;
	unsigned short sat_no;
	unsigned char  pilot=0;
	unsigned char  roll_off=0;

	if ( pCapInfo->pDebugSrcSink != NULL )  //when debugsrc is on, always bypass tune.
		return 1;
	//its's a virtual tuner, skip;
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
	{
		pCapInfo->dwTuneState = 1;
		return 1;
	}

	if ( bDryRun ) return 1;
	if ( dvbs->frequency == 0  )return 1;

	freq       = dvbs->frequency/1000;  //BDA driver in KHz
	symrate    = dvbs->symbol_rate;
	pol		   = dvbs->polarisation;
	modulation = dvbs->modulation;
	innerFEC     = dvbs->fec_inner;
	innerFECRate = dvbs->fec_inner_rate;
	outerFEC     = dvbs->fec_outer;
	outerFECRate = dvbs->fec_outer_rate;
	sat_no = dvbs->sat_no;

	if ( innerFEC >0 && innerFEC <= 6 )
		in_fec_rate=fec[innerFEC-1];
	else
		innerFEC = -1;

	if ( outerFEC >0 && outerFEC <= 6 )
		out_fec_rate=fec[outerFEC-1];
	else
		outerFEC = -1;

	if ( innerFECRate > 0 && innerFECRate <= 15 )
		in_fec_rate = fec_rate[innerFECRate-1];
	else
		innerFECRate = -1;

	if ( outerFECRate > 0 && outerFECRate <= 15 )
		out_fec_rate = fec_rate[outerFECRate-1];
	else
		outerFECRate = -1;

	if ( dvbs->modulation == 0xffff || dvbs->modulation == 0 || modulation == 255  )
		modulation = BDA_MOD_QPSK;

	if ( modulation == 30 || modulation == 31 || modulation == 32 )  //unknown modualtion that comes from SI parser
	{
		if ( IsFireDTV(pCapInfo) || IsHauppaugeDiSEQCSupport(pCapInfo) )
		{
			if ( dvbs->pilot == 0xff ) pilot = 0;
			else
			if ( dvbs->pilot == 1 ) pilot = 0x80;
			else
			if ( dvbs->pilot == 0 ) pilot = 0x40;

			if ( dvbs->roll == 20 ) roll_off = 0x10;
			else
			if ( dvbs->roll == 25 ) roll_off = 0x20;
			else
			if ( dvbs->roll == 35 ) roll_off = 0x30;

			innerFECRate = (innerFECRate| pilot | roll_off );    
			if ( modulation == 30 || modulation == 31 )	{ modulation =  BDA_MOD_NBC_QPSK; in_fec_rate = "DVB-S2, QPSK"; }
			else
			if ( modulation == 32 )	{ modulation =  BDA_MOD_NBC_8PSK; in_fec_rate = "DVB-S2, 8PSK"; }

		} else
		{
			modulation =  BDA_MOD_8VSB; //Technotrebd BDA borrows 8VSD for 8PSK for DVB-S2
			in_fec_rate = "DVB-S2, 8VSB";
		}
	}

	/* ** FEC rate:
	1 Indicates that the rate is 1/2.  
	2 Indicates that the rate is 2/3.  
	3 Indicates that the rate is 3/4.  
	4 Indicates that the rate is 3/5.  
	5 Indicates that the rate is 4/5.  
	6 Indicates that the rate is 5/6.  
	7 Indicates that the rate is 5/11. 
	8 Indicates that the rate is 7/8.  
	9 Indicates the maximum rate.   
     ** FEC of inner/outer:
	1 The FEC is a Viterbi Binary Convolution.        
	2 The FEC is Reed-Solomon 204/188 (outer FEC). 
	3 The FEC is the maximum.                             
	*/


	SendDiSEQCCmd( pCapInfo, dvbs->frequency, pol, sat_no );

    // create tune request
	CComPtr <ITuneRequest> pTuneRequest;

	if (FAILED(hr = graphTools.CreateDVBSTuneRequest(
								pCapInfo->pTuningSpace, pTuneRequest, freq, symrate, pol, modulation,
								innerFEC, innerFECRate, outerFEC, outerFECRate )))
	{
		flog( ("native.log", "Failed to create a DVB-S Tune Request for freq %d symrate:%d, fec_in:%s, fec_in_rate:%s(%d), fec_out:%s, fec_out_rate:%s, pol:%d, hr=0x%x.\n", 
				     freq, symrate, in_fec, in_fec_rate, dvbs->fec_inner_rate, out_fec, out_fec_rate,  pol,  hr ) );

		return -1;
	}
	try
	{
		//SetupTunerNIM( pCapInfo, freq*1000, modulation, symrate );
		SetupTunerFreq( pCapInfo, freq*1000, -1, pol );
		SetupTunerModulator(  pCapInfo, modulation, symrate, innerFEC, innerFECRate, outerFEC, outerFECRate, -1 );

		if (FAILED(hr = pCapInfo->pTuner->put_TuneRequest(pTuneRequest)))
		{
			flog( ("native.log", "Cannot submit DVB-S tune request for freq %d symrate:%d mod:%d fec_in:%s fec_in_rate:%s fec_out:%s fec_out_rate:%s pol:%d, hr=0x%x.\n", 
				     freq, symrate, modulation, in_fec, in_fec_rate, out_fec, out_fec_rate, pol,  hr ) );
			return -1;
		}
		else
		{
			flog( ("native.log", "Successful Changing DVB-S tune request for freq %d symrate:%d mod:%d fec_in:%s fec_in_rate:%s(0x%x) fec_out:%s fec_out_rate:%s pol:%d roll:0x%02x pilot:0x%02x hr=0x%x.\n", 
				     freq, symrate, modulation, in_fec, in_fec_rate, innerFECRate, out_fec, out_fec_rate, pol, roll_off, pilot, hr ) );
			pCapInfo->dwTuneState = 1;
		}
	}
	catch (...)
	{
		flog( ("native.log", "Exception for changing DVB-S freq %d symrate :%d, hr=0x%x\n", freq, symrate, hr ) );
		return -1;
	}

	return 1;
}

int SagePacketInputNum( void* Capture );
long EPG_Dumper( void* context, void* mesg, int bytes )
{
  char src[256+16];
  unsigned long time_cur, time_inc;
  DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)context;
  //CHECK_TAG_CAPINFO2
  SPRINTF( src, sizeof(src), "%s-%d", pCapInfo->videoCaptureFilterNameOrg, pCapInfo->videoCaptureFilterNum );
  //printf( "%s", (char*)mesg );
  //slog( ( "EPGINF:(bytes:%d)|%s|\r\n", bytes, mesg ));

  time_cur = (unsigned long)time( NULL );
  time_inc = time_cur - pCapInfo->EPG_post_time;

  if ( epg_log_enabled )
	  flog(( "epg_data.log", "(%d %d %d)%s->%s \n", pCapInfo->EPG_post_counter, time_inc,
						 pCapInfo->EPG_post_flow_rate_ctrl, src, mesg ));

  if ( pCapInfo->EPG_post_flow_rate_ctrl && pCapInfo->EPG_post_counter >= 50 
	   && time_inc >= 1 )
  {
	if ( epg_log_enabled )
		  flog(( "epg_data.log", "an EPG (%d) is dropped due to flow control \n",  pCapInfo->EPG_post_counter ));
	  pCapInfo->EPG_post_counter--; 
	  return 1;
  }

  if ( time_inc >= 1 )
  {
	  pCapInfo->EPG_post_counter = 0;
	  pCapInfo->EPG_post_time = time_cur;
  }

  int ret = postMessage( src, (char*)mesg, bytes, EPG_MSG_TYPE, 100 );
  pCapInfo->EPG_post_counter++;
  return ret;
}

long Program_Dumper( void* context, void* message , int bytes )
{
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)context;
	PROGRAM_DATA* pProgramData = (PROGRAM_DATA*)message;

	if ( bytes != sizeof(PROGRAM_DATA) )
		slog( ( "ERROR:  PROGRAM_DATA block size wrong (%d != %d)!, version between TSSplitter.ax and DShowCapture.dll isn't consistance.\r\n" ,
		             sizeof(PROGRAM_DATA), bytes ) );
	ASSERT( bytes == sizeof(PROGRAM_DATA) );

	int ret = OnCamPMT( pCapInfo, bytes, pProgramData );
	slog( ( "CAM: PMT dumper CapInfo:0x%x pmt bytes:%d data:0x%x ret:%d (%d=%d)\r\n", pCapInfo, pProgramData->pmt_section_bytes, 
		                          pProgramData->pmt_section_data, ret, sizeof(PROGRAM_DATA), bytes ) );

	if ( strstr(  pCapInfo->videoCaptureFilterName, "Silicondust HDHomeRun" ) ) 
	{
		HRESULT hr = SetupTunerProgramFilter( pCapInfo, pProgramData->pids_table.sid );
		slog(( "PID Filter: Set program ID into HDHR programID:0x%x channel:%d \r\n", pProgramData->pids_table.sid, pProgramData->pids_table.tune_id ));
	} 

	return ret;
}

long AVInf_Dumper( void* context, void* mesg , int bytes )
{
  char *pSrc;
  long ret;
  DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)context;
  //CHECK_TAG_CAPINFO2

  if ( pCapInfo == NULL )
	  return -2;

  pSrc = new char[512];
  SPRINTF( pSrc, 512, "%s-%d", pCapInfo->videoCaptureFilterNameOrg, pCapInfo->videoCaptureFilterNum );
  //printf( "%s", (char*)mesg );
  ret = postMessage( pSrc, (char*)mesg, bytes, AV_INF_TYPE, 100 );
  slog( ( "AVINF:(bytes:%d) %s %s\r\n", bytes, pSrc, mesg ));
  delete pSrc;

  return ret; //return 0, if success
}


void SageLockUpParser( void* Capture )
{
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return ;

	HRESULT hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->LockUpParser(  );
		flog(( "native.log", "Lockup parser\n" ));
	}
	SAFE_RELEASE(  pTSParser );
}
void SageUnLockUpParser( void* Capture )
{
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return ;

	HRESULT hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->UnLockUpParser(  );
		flog(( "native.log", "UnLockup parser\n" ));
	}
	SAFE_RELEASE(  pTSParser );
}

//Implement cross-platform interfcae
//export API
int SageTuneATSCChannel( void* Capture, ATSC_FREQ* atsc, int dryTune ) 
{
	int ret;
	DShowCaptureInfo *CDev = (DShowCaptureInfo *)Capture;
	if ( CDev == NULL ) return -1;

	//reset HDHR pid filter
	if ( strstr(  CDev->videoCaptureFilterName, "Silicondust HDHomeRun" ) )
	{
		SageUnLockUpParser( CDev );
		SetupTunerProgramFilter( CDev, -1 );
	}

	TunerPluginATSCCall( CDev, atsc );

	ret = tuneATSCChannel( CDev, atsc, dryTune ); 

	flog(( "native.log", "*********** Tune ATSC Channel %d (ret:%d) ***********\n", atsc->physical_ch, ret ));
	_ClearPIDMap( CDev );
	return ret;
}

int SageTuneQAMFrequency( void* Capture, QAM_FREQ* qam, int dryTune )
{
	int ret;
	DShowCaptureInfo *CDev = (DShowCaptureInfo *)Capture;
	if ( CDev == NULL ) return -1;

	if ( strstr(  CDev->videoCaptureFilterName, "Silicondust HDHomeRun" ) )
	{
		SageUnLockUpParser( CDev );
		SetupTunerProgramFilter( CDev, -1 );
	}

	TunerPluginQAMCall( CDev, qam );

	ret = tuneQAMChannel( CDev, qam, dryTune ); 
	flog(( "native.log", "*********** Tune QAM Channel %d %d (ret:%d)***********\n", 
		   qam->physical_ch, qam->frequency, ret ));

	_ClearPIDMap( CDev );
	return ret;

}

int SageTuneDVBTFrequency( void* Capture, DVB_T_FREQ* dvbt, int dryTune )
{
	int ret;
	DShowCaptureInfo *CDev = (DShowCaptureInfo *)Capture;
	if ( CDev == NULL ) return -1;
	if ( strstr(  CDev->videoCaptureFilterName, "Silicondust HDHomeRun" ) )
	{
		SageUnLockUpParser( CDev );
		SetupTunerProgramFilter( CDev, -1 );
	}

	TunerPluginDVBTCall( CDev, dvbt );

	ret = tuneDVBTFrequency( CDev, dvbt, dryTune );
	flog(( "native.log", "*********** Tune DVB-T Channel %d (ret:%d)***********\n", dvbt->frequency, ret ));
	_ClearPIDMap( CDev );
	return ret;
}

int SageTuneDVBCFrequency( void* Capture, DVB_C_FREQ* dvbc, int dryTune )
{
	int ret;
	DShowCaptureInfo *CDev = (DShowCaptureInfo *)Capture;
	if ( CDev == NULL ) return -1;
	//reset HDHR pid filter
	if ( strstr(  CDev->videoCaptureFilterName, "Silicondust HDHomeRun" ) )
	{
		SageUnLockUpParser( CDev );
		SetupTunerProgramFilter( CDev, -1 );
	}

	TunerPluginDVBCCall( CDev, dvbc );

	ret = tuneDVBCFrequency( CDev, dvbc, dryTune );
	flog(( "native.log", "*********** Tune DVB-C Channel %d (ret:%d)***********\n", dvbc->frequency, ret ));
	_ClearPIDMap( CDev );
	return ret;
}

int SageTuneDVBSFrequency( void* Capture, DVB_S_FREQ* dvbs, int dryTune )
{
	int ret;
	DShowCaptureInfo *CDev = (DShowCaptureInfo *)Capture;
	if ( CDev == NULL ) return -1;
	if ( strstr(  CDev->videoCaptureFilterName, "Silicondust HDHomeRun" ) )
	{
		SageUnLockUpParser( CDev );
		SetupTunerProgramFilter( CDev, -1 );
	}

	TunerPluginDVBSCall( CDev, dvbs );

	ret = tuneDVBSFrequency( CDev, dvbs, dryTune );
	flog(( "native.log", "*********** Tune DVB-S Channel frq:%d mod:%d (ret:%d)***********\n", dvbs->frequency, 
		         dvbs->modulation, ret ));

	_ClearPIDMap( CDev );
	return ret;
}

int SageGetSatelliteTble( void* Capture, SAT_NAME *sat_name, int max_sat )
{
	int i, num;
	DShowCaptureInfo *CDev = (DShowCaptureInfo *)Capture;
	if ( CDev == NULL ) return -1;

	DISEQC *pDiSEQC;
	if ( CDev->pDiSEQC == NULL )
		return 0;

	pDiSEQC = (DISEQC *)CDev->pDiSEQC;
	num = 0;
	for ( i = 0; i<MAX_LNB_SWITCH && num <max_sat; i++ )
	{
		if ( pDiSEQC->lnb[i].sat_no != -1 )
		{
			sat_name[num].sat_no = pDiSEQC->lnb[i].sat_no;
			strncpy( sat_name[num].name, pDiSEQC->lnb[i].name, sizeof(sat_name[num].name) );
			num++;
		}
	}

	if ( num < max_sat ) sat_name[num].sat_no = -1;

	return num; 
}

int SageCheckLocked( void* Capture )
{
	HRESULT hr;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	unsigned char byteVal;
	bool locked = false;
	CComPtr <IBDA_Topology> bdaNetTop;
	ULONG NodeTypes;
	ULONG NodeType[32];
	ULONG Interfaces;
	GUID Interface[32];
	CComPtr <IUnknown> iNode;
	int i;

	if ( Capture == NULL ) return -1;

	//its's a virtual tuner, skip;
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
		return 1;

	if ( pCapInfo->pDebugSrcSink != NULL )  //when debugsrc is on, always set locked.
		return 1;

	if ( pCapInfo->pBDATuner == NULL )
		return 0;

	if (FAILED(hr = pCapInfo->pBDATuner->QueryInterface(&bdaNetTop)))
	{
		flog( ("native.log", "Failed to get bdaNetTop" ) );
	    return 0;
	}

	if (FAILED(hr = bdaNetTop->GetNodeTypes(&NodeTypes, 32, NodeType)))
	{
		bdaNetTop.Release();
		flog( ("native.log", "Failed to get NodeTop" ) );
	    return 0;
	}

	for ( i=0 ; i<(int)NodeTypes ; i++ )
	{
		hr = bdaNetTop->GetNodeInterfaces(NodeType[i], &Interfaces, 32, Interface);
		if (hr == S_OK)
		{
			for ( ULONG j=0 ; j<Interfaces ; j++ )
			{
				if (Interface[j] == IID_IBDA_SignalStatistics)
				{
					hr = bdaNetTop->GetControlNode(0, 1, NodeType[i], &iNode);
					if (hr == S_OK)
					{
						CComPtr <IBDA_SignalStatistics> pSigStats;
						hr = iNode.QueryInterface(&pSigStats);
						if (hr == S_OK)
						{
							if ( SUCCEEDED(hr = pSigStats->get_SignalLocked(&byteVal)))
								locked = byteVal != 0;

							pSigStats.Release();
						}
						iNode.Release();
					}
					break;
				}
			}
		}
	}
	bdaNetTop.Release();
	if ( locked )
		pCapInfo->dwTuneState |= 0x02;
	
	return locked;
}

int SageChangeCAMChannel( void* Capture, int program, int flag )
{
	int ret = 0;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return -1;

	if ( !strncmp( getSourceType( (CHANNEL_DATA*)pCapInfo->channel), "DVB", 3 ) )
		ret = SwitchCamChannel( NULL, pCapInfo, program, flag );
	else
	{
		//slog(( "CAM: Failed switching channel :%d (%s) \r\n", program, getSourceType( (CHANNEL_DATA*)pCapInfo->channel) ));
	}

	return ret;
}

/*
int SagePickupTSChannel( void* Capture, unsigned short channel, unsigned short program, char *channel_name, bool reset )
{
	HRESULT hr;
	BOOL ret = FALSE;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return -1;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else {
		pTSParser->PickupTSChannel( channel, program, channel_name, reset, &ret );
		if ( program > 0 )
			SageChangeCAMChannel( Capture,  program, 0 );
	}
	SAFE_RELEASE(  pTSParser )

	return ret;

}


int SagePickupATSCChannel( void* Capture, unsigned short major, unsigned short minor, unsigned short program, char *channel_name, bool reset )
{
	HRESULT hr;
	BOOL ret = FALSE;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return -1;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->PickupATSCChannel( major, minor, program, channel_name, reset, &ret );
		if ( program > 0 )
			SageChangeCAMChannel( Capture,  program, 0  );
	}
	SAFE_RELEASE(  pTSParser )

	return ret;

}
*/

int SageTVLockTSChannel( void* Capture, void* pTune, int nFlag )
{
	HRESULT hr;
	BOOL ret = FALSE;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return -1;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->LockTSChannel( pTune, &ret );
		unsigned short program;
		program =  getProgramNum( pTune );
		if ( program > 0 )
		{
			int bEncryptedChannel = (nFlag == 3);
			SageChangeCAMChannel( Capture,  program,  bEncryptedChannel );
		}

		if ( !strncmp( pCapInfo->videoCaptureFilterName, "DVBLink Capture", 14 ) ) 
		{
			pTSParser->WaitCleanStream( -1 );
			flog(( "native.log", "DVBLink Capture set wait clean stream\r\n" ));
		}


	}
	SAFE_RELEASE(  pTSParser )

	return ret;
}


int SageTVScanChannel( void* Capture,   void* pTune )
{
	HRESULT hr;
	BOOL ret = FALSE;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return -1;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->ScanChannel( pTune, &ret );
		unsigned short program;
		program =  getProgramNum( pTune );
		if ( program > 0 )
			SageChangeCAMChannel( Capture,  program,  0  );
	}
	SAFE_RELEASE(  pTSParser )

	return ret;
}

int SageTVReleaseScanChannel( void* Capture )
{
	HRESULT hr;
	BOOL ret = FALSE;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return -1;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->ReleaseScanChannel( );
	}
	SAFE_RELEASE(  pTSParser )

	return 1;
}



int SageTVScanChannelState( void* Capture, int* pScanState, int* pFoundChannelNum, int nClock )
{
	HRESULT hr;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	*pScanState = 0;
	*pFoundChannelNum = 0;
	if ( Capture == NULL || pCapInfo->pSplitter == NULL ) return -1;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->ScanChannelState( pScanState, pFoundChannelNum, nClock );
	}
	SAFE_RELEASE(  pTSParser )

	return pScanState > 0;
}

int SageTVScanChannelList( void* Capture, void** ppChannelList )
{
	HRESULT hr;
	BOOL ret = FALSE;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return -1;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->GetScanChannelList( ppChannelList, &ret );
	}
	SAFE_RELEASE(  pTSParser )

	return ret;
}

int SageTVScanTuneList( void* Capture, void** ppNit )
{
	HRESULT hr;
	BOOL ret = FALSE;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return -1;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->GetScanTuneList( ppNit, &ret );
	}
	SAFE_RELEASE(  pTSParser )

	return ret;
}

int SageSelectOuputFormat( void* Capture, int format ) //0:TS; 1:PS
{
	HRESULT hr;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return -1;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->SetOutputFormat( (unsigned short)format );
		flog(( "native.log", "Select output format:%d\n", format ));
	}
	SAFE_RELEASE(  pTSParser )

	return format;
}


int SagePacketInputNum( void* Capture )
{
	HRESULT hr;
	unsigned long PacketNum;
	ITSParser2 *pTSParser = NULL;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return -1;

	PacketNum = -1;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->PacketInputNum( &PacketNum );

	}
	SAFE_RELEASE(  pTSParser )

	return PacketNum;
}


char*  SageGetTunerDeviceName( void* Capture )
{
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return "";

	return pCapInfo->TunerIDName;
}

void SageEnableCAM( void* Capture )
{
	return;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return ;
	EnableCAMPMT( NULL, pCapInfo );

}

void SageDisableCAM( void* Capture )
{
	return;
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return ;
	DisableCAMPMT( NULL, pCapInfo );
}

void SageGetTSDebugInfo( void* Capture, int Cmd, char* Buf, int BufSize )
{
	HRESULT hr;
	ITSParser2 *pTSParser = NULL;

	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return ;
		hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser );
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->GetDebugInfo(  (unsigned short)Cmd, Buf, BufSize );
	}
	SAFE_RELEASE(  pTSParser )
}

void SageStartParser( void* Capture )
{
	HRESULT hr;
	ITSParser2 *pTSParser = NULL;
	
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return ;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser );
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->StartParser(   );
	}

	pCapInfo->EPG_post_counter = 0;
	pCapInfo->EPG_post_time = (unsigned long)time( NULL );

	SAFE_RELEASE(  pTSParser )
}

void SageStopParser( void* Capture )
{
	HRESULT hr;
	ITSParser2 *pTSParser = NULL;

	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return ;

	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser );
	if ( FAILED( hr ) )
		flog(( "native.log", "digital tv query TS parser interface failed\r\n" ));
	else{
		pTSParser->StopParser(   );
	}
	SAFE_RELEASE(  pTSParser )
}

void SageTunerStart( void* Capture )
{
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return ;
	pCapInfo->pMC->Run();
}

void SageTunerStop( void* Capture )
{
	DShowCaptureInfo *pCapInfo = (DShowCaptureInfo *)Capture;
	if ( Capture == NULL ) return ;
	pCapInfo->pMC->Stop();
}

void SageDelay(void* Capture, uint32_t ms)
{
	Sleep( ms );
}

void _ClearPIDMap(  DShowCaptureInfo *pCapInfo )
{
	HRESULT hr;
	CComPtr <IPin> pPin;
	if (  pCapInfo->pBDADemux == NULL ) return;

	if ( SUCCEEDED(hr = graphTools.FindPin( pCapInfo->pBDADemux, L"1", &pPin.p, REQUESTED_PINDIR_OUTPUT )) )
	{
		IMPEG2PIDMap *pIPidMap = NULL;
		hr = pPin->QueryInterface(IID_IMPEG2PIDMap, (void**)&pIPidMap);
		if (SUCCEEDED(hr))
		{
			ULONG i,pid_total = 0;
			ULONG* unmap_pids=NULL; 

			IEnumPIDMap *pIEnumPIDMap;
			if (SUCCEEDED(pIPidMap->EnumPIDMap(&pIEnumPIDMap)))
			{
				ULONG num;
				PID_MAP pPidMap;
				//count pid;
				while( pIEnumPIDMap->Next(1, &pPidMap, &num) == S_OK ) pid_total++;
				unmap_pids = new ULONG[pid_total+1];

				//get pids
				i = 0;
				pIEnumPIDMap->Reset();
				while(pIEnumPIDMap->Next(1, &pPidMap, &num) == S_OK)
				{
					unmap_pids[i++] = pPidMap.ulPID;
				}
				SAFE_RELEASE( pIEnumPIDMap );

				//unmap pid
				if ( FAILED( hr = pIPidMap->UnmapPID( pid_total, unmap_pids ) ) )
				{
					//flog( ("native.log", "Failed to UnmapPID on TIF pin hr=0x%x \r\n", hr ) );
				} else
				{
					flog( ("native.log", "UnmapPID TIF Pin total:%d \r\n", pid_total ) );
				}
				delete unmap_pids;

			} else
				flog( ("native.log", "Failed to get EnumPIDMap on TIF Pin of Dumexlexer hr=0x%x \r\n", hr ) );

			SAFE_RELEASE( pIPidMap );
		}
		else
		{
			flog( ("native.log", "Failed to get IMPEG2PIDMap on Pin-1 to map pid hr=0x%x \r\n", hr ) );
		}
	} else
	{
		flog( ("native.log", "Failed to get Pin-1 to map pid hr=0x%x \r\n", hr ) );
	}

}



static char *strtok_r (char *s, const char *delim, char **save_ptr)
{
  char *token;

  if (s == NULL)
    s = *save_ptr;

  /* Scan leading delimiters.  */
  s += strspn (s, delim);
  if (*s == '\0')
    {
      *save_ptr = s;
      return NULL;
    }

  /* Find the end of the token.  */
  token = s;
  s = strpbrk (token, delim);
  if (s == NULL)
    /* This token finishes the string.  */
    *save_ptr = strchr (token, '\0');
  else
    {
      /* Terminate the token and make *SAVE_PTR point past it.  */
      *s = '\0';
      *save_ptr = s + 1;
    }
  return token;
}

int GetCfgVal( char* Str, char* Name, int MaxNameLen, int* Val, char* Ext, int MaxExtLen )
{
	const char* delima = " \t,;\r\n";
	const char* delima2 = "= \t,;\r\n";
	char *token, *savedptr;
	int ret = 0;
	if ( Name == NULL || Ext == NULL || MaxNameLen == 0 || MaxExtLen == 0 )
		return 0;
		
	Name[0]= *Val= Ext[0] = 0;;
	token = (char*)strtok_r( Str, delima, &savedptr );
	if ( token != NULL ) 
	{
		strncpy( Name, token, MaxNameLen );
		ret++;

	}
	token = (char*)strtok_r( savedptr, delima2, &savedptr );
	if ( token != NULL ) 
	{ 
		if ( token[0] >= '0' && token[0] <= '9' )
		{
			*Val = atoi( token); 
		} else
			strncpy( Ext, token, MaxExtLen ); 
		ret++;
	}
	token = (char*)strtok_r( savedptr, delima, &savedptr );
	if ( token != NULL )
	{
		strncpy( Ext, token, MaxExtLen );
		ret++;
	}
	return ret;
}