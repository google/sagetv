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
#include <stdarg.h>
#include <string.h>
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
#include "Demuxer.h"
#include "Remuxer.h"


//////////////////////////////////////////// DUMPER Section //////////////////////////////////////////
//static void FlushRemuxer( REMUXER* pRemuxer, int nSlot );
static void _output_statistic( REMUXER *pRemuxer );
static void _display_av_inf( TRACKS *pTracks );
void _input_statistic( DEMUXER *pDemuxer );
static void FlushRemuxer( REMUXER* pRemuxer, int nSlot );
void SetupRemuxOutput( TRACKS *pTrackIn, TRACKS * pTrackOut );
BLOCK_BUFFER* RequestBlockBuffer( DEMUXER *pDemuxer, TRACK* pTrack );
void ReturnBlockBuffer( DEMUXER *pDemuxer, BLOCK_BUFFER* pBlockBuffer );

/////////////////////////////////////////////////////////////////////////////////////////////////////////////

/////////////////////////////////// File Dumper///////////////////////////////////////////
//Dump data into a file
static int PSOutputDataFileDumper( void* pContext, uint8_t* pData, int nSize )
{
	REMUXER *pRemuxer = (REMUXER*)pContext;
	OUTPUT_DATA *pOutputData = (OUTPUT_DATA *)pData;
	//TRACK   *pTrack = (TRACK*)pOutputData->track;
	uint8_t* data_ptr = pOutputData->data_ptr;
	uint16_t bytes = pOutputData->bytes;

	if ( pRemuxer->output_file > 0 ) 
	{
		write( pRemuxer->output_file, data_ptr, bytes  );
	}

	return bytes;
}

#define TS_BLOCK_BUFFER_SIZE   (2*1024)
static int TSOutputDataFileDumper( void* pContext, uint8_t* pData, int nSize )
{
	REMUXER *pRemuxer = (REMUXER*)pContext;
	OUTPUT_DATA *pOutputData = (OUTPUT_DATA *)pData;
	//TRACK   *pTrack = (TRACK*)pOutputData->track;
	uint8_t* data_ptr = pOutputData->data_ptr;
	uint16_t bytes = pOutputData->bytes;

	if ( pRemuxer->output_file > 0 ) 
 	{
		write( pRemuxer->output_file, data_ptr, bytes  );
	}

	return bytes;
}

///////////////////////////////////////////////////////////////////////////////////////////////////


static void PostStatusMessage( REMUXER *pRemuxer, char* pMessageText )
{
	MESSAGE_DATA message={0};
	strncpy( message.title, "STATUS", sizeof(message.title) );
	message.message = (uint8_t*)pMessageText;
	message.message_length = (uint16_t)strlen(pMessageText)+1 ;
	message.buffer = (uint8_t*)pMessageText;
	message.buffer_length = message.message_length ;
	PostDemuxMessage( pRemuxer->demuxer, &message );
}

//////////////////////////////////////////////////////////////////////////////////


static REMUXER* CreateRemuxer( int nStreamType, int nTrackNum, int nESBlockSize )
{
	REMUXER* pRemuxer = NULL;
	//DEMUXER* pDemuxer = NULL;
	pRemuxer = SAGETV_MALLOC( sizeof(REMUXER) );
	pRemuxer->demuxer = CreateDemuxer( nStreamType, nTrackNum, nESBlockSize );
	return pRemuxer;
}

static void ReleaseRemuxer( REMUXER* pRemuxer )
{
	ReleaseDemuxer( pRemuxer->demuxer );
	SAGETV_FREE( pRemuxer );
}

static void ResetRemuxerAll( REMUXER* pRemuxer )
{
	int i;
	pRemuxer->state = 1;
	if ( pRemuxer->ps_builder != NULL )
		ResetPSBuilder( pRemuxer->ps_builder );
	if ( pRemuxer->ts_builder != NULL )
		ResetTSBuilder( pRemuxer->ts_builder );
	ResetDemuxerAll( pRemuxer->demuxer );
	for ( i = 0; i<MAX_PROGRAM_NUM; i++ )
		if ( pRemuxer->output_track[i] )
			ResetTracks( pRemuxer->output_track[i] );
}

static void ResetRemuxer( REMUXER* pRemuxer, int nSlot )
{
	if ( pRemuxer->ps_builder != NULL )
		ResetPSBuilder( pRemuxer->ps_builder );
	if ( pRemuxer->ts_builder != NULL )
		ResetTSBuilder( pRemuxer->ts_builder );
	ResetTracks( pRemuxer->output_track[nSlot] );
}

static void FlushRemuxer( REMUXER* pRemuxer, int nSlot )
{
	FlushDemuxer( pRemuxer->demuxer, nSlot );

	if ( pRemuxer->ps_builder != NULL )
		FlushEndOfCode( pRemuxer->ps_builder );
	if ( pRemuxer->ts_builder != NULL )
		FlushOutData( pRemuxer->ts_builder );
}

static void SetupMemAlloc(  REMUXER* pRemuxer, MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext )
{
	SetupMemAllocHook( pRemuxer->demuxer, pfnMemAlloc, pMemAllocContext );
}

///////////////////////////////////////////////////////////////////////////////////////

int CreateFileOutput( REMUXER *pRemuxer, char* pFileName, int nOutputFormat, int nMaxTrackNum )
{
	//pRemuxer->output_file = fopen( pFileName, "wb" );
#ifdef WIN32	
	//pRemuxer->output_file  = FOPEN( pFileName, _O_WRONLY|_O_BINARY|_O_CREAT, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	pRemuxer->output_file = _sopen( pFileName, O_WRONLY|O_CREAT|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
#else
	pRemuxer->output_file  = open( pFileName, O_WRONLY|O_CREAT, 0666 );
#endif	

	if ( pRemuxer->output_file > 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Output file %s"), pFileName ));
	} else
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Failed open output file %s, %s (errno:%d)"), pFileName, strerror(errno), errno ));
		return 0;
	}

	pRemuxer->output_track[0] = CreateTracks(nMaxTrackNum);
	pRemuxer->output_track[0]->track_type = nOutputFormat;
	if ( IS_PS_TYPE( nOutputFormat ) )
	{
		int build_header_in_buffer; //if es block has space to build a PACK+PES
		build_header_in_buffer = SourceIsTSType( pRemuxer->demuxer );
		pRemuxer->ps_builder = CreatePSBuilder( pRemuxer->output_track[0], ES_PACKET_SIZE, build_header_in_buffer );
		pRemuxer->ps_builder->dumper.stream_dumper = (DUMP)PSOutputDataFileDumper;
		pRemuxer->ps_builder->dumper.stream_dumper_context = pRemuxer;
	} else
	if ( IS_TS_TYPE(nOutputFormat) )
	{
		pRemuxer->ts_builder = CreateTSBuilder( pRemuxer->output_track[0], nOutputFormat );
		pRemuxer->ts_builder->dumper.stream_dumper = (DUMP)TSOutputDataFileDumper;
		pRemuxer->ts_builder->dumper.stream_dumper_context = pRemuxer;
	}
	return 1;
}

int CreateFileOutputW( REMUXER *pRemuxer, wchar_t* pFileName, int nOutputFormat, int nMaxTrackNum)
{
	//pRemuxer->output_file = fopen( pFileName, "wb" );
#ifdef WIN32	
	//pRemuxer->output_file  = FOPEN( pFileName, _O_WRONLY|_O_BINARY|_O_CREAT, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	pRemuxer->output_file = _wsopen( (wchar_t*)pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
#else
	pRemuxer->output_file  = open( (char*)pFileName, O_WRONLY|O_CREAT, 0666 );
#endif	

	if ( pRemuxer->output_file )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Output file %s"), pFileName ));
	} else
		return 0;

	pRemuxer->output_track[0] = CreateTracks(nMaxTrackNum);
	pRemuxer->output_track[0]->track_type = nOutputFormat;
	if ( IS_PS_TYPE( nOutputFormat ) )
	{
		int build_header_in_buffer; //if es block has space to build a PACK+PES
		build_header_in_buffer = SourceIsTSType( pRemuxer->demuxer );
		pRemuxer->ps_builder = CreatePSBuilder( pRemuxer->output_track[0], ES_PACKET_SIZE, build_header_in_buffer );
		pRemuxer->ps_builder->dumper.stream_dumper = (DUMP)PSOutputDataFileDumper;
		pRemuxer->ps_builder->dumper.stream_dumper_context = pRemuxer;
	} else
	if ( IS_TS_TYPE(nOutputFormat) )
	{
		pRemuxer->ts_builder = CreateTSBuilder( pRemuxer->output_track[0], nOutputFormat );
		pRemuxer->ts_builder->dumper.stream_dumper = (DUMP)TSOutputDataFileDumper;
		pRemuxer->ts_builder->dumper.stream_dumper_context = pRemuxer;
	}
	return 1;
}


void ReleaseFileOutput( REMUXER *pRemuxer )
{
	if ( pRemuxer->output_track != NULL )
	{
		_output_statistic( pRemuxer );
		if ( pRemuxer->output_track[0] )
			ReleaseTracks( pRemuxer->output_track[0] );
	}

	if ( pRemuxer->ps_builder != NULL )
	{
		ReleasePSBuilder( pRemuxer->ps_builder );
		pRemuxer->ps_builder = NULL;
	}
	if ( pRemuxer->ts_builder != NULL )
	{
		ReleaseTSBuilder( pRemuxer->ts_builder );
		pRemuxer->ts_builder = NULL;
	}

	if ( pRemuxer->output_file > 0 )
	{
		FCLOSE( pRemuxer->output_file );
		pRemuxer->output_file = 0;
	}
}
//////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////
int CreateStreamOutput( REMUXER *pRemuxer, int nOutputFormat, DUMP pfnOutputDump, void* pOutputDumpContext )
{
	pRemuxer->output_track[0] = CreateTracks(MAX_TRACK_NUM);
	pRemuxer->output_track[0]->track_type = nOutputFormat;
	if ( IS_PS_TYPE( nOutputFormat ) )
	{
		int build_header_in_buffer; //if es block has space to build a PACK+PES
		build_header_in_buffer = SourceIsTSType( pRemuxer->demuxer );
		pRemuxer->ps_builder = CreatePSBuilder( pRemuxer->output_track[0], ES_PACKET_SIZE, build_header_in_buffer );
		pRemuxer->ps_builder->dumper.stream_dumper = pfnOutputDump;
		pRemuxer->ps_builder->dumper.stream_dumper_context = pOutputDumpContext;
		SetupBlockDataDumper( pRemuxer->demuxer, BlockBufferPSDump, pRemuxer->ps_builder );
	} else
	if ( IS_TS_TYPE(nOutputFormat) )
	{
		pRemuxer->ts_builder = CreateTSBuilder( pRemuxer->output_track[0], nOutputFormat );
		pRemuxer->ts_builder->dumper.stream_dumper = pfnOutputDump;
		pRemuxer->ts_builder->dumper.stream_dumper_context = pOutputDumpContext;
		SetupBlockDataDumper( pRemuxer->demuxer, BlockBufferTSDump, pRemuxer->ts_builder );
	}
	pRemuxer->output_format = nOutputFormat;

	return 1;
}

void ReleaseStreamOutput( REMUXER *pRemuxer )
{
	if ( pRemuxer->output_track != NULL )
	{
		_output_statistic( pRemuxer );

		if ( pRemuxer->output_track[0] )
			ReleaseTracks( pRemuxer->output_track[0] );
	}
	if ( pRemuxer->ps_builder != NULL )
	{
		ReleasePSBuilder( pRemuxer->ps_builder );
		pRemuxer->ps_builder = NULL;
	}
	if ( pRemuxer->ts_builder != NULL )
	{
		ReleaseTSBuilder( pRemuxer->ts_builder );
		pRemuxer->ts_builder = NULL;
	}
}


void SetupRemuxOutput( TRACKS *pTrackIn, TRACKS *pTrackOut )
{
	int i;
	if ( pTrackOut == NULL )
		return;
	ASSERT( pTrackIn->total_track == pTrackOut->total_track );
	for ( i = 0; i<pTrackIn->total_track; i++ )
	{
		if ( pTrackIn->track[i].av_elmnt->content_type == VIDEO_DATA || 
			 pTrackIn->track[i].av_elmnt->content_type == AUDIO_DATA  )
		{	
			if ( pTrackIn->track[i].ts_elmnt && pTrackOut->track[i].ts_elmnt )
			{
				*pTrackOut->track[i].ts_elmnt = *pTrackIn->track[i].ts_elmnt;
				if ( pTrackOut->track[i].ts_elmnt->type == PRIVATE_STREAM_TYPE )
				{
					if ( pTrackIn->track[i].av_elmnt->format_fourcc == SAGE_FOURCC( "H264" ) )
						pTrackOut->track[i].ts_elmnt->type = H264_STREAM_TYPE;
					else
					if ( pTrackIn->track[i].av_elmnt->format_fourcc == SAGE_FOURCC( "VC1 " ) )
						pTrackOut->track[i].ts_elmnt->type =  VC1_STREAM_TYPE;
					else
					if ( pTrackIn->track[i].av_elmnt->format_fourcc == SAGE_FOURCC( "AC3 " ) )
						pTrackOut->track[i].ts_elmnt->type =  AC3_STREAM_TYPE;
					else
					if ( pTrackIn->track[i].av_elmnt->format_fourcc ==  SAGE_FOURCC( "MPGA" ) )
						pTrackOut->track[i].ts_elmnt->type =  AUDIO_STREAM_TYPE;
					else
					if ( pTrackIn->track[i].av_elmnt->format_fourcc ==  SAGE_FOURCC( "MPGV" ) )
						pTrackOut->track[i].ts_elmnt->type =  VIDEO_STREAM_TYPE;
					else
					if ( pTrackIn->track[i].av_elmnt->format_fourcc ==  SAGE_FOURCC( "MP4A" ) )
						pTrackOut->track[i].ts_elmnt->type =  MPEG4_STREAM_TYPE;
				}
			}
			*pTrackOut->track[i].av_elmnt = *pTrackIn->track[i].av_elmnt;
			*pTrackOut->track[i].es_elmnt = *pTrackIn->track[i].es_elmnt;
			pTrackOut->track[i].es_elmnt->es_id = MakeESId( pTrackIn->track[i].es_elmnt->pes.stream_id, 
													(uint8_t)(pTrackIn->track[i].es_elmnt->es_id & 0x00ff) );
			SetupTracks( &pTrackOut->track[i] );
		} 
	}

	for ( i = 0; i<pTrackIn->total_track; i++ )
	{
		//we don't need parser work out sub title format, we just pass on 
		if ( pTrackIn->track[i].av_elmnt->content_type == SUBTITLE_DATA || 
			 pTrackIn->track[i].av_elmnt->format_fourcc == SAGE_FOURCC( "SUB " ) )
		{  
			if ( pTrackIn->track[i].ts_elmnt )
				*pTrackOut->track[i].ts_elmnt = *pTrackIn->track[i].ts_elmnt;
			*pTrackOut->track[i].av_elmnt = *pTrackIn->track[i].av_elmnt;
			*pTrackOut->track[i].es_elmnt = *pTrackIn->track[i].es_elmnt;

			pTrackOut->track[i].es_elmnt->es_id = MakeESId( 0xBD, 0x20+(pTrackIn->track[i].es_elmnt->es_id & 0x00ff) );
			SetupTracks( &pTrackOut->track[i] );
			if ( pTrackOut->track[i].av_elmnt->format_fourcc == SAGE_FOURCC( "SUB " ) )
				pTrackOut->track[i].es_elmnt->private_data[1] = (uint8_t)pTrackOut->track[i].av_elmnt->d.s.sub.type; //ZQ subtitle new way
			
			SageLog(( _LOG_TRACE, 3, "Subtitle stream is added, es id:0x%04x at %d", 
				                          pTrackOut->track[i].es_elmnt->es_id, i ));
		}
	}
	pTrackOut->number_track = pTrackIn->number_track;
	pTrackOut->track_attr = pTrackIn->track_attr;
	pTrackOut->main_video_index = pTrackIn->main_video_index;
	pTrackOut->main_audio_index = pTrackIn->main_audio_index;

}

static int RemuxMessageDumper( void* pContext, uint8_t* pData, int nSize )
{
	REMUXER *pRemuxer = (REMUXER*)pContext;
	MESSAGE_DATA *message = (MESSAGE_DATA*)pData;
	if ( strstr( message->title, "LANGUAGE" ) )
	{
		uint32_t language_code = LanguageCode( message->message );
		SetupEPGDumpLanguage( pRemuxer, language_code );
		//SetupEPGDumpLanguage( pRemuxer,  LanguageCode( "eng" ) );
	} else
	if ( !strcmp( message->title, "STATUS" ) )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("*********** PARSER STATUS: %s ***********"), message->message ));
		if ( strstr( (char*)message->message, "STREAM START" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

			ResetBlockBuffer( pRemuxer->demuxer, slot_index );

			if ( pRemuxer->state >= 2 )
			{
				if ( pRemuxer->ps_builder != NULL )
					pRemuxer->ps_builder->state = PUMPOUT_DATA;
				if ( pRemuxer->ts_builder != NULL )
					pRemuxer->ts_builder->state = PUMPOUT_DATA;
			}

		} else
		if ( strstr( (char*)message->message, "STREAM READY" ) )
		{
			int slot_index = 0;
			const char *p;
			TRACKS *tracks, *tracks_out;

			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

			tracks = GetTracks( pRemuxer->demuxer, slot_index );
			tracks_out = pRemuxer->output_track[slot_index];

			if ( SourceIsPSType( pRemuxer->demuxer ) && IsSageTVRecording( pRemuxer->demuxer ) )
			{
				PickupSageTVMainTrack( pRemuxer->demuxer, tracks );
			}

			CheckTracksAttr( tracks , pRemuxer->language_code );
			if ( FindMainVideoAudio( tracks ) )
			{
				SageLog(( _LOG_TRACE, 3, TEXT("Main video at track:%d; Main audio at track:%d"), 
							                tracks->main_video_index,	tracks->main_audio_index ));
				if ( pRemuxer->remuxer_ctrl & MAIN_TRACK_ONLY )
				{
					DisableAuxTrack( tracks );
				}

			} else
			{
				SageLog(( _LOG_ERROR, 3, TEXT("ERROR: main video and audio track not found" ) ));
			}

			TracksIndexing( tracks );
			_display_av_inf( tracks );
			
			SetupRemuxOutput( tracks, tracks_out );

			if ( pRemuxer->ps_builder != NULL )
			{
				CreatSageStreamHeader( pRemuxer->ps_builder );
			} else
			if ( pRemuxer->ts_builder != NULL )
			{
				UpdatePMT( pRemuxer->ts_builder, slot_index );
			} 

			if ( pRemuxer->state == 1 )
			{
				pRemuxer->state = 2; //output data
			}
			
			if ( pRemuxer->task == STRIP_STREAM )
			{
				char avinf[1024];
				int bytes;
				bytes = snprintf( avinf, sizeof(avinf), "AV-INF|f=" );
				bytes += TracksInfo( tracks, avinf+bytes, sizeof(avinf)-bytes );
				if ( pRemuxer->dumper.avinf_dumper != NULL )
					pRemuxer->dumper.avinf_dumper( pRemuxer->dumper.avinf_dumper_context, avinf, bytes );
				SageLog(( _LOG_TRACE, 3, TEXT("OUTPUT:%s"), avinf ));
			} else
			{
				char avinf[1024];
				int bytes;
				bytes = snprintf( avinf, sizeof(avinf), "AV-INF|f=" );
				bytes += TracksInfo( tracks_out, avinf+bytes, sizeof(avinf)-bytes );
				if ( pRemuxer->dumper.avinf_dumper != NULL )
					pRemuxer->dumper.avinf_dumper( pRemuxer->dumper.avinf_dumper_context, avinf, bytes );
				SageLog(( _LOG_TRACE, 3, TEXT("OUTPUT:%s"), avinf ));
			} 

			//start dumping data
			if ( pRemuxer->task == REMUX_STREAM )
			{
				if ( pRemuxer->ps_builder != NULL )
					pRemuxer->ps_builder->state = PUMPOUT_DATA;
				if ( pRemuxer->ts_builder != NULL )
					pRemuxer->ts_builder->state = PUMPOUT_DATA;
			} else
			//if remux a file, rewind to begining of a file to start
			if ( pRemuxer->task == REMUX_FILE && pRemuxer->state < 3  )
			{
				ULONGLONG pos = DemuxUsedBytes( pRemuxer->demuxer );
				QueueZeroDemux( pRemuxer->demuxer );
				
				DemuxSourceSeekPos( pRemuxer->demuxer, 0, SEEK_SET );
				SageLog(( _LOG_ERROR, 3, TEXT("**** File seek to begin of file to process data. (pos:%d) ****" ), 
																							 (uint32_t)pos ));
				PTSLog(( 0, 0, 0, 0, 0 ) ); 

				pRemuxer->state = 3; //rewinded  
			}

			if ( pRemuxer->ps_builder != NULL )
			{
				OUTPUT_DATA output_data={0};
				if ( !(pRemuxer->ps_builder->build_ctrl & BUILD_HEADER_IN_BUFFER ) )
				{
					//dump a system head block (PACK+SYSTEM HEADER)
					output_data.data_ptr = pRemuxer->ps_builder->block_buffer; 
					output_data.bytes    = pRemuxer->ps_builder->system_packet_bytes;
					pRemuxer->ps_builder->dumper.stream_dumper( pRemuxer->ps_builder->dumper.stream_dumper_context, 
						                              &output_data, sizeof(output_data) );
					//PadingBuffer( pRemuxer->ps_builder->block_buffer, pRemuxer->ps_builder->buffer_size ); //clean up pading buffer
					pRemuxer->ps_builder->system_packet_bytes = 0;
				} else
				{
					BLOCK_BUFFER *block_buffer;
					TRACK track={0}; 
					track.channel_index = 0;
					block_buffer = RequestBlockBuffer( pRemuxer->demuxer, &track );
					if ( block_buffer != NULL )
					{
						ASSERT( block_buffer->buffer_size >= pRemuxer->ps_builder->system_packet_bytes );
						memcpy( block_buffer->buffer_start, pRemuxer->ps_builder->block_buffer, 
															pRemuxer->ps_builder->system_packet_bytes );
						//PadingBuffer( block_buffer->data_start+pRemuxer->ps_builder->system_packet_bytes, 
						//	          block_buffer->data_size-pRemuxer->ps_builder->system_packet_bytes ); 
						output_data.data_ptr = block_buffer->data_start; 
						output_data.bytes    = pRemuxer->ps_builder->system_packet_bytes;
						pRemuxer->ps_builder->dumper.stream_dumper( pRemuxer->ps_builder->dumper.stream_dumper_context, 
																					  &output_data, sizeof(output_data) );
						ReturnBlockBuffer( pRemuxer->demuxer, block_buffer );
					}
				}
			}
			if ( pRemuxer->state == 2 )
			{
				ULONGLONG pos = DemuxUsedBytes( pRemuxer->demuxer );
				SageLog(( _LOG_ERROR, 3, TEXT("****** Find AVINF (pos:%d) ******" ), (uint32_t)pos ));
			}

			//debug_dump_content = 1;  //ZQ

		} else
		if ( strstr( (char*)message->message, "STREAM REBUILD" ) )
		{
			int slot_index = 0;
			const char *p;
			SageLog(( _LOG_TRACE, 3, TEXT("****** PARSER STATUS: %s ******"), message->message ));
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
			 {
				ULONGLONG pos = DemuxUsedBytes( pRemuxer->demuxer );
				QueueZeroDemux( pRemuxer->demuxer );
				DemuxSourceSeekPos( pRemuxer->demuxer, 0, SEEK_SET );
				SageLog(( _LOG_ERROR, 3, TEXT("**** File seek to begin of file to process data. (pos:%d) ****" ), 
																							(uint32_t)pos ));
			}


		} else
		if ( strstr( (char*)message->message, "STREAM FAILED" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

		} else
		if ( strstr( (char*)message->message, "STREAM END" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

			if ( pRemuxer->task != REMUX_FILE )
			{
				//do nothing
			} else
			{
				if ( pRemuxer->state == 1 )  //parsing information, not done yet, force it done
				{
					TRACKS *tracks, *tracks_out;
					tracks = GetTracks( pRemuxer->demuxer, slot_index );
					tracks_out = pRemuxer->output_track[slot_index];

					CheckTracksAttr( tracks , pRemuxer->language_code );
					if ( FindMainVideoAudio( tracks ) )
					{
						SageLog(( _LOG_TRACE, 3, TEXT("Main video at track:%d; Main audio at track:%d"), 
												  tracks->main_video_index,	tracks->main_audio_index ));

						if ( pRemuxer->remuxer_ctrl & MAIN_TRACK_ONLY )
						{
							DisableAuxTrack( tracks );
						}

					} else
					{
						SageLog(( _LOG_ERROR, 3, TEXT("ERROR: main video and audio track not found" ) ));
					}
					TracksIndexing( tracks );
					_display_av_inf( tracks );

					SetupRemuxOutput( tracks, tracks_out );
					if ( pRemuxer->ps_builder != NULL )
					{
						CreatSageStreamHeader( pRemuxer->ps_builder );
					} else
					if ( pRemuxer->ts_builder != NULL )
					{
						UpdatePMT( pRemuxer->ts_builder, slot_index );
					} 

					pRemuxer->state = 2; //output data

					{
						ULONGLONG pos = DemuxUsedBytes( pRemuxer->demuxer );
						QueueZeroDemux( pRemuxer->demuxer );

						DemuxSourceSeekPos( pRemuxer->demuxer, 0, SEEK_SET );
						SageLog(( _LOG_ERROR, 3, TEXT("**** File seek to begin of file to process data. (pos:%d) ****" ), 
																									 (uint32_t)pos ));
						PTSLog(( 0, 0, 0, 0, 0 ) ); 
					}

				} else
				{
					FlushRemuxer( pRemuxer, slot_index );
				}
			}
		}
	}

	return 1;
}



////////////////////////////////////// Main //////////////////////////////////////
//** MAIN  ENTRY ***

int EPGDumper( void* pContext, uint8_t* pData, int nSize )
{
	SageLog(( _LOG_ERROR, 3, (char*)pData ));
	return 1;
}

int RemuxFileProgressCallback( void* pContext, void* pData, int nSize )
{
	//REMUXER *pRemuxer = (REMUXER *)pContext;
	//ULONGLONG *plTotalBytes = (ULONGLONG *)pData;
	ASSERT( sizeof(ULONGLONG) == nSize );
	return 1;
}

int RemuxFile( uint16_t nTask, char* pInputFile, TUNE* pTune, int nInputFormat, 
			   char* pOutFile, int nOutputFormat, int nOption )
{
	REMUXER *pRemuxer;
	int ret, max_track_num;

	//if not specify a input format, detect it
	if ( nInputFormat == 0 )
		nInputFormat = DetectFileType( pInputFile );
	if ( nInputFormat == 0 )
		return 0;

	if ( nInputFormat == MPEG_M2TS ) 
		 max_track_num = MAX_TRACK_NUM *2;
	else
		 max_track_num = MAX_TRACK_NUM;

	pRemuxer = CreateRemuxer( nInputFormat, max_track_num, ES_BUFFER_SIZE );
	pRemuxer->task = nTask;

	SetupMessageDumper( pRemuxer->demuxer, (DUMP)RemuxMessageDumper, pRemuxer );

	ret = OpenFileSource( pRemuxer->demuxer, pInputFile, nInputFormat,  pTune );
	if ( !ret )
	{
		ReleaseRemuxer( pRemuxer );
		return 0;
	}

	if ( nOption & 0x01 ) 
		DisabeTSPtsFix( pRemuxer ); 

	if ( nOption & 0x02 ) 
		SetupEPGDump( pRemuxer, (DUMP)EPGDumper, pRemuxer );

	if ( nOption & 0x04 )
		DisableSubtitle( pRemuxer );


	SetupEPGDumpLanguage( pRemuxer, LanguageCode((uint8_t*)"eng") );

	if ( pOutFile != NULL && pOutFile[0] != 0x0 )
		CreateFileOutput( pRemuxer, pOutFile, nOutputFormat, max_track_num );

	if ( IS_TS_TYPE(nOutputFormat) )
		SetupBlockDataDumper( pRemuxer->demuxer, BlockBufferTSDump, pRemuxer->ts_builder );
	else
	if ( IS_PS_TYPE(nOutputFormat) )
		SetupBlockDataDumper( pRemuxer->demuxer, BlockBufferPSDump, pRemuxer->ps_builder );

	pRemuxer->state = 1;

	//looping pump data from file into remuxer
	PumpFileData( pRemuxer->demuxer, 0, RemuxFileProgressCallback, pRemuxer ); 

	PostStatusMessage( pRemuxer, "STREAM END (slot:0)" );

	ReleaseFileOutput( pRemuxer );
	CloseFileSource( pRemuxer->demuxer );
	ReleaseRemuxer( pRemuxer );
	
	return 1;
}

int RemuxFileW( uint16_t nTask, wchar_t* pInputFile, TUNE* pTune, int nInputFormat, 
			   wchar_t* pOutFile, int nOutputFormat, int nOption )
{
	REMUXER *pRemuxer;
	int ret, max_track_num;

	//if not specify a input format, detect it
	if ( nInputFormat == 0 )
		nInputFormat = DetectFileTypeW( pInputFile );
	if ( nInputFormat == 0 )
		return 0;

	if ( nInputFormat == MPEG_M2TS ) 
		 max_track_num = MAX_TRACK_NUM *2;
	else
		 max_track_num = MAX_TRACK_NUM;

	pRemuxer = CreateRemuxer( nInputFormat, max_track_num, ES_BUFFER_SIZE );
	pRemuxer->task = nTask;

	SetupMessageDumper( pRemuxer->demuxer, (DUMP)RemuxMessageDumper, pRemuxer );

	ret = OpenFileSourceW( pRemuxer->demuxer, pInputFile, nInputFormat,  pTune );
	if ( !ret )
	{
		ReleaseRemuxer( pRemuxer );
		return 0;
	}

	if ( nOption & 0x01 ) 
		DisabeTSPtsFix( pRemuxer ); 

	if ( nOption & 0x02 ) 
		SetupEPGDump( pRemuxer, (DUMP)EPGDumper, pRemuxer );

	SetupEPGDumpLanguage( pRemuxer, LanguageCode((uint8_t*)"eng") );

	if ( pOutFile != NULL && pOutFile[0] != 0x0 )
		CreateFileOutputW( pRemuxer, pOutFile, nOutputFormat, max_track_num );

	if ( IS_TS_TYPE(nOutputFormat) )
		SetupBlockDataDumper( pRemuxer->demuxer, BlockBufferTSDump, pRemuxer->ts_builder );
	else
	if ( IS_PS_TYPE(nOutputFormat) )
		SetupBlockDataDumper( pRemuxer->demuxer, BlockBufferPSDump, pRemuxer->ps_builder );

	pRemuxer->state = 1;

	//looping pump data from file into remuxer
	PumpFileData( pRemuxer->demuxer, 0, RemuxFileProgressCallback, pRemuxer ); 

	PostStatusMessage( pRemuxer, "STREAM END (slot:0)" );

	ReleaseFileOutput( pRemuxer );
	CloseFileSource( pRemuxer->demuxer );
	ReleaseRemuxer( pRemuxer );
	
	
	return 1;
}

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
void* OpenRemuxStream( uint16_t nTask, TUNE* pTune, 
					   int nInputFormat, int nOutputFormat, 
					   MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext,
					   DUMP pfnOutputDump, void* pOutputDumpContext )
{
	REMUXER *pRemuxer;
	int ret, max_track_num;

	//if not specify a input format, detect it
	if ( nInputFormat == 0 )
		return 0;

	if ( nInputFormat == MPEG_M2TS ) 
		 max_track_num = MAX_TRACK_NUM *2;
	else
		 max_track_num = MAX_TRACK_NUM;

	SageLog(( _LOG_ERROR, 3, TEXT("Create Remuxer" ) ));
	pRemuxer = CreateRemuxer( nInputFormat, max_track_num, ES_BUFFER_SIZE );

	//pRemuxer->remuxer_ctrl = MAIN_TRACK_ONLY;
	pRemuxer->task = nTask;

	if ( pfnMemAlloc != NULL )
		SetupMemAlloc( pRemuxer, pfnMemAlloc, pMemAllocContext );

	SetupMessageDumper( pRemuxer->demuxer, (DUMP)RemuxMessageDumper, pRemuxer );

	ret = OpenStreamSource( pRemuxer->demuxer, nInputFormat,  pTune );
	if ( !ret )
	{

		ReleaseRemuxer( pRemuxer );
		return 0;
	}

	CreateStreamOutput( pRemuxer, nOutputFormat, pfnOutputDump, pOutputDumpContext );

	pRemuxer->state = 1;
	pRemuxer->dumper.output_dumper = pfnOutputDump;
	pRemuxer->dumper.output_dumper_context = pOutputDumpContext;

	return pRemuxer;
}

void CloseRemuxStream( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;

	FlushRemuxer( pRemuxer, 0 );
	ReleaseStreamOutput( pRemuxer );
	CloseStreamSource( pRemuxer->demuxer );
	ReleaseRemuxer( pRemuxer );
	SageLog(( _LOG_ERROR, 3, TEXT("Remuxer was released." ) ));
}

void ChangeRemuxOutputFormat( void* Handle, int nOutputFormat )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;

	if ( nOutputFormat != pRemuxer->output_format )
	{
		ReleaseStreamOutput( pRemuxer );
		CreateStreamOutput( pRemuxer, nOutputFormat, 
			                pRemuxer->dumper.output_dumper, 
							pRemuxer->dumper.output_dumper_context );
	}
}

void SetupRemuxOutputBlockSize( void* Handle, int nSize )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	if ( pRemuxer->ts_builder )
		SetupBlockDataSize( pRemuxer->ts_builder, nSize );
}

void ResetRemuxStream( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	ResetRemuxerAll( pRemuxer );
}

void ResetRemuxStreamSlot( void* Handle, int nSlot )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	ResetRemuxer( pRemuxer, nSlot );
}

void FlushRemuxStream( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	FlushRemuxer( pRemuxer, 0 );
}

void SetupRemuxStreamTune( void* Handle, TUNE *pTune )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	if ( !SourceIsTSType( pRemuxer->demuxer ) )
		return;
	SetupDemuxTSTune( pRemuxer->demuxer, 0, pTune );
}


void SetupRemuxTSStreamFormat( void* Handle, int nFormat, int nSubFormat )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	if ( !SourceIsTSType( pRemuxer->demuxer ) )
		return;
	SetupDemuxTSStreamFormat( pRemuxer->demuxer, nFormat, nSubFormat );
}

void DisableMultipleAudio( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	pRemuxer->remuxer_ctrl |= MAIN_TRACK_ONLY;
}

void EnableMultipleAudio( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	pRemuxer->remuxer_ctrl &= ~MAIN_TRACK_ONLY;
}

int	PushRemuxStreamData( void* Handle, uint8_t *pData, int nBytes, int *nExpectedBytes )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	return PushDemuxStreamData( pRemuxer->demuxer, pData, nBytes, nExpectedBytes );
}

int CheckParserStart( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	return pRemuxer->demuxer->state > 0 ;
}

void SetTSStreamType( void* Handle, int nFormat, int nSubFormat )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	SetupDemuxTSStreamFormat( pRemuxer->demuxer, nFormat, nSubFormat );
}

void GetTSStreamType( void* Handle, int *pFormat, int *pSubFormat )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	*pFormat    = GetTSStreamFormat( pRemuxer->demuxer );
	*pSubFormat = GetTSStreamSubFormat( pRemuxer->demuxer );
}

void LockUpTSParser( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	LockDemuxTSPPmt( pRemuxer->demuxer );
}

void UnLockUpTSParser( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	UnLockDemuxTSPPmt( pRemuxer->demuxer );
}

void DisableTSPSI( void *Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	DisableDemuxTSPSI( pRemuxer->demuxer );
}

void EnableTSPSI( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	EnableDemuxTSPSI( pRemuxer->demuxer );
}

void DisabeTSPtsFix( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	DisablePTSFix( pRemuxer->demuxer );
}

void DisableSubtitle( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	DisableSubtitleParsing( pRemuxer->demuxer );
}

void EnableTSAudioPriorityHack( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	EnableAudioTSPriorityHack( pRemuxer->demuxer );
}

void  EnableWaitTSCleanStream( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	WaitTSCleanStream( pRemuxer->demuxer );
}

void DisableTSRebuildStream( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	DisableRebuildTSStream( pRemuxer->demuxer );
}
void EnableTSRebuildStream( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	EnableRebuildTSStream( pRemuxer->demuxer );
}

void ChangeDemuxerTSStreamType( void* Handle, int nStreamType )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	SetupDemuxerTSStreamType( pRemuxer->demuxer, nStreamType );
}

int DemuxBlockSize( void* Handle  )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	return BlockSize( pRemuxer->demuxer );
}

struct DEMUXER* GetDemuxer( void* Handle )
{	
	REMUXER *pRemuxer = (REMUXER *)Handle;
	return ( pRemuxer->demuxer );
}

//void* CreateTSPacketDump( void* Handle, DUMP pfnStreamDump, 
//		         void* pStreamDumpContext, DUMP pfnIndexDump, void* pIndexDumpContext )
//{
//	REMUXER *pRemuxer = (REMUXER *)Handle;
//	return CreateTSPacketDumper( pRemuxer->demuxer, pfnStreamDump, pStreamDumpContext,
//							   pfnIndexDump, pIndexDumpContext );
//}
//
//void  ReleaseTSPacketDump( void* Handle )
//{
//	ReleaseTSPacketDumper( Handle );
//}
//
//int   StartTSPacketDump( void* Handle, int nFlag )
//{
//	return StartTSPacketDumper( Handle, nFlag );
//}
//
//void  StopTSPacketDump( void* Handle )
//{
//	StopTSPacketDumper( Handle );
//}

void SetupAVInfDump( void* Handle, DUMP pfnAVInfDump, void* pfnAVInfDumpContext )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	pRemuxer->dumper.avinf_dumper = pfnAVInfDump;
	pRemuxer->dumper.avinf_dumper_context = pfnAVInfDumpContext;
}
void SetupEPGDump( void* Handle, DUMP pfnEPGDump, void* pEPGDumpContext )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	if ( pRemuxer->demuxer->ts_parser )
		SetupTSEPGDump( pRemuxer->demuxer->ts_parser, pfnEPGDump, pEPGDumpContext );
}

void SetupEPGDumpLanguage( void* Handle, uint32_t lLanguageCode )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	if ( pRemuxer->demuxer->ts_parser )
		SetupTSEPGDumpLanguage( pRemuxer->demuxer->ts_parser, lLanguageCode );
	SageLog(( _LOG_ERROR, 3, TEXT("Set default EPG language \"%s\"."), Language(lLanguageCode, NULL )  ));
}

void SetDefaultAudioLanguage( void* Handle, uint32_t lLanguageCode )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	pRemuxer->language_code = lLanguageCode;
	SageLog(( _LOG_ERROR, 3, TEXT("Set default audio language \"%s\"."), Language(lLanguageCode, NULL )  ));
}

void SetupProgramDataDump( void* Handle, DUMP pfnProgramDataDump, void* pfnProgramDataContext )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	SetupTSProgramDataDump( pRemuxer->demuxer, pfnProgramDataDump, pfnProgramDataContext );
}

void SetupFastPESDump( void * Handle, DUMP pfnPESDumper, void* pPESDumperContext )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	SetupPESDump( pRemuxer->demuxer, pfnPESDumper, pPESDumperContext );
}

void UpdateClock( void* Handle, uint32_t lClock )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	UpdateDemuxerClock( pRemuxer->demuxer, lClock );
}

void DisablePSBuildPading( void* Handle )
{
	REMUXER *pRemuxer = (REMUXER *)Handle;
	if ( pRemuxer->ps_builder != NULL )
		DisablePadingPacket( pRemuxer->ps_builder ); 
}
/////////////////////////////////////// Utility ////////////////////////////////////////////////////////////

static void _display_av_inf( TRACKS *pTracks )
{
	//int slot_index = 0;
	char avinf[1024];

	TracksInfo( pTracks, avinf, sizeof(avinf) );
	SageLog(( _LOG_TRACE, 3, TEXT("INPUT-AVINF|%s"), avinf ));
}


static void _output_statistic( REMUXER *pRemuxer )
{
	int i;
	if ( pRemuxer->ps_builder != NULL )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Total output blocks:%d; processed total data:%d "), 
				pRemuxer->ps_builder->output_blocks, pRemuxer->ps_builder->output_bytes ));
		for ( i = 0; i<MAX_TRACK_NUM; i++ )
		{
			if ( pRemuxer->output_track[0]->track[i].es_elmnt &&  pRemuxer->output_track[0]->track[i].es_elmnt->es_id )
			{
				SageLog(( _LOG_TRACE, 3, TEXT("\tes:%d es blocks:%d"), i,
						pRemuxer->output_track[0]->track[i].es_blocks_counter ));
			}
		}
		if ( pRemuxer->ps_builder->out_of_order_blocks )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("out_of_order_blocks in FIFO buffer:%d "), pRemuxer->ps_builder->out_of_order_blocks  ));
		}

	} else
	if ( pRemuxer->ts_builder != NULL )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Total output blocks:%d; packet:%d; processed total data:%d "), 
				pRemuxer->ts_builder->output_blocks, pRemuxer->ts_builder->output_packets,
				pRemuxer->ts_builder->output_bytes ));
		for ( i = 0; i<MAX_TRACK_NUM; i++ )
		{
			if ( pRemuxer->output_track[0]->track[i].es_elmnt &&  pRemuxer->output_track[0]->track[i].es_elmnt->es_id )
			{
				SageLog(( _LOG_TRACE, 3, TEXT("\tes:%d: ts packects :%d; es blocks:%d"), i,
						pRemuxer->output_track[0]->track[i].ts_packets_counter,
						pRemuxer->output_track[0]->track[i].es_blocks_counter ));
			}
		}
	}

}

uint8_t* _search_data_( uint8_t* match, int len, uint8_t* data, int data_size )
{
	int i;
	for ( i = 0; i<data_size-len; i++ )
	
		if ( !memcmp( match, data+i, len ) )
			return data+i;
	
	return NULL;
}
/*
static void _s_(uint8_t*data, int size)
{
	uint8_t pat1[]={0x00, 0x00, 0x01, 0xfd, 0x01, 0x98, 0x88, 0x00, 0x00, 0x81};
	if ( _search_data_( pat1, sizeof(pat1), data, size ) )
	{
		printf( "STOP" );
	}
}
*/
