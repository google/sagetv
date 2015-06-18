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

#ifndef MEPG_VIDEO_FORMAT_ANALYZER_H
#define MEPG_VIDEO_FORMAT_ANALYZER_H
#ifdef __cplusplus
extern "C" {
#endif

typedef struct MPEG_VIDEO
{
	unsigned char  extension_present;
	//unsigned char  display_extension_present;	
	//unsigned char  scalable_extension_present;

	short		   profile;			   //  profile - MPEG2 only	
	short		   level;			   //  level - MPEG2 only
	short		   progressive;		   //  progressive flag	
	short		   chrome;			   //  chrome format
	long		   width;			   //  Native width	in pixels
	long		   height;			   //  Native height in	pixels
	long		   vbv;				   //  vbv
	long		   ar_info;			   //  aspect ratio	code from stream
	ULONGLONG	   picture_time;	   //  Time	per	picture	in 100ns units
	float		   picture_rate;		   //  In frames	per	second
	long		   time_per_frame;	   //  Time per picture in MPEG units
	//unsigned char  FrameRateCode;     //  4 bits frame rate code
	unsigned short frame_rate_nomi;
	unsigned short frame_rate_deno;
	long		   bit_rate;			   //  Bits	per	second
	short		   actual_header_length;	   //  Length of valid bytes in	raw	seq	hdr	
	short		   padding;
	unsigned char  raw_header[150];	   //  The real	sequence header	

} MPEG_VIDEO;

inline static const unsigned char* SearchMPEGStartCode( const unsigned char* pData, int nBytes, unsigned long StartCode )
{
	unsigned long code;

	if ( nBytes < 4 )
		return NULL;

	code = 0xffffff00 |*pData++;
	while ( --nBytes )
	{
		if ( code == StartCode )
			return pData - 4;
		code = (( code << 8 )| *pData++ );
	}

	return NULL;
}


int Mepg2FrameRateNomiValue( unsigned char code );
int Mepg2FrameRateDenoValue( unsigned char code );
int Mepg2AspectRatioNomiValue( unsigned char code, long width, long height );
int Mepg2AspectRatioDenoValue( unsigned char code, long width, long height );
float Mepg1AspectRatioF( unsigned char code, long width, long height );
float Mepg2AspectRatioF( unsigned char code, long width, long height );
int ReadMpegVideoHeader( MPEG_VIDEO *pMpegVideo, const unsigned char* pStart, int Size );

#ifdef __cplusplus
}
#endif

#endif
