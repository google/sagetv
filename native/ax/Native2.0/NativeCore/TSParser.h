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
#ifndef TSPARSER_H
#define TSPARSER_H

#include "SectionData.h"
#include "ESAnalyzer.h"
#include "AVAnalyzer.h"

#ifdef __cplusplus
extern "C" {
#endif

#define	 CHANNEL_LOCKED		0x0001
#define	 PARSING_INFO		0x0002
#define  PUMPOUT_DATA		0x0004
#define	 PAT_PRESENT		0x0010
#define  PMT_PRESENT		0x0020
#define  TS_FILTER_READY	0x0080
#define  AV_DATA_READY		0x0100
#define  NO_AV_DATA			0x0200
#define	 FAILED_CHANNEL	    0x1000
#define	 ENCRYPTED_CHANNEL  0x2000

#define TS_PARSER_CMD_ABORT	 0x0001
#define TS_PARSER_CMD_ZERO	 0x0002
#define TS_PARSER_CMD_RESET	 0x0004

#define CTRL_LOCK_PMT		 0x0001

#define PTS_FIX_THRESHOLD			5000				//5 second
#define EMPTY_SUB_STREAM_THRESHOLD  1000				//200 ES Blocks
#define FAILED_CHANNEL_THRESHOLD    (100*1024*1024/188)  //32M bytes
#define NAKED_STREAM_THRESHOLD      (24*1024*1024/188)   //24M bytes
#define MAX_STREAM_CHECK_TIME		5                    //seconds

#define MEDIA_TYPE_TV		  0x10
#define MEDIA_TYPE_DVD		  0x11

typedef struct TS_PARSER_DUMPER
{
	DUMP  esblock_dumper;
	void* esblock_dumper_context;

	DUMP  message_dumper;
	void* message_dumper_context;

	DUMP  pat_dumper;
	void* pat_dumper_context;
	
	DUMP  pmt_dumper;
	void* pmt_dumper_context;

	DUMP  pcr_dumper;
	void* pcr_dumper_context;

	DUMP  pes_dumper;
	void* pes_dumper_context;

	DUMP  channel_dumper;
	void* channel_dumper_context;

	DUMP  tune_dumper;
	void* tune_dumper_context;

	DUMP  program_dumper;
	void* program_dumper_context;

} TS_PARSER_DUMPER;

typedef struct TIME_STAMP
{
	ULONGLONG scr_offset;
	ULONGLONG ts_offset;
	ULONGLONG count_offset;
	ULONGLONG last_scr;
	ULONGLONG last_ts;
	ULONGLONG last_count;
} TIME_STAMP;

typedef struct SLOT
{
	uint16_t tracks_id;
	uint16_t state;
	uint16_t ctrl;       //0: nono;   1: lock channel (CTRL_LOCK_PMT)

	TUNE tune;
	struct TRACKS* tracks;
	uint16_t num_of_channels;

	LONGLONG pcr;
	LONGLONG pcr_start;
	ULONGLONG pcr_cue; //for interpolating scr
	uint32_t pcr_rate;
	LONGLONG pts_adjust;

	TIME_STAMP time_stamp; //for PTS-FIX obsolute

	TS_STREAMS ts_streams;
	ES_STREAMS es_streams;
	AV_STREAMS av_streams;

} SLOT;

typedef struct TSID_TBL
{
	uint16_t locked;
	uint16_t tsid;
	uint16_t total_program;
} TSID_TBL;


typedef struct TS_PARSER 
{
	uint32_t status;    //PARSER_RUNNING PARSER_STOP
	uint32_t state;     //TS_FILTER_READY, PARSING_INFO...
	uint32_t command;   //stop, reset  (used in pushdata)

	uint16_t stream_format;    //ATSC_STREAM, DVB_STREAM
	uint16_t stream_subformat; //TERRESTRIAL, CABLE, SATELLITE
	uint16_t media_type;		  //MEDIA_TYPE_TV, MEDIA_TYPE_DVD, MEDIA_TYPE_BLUERAY
	uint16_t packet_length;    //188 bytes TS, 192 bytes M2TS, 208 bytes ASI
	uint32_t  psi_time;
	uint32_t  clock_time;       //1 msec, feeding in form api


	uint32_t default_language;
	uint32_t epg_ctrl;          //0:disable; 1:channel epg; 3:group epg; 7:all epg 

	struct TS_FILTER *ts_filter;

	uint16_t tsid_num;
	uint16_t current_service_tsid;

	TS_PARSER_DUMPER dumper;

	uint16_t slot_num;
	SLOT  slot[MAX_SLOT_NUM];

	ULONGLONG  used_bytes;
	uint32_t input_packets;
	uint32_t valid_pcakets;
	uint32_t bad_packets;
	uint32_t bad_blocks;
	uint32_t block_count;
	uint32_t pmt_count;
	uint32_t psi_count;
	uint32_t psi_time_start;
	uint32_t clock_time_start;

	uint16_t first_channel;   //used for picking up the first valid channel has active packets(ripping recording)

	uint16_t subtitle_ctrl;
	uint16_t pts_fix_ctrl;
	LONGLONG  pts_fix_threshold;
	LONGLONG  pts_offset;
	uint16_t rebuild_stream_ctrl;
	uint32_t  naked_stream_threshold;
	uint32_t  empty_sub_stream_threshold;
	uint32_t  failed_channel_threshold;
	uint32_t  max_stream_check_time;

	uint16_t audio_ts_priority_hack; //use for TrueHD,DTS-HD, AC3ext, DTS hack
	uint16_t wait_clean_stream;      //waiting clean stream (a encrypted stream is clean)

} TS_PARSER;


TS_PARSER* CreateTSParser( int nStreamType );
void ReleaseTSParser( TS_PARSER *pTSParser );
void ResetTSParser( TS_PARSER *pTSParser );
void ResetTSParserState( TS_PARSER *pTSParser );
int  PushDataTSParser( TS_PARSER *pTSParser, uint8_t* pData, int nSize );
void ConsolidateTuneParam( TUNE *pTune, int nStreamFormat, int nSubFormat );

int  OpenTSChannel( TS_PARSER *pTSParser, int nSlot, TRACKS* pTracks, TUNE *pTune  );
void CloseTSChannel(  TS_PARSER *pTSParser, int nSlot );
void ResetTSChannel(  TS_PARSER *pTSParser, int nSlot );
void LockTSChannel(  TS_PARSER *pTSParser, int nSlot );
void SetupTune( TS_PARSER *pTSParser, int nSlot, TUNE* pTune );
void QueueTSParserAbort( TS_PARSER *pTSParser );
void QueueTSParserZero( TS_PARSER *pTSParser );
void QueueTSParserReset( TS_PARSER *pTSParser );
void ResetTSParserCommand( TS_PARSER *pTSParser );
void TSParserZero( TS_PARSER *pTSParser );
void SetupTSStreamType( TS_PARSER *pTSParser, int nStreamType );
void UpdateTSParserClock( TS_PARSER *pTSParser, uint32_t lClock );

int CheckTSFormat( const uint8_t* pData, int nBytes );
int CheckM2TFormat( const uint8_t* pData, int nBytes );
int CheckASIFormat( const uint8_t* pData, int nBytes );

//range (100-inf)
uint32_t PacketsSNRatio( TS_PARSER *pTSParser );

void DisablePSI( TS_PARSER *pTSParser );
void EnablePSI( TS_PARSER *pTSParser );
void ResetPSI( TS_PARSER *pTSParser );
void DisablePatPmt( TS_PARSER *pTSParser );
void EnablePatPmt( TS_PARSER *pTSParser );
void EnableRebuildStream( TS_PARSER *pTSParser );
void DisableRebuildStream( TS_PARSER *pTSParser );
void SetupStreamFormat( TS_PARSER *pTSParser, int nFormat, int nSubFormat );
void SetupTune( TS_PARSER *pTSParser, int nSlot, TUNE* pTune );
void SetupTSEPGDump( TS_PARSER *pTSParser, DUMP pfnEPGDump, void* pEPGDumpContext );
void SetupTSEPGDumpLanguage( TS_PARSER *pTSParser, uint32_t lLauguageCode );
ULONGLONG TSDataUsedBytes( TS_PARSER *pTSParser );

void AudioTSPriorityHackEnable( TS_PARSER *pTSParser );
void WaitCleanStream( TS_PARSER *pTSParser );
uint32_t GetTSParserState( TS_PARSER *pTSParser );

void SetupATSDump( TS_PARSER *pTSParser, DUMP pfnATSDump, void* pATSDumpContext );

#ifdef __cplusplus
}
#endif


#endif

