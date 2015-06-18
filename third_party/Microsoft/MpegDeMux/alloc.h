//==========================================================================;
// Copyright 2015 The SageTV Authors. All Rights Reserved.
//
//  THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY
//  KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
//  IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A PARTICULAR
//  PURPOSE.
//
//  Copyright (c) 1996 - 1998  Microsoft Corporation.  All Rights Reserved.
//
//--------------------------------------------------------------------------;
/*  Allocator for sequential buffers
    Like CBaseAllocator BUT always allocates the next buffer
*/
#ifndef __ALLOC_H__
#define __ALLOC_H__


typedef CMediaSample *LPCMEDIASAMPLE;

class CSequentialAllocator : public CMemAllocator
{
public:
    CSequentialAllocator(
        LPUNKNOWN  pUnk,
        HRESULT   *phr,
		LPCWSTR	  pName
    );

    ~CSequentialAllocator();

	// Called to get a media sample buffer to use
    STDMETHODIMP GetBuffer(
        IMediaSample **ppBuffer,
        REFERENCE_TIME * pStartTime,
        REFERENCE_TIME * pEndTime,
        DWORD dwFlags);

	// Called to get a media sample buffer to use
    STDMETHODIMP GetBuffer0(
        IMediaSample **ppBuffer,
        REFERENCE_TIME * pStartTime,
        REFERENCE_TIME * pEndTime,
        DWORD dwFlags);

	STDMETHODIMP ReleaseBuffer(IMediaSample * pSample);

	// Allocates all of the memory needed for this allocator
    HRESULT Alloc();

    /*  Get buffer index */
    int BufferIndex(PBYTE pbBuffer);

    /*  Given an address get the IMediaSample pointer -
        NB needs optimizing
    */
    CMediaSample *SampleFromBuffer(PBYTE pBuffer);

    /*  Open buffer to be consumed */
    void OpenBuffer(CMediaSample *pSample);

    /*  Close buffer that is used up  */
    HRESULT CloseBuffer(LONG lAdvance);

    /*  Get the valid part */
    PBYTE GetOpenBufferData(LONG *plValid);

    /*  Wrap end to go back to start */
    HRESULT Wrap(void);

    /*  Flush the allocator - just discard all the data in it */
    void Flush();

	void Stop();
	void Run();

	long SafeRelease( IMediaSample * pSample );
	long SafeAddRef( IMediaSample * pSample );  
	BOOL IsBufferEmpty( ) { return m_lFree.GetCount() >= m_lCount; }

	int      FreeBufferNum( )  { long Num; GetFreeCount( &Num ); return Num; }; //{ return m_lFree.GetCount(); };
	int	     InUsedBufferNum( ){ return  m_lAllocated - m_lFree.GetCount() ; }; 
	unsigned traceWait( ) { return trace_wait; };
	unsigned traceSemaphore( ) { return trace_semaphore; };
	unsigned traceRelease( )   { return trace_release; };
	unsigned getWaitingNum( )  { return m_lWaiting; };
	unsigned trace_wait;
	unsigned trace_semaphore;
	unsigned trace_release;

	DWORD m_dwWaitCounter; //for performance testing

private:
	WCHAR		   *m_pName;
    PBYTE           m_pbNext;
    LPCMEDIASAMPLE *m_parSamples;
	LONG			m_dwTotalSize;

    /*  Simple wrap around buffer stuff */
    LONG            m_lValid;
    PBYTE           m_pbStartValid;
    PBYTE           m_pBuffer;  /* Copy of CMemAllocator's which is private */

	BOOL			m_bStopAlloc;
	int				m_uFailedNum;

	BOOL IsFreeSample( IMediaSample * pSample ); //debug use
	BOOL IsAllSampleFree( ); //debug use

};

class CDeMuxFilter;
/*  Allocator for subsamples that shares CSequentialAllocator*/
class CSubAllocator : public CBaseAllocator
{
public:
    CSubAllocator(  LPUNKNOWN  pUnk, HRESULT *phr, CSequentialAllocator *pAlloc );
	~CSubAllocator( );

	CMediaSample *GetSample(PBYTE pbData, DWORD dwLen);

	STDMETHODIMP ReleaseBuffer(IMediaSample * pSample);

    /*  Must override Free() */
    void Free() {}

	void SetupDeMuxerFilter( CDeMuxFilter* pDeMuxFilter ) { m_pDeMuxFilter = pDeMuxFilter; }

private:
    CSequentialAllocator *const m_pAlloc;

	CDeMuxFilter* m_pDeMuxFilter;
};

class CLocalAllocator : public CBaseAllocator
{
public:
    CLocalAllocator( PBYTE &pbBufer, int cbBuffer, HRESULT   *phr);
    ~CLocalAllocator( );

	CMediaSample *GetSample();
	STDMETHODIMP ReleaseBuffer(IMediaSample * pSample);
	void Free( ) { };

	PBYTE m_pBuffer;
	DWORD m_dwSize;
};

#endif
