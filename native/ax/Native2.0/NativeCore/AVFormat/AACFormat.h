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

#ifndef MEPG_AAC_FORMAT_ANALYZER_H
#define MEPG_AAC_FORMAT_ANALYZER_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct AAC_AUDIO
{
	unsigned short channels;
	unsigned short block_align;
	unsigned long  samples_per_sec;
	unsigned long  avgbytes_per_sec;
	unsigned short bits_per_sample;
	unsigned short padding1;

	long  state;   //0: unknow; 1:start, 2:need more data, 3:done;
	long  format;  //1: ADTS, 2:LOAS (DVB)
	short version; //2:mpeg2; 4:mepg4
	short padding2;
	union  {
		unsigned char  atds_header[70];
		unsigned char  latm_header[69];
		unsigned char  padding[80];
	} u;
	unsigned short profile;
	unsigned short object_type;
	long  bitrate;
	unsigned long total_frame_bytes;
	long  frames;
	long  expect_bytes;
	short frame_length;
	short padding3;
} AAC_AUDIO;

int ReadAAC_AudioHeader( AAC_AUDIO *pAACAudio, const unsigned char* pStart, int nSize );
int ReadAACHE_AudioHeader( AAC_AUDIO *pAACAudio, const unsigned char* pStart, int nSize );

#ifdef __cplusplus
}
#endif

#endif
