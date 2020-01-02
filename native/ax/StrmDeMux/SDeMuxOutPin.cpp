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
#include <streams.h>
#include <limits.h>
#include <initguid.h>
#include <ks.h>
#include <dvdmedia.h>
#include "SDeMuxOutPin.h"
#include "SDeMuxFilter.h"
#include "../../../third_party/Microsoft/FileSource/strconv.h"
/*  Ignore warnings about this pointers being used in member initialization
    lists
*/
#pragma warning(disable:4355)

//#define DUMP_ENABLE	1
#undef DUMP_ENABLE

DEFINE_GUID(MEDIATYPE_Subtitle,
0xe487eb08, 0x6b26, 0x4be9, 0x9d, 0xd3, 0x99, 0x34, 0x34, 0xd3, 0x13, 0xfd);


CSDeMuxOutPin::CSDeMuxOutPin( CSDeMuxFilter *pFilter,  HRESULT *phr, LPCWSTR pName, 
								  CMediaType *pmt, BYTE uContentType, BYTE uStreamId, BYTE uSubId, BYTE uTrackIndex ):
 CBaseOutputPin(
        NAME("CSDeMuxOutPin"),
        pFilter,
        &pFilter->m_csFilter,
        phr,
        pName),
 m_uContentType( uContentType ),
 m_uStreamId( uStreamId ),
 m_uSubId( uSubId ),
 m_uTrackIndex( uTrackIndex ),
 m_pFilter(pFilter),
 m_pOutputQueue( NULL ),
 m_dwSampleCount( 0 ),
 m_bFirstSample( 0 ),
 m_pSample( NULL ),
 m_pMediaAccept( NULL ),
 m_lMedia(NAME("MediaTypeList"))
{
	AddMediaType( pmt  );
	m_bDiscontinuity=FALSE;
	fd = NULL;  //debug dump
//	DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> created()" ), LPCTSTR(CWtoT(pName)) ));
_file_index = 0;  //ZQ
}

CSDeMuxOutPin::~CSDeMuxOutPin( )
{
	POSITION pos = m_lMedia.GetHeadPosition();
    while (pos) {
        CMediaType *pmtList = m_lMedia.GetNext(pos);
		if ( pmtList )
			delete pmtList;
    }
	SAFE_DELETE( m_pOutputQueue );
	SAFE_RELEASE( m_pSample );  
}

HRESULT CSDeMuxOutPin::NonDelegatingQueryInterface(REFIID riid, void **ppv )
{
	if ( riid == IID_IMediaSeeking )
		return GetInterface(static_cast<IMediaSeeking *>(m_pFilter), ppv);
	else
		return CBaseOutputPin::NonDelegatingQueryInterface(riid, ppv);
};

STDMETHODIMP CSDeMuxOutPin::Connect( IPin * pReceivePin, const AM_MEDIA_TYPE *pmt )
{
	PIN_INFO PinInfo;
	if ( pReceivePin->QueryPinInfo( &PinInfo ) == S_OK )
	{
		if ( PinInfo.pFilter )
		{
			CLSID FilterCLSID;
			FILTER_INFO FilterInfo;
			HRESULT hr;
			hr = PinInfo.pFilter->GetClassID(&FilterCLSID);
			hr = PinInfo.pFilter->QueryFilterInfo(&FilterInfo);
			if ( hr == S_OK )
			{
				char FilterName[512];
				WideCharToMultiByte( CP_ACP, 0, FilterInfo.achName, -1,
									 FilterName, sizeof(FilterName), NULL, NULL);
//				DbgLog((LOG_TRACE, 3, "Connect decoder Filter:\"%s\" GUID:%s", FilterName, LPCSTR(CGUIDtoA(FilterCLSID)) ));
				FilterInfo.pGraph->Release();
			}
		}
		PinInfo.pFilter->Release();
	}
	return CBasePin::Connect( pReceivePin, pmt );
}


HRESULT CSDeMuxOutPin::CheckMediaType(const CMediaType *pmt)
{
	//  Check if we accept a media type - only accept 1 for now
	POSITION pos = m_lMedia.GetHeadPosition();

    while (pos) {
        CMediaType *pmtList = m_lMedia.GetNext(pos);
        if (*pmtList == *pmt) {
//			DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> Check MedType:%s %s 0x%x. Accept" ), LPCTSTR(CWtoT(m_pName)),
//				LPCSTR(CGUIDtoA(pmt->majortype)), LPCSTR(CGUIDtoA(pmt->subtype)), pmt->formattype ));
			m_pMediaAccept = pmtList;
            return S_OK;
        }
    }
//	DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> Check MedType:%s %s 0x%x. Refuse" ), LPCTSTR(CWtoT(m_pName)),
//		LPCSTR(CGUIDtoA(pmt->majortype)), LPCSTR(CGUIDtoA(pmt->subtype)), pmt->formattype ));
    return S_FALSE;
}

HRESULT CSDeMuxOutPin::GetMediaType(int iPosition,CMediaType *pMediaType)
{
   POSITION pos = m_lMedia.GetHeadPosition();
   while (pos) {
        CMediaType *pmtList = m_lMedia.GetNext(pos);
        if (iPosition-- == 0) {
            *pMediaType = *pmtList;
            return S_OK;
        }
    }
    return VFW_S_NO_MORE_ITEMS;
}

HRESULT CSDeMuxOutPin::AddMediaType( CMediaType const *pmt )
{
    CMediaType *pmtNew = new CMediaType(*pmt);
    if (pmtNew) {
        if (m_lMedia.AddTail(pmtNew)) {
//			DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> Added MedType:%s %s 0x%x" ), LPCTSTR(CWtoT(m_pName)),
//						LPCSTR(CGUIDtoA(pmt->majortype)), LPCSTR(CGUIDtoA(pmt->subtype)), pmt->formattype ));
            return S_OK;
        }
    }
    delete pmtNew;
    return E_OUTOFMEMORY;
}

HRESULT CSDeMuxOutPin::DecideAllocator(IMemInputPin *pPin, IMemAllocator **ppAlloc)
{
    HRESULT hr = NOERROR;
    *ppAlloc = NULL;

    ALLOCATOR_PROPERTIES prop;
    ZeroMemory(&prop, sizeof(prop));
	if ( m_uContentType == VIDEO_DATA )
	{
		//ZQ TEST
		hr = InitAllocator( m_pFilter->m_pVideoAllocator, ppAlloc );
		if (SUCCEEDED(hr)) 
			hr = DecideBufferSize(*ppAlloc, &prop);
	}
	else
	if ( m_uContentType == AUDIO_DATA )
	{
		hr = InitAllocator( m_pFilter->m_pAudioAllocator, ppAlloc );
		if (SUCCEEDED(hr)) 
			hr = DecideBufferSize(*ppAlloc, &prop);
	}
	else
	if ( m_uContentType == SUBPICTURE_DATA )
	{
		hr = InitAllocator( m_pFilter->m_pSubPictureAllocator, ppAlloc );
		if (SUCCEEDED(hr)) 
			hr = DecideBufferSize( *ppAlloc, &prop);
	}
	else
		return E_FAIL;

	if (SUCCEEDED(hr)) 
	{
         hr = pPin->NotifyAllocator(*ppAlloc, TRUE );
         if (SUCCEEDED(hr)) 
		 {
			return NOERROR;
         }
	}
    
    if (*ppAlloc) {
        (*ppAlloc)->Release();
        *ppAlloc = NULL;
    }
    return hr;
}

HRESULT CSDeMuxOutPin::InitAllocator( CSequentialAllocator *pAllocator, IMemAllocator **ppAlloc)
{
	HRESULT hr = NOERROR;

	//share a global alloactor in filter
	*ppAlloc = new CSubAllocator(NULL, &hr, pAllocator );
//	DbgLog((LOG_TRACE, 2, TEXT("DeMuxOutputPin <%s> Share a Output alloactor"), LPCTSTR(CWtoT(m_pName)) ));

	if (*ppAlloc == NULL) {
		return E_OUTOFMEMORY;
	}

	if (FAILED(hr)) {
		delete *ppAlloc;
		*ppAlloc = NULL;
		return hr;
	}

	((CSubAllocator*)*ppAlloc)->SetupDeMuxerFilter( m_pFilter );
	/* Get a reference counted IID_IMemAllocator interface */
	(*ppAlloc)->AddRef();
	return NOERROR;
}

HRESULT CSDeMuxOutPin::DecideBufferSize( IMemAllocator * pAlloc, ALLOCATOR_PROPERTIES * pProp )
{
	HRESULT hr;
	
	ALLOCATOR_PROPERTIES propActual;
	
	ALLOCATOR_PROPERTIES prop={0};
	if ( m_uContentType == VIDEO_DATA )
	{
		if (( hr =  m_pFilter->m_pVideoAllocator->GetProperties( &prop ) )== S_OK )
			*pProp = prop;
	} else
	if ( m_uContentType == AUDIO_DATA )
	{
		if (( hr =  m_pFilter->m_pAudioAllocator->GetProperties( &prop ) )== S_OK )
			*pProp = prop;
	} else
	if ( m_uContentType == SUBPICTURE_DATA )
	{
		if (( hr =  m_pFilter->m_pSubPictureAllocator->GetProperties( &prop ) )== S_OK )
			*pProp = prop;
	} else
	{
		return E_FAIL;
	}
	
	hr = pAlloc->SetProperties(pProp, &propActual);
	m_dwBlockSize = pProp->cbBuffer;
//	DbgLog((LOG_TRACE, 2, TEXT("SDeMux OutputPin <%s> Buffer size=%d count=%d align=%d"),
//			LPCTSTR(CWtoT(m_pName)),
//			propActual.cbBuffer, propActual.cBuffers, propActual.cbAlign));

	return hr;

}



HRESULT CSDeMuxOutPin::Active(void) {

    CAutoLock lock(m_pFilter->pStateLock());

    HRESULT hr;

    if (m_pFilter->IsActive()) {
		return S_FALSE;	// succeeded, but did not allocate resources (they already exist...)
    }

    // do nothing if not connected - its ok not to connect to
    // all pins of a source filter
    if (!IsConnected()) {
        return NOERROR;
    }
    hr = CBaseOutputPin::Active();
    if (FAILED(hr)) {
        return hr;
    }

	if ( m_pFilter->NeedSampleGroup( m_uTrackIndex ) )
	{
//		DbgLog((LOG_TRACE, 2, TEXT("DeMuxOutputPin <%s> Need sample grouping"), LPCTSTR(CWtoT(m_pName)) ));
	}

    /*  Create our batch Queuen */
    ASSERT(m_pOutputQueue == NULL);

    hr = S_OK;
	if ( m_mt.majortype == MEDIATYPE_Stream || m_mt.majortype == MEDIATYPE_Subtitle ) 
		m_pOutputQueue = new COutputQueue(GetConnected(), // input pin
										  &hr,            // return code
										  FALSE,          // Auto detect
										  FALSE,          // create a queue (async)
										  1,              // batch size
										  TRUE,           // exact batch
										  1,              // queue size
										  THREAD_PRIORITY_NORMAL );
	else
		//m_pOutputQueue = new COutputQueue(m_Connected, &hr, TRUE, FALSE); //ZQ
		m_pOutputQueue = new COutputQueue(GetConnected(), // input pin
										  &hr,            // return code
										  FALSE,          // Auto detect
										  TRUE,           // create a queue (async)
										  50,             // batch size
										  FALSE,           // exact batch
										  51,             // queue size
										  THREAD_PRIORITY_HIGHEST-1 );  
		
	
	
	m_bDiscontinuity = TRUE;

    if ( m_pOutputQueue == NULL ) 
        return E_OUTOFMEMORY;
    
    if ( FAILED(hr) ) 
	{
		SAFE_DELETE( m_pOutputQueue );
    } 


//	DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> created OutputQueue" ), LPCTSTR(CWtoT(m_pName)) ));
	return NOERROR;
}


//
// Inactive
//
// Pin is inactive - shut down the worker thread
// Waits for the worker to exit before returning.
HRESULT CSDeMuxOutPin::Inactive(void) {

    CAutoLock lock(m_pFilter->pStateLock());

    HRESULT hr;

    // do nothing if not connected - its ok not to connect to
    // all pins of a source filter
    if (!IsConnected()) {
        return NOERROR;
    }

    hr = CBaseOutputPin::Inactive();  // call this first to Decommit the allocator
    if (FAILED(hr)) {
		return hr;
    }

//	DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> delete OutputQueue" ), LPCTSTR(CWtoT(m_pName)) ));
	//clean up Queuen
	SAFE_DELETE( m_pOutputQueue );
	
    return NOERROR;
}

HRESULT CSDeMuxOutPin::CompleteConnect(IPin *pReceivePin)
{
	HRESULT hr = CBaseOutputPin::CompleteConnect(pReceivePin);
	hr = m_pFilter->CompleteConnect( pReceivePin, this );

#ifdef DUMP_ENABLE
	//open_dump();
	if ( IsVideoPin() ) { close_dump(); open_dump( _file_index++ );  } 
#endif


	return hr;
}

HRESULT CSDeMuxOutPin::BreakConnect(void)
{
	HRESULT hr = CBaseOutputPin::BreakConnect();
	hr = m_pFilter->DisconnectConnection(this);

#ifdef DUMP_ENABLE
	close_dump();
#endif

	return hr;
}

HRESULT CSDeMuxOutPin::DeliverNewSegment( REFERENCE_TIME rtStart, REFERENCE_TIME rtStop, double rate )
{
	if ( m_pOutputQueue == NULL ) return S_OK;
//	DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> DeliverNewSegment start:%s stop:%s" ), LPCTSTR(CWtoT(m_pName)), 
//									(LPCTSTR)Disp( (CRefTime)rtStart), (LPCTSTR)Disp( (CRefTime)rtStop) ));

	NewSegment( rtStart, rtStop, rate  );

	m_pOutputQueue->NewSegment( rtStart, rtStop, rate );

	m_bFirstSample = FALSE;
	m_bDiscontinuity = TRUE;

    return S_OK;
}

HRESULT CSDeMuxOutPin::DeliverEndOfStream()
{
	if ( m_pOutputQueue == NULL ) return S_OK;
//	DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> DeliverEndOfStream" ), LPCTSTR(CWtoT(m_pName)) ));

	if ( m_pOutputQueue != NULL )	
	{
		m_pOutputQueue->EOS();
	}

    return S_OK;

}

HRESULT CSDeMuxOutPin::DeliverBeginFlush()
{
//	DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> DeliverBeginFlush" ), LPCTSTR(CWtoT(m_pName)) ));
	if ( m_pOutputQueue != NULL )	m_pOutputQueue->BeginFlush();
	
    return S_OK;

}

HRESULT CSDeMuxOutPin::DeliverEndFlush()
{
    /*  We're already locked via the input pin */
//	DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> DeliverEndFlush ..." ), LPCTSTR(CWtoT(m_pName)) ));

	if ( m_pOutputQueue != NULL )	m_pOutputQueue->EndFlush();

	m_bDiscontinuity = TRUE;
//	DbgLog((LOG_TRACE, 3, TEXT("OutputPin <%s> DeliverEndFlush done. sent Sample Num:%d" ), LPCTSTR(CWtoT(m_pName)), m_dwSampleCount ));

#ifdef DUMP_ENABLE
	//rewind_dump( );
	if ( IsVideoPin() ) { close_dump(); open_dump( _file_index++ );  } 
#endif
    return S_OK;
}

void CSDeMuxOutPin::SendAnyway()
{
	if (NULL != m_pOutputQueue) {
		m_pOutputQueue->SendAnyway();
	}
}

#define RELEASE_SAMPLE( s ) if ( s ) { s->Release(); s = NULL; } 

HRESULT CSDeMuxOutPin::SendSample( const BYTE *pbData, LONG lByte, REFERENCE_TIME rtStart, REFERENCE_TIME rtStop, int nDiscontinuity )
{
	HRESULT hr;
	IMediaSample *pSample = NULL;
	BYTE* pBuffer;
	LONGLONG rt= 0;
	LONGLONG rs= 0;

	if ( pbData == NULL || lByte == 0 )
		return S_OK;

	//ZQ TEST
	if ( m_uContentType == VIDEO_DATA )
	{
		pSample = m_pFilter->m_pVideoBukAllocator->GetSample((PBYTE)pbData, lByte);

	} else
		pSample = Allocator()->GetSample((PBYTE)pbData, lByte);

	if ( pSample == NULL )
	{
//		DbgLog((LOG_TRACE, 2, TEXT("<%s> Faile alloc memory a sample size:%d"), LPCTSTR(CWtoT(m_pName)), lByte ));
		return E_OUTOFMEMORY;
	}

	if ( FAILED( hr = pSample->GetPointer( &pBuffer ) ) )
	{
//		DbgLog((LOG_TRACE, 2, TEXT("DeMuxOutputPin <%s> failed get sample buffer 0x%x"), LPCTSTR(CWtoT(m_pName)), hr ));
		RELEASE_SAMPLE( pSample );
		return E_OUTOFMEMORY;		
	}

	
	if ( nDiscontinuity && !(nDiscontinuity & DISBALE_SAMPLE_PTS_FLAG) )
	{
		rt = rtStart;
		rs = rt+ 1;
		pSample->SetTime( &rt, &rs );

		rt = rtStop;
		rs = rt+ 1;
		pSample->SetMediaTime( &rt, &rs  );
	} 
	

	pSample->SetSyncPoint( FALSE );
	pSample->SetDiscontinuity( 0 );

	if ( nDiscontinuity && m_bDiscontinuity && !m_bFirstSample )
	{
		pSample->SetDiscontinuity( TRUE );
		pSample->SetSyncPoint( TRUE );
		m_bDiscontinuity = FALSE;
		DbgLog((LOG_TRACE, 2, TEXT("Sample Discontinuity %s at stream 0x%02x-0x%02x"), (LPCTSTR)Disp( (CRefTime)rtStart), m_uStreamId, m_uSubId  ));
	} 

	if ( m_bDiscontinuity )
	{
		RELEASE_SAMPLE( pSample );
		return S_OK;
	}
	
	//if ( nDiscontinuity && !(nDiscontinuity & DISBALE_SAMPLE_PTS_FLAG) )
	if ( nDiscontinuity  )
	{
		pSample->SetSyncPoint( TRUE );
	}


#ifdef DUMP_ENABLE
	dump_sample( pSample );
#endif

	hr = m_pOutputQueue->Receive(pSample);

	m_dwSampleCount++;

	return hr;
}

HRESULT CSDeMuxOutPin::OpenSample( REFERENCE_TIME rtStart, BOOL bSync )
{
	HRESULT hr;
	IMediaSample *pSample;
	BYTE* pBuffer;
	if ( m_pAllocator == NULL ) return E_OUTOFMEMORY;
	hr = m_pAllocator->GetBuffer(&pSample, NULL, NULL, 0);
	if ( pSample == NULL )
	{
//		if ( 0x80040211 != hr ) //graph stopped
//			DbgLog((LOG_TRACE, 2, TEXT("<%s> Faile alloc memory a sample"), LPCTSTR(CWtoT(m_pName)) ));
		return E_OUTOFMEMORY;
	}
	
	if ( FAILED( hr = pSample->GetPointer( &pBuffer ) ) )
	{
//		DbgLog((LOG_TRACE, 2, TEXT("DeMuxOutputPin <%s> failed get sample buffer"), LPCTSTR(CWtoT(m_pName)) ));
		RELEASE_SAMPLE( pSample );
		return E_OUTOFMEMORY;		
	}

	LONGLONG rt=rtStart;
	LONGLONG rs=rt+1;
	pSample->SetTime( &rt, &rs );
	pSample->SetDiscontinuity( bSync );

	m_pSample = pSample;
	m_lSampleDataLength = 0;
	//DbgLog((LOG_TRACE, 2, TEXT("DeMuxOutputPin <%s> open sample."), LPCTSTR(CWtoT(m_pName)) ) );
	return S_OK;
}

HRESULT CSDeMuxOutPin::GetSampleTime( REFERENCE_TIME &rtStart, REFERENCE_TIME &rtStop )
{
	if ( m_pSample == NULL ) return E_OUTOFMEMORY;
	m_pSample->GetTime( &rtStart, &rtStop );

	//*bSync = m_pSample->IsDiscontinuity() == S_OK;
	return S_OK;
}

HRESULT CSDeMuxOutPin::LoadSampleData( const BYTE *pbData, LONG lData )
{
	HRESULT hr;
	BYTE* pBuffer;
	LONG  lBufferSize;

	if ( m_pSample == NULL ) return E_OUTOFMEMORY;
	if ( lData == 0 || pbData == NULL ) S_OK;

	if ( FAILED( hr = m_pSample->GetPointer( &pBuffer ) ) )
	{
//		DbgLog((LOG_TRACE, 2, TEXT("DeMuxOutputPin <%s> failed get sample buffer"), LPCTSTR(CWtoT(m_pName)) ));
		SAFE_RELEASE( m_pSample );
		return E_OUTOFMEMORY;		
	}
	
	lBufferSize = m_pSample->GetSize();
	if ( m_lSampleDataLength + lData > lBufferSize ) 
	{
//		DbgLog((LOG_TRACE, 5, TEXT("DeMuxOutputPin <%s> sample buffer is too small to deliver a sample, buf size:%d, req size:%d "), 
//			LPCTSTR(CWtoT(m_pName)), lBufferSize, m_lSampleDataLength + lData ));
		return E_OUTOFMEMORY;
	}

	memcpy( pBuffer+m_lSampleDataLength, pbData, lData );
	m_lSampleDataLength += lData;

	return S_OK;
}

HRESULT CSDeMuxOutPin::SendPartialSample( const BYTE *pbData, LONG lData )
{
	HRESULT hr;
	REFERENCE_TIME Start, Stop; 
	if ( ( hr =  GetSampleTime( Start, Stop )) == S_OK )
	{
		if ( ( hr = SendSample() ) == S_OK ) 
			if ( ( hr = OpenSample( Start, 0  )) == S_OK ) 
				if ( FAILED( hr = LoadSampleData( pbData, lData ) ) )
					DropSample( );
	} else
		DropSample( );

	return hr;
}

void CSDeMuxOutPin::DropSampleData( LONG lData )
{
	if ( lData > 0 )
	{
		if ( m_lSampleDataLength > lData )
		{
			BYTE* pBuffer;
			if ( SUCCEEDED(  m_pSample->GetPointer( &pBuffer ) ) )
				memcpy( pBuffer, pBuffer + lData, m_lSampleDataLength-lData );
			m_lSampleDataLength -= lData;
		} else
			m_lSampleDataLength = 0;
	} else
	if ( lData < 0 )
	{
		lData = -lData;
		if (  m_lSampleDataLength > lData )
			m_lSampleDataLength -= lData;
		else
			m_lSampleDataLength = 0;
	}
	
}

HRESULT CSDeMuxOutPin::ResetSample( REFERENCE_TIME rtStart, BOOL bSync )
{
	HRESULT hr;
	LONGLONG rt=rtStart;
	LONGLONG rs=rt+1;
	if ( m_pSample == NULL ) return E_OUTOFMEMORY;
	if ( FAILED( hr = m_pSample->SetTime( &rt, &rs ) ) ) return hr;
	if ( FAILED( hr = m_pSample->SetDiscontinuity( bSync ) ) ) return hr;
	m_lSampleDataLength = 0;
	return S_OK;
}

HRESULT CSDeMuxOutPin::SendSample( )
{
	HRESULT rv;
	if ( m_pSample == NULL ) return E_OUTOFMEMORY;
	if ( m_lSampleDataLength == 0 ) return S_OK;
	m_pSample->SetActualDataLength( m_lSampleDataLength );


#ifdef DUMP_ENABLE
	dump_sample( m_pSample );
#endif

	rv = m_pOutputQueue->Receive(m_pSample);
	m_dwSampleCount++;
	m_pSample = NULL;
	//DbgLog((LOG_TRACE, 2, TEXT("DeMuxOutputPin <%s> sent sample."), LPCTSTR(CWtoT(m_pName)) ) );
	return rv;
}


void CSDeMuxOutPin::DropSample( )
{
//	DbgLog((LOG_TRACE, 2, TEXT("DeMuxOutputPin <%s> drop sample."), LPCTSTR(CWtoT(m_pName)) ) );
	SAFE_RELEASE( m_pSample );
}

void CSDeMuxOutPin::dump_sample( IMediaSample *pSample )
{
	BYTE* pBuffer;
	int   Length;
	if ( fd == NULL ) return;
	if ( FAILED(  pSample->GetPointer( &pBuffer ) ) ) return;
	Length = pSample->GetActualDataLength( );
	if ( Length > 0)	
	{
		fwrite( pBuffer,1, Length, fd );
	}

}
void CSDeMuxOutPin::open_dump( )
{
	char fname[MAX_PATH];
	char path[MAX_PATH]={0};
	size_t len;
	sprintf( fname, "DUMP_DATA.ENABLE" );
	fd = fopen( fname, "r" );
	if ( fd == NULL ) return;
	len = fread( path,1, MAX_PATH-2, fd );
	if ( len )
		if ( path[len-1] != '\\' ) path[len] = '\\';
	fclose( fd );
	sprintf( fname, "%s%S.bin", path, m_pName);
	fd = fopen( fname, "wb" );
//	DbgLog((LOG_TRACE, 2, TEXT("DeMuxOutputPin <%s> dump file %s  fd=0x%x."), LPCTSTR(CWtoT(m_pName)), fname, fd ) );
}

void CSDeMuxOutPin::open_dump( int index )
{
	char fname[MAX_PATH];
	char path[MAX_PATH]={0};
	size_t len;
	sprintf( fname, "DUMP_DATA.ENABLE" );
	fd = fopen( fname, "r" );
	if ( fd == NULL ) return;
	len = fread( path,1, MAX_PATH-2, fd );
	if ( len )
		if ( path[len-1] != '\\' ) path[len] = '\\';
	fclose( fd );
	sprintf( fname, "%s%S-%d.bin", path, m_pName, index );
	fd = fopen( fname, "wb" );
//	DbgLog((LOG_TRACE, 2, TEXT("DeMuxOutputPin <%s> dump file %s  fd=0x%x."), LPCTSTR(CWtoT(m_pName)), fname, fd ) );
}

void CSDeMuxOutPin::close_dump( )
{
	if ( fd != NULL )
		fclose( fd );
	fd = NULL;
}

void CSDeMuxOutPin::rewind_dump( )
{
	if ( fd != NULL )
		fseek( fd, 0, SEEK_SET );

}

// ZQ for loop testing video codec
HRESULT CSDeMuxOutPin::FillBuffer(IMediaSample *pSample)
{
	BYTE *pData;
    long cbData;

    CheckPointer(pSample, E_POINTER);

    CAutoLock cAutoLockShared(&m_cSharedState);

    // Access the sample's data buffer
    pSample->GetPointer(&pData);
    cbData = pSample->GetSize();

	{ //ZQ

	static FILE* fp=NULL;
	static int frame = 0;
	size_t ct;
	if ( frame == 0 )
		fp = fopen( "video.bin", "rb" );
	if ( fp != NULL )
	{
		ct = fread( pData,1,cbData, fp );
		if ( ct <= 0 )
		fseek( fp, 0, SEEK_SET );
	}

	REFERENCE_TIME rtStart = 0;//cbData * frame;
    REFERENCE_TIME rtStop  = 0;//cbData * (frame+1);
    pSample->SetTime(&rtStart, &rtStop);
	frame++;

	} //ZQ

	// Set TRUE on every sample for uncompressed frames
    pSample->SetSyncPoint(TRUE);

    return S_OK;
}


