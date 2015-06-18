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

#ifndef MEPG_EAC3_FORMAT_ANALYZER_H
#define MEPG_EAC3_FORMAT_ANALYZER_H
#ifdef __cplusplus
extern "C" {
#endif

typedef struct EAC3_AUDIO
{
	unsigned short channels;
	unsigned short block_align;
	unsigned long  samples_per_sec;
	unsigned long  avgbytes_per_sec;
} EAC3_AUDIO;

int ReadEAC3AudioHeader( EAC3_AUDIO *pEAC3Audio, const unsigned char* pStart, int Size );

#ifdef __cplusplus
}
#endif

#endif
