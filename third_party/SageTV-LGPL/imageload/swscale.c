/*
 * Copyright (C) 2001-2003 Michael Niedermayer <michaelni@gmx.at>
 *
 * This file is part of FFmpeg.
 *
 * FFmpeg is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * FFmpeg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FFmpeg; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 *
 * the C code (not assembly, mmx, ...) of the swscaler which has been written
 * by Michael Niedermayer can be used under the LGPL license too
 */

/*
  supported Input formats: YV12, I420/IYUV, YUY2, UYVY, BGR32, BGR24, BGR16, BGR15, RGB32, RGB24, Y8/Y800, YVU9/IF09
  supported output formats: YV12, I420/IYUV, YUY2, UYVY, {BGR,RGB}{1,4,8,15,16,24,32}, Y8/Y800, YVU9/IF09
  {BGR,RGB}{1,4,8,15,16} support dithering

  unscaled special converters (YV12=I420=IYUV, Y800=Y8)
  YV12 -> {BGR,RGB}{1,4,8,15,16,24,32}
  x -> x
  YUV9 -> YV12
  YUV9/YV12 -> Y800
  Y800 -> YUV9/YV12
  BGR24 -> BGR32 & RGB24 -> RGB32
  BGR32 -> BGR24 & RGB32 -> RGB24
  BGR15 -> BGR16
*/

/*
tested special converters (most are tested actually but i didnt write it down ...)
 YV12 -> BGR16
 YV12 -> YV12
 BGR15 -> BGR16
 BGR16 -> BGR16
 YVU9 -> YV12

untested special converters
  YV12/I420 -> BGR15/BGR24/BGR32 (its the yuv2rgb stuff, so it should be ok)
  YV12/I420 -> YV12/I420
  YUY2/BGR15/BGR24/BGR32/RGB24/RGB32 -> same format
  BGR24 -> BGR32 & RGB24 -> RGB32
  BGR32 -> BGR24 & RGB32 -> RGB24
  BGR24 -> YV12
*/

#include <inttypes.h>
#include <string.h>
#include <math.h>
#include <stdio.h>
#include <unistd.h>
//#include "config.h"
#include <assert.h>
#ifdef HAVE_MALLOC_H
#include <malloc.h>
#else
#include <stdlib.h>
#endif
#ifdef HAVE_SYS_MMAN_H
#include <sys/mman.h>
#if defined(MAP_ANON) && !defined(MAP_ANONYMOUS)
#define MAP_ANONYMOUS MAP_ANON
#endif
#endif
#include "swscale.h"
#include "swscale_internal.h"
//#include "x86_cpu.h"
//#include "bswap.h"
//#include "rgb2rgb.h"
#ifdef USE_FASTMEMCPY
#include "libvo/fastmemcpy.h"
#endif

#undef MOVNTQ
#undef PAVGB

//#undef HAVE_MMX2
//#define HAVE_3DNOW
//#undef HAVE_MMX
//#undef ARCH_X86
//#define WORDS_BIGENDIAN
#define DITHER1XBPP

#define FAST_BGR2YV12 // use 7 bit coeffs instead of 15bit

#define RET 0xC3 //near return opcode for X86

#ifdef MP_DEBUG
#define ASSERT(x) assert(x);
#else
#define ASSERT(x) ;
#endif

#ifdef M_PI
#define PI M_PI
#else
#define PI 3.14159265358979323846
#endif

#define isSupportedIn(x)  ((x)==PIX_FMT_RGB32|| (x)==PIX_FMT_BGR24|| (x)==PIX_FMT_BGR32|| (x)==PIX_FMT_RGB24)
#define isSupportedOut(x) (isRGB(x) || isBGR(x))
#define isPacked(x)    (isRGB(x) || isBGR(x))

#define RGB2YUV_SHIFT 16
#define BY ((int)( 0.098*(1<<RGB2YUV_SHIFT)+0.5))
#define BV ((int)(-0.071*(1<<RGB2YUV_SHIFT)+0.5))
#define BU ((int)( 0.439*(1<<RGB2YUV_SHIFT)+0.5))
#define GY ((int)( 0.504*(1<<RGB2YUV_SHIFT)+0.5))
#define GV ((int)(-0.368*(1<<RGB2YUV_SHIFT)+0.5))
#define GU ((int)(-0.291*(1<<RGB2YUV_SHIFT)+0.5))
#define RY ((int)( 0.257*(1<<RGB2YUV_SHIFT)+0.5))
#define RV ((int)( 0.439*(1<<RGB2YUV_SHIFT)+0.5))
#define RU ((int)(-0.148*(1<<RGB2YUV_SHIFT)+0.5))

/*
NOTES
Special versions: fast Y 1:1 scaling (no interpolation in y direction)

TODO
more intelligent missalignment avoidance for the horizontal scaler
write special vertical cubic upscale version
Optimize C code (yv12 / minmax)
add support for packed pixel yuv input & output
add support for Y8 output
optimize bgr24 & bgr32
add BGR4 output support
write special BGR->BGR scaler
*/

#if defined(ARCH_X86) || defined(ARCH_X86_64)
static uint64_t attribute_used __attribute__((aligned(8))) bF8=       0xF8F8F8F8F8F8F8F8LL;
static uint64_t attribute_used __attribute__((aligned(8))) bFC=       0xFCFCFCFCFCFCFCFCLL;
static uint64_t __attribute__((aligned(8))) w10=       0x0010001000100010LL;
static uint64_t attribute_used __attribute__((aligned(8))) w02=       0x0002000200020002LL;
static uint64_t attribute_used __attribute__((aligned(8))) bm00001111=0x00000000FFFFFFFFLL;
static uint64_t attribute_used __attribute__((aligned(8))) bm00000111=0x0000000000FFFFFFLL;
static uint64_t attribute_used __attribute__((aligned(8))) bm11111000=0xFFFFFFFFFF000000LL;
static uint64_t attribute_used __attribute__((aligned(8))) bm01010101=0x00FF00FF00FF00FFLL;

static volatile uint64_t attribute_used __attribute__((aligned(8))) b5Dither;
static volatile uint64_t attribute_used __attribute__((aligned(8))) g5Dither;
static volatile uint64_t attribute_used __attribute__((aligned(8))) g6Dither;
static volatile uint64_t attribute_used __attribute__((aligned(8))) r5Dither;

static uint64_t __attribute__((aligned(8))) dither4[2]={
	0x0103010301030103LL,
	0x0200020002000200LL,};

static uint64_t __attribute__((aligned(8))) dither8[2]={
	0x0602060206020602LL,
	0x0004000400040004LL,};

static uint64_t __attribute__((aligned(8))) b16Mask=   0x001F001F001F001FLL;
static uint64_t attribute_used __attribute__((aligned(8))) g16Mask=   0x07E007E007E007E0LL;
static uint64_t attribute_used __attribute__((aligned(8))) r16Mask=   0xF800F800F800F800LL;
static uint64_t __attribute__((aligned(8))) b15Mask=   0x001F001F001F001FLL;
static uint64_t attribute_used __attribute__((aligned(8))) g15Mask=   0x03E003E003E003E0LL;
static uint64_t attribute_used __attribute__((aligned(8))) r15Mask=   0x7C007C007C007C00LL;

static uint64_t attribute_used __attribute__((aligned(8))) M24A=   0x00FF0000FF0000FFLL;
static uint64_t attribute_used __attribute__((aligned(8))) M24B=   0xFF0000FF0000FF00LL;
static uint64_t attribute_used __attribute__((aligned(8))) M24C=   0x0000FF0000FF0000LL;

#ifdef FAST_BGR2YV12
static const uint64_t bgr2YCoeff  attribute_used __attribute__((aligned(8))) = 0x000000210041000DULL;
static const uint64_t bgr2UCoeff  attribute_used __attribute__((aligned(8))) = 0x0000FFEEFFDC0038ULL;
static const uint64_t bgr2VCoeff  attribute_used __attribute__((aligned(8))) = 0x00000038FFD2FFF8ULL;
#else
static const uint64_t bgr2YCoeff  attribute_used __attribute__((aligned(8))) = 0x000020E540830C8BULL;
static const uint64_t bgr2UCoeff  attribute_used __attribute__((aligned(8))) = 0x0000ED0FDAC23831ULL;
static const uint64_t bgr2VCoeff  attribute_used __attribute__((aligned(8))) = 0x00003831D0E6F6EAULL;
#endif /* FAST_BGR2YV12 */
static const uint64_t bgr2YOffset attribute_used __attribute__((aligned(8))) = 0x1010101010101010ULL;
static const uint64_t bgr2UVOffset attribute_used __attribute__((aligned(8)))= 0x8080808080808080ULL;
static const uint64_t w1111       attribute_used __attribute__((aligned(8))) = 0x0001000100010001ULL;
#endif /* defined(ARCH_X86) || defined(ARCH_X86_64) */

// clipping helper table for C implementations:
static unsigned char clip_table[768];


const uint8_t  __attribute__((aligned(8))) dither_2x2_4[2][8]={
{  1,   3,   1,   3,   1,   3,   1,   3, },
{  2,   0,   2,   0,   2,   0,   2,   0, },
};

const uint8_t  __attribute__((aligned(8))) dither_2x2_8[2][8]={
{  6,   2,   6,   2,   6,   2,   6,   2, },
{  0,   4,   0,   4,   0,   4,   0,   4, },
};

const uint8_t  __attribute__((aligned(8))) dither_8x8_32[8][8]={
{ 17,   9,  23,  15,  16,   8,  22,  14, },
{  5,  29,   3,  27,   4,  28,   2,  26, },
{ 21,  13,  19,  11,  20,  12,  18,  10, },
{  0,  24,   6,  30,   1,  25,   7,  31, },
{ 16,   8,  22,  14,  17,   9,  23,  15, },
{  4,  28,   2,  26,   5,  29,   3,  27, },
{ 20,  12,  18,  10,  21,  13,  19,  11, },
{  1,  25,   7,  31,   0,  24,   6,  30, },
};

const uint8_t  __attribute__((aligned(8))) dither_8x8_73[8][8]={
{  0,  55,  14,  68,   3,  58,  17,  72, },
{ 37,  18,  50,  32,  40,  22,  54,  35, },
{  9,  64,   5,  59,  13,  67,   8,  63, },
{ 46,  27,  41,  23,  49,  31,  44,  26, },
{  2,  57,  16,  71,   1,  56,  15,  70, },
{ 39,  21,  52,  34,  38,  19,  51,  33, },
{ 11,  66,   7,  62,  10,  65,   6,  60, },
{ 48,  30,  43,  25,  47,  29,  42,  24, },
};

const uint8_t  __attribute__((aligned(8))) dither_8x8_220[8][8]={
{117,  62, 158, 103, 113,  58, 155, 100, },
{ 34, 199,  21, 186,  31, 196,  17, 182, },
{144,  89, 131,  76, 141,  86, 127,  72, },
{  0, 165,  41, 206,  10, 175,  52, 217, },
{110,  55, 151,  96, 120,  65, 162, 107, },
{ 28, 193,  14, 179,  38, 203,  24, 189, },
{138,  83, 124,  69, 148,  93, 134,  79, },
{  7, 172,  48, 213,   3, 168,  45, 210, },
};

static inline void rgb24to32(const uint8_t *src,uint8_t *dst,long src_size)
{
  uint8_t *dest = dst;
  const uint8_t *s = src;
  const uint8_t *end;
  end = s + src_size;
  while(s < end)
  {
#ifdef WORDS_BIGENDIAN
    /* RGB24 (= R,G,B) -> RGB32 (= A,B,G,R) */
    *dest++ = 0;
    *dest++ = s[2];
    *dest++ = s[1];
    *dest++ = s[0];
    s+=3;
#else
    *dest++ = *s++;
    *dest++ = *s++;
    *dest++ = *s++;
    *dest++ = 0;
#endif
  }
}

static inline void rgb32to24(const uint8_t *src,uint8_t *dst,long src_size)
{
  uint8_t *dest = dst;
  const uint8_t *s = src;
  const uint8_t *end;
  end = s + src_size;
  while(s < end)
  {
#ifdef WORDS_BIGENDIAN
    /* RGB32 (= A,B,G,R) -> RGB24 (= R,G,B) */
    s++;
    dest[2] = *s++;
    dest[1] = *s++;
    dest[0] = *s++;
    dest += 3;
#else
    *dest++ = *s++;
    *dest++ = *s++;
    *dest++ = *s++;
    s++;
#endif
  }
}

static inline void rgb24tobgr24(const uint8_t *src, uint8_t *dst, long src_size)
{
	unsigned i;
	for(i=0; i<src_size; i+=3)
	{
		register uint8_t x;
		x          = src[i + 2];
		dst[i + 1] = src[i + 1];
		dst[i + 2] = src[i + 0];
		dst[i + 0] = x;
	}
}

static inline void rgb32tobgr32(const uint8_t *src, uint8_t *dst, long src_size)
{
	unsigned i;
	unsigned num_pixels = src_size >> 2;
	for(i=0; i<num_pixels; i++)
	{
#ifdef WORDS_BIGENDIAN
	  dst[4*i + 1] = src[4*i + 3];
	  dst[4*i + 2] = src[4*i + 2];
	  dst[4*i + 3] = src[4*i + 1];
#else
	  dst[4*i + 0] = src[4*i + 2];
	  dst[4*i + 1] = src[4*i + 1];
	  dst[4*i + 2] = src[4*i + 0];
#endif
	}
}

void rgb32tobgr24(const uint8_t *src, uint8_t *dst, long src_size)
{
	long i;
	long num_pixels = src_size >> 2;
	for(i=0; i<num_pixels; i++)
	{
		#ifdef WORDS_BIGENDIAN
			/* RGB32 (= A,B,G,R) -> BGR24 (= B,G,R) */
			dst[3*i + 0] = src[4*i + 1];
			dst[3*i + 1] = src[4*i + 2];
			dst[3*i + 2] = src[4*i + 3];
		#else
			dst[3*i + 0] = src[4*i + 2];
			dst[3*i + 1] = src[4*i + 1];
			dst[3*i + 2] = src[4*i + 0];
		#endif
	}
}

void rgb24tobgr32(const uint8_t *src, uint8_t *dst, long src_size)
{
	long i;
	for(i=0; 3*i<src_size; i++)
	{
		#ifdef WORDS_BIGENDIAN
			/* RGB24 (= R,G,B) -> BGR32 (= A,R,G,B) */
			dst[4*i + 0] = 0;
			dst[4*i + 1] = src[3*i + 0];
			dst[4*i + 2] = src[3*i + 1];
			dst[4*i + 3] = src[3*i + 2];
		#else
			dst[4*i + 0] = src[3*i + 2];
			dst[4*i + 1] = src[3*i + 1];
			dst[4*i + 2] = src[3*i + 0];
			dst[4*i + 3] = 0;
		#endif
	}
}

char *sws_format_name(enum PixelFormat format)
{
    switch (format) {
        case PIX_FMT_YUV420P:
            return "yuv420p";
        case PIX_FMT_YUYV422:
            return "yuyv422";
        case PIX_FMT_RGB24:
            return "rgb24";
        case PIX_FMT_BGR24:
            return "bgr24";
        case PIX_FMT_YUV422P:
            return "yuv422p";
        case PIX_FMT_YUV444P:
            return "yuv444p";
        case PIX_FMT_RGB32:
            return "rgb32";
        case PIX_FMT_YUV410P:
            return "yuv410p";
        case PIX_FMT_YUV411P:
            return "yuv411p";
        case PIX_FMT_RGB565:
            return "rgb565";
        case PIX_FMT_RGB555:
            return "rgb555";
        case PIX_FMT_GRAY8:
            return "gray8";
        case PIX_FMT_MONOWHITE:
            return "mono white";
        case PIX_FMT_MONOBLACK:
            return "mono black";
        case PIX_FMT_PAL8:
            return "Palette";
        case PIX_FMT_YUVJ420P:
            return "yuvj420p";
        case PIX_FMT_YUVJ422P:
            return "yuvj422p";
        case PIX_FMT_YUVJ444P:
            return "yuvj444p";
        case PIX_FMT_XVMC_MPEG2_MC:
            return "xvmc_mpeg2_mc";
        case PIX_FMT_XVMC_MPEG2_IDCT:
            return "xvmc_mpeg2_idct";
        case PIX_FMT_UYVY422:
            return "uyvy422";
        case PIX_FMT_UYYVYY411:
            return "uyyvyy411";
        case PIX_FMT_RGB32_1:
            return "rgb32x";
        case PIX_FMT_BGR32_1:
            return "bgr32x";
        case PIX_FMT_BGR32:
            return "bgr32";
        case PIX_FMT_BGR565:
            return "bgr565";
        case PIX_FMT_BGR555:
            return "bgr555";
        case PIX_FMT_BGR8:
            return "bgr8";
        case PIX_FMT_BGR4:
            return "bgr4";
        case PIX_FMT_BGR4_BYTE:
            return "bgr4 byte";
        case PIX_FMT_RGB8:
            return "rgb8";
        case PIX_FMT_RGB4:
            return "rgb4";
        case PIX_FMT_RGB4_BYTE:
            return "rgb4 byte";
        case PIX_FMT_NV12:
            return "nv12";
        case PIX_FMT_NV21:
            return "nv21";
        default:
            return "Unknown format";
    }
}

#define YSCALE_YUV_2_PACKEDX_C(type) \
		for(i=0; i<(dstW>>1); i++){\
			int j;\
			int Y1=1<<18;\
			int Y2=1<<18;\
			int U=1<<18;\
			int V=1<<18;\
			type *r, *b, *g;\
			const int i2= 2*i;\
			\
			for(j=0; j<lumFilterSize; j++)\
			{\
				Y1 += lumSrc[j][i2] * lumFilter[j];\
				Y2 += lumSrc[j][i2+1] * lumFilter[j];\
			}\
			for(j=0; j<chrFilterSize; j++)\
			{\
				U += chrSrc[j][i] * chrFilter[j];\
				V += chrSrc[j][i+2048] * chrFilter[j];\
			}\
			Y1>>=19;\
			Y2>>=19;\
			U >>=19;\
			V >>=19;\
			if((Y1|Y2|U|V)&256)\
			{\
				if(Y1>255)   Y1=255;\
				else if(Y1<0)Y1=0;\
				if(Y2>255)   Y2=255;\
				else if(Y2<0)Y2=0;\
				if(U>255)    U=255;\
				else if(U<0) U=0;\
				if(V>255)    V=255;\
				else if(V<0) V=0;\
			}

#define YSCALE_YUV_2_RGBX_C(type) \
			YSCALE_YUV_2_PACKEDX_C(type)\
			r = c->table_rV[V];\
			g = c->table_gU[U] + c->table_gV[V];\
			b = c->table_bU[U];\

#define YSCALE_YUV_2_PACKED2_C \
		for(i=0; i<(dstW>>1); i++){\
			const int i2= 2*i;\
			int Y1= (buf0[i2  ]*yalpha1+buf1[i2  ]*yalpha)>>19;\
			int Y2= (buf0[i2+1]*yalpha1+buf1[i2+1]*yalpha)>>19;\
			int U= (uvbuf0[i     ]*uvalpha1+uvbuf1[i     ]*uvalpha)>>19;\
			int V= (uvbuf0[i+2048]*uvalpha1+uvbuf1[i+2048]*uvalpha)>>19;\

#define YSCALE_YUV_2_RGB2_C(type) \
			YSCALE_YUV_2_PACKED2_C\
			type *r, *b, *g;\
			r = c->table_rV[V];\
			g = c->table_gU[U] + c->table_gV[V];\
			b = c->table_bU[U];\

#define YSCALE_YUV_2_PACKED1_C \
		for(i=0; i<(dstW>>1); i++){\
			const int i2= 2*i;\
			int Y1= buf0[i2  ]>>7;\
			int Y2= buf0[i2+1]>>7;\
			int U= (uvbuf1[i     ])>>7;\
			int V= (uvbuf1[i+2048])>>7;\

#define YSCALE_YUV_2_RGB1_C(type) \
			YSCALE_YUV_2_PACKED1_C\
			type *r, *b, *g;\
			r = c->table_rV[V];\
			g = c->table_gU[U] + c->table_gV[V];\
			b = c->table_bU[U];\

#define YSCALE_YUV_2_PACKED1B_C \
		for(i=0; i<(dstW>>1); i++){\
			const int i2= 2*i;\
			int Y1= buf0[i2  ]>>7;\
			int Y2= buf0[i2+1]>>7;\
			int U= (uvbuf0[i     ] + uvbuf1[i     ])>>8;\
			int V= (uvbuf0[i+2048] + uvbuf1[i+2048])>>8;\

#define YSCALE_YUV_2_RGB1B_C(type) \
			YSCALE_YUV_2_PACKED1B_C\
			type *r, *b, *g;\
			r = c->table_rV[V];\
			g = c->table_gU[U] + c->table_gV[V];\
			b = c->table_bU[U];\

#define YSCALE_YUV_2_ANYRGB_C(func)\
	switch(c->dstFormat)\
	{\
	case PIX_FMT_RGB32:\
	case PIX_FMT_BGR32:\
		func(uint32_t)\
			((uint32_t*)dest)[i2+0]= r[Y1] + g[Y1] + b[Y1];\
			((uint32_t*)dest)[i2+1]= r[Y2] + g[Y2] + b[Y2];\
		}		\
		break;\
	case PIX_FMT_RGB24:\
		func(uint8_t)\
			((uint8_t*)dest)[0]= r[Y1];\
			((uint8_t*)dest)[1]= g[Y1];\
			((uint8_t*)dest)[2]= b[Y1];\
			((uint8_t*)dest)[3]= r[Y2];\
			((uint8_t*)dest)[4]= g[Y2];\
			((uint8_t*)dest)[5]= b[Y2];\
			dest+=6;\
		}\
		break;\
	case PIX_FMT_BGR24:\
		func(uint8_t)\
			((uint8_t*)dest)[0]= b[Y1];\
			((uint8_t*)dest)[1]= g[Y1];\
			((uint8_t*)dest)[2]= r[Y1];\
			((uint8_t*)dest)[3]= b[Y2];\
			((uint8_t*)dest)[4]= g[Y2];\
			((uint8_t*)dest)[5]= r[Y2];\
			dest+=6;\
		}\
		break;\



//Note: we have C, X86, MMX, MMX2, 3DNOW version therse no 3DNOW+MMX2 one
//Plain C versions
//#if !defined (HAVE_MMX) || defined (RUNTIME_CPUDETECT)
#define COMPILE_C
//#endif
/*
#ifdef ARCH_POWERPC
#if defined (HAVE_ALTIVEC) || defined (RUNTIME_CPUDETECT)
#define COMPILE_ALTIVEC
#endif //HAVE_ALTIVEC
#endif //ARCH_POWERPC

#if defined(ARCH_X86) || defined(ARCH_X86_64)

#if (defined (HAVE_MMX) && !defined (HAVE_3DNOW) && !defined (HAVE_MMX2)) || defined (RUNTIME_CPUDETECT)
#define COMPILE_MMX
#endif

#if defined (HAVE_MMX2) || defined (RUNTIME_CPUDETECT)
#define COMPILE_MMX2
#endif

#if (defined (HAVE_3DNOW) && !defined (HAVE_MMX2)) || defined (RUNTIME_CPUDETECT)
#define COMPILE_3DNOW
#endif
#endif //ARCH_X86 || ARCH_X86_64

#undef HAVE_MMX
#undef HAVE_MMX2
#undef HAVE_3DNOW
*/
#ifdef COMPILE_C
#undef HAVE_MMX
#undef HAVE_MMX2
#undef HAVE_3DNOW
#undef HAVE_ALTIVEC
#define RENAME(a) a ## _C
#include "swscale_template.c"
#endif

static double getSplineCoeff(double a, double b, double c, double d, double dist)
{
//	printf("%f %f %f %f %f\n", a,b,c,d,dist);
	if(dist<=1.0) 	return ((d*dist + c)*dist + b)*dist +a;
	else		return getSplineCoeff(	0.0,
						 b+ 2.0*c + 3.0*d,
						        c + 3.0*d,
						-b- 3.0*c - 6.0*d,
						dist-1.0);
}

static inline int initFilter(int16_t **outFilter, int16_t **filterPos, int *outFilterSize, int xInc,
			      int srcW, int dstW, int filterAlign, int one, int flags,
			      double param[2])
{
	int i;
	int filterSize;
	int filter2Size;
	int minFilterSize;
	double *filter=NULL;
	double *filter2=NULL;

	// Note the +1 is for the MMXscaler which reads over the end
	*filterPos = av_malloc((dstW+1)*sizeof(int16_t));

	if(FFABS(xInc - 0x10000) <10) // unscaled
	{
		int i;
		filterSize= 1;
		filter= av_malloc(dstW*sizeof(double)*filterSize);
		for(i=0; i<dstW*filterSize; i++) filter[i]=0;

		for(i=0; i<dstW; i++)
		{
			filter[i*filterSize]=1;
			(*filterPos)[i]=i;
		}

	}
	else if(flags&SWS_POINT) // lame looking point sampling mode
	{
		int i;
		int xDstInSrc;
		filterSize= 1;
		filter= av_malloc(dstW*sizeof(double)*filterSize);

		xDstInSrc= xInc/2 - 0x8000;
		for(i=0; i<dstW; i++)
		{
			int xx= (xDstInSrc - ((filterSize-1)<<15) + (1<<15))>>16;

			(*filterPos)[i]= xx;
			filter[i]= 1.0;
			xDstInSrc+= xInc;
		}
	}
	else if((xInc <= (1<<16) && (flags&SWS_AREA)) || (flags&SWS_FAST_BILINEAR)) // bilinear upscale
	{
		int i;
		int xDstInSrc;
		if     (flags&SWS_BICUBIC) filterSize= 4;
		else if(flags&SWS_X      ) filterSize= 4;
		else			   filterSize= 2; // SWS_BILINEAR / SWS_AREA
		filter= av_malloc(dstW*sizeof(double)*filterSize);

		xDstInSrc= xInc/2 - 0x8000;
		for(i=0; i<dstW; i++)
		{
			int xx= (xDstInSrc - ((filterSize-1)<<15) + (1<<15))>>16;
			int j;

			(*filterPos)[i]= xx;
				//Bilinear upscale / linear interpolate / Area averaging
				for(j=0; j<filterSize; j++)
				{
					double d= FFABS((xx<<16) - xDstInSrc)/(double)(1<<16);
					double coeff= 1.0 - d;
					if(coeff<0) coeff=0;
					filter[i*filterSize + j]= coeff;
					xx++;
				}
			xDstInSrc+= xInc;
		}
	}
	else
	{
		double xDstInSrc;
		double sizeFactor, filterSizeInSrc;
		const double xInc1= (double)xInc / (double)(1<<16);

		if     (flags&SWS_BICUBIC)	sizeFactor= 4.0;
		else if(flags&SWS_X)		sizeFactor= 8.0;
		else if(flags&SWS_AREA)		sizeFactor= 1.0; //downscale only, for upscale it is bilinear
		else if(flags&SWS_GAUSS)	sizeFactor= 8.0;   // infinite ;)
		else if(flags&SWS_LANCZOS)	sizeFactor= param[0] != SWS_PARAM_DEFAULT ? 2.0*param[0] : 6.0;
		else if(flags&SWS_SINC)		sizeFactor= 20.0; // infinite ;)
		else if(flags&SWS_SPLINE)	sizeFactor= 20.0;  // infinite ;)
		else if(flags&SWS_BILINEAR)	sizeFactor= 2.0;
		else {
			sizeFactor= 0.0; //GCC warning killer
			ASSERT(0)
		}

		if(xInc1 <= 1.0)	filterSizeInSrc= sizeFactor; // upscale
		else			filterSizeInSrc= sizeFactor*srcW / (double)dstW;

		filterSize= (int)ceil(1 + filterSizeInSrc); // will be reduced later if possible
		if(filterSize > srcW-2)
		{
			filterSize=srcW-2;
			if (filterSize == 0)
				filterSize = 1;
		}

		filter= av_malloc(dstW*sizeof(double)*filterSize);

		xDstInSrc= xInc1 / 2.0 - 0.5;
		for(i=0; i<dstW; i++)
		{
			int xx= (int)(xDstInSrc - (filterSize-1)*0.5 + 0.5);
			int j;
			(*filterPos)[i]= xx;
			for(j=0; j<filterSize; j++)
			{
				double d= FFABS(xx - xDstInSrc)/filterSizeInSrc*sizeFactor;
				double coeff;
				if(flags & SWS_BICUBIC)
				{
					double B= param[0] != SWS_PARAM_DEFAULT ? param[0] : 0.0;
					double C= param[1] != SWS_PARAM_DEFAULT ? param[1] : 0.6;

					if(d<1.0)
						coeff = (12-9*B-6*C)*d*d*d + (-18+12*B+6*C)*d*d + 6-2*B;
					else if(d<2.0)
						coeff = (-B-6*C)*d*d*d + (6*B+30*C)*d*d + (-12*B-48*C)*d +8*B+24*C;
					else
						coeff=0.0;
				}
/*				else if(flags & SWS_X)
				{
					double p= param ? param*0.01 : 0.3;
					coeff = d ? sin(d*PI)/(d*PI) : 1.0;
					coeff*= pow(2.0, - p*d*d);
				}*/
				else if(flags & SWS_X)
				{
					double A= param[0] != SWS_PARAM_DEFAULT ? param[0] : 1.0;

					if(d<1.0)
						coeff = cos(d*PI);
					else
						coeff=-1.0;
					if(coeff<0.0) 	coeff= -pow(-coeff, A);
					else		coeff=  pow( coeff, A);
					coeff= coeff*0.5 + 0.5;
				}
				else if(flags & SWS_AREA)
				{
					double srcPixelSize= 1.0/xInc1;
					if(d + srcPixelSize/2 < 0.5) coeff= 1.0;
					else if(d - srcPixelSize/2 < 0.5) coeff= (0.5-d)/srcPixelSize + 0.5;
					else coeff=0.0;
				}
				else if(flags & SWS_GAUSS)
				{
					double p= param[0] != SWS_PARAM_DEFAULT ? param[0] : 3.0;
					coeff = pow(2.0, - p*d*d);
				}
				else if(flags & SWS_SINC)
				{
					coeff = d ? sin(d*PI)/(d*PI) : 1.0;
				}
				else if(flags & SWS_LANCZOS)
				{
					double p= param[0] != SWS_PARAM_DEFAULT ? param[0] : 3.0;
					coeff = d ? sin(d*PI)*sin(d*PI/p)/(d*d*PI*PI/p) : 1.0;
					if(d>p) coeff=0;
				}
				else if(flags & SWS_BILINEAR)
				{
					coeff= 1.0 - d;
					if(coeff<0) coeff=0;
				}
				else if(flags & SWS_SPLINE)
				{
					double p=-2.196152422706632;
					coeff = getSplineCoeff(1.0, 0.0, p, -p-1.0, d);
				}
				else {
					coeff= 0.0; //GCC warning killer
					ASSERT(0)
				}

				filter[i*filterSize + j]= coeff;
				xx++;
			}
			xDstInSrc+= xInc1;
		}
	}

	/* apply src & dst Filter to filter -> filter2
	   av_free(filter);
	*/
	ASSERT(filterSize>0)
	filter2Size= filterSize;
	ASSERT(filter2Size>0)
	filter2= av_malloc(filter2Size*dstW*sizeof(double));

	for(i=0; i<dstW; i++)
	{
		int j;
		SwsVector scaleFilter;
		SwsVector *outVec;

		scaleFilter.coeff= filter + i*filterSize;
		scaleFilter.length= filterSize;

		outVec= &scaleFilter;

		ASSERT(outVec->length == filter2Size)

		for(j=0; j<outVec->length; j++)
		{
			filter2[i*filter2Size + j]= outVec->coeff[j];
		}

		(*filterPos)[i]+= (filterSize-1)/2 - (filter2Size-1)/2;

		if(outVec != &scaleFilter) sws_freeVec(outVec);
	}
	av_free(filter); filter=NULL;

	/* try to reduce the filter-size (step1 find size and shift left) */
	// Assume its near normalized (*0.5 or *2.0 is ok but * 0.001 is not)
	minFilterSize= 0;
	for(i=dstW-1; i>=0; i--)
	{
		int min= filter2Size;
		int j;
		double cutOff=0.0;

		/* get rid off near zero elements on the left by shifting left */
		for(j=0; j<filter2Size; j++)
		{
			int k;
			cutOff += FFABS(filter2[i*filter2Size]);

			if(cutOff > SWS_MAX_REDUCE_CUTOFF) break;

			/* preserve Monotonicity because the core can't handle the filter otherwise */
			if(i<dstW-1 && (*filterPos)[i] >= (*filterPos)[i+1]) break;

			// Move filter coeffs left
			for(k=1; k<filter2Size; k++)
				filter2[i*filter2Size + k - 1]= filter2[i*filter2Size + k];
			filter2[i*filter2Size + k - 1]= 0.0;
			(*filterPos)[i]++;
		}

		cutOff=0.0;
		/* count near zeros on the right */
		for(j=filter2Size-1; j>0; j--)
		{
			cutOff += FFABS(filter2[i*filter2Size + j]);

			if(cutOff > SWS_MAX_REDUCE_CUTOFF) break;
			min--;
		}

		if(min>minFilterSize) minFilterSize= min;
	}

        if (flags & SWS_CPU_CAPS_ALTIVEC) {
          // we can handle the special case 4,
          // so we don't want to go to the full 8
          if (minFilterSize < 5)
            filterAlign = 4;

          // we really don't want to waste our time
          // doing useless computation, so fall-back on
          // the scalar C code for very small filter.
          // vectorizing is worth it only if you have
          // decent-sized vector.
          if (minFilterSize < 3)
            filterAlign = 1;
        }

        if (flags & SWS_CPU_CAPS_MMX) {
                // special case for unscaled vertical filtering
                if(minFilterSize == 1 && filterAlign == 2)
                        filterAlign= 1;
        }

	ASSERT(minFilterSize > 0)
	filterSize= (minFilterSize +(filterAlign-1)) & (~(filterAlign-1));
	ASSERT(filterSize > 0)
		filter= av_malloc(filterSize*dstW*sizeof(double));
        if(filterSize >= MAX_FILTER_SIZE)
                return -1;
	*outFilterSize= filterSize;

	if(flags&SWS_PRINT_INFO)
		MSG_V("SwScaler: reducing / aligning filtersize %d -> %d\n", filter2Size, filterSize);
	/* try to reduce the filter-size (step2 reduce it) */
	for(i=0; i<dstW; i++)
	{
		int j;

		for(j=0; j<filterSize; j++)
		{
			if(j>=filter2Size) filter[i*filterSize + j]= 0.0;
			else		   filter[i*filterSize + j]= filter2[i*filter2Size + j];
		}
	}
	av_free(filter2); filter2=NULL;


	//FIXME try to align filterpos if possible

	//fix borders
	for(i=0; i<dstW; i++)
	{
		int j;
		if((*filterPos)[i] < 0)
		{
			// Move filter coeffs left to compensate for filterPos
			for(j=1; j<filterSize; j++)
			{
				int left= FFMAX(j + (*filterPos)[i], 0);
				filter[i*filterSize + left] += filter[i*filterSize + j];
				filter[i*filterSize + j]=0;
			}
			(*filterPos)[i]= 0;
		}

		if((*filterPos)[i] + filterSize > srcW)
		{
			int shift= (*filterPos)[i] + filterSize - srcW;
			// Move filter coeffs right to compensate for filterPos
			for(j=filterSize-2; j>=0; j--)
			{
				int right= FFMIN(j + shift, filterSize-1);
				filter[i*filterSize +right] += filter[i*filterSize +j];
				filter[i*filterSize +j]=0;
			}
			(*filterPos)[i]= srcW - filterSize;
		}
	}

	// Note the +1 is for the MMXscaler which reads over the end
	/* align at 16 for AltiVec (needed by hScale_altivec_real) */
	*outFilter= av_malloc(*outFilterSize*(dstW+1)*sizeof(int16_t));
	memset(*outFilter, 0, *outFilterSize*(dstW+1)*sizeof(int16_t));

	/* Normalize & Store in outFilter */
	for(i=0; i<dstW; i++)
	{
		int j;
		double error=0;
		double sum=0;
		double scale= one;

		for(j=0; j<filterSize; j++)
		{
			sum+= filter[i*filterSize + j];
		}
		scale/= sum;
		for(j=0; j<*outFilterSize; j++)
		{
			double v= filter[i*filterSize + j]*scale + error;
			int intV= floor(v + 0.5);
			(*outFilter)[i*(*outFilterSize) + j]= intV;
			error = v - intV;
		}
	}

	(*filterPos)[dstW]= (*filterPos)[dstW-1]; // the MMX scaler will read over the end
	for(i=0; i<*outFilterSize; i++)
	{
		int j= dstW*(*outFilterSize);
		(*outFilter)[j + i]= (*outFilter)[j + i - (*outFilterSize)];
	}

	av_free(filter);
        return 0;
}

static void globalInit(void){
    // generating tables:
    int i;
    for(i=0; i<768; i++){
	int c= FFMIN(FFMAX(i-256, 0), 255);
	clip_table[i]=c;
    }
}

static SwsFunc getSwsFunc(int flags){

	return swScale_C;
}

/* {RGB,BGR}{24,32} -> {RGB,BGR}{24,32} */
static int rgb2rgbWrapper(SwsContext *c, uint8_t* src, int srcStride, int srcSliceY,
			   int srcSliceH, uint8_t* dst, int dstStride){
	const int srcFormat= c->srcFormat;
	const int dstFormat= c->dstFormat;
	const int srcBpp= (fmt_depth(srcFormat) + 7) >> 3;
	const int dstBpp= (fmt_depth(dstFormat) + 7) >> 3;
	const int srcId= fmt_depth(srcFormat) >> 2; /* 1:0, 4:1, 8:2, 15:3, 16:4, 24:6, 32:8 */
	const int dstId= fmt_depth(dstFormat) >> 2;
	void (*conv)(const uint8_t *src, uint8_t *dst, long src_size)=NULL;

	/* BGR -> BGR */
	if(   (isBGR(srcFormat) && isBGR(dstFormat))
	   || (isRGB(srcFormat) && isRGB(dstFormat))){
		switch(srcId | (dstId<<4)){
		case 0x68: conv= rgb32to24; break;
		case 0x86: conv= rgb24to32; break;
		default: MSG_ERR("swScaler: internal error %s -> %s converter\n",
				 sws_format_name(srcFormat), sws_format_name(dstFormat)); break;
		}
	}else if(   (isBGR(srcFormat) && isRGB(dstFormat))
		 || (isRGB(srcFormat) && isBGR(dstFormat))){
		switch(srcId | (dstId<<4)){
		case 0x66: conv= rgb24tobgr24; break;
		case 0x68: conv= rgb32tobgr24; break;
		case 0x86: conv= rgb24tobgr32; break;
		case 0x88: conv= rgb32tobgr32; break;
		default: MSG_ERR("swScaler: internal error %s -> %s converter\n",
				 sws_format_name(srcFormat), sws_format_name(dstFormat)); break;
		}
	}else{
		MSG_ERR("swScaler: internal error %s -> %s converter\n",
			 sws_format_name(srcFormat), sws_format_name(dstFormat));
	}

	if(dstStride*srcBpp == srcStride*dstBpp)
		conv(src, dst + dstStride*srcSliceY, srcSliceH*srcStride);
	else
	{
		int i;
		uint8_t *srcPtr= src;
		uint8_t *dstPtr= dst + dstStride*srcSliceY;

		for(i=0; i<srcSliceH; i++)
		{
			conv(srcPtr, dstPtr, c->srcW*srcBpp);
			srcPtr+= srcStride;
			dstPtr+= dstStride;
		}
	}
	return srcSliceH;
}


/* unscaled copy like stuff (assumes nearly identical formats) */
static int simpleCopy(SwsContext *c, uint8_t* src, int srcStride, int srcSliceY,
             int srcSliceH, uint8_t* dst, int dstStride)
{

	// NOTE: Narflex: I changed this to work properly for subimages; but restricted it to 32 bpp
	int i;
	uint8_t *srcPtr= src;
	uint8_t *dstPtr= dst + dstStride*srcSliceY;
	int length = c->srcW * 4;
	for (i=0;i<srcSliceH;i++)
	{
		memcpy(dstPtr, srcPtr, length);
		srcPtr+= srcStride;
		dstPtr+= dstStride;
	}
	return srcSliceH;


/*	if(dstStride==srcStride && srcStride > 0)
		memcpy(dst + dstStride*srcSliceY, src, srcSliceH*dstStride);
	else
	{
		int i;
		uint8_t *srcPtr= src;
		uint8_t *dstPtr= dst + dstStride*srcSliceY;
		int length=0;

		/* universal length finder */
/*		while(length+c->srcW <= FFABS(dstStride)
		   && length+c->srcW <= FFABS(srcStride)) length+= c->srcW;
		ASSERT(length!=0);

		for(i=0; i<srcSliceH; i++)
		{
			memcpy(dstPtr, srcPtr, length);
			srcPtr+= srcStride;
			dstPtr+= dstStride;
		}
	}
	return srcSliceH;*/
}

static uint16_t roundToInt16(int64_t f){
	int r= (f + (1<<15))>>16;
	     if(r<-0x7FFF) return 0x8000;
	else if(r> 0x7FFF) return 0x7FFF;
	else               return r;
}

static int div_round (int dividend, int divisor)
{
    if (dividend > 0)
	return (dividend + (divisor>>1)) / divisor;
    else
	return -((-dividend + (divisor>>1)) / divisor);
}

SwsContext *sws_getContext(int srcW, int srcH, int srcFormat, int dstW, int dstH, int dstFormat, int flags,
                         double *param){

	SwsContext *c;
	int i;
	int usesVFilter, usesHFilter;
	int unscaled;
	int srcRange, dstRange;

	flags &= ~(SWS_CPU_CAPS_MMX|SWS_CPU_CAPS_MMX2|SWS_CPU_CAPS_3DNOW|SWS_CPU_CAPS_ALTIVEC);

	if(clip_table[512] != 255) globalInit();

	unscaled = (srcW == dstW && srcH == dstH);

	if(!isSupportedIn(srcFormat))
	{
		MSG_ERR("swScaler: %s is not supported as input format\n", sws_format_name(srcFormat));
		return NULL;
	}
	if(!isSupportedOut(dstFormat))
	{
		MSG_ERR("swScaler: %s is not supported as output format\n", sws_format_name(dstFormat));
		return NULL;
	}

	/* sanity check */
	if(srcW<4 || srcH<1 || dstW<8 || dstH<1) //FIXME check if these are enough and try to lowwer them after fixing the relevant parts of the code
	{
		 MSG_ERR("swScaler: %dx%d -> %dx%d is invalid scaling dimension\n",
			srcW, srcH, dstW, dstH);
		return NULL;
	}

	c= av_malloc(sizeof(SwsContext));
	memset(c, 0, sizeof(SwsContext));

	c->srcW= srcW;
	c->srcH= srcH;
	c->dstW= dstW;
	c->dstH= dstH;
	c->lumXInc= ((srcW<<16) + (dstW>>1))/dstW;
	c->lumYInc= ((srcH<<16) + (dstH>>1))/dstH;
	c->flags= flags;
	c->dstFormat= dstFormat;
	c->srcFormat= srcFormat;
        c->vRounder= 4* 0x0001000100010001ULL;

	usesHFilter= usesVFilter= 0;

	c->chrSrcHSubSample = c->chrSrcVSubSample = c->chrDstHSubSample = c->chrDstVSubSample = 0;

	if(param){
		c->param[0] = param[0];
		c->param[1] = param[1];
	}else{
		c->param[0] =
		c->param[1] = SWS_PARAM_DEFAULT;
	}

	c->chrIntHSubSample= c->chrDstHSubSample;
	c->chrIntVSubSample= c->chrSrcVSubSample;

	// note the -((-x)>>y) is so that we allways round toward +inf
	c->chrSrcW= -((-srcW) >> c->chrSrcHSubSample);
	c->chrSrcH= -((-srcH) >> c->chrSrcVSubSample);
	c->chrDstW= -((-dstW) >> c->chrDstHSubSample);
	c->chrDstH= -((-dstH) >> c->chrDstVSubSample);

	/* unscaled special Cases */
	if(unscaled && !usesHFilter && !usesVFilter)
	{
		/* rgb/bgr -> rgb/bgr (no dither needed forms) */
		if(   (isBGR(srcFormat) || isRGB(srcFormat))
		   && (isBGR(dstFormat) || isRGB(dstFormat)))
			c->swScale= rgb2rgbWrapper;

		/* simple copy */
		if(   srcFormat == dstFormat
		   || (isPlanarYUV(srcFormat) && isGray(dstFormat))
		   || (isPlanarYUV(dstFormat) && isGray(srcFormat))
		  )
		{
			c->swScale= simpleCopy;
		}

		if(c->swScale){
			if(flags&SWS_PRINT_INFO)
				MSG_INFO("SwScaler: using unscaled %s -> %s special converter\n",
					sws_format_name(srcFormat), sws_format_name(dstFormat));
			return c;
		}
	}

	c->chrXInc= ((c->chrSrcW<<16) + (c->chrDstW>>1))/c->chrDstW;
	c->chrYInc= ((c->chrSrcH<<16) + (c->chrDstH>>1))/c->chrDstH;

	/* precalculate horizontal scaler filter coefficients */
	{
		const int filterAlign=1;

		initFilter(&c->hLumFilter, &c->hLumFilterPos, &c->hLumFilterSize, c->lumXInc,
				 srcW      ,       dstW, filterAlign, 1<<14,
				 (flags&SWS_BICUBLIN) ? (flags|SWS_BICUBIC)  : flags,
				 c->param);

	} // Init Horizontal stuff



	/* precalculate vertical scaler filter coefficients */
	{
		const int filterAlign=1;
		initFilter(&c->vLumFilter, &c->vLumFilterPos, &c->vLumFilterSize, c->lumYInc,
				srcH      ,        dstH, filterAlign, (1<<12),//(1<<12)-4,
				(flags&SWS_BICUBLIN) ? (flags|SWS_BICUBIC)  : flags,
				c->param);

	}

	// Calculate Buffer Sizes so that they won't run out while handling these damn slices
	c->vLumBufSize= c->vLumFilterSize;
	for(i=0; i<dstH; i++)
	{
		int nextSlice= c->vLumFilterPos[i] + c->vLumFilterSize - 1;

		nextSlice>>= c->chrSrcVSubSample;
		nextSlice<<= c->chrSrcVSubSample;
		if(c->vLumFilterPos[i] + c->vLumBufSize < nextSlice)
			c->vLumBufSize= nextSlice - c->vLumFilterPos[i   ];
	}

	// allocate pixbufs (we use dynamic allocation because otherwise we would need to
	// This is a 2-D array because we need to store multiple lines after horizontal filtering
	// for the inputs to the vertical filters
	c->lumPixBuf= av_malloc(c->vLumBufSize*2*sizeof(uint8_t*));
	//Note we need at least one pixel more at the end because of the mmx code (just in case someone wanna replace the 4000/8000)
	/* align at 16 bytes for AltiVec */
	// We upped this to 16000 from 4000 because we're putting 32bpp in this array for ARGB scaling
	for(i=0; i<c->vLumBufSize; i++)
		c->lumPixBuf[i]= c->lumPixBuf[i+c->vLumBufSize]= av_malloc((dstW+1)*4);

	//try to avoid drawing green stuff between the right end and the stride end
	for(i=0; i<c->vLumBufSize; i++) memset(c->lumPixBuf[i], 0, (dstW+1)*4);

	ASSERT(c->chrDstH <= dstH)

	if(flags&SWS_PRINT_INFO)
	{
#ifdef DITHER1XBPP
		char *dither= " dithered";
#else
		char *dither= "";
#endif
		if(flags&SWS_FAST_BILINEAR)
			MSG_INFO("\nSwScaler: FAST_BILINEAR scaler, ");
		else if(flags&SWS_BILINEAR)
			MSG_INFO("\nSwScaler: BILINEAR scaler, ");
		else if(flags&SWS_BICUBIC)
			MSG_INFO("\nSwScaler: BICUBIC scaler, ");
		else if(flags&SWS_X)
			MSG_INFO("\nSwScaler: Experimental scaler, ");
		else if(flags&SWS_POINT)
			MSG_INFO("\nSwScaler: Nearest Neighbor / POINT scaler, ");
		else if(flags&SWS_AREA)
			MSG_INFO("\nSwScaler: Area Averageing scaler, ");
		else if(flags&SWS_BICUBLIN)
			MSG_INFO("\nSwScaler: luma BICUBIC / chroma BILINEAR scaler, ");
		else if(flags&SWS_GAUSS)
			MSG_INFO("\nSwScaler: Gaussian scaler, ");
		else if(flags&SWS_SINC)
			MSG_INFO("\nSwScaler: Sinc scaler, ");
		else if(flags&SWS_LANCZOS)
			MSG_INFO("\nSwScaler: Lanczos scaler, ");
		else if(flags&SWS_SPLINE)
			MSG_INFO("\nSwScaler: Bicubic spline scaler, ");
		else
			MSG_INFO("\nSwScaler: ehh flags invalid?! ");

		if(dstFormat==PIX_FMT_BGR555 || dstFormat==PIX_FMT_BGR565)
			MSG_INFO("from %s to%s %s ",
				sws_format_name(srcFormat), dither, sws_format_name(dstFormat));
		else
			MSG_INFO("from %s to %s ",
				sws_format_name(srcFormat), sws_format_name(dstFormat));

		MSG_INFO("using C\n");
	}

	if(flags & SWS_PRINT_INFO)
	{
		if(flags & SWS_FAST_BILINEAR)
			MSG_V("SwScaler: using FAST_BILINEAR C scaler for horizontal scaling\n");
		else
			MSG_V("SwScaler: using C scaler for horizontal scaling\n");
		if(c->vLumFilterSize==1 && c->vChrFilterSize==2)
			MSG_V("SwScaler: using 1-tap %s \"scaler\" for vertical luminance scaling (BGR)\n"
				   "SwScaler:       2-tap scaler for vertical chrominance scaling (BGR)\n",(flags & SWS_CPU_CAPS_MMX) ? "MMX" : "C");
		else if(c->vLumFilterSize==2 && c->vChrFilterSize==2)
			MSG_V("SwScaler: using 2-tap linear %s scaler for vertical scaling (BGR)\n", (flags & SWS_CPU_CAPS_MMX) ? "MMX" : "C");
		else
			MSG_V("SwScaler: using n-tap %s scaler for vertical scaling (BGR)\n", (flags & SWS_CPU_CAPS_MMX) ? "MMX" : "C");

		if(dstFormat==PIX_FMT_BGR24)
			MSG_V("SwScaler: using %s YV12->BGR24 Converter\n",
				(flags & SWS_CPU_CAPS_MMX2) ? "MMX2" : ((flags & SWS_CPU_CAPS_MMX) ? "MMX" : "C"));
		else if(dstFormat==PIX_FMT_RGB32)
			MSG_V("SwScaler: using %s YV12->BGR32 Converter\n", (flags & SWS_CPU_CAPS_MMX) ? "MMX" : "C");
		else if(dstFormat==PIX_FMT_BGR565)
			MSG_V("SwScaler: using %s YV12->BGR16 Converter\n", (flags & SWS_CPU_CAPS_MMX) ? "MMX" : "C");
		else if(dstFormat==PIX_FMT_BGR555)
			MSG_V("SwScaler: using %s YV12->BGR15 Converter\n", (flags & SWS_CPU_CAPS_MMX) ? "MMX" : "C");

		MSG_V("SwScaler: %dx%d -> %dx%d\n", srcW, srcH, dstW, dstH);
	}
	if(flags & SWS_PRINT_INFO)
	{
		MSG_DBG2("SwScaler:Lum srcW=%d srcH=%d dstW=%d dstH=%d xInc=%d yInc=%d\n",
			c->srcW, c->srcH, c->dstW, c->dstH, c->lumXInc, c->lumYInc);
		MSG_DBG2("SwScaler:Chr srcW=%d srcH=%d dstW=%d dstH=%d xInc=%d yInc=%d\n",
			c->chrSrcW, c->chrSrcH, c->chrDstW, c->chrDstH, c->chrXInc, c->chrYInc);
	}

	c->swScale= getSwsFunc(flags);
	return c;
}

/**
 * swscale warper, so we don't need to export the SwsContext
 */
int sws_scale(SwsContext *c, uint8_t* src, int srcStride, int srcSliceY,
                           int srcSliceH, uint8_t* dst, int dstStride){
//printf("sws: slice %d %d\n", srcSliceY, srcSliceH);

	return c->swScale(c, src, srcStride, srcSliceY, srcSliceH, dst, dstStride);
}

void sws_freeVec(SwsVector *a){
	if(!a) return;
	av_free(a->coeff);
	a->coeff=NULL;
	a->length=0;
	av_free(a);
}

void sws_freeFilter(SwsFilter *filter){
	if(!filter) return;

	if(filter->lumH) sws_freeVec(filter->lumH);
	if(filter->lumV) sws_freeVec(filter->lumV);
	if(filter->chrH) sws_freeVec(filter->chrH);
	if(filter->chrV) sws_freeVec(filter->chrV);
	av_free(filter);
}


void sws_freeContext(SwsContext *c){
	int i;
	if(!c) return;

	if(c->lumPixBuf)
	{
		for(i=0; i<c->vLumBufSize; i++)
		{
			av_free(c->lumPixBuf[i]);
			c->lumPixBuf[i]=NULL;
		}
		av_free(c->lumPixBuf);
		c->lumPixBuf=NULL;
	}

	if(c->chrPixBuf)
	{
		for(i=0; i<c->vChrBufSize; i++)
		{
			av_free(c->chrPixBuf[i]);
			c->chrPixBuf[i]=NULL;
		}
		av_free(c->chrPixBuf);
		c->chrPixBuf=NULL;
	}

	av_free(c->vLumFilter);
	c->vLumFilter = NULL;
	av_free(c->vChrFilter);
	c->vChrFilter = NULL;
	av_free(c->hLumFilter);
	c->hLumFilter = NULL;
	av_free(c->hChrFilter);
	c->hChrFilter = NULL;

	av_free(c->vLumFilterPos);
	c->vLumFilterPos = NULL;
	av_free(c->vChrFilterPos);
	c->vChrFilterPos = NULL;
	av_free(c->hLumFilterPos);
	c->hLumFilterPos = NULL;
	av_free(c->hChrFilterPos);
	c->hChrFilterPos = NULL;

	av_free(c);
}
