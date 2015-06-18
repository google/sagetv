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

#ifndef __PULLREADER_H__
#define __PULLREADER_H__


#ifdef __cplusplus
extern "C" {
#endif

#define FFLAG_DTSHD 1
#define FFLAG_FLAC 2

struct PullReader *OpenPullReader(const char *filename, int formatflag);

// 0 none
// 1 mpeg1
// 2 mpeg2
// 3 divx3
// 4 mpeg4
// 5 mpeg4flash
// 8 vc1
// 9 wmv9
// 10 h264
// -1 unsupported
int getVideoCodecSettings(struct PullReader *t1, int *width, int *height, int *misc);

// 0 none
// 1 mpeg layer 1,2,3
// 2 aac
// 3 ac3
// 4 dts
// 5 wma
// -1 unsupported
int getAudioCodecSettings(struct PullReader *t1, int *channels, int *bps, int *samplerate,
    int *bitrate, int *blockalign, int *misc);

int getSPUCodecSettings(struct PullReader *t1, int *misc);

void CloseReader(struct PullReader *t1);

int ProcessFrames(struct PullReader *t1, int * type, unsigned char *buffer, int bufferlen, 
    int *haspts, long long *pts, int *duration);

long long getFirstTime(struct PullReader *t1);

long long getDuration(struct PullReader *t1);

int getEOF(struct PullReader *t1);

int prSeek(struct PullReader *t1, long long time);

int prGetStreamCounts(struct PullReader *t1, int *videostreams, int *audiostreams, int *spustreams);

int prSetVideoStream(struct PullReader *t1, int stream);
int prSetAudioStream(struct PullReader *t1, int stream);
int prSetSpuStream(struct PullReader *t1, int stream);

#ifdef __cplusplus
}
#endif

#endif

