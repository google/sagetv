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
#include "subdecoder.h"
#include "circbuffer.h"

// Information on format can be found on website
// http://dvd.sourceforge.net/dvdinfo/spu.html

#define DEBUGSUBPICTURE

#define SUBPICBUFLEN 65536

#define CMD_END     0xFF
#define FSTA_DSP    0x00
#define STA_DSP     0x01
#define STP_DSP     0x02
#define SET_COLOR   0x03
#define SET_CONTR   0x04
#define SET_DAREA   0x05
#define SET_DSPXA   0x06
#define CHG_COLCON  0x07


static SPHandler *SP = NULL;

// offset is in 4 bits unit
static unsigned int get4Bits(unsigned char *data, int offset)
{
    return (data[offset/2] >> (4-((offset&1)<<2)))&0xF;
}

static int DecodeRLE(SPHandler *H, unsigned char *screen, int x, int y, int x2, int y2,
                      unsigned char *buffer, int offset, int len)
{
    int i,j;
    unsigned int runlen;
    unsigned int data;
    int startoffset;
    i=x;
    j=y*720;
    y2*=720;
    offset*=2; // use half byte offsets
    
    while(j<y2)
    {        
        startoffset=offset;
/*        fprintf(stderr, "offset %X data %X %X %X %X\n",offset, 
            get4Bits(buffer, offset), get4Bits(buffer, offset+1),
            get4Bits(buffer, offset+2), get4Bits(buffer, offset+3));*/
        data=get4Bits(buffer, offset);
        offset+=1;
        if((data&0xC)==0)
        {
            data=(data<<4)|get4Bits(buffer,offset);
            offset+=1;
            if((data&0x30)==0)
            {
                data=(data<<4)|get4Bits(buffer,offset);
                offset+=1;
                if((data&0xC0)==0)
                {
                    data=(data<<4)|get4Bits(buffer,offset);
                    offset+=1;                    
                }
            }
        }
        runlen=(data&0xFFFC)>>2;
        if((data&0xFFFC)==0)
        {
            runlen=x2-i+1;
        }
        
        if(runlen+i>x2+1)
        {
            fprintf(stderr, "at %d runlen %d too long setting to %d (x2=%d)\n", i, runlen, x2-i+1,x2);
            runlen=x2-i+1;
        }
        //fprintf(stderr, "runlen %d color %d\n", runlen, data&0x3);
        while(runlen)
        {
            if(j>=H->highlight[2] && j<=H->highlight[3] && i>=H->highlight[0] && i<=H->highlight[1])
            {
                screen[j+i]=(H->highlightalpha[data&0x3]|H->highlightcolor[data&0x3]);
            }
            else
            {
                screen[j+i]=(H->alpha[data&0x3]|H->color[data&0x3]);
            }
            i++;
            runlen--;
        }
        
        if(i>x2)
        {
            //fprintf(stderr, "new line\n");
            j+=720*2; // Using field in frame
            i=x;
            // Align to bytes?
            if(offset&1)
            {
                //fprintf(stderr, "end offset not aligned to byte, increasing offset\n");
                offset++;
            }
        }
    }

    // TEST: Display the palette...
/*    for(i=0;i<256;i++)
    {
        for(j=0;j<16;j++)
        {
            H->subpicbuffer[128+i+720*(16+j)]=0xF0|(i>>4);
        }
    }
*/    
    
}

static int DecodeControlSequence(SPHandler *H, unsigned char *buf, int len, int offset)
{
    unsigned int SP_DCSQ_STM;
    unsigned int command = 0;
    SP_DCSQ_STM = (buf[offset+0]<<8)|buf[offset+1];
    offset+=4;
    
    while(command!=CMD_END)
    {
        command=buf[offset];
        offset+=1;
        switch(command)
        {
            case FSTA_DSP:
                #ifdef DEBUGSUBPICTURE
                fprintf(stderr, "FSTA_DSP\n");
                #endif
                H->subpicenabled=1;
                //enable=2;
                break;
            case STA_DSP:
                #ifdef DEBUGSUBPICTURE
                fprintf(stderr, "STA_DSP\n");
                #endif
                H->subpicenabled=1;
                //enable=1;
                break;
            case STP_DSP:
                #ifdef DEBUGSUBPICTURE
                fprintf(stderr, "STP_DSP\n");
                #endif
                H->subpicenabled=0;
                break;
            case SET_COLOR:
                H->color[0]=buf[offset+1]&0xF;
                H->color[1]=(buf[offset+1]>>4)&0xF;
                H->color[2]=buf[offset+0]&0xF;
                H->color[3]=(buf[offset+0]>>4)&0xF;
                offset+=2;
                #ifdef DEBUGSUBPICTURE
                fprintf(stderr, "SET_COLOR %d %d %d %d\n", 
                    H->color[0], H->color[1], H->color[2], H->color[3]);
                #endif
                break;
            case SET_CONTR:
                H->alpha[0]=(buf[offset+1]&0xF)<<4;
                H->alpha[1]=((buf[offset+1]>>4)&0xF)<<4;
                H->alpha[2]=(buf[offset+0]&0xF)<<4;
                H->alpha[3]=((buf[offset+0]>>4)&0xF)<<4;
                offset+=2;
                #ifdef DEBUGSUBPICTURE
                fprintf(stderr, "SET_CONTR %d %d %d %d\n", 
                    H->alpha[0], H->alpha[1], H->alpha[2], H->alpha[3]);
                #endif
                break;
            case SET_DAREA:
                H->rect[0]=(buf[offset+0]<<4)|(buf[offset+1]>>4);
                H->rect[1]=((buf[offset+1]<<8)&0xF00)|(buf[offset+2]);
                H->rect[2]=(buf[offset+3]<<4)|(buf[offset+4]>>4);;
                H->rect[3]=((buf[offset+4]<<8)&0xF00)|(buf[offset+5]);
                offset+=6;
                #ifdef DEBUGSUBPICTURE
                fprintf(stderr, "SET_DAREA %d %d %d %d\n", 
                    H->rect[0], H->rect[1], H->rect[2], H->rect[3]);
                #endif
                break;
            case SET_DSPXA:
                H->offsetfield1=(buf[offset+0]<<8)|buf[offset+1];
                H->offsetfield2=(buf[offset+2]<<8)|buf[offset+3];
                offset+=4;
                #ifdef DEBUGSUBPICTURE
                fprintf(stderr, "SET_DSPXA\n");
                #endif
                break;
            case CHG_COLCON:
                offset+=((buf[offset+0]<<8)|buf[offset+1]);
                
                #ifdef DEBUGSUBPICTURE
                fprintf(stderr, "SET_COLCON\n");
                #endif
                break;
            case CMD_END:
                #ifdef DEBUGSUBPICTURE
                fprintf(stderr, "CMD_END\n");
                #endif
                break;
            default:
                fprintf(stderr, "Unknown subpicture command %X\n", command);
                return -1;
        }
    }
}

static int PreDecodeSubpicture(SPHandler *H, unsigned char *buf, int len)
{
    H->subpicenabled=0;
    if(buf!=NULL)
    {
        H->SPDSZ = (buf[0]<<8)|buf[1];
        H->SP_DCSQTA = (buf[2]<<8)|buf[3];
        H->PREV_DCSQTA=0xFFFFFFFF;
        
        #ifdef DEBUGSUBPICTURE
        fprintf(stderr, "SubPicture: Should wait %d\n",(buf[H->SP_DCSQTA+0]<<8)|buf[H->SP_DCSQTA+1]);
        #endif
        H->nextspupts=Media_GetMediaTime()*90L+1024*((buf[H->SP_DCSQTA+0]<<8)|buf[H->SP_DCSQTA+1]);
        return 1;
    }
    
    return 0;
}

static int DecodeSubpicture(SPHandler *H, unsigned char *buf, int len)
{
    if(H->SP_DCSQTA<len)
    {
        if((H->nextspupts/90L) > Media_GetMediaTime())
        {
            return 0; // We don't want this block yet
        }
        
        #ifdef DEBUGSUBPICTURE
        fprintf(stderr, "SubPicture: Decoding at %d (requested %d)\n", Media_GetMediaTime(), (int) (H->nextspupts/90L));
        #endif
        DecodeControlSequence(H, buf, len, H->SP_DCSQTA);
        H->PREV_DCSQTA=H->SP_DCSQTA;
        H->SP_DCSQTA=(buf[H->SP_DCSQTA+2]<<8)|buf[H->SP_DCSQTA+3];
        
        if(H->SP_DCSQTA != H->PREV_DCSQTA)
        {            
            #ifdef DEBUGSUBPICTURE
            fprintf(stderr, "SubPicture: Should wait %d\n",(buf[H->SP_DCSQTA+0]<<8)|buf[H->SP_DCSQTA+1]);
            #endif
            H->nextspupts=Media_GetMediaTime()*90L+1024*((buf[H->SP_DCSQTA+0]<<8)|buf[H->SP_DCSQTA+1]);
        }
        else
        {
            H->spuevent=0;
        }
        return 1;
    }
    return 0;
}

static int PostDecodeSubpicture(SPHandler *H, unsigned char *buf, int len)
{
    // TODO: handle regions better to optimize...
    memset(H->subpicbuffer, 0, 720*576);
    if(H->subpicenabled)
    {
        if(H->offsetfield1!=-1)
        {
            DecodeRLE(H, H->subpicbuffer, H->rect[0], H->rect[2], 
                H->rect[1], H->rect[3], buf,
                H->offsetfield1, len);
        }
        if(H->offsetfield2!=-1)
        {
            DecodeRLE(H, H->subpicbuffer, H->rect[0], H->rect[2]+1, 
                H->rect[1], H->rect[3], buf,                      
                H->offsetfield2, len);
        }
    }
    GFX_flipBuffer();    
}

int UpdateSubpictureCLUT(SPHandler *H, unsigned int *clut)
{
    int i;
    for(i=0;i<16;i++) H->clut[i]=clut[i];
}

int UpdateSubpictureHightlight(SPHandler *H, unsigned int *highlightrect, unsigned int colors)
{    
    H->highlight[0]=highlightrect[0];
    H->highlight[1]=highlightrect[1];
    H->highlight[2]=highlightrect[2]*720;
    H->highlight[3]=highlightrect[3]*720;
    H->highlightcolor[3]=(colors>>28)&0xF;
    H->highlightcolor[2]=(colors>>24)&0xF;
    H->highlightcolor[1]=(colors>>22)&0xF;
    H->highlightcolor[0]=(colors>>16)&0xF;
    H->highlightalpha[3]=((colors>>12)&0xF)<<4;
    H->highlightalpha[2]=((colors>>8)&0xF)<<4;
    H->highlightalpha[1]=((colors>>4)&0xF)<<4;
    H->highlightalpha[0]=((colors>>0)&0xF)<<4;
    #ifdef DEBUGSUBPICTURE
    fprintf(stderr,"UpdateSubpictureHightlight New highlight %d %d %d %d %08X\n",
        highlightrect[0],highlightrect[1],highlightrect[2],highlightrect[3], colors);
    #endif
    ACL_LockMutex(H->sputhreadmutex);
    H->spuevent=2;
    ACL_UnlockMutex(H->sputhreadmutex);    
}

int ResetSubpicture(SPHandler *H)
{
    #ifdef DEBUGSUBPICTURE
    fprintf(stderr,"Reset subpicture\n");
    #endif
    ACL_LockMutex(H->sputhreadmutex);
    
    if(H->subpicenabled)
    {
        memset(H->subpicbuffer, 0, 720*576);
        GFX_flipBuffer();
    }
    
    H->subpicturelen=0;
    H->spustate=0;
    H->spuevent=0;
    
    H->highlight[0]=0;
    H->highlight[1]=0;
    H->highlight[2]=0;
    H->highlight[3]=0;
    H->subpicenabled=0;
    H->offsetfield1=-1;
    H->offsetfield2=-1;
    ACL_UnlockMutex(H->sputhreadmutex);    
}                


extern int Media_GetMediaTime();

int SubpictureAddBlock(SPHandler *H, circBuffer *buf, int len)
{
    unsigned int ptshi, ptslo;
    
    if(len>4+9)
    {
        if(getByteCircBuffer(buf,7)&0x80) /* has pts */
        {
            ptshi=(getByteCircBuffer(buf,4+5)&0x08)>>3;
            ptslo=((getByteCircBuffer(buf,4+5)&0x06)>>1)<<30;
            ptslo|=((getByteCircBuffer(buf,4+6)&0xFF)>>0)<<22;
            ptslo|=((getByteCircBuffer(buf,4+7)&0xFE)>>1)<<15;
            ptslo|=((getByteCircBuffer(buf,4+8)&0xFF)>>0)<<7;
            ptslo|=((getByteCircBuffer(buf,4+9)&0xFE)>>1)<<0;
            H->spupts=ptshi;
            H->spupts<<=32;
            H->spupts|=ptslo;
            if((H->spupts/90L) > Media_GetMediaTime())
            {
                //fprintf(stderr, "Time not ready\n");
                return 0; // We don't want this packet yet
            }
            fprintf(stderr, "Subpicture block %02X has PTS: %08X%08X\n",
                getByteCircBuffer(buf,getByteCircBuffer(buf, 8)+9), ptshi, ptslo);
            fprintf(stderr, "pts/90 %d stc/90 %d\n", (unsigned int)  (H->spupts/90L),
                Media_GetMediaTime());
        }
    }

    ACL_LockMutex(H->sputhreadmutex);
    if(H->spuevent!=0)
    {
        ACL_UnlockMutex(H->sputhreadmutex);
        return 0;    
    }
    ACL_UnlockMutex(H->sputhreadmutex);
    
    len-=getByteCircBuffer(buf,8)+9+1;
    if(len<0) return 1;
    
    if(H->subpicturelen+len < SUBPICBUFLEN)
    {
        dropCircBuffer(buf, getByteCircBuffer(buf,8)+9+1);
        getCircBuffer(buf, H->subpicturebuffer+H->subpicturelen, len);
        H->subpicturelen+=len;
    }
    else
    {
        fprintf(stderr, "SubPicture overflow!?!\n");
        H->subpicturelen=0;
        return 0; // WARNING, this might do infinite loop until the thread is properly implemented
    }
    
    // Check if we have a complete block
    if(H->subpicturelen>=4)
    {
        int datalen=((H->subpicturebuffer[0]<<8)|H->subpicturebuffer[1]);
        int controltable=((H->subpicturebuffer[2]<<8)|H->subpicturebuffer[3]);
        if(controltable>=datalen)
        {
            fprintf(stderr, "SubPicture error control > datalen!?!\n");
            H->subpicturelen=0;
            return 1;
        }
        if(H->subpicturelen>=datalen)
        {
            // TODO: add locks...
            ACL_LockMutex(H->sputhreadmutex);
            H->nextspupts=H->spupts;
            H->spueventdata=H->subpicturebuffer;
            H->spuevent=1;
            H->spustate=1;
            ACL_UnlockMutex(H->sputhreadmutex);
            //DecodeSubpicture(H->subpicturebuffer, datalen);
            H->subpicturelen=0;
        }
    }
    return 1;
}

static int SPUHandler(void *data)
{
    SPHandler *H=(SPHandler *)data;
    fprintf(stderr, "Starting SPU Handler\n");
    while(1)
    {
        int needsleep=0;
        ACL_LockMutex(H->sputhreadmutex);
        switch(H->spustate)
        {
            case 0:
                needsleep=1;
                if(H->spuevent==4) return 0;
                H->spuevent=0;
                break;
            case 1:
                switch(H->spuevent)
                {
                    case 0: 
                        needsleep=1;
                        break;
                    case 1:
                        PreDecodeSubpicture(H, H->spueventdata, (H->spueventdata[0]<<8)|(H->spueventdata[1]));
                        H->spuevent=5;
                        break;
                    case 2:
                        PostDecodeSubpicture(H, H->spueventdata, (H->spueventdata[0]<<8)|(H->spueventdata[1]));
                        H->spuevent=0;
                        break;
                    case 3:
                        PreDecodeSubpicture(H, NULL, 0);
                        PostDecodeSubpicture(H, NULL, 0);
                        H->spustate=0;
                        H->spuevent=0;
                        break;
                    case 4:
                        PreDecodeSubpicture(H, NULL, 0);
                        PostDecodeSubpicture(H, NULL, 0);
                        H->spustate=0;
                        H->spuevent=0;
                        return 0;
                        break;
                    case 5:
                        if(DecodeSubpicture(H, H->spueventdata, (H->spueventdata[0]<<8)|(H->spueventdata[1])))
                            PostDecodeSubpicture(H, H->spueventdata, (H->spueventdata[0]<<8)|(H->spueventdata[1]));
                        needsleep=1;
                        break;
                }
                break;
        }
        ACL_UnlockMutex(H->sputhreadmutex);
        if(needsleep) ACL_Delay(10);
    }
}

SPHandler * SubpictureInit()
{
    SPHandler *H = malloc(sizeof(SPHandler));
    if(H==NULL) return 0;
    memset((void *)H, 0, sizeof(SPHandler));
    
    
    H->subpicbuffer = (unsigned char *) malloc(720*576);
    
    if(H->subpicbuffer==0)
    {
        fprintf(stderr, "couldn't allocate subpic buffer\n");
        free(H);
        return 0;
    }
    
    H->subpicturebuffer =  (unsigned char *) malloc(SUBPICBUFLEN);
    if(H->subpicturebuffer==0)
    {
        fprintf(stderr, "couldn't allocate compressed subpic buffer\n");
        free(H->subpicbuffer);
        free(H);
        return 0;
    }
    
    H->sputhreadmutex = ACL_CreateMutex();
    H->sputhread = ACL_CreateThread(SPUHandler, H);
    
    ResetSubpicture(H);
    SP = H;
    return H;
}

void SubpictureDeinit(SPHandler *H)
{
    fprintf(stderr, "Trying to stop spu thread\n");
    if(H->sputhread)
    {
        ACL_LockMutex(H->sputhreadmutex);
        H->spuevent=4;
        ACL_UnlockMutex(H->sputhreadmutex);
        ACL_ThreadJoin(H->sputhread);
        ACL_UnlockMutex(H->sputhreadmutex);
        ACL_RemoveMutex(H->sputhreadmutex);
    }
    fprintf(stderr, "Joined spu thread\n");
    if(H!=NULL) free(H);
    SP=NULL;
}

unsigned char * GetSubpictureScreen()
{
    if(SP==NULL) return NULL;
    return SP->subpicbuffer;
}

unsigned int * GetSubpictureCLUT()
{
    if(SP==NULL) return NULL;
    return SP->clut;
}

int GetSubpictureEnabled()
{
    if(SP!=NULL)
        return SP->subpicenabled;
    return 0;
}
