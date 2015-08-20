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
#ifndef TS_PVR_INFO_PARSER_H
#define TS_PVR_INFO_PARSER_H

#include "TSParser.h"

#ifdef __cplusplus
extern "C" {
#endif


#define PROGRAM_FOUND		0x01
#define TSID_LOCKED			0x02
#define CHANNEL_FOUND		0x04
#define CHANNEL_INFO_FOUND	0x08
#define AVINF_READY			0x10

#define MAX_PID_HIST 512

typedef struct PVR_INFO_PARSER_DUMPER
{
	DUMP  pid_dumper;
	void* pid_dumper_context;

	DUMP  pcr_dumper;
	void* pcr_dumper_context;

	DUMP  avinf_dumper;
	void* avinf_dumper_context;

	DUMP  stream_dumper;
	void* stream_dumper_context;

	DUMP  index_dumper;
	void* index_dumper_context;

	DUMP  ats_dumper;
	void* ats_dumper_context;

	DUMP  new_pid_dumper;
	void* new_pid_dumper_context;

	DUMP  meta_dumper;
	void* meta_dumper_context;

	DUMP  error_dumper;
	void* error_dumper_context;

} PVR_INFO_PARSER_DUMPER;

#define TMP_ES_BUFFER_SIZE (4*1024)

typedef struct _ES_BUFFER
{
	unsigned short stream_id;
	PES pes;
	int buffer_size;
	unsigned char* buffer;
	int bytes;
	int main_track;
	int input_bytes;
} _ES_BUFFER;

typedef struct PID_HIS
{
	unsigned short pid;
	unsigned short dummy;
	unsigned long  counter;
} PID_HIS;

typedef struct PVR_INFO_PARSER
{
	unsigned long state;
	TS_FILTER* ts_filter;
	PVR_INFO_PARSER_DUMPER dumper;
	TUNE tune;

	int max_stream_num;
	TS_STREAMS ts_streams;
	ES_STREAMS es_streams;
	AV_STREAMS av_streams;
	_ES_BUFFER* es_buffer;

	char avinf[1024*2];
	int  avinf_size;
	
	unsigned long ts_packet_counter;
	unsigned long pcr_sec;

	unsigned long total_bytes;
	unsigned long max_check_limit;
	short total_video_num;
	short total_audio_num;
	short found_video_num;
	short found_audio_num;
	unsigned long bit_rate;
	
	//statistic
	PID_HIS pid_hist[MAX_PID_HIST];
	unsigned pid_hist_num;

} PVR_INFO_PARSER;

PVR_INFO_PARSER* CreatePVRTSParser( int nMaxStreamNum, int nStreamFormat, int nSubFormat );
void ReleasePVRTSParser( PVR_INFO_PARSER *pPVRInfoParser );
void ResetPVRTSParser( PVR_INFO_PARSER *pPVRInfoParser );
void SetupPVRTSDump( PVR_INFO_PARSER *pPVRInfoParser, 
					 DUMP pfnPidDump,     void* pfnPidDumpContext,
					 DUMP pfnPcrDump,     void* pfnPcrDumpContext,
					 DUMP pfnAVInfDump,   void* pfnAVInfDumpContext,
					 DUMP pfnStreamDump,  void* pStreamDumpContext, 
					 DUMP pfnAtsDump,	  void* pAtsDumpContext,
					 DUMP pfnNewPidDump,  void* pNewPidDumpContext,
					 DUMP pfnMetaDump,	  void* pMetaDumpContext,
					 DUMP pfnErrorDump,	  void* pErrorDumpContext );
int  StartPVRTSParser( PVR_INFO_PARSER *pPVRInfoParser, int nFlag );
void StopPVRTSParser( PVR_INFO_PARSER *pPVRInfoParser );
void SetupPVRTSTune( PVR_INFO_PARSER *pPVRInfoParser, TUNE *pTune );
int PushPVRTSPacketParser( PVR_INFO_PARSER *pPVRInfoParser, unsigned char* pData, int nSize );
int CheckPVRTrackAVInfRead( PVR_INFO_PARSER *pPVRInfoParser );
int GetPVRAVInf( PVR_INFO_PARSER *pPVRInfoParser, char* pBuffer, int nSize );

#ifdef __cplusplus
}
#endif


#endif

