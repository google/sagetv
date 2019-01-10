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

// The following ifdef block is the standard way of creating macros which make exporting 
// from a DLL simpler. All files within this DLL are compiled with the DSHOWPLAYER_EXPORTS
// symbol defined on the command line. this symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// DSHOWPLAYER_API functions as being imported from a DLL, wheras this DLL sees symbols
// defined with this macro as being exported.
#ifdef DSHOWPLAYER_EXPORTS
#define DSHOWPLAYER_API __declspec(dllexport)
#else
#define DSHOWPLAYER_API __declspec(dllimport)
#endif
#ifdef ENABLE_SAGETV_WMDRM_SUPPORT
#include "keyprovider.h"
#endif
// This is for all graph events
#define WM_DVD_EVENT (WM_USER + 396)

#include <pshpack4.h>

class CVMRAllocator;

#include <pshpack1.h>

class CPlayerData
{
public:
	CPlayerData() : m_pSrcFilter(0), m_pDeMux(0), m_pVideoDecoder(0),
		m_pVideoPostProcessor(0), m_pVideoRenderer(0), m_pAudioDecoder(0),
		m_pAudioPostProcessor(0), m_pAudioRenderer(0), m_pGraph(0),
		m_bTimeshifted(FALSE), m_llCircularBufferSize(0),
		m_dwDXVAMPEGMode(0), m_dwDXVADeinterlaceMode(0),
		m_pVmrAllocator(NULL), m_dwRegister(0), m_pLine21Decoder(0),
#ifdef ENABLE_SAGETV_WMDRM_SUPPORT
		m_bUnifiedDecoder(FALSE), m_bRemuxOptimize(FALSE), prov(0)
#else
		m_bUnifiedDecoder(FALSE), m_bRemuxOptimize(FALSE)
#endif
	{
	}
	virtual ~CPlayerData();
	virtual void SetGraph(IGraphBuilder* pGraph);
	virtual HRESULT SetVideoDecoder(IBaseFilter* pFilter);
	virtual HRESULT SetAudioDecoder(IBaseFilter* pFilter);
	virtual HRESULT SetVideoPostProcessor(IBaseFilter* pFilter);
	virtual HRESULT SetAudioPostProcessor(IBaseFilter* pFilter);
	virtual HRESULT SetVideoRenderer(IBaseFilter* pFilter);
	virtual HRESULT SetAudioRenderer(IBaseFilter* pFilter);
	virtual HRESULT SetTimeshiftingProperties(BOOL timeshift, LONGLONG circBuffSize);
	virtual HRESULT SetSourceFilename(const WCHAR* pwFilename, const char* szHostname);
	virtual HRESULT SetDXVAParameters(DWORD dwMpegMode, DWORD dwDeinterlace);
	void SetVMR9Allocator(CVMRAllocator* pVmrAllocator);
	IGraphBuilder* GetGraph() { return m_pGraph; }
	BOOL RequiresCustomSourceFilter();
	virtual HRESULT SetVideoPosition(RECT *pSrc, RECT *pDst, BOOL hideMouse);

	virtual HRESULT RenderAudio();
	virtual HRESULT RenderAudio(int audioStreamNum);
	virtual HRESULT RenderVideo();
	virtual HRESULT RenderCC();
	virtual HRESULT RenderSource();
	BOOL HasDemux() { return (m_pDeMux != 0); }
	virtual HRESULT InactiveFile();
	IBaseFilter* GetLine21Decoder() { return m_pLine21Decoder; }
	IBaseFilter* GetVideoDecoder() { return m_pVideoDecoder; }

	virtual IBaseFilter* GetVideoRendererFilter();
	virtual HRESULT GetAudioRendererFilter(int audioStreamNum, IBaseFilter** pAudioDec, IBaseFilter** pAudioRend);
	void SetUnifiedDecoder(BOOL x) { m_bUnifiedDecoder = x; }
	BOOL IsUnifiedDecoder() { return m_bUnifiedDecoder; }
	void SetRemuxOptimize(BOOL x) { m_bRemuxOptimize = x; }
	virtual HRESULT SelectAudioStream(int audioStreamNum);
protected:
	HRESULT RenderNextInChain(IPin** sourcePin, IBaseFilter* nextFilter, const GUID* pinCat, BOOL directConnect = FALSE);
	virtual IPin* GetSourceVideoPin();
	virtual IPin* GetSourceAudioPin(int audioStreamNum);
	virtual IPin* GetLine21OutputPin();

	HRESULT CloneAndAddFilter(IBaseFilter* pFilter, IBaseFilter** pRV);

	void PrintVideoRendererInfo();

#ifdef ENABLE_SAGETV_WMDRM_SUPPORT
	HRESULT AddKeyProvider();
#endif

	IBaseFilter* m_pSrcFilter;
	IBaseFilter* m_pDeMux;
	IBaseFilter* m_pVideoDecoder;
	IBaseFilter* m_pVideoPostProcessor;
	IBaseFilter* m_pVideoRenderer;
	IBaseFilter* m_pAudioDecoder;
	IBaseFilter* m_pAudioPostProcessor;
	IBaseFilter* m_pAudioRenderer;
	IBaseFilter* m_pLine21Decoder;

	CVMRAllocator* m_pVmrAllocator;

	IGraphBuilder* m_pGraph;

	BOOL m_bTimeshifted;
	LONGLONG m_llCircularBufferSize;

	DWORD m_dwDXVAMPEGMode;
	DWORD m_dwDXVADeinterlaceMode;

	DWORD m_dwRegister;

	BOOL m_bUnifiedDecoder;
	BOOL m_bRemuxOptimize;
	BOOL m_requiredWMSource;

#ifdef ENABLE_SAGETV_WMDRM_SUPPORT
	CKeyProvider* prov;
#endif
};

#include <poppack.h>
#include <poppack.h>
