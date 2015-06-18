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
	unsigned short nid;
	unsigned short onid;
	unsigned short tsid;
	union {
		TERRESTRIAL_TUNE t;
		CABLE_TUNE		 c;
		SATELLITE_TUNE   s;
	} u;
} TUNE_DAT;


typedef struct CHANNEL_DAT
{
	unsigned char  state;  //state=3 ready
	unsigned char  reserve;//
	union {
		ATSC_CHANNEL   atsc;
		DVB_CHANNEL	   dvb;
	} u;
} CHANNEL_DAT;


typedef struct PROGRAM_DAT
{
	unsigned char  service;  
	unsigned char  channel;
	unsigned short tsid;
	unsigned short program_id;
} PROGRAM_DAT;

typedef struct TUNE_LIST
{
	unsigned short total_list_num;
	unsigned short tune_num;
	unsigned char  stream_format;  //ATSC, DVB
	unsigned char  sub_format;     //T,C,S, 
	TUNE_DAT *tune;
} TUNE_LIST;

typedef struct CHANNEL_LIST
{
	unsigned short total_list_num;
	unsigned short channel_num;
	unsigned char  stream_format;  //ATSC, DVB
	unsigned char  sub_format;     //T,C,S, 
	CHANNEL_DAT    *channel;
} CHANNEL_LIST;

typedef struct PROGRAM_LIST
{
	unsigned short total_list_num;
	unsigned short program_num;
	unsigned short flag;
	unsigned short drop_program_num;
	unsigned char  stream_format;  //ATSC, DVB
	unsigned char  sub_format;     //T,C,S, 
	PROGRAM_DAT    *program;
} PROGRAM_LIST;


#define PSI_SCAN	1
#define NAKED_SCAN  2
#define DONE_SCAN   3

#define SCAN_COMMAND_ZERO    1

typedef struct SCAN
{
	unsigned short task; //1:psi channel scan; 2:naked channel scan
	unsigned short command;
	struct DEMUXER *demuxer;

	unsigned char  stream_format;  //ATSC, DVB
	unsigned char  sub_format;     //T,C,S, QAM 

	unsigned short state;
	CHANNEL_LIST   channel_list;
	TUNE_LIST      tune_list;
	PROGRAM_LIST   program_list;

	unsigned short local_nid;
	unsigned short local_onid;
	unsigned short local_tsid;
	unsigned short selected_tune_index;
	TUNE tune;

	unsigned long  maxium_psi_checking_bytes;
	unsigned long  psi_timeout;

	unsigned long  parse_bytes;
	unsigned long  last_parser_bytes;
	unsigned long  time_elapse;
	unsigned long  last_time_clock;

	unsigned short naked_channel_found;
	unsigned short pmt_dump_enabled;

	unsigned long  pat_counter;
	unsigned long  pmt_counter;
	unsigned long  psi_counter;
	unsigned long  nit_counter;
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
int  UpdateTimeClock( SCAN* pScan, unsigned long lMillionSecond );

int	 PushScanStreamData( SCAN* pScan, unsigned char *pData, int nBytes, int *nExpectedBytes );

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
int  GetStreamFormat( SCAN *pScan, unsigned short* pStreamFormat, unsigned short *pSubFormat );

CHANNEL_LIST* DupChannelList( CHANNEL_LIST *pChannelList );
void ReleaseChannelList( CHANNEL_LIST *pChannelList );
TUNE_LIST*    DupTuneList( TUNE_LIST *pTuneList );
void ReleaseTuneList( TUNE_LIST *pTuneList );

unsigned short GetTracksAttr( SCAN*  pScan );


#ifdef __cplusplus
 }
#endif


#endif

