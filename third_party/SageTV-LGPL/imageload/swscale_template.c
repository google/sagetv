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

#undef REAL_MOVNTQ
#undef MOVNTQ
#undef PAVGB
#undef PREFETCH
#undef PREFETCHW
#undef EMMS
#undef SFENCE

static inline void RENAME(vScale)(int16_t *lumFilter, uint8_t **lumSrc, int lumFilterSize,
				uint8_t *dest, int dstW)
{
	int i;
	for(i=0; i<dstW; i++)
	{
		int val=0;
		int j;
		for(j=0; j<lumFilterSize; j++)
			val += lumSrc[j][i] * lumFilter[j];

		dest[i]= FFMIN(FFMAX(val>>12, 0), 255);
	}
}

// Bilinear / Bicubic scaling
static inline void RENAME(hScale)(uint8_t *dst, int dstW, int dbpp, uint8_t *src, int srcW, int sbpp,
				  int16_t *filter, int16_t *filterPos, long filterSize, int xOffset)
{
	int i;
	int sOffset = xOffset;
	int dOffset = xOffset;
	if (sbpp == 4 && dbpp == 3)
		sOffset++; // skip the alpha channel on the source
	else if (sbpp == 3 && dbpp == 4)
	{
		dOffset++;
		if (xOffset == 3)
		{
			// Fill in the alpha channel
			for(i=0; i<dstW; i++)
			{
				// Don't use dOffset since it is 4 now instead of 0
				dst[i*dbpp /* + dOffset */] = 0xFF;
			}
			return;
		}
	}
	for(i=0; i<dstW; i++)
	{
		int j;
		int srcPos= filterPos[i];
		int val=0;
		int fsi = filterSize*i;
//		printf("filterPos: %d\n", filterPos[i]);
		for(j=0; j<filterSize; j++)
		{
//			printf("filter: %d, src: %d\n", filter[i], src[srcPos + j]);
			val += ((int)src[(srcPos + j) * sbpp + sOffset])*filter[fsi + j];
		}
//		filter += hFilterSize;
		dst[i*dbpp + dOffset] = FFMIN(FFMAX(0, val>>14), (1<<8)-1); // the cubic equation does overflow ...
//		dst[i] = val>>7;
	}
}

static int RENAME(swScale)(SwsContext *c, uint8_t* src, int srcStride, int srcSliceY,
             int srcSliceH, uint8_t* dst, int dstStride){

	/* load a few things into local vars to make the code more readable? and faster */
	const int srcW= c->srcW;
	const int dstW= c->dstW;
	const int dstH= c->dstH;
	const int dstFormat= c->dstFormat;
	const int srcFormat= c->srcFormat;
	const int flags= c->flags;
	int16_t *vLumFilterPos= c->vLumFilterPos;
	int16_t *hLumFilterPos= c->hLumFilterPos;
	int16_t *vLumFilter= c->vLumFilter;
	int16_t *hLumFilter= c->hLumFilter;
	const int vLumFilterSize= c->vLumFilterSize;
	const int hLumFilterSize= c->hLumFilterSize;
	uint8_t **lumPixBuf= c->lumPixBuf;
	const int vLumBufSize= c->vLumBufSize;
	int lastDstY;

	/* vars whch will change and which we need to storw back in the context */
	int dstY= c->dstY;
	int lumBufIndex= c->lumBufIndex;
	int lastInLumBuf= c->lastInLumBuf;
	
//	printf("swscale %X %X %X -> %X %X %X\n", (int)src[0], (int)src[1], (int)src[2],
//		(int)dst[0], (int)dst[1], (int)dst[2]);

//printf("sws Strides:%d %d %d -> %d %d %d\n", srcStride[0],srcStride[1],srcStride[2],
//dstStride[0],dstStride[1],dstStride[2]);

	/* Note the user might start scaling the picture in the middle so this will not get executed
	   this is not really intended but works currently, so ppl might do it */
	if(srcSliceY ==0){
		lumBufIndex=0;
		dstY=0;	
		lastInLumBuf= -1;
	}

	lastDstY= dstY;

	int sbpp = ((c->srcFormat == PIX_FMT_RGB32) || (c->srcFormat == PIX_FMT_BGR32)) ? 4 : 3;
	int dbpp = ((c->dstFormat == PIX_FMT_RGB32) || (c->dstFormat == PIX_FMT_BGR32)) ? 4 : 3;

	for(;dstY < dstH; dstY++){
		unsigned char *dest =dst+dstStride*dstY;

		const int firstLumSrcY= vLumFilterPos[dstY]; //First line needed as input
		const int lastLumSrcY= firstLumSrcY + vLumFilterSize -1; // Last line needed as input

//printf("dstY:%d dstH:%d firstLumSrcY:%d lastInLumBuf:%d vLumBufSize: %d vChrBufSize: %d slice: %d %d vLumFilterSize: %d firstChrSrcY: %d vChrFilterSize: %d c->chrSrcVSubSample: %d\n",
// dstY, dstH, firstLumSrcY, lastInLumBuf, vLumBufSize, vChrBufSize, srcSliceY, srcSliceH, vLumFilterSize, firstChrSrcY, vChrFilterSize,  c->chrSrcVSubSample);
		//handle holes (FAST_BILINEAR & weird filters)
		if(firstLumSrcY > lastInLumBuf) lastInLumBuf= firstLumSrcY-1;
//printf("%d %d %d\n", firstChrSrcY, lastInChrBuf, vChrBufSize);
		ASSERT(firstLumSrcY >= lastInLumBuf - vLumBufSize + 1)

		// Do we have enough lines in this slice to output the dstY line
		if(lastLumSrcY < srcSliceY + srcSliceH)
		{
			//Do horizontal scaling
			while(lastInLumBuf < lastLumSrcY)
			{
				uint8_t *s= src+(lastInLumBuf + 1 - srcSliceY)*srcStride;
				lumBufIndex++;
//				printf("%d %d %d %d\n", lumBufIndex, vLumBufSize, lastInLumBuf,  lastLumSrcY);
				ASSERT(lumBufIndex < 2*vLumBufSize)
				ASSERT(lastInLumBuf + 1 - srcSliceY < srcSliceH)
				ASSERT(lastInLumBuf + 1 - srcSliceY >= 0)
//				printf("%d %d\n", lumBufIndex, vLumBufSize);
				RENAME(hScale)(lumPixBuf[ lumBufIndex ], dstW, dbpp, s, srcW, sbpp, hLumFilter, hLumFilterPos, hLumFilterSize, 0);
				RENAME(hScale)(lumPixBuf[ lumBufIndex ], dstW, dbpp, s, srcW, sbpp, hLumFilter, hLumFilterPos, hLumFilterSize, 1);
				RENAME(hScale)(lumPixBuf[ lumBufIndex ], dstW, dbpp, s, srcW, sbpp, hLumFilter, hLumFilterPos, hLumFilterSize, 2);
				if (dbpp == 4)
					RENAME(hScale)(lumPixBuf[ lumBufIndex ], dstW, dbpp, s, srcW, sbpp, hLumFilter, hLumFilterPos, hLumFilterSize, 3);
				lastInLumBuf++;
			}
			//wrap buf index around to stay inside the ring buffer
			if(lumBufIndex >= vLumBufSize ) lumBufIndex-= vLumBufSize;
		}
		else // not enough lines left in this slice -> load the rest in the buffer
		{
/*		printf("%d %d Last:%d %d LastInBuf:%d %d Index:%d %d Y:%d FSize: %d %d BSize: %d %d\n",
			firstChrSrcY,firstLumSrcY,lastChrSrcY,lastLumSrcY,
			lastInChrBuf,lastInLumBuf,chrBufIndex,lumBufIndex,dstY,vChrFilterSize,vLumFilterSize,
			vChrBufSize, vLumBufSize);*/

			//Do horizontal scaling
			while(lastInLumBuf+1 < srcSliceY + srcSliceH)
			{
				uint8_t *s= src+(lastInLumBuf + 1 - srcSliceY)*srcStride;
				lumBufIndex++;
				ASSERT(lumBufIndex < 2*vLumBufSize)
				ASSERT(lastInLumBuf + 1 - srcSliceY < srcSliceH)
				ASSERT(lastInLumBuf + 1 - srcSliceY >= 0)

				RENAME(hScale)(lumPixBuf[ lumBufIndex ], dstW, dbpp, s, srcW, sbpp, hLumFilter, hLumFilterPos, hLumFilterSize, 0);
				RENAME(hScale)(lumPixBuf[ lumBufIndex ], dstW, dbpp, s, srcW, sbpp, hLumFilter, hLumFilterPos, hLumFilterSize, 1);
				RENAME(hScale)(lumPixBuf[ lumBufIndex ], dstW, dbpp, s, srcW, sbpp, hLumFilter, hLumFilterPos, hLumFilterSize, 2);
				if (dbpp == 4)
					RENAME(hScale)(lumPixBuf[ lumBufIndex ], dstW, dbpp, s, srcW, sbpp, hLumFilter, hLumFilterPos, hLumFilterSize, 3);
				lastInLumBuf++;
			}
			//wrap buf index around to stay inside the ring buffer
			if(lumBufIndex >= vLumBufSize ) lumBufIndex-= vLumBufSize;
			break; //we can't output a dstY line so let's try with the next slice
		}

		// Vertical filtering happens in here
		uint8_t **lumSrcPtr= lumPixBuf + lumBufIndex + firstLumSrcY - lastInLumBuf + vLumBufSize;
		ASSERT(lumSrcPtr + vLumFilterSize - 1 < lumPixBuf + vLumBufSize*2);
		RENAME(vScale)(vLumFilter + dstY*vLumFilterSize, lumSrcPtr, vLumFilterSize,
			dest, dbpp*dstW);
	}

	/* store changed local vars back in the context */
	c->dstY= dstY;
	c->lumBufIndex= lumBufIndex;
	c->lastInLumBuf= lastInLumBuf;

	return dstY - lastDstY;
}
