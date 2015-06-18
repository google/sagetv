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
#include <d3d9.h>
#include "../../include/guids.h"
#include "jni-util.h"
#include <vmr9.h>
#include <evr.h>
#include <dxva2api.h>
#include <streams.h>
#include "../../../third_party/Microsoft/VMRAllocator/VMRAllocator.h"
#include "DShowPlayer.h"
#include "../DShowCapture/DShowUtilities.h"
#include "sage_DShowMediaPlayer.h"
#include "sage_DShowTVPlayer.h"
#include "Deinterlace.h"
#include <mpconfig.h>
#include <Il21dec.h>
#include "sage_PlaybackException.h"
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "MainConcept/ModuleConfig/ModuleConfig.h"
#include "MainConcept/ModuleConfig/mpeg2dec_mc.h"
#include "MainConcept/ModuleConfig/common_mc.h"
#include "MainConcept/legacy/imcdprop.h"
#include "MainConcept/legacy/mcfourcc.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
#include "sage_VideoFrame.h"
#include "../../../third_party/Microsoft/EVRPresenter/ISTVEVRPrstr.h"

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    createGraph0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowMediaPlayer_createGraph0
  (JNIEnv *env, jobject jo)
{
	slog((env, "DShowPlayer createGraph0 called\r\n"));
	CComPtr<IGraphBuilder> pGraph = NULL;
	HRESULT hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
		IID_IGraphBuilder, (void **)&(pGraph.p));
	if (hr == 0x800401F0)
	{
		// I don't know why this happens on rare occassions to some users...so let's just redo it since that should fix the error
		slog((env, "Redoing CoInitialize due to error....\r\n"));
		CoInitializeEx(NULL, COM_THREADING_MODE);
		hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
			IID_IGraphBuilder, (void **)&pGraph);
	}
	PLAYEXCEPT_RET0(sage_PlaybackException_DIRECTX_INSTALL);

	CPlayerData* rv = new CPlayerData();
	rv->SetGraph(pGraph);
	rv->CreatePluginHandler();
	return (jlong) rv;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setVideoDecoderFilter0
 * Signature: (JLjava/lang/String;Ljava/util/Map;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setVideoDecoderFilter0
  (JNIEnv *env, jobject jo, jlong dataPtr, jstring jfilterName, jobject jfilterOptions)
{
	if (jfilterName == NULL || env->GetStringLength(jfilterName) == 0 || !dataPtr){return;}
	const char* cName = env->GetStringUTFChars(jfilterName, NULL);
	slog((env, "DShowPlayer setVideoDecoderFilter0(%s) called\r\n", cName));
	CComPtr<IBaseFilter> pFilter = NULL;
	HRESULT hr;
	if (!strcmp("SageTV MPEG Video Decoder", cName)|| !strcmp( "SageTV MPEG-2 Video Decoder", cName ) )
	{
		/*
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
		if (GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
				"EnableDXVA", 0))
		{
			slog((env, "Using DXVA Enabled SageTV MPEG-2 Video Decoder\r\n"));
			hr = CoCreateInstance(CLSID_EM2VD, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void **)&pFilter);
		}
		else
		{
			slog((env, "Using legacy SageTV MPEG Video Decoder\r\n"));
			hr = CoCreateInstance(CLSID_MainConceptMPEGVideoDecoder, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void **)&pFilter);
		}
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
		 */
	}
	else
		hr = FindFilterByName(&pFilter, CLSID_LegacyAmFilterCategory, cName);
	if (SUCCEEDED(hr))
	{
		hr = ((CPlayerData*) dataPtr)->SetVideoDecoder(pFilter);
	}
	if (FAILED(hr))
	{
		elog((env, "Could not add specified video decoder filter to graph hr=0x%x\r\n", hr));
	}
	else
	{
		// Check if its a combo a/v decoder in one filter
		if (!stricmp(cName, "Sigma Designs MPEG-2 hardware decoder"))
		{
			((CPlayerData*) dataPtr)->SetUnifiedDecoder(TRUE);
		}

		// Try to do the MainConcept decoder configuration
		/*
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
		IModuleConfig* pMCConfig = NULL;
		hr = pFilter->QueryInterface(IID_IModuleConfig, (void**)&pMCConfig);
		if (SUCCEEDED(hr))
		{
			// Set it to the default values first
			hr = pMCConfig->SetDefState();
			HTESTPRINT(hr);
			CComVariant var;
			var.Clear();

			unsigned int varData = 3;
			var = varData;
			hr = pMCConfig->SetValue(&EMC_Quality, &var);
			HTESTPRINT(hr);
			varData = GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
				"EnableDXVA", 0);
			var = varData;
			hr = pMCConfig->SetValue(&EMC_HardwareAcceleration, &var);
			slog((env, "VideoDecoder DXVA enabled %d\r\n", varData ));
			HTESTPRINT(hr);
			varData = GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
				"Postprocess", 0);
			var = varData;
			hr = pMCConfig->SetValue(&EMC_PostProcess, &var);
			slog((env, "VideoDecoder Postprocess %d\r\n", varData ));
			HTESTPRINT(hr);
			varData = 2; var = varData;
			hr = pMCConfig->SetValue(&EM2VD_FormatVideoInfo, &var);
			HTESTPRINT(hr);
			varData = (GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
				"ShowErrorFrames", 0) == 0) ? 1 : 0;
			var = varData;
			hr = pMCConfig->SetValue(&EMC_ErrorConcealment, &var);
			slog((env, "VideoDecoder ShowErrorFrames %d\r\n", varData ));
			HTESTPRINT(hr);
			varData = 0; var = varData;
			hr = pMCConfig->SetValue(&EMC_VMRMaintainAspectRatio, &var);
			HTESTPRINT(hr);
			varData = GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
				"DeinterlaceMode", 4);
			var = varData;
			hr = pMCConfig->SetValue(&EMC_Deinterlace, &var);
			slog((env, "VideoDecoder EMC_Deinterlace %d\r\n", varData ));
			HTESTPRINT(hr);

			// Commit the actual changes to the filter
			hr = pMCConfig->CommitChanges(&var);
			HTESTPRINT(hr);
			var.Clear();
			SAFE_RELEASE(pMCConfig);
		}
		else // try the legacy decoder config
		{
			IMCMpegVideoDecoderFilter* pMCDecoder;
			hr = pFilter->QueryInterface(IID_IMCMpegVideoDecoderFilter, (void**)&pMCDecoder);
			if (SUCCEEDED(hr))
			{
				hr = pMCDecoder->put_FilterSetting(VDSETTING_ALLOW_REORDERING,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"AllowReordering", 1));
				HTESTPRINT(hr);
				hr = pMCDecoder->put_FilterSetting(VDSETTING_SHOW_ERRORFRAMES,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"ShowErrorFrames", 0));
				HTESTPRINT(hr);
				hr = pMCDecoder->put_FilterSetting(VDSETTING_DEINTERLACING,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"Deinterlace", 1));
				HTESTPRINT(hr);
				hr = pMCDecoder->put_FilterSetting(VDSETTING_EXPORT_INTERLACING,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"ExportInterlacing", 0));
				HTESTPRINT(hr);
				pMCDecoder->Release();
			}
			IMCMpegVideoDecoderSettings* pMCDecoderSet;
			hr = pFilter->QueryInterface(IID_IMCMpegVideoDecoderSettings, (void**)&pMCDecoderSet);
			if (SUCCEEDED(hr))
			{
				hr = pMCDecoderSet->put_FourccState(FOURCC_YUY2,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"YUY2", 1));
				HTESTPRINT(hr);
				hr = pMCDecoderSet->put_FourccState(FOURCC_UYVY,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"UYVY", 1));
				HTESTPRINT(hr);
				hr = pMCDecoderSet->put_FourccState(FOURCC_YV12,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"YV12", 0));
				HTESTPRINT(hr);
				hr = pMCDecoderSet->put_FourccState(FOURCC_BGR4,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"RGB32", 1));
				HTESTPRINT(hr);
				hr = pMCDecoderSet->put_FourccState(FOURCC_BGR3,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"RGB24", 1));
				HTESTPRINT(hr);
				hr = pMCDecoderSet->put_FourccState(FOURCC_R565,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"RGB16565", 1));
				HTESTPRINT(hr);
				hr = pMCDecoderSet->put_FourccState(FOURCC_R555,
					GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDec",
						"RGB16555", 1));
				HTESTPRINT(hr);
				hr = pMCDecoderSet->Release();
			}
		}
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
		 */
	}
	env->ReleaseStringUTFChars(jfilterName, cName);
	
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setVideoPostProcessingFilter0
 * Signature: (JLjava/lang/String;Ljava/util/Map;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setVideoPostProcessingFilter0
  (JNIEnv *env, jobject jo, jlong dataPtr, jstring jfilterName, jobject jfilterOptions)
{
	if (jfilterName == NULL || env->GetStringLength(jfilterName) == 0 || !dataPtr){return;}
	const char* cName = env->GetStringUTFChars(jfilterName, NULL);
	slog((env, "DShowPlayer setVideoPostProcessingFilter0(%s) called\r\n", cName));
	CComPtr<IBaseFilter> pFilter = NULL;
	HRESULT hr = FindFilterByName(&pFilter, CLSID_LegacyAmFilterCategory, cName);
	env->ReleaseStringUTFChars(jfilterName, cName);
	if (SUCCEEDED(hr))
	{
		hr = ((CPlayerData*) dataPtr)->SetVideoPostProcessor(pFilter);
	}
	if (SUCCEEDED(hr) && jfilterOptions)
	{
		// See if it has dscaler properties to set
		jint dscalerMode = 0;
		if (GetMapIntValue(env, jfilterOptions, "dscaler_mode", &dscalerMode))
		{
			IDeinterlace2* pDScaler;
			hr = pFilter->QueryInterface(IID_IDeinterlace2, (void**)&pDScaler);
			if (SUCCEEDED(hr))
			{
				pDScaler->put_DeinterlaceType(dscalerMode);
				jboolean isOddFirst = FALSE;
				jboolean isDouble = FALSE;
				if (GetMapBoolValue(env, jfilterOptions, "dscaler_odd_field_first", &isOddFirst))
					pDScaler->put_IsOddFieldFirst(isOddFirst ? VARIANT_TRUE : VARIANT_FALSE);
				if (GetMapBoolValue(env, jfilterOptions, "dscaler_double_refresh", &isDouble))
					pDScaler->put_RefreshRateDouble(isDouble ? VARIANT_TRUE : VARIANT_FALSE);
				if (dscalerMode == sage_DShowTVPlayer_DSCALER_MODE_PLUGIN)
				{
					char dscalerPlugin[256];
					DWORD pluginCount = 0;
					if (GetMapStringValue(env, jfilterOptions, "dscaler_plugin", dscalerPlugin, 256, &pluginCount))
					{
						WCHAR wsz[256];
						MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, dscalerPlugin, -1, wsz, 256);
						pDScaler->put_DScalerPluginName(wsz);
					}
				}
				SAFE_RELEASE(pDScaler);
			}
		}
	}
	if (FAILED(hr))
	{
		elog((env, "Could not add specified video post processing filter to graph hr=0x%x\r\n", hr));
	}
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setVideoRendererFilter0
 * Signature: (JLjava/lang/String;Ljava/util/Map;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setVideoRendererFilter0
  (JNIEnv *env, jobject jo, jlong dataPtr, jstring jfilterName, jobject jfilterOptions)
{
	if (jfilterName == NULL || env->GetStringLength(jfilterName) == 0 || !dataPtr){return;}
	CPlayerData* pData = (CPlayerData*) dataPtr;
	const char* cName = env->GetStringUTFChars(jfilterName, NULL);
	slog((env, "DShowPlayer setVideoRendererFilter0(%s) called\r\n", cName));
	CComPtr<IBaseFilter> pFilter = NULL;
	HRESULT hr = FindFilterByName(&pFilter, CLSID_LegacyAmFilterCategory, cName);
	env->ReleaseStringUTFChars(jfilterName, cName);
	BOOL vmr9Config = FALSE;
	BOOL evrConfig = FALSE;
	if (SUCCEEDED(hr) && jfilterOptions)
	{
		jint dxvaMode = 0;
		jint dxvaDeinterlace = 0;
		GetMapIntValue(env, jfilterOptions, "dxva_mpeg_mode", &dxvaMode);
		GetMapIntValue(env, jfilterOptions, "force_deinterlace", &dxvaDeinterlace);
		pData->SetDXVAParameters(dxvaMode, dxvaDeinterlace);

		jboolean ccOK = JNI_TRUE;
		GetMapBoolValue(env, jfilterOptions, "enable_cc", &ccOK);
		if (!ccOK)
			pData->DisableCC();
		// Get the DX9 device pointers, if they don't exist we can't use our custom VMR9 renderer
		jlong jD3D = 0;
		jlong jD3DDevice = 0;
		if (GetMapLongValue(env, jfilterOptions, "d3d_object_ptr", &jD3D) &&
			GetMapLongValue(env, jfilterOptions, "d3d_device_ptr", &jD3DDevice))
		{
			IDirect3D9* pD3D = (IDirect3D9*) jD3D;
			IDirect3DDevice9* pD3DDevice = (IDirect3DDevice9*) jD3DDevice;
			// Set the rendering mode and number of streams.  
			CComPtr<IVMRFilterConfig9> pConfig = NULL;
			// See if it's EVR or VMR
			hr = pFilter->QueryInterface(IID_IVMRFilterConfig9, (void**)&(pConfig.p));
			if (SUCCEEDED(hr))
			{
				slog((env, "Using VMR9 for video rendering\r\n"));
				hr = pConfig->SetRenderingMode(VMR9Mode_Renderless);
				PLAYEXCEPT_RET(sage_PlaybackException_DIRECTX_INSTALL);

				/*
				 * NOTE: If we don't set the number of streams than we don't get the optimal
				 * format types as choices and end up using a private texture when we don't need to.
				 * I think this is because certain features of the
				 * VMR are not available in mixing mode or something like that.
				 * Update: 10/12/2004 - I have now learned that when you put the VMR9
				 * into mixing mode that it will then use the D3DRenderTarget itself
				 * to do the mixing.  I saw a usenet post of the exact VMR9 corruption
				 * problem I was having where the OSD was showing up on the video frame surface.
				 * By not setting the number of streams I keep the VMR9 in Renderless non-mixing mode.
				 * BUT this has the downside of breaking CC support for the VMR9 so we have a registry
				 * setting to allow this.
				 * 10/13/04 - The first problem came back where the format types are wrong. No idea
				 * why this was working fine yesterday.
				 */
				if (GetRegistryDword(HKEY_LOCAL_MACHINE, 
					"Software\\Frey Technologies\\SageTV\\DirectX9", "AllowCCForVMR9", 1) &&
					ccOK)
				{
					// NOTE: We changed this from 2 to 3 because on Vista you need another input
					// to handle subpicture blending for DVD playback. And I don't believe there's any
					// negative to having 3 instead of 2; the big difference is between 1 and 2.
					hr = pConfig->SetNumberOfStreams(3); // video + CC + subpicture
					PLAYEXCEPT_RET(sage_PlaybackException_DIRECTX_INSTALL);
				}
				else
				{
					hr = pConfig->SetNumberOfStreams(1);
					PLAYEXCEPT_RET(sage_PlaybackException_DIRECTX_INSTALL);
				}

				CComPtr<IVMRSurfaceAllocatorNotify9> lpIVMRSurfAllocNotify = NULL;
				pFilter->QueryInterface(IID_IVMRSurfaceAllocatorNotify9, 
					(void**)&(lpIVMRSurfAllocNotify.p));

				// create our surface allocator
				CVMRAllocator* myVMRAllocator = new CVMRAllocator(hr, pD3D, pD3DDevice);
				PLAYEXCEPT_RET(sage_PlaybackException_SAGETV_INSTALL);
				pData->SetVMR9Allocator(myVMRAllocator);

				// let the allocator and the notify know about each other
				hr = lpIVMRSurfAllocNotify->AdviseSurfaceAllocator(0xCAFEBABE, myVMRAllocator);
				HTESTPRINT(hr);
				hr = myVMRAllocator->AdviseNotify(lpIVMRSurfAllocNotify);
				HTESTPRINT(hr);

				hr = S_OK;

				vmr9Config = TRUE;
			}
			else
			{
				slog((env, "Using EVR for video render\r\n"));
				evrConfig = TRUE;
				hr = S_OK;
			}
		}
	}
	if (SUCCEEDED(hr))
		hr = pData->SetVideoRenderer(pFilter);
	if (SUCCEEDED(hr) && evrConfig)
	{
		// Configure the EVR presenter after we add the EVR to the filter graph
		jlong jD3DDevMgr = 0;
		if (GetMapLongValue(env, jfilterOptions, "d3d_device_mgr", &jD3DDevMgr))
		{
			CComPtr<IMFVideoRenderer> lpIMFVideoRenderer = NULL;
			hr = pFilter->QueryInterface(IID_IMFVideoRenderer, (void**)&(lpIMFVideoRenderer.p));
			HTESTPRINT(hr);
			if (SUCCEEDED(hr))
			{
				// Configure EVR to use our custom presenter
				CComPtr<IMFVideoPresenter> lpIMFVideoPresenter = NULL;
				hr = CoCreateInstance(CLSID_CustomEVRPresenter, NULL, CLSCTX_INPROC_SERVER,
					IID_IMFVideoPresenter, (void**)&(lpIMFVideoPresenter.p));
				HTESTPRINT(hr);
				if (SUCCEEDED(hr))
				{
					// Set the Direct3D device pointer
					IDirect3DDeviceManager9* pD3DDevMgr = (IDirect3DDeviceManager9*) jD3DDevMgr;
					ISTVEVRPrstr* pMyEvr = NULL;
					lpIMFVideoPresenter->QueryInterface(IID_ISTVEVRPrstr, (void**)&pMyEvr);
					pMyEvr->set_D3DDeviceMgr(pD3DDevMgr);
					hr = lpIMFVideoRenderer->InitializeRenderer(NULL, lpIMFVideoPresenter);
					HTESTPRINT(hr);
					SAFE_RELEASE(pMyEvr);

					IEVRFilterConfig* pEvrConfig = NULL;
					hr = pFilter->QueryInterface(IID_IEVRFilterConfig, (void**)&pEvrConfig);
					HTESTPRINT(hr);
					// Try three inputs for now; one for video, one for CC and one for subpicture
					// But only use 3 on Vista by default since we've seen issues w/ it on XP
					OSVERSIONINFOEX osInfo;
					ZeroMemory(&osInfo, sizeof(OSVERSIONINFOEX));
					osInfo.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);
					DWORD evrInputsDefault = 1;
					if (GetVersionEx((LPOSVERSIONINFO)&osInfo))
					{
						if (osInfo.dwMajorVersion >= 6)
							evrInputsDefault = 3;
					}
					DWORD evrInputs = GetRegistryDword(HKEY_LOCAL_MACHINE, 
						"Software\\Frey Technologies\\SageTV\\DirectX9", "EVRInputPins", evrInputsDefault);
					slog((env, "Using %d input pins on the EVR\r\n", (int)evrInputs));
					pEvrConfig->SetNumberOfStreams(evrInputs < 1 ? 1 : (evrInputs > 3 ? 3 : evrInputs));
					SAFE_RELEASE(pEvrConfig);
					slog((env, "Finished with EVR configuration OK\r\n"));
				}
			}
		}
	}
	else if (SUCCEEDED(hr) && vmr9Config)
	{
		IVMRDeinterlaceControl9* pVmrDeint = NULL;
		hr = pFilter->QueryInterface(IID_IVMRDeinterlaceControl9, (void**)&pVmrDeint);
		if (SUCCEEDED(hr))
		{
			slog(("Setting up VMR9 deinterlacing\r\n"));
			hr = pVmrDeint->SetDeinterlacePrefs(DeinterlacePref9_NextBest);
			HTESTPRINT(hr);
/*			VMR9VideoDesc VideoDesc;
			DWORD dwNumModes = 0;
			AM_MEDIA_TYPE vmrConn;
			hr = renderInput->ConnectionMediaType(&vmrConn);
			HTESTPRINT(hr);
			if (vmrConn.formattype == FORMAT_VideoInfo2)
			{
				VIDEOINFOHEADER2* vih2 = (VIDEOINFOHEADER2*) vmrConn.pbFormat;
				
				// Fill in the VideoDesc structure
				VideoDesc.dwSize = sizeof(VMR9VideoDesc);
				VideoDesc.dwSampleWidth = vih2->bmiHeader.biWidth;
				VideoDesc.dwSampleHeight = vih2->bmiHeader.biHeight;
				VideoDesc.SampleFormat = ConvertInterlaceFlags(vih2->dwInterlaceFlags);
				VideoDesc.dwFourCC = vih2->bmiHeader.biCompression;
				VideoDesc.InputSampleFreq.dwNumerator = 30000;
				VideoDesc.InputSampleFreq.dwDenominator = 1001;
				VideoDesc.OutputFrameFreq.dwNumerator = 60000;
				VideoDesc.OutputFrameFreq.dwDenominator = 1001;
				hr = pVmrDeint->GetNumberOfDeinterlaceModes(&VideoDesc, 
					&dwNumModes, NULL);
				HTESTPRINT(hr);
				if (SUCCEEDED(hr) && (dwNumModes != 0))
				{
					// Allocate an array for the GUIDs that identify the modes.
					GUID *pModes = new GUID[dwNumModes];
					if (pModes)
					{
						// Fill the array.
						hr = pVmrDeint->GetNumberOfDeinterlaceModes(&VideoDesc, 
							&dwNumModes, pModes);
						if (SUCCEEDED(hr))
						{
							HTESTPRINT(hr);
							// Loop through each item and get the capabilities.
							for (DWORD i = 0; i < dwNumModes; i++)
							{
								VMR9DeinterlaceCaps Caps;
								ZeroMemory(&Caps, sizeof(Caps));
								Caps.dwSize = sizeof(VMR9DeinterlaceCaps);
								hr = pVmrDeint->GetDeinterlaceModeCaps(&(pModes[i]),
									&VideoDesc, &Caps);
								HTESTPRINT(hr);
								if (SUCCEEDED(hr))
								{
									if (Caps.DeinterlaceTechnology == DeinterlaceTech9_BOBLineReplicate)
										slog(("VM9Deinterlacing Tech: BOBLineReplicate\r\n"));
									else if (Caps.DeinterlaceTechnology == DeinterlaceTech9_BOBVerticalStretch)
										slog(("VM9Deinterlacing Tech: BOBVerticalStretch\r\n"));
									else if (Caps.DeinterlaceTechnology == DeinterlaceTech9_MedianFiltering)
										slog(("VM9Deinterlacing Tech: MedianFiltering\r\n"));
									else if (Caps.DeinterlaceTechnology == DeinterlaceTech9_EdgeFiltering)
										slog(("VM9Deinterlacing Tech: EdgeFiltering\r\n"));
									else if (Caps.DeinterlaceTechnology == DeinterlaceTech9_FieldAdaptive)
										slog(("VM9Deinterlacing Tech: FieldAdaptive\r\n"));
									else if (Caps.DeinterlaceTechnology == DeinterlaceTech9_PixelAdaptive)
										slog(("VM9Deinterlacing Tech: PixelAdaptive\r\n"));
									else if (Caps.DeinterlaceTechnology == DeinterlaceTech9_MotionVectorSteered)
										slog(("VM9Deinterlacing Tech: MotionVectorSteered\r\n"));
									else 
										slog(("VM9Deinterlacing Tech: Proprietary...\r\n"));
								}
							}
							if (dwNumModes)
							{
//								hr = pVmrDeint->SetDeinterlaceMode(0, pModes);
//								HTESTPRINT(hr);
							}
						}
						delete [] pModes;
					}
				}
				FreeMediaType(vmrConn);
			}
			GUID realDeint;
			hr = pVmrDeint->GetActualDeinterlaceMode(0, &realDeint);
			LPOLESTR psz;
			StringFromCLSID(realDeint, &psz);
			char conv[64];
			WideCharToMultiByte(CP_ACP, 0, psz, -1, conv, 64, 0, 0);
			CoTaskMemFree(psz);
			slog(("Actual deinterlace: hr=0x%x guid=%s\r\n", hr, conv));
			GUID setDeint;
			hr = pVmrDeint->GetDeinterlaceMode(0, &setDeint);
			StringFromCLSID(setDeint, &psz);
			WideCharToMultiByte(CP_ACP, 0, psz, -1, conv, 64, 0, 0);
			CoTaskMemFree(psz);
			slog(("deinterlace mode: hr=0x%x guid=%s\r\n", hr, conv));
			if (hr == S_FALSE)
			{
				slog(("Setting deinterlace mode to actual mode...\r\n"));
				hr = pVmrDeint->SetDeinterlaceMode(0, &realDeint);
				hr = pVmrDeint->GetDeinterlaceMode(0, &setDeint);
				StringFromCLSID(setDeint, &psz);
				WideCharToMultiByte(CP_ACP, 0, psz, -1, conv, 64, 0, 0);
				CoTaskMemFree(psz);
				slog(("deinterlace mode: hr=0x%x guid=%s\r\n", hr, conv));
			}
*/
			pVmrDeint->Release();
		}
		DWORD vmrMixMode = GetRegistryDword(HKEY_LOCAL_MACHINE,
			"Software\\Frey Technologies\\SageTV\\DirectX9", "YUVMixing", 1);
		if (vmrMixMode)
		{
			IVMRMixerControl9* vmrMix = NULL;
			hr = pFilter->QueryInterface(IID_IVMRMixerControl9, (void**)&vmrMix);
			if (SUCCEEDED(hr))
			{
				DWORD currPrefs = 0;
				hr = vmrMix->GetMixingPrefs(&currPrefs);
				slog((env, "Curr Mix Prefs=0x%x\r\n", currPrefs));
				currPrefs &= ~MixerPref9_RenderTargetMask;
				currPrefs |= MixerPref9_RenderTargetYUV;
				hr = vmrMix->SetMixingPrefs(currPrefs);
				vmrMixMode = currPrefs;
				vmrMix->GetMixingPrefs(&currPrefs);
				slog((env, "Set to 0x%x, hr=0x%x, New Mix Prefs=0x%x\r\n", vmrMixMode, hr, currPrefs));
				vmrMix->Release();
			}
			else
				HTESTPRINT(hr);
		}
	}
	if (FAILED(hr))
	{
		elog((env, "Could not add specified video rendering filter to graph hr=0x%x\r\n", hr));
	}
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setAudioDecoderFilter0
 * Signature: (JLjava/lang/String;Ljava/util/Map;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setAudioDecoderFilter0
  (JNIEnv *env, jobject jo, jlong dataPtr, jstring jfilterName, jobject jfilterOptions)
{
	if (jfilterName == NULL || env->GetStringLength(jfilterName) == 0 || !dataPtr){return;}
	const char* cName = env->GetStringUTFChars(jfilterName, NULL);
	slog((env, "DShowPlayer setAudioDecoderFilter0(%s) called\r\n", cName));
	CComPtr<IBaseFilter> pFilter = NULL;
	HRESULT hr;
	if (!strcmp("SageTV MPEG Audio Decoder", cName) || !strcmp( "SageTV MPEG-2 Audio Decoder", cName ) )
	{
		if (GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\AudioDec",
				"AlwaysSwapForAC3Filter", 1))
		{
			hr = CoCreateInstance(CLSID_AC3_Filter, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void **)&pFilter);
		}
		/*
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
		else
			hr = CoCreateInstance(CLSID_ELMPGLayer2AudioDecoder, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void **)&pFilter);
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
		 */
	}
	else
		hr = FindFilterByName(&pFilter, CLSID_LegacyAmFilterCategory, cName);
	env->ReleaseStringUTFChars(jfilterName, cName);
	if (SUCCEEDED(hr))
		hr = ((CPlayerData*) dataPtr)->SetAudioDecoder(pFilter);
	if (FAILED(hr))
	{
		elog((env, "Could not add specified audio decoder filter to graph hr=0x%x\r\n", hr));
	}
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setAudioPostProcessingFilter0
 * Signature: (JLjava/lang/String;Ljava/util/Map;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setAudioPostProcessingFilter0
  (JNIEnv *env, jobject jo, jlong dataPtr, jstring jfilterName, jobject jfilterOptions)
{
	if (jfilterName == NULL || env->GetStringLength(jfilterName) == 0 || !dataPtr){return;}
	const char* cName = env->GetStringUTFChars(jfilterName, NULL);
	slog((env, "DShowPlayer setAudioPostProcessingFilter0(%s) called\r\n", cName));
	CComPtr<IBaseFilter> pFilter = NULL;
	HRESULT hr = FindFilterByName(&pFilter, CLSID_LegacyAmFilterCategory, cName);
	env->ReleaseStringUTFChars(jfilterName, cName);
	if (SUCCEEDED(hr))
		hr = ((CPlayerData*) dataPtr)->SetAudioPostProcessor(pFilter);
	if (FAILED(hr))
	{
		elog((env, "Could not add specified audio post processing filter to graph hr=0x%x\r\n", hr));
	}
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setAudioRendererFilter0
 * Signature: (JLjava/lang/String;Ljava/util/Map;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setAudioRendererFilter0
  (JNIEnv *env, jobject jo, jlong dataPtr, jstring jfilterName, jobject jfilterOptions)
{
	if (jfilterName == NULL || env->GetStringLength(jfilterName) == 0 || !dataPtr){return;}
	const char* cName = env->GetStringUTFChars(jfilterName, NULL);
	slog((env, "DShowPlayer setAudioRendererFilter0(%s) called\r\n", cName));
	CComPtr<IBaseFilter> pFilter = NULL;
	HRESULT hr = FindFilterByName(&pFilter, CLSID_AudioRendererCategory, cName);
	env->ReleaseStringUTFChars(jfilterName, cName);
	if (SUCCEEDED(hr))
		hr = ((CPlayerData*) dataPtr)->SetAudioRenderer(pFilter);
	if (FAILED(hr))
	{
		elog((env, "Could not add specified audio renderer filter to graph hr=0x%x\r\n", hr));
	}
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setTimeshifting0
 * Signature: (JZJ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setTimeshifting0
  (JNIEnv *env, jobject jo, jlong dataPtr, jboolean timeshift, jlong circbuffsize)
{
	if (!dataPtr) return;
	slog((env, "DShowPlayer setTimeshift=%d buffer=%d\r\n", timeshift, (int) circbuffsize));
	CPlayerData* playData = (CPlayerData*) dataPtr;
	playData->SetTimeshiftingProperties(timeshift, circbuffsize);
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    inactiveFile0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_inactiveFile0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return;
	CPlayerData* playerData = (CPlayerData*) dataPtr;
	playerData->InactiveFile();
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setVideoHWND0
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setVideoHWND0
  (JNIEnv *env, jobject jo, jlong dataPtr, jlong vhwnd)
{
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	IVideoWindow* pVW = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IVideoWindow, (void**)&pVW);
	if (SUCCEEDED(hr))
	{
		slog((env, "DShowPlayer setVideoHWND(%d)\r\n", (int) vhwnd));
		pVW->put_AutoShow(OAFALSE);
		pVW->put_Owner((OAHWND)vhwnd);
		pVW->put_MessageDrain((OAHWND)vhwnd);
		pVW->put_WindowStyle(WS_CHILD | WS_CLIPSIBLINGS | WS_CLIPCHILDREN);
		pVW->put_Visible(OATRUE);

		// We do all of our own aspect ratio control, so don't let DShow do any for us
		// by setting the aspect ratio mode on the video rendering filter's pin
		IEnumFilters *pEnum = NULL;
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
		}
		SAFE_RELEASE(pVW);
	}
	HTESTPRINT(hr);
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setupGraph0
 * Signature: (JLjava/lang/String;Ljava/lang/String;ZZ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setupGraph0
	(JNIEnv *env, jobject jo, jlong dataPtr, jstring jFilename, jstring jhostname, 
		jboolean renderVideo, jboolean renderAudio)
{
	NPE_RET(dataPtr);
	try
	{
		CPlayerData* playData = (CPlayerData*) dataPtr;
		const jchar* wszFilename = env->GetStringChars(jFilename, NULL);
		const char* cHostname = jhostname ? env->GetStringUTFChars(jhostname, NULL) : NULL;
		HRESULT hr = playData->SetSourceFilename((const WCHAR**) &wszFilename, 1, cHostname);
		env->ReleaseStringChars(jFilename, wszFilename);
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
 * Class:     sage_DShowMediaPlayer
 * Method:    teardownGraph0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_teardownGraph0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	CPlayerData* playData = (CPlayerData*) dataPtr;
	if (playData)
	{
		
		try
		{
			slog((env, "Tearing down playback graph\r\n"));

			// Its deconstructor will remove all of the filters from the graph
			// and release all of the COM refs its holding
			delete playData;

		}
		catch (...)
		{
			slog((env, "NATIVE Exception destroying playback graph\r\n"));
		}

	}
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    getGraphVolume0
 * Signature: (J)F
 */
JNIEXPORT jfloat JNICALL Java_sage_DShowMediaPlayer_getGraphVolume0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (pGraph)
	{
		IBasicAudio *pAudio = NULL;
		HRESULT hr = pGraph->QueryInterface(IID_IBasicAudio, (void**)&pAudio);
		if (SUCCEEDED(hr))
		{
			long theVolume;
			hr = pAudio->get_Volume(&theVolume);
			SAFE_RELEASE(pAudio);
			if (SUCCEEDED(hr))
			{
				// the volume ranges from -10,000 to 0
				return (theVolume/10000.0f) + 1;
			}
		}
	}
	return 0;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setGraphVolume0
 * Signature: (JF)F
 */
JNIEXPORT jfloat JNICALL Java_sage_DShowMediaPlayer_setGraphVolume0
  (JNIEnv *env, jobject jo, jlong dataPtr, jfloat vol)
{
	if (!dataPtr) return 0;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	vol = MAX(0, MIN(1.0f, vol));
	if (pGraph)
	{
		IBasicAudio *pAudio = NULL;
		HRESULT hr = pGraph->QueryInterface(IID_IBasicAudio, (void**)&pAudio);
		if (SUCCEEDED(hr))
		{
			long theVolume;
			if (vol == 0)
				theVolume = -10000;
			else
			{
				theVolume = (long)(10000*log((vol*2999.0) + 1)/log(3000.0)) - 10000;
			}
			hr = pAudio->put_Volume(theVolume);
			SAFE_RELEASE(pAudio);
		}
	}
	return vol;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    getColorKey0
 * Signature: (J)Ljava/awt/Color;
 */
JNIEXPORT jobject JNICALL Java_sage_DShowMediaPlayer_getColorKey0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (!pGraph) return 0;
	BOOL forceColorKey = FALSE;
	HRESULT hr;
	DWORD holder;
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(holder);
	DWORD keyedColor = RGB(1,1,1);
	if (RegCreateKeyEx(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DirectShow", 0, 0,
		REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, 0, &myKey, 0) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "TransparentColorKey", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			keyedColor = holder;
			forceColorKey = TRUE;
		}
		RegCloseKey(myKey);
	}
	COLORKEY overlayKey;
	overlayKey.KeyType = CK_RGB;
	overlayKey.PaletteIndex = 0;
	overlayKey.LowColorValue = keyedColor;
	overlayKey.HighColorValue = keyedColor;

	COLORKEY defaultKey;

	BOOL overlayIsUsed = FALSE;
	IEnumFilters *pEnum = NULL;
	if (!forceColorKey)
	{
		// Find the default color key
		hr = pGraph->EnumFilters(&pEnum);
		if (SUCCEEDED(hr))
		{
			IBaseFilter *currFilt = NULL;
			while (pEnum->Next(1, &currFilt, NULL) == S_OK)
			{
				IPin *overlayPin = NULL;
				IEnumPins* pPinEnum = NULL;
				hr = currFilt->EnumPins(&pPinEnum);
				if (hr == NO_ERROR)
				{
					IPin *pPin = NULL;
					hr = E_FAIL;
					while(S_OK == pPinEnum->Next(1, &pPin, NULL))
					{
						IOverlay *pOverlayPin = NULL;
						hr = pPin->QueryInterface(IID_IOverlay, (void**)&pOverlayPin);
						if (SUCCEEDED(hr))
						{
							hr = pOverlayPin->GetDefaultColorKey(&defaultKey);
							if (!forceColorKey && SUCCEEDED(hr))
							{
								keyedColor = defaultKey.LowColorValue;
								slog((env, "Got the default color key 0x%x\r\n", keyedColor));
								//forceColorKey = TRUE;
							}
							SAFE_RELEASE(pOverlayPin);
						}
						SAFE_RELEASE(pPin);
					}
					SAFE_RELEASE(pPinEnum);
				}
				SAFE_RELEASE(currFilt);
			}
			SAFE_RELEASE(pEnum);
		}
	}
	pEnum = NULL;

	// Set the color key value
	hr = pGraph->EnumFilters(&pEnum);
	if (SUCCEEDED(hr))
	{
		IBaseFilter *currFilt = NULL;
		while (pEnum->Next(1, &currFilt, NULL) == S_OK)
		{
			IPin *overlayPin = NULL;
			IEnumPins* pPinEnum = NULL;
			currFilt->EnumPins(&pPinEnum);
			while (pPinEnum->Next(1, &overlayPin, NULL) == S_OK)
			{
				// let's see if it's overlay
				IMixerPinConfig *pOverlayMix = NULL;
				hr = overlayPin->QueryInterface(IID_IMixerPinConfig, (void**)&pOverlayMix);
				if (SUCCEEDED(hr))
				{
					if (!forceColorKey)
						keyedColor = overlayKey.LowColorValue;
					else
						overlayKey.LowColorValue = overlayKey.HighColorValue = keyedColor;
					IPin* testPin = NULL;
					overlayPin->ConnectedTo(&testPin);
					BOOL currPinUsed = FALSE;
					if (testPin)
					{
						currPinUsed = TRUE;
						SAFE_RELEASE(testPin);
					}
					if (currPinUsed)
					{
						if (forceColorKey)
						{
							slog((env, "Setting the color key to 0x%x\r\n", keyedColor));
							hr = pOverlayMix->SetColorKey(&overlayKey);
						}
						else
						{
							hr = pOverlayMix->GetColorKey(&defaultKey, &keyedColor);
							slog((env, "Got the default overlay color key of 0x%x\r\n", keyedColor));
						}
//						HTESTPRINT(hr);
						if (!overlayIsUsed)
						{
							// Force the color key on all connected mixer pins to match
							overlayIsUsed = TRUE;
							forceColorKey = TRUE;
						}
					}
					SAFE_RELEASE(pOverlayMix);
				}
				SAFE_RELEASE(overlayPin);
			}
			SAFE_RELEASE(pPinEnum);
			SAFE_RELEASE(currFilt);
		}
		SAFE_RELEASE(pEnum);
	}
	static jclass colorClass = (jclass) env->NewGlobalRef(env->FindClass("java/awt/Color"));
	static jmethodID constMeth = env->GetMethodID(colorClass, "<init>", "(I)V");
	// Set the alpha to be 255 for the color.
	keyedColor = keyedColor | 0xFF000000;
	slog((env, "Returning the color key as 0x%x\r\n", keyedColor));
	return env->NewObject(colorClass, constMeth, keyedColor);
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    getVideoDimensions0
 * Signature: (J)Ljava/awt/Dimension;
 */
JNIEXPORT jobject JNICALL Java_sage_DShowMediaPlayer_getVideoDimensions0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return NULL;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (!pGraph) return JNI_FALSE;
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
 * Class:     sage_DShowMediaPlayer
 * Method:    resizeVideo0
 * Signature: (JLjava/awt/Rectangle;Ljava/awt/Rectangle;Z)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_resizeVideo0
  (JNIEnv *env, jobject jo, jlong dataPtr, jobject srcVideoRect, jobject destVideoRect,
  jboolean hideCursor)
{
	static jclass rectClass = (jclass) env->NewGlobalRef(env->FindClass("java/awt/Rectangle"));
	static jfieldID fid_rectx = env->GetFieldID(rectClass, "x", "I");
	static jfieldID fid_recty = env->GetFieldID(rectClass, "y", "I");
	static jfieldID fid_rectwidth = env->GetFieldID(rectClass, "width", "I");
	static jfieldID fid_rectheight = env->GetFieldID(rectClass, "height", "I");
	if (!dataPtr) return;
	CPlayerData* playData = (CPlayerData*) dataPtr;
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
	playData->SetVideoPosition(pSrc, pDst, hideCursor);
	return;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setCCState0
 * Signature: (JI)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowMediaPlayer_setCCState0
  (JNIEnv *env, jobject jo, jlong dataPtr, jint ccState)
{
	if (!dataPtr) return JNI_FALSE;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IBaseFilter* p21DecFilt = playData->GetLine21Decoder();
	if (!p21DecFilt) return JNI_FALSE;
	CComPtr<IAMLine21Decoder> p21 = NULL;
	if (SUCCEEDED(p21DecFilt->QueryInterface(IID_IAMLine21Decoder, (void**)&(p21.p))))
	{
		HRESULT hr = S_OK;
		if (ccState == 0)
			hr = p21->SetServiceState(AM_L21_CCSTATE_Off);
		else
		{
			hr = p21->SetServiceState(AM_L21_CCSTATE_On);
			if (SUCCEEDED(hr))
			{
				hr = p21->SetCurrentService((AM_LINE21_CCSERVICE) ccState);
			}
		}
		return (SUCCEEDED(hr)) ? JNI_TRUE : JNI_FALSE;
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    getCCState0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowMediaPlayer_getCCState0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IBaseFilter* p21DecFilt = playData->GetLine21Decoder();
	if (!p21DecFilt) return 0;
	CComPtr<IAMLine21Decoder> p21 = NULL;
	if (SUCCEEDED(p21DecFilt->QueryInterface(IID_IAMLine21Decoder, (void**)&(p21.p))))
	{
		AM_LINE21_CCSTATE ccOnOff;
		HRESULT hr = p21->GetServiceState(&ccOnOff);
		if (SUCCEEDED(hr) && ccOnOff == AM_L21_CCSTATE_On)
		{
			AM_LINE21_CCSERVICE ccServ;
			hr = p21->GetCurrentService(&ccServ);
			if (SUCCEEDED(hr))
				return ccServ;
		}
	}
	return 0;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setNotificationWindow0
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setNotificationWindow0
  (JNIEnv *env, jobject jo, jlong dataPtr, jlong notifyHwnd)
{
	if (!dataPtr) return;
	CPlayerData* playerData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playerData->GetGraph();
	if (!pGraph) return;
	IMediaEventEx *pME = NULL;
	pGraph->QueryInterface(IID_IMediaEventEx, (void**)&pME);
	HRESULT hr = pME->SetNotifyWindow((OAHWND)notifyHwnd, WM_DVD_EVENT, 0);
	HTESTPRINT(hr);
	SAFE_RELEASE(pME);
}

typedef struct _WMGetLicenseData
    {
    DWORD dwSize;
    HRESULT hr;
    WCHAR *wszURL;
    WCHAR *wszLocalFilename;
    BYTE *pbPostData;
    DWORD dwPostDataSize;
    } 	WM_GET_LICENSE_DATA;
/*
 * Class:     sage_DShowMediaPlayer
 * Method:    processEvents0
 * Signature: (J)Z
 */
JNIEXPORT jint JNICALL Java_sage_DShowMediaPlayer_processEvents0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return JNI_TRUE;
	CPlayerData* playerData = (CPlayerData*) dataPtr;

	HRESULT hr = S_OK;
	// Clear out any events in the DVD event queue, this method is called with null params
	// when that's all we need to do
	CComPtr<IMediaEvent> pIME = NULL;
	hr = playerData->GetGraph()->QueryInterface(IID_IMediaEvent, (void**)&pIME);
    long lEvent, lParam1, lParam2;
    long lTimeOut = 0;
	long evErr = 0;

	jint rv = 0;

	while (SUCCEEDED(pIME->GetEvent(&lEvent, (LONG_PTR *) &lParam1, 
                    (LONG_PTR *) &lParam2, lTimeOut)))
	{
        slog((env, "Event: %#x l1=0x%x l2=0x%x\r\n", lEvent, lParam1, lParam2));

		switch(lEvent)
        {
			case WM_DEMUX_EVENT:
				slog((env, "Demuxer end of stream event received\r\n")); 
				rv = 2;
				break;

			case EC_COMPLETE:
				slog((env, "EC_COMPLETE event received\r\n"));
				rv = 1;
				break;

			case EC_VMR_RENDERDEVICE_SET:
				slog((env, "EC_VMR_RENDERDEVICE_SET event received\r\n"));
				rv = 0x53;
				break;

			case EC_WMT_EVENT:
				AM_WMT_EVENT_DATA* drmEvtData = (AM_WMT_EVENT_DATA*) lParam2;
				slog((env, "DRM event received param1=0x%x hr=0x%x\r\n", lParam1, drmEvtData->hrStatus));
				if (lParam1 == 0x9/*WMT_NO_RIGHTS*/)
				{
					char conv[512];
					WideCharToMultiByte(CP_ACP, 0, (WCHAR*)drmEvtData->pData, -1, conv, 512, 0, 0);
					slog((env, "DRM v1 URL=%s\r\n", conv));
				}
				else if (lParam1 == 0x2b/*WMT_LICENSEURL_SIGNATURE_STATE */)
				{
					slog((env, "DRM trusted (0 ?, 1 trust, 2 bad) URL=%d\r\n", drmEvtData->pData));
				}
				else if (lParam1 == 0x17/*WMT_ACQUIRE_LICENSE*/)
				{
					WM_GET_LICENSE_DATA* licData = (WM_GET_LICENSE_DATA*)drmEvtData->pData;
					slog((env, "DRM License Acquired? size=%d hr=0x%x\r\n", licData->dwSize, licData->hr));
				}
				else if (lParam1 == 0x1a/*WMT_NO_RIGHTS_EX*/)
				{
					WM_GET_LICENSE_DATA* licData = (WM_GET_LICENSE_DATA*)drmEvtData->pData;
					slog((env, "DRM v7 - try to acquire license\r\n"));
				}
				break;
        } // end of switch(lEvent)
		pIME->FreeEventParams(lEvent, lParam1, lParam2) ;
    } // end of while(GetEvent())
	return rv;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    demuxPlaybackControl0
 * Signature: (JIJJ)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowMediaPlayer_demuxPlaybackControl0
  (JNIEnv *env, jobject jo, jlong dataPtr, jint evtCode, jlong param1, jlong param2)
{
	if (!dataPtr) return JNI_TRUE;
	CPlayerData* playerData = (CPlayerData*) dataPtr;

	HRESULT hr = S_OK;

	switch (evtCode)
	{
		case sage_VideoFrame_DVD_CONTROL_AUDIO_CHANGE:
			playerData->RenderAudio((int)param1 + 1); // we use 1-based audio stream indices
			break;
		default:
			break;
	}
	return SUCCEEDED(hr);
}
