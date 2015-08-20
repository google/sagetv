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
// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)

#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include <stdarg.h>
#include <time.h>

#define SAGE_MPEG_VERSION     4

#include "TSnative.h"
#include "PSBuilder.h"

#ifdef WIN32   
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}
#endif

#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _flog x
#endif

static bool flog_enabled=true;
void enable_psbuilder_log()
{
	flog_enabled = true;
}

void disable_psbuilder_log()
{
	flog_enabled = false;
}

static void _flog( char* logname, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	
	if ( !flog_enabled ) return;

	fp = fopen( logname, "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );
	fprintf( fp, "%02d/%02d/%d %02d:%02d:%02d  ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
	ltm.tm_hour, ltm.tm_min, ltm.tm_sec );  
	va_start(args, cstr);
	vfprintf( fp, cstr, args );
	va_end(args);
	fclose( fp );
}
static void _flog_check()
{
	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
	}
}

static int  BuildPESHeader( PS_BUILDER* pBuilder, int streamid, int len, LONGLONG PTS, LONGLONG DTS );
static bool PushPESPacket( PS_BUILDER* pBuilder, int index, char Type,  char* pPacketData, unsigned long Size );
static int   AllocPESPacket( PS_BUILDER* pBuilder );
int BuildPsmPacket( PS_BUILDER* pBuilder, unsigned char *stream_id, unsigned char* stream_type, int stream_num );

static void logfile( char* str )
{
	FILE* fp = fopen( "TSSplitter.log", "a+" ); 
	if ( fp != NULL )
	{
		time_t ct;
		struct tm *ltm;
		time(&ct); ltm=localtime( &ct );
		fprintf( fp, "%02d/%02d %02d:%02d:%02d ", ltm->tm_mon+1, ltm->tm_mday,
		ltm->tm_hour, ltm->tm_min, ltm->tm_sec );  

		fprintf( fp, "%s",  str );
		fflush( fp );
		fclose( fp );
	}
}


/*
static void BuildPTS( PS_BUILDER* pBuilder, LONGLONG PTS )
{
	PutBits( &pBuilder->bits, 2, 4);					// '0010'						4
	PutBits( &pBuilder->bits, (unsigned int)(PTS >> 30), 3);		// PTS [32..30]						3
	PutBits( &pBuilder->bits, 1, 1);		  			// marker bit						1
	PutBits( &pBuilder->bits, (unsigned int)(PTS >> 15), 15);		// PTS [29..15]						15
	PutBits( &pBuilder->bits, 1, 1);					// marker bit						1
	PutBits( &pBuilder->bits, (unsigned int)PTS, 15);			// PTS [14..0]						15
	PutBits( &pBuilder->bits, 1, 1);					// marker bit						1
}
*/


static int BuildPackHeader( PS_BUILDER* pBuilder, long MuxRate, LONGLONG ref_time )
{
	int bytes;
	LONGLONG ext_time;
	LONGLONG time;
	//if (ref_time != 0 && ref_time > pBuilder->lastRefTime)
	//{
	pBuilder->lastRefTime = ref_time;
	//}

	ext_time = pBuilder->lastRefTime % 300;
	time = pBuilder->lastRefTime / 300;

	PutBits( &pBuilder->bits, 0x000001ba, 32);			// pack id					32
	PutBits( &pBuilder->bits, 1, 2);				// 0x01						2
	PutBits( &pBuilder->bits, (unsigned int)(time >> 30)& 0x07, 3);	// system_clock_reference_base			3
	PutBits( &pBuilder->bits, 1, 1);				// marker_bit					1
	PutBits( &pBuilder->bits, (unsigned int)(time >> 15)& 0x7fff, 15);	// system_clock_reference_base			15
	PutBits( &pBuilder->bits, 1, 1);				// marker_bit					1
	PutBits( &pBuilder->bits, (unsigned int)(time & 0x7fff), 15);		// system_clock_reference_base1			15
	PutBits( &pBuilder->bits, 1, 1);				// marker_bit					1
	PutBits( &pBuilder->bits, (unsigned int)ext_time, 9);		// system_clock_reference_extension		9
	PutBits( &pBuilder->bits, 1, 1);				// marker_bit					1
	PutBits( &pBuilder->bits, MuxRate, 22);				// program_mux_rate				22
	PutBits( &pBuilder->bits, 1, 1);				// marker_bit					1
	PutBits( &pBuilder->bits, 1, 1);				// marker_bit					1
	PutBits( &pBuilder->bits, 31, 5);				// reserved					5
	PutBits( &pBuilder->bits, 0, 3);				// pack_stuffing_length				3
	bytes = CloseOutBits( &pBuilder->bits );
	return bytes;

}

static int BuildSystemHeader( PS_BUILDER* pBuilder, int videostream, int vidbsize,
	int audiostream, int audbsize)
{
	int bytes;
	int len = 6+6;
	if (videostream != 0)
		len += 3;

	if (audiostream != 0)
		len += 3;

	PutBits( &pBuilder->bits, 0x000001bb, 32);				// system id					32
	PutBits( &pBuilder->bits, len, 16);					// header_length				16
	PutBits( &pBuilder->bits, 1, 1);					// marker_bit					1
	PutBits( &pBuilder->bits, DEMUX, 22);					// rate_bound					22
	PutBits( &pBuilder->bits, 1, 1);					// marker_bit					1
	PutBits( &pBuilder->bits, 1, 6);					// audio_bound					6
	PutBits( &pBuilder->bits, 0, 1);					// fixed_flag					1
	PutBits( &pBuilder->bits, 0, 1);					// CSPS_flag					1
	PutBits( &pBuilder->bits, 1, 1);					// system_audio_lock_flag			1
	PutBits( &pBuilder->bits, 1, 1);					// system_video_lock_flag			1
	PutBits( &pBuilder->bits, 1, 1);					// marker_bit					1
	PutBits( &pBuilder->bits, 1, 5);					// video_bound					5
	PutBits( &pBuilder->bits, 0, 1);					// packet_rate_restriction_flag			1
	PutBits( &pBuilder->bits, 127, 7);					// reserved_byte				7

	//if (videostream != 0)
	//{
	//	PutBits( &pBuilder->bits, videostream, 8);
	//	PutBits( &pBuilder->bits, 0x3, 2);
	//	PutBits( &pBuilder->bits, vidbscl, 1);
	//	PutBits( &pBuilder->bits, vidbsize, 13);
	//}

	//if (audiostream != 0)
	//{
	//	PutBits( &pBuilder->bits, audiostream, 8);
	//	PutBits( &pBuilder->bits, 0x3, 2);
	//	PutBits( &pBuilder->bits, audbscl, 1);
	//	PutBits( &pBuilder->bits, audbsize, 13);
	//}

	if (videostream != 0)
	{
		PutBits( &pBuilder->bits, 0xb9, 8); //all video streams
		PutBits( &pBuilder->bits, 0x3, 2);
		PutBits( &pBuilder->bits, 1, 1);    // P_STD_buffer_bound_scale 1: 1024
		PutBits( &pBuilder->bits, vidbsize/1024, 13);
	}

	if (audiostream != 0)
	{
		PutBits( &pBuilder->bits, 0xb8, 8); //all auido streams
		PutBits( &pBuilder->bits, 0x3, 2);  
		PutBits( &pBuilder->bits, 0, 1);    // P_STD_buffer_bound_scale 0: 128
		PutBits( &pBuilder->bits, audbsize/128, 13);
	}

	PutBits( &pBuilder->bits, 0xbd, 8); //private stream 1 
	PutBits( &pBuilder->bits, 0x3, 2);
	PutBits( &pBuilder->bits, 1, 1);    //P_STD_buffer_bound_scale 1: 1024
	PutBits( &pBuilder->bits, (58*1024)/1024, 13);

	PutBits( &pBuilder->bits, 0xbf, 8); //private stream 12
	PutBits( &pBuilder->bits, 0x3, 2);
	PutBits( &pBuilder->bits, 1, 1);    //P_STD_buffer_bound_scale 1: 1024
	PutBits( &pBuilder->bits, (2*1024)/1024, 13);

	bytes = CloseOutBits( &pBuilder->bits );
	return bytes;
	
}

static int PadBuffer( PS_BUILDER* pBuilder, char* out_buf, int len )
{
	int i;
	char buf[6]= { 0x0, 0x0, 0x01, 0xBE, 0, 0 };
	if ( len < 6 )
	{
		memset( buf, 0xff, len );
		return 0;
	}

	len -= 6;
	buf[4] = len >> 8; buf[5] = len & 0xff;


	memcpy( out_buf, buf, 6 );

	for ( i = 0; i<len; i++ )
		out_buf[6+i] = 0xff;

	return len + 6;
}

static int BuildStreamHeader( PS_BUILDER* pBuilder, int vidstreamid, int audstreamid )
{ 
	int bytes;

	InitOutBits( &pBuilder->bits, pBuilder->bits_buf, sizeof(pBuilder->bits_buf) );

	BuildPackHeader( pBuilder, DEMUX, 0 );

	// Write the system block
	if ( audstreamid > 0 )
	{
		BuildSystemHeader( pBuilder, vidstreamid,  VIDEO_BLOCK_SIZE, audstreamid, AUDIO_BLOCK_SIZE );
	}
	else
	{
		BuildSystemHeader( pBuilder, vidstreamid, VIDEO_BLOCK_SIZE, 0, 0);		// 4096k for video packets
	}
	
	bytes = CloseOutBits( &pBuilder->bits );
	return bytes;
}

static void FillPESLength( PS_BUILDER* pBuilder, char* pPESHeader, unsigned short Length )
{
	unsigned char *p;
	if ( Length < 6 )
		return;

	Length -= 6;

	p = (unsigned char*)pPESHeader;
	p[4] = ( Length >> 8 ) & 0xff;
	p[5] = ( Length ) & 0xff;
}

static int BuildPESHeader( PS_BUILDER* pBuilder, int streamid, int len, LONGLONG PTS, LONGLONG DTS )
{
	int bytes;
	int hdrlen = 0;
	int PTS_DTS_flags = 0;
	if ( len < 6 )
		return 0;

	len -= 6;

	if ( PTS != -1 && PTS != 0 )
	{
		if ( DTS != -1 && DTS != 0 )
		{
			PTS_DTS_flags = 3;
			hdrlen += 10;
		}
		else
		{
			PTS_DTS_flags = 2;
			hdrlen += 5;
		}
	}

	InitOutBits( &pBuilder->bits, pBuilder->bits_buf, sizeof(pBuilder->bits_buf) );

	PutBits( &pBuilder->bits, 0x000001, 24);						// packet_start_code_prefix		24
	PutBits( &pBuilder->bits, (unsigned int)streamid, 8);			// directory_stream_id					8
	PutBits( &pBuilder->bits, len, 16);								// PES_packet_length		16
	PutBits( &pBuilder->bits, 0x2, 2);								// '10'				2
	PutBits( &pBuilder->bits, 0, 2);								// PES_scrambling_control	2
	PutBits( &pBuilder->bits, 1, 1);								// PES_priority			1
	PutBits( &pBuilder->bits, 0, 1);								// data_alignment_indicator	1
	PutBits( &pBuilder->bits, 0, 1);								// copyright			1
	PutBits( &pBuilder->bits, 0, 1);								// original_or_copy		1
	PutBits( &pBuilder->bits, PTS_DTS_flags, 2);					// PTS_DTS_flags				2
	PutBits( &pBuilder->bits, 0, 1);								// ESCR_flag			1
	PutBits( &pBuilder->bits, 0, 1);								// ES_rate_flag			1
	PutBits( &pBuilder->bits, 0, 1);								// DSM_trick_mode_flag		1
	PutBits( &pBuilder->bits, 0, 1);								// additional_copy_info_flag	1
	PutBits( &pBuilder->bits, 0, 1);								// PES_CRC_flag			1
	PutBits( &pBuilder->bits, 0, 1);								// PES_extension_flag		1
	PutBits( &pBuilder->bits, hdrlen, 8);							// PES_header_data_length		8
	if (PTS_DTS_flags == 2)
	{
		PutBits( &pBuilder->bits, 2, 4);							// '0010'			4
		PutBits( &pBuilder->bits, (unsigned int)(PTS >> 30), 3);	// PTS [32..30]						3
		PutBits( &pBuilder->bits, 1, 1);							// marker bit			1
		PutBits( &pBuilder->bits, (unsigned int)(PTS >> 15), 15);	// PTS [29..15]						15
		PutBits( &pBuilder->bits, 1, 1);							// marker bit			1
		PutBits( &pBuilder->bits, (unsigned int)PTS, 15);			// PTS [14..0]					15
		PutBits( &pBuilder->bits, 1, 1);							// marker bit			1
	}
	else if (PTS_DTS_flags == 3)
	{
		PutBits( &pBuilder->bits, 3, 4);							// '0011'			4
		PutBits( &pBuilder->bits, (unsigned int)(PTS >> 30), 3);	// PTS [32..30]						3
		PutBits( &pBuilder->bits, 1, 1);							// marker bit			1
		PutBits( &pBuilder->bits, (unsigned int)(PTS >> 15), 15);	// PTS [29..15]						15
		PutBits( &pBuilder->bits, 1, 1);							// marker bit			1
		PutBits( &pBuilder->bits, (unsigned int)PTS, 15);			// PTS [14..0]					15
		PutBits( &pBuilder->bits, 1, 1);							// marker bit			1
		PutBits( &pBuilder->bits, 1, 4);							// '0001'			4
		PutBits( &pBuilder->bits, (unsigned int)(DTS >> 30), 3);	// DTS [32..30]						3
		PutBits( &pBuilder->bits, 1, 1);							// marker bit			1
		PutBits( &pBuilder->bits, (unsigned int)(DTS >> 15), 15);	// DTS [29..15]						15
		PutBits( &pBuilder->bits, 1, 1);							// marker bit			1
		PutBits( &pBuilder->bits, (unsigned int)DTS, 15);			// DTS [14..0]					15
		PutBits( &pBuilder->bits, 1, 1);							// marker bit			1
	}
	bytes = CloseOutBits( &pBuilder->bits );
	return bytes;
}

// Mark last valid stream pos
int ClosePSStream( PS_BUILDER* pBuilder )
{
	static unsigned char end_mark[4] = { 0x0, 0x0, 0x01,0xb9 };
	int bytes;
	int i;
	for ( i=0; i < MAX_VIDEO_STREAM_NUM; i++ )
		if ( pBuilder->video_data_bytes[i] )
		{
			bytes = pBuilder->video_data_bytes[i];
			PadBuffer( pBuilder, pBuilder->video_buffer[i]+bytes, PACKETSIZE-bytes );
			PushPESPacket( pBuilder, AllocPESPacket( pBuilder ), 0,  pBuilder->video_buffer[i], PACKETSIZE );
			pBuilder->video_data_bytes[i] = 0;
		}

	for ( i=0; i < MAX_AUDIO_STREAM_NUM; i++ )
		if ( pBuilder->audio_data_bytes[i] )
		{
			bytes = pBuilder->audio_data_bytes[i];
			PadBuffer( pBuilder, pBuilder->audio_buffer[i]+bytes, PACKETSIZE-bytes );
			PushPESPacket( pBuilder, AllocPESPacket( pBuilder ), 1,  pBuilder->audio_buffer[i], PACKETSIZE );
			pBuilder->audio_data_bytes[i] = 0;
		}
	PushPESPacket( pBuilder, AllocPESPacket( pBuilder ), 2,  (char*)end_mark, 4 );
	return 4;
}

int CreatStreamHeader( PS_BUILDER* pBuilder, unsigned char VidstreamId, unsigned char AudstreamId,
					  unsigned char VidstreamType, unsigned char AudstreamType )
{
	int bytes;
	InitOutBits( &pBuilder->bits, pBuilder->bits_buf, sizeof(pBuilder->bits_buf) );
	bytes = BuildStreamHeader( pBuilder, VidstreamId, AudstreamId );
	FlushOutBits( &pBuilder->bits,  pBuilder->video_buffer[0], bytes );
	PadBuffer( pBuilder, pBuilder->video_buffer[0]+bytes, PACKETSIZE-bytes );
	PushPESPacket( pBuilder, AllocPESPacket( pBuilder ), 0,  pBuilder->video_buffer[0], PACKETSIZE );
	memset( pBuilder->video_data_bytes, 0, sizeof(pBuilder->video_data_bytes) );
	memset( pBuilder->audio_data_bytes, 0, sizeof(pBuilder->audio_data_bytes) );
	pBuilder->video_demux_rate = DEMUX;
	pBuilder->audio_demux_rate = DEMUX;

	return PACKETSIZE;
}
int FillSageInfo( PS_BUILDER* pBuilder, unsigned char* ptr, int bytes, unsigned char CtrlBits )
{
	int used_bytes = 0;
	if ( bytes < 12 ) return 0;
	memcpy( ptr, "SAGE", 4 );
	ptr += 4;
	*ptr++ = SAGE_MPEG_VERSION;
	*ptr++ = CtrlBits;
	*ptr++ = pBuilder->total_video_num;
	*ptr++ = pBuilder->total_audio_num;
	*ptr++ = pBuilder->remapping_stream ? 0 : pBuilder->main_video_index;
	*ptr++ = pBuilder->remapping_stream ? 1 : pBuilder->main_audio_index;
	*ptr++ = pBuilder->main_video_stream_type;
	*ptr++ = pBuilder->main_audio_stream_type;
	used_bytes = 4+8;
	
	if ( pBuilder->main_video_stream_type == H264_STREAM_TYPE  )
	{
		FillSageDesc( ptr, bytes-used_bytes, SAGETV_H264_INFO, (char*)&pBuilder->h264_frame_rate, 1 );
	}

	return bytes;
}

//I embedded Sage information in pad packets
//format: SAGE (4byte mark), version(1byte), ctrl bits ( 1byt), video tracks num( 1 byte ), audio track num( 1 bytes )
//  main video track( 1 bytes ); main audi track ( 1 bytes )
int CreatSageStreamHeader( PS_BUILDER* pBuilder, unsigned char VidstreamId, unsigned char AudstreamId,
            unsigned char VidstreamType, unsigned char AudstreamType, unsigned char VidsNum, unsigned char AudsNum, 
			unsigned char VidsMain, unsigned char AudsMain, unsigned char CtrlBits )
{
	int bytes; 
	unsigned char *ptr;
	InitOutBits( &pBuilder->bits, pBuilder->bits_buf, sizeof(pBuilder->bits_buf) );

	if ( pBuilder->remapping_stream && IsAudioStreamId(AudstreamId) )
		AudstreamId = 0xC0 | (AudsMain & 0x1f);

	pBuilder->video_demux_rate = DEMUX;
	pBuilder->audio_demux_rate = DEMUX;
	pBuilder->main_audio_index = AudsMain;
	pBuilder->main_video_index = VidsMain;
	pBuilder->total_audio_num = AudsNum;
	pBuilder->total_video_num = VidsNum;
	pBuilder->main_video_stream_type = VidstreamType;
	pBuilder->main_audio_stream_type = AudstreamType;

	bytes = BuildStreamHeader( pBuilder, VidstreamId, AudstreamId );
	FlushOutBits( &pBuilder->bits,  pBuilder->video_buffer[0], bytes );
	PadBuffer( pBuilder, pBuilder->video_buffer[0]+bytes, PACKETSIZE-bytes );

	//embedding Sage Information
	ptr = (unsigned char*)pBuilder->video_buffer[0]+bytes+6;
	FillSageInfo( pBuilder, ptr, PACKETSIZE-bytes-6, 0 );

	PushPESPacket( pBuilder, AllocPESPacket( pBuilder ), 0,  pBuilder->video_buffer[0], PACKETSIZE );
	memset( pBuilder->video_data_bytes, 0, sizeof(pBuilder->video_data_bytes) );
	memset( pBuilder->audio_data_bytes, 0, sizeof(pBuilder->audio_data_bytes) );

	bytes = 0;
	if ( pBuilder->PSM_packet_enable ) {
		unsigned char stream_id[2]; unsigned char stream_type[2];
		int stream_num;
		stream_num = 2;
		stream_id[0]  = VidstreamId;
		stream_type[0]= VidstreamType;
		stream_id[1]  = AudstreamId;
		stream_type[1]= AudstreamType;
		if ( stream_id[1] == 0xbd && stream_type[1] == 0x06  )
			stream_type[1] = 0x81;

		bytes = BuildPsmPacket( pBuilder, stream_id, stream_type, stream_num );
	} else
	if ( VidstreamType ==  H264_STREAM_TYPE ) //I insert PSM packet for H264 stream
	{
		unsigned char stream_id[2]; unsigned char stream_type[2];
		int stream_num;
		stream_num = 2;
		stream_id[0]  = VidstreamId;
		stream_type[0]= VidstreamType;
		stream_id[1]  = AudstreamId;
		stream_type[1]= AudstreamType;
		if ( stream_id[1] == 0xbd && stream_type[1] == 0x06  )
			stream_type[1] = 0x81;

		bytes = BuildPsmPacket( pBuilder, stream_id, stream_type, stream_num );
	}

	return PACKETSIZE + bytes;
}

static int BuiderVideoPackPESHeader( PS_BUILDER* pBuilder, int trk, LONGLONG ref_time, LONGLONG PTS, LONGLONG DTS, LONGLONG SCR )
{
	int bytes;

	InitOutBits( &pBuilder->bits, pBuilder->bits_buf, sizeof(pBuilder->bits_buf) );

	//build PACK header
	bytes = BuildPackHeader( pBuilder, pBuilder->video_demux_rate, SCR );
	FlushOutBits( &pBuilder->bits,  pBuilder->video_buffer[trk], bytes );
	pBuilder->video_PES_ptr[trk] = pBuilder->video_buffer[trk] + bytes;
	pBuilder->video_data_bytes[trk] = bytes;

	//build PES header
	bytes = BuildPESHeader( pBuilder, pBuilder->video_track[trk].stream_id, PACKETSIZE-bytes, PTS, DTS );
	FlushOutBits( &pBuilder->bits, pBuilder->video_PES_ptr[trk], bytes );
	pBuilder->video_data_bytes[trk] += bytes;

	return pBuilder->video_data_bytes[trk];

}

static char AC3MagicCode[4]={ 0x80, 0x01, 0x00, 0x02 };
static char DTSMagicCode[4]={ 0x80, 0x01, 0x00, 0x03 };  //sagetv creates this 4 bytes majic code ZQ
static int BuiderAudioPackPESHeader( PS_BUILDER* pBuilder, int trk, LONGLONG ref_time, LONGLONG PTS, LONGLONG SCR )
{
	int bytes;
	
	InitOutBits( &pBuilder->bits, pBuilder->bits_buf, sizeof(pBuilder->bits_buf) );

	//build PACK header
	bytes = BuildPackHeader( pBuilder, pBuilder->audio_demux_rate, SCR );
	FlushOutBits( &pBuilder->bits,  pBuilder->audio_buffer[trk], bytes );
	pBuilder->audio_PES_ptr[trk] = pBuilder->audio_buffer[trk] + bytes;
	pBuilder->audio_data_bytes[trk] = bytes;

	//build PES header
	bytes = BuildPESHeader( pBuilder, pBuilder->audio_track[trk].stream_id, PACKETSIZE-bytes, PTS, -1 );
	FlushOutBits( &pBuilder->bits, pBuilder->audio_PES_ptr[trk], bytes );
	pBuilder->audio_data_bytes[trk] += bytes;

	//if it's AC3 audio that's private stream data, we need fill 4 bytes magic bytes, that first bytes is SubId.
	if ( pBuilder->audio_track[trk].av_type == 0x81 ) 
 	{
		char *p = pBuilder->audio_buffer[trk] + pBuilder->audio_data_bytes[trk];
		memcpy( p, AC3MagicCode, sizeof(AC3MagicCode) );
		*p = 0x80+trk;  //new subid (first bytes 4 Magic header)
		pBuilder->audio_data_bytes[trk] += sizeof(AC3MagicCode);
	}
	//if it's DTS audio that's private stream data, we need fill 4 bytes magic bytes, that first bytes is SubId.
	if ( pBuilder->audio_track[trk].av_type == 0x82 ) 
 	{
		char *p = pBuilder->audio_buffer[trk] + pBuilder->audio_data_bytes[trk];
		memcpy( p, DTSMagicCode, sizeof(DTSMagicCode) );
		*p = 0x80+trk;  //new subid (first bytes 4 Magic header)
		pBuilder->audio_data_bytes[trk] += sizeof(DTSMagicCode);
	}


	return pBuilder->audio_data_bytes[trk];

}

unsigned long GetCRC32( const unsigned char *pData, int len );
int BuildPsmPacket( PS_BUILDER* pBuilder, unsigned char *stream_id, unsigned char* stream_type, int stream_num )
{
	int i, bytes = 0, len;
	unsigned char *p;
	unsigned long crc;

	if ( stream_num <= 0 || stream_num > MAX_STREAM_NUM )
		return 0;

	p = (unsigned char*)pBuilder->video_buffer[0];
    	p[0] = 0; p[1] = 0x0; p[2] = 0x1; p[3]=0xbc; //0x000001bc
	
	p[6] = 0x80;  //indicator
	p[7] = 0x01;  //mark

	p[8] = 0x00; //no descript;
	p[9] = 0x00; //no descript;
	
	len = stream_num * 4;
	p[10] = (len) >> 8;
	p[11] = (len) & 0xff;
	bytes = 12;

	for ( i=0; i<stream_num; i++ )
	{
		p[bytes+i*4]   = stream_type[i];
		p[bytes+i*4+1] = stream_id[i];
		p[bytes+i*4+2] = 0; //no descriptor
		p[bytes+i*4+3] = 0;
	}
	
	bytes += stream_num*4;
	len = bytes + 4 - 6 ;
	p[4] = ((len)>>8) & 0xff;
	p[5] =  (len) & 0xff;

	crc = GetCRC32( (const unsigned char*)p, bytes );

	p[bytes++] = (unsigned char)((crc >> 24) & 0xff);
	p[bytes++] = (unsigned char)(crc >> 16) & 0xff;
	p[bytes++] = (unsigned char)(crc >> 8) & 0xff;
	p[bytes++] = (unsigned char)(crc) &  0xff;

	if ( bytes > 1024 )		return 0;
	PadBuffer( pBuilder, pBuilder->video_buffer[0]+bytes, 1024-bytes );
	PushPESPacket( pBuilder, AllocPESPacket( pBuilder ), 0,  pBuilder->video_buffer[0], 1024 );
	memset( pBuilder->video_data_bytes, 0, sizeof(pBuilder->video_data_bytes) );
	memset( pBuilder->audio_data_bytes, 0, sizeof(pBuilder->audio_data_bytes) );

	return 1024;
}



/*
static bool SeekIFrame( PS_BUILDER* pBuilder, char* pData, int Size )
{
	const unsigned char* ptr;
	if ( SearchMPEG2StartCode( (const unsigned char*)pData, Size, GROUP_START_CODE,	&ptr ) )
	{
		return true;
	} else
	if ( SearchMPEG2StartCode( (const unsigned char*)pData, Size, PICTURE_START_CODE, &ptr ) )
	{
		//check if it's I-Frame
		ptr += 4;
		if ( ( *(ptr+2) & 0x38 ) == 0x08 )
			return true;
	}
	return false;
} */

/*
static char* SeekAC3Start(  char* pData, int Size )
{
	int i;
	for ( i = 0; i<Size; i++ )
		if ( *(pData+i) == 0x0B && *(pData+i+1) == 0x77 )
		{
			return pData+i;
		}
	return NULL;
}
*/
//void DropVideoPSPacket( PS_BUILDER* pBuilder )
//{
//	pBuilder->video_data_bytes = 0;
//	pBuilder->video_PES_start = 0;
//}
//
//void DropAudioPSPacket( PS_BUILDER* pBuilder )
//{
//	pBuilder->audio_data_bytes = 0;
//	pBuilder->audio_PES_start =  0; 
//}

int LookupVideoTrack( PS_BUILDER* pBuilder, int Index, unsigned char StreamType )
{
	int i;
	for ( i = 0; i<MAX_VIDEO_STREAM_NUM; i++ )
	{
		if ( pBuilder->video_track[i].state & 0x8000 && 
			 pBuilder->video_track[i].stream_index == Index && 
			 pBuilder->video_track[i].stream_type == StreamType )
		{
			return i;
		}
	}
	return -1;
}

AV_CONTEXT* GetVideoAVInfo( PS_BUILDER* pBuilder, int Index, unsigned char StreamType )
{
	int i;
	for ( i = 0; i<MAX_VIDEO_STREAM_NUM; i++ )
	{
		if ( pBuilder->video_track[i].state & 0x8000 && 
			 pBuilder->video_track[i].stream_index == Index && 
			 pBuilder->video_track[i].stream_type == StreamType &&
			 pBuilder->video_track[i].av_type > 0 )
		{
			return &pBuilder->video_track[i].av;
		}
	}
	return NULL ;
}

int GetVideoNewStreamId( PS_BUILDER* pBuilder, int Index, unsigned char StreamType )
{
	int i;
	for ( i = 0; i<MAX_VIDEO_STREAM_NUM; i++ )
	{
		if ( pBuilder->video_track[i].state & 0x8000 && 
			 pBuilder->video_track[i].stream_index == Index && 
			 pBuilder->video_track[i].stream_type == StreamType && 
			 pBuilder->video_track[i].av_type > 0 )
		{
			return pBuilder->video_track[i].stream_id;
		}
	}
	return 0 ;
}

int AddNewVideoTrack( PS_BUILDER* pBuilder, int Index, unsigned char  StreamType )
{
	int i;

	if ( pBuilder->totoal_stream > MAX_STREAM_NUM )
		return -1;

		//if want remap main audio video track into first ones 
	if ( pBuilder->remapping_stream )
	{
		if ( Index == pBuilder->main_video_index )
		{
			i = 0;
		}
		else
			i = 2 + pBuilder->totoal_stream++;;
	} else
		i = pBuilder->totoal_stream++;

	if ( i<MAX_VIDEO_STREAM_NUM && i >= 0 )
	{
		pBuilder->video_track[i].stream_index = Index;
		pBuilder->video_track[i].stream_type = StreamType;
		pBuilder->video_track[i].state = 0x8000;
		return i;
	}
	return -1;
}

void SetVideoTrackStreamId( PS_BUILDER* pBuilder, int trk, unsigned StreamId )
{
	//mutiple audio track, stream id have to be unique
	if ( IsVideoStreamId(StreamId) )
	{
		pBuilder->video_track[trk].stream_id = 0xE0 | (trk & 0x0f);
	} else
	{
		pBuilder->video_track[trk].stream_id = StreamId;
	}

}


int LookupAudioTrack( PS_BUILDER* pBuilder, int Index, unsigned char StreamType )
{
	int i;
	for ( i = 0; i<MAX_AUDIO_STREAM_NUM; i++ )
	{
		if ( pBuilder->audio_track[i].state & 0x8000 && 
			 pBuilder->audio_track[i].stream_index == Index && 
			 pBuilder->audio_track[i].stream_type == StreamType )
		{
			return i;
		}
	}
	return -1;
}

AV_CONTEXT* GetAudioAVInfo( PS_BUILDER* pBuilder, int Index, unsigned char StreamType )
{
	int i;
	for ( i = 0; i<MAX_AUDIO_STREAM_NUM; i++ )
	{
		if ( pBuilder->audio_track[i].state & 0x8000 && 
			 pBuilder->audio_track[i].stream_index == Index && 
			 pBuilder->audio_track[i].stream_type == StreamType &&
			 pBuilder->audio_track[i].av_type > 0 )
		{
			return &pBuilder->audio_track[i].av;
		}
	}

	return NULL;

}

int GetAudioNewStreamId( PS_BUILDER* pBuilder, int Index, unsigned char StreamType )
{
	int i;
	for ( i = 0; i<MAX_AUDIO_STREAM_NUM; i++ )
	{
		if ( pBuilder->audio_track[i].state & 0x8000 && 
			 pBuilder->audio_track[i].stream_index == Index && 
			 pBuilder->audio_track[i].stream_type == StreamType &&
			 pBuilder->audio_track[i].av_type > 0 )
		{
			return pBuilder->audio_track[i].stream_id;
		}
	}
	return 0;
}

/*
unsigned short LookupAudioStreamId( PS_BUILDER* pBuilder, int Index, unsigned char StreamType )
{
	int i;
	for ( i = 0; i<MAX_AUDIO_STREAM_NUM; i++ )
	{
		if ( pBuilder->audio_track[i].state & 0x8000 && 
			 pBuilder->audio_track[i].stream_index == Index && 
			 pBuilder->audio_track[i].stream_type == StreamType )
		{
			return  pBuilder->audio_track[i].stream_id;
		}
	}

	return -1;

}
*/

int AddNewAudioTrack( PS_BUILDER* pBuilder, int Index, unsigned char  StreamType )
{
	int i;
	if ( pBuilder->totoal_stream > MAX_STREAM_NUM )
		return -1;
	//if want remap main audio video track into first ones 
	if ( pBuilder->remapping_stream )
	{
		if ( Index == pBuilder->main_audio_index )
		{
			i = 1;
		}
		else
			i = 2 + pBuilder->totoal_stream++;
	} else
		i = pBuilder->totoal_stream++;

	if ( i<MAX_AUDIO_STREAM_NUM && i >= 0 )
	{
		pBuilder->audio_track[i].stream_index = Index;
		pBuilder->audio_track[i].stream_type = StreamType;
		pBuilder->audio_track[i].state = 0x8000;
		return i;
	}
	return -1;
}

unsigned char SetAudioTrackStreamId( PS_BUILDER* pBuilder, int trk, unsigned StreamId )
{
	//multiple audio track, stream id have to be unique
	if ( IsPrivateStreamId(StreamId) )
	{
		pBuilder->audio_track[trk].stream_id = StreamId;
	} else
	if ( IsAudioStreamId(StreamId) )
	{
		pBuilder->audio_track[trk].stream_id = 0xC0 | (trk & 0x1f);
	} else
	{
		pBuilder->audio_track[trk].stream_id = StreamId;
	}

	return pBuilder->audio_track[trk].stream_id;
}

/*
static int _VIDEO_BUF_BYTES( PS_BUILDER* pBuilder, int trk )
{
	if ( pBuilder->video_data_bytes[trk]== 0 )
		return 0;
	return pBuilder->video_buffer[trk] + pBuilder->video_data_bytes[trk] - pBuilder->video_PES_ptr[trk];
}
*/

int OutputVideoData( PS_BUILDER* pBuilder, int Index, unsigned char StreamType, bool PESStart, char* pData, int Size, LONGLONG ref_time )
{
	int ret, bytes, size ;
	char* ptr;
	LONGLONG curPTS = -1, curDTS = -1;

	int trk;

	trk = LookupVideoTrack( pBuilder, Index, StreamType );
	if ( trk< 0 )
	{
		trk = AddNewVideoTrack( pBuilder, Index, StreamType );
	}

	//waiting for video Codec to start process PES packets
	if ( ( pBuilder->video_track[trk].state & 0x01 ) == 0x0 )
	{
		PES_INFO VideoPES={0};
		VideoPES.bGroupStart = PESStart;
		if ( PESStart )
		{
			if (  UnpackPESHeader( (unsigned char*)pData, Size, &VideoPES ) > 0 )
			{
				SetVideoTrackStreamId( pBuilder, trk, VideoPES.StreamId );
				VideoPES.StreamId = pBuilder->video_track[trk].stream_id;
				pBuilder->video_track[trk].av.VideoPES = VideoPES;
			}
		}

		//in case of that video header is not in PES packet, QAM
		pBuilder->video_track[trk].av_type = DecodeAVHeader( &pBuilder->video_track[trk].av, &VideoPES, 
					StreamType,  (const unsigned char*)pData, Size );

		if ( pBuilder->video_track[trk].av_type == 0 || pBuilder->video_track[trk].stream_id == 0 )
			return 0;
	}
	
	pBuilder->video_incoming++;
	ptr = pData;
	size = Size;

	if ( PESStart )
	{
		PES_INFO VideoPES={0};
		VideoPES.bGroupStart = PESStart;
		ret = UnpackPESHeader( (unsigned char*)pData, Size, &VideoPES );
		if ( ret <= 0 )
		{
			printf( "Error PES header %d\n", ret );
			return -1;
		}

		VideoPES.StreamId = pBuilder->video_track[trk].stream_id;
		pBuilder->video_track[trk].av.VideoPES = VideoPES;

		if ( pBuilder->video_data_bytes[trk] != 0 )
		{
			bytes = pBuilder->video_buffer[trk] + pBuilder->video_data_bytes[trk] - pBuilder->video_PES_ptr[trk];
			FillPESLength( pBuilder, pBuilder->video_PES_ptr[trk], bytes );
			bytes = pBuilder->video_data_bytes[trk];
			PadBuffer( pBuilder, pBuilder->video_buffer[trk]+bytes, PACKETSIZE-bytes );
			PushPESPacket( pBuilder, pBuilder->VideoSlot[trk], 0,  pBuilder->video_buffer[trk], PACKETSIZE );

			pBuilder->video_packets++;
			pBuilder->video_data_bytes[trk] = 0;
		}
	
		if ( VideoPES.HasPts )
		{
			curPTS = VideoPES.llPts;
			if ( ref_time && curPTS )
			{
				LONGLONG delta =  curPTS - ref_time/300;
				if ( delta<0 ) delta = - delta;
				if ( delta > 15*90000 )
				{
					char buf[128], buf1[32], buf2[32];
					sprintf( buf, "PSBuild drop video PTS:%s SCR:%s\n", PTS2HexString( curPTS, buf1, sizeof(buf1) ),
						                                          PTS2HexString( ref_time/300, buf2, sizeof(buf2)) );
					logfile( buf );
					curPTS = -1;
				}
			}
		}

		if ( VideoPES.HasDts )
		{
			curDTS = VideoPES.llDts;
			if ( ref_time && curDTS )
			{
				LONGLONG delta =  curDTS - ref_time/300;
				if ( delta<0 ) delta = - delta;
				if ( delta > 15*90000 )
				{
					char buf[128], buf1[32], buf2[32];
					sprintf( buf, "PSBuild drop video DTS:%s SCR:%s\n", PTS2HexString( curDTS, buf1, sizeof(buf1) ),
						                                          PTS2HexString( ref_time/300, buf2, sizeof(buf2)) );
					logfile( buf );
					curDTS = -1;
				}
			}
		}

		ptr += VideoPES.HeaderLen;
		size -=  VideoPES.HeaderLen;
	}

	pBuilder->video_track[trk].state |= 0x01;

	while ( size > 0  )
	{
		if ( pBuilder->video_data_bytes[trk] == 0 )
		{
			BuiderVideoPackPESHeader( pBuilder, trk, ref_time, curPTS, curDTS, ref_time );
			pBuilder->VideoSlot[trk] = AllocPESPacket( pBuilder );
		}

		bytes = PACKETSIZE - pBuilder->video_data_bytes[trk];
		bytes = _MIN( size, bytes );
		memcpy( pBuilder->video_buffer[trk] + pBuilder->video_data_bytes[trk], ptr, bytes );
		pBuilder->video_data_bytes[trk] += bytes;

		size -= bytes;
		ptr += bytes;

		if ( pBuilder->video_data_bytes[trk] >= PACKETSIZE )
		{
			PushPESPacket( pBuilder, pBuilder->VideoSlot[trk], 0,  pBuilder->video_buffer[trk], pBuilder->video_data_bytes[trk] );
			pBuilder->video_data_bytes[trk] = 0;
			pBuilder->video_packets++;
		}
	}

	return 1;
}


//static int debug_stop = 0;
//debug search
//static char stop1[]= {0xa6, 0xb6, 0xc1, 0xaf, 0xc6, 0x72, 0x8b };
//static char stop2[]= {0x6d, 0x01, 0x06, 0x54, 0xea, 0x70, 0x35 };
//static char stop3[]= {0xa1, 0x52, 0x86, 0x96, 0x15, 0x1a, 0xf0, 0xab };
		//if ( search_start( pData, Size, stop1 ) >= 0 )
		//{
		//	pBuilder->av.AudioType = AC3_AUDIO;
		//	pBuilder->audio_stream_type = 0x81;
		//	ret = true;
		//}


int search_start( char* pData, int Size, char* match )
{
	
	int i;
	for ( i = 0; i<Size; i++ )
	{
		if ( !memcmp( pData+i, match, 7 ) )
			return i;
	}
	return -1;
}


int OutputAudioData( PS_BUILDER* pBuilder, int Index, unsigned char StreamType, bool PESStart, char* pData, int Size, LONGLONG ref_time )
{
	int ret, bytes, size ;
	char* ptr;
	LONGLONG curPTS = -1;
	int trk;

	trk = LookupAudioTrack( pBuilder, Index, StreamType );
	if ( trk < 0 )
	{
		trk = AddNewAudioTrack( pBuilder, Index, StreamType );
	}

	//track is full
	if ( trk < 0 )
		return -1;

	//make main audio leading other audio stream for third party decoder pickup right 
	if ( pBuilder->main_audio_index == 0xff )
	{
		if ( Index == pBuilder->main_audio_index )
			pBuilder->main_audio_start = true;
		else
		if ( !pBuilder->main_audio_start )
			return -1;
	}

	//waiting for Audio Codec to start output PES packets
	if ( ( pBuilder->audio_track[trk].state & 0x01 ) == 0x0  )
	{
		PES_INFO AudioPES={0};
		unsigned char new_stream_id;
		int ret;
		AudioPES.bGroupStart = PESStart;

		if ( PESStart  ) 
		{
			ret = UnpackPESHeader( (unsigned char*)pData, Size, &AudioPES );
			if ( ret )
				pBuilder->audio_track[trk].av.AudioPES = AudioPES;

		}

		//if audio type unknow, we need decode header to get it
		if (  pBuilder->audio_track[trk].av_type == 0 )
		{
			new_stream_id = pBuilder->audio_track[trk].av.AudioPES.StreamId;
			ret = DecodeAVHeader( &pBuilder->audio_track[trk].av, &AudioPES, 
										StreamType, (const unsigned char*)pData, Size );
			if ( ret )
			{
				pBuilder->audio_track[trk].av_type = ret;
					
				if (    pBuilder->audio_track[trk].av_type != 0x81 
					 && pBuilder->audio_track[trk].av_type != 0x82 
					 && pBuilder->audio_track[trk].av_type != AAC_STREAM_TYPE  )
				{

					//convert stream with type 6 and mpeg1(mpeg2) audio taged with private stream into standard stream
					//found inn DVB-T broadcasting in Europe(UK, Austrialia)
					if (  StreamType == 6 && IsPrivateStreamId( new_stream_id ) )
						new_stream_id = 0xc0+trk;

					SetAudioTrackStreamId( pBuilder, trk, new_stream_id );
				}
			}
		}

		if ( pBuilder->audio_track[trk].av_type && pBuilder->audio_track[trk].stream_id == 0 
			&& pBuilder->audio_track[trk].av.AudioPES.StreamId != 0 )
		{
			new_stream_id = pBuilder->audio_track[trk].av.AudioPES.StreamId;

			if ( pBuilder->audio_track[trk].av_type == 0x81 )
			{
				memcpy( pBuilder->audio_track[trk].av.PrivateHeader, AC3MagicCode, sizeof(AC3MagicCode) );
				pBuilder->audio_track[trk].av.PrivateHeader[0] = 0x80+trk;  
				//new_stream_id = pBuilder->audio_track[trk].av.AudioPES.StreamId;
				new_stream_id = PRIVATE_STREAM_1; //force to use private stream id, because it has a trouble in HD100/HD200
			}

			//there is not DTS audio in ATSC, probabbly in future in DVB-S/C, 
			if (  pBuilder->audio_track[trk].av_type == 0x82 ) 
			{
				memcpy( pBuilder->audio_track[trk].av.PrivateHeader, DTSMagicCode, sizeof(AC3MagicCode) );
				pBuilder->audio_track[trk].av.PrivateHeader[0] = 0x80+trk;  
				//new_stream_id = pBuilder->audio_track[trk].av.AudioPES.StreamId;
				new_stream_id = PRIVATE_STREAM_1; //force to use private stream id, because it has a trouble in HD100/HD200
			}

			if ( pBuilder->audio_track[trk].av_type == AAC_STREAM_TYPE  )
			{
				new_stream_id = pBuilder->audio_track[trk].av.AudioPES.StreamId;
			}

			SetAudioTrackStreamId( pBuilder, trk, new_stream_id );
		}
		

		if ( pBuilder->audio_track[trk].av_type == 0 ||  pBuilder->audio_track[trk].stream_id  == 0 )
			return 0;
	}

	pBuilder->audio_incoming++;
	ptr = pData;
	size = Size;

	if ( PESStart )
	{
		PES_INFO AudioPES={0};
		AudioPES.bGroupStart = PESStart;
		ret = UnpackPESHeader( (unsigned char*)pData, Size, &AudioPES );
		if ( ret <= 0  )
		{
			printf( "expected a PES header %d\n", ret );
			return -1;
		}

		if ( pBuilder->audio_track[trk].stream_id == 0 ) 
			pBuilder->audio_track[trk].stream_id = AudioPES.StreamId;

		AudioPES.StreamId = pBuilder->audio_track[trk].stream_id;
		pBuilder->audio_track[trk].av.AudioPES = AudioPES;
		if ( AudioPES.HasPts )
		{
			curPTS = AudioPES.llPts;
			if ( ref_time && curPTS )
			{
				LONGLONG delta =  curPTS - ref_time/300;
				if ( delta<0 ) delta = - delta;
				if ( delta > 15*90000 )
				{
					char buf[128], buf1[32], buf2[32];
					sprintf( buf, "PSBuild drop audio PTS:%s SCR:%s\n", PTS2HexString( curPTS, buf1, sizeof(buf1) ),
						                                          PTS2HexString( ref_time/300, buf2, sizeof(buf2)) );
					logfile( buf );
					curPTS = -1;
				}
			}
		}

		ptr +=  AudioPES.HeaderLen;
		size -= AudioPES.HeaderLen;
		if ( pBuilder->audio_data_bytes[trk] != 0 )
		{
			bytes = pBuilder->audio_buffer[trk] + pBuilder->audio_data_bytes[trk] - pBuilder->audio_PES_ptr[trk];

			FillPESLength( pBuilder, pBuilder->audio_PES_ptr[trk], bytes );
			bytes = pBuilder->audio_data_bytes[trk];
			PadBuffer( pBuilder, pBuilder->audio_buffer[trk]+bytes, PACKETSIZE-bytes );
			PushPESPacket( pBuilder, pBuilder->AudioSlot[trk], 1,  pBuilder->audio_buffer[trk], PACKETSIZE );
			pBuilder->audio_packets++;
			pBuilder->audio_data_bytes[trk] = 0;
		}

		BuiderAudioPackPESHeader( pBuilder, trk,  ref_time, curPTS, ref_time );
		pBuilder->AudioSlot[trk] = AllocPESPacket( pBuilder );
		
	}

	//pBuilder->audio_PES_start = true;
	pBuilder->audio_track[trk].state |= 0x01;

	while ( size > 0 )
	{
		if ( pBuilder->audio_data_bytes[trk] == 0 )
		{
			BuiderAudioPackPESHeader( pBuilder, trk, ref_time, curPTS, ref_time  );
			pBuilder->AudioSlot[trk] = AllocPESPacket( pBuilder );
		}

		bytes = PACKETSIZE - pBuilder->audio_data_bytes[trk];
		bytes = _MIN( size, bytes );
		memcpy( pBuilder->audio_buffer[trk] + pBuilder->audio_data_bytes[trk], ptr, bytes );
		pBuilder->audio_data_bytes[trk] += bytes;
		size -= bytes;
		ptr += bytes;

		if ( pBuilder->audio_data_bytes[trk] >= PACKETSIZE )
		{
			PushPESPacket( pBuilder, pBuilder->AudioSlot[trk], 1,  pBuilder->audio_buffer[trk], pBuilder->audio_data_bytes[trk] );
			pBuilder->audio_data_bytes[trk] = 0;
			pBuilder->audio_packets++;
		}
	}

	return 1;
}

////////////////////////////////////////////////////////////////////////////////////////
//for keeping output PES packets in sequential order for SCR, I have to buffer packets for 
//sync video audio packet
////////////////////////////////////////////////////////////////////////////////////////
//static int _max_pending_buf = 0, _max_waiting_buf = 0;
static int AllocPESPacket( PS_BUILDER* pBuilder )
{
	int index, i;

	if ( pBuilder->PacketsInPoolNum >= PES_PACKET_BUFFER_SIZE ) 
	{
		flog(( "native.log", "WRNING: PES out pending pool is full, pes skiped, total:%d ready:%d \n", 
								pBuilder->PacketsInPoolNum, pBuilder->ReadyPacketsInPoolNum ));
		return -1;
	}

	index = pBuilder->PacketQueueTail;

	for ( i = 0; i<PES_PACKET_BUFFER_SIZE; i++ )
	{
		if ( pBuilder->PacketBuffer[index].State == 0 )
			break;
		if ( ++index>=PES_PACKET_BUFFER_SIZE) index = 0 ;
	}

	//there is no empty buffer
	if ( i >= PES_PACKET_BUFFER_SIZE )
	{
		flog(( "native.log", "WRNING: PES out pending pool is full, pes skiped! total:%d ready:%d \n", 
								pBuilder->PacketsInPoolNum, pBuilder->ReadyPacketsInPoolNum ));
		return -1;
	}

	pBuilder->PacketBuffer[index].State = 1;
	pBuilder->PacketBuffer[index].SeqNum = pBuilder->Sequence++;
	pBuilder->PacketsInPoolNum++;
	pBuilder->PacketQueueTail = index;
	if ( ++pBuilder->PacketQueueTail >= PES_PACKET_BUFFER_SIZE ) pBuilder->PacketQueueTail = 0;

//if ( _max_pending_buf < pBuilder->PacketsInPoolNum - pBuilder->ReadyPacketsInPoolNum )
//  _max_pending_buf = pBuilder->PacketsInPoolNum - pBuilder->ReadyPacketsInPoolNum;
//_max_waiting_buf = _max_waiting_buf < pBuilder->PacketsInPoolNum ? pBuilder->PacketsInPoolNum : _max_waiting_buf;

	return index;
}

static bool PushPESPacket( PS_BUILDER* pBuilder, int index, char Type,  char* pPacketData, unsigned long Size )
{
	//int index;
	if ( index < 0 || index >= PES_PACKET_BUFFER_SIZE ) 
		return false;

	if ( Size > PACKETSIZE )
	{
		printf( "PES packet size error ZQ\n");
		return false;
	}

    if ( pBuilder->PacketBuffer[index].State != 1 )
	{
		printf( "assertion failed\n" );
	}
	
	if ( pBuilder->PacketBuffer[index].Packet == NULL )
	{
		int bytes = PACKETSIZE > Size ? PACKETSIZE : Size;
		pBuilder->PacketBuffer[index].Packet = sagetv_malloc( bytes );
		pBuilder->PacketBuffer[index].PacketSize = bytes;
	}
	else
	if ( pBuilder->PacketBuffer[index].PacketSize < Size )
	{
		sagetv_free( pBuilder->PacketBuffer[index].Packet );
		pBuilder->PacketBuffer[index].Packet = sagetv_malloc( Size );
		pBuilder->PacketBuffer[index].PacketSize = Size;
	}

	memcpy( pBuilder->PacketBuffer[index].Packet, pPacketData, Size );
	pBuilder->PacketBuffer[index].Type = Type;
	pBuilder->PacketBuffer[index].DataSize = Size;
	pBuilder->PacketBuffer[index].State = 2;
	pBuilder->ReadyPacketsInPoolNum++;

	if ( pBuilder->builder_dumper != NULL )
		pBuilder->builder_dumper( pBuilder->env, TS_PACKET_LENGTH, pPacketData );

	return true;
}

bool PeekPSPacket( PS_BUILDER* pBuilder, short* pType, char** ppPacketData, unsigned long* pSize )
{
	int i;
	unsigned long bytes, seq;
	int index;

	if (  pBuilder->ReadyPacketsInPoolNum == 0 || pBuilder->PacketsInPoolNum == 0 ) 
		return false;

	index = pBuilder->PacketQueueHead;
	//skip empty buffer
	i = 0;
	while ( pBuilder->PacketBuffer[index].State == 0 && i++ < PES_PACKET_BUFFER_SIZE )
		if ( ++index >= PES_PACKET_BUFFER_SIZE ) index = 0;

	//check if early packet is ready
	seq = pBuilder->PacketBuffer[index].SeqNum;
	for ( i = 0; i<PES_PACKET_BUFFER_SIZE; i++ )
	{
		if ( pBuilder->PacketBuffer[i].State == 2 && pBuilder->PacketBuffer[i].SeqNum < seq )
		{
			index = i;
			break;
		}
	}

	if ( pBuilder->PacketBuffer[index].State == 1 )
	{
		//waiting for a block that is not ready, keep output sequential
		if ( pBuilder->PacketsInPoolNum < PES_PACKET_BUFFER_SIZE-1 )
			return false;
		else
		{
			// a pending packet block too many ready blocks, skip it ( seqence is broken ).
			i = 0;
			while ( pBuilder->PacketBuffer[index].State != 2 && i++ < PES_PACKET_BUFFER_SIZE )
				if ( ++index >= PES_PACKET_BUFFER_SIZE ) index = 0;

			if (  pBuilder->PacketBuffer[index].State != 2 ) 
				return false;
		}
	}

	bytes = _MIN( *pSize, pBuilder->PacketBuffer[index].DataSize );
	*pType = pBuilder->PacketBuffer[index].Type;
	*pSize = bytes;

	if ( ppPacketData != NULL )
		*ppPacketData = (char*)pBuilder->PacketBuffer[index].Packet;

	return true;
}

bool PopPSPacket( PS_BUILDER* pBuilder, short* pType, char* pPacketData, unsigned long *pSize )
{
	int i;
	unsigned long bytes, seq;
	int index;

	if (  pBuilder->ReadyPacketsInPoolNum == 0 || pBuilder->PacketsInPoolNum == 0 ) 
		return false;

	index = pBuilder->PacketQueueHead;
	//skip empty buffer
	i = 0;
	while ( pBuilder->PacketBuffer[index].State == 0 && i++ < PES_PACKET_BUFFER_SIZE )
		if ( ++index >= PES_PACKET_BUFFER_SIZE ) index = 0;
	pBuilder->PacketQueueHead = index;

	//check if early packet is ready
	seq = pBuilder->PacketBuffer[index].SeqNum;
	for ( i = 0; i<PES_PACKET_BUFFER_SIZE; i++ )
	{
		if ( pBuilder->PacketBuffer[i].State == 2 && pBuilder->PacketBuffer[i].SeqNum < seq )
		{
			index = i;
			break;
		}
	}

	if ( pBuilder->PacketBuffer[index].State == 1 )
	{
		//waiting for a block that is not ready, keep output sequential
		if ( pBuilder->PacketsInPoolNum < PES_PACKET_BUFFER_SIZE-1 )
			return false;
		else
		{
			i = 0;
			while ( pBuilder->PacketBuffer[index].State != 2 && i++ < PES_PACKET_BUFFER_SIZE )
				if ( ++index >= PES_PACKET_BUFFER_SIZE ) index = 0;

			if (  pBuilder->PacketBuffer[index].State == 2 ) 
				pBuilder->PacketQueueHead = index;
			else
			{
				// a pending packet block too many ready blocks, skip it ( seqence is broken ).
				flog(( "native.log", "WRNING: skip blocks in PES out pending pool, total:%d ready:%d \n", 
								    pBuilder->PacketsInPoolNum, pBuilder->ReadyPacketsInPoolNum ));
				return false;
			}
		}
	}

	if ( *pSize < pBuilder->PacketBuffer[index].DataSize )
	{
		// a pending packet block too many ready blocks, skip it ( seqence is broken ).
		flog(( "native.log", "WRNING: Buffer is too small to PES popup, data lost! buf size:%d, data size:%d \n", 
								   *pSize, pBuilder->PacketBuffer[index].DataSize ));
	}

	bytes =  *pSize < pBuilder->PacketBuffer[index].DataSize ? *pSize : pBuilder->PacketBuffer[index].DataSize;
	*pType = pBuilder->PacketBuffer[index].Type;
	*pSize = bytes;
	pBuilder->PacketBuffer[index].State = 0;

	if ( pPacketData != NULL )
		memcpy( pPacketData, pBuilder->PacketBuffer[index].Packet, bytes );

	if ( pBuilder->PacketQueueHead == index )
		if ( ++pBuilder->PacketQueueHead >= PES_PACKET_BUFFER_SIZE ) pBuilder->PacketQueueHead = 0;

	pBuilder->PacketsInPoolNum--;
	pBuilder->ReadyPacketsInPoolNum--;
	pBuilder->packets++;     
	return true;

}

bool FlushOutPSPacket( PS_BUILDER* pBuilder, short* pType, char* pPacketData, unsigned long* pSize )
{
	int i;
	unsigned long bytes;
	int index;

	if (  pBuilder->ReadyPacketsInPoolNum == 0 || pBuilder->PacketsInPoolNum == 0 ) 
		return false;

	index = pBuilder->PacketQueueHead;
	//skip empty or not ready buffer
	i = 0;
	while ( pBuilder->PacketBuffer[index].State != 2 && i++ < PES_PACKET_BUFFER_SIZE )
		if ( ++index >= PES_PACKET_BUFFER_SIZE ) index = 0;


	bytes = _MIN( *pSize, pBuilder->PacketBuffer[index].DataSize );
	*pType = pBuilder->PacketBuffer[index].Type;
	*pSize = bytes;
	pBuilder->PacketBuffer[index].State = 0;

	if ( pPacketData != NULL )
		memcpy( pPacketData, pBuilder->PacketBuffer[index].Packet, bytes );

	pBuilder->PacketQueueHead = index;
	if ( ++pBuilder->PacketQueueHead >= PES_PACKET_BUFFER_SIZE ) pBuilder->PacketQueueHead = 0;

	pBuilder->PacketsInPoolNum--;
	pBuilder->ReadyPacketsInPoolNum--;

	pBuilder->packets++;     
	return true;
}

int  GetPSReadyPacketNumInPool( PS_BUILDER* pBuilder )
{
	return pBuilder->ReadyPacketsInPoolNum; 
}


PS_BUILDER* PSBuilderOpen( )
{
	int i;
	PS_BUILDER* pBuilder;
	
	pBuilder = (PS_BUILDER*)sagetv_malloc( sizeof(PS_BUILDER) );

	memset( pBuilder->video_buffer, 0x0, sizeof(pBuilder->video_buffer) );
	memset( pBuilder->audio_buffer, 0x0, sizeof(pBuilder->audio_buffer) );
	memset( pBuilder->video_track, 0x0, sizeof(pBuilder->video_track) );
	memset( pBuilder->audio_track, 0x0, sizeof(pBuilder->audio_track) );
	memset( pBuilder->video_data_bytes, 0, sizeof(pBuilder->video_data_bytes) );
	memset( pBuilder->audio_data_bytes, 0, sizeof(pBuilder->audio_data_bytes) );

	InitOutBits( &pBuilder->bits, pBuilder->bits_buf, sizeof(pBuilder->bits_buf) );

	pBuilder->builder_dumper = NULL;

	pBuilder->video_packets = 0;
	pBuilder->audio_packets = 0;
	pBuilder->video_incoming = 0;
	pBuilder->audio_incoming = 0;
	pBuilder->main_audio_index = -1;
	pBuilder->main_video_index = -1;
	pBuilder->total_audio_num = 0;
	pBuilder->total_video_num = 0;
	pBuilder->main_video_stream_type = 0;
	pBuilder->main_audio_stream_type = 0;
	pBuilder->h264_frame_rate = 0;
	pBuilder->main_audio_start = false;
	pBuilder->packets = 0;	
	pBuilder->env = 0;
	pBuilder->remapping_stream = true;
	pBuilder->totoal_stream = 0;
	pBuilder->lastRefTime = 0;
	pBuilder->PSM_packet_enable = false;  //FFMPEG has trouble with this on for private stream 0xbd, 
		                                  //I disable it by default, but I put PSM into stream for H264 stream even disabled//ZQ.

	pBuilder->PacketsInPoolNum = 0;
	pBuilder->ReadyPacketsInPoolNum = 0;
	pBuilder->Sequence = 0;
	memset( pBuilder->PacketBuffer, 0, sizeof(pBuilder->PacketBuffer) );

	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{	
		pBuilder->VideoSlot[i] = -1;
		pBuilder->AudioSlot[i] = -1;
	}

	_flog_check();
	return pBuilder;
}

void PSBuilderClose( PS_BUILDER* builder )
{
	if ( builder != NULL )
	{
		int i;
		for ( i = 0; i<PES_PACKET_BUFFER_SIZE; i++ )
			if ( builder->PacketBuffer[i].Packet != NULL )
				free( builder->PacketBuffer[i].Packet );

		sagetv_free( builder );
	}
}

void PSResetBuilder( PS_BUILDER* pBuilder )
{
	int i;
	memset( pBuilder->video_buffer, 0x0, sizeof(pBuilder->video_buffer) );
	memset( pBuilder->audio_buffer, 0x0, sizeof(pBuilder->audio_buffer) );
	memset( pBuilder->video_track, 0x0, sizeof(pBuilder->video_track) );
	memset( pBuilder->audio_track, 0x0, sizeof(pBuilder->audio_track) );
	memset( pBuilder->video_data_bytes, 0, sizeof(pBuilder->video_data_bytes) );
	memset( pBuilder->audio_data_bytes, 0, sizeof(pBuilder->audio_data_bytes) );
	InitOutBits( &pBuilder->bits, pBuilder->bits_buf, sizeof(pBuilder->bits_buf) );

	pBuilder->video_packets = 0;
	pBuilder->audio_packets = 0;
	pBuilder->video_incoming = 0;
	pBuilder->audio_incoming = 0;
	pBuilder->main_audio_index = -1;
	pBuilder->main_video_index = -1;
	pBuilder->total_audio_num = 0;
	pBuilder->total_video_num = 0;
	pBuilder->main_video_stream_type = 0;
	pBuilder->main_audio_stream_type = 0;
	pBuilder->h264_frame_rate = 0;
	pBuilder->main_audio_start = false;
	
	pBuilder->packets = 0;	
	pBuilder->totoal_stream = 0;

	pBuilder->PacketsInPoolNum = 0;
	pBuilder->ReadyPacketsInPoolNum = 0;
	pBuilder->Sequence = 0;

	for ( i = 0; i<PES_PACKET_BUFFER_SIZE; i++ )
		pBuilder->PacketBuffer[i].State = 0;

	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{	
		pBuilder->VideoSlot[i] = -1;
		pBuilder->AudioSlot[i] = -1;
	}
}

int  PSGetBlockSize( PS_BUILDER *pBuilder )
{
	if ( pBuilder == NULL )
		return 0;

	return PACKETSIZE;
}
