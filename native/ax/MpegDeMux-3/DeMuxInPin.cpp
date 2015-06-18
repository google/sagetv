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


#include "DeMuxInPin.h"
#include "DeMuxOutPin.h"
#include "DeMuxFilter.h"
#include "../../../third_party/Microsoft/FileSource/strconv.h"

#define DUMP_ENABLE  1
//#undef DUMP_ENABLE



/*  -- Input pin methods -- */

/*  Constructor */
CDeMuxInPin::CDeMuxInPin(
    CDeMuxFilter *pFilter,
    HRESULT *phr
) : CBaseInputPin( NAME("CDeMuxInPin"),
			 pFilter,
             &pFilter->m_csFilter,
             phr,
             L"Input"),
    m_pReader(NULL),
    m_pAlloc(NULL),
	tNext(0), tCurrent(0),
	tStart(0), tStop(-1), 
	llMaxPos( -1 ),
	m_dwBlockSize( BLOCK_SIZE ),
	m_dwAllocSize( BLOCK_SIZE * BLOCK_NUM ),
	m_dwBufferAlign( 512 ),
	m_bDiscontinuity( TRUE )
{
	DbgLog((LOG_TRACE, 3, TEXT("InputPin created() ")));

	
#ifdef DUMP_ENABLE
	open_dump( );
#endif

}

CDeMuxInPin::~CDeMuxInPin( )
{

#ifdef DUMP_ENABLE
	close_dump( );
#endif

}

/*  Connection stuff */
HRESULT CDeMuxInPin::CheckConnect(IPin *pPin)
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


HRESULT CDeMuxInPin::CompleteConnect(IPin *pReceivePin)
{
	HRESULT hr;
	DbgLog((LOG_TRACE, 3, TEXT("InputPin connect() ")));
	
	tCurrent =0;
	tStop = -1;	
	CBaseInputPin::CompleteConnect(pReceivePin);
    hr = Filter()->CompleteConnect( pReceivePin, this );
	if(FAILED(hr)) 
	{
		DbgLog((LOG_TRACE, 3, TEXT("Failed CompleteConnect  hr=0x%x"), hr ));
        return hr;
    }

	//if it's not TS format share Filter allocator with output pin
	if ( Filter()->NeedPrivateAllocator() )
	{
		m_dwBlockSize = Filter()->m_dwBlockSize*4; //32*1024;     //ZQ BLOCK SIZE CONTROL
        m_dwAllocSize = 2*m_dwBlockSize;
	}

	m_pAllocator = Filter()->GetAllocator();
	if ( m_pAllocator == 0 )
	{
		DbgLog((LOG_TRACE, 3, TEXT("Sequential Pull Pin Allocator not found")));
        return hr;
	}

    hr = DecideAllocator( m_pAllocator, NULL );
    if(FAILED(hr)) {
		DbgLog((LOG_TRACE, 3, TEXT("Failed DecideAllocator hr=0x%x"), hr ));
        return hr;
    }

	m_pAllocator->AddRef();
	m_pAlloc->Commit();
	return hr;
}

HRESULT CDeMuxInPin::Disconnect() 
{
	DbgLog((LOG_TRACE, 3, TEXT("InputPin Disconnect() ")));
    CAutoLock lock(&m_AccessLock);
	HRESULT hr;
    if(m_pReader) {
        m_pReader->Release();
        m_pReader = NULL;
    }

	if( m_pAlloc ) {
		m_pAlloc->Decommit();
	    m_pAlloc->Release();
	    m_pAlloc = NULL;
	}

	Filter()->AllocatorDecommit( );

	//hr = CBaseInputPin::BreakConnect();
	hr = CBaseInputPin::Disconnect();
	Filter()->DisconnectConnection(this);
    return S_OK;
}

HRESULT CDeMuxInPin::BreakConnect()
{
	HRESULT hr = CBaseInputPin::BreakConnect();
    Filter()->BreakConnect();
	return hr;
}


/*  End of stream */
STDMETHODIMP CDeMuxInPin::EndOfStream()
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
STDMETHODIMP CDeMuxInPin::BeginFlush()
{
    DbgLog((LOG_TRACE, 3, TEXT("SplitterInputPin BeginFlush")));
	CAutoLock lck(&m_AccessLock);
    CBaseInputPin::BeginFlush();
	 m_pReader->BeginFlush();
    return S_OK;
}

STDMETHODIMP CDeMuxInPin::EndFlush()
{
    DbgLog((LOG_TRACE, 3, TEXT("SplitterInputPin EndFlush")));
    CAutoLock lck(&m_AccessLock);
	m_pReader->EndFlush();
	m_bDiscontinuity = TRUE;

    return CBaseInputPin::EndFlush();
}


HRESULT CDeMuxInPin::Active()
{
	DbgLog((LOG_TRACE, 3, TEXT("Active InputPin tCurrent:%d"), tCurrent/UNITS ));
	m_pAlloc->Commit();
	tCurrent = tStart;
	Filter()->Active();
    return CBaseInputPin::Active();
}

HRESULT CDeMuxInPin::Inactive()
{
	DbgLog((LOG_TRACE, 3, TEXT("InActive InputPin, tCurrent:%d"), tCurrent/UNITS ));
    return CBaseInputPin::Inactive();
}

void CDeMuxInPin::ContinueSample()
{
	m_bDiscontinuity = FALSE;
}

/*  forward connection stuff to filter */
HRESULT CDeMuxInPin::CheckMediaType(const CMediaType *pmt)
{
    return Filter()->CheckInputType(pmt);
}

HRESULT CDeMuxInPin::DecideAllocator( IMemAllocator * pAlloc, ALLOCATOR_PROPERTIES *pProps ) 
{
    ALLOCATOR_PROPERTIES *pRequest;
    ALLOCATOR_PROPERTIES Request;
    if(pProps == NULL) {
        Request.cBuffers = m_dwAllocSize/m_dwBlockSize;   //ZQ.
        Request.cbBuffer = m_dwBlockSize;
        Request.cbAlign =  0;
        Request.cbPrefix = 0;
        pRequest = &Request;
    }
    else {
        pRequest = pProps;
    }
    HRESULT hr = m_pReader->RequestAllocator( pAlloc, pRequest, &m_pAlloc );
	DbgLog((LOG_TRACE, 3, TEXT("CDeMuxInPin DecideAllocator Buffer size=%d count=%d align=%d (0x%x-0x%x)"), 
		                        Request.cbBuffer, Request.cBuffers, Request.cbAlign, pAlloc, m_pAlloc ));
	ASSERT( m_dwBlockSize % Request.cbBuffer == 0 );
	m_dwBufferAlign = Request.cbAlign;

    return hr;
}

STDMETHODIMP CDeMuxInPin::NotifyAllocator( IMemAllocator *pAllocator, BOOL bReadOnly )
{
    CheckPointer(pAllocator,E_FAIL);
	HRESULT hr;

    // Free the old allocator if any
	Filter()->AllocatorDecommit( );

	ALLOCATOR_PROPERTIES prop;
	ZeroMemory(&prop, sizeof(prop));
	hr = pAllocator->GetProperties( &prop );
	if ( hr == S_OK )
		hr =Filter()->AllocatorCommit( &prop );

	DbgLog((LOG_TRACE, 3, TEXT("CDeMuxInPin NotifyAllocator Buffer size=%d count=%d align=%d"), 
		                        prop.cbBuffer, prop.cBuffers, prop.cbAlign ));  

    // Notify the base class about the allocator
    return CBaseInputPin::NotifyAllocator(pAllocator,bReadOnly);
	return hr;
} // NotifyAllocator


HRESULT CDeMuxInPin::Length( ULONGLONG *pLength, ULONGLONG *pAvailableLength)
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

HRESULT CDeMuxInPin::PullSample( BOOL bDiscontinuity, IMediaSample** ppSample )
{ 
	HRESULT hr=E_FAIL;
    IMediaSample* pSample = NULL ;

	*ppSample = NULL;
    hr = m_pAlloc->GetBuffer(&pSample, NULL, NULL, 0);
    if( FAILED(hr) ) 
	{
		//SAFE_SAMPLE_RELEASE( pSample );
		if ( VFW_E_NOT_RUNNING == hr )
			DbgLog((LOG_TRACE, 3, TEXT("InputPin Alloc is not running.") ));
		else
			DbgLog((LOG_TRACE, 3, TEXT("PinputPin Alloc a Sample buffer failed, hr=0x%x"), hr ));
		*ppSample = pSample;
        return hr;
    }

	*ppSample = pSample;
	pSample->SetActualDataLength( 0 );
	pSample->SetDiscontinuity( bDiscontinuity || m_bDiscontinuity );

    tNext = tCurrent +  (ULONGLONG)pSample->GetSize() * UNITS ;
	if ( tStop && tNext > tStop ) tNext = tStop;
	//if ( tStop && tCurrent > tStop ) tCurrent = tStop;

	if ( m_bDiscontinuity ) 
	{
		DbgLog((LOG_TRACE, 3, TEXT("Pull Sample Discontinuity pos=0x%x."), tCurrent/UNITS ));
		m_bDiscontinuity = FALSE;
	}

	if ( m_dwBufferAlign > 1 )
	{
		tCurrent = AlignLeft( tCurrent/UNITS,  m_dwBufferAlign )*UNITS;
		tNext    = AlignRight( tNext/UNITS,    m_dwBufferAlign )*UNITS;
	}

	if (  llMaxPos != -1 && tCurrent >= llMaxPos ) 
	{
		tCurrent = llMaxPos;
		return VFW_E_FILE_TOO_SHORT;
	}

	if ( tCurrent >= tNext ) tNext = tCurrent + 1;

    pSample->SetTime( (LONGLONG*)&tCurrent, (LONGLONG*)&tNext );
	//DbgLog((LOG_TRACE, 3, TEXT("[%d - %d]"), tCurrent/UNITS, tNext/UNITS  ));

	hr = m_pReader->SyncReadAligned( pSample );
    if( FAILED(hr) ) 
	{
		if ( 0x80070026 == hr )
		{
			EndOfStream(); //end of stream
			DbgLog((LOG_TRACE, 3, TEXT("End of the Sample %d"), tCurrent/UNITS ));
		} else
		if (hr == VFW_E_FILE_TOO_SHORT)
		{
			EndOfStream(); //end of stream
			DbgLog((LOG_TRACE, 2, TEXT("File duration cut short, end of file.")));
		}
		else
		{
			EndOfStream(); //end of stream
			DbgLog((LOG_TRACE, 3, TEXT("Request a Sample failed hr=0x%x"), hr ));
		    NotifyError(hr);
		}
    } else
	{

		if (hr == VFW_E_FILE_TOO_SHORT)
		{
			DbgLog((LOG_TRACE, 2, TEXT("File duration cut short, end of file.")));
		} else
		if( FAILED(hr) ) 
		{
			DbgLog((LOG_TRACE, 3, TEXT("Wait a Sample failed hr=0x%x"), hr));
			NotifyError(hr);
		} else 
		{
			if ( (DWORD)pSample->GetActualDataLength() > (DWORD)pSample->GetSize() )
			{
				hr = 0x80070009; //ERROR_INVALID_BLOCK
				DbgLog((LOG_TRACE, 3, TEXT("Invalid Block (Data length is bigger (%d)t han buffer size (%d).  hr=0x%x"),
					    hr, pSample->GetActualDataLength(), pSample->GetSize() ));
				//SAFE_SAMPLE_RELEASE( pSample );
			} else
			{
				LONGLONG newStart, newStop;
				pSample->GetTime(&newStart, &newStop);
				//DbgLog((LOG_TRACE, 2, TEXT("newStart=%s newStop=%s"),
				//		(LPCSTR)Disp(newStart, CDISP_DEC),
				//		(LPCSTR)Disp(newStop, CDISP_DEC)));
				if ( tCurrent != newStart )
				{
					DbgLog((LOG_TRACE, 2, TEXT("Playback tried to go before front of buffer, aligning back into buffer")));
					// This happens when we're trying to read part of a pause buffer
					// that's been overwritten and we're getting pushed forward to valid
					// data.
					tCurrent = newStart;
					tNext = newStop;
					pSample->SetDiscontinuity(TRUE);
				}

#ifdef DUMP_ENABLE
	dump_sample( pSample );
#endif

				*ppSample = pSample;
				tCurrent = tNext;
				return S_OK;
			}
			//DbgLog((LOG_TRACE, 3, TEXT("Delivered a Sample.")));
		}
	}

    return hr;
}

ULONGLONG CDeMuxInPin::GetCurrentPos( ) 
{ 
	return  tCurrent/UNITS; 
}

ULONGLONG CDeMuxInPin::GetStopPos( )
{ 
	return  tStop/UNITS; 
}

void CDeMuxInPin::SetStopPos( ULONGLONG pos )
{
	ULONGLONG rem = pos % m_dwBlockSize;
	if ( rem )
	{
		pos -= rem;
		pos += m_dwBlockSize;
	}
	tStop = pos * UNITS;
}

void CDeMuxInPin::SetCurrentPos( ULONGLONG pos )
{
	pos -= pos % m_dwBlockSize;
	tCurrent = pos * UNITS;;
}

void CDeMuxInPin::SetMaxiumPos( ULONGLONG max )
{
	if ( max != -1 )
		llMaxPos = max * UNITS;
	else
		llMaxPos = -1;
}


HRESULT CDeMuxInPin::SetSamplePosRange( ULONGLONG start, ULONGLONG stop )
{
	start -= start % m_dwBlockSize;
	stop  -= stop % m_dwBlockSize;

	tStart = start * UNITS;
	tStop = stop * UNITS;

	tCurrent = tStart;
	m_bDiscontinuity = TRUE;
	DbgLog((LOG_TRACE, 2, TEXT("------>SET Sample start pos %s"),  (LPCTSTR)Disp( tStart/UNITS, DISP_DEC )  ));

	return S_OK;
}

HRESULT CDeMuxInPin::GetSampleTimeRange( ULONGLONG *pStart, ULONGLONG *pStop )
{
	if ( pStart )	*pStart = tStart/UNITS;
	if ( pStop )    *pStop =  tStop/UNITS;
	return S_OK;
}


void CDeMuxInPin::dump_data( char* pData, int dwBytes )
{
	if ( fd == NULL ) return;
	if ( dwBytes > 0)	fwrite( pData, 1, dwBytes, fd );
}

void CDeMuxInPin::dump_sample( IMediaSample *pSample )
{
	BYTE* pBuffer;
	int   Length;
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

unsigned long CDeMuxInPin::cksum_sample( IMediaSample *pSample )
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

void CDeMuxInPin::open_dump( )
{
	char fname[MAX_PATH];
	char path[MAX_PATH]={0};
	int len;
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
void CDeMuxInPin::close_dump( )
{
	if ( fd != NULL )
		fclose( fd );
	fd = NULL;
}

void CDeMuxInPin::rewind_dump( )
{
	if ( fd != NULL )
		fseek( fd, 0, SEEK_SET );
	offset = 0 ;
	DbgLog((LOG_TRACE, 2, TEXT("DeMuxInputPin dump file rewinded.")) );
}

