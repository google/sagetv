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
#define __STDC_FORMAT_MACROS
#include <inttypes.h>
typedef struct AAC_AUDIO
{
	uint16_t channels;
	uint16_t block_align;
	uint32_t samples_per_sec;
	uint32_t avgbytes_per_sec;
	uint16_t bits_per_sample;
	uint16_t padding1;

	int32_t  state;   //0: unknow; 1:start, 2:need more data, 3:done;
	int32_t  format;  //1: ADTS, 2:LOAS (DVB)
	int16_t  version; //2:mpeg2; 4:mepg4
	int16_t  padding2;
	union  {
		uint8_t  atds_header[70];
		uint8_t  latm_header[69];
		uint8_t  padding[80];
	} u;
	uint16_t profile;
	uint16_t object_type;
	int32_t  bitrate;
	uint32_t total_frame_bytes;
	int32_t  frames;
	int32_t  expect_bytes;
	int16_t  frame_length;
	int16_t  padding3;
} AAC_AUDIO;

int ReadAAC_AudioHeader( AAC_AUDIO *pAACAudio, const uint8_t* pStart, int nSize );
int ReadAACHE_AudioHeader( AAC_AUDIO *pAACAudio, const uint8_t* pStart, int nSize );

#ifdef __cplusplus
}
#endif

#endif
