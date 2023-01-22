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
#ifndef _WIN64
  #define _USE_32BIT_TIME_T
#endif

#include <streams.h>
#include <pullpin.h>
#include <limits.h>
#include <initguid.h>
#include <ks.h>
#include <time.h>
#include <dvdmedia.h>
#include "../../../third_party/Microsoft/FileSource/strconv.h"

#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"
#include "PSParser.h"
#include "ESAnalyzer.h"
#include "PSBuilder.h"
#include "TSBuilder.h"
#include "BlockBuffer.h"
#include "Demuxer.h"

#include "Seeker.h"
#include "DeMuxInPin.h"
#include "DeMuxOutPin.h"
#include "DeMuxFilter.h"
#include "FilterProperties.h"

// Eliminate silly MS compiler security warnings about using POSIX functions

#undef DUMP_ENABLE
//#define DUMP_ENABLE  1

#define WAIT_EVENT_TIMEOUT 100000
extern "C" {
static void _flog_local_check();
}
//
DEFINE_GUID(CLSID_MpegDeMux,
    0xabd65dea, 0x5701, 0x4f21, 0x86, 0x3b, 0x59, 0x1b, 0x05, 0x7d, 0x22, 0x68 );

////temporary use a name for testing
//DEFINE_GUID(CLSID_MpegDeMux_2,
//    0xabd65def, 0x5701, 0x4f21, 0x86, 0x3b, 0x59, 0x1b, 0x05, 0x7d, 0x22, 0x68 );

DEFINE_GUID(CLSID_FilterPropertyPage, 
	0xdc89b497, 0x3131, 0x44a7, 0x88, 0x4e, 0xbd, 0x1a, 0x57, 0xab, 0x78, 0x63);
	



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
    &CLSID_MpegDeMux,
    L"SageTV MpegDeMux",
    MERIT_DO_NOT_USE,                                     // Don't use us for real!
    3,                   // 3 pins
    sudMpgPins
};

CFactoryTemplate g_Templates[] = {
	{ L"SageTV MpegDeMux", &CLSID_MpegDeMux, CDeMuxFilter::CreateInstance, NULL, &sudMpgsplit },
	{ L"Filter Property Page", &CLSID_FilterPropertyPage , CFilterProperties::CreateInstance, NULL, NULL }
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



CUnknown * WINAPI CDeMuxFilter::CreateInstance(LPUNKNOWN pUnk, HRESULT *phr)
{
	_flog_local_check();

    CUnknown *pUnkRet = new CDeMuxFilter(pUnk, phr);
    return pUnkRet;
}

HRESULT CDeMuxFilter::NonDelegatingQueryInterface(REFIID riid, void **ppv) 
{
	if ( riid == IID_IMpegDeMux )
	{
        CheckPointer(ppv, E_POINTER);
        return GetInterface(static_cast<IMpegDeMux *>(this), ppv);
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
//class CDeMuxFilter
///////////////////////////////////////////////////////////////////////////////////////
CDeMuxFilter::CDeMuxFilter( LPUNKNOWN pUnk, HRESULT *phr) :
CBaseFilter( TEXT("CMpegDeMux"),
			pUnk, 
			&m_csFilter,
			CLSID_MpegDeMux, 
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
	m_bSeekByPTS( TRUE ),
	//m_rtDuration( _I64_MAX / 2 ),
	m_pAllocator( NULL ),
	m_dwBlockSize( BLOCK_SIZE ),
	m_dwBlockNum( BLOCK_NUM ),
	m_bEnableHDAudio( FALSE ),
	m_lOutputPin(NAME( "StreamList" ))
{
	DbgLog((LOG_TRACE, 3, TEXT("DeMuxFilter is created Ver. 3.2.12  ZQ.")) );

	m_dwSeekingCaps = AM_SEEKING_CanSeekForwards
        | AM_SEEKING_CanSeekBackwards
        | AM_SEEKING_CanSeekAbsolute
        | AM_SEEKING_CanGetStopPos
		| AM_SEEKING_CanGetCurrentPos
        | AM_SEEKING_CanGetDuration;

	m_SeekPos.start = 0;
	m_SeekPos.stop = 0;
	m_pInputPin = new CDeMuxInPin( this, phr );

	pSeeker = new CSeeker();

	//m_pDemuxer = NULL;
	m_pDemuxer = new CDemuxer( this ); 
	//m_pDemuxer->SetRemuxOptimization(m_bRemuxOptimization);
	//m_pDemuxer->SetAFDDump( (MSG_DUMP)m_pfnAFDDump, m_AFDDumpContext );
	//m_pDemuxer->SetCCDump( (CC_DUMP)m_pfnCCDump, m_CCDumpContext );
	m_bStaticFile = TRUE;

	m_dwBitsRate = 567113;
	m_rtLastSeekPos = 0;
	m_dwLastSeekActionTime = 0;
	m_rtSeekStartPos = 0;
	m_rtSeekStartPTS = 0;
	m_rtSeekStart = 0;

	LoadCfgFromRegistry();
	m_pInputPin->SetAllocProp( m_dwBlockSize, m_dwBlockNum*m_dwBlockSize );

	m_dSeekRatio = 0;
	m_dwPTSCount = 0;

	m_rtLastPTS = 0;
	m_rtPreStartMediaTimeOffset = 0;
	m_rtStartMediaTimeOffset = 0;
	m_rtSeekMediaTimeOffset = 0;
	m_dwFileSwitchFlag = 0;
	m_dwFileSwitchSync = 0;
	m_bEndOfData = FALSE;
	m_bEndOfStream = FALSE;

	CreateAllocator( );
	m_pOutputAllocator = NULL;

#ifdef DUMP_ENABLE
	open_dump( );
#endif

}

CDeMuxFilter::~CDeMuxFilter()
{
	DbgLog((LOG_TRACE, 3, TEXT("CDeMuxFilter is waiting thread terminated.")) );

	if ( m_pInputPin )
		delete m_pInputPin;

	if ( m_pDemuxer )
		delete m_pDemuxer;

	if ( pSeeker )
		delete pSeeker;

   while ( 1 )
   {
        CDeMuxOutputPin *pOutputPin = m_lOutputPin.RemoveHead();
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

   	if ( m_pAllocator  )
		m_pAllocator->Release();

	if ( m_pOutputAllocator )
		m_pOutputAllocator->Release();
#ifdef DUMP_ENABLE
	close_dump( );
#endif
   
   DbgLog((LOG_TRACE, 3, TEXT("CDeMuxFilter Destroyed ZQ.\r\n")) );
}

STDMETHODIMP CDeMuxFilter::SetRemuxOptimize(BOOL bRemuxOptimization)
{
	m_bRemuxOptimization = bRemuxOptimization;
	return S_OK;
}

STDMETHODIMP CDeMuxFilter::SetAFDDump( void* pfn, void* context )
{
	m_pfnAFDDump = pfn;
	m_AFDDumpContext = context;
	return S_OK;
}

STDMETHODIMP CDeMuxFilter::SetEventDump( void* pfn, void* context )
{
	m_pfnEventDump = pfn;
	m_EventDumpContext = context;
	return S_OK;
}
	
STDMETHODIMP CDeMuxFilter::SetCCDump( void* pfn, void* context )
{
	m_pfnCCDump = pfn;
	m_CCDumpContext = context;
	return S_OK;
}
STDMETHODIMP CDeMuxFilter::Duration( LONGLONG* prtDuration )
{
	GetDuration( prtDuration );
	return S_OK;
}

HRESULT CDeMuxFilter::GetPlayRate( long *pPlayeRate )
{
	*pPlayeRate = (int)m_dPlayRate;
	if ( m_bPlayRewind ) *pPlayeRate = -(*pPlayeRate);
	return S_OK;
}

HRESULT CDeMuxFilter::SetPlayRate( long iPlayRate )
{
	if ( iPlayRate == 0 ) iPlayRate = 1;

	m_bPlayRewind =  ( iPlayRate < 0 );
	if ( iPlayRate < 0 )
		m_dPlayRate = -iPlayRate;
	else
	    m_dPlayRate = iPlayRate;
	return S_OK;
}

STDMETHODIMP CDeMuxFilter::SelectChannel( long dwChannel )
{
	m_dwDefaultChannel = dwChannel;
	if ( m_pDemuxer )
		m_pDemuxer->SetTSChannel( m_dwDefaultChannel );
	return S_OK;
}
	
STDMETHODIMP CDeMuxFilter::GetSelectedChannel( long* pdwChannel )
{
	*pdwChannel = m_dwDefaultChannel;
	return S_OK;
}

STDMETHODIMP CDeMuxFilter::GetStatistic( void** pData )
{
	//update statistic 
	char buf[512]={0};
	m_Statistic.message = NULL;
	m_Statistic.bitrates   = m_pDemuxer->m_dwBitRate;
	m_Statistic.test_data1 = (long)m_pDemuxer->GetLastTimestamp();
	m_Statistic.test_data2 = (long)m_dSeekRatio; 
	*pData = &m_Statistic;
	if ( NeedPrivateAllocator() )
	{
		{
			char tmp[128];
			int free_num = m_pOutputAllocator->FreeBufferNum( );
			int wait_num = m_pOutputAllocator->traceWait( );
			int release_num = m_pOutputAllocator->traceRelease();
			int input_num = m_pAllocator->FreeBufferNum( );
			sprintf_s( tmp, sizeof(tmp), "in-free:%d;free:%d wait:%d rel:%d", input_num, free_num, wait_num, release_num  );
			if ( strlen( buf )+ strlen( tmp ) < sizeof(buf) )
				strcat_s( buf, sizeof(buf), tmp );
		}

		m_Statistic.overflow = 0;
	}
	else 
	{
		char tmp[64];
		int input_num = m_pAllocator->FreeBufferNum( );
		sprintf_s( tmp, sizeof(tmp),  "in-free:%d;ovflw:%03d", input_num, m_Statistic.overflow  );
		if ( strlen( buf )+ strlen( tmp ) < sizeof(buf) )
			strcat_s( buf, sizeof(buf), tmp );
		
	}
	m_Statistic.message = buf;
	return S_OK;
}


STDMETHODIMP CDeMuxFilter::SetAlloProp( long dwBlockSize, long dwBufferSize )
{
	if ( dwBlockSize <= 0 || dwBufferSize < 0 || dwBufferSize < dwBlockSize )
		return E_INVALIDARG ;

	m_pInputPin->SetAllocProp( dwBlockSize, dwBufferSize  );

	return S_OK;
}

STDMETHODIMP CDeMuxFilter::GetAlloProp( long* pdwBlockSize, long* pdwBufferSize )
{
	*pdwBlockSize = m_pInputPin->BlockSize();
	*pdwBufferSize = m_pInputPin->AllocSize();
	return S_OK;
}


/*  -- Return number of pins */
int CDeMuxFilter::GetPinCount()
{
	CAutoLock lock(pStateLock());
	return  1 + m_lOutputPin.GetCount(); 
}

STDMETHODIMP CDeMuxFilter::GetPages(CAUUID *pPages)
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

STDMETHODIMP CDeMuxFilter::FileSwitch( unsigned long dwOffsetPTS )
{
	HRESULT hr = S_OK;
	DbgLog((LOG_TRACE, 3, TEXT("@@ Filter FileSwitch (flag:%d)."), dwOffsetPTS ) );
	DbgLog((LOG_TRACE, 3, TEXT(" --------->switch pre_pos:%s, pos:%s, seek:%s, cur:%s syn:%s"), 
	    (LPCTSTR)Disp( (CRefTime)m_rtPreStartMediaTimeOffset),
		(LPCTSTR)Disp( (CRefTime)(m_rtSeekMediaTimeOffset+(m_rtCurrPTS-m_rtStartSync))),
		(LPCTSTR)Disp( (CRefTime)m_rtSeekMediaTimeOffset),
		(LPCTSTR)Disp( (CRefTime)m_rtCurrPTS),
		(LPCTSTR)Disp( (CRefTime)m_rtStartSync)		));
	CAutoLock lck(&m_csFilter);
	m_pInputPin->SetSamplePosRange( 0, 0 );
	hr = m_pInputPin->BeginFlush();
	hr = m_pInputPin->EndFlush();
	m_dwFileSwitchFlag = 1;
	m_dwFileSwitchSync = 1;
	m_rtPreStartMediaTimeOffset = m_rtStartMediaTimeOffset;
	m_rtStartMediaTimeOffset = m_rtSeekMediaTimeOffset+(m_rtCurrPTS-m_rtStartSync);
	if ( dwOffsetPTS == 0 )
	{
		m_pInputPin->ContinueSample(); //not do new segement
	}
	ThreadRun();
	return hr;
}

STDMETHODIMP CDeMuxFilter::SwitchPosition( LONGLONG* pllPreLastPTS, LONGLONG* pllLastPTS  )
{
	*pllPreLastPTS = m_rtPreStartMediaTimeOffset;
	*pllLastPTS = m_rtStartMediaTimeOffset;
	return S_OK;
}


/////////////////////////////////////////////////////////////////
//IMediaSeeking methods
HRESULT CDeMuxFilter::GetCapabilities( DWORD * pCapabilities ) 
{
    CheckPointer(pCapabilities, E_POINTER);
    *pCapabilities = m_dwSeekingCaps;
    return S_OK;
}

HRESULT CDeMuxFilter::CheckCapabilities( DWORD * pCapabilities ) 
{
    CheckPointer(pCapabilities, E_POINTER);
    return (~m_dwSeekingCaps & *pCapabilities) ? S_FALSE : S_OK;
}

HRESULT CDeMuxFilter::ConvertTimeFormat( LONGLONG * pTarget, 
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

HRESULT CDeMuxFilter::IsFormatSupported(const GUID * pFormat) 
{
    CheckPointer(pFormat, E_POINTER);
    // only seeking in time (REFERENCE_TIME units) is supported
    return *pFormat == TIME_FORMAT_MEDIA_TIME ? S_OK : S_FALSE;
}

HRESULT CDeMuxFilter::QueryPreferredFormat(GUID *pFormat) 
{
    CheckPointer(pFormat, E_POINTER);
    *pFormat = TIME_FORMAT_MEDIA_TIME;
    return S_OK;
}

HRESULT CDeMuxFilter::SetTimeFormat(const GUID * pFormat) 
{
    CheckPointer(pFormat, E_POINTER);
    // nothing to set; just check that it's TIME_FORMAT_TIME
    return *pFormat == TIME_FORMAT_MEDIA_TIME ? S_OK : E_INVALIDARG;
}

HRESULT CDeMuxFilter::IsUsingTimeFormat(const GUID * pFormat) 
{
    CheckPointer(pFormat, E_POINTER);
    return *pFormat == TIME_FORMAT_MEDIA_TIME ? S_OK : S_FALSE;
}

HRESULT CDeMuxFilter::GetTimeFormat(GUID *pFormat) {
    CheckPointer(pFormat, E_POINTER);
    *pFormat = TIME_FORMAT_MEDIA_TIME;
    return S_OK;
}
HRESULT CDeMuxFilter::GetRate( double * pdRate) 
{
    CheckPointer(pdRate, E_POINTER);
    //CAutoLock lock(m_pLock);
    *pdRate = m_dPlayRate;
	//DbgLog((LOG_TRACE, 2, TEXT("****Get Rate Seeking %f"),  m_dPlayRate  ));
    return S_OK;
}

HRESULT CDeMuxFilter::GetPreroll(LONGLONG *pPreroll) 
{
    CheckPointer(pPreroll, E_POINTER);
    *pPreroll = 0;
	DbgLog((LOG_TRACE, 5, TEXT("****GetPreroll" ) ));
    return S_OK;
}

HRESULT CDeMuxFilter::GetAvailable( LONGLONG * pEarliest, LONGLONG * pLatest )
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

HRESULT CDeMuxFilter::GetDuration( LONGLONG *pDuration ) 
{
	CheckPointer(pDuration, E_POINTER);
	ULONGLONG length=0, Available=0;
	HRESULT hr = m_pInputPin->Length( &length, &Available );
	*pDuration = m_pDemuxer->GetDuration();  
	m_pInputPin->SetStopPos( Available );
	DbgLog((LOG_TRACE, 5, TEXT("****Get Duration=%s"),(LPCTSTR)Disp( (CRefTime)*pDuration)	));

    return S_OK;
}

HRESULT CDeMuxFilter::GetPositions( LONGLONG * pCurrent, LONGLONG * pStop ) 
{
	//if we need more precious position, add position of parser ZQ
    if (pCurrent) 
	{
		*pCurrent = m_pDemuxer->GetLastTimestamp( );
	}

    if(pStop) 
	{
		*pStop = m_pDemuxer->GetDuration();
	}

	DbgLog((LOG_TRACE, 3, TEXT("****Get Pos=%s %s"),(LPCTSTR)Disp( (CRefTime)*pCurrent), (LPCTSTR)Disp( (CRefTime)*pStop )	));
    return S_OK;;
}


HRESULT CDeMuxFilter::GetStopPosition(LONGLONG *pStop) 
{
    CheckPointer(pStop, E_POINTER);
    CAutoLock lock(m_pLock);
	*pStop = m_pDemuxer->GetDuration();  
	if ( *pStop == 0 ) *pStop = _I64_MAX / 2;

	//DbgLog((LOG_TRACE, 3, TEXT("****Get stop Pos=%s "), (LPCTSTR)Disp( (CRefTime)*pStop ) ));	
    return S_OK;
}

HRESULT CDeMuxFilter::GetCurrentPosition(LONGLONG *pCurrent) 
{
	CheckPointer(pCurrent, E_POINTER);
	*pCurrent = m_pDemuxer->GetLastTimestamp( );
	DbgLog((LOG_TRACE, 3, TEXT("****Get Cur=%s %s"), (LPCTSTR)Disp( (CRefTime)*pCurrent )	));	
	return S_OK;
}

HRESULT CDeMuxFilter::SetRate( double dRate) 
{
	DbgLog((LOG_TRACE, 3, TEXT("***Set Rate Seeking")));
    CAutoLock lock(m_pLock);
    m_dPlayRate = dRate;
	if ( m_dPlayRate == 0 )	m_dPlayRate = 1; //don't allow zero
    return S_OK;//ChangeRate();
}

STDMETHODIMP CDeMuxFilter::SetClockDelta(REFERENCE_TIME rtDelta)
{
	DbgLog((LOG_TRACE, 2, TEXT("***Audio Delay set to %s"), (LPCSTR)Disp(rtDelta, DISP_DEC)));
	m_rtAudioDelay = rtDelta;
	return S_OK;
}
//////////////////////////////////////////////////////////
/*  -- Return a given pin -- */
CBasePin *CDeMuxFilter::GetPin(int iPin)
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

CDeMuxOutputPin* CDeMuxFilter::CreateOutputPin( BYTE uContentType, BYTE uStreamId, BYTE uSubId, BYTE uTrackIndex, 
											       CMediaType *pMT, LPCWSTR pName )
{
	HRESULT hr;
	CDeMuxOutputPin* pOutputPin = new CDeMuxOutputPin( this, &hr, pName, pMT, uContentType, uStreamId, uSubId, uTrackIndex );
	//DbgLog((LOG_TRACE, 3, TEXT("Stream OutputPin <%s> is created: stream_id:0x%x, sub_id:0x%x."), LPCTSTR(CWtoT(pName)),
	//	                         pOutputPin->m_uStreamId, pOutputPin->m_uSubId ));
	return pOutputPin;
}

HRESULT  CDeMuxFilter::RegisterOutputPin( CDeMuxOutputPin* pOutputPin )
{
	CDeMuxOutputPin *p = FindOutputPin( pOutputPin->m_uStreamId, pOutputPin->m_uSubId );
	if ( p != NULL )
	{
		DbgLog((LOG_TRACE, 3, TEXT("ERROR: Duplicated Stream in RegisterStream.")));
		return E_UNEXPECTED;
	}
	POSITION pos = m_lOutputPin.AddTail( pOutputPin );
	DbgLog((LOG_TRACE, 3, TEXT("Added a Pin 0x%x "), pOutputPin ));

	return S_OK;
}

HRESULT CDeMuxFilter::UnRegisterOutputPin( CDeMuxOutputPin* pOutputPin )
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

CDeMuxOutputPin* CDeMuxFilter::FindOutputPin( BYTE uStreamId, BYTE uSubId )
{
	POSITION pos = m_lOutputPin.GetHeadPosition();
	CDeMuxOutputPin *pOutputPin;
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

CDeMuxOutputPin* CDeMuxFilter::FindOutputPin( BYTE uTrackIndex )
{
	POSITION pos = m_lOutputPin.GetHeadPosition();
	CDeMuxOutputPin *pOutputPin;
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


HRESULT CDeMuxOutputPin::GetMediaType( int iPosition, CMediaType **pMediaType )
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

HRESULT CDeMuxFilter::CheckInputType(const CMediaType *pmt)
{
	return S_OK;
}

////////////////////////////////////////////////////////////////////////////
//
////////////////////////////////////////////////////////////////////////////
HRESULT CDeMuxFilter::CreateAllocator( )
{
	HRESULT hr = 0;
	m_pAllocator = new CSequentialAllocator(
						NULL, // No owner - allocators are separate objects
						&hr,
						L"DeMuxFilter Allocator" );
	if (m_pAllocator == NULL) {
		return E_OUTOFMEMORY;
	}
	if (FAILED(hr)) 
		return hr;

	/*  The base classes expect the allocator to be AddRef'd */
	m_pAllocator->AddRef();

	return hr;
}

HRESULT CDeMuxFilter::AllocatorCommit ( ALLOCATOR_PROPERTIES* pProp ) 
{
	HRESULT hr;
	ALLOCATOR_PROPERTIES actual;
	hr = m_pAllocator->SetProperties( pProp, &actual );
	if ( hr == S_OK )
		hr = m_pAllocator->Commit(); 

	return hr;
}

HRESULT CDeMuxFilter::AllocatorDecommit()
{
	m_pAllocator->Flush();
	HRESULT hr = m_pAllocator->Decommit();
	return hr;
}

HRESULT CDeMuxFilter::CreateOutputAllocator(  )
{
	HRESULT hr = 0;
	m_pOutputAllocator = new CSequentialAllocator(
						NULL, // No owner - allocators are separate objects
						&hr,
						L"DeMuxFilterOutputAllocator" );
	if (m_pOutputAllocator == NULL) {
		return E_OUTOFMEMORY;
	}
	if (FAILED(hr)) 
		return hr;

	/*  The base classes expect the allocator to be AddRef'd */
	m_pOutputAllocator->AddRef();

	return hr;
}

HRESULT CDeMuxFilter::OutputAllocatorCommit( LONG dwBuffers, LONG dwBufferSize  ) 
{
	HRESULT hr;
	ALLOCATOR_PROPERTIES Prop={0}, PropActual={0};
	Prop.cBuffers = dwBuffers;       
	Prop.cbBuffer = dwBufferSize; 
	Prop.cbAlign = 1;
	hr = m_pOutputAllocator->SetProperties( &Prop, &PropActual );
	if ( hr == S_OK )
		hr = m_pOutputAllocator->Commit(); 

	return hr;
}

HRESULT CDeMuxFilter::OutputAllocatorDecommit()
{
	m_pOutputAllocator->Flush();
	HRESULT hr = m_pOutputAllocator->Decommit();
	return hr;
}


void CDeMuxFilter::ReleasedSample( IMediaSample * pSample )
{
	CMediaSample* pMediaSample = (CMediaSample*)pSample;
	
	{
		AM_SAMPLE2_PROPERTIES Props;
		pMediaSample->GetProperties( sizeof(Props), (unsigned char*)&Props );
		if ( m_bEndOfData && !m_bEndOfStream )
		{
			if (  Props.tStop-Props.tStart == 1 )
			{
				if ( m_rtCurrPTS - Props.tStart < 2*UNITS )
				{
					char message[128]; 
					m_bEndOfStream = TRUE;
					DbgLog((LOG_TRACE, 3, TEXT(" ***********> end of Sample playing, %s %s"), (LPCSTR)Disp((CRefTime)Props.tStart ), (LPCTSTR)Disp((CRefTime)m_rtCurrPTS) ));
					if ( m_pfnEventDump != NULL  )
					{
						snprintf( message, sizeof(message), "EndOfStream pos:%s", (LPCSTR)Disp( (LONGLONG)Props.tStart, DISP_DEC ) );  
									((EVENT_DUMP)m_pfnEventDump)( m_EventDumpContext, (short)strlen(message), message ); 
						DbgLog((LOG_TRACE, 2, TEXT("Sent Message %s"), message ));
					}
				}
			} else
			if ( AllocatorInUsedSampleNum() <= 1 )
			{
				char message[128]; 
				m_bEndOfStream = TRUE;
				DbgLog((LOG_TRACE, 3, TEXT(" ***********> end of Sample playing (no more sample in Alloctor), %s %s"), (LPCSTR)Disp((CRefTime)m_rtCurrPTS ), (LPCTSTR)Disp((CRefTime)m_rtCurrPTS) ));
				if ( m_pfnEventDump != NULL  )
				{
					snprintf( message, sizeof(message), "EndOfStream pos:%s", (LPCSTR)Disp( (LONGLONG)m_rtCurrPTS, DISP_DEC ) );  
							((EVENT_DUMP)m_pfnEventDump)( m_EventDumpContext, (short)strlen(message), message ); 
					DbgLog((LOG_TRACE, 2, TEXT("Sent Message %s"), message ));
				}

			}
		}
	}
}

HRESULT CDeMuxFilter::Active()
{
	m_rtSeekStartPos = 0;
	m_rtSeekStartPTS = 0;
	m_pDemuxer->ResetDemuxerPTS( );
	ULONGLONG llFirstPTS = m_pDemuxer->ReadFileFirstPTS(); 
	ULONGLONG llLastPTS;
	if ( !m_pDemuxer->m_bExactDur )
		llLastPTS = ONE_DAY_MT;
	else
	{
		//if it's crashed recording that doens't have a right duration..
		if ( m_pDemuxer->m_bFileCrashed )
		{
			llLastPTS = MT2PTS(m_pDemuxer->m_rtGuessDur) + llFirstPTS;
			m_rtLastPTS = m_pDemuxer->m_rtGuessDur;
			m_bSeekByPTS = 0;
			DbgLog( (LOG_TRACE, 1, TEXT("**** File Crashed! Seek by position to seek instead of by PTS ****"))); 

		} else
		{
			llLastPTS  = m_pDemuxer->ReadFileLastPTS();
			llLastPTS = PTS_ROUND_UP( llLastPTS, llFirstPTS );
			m_rtLastPTS = PTS2MT( llLastPTS );
		}
	}
	m_pDemuxer->UpdateLastPTS( &llFirstPTS, &llLastPTS );
    if ( m_pDemuxer->m_bExactDur )
      m_pDemuxer->m_rtDur = PTS2MT( llLastPTS - llFirstPTS );
	LONGLONG llDur;
	llDur = m_pDemuxer->GetDuration();
	m_dwBitsRate = m_pDemuxer->m_dwBitRate;
	DbgLog( (LOG_TRACE, 1, TEXT("ReadLastPTS LastPTS:%s, Dur:%s, StartPTS:%s, LastPTS:%s."), 
			(LPCTSTR)Disp((CRefTime)m_rtLastPTS), (LPCTSTR)Disp((CRefTime)llDur), 
			(LPCTSTR)Disp((CRefTime)PTS2MT((LONGLONG)llFirstPTS) ), (LPCTSTR)Disp((CRefTime)PTS2MT((LONGLONG)llLastPTS)))); 

	return S_OK;
}

HRESULT CDeMuxFilter::CompleteConnect(IPin *pReceivePin, CBasePin* pFilterPin )
{
	if ( pFilterPin == m_pInputPin )
	{
		if ( !m_bEnableHDAudio )
				m_pDemuxer->SetHDAudioFlag();

		if ( m_pDemuxer->Init() == S_OK )
		{
			m_dwBitsRate = m_pDemuxer->m_dwBitRate;
			m_bStaticFile = m_pDemuxer->m_bExactDur;
			m_rtLastPTS = m_pDemuxer->m_rtDur;
			m_dwLastPOS = (DWORD)(m_pDemuxer->m_rtByteLength );
			m_uSourceFormat = m_pDemuxer->m_uInputStreamFormat;
			memset( &m_Statistic, 0, sizeof(m_Statistic) );
			if ( m_bStaticFile )
				m_pInputPin->SetMaxiumPos( m_pDemuxer->m_rtByteLength );
			else
				m_pInputPin->SetMaxiumPos( -1 );
			m_dSeekRatio = 0;
			m_dwPTSCount = 0;

			if ( NeedPrivateAllocator() )
			{
				CreateOutputAllocator( );
				OutputAllocatorCommit( FIFO_QUEUE_SIZE * MAX_DEMUX_TRACK_NUM*8, MEM_BLOCK_SIZE ); 
				m_pDemuxer->SetupMemAllocatorHook( m_pOutputAllocator );
			}
			if ( !m_bEnableHDAudio )
				m_pDemuxer->DisableHDAudio();
		}
			
	} else
	{
		CDeMuxOutputPin *pin; 
		int  i=1; 
		while ( ( pin = (CDeMuxOutputPin *)GetPin(i) ) != pFilterPin && pin != NULL )
			i++;
		if ( pin )
		{
			m_pDemuxer->ConnectedTrack( pin->m_uTrackIndex );

			if ( pin->m_uStreamId == 0 && pin->m_uSubId == 0 )
				m_pPassThrough = pin;
		}

	}

	return S_OK;
}

BOOL CDeMuxFilter::IsDeclineFilter( char *pFilterName )
{
	size_t len = strlen( pFilterName );
	char* p = pFilterName;

	while (*p) {*p=tolower(*p);p++;} //incase sensitive
	p = strstr( m_szDeclineDecoder, pFilterName );
	
	if ( p != NULL )
	{
		if ( p > m_szDeclineDecoder ) 
			if ( *(p-1) != ';' && *(p-1) != ',' && *(p-1) != '|' && *(p-1) != '"' )
				return FALSE;
		if ( *(p+len) != 0x0 && *(p+len) != ';' && *(p+len) != ',' && *(p+len) != '|' && *(p-1) != '"' )
				return FALSE;

		return TRUE;
	}
	return FALSE;
}

HRESULT CDeMuxFilter::DisconnectConnection( CBasePin* pFilterPin )
{
	if ( pFilterPin == m_pInputPin )
	{
		if ( NeedPrivateAllocator() )
		{
			OutputAllocatorDecommit();
			m_pOutputAllocator->Release();
			m_pOutputAllocator = NULL;
		}
	} else
	{
		CDeMuxOutputPin *pin; 
		int  i=1; 
		while ( ( pin = (CDeMuxOutputPin *)GetPin(i) ) != pFilterPin && pin != NULL )
			i++;

		if ( pin )
			m_pDemuxer->DisconnectedTrack( pin->m_uTrackIndex );
	}
	return S_OK;
}

void CDeMuxFilter::BreakConnect()
{
	 //DestroyOutputPins
	CDeMuxOutputPin *pin; 
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

STDMETHODIMP CDeMuxFilter::Stop()
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
	
	return CBaseFilter::Stop( );
}

STDMETHODIMP CDeMuxFilter::Pause()
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
					m_rtSeekStartPos = SeekStartPos( llStartTime, &m_rtSeekStartPTS );
					m_pDemuxer->ResetLastPTS( );
					m_pInputPin->SetSamplePosRange( m_rtSeekStartPos, 0 );
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


STDMETHODIMP CDeMuxFilter::Run(REFERENCE_TIME tStart)
{
	HRESULT hr;
	CAutoLock lck(&m_csFilter);
    
	//ThreadRun();
	DbgLog((LOG_TRACE, 3, TEXT("@@ Filter Run") ) );
	hr = CBaseFilter::Run( tStart );

	return hr;
}

HRESULT CDeMuxFilter::SetPositions( LONGLONG *pCurrent,  DWORD CurrentFlags,
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
			*pCurrent = m_rtSeekStart;
			DbgLog( (LOG_TRACE, 3, TEXT("Drop duplicatied seeking command, new time:%s, interval:%d  last:'%s'." ), (LPCTSTR)Disp( (CRefTime)m_rtSeekStart ),
			                   time(NULL)-m_dwLastSeekActionTime, (LPCTSTR)Disp( (CRefTime)m_rtLastSeekPos ) ));
			return hr;
		}

		if ( 0 && m_dwFileSwitchFlag == 1 && start_time == 0 )
		{
			*pCurrent = m_rtSeekStart;
			DbgLog( (LOG_TRACE, 3, TEXT("Skip first seeking command after file switching , new time:%s" ), (LPCTSTR)Disp( (CRefTime)m_rtSeekStart ) ));
			m_rtLastSeekPos = start_time;
			m_dwLastSeekActionTime = (unsigned long)time( NULL );
			return hr;
		}

		SetSeekPos( &start_time , &stop_time );  
		
		if ( ThreadExists() )
		{
			CAutoLock lck(&m_csFilter);

			AllocatorStop();
			m_pDemuxer->Stop();
			ThreadPause();

			//DeliverEndOfStream( ); //ZQ!!! If using it has a big trouble in HD-PVR playback, picture stops after rewinding but sound contiunes
			SendAnyway();
			FlushStreamsBegin( );  
			FlushStreamsEnd( );


			{
				if ( m_bSeekPos )
				{
					LONGLONG llStartTime, llStopTime;
					GetSeekPos( &llStartTime, &llStopTime );

					LONGLONG llFirstPTS = m_pDemuxer->ReadFileFirstPTS(); 
					LONGLONG llLastPTS  = m_pDemuxer->ReadFileLastPTS();
					llLastPTS  = PTS_ROUND_UP( llLastPTS, llFirstPTS );
					llFirstPTS = PTS2MT(llFirstPTS - m_pDemuxer->m_llFirstPTS);
					llLastPTS  = PTS2MT(llLastPTS  - m_pDemuxer->m_llFirstPTS);

					DbgLog( (LOG_TRACE, 2, TEXT("Demuxer seeking:%s, start:%s, first:%s, last:%s "), 
								(LPCTSTR)Disp((CRefTime)llStartTime),
								(LPCTSTR)Disp((CRefTime)(llStartTime+llFirstPTS)),
								(LPCTSTR)Disp((CRefTime)llFirstPTS), 
								(LPCTSTR)Disp((CRefTime)llLastPTS) 	));

					m_dwFileSwitchFlag = 0;
					//if ( llStartTime > llLastPTS ) 
					//{
					//	llStartTime = llLastPTS;
					//	DbgLog( (LOG_TRACE, 2, TEXT("SeekPTS is out of range, force using last PTS :%s "), (LPCTSTR)Disp((CRefTime)llStartTime) ));
					//} else
					//if ( llStartTime < llFirstPTS ) 
					//{
					//	llStartTime = llFirstPTS ;
					//	DbgLog( (LOG_TRACE, 2, TEXT("SeekPTS is out of range, force using First PTS :%s "), (LPCTSTR)Disp((CRefTime)llStartTime) ));
					//}

					llStartTime = llStartTime + llFirstPTS;
					m_rtSeekStartPos = SeekStartPos( llStartTime, &m_rtSeekStartPTS );
					m_pDemuxer->ResetLastPTS( );
					m_pInputPin->SetSamplePosRange( m_rtSeekStartPos, 0 );
					m_rtSeekStart = m_rtSeekStartPTS - llFirstPTS;;
					*pCurrent = m_rtSeekStart;
					DbgLog( (LOG_TRACE, 2, TEXT("Demuxer seeking new time:%s, seek pts:%s, first pts:%s "), (LPCTSTR)Disp((CRefTime)m_rtSeekStart),
									(LPCTSTR)Disp((CRefTime)m_rtSeekStartPTS ), (LPCTSTR)Disp((CRefTime)llFirstPTS) ));

					m_rtStartMediaTimeOffset = 0;
					m_rtPreStartMediaTimeOffset = 0;
					m_rtSeekMediaTimeOffset =m_rtSeekStart;
				}
		
				m_rtLastSeekPos = start_time;
				m_dwLastSeekActionTime = (unsigned long)time( NULL );

			}


#ifdef DUMP_ENABLE
	m_pInputPin->rewind_dump();
	rewind_dump( );
#endif

			m_pDemuxer->Run();
			AllocatorRun();
			ThreadRun();
		}
	}

	return hr;
}


void CDeMuxFilter::SetSeekPos( LONGLONG *pStart, LONGLONG* pStop )
{
	//DbgLog((LOG_TRACE, 3, TEXT("SetSeekPos" ) ));
	CAutoLock lck(&m_csFilter);
	m_SeekPos.start = *pStart;
	m_SeekPos.stop = *pStop;
	m_bSeekPos = TRUE;
}

void CDeMuxFilter::GetSeekPos( LONGLONG *pStart, LONGLONG* pStop )
{
	DbgLog((LOG_TRACE, 3, TEXT("GetSeekPos" ) ));
	CAutoLock lck(&m_csFilter);
	*pStart = m_SeekPos.start;
	*pStop  = m_SeekPos.stop;
	m_bSeekPos = FALSE;
}

LONGLONG CDeMuxFilter::SeekStartPos( LONGLONG StartTime, LONGLONG* pStartPTS )
{
	LONGLONG Pos1; 
	
	DbgLog((LOG_TRACE, 3, TEXT("------>SeekPos %s."), (LPCTSTR)Disp((CRefTime)(StartTime)) ));

	if ( m_bSeekByPTS ) //seek by PTS  
	{
		if ( StartTime > 0 )
		{
			Pos1 = m_pDemuxer->Seek( StartTime, m_pInputPin->BlockSize() );
			*pStartPTS = m_pDemuxer->GetSeekPTS();
		}
		else
		{
			Pos1 = 0;
			*pStartPTS = 0;
		}

		DbgLog(( LOG_TRACE, 3, TEXT("------> Seek by PTS %s "), (LPCTSTR)Disp(Pos1, DISP_DEC) ));
	} else //seek by postion
	{
		Pos1 = StartTime * m_dwBitsRate/UNITS;
		*pStartPTS = StartTime;
		DbgLog(( LOG_TRACE, 3, TEXT("------> Seek by Position %s "), (LPCTSTR)Disp(Pos1, DISP_DEC) ));
	}
	


	return Pos1;
}


void CDeMuxFilter::FlushStreamsBegin( )
{
	HRESULT hr;
	DbgLog((LOG_TRACE, 3, TEXT("FlushStreamsBegin" ) ));
	hr = m_pInputPin->BeginFlush();

	m_pDemuxer->DemuxerFlush( );

	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) 
	{	
		CDeMuxOutputPin *pOutputPin = m_lOutputPin.GetNext(pos);
		if ( pOutputPin->IsConnected() )
		{
			pOutputPin->DeliverBeginFlush();
		}
    }
}

void CDeMuxFilter::FlushStreamsEnd( )
{
	HRESULT hr;
	DbgLog((LOG_TRACE, 3, TEXT("FlushStreamsEnd" ) ));
	hr = m_pInputPin->EndFlush();

	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) 
	{	
		CDeMuxOutputPin *pOutputPin = m_lOutputPin.GetNext(pos);
		if ( pOutputPin->IsConnected() )
		{
			pOutputPin->DeliverEndFlush();
		}
    }
	m_pDemuxer->FlushTrack();

}

void CDeMuxFilter::DeliverEndOfStream( )
{
	DbgLog((LOG_TRACE, 3, TEXT("Filter DeliverEndOfStream" ) ));
	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) 
	{	
		CDeMuxOutputPin *pOutputPin = m_lOutputPin.GetNext(pos);
		if ( pOutputPin->IsConnected() )
		{
			pOutputPin->DeliverEndOfStream();
			pOutputPin->SendAnyway();
		}
    }
}


void CDeMuxFilter::SendAnyway( )
{
	CDeMuxOutputPin *pin; 

	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) {
        pin = m_lOutputPin.GetNext(pos);
		if ( pin->IsConnected() )
		{
			pin->SendAnyway( );
		}
    }
}

BOOL CDeMuxFilter::IsQutputPinActive( )
{
	CDeMuxOutputPin *pin; 

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

void CDeMuxFilter::AllocatorStop()
{
	m_pAllocator->Stop();
	if ( NeedPrivateAllocator() ) 
	{
		m_pOutputAllocator->Stop();
	}

	CDeMuxOutputPin *pin; 
	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) {
        pin = m_lOutputPin.GetNext(pos);
		if ( pin->IsConnected() )
		{
			if ( pin->m_pSampeGroupAlloc != NULL )
				pin->m_pSampeGroupAlloc->Stop();
		}
    }

}

void CDeMuxFilter::AllocatorRun()
{
	m_pAllocator->Run();
	if ( NeedPrivateAllocator() )   
	{
		m_pOutputAllocator->Run();
	}

	CDeMuxOutputPin *pin; 
	POSITION pos = m_lOutputPin.GetHeadPosition();
    while (pos) {
        pin = m_lOutputPin.GetNext(pos);
		if ( pin->IsConnected() )
		{
			if ( pin->m_pSampeGroupAlloc != NULL )
				pin->m_pSampeGroupAlloc->Run();
		}
    }
}

int CDeMuxFilter::AllocatorInUsedSampleNum()
{
	int Num = m_pAllocator->InUsedBufferNum();
	if ( NeedPrivateAllocator() )   
	{
		Num = m_pOutputAllocator->InUsedBufferNum();
	}
	return Num;
}


/////////////////////////////////////////////////////////////////////////////////////////////////
HRESULT CDeMuxFilter::CreateStream( BYTE uContentType, BYTE uStreamId, BYTE uSubId, BYTE uTrackIndex, 
								            CMediaType *pMT, LPCWSTR pName )
{
	CDeMuxOutputPin* pOutputPin = CreateOutputPin( uContentType, uStreamId, uSubId, uTrackIndex, pMT, pName );
	if ( pOutputPin != NULL )
	{
		pOutputPin->AddRef();
		return RegisterOutputPin( pOutputPin );
	}
	return E_OUTOFMEMORY;

}

HRESULT CDeMuxFilter::GetMediaType( BYTE uStreamId, BYTE uSubId, CMediaType **pMediaType, int nIndex )
{
	CDeMuxOutputPin *OutputPin = FindOutputPin( uStreamId, uSubId );
	if ( OutputPin == NULL )
	{
		 *pMediaType = NULL;
		 return VFW_E_BAD_KEY;
	}
	return OutputPin->GetMediaType( nIndex, pMediaType );
}

HRESULT CDeMuxFilter::AddMediaType( BYTE uTrackIndex, CMediaType *pMediaType )
{
	CDeMuxOutputPin *OutputPin = FindOutputPin( uTrackIndex );
	if ( OutputPin == NULL )
	{
		 return VFW_E_BAD_KEY;
	}
	return OutputPin->AddMediaType( pMediaType );
}

BOOL CDeMuxFilter::NeedPrivateAllocator( ) 
{ 
	return IS_TS_TYPE( m_uSourceFormat );
}

HRESULT CDeMuxFilter::SendSampleData( BYTE uTrackIndex, 
			const BYTE *pData, LONG lBytes, REFERENCE_TIME rtStart, BOOL bSync )
{
	CDeMuxOutputPin* pStreamPin;
	REFERENCE_TIME  rtStop=0;
	BOOL bDiscountinuty;
	BYTE uStreamId;



	pStreamPin = FindOutputPin( uTrackIndex );
	if ( pStreamPin == NULL )
		return E_FAIL;

	uStreamId =	pStreamPin->m_uStreamId;

	bDiscountinuty = FALSE;

	if ( bSync ) 
	{
		rtStop  = rtStart+1;  //m_dwBitsRate;

		if ( uTrackIndex == 0xff ) //passthrogh pin
		{

		} else
		if (  uStreamId )
		{
			m_rtCurrPTS = rtStart;
			m_rtCurrPOS = m_rtSampleStart; 

			double ratio;
			if ( rtStart > 0 )
			{
				ratio = (long)(m_rtCurrPOS * UNITS /rtStart);
				if ( m_dwPTSCount > 0 )
					m_dSeekRatio = ( m_dwPTSCount * m_dSeekRatio + ratio )/(m_dwPTSCount+1);
				m_dwPTSCount++;
			}

			if(  m_bSyncPTS && m_rtSeekStartPTS <= rtStart+1 )  
			{
				m_rtStartSync = rtStart;
				m_rtStartPOS  = m_rtSampleStart;
				m_bSyncPTS = FALSE;
				DbgLog((LOG_TRACE, 3, TEXT(" --------->Sample Sync Pos:%s, PTS:%s SEEK:%s, stream_id:0x%x "), 
					(LPCTSTR)Disp( m_rtStartPOS, DISP_DEC ), (LPCTSTR)Disp( (CRefTime)rtStart ), (LPCTSTR)Disp( (CRefTime)m_rtLastSeekPos ), uStreamId ));
			} 

			if(  m_bSyncPTS ) 
			{
				bSync = FALSE;
			}

			if ( m_rtLastPTS < rtStart )
			{
				m_rtLastPTS = rtStart;
				m_dwLastPOS = m_rtCurrPOS;
			}
		} else
		{
			DbgLog((LOG_TRACE, 3, TEXT(" --------->Sample passing sync, %s stream_id:%d "), 
					(LPCTSTR)Disp( m_rtStartPOS, DISP_DEC ), (LPCTSTR)Disp( (CRefTime)rtStart ), uStreamId ));
		}

		rtStart -= m_rtStartSync;

		if ( uStreamId && pStreamPin->IsVideoPin() )
			rtStart += m_rtAudioDelay;

		if ( m_dwFileSwitchSync )
		{
			m_dwFileSwitchSync = 0;
			DbgLog((LOG_TRACE, 3, TEXT(" --------->ori:'%s' new:'%s' sync:'%s'"), 
					(LPCTSTR)Disp( (CRefTime)m_rtCurrPTS ), (LPCTSTR)Disp( (CRefTime)rtStart ), 
					(LPCTSTR)Disp( (CRefTime)m_rtStartSync) ));

		}

	}

	//DbgLog((LOG_TRACE, 2, TEXT(">> %s at stream 0x%02x-0x%02x"), (LPCTSTR)Disp( (CRefTime)rtStart), pStreamPin->m_uStreamId, pStreamPin->m_uSubId  ));
	if (  pStreamPin->IsConnected() )
	{
		int flag;
		if ( !m_bPlayRewind && m_dPlayRate == 1 )
		{
			//normall play bypassing seeker
			BOOL bFirstSample = pStreamPin->FirstSample();

			if ( bFirstSample && bSync )                //NewSegment
			{
				pStreamPin->DeliverNewSegment(  0, m_rtLastPTS-m_rtStartSync, 1.0 ); 
			}

			pStreamPin->SendSample( pData, lBytes, rtStart, rtStart+1, bSync ); 

		} else
		if ( pStreamPin->IsVideoPin() )  //fast moving
		{
			if ( bSync ) pSeeker->SeekReset( pStreamPin, rtStart, m_dPlayRate ) ;

			if ( !m_bPlayRewind ) 
				flag = pSeeker->FastForward( pStreamPin, rtStart, rtStop, pData, lBytes,  bSync );
			else
				flag = pSeeker->FastRewind( pStreamPin, rtStart, rtStop, pData, lBytes,  bSync );
		}

	}

	return S_OK;

}


/////////////////////////////////////////////////////////////////////////////////////////////////



/////////////////////////////////////////////////////////////////////////////
static int ct= 0;
HRESULT CDeMuxFilter::Push( IMediaSample *pSample )
{
	HRESULT hr;
	BOOL bSync;
	if ( pSample == NULL ) return S_OK;
	long lBytes = pSample->GetActualDataLength();
	long lSize  = pSample->GetSize();
	BYTE *pData;
	if ( FAILED( hr = pSample->GetPointer(&pData) ))
		return hr;
	if ( lSize < lBytes || lBytes == 0 )
	{
		DbgLog((LOG_TRACE, 2, TEXT("ERROR: Invaild sample (Bytes:%d, buffer Size:%d), dropped."), lBytes ));
	}

	bSync = pSample->IsDiscontinuity() == S_OK;
	LONGLONG tStart, tStop;
	REFERENCE_TIME rtStart, rtStop;
	pSample->GetTime( &tStart, &tStop );
	rtStart = tStart;
	rtStop  = tStop;
	
	//Virtaul file needs adjust PTS offfset
	LONGLONG llSamplePTSStart = 0, llSamplePTSStop = 0;
	if ( pSample->GetMediaTime((REFERENCE_TIME *)&llSamplePTSStart, (REFERENCE_TIME *)&llSamplePTSStop) == S_OK )
	{
		LONGLONG llAdjust = GROUP_PTS_ADJUST( llSamplePTSStart, llSamplePTSStop );
		if ( m_llAdjust != llAdjust )
		{
			m_pDemuxer->SetPTSOffset( llAdjust );
			m_llAdjust = llAdjust;
		}
	}

	if ( bSync )
	{
		DbgLog((LOG_TRACE, 3, TEXT(" --------->First Sample (Sync)")));
		m_bEndOfStream = FALSE;
		m_bSyncPTS = TRUE;

		POSITION pos = m_lOutputPin.GetHeadPosition();
		while (pos) 
		{	
			CDeMuxOutputPin *pOutputPin = m_lOutputPin.GetNext(pos);
				pOutputPin->m_bFirstSample = TRUE;
		}
	
		//m_llPushBytes = 0;
	}

	long dwBytes, dwUsedBytes;
	PBYTE pbData;
	m_pAllocator->OpenBuffer( (CMediaSample *)pSample );
	pbData= m_pAllocator->GetOpenBufferData( &dwBytes );
	hr = m_pDemuxer->Process( pbData, dwBytes, rtStart, bSync, &dwUsedBytes );
	if ( dwBytes > 0 && dwUsedBytes == 0 || m_pDemuxer->m_bStopParser ) //ZQ.
	{
		if ( m_pDemuxer->m_bStopParser )
		{
			DbgLog(( LOG_TRACE, 3, TEXT("----->Drop samples %d, as parser stopped"), dwBytes ));
		} else
		{
			DbgLog(( LOG_TRACE, 3, TEXT("----->There are data leftover in a sample %d."), dwBytes ));
		}

		dwUsedBytes = dwBytes;
	} 


	m_pAllocator->CloseBuffer( dwUsedBytes );

	SendAnyway( );  //ZQ

	return hr;
}

#define SAFE_SAMPLE_RELEASE( s ) if( s ){ m_pAllocator->SafeRelease( s ); s = NULL;}

HRESULT CDeMuxFilter::DoThreadProcessingLoop(void) 
{
    Command com;
	BOOL bDiscontinuity = TRUE;
	DWORD dwSampleCount = 0;
	IMediaSample *pSample=NULL;
	DbgLog((LOG_TRACE, 1, TEXT("DeMuxFilter PullPump start.")));
	m_llAdjust = 0;
	
	if ( !m_pInputPin->IsConnected() )
	{
		DbgLog((LOG_TRACE, 1, TEXT("InputPin isn't connected, PullPump quit.")));
		return S_OK;
	}

	if ( GetThreadPriority(GetCurrentThread() ) != THREAD_PRIORITY_HIGHEST )
		SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_HIGHEST );

    OnThreadStartPlay();

    do {
			while ( !CheckRequest( &com ) ) 
			{
					
					HRESULT hr;
					ASSERT( pSample == NULL );
					if (  m_bPlayRewind ) 
					{
						int State;
						if ( (State = pSeeker->FastRewindState()) < 0 ) 
						{
							LONGLONG StartTime, StopTime;
							int nRewindBlockNum;
							int IncBlockNum = 0;
							if ( State == -3 )
							{
								com = GetRequest( );
								continue;
							}

							if ( State == -2 || pSeeker->m_dwRewindIFrameNum <= 1 )  
								IncBlockNum = 8;

							if ( pSeeker->m_dwRewindIFrameNum >= 2 )  
								IncBlockNum = -6;

							DbgLog((LOG_TRACE, 3, TEXT("Rewind block %d I-frame:%d"), State, pSeeker->m_dwRewindIFrameNum ));

							nRewindBlockNum = pSeeker->FastRewindStep( IncBlockNum );
							StopTime = m_pInputPin->GetCurrentPos();
							StartTime = StopTime - nRewindBlockNum * 4096;
							if ( StartTime <= 0 ) 
							{
								StartTime = 0;
								pSeeker->FirstSample();
							}
							m_pInputPin->SetCurrentPos( StartTime );
						}
					} 

					ASSERT( pSample == NULL );
					hr = m_pInputPin->PullSample( bDiscontinuity, &pSample );
					bDiscontinuity = FALSE; //first pull set bDiscontinuity only
					if ( hr == VFW_E_FILE_TOO_SHORT || hr == 0x80070026  /* ERROR_HANDLE_EOF */ || hr == 0x80070009 /*ERROR_INVALID_BLOCK */ 
						   || ( pSample && pSample->GetActualDataLength() == 0 ) ) //end of stream
					{
						DbgLog((LOG_TRACE, 2, TEXT(">>>>>(%d) hr=0x%x DataLen=%d"), 
							       dwSampleCount, hr, pSample ? pSample->GetActualDataLength() : -1 ));

						m_bEndOfData = TRUE;

						/*if ( m_pfnEventDump != NULL )
						{
							char message[128]; 
							DbgLog((LOG_TRACE, 3, TEXT(" ***********> end of data %s"), (LPCTSTR)Disp((CRefTime)m_rtCurrPTS) ));
							snprintf( message, sizeof(message), "EndOfStream pos:%s", (LPCTSTR)CDisp(m_rtCurrPTS,CDISP_DEC) );  
							//((EVENT_DUMP)m_pfnEventDump)( m_EventDumpContext, strlen(message), message ); 
							//DbgLog((LOG_TRACE, 2, TEXT("Sent Message %s"), message ));
						}*/

						if (  m_bPlayRewind ) 
						{
							pSeeker->LastSample(); 
							DbgLog((LOG_TRACE, 2, TEXT("Rewind hit the end")));
							hr = S_OK;
							SAFE_SAMPLE_RELEASE( pSample );
							continue;
						} else
						{
							SAFE_SAMPLE_RELEASE( pSample );
							m_pAllocator->Flush();
							DbgLog((LOG_TRACE, 2, TEXT("..........Thread waiting cmd after file end.") ));
							com = GetRequest( );
							m_bEndOfData = FALSE;
							DbgLog(( LOG_TRACE, 1, TEXT("..........Thread get cmd:%s, runing..."), CmdName(com) ));
						}
						

					} else
					if ( hr == VFW_E_NOT_RUNNING )
					{
						DbgLog((LOG_TRACE, 2, TEXT("..........Thread waiting cmd after Allocator stopped")));
						SAFE_SAMPLE_RELEASE( pSample );
						m_pAllocator->Flush();
						com = GetRequest( );
						DbgLog(( LOG_TRACE, 1, TEXT("..........Thread get cmd:%s, runing..."), CmdName(com) ));
					}else
					if ( hr == S_OK )
					{
						pSample->GetTime((REFERENCE_TIME *)&m_rtSampleStart, (REFERENCE_TIME *)&m_rtSampleStop);
						m_rtSampleStart /= UNITS;
						m_rtSampleStop  /= UNITS;

						hr = Push( pSample );
					} 

					if ( pSample != NULL )
						dwSampleCount++;
	
					SAFE_SAMPLE_RELEASE( pSample );


					// all paths release the sample
			}

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

	DeliverEndOfStream( );  
	SAFE_SAMPLE_RELEASE( pSample );
	DbgLog((LOG_ERROR, 1, TEXT("DeMuxFilter PullPump stopped, total sample pulled %d."), dwSampleCount));
	return S_FALSE;
}

void CDeMuxFilter::LoadCfgFromRegistry( )
{
		//HKEY  rootKey = HKEY_LOCAL_MACHINE;  //if I use this, compilier has a stack trouble
		HKEY  root_key = HKEY_LOCAL_MACHINE;
		DWORD holder=0;
		HKEY  my_key;
		DWORD readType;
		DWORD hsize = sizeof(holder);
		if (RegOpenKeyEx(root_key, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDeMux", 0, KEY_QUERY_VALUE, &my_key) == ERROR_SUCCESS)
		{
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
					m_pDemuxer->m_dwAudioTypeEnableMask &= ~AC3_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("AC3 Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "EAC3Disable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pDemuxer->m_dwAudioTypeEnableMask &= ~EAC3_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("EAC3 Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "TrueHDDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pDemuxer->m_dwAudioTypeEnableMask &= ~TAC3_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("TrueHD Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "DTSDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pDemuxer->m_dwAudioTypeEnableMask &= ~DTS_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("DTS Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "DTSMasterDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pDemuxer->m_dwAudioTypeEnableMask &= ~MDTS_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("DTS-Master Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "AACDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pDemuxer->m_dwAudioTypeEnableMask &= ~AAC_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("AAC Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "AACHEDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pDemuxer->m_dwAudioTypeEnableMask &= ~AACH_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("AAC-HE Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "LPCMDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pDemuxer->m_dwAudioTypeEnableMask &= ~LPCM_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("LPCM Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "MPEGDisable", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==1) 
				{
					m_pDemuxer->m_dwAudioTypeEnableMask &= ~MPEG_ENABLE;
					DbgLog(( LOG_TRACE, 3, TEXT("MPEG Disable set %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "SeekByPTS", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder==0) 
				{
					m_bSeekByPTS = 0;
					DbgLog(( LOG_TRACE, 3, TEXT("SeekByPTS %d"), holder ));
				} 
			} 
			if (RegQueryValueEx(my_key, "EnableHDAudio", 0, &readType, (LPBYTE)&holder, &hsize) == ERROR_SUCCESS)
			{
				if ( (int)holder== 1) 
				{
					m_bEnableHDAudio = 1;
					DbgLog(( LOG_TRACE, 3, TEXT("SeekByPTS %d"), holder ));
				} 
			} 

			hsize = sizeof(m_szDeclineDecoder);
			if (RegQueryValueEx(my_key, "DeclineDecoder", 0, &readType, (LPBYTE)&m_szDeclineDecoder, &hsize) == ERROR_SUCCESS)
			{
				DbgLog(( LOG_TRACE, 3, TEXT("DeclineDecoder %d"), m_szDeclineDecoder ));
				char *p = m_szDeclineDecoder;
				while (*p) { *p = tolower(*p); p++; }; //incase sensitive
			} 
			
			
			RegCloseKey(my_key);
		}
}

void CDeMuxFilter::dump_data( char* pData, int dwBytes )
{
	if ( fd == NULL ) return;
	if ( dwBytes > 0)	fwrite( pData, 1, dwBytes, fd );
}

void CDeMuxFilter::dump_sample( IMediaSample *pSample )
{
	BYTE* pBuffer;
	size_t   Length;
	if ( fd == NULL ) return;
	if ( FAILED(  pSample->GetPointer( &pBuffer ) ) ) return;
	Length = pSample->GetActualDataLength( );
	if ( Length > 0)	
	{
		offset += fwrite( pBuffer,1, Length, fd );
	}

}


void CDeMuxFilter::open_dump( )
{
	char fname[MAX_PATH];
	char path[MAX_PATH]={0};
	size_t len;
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
	DbgLog((LOG_TRACE, 2, TEXT("DeMuxFilter dump file %s  fd=0x%x."), fname, fd ) );
}
void CDeMuxFilter::close_dump( )
{
	if ( fd != NULL )
		fclose( fd );
	fd = NULL;
}

void CDeMuxFilter::rewind_dump( )
{
	if ( fd != NULL )
		fseek( fd, 0, SEEK_SET );
	offset = 0 ;
	DbgLog((LOG_TRACE, 2, TEXT("CDeMuxFilter dump file rewinded.")) );
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

static bool flog_enabled=false;

void _flog( int type, int level, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	 
	if ( !flog_enabled ) return;
	if ( level > DEBUG_LEVEL ) return;

	fp = fopen( "MpegDemux.log", "a" ); 
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
	FILE* fp = fopen( "MPEGDEMUX_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
		_flog_setup( "MpegDemux.log", 1 ); //setup native log
		_flog( 0, 0, "----------> StartMpegDeMux ZQ. ---------->" );
	}

}

