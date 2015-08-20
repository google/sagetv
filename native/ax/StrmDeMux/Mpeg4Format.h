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
#ifndef MEPG_MPEG4_FORMAT_ANALYZER_H
#define MEPG_MPEG4_FORMAT_ANALYZER_H
#ifdef __cplusplus
extern "C" {
#endif
typedef struct MPEG_VIDEO_I
{
	unsigned char  extension_present;
	short		   profile;			   //  profile - MPEG2 only	
	short		   level;			   //  level - MPEG2 only
	short		   ver_id;			   // MPEG4 only
	long		   width;			   //  Native width	in pixels
	long		   height;			   //  Native height in	pixels
	long		   vbv;				   //  vbv
	long		   progressive;		   //  progressive flag	
	long		   ar_info;			   //  aspect ratio	code from stream
	long		   frame_num;	   //  Time	per	picture	in 100ns units
	long		   frame_den;	   //  Time	per	picture	in 100ns units
	long		   time_per_frame;	   //  Time per picture in MPEG units
	unsigned short frame_rate_nomi;
	unsigned short frame_rate_deno;
	long		   bit_rate;			   //  Bits	per	second
	short		   actual_header_length;	   //  Length of valid bytes in	raw	seq	hdr	
	unsigned char  raw_header[150];	   //  The real	sequence header	

} MPEG_VIDEO_I;

int ReadMPEG4VideoHeader( MPEG_VIDEO_I *pMpeg4Video, const unsigned char* pStart, int Size );

#ifdef __cplusplus
}
#endif

#endif
