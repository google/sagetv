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
#include "../../include/sage_PVR350OSDRenderingPlugin.h"
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "../../include/Conexant/ivactypes.h"
#include "../../include/Conexant/ivacCtrlProp.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
#include "../DShowCapture/DShowUtilities.h"
#include "PVR350OSDPlugin.h"
#include <ks.h>

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
 * Class:     sage_PVR350OSDRenderingPlugin
 * Method:    updateOSD0
 * Signature: (J[IIILjava/awt/Rectangle;Ljava/awt/Rectangle;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_PVR350OSDRenderingPlugin_updateOSD0(JNIEnv *env, jobject jo,
																	   jlong osdHandle, jintArray jimage,
																	   jint width, jint height,
																	   jobject targetRect,
																	   jobject videoRect)
{
	static jclass rectClass = (jclass) env->NewGlobalRef(env->FindClass("java/awt/Rectangle"));
	static jfieldID fid_rectx = env->GetFieldID(rectClass, "x", "I");
	static jfieldID fid_recty = env->GetFieldID(rectClass, "y", "I");
	static jfieldID fid_rectwidth = env->GetFieldID(rectClass, "width", "I");
	static jfieldID fid_rectheight = env->GetFieldID(rectClass, "height", "I");

	PVR350OSDPluginNativeData* osdData = (PVR350OSDPluginNativeData*) osdHandle;
	jboolean rv = JNI_TRUE;
	IBaseFilter* pOSD = osdData->pOSD;
	WaitForSingleObject(osdData->mutex350OSD, MUTEX350WAITTIME);

	IKsPropertySet* ksProp = NULL;
	HRESULT hr = pOSD->QueryInterface(IID_IKsPropertySet, (void**)&ksProp);
	if (FAILED(hr))
	{
		ReleaseMutex(osdData->mutex350OSD);
		return JNI_FALSE;
	}

	DWORD arrLen = width*height;

	/*
	 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
	OSD_BUFF_LOC osdBuf;
	osdBuf.memCopySize = arrLen*4;
	jint* critArr = (jint*)env->GetPrimitiveArrayCritical(jimage, NULL);
	OSD_USER_BUFPTR pBuf = critArr;

	if (osdData->ntscModeFor350)
	{
		osdBuf.OSDMemOffset = osdData->lastOSDMemWrite == 0 ? 1400000 : 0;

		hr = ksProp->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_MEMCOPY, &osdBuf,
			sizeof(OSD_BUFF_LOC), pBuf, arrLen*4);
		TEST_AND_PRINT
		env->ReleasePrimitiveArrayCritical(jimage, critArr, JNI_ABORT);
		rv = rv && SUCCEEDED(hr);
		if (width == 720 && height >= 480)
		{
			// Full OSD update, just do a video pointer flip, don't waste time with a BLT
			osdData->lastOSDMemWrite = osdBuf.OSDMemOffset;
		}
		else
		{

			BLT_BASE_PARAM m_baseParam;
			OSD_BLT_COPY m_BltCopy;
				
			m_baseParam.ROPCode = 0xA;
			m_baseParam.alphaBlend = 1;
			m_baseParam.pixelBlend = 0;
			m_baseParam.destPixelMask = 0xFFFFFFFF;
			m_baseParam.bltWidth = width;
			m_baseParam.bltHeight = height;
			m_baseParam.destRectAddr = osdData->lastOSDMemWrite +
				(env->GetIntField(targetRect, fid_rectx) + env->GetIntField(targetRect, fid_recty)*720)*4;//0;
			m_baseParam.destStride = 720;//width;

			memcpy(&(m_BltCopy.bltBaseParam), &m_baseParam, sizeof(BLT_BASE_PARAM));
			m_BltCopy.srcStride = width;
			m_BltCopy.srcRectAddr = osdBuf.OSDMemOffset;

			hr = ksProp->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_BLT_COPY, &m_BltCopy,
				sizeof(OSD_BLT_COPY), &m_BltCopy, sizeof(OSD_BLT_COPY));
			TEST_AND_PRINT
			rv = rv && SUCCEEDED(hr);

			// IMPORTANT: This was what was causing a hang in V2RC3 because I was doing it before
			// the BLT block above. The hang was caused by the OSD still performing a BLT while
			// I made a seek call.  If I wait here for the BLT to complete before I release the
			// mutex on the 350 then I should prevent those hangs
			OSD_BLT_STATE osdState = 1;
			int numWaits = 0;
			while (osdState && SUCCEEDED(hr) && (numWaits++ < 500))
			{
				DWORD dwReturned;
				hr = ksProp->Get(PROPSETID_IVAC_DISPLAY, IVAC_OSD_BLT_STATE, &osdState, sizeof(OSD_BLT_STATE),
					&osdState, sizeof(OSD_BLT_STATE), &dwReturned);
				TEST_AND_PRINT
				slog((env, "OSDBLTState=%d\r\n", osdState));
				Sleep(10);
			}
		}
	}
	else
	{
		if (width == 720 && height >= 480)
		{
			osdBuf.OSDMemOffset = 0;

			hr = ksProp->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_MEMCOPY, &osdBuf,
				sizeof(OSD_BUFF_LOC), pBuf, arrLen*4);
			TEST_AND_PRINT
			env->ReleasePrimitiveArrayCritical(jimage, critArr, JNI_ABORT);
			rv = rv && SUCCEEDED(hr);

			// Full OSD update, just do a video pointer flip, don't waste time with a BLT
			osdData->lastOSDMemWrite = 0;
		}
		else
		{
			osdBuf.OSDMemOffset = 1700000;

			hr = ksProp->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_MEMCOPY, &osdBuf,
				sizeof(OSD_BUFF_LOC), pBuf, arrLen*4);
			TEST_AND_PRINT
			env->ReleasePrimitiveArrayCritical(jimage, critArr, JNI_ABORT);
			rv = rv && SUCCEEDED(hr);

			BLT_BASE_PARAM m_baseParam;
			OSD_BLT_COPY m_BltCopy;
					
			m_baseParam.ROPCode = 0xA;
			m_baseParam.alphaBlend = 1;
			m_baseParam.pixelBlend = 0;
			m_baseParam.destPixelMask = 0xFFFFFFFF;
			m_baseParam.bltWidth = width;
			m_baseParam.bltHeight = height;
			m_baseParam.destRectAddr = osdData->lastOSDMemWrite +
				(env->GetIntField(targetRect, fid_rectx) + env->GetIntField(targetRect, fid_recty)*720)*4;
			m_baseParam.destStride = 720;

			m_BltCopy.bltBaseParam = m_baseParam;
			m_BltCopy.srcStride = width;
			m_BltCopy.srcRectAddr = osdBuf.OSDMemOffset;
			hr = ksProp->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_BLT_COPY, &m_BltCopy,
				sizeof(OSD_BLT_COPY), &m_BltCopy, sizeof(OSD_BLT_COPY));
			TEST_AND_PRINT
			rv = rv && SUCCEEDED(hr);

		}
	}

	if (videoRect)
	{
		VIDEO_SCREEN_COORD vCoord;
		vCoord.topLeftX = env->GetIntField(videoRect, fid_rectx);
		vCoord.topLeftY = env->GetIntField(videoRect, fid_recty);
		vCoord.videoWinWidth = env->GetIntField(videoRect, fid_rectwidth);
		vCoord.videoWinHeight = env->GetIntField(videoRect, fid_rectheight);
		hr = ksProp->Set(PROPSETID_IVAC_DISPLAY, IVAC_VIDEO_SCREEN_COORD, &vCoord, 
			sizeof(VIDEO_SCREEN_COORD), &vCoord, sizeof(VIDEO_SCREEN_COORD));
		TEST_AND_PRINT
		rv = rv && SUCCEEDED(hr);
	}

	OSD_DISPLAYED_BUFFER dispBuf;
	dispBuf.OSDBuffStart = osdData->lastOSDMemWrite;
	dispBuf.OSDInitXOffset = 0;
	dispBuf.OSDInitYOffset = 0;
	dispBuf.OSDLines = osdData->ntscModeFor350 ? (480*2) : (576*2);
	dispBuf.OSDStride = 1440;
	hr = ksProp->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_DISPLAYED_BUFFER, &dispBuf,
		sizeof(OSD_DISPLAYED_BUFFER), &dispBuf, sizeof(OSD_DISPLAYED_BUFFER));
	TEST_AND_PRINT
	rv = rv && SUCCEEDED(hr);

 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
	SAFE_RELEASE(ksProp);

	ReleaseMutex(osdData->mutex350OSD);
	return rv;
}

/*
 * Class:     sage_PVR350OSDRenderingPlugin
 * Method:    openOSD0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_PVR350OSDRenderingPlugin_openOSD0
  (JNIEnv *env, jobject jo)
{
	CoInitializeEx(NULL, COM_THREADING_MODE);
	HRESULT hr;
	slog((env, "BVF open350OSD0 called\r\n"));
	PVR350OSDPluginNativeData rv;
	ZeroMemory(&rv, sizeof(PVR350OSDPluginNativeData));
	rv.ntscModeFor350 = 1;
	// We can't load this by the GUID just like some of the KS CC stuff
    ICreateDevEnum *pSysDevEnum = NULL;
    IEnumMoniker *pEnum = NULL;
    IMoniker *pMoniker = NULL;
    hr = CoCreateInstance(CLSID_SystemDeviceEnum, NULL, 
        CLSCTX_INPROC_SERVER, IID_ICreateDevEnum, 
        (void**)&pSysDevEnum);
	if (FAILED(hr)) return 0;
    hr = pSysDevEnum->CreateClassEnumerator(KSCATEGORY_DATADECOMPRESSOR, &pEnum, 0);
	if (hr != S_OK)
	{
		// Nothing to enumerate
		SAFE_RELEASE(pSysDevEnum);
		return hr;
	}
    while (S_OK == pEnum->Next(1, &pMoniker, NULL))
    {
        IPropertyBag *pPropBag = NULL;
        hr = pMoniker->BindToStorage(0, 0, IID_IPropertyBag, 
                                (void **)&pPropBag);
		if (SUCCEEDED(hr))
		{
			VARIANT var;
			VariantInit(&var);
			hr = pPropBag->Read(L"FriendlyName", &var, 0);
			if (SUCCEEDED(hr))
			{
				char conv[512];
				WideCharToMultiByte(CP_ACP, 0, var.bstrVal, -1, conv, 512, 0, 0);
				if (strstr(conv, "PVR"))
				{
        			hr = pMoniker->BindToObject(NULL, NULL, IID_IBaseFilter,
        				(void**)(&(rv.pOSD)));
        			if (SUCCEEDED(hr))
					{
						VariantClear(&var);
						SAFE_RELEASE(pPropBag);
						SAFE_RELEASE(pMoniker);
						break;
					}
				}
			}
			VariantClear(&var);
		}
        SAFE_RELEASE(pPropBag);
        SAFE_RELEASE(pMoniker);
    }

    SAFE_RELEASE(pSysDevEnum);
    SAFE_RELEASE(pEnum);
	if (rv.pOSD)
	{
		hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
			IID_IGraphBuilder, (void **)&(rv.pGraph));
		TEST_AND_BAIL
		if (!rv.mutex350OSD)
		{
			rv.mutex350OSD = CreateMutex(NULL, FALSE, "Global\\SageTV350Sync");
			WaitForSingleObject(rv.mutex350OSD, MUTEX350WAITTIME);
		}
		hr = rv.pGraph->AddFilter(rv.pOSD, L"OSD");
		if (FAILED(hr))
		{
			SAFE_RELEASE(rv.pOSD);
			SAFE_RELEASE(rv.pGraph);
			ReleaseMutex(rv.mutex350OSD);
			CloseHandle(rv.mutex350OSD);
			elog((env, "Error opening 350 OSD hr=0x%x\r\n", hr));
			return 0;
		}
		DWORD holder;
		HKEY myKey;
		DWORD readType;
		DWORD hsize = sizeof(holder);
		if (RegCreateKeyEx(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common", 0, 0,
			REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, 0, &myKey, 0) == ERROR_SUCCESS)
		{
			if (RegQueryValueEx(myKey, "NTSC", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
			{
				rv.ntscModeFor350 = holder;
			}
			else
			{
				RegSetValueEx(myKey, "NTSC", 0, REG_DWORD, (LPBYTE) &(rv.ntscModeFor350), sizeof(rv.ntscModeFor350));
			}

			RegCloseKey(myKey);
		}

		PVR350OSDPluginNativeData* realRV = new PVR350OSDPluginNativeData;
		memcpy(realRV, &rv, sizeof(PVR350OSDPluginNativeData));
		if (Print350OSDInfo(realRV, env, TRUE))
		{
			ReleaseMutex(realRV->mutex350OSD);
			return (jlong) realRV;
		}
		else
		{
			ReleaseMutex(realRV->mutex350OSD);
			Java_sage_PVR350OSDRenderingPlugin_closeOSD0(env, jo, (jlong)realRV);
			return 0;
		}
	}
	CoUninitialize();
	return 0;
}

/*
 * Class:     sage_PVR350OSDRenderingPlugin
 * Method:    closeOSD0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_PVR350OSDRenderingPlugin_closeOSD0
  (JNIEnv *env, jobject jo, jlong osdHandle)
{
	HRESULT hr;
	PVR350OSDPluginNativeData* osdData = (PVR350OSDPluginNativeData*) osdHandle;
	slog((env, "BVF close350OSD0 called\r\n"));
	if (osdData->mutex350OSD)
		WaitForSingleObject(osdData->mutex350OSD, MUTEX350WAITTIME);

	IKsPropertySet* pOsdProps = NULL;
	hr = osdData->pOSD->QueryInterface(IID_IKsPropertySet, (void**)&pOsdProps);
	if (SUCCEEDED(hr))
	{
		// Clear the OSD from the display so it doesn't affect other apps

		/*
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
		OSD_STATE osdState = 0x22;
		pOsdProps->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_STATE, &osdState, sizeof(OSD_STATE),
			&osdState, sizeof(OSD_STATE));

		VIDEO_SCREEN_COORD vCoord;
		vCoord.topLeftX = 0;
		vCoord.topLeftY = 0;
		vCoord.videoWinWidth = 720;
		vCoord.videoWinHeight = osdData->ntscModeFor350?480:576;
		pOsdProps->Set(PROPSETID_IVAC_DISPLAY, IVAC_VIDEO_SCREEN_COORD, &vCoord, 
			sizeof(VIDEO_SCREEN_COORD), &vCoord, sizeof(VIDEO_SCREEN_COORD));
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
		 */

		SAFE_RELEASE(pOsdProps);
	}
	if (osdData->pGraph)
		osdData->pGraph->RemoveFilter(osdData->pOSD);
	SAFE_RELEASE(osdData->pOSD);
	SAFE_RELEASE(osdData->pGraph);

	if (osdData->mutex350OSD)
	{
		ReleaseMutex(osdData->mutex350OSD);
		CloseHandle(osdData->mutex350OSD);
	}

	delete osdData;
	CoUninitialize();
}

jboolean Print350OSDInfo(PVR350OSDPluginNativeData* osdData, JNIEnv* env, BOOL clearMem)
{
	/*
	 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
	DWORD dwReturned;
	OSD_GLOBAL_ALPHA gAlpha;
	OSD_SCREEN_COORD osdCoord;
	DWORD deflicker;
	OSD_BUFFER_INFO m_bufferInfo;
	IKsPropertySet* pOsdProps = NULL;
	HRESULT hr = osdData->pOSD->QueryInterface(IID_IKsPropertySet, (void**)&pOsdProps);
	TEST_AND_BAIL
	hr = pOsdProps->Get(PROPSETID_IVAC_DISPLAY, IVAC_OSD_BUFFER_INFO, &m_bufferInfo,
		sizeof(OSD_BUFFER_INFO), &m_bufferInfo, sizeof(OSD_BUFFER_INFO), &dwReturned);
	TEST_AND_BAIL
	slog((env, "OSD memsize=%d\r\n", m_bufferInfo.OSDMemSize));

	if (clearMem)
	{
		void* pBuf = new BYTE[m_bufferInfo.OSDMemSize];
		ZeroMemory(pBuf, m_bufferInfo.OSDMemSize);
		OSD_BUFF_LOC osdBuf;
		osdBuf.memCopySize = m_bufferInfo.OSDMemSize;
		osdBuf.OSDMemOffset = 0;
		hr = pOsdProps->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_MEMCOPY, &osdBuf,
			sizeof(OSD_BUFF_LOC), pBuf, m_bufferInfo.OSDMemSize);
		delete [] pBuf;
		TEST_AND_BAIL

		deflicker = 1;
		hr = pOsdProps->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_DEFLICKERSTATE, &deflicker,
			sizeof(DWORD), &deflicker, sizeof(DWORD));
		TEST_AND_BAIL

		gAlpha.AlphaEnable = 1;
		gAlpha.AlphaValue = 255;
		hr = pOsdProps->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_GLOBAL_ALPHA, &gAlpha,
			sizeof(OSD_GLOBAL_ALPHA), &gAlpha, sizeof(OSD_GLOBAL_ALPHA));
		TEST_AND_BAIL

		VIDEO_SCREEN_COORD vCoord;
		vCoord.topLeftX = 0;
		vCoord.topLeftY = 0;
		vCoord.videoWinWidth = 720;
		vCoord.videoWinHeight = osdData->ntscModeFor350?480:576;
		hr = pOsdProps->Set(PROPSETID_IVAC_DISPLAY, IVAC_VIDEO_SCREEN_COORD, &vCoord,
			sizeof(VIDEO_SCREEN_COORD), &vCoord, sizeof(VIDEO_SCREEN_COORD));
		TEST_AND_BAIL

		osdCoord.TopLeftX = 0;
		osdCoord.TopLeftY = 0;
		osdCoord.BottomRightX = 720;
		osdCoord.BottomRightY = osdData->ntscModeFor350?480:576;
		hr = pOsdProps->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_SCREEN_COORD, &osdCoord,
			sizeof(OSD_SCREEN_COORD), &osdCoord, sizeof(OSD_SCREEN_COORD));
		TEST_AND_BAIL

		OSD_STATE osdState = 0x23;
		hr = pOsdProps->Set(PROPSETID_IVAC_DISPLAY, IVAC_OSD_STATE, &osdState, sizeof(OSD_STATE),
			&osdState, sizeof(OSD_STATE));
		TEST_AND_BAIL
	}

	// These Get calls are necessary to initialize the OSD correctly
	OSD_PIXEL_FORMAT pixFormat;
	OSD_DISPLAYED_BUFFER osdBuff;
	OSD_STATE osdState;
	hr = pOsdProps->Get(PROPSETID_IVAC_DISPLAY, IVAC_OSD_PIXEL_FORMAT, &pixFormat,
		sizeof(OSD_PIXEL_FORMAT), &pixFormat, sizeof(OSD_PIXEL_FORMAT), &dwReturned);
	hr = pOsdProps->Get(PROPSETID_IVAC_DISPLAY, IVAC_OSD_GLOBAL_ALPHA, &gAlpha,
		sizeof(OSD_GLOBAL_ALPHA), &gAlpha, sizeof(OSD_GLOBAL_ALPHA), &dwReturned);
	hr = pOsdProps->Get(PROPSETID_IVAC_DISPLAY, IVAC_OSD_SCREEN_COORD, &osdCoord,
		sizeof(OSD_SCREEN_COORD), &osdCoord, sizeof(OSD_SCREEN_COORD), &dwReturned);
	hr = pOsdProps->Get(PROPSETID_IVAC_DISPLAY, IVAC_OSD_DISPLAYED_BUFFER, &osdBuff,
		sizeof(OSD_DISPLAYED_BUFFER), &osdBuff, sizeof(OSD_DISPLAYED_BUFFER), &dwReturned);
	hr = pOsdProps->Get(PROPSETID_IVAC_DISPLAY, IVAC_OSD_STATE, &osdState,
		sizeof(OSD_STATE), &osdState, sizeof(OSD_STATE), &dwReturned);
	hr = pOsdProps->Get(PROPSETID_IVAC_DISPLAY, IVAC_OSD_DEFLICKERSTATE, &deflicker,
		sizeof(DWORD), &deflicker, sizeof(DWORD), &dwReturned);

	SAFE_RELEASE(pOsdProps);
	 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
	 */
	return JNI_TRUE;
}

