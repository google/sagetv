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
#ifndef PSBUILDER_H
#define PSBUILDER_H

#include "SectionData.h"
#include "AVAnalyzer.h"
#include "BlockBuffer.h"
#include "Bits.h"

#ifdef __cplusplus
extern "C" {
#endif

#define ES_PACKET_SIZE	(2*1024)

typedef struct PS_BUILDER_DUMPER
{
	DUMP  stream_dumper;
	void* stream_dumper_context;

	//DUMP  message_dumper;
	//void* message_dumper_context;

} PS_BUILDER_DUMPER;

#define BUILD_HEADER_IN_BUFFER		0x0001 
#define CONTINUE_DATA_IN_BUFFER		0x0002

typedef struct PS_BUILDER
{
	uint16_t state;

	PS_BUILDER_DUMPER dumper;

	uint8_t video_stream_id;
	uint8_t audio_stream_id;
	uint8_t video_stream_type;
	uint8_t audio_stream_type;
	uint32_t video_demux_rate;
	uint32_t audio_demux_rate;
	uint32_t video_block_size;
	uint32_t audio_block_size;
	uint32_t demux_bound;

	ULONGLONG scr;
	uint32_t demux;

	struct TRACKS *tracks;
	
	TS_STREAMS ts_streams;
	ES_STREAMS es_streams;
	AV_STREAMS av_streams;
	
	PSM_LIST psm_list;

	uint16_t build_ctrl;	
	//uint16_t build_header_in_buffer;
	uint16_t es_block_size;
	uint8_t* block_buffer;
	uint16_t buffer_size;
	uint16_t pading_block_enable;
	uint16_t system_packet_bytes;

	ULONGLONG  used_bytes;
	ULONGLONG  output_bytes;
	uint32_t input_blocks;
	uint32_t output_blocks;
	uint32_t out_of_order_blocks;
	
	SAGETV_PRIVATE_DATA sagetv_private_data;
	int32_t h264_frame_rate;
	BITS_T bits;
	uint8_t* cur_data_ptr;


} PS_BUILDER;

int BlockBufferPSDump( void* pContext, void* pData, int nSize );
PS_BUILDER* CreatePSBuilder( TRACKS* pTracks, int nMaxBufferSize, int bBuildHeaderInBuffer );
void ReleasePSBuilder( PS_BUILDER *pPSBuilder );
void ResetPSBuilder( PS_BUILDER *pPSBuilder );
int PSBulderPushDataInSafe( PS_BUILDER *pPSBuilder, int nTrackIndex, int bGroup, uint8_t* pData, int nSize );
int PSBulderPushDataInBuffer( PS_BUILDER *pPSBuilder, int nTrackIndex, int bGroup, struct BLOCK_BUFFER* pBlockBuffer );
int SetupTracks( TRACK *pTrack );
int CreatSageStreamHeader( PS_BUILDER* pPSBuilder );
int FlushEndOfCode( PS_BUILDER *pPSBuilder );
void DisablePadingPacket( PS_BUILDER *pPSBuilder ); 

int CreatePES2Header( uint8_t*pBuffer, int nMaxBufferSize, PES *pPES );

#define PACK_HEADER_BYTES 14

static inline int PES2HeaderBytes( int bHasPts, int bHasDts )
{
	return 6+3+(bHasPts? 5:0)+( (bHasPts && bHasDts) ? 5:0 );
}

static inline int BlockHeaderBytes( int bHasPts, int bHasDts )
{
	return PACK_HEADER_BYTES+6+3+(bHasPts? 5:0)+(bHasDts ? 5:0);
}

static inline int BlockTotalBytes( int bHasPts, int bHasDts, int nDataSize )
{
	return PACK_HEADER_BYTES+6+3+(bHasPts? 5:0)+(bHasDts ? 5:0)+nDataSize;
}


char* _pes_header_( uint8_t* p );

#ifdef __cplusplus
}
#endif


#endif
