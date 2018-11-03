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
#include "DShowPlayer.h"
#include <d3d9.h>
#include <streams.h>
#include "../../../third_party/Microsoft/VMRAllocator/VMRAllocator.h"
#include "guids.h"
#include "isharedasync.h"
#include "../DShowCapture/DShowUtilities.h"
#include <dvdmedia.h>
#include <evr9.h>
#include "sage_DShowTVPlayer.h"
#include "jni-util.h"
#include "ISDeMux.h"
#include "IMpegDeMux.h"
#include "dmodshow.h"
#include <Il21dec.h>

#ifdef ENABLE_SAGETV_WMDRM_SUPPORT
#include <C:\WMSDK\WMFSDK9\include\wmsdkidl.h>
#endif

//#define ADD_TO_ROT_IN_RELEASE

long AFDFormatDump( void* context, short bytes, void* mesg )
{
	CPlayerData*  PlayerData = (CPlayerData*)context;
	printf( "AFD MSG : %s\r\n", (char *) mesg );
	return 1;
}

long CC_DUMP( void* context, short cc_num, void* cc_data, short bar_num, void* bar_data )
{
	int i;
	unsigned long* lptr;
	unsigned short*sptr;
	unsigned char* cc_ptr;
	unsigned long  cc_bytes;
	unsigned short bar_top, bar_bottom, bar_left, bar_right;
	CPlayerData*  PlayerData = (CPlayerData*)context;

	lptr = (unsigned long*)cc_data;
	for ( i = 0; i<cc_num && *lptr; i++ )
	{
// wnjj - NOT 64-bit safe (*lptr is unsigned long but used as an unsigned char * cc_ptr)
// Function doesn't seem to do anything useful right now anyway.
		cc_ptr = (unsigned char*)*lptr++;
		cc_bytes = *lptr++;
		//printf( "got CC Data %d bytes start from 0x%p\n", cc_bytes, cc_ptr );
	}

	sptr = (unsigned short*)bar_data;
	for ( i = 0; i<bar_num ; i++ )
	{
		bar_top = *sptr++;    //if bar_top == -1; not specified
		bar_bottom = *sptr++; //if bar_bottom == -1; not specified
		bar_left = *sptr++;   //if bar_left == -1; not specified
		bar_right = *sptr++;  //if bar_right == -1; not specified
		printf("BARS bar_top=%d bat_bottom=%d left=%d right=%d\n", bar_top, bar_bottom, bar_left, bar_right);
	}
	return 1;

}

long CPlayerData::EventDump( void* context, short bytes, void* mesg )
{
	CPlayerData*  PlayerData = (CPlayerData*)context;
	const char* pMesage = (const char*)mesg;
	//printf( "Event MSG : %s\r\n", mesg );
	if ( pMesage == NULL || bytes == 0 ) return -1;

	if ( strstr( pMesage, "EndOfStream" ) )
	{
		IMediaEventSink *pSink = NULL;
		HRESULT hr = PlayerData->m_pGraph->QueryInterface(IID_IMediaEventSink, (void**)&pSink);
		if (SUCCEEDED(hr))
		{
			//IMediaEventSink::Notify
			pSink->Notify( WM_DEMUX_EVENT, 1, 0);
			pSink->Notify( 1, 0, 0 );
			SAFE_RELEASE(pSink);
		}
	}
	return 1;
}

CPlayerData::~CPlayerData()
{
	slog(("NATIVE Deallocating the CPlayerData object\r\n"));
#if (defined(_DEBUG) || defined(ADD_TO_ROT_IN_RELEASE))
	if (m_dwRegister)
		RemoveFromRot(m_dwRegister);
	m_dwRegister = 0;
#endif
	if (m_pGraph)
	{
		IMediaEventEx *pME = NULL;
		HRESULT hr = m_pGraph->QueryInterface(IID_IMediaEventEx, (void**)&pME);
		if (SUCCEEDED(hr))
		{
			pME->SetNotifyWindow(0, WM_DVD_EVENT, 0);
			SAFE_RELEASE(pME);
		}
		/*if (m_pLine21Decoder)
			m_pGraph->RemoveFilter(m_pLine21Decoder);
		if (m_pAudioRenderer)
			m_pGraph->RemoveFilter(m_pAudioRenderer);
		if (m_pVideoRenderer)
			m_pGraph->RemoveFilter(m_pVideoRenderer);
		if (m_pAudioPostProcessor)
			m_pGraph->RemoveFilter(m_pAudioPostProcessor);
		if (m_pVideoPostProcessor)
			m_pGraph->RemoveFilter(m_pVideoPostProcessor);
		if (m_pAudioDecoder)
			m_pGraph->RemoveFilter(m_pAudioDecoder);
		if (m_pVideoDecoder)
			m_pGraph->RemoveFilter(m_pVideoDecoder);
		if (m_pDeMux)
			m_pGraph->RemoveFilter(m_pDeMux);
		if (m_pSrcFilter)
			m_pGraph->RemoveFilter(m_pSrcFilter);

		/// Remove any remaining filters from the graph
		IEnumFilters *pEnum = NULL;
		HRESULT hr = m_pGraph->EnumFilters(&pEnum);
		if (SUCCEEDED(hr))
		{
			IBaseFilter *currFilt = NULL;
			while (pEnum->Next(1, &currFilt, NULL) == S_OK)
			{
				m_pGraph->RemoveFilter(currFilt);
				SAFE_RELEASE(currFilt);
			}
		}
		SAFE_RELEASE(pEnum);*/
	}
#ifdef ENABLE_SAGETV_WMDRM_SUPPORT
	if (prov)
	{
		prov->Release();
		delete prov;
		prov = NULL;
	}
#endif
	SAFE_RELEASE(m_pLine21Decoder);
	SAFE_RELEASE(m_pAudioRenderer);
	SAFE_RELEASE(m_pVmrAllocator);
	SAFE_RELEASE(m_pVideoRenderer);
	SAFE_RELEASE(m_pAudioPostProcessor);
	SAFE_RELEASE(m_pVideoPostProcessor);
	SAFE_RELEASE(m_pAudioDecoder);
	SAFE_RELEASE(m_pVideoDecoder);
	SAFE_RELEASE(m_pDeMux);
	SAFE_RELEASE(m_pSrcFilter);
	if (m_pGraph)
	{
		DestroyGraph(m_pGraph);
	}
	slog(("NATIVE Finished destroying the playback graph\r\n"));
	if (m_pGraph)
	{
		ULONG rCount = m_pGraph->Release();
		if (rCount)
		{
			slog(("Graph ref Count=%d\r\n", rCount));
		}
	}
	if (m_pPluginHandler)
	{
		slog(("Calling GraphDestroyed for GraphPlugins\r\n"));
		m_pPluginHandler->GraphDestroyed();
		slog(("Done calling GraphDestroyed for GraphPlugins. Destroying the plugin object now.\r\n"));
		delete m_pPluginHandler;
		slog(("Done destroying the GraphPlugin object\r\n"));
	}
	//SAFE_RELEASE(m_pGraph);
	// IMPORTANT: DEALLOCATE the VMRAllocator LAST otherwise the pGraph deallocate crashes the system
/*	if (m_pVmrAllocator)
	{
		m_pVmrAllocator->Release();
		m_pVmrAllocator = NULL;
	}*/
}
void CPlayerData::SetGraph(IGraphBuilder* pGraph)
{
	m_pGraph = pGraph;
	m_pGraph->AddRef();
	// Add our graph to the running object table, which will allow
	// the GraphEdit application to "spy" on our graph
#if (defined(_DEBUG) || defined(ADD_TO_ROT_IN_RELEASE))
	AddToRot(m_pGraph, &m_dwRegister);
#endif

}

#define FREY_ADD_FILTER(MemberName, StringName) 		if (!m_pGraph){	return E_FAIL;}\
	HRESULT hr = m_pGraph->AddFilter(pFilter, StringName);\
	if (SUCCEEDED(hr))\
	{\
		MemberName = pFilter;\
		MemberName->AddRef();\
	}\
	return hr

HRESULT CPlayerData::SetVideoDecoder(IBaseFilter* pFilter)
{
	FREY_ADD_FILTER(m_pVideoDecoder, L"Video Decoder");
}
HRESULT CPlayerData::SetAudioDecoder(IBaseFilter* pFilter)
{
	FREY_ADD_FILTER(m_pAudioDecoder, L"Audio Decoder");
}
HRESULT CPlayerData::SetVideoPostProcessor(IBaseFilter* pFilter)
{
	FREY_ADD_FILTER(m_pVideoPostProcessor, L"Video PP");
}
HRESULT CPlayerData::SetAudioPostProcessor(IBaseFilter* pFilter)
{
	FREY_ADD_FILTER(m_pAudioPostProcessor, L"Audio PP");
}
HRESULT CPlayerData::SetVideoRenderer(IBaseFilter* pFilter)
{
	FREY_ADD_FILTER(m_pVideoRenderer, L"VidRend");
}
HRESULT CPlayerData::SetAudioRenderer(IBaseFilter* pFilter)
{
	FREY_ADD_FILTER(m_pAudioRenderer, L"AudRend");
}
HRESULT CPlayerData::SetTimeshiftingProperties(BOOL timeshift, LONGLONG circBuffSize)
{
	m_bTimeshifted = timeshift;
	m_llCircularBufferSize = MAX(0, circBuffSize);
	return S_OK;
}
BOOL CPlayerData::RequiresCustomSourceFilter()
{
	return (m_bTimeshifted || m_llCircularBufferSize);
}
HRESULT CPlayerData::SetSourceFilename(const WCHAR** pwFilename, DWORD dwNumFiles, const char* szHostname)
{
	if (!pwFilename) return E_POINTER;
	HRESULT hr;
	AM_MEDIA_TYPE mt;
	AM_MEDIA_TYPE* pmt = NULL;
	BOOL switchFile = FALSE;
	BOOL requiredSageTVSource = TRUE;
	m_requiredWMSource = FALSE;
	BOOL requiredDShowSource = FALSE;
	BOOL usedSageTVSource = TRUE;
	BOOL allowStreamDemux = GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DirectShow",
		"EnableSageTVStreamDemux", 1);
	BOOL fileNeedsLoading = TRUE;
	if (m_pSrcFilter)
	{
		switchFile = TRUE;
		// File switch while loaded
		IPin* asyncOutPin = FindPinByName(m_pSrcFilter, "Output", PINDIR_OUTPUT);
		if (asyncOutPin)
		{
			asyncOutPin->ConnectionMediaType(&mt);
			SAFE_RELEASE(asyncOutPin);
			pmt = &mt;
		}
	}
	else
	{
		if (GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DirectShow",
			"EnableStandardFilePlayback", 1) && !RequiresCustomSourceFilter())
		{
			if (szHostname && strlen(szHostname))
			{
				// It's not a local file, but check if it's accessible, in which case just playback through
				// the filesystem. This COULD be wrong if the same file is on both systems but different,
				// although that's just a weird case so we should be OK.
				WIN32_FIND_DATAW FindFileData;
				HANDLE hFind;
				hFind = FindFirstFileW(pwFilename[0], &FindFileData);
				if (hFind != INVALID_HANDLE_VALUE)
				{
					requiredSageTVSource = FALSE;
					FindClose(hFind);
				}
			}
			else
				requiredSageTVSource = FALSE;
		}
		if (dwNumFiles > 1)
			requiredSageTVSource = TRUE;

		// We shouldn't use NetShowSource under Vista
		if (!_wcsnicmp(pwFilename[0], L"mms://", 6))
		{
			hr = CoCreateInstance(CLSID_NetShowSource, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&m_pSrcFilter);
			if (FAILED(hr)) return hr;
			hr = m_pGraph->AddFilter(m_pSrcFilter, pwFilename[0]);
			if (FAILED(hr)) return hr;
			usedSageTVSource = FALSE;
		}
        else if (requiredSageTVSource)
		{
			//hr = CoCreateInstance(CLSID_SharedAsyncFile, NULL, CLSCTX_INPROC_SERVER,
			//	IID_IBaseFilter, (void**)&m_pSrcFilter);
			//ZQ. it's a new ShareAysncFile filter supporting vitual file (BD files)
			hr = CoCreateInstance(CLSID_SharedFileSource, NULL, CLSCTX_INPROC_SERVER, 
				IID_IBaseFilter, (void**)&m_pSrcFilter);
			if (FAILED(hr)) return hr;
			hr = m_pGraph->AddFilter(m_pSrcFilter, pwFilename[0]);
			if (FAILED(hr)) return hr;
		}
		else
		{
			slog(("Using the default source filter\r\n"));
			// Check the file extension
			int wlen = wcslen(pwFilename[0]);
			if (wlen > 4)
			{
				if (!_wcsicmp(&(pwFilename[0])[wlen - 4], L".wmv") ||
					!_wcsicmp(&(pwFilename[0])[wlen - 4], L".wma") ||
					!_wcsicmp(&(pwFilename[0])[wlen - 4], L".asf"))
				{
					m_requiredWMSource = TRUE;
				}
				if (!_wcsicmp(&(pwFilename[0])[wlen - 4], L".wtv"))
				{
					requiredDShowSource = TRUE;
				}
				if (wlen > 7 && !_wcsicmp(&(pwFilename[0])[wlen - 7], L".dvr-ms"))
				{
					requiredDShowSource = TRUE;
				}

				if (wlen > 5 && (!_wcsicmp(&(pwFilename[0])[wlen - 5], L".tivo") ||
					(!allowStreamDemux && (!_wcsicmp(&(pwFilename[0])[wlen - 5], L".flac") ||
					!_wcsicmp(&(pwFilename[0])[wlen - 4], L".mkv")))))
				{
					requiredDShowSource = TRUE;
				}
			}
			if (m_requiredWMSource)
			{
				hr = CoCreateInstance(CLSID_WMAsfReader, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&m_pSrcFilter);
				if (FAILED(hr)) return hr;
				hr = m_pGraph->AddFilter(m_pSrcFilter, pwFilename[0]);
				if (FAILED(hr)) return hr;
				usedSageTVSource = FALSE;
#ifdef ENABLE_SAGETV_WMDRM_SUPPORT
				// Prepare for potential DRM playback
				AddKeyProvider();
#endif
			}
			else if (requiredDShowSource)
			{
				usedSageTVSource = FALSE;
				hr = m_pGraph->AddSourceFilter(pwFilename[0], pwFilename[0], &m_pSrcFilter);
				if (FAILED(hr)) return hr;
				fileNeedsLoading = FALSE;
			}
			else
			{
				// Stick with our source filter unless we have to use the WM source. It's needed
				// for fast file switching.
				//hr = CoCreateInstance(CLSID_SharedAsyncFile, NULL, CLSCTX_INPROC_SERVER,
				//	IID_IBaseFilter, (void**)&m_pSrcFilter);
				//ZQ. it's a new ShareAysncFile filter supporting vitual file (BD files)
				hr = CoCreateInstance(CLSID_SharedFileSource, NULL, CLSCTX_INPROC_SERVER, 
					IID_IBaseFilter, (void**)&m_pSrcFilter);
				if (FAILED(hr)) return hr;
				hr = m_pGraph->AddFilter(m_pSrcFilter, pwFilename[0]);
				if (FAILED(hr)) return hr;
			}
		}
	}

	// Set the file active before it's loaded!
	if (usedSageTVSource)
	{
		ISharedAsyncFile* pAsync = NULL;
		m_pSrcFilter->QueryInterface(IID_ISharedAsyncFile, (void**)&pAsync);

		// Setup the circular file
		pAsync->put_CircularSize((long) m_llCircularBufferSize);
		// This pointer is never dereferenced, it just needs to be non-zero
		pAsync->put_ShareInfo((ShareInfo*)m_bTimeshifted);

		// Don't bork the optimization for not going through the server for client playback
		if (requiredSageTVSource && szHostname && strlen(szHostname))
			pAsync->put_RemoteHostname(szHostname);
		else
			pAsync->put_RemoteHostname(NULL);
		pAsync->Release();

	}

	if (fileNeedsLoading)
	{
		slog(("NATIVE Loading the source file\r\n"));
		IFileSourceFilter* pfsf;
		m_pSrcFilter->QueryInterface(IID_IFileSourceFilter, (void**)&pfsf);
		if (dwNumFiles == 1)
		{
			hr = pfsf->Load(pwFilename[0], pmt);
		}
		else
		{
			ISharedAsyncFile* pAsync = NULL;
			m_pSrcFilter->QueryInterface(IID_ISharedAsyncFile, (void**)&pAsync);
			for (DWORD i = 0; i < dwNumFiles; i++)
			{
				hr = pAsync->LoadEx(i, pwFilename[i], pmt);
				if (FAILED(hr)) break;
			}
			pAsync->Release();
		}
		pfsf->Release();
		if (FAILED(hr)) return hr;
	}

	// Stop now and don't do the demux work if this was a switch
	if (switchFile)
		return S_OK;

	// Add our demux to the graph if its an MPEG2 file
	IPin* asyncOutPin = FindPinByName(m_pSrcFilter, NULL, PINDIR_OUTPUT);
	if (!asyncOutPin) return E_FAIL;
	// Check to make sure it's MPEG-2
	IEnumMediaTypes *mtEnum = NULL;
	asyncOutPin->EnumMediaTypes(&mtEnum);
	AM_MEDIA_TYPE *pMT = NULL;
	BOOL mpegOK = 0;
	BOOL mcMpegOK = 0;
	BOOL aviOK = 0;
	BOOL mp3OK = 0;
	int numOutputs = 0;
	while (!mpegOK && !mcMpegOK && S_OK == mtEnum->Next(1, &pMT, NULL))
	{
		if (pMT->majortype == MEDIATYPE_Stream)
		{
			if (pMT->subtype == MEDIASUBTYPE_MPEG2_PROGRAM)
				mpegOK = 1;
			else if (pMT->subtype == MEDIASUBTYPE_MPEG2_TRANSPORT)
				mcMpegOK = 1;
			else if (pMT->subtype == MEDIASUBTYPE_Avi)
				aviOK = 1;
			else if (pMT->subtype == MEDIASUBTYPE_MPEG1Audio)
				mp3OK = 1;
		}
		DeleteMediaType(pMT);
		numOutputs++;
	}
	mtEnum->Release();
	asyncOutPin->Release();

	if ((mpegOK || mcMpegOK) && usedSageTVSource)
	{
		slog(("NATIVE Setting up the MPEG demux\r\n"));
		// Its MPEG2, add our demux to the graph
		// This was totally wrong, the MC Demux doesn't even support Transport Stream
/*		if (mcMpegOK)
		{
			hr = CoCreateInstance(CLSID_MainConceptMPEGSplitter, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&m_pDeMux);
			if (FAILED(hr)) return hr;
			hr = m_pGraph->AddFilter(m_pDeMux, L"MC MPEG2 Demultiplexer");
			if (FAILED(hr)) return hr;
		}
		else*/
		{
			// Check for an override on the demux filter
			char guidHolder[256];
			HKEY myKey;
			DWORD readType;
			DWORD hsize = sizeof(guidHolder);
			m_pDeMux = NULL;
			if (RegCreateKeyEx(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DirectShow", 0, NULL, REG_OPTION_NON_VOLATILE,
				KEY_ALL_ACCESS, NULL, &myKey, &readType) == ERROR_SUCCESS)
			{
				if (RegQueryValueEx(myKey, "CustomDemuxGuid", 0, &readType, (LPBYTE) guidHolder, &hsize) == ERROR_SUCCESS)
				{
					if (strlen(guidHolder))
					{
						slog(("Creating custom demux filter %s\r\n", guidHolder));
						hr = FindFilterByName(&m_pDeMux, CLSID_LegacyAmFilterCategory, guidHolder, 0, NULL);
						HTESTPRINT(hr);
					}
				}
				RegCloseKey(myKey);
			}

			if (m_pDeMux)
			{
				slog(("Using custom demux\r\n"));
				hr = m_pGraph->AddFilter(m_pDeMux, L"Custom Demultiplexer");
				if (FAILED(hr)) return hr;
			}
			else
			{
				hr = CoCreateInstance(CLSID_MpegDeMux, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&m_pDeMux);
				if (FAILED(hr)) return hr;
				hr = m_pGraph->AddFilter(m_pDeMux, L"SageTV MPEG2 Demultiplexer");
				if (FAILED(hr)) return hr;
				IMpegDeMux* piDeMux = NULL;
				hr = m_pDeMux->QueryInterface(IID_IMpegDeMux, (void**)&piDeMux);
				if (FAILED(hr)) return hr;
				piDeMux->SetRemuxOptimize(m_bRemuxOptimize);
				piDeMux->SetAFDDump( AFDFormatDump, this);
				piDeMux->SetCCDump( CC_DUMP, this );
				//ZQ EVENT
				piDeMux->SetEventDump( EventDump, this );
				piDeMux->Release();
			}
		}
		hr = ConnectPins(m_pGraph, m_pSrcFilter, "Output", m_pDeMux, "Input", TRUE);
		if (FAILED(hr)) return hr;
	}
	else if (aviOK)
	{
		slog(("NATIVE setting up the AVI demux\r\n"));
		hr = CoCreateInstance(CLSID_AviSplitter, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void**)&m_pDeMux);
		if (FAILED(hr)) return hr;
		hr = m_pGraph->AddFilter(m_pDeMux, L"AVI Demultiplexer");
		if (FAILED(hr)) return hr;
		IPin* outPin = FindPinByName(m_pSrcFilter, "Output", PINDIR_OUTPUT);
		if (outPin == 0)
			return E_FAIL;
		IPin* inPin = FindUnconnectedPin(m_pDeMux, PINDIR_INPUT, FALSE);
		if (!inPin)
		{
			SAFE_RELEASE(outPin);
			return E_FAIL;
		}
		hr = m_pGraph->ConnectDirect(outPin, inPin, NULL);
		SAFE_RELEASE(inPin);
		SAFE_RELEASE(outPin);
		if (FAILED(hr)) return hr;
	}
	else if (allowStreamDemux && usedSageTVSource && (!mp3OK || numOutputs > 1))
	{
		slog(("NATIVE setting up the SageTV Stream Demuxer\r\n"));
		hr = CoCreateInstance(CLSID_SDeMux, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void**)&m_pDeMux);
		if (FAILED(hr)) return hr;
		hr = m_pGraph->AddFilter(m_pDeMux, L"SageTV Stream Demux");
		if (FAILED(hr)) return hr;

		//setup event handle
		ISDeMux* piDeMux = NULL;
		hr = m_pDeMux->QueryInterface(IID_ISDeMux, (void**)&piDeMux);
		if ( !FAILED(hr) ) 
		{
			piDeMux->SetEventDump( EventDump, this );
			piDeMux->Release();
		}

		hr = ConnectPins(m_pGraph, m_pSrcFilter, "Output", m_pDeMux, "Input", TRUE);
		if (FAILED(hr)) return hr;
	}

	if (m_pPluginHandler)
	{
		slog(("Calling GraphCreated for the PluginHandler\r\n"));
		m_pPluginHandler->GraphCreated(m_pGraph, pwFilename[0]);
		slog(("Done calling GraphCreated for the PluginHandler\r\n"));
	}
	return S_OK;
}

HRESULT CPlayerData::SetDXVAParameters(DWORD dwMpegMode, DWORD dwDeinterlace)
{
	m_dwDXVAMPEGMode = dwMpegMode;
	m_dwDXVADeinterlaceMode = dwDeinterlace;
	return S_OK;
}
void CPlayerData::SetVMR9Allocator(CVMRAllocator* pVmrAllocator)
{
	m_pVmrAllocator = pVmrAllocator;
}

HRESULT CPlayerData::RenderNextInChain(IPin** sourcePin, IBaseFilter* nextFilter, const GUID* pinCat, BOOL directConnect)
{
	if (*sourcePin && nextFilter)
	{
		IPin* destPin = FindPin(nextFilter, PINDIR_INPUT, pinCat, NULL);
		if (!destPin)
		{
			// Sometimes the filters don't specify media types yet, so take the unconnected input
			destPin = FindUnconnectedPin(nextFilter, PINDIR_INPUT);
		}
		if (destPin)
		{
			// Try direct connection first, if it fails try intelligent connect. According to the MS docs
			// this should happen anyways...but it doesn't!
			HRESULT hr = m_pGraph->ConnectDirect(*sourcePin, destPin, NULL);
			if (FAILED(hr))
			{
				hr = m_pGraph->Connect(*sourcePin, destPin);
			}
			destPin->Release();
			if (SUCCEEDED(hr))
			{
				SAFE_RELEASE((*sourcePin));
				*sourcePin = FindPin(nextFilter, PINDIR_OUTPUT, pinCat, NULL);
				return hr;
			}
			else
				return hr;
		}
		else
			return E_FAIL;
	}
	return S_OK;
}

IPin* CPlayerData::GetSourceVideoPin()
{
	return FindPin(m_pDeMux, PINDIR_OUTPUT, &MEDIATYPE_Video, NULL);
}
IPin* CPlayerData::GetSourceAudioPin(int audioStreamNum)
{
	// Account for the special case of DVDLPCM audio
	IPin* rv = NULL;
	int aStreamNum = 0;
	int pinNum = 0;
	if (!m_pDeMux) return NULL;
	while (1)
	{
		HRESULT hr = GetPin(m_pDeMux, PINDIR_OUTPUT, pinNum, &rv);
		if (FAILED(hr))
			return NULL;
		else
		{
			pinNum++;
			if (DoesPinSupportMediaType(rv, &MEDIATYPE_Audio, NULL) ||
				DoesPinSupportMediaType(rv, &MEDIATYPE_MPEG2_PES, &MEDIASUBTYPE_DVD_LPCM_AUDIO) ||
				DoesPinSupportMediaType(rv, &MEDIATYPE_MPEG2_PES, &CLSID_DTS_AUDIO))
			{
				aStreamNum++;
				if (aStreamNum == audioStreamNum)
					return rv;
			}
			SAFE_RELEASE(rv);
		}
	}
	return NULL;
}

IPin* CPlayerData::GetLine21OutputPin()
{
	// Get the decoder filter, since it may not be specified
	IBaseFilter* pVideoDecoder = NULL;
	//IBaseFilter* pVideoRenderer = NULL;
	if (m_pVideoDecoder)
	{
		return FindPin(m_pVideoDecoder, PINDIR_OUTPUT, &MEDIATYPE_AUXLine21Data, NULL);
	}
	else
	{
		IPin* rv = NULL;
		IPin* vSrc = GetSourceVideoPin();
		if (vSrc)
		{
			IPin* vNext = NULL;
			HRESULT hr = vSrc->ConnectedTo(&vNext);
			vSrc->Release();
			if (SUCCEEDED(hr))
			{
				PIN_INFO pInfo;
				hr = vNext->QueryPinInfo(&pInfo);
				rv = FindPin(pInfo.pFilter, PINDIR_OUTPUT, &MEDIATYPE_AUXLine21Data, NULL);
				pInfo.pFilter->Release();
				vNext->Release();
			}
		}
		return rv;
	}

}

HRESULT CPlayerData::CloneAndAddFilter(IBaseFilter* pFilter, IBaseFilter** pRV)
{
	// Clone the filter and make another one to use
	CLSID myCLSID;
	HRESULT hr = pFilter->GetClassID(&myCLSID);
	if (FAILED(hr)) return hr;
	hr = CoCreateInstance(myCLSID, NULL, CLSCTX_INPROC_SERVER, IID_IBaseFilter,
		(void**)pRV);
	if (FAILED(hr)) return hr;
	hr = m_pGraph->AddFilter(*pRV, NULL);
	return hr;
}

HRESULT CPlayerData::RenderAudio()
{
	int audioStreamNum = 1;
	HRESULT hr = S_OK;
	int bestStreamNum = -1;
	int bestStreamHasAC3DTS = 0;
	int bestStreamHasHDAudio = 0;
	int bestStreamChannels = 0;
	while (1)
	{
		IPin* audioSource = GetSourceAudioPin(audioStreamNum);
		if (audioSource)
		{
			IEnumMediaTypes *mtEnum = NULL;
			hr = audioSource->EnumMediaTypes(&mtEnum);
			HTESTPRINT(hr);
			AM_MEDIA_TYPE *pMT = NULL;
			while (S_OK == mtEnum->Next(1, &pMT, NULL))
			{
				int currHasHDAudio = (pMT->subtype == MEDIASUBTYPE_DOLBY_TRUEHD) ||
					(pMT->subtype == MEDIASUBTYPE_DTS_HD) ||
					(pMT->subtype == CLSID_DTS_MASTER_AUDIO);
				int currChans = 0;
				int currFreq = 0;
				if (pMT->formattype == FORMAT_WaveFormatEx)
				{
					currChans = ((WAVEFORMATEX*)pMT->pbFormat)->nChannels;
					currFreq = ((WAVEFORMATEX*)pMT->pbFormat)->nSamplesPerSec;
				}
				if ((!bestStreamHasHDAudio && currHasHDAudio) || (bestStreamHasHDAudio && currHasHDAudio && (currChans > bestStreamChannels)))
				{
					bestStreamNum = audioStreamNum;
					bestStreamHasHDAudio = 1;
					bestStreamChannels = currChans;
				}
				else if (!bestStreamHasHDAudio)
				{
					int currHasAC3DTS = (pMT->subtype == MEDIASUBTYPE_DOLBY_AC3) ||
						(pMT->subtype == CLSID_DTS_AUDIO) || (pMT->subtype == MEDIASUBTYPE_DOLBY_DDPLUS);
					if (bestStreamNum == -1 || (currHasAC3DTS && (!bestStreamHasAC3DTS || (currChans > bestStreamChannels))))
					{
						bestStreamNum = audioStreamNum;
						bestStreamHasAC3DTS = currHasAC3DTS;
						bestStreamChannels = currChans;
					}
				}
				char* fmtStr = NULL;
				if (pMT->subtype == MEDIASUBTYPE_DOLBY_TRUEHD)
					fmtStr = "TrueHD";
				else if (pMT->subtype == MEDIASUBTYPE_DTS_HD)
					fmtStr = "DTSHD";
				else if (pMT->subtype == CLSID_DTS_MASTER_AUDIO)
					fmtStr = "DTSMA";
				else if (pMT->subtype == MEDIASUBTYPE_DOLBY_AC3)
					fmtStr = "AC3";
				else if (pMT->subtype == CLSID_DTS_AUDIO)
					fmtStr = "DTS";
				else if (pMT->subtype == MEDIASUBTYPE_DOLBY_DDPLUS)
					fmtStr = "DD+";
				else
					fmtStr = "Other";
				slog(("Audio Stream #%d format %s channels %d freq %d\r\n", audioStreamNum, fmtStr, currChans, currFreq));
				DeleteMediaType(pMT);
			}
			SAFE_RELEASE(mtEnum);
			SAFE_RELEASE(audioSource);
		}
		else
			break;
		audioStreamNum++;
	}
	if (bestStreamNum == -1)
		bestStreamNum = 1;
	return RenderAudio(bestStreamNum);
}

HRESULT CPlayerData::RenderAudio(int audioStreamNum)
{
	if (!m_pDeMux) return E_POINTER;
	slog(("NATIVE RenderAudio called stream=%d\r\n", audioStreamNum));
	IPin* audioSource = GetSourceAudioPin(audioStreamNum);
	if (!audioSource)
	{
		if (audioStreamNum <= 1)
		{
			slog(("AUDIO not rendered. No source pin found!\r\n"));
		}
		return VFW_S_AUDIO_NOT_RENDERED;
	}

	HRESULT hr = S_OK;

	if (m_bUnifiedDecoder && m_pVideoDecoder)
	{
		// We just connect to the video decoder's audio input pin instead
		if (audioStreamNum == 1)
		{
			slog(("NATIVE Unified decoder audio\r\n"));
			hr = RenderNextInChain(&audioSource, m_pVideoDecoder, &MEDIATYPE_Audio);
		}
		SAFE_RELEASE(audioSource);
		return hr;
	}

	// If we're rendering a different audio stream than what was already rendered, then first clone
	// the decoder & renderer filters and then remove the ones that were already there so the new ones will
	// be used instead.
	bool alreadyConnected = false;
	if (m_pAudioDecoder || m_pAudioRenderer)
	{
		IBaseFilter* testFilter = (m_pAudioDecoder) ? m_pAudioDecoder : m_pAudioRenderer;
		IPin* testPin = FindUnconnectedPin(testFilter, PINDIR_INPUT, TRUE);
		if (testPin)
		{
			alreadyConnected = true;
			SAFE_RELEASE(testPin);
		}

		if (alreadyConnected)
		{
			if (m_pAudioDecoder)
			{
				CLSID myCLSID;
				hr = m_pAudioDecoder->GetClassID(&myCLSID);
				if (FAILED(hr)) return hr;
				hr = m_pGraph->RemoveFilter(m_pAudioDecoder);
				SAFE_RELEASE(m_pAudioDecoder);
				if (FAILED(hr)) return hr;
				hr = CoCreateInstance(myCLSID, NULL, CLSCTX_INPROC_SERVER, IID_IBaseFilter,
					(void**)&m_pAudioDecoder);
				if (FAILED(hr)) return hr;
				hr = m_pGraph->AddFilter(m_pAudioDecoder, NULL);
			}
			if (m_pAudioRenderer)
			{
				CLSID myCLSID;
				hr = m_pAudioRenderer->GetClassID(&myCLSID);
				if (FAILED(hr)) return hr;
				hr = m_pGraph->RemoveFilter(m_pAudioRenderer);
				SAFE_RELEASE(m_pAudioRenderer);
				if (FAILED(hr)) return hr;
				hr = CoCreateInstance(myCLSID, NULL, CLSCTX_INPROC_SERVER, IID_IBaseFilter,
					(void**)&m_pAudioRenderer);
				if (FAILED(hr)) return hr;
				hr = m_pGraph->AddFilter(m_pAudioRenderer, NULL);
			}
		}
	}
	// Always do this check to cleanup any other auto-inserted filters. There's a case where if we specify the audio renderer
	// but not the decoder, the above code would only cleanup the renderer, this'll clean up what's left
	//else
	{
		// Find the audio decoder & renderer by looking at the graph
		IBaseFilter* tempAudioDec = NULL;
		IBaseFilter* tempAudioRend = NULL;
		for (int i = 0; i < 10; i++)
		{
			hr = GetAudioRendererFilter(i, &tempAudioDec, &tempAudioRend);
			if (tempAudioDec || tempAudioRend)
			{
				slog(("Found prio audio decoder/renderer in graph and removing it to switch audio tracks.\r\n"));
				if (tempAudioDec)
					hr = m_pGraph->RemoveFilter(tempAudioDec);
				if (tempAudioRend)
					hr = m_pGraph->RemoveFilter(tempAudioRend);
				SAFE_RELEASE(tempAudioDec);
				SAFE_RELEASE(tempAudioRend);
				break;
			}
		}
	}


	// Check for an audio filter to connect to
	slog(("NATIVE RenderAudio step 1\r\n"));
	// Check if they have the SageTV Audio Decoder selected and if the audio type is AC3. If it is
	// then do NOT use the SageTV Audio Decoder and instead use AC3Filter.
	if (m_pAudioDecoder)
	{
		// Get the GUID and see if it's ours
		CLSID audDecCLSID;
		hr = m_pAudioDecoder->GetClassID(&audDecCLSID);
		/*
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
		if (IsEqualGUID(audDecCLSID, CLSID_ELMPGLayer2AudioDecoder))
		{
			BOOL isAC3Audio = FALSE;
			IEnumMediaTypes *mtEnum = NULL;
			hr = audioSource->EnumMediaTypes(&mtEnum);
			HTESTPRINT(hr);
			AM_MEDIA_TYPE *pMT = NULL;
			while (S_OK == mtEnum->Next(1, &pMT, NULL))
			{
				if ((pMT->majortype == MEDIATYPE_Audio && pMT->subtype == MEDIASUBTYPE_DOLBY_AC3) ||
					(pMT->majortype == MEDIATYPE_MPEG2_PES && pMT->subtype == MEDIASUBTYPE_DVD_LPCM_AUDIO) ||
					(pMT->majortype == MEDIATYPE_MPEG2_PES && pMT->subtype == CLSID_DTS_AUDIO))
				{
					isAC3Audio = TRUE;
				}
				DeleteMediaType(pMT);
			}
			SAFE_RELEASE(mtEnum);
			if (isAC3Audio)
			{
				slog(("Setting up AC3 audio stream playback\r\n"));
				// First remove the SageTV MPEG Audio Decoder
				hr = m_pGraph->RemoveFilter(m_pAudioDecoder);
				HTESTPRINT(hr);
				SAFE_RELEASE(m_pAudioDecoder);
				hr = CoCreateInstance(CLSID_AC3_Filter, NULL, CLSCTX_INPROC_SERVER, IID_IBaseFilter,
					(void**)&m_pAudioDecoder);
				if (FAILED(hr)) return hr;
				hr = m_pGraph->AddFilter(m_pAudioDecoder, L"AC3 Decoder");
				if (FAILED(hr)) return hr;
			}
		}
		 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
		 */
	}

	hr = RenderNextInChain(&audioSource, m_pAudioDecoder, &MEDIATYPE_Audio, TRUE);
	if (FAILED(hr))
		elog(("Unable to use the specified audio decoder filter\r\n"));
	// Check for a postprocessor & the need to render further
	slog(("NATIVE RenderAudio step 2\r\n"));
	hr = RenderNextInChain(&audioSource, m_pAudioPostProcessor, &MEDIATYPE_Audio);
	if (FAILED(hr))
		elog(("Unable to use the specified audio post processor filter\r\n"));
	// Check for a renderer filter & the need to render further
	slog(("NATIVE RenderAudio step 3\r\n"));
	hr = RenderNextInChain(&audioSource, m_pAudioRenderer, &MEDIATYPE_Audio);
	if (FAILED(hr))
		elog(("Unable to use the specified audio renderer filter\r\n"));

	if (audioSource)
	{
		slog(("NATIVE RenderAudio step 4\r\n"));
		hr = m_pGraph->Render(audioSource);
		audioSource->Release();
	}
	return hr;
}

void PrintMTInfo(AM_MEDIA_TYPE* pmtIn)
{
	/* Dump the GUID types */
	slog(("M type %hs  S type %hs F type %hs\r\n", GuidNames[pmtIn->majortype], GuidNames[pmtIn->subtype], GuidNames[pmtIn->formattype]));

	if (IsEqualGUID(pmtIn->formattype, FORMAT_VideoInfo2))
	{
		DWORD dwInterlaceFlags = ((VIDEOINFOHEADER2*)pmtIn->pbFormat)->dwInterlaceFlags;
		if (dwInterlaceFlags & AMINTERLACE_IsInterlaced)
		{
			slog(("Interlaced video\r\n"));
			if (dwInterlaceFlags & AMINTERLACE_1FieldPerSample)
				slog(("One field per sample\r\n"));
			else
				slog(("Both fields per sample\r\n"));
			if (dwInterlaceFlags & AMINTERLACE_Field1First)
				slog(("Field 1 first\r\n"));
			else
				slog(("Field 2 first\r\n"));
			switch (dwInterlaceFlags & AMINTERLACE_FieldPatternMask)
			{
				case AMINTERLACE_FieldPatField1Only:
					slog(("Stream never contains a Field 2\r\n"));
					break;
				case AMINTERLACE_FieldPatField2Only:
					slog(("Stream never contains a Field 1\r\n"));
					break;
				case AMINTERLACE_FieldPatBothRegular:
					slog(("One Field 2 for every Field 1\r\n"));
					break;
				case AMINTERLACE_FieldPatBothIrregular:
					slog(("Random pattern of Field 1 and Field 2\r\n"));
					break;
			}
			switch (dwInterlaceFlags & AMINTERLACE_DisplayModeMask)
			{
				case AMINTERLACE_DisplayModeBobOnly:
					slog(("Bob display mode only.\r\n"));
					break;
				case AMINTERLACE_DisplayModeWeaveOnly:
					slog(("Weave display mode only.\r\n"));
					break;
				case AMINTERLACE_DisplayModeBobOrWeave:
					slog(("Either bob or weave mode.\r\n"));
					break;
			}
		}
		else
			slog(("Progressive video\r\n"));
	}
}

void PrintPinInfo(IPin* pPin)
{
	AM_MEDIA_TYPE currMT;
	HRESULT hr = pPin->ConnectionMediaType(&currMT);
	if (SUCCEEDED(hr))
	{
		PrintMTInfo(&currMT);
		FreeMediaType(currMT);
	}
	else
	{
		slog(("PIN is not connected yet!"));
		IEnumMediaTypes *mtEnum = NULL;
		hr = pPin->EnumMediaTypes(&mtEnum);
		HTESTPRINT(hr);
		AM_MEDIA_TYPE *pMT = NULL;
		while (S_OK == mtEnum->Next(1, &pMT, NULL))
		{
			PrintMTInfo(pMT);
			DeleteMediaType(pMT);
		}
		SAFE_RELEASE(mtEnum);
	}
}

#define IsInterlaced(x) ((x) & AMINTERLACE_IsInterlaced)
#define IsSingleField(x) ((x) & AMINTERLACE_1FieldPerSample)
#define IsField1First(x) ((x) & AMINTERLACE_Field1First)

VMR9_SampleFormat ConvertInterlaceFlags(DWORD dwInterlaceFlags)
{
    if (IsInterlaced(dwInterlaceFlags)) {
        if (IsSingleField(dwInterlaceFlags)) {
            if (IsField1First(dwInterlaceFlags)) {
                return VMR9_SampleFieldSingleEven;
            }
            else {
                return VMR9_SampleFieldSingleOdd;
            }
        }
        else {
            if (IsField1First(dwInterlaceFlags)) {
                return VMR9_SampleFieldInterleavedEvenFirst;
             }
            else {
                return VMR9_SampleFieldInterleavedOddFirst;
            }
        }
    }
    else {
        return VMR9_SampleProgressiveFrame;  // Not interlaced.
    }
}

static BOOL IsH264Video( IPin* Source );
HRESULT CPlayerData::RenderVideo()
{
	if (!m_pDeMux) return E_POINTER;
	slog(("NATIVE RenderVideo called\r\n"));
	IPin* videoSource = GetSourceVideoPin();
	if (!videoSource) return E_FAIL;

	HRESULT hr = S_OK;

	// Check for a video filter to connect to
	slog(("NATIVE RenderVideo step 1\r\n"));
	hr = RenderNextInChain(&videoSource, m_pVideoDecoder, &MEDIATYPE_Video);
	if (FAILED(hr))
		elog(("Unable to use the specified video decoder filter\r\n"));
	// Check for a video postprocessing filter to connect to
	slog(("NATIVE RenderVideo step 2\r\n"));
	hr = RenderNextInChain(&videoSource, m_pVideoPostProcessor, &MEDIATYPE_Video);
	if (FAILED(hr))
		elog(("Unable to use the specified video post processing filter\r\n"));

	slog(("Video source pin information:\r\n"));
	PrintPinInfo(videoSource);
	// Don't manually connect to the rendering filter, do the render call instead. For some reason
	// the media type don't always work out right otherwise.
	if (videoSource)
	{
		slog(("NATIVE RenderVideo step 3\r\n"));
		hr = m_pGraph->Render(videoSource);
		HTESTPRINT(hr);
		videoSource->Release();
	}

	// If we have a rendering filter & DXVA modes specified then attempt to reconnect up the
	// renderer using the appropriate mode if it isn't already
	IPin* renderInput = NULL;
	IBaseFilter* pTempVidRend = GetVideoRendererFilter();
	if (m_pVideoRenderer)
	{
		// This is actually the connected pin
		renderInput = FindUnconnectedPin(m_pVideoRenderer, PINDIR_INPUT, TRUE);

		if (!renderInput)
		{
			// This means that the video renderer we loaded was NOT USED IN the graph, this is
			// a problem because it can cause the ActiveMovie window error.
			slog(("NATIVE Video renderer specified was unused...retrying connection\r\n"));
			// As an extra step; try to remove this other video renderer that was added to the graph
			// and then just try to connect directly to our video renderer; the automatic render may have messed up
			IBaseFilter* oldRenderer = m_pVideoRenderer;
			m_pVideoRenderer = NULL;
			SAFE_RELEASE(pTempVidRend);
			pTempVidRend = GetVideoRendererFilter();
			m_pVideoRenderer = oldRenderer;
			if (pTempVidRend)
			{
				renderInput = FindUnconnectedPin(pTempVidRend, PINDIR_INPUT, TRUE);
				if (renderInput)
				{
					IPin* pOutputForRender = NULL;
					renderInput->ConnectedTo(&pOutputForRender);
					SAFE_RELEASE(renderInput);
					if (pOutputForRender)
					{
						hr = m_pGraph->RemoveFilter(pTempVidRend);
						SAFE_RELEASE(pTempVidRend);
						HTESTPRINT(hr);
						if (SUCCEEDED(hr))
						{
							renderInput = FindUnconnectedPin(m_pVideoRenderer, PINDIR_INPUT, FALSE);
							if (renderInput)
							{
								hr = m_pGraph->Connect(pOutputForRender, renderInput);
								HTESTPRINT(hr);
								SAFE_RELEASE(renderInput);
								SAFE_RELEASE(pOutputForRender);
								if (FAILED(hr))
								{
									return hr;
								}
							}
							else
							{
								SAFE_RELEASE(pOutputForRender);
								return VFW_E_CANNOT_CONNECT;
							}
						}
						else
						{
							SAFE_RELEASE(pOutputForRender);
							return VFW_E_CANNOT_CONNECT;
						}
					}
					else
					{
						SAFE_RELEASE(pTempVidRend);
						return VFW_E_CANNOT_CONNECT;
					}
				}
				else
				{
					SAFE_RELEASE(pTempVidRend);
					return VFW_E_CANNOT_CONNECT;
				}
				SAFE_RELEASE(pTempVidRend);
			}
			else
				return VFW_E_CANNOT_CONNECT;
		}
	}
	else
		renderInput = FindUnconnectedPin(pTempVidRend, PINDIR_INPUT, TRUE);

	// See if we can configure deinterlacing on the VMR9
// NOTE NOTE NOTE VMR9 DEINTERLACING TEST CODE!!!!!!!!!!
	// According to the MS docs this should happen automatically....
	if (pTempVidRend)
	{
		IVMRDeinterlaceControl9* pVmrDeint = NULL;
		hr = pTempVidRend->QueryInterface(IID_IVMRDeinterlaceControl9, (void**)&pVmrDeint);
		if (SUCCEEDED(hr))
		{
			slog(("Setting up VMR9 deinterlacing\r\n"));
//			pVmrDeint->SetDeinterlacePrefs(DeinterlacePref9_NextBest);
			VMR9VideoDesc VideoDesc;
			DWORD dwNumModes = 0;
			AM_MEDIA_TYPE vmrConn;
			hr = renderInput->ConnectionMediaType(&vmrConn);
			HTESTPRINT(hr);
			// NOTE: There's a bug in the MainConcept MPEG2 decoder where it won't connect up
			// with the right deinterlacing mode for VMR9 the first time; but if you reconnect it then it's OK
			// Get the GUID and see if it's ours
			if (m_pVideoDecoder)
			{
				CLSID vidDecCLSID;
				hr = m_pVideoDecoder->GetClassID(&vidDecCLSID);
				/*
				 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
				if (IsEqualGUID(vidDecCLSID, CLSID_EM2VD))
				{
					slog(("Reconnecting up video renderer again for VMR9 deinterlacing...\r\n"));
					IPin* otherIn = NULL;
					renderInput->ConnectedTo(&otherIn);
					m_pGraph->Disconnect(otherIn);
					m_pGraph->Disconnect(renderInput);
					m_pGraph->ConnectDirect(otherIn, renderInput, NULL);
					otherIn->Release();
				}
				 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
				 */
			}
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

			pVmrDeint->Release();
		}
		else
		{
			IVMRDeinterlaceControl* pVmrDeint = NULL;
			hr = pTempVidRend->QueryInterface(IID_IVMRDeinterlaceControl, (void**)&pVmrDeint);
			if (SUCCEEDED(hr))
			{
				slog(("Setting up VMR7 deinterlacing\r\n"));
				pVmrDeint->SetDeinterlacePrefs(DeinterlacePref_NextBest);
				VMRVideoDesc VideoDesc;
				DWORD dwNumModes = 0;
				AM_MEDIA_TYPE vmrConn;
				hr = renderInput->ConnectionMediaType(&vmrConn);
				HTESTPRINT(hr);
				if (vmrConn.formattype == FORMAT_VideoInfo2)
				{
					VIDEOINFOHEADER2* vih2 = (VIDEOINFOHEADER2*) vmrConn.pbFormat;
					
					// Fill in the VideoDesc structure
					VideoDesc.dwSize = sizeof(VMRVideoDesc);
					VideoDesc.dwSampleWidth = vih2->bmiHeader.biWidth;
					VideoDesc.dwSampleHeight = vih2->bmiHeader.biHeight;
					VideoDesc.SingleFieldPerSample = IsInterlaced(vih2->dwInterlaceFlags);
					VideoDesc.dwFourCC = vih2->bmiHeader.biCompression;
					VideoDesc.InputSampleFreq.dwNumerator = 30000;
					VideoDesc.InputSampleFreq.dwDenominator = 1001;
					VideoDesc.OutputFrameFreq.dwNumerator = 60000;
					VideoDesc.OutputFrameFreq.dwDenominator = 1001;
					hr = pVmrDeint->GetNumberOfDeinterlaceModes(&VideoDesc, 
						&dwNumModes, NULL);
					HTESTPRINT(hr);
					if (hr == VFW_E_VMR_NOT_IN_MIXER_MODE)
					{
						// Reconfigure the VMR9 into mixing mode
						IVMRFilterConfig* pVmrConfig = NULL;
						hr = pTempVidRend->QueryInterface(IID_IVMRFilterConfig, (void**)&pVmrConfig);
						if (SUCCEEDED(hr))
						{
							slog(("Reconnecting VMR7 up in mixing mode...\r\n"));
							// We have to disconnect the input before we do this
							IPin* otherIn = NULL;
							renderInput->ConnectedTo(&otherIn);
							m_pGraph->Disconnect(otherIn);
							m_pGraph->Disconnect(renderInput);
							hr = pVmrConfig->SetNumberOfStreams(1);
							HTESTPRINT(hr);
							m_pGraph->ConnectDirect(otherIn, renderInput, NULL);
							otherIn->Release();
						}
						pVmrConfig->Release();
						hr = pVmrDeint->GetNumberOfDeinterlaceModes(&VideoDesc, 
							&dwNumModes, NULL);
					}
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
									VMRDeinterlaceCaps Caps;
									ZeroMemory(&Caps, sizeof(Caps));
									Caps.dwSize = sizeof(VMRDeinterlaceCaps);
									hr = pVmrDeint->GetDeinterlaceModeCaps(&(pModes[i]),
										&VideoDesc, &Caps);
									HTESTPRINT(hr);
									LPOLESTR psz;
									StringFromCLSID(pModes[i], &psz);
									char conv[64];
									WideCharToMultiByte(CP_ACP, 0, psz, -1, conv, 64, 0, 0);
									CoTaskMemFree(psz);
									if (SUCCEEDED(hr))
									{
										if (Caps.DeinterlaceTechnology == DeinterlaceTech_BOBLineReplicate)
											slog(("VMR7Deinterlacing Tech: BOBLineReplicate %s\r\n", conv));
										else if (Caps.DeinterlaceTechnology == DeinterlaceTech_BOBVerticalStretch)
											slog(("VMR7Deinterlacing Tech: BOBVerticalStretch %s\r\n", conv));
										else if (Caps.DeinterlaceTechnology == DeinterlaceTech_MedianFiltering)
											slog(("VMR7Deinterlacing Tech: MedianFiltering %s\r\n", conv));
										else if (Caps.DeinterlaceTechnology == DeinterlaceTech_EdgeFiltering)
											slog(("VMR7Deinterlacing Tech: EdgeFiltering %s\r\n", conv));
										else if (Caps.DeinterlaceTechnology == DeinterlaceTech_FieldAdaptive)
											slog(("VMR7Deinterlacing Tech: FieldAdaptive %s\r\n", conv));
										else if (Caps.DeinterlaceTechnology == DeinterlaceTech_PixelAdaptive)
											slog(("VMR7Deinterlacing Tech: PixelAdaptive %s\r\n", conv));
										else if (Caps.DeinterlaceTechnology == DeinterlaceTech_MotionVectorSteered)
											slog(("VMR7Deinterlacing Tech: MotionVectorSteered %s\r\n", conv));
										else 
											slog(("VMR7Deinterlacing Tech: Proprietary...\r\n"));
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

				pVmrDeint->Release();
			}
			else
			{
				// Check for EVR video processing settings
				IMFGetService* mfGetServ = NULL;
				hr = pTempVidRend->QueryInterface(__uuidof(IMFGetService), (void**)&mfGetServ);
				if (SUCCEEDED(hr))
				{
					slog(("Setting up video processing mode for EVR\r\n"));
					IMFVideoProcessor *pVidProcEvr = NULL;
					hr = mfGetServ->GetService(MR_VIDEO_MIXER_SERVICE , __uuidof(IMFVideoProcessor), (void**)&pVidProcEvr);
					HTESTPRINT(hr);
					if (SUCCEEDED(hr))
					{
						// First get the modes
						UINT numProcModes = 0;
						GUID* procModeGuids = NULL;
						int i;
						hr = pVidProcEvr->GetAvailableVideoProcessorModes(&numProcModes, &procModeGuids);
						HTESTPRINT(hr);
						DXVA2_VideoProcessorCaps dxvaProcCaps;
						for (i = 0; i < (int)numProcModes; i++)
						{
							LPOLESTR psz;
							StringFromCLSID(procModeGuids[i], &psz);
							char conv[64];
							WideCharToMultiByte(CP_ACP, 0, psz, -1, conv, 64, 0, 0);
							CoTaskMemFree(psz);
							slog(("EVR VideoProcMode %d - %s\r\n", i, conv));
							hr = pVidProcEvr->GetVideoProcessorCaps(&procModeGuids[i], &dxvaProcCaps);
							HTESTPRINT(hr);
						}
						if (numProcModes)
						{
							DWORD evrModeIndex = GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DirectShow",
								"EVRModeIndex", 0);
							if (evrModeIndex >= numProcModes)
								evrModeIndex = numProcModes - 1;
							hr = pVidProcEvr->SetVideoProcessorMode(&procModeGuids[evrModeIndex]);
							slog(("EVR set the video processing mode to index %d! hr=0x%x\r\n", evrModeIndex, hr));
							HTESTPRINT(hr);
						}
						CoTaskMemFree(procModeGuids);
						SAFE_RELEASE(pVidProcEvr);
					}
					SAFE_RELEASE(mfGetServ);
				}
			}
		}
		hr = S_OK;
	}
	SAFE_RELEASE(pTempVidRend);

	if (SUCCEEDED(hr) && renderInput && (m_dwDXVADeinterlaceMode || m_dwDXVAMPEGMode))
	{
		slog(("Trying to find DXVA media type\r\n"));
		renderInput->ConnectedTo(&videoSource);

		AM_MEDIA_TYPE currMT;
		hr = videoSource->ConnectionMediaType(&currMT);
		HTESTPRINT(hr);
		VIDEOINFOHEADER2 *pVih = reinterpret_cast<VIDEOINFOHEADER2*>(currMT.pbFormat);
		LPOLESTR psz;
		StringFromCLSID(currMT.subtype, &psz);
		char conv[64];
	    WideCharToMultiByte(CP_ACP, 0, psz, -1, conv, 64, 0, 0);
		CoTaskMemFree(psz);
		slog(("Current DXVA type mpeg=%hs deint=%d\r\n", GuidNames[currMT.subtype], pVih->dwInterlaceFlags));
		FreeMediaType(currMT);

		IEnumMediaTypes *mtEnum = NULL;
		hr = videoSource->EnumMediaTypes(&mtEnum);
		HTESTPRINT(hr);
		AM_MEDIA_TYPE *pMT = NULL;
		AM_MEDIA_TYPE *useMT = NULL;
		while (useMT == NULL && S_OK == mtEnum->Next(1, &pMT, NULL))
		{
			slog(("Enumerating media type on renderer pin\r\n"));
			slog(("M type %hs  S type %hs F type %hs\r\n", GuidNames[pMT->majortype], GuidNames[pMT->subtype], GuidNames[pMT->formattype]));
			if (pMT->formattype == FORMAT_VideoInfo2)
			{
				pVih = reinterpret_cast<VIDEOINFOHEADER2*>(pMT->pbFormat);
				StringFromCLSID(pMT->subtype, &psz);
				WideCharToMultiByte(CP_ACP, 0, psz, -1, conv, 64, 0, 0);
				CoTaskMemFree(psz);
				slog(("Testing DXVA type mpeg=%hs deint=%d\r\n", GuidNames[pMT->subtype], pVih->dwInterlaceFlags));
				/* Access VIDEOINFOHEADER members through pVih. */
				if (((m_dwDXVAMPEGMode && 
					((m_dwDXVAMPEGMode == sage_DShowTVPlayer_SAGE_DXVA_MPEGA && pMT->subtype == DXVA_ModeMPEG2_A) ||
					(m_dwDXVAMPEGMode == sage_DShowTVPlayer_SAGE_DXVA_MPEGB && pMT->subtype == DXVA_ModeMPEG2_B) ||
					(m_dwDXVAMPEGMode == sage_DShowTVPlayer_SAGE_DXVA_MPEGC && pMT->subtype == DXVA_ModeMPEG2_C) ||
						(m_dwDXVAMPEGMode == sage_DShowTVPlayer_SAGE_DXVA_MPEGD && pMT->subtype == DXVA_ModeMPEG2_D))) ||
						!m_dwDXVAMPEGMode) &&
						((m_dwDXVADeinterlaceMode && ((pVih->dwInterlaceFlags & AMINTERLACE_IsInterlaced) == AMINTERLACE_IsInterlaced) &&
						((m_dwDXVADeinterlaceMode == sage_DShowTVPlayer_SAGE_DEINTERLACE_BOB && 
							(pVih->dwInterlaceFlags & AMINTERLACE_DisplayModeBobOnly) == AMINTERLACE_DisplayModeBobOnly) ||
						(m_dwDXVADeinterlaceMode == sage_DShowTVPlayer_SAGE_DEINTERLACE_BOB && 
							(pVih->dwInterlaceFlags & AMINTERLACE_DisplayModeBobOrWeave) == AMINTERLACE_DisplayModeBobOrWeave) ||
						(m_dwDXVADeinterlaceMode == sage_DShowTVPlayer_SAGE_DEINTERLACE_WEAVE && 
							(pVih->dwInterlaceFlags & AMINTERLACE_DisplayModeWeaveOnly) == AMINTERLACE_DisplayModeWeaveOnly) ||
						(m_dwDXVADeinterlaceMode == sage_DShowTVPlayer_SAGE_DEINTERLACE_WEAVE && 
							(pVih->dwInterlaceFlags & AMINTERLACE_DisplayModeBobOrWeave) == AMINTERLACE_DisplayModeBobOrWeave) ||
						(m_dwDXVADeinterlaceMode == sage_DShowTVPlayer_SAGE_DEINTERLACE_BOBWEAVE && 
							(pVih->dwInterlaceFlags & AMINTERLACE_DisplayModeBobOrWeave) == AMINTERLACE_DisplayModeBobOrWeave))) ||
						!m_dwDXVADeinterlaceMode))
				{
					useMT = CreateMediaType(pMT);
					pVih = reinterpret_cast<VIDEOINFOHEADER2*>(useMT->pbFormat);
					if (m_dwDXVADeinterlaceMode == sage_DShowTVPlayer_SAGE_DEINTERLACE_BOB)
						pVih->dwInterlaceFlags = AMINTERLACE_IsInterlaced | AMINTERLACE_DisplayModeBobOnly;
					else if (m_dwDXVADeinterlaceMode == sage_DShowTVPlayer_SAGE_DEINTERLACE_WEAVE)
						pVih->dwInterlaceFlags = AMINTERLACE_IsInterlaced | AMINTERLACE_DisplayModeWeaveOnly;
					else if (m_dwDXVADeinterlaceMode == sage_DShowTVPlayer_SAGE_DEINTERLACE_BOBWEAVE)
						pVih->dwInterlaceFlags = AMINTERLACE_IsInterlaced | AMINTERLACE_DisplayModeBobOrWeave;
				}
			}
			DeleteMediaType(pMT);
		}
		SAFE_RELEASE(mtEnum);

		if (useMT)
		{
			m_pGraph->Disconnect(videoSource);
			m_pGraph->Disconnect(renderInput);

			pVih = reinterpret_cast<VIDEOINFOHEADER2*>(useMT->pbFormat);
			StringFromCLSID(useMT->subtype, &psz);
			WideCharToMultiByte(CP_ACP, 0, psz, -1, conv, 64, 0, 0);
			CoTaskMemFree(psz);
			slog(("About to connect with DXVA media type mpeg=%c deint=%d\r\n", conv[8], 
				pVih->dwInterlaceFlags));
			hr = m_pGraph->ConnectDirect(videoSource, renderInput, useMT);
			HTESTPRINT(hr);
			if (FAILED(hr))
			{
				// Couldn't render with specified DXVA type, just reconnect them back up
				hr = m_pGraph->Connect(videoSource, renderInput);
			}
			else
			{
				pVih = reinterpret_cast<VIDEOINFOHEADER2*>(useMT->pbFormat);
				StringFromCLSID(useMT->subtype, &psz);
				WideCharToMultiByte(CP_ACP, 0, psz, -1, conv, 64, 0, 0);
				CoTaskMemFree(psz);
				slog(("Successfully connected with DXVA media type mpeg=%c deint=%d\r\n", conv[8], 
					pVih->dwInterlaceFlags));
			}
			DeleteMediaType(useMT);
		}
		else
		{
			slog(("Unable to find specified DXVA media type\r\n"));
		}

		SAFE_RELEASE(videoSource);
	}
	SAFE_RELEASE(renderInput);

	PrintVideoRendererInfo();

	return hr;
}

HRESULT CPlayerData::RenderSource()
{
	// Just find the output pin on the source filter and render it
	slog(("NATIVE RenderSource called\r\n"));
	IPin* asyncOutPin = FindPinByName(m_pSrcFilter, NULL, PINDIR_OUTPUT);
	if (!asyncOutPin) return VFW_E_CANNOT_LOAD_SOURCE_FILTER;
	// NOTE: For MP3 files we add the MPEG-1 Stream Splitter filter ourself and render from there.
	// In testing it was found that if we don't do that, then intelligent connect will sometimes take awhile
	// to get things setup. However, that was worked around by NOT skipping the ID3 header information...which
	// leads to other problems. (although that's what the MS source filter does which is why it doesn't have that issue
	// in the test cases we were sent)
	BOOL isMP3File = FALSE;
	IEnumMediaTypes *mtEnum = NULL;
	HRESULT hr = asyncOutPin->EnumMediaTypes(&mtEnum);
	HTESTPRINT(hr);
	AM_MEDIA_TYPE *pMT = NULL;
	while (S_OK == mtEnum->Next(1, &pMT, NULL))
	{
		if (pMT->majortype == MEDIATYPE_Stream && pMT->subtype == MEDIASUBTYPE_MPEG1Audio)
		{
			isMP3File = TRUE;
		}
		DeleteMediaType(pMT);
	}
	SAFE_RELEASE(mtEnum);
	if (isMP3File)
	{
		CComPtr<IBaseFilter> pMpeg1Demux = NULL;
		hr = CoCreateInstance(CLSID_MPEG1Splitter, NULL, CLSCTX_INPROC_SERVER, IID_IBaseFilter,
			(void**)&(pMpeg1Demux.p));
		if (FAILED(hr)) return hr;
		hr = m_pGraph->AddFilter(pMpeg1Demux, L"MPEG-1 Stream Splitter");
		if (FAILED(hr)) return hr;
		IPin* pDemuxIn = FindUnconnectedPin(pMpeg1Demux, PINDIR_INPUT, FALSE);
		if (pDemuxIn)
		{
			hr = m_pGraph->ConnectDirect(asyncOutPin, pDemuxIn, NULL);
			if (SUCCEEDED(hr))
			{
				SAFE_RELEASE(asyncOutPin);
				SAFE_RELEASE(pDemuxIn);
				asyncOutPin = FindUnconnectedPin(pMpeg1Demux, PINDIR_OUTPUT, FALSE);
				if (!asyncOutPin)
					return E_FAIL;
			}
			else
			{
				// Just remove it and do the normal thing
				slog(("Aborting insertion of MPEG-I demux since it failed connection\r\n"));
				m_pGraph->RemoveFilter(pMpeg1Demux);
			}
		}
	}
	// If we've got an active source file and it's not MPEG2 then we'll be here. In that
	// case disable the active file bit while DShow builds the graph since otherwise that could
	// cause it to take forever while it tries to read the file infinitely
	if (m_bTimeshifted)
	{
		ISharedAsyncFile *pAsync = NULL;
		HRESULT hr = m_pSrcFilter->QueryInterface(IID_ISharedAsyncFile, (void**)&pAsync);
		if (SUCCEEDED(hr))
		{
			pAsync->put_ShareInfo(0);
			SAFE_RELEASE(pAsync);
		}
	}
	hr = m_pGraph->Render(asyncOutPin);
	if (m_bTimeshifted)
	{
		ISharedAsyncFile *pAsync = NULL;
		HRESULT hr = m_pSrcFilter->QueryInterface(IID_ISharedAsyncFile, (void**)&pAsync);
		if (SUCCEEDED(hr))
		{
			pAsync->put_ShareInfo((ShareInfo*)1);
			SAFE_RELEASE(pAsync);
		}
	}
	SAFE_RELEASE(asyncOutPin);
	HTESTPRINT(hr);
	int numSucceeded = 0;
	HRESULT failedHR = S_OK;
	if (SUCCEEDED(hr))
		numSucceeded++;
	else
		failedHR = hr;

	// See if there's anymore output pins
    IEnumPins* pEnum = NULL;
    hr = m_pSrcFilter->EnumPins(&pEnum);
    if(FAILED(hr)) 
        return hr;

    ULONG ulFound;
    IPin *pPin = NULL;

    while(S_OK == pEnum->Next(1, &pPin, &ulFound))
    {
        PIN_DIRECTION pindir;
        pPin->QueryDirection(&pindir);
        if(pindir == PINDIR_OUTPUT)
        {
			IPin* dummyPin = NULL;
			hr = pPin->ConnectedTo(&dummyPin);
			SAFE_RELEASE(dummyPin);
			if (hr == VFW_E_NOT_CONNECTED)
			{
				hr = m_pGraph->Render(pPin);
				HTESTPRINT(hr);
				if (FAILED(hr))
					failedHR = hr;
				else
					numSucceeded++;
			}
        } 

        pPin->Release();
    } 
    SAFE_RELEASE(pEnum);

	if (m_requiredWMSource)
	{
		slog(("Attempting to setup multichannel WMA decoding since we're using a WM source\r\n"));
		// Attempt to setup multichannel WMA decoding
	}
	// Look for any CC decoder filters and also multichannel WMA if we're doing that
	IEnumFilters* pEnumFilt = NULL;
	IBaseFilter* pFilter = NULL;
	ULONG cFetched;
	HRESULT wmhr = m_pGraph->EnumFilters(&pEnumFilt);
	if (SUCCEEDED(hr))
	{
		while (pEnumFilt->Next(1, &pFilter, &cFetched) == S_OK)
		{
			if (m_requiredWMSource)
			{
				// Check for the DMO Wrapper filter interface
				IDMOWrapperFilter* pWrapper = NULL;
				wmhr = pFilter->QueryInterface(IID_IDMOWrapperFilter, (void**) &pWrapper);
				if (SUCCEEDED(wmhr))
				{
					slog(("Found filter with DMOWrapper interface\r\n"));
					// Get the connected output pint and see if its audio
					pPin = FindUnconnectedPin(pFilter, PINDIR_OUTPUT, TRUE);
					if (pPin && DoesPinSupportMediaType(pPin, &MEDIATYPE_Audio, NULL))
					{
						slog(("Found the audio decoder w/ the DMOWrapper interface\r\n"));
						IPin* pRendererIn = NULL;
						wmhr = pPin->ConnectedTo(&pRendererIn);
						if (SUCCEEDED(wmhr) && pRendererIn)
						{
							wmhr = pPin->Disconnect();
							if (SUCCEEDED(wmhr))
							{
								pRendererIn->Disconnect();
								VARIANT varg;
								::VariantInit(&varg);
								varg.vt = VT_BOOL;
								varg.boolVal = TRUE;
								CComQIPtr<IMediaObject, &IID_IMediaObject> pMediaObject(pWrapper);
								CComQIPtr<IPropertyBag, &IID_IPropertyBag> pPropertyBag(pMediaObject);
								wmhr = pPropertyBag->Write(L"_HIRESOUTPUT", &varg);
								slog(("Set high-res output for the WM audio decoder hr=0x%x\r\n", wmhr));
								wmhr = m_pGraph->ConnectDirect(pPin, pRendererIn, NULL);
							}
							SAFE_RELEASE(pRendererIn);
						}
					}
					SAFE_RELEASE(pPin);
					SAFE_RELEASE(pWrapper);
				}
			}
			if (!m_pLine21Decoder)
			{
				IAMLine21Decoder* p21 = NULL;
				if (SUCCEEDED(pFilter->QueryInterface(IID_IAMLine21Decoder, (void**)&p21)))
				{
					slog(("Found Line21 filter interface in graph\r\n"));
					SAFE_RELEASE(p21);
					m_pLine21Decoder = pFilter;
					m_pLine21Decoder->AddRef();
				}
			}
			SAFE_RELEASE(pFilter);
		}
		SAFE_RELEASE(pEnumFilt);
	}

	return numSucceeded > 0 ? S_OK : hr;
}

HRESULT CPlayerData::RenderCC()
{
	if (m_bUnifiedDecoder || m_ccDisabled) return S_OK;
	HRESULT hr = S_OK;
	// Find the video renderer (or overlay mixer) in the graph and use the unconnected input pin
	// for the CC rendering.
	CComPtr<IBaseFilter> pVideoRenderer = NULL;
	pVideoRenderer.p = GetVideoRendererFilter();
	if (!pVideoRenderer) return E_FAIL;
	CComPtr<IPin> pExtraRenderInput = NULL;
	pExtraRenderInput.p = FindUnconnectedPin(pVideoRenderer, PINDIR_INPUT);
	if (!pExtraRenderInput) return E_FAIL;

	CComPtr<IPin> line21SrcPin = NULL;
	line21SrcPin.p = GetLine21OutputPin();
	if (!line21SrcPin)
		return E_FAIL;

	// For VMR we use the Line21 Decoder 2. We detect this by checking for a VMR9 interface
	// on the input pin.
	IVMRVideoStreamControl9* pVmrTest = NULL;
	hr = pExtraRenderInput->QueryInterface(IID_IVMRVideoStreamControl9, (void**)&pVmrTest);
	if (SUCCEEDED(hr))
	{
		pVmrTest->Release();
		slog(("VMR9 filter detected, using line 21 decoder 2.\r\n"));
		hr = CoCreateInstance(CLSID_Line21Decoder2, NULL, CLSCTX_INPROC_SERVER, IID_IBaseFilter,
			(void**)&m_pLine21Decoder);
	}
	else
	{
		hr = CoCreateInstance(CLSID_Line21Decoder, NULL, CLSCTX_INPROC_SERVER, IID_IBaseFilter,
			(void**)&m_pLine21Decoder);
	}
	if (FAILED(hr)) return hr;
	hr = m_pGraph->AddFilter(m_pLine21Decoder, L"Line21 Decoder");
	if (FAILED(hr)) return hr;
	CComPtr<IPin> line21InPin = NULL;
	line21InPin.p = FindPinByName(m_pLine21Decoder, NULL, PINDIR_INPUT);
	hr = m_pGraph->ConnectDirect(line21SrcPin, line21InPin, NULL);
	if (FAILED(hr)) return hr;
	CComPtr<IPin> line21OutPin = NULL;
	line21OutPin.p = FindPinByName(m_pLine21Decoder, NULL, PINDIR_OUTPUT);

	hr = m_pGraph->ConnectDirect(line21OutPin, pExtraRenderInput, NULL);
	return hr;
}

HRESULT CPlayerData::InactiveFile()
{
	if (!m_pSrcFilter) return E_POINTER;
	ISharedAsyncFile *pAsync = NULL;
	HRESULT hr = m_pSrcFilter->QueryInterface(IID_ISharedAsyncFile, (void**)&pAsync);
	if (SUCCEEDED(hr))
	{
		pAsync->put_ShareInfo(0);
		SAFE_RELEASE(pAsync);
	}
	m_bTimeshifted = FALSE;
	return hr;
}

IBaseFilter* CPlayerData::GetVideoRendererFilter()
{
	if (m_pVideoRenderer)
	{
		m_pVideoRenderer->AddRef();
		return m_pVideoRenderer;
	}
	else
	{
		IBaseFilter* pVideoDecoder = NULL;
		IBaseFilter* pVideoRenderer = NULL;
		if (m_pVideoDecoder)
		{
			pVideoDecoder = m_pVideoDecoder;
			pVideoDecoder->AddRef();
		}
		else
		{
			CComPtr<IPin> vSrc = NULL;
			vSrc.p = GetSourceVideoPin();
			CComPtr<IPin> vNext = NULL;
			if (vSrc)
			{
				vSrc->ConnectedTo(&vNext);
				if (vNext)
				{
					PIN_INFO pInfo;
					if (SUCCEEDED(vNext->QueryPinInfo(&pInfo)))
						pVideoDecoder = pInfo.pFilter;
				}
			}
		}
		if (pVideoDecoder)
		{
			CComPtr<IPin> vSrc = NULL;
			vSrc.p = FindUnconnectedPin(pVideoDecoder, PINDIR_OUTPUT, TRUE);
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
			pVideoDecoder->Release();
		}
		return pVideoRenderer;
	}
}

HRESULT CPlayerData::GetAudioRendererFilter(int audioStreamNum, IBaseFilter** ppAudioDec, IBaseFilter** ppAudioRend)
{
	IBaseFilter* pAudioDecoder = NULL;
	IBaseFilter* pAudioRenderer = NULL;
	CComPtr<IPin> aSrc = NULL;
	aSrc.p = GetSourceAudioPin(audioStreamNum);
	CComPtr<IPin> aNext = NULL;
	if (aSrc)
	{
		aSrc->ConnectedTo(&aNext);
		if (aNext)
		{
			PIN_INFO pInfo;
			if (SUCCEEDED(aNext->QueryPinInfo(&pInfo)))
				pAudioDecoder = pInfo.pFilter;
		}
	}
	if (pAudioDecoder)
	{
		CComPtr<IPin> aSrc = NULL;
		aSrc.p = FindUnconnectedPin(pAudioDecoder, PINDIR_OUTPUT, TRUE);
		CComPtr<IPin> aNext = NULL;
		if (aSrc)
		{
			aSrc->ConnectedTo(&aNext);
			PIN_INFO pInfo;
			if (aNext)
			{
				if (SUCCEEDED(aNext->QueryPinInfo(&pInfo)))
					pAudioRenderer = pInfo.pFilter;
			}
		}
		if (ppAudioDec)
			*ppAudioDec = pAudioDecoder;
		else
			pAudioDecoder->Release();
	}
	// Check one more filter down in case there's a postprocessor
	if (pAudioRenderer)
	{
		CComPtr<IPin> aSrc = NULL;
		aSrc.p = FindUnconnectedPin(pAudioRenderer, PINDIR_OUTPUT, TRUE);
		CComPtr<IPin> aNext = NULL;
		if (aSrc)
		{
			aSrc->ConnectedTo(&aNext);
			PIN_INFO pInfo;
			if (aNext)
			{
				if (SUCCEEDED(aNext->QueryPinInfo(&pInfo)))
				{
					SAFE_RELEASE(pAudioRenderer);
					pAudioRenderer = pInfo.pFilter;
				}
			}
		}
		if (ppAudioRend)
			*ppAudioRend = pAudioRenderer;
	}
	return S_OK;
}

HRESULT CPlayerData::SetVideoPosition(RECT *pSrc, RECT *pDst, BOOL hideMouse)
{
	if (!m_pGraph) return E_FAIL;
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
		// If we set the src & dest rects on one call then both don't always take hold. Quite often
		// only the first one will. So we set it again on the next rect call to clean it up.
		static bool updateAnyways = false;
		bool updatedDest = false;
		long srcTop, srcLeft, srcWidth, srcHeight;
		// Only set the position if it's different (optimization)
		if (pDst)
		{
			pBV->GetDestinationPosition(&srcLeft, &srcTop, &srcWidth, &srcHeight);
			if (srcTop != pDst->top || srcLeft != pDst->left || srcWidth != (pDst->right - pDst->left) || srcHeight != (pDst->bottom - pDst->top))
			{
				hr = pBV->SetDestinationPosition(pDst->left, pDst->top, pDst->right - pDst->left, pDst->bottom - pDst->top);
				HTESTPRINT(hr);
				updatedDest = true;
			}
		}
		else
		{
			if (!pBV->IsUsingDefaultDestination())
			{
				hr = pBV->SetDefaultDestinationPosition();
				HTESTPRINT(hr);
				updatedDest = true;
			}
		}
		if (pSrc)
		{
			pBV->GetSourcePosition(&srcLeft, &srcTop, &srcWidth, &srcHeight);
			if (updateAnyways || srcTop != pSrc->top || srcLeft != pSrc->left || srcWidth != (pSrc->right - pSrc->left) || srcHeight != (pSrc->bottom - pSrc->top))
			{
				hr = pBV->SetSourcePosition(pSrc->left, pSrc->top, pSrc->right - pSrc->left, pSrc->bottom - pSrc->top);
				HTESTPRINT(hr);
			}
		}
		else
		{
			if (!pBV->IsUsingDefaultSource())
			{
				hr = pBV->SetDefaultSourcePosition();
				HTESTPRINT(hr);
			}
		}
		updateAnyways = false;
		if (updatedDest)
			updateAnyways = true;
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
				hr = pVW->GetWindowPosition(&srcLeft, &srcTop, &srcWidth, &srcHeight);
				if (SUCCEEDED(hr) && (srcWidth != grc.right || srcHeight != grc.bottom)) // top and left won't match, they'll be non-zero
				{
					pVW->SetWindowPosition(0, 0, grc.right, grc.bottom);
				}
			}
			long currCursor = OAFALSE;
			hr = pVW->IsCursorHidden(&currCursor);
			long newCursor = hideMouse == JNI_TRUE ? OATRUE : OAFALSE;
			if (SUCCEEDED(hr) && currCursor != newCursor)
				pVW->HideCursor(newCursor);
			SAFE_RELEASE(pVW);
		}
	}
	return S_OK;
}

void CPlayerData::PrintVideoRendererInfo()
{
	slog(("Filter Graph Video Renderer Info:\r\n"));
	CComPtr<IBaseFilter> pVidRend = NULL;
	pVidRend.p = GetVideoRendererFilter();
	if (!pVidRend)
	{
		slog(("No video renderer filter was found in the graph.\r\n"));
		return;
	}
	// Get the CLSID of the video rendering filter
	CLSID vidRendCLSID;
	HRESULT hr = pVidRend->GetClassID(&vidRendCLSID);
	LPOLESTR psz;
	StringFromCLSID(vidRendCLSID, &psz);
	char conv[64];
	WideCharToMultiByte(CP_ACP, 0, psz, -1, conv, 64, 0, 0);
	CoTaskMemFree(psz);
	slog(("Video Renderer:%hs\r\n", GuidNames[vidRendCLSID]));

	// Now get the media type of its input pin
	CComPtr<IPin> renderInput = NULL;
	// This is actually the connected pin
	renderInput.p = FindUnconnectedPin(pVidRend, PINDIR_INPUT, TRUE);

	if (!renderInput)
	{
		slog(("Unable to find video renderer used input pin???\r\n"));
		return;
	}
	PrintPinInfo(renderInput);
}

HRESULT CPlayerData::SelectAudioStream(int audioStreamNum)
{
	slog(("Switching to audio stream %d\r\n", audioStreamNum));
	int i = 1;
	HRESULT hr = E_FAIL;
	IBaseFilter* currAudioRender = NULL;
	do
	{
		SAFE_RELEASE(currAudioRender);
		currAudioRender = NULL;
		GetAudioRendererFilter(i, NULL, &currAudioRender);
		if (currAudioRender)
		{
			IBasicAudio *pAudio = NULL;
			hr = currAudioRender->QueryInterface(IID_IBasicAudio, (void**)&pAudio);
			if (SUCCEEDED(hr))
			{
				if (audioStreamNum == i)
				{
					slog(("Enabling the sound for stream %d\r\n", i));
					pAudio->put_Volume(0);
					hr = S_OK;
				}
				else
				{
					slog(("Muting the sound for stream %d\r\n", i));
					pAudio->put_Volume(-10000); // muted
				}
				SAFE_RELEASE(pAudio);
			}
		}
		i++;
	} while (currAudioRender);
	return hr;
}

void CPlayerData::CreatePluginHandler(void)
{
	slog(("Creating GraphPluginHandler...\r\n"));
	m_pPluginHandler = new GraphPluginHandler();
	slog(("Done creating GraphPluginHandler\r\n"));
}

void CPlayerData::CallPluginGraphStopped(void)
{
	if (m_pPluginHandler)
	{
		slog(("Calling GraphStopped for the PluginHandler\r\n"));
		m_pPluginHandler->GraphStopped(m_pGraph);
		slog(("Done calling GraphStopped for the PluginHandler\r\n"));
	}
}

DEFINE_GUID(CLSID_MpegH264,
	0x8D2D71CB, 0x243F, 0x45E3, 0xB2, 0xD8, 0x5F, 0xD7, 0x96, 0x7E, 0xC0, 0x9B );

static BOOL IsH264Video( IPin* Source )
{
	BOOL isH264Video = FALSE;
	IEnumMediaTypes *mtEnum = NULL;
	HRESULT hr = Source->EnumMediaTypes(&mtEnum);
	AM_MEDIA_TYPE *pMT = NULL;
	while (S_OK == mtEnum->Next(1, &pMT, NULL))
	{
		if ( pMT->majortype == MEDIATYPE_Video && pMT->subtype ==  CLSID_MpegH264 &&
				pMT->formattype == FORMAT_MPEG2_VIDEO	)
		{
			DeleteMediaType(pMT);
			isH264Video = TRUE;
			break;
		}
		DeleteMediaType(pMT);
	}
	SAFE_RELEASE(mtEnum);
	return isH264Video;
}

#ifdef ENABLE_SAGETV_WMDRM_SUPPORT
HRESULT CPlayerData::AddKeyProvider()
{
    HRESULT hr;

    // Instantiate the key provider class, and AddRef it
    // so that COM doesn't try to free our static object.
	prov = new CKeyProvider();
    prov->AddRef();

    // Give the graph an IObjectWithSite pointer to us for callbacks & QueryService.
    IObjectWithSite* pObjectWithSite = NULL;

    hr = m_pGraph->QueryInterface(IID_IObjectWithSite, (void**)&pObjectWithSite);
    if (SUCCEEDED(hr))
    {
        // Use the IObjectWithSite pointer to specify our key provider object.
        // The filter graph manager will use this pointer to call
        // QueryService to do the unlocking.
        // If the unlocking succeeds, then we can build our graph.
            
        hr = pObjectWithSite->SetSite((IUnknown *) (IServiceProvider *) prov);
        pObjectWithSite->Release();
    }

    return hr;
}
#endif