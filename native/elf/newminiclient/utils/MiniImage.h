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
    int hasAlpha; // Is there a third plane for alpha?
    unsigned int uBytePerLine;
    unsigned char * pPlane;
    unsigned int uWidth;
    unsigned int uHeight;
    unsigned int uFormat;
    unsigned char * pPlane2; // Used for YUY2 UV plane with uFormat=2
    int pPhysPlane;
    int PhysPlaneSize;
    int pPhysPlane2;
    int PhysPlane2Size;
    int surface;
    int hasTransparent;
}EM86Image_t;

typedef struct
{
    int sd;
    unsigned char *buffer;
    int bufferoffset;
    int bufferlevel;
    int buffersize;
    void *output;
    void *output2; // UV plane
    int width;
    int height;
    int datalen;
    int usedlen;
    int destwidth;
    void *outputphys;
    void *outputphys2; // UV plane
    int isfile;
} MiniImageState;

// Loads image from the socket/buffer
// It MUST read all datalen from the stream so we don't desynchronize the socket
// For now we don't scale so width and height must match the image data
int loadMiniImage(int sd, unsigned char *buffer, int bufferoffset, int bufferlevel, int buffersize, 
    void *output, void *output2, int width, int height, int datalen, int destwidth,
    void *outputphys, void *outputphys2, int isfile);

int readData(MiniImageState *state, unsigned char *data, int len);
