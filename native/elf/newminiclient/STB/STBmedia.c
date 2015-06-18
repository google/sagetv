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
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <math.h>
#include <sys/mman.h>
#include <sys/unistd.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <errno.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <fcntl.h>
#include <vid/vid_inf.h>
#include <aud/aud_inf.h>
#include "../thread_util.h"
#include "../subdecoder.h"
#include "../circbuffer.h"

//#define DEBUGPTSOFFSET
//#define DEBUGSTB
//#define DEBUGDEMUX
//#define DEBUGTIME
//#define DEBUGPTS
//#define DEBUGWRITE

static int g_palMode = 0;

#define VIDBUFLEN 65536*32
#define AUDBUFLEN 65536*8
#define PESBUFLEN 65536*2
#define SPBUFLEN 65536*2

// TODO: define in header files
typedef struct {
    int sockfd;                        
    struct sockaddr_in server_addr;
} ServerConnection;

typedef struct {
    ServerConnection sc;
    struct sockaddr_in client_addr;
    int client_len;
} multicastReceiver;

extern multicastReceiver * createMulticastReceiver(char *destip, int port);
extern closeMulticastReceiver(multicastReceiver *mr);
extern multicastReceiver *mr;

typedef struct
{
	int m2vfd;
	int audfd;
	int mode; // 0: video+audio 1: video only
	int vidcliplen;
	int audcliplen;
	unsigned char *vidclipbuffer;
	unsigned char *audclipbuffer;
	int vidmaplength;
	void* vidbase;
    
	circBuffer pesbuffer; 
	circBuffer audpesbuffer;
	circBuffer vidpesbuffer;
	circBuffer sppesbuffer;
	long long vidpts;
	long long audpts;
	int fastmode;    
	int audiomode; // 0: mpeg 1:AC3(Standard) 2:AC3(DVD)
	int audiosub; // For DVD playback...
	int forcedcount;
	int validaudiopes;
	int validvideopes;
	int validsppes;
	int picsub;   // For DVD playback...
	int skipmode;
	SPHandler *sphandle; // Subpicture module handle
	int hasvideo;
	int seqend;
	int videoFormat;
	int multicastMode;
	char multicastIP[16];
	int multicastPort;
	long long ptsOffset;
} STBInfo;

unsigned char smalltest[0x06+0x22] = {    
    0x00, 0x00, 0x01, 0xE0, 0x00, 0x22, 
    0x80, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 
    0xd7, 0xff, 0xfb, 0x80, 0x00, 0x00, 0x01, 0xb5, 
    0x81, 0x1f, 0xf3, 0x41, 0x80, 0x00, 0x00, 0x01, 
    0x1e, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 
    0x01, 0xB7 };

unsigned char testpframe[0x129+9+4] = {
    0x00, 0x00, 0x01, 0xE0, 0x01, 0x29+3+4, 0x80, 0x00, 0x00,
    0x00, 0x00, 0x01, 0x00, 0x00, 0xd7, 0xff, 0xfb, 0x80, 0x00, 0x00, 0x01, 0xb5, 0x81, 0x1f, 0xf3,
    0x41, 0x80, 0x00, 0x00, 0x01, 0x01, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x02, 0x12,
    0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x03, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01,
    0x04, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x05, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00,
    0x00, 0x01, 0x06, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x07, 0x12, 0x71, 0xa9, 0x01,
    0xa0, 0x90, 0x37, 0x02, 0xea, 0x40, 0x07, 0xb0, 0x23, 0x38, 0x00, 0x00, 0x01, 0x08, 0x12, 0x70,
    0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x09, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x0a,
    0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x0b, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00,
    0x01, 0x0c, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x0d, 0x12, 0x70, 0x10, 0x14, 0x70,
    0x00, 0x00, 0x01, 0x0e, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x0f, 0x12, 0x70, 0x10,
    0x14, 0x70, 0x00, 0x00, 0x01, 0x10, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x11, 0x12,
    0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x12, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01,
    0x13, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x14, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00,
    0x00, 0x01, 0x15, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x16, 0x12, 0x70, 0x10, 0x14,
    0x70, 0x00, 0x00, 0x01, 0x17, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x18, 0x12, 0x70,
    0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x19, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x1a,
    0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x1b, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00,
    0x01, 0x1c, 0x12, 0x70, 0x10, 0x14, 0x70, 0x00, 0x00, 0x01, 0x1d, 0x12, 0x70, 0x10, 0x14, 0x70,
    0x00, 0x00, 0x01, 0x1e, 0x12, 0x70, 0x10, 0x14, 0x70,
    0x00, 0x00, 0x01, 0xB7 };

static int Media_reinitAudio(int mode);

#define tbl(x) __asm__ __volatile__ ("mftb %0,268\n\t":"=r" (x) )
#define tbu(x) __asm__ __volatile__ ("mftb %0,269\n\t":"=r" (x) )

static unsigned long long get_timebase() {
  unsigned long long full;
  register unsigned int upper1;
  register unsigned int upper2;
  register unsigned int lower;

  do {
    tbu(upper1);
    tbl(lower);
    tbu(upper2);
  } while(upper1!=upper2);
  full = upper1;
  full = full << 32;
  return full|lower;
}

int SendVideoBlock(STBInfo *si, int flags)
{
    CLIPINFO  info;
    int nbuf;
    if(si->vidcliplen>(60*1024) || (flags!=0 && si->vidcliplen>0 
        && usedspaceCircBuffer(&si->vidpesbuffer)<65536) || (si->seqend && si->vidcliplen>0))
    {        
        ioctl(si->m2vfd, MPEG_VID_GET_CLIP_BUFFERS, &nbuf);
        if(nbuf>0)
        {
            if(ioctl(si->m2vfd, MPEG_VID_GET_BUF_WAIT, &info) < 0)
            {
                fprintf(stderr, "video get empty info error\n");
                return 0;
            }
            memcpy(si->vidbase + info.ulBufAdrOff, si->vidclipbuffer, si->vidcliplen);
            info.uClipAdrOff = info.ulBufAdrOff;
            info.uClipLen = si->vidcliplen;
            if(ioctl(si->m2vfd, MPEG_VID_CLIP_WRITE, &info) < 0)
            {
                fprintf(stderr, "video clip write error\n");
                return 0;
            }
            //fprintf(stderr, "video clip write %X seqend %d\n",si->vidcliplen, si->seqend);
            si->vidcliplen=0;
/*            if(write(si->m2vfd, si->vidclipbuffer, si->vidcliplen)!=0)
            {
                si->vidcliplen=0;
                return 1;
            }*/
        }
    }
    return 0;
}

int SendAudioBlock(STBInfo *si, int flags)
{
    int nbuf;
    if(si->audcliplen>(6*1024) || (flags!=0 && si->audcliplen>0 
        && usedspaceCircBuffer(&si->audpesbuffer)<8192))
    {
        ioctl(si->audfd, MPEG_AUD_GET_CLIP_BUFFERS, &nbuf);
        if(nbuf>0)
        {
            //fprintf(stderr, "SendAudioBlock len %d\n",si->audcliplen);
            //memset(si->audclipbuffer+si->audcliplen, 0, 8192-si->audcliplen);
            if(write(si->audfd, si->audclipbuffer, si->audcliplen)!=0)
            {
                si->audcliplen=0;
                return 1;
            }
        }
    }
    return 0;
}                

int STBWriteBlock2(STBInfo *si, circBuffer *buf, int len)
{
    int sent=0;
#ifdef DEBUGSTB
    fprintf(stderr, "STBwriteBlock len %d type %d\n",len, getByteCircBuffer(buf,3));
#endif
    SYNCINFO time,time2;
    
    int ptshi=0;
    int ptslo=0;
    
    if(getByteCircBuffer(buf,7)&0x80) /* has pts */
    {
        ptshi=(getByteCircBuffer(buf, 4+5)&0x08)>>3;
        ptslo=((getByteCircBuffer(buf, 4+5)&0x06)>>1)<<30;
        ptslo|=((getByteCircBuffer(buf, 4+6)&0xFF)>>0)<<22;
        ptslo|=((getByteCircBuffer(buf, 4+7)&0xFE)>>1)<<15;
        ptslo|=((getByteCircBuffer(buf, 4+8)&0xFF)>>0)<<7;
        ptslo|=((getByteCircBuffer(buf, 4+9)&0xFE)>>1)<<0;
//        fprintf(stderr, "Block %02X has PTS: %d %d\n", buf[3], ptshi, ptslo);
        if(getByteCircBuffer(buf, 3)==0xE0)
        {
            si->vidpts=ptshi;
            si->vidpts<<=32;
            si->vidpts|=ptslo;
        }
        if(getByteCircBuffer(buf, 3)==(si->audiosub>>8))
        {
            if(si->audpts==-1)
            {
                STC_T newstc = {0,0};
                si->audpts=ptshi;
                si->audpts<<=32;
                si->audpts|=ptslo;
                newstc.bit32_1=(si->audpts>>1);
                ioctl(si->m2vfd, MPEG_VID_SET_SYNC_STC, &newstc);
                //newstc.bit32_1=(si->audpts>>1)-22500;
                ioctl(si->audfd, MPEG_AUD_SET_SYNC_STC, &newstc);
            }
            else
            {
                si->audpts=ptshi;
                si->audpts<<=32;
                si->audpts|=ptslo;
            }
        }
    }
        
    if(getByteCircBuffer(buf, 3)==0xE0)
    {
        int origlen;
        if(si->vidpts==-1)
        {
#ifdef DEBUGPTS
            fprintf(stderr, "Discarding video block with no pts\n");
#endif
            dropCircBuffer(buf, len);
            return 1; // Discard the block
        }
        if(getByteCircBuffer(buf, len-4)==0x00 &&
           getByteCircBuffer(buf, len-3)==0x00 &&
           getByteCircBuffer(buf, len-2)==0x01 &&
           getByteCircBuffer(buf, len-1)==0xB7)
        {
            fprintf(stderr, "Detected sequence end in video stream\n");
            si->seqend+=1;
        }
        
        if((65536 - si->vidcliplen) >= len)
        {
            getCircBuffer(buf, si->vidclipbuffer + si->vidcliplen, len);
            si->vidcliplen+=len;
            if(si->seqend==1 && usedspaceCircBuffer(buf)==0)
            {
                fprintf(stderr, "Adding forced next frame\n");
                origlen=(si->vidclipbuffer[si->vidcliplen-len+4]<<8) |
                (si->vidclipbuffer[si->vidcliplen-len+5]);
                origlen-=4;
                si->vidclipbuffer[si->vidcliplen-len+4]=origlen>>8;
                si->vidclipbuffer[si->vidcliplen-len+5]=origlen;
                si->vidcliplen-=4;
                addCircBuffer(buf, testpframe, 0x129+9+4);
            }
            sent=1;
        }
        else
        {
            SendVideoBlock(si, 0);
            if((sent==0) && (65536 - si->vidcliplen) >= len)
            {
                getCircBuffer(buf, si->vidclipbuffer + si->vidcliplen, len);
                si->vidcliplen+=len;
                if(si->seqend==1 && usedspaceCircBuffer(buf)==0)
                {
                    fprintf(stderr, "Adding forced next frame\n");
                    origlen=(si->vidclipbuffer[si->vidcliplen-len+4]<<8) |
                    (si->vidclipbuffer[si->vidcliplen-len+5]);
                    origlen-=4;
                    si->vidclipbuffer[si->vidcliplen-len+4]=origlen>>8;
                    si->vidclipbuffer[si->vidcliplen-len+5]=origlen;
                    si->vidcliplen-=4;
                    addCircBuffer(buf, testpframe, 0x129+9+4);
                }
                sent=1;
            }
        }
        return sent;
    }
    else if(getByteCircBuffer(buf, 3)==(si->audiosub>>8))
    {
        if(si->audpts==-1)
        {
#ifdef DEBUGPTS
            fprintf(stderr, "Discarding audio block until we get audio with pts\n");
#endif
            dropCircBuffer(buf, len);
            return 1; // Discard the block
        }
        
        if(si->mode!=0) 
        {
            // discard audio packets in fast forward mode...
            dropCircBuffer(buf, len);
            return 1;
        }
        
        if((8192 - si->audcliplen) >= len)
        {
            if(si->audiomode==2)
            {
                int packetlen;
                int headerlen;
                // Discard if not right subtype
                if(getByteCircBuffer(buf, getByteCircBuffer(buf, 8)+9)!=(si->audiosub&0xFF))
                {
                    dropCircBuffer(buf, len);
                    return 1;
                }
                
                // copy 4 bytes header
                getCircBuffer(buf, si->audclipbuffer + si->audcliplen, 4);
                si->audcliplen+=4;
                
                // copy packet len
                packetlen=getByteCircBuffer(buf, 0)<<8;
                packetlen|=getByteCircBuffer(buf, 1);
                dropCircBuffer(buf, 2);
                packetlen-=4;
                
                // write new len in buffer
                ((unsigned char *)(si->audclipbuffer + si->audcliplen))[0] =
                    (packetlen>>8)&0xFF;
                ((unsigned char *)(si->audclipbuffer + si->audcliplen))[1] =
                    (packetlen)&0xFF;
                si->audcliplen+=2;
                
                // copy header bytes
                headerlen= 3 + getByteCircBuffer(buf, 2);
                getCircBuffer(buf, si->audclipbuffer + si->audcliplen, 
                    headerlen);
                
                si->audcliplen+=headerlen;
                
                // Discard the 4 bytes dvd extra information
                dropCircBuffer(buf, 4);
                
                // copy data bytes
                getCircBuffer(buf, si->audclipbuffer + si->audcliplen, packetlen-headerlen);
                
                si->audcliplen+=packetlen-headerlen;
            }
            else
            {
                getCircBuffer(buf, si->audclipbuffer + si->audcliplen, len);
                si->audcliplen+=len;
            }
            sent=1;
        }
        else
        {
            SendAudioBlock(si, 0);
            if((sent==0) && (8192 - si->audcliplen) >= len)
            {
                if(si->audiomode==2)
                {
                    int packetlen;
                    int headerlen;
                    // Discard if not right subtype
                    if(getByteCircBuffer(buf, getByteCircBuffer(buf, 8)+9)!=(si->audiosub&0xFF))
                    {
                        dropCircBuffer(buf, len);
                        return 1;
                    }
                    
                    // copy 4 bytes header
                    getCircBuffer(buf, si->audclipbuffer + si->audcliplen, 4);
                    si->audcliplen+=4;
                    
                    // copy packet len
                    packetlen=getByteCircBuffer(buf, 0)<<8;
                    packetlen|=getByteCircBuffer(buf, 1);
                    dropCircBuffer(buf, 2);
                    packetlen-=4;
                    
                    // write new len in buffer
                    ((unsigned char *)(si->audclipbuffer + si->audcliplen))[0] =
                        (packetlen>>8)&0xFF;
                    ((unsigned char *)(si->audclipbuffer + si->audcliplen))[1] =
                        (packetlen)&0xFF;
                    si->audcliplen+=2;
                    
                    // copy header bytes
                    headerlen= 3 + getByteCircBuffer(buf, 2);
                    getCircBuffer(buf, si->audclipbuffer + si->audcliplen, 
                        headerlen);
                    
                    si->audcliplen+=headerlen;
                    
                    // Discard the 4 bytes dvd extra information
                    dropCircBuffer(buf, 4);
                    
                    // copy data bytes
                    getCircBuffer(buf, si->audclipbuffer + si->audcliplen, packetlen-headerlen);
                    
                    si->audcliplen+=packetlen-headerlen;
                }
                else
                {
                    getCircBuffer(buf, si->audclipbuffer + si->audcliplen, len);
                    si->audcliplen+=len;
                }
                sent=1;
            }
        }
        return sent;
    }
    return 1; // Discard unidentified blocks    
}

int STBWriteBlockVid(STBInfo *si, circBuffer *buf, int len)
{
    unsigned char b;
    unsigned int pos=0;
    int copied=0;
#ifdef DEBUGSTB
    fprintf(stderr, "STBwriteBlockVid len %d\n",len);
#endif
    si->validvideopes=0;
    
    
    if(len!=0 && usedspaceCircBuffer(&si->vidpesbuffer)+len < VIDBUFLEN)
    {    
        getCircBuffer2(buf, &si->vidpesbuffer, len);
        copied=1;
    }
    
    int cur=0xFFFFFFFF;
    while(pos<usedspaceCircBuffer(&si->vidpesbuffer))
    {        
        b=getByteCircBuffer(&si->vidpesbuffer, pos);
        pos+=1;
        cur<<=8;
        cur|=b;
        if((cur&0xFFFFFF00)==0x00000100)
        {
            /* video */
            if(b==0xE0)
            {
                if(pos-4>0)
                    dropCircBuffer(&si->vidpesbuffer, pos-4);

                /* verify we have complete packet */
                if(usedspaceCircBuffer(&si->vidpesbuffer)>=6 &&
                    (6+(getByteCircBuffer(&si->vidpesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->vidpesbuffer, 5)) <= 
                        usedspaceCircBuffer(&si->vidpesbuffer))
                {
                    si->validvideopes=1;
                    if(STBWriteBlock2(si, &si->vidpesbuffer,
                        (6+(getByteCircBuffer(&si->vidpesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->vidpesbuffer, 5)))>0)
                    {
                        pos=0;
                    }
                    else
                    {
                        break;
                    }
                }
                else
                {
                    break;
                }
            }
        }
    }    
    
    if(len!=0 && copied == 0 && usedspaceCircBuffer(&si->vidpesbuffer)+len < VIDBUFLEN)
    {
        getCircBuffer2(buf, &si->vidpesbuffer, len);
        copied=1;
    }
    
#ifdef DEBUGSTB
    fprintf(stderr, "STBwriteBlockVid exit copied=%d\n",copied);
#endif
    return copied;
}

int STBWriteBlockAud(STBInfo *si, circBuffer *buf, int len)
{
    unsigned char b;
    unsigned int pos=0;
    int copied=0;
#ifdef DEBUGSTB
    fprintf(stderr, "STBwriteBlockAud\n",len);
#endif
    si->validaudiopes=0;
    
    if(len!=0 && usedspaceCircBuffer(&si->audpesbuffer)+len < AUDBUFLEN)
    {    
        getCircBuffer2(buf, &si->audpesbuffer, len);
        copied=1;
    }
    
    int cur=0xFFFFFFFF;
    while(pos<usedspaceCircBuffer(&si->audpesbuffer))
    {        
        b=getByteCircBuffer(&si->audpesbuffer, pos);
        pos+=1;
        cur<<=8;
        cur|=b;
        if((cur&0xFFFFFF00)==0x00000100)
        {
            /* audio */
            if(b==(si->audiosub>>8))
            {
                if(pos-4>0)
                    dropCircBuffer(&si->audpesbuffer, pos-4);

                /* verify we have complete packet */
                if(usedspaceCircBuffer(&si->audpesbuffer)>=6 &&
                    (6+(getByteCircBuffer(&si->audpesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->audpesbuffer, 5)) <= 
                        usedspaceCircBuffer(&si->audpesbuffer))
                {
                    si->validaudiopes=1;
                    if(STBWriteBlock2(si, &si->audpesbuffer,
                        (6+(getByteCircBuffer(&si->audpesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->audpesbuffer, 5)))>0)
                    {
                        pos=0;
                    }
                    else
                    {
                        break;
                    }
                }
                else
                {
                    break;
                }
            }
            else // bad audio stream packet...
            {
                if(pos-4>0)
                    dropCircBuffer(&si->audpesbuffer, pos-4);

                /* verify we have complete packet */
                if(usedspaceCircBuffer(&si->audpesbuffer)>=6 &&
                    (6+(getByteCircBuffer(&si->audpesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->audpesbuffer, 5)) <= 
                        usedspaceCircBuffer(&si->audpesbuffer))
                {
                    dropCircBuffer(&si->audpesbuffer, 
                        (6+(getByteCircBuffer(&si->audpesbuffer, 4)<<8)+
                            getByteCircBuffer(&si->audpesbuffer, 5)));
                    pos=0;
                }
            }
        }
    }    
    
    if(len!=0 && copied == 0 && usedspaceCircBuffer(&si->audpesbuffer)+len < AUDBUFLEN)
    {
        getCircBuffer2(buf, &si->audpesbuffer, len);
        copied=1;
    }
    
#ifdef DEBUGSTB
    fprintf(stderr, "STBwriteBlockAud exit copied=%d\n",copied);
#endif
    return copied;
}


int STBWriteBlockSubPicture(STBInfo *si, circBuffer *buf, int len)
{
    unsigned char b;
    unsigned int pos=0;
    int copied=0;
#ifdef DEBUGSTB
    fprintf(stderr, "STBWriteBlockSubPicture\n");
#endif
    si->validsppes=0;
    
    if(len!=0 && usedspaceCircBuffer(&si->sppesbuffer)+len < SPBUFLEN)
    {    
        getCircBuffer2(buf, &si->sppesbuffer, len);
        copied=1;
    }
    
    int cur=0xFFFFFFFF;
    while(pos<usedspaceCircBuffer(&si->sppesbuffer))
    {        
        b=getByteCircBuffer(&si->sppesbuffer, pos);
        pos+=1;
        cur<<=8;
        cur|=b;
        if((cur&0xFFFFFF00)==0x00000100)
        {
            /* subpicture */
            if(b==0xBD)
            {
                if(pos-4>0)
                    dropCircBuffer(&si->sppesbuffer, pos-4);

                /* verify we have complete packet */
                if(usedspaceCircBuffer(&si->sppesbuffer)>=6 &&
                    (6+(getByteCircBuffer(&si->sppesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->sppesbuffer, 5)) <= 
                        usedspaceCircBuffer(&si->sppesbuffer))
                {
                    si->validsppes=1;
                    if(SubpictureAddBlock(si->sphandle, &si->sppesbuffer,
                        (6+(getByteCircBuffer(&si->sppesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->sppesbuffer, 5)))>0)
                    {
                        pos=0;
                    }
                    else
                    {
                        break;
                    }
                }
                else
                {
                    break;
                }
            }
        }
    }    
    
    if(len!=0 && copied == 0 && usedspaceCircBuffer(&si->sppesbuffer)+len < SPBUFLEN)
    {
        getCircBuffer2(buf, &si->sppesbuffer, len);
        copied=1;
    }

#ifdef DEBUGSTB
    fprintf(stderr, "STBWriteBlockSubPicture exit\n",len);
#endif
    return copied;

}

int STBWriteBlock(STBInfo *si, circBuffer *buf, int len)
{
    if(getByteCircBuffer(buf,3)==0xE0)
    {
        si->hasvideo=1;
        if(STBWriteBlockVid(si, buf, len))
        {
            return 1;
        }
        else 
        {
            return 0;
        }
    }
    else if(getByteCircBuffer(buf, 3)==(si->audiosub>>8) || getByteCircBuffer(buf, 3)==0xBD)
    {
        // Test subchannel if dvd audio
        if(getByteCircBuffer(buf, 3)==0xBD && 
            getByteCircBuffer(buf, getByteCircBuffer(buf, 8)+9) == si->picsub)
        {
            if(STBWriteBlockSubPicture(si, buf, len))
            {
                return 1;
            }
            else // We need to wait until the subpicture buffer is free
            {
                return 0; 
            }
        }
        if(si->audiomode==2)
        {
            // Discard if not right subtype
            if(getByteCircBuffer(buf, getByteCircBuffer(buf, 8)+9) != (si->audiosub&0xFF))
            {
                dropCircBuffer(buf, len);
                return 1;
            }
        }
        if(STBWriteBlockAud(si, buf, len))
        {
            return 1;
        }
        else
        {
            return 0;
        }
    
    }
    return 1; // Discard other blocks
}

int STBdemux(STBInfo *si, unsigned char *critArr, int jsize, int flags)
{
#ifdef DEBUGSTB    
    fprintf(stderr, "Demuxing\n");
#endif
    addCircBuffer(&si->pesbuffer, critArr, jsize);
    
    unsigned char b;
    unsigned int didwork=0;
    unsigned int pos=0;
    int cur=0xFFFFFFFF;
    while(pos<usedspaceCircBuffer(&si->pesbuffer))
    {        
        b=getByteCircBuffer(&si->pesbuffer, pos);
        pos+=1;
        cur<<=8;
        cur|=b;
        if((cur&0xFFFFFF00)==0x00000100)
        {
            /* video or audio */
            // TODO: fix that for other streams...
            if((b==0xE0)||(b==(si->audiosub>>8))||(b==0xBD))
            {
                didwork=1;
                // drop bytes up to pos-4
                if(pos-4>0)
                    dropCircBuffer(&si->pesbuffer, pos-4);

                /* verify we have complete packet */
                if(usedspaceCircBuffer(&si->pesbuffer)>=6 &&
                    (6+(getByteCircBuffer(&si->pesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->pesbuffer, 5)) <= 
                        usedspaceCircBuffer(&si->pesbuffer))
                {
                    didwork=1;
                    // Try to write the packet in the pes buffers
                    if(STBWriteBlock(si,&si->pesbuffer,
                        6+(getByteCircBuffer(&si->pesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->pesbuffer, 5))
                        > 0)
                    {
                        // STBWriteBlock took the data out
                        pos=0;
                    }
                    else
                    {
                        //fprintf(stderr, "Failed on block type %02X\n",b);
                        // we must wait for more data
                        break;
                    }
                }
                else
                {
                    // we must wait for more data
                    break;
                }
            }
            else if((b==0xBA))
            {
                didwork=1;
                // drop bytes up to pos-4
                if(pos-4>0)
                    dropCircBuffer(&si->pesbuffer, pos-4);
                /* verify we have complete packet */
                if(usedspaceCircBuffer(&si->pesbuffer)>=14)
                {
                    dropCircBuffer(&si->pesbuffer, 14);
                    pos=0;
                }
                else
                {
                    // we must wait for more data
                    break;
                }
            }
            else if((b==0xBB)||(b==0xBC)|(b==0xBE)|(b==0xBF))
            {
                didwork=1;
                // drop bytes up to pos-4
                if(pos-4>0)
                    dropCircBuffer(&si->pesbuffer, pos-4);
                
                /* verify we have complete packet */
                if(usedspaceCircBuffer(&si->pesbuffer)>=6 &&
                    (6+(getByteCircBuffer(&si->pesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->pesbuffer, 5)) <= 
                        usedspaceCircBuffer(&si->pesbuffer))
                {
                    dropCircBuffer(&si->pesbuffer, 6+(getByteCircBuffer(&si->pesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->pesbuffer, 5));
                    pos=0;
                }
                else
                {
                    // we must wait for more data
                    break;
                }
            }
            else if(((b&0xF0)==0xE0)||((b&0xF0)==0xC0)||((b&0xF0)==0xD0)) // Not our selected channel...
            {
                didwork=1;
                // drop bytes up to pos-4
                if(pos-4>0)
                    dropCircBuffer(&si->pesbuffer, pos-4);
                
                /* verify we have complete packet */
                if(usedspaceCircBuffer(&si->pesbuffer)>=6 &&
                    (6+(getByteCircBuffer(&si->pesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->pesbuffer, 5)) <= 
                        usedspaceCircBuffer(&si->pesbuffer))
                {
                    dropCircBuffer(&si->pesbuffer, 6+(getByteCircBuffer(&si->pesbuffer, 4)<<8)+
                        getByteCircBuffer(&si->pesbuffer, 5));
                    pos=0;
                }
                else
                {
                    // we must wait for more data
                    break;
                }
            }
        }
    }
    
    STBWriteBlockVid(si, NULL, 0);
    STBWriteBlockAud(si, NULL, 0);
    STBWriteBlockSubPicture(si, NULL, 0);
    
    if(SendVideoBlock(si, flags&0x180))
        STBWriteBlockVid(si, NULL, 0);
    if(SendAudioBlock(si, flags&0x180))
        STBWriteBlockAud(si, NULL, 0);
    
    // TODO: add flush logic    
    
    return 0;
}

int STBFastForward(STBInfo *si, int mode, int skipmode)
{
#ifdef DEBUGSTB
    fprintf(stderr, "Setting fast forward %d\n",mode);
#endif
    if(mode==0)
    {
        ioctl(si->m2vfd, MPEG_VID_SYNC_ON, VID_SYNC_AUD);
        //ioctl(si->m2vfd, MPEG_VID_SYNC_OFF, 0);
        ioctl(si->m2vfd, MPEG_VID_PLAY, 0);
        ioctl(si->audfd, MPEG_AUD_CLIP_FLUSH, 0);
        ioctl(si->audfd, MPEG_AUD_RESET_CLIPBUF, 0);
        ioctl(si->audfd, MPEG_AUD_SELECT_SOURCE, 1);
        ioctl(si->audfd, MPEG_AUD_SET_STREAM_TYPE, AUD_STREAM_TYPE_PES);
        ioctl(si->audfd, MPEG_AUD_PLAY, 0);
        si->audcliplen=0;
        si->vidpts=-1;
        si->audpts=-1;
        si->mode=0;
        si->fastmode=0;
    }
    else
    {
        ioctl(si->m2vfd, MPEG_VID_SYNC_OFF, 0);
        ioctl(si->m2vfd, MPEG_VID_FASTFORWARD, skipmode ? 0 : 1);
        ioctl(si->audfd, MPEG_AUD_CLIP_FLUSH, 0);
        ioctl(si->audfd, MPEG_AUD_RESET_CLIPBUF, 0);
        ioctl(si->audfd, MPEG_AUD_SELECT_SOURCE, 1);
        ioctl(si->audfd, MPEG_AUD_SET_STREAM_TYPE, AUD_STREAM_TYPE_PES);
        ioctl(si->audfd, MPEG_AUD_STOP, 0);
        si->audcliplen=0;
        si->mode=1;
        si->fastmode=1;
    }
}

STBInfo* si;

int Media_init(int videoFormat)
{
	STC_T zerostc = {0,0};
	int blockingmode;
    
#ifdef DEBUGSTB    
    fprintf(stderr, "STBinitDriver0\n");
#endif
	si = (STBInfo*)malloc(sizeof(STBInfo));
	memset(si, 0, sizeof(STBInfo));
    
	// TODO: This should be broken down in smaller parts (audio/video/subpicture,...)
    
	if( (si->m2vfd = open("/dev/vid",O_RDWR)) < 0)
	{
		return 0;
	}
	ioctl(si->m2vfd, MPEG_VID_SET_DISPSIZE, videoFormat);
	close(si->m2vfd);
	si->m2vfd=-1;
	if( (si->m2vfd = open("/dev/vid",O_RDWR)) < 0)
	{
		return 0;
	}
	ioctl(si->m2vfd, MPEG_VID_SET_DISPSIZE, videoFormat);
	si->videoFormat = videoFormat;

	ioctl(si->m2vfd, MPEG_VID_GET_BUF_SIZE, &si->vidmaplength);
	si->vidbase = mmap(NULL, si->vidmaplength, PROT_WRITE, MAP_SHARED, si->m2vfd, 0);
    if(si->vidbase == NULL || si->vidbase == (void *)0xffffffff)
    {
        fprintf(stderr, "video mmap failed\n");
        close(si->m2vfd);
        return 0;
    }
    
/*    blockingmode = fcntl(si->m2vfd, F_GETFL, NULL);
    if(blockingmode < 0)
    {
        perror("fcntl F_GETFL");
        close(si->m2vfd);
        return 0;
    }
    
    blockingmode |= O_NONBLOCK;
    if(fcntl(si->m2vfd, F_SETFL, blockingmode)<0)
    {
        close(si->m2vfd);
        return 0;
    }*/

    // Uncomment next line to do tests with PAL videos...
	//ioctl(si->m2vfd, MPEG_VID_SET_DISPFMT, VID_DISPFMT_PAL); // Test JFT
    

	ioctl(si->m2vfd, MPEG_VID_RESET_CLIPBUF, 0);
    
	ioctl(si->m2vfd, MPEG_VID_SELECT_SOURCE, 1); // memory source
	ioctl(si->m2vfd, MPEG_VID_PLAY, 0);
	ioctl(si->m2vfd, MPEG_VID_SET_SFM, VID_SFM_NORMAL);

    if(si->videoFormat==0) // Only letterbox if it's a 4:3 display
    {
        ioctl(si->m2vfd, MPEG_VID_SET_DISPMODE, VID_DISPMODE_LETTERBOX);
    }
    else
    {
        ioctl(si->m2vfd, MPEG_VID_SET_DISPMODE, VID_DISPMODE_NORM);
    }

	if(createCircBuffer(&si->audpesbuffer, AUDBUFLEN)==0)
	{
		close(si->m2vfd);
		free(si);
		return 0;
	}
    
	if(createCircBuffer(&si->vidpesbuffer, VIDBUFLEN)==0)
	{
		free(si->audpesbuffer.data);
		close(si->m2vfd);
		free(si);
		return 0;
	}

	if(createCircBuffer(&si->sppesbuffer, SPBUFLEN)==0)
	{
		free(si->audpesbuffer.data);
		free(si->vidpesbuffer.data);
		close(si->m2vfd);
		free(si);
		return 0;
	}
    
	if(createCircBuffer(&si->pesbuffer, PESBUFLEN)==0)
	{
		free(si->sppesbuffer.data);
		free(si->audpesbuffer.data);
		free(si->vidpesbuffer.data);
		close(si->m2vfd);
		free(si);
		return 0;
	}
    
	if((si->audfd = open("/dev/aud_mpeg",O_RDWR)) <0)
	{
		fprintf(stderr, "Unable to open audio device\n");
		return 0;;
	}
/*    blockingmode = fcntl(si->audfd, F_GETFL, NULL);
    if(blockingmode < 0)
    {
        perror("fcntl F_GETFL");
        close(si->audfd);
        return 0;
    }
    
    blockingmode |= O_NONBLOCK;
    if(fcntl(si->audfd, F_SETFL, blockingmode)<0)
    {
        close(si->audfd);
        return 0;
    }*/
	ioctl(si->audfd, MPEG_AUD_RESET_CLIPBUF, 0);
	ioctl(si->audfd, MPEG_AUD_SELECT_SOURCE, 1);
	ioctl(si->audfd, MPEG_AUD_SET_STREAM_TYPE, AUD_STREAM_TYPE_PES);
	ioctl(si->audfd, MPEG_AUD_PLAY, 0);
	ioctl(si->audfd, MPEG_AUD_SET_MUTE, 0);
	ioctl(si->audfd, MPEG_AUD_SYNC_ON, 1); // 1
	ioctl(si->m2vfd, MPEG_VID_SYNC_ON, VID_SYNC_AUD);
//    ioctl(si->m2vfd, MPEG_VID_SYNC_OFF, 0);
	ioctl(si->m2vfd, MPEG_VID_SET_SYNC_STC, &zerostc);
	ioctl(si->audfd, MPEG_AUD_SET_SYNC_STC, &zerostc);
    
	si->vidclipbuffer=malloc(65536);
	si->vidcliplen=0;
	si->audclipbuffer=malloc(65536);
	si->audcliplen=0;
    
    
	si->vidpts=-1;
	si->audpts=-1;
	si->audiomode=0;
	si->audiosub=0xC000;
	si->picsub=0x20;
	si->sphandle = SubpictureInit();
	si->hasvideo=0;
	if(si->sphandle==0)
	{
		fprintf(stderr, "Couldn't create subpicture handler\n");
		return 0;
	}
	si->multicastMode=0;
	return 1;
}

// Called to switch audio codec
static int Media_reinitAudio(int mode)
{
	STC_T zerostc = {0,0};
	int blockingmode;

	fprintf(stderr, "reinit audio to mode %d\n",mode);
	ioctl(si->audfd, MPEG_AUD_END_OF_STREAM, 1); // memory source
	ioctl(si->audfd, MPEG_AUD_CLIP_FLUSH, 0);
	ioctl(si->audfd, MPEG_AUD_RESET_CLIPBUF, 0);
	ioctl(si->audfd, MPEG_AUD_SELECT_SOURCE, 1);
	ioctl(si->audfd, MPEG_AUD_SET_STREAM_TYPE, AUD_STREAM_TYPE_PES);
	ioctl(si->audfd, MPEG_AUD_PLAY, 0);
	ioctl(si->audfd, MPEG_AUD_SET_MUTE, 0);
	ioctl(si->audfd, MPEG_AUD_SYNC_ON, 1); // 1
	ioctl(si->audfd, MPEG_AUD_SET_SYNC_STC, &zerostc);
	resetCircBuffer(&si->audpesbuffer);

	ioctl(si->audfd, MPEG_AUD_STOP, 1);
	close(si->audfd);

	if(mode==0)
	{
		if((si->audfd = open("/dev/aud_mpeg",O_RDWR)) <0)
		{
			fprintf(stderr, "Unable to open audio device\n");
			return 0;;
		}
	}
	else
	{
		if((si->audfd = open("/dev/adec_ac3",O_RDWR)) <0)
		{
			fprintf(stderr, "Unable to open audio device\n");
			return 0;;
		}
	}
/*    blockingmode = fcntl(si->audfd, F_GETFL, NULL);
    if(blockingmode < 0)
    {
        perror("fcntl F_GETFL");
        close(si->audfd);
        return 0;
    }
    
    blockingmode |= O_NONBLOCK;
    if(fcntl(si->audfd, F_SETFL, blockingmode)<0)
    {
        close(si->audfd);
        return 0;
    }*/

	ioctl(si->audfd, MPEG_AUD_RESET_CLIPBUF, 0);
	ioctl(si->audfd, MPEG_AUD_SELECT_SOURCE, 1);
	ioctl(si->audfd, MPEG_AUD_SET_STREAM_TYPE, AUD_STREAM_TYPE_PES);
	ioctl(si->audfd, MPEG_AUD_PLAY, 0);
	ioctl(si->audfd, MPEG_AUD_SET_MUTE, 0);
	ioctl(si->audfd, MPEG_AUD_SYNC_ON, 1); // 1
	ioctl(si->audfd, MPEG_AUD_SET_SYNC_STC, &zerostc);
        
	si->audcliplen=0;
	si->audiomode=mode;
}

int Media_openurl(char *url)
{
#ifdef DEBUGSTB    
    fprintf(stderr, "openURL0 %s\n",url);
#endif
	if (si==NULL) return 0;
	if(si->multicastMode)
	{
		closeMulticastReceiver(mr);
		si->multicastMode=0;
		mr=NULL;
	}
    if(strstr(url, "multicast:")!=NULL)
    {
        char *ipptr;
        //char *portptr;
        #ifdef DEBUGSTB    
        fprintf(stderr, "openURL0 is multicast\n");
        #endif
        ipptr=url+10;
        strncpy(si->multicastIP, ipptr, 15);
        ipptr[15]=0;
        si->multicastPort=31050;
        fprintf(stderr, "enabling multicast from %s:%d\n",
            si->multicastIP, si->multicastPort);
        si->multicastMode=1;
        mr=createMulticastReceiver(si->multicastIP, si->multicastPort);
/*        if((portptr=strstr(ipptr,":"))!=NULL)
        {
            portptr[0]=0;
            portptr+=1;
            strncpy(si->multicastIP, ipptr, 15);
            ipptr[15]=0;
            si->multicastPort=atoi(portptr);
            fprintf(stderr, "enabling multicast from %s:%d\n",
                si->multicastIP, si->multicastPort);
            si->multicastMode=1;
            mr=createMulticastReceiver(si->multicastIP, si->multicastPort);
        }*/
    }
	return 1;
}

int Media_SetMute(int mute)
{
#ifdef DEBUGSTB    
    fprintf(stderr, "setMute0\n");
    fflush(stdout);
#endif
	if (si==NULL) return 0;
	ioctl(si->audfd, MPEG_AUD_SET_MUTE, mute ? 1 : 0);
	return 1;
}


int Media_Stop()
{
#ifdef DEBUGSTB    
    fprintf(stderr, "stopPush0\n");
    fflush(stdout);
#endif
	if (si==NULL) return 0;
//	ioctl(si->m2vfd, MPEG_VID_STOP, 0);
	return 1;
}


int Media_Pause()
{
#ifdef DEBUGSTB    
    fprintf(stderr, "pausePush0\n");
    fflush(stdout);
#endif
	if (si==NULL) return 0;
	ioctl(si->m2vfd, MPEG_VID_PAUSE, 0);
	ioctl(si->audfd, MPEG_AUD_STOP, 0);
	return 1;
}

int Media_Play()
{
#ifdef DEBUGSTB    
    fprintf(stderr, "playPush0\n");
    fflush(stdout);
#endif
	if (si==NULL) return 0;
	ioctl(si->m2vfd, MPEG_VID_CONTINUE, 0);
	ioctl(si->audfd, MPEG_AUD_PLAY, 0);
	return 1;
}

int Media_Flush()
{
#ifdef DEBUGSTB    
    fprintf(stderr, "flushPush0\n");
    fflush(stdout);
#endif
	STC_T zerostc = {0,0};
	if (si==NULL) return 0;
	ioctl(si->audfd, MPEG_AUD_CLIP_FLUSH, 0);
	ioctl(si->m2vfd, MPEG_VID_CLIP_FLUSH, 0);
	ioctl(si->audfd, MPEG_AUD_RESET_CLIPBUF, 0);
	ioctl(si->m2vfd, MPEG_VID_RESET_CLIPBUF, 0);
	ioctl(si->audfd, MPEG_AUD_SELECT_SOURCE, 1);
	ioctl(si->audfd, MPEG_AUD_SET_STREAM_TYPE, AUD_STREAM_TYPE_PES);
	ioctl(si->audfd, MPEG_AUD_PLAY, 0);
	ioctl(si->audfd, MPEG_AUD_SET_MUTE, 0);
	ioctl(si->audfd, MPEG_AUD_SYNC_ON, 1); // 1
	ioctl(si->m2vfd, MPEG_VID_SYNC_ON, VID_SYNC_AUD);
//    ioctl(si->m2vfd, MPEG_VID_SYNC_OFF, 0);

    ioctl(si->m2vfd, MPEG_VID_SET_SYNC_STC, &zerostc);
	ioctl(si->audfd, MPEG_AUD_SET_SYNC_STC, &zerostc);
	ioctl(si->m2vfd, MPEG_VID_SET_SFM, VID_SFM_NORMAL);
	ResetSubpicture(si->sphandle);
	resetCircBuffer(&si->pesbuffer); 
	resetCircBuffer(&si->audpesbuffer);
	resetCircBuffer(&si->vidpesbuffer);
	resetCircBuffer(&si->sppesbuffer);
	si->audcliplen=0;
	si->vidcliplen=0;
	si->vidpts=-1;
	si->audpts=-1;
	si->seqend=0;
	return 1;
}

int Media_GetMediaTime()
{
#ifdef DEBUGSTB    
    fprintf(stderr, "getMediaTimeMillis0\n");
    fflush(stdout);
#endif
	if (si==NULL) return 0;
//	return si->audpts*1000/90000;
//	return 0;
	SYNCINFO time, timeaud;
	time.stc.bit32_1=0;
	time.pts.bit32_1=0;
	timeaud.stc.bit32_1=0;
	timeaud.pts.bit32_1=0;
//	ioctl(si->audfd, MPEG_AUD_GET_SYNC_INFO, &timeaud);
	if(si->hasvideo)
	{
		ioctl(si->m2vfd, MPEG_VID_GET_SYNC_INFO, &time);
	}
	else
	{
		//fprintf(stderr, "audio mediatime\n");
		ioctl(si->audfd, MPEG_AUD_GET_SYNC_INFO, &time);
	}
//	fprintf(stderr, "VSTC/2: %d ASTC/2: %d  VPTS/2: %d\n",time.stc.bit32_1, timeaud.stc.bit32_1,
//		time.pts.bit32_1);
/*	if((time.pts.bit32_1-time.stc.bit32_1)>45000 && si->fastmode==0 && si->skipmode==0)
	{
	    fprintf(stderr, "Entering skip mode, delay > 1sec\n");
	    ioctl(si->m2vfd, MPEG_VID_FASTFORWARD, 1);
	    si->skipmode=1;
	}
	else if((time.pts.bit32_1-time.stc.bit32_1)<45000 && si->skipmode==1)
	{
	    fprintf(stderr, "Leaving skip mode, delay > 1sec\n");
	    ioctl(si->m2vfd, MPEG_VID_PLAY, 0);
	    si->skipmode=0;
	}*/
//    fprintf(stderr, "mediatime %d\n", (int) (((long long)time.pts.bit32_1)*1000/90000*2));
	return ((long long)time.pts.bit32_1)*1000/90000*2;
}

int Media_deinit()
{
#ifdef DEBUGSTB    
    fprintf(stderr, "closeDriver0\n");
    fflush(stdout);
#endif
	if (si==NULL) return 0;
	if(si->multicastMode)
	{
		closeMulticastReceiver(mr);
		mr=NULL;
	}
	ioctl(si->m2vfd, MPEG_VID_END_OF_STREAM, 1); // memory source
	ioctl(si->audfd, MPEG_AUD_END_OF_STREAM, 1); // memory source
	ioctl(si->audfd, MPEG_AUD_SET_MUTE, 1);

	Media_Flush();
	ioctl(si->audfd, MPEG_AUD_STOP, 1);
	ioctl(si->m2vfd, MPEG_VID_STOP, 1);
	usleep(1*1000*1000); // we must not exit until the audio buffers have been consumed or BOOM

	if(si->sphandle!=NULL)
		SubpictureDeinit(si->sphandle);
    
	if(si->pesbuffer.data)
		free(si->pesbuffer.data);
	if(si->audpesbuffer.data)
		free(si->audpesbuffer.data);
	if(si->vidpesbuffer.data)
		free(si->vidpesbuffer.data);
	if(si->sppesbuffer.data)
		free(si->sppesbuffer.data);
	if(si->vidclipbuffer)
		free(si->vidclipbuffer);
	if(si->audclipbuffer)
		free(si->audclipbuffer);
	if(munmap(si->vidbase, si->vidmaplength) < 0)
		fprintf(stderr, "munmap error\n");
	close(si->m2vfd);
	close(si->audfd);
	free(si);
	si=NULL;
	return 1;
}


int fixPTSBlock(STBInfo *si, circBuffer *buf, int len)
{
#ifdef DEBUGSTB
    fprintf(stderr, "fixPTSBlock len %d type %d\n",len, getByteCircBuffer(buf,3));
#endif
    int ptsdtsflag;
    unsigned long long temppts;
    int ptshi=0;
    int ptslo=0;
    unsigned long long tempdts;
    int dtshi=0;
    int dtslo=0;

    ptsdtsflag=(getByteCircBuffer(buf,7)&0xC0)>>6;

    if(ptsdtsflag&0x2) /* has pts */
    {
        ptshi=(getByteCircBuffer(buf, 4+5)&0x08)>>3;
        ptslo=((getByteCircBuffer(buf, 4+5)&0x06)>>1)<<30;
        ptslo|=((getByteCircBuffer(buf, 4+6)&0xFF)>>0)<<22;
        ptslo|=((getByteCircBuffer(buf, 4+7)&0xFE)>>1)<<15;
        ptslo|=((getByteCircBuffer(buf, 4+8)&0xFF)>>0)<<7;
        ptslo|=((getByteCircBuffer(buf, 4+9)&0xFE)>>1)<<0;
        temppts=ptshi;
        temppts<<=32LL;
        temppts|=ptslo;
#ifdef DEBUGPTSOFFSET
        fprintf(stderr,"%X Old pts %lld new pts %lld\n",getByteCircBuffer(buf, 3), temppts, temppts+si->ptsOffset);
#endif
        temppts+=si->ptsOffset;
        temppts&=0x1FFFFFFFFLL;
        setByteCircBuffer(buf, 4+5, (ptsdtsflag<<4) | ((temppts>>29)&0xE) |0x1);
        setByteCircBuffer(buf, 4+6, ((temppts>>22)&0xFF));
        setByteCircBuffer(buf, 4+7, ((temppts>>14)&0xFF)|0x1);
        setByteCircBuffer(buf, 4+8, ((temppts>>7)&0xFF));
        setByteCircBuffer(buf, 4+9, ((temppts<<1)&0xFF)|0x1);
    }
    if(ptsdtsflag&0x1) /* has dts */
    {
        dtshi=(getByteCircBuffer(buf, 4+10)&0x08)>>3;
        dtslo=((getByteCircBuffer(buf, 4+10)&0x06)>>1)<<30;
        dtslo|=((getByteCircBuffer(buf, 4+11)&0xFF)>>0)<<22;
        dtslo|=((getByteCircBuffer(buf, 4+12)&0xFE)>>1)<<15;
        dtslo|=((getByteCircBuffer(buf, 4+13)&0xFF)>>0)<<7;
        dtslo|=((getByteCircBuffer(buf, 4+14)&0xFE)>>1)<<0;
        tempdts=dtshi;
        tempdts<<=32LL;
        tempdts|=dtslo;
//#ifdef DEBUGPTSOFFSET
//        fprintf(stderr,"Old dts %lld new dts %lld\n",tempdts, tempdts+si->ptsOffset);
//#endif
        tempdts+=si->ptsOffset;
        tempdts&=0x1FFFFFFFFLL;
        setByteCircBuffer(buf, 4+10, (1<<4) | ((tempdts>>29)&0xE) |0x1);
        setByteCircBuffer(buf, 4+11, ((tempdts>>22)&0xFF));
        setByteCircBuffer(buf, 4+12, ((tempdts>>14)&0xFF)|0x1);
        setByteCircBuffer(buf, 4+13, ((tempdts>>7)&0xFF));
        setByteCircBuffer(buf, 4+14, ((tempdts<<1)&0xFF)|0x1);
    }
    dropCircBuffer(buf, len);
}

// This only works for DVDs that send complete packets (2048*x) 
int fixPTS(STBInfo *si, unsigned char *critArr, int jsize)
{
    circBuffer *buf,buf2;
    // Create a fake circbuffer for easier code...

    unsigned char b;
    unsigned int didwork=0;
    unsigned int pos=0;
    int cur=0xFFFFFFFF;
    buf=&buf2;
    buf->data=critArr;
    buf->size=jsize;
    buf->start=0;
    buf->datalen=jsize;
    while(pos<usedspaceCircBuffer(buf))
    {
        b=getByteCircBuffer(buf, pos);
        pos+=1;
        cur<<=8;
        cur|=b;
        if((cur&0xFFFFFF00)==0x00000100)
        {
            /* video or audio */
            // TODO: fix that for other streams...
            if(((b&0xF0)==0xE0)||((b&0xF0)==0xC0)||((b&0xF0)==0xD0)||(b==0xBD))
            {
                didwork=1;
                // drop bytes up to pos-4
                if(pos-4>0)
                    dropCircBuffer(buf, pos-4);

                /* verify we have complete packet */
                if(usedspaceCircBuffer(buf)>=6 &&
                    (6+(getByteCircBuffer(buf, 4)<<8)+
                        getByteCircBuffer(buf, 5)) <= 
                        usedspaceCircBuffer(buf))
                {
                    didwork=1;
                    // Try to write the packet in the pes buffers
                    if(fixPTSBlock(si,buf,
                        6+(getByteCircBuffer(buf, 4)<<8)+
                        getByteCircBuffer(buf, 5))
                        > 0)
                    {
                        // STBWriteBlock took the data out
                        pos=0;
                    }
                    else
                    {
                        //fprintf(stderr, "Failed on block type %02X\n",b);
                        // we must wait for more data
                        break;
                    }
                }
                else
                {
                    // we must wait for more data
                    break;
                }
            }
            else if((b==0xBA))
            {
                didwork=1;
                // drop bytes up to pos-4
                if(pos-4>0)
                    dropCircBuffer(buf, pos-4);
                /* verify we have complete packet */
                if(usedspaceCircBuffer(buf)>=14)
                {
                    dropCircBuffer(buf, 14);
                    pos=0;
                }
                else
                {
                    // we must wait for more data
                    break;
                }
            }
            else if((b==0xBB)||(b==0xBC)|(b==0xBE)|(b==0xBF))
            {
                didwork=1;
                // drop bytes up to pos-4
                if(pos-4>0)
                    dropCircBuffer(buf, pos-4);
                
                /* verify we have complete packet */
                if(usedspaceCircBuffer(buf)>=6 &&
                    (6+(getByteCircBuffer(buf, 4)<<8)+
                        getByteCircBuffer(buf, 5)) <= 
                        usedspaceCircBuffer(buf))
                {
                    dropCircBuffer(buf, 6+(getByteCircBuffer(buf, 4)<<8)+
                        getByteCircBuffer(buf, 5));
                    pos=0;
                }
                else
                {
                    // we must wait for more data
                    break;
                }
            }
        }
    }
}

int Media_PushBuffer(int size, int flags, char *buffer)
{
    static unsigned long long prevtime=0;
#ifdef DEBUGSTB
	fprintf(stderr, "pushBuffer0 %d bytes (flags %d)\n", size, flags);
	fflush(stderr);
#endif
	if (si==NULL) return 0;
    if(flags&0x08) // ignore pts
    {
        if(si->fastmode!=1)
        {
            STBFastForward(si, 1, flags&0x1);
        }
    }
    if(flags&0x10)
    {
        if(si->fastmode!=0)
        {
            STBFastForward(si, 0, flags&0x1);
        }
    }
    if(si->ptsOffset!=0)
    {
        fixPTS(si, buffer, size);
    }
	STBdemux(si, buffer, size, flags);
    
    if(flags&0x80)
    {
        audioStatus as;
        ioctl(si->audfd, MPEG_AUD_GET_STATUS, &as);
        if((as.dsp_status&0x2)==0)
            return -1;
    }
    if(flags&0x100)
    {
        int nbuf;
        // TODO: figure out how to implement it...
        if(usedspaceCircBuffer(&si->vidpesbuffer)<65536)
        {
            ioctl(si->m2vfd, MPEG_VID_GET_CLIP_BUFFERS, &nbuf);
            //fprintf(stderr, "MPEG_VID_GET_CLIP_BUFFERS returned %d\n",nbuf);            
            if(nbuf>=23)
            {
                return -2;
            }
        }
    }
#ifdef DEBUGSTB
	fprintf(stderr, "exit pushBuffer0\n");
	fflush(stdout);
#endif
    
    
    /*fprintf(stderr, "Level : P:%d/%d A:%d/%d V:%d/%d\n", 
        usedspaceCircBuffer(&si->pesbuffer), PESBUFLEN, 
        usedspaceCircBuffer(&si->audpesbuffer), AUDBUFLEN,
        usedspaceCircBuffer(&si->vidpesbuffer), VIDBUFLEN);*/
    /*if((get_timebase()-prevtime)>27000000)
    {
        SYNCINFO time,time2;
        unsigned int fixedpts;
        unsigned long long ptstime;
        prevtime=get_timebase();
        ioctl(si->m2vfd, MPEG_VID_GET_SYNC_INFO, &time);
        ioctl(si->audfd, MPEG_AUD_GET_SYNC_INFO, &time2);
        ptstime=*((unsigned long long *)(&time2.stc));
        fixedpts=time2.pts.bit32_1+(prevtime-ptstime)/600;
        if(ptstime!=0)
        {
            fprintf(stderr, "audio ptstime: %llu orig pts: %u fixed pts: %u video stc: %u video pts: %u\n",ptstime,time2.pts.bit32_1, 
                fixedpts,time.stc.bit32_1,time.pts.bit32_1);
            if(fixedpts>time.pts.bit32_1 && (fixedpts-time.pts.bit32_1)>9000)
            {
                STC_T newstc = {fixedpts,0};
                fprintf(stderr, "1 setting video stc to %u\n",newstc.bit32_1);
                ioctl(si->m2vfd, MPEG_VID_SET_SYNC_STC, &newstc);
            }
            else if(fixedpts<time.pts.bit32_1 && (time.pts.bit32_1-fixedpts)>9000)
            {
                STC_T newstc = {fixedpts,0};
                fprintf(stderr, "2 setting video stc to %u\n",newstc.bit32_1);
                ioctl(si->m2vfd, MPEG_VID_SET_SYNC_STC, &newstc);
            }
        }
    }*/
    return ((freespaceCircBuffer(&si->pesbuffer)) > 8192) ? 
        freespaceCircBuffer(&si->pesbuffer) : 0; // return free space
}

int Media_GetVolume()
{
#ifdef DEBUGSTB
	fprintf(stderr, "GetVolume0\n");
	fflush(stdout);
#endif
	AUDVOL vol;
	ioctl(si->audfd, MPEG_AUD_GET_VOL, &vol);
	//int attenlinear = 65535*pow(2.72, vol.frontleft*2/-10.0*log(2.0));
	int attenlinear = floor((65535.0/9.0) * (pow(10.0, (63-vol.frontleft)*1024/65535.0) - 1));
    if(attenlinear>65535) attenlinear=65535;
	if(attenlinear<0) attenlinear=0;
	fprintf(stderr, "GetVolume0 %d %d\n", vol.frontleft, attenlinear);
	return attenlinear;
}

int Media_SetVolume(int volume)
{
#ifdef DEBUGSTB
	fprintf(stderr, "SetVolume0 %d\n",volume);
	fflush(stdout);
#endif
	int attendb;
	if(volume==0) volume=1;
	attendb = 127-floor(127.0 * log((volume*9.0/65535.0) + 1)/log(10.0));

	fprintf(stderr, "SetVolume0 %d %d %d\n",volume, attendb, attendb/2);
	AUDVOL vol;
	int vol2=attendb/2; // 2db steps...
	if(vol2<0) vol2=0;
	if(vol2>63) vol2=63;
	vol.frontleft=vol2;
	vol.frontright=vol2;
	vol.rearleft=vol2;
	vol.rearright=vol2;
	vol.center=vol2;
	vol.lfe=vol2;
	ioctl(si->audfd, MPEG_AUD_SET_VOL, &vol);
	return (63-vol.frontleft)<<10;
}

int Media_SetVideoRect(int srcx, int srcy, int srcwidth, int srcheight,
    int dstx, int dsty, int dstwidth, int dstheight)
{
    SCALEINFO sci;
    if (si==NULL) return 0;
#ifdef DEBUGSTB
    fprintf(stderr, "SetVideoRect0 %d %d %d %d %d %d %d %d\n", 
        srcx, srcy, srcwidth, srcheight,
        dstx, dsty, dstwidth, dstheight);
    fflush(stdout);
#endif
    
    sci.src.hori_off=srcx;
    sci.src.vert_off=srcy;
    sci.src.hori_size=srcwidth;
    sci.src.vert_size=srcheight;
    sci.des.hori_off=dstx;
    sci.des.vert_off=dsty;
    sci.des.hori_size=dstwidth;
    sci.des.vert_size=dstheight;
    
    if(dstwidth < 450 && dstwidth > 270)
    { // Use half mode
        ioctl(si->m2vfd, MPEG_VID_SET_DISPMODE, VID_DISPMODE_1_2);
    }
    else if(dstwidth < 270 && dstwidth > 90)
    { // Use quarter mode
        ioctl(si->m2vfd, MPEG_VID_SET_DISPMODE, VID_DISPMODE_1_4);
    }
    else // Use full mode
    {
        if(si->videoFormat==0) // Only letterbox if it's a 4:3 display
        {
            ioctl(si->m2vfd, MPEG_VID_SET_DISPMODE, VID_DISPMODE_LETTERBOX);
        }
        else
        {
            ioctl(si->m2vfd, MPEG_VID_SET_DISPMODE, VID_DISPMODE_NORM);
        }
    }
    
    ioctl(si->m2vfd, MPEG_VID_SET_SCALE_POS, &sci);
    
    return 0;
}

static int readInt(int pos, unsigned char *cmddata)
{
    return (cmddata[pos+0]<<24)|(cmddata[pos+1]<<16)|(cmddata[pos+2]<<8)|(cmddata[pos+3]);
}

int Media_DVD_Cell(int size, char *data)
{
    fprintf(stderr, "New cell\n");
    ResetSubpicture(si->sphandle);
    if (si==NULL) return 0;
    if(size>=4)
    {
        int ptsoffset;
        ptsoffset = readInt(0, data);
        si->ptsOffset=ptsoffset*2LL;
        fprintf(stderr, "pts offset %lld\n", si->ptsOffset);
    }
    return 0;
}

int Media_DVD_CLUT(int size, unsigned char *clut)
{
    int i;
    unsigned int *cluti=(unsigned int *)clut;
    fprintf(stderr, "New CLUT\n");
    for(i=0;i<16;i++)
    {
        fprintf(stderr, " color %d : %08X\n",i, cluti[i]);
    }
    UpdateSubpictureCLUT(si->sphandle, cluti);
    return 0;
}

int Media_DVD_SPUCTRL(int size, char *data)
{
    fprintf(stderr, "New SPUCTRL\n");
    UpdateSubpictureHightlight(si->sphandle, (unsigned int *) data, ((unsigned int *)&(data[4*4]))[0]);
    return 0;
}

int Media_DVD_SetSTC(unsigned int time) // units are 45000
{
    STC_T newstc = {time,0};
    fprintf(stderr, "Setting STC to %lld\n",time*2LL);
    ioctl(si->m2vfd, MPEG_VID_SET_SYNC_STC, &newstc);
    ioctl(si->audfd, MPEG_AUD_SET_SYNC_STC, &newstc);
    return 0;
}

int Media_DVD_SetStream(int stream, int data)
{
    if(stream==0)
    {
        fprintf(stderr, "Set audio stream to %X\n",data);
        
        if((si->audiosub&0xFF00)!=(data&0xFF00))
        {
            Media_reinitAudio(((data&0xE000)==(0xC0<<8)) ? 0 : 2);
        }
        si->audiosub=data;
    }
    else if(stream==1)
    {
        fprintf(stderr, "Set spu sub stream to %d\n",data);
        si->picsub=0x20|(data&0x1F);
    }
    return 0;
}

int Media_DVD_ForceFormat(int format)
{
    if (si==NULL) return 0;

    if(format==0 && si->videoFormat==0) // Only letterbox if it's a 4:3 display
    {
        ioctl(si->m2vfd, MPEG_VID_SET_DISPMODE, VID_DISPMODE_LETTERBOX);
    }
    else
    {
        ioctl(si->m2vfd, MPEG_VID_SET_DISPMODE, VID_DISPMODE_NORM);
    }
    return 0;
}

void Media_Seek(unsigned int seekHi, unsigned int seekLo)
{
}

int Media_FrameStep(int amount)
{
    return 1;
}
