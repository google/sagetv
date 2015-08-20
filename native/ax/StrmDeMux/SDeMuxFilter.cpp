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

//#define _CRT_SECURE_NO_DEPRECATE 1
//#define _CRT_NONSTDC_NO_DEPRECATE 1
//#define _CRT_SECURE_NO_WARNINGS 1
#pragma warning(disable : 4995)
#pragma warning(disable : 234)
#define _USE_32BIT_TIME_T

#include <streams.h>
#include <pullpin.h>
#include <limits.h>
#include <initguid.h>
#include <ks.h>
#include <time.h>
#include <dvdmedia.h>

#include "CPushReader.h"
#include "SDeMuxInPin.h"
#include "SDeMuxOutPin.h"
#include "SDeMuxFilter.h"
#include "FilterProperties.h"
#include "ISDeMux.h"
#include "PostMsg.h"
#include "../../../third_party/Microsoft/FileSource/strconv.h"

//Asyncfile: E436EBB5-524F-11CE-9F53-0020AF0BA770

#undef DUMP_ENABLE
//#define DUMP_ENABLE  1

#define WAIT_EVENT_TIMEOUT 100000
extern "C" {
static void _flog_local_check();
void setDebugTrace( char* pDebugLogName, int logCtrl ); //pushReader log
}
//
DEFINE_GUID(CLSID_SDeMux,
    0xabd65dff, 0x5702, 0x4f21, 0x86, 0x3b, 0x59, 0x1b, 0x05, 0x7d, 0x22, 0x68 );


DEFINE_GUID(CLSID_SageFFPropertyPage, 
	0xabd65eff, 0x3131, 0x44a7, 0x88, 0x4e, 0xbd, 0x1a, 0x57, 0xab, 0x78, 0x63);
	
static const GUID  CLSID_MpegVC1 =
{ 0x629B40AD, 0xAD74, 0x4EF4, {0xA9, 0x85, 0xF0, 0xC8, 0xD9, 0x2E, 0x5E, 0xCA } };



/*  Registration setup stuff */
//  Setup data

const AMOVIESETUP_MEDIATYPE sudMpgInputType =
{
    &MEDIATYPE_Stream, &MEDIASUBTYPE_MPEG2_PROGRAM
};
const AMOVIESETUP_MEDIATYPE sudMpgAudioOutputType =
{
    &MEDIATYPE_Audio, &MEDIASUBTYPE_MPEG2_AUDIO
};
const AMOVIESETUP_MEDIATYPE sudMpgVideoOutputType =
{
    &MEDIATYPE_Video, &MEDIASUBTYPE_MPEG2_VIDEO
};

const AMOVIESETUP_PIN sudMpgPins[3] =
{
    { L"Input",
      FALSE,                               // bRendered
      FALSE,                               // bOutput
      FALSE,                               // bZero
      FALSE,                               // bMany
      &CLSID_NULL,                         // clsConnectsToFilter
      NULL,                                // ConnectsToPin
      1,            // Number of media types
      &sudMpgInputType
    },
    { L"Audio Output",
      FALSE,                               // bRendered
      TRUE,                                // bOutput
      TRUE,                                // bZero
      FALSE,                               // bMany
      &CLSID_NULL,                         // clsConnectsToFilter
      NULL,                                // ConnectsToPin
      1,      // Number of media types
      &sudMpgAudioOutputType
    },
    { L"Video Output",
      FALSE,                               // bRendered
      TRUE,                                // bOutput
      TRUE,                                // bZero
      FALSE,                               // bMany
      &CLSID_NULL,                         // clsConnectsToFilter
      NULL,                                // ConnectsToPin
      1,      // Number of media types
      &sudMpgVideoOutputType
    }
};

const AMOVIESETUP_FILTER sudMpgsplit =
{
    &CLSID_SDeMux,
    L"SageTV Stream Demuxer",
    MERIT_DO_NOT_USE,                                     // Don't use us for real!
    3,                   // 3 pins
    sudMpgPins
};

CFactoryTemplate g_Templates[] = {
	{ L"SageTV Stream Demuxer", &CLSID_SDeMux, CSDeMuxFilter::CreateInstance, NULL, &sudMpgsplit },
	{ L"Filter Property Page", &CLSID_SageFFPropertyPage , CFilterProperties::CreateInstance, NULL, NULL }
};
int g_cTemplates = sizeof(g_Templates) / sizeof(g_Templates[0]);

STDAPI DllRegisterServer()
{
    return AMovieDllRegisterServer2( TRUE );
} 

STDAPI DllUnregisterServer()
{
    return AMovieDllRegisterServer2( FALSE );
} 

// DllEntryPoint
//
extern "C" BOOL WINAPI DllEntryPoint(HINSTANCE, ULONG, LPVOID);

BOOL APIENTRY DllMain(HANDLE hModule, 
                      DWORD  dwReason, 
                      LPVOID lpReserved)
{
	return DllEntryPoint((HINSTANCE)(hModule), dwReason, lpReserved);
}



CUnknown * WINAPI CSDeMuxFilter::CreateInstance(LPUNKNOWN pUnk, HRESULT *phr)
{
	_flog_local_check();

    CUnknown *pUnkRet = new CSDeMuxFilter(pUnk, phr);
    return pUnkRet;
}

HRESULT CSDeMuxFilter::NonDelegatingQueryInterface(REFIID riid, void **ppv) 
{
	if ( riid == IID_ISDeMux )
	{
        CheckPointer(ppv, E_POINTER);
        return GetInterface(static_cast<ISDeMux *>(this), ppv);
    }
	else 
	if (riid == IID_IMediaSeeking)
	{
		return GetInterface(static_cast<IMediaSeeking *>(this), ppv);
	}
	else if (riid == IID_ISpecifyPropertyPages) {
        return GetInterface(static_cast<ISpecifyPropertyPages *>(this), ppv);
	}
	else 
	{
        return CBaseFilter::NonDelegatingQueryInterface(riid, ppv);
    }
}

///////////////////////////////////////////////////////////////////////////////////////
//class CSDeMuxFilter
///////////////////////////////////////////////////////////////////////////////////////
CSDeMuxFilter::CSDeMuxFilter( LPUNKNOWN pUnk, HRESULT *phr) :
CBaseFilter( TEXT("SDeMux"),
			pUnk, 
			&m_csFilter,
			CLSID_SDeMux, 
			phr ),
    m_pInputPin( NULL ),
	m_uSourceFormat( 0 ),
	m_dPlayRate(1.0),
	m_dwBitsRate(1),
	m_bPlayRewind ( FALSE ),
	m_bRemuxOptimization( FALSE ),
	m_pfnAFDDump( NULL ),
	m_pfnCCDump( NULL ),
	m_pfnEventDump( NULL ),
	m_EventDumpContext( NULL ),
	m_AFDDumpContext( NULL ),
	m_CCDumpContext( NULL ),
	m_dwDefaultChannel(-1),
	m_bSeekPos( FALSE ),
	m_rtStartSync(0),
	m_rtAudioDelay(0),
	m_bSyncPTS(TRUE),
	m_bSyncOutpinPTS(TRUE),
	//m_rtDuration( _I64_MAX / 2 ),
	m_pAllocator( NULL ),
	m_dwBlockSize( BLOCK_SIZE ),
	m_dwBlockNum( BLOCK_NUM ),
	m_dwReaderBlockSize( PUSH_READER_BLOCK_SIZE ),
	m_bAudioClockFix( TRUE ),
	m_lMergeList( NAME( "VideoMergeList" ) ),
	m_lOutputPin(NAME( "StreamList" )),
	m_bPostSubMsg( TRUE )
{
	DbgLog((LOG_TRACE, 3, TEXT("SDeMux Filter is created Ver. 0.0.01.")) );

	m_dwSeekingCaps = AM_SEEKING_CanSeekForwards
        | AM_SEEKING_CanSeekBackwards
        | AM_SEEKING_CanSeekAbsolute
        | AM_SEEKING_CanGetStopPos
		| AM_SEEKING_CanGetCurrentPos
        | AM_SEEKING_CanGetDuration;

	m_SeekPos.start = 0;
	m_SeekPos.stop = 0;
	m_pInputPin = new CSDeMuxInPin( this, phr );

	//m_pPushReader = NULL;
	m_pPushReader = new CPushReader( this ); 
	//m_pPushReader->SetRemuxOptimization(m_bRemuxOptimization);
	//m_pPushReader->SetAFDDump( (MSG_DUMP)m_pfnAFDDump, m_AFDDumpContext );
	//m_pPushReader->SetCCDump( (CC_DUMP)m_pfnCCDump, m_CCDumpContext );
	m_bStaticFile = TRUE;

	m_dwBitsRate = 567113;
	m_rtLastSeekPos = 0;
	m_dwLastSeekActionTime = 0;
	m_rtSeekStartPos = 0;
	m_rtSeekStartPTS = 0;

	LoadCfgFromRegistry();

	m_dSeekRatio = 0;
	m_dwPTSCount = 0;

	m_rtLastPTS = 0;
	m_rtPreStartMediaTimeOffset = 0;
	m_rtStartMediaTimeOffset = 0;
	m_rtSeekMediaTimeOffset = 0;
	m_dwFileSwitchFlag = 0;
	m_bEndOfData = FALSE;
	m_bEndOfStream = FALSE;

	m_pVideoAllocator = NULL;
	m_pAudioAllocator = NULL;
	m_pSubPictureAllocator = NULL;
	CreateOutputAllocator( );

	m_dwTrashSize = BLOCK_SIZE;
	m_dwTrashBuffer = new BYTE[m_dwTrashSize];

#ifdef DUMP_ENABLE
	open_dump( );
#endif

}

CSDeMuxFilter::~CSDeMuxFilter()
{
	DbgLog((LOG_TRACE, 3, TEXT("CSDeMuxFilter is waiting thread terminated.")) );

	if ( m_dwTrashBuffer )
		delete m_dwTrashBuffer;

	if ( m_pInputPin )
		delete m_pInputPin;

	if ( m_pPushReader )
		delete m_pPushReader;


   while ( 1 )
   {
        CSDeMuxOutPin *pOutputPin = m_lOutputPin.RemoveHead();
        if ( pOutputPin == NULL ) 
            break;

        //  Disconnect if necessary
        IPin *pPeer = pOutputPin->GetConnected();
        if (pPeer != NULL) {
            pPeer->Disconnect();
            pOutputPin->Disconnect();
			
        }
        pOutputPin->Release();
    }

	while ( m_lMergeList.GetCount() )
	{
		_BLOCK* pBlock;
		pBlock = m_lMergeList.GetHead();
		pBlock->pSample->Release();
		delete pBlock;
		m_lMergeList.RemoveHead();
	}

   	if ( m_pAllocator  )
		m_pAllocator->Release();

	if ( m_pVideoBukAllocator )	
		m_pVideoBukAllocator->Release();

	if ( m_pVideoAllocator )
		m_pVideoAllocator->Release();
	if ( m_pAudioAllocator )
		m_pAudioAllocator->Release();
	if ( m_pSubPictureAllocator )
		m_pSubPictureAllocator->Release();
#ifdef DUMP_ENABLE
	close_dump( );
#endif
   
   DbgLog((LOG_TRACE, 3, TEXT("CSDeMuxFilter Destroyed ZQ.\r\n")) );
}


STDMETHODIMP CSDeMuxFilter::SetEventDump( void* pfn, void* context )
{
	m_pfnEventDump = pfn;
	m_EventDumpContext = context;
	return S_OK;
}
	
STDMETHODIMP CSDeMuxFilter::Duration( LONGLONG* prtDuration )
{
	GetDuration( prtDuration );
	return S_OK;
}

HRESULT CSDeMuxFilter::GetPlayRate( long *pPlayeRate )
{
	*pPlayeRate = (int)m_dPlayRate;
	if ( m_bPlayRewind ) *pPlayeRate = -(*pPlayeRate);
	return S_OK;
}

HRESULT CSDeMuxFilter::SetPlayRate( long iPlayRate )
{
	if ( iPlayRate == 0 ) iPlayRate = 1;

	m_bPlayRewind =  ( iPlayRate < 0 );
	if ( iPlayRate < 0 )
		m_dPlayRate = -iPlayRate;
	else
	    m_dPlayRate = iPlayRate;
	return S_OK;
}

	

STDMETHODIMP CSDeMuxFilter::GetStatistic( void** pData )
{
	//update statistic 
	char buf[512]={0};
	m_Statistic.message = NULL;
	m_Statistic.bitrates   = m_pPushReader->m_dwBitRate;
	m_Statistic.test_data1 = (long)m_pPushReader->GetLastTimestamp();
	m_Statistic.test_data2 = (long)m_dSeekRatio; 
	*pData = &m_Statistic;
	if (  m_pVideoAllocator != NULL ) 
	{
		
		char tmp[128];
		int free_num = m_pVideoAllocator->FreeBufferNum( );
		int wait_num = m_pVideoAllocator->traceWait( );
		int release_num = m_pVideoAllocator->traceRelease();
		int input_num = 0;
		if ( m_pAllocator != NULL )
			input_num = m_pAllocator->FreeBufferNum( );
		sprintf_s( tmp, sizeof(tmp), "in-free:%d;free:%d wait:%d rel:%d", input_num, free_num, wait_num, release_num  );
		if ( strlen( buf )+ strlen( tmp ) < sizeof(buf) )
			strcat_s( buf, sizeof(buf), tmp );
		

		m_Statistic.overflow = 0;
	}
	else 
	{
		char tmp[64];
		int input_num = 0;
		if ( m_pAllocator != NULL )
			input_num = m_pAllocator->FreeBufferNum( );
		sprintf_s( tmp, sizeof(tmp),  "in-free:%d;ovflw:%03d", input_num, m_Statistic.overflow  );
		if ( strlen( buf )+ strlen( tmp ) < sizeof(buf) )
			strcat_s( buf, sizeof(buf), tmp );
		
	}
	m_Statistic.message = buf;
	return S_OK;
}


STDMETHODIMP CSDeMuxFilter::SetAlloProp( long dwBlockSize, long dwBufferSize )
{
	if ( dwBlockSize <= 0 || dwBufferSize < 0 || dwBufferSize < dwBlockSize )
		return E_INVALIDARG ;

	return S_OK;
}

STDMETHODIMP CSDeMuxFilter::GetAlloProp( long* pdwBlockSize, long* pdwBufferSize )
{
	*pdwBlockSize = 0;
	*pdwBufferSize = 0;
	return S_OK;
}

/*  -- Return number of pins */
int CSDeMuxFilter::GetPinCount()
{
	CAutoLock lock(pStateLock());
	return  1 + m_lOutputPin.GetCount(); 
}

STDMETHODIMP CSDeMuxFilter::GetPages(CAUUID *pPages)
{
    if (pPages == NULL)
        return E_POINTER;

    pPages->cElems = 1;
    pPages->pElems = (GUID *) CoTaskMemAlloc(sizeof(GUID));
    if(pPages->pElems == NULL)
    {
        return E_OUTOFMEMORY;
    }

    *(pPages->pElems) = CLSID_SageFFPropertyPage;
    return NOERROR;

}



/////////////////////////////////////////////////////////////////
//IMediaSeeking methods
HRESULT CSDeMuxFilter::GetCapabilities( DWORD * pCapabilities ) 
{
    CheckPointer(pCapabilities, E_POINTER);
    *pCapabilities = m_dwSeekingCaps;
    return S_OK;
}

HRESULT CSDeMuxFilter::CheckCapabilities( DWORD * pCapabilities ) 
{
    CheckPointer(pCapabilities, E_POINTER);
    return (~m_dwSeekingCaps & *pCapabilities) ? S_FALSE : S_OK;
}

HRESULT CSDeMuxFilter::ConvertTimeFormat( LONGLONG * pTarget, 
				const GUID * pTargetFormat,
               LONGLONG    Source, const GUID * pSourceFormat ) 
{
    CheckPointer(pTarget, E_POINTER);
    // since we only support TIME_FORMAT_MEDIA_TIME, we don't really
    // offer any conversions.
    if  (  pTargetFormat == 0 || *pTargetFormat == TIME_FORMAT_MEDIA_TIME) {
           if(pSourceFormat == 0 || *pSourceFormat == TIME_FORMAT_MEDIA_TIME) 
		{
            *pTarget = Source;
             return S_OK;
        }
    }

    return E_INVALIDARG;
}

HRESULT CSDeMuxFilter::IsFormatSupported(const GUID * pFormat) 
{
    CheckPointer(pFormat, E_POINTER);
    // only seeking in time (REFERENCE_TIME units) is supported
    return *pFormat == TIME_FORMAT_MEDIA_TIME ? S_OK : S_FALSE;
}

HRESULT CSDeMuxFilter::QueryPreferredFormat(GUID *pFormat) 
{
    CheckPointer(pFormat, E_POINTER);
    *pFormat = TIME_FORMAT_MEDIA_TIME;
    return S_OK;
}

HRESULT CSDeMuxFilter::SetTimeFormat(const GUID * pFormat) 
{
    CheckPointer(pFormat, E_POINTER);
    // nothing to set; just check that it's TIME_FORMAT_TIME
    return *pFormat == TIME_FORMAT_MEDIA_TIME ? S_OK : E_INVALIDARG;
}

HRESULT CSDeMuxFilter::IsUsingTimeFormat(const GUID * pFormat) 
{
    CheckPointer(pFormat, E_POINTER);
    return *pFormat == TIME_FORMAT_MEDIA_TIME ? S_OK : S_FALSE;
}

HRESULT CSDeMuxFilter::GetTimeFormat(GUID *pFormat) {
    CheckPointer(pFormat, E_POINTER);
    *pFormat = TIME_FORMAT_MEDIA_TIME;
    return S_OK;
}
HRESULT CSDeMuxFilter::GetRate( double * pdRate) 
{
    CheckPointer(pdRate, E_POINTER);
    //CAutoLock lock(m_pLock);
    *pdRate = m_dPlayRate;
	//DbgLog((LOG_TRACE, 2, TEXT("****Get Rate Seeking %f"),  m_dPlayRate  ));
    return S_OK;
}

HRESULT CSDeMuxFilter::GetPreroll(LONGLONG *pPreroll) 
{
    CheckPointer(pPreroll, E_POINTER);
    *pPreroll = 0;
	DbgLog((LOG_TRACE, 5, TEXT("****GetPreroll" ) ));
    return S_OK;
}

HRESULT CSDeMuxFilter::GetAvailable( LONGLONG * pEarliest, LONGLONG * pLatest )
{
    if ( pEarliest ) 
	{
        *pEarliest = 0;
    }
    if ( pLatest ) 
	{
        CAutoLock lock(m_pLock);
		ULONGLONG length=0, Available=0;
		m_pInputPin->Length( &length, &Available );
		//take count of bit_rate
        *pLatest = Available * UNITS /m_dwBitsRate;
    }
	DbgLog((LOG_TRACE, 3, TEXT("****GetAvailable" ) ));
    return S_OK;
}

HRESULT CSDeMuxFilter::GetDuration( LONGLONG *pDuration ) 
{
	CheckPointer(pDuration, E_POINTER);
	*pDuration = m_pPushReader->GetDuration();  
	DbgLog((LOG_TRACE, 5, TEXT("****Get Duration=%s"),(LPCTSTR)Disp( (CRefTime)*pDuration)	));

    return S_OK;
}

HRESULT CSDeMuxFilter::GetPositions( LONGLONG * pCurrent, LONGLONG * pStop ) 
{
	//if we need more precious position, add position of parser ZQ
    if (pCurrent) 
	{
		*pCurrent = m_pPushReader->GetLastTimestamp( );
	}

    if(pStop) 
	{
		*pStop = m_pPushReader->GetDuration();
	}

	DbgLog((LOG_TRACE, 3, TEXT("****Get Pos=%s %s"),(LPCTSTR)Disp( (CRefTime)*pCurrent), (LPCTSTR)Disp( (CRefTime)*pStop )	));
    return S_OK;;
}


HRESULT CSDeMuxFilter::GetStopPosition(LONGLONG *pStop) 
{
    CheckPointer(pStop, E_POINTER);
    CAutoLock lock(m_pLock);
	*pStop = m_pPushReader->GetDuration();  
	if ( *pStop == 0 ) *pStop = _I64_MAX / 2;

	//DbgLog((LOG_TRACE, 3, TEXT("****Get stop Pos=%s "), (LPCTSTR)Disp( (CRefTime)*pStop ) ));	
    return S_OK;
}

HRESULT CSDeMuxFilter::GetCurrentPosition(LONGLONG *pCurrent) 
{
	CheckPointer(pCurrent, E_POINTER);
	*pCurrent = m_pPushReader->GetLastTimestamp( );
	DbgLog((LOG_TRACE, 3, TEXT("****Get Cur=%s %s"), (LPCTSTR)Disp( (CRefTime)*pCurrent )	));	
	return S_OK;
}

HRESULT CSDeMuxFilter::SetRate( double dRate) 
{
	DbgLog((LOG_TRACE, 3, TEXT("***Set Rate Seeking")));
    CAutoLock lock(m_pLock);
    m_dPlayRate = dRate;
	if ( m_dPlayRate == 0 )	m_dPlayRate = 1; //don't allow zero
    return S_OK;//ChangeRate();
}

STDMETHODIMP CSDeMuxFilter::SetClockDelta(REFERENCE_TIME rtDelta)
{
	DbgLog((LOG_TRACE, 2, TEXT("***Audio Delay set to %s"), (LPCSTR)Disp(rtDelta, DISP_DEC)));
	m_rtAudioDelay = rtDelta;
	return S_OK;
}
//////////////////////////////////////////////////////////
/*  -- Return a given pin -- */
CBasePin *CSDeMuxFilter::GetPin(int iPin)
{
	CAutoLock lock(pStateLock());
	if ( iPin == 0 )
        return m_pInputPin;

	if ( iPin < 0 || iPin > m_lOutputPin.GetCount() )
		return NULL;
	
	CBasePin* pin = NULL;
	iPin--;
	POSITION pos = m_lOutputPin.GetHeadPosition();
    while ( iPin >= 0) 
	{
		pin = m_lOutputPin.GetNext(pos);
		iPin--;
	}
    
	return pin;
}

CSDeMuxOutPin* CSDeMuxFilter::CreateOutputPin( BYTE uContentType, BYTE uStreamId, BYTE uSubId, BYTE uTrackIndex, 
											       CMediaType *pMT, LPCWSTR pName )
{
	HRESULT hr;
	CSDeMuxOutPin* pOutputPin = new CSDeMuxOutPin( this, &hr, pName, pMT, uContentType, uStreamId, uSubId, uTrackIndex );
	//DbgLog((LOG_TRACE, 3, TEXT("Stream OutputPin <%s> is created: stream_id:0x%x, sub_id:0x%x."), LPCTSTR(CWtoT(pName)),
	//	                         pOutputPin->m_uStreamId, pOutputPin->m_uSubId ));
	return pOutputPin;
}

HRESULT  CSDeMuxFilter::RegisterOutputPin( CSDeMuxOutPin* pOutputPin )
{
	CSDeMuxOutPin *p = FindOutputPin( pOutputPin->m_uStreamId, pOutputPin->m_uSubId );
	if ( p != NULL )
	{
		DbgLog((LOG_TRACE, 3, TEXT("ERROR: Duplicated Stream in RegisterStream.")));
		return E_UNEXPECTED;
	}
	POSITION pos = m_lOutputPin.AddTail( pOutputPin );
	DbgLog((LOG_TRACE, 3, TEXT("Added a Pin 0x%x "), pOutputPin ));

	return S_OK;
}

HRESULT CSDeMuxFilter::UnRegisterOutputPin( CSDeMuxOutPin* pOutputPin )
{
	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) {
        if ( pOutputPin == m_lOutputPin.GetNext(pos) )
		{
			DbgLog((LOG_TRACE, 3, TEXT("Removed a Pin 0x%x "), pOutputPin ));
			m_lOutputPin.Remove( pos );
            return S_OK;
        }
    }
    return VFW_S_NO_MORE_ITEMS;
}

CSDeMuxOutPin* CSDeMuxFilter::FindOutputPin( BYTE uStreamId, BYTE uSubId )
{
	POSITION pos = m_lOutputPin.GetHeadPosition();
	CSDeMuxOutPin *pOutputPin;
    while (pos) 
	{
        pOutputPin = m_lOutputPin.GetNext(pos);
		if ( pOutputPin->m_uStreamId == uStreamId && pOutputPin->m_uSubId == uSubId )
		{
			return pOutputPin;
        }
    }
	return NULL;
}

CSDeMuxOutPin* CSDeMuxFilter::FindOutputPin( BYTE uTrackIndex )
{
	POSITION pos = m_lOutputPin.GetHeadPosition();
	CSDeMuxOutPin *pOutputPin;
    while (pos) 
	{
        pOutputPin = m_lOutputPin.GetNext(pos);
		if ( pOutputPin->m_uTrackIndex == uTrackIndex )
		{
			return pOutputPin;
        }
    }
	return NULL;
}


HRESULT CSDeMuxOutPin::GetMediaType( int iPosition, CMediaType **pMediaType )
{
   POSITION pos = m_lMedia.GetHeadPosition();
   while (pos) {
        CMediaType *pmtList = m_lMedia.GetNext(pos);
        if (iPosition-- == 0) {
            *pMediaType = pmtList;
            return S_OK;
        }
    }
    return VFW_S_NO_MORE_ITEMS;
}

HRESULT CSDeMuxFilter::CheckInputType(const CMediaType *pmt)
{
	return S_OK;
}

BOOL CSDeMuxFilter::IsDeclineFilter(  char* pFilterName, CSDeMuxOutPin* pPin )
{
	if ( m_pPushReader->IsMP3Audio( pPin->m_uTrackIndex ) )
	{
		if ( !strcmp( pFilterName, "AC3Filter" ) )
			return TRUE;
	}

	return FALSE;
}
////////////////////////////////////////////////////////////////////////////
//
////////////////////////////////////////////////////////////////////////////

HRESULT CSDeMuxFilter::CreateOutputAllocator(  )
{
	HRESULT hr = 0;
	m_pVideoAllocator = new CSequentialAllocator(
						NULL, // No owner - allocators are separate objects
						&hr,
						L"VideoAllocator");
	if (m_pVideoAllocator == NULL) {
		return E_OUTOFMEMORY;
	}
	if (FAILED(hr)) 	return hr;
	m_pVideoAllocator->AddRef();
	m_pVideoBukAllocator = new CBukAllocator( NULL, &hr, m_pVideoAllocator );
	if (FAILED(hr)) 	return hr;
	m_pVideoBukAllocator->AddRef();

	m_pAudioAllocator = new CSequentialAllocator(
						NULL, // No owner - allocators are separate objects
						&hr,
						L"AudioAllocator");
	if (m_pAudioAllocator == NULL) {
		return E_OUTOFMEMORY;
	}
	if (FAILED(hr)) 	return hr;
	m_pAudioAllocator->AddRef();

	m_pSubPictureAllocator = new CSequentialAllocator(
						NULL, // No owner - allocators are separate objects
						&hr,
						L"SubPictureAllocator");
	if (m_pSubPictureAllocator == NULL) {
		return E_OUTOFMEMORY;
	}
	if (FAILED(hr)) return hr;
	m_pSubPictureAllocator->AddRef();

	return hr;
}

HRESULT CSDeMuxFilter::SetupOutputAllocator( int nContentType, LONG dwBuffers, LONG dwBufferSize  ) 
{
	HRESULT hr = S_OK;
	ALLOCATOR_PROPERTIES Prop={0}, PropActual={0};
	Prop.cBuffers = dwBuffers;       
	Prop.cbBuffer = dwBufferSize; 
	Prop.cbAlign = 1;
	CSequentialAllocator *pAllocator;
	if ( nContentType == VIDEO_DATA ) //Video
	{	
		pAllocator = m_pVideoAllocator;
	}
	else
	if ( nContentType == AUDIO_DATA ) //Audio
	{
		pAllocator = m_pAudioAllocator;
	}
	else
	if ( nContentType == SUBPICTURE_DATA ) //Subpicture
	{
		pAllocator = m_pSubPictureAllocator;
	}
	else
		return E_INVALIDARG;

	if ( pAllocator )
		hr = pAllocator->SetProperties( &Prop, &PropActual );

	if ( nContentType == VIDEO_DATA ) //Video
		m_pVideoBukAllocator->SetProps();

	return hr;

}

HRESULT CSDeMuxFilter::OutputAllocatorCommit( int nContentType  ) 
{
	HRESULT hr = E_FAIL;
	ALLOCATOR_PROPERTIES PropActual={0};
	CSequentialAllocator *pAllocator;
	if ( nContentType == VIDEO_DATA ) //Video
	{
		pAllocator = m_pVideoAllocator;
	}
	else
	if ( nContentType == AUDIO_DATA ) //Audio
	{
		pAllocator = m_pAudioAllocator;
	}
	else
	if ( nContentType == SUBPICTURE_DATA ) //Subpicture
	{
		pAllocator = m_pSubPictureAllocator;
	}
	else
		return E_INVALIDARG;

	if ( pAllocator )
	{
		hr = pAllocator->Commit(); 
		if ( hr != S_OK )
		{
//			DbgLog((LOG_TRACE, 2, TEXT("SDeMux<%s> Failed Commit memory. hr=0x%x" ), LPCTSTR(CWtoT(m_pName)), hr )); 
		}
		hr = pAllocator->GetProperties( &PropActual ); 
//		DbgLog((LOG_TRACE, 2, TEXT("SDeMux<%s> Commit OutputAllocator Buffer size=%d count=%d align=%d hr=0x%x"),
//						LPCTSTR(CWtoT(m_pName)),
//						PropActual.cbBuffer, PropActual.cBuffers, PropActual.cbAlign, hr ));
		_AllocatorBufferTrace(); //ZQ REMOVE ME
	}
	return hr;
}

HRESULT CSDeMuxFilter::OutputAllocatorDecommit( int nContentType )
{
	CSequentialAllocator *pAllocator;
	ALLOCATOR_PROPERTIES PropActual={0};
	if ( nContentType == VIDEO_DATA ) //Video
		pAllocator = m_pVideoAllocator;
	else
	if ( nContentType == AUDIO_DATA ) //Audio
		pAllocator = m_pAudioAllocator;
	else
	if ( nContentType == SUBPICTURE_DATA ) //Subpicture
		pAllocator = m_pSubPictureAllocator;
	else
		return E_INVALIDARG;

	if ( pAllocator == NULL )
		return E_FAIL;

	pAllocator->Flush();
	pAllocator->GetProperties( &PropActual ); 
	HRESULT hr = pAllocator->Decommit();
//	DbgLog((LOG_TRACE, 2, TEXT("SDeMux<%s> Decommit OutputAllocator Buffer size=%d count=%d align=%d hr=0x%x"),
//						LPCTSTR(CWtoT(m_pName)),
//						PropActual.cbBuffer, PropActual.cBuffers, PropActual.cbAlign, hr ));
	return hr;
}


void CSDeMuxFilter::ReleasedSample( IMediaSample * pSample )
{
	CMediaSample* pMediaSample = (CMediaSample*)pSample;
	AM_SAMPLE2_PROPERTIES Props;
	LONGLONG StartTime, StopTime;
	pMediaSample->GetProperties( sizeof(Props), (unsigned char*)&Props );
	pMediaSample->GetMediaTime( &StartTime, &StopTime );
	m_dwReleasedSampleCount++;

	if ( m_bEndOfData && !m_bEndOfStream && m_State == State_Running )
	{
		DWORD OutputCount = TotalOutputSampleCounter();
		//DbgLog((LOG_TRACE, 3, TEXT(" ***********>total out:%d released:%d"), OutputCount, m_dwReleasedSampleCount ));
		if (  0 && Props.tStop-Props.tStart == 1 )
		{
			if ( m_rtCurrPTS - Props.tStart < 2*UNITS )
			{
				char message[128]; 
				m_bEndOfStream = TRUE;
				DbgLog((LOG_TRACE, 3, TEXT(" ***********> end of Sample playing, %s %s"), (LPCSTR)Disp((CRefTime)Props.tStart ), (LPCTSTR)Disp((CRefTime)m_rtCurrPTS) ));
				if ( m_pfnEventDump != NULL  )
				{
					snprintf( message, sizeof(message), "EndOfStream pos:%s", (LPCSTR)Disp( (LONGLONG)Props.tStart, DISP_DEC ) );  
							((EVENT_DUMP)m_pfnEventDump)( m_EventDumpContext, strlen(message), message ); 
					DbgLog((LOG_TRACE, 2, TEXT("Sent Message %s"), message ));
				}
			}
		} else
		if ( m_dwReleasedSampleCount >= OutputCount )
		{
			char message[128]; 
			m_bEndOfStream = TRUE;
			DbgLog((LOG_TRACE, 3, TEXT(" ***********> end of Sample playing, (sample in:%d out:%d ), %s %s"), 
						m_dwReleasedSampleCount, OutputCount,
				        (LPCSTR)Disp((CRefTime)m_rtCurrPTS ), (LPCTSTR)Disp((CRefTime)m_rtCurrPTS) ));
			if ( m_pfnEventDump != NULL  )
			{
				snprintf( message, sizeof(message), "EndOfStream pos:%s", (LPCSTR)Disp( (LONGLONG)m_rtCurrPTS, DISP_DEC ) );  
						((EVENT_DUMP)m_pfnEventDump)( m_EventDumpContext, strlen(message), message ); 
				DbgLog((LOG_TRACE, 2, TEXT("Sent Message %s"), message ));
			}
		}
	}
}

HRESULT CSDeMuxFilter::Active()
{
	m_rtSeekStartPos = 0;
	m_rtSeekStartPTS = 0;
	DbgLog((LOG_TRACE, 2, TEXT("Stream Demux Filter Actived") ));
	return S_OK;
}

HRESULT CSDeMuxFilter::CompleteConnect(IPin *pReceivePin, CBasePin* pFilterPin )
{
	if ( pFilterPin == m_pInputPin )
	{
		{
			CAutoLock lock( ParserLock());
			m_pPushReader->ConnectSourceReader(  );
		}
		if ( m_pPushReader->Init(  ) == S_OK )
		{
	
			m_dwBitsRate = m_pPushReader->m_dwBitRate;
			m_bStaticFile = m_pPushReader->m_bExactDur;
			m_rtLastPTS = MT2PTS(m_pPushReader->m_rtDur);
			LONGLONG llDur = m_pPushReader->GetDuration();
			LONGLONG llPts = PTS2MT( m_rtLastPTS );
			m_rtStartSync = m_pPushReader->m_llFirstPTS;
			DbgLog( (LOG_TRACE, 1, TEXT("FirstPTS:%s, LastPTS:%s, Dur:%s."), 
				(LPCTSTR)Disp((CRefTime)m_rtStartSync),
				(LPCTSTR)Disp((CRefTime)llPts), (LPCTSTR)Disp((CRefTime)llDur) )); 

			m_dwLastPOS = (DWORD)(m_pPushReader->m_rtByteLength );
			m_uSourceFormat = m_pPushReader->m_uInputStreamFormat;
			memset( &m_Statistic, 0, sizeof(m_Statistic) );
			m_pInputPin->ResetPullSampeSate();
			m_bSyncPTS = TRUE;
			m_bSyncOutpinPTS = TRUE;

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
//						DbgLog((LOG_TRACE, 3, "Source Filter:\"%s\" GUID:%s", FilterName, LPCSTR(CGUIDtoA(FilterCLSID)) ));
						FilterInfo.pGraph->Release();
					}
				}
				PinInfo.pFilter->Release();
			}	

			if ( m_bPostSubMsg && m_pPushReader->m_nMainSpu >= 0  )
				m_bPostSubMsg = checkMessageRecipient( );
			if ( m_bPostSubMsg )
			{
				m_pPushReader->PickupSubPictureStream( m_pPushReader->m_nMainSpu );
				DbgLog((LOG_TRACE, 3, "SubTitle Post Message is enabled." ));
			}

		}
			
	} else
	{
		CSDeMuxOutPin *pin; 
		int  i=1; 
		while ( ( pin = (CSDeMuxOutPin *)GetPin(i) ) != pFilterPin )
			i++;

		if ( pin->m_uStreamId == 0 && pin->m_uSubId == 0 )
				m_pPassThrough = pin;
		else
		{

			CMediaType *pmt = pin->GetAcceptMediaType();
			m_pPushReader->SetAcceptMediaType( pin->m_uTrackIndex, pmt );

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
						if ( IsDeclineFilter(  FilterName, pin ) )
						{
//							DbgLog((LOG_TRACE, 3, "Decline decoder Filter:\"%s\" GUID:%s", FilterName, LPCSTR(CGUIDtoA(FilterCLSID)) ));
							return VFW_E_TYPE_NOT_ACCEPTED; 
						}
//						DbgLog((LOG_TRACE, 3, "Decoder Filter:\"%s\" GUID:%s", FilterName, LPCSTR(CGUIDtoA(FilterCLSID)) ));
						FilterInfo.pGraph->Release();
					}
				}
				PinInfo.pFilter->Release();
			}

			if ( pin->m_uContentType == AUDIO_DATA )
				m_pPushReader->PickupAudioStream( pin->m_uTrackIndex );
			if ( pin->m_uContentType == VIDEO_DATA )
				m_pPushReader->PickupVideoStream( pin->m_uTrackIndex );
			if ( pin->m_uContentType == SUBPICTURE_DATA )
				m_pPushReader->PickupSubPictureStream( pin->m_uTrackIndex );

			OutputAllocatorCommit( pin->m_uContentType );
		}
	}

	return S_OK;
}

HRESULT CSDeMuxFilter::DisconnectConnection( CBasePin* pFilterPin )
{
	if ( pFilterPin == m_pInputPin )
	{
		{
			CAutoLock lock( ParserLock());
			m_pPushReader->DisconnectSourceReader();
		}
	}
	else
	{
		CSDeMuxOutPin *pin = (CSDeMuxOutPin *)pFilterPin;
		OutputAllocatorDecommit( pin->m_uContentType );
		m_pPushReader->DropStream( pin->m_uTrackIndex );
	}
	
	return S_OK;
}

void CSDeMuxFilter::BreakConnect()
{
	 //DestroyOutputPins
	CSDeMuxOutPin *pin; 
	ASSERT(m_State == State_Stopped);
	
	for (;;) 
	{
		pin = m_lOutputPin.RemoveHead();
		if ( pin == NULL )
			break;

		//  Disconnect if necessary
		IPin *peer = pin->GetConnected();
		if (peer != NULL) 
		{
			peer->Disconnect();
			pin->Disconnect();
		}
		pin->Release();
	}
}

STDMETHODIMP CSDeMuxFilter::Stop()
{
	CAutoLock lck(&m_csFilter);
    HRESULT hr;

	DbgLog((LOG_TRACE, 1, TEXT("@@ Filter Stop"))); //ZQ
	SendAnyway();
    if ( ThreadExists() ) 
	{
		DbgLog((LOG_TRACE, 1, TEXT("Stop Thread")));


		AllocatorStop(); //ZQ!!!
		hr = ThreadStop();

		if (FAILED(hr)) {
			return hr;
		}
		
		DbgLog((LOG_TRACE, 1, TEXT("Exit Thread")));
		hr = ThreadExit();
		if (FAILED(hr)) {
			return hr;
		}

		DbgLog((LOG_TRACE, 1, TEXT("Close Thread")));
		Close();	// Wait for the thread to exit, then tidy up.
    }
	
	FlushStreamsBegin( );
	FlushStreamsEnd( );
	return CBaseFilter::Stop( );
}

STDMETHODIMP CSDeMuxFilter::Pause()
{
	CAutoLock lck(&m_csFilter);
	int State;
	HRESULT hr = S_OK;
	State = m_State;

	DbgLog(( LOG_TRACE, 1, TEXT("@@ Filter Pause") ));

	if ( State == State_Paused )
	{
		DbgLog(( LOG_TRACE, 1, TEXT("## Filter is at Paused state, call pause (should be called)") )); //should be called ZQ.
		hr = CBaseFilter::Pause();
	} else
	if ( State == State_Running )
	{
		DbgLog(( LOG_TRACE, 1, TEXT("## Filter is at running state, call pause") ));
		hr = CBaseFilter::Pause();
	} else
	if ( State == State_Stopped ) 
	{
		DbgLog(( LOG_TRACE, 1, TEXT("## Filter is at stopped state, call pause, start thread") ));
		hr = CBaseFilter::Pause();
		if ( hr == S_OK )
		{
			if ( State == State_Stopped ) 
			{ 

				ASSERT(!ThreadExists());
				
				AllocatorRun(); //ZQ!!!

				if ( m_bSeekPos )
				{
					LONGLONG llStartTime, llStopTime;
					GetSeekPos( &llStartTime, &llStopTime );
					SeekStartPos( llStartTime, &m_rtSeekStartPTS );
					m_pInputPin->ResetPullSampeSate();
					m_bSyncOutpinPTS = TRUE;
				}

				// start the thread
				if ( !Create() ) 
				{
					return E_FAIL;
				}

				// Tell thread to initialize. If OnThreadCreate Fails, so does this.
				HRESULT hr = ThreadInit();
				if (FAILED(hr))
				{
					DbgLog((LOG_TRACE, 1, TEXT("Filter Thread Init failed")));
					return hr;
				}
				
				
				ThreadRun();
			} 
		}
	}
	
	return hr;
}


STDMETHODIMP CSDeMuxFilter::Run(REFERENCE_TIME tStart)
{
	HRESULT hr;
	CAutoLock lck(&m_csFilter);
	//ThreadRun();
	DbgLog((LOG_TRACE, 3, TEXT("@@ Filter Run") ) );
	hr = CBaseFilter::Run( tStart );

	return hr;
}

HRESULT CSDeMuxFilter::SetPositions( LONGLONG *pCurrent,  DWORD CurrentFlags,
                        LONGLONG *pStop,  DWORD StopFlags ) 
{
    HRESULT hr = S_OK;
	LONGLONG current, stop;
    DWORD StopPosBits = StopFlags & AM_SEEKING_PositioningBitsMask;
    DWORD StartPosBits = CurrentFlags & AM_SEEKING_PositioningBitsMask;
	LONGLONG start_time, stop_time;
	DbgLog((LOG_TRACE, 3, TEXT("@@ Filter SetPositions") ) );

    if ( StopFlags ) 
	{
        CheckPointer(pStop, E_POINTER);
        // accept only relative, incremental, or absolute positioning
        if(StopPosBits != StopFlags) {
            return E_INVALIDARG;
        }
    }

    if ( CurrentFlags ) 
	{
        CheckPointer(pCurrent, E_POINTER);
        if ( StartPosBits != AM_SEEKING_AbsolutePositioning &&
             StartPosBits != AM_SEEKING_RelativePositioning) {
            return E_INVALIDARG;
        }
    }

	current = *pCurrent; //* m_dwBitsRate / UNITS;
	stop  = *pStop;// * m_dwBitsRate / UNITS;
	start_time = 0;
	stop_time = stop;
	// scope for autolock
	{
		CAutoLock lock(m_pLock);

		// set start position
		//m_pInputPin->GetSampleTimeRange( (ULONGLONG*)&start_time, (ULONGLONG*)&stop_time );
		if(StartPosBits == AM_SEEKING_AbsolutePositioning) 
		{
			start_time = current;
		}

		else if(StartPosBits == AM_SEEKING_RelativePositioning)
		{
			start_time += current;
		}

		// set stop position
		if(StopPosBits == AM_SEEKING_AbsolutePositioning) 
		{
			stop_time = stop;
		}

		else if(StopPosBits == AM_SEEKING_IncrementalPositioning) 
		{
			stop_time = start_time + stop;
		}

		else if(StopPosBits == AM_SEEKING_RelativePositioning) 
		{
			stop_time = stop_time + stop;
		}
	}

	if ( StartPosBits )
	{

		//DbgLog( (LOG_TRACE, 3, TEXT(" Set pos Range=%d-%d  time %d-%d."), start_time, stop_time, current, stop ));
		DbgLog( (LOG_TRACE, 3, TEXT("App set pos at time %s stop:%s last seek:%s."), (LPCTSTR)Disp( (CRefTime)current ), 
			                   (LPCTSTR)Disp( (CRefTime)stop ), (LPCTSTR)Disp( (CRefTime)m_rtLastSeekPos)	));

		//skip duplicated seek pos
		if ( start_time == m_rtLastSeekPos && time( NULL ) - m_dwLastSeekActionTime <= 1 ) 
		{
			DbgLog( (LOG_TRACE, 3, TEXT("Drop duplicatied seeking command interval:%d  last:'%s'." ), 
			                   time(NULL)-m_dwLastSeekActionTime, (LPCTSTR)Disp( (CRefTime)m_rtLastSeekPos ) ));
			return hr;
		}
		SetSeekPos( &start_time , &stop_time );  
		
		if ( ThreadExists() )
		{
			CAutoLock lck(&m_csFilter);

			AllocatorStop();
			m_pPushReader->Stop();
			FlushStreamsBegin( );  
			FlushStreamsEnd( );
			SendAnyway();
			ThreadPause();

			FlushStreamsBegin( );  
			FlushStreamsEnd( );
			SendAnyway();

			if ( m_bSeekPos )
			{
				LONGLONG llStartTime, llStopTime;
				GetSeekPos( &llStartTime, &llStopTime );
				SeekStartPos( llStartTime, &m_rtSeekStartPTS );
				m_pInputPin->ResetPullSampeSate();
				m_bSyncOutpinPTS = TRUE;
			}
	
			m_rtLastSeekPos = start_time;
			m_dwLastSeekActionTime = (unsigned long)time( NULL );

#ifdef DUMP_ENABLE
	m_pInputPin->rewind_dump();
	rewind_dump( );
#endif

			m_pPushReader->Run();
			AllocatorRun();
			ThreadRun();
		}
	}
	return hr;
}


void CSDeMuxFilter::SetSeekPos( LONGLONG *pStart, LONGLONG* pStop )
{
	//DbgLog((LOG_TRACE, 3, TEXT("SetSeekPos" ) ));
	CAutoLock lck(&m_csFilter);
	m_SeekPos.start = *pStart;
	m_SeekPos.stop = *pStop;
	m_bSeekPos = TRUE;
}

void CSDeMuxFilter::GetSeekPos( LONGLONG *pStart, LONGLONG* pStop )
{
	DbgLog((LOG_TRACE, 3, TEXT("GetSeekPos" ) ));
	CAutoLock lck(&m_csFilter);
	*pStart = m_SeekPos.start;
	*pStop  = m_SeekPos.stop;
	m_bSeekPos = FALSE;
}

LONGLONG CSDeMuxFilter::SeekStartPos( LONGLONG StartTime, LONGLONG* pStartPTS )
{
	LONGLONG llPTS=StartTime;
	DbgLog((LOG_TRACE, 3, TEXT("------>SeekPos %s."), (LPCTSTR)Disp((CRefTime)(StartTime)) ));
	m_pPushReader->SeekPTS( llPTS );
	return 0;
}


void CSDeMuxFilter::FlushStreamsBegin( )
{
	HRESULT hr;
	DbgLog((LOG_TRACE, 3, TEXT("FlushStreamsBegin" ) ));
	hr = m_pInputPin->BeginFlush();

	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) 
	{	
		CSDeMuxOutPin *pOutputPin = m_lOutputPin.GetNext(pos);
		if ( pOutputPin->IsConnected() )
		{
			pOutputPin->DeliverBeginFlush();
		}
    }
}

void CSDeMuxFilter::FlushStreamsEnd( )
{
	HRESULT hr;
	DbgLog((LOG_TRACE, 3, TEXT("FlushStreamsEnd" ) ));
	hr = m_pInputPin->EndFlush();

	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) 
	{	
		CSDeMuxOutPin *pOutputPin = m_lOutputPin.GetNext(pos);
		if ( pOutputPin->IsConnected() )
		{
			pOutputPin->DeliverEndFlush();
		}
    }
	m_dwLastSeekActionTime = 0;
}

void CSDeMuxFilter::DeliverEndOfStream( )
{
	DbgLog((LOG_TRACE, 3, TEXT("Filter DeliverEndOfStream" ) ));
	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) 
	{	
		CSDeMuxOutPin *pOutputPin = m_lOutputPin.GetNext(pos);
		if ( pOutputPin->IsConnected() )
		{
			pOutputPin->DeliverEndOfStream();
			pOutputPin->SendAnyway();
		}
    }
}


void CSDeMuxFilter::SendAnyway( )
{
	CSDeMuxOutPin *pin; 

	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) {
        pin = m_lOutputPin.GetNext(pos);
		if ( pin->IsConnected() )
		{
			pin->SendAnyway( );
		}
    }
}

void CSDeMuxFilter::ResetOutputSampleCounter( )
{
	CSDeMuxOutPin *pin; 
	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) {
        pin = m_lOutputPin.GetNext(pos);
		if ( pin->IsConnected() )
		{
			pin->ResetSampleCount();
		}
    }
}

DWORD CSDeMuxFilter::TotalOutputSampleCounter( )
{
	DWORD dwTotalCount = 0;
	CSDeMuxOutPin *pin; 
	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) {
        pin = m_lOutputPin.GetNext(pos);
		if ( pin->IsConnected() )
		{
			dwTotalCount += pin->OutSampleCount();
		}
    }
	return dwTotalCount;
}

BOOL CSDeMuxFilter::IsQutputPinActive( )
{
	CSDeMuxOutPin *pin; 

	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) {
        pin = m_lOutputPin.GetNext(pos);
		if ( pin->IsConnected() )
		{
			if ( pin->m_pOutputQueue != NULL )
				return TRUE;
		}
    }

	return FALSE;
}

void CSDeMuxFilter::AllocatorStop()
{
	if ( m_pAllocator != NULL ) 
		m_pAllocator->Stop();

	if ( m_pVideoAllocator )
		m_pVideoAllocator->Stop();

	if ( m_pAudioAllocator )
		m_pAudioAllocator->Stop();

	if ( m_pSubPictureAllocator )
		m_pSubPictureAllocator->Stop();
}

void CSDeMuxFilter::AllocatorRun()
{
	if ( m_pAllocator != NULL ) 
		m_pAllocator->Run();

	if ( m_pVideoAllocator )
		m_pVideoAllocator->Run();

	if ( m_pAudioAllocator )
		m_pAudioAllocator->Run();

	if ( m_pSubPictureAllocator )
		m_pSubPictureAllocator->Run();
}

void CSDeMuxFilter::_AllocatorBufferTrace()
{
	if ( m_pVideoAllocator )
		m_pVideoAllocator->_TraceFreeBufferNum();

	if ( m_pAudioAllocator )
		m_pAudioAllocator->_TraceFreeBufferNum();

	if ( m_pSubPictureAllocator )
		m_pSubPictureAllocator->_TraceFreeBufferNum();
}

int CSDeMuxFilter::AllocatorInUsedSampleNum()
{
	int Num = 0;
	if ( m_pAllocator != NULL ) 
	{
		//Num = m_pAllocator->InUsedBufferNum();
		Num += m_pVideoAllocator->InUsedBufferNum();
		Num += m_pAudioAllocator->InUsedBufferNum();
		Num += m_pSubPictureAllocator->InUsedBufferNum();
	}
	return Num;
}


/////////////////////////////////////////////////////////////////////////////////////////////////
HRESULT CSDeMuxFilter::CreateStream( BYTE uContentType, BYTE uStreamId, BYTE uSubId, BYTE uTrackIndex, 
								            CMediaType *pMT, LPCWSTR pName )
{
	CSDeMuxOutPin* pOutputPin = CreateOutputPin( uContentType, uStreamId, uSubId, uTrackIndex, pMT, pName );
	if ( pOutputPin != NULL )
	{
		pOutputPin->AddRef();
		if ( pOutputPin->m_uContentType == VIDEO_DATA )
		{
			LONG dwBufferSize = m_dwBlockSize;
			//ZQ TEST.
			DWORD dwTotalBytes = m_dwBlockSize * m_dwBlockNum;
			if ( m_dwReaderBlockSize*3 > dwTotalBytes )
			{
				DbgLog((LOG_TRACE, 3, TEXT("Warning: Need more sample number for video: (size:%d num:%d total:%d)"),
							m_dwBlockSize, m_dwBlockNum, dwTotalBytes ));
			}

			////Based on picture size to alloc memory size
			//int PictureSize = m_pPushReader->GetVideoPictureSize(uTrackIndex);
			//if ( PictureSize > 0 )
			//{
			//	dwBufferSize = m_pPushReader->GetVideoPictureSize(uTrackIndex)/32;
			//	dwBufferSize -= dwBufferSize %1024;
			//}
			SetupOutputAllocator( VIDEO_DATA, m_dwBlockNum, m_dwBlockSize );
		} else	
		if ( pOutputPin->m_uContentType == AUDIO_DATA )
		{
			int nChannel = m_pPushReader->GetAudioChannel(uTrackIndex);
			if ( nChannel <= 0 ) nChannel = 2;
			LONG dwBufferSize = 1024*4; 
			LONG dwBufferNum = (nChannel+2)*m_dwBlockNum;

			if ( m_pPushReader->GetTrackFormat( uTrackIndex ) == SAGE_FOURCC( "WMA " ) )
				dwBufferSize *= 4;
			else
			if ( m_pPushReader->GetTrackFormat( uTrackIndex ) == SAGE_FOURCC( "RV40" ) )
				dwBufferSize *= 4;
			else
			if ( m_pPushReader->GetTrackFormat( uTrackIndex ) == SAGE_FOURCC( "VRBS" ) )
				dwBufferSize *= 2;
			else
			if ( m_pPushReader->GetTrackFormat( uTrackIndex ) == SAGE_FOURCC( "VRBS" ) )
				dwBufferSize *= 2;
			//else
			//if ( m_pPushReader->IsMP3Audio( uTrackIndex ) )
			//{
			//	dwBufferSize = 65541;
			//}
			//else
			//if ( m_pPushReader->GetTrackFormat( uTrackIndex ) == SAGE_FOURCC( "COOK" ) )
			//	dwBufferSize = 192000;
			SetupOutputAllocator( AUDIO_DATA, dwBufferNum, dwBufferSize );
		} else	
		if ( pOutputPin->m_uContentType == SUBPICTURE_DATA )
		{
			LONG dwBufferSize = 1024; 
			SetupOutputAllocator( SUBPICTURE_DATA, m_dwBlockNum, dwBufferSize );
		} 

		return RegisterOutputPin( pOutputPin );
	}
	return E_OUTOFMEMORY;

}

HRESULT CSDeMuxFilter::GetMediaType( BYTE uStreamId, BYTE uSubId, CMediaType **pMediaType, int nIndex )
{
	CSDeMuxOutPin *OutputPin = FindOutputPin( uStreamId, uSubId );
	if ( OutputPin == NULL )
	{
		 *pMediaType = NULL;
		 return VFW_E_BAD_KEY;
	}
	return OutputPin->GetMediaType( nIndex, pMediaType );
}

HRESULT CSDeMuxFilter::AddMediaType( BYTE uTrackIndex, CMediaType *pMediaType )
{
	CSDeMuxOutPin *OutputPin = FindOutputPin( uTrackIndex );
	if ( OutputPin == NULL )
	{
		 return VFW_E_BAD_KEY;
	}
	return OutputPin->AddMediaType( pMediaType );
}

HRESULT CSDeMuxFilter::SendSampleData( BYTE uTrackIndex, 
			const BYTE *pData, LONG lBytes, REFERENCE_TIME rtStart, int nSync )
{
	CSDeMuxOutPin* pStreamPin;
	REFERENCE_TIME  rtStop=0;
	BOOL bDiscountinuty;

	pStreamPin = FindOutputPin( uTrackIndex );
	if ( pStreamPin == NULL ) 
		return E_FAIL;

	bDiscountinuty = FALSE;

	if ( nSync ) 
	{
		rtStop  = rtStart+1;  //m_dwBitsRate;

		{

			double ratio;
			if ( rtStart > 0 )
			{
				ratio = (long)(m_rtCurrPOS * UNITS /rtStart);
				if ( m_dwPTSCount > 0 )
					m_dSeekRatio = ( m_dwPTSCount * m_dSeekRatio + ratio )/(m_dwPTSCount+1);
				m_dwPTSCount++;
			}

			if(  m_bSyncPTS )  
			{
				m_rtStartSync = rtStart-1;
				m_bSyncPTS = FALSE;
				DbgLog((LOG_TRACE, 3, TEXT("----------->Sample Sync Pos:%s, PTS:%s SEEK:%s track:%d"), 
					(LPCTSTR)Disp( m_rtStartPOS, DISP_DEC ), (LPCTSTR)Disp( (CRefTime)rtStart ), (LPCTSTR)Disp( (CRefTime)m_rtLastSeekPos ), uTrackIndex));
			} 

			if ( m_rtLastPTS < rtStart )
			{
				m_rtLastPTS = rtStart;
				m_dwLastPOS = m_rtCurrPOS;
			}
			//DbgLog((LOG_TRACE, 3, TEXT(" --------->Sample passing sync %s track:%d"), (LPCTSTR)Disp( (CRefTime)rtStart ), uTrackIndex  ));
		}

		rtStart -= m_rtStartSync;

		if ( pStreamPin->IsVideoPin() )
			rtStart += m_rtAudioDelay;

		if ( rtStart < 0 ) rtStart = 0;

		m_rtCurrPTS = rtStart;

	}


	if (  pStreamPin->IsConnected() )
	{
			//normall play bypassing seeker
			BOOL bFirstSample = pStreamPin->FirstSample();

			if ( bFirstSample && nSync )                //NewSegment
			{
				//pStreamPin->DeliverNewSegment(  0, m_rtLastPTS-m_rtStartSync, 1.0 ); 
				pStreamPin->DeliverNewSegment(  0, m_rtLastPTS, 1.0 ); 
			}

			pStreamPin->SendSample( pData, lBytes, rtStart, rtStart+1, nSync ); 
			//pStreamPin->SendAnyway();
	}

	return S_OK;

}


/////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////

HRESULT CSDeMuxFilter::DoThreadProcessingLoop(void) 
{
	IMediaSample* pVideoSample = NULL ;
	IMediaSample* pAudioSample = NULL ;
	IMediaSample* pSubPictureSample = NULL ;
	int nVideoTrack, nAudioTrack, nSubPictureTrack;
	DWORD nBufferSize;
	PBYTE pBuffer;

    Command com;
	BOOL bDiscontinuity = TRUE;
	DWORD dwSampleCount = 0;
	DbgLog((LOG_TRACE, 1, TEXT("SDeMux Filter PullPump start.")));
	m_rtStartPOS = 0;
	m_bEndOfData = FALSE;
	m_bEndOfStream = FALSE;

	_video_free_num_ = _video_busy_ = _video_empty_ = _video_counter_ = 0;
	_audio_free_num_ = _audio_busy_ = _audio_empty_ = _audio_counter_ = 0;
	_max_video_size_ = 0;
	nVideoTrack = m_pPushReader->m_nConnectedVideoTrack;
	nAudioTrack = m_pPushReader->m_nConnectedAudioTrack;
	nSubPictureTrack = m_pPushReader->m_nConnectedSubPictureTrack;
	m_dwReleasedSampleCount = 0;
	ResetOutputSampleCounter();

	if ( !m_pInputPin->IsConnected() )
	{
		DbgLog((LOG_TRACE, 1, TEXT("InputPin isn't connected, PullPump quit.")));
		return S_OK;
	}

	if ( GetThreadPriority(GetCurrentThread() ) != THREAD_PRIORITY_HIGHEST )
		SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_HIGHEST );

    OnThreadStartPlay();
	setDebugTrace( "DebugTrace.log", 4 );
    do {
			while ( !CheckRequest( &com ) ) 
			{
				int nState = m_pInputPin->CheckPullSampeState( );
					
				if ( nState == END_OF_DATA )
				{
					if ( m_pAllocator != NULL ) 
						m_pAllocator->Flush();
					DbgLog((LOG_TRACE, 2, TEXT("..........Thread waiting cmd after file end.") ));
					com = GetRequest( );
					m_bEndOfData = FALSE;
					m_bEndOfStream = FALSE;
					DbgLog(( LOG_TRACE, 1, TEXT("..........Thread get cmd:%s, runing..."), CmdName(com) ));
				}

				if (  m_bSyncOutpinPTS )
				{
					POSITION pos = m_lOutputPin.GetHeadPosition();
					while (pos) 
					{	
						CSDeMuxOutPin *pPin = m_lOutputPin.GetNext(pos);
						pPin->m_bFirstSample = TRUE; //ask outpin deliver a newsegment
					}
					m_bSyncOutpinPTS = FALSE;
					m_bSyncPTS = TRUE;
				}

				{
					int HasPts, Bytes;
					LONGLONG llPts;
					unsigned char *pData;
					int Length, GroupFlag;

					if ( pVideoSample == NULL && nVideoTrack >= 0 )
					{	//ZQ TEST
						//m_pVideoAllocator->GetBuffer( &pVideoSample );
						m_pVideoBukAllocator->GetBukBuffer( m_dwReaderBlockSize, &pVideoSample );
					}
					if ( pAudioSample == NULL && nAudioTrack >= 0 )
						m_pAudioAllocator->GetBuffer0( &pAudioSample );
					if ( pSubPictureSample == NULL && nSubPictureTrack >= 0 )
						m_pSubPictureAllocator->GetBuffer0( &pSubPictureSample );
					if ( pVideoSample != NULL )
					{
						nBufferSize = pVideoSample->GetSize();
						pVideoSample->GetPointer( &pBuffer );
						Bytes = m_pPushReader->ProcessVideo( pBuffer, nBufferSize, &llPts, &HasPts );
						pVideoSample->SetActualDataLength( Bytes );
						if ( Bytes > 0 ) //ZQ
							m_pVideoBukAllocator->ReleaseUnusedBuffer( Bytes, pVideoSample );
						if ( Bytes > 0 )
						{
							unsigned char *pDataBlock;
							int nDataBlockSize;
							pDataBlock = pBuffer;
							nDataBlockSize = Bytes;
							int FrameReady = 1;
							if ( HasPts )	
								llPts = PTS2MT( llPts );
							else
								llPts = -1;

							if ( m_pPushReader->m_pTrackInf[nVideoTrack].av.format_fourcc == SAGE_FOURCC( "VC1 " ) || 
								 m_pPushReader->m_pTrackInf[nVideoTrack].av.format_fourcc == SAGE_FOURCC( "WMV3" ) ||
								 m_pPushReader->m_pTrackInf[nVideoTrack].av.format_fourcc == SAGE_FOURCC( "RV40" )  )
							{
								FrameReady = MergeVideoDataIntoFrame( pVideoSample, &pDataBlock, &nDataBlockSize, &llPts );
							}

							if ( FrameReady > 0 )
							{
								HasPts = (llPts != -1) ;
								Length = m_pPushReader->RePackData( nVideoTrack, pDataBlock, nDataBlockSize, &pData, &GroupFlag );
								if ( m_pPushReader->m_pTrackInf[nVideoTrack].av.format_fourcc == SAGE_FOURCC( "VC1 " ) )
								{
									if ( HasPts && !GroupFlag ) HasPts = 0;
									//if ( HasPts && !GroupFlag ) HasPts |= DISBALE_SAMPLE_PTS_FLAG;
								} else
								if ( 0 && m_pPushReader->m_pTrackInf[nVideoTrack].av.format_fourcc == SAGE_FOURCC( "RV40" ) )
								{	
									if ( HasPts ) HasPts |= DISBALE_SAMPLE_PTS_FLAG;
								}
									//if ( HasPts && !GroupFlag ) HasPts |= DISBALE_SAMPLE_PTS_FLAG;
								SendSampleData( nVideoTrack, pData, Length, llPts, HasPts );
								ReleaseVideoFrame( );
							}

							dwSampleCount++;
							pVideoSample->Release();
							pVideoSample = NULL;

							_video_free_num_ += m_pVideoAllocator->FreeBufferNum();
							if ( m_pVideoAllocator->FreeBufferNum() <10 ) _video_busy_++;
							if ( m_pVideoAllocator->FreeBufferNum() > m_pVideoAllocator->TotalBufferNum()-10 ) _video_empty_++;
							if ( (int)_max_video_size_ < Bytes ) _max_video_size_ = (DWORD)Bytes;
							_video_counter_++;

						}
					} else
					{
						Bytes = m_pPushReader->ProcessVideo( m_dwTrashBuffer, m_dwTrashSize, &llPts, &HasPts );
					}

					if ( pAudioSample != NULL )
					{
						nBufferSize = pAudioSample->GetSize();
						pAudioSample->GetPointer( &pBuffer );
						Bytes = m_pPushReader->ProcessAudio( pBuffer, nBufferSize, &llPts, &HasPts );
						if ( Bytes > 0 )
						{
							if ( HasPts )	llPts = PTS2MT( llPts );
							Length = m_pPushReader->RePackData( nAudioTrack, pBuffer, Bytes, &pData, &GroupFlag );

							SendSampleData( nAudioTrack, pData, Length, llPts, HasPts );

							//if  more than one audio rendering deviced connected, and not set clock device right, 
							// we need to copy data to all to make time sync work
							if ( m_bAudioClockFix ) 
							{
								POSITION pos = m_lOutputPin.GetHeadPosition();
								while (pos) 
								{	
									CSDeMuxOutPin *pPin = m_lOutputPin.GetNext(pos);
									if ( pPin->IsConnected() && pPin->m_uContentType == AUDIO_DATA && 
										 pPin->m_uTrackIndex != nAudioTrack )
									{
										SendSampleData( pPin->m_uTrackIndex, pData, Length, llPts, HasPts );
									}
								}
							}

							dwSampleCount++;
							pAudioSample->Release();
							pAudioSample = NULL;

							_audio_free_num_ += m_pAudioAllocator->FreeBufferNum();
							if ( m_pAudioAllocator->FreeBufferNum() <10 ) _audio_busy_++;
							if ( m_pAudioAllocator->FreeBufferNum() > m_pAudioAllocator->TotalBufferNum()-10 ) _audio_empty_++;
							_audio_counter_++;

						}
					} else
					{
						Bytes = m_pPushReader->ProcessAudio( m_dwTrashBuffer, m_dwTrashSize, &llPts, &HasPts );
					}

					if ( pSubPictureSample != NULL )
					{
						nBufferSize = pSubPictureSample->GetSize();
						pSubPictureSample->GetPointer( &pBuffer );
						Bytes = m_pPushReader->ProcessSubPicture( pBuffer, nBufferSize, &llPts, &HasPts );
						if ( Bytes > 0  )
						{
							if ( HasPts )	llPts = PTS2MT( llPts );
							Length = m_pPushReader->RePackData( nSubPictureTrack, pBuffer, Bytes, &pData, &GroupFlag );
							if ( HasPts ) HasPts |= DISBALE_SAMPLE_PTS_FLAG;
							SendSampleData( nSubPictureTrack, pData, Length, llPts, HasPts );
							
							PostSubMesage( nSubPictureTrack, pData, Length, llPts, HasPts );

							dwSampleCount++;
							pSubPictureSample->Release();
							pSubPictureSample = NULL;
						}
					} else
					{
						Bytes = m_pPushReader->ProcessSubPicture( m_dwTrashBuffer, m_dwTrashSize, &llPts, &HasPts );
						if ( Bytes > 0 )
						{
							if ( HasPts )	llPts = PTS2MT( llPts );
							if ( Bytes >= (int)m_dwTrashSize ) 
								Bytes = m_dwTrashSize - 1;
							m_dwTrashBuffer[Bytes] = 0;
							PostSubMesage( m_pPushReader->m_nMainSpu, m_dwTrashBuffer, Bytes, llPts, HasPts );
						}
					}


					if ( Bytes == 0 && m_pPushReader->EOFOfPusher() )
					{
						m_pInputPin->m_nPullState = END_OF_DATA;
						m_bEndOfData = TRUE;
					//	DbgLog((LOG_TRACE, 3, TEXT("EOF of pusher.") ));
					}
				}
			} /* end of while ( !CheckRequest( &com ) ) */

			DbgLog(( LOG_TRACE, 1, TEXT("..........Thread CMD %s........Sample num:%d"), CmdName(com), dwSampleCount ));
			// For all commands sent to us there must be a Reply call!
			if ( com == CMD_RUN )
			{
				Reply(NOERROR);
			} else 
			while ( com == CMD_PAUSE ) 
			{
				Reply(NOERROR);
				DbgLog(( LOG_TRACE, 1, TEXT("..........Thread waiting cmd"), CmdName(com) ));
				com = GetRequest( );
				DbgLog(( LOG_TRACE, 1, TEXT("..........Thread get cmd:%s, runing..."), CmdName(com) ));
				Reply(NOERROR);
			} 

			if ( com != CMD_STOP && com != CMD_RUN && com != CMD_PAUSE ) 
			{
				Reply((DWORD) E_UNEXPECTED);
				DbgLog(( LOG_ERROR, 1, TEXT("..........Unexpected thread command (%s, %d)!!!"), CmdName(com), com ));
			}

	} while ( com != CMD_STOP );
	if ( pVideoSample != NULL ) 		pVideoSample->Release();
	if ( pAudioSample != NULL ) 		pAudioSample->Release();
	if ( pSubPictureSample != NULL ) 	pSubPictureSample->Release();
	ReleaseAllVideoFrame( );
	DeliverEndOfStream( );  
	DbgLog((LOG_ERROR, 1, TEXT("SDeMux video stat busy:%d, free buf avg:%d, empty:%d, count:%d"), _video_busy_, _video_counter_==0? 0:_video_free_num_/_video_counter_, 
		                                                                             _video_empty_, _video_counter_ ));
	DbgLog((LOG_ERROR, 1, TEXT("SDeMux audio stat busy:%d, free buf avg:%d, empty:%d, count:%d"), _audio_busy_, _audio_counter_==0? 0:_audio_free_num_/_audio_counter_, 
		                                                                             _audio_empty_, _audio_counter_ ));
	DbgLog((LOG_ERROR, 1, TEXT("SDeMux video maxium data block:%d"), _max_video_size_ ));
	DbgLog((LOG_ERROR, 1, TEXT("SDeMux PullPump stopped, total sample pulled %d."), dwSampleCount));
	setDebugTrace( "", 0 );
	return S_FALSE;
}

static char* Language( unsigned long lLanguageCode, char* pLanguage );
void CSDeMuxFilter::PostSubMesage( BYTE uTrackIndex, 
			const BYTE *pData, LONG lBytes, REFERENCE_TIME rtStart, int nSync )
{
	char buf[2048-128], lan[4], code_set[16];
	//unsigned long pts;
	int pos = 0;

	//if ( rtStart < m_rtStartSync )
	//	rtStart = 0;
	//else
	//	rtStart -= m_rtStartSync;
	lan[0] = 0x0;
	code_set[0] = 0x0;
	Language( m_pPushReader->m_pTrackInf[uTrackIndex].language, lan);
	pos = snprintf( buf, sizeof(buf)-pos, "|SUB|PTS:%s|%s|[%s][len=%d]%s|", (LPCTSTR)Disp((LONGLONG)rtStart, CDISP_DEC), lan, code_set, 
					lBytes,	pData );
	if ( m_bPostSubMsg ) 
	{
		int ret = postMessage( "SubSrc", buf, pos, SUB_MSG_TYPE, 100 );
		if ( !ret )
			m_bPostSubMsg = 0;
	}

	FILE *fp = fopen( "SUB_MSG.log", "a" );
	if ( fp != NULL )
	{
		fwrite( buf, 1, pos, fp );
		fwrite( "\r\n", 1, 2, fp );
		fclose( fp );
	}

}

static char* Language( unsigned long lLanguageCode, char* pLanguage )
{
	static  unsigned char _language[4];
	if ( pLanguage == NULL )
		pLanguage = (char*)_language;

	pLanguage[0] = (unsigned char)(lLanguageCode & 0x0ff);
	pLanguage[1] = (unsigned char)(( lLanguageCode >> 8 )  & 0x0ff);
	pLanguage[2] = (unsigned char)(( lLanguageCode >> 16 ) & 0x0ff);
	pLanguage[3] = 0x0;
	return pLanguage;
}

void CSDeMuxFilter::FlatBlock( )
{
	_BLOCK *pBlock;
	int bBlockIsFlat = 1;
	long nSize;
	LONGLONG llPts;
	unsigned char* pStart, *pNext=NULL;
	if ( m_lMergeList.GetCount() < 2 )
		return;

	POSITION pos = m_lMergeList.GetHeadPosition();
	while ( pos != NULL ) 
	{
		pBlock = m_lMergeList.GetNext(pos);
		nSize = pBlock->pSample->GetActualDataLength();
		pBlock->pSample->GetPointer( &pStart );
		//if ( m_pPushReader->IsVC1Header( pStart ) )
		//	pNext = NULL;
		pBlock->pSample->GetMediaTime( &llPts, &llPts );
		if ( llPts != -1  )
			pNext = NULL;
		if ( pNext != NULL &&  pNext != pStart )
		{
			DbgLog(( LOG_TRACE, 3, TEXT("Block isn't flat") ));
 			bBlockIsFlat = 0;
			break;
		}
		pNext = pStart + nSize;
	}

	if ( !bBlockIsFlat )
	{
		unsigned char *s, *t;
		int s_size=0, t_size=0, cp_size; 
		int state;
		CGenericList<_BLOCK> NewSampleList( NAME("TempSampleList") );
		IMediaSample *pNewSample;
		while ( 1 ) 
		{
			if ( s_size == 0 )
			{
				pBlock = m_lMergeList.GetHead();
				if ( pBlock != NULL )
				{
					m_lMergeList.RemoveHead();
					pBlock->pSample->GetPointer( &s );
					s_size = pBlock->pSample->GetActualDataLength();
					pBlock->pSample->GetMediaTime( &llPts, &llPts );
					state = pBlock->state;

					if (  m_pPushReader->IsVC1Header( s ) ) //start a new block
						t_size = 0;

				} else
					break;

			}

			if ( t_size == 0 )
			{
				//ZQ TEST
				//m_pVideoAllocator->GetBuffer( &pNewSample );
				m_pVideoBukAllocator->GetBukBuffer( m_dwReaderBlockSize, &pNewSample );
				if ( pNewSample != NULL )
				{
					_BLOCK *pTmpBlock = new _BLOCK;
					pTmpBlock->pSample = pNewSample;
					pTmpBlock->state = state;
					NewSampleList.AddTail( pTmpBlock );
					pNewSample->SetMediaTime( &llPts, &llPts );
					pNewSample->GetPointer( &t );
					t_size = pNewSample->GetSize();
				} else
				{
					//give up flatblock
					pBlock->pSample->Release();
					delete pBlock;
					break;
				}
			}

			cp_size = min( s_size, t_size );
			memcpy( t, s, cp_size );
			s += cp_size;
			t += cp_size;
			s_size -= cp_size;
			t_size -= cp_size;

			
			pNewSample->SetActualDataLength( pNewSample->GetSize()-t_size );
			if ( s_size == 0 )
			{
				pBlock->pSample->Release();
				delete pBlock;
			}
		}

		//put new block buffers into merge list
		while ( NewSampleList.GetCount() )
		{
			pBlock = NewSampleList.RemoveHead();
			m_lMergeList.AddTail( pBlock );
		}

	}
}

int CSDeMuxFilter::MergeVideoDataIntoFrame( IMediaSample* pSample, unsigned char**pFrameData, int* pFrameSize, LONGLONG *pllPts )
{
	int FrameSize = 0;
	int bFrameStart=0;
	int nBytes;
	unsigned char* pData;
	_BLOCK *pBlock;

	_BLOCK *pNewBlock = new _BLOCK;
	pNewBlock->state = 1;
	pSample->SetMediaTime( pllPts, pllPts );
	pNewBlock->pSample = pSample;
	pSample->AddRef();
	
	pSample->GetPointer( &pData );
	nBytes = pSample->GetActualDataLength();
	m_lMergeList.AddTail( pNewBlock );


 	////VC1 frame start header
	//bFrameStart = m_pPushReader->IsVC1Header( pData );
	bFrameStart = ( *pllPts != -1  );

	if ( !bFrameStart || m_lMergeList.GetCount() <= 1 )
	{
		return 0;
	}

	POSITION pos = m_lMergeList.GetHeadPosition();
	while ( pos != NULL ) 
	{
		pBlock = m_lMergeList.GetNext(pos);
		if ( pBlock->pSample == pSample )
			break;
		pBlock->state = 2;
	}

	FlatBlock( );

	FrameSize = 0;
	pos = m_lMergeList.GetHeadPosition();
	while ( pos != NULL ) 
	{
		pBlock = m_lMergeList.GetNext(pos);
		if ( pBlock->state == 2 )
			FrameSize += pBlock->pSample->GetActualDataLength();
		else
			break;

	}
	pBlock = m_lMergeList.GetHead();
	pBlock->pSample->GetMediaTime( pllPts, pllPts );
	pBlock->pSample->GetPointer( pFrameData );
	*pFrameSize = FrameSize;

	return 1;
}


void CSDeMuxFilter::ReleaseVideoFrame( )
{
	_BLOCK *pBlock;

	while ( m_lMergeList.GetCount() )
	{
		pBlock = m_lMergeList.GetHead();
		if ( pBlock->state > 1 )
		{
			pBlock->pSample->Release();
			delete pBlock;
			m_lMergeList.RemoveHead();

		} else
		{
			break;
		}
	}
}

void CSDeMuxFilter::ReleaseAllVideoFrame( )
{
	_BLOCK *pBlock;
	while ( m_lMergeList.GetCount() )
	{
		pBlock = m_lMergeList.GetHead();
		pBlock->pSample->Release();
		delete pBlock;
		m_lMergeList.RemoveHead();

	}
}

bool CSDeMuxFilter::NeedSampleGroup( int nTrack )
{
	if ( m_pPushReader->m_pTrackInf[nTrack].av.format_fourcc == SAGE_FOURCC( "VC1 " ) || 
		 m_pPushReader->m_pTrackInf[nTrack].av.format_fourcc == SAGE_FOURCC( "WMV3" ) ||
		 m_pPushReader->m_pTrackInf[nTrack].av.format_fourcc == SAGE_FOURCC( "RV40" )  )
	{
		return true;
	}
	return false;
}

void CSDeMuxFilter::LoadCfgFromRegistry( )
{
		//HKEY  rootKey = HKEY_LOCAL_MACHINE;  //if I use this, compilier has a stack trouble
		HKEY  root_key = HKEY_LOCAL_MACHINE;
		DWORD holder=0;
		HKEY  my_key;
		DWORD readType;
		DWORD hsize = sizeof(holder);
		if (RegOpenKeyEx(root_key, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\StrmDeMux", 0, KEY_QUERY_VALUE, &my_key) == ERROR_SUCCESS)
		{
			if (RegQueryValueEx(my_key, "AuidoClockFix", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				m_bAudioClockFix = holder;
				DbgLog(( LOG_TRACE, 3, TEXT("AuidoClockFix=%d"), holder ));
			} 

			if (RegQueryValueEx(my_key, "ReaderBufferSize", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder > 0 )
				{
					m_dwReaderBlockSize = holder;
					m_dwBlockSize *= 1024;
					DbgLog(( LOG_TRACE, 3, TEXT("Get \"ReadBufferSize\" %d from Registry "), m_dwBlockSize ));
				} else
				{
					m_dwReaderBlockSize = PUSH_READER_BLOCK_SIZE;
					DbgLog(( LOG_TRACE, 3, TEXT("Use Default ReadBufferSize %d"), m_dwBlockSize  ));
				}
			} 

			if (RegQueryValueEx(my_key, "BlockSize", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder > 0 )
				{
					m_dwBlockSize = holder;
					m_dwBlockSize *= 1024;
					DbgLog(( LOG_TRACE, 3, TEXT("Get \"BlockSize\" %d from Registry "), m_dwBlockSize ));
				} else
				{
					m_dwBlockSize = BLOCK_SIZE;
					DbgLog(( LOG_TRACE, 3, TEXT("Use Default BlockSize %d"), m_dwBlockSize  ));
				}
	
			} 
			if (RegQueryValueEx(my_key, "BlockNum", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder) 
				{
					m_dwBlockNum = holder;
					DbgLog(( LOG_TRACE, 3, TEXT("Get \"BlockNum\" %d from Registry "), m_dwBlockNum ));
				} else
				{
					m_dwBlockNum = BLOCK_NUM;
					DbgLog(( LOG_TRACE, 3, TEXT("User Default block number %d "), m_dwBlockNum ));
				}
			} 
			if (RegQueryValueEx(my_key, "AC3Disable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~AC3_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("AC3 Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "EAC3Disable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~EAC3_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("EAC3 Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "TrueHDDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~TAC3_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("TrueHD Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "DTSDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~DTS_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("DTS Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "DTSMasterDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~MDTS_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("DTS-Master Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "AACDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~AAC_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("AAC Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "AACHEDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~AACH_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("AAC-HE Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "LPCMDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~LPCM_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("LPCM Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "PCMDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~PCM_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("PCM Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "MPEGDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~MPEG_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("MPEG Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "COOKDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwAudioTypeEnableMask &= ~COOK_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("MPEG Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "DisableDecodeAudio", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_bDisableDecodeAudio = 1;
					DbgLog(( LOG_TRACE, 3, TEXT("DisableDecodeAudio set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "SubPictureDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwSubPcitureEnableMask = 0;
					DbgLog(( LOG_TRACE, 3, TEXT("Subpicture/Subtitle Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "DecodeCookAuido", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pPushReader->m_dwDecodeAudioType |= COOK_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("Decode cook audio (realNetwork) %d"), holder ));
				} 
			} 
			
			if (RegQueryValueEx(my_key, "EnableSubMsg", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder>0) 
				{
					m_bPostSubMsg = 1;
					DbgLog(( LOG_TRACE, 3, TEXT("PostSubMsg%d"), m_bPostSubMsg ));
				} 
			} 
			RegCloseKey(my_key);
		}
}


#define countof(array) (size_t)(sizeof(array)/sizeof(array[0]))
/*
char* CSDeMuxFilter::GetCLSIDname(const CLSID &CLSID, char *Buf, int BufLen )
{
	//LPOLESTR sclsidW;
	//StringFromIID( CLSID, &sclsidW );
	//wchar_t reg[MAX_PATH];
	//_snwprintf( reg, countof(reg), L"\\CLSID\\%s",(sclsidW));
	//CoTaskMemFree(sclsidW);
	char reg[MAX_PATH];
	snprintf( reg, sizeof(reg), "\\CLSID\\%s",LPCSTR(CGUIDtoA(CLSID)) );
	HKEY  root_key = HKEY_CLASSES_ROOT;
	char* holder=Buf;
	DWORD hsize = BufLen;
	HKEY  my_key;
	DWORD readType;
	if (RegOpenKeyEx(root_key, reg, 0, KEY_QUERY_VALUE, &my_key) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(my_key, reg, 0, &readType, (LPBYTE)holder, &hsize) == ERROR_SUCCESS)
		{
			DbgLog((LOG_TRACE, 3, "Decoder GUID:%s", holder ));
		}
		RegCloseKey(my_key);
	}
	return "";
}
*/


void CSDeMuxFilter::dump_data( char* pData, int dwBytes )
{
	if ( fd == NULL ) return;
	if ( dwBytes > 0)	fwrite( pData, 1, dwBytes, fd );
}

void CSDeMuxFilter::dump_sample( IMediaSample *pSample )
{
	BYTE* pBuffer;
	int   Length;
	if ( fd == NULL ) return;
	if ( FAILED(  pSample->GetPointer( &pBuffer ) ) ) return;
	Length = pSample->GetActualDataLength( );
	if ( Length > 0)	
	{
		offset += fwrite( pBuffer,1, Length, fd );
	}

}


void CSDeMuxFilter::open_dump( )
{
	char fname[MAX_PATH];
	char path[MAX_PATH]={0};
	int len;
	sprintf_s( fname, sizeof(fname), "DUMP_FILTER_DATA.ENABLE" );
	fd = fopen( fname, "r" );
	if ( fd == NULL ) return;
	len = fread( path,1, MAX_PATH-2, fd );
	if ( len )
		if ( path[len-1] != '\\' ) path[len] = '\\';
	fclose( fd );
	sprintf_s( fname, sizeof(fname), "%sFilter.bin", path  );
	offset = 0 ;
	fd = fopen( fname, "wb" );
	DbgLog((LOG_TRACE, 2, TEXT("SDeMux dump file %s  fd=0x%x."), fname, fd ) );
}
void CSDeMuxFilter::close_dump( )
{
	if ( fd != NULL )
		fclose( fd );
	fd = NULL;
}

void CSDeMuxFilter::rewind_dump( )
{
	if ( fd != NULL )
		fseek( fd, 0, SEEK_SET );
	offset = 0 ;
	DbgLog((LOG_TRACE, 2, TEXT("CSDeMuxFilter dump file rewinded.")) );
}


#ifdef WIN32   
#include <time.h>
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}
#endif

#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _flog x
#endif

static bool flog_enabled=1;

void _flog( int type, int level, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	 
	if ( !flog_enabled ) return;
	if ( level > DEBUG_LEVEL ) return;

	fp = fopen( "StmDeMux.log", "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );

	/*
	TCHAR szInfo[200];
	wsprintf( szInfo, _TEXT("%02d/%02d/%d %02d:%02d:%02d (tid %06x) "), ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
		ltm.tm_hour, ltm.tm_min, ltm.tm_sec, GetCurrentThreadId() );  
	va_start(args, cstr);
	fprintf( fp, szInfo );
	fprintf( fp, cstr, args );
	fprintf( fp, _TEXT( "\r\n" )  );
	*/
	
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
static void _flog_local_check()
{
	FILE* fp = fopen( "STRMDEMUX.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
		_flog( 0, 0, "----------> StartMpegDeMux ZQ. ---------->" );
	}

}


