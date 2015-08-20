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

#ifndef MEPG_AV_TRACKS_H
#define MEPG_AV_TRACKS_H

#ifdef __cplusplus
extern "C" {
#endif


#define ES_GROUP_START	0x02
#define TS_GROUP_START	0x01

enum TRACK_TYPE {
	MPEG_TS   = 0x01,
	MPEG_M2TS = 0x02,
	MPEG_ASI =  0x04,
	MPEG_PS   = 0x10,
} ;

#define IS_TS_TYPE( type ) (( type & 0x0f ))
#define IS_PS_TYPE( type ) (( type & 0x10 ))

#define NO_PACKET		0x81
#define NO_ES_BLOCK		0x82
#define ENCRYPTED		0x83
#define UNKONW_FORMAT_1	0x84   //timeout to parsing out format 
#define UNKONW_FORMAT_2	0x85   //maxium data to parsing out format 
#define UNKONW_FORMAT_3	0x86   //unknown audio fomat
#define UNKONW_FORMAT_4	0x87   //private stream
#define UNKONW_FORMAT_5	0x88   //not avideo or video stream

typedef struct TRACK
{
	unsigned short channel_index;
	unsigned char  slot_index;
	unsigned char  group_start;   //ES_GROUP_START, TS_GROUP_START
	unsigned char  scrambling_flag;
	unsigned char  state;         //0: unknown; 1:ready; >129: unkonw format
	unsigned char  command;       //used by ES  block
	unsigned char  reserve;

	struct TS_ELEMENT  *ts_elmnt;
	struct ES_ELEMENT  *es_elmnt;
	struct AV_ELEMENT  *av_elmnt;

	unsigned char* es_data_start;
	unsigned short es_block_size;
	unsigned short es_data_bytes;
	unsigned char* buffer_start;
	unsigned short buffer_size;
	unsigned short buffer_index;
	LONGLONG	start_cue;
	LONGLONG	cue;

	unsigned long  es_pes_bytes;

	unsigned long  ts_packets_counter;
	unsigned long  es_blocks_counter;
	unsigned long  processed_bytes; 

} TRACK;

enum TRACK_ATTR {
	ATTR_VIDEO_PRESENT = 0x0001,
	ATTR_AUDIO_PRESENT = 0x0002,
	ATTR_SUB_PRESENT   = 0x0004,
	ATTR_TTX_PRESENT   = 0x0008,
	ATTR_ENCRYPTED_FLAG= 0x0010,
} ;

typedef struct TRACKS
{
	unsigned short slot_index;
	unsigned short track_type; //1: MPEG-TS; 2:MPEG-PS
	unsigned short track_attr; //
	unsigned short number_track;
	unsigned short total_track;
	unsigned short main_video_index;
	unsigned short main_audio_index;
	unsigned short padding;
	TRACK* track;
} TRACKS;

TRACKS *CreateTracks( int nTrackNum );
void ResetTracks( TRACKS* pTracks );
void ReleaseTracks( TRACKS* pTracks );
void CheckTracksAttr( TRACKS* pTracks, unsigned long LanguageCode );
int  TracksInfo( TRACKS* pTracks, char* pBuffer, int nSize );
static inline int FindMainVideoAudio( TRACKS* pTracks )
{
	 return ( pTracks->main_video_index != 0xffff || pTracks->main_audio_index != 0xffff );
}

#ifdef __cplusplus
}
#endif

#endif
