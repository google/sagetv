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
#include "../../../third_party/Microsoft/StrmDeMux/alloc.h"
#include "ISDeMux.h"

#define	BUFFER_BLOCK_SIZE 32*1024
#define	BUFFER_BLOCK_NUM 2

#define  END_OF_DATA  0x01

class CSDeMuxInPin : public CBaseInputPin
{
	friend class CSDeMuxFilter;
public:
    CSDeMuxInPin( CSDeMuxFilter *pFilter,  HRESULT *phr );
	~CSDeMuxInPin( );
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


	//read data length form async reader
    HRESULT Length( ULONGLONG *pLength, ULONGLONG *pAvailableLength );

    /*  Return our async reader */
    IAsyncReader *Reader() {  return m_pReader;   }
	CSDeMuxFilter *Filter() {  return (CSDeMuxFilter *)m_pFilter;   }

	HRESULT PullSample( BOOL bDiscontinuity, IMediaSample* ppSample );
	HRESULT GetSampleTimeRange( ULONGLONG *pStart, ULONGLONG *pStop );
	ULONGLONG GetCurrentPos(  );
	ULONGLONG GetStopPos(  );
	ULONGLONG GetLength( );
	ULONGLONG GetActualLength( );
	void SetStopPos( ULONGLONG pos );
	void SetCurrentPos( ULONGLONG pos );

	int  ReadSampleData( unsigned char* pBuffer, int nBufferSize );
	int  CheckPullSampeState();
	void ResetPullSampeSate();

	void    ContinueSample();

private:
	CCritSec m_AccessLock;	// locks access by client threads
	IAsyncReader*	m_pReader;
	int				m_nPullState;

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


#endif
