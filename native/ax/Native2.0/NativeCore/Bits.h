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

#ifndef MEPG_BITS_H
#define MEPG_BITS_H

#ifdef __cplusplus
extern "C" {
#endif

#define __STDC_FORMAT_MACROS
#include <inttypes.h>

//input bits
typedef struct BITS_I
{
	const uint8_t *buffer;
	int32_t bits_offset;
	int32_t total_bits;
	int32_t error_flag;
} BITS_I;

//output bits
typedef struct BITS_T
{
	uint32_t outbfr;  //save uncommited to buffer bits
	int32_t  outcnt;			   //bits in outbfr
	int32_t  bytecnt;	       //in byte buffer 	
	uint8_t* buf;
	uint8_t* buf_start;
	int32_t  buf_size;
} BITS_T;

//input bits
void     SkipBits( BITS_I* pBits, int nCodeBits  );
uint16_t U( BITS_I* pBits, int nCodeBits );
int32_t  ReadBitsU( BITS_I* pBits, int nCodeBits  );

//golomb code bits
uint32_t UE( BITS_I *pBits, int *pCodeBits );
int32_t  SE( BITS_I *pBits, int *pCodeBits ) ;
uint32_t ReadUE( BITS_I *pBits );
int32_t  ReadSE( BITS_I *pBits );

//output bits
void InitOutBITS( BITS_T *bits, uint8_t* buf, int size );
void PutOutBITS( BITS_T *bits, uint32_t val, int n );
int32_t  CloseOutBITS( BITS_T *bits );
void AlignOutBITS( BITS_T *bits );
int32_t BitCountBITS( BITS_T *bits );
int32_t ByteCountBITS( BITS_T *bits );
int32_t FlushOutBITS( BITS_T *bits, char* out_buf, int size );

#ifdef __cplusplus
}
#endif

#endif
