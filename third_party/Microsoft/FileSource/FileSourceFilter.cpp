//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: AsyncRdr.cpp
//
// Desc: DirectShow sample code - base library with I/O functionality.
//       This file implements I/O source filter methods and output pin 
//       methods for CAsyncReader and CAsyncOutputPin.
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

#pragma warning(disable : 4996)

#include <streams.h>
#include <initguid.h>   // Make our GUID get defined
#include <stdio.h>
#include <time.h>
#include "jni.h"
#include "strconv.h"
#include "../../../native/ax/FileSource/DebugLog.h"

#pragma warning(disable:4710)  // 'function' not inlined (optimization)
#include "../../../native/include/isharedasync.h"
#include "AsyncIO.h"
#include "../../../native/ax/FileSource/StreamType.h"
#include "FileSourceFilter.h"
#include "../../../native/ax/FileSource/FileStreamData.h"
#include "../../../native/ax/FileSource/NetStreamData.h"
#include "../../../native/ax/FileSource/FilterProperties.h"

/////////////////////////////////////////////////////////////////////////////////


extern "C" 
{
	extern void _flog_setup( char* filename, int enable );
}

static int flog_enabled = 0;
static int _debug_level = DEBUG_LEVEL;
static void _flog_check()
{
	FILE* fp = fopen( "FILE_SOURCE.ENABLE", "r" );
	if ( fp != NULL )
	{
		char buf[80];
		int level = 0;
		flog_enabled = true;
		fread( buf, 1, sizeof(buf), fp );
		level = atoi(buf);
		if ( level > 0 )
			_debug_level = level;
		fclose( fp );
		_flog( 0, 0, "<----------- SageTV File Source ---------->" );
		_flog_setup( "FileSource.log", 1 );

	} else
	{
		_flog_setup( "FileSource.log", 0 );
	}
}
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}

void _flog( int type, int level, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	 
	if ( !flog_enabled ) return;
	if ( level > _debug_level ) return;

	fp = fopen( "FileSource.log", "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );


    TCHAR szInfo[1024*2];
    va_list va;
    va_start(va, cstr);

	wsprintf( szInfo, _TEXT("%02d/%02d/%d %02d:%02d:%02d (tid %06x) "), ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
			ltm.tm_hour, ltm.tm_min, ltm.tm_sec, GetCurrentThreadId() );  

    _vstprintf_s(szInfo + lstrlen(szInfo), sizeof(szInfo)-lstrlen(szInfo), cstr, va);
    lstrcat(szInfo, TEXT("\r\n"));
	fwrite( (wchar_t*)szInfo, 1, lstrlen(szInfo),  fp ); 
	

	va_end(args);
	fclose( fp );
}

/////////////////////////////////////////////////////////////////////////////////
DEFINE_GUID( CLSID_SharedFileSource,
0xc55377a7, 0x1526, 0x11da, 0xb4, 0xd1, 0x00, 0x89, 0x5f, 0x6c, 0xbb, 0x64);

DEFINE_GUID( CLSID_FileSourceProperties,
0xc55377a8, 0x1526, 0x11da, 0xb4, 0xd1, 0x00, 0x89, 0x5f, 0x6c, 0xbb, 0x64);

#define MIN_FILE_SIZE 50000
#define NUM_LOOKS_FOR_DATA 100
#define WAIT_BETWEEN_LOOKS 100

//
// Setup data for filter registration
//
const AMOVIESETUP_MEDIATYPE FilterOpPinTypes =
{ &MEDIATYPE_Stream     // clsMajorType
, &MEDIASUBTYPE_NULL }; // clsMinorType

const AMOVIESETUP_PIN FilterOpPin =
{ L"output"          // strName
, FALSE              // bRendered
, TRUE               // bOutput
, FALSE              // bZero
, FALSE              // bMany
, &CLSID_NULL        // clsConnectsToFilter
, L"input"           // strConnectsToPin
, 1                  // nTypes
, &FilterOpPinTypes };  // lpTypes

const AMOVIESETUP_FILTER FileSource =
{ &CLSID_SharedFileSource              // clsID
, L"SageTV FileSource"			// strName
, MERIT_UNLIKELY                  // dwMerit
, 1                               // nPins
, &FilterOpPin };                    // lpPin


//
//  Object creation template
//
CFactoryTemplate g_Templates[2] = {
    { L"SageTV FileSource", &CLSID_SharedFileSource, CFileSourceFilter::CreateInstance, NULL, &FileSource },
	{ L"FileSource Properties", &CLSID_FileSourceProperties, CFilterProperties::CreateInstance, NULL, NULL }
};

int g_cTemplates = sizeof(g_Templates) / sizeof(g_Templates[0]);

//
// Filter registration functions
//
STDAPI  DllRegisterServer()
{
    return AMovieDllRegisterServer2(TRUE);
}

STDAPI  DllUnregisterServer()
{
    return AMovieDllRegisterServer2(FALSE);
}

/* Create a new instance of this class */
CUnknown *CFileSourceFilter::CreateInstance(LPUNKNOWN pUnk, HRESULT *phr)
{
    /*  DLLEntry does the right thing with the return code and
        returned value on failure
    */
	_flog_check();
	DbgLog((LOG_TRACE, 3, TEXT("FileSource ver 1.0.1 created.")) );
    return new CFileSourceFilter(pUnk, phr);
}



///////////////////////////////////////////////////////////////////
CFileSourceOutpin::CFileSourceOutpin( HRESULT * phr, CFileSourceFilter *pFSF, CCritSec * pLock):
CBasePin( NAME("FileSource output pin"), (CBaseFilter*)pFSF, pLock, phr, L"output",	PINDIR_OUTPUT ),
m_pAsyncIO( NULL ),
m_pFilter( pFSF )
{
}

CFileSourceOutpin::~CFileSourceOutpin()
{
	if ( m_pAsyncIO != NULL )
		delete m_pAsyncIO;
}

void CFileSourceOutpin::Init( CAsyncStream* pStream )
{
	if ( m_pAsyncIO != NULL )
		delete m_pAsyncIO;

	m_pAsyncIO = new CAsyncIo( (CAsyncStream*)pStream );
}

STDMETHODIMP CFileSourceOutpin::BeginFlush(void)
{
	if ( m_pAsyncIO != NULL )
		m_pAsyncIO->BeginFlush();
	return S_OK;
}

STDMETHODIMP CFileSourceOutpin::EndFlush(void)
{
	if ( m_pAsyncIO != NULL )
		m_pAsyncIO->EndFlush();
	return S_OK;
}

STDMETHODIMP CFileSourceOutpin::Connect( IPin * pReceivePin, const AM_MEDIA_TYPE *pmt ) 
{  
	return CBasePin::Connect( pReceivePin, pmt ); 
}

HRESULT CFileSourceOutpin::GetMediaType(int iPosition, CMediaType *pMediaType) 
{ 
   if (iPosition < 0) {
    	return E_INVALIDARG;
    }
    if (iPosition > 0) {
    	return VFW_S_NO_MORE_ITEMS;
    }

    *pMediaType = *LoadType();
    return S_OK;
};

HRESULT CFileSourceOutpin::CheckMediaType(const CMediaType* pType) 
{ 
   CAutoLock lck(m_pLock);

    /*  We treat MEDIASUBTYPE_NULL subtype as a wild card */
    if ((LoadType()->majortype == pType->majortype) &&
		(LoadType()->subtype == MEDIASUBTYPE_NULL ||
         LoadType()->subtype == pType->subtype)) {
	    return S_OK;
    }
    return S_FALSE;
};


STDMETHODIMP CFileSourceOutpin::NonDelegatingQueryInterface(REFIID riid, void** ppv)
{
    CheckPointer(ppv,E_POINTER);

    if (riid == IID_IAsyncReader) {
    	return GetInterface((IAsyncReader*) this, ppv);
    } else 
    return CBasePin::NonDelegatingQueryInterface(riid, ppv);
}

HRESULT CFileSourceOutpin::InitAllocator(IMemAllocator **ppAlloc)
{
    HRESULT hr = NOERROR;
    *ppAlloc = NULL;
    CMemAllocator *pMemObject = NULL;

    /* Create a default memory allocator */
    pMemObject = new CMemAllocator(NAME("Base memory allocator"),NULL, &hr);
    if (pMemObject == NULL) {
    	return E_OUTOFMEMORY;
    }

    if (FAILED(hr)) {
	    delete pMemObject;
	    return hr;
    }

    /* Get a reference counted IID_IMemAllocator interface */
    hr = pMemObject->QueryInterface(IID_IMemAllocator,(void **)ppAlloc);
    if (FAILED(hr)) {
	    delete pMemObject;
	    return E_NOINTERFACE;
    }

    ASSERT(*ppAlloc != NULL);
    return NOERROR;
}


STDMETHODIMP CFileSourceOutpin::RequestAllocator( IMemAllocator* pPreferred,
                    ALLOCATOR_PROPERTIES* pProps, IMemAllocator ** ppActual )
{
	if ( m_pAsyncIO == NULL ) return E_FAIL;
    // we care about alignment but nothing else
    if (!pProps->cbAlign || !m_pAsyncIO->IsAligned(pProps->cbAlign)) {
       m_pAsyncIO->Alignment(&pProps->cbAlign);
    }

    ALLOCATOR_PROPERTIES Actual;
    HRESULT hr;

    if (pPreferred) {
	    hr = pPreferred->SetProperties(pProps, &Actual);
	    if (SUCCEEDED(hr) && m_pAsyncIO->IsAligned(Actual.cbAlign)) {
            pPreferred->AddRef();
    	    *ppActual = pPreferred;
            return S_OK;
        }
    }

    // create our own allocator
    IMemAllocator* pAlloc;
    hr = InitAllocator(&pAlloc);
    if (FAILED(hr)) {
        return hr;
    }

    //...and see if we can make it suitable
    hr = pAlloc->SetProperties(pProps, &Actual);
    if (SUCCEEDED(hr) && m_pAsyncIO->IsAligned(Actual.cbAlign)) {
        // we need to release our refcount on pAlloc, and addref
        // it to pass a refcount to the caller - this is a net nothing.
        *ppActual = pAlloc;
        return S_OK;
    }

    // failed to find a suitable allocator
    pAlloc->Release();

    // if we failed because of the IsAligned test, the error code will
    // not be failure
    if (SUCCEEDED(hr)) {
        hr = VFW_E_BADALIGN;
    }
    return hr;
}

STDMETHODIMP CFileSourceOutpin::Request( IMediaSample* pSample, DWORD dwUser )
{
    REFERENCE_TIME tStart, tStop;
    HRESULT hr = pSample->GetTime(&tStart, &tStop);
    if (FAILED(hr)) {
    	return hr;
    }
	if ( m_pAsyncIO == NULL ) return E_FAIL;
    LONGLONG llPos = tStart / UNITS;
    LONG	 lLength = (LONG) ((tStop - tStart) / UNITS);
    LONGLONG llTotal;
    LONGLONG llAvailable;
	LONGLONG llOverwritten;

    hr = m_pAsyncIO->Length(&llTotal, &llAvailable, &llOverwritten);
	if (llPos >= llTotal)
		return VFW_E_FILE_TOO_SHORT;
    if (llPos + lLength > llTotal) {

        // the end needs to be aligned, but may have been aligned
        // on a coarser alignment.
        LONG lAlign;
        m_pAsyncIO->Alignment(&lAlign);
        llTotal = (llTotal + lAlign -1) & ~(lAlign-1);

        if (llPos + lLength > llTotal) {
            lLength = (LONG) (llTotal - llPos);

            // must be reducing this!
            ASSERT((llTotal * UNITS) <= tStop);
            tStop = llTotal * UNITS;
            pSample->SetTime(&tStart, &tStop);
        }
    }


    BYTE* pBuffer;
    hr = pSample->GetPointer(&pBuffer);
    if (FAILED(hr)) {
    	return hr;
    }

	return m_pAsyncIO->Request(llPos, lLength, TRUE,	pBuffer, (LPVOID)pSample, dwUser);
}

STDMETHODIMP CFileSourceOutpin::WaitForNext( DWORD dwTimeout, IMediaSample** ppSample, DWORD * pdwUser)		
{
    LONG cbActual;
    IMediaSample* pSample;
	HRESULT hr;
	if ( m_pAsyncIO == NULL ) return E_FAIL;
	hr =  m_pAsyncIO->WaitForNext(dwTimeout, (LPVOID*)&pSample,	pdwUser, &cbActual );

    if (SUCCEEDED(hr)) 
	{
        pSample->SetActualDataLength(cbActual);

		LONGLONG llTimeStart, llTimeStop;
		m_pAsyncIO->GetDataStream()->GetMediaTime( &llTimeStart, &llTimeStop );
		pSample->SetMediaTime( &llTimeStart, &llTimeStop );

    } else
	{
		if ( pSample )
			pSample->SetActualDataLength(0);
	}

    *ppSample = pSample;
    return hr;
}

STDMETHODIMP CFileSourceOutpin::SyncReadAligned( IMediaSample* pSample )
{
    REFERENCE_TIME tStart, tStop;
    HRESULT hr = pSample->GetTime(&tStart, &tStop);
    if (FAILED(hr)) {
    	return hr;
    }
	if ( m_pAsyncIO == NULL ) return E_FAIL;
    LONGLONG llPos = tStart / UNITS;
    LONG lLength = (LONG) ((tStop - tStart) / UNITS);
    LONGLONG llTotal;
    LONGLONG llAvailable;
	LONGLONG llOverwritten;

	if ( lLength > pSample->GetSize()  )
		lLength = pSample->GetSize();  //ZQ. overrun buffer

    hr = m_pAsyncIO->Length(&llTotal, &llAvailable, &llOverwritten);
	if (llPos >= llTotal)
		return VFW_E_FILE_TOO_SHORT;
    if (llPos + lLength > llTotal) {

        // the end needs to be aligned, but may have been aligned
        // on a coarser alignment.
        LONG lAlign;
        m_pAsyncIO->Alignment(&lAlign);
        llTotal = (llTotal + lAlign -1) & ~(lAlign-1);

        if (llPos + lLength > llTotal) {
            lLength = (LONG) (llTotal - llPos);

            // must be reducing this!
            ASSERT((llTotal * UNITS) <= tStop);
            tStop = llTotal * UNITS;
            pSample->SetTime(&tStart, &tStop);
        }
    }
	if (llOverwritten > 0 && llPos < llOverwritten + 500000)
	{
		// There's data in the file that's been overwritten. This happens when
		// we go to early into a pause buffer. Reset the timestamps to account for it.
		llPos = llOverwritten + 1000000;
		tStart = llPos * UNITS;
		tStop = (llPos + lLength) * UNITS;
		pSample->SetTime(&tStart, &tStop);
	}


    BYTE* pBuffer;
    hr = pSample->GetPointer(&pBuffer);
    if (FAILED(hr)) 
	{
    	return hr;
    }

    LONG cbActual=0;
    hr = m_pAsyncIO->SyncReadAligned(llPos, lLength, pBuffer, &cbActual, pSample );
	if (FAILED(hr)) 
	{
		pSample->SetActualDataLength( 0 );
	}
	else
	{
		pSample->SetActualDataLength(cbActual);

		LONGLONG llTimeStart, llTimeStop;
		m_pAsyncIO->GetDataStream()->GetMediaTime( &llTimeStart, &llTimeStop );
		pSample->SetMediaTime( &llTimeStart, &llTimeStop );

	}

    return hr;

}

STDMETHODIMP CFileSourceOutpin::SyncRead( LONGLONG llPosition,	LONG lLength, BYTE* pBuffer)
{
	if ( m_pAsyncIO == NULL ) return E_FAIL;
	HRESULT hr = m_pAsyncIO->SyncRead(llPosition, lLength, pBuffer);
	return hr;
}

STDMETHODIMP CFileSourceOutpin::Length( LONGLONG* pTotal, LONGLONG* pAvailable)
{
	if ( m_pAsyncIO == NULL ) return E_FAIL;
	HRESULT hr = m_pAsyncIO->Length(pTotal, pAvailable, NULL);
	return hr;
}

STDMETHODIMP CFileSourceOutpin::Length( LONGLONG* pTotal, LONGLONG* pAvailable, LONGLONG* pOverwritten)
{
	if ( m_pAsyncIO == NULL ) return E_FAIL;
	HRESULT hr = m_pAsyncIO->Length(pTotal, pAvailable, pOverwritten);
    return hr;
}


//////////////////////////////////////////////////////////////////
#pragma warning(disable:4355)
CFileSourceFilter::CFileSourceFilter( LPUNKNOWN pUnk, HRESULT *phr ) :
  //CBaseFilter( NAME("File Source"),  pUnk,   &m_csFilter,  CLSID_AsyncReader, NULL ),
	CBaseFilter( NAME("File Source"),  pUnk,   &m_csFilter,   CLSID_SharedFileSource, NULL ),
  m_OutputPin( phr, this, &m_csFilter ),
  m_pHostname( NULL ),
  m_pShareInfo( NULL ),
  m_dwCircFileSize( 0 ),
  m_pStream( NULL )
{
	m_skipID3Headers = 1;

	HKEY rootKey = HKEY_LOCAL_MACHINE;
	DWORD holder;
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(holder);
	if ( RegOpenKeyEx(rootKey, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\AsyncSource", 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "SkipID3Headers", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_skipID3Headers = holder;
		}
		RegCloseKey(myKey);
	}

    m_mt.SetType(&MEDIATYPE_Stream);
    m_mt.SetSubtype(&MEDIASUBTYPE_NULL);

	DbgLog((LOG_TRACE, 3, TEXT("FileSource created 1.0.2 ZQ. debug level:%d"), _debug_level ) );
}

CFileSourceFilter::~CFileSourceFilter( )
{
	if ( m_pStream != NULL )
	{
		m_pStream->Close();
		delete m_pStream; 
	}
	if ( m_pHostname != NULL )
		delete m_pHostname;


	DbgLog((LOG_TRACE, 3, TEXT("FileSource destroyed ZQ.")) );
}

STDMETHODIMP CFileSourceFilter::NonDelegatingQueryInterface(REFIID riid, void **ppv)
{
	if (riid == IID_IFileSourceFilter) 
	{
		return GetInterface((IFileSourceFilter *)this, ppv);
	}
	else if (riid == IID_ISharedAsyncFile) {
		return GetInterface((ISharedAsyncFile *) this, ppv);
	}
	else if (riid == IID_ISpecifyPropertyPages) {
        return GetInterface(static_cast<ISpecifyPropertyPages *>(this), ppv);
	}
	else {
		return CBaseFilter::NonDelegatingQueryInterface(riid, ppv);
	}
}

int CFileSourceFilter::GetPinCount()
{
    return 1;
}

CBasePin *CFileSourceFilter::GetPin(int n)
{
    if ((GetPinCount() > 0) && (n == 0)) {
    	return &m_OutputPin;
    } else {
    	return NULL;
    }
}

STDMETHODIMP CFileSourceFilter::GetPages(CAUUID *pPages)
{
    if (pPages == NULL)
        return E_POINTER;

    pPages->cElems = 1;
    pPages->pElems = (GUID *) CoTaskMemAlloc(sizeof(GUID));
    if(pPages->pElems == NULL)
    {
        return E_OUTOFMEMORY;
    }

    *(pPages->pElems) = CLSID_FileSourceProperties;
    return NOERROR;

}

STDMETHODIMP CFileSourceFilter::Load(LPCOLESTR lpwszFileName, const AM_MEDIA_TYPE *pmt)
{
	DbgLog((LOG_TRACE, 4, TEXT("Loading File...") ));
	CAutoLock lck(&m_csFilter);
    if (NULL == pmt) {
        m_mt.SetType(&MEDIATYPE_Stream);
        m_mt.SetSubtype(&MEDIASUBTYPE_NULL);
    } else {
        m_mt = *pmt;
    }

	if ( m_pStream != NULL )
	{
		m_pStream->Close();
		delete m_pStream;
		m_pStream = NULL;
	}

	//file name conversion
	int cch = lstrlenW(lpwszFileName) + 1;
	BOOL isMP3 = FALSE;
	char fileExtension[24];
	fileExtension[0] = '\0';
	char *szFilename = new char[cch];
	WideCharToMultiByte(CP_ACP, 0, lpwszFileName, -1, szFilename, cch, 0, 0);

	m_pStream = new CMediaStream( this, m_pHostname  );
	m_pStream->m_pShareInfo = m_pShareInfo;
	m_pStream->m_dwCircFileSize = m_dwCircFileSize;
	if ( m_pStream->Open( (WCHAR*)lpwszFileName ) != S_OK )
	{
		DbgLog((LOG_TRACE, 3, TEXT("Failed Loading File:%s "), szFilename ));
		return E_FAIL;
	}

	//get file filename extension
	char* dotPtr = strrchr(szFilename, '.');

	if (dotPtr)
	{
		isMP3 = !_stricmp(".mp3", dotPtr);
		sprintf(fileExtension, "Extensions\\%s", dotPtr);
	}
	delete [] szFilename;

	if ( pmt == NULL )
	{
		char readBuf[1024*4];
		DWORD  dwRead;
		CStreamType StreamType;
		HRESULT hr = m_pStream->SetPointer(0);
		if ( FAILED(hr) ) 
		{
			return hr;
		}
		hr = m_pStream->Read((BYTE*)readBuf, sizeof(readBuf), FALSE, &dwRead);
		if (FAILED(hr)) 
		{
			return hr;
		}
		m_pStream->SetPointer(0);

		if ( StreamType.CheckTypeByData( readBuf, (int)dwRead ) )
		{
			m_mt = StreamType.m_mt;
		} else
		if ( isMP3)
		{
			DbgLog((LOG_TRACE, 2, TEXT("MP3 file found")));
			m_mt.SetType(&MEDIATYPE_Stream);
			m_mt.SetSubtype(&MEDIASUBTYPE_MPEG1Audio);
		}

		//Jeff's hack code
		if (m_skipID3Headers && readBuf[0] == 'I' && readBuf[1] == 'D' && readBuf[2] == '3')
		{
			BOOL footerPresent = (readBuf[5] & 0x10) == 0x10;
			int mp3headerSize = (((int)readBuf[6]) << 21) + (((int)readBuf[7]) << 14) + (((int)readBuf[8]) << 7) + readBuf[9];
			mp3headerSize += 10 + (footerPresent ? 10 : 0);
			if (mp3headerSize > 0)
			{
				// Now we need to check to make sure the MP3 data actually starts right after the header...we've seen lots of MP3 files where this is not
				// the case. So we scan them until we find the 0xFFE0 mask (the second byte only needs to have the 0xE0 bits set, it doesn't need to be that value)
				// after we skip the known header size.
				LONGLONG totalSize = m_pStream->Size();
				if (mp3headerSize < totalSize)
				{
					hr = m_pStream->SetPointer(mp3headerSize);
					if ( FAILED(hr) ) 
					{
						return hr;
					}
					hr = m_pStream->Read((BYTE*)readBuf, sizeof(readBuf), FALSE, &dwRead);
					if (FAILED(hr)) 
					{
						return hr;
					}
					int currMp3Offset = 0;
					int mp3Failure = 0;
					while (((readBuf[currMp3Offset] & 0xFF) != 0xFF) && ((readBuf[currMp3Offset + 1] & 0xE0) != 0xE0))
					{
						currMp3Offset++;
						if (currMp3Offset == 4095) // one less than the buffer size
						{
							if (mp3headerSize + 4095 > totalSize)
							{
								mp3Failure = 1;
								break;
							}
							m_pStream->SetPointer(mp3headerSize + 4095);
							hr = m_pStream->Read((BYTE*)readBuf, sizeof(readBuf), FALSE, &dwRead);
							if (FAILED(hr)) 
							{
								return hr;
							}
							mp3headerSize += 4095;
							currMp3Offset = 0;
						}
					}
					m_pStream->SetPointer(0);
					if (!mp3Failure)
					{
						m_pStream->m_dwSkipHeaderBytes = (DWORD) mp3headerSize + currMp3Offset;
						DbgLog((LOG_TRACE, 2, TEXT("ID3 header size=%d"), (int)(m_pStream->m_dwSkipHeaderBytes)));
					}
				}
			}

		}

		if ( !StreamType.TypeFound() && !isMP3 )
		{
			if ( StreamType.CheckTypeByExtension( fileExtension, readBuf, (int)dwRead ) )
			{
				m_mt = StreamType.m_mt;
			}
		}

	}


	m_mt.bFixedSizeSamples = FALSE;
    m_mt.bTemporalCompression = TRUE;
    m_mt.lSampleSize = 1;
	m_OutputPin.SetMediaType( &m_mt );
	m_OutputPin.Init( (CAsyncStream*)m_pStream );


	// Because we use file size changes for detecting a source change, we can't
	// keep this at a constant value. It needs to change for each file.  /* Jeff's code, I don't know what's for */
	m_pStream->m_largeFileSize++;
	DbgLog((LOG_TRACE, 4, TEXT("Loading File trace done.") ));
	return S_OK;
};

STDMETHODIMP CFileSourceFilter::Reset()
{
    m_mt.SetType(&MEDIATYPE_Stream);
    m_mt.SetSubtype(&MEDIASUBTYPE_NULL);

	if ( m_pStream != NULL )
	{
		delete m_pStream;
		m_pStream = NULL;
	}

	return S_OK;
}

STDMETHODIMP CFileSourceFilter::LoadEx( DWORD dwSerial, LPCOLESTR lpwszFileName, const AM_MEDIA_TYPE *pmt )
{
  	CAutoLock lck(&m_csFilter);

	//File name conversion
	int cch = lstrlenW(lpwszFileName) + 1;
	BOOL isMP3 = FALSE;
	char fileExtension[24];
	fileExtension[0] = '\0';
	char *szFilename = new char[cch];
	WideCharToMultiByte(CP_ACP, 0, lpwszFileName, -1, szFilename, cch, 0, 0);

	if ( pmt != NULL )
        m_mt = *pmt;

	if ( m_pStream == NULL )
		m_pStream = new CMediaStream( this, m_pHostname, TRUE  );

	if ( m_pStream->OpenEx( dwSerial, (WCHAR*)lpwszFileName ) != S_OK )
	{
		DbgLog((LOG_TRACE, 3, TEXT("Failed Loading File:%s "), szFilename ));
		return E_FAIL;
	}
	m_pStream->m_pShareInfo = m_pShareInfo;
	m_pStream->m_dwCircFileSize = m_dwCircFileSize;

	if ( dwSerial == 0 )
	{
		//get file filename extension
		char* dotPtr = strrchr(szFilename, '.');

		if (dotPtr)
		{
			isMP3 = !_stricmp(".mp3", dotPtr);
			sprintf(fileExtension, "Extensions\\%s", dotPtr);
		}
		delete [] szFilename;

		if ( pmt == NULL )
		{
			char readBuf[1024*4];
			DWORD  dwRead;
			CStreamType StreamType;
			HRESULT hr = m_pStream->SetPointer(0);
			if ( FAILED(hr) ) 
			{
				m_pStream->CloseEx( dwSerial );
				return hr;
			}
			hr = m_pStream->Read((BYTE*)readBuf, sizeof(readBuf), FALSE, &dwRead);
			if (FAILED(hr)) 
			{
				m_pStream->CloseEx( dwSerial );
				return hr;
			}
			m_pStream->SetPointer(0);

			if ( StreamType.CheckTypeByData( readBuf, (int)dwRead ) )
			{
				m_mt = StreamType.m_mt;
			} else
			if ( isMP3)
			{
				DbgLog((LOG_TRACE, 2, TEXT("MP3 file found")));
				m_mt.SetType(&MEDIATYPE_Stream);
				m_mt.SetSubtype(&MEDIASUBTYPE_MPEG1Audio);
			}

			//Jeff's hack code
			if (m_skipID3Headers && readBuf[0] == 'I' && readBuf[1] == 'D' && readBuf[2] == '3')
			{
				BOOL footerPresent = (readBuf[5] & 0x10) == 0x10;
				int mp3headerSize = (((int)readBuf[6]) << 21) + (((int)readBuf[7]) << 14) + (((int)readBuf[8]) << 7) + readBuf[9];
				mp3headerSize += 10 + (footerPresent ? 10 : 0);
				if (mp3headerSize > 0)
						m_pStream->m_dwSkipHeaderBytes = (DWORD) mp3headerSize;
				DbgLog((LOG_TRACE, 2, TEXT("ID3 header size=%d"), mp3headerSize));
			}

			if ( !StreamType.TypeFound() && !isMP3 )
			{
				if ( StreamType.CheckTypeByExtension( fileExtension, readBuf, (int)dwRead ) )
				{
					m_mt = StreamType.m_mt;
				}
			}

		}
	}

	m_mt.bFixedSizeSamples = FALSE;
    m_mt.bTemporalCompression = TRUE;
    m_mt.lSampleSize = 1;
	m_OutputPin.SetMediaType( &m_mt );
	m_OutputPin.Init( (CAsyncStream*)m_pStream );


	// Because we use file size changes for detecting a source change, we can't
	// keep this at a constant value. It needs to change for each file.  /* Jeff's code, I don't know what's for */
	m_pStream->m_largeFileSize++;

	m_pStream->SwitchFile( -1 ); //close file.
	return S_OK;
};

STDMETHODIMP CFileSourceFilter::GetCurFile(LPOLESTR * ppszFileName, AM_MEDIA_TYPE *pmt) 
{
	WCHAR *pszFileName;
	if ( m_pStream != NULL )
		pszFileName = (WCHAR*)m_pStream->GetCurFile() ;
	else
		pszFileName = L"NONE";

	if ( pszFileName == NULL )
		pszFileName = L"NONE";

	int n = sizeof(WCHAR)*(1+lstrlenW(pszFileName));
	*ppszFileName = (LPOLESTR) CoTaskMemAlloc( n );
	if (*ppszFileName!=NULL) 
			CopyMemory(*ppszFileName, pszFileName, n);
	
	if ( pmt != NULL )
		CopyMediaType(pmt, &m_mt);
	return S_OK;
}

STDMETHODIMP CFileSourceFilter::put_ShareInfo(ShareInfo* sharin)
{
	DbgLog((LOG_TRACE, 2, TEXT("ShareInfo is put in") ));
	m_pShareInfo = sharin;
	if ( m_pStream != NULL )
		m_pStream->m_pShareInfo = sharin;
	return S_OK;
}

STDMETHODIMP CFileSourceFilter::put_CircularSize(long circSize) 
{
	DbgLog((LOG_TRACE, 2, TEXT("Circular Size is put in") ));
	m_dwCircFileSize = circSize;
	if ( m_pStream != NULL )
		m_pStream->m_dwCircFileSize = circSize;
	return S_OK;
}

STDMETHODIMP CFileSourceFilter::put_RemoteHostname(LPCSTR pHostname) 
{
	DbgLog((LOG_TRACE, 2, TEXT("put_RemoteHostname(%s) IN"), pHostname ? pHostname : "NULL"));
	if ( m_pHostname )
		delete[] m_pHostname;
	if ( pHostname )
	{
		m_pHostname = new TCHAR[ strlen(pHostname) + 1 ];
		strcpy( m_pHostname, pHostname );
	}
	else
		m_pHostname = NULL;
	DbgLog((LOG_TRACE, 2, TEXT("put_RemoteHostname() OUT"), pHostname));
	return S_OK;
}

STDMETHODIMP CFileSourceFilter::GetFileName( DWORD dwSerial, WCHAR* pFileName, DWORD dwSizeOfFileName  ) 
{
	WCHAR* pInternalFileName = m_pStream->GetFileNameEx( dwSerial );
	int cch = lstrlenW(pInternalFileName) + 1;
	memcpy( pFileName, pInternalFileName, min( cch*2, (int)dwSizeOfFileName)  );
	return S_OK;
}


STDMETHODIMP CFileSourceFilter::GetFileInf( DWORD dwSerial,  void* pInf, DWORD dwSizeOfInfo )
{
	FILE_INF *pFileInf = (FILE_INF*)pInf;
	if ( sizeof(FILE_INF) < dwSizeOfInfo ) return E_INVALIDARG;
	m_pStream->GetFileInf( dwSerial, pFileInf );
	return S_OK;
}

LONGLONG CFileSourceFilter::GetTotalWrite(LPWSTR pwFileName)
{
	static jclass mmcClass;
	static jmethodID mmcInstMeth;
	static jmethodID recBytesMeth;
	static jclass fileClass;
	static jmethodID fileCreator;
	static jobject mmcInst;
	static JavaVM* vmBuf;
	if (!vmBuf)
	{
		HKEY rootKey = HKEY_LOCAL_MACHINE;
		char currVer[16];
		HKEY myKey;
		DWORD readType;
		DWORD hsize = sizeof(currVer);
		if (RegOpenKeyEx(rootKey, "Software\\JavaSoft\\Java Runtime Environment", 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return 0;
		}
		
		if (RegQueryValueEx(myKey, "CurrentVersion", 0, &readType, (LPBYTE)currVer, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return 0;
		}
		RegCloseKey(myKey);
		char pathKey[1024];
		strcpy(pathKey, "Software\\JavaSoft\\Java Runtime Environment\\");
		strcat(pathKey, currVer);
		char jvmPath[1024];
		if (RegOpenKeyEx(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return 0;
		}
		hsize = sizeof(jvmPath);
		if (RegQueryValueEx(myKey, "RuntimeLib", 0, &readType, (LPBYTE)jvmPath, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return 0;
		}
		RegCloseKey(myKey);

		// Go to the 2nd to last backslash, and append jawt.dll from there to complete the string
		HMODULE jvmMod = LoadLibrary(jvmPath);

		jsize numVMs;
		typedef jint (JNICALL *JNIGetCreatedJavaVMsPROC)(JavaVM **, jsize, jsize *);
		
		JNIGetCreatedJavaVMsPROC lpfnProc = (JNIGetCreatedJavaVMsPROC)GetProcAddress(jvmMod, "JNI_GetCreatedJavaVMs");
		if (lpfnProc(&vmBuf, 1, &numVMs))
		{
			if ( jvmMod ) FreeLibrary( jvmMod );
			return 0;
		}
		if ( jvmMod ) FreeLibrary( jvmMod );
	}
	JNIEnv* env;
	bool detachTheThread = false;
	if (vmBuf->GetEnv((void**)&env, JNI_VERSION_1_2) != JNI_OK)
	{
		vmBuf->AttachCurrentThread((void**)&env, NULL);
		detachTheThread = true;
	}
	if (!mmcClass)
	{
		mmcClass = (jclass) env->NewWeakGlobalRef(env->FindClass("sage/MMC"));
		mmcInstMeth = env->GetStaticMethodID(mmcClass, "getInstance", "()Lsage/MMC;");
		recBytesMeth = env->GetMethodID(mmcClass, "getRecordedBytes", "(Ljava/io/File;)J");
		fileClass = (jclass) env->NewGlobalRef(env->FindClass("java/io/File"));
		fileCreator = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
		mmcInst = env->NewWeakGlobalRef(env->CallStaticObjectMethod(mmcClass, mmcInstMeth));
	}
	jstring jfileString = env->NewString(reinterpret_cast<jchar*>(pwFileName), lstrlenW(pwFileName));
	LONGLONG jrv = env->CallLongMethod(mmcInst, recBytesMeth,
		env->NewObject(fileClass, fileCreator, jfileString));
	if (detachTheThread)
		vmBuf->DetachCurrentThread();
	return jrv;
}
