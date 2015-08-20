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
#include "sage_DShowMusicPlayer.h"
#include "sage_DShowMediaPlayer.h"
#include "imusicvis.h"
#include "guids.h"
#include <jawt_md.h>
#include "sage_PlaybackException.h"

#define MUSIC_DATA_HIST_LEN 200

HINSTANCE sageLoadedAwtLib = NULL;

class CMusicPlayerData : public CPlayerData
{
public:
	CMusicPlayerData() : CPlayerData(), m_currAmpData(NULL),
		m_pMusicVisFilter(NULL)
	{

	}
	virtual ~CMusicPlayerData()
	{
		if (m_pGraph)
			m_pGraph->RemoveFilter(m_pMusicVisFilter);
		SAFE_RELEASE(m_pMusicVisFilter);
		if (m_currAmpData)
		{
			delete [] m_currAmpData->times;
			delete [] m_currAmpData->ampData;
			delete m_currAmpData;
			m_currAmpData = NULL;
		}
	}
	HRESULT SetupVisualization()
	{
		if (m_currAmpData)
		{
			delete m_currAmpData;
		}
		m_currAmpData = new MusicVisData;
		m_currAmpData->numBufs = MUSIC_DATA_HIST_LEN;
		m_currAmpData->lastWritten = 0;
		m_currAmpData->times = new REFERENCE_TIME[MUSIC_DATA_HIST_LEN];
		memset((void*)m_currAmpData->times, 0, sizeof(REFERENCE_TIME)*MUSIC_DATA_HIST_LEN);
		m_currAmpData->ampData = new BYTE[2048 * MUSIC_DATA_HIST_LEN];
		memset(m_currAmpData->ampData, 0, 2048 * MUSIC_DATA_HIST_LEN);
		CComPtr<IBaseFilter> audioRender = NULL;
		audioRender.p = FindAudioRendererFilter(m_pGraph);
		if (audioRender.p != NULL)
		{
			// Disconnect it from its upstream filter and insert the infinite tee. Split
			// it so it connects to the original audio renderer and also to our music visualization render
			HRESULT hr = CoCreateInstance(CLSID_MusicVisFilter, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&m_pMusicVisFilter);
			if (FAILED(hr)) return hr;
			CComPtr<IMusicVis> pMVis = NULL;
			m_pMusicVisFilter->QueryInterface(IID_IMusicVis, (void**)&pMVis);
			pMVis->put_MusicVisData(m_currAmpData);

			hr = m_pGraph->AddFilter(m_pMusicVisFilter, L"Music Visualizer");
			if (FAILED(hr)) return hr;

			CComPtr<IPin> pAudioRenderIn = NULL;
			pAudioRenderIn.p = FindPinByName(audioRender, NULL, PINDIR_INPUT);
			CComPtr<IPin> audioGenPin = NULL;
			hr = pAudioRenderIn->ConnectedTo(&audioGenPin);
			if (FAILED(hr)) return hr;
			hr = m_pGraph->Disconnect(audioGenPin);
			if (FAILED(hr)) return hr;
			hr = m_pGraph->Disconnect(pAudioRenderIn);
			if (FAILED(hr)) return hr;

			CComPtr<IBaseFilter> pTee;
			hr = CoCreateInstance(CLSID_InfTee, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&pTee);
			if (FAILED(hr)) return hr;
			hr = m_pGraph->AddFilter(pTee, L"Audio Tee");
			if (FAILED(hr)) return hr;
			CComPtr<IPin> teeIn = NULL;
			teeIn.p = FindPinByName(pTee, NULL, PINDIR_INPUT);
			CComPtr<IPin> teeOut = NULL;
			teeOut.p = FindPinByName(pTee, NULL, PINDIR_OUTPUT);
			hr = m_pGraph->ConnectDirect(audioGenPin, teeIn, NULL);
			if (FAILED(hr)) return hr;
			hr = m_pGraph->ConnectDirect(teeOut, pAudioRenderIn, NULL);
			if (FAILED(hr)) return hr;
			CComPtr<IPin> teeOut2 = NULL;
			teeOut2.p = FindPinByName(pTee, "Output2", PINDIR_OUTPUT);
			CComPtr<IPin> visInPin = NULL;
			visInPin.p = FindPinByName(m_pMusicVisFilter, NULL, PINDIR_INPUT);
			hr = m_pGraph->ConnectDirect(teeOut2, visInPin, NULL);
			if (FAILED(hr)) return hr;
		}
		return S_OK;
	}
	MusicVisData* GetMusicVisData() { return m_currAmpData; }
protected:
	MusicVisData* m_currAmpData;
	IBaseFilter* m_pMusicVisFilter;
};


/*
 * Class:     sage_DShowMusicPlayer
 * Method:    createGraph0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowMusicPlayer_createGraph0
  (JNIEnv *env, jobject jo)
{
	CComPtr<IGraphBuilder> pGraph = NULL;
	HRESULT hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
		IID_IGraphBuilder, (void **)&pGraph);
	PLAYEXCEPT_RET0(sage_PlaybackException_DIRECTX_INSTALL);

	CMusicPlayerData* rv = new CMusicPlayerData();
	rv->SetGraph(pGraph);
	rv->CreatePluginHandler();
	return (jlong) (dynamic_cast<CPlayerData*>(rv));
}

/*
 * Class:     sage_DShowMusicPlayer
 * Method:    addMusicVisualization0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMusicPlayer_addMusicVisualization0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return;
	CMusicPlayerData* playerData = dynamic_cast<CMusicPlayerData*>((CPlayerData*) dataPtr);
	playerData->SetupVisualization();
}

static int lastRed = 220;
static int lastGreen = 10;
static int lastBlue = 0;
static int lastOrigin = 0;
STDMETHODIMP Render(MusicVisData *pMusicData, HDC hdc, RECT *prc, int bufferIndex, JNIEnv* env)
{
	int colorVel = 1;
	int originAdjust = 1;
	HKEY rootKey = HKEY_LOCAL_MACHINE;
	DWORD readVal;
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(readVal);
	if (RegOpenKeyEx(rootKey, "Software\\Frey Technologies\\Common\\DSFilters\\MusicVisFilter", 
		0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "ColorVelocity", 0, &readType, (LPBYTE)&readVal, &hsize) == ERROR_SUCCESS)
		{
			colorVel = readVal;
		}
		if (RegQueryValueEx(myKey, "Velocity", 0, &readType, (LPBYTE)&readVal, &hsize) == ERROR_SUCCESS)
		{
			originAdjust = readVal;
		}
		RegCloseKey(myKey);
	}
//slog((env, "R=%d G=%d B=%d\r\n", lastRed, lastGreen, lastBlue));
	if (lastRed == 0)
	{
		lastGreen -= colorVel;
		lastBlue += colorVel;
		if (lastGreen <= 0)
		{
			lastBlue -= colorVel;
			lastRed = colorVel + lastGreen;
			lastGreen = 0;
		}
		else if (lastBlue <= 0)
		{
			lastGreen += colorVel;
			lastRed = lastBlue - colorVel;
			lastBlue = 0;
		}
	}
	else if (lastGreen == 0)
	{
		lastBlue -= colorVel;
		lastRed += colorVel;
		if (lastBlue <= 0)
		{
			lastRed -= colorVel;
			lastGreen = colorVel + lastBlue;
			lastBlue = 0;
		}
		else if (lastRed <= 0)
		{
			lastBlue += colorVel;
			lastGreen = lastRed - colorVel;
			lastRed = 0;
		}
	}
	else if (lastBlue == 0)
	{
		lastRed -= colorVel;
		lastGreen += colorVel;
		if (lastRed <= 0)
		{
			lastGreen -= colorVel;
			lastBlue = colorVel + lastRed;
			lastRed = 0;
		}
		else if (lastGreen <= 0)
		{
			lastRed += colorVel;
			lastBlue = lastGreen - colorVel;
			lastGreen = 0;
		}
	}

    HPEN hNewPen = CreatePen( PS_SOLID, 0, RGB(lastRed,lastGreen,lastBlue) );

    // Add the pen to the device context.
    HPEN hOldPen= static_cast<HPEN>(SelectObject( hdc, hNewPen ));
#define NUM_SCREEN_SAMPLES 720
	lastOrigin += originAdjust;
	if (lastOrigin >= prc->bottom || lastOrigin < prc->top)
	{
		if (originAdjust >= 0)
			lastOrigin = prc->top;
		else
			lastOrigin = prc->bottom - 1;
	}
	int width = prc->right - prc->left;
	int height = prc->bottom - prc->top;
    MoveToEx( hdc, prc->left, prc->top + height/2, NULL);
	for (int i = 0; i < 1024; i++)
	{
		int offset = ((pMusicData->ampData[bufferIndex*2048 + i] + 
			pMusicData->ampData[bufferIndex*2048 + i + 1024])*height/512);
		offset = max(0, min(offset, height/2));
		for (int j = prc->left + (i*width)/NUM_SCREEN_SAMPLES; j < prc->left + ((i+1)*width)/NUM_SCREEN_SAMPLES; j++)
		{
			MoveToEx(hdc, j, lastOrigin - offset, NULL);
			LineTo(hdc, j, lastOrigin + offset);
		}
	}

    // Delete your brush.
    // Delete your pen.
    if (hNewPen)
    {
        SelectObject( hdc, hOldPen );
        DeleteObject( hNewPen );
    }

    // You're done for this round.
    return S_OK;
}

void loadAWTLib()
{
	if (!sageLoadedAwtLib)
	{
		/*
		 * Explicitly load jawt.dll by using the Windows Registry to locate the current version to use.
		 */
		HKEY rootKey = HKEY_LOCAL_MACHINE;
		char currVer[16];
		HKEY myKey;
		DWORD readType;
		DWORD hsize = sizeof(currVer);
		if (RegOpenKeyEx(rootKey, "Software\\JavaSoft\\Java Runtime Environment", 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return;
		}
		
		if (RegQueryValueEx(myKey, "CurrentVersion", 0, &readType, (LPBYTE)currVer, &hsize) != ERROR_SUCCESS)
		{
			return;
		}
		RegCloseKey(myKey);
		char pathKey[1024];
		strcpy(pathKey, "Software\\JavaSoft\\Java Runtime Environment\\");
		strcat(pathKey, currVer);
		char jvmPath[1024];
		if (RegOpenKeyEx(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return;
		}
		hsize = sizeof(jvmPath);
		if (RegQueryValueEx(myKey, "RuntimeLib", 0, &readType, (LPBYTE)jvmPath, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return;
		}
		RegCloseKey(myKey);

		// Go to the 2nd to last backslash, and append jawt.dll from there to complete the string
		char* goodSlash = strrchr(jvmPath, '\\');
		if (!goodSlash) return;
		*goodSlash = 0;
		goodSlash = strrchr(jvmPath, '\\');
		if (!goodSlash) return;
		strcpy(goodSlash + 1, "jawt.dll");

		sageLoadedAwtLib = LoadLibrary(jvmPath);
	}
}

/*
 * Class:     sage_DShowMusicPlayer
 * Method:    renderVisualization0
 * Signature: (JLjava/awt/Canvas;J)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMusicPlayer_renderVisualization0
  (JNIEnv *env, jobject jo, jlong dataPtr, jobject jCanvas, jlong jMediaTime)
{
	if (!dataPtr) return;
	CMusicPlayerData* playerData = dynamic_cast<CMusicPlayerData*>((CPlayerData*) dataPtr);

	JAWT awt;
	JAWT_DrawingSurface* ds = NULL;
	JAWT_DrawingSurfaceInfo* dsi = NULL;
	JAWT_Win32DrawingSurfaceInfo* dsi_win = NULL;
	jboolean result;
	jint lock;
	HRESULT hr;
	try
	{
		loadAWTLib();
		// Get the AWT, we have to explicitly load it otherwise it'll try it load it
		// when we execute and the link will fail.
		typedef jboolean (JNICALL *AWTPROC)(JNIEnv* env, JAWT* awt);
		
		awt.version = JAWT_VERSION_1_3;
		AWTPROC lpfnProc = (AWTPROC)GetProcAddress(sageLoadedAwtLib, "_JAWT_GetAWT@8");
		result = lpfnProc(env, &awt);
		if (result == JNI_FALSE)
			return;

		// Get the drawing surface
		ds = awt.GetDrawingSurface(env, jCanvas);
		if (ds == NULL)
			return;

		// Lock the drawing surface
		lock = ds->Lock(ds);
		if ((lock & JAWT_LOCK_ERROR) != 0)
			return;

		// Get the drawing surface info
		dsi = ds->GetDrawingSurfaceInfo(ds);

		// Get the platform-specific drawing info
		dsi_win = (JAWT_Win32DrawingSurfaceInfo*)dsi->platformInfo;

		// DRAW HERE
		MusicVisData* musicData = playerData->GetMusicVisData();
		if (!musicData) return;
		/*
		 * We want the buffer with the earliest timestamp that comes after the argument timestamp
		 */
		int bufNum = musicData->lastWritten + 2;
		bufNum %= musicData->numBufs;
		REFERENCE_TIME earlyTime = musicData->times[bufNum];
		int bestBuf = bufNum;
		for (int i = 0; i < musicData->numBufs - 2; i++)
		{
			REFERENCE_TIME currTime = musicData->times[(bufNum + i) % musicData->numBufs];
			if (currTime > jMediaTime && (earlyTime < jMediaTime || currTime < earlyTime))
			{
				earlyTime = currTime;
				bestBuf = (bufNum + i) % musicData->numBufs;
			}
		}

		RECT grc;
		GetClientRect(dsi_win->hwnd, &grc);
		//slog((env, "About to render visualization top=%d left=%d right=%d bottom=%d bufNum=%d mediaTime=%s audioTime=%s lastWritten=%d\r\n",
		//		grc.top, grc.left, grc.right, grc.bottom, bestBuf, (LPCSTR)CDisp(mediaTime, CDISP_DEC),
		//		(LPCSTR)CDisp(musicData->times[bestBuf], CDISP_DEC), musicData->lastWritten));
		hr = Render(musicData, dsi_win->hdc, &grc, bestBuf, env);
		//slog((env, "Rendered visualization hr=0x%x\r\n", hr));

		// Free the drawing surface info
		ds->FreeDrawingSurfaceInfo(dsi);

		// Unlock the drawing surface
		ds->Unlock(ds);

		// Free the drawing surface
		awt.FreeDrawingSurface(ds);
	}
	catch (...)
	{
		elog((env, "EXCEPTION ERROR in renderVisualization.\r\n"));
	}
}
