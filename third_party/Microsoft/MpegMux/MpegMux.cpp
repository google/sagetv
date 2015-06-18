//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: MpegMux.cpp
//
// Desc: DirectShow sample code - implementation of a renderer that MpegMuxs
//       the samples it receives into a text file.
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------


// Summary
//
// We are a generic renderer that can be attached to any data stream that
// uses IMemInputPin data transport. For each sample we receive we write
// its contents including its properties into a MpegMux file. The file we
// will write into is specified when the MpegMux filter is created. GraphEdit
// creates a file open dialog automatically when it sees a filter being
// created that supports the IFileSinkFilter interface.
//
//
// Implementation
//
// Pretty straightforward really, we have our own input pin class so that
// we can override Receive, all that does is to write the properties and
// data into a raw data file (using the Write function). We don't keep
// the file open when we are stopped so the flags to the open function
// ensure that we open a file if already there otherwise we create it.
//
//
// Demonstration instructions
//
// Start GraphEdit, which is available in the SDK DXUtils folder. Drag and drop
// an MPEG, AVI or MOV file into the tool and it will be rendered. Then go to
// the filters in the graph and find the filter (box) titled "Video Renderer"
// This is the filter we will be replacing with the MpegMux renderer. Then click
// on the box and hit DELETE. After that go to the Graph menu and select the
// "Insert Filters", from the dialog box find and select the "MpegMux Filter".
//
// You will be asked to supply a filename where you would like to have the
// data MpegMuxed, the data we receive in this filter is MpegMuxed in text form.
// Then dismiss the dialog. Back in the graph layout find the output pin of
// the filter that used to be connected to the input of the video renderer
// you just deleted, right click and do "Render". You should see it being
// connected to the input pin of the MpegMux filter you just inserted.
//
// Click Pause and Run and then a little later stop on the GraphEdit frame and
// the data being passed to the renderer will be MpegMuxed into a file. Stop the
// graph and dump the filename that you entered when inserting the filter into
// the graph, the data supplied to the renderer will be displayed as raw data
//
//
// Files
//
// MpegMux.cpp             Main implementation of the MpegMux renderer
// MpegMux.def             What APIs the DLL will import and export
// MpegMux.h               Class definition of the derived renderer
// MpegMux.rc              Version information for the sample DLL
// MpegMuxuids.h           CLSID for the MpegMux filter
// makefile             How to build it...
//
//
// Base classes used
//
// CBaseFilter          Base filter class supporting IMediaFilter
// CRenderedInputPin    An input pin attached to a renderer
// CUnknown             Handle IUnknown for our IFileSinkFilter
// CPosPassThru         Passes seeking interfaces upstream
// CCritSec             Helper class that wraps a critical section
//
//

#include <stdio.h>
#include <windows.h>
#include <commdlg.h>
#include <streams.h>
#include <initguid.h>
#include "dvdmedia.h"
#include "MpegMuxuids.h"
#include "../../../native/include/impegmux.h"
#include "MpegMux.h"
#include "../../../native/include/ftmpegdef.h"

// Setup data

const AMOVIESETUP_MEDIATYPE sudPinTypesv =
{
    &MEDIATYPE_Video,            // Major type
    &MEDIASUBTYPE_MPEG2_VIDEO    // Minor type
};
const AMOVIESETUP_MEDIATYPE sudPinTypesa = 
{
    &MEDIATYPE_Audio,            // Major type
    &MEDIASUBTYPE_MPEG1AudioPayload   // Minor type
};

const AMOVIESETUP_PIN sudPins[2] =
{{
    L"Video",                   // Pin string name
    FALSE,                      // Is it rendered
    FALSE,                      // Is it an output
    FALSE,                      // Allowed none
    FALSE,                      // Likewise many
    &CLSID_NULL,                // Connects to filter
    L"Output",                  // Connects to pin
    1,                          // Number of types
    &sudPinTypesv                // Pin information
},
{
    L"Audio",                   // Pin string name
    FALSE,                      // Is it rendered
    FALSE,                      // Is it an output
    FALSE,                      // Allowed none
    FALSE,                      // Likewise many
    &CLSID_NULL,                // Connects to filter
    L"Output",                  // Connects to pin
    1,                          // Number of types
    &sudPinTypesa                // Pin information
}};

const AMOVIESETUP_FILTER sudMpegMux =
{
    &CLSID_MpegMux,                // Filter CLSID
    L"SageTV MpegMux",                    // String name
    MERIT_DO_NOT_USE,           // Filter merit
    2,                          // Number pins
    sudPins                    // Pin details
};


//
//  Object creation stuff
//
CFactoryTemplate g_Templates[]= {
    L"SageTV MpegMux", &CLSID_MpegMux, CMpegMux::CreateInstance, NULL, &sudMpegMux
};
int g_cTemplates = 1;


// Constructor

CMpegMuxFilter::CMpegMuxFilter(CMpegMux *pMpegMux,
                         LPUNKNOWN pUnk,
                         CCritSec *pLock,
                         HRESULT *phr) :
    CBaseFilter(NAME("CMpegMuxFilter"), pUnk, pLock, CLSID_MpegMux),
    m_pMpegMux(pMpegMux)
{
}


//
// GetPin
//
CBasePin * CMpegMuxFilter::GetPin(int n)
{
    if (n == 0)
        return m_pMpegMux->m_pvPin;
	else if (n == 1)
		return m_pMpegMux->m_paPin;
    else
        return NULL;
}


//
// GetPinCount
//
int CMpegMuxFilter::GetPinCount()
{
    return 2;
}


//
// Stop
//
// Overriden to close the MpegMux file
//
STDMETHODIMP CMpegMuxFilter::Stop()
{
    CAutoLock cObjectLock(m_pLock);
	m_pMpegMux->CloseThread();
    m_pMpegMux->CloseFile();
    return CBaseFilter::Stop();
}


//
// Pause
//
// Overriden to open the MpegMux file
//
STDMETHODIMP CMpegMuxFilter::Pause()
{
    CAutoLock cObjectLock(m_pLock);
	QueryPerformanceCounter((LARGE_INTEGER*)&m_pMpegMux->m_baseCpuTime);
    m_pMpegMux->OpenFile();
	m_pMpegMux->StartThread();
    return CBaseFilter::Pause();
}


//
// Run
//
// Overriden to open the MpegMux file
//
//We don't need this since Pause will always be called and handle opening the file
/*STDMETHODIMP CMpegMuxFilter::Run(REFERENCE_TIME tStart)
{
    CAutoLock cObjectLock(m_pLock);
    m_pMpegMux->OpenFile();
    return CBaseFilter::Run(tStart);
}*/


//
//  Definition of CMpegMuxInputPin
//
CMpegMuxInputPin::CMpegMuxInputPin(TCHAR *pObjectName,
							 CMpegMux *pMpegMux,
                             LPUNKNOWN pUnk,
                             CBaseFilter *pFilter,
                             CCritSec *pLock,
                             CCritSec *pReceiveLock,
                             HRESULT *phr,
							 LPCWSTR pName) :

    CRenderedInputPin(pObjectName,
                  pFilter,                   // Filter
                  pLock,                     // Locking
                  phr,                       // Return code
                  pName),                 // Pin name
    m_pReceiveLock(pReceiveLock),
    m_pMpegMux(pMpegMux)
{
}

//
//  Definition of CMpegMuxVideoInputPin
//
CMpegMuxVideoInputPin::CMpegMuxVideoInputPin(CMpegMux *pMpegMux,
                             LPUNKNOWN pUnk,
                             CBaseFilter *pFilter,
                             CCritSec *pLock,
                             CCritSec *pReceiveLock,
                             HRESULT *phr) :

    CMpegMuxInputPin(NAME("CMpegMuxVideoInputPin"),
		pMpegMux, pUnk, pFilter, pLock, pReceiveLock, phr, L"Video"),
		m_foundRecentSeqHdr(FALSE), m_foundFirstGroupStart(FALSE)
{
}

//
//  Definition of CMpegMuxAudioInputPin
//
CMpegMuxAudioInputPin::CMpegMuxAudioInputPin(CMpegMux *pMpegMux,
                             LPUNKNOWN pUnk,
                             CBaseFilter *pFilter,
                             CCritSec *pLock,
                             CCritSec *pReceiveLock,
                             HRESULT *phr) :

    CMpegMuxInputPin(NAME("CMpegMuxAudioInputPin"),
        pMpegMux, pUnk, pFilter, pLock, pReceiveLock, phr, L"Audio")
{
}

//
// CheckMediaType
//
// Check if the pin can support this specific proposed type and format
//
HRESULT CMpegMuxAudioInputPin::CheckMediaType(const CMediaType *pmt)
{
   if(pmt->majortype != GUID_NULL) //== MEDIATYPE_Audio/* && pmt->subtype == MEDIASUBTYPE_MPEG1AudioPayload*/)
        return S_OK;
    else
        return S_FALSE;
}

//
// CheckMediaType
//
// Check if the pin can support this specific proposed type and format
//
HRESULT CMpegMuxVideoInputPin::CheckMediaType(const CMediaType *pmt)
{
//DisplayType(TEXT("CheckMediaType:"), pmt);
    if(pmt->majortype == MEDIATYPE_Video && pmt->subtype != GUID_NULL)
	{
		m_pMpegMux->m_bMpegVideo = (pmt->subtype == MEDIASUBTYPE_MPEG2_VIDEO) ? true : false;
        return S_OK;
	}
    else
        return S_FALSE;
}

//
// GetMediaType
//
HRESULT CMpegMuxVideoInputPin::GetMediaType(int iPosition, CMediaType *pMediaType)
{
//DisplayType(TEXT("GetMediaType:"), pMediaType);
	if (iPosition < 0) return E_INVALIDARG;
    if(iPosition == 0) {
        pMediaType->SetType(&MEDIATYPE_Video);
        //pMediaType->SetSubtype(&MEDIASUBTYPE_MPEG2_VIDEO);
        return S_OK;
    }
    return VFW_S_NO_MORE_ITEMS;
}

//
// GetMediaType
//
HRESULT CMpegMuxAudioInputPin::GetMediaType(int iPosition, CMediaType *pMediaType)
{
	if (iPosition < 0) return E_INVALIDARG;
    if(iPosition == 0) {
//        pMediaType->SetType(&MEDIATYPE_Audio);
  //      pMediaType->SetSubtype(&MEDIASUBTYPE_MPEG1AudioPayload);
        return S_OK;
    }
    return VFW_S_NO_MORE_ITEMS;
}

HRESULT CMpegMuxInputPin::Inactive()
{
    // You do not need to hold the filter lock here. 
    // It is already locked in Stop.

    // Make sure Receive will fail. 
    // This also decommits the allocator.
    HRESULT hr = CBaseInputPin::Inactive();

    // Make sure Receive has completed, and is not using resources.
    CAutoLock c(m_pReceiveLock);

    /* Safe to destroy filter resources used by the streaming thread. */

    return hr;
}

//
// ReceiveCanBlock
//
// We don't hold up source threads on Receive
//
STDMETHODIMP CMpegMuxInputPin::ReceiveCanBlock()
{
    return S_FALSE;
}

__int64 CMpegMux::linRegTimeForBytes(DataRecvTime* pData, DWORD numPoints, LONGLONG byteLen, bool includeGlobalAudioOffset)
{
	if (numPoints == 1) return 1;
	LONGLONG lenAdjust = 0;
	if (includeGlobalAudioOffset)
		lenAdjust = m_totalAudioStatAdjust;
	double meanTime = 0;
	double meanLen = 0;
	DWORD i;
	for (i = 0; i < numPoints; i++)
	{
		meanTime += pData[i].time;
		meanLen += pData[i].len + lenAdjust;
//		DbgLog((LOG_TRACE, 1, TEXT("time[%d]=%s len[%d]=%s"),
//			i, (LPCSTR)CDisp(pData[i].time, CDISP_DEC), i, (LPCSTR)CDisp(pData[i].len, CDISP_DEC)));
	}
	//meanTime /= numPoints;
	//meanLen /= numPoints;

//	DbgLog((LOG_TRACE, 1, TEXT("meantime=%s meanlen=%s"),
//		(LPCSTR)CDisp(meanTime, CDISP_DEC), (LPCSTR)CDisp(meanLen, CDISP_DEC)));
	double sxx = 0;
	double sxy = 0;
	for (i = 0; i < numPoints; i++)
	{
		sxx += (numPoints*(pData[i].len + lenAdjust) - meanLen)*(numPoints*(pData[i].len + lenAdjust) - meanLen);
		sxy += (numPoints*(pData[i].len + lenAdjust) - meanLen)*(numPoints*pData[i].time - meanTime);
	}
	double m = sxy/sxx;
	double b = (meanTime - m * meanLen)/numPoints;
	__int64 cpuFreq;
	QueryPerformanceFrequency((LARGE_INTEGER*)&cpuFreq);
	double bps = cpuFreq/m;
	//DbgLog((LOG_TRACE, 1, TEXT("linregBPS=%s"), (LPCSTR)CDisp(bps)));
	double rv = m * byteLen;
	rv = rv + b;
	return (__int64) rv;
}

BOOL GetClock(const BYTE * pData, LONGLONG *Clock)
{
    BYTE  Byte1 = pData[0];
    DWORD Word2 = ((DWORD)pData[1] << 8) + (DWORD)pData[2];
    DWORD Word3 = ((DWORD)pData[3] << 8) + (DWORD)pData[4];

    /*  Do checks */
    if ((Byte1 & 0xE0) != 0x20 ||
        (Word2 & 1) != 1 ||
        (Word3 & 1) != 1) {
        DbgLog((LOG_TRACE, 2, TEXT("Invalid clock field - 0x%2.2X 0x%4.4X 0x%4.4X"),
            Byte1, Word2, Word3));
        //return FALSE;
    }

    LARGE_INTEGER liClock;
    liClock.HighPart = (Byte1 & 8) != 0;
    liClock.LowPart  = (DWORD)((((DWORD)Byte1 & 0x6) << 29) +
                       (((DWORD)Word2 & 0xFFFE) << 14) +
                       ((DWORD)Word3 >> 1));

    *Clock = liClock.QuadPart;

    return TRUE;
}

//
// Receive
//
// Do something with this media sample
//
STDMETHODIMP CMpegMuxVideoInputPin::Receive(IMediaSample *pSample)
{
	DbgLog((LOG_TRACE, 2, TEXT("MuxVideoInputPin Receive called")));
    PBYTE pbData;
	CAutoLock lock(m_pReceiveLock);
	// Has the filter been stopped yet
	HRESULT hr = CBaseInputPin::Receive(pSample);
	if (hr != S_OK)
	{
		DbgLog((LOG_TRACE, 2, TEXT("MuxVideoInputPin 1 exiting with hr=0x%x"), hr));
		return hr;
	}

    hr = pSample->GetPointer(&pbData);
    if (FAILED(hr)) {
		DbgLog((LOG_TRACE, 2, TEXT("MuxVideoInputPin 2 exiting with hr=0x%x"), hr));
        return hr;
    }
	ULONG len = pSample->GetActualDataLength();
	if (m_pMpegMux->m_bRegBoostThreads)
		SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_HIGHEST);
	DbgLog((LOG_TRACE, 2, TEXT("MuxVideoInputPin Receive data length=%d"), (int)len));
	// If there's no data, then there's nothing to do.
	if (len <= 0 || len == 0xFFFFFFFF)
	{
		DbgLog((LOG_TRACE, 2, TEXT("MuxVideoInputPin exiting due to no length data"), hr));
		return S_OK;
	}

	LONGLONG currPackDTS = 0;

#define PES_VIDEO_HEADER_CODE     0x000001E0
#define FRAGMENTED_PES_LENGTH 65536
#define VDTS_ADJUST 0 //4000
	if (m_pMpegMux->m_bMpegVideo)
	{
		DWORD currWord = DWORD_SWAP(*(UNALIGNED DWORD *)pbData);
		if (m_pMpegMux->m_videoBitrate == 0)
		{
			if (currWord == PES_VIDEO_HEADER_CODE)
			{
				m_pMpegMux->m_bPesVideo = true;
				if (!m_pMpegMux->m_bNextPesPacketFragmented)
				{
					LONGLONG pts = 0;
					if ((pbData[7] >> 6) == 2)
					{
						GetClock(&(pbData[9]), &pts);
					}
					else if ((pbData[7] >> 6) == 3)
					{
						GetClock(&(pbData[9]), &pts);
						GetClock(&(pbData[14]), &currPackDTS);
						currPackDTS += VDTS_ADJUST;
					}
					ULONG headerLen = pbData[8];
					DWORD packetLen = pbData[5];
					packetLen += 6;
					packetLen += (pbData[4] << 8);
					DbgLog((LOG_TRACE, 2, TEXT("PES Video Info PTS/DTSFlag=%d PTS=%s DTS=%s headerLen=%d len=%d packetLen=%d"), 
						pbData[7] >> 6, (LPCSTR) CDisp(pts, CDISP_DEC), (LPCSTR) CDisp(currPackDTS, CDISP_DEC), headerLen,
						len, packetLen));
					if (len <= 9 + headerLen)
					{
						DbgLog((LOG_TRACE, 2, TEXT("MuxVideoInputPin exiting2 due to no length data"), hr));
						return S_OK;
					}
					m_pMpegMux->m_bNextPesPacketFragmented = (len == FRAGMENTED_PES_LENGTH);
					pbData += (9 + headerLen);
					len -= (9 + headerLen);
				}
				else
					m_pMpegMux->m_bNextPesPacketFragmented = (len == FRAGMENTED_PES_LENGTH);
				currWord = DWORD_SWAP(*(UNALIGNED DWORD *)pbData);
			}
			else
				m_pMpegMux->m_bPesVideo = false;
			if (currWord == SEQUENCE_HEADER_CODE)
			{
				// First sequence header we've seen, get the video bitrate
				// from it, also increase our priority so we don't drop video frames!
				m_pMpegMux->m_videoBitrate =
					((pbData[8]*1024) + (pbData[9]*4) + (pbData[10] >> 6)) * 400;
				DbgLog((LOG_TRACE, 2, TEXT("MPEG layer video bitrate=%d"), m_pMpegMux->m_videoBitrate));

				DWORD frameCode = pbData[7] & 0x0F;
				switch (frameCode)
				{
					case 0x01:
						m_pMpegMux->m_frameTicks = 1126125;
						break;
					case 0x02:
						m_pMpegMux->m_frameTicks = 1125000;
						break;
					case 0x03:
						m_pMpegMux->m_frameTicks = 1080000;
						break;
					case 0x04:
						m_pMpegMux->m_frameTicks = 900900;
						break;
					case 0x05:
						m_pMpegMux->m_frameTicks = 900000;
						break;
					case 0x06:
						m_pMpegMux->m_frameTicks = 540000;
						break;
					case 0x07:
						m_pMpegMux->m_frameTicks = 450450;
						break;
					case 0x08:
						m_pMpegMux->m_frameTicks = 450000;
						break;
				}
			}
		}
		else if (m_pMpegMux->m_bPesVideo)
		{
			if (!m_pMpegMux->m_bNextPesPacketFragmented)
			{
				LONGLONG pts = 0;
				if ((pbData[7] >> 6) == 2)
				{
					GetClock(&(pbData[9]), &pts);
				}
				else if ((pbData[7] >> 6) == 3)
				{
					GetClock(&(pbData[9]), &pts);
					GetClock(&(pbData[14]), &currPackDTS);
					currPackDTS += VDTS_ADJUST;
				}
				ULONG headerLen = pbData[8];
				DWORD packetLen = pbData[5];
				packetLen += 6;
				packetLen += (pbData[4] << 8);
				DbgLog((LOG_TRACE, 2, TEXT("PES Video Info PTS/DTSFlag=%d PTS=%s DTS=%s headerLen=%d len=%d packetLen=%d frag=%d"), 
					pbData[7] >> 6, (LPCSTR) CDisp(pts, CDISP_DEC), (LPCSTR) CDisp(currPackDTS, CDISP_DEC), headerLen,
					len, packetLen, m_pMpegMux->m_bNextPesPacketFragmented));
				m_pMpegMux->m_bNextPesPacketFragmented = (len == FRAGMENTED_PES_LENGTH);
				if (len <= 9 + headerLen)
				{
					DbgLog((LOG_TRACE, 2, TEXT("MuxVideoInputPin exiting2 due to no length data")));
					return S_OK;
				}
				pbData += (9 + headerLen);
				len -= (9 + headerLen);
			}
			else
				m_pMpegMux->m_bNextPesPacketFragmented = (len == FRAGMENTED_PES_LENGTH);
		}
	}
	else
	{
		if (m_pMpegMux->m_frameTicks == 0)
		{
			AM_MEDIA_TYPE formy = m_mt;
			VIDEOINFOHEADER* vih = reinterpret_cast<VIDEOINFOHEADER*>(formy.pbFormat);
			/*
			 * gopFrames is only used to determine the boundary for when we write out data
			 *  and for where audio is aligned at
			 * ipFrameDist is used for the offset between PTS & DTS as well as in the increment
			 *  calculations for PTS/DTS inside a GOP
			 * m_numIPFramesPerGop is only used for finding the GOP size
			 */
			m_pMpegMux->m_gopFrames = 15;
			m_pMpegMux->m_ipFrameDist = 1;
			m_pMpegMux->m_numIPFramesPerGop = 1;
			m_pMpegMux->m_videoBitrate = vih->dwBitRate;
			m_pMpegMux->m_frameTicks = (ULONG) (vih->AvgTimePerFrame*27)/10; // convert to 27MHz units from 10MHz
			DbgLog((LOG_TRACE, 2, TEXT("MuxVideoInputPin videoBitrate=%s frameTicks=%s"), 
				(LPCSTR)CDisp(m_pMpegMux->m_videoBitrate, CDISP_DEC), (LPCSTR)CDisp(m_pMpegMux->m_frameTicks, CDISP_DEC)));
			if (!m_pMpegMux->m_videoBitrate)
			{
				m_pMpegMux->m_videoBitrate = 5000000;
				m_pMpegMux->m_frameTicks = (ULONG) (333667*27)/10;
			}
		}
		{
			CAutoLock lock(&m_pMpegMux->m_videoLock);
			m_pMpegMux->m_gopsRecvd++;
			__int64 biggie;
			QueryPerformanceCounter((LARGE_INTEGER*)&biggie);
			biggie -= m_pMpegMux->m_baseCpuTime;
			m_pMpegMux->m_videoStat[m_pMpegMux->m_videoStatIdx].time = biggie;
			m_pMpegMux->m_lastVideoRecvTime = biggie;
			m_pMpegMux->m_videoStat[m_pMpegMux->m_videoStatIdx].len = m_pMpegMux->m_gopsRecvd;
			m_pMpegMux->m_videoStatIdx++;
			if (m_pMpegMux->m_videoStatIdx >= LINREGLEN)
			{
				m_pMpegMux->m_videoStatIdx = 0;
				m_pMpegMux->m_fullVideoStat = true;
			}
			__int64 testTime =
				m_pMpegMux->linRegTimeForBytes(m_pMpegMux->m_videoStat,
				m_pMpegMux->m_fullVideoStat ? LINREGLEN : m_pMpegMux->m_videoStatIdx, m_pMpegMux->m_gopsRecvd, false);
		}
	}


	{
		CAutoLock lock(&m_pMpegMux->m_videoLock);

		// Copy all of the data into the seq buf
		if (len + m_pMpegMux->m_seqBufLen > VID_CIRC_SIZE)
		{
			// BUFFER OVERFLOW
			DbgLog((LOG_TRACE, 2, TEXT("Video BUFFER OVERFLOW time=%d len=%d buflen=%d"),
				timeGetTime(), len, m_pMpegMux->m_seqBufLen));

			return NOERROR;
		}
		ULONG startIdx = (m_pMpegMux->m_seqBufOffset + m_pMpegMux->m_seqBufLen) % VID_CIRC_SIZE;

		if (startIdx + len <= VID_CIRC_SIZE)
			memcpy(m_pMpegMux->m_seqBuf + startIdx, pbData, len);
		else
		{
			memcpy(m_pMpegMux->m_seqBuf + startIdx, pbData, VID_CIRC_SIZE - startIdx);
			memcpy(m_pMpegMux->m_seqBuf, pbData + (VID_CIRC_SIZE - startIdx), len - (VID_CIRC_SIZE - startIdx));
		}
		m_pMpegMux->m_seqBufLen = m_pMpegMux->m_seqBufLen + len;
	}

	if (m_pMpegMux->m_bMpegVideo)
	{
		long lastHdr = 0;
		for (ULONG i = 0; i + 3 < len; i++)
		{
			DWORD currWord = DWORD_SWAP(*(UNALIGNED DWORD *)&pbData[i]);
			// We don't want to dump out after the first code because we don't have any data yet!
			if ((currWord == SEQUENCE_HEADER_CODE && (i != 0 || m_pMpegMux->m_seqBufLen > len)) ||
				(currWord == GROUP_START_CODE))
			{
				if (currWord == SEQUENCE_HEADER_CODE)
					m_foundRecentSeqHdr = TRUE;
				if ((m_foundFirstGroupStart && !m_foundRecentSeqHdr) || currWord == SEQUENCE_HEADER_CODE)
				{
					DWORD seqCodeToPush;
					{
						CAutoLock lock(&m_pMpegMux->m_videoLock);
						m_pMpegMux->m_gopsRecvd++;
						__int64 biggie;
						QueryPerformanceCounter((LARGE_INTEGER*)&biggie);
						biggie -= m_pMpegMux->m_baseCpuTime;
						m_pMpegMux->m_videoStat[m_pMpegMux->m_videoStatIdx].time =
							(((biggie - m_pMpegMux->m_lastVideoRecvTime) * i )/ len) +
							m_pMpegMux->m_lastVideoRecvTime;
						m_pMpegMux->m_lastVideoRecvTime = biggie;
						m_pMpegMux->m_videoStat[m_pMpegMux->m_videoStatIdx].len = m_pMpegMux->m_gopsRecvd;
						m_pMpegMux->m_videoStatIdx++;
						if (m_pMpegMux->m_videoStatIdx >= LINREGLEN)
						{
							m_pMpegMux->m_videoStatIdx = 0;
							m_pMpegMux->m_fullVideoStat = true;
						}
						__int64 testTime =
							m_pMpegMux->linRegTimeForBytes(m_pMpegMux->m_videoStat,
							m_pMpegMux->m_fullVideoStat ? LINREGLEN : m_pMpegMux->m_videoStatIdx, m_pMpegMux->m_gopsRecvd, false);
						DbgLog((LOG_TRACE, 1, TEXT("Video gops=%s Time=%s linRegTime=%s"),
							(LPCSTR)CDisp(m_pMpegMux->m_gopsRecvd, CDISP_DEC),
							(LPCSTR)CDisp(biggie, CDISP_DEC), (LPCSTR)CDisp(testTime, CDISP_DEC)));
						seqCodeToPush = (m_pMpegMux->m_seqBufOffset + m_pMpegMux->m_seqBufLen - len + i)%VID_CIRC_SIZE;
					}
					m_pMpegMux->PushSeqCode(seqCodeToPush);
				}
				else
				{
					m_foundFirstGroupStart = TRUE;
				}
				if (currWord == GROUP_START_CODE)
					m_foundRecentSeqHdr = FALSE;

				i += 3;
			}
			else if (currWord == PICTURE_START_CODE)
			{
				DWORD picType = (pbData[i + 5] & 0x38) >> 3;
				MpegFrameOffset offy;
				offy.dts = currPackDTS;
				offy.frameType = picType;
				{
					CAutoLock lock(&m_pMpegMux->m_videoLock);
					offy.offset = (m_pMpegMux->m_seqBufOffset + m_pMpegMux->m_seqBufLen - len + i)%VID_CIRC_SIZE;
				}
				m_pMpegMux->PushFrameOffset(offy);

				// Check to see if we've dropped a sequence header, if we drop too much here then
				// we're sort of screwed, since it won't be enough to notice the extra frames, but
				// I don't think I've ever seen it drop over 15 frames consecutively
				CAutoLock lock (&m_pMpegMux->m_seqReadyLock);
				// JK I added the +2 because I think sometimes an extra frame gets stuck in a GOP somehow
				if (m_pMpegMux->m_gopFrames != 0 && !m_pMpegMux->m_bPesVideo &&
					m_pMpegMux->m_numFrameCodes > (m_pMpegMux->m_gopFrames * (m_pMpegMux->m_numSeqReady + 1) + 2))
				{
					// We've seen enough frames to make more gops then we've seen, we dropped
					// a sequence header due to CPU utilization, dump a sequence anyways so
					// we don't lose audio video sync by a whole GOP
					DbgLog((LOG_TRACE, 2, TEXT("DROPPED SEQUENCE CODE ----------- DO A SEQ NOW gop=%d numseq=%d numFrames=%d"),
						m_pMpegMux->m_gopFrames, m_pMpegMux->m_numSeqReady, m_pMpegMux->m_numFrameCodes));
					m_pMpegMux->PushSeqCode(offy.offset);
				}
			}
		}
	}
	else
	{
		REFERENCE_TIME start, stop;
		pSample->GetTime(&start, &stop);
		DbgLog((LOG_TRACE, 1, TEXT("MediaSample Time start=%s stop=%s"),
				(LPCSTR)CDisp(start, CDISP_DEC), (LPCSTR)CDisp(stop, CDISP_DEC)));
		pSample->GetMediaTime(&start, &stop);
		DbgLog((LOG_TRACE, 1, TEXT("MediaSample MediaTime start=%s stop=%s"),
				(LPCSTR)CDisp(start, CDISP_DEC), (LPCSTR)CDisp(stop, CDISP_DEC)));
		DbgLog((LOG_TRACE, 1, TEXT("MediaSample syncpoint=%d preroll=%d isdiscont=%d"),
				pSample->IsSyncPoint(), pSample->IsPreroll(), pSample->IsDiscontinuity()));
		MpegFrameOffset offy;
		offy.dts = 0;
		offy.frameType = (pSample->IsSyncPoint() == S_OK) ? I_Frame : P_Frame;
		{
			CAutoLock lock(&m_pMpegMux->m_videoLock);
			offy.offset = (m_pMpegMux->m_seqBufOffset + m_pMpegMux->m_seqBufLen - len)%VID_CIRC_SIZE;
		}
		m_pMpegMux->PushFrameOffset(offy);

		if (offy.frameType == I_Frame && !m_foundFirstGroupStart)
		{
			m_foundFirstGroupStart = TRUE;
		}
		else if (offy.frameType == I_Frame)
		{
			// Check to see if we've got enough frames to fake a GOP
			CAutoLock lock (&m_pMpegMux->m_seqReadyLock);
//			if (m_pMpegMux->m_gopFrames != 0 && //pSample->IsSyncPoint()
//				m_pMpegMux->m_numFrameCodes > m_pMpegMux->m_gopFrames * (m_pMpegMux->m_numSeqReady + 1))
			{
				DbgLog((LOG_TRACE, 1, TEXT("Video gops=%s"),
					(LPCSTR)CDisp(m_pMpegMux->m_gopsRecvd, CDISP_DEC)));
				m_pMpegMux->PushSeqCode(offy.offset);
			}
		}
	}

	if (m_pMpegMux->m_numSeqReady > 0 && m_pMpegMux->m_audioBitrate &&
			(m_pMpegMux->m_audBufLen >= m_pMpegMux->m_audioBytesPerGOP))
		m_pMpegMux->m_evWork.Set();

	return S_OK;
}

void CMpegMux::PushSeqCode(DWORD idx)
{
	CAutoLock lock(&m_seqReadyLock);
	if (m_videoPacksPerGOP == 0 && m_audioBitrate)
	{
		// Init calculation

		if (m_bMpegVideo)
		{
			// Determine how many I & P frames were in this sequence, also
			// determine the maximum number of B frames between 2 I or P frames
			DWORD numIPFrames = 0;
			DWORD maxBFrames = 0;
			DWORD currBCount = 0;
			for (DWORD i = 0; i < m_numFrameCodes && m_frameCodeQ[i].offset < idx; i++)
			{
				if (m_frameCodeQ[i].frameType == I_Frame ||
					m_frameCodeQ[i].frameType == P_Frame)
				{
					numIPFrames++;
					maxBFrames = max(maxBFrames, currBCount);
					currBCount = 0;
				}
				else // B Frame
				{
					currBCount++;
				}
			}
			maxBFrames = max(maxBFrames, currBCount);
			m_ipFrameDist = maxBFrames + 1;
			m_numIPFramesPerGop = numIPFrames;
			m_gopFrames = m_ipFrameDist * m_numIPFramesPerGop;
			// I'm pretty sure the GOP correction is only needed on the Creative
			// card. I never had this before with Provideo and it was OK
			if (m_bPesVideo)
			{
				DbgLog((LOG_TRACE, 2, TEXT("Setting GOP size to 15 PES stream")));
				m_ipFrameDist = 3;
				m_numIPFramesPerGop = 5;
				m_gopFrames = 15;
			}
			/*if (m_gopFrames != 6 && m_gopFrames != 15)
			{
				// This isn't right, change it to 15
				DbgLog((LOG_TRACE, 2, TEXT("Correcting GOP size to 15 from invalid size %d"), m_gopFrames));
				m_ipFrameDist = 3;
				m_numIPFramesPerGop = 5;
				m_gopFrames = 15;
			}*/
		}

		// Adjust the pack size based on the video bitrate. We want to try to average about 6
		// packs per frame because we can end up with as much as a pack of overhead per frame
		// when we're matching on frame boundaries like for non-MPEG2 video
		double fudgeVidBytesPerGOP = (m_videoBitrate * m_frameTicks *
			m_gopFrames * FUDGE) / (27000000 * 8.0);
		double idealPackSize = fudgeVidBytesPerGOP/(6*m_gopFrames);
		if (idealPackSize > 1500)
			m_buffy_size = 2048;
		else if (idealPackSize > 750)
			m_buffy_size = 1024;
		else if (idealPackSize > 375)
			m_buffy_size = 512;
		else// if (idealPackSize > 187)
			m_buffy_size = 256;
		DWORD usableBuffer = m_buffy_size - 33;
		m_videoPacksPerGOP = ((DWORD) (fudgeVidBytesPerGOP / usableBuffer)) + 1 + m_gopFrames;

		m_audioBytesPerGOP = ((m_audioBitrate / 8000) * m_frameTicks * m_gopFrames) / 27000;
		// Add an extra full pack in here for GOP alignment
		m_audioPacksPerGOP = (m_audioBytesPerGOP / usableBuffer) + 2;
	}
	m_stCodeQ[(m_stCodeIdx + m_numSeqReady) % STCODE_Q_SIZE] = idx;
//	DbgLog((LOG_TRACE, 2, TEXT("Video sequence start code located at offset=%d"), idx));
	m_numSeqReady++;
}

DWORD CMpegMux::PeekSeqLen()
{
    CAutoLock lock(&m_seqReadyLock);
	DWORD stCode = m_stCodeQ[m_stCodeIdx];
	if (stCode < m_seqBufOffset) stCode += VID_CIRC_SIZE;
	stCode -= m_seqBufOffset;
	return stCode;
}

DWORD CMpegMux::PopSeqLen()
{
    CAutoLock lock(&m_seqReadyLock);
	DWORD stCode = m_stCodeQ[m_stCodeIdx];
	m_stCodeIdx++;
	if (m_stCodeIdx >= STCODE_Q_SIZE) m_stCodeIdx -= STCODE_Q_SIZE;
	if (stCode < m_seqBufOffset) stCode += VID_CIRC_SIZE;
	stCode -= m_seqBufOffset;
	m_numSeqReady--;
	return stCode;
}

void CMpegMux::PushFrameOffset(MpegFrameOffset x)
{
	CAutoLock lock(&m_seqReadyLock);
	m_frameCodeQ[(m_frameCodeIdx + m_numFrameCodes) % MPEGFRAME_Q_SIZE] = x;
	m_numFrameCodes++;
	DbgLog((LOG_TRACE, 2, TEXT("Picture code found for %s picture off=%d numFrames=%d numSeq=%d"),
		(x.frameType == I_Frame ? "I" : (x.frameType == P_Frame ? "P" : "B")), x.offset,
		m_numFrameCodes, m_numSeqReady));
}
MpegFrameOffset CMpegMux::PopFrameOffset()
{
	CAutoLock lock(&m_seqReadyLock);
	MpegFrameOffset rv = m_frameCodeQ[m_frameCodeIdx];
	m_frameCodeIdx++;
	if (m_frameCodeIdx >= MPEGFRAME_Q_SIZE) m_frameCodeIdx -= MPEGFRAME_Q_SIZE;
	m_numFrameCodes--;
	return rv;
}


//
// Receive
//
// Do something with this media sample
//
STDMETHODIMP CMpegMuxAudioInputPin::Receive(IMediaSample *pSample)
{
    PBYTE pbData;
	CAutoLock lock(m_pReceiveLock);
	HRESULT hr = CBaseInputPin::Receive(pSample);
	if (hr != S_OK) return hr;

    hr = pSample->GetPointer(&pbData);
    if (FAILED(hr)) {
        return hr;
    }
	if (m_pMpegMux->m_bRegBoostThreads)
		SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_HIGHEST);
	long len = pSample->GetActualDataLength();
	// If there's no data, then there's nothing to do.
	if (len <= 0 || len == 0xFFFFFFFF)
		return S_OK;

	{
		CAutoLock lock(&m_pMpegMux->m_audioLock);
		m_pMpegMux->m_aBytesRecvd += len;
		__int64 biggie;
		QueryPerformanceCounter((LARGE_INTEGER*)&biggie);
		biggie -= m_pMpegMux->m_baseCpuTime;
		m_pMpegMux->m_audioStat[m_pMpegMux->m_audioStatIdx].time = biggie;
		m_pMpegMux->m_audioStat[m_pMpegMux->m_audioStatIdx].len = m_pMpegMux->m_aBytesRecvd;
		m_pMpegMux->m_audioStatIdx++;
		if (m_pMpegMux->m_audioStatIdx >= LINREGLEN)
		{
			m_pMpegMux->m_audioStatIdx = 0;
			m_pMpegMux->m_fullAudioStat = true;
		}
		__int64 testTime =
			m_pMpegMux->linRegTimeForBytes(m_pMpegMux->m_audioStat,
			m_pMpegMux->m_fullAudioStat ? LINREGLEN : m_pMpegMux->m_audioStatIdx, m_pMpegMux->m_aBytesRecvd, true);
		DbgLog((LOG_TRACE, 1, TEXT("Audio Bytes=%d Time=%s linRegTime=%s"),
			(int)m_pMpegMux->m_aBytesRecvd, (LPCSTR)CDisp(biggie, CDISP_DEC), (LPCSTR)CDisp(testTime, CDISP_DEC)));
	}

	if (m_pMpegMux->m_audioBitrate == 0 && pbData[0] == 255 && ((pbData[1] >> 4) == 15))
	{
		// Get the information about the MPEG audio stream
		DWORD layerMask = (pbData[1] & 0x06) >> 1;
		m_pMpegMux->m_aFrameSize = (layerMask == 3) ? 384 : 1152;

		DWORD rateMask = pbData[2] >> 4;
		if (layerMask == 3) // layer 1
		{
			switch (rateMask)
			{
				case 0x1:
					m_pMpegMux->m_audioBitrate = 32000;
					break;
				case 0x2:
					m_pMpegMux->m_audioBitrate = 64000;
					break;
				case 0x3:
					m_pMpegMux->m_audioBitrate = 96000;
					break;
				case 0x4:
					m_pMpegMux->m_audioBitrate = 128000;
					break;
				case 0x5:
					m_pMpegMux->m_audioBitrate = 160000;
					break;
				case 0x6:
					m_pMpegMux->m_audioBitrate = 192000;
					break;
				case 0x7:
					m_pMpegMux->m_audioBitrate = 224000;
					break;
				case 0x8:
					m_pMpegMux->m_audioBitrate = 256000;
					break;
				case 0x9:
					m_pMpegMux->m_audioBitrate = 288000;
					break;
				case 0xA:
					m_pMpegMux->m_audioBitrate = 320000;
					break;
				case 0xB:
					m_pMpegMux->m_audioBitrate = 352000;
					break;
				case 0xC:
					m_pMpegMux->m_audioBitrate = 384000;
					break;
				case 0xD:
					m_pMpegMux->m_audioBitrate = 416000;
					break;
				default:
					m_pMpegMux->m_audioBitrate = 448000;
					break;
			}
		}
		else if (layerMask == 2) // layer 2
		{
			switch (rateMask)
			{
				case 0x1:
					m_pMpegMux->m_audioBitrate = 32000;
					break;
				case 0x2:
					m_pMpegMux->m_audioBitrate = 48000;
					break;
				case 0x3:
					m_pMpegMux->m_audioBitrate = 56000;
					break;
				case 0x4:
					m_pMpegMux->m_audioBitrate = 64000;
					break;
				case 0x5:
					m_pMpegMux->m_audioBitrate = 80000;
					break;
				case 0x6:
					m_pMpegMux->m_audioBitrate = 96000;
					break;
				case 0x7:
					m_pMpegMux->m_audioBitrate = 112000;
					break;
				case 0x8:
					m_pMpegMux->m_audioBitrate = 128000;
					break;
				case 0x9:
					m_pMpegMux->m_audioBitrate = 160000;
					break;
				case 0xA:
					m_pMpegMux->m_audioBitrate = 192000;
					break;
				case 0xB:
					m_pMpegMux->m_audioBitrate = 224000;
					break;
				case 0xC:
					m_pMpegMux->m_audioBitrate = 256000;
					break;
				case 0xD:
					m_pMpegMux->m_audioBitrate = 320000;
					break;
				default:
					m_pMpegMux->m_audioBitrate = 384000;
					break;
			}
		}
		else // layerMask == 1 is layer 3
		{
			switch (rateMask)
			{
			case 0x1:
				m_pMpegMux->m_audioBitrate = 32000;
				break;
			case 0x2:
				m_pMpegMux->m_audioBitrate = 40000;
				break;
			case 0x3:
				m_pMpegMux->m_audioBitrate = 48000;
				break;
			case 0x4:
				m_pMpegMux->m_audioBitrate = 56000;
				break;
			case 0x5:
				m_pMpegMux->m_audioBitrate = 64000;
				break;
			case 0x6:
				m_pMpegMux->m_audioBitrate = 80000;
				break;
			case 0x7:
				m_pMpegMux->m_audioBitrate = 96000;
				break;
			case 0x8:
				m_pMpegMux->m_audioBitrate = 112000;
				break;
			case 0x9:
				m_pMpegMux->m_audioBitrate = 128000;
				break;
			case 0xA:
				m_pMpegMux->m_audioBitrate = 160000;
				break;
			case 0xB:
				m_pMpegMux->m_audioBitrate = 192000;
				break;
			case 0xC:
				m_pMpegMux->m_audioBitrate = 224000;
				break;
			case 0xD:
				m_pMpegMux->m_audioBitrate = 256000;
				break;
			default:
				m_pMpegMux->m_audioBitrate = 320000;
				break;
			}
		}
		DWORD sampleBitmask = (pbData[2] >> 2) & 0x3;
		m_pMpegMux->m_audioSampleRate = (sampleBitmask == 0) ? 44100 : 48000;
		m_pMpegMux->m_nextAPTS = (m_pMpegMux->m_llRegAPTS * m_pMpegMux->m_audioSampleRate) / 90000;

		DbgLog((LOG_TRACE, 2, TEXT("MPEG Audio Info rate=%d sampling=%d"),
			m_pMpegMux->m_audioBitrate, m_pMpegMux->m_audioSampleRate));
	}

	{
		CAutoLock lock(&m_pMpegMux->m_audioLock);
		
		// Fill a circular buffer with the audio data that we receive.
		if (len + m_pMpegMux->m_audBufLen > AUD_CIRC_SIZE)
		{
			// BUFFER OVERFLOW
			DbgLog((LOG_TRACE, 2, TEXT("Audio BUFFER OVERFLOW time=%d len=%d buflen=%d"),
				timeGetTime(), len, m_pMpegMux->m_audBufLen));

			return NOERROR;
		}
		ULONG startIdx = (m_pMpegMux->m_audBufOffset + m_pMpegMux->m_audBufLen) % AUD_CIRC_SIZE;

		if (startIdx + len <= AUD_CIRC_SIZE)
			memcpy(m_pMpegMux->m_audBuf + startIdx, pbData, len);
		else
		{
			memcpy(m_pMpegMux->m_audBuf + startIdx, pbData, AUD_CIRC_SIZE - startIdx);
			memcpy(m_pMpegMux->m_audBuf, pbData + (AUD_CIRC_SIZE - startIdx), len - (AUD_CIRC_SIZE - startIdx));
		}
		m_pMpegMux->m_audBufLen = (m_pMpegMux->m_audBufLen + len) % AUD_CIRC_SIZE;

		LONGLONG brv = m_pMpegMux->m_seqBufLen * 8;
		if (m_pMpegMux->m_videoBitrate != 0)
			brv /= (m_pMpegMux->m_videoBitrate/1000);
		LONGLONG bra = m_pMpegMux->m_audBufLen * 8000;
		bra /= m_pMpegMux->m_audioBitrate;
		DbgLog((LOG_TRACE, 2, TEXT("AudioRecv atime=%d vtime=%d tdiff=%d"),
			(int)bra, (int)brv, (int)(bra - brv)));

	}

	// Check to see if video was being held for us
	if (m_pMpegMux->m_numSeqReady > 0 && m_pMpegMux->m_audioBitrate &&
		(m_pMpegMux->m_audBufLen >= m_pMpegMux->m_audioBytesPerGOP))
		m_pMpegMux->m_evWork.Set();

	return S_OK;
}

//
// EndOfStream
//
STDMETHODIMP CMpegMuxInputPin::EndOfStream(void)
{
	// Be sure to synchronize this with the data streaming
    CAutoLock lock(m_pReceiveLock);
	DbgLog((LOG_TRACE, 2, TEXT("EndOfStream Call Received")));
    return CRenderedInputPin::EndOfStream();

} // EndOfStream


//
//  CMpegMux class
//
CMpegMux::CMpegMux(LPUNKNOWN pUnk, HRESULT *phr) :
    CUnknown(NAME("CMpegMux"), pUnk),
    m_pFilter(NULL),
    m_pvPin(NULL),
    m_paPin(NULL),
    m_hFile(INVALID_HANDLE_VALUE),
    m_pFileName(0),
	m_seqBufLen(0),
	m_seqBufOffset(0),
	m_audBufLen(0),
	m_audBufOffset(0),
	m_aFrameRem(0),
	m_videoBitrate(0),
	m_audioBitrate(0),
	m_aFrameSize(0),
	m_audioSampleRate(0),
	m_hThread(NULL),
	m_evStop(TRUE),
	m_evWork(TRUE),
	m_frameTicks(0),
	m_gopFrames(0),
	m_audioByteGOPExtras(0),
	m_circFileSize(0),
	m_nextCircFileSize(0),
	m_lastWrittenAPTS(0)
{
	int i = 0;
    m_pFilter = new CMpegMuxFilter(this, GetOwner(), &m_Lock, phr);
    if (m_pFilter == NULL) {
        *phr = E_OUTOFMEMORY;
        return;
    }

    m_pvPin = new CMpegMuxVideoInputPin(this,GetOwner(),
                               m_pFilter,
                               &m_Lock,
                               &m_vReceiveLock,
                               phr);
    if (m_pvPin == NULL) {
        *phr = E_OUTOFMEMORY;
        return;
    }
    m_paPin = new CMpegMuxAudioInputPin(this,GetOwner(),
                               m_pFilter,
                               &m_Lock,
                               &m_aReceiveLock,
                               phr);
    if (m_paPin == NULL) {
        *phr = E_OUTOFMEMORY;
        return;
    }

	m_seqBuf = new BYTE[VID_CIRC_SIZE];
	m_audBuf = new BYTE[AUD_CIRC_SIZE];
	m_seqBufOffset = 0;
	m_seqBufLen = 0;
	m_audBufOffset = 0;
	m_audBufLen = 0;
	m_aFrameRem = 0;
	m_numSeqReady = 0;
	m_numFrameCodes = 0;
	for (i = 0; i < DIFF_HIST_LEN; i++)
		m_diffHist[i] = 0;
	m_diffHistPos = 0;
	m_diffHistValid = false;
	for (i = 0; i < DIFF_HIST_LEN; i++)
		m_scrDiffHist[i] = 0;
	m_scrDiffHistPos = 0;
	m_scrDiffHistValid = false;
	m_stCodeIdx = 0;
	m_frameCodeIdx = 0;
	m_videoPacksPerGOP = 0;
	m_audioBytesPerGOP = 0;
	m_bytePos = 0;
	m_spareByte = 0;
	m_bitPos = 0;
	m_shareInfo.totalWrite = 0;
	m_gopsWritten = m_aBytesWritten = 0;
	m_gopsRecvd = m_aBytesRecvd = 0;
	m_videoStatIdx = m_audioStatIdx = 0;
	m_fullVideoStat = m_fullAudioStat = false;
	m_nextVSCR = 0;
	m_nextASCR = 0;
	m_nextASCRE = 0;
	m_baseAudioTime = 0;
	m_baseVideoTime = 0;
	m_lastVideoRecvTime = 0;
	m_totalAudioStatAdjust = 0;
	m_bDropNextSeq = false;
	m_wroteSysHdr = false;
	m_bNextPesPacketFragmented = false;
	m_lastFrameAdjust = 0;
	m_numFrameAdjustRepeat = 0;

	m_bRegBoostThreads = true;
	m_llRegVDTS = 19000;
	m_llRegAPTS = 20000;
	m_extraDTS = 0;
	m_bResyncAV = TRUE;

	HKEY rootKey = HKEY_LOCAL_MACHINE;
	DWORD holder;
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(holder);
	if (RegOpenKeyEx(rootKey, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegMux", 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "BoostThreadPriorities", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bRegBoostThreads = (holder == 1);
		}
		if (RegQueryValueEx(myKey, "InitialVideoDTS", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_llRegVDTS = holder;
		}
		if (RegQueryValueEx(myKey, "InitialAudioPTS", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_llRegAPTS = holder;
		}
		if (RegQueryValueEx(myKey, "SynchronizeAV", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bResyncAV = holder ? TRUE : FALSE;
		}
		RegCloseKey(myKey);
	}
	
	m_nextVDTS = m_llRegVDTS;
}


//
// SetFileName
//
// Implemented for IFileSinkFilter support
//
STDMETHODIMP CMpegMux::SetFileName(LPCOLESTR pszFileName,const AM_MEDIA_TYPE *pmt)
{
    // Is this a valid filename supplied
	CAutoLock lock(&m_fileLock);
    CheckPointer(pszFileName,E_POINTER);

    if(wcslen(pszFileName) > MAX_PATH)
        return ERROR_FILENAME_EXCED_RANGE;

	// If a file's already open, then this is being called while
	// we're executing and we need to do a switch.
	bool leaveOpen = false;
	if (m_hFile != INVALID_HANDLE_VALUE)
	{
		CloseFile();
		leaveOpen = true;
		m_bDropNextSeq = true;
		delete m_pFileName;
	}

    // Take a copy of the filename

    m_pFileName = new WCHAR[1+lstrlenW(pszFileName)];
    if (m_pFileName == 0)
        return E_OUTOFMEMORY;
    lstrcpyW(m_pFileName,pszFileName);

    // Create the file then close it

    HRESULT hr = OpenFile();
	if (!leaveOpen)
	    CloseFile();
	m_circFileSize = m_nextCircFileSize;
	m_nextCircFileSize = 0;
    return hr;

} // SetFileName


//
// GetCurFile
//
// Implemented for IFileSinkFilter support
//
STDMETHODIMP CMpegMux::GetCurFile(LPOLESTR * ppszFileName,AM_MEDIA_TYPE *pmt)
{
    CheckPointer(ppszFileName, E_POINTER);
    *ppszFileName = NULL;
    if (m_pFileName != NULL) {
        *ppszFileName = (LPOLESTR)
        QzTaskMemAlloc(sizeof(WCHAR) * (1+lstrlenW(m_pFileName)));
        if (*ppszFileName != NULL) {
            lstrcpyW(*ppszFileName, m_pFileName);
        }
    }

    if(pmt) {
        ZeroMemory(pmt, sizeof(*pmt));
        pmt->majortype = MEDIATYPE_NULL;
        pmt->subtype = MEDIASUBTYPE_NULL;
    }
    return S_OK;

} // GetCurFile


// start the thread
HRESULT CMpegMux::StartThread(void)
{
    if (m_hThread) {
        return S_OK;
    }

    // clear the stop event before starting
    m_evStop.Reset();

    DWORD dwThreadID;
    m_hThread = CreateThread(
                    NULL,
                    0,
                    InitialThreadProc,
                    this,
                    0,
                    &dwThreadID);
    if (!m_hThread) {
	DWORD dwErr = GetLastError();
        return HRESULT_FROM_WIN32(dwErr);
    }
    return S_OK;
}

// stop the thread and close the handle
HRESULT CMpegMux::CloseThread(void)
{
    // signal the thread-exit object
    m_evStop.Set();

    if (m_hThread) {

        WaitForSingleObject(m_hThread, INFINITE);
        CloseHandle(m_hThread);
        m_hThread = NULL;
    }
    return S_OK;
}

// called on thread to process any sequences
void CMpegMux::ProcessSeqs(void)
{
    for (;;) {
        {
            CAutoLock lock(&m_seqReadyLock);

			if (m_numSeqReady <= 0)
			{
				m_evWork.Reset();
				return;
			}

            // release critsec
        }
        {
            CAutoLock lock(&m_audioLock);

			if (m_audioBitrate == 0 || m_audBufLen < m_audioBytesPerGOP)
			{
				m_evWork.Reset();
				return;
			}

            // release critsec
        }

		BOOL holdOnASec;
		{
			CAutoLock lock(&m_fileLock);

	        holdOnASec = !PackupVideoSequence();
		}
		if (holdOnASec)
			Sleep(10);
    }
}

// the thread proc - assumes that DWORD thread param is the
// this pointer
DWORD CMpegMux::ThreadProc(void)
{
    HANDLE ahev[] = {m_evStop, m_evWork};
	if (m_bRegBoostThreads)
		SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_ABOVE_NORMAL);

    for (;;) {
	    DWORD dw = WaitForMultipleObjects(
		        2,
		        ahev,
		        FALSE,
		        INFINITE);
	    if (dw == WAIT_OBJECT_0+1) {

	        // requests need processing
	        ProcessSeqs();
	    } else {
	        // any error or stop event - we should exit
	        return 0;
	    }
    }
}

// Destructor

CMpegMux::~CMpegMux()
{
    // shutdown worker thread
    CloseThread();

    CloseFile();

    delete m_pvPin;
    delete m_paPin;
    delete m_pFilter;
    delete m_pFileName;
	delete [] m_seqBuf;
	delete [] m_audBuf;
}


//
// CreateInstance
//
// Provide the way for COM to create a MpegMux filter
//
CUnknown * WINAPI CMpegMux::CreateInstance(LPUNKNOWN punk, HRESULT *phr)
{
    CMpegMux *pNewObject = new CMpegMux(punk, phr);
    if (pNewObject == NULL) {
        *phr = E_OUTOFMEMORY;
    }
    return pNewObject;

} // CreateInstance


//
// NonDelegatingQueryInterface
//
// Override this to say what interfaces we support where
//
STDMETHODIMP CMpegMux::NonDelegatingQueryInterface(REFIID riid, void ** ppv)
{
    CheckPointer(ppv,E_POINTER);
    CAutoLock lock(&m_Lock);

    // Do we have this interface

    if (riid == IID_IFileSinkFilter) {
        return GetInterface((IFileSinkFilter *) this, ppv);
    } 
	else if (riid == IID_IMpegMux) {
		return GetInterface((IMpegMux *) this, ppv);
	}
    else if (riid == IID_IBaseFilter || riid == IID_IMediaFilter || riid == IID_IPersist) {
	    return m_pFilter->NonDelegatingQueryInterface(riid, ppv);
    } 

    return CUnknown::NonDelegatingQueryInterface(riid, ppv);

} // NonDelegatingQueryInterface

//
// OpenFile
//
// Opens the file ready for MpegMuxing
//
HRESULT CMpegMux::OpenFile()
{
    TCHAR *pFileName = NULL;

    // Is the file already opened
    if (m_hFile != INVALID_HANDLE_VALUE) {
        return NOERROR;
    }

    // Has a filename been set yet
    if (m_pFileName == NULL) {
        return ERROR_INVALID_NAME;
    }

    // Convert the UNICODE filename if necessary

#if defined(WIN32) && !defined(UNICODE)
    char convert[MAX_PATH];
    if(!WideCharToMultiByte(CP_ACP,0,m_pFileName,-1,convert,MAX_PATH,0,0))
        return ERROR_INVALID_NAME;
    pFileName = convert;
#else
    pFileName = m_pFileName;
#endif

    // Try to open the file

    m_hFile = CreateFile((LPCTSTR) pFileName,   // The filename
                         GENERIC_WRITE,         // File access
                         FILE_SHARE_READ | FILE_SHARE_WRITE,             // Share access
                         NULL,                  // Security
                         OPEN_ALWAYS,         // Open flags
                         (DWORD) 0,             // More flags
                         NULL);                 // Template

    if (m_hFile == INVALID_HANDLE_VALUE) {
        DWORD dwErr = GetLastError();
        return HRESULT_FROM_WIN32(dwErr);
    }

	m_shareInfo.totalWrite = 0;

	return S_OK;

} // Open


//
// CloseFile
//
// Closes any MpegMux file we have opened
//
HRESULT CMpegMux::CloseFile()
{
	DbgLog((LOG_TRACE, 2, TEXT("MpegMux is closing the file")));
	CAutoLock lock(&m_fileLock);
    if (m_hFile == INVALID_HANDLE_VALUE) {
        return NOERROR;
    }

    CloseHandle(m_hFile);
    m_hFile = INVALID_HANDLE_VALUE;
    return NOERROR;

} // Open

HRESULT CMpegMux::WritePSHeaders()
{
	// Calculate the mux_rate first
	// 33 bytes of overhead in a pack

	DWORD gopBytes = (m_audioPacksPerGOP + m_videoPacksPerGOP) * m_buffy_size;

	m_muxRate = (DWORD) (((LONGLONG)gopBytes * 540000L/*27e6/50*/)/((LONGLONG)m_frameTicks * m_gopFrames));
	
	DbgLog((LOG_TRACE, 2, TEXT("MPEG Mux Details vPacksPerGOP=%d aPacksPerGOP=%d muxRate=%d audBytesPerGOP=%d"),
		m_videoPacksPerGOP, m_audioPacksPerGOP, m_muxRate, m_audioBytesPerGOP));

	WritePackHeader(0, 0, 0);
	WriteSystemHeader();
	WritePESPaddingPacket(0);

	//FlushFileBuffers(m_hFile);
	return S_OK;
}

void CMpegMux::WriteSystemHeader()
{
	BufferBits(SYSTEM_HEADER_START_CODE, 32); // system_header_start_code
	BufferBits(12, 16); // header length = 6 + numStreams*3
	BufferBits(1, 1); // marker
	BufferBits(m_muxRate, 22); // rate_bound
	BufferBits(1, 1); // marker
	BufferBits(1, 6); // audio_bound
	BufferBits(0, 1); // fixed_flag
	BufferBits(0, 1); // CSPS_flag
	BufferBits(1, 1); // audio lock
	BufferBits(1, 1); // video lock
	BufferBits(1, 1); // marker
	BufferBits(1, 5); // video bound
	BufferBits(0, 1); // packet rate restriction flag
	BufferBits(0x7F, 7); // reserved

	// video stream info
	BufferBits(VIDEO_GLOBAL, 8); // stream_id
	BufferBits(0x03, 2);
	BufferBits(1, 1); // p_std buffer bound scale (0-audio, 1-video)
	BufferBits(230, 13);

	// audio stream info
	BufferBits(AUDIO_GLOBAL, 8); // stream_id
	BufferBits(0x03, 2);
	BufferBits(0, 1); // p_std buffer bound scale (0-audio, 1-video)
	BufferBits(32, 13);

	m_wroteSysHdr = true;
}

void CMpegMux::WritePESPaddingPacket(ULONG packSize)
{
	BufferBits(0x000001, 24); // packet_start_code_prefix
	BufferBits(190, 8); // stream_id for padding
	unsigned int numPads = (packSize == 0) ? (m_buffy_size - m_bytePos - 2) :
		(packSize - 6);
	BufferBits(numPads, 16);
	memset(m_buffy + m_bytePos, 0xFF, numPads);
	m_bytePos += numPads;
	WriteBuff(m_bytePos);
}

STDMETHODIMP CMpegMux::get_ShareInfo(ShareInfo **sharin)
{
	*sharin = &m_shareInfo;
	return S_OK;
}

STDMETHODIMP CMpegMux::get_FileLength(LONGLONG *fileLength)
{
	CAutoLock(&(m_shareInfo.shareLock));
	*fileLength = m_shareInfo.totalWrite;
	return S_OK;
}

STDMETHODIMP CMpegMux::put_CircularSize(long lCircSize)
{
	if (m_pFilter->IsStopped())
		m_circFileSize = lCircSize;
	else
		m_nextCircFileSize = lCircSize;
	return S_OK;
}

// len is the total length of the sequence
BOOL CMpegMux::PackupVideoSequence()
{
	DWORD dw_initialFrameCodeIdx = m_frameCodeIdx;
	// This initializes more of the variables
	DWORD len = PeekSeqLen();
	DWORD usableBuffer = m_buffy_size - 33;
	DWORD numFramesThisGOP = 1;
	DWORD currAudioPacksPerGOP = m_audioPacksPerGOP;
	DWORD currAudioBytesPerGOP = m_audioBytesPerGOP;
	ULONGLONG targetDTS = (ULONGLONG) m_frameCodeQ[m_frameCodeIdx].dts;
	ULONGLONG nextTargetDTS = 0;
	for (DWORD i = 1; i < m_numFrameCodes; i++)
	{
DbgLog((LOG_TRACE, 2, TEXT("idx=%d type=%d"), (int)m_frameCodeIdx, (int)m_frameCodeQ[(m_frameCodeIdx + i) % MPEGFRAME_Q_SIZE].frameType));
		if (m_frameCodeQ[(m_frameCodeIdx + i) % MPEGFRAME_Q_SIZE].frameType == I_Frame)
		{
//			currAudioBytesPerGOP = ((m_audioBitrate / 8000) * m_frameTicks * numFramesThisGOP) / 27000;
			// Add an extra full pack in here for GOP alignment
//			currAudioPacksPerGOP = (currAudioBytesPerGOP / usableBuffer) + 2;
			nextTargetDTS = (ULONGLONG) m_frameCodeQ[(m_frameCodeIdx + i) % MPEGFRAME_Q_SIZE].dts;
			DbgLog((LOG_TRACE, 2, TEXT("Num Frames this GOP Sequence is %d"), numFramesThisGOP));
//ASSERT(numFramesThisGOP > 10);
			break;
		}
		else
			numFramesThisGOP++;
	}
	// Counting the num of frames for the current GOP only seemed to be helpful when it 
	// was doing the length 1 GOPs. When it outputs length 12 GOPS (which happens with the VBDVCR)
	// it corresponds to 15 video frames of audio, so we can't just count frames, we need to
	// hack an adjustment in
/*	if (m_gopFrames > numFramesThisGOP && numFramesThisGOP > 1)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Corrected GOP size from %d to be %d"),
			numFramesThisGOP, m_gopFrames));
		numFramesThisGOP = m_gopFrames;
		currAudioBytesPerGOP = ((m_audioBitrate / 8000) * m_frameTicks * numFramesThisGOP) / 27000;
		// Add an extra full pack in here for GOP alignment
		currAudioPacksPerGOP = (currAudioBytesPerGOP / usableBuffer) + 2;
	}*/
	if (targetDTS && nextTargetDTS)
	{
		numFramesThisGOP = (DWORD) (nextTargetDTS - targetDTS + m_extraDTS)/(m_frameTicks/300);
//		m_extraDTS = (nextTargetDTS - targetDTS + m_extraDTS) - (numFramesThisGOP * (m_frameTicks/300));
		DbgLog((LOG_TRACE, 2, TEXT("Adjusting GOP times to match DTS variables target=%s nextTarget=%s frames=%d extraDTS=%s"),
			(LPCSTR)CDisp(targetDTS, CDISP_DEC),(LPCSTR)CDisp(nextTargetDTS, CDISP_DEC), numFramesThisGOP,
			(LPCSTR)CDisp(m_extraDTS, CDISP_DEC)));
		currAudioBytesPerGOP = (DWORD) ((m_audioBitrate / 8000) * (nextTargetDTS - targetDTS) * 300) / 27000;
		// Add an extra full pack in here for GOP alignment
		currAudioPacksPerGOP = (currAudioBytesPerGOP / usableBuffer) + 2;
	}

	// Check to make sure we have enough audio data, because of GOP resizing, we may not have enough
	if (currAudioBytesPerGOP > m_audBufLen)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Aborting GOP write because of lack of audio data, have=%d need=%d"),
			m_audBufLen, currAudioBytesPerGOP));
		return FALSE;
	}

	bool freshOne = !m_wroteSysHdr;
	if (!m_wroteSysHdr)
	{
		m_shareInfo.totalWrite = 0;
		WritePSHeaders();
	}

#ifdef _DEBUG
	LONGLONG brv = m_seqBufLen * 8;
	brv /= (m_videoBitrate/1000);
	LONGLONG bra = m_audBufLen * 8000;
	bra /= m_audioBitrate;

	m_diffHist[m_diffHistPos] = (LONG) (bra - brv);
	m_diffHistPos++;
	if (m_diffHistPos >= DIFF_HIST_LEN)
	{
		m_diffHistPos -= DIFF_HIST_LEN;
		m_diffHistValid = true;
	}
	LONG diffAvg = 0;
	if (m_diffHistValid)
	{
		for (int i = 0; i < DIFF_HIST_LEN; i++)
			diffAvg += m_diffHist[i];
		diffAvg /= DIFF_HIST_LEN;
		DbgLog((LOG_TRACE, 2, TEXT("DIFF AVERAGE=%d"), (int)diffAvg));
	}

	DbgLog((LOG_TRACE, 1, TEXT("Seq Bound Diffs atime=%d vtime=%d tdiff=%d"),
		(int)bra, (int)brv, (int)(bra - brv)));
#endif
	m_scrDiffHist[m_scrDiffHistPos] = (LONG) (((LONGLONG)m_nextASCR) - ((LONGLONG)m_nextVSCR));
	m_scrDiffHistPos++;
	if (m_scrDiffHistPos >= DIFF_HIST_LEN)
	{
		m_scrDiffHistPos -= DIFF_HIST_LEN;
		m_scrDiffHistValid = true;
	}
	LONG scrDiffAvg = 0;
	LONG scrBaseDiff = 0;
	if (m_scrDiffHistValid)
	{
		for (int i = 0; i < DIFF_HIST_LEN; i++)
			scrDiffAvg += m_scrDiffHist[i];
		scrDiffAvg /= DIFF_HIST_LEN;
		scrBaseDiff = scrDiffAvg - (LONG)(((LONGLONG)m_llRegAPTS) - ((LONGLONG)m_llRegVDTS));
		DbgLog((LOG_TRACE, 2, TEXT("SCR DIFF AVERAGE=%d BASEDIFF=%d"), scrDiffAvg, scrBaseDiff));
	}

	DbgLog((LOG_TRACE, 1, TEXT("Seq Bound SCR Diffs ascr=%s vscr=%s tdiff=%d"),
		(LPCSTR)CDisp(m_nextASCR, CDISP_DEC), (LPCSTR)CDisp(m_nextVSCR, CDISP_DEC),
		(LONG)(((LONGLONG)m_nextASCR) - ((LONGLONG)m_nextVSCR))));

	if (m_scrDiffHistValid && abs(scrBaseDiff) > 1000)
	{
		DbgLog((LOG_TRACE, 2, TEXT("PES Correction for PES drift before vdts=%s ascr=%s"),
			(LPCSTR)CDisp(m_nextVDTS, CDISP_DEC), (LPCSTR)CDisp(m_nextASCR, CDISP_DEC)));
		if (scrBaseDiff < 0)
		{
			m_nextVDTS = m_nextVDTS - abs(scrBaseDiff);
			m_nextASCR = m_nextASCR + abs(scrBaseDiff);
		}
		else
		{
			m_nextVDTS = m_nextVDTS + scrBaseDiff;
			m_nextASCR = m_nextASCR - scrBaseDiff;
		}
		DbgLog((LOG_TRACE, 2, TEXT("PES Correction for PES drift after vdts=%s ascr=%s"),
			(LPCSTR)CDisp(m_nextVDTS, CDISP_DEC), (LPCSTR)CDisp(m_nextASCR, CDISP_DEC)));
		//for (int i = 0; i < DIFF_HIST_LEN; i++)
		//	m_scrDiffHist[i] -= scrBaseDiff;
		m_scrDiffHistPos = 0;
		m_scrDiffHistValid = false;
	}

	// First we write out the pack with the PTS & DTS information
	ULONGLONG scr = m_nextVSCR;
	ULONGLONG scre = 0;

//	DbgLog((LOG_TRACE, 2, TEXT("Packup audBufLen=%d vBufCurr=%d seqBufLen=%d audToWrite=%d"),
//		m_audBufLen, len, m_seqBufLen, m_audioBytesPerGOP));

	// This is used to ensure the entire AM_MEDIA_TYPE structure will go into a single pack
	DWORD dwMustWriteBytesInPack = 0;

	DWORD aPacksToWrite = currAudioPacksPerGOP;
	DWORD vPacksToWrite = m_videoPacksPerGOP;
	DWORD aBytesToWrite = currAudioBytesPerGOP - m_audioByteGOPExtras;
	m_audioByteGOPExtras = 0;
	if (freshOne)
	{
		DbgLog((LOG_TRACE, 1, TEXT("File change audiorem=%d nextAPTS=%s nextASCR=%s"),
			(int) m_aFrameRem, (LPCSTR)CDisp(m_nextAPTS, CDISP_DEC), (LPCSTR)CDisp(m_nextASCR, CDISP_DEC)));
		if (m_aFrameRem != 0)
		{
			// This is a new file we've started and there's audio fragments from
			// the last frame in here, clear 'em out, but be sure not to
			// screw up our sync.
			aBytesToWrite -= m_aFrameRem;

			CAutoLock lock(&m_audioLock);
			m_audBufLen -= m_aFrameRem;
			m_audBufOffset = (m_audBufOffset + m_aFrameRem) % AUD_CIRC_SIZE;
			m_aBytesWritten += m_aFrameRem;
			m_aFrameRem = 0;
		}
		ULONGLONG scrAdjust = m_nextVSCR;
		ULONGLONG audioAdjust = (scrAdjust * m_audioSampleRate) / 90000;
		if (audioAdjust >= m_nextAPTS)
			m_nextAPTS = 0;
		else
			m_nextAPTS -= audioAdjust;
		if (scrAdjust >= m_nextASCR)
			m_nextASCR = 0;
		else
			m_nextASCR -= scrAdjust;
		m_nextVSCR = 0;
		scr = 0;
		m_nextVDTS -= scrAdjust;
		DbgLog((LOG_TRACE, 1, TEXT("File change completed adjust nextAPTS=%s nextASCR=%s"),
			(LPCSTR)CDisp(m_nextAPTS, CDISP_DEC), (LPCSTR)CDisp(m_nextASCR, CDISP_DEC)));

	
		if (!m_bMpegVideo)
		{
			//This is where we write out the video stream format information after our unique stream identifier
			DWORD holder = MAGIC_STREAM_IDENTIFIER;
			int formLength = sizeof(holder); // magic id
			CMediaType* cmt = m_pvPin->MediaType();
			//VIDEOINFOHEADER* vih = reinterpret_cast<VIDEOINFOHEADER*>(cmt->pbFormat);
			//BITMAPINFOHEADER* bih = reinterpret_cast<BITMAPINFOHEADER*>(&vih->bmiHeader);
			formLength += sizeof(AM_MEDIA_TYPE);
			formLength += cmt->cbFormat;
			len += formLength;
			LPBYTE formBlock = new BYTE[formLength];
			memcpy(formBlock, (void*)&holder, sizeof(holder));
			memcpy(formBlock + sizeof(holder), (void*)cmt, sizeof(AM_MEDIA_TYPE));
			memcpy(formBlock + sizeof(holder) + sizeof(AM_MEDIA_TYPE), (void*)cmt->pbFormat, cmt->cbFormat);

			CAutoLock vlock(&m_videoLock);
			m_seqBufLen += formLength;
			if ((int)m_seqBufOffset < formLength)
				m_seqBufOffset = (m_seqBufOffset + VID_CIRC_SIZE) - formLength;
			else
				m_seqBufOffset -= formLength;

			if (m_seqBufOffset + formLength <= VID_CIRC_SIZE)
				memcpy((void*)(m_seqBuf + m_seqBufOffset), (void*)formBlock, formLength);
			else
			{
				memcpy((void*)(m_seqBuf + m_seqBufOffset), formBlock, VID_CIRC_SIZE - m_seqBufOffset);
				memcpy((void*)m_seqBuf, formBlock + VID_CIRC_SIZE - m_seqBufOffset, 
					formLength - (VID_CIRC_SIZE - m_seqBufOffset));
			}
			dwMustWriteBytesInPack = formLength;
			
			delete [] formBlock;
		}
	}

	// Check to see if we've got too much video data to fit in here correctly.
	if (vPacksToWrite < ((len / usableBuffer) + 1 + numFramesThisGOP))
	{
		vPacksToWrite = (len / usableBuffer) + 1 + numFramesThisGOP;
		DbgLog((LOG_TRACE, 2, TEXT("Adjusted number of video packs from %d to %d"),
			m_videoPacksPerGOP, vPacksToWrite));
	}

	// If the first video frame is smaller than a buffer size we'll have a problem; but we've adjusted
	// the pack size to make sure it'll never be that small.
	if (usableBuffer <= len)
	{
		WritePackHeader(scr, scre, 0);
		if (dwMustWriteBytesInPack > usableBuffer)
		{
			WritePESVideoPacket(m_nextVDTS + (m_frameTicks*m_ipFrameDist)/300, m_nextVDTS, dwMustWriteBytesInPack, TRUE);
			len -= dwMustWriteBytesInPack;
		}
		else
		{
			WritePESVideoPacket(m_nextVDTS + (m_frameTicks*m_ipFrameDist)/300, m_nextVDTS, usableBuffer, TRUE);
			len -= usableBuffer;
		}
	}
	else
	{
		// Not a full pack of video data for this GOP.
		if (usableBuffer - len > 7)
		{
			WritePackHeader(scr, scre, 0);
			if (len > 0)
			{
				WritePESVideoPacket(m_nextVDTS + (m_frameTicks*m_ipFrameDist)/300, m_nextVDTS, len, TRUE);
				WritePESPaddingPacket(usableBuffer - len);
			}
			else // we're missing 19 of the used bytes cause we didn't do that header
				WritePESPaddingPacket(usableBuffer - len + 19);
		}
		else
		{
			WritePackHeader(scr, scre, usableBuffer - len);
			WritePESVideoPacket(m_nextVDTS + (m_frameTicks*m_ipFrameDist)/300, m_nextVDTS, len, TRUE);
		}
		len = 0;
	}
	ULONGLONG currVDTS = m_nextVDTS;
	DWORD lastVFrameType = I_Frame;
	PopFrameOffset();
	vPacksToWrite--;
	DWORD packsToWrite = aPacksToWrite + vPacksToWrite;


	// Interpolate the audio & video times for what we've written, add/drop
	// audio frames if we're losing sync.
	int frameAdjust = 0;
	int maxFrameAdjust = 0;
//	if (m_fullVideoStat && m_fullAudioStat)
	{
		__int64 audioTime = 0;
		__int64 videoTime = 0;
		{
			CAutoLock lock(&m_videoLock);
			videoTime = linRegTimeForBytes(m_videoStat, m_fullVideoStat ? LINREGLEN : m_videoStatIdx, m_gopsWritten, false);
		}
		{
			CAutoLock lock(&m_audioLock);
			audioTime = linRegTimeForBytes(m_audioStat, m_fullAudioStat ? LINREGLEN : m_audioStatIdx, m_aBytesWritten, true);
		}
		__int64 cpuRes = 0;
		QueryPerformanceFrequency((LARGE_INTEGER*)&cpuRes);
		//videoTime = ((m_nextVDTS - 19000) * cpuRes )/ 90000;
		__int64 diffTime = videoTime - audioTime;
		double doubleDiff = (diffTime/((double)cpuRes));
		DbgLog((LOG_TRACE, 1, TEXT("Video-Audio Time=%s vTime=%s aTime=%s cpuRes=%s"),
			(LPCSTR)CDisp(doubleDiff),
			(LPCSTR)CDisp(videoTime/((double)cpuRes)),
			(LPCSTR)CDisp(audioTime/((double)cpuRes)),
			(LPCSTR)CDisp(cpuRes, CDISP_DEC)));
#define DIFF_FOR_SYNC_ADJUST 0.05
		{
			CAutoLock lock(&m_audioLock);
			if (!m_fullAudioStat && m_audioStatIdx > MINREGLENA && m_audioStatIdx < MAXREGLENA)
			{
				m_baseAudioTime = linRegTimeForBytes(m_audioStat, m_fullAudioStat ? LINREGLEN : m_audioStatIdx, 0, false);
				DbgLog((LOG_TRACE, 1, TEXT("Audio Base Time=%s"),
					(LPCSTR)CDisp(m_baseAudioTime/((double)cpuRes))));
			}
		}
		{
			CAutoLock lock(&m_videoLock);
			if (!m_fullVideoStat && m_videoStatIdx > MINREGLENV && m_videoStatIdx < MAXREGLENV)
			{
				m_baseVideoTime = linRegTimeForBytes(m_videoStat, m_fullVideoStat ? LINREGLEN : m_videoStatIdx, 0, false);
				DbgLog((LOG_TRACE, 1, TEXT("Video Base Time=%s"),
					(LPCSTR)CDisp(m_baseVideoTime/((double)cpuRes))));
			}
		}
		if (m_baseAudioTime != 0 && m_baseVideoTime != 0)
		{
			videoTime -= m_baseVideoTime;
			audioTime -= m_baseAudioTime;
			diffTime = videoTime - audioTime;
			doubleDiff = (diffTime/((double)cpuRes));
			DbgLog((LOG_TRACE, 1, TEXT("2Video-Audio Time=%s vTime=%s aTime=%s"),
				(LPCSTR)CDisp(doubleDiff),
				(LPCSTR)CDisp(videoTime/((double)cpuRes)),
				(LPCSTR)CDisp(audioTime/((double)cpuRes))));

			// Only do single frame adjustment for now to prevent overcompensation which seems to
			// come up with some frequency.
			if (doubleDiff > DIFF_FOR_SYNC_ADJUST ||
				-1*doubleDiff > DIFF_FOR_SYNC_ADJUST)
			{
				frameAdjust = (int)(doubleDiff / (m_aFrameSize / ((double) m_audioSampleRate)));
				frameAdjust *= -1;
				maxFrameAdjust = frameAdjust;
				DbgLog((LOG_TRACE, 1, TEXT("Sync Frame Adjustment of %d audio frames for time %s"),
					frameAdjust, (LPCSTR)CDisp(doubleDiff)));
				if (frameAdjust < 0)
				{
					frameAdjust = max(-1, frameAdjust);
					// Check to make sure we've got enough extra audio data in the
					// buffer to remove.
					DWORD nextFrameLen = (m_aFrameSize * m_audioBitrate/8) / m_audioSampleRate;
					nextFrameLen++; // pad maybe
					CAutoLock lock(&m_audioLock);
					if (m_audBufLen + nextFrameLen*frameAdjust < aBytesToWrite)
					{
						frameAdjust = 0;
						DbgLog((LOG_TRACE, 1, TEXT("Sync Frame Adjustment suspended to prevent underflow")));
					}
				}
				else if (frameAdjust > 0)
					frameAdjust = min(1, frameAdjust);
			}
		}
	}
#define FRAME_ADJUST_REPEAT_SETTLED 20
	if (!m_bResyncAV)
	{
		frameAdjust = 0;
	}
	else if (m_bPesVideo) // no sync correction if there's timing in the MPEG2 stream
	{
		if (!maxFrameAdjust || maxFrameAdjust != m_lastFrameAdjust)
			frameAdjust = m_numFrameAdjustRepeat = 0;
		else
		{
			if (m_numFrameAdjustRepeat < FRAME_ADJUST_REPEAT_SETTLED)
			{
				frameAdjust = 0;
				m_numFrameAdjustRepeat++;
			}
			else // Reset the repeat counter when we do the adjustment
				m_numFrameAdjustRepeat = 0;
		}
		m_lastFrameAdjust = maxFrameAdjust;
		// We only accept frame adjust with VBDVCR if it's the same offset twice in a row
		// and there is an offset
	}

	bool wasteSpace = false;

	LONG scrStep;
	LONG screStep;
	if (targetDTS && nextTargetDTS)
	{
		ULONGLONG dtsDiff = (ULONGLONG) (nextTargetDTS - targetDTS)*300;
		scrStep = (LONG) (dtsDiff / ((packsToWrite + 1) * 300));
		screStep = (LONG) ((dtsDiff / (packsToWrite + 1)) % 300);
	}
	else
	{
		scrStep = (m_frameTicks*numFramesThisGOP) / ((packsToWrite + 1) * 300);
		screStep = ((m_frameTicks*numFramesThisGOP) / (packsToWrite + 1)) % 300;
	}
	// Always write a video pack first, that's what the demux likes to sync on
	bool firstPack = true;
	bool wrotePtsForVPack = false;
	while (packsToWrite > 0)
	{
		scr += scrStep;
		scre += screStep;
		scr += screStep / 300;
		scre %= 300;

DbgLog((LOG_TRACE, 2, TEXT("packsToWrite=%d aPacksToWrite=%d vPacksToWrite=%d m_seqBufOffset=%d"), packsToWrite, aPacksToWrite, vPacksToWrite, m_seqBufOffset));
		// We either write a video or an audio packet, whichever is up next
		if (!firstPack && ((scr >= m_nextASCR && aPacksToWrite > 0) || vPacksToWrite == 0))
		{
DbgLog((LOG_TRACE, 2, TEXT("Writing audio packet out")));
			// We track PTS for audio in units of the sampling frequency so
			// we don't get rounding errors.
			ULONGLONG tempapts = (m_nextAPTS * 90000) / m_audioSampleRate;
			if (usableBuffer <= aBytesToWrite)
			{
				WritePackHeader(scr, scre, 0);
				WritePESAudioPacket(tempapts, usableBuffer, frameAdjust);
				aBytesToWrite -= usableBuffer;
			}
			else
			{
				// Not a full pack of audio data for this GOP.
				if (usableBuffer - aBytesToWrite > 7)
				{
					if (aBytesToWrite > 0)
					{
						WritePackHeader(scr, scre, 0);
						WritePESAudioPacket(tempapts, aBytesToWrite, frameAdjust);
						WritePESPaddingPacket(usableBuffer - aBytesToWrite);
					}
					else if (m_aFrameRem > 0)
					{
						DbgLog((LOG_TRACE, 2, TEXT("Writing audio align packet of %d"), m_aFrameRem));
						m_audioByteGOPExtras = m_aFrameRem;
						WritePackHeader(scr, scre, 0);
						WritePESAudioPacket(0, m_aFrameRem, 0);
						WritePESPaddingPacket(usableBuffer - m_aFrameRem);
					}
					else // we're missing 19 of the used bytes cause we didn't do that header
					{
						if (wasteSpace)
						{
							WritePackHeader(scr, scre, 0);
							WritePESPaddingPacket(usableBuffer + 19);
						}
					}
				}
				else
				{
					WritePackHeader(scr, scre, usableBuffer - aBytesToWrite);
					WritePESAudioPacket(tempapts, aBytesToWrite, frameAdjust);
				}
				aBytesToWrite = 0;
			}
			aPacksToWrite--;
			frameAdjust = 0;
		}
		else
		{
			ULONGLONG currPTS = 0;
			ULONGLONG currDTS = 0;
			while (true)
			{
				// Be sure to eat up ALL of the frames in the buffer you're writing
				DWORD testOffset = m_frameCodeQ[m_frameCodeIdx].offset;
DbgLog((LOG_TRACE, 2, TEXT("testOffset=%d seqBufOffset=%d usableBuffer=%d len=%d wrotePtsForVPack=%d"), testOffset,
	   m_seqBufOffset, usableBuffer, len, wrotePtsForVPack));
				if (testOffset < m_seqBufOffset)
					testOffset += VID_CIRC_SIZE;
				if (testOffset >= m_seqBufOffset &&
					m_seqBufOffset + min(usableBuffer, len) > testOffset)
				{
					// We're writing the next picture start code in this pack
					DWORD currFrameType = PopFrameOffset().frameType;
					ULONGLONG lastPTS = currPTS, lastDTS = currDTS;
DbgLog((LOG_TRACE, 2, TEXT("Popped last frame type=%d"), currFrameType));
					if (lastVFrameType != B_Frame && currFrameType != B_Frame)
					{
						// I-I, I-P, or P-I. It's jumping the B Frame distance
						currVDTS += (m_ipFrameDist*m_frameTicks)/300;
						currDTS = currVDTS;
						currPTS = currDTS + (m_ipFrameDist*m_frameTicks)/300;
					}
					else if (lastVFrameType != B_Frame && currFrameType == B_Frame)
					{
						// I-B, P-B
						currVDTS += m_frameTicks/300;
						currDTS = 0;
						currPTS = currVDTS;
					}
					else if (lastVFrameType == B_Frame && currFrameType != B_Frame)
					{
						// B-I, B-P
						currVDTS += m_frameTicks/300;
						currDTS = currVDTS;
						currPTS = currDTS + (m_ipFrameDist*m_frameTicks)/300;
					}
					else // B to B
					{
						// B-B
						currVDTS += m_frameTicks/300;
						currDTS = 0;
						currPTS = currVDTS;
					}
					lastVFrameType = currFrameType;
					if (!m_bMpegVideo && testOffset > m_seqBufOffset)
					{
						DWORD picAlign = testOffset - m_seqBufOffset;
						DbgLog((LOG_TRACE,2,TEXT("Extra VIDEO Data of %d"), picAlign));
						// Not a full pack of video data for this GOP.
						if (usableBuffer - picAlign > 7)
						{
							WritePackHeader(scr, scre, 0);
							if (wrotePtsForVPack)
								WritePESVideoPacket(0, 0, picAlign);
							else
								WritePESVideoPacket(lastPTS, lastDTS, picAlign);
							WritePESPaddingPacket(usableBuffer - picAlign);
						}
						else
						{
							WritePackHeader(scr, scre, usableBuffer - picAlign);
							if (wrotePtsForVPack)
								WritePESVideoPacket(0, 0, picAlign);
							else
								WritePESVideoPacket(lastPTS, lastDTS, picAlign);
						}
						len -= picAlign;
						scr += scrStep;
						scre += screStep;
						scr += screStep / 300;
						scre %= 300;
						packsToWrite--;
						vPacksToWrite--;
					}
					wrotePtsForVPack = false;
				}
				else
					break;
			}
			// ONLY_I_FRAME_PTS
			if (m_bMpegVideo)
			{
				currPTS=currDTS=0;
			}
/*#ifdef ONLY_IP_FRAME_PTS
			if (lastVFrameType == B_Frame)
				currPTS=currDTS=0;
#endif*/
			wrotePtsForVPack = true;
			if (usableBuffer <= len)
			{
				WritePackHeader(scr, scre, 0);
				WritePESVideoPacket(currPTS, currDTS, usableBuffer);
				len -= usableBuffer;
			}
			else
			{
				// Not a full pack of video data for this GOP.
				if (usableBuffer - len > 7)
				{
					if (len > 0)
					{
						WritePackHeader(scr, scre, 0);
						WritePESVideoPacket(currPTS, currDTS, len);
						WritePESPaddingPacket(usableBuffer - len);
					}
					else // we're missing 19 of the used bytes cause we didn't do that header
					{
						if (wasteSpace)
						{
							WritePackHeader(scr, scre, 0);
							WritePESPaddingPacket(usableBuffer - len + 19);
						}
					}
				}
				else
				{
					WritePackHeader(scr, scre, usableBuffer - len);
					WritePESVideoPacket(currPTS, currDTS, len);
				}
				len = 0;
			}
			vPacksToWrite--;
		}

		packsToWrite--;
		firstPack = false;
	}

	/*
	 * IMPORTANT NOTE: FLUSHING THE FILE BUFFERS CAUSES A MAJOR DELAY, DO NOT
	 * DO IT, EVEN ON THE WORKER THREAD. IT LOCKS OUR WHOLE PROCESS WAITING FOR THE DEVICE.
	 */
	//	FlushFileBuffers(m_hFile);

	DbgLog((LOG_TRACE, 2, TEXT("Wrote MPEG Packet aFrameRem=%d nextVDTS=%d"),
		m_aFrameRem, (LONG)m_nextVDTS));

	if (targetDTS && nextTargetDTS)
	{
		ULONGLONG dtsDiff = (ULONGLONG) (nextTargetDTS - targetDTS);
		m_nextVDTS += dtsDiff;
		m_nextVSCR += dtsDiff;
	}
	else
	{
		m_nextVDTS += (m_frameTicks * numFramesThisGOP)/300;
		m_nextVSCR += (m_frameTicks * numFramesThisGOP)/300;
	}
	PopSeqLen();
	m_gopsWritten++;
	if (!m_bMpegVideo)
		m_gopsWritten += (m_gopFrames - 1);

	ASSERT((dw_initialFrameCodeIdx + numFramesThisGOP) % MPEGFRAME_Q_SIZE == m_frameCodeIdx);

	if (m_bDropNextSeq)
	{
		m_bDropNextSeq = false;
		m_wroteSysHdr = false;
		m_bytePos = 0;
		m_spareByte = 0;
		m_bitPos = 0;
	}
	return TRUE;
}

// 14 bytes overhead
void CMpegMux::WritePackHeader(ULONGLONG scr, ULONGLONG scre, ULONG numStuffs)
{
	BufferBits(0x000001BA, 32); // pack_start_code
	BufferBits(0x01, 2);

	BufferBits(scr >> 30, 3); // SCR [32-30]
	BufferBits(1, 1); // marker
	BufferBits((scr & 0x3FFFFFFF) >> 15, 15); // SCR [29-15]
	BufferBits(1, 1); // marker
	BufferBits((scr & 0x7FFF), 15); // SCR [14-0]
	BufferBits(1, 1); // marker
	BufferBits((scre & 0x1FF), 9); // SCRE
	BufferBits(1, 1); // marker
	BufferBits(m_muxRate, 22); // program_mux_rate
	BufferBits(1, 1); // marker
	BufferBits(1, 1); // marker
	BufferBits(0, 5); // reserved
	BufferBits(numStuffs, 3); // no stuffing bytes
	for (ULONG i = 0; i < numStuffs; i++)
		BufferBits(0xFF, 8);
}

// 19 bytes of overhead in this
void CMpegMux::WritePESVideoPacket(ULONGLONG pts, ULONGLONG dts, DWORD len, BOOL bDataAlignment)
{
	BufferBits(0x000001, 24); // packet_start_code_prefix
	BufferBits(m_bMpegVideo ? VIDEO_STREAM : PRIVATE_STREAM_1, 8); // stream_id for video
	BufferBits(len + 13, 16); // pes packet length
	BufferBits(0x02, 2); // 10
	BufferBits(0, 2); // scrambling control
	BufferBits(0, 1); // priority
	BufferBits(bDataAlignment ? 1 : 0, 1); // data alignment
	BufferBits(0, 1); // copyright
	BufferBits(0, 1); // 1-original, 0-copy
	if (pts != 0 && dts != 0)
		BufferBits(0x03, 2); // 3-PTS&DTS, 2-PTS, 0-neither
	else if (pts != 0)
		BufferBits(0x02, 2); // 3-PTS&DTS, 2-PTS, 0-neither
	else 
		BufferBits(0x00, 2); // 3-PTS&DTS, 2-PTS, 0-neither
	BufferBits(0, 6); // ESCR, ES_Rate, DSM, copy info, CRC, ext
	BufferBits(10, 8); // header data length
	// write pts and dts
	if (pts != 0)
	{
		BufferBits((dts != 0) ? 0x03 : 0x02, 4);
		BufferBits(pts >> 30, 3); // PTS [32-30]
		BufferBits(1, 1);
		BufferBits((pts & 0x3FFFFFFF) >> 15, 15); // PTS [29-15]
		BufferBits(1, 1); // marker
		BufferBits((pts & 0x7FFF), 15); // PTS [14-0]
		BufferBits(1, 1); // marker
		if (dts != 0)
		{
			BufferBits(0x01, 4);
			BufferBits(dts >> 30, 3); // DTS [32-30]
			BufferBits(1, 1);
			BufferBits((dts & 0x3FFFFFFF) >> 15, 15); // DTS [29-15]
			BufferBits(1, 1); // marker
			BufferBits((dts & 0x7FFF), 15); // DTS [14-0]
			BufferBits(1, 1); // marker
		}
		else
		{
			memset(m_buffy + m_bytePos, 0xFF, 5);
			m_bytePos += 5;
		}
	}
	else
	{
		memset(m_buffy + m_bytePos, 0xFF, 10);
		m_bytePos += 10;
	}
	WriteBuff(m_bytePos);

	if (m_seqBufOffset + len <= VID_CIRC_SIZE)
		Write(m_seqBuf + m_seqBufOffset, len);
	else
	{
		Write(m_seqBuf + m_seqBufOffset, VID_CIRC_SIZE - m_seqBufOffset);
		Write(m_seqBuf, len - (VID_CIRC_SIZE - m_seqBufOffset));
	}
	
	{
		// The only thing shared that can be modified elsewhere is the length
		CAutoLock lock(&m_videoLock);
		m_seqBufLen -= len;
		m_seqBufOffset = (m_seqBufOffset + len) % VID_CIRC_SIZE;
	}
}

void CMpegMux::WritePESAudioPacket(ULONGLONG pts, DWORD len, int frameAdjust)
{
	BufferBits(0x000001, 24); // packet_start_code_prefix
	BufferBits(AUDIO_STREAM, 8);
	BufferBits(len + 13, 16); // pes packet length

	// Don't duplicate the PTS if an audio frame is bigger than a packet
	if (pts != 0 && m_lastWrittenAPTS != pts)
	{
		m_lastWrittenAPTS = pts;
	}
	else
		pts = 0;
	BufferBits(0x02, 2); // 10
	BufferBits(0, 2); // scrambling control
	BufferBits(0, 1); // priority
	BufferBits(0, 1); // data alignment
	BufferBits(0, 1); // copyright
	BufferBits(0, 1); // 1-original, 0-copy

	BufferBits((pts == 0) ? 0 : 0x02, 2); // 3-PTS&DTS, 2-PTS, 0-neither
	BufferBits(0, 6); // ESCR, ES_Rate, DSM, copy info, CRC, ext
	
	BufferBits(0x0A, 8); // header data length
	
	// write pts
	if (pts != 0)
	{
		BufferBits(0x02, 4);
		BufferBits(pts >> 30, 3); // PTS [32-30]
		BufferBits(1, 1);
		BufferBits((pts & 0x3FFFFFFF) >> 15, 15); // PTS [29-15]
		BufferBits(1, 1); // marker
		BufferBits((pts & 0x7FFF), 15); // PTS [14-0]
		BufferBits(1, 1); // marker
		memset(m_buffy + m_bytePos, 0xFF, 5);
		m_bytePos += 5;
	}
	else
	{
		memset(m_buffy + m_bytePos, 0xFF, 10);
		m_bytePos += 10;
	}
	
	WriteBuff(m_bytePos);

	DWORD skippedFrames = 0;
	{
		CAutoLock lock(&m_audioLock);

		if (m_audBufLen < len)
			DbgLog((LOG_ERROR, 1, TEXT("UNDERFLOW of audio MPEG-2 data by %d bytes"), len - m_audBufLen));

		bool hasPad = false;
		if (frameAdjust < 0)//bra - brv > 1200) // 350
		{
			// Remove an audio frame, we've got loss of sync. We first need to write out
			// the remainder of the previous audio frame. We then skip the next frame
			// in the sequence 

			if (m_aFrameRem > 0)
			{
				DWORD remLen = m_aFrameRem;
				if (m_audBufOffset + remLen <= AUD_CIRC_SIZE)
					Write(m_audBuf + m_audBufOffset, remLen);
				else
				{
					Write(m_audBuf + m_audBufOffset, AUD_CIRC_SIZE - m_audBufOffset);
					Write(m_audBuf, remLen - (AUD_CIRC_SIZE - m_audBufOffset));
				}
				m_audBufLen -= remLen;
				m_audBufOffset = (m_audBufOffset + remLen) % AUD_CIRC_SIZE;
				m_aBytesWritten += remLen;
				len -= remLen;
				m_aFrameRem = 0;
			}

			LONGLONG totalRecvAdjust = 0;
			while (frameAdjust < 0)
			{
				DbgLog((LOG_TRACE, 1, TEXT("REMOVING audio frame to regain sync")));
				if (m_audBuf[m_audBufOffset] != 0xFF ||
					(m_audBuf[(m_audBufOffset + 1) % AUD_CIRC_SIZE] >> 4) != 0xF)
				{
					DbgLog((LOG_ERROR, 1, TEXT("ERROR Sync Word LOST in MPEG audio after writing REM")));
				}
				hasPad = (m_audBuf[(m_audBufOffset + 2) % AUD_CIRC_SIZE] & 0x02) == 0x02;
				DWORD nextFrameLen = (m_aFrameSize * m_audioBitrate/8) / m_audioSampleRate;
				if (hasPad) nextFrameLen++;

				m_audBufLen -= nextFrameLen;
				m_audBufOffset = (m_audBufOffset + nextFrameLen) % AUD_CIRC_SIZE;
				totalRecvAdjust += nextFrameLen;

				frameAdjust++;
			}
			//m_aBytesRecvd -= totalRecvAdjust;
			m_totalAudioStatAdjust -= totalRecvAdjust;
		}
		else if (frameAdjust > 0)
		{
			// Duplicate an audio frame, we've got loss of sync. We first need to write out
			// the remainder of the previous audio frame. We then duplicate the next frame
			// in the sequence because we don't know where the rem one started.

			if (m_aFrameRem > 0)
			{
				DWORD remLen = m_aFrameRem;
				if (m_audBufOffset + remLen <= AUD_CIRC_SIZE)
					Write(m_audBuf + m_audBufOffset, remLen);
				else
				{
					Write(m_audBuf + m_audBufOffset, AUD_CIRC_SIZE - m_audBufOffset);
					Write(m_audBuf, remLen - (AUD_CIRC_SIZE - m_audBufOffset));
				}
				m_audBufLen -= remLen;
				m_audBufOffset = (m_audBufOffset + remLen) % AUD_CIRC_SIZE;
				m_aBytesWritten += remLen;
				len -= remLen;
				m_aFrameRem = 0;
			}

			LONGLONG totalRecvAdjust = 0;
			// It's always the same frame here we're writing.
			if (m_audBuf[m_audBufOffset] != 0xFF ||
				(m_audBuf[(m_audBufOffset + 1) % AUD_CIRC_SIZE] >> 4) != 0xF)
			{
				DbgLog((LOG_ERROR, 1, TEXT("ERROR Sync Word LOST in MPEG audio after writing REM")));
			}
			hasPad = (m_audBuf[(m_audBufOffset + 2) % AUD_CIRC_SIZE] & 0x02) == 0x02;
			DWORD nextFrameLen = (m_aFrameSize * m_audioBitrate/8) / m_audioSampleRate;
			if (hasPad) nextFrameLen++;

			while (frameAdjust > 0)
			{
				if (nextFrameLen > len)
				{
					// We're going to fill it out with this duplication, so don't do it!
					break;
				}
				DbgLog((LOG_TRACE, 1, TEXT("Inserting DUPLICATE audio frame to regain sync")));

				if (m_audBufOffset + nextFrameLen <= AUD_CIRC_SIZE)
					Write(m_audBuf + m_audBufOffset, nextFrameLen);
				else
				{
					Write(m_audBuf + m_audBufOffset, AUD_CIRC_SIZE - m_audBufOffset);
					Write(m_audBuf, nextFrameLen - (AUD_CIRC_SIZE - m_audBufOffset));
				}

				//m_audBufLen -= nextFrameLen;
				//m_audBufOffset = (m_audBufOffset + nextFrameLen) % AUD_CIRC_SIZE;
				len -= nextFrameLen;
				skippedFrames++;
				totalRecvAdjust += nextFrameLen;
				frameAdjust--;
			}

			//m_aBytesRecvd += totalRecvAdjust;
			m_aBytesWritten += totalRecvAdjust;
			m_totalAudioStatAdjust += totalRecvAdjust;
		}

		if (len > 0)
		{
			if (m_audBufOffset + len <= AUD_CIRC_SIZE)
				Write(m_audBuf + m_audBufOffset, len);
			else
			{
				Write(m_audBuf + m_audBufOffset, AUD_CIRC_SIZE - m_audBufOffset);
				Write(m_audBuf, len - (AUD_CIRC_SIZE - m_audBufOffset));
			}
		
			// Update the PTS for the audio, do this by walking through the
			// frames we just wrote. If we wrote out the sync byte, then we should
			// increment the skippedFrames counter.
			DWORD currFramePos = m_aFrameRem;
			while (currFramePos < len)
			{
				if (m_audBuf[(currFramePos + m_audBufOffset) % AUD_CIRC_SIZE] != 0xFF ||
					(m_audBuf[(currFramePos + m_audBufOffset + 1) % AUD_CIRC_SIZE] >> 4) != 0xF)
				{
					DbgLog((LOG_TRACE, 2, TEXT("ERROR Sync Word LOST in MPEG audio")));
				}
				hasPad = (m_audBuf[(currFramePos + m_audBufOffset + 2) % AUD_CIRC_SIZE] & 0x02) == 0x02;
				currFramePos += (m_aFrameSize * m_audioBitrate/8) / m_audioSampleRate;
				if (hasPad) currFramePos++;
				skippedFrames++;
			}
			m_aFrameRem = currFramePos - len;

			m_audBufLen -= len;
			m_audBufOffset = (m_audBufOffset + len) % AUD_CIRC_SIZE;
		}
	}

	m_nextAPTS += m_aFrameSize * skippedFrames;
	m_nextASCR += (m_aFrameSize * skippedFrames * 90000) / m_audioSampleRate;
	m_aBytesWritten += len;
}

void CMpegMux::BufferBits(ULONGLONG value, unsigned int numBits)
{
	if (numBits <= 8 - m_bitPos)
	{
		m_spareByte |= (unsigned char)((value << (8 - m_bitPos - numBits)) & 0xFF);
		m_bitPos += numBits;
		if (m_bitPos == 8)
		{
			m_buffy[m_bytePos++] = m_spareByte;
			m_spareByte = 0;
			m_bitPos = 0;
		}
		return;
	}
	if (m_bitPos != 0)
	{
		m_spareByte |= (unsigned char)((value >> (numBits - (8 - m_bitPos))) & 0xFF);
		numBits -= (8 - m_bitPos);
		m_buffy[m_bytePos++] = m_spareByte;
		m_spareByte = 0;
		m_bitPos = 0;
	}
	while (numBits >= 8)
	{
		m_buffy[m_bytePos++] = (unsigned char)((value >> (numBits - 8)) & 0xFF);
		numBits -= 8;
	}
	if (numBits > 0)
		BufferBits(value, numBits);
}

HRESULT CMpegMux::WriteBuff(unsigned int numBytes)
{
	if (Write(m_buffy, numBytes) != S_OK)
	{
		DWORD dwErr = GetLastError();
		return HRESULT_FROM_WIN32(dwErr);
	}
	m_bytePos = 0;
    return S_OK;
}

//
// Write
//
// Write stuff to the file
//
HRESULT CMpegMux::Write(PBYTE pbData,LONG lData)
{
	if (!m_bDropNextSeq)
	{
		DWORD dwWritten;
		LONGLONG startSize = (LONGLONG) m_shareInfo.totalWrite;
		if (m_circFileSize > 0)
		{
			startSize = startSize % m_circFileSize;
			if (startSize == 0)
			{
				if (SetFilePointer(m_hFile, 0, NULL, FILE_BEGIN) == INVALID_FILE_SIZE)
				{
					DWORD dwErr = GetLastError();
					return HRESULT_FROM_WIN32(dwErr);
				}
			}
			if (startSize + lData > m_circFileSize)
			{
				// We're going to go over on this write
				DWORD chunk1 = (DWORD)(((LONGLONG)m_circFileSize) - startSize);
				if (!WriteFile(m_hFile,(PVOID)pbData,chunk1,&dwWritten,NULL)) {
					DWORD dwErr = GetLastError();
					return HRESULT_FROM_WIN32(dwErr);
				}
				if (SetFilePointer(m_hFile, 0, NULL, FILE_BEGIN) == INVALID_FILE_SIZE)
				{
					DWORD dwErr = GetLastError();
					return HRESULT_FROM_WIN32(dwErr);
				}
				if (!WriteFile(m_hFile,(PVOID)(pbData + chunk1),(DWORD)(lData - chunk1),&dwWritten,NULL)) {
					DWORD dwErr = GetLastError();
					return HRESULT_FROM_WIN32(dwErr);
				}
			}
			else
			{
				// This write is clear
				if (!WriteFile(m_hFile,(PVOID)pbData,(DWORD)lData,&dwWritten,NULL)) {
					DWORD dwErr = GetLastError();
					return HRESULT_FROM_WIN32(dwErr);
				}
			}
		}
		else
		{
			if (!WriteFile(m_hFile,(PVOID)pbData,(DWORD)lData,&dwWritten,NULL)) {
				DWORD dwErr = GetLastError();
				return HRESULT_FROM_WIN32(dwErr);
			}
		}
		{
			CAutoLock(&(m_shareInfo.shareLock));
			m_shareInfo.totalWrite += lData;
		}
	}
    return S_OK;
}


//
// DllRegisterSever
//
// Handle the registration of this filter
//
STDAPI DllRegisterServer()
{
    return AMovieDllRegisterServer2( TRUE );

} // DllRegisterServer


//
// DllUnregisterServer
//
STDAPI DllUnregisterServer()
{
    return AMovieDllRegisterServer2( FALSE );

} // DllUnregisterServer

