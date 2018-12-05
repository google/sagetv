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
#include "../../include/guids.h"
#include "jni-util.h"
#include <initguid.h>
#include <memory.h>
#include <string.h>
#include <streams.h>
#include <ks.h>
#include <ksproxy.h>
#include "guids.h"
#include "sage_DShowCaptureDevice.h"
#include "DShowUtilities.h"
#include "DShowCapture.h"
#include "CAMCtrl.h"
#include "FilterGraphTools.h"
//#include "../ax/TSnative/TSnative.h"
#include "Channel.h"
#include "SageDTV.h"

TV_TYPE GetTVType( DShowCaptureInfo *pCapInfo );

static int InitFireDTV( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int OpenFireDTVCam( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int CloseFireDTVCam( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int ResetFireDTV( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static bool IsFireDTVPresent( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static bool IsFireDTVReady( JNIEnv *env, DShowCaptureInfo* pCapInfo, int* pStaus );
static int FireDTVCamSwitchChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId );
static int SetnFireDTVHWPidFiltering(  JNIEnv *env, DShowCaptureInfo* pCapInfo, unsigned short* pPids, int PidNum );

static int InitAnyseeCAM( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int CloseAnyseeCI( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static void ReleaseAnyseeCI( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int OpenAnyseeCam( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int CloseAnyseeCam( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int AnyseeCamSwitchChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId, int encryptionFlag );
static long OnAnyseePMT(  JNIEnv *env, DShowCaptureInfo* pCapInfo, short bytes, void* mesg );

static void ReleaseTechnoTrend( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int InitTechnoTrend( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int CloseTechnoTrendCI( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int OpenTechnoTrendCI( JNIEnv *env, DShowCaptureInfo* pCapInfo );
static int CloseTechnoTrendCI( JNIEnv *env, DShowCaptureInfo* pCapInfo );

static long OnFireDTVPMT( JNIEnv *env, DShowCaptureInfo* pCapInfo, short bytes, void* mesg );
static long OnTechnoTrendPMT( JNIEnv *env, DShowCaptureInfo* pCapInfo, short bytes, void* mesg );
static int  TechnoTrendCamSwitchChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId );

void DebugMsg( char* msg )
{
	slog(( "CAM-DEBUG:%s\r\n", msg ) ); 
}
void LogFireDTVPMT( unsigned char* p, int bytes, unsigned int servid )
{
	FILE* fp = fopen( "FireDTV_PMT.log", "a+" );
	if ( fp != NULL )
	{
		int i;
		fprintf( fp, "servid:%d pmt size:%d\r\n", servid, bytes );
		for ( i = 0; i<bytes+2+10; i++ )
			fprintf( fp, "%02x ", p[i] );

		fprintf( fp, "\r\n" );
		fclose( fp );
	}
}

int IsCAMInitialized( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	CAM_CTRL_HEADER* header;
	if ( pCapInfo == NULL || pCapInfo->pCamCtrlData == NULL ) 
		return 0;

	header = (CAM_CTRL_HEADER *)pCapInfo->pCamCtrlData; 
	if ( header->pCapInfo == pCapInfo && header->state != CAM_CLOSE )
		return 1;

	return 0;
}

int IsCAMValid( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	CAM_CTRL_HEADER* header;
	if ( pCapInfo == NULL || pCapInfo->pCamCtrlData == NULL ) 
		return 0;

	header = (CAM_CTRL_HEADER *)pCapInfo->pCamCtrlData; 
	if ( header->pCapInfo == pCapInfo && header->state == CAM_NULL )
		return 0;

	return 1;
}

int InitialCAM( JNIEnv *env, DShowCaptureInfo* pCapInfo, char* pDeviceName )
{
	if ( pCapInfo == NULL ) return 0;
	slog(( "CAM:Initialize (tag:%s) CAM/CI ctrl block (ver 1.3) %s-%d 0x%x 0x%x env:0x%x\r\n", pDeviceName, 
		              pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, pCapInfo,
					  pCapInfo->pCamCtrlData, env ));

	if ( pCapInfo->pCamCtrlData != NULL )
	{
		//check if expecteced CAM ctrl block, if not release it
		char* pTag = (char*)pCapInfo->pCamCtrlData;
		if ( memcmp( pTag, pDeviceName, 8 ) )
			ReleaseCAM( env, pCapInfo );
		else
		{
			slog(( "CAM:CAM ctrl block is already initialized \r\n" ));
			return 0;
		}
	}

	if ( pCapInfo->pCamCtrlData == NULL )
	{
		if ( !memcmp( pDeviceName, FIREDTV_CAM, 8 ) )
		{
			FIREDTV *pFireDTV = (FIREDTV *)new char[sizeof(FIREDTV)];
			memset( pFireDTV, 0x0, sizeof(FIREDTV) );
			pCapInfo->pCamCtrlData = pFireDTV;
			memcpy( pFireDTV->header.TAG , pDeviceName, 8 );
			pFireDTV->header.pCapInfo = pCapInfo;
			pFireDTV->header.env = env;
			pFireDTV->header.state = CAM_CLOSE;
			pFireDTV->header.OnPMTEnable = TRUE;
			InitFireDTV( env, pCapInfo );

		} else
		if ( !memcmp( pDeviceName, ANYSEE_CAM, 8 ) )
		{
			ANYSEE *pAnysee = (ANYSEE *)new char[sizeof(ANYSEE)];
			memset( pAnysee, 0x0, sizeof(ANYSEE) );
			pCapInfo->pCamCtrlData = pAnysee;
			memcpy( pAnysee->header.TAG , pDeviceName, 8 );
			pAnysee->header.pCapInfo = pCapInfo;
			pAnysee->header.env = env;
			pAnysee->header.state = CAM_CLOSE;
			pAnysee->header.OnPMTEnable = TRUE;
			InitAnyseeCAM( env, pCapInfo );

		} else
		if ( !memcmp( pDeviceName, TECHNOTREND_CAM, 8 ) )
		{
			TECHNOTREND *pTechnoTrend = (TECHNOTREND *)new char[sizeof(TECHNOTREND)];
			memset( pTechnoTrend, 0x0, sizeof(TECHNOTREND) );
			pCapInfo->pCamCtrlData = pTechnoTrend;
			memcpy( pTechnoTrend->header.TAG , pDeviceName, 8 );
			pTechnoTrend->header.pCapInfo = pCapInfo;
			pTechnoTrend->header.env = env;
			pTechnoTrend->header.state = CAM_CLOSE;
			pTechnoTrend->header.OnPMTEnable = TRUE;
			InitTechnoTrend( env, pCapInfo );
		} else
		if ( !memcmp( pDeviceName, UNKNOWN_CAM, 8 ) )
		{
			CAM_CTRL_HEADER *pHeader = (CAM_CTRL_HEADER*)new char[sizeof(CAM_CTRL_HEADER)];
			memset( pHeader, 0x0, sizeof(CAM_CTRL_HEADER) );
			pCapInfo->pCamCtrlData = pHeader;
			memcpy( pHeader->TAG , pDeviceName, 8 );
			pHeader->pCapInfo = pCapInfo;
			pHeader->env = env;
			pHeader->state = CAM_NULL;
			pHeader->OnPMTEnable = FALSE;
		}
	}
	return 1;
}

int ReleaseCAM( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	if ( pCapInfo == NULL ) return 0;

	char* pTag = (char*)pCapInfo->pCamCtrlData;
	slog(( "CAM:Release CAM ctrl block %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	if ( pCapInfo->pCamCtrlData == NULL ) return 0;

	if ( !memcmp( pTag, FIREDTV_CAM, 8 ) )
	{
		ResetFireDTV( env, pCapInfo );
	} else
	if ( !memcmp( pTag, ANYSEE_CAM, 8 ) )
	{
		CloseAnyseeCI( env, pCapInfo );
		ReleaseAnyseeCI( env, pCapInfo );
	} else
	if ( !memcmp( pTag, TECHNOTREND_CAM, 8 ) )
	{
		CloseTechnoTrendCI( env, pCapInfo );
		ReleaseTechnoTrend( env, pCapInfo );
	}

	delete pCapInfo->pCamCtrlData;
	pCapInfo->pCamCtrlData = NULL;
	return 1;
}

int OpenCAM( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	int ret = 0;
	if ( pCapInfo == NULL ) return 0;

	char* pTag = (char*)pCapInfo->pCamCtrlData;
	slog(( "CAM:Open CAM/CI device %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));

	if ( pCapInfo->pCamCtrlData == NULL ) 
	{
		slog(( "CAM: device is not init\r\n" ));
		return 0;
	}

	if ( !memcmp( pTag, FIREDTV_CAM, 8 ) )
	{
		ret = OpenFireDTVCam( env, pCapInfo );
	} else
	if ( !memcmp( pTag, ANYSEE_CAM, 8 ) )
	{
		ret = OpenAnyseeCam( env, pCapInfo );
	} else
	if ( !memcmp( pTag, TECHNOTREND_CAM, 8 ) )
	{
		ret = OpenTechnoTrendCI( env, pCapInfo );
	} else
	{
		slog(( "CAM:unknown CAM/CI device %s %s-%d\r\n", pTag,
			            pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		ret = -1;
	}

	return ret;
}

int CloseCAM( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	int ret = 0;
	if ( pCapInfo == NULL ) return 0;

	char* pTag = (char*)pCapInfo->pCamCtrlData;
	slog(( "CAM:Close device %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));

	if ( pCapInfo->pCamCtrlData == NULL ) 
	{
		slog(( "CAM: device is not initialized\r\n" ));
		return 0;
	}

	if ( !memcmp( pTag, FIREDTV_CAM, 8 ) )
	{
		ret = CloseFireDTVCam( env, pCapInfo );
	} else
	if ( !memcmp( pTag, ANYSEE_CAM, 8 ) )
	{
		ret = CloseAnyseeCam( env, pCapInfo );
	} else
	if ( !memcmp( pTag, TECHNOTREND_CAM, 8 ) )
	{
		ret = CloseTechnoTrendCI( env, pCapInfo );
	} else
	{
		slog(( "CAM:unknown device %s %s-%d\r\n", pTag,
			            pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		ret = -1;
	}

	return ret;

}

void EnableCAMPMT( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	if ( pCapInfo == NULL ) return;

	char* pTag = (char*)pCapInfo->pCamCtrlData;
	if ( pCapInfo->pCamCtrlData == NULL ) 
		return;

	slog(( "CAM:Enable OnPMT in device %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));


	if ( !memcmp( pTag, FIREDTV_CAM, 8 ) )
	{
		((CAM_CTRL_HEADER*)pCapInfo->pCamCtrlData)->OnPMTEnable = true;
	} else
	if ( !memcmp( pTag, ANYSEE_CAM, 8 ) )
	{
		((CAM_CTRL_HEADER*)pCapInfo->pCamCtrlData)->OnPMTEnable = true;
	} else
	if ( !memcmp( pTag, TECHNOTREND_CAM, 8 ) )
	{
		((CAM_CTRL_HEADER*)pCapInfo->pCamCtrlData)->OnPMTEnable = true;
	} else
	{
		slog(( "CAM:unknown device %s %s-%d\r\n", pTag,
			            pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	}
}

void DisableCAMPMT( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	if ( pCapInfo == NULL ) return;

	char* pTag = (char*)pCapInfo->pCamCtrlData;
	if ( pCapInfo->pCamCtrlData == NULL ) 
		return;

	slog(( "CAM:Disable OnPMT in device %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));

	if ( !memcmp( pTag, FIREDTV_CAM, 8 ) )
	{
		((CAM_CTRL_HEADER*)pCapInfo->pCamCtrlData)->OnPMTEnable = false;
	} else
	if ( !memcmp( pTag, ANYSEE_CAM, 8 ) )
	{
		((CAM_CTRL_HEADER*)pCapInfo->pCamCtrlData)->OnPMTEnable = false;
	} else
	if ( !memcmp( pTag, TECHNOTREND_CAM, 8 ) )
	{
		((CAM_CTRL_HEADER*)pCapInfo->pCamCtrlData)->OnPMTEnable = false;
	} else
	{
		slog(( "CAM:unknown device %s %s-%d\r\n", pTag,
			            pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	}
}

int  SwitchCamChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId, int encryptionFlag )
{
	int ret = 0;
	if ( pCapInfo == NULL ) return 0;

	char* pTag = (char*)pCapInfo->pCamCtrlData;

	if ( pCapInfo->pCamCtrlData == NULL ) 
	{
		slog(( "CAM: device is not CAM device for switching channel\r\n" ));
		return 0;
	}

	//slog(( "CAM: switch channel service id:%d %s-%d\r\n", serviceId,
	//	pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));

	if ( !memcmp( pTag, FIREDTV_CAM, 8 ) )
	{
		ret = FireDTVCamSwitchChannel( env, pCapInfo, serviceId );
	} else
	if ( !memcmp( pTag, ANYSEE_CAM, 8 ) )
	{
		ret = AnyseeCamSwitchChannel( env, pCapInfo, serviceId, encryptionFlag );
	} else
	if ( !memcmp( pTag, TECHNOTREND_CAM, 8 ) )
	{
		ret = TechnoTrendCamSwitchChannel( env, pCapInfo, serviceId );
	} else
	{
		slog(( "CAM:unknown device %s %s-%d\r\n", pTag,
			            pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		ret = -1;
	}
	return 1;
}

long OnCamPMT( void* context, short bytes, void* mesg )
{
	int ret = 0;
	JNIEnv *env;
	DShowCaptureInfo* pCapInfo;

	if ( context == NULL )
		return 0;

	pCapInfo = (DShowCaptureInfo*)context;
	CAM_CTRL_HEADER* pCamCtrlHeader = (CAM_CTRL_HEADER*)pCapInfo->pCamCtrlData;

	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
		return 0;
//slog( ( "CAM:trace A CapInfo:0x%x 0x%x\r\n", pCapInfo, pCamCtrlHeader ));
//if ( pCamCtrlHeader != NULL ){ slog(( "CAM:trace B CapInfo:0x%x %s env:0x%x\r\n", pCamCtrlHeader->pCapInfo, pCamCtrlHeader->TAG,  pCamCtrlHeader->env )); }

	//check if it's valid
	if ( pCamCtrlHeader != NULL && pCamCtrlHeader->pCapInfo != pCapInfo )
	{
		return 0;
	}

	if ( pCamCtrlHeader == NULL || pCamCtrlHeader->env == NULL )
	{
		return 0;
	}

	env = (JNIEnv*)pCamCtrlHeader->env;

	if (  pCapInfo->pBDATuner == NULL )
	{
		 slog(( "CAM: BDA tuner is not ready %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                               pCapInfo->videoCaptureFilterNum ));
		return 0;
	}

	//if ( !pCamCtrlHeader->OnPMTEnable ) //MarkSS
	//	return 0;

	if ( !memcmp( pCamCtrlHeader->TAG, TECHNOTREND_CAM, 8 ))
	{
		ret = OnTechnoTrendPMT( env, pCapInfo, bytes, mesg );
	} else
	if ( !memcmp( pCamCtrlHeader->TAG, FIREDTV_CAM, 8 ) )
	{
		ret = OnFireDTVPMT( env, pCapInfo, bytes, mesg );
	} else
	if ( !memcmp( pCamCtrlHeader->TAG, ANYSEE_CAM, 8 ) )
	{
		ret = OnAnyseePMT( env, pCapInfo, bytes, mesg );
	} else
	{
		return 0;
		 slog(( "CAM: Unknown card type for OnCamPTM: unknown name %s-%d\r\n", 
														   pCapInfo->videoCaptureFilterName, 
			                                               pCapInfo->videoCaptureFilterNum ));
	}

	return ret;
}


//////////////////////////////////////////////////////////////////////////////
//FireDTV CI 
//////////////////////////////////////////////////////////////////////////////
static int InitFireDTV( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	if ( !IsFireDTVPresent( env, pCapInfo ) )
	{
		slog(( "CAM:can't FireDTV CAM/CI is not present on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return 0;
	}
	return 1;
}

static int GetFireDTVStatus( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	HRESULT hr;
	IKsPropertySet *pIKsPropSet = NULL;
	unsigned short CI_Status=0;
	if ( pCapInfo == NULL || pCapInfo->pBDATuner == NULL )
		return -1;
	
	hr = pCapInfo->pBDATuner->QueryInterface( IID_IKsPropertySet, (void**)&pIKsPropSet );
	if (!SUCCEEDED(hr))
	{
		SAFE_RELEASE( pIKsPropSet );
		slog(( "CAM:can't find FireDTV KsPropertySet interface on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	}

	//hr = pIKsPropSet->Set( IID_KSPROPSETID_FIREDTV, KSPROPERTY_FIRESAT_TEST_INTERFACE, NULL, 0, NULL, 0 );
	//if (!SUCCEEDED(hr))
	//{
	//	SAFE_RELEASE( pIKsPropSet );
	//	slog(( "CAM:can't test FireDTV CAM/CI interface to check status hr:0x%x %s-%d\r\n", hr,
	//		    pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	//	return -1;
	//}

	char data1[1036], data2[1036];
	DWORD dwRteurnBytes = 0;
	
	hr = pIKsPropSet->Get( IID_KSPROPSETID_FIREDTV, KSPROPERTY_FIRESAT_GET_CI_STATUS, data1, sizeof(data1),
						data2, sizeof(data2), &dwRteurnBytes );

	if ( !SUCCEEDED(hr) )
	{
		SAFE_RELEASE( pIKsPropSet );
		slog(( "CAM:can't Get FireDTV CI status hr:0x%x %s-%d\r\n", hr,
			pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
         if ( hr = 0x8007001F )
          {
            if ( ResetFireDTV( env, pCapInfo ) <= 0 )
			{
				SAFE_RELEASE( pIKsPropSet );
				return -1;
			}
	
			//hr = pIKsPropSet->Get( IID_KSPROPSETID_FIREDTV, KSPROPERTY_FIRESAT_GET_CI_STATUS, &Data, sizeof(Data),
			//				&CI_Status, sizeof(CI_Status), &dwRteurnBytes );
			hr = pIKsPropSet->Get( IID_KSPROPSETID_FIREDTV, KSPROPERTY_FIRESAT_GET_CI_STATUS, data1, sizeof(data1),
						data2, sizeof(data2), &dwRteurnBytes );

            if ( !SUCCEEDED(hr) )
            {
				slog(( "CAM:can't Get FireDTV CI status second try, hr:0x%x %s-%d\r\n", hr,
					   pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
				SAFE_RELEASE( pIKsPropSet );
				return -1;
            }
         } else
		 {
			SAFE_RELEASE( pIKsPropSet );
			return -1;
		 }
	}
	
	CI_Status = ( data2[1] << 8 ) | data2[0] ; 
	SAFE_RELEASE( pIKsPropSet );
	return CI_Status;
}

static bool IsFireDTVReady( JNIEnv *env, DShowCaptureInfo* pCapInfo, int* pStatus )
{
	int status = GetFireDTVStatus( env, pCapInfo );
	*pStatus = status;

	if ( status == -1 )
		return false;

	if ( status & CI_MODULE_ERROR )
		return false;

	if ( ( status & ( CI_MODULE_PRESENT | CI_MODULE_IS_DVB | CI_MODULE_INIT_READY ) )
		   ==  ( CI_MODULE_PRESENT | CI_MODULE_IS_DVB | CI_MODULE_INIT_READY ) )
		return true;

	return false;

}

static bool IsFireDTVPresent( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	int status = GetFireDTVStatus( env, pCapInfo );
	slog(( "CAM:Check FireDTV CI Present, status: 0x%x  %s-%d\r\n", status, 
		                              pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	if ( status == -1 )
		return false;
	
	if ( ( status & ( CI_MODULE_PRESENT | CI_MODULE_IS_DVB)) == ( CI_MODULE_PRESENT | CI_MODULE_IS_DVB) )
		return true;

	return false;
}

int ResetFireDTV( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	HRESULT hr;
	IKsPropertySet *pIKsPropSet = NULL;
	bool bCIPresent = false;

	if ( pCapInfo == NULL || pCapInfo->pBDATuner == NULL )
		return 0;
	
	hr = pCapInfo->pBDATuner->QueryInterface( IID_IKsPropertySet, (void**)&pIKsPropSet );
	if (!SUCCEEDED(hr))
	{
		SAFE_RELEASE( pIKsPropSet );
		slog(( "CAM:can't find FireDTV KsPropertySet interface to reset CAM on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return 0;
	}

	unsigned char CAData[1036]={0}, Instance[1036] = {0};
	int offs = 0, uLength = 1;

    CAData[offs] = 0; offs++;//slot
    CAData[offs] = 0; offs++;//utag (CA RESET)

    CAData[offs] = 0; offs++;//padding
    CAData[offs] = 0; offs++;//padding

    CAData[offs] = 0; offs++;//bmore (FALSE)

    CAData[offs] = 0; offs++;//padding

    CAData[offs] = 0; offs++;//padding
    CAData[offs] = 0; offs++;//padding

    CAData[offs] = (byte)(uLength % 256); offs++;		//ulength lo
    CAData[offs] = (byte)(uLength / 256); offs++;		//ulength hi

    CAData[offs] = 0; offs++;// HW Reset of CI part
	memcpy( Instance, CAData, sizeof(Instance) );

	hr = pIKsPropSet->Set( IID_KSPROPSETID_FIREDTV, KSPROPERTY_FIRESAT_HOST2CA, 
		Instance, sizeof(Instance), CAData, sizeof(CAData) );
	if (!SUCCEEDED(hr))
	{
		slog(( "CAM:can't Reset FireDTV CAM/CI hr:0x%x %s-%d\r\n", hr,
			pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		SAFE_RELEASE( pIKsPropSet );
		return 0;
	}
	slog(( "CAM:Reset FireDTV CAM/CI successful on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	SAFE_RELEASE( pIKsPropSet );
	return 1;
}

static int OpenFireDTVCam( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	int status;
	if ( pCapInfo == NULL  )
		return 0;

	FIREDTV *pFireDTV = (FIREDTV *)pCapInfo->pCamCtrlData;
	if ( pFireDTV == NULL )
	{
		slog(( "CAM:Error: FireDTV is not initailized to Enable CAM on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return 0;
	}

	if ( pFireDTV->header.state != CAM_CLOSE )
	{
		slog(( "CAM:CAM is open already. %s-%d \r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return 0;
	}

	if ( !IsFireDTVReady( env, pCapInfo, &status ) )
	{
		slog(( "CAM:FireDTV CAM/CI not Ready, status: 0x%x  %s-%d\r\n", status, 
		                              pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));

		if ( ResetFireDTV( env, pCapInfo ) <= 0 )
		{
			slog(( "CAM:Error: FireDTV can't be reset %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
			return 0;
		}
	}

	memset( &pFireDTV->ca_data, 0, sizeof(pFireDTV->ca_data) );
	slog(( "CAM:FireDTV CAM/CI is open,  %s-%d\r\n",  pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	pFireDTV->header.state = CAM_OPEN;
	pFireDTV->header.OnPMTEnable = true;
	
	return 1;
}

static int CloseFireDTVCam( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	FIREDTV *pFireDTV;

	if ( pCapInfo == NULL  )
		return -1;

	pFireDTV = (FIREDTV *)pCapInfo->pCamCtrlData;
	if ( pFireDTV == NULL )
	{
		slog(( "CAM:Error: FireDTV's CAM/CI is not initailized on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	}

	ResetFireDTV( env, pCapInfo );
	pFireDTV->header.state = CAM_CLOSE;
	slog(( "CAM:FireDTV's CAM/CI closed\r\n" ));
	return 1;
}

static int FireDTVCamSwitchChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId )
{
	FIREDTV *pFireDTV;

	if ( pCapInfo== NULL  )
		return -1;

	pFireDTV = (FIREDTV *)pCapInfo->pCamCtrlData;
	if ( pFireDTV == NULL )
	{
		slog(( "CAM:Error: FireDTV's CAM/CI is not initailized on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	}

	if ( memcmp( pFireDTV->header.TAG, FIREDTV_CAM, 8 ) || pFireDTV->header.state == CAM_CLOSE )
	{
//ZQ remove after debug
slog(( "CAM: Failed switching channel %d FireDTV %d\r\n", serviceId, pFireDTV->header.state ));
		return 0;
	}
	int status = GetFireDTVStatus( env, pCapInfo );
	pFireDTV->serviceID = serviceId;
	pFireDTV->header.state = CAM_PENDING;
	slog(( "CAM: Switch channel %d FireDTV '%s-%d' (status:0x%x)\r\n", serviceId,
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, status ));

	return 1;
}
long OnFireDTVPMT(  JNIEnv *env, DShowCaptureInfo* pCapInfo, short bytes, void* mesg )
{
	FIREDTV *pFireDTV;
	IKsPropertySet *pIKsPropSet;
	int status;
	HRESULT hr;

	if ( env == NULL || pCapInfo == NULL )
		return 0;

	pFireDTV = (FIREDTV *)pCapInfo->pCamCtrlData;
	if ( pFireDTV == NULL ) 
		return 0;

	if ( memcmp( pFireDTV->header.TAG, FIREDTV_CAM, 8 ) || pFireDTV->header.state == CAM_CLOSE )
		return 0;

	PROGRAM_DATA *program = ( PROGRAM_DATA *)mesg;

	//slog(( "CAM:PMT: service:%d, pid:%d pmt size:%d, pmt:0x%x ( size:%d mesg:0x%x) state:%d\r\n", 
	//	    section->service, section->pid, section->length, section->data,	bytes, mesg, pFireDTV->header.state ));


	//ZQ updated on 5/30/09
	//section->data[6] = 0; //section_number
	//section->data[7] = 0; //last_section_number

	if ( program->pids_table.sid != pFireDTV->serviceID )
	{
		//slog(( "CAM:is not expected PMT %d %d drope it\r\n", section->service, pFireDTV->serviceID )); //ZQ debug test remove
		return 0;
	} 

	if ( pFireDTV->header.state != CAM_PENDING )
	{
		//slog(( "CAM: state isn't in pending %d\r\n", pFireDTV->header.state )); //ZQ debug test remove
		return 0;
	}

	if ( !IsFireDTVReady( env, pCapInfo, &status ) )
	{
		if ( ResetFireDTV( env, pCapInfo ) <= 0 )
		{
			slog(( "CAM:FireDTV CAM/CI is not Ready, status: 0x%x  %s-%d\r\n", status, 
	                pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
			return 0;
		}
	}

	if ( program->pmt_section_data == NULL || program->pmt_section_bytes == 0 )
	{
		slog(( "CAM:PMT data pointer is null, drop PMT data. \r\n" ));
		return 0;
	}
	if ( program->pmt_section_bytes > MAX_PMT_SIZE-12 )
	{
		slog(( "CAM:PMT data size overflow, drop PMT data. %d \r\n", program->pmt_section_bytes ));
		return 0;
	}

	//TS_PMT *pmt = (TS_PMT *)section->tbl;
	//slog(( "CAM:PMT tbl_id:%d : pid num:%d, Nr:%d LastNr:%d, length:%d status:0x%x\r\n", 
	//	    pmt->TableId, pmt->NumStreams, pmt->SectionNum, pmt->LastSectionNum, section->length, status  ));

	//if ( pFireDTV->header.state == CAM_PENDING )
	//{
	//	SetnFireDTVHWPidFiltering( env, pCapInfo, pmt->ElementaryPID, pmt->NumStreams );
	//}

	unsigned char* p = (unsigned  char*)pFireDTV->ca_data.data1;
	unsigned char* s = (unsigned  char*)pFireDTV->ca_data.data2;
	memset( p, 0, MAX_PMT_SIZE );

    int offs = 0, length = 2+program->pmt_section_bytes;

    p[offs] = 0; offs++;//slot
    p[offs] = 2; offs++;//utag

    p[offs] = 0; offs++;//padding
    p[offs] = 0; offs++;//padding

    p[offs] = 0; offs++;//bmore

    p[offs] = 0; offs++;//padding

    p[offs] = 0; offs++;//padding
    p[offs] = 0; offs++;//padding

    p[offs] = (unsigned char)(length % 256); offs++;		//length lo
    p[offs] = (unsigned char)(length / 256); offs++;		//length hi

    p[offs] = 3; offs++;// List Management = ONLY
    p[offs] = 1; offs++;// pmt_cmd = OK DESCRAMBLING		

	memcpy( p+offs, program->pmt_section_data, program->pmt_section_bytes );
	*(p+offs+6) = 0x0; //section->data[6] = 0; //section_number
	*(p+offs+7) = 0x0; //section->data[7] = 0; //last_section_number

	memcpy( s, p, MAX_PMT_SIZE );

	hr = pCapInfo->pBDATuner->QueryInterface( IID_IKsPropertySet, (void**)&pIKsPropSet );
	if (!SUCCEEDED(hr))
	{
		SAFE_RELEASE( pIKsPropSet );
		slog(( "CAM:can't find FireDTV KsPropertySet interface to send CAM cmd on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return 0;
	}

	hr = pIKsPropSet->Set( IID_KSPROPSETID_FIREDTV, KSPROPERTY_FIRESAT_HOST2CA, 
		                   s, MAX_PMT_SIZE, p, MAX_PMT_SIZE );

	SAFE_RELEASE( pIKsPropSet );
	if (!SUCCEEDED(hr))
	{
		slog(( "CAM:can't send PMT into FireDTV  CAM/CI hr:09x%x %s-%d\r\n", hr,
			      pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return 0;
	} else
	{

		pFireDTV->header.state = CAM_ENABLED;
		slog(( "CAM:PMT %d sent to CAM/CI module\r\n", pFireDTV->serviceID ));
		//slog(( "CAM:>>length %d|%02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x\r\n",
		//	    length, p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[8],p[9],p[10],p[11],p[12],p[13],p[14],p[15],p[16],p[17], p[18], p[19] ));
	}

	return 1;

}

static int SetnFireDTVHWPidFiltering(  JNIEnv *env, DShowCaptureInfo* pCapInfo, unsigned short* pPids, int PidNum )
{
	HRESULT hr;
	IKsPropertySet *pIKsPropSet;
	unsigned long   propertySelect;
	DVB_S_PIDS S_Pids, S_Pids2;
	DVB_T_PIDS T_Pids, T_Pids2;
	int i, j;
	if ( pCapInfo == NULL || pCapInfo->pBDATuner == 0 ) 
		return 0;

	TV_TYPE type = GetTVType( pCapInfo );
	if ( type == DVBT )
	{
		propertySelect = KSPROPERTY_FIRESAT_SELECT_PIDS_DVB_T;
		memset(	&T_Pids, 0, sizeof(T_Pids) );
	} else
	if ( type == DVBC || type == DVBS )
	{
		propertySelect = KSPROPERTY_FIRESAT_SELECT_PIDS_DVB_S;
		memset(	&S_Pids, 0, sizeof(S_Pids) );
	} else
	{
		return 0;
	}

	hr = pCapInfo->pBDATuner->QueryInterface( IID_IKsPropertySet, (void**)&pIKsPropSet );
	if (!SUCCEEDED(hr))
	{
		SAFE_RELEASE( pIKsPropSet );
		slog(( "CAM:can't find FireDTV KsPropertySet interface on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	}

	unsigned long isTypeSupported=0;
    hr = pIKsPropSet->QuerySupported( IID_IKsPropertySet, propertySelect, &isTypeSupported );
    if ( hr != 0 || isTypeSupported  == 0 )
    {
	    slog(( "CAM:FireDTV not support hardware pid filtering hr:0x%x %d %d %s-%d\r\n", 
			       hr, isTypeSupported, propertySelect,
		           pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
        SAFE_RELEASE( pIKsPropSet );
        return -1;
    }

	
    PidNum = PidNum > 16 ? 16 : PidNum;
	if ( type == DVBT )
	{
		T_Pids.bCurrentTransponder = 1;
		T_Pids.bFullTransponder = 0;
		for ( i = j = 0; i<PidNum; i++ )
			if ( pPids[i] )
				T_Pids.pids[j++] = pPids[i];
		T_Pids.uNumberOfValidPids = j;
		memcpy( &T_Pids2, &T_Pids, sizeof(T_Pids2) );
		hr = pIKsPropSet->Set( IID_KSPROPSETID_FIREDTV, propertySelect, 
		                   &T_Pids2, sizeof(T_Pids2), &T_Pids, sizeof(T_Pids) );

	} else
	if ( type == DVBC || type == DVBS )
	{
		S_Pids.bCurrentTransponder = 1;
		S_Pids.bFullTransponder = 0;
		for ( i = j = 0; i<PidNum; i++ )
			if ( pPids[i] )
				S_Pids.pids[j++] = pPids[i];
		T_Pids.uNumberOfValidPids = j;
		memcpy( &S_Pids2, &S_Pids, sizeof(S_Pids2) );
		hr = pIKsPropSet->Set( IID_KSPROPSETID_FIREDTV, propertySelect, 
		                   &S_Pids2, sizeof(S_Pids2), &S_Pids, sizeof(S_Pids) );

	} 

	SAFE_RELEASE( pIKsPropSet );
	if (!SUCCEEDED(hr))
	{
	    slog(( "CAM:FireDTV set hardware pid filtering failed hr:0x%x %s-%d\r\n", hr, 
		           pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	} else
	{
	    slog(( "CAM:FireDTV set hardware pid filtering success %s-%d\r\n", 
		           pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));

	}

	return 1;
}

//////////////////////////////////////////////////////////////////////////////
//Anysee CI 
//////////////////////////////////////////////////////////////////////////////
#define GUID_ANYSEE_CAPTUER_FILTER_PROPERTY { 0xb8e78938, 0x899d, 0x41bd, { 0xb5, 0xb4, 0x62, 0x69, 0xf2, 0x80, 0x18, 0x99 }}
static const GUID  KSPROPSETID_Anyseesat =  { 0xb8e78938, 0x899d, 0x41bd, { 0xb5, 0xb4, 0x62, 0x69, 0xf2, 0x80, 0x18, 0x99 }};

#define FW_PCB508TC    18   // DVB-T + DVB-C + Smartcard Interface + CI
#define FW_PCB508S2    19   // DVB-S2 + Smartcard Interface + CI
#define FW_PCB508T2C   20   // DVB-T2 + DVB-C + Smartcard Interface + CI
#define FW_PCB508PTC      21   // PCI + PCB508TC
#define FW_PCB508PS2      22   // PCI + PCB508S2
#define FW_PCB508PT2C      23   // PCI + PCB508T2C
#define USERPROPERTY_READ_PLATFORM 6
typedef struct _tagUSERPROPERTY_READ_PLATFORM_S {
	KSPROPERTY Property;
	DWORD	   dwDeviceInfo;  
	DWORD	   dummy; //ZQ for 8 bytes alignment
} USERPROPERTY_READ_PLATFORM_S, *PUSERPROPERTY_READ_PLATFORM_S;
 
#define MAX_ANYSEE_NUM	16
static ANYSEE* gAnysees[MAX_ANYSEE_NUM]={0};
#define ANYSEE_CAM_INITIALIZED   1
#define ANYSEE_CAM_READY		 2

#define CI_TIMEOUT 12000   //12 seconds

void fCIStateFunc(int nPort, int nCmd, char *str)
{
	ANYSEE* pAnysee;
	nPort--;
	slog(( "CAM: Anysee got a state change message at port:%d cmd:%d '%s'.\r\n", nPort, nCmd, str ));
	if ( nPort >= MAX_ANYSEE_NUM )
	{
		slog(( "CAM: Anysee port numbre is out of range of %d, skip command\r\n", MAX_ANYSEE_NUM ));
	}
	pAnysee  = gAnysees[nPort];
	if ( pAnysee == NULL )
		return;

	if ( nCmd == CI_MSG_INITIALIZATION_CAM )
	{
		pAnysee->state |= ANYSEE_CAM_INITIALIZED;
	} else
	if ( nCmd == CI_MSG_INSERT_CAM )
	{
		pAnysee->state |= ANYSEE_CAM_READY;
	} 
}

void fCIMessageFunc(int nPort, PVOID pData )
{
	slog(( "CAM: Anysee got a message at port:%d.\r\n", nPort ));
}

static int InitAnyseeCAM( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	HMODULE hCIAPILib;
	if ( pCapInfo == NULL ) return 0;

	ANYSEE* pAnysee = (ANYSEE*)pCapInfo->pCamCtrlData;
	if ( pAnysee == NULL ) 
	{
		slog(( "CAM: ctrl block is not ready for Anysee CI\r\n" ));
		return 0;
	}
	//check tuner has CI interface
	IKsPropertySet* pKPS=NULL;
	HRESULT hr = pCapInfo->pBDACapture->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
	if ( FAILED( hr ) )
	{
		slog(( "CAM: Anysee tuner pin KPS not found %s-%d hr:0x%x\r\n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );
		return 0;
	}
	USERPROPERTY_READ_PLATFORM_S DeviceInfo;
	hr = pKPS->Get( KSPROPSETID_Anyseesat, USERPROPERTY_READ_PLATFORM,
					&DeviceInfo,  sizeof(USERPROPERTY_READ_PLATFORM_S),
					&DeviceInfo,  sizeof(USERPROPERTY_READ_PLATFORM_S), NULL ); 
	SAFE_RELEASE( pKPS );
	if( FAILED(hr) ) 
	{
		slog(( "CAM: Anysee not found device inforam on Anysee Capture tuner %s-%d hr:0x%x (%d)\r\n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr, sizeof(USERPROPERTY_READ_PLATFORM_S) ) );
		//return 0;
	} 
	DWORD dwAnyseeDevice = (DeviceInfo.dwDeviceInfo >> 16) & 0x0FF;
    if ( dwAnyseeDevice == FW_PCB508TC  ) { slog(("DVB-S: Anysee tuner PCB508TC\r\n"	  ));} else
	if ( dwAnyseeDevice == FW_PCB508S2  ) { slog(("DVB-S: Anysee tuner FW_PCB508S2\r\n"   ));} else
	if ( dwAnyseeDevice == FW_PCB508T2C ) { slog(("DVB-S: Anysee tuner FW_PCB508T2C\r\n"  ));} else
	if ( dwAnyseeDevice == FW_PCB508PTC ) { slog(("DVB-S: Anysee tuner FW_PCB508PTC\r\n"  ));} else
	if ( dwAnyseeDevice == FW_PCB508PS2 ) { slog(("DVB-S: Anysee tuner FW_PCB508PS2\r\n"  ));} else
	if ( dwAnyseeDevice == FW_PCB508PT2C ){ slog(("DVB-S: Anysee tuner FW_PCB508PT2C\r\n" ));} else
	{
		slog(("CAM: Anysee Capture tuner doens't suppport CI %s-%d hr:0x%x\r\n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );
		return 0;
	}

	if ( pAnysee->hLibHandle != NULL )
		FreeLibrary( pAnysee->hLibHandle );

	//hCIAPILib = LoadLibrary(_T("CIAPI.dll"));
	{  //CIAPI.dll has thread safe issue, a walkaround way is here
		char AnyseeCIAPI[MAX_PATH];
		sprintf( AnyseeCIAPI, _T("CIAPI%d.dll"), pCapInfo->videoCaptureFilterNum);
		hCIAPILib = LoadLibrary( AnyseeCIAPI );
		if ( hCIAPILib == NULL )
		{
			slog(( "CAM: Anysee Can't find '%s' for Anysee CI\r\n", AnyseeCIAPI ));
			return 0;
		} else
		{
			slog(( "CAM: Anysee loaded '%s' for Anysee CI\r\n", AnyseeCIAPI ));
		}
	}

	if ( hCIAPILib == NULL )
	{
		slog(( "CAM: Anysee didn't find 'CIAPI.dll' for Anysee CI\r\n" ));
		return 0;
	}
	
	pAnysee->hLibHandle = hCIAPILib;

	pAnysee->CreateCI = (pCreateDtvCIAPI) GetProcAddress(hCIAPILib, "CreateDtvCIAPI");
	pAnysee->DestroyCI = (pDestroyDtvCIAPI) GetProcAddress(hCIAPILib, "DestroyDtvCIAPI");
	pAnysee->GetanyseeNumberofDevicesEx = 
		    (pGetanyseeNumberofDevicesEx) GetProcAddress(hCIAPILib, "GetanyseeNumberofDevicesEx");

	if ( pAnysee->CreateCI  == NULL || pAnysee->DestroyCI == NULL || pAnysee->GetanyseeNumberofDevicesEx == NULL )
	{
		slog(( "CAM:Can't find API entry in 'CIAPI.dll' for Anysee CI\r\n" ));
		return -1;

	}

	pAnysee->CILib = NULL;
	pAnysee->portNum = -1;


	return 1;
}

static int CloseAnyseeCI( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	ANYSEE *pAnysee;

	if ( pCapInfo == NULL  )
		return -1;

	pAnysee = (ANYSEE *)pCapInfo->pCamCtrlData;
	if ( pAnysee == NULL || pAnysee->CILib == NULL || pAnysee->header.state == CAM_CLOSE )
	{
		slog(( "CAM:Error: Anysee CAM/CI is not initailized on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	}
	pAnysee->state = 0;
	pAnysee->DestroyCI( &pAnysee->CILib );
	pAnysee->CILib = NULL;
	pAnysee->header.state = CAM_CLOSE;
	slog(( "CAM: Anysee CAM/CI is closed on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	return 1;
}

static void ReleaseAnyseeCI( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	ANYSEE *pAnysee;

	if ( pCapInfo == NULL  )
		return ;

	pAnysee = (ANYSEE *)pCapInfo->pCamCtrlData;
	if ( pAnysee == NULL )
	{
		slog(( "CAM:Error: Anysee CAM/CI is not initailized on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return;
	}

	if ( pAnysee->portNum < MAX_ANYSEE_NUM )
		gAnysees[pAnysee->portNum] = 0x0;

	if ( pAnysee->hLibHandle != NULL )
		FreeLibrary( pAnysee->hLibHandle );

	pAnysee->hLibHandle = NULL;
	slog(( "CAM: Anysee CAM/CI is released on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
}

static int OpenAnyseeCam( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	ANYSEE *pAnysee;

	if ( pCapInfo == NULL  )
		return -1;

	pAnysee = (ANYSEE *)pCapInfo->pCamCtrlData;
	if ( pAnysee == NULL )
	{
		slog(( "CAM: Anysee CAM/CI is not initailized on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	}	

	if ( pAnysee->header.state != CAM_CLOSE )
	{
		slog(( "CAM: Anysee CI is open already. %s-%d \r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return 0;
	}

	if ( pAnysee->CreateCI == NULL )
	{
		slog(( "CAM: Anysee CIAPI.dll was not found. %s-%d \r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return 0;
	}

	if ( !pAnysee->CreateCI(&pAnysee->CILib) )
	{
		slog(( "CAM: Can't Create CI Intetface for Anysee tuner\r\n" ));
		return -1;
	}

	pAnysee->header.state = 0;
	ANYSEECIDEVICESINFO ADList;
	int i, nTotalPort = pAnysee->GetanyseeNumberofDevicesEx( &ADList );
	for( i = 0; i<nTotalPort; i++ )
	{
		slog(( "CAM:Anysee %s %s \r\n", ADList.pBuffer[i], pCapInfo->bdaCaptureFilterName ));
		if ( strcmp( ADList.pBuffer[i], pCapInfo->bdaCaptureFilterName ) )
			continue;
		
		if ( pAnysee->CILib->OpenCILib(NULL, ADList.dwADIndex[i]) == S_OK )
		{
			pAnysee->portNum = i;
			int ret = pAnysee->CILib->CI_Control(CI_CONTROL_IS_PLUG_OPEN, (LPARAM *)&fCIStateFunc, (LPARAM *)fCIMessageFunc);
			if ( !SUCCEEDED( ret ) )
			{
				slog(( "CAM: Anysee failed seeting CI fCIStateFunc,  fCIMessageFunc on port %d\r\n", pAnysee->portNum ));
				return 1;
			} 
			if ( pAnysee->portNum >= MAX_ANYSEE_NUM )
			{
				slog(( "CAM: Anysee to may Anysee device num, drop this port %d.\r\n", pAnysee->portNum ));
			} else
			{
				gAnysees[ pAnysee->portNum ] = pAnysee;
			}

			//if ( CI has card inserted.
			{
				unsigned long wait_time = 0;
				slog(( "CAM: Anysee is waiting CI ready.  port %d.\r\n", pAnysee->portNum ));
				while ( wait_time < CI_TIMEOUT )
				{
					if ( wait_time > 2000 && !(pAnysee->state & ANYSEE_CAM_INITIALIZED ) )
						break;
					if ( pAnysee->state & ANYSEE_CAM_READY )
						break;
					Sleep( 250 );
					wait_time += 250;
				}
				if ( pAnysee->state & ANYSEE_CAM_READY )
					pAnysee->header.state |= CAM_INITIALIZED;
				slog(( "CAM: Anysee state:%d, time:%d.  port %d.\r\n", pAnysee->state, wait_time, pAnysee->portNum ));
			}
		}
	}
	slog(( "CAM: Anysee CI is open on  port %d ( total port:%d ) %x\r\n", pAnysee->portNum, nTotalPort, pAnysee->header.state ));
	pAnysee->header.state |= CAM_OPEN;
	pAnysee->header.OnPMTEnable = true;
	if ( !(pAnysee->state & ANYSEE_CAM_INITIALIZED) )
	{
		slog(( "CAM: Anysee CI initializing failed on port %d, CI is disabled %x\r\n", pAnysee->portNum, pAnysee->header.state ));
		pAnysee->header.OnPMTEnable = false;
	}
	return 1;
}

static int CloseAnyseeCam( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	if ( pCapInfo != NULL && pCapInfo->pCamCtrlData != NULL  )
	{
		ANYSEE *pAnysee;
		pAnysee = (ANYSEE *)pCapInfo->pCamCtrlData;
		pAnysee->state = 0;
	}
	return 1;
}

static int AnyseeCamSwitchChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId, int encryFlag )
{
	ANYSEE *pAnysee;

	if ( pCapInfo == NULL  )
		return -1;

	pAnysee = (ANYSEE *)pCapInfo->pCamCtrlData;
	if ( pAnysee == NULL )
	{
		slog(( "CAM:Error: Anysee CAM/CI is not initailized on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	}	

	pAnysee->serviceID = serviceId;
	
	if ( pAnysee->header.state == CAM_CLOSE )
		return 1;

	//if ( pAnysee->header.state & CAM_ENABLED )
	//		pAnysee->state = 0;

	if ( encryFlag )
		pAnysee->state &= ~CAM_SKIP;
	else 
		pAnysee->state |= CAM_SKIP;

	return 1;
}

static long OnAnyseePMT(  JNIEnv *env, DShowCaptureInfo* pCapInfo, short bytes, void* mesg )
{
	ANYSEE *pAnysee;

	if ( pCapInfo == NULL  )
		return -1;

	pAnysee = (ANYSEE *)pCapInfo->pCamCtrlData;
	if ( pAnysee == NULL )
	{
		slog(( "CAM:Error: Anysee CAM/CI is not initailized on %s-%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	}	

	if ( memcmp( pAnysee->header.TAG, ANYSEE_CAM, 8 ) || pAnysee->header.state == CAM_CLOSE )
	{
		slog(( "CAM: Anysee CAM is not open, TAG:%s %d\r\n", pAnysee->header.TAG, pAnysee->header.state ));
		return 0;
	}

	if ( 0 && pAnysee->state & CAM_SKIP )
	{
		slog(( "CAM: Anysee skip CAM for a clear channel\r\n" ));
		return 0;
	}

	if ( (pAnysee->state & ANYSEE_CAM_READY )==0 )
	{
		slog(( "CAM: Anysee CAM isn't ready , skip setting PMT at port:%d, state:%d\r\n",  pAnysee->portNum, pAnysee->state ));
		return 2;
	}

	
	int serviceId = pAnysee->serviceID;
	PROGRAM_DATA *program_data = ( PROGRAM_DATA *)mesg;
	if ( program_data != NULL && program_data->pids_table.sid != serviceId )
	{
		slog(( "CAM: Anysee drop unwanted PMT %d (expect %d)\r\n", (unsigned short)program_data->pids_table.sid, serviceId ));
		return 0;
	}

	unsigned char* pPmtSectionData = program_data->pmt_section_data;
	long lPmtSectionBytes =  program_data->pmt_section_bytes;
	int ret = pAnysee->CILib->SetanyseeCIPMTSection( lPmtSectionBytes, pPmtSectionData );
	if ( !SUCCEEDED( ret ) )
	{
		slog(( "CAM: Anysee failed setting PMT service id:%d, ret:0x%x, port:%d\r\n", serviceId, ret, pAnysee->portNum ));
		return 0;
	} else
	{
		pAnysee->header.state |= CAM_ENABLED;
		slog(( "CAM: Anysee setting PMT, service id:%d, ret:0x%x, port:%d\r\n", serviceId, ret, pAnysee->portNum ));
		return 1;
	}
	
	return 0;


}

//////////////////////////////////////////////////////////////////////////////
//TechnoTrend CI 
//////////////////////////////////////////////////////////////////////////////
static void __stdcall OnCAStatus(void * Context, char nSlot, char nReplyTag, unsigned short wStatus );
static void __stdcall OnSlotStatus( void * Context, char nSlot, char nStatus, SLOTINF* csInfo );



static int InitTechnoTrend( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	HMODULE hTechnoTrendLib;
	FARPROC lpfnProc;
	if ( pCapInfo == NULL ) return 0;

	TECHNOTREND* pTechnoTrend = (TECHNOTREND*)pCapInfo->pCamCtrlData;
	if ( pTechnoTrend == NULL ) 
	{
		slog(( "CAM: ctrl block is not ready for TechnoTrend CI\r\n" ));
		return 0;
	}

	if ( pTechnoTrend->hLibHandle != NULL )
		FreeLibrary( pTechnoTrend->hLibHandle );

	hTechnoTrendLib = LoadLibrary("ttBdaDrvApi_Dll.dll");
	if ( hTechnoTrendLib == NULL )
	{
		slog(( "CAM:Can't find 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n" ));
		return 0;
	}
	pTechnoTrend->hLibHandle = hTechnoTrendLib;

	lpfnProc = GetProcAddress( hTechnoTrendLib, "_bdaapiOpenHWIdx@8" );
	if ( lpfnProc == NULL )
		lpfnProc = GetProcAddress( hTechnoTrendLib, "bdaapiOpenHWIdx@8" );
	if ( lpfnProc == NULL )
	{
		slog(("CAM:Can't find 'bdaapiOpenHWIdx' in 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n"));
		return -1;
	}
	pTechnoTrend->bdaapiOpenHWIdx = (pfn_bdaapiOpenHWIdx)lpfnProc;

	lpfnProc = GetProcAddress( hTechnoTrendLib, "_bdaapiOpenCISlim@20" );
	if ( lpfnProc == NULL )
		lpfnProc = GetProcAddress( hTechnoTrendLib, "bdaapiOpenCISlim@20" );
	if ( lpfnProc == NULL )
	{
		slog(("CAM:Can't find 'bdaapiOpenCISlim' in 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n"));
		return -1;
	}
	pTechnoTrend->bdaapiOpenCISlim = (pfn_bdaapiOpenCISlim)lpfnProc;
	
	lpfnProc = GetProcAddress( hTechnoTrendLib, "_bdaapiOpenCIWithoutPointer@4" );
	if ( lpfnProc == NULL )
		lpfnProc = GetProcAddress( hTechnoTrendLib, "bdaapiOpenCIWithoutPointer@4" );
	if ( lpfnProc == NULL )
	{
		slog(("CAM:Can't find 'bdaapiOpenCIWithoutPointer' in 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n"));
		return -1;
	}
	pTechnoTrend->bdaapiOpenCIWithoutPointer = (pfn_bdaapiOpenCIWithoutPointer)lpfnProc;

	lpfnProc = GetProcAddress( hTechnoTrendLib, "_bdaapiCIGetSlotStatus@8" );
	if ( lpfnProc == NULL )
		lpfnProc = GetProcAddress( hTechnoTrendLib, "bdaapiCIGetSlotStatus@8" );
	if ( lpfnProc == NULL )
	{
		slog(("CAM:Can't find 'bdaapiCIGetSlotStatus' in 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n"));
		return -1;
	}
	pTechnoTrend->bdaapiCIGetSlotStatus = (pfn_bdaapiCIGetSlotStatus)lpfnProc;
	
	lpfnProc = GetProcAddress( hTechnoTrendLib, "_bdaapiCloseCI@4" );
	if ( lpfnProc == NULL )
		lpfnProc = GetProcAddress( hTechnoTrendLib, "bdaapiCloseCI@4" );
	if ( lpfnProc == NULL )
	{
		slog(("CAM:Can't find 'bdaapiCloseCI' in 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n"));
		return -1;
	}
	pTechnoTrend->bdaapiCloseCI = (pfn_bdaapiCloseCI)lpfnProc;
	
	lpfnProc = GetProcAddress( hTechnoTrendLib, "_bdaapiClose@4" );
	if ( lpfnProc == NULL )
		lpfnProc = GetProcAddress( hTechnoTrendLib, "bdaapiClose@4" );
	if ( lpfnProc == NULL )
	{
		slog(("CAM:Can't find 'bdaapiClose' in 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n"));
		return -1;
	}
	pTechnoTrend->bdaapiClose = (pfn_bdaapiClose)lpfnProc;
	
	lpfnProc = GetProcAddress( hTechnoTrendLib, "_bdaapiCIReadPSIFastDrvDemux@8" );
	if ( lpfnProc == NULL )
		lpfnProc = GetProcAddress( hTechnoTrendLib, "bdaapiCIReadPSIFastDrvDemux@8" );
	if ( lpfnProc == NULL )
	{
		slog(("CAM:Can't find 'bdaapiCIReadPSIFastDrvDemux' in 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n"));
		return -1;
	}
	pTechnoTrend->bdaapiCIReadPSIFastDrvDemux = (pfn_bdaapiCIReadPSIFastDrvDemux)lpfnProc;

	lpfnProc = GetProcAddress( hTechnoTrendLib, "_bdaapiSetDiSEqCMsg@24" );
	if ( lpfnProc == NULL )
		lpfnProc = GetProcAddress( hTechnoTrendLib, "bdaapiSetDiSEqCMsg@24" );
	if ( lpfnProc == NULL )
	{
		slog(("CAM:Can't find 'bdaapiSetDiSEqCMsg' in 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n"));
		return -1;
	}
	pTechnoTrend->bdaapiSetDiSEqCMsg = (pfn_bdaapiSetDiSEqCMsg)lpfnProc;

	lpfnProc = GetProcAddress( hTechnoTrendLib, "_bdaapiSetDVBTAntPwr@8" );
	if ( lpfnProc == NULL )
		lpfnProc = GetProcAddress( hTechnoTrendLib, "bdaapiSetDVBTAntPwr@8" );
	if ( lpfnProc == NULL )
	{
		slog(("CAM:Can't find 'bdaapiSetDVBTAntPwr' in 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n"));
	}
	pTechnoTrend->bdaapiSetDVBTAntPwr = (pfn_bdaapiSetDVBTAntPwr)lpfnProc;

	lpfnProc = GetProcAddress( hTechnoTrendLib, "_bdaapiGetDVBTAntPwr@8" );
	if ( lpfnProc == NULL )
		lpfnProc = GetProcAddress( hTechnoTrendLib, "bdaapiGetDVBTAntPwr@8" );
	if ( lpfnProc == NULL )
	{
		slog(("CAM:Can't find 'bdaapiSetDVBTAntPwr' in 'ttBdaDrvApi_Dll.dll' for TechnoTrend CI\r\n"));
	}
	pTechnoTrend->bdaapiGetDVBTAntPwr = (pfn_bdaapiGetDVBTAntPwr)lpfnProc;

	slog(( "CAM:TechnoTrend CI is initialized\r\n" ));
	
	pTechnoTrend->handle = -1;
	pTechnoTrend->status = 0;
	//slog(( "CAM:*pTechnoTrend  status %d (0x%x)\r\n",  pTechnoTrend->status,  &pTechnoTrend->status ));
	return 1;
}

static void ReleaseTechnoTrend( JNIEnv *env, DShowCaptureInfo* pCapInfo  )
{
	if ( pCapInfo == NULL ) return ;
	TECHNOTREND* pTechnoTrend = (TECHNOTREND*)pCapInfo->pCamCtrlData;
	if ( pTechnoTrend == NULL  )
		return;

	if ( pTechnoTrend->hLibHandle != NULL )
		FreeLibrary( pTechnoTrend->hLibHandle );

	pTechnoTrend->status = 0;
	slog(( "CAM:TechnoTrend CI is released\r\n" ));
}


static int GetTechnoTrendDeviceID( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	HRESULT hr;
    IKsPin* pKsPin = NULL;
    KSMULTIPLE_ITEM *pmi;
	IPin *pPin;
	int TechNoTrendDeviceID = -1; 

	if ( pCapInfo == NULL || pCapInfo->pBDATuner == NULL )
	{
		slog(( "CAM:TechnoTrend BDA Tuner is not ready %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                                    pCapInfo->videoCaptureFilterNum ));
		return -1;
	}

	hr = GetPin( pCapInfo->pBDATuner, PINDIR_OUTPUT, 0, &pPin );
	if (FAILED(hr)) 
	{
		slog (( "TechnoTrend BDA Tuner Pin is not ready %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                                    pCapInfo->videoCaptureFilterNum ));
		return -1;
	}

    hr = pPin->QueryInterface(IID_IKsPin, (void **)&pKsPin);
    if (FAILED(hr)) 
	{
		slog (( "TechnoTrend BDA Tuner Pin interface is not found %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                                    pCapInfo->videoCaptureFilterNum ));
		pPin->Release();
        return -1;  // Pin does not support IKsPin.
	}

    hr = pKsPin->KsQueryMediums(&pmi);
    pKsPin->Release();
	pPin->Release();
    if (FAILED(hr))
	{
		slog (( "TechnoTrend BDA Tuner mediums is not retrieved %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                                    pCapInfo->videoCaptureFilterNum ));
        return -1;  // Pin does not support mediums.
	}

    if (pmi->Count) 
    {
        REGPINMEDIUM *pTemp = (REGPINMEDIUM*)(pmi + 1);
       	TechNoTrendDeviceID = pTemp->dw1;
    }        

    CoTaskMemFree(pmi);
	slog(( "CAM:TechnoTrend deviceid %d %s-%d\r\n", TechNoTrendDeviceID,
		 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	return TechNoTrendDeviceID;
}

static int GetTechnoTrendDeviceType( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	int deviceType = 0;
	if ( pCapInfo== NULL )  return -1;

	if ( strstr( pCapInfo->bdaTunerFilterName, "USB 2.0 BDA DVB-C" ) ||
		 strstr( pCapInfo->bdaTunerFilterName, "USB 2.0 BDA DVB-S" ) ||
		 strstr( pCapInfo->bdaTunerFilterName, "USB 2.0 BDA DVB-T" ) )
	 {
		 deviceType = DevTypeUsb2;
		 slog(( "CAM:TechnoTrend card type: devTypeUSB2 %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                               pCapInfo->videoCaptureFilterNum ));
     } else
	 if ( strstr( pCapInfo->bdaTunerFilterName, "TTHybridTV BDA DVBT " ) )
      {
		 deviceType = DevTypeB3;
		 slog(( "CAM:TechnoTrend card type: Budet3 %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                               pCapInfo->videoCaptureFilterNum ));

      } else
	 if ( strstr( pCapInfo->bdaTunerFilterName, "TechnoTrend BDA/DVB-C " ) ||
		 strstr( pCapInfo->bdaTunerFilterName, "TechnoTrend BDA/DVB-S " ) ||
		 strstr( pCapInfo->bdaTunerFilterName, "TechnoTrend BDA/DVB-T " ) )
      {
		 deviceType = DevTypeB2;
		 slog(( "CAM:TechnoTrend card type: Budet2 %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                               pCapInfo->videoCaptureFilterNum ));
      } else
	 if ( strstr( pCapInfo->bdaTunerFilterName, "Pinnacle PCTV 400e " ) )
      {
		 deviceType = DevTypeUsb2Pinnacle;
		 slog(( "CAM:TechnoTrend card type: DevTypeUsb2Pinnacle %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                               pCapInfo->videoCaptureFilterNum ));
      }
      else
      {
        // _log.Info("Technotrend Unknown card type");
         deviceType = TypeUnknown;
		 slog(( "CAM:TechnoTrend card type: unknown name %s-%d\r\n", pCapInfo->videoCaptureFilterName, 
			                                               pCapInfo->videoCaptureFilterNum ));
      }

	  return deviceType;
}

static int OpenTechnoTrendCI( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	if ( pCapInfo== NULL )  return -1;
	TECHNOTREND* pTechnoTrend = (TECHNOTREND*)pCapInfo->pCamCtrlData;
	if ( pTechnoTrend == NULL  )	return -1;

	if ( pTechnoTrend->header.state != CAM_CLOSE )
	{
		slog(( "CAM:TechnoTrend CAM/CI device is already handle:0x%x %s-%d\r\n", pTechnoTrend->handle,
			 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return 1;
	}

	int device_id = GetTechnoTrendDeviceID( env, pCapInfo );
	if ( device_id < 0 )
	{
		return -1;
	}

	slog(( "CAM: open TechnoTrend CI on device id:%d, tuner:'%s', %s-%d\r\n", device_id, pCapInfo->bdaTunerFilterName,
			 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));

	pTechnoTrend->deviceType = GetTechnoTrendDeviceType( env, pCapInfo );

	if ( pTechnoTrend->deviceType == TypeUnknown )
	{
		slog(( "CAM:Failed open TechnoTrend device, due to unknown device type %s-%d\r\n", 
			 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	}

	pTechnoTrend->handle = 0;
	if ( pTechnoTrend->bdaapiOpenHWIdx != NULL )
		pTechnoTrend->handle = pTechnoTrend->bdaapiOpenHWIdx( pTechnoTrend->deviceType, device_id );
	else
	{
		pTechnoTrend->handle = -1;
		slog(( "CAM:bdaapiOpenHWIdxAPI is not found in dll, TechnoTrend CI %s-%d\r\n", 
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	}

	if ( pTechnoTrend->handle == -1  )
	{
		slog(( "CAM:Failed open TechnoTrend device ret:%d, device type:%d, device id:%d %s-%d\r\n", 
			 pTechnoTrend->handle, pTechnoTrend->deviceType, device_id,
			 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
	}

	if ( pTechnoTrend->bdaapiOpenCISlim != NULL )
	{
		//CallbackFunctionsSlim CFS;
		pTechnoTrend->CFS.onCAStatus =  OnCAStatus;         
		pTechnoTrend->CFS.onSlotStatus = OnSlotStatus;
		pTechnoTrend->CFS.onCAStatusContext = pCapInfo;
		pTechnoTrend->CFS.onSlotStatusContext = pCapInfo;

        int ret = pTechnoTrend->bdaapiOpenCISlim( pTechnoTrend->handle, pTechnoTrend->CFS );
        if ( ret == 0)
        {
			pTechnoTrend->header.state = CAM_OPEN;      
			slog(( "CAM:TechnoTrend CI is opened. handle: 0x%x %s-%d\r\n", pTechnoTrend->handle,
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));

		} else
		{
			slog(( "CAM:Failed open TechnoTrend CI %s-%d, ret:%d\r\n", 
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, ret ));

		}
	} else
	{
		slog(( "CAM:bdaapiOpenCISlim API is not found in dll, TechnoTrend CI %s-%d\r\n", 
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));

	}
	
	pTechnoTrend->header.OnPMTEnable = true;
	return pTechnoTrend->header.state == CAM_OPEN ? 1:0;
}

static int CloseTechnoTrendCI( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	if ( pCapInfo== NULL  )
		return -1;

	TECHNOTREND* pTechnoTrend = (TECHNOTREND*)pCapInfo->pCamCtrlData;
	if ( pTechnoTrend == NULL  )
		return -1;

	if ( pTechnoTrend->bdaapiClose && pTechnoTrend->handle != -1 )
	{
		pTechnoTrend->bdaapiCloseCI(pTechnoTrend->handle);
		pTechnoTrend->bdaapiClose( pTechnoTrend->handle );
		pTechnoTrend->handle = -1;
	}

	pTechnoTrend->header.state = CAM_CLOSE;
	pTechnoTrend->status = 0;
	//slog(( "CAM:**pTechnoTrend  status %d (0x%x)\r\n",  pTechnoTrend->status,  &pTechnoTrend->status ));
	return 1;
}
static int TechnoTrendCamSwitchChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId )
{
	if ( pCapInfo== NULL  )
		return -1;

	TECHNOTREND* pTechnoTrend = (TECHNOTREND*)pCapInfo->pCamCtrlData;
	if ( pTechnoTrend == NULL  )
		return -1;

	if ( memcmp( pTechnoTrend->header.TAG, TECHNOTREND_CAM, 8 ) || pTechnoTrend->header.state == CAM_CLOSE )
		return 0;

	pTechnoTrend->serviceID = serviceId;
	pTechnoTrend->header.state = CAM_PENDING;
	slog(( "CAM: Switch channel %d TechnoTrend CI %s-%d\r\n", serviceId,
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
	return 1;
}

static long OnTechnoTrendPMT( JNIEnv *env, DShowCaptureInfo* pCapInfo, short bytes, void* mesg )
{
	if ( pCapInfo== NULL  )
		return -1;

	TECHNOTREND* pTechnoTrend = (TECHNOTREND*)pCapInfo->pCamCtrlData;
	if ( pTechnoTrend == NULL  )
	{
		return -1;
	}

	if ( memcmp( pTechnoTrend->header.TAG, TECHNOTREND_CAM, 8 ) || pTechnoTrend->header.state == CAM_CLOSE )
	{
		//slog(( "CAM: failed OnTechnoTrend PMT %s %d\r\n", pTechnoTrend->header.TAG, pTechnoTrend->header.state ));
		return 0;
	}

	if ( pTechnoTrend->bdaapiCIReadPSIFastDrvDemux == NULL  )
	{
		//slog(( "CAM:failed bdaapiCIReadPSIFastDrvDemux API null, TechnoTrend CI %s-%d\r\n", 
		//		 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		return -1;
    }

	if ( pTechnoTrend->status == 0 ) //device is not ready
	{
		//slog(( "CAM:pTechnoTrend is not ready status %d (0x%x)\r\n",  pTechnoTrend->status,  &pTechnoTrend->status ));
		return 2;
	}

	if ( pTechnoTrend->header.state != CAM_PENDING )
	{
		//slog(( "CAM:pTechnoTrend pTechnoTrend->header.state :%d\n\r\n", pTechnoTrend->header.state ));
		return 0;
	}

	int serviceId = pTechnoTrend->serviceID;
	PROGRAM_DATA *program_data = ( PROGRAM_DATA *)mesg;
	if ( program_data != NULL && program_data->pids_table.sid != serviceId )
	{
		//slog(( "CAM:is not expected PMT %d drope it\r\n", (unsigned short)section->service ));
		return 0;
	}

    if ( pTechnoTrend->bdaapiCIReadPSIFastDrvDemux(  pTechnoTrend->handle, serviceId ) == 0 )
	{
		slog(( "CAM:Send service id:%d into TechnoTrend CAM/CI module %s-%d\r\n", 
			     serviceId,
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		pTechnoTrend->header.state = CAM_ENABLED;
		return 1;
	}
    else
    {
		slog(( "CAM:failed bdaapiCIReadPSIFastDrvDemux with service id:%d, TechnoTrend CI %s-%d\r\n", 
			     serviceId,
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));
		pTechnoTrend->header.state = CAM_ERROR;
		return 0;

    }
	return 0;
}

static void __stdcall OnSlotStatus( void *context, char nSlot, char Status, SLOTINF* csInfo )
{
	DShowCaptureInfo* pCapInfo;
	if ( context == NULL )
		return ;

	pCapInfo = (DShowCaptureInfo*)context;
	CAM_CTRL_HEADER* pCamCtrlHeader = (CAM_CTRL_HEADER*)pCapInfo->pCamCtrlData;

	if ( pCamCtrlHeader == NULL || pCamCtrlHeader->env == NULL )
		return ;

	if (  pCapInfo->pBDATuner == NULL )
		return ;

	if ( memcmp( pCamCtrlHeader->TAG, TECHNOTREND_CAM, 8 ))
		return;

	//printf( "TechnoTrend Slot status change\n" );
	if ( Status == 2 || Status == 3 || Status == 4 )
	//if ( Status == 2 || Status == 3 || Status == 4 || Status == 1 ) //MarkSS
    {
		TECHNOTREND* pTechnoTrend = (TECHNOTREND*)pCapInfo->pCamCtrlData;

		slog(( "CAM:**********pTechnoTrend 0x%x status %d.\r\n",  pTechnoTrend, pTechnoTrend !=NULL ? pTechnoTrend->status : 999 ));
		if ( pTechnoTrend != NULL ) //&& pTechnoTrend->status == 0 )
		{
			pTechnoTrend->status = Status;
			slog(( "CAM:********pTechnoTrend  status %d (0x%x)\r\n",  pTechnoTrend->status,  &pTechnoTrend->status ));
		}
    }
	slog(( "CAM:*slot TechnoTrend CAM/CI slot status changed %d\r\n", Status )); 
}

static void __stdcall OnCAStatus(void *Context, char nSlot, char nReplyTag, unsigned short wStatus )
{
	//printf( "TechnoTrend CA status change\n" );
	slog(( "CAM:#slot TechnoTrend CAM/CI slot status changed:%d slot:%d tag:%d\r\n", wStatus, nSlot, nReplyTag ));
}