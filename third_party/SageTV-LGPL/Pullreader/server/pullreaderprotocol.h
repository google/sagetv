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
// All data is sent in the format of the compiled platform for now

// Commands
#define PR_OpenPullReader 1
// in: null terminated string 
// out: handle

#define PR_getVideoCodecSettings 2
// in: handle
// out : codec, width, height, misc

#define PR_getAudioCodecSettings 3
// in: handle
// out: codec, channels, bps, samplerate, bitrate, blockalign, misc

#define PR_CloseReader 4
// in: handle
// out:

#define PR_ProcessFrames 5
// in: handle, buffer address, bufferlen
// out : type, haspts, (long long) pts, duration, data processed

#define PR_getFirstTime 6
// in: handle
// out: (long long) time

#define PR_getDuration 7
// in: handle
// out: (long long) time

#define PR_getEOF 8
// in: handle
// out: eof state

#define PR_prSeek 9
// in: handle, (long long) time
// out: status

#define PR_prGetStreamCounts 10
// in: handle
// out: videostreams, audiostreams, spustreams, status

#define PR_prSetVideoStream 11
// in: handle, stream 
// out: status

#define PR_prSetAudioStream 12
// in: handle, stream 
// out: status

#define PR_prSetSpuStream 13
// in: handle, stream 
// out: status

