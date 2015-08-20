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
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>

int Media_init(int videoFormat)
{
    return 1;
}

int Media_deinit()
{
}

// Note: this will modify settings by inserting 00s at separator
// Format: (Prop=attr*;)*([(Prop=attr*;)*])*
static void ParsePushURL(unsigned char *settings, unsigned char **tokens, int *ntokens, 
    int *properties, int *nproperties, int *streams, int *nstreams, int maxtokens, int maxstreams)
{
    *nproperties=0;
    *ntokens=0;
    *nstreams=0;
    properties[0]=0; // Token 0 is first property
    tokens[0]=settings;
    while((*settings)!=0 && *ntokens<(maxtokens-1) && *nstreams<(maxstreams-1))
    {
        if(*settings==';')
        {
            *settings=0;
            *ntokens=*ntokens+1;
            tokens[*ntokens]=settings+1;
            *nproperties=*nproperties+1;
            properties[*nproperties]=*ntokens;
        }
        else if(*settings=='=' )
        {
            *settings=0;
            *ntokens=*ntokens+1;
            tokens[*ntokens]=settings+1;
        }
        else if(*settings=='[')
        {
            *settings=0;
            streams[*nstreams]=*nproperties;
            *nstreams=*nstreams+1;
            tokens[*ntokens]=settings+1;
        }
        else if(*settings==']')
        {
            *settings=0;
            tokens[*ntokens]=settings+1;
        }
        settings++;
    }
}

static int containerPropertiesCount(unsigned char **tokens, int *ntokens, 
    int *properties, int *nproperties, int *streams, int **nstreams)
{
    if(*nstreams>0 && *nproperties>streams[0])
    {
        return streams[0];
    }
    else
    {
        return *nproperties;
    }
}

static int streamPropertiesCount(unsigned char **tokens, int *ntokens, 
    int *properties, int *nproperties, int *streams, int **nstreams, int stream)
{
    if(*nstreams>stream)
    {
        if(*nstreams>(stream+1))
        {
            return streams[stream+1]-streams[stream];
        }
        else
        {
            return *nproperties-streams[stream];
        }
    }
    return 0;
}

static int ParseContainerMode(unsigned char *arg)
{
    if(strcasecmp(arg,"MPEG2-TS")==0)
        return 1;
    return 0;
}

static int ParseStreamMode(unsigned char *arg)
{
    if(strcasecmp(arg,"vid")==0)
        return 0;
    if(strcasecmp(arg,"aud")==0)
        return 1;
    return -1;
}

static int ParseStreamCodec(unsigned char *arg)
{
    if(strcasecmp(arg,"MPEG2-Video")==0)
        return 2;
    if(strcasecmp(arg,"MPEG4")==0)
        return 4;
    if(strcasecmp(arg,"WMV9")==0)
        return 9;
    if(strcasecmp(arg,"DIVX3")==0)
        return 3;
    if(strcasecmp(arg,"VC1")==0)
        return 8;
    if(strcasecmp(arg,"H.264")==0)
        return 10;
    if(strcasecmp(arg,"AAC")==0)
        return 2;
    if(strcasecmp(arg,"AC3")==0)
        return 3;
    return -1;
}

static int ParseStreamTag(unsigned char *arg)
{
    return strtol(arg, NULL, 16);
}

static int ParseStreamMain(unsigned char *arg)
{
    if(strcasecmp(arg,"yes")==0)
        return 1;
    return 0;
}

int Media_openurl(char *url)
{
    return 1;
}

int Media_SetMute(int mute)
{
    return 1;
}


int Media_Stop()
{
    return 1;
}


int Media_Pause()
{
    return 1;
}

int Media_Play()
{
    return 1;
}

int Media_Flush()
{
    return 1;
}

int Media_GetMediaTime()
{
    int ptime=0;
    return ptime;
}


static int readInt(int pos, unsigned char *cmddata)
{
    return (cmddata[pos+0]<<24)|(cmddata[pos+1]<<16)|(cmddata[pos+2]<<8)|(cmddata[pos+3]);
}

int Media_PushBuffer(int size, int flags, char *buffer)
{
    return 0;
}

int Media_GetVolume()
{
    return 65535;
}

int Media_SetVolume(int volume)
{
    return 65535;
}

int Media_SetVideoRect(int srcx, int srcy, int srcwidth, int srcheight,
    int dstx, int dsty, int dstwidth, int dstheight)
{
    return 0;
}

int Media_DVD_Cell(int size, char *data)
{
    return 0;
}

int Media_DVD_CLUT(int size, unsigned char *clut)
{
    return 0;
}

int Media_DVD_SPUCTRL(int size, unsigned char *data)
{
    return 0;
}

int Media_DVD_SetSTC(unsigned int time) // units are 45000
{
    return 0;
}


int Media_DVD_SetStream(int stream, int data)
{
    return 0;
}

int Media_DVD_ForceFormat(int format)
{
    return 0;
}

int Media_FrameStep(int amount)
{
    return 1;
}

void Media_Seek(unsigned int seekHi, unsigned int seekLo)
{
}


// Sets advanced aspect ratio string
// NAME |
// source=x,y,w,h,xpm,ypm,xm,ym,wm,hm |
// where pm is one of FrontEdgeToBorder,RearEdgeToBorder,FrontEdgeToCenter,RearEdgeToCenter
// and modes are Fixed or Relative
// output=x,y,w,h,xpm,ypm,xm,ym,wm,hm |
// nonlinearmode=width,level |
// blackstrip=h,v |
// cutstrip=h,v|
// scalingmode=PanScan,LetterBox,ARIB|
// deint=Discard_Bob or Weave or ConstantBlend or MotionAdaptative|
int Media_SetAdvancedAspect(char *format)
{
    return 0;
}
