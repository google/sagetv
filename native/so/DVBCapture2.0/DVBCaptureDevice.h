/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


#include "Channel.h"

#define DEBUGDVB 1


#define EPG_MSG_TYPE		10
#define AV_INF_TYPE		11

#define strnicmp  strncasecmp
#define stricmp   strcasecmp
#define MAX_STREAMS	16
#define ALL_PIDS 0x2000

//typedef int (*OUTPUT_DUMP)( void* context, unsigned char* pData, unsigned long lBytes );
typedef struct {
	char  raw_dump_path[256];
	unsigned long dump_size;
	unsigned long dumped_bytes;
	char  debug_source[256];
	int   debug_source_mode;
	FILE* dump_fd;
	FILE* source_fd;
	unsigned long audio_ctrl;
} DBG;

typedef struct lnb_types_st {
	char	 name[16];
	unsigned long	low_val;
	unsigned long	high_val;	
	unsigned long	switch_val;	
} LNB;


typedef struct DVBCaptureDev
{
	char tag[4];               //check data is valid that come back from Java
	char tunerDeviceName[64];
	int  frontendFd;
	int  dvrFd; 
	//int  demuxFd; 
	int  demuxDevs[MAX_STREAMS];
	FILE* fd; // file to write the captured data to
	long circFileSize;
	char devName[256];
	char frontendName[256];
	char demuxName[256];
	char dvrName[256];
	unsigned char buf[BUFFERSIZE];
	long circWritePos;
	
	int hard_demulex;
	unsigned short filterPids[MAX_STREAMS];

	CHANNEL_DATA channel;
	LNB	lnb;	          //DVB-S lnb swicth control
	int	sat_no;
	
	int outputFormat;
	int parserEnabled;
	struct TUNE	tune;
	struct REMUXER *remuxer;
	int  paserEnabled;

	SCAN_FILTER *scanFilter;
	int  scanChannelEnabled;

	unsigned long long  totalOutBytes;
	unsigned long long  totalProcessedBytes;
	unsigned long long  totalInputBytes;
	unsigned long long  lastOutputBytes;
	unsigned long 		dumpBytes;
    unsigned long long  syncBytes;
	unsigned long startTime;   //start time in ms
	unsigned char outBuffer[ TS_BUFFER_PACKETS*TS_PACKET_LENGTH ];
	unsigned char alignBuffer[2048];
	int	alignBytes;
	int expectedBytes;
	DBG dbg;
	unsigned long dmx_buffer_size;

	pthread_mutex_t mutex1_scan_session;
	pthread_mutex_t mutex1_scan_data;
	pthread_mutex_t mutex1_push_data;

    circBuffer capBuffer;
    ACL_mutex *capMutex;
    int capState; // 0: normal 1: discarding 2: exit
    ACL_Thread *capThread;
    int discardCount;
    unsigned char buf2[BUFFERSIZE];

#ifdef FILETRANSITION
	FILE* newfd; // next file to write the captured data to
	int bytesTested; // We want to give up after some fixed amount of bytes if no transition found
	int detectmode; // 0: unknown 1: mpeg 2 PS  2: mpeg 2 TS  3: unsupported
	int detecttype; // 0: unknown, 1: mpeg1  2:mpeg2  3:H264
	int detectid; // stream ID of detect (should be 0xE0 for now)
#endif
} DVBCaptureDev;

typedef struct {
	DVBCaptureDev *CDev;
} DVB_ENV;

//export API
int SageTuneATSCChannel( void* Capture, ATSC_FREQ* atsc, int dryTune );
int SageTuneQAMFrequency( void* Capture, QAM_FREQ* qam , int dryTune);
int SageTuneDVBTFrequency( void* Capture, DVB_T_FREQ* dvbt , int dryTune);
int SageTuneDVBCFrequency( void* Capture, DVB_C_FREQ* dvbc , int dryTune);
int SageTuneDVBSFrequency( void* Capture, DVB_S_FREQ* dvbs , int dryTune);
int SageCheckLocked( void* Capture );
int SageSelectTSChannel( void* Capture, int channel, bool reset );
int SageSelectTSProgram( void* Capture, int program, bool reset );
int SageSelectOuputFormat( void* Capture, int format ); //0:TS; 1:PS
int SageResetSIParser( void* Capture );
int SageGetVideoChannelNum( void* Capture );
int SageGetValidChannelNum( void* Capture );
int SagePacketInputNum( void* Capture );
int SageGetChannelName( void* , void * ChannelName, int BufSize );
int SageIsScrambleTSdProgram( void* Capture, unsigned short channel );
int SageIsVideoStreamReady( void* Capture );
int SageIsAudioStreamReady( void* Capture );
int SageGetProgramState( void* Capture, unsigned short programNum  );
int SageGetVideoProgramList( void* Capture, unsigned short* program_list, int list_size );
char* SageGetTunerDeviceName( void* Capture );
