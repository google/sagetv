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
	uint16_t lpcm_source; //1: vob file; 2:dvd; 3:blueray;
	uint16_t channels;
	uint16_t block_align;
	uint16_t bits_per_sample;
	uint32_t samples_per_sec;
	uint32_t avgbytes_per_sec;
	uint32_t channel_cfg;
	uint16_t header_size;
	uint16_t padding1;
	uint16_t padding2;
	uint16_t padding3;
} LPCM_AUDIO;

int ReadLPCM_AudioHeader( LPCM_AUDIO *pLPCMAudio, const uint8_t* pStart, int nSize );
void *OpenLPCMConvert( LPCM_AUDIO *pLPCMAudio );
void CloseLPCMConvert( void* pConverter );
void ResetLPCMConvert( void* pConverter );
int LPCMConvert( void* pConverter, int bGroupStart, const uint8_t* pInData, int nSize, uint8_t* pOutData, int nBufSize );
#ifdef __cplusplus
}
#endif


#endif
