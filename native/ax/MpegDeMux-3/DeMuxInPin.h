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
#ifndef __DEMUXINPUTPIN_H__
#define __DEMUXINPUTPIN_H__

#include <stdio.h>
#include "../../../third_party/Microsoft/MpegDemux/alloc.h"
#include "IMpegDeMux.h"

// s is offset of time, t is "first pts+offset of time"
//the ajust is "s-(t-s)"
#define GROUP_PTS_ADJUST( s, t ) ( s+s-t)


#define AlignLeft(  ll, lAlign ) ( ll & ~(lAlign-1) )                  //trunck
#define AlignRight( ll, lAlign ) ( (ll + (lAlign -1)) & ~(lAlign -1) ) //round up to next boundary

class CDeMuxInPin : public CBaseInputPin
{
	friend class CDeMuxFilter;
public:
    CDeMuxInPin( CDeMuxFilter *pFilter,  HRESULT *phr );
	~CDeMuxInPin( );
    /*  NonDelegating IUnknown methods - we don't support IMemInputPin */
    STDMETHODIMP NonDelegatingQueryInterface(REFIID riid, void ** ppv)
    {
        if (riid == IID_IMemInputPin) {
            return E_NOINTERFACE;
        }
        return CBaseInputPin::NonDelegatingQueryInterface(riid, ppv);
    }

    /*  IPin methods */
    STDMETHODIMP EndOfStream();
    STDMETHODIMP BeginFlush();
    STDMETHODIMP EndFlush();
    //STDMETHODIMP Receive(IMediaSample *pSample);

    /*  Say if we're blocking */
    STDMETHODIMP ReceiveCanBlock()
    {
        return S_FALSE;
    }

	HRESULT BreakConnect();  
	STDMETHODIMP Disconnect();
	STDMETHODIMP NotifyAllocator( IMemAllocator *pAllocator, BOOL bReadOnly );

	// agree an allocator using RequestAllocator - optional
    // props param specifies your requirements (non-zero fields).
    // returns an error code if fail to match requirements.
    // optional IMemAllocator interface is offered as a preferred allocator
    // but no error occurs if it can't be met.
    virtual HRESULT DecideAllocator( IMemAllocator* pAlloc, ALLOCATOR_PROPERTIES * pProps);

    /*  CBasePin methods */
    HRESULT CheckConnect(IPin *pPin);  //  Override to connect to puller
    HRESULT Active();
    HRESULT Inactive();
    HRESULT CheckMediaType(const CMediaType *pmt);
    HRESULT CompleteConnect(IPin *pReceivePin);

    /*  Report filter from reader */
    void NotifyError( HRESULT hr )
    {
        if (FAILED(hr)) {
			DbgLog((LOG_TRACE, 3, TEXT("InputPin got NotifyError :%x"), hr ));
            m_pFilter->NotifyEvent(EC_ERRORABORT, hr, 0);
        }
        EndOfStream();
    };


    /*  Point to our media type */
    CMediaType *MediaType()
    {
        return &m_mt;
    }

	int BlockSize( ) { return m_dwBlockSize; }
	int AllocSize( ) { return m_dwAllocSize; }
	void SetAllocProp ( int nBlockSize, int nAllocBufferSize ) { m_dwBlockSize = nBlockSize; m_dwAllocSize = nAllocBufferSize; };

	//read data length form async reader
    HRESULT Length( ULONGLONG *pLength, ULONGLONG *pAvailableLength );

    /*  Return our async reader */
    IAsyncReader *Reader() {  return m_pReader;   }
	CDeMuxFilter *Filter() {  return (CDeMuxFilter *)m_pFilter;   }

	HRESULT PullSample( BOOL bDiscontinuity, IMediaSample** ppSample );
	HRESULT SetSamplePosRange( ULONGLONG start, ULONGLONG stop );
	HRESULT GetSampleTimeRange( ULONGLONG *pStart, ULONGLONG *pStop );
	ULONGLONG GetCurrentPos(  );
	ULONGLONG GetStopPos(  );
	void SetStopPos( ULONGLONG pos );
	void SetCurrentPos( ULONGLONG pos );
	void SetMaxiumPos( ULONGLONG max );

	void    ContinueSample();

private:
	CCritSec m_AccessLock;	// locks access by client threads
	IAsyncReader*	m_pReader;
	IMemAllocator*  m_pAlloc;
	DWORD			m_dwBlockSize;
	DWORD			m_dwAllocSize;
	DWORD_PTR	    m_dwBufferAlign; 

    //  Get a properly case pointer to our filter
	ULONGLONG tNext,  tCurrent;
	ULONGLONG tStart, tStop;
	ULONGLONG llMaxPos;
	BOOL m_bDiscontinuity;

	//for debug 
	FILE* fd;
	size_t offset;
	void *_sample;
	unsigned long _cksum;

	void open_dump();
	void close_dump();
	void rewind_dump( );
	void dump_data( char* pData, int dwBytes );
	void dump_sample( IMediaSample *pSample );
	unsigned long cksum_sample( IMediaSample *pSample );



};

class CParseReader
{
public:
    /*  Set the position */
    virtual HRESULT Length(LONGLONG *pLength) = 0;
    virtual HRESULT AvailableLength(LONGLONG *pLength) = 0;
    virtual HRESULT SetPointer(LONGLONG) = 0;
    virtual HRESULT Read(PBYTE pbData, DWORD cbData) = 0;
	virtual HRESULT ReadEx(PBYTE pbData, DWORD cbData, DWORD* pcbBytes, 
		                   LONGLONG* pllTimeStart, LONGLONG* pllTimeEnd  ) = 0;
};

/*  Parsing reader from CAsyncReader */
class CParseReaderFromAsync : public CParseReader
{
public:
    CParseReaderFromAsync(IAsyncReader *pRdr) :
        m_pReader(pRdr), m_llPos(0), m_bReadExSupport( TRUE ) 
	{
		unsigned char buf[16];
		DWORD dwBytes;
		LONGLONG llStart, llStop;
		m_bReadExSupport = S_OK == ReadEx(buf, sizeof(buf), &dwBytes, &llStart, &llStop  );
	};

    HRESULT Length(LONGLONG *pLength)
    {
		// JK This is where the call for the available vs total is made
        LONGLONG llAvailable;
        return m_pReader->Length(pLength, &llAvailable);
    }
    HRESULT AvailableLength(LONGLONG *pAvailableLength)
    {
		// JK This is where the call for the available vs total is made
        LONGLONG llTotal;
        return m_pReader->Length(&llTotal, pAvailableLength);
    }
    HRESULT SetPointer(LONGLONG llPos)
    {
        m_llPos = llPos;
        return S_OK;
    }
    HRESULT Read(PBYTE pbData, DWORD cbData)
    {
        HRESULT hr = m_pReader->SyncRead(m_llPos, (LONG)cbData, pbData);
        if (S_OK == hr) {
            m_llPos += cbData;
        }
        return hr;
    }

    HRESULT ReadEx(PBYTE pbData, DWORD cbData, DWORD* pcbBytes, LONGLONG* pllTimeStart, LONGLONG* pllTimeEnd  )
    {
		HRESULT hr=S_OK;
		LONGLONG tCurrent, tNext;
		*pcbBytes = 0;
		*pllTimeStart = 0;
		*pllTimeEnd = 0;

		if ( !m_bReadExSupport )
		{
			if ( !FAILED( hr = Read( pbData, cbData ) ) )
				*pcbBytes = cbData;
			return hr;
		}

		CLocalAllocator LocalAlloc( pbData, cbData, &hr );
		IMediaSample *pSample;
		pSample= LocalAlloc.GetSample();
	
		tCurrent = m_llPos * UNITS;
		tNext = (m_llPos + cbData) * UNITS;
		pSample->SetActualDataLength( 0 );
		pSample->SetDiscontinuity( 1 );
		pSample->SetTime( (LONGLONG*)&tCurrent, (LONGLONG*)&tNext );
		hr = m_pReader->SyncReadAligned( pSample );
        if (S_OK == hr) 
		{
			m_llPos += pSample->GetActualDataLength();
			*pcbBytes = pSample->GetActualDataLength();
			if ( S_OK != pSample->GetMediaTime( pllTimeStart, pllTimeEnd  ) )
			{
				*pllTimeStart = 0;
				*pllTimeEnd = 0;
			}
        } 
		pSample->Release();
        return hr;
    }

private:
    IAsyncReader *m_pReader;
    LONGLONG      m_llPos;
	BOOL		  m_bReadExSupport;


};


#endif
