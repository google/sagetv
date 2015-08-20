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
#ifdef EM86
#define DirectPush
#endif

int Media_init();
int Media_deinit();
int Media_openurl(char *url);
int Media_GetMediaTime();
int Media_SetMute(int mute);
int Media_Stop();
int Media_Pause();
int Media_Play();
int Media_Flush();
int Media_NextBuffer();
int Media_PushBuffer(int size, int flags, char *buffer);
int Media_GetVideoRect();
int Media_SetVideoRect();
int Media_GetVolume();
int Media_SetVolume(int volume);
int Media_SetVideoRect(int srcx, int srcy, int srcwidth, int srcheight,
    int dstx, int dxty, int dstwidth, int dstheight);
int Media_DVD_Cell(int size, char *data);
int Media_DVD_CLUT(int size, unsigned char *clut);
int Media_DVD_SPUCTRL(int size, char *data);
int Media_DVD_SetSTC(unsigned int time); // units are 45000
int Media_DVD_SetStreams(int stream, int data);
int Media_FrameStep(int amount);
void Media_Seek(unsigned int seekHi, unsigned int seekLo);
int Media_SetAdvancedAspect(char *format);

#ifdef DirectPush
int Media_PushBuffer2(int sd);
#endif
