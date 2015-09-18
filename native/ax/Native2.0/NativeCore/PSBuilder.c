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
//#include "PSParser.h"
#include "BlockBuffer.h"
#include "AVAnalyzer.h"
#include "TSBuilder.h"
#include "PSBuilder.h"
//#include "Bits.h" 

#define SAGE_MPEG_VERSION     6

#define  VIDEO_BLOCK_SIZE		   (1024*232)
#define  AUDIO_BLOCK_SIZE	 	   (128*32)

#define  HD_STREAM_RATE	((LONGLONG)2401587)			// Original HD stream rate 19.2 Mbps
#define  DEMUX(x)			(((int)(x)*8)/50)		// Demux value for HD content STREAMRATE / 50

static int  BuildBlockHeader( PS_BUILDER* pPSBuilder, ES_ELEMENT *pESElmnt, uint8_t* pData, int nSize );
static int  AppedEndOFCode( char* pOutBuf, int len );
static int  PadingBuffer( char* pOutBuf, int nLen );
static void BuildPadBufferHeader( char* pOutBuf, int nLen );
int PSBulderPushDataInSafe( PS_BUILDER *pPSBuilder, int nTrackIndex, int bGroup, uint8_t* pData, int nSize );
///////////////////////////////// PUSH SECTION  //////////////////////////////////////////
int BlockBufferPSDump( void* pContext, void* pData, int nSize )
{
	PS_BUILDER *pPSBuilder = (PS_BUILDER *)pContext;
	BLOCK_BUFFER* block_buffer_out = (BLOCK_BUFFER* )pData;
	if ( pPSBuilder == NULL )
		return 0;
	if ( pPSBuilder->state == PUMPOUT_DATA ) 
	{
		int channel_index = block_buffer_out->track_index;
		int group_start = block_buffer_out->group_start;
		int skip = 0;
		AssignPTS( &pPSBuilder->tracks->track[channel_index].es_elmnt->pes, &block_buffer_out->pes);
		pPSBuilder->tracks->track[channel_index].es_elmnt->scr = block_buffer_out->start_cue;

		//have main video/audio go first, 
		{
			if (  pPSBuilder->tracks->main_video_index != 0xffff && pPSBuilder->tracks->main_video_index != channel_index &&
				  pPSBuilder->tracks->main_audio_index != 0xffff && pPSBuilder->tracks->main_audio_index != channel_index )
			{
				if ( pPSBuilder->tracks->track[channel_index].es_elmnt->content_type == VIDEO_DATA )
				{
					skip = pPSBuilder->tracks->track[pPSBuilder->tracks->main_video_index ].es_blocks_counter == 0;
				} else
				{
					skip = pPSBuilder->tracks->track[pPSBuilder->tracks->main_audio_index ].es_blocks_counter == 0;
				}
			}
		}

		//have av header at first packet
		if ( !skip )
		{
			if ( pPSBuilder->tracks->track[channel_index].es_blocks_counter == 0 )
			{
				AV_ELEMENT av_elmnt = *pPSBuilder->tracks->track[channel_index].av_elmnt;
				ES_ELEMENT es_elmnt = *pPSBuilder->tracks->track[channel_index].es_elmnt;
				int offset = block_buffer_out->pes.header_length + es_elmnt.private_bytes;
				skip = !AnylyzeAVElement( &av_elmnt, &es_elmnt, NULL, 
										   block_buffer_out->data_start-offset, 
										   block_buffer_out->data_size+offset, SAGETV_TV_RECORDING );
				if ( !skip ) 
					group_start |= ES_GROUP_START;
			} 
		}

		if ( !skip )
		{
			if ( ( pPSBuilder->build_ctrl & BUILD_HEADER_IN_BUFFER ) )
				PSBulderPushDataInBuffer( pPSBuilder, channel_index, group_start,  block_buffer_out );
			else
				PSBulderPushDataInSafe( pPSBuilder, channel_index, group_start, block_buffer_out->data_start, block_buffer_out->data_size );
		}

		if ( 0 && channel_index == 0 )
		{
			ULONGLONG pts=0, dts;
			if ( block_buffer_out->pes.has_dts )
				dts = block_buffer_out->pes.dts;

			if ( block_buffer_out->pes.has_pts )
				pts = block_buffer_out->pes.pts;
#ifdef WIN32
			printf( "%d(%d): %10I64u-%10I64u %5ld\t[%10I64u %10I64u] \n", channel_index, group_start,
					   block_buffer_out->start_cue/300, block_buffer_out->end_cue/300, 
					   (uint32_t)(block_buffer_out->end_cue-block_buffer_out->start_cue)/300, pts, dts );
#endif
#ifdef LINUX
			printf( "%d(%d): %10llu-%10llu %5ld\t[%10llu %10llu] \n", channel_index, group_start,
					   block_buffer_out->start_cue/300, block_buffer_out->end_cue/300,
					   (uint32_t)(block_buffer_out->end_cue-block_buffer_out->start_cue)/300, pts, dts );
#endif
		}

	} 
	return 1;
}

//////////////////////////////////////////////////////////////////////////////////////////
int PSBulderPushDataInSafe( PS_BUILDER *pPSBuilder, int nTrackIndex, int bGroup, uint8_t* pData, int nSize )
{
	OUTPUT_DATA output_data={0};
	TRACK* track;
	int used_bytes= 0;
	uint8_t buf[256];
	int pes_header_bytes, header_bytes=0, content_bytes=0, bytes;
	ES_ELEMENT es_elmnt;

	if ( pPSBuilder->dumper.stream_dumper == NULL || nSize == 0 ) 
		return 0;

	track = &pPSBuilder->tracks->track[nTrackIndex];

	//information isn't ready
	if ( track->es_elmnt->content_type == 0 )
		return 0;

	//start from content header
	if ( track->es_blocks_counter == 0 && bGroup == 0 )
		return 0;

	output_data.group_flag = bGroup;
	output_data.fourcc = track->es_elmnt->format_fourcc;
	es_elmnt = pPSBuilder->es_streams.es_element[nTrackIndex];
	while( nSize - used_bytes > 0  )
	{
		pes_header_bytes = PES2HeaderBytes( es_elmnt.pes.has_pts, es_elmnt.pes.has_dts )
					           + es_elmnt.private_bytes;
		header_bytes = PACK_HEADER_BYTES + pes_header_bytes;
		content_bytes =	( nSize-used_bytes > pPSBuilder->buffer_size )? pPSBuilder->buffer_size : nSize-used_bytes;

		//build and dump  a header of a block (PACK+PES)
		es_elmnt.pes.packet_length = content_bytes + pes_header_bytes - 6;
		bytes = BuildBlockHeader( pPSBuilder, &es_elmnt, buf, header_bytes );
		ASSERT( bytes == header_bytes );
		es_elmnt.pes.has_pts = es_elmnt.pes.has_dts = 0;

		//dump header (PACK+PES)
		output_data.data_ptr = buf;
		output_data.bytes = bytes;
		pPSBuilder->dumper.stream_dumper( pPSBuilder->dumper.stream_dumper_context, &output_data, sizeof(output_data) );
		pPSBuilder->output_bytes += bytes;

		//dump PES data of a block (content only)
		output_data.data_ptr = pData+used_bytes;
		output_data.bytes = content_bytes;
		pPSBuilder->dumper.stream_dumper( pPSBuilder->dumper.stream_dumper_context, &output_data, sizeof(output_data) );

		used_bytes += content_bytes;
		pPSBuilder->output_bytes += content_bytes;
		pPSBuilder->output_blocks++;
		track->es_blocks_counter++;
	}

	if ( pPSBuilder->pading_block_enable )
	{
		int len = pPSBuilder->buffer_size - header_bytes - content_bytes;
		if ( len > 0 )
		{
			BuildPadBufferHeader( (char*)pPSBuilder->block_buffer, len );
			output_data.data_ptr = pPSBuilder->block_buffer;
			output_data.bytes = len;
			pPSBuilder->dumper.stream_dumper( pPSBuilder->dumper.stream_dumper_context, &output_data, sizeof(output_data) );
			pPSBuilder->output_bytes += len;
		}
	}

	pPSBuilder->input_blocks++;
	pPSBuilder->used_bytes += nSize ;

	return used_bytes;
}

int PSBulderPushDataInBuffer( PS_BUILDER *pPSBuilder, int nTrackIndex, int bGroup, BLOCK_BUFFER *pBlockBuffer )
{
	OUTPUT_DATA output_data={0};
	TRACK* track;
	int pes_header_bytes, header_bytes, content_bytes, bytes;
	ES_ELEMENT es_elmnt;
	uint8_t *p;
	int used_bytes= 0;
	int header_space, pading_bytes;

	if ( pPSBuilder->dumper.stream_dumper == NULL || pBlockBuffer->buffer_size == 0 || pBlockBuffer->data_size == 0 ) 
		return 0;

	track = &pPSBuilder->tracks->track[nTrackIndex];

	//information isn't ready
	if ( track->es_elmnt->content_type == 0 )
		return 0;

	//start from content header
	if ( track->es_blocks_counter == 0 && bGroup == 0 )
		return 0;

	output_data.group_flag = bGroup;
	output_data.fourcc = track->es_elmnt->format_fourcc;
	es_elmnt = pPSBuilder->es_streams.es_element[nTrackIndex];
	{
		pading_bytes = 0;
		header_space = (int)(pBlockBuffer->data_start - pBlockBuffer->buffer_start);
		pes_header_bytes = PES2HeaderBytes( es_elmnt.pes.has_pts, es_elmnt.pes.has_dts )
					           + es_elmnt.private_bytes;
		header_bytes = PACK_HEADER_BYTES + pes_header_bytes;
		ASSERT( header_bytes <= header_space );

		p = pBlockBuffer->data_start - header_bytes;

		content_bytes =	pBlockBuffer->data_size;

		//build and dump  a header of a block (PACK+PES)
		es_elmnt.pes.packet_length = content_bytes + pes_header_bytes - 6;
		bytes = BuildBlockHeader( pPSBuilder, &es_elmnt, p+used_bytes, header_bytes );
		ASSERT( bytes == header_bytes );
		used_bytes += bytes;

		
		if ( ( pPSBuilder->build_ctrl & CONTINUE_DATA_IN_BUFFER ) )
		{
			pading_bytes = (int)(p-pBlockBuffer->buffer_start);
			PadingBuffer( (char*)pBlockBuffer->buffer_start, pading_bytes );
			//dump  (padding+PACK+PES+content)
			ASSERT( p+used_bytes == pBlockBuffer->data_start );
			output_data.data_ptr = pBlockBuffer->buffer_start;
			output_data.bytes = used_bytes+content_bytes+pading_bytes;
			output_data.start_offset = pading_bytes;
			pPSBuilder->dumper.stream_dumper( pPSBuilder->dumper.stream_dumper_context, &output_data, sizeof(output_data) );
			pPSBuilder->output_bytes += bytes+content_bytes+pading_bytes;
		} else
		{
			//dump  (padding+PACK+PES+content)
			ASSERT( p+used_bytes == pBlockBuffer->data_start );
			output_data.data_ptr = p;
			output_data.bytes = used_bytes+content_bytes;
			pPSBuilder->dumper.stream_dumper( pPSBuilder->dumper.stream_dumper_context, &output_data, sizeof(output_data) );
			pPSBuilder->output_bytes += bytes+content_bytes;
		}
		//used_bytes += content_bytes;
		pPSBuilder->output_blocks++;
		track->es_blocks_counter++;

	}

	pPSBuilder->input_blocks++;
	pPSBuilder->used_bytes += content_bytes ;

	return content_bytes;
}


///////////////////////////////// PUSH SECTION  //////////////////////////////////////////

PS_BUILDER* CreatePSBuilder( TRACKS* pTracks, int nMaxBufferSize, int bBuildHeaderInBuffer )
{
	int i, bit_rate;
	PS_BUILDER *pPSBuilder = SAGETV_MALLOC( sizeof(PS_BUILDER) );

	pPSBuilder->ts_streams.total_streams = 0;
	pPSBuilder->ts_streams.num_stream = 0;
	pPSBuilder->ts_streams.ts_element = SAGETV_MALLOC( pTracks->total_track*sizeof(TS_ELEMENT) );
	pPSBuilder->es_streams.total_streams = pTracks->total_track;
	pPSBuilder->es_streams.num_stream = 0;
	pPSBuilder->es_streams.es_element = SAGETV_MALLOC( pTracks->total_track*sizeof(ES_ELEMENT) );
	pPSBuilder->av_streams.total_streams = pTracks->total_track;
	pPSBuilder->av_streams.num_stream = 0;
	pPSBuilder->av_streams.av_element = SAGETV_MALLOC( pTracks->total_track*sizeof(AV_ELEMENT) );
	for ( i = 0; i < pTracks->total_track; i++ )
	{
		pPSBuilder->ts_streams.ts_element[i].channel_index = i;
		pPSBuilder->es_streams.es_element[i].channel_index = i;
		pPSBuilder->av_streams.av_element[i].channel_index = i;

		pTracks->track[i].ts_elmnt = &pPSBuilder->ts_streams.ts_element[i];
		pTracks->track[i].es_elmnt = &pPSBuilder->es_streams.es_element[i];
		pTracks->track[i].av_elmnt = &pPSBuilder->av_streams.av_element[i];
	}
	pTracks->main_video_index = 0xffff;
	pTracks->main_audio_index = 0xffff;
	pPSBuilder->tracks = pTracks;

	
	bit_rate = HD_STREAM_RATE;

	pPSBuilder->demux = DEMUX(bit_rate); 
	if ( bBuildHeaderInBuffer )
	{
		pPSBuilder->build_ctrl |= BUILD_HEADER_IN_BUFFER;
		pPSBuilder->build_ctrl |= CONTINUE_DATA_IN_BUFFER;
	}
	if ( nMaxBufferSize <2048 )
		nMaxBufferSize = 2048;
	pPSBuilder->es_block_size = nMaxBufferSize;

	if ( !bBuildHeaderInBuffer )
	{
		pPSBuilder->buffer_size = nMaxBufferSize;
		pPSBuilder->block_buffer = SAGETV_MALLOC(pPSBuilder->buffer_size);
		PadingBuffer( (char*)pPSBuilder->block_buffer, pPSBuilder->buffer_size );
	} else
	{
		pPSBuilder->buffer_size = 512;
		pPSBuilder->block_buffer = SAGETV_MALLOC( pPSBuilder->buffer_size );
	}

	pPSBuilder->pading_block_enable = 1;
	SageLog(( _LOG_TRACE, 3, TEXT("PS Builder is created  version 2.0.1  ZQ..") ));
	return pPSBuilder;
}

void ReleasePSBuilder( PS_BUILDER *pPSBuilder )
{
	SAGETV_FREE( pPSBuilder->block_buffer );
	SAGETV_FREE( pPSBuilder->ts_streams.ts_element );
	SAGETV_FREE( pPSBuilder->es_streams.es_element );
	SAGETV_FREE( pPSBuilder->av_streams.av_element );
	SAGETV_FREE( pPSBuilder );
}

void ResetPSBuilder( PS_BUILDER *pPSBuilder )
{
	pPSBuilder->used_bytes = 0;
	pPSBuilder->output_bytes = 0;
	pPSBuilder->input_blocks = 0;
	pPSBuilder->output_blocks = 0;
	pPSBuilder->out_of_order_blocks = 0;
	pPSBuilder->state = 0;
	memset( &pPSBuilder->bits, 0, sizeof(pPSBuilder->bits) );
}

int FlushEndOfCode( PS_BUILDER *pPSBuilder )
{
	OUTPUT_DATA output_data={0};
	uint8_t buf[16];
	if ( pPSBuilder->dumper.stream_dumper != NULL && pPSBuilder->output_bytes ) 
	{
		AppedEndOFCode( (char*)buf, 4 );
		//output_data.track = &pPSBuilder->tracks->track[0];
		output_data.data_ptr = buf;
		output_data.bytes = 4;
		pPSBuilder->dumper.stream_dumper( pPSBuilder->dumper.stream_dumper_context, &output_data, sizeof(output_data) );

		pPSBuilder->output_bytes += 4;
		pPSBuilder->output_blocks++;
		pPSBuilder->tracks->track[0].es_blocks_counter++;
		return 1;
	}

	return 0;

}

static int BuildPackHeader( PS_BUILDER* pPSBuilder, long nMuxRate, ULONGLONG scr )
{
	int bytes;
	ULONGLONG ext_time;
	ULONGLONG time;

	ext_time = scr % 300;
	time = scr / 300;

	PutOutBITS( &pPSBuilder->bits, 0x000001ba, 32);		// pack id						32
	PutOutBITS( &pPSBuilder->bits, 1, 2);				// 0x01							2
	PutOutBITS( &pPSBuilder->bits, (uint32_t)(time >> 30)& 0x07, 3);	// system_clock_reference_base				3
	PutOutBITS( &pPSBuilder->bits, 1, 1);				// marker_bit					1
	PutOutBITS( &pPSBuilder->bits, (uint32_t)(time >> 15)& 0x7fff, 15);	// system_clock_reference_base			15
	PutOutBITS( &pPSBuilder->bits, 1, 1);				// marker_bit					1
	PutOutBITS( &pPSBuilder->bits, (uint32_t)(time & 0x7fff), 15);		// system_clock_reference_base1			15
	PutOutBITS( &pPSBuilder->bits, 1, 1);				// marker_bit					1
	PutOutBITS( &pPSBuilder->bits, (uint32_t)ext_time, 9);		// system_clock_reference_extension				9
	PutOutBITS( &pPSBuilder->bits, 1, 1);				// marker_bit					1
	PutOutBITS( &pPSBuilder->bits, nMuxRate, 22);		// program_mux_rate			22
	//PutOutBITS( &pPSBuilder->bits, 1, 1);				// marker_bit					1
	//PutOutBITS( &pPSBuilder->bits, 1, 1);				// marker_bit					1
	//PutOutBITS( &pPSBuilder->bits, 31, 5);			// reserved						5
	//PutOutBITS( &pPSBuilder->bits, 0, 3);				// pack_stuffing_length			3
	PutOutBITS( &pPSBuilder->bits, 0x3f8, 10 );	  //marker_bit, marker_bit, reserved, pack_stuffing_length	
	bytes = CloseOutBITS( &pPSBuilder->bits );
	return bytes;

}

static int BuildSystemHeader( PS_BUILDER* pPSBuilder, int videostream, int vidbsize,
	                          int audiostream, int audbsize, int demux )
{
	int bytes;
	int len = 6+6;
	if (videostream != 0)
		len += 3;

	if (audiostream != 0)
		len += 3;

	PutOutBITS( &pPSBuilder->bits, 0x000001bb, 32);			// system id				32
	PutOutBITS( &pPSBuilder->bits, len, 16);					// header_length			16
	PutOutBITS( &pPSBuilder->bits, 1, 1);					// marker_bit					1
	PutOutBITS( &pPSBuilder->bits, demux, 22);				// rate_bound					22
	//PutOutBITS( &pPSBuilder->bits, 1, 1);					// marker_bit					1
	//PutOutBITS( &pPSBuilder->bits, 1, 6);					// audio_bound					6
	//PutOutBITS( &pPSBuilder->bits, 0, 1);					// fixed_flag					1
	//PutOutBITS( &pPSBuilder->bits, 0, 1);					// CSPS_flag					1
	//PutOutBITS( &pPSBuilder->bits, 1, 1);					// system_audio_lock_flag		1
	//PutOutBITS( &pPSBuilder->bits, 1, 1);					// system_video_lock_flag		1
	//PutOutBITS( &pPSBuilder->bits, 1, 1);					// marker_bit					1
	//PutOutBITS( &pPSBuilder->bits, 1, 5);					// video_bound					5
	//PutOutBITS( &pPSBuilder->bits, 0, 1);					// packet_rate_restriction_flag	1
	//PutOutBITS( &pPSBuilder->bits, 127, 7);					// reserved_byte			7
	PutOutBITS( &pPSBuilder->bits, 0x104e17f, 25 ); //OP

	//if (videostream != 0)
	//{
	//	PutOutBITS( &pPSBuilder->bits, videostream, 8);
	//	PutOutBITS( &pPSBuilder->bits, 0x3, 2);
	//	PutOutBITS( &pPSBuilder->bits, vidbscl, 1);
	//	PutOutBITS( &pPSBuilder->bits, vidbsize, 13);
	//}

	//if (audiostream != 0)
	//{
	//	PutOutBITS( &pPSBuilder->bits, audiostream, 8);
	//	PutOutBITS( &pPSBuilder->bits, 0x3, 2);
	//	PutOutBITS( &pPSBuilder->bits, audbscl, 1);
	//	PutOutBITS( &pPSBuilder->bits, audbsize, 13);
	//}

	if (videostream != 0)
	{
		//PutOutBITS( &pPSBuilder->bits, 0xb9, 8); //all video streams
		//PutOutBITS( &pPSBuilder->bits, 0x3, 2);
		//PutOutBITS( &pPSBuilder->bits, 1, 1);    // P_STD_buffer_bound_scale 1: 1024
		PutOutBITS( &pPSBuilder->bits, 0x5cf, 11 ); //OP
		PutOutBITS( &pPSBuilder->bits, vidbsize/1024, 13);
	}

	if (audiostream != 0)
	{
		//PutOutBITS( &pPSBuilder->bits, 0xb8, 8); //all auido streams
		//PutOutBITS( &pPSBuilder->bits, 0x3, 2);  
		//PutOutBITS( &pPSBuilder->bits, 0, 1);    // P_STD_buffer_bound_scale 0: 128
		PutOutBITS( &pPSBuilder->bits, 0x5c6, 11 ); //OP
		PutOutBITS( &pPSBuilder->bits, audbsize/128, 13);
	}

	//PutOutBITS( &pPSBuilder->bits, 0xbd, 8); //private stream 1 
	//PutOutBITS( &pPSBuilder->bits, 0x3, 2);
	//PutOutBITS( &pPSBuilder->bits, 1, 1);    //P_STD_buffer_bound_scale 1: 1024
	//PutOutBITS( &pPSBuilder->bits, (58*1024)/1024, 13);
	PutOutBITS( &pPSBuilder->bits, 0xBDE03A, 24 ); //OP

	//PutOutBITS( &pPSBuilder->bits, 0xbf, 8); //private stream 12
	//PutOutBITS( &pPSBuilder->bits, 0x3, 2);
	//PutOutBITS( &pPSBuilder->bits, 1, 1);    //P_STD_buffer_bound_scale 1: 1024
	//PutOutBITS( &pPSBuilder->bits, (2*1024)/1024, 13);
	PutOutBITS( &pPSBuilder->bits, 0xBFE002, 24 ); //OP

	bytes = CloseOutBITS( &pPSBuilder->bits );
	return bytes;
	
}
#define PSM_START_CODE			0xBC

#define REGISTRATION_DESC		0x05
#define CA_DESC					0x09
#define ISO639_LANGUAGE_DESC	0x0a
#define DVB_PRIVATE_INDICATOR   0x0f
#define DVB_BITRATE				0x0e
#define DVB_AC3_DESC			0x6a
#define ATSC_AC3_DESC			0x81
#define ATSC_CAPTION_DESC		0x86
#define MPEG4_AUDIO_DESC		0x1c
#define DTS_DESC				0x73
#define VIDEO_DESC				0x02
#define AUDIO_DESC				0x03
#define TELTEXT_DESC			0x56
#define SUBTITLE_DESC			0x59
#define VBI_DATA_DESC			0x45
#define VBI_TELTEXT				0x46
#define DVB_STREAM_ID			0x52
#define DVB_USER_DESC			0xfe

uint32_t CalTSCRC32( const uint8_t *pData, int nLen );

static int PutLanguageCodeDesc( uint32_t LanguageCode, uint8_t AudioType, uint8_t *pData, int nLen )
{
	uint8_t buf[4];
	buf[0] = (uint8_t)(LanguageCode & 0xff);
	buf[1] = (uint8_t)((LanguageCode >>8) & 0xff);
	buf[2] = (uint8_t)((LanguageCode >>16) & 0xff);
	buf[3] = AudioType;
	PutDescriptor( pData, ISO639_LANGUAGE_DESC,  buf, 4  );
	return 4+2; //6 bytes;
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


int BuildPsmPacket( PS_BUILDER* pPSBuilder, uint8_t* pData, int nSize )
{
	int i, bytes, packet_len, used_bytes=0;
	uint8_t *p;
	uint32_t crc;
	ES_ELEMENT *es_elmnt;
	TS_ELEMENT *ts_elmnt;
	AV_ELEMENT *av_elmnt;

	TRACKS *tracks = pPSBuilder->tracks;

	if ( nSize < 12 )
		return 0;

    pData[0] = 0x00; 
	pData[1] = 0x00; 
	pData[2] = 0x01; 
	pData[3]  = PSM_START_CODE; //0x000001bc
	
	pData[6] = 0x80;  //indicator
	pData[7] = 0x01;  //mark

	pData[8] = 0x00; //no descript;
	pData[9] = 0x00; //no descript;
	
	used_bytes = 12;
	bytes = 0;

	p = pData+12;
	for ( i = 0; i<tracks->total_track && used_bytes + 4 < nSize; i++ )
	{
		uint8_t stream_id, stream_type;
		int desc_len =0;
		es_elmnt = pPSBuilder->tracks->track[i].es_elmnt;
		ts_elmnt = pPSBuilder->tracks->track[i].ts_elmnt;
		av_elmnt = pPSBuilder->tracks->track[i].av_elmnt;
		if ( av_elmnt->format_fourcc )
		{
			stream_id = es_elmnt->pes.stream_id;
			if ( IS_PRIVATE1_STREAM_ID(stream_id) ) //0xBD
				stream_id = es_elmnt->private_data[0];
			else
			if ( IS_OTHER_STREAM_ID(stream_id) )    //0xFD
				stream_id = es_elmnt->private_data[0];

			if ( ts_elmnt != NULL)
				stream_type = ts_elmnt->type;
			else
				stream_type = LookupStreamType( av_elmnt->format_fourcc );

			if ( stream_type == 0 || stream_id == 0 )
				continue;

			p[bytes  ] = stream_type;  //filling stream type
			p[bytes+1] = stream_id;    //filling stream id

			used_bytes += 4;

			p[bytes+4] = 0xff; //terminator of descriptor
			if ( av_elmnt->content_type == AUDIO_DATA )
			{
				uint32_t language_code;
				uint8_t audio_type;
				if ( ts_elmnt != NULL && ts_elmnt->language_code )
				{
					language_code = ts_elmnt->language_code;
					audio_type    = ts_elmnt->audio_type;
				}
				else
				{
					language_code = es_elmnt->language_code;
					audio_type    = es_elmnt->audio_type;
				}
				
				if ( language_code )
					desc_len += PutLanguageCodeDesc( language_code, audio_type, p+bytes+4+desc_len, nSize-used_bytes-desc_len );
			} else
			if ( av_elmnt->content_type == SUBTITLE_DATA )
			{
				if ( av_elmnt->d.s.sub.type == 1 ) //DVB-SUBTITLE
					desc_len += PutSubtitleCodeDesc( &av_elmnt->d.s.sub, p+bytes+4+desc_len, nSize-used_bytes-desc_len );
					
			}

			p[bytes+2] = (desc_len >> 8) & 0xff; //no descriptor
			p[bytes+3] = desc_len & 0xff ;
			
			bytes += 4+desc_len;
			used_bytes += desc_len;
		}
	}

	pData[10] = (bytes >> 8)&0xff;
	pData[11] = bytes & 0xff;

	packet_len = 12+bytes+4-6;
	pData[4] = (packet_len>>8) & 0xff;
	pData[5] =  packet_len & 0xff;

	if ( used_bytes + 4 > nSize )
		return 0;

	crc = CalTSCRC32( (const uint8_t*)pData+6, packet_len-4 );

	p[bytes++] = (uint8_t)((crc >> 24) & 0xff);
	p[bytes++] = (uint8_t)(crc >> 16) & 0xff;
	p[bytes++] = (uint8_t)(crc >> 8) & 0xff;
	p[bytes++] = (uint8_t)(crc) &  0xff;

	return packet_len+6;
}

#define ISO_11172_END_CODE       0xB9
#define PADDING_START_CODE       0xBE
static int AppedEndOFCode( char* pOutBuf, int nLen )
{
	if ( nLen < 4 )
		return 0;
	*pOutBuf++ = 0x00;
	*pOutBuf++ = 0x00;
	*pOutBuf++ = 0x01;
	*pOutBuf++ = ISO_11172_END_CODE;
	return 4;
}

static int PadingBuffer( char* pOutBuf, int nLen )
{
	int len = nLen;
	if ( len < 6 )
	{
		*pOutBuf++ = 0xff;
		*pOutBuf++ = 0xff;
		*pOutBuf++ = 0xff;
		*pOutBuf++ = 0xff;
		*pOutBuf++ = 0xff;
		*pOutBuf++ = 0xff;
		return 0;
	}
	len-= 6;

	*pOutBuf++ = 0x00;
	*pOutBuf++ = 0x00;
	*pOutBuf++ = 0x01;
	*pOutBuf++ = PADDING_START_CODE ;
	*pOutBuf++ = len >> 8; 
	*pOutBuf++ = len & 0xff;

	for ( ; len>0; len-- )
		*pOutBuf++ = 0xff;

	return nLen + 6;
}

static void BuildPadBufferHeader( char* pOutBuf, int nLen )
{
	int len = nLen;
	if ( len < 6 )
	{
		*pOutBuf++ = 0xff;
		*pOutBuf++ = 0xff;
		*pOutBuf++ = 0xff;
		*pOutBuf++ = 0xff;
		*pOutBuf++ = 0xff;
		*pOutBuf++ = 0xff;
		return ;
	}
	len-= 6;

	*pOutBuf++ = 0x00;
	*pOutBuf++ = 0x00;
	*pOutBuf++ = 0x01;
	*pOutBuf++ = PADDING_START_CODE ;
	*pOutBuf++ = len >> 8; 
	*pOutBuf++ = len & 0xff;
}

static int BuildPES2Header( BITS_T* pBits, PES *pPES )
{
	int bytes;
	int hdrlen = 0;
	int PTS_DTS_flags = 0;
	int len = pPES->packet_length;

	if ( pPES->has_pts )
	{
		if ( pPES->has_dts )
		{
			PTS_DTS_flags = 3;
			hdrlen += 10;
		}
		else
		{
			PTS_DTS_flags = 2;
			hdrlen += 5;
		}
	}

	PutOutBITS( pBits, 0x000001, 24);						// packet_start_code_prefix		24
	PutOutBITS( pBits, (uint32_t)pPES->stream_id, 8);			// directory_stream_id		8
	PutOutBITS( pBits, len, 16);								// PES_packet_length		16
	//PutOutBITS( pBits, 0x2, 2);								// '10'						2
	//PutOutBITS( pBits, 0, 2);								// PES_scrambling_control	2
	//PutOutBITS( pBits, 1, 1);								// PES_priority				1
	//PutOutBITS( pBits, 0, 1);								// data_alignment_indicator	1
	//PutOutBITS( pBits, 0, 1);								// copyright				1
	//PutOutBITS( pBits, 0, 1);								// original_or_copy			1
	PutOutBITS( pBits, 0x88, 8); //OP	

	PutOutBITS( pBits, PTS_DTS_flags, 2);					// PTS_DTS_flags			2
	//PutOutBITS( pBits, 0, 1);								// ESCR_flag				1
	//PutOutBITS( pBits, 0, 1);								// ES_rate_flag				1
	//PutOutBITS( pBits, 0, 1);								// DSM_trick_mode_flag		1
	//PutOutBITS( pBits, 0, 1);								// additional_copy_info_flag	1
	//PutOutBITS( pBits, 0, 1);								// PES_CRC_flag				1
	//PutOutBITS( pBits, 0, 1);								// PES_extension_flag		1
	PutOutBITS( pBits, 0x0, 6); //OP

	PutOutBITS( pBits, hdrlen, 8);							// PES_header_data_length	8
	if (PTS_DTS_flags == 2)
	{
		PutOutBITS( pBits, 2, 4);							// '0010'			4
		PutOutBITS( pBits, (uint32_t)(pPES->pts >> 30), 3);	// PTS [32..30]		3
		PutOutBITS( pBits, 1, 1);							// marker bit		1
		PutOutBITS( pBits, (uint32_t)(pPES->pts >> 15), 15);	// PTS [29..15]		15
		PutOutBITS( pBits, 1, 1);							// marker bit		1
		PutOutBITS( pBits, (uint32_t)pPES->pts, 15);			// PTS [14..0]		15
		PutOutBITS( pBits, 1, 1);							// marker bit		1
	}
	else if (PTS_DTS_flags == 3)
	{
		PutOutBITS( pBits, 3, 4);							// '0011'			4
		PutOutBITS( pBits, (uint32_t)(pPES->pts >> 30), 3);	// PTS [32..30]		3
		PutOutBITS( pBits, 1, 1);							// marker bit		1
		PutOutBITS( pBits, (uint32_t)(pPES->pts >> 15), 15);	// PTS [29..15]		15
		PutOutBITS( pBits, 1, 1);							// marker bit		1
		PutOutBITS( pBits, (uint32_t)pPES->pts, 15);			// PTS [14..0]		15
		//PutOutBITS( pBits, 1, 1);							// marker bit		1
		//PutOutBITS( pBits, 1, 4);							// '0001'			4
		PutOutBITS( pBits, 0x11, 5); //OP

		PutOutBITS( pBits, (uint32_t)(pPES->dts >> 30), 3);	// DTS [32..30]		3
		PutOutBITS( pBits, 1, 1);							// marker bit		1
		PutOutBITS( pBits, (uint32_t)(pPES->dts >> 15), 15);	// DTS [29..15]		15
		PutOutBITS( pBits, 1, 1);							// marker bit		1
		PutOutBITS( pBits, (uint32_t)pPES->dts, 15);			// DTS [14..0]		15
		PutOutBITS( pBits, 1, 1);							// marker bit		1
	}
	bytes = CloseOutBITS( pBits );
	return bytes;
}

int CreatePES2Header( uint8_t*pBuffer, int nMaxBufferSize, PES *pPES )
{
	BITS_T bits;
	InitOutBITS( &bits, pBuffer, nMaxBufferSize );
	return BuildPES2Header( &bits, pPES );
}

static int CreateSystemHeader( PS_BUILDER* pPSBuilder, uint8_t* pData, int nSize  )
{
	int bytes;
	uint8_t vidstream_id = 0, audstream_id = 0;

	if ( pPSBuilder->tracks->main_video_index == 0xffff && 
		 pPSBuilder->tracks->main_audio_index == 0xffff )
		 return 0;

	if ( pPSBuilder->tracks->main_video_index != 0xffff )
		vidstream_id = pPSBuilder->tracks->track[pPSBuilder->tracks->main_video_index].es_elmnt->pes.stream_id;
	if ( pPSBuilder->tracks->main_audio_index != 0xffff )
		audstream_id = pPSBuilder->tracks->track[pPSBuilder->tracks->main_audio_index].es_elmnt->pes.stream_id;

	InitOutBITS( &pPSBuilder->bits, pData, nSize );
	BuildPackHeader( pPSBuilder, pPSBuilder->demux, 0 );

	bytes = BuildSystemHeader( pPSBuilder, vidstream_id, (vidstream_id ? VIDEO_BLOCK_SIZE:0  ), 
										   audstream_id, (audstream_id ? AUDIO_BLOCK_SIZE:0  ),
										   pPSBuilder->demux );

	return bytes;
}


static int BuildBlockHeader( PS_BUILDER* pPSBuilder, ES_ELEMENT *pESElmnt, uint8_t* pData, int nSize )
{	int bytes=0;
	//ES_ELEMENT* es_elmnt = &pPSBuilder->es_streams.es_element[nTrackIndex];
	InitOutBITS( &pPSBuilder->bits, pData, nSize );
	BuildPackHeader( pPSBuilder, pPSBuilder->demux, pESElmnt->scr );
	if ( pESElmnt->es_type == ES_MPEG1 )
	{
		//ASSERT( 0 ); //not done yet
		//bytes += BuildPES1Header( pPSBuilder, int streamid, int len, ULONGLONG PTS, ULONGLONG DTS )
		pESElmnt->es_type = ES_MPEG2;
		bytes = BuildPES2Header( &pPSBuilder->bits, &pESElmnt->pes );
	} else
	{
		bytes = BuildPES2Header( &pPSBuilder->bits, &pESElmnt->pes );
	}

	if ( pESElmnt->private_bytes > 0 )
	{
		memcpy( pData+bytes, pESElmnt->private_data, pESElmnt->private_bytes );
		bytes += pESElmnt->private_bytes;
	}

	return bytes;
}

int SetupTracks( TRACK *pTrack )
{
	ES_ELEMENT* es_elmnt = pTrack->es_elmnt;
	AV_ELEMENT* av_elmnt = pTrack->av_elmnt;
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "MPGV" ) || 
		 av_elmnt->format_fourcc == SAGE_FOURCC( "H264" ) )
	{
		es_elmnt->pes.pes_sub_id = 0xE0 |(es_elmnt->es_id & 0x0f);
		es_elmnt->pes.stream_id  = 0xE0 |(es_elmnt->es_id & 0x0f);
		es_elmnt->private_bytes = 0;
		return 1;
	} else
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "MPGA" ) )
	{
		es_elmnt->pes.pes_sub_id = 0xC0 |(es_elmnt->es_id & 0x1f);
		es_elmnt->pes.stream_id  = 0xC0 |(es_elmnt->es_id & 0x1f);
		es_elmnt->private_bytes = 0;
		return 1;
	} else
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "AC3 " ) )
	{
		es_elmnt->pes.pes_sub_id = 0x80|(es_elmnt->es_id & 0x07);
		es_elmnt->pes.stream_id = 0xBD ;
		es_elmnt->private_bytes = 4;
		es_elmnt->private_data[0] = 0x80|(es_elmnt->es_id & 0x07);
		es_elmnt->private_data[1] = 1;
		es_elmnt->private_data[2] = 0;
		es_elmnt->private_data[3] = 0;
		return 1;
	} else
	if ( av_elmnt->format_fourcc  == SAGE_FOURCC( "AC3T" )
		|| av_elmnt->format_fourcc == SAGE_FOURCC( "AC3E" ) )
	{
		es_elmnt->pes.pes_sub_id = 0x80|(es_elmnt->es_id & 0x07);
		es_elmnt->pes.stream_id = 0xFD ;
		es_elmnt->private_bytes = 4;
		es_elmnt->private_data[0] = 0x80|(es_elmnt->es_id & 0x07);
		es_elmnt->private_data[1] = 1;
		es_elmnt->private_data[2] = 'C';
		es_elmnt->private_data[3] = av_elmnt->format_fourcc == SAGE_FOURCC( "AC3T" )?1: \
			                        av_elmnt->format_fourcc == SAGE_FOURCC( "AC3E" )?2:0xff;
		return 1;
	} else
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "DTS " ) )
	{
		es_elmnt->pes.pes_sub_id = 0x88|(es_elmnt->es_id & 0x07);
		es_elmnt->pes.stream_id = 0xBD ;
		es_elmnt->private_bytes = 4;
		es_elmnt->private_data[0] = 0x88|(es_elmnt->es_id & 0x07);
		es_elmnt->private_data[1] = 1;
		es_elmnt->private_data[2] = 0;
		es_elmnt->private_data[3] = 0;
		return 1;
	} else
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "DTSM" ) )
	{
		es_elmnt->pes.pes_sub_id = 0x88|(es_elmnt->es_id & 0x07);
		es_elmnt->pes.stream_id = 0xBD ;
		es_elmnt->private_bytes = 4;
		es_elmnt->private_data[0] = 0x88|(es_elmnt->es_id & 0x07);
		es_elmnt->private_data[1] = 1;
		es_elmnt->private_data[2] = 'C';
		es_elmnt->private_data[3] = 2;
		return 1;
	} else
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "AAC " ) ) //SAGETV standard
	{
		es_elmnt->pes.pes_sub_id = 0xE8|(es_elmnt->es_id & 0x7);
		es_elmnt->pes.stream_id = 0xBD ;
		es_elmnt->private_bytes = 4;
		es_elmnt->private_data[0] = 0xE8|(es_elmnt->es_id & 0x7);
		es_elmnt->private_data[1] = 2;
		es_elmnt->private_data[2] = 0;
		es_elmnt->private_data[3] = 0;
		return 1;
	} else
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "LPCM" ) )
	{
		es_elmnt->pes.pes_sub_id = 0xA0|(es_elmnt->es_id & 0x0f);
		es_elmnt->pes.stream_id = 0xBD ;
		es_elmnt->private_bytes = 1;
		es_elmnt->private_data[0] = 0xA0|(es_elmnt->es_id & 0x0f);
		return 1;
	} else
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "TTX " ) )
	{
		es_elmnt->pes.pes_sub_id = 0x10|(es_elmnt->es_id & 0x0f);
		es_elmnt->pes.stream_id = 0xBD ;
		//es_elmnt->sub_id = es_elmnt->es_id & 0x0f;
		//es_elmnt->stream_id = 0xBD ;
		es_elmnt->private_bytes = 0;
		es_elmnt->private_data[0] = 0x10|(es_elmnt->es_id & 0x0f);
		return 1;
	} else
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "SUB " ) ) 
	{
		es_elmnt->pes.pes_sub_id = 0x20|(es_elmnt->es_id & 0x0f);  //ZQ new way subtitle
		es_elmnt->pes.stream_id = 0xBD ;
		es_elmnt->private_bytes = 1;
		es_elmnt->private_data[0] = 0x20|(es_elmnt->es_id & 0x0f);
		return 1;
	} else
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "VC1 " ) )
	{
		es_elmnt->pes.pes_sub_id = 0x10|(es_elmnt->es_id & 0x0f);
		es_elmnt->pes.stream_id = 0xFD ;
		//es_elmnt->sub_id = es_elmnt->es_id & 0x0f;
		//es_elmnt->stream_id = 0xFD ;
		es_elmnt->private_bytes = 4;
		es_elmnt->private_data[0] = 0x10|(es_elmnt->es_id & 0x0f);
		ASSERT( 0 );//not done yet 
		return 1;
	} else
	if ( av_elmnt->format_fourcc == SAGE_FOURCC( "AACH" ) )
	{
		es_elmnt->pes.pes_sub_id = 0xC0 |(es_elmnt->es_id & 0x1f);
		es_elmnt->pes.stream_id  = 0xC0 |(es_elmnt->es_id & 0x1f);
		es_elmnt->private_bytes = 0;
		return 1;
	}


	return 0;	
}

#define SAGETV_H264_INFO     0x40
static int FillSageInfo( PS_BUILDER* pPSBuilder, uint8_t* ptr, int bytes, uint8_t CtrlBits )
{
	int used_bytes = 0;
	if ( bytes < 12 ) return 0;
	memcpy( ptr, "SAGE", 4 );
	ptr += 4;
	*ptr++ = SAGE_MPEG_VERSION;
	*ptr++ = CtrlBits;
	*ptr++ = pPSBuilder->sagetv_private_data.video_track_num;
	*ptr++ = pPSBuilder->sagetv_private_data.audio_track_num;
	*ptr++ = pPSBuilder->sagetv_private_data.video_main_track;
	*ptr++ = pPSBuilder->sagetv_private_data.audio_main_track;
	*ptr++ = pPSBuilder->sagetv_private_data.main_video_stream_type;
	*ptr++ = pPSBuilder->sagetv_private_data.main_audio_stream_type;
	used_bytes = 4+8;
	
	if ( pPSBuilder->sagetv_private_data.main_video_stream_type == H264_STREAM_TYPE  )
	{
		uint8_t frame_rate = (uint8_t)pPSBuilder->h264_frame_rate;
		AppendDescriptor( ptr, bytes-used_bytes, SAGETV_H264_INFO, &frame_rate, 1 );
		bytes += 2+1;
	}
	bytes += 2;
	return bytes;
}

int CreatSageStreamHeader( PS_BUILDER* pPSBuilder )
{
	int bytes;
	uint8_t *buf_ptr=pPSBuilder->block_buffer;
	int buf_size = 16*6;
	uint8_t video_track_num = 0, audio_track_num = 0;

	//int buf_size = pPSBuilder->block_buffer

	if ( pPSBuilder->dumper.stream_dumper == NULL  ) 
	{
		SageLog(( _LOG_TRACE, 3, TEXT("dumper isn't set up, PS header isn't created.") ));
		return 0;
	}

	bytes = CreateSystemHeader( pPSBuilder, buf_ptr, buf_size);
	if ( bytes == 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("failed creating system header, PS header isn't created.") ));
		return 0;
	}
	PadingBuffer( (char*)buf_ptr+bytes, buf_size-bytes );

	{   //embed sagetv information into padding packet
		int i;
		uint8_t video_main_track = 0xff, audio_main_track = 0xff;
		uint8_t main_video_stream_type = 0, main_audio_stream_type = 0;
		ES_ELEMENT *es_elmnt;
		TS_ELEMENT *ts_elmnt;
		AV_ELEMENT *av_elmnt;
		int len;
		uint8_t *ptr;
		TRACKS *tracks;

		ptr = buf_ptr + bytes+6;
		len = buf_size - (bytes+6);

		tracks = pPSBuilder->tracks;
		for ( i = 0; i<tracks->total_track; i++ )
		{
			if ( tracks->track[i].av_elmnt->content_type == VIDEO_DATA )
				video_track_num++;
			else
			if ( tracks->track[i].av_elmnt->content_type == AUDIO_DATA )
				audio_track_num++;
				
		}

		if ( pPSBuilder->tracks->main_video_index != 0xffff )
		{
			es_elmnt = pPSBuilder->tracks->track[pPSBuilder->tracks->main_video_index].es_elmnt;
			ts_elmnt = pPSBuilder->tracks->track[pPSBuilder->tracks->main_video_index].ts_elmnt;
			av_elmnt = pPSBuilder->tracks->track[pPSBuilder->tracks->main_video_index].av_elmnt;
			
			video_main_track = es_elmnt->es_id & 0xff;  //### main audio/video
			if ( ts_elmnt != NULL)
				main_video_stream_type = ts_elmnt->type;
			else
				main_video_stream_type = LookupStreamType( av_elmnt->format_fourcc );
			
		}
		if ( pPSBuilder->tracks->main_audio_index != 0xffff )
		{
			es_elmnt = pPSBuilder->tracks->track[pPSBuilder->tracks->main_audio_index].es_elmnt;
			ts_elmnt = pPSBuilder->tracks->track[pPSBuilder->tracks->main_audio_index].ts_elmnt;
			av_elmnt = pPSBuilder->tracks->track[pPSBuilder->tracks->main_audio_index].av_elmnt;

			audio_main_track = es_elmnt->es_id & 0xff;   //### main audio/video
			if ( ts_elmnt != NULL )
				main_audio_stream_type = ts_elmnt->type;
			else
				main_audio_stream_type = LookupStreamType( av_elmnt->format_fourcc );
		}

		pPSBuilder->sagetv_private_data.video_track_num = video_track_num;
		pPSBuilder->sagetv_private_data.audio_track_num = audio_track_num;
		pPSBuilder->sagetv_private_data.video_main_track = video_main_track;
		pPSBuilder->sagetv_private_data.audio_main_track = audio_main_track;
		pPSBuilder->sagetv_private_data.main_video_stream_type = main_video_stream_type;
		pPSBuilder->sagetv_private_data.main_audio_stream_type = main_audio_stream_type;

		FillSageInfo( pPSBuilder, ptr, len, 0 );
	}

	ASSERT( pPSBuilder->buffer_size >= buf_size + 16*6 );
	//build PSM packet  
	buf_ptr += buf_size;
	buf_size = pPSBuilder->buffer_size - buf_size;
	InitOutBITS( &pPSBuilder->bits, buf_ptr, buf_size );
	bytes = BuildPackHeader( pPSBuilder, pPSBuilder->demux, 0 );
	bytes += BuildPsmPacket( pPSBuilder, buf_ptr+bytes, buf_size-bytes );
	PadingBuffer( (char*)buf_ptr+bytes, buf_size-bytes );
	pPSBuilder->system_packet_bytes = pPSBuilder->buffer_size;
	SageLog(( _LOG_TRACE, 3, TEXT("system header, PSM (bytes:0x%x), SageInfo was created, video_track:%d audio_track:%d."), 
			   bytes, video_track_num, audio_track_num ));

	return 1;
}

void DisablePadingPacket( PS_BUILDER *pPSBuilder )
{	
	pPSBuilder->pading_block_enable = 0; 
}


/*
//I embedded Sage information in pad packets
//format: SAGE (4byte mark), version(1byte), ctrl bits ( 1byt), video tracks num( 1 byte ), audio track num( 1 bytes )
//  main video track( 1 bytes ); main audi track ( 1 bytes )
int CreatSageStreamHeader( PS_BUILDER* pPSBuilder, uint8_t VidstreamId, uint8_t AudstreamId,
            uint8_t VidstreamType, uint8_t AudstreamType, uint8_t VidsNum, uint8_t AudsNum, 
			uint8_t VidsMain, uint8_t AudsMain, uint8_t CtrlBits )
{
	int bytes; 
	uint8_t *ptr;
	InitOutBITS( &pPSBuilder->bits, pPSBuilder->bits_buf, sizeof(pPSBuilder->bits_buf) );

	if ( pPSBuilder->remapping_stream && IsAudioStreamId(AudstreamId) )
		AudstreamId = 0xC0 | (AudsMain & 0x1f);

	pPSBuilder->video_demux_rate = DEMUX;
	pPSBuilder->audio_demux_rate = DEMUX;
	pPSBuilder->main_audio_index = AudsMain;
	pPSBuilder->main_video_index = VidsMain;
	pPSBuilder->total_audio_num = AudsNum;
	pPSBuilder->total_video_num = VidsNum;
	pPSBuilder->main_video_stream_type = VidstreamType;
	pPSBuilder->main_audio_stream_type = AudstreamType;

	bytes = BuildStreamHeader( pPSBuilder, VidstreamId, AudstreamId );
	FlushOutBITS( &pPSBuilder->bits,  pPSBuilder->video_buffer[0], bytes );
	PadingBuffer( pPSBuilder, pPSBuilder->video_buffer[0]+bytes, PACKETSIZE-bytes );

	//embedding Sage Information
	ptr = (uint8_t*)pPSBuilder->video_buffer[0]+bytes+6;
	FillSageInfo( pPSBuilder, ptr, PACKETSIZE-bytes-6, 0 );PadingBuffer

	PushPESPacket( pPSBuilder, AllocPESPacket( pPSBuilder ), 0,  pPSBuilder->video_buffer[0], PACKETSIZE );
	memset( pPSBuilder->video_data_bytes, 0, sizeof(pPSBuilder->video_data_bytes) );
	memset( pPSBuilder->audio_data_bytes, 0, sizeof(pPSBuilder->audio_data_bytes) );

	bytes = 0;
	if ( pPSBuilder->PSM_packet_enable ) {
		uint8_t stream_id[2]; uint8_t stream_type[2];
		int stream_num;
		stream_num = 2;
		stream_id[0]  = VidstreamId;
		stream_type[0]= VidstreamType;
		stream_id[1]  = AudstreamId;
		stream_type[1]= AudstreamType;
		if ( stream_id[1] == 0xbd && stream_type[1] == 0x06  )
			stream_type[1] = 0x81;

		bytes = BuildPsmPacket( pPSBuilder, stream_id, stream_type, stream_num );
	} else
	if ( VidstreamType ==  H264_STREAM_TYPE ) //I insert PSM packet for H264 stream
	{
		uint8_t stream_id[2]; uint8_t stream_type[2];
		int stream_num;
		stream_num = 2;
		stream_id[0]  = VidstreamId;
		stream_type[0]= VidstreamType;
		stream_id[1]  = AudstreamId;
		stream_type[1]= AudstreamType;
		if ( stream_id[1] == 0xbd && stream_type[1] == 0x06  )
			stream_type[1] = 0x81;

		bytes = BuildPsmPacket( pPSBuilder, stream_id, stream_type, stream_num );
	}

	return PACKETSIZE + bytes;
}
*/

