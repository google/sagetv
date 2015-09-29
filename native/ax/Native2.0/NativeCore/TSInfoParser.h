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
#ifndef TS_INFO_PARSER_H
#define TS_INFO_PARSER_H

#include "TSParser.h"

#ifdef __cplusplus
extern "C" {
#endif


#define PROGRAM_FOUND		0x01
#define TSID_LOCKED			0x02
#define CHANNEL_FOUND		0x04
#define CHANNEL_INFO_FOUND	0x08
#define AVINF_READY			0x10

	// |char verison|short P pid| char 0xff|short tsid|char 0xff|short V pid|char type|short A pid|char type|
#define NULL_PACKET_TAG_OFFSET (188-24)
#define NULL_PACKET_TAG_VER 	(NULL_PACKET_TAG_OFFSET+0)
#define NULL_PACKET_TAG_P_PID 	(NULL_PACKET_TAG_OFFSET+1)
#define NULL_PACKET_TAG_TSID 	(NULL_PACKET_TAG_OFFSET+4)
#define NULL_PACKET_TAG_PROG 	(NULL_PACKET_TAG_OFFSET+7)
#define NULL_PACKET_TAG_V_PID 	(NULL_PACKET_TAG_OFFSET+10)
#define NULL_PACKET_TAG_V_TYPE 	(NULL_PACKET_TAG_OFFSET+12)
#define NULL_PACKET_TAG_A_PID 	(NULL_PACKET_TAG_OFFSET+13)
#define NULL_PACKET_TAG_A_TYPE 	(NULL_PACKET_TAG_OFFSET+15)
#define TAG_VERIOSN	0x01
typedef struct PVR_META_INF
{
	uint8_t state;
	uint8_t ver;
	uint16_t program_pid;
	uint16_t program_num;
	uint16_t tsid;
	uint16_t video_pid;
	uint16_t audio_pid;
	uint8_t  video_type;
	uint8_t  audio_type;
} PVR_META_INF;

typedef struct TS_INFO_PARSER_DUMPER
{
	DUMP  pid_dumper;
	void* pid_dumper_context;

	DUMP  pcr_dumper;
	void* pcr_dumper_context;

	DUMP  avinf_dumper;
	void* avinf_dumper_context;

	DUMP  stream_dumper;
	void* stream_dumper_context;

	DUMP  time_dumper;
	void* time_dumper_context;

} TS_INFO_PARSER_DUMPER;

#define TMP_ES_BUFFER_SIZE (4*1024)


typedef struct ES_BUFFER
{
	uint16_t stream_id;
	PES pes;
	int32_t buffer_size;
	uint8_t* buffer;
	int32_t bytes;
	int32_t main_track;
	int32_t input_bytes;
} ES_BUFFER;


typedef struct TS_INFO_PARSER
{
	uint32_t state;
	TS_FILTER* ts_filter;
	TS_INFO_PARSER_DUMPER dumper;
	TUNE tune;

	int32_t max_stream_num;
	TS_STREAMS ts_streams;
	ES_STREAMS es_streams;
	AV_STREAMS av_streams;
	ES_BUFFER* es_buffer;

	uint32_t utc_sec;
	uint32_t utc_sec_start;
	uint32_t pcr_sec;
	uint32_t pcr_sec_start;
		
	char avinf[1024*2];
	int32_t  avinf_size;

	uint32_t total_bytes;
	uint32_t max_check_limit;
	int16_t total_video_num;
	int16_t total_audio_num;
	int16_t found_video_num;
	int16_t found_audio_num;
	uint32_t bit_rate;
	
} TS_INFO_PARSER;

TS_INFO_PARSER* CreateTSInfoParser( int nMaxStreamNum, int nStreamFormat, int nSubFormat );
void ReleaseTSInfoParser( TS_INFO_PARSER *pTSInfoParser );
void ResetTSInfoParser( TS_INFO_PARSER *pTSInfoParser );
void SetupTSInfDump( TS_INFO_PARSER *pTSInfoParser, 
					 DUMP pfnPidDump,     void* pfnPidDumpContext,
					 DUMP pfnPcrDump,     void* pfnPcrDumpContext,
					 DUMP pfnAVInfDump,   void* pfnAVInfDumpContext,
					 DUMP pfnStreamDump,  void* pStreamDumpContext, 
					 DUMP pfnTimeDump,    void* pTimeDumpContext );
int  StartTSInfoParser( TS_INFO_PARSER *pTSInfoParser, int nFlag );
void StopTSInfoParser( TS_INFO_PARSER *pTSInfoParser );
void SetupTSTune( TS_INFO_PARSER *pTSInfoParser, TUNE *pTune );
void DisableParseData( TS_INFO_PARSER *pTSInfoParser );
int  PushTSPacketParser( TS_INFO_PARSER *pTSInfoParser, uint8_t* pData, int nSize );
int  CheckTrackAVInfRead( TS_INFO_PARSER *pTSInfoParser );
int  GetAVInfo( TS_INFO_PARSER *pTSInfoParser, char* pBuffer, int nSize );
int  GetPVRMetaInf( PVR_META_INF* pMetaInf, uint8_t *pData );
int  SearchPVRMetaInf( int* pNextStart, uint8_t *pData, int nBytes );
int	 CheckSagePVRData( const uint8_t* pData, int nBytes );

#ifdef __cplusplus
}
#endif


#endif

