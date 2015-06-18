/*
 * Copyright (C) 2001-2003 Michael Niedermayer <michaelni@gmx.at>
 *
 * This file is part of FFmpeg.
 *
 * FFmpeg is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * FFmpeg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with FFmpeg; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#ifndef SWSCALE_INTERNAL_H
#define SWSCALE_INTERNAL_H

#ifdef HAVE_ALTIVEC_H
#include <altivec.h>
#endif

//#include "avutil.h"

#ifdef CONFIG_DARWIN
#define AVV(x...) (x)
#else
#define AVV(x...) {x}
#endif

#define av_malloc malloc
#define av_free(x) (x ? free(x) : 0)
#define FFMAX(a,b) ((a) > (b) ? (a) : (b))
#define FFMIN(a,b) ((a) > (b) ? (b) : (a))
#define FFABS(a) ((a) >= 0 ? (a) : (-(a)))

#define MSG_WARN(args...) fprintf(stderr, ##args )
#define MSG_FATAL(args...) fprintf(stderr, ##args )
#define MSG_ERR(args...) fprintf(stderr, ##args )
#define MSG_V(args...) fprintf(stderr, ##args )
#define MSG_DBG2(args...) fprintf(stderr, ##args )
#define MSG_INFO(args...) fprintf(stderr, ##args )

#define MAX_FILTER_SIZE 256

typedef int (*SwsFunc)(struct SwsContext *context, uint8_t* src, int srcStride, int srcSliceY,
             int srcSliceH, uint8_t* dst, int dstStride);

#ifdef WORDS_BIGENDIAN
#define PIX_FMT_RGBA PIX_FMT_RGB32_1
#define PIX_FMT_BGRA PIX_FMT_BGR32_1
#define PIX_FMT_ARGB PIX_FMT_RGB32
#define PIX_FMT_ABGR PIX_FMT_BGR32
#else
#define PIX_FMT_RGBA PIX_FMT_BGR32
#define PIX_FMT_BGRA PIX_FMT_RGB32
#define PIX_FMT_ARGB PIX_FMT_BGR32_1
#define PIX_FMT_ABGR PIX_FMT_RGB32_1
#endif

/* this struct should be aligned on at least 32-byte boundary */
typedef struct SwsContext{
	/**
	 *
	 * Note the src,dst,srcStride,dstStride will be copied, in the sws_scale() warper so they can freely be modified here
	 */
	SwsFunc swScale;
	int srcW, srcH, dstH;
	int chrSrcW, chrSrcH, chrDstW, chrDstH;
	int lumXInc, chrXInc;
	int lumYInc, chrYInc;
	int dstFormat, srcFormat;               ///< format 4:2:0 type is allways YV12
	int origDstFormat, origSrcFormat;       ///< format
	int chrSrcHSubSample, chrSrcVSubSample;
	int chrIntHSubSample, chrIntVSubSample;
	int chrDstHSubSample, chrDstVSubSample;
	int vChrDrop;
    int sliceDir;
	double param[2];

	uint8_t **lumPixBuf;
	int16_t **chrPixBuf;
	int16_t *hLumFilter;
	int16_t *hLumFilterPos;
	int16_t *hChrFilter;
	int16_t *hChrFilterPos;
	int16_t *vLumFilter;
	int16_t *vLumFilterPos;
	int16_t *vChrFilter;
	int16_t *vChrFilterPos;

	int hLumFilterSize;
	int hChrFilterSize;
	int vLumFilterSize;
	int vChrFilterSize;
	int vLumBufSize;
	int vChrBufSize;

	int lastInLumBuf;
	int lastInChrBuf;
	int lumBufIndex;
	int chrBufIndex;
	int dstY;
	int flags;

#define RED_DITHER   "0*8"
#define GREEN_DITHER "1*8"
#define BLUE_DITHER  "2*8"
#define Y_COEFF      "3*8"
#define VR_COEFF     "4*8"
#define UB_COEFF     "5*8"
#define VG_COEFF     "6*8"
#define UG_COEFF     "7*8"
#define Y_OFFSET     "8*8"
#define U_OFFSET     "9*8"
#define V_OFFSET     "10*8"
#define LUM_MMX_FILTER_OFFSET "11*8"
#define CHR_MMX_FILTER_OFFSET "11*8+4*4*256"
#define DSTW_OFFSET  "11*8+4*4*256*2" //do not change, its hardcoded in the asm
#define ESP_OFFSET  "11*8+4*4*256*2+8"
#define VROUNDER_OFFSET "11*8+4*4*256*2+16"
#define U_TEMP       "11*8+4*4*256*2+24"
#define V_TEMP       "11*8+4*4*256*2+32"

	int dstW;
	uint64_t esp __attribute__((aligned(8)));
	uint64_t vRounder     __attribute__((aligned(8)));
	uint64_t u_temp       __attribute__((aligned(8)));
	uint64_t v_temp       __attribute__((aligned(8)));


} SwsContext;
//FIXME check init (where 0)

char *sws_format_name(int format);

//FIXME replace this with something faster
#define isPlanarYUV(x) ((x)==PIX_FMT_YUV410P || (x)==PIX_FMT_YUV420P	\
			|| (x)==PIX_FMT_YUV411P || (x)==PIX_FMT_YUV422P	\
			|| (x)==PIX_FMT_YUV444P || (x)==PIX_FMT_NV12	\
			|| (x)==PIX_FMT_NV21)
#define isYUV(x)       ((x)==PIX_FMT_UYVY422 || (x)==PIX_FMT_YUYV422 || isPlanarYUV(x))
#define isGray(x)      ((x)==PIX_FMT_GRAY8)
#define isRGB(x)       ((x)==PIX_FMT_BGR32 || (x)==PIX_FMT_RGB24	\
			|| (x)==PIX_FMT_RGB565 || (x)==PIX_FMT_RGB555	\
			|| (x)==PIX_FMT_RGB8 || (x)==PIX_FMT_RGB4	\
			|| (x)==PIX_FMT_MONOBLACK)
#define isBGR(x)       ((x)==PIX_FMT_RGB32 || (x)==PIX_FMT_BGR24	\
			|| (x)==PIX_FMT_BGR565 || (x)==PIX_FMT_BGR555	\
			|| (x)==PIX_FMT_BGR8 || (x)==PIX_FMT_BGR4	\
			|| (x)==PIX_FMT_MONOBLACK)

static inline int fmt_depth(int fmt)
{
    switch(fmt) {
        case PIX_FMT_BGRA:
        case PIX_FMT_ABGR:
        case PIX_FMT_RGBA:
        case PIX_FMT_ARGB:
            return 32;
        case PIX_FMT_BGR24:
        case PIX_FMT_RGB24:
            return 24;
        case PIX_FMT_BGR565:
        case PIX_FMT_RGB565:
            return 16;
        case PIX_FMT_BGR555:
        case PIX_FMT_RGB555:
            return 15;
        case PIX_FMT_BGR8:
        case PIX_FMT_RGB8:
            return 8;
        case PIX_FMT_BGR4:
        case PIX_FMT_RGB4:
        case PIX_FMT_BGR4_BYTE:
        case PIX_FMT_RGB4_BYTE:
            return 4;
        case PIX_FMT_MONOBLACK:
            return 1;
        default:
            return 0;
    }
}

#endif
