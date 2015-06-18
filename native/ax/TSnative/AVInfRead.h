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


#if !defined( _TS_NATIVE_AVINFREAD_INCLUDED_ )
#define _TS_NATIVE_AVINFREAD_INCLUDED_

//data structure byte packing throughout
#ifdef WIN32
#if( _MSC_VER <= 800 )
#pragma pack(1)   
#else
#include <pshpack1.h>
#endif
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define TS_FORMAT	   1
#define PES_FORMAT     2
#define M2T_FORMAT     3

#define MAX_STREAM_NUM    16

const LONGLONG MILLISECONDS = (1000);            // 10 ^ 3
const LONGLONG NANOSECONDS = (1000000000);       // 10 ^ 9
const LONGLONG UNITS = (1000000000/ 100);		 // 10 ^ 7

#define AUDIO_PRESENT_MASK    2
#define VIDEO_PRESENT_MASK    1

typedef struct {
	bool		   stream_ready;   //when codec information is ready, it's set
	bool		   stream_present; //it's set when stream is indictate by PES system header, or PMT table
	bool		   stream_pin_ready; //it's set when stream pin is pull out for downstream.
	unsigned char  stream_id;      //stream id ( 2,3 for Video, 4,5 for Audio, 0x81 for AC3 )
	unsigned char  stream_type;    //stream type ( PES stream id, 0xCx for video, 0xEx for audio, 0xbd, 0xfd private stream )
	AV_CONTEXT	   av;             //Audio/video codec information
	LONGLONG	   firstPTS;	   //first Picture Time Stamp
	LONGLONG	   lastPTS;		   //last Picture Time Stamp
	unsigned long  data_frames;    //recieved data frames
	unsigned short pid;            //TS pid
	char  stream_desc[180];
} STREAM_INF;


typedef struct
{
	bool		   start_output;
	void*		   parser;
	//CMPEG2Parser*  master;
	STREAM_INF	   stream[MAX_STREAM_NUM];
	char           program_desc[180];	    
	int  program_id;
	int	 selected_channel;	
	int  parser_state;
	int  stream_state;
	int  channel_type;
	int  video_state;
	int  audio_state;
	int  stream_num;
	int  audio_stream_num;
	int  video_stream_num;
	int  video_main_track;
	int	 audio_main_track;
	int	 sage_inf_flag;
	unsigned long  start_tick;
	unsigned long  last_tick;
	LONGLONG	   llFirstPTS;
	int			   nTrackPTSChannel;
	unsigned short uInputStreamFormat;
	unsigned short dwAudioCtrl;
	unsigned long  avg_byte_rate;
}	ENV;

//define mask bit for stream_state
#define   PARSER_STREAM_DONE  0x800
#define   PARSER_STREAM_START 0x001
#define   PARSER_STREAM_READY 0x002

extern int GetAVInf( char* FileName, unsigned long CheckSize, bool bLiveFile, int RequestedChannel, 
			   char* FormatBuf, int FormatSize, char* DurationBuf, int DurationSize, int* ProgramNum );
extern int DetectInputStreamFormat( unsigned char* pData, long dwSize );
#ifdef __cplusplus
}
#endif

#endif

