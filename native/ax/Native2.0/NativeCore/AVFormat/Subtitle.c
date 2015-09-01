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

#include "Subtitle.h"
#define SUBTITLE_DESC				0x59

extern uint8_t* GetDescriptor( const uint8_t *pData, int Bytes, uint8_t Tag, int *pLength );
extern uint32_t LanguageCode( uint8_t* pLanguage );


int ParseDVBSubtitleDesc( SUBTITLE *pSubtitle, uint8_t* pDescDate, int nDescLenghth )
{
	uint8_t* desc_ptr;
	int desc_len;

	if ( ( desc_ptr = GetDescriptor( pDescDate, nDescLenghth, SUBTITLE_DESC, &desc_len ) )!= NULL && desc_len >= 8 )
	{
		pSubtitle->lanugaue = LanguageCode( desc_ptr+2 );
		pSubtitle->comp_type = desc_ptr[2+3];
		pSubtitle->cpgid = ((uint16_t)desc_ptr[2+3+1]<<8)|desc_ptr[2+3+2];
		pSubtitle->apgid = ((uint16_t)desc_ptr[2+3+2+1]<<8)|desc_ptr[2+3+2+1+1];
		return 1;
	} else
	{	
		return 0;
	}
}

int ReadDVBSubtitleHeader( DVB_SUB *pDVBSub, const uint8_t* pStart, int Size )
{
	uint8_t	subtitle_id;
	uint16_t  segmnt_type;	
	uint16_t  page_id;
	uint16_t  segmnt_length;

	//ZQ we may search sync header 0x0f
	if ( pStart[1] != 0x0f )
		return 0;

	subtitle_id = pStart[0];
	segmnt_type = pStart[2];
	page_id = ((uint16_t)pStart[3]<<8)|pStart[4];
	segmnt_length = ((uint16_t)pStart[5]<<8)|pStart[6];
	if ( segmnt_length+7+1 <= Size && pStart[7+segmnt_length] == 0x0f )
	{
		pDVBSub->subtitle_id = subtitle_id;
		pDVBSub->segmnt_type = segmnt_type;
		pDVBSub->page_id = page_id;
		pDVBSub->segmnt_length = segmnt_length;
		pDVBSub->data1 = pStart[7+segmnt_length+1];
		return 1;
	}
	return 0;
}
