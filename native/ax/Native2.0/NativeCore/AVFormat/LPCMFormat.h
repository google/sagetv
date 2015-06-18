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

#ifndef MEPG_LPCM_FORMAT_ANALYZER_H
#define MEPG_LPCM_FORMAT_ANALYZER_H
#ifdef __cplusplus
extern "C" {
#endif

typedef struct LPCM_AUDIO
{
	unsigned short lpcm_source; //1: vob file; 2:dvd; 3:blueray;
	unsigned short channels;
	unsigned short block_align;
	unsigned short bits_per_sample;
	unsigned long  samples_per_sec;
	unsigned long  avgbytes_per_sec;
	unsigned long  channel_cfg;
	unsigned short header_size;
	unsigned short padding1;
	unsigned short padding2;
	unsigned short padding3;
} LPCM_AUDIO;

int ReadLPCM_AudioHeader( LPCM_AUDIO *pLPCMAudio, const unsigned char* pStart, int nSize );
void *OpenLPCMConvert( LPCM_AUDIO *pLPCMAudio );
void CloseLPCMConvert( void* pConverter );
void ResetLPCMConvert( void* pConverter );
int LPCMConvert( void* pConverter, int bGroupStart, const unsigned char* pInData, int nSize, unsigned char* pOutData, int nBufSize );
#ifdef __cplusplus
}
#endif


#endif
