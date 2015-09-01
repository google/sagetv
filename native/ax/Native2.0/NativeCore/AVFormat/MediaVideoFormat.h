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

#ifndef MEDIA_VIDEO_FORMAT_ANALYZER_H
#define MEDIA_VIDEO_FORMAT_ANALYZER_H

typedef struct
{
  uint32_t  biSize; 
  int32_t   biWidth; 
  int32_t   biHeight; 
  int16_t   biPlanes; 
  int16_t   biBitCount; 
  uint32_t  biCompression; 
  uint32_t  biSizeImage; 
  int32_t   biXPelsPerMeter; 
  int32_t   biYPelsPerMeter; 
  uint32_t  biClrUsed; 
  uint32_t  biClrImportant; 
} _BITMAPINFOHEADER;

typedef struct 
{ 
  int32_t   left; 
  int32_t   top; 
  int32_t   right; 
  int32_t   bottom; 
} _RECT;

typedef struct   {
  char      majortype[16];  //GUID
  char      subtype[16];    //GUID
  char      bFixedSizeSamples;
  char      bTemporalCompression;
  uint32_t  lSampleSize;
  char      formattype[16];   //GUID
	void*	    *pUnk;
  uint32_t  cbFormat;
	void*	    *pBFoarmt;
} _MEDIA_DATA;

typedef struct  {
  _RECT     rcSource;
  _RECT     rcTarget;
  uint32_t  dwBitRate;
  uint32_t  dwBitErrorRate;
  int64_t		AvgTimePerFrame;
  _BITMAPINFOHEADER   bmiHeader;
	uint8_t   bmiExt[4096];
} _VIDEOINFOHEADER;

typedef struct {
	_MEDIA_DATA		 Media;
	_VIDEOINFOHEADER Video;
} MEDIA_VIDEO;

//int ReadA_MediaVideoHeader( A_AUDIO *pA_Audio, const unsigned char* pStart, int Size );


#endif
