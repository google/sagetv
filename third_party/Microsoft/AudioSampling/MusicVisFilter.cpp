//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
//
// Desc: DirectShow sample code - source file for audio oscilloscope filter.
//
// Copyright (c) 1992-2001 Microsoft Corporation. All rights reserved.
//------------------------------------------------------------------------------

#include <streams.h>
#include <commctrl.h>
#include <mmsystem.h>
#include <initguid.h>
#include <wxdebug.h>
#include "../../../native/include/IMusicVis.h"
#include "MusicVisFilter.h"
//#include "../../../native/include/guids.h"


// Setup data

const AMOVIESETUP_MEDIATYPE sudPinTypes =
{
    &MEDIATYPE_Audio,           // Major type
    &MEDIASUBTYPE_NULL          // Minor type
};


const AMOVIESETUP_PIN sudPins  =
{
    L"Input",                   // Pin string name
    FALSE,                      // Is it rendered
    FALSE,                      // Is it an output
    FALSE,                      // Allowed zero pins
    FALSE,                      // Allowed many
    &CLSID_NULL,                // Connects to filter
    L"Output",                  // Connects to pin
    1,                          // Number of pins types
    &sudPinTypes } ;            // Pin information


const AMOVIESETUP_FILTER sudMusicVisFilter =
{
    &CLSID_MusicVisFilter,               // Filter CLSID
    L"SageTV Music Visualization",            // String name
    MERIT_DO_NOT_USE,           // Filter merit
    1,                          // Number pins
    &sudPins                    // Pin details
};


// List of class IDs and creator functions for class factory

CFactoryTemplate g_Templates []  = {
    { L"SageTV Music Visualization"
    , &CLSID_MusicVisFilter
    , CMusicVis::CreateInstance
    , NULL
    , &sudMusicVisFilter }
};
int g_cTemplates = sizeof(g_Templates) / sizeof(g_Templates[0]);


//
// CreateInstance
//
// This goes in the factory template table to create new instances
//
CUnknown * WINAPI CMusicVis::CreateInstance(LPUNKNOWN pUnk, HRESULT *phr)
{
    return new CMusicVis(pUnk, phr);

} // CreateInstance


//
// Constructor
//
//
CMusicVisFilter::CMusicVisFilter(CMusicVis *pMusicVis,
                         LPUNKNOWN pUnk,
                         CCritSec *pLock,
                         HRESULT *phr) :
    CBaseFilter(NAME("CMusicVisFilter"), pUnk, pLock, CLSID_MusicVisFilter),
    m_pMusicVis(pMusicVis)
{
}

CMusicVis::CMusicVis(LPUNKNOWN pUnk, HRESULT *phr) :
    CUnknown(NAME("CMusicVis"), pUnk),
    m_pFilter(NULL),
    m_pPin(NULL)
{
    m_pFilter = new CMusicVisFilter(this, GetOwner(), &m_Lock, phr);
    if (m_pFilter == NULL) {
        *phr = E_OUTOFMEMORY;
        return;
    }

    m_pPin = new CMusicVisInputPin(this,GetOwner(),
                               m_pFilter,
                               &m_Lock,
                               &m_ReceiveLock,
                               phr);
    if (m_pPin == NULL) {
        *phr = E_OUTOFMEMORY;
        return;
    }
	m_pMusicVisData = NULL;
}

//
// Destructor
//
CMusicVis::~CMusicVis()
{
    delete m_pPin;
    delete m_pFilter;

} // (Destructor)

// NonDelegatingQueryInterface
//
// Override this to say what interfaces we support where
//
STDMETHODIMP CMusicVis::NonDelegatingQueryInterface(REFIID riid, void ** ppv)
{
    CheckPointer(ppv,E_POINTER);

    // Do we have this interface

    if (riid == IID_IMusicVis) {
        return GetInterface((IMusicVis *) this, ppv);
    } 
    else if (riid == IID_IBaseFilter || riid == IID_IMediaFilter || riid == IID_IPersist) {
	    return m_pFilter->NonDelegatingQueryInterface(riid, ppv);
    } 

    return CUnknown::NonDelegatingQueryInterface(riid, ppv);

} // NonDelegatingQueryInterface

//
// GetPinCount
//
// Return the number of input pins we support
//
int CMusicVisFilter::GetPinCount()
{
    return 1;

} // GetPinCount


//
// GetPin
//
// Return our single input pin - not addrefed
//
CBasePin *CMusicVisFilter::GetPin(int n)
{
    // We only support one input pin and it is numbered zero

    ASSERT(n == 0);
    if (n != 0) {
        return NULL;
    }
    return m_pMusicVis->m_pPin;

} // GetPin



//
// Constructor
//
CMusicVisInputPin::CMusicVisInputPin(CMusicVis *pMusicVis,
                             LPUNKNOWN pUnk,
                             CBaseFilter *pFilter,
                             CCritSec *pLock,
                             CCritSec *pReceiveLock,
                             HRESULT *phr) :

    CRenderedInputPin(NAME("CMusicVisInputPin"),
                  pFilter,                   // Filter
                  pLock,                     // Locking
                  phr,                       // Return code
                  L"Input"),                 // Pin name
    m_pReceiveLock(pReceiveLock),
    m_pMusicVis(pMusicVis)
{
}
// (Constructor)


//
// CheckMediaType
//
// Check if the pin can support this specific proposed type and format
//
HRESULT CMusicVisInputPin::CheckMediaType(const CMediaType *)
{
    return S_OK;
}


//
// ReceiveCanBlock
//
// We don't hold up source threads on Receive
//
STDMETHODIMP CMusicVisInputPin::ReceiveCanBlock()
{
    return S_FALSE;
}

//
// EndOfStream
//
STDMETHODIMP CMusicVisInputPin::EndOfStream(void)
{
	// We can't just pass this through or it'll terminate the graph early with an EC_COMPLETE
	return S_OK;
//    CAutoLock lock(m_pReceiveLock);
  //  return CRenderedInputPin::EndOfStream();

} // EndOfStream


//
// SetMediaType
//
// Actually set the format of the input pin
//
HRESULT CMusicVisInputPin::SetMediaType(const CMediaType *pmt)
{
    HRESULT hr = CBaseInputPin::SetMediaType(pmt);
    if (SUCCEEDED(hr)) {

        WAVEFORMATEX *pwf = (WAVEFORMATEX *) pmt->Format();

		m_nChans = pwf->nChannels;
		m_nBits = pwf->wBitsPerSample;
		m_nBlockAlign = pwf->nBlockAlign;
		alreadyDone = 0;
    }
    return hr;

} // SetMediaType


//
// Receive
//
// Here's the next block of data from the stream
//
HRESULT CMusicVisInputPin::Receive(IMediaSample * pSample)
{
    // Lock this with the filter-wide lock
    CAutoLock lock(m_pReceiveLock);

    // Check all is well with the base class
    HRESULT hr = CBaseInputPin::Receive(pSample);
    if (FAILED(hr)) {
        return hr;
    }

	// We want to get it in spurts
    // Send the sample to the video window object for rendering
	if (m_pMusicVis->m_pMusicVisData && pSample->GetActualDataLength())
	{
		MusicVisData* pmvd = m_pMusicVis->m_pMusicVisData;
		int currBuffer = pmvd->lastWritten;
		if (alreadyDone >= 1024)
		{
			alreadyDone = 0;
			currBuffer++;
		}
		currBuffer %= pmvd->numBufs;

		DbgLog((LOG_TRACE, 2, TEXT("Visualization has a pointer to data array.")));
		PBYTE pbData;
		if (SUCCEEDED(pSample->GetPointer(&pbData)))
		{
			REFERENCE_TIME tStart, tStop;
			pSample->GetTime (&tStart,&tStop);
			pmvd->times[currBuffer] = tStart + m_tStart;
			int currBufferOffset = currBuffer * 2048;
			LONG actLen = max(0, pSample->GetActualDataLength());
			int  nBytes;
			int  nSamplesPerChan;
			int idx = 0;
			nBytes = pSample->GetActualDataLength();
			nSamplesPerChan = nBytes / (m_nChans * m_nBlockAlign);
			DbgLog((LOG_TRACE, 2, TEXT("Visualization has a pointer to music array sampsPerChan=%d"), nSamplesPerChan));
			switch (m_nBits + m_nChans)
			{
				BYTE * pb;
				WORD * pw;

				case 9:
					{   // Mono, 8-bit
						pb = pbData;
						while (idx < nSamplesPerChan && alreadyDone < 1024) 
						{
							int x = (int)*pb++ - 127;
							pmvd->ampData[currBufferOffset+alreadyDone] = abs(x);  // Make zero centered
							pmvd->ampData[currBufferOffset+alreadyDone + 1024] = 
								pmvd->ampData[currBufferOffset+alreadyDone];
							idx++;
							alreadyDone++;
						}
					}
					break;

				case 10:
					{   // Stereo, 8-bit
						pb = pbData;
						while (idx < nSamplesPerChan && alreadyDone < 1024) 
						{
							int x = (int)*pb++ - 127;
							pmvd->ampData[currBufferOffset+alreadyDone] = abs(x);  // Make zero centered
							x = (int)*pb++ - 127;
							pmvd->ampData[currBufferOffset+alreadyDone + 1024] = abs(x);  // Make zero centered
							idx++;
							alreadyDone++;
						}
					}
					break;

				case 17:
					{ // Mono, 16-bit
						pw = (WORD *) pbData;
						while (idx < nSamplesPerChan && alreadyDone < 1024) 
						{
							int x = (int) ((short) *pw++) / 256;
							pmvd->ampData[currBufferOffset+alreadyDone] = abs(x);
							pmvd->ampData[currBufferOffset+alreadyDone + 1024] = 
								pmvd->ampData[currBufferOffset+alreadyDone];
							idx++;
							alreadyDone++;
						}
					}
					break;

				case 18:
					{ // Stereo, 16-bit
						pw = (WORD *)pbData;
						while (idx < nSamplesPerChan && alreadyDone < 1024) 
						{
							int x = (int) ((short) *pw++) / 256;
							pmvd->ampData[currBufferOffset+alreadyDone] = abs(x);
							x = (int) ((short) *pw++) / 256;
							pmvd->ampData[currBufferOffset+alreadyDone + 1024] = abs(x);
							idx++;
							alreadyDone++;
						}
					}
					break;

			} // End of format switch
			pmvd->lastWritten = currBuffer;
		}
	}
    return hr;//m_pFilter->m_Window.Receive(pSample);

} // Receive




//
// DllRegisterServer
//
// Handles DLL registry
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

STDMETHODIMP CMusicVis::put_MusicVisData(MusicVisData* pMusicVisData)
{
	m_pMusicVisData = pMusicVisData;
	return S_OK;
}

