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
#include <string.h>
#include <stdlib.h>
#include "circbuffer.h"

int createCircBuffer(circBuffer *buf, int len)
{
    buf->data=malloc(len);
    if(buf->data==NULL) return 0;
    buf->size=len;
    buf->start=0;
    buf->datalen=0;
    return buf->size;
}

int resetCircBuffer(circBuffer *buf)
{
    buf->start=0;
    buf->datalen=0;
    return buf->size;
}

int freespaceCircBuffer(circBuffer *buf)
{
    return buf->size-buf->datalen;
}

int usedspaceCircBuffer(circBuffer *buf)
{
    return buf->datalen;
}

int addCircBuffer(circBuffer *buf, unsigned char *data, int len)
{    
    int pos=0;
    int startoffset;
    int partsize;
    
    // Verify enough free space
    if(buf->size - buf->datalen < len) return 0;
    
    // find pointer to begining of free region
    startoffset = buf->start + buf->datalen;
    startoffset -= (startoffset >= buf->size) ? buf->size : 0;
    
    // copy first part from start to size
    partsize = (startoffset+len <= buf->size) ? len : buf->size-startoffset;
    memcpy(&buf->data[startoffset], &data[pos], partsize);
    pos+=partsize;
    buf->datalen+=partsize;
    len-=partsize;
    
    // copy second part if needed
    if(len>0)
    {
        memcpy(&buf->data[0], &data[pos], len);
        buf->datalen+=len;
    }
    return 1;
}

// drop a part of circular buffer from start pointer
int dropCircBuffer(circBuffer *buf, int len)
{
    if(len > buf->datalen) return 0;
    buf->datalen -= len;
    buf->start += len;
    buf->start -= (buf->start >= buf->size) ? buf->size : 0;
    return 1;
}

// From circular buffer to data buffer
int getCircBuffer(circBuffer *buf, unsigned char *data, int len)
{
    int pos=0;
    int partsize;
    int startoffset;
    if(len > buf->datalen) return 0;
    
    // copy first part
    startoffset = buf->start;
    partsize = (startoffset + len <= buf->size) ? len : buf->size - startoffset;
    memcpy(&data[pos], &buf->data[startoffset], partsize);
    
    len -= partsize;
    pos += partsize;
    buf->datalen -= partsize;
    buf->start += partsize;
    buf->start -= (buf->start >= buf->size) ? buf->size : 0;
    
    // copy second part if needed
    if(len>0)
    {
        memcpy(&data[pos], &buf->data[0], len);
        buf->datalen -= len;
        buf->start += len;
    }    
    return 1;
}

// From circular buffer to circular buffer
int getCircBuffer2(circBuffer *inbuf, circBuffer *outbuf, int len)
{
    int partsize;
    int startoffset;
    if(len > inbuf->datalen) return 0;
    
    // copy first part
    startoffset = inbuf->start;
    partsize = (startoffset + len <= inbuf->size) ? len : inbuf->size - startoffset;
    addCircBuffer(outbuf, &inbuf->data[startoffset], partsize);
    
    len -= partsize;
    inbuf->datalen -= partsize;
    inbuf->start += partsize;
    inbuf->start -= (inbuf->start >= inbuf->size) ? inbuf->size : 0;
    
    // copy second part if needed
    if(len>0)
    {
        addCircBuffer(outbuf, &inbuf->data[0], len);
        inbuf->datalen -= len;
        inbuf->start += len;
    
    }    
    return 1;
}
