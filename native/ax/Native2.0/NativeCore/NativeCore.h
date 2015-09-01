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
#ifndef   _NATIVE_CORE_H_ 
#define   _NATIVE_CORE_H_

#ifdef __cplusplus
extern "C" {
#endif

#ifndef NULL
#define NULL    ((void *)0)
#endif

#ifdef WIN32   //windows defined
#pragma warning(disable : 4996)
#pragma warning(disable : 4995)
#pragma warning(disable : 234)

#define _USE_32BIT_TIME_T

// TODO why this check, above is already a WIN32 check?
#ifdef WIN32
typedef			 _int64  LONGLONG;
typedef unsigned _int64  ULONGLONG;
#define inline _inline
#else
typedef long long LONGLONG;
typedef unsigned long long ULONGLONG;
typedef LONGLONG REFERENCE_TIME;

#define inline __inline__
#endif

// TODO why linux here within WIN32 - is about MINGW / Cygwin support?
#ifdef Linux
#define _MAX_PATH       512
#endif

#ifndef ASSERT	
#define ASSERT	assert
#endif
//#define ASSERT	_ASSERT

#ifndef TEXT
#define TEXT( x ) x
#endif

#define snprintf  _snprintf
#define vsnprintf  vsnprintf

#ifndef lstrlen
#define lstrlen	strlen
#endif

#ifndef lstrcat
#define lstrcat	strcat
#endif

#define STRNICMP  strnicmp
#define STRICMP   stricmp

#define FCLOSE	close
#define FSEEK	_lseeki64
#define FTELL	_telli64
#endif

#ifdef Linux   //

#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/errno.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>
#include <memory.h>	
#include <stdlib.h>

#define TEXT( x )	x
#define snprintf snprintf
#define vnprintf vnprintf
#define lstrlen	strlen
#define lstrcat	strcat

#define STRNICMP   strncasecmp
#define STRICMP    strcasecmp

//#define FOPEN	open64
#define FCLOSE	close
#define FSEEK	lseek64
#define FTELL(x)	lseek64(x, 0, SEEK_END) 

#ifndef __USE_FILE_OFFSET64
typedef long long off64_t;
extern off64_t lseek64( int filedes, off64_t offset, int whence );
extern off64_t tell64( int filedes );
#endif

typedef			 long long  LONGLONG;
typedef unsigned long long ULONGLONG;
typedef ULONGLONG REFERENCE_TIME;
#define ASSERT   assert
#endif

// added define for Apple OS/X, added OSByteOrder in comparison to Linux
#ifdef __APPLE__
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/errno.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>
#include <memory.h>
#include <stdlib.h>
#include <libkern/OSByteOrder.h>

#define lseek64 lseek
#define open64 open

#define TEXT( x )	x
#define snprintf snprintf
#define vnprintf vnprintf
#define lstrlen	strlen
#define lstrcat	strcat

#define STRNICMP   strncasecmp
#define STRICMP    strcasecmp

//#define FOPEN	open64
#define FCLOSE	close
#define FSEEK	lseek64
#define FTELL(x)	lseek64(x, 0, SEEK_END)

#ifndef __USE_FILE_OFFSET64
typedef long long off64_t;
extern off64_t lseek64( int filedes, off64_t offset, int whence );
extern off64_t tell64( int filedes );
#endif

typedef			 long long  LONGLONG;
typedef unsigned long long ULONGLONG;
typedef ULONGLONG REFERENCE_TIME;
#define ASSERT   assert

#endif

#define _MIN(x,y) ((x)>(y)? (y):(x))
#define _MAX(x,y) ((x)>(y)? (x):(y))

#include <assert.h>
#include "NativeMemory.h"
#include "AVTrack.h"
#include "AVFormat/MpegVideoFormat.h"
#include "AVFormat/VC1Format.h"
#include "AVFormat/H264Format.h"
#include "AVFormat/AC3Format.h"
#include "AVFormat/EAC3Format.h"
#include "AVFormat/MpegAudioFormat.h"
#include "AVFormat/DTSFormat.h"
#include "AVFormat/AACFormat.h"
#include "AVFormat/LPCMFormat.h"
#include "AVFormat/MediaVideoFormat.h"
#include "AVFormat/Subtitle.h"

////////////////////////////////////////////////////////////////////////////////////
//debug log 
enum {  _LOG_TIMING  = 0x01,    // Timing and performance measurements
        _LOG_TRACE   = 0x02,     // General step point call tracing
        _LOG_MEMORY  = 0x04,   // Memory and object allocation/destruction
        _LOG_LOCKING = 0x08,   // Locking/unlocking of critical sections
        _LOG_ERROR   = 0x10,     // Debug error notification
        _LOG_CUSTOM1 = 0x20,
        _LOG_CUSTOM2 = 0x40,
};

//#define REMOVE_LOG	

#ifdef REMOVE_LOG	
#define SageLog(x)
#define PTSLog(x)
#else
#define SageLog(x)   _sagelog x
//#define PTSLog(x)  _pts_log1 x
#define PTSLog(x)
#endif
void _sagelog( int type, int level, const char* cstr, ... ); 
void _flog_setup( char* filename, int enable );
int _flog_check();
void _enable_native_log( );
void _disable_native_log( );
void _pts_log0( int type, ULONGLONG pts, ULONGLONG dts, ULONGLONG position, int index );
void _pts_log1( int type, ULONGLONG pts, ULONGLONG dts, ULONGLONG position, int index );
//#define MEMORY_REPORT() MemoryReport( )
////////////////////////////////////////////////////////////////////////////////////
#ifdef _DEBUG
#define SET_SAGE_TAG( p, tag )    { memcpy( p->_tag_, tag, sizeof( p->_tag_ ) );  }
#define CHECK_SAGE_TAG( p, tag )  { ASSERT( !memcmp( p->_tag_, tag, sizeof(p->_tag_) ) ); }
#else
#define SET_SAGE_TAG( p, tag )    
#define CHECK_SAGE_TAG( p, tag )  
#endif

///////////////////////////////////////////////////////////////////////
extern int sagelog_enabled;
extern int console_enabled;
extern int trace_level;
extern void _flog_setup( char* filename, int enable );
extern char* _get_flog_path(  );
extern int _get_flog_state( );
///////////////////////////////////////////////////////////////////////

typedef int (*DUMP)( void* pContext, void* pData, int nSize );
typedef void* (*MEM_ALLOC_HOOK)( void* pContext, void* pMemory, int nSize, int nCmd );
///////////////////////////////////////////////////////////////////////

#define SAGE_FOURCC( a ) (*((unsigned long*)( a )))
//#define SAGE_FOURCC(a)      ((a[0]<<24)|(a[1]<<16)|(a[2]<<8)|a[3])
char* _sagetv_fourcc_( unsigned long lFourCC, char* pFourCC );


///////////////////////////////////////////////////////////////////////
#ifdef TS_SPLITTER
#define MAX_SLOT_NUM		4
#else
#define MAX_SLOT_NUM		1   
#endif 
#define MAX_TRACK_NUM	    8
#define MAX_USER_PID_NUM    8

#define MAX_PROGRAM_NUM     1  //support multiple channel streaming on a TSParser if it more than 1

#define ES_BLOCK_SIZE	  (2*1024+128)
#define PACK_SPACE		  (40)
#define ES_BUFFER_SIZE    (ES_BLOCK_SIZE+PACK_SPACE)

enum JOB_TASK {
	REMUX_FILE	 =	0x01,
	REMUX_STREAM =	0x02,
	FILE_AVINF	 =	0x03,
	STREAM_AVINF =	0x04,
	CHANNEL_INF  =	0x05,
	PTS_CHECK	 =	0x06,
	STRIP_STREAM =  0x07, //soft or hard demuxing packets
	QUICK_FIX	 =	0x09,
} ;

enum STRAM_TYPE {
	ATSC_STREAM	= 0x01,
	DVB_STREAM	= 0x02,
	FREE_STREAM = 0x03,
} ;

enum STREAM_SUB_TYPE {
	UNKONW_TYPE   = 0x00,
	TERRESTRIAL   = 0x01,
	CABLE         = 0x02,
	SATELLITE     = 0x03,
} ;

enum STREAM_DATA_TYPE {
	VIDEO_DATA	  = 0x01,
	AUDIO_DATA	  = 0x02,
	SUBTITLE_DATA = 0x03,
	TELETEXT_DATA = 0x04,
	PRIVAITE_DATA = 0x05,
	VBI_DATA	  = 0x06,
	UNIDENTIFIED  = 0x0E,  //waiting for parsing
	UNKNOWN_DATA   = 0x0F,
} ;

enum STATUS {
	PARSER_STOP    = 0x00,
	PARSER_RUNNING = 0x01,
	PARSER_FAILED  = 0x0f
} ; 


#define USER_SET_PID   0x10
/////////////////////////////////////////////////////////////

/////////////////////////////////////////////////////////////

typedef struct {
	unsigned long  freq;
	unsigned short band;
	unsigned short modulation;
	unsigned char  trans_mode;
	unsigned char  padding[3]; //padding
} TERRESTRIAL_TUNE;

typedef struct {
	unsigned long  freq;
	unsigned long  symbol_rate;
	unsigned char  modulation;
	unsigned char  reserve; //padding
	unsigned char  fec_out;
	unsigned char  fec_out_rate;
	unsigned char  fec_in;
	unsigned char  fec_in_rate;
	unsigned char  padding[2]; //padding
} CABLE_TUNE;

typedef struct {
	unsigned long  freq;
	unsigned long  symbol_rate;
	unsigned char  fec;
	unsigned char  fec_rate;
	unsigned char  pol;
	unsigned char  modulation;
	short orbit;  //positive Esat; negative West
	unsigned short padding; //padding for aligment of 4 bytes
} SATELLITE_TUNE;

typedef struct ATSC_CHANNEL
{
	unsigned short major_num;
	unsigned short minor_num;
	unsigned short physical_ch;
	unsigned short program_id;
	unsigned short tsid;
	unsigned short service_type;
	union {
		TERRESTRIAL_TUNE atsc;
		CABLE_TUNE		 qam;
	} u;
	char name[12*2];
} ATSC_CHANNEL;


typedef struct DVB_CHANNEL
{
	unsigned short onid;
	unsigned short tsid;
	unsigned short sid;
	unsigned short service_type;
	unsigned short ca;
	unsigned short dvb_type; //1:terrestrial, 2:cable, 3:satellite
	union {
		TERRESTRIAL_TUNE t;
		CABLE_TUNE		 c;
		SATELLITE_TUNE   s;
	} dvb;
	unsigned short  running_status; //0: undefined, 1:stop, 2 pause, 4 running, 3, will start
	char name[10*2];
} DVB_CHANNEL;

typedef struct UNKNOWN_CHANNEL
{
	unsigned short data1;
	unsigned short data2;
	unsigned short data3;
	unsigned short tsid;
} UNKNOWN_CHANNEL;

typedef struct PID_ENTRY
{
	unsigned short pid;
	unsigned char  type;
	unsigned char  index;
	char desc[16];
} PID_ENTRY;

typedef struct PIDS_TABLE
{
	unsigned short tune_id;
	unsigned short tsid;
	unsigned short sid;
	unsigned short pcr_pid;
	unsigned short ca_pid;
	unsigned short pid_num;
	PID_ENTRY pid_tbl[MAX_USER_PID_NUM];
} PIDS_TABLE;

typedef struct TUNE
{
	unsigned char  state;					 //1:locked a channel; 0:not locked
	unsigned char  stream_format;            //ATSC,DVB
	unsigned char  sub_format;               //T,C,S,QAM 
	unsigned char  tune_string_type;         //3:xx-xx-xx; 2:xx-xx; 1: program; 0:channel
	unsigned short channel;                  //if tune_string_type = 0, pick up by channel
	unsigned short tune_id;					 //uniqe id of caller
	union {
		ATSC_CHANNEL   atsc;
		DVB_CHANNEL	   dvb;
		UNKNOWN_CHANNEL unkn;
	} u;

	PIDS_TABLE pids_table;

} TUNE;

typedef struct PROGRAM_DATA
{
	PIDS_TABLE pids_table;
	unsigned char* pmt_section_data;
	unsigned short pmt_section_bytes;
	unsigned short padding;
} PROGRAM_DATA;

//check if aligment correct
#define STRUCT_TUNE_SIZE 232
#define TUNE_SIZE_CHECK( )  ASSERT( STRUCT_TUNE_SIZE == sizeof( TUNE ) );

typedef struct MESSAGE_DATA
{
	char title[32];
	unsigned char  *message;
	unsigned char  *buffer;
	unsigned short message_length;
	unsigned short buffer_length;
} MESSAGE_DATA;


enum ES_BLOCK_CMD {
	NONE            = 0x0,
	REQUEST_BUFFER  = 0x01,
	DATA_READY		= 0x02,
	DATA_OVERFLOW   = 0x04,
	START_FILL_DATA	= 0x10,
	STOP_FILL_DATA  = 0x20
} ;


typedef struct PES
{
	unsigned char  stream_id;
	unsigned char  pes_sub_id;   //unique id of PES; 0xe0, 0xe1 (video); 0xc0, 0xc1 (audio); 0x80, 0x81 (AC3)...
	unsigned char  has_pts;
	unsigned char  has_dts;
	unsigned short header_length;
	unsigned short packet_length;
	LONGLONG       pts;
	LONGLONG       dts;
	unsigned char  es_type; //mpeg1=1, mpeg2=2, extended mpge2=3
	unsigned char  padding[3];
} PES;

typedef struct TS_ELEMENT
{
	unsigned short pid;
	unsigned short padding1;
	unsigned char  type;
	unsigned char  channel_index;
	unsigned char  audio_type; //1:sillence; 2:impaired hearing; 3:commontary
	unsigned char  content_type;
	unsigned long  format_fourcc;
	unsigned long  language_code;
	unsigned char  desc[64];
	unsigned short desc_len;
} TS_ELEMENT;

typedef struct  ES_ELEMENT
{
	unsigned short channel_index;  //index of ES_ELEMENT
	unsigned short es_id;          //stream_id (8bits)+ stream_index(8bits)
	unsigned char  stream_id;
	unsigned char  es_type;        //mpeg1, mpeg2, vc1
	unsigned char  content_type;   //1:video; 2:audio; 
	unsigned char  padding1;
	unsigned long  format_fourcc;
	unsigned long  language_code;
	unsigned char  audio_type;     //1:sillence; 2:impaired hearing; 3:commontary
	unsigned char  padding2[3];        //padding
	ULONGLONG	   scr;
	unsigned short private_bytes;  //4 byte for private stream in PS stream
	unsigned short padding3;
	unsigned char  private_data[4];
	PES  pes;
} ES_ELEMENT;

typedef struct  AV_ELEMENT
{
	unsigned short channel_index;	//index of ES_ELEMENT
	unsigned char  content_type;	//1:video; 2:audio; 
	unsigned char  stream_index;	//keep stream in an order [main-video, sub-video1 sub-video2...][main-audio, sub-audio1, sub-audio2...][]
	unsigned long  format_fourcc;
	union 
	{
		union 
		{
			MPEG_VIDEO	mpeg_video;
			H264_VIDEO	h264;
		} v;
		union 
		{
			AC3_AUDIO	 ac3;
			EAC3_AUDIO	 eac3;
			LPCM_AUDIO	 lpcm;
			MPEG_AUDIO	 mpeg_audio;
			AAC_AUDIO	 aac;
			DTS_AUDIO	 dts;
		} a;
		union
		{
			SUBTITLE	 sub;
		} s;

	} d;
	unsigned char* private_data;
} AV_ELEMENT;

typedef struct TS_STREAMS
{
	unsigned short num_stream;
	unsigned short total_streams;
	unsigned short pcr_pid;
	unsigned short ca_pid;
	unsigned short pmt_pid;
	unsigned short padding;
	TS_ELEMENT    *ts_element;
	void		  *container;
} TS_STREAMS;

typedef struct ES_STREAMS
{
	unsigned short num_stream;
	unsigned short total_streams;
	ULONGLONG	   scr;
	ES_ELEMENT     *es_element;
} ES_STREAMS;

typedef struct AV_STREAMS
{
	unsigned short num_stream;
	unsigned short total_streams;
	AV_ELEMENT     *av_element;
} AV_STREAMS;


typedef struct OUTPUT_DATA
{
	unsigned char *data_ptr;
	unsigned short bytes;
	unsigned short group_flag;
	unsigned short start_offset;
	unsigned short dummy; //reserver
	unsigned long  fourcc;
} OUTPUT_DATA;

typedef struct DATA_BUFFER
{
	unsigned short  data_bytes;
	unsigned short  buffer_size;
	unsigned short  padding1;
	unsigned short  padding2;
	unsigned char*  buffer;
} DATA_BUFFER;

typedef struct ATS_TIME
{
	unsigned short type;
	union 
	{
		unsigned long ats;
		ULONGLONG pts;
	} t;
	unsigned long packets;
} ATS_TIME;

char* CodeConfig( );
int  RemuxFile(  unsigned short task, char* pInputFile, TUNE* pTune, int nInputFormat, 
			     char* pOutFile, int nOutputFormat, int nOption );
void* OpenRemuxStream( unsigned short nTask, TUNE* pTune, 
					   int nInputFormat, int nOutputFormat, 
					   MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext,
					   DUMP pfnOutputDump, void* pOutputDumpContext );
int	 PushRemuxStreamData( void* Handle , unsigned char *pData, int nBytes, int *nExpectedBytes );
void CloseRemuxStream( void* Handle );
void ResetRemuxStream( void* Handle );
void FlushRemuxStream( void* Handle );
void ResetRemuxStreamSlot( void* Handle, int nSlot );
void SetupRemuxStreamTune( void* Handle, TUNE *pTune );
void SetupRemuxTSStreamFormat( void* Handle, int nFormat, int nSubFormat );
void LockUpTSParser( void* Handle );
void UnLockUpTSParser( void* Handle );
void DisableTSRebuildStream( void* Handle );
void EnableTSRebuildStream( void* Handle );
void DisableTSPSI( void *Handle );
void EnableTSPSI( void*Handle ); 
void SetupAVInfDump( void* Handle, DUMP pfnAVInfDump, void* pfnAVInfDumpContext );
void SetupProgramDataDump( void* Handle, DUMP pfnProgramDataDump, void* pfnProgramDataContext );

struct DEMUXER* GetDemuxer( void* Handle );
int GetAVFormat(  char* pFileName, unsigned long nCheckMaxiumSize, int bStreamData, 
			   int nRequestedTSChannel,   char* pFormatBuf, int nFormatSize, char* pDurationBuf, 
			   int DurationSize, int* nTotalChannel );
char* _data_alignment_check_( char* buf, int buf_size );
char* _access_alignment_check_( char* buf, int buf_len );

int CheckSagePVRData( const unsigned char* pData, int nBytes );

#ifdef __cplusplus
 }
#endif

#endif
