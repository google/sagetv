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

#ifndef _TSSPLITTER_H_
#define _TSSPLITTER_H_

#define MAX_STREAM_NUM    24

#include "TSnative.h"
#include "TSParser.h"
#include "SIParser.h"
#include "TSBuilder.h"
#include "PSParser.h"
#include "PSBuilder.h"

#ifdef __cplusplus
extern "C" {
#endif

#define SPLT_RUNNING	1
#define SPLT_STOP		0

#define VIDEO_AUDIO_STREAM    3
#define VIDEO_ONLY_STREAM     1
#define AUDIO_ONLY_STREAM     2

#define VIDEO_PRESENT_MASK     1
#define AUDIO_PRESENT_MASK     2

typedef struct {
	LONGLONG	syncPTS;
	LONGLONG	syncDTS;
	LONGLONG	newPTS;	   //first Picture Time Stamp
	LONGLONG	lastPTS;   //last Picture Time Stamp
	LONGLONG	newDTS;	   //first Picture Time Stamp
	LONGLONG	lastDTS;   //last Picture Time Stamp
	unsigned long ticks; //million second.
} PTS_INF;


typedef long (*EPG_DUMP)( void* context, short bytes, void* mesg );
typedef long (*AV_INF_DUMP)( void* context, short bytes, void* mesg );
typedef long (*DATA_DUMP)( void* context, short bytes, void* mesg );
typedef long (*PID_DUMP)( void* context, short bytes, void* mesg );
typedef int  (*OUTPUT_DUMP)( void* context, unsigned char* pData, unsigned long lBytes );
typedef int  (*ALLOC_BUFFER)( void* conext, unsigned char** ppData, int cmd ); //cmd 0:alloc, 1:release

typedef struct  {

	TS_PARSER*  parser;
	SI_PARSER*  si_parser;
	TS_BUILDER* ts_builder;
	PS_BUILDER* ps_builder;

	int  output_select; //0 for TS; 1: for PS

	int  program_id;
	unsigned short  tsid; //transport service id;
	unsigned short  selected_channel;	
	unsigned short  selected_program;	
	int  builder_state;
	int  video_state;
	int  audio_state;
	int  channel_type;  //3:vido_audio_channel; 1:video_only_channel; 2:audio_only_channel
	unsigned long ts_packet_num;

	EPG_DUMP    pfnEPGDump;
	AV_INF_DUMP pfnAVInfDump;
	PID_DUMP    pfnPidTblDump;
	DATA_DUMP   pfnPMTDataDump;
	void*       EPGContext;
	void*	    AVInfContext;
	void*	    PidContext;
	void*	    PMTDataContext;
	unsigned long EPG_parser_ctrl;
	unsigned long avinfo_ctrl;

	AV_CONTEXT     av[MAX_STREAM_NUM];
	unsigned short stream_type[MAX_STREAM_NUM];
	unsigned char  stream_id[MAX_STREAM_NUM];
	unsigned short stream_pid[MAX_STREAM_NUM];
	unsigned char  stream_ts_type[MAX_STREAM_NUM];
	unsigned char  stream_desc[MAX_STREAM_NUM][32];
	unsigned long  stream_counter[MAX_STREAM_NUM];
	unsigned char  stream_posted[MAX_STREAM_NUM];
	unsigned short stream_pack_byte[MAX_STREAM_NUM];
	

	unsigned char video_stream_id;   //PS builder used
	unsigned char audio_stream_id;   //PS builder used
	unsigned char video_stream_type; //PS builder used
	unsigned char audio_stream_type; //PS builder used
	unsigned char video_stream_index;//PS builder used
	unsigned char audio_stream_index;//PS builder used
	unsigned char video_stream_num;  //PS builder used
	unsigned char audio_stream_num;  //PS builder used

	unsigned short selected_audio_pid;
	unsigned short selected_video_pid;
	bool enable_ptsfix;
	
	LONGLONG  last_pcr;
	LONGLONG  inc_pcr;
	LONGLONG  cur_pcr;
	LONGLONG  last_PTS;
	LONGLONG  cur_PTS;
	LONGLONG  last_sync_pcr;
	LONGLONG  pcr_rate;
	unsigned  long last_pcr_packet_num;
	unsigned  long last_pts_packet_num;

	LONGLONG  frame_counter; //use frame counter to correct/estimate PCR
	LONGLONG  frame_pcr_inc;
	LONGLONG  pcr_per_frame; 

	PTS_INF	pts_inf[MAX_STREAM_NUM];
	//LONGLONG startPTS;
	int rebuild_pts;
	bool disable_multi_audio_stream; 
	bool exclusive_teletext;
	
	bool AVinfo_notify_posted;
	//unsigned short  channel_program_id[MAX_STREAM_NUM];
	unsigned short	tsids_num;
	unsigned short	cur_tsid;
	unsigned short  tsids[MAX_SERVICE_NUM];
	unsigned short  stream_network_type;

	bool lock_ATSC_channel;  //try to lock ATSC channel by major minor
	unsigned short ATSC_major;
	unsigned short ATSC_minor;

	unsigned char  channel_flag[MAX_STREAM_NUM];
	char channel_name[40];
	bool channel_picked;
	bool plungin_enabled;
	int	 state;				//0: stop; 1: running
	int  busy;				//1: busy  0: done

	//trace timimg.
	//LONGLONG hrtime_start;

	char __dummy[1];  //for av alligment

} TSSPLT;

 
void* OpenTSSplitter( int OutputSel );
void  CloseTSSplitter( void* handle );
void  ResetTSSplitter( TSSPLT* splt );
bool  SelectTSChannel( TSSPLT* splt, unsigned short channel, bool reset ); //ATSC  //obsolute ZQ. 
bool  SelectTSProgram( TSSPLT* splt, unsigned short Prgoram, bool reset ); //DVB   //obsolute ZQ. 
bool  PickupTSChannel( TSSPLT* splt, unsigned short channel, unsigned short program, char *channel_name, bool reset );
bool  PickupATSCChannel( TSSPLT* splt, unsigned short major, unsigned short minor, unsigned short Program, char *ChannelName, bool reset );
bool  _ExtractStream( TSSPLT* splt, PID_TBL* Pids, int numPids ); //REMOVE IT  //obsolute ZQ. 

void  SelectOuputFormat( TSSPLT* splt, int OutputSel );        //0: TS, 1:PS

bool  PopupPacket( TSSPLT* splt, unsigned char* pData, unsigned int* pType );
bool  DrainPacket( TSSPLT* splt, unsigned char* pData, unsigned int* Size ); //empty packet in buffer
bool  CheckProgramStart( TSSPLT* splt );

int   GetTotalProgram( TSSPLT* splt );
int   GetTSProgramList( TSSPLT* splt, unsigned short* ProgramList, unsigned long MaxBytes );
bool  GetPmtVersion( TSSPLT* splt, int Program, unsigned short* pVer );
bool  GetCountOfStreams( TSSPLT* splt, int Program, unsigned short* pVal );
bool  StreamType( TSSPLT* splt, int Program, unsigned short index, unsigned char* pVal );
int   GetVideoProgramList( TSSPLT* splt, unsigned short* ProgramList, unsigned long MaxBytes );

int   GetValidChannelNum( TSSPLT* splt );
int   GetChannelName( TSSPLT* splt, void* pChannelName , unsigned long MaxBytes );
int   GetVideoChannelNum( TSSPLT* splt );
void  SetEPGDump( TSSPLT* splt, EPG_DUMP pfn, void* Context );
void  SetAVInfDump( TSSPLT* splt, AV_INF_DUMP pfn, void* Context );
void  SetPidTblDump( TSSPLT* splt, PID_DUMP pfn, void* Context );
void  SetPMTDataDump( TSSPLT* splt, DATA_DUMP pfn, void* Context );
void  StopTSParser( TSSPLT* splt );
void  StarTStParser( TSSPLT* splt );
bool  IsScrambledTSChannel( TSSPLT* splt, unsigned short Channel );
bool  IsScrambleTSdProgram( TSSPLT* splt, unsigned short Program );
bool  RetreveSIProgramName( TSSPLT* splt, unsigned short Program, char* name, unsigned short size );

bool  ParseData( TSSPLT* splt, const unsigned char* pData, long dwBytes );
int   NumOfPacketsInPool( TSSPLT* splt );
void  SetupRebuildPTS( TSSPLT* splt, int contrl );
void  EPGParserCtrl( TSSPLT* splt, unsigned long contrl );
void  AVInfoCtrl( TSSPLT* splt, unsigned long contrl );
void  ResetSIParser( TSSPLT* splt );
void  DisableMultiAudio( TSSPLT* splt, int flag );
LONGLONG GetCurPTS( TSSPLT* splt );
LONGLONG GetLastPTS( TSSPLT* splt );
bool IsVideoStreamReady( TSSPLT* splt );
bool IsAudioStreamReady( TSSPLT* splt );
unsigned long PacketInputNum( TSSPLT* splt );
int  GetProgramState( TSSPLT* splt, unsigned short Program );
int  GetTotalProgramNum( TSSPLT* splt );
void SetTSStreamType( TSSPLT* splt, int type );
void SetSIStreamType( TSSPLT* splt, int type );
int  GetSIStreamType( TSSPLT* splt );
void StartTSParser( TSSPLT* splt );
void StopTSParser( TSSPLT* splt );
bool IsTSParserStopped( TSSPLT* splt );

int PushData( TSSPLT* splt, const unsigned char* data, int len, unsigned char* out_buf, int buf_size, 
			  OUTPUT_DUMP pfnOutput, void* context );
int FlashData( TSSPLT* splt, unsigned char* out_buf, int buf_size, 
			  OUTPUT_DUMP pfnOutput, void* context );

int PushData2( TSSPLT* splt, const unsigned char* data, int len, ALLOC_BUFFER pfnAllocBuffer, void* context_alloc,
			  OUTPUT_DUMP pfnOutput, void* context_output );
int FlashData2( TSSPLT* splt, ALLOC_BUFFER pfnAllocBuffer, void* context_alloc,
			   OUTPUT_DUMP pfnOutput, void* context_output );
int RebuildTSPMT( TSSPLT* splt );

bool InitPushDone( TSSPLT* splt, const unsigned char* data, int len, char* AVInfoBuf, int BufSize );
void FlushPush( TSSPLT* splt );
void DisablePTSFIX( TSSPLT* splt );
void EnablePTSFIX( TSSPLT* splt );
void enable_tssplitter_log();
void disable_tssplitter_log();
int  GetNetworkNum( TSSPLT* splt );
int  GetNetworkList( TSSPLT* splt, char* Buf, int MaxBufSize );
void SelectTSTSID( TSSPLT* splt, unsigned short tsid );
void LockATSCChannel( TSSPLT* splt, unsigned short major, unsigned short minor );
void GetDebugInfo( TSSPLT* splt, unsigned short cmd, char* Buf, unsigned long BufSize );
char* GetStreamInfo( TSSPLT* ts, char* buf, int buf_len );

bool IsMultipleTSDI(  TSSPLT* ts );
unsigned short ShiftTSID( TSSPLT* ts, unsigned short tsid );
unsigned short CurTSID( TSSPLT* ts );
int TSSpltBuildState( TSSPLT* splt );
void LockUpParser( TSSPLT* splt );
void UnLockUpParser( TSSPLT* splt );
void SetDeviceName( char* name );
 
#ifdef __cplusplus
}
#endif


#endif




