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
#define __STDC_FORMAT_MACROS
#include <inttypes.h>
typedef struct MPEG_VIDEO
{
	uint8_t  extension_present;
	//uint8_t  display_extension_present;	
	//uint8_t  scalable_extension_present;

	int16_t		  profile;			   //  profile - MPEG2 only	
	int16_t	   level;			       //  level - MPEG2 only
	int16_t	   progressive;		   //  progressive flag	
	int16_t	   chrome;			     //  chrome format
	int32_t	   width;			       //  Native width	in pixels
	int32_t	   height;			     //  Native height in	pixels
	int32_t	   vbv;				       //  vbv
	int32_t	   ar_info;			     //  aspect ratio	code from stream
	uint64_t   picture_time;	   //  Time	per	picture	in 100ns units
	float		   picture_rate;		 //  In frames	per	second
	int32_t	   time_per_frame;	   //  Time per picture in MPEG units
	//uint8_t  FrameRateCode;//  4 bits frame rate code
	uint16_t   frame_rate_nomi;
	uint16_t   frame_rate_deno;
	int32_t	   bit_rate;			     //  Bits	per	second
	int16_t		 actual_header_length;	   //  Length of valid bytes in	raw	seq	hdr	
	int16_t	   padding;
	uint8_t    raw_header[150];	   //  The real	sequence header	

} MPEG_VIDEO;

inline static const uint8_t* SearchMPEGStartCode( const uint8_t* pData, int nBytes, uint32_t StartCode )
{
	uint32_t code;

	if ( nBytes < 4 )
		return (unsigned char *)NULL;

	code = 0xffffff00 |*pData++;
	while ( --nBytes )
	{
		if ( code == StartCode )
			return pData - 4;
		code = (( code << 8 )| *pData++ );
	}

	return (unsigned char *)NULL;
}


int Mepg2FrameRateNomiValue( uint8_t code );
int Mepg2FrameRateDenoValue( uint8_t code );
int Mepg2AspectRatioNomiValue( uint8_t code, int32_t width, int32_t height );
int Mepg2AspectRatioDenoValue( uint8_t code, int32_t width, int32_t height );
float Mepg1AspectRatioF( uint8_t code, int32_t width, int32_t height );
float Mepg2AspectRatioF( uint8_t code, int32_t width, int32_t height );
int ReadMpegVideoHeader( MPEG_VIDEO *pMpegVideo, const uint8_t* pStart, int Size );

#ifdef __cplusplus
}
#endif

#endif
