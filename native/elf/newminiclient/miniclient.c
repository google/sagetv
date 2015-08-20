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
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/reboot.h>
#include <dirent.h>
#include <net/if.h>
#include <errno.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <signal.h>
#include <pthread.h>
#include <time.h>

#include "inputcalls.h"
#include "thread_util.h"
#include "gfxcalls.h"
#include "mediacalls.h"
#include "mediacmd.h"
#include "mcprop.h"
#include "fscmd.h"
#include "gfxcmd.h"

#ifdef EM86
#define DirectPush
#endif

#ifdef PIONEER
float cosf(float a)
{
    return (float) cos((double)a);
}

float sinf(float a)
{
    return (float) sin((double)a);
}
#endif

typedef struct {
    long long size;
    FILE *dfile;
    unsigned int secureID;
    ACL_Thread *thread; // Don't use unless you have the doneThreadsMutex
} TransferStream;

typedef struct {
    int sockfd;
    struct sockaddr_in server_addr;
} ServerConnection;

#define TESTMULTICAST
#ifdef TESTMULTICAST
typedef struct {
    ServerConnection sc;
    struct sockaddr_in client_addr;
    int client_len;
} multicastReceiver;

multicastReceiver *mr=NULL;
#endif

#ifdef EM86
#include <openssl/evp.h>
#include <openssl/rsa.h>
#include <openssl/x509.h>
#endif

extern int ExecuteGFXCommand(int cmd, int len, unsigned char *cmddata, int *hasret, int sd);
extern int ExecuteMediaCommand(int cmd, int len, unsigned char *cmddata, int *hasret, int sd);

char *gfxservername=NULL;
int gfxport=31099;

// Key used for encryption
char pubkey[256];
char symkey[128]; // encrypted symmetric key
char ourkey[16]; // Blowfish key for encryption
unsigned char encryptbuffer[4096];
char videomode[256];
int encryptenabled=0;
unsigned char gfxcmdbuffer[12288+4]; // For extra padding in properties...
char aspectProp[16];
char firmwareversion[128]={"UNKNOWN"};
#ifdef EM86
char hdmimode[16];
char advancedAspect[256]={"Source"};
char defaultadvancedAspect[256]={"Source"};

char audioout[16];
extern int audiooutput;
extern int dviSampleRate;
extern int dvimode;
char ntscdefaultmodes[] =
{ "720x480i@59.94|standard=NTSC_M;720x480p@59.94|standard=480p59;"
   "1280x720p@59.94|standard=720p59;"
   "1920x1080p@23.976|standard=1080p23;"
   "1920x1080i@59.94|standard=1080i59;"
   "1920x1080p@59.94|standard=1080p59;"
    };
char ntscdigitaldefaultmodes[] ={ "720x480i@59.94|standard=HDMI_480i59;"
    "720x480p@59.94|standard=HDMI_480p59;"
    "1280x720p@59.94|standard=HDMI_720p59;"
   "1920x1080p@23.976|standard=HDMI_1080p23;"
    "1920x1080i@59.94|standard=HDMI_1080i59;"
   "1920x1080p@59.94|standard=HDMI_1080p59;"
   };
// TODO: set back 480 to 576 in pal modes when fixed in nanoserver
char paldefaultmodes[] =
{ "720x576i@50|standard=PAL_BG;720x576p@50|standard=576p50;"
   "1280x720p@50|standard=720p50;"
   "1920x1080p@23.976|standard=1080p23;"
   "1920x1080i@50|standard=1080i50;"
   "1920x1080p@50|standard=1080p50;"
    };
char paldigitaldefaultmodes[] ={ "720x576i@50|standard=HDMI_576i50;"
    "720x576p@59.94|standard=HDMI_576p50;"
    "1280x720p@50|standard=HDMI_720p50;"
   "1920x1080p@23.976|standard=HDMI_1080p23;"
    "1920x1080i@50|standard=HDMI_1080i50;"
   "1920x1080p@50|standard=HDMI_1080p50;"
   };
char standardmodes[4096] =
{ "720x480i@59.94|standard=NTSC_M;720x480p@59.94|standard=480p59;"
   "1280x720p@59.94|standard=720p59;"
   "1920x1080p@23.976|standard=1080p23;"
   "1920x1080i@59.94|standard=1080i59;"
   "1920x1080p@59.94|standard=1080p59;"
    };
char digitalmodes[4096]={"720x480i@59.94|standard=HDMI_480i59;"
    "720x480p@59.94|standard=HDMI_480p59;"
    "1280x720p@59.94|standard=HDMI_720p59;"
    "1920x1080p@23.976|standard=1080p23;"
    "1920x1080i@59.94|standard=HDMI_1080i59;"
    "1920x1080p@59.94|standard=HDMI_1080p59;"
   };
char tvstandard[16]={"NTSC"}; // NTSC,PAL
void Output_SetMode(char *mode);
char subtitlecallback[16]={"FALSE"};
int subtitleenable=0; // Don't send subtitles
#endif
#ifdef EM8634
char cachedauth[33];
char cachedsetauth[128];
char configservername[1024];
#endif
char gfxflip[16]={"FALSE"};
extern int gfxcanflip;

#ifdef EM8654
char advdeintstr[16]={"TRUE"};
int advdeint=1;
#endif

#ifdef EM86
EVP_CIPHER_CTX ctx;
EVP_CIPHER_CTX decctx;
#endif

int needuisize=0;
int needhdmi=0;
int mediaevent=0;
int mediaeventdatalen=0;
unsigned char *mediaeventdata=NULL;
int uiwidth=1024;
int uiheight=576;
#ifdef EM86
extern int pushmode;
#endif
// Notes: the ints are stored in network order

typedef struct {
    unsigned char type;
    unsigned char size0;
    unsigned char size1;
    unsigned char size2;
    unsigned int timestamp;
    unsigned int ID;
    unsigned int pad;
}ReplyPacket;

ACL_mutex *UISendMutex;
ACL_mutex *MediaCmdMutex;

// Handle join of threads that have completed...
ACL_Thread *doneThreads[1024];
int doneThreadsCount=0;
ACL_mutex *doneThreadsMutex;

int uisockfd=-1;
int mediasockfd=-1;

#define DEBUGCLIENT
#define DEBUGCLIENTFS
//#define DEBUGCLIENT2
//#define DEBUGCLIENT3
#define DEBUGPROPERTIES

int ConnectionError=0;
int ExitValue;
int inputhandle;

long long prevcmdtime=0;
int prevcmd=0;

static int readInt(int pos, unsigned char *cmddata)
{
    return (cmddata[pos+0]<<24)|(cmddata[pos+1]<<16)|(cmddata[pos+2]<<8)|(cmddata[pos+3]);
}

static unsigned long long readLongLong(int pos, unsigned char *cmddata)
{
    unsigned int a,b;
    unsigned long long c;
    a=readInt(pos,cmddata);
    b=readInt(pos+4,cmddata);
    c=a;
    c<<=32;
    c|=b;
    return c;
}

static short readShort(int pos, unsigned char *cmddata)
{
    return (cmddata[pos+0]<<8)|(cmddata[pos+1]);
}

static long long get_timebase()
{
  struct timeval tm;
  gettimeofday(&tm, 0);
  return tm.tv_sec * 1000000LL + (tm.tv_usec);
}

// Note: this will modify settings by inserting 00s at separator
// Format: Prop=attr*|
ParseSettings(unsigned char *settings, unsigned char **tokens, int *ntokens, 
    int *properties, int *nproperties, int maxtokens)
{
    *nproperties=1;
    *ntokens=1;
    properties[0]=0; // Token 0 is first property
    tokens[0]=settings;
    while((*settings)!=0 && *ntokens<(maxtokens-1))
    {
        if(*settings=='|')
        {
            *settings=0;
            properties[*nproperties]=*ntokens;
            *nproperties=*nproperties+1;
            tokens[*ntokens]=settings+1;
            *ntokens=*ntokens+1;
        }
        else if(*settings==',' || *settings=='=')
        {
            *settings=0;
            tokens[*ntokens]=settings+1;
            *ntokens=*ntokens+1;
        }
        settings++;
    }
    properties[*nproperties]=*ntokens;
}


// TODO: this could probably be improved
int fullrecv(int sock, void *vbuffer, int size)
{
    int cur=0;
    int count=0;
    unsigned char *buffer=(unsigned char *) vbuffer;

    while(cur<size)
    {
        count=recv(sock,&buffer[cur],size-cur,0);
        if(count<=0)
        {
            if(count==0)
            {
                fprintf(stderr, "Connection has been terminated by server\n");
            }
            else
            {
                perror("recv");
            }
            fflush(stdin);
            fflush(stderr);
            return count;
        }
        cur+=count;
    }
    return size;
}

int GetMACAddress(unsigned char *buffer)
{
    int fd;
    unsigned char *hw;
    struct ifreq ifreq;

    fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
    strcpy(ifreq.ifr_name, "eth0");
    ioctl(fd, SIOCGIFHWADDR, &ifreq);
    hw = (unsigned char *) ifreq.ifr_hwaddr.sa_data;
    buffer[0]=hw[0];
    buffer[1]=hw[1];
    buffer[2]=hw[2];
    buffer[3]=hw[3];
    buffer[4]=hw[4];
    buffer[5]=hw[5];
    close(fd);
    return 0;
}

ServerConnection* EstablishServerConnection(char *servername, int port, int type)
{
    ServerConnection sc;
    ServerConnection *outsc;
    int flag=1;
    int blockingmode;

    memset(&sc, 0, sizeof(ServerConnection));

    if((sc.sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0)
    {
        #ifdef DEBUGCLIENT
        perror("socket");
        #endif
        return NULL;
    }

    sc.server_addr.sin_family = AF_INET;
    sc.server_addr.sin_addr.s_addr = inet_addr(servername);
    sc.server_addr.sin_port = htons(port);

    blockingmode = fcntl(sc.sockfd, F_GETFL, NULL);
    if(blockingmode < 0)
    {
        #ifdef DEBUGCLIENT
        perror("fcntl F_GETFL");
        #endif
        close(sc.sockfd);
        return NULL;
    }

    blockingmode |= O_NONBLOCK;
    if(fcntl(sc.sockfd, F_SETFL, blockingmode)<0)
    {
        #ifdef DEBUGCLIENT
        perror("fcntl F_SETFL");
        #endif
        close(sc.sockfd);
        return NULL;
    }

    if(connect(sc.sockfd, (struct sockaddr *) &sc.server_addr,
       sizeof(struct sockaddr)) < 0)
    {
        fd_set wfds;
        fprintf(stderr, "Connect error %d\n",errno);
        if(errno == EINPROGRESS)
        {
            // Wait until there is success, error or timeout...
            while(1)
            {
                struct timeval tv;
                int selret;
                tv.tv_sec = 2;  // Wait 2 seconds for connection
                tv.tv_usec = 0; 

                FD_ZERO(&wfds); 
                FD_SET(sc.sockfd, &wfds);
                selret = select(sc.sockfd+1, NULL, &wfds, NULL, &tv); 
                fprintf(stderr, "Select returned %d\n", selret);
                if(selret<=0)
                {
                    // Possibly a timeout
                    #ifdef DEBUGCLIENT
                    perror("connect");
                    #endif
                    close(sc.sockfd);
                    return NULL;
                }
                else
                {
                    // We should be connected here... 
                    // TODO: maybe add better error code, FD_ISSET...
                    break;
                }
            }
        }
        else
        {
            return NULL;
        }
    }

    blockingmode = fcntl(sc.sockfd, F_GETFL, NULL);
    if(blockingmode < 0)
    {
        #ifdef DEBUGCLIENT
        perror("fcntl F_GETFL");
        #endif
        return NULL;
    }

    blockingmode &= ~O_NONBLOCK;
    if(fcntl(sc.sockfd, F_SETFL, blockingmode)<0)
    {
        #ifdef DEBUGCLIENT
        perror("fcntl F_SETFL");
        #endif
        return NULL;
    }

    setsockopt(sc.sockfd,
               IPPROTO_TCP,
               TCP_NODELAY,
               (char *) &flag,
                sizeof(int));
    fprintf(stderr,"Sending connect request header\n");
    fflush(stderr);
    // Send protocol version, mac address then see if we get back reply of 2
    {
        char msg[8] = { 1, 0, 0, 0, 0, 0, 0, 0 };
        msg[7]=type;
        GetMACAddress((unsigned char *)&msg[1]);
        if(send(sc.sockfd,&msg[0],8,MSG_NOSIGNAL)<0)
        {
            #ifdef DEBUGCLIENT
            perror("send ver+addr");
            #endif
            close(sc.sockfd);
            return NULL;
        }
        if(fullrecv(sc.sockfd, &msg[0], 1)<1 || msg[0]!=2)
        {            
            fprintf(stderr, "Error with reply from server\n");
            close(sc.sockfd);
            return NULL;
        }
        fprintf(stderr, "Connection accepted by server\n");
    }
    outsc = (ServerConnection *) malloc(sizeof(ServerConnection));
    if(outsc==NULL) return NULL;
    memcpy(outsc, &sc, sizeof(ServerConnection));
    return outsc;
}

DropServerConnection(ServerConnection *sc)
{
    // TODO: don't use that if we want it to work on windows
    close(sc->sockfd);
    free(sc);
}

int SendUIReply(int uisockfd, int type, void *data, int len)
{
    ReplyPacket packreply;
    unsigned int timestamp=0;
    unsigned int ID=0;

    packreply.type=type;
    packreply.size0=(len>>16)&0xFF;
    packreply.size1=(len>>8)&0xFF;
    packreply.size2=(len>>0)&0xFF;

    // make sure this is in network byte order...
    packreply.timestamp=htonl(timestamp);
    packreply.ID=htonl(ID);
    packreply.pad=htonl(0);

    send(uisockfd,&packreply,16,MSG_NOSIGNAL);
#ifdef EM86
    if(encryptenabled)
    {
        int olen,tlen;
        int k;
        if(len>4000)
        {
            fprintf(stderr, "Too long reply for encryption\n");
            return -1;
        }
        EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, (unsigned char *) data, len);
        if(EVP_EncryptFinal(&ctx, encryptbuffer + olen, &tlen) != 1)
        {
            fprintf(stderr, "EVP_EncryptFinal\n");
            return -1;
        }
        olen += tlen;
        send(uisockfd, encryptbuffer, olen, MSG_NOSIGNAL);
        if(len&0x7) EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, encryptbuffer, 8-(len&0x7));
    }
    else
#endif
    {
        send(uisockfd,data,len,MSG_NOSIGNAL);
    }
    return 0;
}


int FSDownloadThread(void *data)
{
    TransferStream *ts=(TransferStream *) data;
    char *databuffer=malloc(16384);
    long long size=ts->size;
    FILE *dfile=ts->dfile;
    unsigned int secureID=htonl(ts->secureID);
    ServerConnection *sc = NULL;
    int retval=0;
    free(data);
    if(databuffer==NULL)
    {
        retval=-1;
        goto transferdone;
    }

    printf("Trying to connect to server at %s:%d\n",gfxservername,gfxport);
    sc = EstablishServerConnection(gfxservername, gfxport, 2);
    if(sc==NULL)
    {
        fprintf(stderr, "couldn't connect to file server.\n");
        retval=-1;
        goto transferdone;
    }
    if(send(sc->sockfd, &secureID, 4, MSG_NOSIGNAL)==-1)
    {
        retval=-1;
        goto transferdone;
    }
    fprintf(stderr, "Downloading file of size %lld\n",ts->size);
    while(size)
    {
        if(fullrecv(sc->sockfd, databuffer, size>16384 ? 16384 : size) != (size>16384 ? 16384 : size))
        {
            retval=-1;
            fprintf(stderr,"Error receiving file with %lld remaining\n", size);
            goto transferdone;
        }
        if(fwrite(databuffer, size>16384 ? 16384 : size, 1, dfile)!=1)
        {
            retval=-1;
            fprintf(stderr,"Error writing file with %lld remaining\n", size);
            goto transferdone;
        }
        size -= (size>16384 ? 16384 : size);
    }
    send(sc->sockfd, &retval, 4, MSG_NOSIGNAL);
transferdone:
    if(sc)
    {
        close(sc->sockfd);
        free(sc);
        sc=NULL;
    }
    fclose(dfile);
    ACL_LockMutex(doneThreadsMutex);
    doneThreads[doneThreadsCount]=ts->thread;
    doneThreadsCount++;
    ACL_UnlockMutex(doneThreadsMutex);
    if(databuffer!=NULL)
    {
        free(databuffer);
    }
    fprintf(stderr,"Download thread done %X\n", ts->thread);
    return retval;
}

int FSUploadThread(void *data)
{
    TransferStream *ts=(TransferStream *) data;
    char *databuffer=malloc(16384);
    long long size=ts->size;
    FILE *dfile=ts->dfile;
    unsigned int secureID=htonl(ts->secureID);
    ServerConnection *sc = NULL;
    int retval=0;
    free(data);
    if(databuffer==NULL)
    {
        retval=-1;
        goto transferdone;
    }

    printf("Trying to connect to server at %s:%d\n",gfxservername,gfxport);
    sc = EstablishServerConnection(gfxservername, gfxport, 2);
    if(sc==NULL)
    {
        fprintf(stderr, "couldn't connect to file server.\n");
        retval=-1;
        goto transferdone;
    }
    if(send(sc->sockfd, &secureID, 4, MSG_NOSIGNAL)!=4)
    {
        retval=-1;
        goto transferdone;
    }

    fprintf(stderr, "Uploading file of size %lld\n",ts->size);
    while(size)
    {
        if(fread(databuffer, (size>16384 ? 16384 : size), 1, dfile)!=1)
        {
            retval=-1;
            fprintf(stderr,"Error reading file with %lld remaining\n", size);
            goto transferdone;
        }
        if(send(sc->sockfd, databuffer, (size>16384 ? 16384 : size), MSG_NOSIGNAL)!=(size>16384 ? 16384 : size))
        {
            retval=-1;
            fprintf(stderr,"Error sending file with %lld remaining\n", size);
            goto transferdone;
        }
        size -= (size>16384 ? 16384 : size);
    }

transferdone:
    if(sc)
    {
        close(sc->sockfd);
        free(sc);
        sc=NULL;
    }
    fclose(dfile);
    ACL_LockMutex(doneThreadsMutex);
    doneThreads[doneThreadsCount]=ts->thread;
    doneThreadsCount++;
    ACL_UnlockMutex(doneThreadsMutex);
    if(databuffer!=NULL)
    {
        free(databuffer);
    }
    fprintf(stderr,"Upload thread done %X\n", ts->thread);
    return retval;
}


int ProcessGFXCommand(int sockfd)
{
    unsigned char cmd[4];
    unsigned int command,len;
    int hasret=0;
    unsigned int retval=0;
    unsigned char retbuf[4];
    fd_set rfds;
    struct timeval tv;
    int maxfd=0;

    FD_ZERO(&rfds);
    FD_SET(sockfd, &rfds);
    maxfd=sockfd;

    // 0.1 second timeout should be fine for now
    tv.tv_sec = 0;
    tv.tv_usec = 100000;

    retval = select(maxfd+1, &rfds, NULL, NULL, &tv);

    if (retval == -1)
        return -1;

    if(FD_ISSET(sockfd, &rfds))
    {
        FD_CLR(sockfd, &rfds);
        if(fullrecv(sockfd, &cmd, 4)<4)
        {
            return -1;
        }
        command=cmd[0];
        len=cmd[1]<<16|cmd[2]<<8|cmd[3];
    #ifdef DEBUGCLIENT2
        fprintf(stderr, "Received command %d %d\n",command, len);
    #endif    
        if(len>12288)
        {
            fprintf(stderr, "Gfx command is too long for this client %d %d\n", command, len);
            // TODO: read all the bytes?
            //return -2;
            len=12288;
        }

        if(fullrecv(sockfd, &gfxcmdbuffer, len)<len)
        {
            return -3;
        }

        if(command==0) // Query Property
        {
            ReplyPacket packreply;
            unsigned int timestamp=0;
            unsigned int ID=0;
            int replylen=0;
            int i=0;
            gfxcmdbuffer[len]=0;
    #ifdef DEBUGPROPERTIES
            fprintf(stderr, "Query Property %s\n", gfxcmdbuffer);
    #endif


            while(MCProperties[i].name!=NULL)
            {
                if(strcasecmp((char *) gfxcmdbuffer, MCProperties[i].name)==0)
                {
    #ifdef EM86
                    if(MCProperties[i].value==hdmimode)
                    {
                        switch(dvimode)
                        {
                            case -1:
                                snprintf(hdmimode, 16, "Unknown");
                                break;
                            case 0:
                                snprintf(hdmimode, 16, "None");
                                break;
                            case 1:
                                snprintf(hdmimode, 16, "DVI");
                                break;
                            case 2:
                                snprintf(hdmimode, 16, "HDMI");
                                break;
                        }
                    }
                    if(MCProperties[i].value==digitalmodes)
                    {
                        Output_UpdateModes();
                    }
                    if(MCProperties[i].value==audioout)
                    {
                        if(audiooutput==0)
                            strcpy(audioout, "Analog");
                        else if(audiooutput==1)
                            strcpy(audioout, "Digital");
                        else if(audiooutput==2)
                            strcpy(audioout, "HDMI");
                        else if(audiooutput==3)
                            strcpy(audioout, "HDMIHBR");
                        else
                            strcpy(audioout, "UNKNOWN");
                    }
    #endif
                    if(MCProperties[i].value!=symkey)
                    {
                        fprintf(stderr, "Sending back %s\n",MCProperties[i].value);
                    }
                    if(MCProperties[i].len==0)
                    {                
                        replylen=strlen(MCProperties[i].value);
                    }
                    else
                    {
                        replylen=MCProperties[i].len;
                    }
                    break;
                }
                i++;
            }

            packreply.type=0;
            packreply.size0=(replylen>>16)&0xFF;
            packreply.size1=(replylen>>8)&0xFF;
            packreply.size2=(replylen>>0)&0xFF;

            // make sure this is in network byte order...
            packreply.timestamp=htonl(timestamp);
            packreply.ID=htonl(ID);
            packreply.pad=htonl(0);
            retval=htonl(retval);

            ACL_LockMutex(UISendMutex);
            send(uisockfd, &packreply, 16, MSG_NOSIGNAL);


            if(replylen!=0)
            {
    #ifdef EM86
                if(encryptenabled)
                {
                    int olen,tlen;
                    if(replylen>4000)
                    {
                        fprintf(stderr, "Too long reply for encryption\n");
                        return -1;
                    }
                    EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, (unsigned char *) MCProperties[i].value, replylen);
                    if(EVP_EncryptFinal(&ctx, encryptbuffer + olen, &tlen) != 1)
                    {
                        fprintf(stderr, "EVP_EncryptFinal\n");
                        return -1;
                    }
                    olen += tlen;
                    send(uisockfd, encryptbuffer, olen, MSG_NOSIGNAL);
                    if(replylen&0x7) EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, encryptbuffer, 8-(replylen&0x7));
                }
                else
    #endif
                {
                    send(uisockfd, MCProperties[i].value, replylen, MSG_NOSIGNAL);
                }
            }
            ACL_UnlockMutex(UISendMutex);
        }
        else if(command==1) // Set Property
        {
            ReplyPacket packreply;
            unsigned int timestamp=0;
            unsigned int ID=0;
            int i;
            int z;
            int len=4;
            int namelen,vallen;
            unsigned char *propname, *propval;
            int encryptthispropertyretval=encryptenabled;
            namelen=(gfxcmdbuffer[0]<<8)|(gfxcmdbuffer[1]);
            vallen=(gfxcmdbuffer[2]<<8)|(gfxcmdbuffer[3]);
            for(z=0;z<vallen;z++) gfxcmdbuffer[4+namelen+vallen-z]=gfxcmdbuffer[4+namelen+vallen-1-z];
            propname=&gfxcmdbuffer[4];
            propval=&gfxcmdbuffer[4+namelen+1];

            gfxcmdbuffer[4+namelen]=0;
            gfxcmdbuffer[4+namelen+1+vallen]=0;


    #ifdef DEBUGPROPERTIES
            fprintf(stderr, "Set Property %s\n",propname);
            fflush(stderr);
    #endif
            retval=1;
            i=0;
            while(MCProperties[i].name!=NULL)
            {
                // TODO: fix this to match the right names if len don't match
                if(strncasecmp((char *)propname, MCProperties[i].name, namelen)==0 && 
                    strlen(MCProperties[i].name)==namelen )
                {
                    fprintf(stderr, "Found %s\n",MCProperties[i].name);
    #ifdef EM86
                    if(MCProperties[i].value==pubkey)
                    {
                        EVP_PKEY *pkey = NULL;
                        RSA *rsa = NULL;
                        unsigned char *keyptr;
                        int keysize;
                        int k;
                        keyptr=propval;
                        pkey = d2i_PUBKEY(NULL, (const unsigned char **) &keyptr, vallen);

                        if(!pkey)
                        {
                            fprintf(stderr, "Could not read public key\n");
                        }
                        rsa = EVP_PKEY_get1_RSA(pkey);
                        EVP_PKEY_free(pkey);
                        if(!rsa)
                        {
                            fprintf(stderr, "Error getting rsa key\n");
                        }
                        keysize = RSA_size(rsa);
                        fprintf(stderr, "Key size %d\n", keysize);

                        RAND_pseudo_bytes(ourkey, 16);
                        for(k=0;k<16;k++) fprintf(stderr, "%d\n", ourkey[k]);

                        RSA_public_encrypt(16, (unsigned char *) ourkey, (unsigned char *) symkey, 
                            rsa, RSA_PKCS1_PADDING);
                        EVP_CIPHER_CTX_init(&ctx);

                        EVP_EncryptInit(&ctx, EVP_bf_ecb(), (unsigned char *) ourkey, NULL);
                        RSA_free(rsa);

                        fprintf(stderr, "Setting public key %d bytes\n", vallen);

                    }
                    else if(MCProperties[i].value==symkey)
                    {
                        fprintf(stderr, "Setting symmetric key\n");
                    }
                    else if(MCProperties[i].value==(char*) &encryptenabled)
                    {
                        fprintf(stderr, "Setting encryptenabled key\n");
                        if(strncasecmp((char *) propval, "TRUE", vallen)==0)
                        {
                            encryptenabled=1;
                        }
                        else
                        {
                            encryptenabled=0;
                        }
                        fprintf(stderr, "encryptenabled is now %d\n",encryptenabled);
                    }
                    else 
    #endif
                    if(MCProperties[i].value==videomode)
                    {
                        unsigned char cmd1[4];
                        int hasret;
                        cmd1[0]=0;
                        cmd1[1]=0;
                        cmd1[2]=0;
                        cmd1[3]=0;
                        fprintf(stderr, "Setting video mode\n");
                        if(strcmp((char *) propval, "720x480i@59.94")==0)
                        {
                            fprintf(stderr,"Found 720x480i@59.94\n");
                            cmd1[3]=0;
                            retval = ExecuteGFXCommand(128, 4, &cmd1[0], &hasret, uisockfd);
                        }
    #ifdef EM86
                        // Prevents memory fragmentation issue if there is a call to media init
                        ACL_LockMutex(MediaCmdMutex);
                        Output_SetMode((char *) propval);
                        ACL_UnlockMutex(MediaCmdMutex);
    #endif
                    }
                    if(MCProperties[i].value==aspectProp)
                    {
                        unsigned char cmd1[4];
                        int hasret;
                        unsigned int newaspect;
                        strncpy(aspectProp, (char *) propval, 15);
                        aspectProp[15]=0;
                        fprintf(stderr, "Found %s\n",aspectProp);
                        newaspect=(int) ((float) atof(aspectProp)*1000.0f);
                        cmd1[0]=(newaspect>>24)&0xFF;
                        cmd1[1]=(newaspect>>16)&0xFF;
                        cmd1[2]=(newaspect>>8)&0xFF;
                        cmd1[3]=(newaspect>>0)&0xFF;
                        retval = ExecuteGFXCommand(129, 4, &cmd1[0], &hasret, uisockfd);
                        uiwidth=(retval&0xFFFF);
                        uiheight=(retval>>16)&0xFFFF;
                        needuisize=1;
                    }
    #ifdef EM86
                    else if(MCProperties[i].value==audioout)
                    {
                        strncpy(audioout, (char *) propval, 15);
                        audioout[15]=0;
                        if(strcasecmp(audioout,"Analog")==0)
                        {
                            audiooutput=0;
                        }
                        else if(strcasecmp(audioout,"Digital")==0)
                        {
                            audiooutput=1;
                        }
                        else if(strcasecmp(audioout,"HDMI")==0)
                        {
                            audiooutput=2;
                        }
                        else if(strcasecmp(audioout,"HDMIHBR")==0)
                        {
                            audiooutput=3;
                        }
                        dviSampleRate=-1;
                        fprintf(stderr, "Setting audiooutput to %d\n",audiooutput);
                    }
                    else if(MCProperties[i].value==advancedAspect)
                    {
                        strncpy(advancedAspect, (char *) propval, 255);
                        advancedAspect[255]=0;
                        fprintf(stderr, "Found %s\n",advancedAspect);
                        Media_SetAdvancedAspect(advancedAspect);
                    }
                    else if(MCProperties[i].value==defaultadvancedAspect)
                    {
                        strncpy(defaultadvancedAspect, (char *) propval, 255);
                        defaultadvancedAspect[255]=0;
                        fprintf(stderr, "Found %s\n",defaultadvancedAspect);
                    }
                    else if(MCProperties[i].value==subtitlecallback)
                    {
                        strncpy(subtitlecallback, (char *) propval, 15);
                        subtitlecallback[15]=0;
                        subtitleenable=0;
                        if(strcasecmp(subtitlecallback, "TRUE")==0)
                        {
                            subtitleenable=1;
                        }
                    }
    #endif
    #ifdef EM8634
                    else if(MCProperties[i].value==cachedsetauth)
                    {
                        FILE *tmpfile;
                        int olen,tlen;
                        EVP_CIPHER_CTX_init(&decctx);
                        if(vallen>4000)
                        {
                            fprintf(stderr, "property value too long\n");
                            return -1;
                        }
                        fprintf(stderr, "encrypted length %d\n",vallen);
                        EVP_DecryptInit(&decctx, EVP_bf_ecb(), (unsigned char *) ourkey, NULL);
                        EVP_DecryptUpdate(&decctx, encryptbuffer, &olen, propval, vallen);
                        if(EVP_EncryptFinal(&decctx, encryptbuffer + olen, &tlen) != 1)
                        {
                            fprintf(stderr, "EVP_DecryptFinal\n");
                            return -1;
                        }
                        EVP_CIPHER_CTX_cleanup(&decctx);
                        olen+=tlen;
                        encryptbuffer[32]=0;
                        fprintf(stderr, "decrypted length %d (%d)\n", olen, tlen);
                        tmpfile=fopen("/tmp/patch.properties","wb");
                        if(tmpfile!=NULL)
                        {
                            fprintf(tmpfile,"auth/%s=%s\n",configservername, encryptbuffer);
                            fclose(tmpfile);
                            tmpfile=NULL;
                        }
                    }
    #endif
                    else if(MCProperties[i].value==gfxflip)
                    {
                        strncpy(gfxflip, (char *) propval, 15);
                        gfxflip[15]=0;
                        gfxcanflip=0;
                        if(strcasecmp(gfxflip, "TRUE")==0)
                        {
                            gfxcanflip=1;
                            fprintf(stderr, "using gfx buffer flip\n");
                        }
                    }
#ifdef EM8654
                    else if(MCProperties[i].value==advdeintstr)
                    {
                        strncpy(advdeintstr, (char *) propval, 15);
                        advdeintstr[15]=0;
                        advdeint=0;
                        if(strcasecmp(advdeintstr, "TRUE")==0)
                        {
                            advdeint=1;
                        }
                        fprintf(stderr, "advdeint %d\n",advdeint);
                    }
#endif
                    else
                    {
                        fprintf(stderr, "Setting %s to %s\n", propname, propval);
                    }
                    retval=0;
                    break;
                }
                i++;
            }

            packreply.type=1;
            packreply.size0=(len>>16)&0xFF;
            packreply.size1=(len>>8)&0xFF;
            packreply.size2=(len>>0)&0xFF;

            // make sure this is in network byte order...
            packreply.timestamp=htonl(timestamp);
            packreply.ID=htonl(ID);
            packreply.pad=htonl(0);
            retval=htonl(retval);

            // Watch for asynch encryption
            ACL_LockMutex(UISendMutex);
            send(uisockfd,&packreply,16,MSG_NOSIGNAL);
    #ifdef EM86
            if(encryptthispropertyretval)
            {
                int olen, tlen;
                if(len>4000)
                {
                    fprintf(stderr, "Too long reply for encryption\n");
                    return -1;
                }
                EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, (unsigned char *) &retval, len);
                if(EVP_EncryptFinal(&ctx, encryptbuffer + olen, &tlen) != 1)
                {
                    fprintf(stderr, "EVP_EncryptFinal\n");
                    return -1;
                }
                olen += tlen;
                send(uisockfd, encryptbuffer, olen, MSG_NOSIGNAL);
                if(len&0x7) EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, encryptbuffer, 8-(len&0x7));
            }
            else
    #endif
            {
                send(uisockfd,&retval,4,MSG_NOSIGNAL);
            }
            ACL_UnlockMutex(UISendMutex);
        }
        else if(command==16 && len>=4)
        {
            int gfxcommand,gfxlen;
            gfxcommand=gfxcmdbuffer[0];
            gfxlen=gfxcmdbuffer[1]<<16 |
                gfxcmdbuffer[2]<<8 |
                gfxcmdbuffer[3]<<0;
    #ifdef DEBUGCLIENT3
            long long curcmdtime=get_timebase();
    #endif
            if(gfxcommand==GFXCMD_TEXTUREBATCH)
            {
                if(gfxlen>=8)
                {
                    int textureBatchCount,textureBatchSize;
                    int i,gfxoffset=16;
                    textureBatchCount=
                        (gfxcmdbuffer[4]<<24) |
                        (gfxcmdbuffer[5]<<16) |
                        (gfxcmdbuffer[6]<<8) |
                        (gfxcmdbuffer[7]<<0);
                    textureBatchSize=
                        (gfxcmdbuffer[8]<<24) |
                        (gfxcmdbuffer[9]<<16) |
                        (gfxcmdbuffer[10]<<8) |
                        (gfxcmdbuffer[11]<<0);
                    //fprintf(stderr, "Batch %d %d (%d)\n",textureBatchCount,textureBatchSize, len);
                    for(i=0;i<textureBatchCount;i++)
                    {
                        gfxcommand=gfxcmdbuffer[0+gfxoffset];
                        gfxlen=gfxcmdbuffer[1+gfxoffset]<<16 |
                            gfxcmdbuffer[2+gfxoffset]<<8 |
                            gfxcmdbuffer[3+gfxoffset]<<0;
                        //fprintf(stderr, "Batch cmd %d %d\n",gfxcommand,gfxlen);
                        retval = ExecuteGFXCommand(gfxcommand, gfxlen,
                            gfxcmdbuffer+4+gfxoffset, &hasret, uisockfd);
                        gfxoffset+=4+4+gfxlen;
                    }
                }
            }
            else
            {
                retval = ExecuteGFXCommand(gfxcommand, gfxlen, gfxcmdbuffer+4, &hasret, uisockfd);
            }

    #ifdef DEBUGCLIENT3
            // command took more than 10ms
            if((get_timebase()-curcmdtime)/1000LL > 10LL)
            {
                fprintf(stderr, "gfxcommand %d %d took %lld\n",gfxcommand, gfxlen, 
                    (get_timebase()-curcmdtime)/1000LL);
            }
            // time since last command took more than 20ms
            if((get_timebase()-prevcmdtime)/1000LL > 20LL) // more than 10ms
            {
                fprintf(stderr, "gfxcommand %d %d prev delta %lld with cmd %d\n",gfxcommand, gfxlen, 
                    (get_timebase()-prevcmdtime)/1000LL, prevcmd);
            }
            prevcmdtime=get_timebase();
            prevcmd=gfxcommand;
    #endif

    #ifdef DEBUGCLIENT2
            fprintf(stderr, "done %d %d\n",hasret, retval);
    #endif

            if(hasret)
            {
                retval=htonl(retval);
                ACL_LockMutex(UISendMutex);
                retval = SendUIReply(uisockfd, 16, &retval,4);
                ACL_UnlockMutex(UISendMutex);
                return retval;
            }
        }
#ifdef EM8634
        else if(command==2 && len>=4) // FS commands
        {
            int fscommand,fslen;
            int pathlen;
            long long retval;
            int retlen=4;
            int hasret=1;
            char fname[PATH_MAX];
            struct stat statbuf;
            fscommand=gfxcmdbuffer[0];
            fslen=gfxcmdbuffer[1]<<16 |
                gfxcmdbuffer[2]<<8 |
                gfxcmdbuffer[3]<<0;
    #ifdef DEBUGCLIENTFS
            fprintf(stderr, "fscommand %d %d\n",fscommand, fslen);
    #endif
            switch(fscommand)
            {
                case FSCMD_CREATE_DIRECTORY:
                    pathlen = readShort(4, gfxcmdbuffer);
                    if(pathlen>12000) 
                    {
                        retval=FS_RV_ERROR_UNKNOWN;
                        break;
                    }
                    gfxcmdbuffer[6+pathlen]=0;
                    strcpy(fname, "/tmp/external/");
                    strncpy(fname+14, (char *) &gfxcmdbuffer[6], PATH_MAX-1-14);
                    fname[PATH_MAX-1]=0;
                    if(mkdir(fname, 0777)!=0)
                    {
                        retval=FS_RV_ERROR_UNKNOWN;
                    }
                    else
                    {
                        retval=FS_RV_SUCCESS;
                    }
                    break;
                case FSCMD_GET_PATH_ATTRIBUTES:
                    pathlen = readShort(4, gfxcmdbuffer);
                    if(pathlen>12000) 
                    {
                        retval=FS_RV_ERROR_UNKNOWN;
                        break;
                    }
                    gfxcmdbuffer[6+pathlen]=0;
                    strcpy(fname, "/tmp/external/");
                    strncpy(fname+14, (char *) &gfxcmdbuffer[6], PATH_MAX-1-14);
                    fname[PATH_MAX-1]=0;
                    retval=0;
                    if(lstat(fname, &statbuf)==0)
                    {
                        if(S_ISREG(statbuf.st_mode)) retval|=FS_PATH_FILE;
                        if(S_ISDIR(statbuf.st_mode)) retval|=FS_PATH_DIRECTORY;
                    }
                    else
                    {
                        fprintf(stderr, "error on stat %d\n",errno);
                    }
    #ifdef DEBUGCLIENTFS
                    fprintf(stderr, "get path attrib on %s : %lld\n",fname, retval);
    #endif
                    break;
                case FSCMD_GET_FILE_SIZE:
                    pathlen = readShort(4, gfxcmdbuffer);
                    if(pathlen>12000) 
                    {
                        retval=0;
                        break;
                    }
                    gfxcmdbuffer[6+pathlen]=0;
                    retlen=8;
                    retval=0;
                    strcpy(fname, "/tmp/external/");
                    strncpy(fname+14, (char *) &gfxcmdbuffer[6], PATH_MAX-1-14);
                    fname[PATH_MAX-1]=0;
                    if(lstat(fname, &statbuf)==0)
                    {
                        retval=statbuf.st_size;
                    }
                    break;
                case FSCMD_GET_PATH_MODIFIED_TIME:
                    pathlen = readShort(4, gfxcmdbuffer);
                    if(pathlen>12000) 
                    {
                        retval=0;
                        break;
                    }
                    gfxcmdbuffer[6+pathlen]=0;
                    retlen=8;
                    retval=0;
                    strcpy(fname, "/tmp/external/");
                    strncpy(fname+14, (char *) &gfxcmdbuffer[6], PATH_MAX-1-14);
                    fname[PATH_MAX-1]=0;
                    if(lstat(fname, &statbuf)==0)
                    {
                        retval=statbuf.st_mtime;
                    }
                    break;
                case FSCMD_DIR_LIST:
                    {
                        DIR *dirhandle;
                        struct dirent* direntry;
                        retlen=2;
                        pathlen = readShort(4, gfxcmdbuffer);
                        if(pathlen>12000) 
                        {
                            retval=0;
                            break;
                        }
                        gfxcmdbuffer[6+pathlen]=0;
                        retval=0;
    #ifdef DEBUGCLIENTFS
                        fprintf(stderr, "open dir %s\n",&gfxcmdbuffer[6]);
    #endif
                        strcpy(fname, "/tmp/external/");
                        strncpy(fname+14, (char *) &gfxcmdbuffer[6], PATH_MAX-1-14);
                        fname[PATH_MAX-1]=0;

                        if(dirhandle = opendir(fname))
                        {
                            short shortret=0xFFFF;
                            hasret=0;
                            ACL_LockMutex(UISendMutex);
                            SendUIReply(uisockfd, 2, &shortret,2);
                            while((direntry=readdir(dirhandle))!=NULL)
                            {
                                unsigned short nlen;
    #ifdef DEBUGCLIENTFS
                        fprintf(stderr, "dir entry %s\n",direntry->d_name);
    #endif
                                if(strcmp(direntry->d_name,".")!=0 &&
                                    strcmp(direntry->d_name,"..")!=0)
                                {
                                    nlen=strlen(direntry->d_name);
                                    nlen=htons(nlen);
                                    send(uisockfd,&nlen,2,MSG_NOSIGNAL);
                                    send(uisockfd,direntry->d_name,strlen(direntry->d_name),MSG_NOSIGNAL);
                                }
                            }
                            retval=0;
                            send(uisockfd,&retval,2,MSG_NOSIGNAL);
                            ACL_UnlockMutex(UISendMutex);
                            closedir(dirhandle);
                        }
                        retval=0;
                        break;
                    }
                case FSCMD_LIST_ROOTS:
                    {
                        unsigned short nlen;
                        unsigned char rootmsg[4+14]; // /tmp/external/
                        retlen=0;
                        ACL_LockMutex(UISendMutex);
                        rootmsg[0]=0;
                        rootmsg[1]=1;
                        rootmsg[2]=0;
                        rootmsg[3]=14;
                        memcpy(&rootmsg[4],"/tmp/external/", 14);
                        SendUIReply(uisockfd, 2, rootmsg,18);
                        ACL_UnlockMutex(UISendMutex);
                        retval=0;
                        hasret=0;
                        break;
                    }
                case FSCMD_DOWNLOAD_FILE:
                case FSCMD_UPLOAD_FILE:
                    {
                        int secureID;
                        long long fileOffset, fileSize;
                        char *filename;
                        secureID = readInt(4, gfxcmdbuffer);
                        fileOffset = readLongLong(8, gfxcmdbuffer);
                        fileSize = readLongLong(16, gfxcmdbuffer);
                        pathlen = readShort(24, gfxcmdbuffer);
                        if(pathlen>12000) 
                        {
                            retval=FS_RV_ERROR_UNKNOWN;
                            break;
                        }
                        gfxcmdbuffer[26+pathlen]=0;
                        memcpy(&gfxcmdbuffer[26-14], "/tmp/external/", 14);
                        filename=(char *) &gfxcmdbuffer[26-14];
                        if(fscommand==FSCMD_DOWNLOAD_FILE)
                        {
                            FILE *dfile;
                            dfile = fopen(filename,"w+b");
                            retval=FS_RV_ERROR_UNKNOWN;
                            if(dfile!=NULL)
                            {
                                if(fseeko(dfile, fileOffset, SEEK_SET)==0)
                                {
                                    TransferStream *ts=malloc(sizeof(TransferStream));
                                    if(ts!=NULL)
                                    {
                                        ts->dfile=dfile;
                                        ts->size=fileSize;
                                        ts->secureID=secureID;
                                        ACL_LockMutex(doneThreadsMutex);
                                        ts->thread=ACL_CreateThread(FSDownloadThread, ts);
                                        fprintf(stderr, "Created thread %x\n",ts->thread);
                                        ACL_UnlockMutex(doneThreadsMutex);
                                        retval=FS_RV_SUCCESS;
                                    }
                                }
                            }
                        }
                        else if(fscommand==FSCMD_UPLOAD_FILE)
                        {
                            FILE *dfile;
                            dfile = fopen(filename,"rb");
                            if(dfile!=NULL)
                            {
                                if(fseeko(dfile, fileOffset, SEEK_SET)==0)
                                {
                                    TransferStream *ts=malloc(sizeof(TransferStream));
                                    if(ts!=NULL)
                                    {
                                        ts->dfile=dfile;
                                        ts->size=fileSize;
                                        ts->secureID=secureID;
                                        ACL_LockMutex(doneThreadsMutex);
                                        ts->thread=ACL_CreateThread(FSUploadThread, ts);
                                        fprintf(stderr, "Created thread %x\n",ts->thread);
                                        ACL_UnlockMutex(doneThreadsMutex);
                                        retval=FS_RV_SUCCESS;
                                    }
                                }
                            }
                        }
                        retval=0;
                        break;
                    }
            }
    #ifdef DEBUGCLIENTFS
            fprintf(stderr, "fscommand hasret %d len %d\n",hasret, retlen);
    #endif
            if(hasret)
            {
                if(retlen==2)
                {
                    *((unsigned short *)&retval) = htons(retval);
                }
                else if(retlen==4)
                {
                    *((unsigned int *)&retval) = htonl(retval);
                }
                else if(retlen==8)
                {
                    unsigned long long retvaltmp=retval;
                    ((unsigned int *)&retval)[0] = htonl(retvaltmp>>32);
                    ((unsigned int *)&retval)[1] = htonl(retvaltmp&0xFFFFFFFF);
                }
                ACL_LockMutex(UISendMutex);
                retval=SendUIReply(uisockfd, 2, &retval, retlen);
                ACL_UnlockMutex(UISendMutex);
                return retval;
            }
        }
#endif
    }
    return retval;
}

int SendUISize(int sockfd, int width, int height)
{
    ReplyPacket packreply;
    unsigned int timestamp=0;
    unsigned int ID=0;
    int len=8;

    packreply.type=192;
    packreply.size0=(len>>16)&0xFF;
    packreply.size1=(len>>8)&0xFF;
    packreply.size2=(len>>0)&0xFF;

    // make sure this is in network byte order...
    packreply.timestamp=htonl(timestamp);
    packreply.ID=htonl(ID);
    packreply.pad=htonl(0);
    width=htonl(width);
    height=htonl(height);

    ACL_LockMutex(UISendMutex);
    send(uisockfd,&packreply,16,MSG_NOSIGNAL);
#ifdef EM86
    if(encryptenabled)
    {
        int olen,tlen;
        int k;
        int len=8;
        if(len>4000)
        {
            fprintf(stderr, "Too long reply for encryption\n");
            return -1;
        }
        // verify this is right for 2 updates
        EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, (unsigned char *) &width, 4);
        EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, (unsigned char *) &height, 4);
        if(EVP_EncryptFinal(&ctx, encryptbuffer + olen, &tlen) != 1)
        {
            fprintf(stderr, "EVP_EncryptFinal\n");
            return -1;
        }
        olen += tlen;
        send(uisockfd, encryptbuffer, olen, MSG_NOSIGNAL);
        if(len&0x7) EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, encryptbuffer, 8-(len&0x7));
    }
    else
#endif
    {
        send(uisockfd,&width,4,MSG_NOSIGNAL);
        send(uisockfd,&height,4,MSG_NOSIGNAL);
    }
    ACL_UnlockMutex(UISendMutex);
    return 0;
}

// Status 0 is no error
int SendImageLoaded(int sockfd, int handle, int status)
{
    ReplyPacket packreply;
    unsigned int timestamp=0;
    unsigned int ID=0;
    int len=8;

    fprintf(stderr, "sending image loaded %X %d\n",handle, status);
    packreply.type=228;
    packreply.size0=(len>>16)&0xFF;
    packreply.size1=(len>>8)&0xFF;
    packreply.size2=(len>>0)&0xFF;

    // make sure this is in network byte order...
    packreply.timestamp=htonl(timestamp);
    packreply.ID=htonl(ID);
    packreply.pad=htonl(0);
    handle=htonl(handle);
    status=htonl(status);

    ACL_LockMutex(UISendMutex);
    send(uisockfd,&packreply,16,MSG_NOSIGNAL);
#ifdef EM86
    if(encryptenabled)
    {
        int olen,tlen;
        int k;
        int len=8;
        if(len>4000)
        {
            fprintf(stderr, "Too long reply for encryption\n");
            return -1;
        }
        // verify this is right for 2 updates
        EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, (unsigned char *) &handle, 4);
        EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, (unsigned char *) &status, 4);
        if(EVP_EncryptFinal(&ctx, encryptbuffer + olen, &tlen) != 1)
        {
            fprintf(stderr, "EVP_EncryptFinal\n");
            return -1;
        }
        olen += tlen;
        send(uisockfd, encryptbuffer, olen, MSG_NOSIGNAL);
        if(len&0x7) EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, encryptbuffer, 8-(len&0x7));
    }
    else
#endif
    {
        send(uisockfd,&handle,4,MSG_NOSIGNAL);
        send(uisockfd,&status,4,MSG_NOSIGNAL);
    }
    ACL_UnlockMutex(UISendMutex);
    return 0;
}

int SendInsertRemoveUSB(int sockfd, int type, char *name)
{
    ReplyPacket packreply;
    unsigned int timestamp=0;
    unsigned int ID=0;
    short namelen=strlen(name);
    short desclen=0;
    int len=4+strlen(name);

    packreply.type= (type==0) ? 202 : 203;
    packreply.size0=(len>>16)&0xFF;
    packreply.size1=(len>>8)&0xFF;
    packreply.size2=(len>>0)&0xFF;
    fprintf(stderr,"Sending event %d to socket %d len %d\n",packreply.type, sockfd, len);
    if(encryptenabled)
    {
        fprintf(stderr, "Waiting to do USB after encryption\n");
        return 0;
    }
    // make sure this is in network byte order...
    packreply.timestamp=htonl(timestamp);
    packreply.ID=htonl(ID);
    packreply.pad=htonl(0);
    ACL_LockMutex(UISendMutex);
    send(uisockfd,&packreply,16,MSG_NOSIGNAL);
    namelen=htons(namelen);
    send(uisockfd,&namelen,2,MSG_NOSIGNAL);
    send(uisockfd, name, strlen(name), MSG_NOSIGNAL);
    send(uisockfd,&desclen,2, MSG_NOSIGNAL); // Don't send any desc or the server breaks (6.5.9)
    ACL_UnlockMutex(UISendMutex);
    return 0;

}

int SendHDMIUpdate(int sockfd)
{
    ReplyPacket packreply;
    unsigned int timestamp=0;
    unsigned int ID=0;
    int len=0;

    packreply.type=224;
    packreply.size0=(len>>16)&0xFF;
    packreply.size1=(len>>8)&0xFF;
    packreply.size2=(len>>0)&0xFF;

    // make sure this is in network byte order...
    packreply.timestamp=htonl(timestamp);
    packreply.ID=htonl(ID);
    packreply.pad=htonl(0);

    ACL_LockMutex(UISendMutex);
    send(uisockfd,&packreply,16,MSG_NOSIGNAL);
    ACL_UnlockMutex(UISendMutex);
    return 0;
}

// PTS are in 45000 rate
int SendSubpictureUpdate(int flag, int pts, int duration, unsigned char *data, int datalen)
{
    ReplyPacket packreply;
    unsigned int timestamp=0;
    unsigned int ID=0;
    int len=14+datalen;
    char header[14];

    if(subtitleenable==0) return 0;

    packreply.type=225;
    packreply.size0=(len>>16)&0xFF;
    packreply.size1=(len>>8)&0xFF;
    packreply.size2=(len>>0)&0xFF;

    // make sure this is in network byte order...
    packreply.timestamp=htonl(timestamp);
    packreply.ID=htonl(ID);
    packreply.pad=htonl(0);

    header[0]=(flag>>24)&0xFF;
    header[1]=(flag>>16)&0xFF;
    header[2]=(flag>>8)&0xFF;
    header[3]=(flag>>0)&0xFF;
    header[4]=(pts>>24)&0xFF;
    header[5]=(pts>>16)&0xFF;
    header[6]=(pts>>8)&0xFF;
    header[7]=(pts>>0)&0xFF;
    header[8]=(duration>>24)&0xFF;
    header[9]=(duration>>16)&0xFF;
    header[10]=(duration>>8)&0xFF;
    header[11]=(duration>>0)&0xFF;
    header[12]=(datalen>>8)&0xFF;
    header[13]=(datalen>>0)&0xFF;
    ACL_LockMutex(UISendMutex);
    send(uisockfd,&packreply,16,MSG_NOSIGNAL);
    send(uisockfd,&header[0],14,MSG_NOSIGNAL);
    if(datalen>0)
        send(uisockfd, data, datalen, MSG_NOSIGNAL);
    ACL_UnlockMutex(UISendMutex);
    return 0;
}

struct USBPartition
{
    char name[256];
    int detected;
    struct USBPartition *next;
} ;

struct USBPartition *USBPartitions=NULL;

// Not very efficient but we shouldn't have many partitions
// Doing many malloc was slower... could be faster on a system with mmu though
struct USBPartition *FindUSBPartitions()
{
    struct USBPartition *ufirstpart=NULL;
    struct USBPartition *upart=NULL;
    char filename[PATH_MAX];
    struct stat sbuf;
    int count=0;
    DIR *dirhandle;
    struct dirent* direntry;
    if(dirhandle = opendir("/tmp/external"))
    {
        while((direntry=readdir(dirhandle))!=NULL)
        {
            unsigned short nlen;
#ifdef DEBUGCLIENTFS
            fprintf(stderr, "dir entry %s\n",direntry->d_name);
#endif
            if(strcmp(direntry->d_name,".")!=0 &&
                strcmp(direntry->d_name,"..")!=0)
            {
                count+=1;
                if(!ufirstpart) // First one
                {
                    ufirstpart=upart=malloc(sizeof(struct USBPartition));
                }
                else
                {
                    upart->next=malloc(sizeof(struct USBPartition));
                    upart=upart->next;
                }
                strncpy(upart->name, direntry->d_name, 255);
                upart->name[255]=0;
                upart->next=NULL;
            }
        }
        closedir(dirhandle);
    }
    // For now limit search to 16 host...
    if(count!=0) fprintf(stderr, "Count is %d\n",count);
    return ufirstpart;
}

int DetectUSB(int sockfd)
{
    // Scan /dev/scsi/* for partX
    struct USBPartition *detectedPartitions = FindUSBPartitions();
    struct USBPartition *currentPartitions = USBPartitions;
    struct USBPartition *searchParts;
    struct USBPartition *prevPart=NULL;
    struct USBPartition *foundParts=detectedPartitions;

    // Clear detected flag of all the current partitions
    searchParts = USBPartitions;
    while(searchParts!=NULL)
    {
        searchParts->detected=0;
        searchParts=searchParts->next;
    }

/*    fprintf(stderr, "Current partitions :\n");
    currentPartitions = USBPartitions;
    while(currentPartitions!=NULL)
    {
        fprintf(stderr, "Current partition %X at %d/%d/%d/%d/%d\n", currentPartitions,
            currentPartitions->host, currentPartitions->bus,
            currentPartitions->target, currentPartitions->lun,
            currentPartitions->partition);
        currentPartitions=currentPartitions->next;
    }*/

    // look into current USBDevice to see if there is a match for each
    while(detectedPartitions!=NULL)
    {
        struct USBPartition *oldPart=detectedPartitions;
/*        fprintf(stderr, "Found partition %X at %d/%d/%d/%d/%d\n", detectedPartitions,
            detectedPartitions->host, detectedPartitions->bus,
            detectedPartitions->target, detectedPartitions->lun,
            detectedPartitions->partition);*/
        // Mark our partition as a new one
        detectedPartitions->detected=1;
        // See if it is in our current devices
        searchParts = USBPartitions;
        while(searchParts!=NULL)
        {
            if(strcmp(searchParts->name,detectedPartitions->name)==0)
            {
                searchParts->detected=1;
                // Remove the partition from our detectedPartition list
                // Mark our partition as a old one
                detectedPartitions->detected=0;
            }
            searchParts=searchParts->next;
        }
        // Go to next partition
        detectedPartitions=detectedPartitions->next;
    }

    // Go through the current partitions and signal the removed ones
    searchParts = USBPartitions;
    prevPart=NULL;
    while(searchParts!=NULL)
    {
        // This partition is not there anymore
        if(searchParts->detected==0)
        {
            fprintf(stderr, "Partition %X at %s has been removed\n", 
                searchParts,
                searchParts->name);
            SendInsertRemoveUSB(sockfd, 1, searchParts->name);
            if(prevPart!=NULL)
            {
                prevPart->next=searchParts->next;
            }
            else
            {
                USBPartitions = searchParts->next;
            }
            currentPartitions=searchParts;
            searchParts=searchParts->next;
            free(currentPartitions);
        }
        else
        {
            prevPart=searchParts;
            searchParts=searchParts->next;
        }
    }
    if(prevPart) prevPart->next=NULL;

    // Go through the detected partitions and find the new ones
    prevPart = USBPartitions;
    // Find last partition in our main list
    while(prevPart!=NULL && prevPart->next!=NULL)
    {
        prevPart=prevPart->next;
    }

    searchParts = foundParts;
    while(searchParts!=NULL)
    {
        // This partition is new
        if(searchParts->detected==1)
        {
            fprintf(stderr, "Partition %X at %s has been added\n",
                searchParts,
                searchParts->name);
            SendInsertRemoveUSB(sockfd, 0, searchParts->name);
            if(prevPart!=NULL)
            {
                prevPart->next=searchParts;
                prevPart=searchParts;
            }
            else
            {
                USBPartitions = searchParts;
                prevPart=searchParts;
            }
            searchParts=searchParts->next;
            prevPart->next=NULL;
        }
        else
        {
            currentPartitions=searchParts;
            searchParts=searchParts->next;
            free(currentPartitions);
        }
    }
}

int GFXThread(void *data)
{
    ServerConnection *sc = NULL;
    long long updatetime;
    int usbcount=0;
    fprintf(stderr, "Starting GFX thread\n");
    printf("Trying to connect to server at %s:%d\n",gfxservername,gfxport);
    while(sc==NULL)
    {
        sc = EstablishServerConnection(gfxservername, gfxport, 0);
        if(sc==NULL)
        {
            fprintf(stderr, "couldn't connect to gfx/input server, retrying in 2 secs.\n");
            ACL_Delay(2000000);
        }
        if(ConnectionError!=0) return -1;
    }

    #ifdef DEBUGCLIENT
    fprintf(stderr, "Connected to gfx server\n");
    #endif

    updatetime=get_timebase()+100000LL;

    uisockfd=sc->sockfd;
#ifdef EM86
    needuisize=1;
#endif
    while(1)
    {
        if(needuisize)
        {
            fprintf(stderr,"Sending ui size %dX%d\n",uiwidth, uiheight);
            fflush(stderr);
            SendUISize(sc->sockfd, uiwidth, uiheight);
            needuisize=0;
        }
        if(needhdmi)
        {
            fprintf(stderr,"Sending hdmi updated packet\n");
            fflush(stderr);
            SendHDMIUpdate(sc->sockfd);
            needhdmi=0;
        }
        if(updatetime<=get_timebase())
        {
            updatetime=get_timebase()+100000LL;
            ACL_LockMutex(doneThreadsMutex);
            while(doneThreadsCount)
            {
                fprintf(stderr,"Trying to join with thread %d\n", doneThreads[doneThreadsCount-1]);
                ACL_ThreadJoin(doneThreads[doneThreadsCount-1]);
                doneThreadsCount--;
            }
            ACL_UnlockMutex(doneThreadsMutex);
            // Maybe look at adding a maximum skipped count?
            if(ACL_TryLockMutex(MediaCmdMutex)==0)
            {
                Output_UpdateHDMI();
                ACL_UnlockMutex(MediaCmdMutex);
            }
            if(usbcount++==50) // 5.0 seconds
            {
                //DetectUSB(sc->sockfd);
                usbcount=0;
            }
        }
        if(ProcessGFXCommand(sc->sockfd)<0)
        {
            // TODO: Should try to reconnect
            fprintf(stderr, "Error processing GFX Command\n");
            break;
        }
        if(ConnectionError!=0)
        {
            fprintf(stderr, "GFX Connection error, need to exit\n");
            break;
        }
    }

    ConnectionError=1;
    return -1;
}

#ifdef TESTMULTICAST

multicastReceiver * createMulticastReceiver(char *destip, int port)
{
    struct ip_mreq mreq;
    multicastReceiver *mr;
    mr = (multicastReceiver *) malloc(sizeof(multicastReceiver));
    if(mr==NULL) return NULL;

    memset(&mr->sc, 0, sizeof(ServerConnection));
    memset(&mr->client_addr, 0, sizeof(struct sockaddr_in));
    mr->client_len=0;

    mr->sc.server_addr.sin_family = AF_INET;
    mr->sc.server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    mr->sc.server_addr.sin_port = htons(port);

    if((mr->sc.sockfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
    {
        #ifdef DEBUGCLIENT
        perror("socket");
        #endif
        return NULL;
    }

    if(bind(mr->sc.sockfd, (struct sockaddr *) &mr->sc.server_addr,
        sizeof(struct sockaddr)) < 0)
    {
        #ifdef DEBUGCLIENT
        perror("bind");
        #endif
        return NULL;
    }

    mreq.imr_multiaddr.s_addr = inet_addr(destip);
    mreq.imr_interface.s_addr = INADDR_ANY;
    setsockopt(mr->sc.sockfd, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq));

    return mr;
}

int recvMulticast(multicastReceiver *mr, char *databuf, int datalen)
{
    int n = recvfrom(mr->sc.sockfd,databuf,datalen,0,(struct sockaddr *)(&mr->client_addr),
        (unsigned int *) &mr->client_len);
    return n;
}

closeMulticastReceiver(multicastReceiver *mr)
{
    if(mr!=NULL)
    {
        close(mr->sc.sockfd); 
        free(mr);
    }
}

#endif

int ProcessMediaCommand(int sockfd, char *cmdbuffer)
{
    unsigned char cmd[4];
    unsigned int command,len;
    int hasret=0;
    int retval;
    unsigned char retbuf[4];
    fd_set rfds;
    struct timeval tv;
    int maxfd=0;

    FD_ZERO(&rfds);
    FD_SET(sockfd, &rfds);
    maxfd=sockfd;
#ifdef TESTMULTICAST
    if(mr!=NULL)
    {
        FD_SET(mr->sc.sockfd, &rfds);
        if(mr->sc.sockfd>maxfd) maxfd=mr->sc.sockfd;
    }
#endif
    // 0.001 second
    tv.tv_sec = 0;
    tv.tv_usec = 1000;

    retval = select(maxfd+1, &rfds, NULL, NULL, &tv);

    if (retval == -1)
        return -1;

#ifdef EM86
    // TODO: verify if that does anything bad on mvp...
    if (retval == 0 && (pushmode==0 || pushmode==2))
    {
        ACL_LockMutex(MediaCmdMutex);
        Media_PushBuffer(0, 0, 0);
        ACL_UnlockMutex(MediaCmdMutex);
        return 0;
    }
#endif

#ifdef TESTMULTICAST
    if(mr!=NULL)
    {
        if(FD_ISSET(mr->sc.sockfd, &rfds))
        {
            FD_CLR(mr->sc.sockfd, &rfds);
            if(recvMulticast(mr, &cmdbuffer[8], 2048)!=2048)
            {
                exit(1);
            }
            command=23; //push buffer
            cmdbuffer[0]=0;
            cmdbuffer[1]=0;
            cmdbuffer[2]=0x00; // 0k
            cmdbuffer[3]=0x00;
            cmdbuffer[4]=0;
            cmdbuffer[5]=0;
            cmdbuffer[6]=0;
            cmdbuffer[7]=0;
            retval = ExecuteMediaCommand(command, 8, (unsigned char *) cmdbuffer, &hasret, mediasockfd);
            if(retval>=2048)
            {
                cmdbuffer[0]=0;
                cmdbuffer[1]=0;
                cmdbuffer[2]=0x8; // 2k
                cmdbuffer[3]=0x00;
                cmdbuffer[4]=0;
                cmdbuffer[5]=0;
                cmdbuffer[6]=0;
                cmdbuffer[7]=0;
                retval = ExecuteMediaCommand(command, 2048+8, (unsigned char *) cmdbuffer, &hasret, mediasockfd);
            }
        }
    }
#endif
    if(FD_ISSET(sockfd, &rfds))
    {
        FD_CLR(sockfd, &rfds);
        ACL_LockMutex(MediaCmdMutex);
        if(fullrecv(sockfd, &cmd, 4)<4)
        {
            ACL_UnlockMutex(MediaCmdMutex);
            return -1;
        }

        command=cmd[0];
        len=cmd[1]<<16|cmd[2]<<8|cmd[3];
        if(command!=MEDIACMD_PUSHBUFFER && len>65536)
        {
            fprintf(stderr, "Media command is too long for this client\n");
            // TODO: read all the bytes?
            ACL_UnlockMutex(MediaCmdMutex);
            return -2;
        }

        #ifdef DirectPush
        if(command!=MEDIACMD_PUSHBUFFER)
        #endif
        {
            if(fullrecv(sockfd, cmdbuffer, len)<len)
            {
                ACL_UnlockMutex(MediaCmdMutex);
                return -3;
            }
        }
        retval = ExecuteMediaCommand(command, len, (unsigned char *) cmdbuffer, &hasret, mediasockfd);
        ACL_UnlockMutex(MediaCmdMutex);

        if(hasret)
        {
            retbuf[0]=retval>>24;
            retbuf[1]=retval>>16;
            retbuf[2]=retval>>8;
            retbuf[3]=retval>>0;
            send(sockfd,&retbuf[0],4,MSG_NOSIGNAL);
            return 0;
        }
        return retval;
    }
    return 0;
}

int InputThread(void *data)
{
    inputhandle = InputInit();

    while(ConnectionError==0)
    {
        ReplyPacket packevent;

        unsigned char cmd[4];
        unsigned char reply[4];
        unsigned int event;
        unsigned int len;
        unsigned int timestamp=0;
        unsigned int ID=0;
        unsigned int Pad=0;
        unsigned char *data;
        int status = ReadInput(inputhandle, &event, &len, &data);
        if(status == -1)
        {
            break;
        }
        if(status == 0) // No input was read (discarded events)
        {
            continue;
        }
        #ifdef DEBUGCLIENT2
        fprintf(stderr, "Got input event\n");
        #endif
        packevent.type=event;
        packevent.size0=(len>>16)&0xFF;
        packevent.size1=(len>>8)&0xFF;
        packevent.size2=(len>>0)&0xFF;
        // make sure this is in network byte order...
        packevent.timestamp=htonl(timestamp);
        packevent.ID=htonl(ID);
        packevent.pad=htonl(0);

        ACL_LockMutex(UISendMutex);
        send(uisockfd,&packevent,16,MSG_NOSIGNAL);
#ifdef EM86
        if(encryptenabled)
        {
            int olen,tlen;
            if(len>4000)
            {
                fprintf(stderr, "Too long reply for encryption\n");
                return -1;
            }
            EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, data, len);
            if(EVP_EncryptFinal(&ctx, encryptbuffer + olen, &tlen) != 1)
            {
                fprintf(stderr, "EVP_EncryptFinal\n");
                return -1;
            }
            olen += tlen;
            send(uisockfd, encryptbuffer, olen, MSG_NOSIGNAL);
            if(len&0x7) EVP_EncryptUpdate(&ctx, encryptbuffer, &olen, encryptbuffer, 8-(len&0x7));
        }
        else
#endif
        {
            send(uisockfd,data,len,MSG_NOSIGNAL);
        }
        ACL_UnlockMutex(UISendMutex);

        ReleaseInput(inputhandle); // Release the event data
    }

    ReleaseInput(inputhandle); // Release the event data
    CloseInput(inputhandle);
    ConnectionError=1;
    fprintf(stderr, "Input Thread is done\n");
    return -1;
}

int needreconnect=0;
int GFX_ReconnnectMedia()
{
    fprintf(stderr, "Got GFX_ReconnnectMedia call\n");
    needreconnect=1;
    return 0;
}

int MediaThread(void *data)
{
    char *cmdbuffer = (char *) malloc(65536);
    if(cmdbuffer==NULL)
    {
        fprintf(stderr, "Couldn't allocate cmd buffer\n");
        ConnectionError=1;
        return -1;
    }

    while(ConnectionError==0)
    {
        ServerConnection *sc = NULL;
        while(sc==NULL)
        {
            ACL_LockMutex(MediaCmdMutex);
            sc = EstablishServerConnection(gfxservername, gfxport, 1);
            ACL_UnlockMutex(MediaCmdMutex);
            if(sc==NULL)
            {
                fprintf(stderr, "couldn't connect to media server, retrying in 1 secs.\n");
                ACL_Delay(1000000);
            }
            if(ConnectionError!=0) return -1;
        }

        #ifdef DEBUGCLIENT
        fprintf(stderr, "Connected to media server\n");
        #endif

        mediasockfd=sc->sockfd;
#ifdef TESTMULTICAST
        // TEST TEST TEST
//        mr=createMulticastReceiver("224.1.1.1", 31050);
#endif
        while(1)
        {
            if(needreconnect)
            {
                fprintf(stderr, "Clearing GFX_ReconnnectMedia state\n");
                needreconnect=0;
                break;
            }
            if(ProcessMediaCommand(sc->sockfd, cmdbuffer)<0)
            {
                // TODO: Should try to reconnect
                fprintf(stderr, "Error processing Media Command\n");
                break;
            }
            if(ConnectionError!=0)
            {
                fprintf(stderr, "Media Connection error, need to exit\n");
                break;
            }
            if(mediaevent!=0)
            {
                ReplyPacket packreply;
                unsigned int timestamp=0;
                unsigned int ID=0;
                fprintf(stderr, "Sending mediaevent %d\n",mediaevent);
                packreply.type=mediaevent;
                packreply.size0=(mediaeventdatalen>>16)&0xFF;
                packreply.size1=(mediaeventdatalen>>8)&0xFF;
                packreply.size2=(mediaeventdatalen>>0)&0xFF;

                // make sure this is in network byte order...
                packreply.timestamp=htonl(timestamp);
                packreply.ID=htonl(ID);
                packreply.pad=htonl(0);
                ACL_LockMutex(UISendMutex);
                send(uisockfd, &packreply, 16, MSG_NOSIGNAL);
                if(mediaeventdatalen!=0)
                {
                    send(uisockfd, mediaeventdata, mediaeventdatalen, MSG_NOSIGNAL);
                }
                ACL_UnlockMutex(UISendMutex);
                mediaevent=0;
                mediaeventdatalen=0;
                if(mediaeventdata) free(mediaeventdata);
                mediaeventdata=NULL;
            }
        }
        // In case we had an error on the socket
        ACL_LockMutex(MediaCmdMutex);
        Media_deinit();
        ACL_UnlockMutex(MediaCmdMutex);
        DropServerConnection(sc);
    }

    ConnectionError=1;
    //CloseInput(inputhandle);
    fprintf(stderr, "Media Thread is done\n");
    return -1;
}

unsigned int FindServer(char *servername)
{
    ServerConnection sc;
    struct sockaddr_in dest_addr;      /* destination address */
    struct sockaddr_in client_addr;    /* client's address */
    socklen_t client_len;
    int flag=1;
    struct timeval timeout;
    int opt=1;    
    char msg[10] = { 'S', 'T', 'V', 1, 0, 0, 0, 0, 0, 0}; // Query
    char inmsg[1024];

    memset(&sc, 0, sizeof(ServerConnection));
    memset(&dest_addr, 0, sizeof(struct sockaddr_in));

    // Accept input from all interfaces on a port that will be assigned
    sc.server_addr.sin_family = AF_INET;
    sc.server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    sc.server_addr.sin_port = htons(0);

    if((sc.sockfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
    {
        #ifdef DEBUGCLIENT
        perror("socket");
        #endif
        return 0;
    }

    if(bind(sc.sockfd, (struct sockaddr *) &sc.server_addr,
        sizeof(struct sockaddr)) < 0)
    {
        #ifdef DEBUGCLIENT
        perror("bind");
        #endif
        return 0;
    }

    dest_addr.sin_family = AF_INET;
    dest_addr.sin_addr.s_addr = inet_addr("255.255.255.255");
    dest_addr.sin_port = htons(31100);
    timeout.tv_sec=1;
    timeout.tv_usec=0;
    setsockopt(sc.sockfd, SOL_SOCKET, SO_RCVTIMEO, (void *)&timeout, 
        sizeof(timeout));
    setsockopt(sc.sockfd, SOL_SOCKET, SO_BROADCAST, &opt, sizeof(int));
    GetMACAddress((unsigned char *)&msg[4]);

    while(1)
    {
        int n;
        client_len = sizeof(client_addr);
        n = sendto(sc.sockfd,msg,10,0,(struct sockaddr *)(&dest_addr),
            sizeof(dest_addr));
        if(n<0)
        {
            #ifdef DEBUGCLIENT
            perror("Error sending message\n");
            #endif
            return 0;
        }


        n=-1;
        while(1)
        {
            n = recvfrom(sc.sockfd,inmsg,1024,0,(struct sockaddr *)(&client_addr),
                &client_len);
            fprintf(stderr, "n : %d\n",n);
            if(n<0)
            {
                if(errno==EAGAIN) break;
                #ifdef DEBUGCLIENT
                perror("Error receiving message\n");
                #endif
                return 0;
            }
            if(n<4) continue;

            if(inmsg[0]=='S' && inmsg[1]=='T' && inmsg[2]=='V' && inmsg[3]==2 )
            {
                int ip;
                fprintf(stderr, "%X\n",ntohl(client_addr.sin_addr.s_addr));
                close(sc.sockfd);
                ip=ntohl(client_addr.sin_addr.s_addr);
                sprintf(servername,"%d.%d.%d.%d",(ip>>24)&0xFF,(ip>>16)&0xFF,(ip>>8)&0xFF,(ip>>0)&0xFF);
                return 1;
            }
        }
    }
}

void UpdateProperties()
{
    FILE *vfile;
    vfile=fopen("/version","rb");
    if(vfile!=NULL)
    {
        fread(firmwareversion, 1, 127, vfile);
        fclose(vfile);
    }
#ifdef EM8634
    if(getenv("CACHED_AUTH")!=NULL)
    {
        strncpy(cachedauth, getenv("CACHED_AUTH"), 32);
        cachedauth[32]=0;
    }
    else
    {
        cachedauth[0]=0;
    }
    if(getenv("SERVER_NAME")!=NULL)
    {
        strncpy(configservername, getenv("SERVER_NAME"), 1023);
        configservername[1023]=0;
    }
    else
    {
        configservername[0]=0;
    }
#endif
}

static void sig_handler(int signum)
{
    fprintf(stderr, "Received signal %d\n", signum);
    ConnectionError=1;
}

static void sig_handler_verybad(int signum)
{
    fprintf(stderr, "Received signal %d\n", signum);
    ConnectionError=1;
    sync();
    reboot(RB_AUTOBOOT);
}

static int InstallSignals()
{
    int signals[] = { SIGHUP, SIGINT, SIGQUIT, SIGTERM, SIGILL, SIGBUS, SIGFPE, SIGSEGV };
    int i;

    // We don't want sigpipe
    signal(SIGPIPE,SIG_IGN);

    for(i=0;i<8;i++)
    {
        if(i<4)
        {
            if(signal(signals[i], sig_handler) == SIG_ERR) fprintf(stderr, "Couldn't install signal handler %d\n",signals[i]);
        }
        else if(getenv("REBOOT")!=NULL)
        {
            if(signal(signals[i], sig_handler_verybad) == SIG_ERR) fprintf(stderr, "Couldn't install signal handler %d\n",signals[i]);
        }
    }
}

int main(int argc, char **argv)
{
    char servername[16]; // 3.3.3.3 total 12+3+1
    ACL_Thread *gfx_thread=NULL;
    ACL_Thread *media_thread=NULL;
    ACL_Thread *input_thread=NULL;
    #ifdef DEBUGCLIENT
    fprintf(stderr,"Starting SageTVMini Client\n");
    #endif
    ExitValue=0;

    // Search for our server
    if(argc<2)
    {
        if(!FindServer(servername))
        {
            fprintf(stderr, "Couldn't find server\n");
            exit(0);
        }
        printf("%s",servername);
        exit(1);
    }
    fprintf(stderr, "Connecting to %s\n",argv[1]);
    UpdateProperties();

    // To show the logo while we search...
    UISendMutex = ACL_CreateMutex();
    MediaCmdMutex = ACL_CreateMutex();
    doneThreadsMutex = ACL_CreateMutex();
    // TODO
    //ACL_LockMutex(UISendMutex); // Lock it until the connection is established
    {
        char *portstr;
        gfxservername=(char *) argv[1];
        gfxport=31099;
        if((portstr=strstr(gfxservername,":"))!=NULL)
        {
            *portstr=0;
            portstr+=1;
            gfxport=atoi(portstr);
        }
    }
    InstallSignals();
    gfx_thread = ACL_CreateThread(GFXThread, NULL);
    input_thread = ACL_CreateThread(InputThread, NULL);
    media_thread = ACL_CreateThread(MediaThread, NULL);
    fprintf(stderr,"Starting main loop\n");
    while(1)
    {
        if(ConnectionError!=0)
        {
            // TODO: tell other threads to exit so they can do it more cleanly
            fprintf(stderr, "Connection error %d, exiting\n",ConnectionError);
            fprintf(stderr, "Joining gfx thread\n");
            ACL_ThreadJoin(gfx_thread);
            fprintf(stderr, "Joining input thread\n");
            ACL_ThreadJoin(input_thread);
            fprintf(stderr, "Joining media thread\n");
            ACL_ThreadJoin(media_thread);
            fprintf(stderr, "Deinit GFX\n");
            {
                FILE *gfxfile;
                gfxfile=fopen("/tmp/firmwareupdate","rb");
                if(gfxfile!=NULL)
                {
                    fprintf(stderr, "Skipping deinit because of firmware update\n");
                    GFX_keepOSD();
                }
                else
                {
                    GFX_deinit();
                }
            }
            fprintf(stderr, "Exiting\n");
            exit(ExitValue);
        }
        ACL_Delay(200000);
    }
    exit(0);
}
