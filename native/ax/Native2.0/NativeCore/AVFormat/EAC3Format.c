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
#include "EAC3Format.h"


static int EAC3SyncInfo ( uint8_t* buf, int * sample_rate, int * bit_rate, int *channels )
{
	static int blocks[4] = { 1, 2, 3, 6 };
	static int samplerate[3] = { 48000, 44100, 32000 };
	static int channel_num[8] = { 2, 1, 2, 3, 3, 4, 4, 5 };
	int frame_size;
    int frmsizecod;
	int numblkscod;
    int samperate;
	int head_size;
    int acmod;
	int lfeon;
	int bsid;
    //int sync_code;

	/* syncword */
	//sync_code = ( ( buf[0] == 0x0b && buf[1] == 0x77 ) || ( buf[0] == 0x77 && buf[1] == 0x0b ) );
	//if ( !sync_code )
	//	return 0;

	 bsid = buf[5]>>3;

	 //bsid range [0,10] for AC3; (10, 16] for EAC3
	 if ( bsid <= 10 || bsid > 16 )
		 return 0;

	//5 bits skip, 2 bits stream_type, 3 bits substream_id

	//11 bits frame_size
	frame_size = (( buf[2] & 0x07 )<<3) | buf[3];
	if ( frame_size < 2 )
		return 0;
	head_size = (frame_size+1)*2;

	//4 bits frame size code sample rate
	frmsizecod = (buf[4]>>6)&0x03;
	if ( frmsizecod == 0x03 )
	{
		frmsizecod = (buf[4]>>4)&0x03;
		if ( frmsizecod == 0x03 )
			return 0;
		numblkscod = 6;
		samperate = samplerate[ frmsizecod ] / 2;
	} else
	{
		numblkscod = (buf[4]>>4)&0x03;
		numblkscod = blocks[ numblkscod ];
		samperate = samplerate[ frmsizecod ];
	}

	//4 channel config
	acmod = ( buf[4] >> 1 )& 0x07;
	lfeon = buf[4] & 0x01;
	*channels = channel_num[acmod] + lfeon;

	*bit_rate = 8 * head_size * (samperate) / (numblkscod * 256);
	*sample_rate = samperate;
	return 1;
}


int ReadEAC3AudioHeader( EAC3_AUDIO *pEAC3Audio, const uint8_t* pStart, int nSize )
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

	if ( !EAC3SyncInfo ( (uint8_t*)p+i, &sample_rate, &bit_rate, &channels ) )
		return 0;

	pEAC3Audio->samples_per_sec = sample_rate;
	pEAC3Audio->avgbytes_per_sec =  bit_rate >> 3;
	pEAC3Audio->channels = channels;
	pEAC3Audio->block_align = (uint16_t)((pEAC3Audio->avgbytes_per_sec * 1536) / pEAC3Audio->samples_per_sec);

	return 1;

}
