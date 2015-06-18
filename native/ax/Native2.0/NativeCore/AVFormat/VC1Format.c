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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../NativeCore.h"
#include "../Bits.h"
#include "MpegVideoFormat.h"
#include "VC1Format.h"

//VC-1 video
#define VC1_CODE_RES0				    0x00000100
#define VC1_CODE_ENDOFSEQ				0x0000010A
#define VC1_CODE_SLICE					0x0000010B
#define VC1_CODE_FIELD					0x0000010C
#define VC1_CODE_FRAME					0x0000010D
#define VC1_CODE_ENTRYPOINT				0x0000010E
#define VC1_CODE_SEQHDR					0x0000010F

static const int vc1_fps_nr[5] = { 24, 25, 30, 50, 60 };
static const int vc1_fps_dr[2] = { 1000, 1001 };



static int UnpackMPEGVC1SEQHeader( MPEG_VIDEO *pMpegVideo, const unsigned char *pbData, int size )
{
	const unsigned char* p;
	int bits_offset, total_bits;
	unsigned int profile, level, chromaformat, interlace;
	unsigned int coded_width=0, coded_height=0;  
	unsigned int display_width=0, display_height=0;  
	BITS_I bits;
	
	if ( size <= 8 ) return 0;

	p = pbData;
	bits_offset = 0;
	total_bits = ( size - 4 )*8;

	bits.error_flag = 0;
	bits.buffer = p;
	bits.total_bits = ( size - 4 )*8;
	bits.bits_offset = 0;

	profile = ReadBitsU( &bits, 2 );	
	if ( profile == 2 ) //PROFILE_COMPLEX
		return 0; //I don't know WMV9 profile
	else
	if ( profile == 3 ) //PROFILE_ADVANCED
	{
		unsigned int w=0, h=0, ar=0;
		//ADVANCED pofile
		level = ReadBitsU( &bits, 3 );
		if ( level >= 5 )
			return 0; //reserved level
		chromaformat = ReadBitsU( &bits, 2 );
		if ( chromaformat != 1 )
			return 0; //not 4:2:0 chroma format
		pMpegVideo->profile = profile;
		pMpegVideo->level = level;
		SkipBits( &bits, 3+5+1 ); //frmrtq_postproc(3bits) bitrtq_postproc(5bits) postprocflag(1bits)
		coded_width = ReadBitsU( &bits, 12 );
		coded_height = ReadBitsU( &bits, 12 );

		coded_width  = (coded_width+1)*2;
		coded_height = (coded_height+1)*2;

		display_width = coded_width;
		display_height = coded_height;  

		SkipBits( &bits, 1 ); // broadcast
		interlace =  ReadBitsU( &bits, 1 );

		SkipBits( &bits, 1+1 ); //tfcntrflag(1bits) finterpflag(1bits)
		SkipBits( &bits, 1 );   //reserved bit
		SkipBits( &bits, 1 );   //psf

		if ( ReadBitsU( &bits, 1 ) ) //display_info
		{
			display_width = ReadBitsU( &bits, 14 )+1;
			display_height = ReadBitsU( &bits, 14 )+1;
		

			if ( ReadBitsU( &bits, 1 ) ) //ar control
			{
				ar = ReadBitsU( &bits, 4 );

				if ( ar == 0 )
					return 0;

				if ( ar == 15 )
				{
					w = ReadBitsU( &bits, 8 );
					h = ReadBitsU( &bits, 8 );
				} 

				pMpegVideo->ar_info = ar | (w<<8) | (h<<16);
			}

			if ( ReadBitsU( &bits, 1 ) ) //framerate control
			{
				unsigned int frame_rate = 0;
				unsigned int dr = 0, nr = 0;
				if ( ReadBitsU( &bits, 1 ) ) //defined frame_rate
				{
					frame_rate = ReadBitsU( &bits, 16 )+1;
					pMpegVideo->frame_rate_nomi = Mepg2FrameRateNomiValue(frame_rate);
					pMpegVideo->frame_rate_deno = Mepg2FrameRateDenoValue(frame_rate);
				}
				else
				{
					nr = ReadBitsU( &bits, 8 );
					dr = ReadBitsU( &bits, 4 );
					if(nr && nr < 8 && dr && dr < 3)
					{
						nr = vc1_fps_nr[nr - 1];
						dr = vc1_fps_dr[dr - 1];
					}
				}
				//pMpegVideo->FrameRateCode = frame_rate;// | ( nr << 8 ) | ( dr << 16 );
				pMpegVideo->frame_rate_nomi = nr;
				pMpegVideo->frame_rate_deno = dr;
			}

		}
		pMpegVideo->width = display_width;
	    pMpegVideo->height = coded_height;
	} else
	{ //sample, main profile

		if ( ReadBitsU( &bits, 2 ) ) //forbidden 
			return 0;
	
		return 0; //ZQ I don't know how to get frame rate, ar, etc yet.

	}

	return 1;
}

int ReadVC1VideoHeader( MPEG_VIDEO *pMpegVideo, const unsigned char* pStart, int Size )
{
	//Video information always is in header of PES, start indictates start of a PES 	
	const unsigned char* data;
	const unsigned char* start;
	int size;
	const unsigned char *seq_start;
	const unsigned char *entrypoint_start;
	const unsigned char *frame_start;
	const unsigned char *seqend_start;

	data = pStart;
	size = Size;

	start = SearchMPEGStartCode( data, size, VC1_CODE_SEQHDR );
	if ( start == NULL )	
	{
		return 0;
	}
	seq_start = start+4;
	if ( Size > (start - pStart) + 12 )
	{
		data = start;
		size = Size - (int)(start - pStart);
		entrypoint_start = SearchMPEGStartCode( data, size, VC1_CODE_ENTRYPOINT );
		frame_start	 = SearchMPEGStartCode( data, size, VC1_CODE_FRAME );
		seqend_start = SearchMPEGStartCode( data, size, VC1_CODE_ENDOFSEQ );
		if ( frame_start == NULL || entrypoint_start == NULL )
			return 0;

		size = Size - (int)(seq_start - pStart);
		if ( UnpackMPEGVC1SEQHeader( pMpegVideo, seq_start, size ) )
		{
			seq_start -= 4;
			size = (int)(frame_start - seq_start);
			if ( size > 0 && size <sizeof(pMpegVideo->raw_header) )
			{
				memcpy( pMpegVideo->raw_header, seq_start, size );
				pMpegVideo->actual_header_length = size;
			}
			return 1;
		}
	} 

	return 0;	
}


////////////////////////////////////////////////////////////////////////////////////////////

//////////////////////////////////////////////////////////////////





