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
#include <sys/mman.h>
#include <sys/unistd.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include "../inputcalls.h"
#include "../thread_util.h"

extern ACL_mutex *MediaCmdMutex;
extern int ConnectionError;
extern int Media_Stop();
extern int Media_deinit();
extern int ExitValue;
unsigned char key[4];

int InputInit()
{
    int irdevice = open("/dev/rawir",O_RDONLY);
    key[0]=0;
    key[1]=0;
    key[2]=0;
    key[3]=0;
    return (int) irdevice;
}

int ReadInput(int handle, unsigned int *event, unsigned int *len, unsigned char **data)
{
    fd_set rfds;
    int irdevice = handle;
    int n=0;
    int count;
    int selret;
    struct timeval timeout;
    FD_ZERO(&rfds);
    FD_SET(irdevice, &rfds);
    if(irdevice+1>n) n=irdevice+1;
    timeout.tv_sec=0;
    timeout.tv_usec=100000;
    selret = select(n, &rfds, NULL, NULL, &timeout);
    if(selret < 0)
    {
        fprintf(stderr, "ReadInput select() error");
        return -1;
    }

    if(FD_ISSET(irdevice, &rfds))
    {
        if(read(irdevice, key+2, 2)==2)
        {
            fprintf(stderr,"Got input %d\n",(key[2]<<8|key[3]));
            if(((key[2]<<8|key[3])&~0x800)==14269 || ((key[2]<<8|key[3])&~0x800)==14333) 
            {
                ExitValue=1; 
                fprintf(stderr, "Shutdown, trying to call deinit\n");
                ACL_LockMutex(MediaCmdMutex);
                Media_Stop();
                Media_deinit();
                ConnectionError=1;
                ACL_UnlockMutex(MediaCmdMutex);
                fprintf(stderr, "Done\n");
                return(-1);
            }
            *event=128;
            *len=4;
            *data=&key[0];
            return 1;
        }
    }
    return 0;
}

// Note: this could be called more than once for one event, don't free null
int ReleaseInput(int handle)
{
    return 1;
}

int CloseInput(int handle)
{
    int irdevice = handle;
    close(irdevice);
    return 0;
}
