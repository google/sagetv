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
#include <time.h>
#include "DShowCapture.h"
#include "../../include/guids.h"
#include "../../include/sage_DShowCaptureDevice.h"
#include "DShowUtilities.h"
#include "FilterGraphTools.h"
#include "../../include/iTSSplitter.h"
#include "QAMCtrl.h"     
#include "TunerPlugin.h"     
#include "Channel.h"
 
extern FilterGraphTools graphTools;
TV_TYPE GetTVType( DShowCaptureInfo *pCapInfo );
char * TVTypeString( TV_TYPE BDATVType );
HRESULT  ChannelSignalStrength( JNIEnv *env, DShowCaptureInfo* pCapInfo, long *strength, long* quality, bool* locked );
HRESULT  ChangeATSCChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhysicalChannel,long lMajorChannel, long lMinorChannel );
HRESULT  SetupBDAStreamOutFormat( JNIEnv *env, DShowCaptureInfo* pCapInfo, int streamType );

void AddBDAVideoCaptureFilters(JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, int devCaps);
void BDAGraphConnectDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void BDAGraphDisconnectDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void BDAGraphSetDebugRawDumpFileName( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, char* Channel  );
void BDAGraphSetDebugFileSource( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph  );
void BDAGraphConnectFilter( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void ClearUpDebugFileSource( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void BDAGraphConnectDebugRawDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph  );
void SetupCAM( JNIEnv* env, DShowCaptureInfo *pCapInfo );
int  SetupSatelliteLNB( DShowCaptureInfo* pCapInfo, int bReload  );


extern "C" { int  SwitchCamChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId, int encryptionFlag ); }



/* Class:     sage_DShowCaptureDevice
 * Method:    getDevicesInCategory0
 * Signature: (Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_DShowCaptureDevice_scanBDAChannel0
	(JNIEnv *env, jobject jo, jlong capInfo, jstring jnum, jstring jcountry, jint streamType )
{
	static jclass stringClass;

	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if (!pCapInfo )
	{
		slog((env, "ScanDBAChannel0 failed device is not open or not exist.\r\n"  ));
		return env->NewStringUTF("");
	}

	if ( !capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ) )
	{
		slog((env, "is not BDA capture device\r\n" ));
		return env->NewStringUTF("");
	}

	TV_TYPE TVType = GetTVType( pCapInfo );
	const char* cnum = env->GetStringUTFChars(jnum, NULL);
	const jchar* country = env->GetStringChars( jcountry, NULL);

	char  countryRegion[256]={0};
	char  tuningString[64]={0};

	if ( country != NULL && country[0] )
		wcstombs( countryRegion, (wchar_t*)country, sizeof(countryRegion)-2 );

	//trime space
	size_t len = strlen(countryRegion);
	while( len-- > 0 )
	{
		if ( countryRegion[len] == ' ' ) countryRegion[len] = 0x0;
		else break;
	}
	strncpy( pCapInfo->Country, countryRegion, sizeof(pCapInfo->Country) );

	//$NEW
	strncpy( tuningString, cnum, sizeof(tuningString) );
	env->ReleaseStringUTFChars( jnum, cnum);
	env->ReleaseStringChars( jcountry, country);
	
	//if ( pCapInfo->filterState <= 1 )  //in case, CaptureFilters is not ready
	{
		AddBDAVideoCaptureFilters( env, pCapInfo, pCapInfo->pGraph, 0 );             
		BDAGraphSetDebugFileSource( env, pCapInfo, pCapInfo->pGraph  );
		BDAGraphConnectFilter( env, pCapInfo,  pCapInfo->pGraph );
		SetupCAM( env, pCapInfo );
		SetupTunerPlugin( env, pCapInfo, GetTVType( pCapInfo ) );
		BDAGraphConnectDebugRawDumpSink( env, pCapInfo, pCapInfo->pGraph  );
       	BDAGraphConnectDumpSink( env, pCapInfo,  pCapInfo->pGraph );
		ClearUpDebugFileSource(  env, pCapInfo, pCapInfo->pGraph  );
	}

	TV_TYPE BDATVType = GetTVType( pCapInfo ); 
	if ( BDATVType == ATSC )
	{
		if ( !stricmp( pCapInfo->TuningMode, "Cable" ) )
		{
			if ( QAMTunerType( env, pCapInfo ) == 1  )
			{
				BDATVType = QAM;
			}
			else 
			if ( strstr(  pCapInfo->videoCaptureFilterName, "Silicondust HDHomeRun" ) ) //ZQ. hardcode HDHR QAM
			{
				BDATVType = QAM;
				slog( (env, "It's a HDHomeRun QAM tuner\r\n") );
			}
		}

		if ( strcmp( getSourceType( (CHANNEL_DATA*)pCapInfo->channel ), TVTypeString( BDATVType ) ) )
			setSourceType( (CHANNEL_DATA*)pCapInfo->channel, TVTypeString( BDATVType ) );
	}

	if ( BDATVType == DVBS ) 
	{
		if ( atoi( tuningString) == 0 )
			SetupSatelliteLNB( pCapInfo, 1 );
	}

	char* deviceName =  SageGetTunerDeviceName(pCapInfo);	
	slog(( env, "Scan Channel:%s,  country:%s, type:%s...\r\n", 
	                   tuningString, countryRegion, getSourceType( (CHANNEL_DATA*)pCapInfo->channel ) ));	
			   
	char* scanResult = scanChannel( (CHANNEL_DATA*)pCapInfo->channel, deviceName, tuningString,  
		                               countryRegion,  (int)streamType );
	len = strlen( scanResult );
	//trim \r\n
	while ( len > 1 && scanResult[len-1] == '\r' || scanResult[len-1] == '\n' ) 
	{
		scanResult[len-1] = 0; 
		len--;
	};
	if ( scanResult != NULL && len >= 1024 )
		slog(( env, "Scan Channel: scan result too long to log here, check native log\r\n" ));
	else
		slog(( env, "Scan Channel:%s,  ret='%s'.\r\n", tuningString, scanResult==NULL ? "": scanResult  ));	

	jstring jretstr;
	if ( scanResult != NULL )
	{
		jretstr = env->NewStringUTF(scanResult );
		free( scanResult );
	} else
		jretstr = env->NewStringUTF( "" );

	return jretstr;
	//$NEW

}


