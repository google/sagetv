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
#include "sage_DShowDVDPlayer.h"
#include "sage_DShowMediaPlayer.h"
#include "sage_VideoFrame.h"
#include "sage_PlaybackException.h"
#include <streams.h>
#include <d3d9.h>
#include "..\..\..\third_party\Microsoft\VMRAllocator\VMRAllocator.h"

IPin* FindDecoderSubpictureOutputPin(IBaseFilter* pFilter)
{
    IEnumPins* pEnum = NULL;
    HRESULT hr = pFilter->EnumPins(&pEnum);
    if (hr != NOERROR)
        return NULL;

    ULONG ulFound;
    IPin *pPin = NULL;
    hr = E_FAIL;

    while(S_OK == pEnum->Next(1, &pPin, &ulFound))
    {
		PIN_INFO PinInfo;
		//
		// grab this, so we can examine its name field
		//
	    hr = pPin->QueryPinInfo(&PinInfo);
	    if(SUCCEEDED(hr))
		{
			PinInfo.pFilter->Release();
			//
			// check direction
			//
			if (PinInfo.dir == PINDIR_OUTPUT)
			{
				// Make sure its not connected yet and its a video type.
				IPin* dummyPin = NULL;
				hr = pPin->ConnectedTo(&dummyPin);
				SAFE_RELEASE(dummyPin);
				if (hr == VFW_E_NOT_CONNECTED)
				{
					IEnumMediaTypes *mtEnum = NULL;
					pPin->EnumMediaTypes(&mtEnum);
					AM_MEDIA_TYPE *pMT = NULL;
					while (S_OK == mtEnum->Next(1, &pMT, NULL))
					{
						if (pMT->majortype == MEDIATYPE_Video)
						{
							DeleteMediaType(pMT);
							SAFE_RELEASE(mtEnum);
							SAFE_RELEASE(pEnum);
							return pPin;
						}
						DeleteMediaType(pMT);
					}
					SAFE_RELEASE(mtEnum);
				}
			}
		}
        pPin->Release();
    } 
    SAFE_RELEASE(pEnum);
	return NULL;
}

class CDVDPlayerData : public CPlayerData
{
public:
	CDVDPlayerData() : CPlayerData(), m_pDvdCtrl(0), m_pDvdInfo(0), m_currDomain(DVD_DOMAIN_FirstPlay),
		m_totalTitles(0), m_currTitle(0), m_totalChapters(0), m_currChapter(0),
		m_totalAngles(0), m_currAngle(0), m_numButtons(0), m_numAvailLangs(0), m_numAvailSubs(0),
		m_currLang(-1), m_currSub(-1)
	{

	}
	virtual ~CDVDPlayerData()
	{
		SAFE_RELEASE(m_pDvdInfo);
		SAFE_RELEASE(m_pDvdCtrl);
	}
	virtual HRESULT SetDVDLocation(const WCHAR* wszLocation)
	{
		HRESULT hr = CoCreateInstance(CLSID_DVDNavigator, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void**)&m_pDeMux);
		if (FAILED(hr)) return hr;
		hr = m_pGraph->AddFilter(m_pDeMux, L"DVD Navigator");
		if (FAILED(hr)) return hr;
		m_pDeMux->QueryInterface(IID_IDvdControl2, (void**)&m_pDvdCtrl);
		m_pDeMux->QueryInterface(IID_IDvdInfo2, (void**)&m_pDvdInfo);
		hr = m_pDvdCtrl->SetDVDDirectory(wszLocation);
		if (m_pPluginHandler)
		{
			slog(("Calling GraphCreated for DVD for the PluginHandler\r\n"));
			m_pPluginHandler->GraphCreated(m_pGraph, wszLocation);
			slog(("Done calling GraphCreated for DVD for the PluginHandler\r\n"));
		}
		return hr;
	}
	virtual HRESULT SetVideoPosition(RECT *pSrc, RECT *pDst, BOOL hideMouse)
	{
		if (!m_pGraph) return E_FAIL;
		if (m_pVmrAllocator && pSrc && pDst)
		{
			// Update our VMR9 window positioning for mouse events to work (this may only
			// work on XP, I think it will still fail on 2K)
			m_pVmrAllocator->UpdateVideoPosition(pSrc, pDst);
			return S_OK;
		}

		IBasicVideo* pBV = NULL;
		HRESULT hr = m_pGraph->QueryInterface(IID_IBasicVideo, (void**)&pBV);
		if (SUCCEEDED(hr))
		{
	/*		long nativeWidth;
			hr = pBV->get_VideoWidth(&nativeWidth);
			if (FAILED(hr))
			{
				SAFE_RELEASE(pBV);
				// no video is present
				return;
			}
	*/
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
			hr = m_pGraph->QueryInterface(IID_IVideoWindow, (void**)&pVW);
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
				pVW->HideCursor(hideMouse == JNI_TRUE ? OATRUE : OAFALSE);
				SAFE_RELEASE(pVW);
			}
		}
		return S_OK;
	}

	HRESULT RenderSubPicture()
	{
		if (!m_pDeMux) return E_POINTER;

		// Render from the output of the DVD Nav & then from the output of the decoder filter
		IPin* subpicSource = FindPinByName(m_pDeMux, "SubPicture", PINDIR_OUTPUT);
		if (!subpicSource) return E_FAIL;
		HRESULT hr;
		// Check if the video rendering filter has an extra input pin. If it doesn't then
		// this'll end up adding another renderer to the graph which causes an ActiveMovieWindow
		// to display. We abort rendering of the subpicture in this case.
/*		if (!m_pVideoRenderer)
		{
			IBaseFilter* pCurrVRend = FindVideoRendererFilter(m_pGraph);
			if (!pCurrVRend) return E_FAIL;
			IPin* testPin = FindUnconnectedPin(m_pVideoRenderer, PINDIR_INPUT);
			if (!testPin)
			{
				slog(("Aborting subpicture rendering because there's no more input pins on video renderer.\r\n"));
				return E_FAIL;
			}
			testPin->Release();
		}
		else
		{
			IPin* testPin = FindUnconnectedPin(m_pVideoRenderer, PINDIR_INPUT);
			if (!testPin)
			{
				slog(("Aborting subpicture rendering because there's no more input pins on video renderer.\r\n"));
				return E_FAIL;
			}
			testPin->Release();
		}
*/

		// Get the decoder filter, since it may not be specified
		IBaseFilter* pVideoDecoder = NULL;
		IBaseFilter* pVideoRenderer = NULL;
		if (m_pVideoDecoder)
		{
			pVideoDecoder = m_pVideoDecoder;
			pVideoDecoder->AddRef();
		}
		else
		{
			IPin* vSrc = GetSourceVideoPin();
			IPin* vNext = NULL;
			vSrc->ConnectedTo(&vNext);
			vSrc->Release();
			PIN_INFO pInfo;
			hr = vNext->QueryPinInfo(&pInfo);
			pVideoDecoder = pInfo.pFilter;
			vNext->Release();
		}

		// We added this back for Vista to get DVD subpictures to show up. But this won't end up doing anything
		// if we can't find another input pin on the renderer.
		BOOL renderDVDSubpic = TRUE;
		if (renderDVDSubpic)
		{
			if (m_pVideoRenderer)
			{
				pVideoRenderer = m_pVideoRenderer;
				pVideoRenderer->AddRef();
			}
			else
			{
				IPin* vSrc = FindUnconnectedPin(pVideoDecoder, PINDIR_OUTPUT, TRUE);
				IPin* vNext = NULL;
				vSrc->ConnectedTo(&vNext);
				vSrc->Release();
				PIN_INFO pInfo;
				hr = vNext->QueryPinInfo(&pInfo);
				pVideoRenderer = pInfo.pFilter;
				vNext->Release();
			}
		}


		IPin* subpicDestPin = FindUnconnectedPin(pVideoDecoder, PINDIR_INPUT);
		if (!subpicDestPin)
		{
			pVideoDecoder->Release();
			return E_FAIL;
		}

		hr = m_pGraph->Connect(subpicSource, subpicDestPin);
		SAFE_RELEASE(subpicDestPin);
		SAFE_RELEASE(subpicSource);
		/*
		 * NOTE: 7/27/04 - On my system, the video wouldn't play at all on DVDs if I connected
		 * up the subpicture stream. But the subpicture worked fine when I didn't connect it.
		 * For now I'm going to leave it that way and then just connect up the CC instead.
		 */
		if (renderDVDSubpic && SUCCEEDED(hr))
		{
			IPin* decoderSubpicOut = FindDecoderSubpictureOutputPin(pVideoDecoder);
			if (decoderSubpicOut)
			{
				IPin* renderSubpicIn = FindUnconnectedPin(pVideoRenderer, PINDIR_INPUT);
				if (renderSubpicIn)
				{
					hr = m_pGraph->Connect(decoderSubpicOut, renderSubpicIn);
					SAFE_RELEASE(renderSubpicIn);
				}
//				hr = m_pGraph->Render(decoderSubpicOut);
				SAFE_RELEASE(decoderSubpicOut);
			}
		}
		
		if (pVideoRenderer)
			pVideoRenderer->Release();
		pVideoDecoder->Release();
		return hr;
	}
	IDvdControl2* GetDVDCtrl() { return m_pDvdCtrl; }
	IDvdInfo2* GetDVDInfo() { return m_pDvdInfo; }
	DVD_DOMAIN m_currDomain;
	int m_totalTitles;
	int m_currTitle;
	int m_totalChapters;
	int m_currChapter;
	int m_totalAngles;
	int m_currAngle;
	int m_numButtons;
	int m_currLang;
	int m_currSub;
	TCHAR m_availLangs[8][256];
	int m_numAvailLangs;
	TCHAR m_availSubs[32][256];
	int m_numAvailSubs;
protected:
	IPin* GetSourceVideoPin()
	{
		return FindPinByName(m_pDeMux, "Video", PINDIR_OUTPUT);
	}
	IPin* GetSourceAudioPin(int audioStreamNum)
	{
		if (audioStreamNum == 1)
			return FindPinByName(m_pDeMux, "AC3", PINDIR_OUTPUT);
		else
			return NULL;
	}

	IDvdControl2* m_pDvdCtrl;
	IDvdInfo2* m_pDvdInfo;
};

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    createGraph0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowDVDPlayer_createGraph0
  (JNIEnv *env, jobject jo)
{
	CComPtr<IGraphBuilder> pGraph = NULL;
	HRESULT hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
		IID_IGraphBuilder, (void **)&pGraph);
	if (hr == 0x800401F0)
	{
		// I don't know why this happens on rare occassions to some users...so let's just redo it since that should fix the error
		slog((env, "Redoing CoInitialize due to error....\r\n"));
		CoInitializeEx(NULL, COM_THREADING_MODE);
		hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
			IID_IGraphBuilder, (void **)&pGraph);
	}
	PLAYEXCEPT_RET0(sage_PlaybackException_DIRECTX_INSTALL);

	CDVDPlayerData* rv = new CDVDPlayerData();
	rv->SetGraph(pGraph);
	rv->CreatePluginHandler();
	return (jlong) (dynamic_cast<CPlayerData*>(rv));
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    setupGraph0
 * Signature: (JLjava/lang/String;Ljava/lang/String;ZZ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowDVDPlayer_setupGraph0
	(JNIEnv *env, jobject jo, jlong dataPtr, jstring jFilename, jstring jhostname, 
		jboolean renderVideo, jboolean renderAudio)
{
	NPE_RET(dataPtr);
	try
	{
		CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
		const jchar* wszFilename = jFilename ? env->GetStringChars(jFilename, NULL) : NULL;
		HRESULT hr = playerData->SetDVDLocation((const WCHAR*) wszFilename);
		if (wszFilename)
			env->ReleaseStringChars(jFilename, wszFilename);
		PLAYEXCEPT_RET(sage_PlaybackException_FILESYSTEM);

		if (renderAudio)
		{
			hr = playerData->RenderAudio();
			PLAYEXCEPT_RET(sage_PlaybackException_AUDIO_RENDER);
		}
		if (renderVideo)
		{
			hr = playerData->RenderVideo();
			PLAYEXCEPT_RET(sage_PlaybackException_VIDEO_RENDER);
			hr = playerData->RenderSubPicture();
			HTESTPRINT(hr); // if there's no subpicture we're OK and shouldn't fail
			hr = playerData->RenderCC();
			HTESTPRINT(hr); // if there's no CC we're OK and shouldn't fail
		}
		LogFilterGraphInfo(playerData->GetGraph());
	}
	catch (...)
	{
		throwPlaybackException(env, sage_PlaybackException_DIRECTX_INSTALL, 0);
	}
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDTitle0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowDVDPlayer_getDVDTitle0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	return playerData->m_currTitle;
/*	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return 0;
	DVD_PLAYBACK_LOCATION2 currLoc;
	currLoc.TitleNum = 0;
	pDvdInfo->GetCurrentLocation(&currLoc);
	return currLoc.TitleNum;*/
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDTotalTitles0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowDVDPlayer_getDVDTotalTitles0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	return playerData->m_totalTitles;
/*	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return 0;
	ULONG numVols, currVol, numTitles=0;
	DVD_DISC_SIDE currSide;
	pDvdInfo->GetDVDVolumeInfo(&numVols, &currVol, &currSide, &numTitles);
	return numTitles;*/
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDChapter0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowDVDPlayer_getDVDChapter0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	return playerData->m_currChapter;
/*	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return 0;
	DVD_PLAYBACK_LOCATION2 currLoc;
	currLoc.ChapterNum = 0;
	pDvdInfo->GetCurrentLocation(&currLoc);
	return currLoc.ChapterNum;*/
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDTotalChapters0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowDVDPlayer_getDVDTotalChapters0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	return playerData->m_totalChapters;
/*	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return 0;
	DVD_PLAYBACK_LOCATION2 currLoc;
	HRESULT hr = pDvdInfo->GetCurrentLocation(&currLoc);
	if (SUCCEEDED(hr))
	{
		ULONG numChaps = 0;
		hr = pDvdInfo->GetNumberOfChapters(currLoc.TitleNum, &numChaps);
		return numChaps;
	}
	return 0;*/
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDDomain0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowDVDPlayer_getDVDDomain0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	return playerData->m_currDomain;
/*	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return 0;
	DVD_DOMAIN currDomain;
	ZeroMemory(&currDomain, sizeof(DVD_DOMAIN));
	pDvdInfo->GetCurrentDomain(&currDomain);
	return currDomain;*/
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    areDVDButtonsVisible0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowDVDPlayer_areDVDButtonsVisible0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return JNI_FALSE;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	return playerData->m_numButtons > 0;
/*	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return JNI_FALSE;
	ULONG butt1, butt2;
	HRESULT hr = pDvdInfo->GetCurrentButton(&butt1, &butt2);
	return SUCCEEDED(hr) && butt1;*/
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDAngle0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowDVDPlayer_getDVDAngle0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	return playerData->m_currAngle;
/*	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return 0;
	ULONG ulTotal=0, ulCurr=0;
	pDvdInfo->GetCurrentAngle(&ulTotal, &ulCurr);
	return ulCurr;*/
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDTotalAngles0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowDVDPlayer_getDVDTotalAngles0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	return playerData->m_totalAngles;
/*	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return 0;
	ULONG ulTotal=0, ulCurr=0;
	pDvdInfo->GetCurrentAngle(&ulTotal, &ulCurr);
	return ulTotal;*/
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDAspectRatio0
 * Signature: (J)F
 */
JNIEXPORT jfloat JNICALL Java_sage_DShowDVDPlayer_getDVDAspectRatio0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return 0;
	DVD_VideoAttributes dvdVid;
	ZeroMemory(&dvdVid, sizeof(DVD_VideoAttributes));
	if (S_OK == pDvdInfo->GetCurrentVideoAttributes(&dvdVid))
	{
		if (dvdVid.ulAspectX && dvdVid.ulAspectY)
			return ((float)dvdVid.ulAspectX)/dvdVid.ulAspectY;
	}
	return 0;
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDLanguage0
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_DShowDVDPlayer_getDVDLanguage0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	return env->NewStringUTF((playerData->m_currLang < 0 || playerData->m_numAvailLangs == 0) ? "" : 
		playerData->m_availLangs[min(playerData->m_currLang, playerData->m_numAvailLangs - 1)]);
/*	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return 0;
	LCID language;
	TCHAR langString[256];
	ULONG ulTotal=0, ulCurr=0;
	HRESULT hr = pDvdInfo->GetCurrentAudio(&ulTotal, &ulCurr);
	DVD_AudioAttributes dvdAudio;
	if (SUCCEEDED(hr))
	{
		hr = pDvdInfo->GetAudioLanguage(ulCurr, &language);
		if (SUCCEEDED(hr))
		{
			sprintf(langString, "%d ", ulCurr + 1);
			GetLocaleInfo(language, LOCALE_SENGLANGUAGE, langString + strlen(langString), 256 - strlen(langString));
			pDvdInfo->GetAudioAttributes(ulCurr, &dvdAudio);
			if (SUCCEEDED(hr))
			{
				switch (dvdAudio.AudioFormat)
				{
					case DVD_AudioFormat_AC3:
						strcat(langString, " AC3");
						break;
					case DVD_AudioFormat_DTS:
						strcat(langString, " DTS");
						break;
					case DVD_AudioFormat_SDDS:
						strcat(langString, " SDDS");
						break;
				}
				switch (dvdAudio.LanguageExtension)
				{
					case DVD_AUD_EXT_DirectorComments1:
						strcat(langString, " w/Comments");
						break;
					case DVD_AUD_EXT_DirectorComments2:
						strcat(langString, " w/Comments 2");
						break;
				}
				if (dvdAudio.bNumberOfChannels == 6)
				{
					strcat(langString, " 5.1");
				}
			}
			return env->NewStringUTF(langString);
		}
	}
	return env->NewStringUTF("");*/
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDAvailableLanguages0
 * Signature: (J)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_DShowDVDPlayer_getDVDAvailableLanguages0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return NULL;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	static jclass stringClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/String"));
	jobjectArray rv = env->NewObjectArray(playerData->m_numAvailLangs, stringClass, NULL);
	for (int i = 0; i < playerData->m_numAvailLangs; i++)
	{
		env->SetObjectArrayElement(rv, i, env->NewStringUTF(playerData->m_availLangs[i]));
	}
	return rv;
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDSubpicture0
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_DShowDVDPlayer_getDVDSubpicture0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	if (playerData->m_currSub < 0 || playerData->m_numAvailSubs == 0)
		return NULL;
	return env->NewStringUTF(playerData->m_availSubs[min(playerData->m_currSub, playerData->m_numAvailSubs - 1)]);
/*	IDvdInfo2* pDvdInfo = playerData->GetDVDInfo();
	if (!pDvdInfo) return 0;
	LCID language;
	TCHAR langString[256];
	BOOL subPicDisabled;
	DVD_SubpictureAttributes dvdSubpic;
	ULONG ulTotal=0, ulCurr=0;
	HRESULT hr = pDvdInfo->GetCurrentSubpicture(&ulTotal, &ulCurr, &subPicDisabled);
	if (SUCCEEDED(hr))
	{
		if (subPicDisabled) return NULL;
		hr = pDvdInfo->GetSubpictureLanguage(ulCurr, &language);
		if (SUCCEEDED(hr))
		{
			sprintf(langString, "%d ", ulCurr + 1);
			GetLocaleInfo(language, LOCALE_SENGLANGUAGE, langString + strlen(langString), 256 - strlen(langString));
			pDvdInfo->GetSubpictureAttributes(ulCurr, &dvdSubpic);
			if (SUCCEEDED(hr))
			{
					switch (dvdSubpic.LanguageExtension)
					{
						case DVD_SP_EXT_Caption_Normal:
							strcat(langString, " Normal");
							break;
						case DVD_SP_EXT_Caption_Big:
							strcat(langString, " Large");
							break;
						case DVD_SP_EXT_Caption_Children:
							strcat(langString, " Children");
							break;
						case DVD_SP_EXT_CC_Normal:
							strcat(langString, " Normal CC");
							break;
						case DVD_SP_EXT_CC_Big:
							strcat(langString, " Large CC");
							break;
						case DVD_SP_EXT_CC_Children:
							strcat(langString, " Children CC");
							break;
						case DVD_SP_EXT_Forced:
							strcat(langString, " Forced");
							break;
						case DVD_SP_EXT_DirectorComments_Normal:
							strcat(langString, " Director Comments");
							break;
						case DVD_SP_EXT_DirectorComments_Big:
							strcat(langString, " Large Director Comments");
							break;
						case DVD_SP_EXT_DirectorComments_Children:
							strcat(langString, " Director Comments for Children");
							break;
					}
			}
			return env->NewStringUTF(langString);
		}
	}
	return env->NewStringUTF("");*/
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDVDAvailableSubpictures0
 * Signature: (J)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_DShowDVDPlayer_getDVDAvailableSubpictures0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	static jclass stringClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/String"));
	jobjectArray rv = env->NewObjectArray(playerData->m_numAvailSubs, stringClass, NULL);
	for (int i = 0; i < playerData->m_numAvailSubs; i++)
	{
		env->SetObjectArrayElement(rv, i, env->NewStringUTF(playerData->m_availSubs[i]));
	}
	return rv;
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    setDVDRate0
 * Signature: (JF)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowDVDPlayer_setDVDRate0
  (JNIEnv *env, jobject jo, jlong dataPtr, jfloat rate)
{
	if (!dataPtr) return JNI_FALSE;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	IDvdControl2* pCtrl = playerData->GetDVDCtrl();
	if (!pCtrl) return JNI_FALSE;
	slog((env, "Doing DVD SetRate %f\r\n", rate));
	HRESULT hr;
	if (rate > 0)
	{
		hr = pCtrl->PlayForwards(rate, DVD_CMD_FLAG_Flush, NULL);
	}
	else if (rate < 0)
	{
		hr = pCtrl->PlayBackwards(-1*rate, DVD_CMD_FLAG_Flush, NULL);
	}
	return SUCCEEDED(hr);
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    seekDVD0
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowDVDPlayer_seekDVD0
  (JNIEnv *env, jobject jo, jlong dataPtr, jlong jtime)
{
	try
	{
		if (!dataPtr) return JNI_FALSE;
		CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
		IDvdControl2* pCtrl = playerData->GetDVDCtrl();
		if (!pCtrl) return JNI_FALSE;
		slog((env, "Doing DVD Media Time Select Millis %d\r\n", (jint) jtime));
		DVD_HMSF_TIMECODE newLoc;
		newLoc.bFrames = 0;
		jtime /= 1000L;
		newLoc.bSeconds = (BYTE) (jtime % 60);
		jtime -= newLoc.bSeconds;
		jtime /= 60;
		newLoc.bMinutes = (BYTE) (jtime % 60);
		jtime -= newLoc.bMinutes;
		jtime /= 60;
		newLoc.bHours = (BYTE) jtime;
		HRESULT hr = pCtrl->PlayAtTime(&newLoc, DVD_CMD_FLAG_Flush | DVD_CMD_FLAG_Block, NULL);
		slog((env, "Did DVD Media Time Select Millis %d\r\n", (jint) jtime));
		return SUCCEEDED(hr);
	}
	catch (...)
	{
		slog((env, "Native exception in DVD Seek...continuing..\r\n"));
		return JNI_FALSE;
	}
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getMediaTimeMillis0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowDVDPlayer_getMediaTimeMillis0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	IDvdInfo2* pInfo = playerData->GetDVDInfo();
	if (!pInfo) return 0;
	DVD_PLAYBACK_LOCATION2 currLoc;
	HRESULT hr = pInfo->GetCurrentLocation(&currLoc);
	jlong rv = 0;
	if (SUCCEEDED(hr))
	{
		rv += currLoc.TimeCode.bHours;
		rv *= 60; // hours->minutes
		rv += currLoc.TimeCode.bMinutes;
		rv *= 60; // minutes->seconds
		rv += currLoc.TimeCode.bSeconds;
		rv *= 1000L; // millis
	}
	else
		rv = 0;
	return rv;
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    getDurationMillis0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowDVDPlayer_getDurationMillis0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	IDvdInfo2* pInfo = playerData->GetDVDInfo();
	if (!pInfo) return 0;
	DVD_HMSF_TIMECODE totalTime;
	ULONG timeFlags;
	jlong rv = 0;
	HRESULT hr = pInfo->GetTotalTitleTime(&totalTime, &timeFlags);
	if (SUCCEEDED(hr))
	{
		rv += totalTime.bHours;
		rv *= 60; // hours->minutes
		rv += totalTime.bMinutes;
		rv *= 60; // minutes->seconds
		rv += totalTime.bSeconds;
		rv *= 1000L; // nanos
	}
	else
	{
		// Default it to 2 hours
		rv = 7200000L;
	}
	return rv;
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    processEvents0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowDVDPlayer_processEvents0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return JNI_TRUE;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	IDvdInfo2* pInfo = playerData->GetDVDInfo();
	IDvdControl2* pCtrl = playerData->GetDVDCtrl();
	if (!pCtrl || !pInfo) return JNI_TRUE;

	HRESULT hr = S_OK;
	// Clear out any events in the DVD event queue, this method is called with null params
	// when that's all we need to do
	CComPtr<IMediaEvent> pIME = NULL;
	hr = playerData->GetGraph()->QueryInterface(IID_IMediaEvent, (void**)&pIME);
	long lEvent;
	LONG_PTR lParam1, lParam2;
    long lTimeOut = 0;
	long evErr = 0;
	static jclass stringClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/String"));
	LCID language;
	TCHAR langString[256];
	BOOL subPicDisabled;
	DVD_SubpictureAttributes dvdSubpic;
	ULONG ulTotal=0, ulCurr=0;

	jboolean rv = 0;
	ULONG numVols, currVol, numTitles=0;

	while (SUCCEEDED(pIME->GetEvent(&lEvent, &lParam1, &lParam2, lTimeOut)))
	{
        slog((env, "Event: %#x l1=0x%x l2=0x%x\r\n", lEvent, lParam1, lParam2));

		switch(lEvent)
        {
			case EC_DVD_PLAYBACK_STOPPED:
				slog((env, "EC_DVD_PLAYBACK_STOPPED event received reason=%d\n", (int)lParam1));
				rv = 1;
				break;
			case EC_COMPLETE:
				slog((env, "EC_COMPLETE event received\n"));
				rv = 1;
				break;
	        case EC_DVD_NO_FP_PGC:
				pCtrl->PlayTitle(1, DVD_CMD_FLAG_None, NULL);
				break;
			case EC_DVD_ANGLE_CHANGE:
				playerData->m_currAngle = (int) lParam2;
				playerData->m_totalAngles = (int) lParam1;
				break;
			case EC_DVD_CHAPTER_START:
				playerData->m_currChapter = (int) lParam1;
				break;
			case EC_DVD_DOMAIN_CHANGE:
				playerData->m_currDomain = (DVD_DOMAIN)lParam1;
				DVD_DISC_SIDE currSide;
				pInfo->GetDVDVolumeInfo(&numVols, &currVol, &currSide, &numTitles);
				playerData->m_totalTitles = numTitles;
				// Audio & subpic selections can change on domain changes in DShow
				//break;
			case EC_DVD_TITLE_CHANGE:
				if (lEvent == EC_DVD_TITLE_CHANGE)
				{
					playerData->m_currTitle = (int) lParam1;
					hr = pInfo->GetNumberOfChapters((ULONG) lParam1, (ULONG*)&(playerData->m_totalChapters));
				}
				// Subtitles/audio selections change on title boundaries so reconfigure those values
				hr = pInfo->GetCurrentSubpicture(&ulTotal, &ulCurr, &subPicDisabled);
				if (SUCCEEDED(hr))
				{
					for( UINT j = 0; j < ulTotal; j++ )
					{
						hr = pInfo->GetSubpictureLanguage(j, &language);
						if (SUCCEEDED(hr))
						{
							sprintf(langString, "%d ", j + 1);
							GetLocaleInfo(language, LOCALE_SENGLANGUAGE, langString + (int)strlen(langString), 256 - (int)strlen(langString));
							pInfo->GetSubpictureAttributes(j, &dvdSubpic);
							if (SUCCEEDED(hr))
							{
								switch (dvdSubpic.LanguageExtension)
								{
									case DVD_SP_EXT_Caption_Normal:
										strcat(langString, " Normal");
										break;
									case DVD_SP_EXT_Caption_Big:
										strcat(langString, " Large");
										break;
									case DVD_SP_EXT_Caption_Children:
										strcat(langString, " Children");
										break;
									case DVD_SP_EXT_CC_Normal:
										strcat(langString, " Normal CC");
										break;
									case DVD_SP_EXT_CC_Big:
										strcat(langString, " Large CC");
										break;
									case DVD_SP_EXT_CC_Children:
										strcat(langString, " Children CC");
										break;
									case DVD_SP_EXT_Forced:
										strcat(langString, " Forced");
										break;
									case DVD_SP_EXT_DirectorComments_Normal:
										strcat(langString, " Director Comments");
										break;
									case DVD_SP_EXT_DirectorComments_Big:
										strcat(langString, " Large Director Comments");
										break;
									case DVD_SP_EXT_DirectorComments_Children:
										strcat(langString, " Director Comments for Children");
										break;
								}
							}
							strcpy(playerData->m_availSubs[j], langString);
						}
						else
							playerData->m_availSubs[j][0] = '\0';
					}
					playerData->m_numAvailSubs = ulTotal;
					playerData->m_currSub = subPicDisabled ? -1 : ulCurr;
				}
				hr = pInfo->GetCurrentAudio(&ulTotal, &ulCurr);
				DVD_AudioAttributes dvdAudio;
				if (SUCCEEDED(hr))
				{
					for( UINT j = 0; j < ulTotal; j++ )
					{
						hr = pInfo->GetAudioLanguage(j, &language);
						if (SUCCEEDED(hr))
						{
							sprintf(langString, "%d ", j + 1);
							GetLocaleInfo(language, LOCALE_SENGLANGUAGE, langString + (int)strlen(langString), 256 - (int)strlen(langString));
							pInfo->GetAudioAttributes(j, &dvdAudio);
							if (SUCCEEDED(hr))
							{
								switch (dvdAudio.AudioFormat)
								{
									case DVD_AudioFormat_AC3:
										strcat(langString, " AC3");
										break;
									case DVD_AudioFormat_DTS:
										strcat(langString, " DTS");
										break;
									case DVD_AudioFormat_SDDS:
										strcat(langString, " SDDS");
										break;
								}
								switch (dvdAudio.LanguageExtension)
								{
									case DVD_AUD_EXT_DirectorComments1:
										strcat(langString, " w/Comments");
										break;
									case DVD_AUD_EXT_DirectorComments2:
										strcat(langString, " w/Comments 2");
										break;
								}
								if (dvdAudio.bNumberOfChannels == 6)
								{
									strcat(langString, " 5.1");
								}
							}
							strcpy(playerData->m_availLangs[j], langString);
						}
						else
							playerData->m_availLangs[j][0] = '\0';
					}
					playerData->m_numAvailLangs = ulTotal;
					playerData->m_currLang = ulCurr;
				}
				break;
			case EC_DVD_BUTTON_CHANGE:
				playerData->m_numButtons = (int) lParam1;
				break;
			case EC_DVD_AUDIO_STREAM_CHANGE:
				playerData->m_currLang = (lParam1 == 0xFFFFFFFF) ? -1 : (int) lParam1;
				break;
			case EC_DVD_SUBPICTURE_STREAM_CHANGE:
				playerData->m_currSub = (lParam1 == 0xFFFFFFFF) ? -1 : (int) lParam1;
				if (lParam2 == 0)
					playerData->m_currSub = -1; // disabled subpicture
				break;

	        case EC_DVD_ERROR:
		        slog((env, "DVD Event: Error event received (code %ld)\r\n", (DWORD) lParam1));
				hr = (DWORD) lParam1;
				pIME->FreeEventParams(lEvent, lParam1, lParam2);
				switch (hr)
				{
					case DVD_ERROR_Unexpected:
						throwPlaybackException(env, sage_PlaybackException_DVD_GENERAL, hr);/*"An unexpected error (possibly incorrectly authored content)\nwas encountered.\nCan't playback this DVD-Video disc."*/
						return rv;
					case DVD_ERROR_CopyProtectFail:
						throwPlaybackException(env, sage_PlaybackException_DVD_COPYPROTECT, hr);/*"Key exchange for DVD copy protection failed.\nCan't playback this DVD-Video disc."*/
						return rv;
					case DVD_ERROR_InvalidDVD1_0Disc:
						throwPlaybackException(env, sage_PlaybackException_DVD_GENERAL, hr);/*"This DVD-Video disc is incorrectly authored for v1.0  of the spec.\nCan't playback this disc."*/
						return rv;
					case DVD_ERROR_InvalidDiscRegion:
						throwPlaybackException(env, sage_PlaybackException_DVD_REGION, hr);/*"This DVD-Video disc cannot be played, because it is not\nauthored to play in the current system region.\nThe region mismatch may be fixed by changing the\nsystem region (with DVDRgn.exe)."*/
						return rv;
					case DVD_ERROR_LowParentalLevel:
						throwPlaybackException(env, sage_PlaybackException_DVD_GENERAL, hr);/*"Player parental level is set lower than the lowest parental\nlevel available in this DVD-Video content.\nCannot playback this DVD-Video disc."*/
						return rv;
					case DVD_ERROR_MacrovisionFail:
						throwPlaybackException(env, sage_PlaybackException_DVD_COPYPROTECT, hr);/*"This DVD-Video content is protected by Macrovision.\nThe system does not satisfy Macrovision requirement.\nCan't continue playing this disc."*/
						return rv;
					case DVD_ERROR_IncompatibleSystemAndDecoderRegions:
						throwPlaybackException(env, sage_PlaybackException_DVD_REGION, hr);/*"No DVD-Video disc can be played on this system, because \nthe system region does not match the decoder region.\nPlease contact the manufacturer of this system."*/
						return rv;
					case DVD_ERROR_IncompatibleDiscAndDecoderRegions:
						throwPlaybackException(env, sage_PlaybackException_DVD_REGION, hr);/*"This DVD-Video disc cannot be played on this system, because it is\nnot authored to be played in the installed decoder's region."*/
						return rv;
				}
				throwPlaybackException(env, sage_PlaybackException_DVD_GENERAL, hr);/*"An unknown error occurred while trying to playback the DVD"*/
				return rv;
            
        } // end of switch(lEvent)
		pIME->FreeEventParams(lEvent, lParam1, lParam2) ;
    } // end of while(GetEvent())
	return rv;
}

/*
 * Class:     sage_DShowDVDPlayer
 * Method:    playbackControlMessage0
 * Signature: (JIJJ)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowDVDPlayer_playbackControlMessage0
  (JNIEnv *env, jobject jo, jlong dataPtr, jint msgCode, jlong param1, jlong param2)
{
	if (!dataPtr) return JNI_TRUE;
	CDVDPlayerData* playerData = dynamic_cast<CDVDPlayerData*>((CPlayerData*) dataPtr);
	IDvdInfo2* pInfo = playerData->GetDVDInfo();
	IDvdControl2* pCtrl = playerData->GetDVDCtrl();
	if (!pCtrl || !pInfo) return JNI_TRUE;
	POINT p;
	ULONG ulAvailable;
	ULONG ulCurrent;
	BOOL bIsDisabled;

	HRESULT hr = S_OK;

	//slog((env, "DShowDVDPlayer native playback control msgCode=%d param1=%d param2=%d\r\n", msgCode, (int)param1, (int)param2));
	switch (msgCode)
	{
		case sage_VideoFrame_DVD_CONTROL_MENU:
			hr = pCtrl->ShowMenu(DVD_MENU_Root/*/param1 == 1 ? DVD_MENU_Title : DVD_MENU_Root/**/, DVD_CMD_FLAG_Flush | DVD_CMD_FLAG_Block, NULL);
			break;
		case sage_VideoFrame_DVD_CONTROL_TITLE_SET:
			if (param2 == 0)
				hr = pCtrl->PlayTitle((DWORD) param1, DVD_CMD_FLAG_Flush | DVD_CMD_FLAG_Block, NULL);
			else
				hr = pCtrl->PlayChapterInTitle((DWORD) param1, (DWORD) param2, DVD_CMD_FLAG_Flush | DVD_CMD_FLAG_Block, NULL);
			break;
		case sage_VideoFrame_DVD_CONTROL_CHAPTER_SET:
			hr = pCtrl->PlayChapter((DWORD) param1, DVD_CMD_FLAG_Flush | DVD_CMD_FLAG_Block, NULL);
			break;
		case sage_VideoFrame_DVD_CONTROL_CHAPTER_NEXT:
			hr = pCtrl->PlayNextChapter(DVD_CMD_FLAG_Flush | DVD_CMD_FLAG_Block, NULL);
			break;
		case sage_VideoFrame_DVD_CONTROL_CHAPTER_PREV:
			hr = pCtrl->PlayPrevChapter(DVD_CMD_FLAG_Flush | DVD_CMD_FLAG_Block, NULL);
			break;
		case sage_VideoFrame_DVD_CONTROL_ACTIVATE_CURRENT:
			hr = pCtrl->ActivateButton();
			break;
		case sage_VideoFrame_DVD_CONTROL_RETURN:
			hr = pCtrl->ReturnFromSubmenu(DVD_CMD_FLAG_Flush | DVD_CMD_FLAG_Block, NULL);
			break;
		case sage_VideoFrame_DVD_CONTROL_BUTTON_NAV:
			hr = pCtrl->SelectRelativeButton(param1 == 1 ? DVD_Relative_Upper :
			(param1 == 2 ? DVD_Relative_Right : (param1 == 3 ? DVD_Relative_Lower :
			DVD_Relative_Left)));
			break;
		case sage_VideoFrame_DVD_CONTROL_MOUSE_HOVER:
			p.x = (long) param1;
			p.y = (long) param2;
			hr = pCtrl->SelectAtPosition(p);
			hr = S_OK; // this can fail just because a button isn't there
			break;
		case sage_VideoFrame_DVD_CONTROL_MOUSE_CLICK:
			p.x = (long) param1;
			p.y = (long) param2;
			hr = pCtrl->ActivateAtPosition(p);
			hr = S_OK; // this can fail just because a button isn't there
			break;
		case sage_VideoFrame_DVD_CONTROL_SUBTITLE_TOGGLE:
			hr = pInfo->GetCurrentSubpicture(&ulAvailable, &ulCurrent, &bIsDisabled);
			if (SUCCEEDED(hr))
			{
				hr = pCtrl->SetSubpictureState(bIsDisabled, 0, NULL);
			}
			break;
		case sage_VideoFrame_DVD_CONTROL_SUBTITLE_CHANGE:
			hr = pInfo->GetCurrentSubpicture(&ulAvailable, &ulCurrent, &bIsDisabled);
			if (param1 == -1)
			{
				if (SUCCEEDED(hr) && ulAvailable)
				{
					hr = pCtrl->SelectSubpictureStream((ulCurrent + 1) % ulAvailable, 0, NULL);
					if (bIsDisabled)
						hr = pCtrl->SetSubpictureState(bIsDisabled, 0, NULL);
				}
			}
			else
			{
				if (FAILED(hr))
					bIsDisabled = FALSE;
				hr = pCtrl->SelectSubpictureStream((ULONG)param1, 0, NULL);
				if (bIsDisabled)
					hr = pCtrl->SetSubpictureState(bIsDisabled, 0, NULL);
			}
			break;
		case sage_VideoFrame_DVD_CONTROL_ANGLE_CHANGE:
			if (param1 == -1)
			{
				hr = pInfo->GetCurrentAngle(&ulAvailable, &ulCurrent);
				if (SUCCEEDED(hr) && ulAvailable)
				{
					// Angles are 1-based, not 0-based
					ulCurrent++;
					if (ulCurrent > ulAvailable)
						ulCurrent -= ulAvailable;
					hr = pCtrl->SelectAngle(ulCurrent, 0, NULL);
				}
			}
			else
			{
				hr = pCtrl->SelectAngle((ULONG)param1, 0, NULL);
			}
			break;
		case sage_VideoFrame_DVD_CONTROL_AUDIO_CHANGE:
			if (param1 == -1)
			{
				hr = pInfo->GetCurrentAudio(&ulAvailable, &ulCurrent);
				if (SUCCEEDED(hr) && ulAvailable)
				{
					hr = pCtrl->SelectAudioStream((ulCurrent + 1) % ulAvailable, 0, NULL);
				}
			}
			else
			{
				hr = pCtrl->SelectAudioStream((ULONG)param1, 0, NULL);
			}
			break;

	}
	if (hr == VFW_E_DVD_OPERATION_INHIBITED)
	{
		//hr = -2;
		PLAYEXCEPT_RET0(sage_PlaybackException_DVD_INVALIDOP);
	}
	else if (hr == VFW_E_DVD_INVALIDDOMAIN)
	{
		//hr = -2;
		PLAYEXCEPT_RET0(sage_PlaybackException_DVD_INVALIDOP);
	}
	return SUCCEEDED(hr);
}
