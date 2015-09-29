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
#ifndef TSBUILDER_H
#define TSBUILDER_H

#include "SectionData.h"
#include "ESAnalyzer.h"
#include "AVAnalyzer.h"
#include "TSFilter.h"
#include "Bits.h"

//#define MAX_PROGRAM_NUM  4

#define	TS_PACKET_LENGTH    188
#define	M2TS_PACKET_LENGTH  192
#define	ASI_PACKET_LENGTH   208

#ifdef __cplusplus
extern "C" {
#endif


typedef struct TS_BUILDER_DUMPER
{
	DUMP  stream_dumper;
	void* stream_dumper_context;

	//DUMP  message_dumper;
	//void* message_dumper_context;
} TS_BUILDER_DUMPER;



typedef struct TS_PROGRAM
{
	struct TRACKS *tracks;

	TS_STREAMS ts_streams;
	ES_STREAMS es_streams;
	AV_STREAMS av_streams;

	TS_PACKET  *ts_packets;

	//PMT
	TS_SECTION *pmt_section;
	uint16_t pmt_program;
	TS_PACKET      pmt_packet;

	ULONGLONG pcr;
	ULONGLONG last_pcr;
	uint16_t pcr_pid;

} TS_PROGRAM;

typedef struct TS_BUILDER
{
	//unsigned  short status;
	uint16_t state;

	TS_BUILDER_DUMPER dumper;

	uint16_t packet_length;

	TS_SECTION*  pat_section;
	TS_PACKET    pat_packet;

	TS_PROGRAM*  progams[MAX_PROGRAM_NUM];

	DATA_BUFFER    *output_buffer;  //a buffer holds packets
	uint8_t  *local_data_buffer;       
	uint16_t  local_data_size;
	MEM_ALLOC_HOOK pfn_mem_alloc_hook;
	void* pfn_mem_alloc_hook_context;
	uint16_t  block_data_size;

	OUTPUT_DATA  output_data;       //dump data
	ULONGLONG  used_bytes;
	ULONGLONG  output_bytes;
	uint32_t output_packets;
	uint32_t input_blocks;
	uint32_t output_blocks;

} TS_BUILDER;

int BlockBufferTSDump( void* pContext, void* pData, int nSize );
TS_BUILDER* CreateTSBuilder( TRACKS* pTracks, int nTSFormat );
void UpdatePMT( TS_BUILDER *pTSBuilder, int nProgramIndex );
void ReleaseTSBuilder( TS_BUILDER *pTSBuilder );
void ResetTSBuilder( TS_BUILDER *pTSBuilder );
void SetupBlockDataSize( TS_BUILDER *pTSBuilder, int nSize );
int  PushTSBlockData( TS_BUILDER *pTSBuilder, int nProgramIndex, int nTrackIndex, int GroupFlag, uint8_t* pData, int nBytes );
void FlushOutData( TS_BUILDER *pTSBuilder );
void SetupTSBuilderAllocMemoryHook( TS_BUILDER *pTSBuilder, MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext );

uint8_t LookupStreamType( uint32_t lFourCC );
int     PutDescriptor( uint8_t *pData, uint8_t Tag, uint8_t *pDesc, int nDescLength );
uint8_t *AppendDescriptor( uint8_t *pData, int nBytes, uint8_t Tag, uint8_t *pDesc, int nDescLength );

char* _pes_header_( uint8_t* p );

#ifdef __cplusplus
}
#endif

#endif
