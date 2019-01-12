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
#include "guids.h"
#include "sage_TranscodeJob.h"
#include "../DShowCapture/DShowUtilities.h"
#include "../DShowPlayer/DShowPlayer.h"
#include "sage_PlaybackException.h"
#include "IMpegDeMux.h"
#include <streams.h>
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "DivX/AVIWriterConfig.h"
#include "MainConcept/mcmpguid.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
//#define LAZER_XCODE
#ifdef LAZER_XCODE
#include "impegmux.h"
#include "ks.h"
#include "codecapi.h"
#endif

BOOL APIENTRY DllMain( HANDLE hModule, 
                       DWORD  ul_reason_for_call, 
                       LPVOID lpReserved
					 )
{
    return TRUE;
}

BOOL isDivXVideoPin(IPin* pPin)
{
	// These are in little endian for the format block
	DWORD divxCodes[] = { (('3' << 24) | ('V' << 16) | ('I' << 8) | ('D')),
		(('4' << 24) | ('V' << 16) | ('I' << 8) | ('D')),
		(('X' << 24) | ('V' << 16) | ('I' << 8) | ('D')),
		(('0' << 24) | ('5' << 16) | ('X' << 8) | ('D')) };
	IEnumMediaTypes *mtEnum = NULL;
	pPin->EnumMediaTypes(&mtEnum);
	AM_MEDIA_TYPE *pMT = NULL;
	while (S_OK == mtEnum->Next(1, &pMT, NULL))
	{
		// The biCompression parameter of the bitmapinfoheader in the format block shows
		// the fourcc code for the video, compare it to that
		if (pMT->formattype == FORMAT_VideoInfo)
		{
			DWORD currFourCC = ((VIDEOINFOHEADER*)pMT->pbFormat)->bmiHeader.biCompression;
			for (int i = 0; i < 4; i++)
			{
				if (divxCodes[i] == currFourCC)
				{
					DeleteMediaType(pMT);
					SAFE_RELEASE(mtEnum);
					return TRUE;
				}
			}
		}
		DeleteMediaType(pMT);
    }
	SAFE_RELEASE(mtEnum);
	return FALSE;
}

BOOL isMPEG2VideoPin(IPin* pPin)
{
	IEnumMediaTypes *mtEnum = NULL;
	pPin->EnumMediaTypes(&mtEnum);
	AM_MEDIA_TYPE *pMT = NULL;
	while (S_OK == mtEnum->Next(1, &pMT, NULL))
	{
		if (pMT->subtype == MEDIASUBTYPE_MPEG2_VIDEO)
		{
			DeleteMediaType(pMT);
			SAFE_RELEASE(mtEnum);
			return TRUE;
		}
		DeleteMediaType(pMT);
    }
	SAFE_RELEASE(mtEnum);
	return FALSE;
}

DWORD getVideoFrameRate(IPin* pPin)
{
	DWORD rv = 30000;
	IEnumMediaTypes *mtEnum = NULL;
	pPin->EnumMediaTypes(&mtEnum);
	AM_MEDIA_TYPE *pMT = NULL;
	while (S_OK == mtEnum->Next(1, &pMT, NULL))
	{
		// The biCompression parameter of the bitmapinfoheader in the format block shows
		// the fourcc code for the video, compare it to that
		if (pMT->formattype == FORMAT_VideoInfo)
		{
			REFERENCE_TIME frameTime = ((VIDEOINFOHEADER*)pMT->pbFormat)->AvgTimePerFrame;
			rv = (DWORD)((ULONGLONG)(10010000000L) / frameTime);
		}
		DeleteMediaType(pMT);
    }
	SAFE_RELEASE(mtEnum);
	slog(("Using divx video frame rate %d\r\n", rv));
	return rv;
}

//#define ENABLE_DIVX_FILEWRITER

class CTranscodePlayerData : public CPlayerData
{
public:
	CTranscodePlayerData() : CPlayerData(), m_pAviMux(NULL), m_pSink(NULL)
	{

	}
	virtual ~CTranscodePlayerData()
	{
		SAFE_RELEASE(m_pAviMux);
		SAFE_RELEASE(m_pSink);
	}
	IBaseFilter* GetMux()
	{
		return m_pAviMux;
	}
	HRESULT RemuxMPEG2IntoAVI(const WCHAR* pwFilename)
	{
		HRESULT hr;
		divxVideo = FALSE;
		IPin* videoSource;
		// Check for DivX video first which means we use a different mux
#ifdef ENABLE_DIVX_FILEWRITER
		videoSource = GetSourceVideoPin();
		ASSERT(videoSource != NULL);
		divxVideo = isDivXVideoPin(videoSource);
		if (divxVideo)
		{
			// Use the DivX filewriter if we're doing a DivX AVI file
			hr = loadDivXFW(&m_pAviMux);
			if (SUCCEEDED(hr))
			{
				hr = m_pGraph->AddFilter(m_pAviMux, L"DivX AVI Mux");
				if (FAILED(hr))
				{
					SAFE_RELEASE(videoSource);
					return hr;
				}
				slog(("Configured the DivX Mux writer for transcode\r\n"));

				IAVIWriterConfig* pDivxConfig = NULL;
				hr = m_pAviMux->QueryInterface(IID_IAVIWriterConfig, (void**)&pDivxConfig);
				ASSERT(SUCCEEDED(hr));
				pDivxConfig->put_CaptureMode(FALSE);
				pDivxConfig->put_Scale(1001);
				pDivxConfig->put_Rate(getVideoFrameRate(videoSource));
				pDivxConfig->Release();
			}
			else
			{
				elog(("DivX AVI Mux was not found!\r\n"));
				divxVideo = FALSE;
			}
		}
		SAFE_RELEASE(videoSource);
#endif
		if (!divxVideo)
		{
			// First add the AVI Mux filter, and then add our Dump filter to do the writing.

			hr = CoCreateInstance(CLSID_AviDest, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&m_pAviMux);
			if (FAILED(hr)) return hr;
			hr = m_pGraph->AddFilter(m_pAviMux, L"AVI Mux");
			if (FAILED(hr)) return hr;
			slog(("Added the AVI Mux filter for transcode\r\n"));
			hr = CoCreateInstance(CLSID_MPEG2Dump, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&m_pSink);
			if (FAILED(hr)) return hr;
			hr = m_pGraph->AddFilter(m_pSink, L"Dump");
			if (FAILED(hr)) return hr;
			slog(("Added the Dump filter for transcode\r\n"));

			// Configure the AVI Mux filter
			{
				CComPtr<IConfigInterleaving> pInterleave = NULL;
				hr = m_pAviMux->QueryInterface(IID_IConfigInterleaving, (void**)&pInterleave);
				_ASSERT(SUCCEEDED(hr));
				// Suggested times for doing Divx from some research
				REFERENCE_TIME t1 = 10000000;
				REFERENCE_TIME t2 = 5000000;
				hr = pInterleave->put_Interleaving(&t1, &t2);
				HTESTPRINT(hr);
				hr = pInterleave->put_Mode(INTERLEAVE_FULL);
				HTESTPRINT(hr);
			}

			{
				CComPtr<IConfigAviMux> pConfigAvi = NULL;
				hr = m_pAviMux->QueryInterface(IID_IConfigAviMux, (void**)&pConfigAvi);
				_ASSERT(SUCCEEDED(hr));
				//hr = pConfigAvi->SetMasterStream(0);
				HTESTPRINT(hr);
				hr = pConfigAvi->SetOutputCompatibilityIndex(TRUE);
				HTESTPRINT(hr);
			}
			slog(("Configured the AVI Mux filter for transcode\r\n"));
		}

		// CONNECT THE OUTPUT PINS ON THE DEMUX TO THE AVI INPUT PINS
		videoSource = GetSourceVideoPin();
		ASSERT(videoSource != NULL);
		BOOL recompress = FALSE;
		if (isMPEG2VideoPin(videoSource))
		{
			// This is an MPEG2->MPEG4 conversion. We need to decode->encode the audio & video.
			// Even though it would seem like the audio doesn't need to be encoded->decoded, we end up
			// with a/v sync issues if we don't do that part
			recompress = TRUE;
			slog(("Performing MPEG2 decompress before recompression\r\n"));
			// Decode the streams with our own MPEG2 decoders
			/*
			 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START

			CComPtr<IBaseFilter> vidDec = NULL;
			hr = CoCreateInstance(CLSID_EM2VD, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void **)&(vidDec.p));
			if (FAILED(hr)) return hr;
			slog(("Created MPEG video decoder\r\n"));
			hr = SetVideoDecoder(vidDec.p);
			if (FAILED(hr)) return hr;
			slog(("Added MPEG video decoder to graph\r\n"));
			CComPtr<IBaseFilter> vidEnc = NULL;
			hr = loadDivX(&(vidEnc.p));
			if (FAILED(hr)) return hr;
			slog(("Loaded DivX video encoder filter\r\n"));
			hr = SetVideoPostProcessor(vidEnc.p);
			if (FAILED(hr)) return hr;
			slog(("Added DivX video encoder to graph\r\n"));
			IPin* nextVPin = FindUnconnectedPin(vidDec.p, PINDIR_INPUT);
			ASSERT(nextVPin != NULL);
			hr = m_pGraph->ConnectDirect(videoSource, nextVPin, NULL);
			SAFE_RELEASE(videoSource);
			SAFE_RELEASE(nextVPin);
			if (FAILED(hr)) return hr;
			slog(("Connected video source to video decoder\r\n"));
			videoSource = FindUnconnectedPin(vidDec.p, PINDIR_OUTPUT);
			ASSERT(videoSource != NULL);
			nextVPin = FindUnconnectedPin(vidEnc.p, PINDIR_INPUT);
			ASSERT(nextVPin != NULL);
			hr = m_pGraph->ConnectDirect(videoSource, nextVPin, NULL);
			SAFE_RELEASE(videoSource);
			SAFE_RELEASE(nextVPin);
			if (FAILED(hr)) return hr;
			slog(("Connected video decoder to video encoder\r\n"));
			videoSource = FindUnconnectedPin(vidEnc.p, PINDIR_OUTPUT);
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
		 */
		}
		IPin* aviInput = FindUnconnectedPin(m_pAviMux, PINDIR_INPUT);
		ASSERT(aviInput != NULL);
		hr = m_pGraph->ConnectDirect(videoSource, aviInput, NULL);
		SAFE_RELEASE(videoSource);
		SAFE_RELEASE(aviInput);
		if (FAILED(hr)) return hr;
		IPin* audioSource = GetSourceAudioPin(1);
		ASSERT(audioSource != NULL);
		if (recompress)
		{
			// Decode the streams with our own MPEG2 decoders
			/*
			 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
			CComPtr<IBaseFilter> audDec = NULL;
			hr = CoCreateInstance(CLSID_ELMPGLayer2AudioDecoder, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void **)&(audDec.p));
			if (FAILED(hr)) return hr;
			slog(("Created MPEG audio decoder\r\n"));
			hr = SetAudioDecoder(audDec.p);
			if (FAILED(hr)) return hr;
			slog(("Added MPEG audio decoder to graph\r\n"));
			CComPtr<IBaseFilter> audEnc = NULL;
			hr = CoCreateInstance(CLSID_EL2AudioEncoder, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void **)&(audEnc.p));
			if (FAILED(hr)) return hr;
			slog(("Loaded MPEG audio encoder filter\r\n"));
			hr = SetAudioPostProcessor(audEnc.p);
			if (FAILED(hr)) return hr;
			slog(("Added MPEG audio encoder to graph\r\n"));
			IPin* nextAPin = FindUnconnectedPin(audDec.p, PINDIR_INPUT);
			ASSERT(nextAPin != NULL);
			hr = m_pGraph->ConnectDirect(audioSource, nextAPin, NULL);
			SAFE_RELEASE(audioSource);
			SAFE_RELEASE(nextAPin);
			if (FAILED(hr)) return hr;
			slog(("Connected audio source to audio decoder\r\n"));
			audioSource = FindUnconnectedPin(audDec.p, PINDIR_OUTPUT);
			ASSERT(audioSource != NULL);
			nextAPin = FindUnconnectedPin(audEnc.p, PINDIR_INPUT);
			ASSERT(nextAPin != NULL);
			hr = m_pGraph->ConnectDirect(audioSource, nextAPin, NULL);
			SAFE_RELEASE(audioSource);
			SAFE_RELEASE(nextAPin);
			if (FAILED(hr)) return hr;
			slog(("Connected audio decoder to audio encoder\r\n"));
			audioSource = FindUnconnectedPin(audEnc.p, PINDIR_OUTPUT);
			 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
			 */
		}
		IPin* aviInput2 = FindUnconnectedPin(m_pAviMux, PINDIR_INPUT);
		ASSERT(aviInput2 != NULL);
		hr = m_pGraph->ConnectDirect(audioSource, aviInput2, NULL);
		SAFE_RELEASE(audioSource);
		SAFE_RELEASE(aviInput2);
		if (FAILED(hr)) return hr;
		slog(("Connected the demux to the AVI Mux filter for transcode\r\n"));

		// We still need to connect the mux output to the dump
		if (m_pSink)
		{
			CComPtr<IPin> muxOut = NULL;
			muxOut.p = FindUnconnectedPin(m_pAviMux, PINDIR_OUTPUT);
			ASSERT(muxOut != NULL);
			CComPtr<IPin> dumpIn = NULL;
			dumpIn.p = FindUnconnectedPin(m_pSink, PINDIR_INPUT);
			ASSERT(dumpIn != NULL);
			hr = m_pGraph->ConnectDirect(muxOut, dumpIn, NULL);
			if (FAILED(hr)) return hr;
			slog(("Connected the AVI Mux filter to the dump for transcode\r\n"));
		}


		// Now set the output filename in the sink filter
		{
			CComPtr<IFileSinkFilter> pfsf = NULL;
			(m_pSink ? m_pSink : m_pAviMux)->QueryInterface(IID_IFileSinkFilter, (void**)&pfsf);
			hr = pfsf->SetFileName(pwFilename, NULL);
			if (FAILED(hr)) return hr;
		}
		slog(("Completed transcode graph setup\r\n"));
		return S_OK;
	}
#ifdef LAZER_XCODE
	HRESULT TranscodeToMPEG4Stream()
	{
		HRESULT hr;
		IPin* videoSource;
		hr = CoCreateInstance(CLSID_MpegMux, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void**)&m_pAviMux);
		if (FAILED(hr)) return hr;
		hr = m_pGraph->AddFilter(m_pAviMux, NULL);
		if (FAILED(hr)) return hr;
		slog(("Added the MPEG Mux filter for transcode\r\n"));

		// CONNECT THE OUTPUT PINS ON THE DEMUX TO THE AVI INPUT PINS
		videoSource = GetSourceVideoPin();
		ASSERT(videoSource != NULL);
		slog(("Insertings Lazer Xcoder into graph\r\n"));
		CComPtr<IBaseFilter> vidDec = NULL;
		hr = CoCreateInstance(CLSID_LumanateX, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void **)&(vidDec.p));
		if (FAILED(hr)) return hr;
		slog(("Created MPEG video decoder-MPEG4 encoder\r\n"));
		hr = SetVideoDecoder(vidDec.p);
		if (FAILED(hr)) return hr;
		slog(("Added MPEG video decoder to graph\r\n"));
		IPin* nextVPin = FindUnconnectedPin(vidDec.p, PINDIR_INPUT);
		ASSERT(nextVPin != NULL);
		hr = m_pGraph->ConnectDirect(videoSource, nextVPin, NULL);
		SAFE_RELEASE(videoSource);
		SAFE_RELEASE(nextVPin);
		if (FAILED(hr)) return hr;
		slog(("Connected video source to video decoder\r\n"));
		CComQIPtr<ICodecAPI> pCodecAPI = vidDec;
		VARIANT theValue;
		theValue.vt = VT_BSTR;
	    WCHAR wszId[512];
	    StringFromGUID2(CODECAPI_GUID_AVEndMPEG4Video, wszId, 512);
		theValue.bstrVal = SysAllocString(wszId);
		hr = pCodecAPI->SetValue(&CODECAPI_AVEncCodecType, &theValue);
		SysFreeString(theValue.bstrVal);
		if (FAILED(hr)) return hr;
		theValue.vt = VT_UI4;
		theValue.ulVal = 768000;
		hr = pCodecAPI->SetValue(&CODECAPI_AVEncCommonMeanBitRate, &theValue);
		if (FAILED(hr)) return hr;
		theValue.ulVal = (320 << 16) | (240);
		hr = pCodecAPI->SetValue(&CODECAPI_AVEncVideoDisplayDimension, &theValue);
		if (FAILED(hr)) return hr;
		slog(("Configured MPEG4 encoding parameters\r\n"));
		videoSource = FindUnconnectedPin(vidDec.p, PINDIR_OUTPUT);

		IPin* aviInput = FindUnconnectedPin(m_pAviMux, PINDIR_INPUT);
		ASSERT(aviInput != NULL);
		hr = m_pGraph->ConnectDirect(videoSource, aviInput, NULL);
		SAFE_RELEASE(videoSource);
		SAFE_RELEASE(aviInput);
		if (FAILED(hr)) return hr;
		slog(("Connected xcoder to mux\r\n"));

		IPin* audioSource = GetSourceAudioPin(1);
		ASSERT(audioSource != NULL);
		// Decode the streams with our own MPEG2 decoders
		CComPtr<IBaseFilter> audDec = NULL;
		hr = CoCreateInstance(CLSID_AC3_Filter/*CLSID_MainConceptMPEGAudioDecoder*/, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void **)&(audDec.p));
		if (FAILED(hr)) return hr;
		slog(("Created MPEG audio decoder\r\n"));
		hr = SetAudioDecoder(audDec.p);
		if (FAILED(hr)) return hr;
		slog(("Added MPEG audio decoder to graph\r\n"));
		CComPtr<IBaseFilter> audEnc = NULL;
		hr = CoCreateInstance(CLSID_EL2AudioEncoder, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void **)&(audEnc.p));
		if (FAILED(hr)) return hr;
		slog(("Loaded MPEG audio encoder filter\r\n"));
		hr = SetAudioPostProcessor(audEnc.p);
		if (FAILED(hr)) return hr;
		slog(("Added MPEG audio encoder to graph\r\n"));
		IPin* nextAPin = FindUnconnectedPin(audDec.p, PINDIR_INPUT);
		ASSERT(nextAPin != NULL);
		hr = m_pGraph->ConnectDirect(audioSource, nextAPin, NULL);
		SAFE_RELEASE(audioSource);
		SAFE_RELEASE(nextAPin);
		if (FAILED(hr)) return hr;
		slog(("Connected audio source to audio decoder\r\n"));
		audioSource = FindUnconnectedPin(audDec.p, PINDIR_OUTPUT);
		ASSERT(audioSource != NULL);
		nextAPin = FindUnconnectedPin(audEnc.p, PINDIR_INPUT);
		ASSERT(nextAPin != NULL);
		hr = m_pGraph->ConnectDirect(audioSource, nextAPin, NULL);
		SAFE_RELEASE(audioSource);
		SAFE_RELEASE(nextAPin);
		if (FAILED(hr)) return hr;
		slog(("Connected audio decoder to audio encoder\r\n"));
		audioSource = FindUnconnectedPin(audEnc.p, PINDIR_OUTPUT);

		IPin* aviInput2 = FindUnconnectedPin(m_pAviMux, PINDIR_INPUT);
		ASSERT(aviInput2 != NULL);
		hr = m_pGraph->ConnectDirect(audioSource, aviInput2, NULL);
		SAFE_RELEASE(audioSource);
		SAFE_RELEASE(aviInput2);
		if (FAILED(hr)) return hr;
		slog(("Connected the demux to the AVI Mux filter for transcode\r\n"));

		// Now set the output filename in the sink filter
		{
			CComPtr<IMpegMux> pMuxy = NULL;
			hr = m_pAviMux->QueryInterface(IID_IMpegMux, (void**)&pMuxy);
			if (FAILED(hr)) return hr;
			hr = pMuxy->SetBuffering(TRUE);
			if (FAILED(hr)) return hr;
		}
		slog(("Completed transcode graph setup\r\n"));
		return S_OK;
	}
#endif
	HRESULT WaitForTranscodeCompletion()
	{
		long evCode;
		LONG_PTR param1, param2;
		HRESULT hr;
		int numStreams = 1; // MS AVI Mux only sends out one, DivX will send out 3
#define XCODE_COMPLETES_ON_EC_COMPLETE
#ifdef XCODE_COMPLETES_ON_EC_COMPLETE
		CComPtr<IMediaEvent> pEvent = NULL;
		m_pGraph->QueryInterface(IID_IMediaEvent, (void**)&pEvent);
		pEvent->CancelDefaultHandling(EC_COMPLETE);
		pEvent->CancelDefaultHandling(EC_ERRORABORT);
		pEvent->CancelDefaultHandling(EC_USERABORT);
		while (hr = pEvent->GetEvent(&evCode, &param1, &param2, INFINITE), SUCCEEDED(hr))
		{
			slog(("Got event code=%d lParam1=0x%x lParam2=0x%x\r\n", evCode, param1, param2));
			hr = pEvent->FreeEventParams(evCode, param1, param2);
			switch(evCode) 
			{ 
				// Call application-defined functions for each 
				// type of event that you want to handle.
				case EC_COMPLETE:
					if (--numStreams > 0)
						continue;
					else
					{
						// The DIVX Filewriter sends out EC_COMPLETE messages too early sometimes,
						// so wait just in case.
						if (divxVideo)
							Sleep(20000);
						return S_OK;
					}
				case EC_ERRORABORT:
				case EC_USERABORT:
					return E_FAIL;
			} 
		}
#else
		CComPtr<IMediaControl> pMC = NULL;
		m_pGraph->QueryInterface(IID_IMediaControl, (void**)&pMC);
		OAFilterState currState = 0;
		while (hr = pMC->GetState(5000, &currState), SUCCEEDED(hr))
		{
			if (currState == State_Stopped)
			{
				slog(("Transcode graph stopped itself.\r\n"));
				break;
			}
		}
#endif
		return hr;

	}
	BOOL divxVideo;
protected:
	IBaseFilter* m_pAviMux;
	// sink will be null if the mux does the file writing also
	IBaseFilter* m_pSink;
};

/*
 * Class:     sage_TranscodeJob
 * Method:    createTranscodeEngine0
 * Signature: (BBLjava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_TranscodeJob_createTranscodeEngine0
  (JNIEnv *env, jclass jc, jbyte bStartFormat, jbyte bEndFormat, jstring jsSrcFile, jstring jsDestFile)
{
	// NOTE FOR NOW THE start format must be MPEG2 PS and the end format must be AVI w/ the streams
	// muxable into an AVI container (so it can't be MPEG2 video)
//	CoInitializeEx(NULL, COM_THREADING_MODE);
	try
	{
		CComPtr<IGraphBuilder> pGraph = NULL;
		HRESULT hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
			IID_IGraphBuilder, (void **)&pGraph);
		PLAYEXCEPT_RET0(sage_PlaybackException_DIRECTX_INSTALL);

		CTranscodePlayerData* rv = new CTranscodePlayerData();
		rv->SetGraph(pGraph);
#ifndef LAZER_XCODE
		rv->SetRemuxOptimize(TRUE);
#endif

		// Now setup the source file in the graph
		const jchar* wszFilename = env->GetStringChars(jsSrcFile, NULL);
		hr = rv->SetSourceFilename(reinterpret_cast<const WCHAR*>(wszFilename), NULL);
		env->ReleaseStringChars(jsSrcFile, wszFilename);
		PLAYEXCEPT_RET0(sage_PlaybackException_FILESYSTEM);

		if (!rv->HasDemux())
		{
			throwPlaybackException(env, sage_PlaybackException_FILESYSTEM, 0);
			return 0;
		}

#ifdef LAZER_XCODE
		hr = rv->TranscodeToMPEG4Stream();
#else
		const jchar* wszFilename2 = env->GetStringChars(jsDestFile, NULL);
		hr = rv->RemuxMPEG2IntoAVI(reinterpret_cast<const WCHAR*>(wszFilename2));
		env->ReleaseStringChars(jsDestFile, wszFilename2);
#endif
		PLAYEXCEPT_RET0(sage_PlaybackException_FILESYSTEM);

		return (jlong) rv;
	}
	catch (...)
	{
		throwPlaybackException(env, sage_PlaybackException_SAGETV_INSTALL, 0);
		return 0;
	}
}

/*
 * Class:     sage_TranscodeJob
 * Method:    doNativeTranscode0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_TranscodeJob_doNativeTranscode0
  (JNIEnv *env, jclass jc, jlong ptr)
{
	NPE_RET0(ptr);
	CTranscodePlayerData* pTrans = (CTranscodePlayerData*) ptr;
	IGraphBuilder* pGraph = pTrans->GetGraph();
	_ASSERT(pGraph);
	{
		IMediaFilter *pMediaFilter = 0;
		pGraph->QueryInterface(IID_IMediaFilter, (void**)&pMediaFilter);
		pMediaFilter->SetSyncSource(NULL);
		pMediaFilter->Release();

		CComPtr<IMediaControl> pMC = NULL;
		HRESULT hr = pGraph->QueryInterface(IID_IMediaControl, (void**)&pMC);
		_ASSERT(SUCCEEDED(hr));

		hr = pMC->Run();
		PLAYEXCEPT_RET0(sage_PlaybackException_FILESYSTEM);
		
		slog((env, "Started transcoding...waiting for it to finish...\r\n"));
		// Wait for it to enter the run state
#ifndef LAZER_XCODE
		hr = pTrans->WaitForTranscodeCompletion();
		PLAYEXCEPT_RET0(sage_PlaybackException_FILESYSTEM);
#endif
/*		FILTER_STATE testGraphState;
		pMC->GetState(1000, (OAFilterState*)&testGraphState);
		slog(("Graph has been started state=%d\r\n", testGraphState));
		while (evCode == 0)
		{
			hr = pME->WaitForCompletion(100, &evCode);
			slog((env, "Waiting for xcode to finish...\r\n"));
		}
*/		slog((env, "Transcoding complete. hr=0x%x\r\n", hr));
		// This Stop was hanging sometimes so I removed it, it'll happen in the cleanup anyways.
		// I think it's due to the DivX AVI File Writer
		//pMC->Stop();
		return JNI_TRUE;//(evCode == EC_COMPLETE) && SUCCEEDED(hr);
	}
}

/*
 * Class:     sage_TranscodeJob
 * Method:    killNativeTranscode0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_TranscodeJob_killNativeTranscode0
  (JNIEnv *env, jclass jc, jlong ptr)
{
	if (ptr)
	{
		slog((env, "Destroying the transcode graph.\r\n"));
		CTranscodePlayerData* pTrans = (CTranscodePlayerData*) ptr;

		IGraphBuilder* pGraph = pTrans->GetGraph();
		if (pGraph)
		{
			CComPtr<IMediaControl> pMC = NULL;
			HRESULT hr = pGraph->QueryInterface(IID_IMediaControl, (void**)&pMC);
			if (SUCCEEDED(hr))
			{
				FILTER_STATE testGraphState;
				hr = pMC->GetState(0, (OAFilterState*)&testGraphState);
				if (hr == VFW_S_STATE_INTERMEDIATE || testGraphState != State_Stopped)
				{
					slog((env, "Stopping the transcode graph.\r\n"));
					hr = pMC->Stop();
					hr = pMC->GetState(50, (OAFilterState*)&testGraphState);
				}
			}
		}
		delete pTrans;
		slog((env, "Stopped and destroyed the transcode graph.\r\n"));
//		CoUninitialize();
	}
}
#ifdef LAZER_XCODE
/*
 * Class:     sage_TranscodeJob
 * Method:    readTranscodeData0
 * Signature: ([B)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_TranscodeJob_readTranscodeData0
  (JNIEnv *env, jclass jc, jlong ptr, jbyteArray jb)
{
	CTranscodePlayerData* pTrans = (CTranscodePlayerData*) ptr;
	IBaseFilter* pAviMux = pTrans->GetMux();
	CComPtr<IMpegMux> pMuxy = NULL;
	HRESULT hr = pAviMux->QueryInterface(IID_IMpegMux, (void**)&pMuxy);
	if (FAILED(hr)) return JNI_FALSE;
//	void* nbuf = env->GetPrimitiveArrayCritical(jb, NULL);
	jbyte* nbuf = env->GetByteArrayElements(jb, NULL);
	pMuxy->get_buffer(nbuf, 32768, 0);
//	env->ReleasePrimitiveArrayCritical(jb, nbuf, 0);
	env->ReleaseByteArrayElements(jb, nbuf, 0);

	return JNI_TRUE;
}
#endif
