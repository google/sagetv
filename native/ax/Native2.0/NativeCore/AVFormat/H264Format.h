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

#ifndef MEPG_H264_FORMAT_ANALYZER_H
#define MEPG_H264_FORMAT_ANALYZER_H
#ifdef __cplusplus
extern "C" {
#endif

typedef struct H264_VIDEO
{
	uint16_t profile, level;
	uint16_t constraint_set0;
	uint16_t constraint_set1;
	uint16_t constraint_set2;
	uint16_t width, height;
	uint16_t progressive; //PAFF
	uint16_t padding1;
	uint16_t padding2;
	uint32_t sps_id;
	uint32_t cbr;
	uint32_t frame_rate_nomi;
	uint32_t frame_rate_deno;
	uint16_t ar_nomi;
	uint16_t ar_deno;
	uint16_t sps_length;
	uint16_t padding;
	uint8_t  sps[256];
	int32_t  guessH264; //-100, 100, 0: is unsure, 100 sure is H264 
} H264_VIDEO;

float H264AspectRatioF( uint16_t nomi, uint16_t deno, int32_t width, int32_t height );
int H264AspectRatioDenoValue( uint16_t nomi, uint16_t deno, int32_t width, int32_t height );
int H264AspectRatioNomiValue( uint16_t nomi, uint16_t deno, int32_t width, int32_t height );

int ReadH264VideoHeader( H264_VIDEO *pH264Video, const uint8_t* pStart, int Size );

#ifdef __cplusplus
}
#endif

#endif
