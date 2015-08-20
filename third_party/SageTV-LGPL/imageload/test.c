/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
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
#include <inttypes.h>
#include <stdarg.h>
#include <fcntl.h>

#include "swscale.h"
#include "swscale_internal.h"
#include "imageload.h"


#define W 960
#define H 360

#define SCALE 4

int main_scaler(int argc, char **argv){
	printf("Starting...\r\n");fflush(stdout);
	uint8_t *src_data = malloc(W*H*4/(SCALE*SCALE));
	int src_stride=4*W/SCALE;
	uint8_t *dest_data = malloc(W*H*4);
	int dest_stride=W*4;
	int x, y;
	struct SwsContext *sws;
	memset(dest_data, 0, W*H*4);
	printf("Creating scaling context...\r\n");fflush(stdout);
	sws= sws_getContext(W/SCALE, H/SCALE, PIX_FMT_RGB32, W, H, PIX_FMT_RGB32, 0x1002, NULL);
    printf("Creating source image...\r\n");fflush(stdout);
	for(y=0; y<H/SCALE; y++){
		for(x=0; x<W/SCALE; x++){
			src_data[ 4*x + y*4*W/SCALE]= 0xFF;
			src_data[ 4*x + 1 + y*4*W/SCALE]= ((x+y)*255/(2*W/SCALE)) & 0xFF;
			src_data[ 4*x + 2 + y*4*W/SCALE]= ((x+y)*255/(2*W/SCALE)) & 0xEF;
			src_data[ 4*x + 3 + y*4*W/SCALE]= ((x+y)*255/(2*W/SCALE)) & 0xDF;
		}
	}
	FILE* srcFD = fopen("src.raw", "wb");
	fwrite(src_data, sizeof(uint8_t), W*H*4/(SCALE*SCALE), srcFD);
	fclose(srcFD);
	printf("Performing scaling...\r\n");fflush(stdout);
//	sws_scale(sws, src, src_stride, 0, H, dest, dest_stride);
	int i  = 0;
	int rate = 1;
	printf("Progressively scaling %d rows at a time\r\n", rate);
	for (i = 0; i < H/rate; i++)
	{
		sws_scale(sws, src_data+i*rate*W*4/SCALE, src_stride, i*rate, rate, dest_data, dest_stride);
	}

	printf("Exporting result\r\n");fflush(stdout);
	FILE* dstFD = fopen("dst.raw", "wb");
	fwrite(dest_data, sizeof(uint8_t), W*H*4, dstFD);
	fclose(dstFD);

	sws_freeContext(sws);

	sws= sws_getContext(W, H, PIX_FMT_RGB32, W/SCALE, H/SCALE, PIX_FMT_RGB32, 0x1002, NULL);
	sws_scale(sws, dest_data, dest_stride, 0, H/SCALE, src_data, src_stride);
	dstFD = fopen("dst2.raw", "wb");
	fwrite(src_data, sizeof(uint8_t), W*H*4/(SCALE*SCALE), dstFD);
	fclose(dstFD);
	free(dest_data);
	free(src_data);
    return 123;
}

int main(int argc, char** argv)
{
	if (argc != 5)
	{
		printf("Usage: program SourceJPEG DestJPEG DestWidth DestHeight\r\n");
		return -1;
	}
	RawImage_t* myImage;
	char* lastDot = strrchr(argv[1], '.');
	if (lastDot && !strcasecmp(lastDot, ".png"))
	{
		FILE* fp = fopen(argv[1], "rb");
		myImage = LoadPNG(fp, atoi(argv[3]), atoi(argv[4]));
		fclose(fp);
	}
	else if (lastDot && !strcasecmp(lastDot, ".gif"))
	{
		int fp = open(argv[1], O_RDONLY | O_BINARY);
		myImage = LoadGIF(fp, atoi(argv[3]), atoi(argv[4]));
		close(fp);
	}
	else if (lastDot && (!strcasecmp(lastDot, ".tif") || !strcasecmp(lastDot, ".tiff")))
	{
		int fp = open(argv[1], O_RDONLY | O_BINARY);
		myImage = LoadTIFF(fp, argv[1], atoi(argv[3]), atoi(argv[4]));
		close(fp);
	}
	else
	{
		FILE* fp = fopen(argv[1], "rb");
		myImage = LoadJPEG(fp, atoi(argv[3]), atoi(argv[4]), 32);
		fclose(fp);
	}
	printf("Finished loading image\r\n");fflush(stdout);
	if (!myImage)
	{
		printf("FAILED loading image\r\n");
		return -1;
	}

	FILE* fp = fopen(argv[2], "wb");
	if (strstr(argv[2], ".png"))
	{
		if (SavePNG(myImage, fp))
			printf("FAILED saving PNG image\r\n");
	}
	else if (SaveJPEG(myImage, fp))
	{
		printf("FAILED saving JPEG image\r\n");
	}
	fclose(fp);

	free(myImage->pPlane);
	free(myImage);
	return 0;
}
