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

#pragma warning(disable : 4996)

#ifndef _WIN64
  #define _USE_32BIT_TIME_T
#endif

#include <streams.h>

#include <bdaiface.h>
#include <bdatypes.h>

#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <io.h>
#include <fcntl.h>
#include <share.h>
#include <sys/types.h>
#include <sys/stat.h>
#include "../../include/isharedasync.h"
#include "StreamData.h"

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
#include "..\..\..\third_party\Microsoft\FileSource\strconv.h"
#include "DebugLog.h"
#include "PTSParser.h"

static void _display_av_inf( TRACKS *pTracks );
#define MAX_DEMUX_TRACK_NUM  20



int CPTSParser::PTSDataDumper( void* pContext, void* pData, int nSize )
{
	CPTSParser* pCPTSParser = (CPTSParser*)pContext;
	DEMUXER *pDemuxer   = (DEMUXER *)pCPTSParser->m_pDemuxer;
	TRACK *pTrack = (TRACK *)pData;
	PES *pPES = &pTrack->es_elmnt->pes;
	ULONGLONG pts = 0;
	if ( pTrack->av_elmnt->content_type == VIDEO_DATA || pTrack->av_elmnt->content_type == AUDIO_DATA )
	{
		if ( pPES->has_pts )
			pts = pPES->pts;

		if ( pts )
		{
			pCPTSParser->m_llUpdatedPTS = pts;
			pCPTSParser->m_nUpdatedPTSTrack = pTrack->channel_index;
		}
	}
	return 1;
}

int CPTSParser::AVInfMessageDumper( void* pContext, void* pData, int nSize )
{
	CPTSParser* pCPTSParser = (CPTSParser*)pContext;
	DEMUXER *pDemuxer   = (DEMUXER *)pCPTSParser->m_pDemuxer;
	MESSAGE_DATA *message = (MESSAGE_DATA*)pData;
	if ( !strcmp( message->title, "STATUS" ) )
	{
		
		if ( strstr( (char*)message->message, "STREAM START" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

			ResetBlockBuffer( pDemuxer, slot_index );
			if ( pCPTSParser->m_nState == 0 )
				pCPTSParser->m_nState = 1;

		} else
		if ( strstr( (char*)message->message, "STREAM FAILED" ) )
		{
			int slot_index = 0;
			const char *p;
			SageLog(( _LOG_TRACE, 3, TEXT("****** PARSER STATUS: %s ******"), message->message ));
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
			pCPTSParser->m_nState = -1;

		} else
		if ( strstr( (char*)message->message, "STREAM REBUILD" ) )
		{
			int slot_index = 0;
			const char *p;
			SageLog(( _LOG_TRACE, 3, TEXT("****** PARSER STATUS: %s ******"), message->message ));
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
			QueueZeroDemux( (DEMUXER *)pCPTSParser->m_pDemuxer );
			pCPTSParser->m_bStreamRebuild = TRUE;

		} else
		if ( strstr( (char*)message->message, "STREAM READY" ) )
		{
			int slot_index = 0;
			const char *p;
			SageLog(( _LOG_TRACE, 3, TEXT("****** PARSER STATUS: %s ******"), message->message ));
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
			if ( pCPTSParser->m_nState == 1 ) 
			{
				TRACKS *tracks;
				ULONGLONG pos = DemuxUsedBytes( pDemuxer );
				tracks = GetTracks( pDemuxer, slot_index );

				CheckTracksAttr( tracks , LanguageCode((unsigned char*)"eng") );
				TracksIndexing( tracks );
				_display_av_inf( tracks );

				if ( pCPTSParser->m_nState < 2 )
					SageLog(( _LOG_ERROR, 3, TEXT("**** Find AVINF(pos:%d) ****" ), (unsigned long)pos ));

				pCPTSParser->m_nState = 2;
			}
		} else
		if ( strstr( (char*)message->message, "STREAM END" ) )
		{
			int slot_index = 0;
			const char *p;
			SageLog(( _LOG_TRACE, 3, TEXT("****** PARSER STATUS: %s ******"), message->message ));
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

		} 
	}
	return 1;
}

HRESULT CPTSParser::Init( CAsyncStream *pStream )
{
	HRESULT hr;
	DWORD StartTick = 0;  //for checking timeout of data
	DbgLog( (LOG_TRACE, 2, TEXT("MPEG2 PTSParser Initilization Ver 3.1.0")  ) );  

	DWORD dwTotalInitLen = 6*12*524288; 

    /*  Just read 32K and look for interesting stuff */
	const DWORD dwIncLen = 32*1024;  
	DWORD dwBytes;
    PBYTE pbData = new BYTE[dwIncLen];
    if (pbData == NULL) {
		return E_FAIL;
    }
	
	memset( pbData, 0, dwIncLen );
    hr = pStream->Read(pbData, dwIncLen, 0, &dwBytes );
    if (S_OK != hr) {
        delete [] pbData;
		return hr;
    }
	dwTotalInitLen = dwTotalInitLen > dwIncLen ? dwTotalInitLen - dwIncLen : 0;

	//detect input format first
	StartTick = 0; 
	while (  ( m_uInputStreamFormat == 0 ) && dwTotalInitLen )
	{
		m_uInputStreamFormat = CheckFormat( pbData, dwIncLen );
		if ( m_uInputStreamFormat )
			break;

		hr = pStream->Read(pbData, dwIncLen, 0, &dwBytes );
		if ( S_OK != hr || dwBytes == 0 )
			break;
	    
		dwTotalInitLen = dwTotalInitLen > dwBytes ? dwTotalInitLen - dwBytes : 0;
	}

	if ( m_uInputStreamFormat == 0 )
	{
		delete [] pbData;
		DbgLog((LOG_TRACE, 2, TEXT("MPEG2 DeMux Init failed (known format)")));
		return E_FAIL;
	}

	m_llFirstPTS = 0;
	pStream->SetPointer( 0 );

	//create a demuxer
	if ( m_pDemuxer ) 
	{
		ReleaseDemuxer( (DEMUXER*)m_pDemuxer );
	}
	TUNE tune={0};
	tune.channel = 1;
	m_pDemuxer = CreateDemuxer( m_uInputStreamFormat, MAX_DEMUX_TRACK_NUM, ES_BUFFER_SIZE );
	if ( !OpenStreamSource( (DEMUXER*)m_pDemuxer, m_uInputStreamFormat, &tune ) )
	{
		delete [] pbData;
		DbgLog((LOG_TRACE, 2, TEXT("MPEG2 DeMux Open Stream source Failed")));
		return E_FAIL;
	}

	//setup dumper
	SetupMessageDumper( (DEMUXER*)m_pDemuxer, (DUMP)AVInfMessageDumper, this );
	//SetupPESDump( (DEMUXER*)m_pDemuxer, PTSDataDumper, this );
	m_nState = 0;
	if ( IS_TS_TYPE( m_uInputStreamFormat ) )
	{
		DisableDemuxTSPSI( (DEMUXER*)m_pDemuxer );
		DisablePTSFix( (DEMUXER*)m_pDemuxer );
		((DEMUXER*)m_pDemuxer)->ts_parser->empty_sub_stream_threshold = dwTotalInitLen;
	}
	else
	if ( IS_PS_TYPE( m_uInputStreamFormat ) )
	{
	}

	long  CheckBytes = dwTotalInitLen;
	StartTick = 0; 
	m_bStreamRebuild = FALSE;
	ZeroDemuxer( );
	while ( CheckBytes > 0 )
	{
		int nUsedBytes, nReadBytes;
		nUsedBytes = PushData( pbData, dwIncLen );
		if ( nUsedBytes == 0 )
			nUsedBytes = dwIncLen;
		ASSERT( dwIncLen >= nUsedBytes );

		//AVInf ready
		if ( m_nState == 2 )
			break;
		//Failed
		if ( m_nState == -1 )
			break;

		if (  m_bStreamRebuild ) 
		{
			CheckBytes = dwTotalInitLen;
			pStream->SetPointer( 0 ); 
			m_bStreamRebuild = FALSE;
		}

		nReadBytes = dwIncLen - nUsedBytes;
		if ( nReadBytes > 0 )
		{
			memcpy( pbData,  pbData+nUsedBytes, nReadBytes );
			hr = pStream->Read( pbData+nReadBytes, nUsedBytes,  0, &dwBytes );
			CheckBytes -= (DWORD)nUsedBytes;
		} else
		{
			hr = pStream->Read( pbData, dwIncLen, 0, &dwBytes );
			CheckBytes -= dwIncLen;
		}
		if ( S_OK != hr ) 
			break;
	};

	delete pbData;	
	LockDemuxTSPPmt( (DEMUXER*)m_pDemuxer );
	//select PTS track
	{
		m_nPTSTrackIndex = 0;    

		WORD nVideoTrackIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->main_video_index;
		WORD nAudioTrackIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->main_audio_index;
		if ( nVideoTrackIndex != 0xffff )
			m_nPTSTrackIndex = nVideoTrackIndex;
		else
		if ( nAudioTrackIndex != 0xffff )
			m_nPTSTrackIndex = nAudioTrackIndex;

	}

	
	//reset Demuxer
	m_llRecentBytePos = 0;
	m_rtRecentTime = 0;
	pStream->SetPointer( 0 );
	return S_OK;
}

void CPTSParser::ResetDemuxerPTS( )
{
	m_llFirstPTS = m_llLastPTS = 0;
}

void CPTSParser::ResetTracksPTS( )
{
	m_llFirstPTS = m_llLastPTS = 0;
}

void CPTSParser::EnableFastPTSParser()
{
	SetupPESDump( (DEMUXER*)m_pDemuxer, PTSDataDumper, this );
}

void CPTSParser::DisableFastPTSParser()
{
	SetupPESDump( (DEMUXER*)m_pDemuxer, NULL, NULL );
}

void CPTSParser::ZeroDemuxer( )
{
	//m_nRemainBytes = 0;
	//m_nExpectedBytes = 0;	
	SetZeroDemux( (DEMUXER*)m_pDemuxer );
}

void CPTSParser::DemuxerReset( )
{
	//m_nRemainBytes = 0;
	//m_nExpectedBytes = 0;	
	ResetDemuxer( (DEMUXER*)m_pDemuxer, 0 );
}

LONGLONG  CPTSParser::ReadFileSize( CAsyncStream *pStream )
{
	return pStream->Size();
}

LONGLONG  CPTSParser::ReadFileFirstPTS( CAsyncStream *pStream )
{
	int i;
	const DWORD dwIncLen = 32*1024;  
    
	PBYTE pbData = new BYTE[dwIncLen];

    if (pbData == NULL) 
		return 0;
	LONGLONG rtByteLength;
	rtByteLength = pStream->Size();
	i = 0;
	while (  i<200 )
	{
		LONGLONG llPos, llPTS;

		llPos =  (REFERENCE_TIME)(i*dwIncLen);
		if ( llPos>= rtByteLength  ) break;
		
		llPTS = ParsePTS( pStream, pbData, &llPos, dwIncLen );
		if (  llPTS == -1 )
		{
			DbgLog((LOG_TRACE, 2, TEXT("ERROR: First PTS not found..") ));
			break;
		} 

		//last PTS is found
		if ( m_llUpdatedPTS )
			break;
		i++;
	}

	delete pbData;
	return m_llUpdatedPTS;
}

LONGLONG  CPTSParser::ReadFileLastPTS( CAsyncStream *pStream )
{
	int i;
	const DWORD dwIncLen = 32*1024;  
    
	PBYTE pbData = new BYTE[dwIncLen];

    if (pbData == NULL) 
		return 0;

	LONGLONG rtByteLength;
	rtByteLength = pStream->Size();

	i = 1;
	while (  i<1000 )
	{
		LONGLONG llPos, llPTS;

		llPos =  rtByteLength - (REFERENCE_TIME)(i*dwIncLen);
		if ( llPos < 0 ) break;
		
		llPTS = ParsePTS( pStream, pbData, &llPos, dwIncLen );
		if (  llPTS == -1 )
		{
			DbgLog((LOG_TRACE, 2, TEXT("ERROR: Last PTS not found..") ));
			break;
		} 

		if ( m_llUpdatedPTS )
			break;

		i++;
	}

	delete pbData;
	return m_llUpdatedPTS;

}


LONGLONG CPTSParser::ParsePTS( CAsyncStream *pStream, BYTE* pBuffer, LONGLONG* llPos, DWORD dwBytes )
{
	HRESULT hr;
	LONGLONG lStart;
	DWORD dwActual;
	int Bytes, Size, BlockSize;
	char* pData;


	if ( dwBytes == 0 )
	{
		DbgLog((LOG_TRACE, 2, TEXT("ERROR: Searching range is zero") ));
		return 0;
	}

	if ( IS_TS_TYPE( m_uInputStreamFormat )  )
		BlockSize = TS_PACKET_LENGTH * 20;
	else
		BlockSize = 1024*2;

	lStart = *llPos;
	if ( lStart < 0 ) lStart = 0;
	hr = pStream->SetPointer( lStart );
	if ( S_OK != hr )
	{
		DbgLog((LOG_TRACE, 2, TEXT("ERROR: SetPointer for reading data PTS hr=0x%x, pos:%s"), hr, (LPCTSTR)Disp(lStart, CDISP_DEC) ));
		return -1;
	}

	if ( S_OK == hr )
		hr = pStream->Read( pBuffer, dwBytes, 0, &dwActual );
	if ( S_OK != hr )
	{
		DbgLog((LOG_TRACE, 2, TEXT("ERROR: Read data for parsing PTS hr=0x%x, pos:%s, bytes:%d"), 
				     hr,  (LPCTSTR)Disp(lStart, CDISP_DEC), dwBytes ));

		LONGLONG llBytes = 0;
		llBytes = pStream->Size();
		DbgLog((LOG_TRACE, 2, TEXT("ERROR: File Availabe Bytes: %s, bytes:%d, hr=0x%x"), (LPCTSTR)Disp(llBytes, CDISP_DEC), dwBytes, hr ));
		return -1;
	}

	ZeroDemuxer( );
	EnableFastPTSParser();
	m_llUpdatedPTS = 0;
	pData = (char*)pBuffer;
	Bytes = dwBytes;
	while ( Bytes > 16 )
	{
		int nUsedBytes;
		Size = min( Bytes, BlockSize );

		nUsedBytes = PushData( (unsigned char*)pData, Size );
		if ( nUsedBytes == 0 ) nUsedBytes = Size;

		if ( m_llUpdatedPTS )
			break;

		pData += nUsedBytes;
		Bytes -= nUsedBytes;
	}

	DisableFastPTSParser();
	if ( m_llUpdatedPTS )
	{
		*llPos += dwBytes-Bytes;
		m_llUpdatedPTS = PTS_ROUND_UP( m_llUpdatedPTS, (ULONGLONG)m_llFirstPTS ); 
		return  PTS2MT( m_llUpdatedPTS - m_llFirstPTS  )+1;
	}

	return 0;
}

int CPTSParser::PushData( unsigned char* pbData, int nBytes )
{
	int nExpectedBytes;
	return PushDemuxStreamData( (DEMUXER*)m_pDemuxer, pbData, nBytes, &nExpectedBytes );
}

static void _display_av_inf( TRACKS *pTracks )
{
	char avinf[1024];
	TracksInfo( pTracks, avinf, sizeof(avinf) );
	SageLog(( _LOG_TRACE, 3, TEXT("INPUT-AVINF|%s"), avinf ));
}
