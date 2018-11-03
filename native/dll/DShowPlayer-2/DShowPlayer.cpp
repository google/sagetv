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
#include "jni-util.h"
#include "DShowPlayer.h"
#include "../DShowCapture/DShowUtilities.h"
#include "sage_VideoFrame.h"
#include "JeffMixer.h"
#include "guids.h"
#include <streams.h>
//#include <qedit.h>
#ifdef FUJITSU_BUILD
#include <mmdeviceapi.h>
#include <endpointvolume.h>
#include <dwmapi.h>
#endif

BOOL APIENTRY DllMain( HANDLE hModule, 
                       DWORD  ul_reason_for_call, 
                       LPVOID lpReserved
					 )
{
    switch (ul_reason_for_call)
	{
		case DLL_PROCESS_ATTACH:
		case DLL_THREAD_ATTACH:
		case DLL_THREAD_DETACH:
		case DLL_PROCESS_DETACH:
			break;
    }
    return TRUE;
}


/*
 * Class:     sage_VideoFrame
 * Method:    init0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_VideoFrame_init0
  (JNIEnv *env, jobject jo)
{
	CoInitializeEx(NULL, COM_THREADING_MODE);
#ifdef FUJITSU_BUILD
	// Disable Aero Glass
	DwmEnableComposition(DWM_EC_DISABLECOMPOSITION);
#endif
}

/*
 * Class:     sage_VideoFrame
 * Method:    goodbye0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_VideoFrame_goodbye0
  (JNIEnv *env, jobject jo)
{
	CoUninitialize();
}

// The functions below are no longer in used in the Java code.
// The IMediaDet interface is depricated and the needed qedit.h header is not part of Windows SDK 7.1A
// jlong getFileDurationMyWay(JNIEnv *env, jstring jname)
// {
// 	HRESULT hr;
// 	jlong rv = 0;
// 	// 6/13/03 Try my old way to get the duration instead
// 	IGraphBuilder *pGraph = NULL;
// 	hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
// 		IID_IGraphBuilder, (void **)&pGraph);
// 	if (FAILED(hr)) return 0;
// 	IBaseFilter* pSrcFilter = NULL;
// 
// 	IFileSourceFilter* pfsf = NULL;
// 	hr = CoCreateInstance(CLSID_SharedAsyncFile, NULL, CLSCTX_INPROC_SERVER,
// 		IID_IBaseFilter, (void**)&pSrcFilter);
// 	if (FAILED(hr)) return 0;
// 	hr = pGraph->AddFilter(pSrcFilter, L"The Source");
// 	if (FAILED(hr)) return 0;
// 
// 	hr = pSrcFilter->QueryInterface(IID_IFileSourceFilter, (void**)&pfsf);
// 	if (FAILED(hr)) return 0;
// 	const jchar* name2 = env->GetStringChars(jname, NULL);
// 	hr = pfsf->Load((LPCOLESTR)name2,NULL);
// 	env->ReleaseStringChars(jname, name2);
// 	SAFE_RELEASE(pfsf);
// 	TEST_AND_PRINT
// 	if (FAILED(hr)) return 0;
// 
// 	IPin* mediaPin = FindPinByName(pSrcFilter, NULL, PINDIR_OUTPUT);
// 	if (!mediaPin)
// 	{
// 		elog((env, "Unable to find output pin on source filter\r\n"));
// 		SAFE_RELEASE(pSrcFilter);
// 		SAFE_RELEASE(pGraph);
// 		return 0;
// 	}
// 
// 	// Check to make sure it's MPEG-2
// 	IEnumMediaTypes *mtEnum = NULL;
// 	mediaPin->EnumMediaTypes(&mtEnum);
// 	AM_MEDIA_TYPE *pMT = NULL;
// 	BOOL mpegOK = 0;
// 	BOOL tsMpegOK = 0;
// 	while (!mpegOK && S_OK == mtEnum->Next(1, &pMT, NULL))
// 	{
// 		if (pMT->majortype == MEDIATYPE_Stream )
// 		{
// 			if ( pMT->subtype == MEDIASUBTYPE_MPEG2_PROGRAM )
// 				mpegOK = 1;
// 			if ( pMT->subtype == MEDIASUBTYPE_MPEG2_TRANSPORT )
// 				tsMpegOK = 1;
// 		}
// 		DeleteMediaType(pMT);
// 	}
// 	SAFE_RELEASE(mtEnum);
// 	SAFE_RELEASE(mediaPin);
// 	if ( mpegOK || tsMpegOK )
// 	{
// 		// First add the demultiplexor to the graph
// 		IBaseFilter* pMux = NULL;
// 		hr = CoCreateInstance(CLSID_MpegDeMux, NULL, CLSCTX_INPROC_SERVER,
// 			IID_IBaseFilter, (void**)&pMux);
// 		if (FAILED(hr))
// 		{
// 			elog((env, "Cannot load MPEG Demux filter\r\n"));
// 			SAFE_RELEASE(pSrcFilter);
// 			SAFE_RELEASE(pGraph);
// 			return 0;
// 		}
// 		hr = pGraph->AddFilter(pMux, L"MPEG-2 Splitter");
// 		if (FAILED(hr))
// 		{
// 			elog((env, "Cannot add MPEG Demux filter\r\n"));
// 			SAFE_RELEASE(pMux);
// 			SAFE_RELEASE(pSrcFilter);
// 			SAFE_RELEASE(pGraph);
// 			return 0;
// 		}
// 		hr = ConnectPins(pGraph, pSrcFilter, "Output", pMux, "Input", TRUE);
// 		if (FAILED(hr))
// 		{
// 			elog((env, "Cannot connect pins\r\n"));
// 			SAFE_RELEASE(pMux);
// 			SAFE_RELEASE(pSrcFilter);
// 			SAFE_RELEASE(pGraph);
// 			return 0;
// 		}
// 
// 		IPin* pSeekPin = FindPinByName(pMux, "Video", PINDIR_OUTPUT);
// 		if (pSeekPin)
// 		{
// 			IMediaSeeking* pSeeker = NULL;
// 			hr = pSeekPin->QueryInterface(IID_IMediaSeeking, (void**)&pSeeker);
// 			if (FAILED(hr))
// 			{
// 				elog((env, "Cannot get seeking interface\r\n"));
// 				SAFE_RELEASE(pMux);
// 				SAFE_RELEASE(pSrcFilter);
// 				SAFE_RELEASE(pGraph);
// 				return 0;
// 			}
// 
// 			pSeeker->SetTimeFormat(&TIME_FORMAT_MEDIA_TIME);
// 
// 			// Get the duration
// 			pSeeker->GetDuration(&rv);
// 			rv /= 10000;
// 			slog((env, "Duration is %d millis\r\n", (int)rv));
// 			SAFE_RELEASE(pSeekPin);
// 			SAFE_RELEASE(pSeeker);
// 		} else
// 		if ( tsMpegOK )
// 		{
// 			slog((env, "File is MPEG-2 TS file, but can't parser by SageTV MpegDemux, skip it(don't use MSFT's demux, it causes trouble)\r\n"));
// 			rv = -1;
// 		}
// 
// 		SAFE_RELEASE(pMux);
// 		SAFE_RELEASE(pSrcFilter);
// 		SAFE_RELEASE(pGraph);
// 
// 		return rv;
// 
// 	}
// 
// 	SAFE_RELEASE(pSrcFilter);
// 	SAFE_RELEASE(pGraph);
// 
// 	slog((env, "File is not a valid MPEG-2 PS file...using standard file duration detection\r\n"));
// 	return 0;
// }
// 
// /*
//  * Class:     sage_VideoFrame
//  * Method:    getFileDuration0
//  * Signature: (Ljava/lang/String;)J
//  */
// JNIEXPORT jlong JNICALL Java_sage_VideoFrame_getFileDuration0(JNIEnv *env, jclass jc,
// 																   jstring jname)
// {
// 	CoInitializeEx(NULL, COM_THREADING_MODE);
// 	IMediaDet* pMediaObj;
// 	jlong rv;
// 	double duration = 0;
// 	HRESULT hr;
// 	try
// 	{
// 		rv = getFileDurationMyWay(env, jname);
// 		if (rv)
// 		{
// 			CoUninitialize();
// 			return rv;
// 		}
// 
// 		hr = CoCreateInstance(CLSID_MediaDet, NULL, CLSCTX_INPROC_SERVER,
// 			IID_IMediaDet, (void**)&pMediaObj);
// 		TEST_AND_PRINT
// 		if (FAILED(hr))
// 		{
// 			CoUninitialize();
// 			return 0;
// 		}
// 
// 		const jchar* name = env->GetStringChars(jname, NULL);
// 		hr = pMediaObj->put_Filename((BSTR)name);
// 		env->ReleaseStringChars(jname, name);
// 		TEST_AND_PRINT
// 		if (FAILED(hr)) 
// 		{
// 			SAFE_RELEASE(pMediaObj);
// 			CoUninitialize();
// 			return 0;
// 		}
// 
// 		long numStreams;
// 		pMediaObj->get_OutputStreams(&numStreams);
// 		//slog((env, "NumStreams=%d\r\n", numStreams));
// 
// 		long currStream = 0;
// 		hr = pMediaObj->put_CurrentStream(currStream);
// 		TEST_AND_PRINT
// 		if (numStreams > 1)
// 		{
// 			// Find the video stream and select that
// 			GUID testGuid;
// 			pMediaObj->get_StreamType(&testGuid);
// 			while (testGuid != MEDIATYPE_Video && ++currStream < numStreams)
// 			{
// 				pMediaObj->put_CurrentStream(currStream);
// 				TEST_AND_PRINT
// 				pMediaObj->get_StreamType(&testGuid);
// 			}
// 				
// 		}
// 
// 		//slog((env, "CurrentStream=%d\r\n", currStream));
// 		hr = pMediaObj->get_StreamLength(&duration);
// 		TEST_AND_PRINT
// 	}
// 	catch (...)
// 	{
// 		const jchar* name = env->GetStringChars(jname, NULL);
// 		elog((env, "ERROR calculating duration for file %s\r\n", name));
// 		env->ReleaseStringChars(jname, name);
// 		rv = 0;
// 	}
// 
// 	rv = (jlong) (duration * 1000);
// 	slog((env, "Duration is %d millis\r\n", (int)rv));
// 
// 	SAFE_RELEASE(pMediaObj);
// 
// 	CoUninitialize();
// 	return rv;
// }

/*
 * Class:     sage_VideoFrame
 * Method:    setSystemVolume0
 * Signature: (F)F
 */
JNIEXPORT jfloat JNICALL Java_sage_VideoFrame_setSystemVolume0
  (JNIEnv *env, jclass jc, jfloat f)
{
#ifdef FUJITSU_BUILD
	CoInitialize(NULL);
	IMMDeviceEnumerator *deviceEnumerator = NULL;
	HRESULT hr = CoCreateInstance(__uuidof(MMDeviceEnumerator), NULL, CLSCTX_INPROC_SERVER, __uuidof(IMMDeviceEnumerator), (LPVOID *)&deviceEnumerator);
	if (FAILED(hr)) return 0;
	IMMDevice *defaultDevice = NULL; 

	hr = deviceEnumerator->GetDefaultAudioEndpoint(eRender, eConsole, &defaultDevice);
	if (FAILED(hr)) return 0;
	deviceEnumerator->Release();
	deviceEnumerator = NULL; 

	IAudioEndpointVolume *endpointVolume = NULL;
	hr = defaultDevice->Activate(__uuidof(IAudioEndpointVolume), CLSCTX_INPROC_SERVER, NULL, (LPVOID *)&endpointVolume);
	if (FAILED(hr)) return 0;
	defaultDevice->Release();
	defaultDevice = NULL; 

	hr = endpointVolume->SetMasterVolumeLevelScalar((float)f, NULL);
	endpointVolume->Release(); 
	CoUninitialize();
	return f;
#else
	CJeffMixer mixer(MIXERLINE_COMPONENTTYPE_DST_SPEAKERS,
		NO_SOURCE, MIXERCONTROL_CONTROLTYPE_VOLUME);
	f = MIN(1.0f, MAX(0, f));
	int level = (int) (65535 * f);
	level = MAX(0, MIN(65535, level));
	level = convertVolumeAntilog(level);
	level = MAX(0, MIN(65535, level));
	mixer.SetControlValueD(level);
	return f;
#endif
}

/*
 * Class:     sage_VideoFrame
 * Method:    getSystemVolume0
 * Signature: ()F
 */
JNIEXPORT jfloat JNICALL Java_sage_VideoFrame_getSystemVolume0
  (JNIEnv *env, jclass jc)
{
#ifdef FUJITSU_BUILD
	CoInitialize(NULL);
	IMMDeviceEnumerator *deviceEnumerator = NULL;
	HRESULT hr = CoCreateInstance(__uuidof(MMDeviceEnumerator), NULL, CLSCTX_INPROC_SERVER, __uuidof(IMMDeviceEnumerator), (LPVOID *)&deviceEnumerator);
	if (FAILED(hr)) return 0;
	

	IMMDevice *defaultDevice = NULL; 

	hr = deviceEnumerator->GetDefaultAudioEndpoint(eRender, eConsole, &defaultDevice);
	if (FAILED(hr)) return 0;
	deviceEnumerator->Release();
	deviceEnumerator = NULL; 

	IAudioEndpointVolume *endpointVolume = NULL;
	hr = defaultDevice->Activate(__uuidof(IAudioEndpointVolume), CLSCTX_INPROC_SERVER, NULL, (LPVOID *)&endpointVolume);
	if (FAILED(hr)) return 0;
	defaultDevice->Release();
	defaultDevice = NULL; 

	// -------------------------
	float currentVolume = 0;

	hr = endpointVolume->GetMasterVolumeLevelScalar(&currentVolume);
	if (FAILED(hr)) return 0;
	endpointVolume->Release(); 
	CoUninitialize();
	return currentVolume;
#else
	CJeffMixer mixer(MIXERLINE_COMPONENTTYPE_DST_SPEAKERS,
		NO_SOURCE, MIXERCONTROL_CONTROLTYPE_VOLUME);
	return (jfloat) (convertVolumeLog(mixer.GetControlValueD()) / 65535.0f);
#endif
}

/*
 * Class:     sage_VideoFrame
 * Method:    setSystemMute0
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sage_VideoFrame_setSystemMute0
  (JNIEnv *env, jclass jc, jboolean muteNow)
{
#ifdef FUJITSU_BUILD
	CoInitialize(NULL);
	IMMDeviceEnumerator *deviceEnumerator = NULL;
	HRESULT hr = CoCreateInstance(__uuidof(MMDeviceEnumerator), NULL, CLSCTX_INPROC_SERVER, __uuidof(IMMDeviceEnumerator), (LPVOID *)&deviceEnumerator);
	if (FAILED(hr)) return;
	

	IMMDevice *defaultDevice = NULL; 

	hr = deviceEnumerator->GetDefaultAudioEndpoint(eRender, eConsole, &defaultDevice);
	if (FAILED(hr)) return;
	deviceEnumerator->Release();
	deviceEnumerator = NULL; 

	IAudioEndpointVolume *endpointVolume = NULL;
	hr = defaultDevice->Activate(__uuidof(IAudioEndpointVolume), CLSCTX_INPROC_SERVER, NULL, (LPVOID *)&endpointVolume);
	if (FAILED(hr)) return;
	defaultDevice->Release();
	defaultDevice = NULL; 

	// -------------------------
	hr = endpointVolume->SetMute(muteNow, NULL);
	endpointVolume->Release(); 
	CoUninitialize();
#endif
	return;
}

/*
 * Class:     sage_VideoFrame
 * Method:    getSystemMute0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_VideoFrame_getSystemMute0
  (JNIEnv *env, jclass jc)
{
	BOOL currentMute = 0;
#ifdef FUJITSU_BUILD
	CoInitialize(NULL);
	IMMDeviceEnumerator *deviceEnumerator = NULL;
	HRESULT hr = CoCreateInstance(__uuidof(MMDeviceEnumerator), NULL, CLSCTX_INPROC_SERVER, __uuidof(IMMDeviceEnumerator), (LPVOID *)&deviceEnumerator);
	if (FAILED(hr)) return 0;

	IMMDevice *defaultDevice = NULL; 

	hr = deviceEnumerator->GetDefaultAudioEndpoint(eRender, eConsole, &defaultDevice);
	if (FAILED(hr)) return 0;
	deviceEnumerator->Release();
	deviceEnumerator = NULL; 

	IAudioEndpointVolume *endpointVolume = NULL;
	hr = defaultDevice->Activate(__uuidof(IAudioEndpointVolume), CLSCTX_INPROC_SERVER, NULL, (LPVOID *)&endpointVolume);
	if (FAILED(hr)) return 0;
	defaultDevice->Release();
	defaultDevice = NULL; 

	hr = endpointVolume->GetMute(&currentMute);
	if (FAILED(hr)) return JNI_FALSE;
	endpointVolume->Release(); 
	CoUninitialize();
#endif
	return currentMute ? JNI_TRUE : JNI_FALSE;
}
