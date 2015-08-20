//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: AsyncRdr.h
//
// Desc: DirectShow sample code - base library for I/O functionality.
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

#ifndef _FILE_SOURCE_FILTER_H_
#define _FILE_SOURCE_FILTER_H_

#include "../../../native/include/isharedasync.h"
#include "../../../native/ax/FileSource/StreamData.h"

void _flog( int type, int level, const char* cstr, ... );

class CAsyncIo;
class CFileSourceFilter;
class CPTSParser;
class CFileSourceOutpin : public CBasePin, public IAsyncReader
{
	friend class FileSourceFilter;
public:
    CFileSourceOutpin( HRESULT * phr, CFileSourceFilter *pFSF, CCritSec * pLock);

    ~CFileSourceOutpin();

    DECLARE_IUNKNOWN
    STDMETHODIMP NonDelegatingQueryInterface(REFIID, void**);


    // --- IAsyncReader methods ---
    STDMETHODIMP RequestAllocator( IMemAllocator* pPreferred,
				                   ALLOCATOR_PROPERTIES* pProps,
								   IMemAllocator ** ppActual);
    STDMETHODIMP Request( IMediaSample* pSample, DWORD dwUser);	
    STDMETHODIMP WaitForNext( DWORD dwTimeout, IMediaSample** ppSample, DWORD * pdwUser);		
    STDMETHODIMP SyncReadAligned( IMediaSample* pSample );
    STDMETHODIMP SyncRead( LONGLONG llPosition,	LONG lLength, BYTE* pBuffer);	
    STDMETHODIMP Length( LONGLONG* pTotal, LONGLONG* pAvailable);
    STDMETHODIMP Length( LONGLONG* pTotal, LONGLONG* pAvailable, LONGLONG* pOverwritten);

    STDMETHODIMP Connect( IPin * pReceivePin, const AM_MEDIA_TYPE *pmt );
	HRESULT GetMediaType(int iPosition, CMediaType *pMediaType);
	HRESULT CheckMediaType(const CMediaType* pType);
    HRESULT CheckConnect(IPin *pPin)
    {
        return CBasePin::CheckConnect(pPin);
    }

    HRESULT CompleteConnect(IPin *pReceivePin)
    {
		return CBasePin::CompleteConnect(pReceivePin);
    }


    HRESULT BreakConnect()
    {
        return CBasePin::BreakConnect();
    }

    const CMediaType *LoadType() const
    {
        return &m_mt;
    }

   STDMETHODIMP BeginFlush(void);
   STDMETHODIMP EndFlush(void);

   void Init( CAsyncStream* pStream );

protected:
	 CAsyncIo*			m_pAsyncIO;
	 CFileSourceFilter* m_pFilter;

private:
	HRESULT InitAllocator(IMemAllocator **ppAlloc);
};

class CFileSourceFilter : public CBaseFilter,  public IFileSourceFilter, 
	  public ISharedAsyncFile, public ISpecifyPropertyPages
{
public:
	
	CFileSourceFilter( LPUNKNOWN pUnk, HRESULT *phr );
	~CFileSourceFilter( );

	static CUnknown * WINAPI CreateInstance(LPUNKNOWN punk, HRESULT *phr);

    DECLARE_IUNKNOWN

    STDMETHODIMP NonDelegatingQueryInterface(REFIID riid, void **ppv);

    /*  IFileSourceFilter methods */
	STDMETHODIMP Load(LPCOLESTR lpwszFileName, const AM_MEDIA_TYPE *pmt) ;
    STDMETHODIMP GetCurFile(LPOLESTR * ppszFileName, AM_MEDIA_TYPE *pmt) ;

	/* ISharedAsyncFile */
	STDMETHODIMP put_ShareInfo( ShareInfo* sharin );
	STDMETHODIMP put_CircularSize( long circSize );
	STDMETHODIMP put_RemoteHostname( LPCSTR pHostname );
	HRESULT ReOpenConnection();
	STDMETHODIMP Reset();
	STDMETHODIMP LoadEx( DWORD dwSerial, LPCOLESTR lpwszFileName, const AM_MEDIA_TYPE *pmt) ;
	STDMETHODIMP GetFileName( DWORD dwSerial, WCHAR* pFileName, DWORD dwSizeOfFileName  ); 
	STDMETHODIMP GetFileInf( DWORD dwSerial,  void* pInfo, DWORD dwSizeOfInfo ) ;

	// ISpecifyPropertyPages methods
	STDMETHODIMP GetPages(CAUUID *pPages);

    int GetPinCount();
    CBasePin *GetPin(int n);

    // --- Access our media type
    const CMediaType *LoadType() const
    {
        return &m_mt;
    }

    virtual HRESULT Connect( IPin * pReceivePin, const AM_MEDIA_TYPE *pmt )/*optional media type*/
    {
        return m_OutputPin.CBasePin::Connect(pReceivePin, pmt);
    }

	LONGLONG GetTotalWrite(LPWSTR pwFileName);

protected:
	CCritSec		m_csFilter;
	CMediaType		m_mt;
	CMediaStream*	m_pStream;
	CFileSourceOutpin m_OutputPin;
	LPTSTR			m_pHostname;
	ShareInfo*		m_pShareInfo;
	DWORD			m_dwCircFileSize;

private:
	//CPTSParser*	m_pPTSParser;
	BOOL		m_skipID3Headers;
};
 
#endif