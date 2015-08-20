//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
//
// Desc: DirectShow sample code - header file for audio oscilloscope filter.
//
// Copyright (c) 1992-2001 Microsoft Corporation. All rights reserved.
//------------------------------------------------------------------------------

// {3F886457-18BD-4a31-8712-0BBDC85FF7D5}
DEFINE_GUID(CLSID_MusicVisFilter, 
0x3f886457, 0x18bd, 0x4a31, 0x87, 0x12, 0xb, 0xbd, 0xc8, 0x5f, 0xf7, 0xd5);


class CMusicVisInputPin;
class CMusicVis;
class CMusicVisFilter;

// Main filter object

class CMusicVisFilter : public CBaseFilter
{
    CMusicVis * const m_pMusicVis;

public:

    // Constructor
    CMusicVisFilter(CMusicVis *pMusicVis,
                LPUNKNOWN pUnk,
                CCritSec *pLock,
                HRESULT *phr);

    // Pin enumeration
    CBasePin * GetPin(int n);
    int GetPinCount();
};


//  Pin object

class CMusicVisInputPin : public CRenderedInputPin
{
    CMusicVis    * const m_pMusicVis;           // Main renderer object
    CCritSec * const m_pReceiveLock;    // Sample critical section

	int m_nChans;
	int m_nBits;
	int m_nBlockAlign;
	int alreadyDone;

public:

    CMusicVisInputPin(CMusicVis *pMusicVis,
                  LPUNKNOWN pUnk,
                  CBaseFilter *pFilter,
                  CCritSec *pLock,
                  CCritSec *pReceiveLock,
                  HRESULT *phr);

    // Do something with this media sample
    STDMETHODIMP Receive(IMediaSample *pSample);
    STDMETHODIMP EndOfStream(void);
    STDMETHODIMP ReceiveCanBlock();

    // Check if the pin can support this specific proposed type and format
    HRESULT CheckMediaType(const CMediaType *);

	// Get the audio format parameters here
	HRESULT SetMediaType(const CMediaType *pmt);
};


//  CMusicVis object which has filter and pin members

class CMusicVis : public CUnknown, public IMusicVis
{
    friend class CMusicVisFilter;
    friend class CMusicVisInputPin;

    CMusicVisFilter *m_pFilter;         // Methods for filter interfaces
    CMusicVisInputPin *m_pPin;          // A simple rendered input pin
    CCritSec m_Lock;                // Main renderer critical section
    CCritSec m_ReceiveLock;         // Sublock for received samples

public:

    DECLARE_IUNKNOWN

    CMusicVis(LPUNKNOWN pUnk, HRESULT *phr);
    ~CMusicVis();

    static CUnknown * WINAPI CreateInstance(LPUNKNOWN punk, HRESULT *phr);

	STDMETHODIMP put_MusicVisData(MusicVisData*);

private:

    // Overriden to say what interfaces we support where
    STDMETHODIMP NonDelegatingQueryInterface(REFIID riid, void ** ppv);

	MusicVisData* m_pMusicVisData;
};

