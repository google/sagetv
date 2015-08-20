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

#if !defined( _TS_NATIVE_INCLUDED_ )
#define _TS_NATIVE_INCLUDED_

#if defined(__APPLE__) || defined(__linux__)
 #include <stdbool.h>
#else
 #define  bool    int         
 #define  true	 1
 #define  false   0
#endif

#define _MIN(x,y) ((x)>(y)? (y):(x))
#define _MAX(x,y) ((x)>(y)? (x):(y))

#ifdef WIN32
//*********************** WINDOWS section *************************

#if( _MSC_VER <= 800 )
#pragma pack(1)  
#else
#include <pshpack1.h>
#endif

//*********************** WINDOWS section *************************
#else
#ifdef MAC
//*********************** MAC section *************************
#include <sys/cdefs.h>

#ifndef _inline
 #define _inline inline
#endif

#define min(x,y) ((x)>(y)? (y):(x))   //replace with _MIN
#define max(x,y) ((x)>(y)? (x):(y))   //replace with _MAX

#define stricmp strcasecmp
#define strnicmp strncasecmp

//*********************** MAC section *************************
#else
//*********************** LINUX section *************************
//typedef _int64 long long
#ifndef _inline
 #define _inline inline
#endif
#define min(x,y) ((x)>(y)? (y):(x))   //replace with _MIN
#define max(x,y) ((x)>(y)? (x):(y))   //replace with _MAX

//LINUX OS API conversion
#define stricmp strcasecmp
#define strnicmp strncasecmp
//*********************** LINUX section *************************
#endif
#endif


#ifdef __cplusplus
extern "C" {
#endif

/* put memory alloc under control (leak check) */
_inline void* sagetv_malloc( int size );
_inline void  sagetv_free( void* );

//for cross-platform API stricmp
int  sage_stricmp ( const char * dst,  const char * src );

#define	TS_PACKET_LENGTH  188

#define	MAX_PROGRAM		64
#define	MAX_ES			64

#define MAX_SERVICE_NUM 32

#define	SYNC		   0x47

typedef long (*LPFNParserDump)(void* handle, short, void*);
typedef long (*LPFNBuilderDump)(void* handle, short, void*);
typedef long (*LPFNRawDump)(void* handle, void*, long );
typedef long (*LPFNMesgDump)(void* handle, short, void*);

#ifdef WIN32   //windows defined
typedef _int64 LONGLONG;
typedef unsigned _int64 ULONGLONG;
typedef LONGLONG REFERENCE_TIME;
#else
typedef long long LONGLONG;
typedef unsigned long long ULONGLONG;
typedef LONGLONG REFERENCE_TIME;
#endif

#ifndef STREAM_TYPE_ENUM
#define STREAM_TYPE_ENUM
typedef enum
{
	UNKNOWN_STREAM_TYPE = 0,
	ATSC_STREAM_TYPE = 1,
	DVB_STREAM_TYPE = 2,
} STREAM_TYPE;
#endif

typedef	struct	
{
	char		  sync;
	bool		  error;
	bool		  start;
	bool		  priority;	
	unsigned short pid;	
	char		  scrambling_ctr;
	char		  adaption_ctr;	
	char		  continuity_ct;
	char		  pcr_flag;
} TS_HEADER;

typedef	struct	
{
	unsigned char apapt_len;
	bool		  discontinute;	
	bool		  random_acceess;
	bool		  elem_stream_prio;	
	char		  flags;
} TS_ADAPTION;

typedef	struct	
{
	//unsigned char PCR[6];
	//unsigned char OPCR[6];
	unsigned char splice;
} TS_ADAPTION_OPTION;

typedef struct
{
	int  bytes;
	int  total_bytes;
	int  data_size;
	char* data;       //maxium is 1K bytes
	char left_over[200];
	bool left_over_size;
} TS_SECTION;

typedef	struct	
{
	unsigned short SectionLength;
	unsigned short TSID;
	unsigned char  VersionNumber;
	bool		   CurrentNextIndicator;
	unsigned char  SectionNumber;
	unsigned char  LastSectionNumber;
	unsigned short NumPrograms;	
	unsigned short ProgramNumber[MAX_PROGRAM];
	unsigned short ProgramPID[MAX_PROGRAM];	
	unsigned char  counter;	//used by TS_HEADER.continuity_ct 4 bits for PAT
	TS_SECTION section;
} TS_PAT;
	
/* Program Map data*/
typedef	struct	
{
	unsigned char  TableId;
	unsigned short SectionLength;
	unsigned short ProgramNumber;
	unsigned char  VersionNumber;
	bool		   CurrentNextIndicator;
	unsigned char  SectionNum;
	unsigned char  LastSectionNum;
	unsigned short PCRPID;
	unsigned short ProgramInfoLength;
	unsigned char* ProgramInfo;
	unsigned short NumStreams;	
	unsigned char  StreamType[MAX_ES];	
	unsigned short ElementaryPID[MAX_ES];
	unsigned short ESInfoLength[MAX_ES];
	unsigned char* ESInfo[MAX_ES]; 
	unsigned char  EScounter[MAX_PROGRAM];	//used by TS_HEADER.continuity_ct 4 bits for every ES;
	unsigned char  counter;	//used by TS_HEADER.continuity_ct 4 bits for PMt
	TS_SECTION*    section;
} TS_PMT;

typedef struct
{
	unsigned short DescLength;
	unsigned char* DescInfo; 
} DESC;

typedef struct
{
	short channelID;
	short programID;
	short type;
	short pid;
} PROGRAM_INFO;

typedef struct
{
	unsigned short  channelID;
	unsigned short  programID;
	unsigned short  streamIndex;  //streamIndex:-1 indicates PMT, orthers are ES
	unsigned short  streamType;
	unsigned short  pid;
	char   Desc[180]; 
} AVSTREAM_INFO;

typedef struct
{
	unsigned char  type;
	short sub_channel;
	bool  start_group_flag;
	unsigned continuity;
	short pid;
	short bytes;
	char* data;
	unsigned short header_bytes;
	LONGLONG    PCR;
} TS_AV_INFO;

typedef struct
{
	unsigned char  stream_id;
	unsigned char  stream_type;
	bool  start_group_flag;
	bool  has_PTS;
	bool  has_DTS;
	short bytes;
	char* data;
	short header_bytes;
	LONGLONG    PCR;  //PCR for TS, SCR for PS
	LONGLONG	PTS;
	LONGLONG	DTS;
} PES_AV_INFO;

typedef struct
{
	char  type;
	short pid;
	void* data;
	short size;
} SIGNAL_INFO;

typedef struct
{
	short pid;
	short service;
	short channel;
	void* parser;
	void* tbl;
	char* data;
	short length;
} SECTION_INFO;


#ifndef SI_DATA_STRUCT
#define SI_DATA_STRUCT

#define	MAX_CHANNEL_NAME	32
typedef struct 
{
	unsigned short a;  //ATSC:major;  DVB: ONID
	unsigned short b;  //ATSC:mainor; DVB: TSID
	unsigned short c;  //ATSC:program_id; DVB: program_id
} CHANNEL;

typedef struct
{
	CHANNEL channel;
	char name[MAX_CHANNEL_NAME];
	unsigned short type;
} CHANNEL_NAME;

#endif

typedef int  (*SI_PARSER_DUMPER)( void* context, unsigned short pid, TS_HEADER* ts, char* data, int bytes );

#define STREAM_START		   0x01
#define STREAM_READY		   0x02
#define STREAM_CLOSE		   0x03
#define STREAM_VIDEO_START	   0x04
#define STREAM_AUDIO_START	   0x05
#define PCR_READY			   0x06
#define PES_SYSTEM_PACK		   0x10
#define PES_PACKET_PACK		   0x11
#define PES_PADDING_PACK	   0x12
#define PES_SAGE_PRIVATE_INF   0x13

void StartSection(TS_SECTION* pSection, int nSectionLength );
bool PushSectionData( TS_SECTION* pSection, char* pData, int Bytes );
bool SectionCrcCheck( TS_SECTION* pSection );
bool BuildSectionData( TS_SECTION* pSection, int nSectionLength, char* pData );
int  PopSectionData( TS_SECTION* pSection, char* pData, int Bytes );


#ifdef __cplusplus
}
#endif

#endif

