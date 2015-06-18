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

#if !defined( _TS_NATIVE_PES_PARSER_INCLUDED_ )
#define _TS_NATIVE_PES_PARSER_INCLUDED_

#include "TSnative.h"
#include "AVUtility.h"

#ifdef WIN32
#if( _MSC_VER <= 800 )
#pragma pack(1)  
#else
#include <pshpack1.h>
#endif
#else
//typedef _int64 long long
#ifndef _inline
 #define _inline inline
#endif
#endif

#define  VIDEO_STREAM_DESC_TAG			02          //ISO 13818-1-200
#define  AUDIO_STREAM_DESC_TAG			03          //ISO 13818-1-200
#define  MPEG4_VIDEO_STREAM_DESC_TAG    27          //ISO 13818-1-200
#define  MPEG4__STREAM_DESC_TAG			28          //ISO 13818-1-200
#define  VIDEO_WINDOW_DESC_TAG			 8          //ISO 13818-1-200

#ifdef __cplusplus
extern "C" {
#endif

#define PES_AV_DATA_BUFFER_SIZE  64
#define DEFAULT_BLOCK_SIZE		 4096

#define MAX_PES_STREAM	   16
//#define MAX_VIDEO_STREAM   6
//#define MAX_AUDIO_STREAM   (MAX_PES_STREAM-MAX_VIDEO_STREAM)
#define MAX_PROP_STREAM    02

typedef struct {
	char* pData;
	unsigned short Size;
	LONGLONG SCR;
	unsigned long Demux;
} PACK_INF;

typedef struct {
	char* pData;
	unsigned short Size;
	unsigned long Demux;
	unsigned long VideoBlockSize;
	unsigned long AudioBlockSize;
} SYSTEM_HEAD_INF;

typedef struct {
	char* pData;
	unsigned short Size;
	LONGLONG SCR;
	unsigned long Demux;
} PES_PACK_INF;

typedef struct {
	char* pData;
	unsigned short Size;
	void* reserved;
} PADDING_INF;

typedef struct {
	char* pData;
	unsigned short Size;
	unsigned char Version;
	unsigned char VideoNum;
	unsigned char AudioNum;
	unsigned char VideoMain;
	unsigned char AudioMain;
	unsigned char MainVideoStreamType;
	unsigned char MainAudioStreamType;
} SAGE_PRIVATE_INF;

typedef struct {
	short			Type;		//reserver
	unsigned long	PacketSize;
	unsigned long	DataSize;
	unsigned char*  Packet;
} PES_AV_DATA;

typedef struct {
	unsigned char  stream_id;
	unsigned short stream_type;
	void*    stream_info;
} PSM;

typedef struct {

	unsigned long     parser_data_bytes;
	unsigned long     other_data_bytes;
	PES_INFO   OtherPES;
	bool	 video_PES_start;
	bool	 audio_PES_start;

	unsigned char video_stream_id;
	unsigned char audio_stream_id;
	unsigned char video_stream_type;
	unsigned char audio_stream_type;
	unsigned long video_demux_rate;
	unsigned long audio_demux_rate;
	unsigned long video_block_size;
	unsigned long audio_block_size;
	unsigned long demux_bound;

	int	video_track_num;
	int audio_track_num;
	int video_main_track;
	int audio_main_track;
	unsigned char main_video_stream_type;
	unsigned char main_audio_stream_type;
	int info_version;
	bool sage_info_embedded;

	unsigned short stream_LUT[MAX_PES_STREAM];  //stream index look up table(LUT)
	unsigned char  proprietary_stream[MAX_PROP_STREAM];

	PSM psm[MAX_PES_STREAM];
	int psm_num;

	AV_CONTEXT AV[MAX_PES_STREAM];
	PES_INFO   PES[MAX_PES_STREAM];
	//int stream_data_size[MAX_PES_STREAM];
	unsigned char stream_type[MAX_PES_STREAM];
	int cur_stream_index;

	//Statistic	
	unsigned long video_out_bytes;
	unsigned long audio_out_bytes;
	unsigned long video_packets;
	unsigned long audio_packets;
	unsigned long other_packets;
	unsigned long video_block;
	unsigned long audio_block;
	unsigned long packets;

	//H.264 Info
	unsigned char h264_frame_rate;

	LPFNBuilderDump av_dumper;                 //Audio video data dumpre 
	LPFNParserDump  signal_dumper;             //state change callback function
	LPFNRawDump     raw_dumper;             //state change callback function
	void*    env;  //caller enviroment context, used by dumper call back.

	int parser_state;
	//unsigned char cur_stream_id;
	//unsigned char cur_stream_type;

	char * data_block_buffer;
	char * data_block_remain;
	int  data_block_size;

	LONGLONG	  data_pos;                        //a packet position = data_pos + data_offset
	unsigned long data_offset;                     //

} PS_PARSER;

void SetPSDumpAVCallback( PS_PARSER* parser,  LPFNParserDump dumper );
void SetPSDumpSignalCallback( PS_PARSER* parser,  LPFNParserDump dumper); 
void SetPSDumpRawCallback( PS_PARSER* pParser,  LPFNRawDump dumper);

PS_PARSER* PSParserOpen( );
void PSParserClose( PS_PARSER* pParser );

LONGLONG PSDataPosition( PS_PARSER* pParser );
void   PSResetDataPosition( PS_PARSER* pParser );

void PSProcessBlock( PS_PARSER* pParser, int size, char* data );
void PSFlushout( PS_PARSER* pParser );
void PSResetBlockBuffer( PS_PARSER* pParser );
unsigned long PSProcessBlockWithoutBuffer( PS_PARSER* pParser, int Size, char* pData, int* pUsedBytes );
int DecodePackHeader( char* pData, int Bytes, unsigned long *pDemux, LONGLONG* pRefTime );
unsigned long GetStreamBoundRate( PS_PARSER* pParser );
unsigned long GetVideoBlockSize( PS_PARSER* pParser );
unsigned long GetAudioBlockSize( PS_PARSER* pParser );
int  GetMainTrack( PS_PARSER* pParser, int* video_track, int* audio_track );
int  GetTrackNum( PS_PARSER* pParser, int* video_num, int* audio_num );
int  GetMainStreamType( PS_PARSER* pParser, int* video_stream_type, int* audio_stream_type );
int  GetH264FrameRate( PS_PARSER* pParser );
#ifdef __cplusplus
}
#endif

#endif
