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
#include <unistd.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include "inputcalls.h"
#include "thread_util.h"

int ExitValue;

int uiwidth=1024;
int uiheight=576;

static unsigned char * gbusmem;
struct gbus *pgbus;

int ConnectionError;
int Media_Stop()
{

}

int Media_deinit()
{

}

ACL_mutex *MediaCmdMutex;

void gbus_write_uint32(void *pgbus, unsigned int offset, unsigned int data)
{
    volatile unsigned int * ptr = (volatile unsigned int *) (gbusmem+offset);
    *ptr=data;
}

unsigned int gbus_read_uint32(void *pgbus, unsigned int offset)
{
    volatile unsigned int * ptr = (volatile unsigned int *) (gbusmem+offset);
    return *ptr;
}


int main(int argc, char **argv)
{
    unsigned char *data;
    int keycode;
    unsigned int event,len;
    int retval=1;
    int handle=InputInit();
    int hasdata=0;    
#ifdef EM8634
    int memfd;
    memfd=open("/dev/mem", O_RDWR|O_SYNC);
    if(memfd<0)
    {
        fprintf(stderr, "Couldn't open /dev/mem %d\n");
        return(0);
    }
    pgbus=1; // To pass null test
    gbusmem = mmap(0, 0x100000, PROT_READ|PROT_WRITE, MAP_SHARED, 
        memfd, 0x0);
    if(gbusmem==MAP_FAILED)
    {
        fprintf(stderr,"Couldn't map hwmem %d\n");
        close(memfd);
        return(0);
    }
#else
    gbusmem = 0;
#endif
    MediaCmdMutex = ACL_CreateMutex();
    while((hasdata=ReadInput(handle,&event,&len,&data))>=0)
    {
        if(hasdata)
        {
            keycode=data[3]|(data[2]<<8)|(data[1]<<16)|(data[0]<<24);
            if((keycode&~2048)==6075 || (keycode&~2048)==5920)
            {
                retval=2;
                break;
            }
        }
        if(argc>1)
        {
            if(hasdata)printf("%08X",keycode);
            break;
        }
    }
    CloseInput(handle);
    exit(retval);
}
