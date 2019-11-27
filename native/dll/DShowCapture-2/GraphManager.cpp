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
#include "DShowUtilities.h"
#include "sage_DShowCaptureDevice.h"
#include "DShowCapture.h"
#include "FilterGraphTools.h"
#include "QAMCtrl.h"
#include "TunerPlugin.h"
#include "sage_EncodingException.h"
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "Plextor/AUDIO_PREVIEW.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
#include "Channel.h"
#include "SageDTV.h"
#include "uniapi.h"
FilterGraphTools graphTools;

TV_TYPE GetTVType( DShowCaptureInfo *pCapInfo );
void SetTVType( DShowCaptureInfo *pCapInfo, TV_TYPE BDATVType, BOOL bIsNetwork  );
void AddVideoCaptureFilters(JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, ICaptureGraphBuilder2* pBuilder );
void AddBDAVideoCaptureFilters(JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, int devCaps);
void BDAGraphConnectDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, char* szFileExt );
void BDAGraphDisconnectDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
BOOL CheckFakeBDACrossBar( JNIEnv *env, char* capFiltName, int CapFiltNum, char* BDAFiltDevName, int BDAFiltDevNameSize );
HRESULT ConnectCaptureCrossbar( IGraphBuilder* pGraph, IBaseFilter *pVideoCapture, IBaseFilter *pCrossbar, 
							  IPin**ppVideoPin, IPin** ppAudioPin, DShowCaptureInfo* pCapInfo );
void RemoveUnregisterCrossbar( DShowCaptureInfo *pCapInfo, IBaseFilter* pCrossbar0 );
void BDAGraphConnectDebugRawDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph  );
//void BDAGraphDisconnectDebugRawDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
int  LoadTuningEntryTable( JNIEnv* env, DShowCaptureInfo *pCapInfo );
void SaveScanTuningTable( JNIEnv* env, DShowCaptureInfo *pCapInfo );
void TearDownCAM( JNIEnv* env, DShowCaptureInfo *pCapInfo );
int LoadTuneTable( JNIEnv* env, DShowCaptureInfo *pCapInfo, TV_TYPE BDATVType );
int SetupBDAQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo );
void ReleaseDiSEQC( DShowCaptureInfo* pCapInfo );
int InitSageDTV( DShowCaptureInfo* pCapInfo );
int HasTunerTag( char* filterName );
int GetIndexFromTunerTag( char* filterName );
char* GetTypeFromTunerTag( char* filterName, char* typeBuf, int bufSize );
void RemoveTunerTag( char* filterName );
TV_TYPE String2TVType( char* szTvType );
static char* BDATypeCapString( DWORD BDACap );
int BDATypeNum( DWORD dwBDACap );
static char* GetBDAType( DWORD dwBDACap, int nIndex );
void SwitchBDAConnect( DShowCaptureInfo* pCapInfo );

HANDLE mutex350Encoder = NULL;

//ZQ REMOVE ME
#undef sage_DShowCaptureDevice_BDA_ATSC
#define sage_DShowCaptureDevice_BDA_ATSC    0x2000000L
#undef sage_DShowCaptureDevice_BDA_QAM
#define sage_DShowCaptureDevice_BDA_QAM     0x0200000L
#undef sage_DShowCaptureDevice_BDA_DVB_T
#define sage_DShowCaptureDevice_BDA_DVB_T   0x0400000L
#undef sage_DShowCaptureDevice_BDA_DVB_S
#define sage_DShowCaptureDevice_BDA_DVB_S   0x0800000L
#undef sage_DShowCaptureDevice_BDA_DVB_C
#define sage_DShowCaptureDevice_BDA_DVB_C   0x1000000L
//ZQ REMOVE ME
#define BDA_CAPTURE_ALL ( sage_DShowCaptureDevice_BDA_DVB_T|sage_DShowCaptureDevice_BDA_DVB_C |sage_DShowCaptureDevice_BDA_DVB_S | \
			              sage_DShowCaptureDevice_BDA_ATSC | sage_DShowCaptureDevice_BDA_QAM )

DShowCaptureInfo *CreateDshowCaptureInfo( JNIEnv *env, char* videoCaptureFilterName, jint capDevNum, 
										   jint deviceCaps, char* tvType, char* TuningMode, char* Country );

 /*
 * Class:     sage_DShowCaptureDevice
 * Method:    initGraph0
 * Signature: (Ljava/lang/String;IILjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowCaptureDevice_initGraph0
  (JNIEnv *env, jobject jo, jstring jcapDevName, jint capDevNum, jint deviceCaps, jstring jtvtype, jstring jtuningMode, jstring jcountry )
{
	char videoCaptureFilterName[256];
	char tvType[32];
	char Country[256];
	char TuningMode[32];
	long int DeviceCaps;
	int detectedCaptureNum = BDATypeNum(deviceCaps);		//Determine the number of BDA tuners 

	const char* tempStr = env->GetStringUTFChars(jcapDevName, NULL);
	strncpy(videoCaptureFilterName, tempStr, sizeof(videoCaptureFilterName));
	env->ReleaseStringUTFChars(jcapDevName, tempStr);

	const char* TVTypeStr = env->GetStringUTFChars( jtvtype, NULL);
	strncpy( tvType, TVTypeStr, sizeof(tvType) ); 
	env->ReleaseStringUTFChars( jtvtype, TVTypeStr);

	const char* pCountry = env->GetStringUTFChars( jcountry, NULL);
	strncpy( Country, pCountry, sizeof(Country) );
	env->ReleaseStringUTFChars( jcountry, pCountry );

	const char* pTuningMode = env->GetStringUTFChars( jtuningMode, NULL);
	strncpy( TuningMode, pTuningMode, sizeof(TuningMode) );
	env->ReleaseStringUTFChars( jtuningMode, pTuningMode);

	if ( (deviceCaps & BDA_CAPTURE_ALL) && detectedCaptureNum > 0 ) ////ZQ REMOVE ME
	{
		/* ----------------
		* JRE: HVR-55xx have 3 BDA tuners
		* Future proof by setting a maximum of 4 BDA tuners
		*/
		DShowCaptureInfo *rv;
		DShowCaptureInfo *rvs[MAX_CAPTURE_NUM]={0};

		if (detectedCaptureNum > MAX_CAPTURE_NUM)
		{
			slog((env, "Capture has %i BDA tuners, we support a maximum of %i.\r\n", detectedCaptureNum, MAX_CAPTURE_NUM));
			return 0;
		}
		DeviceCaps = deviceCaps & ~BDA_CAPTURE_ALL;
		
		int i, j;

		for (i = 0; i<detectedCaptureNum; i++)
		{
			strncpy(TuningMode, GetBDAType(deviceCaps, i), sizeof(TuningMode));
			slog((env, "Create DshowCapture %d %s.\r\n", i, TuningMode));
			rvs[i] = CreateDshowCaptureInfo(env, videoCaptureFilterName, capDevNum, deviceCaps, tvType,
				TuningMode, Country);
		}


		for (i = 0; i<detectedCaptureNum; i++)
		{
			if (rvs[i])
			{
				rvs[i]->captureNum = detectedCaptureNum;
				for (j = 0; j<detectedCaptureNum; j++)
					rvs[i]->captures[j] = rvs[j];
			}
		}

		rv = new DShowCaptureInfo;
		memset( rv, 0, sizeof(rv) );

		for (i = 0; i<detectedCaptureNum; i++)
		{
			if (rvs[i])
			{
				memcpy(rv, rvs[i], sizeof(DShowCaptureInfo));
				break;
			}
		}

		if (i == detectedCaptureNum)		// no valid rvs
			return 0;

		setChannelDev( (CHANNEL_DATA*)rv->channel, (void*)rv );
		return (jlong)rv;

	} else
	{

		return (jlong)CreateDshowCaptureInfo( env, videoCaptureFilterName, capDevNum, deviceCaps, tvType, 
			                                  TuningMode, Country );
	}


}
DShowCaptureInfo *CreateDshowCaptureInfo( JNIEnv *env, char* videoCaptureFilterName, jint capDevNum, 
										   jint deviceCaps, char* tvType, char* TuningMode, char* Country )
{
	try
	{
		int i;
		HRESULT hr;
		DShowCaptureInfo rv={0};
		char TunerType[16]={0};
		ZeroMemory(&rv, sizeof(DShowCaptureInfo));
		strncpy( rv.TAG, "DSHOW", sizeof(rv.TAG) );

		//const char* tempStr = env->GetStringUTFChars(jcapDevName, NULL);
		//strncpy(rv.videoCaptureFilterName, tempStr, sizeof(rv.videoCaptureFilterName));
		//env->ReleaseStringUTFChars(jcapDevName, tempStr);

		strncpy( rv.videoCaptureFilterName, videoCaptureFilterName, sizeof(rv.videoCaptureFilterName) );
		//processing passed in configure name with tag<...>
		strncpy( rv.videoCaptureFilterNameOrg, videoCaptureFilterName, sizeof(rv.videoCaptureFilterNameOrg) );
		if ( HasTunerTag( rv.videoCaptureFilterName ) )
		{
			int index = GetIndexFromTunerTag( rv.videoCaptureFilterName );
			if ( index >= 0 )
				capDevNum = index;
			GetTypeFromTunerTag( rv.videoCaptureFilterName, TunerType, sizeof(TunerType) );
			RemoveTunerTag( rv.videoCaptureFilterName );
		}

		strncpy( rv.tvType, tvType, sizeof(rv.tvType) ); 
		if ( TunerType[0] != 0 )
			strncpy( rv.tvType, TunerType, sizeof(rv.tvType) );

		strncpy( rv.Country, Country, sizeof(rv.Country) );

		strncpy( rv.TuningMode, TuningMode, sizeof(rv.TuningMode) );

		if ( rv.TuningMode[0] && String2TVType( rv.TuningMode ) && strstr(rv.TuningMode, "Cable" ) == NULL ) //is BDA type
		{
			strncpy( rv.tvType, rv.TuningMode, sizeof(rv.tvType) );
		}

		rv.videoCaptureFilterNum = capDevNum;
		rv.captureConfig = deviceCaps;

		slog((env, "initGraph0 (ver 2.1.0) called capDev='%s-%d', Country:'%s', TuningMode:%s, TunerType:%s Cap:0x%x\r\n", rv.videoCaptureFilterName,
			rv.videoCaptureFilterNum, rv.Country, rv.TuningMode, rv.tvType, deviceCaps ));
		CoInitializeEx(NULL, COM_THREADING_MODE);


		SPRINTF( rv.TunerIDName, sizeof(rv.TunerIDName), "%s-%d", rv.videoCaptureFilterName, rv.videoCaptureFilterNum );
		//convert  illegal characters
		for ( i = 0; i<(int)strlen(rv.TunerIDName) && i<(int)sizeof(rv.TunerIDName); i++ )
		{
			if ( rv.TunerIDName[i] == '\\' || rv.TunerIDName[i] == '/' || rv.TunerIDName[i] == ':' || rv.TunerIDName[i] == '?' ||
				 rv.TunerIDName[i] == '*'  || rv.TunerIDName[i] == '<' || rv.TunerIDName[i] == '>'  || rv.TunerIDName[i] == '|' )
			rv.TunerIDName[i] = '-';
		}

		if (!mutex350Encoder)
		{
			// We only need to do this if there's a 350 in use, and if there is then we've set a registry flag to indicate so
			HKEY myKey;
			DWORD holder;
			DWORD readType;
			DWORD hsize = sizeof(holder);
			if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, "SYSTEM\\CurrentControlSet\\Services\\Globespan\\Parameters\\ivac15\\Driver", 0, NULL, 
				&myKey) == ERROR_SUCCESS)
			{
				if (RegQueryValueEx(myKey, "HcwTVOutColorBars", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
				{
					if (holder == 0)
					{
						slog((env, "Setting up mutex lock for PVR-350 sync\r\n"));
						mutex350Encoder = CreateMutex(NULL, FALSE, "Global\\SageTV350Sync");
					}
				}
				RegCloseKey(myKey);
			}
		}

		// Create the capture graph builder
		CComPtr<ICaptureGraphBuilder2> pGraphBuilder = NULL;
		hr = CoCreateInstance (CLSID_CaptureGraphBuilder2 , NULL, CLSCTX_INPROC,
			IID_ICaptureGraphBuilder2, (void **) &pGraphBuilder);
		ENCEXCEPT_RET0(sage_EncodingException_DIRECTX_INSTALL);

		// Create the filter graph
		CComPtr<IGraphBuilder> pGraph = NULL;
		hr = CoCreateInstance (CLSID_FilterGraph, NULL, CLSCTX_INPROC,
			IID_IGraphBuilder, (void **) &pGraph);
		ENCEXCEPT_RET0(sage_EncodingException_DIRECTX_INSTALL);

		// Attach the filter graph to the capture graph
		hr = pGraphBuilder->SetFiltergraph(pGraph);
		ENCEXCEPT_RET0(sage_EncodingException_DIRECTX_INSTALL);

		// Add our graph to the running object table, which will allow
		// the GraphEdit application to "spy" on our graph
	#ifdef _DEBUG
		AddToRot(pGraph, &rv.dwRegister);
	#endif

		rv.filterState = 0;
		// Add the video capture source filter, throw a Java exception in that function if it fails
		if ( capMask( deviceCaps, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ) )  //ZQ
		{
			rv.channel = (CHANNEL_DATA*)malloc( sizeof( CHANNEL_DATA) );
			memset( rv.channel, 0, sizeof( CHANNEL_DATA) );
			openChannel( (CHANNEL_DATA*)rv.channel, &rv );
			enableFrqTableUpdate( (CHANNEL_DATA*)rv.channel );

			if ( capMask( deviceCaps, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ) )
				GetVirtualTunerDevName( env, rv.videoCaptureFilterName, rv.videoCaptureFilterNum, 
											 rv.bdaCaptureFilterName, sizeof(rv.bdaCaptureFilterName),
											 rv.virtualTunerParam,    sizeof(rv.virtualTunerParam), &rv.virtualTunerMask );
			else
				CheckFakeBDACrossBar( env, rv.videoCaptureFilterName, rv.videoCaptureFilterNum, 
										   rv.bdaCaptureFilterName, sizeof(rv.bdaCaptureFilterName) );

			slog((env, "Add BDA filters (capture:'%s') for device=%s\r\n", rv.bdaCaptureFilterName,
																		 rv.videoCaptureFilterName));
			AddBDAVideoCaptureFilters( env, &rv, pGraph, deviceCaps );             
			
		}
		else
		{
			slog((env, "Add analog filters for device=%s\r\n", rv.videoCaptureFilterName));
		    AddVideoCaptureFilters(env, &rv, pGraph, pGraphBuilder );
		}

		jthrowable exc = env->ExceptionOccurred();
		if (exc)
		{
			// Nothing to rethrow, its already thrown!
			return 0;
		}

		rv.pBuilder = pGraphBuilder;
		rv.pBuilder->AddRef();
		rv.pGraph = pGraph;
		rv.pGraph->AddRef();
		
		// Obtain interface for media control
		pGraph->QueryInterface(IID_IMediaControl,(LPVOID *) &(rv.pMC));
		
		DShowCaptureInfo *dynRv = new DShowCaptureInfo;

		memcpy(dynRv, &rv, sizeof(DShowCaptureInfo));

		//DShowCaptureInfo is changed, must reset
		if ( capMask( deviceCaps, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ) )  //ZQ
		{
			setChannelDev( (CHANNEL_DATA*)dynRv->channel, dynRv ); 
			InitSageDTV( dynRv );
		}

		return dynRv;
	}
	catch (...)
	{
		throwEncodingException(env, sage_EncodingException_CAPTURE_DEVICE_INSTALL, 0);
		CoUninitialize();
		return 0;
	}
}


/*
 * Class:     sage_DShowCaptureDevice
 * Method:    teardownGraph0
 * Signature: (J)V
 */
void TeardownBDAGraph(JNIEnv *env, DShowCaptureInfo* pCapInfo );
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_teardownGraph0
  (JNIEnv *env, jobject jo, jlong capInfo)
{
	/*
	 * This stops all graph activity and then destroys the entire thing.
	 */
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if (!pCapInfo) return;
	slog((env, "teardownGraph0 called for device='%s'\r\n", pCapInfo->videoCaptureFilterName));

	GETENCODERMUTEX;
	
	int detectedCaptureNum = pCapInfo->captureNum;
	if (detectedCaptureNum > 0)
	{
		int i;
		for (i = 0; i < detectedCaptureNum; i++)
		{
			if (pCapInfo->captures[i]->dwBDAType != pCapInfo->dwBDAType)
			{
				slog((env, "TeardownBDAGraph called for device='%i %s'\r\n", i, pCapInfo->videoCaptureFilterName));
				TeardownBDAGraph(env, pCapInfo->captures[i]);
			}
		}
	}

	//Release CAM
	TearDownCAM( env, pCapInfo );
	ReleaseQAM(  env, pCapInfo );
	ReleaseTunerPlugin( env, pCapInfo );
slog((env, "teardownGraph0 step-1 \r\n" ));
	ReleaseDiSEQC( pCapInfo );
slog((env, "teardownGraph0 step0 \r\n" ));

    // Remove filter graph from the running object table   
    if (pCapInfo->dwRegister)
        RemoveFromRot(pCapInfo->dwRegister);
slog((env, "teardownGraph0 step1 \r\n" ));    
	//crossbar has unregistered crossbar connected
	graphTools.DisconnectFilter( pCapInfo->pGraph, pCapInfo->pTVAudio );
	graphTools.DisconnectFilter( pCapInfo->pGraph, pCapInfo->pTVTuner );
	RemoveUnregisterCrossbar(  pCapInfo, pCapInfo->pCrossbar );
slog((env, "teardownGraph0 step2 \r\n" ));
	//graphTools.DisconnectFilter( pCapInfo->pGraph, pCapInfo->pBDACapture );
    //graphTools.DisconnectFilter( pCapInfo->pGraph, pCapInfo->pSplitter );

	graphTools.DisconnectAllPins( pCapInfo->pGraph );
	graphTools.RemoveAllFilters( pCapInfo->pGraph );
    pCapInfo->filterState = 0;
slog((env, "teardownGraph0 step3 \r\n" ));
    SAFE_RELEASE(pCapInfo->pCCSink);
    SAFE_RELEASE(pCapInfo->pSink);
    SAFE_RELEASE(pCapInfo->pAudioCaptureFilter);
    SAFE_RELEASE(pCapInfo->pAudioCompressor);
    SAFE_RELEASE(pCapInfo->pVideoCompressor);
    SAFE_RELEASE(pCapInfo->pEncoder);
    SAFE_RELEASE(pCapInfo->pCrossbar);
    SAFE_RELEASE(pCapInfo->pTVAudio);
    SAFE_RELEASE(pCapInfo->pTVTuner);
    SAFE_RELEASE(pCapInfo->pVideoCaptureFilter);
    SAFE_RELEASE(pCapInfo->pMC);
slog((env, "teardownGraph0 step4 \r\n" ));  
    SAFE_RELEASE(pCapInfo->pBDANetworkProvider);
    SAFE_RELEASE(pCapInfo->pTuningSpace);
    SAFE_RELEASE(pCapInfo->pTuner);
    SAFE_RELEASE(pCapInfo->pBDATuner);
	SAFE_RELEASE(pCapInfo->pBDADemod);
	SAFE_RELEASE(pCapInfo->pBDACapture);
	SAFE_RELEASE(pCapInfo->pBDATIF);
	SAFE_RELEASE(pCapInfo->pBDASectionTbl);
	SAFE_RELEASE(pCapInfo->pBDADemux);

 //   SAFE_RELEASE(pCapInfo->pBDANetworkProvider2);
 //   SAFE_RELEASE(pCapInfo->pTuningSpace2);
 //   SAFE_RELEASE(pCapInfo->pTuner2);
 //   SAFE_RELEASE(pCapInfo->pBDATuner2);
	//SAFE_RELEASE(pCapInfo->pBDADemod2);
	//SAFE_RELEASE(pCapInfo->pBDACapture);
	//SAFE_RELEASE(pCapInfo->pBDATIF2);
	//SAFE_RELEASE(pCapInfo->pBDASectionTbl2);
	//SAFE_RELEASE(pCapInfo->pBDADemux2);
	//SAFE_RELEASE(pCapInfo->pSplitter2);
	//SAFE_RELEASE(pCapInfo->pDebugSink2);
	//SAFE_RELEASE(pCapInfo->pDebugSrcSink2);
	//SAFE_RELEASE(pCapInfo->pPrePlugin2);  

	//SAFE_RELEASE(pCapInfo->pTeeFilter);
slog((env, "teardownGraph0 step4-1 0x%x \r\n", pCapInfo->pSplitter ));  
	SAFE_RELEASE(pCapInfo->pSplitter);
slog((env, "teardownGraph0 step5 \r\n" ));	
	SAFE_RELEASE(pCapInfo->pDebugSink);
	SAFE_RELEASE(pCapInfo->pDebugSrcSink);
	try {
		graphTools.DisconnectFilter( pCapInfo->pGraph, pCapInfo->pPrePlugin );
		SAFE_RELEASE(pCapInfo->pPrePlugin);
	} catch (...) {	}
	
	//BDAGraphDisconnectDebugRawDumpSink( env, pCapInfo, pCapInfo->pGraph  );
slog((env, "teardownGraph0 step6. \r\n" ));
	if (pCapInfo->pGraph)
		DestroyGraph(pCapInfo->pGraph);

    //SAFE_RELEASE(pCapInfo->pBuilder);
	if ( pCapInfo->pBuilder )
	{
slog((env, "teardownGraph0 pBuilder. \r\n" ));
		int r = pCapInfo->pBuilder->Release();
		while( r > 0 )
		{
			slog((env, "....teardownGraph0 pBuilder. ref=%d \r\n", r ));
			r = pCapInfo->pBuilder->Release();
		}
		pCapInfo->pBuilder = NULL;
	}

    //SAFE_RELEASE(pCapInfo->pGraph);
	if ( pCapInfo->pGraph )
	{
slog((env, "teardownGraph0 pGraph. \r\n" ));
		int r = pCapInfo->pGraph->Release();
		while( r > 0 )
		{
slog((env, "....teardownGraph0 pGraph. ref=%d \r\n", r ));
			r = pCapInfo->pGraph->Release();
		}
		pCapInfo->pGraph = NULL;
	}

slog((env, "teardownGraph0 step8 \r\n" ));

	RELEASEENCODERMUTEX;

	if (pCapInfo->encodeParams)
		delete pCapInfo->encodeParams;
slog((env, "teardownGraph0 step9 \r\n" ));
	if ( pCapInfo->channel )
	{
		closeChannel( ( CHANNEL_DATA*)pCapInfo->channel );
		free( pCapInfo->channel );
	}

	for (int i = 0; i<detectedCaptureNum; i++)
	{
		if (pCapInfo->captures[i])
			delete pCapInfo->captures[i];
	}

	delete pCapInfo;
	//memory intruder detecte ZQ.
	//VirtualFree( pCapInfo, sizeof(DShowCaptureInfo), MEM_RELEASE );

	CoUninitialize();
slog((env, "teardownGraph0 step10 \r\n" ));
}

void TeardownBDAGraph(JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	if (!pCapInfo) return;
	slog((env, "teardown BDA Graph0 of '%s'\r\n", pCapInfo->videoCaptureFilterName));

	if ( pCapInfo->filterState > 1 )
	{
		TearDownCAM( env, pCapInfo );
		ReleaseQAM(  env, pCapInfo );
		ReleaseTunerPlugin( env, pCapInfo );
		ReleaseDiSEQC( pCapInfo );
	}

	graphTools.DisconnectAllPins( pCapInfo->pGraph );
    if (pCapInfo->dwRegister)
        RemoveFromRot(pCapInfo->dwRegister);	
	graphTools.RemoveAllFilters( pCapInfo->pGraph );

    pCapInfo->filterState = 0;
	pCapInfo->dwBDAType = 0;
	pCapInfo->dwTuneState = 0;
    SAFE_RELEASE(pCapInfo->pBDANetworkProvider);
    SAFE_RELEASE(pCapInfo->pTuningSpace);
    SAFE_RELEASE(pCapInfo->pTuner);
    SAFE_RELEASE(pCapInfo->pBDATuner);
	SAFE_RELEASE(pCapInfo->pBDADemod);
	SAFE_RELEASE(pCapInfo->pBDACapture);
	SAFE_RELEASE(pCapInfo->pBDATIF);
	SAFE_RELEASE(pCapInfo->pBDASectionTbl);
	SAFE_RELEASE(pCapInfo->pBDADemux);

	try {
		graphTools.DisconnectFilter( pCapInfo->pGraph, pCapInfo->pPrePlugin );
		SAFE_RELEASE(pCapInfo->pPrePlugin);
	} catch (...) {	}
	
		//BDAGraphDisconnectDebugRawDumpSink( env, pCapInfo, pCapInfo->pGraph  );
slog((env, "teardownGraph0 step6a. \r\n" ));
	if (pCapInfo->pGraph)
		DestroyGraph(pCapInfo->pGraph);

    //SAFE_RELEASE(pCapInfo->pBuilder);
	if ( pCapInfo->pBuilder )
	{
slog((env, "teardownGraph0 pBuilder. \r\n" ));
		int r = pCapInfo->pBuilder->Release();
		while( r > 0 )
		{
			slog((env, "....teardownGraph0 pBuilder. ref=%d \r\n", r ));
			r = pCapInfo->pBuilder->Release();
		}
		pCapInfo->pBuilder = NULL;
	}

    //SAFE_RELEASE(pCapInfo->pGraph);
	if ( pCapInfo->pGraph )
	{
slog((env, "teardownGraph0 pGraph. \r\n" ));
		int r = pCapInfo->pGraph->Release();
		while( r > 0 )
		{
slog((env, "....teardownGraph0 pGraph. ref=%d \r\n", r ));
			r = pCapInfo->pGraph->Release();
		}
		pCapInfo->pGraph = NULL;
	}

	slog((env, "teardown BDA Graph0 of '%s' is done. \r\n", pCapInfo->videoCaptureFilterName));

}


/*
 * Class:     sage_DShowCaptureDevice
 * Method:    updateColors0
 * Signature: (JIIIII)I[
 */
JNIEXPORT jintArray JNICALL Java_sage_DShowCaptureDevice_updateColors0
  (JNIEnv *env, jobject jo, jlong capInfo, jint brightness, jint contrast, jint huey, jint saturation,
	jint sharpness)
{
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if (pCapInfo->pVideoCaptureFilter == NULL) return env->NewIntArray(5);
	IAMVideoProcAmp* videoProc = NULL;
	if (FAILED(pCapInfo->pVideoCaptureFilter->QueryInterface(IID_IAMVideoProcAmp, (void**)&videoProc)))
		return env->NewIntArray(5);
	jint retColors[5];
	double br = brightness;
	double con = contrast;
	double hue = huey;
	double sat = saturation;
	double sha = sharpness;
	long pMin, pMax, pSteppingDelta, pDefault, pCaps, val;
	HRESULT hr = videoProc->GetRange(VideoProcAmp_Brightness, &pMin, &pMax, &pSteppingDelta, &pDefault, &pCaps);
	BOOL hasDefault = (br >= 0);
	if (br < 0) br = ((pDefault-pMin)*255.0)/(pMax-pMin);
	if (hr == S_OK)
	{
		hr = videoProc->Set(VideoProcAmp_Brightness, (int)(((pMax-pMin)*br)/255 + pMin), VideoProcAmp_Flags_Auto );
		hr = videoProc->Set(VideoProcAmp_Brightness, (int)(((pMax-pMin)*br)/255 + pMin), VideoProcAmp_Flags_Manual);
		slog(( "Set capture brightness: (%d %d)-%d val:%d hr:0x%x %s-%d\r\n", pMin, pMax, brightness, (int)(((pMax-pMin)*br)/255 + pMin), hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum)); 
	}
	if (hasDefault)
		retColors[0] = brightness;
	else
	{
		hr = videoProc->Get(VideoProcAmp_Brightness, &val, &pCaps);
		retColors[0] = ((val-pMin)*255)/(pMax-pMin);
		slog(( "Get captue brightness: %d hr:0x%x %s-%d\r\n", val, hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum)); 
	}

	hr = videoProc->GetRange(VideoProcAmp_Contrast, &pMin, &pMax, &pSteppingDelta, &pDefault, &pCaps);
	hasDefault = (con >= 0);
	if (con < 0) con = ((pDefault-pMin)*255.0)/(pMax-pMin);
	if (hr == S_OK)
	{
		hr = videoProc->Set(VideoProcAmp_Contrast, (int)(((pMax-pMin)*con)/255 + pMin), VideoProcAmp_Flags_Auto );
		hr = videoProc->Set(VideoProcAmp_Contrast, (int)(((pMax-pMin)*con)/255 + pMin), VideoProcAmp_Flags_Manual);
		//slog(( "Set capture contrast:%d hr:0x%x %s-%d\r\n", con, hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum )); 
	}
	if (hasDefault)
		retColors[1] = contrast;
	else
	{
		hr = videoProc->Get(VideoProcAmp_Contrast, &val, &pCaps);
		retColors[1] = ((val-pMin)*255)/(pMax-pMin);
		//slog(( "Get captue contrast:%d hr:0x%x %s-%d\r\n", val, hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum)); 
	}

	hr = videoProc->GetRange(VideoProcAmp_Hue, &pMin, &pMax, &pSteppingDelta, &pDefault, &pCaps);
	hasDefault = (hue >= 0);
	if (hue < 0) hue = ((pDefault-pMin)*255.0)/(pMax-pMin);
	if (hr == S_OK)
	{
		hr = videoProc->Set(VideoProcAmp_Hue, (int)(((pMax-pMin)*hue)/255 + pMin), VideoProcAmp_Flags_Auto );
		hr = videoProc->Set(VideoProcAmp_Hue, (int)(((pMax-pMin)*hue)/255 + pMin), VideoProcAmp_Flags_Manual);
		//slog(( "Set capture hue:%d hr:0x%x %s-%d\r\n", hue, hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum)); 
	}
	if (hasDefault)
		retColors[2] = huey;
	else
	{
		hr = videoProc->Get(VideoProcAmp_Hue, &val, &pCaps);
		retColors[2] = ((val-pMin)*255)/(pMax-pMin);
		//slog(( "Get captue hue:%d hr:0x%x %s-%d\r\n", val, hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum)); 
	}

	hr = videoProc->GetRange(VideoProcAmp_Saturation, &pMin, &pMax, &pSteppingDelta, &pDefault, &pCaps);
	hasDefault = (sat >= 0);
	if (sat < 0) sat = ((pDefault-pMin)*255.0)/(pMax-pMin);
	if (hr == S_OK)
	{
		hr = videoProc->Set(VideoProcAmp_Saturation, (int)(((pMax-pMin)*sat)/255 + pMin), VideoProcAmp_Flags_Auto );
		hr = videoProc->Set(VideoProcAmp_Saturation, (int)(((pMax-pMin)*sat)/255 + pMin), VideoProcAmp_Flags_Manual);
		//slog(( "Set capture Saturation: %d hr:0x%x %s-%d\r\n", sat, hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum)); 
	}
	if (hasDefault)
		retColors[3] = saturation;
	else
	{
		hr = videoProc->Get(VideoProcAmp_Saturation, &val, &pCaps);
		retColors[3] = ((val-pMin)*255)/(pMax-pMin);
		//slog(( "Get captue Saturation: %d hr:0x%x%s-%d\r\n", val, hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum)); 
	}

	hr = videoProc->GetRange(VideoProcAmp_Sharpness, &pMin, &pMax, &pSteppingDelta, &pDefault, &pCaps);
	hasDefault = (sha >= 0);
	if (sha < 0) sha = ((pDefault-pMin)*255.0)/(pMax-pMin);
	if (hr == S_OK)
	{
		hr = videoProc->Set(VideoProcAmp_Sharpness, (int)(((pMax-pMin)*sha)/255 + pMin), VideoProcAmp_Flags_Auto );
		hr = videoProc->Set(VideoProcAmp_Sharpness, (int)(((pMax-pMin)*sha)/255 + pMin), VideoProcAmp_Flags_Manual);
		//slog(( "Set capture sharpness:%d hr:0x%x %s-%d\r\n", sha, hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum)); 
	}
	if (hasDefault)
		retColors[4] = sharpness;
	else
	{
		hr = videoProc->Get(VideoProcAmp_Sharpness, &val, &pCaps);
		retColors[4] = ((val-pMin)*255)/(pMax-pMin);
		//slog(( "Get captue sharpness: %d hr:0x%x %s-%d\r\n", val, hr, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum)); 
	}

	SAFE_RELEASE(videoProc);
	jintArray rv = env->NewIntArray(5);
	env->SetIntArrayRegion(rv, 0, 5, retColors);
	return rv;
}


/*
 * Class:     sage_DShowCaptureDevice
 * Method:    getNativeVideoPreviewConfigHandle0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowCaptureDevice_getNativeVideoPreviewConfigHandle0
  (JNIEnv *env, jobject jo, jlong capInfo)
{
	if (!capInfo) return 0;
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if (pCapInfo->pPreviewVideo)
	{
		// Return the graph pointer so they can get both IVideoWindow & IBasicVideo
		return (jlong) pCapInfo->pGraph;
	}
	return 0;
}

void AddVideoCaptureFilters(JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph,
							ICaptureGraphBuilder2* pBuilder )
{
	HRESULT hr;
	CComPtr<IBaseFilter> pSrcVideoFilter;
	hr = FindFilterByName(&pSrcVideoFilter, AM_KSCATEGORY_CAPTURE,
		pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, pCapInfo->deviceName, sizeof(pCapInfo->deviceName) );
	ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
	char* devNameEnd = strstr(pCapInfo->deviceName, "{");
	if (devNameEnd)
		*devNameEnd = '\0';

    // Add Capture filter to our graph.
    hr = pGraph->AddFilter(pSrcVideoFilter, NULL);
	ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);

	CComPtr<IBaseFilter> pTVTuner = NULL;
	CComPtr<IBaseFilter> pTVAudio = NULL;
	CComPtr<IBaseFilter> pCrossbar = NULL;

	CComPtr<IPin> pCapVideoIn = NULL;
	// At least we know how to do this by ourself or we wouldn't have gotten here
	// The crossbar will be the filter that connects up to the analog video input pin
	// of the capture filter.
	{
		pCapVideoIn.p = FindVideoCaptureInputPin(pSrcVideoFilter);
		if (!pCapVideoIn.p)
		{
			hr = E_FAIL;
			ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
		}

		// The filter that can connect to this pin is the crossbar for this capture setup.
		hr = FindUpstreamFilter(&pCrossbar, AM_KSCATEGORY_CROSSBAR, pCapVideoIn.p, pCapInfo->deviceName);
		ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);


		// Add crossbar filter to our graph. We at least need this.
		hr = pGraph->AddFilter(pCrossbar, NULL);
		ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);

	}


	// UPDATE: 11/11/04 - There's been a bunch of weird tuning problems in 2.1 and this
	// is one of the main things I changed. Let's try the old graph building and see
	// how that works now.

	// Find the video & audio tuner input pins on the crossbar filter
	/*
	long numIn=0, numOut=0;
	IAMCrossbar* pRealCross = NULL;
	hr = pCrossbar->QueryInterface(IID_IAMCrossbar, (void**) &pRealCross);
	ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
	//ENCEXCEPT_RET("Unable to get crossbar interface from the crossbar");
	pRealCross->get_PinCounts(&numOut, &numIn);
	CComPtr<IPin> pVTuneCross=NULL;
	CComPtr<IPin> pATuneCross=NULL;
	long relatedPin, pinType;

	for (int i = 0; i < numIn; i++)
	{
		pRealCross->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);
		if (pinType == PhysConn_Video_Tuner && !pVTuneCross)
		{
			GetPin(pCrossbar, PINDIR_INPUT, i, &(pVTuneCross.p));
		}
		else if (pinType == PhysConn_Audio_Tuner && !pATuneCross)
		{
			GetPin(pCrossbar, PINDIR_INPUT, i, &(pATuneCross.p));
		}
	}
	SAFE_RELEASE(pRealCross);
	*/

	//ZQ. handle more than one crossbar hardware configuration, we need search a path over crossbar from 
	//capture filter to video tuner and audio tuner.
	CComPtr<IPin> pVTuneCross=NULL;
	CComPtr<IPin> pATuneCross=NULL;

	hr = ConnectCaptureCrossbar( pGraph, pSrcVideoFilter, pCrossbar, &(pVTuneCross.p), &(pATuneCross.p), pCapInfo );

	// The pin that can connect to this is the tuner
	if ( hr == S_OK && pVTuneCross && pATuneCross)
	{
		hr = FindUpstreamFilter(&pTVTuner, AM_KSCATEGORY_TVTUNER, pVTuneCross, pCapInfo->deviceName);
		if (SUCCEEDED(hr))
		{
			hr = pGraph->AddFilter(pTVTuner, NULL);
			TEST_AND_PRINT

			hr = FindUpstreamFilter(&pTVAudio, AM_KSCATEGORY_TVAUDIO, pATuneCross, pCapInfo->deviceName);
			if (SUCCEEDED(hr))
			{
				hr = pGraph->AddFilter(pTVAudio, NULL);
				TEST_AND_PRINT
			}
			IPin* pTVTunerVideoPin = NULL;
			IPin* pTVAudioIn = NULL;
			if (pTVAudio)
				pTVAudioIn = FindPinByName(pTVAudio, NULL, PINDIR_INPUT);
			// The video output pin of the tuner is the one that's not audio
			IEnumPins* pEm = NULL;
			hr = pTVTuner->EnumPins(&pEm);
			if (SUCCEEDED(hr))
			{
				pEm->Reset();
				ULONG cFetched;
				IPin* pPin = NULL;
				int index = 0;
				while(hr = pEm->Next(1, &pPin, &cFetched), hr == S_OK)
				{
					PIN_INFO PinInfo;
					hr = pPin->QueryPinInfo(&PinInfo);
					if(SUCCEEDED(hr) && PinInfo.dir == PINDIR_OUTPUT)
					{
						IEnumMediaTypes *mtEnum = NULL;
						hr = pPin->EnumMediaTypes(&mtEnum);
						BOOL hasAudio = FALSE;
						if (SUCCEEDED(hr))
						{
							AM_MEDIA_TYPE *pMT = NULL;
							while (S_OK == mtEnum->Next(1, &pMT, NULL))
							{
								if (pMT->majortype == MEDIATYPE_AnalogAudio)
								{
									DeleteMediaType(pMT);
									hasAudio = TRUE;
									break;
								}
								DeleteMediaType(pMT);
							}
							SAFE_RELEASE(mtEnum);
						}
						if (!hasAudio && !pTVTunerVideoPin)
						{
							pTVTunerVideoPin = pPin;
							pTVTunerVideoPin->AddRef();
						}
						else if (pTVAudioIn)
						{
							hr = pGraph->Connect(pPin, pTVAudioIn);
							if (SUCCEEDED(hr))
							{
								SAFE_RELEASE(pTVAudioIn);
							}
							HTESTPRINT(hr);
						}
					}
					pPin->Release();
				}
				pEm->Release();
			}
			// In case it didn't work above.
			SAFE_RELEASE(pTVAudioIn);

			if (pTVTunerVideoPin && pVTuneCross)
			{
				// Sometimes this fails, but its OK
				hr = pGraph->ConnectDirect(pTVTunerVideoPin, pVTuneCross, NULL);
				HTESTPRINT(hr);
			}
			SAFE_RELEASE(pTVTunerVideoPin);

			if (pTVAudio && pATuneCross)
			{
				IPin* outPin = FindPinByName(pTVAudio, NULL, PINDIR_OUTPUT);
				if (outPin)
				{
					// Sometimes this fails, but its OK
					hr = pGraph->ConnectDirect(outPin, pATuneCross, NULL);
					HTESTPRINT(hr);
				}
				SAFE_RELEASE(outPin);
			}
		}
	}


	pCapInfo->pVideoCaptureFilter = pSrcVideoFilter;
	pCapInfo->pVideoCaptureFilter->AddRef();
	if (pCrossbar)
	{
		pCapInfo->pCrossbar = pCrossbar;
		pCapInfo->pCrossbar->AddRef();
	}
	if (pTVTuner)
	{
		pCapInfo->pTVTuner = pTVTuner;
		pCapInfo->pTVTuner->AddRef();
	}
	if (pTVAudio)
	{
		pCapInfo->pTVAudio = pTVAudio;
		pCapInfo->pTVAudio->AddRef();
	}
}

//ZQ
#define MAX_CROSSBAR_NUM   6
//find Video Tune input pin and Audio input pin in crossbars(crossbar)
HRESULT ConnectCaptureCrossbar( IGraphBuilder* pGraph, IBaseFilter *pSrcCapture, IBaseFilter *pCrossbar, 
							  IPin**ppVideoPin, IPin** ppAudioPin, DShowCaptureInfo* pCapInfo )
{
	if ( pCrossbar == NULL )
		return E_FAIL;
	CComPtr<IBaseFilter> pCrossbar1=NULL;
	IAMCrossbar* pRealCross[MAX_CROSSBAR_NUM] = {NULL};
	
	HRESULT hr;
	int index = 0, i, j;
	long relatedPin, pinType;
	long numOut, numIn;
	IPin  *pVideoPin = NULL, *pAudioPin = NULL;

	*ppVideoPin = NULL;
	*ppAudioPin = NULL;
	//search video pin that can connect to Video tuner
	pCrossbar1 = pCrossbar;
	pCrossbar->AddRef();
	do {

		hr = pCrossbar1->QueryInterface(IID_IAMCrossbar, (void**) &pRealCross[index] );
		pRealCross[index]->get_PinCounts(&numOut, &numIn);

		for ( i = 0; i < numIn; i++ )
		{
			pRealCross[index]->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);
			if (pinType == PhysConn_Video_Tuner)
			{
				CComPtr<IPin> pPin = NULL;
				hr = GetPin( pCrossbar1, PINDIR_INPUT, i, &pPin );
				if (SUCCEEDED(hr))
				{
					CComPtr<IBaseFilter> pTVTuner = NULL;
					hr = FindUpstreamFilter(&pTVTuner, AM_KSCATEGORY_TVTUNER, pPin, pCapInfo->deviceName);
					if (SUCCEEDED(hr))
					{
						pVideoPin = pPin;
						break;
					}
				}
			}
		}

		/* if not find Video tuner pin, search upstream pin in crossbar category */
		if ( pVideoPin == NULL )
		{
			IEnumMoniker *pEnum = NULL;
			IBaseFilter *p;
			hr = graphTools.EnumFilterFirst( AM_KSCATEGORY_CROSSBAR, &pEnum, &p ); 
			while (  hr == S_OK )
			{
				pGraph->AddFilter(p, NULL);
				hr = graphTools.ConnectFilters( pGraph, p, pCrossbar1 );
				if ( hr == S_OK ) 
				{
					pCrossbar1.p = p;
					SAFE_RELEASE(p);
					if ( index < MAX_CROSSBAR_NUM )
						index++;
					break; 
				}
				pGraph->RemoveFilter(p);
				SAFE_RELEASE(p);
				hr = graphTools.EnumFilterNext( pEnum, &p );
			}

			if ( pEnum != NULL )
				graphTools.EnumFilterClose( pEnum );

			if ( hr != S_OK )
				break;

		}

	} while ( pVideoPin == NULL );

	
	//search audio pin that can connect to audio tuner
	// check connected crossbars if there is a crossbar can connect to audio tuner
	for ( j = 0; pRealCross[j] != NULL; j++ )
	{
		pRealCross[j]->get_PinCounts(&numOut, &numIn);
		for ( i = 0; i < numIn; i++ )
		{
			pRealCross[j]->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);
			if ( pinType == PhysConn_Audio_Tuner )
			{
				CComPtr<IBaseFilter> pCrossbar = NULL ;
				CComPtr<IPin> pPin = NULL;
				pRealCross[j]->QueryInterface(IID_IBaseFilter, (void**)&pCrossbar);
				if ( pCrossbar != NULL )
				{
					hr = GetPin( pCrossbar, PINDIR_INPUT, i, &pPin );
					if ( SUCCEEDED(hr) )
					{
						CComPtr<IBaseFilter> pTVAudio = NULL;
						hr = FindUpstreamFilter(&pTVAudio, AM_KSCATEGORY_TVAUDIO, pPin, pCapInfo->deviceName);
						if ( SUCCEEDED(hr) )
						{
							pAudioPin = pPin;
							break;
						}
					}
				}
			}
		}
	}
	//if there is no crossbar in video path, search crossbar for itself
	if( pAudioPin == NULL )
	{
		pCrossbar1 = pCrossbar;
		do {
			hr = pCrossbar1->QueryInterface(IID_IAMCrossbar, (void**) &pRealCross[index] );
			pRealCross[index]->get_PinCounts(&numOut, &numIn);

			for ( i = 0; i < numIn; i++ )
			{
				pRealCross[index]->get_CrossbarPinInfo(FALSE, i, &relatedPin, &pinType);
				if (pinType == PhysConn_Audio_Tuner)
				{
					CComPtr<IPin> pPin=NULL;
					hr = GetPin( pCrossbar1, PINDIR_INPUT, i, &pPin );
					if (SUCCEEDED(hr))
					{
						CComPtr<IBaseFilter> pTVAudio = NULL;
						hr = FindUpstreamFilter(&pTVAudio, AM_KSCATEGORY_TVAUDIO, pPin, pCapInfo->deviceName);
						if (SUCCEEDED(hr))
						{
							pAudioPin = pPin;
							break;
						}
					}
				}
			}

			/* if not find audio tuner pin, search upstream pin in crossbar category */
			if ( pAudioPin == NULL )
			{
				IEnumMoniker *pEnum = NULL;
				IBaseFilter *p=NULL;
				hr = graphTools.EnumFilterFirst( AM_KSCATEGORY_CROSSBAR, &pEnum, &p ); 
				while (  hr == S_OK )
				{
					pGraph->AddFilter(p, NULL);
					hr = graphTools.ConnectFilters( pGraph, p, pCrossbar1 );
					if ( hr == S_OK ) 
					{
						pCrossbar1.p = p;
						SAFE_RELEASE(p);
						if ( index < MAX_CROSSBAR_NUM )
							index++;
						break; 
					}
					pGraph->RemoveFilter(p);
					SAFE_RELEASE(p);
					hr = graphTools.EnumFilterNext( pEnum, &p );
				}

				if ( pEnum != NULL )
					graphTools.EnumFilterClose( pEnum );

				if ( hr != S_OK )
					break;

			}
		}  while ( pAudioPin == NULL );
	}

    // Connect crossbar to capture filter:
	// Connect up the crossbar to the capture filter. This is where it will fail
	// if we're trying to use a capture device already in use
	CComPtr<IPin> pCapVideoIn = NULL;
	pCapVideoIn.p = FindVideoCaptureInputPin(pSrcCapture);
	IPin* pCrossVideoOut = FindPin(pCrossbar, PINDIR_OUTPUT, &MEDIATYPE_AnalogVideo, NULL);
	if ( pCrossVideoOut && pCapVideoIn )
	{
		/*
		 * Normally this will fail if the device is already in use, but sometimes
		 * it fails anyways if you're using the non-primary tuner. But I've also seen
		 * it fail using the primary tuner for the Roslyn card.
		 */
		hr = pGraph->ConnectDirect(pCrossVideoOut, pCapVideoIn, NULL);
		SAFE_RELEASE(pCrossVideoOut);
		//SAFE_RELEASE(pCapVideoIn);
		HTESTPRINT(hr);
		/*if (pCapInfo->videoCaptureFilterNum == 0)
		{
			ENCEXCEPT_RET("Unable to connect analog video cross streams");
		}*/
	}
	// In case only one or the other was there
	SAFE_RELEASE(pCrossVideoOut);

	//connect audio capture to crossbar that maybe is not the same videopin crossbar
	IPin* pCapAudioIn = FindPin(pSrcCapture, PINDIR_INPUT, &MEDIATYPE_AnalogAudio, NULL);
	if ( pCapAudioIn )
	{
		for ( j = 0; pRealCross[j] != NULL; j++ )
		{
			CComPtr<IBaseFilter> pCrossbar = NULL ;
			pRealCross[j]->QueryInterface(IID_IBaseFilter, (void**)&pCrossbar);
			if ( pCrossbar != NULL )
			{
				IPin* pCrossAudioOut = FindPin(pCrossbar, PINDIR_OUTPUT, &MEDIATYPE_AnalogAudio, NULL);
				if ( pCrossAudioOut )
				{
					hr = pGraph->Connect(pCrossAudioOut, pCapAudioIn);
					if ( !FAILED( hr ) )
						break;
					HTESTPRINT(hr);
					SAFE_RELEASE(pCrossAudioOut);
					
				}

			}
		}
	    SAFE_RELEASE(pCapAudioIn);
	}

	*ppVideoPin = pVideoPin;
	*ppAudioPin = pAudioPin;


	return S_OK;
}

JNIEXPORT jstring JNICALL Java_sage_DShowCaptureDevice_getBroadcastStandard0
		(JNIEnv *env, jobject jo, jlong capInfo )
{

	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if ( pCapInfo == NULL )
	{
		slog((env, "Device is not ready for GetBroadcastStandard(): pCapInfo=NULL\r\n"));
		return env->NewStringUTF("");
	}
	slog((env, "GetBroadcastStandard %s for device:'%s-%d'\r\n", pCapInfo->tvType, 
		     pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ));

	return env->NewStringUTF( pCapInfo->tvType );
}


char * TVTypeString( TV_TYPE BDATVType )
{
	switch( BDATVType ) {
	case ATSC: return ( "ATSC" ); break;
	case DVBT: return ( "DVB-T" ); break;
	case DVBC: return ( "DVB-C" ); break;
	case DVBS: return ( "DVB-S" ); break;
	case QAM : return ( "QAM" ); break;
	case ATSC_N: return ("ATSC_N"); break;
	default: return "UNKNOW";
	}
	return "";
}

TV_TYPE String2TVType( char* szTvType )
{
	enum TV_TYPE BDATVType = UNKNOWN;
	if ( !strnicmp( "ATSC", szTvType, 4 ) )
		BDATVType = ATSC;
	else
	if ( !strnicmp( "DVBT", szTvType, 4 ) || !strnicmp( "DVB-T", szTvType, 5 ))
		BDATVType = DVBT;
	else
	if ( !strnicmp( "DVBC", szTvType, 4 ) || !strnicmp( "DVB-C", szTvType, 5 ))
		BDATVType = DVBC;
	else
	if ( !strnicmp( "DVBS", szTvType, 4 ) || !strnicmp( "DVB-S", szTvType, 5 ))
		BDATVType = DVBS;
	else
	if ( !strnicmp( "CABLE", szTvType, 5 ) || !strnicmp( "QAM", szTvType, 3 ))  //ATSC + QAM
		BDATVType = QAM;
	else
	if ( !strnicmp( "ATSC_N", szTvType, 6 ) )
		BDATVType = ATSC_N;

	return BDATVType;
}

static char* BDATypeCapString( DWORD dwBDACap )
{
	static char BDACapString[32]={0};
	int i;
	BDACapString[0] = 0x0;
	i = 0;
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_DVB_T )
	{
		strcat( BDACapString, "DVB-T" );
		i++;
	}
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_DVB_C )
	{
		if ( i ) strcat( BDACapString, "|" );
		strcat( BDACapString, "DVB-C" );
		i++;
	}
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_DVB_S )
	{
		if ( i ) strcat( BDACapString, "|" );
		strcat( BDACapString, "DVB-S" );
		i++;
	}
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_QAM )
	{
		if ( i ) strcat( BDACapString, "|" );
		strcat( BDACapString, "QAM" );
		i++;
	}
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_ATSC )
	{
		if ( i ) strcat( BDACapString, "|" );
		strcat( BDACapString, "ATSC" );
		i++;
	}
	return BDACapString;
}

int BDATypeNum( DWORD dwBDACap )
{
	static char BDACapString[32]={0};
	int i;
	BDACapString[0] = 0x0;
	i = 0;
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_DVB_T )
		i++;
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_DVB_C )
		i++;
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_DVB_S )
		i++;
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_QAM )
		i++;
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_ATSC )
		i++;
	return i;
}

static char* GetBDAType( DWORD dwBDACap, int nIndex )
{
	int i;
	i = 0;
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_ATSC )
	{
		if ( i == nIndex )
			return "ATSC";
		i++;
	}
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_DVB_T )
	{
		if ( i == nIndex )
			return "DVB-T";
		i++;
	}
	if ( dwBDACap & sage_DShowCaptureDevice_BDA_DVB_C )
	{
		if ( i == nIndex )
			return "DVB-C";
		i++;
	}

	if ( dwBDACap & sage_DShowCaptureDevice_BDA_DVB_S )
	{
		if ( i == nIndex )
			return "DVB-S";
		i++;
	}

	if ( dwBDACap & sage_DShowCaptureDevice_BDA_QAM )
	{
		if ( i == nIndex )
			return "QAM";
		i++;
	}


	return "UNKNOWN";
	
}

TV_TYPE GetTVType( DShowCaptureInfo *pCapInfo )
{
	enum TV_TYPE BDATVType = UNKNOWN;
	if ( !strnicmp( "ATSC", pCapInfo->tvType, 4 ) )
		BDATVType = ATSC;
	else
	if ( !strnicmp( "DVBT", pCapInfo->tvType, 4 ) || !strnicmp( "DVB-T", pCapInfo->tvType, 5 ))
		BDATVType = DVBT;
	else
	if ( !strnicmp( "DVBC", pCapInfo->tvType, 4 ) || !strnicmp( "DVB-C", pCapInfo->tvType, 5 ))
		BDATVType = DVBC;
	else
	if ( !strnicmp( "DVBS", pCapInfo->tvType, 4 ) || !strnicmp( "DVB-S", pCapInfo->tvType, 5 ))
		BDATVType = DVBS;
	else
	if ( !strnicmp( "CABLE", pCapInfo->tvType, 5 ) || !strnicmp( "QAM", pCapInfo->tvType, 3 ))  //ATSC + QAM
		BDATVType = QAM;
	else
	if ( !strnicmp( "ATSC_N", pCapInfo->tvType, 6 ) )
		BDATVType = ATSC_N;

	return BDATVType;
}

void SetTVType( DShowCaptureInfo *pCapInfo, TV_TYPE BDATVType, BOOL bIsNetwork )
{
	switch( BDATVType ) {
	case ATSC: strcpy( pCapInfo->tvType, "ATSC" ) ; break;
	case DVBT: strcpy( pCapInfo->tvType, "DVB-T" ); break;
	case DVBC: strcpy( pCapInfo->tvType, "DVB-C" ); break;
	case DVBS: strcpy( pCapInfo->tvType, "DVB-S" ); break;
	case QAM : strcpy(pCapInfo->tvType, "QAM" ); break;
	case ATSC_N : strcpy(pCapInfo->tvType, "ATSC NET" ); break;
	default: pCapInfo->tvType[0] = 0x0; break;
	}
	if ( bIsNetwork )
		strcat( pCapInfo->tvType, " NET" );
}

BOOL IsNetworkProvider( DShowCaptureInfo *pCapInfo )
{
	return strstr( pCapInfo->tvType,"NET" ) != NULL;
}

//HRESULT  SetupTunerFreq( DShowCaptureInfo* pCapInfo, long freq, long bandwidth, int pol );
//HRESULT  SetupTunerModulator( DShowCaptureInfo* pCapInfo, 
//		int modulation, int symrate, int innerFEC, int innerFECRate, int outerFEC, int outerFECRate, int invserion );
HRESULT SetupBDATuningSpace( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph )
{
	HRESULT hr = E_FAIL;
	TV_TYPE BDATVType=GetTVType( pCapInfo );
	BOOL bUseNetworkProvider = IsNetworkProvider(pCapInfo);

	if ( capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ) )
		return S_OK;

	CComPtr <ITuningSpace> piTuningSpace;
	slog( (env, "Setup %s BDATuningSpace (%d  %d)\r\n", pCapInfo->tvType, BDATVType, bUseNetworkProvider ) );

	if ( FAILED( hr = graphTools.InitTuningSpace( piTuningSpace, BDATVType ) ))
	{
		slog( (env, "Couldn't initial %s TuningSpace\r\n", pCapInfo->tvType ) );
		return hr;
	}
	if ( piTuningSpace == NULL )
	{
		slog( (env, "Couldn't initial %s TuningSpace.\r\n", pCapInfo->tvType ) );
		return E_FAIL;
	}

	pCapInfo->pTuningSpace = piTuningSpace;
	pCapInfo->pTuningSpace->AddRef();

	CLSID CLSIDNetworkType;
	if ( !bUseNetworkProvider )
	{
		CComBSTR bstrNetworkType;
		if (FAILED(hr = piTuningSpace->get_NetworkType(&bstrNetworkType)))
		{
			slog( (env,"Failed to get TuningSpace Network Type\r\n" ) );
			SAFE_RELEASE(pCapInfo->pTuningSpace);
			return hr;
		}
		if (FAILED(hr = CLSIDFromString( bstrNetworkType, &CLSIDNetworkType)))
		{
			slog ( (env,"Couldn't get CLSIDFromString\r\n" ) );
			SAFE_RELEASE(pCapInfo->pTuningSpace);
			return hr;
		}
	} else
	{
		//Microsoft Network Provider "{B2F3A67C-29DA-4C78-8831-091ED509A475}"
		if (FAILED(hr = CLSIDFromString( L"{B2F3A67C-29DA-4C78-8831-091ED509A475}", &CLSIDNetworkType)))
		{
			slog ( (env,"Couldn't get CLSIDFromString for Network provider.\r\n" ) );
			SAFE_RELEASE(pCapInfo->pTuningSpace);
			return hr;
		}
	}

	// create the network provider based on the clsid obtained from the tuning space, add it into GraphBuilder
	CComPtr <IBaseFilter>    piBDANetworkProvider;  
	if (FAILED(hr = graphTools.AddFilter( pGraph, CLSIDNetworkType, &piBDANetworkProvider, L"SageTV Network Provider")))
	{
		slog( (env,"Failed to add Network Provider to the graph\r\n" ) );
	    SAFE_RELEASE(pCapInfo->pTuningSpace);
		return hr;
	}
	pCapInfo->pBDANetworkProvider = piBDANetworkProvider;
	pCapInfo->pBDANetworkProvider->AddRef();

	//if (FAILED(hr = pCapInfo->piBDANetworkProvider->QueryInterface(__uuidof(IScanningTuner), reinterpret_cast<void **>(&pCapInfo->piTuner))))
	CComPtr <IScanningTuner> piTuner;
	if (FAILED(hr = piBDANetworkProvider->QueryInterface(__uuidof(IScanningTuner), reinterpret_cast<void **>(&piTuner))))
	{
	  	slog( (env, "Failed while interfacing Tuner with Network Provider\r\n" ) );
		SAFE_RELEASE(pCapInfo->pBDANetworkProvider);
	    SAFE_RELEASE(pCapInfo->pTuningSpace);
		return hr;
	}
	pCapInfo->pTuner = piTuner;
	pCapInfo->pTuner->AddRef();

//$NEW
	if ( BDATVType != UNKNOWN )
	{
		LoadTuneTable( env, pCapInfo, BDATVType );
	}
//$NEW
	if ( BDATVType == ATSC )
	{
		//setup a default channel to test tuner
		CComPtr <ITuneRequest> pTuneRequest;
		if ( FAILED( hr = graphTools.CreateATSCTuneRequest( piTuningSpace, pTuneRequest, 50, 3, 48, 0 ) ))
		{
			slog( (env, "Failed to create ATSC tuning request to tuner hr:%x\r\n", hr) );
		} else
		if (FAILED( pCapInfo->pTuner->put_TuningSpace( piTuningSpace )) )
		{
			slog( (env, "Failed to set ATSC tuning space to tuner\r\n") );
		}

		CComQIPtr <IATSCChannelTuneRequest> piATSCTuneRequest(pTuneRequest);
		if (FAILED(hr = pCapInfo->pTuner->put_TuneRequest(piATSCTuneRequest)))
		{
			slog( (env, "Failed to submit ATSC tune request to the Network Provider\r\n") );
		}

	} else
	if ( BDATVType == DVBT )
	{
		//setup a default channel to test tuner
		CComPtr <ITuneRequest> pTuneRequest;
		if ( FAILED (hr = graphTools.CreateDVBTTuneRequest( piTuningSpace, pTuneRequest, 4112, 544, 545 )))
		{
			slog( (env, "Failed to create DVB-T tuning request to tuner hr:%x\r\n", hr) );
		} else
		if (FAILED( pCapInfo->pTuner->put_TuningSpace( piTuningSpace )) )
		{
			slog( (env, "Failed to set DVB-T tuning space to tuner\r\n") );
		}

		CComQIPtr <IDVBTuneRequest> piDVBTuneRequest(pTuneRequest);
		if (FAILED(hr = pCapInfo->pTuner->put_TuneRequest(piDVBTuneRequest)))
		{
			slog( (env, "Failed to submit DVB-T tune request to the Network Provider\r\n") );
		}
	}else
	if ( BDATVType == DVBC )
	{
		//setup a default channel to test tuner
		CComPtr <ITuneRequest> pTuneRequest;
		if ( FAILED( hr=graphTools.CreateDVBCTuneRequest( piTuningSpace, pTuneRequest, 338000, 6875, BDA_MOD_64QAM ) ))
		{
			slog( (env, "Failed to create DVB-C tuning request to tuner hr:%x\r\n", hr) );
		} else
		if (FAILED( pCapInfo->pTuner->put_TuningSpace( piTuningSpace )) )
		{
			slog( (env, "Failed to set DVB-C tuning space to tuner\r\n") );
		}

		CComQIPtr <IDVBTuneRequest> piDVBTuneRequest(pTuneRequest);

		//ZQ test for Rasmus
		//SetupTunerFreq( pCapInfo, 338000*1000, -1, -1 );
		//SetupTunerModulator( pCapInfo, BDA_MOD_64QAM, 6875, -1, -1, -1, -1, -1 );

		if (FAILED(hr = pCapInfo->pTuner->put_TuneRequest(piDVBTuneRequest)))
		{
			slog( (env, "Failed to submit DVB-C tune request to the Network Provider\r\n") );
		}
	} else
	if ( BDATVType == DVBS )
	{
		//setup a default channel to test tuner
		CComPtr <ITuneRequest> pTuneRequest;
		if ( FAILED( hr=graphTools.CreateDVBSTuneRequest( piTuningSpace, pTuneRequest, 338000, 6875, 1, 20 ) ))
		{
			slog( (env, "Failed to create DVB-S tuning request to tuner hr:%x\r\n", hr) );
		} else
		if (FAILED( pCapInfo->pTuner->put_TuningSpace( piTuningSpace )) )
		{
			slog( (env, "Failed to set DVB-S tuning space to tuner\r\n") );
		}

		CComQIPtr <IDVBTuneRequest> piDVBTuneRequest(pTuneRequest);
		if (FAILED(hr = pCapInfo->pTuner->put_TuneRequest(piDVBTuneRequest)))
		{
			slog( (env, "Failed to submit DVB-S tune request to the Network Provider\r\n") );
		}

	} else
	if ( BDATVType == QAM ) 
	{
		//setup a default channel to test tuner
		CComPtr <ITuneRequest> pTuneRequest;
		if ( FAILED( hr = graphTools.CreateQAMTuneRequest( piTuningSpace, pTuneRequest, 12, 205250000, BDA_MOD_256QAM ) ))
		{
			slog( (env, "Failed to create QAM tuning request to tuner hr:%x\r\n", hr) );
		} else
		if (FAILED( pCapInfo->pTuner->put_TuningSpace( piTuningSpace )) )
		{
			slog( (env, "Failed to set QAM tuning space to tuner hr:0x%x (wait for connecting) \r\n", hr) );
		}

		CComQIPtr <IATSCChannelTuneRequest> piATSCTuneRequest(pTuneRequest);
		if (FAILED(hr = pCapInfo->pTuner->put_TuneRequest(piATSCTuneRequest)))
		{
			slog( (env, "Failed to submit QAM tune request to the Network Provider hr:0x%x (wait for connecting)\r\n", hr) );
		}
		
		SetupBDAQAM( env, pCapInfo );
	}


	return S_OK;
}

void ReleaseBDATuningSpace( JNIEnv* env, DShowCaptureInfo *pCapInfo )
{
	slog( (env, "Release %s BDATuningSpace\r\n", pCapInfo->tvType ) );
    SAFE_RELEASE(pCapInfo->pTuner);
    SAFE_RELEASE(pCapInfo->pTuningSpace);
	graphTools.DisconnectFilter( pCapInfo->pGraph, pCapInfo->pBDANetworkProvider );
	graphTools.RemoveFilter( pCapInfo->pGraph, pCapInfo->pBDANetworkProvider );
    SAFE_RELEASE(pCapInfo->pBDANetworkProvider);
}

void AddBDAVideoCaptureFilters(JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, int devCaps )
{
    HRESULT hr = S_OK;;
	graphTools.SetLogOutput( env );


	if ( pCapInfo->filterState < 1 ) //have not add filters yet
	{
		TV_TYPE BDATVType = GetTVType( pCapInfo ); 

		//if it's a virtual tuner, we add TSSplitter only.
		if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
		{
			slog( (env,"Add Virtual Capture Device Filter %s.\r\n", pCapInfo->bdaCaptureFilterName ) );
			if ( BDATVType == UNKNOWN ) 
			{
				switch ( pCapInfo->virtualTunerMask ){ 
				case 0:
					if  ( !strcmp( pCapInfo->TuningMode, "Cable" ) )
						BDATVType = QAM;
					else
						BDATVType = ATSC;
					break;
				case 1: 	BDATVType = DVBT; break;
				case 2: 	BDATVType = DVBC; break;
				case 3:		BDATVType = DVBS; break;
				default:    BDATVType = ATSC;
				}
				SetTVType( pCapInfo, BDATVType, FALSE );
			}
			LoadTuneTable( env, pCapInfo, BDATVType );

		} else
		{
			if ( BDATVType == UNKNOWN ) 
			{
				if ( strstr(  pCapInfo->videoCaptureFilterName, "Silicondust HDHomeRun" ) && !stricmp( pCapInfo->TuningMode, "Cable" ) )
				{
					BDATVType = QAM;    //ZQ. hardcode HDHR QAM
					slog( (env, "It's a HDHomeRun QAM(US Cable) tuner\r\n") );
					SetTVType( pCapInfo, BDATVType, FALSE );
					if ( FAILED( hr = SetupBDATuningSpace( env, pCapInfo, pGraph ) ) )
							ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);

				} else
					slog( (env,"Not specify TV type ( Network type %s ), to search matching one.\r\n", pCapInfo->tvType ) );

			} else
			{
				if ( ATSC_N == BDATVType )
				{
					if ( !stricmp( pCapInfo->TuningMode, "Cable" ) )
						BDATVType = QAM;
					else
						BDATVType = ATSC;
					SetTVType( pCapInfo, BDATVType, FALSE );
					if ( FAILED( hr = SetupBDATuningSpace( env, pCapInfo, pGraph ) ) )
							ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
					
				}
				else
				{
					if ( FAILED( hr = SetupBDATuningSpace( env, pCapInfo, pGraph ) ) )
							ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
				}
			}

			//ZQ if we have multiple BDA cards, we need find the right one for BDA capture, 
			//but before the tuner is connected to network provider, we can't determine if tuner is connectable
			CComPtr <IBaseFilter> piBDADemux;
			if (FAILED(hr = graphTools.AddFilter( pGraph, CLSID_MPEG2Demultiplexer, &piBDADemux, L"BDA MPEG-2 Demultiplexer" )))
			{
				slog( (env,"Can not load BDA MPEG-2 Demultiplexer Device\r\n" ) );
				ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
			}
			pCapInfo->pBDADemux = piBDADemux;
			pCapInfo->pBDADemux->AddRef();

			CComPtr <IBaseFilter> piBDATIF;
			if (FAILED(hr = graphTools.AddFilterByName( pGraph, &piBDATIF, KSCATEGORY_BDA_TRANSPORT_INFORMATION, L"BDA MPEG2 Transport Information Filter" )))
			{
				slog( (env,"Can not load BDA MPEG2 Transport Information Filter\r\n") );
				ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
			}
			pCapInfo->pBDATIF = piBDATIF;
			pCapInfo->pBDATIF->AddRef();

			CComPtr <IBaseFilter> piSectionTbl;
			if (FAILED(hr = graphTools.AddFilterByDevicePath( pGraph, &piSectionTbl, 
				L"@device:sw:{083863F1-70DE-11D0-BD40-00A0C911CE86}\\{C666E115-BB62-4027-A113-82D643FE2D99}", L"MPEG-2 Sections and Tables" )))
			{
				slog( (env,"Can not load BDA MPEG2 Section and Tables  Filter\r\n") ); //only for FireDTV CAM/CI
				ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
			}
			pCapInfo->pBDASectionTbl = piSectionTbl;
			pCapInfo->pBDASectionTbl->AddRef();

		}

		LPWSTR CaptureDevName=NULL;
		LPWSTR CaptureFriendName=NULL;
		graphTools.strCopyA2W( CaptureDevName,  pCapInfo->bdaCaptureFilterName, strlen(pCapInfo->bdaCaptureFilterName) ) ;
		graphTools.strCopyA2W( CaptureFriendName,  pCapInfo->videoCaptureFilterName, strlen(pCapInfo->videoCaptureFilterName) ) ;
		CComPtr <IBaseFilter> piBDACapture;

		if (FAILED(hr = graphTools.AddFilterByDevicePath2( pGraph, &piBDACapture, CaptureDevName, CaptureFriendName )))
	    {
			SAFE_DELETE(CaptureDevName);
			slog( (env,"Failed adding filter by device path, Try to add Capture filter by name  '%s-%d'.\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
			hr = FindFilterByName(&(piBDACapture), AM_KSCATEGORY_CAPTURE,
							pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ,NULL, 0);
			if ( FAILED( hr ) )
				hr = FindFilterByName(&(piBDACapture), KSCATEGORY_BDA_RECEIVER_COMPONENT,
							pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ,NULL, 0);
				if ( FAILED( hr ) ) 
					hr = FindFilterByName(&(piBDACapture), KSCATEGORY_BDA_NETWORK_TUNER,
							pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ,NULL, 0);

			if ( FAILED( hr ) ) 
			{
				ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
			}  

			if ( !FAILED( hr ) )
			{
				hr = pGraph->AddFilter( piBDACapture, L"Virtual Device" );
			}
	    }
		SAFE_DELETE(CaptureDevName);
		SAFE_DELETE(CaptureFriendName);
		pCapInfo->pBDACapture = piBDACapture;
		pCapInfo->pBDACapture->AddRef();

		if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK )
			&& !strncmp( pCapInfo->videoCaptureFilterName, "PUSH TS SOURCE", 14 ) )
		{
			IFileSinkFilter* pfsf = NULL;
			pCapInfo->pBDACapture->QueryInterface( IID_IFileSinkFilter, (void**)&pfsf );
			LPWSTR pPathName = NULL;
			graphTools.strCopyA2W( pPathName, pCapInfo->virtualTunerParam );
			hr = pfsf->SetFileName( pPathName, NULL );
			if ( pPathName ) delete pPathName;
			SAFE_RELEASE(pfsf);
			slog(( env, "Set virtualtuner 'PUSH TS SOURCE' File name:'%s' hr=0x%x\r\n", pCapInfo->virtualTunerParam, hr ));
		}

		if ( pCapInfo->pSplitter == NULL )
		{
			CComPtr <IBaseFilter> piSplitter;
			if (FAILED(hr = graphTools.AddFilterByName( pGraph, &piSplitter, CLSID_LegacyAmFilterCategory, L"SageTV TS Splitter 3.0" )))
			{
				{
					slog( (env,"Can not load Sage TV Splitter Filter\r\n") );
					ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
				}
			}
			pCapInfo->pSplitter = piSplitter;
			pCapInfo->pSplitter->AddRef();
			slog( (env,"Loaded Sage TV Splitter Filter 0x%x\r\n", pCapInfo->pSplitter ) );
		}

		//Load Plugin Filter
		if ( pCapInfo->pPrePlugin == NULL ) {
			char PluginName[_MAX_PATH]={0};
			ReadAdditionalSettingString( env, "PluginFilter", "PrePlugin", PluginName, sizeof(PluginName) );
			if ( PluginName[0] )
			{
				LPWSTR pName=NULL;
				graphTools.strCopyA2W( pName,  PluginName, strlen(PluginName) ) ;

				try
				{
					CComPtr <IBaseFilter> piPlugin;
					if (FAILED(hr = graphTools.AddFilterByName( pGraph, &piPlugin, CLSID_LegacyAmFilterCategory, pName )))
					{
						if  (FAILED(hr = graphTools.AddFilterByName( pGraph, &piPlugin, CLSID_LegacyAmFilterCategory, pName )))
						{
							slog( ( env,"Can not load Plugin Filter '%s'\r\n", PluginName ) );
						}
					} else
					if ( piPlugin != NULL )
					{
						pCapInfo->pPrePlugin = piPlugin;
						pCapInfo->pPrePlugin->AddRef();
						slog( ( env,"Plugin Filter '%s' is loaded\r\n", PluginName ) );
					}
				} catch ( ... )
				{
					slog((env, "Plugin Filter '%s' has an exception, failed!. \r\n", PluginName ) );
				}

				SAFE_DELETE( pName );
			}
		}
		pCapInfo->dwBDAType = String2TVType( pCapInfo->tvType );
		ASSERT( pCapInfo->filterState == 0 );
		pCapInfo->filterState = 1;
		slog( (env,"BDA Filters are loaded\r\n") );
	}     
   
}
//ZQ

void RemoveUnregisterCrossbar( DShowCaptureInfo *pCapInfo, IBaseFilter* pCrossbar0 )
{
	IBaseFilter* Crossbar[MAX_CROSSBAR_NUM]={NULL};
	IAMCrossbar *pXBar = NULL;
	int index = 0, i;
	HRESULT hr;
	IBaseFilter* pCrossbar;
	pCrossbar = pCrossbar0;

	if ( pCapInfo == NULL || pCrossbar == NULL )
		return;

	while ( 1 )
	{
		hr = pCapInfo->pBuilder->FindInterface( &LOOK_UPSTREAM_ONLY, NULL, pCrossbar,
				IID_IAMCrossbar, (void**)&pXBar);

		if ( FAILED( hr ) )
			break;

		if ( SUCCEEDED(hr) && pXBar != NULL )
		{
			hr = pXBar->QueryInterface(IID_IBaseFilter, (void**)&pCrossbar);
			if ( FAILED( hr ) )
				break;
			if ( index < MAX_CROSSBAR_NUM )
				Crossbar[ index++ ] = pCrossbar;
			else
				break;
		}
	}

	for ( i = 0; i<index; i++ )
	{
		if ( Crossbar[i] != NULL )
		{
			graphTools.DisconnectFilter( pCapInfo->pGraph, Crossbar[i] );
			pCapInfo->pGraph->RemoveFilter( Crossbar[i] );
		}
	}

}

int LoadTuneTable( JNIEnv* env, DShowCaptureInfo *pCapInfo, TV_TYPE BDATVType )
{
	int ret = 1;
	if ( BDATVType == ATSC && !stricmp( pCapInfo->TuningMode, "Cable" ) )
	{
		SetupQAM( env, pCapInfo );
		if ( QAMTunerType( env, pCapInfo ) == 1  )
		{
			BDATVType = QAM;
			slog( (env, "It's a QAM tuner\r\n") );
		} else
		{
			if ( strstr(  pCapInfo->videoCaptureFilterName, "Silicondust HDHomeRun" ) )
			{
				BDATVType = QAM;    //ZQ. hardcode HDHR QAM
				slog( (env, "It's a HDHomeRun QAM tuner\r\n") );
			}
		}
	}
	setSourceType( (CHANNEL_DATA*)pCapInfo->channel, TVTypeString( BDATVType ) );
	setCountryRegion( (CHANNEL_DATA*)pCapInfo->channel, pCapInfo->Country );

	slog( (env, "LoadTuneTable %s\r\n", TVTypeString(BDATVType) ) );

	if ( loadScanTuningTable( (CHANNEL_DATA*)pCapInfo->channel ) < 0 )
	{
		/*
		char oldFileName[_MAX_PATH];
		char newFileName[_MAX_PATH];
		if ( BDATVType == DVBT || BDATVType== ATSC )
			SPRINTF( oldFileName, sizeof(oldFileName), "%s-%d.frq", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum );
		else
			SPRINTF( oldFileName, sizeof(oldFileName), "%s(%s)-%d.frq",  pCapInfo->videoCaptureFilterName, TVTypeString(BDATVType), pCapInfo->videoCaptureFilterNum );
		*/

		char newFileName[_MAX_PATH];
		SPRINTF( newFileName, sizeof(newFileName), "%s-%s.frq", SageGetTunerDeviceName( pCapInfo ), getSourceType( (CHANNEL_DATA*)pCapInfo->channel) );
		slog( (env, "Failed loading frq file %s\r\n", newFileName ) );

		/*
		if ( !rename( oldFileName, newFileName ) ) //convert old frq file name to new one
		{
			ret = loadScanTuningTable( (CHANNEL_DATA*)pCapInfo->channel );
		} else
		{
			//save a empty file name for edit
			saveScanTuningTable( (CHANNEL_DATA*)pCapInfo->channel );
			ret = 0;
		} */

	}
	return ret;
}

//Virtual capture device in
//CLSID_LegacyAmFilterCategory