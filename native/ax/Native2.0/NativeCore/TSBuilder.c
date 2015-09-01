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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "NativeCore.h"
#include "TSFilter.h"
#include "AVAnalyzer.h"
#include "BlockBuffer.h"
#include "ESAnalyzer.h"
#include "PSBuilder.h"
#include "TSBuilder.h"
//#include "Bits.h"


uint8_t LookupStreamType( uint32_t lFourCC );
int32_t  PutDescriptor( uint8_t *pData, uint8_t Tag, uint8_t *pDesc, int nDescLength );
uint8_t *AppendDescriptor( uint8_t *pData, int nBytes, uint8_t Tag, uint8_t *pDesc, int nDescLength );
static   int  PutSubtitleCodeDesc( SUBTITLE *pSubtitle, uint8_t *pData, int nLen );

static TS_PROGRAM* CreateTSProgram( TRACKS* pTracks, int nTSFormat);
static void ReleaseTSPorogram( TS_PROGRAM *pTSProgram );
static void UpdatePAT( TS_BUILDER *pTSBuilder );
static int PushPatData( TS_BUILDER *pTSBuilder );
static int PushPmtData( TS_BUILDER *pTSBuilder, int nProgramIndex );
static int PushContentData( TS_BUILDER *pTSBuilder, TS_PACKET* pTSPacket, uint8_t* pESHead, int nESHeadSize, 
						            int nGroupFlag, uint32_t FourCC, uint8_t* pData, int nBytes );
/////////////////////////////////  Memory  //////////////////////////////////////////
inline void RequestAllocBuffer( TS_BUILDER *pTSBuilder, DATA_BUFFER *pDataBuffer )
{
	pDataBuffer->buffer_size = pTSBuilder->block_data_size;
	pDataBuffer->buffer = pTSBuilder->pfn_mem_alloc_hook( pTSBuilder->pfn_mem_alloc_hook_context, NULL, 
                                                                            pDataBuffer->buffer_size, 1 );
}
inline void ReturnAllocBuffer( TS_BUILDER *pTSBuilder, DATA_BUFFER *pDataBuffer )
{
	pTSBuilder->pfn_mem_alloc_hook( pTSBuilder->pfn_mem_alloc_hook_context, pDataBuffer->buffer, 
                                                                            pDataBuffer->buffer_size, 0 );
	pDataBuffer->data_bytes = 0;
}

//////////////////////////////////////////////////////////////////////////////////////////////////////
int BlockBufferTSDump( void* pContext, void* pData, int nSize )
{
	TS_BUILDER *pTSBuilder = (TS_BUILDER *)pContext;
	BLOCK_BUFFER* block_buffer_out = (BLOCK_BUFFER* )pData;
	if (  pTSBuilder->state == PUMPOUT_DATA ) 
	{
		int channel_index = block_buffer_out->track_index;
		int group_flag   = block_buffer_out->group_start;
		int slot_index = block_buffer_out->slot_index;
		int skip = 0;
		TS_PROGRAM *ts_program = pTSBuilder->progams[slot_index];
		AssignPTS( &ts_program->tracks->track[channel_index].es_elmnt->pes,  &block_buffer_out->pes);
		if (  block_buffer_out->start_cue - ts_program->last_pcr > 300*10 )
		{
			ts_program->pcr = block_buffer_out->start_cue;
			ts_program->last_pcr =  block_buffer_out->start_cue;
		} else
		{
			ts_program->pcr = 0;
		}

		//have main video/audio go first
		{
			if (  ts_program->tracks->main_video_index != 0xffff && 
				  ts_program->tracks->main_video_index != channel_index &&
				  ts_program->tracks->main_audio_index != 0xffff && 
				  ts_program->tracks->main_audio_index != channel_index )
			{
				if ( ts_program->tracks->track[channel_index].es_elmnt->content_type == VIDEO_DATA )
				{
					skip = ts_program->tracks->track[ts_program->tracks->main_video_index].es_blocks_counter == 0;
				} else
				{
					skip = ts_program->tracks->track[ts_program->tracks->main_audio_index].es_blocks_counter == 0;
				}
			}
		}

		//have av header at first packet
		if ( !skip )
			if ( ts_program->tracks->track[channel_index].es_blocks_counter == 0 )
			{
				AV_ELEMENT av_elmnt = *ts_program->tracks->track[channel_index].av_elmnt;
				ES_ELEMENT es_elmnt = *ts_program->tracks->track[channel_index].es_elmnt;
				int offset = block_buffer_out->pes.header_length + es_elmnt.private_bytes;
				skip = !AnylyzeAVElement( &av_elmnt, &es_elmnt, NULL, 
										  block_buffer_out->data_start-offset, 
										  block_buffer_out->data_size+offset, SAGETV_TV_RECORDING );
			}

		if ( !skip )
		{
			PushTSBlockData( pTSBuilder, slot_index, channel_index, group_flag,
								block_buffer_out->data_start, block_buffer_out->data_size );
		}
	}
	return 1;
}

int PushTSBlockData( TS_BUILDER *pTSBuilder, int nProgramIndex, int nTrackIndex,
						    int nGroupFlag, uint8_t* pData, int nBytes )
{
	int used_bytes = 0;
	TS_PROGRAM *program;
	TRACK *track;
	if ( pTSBuilder->dumper.stream_dumper == NULL )
		return nBytes;

	ASSERT( nProgramIndex < MAX_PROGRAM_NUM  );
	program = pTSBuilder->progams[nProgramIndex];

	ASSERT( program->tracks->total_track > nTrackIndex );
	track = &program->tracks->track[nTrackIndex];

	if (  pTSBuilder->output_packets % 2000 == 0 )
		pTSBuilder->output_bytes += PushPatData( pTSBuilder );

	if ( pTSBuilder->output_packets  % 8000 == 0 )
	{
		pTSBuilder->output_bytes += PushPmtData( pTSBuilder, nProgramIndex );
	}

	if ( program->pcr != 0 && program->ts_packets[nTrackIndex].pid == program->pcr_pid )
	{
		program->ts_packets[nTrackIndex].pcr = program->pcr;
		program->ts_packets[nTrackIndex].pcr_flag = 1;
	}

	{
		uint8_t es_header[256];
		int bytes; 
		uint32_t packets_start = pTSBuilder->output_packets;
		track->es_elmnt->pes.packet_length = PES2HeaderBytes( track->es_elmnt->pes.has_pts, track->es_elmnt->pes.has_dts ) + nBytes - 6;
		bytes = CreatePES2Header( es_header, sizeof(es_header), &track->es_elmnt->pes );

		bytes = PushContentData( pTSBuilder, &program->ts_packets[nTrackIndex], es_header, bytes, 
			                     nGroupFlag, program->ts_streams.ts_element[nTrackIndex].format_fourcc, 
								 pData, nBytes );

		pTSBuilder->output_bytes += bytes;
		track->es_blocks_counter++;
		track->ts_packets_counter += pTSBuilder->output_packets - packets_start;
	}

	return used_bytes;
}

////////////////////////////////////////////////////////////////////////////////////////////////////
TS_BUILDER* CreateTSBuilder( TRACKS* pTracks, int nTSFormat )
{
	TS_BUILDER *pTSBuilder = SAGETV_MALLOC( sizeof(TS_BUILDER) );
	pTSBuilder->progams[0] = CreateTSProgram( pTracks, nTSFormat );

	//pTSBuilder->pat = SAGETV_MALLOC( sizeof(TS_PAT) );
	pTSBuilder->pat_section = CreateSection();

	if (nTSFormat == MPEG_ASI  )
		pTSBuilder->packet_length = ASI_PACKET_LENGTH;
	else
	if (nTSFormat == MPEG_M2TS )
		pTSBuilder->packet_length = M2TS_PACKET_LENGTH;
	else
		pTSBuilder->packet_length = TS_PACKET_LENGTH;

	pTSBuilder->output_buffer = SAGETV_MALLOC( sizeof(DATA_BUFFER) );
	pTracks->slot_index = 0;
	pTSBuilder->pat_packet.pid = 0x0;
	UpdatePAT( pTSBuilder );
	pTSBuilder->block_data_size = pTSBuilder->packet_length*30;
	SageLog(( _LOG_TRACE, 3, TEXT("TS Builder is created  version 2.0.2  ZQ.") ));
	return pTSBuilder;
}

void ReleaseTSBuilder( TS_BUILDER *pTSBuilder )
{
	int i;
	
	for ( i = 0; i<MAX_PROGRAM_NUM; i++ )
	{
		if ( pTSBuilder->progams[i] != NULL )
			ReleaseTSPorogram( pTSBuilder->progams[i] );
	}

	ReleaseSection( pTSBuilder->pat_section );
	//SAGETV_FREE( pTSBuilder->pat );

	if ( pTSBuilder->output_buffer->buffer != NULL )
	{
		if ( pTSBuilder->pfn_mem_alloc_hook != NULL ) 
			ReturnAllocBuffer( pTSBuilder, pTSBuilder->output_buffer );
	}

	if ( pTSBuilder->local_data_buffer != NULL )
		SAGETV_FREE( pTSBuilder->local_data_buffer );
	
	SAGETV_FREE( pTSBuilder->output_buffer );
	SAGETV_FREE( pTSBuilder );
}

int AddProgram( TS_BUILDER *pTSBuilder, TRACKS* pTracks, int nProgramIndex )
{

	if ( nProgramIndex >= MAX_PROGRAM_NUM )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: program %d isn't avaliable, max program index:%d" ), nProgramIndex, MAX_PROGRAM_NUM ));
	}
	if ( pTSBuilder->progams[nProgramIndex] )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: program %d is already exist" ), nProgramIndex));
		return 0;
	}
	pTSBuilder->progams[nProgramIndex] = CreateTSProgram( pTracks, pTSBuilder->progams[0]->tracks->track_type );
	pTracks->slot_index = nProgramIndex;

	UpdatePAT( pTSBuilder );
	return 1;
}
void SetupTSBuilderAllocMemoryHook( TS_BUILDER *pTSBuilder, MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext )
{
	pTSBuilder->pfn_mem_alloc_hook = pfnMemAlloc;
	pTSBuilder->pfn_mem_alloc_hook_context = pMemAllocContext;
}

static TS_PROGRAM* CreateTSProgram( TRACKS* pTracks, int nTSFormat )
{
	int i;
	TS_PROGRAM *pTSProgram = SAGETV_MALLOC( sizeof(TS_PROGRAM) );

	pTSProgram->ts_streams.total_streams = 0;
	pTSProgram->ts_streams.num_stream = 0;
	pTSProgram->ts_streams.ts_element = SAGETV_MALLOC( pTracks->total_track*sizeof(TS_ELEMENT) );
	pTSProgram->es_streams.total_streams = pTracks->total_track;
	pTSProgram->es_streams.num_stream = 0;
	pTSProgram->es_streams.es_element = SAGETV_MALLOC( pTracks->total_track*sizeof(ES_ELEMENT) );
	pTSProgram->av_streams.total_streams = pTracks->total_track;
	pTSProgram->av_streams.num_stream = 0;
	pTSProgram->av_streams.av_element = SAGETV_MALLOC( pTracks->total_track*sizeof(AV_ELEMENT) );
	for ( i = 0; i < pTracks->total_track; i++ )
	{
		pTSProgram->ts_streams.ts_element[i].channel_index = i;
		pTSProgram->es_streams.es_element[i].channel_index = i;
		pTSProgram->av_streams.av_element[i].channel_index = i;

		pTracks->track[i].ts_elmnt = &pTSProgram->ts_streams.ts_element[i];
		pTracks->track[i].es_elmnt = &pTSProgram->es_streams.es_element[i];
		pTracks->track[i].av_elmnt = &pTSProgram->av_streams.av_element[i];
	}
	pTracks->main_video_index = 0xffff;
	pTracks->main_audio_index = 0xffff;
	pTSProgram->tracks = pTracks;

	pTSProgram->ts_packets = SAGETV_MALLOC( pTracks->total_track*sizeof(TS_PACKET) );

	//pTSProgram->pmt = SAGETV_MALLOC( sizeof(TS_PMT) );
	pTSProgram->pmt_section = CreateSection();

	//pTSProgram->buffer_size = nBufferSize - nBufferSize%pTSProgram->packet_length;
	//if ( pTSProgram->buffer_size != 0 )
	//	pTSProgram->block_buffer=SAGETV_MALLOC(pTSProgram->buffer_size);

	return pTSProgram;
}

static void ReleaseTSPorogram( TS_PROGRAM *pTSProgram )
{
	ReleaseSection( pTSProgram->pmt_section );
	SAGETV_FREE( pTSProgram->ts_packets );
	SAGETV_FREE( pTSProgram->ts_streams.ts_element );
	SAGETV_FREE( pTSProgram->es_streams.es_element );
	SAGETV_FREE( pTSProgram->av_streams.av_element );
	SAGETV_FREE( pTSProgram );
}

static void ResetTSProgram( TS_PROGRAM *pTSProgram )
{
}

void ResetTSBuilder( TS_BUILDER *pTSBuilder )
{
	int i;
	pTSBuilder->used_bytes = 0;
	pTSBuilder->output_bytes = 0;
	pTSBuilder->input_blocks = 0;
	pTSBuilder->output_blocks = 0;
	pTSBuilder->output_packets = 0;
	pTSBuilder->state = 0;
	for ( i = 0; i<MAX_PROGRAM_NUM; i++ )
		ResetTSProgram( pTSBuilder->progams[i] );
}

void SetupBlockDataSize( TS_BUILDER *pTSBuilder, int nSize )
{
	int n = nSize/pTSBuilder->packet_length;
	pTSBuilder->block_data_size = n * pTSBuilder->packet_length;
	ASSERT( pTSBuilder->block_data_size  > 0 );
}

#define SAGETV_TSID 0x01
static void BuildPATSection( TS_SECTION* pSection, TS_PAT* pPat )
{
	int i;
	uint8_t* data;
	int	 offset;
	int  section_length;
	SECTION_HEADER section_header={0};

	section_length = 1+ 8 + 4 * pPat->total_program_number +16; //guess length
	AllocSectionData( pSection, section_length );
	data = StartSection( pSection, section_length  );
	pSection->start_offset = 1; //first byte in section is offset 
	
	section_header.table_id = 0;
	section_header.tsid = pPat->tsid;
	section_header.version = 1;
	section_header.current_next_indicator = 1;
	offset = BuildSectionHeader( &section_header, pSection );

	section_length = offset;
	for ( i = 0; i<pPat->total_program_number ; i++ )
	{
		data[offset] = ( pPat->program_number[i]>>8 ) & 0xff;
		data[offset + 1] = ( pPat->program_number[i] ) & 0xff;
		data[offset + 2] = 0xe0;		//reserved bits 111
		data[offset + 2] |= ( pPat->program_pid[i]>>8 ) & 0x1f; 
		data[offset + 3]  = ( pPat->program_pid[i] ) & 0xff;		
		offset +=	4;
		section_length += 4;
	}
	SealSectionData( pSection, section_length );
}


static void UpdatePAT( TS_BUILDER *pTSBuilder )
{
	int i, num = 0;
	TS_PAT pat={0};
	pat.tsid = SAGETV_TSID; 
	for ( i = 0; i<MAX_PROGRAM_NUM; i++ )
	{
		if ( pTSBuilder->progams[i] != NULL )
		{
			pTSBuilder->progams[i]->pmt_packet.pid = 0x100+i;
			pTSBuilder->progams[i]->pmt_program = 1+pTSBuilder->progams[i]->tracks->slot_index;

			pat.program_number[i] = pTSBuilder->progams[i]->pmt_program;
			pat.program_pid[i] = pTSBuilder->progams[i]->pmt_packet.pid;
			num++;
		}
	}
	pat.total_program_number = num;
	BuildPATSection( pTSBuilder->pat_section, &pat );
}

static void BuildPMTSection( TS_SECTION* pSection, TS_PMT* pPmt )
{
	int i;
	uint8_t* data;
	int	 offset, desc_len = 0;
	int  section_length;
	SECTION_HEADER section_header={0};

	section_length = 8 + 32 * pPmt->total_stream_number+100; //guess length
	AllocSectionData( pSection, section_length );
	data = StartSection( pSection, section_length  );
	pSection->start_offset = 1; //first byte in section is offset 

	section_header.table_id = 0x02;
	section_header.tsid = pPmt->program_number;
	section_header.version = 0;
	section_header.current_next_indicator = 1;
	section_header.section_number = 0;
	section_header.last_section_number = 0;
	offset = BuildSectionHeader( &section_header, pSection );

	//fill pcr pid
	data[offset] = 0xe0;
	data[offset]   |= (uint8_t)(( pPmt->pcr_pid ) >> 8 )&0x1f;
	data[offset+1] = (uint8_t)( pPmt->pcr_pid );

	//fill PMT descriptor
	data[offset+2] = 0xf0;
	if ( pPmt->program_desc.desc_bytes )
	{
		uint16_t desc_len = pPmt->program_desc.desc_bytes;
		data[offset+2] = 0xf0;
		data[offset+2] = (uint8_t)(( desc_len ) >> 8)&0x03;
		data[offset+3] = (uint8_t)( desc_len );
	}
	offset += 4 + desc_len;

	section_length = offset;
	for ( i = 0; i<pPmt->total_stream_number  ; i++ )
	{
		data[offset] = pPmt->stream_type[i];
		data[offset + 1] = 0xe0;
		data[offset + 1] |= (uint8_t)(pPmt->stream_pid[i]>>8 )&0x1f;
		data[offset + 2] = (uint8_t)(pPmt->stream_pid[i]); 

		data[offset+3] = 0xf0;
		desc_len = pPmt->stream_desc[i].desc_bytes;
		if ( desc_len )
		{
			data[offset + 3] |= (uint8_t)( desc_len >>8 )&0x03; 
			data[offset + 4] = (uint8_t)( desc_len  );
			memcpy( data+offset+5, pPmt->stream_desc[i].desc_ptr, desc_len );
		} else
		{
			data[offset + 3] |= 0; 
			data[offset + 4] = 0;
		}
		offset +=	5 + desc_len;
		section_length += 5 + desc_len;
		if ( section_length >= pSection->data_size ) break;
	}

	SealSectionData( pSection, section_length );
}
/*
 //VIDEO
 SAGE_FOURCC( "H264" )
 SAGE_FOURCC( "VC1 " )
 SAGE_FOURCC( "MP4V" )
 SAGE_FOURCC( "MPGV" )
 SAGE_FOURCC( "MPE1" )
 //AUDIO
 SAGE_FOURCC( "AC3 " )
 SAGE_FOURCC( "DTS " )
 SAGE_FOURCC( "AUD " )
 SAGE_FOURCC( "MPGA" )
 SAGE_FOURCC( "MP4A" )
 SAGE_FOURCC( "AAC " )
 SAGE_FOURCC( "AACH" )
 SAGE_FOURCC( "LPCM" )
 SAGE_FOURCC( "AC3E" )

 SAGE_FOURCC( "SUB " ); //sub title
 SAGE_FOURCC( "TTX " ); //teletext
*/
void UpdatePMT( TS_BUILDER *pTSBuilder, int nProgramIndex )
{
	int i, j, k;
	char buf[32];
	int len;
	uint8_t  type;
	uint16_t pid;
	uint32_t  language_code;
	uint8_t  audio_type;
	TS_PROGRAM *program = pTSBuilder->progams[nProgramIndex];
	TS_PMT pmt={0};
	uint8_t  index_order[128]={0};

	ASSERT( 128 >= program->tracks->total_track );
	for ( i = 0; i<program->tracks->total_track; i++ )
	{
		for ( j = 0; j<program->tracks->total_track; j++ )
		{
			if ( program->tracks->track[j].av_elmnt->stream_index == i )
			{
				index_order[i] = j;
				break;
			}
		}
		if ( j >= program->tracks->total_track )
			index_order[i] = 0xff;
	}

	j = 0;
	pmt.program_number = pTSBuilder->progams[nProgramIndex]->pmt_program;
	//video stream

	for ( k = 0; k<program->tracks->total_track; k++ )
	{
		i = index_order[k]; //keep PMT in stream order.
		if ( i == 0xff ) continue;
		if ( program->tracks->track[i].av_elmnt->content_type != VIDEO_DATA )
			continue;
		pid = 0x200+j;
		type = LookupStreamType( program->tracks->track[i].av_elmnt->format_fourcc );
		if ( type == 0 && program->tracks->track[i].ts_elmnt->type == 0 )
			continue;
		if ( program->pcr_pid == 0 )
		{
			program->pcr_pid = pid;
			pmt.pcr_pid = pid;
		}

		if ( program->tracks->track[i].ts_elmnt->type > 0  ) 
			type = program->tracks->track[i].ts_elmnt->type;
		pmt.stream_pid[j]  = pid;  
		pmt.stream_type[j] = type; 
		program->tracks->track[i].ts_elmnt->pid = pid;    //ZQ
		program->tracks->track[i].ts_elmnt->type = type;
		program->ts_packets[i].pid = pid;

		len = 0;
		NewDescData( &pmt.stream_desc[j], 16 );
		buf[0] = 0;
		len += PutDescriptor( pmt.stream_desc[j].desc_ptr, VIDEO_DESC, (uint8_t*)buf, 1 );
		pmt.stream_desc[j].desc_bytes = len;
		ASSERT( len <= pmt.stream_desc[j].buffer_size );
		j++;
	}

	//audio stream
	for ( k = 0; k<program->tracks->total_track; k++ )
	{
		i = index_order[k]; //keep PMT in stream order.
		if ( i == 0xff ) continue;
		if ( program->tracks->track[i].av_elmnt->content_type != AUDIO_DATA )
			continue;
		pid = 0x200+j;                       
		type = LookupStreamType( program->tracks->track[i].av_elmnt->format_fourcc );
		if ( type == 0 && program->tracks->track[i].ts_elmnt->type == 0 )
			continue;
		if ( program->pcr_pid == 0 )
		{
			program->pcr_pid = pid;
			pmt.pcr_pid = pid;
		}

		if ( program->tracks->track[i].ts_elmnt->type > 0  ) 
			type = program->tracks->track[i].ts_elmnt->type;
		pmt.stream_pid[j]  = pid;  
		pmt.stream_type[j] = type; 
		program->tracks->track[i].ts_elmnt->pid = pid;
		program->tracks->track[i].ts_elmnt->type = type;
		program->ts_packets[i].pid = pid;
		NewDescData( &pmt.stream_desc[j], 32 );
		
		len = 0;
		buf[0] = 0;
		
		len += PutDescriptor( pmt.stream_desc[j].desc_ptr+len, AUDIO_DESC, (uint8_t*)buf, 1 );

		if ( program->tracks->track[i].ts_elmnt->format_fourcc == SAGE_FOURCC( "AC3 " ) )
		{
			buf[0] = 0x0;
			len += PutDescriptor( pmt.stream_desc[j].desc_ptr+len, ATSC_AC3_DESC, (uint8_t*)buf, 1 );
		} else
		if ( program->tracks->track[i].ts_elmnt->format_fourcc == SAGE_FOURCC( "DTS " ) )
		{
			buf[0] = 0x0;
			len += PutDescriptor( pmt.stream_desc[j].desc_ptr+len, DTS_DESC, (uint8_t*)buf, 1 );
		} 

		language_code = 0;
		audio_type = 0;
		if ( program->tracks->track[i].es_elmnt->language_code )
		{
			language_code = program->tracks->track[i].es_elmnt->language_code;
			audio_type    = program->tracks->track[i].es_elmnt->audio_type;
		}
		if ( program->tracks->track[i].ts_elmnt->language_code )
		{
			language_code = program->tracks->track[i].ts_elmnt->language_code;
			audio_type    = program->tracks->track[i].ts_elmnt->audio_type;
		}
		if ( language_code )
		{
			Language( language_code, buf );
			buf[3] = audio_type;
			len += PutDescriptor( pmt.stream_desc[j].desc_ptr+len, ISO639_LANGUAGE_DESC, (uint8_t*)buf, 4 );
		}
	
		pmt.stream_desc[j].desc_bytes = len;
		ASSERT( len <= pmt.stream_desc[j].buffer_size );
		j++;
	}

	//teletext, subtitle, etc...
	for ( k = 0; k<program->tracks->total_track; k++ )
	{
		i = index_order[k]; //keep PMT in stream order.
		if ( i == 0xff ) continue;
		if ( program->tracks->track[i].av_elmnt->content_type == AUDIO_DATA ||
			 program->tracks->track[i].av_elmnt->content_type == VIDEO_DATA )
			 continue;
		pid = 0x200+j;
		type = LookupStreamType( program->tracks->track[i].av_elmnt->format_fourcc );
		if ( type == 0 && program->tracks->track[i].ts_elmnt->type == 0 )
			continue;
		if ( program->pcr_pid == 0 )
		{
			program->pcr_pid = pid;
			pmt.pcr_pid = pid;
		}

		if ( program->tracks->track[i].ts_elmnt->type > 0  ) 
			type = program->tracks->track[i].ts_elmnt->type;
		pmt.stream_pid[j]  = pid;  
		pmt.stream_type[j] = type; 
		program->tracks->track[i].ts_elmnt->pid = pid;
		program->tracks->track[i].ts_elmnt->type = type;
		program->ts_packets[i].pid = pid;
		NewDescData( &pmt.stream_desc[j], 32 );
		len = 0;
		buf[0] = 0;
		if ( program->tracks->track[i].av_elmnt->content_type == SUBTITLE_DATA )
			len += PutSubtitleCodeDesc( &program->tracks->track[i].av_elmnt->d.s.sub, pmt.stream_desc[j].desc_ptr+len, 32-len );
		//else
		//if ( program->tracks->track[i].av_elmnt->content_type == SAGE_FOURCC( "TTX " ))
		//	len += PutDescriptor( pmt.stream_desc[j].desc_ptr+len, TELTEXT_DESC, (uint8_t*)buf, 1 );

		language_code = 0;
		audio_type = 0;
		if ( program->tracks->track[i].es_elmnt->language_code )
		{
			language_code = program->tracks->track[i].es_elmnt->language_code;
			audio_type    = program->tracks->track[i].es_elmnt->audio_type;
		}
		if ( program->tracks->track[i].ts_elmnt->language_code )
		{
			language_code = program->tracks->track[i].ts_elmnt->language_code;
			audio_type    = program->tracks->track[i].ts_elmnt->audio_type;
		}
		if ( language_code )
		{
			Language( language_code, buf );
			buf[3] = audio_type;
			len += PutDescriptor( pmt.stream_desc[j].desc_ptr+len, ISO639_LANGUAGE_DESC, (uint8_t*)buf, 4 );
		}
		pmt.stream_desc[j].desc_bytes = len;
		ASSERT( len <= pmt.stream_desc[j].buffer_size );
		j++;
	}
	
	pmt.total_stream_number = j;
	BuildPMTSection( program->pmt_section, &pmt );

	ReleaseDescData( &pmt.program_desc );
	for ( j = 0; j<program->tracks->total_track; j++ )
	{
		ReleaseDescData( &pmt.stream_desc[j] );
	}
}


///////////////////////////////////////////////////////////////////////////////////////
static void FillPCR( uint8_t* pkt, ULONGLONG PCR )
{
	ULONGLONG PCR_base, PCR_ext;
	PCR_base = PCR/300; 
	PCR_ext =  PCR-PCR_base*300;
	pkt[0] = (uint8_t)(( PCR_base >> 25 ));
	pkt[1] = (uint8_t)(( PCR_base >> 17 ));
	pkt[2] = (uint8_t)(( PCR_base >> 9 ));
	pkt[3] = (uint8_t)(( PCR_base >> 1 ));
	pkt[4] = ( PCR_base && 0x1 )? 0x80 : 0;
	pkt[4] |=  0x7e; //6 1's
	pkt[4] |= (uint8_t)(( PCR_ext >> 8 ) & 0x01 );
	pkt[5] =  (uint8_t)(( PCR_ext ));
}

static int FillTSPack( TS_PACKET *pTSPacket, uint8_t* pData, int nBytes, uint8_t *pPacket )
{
	int used_bytes = 0;
	int i;
	uint8_t *p=pPacket;

    pTSPacket->adaption_ctr = 0;
	pTSPacket->adaption.apapt_len = 0;


	if ( pData != NULL && nBytes > 0 )
		pTSPacket->adaption_ctr |= 0x01;

	if ( pTSPacket->pcr_flag )
		pTSPacket->adaption_ctr |= 0x02;

	if ( pTSPacket->start )
	{
		pTSPacket->adaption.random_acceess = 1;
		pTSPacket->adaption_ctr |= 0x02;
	}

	if ( pTSPacket->adaption_ctr & 0x02 )
	{
		if ( pTSPacket->pcr_flag  )
		{
			if ( nBytes < TS_PACKET_LENGTH-5-1-6 )
			{
				pTSPacket->adaption.apapt_len =  TS_PACKET_LENGTH-5-nBytes;
				used_bytes = nBytes;
			}
			else
			{
				pTSPacket->adaption.apapt_len = 1+6;
				used_bytes = TS_PACKET_LENGTH-5-pTSPacket->adaption.apapt_len;
			}
		} else
		{
			if ( nBytes < TS_PACKET_LENGTH-5-1 )
			{
				pTSPacket->adaption.apapt_len =  TS_PACKET_LENGTH-5-nBytes;
				used_bytes = nBytes;
			}
			else
			{
				pTSPacket->adaption.apapt_len = 1;
				used_bytes = TS_PACKET_LENGTH-5-pTSPacket->adaption.apapt_len;
			}
		}
	} else
	{
		if ( nBytes >= TS_PACKET_LENGTH-4 )
		{
			pTSPacket->adaption.apapt_len = 0;
			used_bytes = TS_PACKET_LENGTH-4;
		} else
		if ( nBytes < TS_PACKET_LENGTH-5-1 )
		{
			pTSPacket->adaption.apapt_len = TS_PACKET_LENGTH-5-nBytes;
			used_bytes = nBytes;
			pTSPacket->adaption_ctr |= 0x02;
		} else
		{
			pTSPacket->adaption.apapt_len = 1;
			used_bytes = TS_PACKET_LENGTH-5-pTSPacket->adaption.apapt_len;
			pTSPacket->adaption_ctr |= 0x02;
		}
	}

	p[0] = TS_SYNC	;
	p[1] = 0;
	if ( pTSPacket->error )	   p[1] |= 0x80;	
	if ( pTSPacket->start )	   p[1] |= 0x40;	
	if ( pTSPacket->priority ) p[1] |= 0x20;
	p[1] |= ( pTSPacket->pid >> 8	) &	0x1f;
	p[2] =   pTSPacket->pid & 0xff;

	p[3]  = ( pTSPacket->scrambling_ctr & 0x03	) << 6;	
	p[3] |= ( pTSPacket->adaption_ctr	& 0x03	) << 4;	
	p[3] |= ( pTSPacket->continuity_ct & 0x0f   );

	p = p+4;
	i = 0;
	if ( pTSPacket->adaption_ctr & 0x02 )
	{
		*p++ = pTSPacket->adaption.apapt_len;
		*p = pTSPacket->adaption.flags;
		if ( pTSPacket->adaption.discontinute   )   *p |= 0x80;
		if ( pTSPacket->adaption.random_acceess )   *p |= 0x40;
		if ( pTSPacket->adaption.elem_stream_prio ) *p |= 0x20;
		p++;
		i = pTSPacket->adaption.apapt_len-1;
		if ( pTSPacket->pcr_flag )
		{
			FillPCR( p, pTSPacket->pcr );
			i -= 6;
			p += 6;
		}
	}
	ASSERT( i >= 0 );
	while ( i-- ) 
		*p++ = 0xff;


	if ( pTSPacket->adaption_ctr & 0x01 )
	{
		i = used_bytes;
		while ( i-- ) 
			*p++ = *pData++;
	}

	return used_bytes;
}

static int FillTSPack2( TS_PACKET *pTSPacket, uint8_t* pData1, int nBytes1, uint8_t* pData2, int nBytes2, uint8_t *pPacket )
{
	int used_bytes = 0;
	int i;
	int nBytes = nBytes1+nBytes2;
	uint8_t *p=pPacket;

    pTSPacket->adaption_ctr = 0;
	pTSPacket->adaption.apapt_len = 0;

	if (  nBytes > 0 )
		pTSPacket->adaption_ctr |= 0x01;

	if ( pTSPacket->pcr_flag )
		pTSPacket->adaption_ctr |= 0x02;

	////Mpeg2Reader seeking in java code can't handle this code to parser out PTS, I disable it ZQ.
	//if ( pTSPacket->start )
	//{
	//	pTSPacket->adaption.random_acceess = 1;
	//	pTSPacket->adaption_ctr |= 0x02;
	//} 

	if ( pTSPacket->adaption_ctr & 0x02 )
	{
		if ( pTSPacket->pcr_flag  )
		{
			if ( nBytes < TS_PACKET_LENGTH-5-1-6 )
			{
				pTSPacket->adaption.apapt_len =  TS_PACKET_LENGTH-5-nBytes;
				used_bytes = nBytes;
			}
			else
			{
				pTSPacket->adaption.apapt_len = 1+6;
				used_bytes = TS_PACKET_LENGTH-5-pTSPacket->adaption.apapt_len;
			}
		} else
		{
			if ( nBytes < TS_PACKET_LENGTH-5-1 )
			{
				pTSPacket->adaption.apapt_len =  TS_PACKET_LENGTH-5-nBytes;
				used_bytes = nBytes;
			}
			else
			{
				pTSPacket->adaption.apapt_len = 1;
				used_bytes = TS_PACKET_LENGTH-5-pTSPacket->adaption.apapt_len;
			}
		}
	} else
	{
		if ( nBytes >= TS_PACKET_LENGTH-4 )
		{
			pTSPacket->adaption.apapt_len = 0;
			used_bytes = TS_PACKET_LENGTH-4;
		} else
		if ( nBytes < TS_PACKET_LENGTH-5-1 )
		{
			pTSPacket->adaption.apapt_len = TS_PACKET_LENGTH-5-nBytes;
			used_bytes = nBytes;
			pTSPacket->adaption_ctr |= 0x02;
		} else
		{
			pTSPacket->adaption.apapt_len = 1;
			used_bytes = TS_PACKET_LENGTH-5-pTSPacket->adaption.apapt_len;
			pTSPacket->adaption_ctr |= 0x02;
		}
	}

	p[0] = TS_SYNC	;
	p[1] = 0;
	if ( pTSPacket->error )	   p[1] |= 0x80;	
	if ( pTSPacket->start )	   p[1] |= 0x40;	
	if ( pTSPacket->priority ) p[1] |= 0x20;
	p[1] |= ( pTSPacket->pid >> 8	) &	0x1f;
	p[2] =   pTSPacket->pid & 0xff;

	p[3]  = ( pTSPacket->scrambling_ctr & 0x03	) << 6;	
	p[3] |= ( pTSPacket->adaption_ctr	& 0x03	) << 4;	
	p[3] |= ( pTSPacket->continuity_ct & 0x0f   );

	p = p+4;
	i = 0;
	if ( pTSPacket->adaption_ctr & 0x02 )
	{
		*p++ = pTSPacket->adaption.apapt_len;
		*p = pTSPacket->adaption.flags;
		if ( pTSPacket->adaption.discontinute   )   *p |= 0x80;
		if ( pTSPacket->adaption.random_acceess )   *p |= 0x40;
		if ( pTSPacket->adaption.elem_stream_prio ) *p |= 0x20;
		if ( pTSPacket->pcr_flag )					*p |= 0x10;
		p++;
		i = pTSPacket->adaption.apapt_len-1;
		if ( pTSPacket->pcr_flag )
		{
			FillPCR( p, pTSPacket->pcr );
			i -= 6;
			p += 6;
		}
	}
	ASSERT( i >= 0 );
	while ( i-- ) 
		*p++ = 0xff;


	if ( pTSPacket->adaption_ctr & 0x01 )
	{
		int k = nBytes1;
		ASSERT( nBytes1 <= used_bytes );
		i = used_bytes;
		while ( k-- ) 
		{ 
			i--; 
			*p++ = *pData1++; 
		}
		while ( i-- ) 
		{ 
			*p++ = *pData2++; 
		}
			
	}

	return used_bytes-nBytes1;
}

uint8_t* RequsetTSPacket( TS_BUILDER *pTSBuilder )
{
	uint8_t *p;
	if ( pTSBuilder->output_buffer->buffer_size == 0 )
	{
		if ( pTSBuilder->pfn_mem_alloc_hook == NULL ) 
		{
			if ( pTSBuilder->local_data_size == 0 )
			{
				 pTSBuilder->local_data_size   = pTSBuilder->block_data_size ;
				 pTSBuilder->local_data_buffer = SAGETV_MALLOC( pTSBuilder->local_data_size );
				 pTSBuilder->output_buffer->buffer_size = pTSBuilder->local_data_size;
				 pTSBuilder->output_buffer->buffer = pTSBuilder->local_data_buffer;
				 memset( &pTSBuilder->output_data, 0, sizeof(pTSBuilder->output_data) );
			}
		} else
		{
			RequestAllocBuffer( pTSBuilder, pTSBuilder->output_buffer );
		}
	}

	if ( pTSBuilder->output_buffer->data_bytes + pTSBuilder->packet_length > pTSBuilder->output_buffer->buffer_size )
	{
	
		//OUTPUT_DATA output_data={0};
		pTSBuilder->output_data.bytes    = pTSBuilder->output_buffer->data_bytes;
		pTSBuilder->output_data.data_ptr = pTSBuilder->output_buffer->buffer;
		pTSBuilder->output_packets += pTSBuilder->output_buffer->data_bytes/pTSBuilder->packet_length;
		pTSBuilder->dumper.stream_dumper( pTSBuilder->dumper.stream_dumper_context, &pTSBuilder->output_data, sizeof( OUTPUT_DATA ) );
		pTSBuilder->output_blocks++;
		pTSBuilder->output_data.group_flag = 0;
		pTSBuilder->output_data.start_offset = 0;
	

		if ( pTSBuilder->pfn_mem_alloc_hook != NULL ) 
		{
			ReturnAllocBuffer( pTSBuilder, pTSBuilder->output_buffer );
			RequestAllocBuffer( pTSBuilder, pTSBuilder->output_buffer );
		} else
		{
			ASSERT(  pTSBuilder->output_buffer->buffer == pTSBuilder->local_data_buffer );
			pTSBuilder->output_buffer->data_bytes = 0;
		}
	}

	p = pTSBuilder->output_buffer->buffer + pTSBuilder->output_buffer->data_bytes;
	pTSBuilder->output_buffer->data_bytes += pTSBuilder->packet_length;
	return p;
}

static void MarkGroupStart( TS_BUILDER *pTSBuilder, int nGroupFlag, uint32_t FourCC, uint8_t* ptr )
{
	pTSBuilder->output_data.group_flag   = nGroupFlag;
	pTSBuilder->output_data.fourcc = FourCC;
	pTSBuilder->output_data.start_offset = (uint16_t)(ptr-pTSBuilder->output_buffer->buffer);	
}
///////////////////////////////// PUSH SECTION  //////////////////////////////////////////
static int PushContentData( TS_BUILDER *pTSBuilder, TS_PACKET* pTSPacket, uint8_t* pESHead, int nESHeadSize, 
						    int nGroupFlag, uint32_t FourCC, uint8_t* pData, int nBytes )
{
	int bytes, used_bytes = 0;

	pTSPacket->start = 1;
	while ( used_bytes < nBytes )
	{
		uint8_t *packet_p = RequsetTSPacket( pTSBuilder );
		//insert a PES header into first packet of a block.
		if ( pTSPacket->start )
		{
			if ( nGroupFlag ) MarkGroupStart( pTSBuilder, nGroupFlag, FourCC, packet_p );
			bytes = FillTSPack2( pTSPacket, pESHead, nESHeadSize, pData, nBytes, packet_p );
		} else
		{
			bytes = FillTSPack( pTSPacket, pData+used_bytes, nBytes-used_bytes, packet_p ); //merge two data source into a packet
		}


		used_bytes += bytes;

		pTSPacket->start = 0;
		pTSPacket->pcr_flag = 0;
		pTSPacket->continuity_ct++;
		if ( bytes == 0 ) break;
	}

	return used_bytes;
}

static int PushSectionData( TS_BUILDER *pTSBuilder, TS_SECTION *pSection, TS_PACKET* pTSPacket )
{
	int bytes, used_bytes = 0;
	uint8_t *p;
	//DATA_BUFFER *data_buffer = pTSBuilder->output_buffer;
	
	p = pSection->data;

	pTSPacket->start = 1;

	while ( used_bytes < pSection->total_bytes )
	{
		uint8_t *packet_p = RequsetTSPacket( pTSBuilder );
		bytes = FillTSPack( pTSPacket, p+used_bytes, pSection->total_bytes-used_bytes, packet_p );

		used_bytes += bytes;

		pTSPacket->start = 0;
		pTSPacket->continuity_ct++;
		if ( bytes == 0 ) break;
	}

	return used_bytes;
}
static int PushPatData( TS_BUILDER *pTSBuilder )
{
	int used_bytes = 0;
	used_bytes = PushSectionData( pTSBuilder, pTSBuilder->pat_section, &pTSBuilder->pat_packet );
	return used_bytes;
}

static int PushPmtData( TS_BUILDER *pTSBuilder, int nProgramIndex )
{
	int used_bytes = 0;
	TS_PROGRAM *program = pTSBuilder->progams[nProgramIndex];
	used_bytes = PushSectionData( pTSBuilder, program->pmt_section, &program->pmt_packet);
	return used_bytes;
}

void FlushOutData( TS_BUILDER *pTSBuilder )
{
	pTSBuilder->output_data.bytes    = pTSBuilder->output_buffer->data_bytes;
	pTSBuilder->output_data.data_ptr = pTSBuilder->output_buffer->buffer;
	pTSBuilder->output_packets += pTSBuilder->output_buffer->data_bytes/pTSBuilder->packet_length;
	pTSBuilder->dumper.stream_dumper( pTSBuilder->dumper.stream_dumper_context, &pTSBuilder->output_data, sizeof( OUTPUT_DATA ) );
}

uint8_t LookupStreamType( uint32_t lFourCC )
{
	if ( lFourCC == SAGE_FOURCC( "MPGV" ) )
		return VIDEO_STREAM_TYPE;
	else
	if ( lFourCC == SAGE_FOURCC( "MPGA" ) )
		return  AUDIO_STREAM_TYPE;
	else
	if ( lFourCC == SAGE_FOURCC( "H264" ) )
		return  H264_STREAM_TYPE; 
	else
	if ( lFourCC == SAGE_FOURCC( "AC3 " ) )
		return  AC3_STREAM_TYPE; 
	else
	if ( lFourCC == SAGE_FOURCC( "DTS " ) )
		return  DTS_STREAM_TYPE; 
	else
	if ( lFourCC == SAGE_FOURCC( "AACH" ) )
		return  AAC_HE_STREAM_TYPE; 
	else
	if ( lFourCC == SAGE_FOURCC( "AAC " ) )
		return  AAC_STREAM_TYPE; 
	else
	if ( lFourCC == SAGE_FOURCC( "VC1 " ) )
		return  VC1_STREAM_TYPE; 
	else
	if ( lFourCC == SAGE_FOURCC( "TTX " ) )
		return  PRIVATE_STREAM_TYPE; 
	else
	if ( lFourCC == SAGE_FOURCC( "SUB " ) )
		return  PRIVATE_STREAM_TYPE; 
	else
		return 0;
	
	return 0;
}

int PutDescriptor( uint8_t *pData, uint8_t Tag, uint8_t *pDesc, int nDescLength )
{
	pData[0] = Tag;
	pData[1] = nDescLength;
	memcpy( pData+2, pDesc, nDescLength );
	return 2+nDescLength;
}

uint8_t* AppendDescriptor( uint8_t *pData, int nBytes, uint8_t Tag, uint8_t *pDesc, int nDescLength )
{
	uint8_t tag; 
	int len;
	int i=0;
	//skip to the end of desciptor buffer
	while ( i+2 <= nBytes ) 
	{
		tag = pData[i];
		len = pData[i+1];
		if ( tag == 0 || tag == 0xff )
			break;
		i += 2+len;
	}
	if ( i+2+nDescLength > nBytes  )
		return NULL;

	pData[i]   = Tag;
	pData[i+1] = nDescLength;
	memcpy( pData+i+2, pDesc, nDescLength );

	return pData+i;
}

static int PutSubtitleCodeDesc( SUBTITLE *pSubtitle, uint8_t *pData, int nLen )
{
	uint8_t buf[8];
	buf[0] = (uint8_t)(pSubtitle->lanugaue & 0xff);
	buf[1] = (uint8_t)((pSubtitle->lanugaue >>8) & 0xff);
	buf[2] = (uint8_t)((pSubtitle->lanugaue >>16) & 0xff);
	buf[3] = pSubtitle->comp_type;
	buf[4] = (uint8_t)(pSubtitle->cpgid >> 8);
	buf[5] = (uint8_t)(pSubtitle->cpgid & 0xff);
	buf[6] = (uint8_t)(pSubtitle->apgid >> 8);;
	buf[7] = (uint8_t)(pSubtitle->apgid & 0xff);
	PutDescriptor( pData, SUBTITLE_DESC,  buf, 8  );
	return 8+2; //6 bytes;
}
