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
	unsigned short profile, level;
	unsigned short constraint_set0;
	unsigned short constraint_set1;
	unsigned short constraint_set2;
	unsigned short width, height;
	unsigned short progressive; //PAFF
	unsigned short padding1;
	unsigned short padding2;
	unsigned long  sps_id;
	unsigned long  cbr;
	unsigned long  frame_rate_nomi;
	unsigned long  frame_rate_deno;
	unsigned short ar_nomi;
	unsigned short ar_deno;
	unsigned short sps_length;
	unsigned short padding;
	unsigned char  sps[256];
	int guessH264; //-100, 100, 0: is unsure, 100 sure is H264 
} H264_VIDEO;

float H264AspectRatioF( unsigned short nomi, unsigned short deno, long width, long height );
int H264AspectRatioDenoValue( unsigned short nomi, unsigned short deno, long width, long height );
int H264AspectRatioNomiValue( unsigned short nomi, unsigned short deno, long width, long height );

int ReadH264VideoHeader( H264_VIDEO *pH264Video, const unsigned char* pStart, int Size );

#ifdef __cplusplus
}
#endif

#endif
