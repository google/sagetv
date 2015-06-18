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
#include "pullreader.h"

int main(int argc, char **argv)
{
    unsigned char *decbuffer;
    int videobytes=0;
    int audiobytes=0;
    int dectype;
    int haspts;
    int duration;
    long long prevpts=0;
    long long pts;
    struct PullReader * pr;
    decbuffer=(unsigned char *) malloc(32768);
    fprintf(stderr, "Opening %s with pullreader\n",argv[1]);
    pr=OpenPullReader(argv[1],0);
    fprintf(stderr, "Opened pull reader retval %d\n",pr);
    if(pr!=NULL)
    {
        int width, height, miscv;
        int channels, bps, samplerate, bitrate, blockalign, misca;
        int videocodec = getVideoCodecSettings(pr, &width, &height, &miscv);
        int audiocodec = getAudioCodecSettings(pr, &channels, &bps, &samplerate, &bitrate, &blockalign, &misca);
        fprintf(stderr, "codec settings : V(%d): %dX%d %d  A(%d): %d %d %d %d %d %d\n", 
            videocodec, width, height, miscv, audiocodec,
            channels, bps, samplerate, bitrate, blockalign, misca);
    }
    prSetSpuStream(pr, 0);

//    prSeek(pr, 12*60*1000);
//    prSeek(pr, 30*1000);
    while(!getEOF(pr))
    {
        int datalen;
        pts=0;
        datalen = ProcessFrames(pr, &dectype, decbuffer, 32768, &haspts, &pts, &duration);
        fprintf(stderr,"Process frame returned %d bytes, type %d, haspts %d pts %lld duration %d\n",
            datalen, dectype, haspts, pts, duration);
        if(dectype==0) videobytes+=datalen;
        if(dectype==1) audiobytes+=datalen;
        if(dectype==0 && haspts)
        {
            fprintf(stderr, "pts delta %lld\n",pts-prevpts);
            prevpts=pts;
        }
/*        if(dectype==0)
	{
                FILE *dumpfile=fopen("dump.video","a+b");
                fwrite(decbuffer, datalen, 1, dumpfile);
                fclose(dumpfile);
  	}*/

      fprintf(stderr, "videobytes %d\n audiobytes %d\n",videobytes, audiobytes);
    }
    fprintf(stderr, "Done\n");
    fprintf(stderr, "videobytes %d\n audiobytes %d\n",videobytes, audiobytes);
    CloseReader(pr);
}

// Temp test
int ff_rate_control_init(void *s, int old_bitrate)
{
   exit(1);
}

