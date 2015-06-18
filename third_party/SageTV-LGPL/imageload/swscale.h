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

#ifndef SWSCALE_H
#define SWSCALE_H

/**
 * @file swscale.h
 * @brief 
 *     external api for the swscale stuff
 */

#ifdef __cplusplus
extern "C" {
#endif

#define AV_STRINGIFY(s)         AV_TOSTRING(s)
#define AV_TOSTRING(s) #s

#define LIBSWSCALE_VERSION_INT  ((0<<16)+(5<<8)+0)
#define LIBSWSCALE_VERSION      0.5.0
#define LIBSWSCALE_BUILD        LIBSWSCALE_VERSION_INT

#define LIBSWSCALE_IDENT        "SwS" AV_STRINGIFY(LIBSWSCALE_VERSION)

/* values for the flags, the stuff on the command line is different */
#define SWS_FAST_BILINEAR 1
#define SWS_BILINEAR 2
#define SWS_BICUBIC  4
#define SWS_X        8
#define SWS_POINT    0x10
#define SWS_AREA     0x20
#define SWS_BICUBLIN 0x40
#define SWS_GAUSS    0x80
#define SWS_SINC     0x100
#define SWS_LANCZOS  0x200
#define SWS_SPLINE   0x400

#define SWS_SRC_V_CHR_DROP_MASK		0x30000
#define SWS_SRC_V_CHR_DROP_SHIFT	16

#define SWS_PARAM_DEFAULT		123456

#define SWS_PRINT_INFO		0x1000

//the following 3 flags are not completly implemented
//internal chrominace subsamling info
#define SWS_FULL_CHR_H_INT	0x2000
//input subsampling info
#define SWS_FULL_CHR_H_INP	0x4000
#define SWS_DIRECT_BGR		0x8000
#define SWS_ACCURATE_RND	0x40000

#define SWS_CPU_CAPS_MMX   0x80000000
#define SWS_CPU_CAPS_MMX2  0x20000000
#define SWS_CPU_CAPS_3DNOW 0x40000000
#define SWS_CPU_CAPS_ALTIVEC 0x10000000

#define SWS_MAX_REDUCE_CUTOFF 0.002

#define SWS_CS_ITU709		1
#define SWS_CS_FCC 		4
#define SWS_CS_ITU601		5
#define SWS_CS_ITU624		5
#define SWS_CS_SMPTE170M 	5
#define SWS_CS_SMPTE240M 	7
#define SWS_CS_DEFAULT 		5



// when used for filters they must have an odd number of elements
// coeffs cannot be shared between vectors
typedef struct {
	double *coeff;
	int length;
} SwsVector;

// vectors can be shared
typedef struct {
	SwsVector *lumH;
	SwsVector *lumV;
	SwsVector *chrH;
	SwsVector *chrV;
} SwsFilter;

struct SwsContext;

void sws_freeContext(struct SwsContext *swsContext);

struct SwsContext *sws_getContext(int srcW, int srcH, int srcFormat, int dstW, int dstH, int dstFormat, int flags,
			 double *param);
int sws_scale(struct SwsContext *context, uint8_t* src, int srcStride, int srcSliceY,
                           int srcSliceH, uint8_t* dst, int dstStride);


SwsVector *sws_getGaussianVec(double variance, double quality);
SwsVector *sws_getConstVec(double c, int length);
SwsVector *sws_getIdentityVec(void);
void sws_scaleVec(SwsVector *a, double scalar);
void sws_normalizeVec(SwsVector *a, double height);
void sws_convVec(SwsVector *a, SwsVector *b);
void sws_addVec(SwsVector *a, SwsVector *b);
void sws_subVec(SwsVector *a, SwsVector *b);
void sws_shiftVec(SwsVector *a, int shift);
SwsVector *sws_cloneVec(SwsVector *a);

void sws_printVec(SwsVector *a);
void sws_freeVec(SwsVector *a);

void sws_freeFilter(SwsFilter *filter);

/**
 * Pixel format. Notes:
 *
 * PIX_FMT_RGB32 is handled in an endian-specific manner. A RGBA
 * color is put together as:
 *  (A << 24) | (R << 16) | (G << 8) | B
 * This is stored as BGRA on little endian CPU architectures and ARGB on
 * big endian CPUs.
 *
 * When the pixel format is palettized RGB (PIX_FMT_PAL8), the palettized
 * image data is stored in AVFrame.data[0]. The palette is transported in
 * AVFrame.data[1] and, is 1024 bytes long (256 4-byte entries) and is
 * formatted the same as in PIX_FMT_RGB32 described above (i.e., it is
 * also endian-specific). Note also that the individual RGB palette
 * components stored in AVFrame.data[1] should be in the range 0..255.
 * This is important as many custom PAL8 video codecs that were designed
 * to run on the IBM VGA graphics adapter use 6-bit palette components.
 */
enum PixelFormat {
    PIX_FMT_NONE= -1,
    PIX_FMT_YUV420P,   ///< Planar YUV 4:2:0, 12bpp, (1 Cr & Cb sample per 2x2 Y samples)
    PIX_FMT_YUYV422,   ///< Packed YUV 4:2:2, 16bpp, Y0 Cb Y1 Cr
    PIX_FMT_RGB24,     ///< Packed RGB 8:8:8, 24bpp, RGBRGB...
    PIX_FMT_BGR24,     ///< Packed RGB 8:8:8, 24bpp, BGRBGR...
    PIX_FMT_YUV422P,   ///< Planar YUV 4:2:2, 16bpp, (1 Cr & Cb sample per 2x1 Y samples)
    PIX_FMT_YUV444P,   ///< Planar YUV 4:4:4, 24bpp, (1 Cr & Cb sample per 1x1 Y samples)
    PIX_FMT_RGB32,     ///< Packed RGB 8:8:8, 32bpp, (msb)8A 8R 8G 8B(lsb), in cpu endianness
    PIX_FMT_YUV410P,   ///< Planar YUV 4:1:0,  9bpp, (1 Cr & Cb sample per 4x4 Y samples)
    PIX_FMT_YUV411P,   ///< Planar YUV 4:1:1, 12bpp, (1 Cr & Cb sample per 4x1 Y samples)
    PIX_FMT_RGB565,    ///< Packed RGB 5:6:5, 16bpp, (msb)   5R 6G 5B(lsb), in cpu endianness
    PIX_FMT_RGB555,    ///< Packed RGB 5:5:5, 16bpp, (msb)1A 5R 5G 5B(lsb), in cpu endianness most significant bit to 1
    PIX_FMT_GRAY8,     ///<        Y        ,  8bpp
    PIX_FMT_MONOWHITE, ///<        Y        ,  1bpp, 1 is white
    PIX_FMT_MONOBLACK, ///<        Y        ,  1bpp, 0 is black
    PIX_FMT_PAL8,      ///< 8 bit with PIX_FMT_RGB32 palette
    PIX_FMT_YUVJ420P,  ///< Planar YUV 4:2:0, 12bpp, full scale (jpeg)
    PIX_FMT_YUVJ422P,  ///< Planar YUV 4:2:2, 16bpp, full scale (jpeg)
    PIX_FMT_YUVJ444P,  ///< Planar YUV 4:4:4, 24bpp, full scale (jpeg)
    PIX_FMT_XVMC_MPEG2_MC,///< XVideo Motion Acceleration via common packet passing(xvmc_render.h)
    PIX_FMT_XVMC_MPEG2_IDCT,
    PIX_FMT_UYVY422,   ///< Packed YUV 4:2:2, 16bpp, Cb Y0 Cr Y1
    PIX_FMT_UYYVYY411, ///< Packed YUV 4:1:1, 12bpp, Cb Y0 Y1 Cr Y2 Y3
    PIX_FMT_BGR32,     ///< Packed RGB 8:8:8, 32bpp, (msb)8A 8B 8G 8R(lsb), in cpu endianness
    PIX_FMT_BGR565,    ///< Packed RGB 5:6:5, 16bpp, (msb)   5B 6G 5R(lsb), in cpu endianness
    PIX_FMT_BGR555,    ///< Packed RGB 5:5:5, 16bpp, (msb)1A 5B 5G 5R(lsb), in cpu endianness most significant bit to 1
    PIX_FMT_BGR8,      ///< Packed RGB 3:3:2,  8bpp, (msb)2B 3G 3R(lsb)
    PIX_FMT_BGR4,      ///< Packed RGB 1:2:1,  4bpp, (msb)1B 2G 1R(lsb)
    PIX_FMT_BGR4_BYTE, ///< Packed RGB 1:2:1,  8bpp, (msb)1B 2G 1R(lsb)
    PIX_FMT_RGB8,      ///< Packed RGB 3:3:2,  8bpp, (msb)2R 3G 3B(lsb)
    PIX_FMT_RGB4,      ///< Packed RGB 1:2:1,  4bpp, (msb)2R 3G 3B(lsb)
    PIX_FMT_RGB4_BYTE, ///< Packed RGB 1:2:1,  8bpp, (msb)2R 3G 3B(lsb)
    PIX_FMT_NV12,      ///< Planar YUV 4:2:0, 12bpp, 1 plane for Y and 1 for UV
    PIX_FMT_NV21,      ///< as above, but U and V bytes are swapped

    PIX_FMT_RGB32_1,   ///< Packed RGB 8:8:8, 32bpp, (msb)8R 8G 8B 8A(lsb), in cpu endianness
    PIX_FMT_BGR32_1,   ///< Packed RGB 8:8:8, 32bpp, (msb)8B 8G 8R 8A(lsb), in cpu endianness

    PIX_FMT_NB,        ///< number of pixel formats, DO NOT USE THIS if you want to link with shared libav* because the number of formats might differ between versions
};

#ifdef __cplusplus
}
#endif

#endif
