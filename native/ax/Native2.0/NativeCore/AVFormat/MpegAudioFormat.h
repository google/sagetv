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

#ifndef MEPG_MEPG_AUDIO_FORMAT_ANALYZER_H
#define MEPG_MEPG_AUDIO_FORMAT_ANALYZER_H

#ifdef __cplusplus
extern "C" {
#endif

#define	ACM_MPEG_LAYER1				(0x0001)
#define	ACM_MPEG_LAYER2				(0x0002)
#define	ACM_MPEG_LAYER3				(0x0004)
#define	ACM_MPEG_STEREO				(0x0001)
#define	ACM_MPEG_JOINTSTEREO		(0x0002)
#define	ACM_MPEG_DUALCHANNEL		(0x0004)
#define	ACM_MPEG_SINGLECHANNEL		(0x0008)
#define	ACM_MPEG_PRIVATEBIT			(0x0001)
#define	ACM_MPEG_COPYRIGHT			(0x0002)
#define	ACM_MPEG_ORIGINALHOME		(0x0004)
#define	ACM_MPEG_PROTECTIONBIT		(0x0008)
#define	ACM_MPEG_ID_MPEG1			(0x0010)


typedef struct MPEG_AUDIO
{
 	uint16_t  channels;
	uint16_t  block_align;
	uint32_t  samples_per_sec;
	uint32_t  avgbytes_per_sec;
	uint32_t	head_bitrate;

	uint16_t	head_layer;
	uint16_t	head_mode;	
	uint16_t	head_mode_ext;
	uint16_t	head_emphasis;
	uint16_t	head_flags;
	uint16_t  padding1;
	uint16_t  padding2;
	uint16_t  padding3;

} MPEG_AUDIO;

int ReadMpegAudioHeader( MPEG_AUDIO *pMpegAudio, const uint8_t* pStart, int Size );

#ifdef __cplusplus
}
#endif

#endif
