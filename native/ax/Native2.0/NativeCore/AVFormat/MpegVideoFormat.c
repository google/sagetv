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
#include "MpegVideoFormat.h"

#define MPEG_TIME_DIVISOR (90000)
#define MPEG_MAX_TIME ((int64_t)0x200000000)

#define PICTURE_START_CODE       0x00000100
#define USER_DATA_START_CODE     0x000001B2
#define SEQUENCE_HEADER_CODE     0x000001B3
#define SEQUENCE_ERROR_CODE      0x000001B4
#define EXTENSION_START_CODE     0x000001B5
#define SEQUENCE_END_CODE        0x000001B7
#define GROUP_START_CODE         0x000001B8

static const int32_t PictureTimes[16]	= {	
	0,
	(int32_t)((double)10000000	/ 23.976),
	(int32_t)((double)10000000	/ 24),
	(int32_t)((double)10000000	/ 25),
	(int32_t)((double)10000000	/ 29.97),
	(int32_t)((double)10000000	/ 30),
	(int32_t)((double)10000000	/ 50),
	(int32_t)((double)10000000	/ 59.94),
	(int32_t)((double)10000000	/ 60)
};

static const float	PictureRates[] =   {
	(float)0,
	(float)23.976,	
	(float)24,	
	(float)25,	
	(float)29.97,
	(float)30,	
	(float)50,	
	(float)59.94,
	(float)60.0
};


/*	Bit	rate tables	*/
static const uint16_t BitRates[3][16] ={
	{ 	0, 32,	64,	 96,  128, 160,	192, 224, 256, 288,	320, 352, 384, 416,	448, 0 },
	{	0, 32,	48,	 56,   64,	80,	 96, 112, 128, 160,	192, 224, 256, 320,	384, 0 },
	{	0, 32,	40,	 48,   56,	64,	 80,  96, 112, 128,	160, 192, 224, 256,	320, 0 }
};

//float Mepg2FrameRate( int8_t code )
//{
//	switch (code &0x0f ) {
//	case 1: return (float)24000.0/1001;
//	case 2: return 24;
//	case 3: return 25;
//	case 4: return (float)30000.0/1001;
//	case 5: return 30;
//	case 6: return 50;
//	case 7: return (float)60000.0/1001;
//	case 8: return 60;
//	default: return 0;
//	}
//	return 0;
//}


float Mepg2AspectRatioF( uint8_t code, int32_t width, int32_t height )
{
	if ( height == 0 || width == 0 ) return 0;

	switch ( code & 0x0f ) {
	case 1:
		//return 1;
		return (float)((1.0*width)/(1.0*height));
	case 2:
		return (float)((4.0)/(3.0));
	case 3:
		return (float)((16.0)/(9.0));
	case 4:
		return (float)((221.0)/(100.0));

	case 5:
		return (float)12/11;  //MPEG4 625type 4:3 ISO 14496-2
	case 6:
		return (float)10/11;  //MPEG4 525 type 4:3 ISO 14496-2
	case 7:
		return (float)16/11;  //MPEG4 16:11 (625-type stretched for 16:9 picture) ISO 14496-2
	case 8:
		return (float)40/33;  //MPEG4 40:33 (525-type stretched for 16:9 picture) ISO 14496-2
	
	case 0x0f:
		return ((float)width)/((float)height);

	default:
		return 0;
	}

	return 0;
}

/*
static int LCMNomiCalculator( int n, int m )
{
	int k = n > m ? m : n;
	int t=2;
	while ( t < k )
	{
		if ( n % t == 0 && m % t == 0 )
		{
			n /= t;
			m /= t;
		} else
		{
			t++;
		}
		k = n > m ? m : n;
	}

	return n;
}
static int LCMDenoCalculator( int n, int m )
{
	int k = n > m ? m : n;
	int t=2;
	while ( t < k )
	{
		if ( n % t == 0 && m % t == 0 )
		{
			n /= t;
			m /= t;
		} else
		{
			t++;
		}
		k = n > m ? m : n;
	}

	return m;
}
*/


static int GCD( int n, int m )
{
	if ( m )
		return GCD( m, n % m );
	else
		return n;
}

static int LCMNomiCalculator( int n, int m )
{
	int gcd = GCD( n, m );
	return n/gcd;
}

static int LCMDenoCalculator( int n, int m )
{
	int gcd = GCD( n, m );
	return m/gcd;
}

int Mepg2AspectRatioNomiValue( uint8_t code, int32_t width, int32_t height )
{
	if ( height == 0 || width == 0 ) return 0;
	switch ( code & 0x0f ) {
	case 1:
		return LCMNomiCalculator( width, height );
	case 2:
		return 4;
	case 3:
		return 16;
	case 4:
		return 221;

	case 5:
		return 12;  //MPEG4 625type 4:3 ISO 14496-2
	case 6:
		return 10;  //MPEG4 525 type 4:3 ISO 14496-2
	case 7:
		return 16;  //MPEG4 16:11 (625-type stretched for 16:9 picture) ISO 14496-2
	case 8:
		return 40;  //MPEG4 40:33 (525-type stretched for 16:9 picture) ISO 14496-2

	case 0x0f:
		return height;

	default:
		return 0;
	}
	return 0;
}

int Mepg2AspectRatioDenoValue( uint8_t code, int32_t width, int32_t height )
{
	if ( height == 0 || width == 0 ) return 0;
	switch ( code & 0x0f ) {
	case 1:
		return LCMDenoCalculator( width, height );
	case 2:
		return 3;
	case 3:
		return 9;
	case 4:
		return 100;

	case 5:
		return 11;  //MPEG4 625type 4:3 ISO 14496-2
	case 6:
		return 11;  //MPEG4 525 type 4:3 ISO 14496-2
	case 7:
		return 11;  //MPEG4 16:11 (625-type stretched for 16:9 picture) ISO 14496-2
	case 8:
		return 33;  //MPEG4 40:33 (525-type stretched for 16:9 picture) ISO 14496-2

	case 0x0f:
		return width; 

	default:
		return 1;
	}
	return 1;
}

float Mepg1AspectRatioF( uint8_t code, int32_t width, int32_t height )
{
	switch ( code & 0x0f ) {
	case 1:
		return (float)((1.0*width)/(1.0*height));
		//return 1.0;
	case 2:
		return (float)0.6735;
	case 3:
		return (float)0.7031;
	case 4:
		return (float)0.7615;
	case 5:
		return (float)0.8055;
	case 6:
		return (float)0.8437;
	case 7:
		return (float)0.8935;
	case 8:
		return (float)0.9157;
	case 9:
		return (float)0.9815;
	case 10:
		return (float)1.0255;
	case 11:
		return (float)1.0695;
	case 12:
		return (float)1.0950;
	case 13:
		return (float)1.1575;
	case 14:
		return (float)1.2015;
	default:
		return 0;
	}
	return 0;
}

int Mepg2FrameRateNomiValue( uint8_t code )
{
	switch (code & 0x0f) {
	case 1: return 24000;
	case 2: return 24;
	case 3: return 25;
	case 4: return 30000;
	case 5: return 30;
	case 6: return 50;
	case 7: return 60000;
	case 8: return 60;
	default: return 0;
	}
	return 0;
}

int Mepg2FrameRateDenoValue( uint8_t code )
{
	switch (code & 0x0f) {
	case 1: return 1001;
	case 2: return 1;
	case 3: return 1;
	case 4: return 1001;
	case 5: return 1;
	case 6: return 1;
	case 7: return 1001;
	case 8: return 1;
	default: return 1;
	}
	return 1;
}

static short MPEG2SequenceHeaderSize( const uint8_t *pb )	
{
	/*	No quantization	matrices ? */
	if ((pb[11]	& 0x03)	== 0x00) {
		return 12 +	10;	
	}
	/*	Just non-intra quantization	matrix ? */	
	if ((pb[11]	& 0x03)	== 0x01) {
		return 12 +	64 + 10;
	}
	/*	Intra found	- is there a non-intra ? */	
	if (pb[11 +	64]	& 0x01)	{
		return 12 +	64 + 64	+ 10;
	} else {
		return 12 +	64 + 10;
	}
}




static int UnpackMPEGSeqHdr( MPEG_VIDEO *pMpegVideo, const uint8_t *pbData )
{
	uint32_t width_and_height;
	uint8_t pel_aspect_ratio_and_pictureRate;

	/*	Check random marker	bit	*/
	if ( !(	pbData[10] & 0x20 )	) 
		return 0;

	//memset(	&av->Mpeg2Hdr, 0, sizeof( av->Mpeg2Hdr ) );	
	
	width_and_height = ((uint32_t)pbData[4] << 16) +	
				  	    ((uint32_t)pbData[5] << 8) +
						    ((uint32_t)pbData[6]);

	
	pMpegVideo->width  = width_and_height >> 12;
	pMpegVideo->height = width_and_height &	0xFFF;

	/* the '8' bit is the scramble flag	used by	sigma designs -	ignore */
	pel_aspect_ratio_and_pictureRate = pbData[7];	
	if ((pel_aspect_ratio_and_pictureRate &	0x0F) >	8)	
		pel_aspect_ratio_and_pictureRate &=	0xF7;

	pMpegVideo->ar_info = (uint32_t)pel_aspect_ratio_and_pictureRate >> 4;	

	if ( (pel_aspect_ratio_and_pictureRate & 0xF0) == 0	||
		 (pel_aspect_ratio_and_pictureRate & 0x0F) == 0) 
	{
		return 0;
	}

	//pMpegVideo->FrameRateCode = pel_aspect_ratio_and_pictureRate;
	pMpegVideo->frame_rate_nomi = Mepg2FrameRateNomiValue(pel_aspect_ratio_and_pictureRate);
	pMpegVideo->frame_rate_deno = Mepg2FrameRateDenoValue(pel_aspect_ratio_and_pictureRate);

	pMpegVideo->picture_time = (int64_t)PictureTimes[pel_aspect_ratio_and_pictureRate & 0x0F];
	pMpegVideo->picture_rate	= PictureRates[pel_aspect_ratio_and_pictureRate	& 0x0F];

	pMpegVideo->time_per_frame = (int32_t)pMpegVideo->picture_time * 9/1000;	

	/*	Pull out the bit rate and aspect ratio for the type	*/
	pMpegVideo->bit_rate	= ((((uint32_t)pbData[8]	<< 16) +
							 ((uint32_t)pbData[9]	<<	8) +
							 (uint32_t)pbData[10])	>> 6 );		
	if (pMpegVideo->bit_rate	== 0x3FFFF)	{
		pMpegVideo->bit_rate	 = 0;
	} else {
		pMpegVideo->bit_rate	*= 400;	
	}

	/*	Pull out the vbv */	
	pMpegVideo->vbv	= ((((int32_t)pbData[10] &	0x1F) << 5)	| ((int32_t)pbData[11]	>> 3)) * 16	* 1024;	

	pMpegVideo->actual_header_length	= MPEG2SequenceHeaderSize(pbData);
	if ( (unsigned int)pMpegVideo->actual_header_length > sizeof(pMpegVideo->raw_header) )
		return 0;

	memcpy((void*)pMpegVideo->raw_header, (void*)pbData,	pMpegVideo->actual_header_length	);
  SageLog(( _LOG_ERROR, 3, TEXT("TRACE--- %d %d %d %d %d |%d %d %d %d %x\n"), width_and_height, 
         pMpegVideo->width, pMpegVideo->height, pMpegVideo->ar_info, pMpegVideo->ar_info,
         pMpegVideo->frame_rate_nomi, pMpegVideo->frame_rate_deno,   pMpegVideo->picture_rate, pMpegVideo->bit_rate, pMpegVideo->bit_rate  ));
	return 1;
}


static int UnpackMPEG2SeqExtHdr( MPEG_VIDEO *pMpegVideo, const uint8_t *pbData )
{
	 //	 check the extension id	
	if ( (pbData[4]	>> 4) != 0x01 )	/* 0x02, 0x08 skipped*/
		return 0;

	pMpegVideo->profile	 = pbData[4] & 0x07;
	pMpegVideo->level	 = pbData[5] >>	4;
	pMpegVideo->width	+= ((pbData[5] & 1)	<< 13) + ((pbData[6] & 0x80) <<	5);	
	pMpegVideo->height	+= (pbData[6] &	0x60) << 7;	
	pMpegVideo->bit_rate	+= 400 * (((pbData[6] &	0x1F) << (18 + 7)) +
							 ((pbData[7] & 0xFE) <<	(18	- 1)));	
	pMpegVideo->progressive	= (pbData[5] & 0x8)	>> 3;
	pMpegVideo->chrome = (pbData[5] & 0x6)	>> 1;
	return 1;
}

int ReadMpegVideoHeader( MPEG_VIDEO *pMpegVideo, const uint8_t* pStart, int Size )
{
	//Video information always is in header of PES, start indictates start of a PES 	
	const uint8_t* data;
	const uint8_t* p;
	int size;

	data = pStart;
	size = Size;

	p = SearchMPEGStartCode( data, size, SEQUENCE_HEADER_CODE );
	if ( p == NULL )
		return 0;

	if ( UnpackMPEGSeqHdr( pMpegVideo, p ) == 0 )
		return 0;

	//search and parse mpeg2 extension header
	if ( Size > (p - pStart) + pMpegVideo->actual_header_length-10 )
	{
		data = (const uint8_t*)p + (pMpegVideo->actual_header_length-10);
		size = Size - (int)(p - pStart) - (pMpegVideo->actual_header_length-10);

		if ( size >= 7 )
		{
			int ext_type_id;
			pMpegVideo->extension_present = 0; //MPEG1 Video
			while ( size >= 7 )
			{
				p = SearchMPEGStartCode( data, size, EXTENSION_START_CODE );
				//MPEG-1 desn't	have extension part	
				if ( p != NULL )
				{
					ext_type_id =( p[4] >> 4 );
					if ( ext_type_id == 0x01 ) //Sequence_Extension
					{
						pMpegVideo->extension_present = 1; //MPEG2 Video
						UnpackMPEG2SeqExtHdr( pMpegVideo, p );
						break; //we don't care other extension, as the information we needed is here
					}
					else
					if ( ext_type_id == 0x02 ) //Sequence_Display_Extension
					{
						pMpegVideo->extension_present = 1; //MPEG2 Video
					} else
					if ( ext_type_id == 0x08 )	//Picture_Coding_Extension
					{
						pMpegVideo->extension_present = 1; //MPEG2 Video
					}

					data += (p-data)+7;
					size -= (int)(p-data)+7;
				} else
					break;
			}
			return 1;
		}

	}
	
	return 0;
}


