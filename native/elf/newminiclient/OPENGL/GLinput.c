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
#include <fcntl.h>
#include "../inputcalls.h"
#include "../thread_util.h"
#include "GLgfx.h"

#ifdef GLX11
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#endif

extern GLRenderContext_t * RC;

extern ACL_mutex *MediaCmdMutex;
extern int ConnectionError;

unsigned char key[4];
extern int ExitValue;

int InputInit()
{
    return 0;
}

int ReadInput(int handle, unsigned int *event, unsigned int *len, unsigned char **data)
{
    fd_set rfds;
    int n=0;
    int* fds = (int *)handle;
    char tempkey[4];
    int count;
    int selret;
    struct timeval timeout;

    if(RC==NULL) // GLgfx is not ready
    {
        FD_ZERO(&rfds);
        timeout.tv_sec=0;
        timeout.tv_usec=100000;
        selret = select(n, &rfds, NULL, NULL, &timeout);
        if(selret < 0)
        {
            perror("ReadInput select() error");
            return -1;
        }
    }
    else
    {
        // We need to obtain the mutex to prevent 2 threads using UI calls at same time
        FD_ZERO(&rfds);
        timeout.tv_sec=0;
        timeout.tv_usec=500000;
        selret = select(n, &rfds, NULL, NULL, &timeout);
        if(selret < 0)
        {
            perror("ReadInput select() error");
            return -1;
        }
        ACL_LockMutex(RC->GFXMutex);
#ifdef GLX11
        int i32NumMessages = XPending( RC->nativeDisplay );
        int i;
        for(i = 0; i < i32NumMessages; i++ )
        {
            XEvent	event;
            XNextEvent( RC->nativeDisplay, &event );
            switch( event.type )
            {
                default:
                    break;
            }
        }
#endif
        ACL_UnlockMutex(RC->GFXMutex);
    }
    return 0;
}

int ReleaseInput(int handle)
{
    return 1;
}

int CloseInput(int handle)
{
    int *fds =  (int *)handle;
    return 0;
}
