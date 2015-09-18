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
#include "AC3Format.h"

#define A52_DOLBY 10
#define A52_LFE   16
static int AC3SyncInfo ( uint8_t* buf, int * sample_rate, int * bit_rate, int *channels )
{
    static int rate_tab[] = { 32,  40,  48,  56,  64,  80,  96, 112,
			 128, 160, 192, 224, 256, 320, 384, 448, 512, 576, 640};
    static uint8_t lfeon[8] = {0x10, 0x10, 0x04, 0x04, 0x04, 0x01, 0x04, 0x01};
    static uint8_t halfrate[12] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3};
    static int channels_num[8] = { 2, 1, 2, 3, 3, 4, 4, 5 };

    int flags;
    int frmsizecod;
    int bitrate;
    int half;
    int acmod;
	int bsid;
    //int sync_code;

	/* syncword */
	//sync_code = ( ( buf[0] == 0x0b && buf[1] == 0x77 ) || ( buf[0] == 0x77 && buf[1] == 0x0b ) );
	//if ( !sync_code )
	//	return 0;

	 bsid = buf[5]>>3;

	 //bsid range [0,10] for AC3; (10, 16] for EAC3
	 if ( bsid > 10 )
		 return 0;
	//if ( bsid >= 12 )		/* bsid >= 12 */
	//	return 0;

    half = halfrate[buf[5] >> 3];
    /* acmod, dsurmod and lfeon */
    acmod = buf[6] >> 5;
    flags = ((((buf[6] & 0xf8) == 0x50) ? A52_DOLBY : acmod) |
	                 ((buf[6] & lfeon[acmod]) ? A52_LFE : 0));

    frmsizecod = buf[4] & 0x3f;
    if (frmsizecod >= 38)
		return 0;
    bitrate = rate_tab[frmsizecod >> 1];
    *bit_rate = (bitrate * 1000) >> half;

    *channels = channels_num[ flags & 7 ];
     if ( flags & A52_LFE )
		(*channels)++;

    switch (buf[4] & 0xc0) {
    case 0:	/* 48 KHz */
		*sample_rate = 48000 >> half;
		return 1;  
    case 0x40:	  /*44.1 KHZ */
		*sample_rate = 44100 >> half;
		return 1;  
    case 0x80:	  /* 32 KHZ */
		*sample_rate = 32000 >> half;
		return 1;  
    default:
		return 0;
    }
}


int ReadAC3AudioHeader( AC3_AUDIO *pAC3Audio, const uint8_t* pStart, int nSize )
{
	const uint8_t *p;
	int i;
	int sample_rate, bit_rate, channels;
	p = pStart;

	//search for AC3 SYNC code 
	for ( i = 0; i<nSize-1; i++ )
	{
		if ( ( p[i] == 0x0B && p[i+1] == 0x77 ) ||
			 ( p[i] == 0x77 && p[i+1] == 0x0B ) )
			 break;
	}

	if ( i >= nSize -6 )
		return 0;

	if ( !AC3SyncInfo ( (uint8_t*)p+i, &sample_rate, &bit_rate, &channels ) )
		return 0;

	pAC3Audio->samples_per_sec = sample_rate;
	pAC3Audio->avgbytes_per_sec =  bit_rate;
	pAC3Audio->channels = channels;
	pAC3Audio->block_align = (uint16_t)((pAC3Audio->avgbytes_per_sec * 1536) / pAC3Audio->samples_per_sec);

	return 1;

}


