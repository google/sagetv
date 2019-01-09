//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: PsiParser.cpp
//
// Desc: DirectShow sample code - 
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)

#ifndef _WIN64
  #define _USE_32BIT_TIME_T
#endif

#include <streams.h>

#include <bdaiface.h>
#include <bdatypes.h>

#include "dbg.h"
#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <io.h>
#include <fcntl.h>
#include <share.h>
#include <sys/types.h>
#include <sys/stat.h>

#include "iTSSplitter.h"
#include "TSSplitterGuid.h"
#include "NativeCore.h"
#include "Remuxer.h"
#include "ChannelScan.h"
#include "Scan.h"
#include "TSSplitFilter.h"

#define TS_PACKET_LENGTH	188

#define STATE_CHECK_SIZE	     (1024*1024*5)    //5M
#define STATE_CHECK_SIZE_X_2	 (STATE_CHECK_SIZE*2)
#define STATE_CHECK_SIZE_X_4	 (STATE_CHECK_SIZE*4)
#define STATE_CHECK_SIZE_X_8	 (STATE_CHECK_SIZE*8)

#ifdef REMOVE_LOG	
#define SageLog(x)
#else
#define SageLog(x)   _sagelog x
#endif
void _sagelog( int type, int level, const char* cstr, ... );

static bool flog_enabled= false;
static void logfile( char* str )
{
	FILE* fp;
	if( !flog_enabled ) return;
	fp = fopen( "native.log", "a+" ); 
	if ( fp != NULL )
	{
		time_t ct;
		struct tm *ltm;
		time(&ct); ltm=localtime( &ct );
		fprintf( fp, "%02d/%02d %02d:%02d:%02d ", ltm->tm_mon+1, ltm->tm_mday,
		ltm->tm_hour, ltm->tm_min, ltm->tm_sec );  

		fprintf( fp, "%s",  str );
		fflush( fp );
		fclose( fp );
	}
}
static void _flog_local_check()
{

	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true; //enable local flog
		_enable_native_log( ); //enable native code log
		fclose( fp );
	}

	TUNE_SIZE_CHECK( );
	if ( STRUCT_TUNE_SIZE != sizeof( TUNE ) )
	{
		fp = fopen( "TSSplitter.log", "a+" );
		if ( fp != NULL )
		{
			fprintf( fp, "WARING: please check aligment byte setting!!! struct TUNE size isn't right\n" );
			fclose( fp );
			flog_enabled = true;
		}
	}

}

char* GUID2String( const GUID* guid, char* str, size_t sz )
{
    snprintf(str, sz, "{%08x-%04x-%04x-%02x%02x-%02x%02x%02x%02x%02x%02x}",
             guid->Data1, guid->Data2, guid->Data3,
             guid->Data4[0], guid->Data4[1], guid->Data4[2], guid->Data4[3],
             guid->Data4[4], guid->Data4[5], guid->Data4[6], guid->Data4[7]);
    return str;
}

//
// Receive
//
// Here's the next block of data from the stream
//
extern "C" {
int SearchFrameStart( const unsigned char* pData, int nSize, const unsigned char **ppStart );

static int OutputDump( void* pContext, void* pData, int nSize )
{
	CTSParserFilter *pFilter = (CTSParserFilter *)pContext;
	OUTPUT_DATA   *pOutputData = (OUTPUT_DATA*)pData;
	unsigned char* pBlockData = pOutputData->data_ptr;
	int nBytes = pOutputData->bytes;
	unsigned long dwUsedBytes;
	unsigned long dwGroupFlag = pOutputData->group_flag;
	int nGroupStart = pOutputData->start_offset;

//ZQ REMOVE ME 
	/*
	if ( pOutputData->fourcc == SAGE_FOURCC( "MPGV" ) )
	{
		int found_I_frame = 0;
		if ( dwGroupFlag  )
		{
			const unsigned char* frame_start_ptr;
			unsigned char *start_ptr;
			int frame_byes, frame_type;
			if ( nGroupStart > 0 && nBytes-nGroupStart > 0 )
			{
				start_ptr = pBlockData + nGroupStart;
				frame_byes= nBytes-nGroupStart;
			} else
			{
				start_ptr = pBlockData;
				frame_byes= nBytes;
			}
			frame_type = SearchFrameStart( start_ptr, frame_byes, &frame_start_ptr );
			if ( frame_type == 1 )
			{
				static int nIframeCount=0;
				nIframeCount++;
				if ( frame_start_ptr-start_ptr > 20 )
				{
					SageLog(( LOG_TRACE, 3, "------------->I frame isn't aligmnet at header (%d, %d)<-------------",nIframeCount, frame_start_ptr-start_ptr  ));
				}
				found_I_frame = 1;
			} else
			{
				static int nBframeCount=0;
				nBframeCount++;
			} 
		}
		{
			const unsigned char* frame_start_ptr;
			int frame_type = SearchFrameStart( pBlockData, nBytes, &frame_start_ptr );
			if ( frame_type == 1 && found_I_frame == 0 )
			{
				static int n = 0;
				SageLog(( LOG_TRACE, 3, "------------->Missing I frame (%d, %d %d)<-------------",++n, frame_start_ptr- pBlockData, nGroupStart   ));
			}
		}
	} 
	*/
//ZQ REMOVE ME

	//split data at boundary
	if (  dwGroupFlag && nGroupStart > 0 && nBytes-nGroupStart > 0 )
	{
		unsigned long dwUsedBytes1, dwUsedBytes2;
		pFilter->SendPacket( 0, pBlockData, nGroupStart, &dwUsedBytes1 );
		pFilter->SendPacket( 1, pBlockData + nGroupStart, nBytes-nGroupStart, &dwUsedBytes2 );
		dwUsedBytes = dwUsedBytes1+dwUsedBytes2;
	} else
	{
		if ( dwGroupFlag )
			pFilter->SendPacket( 1, pBlockData, nBytes, &dwUsedBytes );
		else
			pFilter->SendPacket( 0, pBlockData, nBytes, &dwUsedBytes );
	}

	return nBytes;
}

}

int MemAllocHook( void* pContext, const unsigned char* pData, int lDataLen )
{
	CTSParserFilter *pFilter = (CTSParserFilter *)pContext;
	return 1;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// CreateInstance
//
// This goes in the factory template table to create new instances
//
CUnknown * WINAPI CTSParserFilter::CreateInstance(LPUNKNOWN pUnk, HRESULT *phr)
{
	_flog_local_check();
    return new CTSParserFilter(pUnk, phr);

} // CreateInstance

//
// Constructor
//
// Create the filter, and input pin
//

///////////////////////////////////////////////////////////////

//
// Constructor
//
CCritSec m_gTSSplitterLock; //protect memory report of native core, because they are global variables
CTSParserInputPin::CTSParserInputPin( CTSParserFilter *pFilter,
                                        LPUNKNOWN pUnk,
                                        CCritSec *pLock,
                                        CCritSec *pReceiveLock,
                                        HRESULT *phr) 
: CRenderedInputPin(NAME("CTSSplitterInputPin"),
                    (CBaseFilter *) pFilter,    // Filter
                    pLock,                      // Locking
                    phr,                        // Return code
                    L"Input")                   // Pin name
, m_pFilter(pFilter)
, m_pReceiveLock(pReceiveLock)
, m_bCreated(false)
{
	{
		CAutoLock lock(&m_gTSSplitterLock);
		MEMORY_TRACK();
	}

	m_pTSRemuxer = (REMUXER*)OpenRemuxStream( REMUX_STREAM , &m_Tune, 
		                       MPEG_TS, MPEG_PS, 
							   NULL, NULL, //MemAllocHook, m_pFilter,
							   OutputDump, m_pFilter ); 

	m_bCreated = true;;
	m_lUnusedBytes = 0;

	m_nExpectedBytes = 0;
	m_nAlignBytes = 0;

	m_pCScan = NULL;
	m_bScanChannel = 0;

	m_mt.SetType(&MEDIATYPE_Stream);
	m_mt.SetSubtype(&MEDIASUBTYPE_MPEG2_TRANSPORT);
	m_bIsSagePushSource = FALSE;
	SetupEPGDumpLanguage( m_pTSRemuxer, LANGUAGE_CODE( "eng" ) ); //set default language
	SetDefaultAudioLanguage( m_pTSRemuxer, LANGUAGE_CODE( "eng" ) );

} // (Constructor)


//
// Destructor 
//
CTSParserInputPin::~CTSParserInputPin()
{

  if ( m_pTSRemuxer != NULL )
	  CloseRemuxStream( m_pTSRemuxer );
  
  if ( m_pCScan != NULL )
	 delete m_pCScan;
  
  {
	CAutoLock lock(&m_gTSSplitterLock);
	MEMORY_REPORT();
  }

} // (Destructor)



HRESULT CTSParserInputPin::GetParser( REMUXER** ppSplt )
{
    CAutoLock lock(m_pReceiveLock);

    if( m_pTSRemuxer != NULL)
        *ppSplt = m_pTSRemuxer;
    else
        return E_INVALIDARG;

    return NOERROR;
} 

HRESULT CTSParserInputPin::GetChannelScan( CScan** ppScan )
{
    CAutoLock lock(m_pReceiveLock);

    if( m_pCScan != NULL)
        *ppScan  = m_pCScan;
    else
        return E_INVALIDARG;

    return NOERROR;
} 


//
// CheckMediaType
//
// Check that we can support a given proposed type
//

HRESULT CTSParserInputPin::CheckMediaType(const CMediaType *pMediaType)
{
	char guid[128];
	m_bIsSagePushSource = ( pMediaType->formattype == FORMAT_SAGETVInfo );
	GUID2String( &pMediaType->formattype, guid, sizeof(guid) );
	DbgLog((LOG_TRACE, 2, TEXT("Connected Inpin formattype:%s, cbFormat:%d, isSagePush:%d"), 
		guid, pMediaType->cbFormat, m_bIsSagePushSource  ) );


    if( *(pMediaType->Type()) == MEDIATYPE_Stream )
	{
        return S_OK;
    }

    return VFW_E_INVALIDMEDIATYPE;
} // CheckMediaType

// returns the preferred formats for a pin
HRESULT CTSParserInputPin::GetMediaType(
    int iPosition,
    CMediaType *pMediaType
)
{
	//CMediaType mt=;
    if ( iPosition == 0 )
	{
            *pMediaType = m_mt;;
            return S_OK;
    }
    return VFW_S_NO_MORE_ITEMS;
}



HRESULT CTSParserInputPin::GetAllocSize( DWORD* dwSize )
{
	ALLOCATOR_PROPERTIES Props;
	HRESULT hr;
	*dwSize = 0;
	if ( m_pAllocator == NULL )
		return E_FAIL;

	memset( &Props, 0x0, sizeof( Props ) );
	hr = m_pAllocator->GetProperties( &Props );
	if ( hr == S_OK )
		*dwSize = Props.cbBuffer;
	return hr;
    
}

HRESULT CTSParserInputPin::CompleteConnect(IPin *pReceivePin)
{
  CRenderedInputPin::CompleteConnect(pReceivePin);
  if ( m_pTSRemuxer == NULL ) 
	  return S_OK;
  return S_OK;
}

HRESULT CTSParserInputPin::Disconnect()
{
  SageLog((LOG_TRACE, 3, TEXT("Disconnected InputPin") ));
  return CRenderedInputPin::Disconnect();
}

HRESULT CTSParserInputPin::Receive(IMediaSample * pSample)
{
    // Lock this with the receiver lock
    CAutoLock lock(m_pReceiveLock);

    // Check all is well with the base class, if not, drop it.
    HRESULT hr = CBaseInputPin::Receive(pSample);
    if(hr != S_OK)
    {
        return hr;
    }

    // Send the sample to the table processor object for processing

    CHECK_BADPTR( TEXT("invalid sample"), pSample);

    BYTE* pData = NULL;
    hr = pSample->GetPointer(&pData);
    CHECK_ERROR( TEXT("pSample->GetPointer() failed"), hr);
    CHECK_BADPTR( TEXT("invalid data point"), pData);

    long lDataLen;
    lDataLen =  pSample->GetActualDataLength();

	//we need check TS format, to setup TSDemuxer
	if ( m_bIsSagePushSource && m_pFilter->m_dwDumpedBytes == 0 )
	{
		int uInputStreamFormat = CheckFormat( pData, lDataLen );
		if ( uInputStreamFormat && uInputStreamFormat != MPEG_TS )
		{
			ChangeDemuxerTSStreamType( m_pTSRemuxer, uInputStreamFormat );
		}
	}

	//use debug data file to replace input data for debug
	if  ( m_pFilter->m_hDebugFileSource != NULL )
	{
		int lReadBytes = (long)fread( pData, 1, lDataLen, m_pFilter->m_hDebugFileSource );
		if ( feof( m_pFilter->m_hDebugFileSource ) )
			fseek(  m_pFilter->m_hDebugFileSource, 0, SEEK_SET ); //looping reading
		lDataLen = lReadBytes;
	}

	if ( !m_pFilter->m_bParserEnabled )
		return hr;

	//pass thrus data pin
	DWORD Bytes, dwLength, UsedBytes;
	BYTE *pStart;
	DWORD dwAllocSize;
	pStart = pData;
	dwLength = lDataLen;

	if (  lDataLen > 0 && m_pFilter->m_dwDumpSize && m_pFilter->GetDumpSize( &dwAllocSize ) == S_OK &&
			m_pFilter->m_dwDumpedBytes < m_pFilter->m_dwDumpSize  )
	{
		while ( dwLength > 0   )
		{
			Bytes =  dwAllocSize > dwLength ? dwLength : dwAllocSize;
			m_pFilter->DumpOut( pStart, Bytes, &UsedBytes );
			dwLength -= UsedBytes;
			pStart += UsedBytes;
		}
	}
	m_pFilter->m_dwDumpedBytes += lDataLen;

	if ( m_pFilter->m_bPassThroughEnabled  )
	{
		pStart = pData;
		dwLength = lDataLen;
		if (  m_pFilter->GetPassThroughSize( &dwAllocSize ) == S_OK )
		{
			while ( dwLength > 0  )
			{
				Bytes =  dwAllocSize > dwLength ? dwLength : dwAllocSize;
				m_pFilter->PassThrough( pStart, Bytes, &UsedBytes );
				dwLength -= UsedBytes;
				pStart += UsedBytes;
			}
		}
	}


	//Remux data to push data out
	if ( !m_bScanChannel && m_pTSRemuxer && lDataLen ) 
	{
		int nUsedBytes;
		pStart   = pData;
		dwLength = lDataLen;
		if ( m_nExpectedBytes )
		{
			int nExpectedBytes2;
			memcpy( m_cAlignBuffer+m_nAlignBytes, pStart, m_nExpectedBytes );
			nUsedBytes = PushRemuxStreamData( m_pTSRemuxer, m_cAlignBuffer, m_nAlignBytes+m_nExpectedBytes, &nExpectedBytes2 );
			if ( nExpectedBytes2 == 0 )
			{
				pStart   +=  m_nExpectedBytes;
				dwLength -=  m_nExpectedBytes;
			}
		}

		nUsedBytes = PushRemuxStreamData( m_pTSRemuxer, pStart, dwLength, &m_nExpectedBytes );
		m_nAlignBytes = dwLength - nUsedBytes;
		ASSERT( m_nExpectedBytes+m_nAlignBytes <= sizeof(m_cAlignBuffer) );
		ASSERT( m_nAlignBytes >= 0 );
		if ( m_nAlignBytes > 0 && m_nAlignBytes + m_nExpectedBytes <= sizeof(m_cAlignBuffer))
		{
			memcpy( m_cAlignBuffer, pStart+nUsedBytes, m_nAlignBytes );

		} else
		{
			//drop data, because of asking too many
			m_nExpectedBytes = 0;
		}

	}

	if ( m_bScanChannel && m_pCScan )
	{
		m_pCScan->ProcessScan( pData, lDataLen );
	}

	if ( !m_pFilter->m_bDisableClockTimeout )
	{
		DWORD dwTickMs =  clock( )*1000/CLOCKS_PER_SEC;
		UpdateClock( m_pTSRemuxer, dwTickMs );
	}

    return hr;
} 
	
void CTSParserInputPin::DrainData( )
{
	FlushRemuxStream( m_pTSRemuxer );
}


void CTSParserInputPin::StartChannelScan( struct TUNE* pTune )
{
	CAutoLock lock(m_pReceiveLock);
	if ( pTune == NULL )
		return;
	if ( m_pCScan == NULL )
		m_pCScan = new CScan;

	//m_pCScan->EnableFileCache( "ScanRawDataDump.ts" );
	m_pCScan->StartChannelScan( pTune );
	m_bScanChannel = TRUE;
}
void CTSParserInputPin::CloseChannelScan( )
{
	CAutoLock lock(m_pReceiveLock);
	if ( m_pCScan == NULL )
		return;

	m_pCScan->CloseChannelScan( );
	delete m_pCScan;
	m_pCScan = NULL;
	m_bScanChannel = FALSE;
}

//
//
STDMETHODIMP CTSParserInputPin::EndOfStream(void)
{
    CAutoLock lock(m_pReceiveLock);
    return CRenderedInputPin::EndOfStream();

} // EndOfStream

STDMETHODIMP CTSParserInputPin::ReceiveConnection( IPin * pConnector, const AM_MEDIA_TYPE *pmt   )
{
	m_pFilter->ReadRegistryCfg(); 

	//m_pFilter->InputPinConnected( pmt );
	return CRenderedInputPin::ReceiveConnection( pConnector, pmt   );
}




////////////////////////////////////////////////////////////////////////////////////////////////////////////
CTSParserOutputPin::CTSParserOutputPin( LPCWSTR pPinName,
									    CTSParserFilter *pFilter,
                                        LPUNKNOWN pUnk,
                                        CCritSec *pLock,
                                        HRESULT *phr) 

: CBaseOutputPin(   NAME("OutputPin"),
                    (CBaseFilter *) pFilter,    // Filter
                    pLock,                      // Locking
                    phr,                        // Return code
                    pPinName )               // Pin name

, m_pFilter(pFilter)
{
	DbgLog((LOG_TRACE, 3, TEXT("Ouput Pin is created") ) );
} 

CTSParserOutputPin::~CTSParserOutputPin()
{
	DbgLog((LOG_TRACE, 3, TEXT("Ouput Pin is released") ) );
	
}

void CTSParserOutputPin::DefaultMediaType(CMediaType *pMediaType)
{
	m_mt = *pMediaType ;
}

HRESULT CTSParserOutputPin::GetMediaType(int iPosition, CMediaType *pMediaType)
{
    if (iPosition < 0) {
    	return E_INVALIDARG;
    }
    if (iPosition > 0) {
    	return VFW_S_NO_MORE_ITEMS;
    }
    *pMediaType = m_mt;
    return S_OK;
}

HRESULT CTSParserOutputPin::CheckMediaType(const CMediaType *pmt)
{
	if ( pmt->majortype == MEDIATYPE_Stream ) 
	{
        return NOERROR;
    }
	
	return E_FAIL;
}

HRESULT CTSParserOutputPin::DecideBufferSize(IMemAllocator *pAlloc, ALLOCATOR_PROPERTIES *pRequest)
{
    HRESULT hr;

    CheckPointer(pAlloc, E_POINTER);
    CheckPointer(pRequest, E_POINTER);

    pRequest->cBuffers = 1;
    pRequest->cbBuffer = m_pFilter->GetBlockSize( this );

    ALLOCATOR_PROPERTIES Actual;
	hr = pAlloc->SetProperties( pRequest, &Actual );
    if (FAILED(hr)) 
    {
        return hr;
    }

    if( Actual.cbBuffer < pRequest->cbBuffer )
    {
        return E_FAIL;
    }
	return NOERROR;
	
}

STDMETHODIMP CTSParserOutputPin::EndOfStream(void)
{
    return CBaseOutputPin::EndOfStream();
}

HRESULT CTSParserOutputPin::SendOut( DWORD nGroupFlag, const BYTE* pData, DWORD dwBytes, DWORD* dwUsedBytes )
{
    IMediaSample *pSample;
    BYTE *pSampleData;
    DWORD cbData;
	*dwUsedBytes = 0;

    HRESULT hr = GetDeliveryBuffer(&pSample,NULL,NULL,0);
    if (FAILED(hr)) 
        return E_FAIL;

    pSample->GetPointer(&pSampleData);
    cbData = pSample->GetSize();
	if ( cbData < dwBytes )
	{
		dwBytes = cbData;
		ASSERT( 0 );
	}

	memcpy( pSampleData, pData, dwBytes );
	pSample->SetActualDataLength( dwBytes );
	*dwUsedBytes = dwBytes;

	hr = Deliver(pSample);
    pSample->Release();

	return S_OK;
}

HRESULT CTSParserOutputPin::GetAllocSize( DWORD* dwSize )
{
	ALLOCATOR_PROPERTIES Props;
	HRESULT hr;
	*dwSize = 0;
	if ( m_pAllocator == NULL )
		return E_FAIL;

	memset( &Props, 0x0, sizeof( Props ) );
	hr = m_pAllocator->GetProperties( &Props );
	if ( hr == S_OK )
		*dwSize = Props.cbBuffer;
	return hr;
    
}

HRESULT CTSParserOutputPin::Connect( IPin * pReceivePin,const AM_MEDIA_TYPE *pmt )
{
	m_pFilter->OutpinConnecting( pReceivePin, this, pmt );
	return CBasePin::Connect( pReceivePin, pmt );
}

HRESULT CTSParserOutputPin::CompleteConnect( IPin *pReceivePin )
{
	HRESULT hr = CBaseOutputPin::CompleteConnect(pReceivePin);
	m_pFilter->OutpinConnected( pReceivePin, this );
	return hr;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////

CTSParserFilter::CTSParserFilter(LPUNKNOWN pUnk,
                           HRESULT *phr) 
: CBaseFilter(NAME("CTSParser"), pUnk, &m_Lock, CLSID_TSParser)

{
    ASSERT(phr);
	m_wOutputFormat = MPEG_PS;
    // Create the single input pin

	m_pInputPin = new CTSParserInputPin(   
		                                this,
                                        GetOwner(),
                                        &m_Lock,
                                        &m_ReceiveLock,
                                        phr);
    if(m_pInputPin == NULL)
    {
        if (phr)
            *phr = E_OUTOFMEMORY;
    }

    m_pOutputPin = new CTSParserOutputPin(   
										L"TSPin",
		                                this,
                                        GetOwner(),
                                        &m_Lock,
                                        phr);
    if( m_pOutputPin == NULL )
    {
        if (phr)
            *phr = E_OUTOFMEMORY;
		delete m_pInputPin;
    }
    
    m_pVideoPin = new CTSParserOutputPin(   
										L"VideoPin",
		                                this,
                                        GetOwner(),
                                        &m_Lock,
                                        phr);
    if( m_pVideoPin == NULL )
    {
        if (phr)
            *phr = E_OUTOFMEMORY;
		delete m_pInputPin;
		delete m_pOutputPin;
    }

    m_pAudioPin = new CTSParserOutputPin(   
										L"AudioPin",
		                                this,
                                        GetOwner(),
                                        &m_Lock,
                                        phr);
    if( m_pAudioPin == NULL )
    {
        if (phr)
            *phr = E_OUTOFMEMORY;
		delete m_pInputPin;
		delete m_pOutputPin;
		delete m_pVideoPin;
    }
    m_pPassThrusPin = new CTSParserOutputPin(   
										L"PassThrusPin",
		                                this,
                                        GetOwner(),
                                        &m_Lock,
                                        phr);
    if( m_pPassThrusPin == NULL )
    {
        if (phr)
            *phr = E_OUTOFMEMORY;
		delete m_pInputPin;
		delete m_pOutputPin;
		delete m_pVideoPin;
		delete m_pAudioPin;
    }
    
    m_pDumpPin= new CTSParserOutputPin(   
										L"DumpPin",
		                                this,
                                        GetOwner(),
                                        &m_Lock,
                                        phr);
    if( m_pDumpPin == NULL )
    {
        if (phr)
            *phr = E_OUTOFMEMORY;
		delete m_pInputPin;
		delete m_pOutputPin;
		delete m_pVideoPin;
		delete m_pAudioPin;
		delete m_pPassThrusPin;
    }

	CMediaType mt;
	//mt.AllocFormatBuffer( TS_PACKET_LENGTH * TS_BUFFER_PACKETS );
	//mt.SetSampleSize( TS_PACKET_LENGTH * TS_BUFFER_PACKETS );
    //mt.SetTemporalCompression(FALSE);

	mt.SetType(&MEDIATYPE_Stream);
	mt.SetSubtype(&MEDIASUBTYPE_MPEG2_TRANSPORT);
	m_pPassThrusPin->DefaultMediaType( &mt );
	m_pDumpPin->DefaultMediaType( &mt );

	mt.SetType(&MEDIATYPE_Video);
	mt.SetSubtype(&MEDIASUBTYPE_MPEG2_VIDEO);
    mt.SetFormatType(&FORMAT_VideoInfo);
	m_pVideoPin->DefaultMediaType( &mt );

	mt.SetType(&MEDIATYPE_Audio);
	mt.SetSubtype(&MEDIASUBTYPE_DOLBY_AC3);
    mt.SetFormatType(&FORMAT_DolbyAC3);
	m_pAudioPin->DefaultMediaType( &mt );

	m_dwDumpedBytes = 0;
	m_dwDumpSize = 0;
	m_dwInputBytes = 0;
	m_dwOutputBytes = 0;
	m_bRebultPMT = FALSE;
	m_bRebuildTSPMTEnabled = FALSE;
	memset( m_szDebugFileSource, 0, sizeof(m_szDebugFileSource) );
	m_hDebugFileSource = NULL;
	m_bPassThroughEnabled = FALSE;
	m_bParserEnabled = TRUE;
	m_bFilterRunning = FALSE;
	m_bDebugFlag = FALSE;
	m_dwSelectProgram = -1;
	m_dwSelectChannel = -1;
	m_dwSelectTSID = 0;
	m_dwPIDDumpDisable = 0;
	m_bDisableClockTimeout = TRUE;
	m_bSubtitleEnable = TRUE;

	m_wFormat = 0;
	m_wSubFormat = 0;

} 


//
// Destructor
//
CTSParserFilter::~CTSParserFilter()
{

	SageLog(( _LOG_TRACE, 3, TEXT("*********** CTSParserFilter was releasing...") )); 
	if ( m_hDebugFileSource != NULL )
		fclose( m_hDebugFileSource );

	try
	{
	    if ( m_pInputPin )
			delete m_pInputPin;
	}
	catch ( ... )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("*********** ERROR: catch an error on deconstruct InputPin")  )); 
	}

	try 
	{
		if ( m_pOutputPin )
			delete m_pOutputPin;
	}
	catch ( ... )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("*********** ERROR: catch an error on deconstruct OuputPin")  )); 
	}

    if ( m_pAudioPin )
		delete m_pAudioPin;

    if ( m_pVideoPin )
		delete m_pVideoPin;

    if ( m_pPassThrusPin )
		delete m_pPassThrusPin;

	try {
		if ( m_pDumpPin )
			delete m_pDumpPin;
	}
	catch ( ... )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("*********** ERROR: catch an error on deconstruct DumpPin")  )); 
	}

	SageLog(( _LOG_TRACE, 3, TEXT("*********** CTSParserFilter was released") )); 

} 

//
// NonDelegatingQueryInterface
//
// Reveals ITSParser2 and ISpecifyPropertyPages
//
STDMETHODIMP CTSParserFilter::NonDelegatingQueryInterface(REFIID riid, void **ppv)
{
    CheckPointer(ppv,E_POINTER);

    if (riid == IID_ITSParser2) {
        return GetInterface((ITSParser2 *) this, ppv);
    } 
	else 
	if (riid == IID_ISpecifyPropertyPages) {
        return GetInterface(static_cast<ISpecifyPropertyPages *>(this), ppv);
	}
    else {
        return CBaseFilter::NonDelegatingQueryInterface(riid, ppv);
    }

} // NonDelegatingQueryInterface

void CTSParserFilter::ReadRegistryCfg()
{
	HKEY rootKey = HKEY_LOCAL_MACHINE;
	DWORD holder;
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(holder);
	if (RegOpenKeyEx(rootKey, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\TSSplitter", 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "DisablePTSFix", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			if ( holder ) 
			{
				REMUXER*  pRemuxer;
				if  ( m_pInputPin->GetParser( &pRemuxer ) == NOERROR )
					DisabeTSPtsFix( pRemuxer );
			}
		}

		if (RegQueryValueEx(myKey, "EPGParserCtrl", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			REMUXER*  pRemuxer;
			if  ( m_pInputPin->GetParser( &pRemuxer ) == NOERROR )
			{
				//EPGParserCtrl( pRemuxer, holder ); //ZQ TODO
			}
		}

		//if (RegQueryValueEx(myKey, "AVInfoCtrl", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		//{
		//	REMUXER*  pRemuxer;
		//	if  ( m_pInputPin->GetParser( &pRemuxer ) == NOERROR )
		//	{
		//		AVInfoCtrl( pRemuxer, holder );
		//	}
		//}

		if (RegQueryValueEx(myKey, "DisableMultiAudio", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			REMUXER*  pRemuxer;
			if  ( m_pInputPin->GetParser( &pRemuxer ) == NOERROR )
			{
				if ( holder )
					DisableMultipleAudio( pRemuxer );
				else
					EnableMultipleAudio( pRemuxer );
			}
		}

		
		if (RegQueryValueEx(myKey, "RebuildPMTEnabled", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bRebuildTSPMTEnabled = (BOOL)holder;
		}

		if (RegQueryValueEx(myKey, "OutputFormat", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			if ( (WORD)holder == 0 )
				holder = MPEG_PS;
			//format validation
			if ( (WORD)holder == MPEG_TS || (WORD)holder == MPEG_TS || (WORD)holder == MPEG_M2TS )
				m_wOutputFormat = (WORD)holder;
		}
		if (RegQueryValueEx(myKey, "DebugProgram", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bDebugFlag = TRUE;
			m_dwSelectProgram = holder;
		}
		if (RegQueryValueEx(myKey, "DebugChannel", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bDebugFlag = TRUE;
			m_dwSelectChannel = holder;
		}
		if (RegQueryValueEx(myKey, "DebugTSID", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bDebugFlag = TRUE;
			m_dwSelectTSID = holder;
		}

		if (RegQueryValueEx(myKey, "DebugDumpSize", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_dwDumpSize = holder;
		}
		if (RegQueryValueEx(myKey, "PassThroughEnabled", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bPassThroughEnabled = holder;
		}

		if (RegQueryValueEx(myKey, "NetworkType", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			SetNetworkType( (WORD)holder, 0 );
		}

		if (RegQueryValueEx(myKey, "DisablePIDDump", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_dwPIDDumpDisable = holder;
		}

		char fname[_MAX_PATH]={0};
		hsize = sizeof(fname);
		if (RegQueryValueEx(myKey, "DebugSource", 0, &readType, (LPBYTE) &fname, &hsize) == ERROR_SUCCESS)
		{
			SetDebugSourceFile( fname );		
		}

		char language[16]={0};
		hsize = sizeof(language)-2;
		if (RegQueryValueEx(myKey, "Language", 0, &readType, (LPBYTE) &language, &hsize) == ERROR_SUCCESS)
		{
			SetAudioLanguage( language );
		}

		if (RegQueryValueEx(myKey, "DisableClockTimeout", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bDisableClockTimeout = holder;
		}
		
		if (RegQueryValueEx(myKey, "EnableSubtitle", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bSubtitleEnable = holder;
		}

		RegCloseKey(myKey);
	}

}

int CTSParserFilter::GetPinCount()
{
    return 6;
} 


//
// GetPin
//
// Return our single input pin - not addrefed
//
CBasePin *CTSParserFilter::GetPin(int n)
{
    // We only support one input pin and it is numbered zero

	switch ( n ) {
	case 0: 
		return m_pInputPin;        
	case 1:
	   return m_pOutputPin;
	case 2:
		return m_pVideoPin;
	case 3:
		return m_pAudioPin;
	case 4:
		return m_pPassThrusPin;
	case 5:
		return m_pDumpPin;
	}

    return NULL;
} 

void CTSParserFilter::InputPinConnected( const AM_MEDIA_TYPE *pmt )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return ;

	if ( m_bDebugFlag )
	{
		TUNE Tune={0};
		if ( m_dwSelectChannel != -1 )
		{
			Tune.channel = (unsigned short)m_dwSelectChannel;
		} 
		if ( m_dwSelectProgram != -1 )
		{
			Tune.u.unkn.data1 = (unsigned short)m_dwSelectProgram;
		}

		if ( m_dwSelectTSID > 0 )
		{
			Tune.tune_string_type = 1;
			Tune.u.unkn.tsid =  (unsigned short)m_dwSelectTSID;
		}

		SetupRemuxStreamTune( pRemuxer, &Tune );
		EnableTSPSI( pRemuxer );
		m_pInputPin->m_Tune = Tune;
		if ( m_szDebugFileSource[0] )
		{	if ( m_hDebugFileSource != NULL )
				fclose( m_hDebugFileSource );
			m_hDebugFileSource = fopen( m_szDebugFileSource, "rb" );  
		}


	}

	if ( !m_bSubtitleEnable )
		DisableSubtitle( pRemuxer );
}

void CTSParserFilter::OutpinConnecting( IPin * pReceivePin, CBasePin* pFilterPin, const AM_MEDIA_TYPE *pmt )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return ;

	if ( pFilterPin == m_pOutputPin )
	{
		CMediaType mt;
		mt.SetTemporalCompression(FALSE);
		mt.SetType(&MEDIATYPE_Stream);
		mt.SetSubtype(&MEDIASUBTYPE_MPEG2_TRANSPORT);
		if ( IS_PS_TYPE( m_wOutputFormat ) )
			mt.SetSubtype(&MEDIASUBTYPE_MPEG2_PROGRAM ); 
		else
			mt.SetSubtype(&MEDIASUBTYPE_MPEG2_TRANSPORT);
		m_pOutputPin->DefaultMediaType( &mt );
	}
}

void CTSParserFilter::OutpinConnected(IPin *pReceivePin, CBasePin* pFilterPin )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return ;

	if ( pFilterPin == m_pOutputPin )
	{
		ChangeRemuxOutputFormat( pRemuxer,  m_wOutputFormat  ); 
		if ( IS_TS_TYPE( m_wOutputFormat ) && m_pOutputPin != NULL )
		{
			int nBlockSize = GetBlockSize( m_pOutputPin );
			SetupRemuxOutputBlockSize( pRemuxer, nBlockSize );
		}
	}
}

HRESULT CTSParserFilter::SendPacket( DWORD nGroupFlag, BYTE* pPacket, DWORD dwSize, DWORD* dwUsedBytes )
{
	HRESULT hr;

	if ( !m_bFilterRunning ) //drop data
		return S_OK;

	hr = m_pOutputPin->SendOut( nGroupFlag, pPacket, dwSize, dwUsedBytes );
	return hr; 
}

HRESULT CTSParserFilter::PassThrough( BYTE* pPacket, DWORD dwSize, DWORD* dwUsedBytes )
{
	HRESULT hr;
	if ( m_pPassThrusPin != NULL )
		hr = m_pPassThrusPin->SendOut( 0, pPacket, dwSize, dwUsedBytes );
	return hr; 
}

HRESULT CTSParserFilter::DumpOut( BYTE* pPacket, DWORD dwSize, DWORD* dwUsedBytes )
{
	HRESULT hr;
	if ( m_pDumpPin!= NULL )
		hr = m_pDumpPin->SendOut( 0, pPacket, dwSize, dwUsedBytes );
	return hr; 

}

HRESULT CTSParserFilter::GetPassThroughSize( DWORD* pdwSize )
{
	HRESULT hr = E_FAIL;
	if ( m_pPassThrusPin != NULL )
		hr = m_pPassThrusPin->GetAllocSize( pdwSize );
	return hr; 
}

HRESULT CTSParserFilter::GetDumpSize( DWORD* pdwSize )
{
	HRESULT hr = E_FAIL;

	if ( m_pDumpPin!= NULL )
		hr = m_pDumpPin->GetAllocSize( pdwSize );
	return hr; 
}

HRESULT CTSParserFilter::GetOutputSize( DWORD* pdwSize )
{
	HRESULT hr = E_FAIL;
	if ( m_pDumpPin!= NULL )
		hr = m_pOutputPin->GetAllocSize( pdwSize );
	return hr; 
}


//
// GetCountOfPrograms
//
STDMETHODIMP CTSParserFilter::CheckProgramStart( BOOL* bStart )
{
	HRESULT hr;
	REMUXER*  pRemuxer;
	if ( bStart == NULL )	return E_INVALIDARG;

	if ( m_pInputPin == NULL )
		return E_FAIL;

	hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	*bStart = CheckParserStart( pRemuxer );

    return NOERROR;
} 

STDMETHODIMP CTSParserFilter::Reset( )
{
	HRESULT hr;
	REMUXER*  pRemuxer;

	if ( m_pInputPin == NULL )
		return E_FAIL;

	hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	ResetRemuxStream( pRemuxer );
	m_dwDumpedBytes = 0;

	m_dwInputBytes = 0;
	m_dwOutputBytes = 0;
	m_bRebultPMT = FALSE;
    return NOERROR;

}


STDMETHODIMP CTSParserFilter::PickupTSChannel( WORD wChannel, WORD wProgram, char* Name, BOOL Reset, BOOL* ret )
{
	HRESULT hr;
	REMUXER*  pRemuxer;

	m_dwDumpedBytes = 0;

	if ( m_szDebugFileSource[0] )
	{
		if ( m_hDebugFileSource != NULL )
			fclose( m_hDebugFileSource );
		m_hDebugFileSource = fopen( m_szDebugFileSource, "rb" );  
	}

    if ( m_pInputPin == NULL  )
		return E_FAIL;

	hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	//drain off packets in parser
	m_pInputPin->DrainData( );

	if ( wChannel != -1 )
	{
		TUNE Tune={0};
		Tune.tune_string_type = 0;
		Tune.channel = wChannel;
		if ( Reset )
			ResetRemuxStreamSlot( pRemuxer, 0  );
		EnableTSPSI( pRemuxer );
		//DisabeTSPtsFix( pRemuxer );
		SetupRemuxStreamTune( pRemuxer, &Tune );
		m_pInputPin->m_Tune = Tune;
	} else
	if ( wProgram != -1 )
	{
		TUNE Tune={0};
		Tune.tune_string_type = 1;
		Tune.u.unkn.data1 = wProgram;
		if ( Reset )
			ResetRemuxStreamSlot( pRemuxer, 0  );
		EnableTSPSI( pRemuxer );
		//DisabeTSPtsFix( pRemuxer );
		SetupRemuxStreamTune( pRemuxer, &Tune );
		m_pInputPin->m_Tune = Tune;
	} 

	return NOERROR;
}

STDMETHODIMP CTSParserFilter::PickupATSCChannel( WORD wMajor, WORD wMinor, WORD  wProgram, char* Name, BOOL Reset, BOOL* ret )
{
	HRESULT hr;
	REMUXER*  pRemuxer;

	m_dwDumpedBytes = 0;

	if ( m_szDebugFileSource[0] )
	{
		if ( m_hDebugFileSource != NULL )
			fclose( m_hDebugFileSource );
		m_hDebugFileSource = fopen( m_szDebugFileSource, "rb" );  
	}

    if ( m_pInputPin == NULL  )
		return E_FAIL;

	hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	//drain off packets in parser
	m_pInputPin->DrainData( );
	{
		TUNE Tune={0};
		Tune.tune_string_type = 0;
		if ( wMajor != 0xffff )   Tune.tune_string_type++;
		if ( wMinor != 0xffff )   Tune.tune_string_type++;
		if ( wProgram != 0xffff ) Tune.tune_string_type++;
		Tune.stream_format = ATSC_STREAM;
		Tune.u.atsc.major_num = wMajor != 0xffff ? wMajor : 0 ;
		Tune.u.atsc.minor_num = wMinor != 0xffff ? wMinor : 0;
		Tune.u.atsc.program_id = wProgram != 0xffff ? wProgram : 0 ;
		if ( Reset )
			ResetRemuxStreamSlot( pRemuxer, 0  );
		EnableTSPSI( pRemuxer );
		//DisabeTSPtsFix( pRemuxer );
		SetupRemuxStreamTune( pRemuxer, &Tune );
		m_pInputPin->m_Tune = Tune;


	} 

	return NOERROR;
}

STDMETHODIMP CTSParserFilter::PickupDVBChannel( WORD wOnid, WORD wTsid, WORD wSid, char* Name, BOOL Reset, BOOL* ret )
{
	HRESULT hr;
	REMUXER*  pRemuxer;

	m_dwDumpedBytes = 0;

	if ( m_szDebugFileSource[0] )
	{
		if ( m_hDebugFileSource != NULL )
			fclose( m_hDebugFileSource );
		m_hDebugFileSource = fopen( m_szDebugFileSource, "rb" );  
	}

    if ( m_pInputPin == NULL  )
		return E_FAIL;

	hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	//drain off packets in parser
	m_pInputPin->DrainData( );
	{
		TUNE Tune={0};
		{
			Tune.tune_string_type = 3;
			Tune.stream_format = DVB_STREAM;
			Tune.u.dvb.onid = wOnid;
			Tune.u.dvb.tsid = wTsid;
			Tune.u.dvb.sid  = wSid;
			if ( Tune.u.dvb.tsid == 0 )
				Tune.tune_string_type = 2;
		}
		if ( Reset )
			ResetRemuxStreamSlot( pRemuxer, 0  );
		EnableTSPSI( pRemuxer );
		//DisabeTSPtsFix( pRemuxer );
		SetupRemuxStreamTune( pRemuxer, &Tune );
		m_pInputPin->m_Tune = Tune;

	} 

	return NOERROR;
}

//
// GetRecordStreamType
//
STDMETHODIMP CTSParserFilter::GetStreamType(WORD wProgramNumber, WORD wIndex, BYTE *pVal)
{
    if(pVal == NULL)
        return E_INVALIDARG;

    CAutoLock cAutoLock(&m_ParserLock);
	HRESULT hr;
	REMUXER*  pRemuxer;
	if ( m_pInputPin == NULL )
		return E_FAIL;
         
	hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	//ZQ TODO if ( StreamType( pRemuxer, wProgramNumber, wIndex, pVal ) )
		return NO_ERROR;
    
    return E_INVALIDARG;
}

STDMETHODIMP CTSParserFilter::SetDumpSize(DWORD dwSizeLimit )
{
	m_dwDumpSize = dwSizeLimit;
	return S_OK;
}

STDMETHODIMP CTSParserFilter::GetStatics(DWORD *dwInputBytes, DWORD *dwOutBytes )
{
	*dwInputBytes = m_dwInputBytes;
	*dwOutBytes = m_dwOutputBytes;
	return S_OK;
}

STDMETHODIMP CTSParserFilter::SetDebugSourceFile(CHAR  *pszFilename )
{
	if ( pszFilename == NULL )		return E_INVALIDARG;
	strncpy( m_szDebugFileSource, pszFilename, sizeof(m_szDebugFileSource) );
	logfile( pszFilename );
	return S_OK;
}

STDMETHODIMP CTSParserFilter::SetAudioLanguage(CHAR  *pszLanguageCode )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	if ( pszLanguageCode == NULL )		return E_INVALIDARG;
	if ( !stricmp( pszLanguageCode, "original" ) )
		pszLanguageCode = "qaa";
	
	SetupEPGDumpLanguage( pRemuxer, LANGUAGE_CODE( pszLanguageCode ) ); //set default language
	SetDefaultAudioLanguage( pRemuxer, LANGUAGE_CODE( pszLanguageCode ) );

	return S_OK;
}

STDMETHODIMP CTSParserFilter::WaitCleanStream( DWORD dwLimit )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;
	EnableWaitTSCleanStream( pRemuxer );

	return S_OK;
}


STDMETHODIMP CTSParserFilter::SetOutputFormat(WORD   wOutputFormat )
{
	//take effect after select program
	if ( wOutputFormat == 0 )
		m_wOutputFormat = MPEG_TS;
	else
	if ( wOutputFormat == 1 )
		m_wOutputFormat = MPEG_PS;
	else
	{
		ASSERT( 0 );
	}

	return NOERROR;

}

STDMETHODIMP CTSParserFilter::SetNetworkType(WORD wFormat, WORD wSubFormat )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	m_wFormat    = wFormat;
	m_wSubFormat = wSubFormat;
	SetTSStreamType( pRemuxer, wFormat, wSubFormat );	
	return NOERROR;
}

STDMETHODIMP CTSParserFilter::GetNetworkType(WORD* pFormat, WORD* pSubFormat )
{
	REMUXER*  pRemuxer;
	if ( pFormat == NULL || pSubFormat == NULL )		return E_INVALIDARG;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	int nFormat, nSubFormat;
	GetTSStreamType( pRemuxer, &nFormat, &nSubFormat );
	*pFormat = nFormat;
	*pSubFormat = nSubFormat;
	
	return NOERROR;
}




STDMETHODIMP CTSParserFilter::SetEPGDump( void* pfn, void* Context  )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	SetupEPGDump( pRemuxer, (DUMP)pfn, Context );
	return NOERROR;

}

STDMETHODIMP CTSParserFilter::SetPIDsDump( void* pfn, void* Context  )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	//ZQ TODO if ( !m_dwPIDDumpDisable )
	//ZQ TODO 	::SetPidTblDump( pRemuxer, (EPG_DUMP)pfn, Context );

	return NOERROR;

}

STDMETHODIMP CTSParserFilter::LockUpParser(  )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	LockUpTSParser( pRemuxer );
	return NOERROR;
}

STDMETHODIMP CTSParserFilter::UnLockUpParser(  )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	UnLockUpTSParser( pRemuxer );
	return NOERROR;
}


STDMETHODIMP CTSParserFilter::SetAVInfDump( void* pfn, void* Context  )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	SetupAVInfDump( pRemuxer, (DUMP)pfn, Context );
	//ZQ TODO ::SetAVInfDump( pRemuxer, (EPG_DUMP)pfn, Context );
	return NOERROR;

}

STDMETHODIMP CTSParserFilter::SetProgramDataDump( void* pfn, void* Context  )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	SetupProgramDataDump( pRemuxer, (DUMP)pfn, Context );
	return NOERROR;

}

STDMETHODIMP CTSParserFilter::StopParser()
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	m_bParserEnabled = FALSE;

	//drain off packets in parser
	m_pInputPin->DrainData( ); //there is risk thread race

	m_bFilterRunning = FALSE;

	return NOERROR;
}

STDMETHODIMP CTSParserFilter::StartParser()
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;
	
	m_bParserEnabled = TRUE;
	m_bFilterRunning = TRUE;
	return NOERROR;

}


STDMETHODIMP CTSParserFilter::PassThrusEnable( BOOL Enable )
{
	m_bPassThroughEnabled = Enable;
	return NOERROR;
}

STDMETHODIMP CTSParserFilter::SelectProgramWithoutReset(WORD  wProgram )
{
	HRESULT hr;
	REMUXER*  pRemuxer;

	m_dwDumpedBytes = 0;

	if ( m_szDebugFileSource[0] )
	{
		if ( m_hDebugFileSource != NULL )
			fclose( m_hDebugFileSource );
		m_hDebugFileSource = fopen( m_szDebugFileSource, "rb" );  
	}

    if ( m_pInputPin == NULL )
		return E_FAIL;

	hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	//drain off packets in parser
	m_pInputPin->DrainData( );

	if ( wProgram != -1 )
	{
		TUNE Tune={0};
		if ( m_dwSelectTSID > 0 )
			Tune.u.unkn.tsid =  (unsigned short)m_dwSelectTSID; //for debug
		Tune.tune_string_type = 1;
		Tune.u.unkn.data1 = wProgram;
		SetupRemuxStreamTune( pRemuxer, &Tune );
		m_pInputPin->m_Tune = Tune;


	} 


	return NOERROR;

}

STDMETHODIMP CTSParserFilter::SelectChannelWithoutReset( WORD  nChannel )
{
	HRESULT hr;
	REMUXER*  pRemuxer;

	m_dwDumpedBytes = 0;

	if ( m_szDebugFileSource[0] )
	{
		if ( m_hDebugFileSource != NULL )
			fclose( m_hDebugFileSource );
		m_hDebugFileSource = fopen( m_szDebugFileSource, "rb" );  
	}

    if ( m_pInputPin == NULL  )
		return E_FAIL;

	hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	//drain off packets in parser
	m_pInputPin->DrainData( );

	if ( nChannel != -1 )
	{
		TUNE Tune={0};
		Tune.channel = (unsigned short)nChannel;
		SetupRemuxStreamTune( pRemuxer, &Tune );
		m_pInputPin->m_Tune = Tune;


	} 

	return NOERROR;
}

STDMETHODIMP CTSParserFilter::GetDebugInfo( WORD cmd, char* Buf, DWORD BufSize )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	//ZQ TODO ::GetDebugInfo( pRemuxer, cmd, Buf, BufSize );
	return NOERROR;

}

STDMETHODIMP CTSParserFilter::LockATSCChannel( WORD wMajor, WORD wMinor )
{
	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;
	
	{
		TUNE Tune={0};
		Tune.tune_string_type = 2;
		Tune.stream_format = ATSC_STREAM;
		Tune.u.atsc.major_num = wMajor;
		Tune.u.atsc.minor_num = wMinor;
		SetupRemuxStreamTune( pRemuxer, &Tune );
		EnableTSPSI( pRemuxer );
		//DisabeTSPtsFix( pRemuxer );
		m_pInputPin->m_Tune = Tune;

	} 
	return NOERROR;
}

STDMETHODIMP CTSParserFilter::LockTSChannel( void* pTune, BOOL* pRet )
{
	if ( pTune == NULL )
		return E_INVALIDARG;

	REMUXER*  pRemuxer;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;
	ResetRemuxStream( pRemuxer );
	m_dwDumpedBytes = 0;
	EnableTSPSI( pRemuxer );
	//DisabeTSPtsFix( pRemuxer );
	SetupRemuxStreamTune( pRemuxer, (TUNE*)pTune );
	m_pInputPin->m_Tune = *(TUNE*)pTune;


	return NOERROR;
}

STDMETHODIMP CTSParserFilter::PacketInputNum(DWORD *dwTSPackets )
{
	REMUXER*  pRemuxer;
	if ( dwTSPackets == NULL )		return E_INVALIDARG;
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	//ZQ TODO *dwTSPackets = ::PacketInputNum( pRemuxer );
	return NOERROR;
}

STDMETHODIMP CTSParserFilter::GetNetworkList( char* Buf, DWORD MaxBytes, DWORD* Bytes )
{
	REMUXER*  pRemuxer;
	
	HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
	if ( hr != NOERROR  )
		return hr;

	//ZQ TODO 
	//if ( Bytes != NULL )
	//	*Bytes = (WORD)::GetSINetworkList( pRemuxer->si_parser, Buf, MaxBytes );
	//else
	//	GetSINetworkList( pRemuxer->si_parser, Buf, MaxBytes );

	return NOERROR;
}

STDMETHODIMP CTSParserFilter::GetPages(CAUUID *pPages)
{
    if (pPages == NULL)
        return E_POINTER;

    pPages->cElems = 1;
    pPages->pElems = (GUID *) CoTaskMemAlloc(sizeof(GUID));
    if(pPages->pElems == NULL)
    {
        return E_OUTOFMEMORY;
    }

    *(pPages->pElems) = CLSID_FilterPropertyPage;
    return NOERROR;

}


DWORD CTSParserFilter::GetBlockSize( CTSParserOutputPin *pin )
{
	if ( pin == m_pDumpPin || pin == m_pPassThrusPin )
	{
		DWORD Size;
		HRESULT hr = m_pInputPin->GetAllocSize( &Size );
		if ( hr != S_OK )
			return TS_PACKET_LENGTH * TS_BUFFER_PACKETS;
		else
			return Size;

	} else
	if ( pin == m_pVideoPin )
	{
		return 2048*4;
	} else
	if ( pin == m_pAudioPin)
	{
		return 2048*4;
	} else
	if ( pin == m_pOutputPin )
	{
		REMUXER*  pRemuxer;
		HRESULT hr = m_pInputPin->GetParser( &pRemuxer );
		return DemuxBlockSize( pRemuxer );
	}
	return TS_PACKET_LENGTH * TS_BUFFER_PACKETS;;
}


STDMETHODIMP CTSParserFilter::ScanChannel( void* pScanTune, BOOL* pRet )
{
	if ( pScanTune == NULL )
		return E_INVALIDARG;

	m_pInputPin->StartChannelScan( (TUNE*)pScanTune );
	*pRet = TRUE;

	return NOERROR;
}

STDMETHODIMP CTSParserFilter::ReleaseScanChannel(  )
{
	m_pInputPin->CloseChannelScan( );
	return NOERROR;
}

STDMETHODIMP CTSParserFilter::GetScanChannelList( void **pChannelList, BOOL* pRet )
{
	CScan*  pCScan;
	HRESULT hr = m_pInputPin->GetChannelScan( &pCScan );
	if ( hr != NOERROR  )
		return hr;
	*pChannelList = pCScan->GetChannelList( );
	*pRet = TRUE;
	return NOERROR;
}

STDMETHODIMP CTSParserFilter::GetScanTuneList( void **pTuneList, BOOL* pRet )
{
	CScan*  pCScan;
	HRESULT hr = m_pInputPin->GetChannelScan( &pCScan );
	if ( hr != NOERROR  )
		return hr;
	*pTuneList = pCScan->GetTuneList( );
	*pRet = TRUE;
	return NOERROR;
}


STDMETHODIMP CTSParserFilter::ScanChannelState( int* pScanState, int *pFoundChannelNum, int nClock  )
{
	CScan*  pCScan;
	HRESULT hr = m_pInputPin->GetChannelScan( &pCScan );
	if ( hr != NOERROR  )
		return hr;

	if ( nClock > 0 )
		pCScan->ScanChannelTimeClock( nClock*100 );
	*pScanState =  pCScan->ScanChannelState( );
	*pFoundChannelNum = pCScan->ScanChannelNum( );

	return NOERROR;
}




/////////////////////////////////////////////////////////////
