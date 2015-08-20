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
#include <stdlib.h>
#include <sys/time.h>
#include "pullreader.h"
#include "pullreaderprotocol.h"


// On entry stdin is the input for commands, stdout is the output for results
// stderr should be the same as the parent process stderr (or another way to report messages)
char filename[4096];

int main(int argc, char **argv)
{
    struct PullReader * pr;
    unsigned char *decbuffer;
    int dectype;
    int haspts;
    long long pts;
    int done=0;
    int input[16];
    int output[16];
    long long firsttime;
    long long duration;
    long long seektime;
    int tmpchar;
    int tmppos;
    while(!done)
    {
        int command;
        if(fread(&command, 1, 4, stdin)!=4)
        {
            fprintf(stderr, "Couldn't read command\n");
            done=1;
            break;
        }
        switch(command)
        {
            case PR_OpenPullReader:
                // in: null terminated string 
                // out: handle
                tmpchar=255;
                tmppos=0;
                while(tmpchar!=0)
                {
                    tmpchar = fgetc(stdin);
                    if(tmpchar<0)
                    {
                        fprintf(stderr, "Couldn't read input parameters\n");
                        done=1;
                        break;
                    }
                    if(tmppos<4095)
                    {
                        filename[tmppos]=tmpchar;
                        tmppos+=1;
                    }
                }
                pr=OpenPullReader(filename);
                if(fwrite(&pr, 1, 4, stdout)!=4)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_getVideoCodecSettings:
                // in: handle
                // out : codec, width, height, misc
                if(fread(&input[0], 1, 4, stdin)!=4)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                if((int)pr!=(int)input[0])
                {
                    fprintf(stderr, "Invalid handle detected, aborting\n");
                    done=1;
                    break;
                }
                output[0] = getVideoCodecSettings(pr, &output[1], &output[2], &output[3]);
                if(fwrite(&output[0], 1, 4*4, stdout)!=4*4)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_getAudioCodecSettings:
                // in: handle
                // out: channels, bps, samplerate, bitrate, blockalign, misc
                if(fread(&input[0], 1, 4, stdin)!=4)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                if((int)pr!=(int)input[0])
                {
                    fprintf(stderr, "Invalid handle detected, aborting\n");
                    done=1;
                    break;
                }
                output[0] = getAudioCodecSettings(pr,
                    &output[1], &output[2], &output[3],
                    &output[4], &output[5], &output[6]);
                if(fwrite(&output[0], 1, 4*7, stdout)!=4*7)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_CloseReader:
                // in: handle
                // out:
                if(fread(&input[0], 1, 4, stdin)!=4)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                if((int)pr!=(int)input[0])
                {
                    fprintf(stderr, "Invalid handle detected, aborting\n");
                    done=1;
                    break;
                }
                CloseReader(pr);
                pr=NULL;
                fflush(stdout);
                done=1;
                break;
            case PR_ProcessFrames:
                // in: handle, buffer address, bufferlen
                // out : data processed, type, haspts, (long long) pts
                if(fread(&input[0], 1, 12, stdin)!=12)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                if((int)pr!=(int)input[0])
                {
                    fprintf(stderr, "Invalid handle detected, aborting\n");
                    done=1;
                    break;
                }
                output[0] = ProcessFrames(pr, &output[1], (unsigned char *) input[1], input[2], &output[2], 
                    (long long *) &output[3], &output[5]);
                if(fwrite(&output[0], 1, 4*6, stdout)!=4*6)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_getFirstTime:
                // in: handle
                // out: (long long) time
                if(fread(&input[0], 1, 4, stdin)!=4)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                if((int)pr!=(int)input[0])
                {
                    fprintf(stderr, "Invalid handle detected, aborting\n");
                    done=1;
                    break;
                }
                firsttime = getFirstTime(pr);
                if(fwrite(&firsttime, 1, 8, stdout)!=8)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_getDuration:
                // in: handle
                // out: (long long) time
                if(fread(&input[0], 1, 4, stdin)!=4)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                if((int)pr!=(int)input[0])
                {
                    fprintf(stderr, "Invalid handle detected, aborting\n");
                    done=1;
                    break;
                }
                duration = getDuration(pr);
                if(fwrite(&duration, 1, 8, stdout)!=8)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_getEOF:
                // in: handle
                // out: eof state
                if(fread(&input[0], 1, 4, stdin)!=4)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                if((int)pr!=(int)input[0])
                {
                    fprintf(stderr, "Invalid handle detected, aborting\n");
                    done=1;
                    break;
                }
                output[0] = getEOF(pr);
                if(fwrite(&output[0], 1, 4*1, stdout)!=4*1)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_prSeek:
                // in: handle, (long long) time
                // out: status
                if(fread(&input[0], 1, 4*3, stdin)!=4*3)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                if((int)pr!=(int)input[0])
                {
                    fprintf(stderr, "Invalid handle detected, aborting\n");
                    done=1;
                    break;
                }
                seektime=*( (long long *) &input[1]);
                output[0] = prSeek(pr, seektime);
                if(fwrite(&output[0], 1, 4*1, stdout)!=4*1)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_prGetStreamCounts:
            // in: handle
            // out: videostreams, audiostreams, spustreams, status
                if(fread(&input[0], 1, 4*1, stdin)!=4*1)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                if((int)pr!=(int)input[0])
                {
                    fprintf(stderr, "Invalid handle detected, aborting\n");
                    done=1;
                    break;
                }
                output[3] = prGetStreamCounts(pr, &output[0], &output[1], &output[2]);
                if(fwrite(&output[0], 1, 4*4, stdout)!=4*4)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_prSetVideoStream:
            // in: handle, stream 
            // out: status
                if(fread(&input[0], 1, 4*2, stdin)!=4*2)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                output[0] = prSetVideoStream(pr, input[1]);
                if(fwrite(&output[0], 1, 4*1, stdout)!=4*1)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_prSetAudioStream:
            // in: handle, stream 
            // out: status
                if(fread(&input[0], 1, 4*2, stdin)!=4*2)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                output[0] = prSetAudioStream(pr, input[1]);
                if(fwrite(&output[0], 1, 4*1, stdout)!=4*1)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;
            case PR_prSetSpuStream:
            // in: handle, stream 
            // out: status
                if(fread(&input[0], 1, 4*2, stdin)!=4*2)
                {
                    fprintf(stderr, "Couldn't read input parameters\n");
                    done=1;
                    break;
                }
                output[0] = prSetSpuStream(pr, input[1]);
                if(fwrite(&output[0], 1, 4*1, stdout)!=4*1)
                {
                    fprintf(stderr, "Couldn't write output parameters\n");
                    done=1;
                    break;
                }
                fflush(stdout);
                break;

            default:
                fprintf(stderr, "Unknown command %d\n",command);
                done=1;
                break;
        }
    }
}

// Temp test
int ff_rate_control_init(void *s, int old_bitrate)
{
   exit(1);
}
