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
typedef struct {
    int hasAlpha; // Is there alpha?
    unsigned int uBytePerLine;
    unsigned char * pPlane;
	unsigned int uWidth;
	unsigned int uHeight;
	unsigned int ubpp;
} RawImage_t;

RawImage_t* LoadPNG(FILE* fp, int imgwidth, int imgheight);
RawImage_t* LoadGIF(int fp, int imgwidth, int imgheight);
RawImage_t* LoadTIFF(int fp, const char* filename, int imgwidth, int imgheight);
RawImage_t* LoadJPEG(FILE* fp, int imgwidth, int imgheight, int bpp, int rotation);
int SaveJPEG(RawImage_t* image, FILE* fp);
int SavePNG(RawImage_t* image, FILE* fp);
int LoadJPEGDimensions(FILE* fp, int *imgwidth, int *imgheight);
int LoadGIFDimensions(int fp, int *imgwidth, int *imgheight);
int LoadTIFFDimensions(int fp, const char* filename, int *imgwidth, int *imgheight);
int LoadPNGDimensions(FILE* fp, int *imgwidth, int *imgheight);
