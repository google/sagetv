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
#include "sage_DShowTVPlayer.h"
#include "sage_DShowMediaPlayer.h"
#include "sage_VideoFrame.h"
#include <ks.h>
#include "sage_PlaybackException.h"
#include "IMpegDeMux.h"
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "Conexant/ivactypes.h"
#include "Conexant/ivacctrlprop.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */

class CTVPlayerData : public CPlayerData
{
public:
	CTVPlayerData() : CPlayerData()
	{
		m_pHWDecoder = NULL;
		m_bHWDecodeOnly = FALSE;

	}
	virtual ~CTVPlayerData()
	{
		if (m_pHWDecoder)
		{
			if (m_pGraph)
				m_pGraph->RemoveFilter(m_pHWDecoder);
			SAFE_RELEASE(m_pHWDecoder);
		}
	}
	HRESULT SetHWDecoder(IBaseFilter* pFilter, BOOL hwDecodeOnly)
	{
		if (!m_pGraph){	return E_FAIL;}
		m_bHWDecodeOnly = hwDecodeOnly;
		HRESULT hr = m_pGraph->AddFilter(pFilter, L"HW Decoder");
		if (SUCCEEDED(hr))
		{
			m_pHWDecoder = pFilter;
			m_pHWDecoder->AddRef();

			// Now connect it up from the demux
			IPin* pDecoderInPin = FindPin(m_pHWDecoder, PINDIR_INPUT,
				&MEDIATYPE_Stream, &MEDIASUBTYPE_MPEG2_PROGRAM);
			if (pDecoderInPin)
			{
				IPin* psPin = FindPinByName(m_pDeMux, "PassThrough", PINDIR_OUTPUT);
				if (psPin)
				{
					hr = m_pGraph->ConnectDirect(psPin, pDecoderInPin, NULL);
					SAFE_RELEASE(psPin);
				}
				else
					hr = E_FAIL;
				SAFE_RELEASE(pDecoderInPin);
			}
			else
				hr = E_FAIL;
		}
		return hr;
	}
	BOOL IsHWDecodeOnly() { return m_bHWDecodeOnly; }
	IBaseFilter* GetHWDecoder() { return m_pHWDecoder; }
protected:
	IBaseFilter* m_pHWDecoder;
	BOOL m_bHWDecodeOnly;
};

/*
 * Class:     sage_DShowTVPlayer
 * Method:    createGraph0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowTVPlayer_createGraph0
  (JNIEnv *env, jobject jo)
{
	CComPtr<IGraphBuilder> pGraph = NULL;
	HRESULT hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
		IID_IGraphBuilder, (void **)&pGraph);
	PLAYEXCEPT_RET0(sage_PlaybackException_DIRECTX_INSTALL);

	CTVPlayerData* rv = new CTVPlayerData();
	rv->SetGraph(pGraph);
	rv->CreatePluginHandler();
	return (jlong) (dynamic_cast<CPlayerData*>(rv));
}

/*
 * Class:     sage_DShowTVPlayer
 * Method:    switchLoadTVFile0
 * Signature: (JLjava/lang/String;Ljava/lang/String;Z)V
 */
JNIEXPORT void JNICALL Java_sage_DShowTVPlayer_switchLoadTVFile0
  (JNIEnv *env, jobject jo, jlong dataPtr, jstring jFilename, jstring jhostname, jboolean waitUntilDone)
{
	NPE_RET(dataPtr);
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder *pGraph = playData->GetGraph();
	CComPtr<IMediaControl> pMC = NULL;
	pGraph->QueryInterface(IID_IMediaControl, (void**)&pMC);
	elog((env, "switchLoadTVFile0, wait:%d \r\n", waitUntilDone ));
	if (waitUntilDone)
	{
		jlong currPos = Java_sage_DShowMediaPlayer_getMediaTimeMillis0(env, jo, dataPtr);
		jlong currDur = Java_sage_DShowMediaPlayer_getDurationMillis0(env, jo, dataPtr);
		if (currPos/10000L < currDur)
		{
			IMediaEvent* pME = NULL;
			long evCode = 0;
			pGraph->QueryInterface(IID_IMediaEvent, (void**)&pME);
			pME->WaitForCompletion(500, &evCode);
			SAFE_RELEASE(pME);
		}
		//else NOT WAITING for completion, files already done!
	}

	//ZQ Fast smooth switch
	//pMC->Stop();
	//WaitForState(env, pMC, State_Stopped);

	const jchar* wszFilename = env->GetStringChars(jFilename, NULL);
	const char* cHostname = jhostname ? env->GetStringUTFChars(jhostname, NULL) : NULL;
	HRESULT hr = playData->SetSourceFilename((const WCHAR**)&wszFilename, 1, cHostname);

	if ( playData->HasDemux() )
	{   //ZQ notify MpegDemux, file switching
		IBaseFilter* pDeMux = playData->GetDemux();
		IMpegDeMux* piDeMux = NULL;
		hr = pDeMux->QueryInterface(IID_IMpegDeMux, (void**)&piDeMux);
		if ( !FAILED(hr)) 
		{
			piDeMux->FileSwitch( 0 );
			elog((env, "switchLoadTVFile0, Fast smooth switch.\r\n") );
		}
		SAFE_RELEASE( piDeMux );
	}

	env->ReleaseStringChars(jFilename, wszFilename);
	if (cHostname)
		env->ReleaseStringUTFChars(jhostname, cHostname);
	PLAYEXCEPT_RET(sage_PlaybackException_FILESYSTEM);
}

/*
 * Class:     sage_DShowTVPlayer
 * Method:    setupGraphMultifile0
 * Signature: (J[Ljava/lang/String;Ljava/lang/String;ZZ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowTVPlayer_setupGraphMultifile0
  (JNIEnv *env, jobject jo, jlong dataPtr, jobjectArray jFilenames, jstring jhostname, 
		jboolean renderVideo, jboolean renderAudio)
{
	NPE_RET(dataPtr);
	try
	{
		CPlayerData* playData = (CPlayerData*) dataPtr;
		int numFiles = env->GetArrayLength(jFilenames);
		WCHAR** wszFilenames = (WCHAR**) malloc(numFiles * sizeof(WCHAR*));
		for (int i = 0; i < numFiles; i++)
		{
			wszFilenames[i] = (WCHAR*)env->GetStringChars((jstring) env->GetObjectArrayElement(jFilenames, i), NULL);
		}
		const char* cHostname = jhostname ? env->GetStringUTFChars(jhostname, NULL) : NULL;
		HRESULT hr = playData->SetSourceFilename((const WCHAR**)wszFilenames, (DWORD)numFiles, cHostname);
		for (int i = 0; i < numFiles; i++)
		{
			env->ReleaseStringChars((jstring) env->GetObjectArrayElement(jFilenames, i), (jchar*)wszFilenames[i]);
		}
		if (cHostname)
			env->ReleaseStringUTFChars(jhostname, cHostname);
		if (hr == E_NOINTERFACE)
		{
			// This happens if they're using the wrong SageTV filter components from an older version
			PLAYEXCEPT_RET(sage_PlaybackException_SAGETV_INSTALL);
		}
		PLAYEXCEPT_RET(sage_PlaybackException_FILESYSTEM);

		BOOL hasDemux = playData->HasDemux();
		if (hasDemux)
		{
			if (renderAudio)
				hr = playData->RenderAudio();
			PLAYEXCEPT_RET(sage_PlaybackException_AUDIO_RENDER);
			if (renderVideo)
				hr = playData->RenderVideo();
			PLAYEXCEPT_RET(sage_PlaybackException_VIDEO_RENDER);
			if (renderVideo)
				hr = playData->RenderCC(); // We accept failures rendering this
			//HTESTPRINT(hr);
		}
		else if (renderAudio || renderVideo)
		{
			hr = playData->RenderSource();
			PLAYEXCEPT_RET(renderVideo ? sage_PlaybackException_VIDEO_RENDER : sage_PlaybackException_AUDIO_RENDER);
		}
		LogFilterGraphInfo(playData->GetGraph());
	}
	catch (...)
	{
		throwPlaybackException(env, sage_PlaybackException_VIDEO_RENDER, 0);
	}
}

/*
 * Class:     sage_DShowTVPlayer
 * Method:    frameStep0
 * Signature: (JI)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowTVPlayer_frameStep0
  (JNIEnv *env, jobject jo, jlong dataPtr, jint amount)
{
	if (amount <= 0 || !dataPtr) return JNI_FALSE;
	CTVPlayerData* playerData = dynamic_cast<CTVPlayerData*>((CPlayerData*) dataPtr);
	IBaseFilter* pHWDecoder = playerData->GetHWDecoder();
	if (playerData->IsHWDecodeOnly() && pHWDecoder)
	{
		IKsPropertySet* ksProp = NULL;
		/*
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
		HRESULT hr = pHWDecoder->QueryInterface(IID_IKsPropertySet, (void**)&ksProp);
		if (SUCCEEDED(hr))
		{
			PLAY_MODE_VALUE stepper;
			stepper.dValue = amount;
			hr = ksProp->Set(PROPSETID_IVAC_PROPERTIES, IVAC_RUN_MODE,
					&stepper, sizeof(PLAY_MODE_VALUE), &stepper, sizeof(PLAY_MODE_VALUE));
			SAFE_RELEASE(ksProp);
			return SUCCEEDED(hr);
		}
		else
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
		 */
			return JNI_FALSE;
	}
	else
	{
		return Java_sage_DShowMediaPlayer_frameStep0(env, jo, dataPtr, amount);
	}
}

/*
 * Class:     sage_DShowTVPlayer
 * Method:    addHWDecoderFilter0
 * Signature: (JLjava/lang/String;Z)J
 */
JNIEXPORT void JNICALL Java_sage_DShowTVPlayer_addHWDecoderFilter0
  (JNIEnv *env, jobject jo, jlong dataPtr, jstring jhwDecoderName, jboolean hwDecodeOnly)
{
	if (jhwDecoderName == NULL || env->GetStringLength(jhwDecoderName) == 0 || !dataPtr){return;}
	const char* cName = env->GetStringUTFChars(jhwDecoderName, NULL);
	CComPtr<IBaseFilter> pFilter = NULL;
	HRESULT hr = FindFilterByName(&pFilter, KSCATEGORY_DATADECOMPRESSOR, cName);
	env->ReleaseStringUTFChars(jhwDecoderName, cName);
	if (SUCCEEDED(hr))
	{
		CTVPlayerData* playerData = dynamic_cast<CTVPlayerData*>((CPlayerData*) dataPtr);
		hr = playerData->SetHWDecoder(pFilter, hwDecodeOnly);
	}
	if (FAILED(hr))
	{
		elog((env, "Could not add specified hardware decoder filter to graph\r\n"));
	}
}
