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
#include "MpegVideoFrame.h"

//detecte frame type

#define PICTURE_START_CODE       0x00
#define USER_DATA_START_CODE     0xB2
#define SEQUENCE_HEADER_CODE     0xB3
#define SEQUENCE_ERROR_CODE      0xB4
#define EXTENSION_START_CODE     0xB5
#define SEQUENCE_END_CODE        0xB7
#define GROUP_START_CODE         0xB8

inline uint8_t* SeekMPEG2StartCode( const uint8_t* pData, int nBytes, uint8_t StartCode )
{
	uint32_t code;

	if ( nBytes < 4 )
		return NULL;

	code = 0xffffff00 |*pData++;
	while ( --nBytes )
	{
		if ( ( (code&0x00ffffff)==0x01 ) && *(pData)==StartCode  )
			return (uint8_t* )pData-3;
		code = (( code << 8 )| *pData++ );
	}

	return NULL;
}

int SeekFrameType( const uint8_t* pData, int Size, const uint8_t **ppStart )
{
	const uint8_t* ptr;
	*ppStart = NULL;
	if ( pData == NULL ) return 0;
	if ( (ptr = SeekMPEG2StartCode( (const uint8_t*)pData, Size, PICTURE_START_CODE ) ) != NULL )
	{
		uint8_t picture_coding_type = ( (*(ptr+5)&0x38 )>>3 );
		if ( picture_coding_type == 1 || picture_coding_type == 2 || picture_coding_type == 3 )
		{
			*ppStart = ptr;
			return picture_coding_type;
		}
	}
	return 0;
} 

inline uint8_t* SeekSequenceHeader( const uint8_t* pData, int Size )
{
	return SeekMPEG2StartCode( (const uint8_t*)pData, Size, SEQUENCE_HEADER_CODE );
} 

inline uint8_t* SeekExtensionHeader( const uint8_t* pData, int Size )
{
	return SeekMPEG2StartCode( (const uint8_t*)pData, Size, EXTENSION_START_CODE );
} 

inline uint8_t* SeekGroupHeader( const uint8_t* pData, int Size )
{
	return SeekMPEG2StartCode( (const uint8_t*)pData, Size, GROUP_START_CODE );
} 


int SearchFrameStart( const uint8_t* pData, int nSize, const uint8_t **ppStart )
{
	const uint8_t *seq_header_p, *seq_ext_p, *seq_group_p, *next_p, *frame_p;
	int bytes, frame_type;
	if ( ( seq_header_p = SeekSequenceHeader( pData, nSize ) ) != NULL )
	{
		next_p = seq_header_p+4; bytes = nSize-(int)(next_p-pData);
		if ( ( seq_ext_p = SeekExtensionHeader( next_p, bytes ) ) != NULL )
		{
			next_p = seq_ext_p+4; bytes = nSize-(int)(next_p-pData);
		} 
		if ( ( seq_group_p = SeekGroupHeader( next_p, bytes ) ) != NULL )
		{
			next_p = seq_group_p+4; bytes = nSize-(int)(next_p-pData);

			if ( (frame_type = SeekFrameType( next_p, bytes,  &frame_p ) ) )
			{
				*ppStart = seq_header_p;
				return frame_type;
			}
		}
	}
	return 0;
}
