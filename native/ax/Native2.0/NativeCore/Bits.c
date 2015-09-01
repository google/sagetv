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
#include "Bits.h"
#include <stdio.h>
#include <memory.h>
////////////////////////////////////////////////////////////////////////////////////////
//INPUT BITS
////////////////////////////////////////////////////////////////////////////////////////

uint16_t U( BITS_I* pBits, int nCodeBits ) 
{
	register uint16_t val, i;      // control bit for current bit posision                                       
	int  totbitoffset;
	int32_t byteoffset;        // byte from start of buffer                                                
	int  bitoffset ;        // bit from start of byte
	    
	if ( nCodeBits > pBits->total_bits || pBits->error_flag )
	{
		pBits->error_flag = 1;
		return 0;
	}

	totbitoffset = pBits->bits_offset;
	val = 0;

	for ( i = 0; i<(int16_t)nCodeBits; i++ )
	{
		val <<= 1;
		byteoffset= totbitoffset>>3;                                                                      
		bitoffset= 7-(totbitoffset&0x07); 
		totbitoffset++;
		if ( (pBits->buffer[byteoffset] & (0x01<<bitoffset)) )
		{
			 val |= 1;
		}
	}
	return val;
	 
}

uint32_t UL( BITS_I* pBits, int nCodeBits ) 
{
	register uint32_t val;
	register uint16_t i;      // control bit for current bit posision                                       
	int  totbitoffset;
	int32_t byteoffset;        // byte from start of buffer                                                
	int  bitoffset ;        // bit from start of byte
	    
	if ( nCodeBits > pBits->total_bits || pBits->error_flag )
	{
		pBits->error_flag = 1;
		return 0;
	}

	totbitoffset = pBits->bits_offset;
	val = 0;

	for ( i = 0; i<(int16_t)nCodeBits; i++ )
	{
		val <<= 1;
		byteoffset= totbitoffset>>3;                                                                      
		bitoffset= 7-(totbitoffset&0x07); 
		totbitoffset++;
		if ( (pBits->buffer[byteoffset] & (0x01<<bitoffset)) )
		{
			 val |= 1;
		}
	}
	return val;
	 
}

void SkipBits( BITS_I* pBits, int nCodeBits  )
{
	if ( pBits->error_flag ) 
		return ;
	pBits->total_bits  -= nCodeBits;
	pBits->bits_offset += nCodeBits;
}

int32_t ReadBitsU( BITS_I* pBits, int nCodeBits  )
{
	int32_t val;
	if ( nCodeBits >= 16 )
		val = UL( pBits, nCodeBits );
	else
		val = U( pBits, nCodeBits );
	if ( pBits->error_flag ) 
		return 0;
	pBits->total_bits  -= nCodeBits;
	pBits->bits_offset += nCodeBits;
	return val;
}

static int32_t GolombCode (const uint8_t buffer[], int totbitoffset, int *info, int bytecount )                           
 {                                                                                                   
	register int inf;                                                                                 
	int32_t byteoffset;      // byte from start of buffer                                                
	int bitoffset;        // bit from start of byte                                                     
	int ctr_bit=0;        // control bit for current bit posision                                       
	int bitcounter=1;                                                                                 
	int len;                                                                                          
	int info_bit;                                                                                     
	                                                                                                    
	byteoffset= totbitoffset>>3;                                                                      
	bitoffset= 7-(totbitoffset&0x07);                                                                 
	ctr_bit = (buffer[byteoffset] & (0x01<<bitoffset));   // set up control bit                       
	                                                                                                    
	len=1;                                                                                            
	while (ctr_bit==0)                                                                                
	{                 // find leading 1 bit                                                           
		len++;                                                                                          
		bitoffset-=1;                                                                                   
		bitcounter++;                                                                                   
		if (bitoffset<0)                                                                                
		{                 // finish with current byte ?                                                 
		bitoffset=bitoffset+8;                                                                        
		byteoffset++;                                                                                 
		}                                                                                               
		ctr_bit=buffer[byteoffset] & (0x01<<(bitoffset));                                               
	}                                                                                                 
	    
	// make infoword                                                                                
	inf=0;                          // shortest possible code is 1, then info is always 0             
	for(info_bit=0;(info_bit<(len-1)); info_bit++)                                                    
	{                                                                                                 
		bitcounter++;                                                                                   
		bitoffset-=1;                                                                                   
		if (bitoffset<0)                                                                                
		{ // finished with current byte ?                                               
		bitoffset=bitoffset+8;                                                                        
		byteoffset++;                                                                                 
		}                                                                                               
		if (byteoffset > bytecount)                                                                     
		{                                                                                               
			return -1;                                                                                    
		}                                                                                               
		inf=(inf<<1);                                                                                   
		if(buffer[byteoffset] & (0x01<<(bitoffset)))                                                    
		inf |=1;                                                                                      
	}                                                                                                 
	                                                                                                    
	*info = inf;                                                                                      
	return bitcounter;           // return absolute offset in bit from start of frame                 
 }                                                                                                   


uint32_t UE( BITS_I *pBits, int *pCodeBits )
{
	int info;
	*pCodeBits = GolombCode( pBits->buffer, pBits->bits_offset, 
	                         &info, (pBits->total_bits>>3)+((pBits->total_bits&0x7)?1:0)  );
	if ( *pCodeBits == -1 ) 
	{
		pBits->error_flag = 1;
		return 0;
	}
	return (1<<(*pCodeBits>>1))+info-1;
}

int32_t SE( BITS_I *pBits, int *pCodeBits ) 
{
	int info;
	int n, val;
	
	*pCodeBits = GolombCode( pBits->buffer, pBits->bits_offset, 
	                         &info, (pBits->total_bits>>3)+((pBits->total_bits&0x7)?1:0)  );
	if ( *pCodeBits  == -1 )
	{
		pBits->error_flag = 1;
		return 0;
	}
	
	n = (1 << (*pCodeBits>>1))+info-1;
	val = (n+1)>>1;
	if ((n & 0x01)==0 )                           // lsb is signed bit
		val = -val;

	return val;
}


uint32_t ReadUE( BITS_I *pBits )
{
	int val, bits;
	val = UE( pBits, &bits );
	if ( pBits->error_flag ) 
		return 0;
	pBits->bits_offset += bits;
	pBits->total_bits  -= bits;
	return val;
}


int32_t ReadSE( BITS_I *pBits )
{
	int val, bits;
	val = SE( pBits, &bits );
	if ( pBits->error_flag ) 
		return 0;
	pBits->bits_offset += bits;
	pBits->total_bits  -= bits;
	return val;
}


////////////////////////////////////////////////////////////////////////////////////////
//OUTPUT BITS
////////////////////////////////////////////////////////////////////////////////////////
void InitOutBITS( BITS_T *bits, uint8_t* buf, int size )
{
	bits->outbfr =0;
	bits->outcnt = 32;
	bits->bytecnt = 0;
    bits->buf = buf;
	bits->buf_start = buf;
	bits->buf_size  = size;
}


void PutOutBITS( BITS_T *bits, uint32_t val, int n )  
{
	uint32_t mask;
	if ( n == 0 ) return;
	if ( bits->bytecnt >= bits->buf_size )
		return;

	mask = 0xffffffff;
	mask >>= 32-n;
	val &= mask;

	if ( bits->outcnt > n )
	{
		bits->outbfr <<= n;
		bits->outbfr |= val;
		bits->outcnt -= n;
	}
	else
	{
		if ( bits->outcnt == 32 )   /*intel cpu if shift more than register bits length, operation skips, ZQ */
			bits->outbfr = 0;
		else
			bits->outbfr = ( bits->outbfr << bits->outcnt );
		bits->outbfr |=  val >> (n - bits->outcnt)  ;
		
    	*(bits->buf++) = ( (uint8_t)(bits->outbfr>>24) ); /* write to stream */
	    *(bits->buf++) = ( (uint8_t)(bits->outbfr>>16) ); /* write to stream */
		*(bits->buf++) = ( (uint8_t)(bits->outbfr>>8 ) ); /* write to stream */
    	*(bits->buf++) = ( (uint8_t)(bits->outbfr )	 ); /* write to stream */

		bits->outcnt += 32 - n;
		bits->bytecnt+=4;
		bits->outbfr = val;
	}
}

int32_t CloseOutBITS( BITS_T *bits )
{
	bits->bytecnt += (32-bits->outcnt)/8;
	bits->bytecnt += ((32-bits->outcnt) & 7 ) != 0 ? 1 : 0 ;
	if ( bits->outcnt < 32 )
	{	
		bits->outbfr = bits->outbfr << bits->outcnt;
		bits->outcnt = 32-bits->outcnt; //Jeff found this bug!!! (twice...it used to be above the prior line, then commented out, and now it's here!)
		if ( bits->outcnt >=24 )
		{
			*(bits->buf++) = ( (uint8_t)(bits->outbfr>>24) ); /* write to stream */
			*(bits->buf++) = ( (uint8_t)(bits->outbfr>>16) ); /* write to stream */
			*(bits->buf++) = ( (uint8_t)(bits->outbfr>>8) ); /* write to stream */
			*(bits->buf) |= (uint8_t)(bits->outbfr);
		} else
		if ( bits->outcnt >=16 )
		{
			*(bits->buf++) = ( (uint8_t)(bits->outbfr>>24) ); /* write to stream */
			*(bits->buf++) = ( (uint8_t)(bits->outbfr>>16) ); /* write to stream */
			*(bits->buf) |= (uint8_t)(bits->outbfr>>8);
		} else
		if ( bits->outcnt >=8 )
		{
			*(bits->buf++) = ( (uint8_t)(bits->outbfr>>24) ); /* write to stream */
			*(bits->buf) |= (uint8_t)(bits->outbfr>>16);
		} else
		if ( bits->outcnt > 0 )
		{
		    *(bits->buf) |= (uint8_t)(bits->outbfr>>24);
		}
	}

	bits->outcnt = 32;
	bits->outbfr = 0;
	return 	bits->bytecnt;

}

void AlignOutBITS( BITS_T *bits )                
{
	PutOutBITS( bits, 0, bits->outcnt & 7  );
}

int32_t BitCountBITS( BITS_T *bits )                 
{
	return 8 * bits->bytecnt + (32 - bits->outcnt);
}

int32_t ByteCountBITS( BITS_T *bits )                  
{
	return bits->bytecnt;
}

int32_t CopyOutBits( BITS_T *bits, char* out_buf, int size )                 
{
	int bytes;
	if ( bits->outcnt < 32 )
		PutOutBITS( bits, 0, bits->outcnt );

	bytes =  size > bits->bytecnt ? bits->bytecnt : size ;
	memcpy( out_buf, bits->buf_start, bytes );
	if ( bytes < bits->bytecnt )
	{
		memcpy( bits->buf_start, bits->buf_start+bytes, ( bits->bytecnt - bytes ) );
		bits->bytecnt -= bytes;
		bits->buf -= bytes;
	} else
	{
		bits->bytecnt = 0;
		bits->buf = bits->buf_start;
	}

	return bytes;
}

