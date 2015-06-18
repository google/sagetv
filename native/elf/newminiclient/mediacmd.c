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
#include "mediacmd.h"
#include "mediacalls.h"


static int readInt(int pos, unsigned char *cmddata)
{
    return (cmddata[pos+0]<<24)|(cmddata[pos+1]<<16)|(cmddata[pos+2]<<8)|(cmddata[pos+3]);
}

static short readShort(int pos, unsigned char *cmddata)
{
    return (cmddata[pos+0]<<8)|(cmddata[pos+1]);
}

int ExecuteMediaCommand(int cmd, int len, unsigned char *cmddata, int *hasret, int sd)
{
    *hasret=0; // Nothing to return by default
    // TODO verify sizes...
    //if(cmd!=MEDIACMD_PUSHBUFFER)
    //    fprintf(stderr, "Execute media command %d\n",cmd);
    switch(cmd)
    {
        case MEDIACMD_INIT:
            *hasret=1;
            return Media_init(readInt(0, cmddata));
            break;
        case MEDIACMD_DEINIT:
            *hasret=1;
            return Media_deinit();
            break;
        case MEDIACMD_OPENURL:
            *hasret=1;
            return Media_openurl(&cmddata[4]);
            break;
        case MEDIACMD_GETMEDIATIME:
            *hasret=1;
            return Media_GetMediaTime();
            break;
        case MEDIACMD_SETMUTE:
            *hasret=1;
            return Media_SetMute(readInt(0, cmddata));
            break;
        case MEDIACMD_STOP:
            *hasret=1;
            return Media_Stop();
            break;
        case MEDIACMD_PAUSE:
            *hasret=1;
            return Media_Pause();
            break;
        case MEDIACMD_PLAY:
            *hasret=1;
            return Media_Play();
            break;
        case MEDIACMD_FLUSH:
            *hasret=1;
            return Media_Flush();
            break;
        case MEDIACMD_PUSHBUFFER:
            #ifdef DirectPush
            *hasret=0;
            Media_PushBuffer2(sd);
            #else
            *hasret=1;
            return Media_PushBuffer(readInt(0, cmddata), readInt(4, cmddata), &cmddata[8]);
            #endif
            break;
        case MEDIACMD_GETVOLUME:
            *hasret=1;
            return Media_GetVolume();
            break;
        case MEDIACMD_SETVOLUME:
            *hasret=1;
            return Media_SetVolume(readInt(0, cmddata));
            break;
        case MEDIACMD_SETVIDEORECT:
            *hasret=1;
            return Media_SetVideoRect(readInt(0, cmddata), readInt(4, cmddata), 
                                      readInt(8, cmddata), readInt(12, cmddata),
                                      readInt(16, cmddata), readInt(20, cmddata),
                                      readInt(24, cmddata), readInt(28, cmddata));
            break;
        case MEDIACMD_DVD_NEWCELL:
            *hasret=1;
            return Media_DVD_Cell(readInt(0, cmddata), &cmddata[4]);
            break;
        case MEDIACMD_DVD_CLUT:
            *hasret=1;
            return Media_DVD_CLUT(readInt(0, cmddata), &cmddata[4]);
            break;
        case MEDIACMD_DVD_SPUCTRL:
            *hasret=1;
            return Media_DVD_SPUCTRL(readInt(0, cmddata), &cmddata[4]);
            break;
        case MEDIACMD_DVD_STC:
            *hasret=1;
            return Media_DVD_SetSTC(readInt(0, cmddata));
            break;
        case MEDIACMD_DVD_STREAMS:
            *hasret=1;
            return Media_DVD_SetStream(readInt(0, cmddata), readInt(4, cmddata));
            break;
        case MEDIACMD_DVD_FORMAT:
            *hasret=1;
            return Media_DVD_ForceFormat(readInt(0, cmddata));
            break;
        case MEDIACMD_FRAMESTEP:
            *hasret=1;
            return Media_FrameStep(readInt(0, cmddata));
            break;
        case MEDIACMD_SEEK:
            Media_Seek(readInt(0, cmddata), readInt(4, cmddata));
            break;
        default:
            return -1;
    }
    return 0;
}
