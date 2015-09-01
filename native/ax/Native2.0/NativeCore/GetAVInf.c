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
#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"
#include "PSParser.h"
#include "ESAnalyzer.h"
#include "PSBuilder.h"
#include "TSBuilder.h"
#include "BlockBuffer.h"
#include "TSFilter.h"
#include <stdarg.h>
#include <stdio.h>
#ifdef WIN32
#include <io.h>
#include <share.h>
#endif
#include <string.h>
#include <stdlib.h>	
#include <memory.h>	
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include "Demuxer.h"
#include "GetAVInf.h"
#include "TSInfoParser.h"


#define PTS2MT( x ) ( (x)*PTS_UNITS )   //PTS to Media Time ( 0.1 ms )
#define MT2PTS( x ) ( (x)*UNITS_PTS )   //Media Time to PTS
#define ONE_DAY_PTS  ((LONGLONG)24*3600*MPEG_TIME_DIVISOR)
#define ONE_DAY_MT   ((LONGLONG)24*3600*UNITS)
#define PTS_OF_1SEC  (MPEG_TIME_DIVISOR) 
#define PTS_OF_1HOUR ((ULONGLONG)3600*MPEG_TIME_DIVISOR)
#define PTS_OF_100MS ((MPEG_TIME_DIVISOR/10) 
#define PTS_OF_3HOUR ((ULONGLONG)3*3600*MPEG_TIME_DIVISOR)
#ifdef Linux
#define MAX_PTS_VALUE ((ULONGLONG)0x00000001FFFFFFFFLL)
#else
#define MAX_PTS_VALUE ((ULONGLONG)0x00000001FFFFFFFF)
#endif
#define IS_PTS_ROUND_UP( LastPTS, FirstPTS ) (LastPTS + PTS_OF_3HOUR < FirstPTS &&  FirstPTS + PTS_OF_3HOUR > MAX_PTS_VALUE )
#define PTS_ROUND_UP( LastPTS, FirstPTS ) ((LastPTS + PTS_OF_3HOUR < FirstPTS &&  FirstPTS + PTS_OF_3HOUR > MAX_PTS_VALUE ) ? LastPTS+MAX_PTS_VALUE : LastPTS ) 

static void _display_av_inf( TRACKS *pTracks );
int long_long( ULONGLONG llVal, char* pBuffer, int nSize );
int time_stamp( LONGLONG llTime, char* pBuffer, int nSize );
char* long_long_hs( ULONGLONG llVal, char* pBuffer, int nSize );
int DetectSagePVRFileW( wchar_t* pFileName, PVR_META_INF* pMetaInf );
int DetectSagePVRFile( char* pFileName, PVR_META_INF* pMetaInf );
int DetectPVRRecordingFile( char* pFileName );
int DetectPVRRecordingFileW( wchar_t* pFileName );

static int AVInfDataDumper( void* pContext, void* pData, int nSize )
{
	AVINF* pAVInf = (AVINF*)pContext;
	DEMUXER *pDemuxer = pAVInf->demuxer;
	BLOCK_BUFFER *pBlockBuffer = (BLOCK_BUFFER*)pData;
	PES *pPES = &pBlockBuffer->pes;
	TRACK *pTrack;
	ULONGLONG pts = 0, pcr;
	if ( pBlockBuffer->slot_index != 0 ) return 0;
	pTrack = &pDemuxer->tracks[0]->track[pBlockBuffer->track_index];
	if ( pTrack->es_elmnt->content_type == VIDEO_DATA || pTrack->es_elmnt->content_type == AUDIO_DATA )
	{
		if ( pPES->has_dts )
			pts = pPES->dts;
		else
		if ( pPES->has_pts )
			pts = pPES->pts;


		if ( pts )
		{
			if ( pAVInf->first_pts == 0 )
				pAVInf->first_pts = pts;

			pAVInf->current_pts = pts;
			if ( pts > pAVInf->last_pts )
				pAVInf->last_pts = pts;
		}

		if ( pTrack->es_elmnt->content_type == VIDEO_DATA )
			pAVInf->video_data_block++;

		if ( pTrack->es_elmnt->content_type == AUDIO_DATA )
			pAVInf->audio_data_block++;
	}
	pcr = pBlockBuffer->start_cue;

	return 1;
}

static int AVInfMEssageDumper( void* pContext, void* pData, int nSize )
{
	AVINF* pAVInf = (AVINF*)pContext;
	DEMUXER *pDemuxer = pAVInf->demuxer;
	MESSAGE_DATA *message = (MESSAGE_DATA*)pData;
	if ( !strcmp( message->title, "STATUS" ) )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("*********** PARSER STATUS: %s ***********"), message->message ));
		if ( strstr( (char*)message->message, "STREAM START" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

			ResetBlockBuffer( pAVInf->demuxer, slot_index );
			if ( pAVInf->state == 0 )
				pAVInf->state = 1;

		} else
		if ( strstr( (char*)message->message, "STREAM FAILED" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

		} else
		if ( strstr( (char*)message->message, "STREAM READY" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
			if ( pAVInf->state == 1 ) 
			{
				TRACKS *tracks;
				ULONGLONG pos = DemuxUsedBytes( pDemuxer );
				tracks = GetTracks( pDemuxer, slot_index );

				CheckTracksAttr( tracks , (uint32_t)LanguageCode((uint8_t*)"eng") );
				TracksIndexing( tracks );
				_display_av_inf( tracks );

				if ( pAVInf->state < 2 )
					SageLog(( _LOG_ERROR, 3, TEXT("**** Find AVINF(pos:%d) ****" ), (uint32_t)pos ));

				pAVInf->state = 2;
			}
		} else
		if ( strstr( (char*)message->message, "STREAM END" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

			ResetBlockBuffer( pAVInf->demuxer, slot_index );
		
		} else
		{

		}
	}
	return 1;
}

static int PTSDataDumper( void* pContext, void* pData, int nSize )
{
	AVINF* pAVInf = (AVINF*)pContext;
	//DEMUXER *pDemuxer = pAVInf->demuxer;
	TRACK *pTrack = (TRACK *)pData;
	PES *pPES = &pTrack->es_elmnt->pes;
	ULONGLONG pts = 0;
	if ( pTrack->av_elmnt->content_type == VIDEO_DATA || pTrack->av_elmnt->content_type == AUDIO_DATA )
	{
		if ( pPES->has_dts )
			pts = pPES->dts;
		else
		if ( pPES->has_pts )
			pts = pPES->pts;


		if ( pts )
		{
			if ( pAVInf->first_pts == 0 )
				pAVInf->first_pts = pts;

			pAVInf->current_pts = pts;
			if ( pts > pAVInf->last_pts )
				pAVInf->last_pts = pts;
		}

		if ( pTrack->es_elmnt->content_type == VIDEO_DATA )
			pAVInf->video_data_block++;

		if ( pTrack->es_elmnt->content_type == AUDIO_DATA )
			pAVInf->audio_data_block++;
	}
	return 1;
}

int ATSDataDumper( void* pContext, void* pData, int nSize )
{
	AVINF* pAVInf = (AVINF*)pContext;
	//DEMUXER *pDemuxer   = (DEMUXER *)pAVInf->demuxer;
	ATS_TIME* ats_time = (ATS_TIME*)pData;
	ASSERT( nSize == sizeof(ATS_TIME) );
	if ( ats_time->type == 1 )
	{
		ULONGLONG pts = (ats_time->t.ats);
		if ( pts )
		{
			if ( pAVInf->first_pts == 0 )
				pAVInf->first_pts = pts;

			pAVInf->current_pts = pts;
			if ( pts > pAVInf->last_pts )
				pAVInf->last_pts = pts;
		}
	}
	return 1;
}


static int AVInfProgressCallback( void* pContext, void* pData, int nSize )
{
	AVINF* pAVInf = (AVINF*)pContext;
	//DEMUXER *pDemuxer = pAVInf->demuxer;
	//ULONGLONG *plTotalBytes = (ULONGLONG *)pData;
	ASSERT( sizeof(ULONGLONG) == nSize );

	//stream ready, stop parsing data.
	if ( pAVInf->state == 2 ) 
	{
		return 0;
	}

	return 1;
}

static int PTSInfProgressCallback( void* pContext, void* pData, int nSize )
{
	AVINF* pAVInf = (AVINF*)pContext;
	//DEMUXER *pDemuxer = pAVInf->demuxer;
	//ULONGLONG *plTotalBytes = (ULONGLONG *)pData;
	ASSERT( sizeof(ULONGLONG) == nSize );

	//stream ready, stop parsing data.
	if ( pAVInf->last_pts ) 
	{
		return 0;
	}

	return 1;
}

#define REWIND_BLOCK_SIZE 1024*128

int _GetAVFormat( void* pFileName, int bWcharFileName, uint32_t nCheckMaxiumSize, int bStreamData, 
			   int nRequestedTSChannel,   char* pFormatBuf, int nFormatSize, char* pDurationBuf, 
			   int nDurationBufSize, int* nTotalChannel )
{
	AVINF avinf={0};
	TUNE tune={0};
	uint32_t av_packets, check_size;
	int sagepvr_type, pvr_recording_type, file_type, av_present=0, encrypted_data=0;
	ULONGLONG last_pts;
	int ret, channel=0, i, track_num;
	PVR_META_INF MetaInf={0};

	if ( !bWcharFileName )
	{
		pvr_recording_type = DetectPVRRecordingFile( (char*)pFileName );
		if ( pvr_recording_type )
		{
			file_type = MPEG_TS;
			sagepvr_type = 0;
		} else
		{
			sagepvr_type = DetectSagePVRFile( (char*)pFileName, &MetaInf );
			if ( sagepvr_type != 0 )
			{
				file_type = MPEG_TS;
			} else
			{
				file_type = DetectFileType( (char*)pFileName );
			}
		}

	}
	else
	{
		pvr_recording_type = DetectPVRRecordingFileW( (wchar_t*)pFileName );
		if ( pvr_recording_type != 0 )
		{
			file_type = MPEG_TS;
			sagepvr_type = 0;
		} else
		{
			sagepvr_type = DetectSagePVRFileW( (wchar_t*)pFileName, &MetaInf );
			if ( sagepvr_type != 0 )
			{
				file_type = MPEG_TS;
			} else
			{
				file_type = DetectFileTypeW( (wchar_t*)pFileName );
			}
		}
	}
	if ( file_type == 0 )
		return -2;

	if ( pvr_recording_type )
	{
		SageLog(( _LOG_ERROR, 3, TEXT("It's a PVR network recording. type %d\n"), pvr_recording_type ));

	} else
	if ( sagepvr_type )
	{
		SageLog(( _LOG_ERROR, 3, TEXT("It's a SagePVR recording. SagePVR Meta:%d tsid:%d program:%d\n"), 
			                           MetaInf.state, MetaInf.tsid, MetaInf.program_num ));
	}

	avinf.max_check_bytes = nCheckMaxiumSize;

	if ( file_type == MPEG_M2TS ) 
		 track_num = MAX_TRACK_NUM *2;
	else
		 track_num = MAX_TRACK_NUM;
	avinf.demuxer = CreateDemuxer( file_type, track_num, ES_BUFFER_SIZE );

	SetupBlockDataDumper( avinf.demuxer, AVInfDataDumper, &avinf );
	SetupMessageDumper( avinf.demuxer, AVInfMEssageDumper, &avinf );

	if ( bStreamData )
		avinf.task = STREAM_AVINF;
	else
		avinf.task = FILE_AVINF;

	if ( pvr_recording_type == 2 )
	{
		//if nRequestedTSChannel = 0; auto search first vaild channel. 
		tune.tune_string_type = nRequestedTSChannel > 0 ? 1 : 0;  
		tune.channel = nRequestedTSChannel;
		tune.stream_format = ATSC_STREAM;
		tune.sub_format = CABLE;
	} else
	//if it's a SagePVR
	if ( sagepvr_type && MetaInf.state > 0 )
	{
		tune.tune_string_type = 15;
		tune.u.atsc.program_id = MetaInf.program_num;
		tune.stream_format = ATSC_STREAM;
	} else
	{
		//if nRequestedTSChannel = 0; auto search first vaild channel. 
		tune.tune_string_type = nRequestedTSChannel > 0 ? 1 : 0;  
		tune.u.unkn.data1 = nRequestedTSChannel;
		tune.stream_format = FREE_STREAM;
	}

	if ( IS_TS_TYPE( file_type ) )
	{
		DisableDemuxTSPSI( avinf.demuxer );
		DisablePTSFix( avinf.demuxer );
		avinf.demuxer->ts_parser->empty_sub_stream_threshold = nCheckMaxiumSize;
		//avinf.demuxer->ts_parser->naked_stream_threshold = nCheckMaxiumSize;
	} 

	if ( !bWcharFileName )
		ret = OpenFileSource( avinf.demuxer, (char*)pFileName, file_type,  &tune );
	else
		ret = OpenFileSourceW( avinf.demuxer, (wchar_t*)pFileName, file_type,  &tune );
	if ( !ret )
	{
		CloseFileSource( avinf.demuxer );
		ReleaseDemuxer( avinf.demuxer );
		return -3;
	}

	avinf.state = 0;

	//*** looping pump data from file into demuxer ***end
	PumpFileData( avinf.demuxer, avinf.max_check_bytes, AVInfProgressCallback, &avinf ); 

	if ( avinf.state == 1 ) //AVINF is not ready, check Attribute anyway
	{
		TRACKS *tracks;
		ULONGLONG pos = DemuxUsedBytes( avinf.demuxer );

		SageLog(( _LOG_ERROR, 3, TEXT("***********  STREAM IS NOT READY (at bytes:%d, pos:%d), CHECK AVAILABLE STREAMS ***********" ), 
			   avinf.max_check_bytes, (uint32_t)pos ));
		tracks = GetTracks( avinf.demuxer, 0 );

		CheckTracksAttr( tracks , (uint32_t)LanguageCode((uint8_t*)"eng") );
		TracksIndexing( tracks );
		_display_av_inf( tracks );

	} else
	if ( avinf.state == 2 ) 	//AVINF is ready
	{
	}

	av_present = (IsVideoDataPresent( avinf.demuxer, 0 ) || IsAudioDataPresent( avinf.demuxer, 0 ));
	encrypted_data = IsEncryptedData( avinf.demuxer, 0 ); //IsEncryptedTSChannel( avinf.demuxer, 0 );
	av_packets = GetInputVideoPacketCount( avinf.demuxer, 0 )+
				 GetInputAudioPacketCount( avinf.demuxer, 0 );
	avinf.last_pts = 0;
	LockDemuxTSPPmt( avinf.demuxer );
	

	// get duration to read last PTS
	SetupPESDump( avinf.demuxer, PTSDataDumper, &avinf );
	if ( sagepvr_type )
		SetupTSATSDump( avinf.demuxer, ATSDataDumper, &avinf );

	//read first PTS
	DemuxSourceSeekPos( avinf.demuxer, 0, SEEK_SET );
	last_pts = avinf.last_pts;
	avinf.last_pts = 0;
	check_size = 0;
	while ( av_present && !encrypted_data && avinf.last_pts == 0 ) 
	{
		int ret = PumpFileData( avinf.demuxer,  REWIND_BLOCK_SIZE, PTSInfProgressCallback, &avinf ); 
		if  ( ret == 0 )
			break;
		check_size += REWIND_BLOCK_SIZE;
		if ( check_size > nCheckMaxiumSize )
			break;
	}
	//read last PTS
	last_pts = avinf.last_pts;
	avinf.last_pts = 0;
	check_size = 0;
	i = 1;
	while ( av_present && !encrypted_data && avinf.last_pts == 0 ) 
	{
		int ret;
		ULONGLONG end_pos = DemuxSourceLength( avinf.demuxer );
		uint32_t bytes_for_pts = REWIND_BLOCK_SIZE * i;
		if ( end_pos > bytes_for_pts )
				end_pos -= bytes_for_pts;
		else 
		{
			avinf.last_pts = last_pts;
			break;
		}
		i++;
		//SetZeroDemux( avinf.demuxer );
		DemuxSourceSeekPos( avinf.demuxer, end_pos, SEEK_SET );
		ret = PumpFileData( avinf.demuxer,  REWIND_BLOCK_SIZE, PTSInfProgressCallback, &avinf ); 
		check_size += REWIND_BLOCK_SIZE;
		if ( check_size > nCheckMaxiumSize )
			break;

	}
	{
		char first_pts_buf[64], last_pts_buf[64];;
		long_long_hs( avinf.first_pts, first_pts_buf, sizeof(first_pts_buf) );
		long_long_hs( avinf.last_pts, last_pts_buf, sizeof(last_pts_buf) );
		SageLog(( _LOG_ERROR, 3, TEXT("*********** PTS: first:0x%s last:0x%s ***********" ), first_pts_buf, last_pts_buf ));
	}

	SetupPESDump( avinf.demuxer, NULL, NULL );
	{
		TRACKS *tracks;
		ULONGLONG dur=0;
		tracks = GetTracks( avinf.demuxer, 0 );
		if ( tracks->number_track  )
		{
			TracksInfo( tracks,  pFormatBuf, nFormatSize );
			//time_stamp_s( avinf.last_pts ,pDurationBuf, nDurationBufSize );
			if ( avinf.last_pts > avinf.first_pts )
				dur = (avinf.last_pts-avinf.first_pts); //*PTS_UNITS;
			else
			{
				ULONGLONG last_pts;
				last_pts = PTS_ROUND_UP( avinf.last_pts, avinf.first_pts );
				if ( last_pts < avinf.first_pts )
					dur = 0;
				else
					dur = last_pts - avinf.first_pts;
			}
			//time_stamp( dur, pDurationBuf, nDurationBufSize ); in format hh:mm:ss'nnn'xx
			dur = PTS2MT( dur );
			long_long_hs( dur, pDurationBuf, nDurationBufSize );
		} else
		{
			if ( encrypted_data  )
			{
				snprintf( pFormatBuf, nFormatSize, "ENCRYPTED;" );
				snprintf( pDurationBuf, nDurationBufSize, "00" );
			} else
			if ( av_packets == 0 || !av_present )
			{
				snprintf( pFormatBuf, nFormatSize, "NO-DATA;" );
				snprintf( pDurationBuf, nDurationBufSize, "00" );
			} else
			{
				int pos = 0;
				pos += snprintf( pFormatBuf, nFormatSize, "UNKNOWN STREAM;" );
				TracksInfo( tracks,  pFormatBuf+pos, nFormatSize-pos );
				snprintf( pDurationBuf, nDurationBufSize, "00" );
			}

		}

		*nTotalChannel = GetNumOfChannels( avinf.demuxer, 0 ); 
		channel = GetChannelNumber( avinf.demuxer, 0 ); 
		
	}

	CloseFileSource( avinf.demuxer );
	ReleaseDemuxer( avinf.demuxer );
	
	return channel;

}

int GetAVFormat(  char* pFileName, uint32_t nCheckMaxiumSize, int bStreamData, 
			   int nRequestedTSChannel,   char* pFormatBuf, int nFormatSize, char* pDurationBuf, 
			   int nDurationBufSize, int* nTotalChannel )
{
	return _GetAVFormat(  pFileName, 0, nCheckMaxiumSize, bStreamData, 
				  nRequestedTSChannel,   pFormatBuf, nFormatSize, pDurationBuf, 
				  nDurationBufSize, nTotalChannel );
}

int GetAVFormatW(  wchar_t* pFileName, uint32_t nCheckMaxiumSize, int bStreamData, 
			   int nRequestedTSChannel,   char* pFormatBuf, int nFormatSize, char* pDurationBuf, 
			   int nDurationBufSize, int* nTotalChannel )
{
	return _GetAVFormat(  pFileName, 1, nCheckMaxiumSize, bStreamData, 
				  nRequestedTSChannel,   pFormatBuf, nFormatSize, pDurationBuf, 
				  nDurationBufSize, nTotalChannel );
}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////  PTS Retreving /////////////////////////////////////////////////
int AVPtsPCRDumper( void* pContext, void* pData, int nSize )
{
	AVPTS* pAVPts = (AVPTS*)pContext;
	PCR_DATA  *pcr_data = (PCR_DATA *)pData;
	//SLOT      *slot     = pcr_data->container;
	char buf[80];
	int  buf_size = sizeof(buf), pos=0;
	ULONGLONG pcr = pcr_data->pcr/300;
	int bad_pcr;

	if ( (bad_pcr = pAVPts->current_pcr > pcr) )
		pAVPts->ill_pcr_count++;
	pAVPts->current_pcr = pcr;

	pos  += snprintf( buf+pos, buf_size-pos, "%04d\t", pAVPts->count );
	pos  += long_long( pAVPts->current_pcr, buf+pos, buf_size-pos );
	pos  += snprintf(  buf+pos, buf_size-pos, "\t SCR" );
	pos  += snprintf(  buf+pos, buf_size-pos, "\t\t\t" ); 
	pos  += time_stamp( pAVPts->current_pcr, buf+pos, buf_size-pos );
	if ( bad_pcr )
		pos  += snprintf(  buf+pos, buf_size-pos, "\tx" ); 
	pos  += snprintf(  buf+pos, buf_size-pos, "\n" ); 

	write( pAVPts->pts_file, buf, pos );
	pAVPts->count++;

	return 1;
}

int ATSPCRDumper( void* pContext, void* pData, int nSize )
{
	AVPTS* pAVPts = (AVPTS*)pContext;
	ATS_TIME  *ats_time = (ATS_TIME *)pData;
	//SLOT      *slot     = pcr_data->container;
	char buf[80];
	int  buf_size = sizeof(buf), pos=0;
	ULONGLONG ats = (ULONGLONG)MT2PTS((ULONGLONG)10000*ats_time->t.ats);


	pos  += snprintf( buf+pos, buf_size-pos, "%04d\t", pAVPts->count );
	pos  += long_long( ats, buf+pos, buf_size-pos );
	pos  += snprintf(  buf+pos, buf_size-pos, "\t ATS" );
	pos  += snprintf(  buf+pos, buf_size-pos, "\t\t\t" ); 
	pos  += time_stamp( ats, buf+pos, buf_size-pos );
	pos  += snprintf(  buf+pos, buf_size-pos, "\n" ); 

	write( pAVPts->pts_file, buf, pos );
	pAVPts->count++;

	return 1;
}

int AVPtsDataDumper( void* pContext, void* pData, int nSize )
{
	AVPTS* pAVPts = (AVPTS*)pContext;
	DEMUXER *pDemuxer = pAVPts->demuxer;
	BLOCK_BUFFER *pBlockBuffer = (BLOCK_BUFFER*)pData;
	PES *pPES = &pBlockBuffer->pes;
	TRACK *pTrack;
	ULONGLONG pts = 0;
	char buf[80];
	int  buf_size = sizeof(buf), pos=0;
	char buf1[80];
	int  buf_size1 = sizeof(buf1), pos1=0;
	char buf2[80];
	int  buf_size2 = sizeof(buf2), pos2=0;
	int bad_pts=0;
	char* data_type;

	if ( pBlockBuffer->slot_index != 0 ) return 0;
	pTrack = &pDemuxer->tracks[0]->track[pBlockBuffer->track_index];
	if ( pTrack->es_elmnt->content_type == VIDEO_DATA || 
		 pTrack->es_elmnt->content_type == AUDIO_DATA ||
		 pTrack->es_elmnt->content_type == SUBTITLE_DATA ||
		 pTrack->es_elmnt->content_type == PRIVAITE_DATA )
	{
		buf1[0] = 0x0;
		buf2[0] = 0x0;
		if ( pPES->has_pts )
		{
			pts = pPES->pts;
			//if ( ( bad_pts = pts < pAVPts->current_pts && pTrack->es_elmnt->content_type == AUDIO_DATA ))
			//	pAVPts->ill_pts_count++;

			if ( pTrack->es_elmnt->content_type == AUDIO_DATA )		data_type = " A"; else
			if ( pTrack->es_elmnt->content_type == VIDEO_DATA )		data_type = "V"; else
			if ( pTrack->es_elmnt->content_type == SUBTITLE_DATA )	data_type = " S"; else
			if ( pTrack->es_elmnt->content_type == PRIVAITE_DATA )	data_type = " P"; else
				data_type = "  ";

			pos1  += long_long( pts, buf1+pos1, buf_size1-pos1 );
			pos1  += snprintf(  buf1+pos1, buf_size-pos, "\t %s%d", data_type, pTrack->es_elmnt->channel_index  );

			pAVPts->current_pts = pts;
		}
		if ( pPES->has_dts )
		{
			pts = pPES->dts;
			if ( pts < pAVPts->current_dts )
			{
				pAVPts->ill_pts_count++;
				bad_pts = 1;
			}

			if ( pTrack->es_elmnt->content_type == AUDIO_DATA )		data_type = " A"; else
			if ( pTrack->es_elmnt->content_type == VIDEO_DATA )		data_type = "V"; else
			if ( pTrack->es_elmnt->content_type == SUBTITLE_DATA )	data_type = " S"; else
			if ( pTrack->es_elmnt->content_type == PRIVAITE_DATA )	data_type = " P"; else
				data_type = "  ";

			pos2  += long_long( pts, buf2+pos2, buf_size2-pos2 );
			pAVPts->current_dts = pts;
		}

		if ( pPES->has_pts )
		{
			LONGLONG delta = pAVPts->current_pts - pAVPts->current_pcr;
			if ( delta < 0 ) delta = -delta;
			if ( delta > MILLSECOND2PTS(PTS_FIX_THRESHOLD) )
				bad_pts = 2; 
		}

		if ( pos1+pos2 )
		{
			pos  += snprintf(  buf+pos, buf_size-pos, "%04d\t%s\t%s",   pAVPts->count, buf1, buf2 );
			
			if ( pos2 )
				pos  += snprintf(  buf+pos, buf_size-pos, "\t" ); 
			else
				pos  += snprintf(  buf+pos, buf_size-pos, "\t\t" ); 

			pos  += time_stamp( pts, buf+pos, buf_size-pos );

			if ( bad_pts == 1 )
				pos  += snprintf(  buf+pos, buf_size-pos, "\tX" ); 
			else
				pos  += snprintf(  buf+pos, buf_size-pos, "\t*" ); 

			pos  += snprintf(  buf+pos, buf_size-pos, "\n" ); 
			write( pAVPts->pts_file, buf, pos );
			pAVPts->count++;
		}

		if ( pts )
		{
			if ( pAVPts->first_pts == 0 )
			{
				pAVPts->first_pts = pts;
				pAVPts->smallest_pts = pts;
				pAVPts->bigest_pts = pts;
				pAVPts->last_pts = 0;
			}

			if ( pts > pAVPts->last_pts )
				pAVPts->last_pts = pts;

			if ( pAVPts->smallest_pts > pts )
			  pAVPts->smallest_pts = pts;

			if ( pAVPts->bigest_pts < pts )
				pAVPts->bigest_pts = pts;

			pAVPts->current_pts = pts;

		}

		if ( pTrack->es_elmnt->content_type == VIDEO_DATA )
			pAVPts->video_data_block++;

		if ( pTrack->es_elmnt->content_type == AUDIO_DATA )
			pAVPts->audio_data_block++;
	}


	return 1;
}

int AVPtsMEssageDumper( void* pContext, void* pData, int nSize )
{
	AVPTS* pAVPts = (AVPTS*)pContext;
	DEMUXER *pDemuxer = pAVPts->demuxer;
	MESSAGE_DATA *message = (MESSAGE_DATA*)pData;
	if ( !strcmp( message->title, "STATUS" ) )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("*********** PARSER STATUS: %s ***********"), message->message ));
		if ( strstr( (char*)message->message, "STREAM START" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

			ResetBlockBuffer( pAVPts->demuxer, slot_index );
			if ( pAVPts->state == 0 )
				pAVPts->state = 1;

		} else
		if ( strstr( (char*)message->message, "STREAM FAILED" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

		} else
		if ( strstr( (char*)message->message, "STREAM READY" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
			if ( pAVPts->state == 1 ) 
			{
				TRACKS *tracks;
				ULONGLONG pos = DemuxUsedBytes( pDemuxer );
				tracks = GetTracks( pDemuxer, slot_index );

				CheckTracksAttr( tracks , (uint32_t)LanguageCode((uint8_t*)"eng") );
				TracksIndexing( tracks );
				_display_av_inf( tracks );

				if ( pAVPts->state < 2 )
					SageLog(( _LOG_ERROR, 3, TEXT("**** Found AVPTS(pos:%d) ****" ), (uint32_t)pos ));

				pAVPts->state = 2;
			}
		} else
		if ( strstr( (char*)message->message, "STREAM END" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

			ResetBlockBuffer( pAVPts->demuxer, slot_index );

		} else
		{

		}
	}
	return 1;
}

int AVPtsProgressCallback( void* pContext, void* pData, int nSize )
{
	AVPTS* pAVPts = (AVPTS*)pContext;
	//DEMUXER *pDemuxer = pAVPts->demuxer;
	ULONGLONG *plTotalBytes = (ULONGLONG *)pData;
	int ratio=0;
	ASSERT( sizeof(ULONGLONG) == nSize );

	pAVPts->push_bytes += *plTotalBytes;
	if ( pAVPts->file_size );
		ratio = (int)( pAVPts->push_bytes*100/pAVPts->file_size );
	
	//ASSERT( ratio <= 100 );

	printf( "\r%d", ratio );
	return 1;
}


int GetAVPts( char* pFileName, char* pPTSFile, int nOption, uint32_t nCheckMaxiumSize, int nRequestedTSChannel, int* nTotalChannel )
{
	AVPTS avpts={0};
	TUNE tune={0};
	uint32_t av_packets;
	int file_type;
	int ret;

	file_type = DetectFileType( pFileName );
	if ( file_type == 0 )
		return 0;
#ifdef WIN32
	//avpts.pts_file = FOPEN( pPTSFile, _O_WRONLY|_O_CREAT|_O_TRUNC, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	avpts.pts_file = _sopen( pPTSFile, O_WRONLY|_O_CREAT|_O_TRUNC, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	if ( avpts.pts_file < 0 )
		avpts.pts_file = _wsopen( (wchar_t*)pPTSFile, O_WRONLY|_O_CREAT|_O_TRUNC, _SH_DENYNO , _S_IREAD|_S_IWRITE );
#else
	avpts.pts_file = open( pPTSFile, O_WRONLY|O_CREAT|O_TRUNC, 0666 );
#endif
	if ( avpts.pts_file < 0 )
	{
		SageLog(( _LOG_ERROR, 3, TEXT( "Can't open PTS log file :%s (errno:%d)." ), pPTSFile, errno ));
		return 0;
	}

	avpts.max_check_bytes = nCheckMaxiumSize;
	avpts.demuxer = CreateDemuxer( file_type, MAX_TRACK_NUM, ES_BUFFER_SIZE );

	SetupBlockDataDumper( avpts.demuxer, AVPtsDataDumper,	 &avpts );
	SetupMessageDumper( avpts.demuxer,   AVPtsMEssageDumper, &avpts );
	SetupPCRDumper( avpts.demuxer,		 AVPtsPCRDumper,	 &avpts );
	SetupTSATSDump( avpts.demuxer,		 ATSPCRDumper, &avpts );
	avpts.task = STREAM_AVINF;

	tune.tune_string_type = 1;
	tune.u.unkn.data1 = nRequestedTSChannel;
	tune.stream_format = FREE_STREAM;

	if ( IS_TS_TYPE( file_type ) )
	{
		DisableDemuxTSPSI( avpts.demuxer );  
		if ( nOption == 0 )
			DisablePTSFix( avpts.demuxer );
		avpts.demuxer->ts_parser->empty_sub_stream_threshold = nCheckMaxiumSize;
	} 

	ret = OpenFileSource( avpts.demuxer, pFileName, file_type,  &tune );
	if ( !ret )
	{
		CloseFileSource( avpts.demuxer );
		ReleaseDemuxer( avpts.demuxer );
		return 0;
	}

	avpts.file_size = DemuxSourceLength( avpts.demuxer );
	avpts.state = 0;

	{
		char buf[512];
		int  buf_size = sizeof(buf), pos=0;
		pos += snprintf( buf+pos, buf_size-pos, "SEQ\t      PTS\tTRACK\t      DTS\tPTS time format\t\tMark\n" );
		write( avpts.pts_file, buf, pos );
	}

	//*** looping pump data from file into demuxer ***
	PumpFileData( avpts.demuxer, avpts.max_check_bytes, AVPtsProgressCallback, &avpts ); 

	//AVPTS is ready, get duration to read last PTS
	if ( avpts.state == 2 ) 
	{
		av_packets = GetInputVideoPacketCount( avpts.demuxer, 0 )+
					 GetInputAudioPacketCount( avpts.demuxer, 0 );
		//LockDemuxTSPPmt( avpts.demuxer );
	}

	{  //statistic
		char buf[512];
		int  buf_size = sizeof(buf), pos=0;
		pos += snprintf( buf+pos, buf_size-pos, "First PTS:" );
		pos += long_long( avpts.first_pts, buf+pos, buf_size-pos );
		pos += snprintf( buf+pos, buf_size-pos, "\t(" );
		pos += time_stamp( avpts.first_pts, buf+pos, buf_size-pos );
		pos += snprintf( buf+pos, buf_size-pos, ")\n" );

		pos += snprintf( buf+pos, buf_size-pos, "Last  PTS:" );
		pos += long_long( avpts.last_pts, buf+pos, buf_size-pos );
		pos += snprintf( buf+pos, buf_size-pos, "\t(" );
		pos += time_stamp( avpts.last_pts, buf+pos, buf_size-pos );
		pos += snprintf( buf+pos, buf_size-pos, ")\n" );

		pos += snprintf( buf+pos, buf_size-pos, "Low   PTS:" );
		pos += long_long( avpts.smallest_pts, buf+pos, buf_size-pos );
		pos += snprintf( buf+pos, buf_size-pos, "\t(" );
		pos += time_stamp( avpts.smallest_pts, buf+pos, buf_size-pos );
		pos += snprintf( buf+pos, buf_size-pos, ")\n" );
		pos += snprintf( buf+pos, buf_size-pos, "High  PTS:" );
		pos += long_long( avpts.bigest_pts, buf+pos, buf_size-pos );
		pos += snprintf( buf+pos, buf_size-pos, "\t(" );
		pos += time_stamp( avpts.bigest_pts, buf+pos, buf_size-pos );
		pos += snprintf( buf+pos, buf_size-pos, ")\n" );

		pos += snprintf( buf+pos, buf_size-pos, "pcr changes:%d  pts changes:%d\n", avpts.ill_pcr_count, avpts.ill_pts_count );
		write( avpts.pts_file, buf, pos );

	}

	*nTotalChannel = GetNumOfChannels( avpts.demuxer, 0 ); 

	CloseFileSource( avpts.demuxer );
	ReleaseDemuxer( avpts.demuxer );
	FCLOSE ( avpts.pts_file );
	return 1;

}

////////////////////////////////////////////////////////////////////////////////
int long_long( ULONGLONG llVal, char* pBuffer, int nSize )
{
#ifdef WIN32	
	return snprintf( pBuffer, nSize, "%9I64u", llVal );
#endif 
#ifdef Linux
	return snprintf( pBuffer, nSize, "%9"PRId64"", llVal );
#endif 
	return 0;
}

int long_long_hex( ULONGLONG llVal, char* pBuffer, int nSize )
{
#ifdef WIN32	
	return snprintf( pBuffer, nSize, "%I64x", llVal );
#endif 
#ifdef Linux
	return snprintf( pBuffer, nSize, "%"PRIx64"", llVal );
#endif 
	return 0;
}

char* long_long_s( ULONGLONG llVal, char* pBuffer, int nSize )
{
	long_long( llVal, pBuffer, nSize );
	return pBuffer;
}

char* long_long_hs( ULONGLONG llVal, char* pBuffer, int nSize )
{
	long_long_hex( llVal, pBuffer, nSize );
	return pBuffer;
}

char* long_long_ss( ULONGLONG llVal )
{
	static char _tmp[64];
	long_long( llVal, _tmp, sizeof(_tmp) );
	return _tmp;
}

char* long_long_hss( ULONGLONG llVal )
{
	static char _tmp[64];
	long_long_hex( llVal, _tmp, sizeof(_tmp) );
	return _tmp;
}


ULONGLONG  hs_long_long( char* digital )
{
	ULONGLONG ll = 0;
	if ( digital == NULL )
		return 0;
	while ( *digital && (*digital == ' ' || *digital == '\t' ) )
		digital++;

	if ( *digital == '0' && *(digital+1) == 'x' )
		digital += 2;

	if ( *digital == 'x' )
		digital++;

	while ( *digital )
	{
		if ( *digital >= '0' && *digital <= '9' )
			ll = ll* 16 + *digital - '0';
		else
		if ( *digital == 'a' || *digital == 'A' )
			ll = ll*16 + 10;
		else
		if ( *digital == 'b' || *digital == 'B' )
			ll = ll*16 + 11;
		else
		if ( *digital == 'c' || *digital == 'C' )
			ll = ll*16 + 12;
		else
		if ( *digital == 'd' || *digital == 'D' )
			ll = ll*16 + 13;
		else
		if ( *digital == 'e' || *digital == 'E' )
			ll = ll*16 + 14;
		else
		if ( *digital == 'f' || *digital == 'F' )
			ll = ll*16 + 15;
		else
			break;

		digital++;
	}
	return ll;
}


int time_stamp( LONGLONG llTime, char* pBuffer, int nSize )
{
		char *p;
		int pos=0;
		LONGLONG div;
		p = pBuffer;

		if ( llTime < 0 )
		{
			llTime = -llTime;
			p[pos++]='-'; 
		}

		llTime = (llTime * PTS_UNITS);  //convert to standar time seconds;
		div = (ULONGLONG)(3600) * 10000000;
		if ( llTime >= div && nSize > pos ) {
			pos += snprintf( p+pos, nSize-pos,  TEXT("%d"), (uint32_t)(llTime / div) );
			llTime = llTime % div;
		} else
			p[pos++]='0';

		p[pos++]=':';
		div = (ULONGLONG)60 * 10000000;
		if (llTime >= div) {
			pos +=  snprintf( p+pos, nSize-pos,  TEXT("%02d"), (uint32_t)(llTime / div) );
			llTime = llTime % div;
		} else
			p[pos++]='0';
		p[pos++]=':';

		pos += snprintf( p+pos, nSize-pos, TEXT("%02d."), (uint32_t)llTime/10000000 );
		llTime = llTime % 10000000;
		pos += snprintf( p+pos, nSize-pos, TEXT("%3.3d'"), (uint32_t)(llTime/10000) );
		llTime = llTime % 10000;
		pos += snprintf( p+pos, nSize-pos, TEXT("%3.3d.%d"), (uint32_t)(llTime/10), (uint32_t)llTime%10 );

		return pos;
}

char* time_stamp_s( LONGLONG llTime, char* pBuffer, int nSize )
{
	time_stamp( llTime, pBuffer, nSize );
	return pBuffer;
}

char* time_stamp_ss( LONGLONG llTime )
{
	static char _tmp[40];
	time_stamp( llTime, _tmp, sizeof(_tmp) );
	return _tmp;
}


int ms_time_stamp( ULONGLONG llTime, char* pBuffer, int nSize )
{

		char *p;
		int pos=0;
		ULONGLONG div;
		p = pBuffer;

		//llTime = (llTime * PTS_UNITS);  //convert to standar time seconds;
		div = (ULONGLONG)(3600) * 10000000;
		if ( llTime >= div && nSize > pos ) {
			pos += snprintf( p+pos, nSize-pos,  TEXT("%d"), (uint32_t)(llTime / div) );
			llTime = llTime % div;
		} else
			p[pos++]='0';

		p[pos++]=':';
		div = (ULONGLONG)60 * 10000000;
		if (llTime >= div) {
			pos +=  snprintf( p+pos, nSize-pos,  TEXT("%02d"), (uint32_t)(llTime / div) );
			llTime = llTime % div;
		} else
			p[pos++]='0';
		p[pos++]=':';

		pos += snprintf( p+pos, nSize-pos, TEXT("%02d."), (uint32_t)llTime/10000000 );
		llTime = llTime % 10000000;
		pos += snprintf( p+pos, nSize-pos, TEXT("%3.3d'"), (uint32_t)(llTime/10000) );
		llTime = llTime % 10000;
		pos += snprintf( p+pos, nSize-pos, TEXT("%3.3d.%d"), (uint32_t)(llTime/10), (uint32_t)llTime%10 );

		return pos;
}


void _pts_log0( int type, ULONGLONG pts, ULONGLONG dts, ULONGLONG position, int index ) 
{
	static ULONGLONG last_pcr = 0, last_pos = 0; 
	char buf[1024]={0};
	int pos = 0 ,buf_size = sizeof( buf) ;
	double rate = 0;

	FILE *fp = fopen( "PTSTrace.log", "a+t" );
	if ( fp == NULL ) 
		return;

#ifdef WIN32
	pos += snprintf( buf+pos, buf_size-pos, "%9I64u", position );
#endif
#ifdef Linux
	pos += snprintf( buf+pos, buf_size-pos, "%9"PRIu64"", position );
#endif

	//buf[pos++] = '\t';
	pos += snprintf( buf+pos, buf_size-pos, "%6s", "" );

	if ( type >= 3 )
	{
		buf[pos++] = '\t';
		buf[pos++] = '\t';
	}

	pos += time_stamp( pts,  buf+pos, buf_size- pos  );
	pos += snprintf( buf+pos, buf_size-pos, " %s ",
		  type == 1 ? "PCR" : type == 2 ? "SCR" : type == 3 ? "PTS" : type == 4 ? "DTS" : "" );

	if ( type == 1 || type == 2 )
	{
		if ( last_pcr && ( pts-last_pcr > 4*300*9000 || last_pcr-pts < -4*300*9000 ) )
		{
			pos += snprintf( buf+pos, buf_size-pos, "**** FIX ***** " );
		}
		rate = 1.0*(pts-last_pcr)*188/(position-last_pos);
		pos += snprintf( buf+pos, buf_size-pos, " %8.2f ", rate );
		last_pos = position;
		last_pcr = pts;
	}

	if ( dts ) 
	{
		pos += time_stamp( dts,  buf+pos, buf_size- pos  );
		pos += snprintf( buf+pos, buf_size- pos, " DTS " );
	}

	if ( type >= 3 )
	{
		pos += snprintf( buf+pos, buf_size- pos, "track:%04x", index );
	}

	buf[pos++] = '\n';
	//buf[pos++] = 0x0;
	fwrite( buf, 1, pos, fp );
	fclose( fp );
	
}

void _pts_log1( int type, ULONGLONG pts, ULONGLONG dts, ULONGLONG position, int index ) 
{
	static ULONGLONG last_pcr = 0, last_pos = 0; 
	char buf[1024];
	int pos = 0 ,buf_size = sizeof( buf) ;
	double rate = 0;

	FILE *fp = fopen( "PTSTrace.log", "a+t" );
	if ( fp == NULL ) 
		return;

	pos += long_long( position, buf+pos, buf_size- pos );
	if ( type >= 3 )
		pos += snprintf( buf+pos, buf_size- pos, "(%d)", index );
	pos += snprintf( buf+pos, buf_size-pos, "%6s", "" );

	if ( type >= 3 )
	{
		buf[pos++] = '\t';
		buf[pos++] = '\t';
	}

	pos += snprintf( buf+pos, buf_size-pos, " %s:",
		  type == 1 ? "PCR" : type == 2 ? "SCR" : type == 3 ? "PTS" : type == 4 ? "DTS" : "" );
		  
#ifdef WIN32
	pos += snprintf( buf+pos, buf_size-pos, "%I64u ", pts );
#endif
#ifdef Linux
	pos += snprintf( buf+pos, buf_size-pos, "%"PRIu64" ", pts );
#endif
	if ( type == 1 || type == 2 )
	{
		if ( last_pcr && ( pts-last_pcr > 4*300*9000 || last_pcr-pts < -4*300*9000 ) )
		{
			pos += snprintf( buf+pos, buf_size-pos, "**** FIX ***** " );
		}
		rate = 1.0*(pts-last_pcr)*188/(position-last_pos);
		pos += snprintf( buf+pos, buf_size-pos, " %8.2f ", rate );
		last_pos = position;
		last_pcr = pts;
	}

	if ( dts ) 
	{
#ifdef WIN32
		pos += snprintf( buf+pos, buf_size- pos, "DTS:%I64u  ", dts );
#endif
#ifdef Linux
		pos += snprintf( buf+pos, buf_size- pos, "DTS:%"PRIu64"  ", dts );
#endif
	}

	buf[pos++] = '\n';
	fwrite( buf, 1, pos, fp );
	fclose( fp );
	
}

void _scr_log( ULONGLONG scr )
{
	char buf[1024];
	int pos = 0 ,buf_size = sizeof( buf) ;
	FILE *fp = fopen( "PTSTrace.log", "a+t" );
	if ( fp == NULL ) 
		return;

#ifdef WIN32
	pos += snprintf( buf+pos, buf_size-pos, "%10I64u \n", scr );
#endif
#ifdef Linux
	pos += snprintf( buf+pos, buf_size-pos, "%10"PRIu64" \n", scr );
#endif

	fwrite( buf, 1, pos, fp );

	fclose( fp );

}

#define BUF_SIZE (32*1024)
#define PVR_BUFFER_SIZE (BUF_SIZE-BUF_SIZE%188)
int DetectSagePVRFile( char* pFileName, PVR_META_INF* pMetaInf )
{
	char buf[PVR_BUFFER_SIZE];
	int fp;
	int bytes, count;
	int start_offset, next_block, pos = 0;
	int file_type=0;

#ifdef WIN32	
	//fp = FOPEN( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	fp = _sopen( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD );
#else
	fp  = open( pFileName, O_RDONLY );	
#endif	
	if ( fp < 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("file %s can't be open, errno:%d"), pFileName, errno ));
		return 0;
	}

	count = 0;
	while ( count++ < 4 )
	{
		bytes = (int)read( fp, buf, sizeof(buf) );
		if ( bytes <= 0 )
			break;
		file_type = CheckSagePVRData( (uint8_t*)buf, bytes );
		if ( file_type != 0 )
		{
			start_offset = SearchPVRMetaInf( &next_block, (uint8_t*)buf, bytes );
			break;
		}
		pos += bytes;
	}

	pos += start_offset;
	pMetaInf->state = 0;
	while ( file_type > 0 &&  count++ < 400 )
	{
		lseek( fp, pos, SEEK_SET );
		bytes = (int)read( fp, buf, 188 );
		if ( bytes <= 0 )
			break;
		start_offset = SearchPVRMetaInf( &next_block, (uint8_t*)buf, bytes );
		if ( start_offset >= 0 )
		{
			int ret = GetPVRMetaInf( pMetaInf, (uint8_t*)buf );
			if ( ret > 0 )
				break;
		}
		pos += next_block;
	}

	FCLOSE( fp );
	if ( file_type == 0)
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Unknown file format %s"), pFileName ));
	} else
	{
		SageLog(( _LOG_TRACE, 3, TEXT("SageTV PVR format %s"), pFileName ));
	}

	return file_type;

}

int DetectSagePVRFileW( wchar_t* pFileName, PVR_META_INF* pMetaInf )
{
	char buf[32*1024];
	int fp;
	int bytes, count;
	int start_offset, next_block, pos = 0;
	int file_type;

#ifdef WIN32	
	//fp = FOPEN( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	fp = _wsopen( (wchar_t*)pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD );
#else
	fp  = open( (char*)pFileName, O_RDONLY );
#endif	
	if ( fp < 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("file %s can't be open, errno:%d"), pFileName, errno ));
		return 0;
	}

	count = 0;
	while ( count++ < 10 )
	{
		bytes = (int)read( fp, buf, sizeof(buf) );
		if ( bytes <= 0 )
			break;
		file_type = CheckSagePVRData( (uint8_t*)buf, bytes );
		if ( file_type != 0 )
		{
			start_offset = SearchPVRMetaInf( &next_block, (uint8_t*)buf, bytes );
			break;
		}
		pos += bytes;

	}

	pos += start_offset;
	pMetaInf->state = 0;
	while ( file_type > 0 &&  count++ < 400 )
	{
		lseek( fp, pos, SEEK_SET );
		bytes = (int)read( fp, buf, 188 );
		if ( bytes <= 0 )
			break;
		start_offset = SearchPVRMetaInf( &next_block, (uint8_t*)buf, bytes );
		if ( start_offset >= 0 )
		{
			int ret = GetPVRMetaInf( pMetaInf, (uint8_t*)buf );
			if ( ret > 0 )
				break;
		}
		pos += next_block;
	}
	FCLOSE( fp );
	if ( file_type == 0)
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Unknown file format %s"), pFileName ));
	} else
		SageLog(( _LOG_TRACE, 3, TEXT("SageTV PVR %s"), pFileName ));

	return file_type;

}

//null packets carry ATS
static int CheckPVRTag( const uint8_t* pData, int nBytes )
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
int DetectPVRRecordingFile( char* pFileName )
{
	char buf[188*2];
	int fp;
	int bytes, file_type;

#ifdef WIN32	
	//fp = FOPEN( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	fp = _sopen( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD );
#else
	fp  = open( pFileName, O_RDONLY );	
#endif	
	if ( fp < 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("file %s can't be open, errno:%d"), pFileName, errno ));
		return 0;
	}

	lseek( fp, 0, SEEK_SET );
	bytes = (int)read( fp, buf, 188 );
	if ( bytes <= 0 )
		return 0;
	FCLOSE( fp );
	file_type = CheckPVRTag( (const uint8_t*)buf, 188 );
	return file_type;
}

int DetectPVRRecordingFileW( wchar_t* pFileName )
{
	char buf[188*2];
	int fp;
	int bytes, file_type;

#ifdef WIN32	
	//fp = FOPEN( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	fp = _wsopen( (wchar_t*)pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD );
#else
	fp  = open( (char*)pFileName, O_RDONLY );
#endif	
	if ( fp < 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("file %s can't be open, errno:%d"), pFileName, errno ));
		return 0;
	}

	lseek( fp, 0, SEEK_SET );
	bytes = (int)read( fp, buf, 188 );
	if ( bytes <= 0 )
		return 0;
	FCLOSE( fp );
	file_type = CheckPVRTag( (const uint8_t*)buf, 188 );
	return file_type;
}

static void _display_av_inf( TRACKS *pTracks )
{
	//int slot_index = 0;
	char avinf[1024];

	TracksInfo( pTracks, avinf, sizeof(avinf) );
	SageLog(( _LOG_TRACE, 3, TEXT("INPUT|%s"), avinf ));
}
