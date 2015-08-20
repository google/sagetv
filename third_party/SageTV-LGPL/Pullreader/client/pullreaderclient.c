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
#include <sys/types.h>
#include <string.h>
#include <unistd.h>
#include "pullreader.h"
#include "pullreaderprotocol.h"

int pr_in, pr_out;
int input[16];
int output[16];

static int startPullreader()
{
    pid_t pid;
    int i;
    int in[2], out[2];
    if(pipe(in)<0)
    {
        return 0;
    }

    if(pipe(out)<0)
    {
        close(in[0]);
        close(in[1]);
        return 0;
    }

    switch (pid = vfork())
    {
        case -1:
            fprintf(stderr, "couldn't fork to start pullreader\n");
            return -1;
            break;
        case 0: // child
            dup2(in[0], 0);
            dup2(out[1], 1);
            close(in[1]);
            close(out[0]);
            for (i = 3; i < 1024; i++) close(i);
            execl("/client/pullreader", "pullreader", NULL);
            fprintf(stderr, "error executing pullreader\n");
            _exit(1);
            break;
        default: // parent
            pr_in=out[0];
            pr_out=in[1];
            return 0;
    }
}

struct PullReader *OpenPullReader(const char *filename, int formatflag)
{
    if(startPullreader()!=0)
        return NULL;
    output[0]=PR_OpenPullReader;
    if(write(pr_out, &output[0], 4*1)!=4*1)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return NULL;
    }
    if(write(pr_out, filename, strlen(filename)+1)!=strlen(filename)+1)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return NULL;
    }
    if(read(pr_in, &input[0], 4)!=4)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return NULL;
    }
    return (struct PullReader *) input[0];
}

int getVideoCodecSettings(struct PullReader *t1, int *width, int *height, int *misc)
{
    output[0]=PR_getVideoCodecSettings;
    output[1]=(int) t1;
    if(write(pr_out, &output[0], 4*2)!=4*2)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return -1;
    }
    if(read(pr_in, &input[0], 4*4)!=4*4)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return -1;
    }
    *width=input[1];
    *height=input[2];
    *misc=input[3];
    return input[0];
}

int getAudioCodecSettings(struct PullReader *t1, int *channels, int *bps, int *samplerate,
    int *bitrate, int *blockalign, int *misc)
{
    output[0]=PR_getAudioCodecSettings;
    output[1]=(int) t1;
    if(write(pr_out, &output[0], 4*2)!=4*2)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return -1;
    }
    if(read(pr_in, &input[0], 4*7)!=4*7)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return -1;
    }
    *channels=input[1];
    *bps=input[2];
    *samplerate=input[3];
    *bitrate=input[4];
    *blockalign=input[5];
    *misc=input[6];
    return input[0];
}

void CloseReader(struct PullReader *t1)
{
    int status=0;
    output[0]=PR_CloseReader;
    output[1]=(int) t1;
    if(write(pr_out, &output[0], 4*2)!=4*2)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
    }
    wait(&status);
}

int ProcessFrames(struct PullReader *t1, int * type, unsigned char *buffer, int bufferlen, 
    int *haspts, long long *pts, int *duration)
{
    output[0]=PR_ProcessFrames;
    output[1]=(int) t1;
    output[2]=(int) buffer;
    output[3]=(int) bufferlen;

    if(write(pr_out, &output[0], 4*4)!=4*4)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return 0;
    }
    if(read(pr_in, &input[0], 4*6)!=4*6)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return 0;
    }
    *type=input[1];
    *haspts=input[2];
    *pts=*((long long *)&input[3]);
    *duration=input[5];
    return input[0];
}

long long getFirstTime(struct PullReader *t1)
{
    output[0]=PR_getFirstTime;
    output[1]=(int) t1;

    if(write(pr_out, &output[0], 4*2)!=4*2)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return -1LL;
    }
    if(read(pr_in, &input[0], 4*2)!=4*2)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return -1LL;
    }
    return *((long long *)&input[0]);;
}

long long getDuration(struct PullReader *t1)
{
    output[0]=PR_getDuration;
    output[1]=(int) t1;

    if(write(pr_out, &output[0], 4*2)!=4*2)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return -1LL;
    }
    if(read(pr_in, &input[0], 4*2)!=4*2)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return -1LL;
    }
    return *((long long *)&input[0]);;
}


int getEOF(struct PullReader *t1)
{
    output[0]=PR_getEOF;
    output[1]=(int) t1;

    if(write(pr_out, &output[0], 4*2)!=4*2)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return 0;
    }
    if(read(pr_in, &input[0], 4*1)!=4*1)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return 0;
    }
    return input[0];
}

int prSeek(struct PullReader *t1, long long time)
{
    output[0]=PR_prSeek;
    output[1]=(int) t1;
    *((long long *)&output[2])=time;

    if(write(pr_out, &output[0], 4*4)!=4*4)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return 0;
    }
    if(read(pr_in, &input[0], 4*1)!=4*1)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return 0;
    }
    return input[0];
}

int prGetStreamCounts(struct PullReader *t1, int *videostreams, int *audiostreams, int *spustreams)
{
    output[0]=PR_prGetStreamCounts;
    output[1]=(int) t1;
    if(write(pr_out, &output[0], 4*2)!=4*2)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return 0;
    }
    if(read(pr_in, &input[0], 4*4)!=4*4)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return 0;
    }
    *videostreams=input[0];
    *audiostreams=input[1];
    *spustreams=input[2];
    return input[3];
}

int prSetVideoStream(struct PullReader *t1, int stream)
{
    output[0]=PR_prSetVideoStream;
    output[1]=(int) t1;
    output[2]=stream;
    if(write(pr_out, &output[0], 4*3)!=4*3)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return 0;
    }
    if(read(pr_in, &input[0], 4*1)!=4*1)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return 0;
    }
    return input[0];
}

int prSetAudioStream(struct PullReader *t1, int stream)
{
    output[0]=PR_prSetAudioStream;
    output[1]=(int) t1;
    output[2]=stream;
    if(write(pr_out, &output[0], 4*3)!=4*3)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return 0;
    }
    if(read(pr_in, &input[0], 4*1)!=4*1)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return 0;
    }
    return input[0];
}

int prSetSpuStream(struct PullReader *t1, int stream)
{
    output[0]=PR_prSetSpuStream;
    output[1]=(int) t1;
    output[2]=stream;
    if(write(pr_out, &output[0], 4*3)!=4*3)
    {
        fprintf(stderr, "Error sending command to pullreader\n");
        return 0;
    }
    if(read(pr_in, &input[0], 4*1)!=4*1)
    {
        fprintf(stderr, "Error reading command from pullreader\n");
        return 0;
    }
    return input[0];
}

