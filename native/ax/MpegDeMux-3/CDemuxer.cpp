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
#include <string.h>
#include <dvdmedia.h>
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
#include "TSInfoParser.h"
#include "CDemuxer.h"

#include "Seeker.h"
#include "DeMuxInPin.h"
#include "DeMuxOutPin.h"
#include "DeMuxFilter.h"
#include "../../../third_party/Microsoft/FileSource/strconv.h"



#define	WAVE_FORMAT_DOLBY_AC3	0x2000	 //WAVE_FORMAT_DVM	ZQ(?)
#define	WAVE_FORMAT_DTS			0x2001	 //WAVE_FORMAT_DVM	ZQ(?)
#define	WAVE_FORMAT_MPEG		0x0050   /* MPEG Layer 1,2 */	
#define	WAVE_FORMAT_MPEG_ADTS_AAC 0x1600
#define WAVE_FORMAT_MPEG_LOAS	  0x1602
#define WAVE_FORMAT_TRUE_HD		0x4341

static const GUID  CLSID_MpegH264VC1 =
{ 0x31435641, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71} };

//static const GUID  CLSID_DTS_AUDIO=
//{ 0xAD08B891, 0xEA90, 0x40DC, {0x93, 0xEE, 0x73, 0xC7, 0xC4, 0x61, 0x92, 0xE3} };

static const GUID  CLSID_DTS_AUDIO=
{ 0xe06d8033, 0xdb46, 0x11cf,  {0xb4, 0xd1, 0x00, 0x80, 0x05f, 0x6c, 0xbb, 0xea } };

static const GUID  WAVE_FORMAT_DVD_DTS=
{ 0x00002001, 0x0000,0x0010, { 0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71} };

//static const GUID  CLSID_DTS_MASTER_AUDIO=
//{ 0x00002001, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71} };

static const GUID  CLSID_DTS_MASTER_AUDIO=
{ 0xa2e58eb7, 0xfa9, 0x48bb, { 0xa4, 0x0c, 0xfa, 0xe, 0x15, 0x6d, 0x6, 0x45 } };

static const GUID  MEDIASUBTYPE_DOLBY_DDPLUS=
{ 0xa7fb87af, 0x2d02, 0x42fb, {0xa4, 0xd4, 0x5, 0xcd, 0x93, 0x84, 0x3b, 0xdd } };

static const GUID  MEDIASUBTYPE_DTS_HD = 
{ 0xa2e58eb7, 0xfa9, 0x48bb,  {0xa4, 0xc, 0xfa, 0xe, 0x15, 0x6d, 0x6, 0x45 } };

static const GUID  MEDIASUBTYPE_DOLBY_TRUEHD=
{ 0xeb27cec4, 0x163e, 0x4ca3, {0x8b, 0x74, 0x8e, 0x25, 0xf9, 0x1b, 0x51, 0x7e } };

static const GUID  MEDIASUBTYPE_DOLBY_TRUEHD2=
{ 0x4288B843, 0x610B, 0x4E15, { 0xA5, 0x3B, 0x43, 0x00, 0x7F, 0xCF, 0xF6, 0x14} };

static const GUID  FORMAT_DIVX_VIDEO =
{ 0x05589f80, 0xC356, 0x11CE, {0xbf, 0x01, 0x00, 0xaa, 0x00, 0x55, 0x59, 0x5a} };

static const GUID  CLSID_DIVX=
{ 0x58564944, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID  FORMAT_MPEG4_VIDEO =
{ 0x05589f80, 0xc356, 0x11ce, {0xbf, 0x01, 0x00, 0xaa, 0x00, 0x55, 0x59, 0x5a } };

static const GUID  CLSID_MP4V =
{ 0x6d703476, 0x0000, 0x0010, {0xb5, 0x37, 0x98, 0x08, 0x00, 0x00, 0x00, 0x00} };

static const GUID  CLSID_MpegVC1 =
{ 0x629B40AD, 0xAD74, 0x4EF4, {0xA9, 0x85, 0xF0, 0xC8, 0xD9, 0x2E, 0x5E, 0xCA } };

static const GUID  CLSID_MpegWVC1 =
{ 0x31435657, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID CLSID_MpegH264 =
{ 0x8D2D71CB, 0x243F, 0x45E3, {0xB2, 0xD8, 0x5F, 0xD7, 0x96, 0x7E, 0xC0, 0x9B } };

static const GUID  CLSID_MpegH264A =
{ 0x6F29D2AD, 0xE130, 0x45AA, {0xb4, 0x2f, 0xf6, 0x23, 0xad, 0x35, 0x4a, 0x90} };

static const GUID MEDIASUBTYPE_AAC =
{ 0x000000ff, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_LATM_AAC =
{ 0x000001ff, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_MPEG_ADTS_AAC = 
{ 0x00001600, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_MPEG_LATM_AAC = 
{ 0x00001602, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_WVC1 = 
{ 0x31435657, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID KSDATAFORMAT_SUBTYPE_PCM = 
{ 0x00000001, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };


//DEFINE_GUID(MEDIASUBTYPE_wvc1, 0x31637677, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
//DEFINE_GUID(MEDIASUBTYPE_NOKIA_MPEG_ADTS_AAC, 0x00001608, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
//DEFINE_GUID(MEDIASUBTYPE_NOKIA_MPEG_RAW_AAC,  0x00001609, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
//DEFINE_GUID(MEDIASUBTYPE_MPEG_RAW_AAC,  0x00001601, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
//DEFINE_GUID(MEDIASUBTYPE_VODAFONE_MPEG_ADTS_AAC, 0x0000160A, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
//DEFINE_GUID(MEDIASUBTYPE_VODAFONE_MPEG_RAW_AAC,  0x0000160B, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);


///////////////////////////////////////////////////////////////////////////////////////////

static void _display_av_inf( TRACKS *pTracks );
char* time_stamp_ss( ULONGLONG llTime );
int CDemuxer::ESDataDumper( void* pContext, void* pData, int nSize )
{
	CDemuxer* pCDemuxer = (CDemuxer*)pContext;
	DEMUXER *pDemuxer   = (DEMUXER *)pCDemuxer->m_pDemuxer;
	BLOCK_BUFFER *pBlockBuffer = (BLOCK_BUFFER*)pData;
	PES *pPES = &pBlockBuffer->pes;
	TRACK *pTrack;
	ULONGLONG llPts = 0;
	BOOL bSync = FALSE;
	if ( pBlockBuffer->slot_index != 0 ) return 0;

	pTrack = &pDemuxer->tracks[0]->track[pBlockBuffer->track_index];
	if ( pTrack->av_elmnt->content_type == VIDEO_DATA || pTrack->av_elmnt->content_type == AUDIO_DATA )
	{
		if ( pPES->has_pts )
		{
			llPts = pPES->pts;
			if ( llPts == 0 ) llPts = 1;								  //ZQ.	
			pCDemuxer->SetStreamPTS( pTrack->channel_index, &llPts, 1 );
			bSync = TRUE;
		}
	}

	
	if ( bSync  )
	{
		LONGLONG llOffsetPts = pCDemuxer->GetStreamLastPTS( pTrack->channel_index ) ;
		if ( llOffsetPts >=0  )
			llPts = PTS2MT(llOffsetPts);
		else
		{
			//return 1;
			//llPts = 1;
			bSync = 0;
		}
	}

	BYTE* pbData  = pBlockBuffer->data_start;
	DWORD dwSize = pBlockBuffer->data_size;
	int media_type = UNKNOWN_MEDIA;
	if ( pDemuxer->ps_parser && pDemuxer->ps_parser->sagetv_private_data.sage_info_embedded )
		media_type = SAGETV_TV_RECORDING;
	else
	if ( pDemuxer->ts_parser && pDemuxer->ts_parser->media_type == MEDIA_TYPE_DVD )
		media_type = BLUERAY_DVD_MEDIA;

	if ( pCDemuxer->m_nState < 2 )
		return 0;

	//extract DTS CORE (drop extension)
	if ( media_type == BLUERAY_DVD_MEDIA && pCDemuxer->m_bHDAudioFlag )
	{
		if (pDemuxer->tracks[0]->track[pBlockBuffer->track_index].av_elmnt->format_fourcc == SAGE_FOURCC("DTSH") ||
		    pDemuxer->tracks[0]->track[pBlockBuffer->track_index].av_elmnt->format_fourcc == SAGE_FOURCC("DTSM") )
		{
			if ( !pCDemuxer->DTSCoreHeaderCheck( pbData, dwSize ) )
				return 0;
		}
	}

	//disable unconnected track parsing
	CDeMuxOutputPin *pStreamPin = pCDemuxer->m_pFilter->FindOutputPin((BYTE)pTrack->channel_index);
	//if ( pStreamPin == NULL || !pStreamPin->IsConnected() )
	if ( pStreamPin == NULL  || !pCDemuxer->m_pTrackInf[pTrack->channel_index].connected )
	{
		pDemuxer->tracks[0]->track[pBlockBuffer->track_index].command = STOP_FILL_DATA;
		SageLog(( _LOG_TRACE, 3, TEXT("Disable track %d, it's not connected "), pBlockBuffer->track_index));
		return 0;
	}
	assert( pStreamPin->IsConnected() );
	assert( pStreamPin->m_uTrackIndex == pTrack->channel_index );

	//drop audio/subtitle util video is ready
	if ( pStreamPin->OutSampleCount() == 0 )
	{
		if ( pCDemuxer->m_pTrackInf[pTrack->channel_index].stream_type != 1 && 
			 (pCDemuxer->m_dwConnectedType & VIDEO_CONNECTED) &&
			!(pCDemuxer->m_dwConnectedType & VIDEO_PRESENT) )
			return 0;
	}

	if ( pStreamPin->OutSampleCount() == 0 )
	{
		int offset = pPES->header_length + pDemuxer->tracks[0]->track[pBlockBuffer->track_index].es_elmnt->private_bytes;
		int start = AnylyzeAVElement( pDemuxer->tracks[0]->track[pBlockBuffer->track_index].av_elmnt, 
						              pDemuxer->tracks[0]->track[pBlockBuffer->track_index].es_elmnt,
									  pDemuxer->tracks[0]->track[pBlockBuffer->track_index].ts_elmnt, pbData-offset, dwSize+offset, media_type );
		if ( !start ) 
			return 0;
		//it's a fix for R4000 recording that I frame (group header) has no PTS and no group start flag;
		//I use the latest PTS to go with I-frame.
		if ( !bSync )
		{
			LONGLONG llOffsetPts = pCDemuxer->GetStreamLastPTS( pTrack->channel_index ) ;
			if ( llOffsetPts >=0  )
				llPts = PTS2MT(llOffsetPts);
			else
				llPts = 0;
			bSync = 1;
		}

		switch ( pCDemuxer->m_pTrackInf[(BYTE)pTrack->channel_index].stream_type ) {
		case 1:	pCDemuxer->m_dwConnectedType |= VIDEO_PRESENT; break;
		case 2:	pCDemuxer->m_dwConnectedType |= AUDIO_PRESENT; break;   
		case 3: pCDemuxer->m_dwConnectedType |= SUBTITLE_PRESENT; break;   
		}
		
	}


	HRESULT hr = pCDemuxer->m_pFilter->SendSampleData( (BYTE)pTrack->channel_index, pbData, dwSize, llPts, bSync );
	if ( hr == S_OK )
	{
		pCDemuxer->m_pTrackInf[pTrack->channel_index].data_frames++;
	}

	return 1;
}

int CDemuxer::AVInfMessageDumper( void* pContext, void* pData, int nSize )
{
	CDemuxer* pCDemuxer = (CDemuxer*)pContext;
	DEMUXER *pDemuxer   = (DEMUXER *)pCDemuxer->m_pDemuxer;
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
			if ( pCDemuxer->m_nState == 0 )
				pCDemuxer->m_nState = 1;

		} else
		if ( strstr( (char*)message->message, "STREAM FAILED" ) )
		{
			int slot_index = 0;
			const char *p;
			SageLog(( _LOG_TRACE, 3, TEXT("****** PARSER STATUS: %s ******"), message->message ));
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
			pCDemuxer->m_nState = -1;

		} else
		if ( strstr( (char*)message->message, "STREAM REBUILD" ) )
		{
			int slot_index = 0;
			const char *p;
			SageLog(( _LOG_TRACE, 3, TEXT("****** PARSER STATUS: %s ******"), message->message ));
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
			QueueZeroDemux( (DEMUXER *)pCDemuxer->m_pDemuxer );
			pCDemuxer->m_bStreamRebuild = TRUE;

		} else
		if ( strstr( (char*)message->message, "STREAM READY" ) )
		{
			int slot_index = 0;
			const char *p;
			SageLog(( _LOG_TRACE, 3, TEXT("****** PARSER STATUS: %s ******"), message->message ));
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
			if ( pCDemuxer->m_nState == 1 ) 
			{
				TRACKS *tracks;
				ULONGLONG pos = DemuxUsedBytes( pDemuxer );
				tracks = GetTracks( pDemuxer, slot_index );

				CheckTracksAttr( tracks , LanguageCode((unsigned char*)"eng") );
				TracksIndexing( tracks );
				_display_av_inf( tracks );

				if ( pCDemuxer->m_nState < 2 )
					SageLog(( _LOG_ERROR, 3, TEXT("**** Find AVINF(pos:%d) ****" ), (unsigned long)pos ));

				pCDemuxer->m_nState = 2;
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

int CDemuxer::PTSDataDumper( void* pContext, void* pData, int nSize )
{
	CDemuxer* pCDemuxer = (CDemuxer*)pContext;
	DEMUXER *pDemuxer   = (DEMUXER *)pCDemuxer->m_pDemuxer;
	TRACK *pTrack = (TRACK *)pData;
	PES *pPES = &pTrack->es_elmnt->pes;
	ULONGLONG pts = 0;
	if ( pTrack->av_elmnt->content_type == VIDEO_DATA || pTrack->av_elmnt->content_type == AUDIO_DATA )
	{
		if ( pPES->has_pts )
			pts = pPES->pts;

		pCDemuxer->m_llUpdatedPTS = pts;
	}
	return 1;
}

int CDemuxer::ATSDataDumper( void* pContext, void* pData, int nSize )
{
	CDemuxer* pCDemuxer = (CDemuxer*)pContext;
	DEMUXER *pDemuxer   = (DEMUXER *)pCDemuxer->m_pDemuxer;
	ATS_TIME* ats_time = (ATS_TIME*)pData;
	ASSERT( nSize == sizeof(ATS_TIME) );
	if ( ats_time->type == 1 )
	{
		ULONGLONG pts = MT2PTS((ULONGLONG)10000*ats_time->t.ats);
		pCDemuxer->m_llUpdatedPTS = pts;
	}
	return 1;
}

void* CDemuxer::MemAllocatorHook( void* pContext, void* pMemory, int nSize, int nCmd )
{
	CDemuxer* pCDemuxer = (CDemuxer*)pContext;
	ASSERT( pCDemuxer->m_pAllocator );
	if ( nCmd == 1 && nSize > 0 )
	{
		unsigned char* pbData = NULL;
		IMediaSample* pSample = NULL ;
		HRESULT hr = pCDemuxer->m_pAllocator->GetBuffer0( &pSample, NULL, NULL, 0 );
		if ( hr == S_OK )
		{
			ASSERT( pSample->GetSize() >= nSize );
			pSample->SetActualDataLength( nSize );
			pSample->GetPointer( &pbData );
		} else
		{
			if ( pSample != NULL )
				pSample->Release();
		}
		return pbData;
	}
	if ( nCmd == 0 && pMemory != NULL )
	{
		IMediaSample* pSample = pCDemuxer->m_pAllocator->SampleFromBuffer((PBYTE)pMemory);
		if ( pSample != NULL )
		{
			pSample->Release();
		}
		else
		{
			DbgLog( (LOG_TRACE, 2, TEXT("ERROR: Sample is not found in Sample Buffer. memory leaking or Allocator stop.")  ) ); 
		}

		return NULL;
	}
	return NULL;
}


CDemuxer::~CDemuxer()
{
	int i;
	if ( m_pDemuxer ) 
	{
		for ( i = 0; i<MAX_DEMUX_TRACK_NUM; i++ )
			CloseConverter( i );
		ReleaseDemuxer( (DEMUXER*)m_pDemuxer );
	}


	MEMORY_REPORT();
}

void CDemuxer::ResetTrack( int nTrack )
{
	m_pTrackInf[ nTrack ].stream_ready = 0;
	m_pTrackInf[ nTrack ].data_frames = 0;
	m_pTrackInf[ nTrack ].firstPTS = 0;
	m_pTrackInf[ nTrack ].lastPTS = 0;
	m_pTrackInf[ nTrack ].newPTS = 0;
	m_pTrackInf[ nTrack ].stream_id = 0;
	m_pTrackInf[ nTrack ].pin_index = 0;
	m_pTrackInf[ nTrack ].stream_type = 0;
}

void CDemuxer::ZeroTrack( int nTrack )
{
	m_pTrackInf[ nTrack ].data_frames = 0;
}

HRESULT CDemuxer::Init()
{
	HRESULT hr;
	LONGLONG availLength, availLength0 ;
	DWORD StartTick = 0;  //for checking timeout of data
	DbgLog( (LOG_TRACE, 2, TEXT("MPEG2 DeMux Initilization Ver 3.2.0")  ) );  
	memset( m_pTrackInf, 0, sizeof(m_pTrackInf) );
	CParseReaderFromAsync *pRdr = new CParseReaderFromAsync( m_pFilter->InputPin()->Reader() );

	m_bExactDur = FALSE;
	if (SUCCEEDED(pRdr->Length(&availLength)) && SUCCEEDED(pRdr->AvailableLength(&m_rtByteLength)))
	{
		m_bExactDur = (m_rtByteLength == availLength);
		availLength0 = availLength;
	}
	m_uInputStreamFormat = 0;
	pRdr->SetPointer( 0 );
	DbgLog( (LOG_TRACE, 2, TEXT("File Legnth %d 0x%x 0x%x"), m_bExactDur,(LPCSTR)Disp( m_rtByteLength, CDISP_DEC), (LPCSTR)Disp(availLength, CDISP_DEC)  ) );  

	if ( m_dwWaitingTimeForAudio == 0 )
		 m_dwWaitingTimeForAudio = 10000; //10 second timeout for main track audio data available ;
	if ( m_dwWaitingTimeForData == 0 )
		m_dwWaitingTimeForData = 10000;   //12 second timeout for data available;

	DWORD dwTotalInitLen = !m_bExactDur ? m_dwRegInitReadSize : ((DWORD)min(m_dwRegInitReadSize, availLength));
	if (dwTotalInitLen == 0)
	{
		if ( m_pFilter->FindOutputPin( 0, 0 ) ) 
		{
			if ( IS_TS_TYPE( m_uInputStreamFormat ) )
			{
				
				CMediaType cmt(&MEDIATYPE_Stream);
				cmt.SetSubtype(&MEDIASUBTYPE_MPEG2_TRANSPORT);
				cmt.bFixedSizeSamples = FALSE;
				cmt.bTemporalCompression = FALSE;
				cmt.lSampleSize = 1;
				cmt.formattype = FORMAT_None;
				m_pFilter->CreateStream( 0, 0, 0, 0xff, &cmt, L"PassThrough" );
			}
			else
			if ( IS_PS_TYPE( m_uInputStreamFormat ) )
			{
				
				CMediaType cmt(&MEDIATYPE_Stream);
				cmt.SetSubtype(&MEDIASUBTYPE_MPEG2_PROGRAM);
				cmt.bFixedSizeSamples = FALSE;
				cmt.bTemporalCompression = FALSE;
				cmt.lSampleSize = 1;
				cmt.formattype = FORMAT_None;
				m_pFilter->CreateStream( 0, 0, 0, 0xff, &cmt, L"PassThrough" );
			}
		}
		delete pRdr;
		return S_OK;
	}

    /*  Just read 32K and look for interesting stuff */
	DWORD dwIncLen = 64*1024+4*1024;  
	if ( m_rtByteLength < (LONGLONG)dwIncLen )
		dwIncLen = (DWORD)m_rtByteLength;
    PBYTE pbData = new BYTE[dwIncLen];
    if (pbData == NULL) {
        //return E_OUTOFMEMORY;
		delete pRdr;
		return S_OK;
    }
	
	memset( pbData, 0, dwIncLen );
    hr = pRdr->Read(pbData, dwIncLen);
    if (S_OK != hr) {
        delete [] pbData;
        //return hr;
		delete pRdr;
		return S_OK;
    }
	//dwTotalInitLen = dwTotalInitLen > dwIncLen ? dwTotalInitLen - dwIncLen : 0;

	//detect input format first
	StartTick = 0; 
	while (  ( m_uInputStreamFormat == 0 ) && dwTotalInitLen )
	{
		if ( ( m_bIsSagePVRData = CheckSagePVRData( pbData, dwIncLen ) ) )
		{
			m_uInputStreamFormat = MPEG_TS;
			DbgLog( (LOG_TRACE, 2, TEXT("It is SageTV PVR recording data.")  ) );  
			break;
		}
		
		if ( ( m_nPVRDataType = CheckPVRTag( pbData, dwIncLen ) ) )
		{
			m_uInputStreamFormat = MPEG_TS;
			DbgLog( (LOG_TRACE, 2, TEXT("It is QAM PVR recording data.")  ) );  
			break;
		}

		m_uInputStreamFormat = CheckFormat( pbData, dwIncLen );
		if ( m_uInputStreamFormat )
			break;

		hr = pRdr->Read(pbData, dwIncLen);
		if (S_OK != hr)
			break;
	    
		if ( dwIncLen == 0 )
		{
			if ( StartTick == 0 ) StartTick = GetTickCount();
			if ( GetTickCount() - StartTick > m_dwWaitingTimeForData )
				break;
		} else
			StartTick = 0;

		dwTotalInitLen = dwTotalInitLen > dwIncLen ? dwTotalInitLen - dwIncLen : 0;
	}

	if ( m_uInputStreamFormat == 0 )
	{
		delete [] pbData;
		delete pRdr;
		DbgLog((LOG_TRACE, 2, TEXT("MPEG2 DeMux Init failed (known format)")));
        //return VFW_E_INVALID_FILE_FORMAT;
		return S_OK;
	}

	m_rtDur = 0;
	m_rtGuessDur = 0;
	m_llFirstPTS = 0;
	pRdr->SetPointer( 0 );

	if ( !m_dwRequestedByUser )
		m_dwRequestedTSChannel = 0;

	// Make sure the main PS/ts pin is there
    if ( m_pFilter->FindOutputPin( 0, 0 ) == NULL  ) 
	{
		if ( IS_TS_TYPE( m_uInputStreamFormat ) )
		{
			CMediaType cmt(&MEDIATYPE_Stream);
			cmt.SetSubtype(&MEDIASUBTYPE_MPEG2_TRANSPORT);
			cmt.bFixedSizeSamples = FALSE;
			cmt.bTemporalCompression = FALSE;
			cmt.lSampleSize = 1;
			cmt.formattype = FORMAT_None;
			m_pFilter->CreateStream( 0, 0, 0, 0xff, &cmt, L"PassThrough" );
		}
		else
		if ( IS_PS_TYPE( m_uInputStreamFormat ) )
		{
			CMediaType cmt(&MEDIATYPE_Stream);
			cmt.SetSubtype(&MEDIASUBTYPE_MPEG2_PROGRAM);
			cmt.bFixedSizeSamples = FALSE;
			cmt.bTemporalCompression = FALSE;
			cmt.lSampleSize = 1;
			cmt.formattype = FORMAT_None;
			m_pFilter->CreateStream( 0, 0, 0, 0xff, &cmt, L"PassThrough" );
		}
    }

	//create a demuxer
	if ( m_pDemuxer ) 
	{
		ReleaseDemuxer( (DEMUXER*)m_pDemuxer );
	}
	TUNE tune={0};
	tune.channel = 0; //auto pick up a channel

	PVR_META_INF Meta;
	if ( m_bIsSagePVRData && GetSagePVRMetaInf( pRdr, &Meta ) )
	{
		tune.tune_string_type = 15;
		tune.u.atsc.tsid = Meta.tsid;
		tune.u.atsc.program_id = Meta.program_num;
		tune.stream_format = ATSC_STREAM;  //because I don't care its stream type, it's safe to treat it as an ATSC stream
		DbgLog( (LOG_TRACE, 1, TEXT("Get SagtPVR Meta data program:%d tsid:%d"), Meta.tsid, Meta.program_num ) );
	}
	if ( m_nPVRDataType == 2 )
	{
		tune.stream_format = ATSC_STREAM;  //because I don't care its stream type, it's safe to treat it as an ATSC stream
		tune.sub_format = CABLE;
		DbgLog( (LOG_TRACE, 1, TEXT("QAM encoder recording.") ) );
	}

	m_pDemuxer = CreateDemuxer( m_uInputStreamFormat, MAX_DEMUX_TRACK_NUM, MEM_BLOCK_SIZE ); 
	if ( !OpenStreamSource( (DEMUXER*)m_pDemuxer, m_uInputStreamFormat, &tune ) )
	{
		delete [] pbData;
		delete pRdr;
		DbgLog((LOG_TRACE, 2, TEXT("MPEG2 DeMux Open Stream source Failed")));
		return S_OK;
	}

	//setup dumper
	SetupBlockDataDumper( (DEMUXER*)m_pDemuxer, (DUMP)ESDataDumper,  this );
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

	DWORD CheckBytes = 0; 
	StartTick = 0; 
	m_bStreamRebuild = FALSE;
	pRdr->SetPointer( 0 );
	ZeroDemuxer( );
	hr = pRdr->Read( pbData, dwIncLen );
	if ( S_OK != hr) 
		CheckBytes = dwTotalInitLen;
	while ( CheckBytes < dwTotalInitLen )
	{
		int nUsedBytes, nReadBytes;
		nUsedBytes = PushData( pbData, dwIncLen );
		if ( nUsedBytes == 0 )
			nUsedBytes = dwIncLen;
		ASSERT( (int)dwIncLen >= nUsedBytes );

		//AVInf ready
		if ( m_nState == 2 )
			break;
		//Failed
		if ( m_nState == -1 )
			break;

		CheckBytes += (DWORD)nUsedBytes;
		if (  m_bStreamRebuild ) 
		{
			CheckBytes = 0;//dwTotalInitLen;
			pRdr->SetPointer( 0 ); 
			m_bStreamRebuild = FALSE;
		}

		nReadBytes = dwIncLen - nUsedBytes;
		if ( nReadBytes > 0 )
		{
			memcpy( pbData,  pbData+nUsedBytes, nReadBytes );
			hr = pRdr->Read( pbData+nReadBytes, nUsedBytes );
		} else
		{
			nReadBytes = dwIncLen;
			if ( CheckBytes + dwIncLen >= dwTotalInitLen )
				nReadBytes = dwTotalInitLen - CheckBytes;

			//if small clip, we need rewind to repeat parse its format
			if ( nReadBytes == 0 )
			{
				if ( dwTotalInitLen < 32*1024  )
				{
					pRdr->SetPointer( 0 ); 
					dwTotalInitLen += dwIncLen;
				} else
					break;
			}

			hr = pRdr->Read( pbData, nReadBytes );
		}
		if ( S_OK != hr ) 
			break;
	};
	DbgLog( (LOG_TRACE, 1, TEXT("Format detection is done.")));

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

		m_llFirstPTS = m_pTrackInf[m_nPTSTrackIndex].firstPTS;
	}

	//rate bound
	//caculate data play duration
	if ( IS_PS_TYPE( m_uInputStreamFormat ) )
	{
		m_dwRateBound = PSStreamBoundRate( ((DEMUXER*)m_pDemuxer)->ps_parser );
	}
	else
		m_dwRateBound = m_dwAudioRate + m_dwVideoRate;

	if ( m_dwRateBound == 0 )
		m_dwRateBound = 000000; 

	//if file length is not changed, we think it's a static file, not stream file/data
	m_bExactDur = FALSE;
	if (SUCCEEDED(pRdr->Length(&availLength)) && SUCCEEDED(pRdr->AvailableLength(&m_rtByteLength)))
	{
		m_bExactDur = (m_rtByteLength == availLength) && (availLength0 == availLength);
	}
	
	if ( m_bExactDur )
	{
		LONGLONG llDur;
		//for best performace not overheating on buffer alligement, set block size
		m_llFirstPTS = m_llLastPTS = 0;
		m_llFirstPTS = ReadFileFirstPTS( pRdr );
		m_llLastPTS  = ReadFileLastPTS( pRdr );
		if ( m_llLastPTS == 0 ) m_bFileCrashed = 1;
		//PTS round up
		m_llLastPTS = PTS_ROUND_UP( m_llLastPTS, m_llFirstPTS ); 
		llDur = m_llLastPTS - m_llFirstPTS;
		m_rtDur =  PTS2MT( llDur )  ;
		m_llFirstPTS = PTS2MT( m_llFirstPTS );
		m_llLastPTS =  PTS2MT( m_llLastPTS );
		if (  m_rtDur< 0 ) m_bFileCrashed = 1;
		if ( m_bFileCrashed )
		{   //last PTS not found or the file is carshed, //guess out duration
			LONGLONG llPTS;
			LONGLONG llPos, llStart = m_rtByteLength*2/3, llEnd = m_rtByteLength;
			while ( llStart > dwIncLen )
			{
				llPos = llStart;
				llPTS = FindFirstPTS( pRdr, pbData,  dwIncLen, &llPos, llEnd );
				if (llPTS > 0 ) break;
				llEnd = llStart;
				llStart = llStart*3/2;
			}
			if ( llPTS > 0 )
				m_rtGuessDur = m_rtByteLength * (LONGLONG)(1.0*llPTS/llPos);
			m_rtDur = m_rtGuessDur;
		}
		
		if ( m_rtDur )
			m_dwBitRate = (DWORD)(m_rtByteLength*UNITS/m_rtDur);

		DbgLog( (LOG_TRACE, 1, TEXT("Static File Dur:%s, StartPTS:%s, LastPTS:%s, rate:%d '%s'."), 
			(LPCTSTR)Disp((CRefTime)m_rtDur), 
			(LPCTSTR)Disp((CRefTime)m_llFirstPTS), (LPCTSTR)Disp((CRefTime)m_llLastPTS), m_dwBitRate, m_bFileCrashed? "Crashed file":"" )); 
		


	} else
	{
		m_rtDur = ONE_DAY_MT;
		DbgLog( (LOG_TRACE, 1, TEXT(" Alive File. Dur:%s"), (LPCTSTR)Disp((CRefTime)m_rtDur) ));
	}

	DbgLog((LOG_TRACE, 3, TEXT("Approximating streams rate audioRate=%d videoRate=%d, RateBound=%d"),
				m_dwAudioRate, m_dwVideoRate, m_dwRateBound));
	
	//reset Demuxer
	m_pTrackInf[m_nPTSTrackIndex].lastPTS = 0;
	m_llRecentBytePos = 0;
	m_rtRecentTime = 0;
	pRdr->SetPointer( 0 );
	//create output pin
	UpdateStream(  );

	
	//DbgLog( (LOG_TRACE, 2, TEXT("File Legnth static:%d %s %s dur:%s  :%d BPS"), 
	//	 m_bExactDur, (LPCSTR)Disp( m_rtByteLength, CDISP_DEC), 
	//	 (LPCSTR)Disp(availLength, CDISP_DEC), (LPCSTR)Disp( (CRefTime)m_rtDur),
	//	 (unsigned long)(availLength)/( m_rtDur/UNITS + 1) ));  

	DbgLog((LOG_TRACE, 2, TEXT("MPEG2 DeMux  Init Done with successed")));
	delete [] pbData;	
	delete pRdr;
	return S_OK;

}
//////////////////////////////////////////////////////////////////
int CDemuxer::GetSagePVRMetaInf( CParseReader *pRdr, void *pMetaInf ) //ZQ.1
{
	HRESULT  hr;
	DWORD dwPos = 0;
	int dwNextOffset, dwOffset;
	unsigned char Buf[188];
	PVR_META_INF *pMeta = (PVR_META_INF*)pMetaInf;
	
	//search Sage NULL packet header
	pRdr->SetPointer(dwPos);
	while ( dwPos < 1024*1024 ) 
	{
		unsigned char tmp[4096];
		hr = pRdr->Read(tmp, sizeof(tmp));
		if ( hr != S_OK ) return -1;
		dwOffset = SearchPVRMetaInf( &dwNextOffset, tmp, sizeof(tmp) );
		if ( dwOffset >= 0 )
		{
			dwPos += dwOffset;
			break;
		}
		dwPos += sizeof(tmp);
	}

	//find meta data
	pRdr->SetPointer(dwPos);
	while ( dwPos < 1024*1024 )
	{
		hr = pRdr->Read(Buf, sizeof(Buf));
		if ( hr != S_OK ) break;
		
		dwOffset = SearchPVRMetaInf( &dwNextOffset, Buf, sizeof(Buf) );
		if ( dwOffset < 0 )
			break;
		dwPos += 188+dwNextOffset;
		if ( CheckSagePVRData( Buf+dwOffset, sizeof(Buf)-dwOffset ) )
			if ( GetPVRMetaInf( pMeta, Buf+dwOffset ) && pMeta->state > 0 )
			{
				pRdr->SetPointer(0);
				return 1;
			}
		pRdr->SetPointer( dwPos );
	}
	return 0;
}
//////////////////////////////////////////////////////////////////
void CDemuxer::SetupMemAllocatorHook( CSequentialAllocator *pOutputAllocator )
{
	ASSERT( pOutputAllocator );
	m_pAllocator = pOutputAllocator;
	SetupMemAllocHook(  (DEMUXER*)m_pDemuxer, MemAllocatorHook, this );
	
}

//////////////////////////////////////////////////////////////////
HRESULT CDemuxer::SetMpeg1VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
    cmt->majortype = MEDIATYPE_Video;
    cmt->subtype =   MEDIASUBTYPE_MPEG1Payload;
    VIDEOINFO *videoInfo =
        (VIDEOINFO *)cmt->AllocFormatBuffer(FIELD_OFFSET(MPEG1VIDEOINFO, bSequenceHeader[pMpegVideo->actual_header_length]));
    if (videoInfo == NULL) 
        return E_OUTOFMEMORY;
    
    RESET_HEADER(videoInfo);
	ZeroMemory(videoInfo, sizeof(videoInfo) ); 

	videoInfo->dwBitRate          = pMpegVideo->bit_rate;
    videoInfo->rcSource.right     = pMpegVideo->width;
    videoInfo->bmiHeader.biWidth  = pMpegVideo->width;
    videoInfo->rcSource.bottom    = pMpegVideo->height;
    videoInfo->bmiHeader.biHeight = pMpegVideo->height;
    videoInfo->bmiHeader.biXPelsPerMeter = 0;//pMpegVideo->lXPelsPerMeter;
    videoInfo->bmiHeader.biYPelsPerMeter = 0;//pMpegVideo->lYPelsPerMeter;
    videoInfo->bmiHeader.biSize   = sizeof(BITMAPINFOHEADER);

    videoInfo->AvgTimePerFrame = pMpegVideo->time_per_frame;
    MPEG1VIDEOINFO *mpgvideoInfo = (MPEG1VIDEOINFO *)videoInfo;
	mpgvideoInfo->cbSequenceHeader = pMpegVideo->actual_header_length;
	CopyMemory((PVOID)mpgvideoInfo->bSequenceHeader,
               (PVOID)pMpegVideo->raw_header,
			   pMpegVideo->actual_header_length);
    mpgvideoInfo->dwStartTimeCode = 0;//pMpegVideo->StartTimeCode;

    cmt->SetFormatType(&FORMAT_MPEGVideo);
    return S_OK;
}

HRESULT CDemuxer::SetMpeg2VideoMediaType(CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
    cmt->majortype = MEDIATYPE_Video;
    cmt->subtype = MEDIASUBTYPE_MPEG2_VIDEO;

    int header_padded_length = pMpegVideo->actual_header_length + (4 - (pMpegVideo->actual_header_length % 4)) % 4; // pad length to DWORD boundary

    MPEG2VIDEOINFO *videoInfo = // This macro finds the pointer to the last element in the sedhdr block to determine size
        //(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(FIELD_OFFSET(MPEG2VIDEOINFO, dwSequenceHeader[pMpegVideo->lActualHeaderLen]));
		// There's already a DWORD in the block for the sequence header, so subtract 4 bytes
		(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(sizeof(MPEG2VIDEOINFO) + header_padded_length - 4);
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }

	// reset the header's memory
	ZeroMemory((PVOID)(videoInfo), sizeof(MPEG2VIDEOINFO) + header_padded_length - 4);

    videoInfo->hdr.dwBitRate          = pMpegVideo->bit_rate;
    videoInfo->hdr.rcSource.right     = pMpegVideo->width;
    videoInfo->hdr.bmiHeader.biWidth  = pMpegVideo->width;
    videoInfo->hdr.rcSource.bottom    = pMpegVideo->height;
    videoInfo->hdr.bmiHeader.biHeight = pMpegVideo->height;
    videoInfo->hdr.bmiHeader.biXPelsPerMeter = 2000;//pMpegVideo->lXPelsPerMeter;
    videoInfo->hdr.bmiHeader.biYPelsPerMeter = 2000;//pMpegVideo->lYPelsPerMeter;
    videoInfo->hdr.bmiHeader.biSize   = sizeof(BITMAPINFOHEADER);

	// Determine the aspect ratio
	switch (pMpegVideo->ar_info)
	{
		case 1: // SAR=1:1
			videoInfo->hdr.dwPictAspectRatioX = 1;
			videoInfo->hdr.dwPictAspectRatioY = 1;
			break;
		case 2: // DAR=3/4
			videoInfo->hdr.dwPictAspectRatioX = 4;
			videoInfo->hdr.dwPictAspectRatioY = 3;
			break;
		case 3: // DAR=9/16
			videoInfo->hdr.dwPictAspectRatioX = 16;
			videoInfo->hdr.dwPictAspectRatioY = 9;
			break;
		/*case 4: // DAR=1/2.21
			videoInfo->hdr.dwPictAspectRatioX = ;
			videoInfo->hdr.dwPictAspectRatioY = 1;
			break;*/
	}
	
	videoInfo->hdr.dwInterlaceFlags = pMpegVideo->progressive ? 0 : (AMINTERLACE_IsInterlaced);
	videoInfo->hdr.AvgTimePerFrame = pMpegVideo->picture_time;
    videoInfo->cbSequenceHeader = header_padded_length;
    CopyMemory((PVOID)videoInfo->dwSequenceHeader,
			   (PVOID)pMpegVideo->raw_header,
               pMpegVideo->actual_header_length);
    //videoInfo->dwStartTimeCode = pMpegVideo->StartTimeCode;
	switch (pMpegVideo->profile)
	{
		case 5:
			videoInfo->dwProfile = AM_MPEG2Profile_Simple;
			break;
		case 4:
			videoInfo->dwProfile = AM_MPEG2Profile_Main;
			break;
		case 3:
			videoInfo->dwProfile = AM_MPEG2Profile_SNRScalable;
			break;
		case 2:
			videoInfo->dwProfile = AM_MPEG2Profile_SpatiallyScalable;
			break;
		case 1:
			videoInfo->dwProfile = AM_MPEG2Profile_High;
			break;
	}
	switch (pMpegVideo->level)
	{
		case 10:
			videoInfo->dwLevel = AM_MPEG2Level_Low;
			break;
		case 8:
			videoInfo->dwLevel = AM_MPEG2Level_Main;
			break;
		case 6:
			videoInfo->dwLevel = AM_MPEG2Level_High1440;
			break;
		case 4:
			videoInfo->dwLevel = AM_MPEG2Level_High;
			break;
	}

    cmt->SetFormatType(&FORMAT_MPEG2Video);
    return S_OK;
}

HRESULT CDemuxer::SetH264VideoMediaType(CMediaType *cmt, H264_VIDEO *pH264Video )
{
    cmt->majortype = MEDIATYPE_Video;
    cmt->subtype = MEDIASUBTYPE_H264; 

	const DWORD H264FOURCC = 0x34363248; //DWORD('H264');

  int header_padded_length = pH264Video->sps_length + (4 - (pH264Video->sps_length % 4)) % 4; // pad length to DWORD boundary

	//VIDEOINFOHEADER2 *videoInfo = (VIDEOINFOHEADER2*)cmt->AllocFormatBuffer( sizeof(VIDEOINFOHEADER2)+pH264Video->sps_length-4 );
	 MPEG2VIDEOINFO *videoInfo = // This macro finds the pointer to the last element in the sedhdr block to determine size
        //(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(FIELD_OFFSET(MPEG2VIDEOINFO, dwSequenceHeader[pMpegVideo->lActualHeaderLen]));
		// There's already a DWORD in the block for the sequence header, so subtract 4 bytes
		(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(sizeof(MPEG2VIDEOINFO) + header_padded_length-4);
	
	cmt->bFixedSizeSamples = 1;
	cmt->SetTemporalCompression( FALSE );
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(videoInfo), sizeof(VIDEOINFOHEADER2));
    //cmt->SetFormatType(&FORMAT_VideoInfo2);
	cmt->SetFormatType(&FORMAT_MPEG2Video);
    videoInfo->hdr.rcSource.right  = pH264Video->width;
    videoInfo->hdr.rcSource.bottom = pH264Video->height-8;
    videoInfo->hdr.rcTarget.right  = pH264Video->width;
    videoInfo->hdr.rcTarget.bottom = pH264Video->height-8;
    videoInfo->hdr.dwBitRate       = 0;
	videoInfo->hdr.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
	videoInfo->hdr.bmiHeader.biWidth = pH264Video->width;
	videoInfo->hdr.bmiHeader.biHeight= pH264Video->height-8;
	videoInfo->hdr.bmiHeader.biPlanes = 1;
	videoInfo->hdr.bmiHeader.biBitCount = 0;
	videoInfo->hdr.bmiHeader.biCompression = H264FOURCC;
	videoInfo->hdr.bmiHeader.biSizeImage = 0;
	videoInfo->hdr.bmiHeader.biClrUsed = 0;//0x73; //DIBSIZE(videoInfo->bmiHeader); //6619248
	videoInfo->cbSequenceHeader = header_padded_length;
    CopyMemory((PVOID)videoInfo->dwSequenceHeader,
			   (PVOID)pH264Video->sps,  pH264Video->sps_length);
	videoInfo->dwProfile = pH264Video->profile;
	videoInfo->dwLevel = pH264Video->level;

	// Determine the aspect ratio
	//guess ration
	{
		float r_min=100, rr, ratio_tbl[4]={ (float)1.0/1, (float)4.0/3, (float)16.0/9, (float)20.0/11 };
		int i, ri;
		rr =  (float)pH264Video->width/(pH264Video->height-8);
		for ( i=0; i<4; i++ )
		{
			float dif = ( rr-ratio_tbl[i] );
			if ( dif < 0 ) dif = -dif;
			if ( dif < r_min )
			{
				ri = i; r_min = dif;
			}
		}

		switch (ri+1)
		{
			case 1: // SAR=1:1
				videoInfo->hdr.dwPictAspectRatioX = 1;
				videoInfo->hdr.dwPictAspectRatioY = 1;
				break;
			case 2: // DAR=3/4
				videoInfo->hdr.dwPictAspectRatioX = 4;
				videoInfo->hdr.dwPictAspectRatioY = 3;
				break;
			case 3: // DAR=9/16
				videoInfo->hdr.dwPictAspectRatioX = 16;
				videoInfo->hdr.dwPictAspectRatioY = 9;
				break;
			case 4: 
				videoInfo->hdr.dwPictAspectRatioX = 20;
				videoInfo->hdr.dwPictAspectRatioY = 11;
				break;
		}
	}
	{
		if ( pH264Video->frame_rate_nomi && pH264Video->frame_rate_deno ) 
			videoInfo->hdr.AvgTimePerFrame = (LONGLONG)10000000*pH264Video->frame_rate_deno/pH264Video->frame_rate_nomi;  //10000000/frame_rate; (400000)
		else	
			videoInfo->hdr.AvgTimePerFrame = 10000000/25;

		videoInfo->hdr.dwInterlaceFlags = pH264Video->progressive; 
	}
    return S_OK;
}

HRESULT CDemuxer::SetH264VideoMediaType4Cyberlink(CMediaType *cmt, H264_VIDEO *pH264Video )
{
    cmt->majortype = MEDIATYPE_Video;
	cmt->subtype = CLSID_MpegH264; 

	const DWORD H264FOURCC = 0x34363248; //DWORD('H264');

  int header_padded_length = pH264Video->sps_length + (4 - (pH264Video->sps_length % 4)) % 4; // pad length to DWORD boundary

	//VIDEOINFOHEADER2 *videoInfo = (VIDEOINFOHEADER2*)cmt->AllocFormatBuffer( sizeof(VIDEOINFOHEADER2)+pH264Video->sps_length-4 );
	 MPEG2VIDEOINFO *videoInfo = // This macro finds the pointer to the last element in the sedhdr block to determine size
        //(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(FIELD_OFFSET(MPEG2VIDEOINFO, dwSequenceHeader[pMpegVideo->lActualHeaderLen]));
		// There's already a DWORD in the block for the sequence header, so subtract 4 bytes
		(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(sizeof(MPEG2VIDEOINFO) + header_padded_length-4);
	
	cmt->bFixedSizeSamples = 0;
	cmt->SetTemporalCompression( TRUE );
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(videoInfo), sizeof(VIDEOINFOHEADER2));
    //cmt->SetFormatType(&FORMAT_VideoInfo2);
	cmt->SetFormatType(&FORMAT_MPEG2Video);
    videoInfo->hdr.rcSource.right  = pH264Video->width;
    videoInfo->hdr.rcSource.bottom = pH264Video->height-8; 
    videoInfo->hdr.rcTarget.right  = pH264Video->width;
    videoInfo->hdr.rcTarget.bottom = pH264Video->height-8;
    videoInfo->hdr.dwBitRate       = 0;
	videoInfo->hdr.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
	videoInfo->hdr.bmiHeader.biWidth = pH264Video->width;
	videoInfo->hdr.bmiHeader.biHeight= pH264Video->height-8;
	videoInfo->hdr.bmiHeader.biPlanes = 1;
	videoInfo->hdr.bmiHeader.biBitCount = 0;
	videoInfo->hdr.bmiHeader.biCompression = H264FOURCC;
	videoInfo->hdr.bmiHeader.biSizeImage = 0;
	videoInfo->hdr.bmiHeader.biClrUsed = 0;//0x73; //DIBSIZE(videoInfo->bmiHeader); //6619248
	videoInfo->dwProfile = pH264Video->profile;
	videoInfo->dwLevel = pH264Video->level;
	videoInfo->dwFlags = 0;
	videoInfo->cbSequenceHeader = header_padded_length;
    CopyMemory((PVOID)videoInfo->dwSequenceHeader,
			   (PVOID)pH264Video->sps,  pH264Video->sps_length);
	// Determine the aspect ratio
	//guess ration
	{
		float r_min=100, rr, ratio_tbl[4]={ (float)1.0/1, (float)4.0/3, (float)16.0/9, (float)20.0/11 };
		int i, ri;
		rr =  (float)pH264Video->width/(pH264Video->height-8);
		for ( i=0; i<4; i++ )
		{
			float dif = ( rr-ratio_tbl[i] );
			if ( dif < 0 ) dif = -dif;
			if ( dif < r_min )
			{
				ri = i; r_min = dif;
			}
		}

		switch (ri+1)
		{
			case 1: // SAR=1:1
				videoInfo->hdr.dwPictAspectRatioX = 1;
				videoInfo->hdr.dwPictAspectRatioY = 1;
				break;
			case 2: // DAR=3/4
				videoInfo->hdr.dwPictAspectRatioX = 4;
				videoInfo->hdr.dwPictAspectRatioY = 3;
				break;
			case 3: // DAR=9/16
				videoInfo->hdr.dwPictAspectRatioX = 16;
				videoInfo->hdr.dwPictAspectRatioY = 9;
				break;
			case 4: 
				videoInfo->hdr.dwPictAspectRatioX = 20;
				videoInfo->hdr.dwPictAspectRatioY = 11;
				break;
		}
	}
	{
		if ( pH264Video->frame_rate_nomi && pH264Video->frame_rate_deno ) 
			videoInfo->hdr.AvgTimePerFrame = (LONGLONG)10000000*pH264Video->frame_rate_deno/pH264Video->frame_rate_nomi;  //10000000/frame_rate; (400000)
		else	
			videoInfo->hdr.AvgTimePerFrame = 10000000/25;

		videoInfo->hdr.dwInterlaceFlags = pH264Video->progressive; 
	}
    return S_OK;
}


HRESULT CDemuxer::SetH264VC1VideoMediaType(CMediaType *cmt, H264_VIDEO *pH264Video )
{
    cmt->majortype = MEDIATYPE_Video;
	cmt->subtype = CLSID_MpegH264VC1;
	const DWORD AVC1FOURCC = 0x31435641; //DWORD('AVC1');
	
    MPEG2VIDEOINFO *videoInfo = // This macro finds the pointer to the last element in the sedhdr block to determine size
        //(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(FIELD_OFFSET(MPEG2VIDEOINFO, dwSequenceHeader[pH264Video->lActualHeaderLen]));
		// There's already a DWORD in the block for the sequence header, so subtract 4 bytes
		(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(sizeof(MPEG2VIDEOINFO) + sizeof(VIDEOINFOHEADER2) );
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	// reset the header's memory
	ZeroMemory((PVOID)(videoInfo), sizeof(MPEG2VIDEOINFO) + sizeof(VIDEOINFOHEADER2) );

    videoInfo->hdr.dwBitRate          = 0;
    videoInfo->hdr.rcSource.right     = pH264Video->width;
    videoInfo->hdr.bmiHeader.biWidth  = pH264Video->width;
    videoInfo->hdr.rcSource.bottom    = pH264Video->height-8;
    videoInfo->hdr.bmiHeader.biHeight = pH264Video->height-8;
    videoInfo->hdr.bmiHeader.biXPelsPerMeter = 0;//pH264Video->lXPelsPerMeter;
    videoInfo->hdr.bmiHeader.biYPelsPerMeter = 0;//pH264Video->lYPelsPerMeter;
	videoInfo->hdr.bmiHeader.biCompression = AVC1FOURCC;
    videoInfo->hdr.bmiHeader.biSize   = sizeof(BITMAPINFOHEADER);

	{
		if ( pH264Video->frame_rate_nomi && pH264Video->frame_rate_deno ) 
			videoInfo->hdr.AvgTimePerFrame = (LONGLONG)10000000*pH264Video->frame_rate_deno/pH264Video->frame_rate_nomi;  //10000000/frame_rate; (400000)
		else	
			videoInfo->hdr.AvgTimePerFrame = 10000000/25;

		videoInfo->hdr.dwInterlaceFlags = pH264Video->progressive; 

	}

	videoInfo->hdr.dwPictAspectRatioX = pH264Video->width;
	videoInfo->hdr.dwPictAspectRatioY = pH264Video->height-8;
	videoInfo->cbSequenceHeader = pH264Video->sps_length;
    CopyMemory((PVOID)videoInfo->dwSequenceHeader,
			   (PVOID)pH264Video->sps,  pH264Video->sps_length);
    //videoInfo->cbSequenceHeader = sizeof(code);
    //CopyMemory((PVOID)videoInfo->dwSequenceHeader, code, sizeof(code) );

    videoInfo->dwStartTimeCode = 0;

	videoInfo->dwProfile = 100;
	videoInfo->dwLevel = 40;
	videoInfo->dwFlags = 4;


    cmt->SetFormatType(&FORMAT_MPEG2Video);
    return S_OK;

}

HRESULT CDemuxer::SetVC1VideoMediaType(CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

	const DWORD VC1FOURCC = 0x312d4356; //DWORD('VC-1');
	cmt->SetType( &MEDIATYPE_Video ); 
	cmt->subtype = CLSID_MpegVC1;
	cmt->SetFormatType( &FORMAT_VideoInfo );

	cmt->SetTemporalCompression( 0);
	cmt->lSampleSize = 0;
	cmt->bFixedSizeSamples = 0;

	 _VIDEOINFOHEADER*  VideoHdr = ( _VIDEOINFOHEADER*)cmt->AllocFormatBuffer( sizeof(_VIDEOINFOHEADER) );
    if ( VideoHdr == NULL) {
        return E_OUTOFMEMORY;
    }

	// reset the header's memory
	ZeroMemory((PVOID)(VideoHdr), sizeof(_VIDEOINFOHEADER)  );

    VideoHdr->rcSource.right  = pMpegVideo->width;
    VideoHdr->rcSource.bottom = pMpegVideo->height;
    VideoHdr->rcTarget.right  = pMpegVideo->width;
    VideoHdr->rcTarget.bottom = pMpegVideo->height;
    VideoHdr->dwBitRate       = 0;
	if ( pMpegVideo->frame_rate_nomi && pMpegVideo->frame_rate_deno )
		VideoHdr->AvgTimePerFrame = (LONGLONG)(UNITS/pMpegVideo->frame_rate_nomi/pMpegVideo->frame_rate_deno);
	VideoHdr->bmiHeader.biSize = sizeof(_BITMAPINFOHEADER);
	VideoHdr->bmiHeader.biWidth = pMpegVideo->width;
	VideoHdr->bmiHeader.biHeight= pMpegVideo->height;
	VideoHdr->bmiHeader.biPlanes = 1;
	VideoHdr->bmiHeader.biBitCount = 0;
	VideoHdr->bmiHeader.biCompression = VC1FOURCC;
	VideoHdr->bmiHeader.biSizeImage = DIBSIZE(VideoHdr->bmiHeader);

    return S_OK;
}


HRESULT CDemuxer::SetWVC1VideoMediaType(CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

	const DWORD WVC1FOURCC = 0x31435657;// [WVC1]

    cmt->majortype = MEDIATYPE_Video;
	cmt->SetType( &MEDIATYPE_Video ); 
    cmt->subtype = CLSID_MpegWVC1; 
	cmt->SetFormatType( &FORMAT_VideoInfo );

	cmt->SetTemporalCompression( 0);
	cmt->lSampleSize = 1;
	cmt->bFixedSizeSamples = 0;

	int extra_data_size;
	unsigned char *extra_data;
	extra_data_size = pMpegVideo->actual_header_length+1;
	extra_data = pMpegVideo->raw_header;
	int size = sizeof(_VIDEOINFOHEADER) + extra_data_size;

	 _VIDEOINFOHEADER*  VideoHdr = ( _VIDEOINFOHEADER*)cmt->AllocFormatBuffer( size );
    if ( VideoHdr == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(VideoHdr), size  );
    VideoHdr->rcSource.right  = pMpegVideo->width;
    VideoHdr->rcSource.bottom = pMpegVideo->height;
    VideoHdr->rcTarget.right  = pMpegVideo->width;
    VideoHdr->rcTarget.bottom = pMpegVideo->height;
    VideoHdr->dwBitRate       = 0;
	VideoHdr->bmiHeader.biSize = sizeof(_BITMAPINFOHEADER)+extra_data_size;
	VideoHdr->bmiHeader.biWidth = pMpegVideo->width;
	VideoHdr->bmiHeader.biHeight= pMpegVideo->height;
	VideoHdr->bmiHeader.biPlanes = 1;
	VideoHdr->bmiHeader.biBitCount = 24;
	VideoHdr->bmiHeader.biCompression = WVC1FOURCC;
	VideoHdr->bmiHeader.biXPelsPerMeter = 1;
	VideoHdr->bmiHeader.biYPelsPerMeter = 1;
	VideoHdr->bmiHeader.biSizeImage = pMpegVideo->width*pMpegVideo->height*3;//DIBSIZE(VideoHdr->bmiHeader);;
	if ( pMpegVideo->frame_rate_nomi && pMpegVideo->frame_rate_deno ) 
		VideoHdr->AvgTimePerFrame = (LONGLONG)10000000*pMpegVideo->frame_rate_deno/pMpegVideo->frame_rate_nomi;  //10000000/frame_rate; (400000)
	else	
		VideoHdr->AvgTimePerFrame = 10000000/25;

	if (extra_data_size)
	{
		CopyMemory((char*)VideoHdr+sizeof(_VIDEOINFOHEADER)+1, (PVOID)extra_data,  extra_data_size-1 );
	}


	return S_OK;
}


HRESULT CDemuxer::SetMpeg4VideoMediaType(CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;


	cmt->SetType( &MEDIATYPE_Video ); 
	const DWORD DIVXFOURCC = 0x44495658; //DWORD('XVID');
	FOURCCMap divx(DIVXFOURCC);
	cmt->SetSubtype( &divx );
	cmt->SetFormatType( &FORMAT_VideoInfo );

	cmt->SetTemporalCompression( 0);
	cmt->lSampleSize = 0;
	cmt->bFixedSizeSamples = 0;

	 _VIDEOINFOHEADER*  VideoHdr = ( _VIDEOINFOHEADER*)cmt->AllocFormatBuffer( sizeof(_VIDEOINFOHEADER) );
    if ( VideoHdr == NULL) {
        return E_OUTOFMEMORY;
    }

	// reset the header's memory
	ZeroMemory((PVOID)(VideoHdr), sizeof(_VIDEOINFOHEADER)  );

    VideoHdr->rcSource.right  = pMpegVideo->width;
    VideoHdr->rcSource.bottom = pMpegVideo->height;
    VideoHdr->rcTarget.right  = pMpegVideo->width;
    VideoHdr->rcTarget.bottom = pMpegVideo->height;
    VideoHdr->dwBitRate       = 0x32c9c3; //pMpegVideo->BitRate;
	VideoHdr->AvgTimePerFrame = (LONGLONG)(UNITS/pMpegVideo->picture_rate);
	VideoHdr->bmiHeader.biSize = sizeof(_BITMAPINFOHEADER);
	VideoHdr->bmiHeader.biWidth = pMpegVideo->width;
	VideoHdr->bmiHeader.biHeight= pMpegVideo->height;
	VideoHdr->bmiHeader.biPlanes = 1;
	VideoHdr->bmiHeader.biBitCount = 24;
	VideoHdr->bmiHeader.biCompression = DIVXFOURCC;
	VideoHdr->bmiHeader.biSizeImage = DIBSIZE(VideoHdr->bmiHeader);

    return S_OK;
}

HRESULT CDemuxer::SetSageDixVMediaType(CMediaType *cmt, MEDIA_VIDEO *pMediaVideo )
{
	memcpy( (void*)&cmt->majortype, pMediaVideo->Media.majortype, 16 );
	memcpy( (void*)&cmt->subtype,   pMediaVideo->Media.subtype  , 16 );
	memcpy( (void*)&cmt->formattype,pMediaVideo->Media.formattype, 16 );
	cmt->bFixedSizeSamples = pMediaVideo->Media.bFixedSizeSamples;
	cmt->bTemporalCompression = pMediaVideo->Media.bTemporalCompression;
	cmt->lSampleSize = pMediaVideo->Media.lSampleSize;
	cmt->AllocFormatBuffer( pMediaVideo->Media.cbFormat );
	memcpy( (void*)cmt->pbFormat, (const void*)&pMediaVideo->Video, cmt->cbFormat );
	m_dwVideoRate = pMediaVideo->Video.dwBitRate / 8; // it's in bits/second
	m_bUseDataAlignForSync = TRUE;
	return S_OK;
}
///////////////////////////////// AUDIO ////////////////////////////////////////
typedef struct 
{
    unsigned short        wFormatTag;         /* format type */
    unsigned short        nChannels;          /* number of channels (i.e. mono, stereo...) */
    unsigned long         nSamplesPerSec;     /* sample rate */
    unsigned long         nAvgBytesPerSec;    /* for buffer estimation */
    unsigned short        nBlockAlign;        /* block size of data */
    unsigned short        wBitsPerSample;     /* number of bits per sample of mono data */
    unsigned short        cbSize;             /* the count in bytes of the size of */
				    /* extra information (after cbSize) */
} _WAVEFORMATEX;
#define SIZEOF_WAVEFORMATEX  18 //ZQ don't use sizeof(_WAVEFORMATEX), as struct byte aligment casue a trouble

HRESULT CDemuxer::SetAACAuidoMediaType( CMediaType *cmt, AAC_AUDIO *pAACAudio )
{
	_WAVEFORMATEX*   pWfx;	
	cmt->SetType( &MEDIATYPE_Audio ); 
	if ( pAACAudio->format == 1 )
	{
		pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX  );
		memset( pWfx, 0, SIZEOF_WAVEFORMATEX  );
		pWfx->cbSize = 0;
		pWfx->nSamplesPerSec = pAACAudio->samples_per_sec;
		pWfx->nChannels      = pAACAudio->channels;

		cmt->SetSubtype( &MEDIASUBTYPE_AAC );
		cmt->SetFormatType(&FORMAT_WaveFormatEx);
	}
	else
	{
		cmt->subtype = MEDIASUBTYPE_LATM_AAC; 
		cmt->SetFormatType(&FORMAT_WaveFormatEx);
		int extradata_size = sizeof( pAACAudio->u.latm_header );
		pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX + extradata_size );
		memset( (char*)pWfx, 0, SIZEOF_WAVEFORMATEX + extradata_size  );
		pWfx->cbSize = extradata_size;
		pWfx->nSamplesPerSec = pAACAudio->samples_per_sec;
		pWfx->nChannels      = pAACAudio->channels;
		pWfx->wFormatTag = 0x1ff;
		memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, pAACAudio->u.latm_header, extradata_size );

	}
	return S_OK;
}


HRESULT CDemuxer::SetAACAuidoMediaType2( CMediaType *cmt, AAC_AUDIO *pAACAudio )
{
	_WAVEFORMATEX*   pWfx;	
	cmt->SetType( &MEDIATYPE_Audio ); 
	if ( pAACAudio->format == 1 ) //ADTS
	{
		pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX  );
		memset( pWfx, 0, SIZEOF_WAVEFORMATEX  );
		pWfx->cbSize = 0;
		pWfx->nSamplesPerSec = pAACAudio->samples_per_sec;
		pWfx->nChannels      = pAACAudio->channels;

		pWfx->wFormatTag = WAVE_FORMAT_MPEG_ADTS_AAC;
		cmt->SetSubtype( &MEDIASUBTYPE_MPEG_ADTS_AAC);
		cmt->SetFormatType(&FORMAT_WaveFormatEx);
	}
	else
	if ( pAACAudio->format == 2 ) //LOAS
	{
		int extradata_size = sizeof( pAACAudio->u.latm_header );
		pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX + extradata_size );
		memset( (char*)pWfx, 0, SIZEOF_WAVEFORMATEX + extradata_size  );
		pWfx->cbSize = extradata_size;
		pWfx->nSamplesPerSec = pAACAudio->samples_per_sec;
		pWfx->nChannels      = pAACAudio->channels;
		pWfx->wFormatTag = WAVE_FORMAT_MPEG_LOAS;
		memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, pAACAudio->u.latm_header, extradata_size );
		cmt->SetSubtype( &MEDIASUBTYPE_MPEG_LATM_AAC );
		cmt->SetFormatType(&FORMAT_WaveFormatEx);
	} else
	{
		pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX  );
		memset( pWfx, 0, SIZEOF_WAVEFORMATEX  );
		pWfx->cbSize = 0;
		pWfx->nSamplesPerSec = pAACAudio->samples_per_sec;
		pWfx->nChannels      = pAACAudio->channels;

		cmt->SetSubtype( &MEDIASUBTYPE_AAC );
		cmt->SetFormatType(&FORMAT_WaveFormatEx);
	}
	return S_OK;
}



HRESULT CDemuxer::SetAC3AuidoMediaType( CMediaType *cmt, AC3_AUDIO *pAC3Audio )
{
	typedef	struct	
	{
		_WAVEFORMATEX   wfx;	
		unsigned char  BigEndian;
		unsigned char  bsid;
		unsigned char  lfeon;
		unsigned char  copyrightb;
		unsigned char  nAuxBitsCode;  /*  Aux bits per frame */	
	} TS_AC3WAV;

	TS_AC3WAV AC3Wav = {0};
	AC3Wav.wfx.cbSize =	sizeof(TS_AC3WAV) -	sizeof(_WAVEFORMATEX );
	AC3Wav.wfx.wFormatTag =	WAVE_FORMAT_DOLBY_AC3;
	AC3Wav.wfx.nSamplesPerSec = pAC3Audio->samples_per_sec;
	AC3Wav.wfx.nAvgBytesPerSec = pAC3Audio->avgbytes_per_sec;
	AC3Wav.wfx.nChannels = pAC3Audio->channels;
	AC3Wav.wfx.nBlockAlign = pAC3Audio->block_align;

	cmt->SetFormat((PBYTE)&AC3Wav, sizeof(TS_AC3WAV));
	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype(&MEDIASUBTYPE_DOLBY_AC3);
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}

HRESULT CDemuxer::SetTAC3AuidoMediaType( CMediaType *cmt, AC3_AUDIO *pAC3Audio )
{
	typedef	struct	
	{
		_WAVEFORMATEX   wfx;	
		unsigned char  BigEndian;
		unsigned char  bsid;
		unsigned char  lfeon;
		unsigned char  copyrightb;
		unsigned char  nAuxBitsCode;  /*  Aux bits per frame */	
	} TS_AC3WAV;

	TS_AC3WAV AC3Wav = {0};
	AC3Wav.wfx.cbSize =	sizeof(TS_AC3WAV) -	sizeof(_WAVEFORMATEX);
	AC3Wav.wfx.wFormatTag =	WAVE_FORMAT_DOLBY_AC3;
	AC3Wav.wfx.nSamplesPerSec = pAC3Audio->samples_per_sec;
	AC3Wav.wfx.nAvgBytesPerSec = pAC3Audio->avgbytes_per_sec;
	AC3Wav.wfx.nChannels = pAC3Audio->channels;
	AC3Wav.wfx.nBlockAlign = pAC3Audio->block_align; 
	cmt->SetFormat((PBYTE)&AC3Wav, sizeof(TS_AC3WAV));

	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype(&MEDIASUBTYPE_DOLBY_TRUEHD); 
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}

HRESULT CDemuxer::SetTrueHDAuidoMediaType( CMediaType *cmt, AC3_AUDIO *pAC3Audio )
{
	_WAVEFORMATEX   Wfx={0};
	Wfx.wFormatTag = WAVE_FORMAT_TRUE_HD;
	Wfx.cbSize = 0;//sizeof( _WAVEFORMATEX );
	Wfx.nSamplesPerSec = pAC3Audio->samples_per_sec;
	Wfx.nAvgBytesPerSec = 0;//pAC3Audio->avgbytes_per_sec;
	Wfx.nChannels = pAC3Audio->channels;
	Wfx.nBlockAlign = 32; //pcm_resolution < 2 ? 2 : 3;
	Wfx.wBitsPerSample = 24;
	cmt->SetFormat((PBYTE)&Wfx, SIZEOF_WAVEFORMATEX);


	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype(&MEDIASUBTYPE_DOLBY_TRUEHD2); 
	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	cmt->SetSampleSize (2216);

	return S_OK;
}

HRESULT CDemuxer::SetDTSAuidoMediaType( CMediaType *cmt, DTS_AUDIO *pDTSAudio )
{
	_WAVEFORMATEX   Wfx={0};
	Wfx.wFormatTag = WAVE_FORMAT_DTS;
	Wfx.cbSize = sizeof( _WAVEFORMATEX );
	Wfx.nSamplesPerSec = pDTSAudio->samples_per_sec;
	Wfx.nAvgBytesPerSec = pDTSAudio->avgbytes_per_sec;
	Wfx.nChannels = pDTSAudio->channels;
	Wfx.nBlockAlign = 0; //pcm_resolution < 2 ? 2 : 3;
	Wfx.wBitsPerSample = 16;
	cmt->SetFormat((PBYTE)&Wfx, SIZEOF_WAVEFORMATEX);

	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype(&CLSID_DTS_AUDIO);

	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	cmt->bTemporalCompression = TRUE;
	cmt->bFixedSizeSamples = FALSE;

	return S_OK;
}

HRESULT CDemuxer::SetDVDDTSAuidoMediaType( CMediaType *cmt, DTS_AUDIO *pDTSAudio )
{
	_WAVEFORMATEX   Wfx={0};
	Wfx.wFormatTag = WAVE_FORMAT_DTS;
	Wfx.cbSize = sizeof( _WAVEFORMATEX );
	Wfx.nSamplesPerSec = pDTSAudio->samples_per_sec;
	Wfx.nAvgBytesPerSec = pDTSAudio->avgbytes_per_sec;
	Wfx.nChannels = pDTSAudio->channels;
	Wfx.nBlockAlign = 0; //pcm_resolution < 2 ? 2 : 3;
	Wfx.wBitsPerSample = 16;
	cmt->SetFormat((PBYTE)&Wfx, SIZEOF_WAVEFORMATEX);

	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype(&WAVE_FORMAT_DVD_DTS);

	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	cmt->bTemporalCompression = TRUE;
	cmt->bFixedSizeSamples = FALSE;

	return S_OK;
}

HRESULT CDemuxer::SetMDTSAuidoMediaType( CMediaType *cmt, DTS_AUDIO *pDTSAudio )
{
	_WAVEFORMATEX   Wfx={0};
	Wfx.wFormatTag = WAVE_FORMAT_DTS;
	Wfx.cbSize = 0;//sizeof( _WAVEFORMATEX );
	Wfx.nSamplesPerSec = pDTSAudio->samples_per_sec;
	//Wfx.nAvgBytesPerSec = pDTSAudio->avgbytes_per_sec;
	Wfx.nChannels = pDTSAudio->channels;
	Wfx.nBlockAlign = 0; 
	Wfx.wBitsPerSample = 0;
	cmt->SetFormat((PBYTE)&Wfx, SIZEOF_WAVEFORMATEX);

	//cmt->SetType(&MEDIATYPE_MPEG2_PES);
	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype(&CLSID_DTS_MASTER_AUDIO);
	
	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	cmt->bTemporalCompression = FALSE;
	cmt->bFixedSizeSamples = FALSE;
	cmt->cbFormat = 18;

	return S_OK;
}

/*
HRESULT CDemuxer::SetLPCMAuidoMediaType( CMediaType *cmt, LPCM_AUDIO *pLPCMAudio )
{
	_WAVEFORMATEX   Wfx={0};
	Wfx.cbSize = 0;//sizeof( _WAVEFORMATEX );
	Wfx.nSamplesPerSec = pLPCMAudio->samples_per_sec;
	Wfx.nAvgBytesPerSec = pLPCMAudio->avgbytes_per_sec;
	Wfx.nChannels = pLPCMAudio->channels;
	Wfx.nBlockAlign = 4;//pLPCMAudio->block_align;
	Wfx.wBitsPerSample = pLPCMAudio->bits_per_sample;
	Wfx.wFormatTag = WAVE_FORMAT_PCM;
	cmt->SetFormat((PBYTE)&Wfx, SIZEOF_WAVEFORMATEX);

	//cmt->SetType(&MEDIATYPE_Audio);
	cmt->SetType(&MEDIATYPE_MPEG2_PES);
	cmt->SetSubtype(&MEDIASUBTYPE_DVD_LPCM_AUDIO);
	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	//cmt->SetType(&MEDIATYPE_Audio);
	//cmt->SetSubtype(&MEDIASUBTYPE_PCM);
	//cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}
*/


HRESULT CDemuxer::SetLPCMAuidoMediaType( CMediaType *cmt, LPCM_AUDIO *pLPCMAudio )
{
	typedef	struct	
	{
		unsigned short        wFormatTag;        //  format type 
		unsigned short        nChannels;         //  number of channels (i.e. mono, stereo...) 
		unsigned long         nSamplesPerSec;    //  sample rate 
		unsigned long         nAvgBytesPerSec;   //  for buffer estimation 
		unsigned short        nBlockAlign;       //  block size of data 
		unsigned short        wBitsPerSample;    //  number of bits per sample of mono data 
		unsigned short        cbSize;            //  the count in bytes of the size of 

		unsigned short   wValidBitsPerSample;
		unsigned long   dwChannelMask;
		GUID            SubFormat;
	} _PCM_WAV;

	_PCM_WAV pcm={0};
	pcm.wFormatTag = WAVE_FORMAT_EXTENSIBLE;
	pcm.cbSize = 22;//sizeof( _WAVEFORMATEX );
	pcm.nSamplesPerSec = pLPCMAudio->samples_per_sec;
	pcm.nAvgBytesPerSec = pLPCMAudio->avgbytes_per_sec;
	pcm.nChannels = pLPCMAudio->channels;
	pcm.nBlockAlign = (pLPCMAudio->bits_per_sample/8)*pLPCMAudio->channels+
		(pLPCMAudio->bits_per_sample%8?pLPCMAudio->channels:0);
	pcm.wBitsPerSample = pLPCMAudio->bits_per_sample;
	pcm.wValidBitsPerSample = pLPCMAudio->bits_per_sample;
	pcm.dwChannelMask = pLPCMAudio->channel_cfg;
	pcm.SubFormat = KSDATAFORMAT_SUBTYPE_PCM;

	cmt->SetFormat((PBYTE)&pcm, sizeof( _PCM_WAV) );
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	cmt->SetType(&MEDIATYPE_Audio);
	cmt->SetSubtype(&MEDIASUBTYPE_PCM);

	return S_OK;
}


HRESULT CDemuxer::SetMpegAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio )
{
	typedef	struct	
	{
	    unsigned short  wFormatTag;         /* format type */
		unsigned short  nChannels;          /* number of channels (i.e. mono, stereo...) */
		unsigned long   nSamplesPerSec;     /* sample rate */
		unsigned long   nAvgBytesPerSec;    /* for buffer estimation */
		unsigned short  nBlockAlign;        /* block size of data */
		unsigned short  wBitsPerSample;     /* number of bits per sample of mono data */
		unsigned short  cbSize;             /* the count in bytes of the size of */

		unsigned short	fwHeadLayer;
		unsigned long	dwHeadBitrate;
		unsigned short	fwHeadMode;	
		unsigned short	fwHeadModeExt;
		unsigned short	wHeadEmphasis;
		unsigned short	fwHeadFlags;
		unsigned long	dwPTSLow;
		unsigned long	dwPTSHigh;
	} _MPEGWAV;
	typedef struct 
	{
		_MPEGWAV MpegWav;
		unsigned char extra[256];
	} _MPEGAUDIO;

	//TS_MPEGWAV   MpegWav={0};	
	_MPEGAUDIO MpegAudio={0};
	MpegAudio.MpegWav.cbSize = 22;//2*9;//sizeof( _WAVEFORMATEX )-2;
	MpegAudio.MpegWav.wFormatTag = WAVE_FORMAT_MPEG;
	MpegAudio.MpegWav.nSamplesPerSec = pMpegAudio->samples_per_sec;
	MpegAudio.MpegWav.nAvgBytesPerSec = pMpegAudio->avgbytes_per_sec;
	MpegAudio.MpegWav.nChannels = pMpegAudio->channels;
	MpegAudio.MpegWav.nBlockAlign = pMpegAudio->block_align;
	MpegAudio.MpegWav.wBitsPerSample = 0;
	MpegAudio.MpegWav.dwPTSLow = 0;
	MpegAudio.MpegWav.dwPTSHigh = 0;
	MpegAudio.MpegWav.dwHeadBitrate = pMpegAudio->avgbytes_per_sec;//pMpegAudio->head_bitrate;
	MpegAudio.MpegWav.fwHeadFlags = pMpegAudio->head_flags;
	MpegAudio.MpegWav.fwHeadLayer = pMpegAudio->head_layer;
	MpegAudio.MpegWav.fwHeadMode = pMpegAudio->head_mode;
	MpegAudio.MpegWav.fwHeadModeExt = pMpegAudio->head_mode_ext;
	MpegAudio.MpegWav.wHeadEmphasis = pMpegAudio->head_emphasis;
	if ( pMpegAudio->head_layer == 1 )
		MpegAudio.MpegWav.fwHeadLayer = ACM_MPEG_LAYER1;
	else
	if ( pMpegAudio->head_layer == 2 )
		MpegAudio.MpegWav.fwHeadLayer = ACM_MPEG_LAYER2;
	else
	if ( pMpegAudio->head_layer == 3 )
		MpegAudio.MpegWav.fwHeadLayer = ACM_MPEG_LAYER3;
	
	cmt->SetFormat((PBYTE)&MpegAudio, sizeof(_MPEGWAV) );
	cmt->SetType(&MEDIATYPE_Audio);
	cmt->SetSubtype(&MEDIASUBTYPE_MPEG2_AUDIO); //ZQ.
	//cmt->SetSubtype(&MEDIASUBTYPE_MPEG1AudioPayload ); //
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}


////////////////////////////////////////////////

HRESULT CDemuxer::InitMPEG1Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.v.mpeg_video;

	DbgLog((LOG_TRACE, 3, TEXT(" Mpeg1 %s "), 
				pMpegVideo->profile == 5 ? TEXT("Simple Profile") :
				pMpegVideo->profile == 4 ? TEXT("Main Profile")   :
				pMpegVideo->profile == 3 ? TEXT("SNR Scalable Profile") :
				pMpegVideo->profile == 2 ? TEXT("Spatially Scalable Profile") :
				pMpegVideo->profile == 1 ? TEXT("High Profile") :
													TEXT("Unrecognized Profile") ));
	DbgLog((LOG_TRACE, 3, TEXT("Progressive=%d"), pMpegVideo->progressive));

	HRESULT hr = SetMpeg1VideoMediaType( &cmt,  pMpegVideo );
	if (FAILED(hr)) 
	{
		return hr;
	}
	m_dwVideoRate = pMpegVideo->bit_rate/8; // it's in bits/second

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", uPinIndex );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;

}

HRESULT CDemuxer::InitMPEG2Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.v.mpeg_video;

	DbgLog((LOG_TRACE, 3, TEXT( "Mpeg2 %s : %s"),
		pMpegVideo->profile == 5 ? TEXT("Simple Profile") :
		pMpegVideo->profile == 4 ? TEXT("Main Profile")   :
		pMpegVideo->profile == 3 ? TEXT("SNR Scalable Profile") :
		pMpegVideo->profile == 2 ? TEXT("Spatially Scalable Profile") :
		pMpegVideo->profile == 1 ? TEXT("High Profile") :
											TEXT("Unrecognized Profile"),
		pMpegVideo->level  == 10 ? TEXT("Low Level") :
		pMpegVideo->level  == 8  ? TEXT("Main Level") :
		pMpegVideo->level  == 6  ? TEXT("High 1440 Level") :
		pMpegVideo->level  == 4  ? TEXT("High Level") :	TEXT("Unrecognized Level")));
	
	DbgLog((LOG_TRACE, 3, TEXT("Progressive=%d"), pMpegVideo->progressive));

	HRESULT hr = SetMpeg2VideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	// set the video rate for seeking purposes
	m_dwVideoRate = pMpegVideo->bit_rate/8; // it's in bits/second

	m_dwVideoRate = pMpegVideo->bit_rate / 8;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", uPinIndex );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;

}

HRESULT CDemuxer::InitH264Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	H264_VIDEO *pH264Video = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.v.h264;

	DbgLog((LOG_TRACE, 3, TEXT(" MPEG H.264") ));
	HRESULT hr, hr1, hr2 = -1;
	hr1 = SetH264VideoMediaType4Cyberlink( &cmt, pH264Video );
	if (FAILED(hr1)) 
	{
		hr2 = SetH264VideoMediaType( &cmt, pH264Video );
		if ( FAILED( hr2 ) )
			return hr1;
	}

	m_dwVideoRate = pH264Video->cbr/8;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", uPinIndex );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );	
	if ( hr2 == -1 )
	{
		hr2 = SetH264VideoMediaType( &cmt, pH264Video );
		if ( !FAILED( hr2 ) )
			m_pFilter->AddMediaType( nTrackIndex, &cmt );
	}

	return hr;
}

HRESULT CDemuxer::InitVC1Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.v.mpeg_video;

	DbgLog((LOG_TRACE, 3, TEXT(" MPEG VC1") ));
	HRESULT hr = SetVC1VideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMpegVideo->bit_rate / 8;
	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "VC1%d", uPinIndex );
	else
		sprintf( szPinName, "VC1" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
	//if ( !FAILED ( SetWVC1VideoMediaType( &cmt, pMpegVideo ) ) )
	//	m_pFilter->AddMediaType( nTrackIndex, &cmt );
	SetWVC1VideoMediaType( &cmt, pMpegVideo );
	m_pFilter->AddMediaType( (BYTE)nTrackIndex, &cmt );

	return hr;
}


HRESULT CDemuxer::InitDixVStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.v.mpeg_video;

	DbgLog((LOG_TRACE, 3, TEXT(" MPEG DIVX") ));
	HRESULT hr = SetMpeg4VideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMpegVideo->bit_rate / 8;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", uPinIndex );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;
}

HRESULT CDemuxer::InitDixVMediaStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MEDIA_VIDEO *pMediaVideo = { 0 }; //to do
	//MPEG_VIDEO *pMpegVideo = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->private_data;

	DbgLog((LOG_TRACE, 3, TEXT(" SageTV DIVX Media") ));
	HRESULT hr = SetSageDixVMediaType( &cmt, pMediaVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMediaVideo->Video.dwBitRate / 8; // it's in bits/second

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", uPinIndex );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;
}


/////////////////////////////////////////////////////////
HRESULT CDemuxer::InitAC3Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	AC3_AUDIO *pAC3Audio = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.a.ac3;

	DbgLog((LOG_TRACE, 3, TEXT("AC3 Audio") ));

	HRESULT hr = SetAC3AuidoMediaType( &cmt, pAC3Audio );
	if ( FAILED(hr) ) 
		return hr;

	m_dwAudioRate = pAC3Audio->avgbytes_per_sec;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	if ( bSubPin )
		sprintf( szPinName, "AC3-%d", uPinIndex );
	else
		sprintf( szPinName, "AC3" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

/////////////////////////////////////////////////////////
HRESULT CDemuxer::InitEAC3Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	AC3_AUDIO *pAC3Audio = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.a.ac3;

	DbgLog((LOG_TRACE, 3, TEXT("EAC3 Audio") ));

	HRESULT hr = SetAC3AuidoMediaType( &cmt, pAC3Audio );
	if ( FAILED(hr) ) 
		return hr;

	m_dwAudioRate = pAC3Audio->avgbytes_per_sec;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	if ( bSubPin )
		sprintf( szPinName, "EAC3-%d", uPinIndex );
	else
		sprintf( szPinName, "EAC3" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

HRESULT CDemuxer::InitTAC3Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	AC3_AUDIO *pAC3Audio = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.a.ac3;

	DbgLog((LOG_TRACE, 3, TEXT("TrueHD AC3 Audio") ));

	HRESULT hr = SetTAC3AuidoMediaType( &cmt, pAC3Audio );
	if ( FAILED(hr) ) 
		return hr;

	m_dwAudioRate = pAC3Audio->avgbytes_per_sec;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	if ( bSubPin )
		sprintf( szPinName, "TrueHD-%d", uPinIndex );
	else
		sprintf( szPinName, "TrueHD" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr = m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
	
	if ( !FAILED ( SetTrueHDAuidoMediaType( &cmt, pAC3Audio ) ) )
		m_pFilter->AddMediaType( nTrackIndex, &cmt );

	return hr;

}

HRESULT CDemuxer::InitAACStream( int nTrackIndex, BOOL bSubPin )
{
	HRESULT hr;
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	AAC_AUDIO *pAACAudio = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.a.aac;

	DbgLog((LOG_TRACE, 3, TEXT("AAC Audio") ));
	hr = SetAACAuidoMediaType( &cmt, pAACAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pAACAudio->avgbytes_per_sec;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	if ( bSubPin )
		sprintf( szPinName, "AAC%d", uPinIndex );
	else
		sprintf( szPinName, "AAC" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr = m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	if ( !FAILED ( SetAACAuidoMediaType2( &cmt, pAACAudio ) ) )
		m_pFilter->AddMediaType( nTrackIndex, &cmt );

	return hr;
}

HRESULT CDemuxer::InitDTSStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	DTS_AUDIO *pDTSAudio = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.a.dts;

	DbgLog((LOG_TRACE, 3, TEXT("DTS Audio") ));
	HRESULT hr = SetDTSAuidoMediaType( &cmt, pDTSAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pDTSAudio->avgbytes_per_sec;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	if ( bSubPin )
		sprintf( szPinName, "DTS%d", uPinIndex );
	else
		sprintf( szPinName, "DTS" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr = m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
	if (!FAILED( SetDVDDTSAuidoMediaType( &cmt, pDTSAudio ) )) 
		m_pFilter->AddMediaType( nTrackIndex, &cmt );
	return hr;
}

HRESULT CDemuxer::InitMDTSStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	DTS_AUDIO *pDTSAudio = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.a.dts;

	DbgLog((LOG_TRACE, 3, TEXT("DTS MASTER Audio") ));
	HRESULT hr = SetMDTSAuidoMediaType( &cmt, pDTSAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pDTSAudio->avgbytes_per_sec;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	if ( bSubPin )
		sprintf( szPinName, "DTS-M%d", uPinIndex );
	else
		sprintf( szPinName, "DTS-M" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr = m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
	if (!FAILED( SetDVDDTSAuidoMediaType( &cmt, pDTSAudio ) )) 
		m_pFilter->AddMediaType( nTrackIndex, &cmt );

	return hr;
}

HRESULT CDemuxer::InitLPCMStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	LPCM_AUDIO *pLPCMAudio = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.a.lpcm;

	DbgLog((LOG_TRACE, 3, TEXT("LPCM Audio") ));
	HRESULT hr = SetLPCMAuidoMediaType( &cmt, pLPCMAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pLPCMAudio->avgbytes_per_sec;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	if ( bSubPin )
		sprintf( szPinName, "PCM%d", uPinIndex );
	else
		sprintf( szPinName, "PCM" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

HRESULT CDemuxer::InitMpegAudioStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_AUDIO *pMpegAudio = &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->d.a.mpeg_audio;

	DbgLog((LOG_TRACE, 3, TEXT("MPEG Audio") ));
	HRESULT hr = SetMpegAuidoMediaType( &cmt, pMpegAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pMpegAudio->avgbytes_per_sec;

	BYTE uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	BYTE uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	BYTE uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;

	if ( bSubPin )
		sprintf( szPinName, "Audio%d", uPinIndex );
	else
		sprintf( szPinName, "Audio" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}


/////////////////////////////////////////////////////////
/*  Initialize a stream */
HRESULT CDemuxer::InitStream( int nTrackIndex )
{
    UCHAR uStreamId, uContentType, uPinIndex;
	int nMainVideo, nMainAudio;
	BOOL bSubPin;
	nMainVideo = ((DEMUXER*)m_pDemuxer)->tracks[0]->main_video_index;
	nMainAudio = ((DEMUXER*)m_pDemuxer)->tracks[0]->main_audio_index;
	bSubPin = nMainVideo != nTrackIndex && nMainAudio != nTrackIndex;

	uContentType = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->content_type;
	uStreamId = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].es_elmnt->pes.stream_id;
	uPinIndex = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->stream_index;
	DbgLog((LOG_TRACE, 2, TEXT("InitStream called for track:%d streamID=0x%x pinIndex=%d contentType=%d"), 
				                   nTrackIndex , uStreamId, uPinIndex, uContentType ));

	m_pTrackInf[ nTrackIndex ].stream_type = uContentType;
	m_pTrackInf[ nTrackIndex ].stream_id = uStreamId;
	m_pTrackInf[ nTrackIndex ].pin_index = uPinIndex ;

    /*  Do nothing if we've already seen this one */
    if ( NULL != m_pFilter->FindOutputPin( uStreamId, uPinIndex ) ) 
	{
		DbgLog((LOG_TRACE, 2, TEXT("Steam is already initialized for track:%d"), nTrackIndex ));
        return E_FAIL;
	}

	DWORD dwFormatFourCC = ((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrackIndex].av_elmnt->format_fourcc;
	HRESULT hr = E_FAIL;
	
	if ( dwFormatFourCC == SAGE_FOURCC( "MPGV" ) )
	{
		hr = InitMPEG2Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "MP1V" ) )
	{
		hr = InitMPEG1Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "H264" ) )
	{
		hr = InitH264Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "VC1 " ) )
	{
		hr = InitVC1Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "MP4V" ) )
	{
		hr = InitDixVStream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AC3 " ) )
	{
		if ( m_dwAudioTypeEnableMask & AC3_ENABLE )
			hr = InitAC3Stream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("AC3 Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "MPGA" ) )
	{
		if ( m_dwAudioTypeEnableMask & MPEG_ENABLE )
			hr = InitMpegAudioStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("MPEG Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AAC " ) )
	{
		if ( m_dwAudioTypeEnableMask & AAC_ENABLE )
			hr = InitAACStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("AAC Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AACH" ) )
	{
		if ( m_dwAudioTypeEnableMask & AACH_ENABLE )
			hr = InitAACStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("AAC-HE Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "DTS " ) )
	{
		if ( m_dwAudioTypeEnableMask & DTS_ENABLE )
			hr = InitDTSStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("DTS Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "DTSM" ) )
	{
		if ( m_dwAudioTypeEnableMask & MDTS_ENABLE )
		{
			if ( m_bHDAudioFlag )
				hr = InitDTSStream( nTrackIndex, bSubPin );
			else
				hr = InitMDTSStream( nTrackIndex, bSubPin );
		}
		else
			DbgLog((LOG_TRACE, 3, TEXT("DTS-Master Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "LPCM" ) )
	{
		if ( m_dwAudioTypeEnableMask & LPCM_ENABLE )
			hr = InitLPCMStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("LPCM Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AC3E" ) ) //EAC3
	{
		if ( m_dwAudioTypeEnableMask & EAC3_ENABLE )
			hr = InitEAC3Stream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("EAC3 Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AC3T" ) ) //TrueHD
	{
		if ( m_dwAudioTypeEnableMask & TAC3_ENABLE )
		{
			if ( m_bHDAudioFlag )
				hr = InitAC3Stream( nTrackIndex, bSubPin );
			else
				hr = InitTAC3Stream( nTrackIndex, bSubPin );
		}
		else
			DbgLog((LOG_TRACE, 3, TEXT("TrueHD Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} 

	//hr = InitDixVMediaStream( nTrackIndex, bSubPin );
	if ( !FAILED(hr) )
	{
		m_pTrackInf[ nTrackIndex ].stream_ready = true;
		m_pTrackInf[ nTrackIndex ].data_frames = 0;
		DbgLog((LOG_TRACE, 2, TEXT("InitStream called sucessfull for track:%d"), nTrackIndex ));
	} else
		DbgLog((LOG_TRACE, 2, TEXT("InitStream called failed for track:%d"), nTrackIndex ));

    return hr; 
}

void CDemuxer::UpdateStream(  )
{
	int i, pin_num = 0;
	unsigned char  index_order[128]={0};
	TRACKS* pTracks = ((DEMUXER*)m_pDemuxer)->tracks[0];
	ASSERT( 128 >= pTracks->total_track );

	if ( m_nState != 2 )
	{
		DbgLog((LOG_TRACE, 2, TEXT("PSParser didn't post ready message, force sorting tracks!") ));
		CheckTracksAttr( pTracks , LanguageCode((unsigned char*)"eng") );
		TracksIndexing( pTracks );
		_display_av_inf( pTracks );
	}

	//keep pin in the index order by parser.
	/*
	for ( i = 0; i<pTracks->total_track; i++ )
	{
		int j;
		for ( j = 0; j<pTracks->total_track; j++ )
		{
			if ( pTracks->track[j].av_elmnt->stream_index == i )
			{
				index_order[i] = j;
				break;
			}
		}
		if ( j >= pTracks->total_track )
			index_order[i] = 0xff;
	}

	for ( i = 0; i<pTracks->total_track; i++ )
	{	
		int j;
		j = index_order[i];
		if ( j == 0xff ) continue;
		if ( pTracks->track[j].av_elmnt->format_fourcc )
		{
			HRESULT hr = InitStream( j );
			if ( !FAILED(hr) ) pin_num++;
		}
	}
	*/
	for ( i = 0; i<pTracks->total_track; i++ )
	{	
		if ( pTracks->track[i].av_elmnt->format_fourcc )
		{
			HRESULT hr = InitStream( i );
			if ( !FAILED(hr) ) pin_num++;
		}
	}


	if ( pin_num > 0 && m_nState < 2 ) 
	{
		DbgLog((LOG_TRACE, 2, TEXT("PSParser force ready!") ));
		m_nState = 2;
	}
	
}


LONGLONG  CDemuxer::ReadFileLastPTS( ) //static file return a last PTS; live file returns 0xc0000
{
	LONGLONG llLastPTS;

	CParseReaderFromAsync *pRdr = new CParseReaderFromAsync( m_pFilter->InputPin()->Reader() );

	llLastPTS = ReadFileLastPTS( pRdr );

	delete pRdr;
	return llLastPTS;
}

LONGLONG  CDemuxer::ReadFileFirstPTS( ) //static file return a first PTS; 
{
	LONGLONG llFirstPTS;

	CParseReaderFromAsync *pRdr = new CParseReaderFromAsync( m_pFilter->InputPin()->Reader() );

	llFirstPTS = ReadFileFirstPTS( pRdr );

	delete pRdr;
	return llFirstPTS;
}

LONGLONG  CDemuxer::ReadFileLastPTS(  CParseReader *pRdr )
{
	int i;
	const DWORD dwIncLen = 32*1024;  
    
	PBYTE pbData = new BYTE[dwIncLen];

    if (pbData == NULL) 
		return 0;

	LONGLONG rtByteLength;
	pRdr->AvailableLength(&rtByteLength);

	i = 1;
	while (  i<1000 )
	{
		LONGLONG llPos, llPTS;

		llPos =  rtByteLength - (REFERENCE_TIME)(i*dwIncLen);
		if ( llPos < 0 ) break;
		
		llPTS = ParsePTS( pRdr, pbData, &llPos, dwIncLen );
		if (  llPTS == -1 )
		{
			DbgLog((LOG_TRACE, 2, TEXT("ERROR: Last PTS not found..") ));
			break;
		} 

		if ( m_llUpdatedPTS )
			break;

		i++;
	}

	delete [] pbData;
	return m_llUpdatedPTS;

}

LONGLONG  CDemuxer::ReadFileFirstPTS(  CParseReader *pRdr )
{
	int i;
	const DWORD dwIncLen = 32*1024;  
    
	PBYTE pbData = new BYTE[dwIncLen];

    if (pbData == NULL) 
		return 0;
	LONGLONG rtByteLength;
	pRdr->AvailableLength(&rtByteLength);
	i = 0;
	while (  i<200 )
	{
		LONGLONG llPos, llPTS;

		llPos =  (REFERENCE_TIME)(i*dwIncLen);
		if ( llPos>= rtByteLength  ) break;
		
		llPTS = ParsePTS( pRdr, pbData, &llPos, dwIncLen );
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

	delete [] pbData;
	return m_llUpdatedPTS;
}

LONGLONG CDemuxer::Seek( LONGLONG rtSeekPTS, DWORD dwBlockSize  )
{
	const DWORD dwIncLen = 32*1024;  
	LONGLONG llLastPTS, llFirstPTS;
	LONGLONG llLastPos, llFirstPos;
	LONGLONG rtByteLength, llPos, llPTSPos;
	int k;
	bool bBadLastPTS = false;
	bool bFoundBadPTS = false;

	PBYTE pbData = new BYTE[dwIncLen];
	if (pbData == NULL) return 0;
	CParseReaderFromAsync *pRdr = new CParseReaderFromAsync( m_pFilter->InputPin()->Reader() );

	ASSERT( m_uEntry==0 );
	m_uEntry++;

	//get last PTS
	llLastPTS =  m_rtDur;
	rtByteLength = m_rtByteLength;
	llLastPos = rtByteLength;
	k = 0;
	if ( !m_bExactDur )
	{
		//update last PTS
		if ( SUCCEEDED( pRdr->AvailableLength(&rtByteLength) ))
		{
			if ( rtByteLength != m_rtByteLength )
			{
				LONGLONG llPTS;
				m_rtByteLength = rtByteLength;
				llPos = rtByteLength-dwIncLen;

				if ( llPos < 0 )
				{
					llLastPTS = GetStreamLastPTS( m_nPTSTrackIndex );
					llLastPTS = PTS2MT( llLastPTS );
				} else
				{
					while (	( llPTS = ParsePTS( pRdr, pbData, &llPos, dwIncLen ) ) == 0 )
					{
						if ( llPos < dwIncLen ) break;
						llPos -= dwIncLen;
						k++;
					}

					if (  llPos == 0 || llPTS == -1 )
					{
						DbgLog((LOG_TRACE, 2, TEXT("ERROR: Last PTS not found.") ));
						rtSeekPTS = -1; //give up seeking
						llPTSPos = 0;
					} else
					{
						rtByteLength = llLastPos = llPos;
						llLastPTS = llPTS;
						if ( llLastPTS )
							m_dSeekRatio = (double)(rtByteLength * UNITS/llLastPTS);
					}
				}
			}
		}
	}

	//find first PTS
	{
		llFirstPos=0;
		llFirstPTS = FindFirstPTS( pRdr, pbData,  dwIncLen, &llFirstPos, rtByteLength );
		if ( llFirstPTS <= 0 )
			llFirstPTS = 0;
			

		if ( llFirstPTS >= llLastPTS )
		{
			DbgLog((LOG_TRACE, 2, TEXT("====>ERROR: the Last PTS is small than the first PTS,  first PTS:%s last PTS:%s. "), 
				(LPCTSTR)Disp(llFirstPTS, CDISP_DEC), (LPCTSTR)Disp(llLastPTS, CDISP_DEC)   ));
			bBadLastPTS = true;
		}
	}


	double ratio;
	ratio = m_dSeekRatio;
	if ( ratio <20  )
		ratio = m_dwBitRate;

	if ( !bBadLastPTS && llLastPTS  ) 
		ratio = (long)(llLastPos * UNITS/llLastPTS);
	

	if ( FAILED( pRdr->AvailableLength(&rtByteLength) ))
		rtByteLength = m_rtByteLength;

	DbgLog((LOG_TRACE, 2, TEXT("====>Seeking PTS for Seek ratio:%d, Available bytes:%s, LastPTS:%s, FirstPTS:%s, SeekPTS:%s (%d)." ),
			(long)ratio, //(LPCTSTR)Disp(llLastPTS, CDISP_DEC), 
			(LPCTSTR)Disp(rtByteLength, CDISP_DEC),
			(LPCTSTR)Disp(llLastPTS, CDISP_DEC),
			(LPCTSTR)Disp(llFirstPTS, CDISP_DEC),
			(LPCTSTR)Disp(rtSeekPTS, CDISP_DEC), k ));

	//DbgLog((LOG_TRACE, 2, TEXT("====>Seeking PTS for Seek ratio:%d, Available bytes:%s, LastPTS:%s, FirstPTS:%s, SeekPTS:%s (%d)." ),
	//		(long)ratio, //(LPCTSTR)Disp(llLastPTS, CDISP_DEC), 
	//		(LPCTSTR)Disp(rtByteLength, CDISP_DEC),
	//		(LPCTSTR)Disp((CRefTime)llLastPTS),
	//		(LPCTSTR)Disp((CRefTime)llFirstPTS),
	//		(LPCTSTR)Disp((CRefTime)rtSeekPTS), k ));

	if ( rtSeekPTS == 0 )
	{
		llPTSPos = 0;
	} else
	if ( rtSeekPTS > m_rtDur || !bBadLastPTS && ( rtSeekPTS > llLastPTS ) )
	{
		llPTSPos = rtByteLength > dwBlockSize*8 ? rtByteLength-dwBlockSize*8 : 0;
		DbgLog((LOG_TRACE, 2, TEXT("====>ERROR: Seeking position out of range, use the last PTS:%s Pos:%s  SeekPos%s (8). "), 
			(LPCTSTR)Disp(llLastPTS, CDISP_DEC),  (LPCTSTR)Disp(llPos, CDISP_DEC), (LPCTSTR)Disp(llPTSPos, CDISP_DEC)  ));  
		m_llLastSeekPTS = llLastPTS;

	} else
	if ( rtSeekPTS < llFirstPTS )
	{
		llPTSPos = 0;
		DbgLog((LOG_TRACE, 2, TEXT("====>ERROR: Seeking  position out of range, use the first PTS:%s Pos:%s. "), 
		(LPCTSTR)Disp(llFirstPTS, CDISP_DEC),  (LPCTSTR)Disp(llPos, CDISP_DEC) ));
		m_llLastSeekPTS = llFirstPTS;

	} else
	if ( rtSeekPTS > 0 )
	{
		LONGLONG llPTS, llPos;
		LONGLONG llPTS1=0, llPTS2=0;
		LONGLONG llPos1=0, llPos2=0;
		double r;
		int dir=0;
		int m, n;  

		//llPos =  (LONGLONG)( rtByteLength *( 1.0* rtSeekPTS/llLastPTS )); 
		//DbgLog((LOG_TRACE, 2, TEXT("====>First Pos for Seek %s."), (LPCTSTR)Disp(llPos, CDISP_DEC) ));

		llPos = (LONGLONG)( rtSeekPTS * ratio )/UNITS;
		if ( rtByteLength-dwIncLen < llPos ) llPos = rtByteLength-dwIncLen;
		if ( 0 > llPos ) llPos = 0;
		
		DbgLog((LOG_TRACE, 2, TEXT("====>First Pos for Seek %s."), (LPCTSTR)Disp(llPos, CDISP_DEC) ));
		llPTSPos = llPos;
		r = 1.0;
		m = 0; n = 0;
		while ( ++m < 20 )
		{
			if ( llPos > rtByteLength ) llPos = rtByteLength;
			if ( llPos < 0 ) llPos = 0;

			if ( dir < 2 )
				llPTS = FindFirstPTS( pRdr, pbData,  dwIncLen, &llPos, rtByteLength );
			else
				llPTS = BackwardFindFirstPTS( pRdr, pbData,  dwIncLen, &llPos );

			if ( llPTS == -1 ) //reading file failed
			{
				DbgLog((LOG_TRACE, 2, TEXT("ERROR: didn't find PTS  pos:%s bytes:%s (%d).") ,
					(LPCTSTR)Disp(llPos, CDISP_DEC), (LPCTSTR)Disp(rtByteLength, CDISP_DEC), m ));
				break;
			} else
			if ( llPTS == 0 ) //pts not found
			{
				DbgLog((LOG_TRACE, 2, TEXT("ERROR: The PTS not found pos:%s bytes:%s direction:%d (%d)." ),
					(LPCTSTR)Disp(llPos, CDISP_DEC), (LPCTSTR)Disp(rtByteLength, CDISP_DEC), dir, m ));

				if ( llPos >= rtByteLength )
				{
					llPTS = llLastPTS;
					llPTSPos = llLastPos;
					DbgLog((LOG_TRACE, 2, TEXT("====>Force to use the last PTS:%s Pos:%s. "), 
						(LPCTSTR)Disp(llPTS, CDISP_DEC),  (LPCTSTR)Disp(llPos, CDISP_DEC) ));
				} else
				if ( llPos < dwIncLen )
				{
					llPTS = llFirstPTS;
					llPTSPos = llFirstPos;
					DbgLog((LOG_TRACE, 2, TEXT("====>Force to use the first PTS:%s Pos:%s. "), 
						(LPCTSTR)Disp(llPTS, CDISP_DEC),  (LPCTSTR)Disp(llPos, CDISP_DEC) ));
				}
				else
				{
					//reverse searhing
					if ( dir < 2 )
						llPTS = BackwardFindFirstPTS( pRdr, pbData,  dwIncLen, &llPos );
					else
						llPTS = FindFirstPTS( pRdr, pbData,  dwIncLen, &llPos, rtByteLength );
					if ( llPTS <= 0 ) //reading file failed
					{
						DbgLog((LOG_TRACE, 2, TEXT("ERROR: didn't found PTS  back forth, give up! pos:%s, bytes:%s (%d)."),
						 (LPCTSTR)Disp(llPos, CDISP_DEC), (LPCTSTR)Disp(rtByteLength, CDISP_DEC), m ));
						break;
					}
				}
			}

			
			if ( llPTS <= rtSeekPTS )
			{
				if ( (rtSeekPTS - llPTS )*10/UNITS < 1 ) //0.1 second, found target PTS
				{
					n = 0;
					llPTSPos = llPos;
					break; 
				}
	
				if ( llPTS1 == 0 )
				{
					llPos1 = llPos;
					llPTS1 = llPTS;
					r = 2;
				} else
				if ( llPTS1 < llPTS )
				{
					r = ( 1.0*(llPos-llPos1)/(llPTS-llPTS1)*( (rtSeekPTS+UNITS/10)-llPTS )/dwIncLen );
					llPos1 = llPos;
					llPTS1 = llPTS;
				} else
				if ( llPTS1 == llPTS )
				{
					if ( llPos1 < llPos )
						llPos1 = llPos;
					r = m;
				} else
				{
					if ( llPTS1-llPTS <= UNITS )
						r = 4.0;
					else
					if ( llPos1 < llPos )   //if  (llPTS1 > llPTS) and ( llPos1 < llPos) then there is a bad PTS in between
					{
						r = 4.0;
						bFoundBadPTS = true;
						DbgLog((LOG_TRACE, 2, TEXT("====>Catch a bad PTS. Pos1:%s Pos%s; PTS1:%s Pos:%s" ),
								(LPCTSTR)Disp(llPos1, CDISP_DEC), (LPCTSTR)Disp(llPos, CDISP_DEC),
								(LPCTSTR)Disp(llPTS1, CDISP_DEC), (LPCTSTR)Disp(llPTS, CDISP_DEC) ));
					} else

						r = ( 1.0*(llPos1-llPos)/(llPTS1-llPTS)*( (rtSeekPTS+UNITS/2)-llPTS )/dwIncLen );
				}

				//cal next position
				if ( llPos2 == 0 )
				{
					LONGLONG llNewPos = llPos + (LONGLONG)(dwIncLen * r );
					if ( !bBadLastPTS && llNewPos  >=  llLastPos  ) //a bad PTS causing wrong position
					{
						llPos = llLastPos;
					}
					else
					if ( llNewPos < llPos1 )
					{
						r = 3.0;
						llPos +=  (LONGLONG)(dwIncLen * r );
					} else
						llPos +=  (LONGLONG)(dwIncLen * r );

				} else
				{
					LONGLONG llNewPos = llPos + (LONGLONG)(dwIncLen * r );
					if ( llNewPos >=  llPos2 || llNewPos <= llPos1 ) //a bad PTS causing wrong position
					{
						llPos = ( llPos1 + llPos2 )/2;  //give up r, use binary searching
					} else
					{
						llPos = llNewPos;
					}
				}

				if ( llPos < 0 ) llPos = 0;

				dir = 1; //foward searching

			} else
			if ( llPTS > rtSeekPTS )
			{
				if ( ( llPTS - rtSeekPTS )*10/UNITS < 1 ) //0.1 second, found target PTS
				{
					n = 0;
					llPTSPos = llPos;
					break; 
				}

				if ( llPTS2 == 0 )
				{
					llPos2 = llPos;
					llPTS2 = llPTS;
					r = -2;
				} else
				if ( llPTS2 > llPTS )
				{
					r = -( 1.0*(llPos2-llPos)/(llPTS2-llPTS)*( llPTS-(rtSeekPTS-UNITS/10) )/dwIncLen );
					llPos2 = llPos;
					llPTS2 = llPTS;
				} else
				if ( llPTS2 == llPTS )
				{
					if ( llPos2 > llPos ) 
						llPos2 = llPos;
					r = -m;
				} else 
				{
					if ( llPTS-llPTS2 <= UNITS ) 
						r = -4.0;
					else
					if ( llPos2 > llPos )   //if  (llPTS2 <llPTS) and ( llPos2 > llPos) then there is a bad PTS in between
					{
						r = -4.0;
						bFoundBadPTS = true;
						DbgLog((LOG_TRACE, 2, TEXT("====>Catch a bad PTS. Pos2:%s Pos%s; PTS2:%s Pos:%s" ),
								(LPCTSTR)Disp(llPos2, CDISP_DEC), (LPCTSTR)Disp(llPos, CDISP_DEC),
								(LPCTSTR)Disp(llPTS2, CDISP_DEC), (LPCTSTR)Disp(llPTS, CDISP_DEC) ));
					} else
						r = -( 1.0*(llPos-llPos2)/(llPTS-llPTS2)*( llPTS-(rtSeekPTS-UNITS/2) )/dwIncLen );
				}


				//cal next position
				if ( llPos1 == 0 )
				{
					LONGLONG llNewPos = llPos + (LONGLONG)(dwIncLen * r );
					if ( llNewPos < 0  )
					{
						llPos = llFirstPos;
					}
					else
					if ( llNewPos > llPos2 )
					{
						r = -3;
						llPos += (LONGLONG)(dwIncLen * r );
					} else
						llPos += (LONGLONG)(dwIncLen * r );;
					
				}
				else
				{
					LONGLONG llNewPos = llPos + (LONGLONG)(dwIncLen * r );
					if ( llNewPos <= llPos1 || llNewPos >= llPos2 )
					{
						llPos = ( llPos1 + llPos2 )/2;
					} else
					{
						llPos = llNewPos;
					}
				}

				if ( llPos < 0 ) llPos = 0;
				dir = 2; //backward searching

			}

			//*************
			if ( 0 ) //tracking pts seeking
			  DbgLog((LOG_TRACE, 2, TEXT(" >>>>>%d Pos:%s Pos1:%s Pos2:%s  PTS:%s PTS1:%s PTS2:%s"), m,
				(LPCTSTR)Disp(llPos, CDISP_DEC), (LPCTSTR)Disp(llPos1, CDISP_DEC), (LPCTSTR)Disp(llPos2, CDISP_DEC),
				(LPCTSTR)Disp(llPTS, CDISP_DEC), (LPCTSTR)Disp(llPTS1, CDISP_DEC), (LPCTSTR)Disp(llPTS2, CDISP_DEC) ));
			//*************

			{ //check stream rate;
				if ( llPos1 && llPos2 && !bFoundBadPTS ) 
				{
					if ( ( llPos2 -llPos1 ) >= 2*dwIncLen && ( llPTS2-llPTS1 ) > 0  )
					{
						double  rate = 1.0*( llPos2 -llPos1 )*UNITS/( llPTS2-llPTS1 );
						if ( rate > 20*(1024*1024) || rate < 800 ) //20M bytes per second is impossiable
						{
							bFoundBadPTS = true;
							DbgLog((LOG_TRACE, 2, TEXT("====>Catch a bad PTS, between Pos1:%s-Pos2%s; PTS1:%s-PTS2:%s, rate=%d BPS" ),
							(LPCTSTR)Disp(llPos2, CDISP_DEC), (LPCTSTR)Disp(llPos2, CDISP_DEC),
							(LPCTSTR)Disp(llPTS2, CDISP_DEC), (LPCTSTR)Disp(llPTS1, CDISP_DEC), (int)rate ));
						}
					}
				}
			}


			if ( ( llPos2 > llPos1 ) && (( llPos2 - llPos1 ) <= dwBlockSize) )
			{
				llPTS = llPTS1;
				llPTSPos = llPos1;
				n = 1;
				break;
			}
			if ( ( llPTS2 > llPTS1 ) && ( llPTS2 - llPTS1 )*10/UNITS < 2 ) //0.1 second
			{
				llPTS = llPTS1;
				llPTSPos = llPos1;
				n = 2;
				break; 

			}
			if ( llPTS2 && ( llPTS2 - rtSeekPTS )*10/UNITS < 1 ) //0.1 second
			{
				llPTS = llPTS2;
				llPTSPos = llPos2;
				n = 3;
				break; 
			}

			if ( llPTS1 && ( rtSeekPTS - llPTS1 )*10/UNITS < 1 ) //0.1 second
			{
				llPTS = llPTS1;
				llPTSPos = llPos1;
				n = 4;
				break; 
			}
		}

		if ( !bBadLastPTS && llLastPTS  ) 
			ratio = (long)(llLastPos * UNITS/llLastPTS);
		else ratio = 0; 

		if ( m >= 20 )
		{
			DbgLog((LOG_TRACE, 2, TEXT("====>Target PTS is not found!!!, (bad pts found:%d)" ), bFoundBadPTS  ));
			if ( bFoundBadPTS )
			{
				LONGLONG llNewPos = llPos;
				if ( llPos1 - llFirstPos > 2*dwIncLen && llPTS1 - llFirstPTS > 100 )
				{
					llNewPos = llFirstPos + (rtSeekPTS-llFirstPTS)*(llPos1 - llFirstPos )/(llPTS1 - llFirstPTS );
					if ( llNewPos > llPos1 && llNewPos < llPos2 )
						llPos = llNewPos;
				} else
				if ( llPos2 - llFirstPos > 2*dwIncLen && llPTS2 - llFirstPTS > 100 )
				{
					llNewPos = llFirstPos + (rtSeekPTS-llFirstPTS)*(llPos2 - llFirstPos )/(llPTS2 - llFirstPTS );
					if ( llNewPos > llPos1 && llNewPos < llPos2 )
						llPos = llNewPos;
				} 

				if ( llPos != llNewPos )
				{
					llPTS = FindFirstPTS( pRdr, pbData,  dwIncLen, &llPos, rtByteLength );
					DbgLog((LOG_TRACE, 2, TEXT("====>Guess a new posution:%s PTS:%s" ), (LPCTSTR)Disp(llPos, CDISP_DEC), (LPCTSTR)Disp(llPTS, CDISP_DEC)  ));
				}
			}
		}

		DbgLog((LOG_TRACE, 2, TEXT("====>Seeking  PTS:%s, diff:%s ms, %s-%s(%s ms), Pos:%s-%s, (%d+%d,%d) ratio:%d."), 
			    (LPCTSTR)Disp((CRefTime)llPTS),
				(LPCTSTR)Disp( ( llPTS-rtSeekPTS )*1000/UNITS, CDISP_DEC),
				(LPCTSTR)Disp( llPTS1, CDISP_DEC), (LPCTSTR)Disp( llPTS2, CDISP_DEC),
				(LPCTSTR)Disp( ( llPTS2-llPTS1 )*1000/UNITS, CDISP_DEC),
				(LPCTSTR)Disp( llPos1, CDISP_DEC), (LPCTSTR)Disp( llPos2, CDISP_DEC), 
				 m, k, n, (long)ratio  ));
	
		m_dSeekRatio = ratio;
		m_llLastSeekPTS = llPTS;

	}

	delete pRdr;
	delete [] pbData;

	//enable output
	//env.start_output = old_start_output;

	m_uEntry--;

	DisableFastPTSParser();
	return llPTSPos;
}


//used by seek, offset PTS
LONGLONG CDemuxer::ParsePTS( CParseReader *pRdr, BYTE* pBuffer, LONGLONG* llPos, DWORD dwBytes )
{
	HRESULT hr;
	LONGLONG lStart;
	int Bytes, Size, BlockSize;
	char* pData;
	DWORD ReadBytes;
	LONGLONG llStart, llStop, llAdjust;

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
	hr = pRdr->SetPointer( lStart );
	if ( S_OK != hr )
	{
		DbgLog((LOG_TRACE, 2, TEXT("ERROR: SetPointer for reading data PTS hr=0x%x, pos:%s"), hr, (LPCTSTR)Disp(lStart, CDISP_DEC) ));
		return -1;
	}

	if ( S_OK == hr )
		hr = pRdr->ReadEx( pBuffer, dwBytes, &ReadBytes, &llStart, &llStop ); //ZQ.
		//hr = pRdr->Read( pBuffer, dwBytes );
	if ( S_OK != hr )
	{
		DbgLog((LOG_TRACE, 2, TEXT("ERROR: Read data for parsing PTS hr=0x%x, pos:%s, bytes:%d"), 
				     hr,  (LPCTSTR)Disp(lStart, CDISP_DEC), dwBytes ));

		LONGLONG llBytes = 0;
		hr = pRdr->AvailableLength( &llBytes );
		DbgLog((LOG_TRACE, 2, TEXT("ERROR: File Availabe Bytes: %s, bytes:%d, hr=0x%x"), (LPCTSTR)Disp(llBytes, CDISP_DEC), dwBytes, hr ));
		return -1;
	}

	ZeroDemuxer( );
	EnableFastPTSParser();
	m_llUpdatedPTS = 0;
	pData = (char*)pBuffer;
	Bytes = ReadBytes;  //ZQ.
	//Bytes = dwBytes;
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
		llAdjust =  GROUP_PTS_ADJUST( llStart, llStop ); //ZQ.
		m_llUpdatedPTS += llAdjust;		//ZQ.
		*llPos += dwBytes-Bytes;
		m_llUpdatedPTS = PTS_ROUND_UP( m_llUpdatedPTS, (ULONGLONG)m_llFirstPTS ); 
		if ( (LONGLONG)m_llUpdatedPTS < m_llFirstPTS ) return 1;  //ZQ.
		return  PTS2MT( m_llUpdatedPTS - m_llFirstPTS  )+1;
	}

	return 0;
}

//used by seek, offset PTS
LONGLONG CDemuxer::FindFirstPTS( CParseReader *pRdr, BYTE* pBuffer, DWORD dwBufferSize, LONGLONG* llPos, LONGLONG llMaxLen )
{
	HRESULT hr;
	LONGLONG llCurPos;
	BOOL  bFoundPTS;
	char* pData;
	int   Bytes, Size, BlockSize, ReadSize;
	DWORD ReadBytes;
	LONGLONG llStart, llStop, llAdjust;

	ASSERT( dwBufferSize > 0 );

	if ( IS_TS_TYPE( m_uInputStreamFormat) )
		BlockSize = TS_PACKET_LENGTH * 20;
	else
		BlockSize = 1024*2;

	llCurPos = *llPos;
	if ( llCurPos < 0 ) llCurPos = 0;
	if ( llCurPos >= llMaxLen )
		return 0;
	hr = pRdr->SetPointer( llCurPos );
	if ( S_OK != hr )
	{
		DbgLog((LOG_TRACE, 2, TEXT("ERROR: File seek to location %s"), (LPCTSTR)Disp(llCurPos, CDISP_DEC) ));
		return 0;
	}

	ZeroDemuxer( );
	EnableFastPTSParser();
	m_llUpdatedPTS = 0;
	

	bFoundPTS = FALSE;
	while( !bFoundPTS && llCurPos < llMaxLen )
	{
		ReadSize = ( llMaxLen-llCurPos > (LONGLONG) dwBufferSize )? dwBufferSize : (int)(llMaxLen-llCurPos) ;
		//ReadSize = min( (int)dwBufferSize, (int)(llMaxLen-llCurPos) );
		if ( ReadSize == 0 )
			break;
		hr = pRdr->ReadEx( pBuffer, dwBufferSize, &ReadBytes, &llStart, &llStop ); //ZQ.
		//hr = pRdr->Read( pBuffer, ReadSize );
		if ( S_OK != hr )
			break;

		pData = (char*)pBuffer;
		Bytes = ReadBytes;  //ZQ.
		//Bytes = ReadSize;
		while ( Bytes > 16 )
		{
			int nUsedBytes;
			Size = min( Bytes, BlockSize );

			nUsedBytes = PushData( (unsigned char*)pData, Size ); 
			if ( nUsedBytes == 0 ) nUsedBytes = Size;
			if ( m_llUpdatedPTS )
			{
				llCurPos += ReadSize - Bytes;
				bFoundPTS = TRUE;
				break;
			}
			pData += nUsedBytes;
			Bytes -= nUsedBytes;
		}

		if ( bFoundPTS ) 
			break;
		llCurPos += ReadSize;
	}

	DisableFastPTSParser( );
	if ( bFoundPTS )
	{
		llAdjust =  GROUP_PTS_ADJUST( llStart, llStop );  //ZQ.
		m_llUpdatedPTS += llAdjust;  //ZQ.
		*llPos = llCurPos;
		m_llUpdatedPTS = PTS_ROUND_UP( m_llUpdatedPTS, (ULONGLONG)m_llFirstPTS ); 
		if ( (LONGLONG)m_llUpdatedPTS < m_llFirstPTS ) return 1;
		return PTS2MT( m_llUpdatedPTS- m_llFirstPTS  ) +1;
	}

	return 0;
}

//used by seek, offset PTS
LONGLONG CDemuxer::BackwardFindFirstPTS( CParseReader *pRdr, BYTE* pBuffer, DWORD dwBufferSize, LONGLONG* llPos )
{
	HRESULT hr;
	LONGLONG lCurPos;
	BOOL  bFoundPTS;
	char* pData;
	int   Bytes, Size, BlockSize;
	DWORD ReadBytes;
	LONGLONG llStart, llStop, llAdjust;

	ASSERT( dwBufferSize > 0 );

	if ( IS_TS_TYPE( m_uInputStreamFormat ) )
		BlockSize = TS_PACKET_LENGTH * 20;
	else
		BlockSize = 1024*2;

	lCurPos = *llPos;
	
	EnableFastPTSParser();
	m_llUpdatedPTS = 0;
	bFoundPTS = FALSE;
	while( !bFoundPTS && lCurPos > 0 )    
	{
		lCurPos -= dwBufferSize;
		if ( lCurPos < 0 ) lCurPos = 0;
		hr = pRdr->SetPointer( lCurPos );
		if ( S_OK != hr )
		{
			DbgLog((LOG_TRACE, 2, TEXT("ERROR: File backward seek to location %s"), (LPCTSTR)Disp(lCurPos, CDISP_DEC) ));
			return 0;
		}

		hr = pRdr->ReadEx( pBuffer, dwBufferSize, &ReadBytes, &llStart, &llStop ); //ZQ.
		//hr = pRdr->Read( pBuffer, dwBufferSize );
		if ( S_OK != hr )
			break;

		ZeroDemuxer( );

		pData = (char*)pBuffer;
		Bytes = ReadBytes;   //ZQ.
		//Bytes = dwBufferSize;
		while ( Bytes > 16 )
		{
			int nUsedBytes;
			Size = min( Bytes, BlockSize );

			nUsedBytes = PushData( (unsigned char*)pData, Bytes );
			if ( nUsedBytes == 0 ) nUsedBytes = Size;

			if ( m_llUpdatedPTS )
			{
				lCurPos += dwBufferSize - Bytes;
				bFoundPTS = TRUE;
				break;
			}
			pData += nUsedBytes;
			Bytes -= nUsedBytes;
		}

	}

	DisableFastPTSParser();
	if ( bFoundPTS )
	{
		llAdjust =  GROUP_PTS_ADJUST( llStart, llStop );  //ZQ.
		m_llUpdatedPTS += llAdjust;  //ZQ.
		*llPos = lCurPos;
		m_llUpdatedPTS = PTS_ROUND_UP( m_llUpdatedPTS, (ULONGLONG)m_llFirstPTS );
		if ( (LONGLONG)m_llUpdatedPTS < m_llFirstPTS ) return 1;   //ZQ. 
		return PTS2MT(  m_llUpdatedPTS - m_llFirstPTS ) +1;
	}


	return 0;
}


void CDemuxer::UpdateLastPTS( ULONGLONG* pllFirstPTS, ULONGLONG* pllLastPTS )
{
	if ( pllFirstPTS != NULL )
		m_llFirstPTS = *pllFirstPTS;
	if ( pllLastPTS != NULL )
	{
		m_llLastPTS = *pllLastPTS;
		m_llLastPTS = PTS_ROUND_UP( m_llLastPTS, m_llFirstPTS ); 
		if ( !m_bExactDur &&  m_llLastPTS >= m_llFirstPTS )
			m_rtDur = PTS2MT( m_llLastPTS - m_llFirstPTS );
	}
}

void CDemuxer::SetStreamPTS( int nTrackIndex, ULONGLONG* pllPTS, BOOL bUpdateDuration )
{
	if ( bUpdateDuration )  
		CAutoLock ptsLock(&m_csRecentPts);

	if ( m_pTrackInf[nTrackIndex].firstPTS == 0 ) // || m_pTrackInf[nTrackIndex].firstPTS > *pllPTS ) //round up cause problem
	{
		m_pTrackInf[nTrackIndex].firstPTS = *pllPTS;
		m_pTrackInf[nTrackIndex].newPTS = 0;
	}

    m_pTrackInf[nTrackIndex].lastPTS = *pllPTS;

	if ( bUpdateDuration )
	{
		if ( m_llFirstPTS == 0 ) 
			m_llFirstPTS = *pllPTS;

		if ( !m_bExactDur && nTrackIndex == m_nPTSTrackIndex )
		{
			m_llLastPTS = *pllPTS;
			REFERENCE_TIME rtNewDur = PTS2MT( GetStreamLastPTS( nTrackIndex ) );
			if (  rtNewDur > m_rtDur )
				m_rtDur = rtNewDur;
		}
	}
}


LONGLONG CDemuxer::GetStreamPTS( int nTrackIndex )
{
	return m_pTrackInf[nTrackIndex].lastPTS;
}

LONGLONG  CDemuxer::GetStreamLastPTS( int nTrackIndex )
{
	LONGLONG lastPTS;
	CAutoLock ptsLock(&m_csRecentPts);
	if ( m_pTrackInf[nTrackIndex].lastPTS == 0 )
		return 0;

	if ( m_pTrackInf[nTrackIndex].lastPTS < m_llFirstPTS ) //&& m_llFirstPTS - m_pTrackInf[nTrackIndex].lastPTS > 900000 )
	{
		lastPTS = m_pTrackInf[nTrackIndex].lastPTS;
		lastPTS = PTS_ROUND_UP( lastPTS, m_llFirstPTS );

	} else
	{
		lastPTS = m_pTrackInf[nTrackIndex].lastPTS;
	}

	return (lastPTS-m_llFirstPTS) ;
}

REFERENCE_TIME CDemuxer::GetDuration( )
{
	return  m_rtDur;
}

void CDemuxer::ResetLastPTS(  )
{
	int i;
	for ( i = 0; i<MAX_DEMUX_TRACK_NUM; i++ )
		m_pTrackInf[ i ].lastPTS = 0;
}

/* Gives the last timestamp that was passed through the demux */
REFERENCE_TIME CDemuxer::GetLastTimestamp( )
{
	return  PTS2MT( GetStreamLastPTS( m_nPTSTrackIndex ) );
}


void CDemuxer::ResetDemuxerPTS( )
{
	int track_index;
	for ( track_index = 0; track_index<MAX_DEMUX_TRACK_NUM; track_index++ )
	{
		m_pTrackInf[track_index].firstPTS = 0;
		m_pTrackInf[track_index].newPTS = 0;
	}
	m_llFirstPTS = m_llLastPTS = 0;
	m_rtDur = 0;
}

void CDemuxer::ResetTracksPTS( )
{
	int index;
	for ( index = 0; index<MAX_DEMUX_TRACK_NUM ; index++ )
	{
		m_pTrackInf[index].firstPTS = 0;
		m_pTrackInf[index].newPTS = 0;
	}
	m_llFirstPTS = m_llLastPTS = 0;
	m_rtDur = 0;
}

void CDemuxer::EnableFastPTSParser()
{
	if ( !m_bIsSagePVRData )
		SetupPESDump( (DEMUXER*)m_pDemuxer, PTSDataDumper, this );
	else
		SetupTSATSDump( (DEMUXER*)m_pDemuxer, ATSDataDumper, this );
}

void CDemuxer::DisableFastPTSParser()
{
	if ( !m_bIsSagePVRData )
		SetupPESDump( (DEMUXER*)m_pDemuxer, NULL, NULL );
	else
		SetupTSATSDump( (DEMUXER*)m_pDemuxer, NULL, NULL );
}

void CDemuxer::ZeroDemuxer( )
{
	//m_nRemainBytes = 0;
	//m_nExpectedBytes = 0;	
	SetZeroDemux( (DEMUXER*)m_pDemuxer );
}

void CDemuxer::DemuxerReset( )
{
	//m_nRemainBytes = 0;
	//m_nExpectedBytes = 0;	
	ResetDemuxer( (DEMUXER*)m_pDemuxer, 0 );
}

void CDemuxer::DemuxerFlush( )
{
	FlushDemuxStream( (DEMUXER*)m_pDemuxer );
}

//int CDemuxer::PushData( unsigned char* pbData, int nBytes )
//{
//	int bytes, used_bytes;
//	unsigned char *p;
//
//	p = pbData;
//	bytes = nBytes;
//	if ( m_nExpectedBytes && m_nExpectedBytes+m_nRemainBytes <= sizeof( m_AlignBuf ) )
//	{
//		int expected_bytes2;
//		memcpy( m_AlignBuf+m_nRemainBytes, p, m_nExpectedBytes );
//		used_bytes = PushDemuxStreamData( (DEMUXER*)m_pDemuxer, m_AlignBuf, 
//			                              m_nRemainBytes+m_nExpectedBytes, &expected_bytes2 );
//		if ( expected_bytes2 == 0 )
//		{
//			p     +=  m_nExpectedBytes;
//			bytes -=  m_nExpectedBytes;
//		}
//	}
//	used_bytes = PushDemuxStreamData( (DEMUXER*)m_pDemuxer, p, bytes, &m_nExpectedBytes );
//	m_nRemainBytes = bytes - used_bytes;
//	if ( m_nRemainBytes > 0 && m_nRemainBytes + m_nExpectedBytes <= sizeof(m_AlignBuf) )
//	{
//		memcpy( m_AlignBuf, p+used_bytes, m_nRemainBytes );
//	}
//
//	return used_bytes;
//}

int CDemuxer::PushData( unsigned char* pbData, int nBytes )
{
	int nExpectedBytes;
	return PushDemuxStreamData( (DEMUXER*)m_pDemuxer, pbData, nBytes, &nExpectedBytes );
}

void CDemuxer::SetTSChannel( long nChannel )
{
	TUNE tune={0};
	tune.channel = (unsigned short)nChannel;
	SetupDemuxStreamTune( m_pDemuxer, &tune );
}


void CDemuxer::Stop( ) 
{
	m_bStopParser = TRUE;   
	DbgLog((LOG_TRACE, 2, TEXT("Parser Stop") ));
}

void CDemuxer::Run( )
{
	m_bStopParser = FALSE;
	ZeroDemuxer();
	for ( int i = 0; i<MAX_DEMUX_TRACK_NUM; i++ )
		ZeroTrack( i );
	DbgLog((LOG_TRACE, 2, TEXT("Parser Run") ));
}

HRESULT CDemuxer::Process(
    const BYTE * pbData,
    LONG lBytes,
	REFERENCE_TIME rtStart,
	BOOL bSync,
	LONG *plUsedBytes
)
{
	ASSERT( m_uEntry == 0 );
	m_uEntry++;

	int nExpectedBytes;

	*plUsedBytes = PushDemuxStreamData( (DEMUXER*)m_pDemuxer, 
						(unsigned char*)pbData, (int)lBytes, &nExpectedBytes );

	{
		LONG nTotalBytes = *plUsedBytes, nSendBytes;
		while ( nTotalBytes > 0 )
		{
			if ( nTotalBytes > 64*1024 )
				nSendBytes = 64*1024;
			 else
				nSendBytes = nTotalBytes;
			m_pFilter->SendSampleData(	0xff, pbData, nSendBytes, rtStart, bSync );
			bSync = 0;
			nTotalBytes -= nSendBytes;
		}
	}

	m_uEntry--;
    return S_OK;
}

LONGLONG CDemuxer::SetPTSOffset( LONGLONG llPTSOffset )
{
	return SetDemuxPTSOffset( (DEMUXER*)m_pDemuxer, llPTSOffset );
}

void CDemuxer::OpenConverter( int nTrack )
{
	if  (((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrack].av_elmnt->format_fourcc == SAGE_FOURCC( "LPCM" ) )
	{
		m_Converter[nTrack] = OpenLPCMConvert( &((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrack].av_elmnt->d.a.lpcm );
	}
}

void CDemuxer::CloseConverter( int nTrack )
{
	if  (((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrack].av_elmnt->format_fourcc == SAGE_FOURCC( "LPCM" ) )
	{
		if ( m_Converter[nTrack] )
		{
			CloseLPCMConvert( m_Converter[nTrack] );
			m_Converter[nTrack] = NULL;
		}
	}
}

void CDemuxer::ResetConverter( int nTrack )
{
	if  (((DEMUXER*)m_pDemuxer)->tracks[0]->track[nTrack].av_elmnt->format_fourcc == SAGE_FOURCC( "LPCM" ) )
	{
		if ( m_Converter[nTrack] )
		{
			ResetLPCMConvert( m_Converter[nTrack] );
		}
	}
}

int CDemuxer::ConvertInPlace( int bGroupStart, unsigned char* pData, int nBytes, unsigned short uTrackIndex, unsigned char** ppData )
{
	*ppData = pData;
	if  (((DEMUXER*)m_pDemuxer)->tracks[0]->track[uTrackIndex].av_elmnt->format_fourcc == SAGE_FOURCC( "LPCM" ) )
	{
		int n = LPCMConvert(  m_Converter[uTrackIndex], bGroupStart, pData, nBytes, pData, nBytes );
		return n;
	} 

	return nBytes;
}

void CDemuxer::DisableHDAudio( )
{
	m_bHDAudioFlag = TRUE;
	if ( m_pDemuxer != NULL )
		EnableAudioTSPriorityHack((DEMUXER*)m_pDemuxer); 
}

void CDemuxer::SetHDAudioFlag( )
{
	m_bHDAudioFlag = TRUE;
}

void CDemuxer::ConnectedTrack( int nTrack )
{
	if ( nTrack < 0 || nTrack >= MAX_DEMUX_TRACK_NUM )
		return;
	m_pTrackInf[nTrack].connected = 1;
	switch ( m_pTrackInf[nTrack].stream_type ) {
	case 1:	m_dwConnectedType |= VIDEO_CONNECTED; break;
	case 2:	m_dwConnectedType |= AUDIO_CONNECTED; break;   
	case 3: m_dwConnectedType |= SUBTITLE_CONNECTED; break;   
	}
	OpenConverter( nTrack );
}

void CDemuxer::DisconnectedTrack( int nTrack )
{
	int i, count;
	if ( nTrack < 0 || nTrack >= MAX_DEMUX_TRACK_NUM )
		return;
	m_pTrackInf[nTrack].connected = 0;
	count = 0;
	for ( i = 0; i<MAX_DEMUX_TRACK_NUM; i++ )
	{
		if ( m_pTrackInf[i].connected && m_pTrackInf[nTrack].stream_type == m_pTrackInf[i].stream_type )
			count++;
	}
	if ( count == 0 )
	{
		switch ( m_pTrackInf[nTrack].stream_type ) {
		case 1:	m_dwConnectedType &= ~VIDEO_CONNECTED; break;
		case 2:	m_dwConnectedType &= ~AUDIO_CONNECTED; break;   
		case 3: m_dwConnectedType &= ~SUBTITLE_CONNECTED; break;   
		}
	}
	CloseConverter( nTrack );
}

void CDemuxer::FlushTrack(  )
{
	int i;
	for ( i = 0; i<MAX_DEMUX_TRACK_NUM; i++ )
		ResetConverter( i );

	m_dwConnectedType &= 0x0f;
}

int CDemuxer::DTSCoreHeaderCheck( unsigned char* pData, int nBytes )
{
	if ( nBytes < 5 )
		return -1;
		
	/* 16 bits, Big Endian version of the bitstream */
	if( pData[0] == 0x7f && pData[1] == 0xfe &&
			 pData[2] == 0x80 && pData[3] == 0x01 && (pData[4] & 0xfc) == 0xfc )
	{
		return 1;
	}
	/* 16 bits, Little Endian version of the bitstream */
	if( pData[0] == 0xfe && pData[1] == 0x7f &&
			 pData[2] == 0x01 && pData[3] == 0x80 )
	{
		return 1;
	}
	/* 14 bits,Little endian version of the bitstream */
	if( pData[0] == 0xff && pData[1] == 0x1f &&
		pData[2] == 0x00 && pData[3] == 0xe8 &&
		(pData[4] & 0xf0) == 0xf0 && pData[5] == 0x07 )
	{
		return 1;
	}
	/* 14 bits, Big Endian version of the bitstream */
	if( pData[0] == 0x1f && pData[1] == 0xff &&
			 pData[2] == 0xe8 && pData[3] == 0x00 &&
			 pData[4] == 0x07 && (pData[5] & 0xf0) == 0xf0 )
	{
		return 1;	        
	}
	return 0;
}

//null packets carry ATS
int CDemuxer::CheckPVRTag( const unsigned char* pData, int nBytes )
{
	int i; 
	if ( nBytes < 14 ) return 0;
	for ( i = 0; i<nBytes; i++ )
	{
		if ( pData[i] == 0x47 && pData[i+1] == 0x1f && pData[i+2] == 0xff && pData[i+3] == 0x1f &&
			 pData[i+4] == 'Q' && pData[i+5] == 'A' && pData[i+6] == 'M' && pData[i+7] == 'E' &&
			 pData[i+10] == 0xff && pData[i+13] == 0xff )
			 return 2;
	}

	return 0;
}

////////////////////////////////////////////////////////////////////
static void _display_av_inf( TRACKS *pTracks )
{
	char avinf[1024];
	TracksInfo( pTracks, avinf, sizeof(avinf) );
	SageLog(( _LOG_TRACE, 3, TEXT("INPUT-AVINF|%s"), avinf ));
}


