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
#include <sys/types.h>
#include <unistd.h>
#include <signal.h>
#include <float.h>
#include <math.h>
#include <string.h>

main(int argc, char **argv)
{
    char reply[1024];
    int hasreply=0;
    int i=0;
    FILE *configflash;
    if(argc<2)
    {
        fprintf(stderr, "Usage %s <property>\n", argv[0]);
        exit(0);
    }
    configflash=fopen("/rw/sage/Sage.properties","rb");
    if(configflash)
    {
        char tmpline[1024];
        int palmode=0;
        tmpline[1023]=0;
        while(!feof(configflash))
        {
            if(fgets(tmpline, 1023, configflash)==NULL)
                break;
            if(strncasecmp(tmpline,argv[1],strlen(argv[1]))==0)
            {
                strcpy(reply, tmpline+strlen(argv[1])+1);
                hasreply=1;
            }
            if(((unsigned char) tmpline[0])==0xFF)
                break;
        }
        fclose(configflash);
    }

    if(hasreply)
    {
        for(i=0;i<strlen(reply);i++)
        {
            if(reply[i]=='\n') reply[i]=0;
        }
        printf("%s",reply);
    }
}
