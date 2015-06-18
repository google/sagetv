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
#if !defined( _TS_NATIVE_PES_BUILDER_INCLUDED_ )
#define _TS_NATIVE_PES_BUILDER_INCLUDED_

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


#ifdef __cplusplus
extern "C" {
#endif

#define  VIDEO_BLOCK_SIZE		   (1024*232)
#define  AUDIO_BLOCK_SIZE	 	   (128*32)

#define  PACKETSIZE		(2048*1)					// MPEG Program stream block size
#define  CLOCKRATE		((LONGLONG)27000000)			// MPEG System clock rate
#define  STREAMRATE		((LONGLONG)2401587)			// Original HD stream rate 19.2 Mbps
#define  FILE_TIME		(CLOCKRATE * (LONGLONG)60)		// Time for each file
#define  DEMUX			(((int)STREAMRATE * 8) / 50)		// Demux value for HD content STREAMRATE / 50

#define  PES_PACKET_BUFFER_SIZE		128*2              //tototal buffer size could be (PES_PACKET_BUFFER_SIZE*PACKETSIZE)

#define  MAX_STREAM_NUM				 24
#define  MAX_AUDIO_STREAM_NUM	     10
#define  MAX_VIDEO_STREAM_NUM	     10

typedef struct {
	char		    Type;
	short			State;		//0:empty, 1:pending, 2:ready
	unsigned long   SeqNum;
	unsigned long	PacketSize;
	unsigned long	DataSize;
	unsigned char*  Packet;
} PS_PACKET_DATA;

typedef struct {
	int		 track;	
	short	 state;
	unsigned char stream_id;
	unsigned char stream_type;
	unsigned char stream_index;
	unsigned char av_type;
	AV_CONTEXT av;
} TRACK;

typedef struct {

	BITS_T   bits;
	unsigned char     bits_buf[128];
	char     video_buffer[MAX_VIDEO_STREAM_NUM][PACKETSIZE+32];
	char     audio_buffer[MAX_AUDIO_STREAM_NUM][PACKETSIZE+32];
	char*    video_PES_ptr[MAX_VIDEO_STREAM_NUM];
	char*    audio_PES_ptr[MAX_AUDIO_STREAM_NUM];
	short    video_data_bytes[MAX_VIDEO_STREAM_NUM];
	short    audio_data_bytes[MAX_AUDIO_STREAM_NUM];
	//bool	 video_PES_start[MAX_VIDEO_STREAM_NUM];
	//bool	 audio_PES_start[MAX_AUDIO_STREAM_NUM];

	unsigned char video_stream_id;
	unsigned char audio_stream_id;
	unsigned long video_demux_rate;
	unsigned long audio_demux_rate;
	unsigned char main_video_index;
	unsigned char main_audio_index;
	unsigned char total_video_num;
	unsigned char total_audio_num;
	unsigned char main_video_stream_type;
	unsigned char main_audio_stream_type;
	bool main_audio_start;
	bool remapping_stream; 
	unsigned int totoal_stream;

	TRACK audio_track[MAX_STREAM_NUM];
	TRACK video_track[MAX_STREAM_NUM];
	
	
	//PES_INFO VideoPES;
	//PES_INFO AudioPES;

	PS_PACKET_DATA PacketBuffer[PES_PACKET_BUFFER_SIZE];    //packets buffer pool
	int PacketsInPoolNum;                          //number of packet that are in packets buffer pool
	int ReadyPacketsInPoolNum;
	unsigned long Sequence;
	int PacketQueueHead;						   //packets buffer pool queue header	
	int PacketQueueTail;						   //packets buffer pool queue tail
	int VideoSlot[MAX_STREAM_NUM];
	int AudioSlot[MAX_STREAM_NUM];

	//Statistic	
	unsigned long video_packets;
	unsigned long audio_packets;
	unsigned long video_incoming;
	unsigned long audio_incoming;
	unsigned long packets;

	// Timestamp tracking
	LONGLONG lastRefTime;

	//H.264 Info
	unsigned char h264_frame_rate;

	bool PSM_packet_enable;

	LPFNBuilderDump builder_dumper;
	void*    env;  //caller enviroment context, used by dumper call back.

} PS_BUILDER;


PS_BUILDER* PSBuilderOpen( );
void PSBuilderClose( PS_BUILDER* buider );
void PSResetBuilder( PS_BUILDER* builder );
int  CreatStreamHeader( PS_BUILDER* pBuilder, unsigned char VidstreamId, unsigned char AudstreamId,
                        unsigned char VidstreamType, unsigned char AudstreamType );

int  CreatSageStreamHeader( PS_BUILDER* pBuilder,   unsigned char VidstreamId, unsigned char AudstreamId,
					  unsigned char VidstreamType, unsigned char AudstreamType, 
					  unsigned char VidsNum,	   unsigned char AudsNum, 
					  unsigned char VidsMain,	   unsigned char AudsMain, 
					  unsigned char CtrlBits );

int ClosePSStream( PS_BUILDER* pBuilder );
int OutputVideoData( PS_BUILDER* pBuilder, int Index, unsigned char StreamType, bool PESStart, char* pData, int Size, LONGLONG ref_time );
int OutputAudioData( PS_BUILDER* pBuilder, int Index, unsigned char StreamType, bool PESStart, char* pData, int Size, LONGLONG ref_time );
int LookupAudioTrack( PS_BUILDER* pBuilder, int Index, unsigned char StreamType );
int LookupVideoTrack( PS_BUILDER* pBuilder, int Index, unsigned char StreamType );
AV_CONTEXT* GetAudioAVInfo( PS_BUILDER* pBuilder, int Index, unsigned char StreamType );
AV_CONTEXT* GetVideoAVInfo( PS_BUILDER* pBuilder, int Index, unsigned char StreamType );
int GetVideoNewStreamId( PS_BUILDER* pBuilder, int Index, unsigned char StreamType );
int GetAudioNewStreamId( PS_BUILDER* pBuilder, int Index, unsigned char StreamType );

//unsigned short LookupAudioStreamId( PS_BUILDER* pBuilder, int Index, unsigned char StreamType, char* PrivateHeader, int length  );
//unsigned short LookupVideoStreamId( PS_BUILDER* pBuilder, int Index, unsigned char StreamType );
//void DropVideoPSPacket( PS_BUILDER* pBuilder );
//void DropAudioPSPacket( PS_BUILDER* pBuilder );

int  GetPSReadyPacketNumInPool( PS_BUILDER* pBuilder ); 
//bool PeekPSPacket( PS_BUILDER* pBuilder, short* pType, char** ppPacketData,unsigned  long* pSize );
bool PopPSPacket( PS_BUILDER* pBuilder,  short* pType, char* pPacketData, unsigned long* pSize );
bool FlushOutPSPacket( PS_BUILDER* pBuilder, short* pType, char* pPacketData, unsigned long* pSize );

int  PSGetBlockSize( PS_BUILDER *builder );

#ifdef __cplusplus
}
#endif

#endif
