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
#ifndef _CHANNEL_SCAN_H_
#define _CHANNEL_SCAN_H_
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

#define SCAN_MAX_CHECK_BYTES   (32*1024*1024)
#define MAX_SCAN_PROGRAM_NUM  100

typedef struct TUNE_DAT
{
	uint16_t nid;
	uint16_t onid;
	uint16_t tsid;
	union {
		TERRESTRIAL_TUNE t;
		CABLE_TUNE		 c;
		SATELLITE_TUNE   s;
	} u;
} TUNE_DAT;


typedef struct CHANNEL_DAT
{
	uint8_t  state;  //state=3 ready
	uint8_t  reserve;//
	union {
		ATSC_CHANNEL   atsc;
		DVB_CHANNEL	   dvb;
	} u;
} CHANNEL_DAT;


typedef struct PROGRAM_DAT
{
	uint8_t  service;  
	uint8_t  channel;
	uint16_t tsid;
	uint16_t program_id;
} PROGRAM_DAT;

typedef struct TUNE_LIST
{
	uint16_t total_list_num;
	uint16_t tune_num;
	uint8_t  stream_format;  //ATSC, DVB
	uint8_t  sub_format;     //T,C,S, 
	TUNE_DAT *tune;
} TUNE_LIST;

typedef struct CHANNEL_LIST
{
	uint16_t total_list_num;
	uint16_t channel_num;
	uint8_t  stream_format;  //ATSC, DVB
	uint8_t  sub_format;     //T,C,S, 
	CHANNEL_DAT    *channel;
} CHANNEL_LIST;

typedef struct PROGRAM_LIST
{
	uint16_t total_list_num;
	uint16_t program_num;
	uint16_t flag;
	uint16_t drop_program_num;
	uint8_t  stream_format;  //ATSC, DVB
	uint8_t  sub_format;     //T,C,S, 
	PROGRAM_DAT    *program;
} PROGRAM_LIST;


#define PSI_SCAN	1
#define NAKED_SCAN  2
#define DONE_SCAN   3

#define SCAN_COMMAND_ZERO    1

typedef struct SCAN
{
	uint16_t task; //1:psi channel scan; 2:naked channel scan
	uint16_t command;
	struct DEMUXER *demuxer;

	uint8_t  stream_format;  //ATSC, DVB
	uint8_t  sub_format;     //T,C,S, QAM 

	uint16_t state;
	CHANNEL_LIST   channel_list;
	TUNE_LIST      tune_list;
	PROGRAM_LIST   program_list;

	uint16_t local_nid;
	uint16_t local_onid;
	uint16_t local_tsid;
	uint16_t selected_tune_index;
	TUNE tune;

	uint32_t  maxium_psi_checking_bytes;
	uint32_t  psi_timeout;

	uint32_t  parse_bytes;
	uint32_t  last_parser_bytes;
	uint32_t  time_elapse;
	uint32_t  last_time_clock;

	uint16_t naked_channel_found;
	uint16_t pmt_dump_enabled;

	uint32_t  pat_counter;
	uint32_t  pmt_counter;
	uint32_t  psi_counter;
	uint32_t  nit_counter;
	int demuxer_myown_flag;
	int fd;
} SCAN;



SCAN *CreateChannelScan( void* pDemuxer, TUNE *pTune );
void ReleaseChannelScan( SCAN* pScan );
void ResetChannelScan( SCAN* pScan );
void ResetChannelScanList( SCAN* pScan ); //clean up channel scan result
void ResetTuneList( SCAN* pScan );
void DoChannelScan( SCAN* pScan, int nTask );
void ChannelScanTune( SCAN* pScan, TUNE *pTune );
struct DEMUXER* GetChannelScanDemuxer( SCAN* pScan );
int  GetChannelNum( SCAN *pScan );
void EnableProgramList( SCAN *pScan );
void QueueChannelScanZero( SCAN *pScan );
//return -1:no data incoming; 0: inprogress; 1: timeout, not channel found; 2: PSI channels info ready; naked channels found;
int  IsChannelInfoReady( SCAN* pScan ); 
int  ChannelInfoState( SCAN* pScan );
int  ChannelInfoChannelNum( SCAN* pScan );
int  IsNakedStream( SCAN* pScan );
int  UpdateTimeClock( SCAN* pScan, uint32_t lMillionSecond );

int	 PushScanStreamData( SCAN* pScan, uint8_t *pData, int nBytes, int *nExpectedBytes );

int  MergeChannelListProgramList(  SCAN *pScan );
int  GuessChannelInfSize( CHANNEL_LIST *pChannelList );
int  GuessTuneInfSize( CHANNEL_LIST *pChannelList );
int  GuessNitInfSize( TUNE_LIST *pTuneList );
int  GuessProgramInfSize( PROGRAM_LIST *pProgramList );
int  MakeChannelInf( CHANNEL_LIST *pChannelList, char* pInfBuf, int nInfBufSize );
int  MakeTuneInf( CHANNEL_LIST *pChannelList, char* pInfBuf, int nInfBufSize );
int  MakeNitInf( TUNE_LIST *pTuneList, char* pInfBuf, int nInfBufSize );
int  MakeProgramInf( PROGRAM_LIST *pProgramList, SCAN* pScan, char* pInfBuf, int nInfBufSize );
int ChannelInfoReadyNum( SCAN* pScan );

CHANNEL_LIST* GetChannelList( SCAN *pScan );
TUNE_LIST*    GetTuneList( SCAN *pScan );
PROGRAM_LIST* GetProgramList( SCAN *pScan );
int  GetStreamFormat( SCAN *pScan, uint16_t* pStreamFormat, uint16_t *pSubFormat );

CHANNEL_LIST* DupChannelList( CHANNEL_LIST *pChannelList );
void ReleaseChannelList( CHANNEL_LIST *pChannelList );
TUNE_LIST*    DupTuneList( TUNE_LIST *pTuneList );
void ReleaseTuneList( TUNE_LIST *pTuneList );

uint16_t GetTracksAttr( SCAN*  pScan );


#ifdef __cplusplus
 }
#endif


#endif

