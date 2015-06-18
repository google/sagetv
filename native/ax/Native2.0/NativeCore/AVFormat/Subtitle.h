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


typedef struct _SUBTITLE
{
	unsigned short type; //1:DVB subtitle; 2:PGS
	unsigned char  comp_type;
	unsigned char  padding1;
	unsigned long  lanugaue;
	unsigned short cpgid;
	unsigned short apgid;

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

	unsigned short  segmnt_type;	
	unsigned short  page_id;
	unsigned short  segmnt_length;
	unsigned short  cmd;
	unsigned char   subtitle_id;
	unsigned char   data1; //zq test use
} DVB_SUB;

int ParseDVBSubtitleDesc( SUBTITLE *pSubtitle, unsigned char* pDescDate, int nDescLenghth );
int ReadDVBSubtitleHeader( DVB_SUB *pDVBSub, const unsigned char* pStart, int Size );

#ifdef __cplusplus
}
#endif


#endif
