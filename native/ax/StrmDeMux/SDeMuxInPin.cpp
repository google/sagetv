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


#include "SDeMuxInPin.h"
#include "SDeMuxOutPin.h"
#include "SDeMuxFilter.h"
#include "../../../third_party/Microsoft/FileSource/strconv.h"

#define DUMP_ENABLE  1
//#undef DUMP_ENABLE



/*  -- Input pin methods -- */

/*  Constructor */
CSDeMuxInPin::CSDeMuxInPin(
    CSDeMuxFilter *pFilter,
    HRESULT *phr
) : CBaseInputPin( NAME("CSDeMuxInPin"),
			 pFilter,
             &pFilter->m_csFilter,
             phr,
             L"Input"),
    m_pReader(NULL),
	tNext(0), tCurrent(0),
	tStart(0), tStop(-1), 
	llMaxPos( -1 ),
	m_nPullState(0),
	m_bDiscontinuity( TRUE )
{
	DbgLog((LOG_TRACE, 3, TEXT("InputPin created() ")));
    m_pAllocator = NULL;

	
#ifdef DUMP_ENABLE
	open_dump( );
#endif

}

CSDeMuxInPin::~CSDeMuxInPin( )
{

#ifdef DUMP_ENABLE
	close_dump( );
#endif

}

/*  Connection stuff */
HRESULT CSDeMuxInPin::CheckConnect(IPin *pPin)
{
    HRESULT hr = CBaseInputPin::CheckConnect(pPin);
    if (FAILED(hr)) {
        return hr;
    }

	// need to check up Pin has IAsyncReader  ZQ.
	if( m_pReader == NULL ) {
		hr = pPin->QueryInterface(IID_IAsyncReader, (void**)&m_pReader);
		if(FAILED(hr)) {

			DbgLog((LOG_TRACE, 3, TEXT("InputPin doesn't have IAsyncReader ")));
			return(hr);
		}
	 }

	LONGLONG llTotal, llAvail;
    hr = m_pReader->Length(&llTotal, &llAvail);
    if(FAILED(hr)) 
	{
		DbgLog((LOG_TRACE, 3, TEXT("InputPin Can't access Length")));
        return hr;
    }
	DbgLog((LOG_TRACE, 3, TEXT("Source Length %d %d"), llTotal, llAvail ));


    return hr;
}


HRESULT CSDeMuxInPin::CompleteConnect(IPin *pReceivePin)
{
	HRESULT hr;
	DbgLog((LOG_TRACE, 3, TEXT("InputPin connect() ")));
	
	tCurrent = 0;
	tNext = 0;
	tStop = -1;	
	CBaseInputPin::CompleteConnect(pReceivePin);
	
	PBYTE pDummy=(PBYTE)"DUMMY";
	if ( m_pAllocator ) delete m_pAllocator;
	m_pAllocator = new CLocalAllocator( pDummy, 32*1024, &hr );

    hr = DecideAllocator( m_pAllocator, NULL );
    if(FAILED(hr)) {

		delete  ((CLocalAllocator*)m_pAllocator);
		m_pAllocator = NULL;
		DbgLog((LOG_TRACE, 3, TEXT("Failed DecideAllocator hr=0x%x"), hr ));
        return hr;
    }

	((CLocalAllocator*)m_pAllocator)->AddRef();
	((CLocalAllocator*)m_pAllocator)->Commit();

    hr = Filter()->CompleteConnect( pReceivePin, this );
	if(FAILED(hr)) 
	{
		DbgLog((LOG_TRACE, 3, TEXT("Failed CompleteConnect  hr=0x%x"), hr ));
        return hr;
    }
	return hr;
}

HRESULT CSDeMuxInPin::Disconnect() 
{
	DbgLog((LOG_TRACE, 3, TEXT("InputPin Disconnect() ")));
    CAutoLock lock(&m_AccessLock);
	HRESULT hr;
    if(m_pReader) {
        m_pReader->Release();
        m_pReader = NULL;
    }

	if ( m_pAllocator )
	{
		((CLocalAllocator*)m_pAllocator)->Release();
		((CLocalAllocator*)m_pAllocator)->Decommit();
		delete  ((CLocalAllocator*)m_pAllocator);
		m_pAllocator = NULL;
	}

	hr = CBaseInputPin::Disconnect();
	Filter()->DisconnectConnection(this);
    return S_OK;
}

HRESULT CSDeMuxInPin::BreakConnect()
{
	HRESULT hr = CBaseInputPin::BreakConnect();
    Filter()->BreakConnect();
	return hr;
}


/*  End of stream */
STDMETHODIMP CSDeMuxInPin::EndOfStream()
{
	DbgLog((LOG_TRACE, 4, TEXT("InPin EndOfStream")));
    CAutoLock lck(&m_AccessLock);
    HRESULT hr = CheckStreaming();
    if (hr != S_OK) {
        return hr;
    }
	
    return S_OK;
}

/*  Forward BeginFlush() and EndFlush() to filter */
STDMETHODIMP CSDeMuxInPin::BeginFlush()
{
    DbgLog((LOG_TRACE, 3, TEXT("SplitterInputPin BeginFlush")));
	CAutoLock lck(&m_AccessLock);
    CBaseInputPin::BeginFlush();
	m_pReader->BeginFlush();
    return S_OK;
}

STDMETHODIMP CSDeMuxInPin::EndFlush()
{
    DbgLog((LOG_TRACE, 3, TEXT("SplitterInputPin EndFlush")));
    CAutoLock lck(&m_AccessLock);
	m_pReader->EndFlush();
	m_bDiscontinuity = TRUE;
    return CBaseInputPin::EndFlush();
}


HRESULT CSDeMuxInPin::Active()
{
	Filter()->Active();
    return CBaseInputPin::Active();
}

HRESULT CSDeMuxInPin::Inactive()
{
	DbgLog((LOG_TRACE, 3, TEXT("InActive InputPin, tCurrent:%d"), tCurrent/UNITS ));
    return CBaseInputPin::Inactive();
}

void CSDeMuxInPin::ContinueSample()
{
	m_bDiscontinuity = FALSE;
}

/*  forward connection stuff to filter */
HRESULT CSDeMuxInPin::CheckMediaType(const CMediaType *pmt)
{
    return Filter()->CheckInputType(pmt);
}

HRESULT CSDeMuxInPin::DecideAllocator( IMemAllocator * pAlloc, ALLOCATOR_PROPERTIES *pProps ) 
{
    ALLOCATOR_PROPERTIES *pRequest;
    ALLOCATOR_PROPERTIES Request;
    if(pProps == NULL) {
        Request.cBuffers = BUFFER_BLOCK_NUM;   //ZQ.
        Request.cbBuffer = BUFFER_BLOCK_SIZE; 
        Request.cbAlign =  1;
        Request.cbPrefix = 0;
        pRequest = &Request;
    }
    else {
        pRequest = pProps;
    }

	//we use LocalAlocator, not global allocator
	IMemAllocator*  pAlloc2;
    HRESULT hr = m_pReader->RequestAllocator( pAlloc, pRequest, &pAlloc2 );
	DbgLog((LOG_TRACE, 3, TEXT("CSDeMuxInPin DecideAllocator Buffer size=%d count=%d align=%d (0x%x-0x%x)"), 
		                        pRequest->cbBuffer, pRequest->cBuffers, pRequest->cbAlign, pAlloc, pAlloc2 ));
	if ( pAlloc == pAlloc2 )
	{
		DbgLog((LOG_TRACE, 3, TEXT("CSDeMuxInPin Source Filter agreed my allocator.") ));
	} else
	{
		if ( pRequest->cbAlign > 1 )
			DbgLog((LOG_TRACE, 3, TEXT("ERROR Source Filter Align:%d. we need Align=1"), pRequest->cbAlign ));

		DbgLog((LOG_TRACE, 3, TEXT("CSDeMuxInPin Source Filter didn't accept my allocator.") ));
		return VFW_E_TYPE_NOT_ACCEPTED;
	}

    return hr;
}

STDMETHODIMP CSDeMuxInPin::NotifyAllocator( IMemAllocator *pAllocator, BOOL bReadOnly )
{
    CheckPointer(pAllocator,E_FAIL);
	HRESULT hr;

	ALLOCATOR_PROPERTIES prop;
	ZeroMemory(&prop, sizeof(prop));
	hr = pAllocator->GetProperties( &prop );

	DbgLog((LOG_TRACE, 3, TEXT("CSDeMuxInPin NotifyAllocator Buffer size=%d count=%d align=%d"), 
		                        prop.cbBuffer, prop.cBuffers, prop.cbAlign ));  

    // Notify the base class about the allocator
    return CBaseInputPin::NotifyAllocator(pAllocator,bReadOnly);
	return hr;
} // NotifyAllocator


HRESULT CSDeMuxInPin::Length( ULONGLONG *pLength, ULONGLONG *pAvailableLength)
{
    LONGLONG Length, Available;
	HRESULT hr=0;
	if ( pLength )			 *pLength = 0;
	if ( pAvailableLength ) *pAvailableLength = 0;
	if ( m_pReader != NULL )
	{
	    hr = m_pReader->Length(&Length, &Available);
		if ( pLength )			 *pLength = Length;
		if ( pAvailableLength ) *pAvailableLength = Available;
	}
	return hr;

}

//#define ERROR_HANDLE_EOF	 0x80070026
//#define ERROR_INVALID_BLOCK  0x80070009
#define SAFE_SAMPLE_RELEASE( s ) if( s ){ m_pAllocator->SafeRelease( s ); s = NULL;}

HRESULT CSDeMuxInPin::PullSample( BOOL bDiscontinuity, IMediaSample* pSample )
{ 
	HRESULT hr=E_FAIL;
	pSample->SetActualDataLength( 0 );
	pSample->SetDiscontinuity( bDiscontinuity || m_bDiscontinuity );

    tNext = tCurrent +  (ULONGLONG)pSample->GetSize() * UNITS ;

	//when read ahread of online video, it hangs for data avilable, because sample size is big.
	//we don't allow read position beyond aviliable data
	LONGLONG llTotal, llAvailable;
	if ( m_pReader->Length( &llTotal, &llAvailable ) == S_OK )
	{
		llAvailable *= UNITS;
		if ( (LONGLONG)tNext > llAvailable )
		{
			//tNext = llAvailable;
			//if ( tCurrent > tNext-(ULONGLONG)pSample->GetSize() * UNITS )
			//	tCurrent -= tNext-(ULONGLONG)pSample->GetSize() * UNITS;
			//else
			//	tCurrent = 0;
			DbgLog((LOG_TRACE, 3, TEXT(">>>Read ahread of vailabel data.")  ));

		}
	}

	if ( tStop && tNext > tStop ) tNext = tStop;

	if ( m_bDiscontinuity ) 
	{
		DbgLog((LOG_TRACE, 3, TEXT("Pull Sample Discontinuity pos=0x%x."), tCurrent/UNITS ));
		m_bDiscontinuity = FALSE;
	}


	if (  llMaxPos != -1 && tCurrent >= llMaxPos ) 
	{
		return VFW_E_FILE_TOO_SHORT;
	}

	if ( tCurrent >= tNext ) tNext = tCurrent + 1;

    pSample->SetTime( (LONGLONG*)&tCurrent, (LONGLONG*)&tNext );
	//DbgLog((LOG_TRACE, 2, TEXT("######### set tCurrunt:%d."),  tCurrent/UNITS ));
	//DbgLog((LOG_TRACE, 3, TEXT("[%d - %d]"), tCurrent/UNITS, tNext/UNITS  ));

	hr = m_pReader->SyncReadAligned( pSample );
    if( FAILED(hr) ) 
	{
		if ( 0x80070026 == hr )
		{
			EndOfStream(); //end of stream
			m_nPullState = END_OF_DATA;
			DbgLog((LOG_TRACE, 3, TEXT("End of the Sample %d."), tCurrent/UNITS));
		} else
		if (hr == VFW_E_FILE_TOO_SHORT)
		{
			EndOfStream(); //end of stream
			m_nPullState = END_OF_DATA;
			DbgLog((LOG_TRACE, 2, TEXT("File duration cut short, end of file") ));
		}
		else
		{
			EndOfStream(); //end of stream
			m_nPullState = END_OF_DATA;
			DbgLog((LOG_TRACE, 3, TEXT("Request a Sample failed hr=0x%x"), hr ));
		    NotifyError(hr);
		}
    } else
	{

		if (hr == VFW_E_FILE_TOO_SHORT)
		{
			m_nPullState = END_OF_DATA;
			DbgLog((LOG_TRACE, 2, TEXT("File duration cut short, end of file.") ));
		} else
		if( FAILED(hr) ) 
		{
			m_nPullState = END_OF_DATA;
			DbgLog((LOG_TRACE, 3, TEXT("Wait a Sample failed hr=0x%x."), hr));
			NotifyError(hr);
		} else 
		{
			if ( (DWORD)pSample->GetActualDataLength() > (DWORD)pSample->GetSize() )
			{
				hr = 0x80070009; //ERROR_INVALID_BLOCK
				DbgLog((LOG_TRACE, 3, TEXT("Invalid Block (Data length is bigger (%d)t han buffer size (%d).  hr=0x%x"),
					    hr, pSample->GetActualDataLength(), pSample->GetSize() ));
				m_nPullState = END_OF_DATA;
			} else
			{

				if ( pSample->GetActualDataLength() == 0 )
				{
					m_nPullState = END_OF_DATA;
					DbgLog((LOG_TRACE, 2, TEXT("get an empty Sample.") ));
				}

#ifdef DUMP_ENABLE
	dump_sample( pSample );
#endif
				tNext = tCurrent + pSample->GetActualDataLength()*UNITS;
				tCurrent = tNext;
				return S_OK;
			}
			//DbgLog((LOG_TRACE, 3, TEXT("Delivered a Sample.")));
		}
	}

    return hr;
}

void CSDeMuxInPin::ResetPullSampeSate()
{
	m_nPullState = 0;
}

int CSDeMuxInPin::CheckPullSampeState()
{
	return m_nPullState;
}

int CSDeMuxInPin::ReadSampleData( unsigned char* pBuffer, int nBufferSize )
{
	HRESULT hr;
	long  lSize;
	BYTE *pData;

	IMediaSample *pSample;
	CLocalAllocator* pLocalAlloc = (CLocalAllocator*)m_pAllocator;
	pLocalAlloc->SetupBuffer( pBuffer, nBufferSize );
	pSample= pLocalAlloc->GetSample();

	hr = PullSample( FALSE, pSample );
	if ( hr == S_OK )
	{
		lSize  = pSample->GetActualDataLength();
		if ( lSize == 0 )
			return 0;
		if ( FAILED( hr = pSample->GetPointer(&pData) ))
			return 0;
		ASSERT( lSize <= nBufferSize );
		memcpy( pBuffer, pData, lSize );

		return (int)lSize;
	}
	if ( pSample != NULL )
		pSample->Release();

	return 0;
}


ULONGLONG CSDeMuxInPin::GetLength( )
{
	LONGLONG llTotal, llAvailable;
	HRESULT hr = m_pReader->Length( &llTotal, &llAvailable );
	if ( hr == S_OK )
		return llTotal;
	return -1;
}

ULONGLONG CSDeMuxInPin::GetActualLength( )
{
	LONGLONG llTotal, llAvailable;
	HRESULT hr = m_pReader->Length( &llTotal, &llAvailable );
	if ( hr == S_OK )
		return llAvailable;
	return -1;
}

ULONGLONG CSDeMuxInPin::GetCurrentPos( ) 
{ 
	return  tCurrent/UNITS; 
}

ULONGLONG CSDeMuxInPin::GetStopPos( )
{ 
	return  tStop/UNITS; 
}

void CSDeMuxInPin::SetStopPos( ULONGLONG pos )
{
	tStop = pos * UNITS;
}

void CSDeMuxInPin::SetCurrentPos( ULONGLONG pos )
{
	
	LONGLONG llTotal, llAvailable;
	if ( m_pReader->Length( &llTotal, &llAvailable ) == S_OK )
	{
		if ( (LONGLONG)pos > llAvailable )
			pos = llAvailable;
	}

	tCurrent = pos * UNITS;;
}



HRESULT CSDeMuxInPin::GetSampleTimeRange( ULONGLONG *pStart, ULONGLONG *pStop )
{
	if ( pStart )	*pStart = tStart/UNITS;
	if ( pStop )    *pStop =  tStop/UNITS;
	return S_OK;
}


void CSDeMuxInPin::dump_data( char* pData, int dwBytes )
{
	if ( fd == NULL ) return;
	if ( dwBytes > 0)	fwrite( pData, 1, dwBytes, fd );
}

void CSDeMuxInPin::dump_sample( IMediaSample *pSample )
{
	BYTE* pBuffer;
	size_t Length = 0;
	size_t offset = 0;
	if ( fd == NULL ) return;
	if ( FAILED(  pSample->GetPointer( &pBuffer ) ) ) return;
	Length = pSample->GetActualDataLength( );
	if ( Length > 0)
	{
		offset += fwrite( pBuffer,1, Length, fd );
		if ( offset == 0x10000 )
			printf( "\n" );
	}
}

unsigned long CSDeMuxInPin::cksum_sample( IMediaSample *pSample )
{
	unsigned long sum = 0;
	unsigned char* pBuffer;
	int   Length;
	if ( FAILED(  pSample->GetPointer( &pBuffer ) ) ) return -1;
	Length = pSample->GetActualDataLength( );
	if ( Length > 0)
	{
		int i;
		for ( i = 0; i<Length; i++ )
		{
			unsigned long a = pBuffer[i];
			sum += a;
		}
	}
	return sum;
}

void CSDeMuxInPin::open_dump( )
{
	char fname[MAX_PATH];
	char path[MAX_PATH]={0};
	size_t len;
	sprintf( fname, "DUMP_INPUT_DATA.ENABLE" );
	fd = fopen( fname, "r" );
	if ( fd == NULL ) return;
	len = fread( path,1, MAX_PATH-2, fd );
	if ( len )
		if ( path[len-1] != '\\' ) path[len] = '\\';
	fclose( fd );
	sprintf( fname, "%s%s.bin", path, "FilterInput"  );
	fd = fopen( fname, "wb" );
	offset = 0 ;
	DbgLog((LOG_TRACE, 2, TEXT("DeMuxInputPin dump file %s  fd=0x%x."),  fname, fd ) );
}
void CSDeMuxInPin::close_dump( )
{
	if ( fd != NULL )
		fclose( fd );
	fd = NULL;
}

void CSDeMuxInPin::rewind_dump( )
{
	if ( fd != NULL )
		fseek( fd, 0, SEEK_SET );
	offset = 0 ;
	DbgLog((LOG_TRACE, 2, TEXT("DeMuxInputPin dump file rewinded.")) );
}

