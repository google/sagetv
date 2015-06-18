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
  unsigned long  biSize; 
  long    biWidth; 
  long    biHeight; 
  short   biPlanes; 
  short   biBitCount; 
  unsigned long  biCompression; 
  unsigned long  biSizeImage; 
  long   biXPelsPerMeter; 
  long   biYPelsPerMeter; 
  unsigned long  biClrUsed; 
  unsigned long  biClrImportant; 
} _BITMAPINFOHEADER;

typedef struct 
{ 
  long left; 
  long top; 
  long right; 
  long bottom; 
} _RECT;

typedef struct   {
    char      majortype[16];  //GUID
    char      subtype[16];    //GUID
    char      bFixedSizeSamples;
    char      bTemporalCompression;
    unsigned long  lSampleSize;
    char      formattype[16];   //GUID
	void*	  *pUnk;
    unsigned long  cbFormat;
	void*	  *pBFoarmt;
} _MEDIA_DATA;

typedef struct  {
    _RECT               rcSource;
    _RECT               rcTarget;
    unsigned long       dwBitRate;
    unsigned long       dwBitErrorRate;
    LONGLONG		    AvgTimePerFrame;
    _BITMAPINFOHEADER   bmiHeader;
	unsigned char		bmiExt[4096];
}  _VIDEOINFOHEADER;

typedef struct {
	_MEDIA_DATA		 Media;
	_VIDEOINFOHEADER Video;
} MEDIA_VIDEO;

//int ReadA_MediaVideoHeader( A_AUDIO *pA_Audio, const unsigned char* pStart, int Size );


#endif
