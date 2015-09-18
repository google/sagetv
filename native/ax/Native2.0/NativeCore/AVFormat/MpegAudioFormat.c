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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../NativeCore.h"
#include "MpegAudioFormat.h"


/*  Bit rate tables */
static const int MpegBitRateTbl[2][3][16]	=
{
	{	// MPEG 1	
		{	0, 32,	64,	 96,  128, 160,	192, 224, 256, 288,	320, 352, 384, 416,	448, 0 },
		{	0, 32,	48,	 56,   64,	80,	 96, 112, 128, 160,	192, 224, 256, 320,	384, 0 },
		{	0, 32,	40,	 48,   56,	64,	 80,  96, 112, 128,	160, 192, 224, 256,	320, 0 }
	},
	{	// MPEG 2 ( 2.5)		
		{	0, 32,	48,	56,	64,	80,	96,	112,	128,	144,	160,	176,  192,  224,  256,},// Layer1
		{	0,	8,	16,	24,	32,	40,	48,	56,		64,		80,		96,		112,  128,  144,  160,},// Layer2
		{	0,	8,	16,	24,	32,	40,	48,	56,		64,		80,		96,		112,  128,  144,  160,}	// Layer3
	}
};

static const int SamplingRateTbl[2][3] = 
{ 
	{44100, 48000, 32000  },	// MPEG 1
	{22050, 24000, 16000, }		// MPEG 2
};

int ReadMpegAudioHeader( MPEG_AUDIO *pMpegAudio, const uint8_t* pStart, int Size )
{
	int Layer,i,MPGVersion, CRC_protected, BiteRateIndex, SampleRateIndex;
	int8_t LayerCode;
	const int8_t *pData;
	uint32_t Bitrate;

	pData = pStart;

	//search for SYNC bits (12bits of 1) of Mpeg 4 bytes audio header
	//11 bits 1 if we supports MPEG-2.5 , I will add it suport in later versuin  ZQ. 
	for ( i = 0; i<Size-2; i++, pData++ ) 
		if ( *pData == 0xff && ( *(pData+1) & 0xf0 ) == 0xf0 && ( *(pData+2) & 0xf0 ) != 0xf0  )
			break;
	if ( i>Size-4 )
		return 0;

	//verify if it's a vaild header
	if (((*(pData+2)	>> 2) &	3) == 3)	//Invalid sample rate
		return 0;
	if ((*(pData+2) >> 4) ==	0x0F)		//Invalid bit rate
		return 0;
	if ((*(pData+2) & 0x0C) == 0x0C)		//Invalid audio	sampling frequency
		return 0;

	MPGVersion = ((*(pData+1) >> 3) &0x03);
	if ( MPGVersion == 0 || MPGVersion == 1 )
		return 0;
	if ( MPGVersion == 0x02 ) MPGVersion = 2; //MPEG1
	if ( MPGVersion == 0x03 ) MPGVersion = 1; //MPEG2

	/*	Get	the	layer so we	can	work out the bit rate */
	LayerCode	= ((*(pData+1) >> 1) & 0x03);	
	switch ( LayerCode ) {
		case 3:	
			pMpegAudio->head_layer	= ACM_MPEG_LAYER1;
			Layer =	1;
			break;
		case 2:	
			pMpegAudio->head_layer	= ACM_MPEG_LAYER2;
			Layer =	2;
			break;
		case 1:	
			pMpegAudio->head_layer	= ACM_MPEG_LAYER3;
			Layer =	3;
			break;
		case 0:	
			return 0;
		default:
			return 0;
	}

	CRC_protected = (*(pData+1) & 0x1 );

		/*	Get	samples	per	second from	sampling frequency */
	SampleRateIndex = (*(pData+2) >> 2)	& 3;
	if ( SampleRateIndex	== 3 ) return 0;
	pMpegAudio->samples_per_sec	= SamplingRateTbl[MPGVersion-1][SampleRateIndex];
	
	BiteRateIndex = ((*(pData+2)	>> 4 ) & 0x0f);
	if ( BiteRateIndex == 0x0f ) return 0;
	Bitrate = (uint32_t )MpegBitRateTbl[MPGVersion-1][Layer - 1][BiteRateIndex] * 1000;
	if ( pMpegAudio->samples_per_sec	!= 44100 &&	
		/*	Layer 3	can	sometimes switch bitrates */
		!(Layer	== 3 &&	/* !m_pStreamList->AudioLock() && */
			(*(pData+2) >> 4) ==	0))	{

		if (Layer == 1)	{
			pMpegAudio->block_align	= (uint16_t)(4 * ((Bitrate * 12) / pMpegAudio->samples_per_sec));
		} else {
			pMpegAudio->block_align	= (uint16_t)((144 * Bitrate) / pMpegAudio->samples_per_sec);
		}
	} else {
		pMpegAudio->block_align	= 1;
	}


	/*	Get	number of channels from	Mode */	
	switch (*(pData+3) >> 6)	{
	case 0x00:
		pMpegAudio->head_mode = ACM_MPEG_STEREO;
		break;
	case 0x01:
		pMpegAudio->head_mode = ACM_MPEG_JOINTSTEREO;
		break;
	case 0x02:
		pMpegAudio->head_mode = ACM_MPEG_DUALCHANNEL;
		break;
	case 0x03:
		pMpegAudio->head_mode = ACM_MPEG_SINGLECHANNEL;
		break;
	}
	pMpegAudio->channels =	
		(uint16_t)(pMpegAudio->head_mode == ACM_MPEG_SINGLECHANNEL	? 1	: 2);
	pMpegAudio->head_mode_ext =	(uint16_t)(1 <<	(*(pData+3) >> 4));
	pMpegAudio->head_emphasis =	(uint16_t)((*(pData+3) &	0x03) +	1);	
	pMpegAudio->head_flags	  =	(uint16_t)(((*(pData+2) & 1)	? ACM_MPEG_PRIVATEBIT :	0) +
						   ((*(pData+3) & 8)	? ACM_MPEG_COPYRIGHT : 0) +	
						   ((*(pData+3) & 4)	? ACM_MPEG_ORIGINALHOME	: 0) +
						   ((*(pData+1) & 1)	? ACM_MPEG_PROTECTIONBIT : 0) +	
						   ((*(pData+1) & 0x08) ? ACM_MPEG_ID_MPEG1 : 0));




	pMpegAudio->head_bitrate =	Bitrate * pMpegAudio->channels;	
	pMpegAudio->avgbytes_per_sec	= Bitrate/8; //av->MpegWav.dwHeadBitrate;


	return 1;
}






	//pMpegAudio->samples_per_sec;
	//;
	//pMpegAudio->block_align;

	//pMpegAudio->head_layer;
	//pMpegAudio->head_bitrate;
	//pMpegAudio->head_mode;	
	//pMpegAudio->head_mode_ext;
	//pMpegAudio->head_emphasis;
	//pMpegAudio->head_flags;
	//pMpegAudio->pts_low;
	//pMpegAudio->pts_high;
