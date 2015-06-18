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

//input bits
typedef struct BITS_I
{
	const unsigned char *buffer;
	int bits_offset;
	int total_bits;
	int error_flag;
} BITS_I;

//output bits
typedef struct BITS_T
{
	unsigned long outbfr;  //save uncommited to buffer bits
	int outcnt;			   //bits in outbfr
	int	bytecnt;	       //in byte buffer 	
	unsigned char* buf;
	unsigned char* buf_start;
	int	buf_size;
} BITS_T;

//input bits
void  SkipBits( BITS_I* pBits, int nCodeBits  );
unsigned short U( BITS_I* pBits, int nCodeBits );
int   ReadBitsU( BITS_I* pBits, int nCodeBits  );

//golomb code bits
unsigned long UE( BITS_I *pBits, int *pCodeBits );
int SE( BITS_I *pBits, int *pCodeBits ) ;
unsigned int ReadUE( BITS_I *pBits );
int ReadSE( BITS_I *pBits );

//output bits
void InitOutBITS( BITS_T *bits, unsigned char* buf, int size );
void PutOutBITS( BITS_T *bits, unsigned long val, int n );
int  CloseOutBITS( BITS_T *bits );
void AlignOutBITS( BITS_T *bits );
long BitCountBITS( BITS_T *bits );
long ByteCountBITS( BITS_T *bits );
int  FlushOutBITS( BITS_T *bits, char* out_buf, int size );

#ifdef __cplusplus
}
#endif

#endif
