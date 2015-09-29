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

#ifndef MEPG_SUBTITLE_FORMAT_ANALYZER_H
#define MEPG_SUBTITLE_FORMAT_ANALYZER_H

#define	DVB_SUBTITLE		1
#define PGS_SUBTITLE		2

#ifdef __cplusplus
extern "C" {
#endif

#define __STDC_FORMAT_MACROS
#include <inttypes.h>

typedef struct _SUBTITLE
{
	uint16_t type; //1:DVB subtitle; 2:PGS
	uint8_t  comp_type;
	uint8_t  padding1;
	uint32_t lanugaue;
	uint16_t cpgid;
	uint16_t apgid;

} SUBTITLE;

//segment type
#define PAGE_COMPOSITION_SEGMENT	0x10
#define REGION_COMPOSITION_SEGMENT	0x11
#define CLUT_DEFINITION_SEGMENT		0x12
#define OBJECT_DATA_SEGMENT			0x13
#define DISPLAY_DEFINITION_SEGMENT  0x14
#define END_OF_DISPLAY_SET_SEGMENT  0x80
typedef struct _DVB_SUB
{

	uint16_t  segmnt_type;	
	uint16_t  page_id;
	uint16_t  segmnt_length;
	uint16_t  cmd;
	uint8_t   subtitle_id;
	uint8_t   data1; //zq test use
} DVB_SUB;

int ParseDVBSubtitleDesc( SUBTITLE *pSubtitle, uint8_t* pDescDate, int nDescLenghth );
int ReadDVBSubtitleHeader( DVB_SUB *pDVBSub, const uint8_t* pStart, int Size );

#ifdef __cplusplus
}
#endif


#endif
