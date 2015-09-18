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
#ifndef TS_CHANNEL_PARSER_H
#define TS_CHANNEL_PARSER_H

#include "TSParser.h"

#ifdef __cplusplus
extern "C" {
#endif


#define PROGRAM_FOUND		0x01
#define TSID_LOCKED			0x02
#define CHANNEL_FOUND		0x04
#define CHANNEL_INFO_FOUND	0x08
#define AVINF_READY			0x10
#define CHANNEL_INFO_READY	0x20

#define MAX_ATSC_CHANNEL_NUM   20
#define MAX_QAM_CHANNEL_NUM   40
typedef struct TS_CHANNEL_PARSER_DUMPER
{
	DUMP  pid_dumper;
	void* pid_dumper_context;

	DUMP  avinf_dumper;
	void* avinf_dumper_context;

	DUMP  stream_dumper;
	void* stream_dumper_context;

} TS_CHANNEL_PARSER_DUMPER;


#define TMP_ES_BUFFER_SIZE (4*1024)
typedef struct ES_BUFFER_
{
	uint16_t stream_id;
	PES pes;
	int32_t buffer_size;
	uint8_t* buffer;
	int32_t bytes;
	int32_t main_track;
	int32_t input_bytes;
} ES_BUFFER_;

typedef struct TS_CHANNEL_PARSER
{
	uint32_t state;
	TS_FILTER* ts_filter;
	TS_CHANNEL_PARSER_DUMPER dumper;
	TUNE tune;

	int max_stream_num;
	TS_STREAMS ts_streams;
	ES_STREAMS es_streams;
	AV_STREAMS av_streams;
	ES_BUFFER_* es_buffer;

	char avinf[1024];
	char avinf_size;

	int32_t stream_format;
	int32_t sub_format;
	CHANNEL_LIST   channel_list;
	TUNE_LIST      tune_list;
	PROGRAM_LIST   program_list;

	uint32_t total_bytes;
	uint32_t max_check_limit;
	int16_t total_video_num;
	int16_t total_audio_num;
	int16_t found_video_num;
	int16_t found_audio_num;
	uint32_t bit_rate;
	
} TS_CHANNEL_PARSER;

TS_CHANNEL_PARSER* CreateTSChannelParser( int nMaxStreamNum, int nStreamFormat, int nSubFormat );
void ReleaseTSChannelParser( TS_CHANNEL_PARSER *pTSChannelParser );
void ResetTSChannelParser( TS_CHANNEL_PARSER *pTSChannelParser );
void SetupTSChannelDump( TS_CHANNEL_PARSER *pTSChannelParser, 
					 DUMP pfnPidDump,     void* pfnPidDumpContext,
					 DUMP pfnAVInfDump,   void* pfnAVInfDumpContext,
					 DUMP pfnStreamDump,  void* pStreamDumpContext );
int  StartTSChannelParser( TS_CHANNEL_PARSER *pTSChannelParser, TUNE* pTune );
void StopTSChannelParser( TS_CHANNEL_PARSER *pTSChannelParser );
void SetupTSChannelTune( TS_CHANNEL_PARSER *pTSChannelParser, TUNE *pTune );
int PushTSChannelPacketParser( TS_CHANNEL_PARSER *pTSChannelParser, uint8_t* pData, int nSize );
int CheckChannelTrackAVInfRead( TS_CHANNEL_PARSER *pTSChannelParser );
int GetChannelAVInf( TS_CHANNEL_PARSER *pTSChannelParser, char* pBuffer, int nSize );
int IsTSChannelInfoReady( TS_CHANNEL_PARSER *pTSChannelParser );
int GetTSChannelScanState( TS_CHANNEL_PARSER *pTSChannelParser );
int TSChannelScanNum( TS_CHANNEL_PARSER *pTSChannelParser );
CHANNEL_LIST* GetTSChannelScanList( TS_CHANNEL_PARSER *pTSChannelParser );
int GetTSProgramScanNum( TS_CHANNEL_PARSER *pTSChannelParser );
int MergeTSChannelListProgramList( TS_CHANNEL_PARSER *pTSChannelParser );
TUNE_LIST *GetTSTuneScanList( TS_CHANNEL_PARSER *pTSChannelParser );

#ifdef __cplusplus
}
#endif


#endif

