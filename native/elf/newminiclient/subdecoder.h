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
#include "thread_util.h"

typedef struct
{
    unsigned char *subpicbuffer; // decoded data
    unsigned char *subpicturebuffer; // compressed data
    int subpicturelen; // compressed data len
    unsigned int subpicenabled;
    unsigned int color[4];
    unsigned int alpha[4];
    unsigned int rect[4]; // startx, endx, starty, endy
    unsigned int clut[16];
    unsigned int highlight[4]; // startx, endx, starty, endy
    unsigned int highlightcolor[4];
    unsigned int highlightalpha[4];
    ACL_Thread *sputhread;
    ACL_mutex *sputhreadmutex;
    long long spupts;
    long long nextspupts;
    int spustate; // 0: No spu  1: valid spu 
    int spuevent; // 0: no event  1: new spu block 2: new highlight  3: stop spu  4: stop spu thread 5: decoding
    unsigned char *spueventdata;
    unsigned int SPDSZ;
    unsigned int SP_DCSQTA;
    unsigned int PREV_DCSQTA;
    int offsetfield1;
    int offsetfield2;
} SPHandler;

int UpdateSubpictureCLUT(SPHandler *H, unsigned int *clut);
int UpdateSubpictureHightlight(SPHandler *H, unsigned int *hightlightrect, unsigned int colors);
int ResetSubpicture(SPHandler *H);

SPHandler * SubpictureInit();
void SubpictureDeinit(SPHandler *H);
unsigned char * GetSubpictureScreen();
unsigned int * GetSubpictureCLUT();
int GetSubpictureEnabled();
