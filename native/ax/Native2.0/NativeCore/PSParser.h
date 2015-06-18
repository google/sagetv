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
#ifndef PSPARSER_H
#define PSPARSER_H

#include "SectionData.h"
#include "ESAnalyzer.h"
#include "AVAnalyzer.h"

#define MAX_PES_STREAM  16


#define	 PARSING_INFO		0x0002
#define  PUMPOUT_DATA		0x0004

#define PS_PARSER_CMD_ABORT	 0x0001
#define PS_PARSER_CMD_ZERO	 0x0002
#define PS_PARSER_CMD_RESET	 0x0004

#ifdef __cplusplus
extern "C" {
#endif


typedef struct PS_PARSER_DUMPER
{
	DUMP  esblock_dumper;
	void* esblock_dumper_context;

	DUMP  message_dumper;
	void* message_dumper_context;

	DUMP  pes_dumper;
	void* pes_dumper_context;

	DUMP  scr_dumper;
	void* scr_dumper_context;

} PS_PARSER_DUMPER;

typedef struct SAGETV_PRIVATE_DATA
{
	int	video_track_num;
	int audio_track_num;
	int video_main_track;
	int audio_main_track;
	unsigned char main_video_stream_type;
	unsigned char main_audio_stream_type;
	unsigned char h264_frame_rate;
	unsigned char padding;
	int info_version;
	int sage_info_embedded;
} SAGETV_PRIVATE_DATA;

typedef struct PSM
{
	unsigned char  stream_id;
	unsigned char  padding;
	unsigned short stream_type;
	unsigned char  stream_info[32];
} PSM;

typedef struct PSM_LIST
{
	unsigned char  psm_info[32];
	unsigned short psm_num;
	unsigned short psm_total_num;
	PSM *psm;
} PSM_LIST;



typedef struct PS_PARSER 
{
	//unsigned  short status;
	unsigned short state;
	unsigned short command;

	PS_PARSER_DUMPER dumper;

	unsigned char video_stream_id;
	unsigned char audio_stream_id;
	unsigned char video_stream_type;
	unsigned char audio_stream_type;
	unsigned long video_demux_rate;
	unsigned long audio_demux_rate;
	unsigned long video_block_size;
	unsigned long audio_block_size;
	unsigned long demux_bound;
	
	struct TRACKS *tracks;
	ULONGLONG scr;
	ULONGLONG scr_cue;
	
	TS_STREAMS ts_streams;
	ES_STREAMS es_streams;
	AV_STREAMS av_streams;
	
	PSM_LIST psm_list;
	unsigned long empty_sub_stream_threshold;
	ULONGLONG  used_bytes;
	unsigned long input_blocks;
	unsigned long bad_blocks;
	unsigned long block_count;
	LONGLONG  pts_offset;

	unsigned short pack_type;  //curent pack_type;
	SAGETV_PRIVATE_DATA sagetv_private_data;

} PS_PARSER;


PS_PARSER* CreatePSParser( TRACKS* pTracks );
void ReleasePSParser( PS_PARSER *pTSParser );
void ResetPSParser( PS_PARSER *pTSParser );
int  PushDataPSParser( PS_PARSER *pPSParser, const unsigned char* pData, int nSize, int *pExpectedBytes );

int CheckPSFormat( const unsigned char* pData, int nSize);
ULONGLONG PSDataUsedBytes( PS_PARSER *pPSParser );
unsigned long PSStreamBoundRate( PS_PARSER* pParser );

void QueuePSParserReset( PS_PARSER *pPSParser );
void QueuePSParserAbort( PS_PARSER *pPSParser );
void QueuePSParserZero( PS_PARSER *pPSParser );
void ResetPSParserCommand( PS_PARSER *pPSParser );
void PSParserZero( PS_PARSER *pPSParser );

int PickupMainTrack( PS_PARSER *pPSParser, TRACKS* pTracks );

char* _pes_header_( unsigned char* p );


#ifdef __cplusplus
}
#endif


#endif
