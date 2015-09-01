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
#include "LPCMFormat.h"

#define SPEAKER_FRONT_LEFT             0x1
#define SPEAKER_FRONT_RIGHT            0x2
#define SPEAKER_FRONT_CENTER           0x4
#define SPEAKER_LOW_FREQUENCY          0x8
#define SPEAKER_BACK_LEFT              0x10
#define SPEAKER_BACK_RIGHT             0x20
#define SPEAKER_FRONT_LEFT_OF_CENTER   0x40
#define SPEAKER_FRONT_RIGHT_OF_CENTER  0x80
#define SPEAKER_BACK_CENTER            0x100
#define SPEAKER_SIDE_LEFT              0x200
#define SPEAKER_SIDE_RIGHT             0x400
#define SPEAKER_TOP_CENTER             0x800
#define SPEAKER_TOP_FRONT_LEFT         0x1000
#define SPEAKER_TOP_FRONT_CENTER       0x2000
#define SPEAKER_TOP_FRONT_RIGHT        0x4000
#define SPEAKER_TOP_BACK_LEFT          0x8000
#define SPEAKER_TOP_BACK_CENTER        0x10000
#define SPEAKER_TOP_BACK_RIGHT         0x20000
#define SPEAKER_RESERVED               0x80000000


static int UnpackVobLPCMHeader( LPCM_AUDIO *pLPCMAudio, const uint8_t* pbData, int nSize );
static int UnpackDVDLPCMHeader( LPCM_AUDIO *pLPCMAudio, const uint8_t* pbData, int nSize );
static int UnpackBRLPCMHeader( LPCM_AUDIO *pLPCMAudio, const uint8_t* pbData, int nSize );

int ReadLPCM_AudioHeader( LPCM_AUDIO *pLPCMAudio, const uint8_t* pbData, int nSize )
{
	if ( pLPCMAudio->lpcm_source == 0 ) 
		return 0;

	if ( pLPCMAudio->lpcm_source == 1 ) 
		return UnpackVobLPCMHeader( pLPCMAudio, pbData, nSize );
	
	if ( pLPCMAudio->lpcm_source == 2 ) 
		return UnpackDVDLPCMHeader( pLPCMAudio, pbData, nSize );
	
	if ( pLPCMAudio->lpcm_source == 3 ) 
		return UnpackBRLPCMHeader( pLPCMAudio, pbData, nSize );

	return 0;
}

static int UnpackVobLPCMHeader( LPCM_AUDIO *pLPCMAudio, const uint8_t* pbData, int nSize )
{
	uint8_t header;
	if ( nSize < 6 ) 
		return 0;

    /* Check frame sync and drop it. */
    if( pbData[5] != 0x80)
		return 0;
	
	header = pbData[4];

    switch( (header >> 4) & 0x3 )
    {
    case 0:
		pLPCMAudio->samples_per_sec = 48000;
        break;
    case 1:
        pLPCMAudio->samples_per_sec = 96000;
        break;
    case 2:
        pLPCMAudio->samples_per_sec = 44100;
        break;
    case 3:
        pLPCMAudio->samples_per_sec = 32000;
        break;
    }

	pLPCMAudio->channels = (header & 0x7) + 1;

	
    switch( (header >> 6) & 0x3 )
    {
    case 2:
        pLPCMAudio->bits_per_sample = 24;
        break;
    case 1:
        pLPCMAudio->bits_per_sample = 20;
        break;
    case 0:
    default:
        pLPCMAudio->bits_per_sample = 16;
        break;
    }

    switch( header & 0x7 )
    {
    case 0:
		pLPCMAudio->channel_cfg = SPEAKER_FRONT_CENTER;
        break;
    case 1:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT;
        break;
    case 2:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT | SPEAKER_LOW_FREQUENCY;
        break;
    case 3:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT
                               | SPEAKER_BACK_LEFT  | SPEAKER_BACK_RIGHT ;
        break;
    case 4:
        /* This is unsure. */
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT
                               | SPEAKER_BACK_LEFT   | SPEAKER_BACK_RIGHT 
                               | SPEAKER_LOW_FREQUENCY;
        break;
    case 5:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT
                               | SPEAKER_BACK_LEFT   | SPEAKER_BACK_RIGHT 
                               | SPEAKER_FRONT_CENTER | SPEAKER_LOW_FREQUENCY;
        break;
    case 6:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT
                               | SPEAKER_SIDE_LEFT | SPEAKER_SIDE_RIGHT
                               | SPEAKER_FRONT_CENTER | SPEAKER_FRONT_LEFT_OF_CENTER
                               | SPEAKER_FRONT_RIGHT_OF_CENTER;
        break;
    case 7:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT
                               |  SPEAKER_BACK_LEFT  | SPEAKER_BACK_RIGHT 
                               | SPEAKER_FRONT_CENTER | SPEAKER_FRONT_LEFT_OF_CENTER
                               | SPEAKER_FRONT_RIGHT_OF_CENTER | SPEAKER_LOW_FREQUENCY;
        break;
    }

	pLPCMAudio->avgbytes_per_sec = pLPCMAudio->samples_per_sec*pLPCMAudio->channels*pLPCMAudio->bits_per_sample/8;
	pLPCMAudio->header_size = 6;
    return 1;
}

static const unsigned int dvd_group1[21] = { 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4 };
static const unsigned int dvd_group2[21] = { 0, 0, 1, 2, 1, 2, 3, 1, 2, 3, 2, 3, 4, 1, 2, 1, 2, 3, 1, 1, 2 };
static const uint32_t dvd_ch_group1[21] = {
     SPEAKER_FRONT_CENTER,
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT, 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT| SPEAKER_FRONT_CENTER,  
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT| SPEAKER_FRONT_CENTER,  
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT| SPEAKER_FRONT_CENTER,  
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT| SPEAKER_FRONT_CENTER,  
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT| SPEAKER_FRONT_CENTER,  
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT|  SPEAKER_BACK_LEFT | SPEAKER_BACK_RIGHT , 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT|  SPEAKER_BACK_LEFT | SPEAKER_BACK_RIGHT , 
     SPEAKER_FRONT_LEFT|SPEAKER_FRONT_RIGHT|  SPEAKER_BACK_LEFT | SPEAKER_BACK_RIGHT , 
};
static const unsigned dvd_ch_group2[21] = {
     0 ,
     0 ,
     SPEAKER_BACK_CENTER, 
     SPEAKER_SIDE_LEFT|SPEAKER_SIDE_RIGHT,    
     SPEAKER_LOW_FREQUENCY,        
     SPEAKER_LOW_FREQUENCY|SPEAKER_BACK_CENTER,   
     SPEAKER_LOW_FREQUENCY|SPEAKER_SIDE_LEFT|SPEAKER_SIDE_RIGHT,    
     SPEAKER_FRONT_CENTER,     
     SPEAKER_FRONT_CENTER|SPEAKER_BACK_CENTER, 
     SPEAKER_FRONT_CENTER|SPEAKER_SIDE_LEFT|SPEAKER_SIDE_RIGHT,    
     SPEAKER_FRONT_CENTER|SPEAKER_LOW_FREQUENCY,        
     SPEAKER_FRONT_CENTER|SPEAKER_LOW_FREQUENCY|SPEAKER_BACK_CENTER,   
     SPEAKER_FRONT_CENTER|SPEAKER_LOW_FREQUENCY|SPEAKER_SIDE_LEFT|SPEAKER_SIDE_RIGHT,    
     SPEAKER_BACK_CENTER, 
     SPEAKER_SIDE_LEFT|SPEAKER_SIDE_RIGHT,    
     SPEAKER_LOW_FREQUENCY,        
     SPEAKER_LOW_FREQUENCY|SPEAKER_BACK_CENTER,   
     SPEAKER_LOW_FREQUENCY|SPEAKER_SIDE_LEFT|SPEAKER_SIDE_RIGHT,    
     SPEAKER_LOW_FREQUENCY,        
     SPEAKER_FRONT_CENTER,     
     SPEAKER_FRONT_CENTER|SPEAKER_LOW_FREQUENCY,          
};
static int UnpackDVDLPCMHeader( LPCM_AUDIO *pLPCMAudio, const uint8_t* pbData, int nSize )
{
	int channels1 = 0, channels2 = 0, channels1_cfg=0 , channels2_cfg=0;
	int index_size_g1, index_size_g2, index_rate_g1, index_rate_g2, assignment;
	int group2_used;
  const unsigned header_size = (pbData[1]<<8)|pbData[2];
  if( header_size + 3 < 11 )
        return 0;

  //*pi_padding = 3+i_header_size - LPCM_AOB_HEADER_LEN;
  index_size_g1 = (pbData[6] >> 4) & 0x0f;
  index_size_g2 = (pbData[6]     ) & 0x0f;
  index_rate_g1 = (pbData[7] >> 4) & 0x0f;
  index_rate_g2 = (pbData[7]     ) & 0x0f;
  assignment     = pbData[9];

  /* Validate */
  if( index_size_g1 > 0x02 || ( index_size_g2 != 0x0f && index_size_g2 > 0x02 ) )
      return 0;
  if( (index_rate_g1 & 0x07) > 0x02 || ( index_rate_g2 != 0x0f && (index_rate_g1 & 0x07) > 0x02 ) )
      return 0;
  if( assignment > 20 )
      return 0;

    
  pLPCMAudio->bits_per_sample = 16 + 4 * index_size_g1;
  if( index_rate_g1 & 0x08 )
      pLPCMAudio->samples_per_sec = 44100 << (index_rate_g1 & 0x07);
  else
      pLPCMAudio->samples_per_sec = 48000 << (index_rate_g1 & 0x07);


  /* Group1 */
	channels1 = dvd_group1[assignment];
	channels1_cfg = dvd_ch_group1[assignment];

  /* Group2 */
  if( index_size_g2 != 0x0f && index_rate_g2 != 0x0f )
	{
		channels2 = dvd_group2[assignment];
		channels2_cfg = dvd_ch_group2[assignment];
	}

	/* It is enabled only when presents and compatible wih group1 */
  group2_used = index_size_g1 == index_size_g2 && index_rate_g1 == index_rate_g2;

	pLPCMAudio->channels = channels1 + ( group2_used ? channels2 : 0 );
	pLPCMAudio->channel_cfg = channels1_cfg | ( group2_used ? channels2_cfg : 0 );
	if ( pLPCMAudio->channels == 0 )
		return 0;

	pLPCMAudio->avgbytes_per_sec = pLPCMAudio->samples_per_sec*pLPCMAudio->channels*pLPCMAudio->bits_per_sample/8;
	pLPCMAudio->header_size = 11;
    return 1;
}

static int UnpackBRLPCMHeader( LPCM_AUDIO *pLPCMAudio, const uint8_t* pbData, int nSize )
{
  uint32_t h;
	h = pbData[0];	h <<= 8; 
	h |= pbData[1];	h <<= 8;
	h |= pbData[2];	h <<= 8;
	h |= pbData[3];
    switch( ( h & 0xf000) >> 12 )
    {
    case 1:
        pLPCMAudio->channels = 1;
        break;
    case 3:
        pLPCMAudio->channels = 2;
        break;
    case 4:
        pLPCMAudio->channels = 3;
        break;
    case 5:
        pLPCMAudio->channels = 3;
        break;
    case 6:
        pLPCMAudio->channels = 4;
        break;
    case 7:
        pLPCMAudio->channels = 4;
        break;
    case 8:
        pLPCMAudio->channels = 5;
        break;
    case 9:
        pLPCMAudio->channels = 6;
        break;
    case 10:
        pLPCMAudio->channels = 7;
        break;
    case 11:
        pLPCMAudio->channels = 8;
        break;

    default:
        return 0;
    }
    switch( (h >> 6) & 0x03 )
    {
    case 1:
        pLPCMAudio->bits_per_sample = 16;
        break;
    case 2: /* 20 bits but samples are stored on 24 bits */
    case 3: /* 24 bits */
        pLPCMAudio->bits_per_sample = 24;
        break;
    default:
        return 0;
    }
    switch( (h >> 8) & 0x0f ) 
    {
    case 1:
        pLPCMAudio->samples_per_sec = 48000;
        break;
    case 4:
        pLPCMAudio->samples_per_sec = 96000;
        break;
    case 5:
        pLPCMAudio->samples_per_sec = 192000;
        break;
    default:
        return 0;
    }
    switch( ( h & 0xf000) >> 12 )
    {
    case 1:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_CENTER;
        break;
    case 3:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT;
        break;
    case 4:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT | SPEAKER_FRONT_CENTER;
        break;
    case 5:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT | SPEAKER_BACK_CENTER;
        break;
    case 6:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT | SPEAKER_FRONT_CENTER |
                                SPEAKER_BACK_CENTER;
        break;
    case 7:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT |
                                 SPEAKER_BACK_LEFT | SPEAKER_BACK_RIGHT ;
        break;
    case 8:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT | SPEAKER_FRONT_CENTER |
                                 SPEAKER_BACK_LEFT | SPEAKER_BACK_RIGHT ;
        break;
    case 9:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT | SPEAKER_FRONT_CENTER |
                                SPEAKER_BACK_LEFT | SPEAKER_BACK_RIGHT |
                                SPEAKER_LOW_FREQUENCY;
        break;
    case 10:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT | SPEAKER_FRONT_CENTER |
								SPEAKER_BACK_LEFT | SPEAKER_BACK_RIGHT |
                                SPEAKER_SIDE_LEFT | SPEAKER_SIDE_RIGHT            ;
        break;
    case 11:
        pLPCMAudio->channel_cfg = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT | SPEAKER_FRONT_CENTER |
								SPEAKER_BACK_LEFT | SPEAKER_BACK_RIGHT |
                                SPEAKER_SIDE_LEFT | SPEAKER_SIDE_RIGHT |
                                SPEAKER_LOW_FREQUENCY;
        break;

    default:
        return 0;
    }

	pLPCMAudio->avgbytes_per_sec = pLPCMAudio->samples_per_sec*pLPCMAudio->channels*pLPCMAudio->bits_per_sample/8;
	pLPCMAudio->header_size = 4;
	return 1;
}

static int ConvertVobLPCM(  void* pConverter, int bGroupStart, const uint8_t* pbData, int nSize, uint8_t* pOutData, int nBufSize );
//static int ConvertDVDLPCM(  void* pConverter, int bGroupStart, const uint8_t* pbData, int nSize, uint8_t* pOutData, int nBufSize );
static int ConvertBRLPCM(  void* pConverter, int bGroupStart, const uint8_t* pbData, int nSize, uint8_t* pOutData, int nBufSize );

typedef struct 
{
	uint8_t* buffer;
	uint16_t buffer_size;
	uint16_t data_bytes;
} CVT_BUF;


typedef struct LPCM_CVT
{
	LPCM_AUDIO lpcm_audio;
	CVT_BUF    cvt_buf;
	uint16_t   start_flag;
} LPCM_CVT;

void *OpenLPCMConvert( LPCM_AUDIO *pLPCMAudio )
{
	LPCM_CVT *pLCPMCvt = (LPCM_CVT *)SAGETV_MALLOC( sizeof(LPCM_CVT) );
	pLCPMCvt->lpcm_audio = *pLPCMAudio;
	if ( pLCPMCvt->lpcm_audio.lpcm_source == 3 ) //BlueRay DVD (assume)
	{
		pLCPMCvt->cvt_buf.buffer_size = 4*1024;
		pLCPMCvt->cvt_buf.buffer = (uint8_t*)SAGETV_MALLOC( pLCPMCvt->cvt_buf.buffer_size );
	}
	return pLCPMCvt;
}

void CloseLPCMConvert( void* pConverter )
{
	LPCM_CVT *pLCPMCvt = (LPCM_CVT *)pConverter;
	if ( pLCPMCvt->cvt_buf.buffer )
	{
		SAGETV_FREE( pLCPMCvt->cvt_buf.buffer );
	}
	SAGETV_FREE( pLCPMCvt );
}

void ResetLPCMConvert( void* pConverter )
{
	LPCM_CVT *pLCPMCvt = (LPCM_CVT *)pConverter;
	pLCPMCvt->cvt_buf.data_bytes = 0;
	pLCPMCvt->start_flag = 0;
}

int LPCMConvert( void* pConverter, int bGroupStart, const uint8_t* pInData, int nSize, uint8_t* pOutData, int nBufSize )
{
	LPCM_CVT *pLCPMCvt = (LPCM_CVT *)pConverter;
	if ( pLCPMCvt->lpcm_audio.lpcm_source == 0 ) 
		return 0;

	if ( pLCPMCvt->lpcm_audio.lpcm_source == 1 ) 
		return ConvertVobLPCM( pConverter, bGroupStart, pInData, nSize, pOutData, nBufSize );
	
	if ( pLCPMCvt->lpcm_audio.lpcm_source == 2 ) 
		return ConvertVobLPCM( pConverter, bGroupStart, pInData, nSize, pOutData, nBufSize );
	
	if ( pLCPMCvt->lpcm_audio.lpcm_source == 3 ) 
		return ConvertBRLPCM( pConverter, bGroupStart, pInData, nSize, pOutData, nBufSize );

	return 0;
	
}

static int ConvertVobLPCM( void* pConverter, int bGroupStart, const uint8_t* pbData, int nSize, uint8_t* pOutData, int nBufSize  )
{
	LPCM_CVT *pLCPMCvt = (LPCM_CVT *)pConverter;
	LPCM_AUDIO *pLPCMAudio = &pLCPMCvt->lpcm_audio;
	uint8_t *p_out, *p_in;
	int bytes_in, bytes_out;
	p_in = (uint8_t *)pbData;
	p_out = pOutData;
	bytes_in = nSize;

	p_in += pLPCMAudio->header_size;
	bytes_in -= pLPCMAudio->header_size;
	if ( bytes_in <= 0 )
		return 0;
	
	if ( bytes_in > nBufSize ) 
		bytes_in = nBufSize;

    // 20/24 bits LPCM use special packing
	bytes_out = 0;
	if( pLPCMAudio->bits_per_sample == 24 )
    {
        while( bytes_in > 12 )
        {
            /* Sample 1 */
            p_out[0] = p_in[0];
            p_out[1] = p_in[1];
            p_out[2] = p_in[8];
            /* Sample 2 */
            p_out[3] = p_in[2];
            p_out[4] = p_in[3];
            p_out[5] = p_in[9];
            /* Sample 3 */
            p_out[6] = p_in[4];
            p_out[7] = p_in[5];
            p_out[8] = p_in[10];
            /* Sample 4 */
            p_out[9] = p_in[6];
            p_out[10] = p_in[7];
            p_out[11] = p_in[11];

            nBufSize -= 12;
			bytes_in -= 12;
			bytes_out += 12;
            p_out += 12;
            p_in += 12;
        }
    }
    else if( pLPCMAudio->bits_per_sample == 20 )
    {
        while( bytes_in > 10 )
        {
            /* Sample 1 */
            p_out[0] = p_in[0];
            p_out[1] = p_in[1];
            p_out[2] = p_in[8] & 0xF0;
            /* Sample 2 */
            p_out[3] = p_in[2];
            p_out[4] = p_in[3];
            p_out[5] = p_in[8] << 4;
            /* Sample 3 */
            p_out[6] = p_in[4];
            p_out[7] = p_in[5];
            p_out[8] = p_in[9] & 0xF0;
            /* Sample 4 */
            p_out[9] = p_in[6];
            p_out[10] = p_in[7];
            p_out[11] = p_in[9] << 4;

            nBufSize -= 10;
			bytes_in -= 12;
			bytes_out += 12;
            p_out += 10;
            p_in += 12;
        }
    }
    else
    {
        memcpy( p_out, p_in, bytes_in );
		bytes_out = bytes_in;
    }
	
	return bytes_out;
}


static int ConvertBRLPCM( void* pConverter, int bGroupStart, const uint8_t* pbData, int nSize, uint8_t* pOutData, int nBufSize )
{
	LPCM_CVT *pLCPMCvt = (LPCM_CVT *)pConverter;

	uint8_t *p_out, *p_in;
	int used_bytes;
	int convert_bytes, leftover_bytes;
	LPCM_AUDIO lpcm_audio;
	p_out = pOutData;

	//wait group start
	if ( !pLCPMCvt->start_flag && !bGroupStart )
		return 0;

	//wait header that is real begining of a block
	if ( !pLCPMCvt->start_flag && !UnpackBRLPCMHeader( &lpcm_audio, pbData, nSize ) )
		return 0;

	pLCPMCvt->start_flag = 1;

	//skip 4 bytes header
	if ( bGroupStart )
	{
		pbData += (int)pLCPMCvt->lpcm_audio.header_size;
		nSize -= (int)pLCPMCvt->lpcm_audio.header_size;
	}
	if ( nSize <= 0 )
		return 0;


	//if buffer isn't alloced or buffer size is samller tha we need, alloc a new one
	if ( pLCPMCvt->cvt_buf.buffer_size + pLCPMCvt->cvt_buf.data_bytes < nSize )
	{
		int buffer_size = nSize+512;
		uint8_t* p = SAGETV_MALLOC( buffer_size );
		if ( pLCPMCvt->cvt_buf.data_bytes )
			memcpy( p, pLCPMCvt->cvt_buf.buffer, pLCPMCvt->cvt_buf.data_bytes );
		if ( pLCPMCvt->cvt_buf.buffer )
			SAGETV_FREE( pLCPMCvt->cvt_buf.buffer );
		pLCPMCvt->cvt_buf.buffer = p;
		pLCPMCvt->cvt_buf.buffer_size = buffer_size;
	}

	p_in = pLCPMCvt->cvt_buf.buffer;
	convert_bytes = pLCPMCvt->cvt_buf.data_bytes + nSize;
	memcpy( p_in+pLCPMCvt->cvt_buf.data_bytes, pbData, nSize );


	used_bytes = 0;
	while ( convert_bytes - used_bytes > 18 &&  used_bytes+18 <= nBufSize )
	{
		p_out[0] = p_in[2];
		//p_out[1] = p_in[1];
		p_out[2] = p_in[0];

		p_out[3] = p_in[5];
		//p_out[4] = p_in[4];
		p_out[5] = p_in[3];

		p_out[6] = p_in[8];
		//p_out[7] = p_in[7];
		p_out[8] = p_in[6];

		p_out[9]  = p_in[11];
		//p_out[10] = p_in[10];
		p_out[11] = p_in[9];

		p_out[12] = p_in[14];
		//p_out[13] = p_in[13];
		p_out[14] = p_in[12];

		p_out[15] = p_in[17];
		//p_out[16] = p_in[16];
		p_out[17] = p_in[15];

		p_in += 18;
		p_out += 18;
		used_bytes += 18;

	}

	leftover_bytes = convert_bytes - used_bytes;
	if ( leftover_bytes > 0 )
	{
		p_in = pLCPMCvt->cvt_buf.buffer;
		memcpy( p_in, p_in+used_bytes, leftover_bytes );
		pLCPMCvt->cvt_buf.data_bytes = leftover_bytes;
	} else
		pLCPMCvt->cvt_buf.data_bytes = 0;
	
	return used_bytes;

}

