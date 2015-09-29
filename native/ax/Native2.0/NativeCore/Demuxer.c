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

#include "TSFilterDump.h"
#include "Demuxer.h"

#ifndef _MAX_PATH
#define _MAX_PATH      512
#endif

///////////////////////////////////////////////////////////////////////////////////////////////////////////
//Demux->TSParser->TSFilter[PAT, PMT, PSI, TS_STREAM]->PSIPaser
//    |->PSParser
///////////////////////////////////////////////////////////////////////////////////////////////////////////

//#define DUMP_STREAM 1
int debug_dump_content = 0;

//////////////////////////////////////////// DUMPER Section //////////////////////////////////////////
static int  RequestESBuffer( DEMUXER* pDemuxer, TRACK* pTrack );
static int  ProcessESBuffer( DEMUXER* pDemuxer, TRACK* pTrack );
static void FlushFIFOBuffer( DEMUXER* pDemuxer, TRACK* pTrack );
//static void DisplayAVInf( DEMUXER* pDemuxer, int nSlot );
static void ReleaseESBuffer( DEMUXER* pDemuxer, BLOCK_BUFFER *pBlockBuffer );

static void _input_statistic( DEMUXER *pDemuxer );

static void InitMemoryAlloc( MEMORY_ALLOC *pMemAlloc, MEM_ALLOC_HOOK pfnMemAllocHook, void* pMemAllocHookContext, int nNodeNum, int nBlockSize );
static void ReleaseMemoryAlloc( MEMORY_ALLOC *pMemAlloc );
static void ResetMemoryAlloc( MEMORY_ALLOC *pMemAlloc );
static void SetupMemoryAlloc( MEMORY_ALLOC *pMemAlloc, MEM_ALLOC_HOOK pfnMemAllocHook, void* pMemAllocHookContext, int nNodeNum, int nBlockSize );
/////////////////////////////////////////////////////////////////////////////////////////////////////////////

static int ESBlockDataDumper( void* pContext, uint8_t* pData, int nSize )
{
	DEMUXER *pDemuxer = (DEMUXER*)pContext;
	TRACK   *pTrack = (TRACK*)pData;

	if ( pTrack->command == REQUEST_BUFFER )
	{
		int ret = RequestESBuffer( pDemuxer, pTrack );
		if ( ret > 0 )
			pTrack->command = START_FILL_DATA;
		else
		{
			SageLog(( _LOG_TRACE, 2, TEXT("ERROR: requesting ES buffer failed (ch:%d pid:0x%03x), process stopped!"),
				       pTrack->ts_elmnt->pid, pTrack->ts_elmnt->pid));
			pTrack->command = STOP_FILL_DATA;
		}
		
		return 1;
	} else
	if ( pTrack->command == DATA_READY )
	{
		ProcessESBuffer( pDemuxer, pTrack  );
		if ( pTrack->command != STOP_FILL_DATA )
			pTrack->command = START_FILL_DATA;
		return 1;
	} else
	if ( pTrack->command == DATA_OVERFLOW )
	{
		pTrack->command = STOP_FILL_DATA;
		SageLog(( _LOG_TRACE, 2, TEXT("ERROR: the ES buffer size is too small (ch:%d pid:0x%03x), data overflow, process stopped!"),
				 pTrack->ts_elmnt->pid, pTrack->ts_elmnt->pid));
		return 1;
	} else
	if ( pTrack->command == STOP_FILL_DATA )
	{
		//do nothing, process stop
	}

	return 0;
}

void DisableAuxTrack( TRACKS *pTracks )
{
	int i;
	for ( i = 0; i<pTracks->number_track; i++ )
	{
		if ( pTracks->track[i].ts_elmnt->content_type != VIDEO_DATA &&
			 pTracks->track[i].ts_elmnt->content_type != AUDIO_DATA )
			 pTracks->track[i].ts_elmnt->pid = 0xffff;
	}
	if ( pTracks->main_audio_index != 0xffff )
	{
		for ( i = 0; i<pTracks->number_track; i++ )
		{
			if ( pTracks->track[i].ts_elmnt->content_type == AUDIO_DATA &&
				 i != pTracks->main_audio_index )
			 pTracks->track[i].ts_elmnt->pid = 0xffff;
		}
	}
	if ( pTracks->main_video_index != 0xffff )
	{
		for ( i = 0; i<pTracks->number_track; i++ )
		{
			if ( pTracks->track[i].ts_elmnt->content_type == VIDEO_DATA &&
				 i != pTracks->main_video_index )
			 pTracks->track[i].ts_elmnt->pid = 0xffff;
		}
	}
}

//static void DisplayAVInf( DEMUXER* pDemuxer, int nSlot )
//{
//	int slot_index = 0;
//	TRACKS *tracks;
//	char avinf[1024];
//	tracks = pDemuxer->tracks[nSlot];
//
//	TracksInfo( tracks, avinf, sizeof(avinf) );
//	SageLog(( _LOG_TRACE, 3, TEXT("INPUT-AVINF|%s"), avinf ));
//}


//////////////////////////////// API SECTION ///////////////////////////////////

static void InitTSTrack( DEMUXER* pDemuxer, int nSlot, int nTrackNum, int nStreamType, int nBlockSize );
static void ReleaseTSTrack( DEMUXER* pDemuxer , int nSlot );
static void ResetTSTrack( DEMUXER* pDemuxer, int nSlot );

DEMUXER* CreateTsDemuxer( int nTrackNum, int nStreamType, int nBlockSize )
{
	DEMUXER* pDemuxer = SAGETV_MALLOC( sizeof(DEMUXER) );
	pDemuxer->ts_parser = CreateTSParser( nStreamType );
	InitTSTrack( pDemuxer, 0, nTrackNum, nStreamType,  nBlockSize ); 
	pDemuxer->ts_parser->dumper.esblock_dumper          = (DUMP)ESBlockDataDumper;
	pDemuxer->ts_parser->dumper.esblock_dumper_context  = pDemuxer;
	return pDemuxer;
}

void ReleaseTsDemuxer( DEMUXER* pDemuxer )
{
	int i;
	for ( i = 0; i<MAX_SLOT_NUM; i++ )
	{
		if ( pDemuxer->tracks[i] != NULL )
		{
				ReleaseTSTrack( pDemuxer, i );
		}
	}
	ReleaseTSParser( pDemuxer->ts_parser );
	SAGETV_FREE( pDemuxer );
}

void ResetTsDemuxer( DEMUXER* pDemuxer )
{
	int i;
	for ( i = 0; i<MAX_SLOT_NUM; i++ )
	{
		ResetTSTrack( pDemuxer, i );
	}
	ResetTSParser( pDemuxer->ts_parser );
	ResetPSI( pDemuxer->ts_parser );
}

void ResetTsDemuxerSlot( DEMUXER* pDemuxer, int nSlot )
{
	ResetTSTrack( pDemuxer, nSlot );
	ResetTSChannel( pDemuxer->ts_parser, nSlot );
	ResetTSParserState( pDemuxer->ts_parser );
	//ResetPSI( pDemuxer->ts_parser );

}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

static void InitTSTrack( DEMUXER* pDemuxer, int nSlot, int nTrackNum, int nStreamType, int nBlockSize )
{
	int fifo_queue_size = FIFO_QUEUE_SIZE;
	if ( nStreamType == MPEG_M2TS )
		fifo_queue_size += FIFO_QUEUE_SIZE/2;
	pDemuxer->tracks[nSlot] = CreateTracks( nTrackNum );
	pDemuxer->tracks[nSlot]->track_type = nStreamType;
	pDemuxer->tracks[nSlot]->track_attr = 0;
	pDemuxer->track_debug[nSlot] = SAGETV_MALLOC( sizeof(TRACK_DEBUG)*nTrackNum );
	pDemuxer->fifo_buffer[nSlot] = CreateFIFOBuffer( fifo_queue_size, nBlockSize );
	InitMemoryAlloc( &pDemuxer->memory_alloc[nSlot], NULL, NULL,
					 pDemuxer->fifo_buffer[0]->total_queue_num, 
					 pDemuxer->fifo_buffer[0]->block_buffer_size );
	ASSERT( FIFO_QUEUE_SIZE > nTrackNum );

}

static void ReleaseTSTrack( DEMUXER* pDemuxer , int nSlot )
{
	int i;
	for ( i = 0; i<pDemuxer->tracks[nSlot]->total_track; i++ ) 
	{
		if ( pDemuxer->track_debug[nSlot]->track_dump_file[i] != NULL )
			fclose( pDemuxer->track_debug[nSlot]->track_dump_file[i] );

		//if ( pDemuxer->input_data_buffer[nSlot]->buffers[i] != NULL )
		//	SAGETV_FREE( pDemuxer->input_data_buffer[nSlot]->buffers[i] );

	}

	ReleaseTracks( pDemuxer->tracks[nSlot]   );
	ReleaseFIFOBuffer( pDemuxer->fifo_buffer[nSlot] );
	ReleaseMemoryAlloc( &pDemuxer->memory_alloc[nSlot] );
	SAGETV_FREE( pDemuxer->track_debug[nSlot]);

}

void TracksIndexing( TRACKS *pTracks )
{
	int i, index=0, video_index=0, audio_index=0, sub_index=0;
	if ( IS_PS_TYPE( pTracks->track_type ) )
	{
		int j;
		uint8_t  sort_index[128]={0}, si;
		uint16_t sort_val[128], sv;
		for ( i = 0; i<pTracks->total_track; i++ )
		{
			 sort_index[i]=i;
			 if ( pTracks->track[i].es_elmnt->es_id )
			 {
				 if ( pTracks->main_video_index == i || pTracks->main_audio_index == i )
					sort_val[i] = 0;
				 else
					sort_val[i] = 1+( sort_val[i]=pTracks->track[i].es_elmnt->es_id & 0xff );
			 
				 sort_val[i] +=	pTracks->track[i].av_elmnt->content_type == VIDEO_DATA ? 0 :
								pTracks->track[i].av_elmnt->content_type == AUDIO_DATA ? 0x200:0x400;
			 }					
			 else
				sort_val[i] = 0xffff;
		}
		//buble sorting
		for ( i = 0; i<pTracks->total_track; i++ )
		{
			for ( j = i+1; j<pTracks->total_track; j++ )
			{
				if ( sort_val[i] > sort_val[j] )
				{
					si = sort_index[i]; sort_index[i] = sort_index[j]; sort_index[j]= si;
					sv = sort_val[i];   sort_val[i] = sort_val[j];     sort_val[j] = sv;
				}
			}
		}

		for ( i = 0; i<pTracks->total_track; i++ )
		{
			j = sort_index[i];
			pTracks->track[j].av_elmnt->stream_index = i;
		}
	}
	else	
	{
		//video stream
		for ( i = 0; i<pTracks->total_track; i++ )
		{
			if ( pTracks->track[i].av_elmnt->content_type == VIDEO_DATA )
			{
				pTracks->track[i].es_elmnt->es_id &= 0xff00;
				if ( pTracks->main_video_index != i )
				{
					pTracks->track[i].es_elmnt->es_id |= 1+video_index++;
					pTracks->track[i].av_elmnt->stream_index = 1+index++;
				}
				else
				{
					pTracks->track[i].es_elmnt->es_id |= 0;
					pTracks->track[i].av_elmnt->stream_index = 0;
				}
			}
		}
		if ( pTracks->main_video_index != 0xffff )	video_index++;
		index = video_index;
		ASSERT( video_index <256 );
		//audio stream
		for ( i = 0; i<pTracks->total_track; i++ )
		{
			if ( pTracks->track[i].av_elmnt->content_type == AUDIO_DATA )
			{
				pTracks->track[i].es_elmnt->es_id &= 0xff00;
				if ( pTracks->main_audio_index != i )
				{
					pTracks->track[i].es_elmnt->es_id |= 1+audio_index++;
					pTracks->track[i].av_elmnt->stream_index = video_index+index++;
				}
				else
				{
					pTracks->track[i].es_elmnt->es_id |= 0;
					pTracks->track[i].av_elmnt->stream_index = video_index;
				}
			}
		}
		if ( pTracks->main_audio_index != 0xffff )	audio_index++;
		index = audio_index+video_index;
		ASSERT( audio_index <256 );
		//subtitle stream
		for ( i = 0; i<pTracks->total_track; i++ )
		{
			if ( pTracks->track[i].av_elmnt->content_type == SUBTITLE_DATA )
			{
				pTracks->track[i].es_elmnt->es_id &= 0xff00;
				pTracks->track[i].es_elmnt->es_id |= sub_index++;
				pTracks->track[i].av_elmnt->stream_index = index+sub_index++;
			}
		}
		ASSERT( sub_index <256 );
		index = audio_index+video_index+sub_index;
		/*if ( 0 ) //ZQ REMOVE ME
		{
			for ( i = 0; i<pTracks->total_track; i++ )
			{
				if ( pTracks->track[i].av_elmnt->content_type != VIDEO_DATA &&
					 pTracks->track[i].av_elmnt->content_type != AUDIO_DATA  )
				{
					pTracks->track[i].es_elmnt->es_id &= 0xff00;
					pTracks->track[i].es_elmnt->es_id |= others_index++;
					pTracks->track[i].av_elmnt->stream_index = index+index++;
				}
			}
			ASSERT( others_index <256 );
		} */
	}
}

void ResetBlockBuffer( DEMUXER* pDemuxer, int nSlot )
{
	if ( pDemuxer->fifo_buffer[nSlot] == NULL )
		return;
	ResetFIFOBuffer( pDemuxer->fifo_buffer[nSlot] );
	ResetMemoryAlloc( &pDemuxer->memory_alloc[nSlot] );
}

static void ResetTSTrack( DEMUXER* pDemuxer, int nSlot )
{
	if ( pDemuxer->tracks[nSlot] == NULL )
		return;
	ResetFIFOBuffer( pDemuxer->fifo_buffer[nSlot] );
	ResetMemoryAlloc( &pDemuxer->memory_alloc[nSlot] );
	ResetTracks( pDemuxer->tracks[nSlot] );
}


static void SetupDemuxerMemAlloc( DEMUXER* pDemuxer, int nSlot, MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		SetupMemoryAlloc( &pDemuxer->memory_alloc[nSlot], 
					   pfnMemAlloc,  pMemAllocContext,
					   pDemuxer->fifo_buffer[0]->total_queue_num, 
					   pDemuxer->fifo_buffer[0]->block_buffer_size );
}

void SetupMemAllocHook(  DEMUXER* pDemuxer, MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext )
{
	SetupDemuxerMemAlloc( pDemuxer, 0, pfnMemAlloc, pMemAllocContext );
}

	//pDemuxer->track_debug[1] = SAGETV_MALLOC( sizeof(TRACK_DEBUG)*MAX_TRACK_NUM);
	//test_tracks = CreateTracks( MAX_TRACK_NUM );
//-------------------------------------------------------------------------

DEMUXER* CreatePsDemuxer( int nTrackNum )
{
	DEMUXER* pDemuxer = SAGETV_MALLOC( sizeof(DEMUXER) );

	pDemuxer->tracks[0] = CreateTracks( nTrackNum );
	pDemuxer->ps_parser = CreatePSParser( pDemuxer->tracks[0] );
	pDemuxer->ps_parser->dumper.esblock_dumper          = (DUMP)ESBlockDataDumper;
	pDemuxer->ps_parser->dumper.esblock_dumper_context  = (DUMP)pDemuxer;
	//pDemuxer->ps_parser->dumper.message_dumper         = MessageDumper;
	//pDemuxer->ps_parser->dumper.message_dumper_context = pDemuxer;
	pDemuxer->tracks[0]->track_type = MPEG_PS;
	pDemuxer->track_debug[0] = SAGETV_MALLOC( sizeof(TRACK_DEBUG)*nTrackNum );
	return pDemuxer;
}

void ResetPsDemuxer( DEMUXER* pDemuxer )
{
	ResetTracks( pDemuxer->tracks[0] );
	ResetPSParser( pDemuxer->ps_parser );
}

void ReleasePsDemuxer( DEMUXER* pDemuxer )
{
	int i;
	for ( i = 0; i<pDemuxer->tracks[0]->total_track; i++ ) 
	{
		if ( pDemuxer->track_debug[0]->track_dump_file[i] != 0 )
			fclose( pDemuxer->track_debug[0]->track_dump_file[i] );
	}

	ReleasePSParser( pDemuxer->ps_parser );
	ReleaseTracks( pDemuxer->tracks[0] );
	SAGETV_FREE( pDemuxer->track_debug[0]);
	SAGETV_FREE( pDemuxer );
}
///////////////////////////////////////////////////////////////////////////////////

static void InitMemoryAlloc( MEMORY_ALLOC *pMemAlloc, MEM_ALLOC_HOOK pfnMemAllocHook, void* pMemAllocHookContext, int nNodeNum, int nBlockSize )
{
	int i;
	pMemAlloc->local_mem_block_num = nNodeNum;
	pMemAlloc->local_mem_block_state = SAGETV_MALLOC( sizeof(char) * nNodeNum );
	pMemAlloc->local_mem_block_addr = SAGETV_MALLOC( sizeof(char*) * nNodeNum );
	pMemAlloc->block_size = nBlockSize;
	if ( pfnMemAllocHook != NULL )
	{
		pMemAlloc->memory_alloc_hook = pfnMemAllocHook;
		pMemAlloc->memory_alloc_hook_context = pMemAllocHookContext;
		pMemAlloc->block_size = nBlockSize;
	} else
	{
		pMemAlloc->local_buffer_data = SAGETV_MALLOC( nNodeNum * nBlockSize );
		pMemAlloc->local_buffer_size = nNodeNum * nBlockSize;
		for ( i = 0; i< nNodeNum; i++ )
		{
			pMemAlloc->local_mem_block_state[i] = 0;
			pMemAlloc->local_mem_block_addr[i] = i*nBlockSize + pMemAlloc->local_buffer_data;
		}
		pMemAlloc->inuse_num = 0;
	}
}

static void ReleaseMemoryAlloc( MEMORY_ALLOC *pMemAlloc )
{
	int i;
	if ( pMemAlloc->memory_alloc_hook != NULL )
	{
		for ( i = 0; i < pMemAlloc->local_mem_block_num; i++ )
		{
			if ( pMemAlloc->local_mem_block_addr[i] != NULL )
			{
				if ( pMemAlloc->local_mem_block_state[i] )
				{
					uint8_t* p = (uint8_t*)pMemAlloc->local_mem_block_addr[i];
					pMemAlloc->memory_alloc_hook( pMemAlloc->memory_alloc_hook_context, p, 0, 0 );
					pMemAlloc->local_mem_block_state[i] = 0;
					pMemAlloc->local_mem_block_addr[i] = 0;
					pMemAlloc->inuse_num--;
				}
			}
		}

	} else
	if ( pMemAlloc->local_buffer_data != NULL )
	{
		SAGETV_FREE( pMemAlloc->local_buffer_data );
		pMemAlloc->inuse_num = 0;
	}

	SAGETV_FREE( pMemAlloc->local_mem_block_state );
	SAGETV_FREE( pMemAlloc->local_mem_block_addr );
}

static void ResetMemoryAlloc( MEMORY_ALLOC *pMemAlloc )
{
	int i;
	if ( pMemAlloc->memory_alloc_hook != NULL )
	{
		for ( i = 0; i < pMemAlloc->local_mem_block_num; i++ )
		{
			if ( pMemAlloc->local_mem_block_addr[i] != NULL )
			{
				if ( pMemAlloc->local_mem_block_state[i] )
				{
					uint8_t* p = (uint8_t*)pMemAlloc->local_mem_block_addr[i];
					pMemAlloc->memory_alloc_hook( pMemAlloc->memory_alloc_hook_context, p, 0, 0 );
					pMemAlloc->local_mem_block_state[i] = 0;
					pMemAlloc->local_mem_block_addr[i] = 0;
					pMemAlloc->inuse_num--;
				}
			}
		}

	} else
	if ( pMemAlloc->local_buffer_data != NULL )
	{
		for ( i = 0; i< pMemAlloc->local_mem_block_num; i++ )
		{
			pMemAlloc->local_mem_block_state[i] = 0;
		}
		pMemAlloc->inuse_num = 0;
	}
}

static void SetupMemoryAlloc( MEMORY_ALLOC *pMemAlloc, MEM_ALLOC_HOOK pfnMemAllocHook, void* pMemAllocHookContext, int nNodeNum, int nBlockSize )
{
	ReleaseMemoryAlloc( pMemAlloc );
	InitMemoryAlloc( pMemAlloc, pfnMemAllocHook, pMemAllocHookContext, nNodeNum, nBlockSize );
}


static void* RequestBlockMemory( MEMORY_ALLOC *pMemAlloc, int nSize )
{
	uint8_t* memory_start;
	int i;
	if ( pMemAlloc->memory_alloc_hook != NULL )
	{
		for ( i = 0; i < pMemAlloc->local_mem_block_num; i++ )
			if ( pMemAlloc->local_mem_block_state[i] == 0 )
				break;
		ASSERT( i < pMemAlloc->local_mem_block_num );
		memory_start = pMemAlloc->memory_alloc_hook( pMemAlloc->memory_alloc_hook_context, NULL, nSize, 1 );
		if ( memory_start )
		{
			pMemAlloc->local_mem_block_addr[i] = (int8_t*)memory_start;
			pMemAlloc->local_mem_block_state[i] = 1;
			pMemAlloc->inuse_num++;
		}
		return memory_start;
	} else
	{
		ASSERT( pMemAlloc->local_buffer_data != NULL && pMemAlloc->local_mem_block_num > 0 );
		for ( i = 0; i < pMemAlloc->local_mem_block_num; i++ )
			if ( pMemAlloc->local_mem_block_state[i] == 0 )
				break;
		//ASSERT( i < pMemAlloc->local_mem_block_num );
		if ( i>=pMemAlloc->local_mem_block_num )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("ERROR: out of local memeory, (total:%d)"), pMemAlloc->local_mem_block_num  ));
			return 0;
		}
		memory_start = (uint8_t*)pMemAlloc->local_mem_block_addr[i];
		pMemAlloc->local_mem_block_state[i] = 1;
		pMemAlloc->inuse_num++;
		return memory_start;
	}
}

static void ReleaseBlockMemory( MEMORY_ALLOC *pMemAlloc, void* pBuffer )
{
	int i;
	if ( pMemAlloc->memory_alloc_hook != NULL )
	{
		for ( i = 0; i < pMemAlloc->local_mem_block_num; i++ )
		{
			if ( pBuffer == pMemAlloc->local_mem_block_addr[i] )
			{
				pMemAlloc->local_mem_block_state[i] = 0;
				pMemAlloc->local_mem_block_addr[i] = NULL;
				break;
			}
		}
		pMemAlloc->memory_alloc_hook( pMemAlloc->memory_alloc_hook_context, pBuffer, 0, 0 );
		ASSERT( i < pMemAlloc->local_mem_block_num );
	} else
	{
		for ( i = 0; i < pMemAlloc->local_mem_block_num; i++ )
		{
			if ( pBuffer == pMemAlloc->local_mem_block_addr[i] )
			{
				pMemAlloc->local_mem_block_state[i] = 0;
				break;
			}
		}
		ASSERT( i < pMemAlloc->local_mem_block_num );
	}
	pMemAlloc->inuse_num--;
}

///////////////////////////////////////////////////////////////////////////////

static int RequestESBuffer( DEMUXER* pDemuxer, TRACK* pTrack )
{
	BLOCK_BUFFER *block_buffer = NULL;
	uint8_t* buffer_start;

	if ( pTrack->channel_index >= pDemuxer->tracks[pTrack->slot_index]->total_track )
		return 0;
	
	pTrack->es_data_start = NULL;
	pTrack->es_block_size = 0;
	pTrack->es_data_bytes = 0;
	pTrack->buffer_start = NULL;
	pTrack->buffer_size = 0;

	buffer_start = RequestBlockMemory( &pDemuxer->memory_alloc[pTrack->slot_index], 
		                               pDemuxer->fifo_buffer[pTrack->slot_index]->block_buffer_size );
	if ( buffer_start == NULL )
		return 0;

	block_buffer = RequestFIFOBuffer( pDemuxer->fifo_buffer[pTrack->slot_index] );
	if ( block_buffer == NULL )
	{
		return 0;
	} else
	{
		block_buffer->start_cue = pTrack->cue; //ZQ...current scr
		block_buffer->slot_index  = pTrack->slot_index;
		block_buffer->track_index = pTrack->channel_index ;
		block_buffer->buffer_start = buffer_start;
		block_buffer->buffer_size  = pDemuxer->fifo_buffer[pTrack->slot_index]->block_buffer_size;
		block_buffer->data_start   = buffer_start;
		block_buffer->data_size  = 0;

		pTrack->buffer_index  = block_buffer->index;
		pTrack->buffer_start  = block_buffer->buffer_start ;
		pTrack->buffer_size   = block_buffer->buffer_size;
		pTrack->es_data_start = block_buffer->buffer_start + PACK_SPACE; //pTrack->buffer_start + PACK_SPACE;
		pTrack->es_block_size = block_buffer->buffer_size  - PACK_SPACE; //block_buffer->buffer_size-PACK_SPACE;
		pTrack->es_data_bytes = 0;

		ASSERT( pTrack->es_block_size <= pTrack->buffer_size-PACK_SPACE );
	}
	return 1;
}

static void ReleaseESBuffer( DEMUXER* pDemuxer,  BLOCK_BUFFER *pBlockBuffer )
{
	ReleaseBlockMemory( &pDemuxer->memory_alloc[pBlockBuffer->slot_index], (void*)pBlockBuffer->buffer_start );
	ReleaseBlockBuffer( pDemuxer->fifo_buffer[pBlockBuffer->slot_index],  pBlockBuffer );
	ASSERT( pDemuxer->fifo_buffer[pBlockBuffer->slot_index]->block_buffer_inuse == pDemuxer->memory_alloc[pBlockBuffer->slot_index].inuse_num );
}

void raw_es_data_dump_file( DEMUXER *pDemuxer, TRACK *pTrack, int bGroupStart, uint8_t*p, int bytes  );
static int ProcessESBuffer( DEMUXER* pDemuxer, TRACK* pTrack )
{
	uint8_t *p;
	int bytes;
	BLOCK_BUFFER *block_buffer;
	BLOCK_BUFFER *block_buffer_out=NULL;
	BLOCK_BUFFER  tmp_block_buffer;

	if ( pTrack->es_data_start == NULL )
		return 1;

	if ( pTrack->group_start & ES_GROUP_START )
	{
		if ( pTrack->es_elmnt->pes.packet_length == 0 )
		{
			uint16_t packet_length = pTrack->es_data_bytes - 6;
			pTrack->es_data_start[4] = (packet_length >> 8 ) & 0xff;
			pTrack->es_data_start[5] = (packet_length ) & 0xff;
		}
	}

	if ( (pDemuxer->ts_parser && IsPESHeaderInTs( pTrack->es_data_start, pTrack->es_data_bytes )) ||
		 (pDemuxer->ps_parser && IsPESHeaderInPs( pTrack->es_data_start, pTrack->es_data_bytes )) )
	{
		p = pTrack->es_data_start + pTrack->es_elmnt->pes.header_length +  pTrack->es_elmnt->private_bytes;
		bytes = pTrack->es_data_bytes - pTrack->es_elmnt->pes.header_length - pTrack->es_elmnt->private_bytes;
	} else
	{
		p = pTrack->es_data_start;
		bytes = pTrack->es_data_bytes;
	}

	if ( pTrack->buffer_index == 0xffff )
	{
		tmp_block_buffer.state = 0;
		block_buffer = &tmp_block_buffer;
		block_buffer->pes = pTrack->es_elmnt->pes;
		block_buffer->buffer_start = pTrack->buffer_start;
		block_buffer->buffer_size = pTrack->buffer_size;
		block_buffer->data_start = p;
		block_buffer->data_size = bytes;
		//block_buffer->start_cue = pTrack->start_cue;  //ZQ...
		block_buffer->end_cue   = pTrack->cue;
		block_buffer->group_start = pTrack->group_start;// & ES_GROUP_START;
		block_buffer->fifo_index = 0xffff;
		block_buffer->slot_index  = pTrack->slot_index;
		block_buffer->track_index = pTrack->channel_index;
		block_buffer_out = &tmp_block_buffer;

	} else
	{
		ASSERT( pDemuxer->fifo_buffer[pTrack->slot_index]->block_buffer_inuse == 
				pDemuxer->fifo_buffer[pTrack->slot_index]->num_of_in_queue + pDemuxer->fifo_buffer[pTrack->slot_index]->num_of_out_queue );
		block_buffer = &pDemuxer->fifo_buffer[pTrack->slot_index]->block_buffer_pool[pTrack->buffer_index];
		block_buffer->pes = pTrack->es_elmnt->pes;
		block_buffer->data_start = p;
		block_buffer->data_size = bytes;
		//block_buffer->start_cue = pTrack->start_cue;  //ZQ...
		block_buffer->end_cue   = pTrack->cue;
		block_buffer->group_start = pTrack->group_start;// & ES_GROUP_START;
		ASSERT( block_buffer->track_index == pTrack->channel_index );

		if ( block_buffer->fifo_index != 0xffff )
		{
			//fifo data
			PushBlockBuffer( pDemuxer->fifo_buffer[pTrack->slot_index], block_buffer );
		} else
		{
			//out of order data fifo (kicked out off fifo) 
			block_buffer_out = block_buffer;
			block_buffer->start_cue = pDemuxer->output_cue+1; //PCR/SCR is outof order, correct it anyway//ZQ...
			ASSERT( pDemuxer->fifo_buffer[pTrack->slot_index]->num_of_out_queue );
			pDemuxer->fifo_buffer[pTrack->slot_index]->num_of_out_queue--;
			pDemuxer->out_of_order_blocks++;
		}
	}
	
	//pop out block buffers for output
	while ( 1 ) 
	{
		if ( block_buffer_out == NULL && pDemuxer->fifo_buffer[pTrack->slot_index] != NULL  )
			block_buffer_out = PopBlockBuffer( pDemuxer->fifo_buffer[pTrack->slot_index] );

		if ( block_buffer_out == NULL ) 
			break;
		pDemuxer->output_cue = block_buffer_out->start_cue;
		if ( pDemuxer->dumper.block_data_dumper != NULL )
		{
			pDemuxer->dumper.block_data_dumper( pDemuxer->dumper.block_data_dumper_context, block_buffer_out, sizeof(BLOCK_BUFFER) );
		}

		if ( pDemuxer->fifo_buffer[pTrack->slot_index] != NULL )
		{
			ReleaseESBuffer( pDemuxer, block_buffer_out );
		}

		block_buffer_out = NULL;
	}

	//debug dump content data
	if ( debug_dump_content  )
	{ 
		raw_es_data_dump_file( pDemuxer, pTrack, (pTrack->group_start & ES_GROUP_START), p, bytes  );
	}

	return 1;
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

void FlushFIFOBuffer( DEMUXER* pDemuxer, TRACK* pTrack )
{
	BLOCK_BUFFER *block_buffer_out=NULL;

	//pop out ready block buffers for output
	while ( pDemuxer->fifo_buffer[pTrack->slot_index]->num_of_in_queue ) 
	{
		block_buffer_out = PopBlockBuffer( pDemuxer->fifo_buffer[pTrack->slot_index] );

		if ( block_buffer_out == NULL ) 
			break;

		if ( pDemuxer->dumper.block_data_dumper != NULL )
		{
			pDemuxer->dumper.block_data_dumper( pDemuxer->dumper.block_data_dumper_context, block_buffer_out, sizeof(BLOCK_BUFFER) );
		}

		if ( pDemuxer->fifo_buffer[pTrack->slot_index] != NULL )
		{
			ReleaseESBuffer( pDemuxer,  block_buffer_out );
		}

		block_buffer_out = NULL;

	}

	///* We can't don't this, because in Linux the pushdata thread (thread racing) may push data into ProcessESBuffer() 
	////after fflush.
	////release not ready block buffers
	//while ( pDemuxer->fifo_buffer[pTrack->slot_index]->num_of_in_queue ) 
	//{
	//	//fake a ready buffer
	//	block_buffer_out = TopBlockBuffer( pDemuxer->fifo_buffer[pTrack->slot_index] );
	//	if ( block_buffer_out->fifo_index != 0xffff )
	//	{
	//		PushBlockBuffer( pDemuxer->fifo_buffer[pTrack->slot_index], block_buffer_out );
	//	}

	//	block_buffer_out = PopBlockBuffer( pDemuxer->fifo_buffer[pTrack->slot_index] );

	//	if ( block_buffer_out == NULL ) 
	//		break;

	//	if ( pDemuxer->fifo_buffer[pTrack->slot_index] != NULL )
	//	{
	//		ReleaseESBuffer( pDemuxer,  block_buffer_out );
	//	}

	//	block_buffer_out = NULL;

	//}
	//SageLog(( _LOG_TRACE, 2, TEXT("Flush FIFO Buffer. %d slot:%d"), 
	//	pDemuxer->fifo_buffer[pTrack->slot_index]->num_of_in_queue, pTrack->slot_index  ));
}


//static void PostStatusMessage( DEMUXER *pDemuxer, char* pMessageText )
//{
//	if ( IS_PS_TYPE( pDemuxer->source_format ) )
//	{
//		PS_PARSER *pPSParser = pDemuxer->ps_parser;
//		if ( pPSParser->dumper.message_dumper != NULL )
//		{
//			MESSAGE_DATA message={0};
//			strncpy( message.title, "STATUS", sizeof(message.title) );
//			message.message = pMessageText;
//			message.message_length = (uint16_t)strlen(pMessageText)+1 ;
//			message.buffer = pMessageText;
//			message.buffer_length = message.message_length ;
//		
//			pPSParser->dumper.message_dumper( pPSParser->dumper.message_dumper_context, &message, sizeof(message) );
//		}
//	}
//	if ( IS_TS_TYPE( pDemuxer->source_format ) )
//	{
//		TS_PARSER *pTSParser = pDemuxer->ts_parser;
//		if ( pTSParser->dumper.message_dumper != NULL )
//		{
//			MESSAGE_DATA message={0};
//			strncpy( message.title, "STATUS", sizeof(message.title) );
//			message.message = pMessageText;
//			message.message_length = (uint16_t)strlen(pMessageText)+1 ;
//			message.buffer = pMessageText;
//			message.buffer_length = message.message_length ;
//		
//			pTSParser->dumper.message_dumper( pTSParser->dumper.message_dumper_context, &message, sizeof(message) );
//		}
//	}
//}
//

//////////////////////////////////////////////////////////////////////////////////

//static int GetDigitsNum( char* pString, int *pNum )
//{
//	int i = 0;
//	int num = 0;
//	while( pString[i] == ' ' ) i++;
//
//	while ( pString[i] >= '0' && pString[i] <= '9' )
//	{
//		num = num*10 + pString[i]-'0';
//		i++;
//	}
//
//	if ( i > 0 ) 
//		*pNum = num;
//
//	return i;
//}

int CheckFormat( const uint8_t* pData, int nBytes )
{
	int ts_score = 0, m2t_score = 0, asi_score, ps_score = 0;
	ts_score = CheckTSFormat( pData, nBytes );
	m2t_score =  CheckM2TFormat( pData, nBytes );
	asi_score = CheckASIFormat( pData, nBytes );
	ps_score = CheckPSFormat( pData, nBytes );

	if ( ts_score == 0 && m2t_score == 0 && ps_score == 0 && asi_score )
		return 0;

	if ( ts_score > m2t_score && ts_score >= ps_score && ts_score > asi_score )
		return MPEG_TS;
	if ( m2t_score > ts_score && m2t_score >= ps_score && m2t_score > asi_score )
		return MPEG_M2TS;
	if ( ps_score> ts_score && ps_score > m2t_score && ps_score > asi_score  )
		return MPEG_PS;
	if ( asi_score> ts_score && asi_score > m2t_score && asi_score > ps_score  )
		return MPEG_ASI;

	return 0;
}
////////////////////////////////////////////////////////////////////////////////////////////////////
// File Demux
////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////

static int CreateTSFileSource( DEMUXER* pDemuxer, char* pFileName, int nFileFormat, TUNE *pTune )
{
	int fp;
#ifdef WIN32
	//fp = FOPEN( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	fp = _sopen( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD );
#else
#ifdef 	O_LARGEFILE
	fp  = open( pFileName, O_RDONLY|O_LARGEFILE );
#else
	fp  = open( pFileName, O_RDONLY );
#endif
#endif	

	if ( fp < 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("file %s can't be open"), pFileName ));
		return 0;
	}

	SageLog(( _LOG_TRACE, 3, TEXT("file %s"), pFileName ));
	if ( OpenTSChannel( pDemuxer->ts_parser, 0, pDemuxer->tracks[0], pTune ) == 0 )
	{
		FCLOSE( fp );
		return 0;
	}
	pDemuxer->source_file = fp;
	return 1;
}

static int CreateTSFileSourceW( DEMUXER* pDemuxer, wchar_t* pFileName, int nFileFormat, TUNE *pTune )
{
	int fp;
#ifdef WIN32
	//fp = FOPEN( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );-
	fp = _wsopen( (wchar_t*)pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD );
#else
#ifdef 	O_LARGEFILE
	fp  = open( pFileName, O_RDONLY|O_LARGEFILE );
#else
	fp  = open( (char*)pFileName, O_RDONLY );
#endif
#endif	

	if ( fp < 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("file %s can't be open"), pFileName ));
		return 0;
	}

	SageLog(( _LOG_TRACE, 3, TEXT("file %s"), pFileName ));
	if ( OpenTSChannel( pDemuxer->ts_parser, 0, pDemuxer->tracks[0], pTune ) == 0 )
	{
		FCLOSE( fp );
		return 0;
	}
	pDemuxer->source_file = fp;
	return 1;
}


/* multiple channel per file source
	//InitTSTrack( pDemuxer, 1, MAX_TRACK_NUM );
	//OpenTSChannel( pDemuxer->ts_parser, 1, pDemuxer->tracks[1], &tune ); 
	//ReleaseTSTrack( pDemuxer, 1 );
*/
static void ReleaseTSFileSource( DEMUXER* pDemuxer )
{
	CloseTSChannel( pDemuxer->ts_parser, 0 );
}

static int CreatePSFileSource( DEMUXER* pDemuxer, char* pFileName )
{
	int fp;
#ifdef WIN32	
	//fp = FOPEN( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	fp = _sopen( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD );
	if ( fp < 0 )
		fp = _wsopen( (wchar_t*)pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD );
#else
	fp  = open( pFileName, O_RDONLY );	
#endif	
	if ( fp < 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("file %s can't be open"), pFileName ));
		return 0;
	}
	SageLog(( _LOG_TRACE, 3, TEXT("file %s"), pFileName ));
	pDemuxer->source_file = fp;

	return 1;
}

static int CreatePSFileSourceW( DEMUXER* pDemuxer, wchar_t* pFileName )
{
	int fp;
#ifdef WIN32	
	//fp = FOPEN( pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
	fp = _wsopen( (wchar_t*)pFileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD );
#else
	fp  = open( (char*)pFileName, O_RDONLY );
#endif	
	if ( fp < 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("file %s can't be open"), pFileName ));
		return 0;
	}
	SageLog(( _LOG_TRACE, 3, TEXT("file %s"), pFileName ));
	pDemuxer->source_file = fp;

	return 1;
}

static void ReleasePSFileSource( DEMUXER* pDemuxer )
{
	
}

int DetectFileType( char* pFileName )
{
	char buf[32*1024];
	int fp;
	int bytes, count;
	int file_type=0;

	//deteck file format TS, M2T, PS
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
	while ( count++ < 10 )
	{
		bytes = (int)read( fp, buf, sizeof(buf) );
		if ( bytes <= 0 )
			break;
		file_type = CheckFormat( (uint8_t*)buf, bytes );
		if ( file_type != 0 )
			break;
	}
	FCLOSE( fp );
	if ( file_type == 0)
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Unknown file format %s"), pFileName ));
	}

	return file_type;

}

int DetectFileTypeW( wchar_t* pFileName )
{
	char buf[32*1024];
	int fp;
	int bytes, count;
	int file_type;

	//deteck file format TS, M2T, PS
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
		file_type = CheckFormat( (uint8_t*)buf, bytes );
		if ( file_type != 0 )
			break;
	}
	FCLOSE( fp );
	if ( file_type == 0)
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Unknown file format %s"), pFileName ));
	}

	return file_type;

}

DEMUXER* CreateDemuxer( int nStreamType, int nTrackNum, int nESBlockSize )
{
	DEMUXER* pDemuxer = NULL;
	if ( IS_TS_TYPE( nStreamType ) )
	{
		pDemuxer = CreateTsDemuxer( nTrackNum, nStreamType, nESBlockSize );
		pDemuxer->source_format = nStreamType ;
		pDemuxer->block_size = nESBlockSize;
	}
	else
	if ( IS_PS_TYPE( nStreamType ) )
	{
		pDemuxer = CreatePsDemuxer( nTrackNum );
		pDemuxer->source_format = nStreamType ;
		pDemuxer->block_size = nESBlockSize;
	}
	return pDemuxer;
}

void ReleaseDemuxer( DEMUXER* pDemuxer )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		ReleaseTsDemuxer( pDemuxer );
	else
	if ( IS_PS_TYPE( pDemuxer->source_format ) )
		ReleasePsDemuxer( pDemuxer );
}

void ResetDemuxerAll( DEMUXER* pDemuxer )
{
	pDemuxer->out_of_order_blocks = 0;
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		ResetTsDemuxer( pDemuxer );
	else
	if ( IS_PS_TYPE( pDemuxer->source_format ) )
		ResetPsDemuxer( pDemuxer );
}

void ResetDemuxer( DEMUXER* pDemuxer, int nSlot )
{
	pDemuxer->out_of_order_blocks = 0;
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		ResetTsDemuxerSlot( pDemuxer, nSlot );
	}
	else
	if ( IS_PS_TYPE( pDemuxer->source_format ) )
		ResetPsDemuxer( pDemuxer );
}

void FlushDemuxer( DEMUXER* pDemuxer, int nSlot )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		int i;
		TRACK *track;
		for ( i = 0; i<pDemuxer->ts_parser->slot[nSlot].tracks->number_track; i++ )
		{
			track= &pDemuxer->ts_parser->slot[nSlot].tracks->track[i];
			FlushFIFOBuffer( pDemuxer, track );
		}
	}
}

int OpenFileSource( DEMUXER *pDemuxer, char* pFileName, int nFileFormat,  TUNE* pTune )
{
	int ret;

	//if it's TS format
	if ( IS_TS_TYPE( nFileFormat ) )
	{
		ret = CreateTSFileSource( pDemuxer, pFileName, nFileFormat, pTune );
		if ( !ret )
		{
			return 0;
		}

		pDemuxer->input_buffer_size = BUFFER_SIZE-(BUFFER_SIZE%pDemuxer->ts_parser->packet_length); //
		pDemuxer->input_buffer = SAGETV_MALLOC( pDemuxer->input_buffer_size );
		pDemuxer->data = pDemuxer->input_buffer;
		pDemuxer->size = pDemuxer->input_buffer_size;

	} else
	//if it's PS format
	if ( IS_PS_TYPE( nFileFormat ) )
	{
		ret = CreatePSFileSource( pDemuxer, pFileName );
		if ( !ret )
		{
			ReleasePsDemuxer( pDemuxer );
			return 0;
		}

		pDemuxer->input_buffer_size = BUFFER_SIZE+PACK_SPACE;
		pDemuxer->input_buffer = SAGETV_MALLOC( pDemuxer->input_buffer_size );
		pDemuxer->data = pDemuxer->input_buffer + PACK_SPACE;
		pDemuxer->size = BUFFER_SIZE;
	}

	pDemuxer->language_code = LanguageCode((uint8_t*)"eng");

	return 1;

}

int OpenFileSourceW( DEMUXER *pDemuxer, wchar_t* pFileName, int nFileFormat,  TUNE* pTune )
{
	int ret;

	//if it's TS format
	if ( IS_TS_TYPE( nFileFormat ) )
	{
		ret = CreateTSFileSourceW( pDemuxer, pFileName, nFileFormat, pTune );
		if ( !ret )
		{
			return 0;
		}

		pDemuxer->input_buffer_size = BUFFER_SIZE-(BUFFER_SIZE%pDemuxer->ts_parser->packet_length); //
		pDemuxer->input_buffer = SAGETV_MALLOC( pDemuxer->input_buffer_size );
		pDemuxer->data = pDemuxer->input_buffer;
		pDemuxer->size = pDemuxer->input_buffer_size;

	} else
	//if it's PS format
	if ( IS_PS_TYPE( nFileFormat ) )
	{
		ret = CreatePSFileSourceW( pDemuxer, pFileName );
		if ( !ret )
		{
			ReleasePsDemuxer( pDemuxer );
			return 0;
		}

		pDemuxer->input_buffer_size = BUFFER_SIZE+PACK_SPACE;
		pDemuxer->input_buffer = SAGETV_MALLOC( pDemuxer->input_buffer_size );
		pDemuxer->data = pDemuxer->input_buffer + PACK_SPACE;
		pDemuxer->size = BUFFER_SIZE;
	}

	pDemuxer->language_code = LanguageCode((uint8_t*)"eng");

	return 1;

}

void CloseFileSource( DEMUXER *pDemuxer )
{
	if ( pDemuxer->source_file > 0 )
		FCLOSE(pDemuxer->source_file);

	SAGETV_FREE( pDemuxer->input_buffer );
	_input_statistic( pDemuxer );

	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		ReleaseTSFileSource( pDemuxer );

	} else
	if ( IS_PS_TYPE( pDemuxer->source_format ) )
	{
		ReleasePSFileSource( pDemuxer );
	}
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////
int OpenStreamSource( DEMUXER *pDemuxer, int nFileFormat, TUNE* pTune )
{
	//if it's TS format
	if ( IS_TS_TYPE( nFileFormat ) )
	{
		if ( OpenTSChannel( pDemuxer->ts_parser, 0, pDemuxer->tracks[0], pTune ) == 0 )
		{
			return 0;
		}

	} else
	//if it's PS format
	if ( IS_PS_TYPE( nFileFormat ) )
	{

	}

	pDemuxer->language_code = LanguageCode((uint8_t*)"eng");

	return 1;
}

void CloseStreamSource( DEMUXER *pDemuxer )
{
	SAGETV_FREE( pDemuxer->input_buffer );
	_input_statistic( pDemuxer );
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		CloseTSChannel( pDemuxer->ts_parser, 0 );
	} else
	if ( IS_PS_TYPE( pDemuxer->source_format ) )
	{
		//do nothing
	}
}


void SetupMessageDumper( DEMUXER* pDemuxer, DUMP pfnMessagDumper, void* pMessagDumperContext )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		pDemuxer->ts_parser->dumper.message_dumper = pfnMessagDumper;
		pDemuxer->ts_parser->dumper.message_dumper_context = pMessagDumperContext;
	} else
	if ( IS_PS_TYPE( pDemuxer->source_format ) )
	{
		pDemuxer->ps_parser->dumper.message_dumper = pfnMessagDumper;
		pDemuxer->ps_parser->dumper.message_dumper_context = pMessagDumperContext;
	}

}

void SetupPCRDumper( DEMUXER* pDemuxer, DUMP pfnPCRDumper, void* pPCRDumperContext )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		pDemuxer->ts_parser->dumper.pcr_dumper = pfnPCRDumper;
		pDemuxer->ts_parser->dumper.pcr_dumper_context = pPCRDumperContext;
	} else
	if ( IS_PS_TYPE( pDemuxer->source_format ) )
	{
		pDemuxer->ps_parser->dumper.scr_dumper = pfnPCRDumper;
		pDemuxer->ps_parser->dumper.scr_dumper_context = pPCRDumperContext;
	}
}

void SetupBlockDataDumper( DEMUXER* pDemuxer, DUMP pfnBlockDataDmper, void* pMsgBlockDataDumperContext )
{
	pDemuxer->dumper.block_data_dumper = pfnBlockDataDmper;
	pDemuxer->dumper.block_data_dumper_context = pMsgBlockDataDumperContext;
}

void SetupTSProgramDataDump( DEMUXER* pDemuxer, DUMP pfnProgramDataDump, void* pfnProgramDataContext )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		pDemuxer->ts_parser->dumper.program_dumper = pfnProgramDataDump;
		pDemuxer->ts_parser->dumper.program_dumper_context = pfnProgramDataContext;
	}
}

//////////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////// Push File Data //////////////////////////////////////
//return 0: data is used up; return 1: if pfnProgressCallback != NULL; avformat found
int PumpFileData( DEMUXER *pDemuxer, ULONGLONG lMaxLimitBytes, DUMP pfnProgressCallback, void* pCallbaclContext  )
{
	ULONGLONG total_bytes = 0;
	int flag;
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		int bytes;
		int used_bytes, read_bytes, read_offset;
		read_bytes = pDemuxer->size;
		read_offset = 0;
		while ( 1 /*!eof( pDemuxer->source_file )*/ )
		{ 
			bytes = (int)read( pDemuxer->source_file, pDemuxer->data+read_offset, read_bytes  );
			if ( bytes == 0 )
				return 0;

			bytes += read_offset;

			used_bytes = PushDataTSParser( pDemuxer->ts_parser, pDemuxer->data, bytes );


			if ( used_bytes < bytes )
				memcpy( pDemuxer->data, pDemuxer->data+used_bytes, bytes-used_bytes ); //we have less 188/192 bytes moving around

			read_offset = bytes-used_bytes;
			read_bytes = pDemuxer->size-read_offset;

			total_bytes += bytes;
			if ( lMaxLimitBytes && lMaxLimitBytes <= total_bytes ) 
			{
				return 0;
			}
			if ( pfnProgressCallback != NULL )
			{
				flag = pfnProgressCallback( pCallbaclContext, &total_bytes, sizeof(total_bytes) );
				if ( flag == 0 ) 
					return 1;
			}
		}

	} else
	if ( IS_PS_TYPE ( pDemuxer->source_format ) )
	{
		int bytes, read_bytes, used_bytes=0,  read_offset=0, push_bytes=0, push_offset=0;
		int expected_bytes = 0, skip_bytes = 0;;
		read_bytes = pDemuxer->size/2;
		//while ( !eof( pDemuxer->source_file) )
		while( 1 )
		{ 
			bytes = (int)read( pDemuxer->source_file, pDemuxer->data+read_offset, read_bytes  );
			if ( bytes == 0 )
				return 0;

			push_bytes += bytes;

			if ( skip_bytes > 0 )
			{
				if ( skip_bytes > push_bytes  )
				{
					skip_bytes -= push_bytes;
					push_bytes  = 0;
				} else
				{
					push_offset += skip_bytes;
					push_bytes  -= skip_bytes;
					skip_bytes = 0;
				}
			} 

			used_bytes = PushDataPSParser( pDemuxer->ps_parser, pDemuxer->data+push_offset, push_bytes, &expected_bytes );

			if ( expected_bytes == 0 )
			{
				if ( used_bytes < push_bytes )
				{
					//we have less 16 bytes of a pack header moving around 
					memcpy( pDemuxer->data, pDemuxer->data+push_offset+used_bytes, push_bytes-used_bytes ); 
					read_offset = push_bytes-used_bytes;
					read_bytes = pDemuxer->size/2-read_offset;
				} else
				{
					read_offset = 0;
					read_bytes = pDemuxer->size/2;
				}

				push_offset = 0;
				push_bytes = read_offset;
				
			} else
			{
				push_offset += used_bytes;
				push_bytes  -= used_bytes;
				read_offset += read_bytes;
				read_bytes  = expected_bytes;
				if ( read_offset + read_bytes > (int)pDemuxer->size ) //a pading es may have a big size
				{
					uint8_t *p = pDemuxer->data+push_offset;
					if (!( p[0] == 0x0 &&  p[1] == 0x0 && p[2] == 0x01 && p[3] == 0xbe ) ) //if not a long padding ES, 
					{
						SageLog(( _LOG_TRACE, 3, TEXT("WARNING:Drop a excessive size (%d, max:%d) ES block. %s"), 
							            expected_bytes + push_bytes, pDemuxer->size, _pes_header_(  p ) ));
					}
					skip_bytes = expected_bytes;
					read_offset = 0;
					read_bytes = pDemuxer->size/2;
					push_offset = 0;
					push_bytes = 0;
				}
			}

			total_bytes += bytes;
			if ( lMaxLimitBytes && lMaxLimitBytes <= total_bytes ) 
			{
				return 0;
			}
			if ( pfnProgressCallback != NULL )
			{
				flag = pfnProgressCallback( pCallbaclContext, &total_bytes, sizeof(total_bytes) );
				if ( flag == 0 ) 
					return 1;
			}

		}
	}
	return 1;
}

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////// Push File Data //////////////////////////////////////
int	PushDemuxStreamData( void* Handle, uint8_t *pData, int nBytes, int *nExpectedBytes )
{
	DEMUXER *pDemuxer = (DEMUXER *)Handle;
	int used_bytes = 0;

	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		used_bytes = PushDataTSParser( pDemuxer->ts_parser, pData, nBytes );
		if ( nBytes - used_bytes > 0 && nBytes - used_bytes < pDemuxer->ts_parser->packet_length )
		{
			*nExpectedBytes = pDemuxer->ts_parser->packet_length - (nBytes - used_bytes);
		} else
			*nExpectedBytes = 0;

	} else
	if ( IS_PS_TYPE( pDemuxer->source_format ) )
	{
		used_bytes = PushDataPSParser( pDemuxer->ps_parser, pData, nBytes, nExpectedBytes );
	}

	return used_bytes;
}

////////////////////////////////////////////////////////////////////////////////////////////////////

void* OpenDemuxStream( TUNE* pTune, 
					   int nInputFormat, int nOutputFormat, 
					   MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext,
					   DUMP pfnOutputDump, void* pOutputDumpContext )
{
	DEMUXER *pDemuxer;
	int ret;

	//if not specify a input format, detect it
	if ( nInputFormat == 0 )
		return NULL;

	pDemuxer = CreateDemuxer( nInputFormat, MAX_TRACK_NUM, ES_BUFFER_SIZE );

	//pDemuxer->remuxer_ctrl = MAIN_TRACK_ONLY;
	//pDemuxer->task = nTask;

	SetupMemAllocHook( pDemuxer, pfnMemAlloc, pMemAllocContext );

	ret = OpenStreamSource( pDemuxer, nInputFormat,  pTune );
	if ( !ret )
	{
		ReleaseDemuxer( pDemuxer );
		return NULL;
	}

	pDemuxer->state = 1;

	return pDemuxer;
}

void CloseDemuxStream( void* Handle )
{
	DEMUXER *pDemuxer = (DEMUXER *)Handle;

	FlushDemuxer( pDemuxer, 0 );

	CloseStreamSource( pDemuxer );
	ReleaseDemuxer( pDemuxer );

}

void ResetDemuxStream( void* Handle )
{
	DEMUXER *pDemuxer = (DEMUXER *)Handle;
	ResetDemuxerAll( pDemuxer );
}

void FlushDemuxStream( void* Handle )
{
	DEMUXER *pDemuxer = (DEMUXER *)Handle;
	FlushDemuxer( pDemuxer, 0 );
}

void SetupDemuxStreamTune( void* Handle, TUNE *pTune )
{
	DEMUXER *pDemuxer = (DEMUXER *)Handle;
	if ( !IS_TS_TYPE( pDemuxer->source_format ) )
		return;
	
	SetupTune( pDemuxer->ts_parser, 0, pTune );
}


void SetupDemuxTSStreamFormat( void* Handle, int nFormat, int nSubFormat )
{
	DEMUXER *pDemuxer = (DEMUXER *)Handle;
	if ( !IS_TS_TYPE( pDemuxer->source_format ) )
		return;
	SetupStreamFormat( pDemuxer->ts_parser, nFormat, nSubFormat );
}


ULONGLONG DemuxUsedBytes( DEMUXER *pDemuxer )
{
	//rewind data to begin, restart
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		return TSDataUsedBytes( pDemuxer->ts_parser);
	else
		return PSDataUsedBytes( pDemuxer->ps_parser);
}

int IsSageTVRecording( DEMUXER *pDemuxer )
{
	if ( pDemuxer->ps_parser != NULL )
		return pDemuxer->ps_parser->sagetv_private_data.sage_info_embedded;
	return 0;
}

void PickupSageTVMainTrack( DEMUXER *pDemuxer, TRACKS* pTracks )
{
	if ( pDemuxer->ps_parser != NULL )
		PickupMainTrack( pDemuxer->ps_parser, pTracks );
}

void QueueAbortDemux( DEMUXER *pDemuxer )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		QueueTSParserAbort( pDemuxer->ts_parser );
	else
		QueuePSParserAbort( pDemuxer->ps_parser );
}

void QueueResetDemux( DEMUXER *pDemuxer )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		QueueTSParserReset( pDemuxer->ts_parser );
	else
		QueuePSParserReset( pDemuxer->ps_parser );
}

void QueueZeroDemux( DEMUXER *pDemuxer )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		QueueTSParserZero( pDemuxer->ts_parser );
	else
		QueuePSParserZero( pDemuxer->ps_parser );
}

void SetZeroDemux( DEMUXER *pDemuxer )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		TSParserZero( pDemuxer->ts_parser );
	else
		PSParserZero( pDemuxer->ps_parser );
}

void ResetDemuxCommad( DEMUXER *pDemuxer )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		ResetTSParserCommand( pDemuxer->ts_parser );
	else
		ResetPSParserCommand( pDemuxer->ps_parser );
}

ULONGLONG DemuxSourceSeekPos( DEMUXER *pDemuxer, ULONGLONG lPos, int SeekSet )
{
	return FSEEK( pDemuxer->source_file, lPos, SeekSet );
}

ULONGLONG DemuxSourceLength( DEMUXER *pDemuxer )
{
	ULONGLONG end_pos;
	ULONGLONG cur_pos;
	cur_pos = FSEEK( pDemuxer->source_file, 0, SEEK_CUR );
	end_pos = FSEEK( pDemuxer->source_file, 0, SEEK_END );
	FSEEK( pDemuxer->source_file, cur_pos, SEEK_SET );
	return end_pos;

}

ULONGLONG DemuxSourceCurPos( DEMUXER *pDemuxer )
{
	return FTELL( pDemuxer->source_file );
}

BLOCK_BUFFER* RequestBlockBuffer( DEMUXER *pDemuxer, TRACK* pTrack )
{
	if ( pDemuxer->fifo_buffer[pTrack->slot_index] == NULL )
		return NULL;
	RequestESBuffer( pDemuxer, pTrack );
	return &pDemuxer->fifo_buffer[pTrack->slot_index]->block_buffer_pool[pTrack->buffer_index];

}
void ReturnBlockBuffer( DEMUXER *pDemuxer, BLOCK_BUFFER* pBlockBuffer )
{
	ReleaseESBuffer( pDemuxer, pBlockBuffer );
	pDemuxer->fifo_buffer[pBlockBuffer->slot_index]->num_of_in_queue--;
}

int PostDemuxMessage( DEMUXER *pDemuxer, MESSAGE_DATA *pMessage )
{
	if ( IS_PS_TYPE( pDemuxer->source_format )  )
	{
		PS_PARSER *pPSParser = pDemuxer->ps_parser;
		if ( pPSParser->dumper.message_dumper != NULL )
		{
			pPSParser->dumper.message_dumper( pPSParser->dumper.message_dumper_context, pMessage, sizeof(MESSAGE_DATA) );
		}
		return 1;
	}
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		TS_PARSER *pTSParser = pDemuxer->ts_parser;
		if ( pTSParser->dumper.message_dumper != NULL )
		{
			pTSParser->dumper.message_dumper( pTSParser->dumper.message_dumper_context, pMessage, sizeof(MESSAGE_DATA) );
		}
		return 1;
	}
	return 0;
}

int GetTSStreamFormat( DEMUXER *pDemuxer )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		return pDemuxer->ts_parser->stream_format;
	return 0;
}
int GetTSStreamSubFormat( DEMUXER *pDemuxer )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
		return pDemuxer->ts_parser->stream_subformat;
	return 0;
}

uint32_t GetInputVideoPacketCount( DEMUXER *pDemuxer, int nSlot )
{
	uint32_t i, sum=0;
	if ( IS_PS_TYPE( pDemuxer->source_format )  )
	{
		for ( i = 0; i<MAX_TRACK_NUM; i++ )
		{
			if ( pDemuxer->tracks[nSlot]->track[i].es_elmnt && 
				 pDemuxer->tracks[nSlot]->track[i].es_elmnt->content_type == VIDEO_DATA )
				sum += pDemuxer->tracks[nSlot]->track[i].es_blocks_counter;
		}

	} else
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		for ( i = 0; i<MAX_TRACK_NUM; i++ )
		{
			if ( pDemuxer->tracks[nSlot]->track[i].ts_elmnt && 
				 pDemuxer->tracks[nSlot]->track[i].ts_elmnt->content_type == VIDEO_DATA )
				sum += pDemuxer->tracks[nSlot]->track[i].ts_packets_counter;
		}
	}
	return sum;
}

uint32_t GetInputAudioPacketCount( DEMUXER *pDemuxer, int nSlot )
{
	uint32_t i, sum=0;
	if ( IS_PS_TYPE( pDemuxer->source_format )  )
	{
		for ( i = 0; i<MAX_TRACK_NUM; i++ )
		{
			if ( pDemuxer->tracks[nSlot]->track[i].es_elmnt && 
				 pDemuxer->tracks[nSlot]->track[i].es_elmnt->content_type == AUDIO_DATA )
				sum += pDemuxer->tracks[nSlot]->track[i].es_blocks_counter;
		}
	}
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		for ( i = 0; i<MAX_TRACK_NUM; i++ )
		{
			if ( pDemuxer->tracks[nSlot]->track[i].ts_elmnt && 
				 pDemuxer->tracks[nSlot]->track[i].ts_elmnt->content_type == AUDIO_DATA )
				sum += pDemuxer->tracks[nSlot]->track[i].ts_packets_counter; 
		}
	}
	return sum;
}

int IsVideoDataPresent( DEMUXER *pDemuxer, int nSlot )
{
	return (pDemuxer->tracks[nSlot]->track_attr & ATTR_VIDEO_PRESENT );
}

int IsAudioDataPresent( DEMUXER *pDemuxer, int nSlot )
{
	return (pDemuxer->tracks[nSlot]->track_attr & ATTR_AUDIO_PRESENT );
}

int IsEncryptedData( DEMUXER *pDemuxer, int nSlot )
{
	return (pDemuxer->tracks[nSlot]->track_attr & ATTR_ENCRYPTED_FLAG );
}


int IsEncryptedTSChannel( DEMUXER *pDemuxer, int nSlot )
{
	if ( IS_TS_TYPE( pDemuxer->source_format ) )
	{
		int i, k;
		for ( i = 0; i<MAX_SLOT_NUM; i++ )
		{
			if ( pDemuxer->ts_parser->slot[nSlot].ts_streams.ca_pid != 0x0 )
				return 1;

			if ( ( pDemuxer->ts_parser->slot[nSlot].state &  NO_AV_DATA ) )
				for ( k = 0; k<pDemuxer->ts_parser->slot[nSlot].tracks->number_track; k++ )
					if ( pDemuxer->ts_parser->slot[nSlot].tracks->track[k].scrambling_flag )
						return 1;

		}
	} 

	return 0;
}

void DisablePTSFix( DEMUXER *pDemuxer )
{ 
	if IS_TS_TYPE( pDemuxer->source_format ) 
	{
		pDemuxer->ts_parser->pts_fix_ctrl = 0; 
		SageLog(( _LOG_TRACE, 2, TEXT(" PTSFix is disbaled") ));
	}
}

void DisableSubtitleParsing( DEMUXER *pDemuxer )
{
	if IS_TS_TYPE( pDemuxer->source_format ) 
	{
		pDemuxer->ts_parser->subtitle_ctrl = 0; 
		SageLog(( _LOG_TRACE, 2, TEXT(" Subtitle parsing is disbaled") ));
	}
}

void EnableAudioTSPriorityHack( DEMUXER *pDemuxer )
{
	if IS_TS_TYPE( pDemuxer->source_format ) 
	{
		AudioTSPriorityHackEnable( pDemuxer->ts_parser );
		SageLog(( _LOG_TRACE, 2, TEXT(" Audio TS priority hack is enabled") ));
	}

}

void WaitTSCleanStream( DEMUXER *pDemuxer )
{
	if IS_TS_TYPE( pDemuxer->source_format ) 
	{
		WaitCleanStream( pDemuxer->ts_parser );
		SageLog(( _LOG_TRACE, 2, TEXT(" waiting clean stream is enabled") ));
	}
}

void SetupPESDump( DEMUXER *pDemuxer, DUMP pfnPESDumper, void* pPESDumperContext )
{
	if IS_TS_TYPE( pDemuxer->source_format ) 
	{
		pDemuxer->ts_parser->dumper.pes_dumper = pfnPESDumper;
		pDemuxer->ts_parser->dumper.pes_dumper_context = pPESDumperContext;
	} else
	if IS_PS_TYPE( pDemuxer->source_format ) 
	{
		pDemuxer->ps_parser->dumper.pes_dumper = pfnPESDumper;
		pDemuxer->ps_parser->dumper.pes_dumper_context = pPESDumperContext;
	}
}

void SetupTSATSDump( DEMUXER *pDemuxer, DUMP pfnATSDumper, void* pATSDumperContext )
{
	if IS_TS_TYPE( pDemuxer->source_format ) 
	{
		SetupATSDump( pDemuxer->ts_parser, pfnATSDumper, pATSDumperContext );
	} 
}

void UpdateDemuxerClock( DEMUXER *pDemuxer, uint32_t lClock ) //units 1ms
{
	UpdateTSParserClock( pDemuxer->ts_parser, lClock );
}

LONGLONG SetDemuxPTSOffset( DEMUXER *pDemuxer, LONGLONG llPTSOffset )
{
	LONGLONG old_pts_offset = 0;
	if IS_TS_TYPE( pDemuxer->source_format ) 
	{
		old_pts_offset = pDemuxer->ts_parser->pts_offset;
		pDemuxer->ts_parser->pts_offset = llPTSOffset;
	}
	else
	if IS_PS_TYPE( pDemuxer->source_format ) 
	{
		old_pts_offset = pDemuxer->ps_parser->pts_offset;
		pDemuxer->ps_parser->pts_offset = llPTSOffset;
	}
	return old_pts_offset;
}

//void* CreateTSPacketDumper( DEMUXER *pDemuxer, DUMP pfnStreamDump, 
//		         void* pStreamDumpContext, DUMP pfnIndexDump, void* pIndexDumpContext )
//{
//	if IS_PS_TYPE( pDemuxer->source_format ) 
//		return NULL;
//	return CreateTSFilterDump( pDemuxer->ts_parser, pfnStreamDump, pStreamDumpContext, 
//		                pfnIndexDump, pIndexDumpContext );
//
//}
//void  ReleaseTSPacketDumper( void *pTSFilterDemuxer )
//{
//	ReleaseTSFilterDump( (TS_FILTER_DUMP*)pTSFilterDemuxer );
//}
//
//int   StartTSPacketDumper( void *pTSFilterDemuxer, int nFlag )
//{
//	return StartTSFilterDump( pTSFilterDemuxer, nFlag );
//}
//
//void  StopTSPacketDumper( void *pTSFilterDemuxer )
//{
//	StopTSFilterDump( pTSFilterDemuxer );
//}
//

/////////////////////////////////////// Utility ////////////////////////////////////////////////////////////
void raw_es_data_dump_file( DEMUXER *pDemuxer, TRACK *pTrack, int bGroupStart, uint8_t*p, int bytes  )
{
	if ( bGroupStart &&
		 pDemuxer->track_debug[pTrack->slot_index]->track_dump_file[pTrack->channel_index] == NULL )
	{
		char file_name[_MAX_PATH];
		snprintf( file_name, sizeof(file_name), "dump_%d_%d.bin", pTrack->slot_index, pTrack->channel_index );
		pDemuxer->track_debug[pTrack->slot_index]->track_dump_file[pTrack->channel_index] = fopen( file_name, "wb" );

	}
	if (  bytes && pDemuxer->track_debug[pTrack->slot_index]->track_dump_file[pTrack->channel_index] != NULL )
	{
		fwrite( p, bytes, 1, pDemuxer->track_debug[pTrack->slot_index]->track_dump_file[pTrack->channel_index] );
	}
}


void _input_statistic( DEMUXER *pDemuxer )
{
	int i;
	if ( pDemuxer->ts_parser != NULL )
	{
		if ( pDemuxer->ts_parser->bad_packets )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("Missing SYNC header %d; broken block  %d"),
					pDemuxer->ts_parser->bad_packets, pDemuxer->ts_parser->bad_blocks ));
		}
		SageLog(( _LOG_TRACE, 3, TEXT("Total input packets %d "), pDemuxer->ts_parser->ts_filter->ts_packet_counter ));
		for ( i = 0; i<MAX_TRACK_NUM; i++ )
		{
			if ( pDemuxer->tracks[0]->track[i].ts_elmnt )
				SageLog(( _LOG_TRACE, 3, TEXT("\tch:%d: ts packets:%d es blocks:%d"), i,
						pDemuxer->tracks[0]->track[i].ts_packets_counter, 
						pDemuxer->tracks[0]->track[i].es_blocks_counter ));
		}
	} else
	if ( pDemuxer->ps_parser != NULL )
	{
		if ( pDemuxer->ps_parser->bad_blocks )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("Missing SYNC ES header or broken block  %d"),
				pDemuxer->ps_parser->bad_blocks ));
		}
		SageLog(( _LOG_TRACE, 3, TEXT("Total input blocks:%d; processed total data:%d "), 
					pDemuxer->ps_parser->block_count, pDemuxer->ps_parser->used_bytes));
		for ( i = 0; i<pDemuxer->tracks[0]->number_track; i++ )
		{
			if ( pDemuxer->tracks[0]->track[i].es_elmnt )
				SageLog(( _LOG_TRACE, 3, TEXT("\tes:0:%d, es blocks:%d"), i,
						pDemuxer->tracks[0]->track[i].es_blocks_counter ));
		}
	}

}


