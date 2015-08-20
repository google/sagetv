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
#include "sage_DShowLivePlayer.h"
#include "sage_DShowMediaPlayer.h"
#include "JeffMixer.h"
#include "sage_PlaybackException.h"
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "Plextor/AUDIO_PREVIEW.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
#include "sage_DShowSharedLiveMediaPlayer.h"

class CLivePlayerData : public CPlayerData
{
public:
	CLivePlayerData() : CPlayerData() , m_pAudioSource(NULL)
	{

	}
	virtual ~CLivePlayerData()
	{
		if (m_pAudioSource)
		{
			m_pGraph->RemoveFilter(m_pAudioSource);
			SAFE_RELEASE(m_pAudioSource);
		}
	}
	BOOL HasAudioSource()
	{
		if (m_pAudioSource != NULL)
			return TRUE;
		IPin* audioSourcePin = GetSourceAudioPin();
		if (audioSourcePin)
		{
			audioSourcePin->Release();
			return TRUE;
		}
		else
			return FALSE;
	}
	virtual HRESULT SetLiveSource(const char* capFiltName, int capFiltNum)
	{
		HRESULT hr = FindFilterByName(&m_pDeMux, AM_KSCATEGORY_CAPTURE,
			capFiltName, capFiltNum, NULL);
		if (FAILED(hr)) return hr;
		hr = m_pGraph->AddFilter(m_pDeMux, L"VideoCapture");
		return hr;
	}
	HRESULT SetAudioSource(const char* capFiltName, int capFiltNum )
	{
		// Add the audio capture filter to the graph
		HRESULT hr = FindFilterByName(&m_pAudioSource, CLSID_AudioInputDeviceCategory,
			capFiltName, capFiltNum);
		// Also check the WDM Streaming capture devices category
		if (FAILED(hr))
		{
			hr = FindFilterByName(&m_pAudioSource, AM_KSCATEGORY_CAPTURE,
				capFiltName, capFiltNum);
		}
		if (FAILED(hr)) return hr;
		hr = m_pGraph->AddFilter(m_pAudioSource, NULL);
		return hr;
	}
	virtual IPin* GetSourceAudioPin()
	{
		if (m_pAudioSource)
			return FindPin(m_pAudioSource, PINDIR_OUTPUT, NULL, NULL);
		else
		{
			IPin* rv = FindPinByCategoryAndType(m_pDeMux, &PIN_CATEGORY_PREVIEW, &MEDIATYPE_Audio);
			if (!rv)
				rv = FindPinByCategoryAndType(m_pDeMux, &PIN_CATEGORY_CAPTURE, &MEDIATYPE_Audio);
			return rv;
		}
	}
	BOOL bPlextorTV100U;

protected:
	IPin* GetSourceVideoPin()
	{
		IPin* rv = FindPinByCategoryAndType(m_pDeMux, &PIN_CATEGORY_PREVIEW, &MEDIATYPE_Video);
		if (!rv)
			rv = FindPinByCategoryAndType(m_pDeMux, &PIN_CATEGORY_VIDEOPORT, &MEDIATYPE_Video);
		if (!rv)
			rv = FindPinByCategoryAndType(m_pDeMux, &PIN_CATEGORY_CAPTURE, &MEDIATYPE_Video);
		return rv;
	}
	IPin* GetLine21OutputPin()
	{
		// Before we render the CC we need to add the filters to the VBI pins to
		// get it in the CC format for rendering.
		HRESULT hr;
		IPin* line21Pin = FindPin(m_pDeMux, PINDIR_OUTPUT, &MEDIATYPE_AUXLine21Data, NULL);
		if (!line21Pin)
		{
			IPin* vbiPin = FindPin(m_pDeMux, PINDIR_OUTPUT, &MEDIATYPE_VBI, NULL);
			if (vbiPin)
			{
				IBaseFilter* pTeeSinkConverter;
				hr = FindFilterByName(&pTeeSinkConverter, AM_KSCATEGORY_SPLITTER,
					"Tee/Sink-to-Sink Converter");
				if (SUCCEEDED(hr))
				{
					IBaseFilter* pCCDecoder;
					hr = FindFilterByName(&pCCDecoder, AM_KSCATEGORY_VBICODEC, "CC Decoder");
					if (SUCCEEDED(hr))
					{
						m_pGraph->AddFilter(pTeeSinkConverter, L"TeeSinkConverter");
						m_pGraph->AddFilter(pCCDecoder, L"CC Decoder");
						IPin* teeSinkInPin = FindPinByName(pTeeSinkConverter, NULL, PINDIR_INPUT);
						hr = m_pGraph->ConnectDirect(vbiPin, teeSinkInPin, NULL);
						HTESTPRINT(hr);
						SAFE_RELEASE(teeSinkInPin);
						IPin* teeSinkOutPin = FindPinByName(pTeeSinkConverter, NULL, PINDIR_OUTPUT);
						IPin* ccDecInPin = FindPinByName(pCCDecoder, "VBI", PINDIR_INPUT);
						hr = m_pGraph->ConnectDirect(teeSinkOutPin, ccDecInPin, NULL);
						SAFE_RELEASE(ccDecInPin);
						if (FAILED(hr))
						{
							// Try the CC pin
							ccDecInPin = FindPinByName(pCCDecoder, "HWCC", PINDIR_INPUT);
							hr = m_pGraph->ConnectDirect(teeSinkOutPin, ccDecInPin, NULL);
							SAFE_RELEASE(ccDecInPin);
						}
						SAFE_RELEASE(teeSinkOutPin);
						HTESTPRINT(hr);
						line21Pin = FindPinByName(pCCDecoder, NULL, PINDIR_OUTPUT);
						SAFE_RELEASE(pCCDecoder);
					}
					SAFE_RELEASE(pTeeSinkConverter);
				}
				SAFE_RELEASE(vbiPin);
			}
		}
		return line21Pin;
	}
	IBaseFilter* GetVideoRendererFilter()
	{
		if (m_pVideoRenderer)
		{
			m_pVideoRenderer->AddRef();
			return m_pVideoRenderer;
		}
		else
		{
			IBaseFilter* pVideoRenderer = NULL;
			CComPtr<IPin> vSrc = NULL;
			vSrc.p = GetSourceVideoPin();
			CComPtr<IPin> vNext = NULL;
			if (vSrc)
			{
				vSrc->ConnectedTo(&vNext);
				PIN_INFO pInfo;
				if (vNext)
				{
					if (SUCCEEDED(vNext->QueryPinInfo(&pInfo)))
						pVideoRenderer = pInfo.pFilter;
				}
			}
			return pVideoRenderer;
		}
	}
	IBaseFilter* m_pAudioSource;
};

/*
 * Class:     sage_DShowLivePlayer
 * Method:    createGraph0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowLivePlayer_createGraph0
  (JNIEnv *env, jobject jo)
{
	CComPtr<IGraphBuilder> pGraph = NULL;
	HRESULT hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
		IID_IGraphBuilder, (void **)&pGraph);
	PLAYEXCEPT_RET0(sage_PlaybackException_DIRECTX_INSTALL);

	CLivePlayerData* rv = new CLivePlayerData();
	rv->SetGraph(pGraph);
	rv->CreatePluginHandler();
	return (jlong) (dynamic_cast<CPlayerData*>(rv));
}

/*
 * Class:     sage_DShowLivePlayer
 * Method:    setLiveSource0
 * Signature: (JLjava/lang/String;ILjava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_sage_DShowLivePlayer_setLiveSource0
  (JNIEnv *env, jobject jo, jlong dataPtr, jstring jcapFiltName, jint capFiltNum,
  jstring jaudCapFiltName, jint audCapFiltNum )
{
	NPE_RET(dataPtr);
	CLivePlayerData* playerData = dynamic_cast<CLivePlayerData*>((CPlayerData*) dataPtr);
	const char* capFiltName = env->GetStringUTFChars(jcapFiltName, NULL);
	// Check for the Plextor TV-100U. We need to call a special function for audio preview for that device
	playerData->bPlextorTV100U = !strcmp(capFiltName, "PX-TV100U");
	if (playerData->bPlextorTV100U)
	{
		slog((env, "Plextor TV-100U detected...\r\n"));
	}
	HRESULT hr = playerData->SetLiveSource(capFiltName, capFiltNum);
	env->ReleaseStringUTFChars(jcapFiltName, capFiltName);
	if (jaudCapFiltName && !playerData->bPlextorTV100U)
	{
		const char* acapFiltName = env->GetStringUTFChars(jaudCapFiltName, NULL);
		hr = playerData->SetAudioSource(acapFiltName, audCapFiltNum );
		env->ReleaseStringUTFChars(jaudCapFiltName, acapFiltName);
	}
}

/*
 * Class:     sage_DShowLivePlayer
 * Method:    setupGraph0
 * Signature: (JLjava/lang/String;Ljava/lang/String;ZZ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowLivePlayer_setupGraph0
	(JNIEnv *env, jobject jo, jlong dataPtr, jstring jFilename, jstring jhostname, 
		jboolean renderVideo, jboolean renderAudio)
{
	NPE_RET(dataPtr);
	CLivePlayerData* playerData = dynamic_cast<CLivePlayerData*>((CPlayerData*) dataPtr);
	HRESULT hr;
	if (renderVideo)
	{
		hr = playerData->RenderVideo();
		PLAYEXCEPT_RET(sage_PlaybackException_VIDEO_RENDER);
		if (GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DirectShow",
			"EnableLiveCC", 0))
		{
			// On the Hauppauge WinTV USB capdev, doing this causes the video to fail...how stupid
			hr = playerData->RenderCC(); // failure here is OK
			HTESTPRINT(hr);
		}
	}
	if (renderAudio)
	{
		if (playerData->HasAudioSource()) 
		{
			hr = playerData->RenderAudio();
			PLAYEXCEPT_RET(sage_PlaybackException_AUDIO_RENDER);
		}
	}
	LogFilterGraphInfo(playerData->GetGraph());
}

/*
 * Class:     sage_DShowLivePlayer
 * Method:    getLiveMute0
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowLivePlayer_getLiveMute0
  (JNIEnv *env, jclass jo, jint liveIndex)
{
	CJeffMixer mixer(MIXERLINE_COMPONENTTYPE_DST_SPEAKERS,
		(liveIndex < 0) ? MIXERLINE_COMPONENTTYPE_SRC_LINE : (DWORD) liveIndex,
                  MIXERCONTROL_CONTROLTYPE_MUTE, (liveIndex >= 0));
	return (mixer.GetControlValueB() == 1);
}

/*
 * Class:     sage_DShowLivePlayer
 * Method:    setLiveMute0
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowLivePlayer_setLiveMute0
  (JNIEnv *env, jclass jo, jint liveIndex, jboolean muteState)
{
	try
	{
		if ( liveIndex < 0 )
			liveIndex = MIXERLINE_COMPONENTTYPE_SRC_LINE;
		
		CJeffMixer *pMixer = new CJeffMixer( MIXERLINE_COMPONENTTYPE_DST_SPEAKERS,
												(DWORD)liveIndex, MIXERCONTROL_CONTROLTYPE_MUTE, 1 );
		if (muteState)
			pMixer->On();
		else
			pMixer->Off();
		delete pMixer;
		 	
	}
	catch (...){}

}

/*
 * Class:     sage_DShowSharedLiveMediaPlayer
 * Method:    setVideoHWND0
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowSharedLiveMediaPlayer_setVideoHWND0
  (JNIEnv *env, jobject jo, jlong jVidPtr, jlong vhwnd)
{
	if (!jVidPtr) return;
	IGraphBuilder* pGraph = (IGraphBuilder*) jVidPtr;
	IVideoWindow* pVW = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IVideoWindow, (void**)&pVW);
	if (SUCCEEDED(hr))
	{
		slog((env, "DShowPlayer setVideoHWND(%d)\r\n", (int) vhwnd));
		if (!vhwnd)
		{
			pVW->put_Visible(OAFALSE);
		}
		else
		{
			pVW->put_AutoShow(OAFALSE);
			pVW->put_Owner((OAHWND)vhwnd);
			pVW->put_MessageDrain((OAHWND)vhwnd);
			pVW->put_WindowStyle(WS_CHILD | WS_CLIPSIBLINGS | WS_CLIPCHILDREN);
		}

		// We do all of our own aspect ratio control, so don't let DShow do any for us
		// by setting the aspect ratio mode on the video rendering filter's pin
		// Overlay is not used for preview so this is commented out
		/*IEnumFilters *pEnum = NULL;
		hr = pGraph->EnumFilters(&pEnum);
		if (SUCCEEDED(hr))
		{
			IBaseFilter *currFilt = NULL;
			while (pEnum->Next(1, &currFilt, NULL) == S_OK)
			{
				IPin *overlayPin = NULL;
				hr = currFilt->FindPin(L"Input0", &overlayPin);
				if (SUCCEEDED(hr))
				{
					// Right pin name, let's see if it's overlay
					IMixerPinConfig *pOverlayMix = NULL;
					hr = overlayPin->QueryInterface(IID_IMixerPinConfig, (void**)&pOverlayMix);
					if (SUCCEEDED(hr))
					{
						pOverlayMix->SetAspectRatioMode(AM_ARMODE_STRETCHED);
						SAFE_RELEASE(pOverlayMix);
					}
					SAFE_RELEASE(overlayPin);
				}
				SAFE_RELEASE(currFilt);
			}
			SAFE_RELEASE(pEnum);
			hr = S_OK;
		}*/
		SAFE_RELEASE(pVW);
	}
	HTESTPRINT(hr);
}

/*
 * Class:     sage_DShowSharedLiveMediaPlayer
 * Method:    getVideoDimensions0
 * Signature: (J)Ljava/awt/Dimension;
 */
JNIEXPORT jobject JNICALL Java_sage_DShowSharedLiveMediaPlayer_getVideoDimensions0
  (JNIEnv *env, jobject jo, jlong jVidPtr)
{
	if (!jVidPtr) return NULL;
	IGraphBuilder* pGraph = (IGraphBuilder*) jVidPtr;
	IBasicVideo* pBV = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IBasicVideo, (void**)&pBV);
	if (SUCCEEDED(hr))
	{
		long nativeWidth, nativeHeight;
		hr = pBV->get_VideoWidth(&nativeWidth);
		if (FAILED(hr)) { SAFE_RELEASE(pBV); return NULL; }
		hr = pBV->get_VideoHeight(&nativeHeight);
		if (FAILED(hr)) { SAFE_RELEASE(pBV); return NULL; }
		static jclass dimClass = (jclass) env->NewGlobalRef(env->FindClass("java/awt/Dimension"));
		static jmethodID constMeth = env->GetMethodID(dimClass, "<init>", "(II)V");
		jobject dimObj = env->NewObject(dimClass, constMeth, nativeWidth, nativeHeight);
		SAFE_RELEASE(pBV);
		return dimObj;
	}
	return NULL;
}

/*
 * Class:     sage_DShowSharedLiveMediaPlayer
 * Method:    resizeVideo0
 * Signature: (JLjava/awt/Rectangle;Ljava/awt/Rectangle;Z)V
 */
JNIEXPORT void JNICALL Java_sage_DShowSharedLiveMediaPlayer_resizeVideo0
  (JNIEnv *env, jobject jo, jlong jVidPtr, jobject srcVideoRect, jobject destVideoRect,
  jboolean hideCursor)
{
	try
	{
		if (!jVidPtr) return;
		static jclass rectClass = (jclass) env->NewGlobalRef(env->FindClass("java/awt/Rectangle"));
		static jfieldID fid_rectx = env->GetFieldID(rectClass, "x", "I");
		static jfieldID fid_recty = env->GetFieldID(rectClass, "y", "I");
		static jfieldID fid_rectwidth = env->GetFieldID(rectClass, "width", "I");
		static jfieldID fid_rectheight = env->GetFieldID(rectClass, "height", "I");
		IGraphBuilder* pGraph = (IGraphBuilder*) jVidPtr;
		RECT rSrc;
		RECT rDst;
		RECT* pSrc;
		RECT* pDst;
		if (srcVideoRect)
		{
			rSrc.left = env->GetIntField(srcVideoRect, fid_rectx);
			rSrc.top = env->GetIntField(srcVideoRect, fid_recty);
			rSrc.right = env->GetIntField(srcVideoRect, fid_rectwidth) + rSrc.left;
			rSrc.bottom = env->GetIntField(srcVideoRect, fid_rectheight) + rSrc.top;
			pSrc = &rSrc;
		}
		else
		{
			pSrc = NULL;
		}
		if (destVideoRect)
		{
			rDst.left = env->GetIntField(destVideoRect, fid_rectx);
			rDst.top = env->GetIntField(destVideoRect, fid_recty);
			rDst.right = env->GetIntField(destVideoRect, fid_rectwidth) + rDst.left;
			rDst.bottom = env->GetIntField(destVideoRect, fid_rectheight) + rDst.top;
			pDst = &rDst;
		}
		else
		{
			pDst = NULL;
		}
		IBasicVideo* pBV = NULL;
		HRESULT hr = pGraph->QueryInterface(IID_IBasicVideo, (void**)&pBV);
		if (SUCCEEDED(hr))
		{
			long srcTop, srcLeft, srcWidth, srcHeight;
			pBV->GetSourcePosition(&srcLeft, &srcTop, &srcWidth, &srcHeight);
			pBV->GetDestinationPosition(&srcLeft, &srcTop, &srcWidth, &srcHeight);
			if (pSrc)
			{
				pBV->SetSourcePosition(pSrc->left, pSrc->top, pSrc->right - pSrc->left, pSrc->bottom - pSrc->top);
			}
			else
			{
				pBV->SetDefaultSourcePosition();
			}
			if (pDst)
			{
				pBV->SetDestinationPosition(pDst->left, pDst->top, pDst->right - pDst->left, pDst->bottom - pDst->top);
			}
			else
			{
				pBV->SetDefaultDestinationPosition();
			}
			SAFE_RELEASE(pBV);

			IVideoWindow* pVW = NULL;
			hr = pGraph->QueryInterface(IID_IVideoWindow, (void**)&pVW);
			if (SUCCEEDED(hr))
			{
				OAHWND vidWinHWND;
				hr = pVW->get_Owner(&vidWinHWND);
				if (SUCCEEDED(hr))
				{
					RECT grc;
					GetClientRect((HWND)vidWinHWND, &grc);
					pVW->SetWindowPosition(0, 0, grc.right, grc.bottom);
				}
				pVW->HideCursor(hideCursor == JNI_TRUE ? OATRUE : OAFALSE);
				pVW->put_Visible(OATRUE);
				SAFE_RELEASE(pVW);
			}
		}
	}
	catch (...)
	{
		slog((env, "Native exception in %s at %d\r\n", __FILE__, __LINE__));
	}
	return;
}

/*
 * Class:     sage_DShowSharedLiveMediaPlayer
 * Method:    openAudioPreview0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowSharedLiveMediaPlayer_openAudioPreview0
  (JNIEnv *env, jclass jc, jstring jdevName)
{
	if (!jdevName) return 0;
	// Audio preview is not available on Windows 2000
	OSVERSIONINFO osVer;
	ZeroMemory(&osVer, sizeof(OSVERSIONINFO));
	osVer.dwOSVersionInfoSize = sizeof(OSVERSIONINFO);
	BOOL vtest = GetVersionEx(&osVer);
	_ASSERT(vtest);
	if (osVer.dwMajorVersion == 5 && osVer.dwMinorVersion == 0)
	{
		// Windows 2000
		return 0;
	}
	const char* cname = env->GetStringUTFChars(jdevName, NULL);
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
	if (!strcmp("PX-TV100U", cname))
	{
		// Check for the Plextor TV-100U. We need to call a special function for audio preview for that device
		slog((env, "Plextor TV-100U audio preview opened\r\n"));
		IAudioPreview* pAudPrev = NULL;
		HRESULT hr = CoCreateInstance(CLSID_AudioPreview, NULL, 
			CLSCTX_INPROC_SERVER, IID_IAudioPreview, 
			(void**)&pAudPrev);
		HTESTPRINT(hr);
		env->ReleaseStringUTFChars(jdevName, cname);
		return (jlong) pAudPrev;
	}
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
	env->ReleaseStringUTFChars(jdevName, cname);
	return 0;
}

/*
 * Class:     sage_DShowSharedLiveMediaPlayer
 * Method:    setAudioPreview0
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowSharedLiveMediaPlayer_setAudioPreview0
  (JNIEnv *env, jclass jc, jlong jptr, jboolean muteOn)
{
	if (jptr)
	{
		slog((env, "Plextor TV-100U audio preview state=%d\r\n", !muteOn));
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
		IAudioPreview* pAudPrev = (IAudioPreview*)jptr;
		pAudPrev->Enable(!muteOn);
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
	}
}

/*
 * Class:     sage_DShowSharedLiveMediaPlayer
 * Method:    closeAudioPreview0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_DShowSharedLiveMediaPlayer_closeAudioPreview0
  (JNIEnv *env, jclass jc, jlong jptr)
{
	if (jptr)
	{
		slog((env, "Plextor TV-100U audio preview closed\r\n"));
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
		IAudioPreview* pAudPrev = (IAudioPreview*)jptr;
		SAFE_RELEASE(pAudPrev);
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
	}
}

