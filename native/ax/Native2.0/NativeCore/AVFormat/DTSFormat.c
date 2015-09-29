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
#include "DTSFormat.h"

#define DTS_HEADER_SIZE 14

static const uint32_t dts_samplerate[] =
{
    0, 8000, 16000, 32000, 0, 0, 11025, 22050, 44100, 0, 0,
    12000, 24000, 48000, 96000, 192000
};

static const uint32_t dts_bitrate[] =
{
    32000, 56000, 64000, 96000, 112000, 128000,
    192000, 224000, 256000, 320000, 384000,
    448000, 512000, 576000, 640000, 768000,
    896000, 1024000, 1152000, 1280000, 1344000,
    1408000, 1411200, 1472000, 1536000, 1920000,
    2048000, 3072000, 3840000, 1/*open*/, 2/*variable*/, 3/*lossless*/
};

static void LittleEndian( uint8_t *p_out, const uint8_t *p_in, int i_in )
{
    int i;
    for( i = 0; i < i_in/2; i++  )
    {
        p_out[i*2] = p_in[i*2+1];
        p_out[i*2+1] = p_in[i*2];
    }
}

static int LittleEndian14( uint8_t *p_out, const uint8_t *p_in, int i_in, int i_le )
{
    uint8_t tmp, cur = 0;
    int bits_in, bits_out = 0;
    int i, i_out = 0;

    for( i = 0; i < i_in; i++  )
    {
        if( i%2 )
        {
            tmp = p_in[i-i_le];
            bits_in = 8;
        }
        else
        {
            tmp = p_in[i+i_le] & 0x3F;
            bits_in = 8 - 2;
        }

        if( bits_out < 8 )
        {
            int need = _MIN( 8 - bits_out, bits_in );
            cur <<= need;
            cur |= ( tmp >> (bits_in - need) );
            tmp <<= (8 - bits_in + need);
            tmp >>= (8 - bits_in + need);
            bits_in -= need;
            bits_out += need;
        }

        if( bits_out == 8 )
        {
            p_out[i_out] = cur;
            cur = 0;
            bits_out = 0;
            i_out++;
        }

        bits_out += bits_in;
        cur <<= bits_in;
        cur |= tmp;
    }

    return i_out;
}

static int UnpackDTSInfo( DTS_AUDIO *pDTSAudio, const uint8_t *buf )
{
    uint32_t audio_mode;
    uint32_t sample_rate;
    uint32_t bit_rate;
    uint32_t frame_length;
	  uint32_t pcm_resolution;
	  uint32_t channels;

    uint32_t frame_size;
    uint32_t lfe;

    frame_length = ((buf[4] & 0x01) << 6) | (buf[5] >> 2);
    frame_size = ((buf[5] & 0x03) << 12) | (buf[6] << 4) | (buf[7] >> 4);
    audio_mode = ((buf[7] & 0x0f) << 2) | (buf[8] >> 6);
    sample_rate = (buf[8] >> 2) & 0x0f;
    bit_rate = ((buf[8] & 0x03) << 3) | ((buf[9] >> 5) & 0x07);
	  pcm_resolution = ((buf[13] & 0x01)<<2) | ((buf[14] >> 6 ) & 0x03);

    if( sample_rate >= sizeof( dts_samplerate ) /sizeof( dts_samplerate[0] ) )
		return 0;

    sample_rate = dts_samplerate[ sample_rate ];
    if( sample_rate == 0 ) return 0;

    if( bit_rate >= sizeof( dts_bitrate ) / sizeof( dts_bitrate[0] ) )
        return 0;
    bit_rate = dts_bitrate[ bit_rate ];

    if( bit_rate == 0 ) return 0;

    lfe = ( buf[10] >> 1 ) & 0x03;
    if( lfe ) audio_mode |= 0x10000;

    switch( audio_mode & 0xFFFF )
    {
        case 0x0:      /* Mono */
			channels = 1;
            break;
        case 0x1:      /* Dual-mono = stereo + dual-mono */
			channels = 3;
            break;
        case 0x2:
        case 0x3:
        case 0x4:         /* Stereo */
            channels = 2;
            break;
        case 0x5:          /* 3F */
            channels = 3;
            break;
        case 0x6:          /* 2F/1R */
            channels = 3;
            break;
        case 0x7:         /* 3F/1R */
            channels = 4;
            break;
        case 0x8:         /* 2F2R */
            channels = 4;
            break;
        case 0x9:           /* 3F2R */
            channels = 5;
            break;
        case 0xA:
        case 0xB:          /* 2F2M2R */
            channels = 6;
            break;
        case 0xC:          /* 3F2M2R */
            channels = 7;
            break;
        case 0xD:
        case 0xE:          /* 3F2M2R/LFE */
            channels = 8;
            break;

        default:  
            if( audio_mode <= 63 )
            {
                /* User defined */
                channels = 0;
            }
            else return 0;
            break;
    }

    if( audio_mode & 0x10000 )
        channels++;

	pDTSAudio->channels = channels;
	pDTSAudio->samples_per_sec = sample_rate;
	pDTSAudio->avgbytes_per_sec = bit_rate/8;
	pDTSAudio->block_align = 0;
	pDTSAudio->bits_per_sample = 16;

    return 1;
}


int ReadDTS_AudioHeader( DTS_AUDIO *pDTSAudio, const uint8_t* pbData, int Size )
{
	int i, ret=0;
	const uint8_t* pData = pbData;
  uint8_t buf[DTS_HEADER_SIZE];

	if ( Size < DTS_HEADER_SIZE )
		return 0;

	for ( i = 0; i<Size-DTS_HEADER_SIZE; i++  )
	{
		/* 14 bits,Little endian version of the bitstream */
		if( pData[i+0] == 0xff && pData[i+1] == 0x1f &&
			pData[i+2] == 0x00 && pData[i+3] == 0xe8 &&
			(pData[i+4] & 0xf0) == 0xf0 && pData[i+5] == 0x07 )
		{
			LittleEndian14( buf, pData+i, DTS_HEADER_SIZE, 1 );
			ret = UnpackDTSInfo( pDTSAudio, buf );
		}
		/* 14 bits, Big Endian version of the bitstream */
		else if( pData[i+0] == 0x1f && pData[i+1] == 0xff &&
				 pData[i+2] == 0xe8 && pData[i+3] == 0x00 &&
				 pData[i+4] == 0x07 && (pData[i+5] & 0xf0) == 0xf0 )
		{
			LittleEndian14( buf, pData+i, DTS_HEADER_SIZE, 0 );
			ret = UnpackDTSInfo( pDTSAudio, buf );
	        
		}
		/* 16 bits, Big Endian version of the bitstream */
		else if( pData[i+0] == 0x7f && pData[i+1] == 0xfe &&
				 pData[i+2] == 0x80 && pData[i+3] == 0x01 && (pData[i+4] & 0xfc) == 0xfc )
		{
			ret = UnpackDTSInfo( pDTSAudio, pData+i );
		}
		/* 16 bits, Little Endian version of the bitstream */
		else if( pData[i+0] == 0xfe && pData[i+1] == 0x7f &&
				 pData[i+2] == 0x01 && pData[i+3] == 0x80 )
		{
			LittleEndian( buf, pData+i, DTS_HEADER_SIZE );
			ret = UnpackDTSInfo( pDTSAudio, buf );
		}

		if ( ret ) break;
	}

   return ret;
}



