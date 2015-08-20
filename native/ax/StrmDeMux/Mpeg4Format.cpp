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
#include "Bits.h"
#include "Mpeg4Format.h"

#define VOS_STARTCODE        0xB0
#define USER_DATA_STARTCODE  0xB2
#define GOP_STARTCODE        0xB3
#define VISUAL_OBJ_STARTCODE 0xB5
#define VOP_STARTCODE        0xB6


//we just data to check MEPG4 type and version
int ReadMPEG4VideoHeader( MPEG_VIDEO_I *pMpeg4Video, const unsigned char* pStart, int Size )
{
	const unsigned char* p=pStart;
	int i;
	int vol_type, profile_level=-1, vo_ver_id=-1;
	for ( i = 0; i<Size-4;i++ )
	{
		if ( p[i] == 0 && p[i+1] == 0 && p[i+2] == 0x01 )
		{
			if ( p[i+3] == VOS_STARTCODE )
			{
				profile_level = p[i+4];
			} else
			if ( p[i+3] == VISUAL_OBJ_STARTCODE )
			{
				BITS_I bits={0};
				bits.buffer =  p+i+4;
				bits.total_bits = (Size-i-4)*8;;
				if ( ReadBitsU( &bits, 1 ) )
				{
					 vo_ver_id = ReadBitsU( &bits, 4 ); //obj layer ver id 
					SkipBits( &bits, 1 );//obj layer priority
				}
			} else
			if ( p[i+3] >= 0x20 &&  p[i+3] <= 0x2f )
			{
				BITS_I bits={0};
				bits.buffer = p+i+4;
				bits.total_bits = (Size-i-4)*8;
				ReadBitsU( &bits, 1 ); //random access
				vol_type = ReadBitsU( &bits, 8 );
				if ( ReadBitsU( &bits, 1 ) )
				{
					vo_ver_id = ReadBitsU( &bits, 4 ); //obj layer ver id 
					SkipBits( &bits, 1 );//obj layer priority
				}
			} 

		}
	}

	pMpeg4Video->ver_id = vo_ver_id;
	pMpeg4Video->profile = profile_level;

	if ( vo_ver_id == -1 && profile_level == -1 )
		return 0;


	return 1;
}

