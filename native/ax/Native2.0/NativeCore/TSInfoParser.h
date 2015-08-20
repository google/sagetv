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
	unsigned char state;
	unsigned char ver;
	unsigned short program_pid;
	unsigned short program_num;
	unsigned short tsid;
	unsigned short video_pid;
	unsigned short audio_pid;
	unsigned char  video_type;
	unsigned char  audio_type;
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
	unsigned short stream_id;
	PES pes;
	int buffer_size;
	unsigned char* buffer;
	int bytes;
	int main_track;
	int input_bytes;
} ES_BUFFER;


typedef struct TS_INFO_PARSER
{
	unsigned long state;
	TS_FILTER* ts_filter;
	TS_INFO_PARSER_DUMPER dumper;
	TUNE tune;

	int max_stream_num;
	TS_STREAMS ts_streams;
	ES_STREAMS es_streams;
	AV_STREAMS av_streams;
	ES_BUFFER* es_buffer;

	unsigned long utc_sec;
	unsigned long utc_sec_start;
	unsigned long pcr_sec;
	unsigned long pcr_sec_start;
		
	char avinf[1024*2];
	int  avinf_size;

	unsigned long total_bytes;
	unsigned long max_check_limit;
	short total_video_num;
	short total_audio_num;
	short found_video_num;
	short found_audio_num;
	unsigned long bit_rate;
	
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
int  PushTSPacketParser( TS_INFO_PARSER *pTSInfoParser, unsigned char* pData, int nSize );
int  CheckTrackAVInfRead( TS_INFO_PARSER *pTSInfoParser );
int  GetAVInfo( TS_INFO_PARSER *pTSInfoParser, char* pBuffer, int nSize );
int  GetPVRMetaInf( PVR_META_INF* pMetaInf, unsigned char *pData );
int  SearchPVRMetaInf( int* pNextStart, unsigned char *pData, int nBytes );
int	 CheckSagePVRData( const unsigned char* pData, int nBytes );

#ifdef __cplusplus
}
#endif


#endif

