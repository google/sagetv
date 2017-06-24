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
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/errno.h>
#include <unistd.h>
#include <features.h> 
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <getopt.h>
#include <errno.h>
#include <sys/time.h>
#include <math.h>
#include <linux/types.h>
#include <time.h>
#include <sys/timeb.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <dirent.h>
#include "sage_IVTVCaptureDevice.h"
#include "sage_LinuxIVTVCaptureManager.h"
#include "videodev2.h"
#include "thread_util.h"
#include "circbuffer.h"

// Enable transation on good point between files

#define FILETRANSITION

// Consider latency issues vs cpu usage
#define BUFFERSIZE 16384

// Tweak this if needed
#define CAPCIRCBUFFERSIZE 4*1024*1024 

#define PACK_START_CODE          0x000001BA
#define SYSTEM_HEADER_START_CODE 0x000001BB

#define DWORD unsigned int
#define max(x, y) ((x >= y) ? x : y)
#define min(x, y) ((x <= y) ? x : y)

inline DWORD DWORD_SWAP(DWORD x)
{
    return
     ((DWORD)( ((x) << 24) | ((x) >> 24) |
               (((x) & 0xFF00) << 8) | (((x) & 0xFF0000) >> 8)));
}

#define BOOL int

static inline BOOL IsNTSCVideoCode(int x)
{ 
	return x<=1; 
}

struct bcast 
{
	char ch[16];
	unsigned long fq;
};

// Access predefined broadcast standard arrays
extern struct bcast NTSC_BCAST[256]; 
extern struct bcast NTSC_CABLE[256];
extern struct bcast NTSC_HRC[256];
extern struct bcast NTSC_BCAST_JP[256];
extern struct bcast NTSC_CABLE_JP[256];
extern struct bcast PAL_AUSTRALIA[256];
extern struct bcast PAL_EUROPE[256];
extern struct bcast PAL_EUROPE_EAST[256];
extern struct bcast PAL_ITALY[256];
extern struct bcast PAL_IRELAND[256];
extern struct bcast PAL_NEWZEALAND[256];
extern struct bcast PALNC_ARGENTINA[256];

typedef struct SageTVMPEG2EncodingParameters
{
	int audiooutputmode;// = 0; 
	int audiocrc;// = 0;
	int gopsize;// = 15;
	int videobitrate;// = 4000000;
	int peakvideobitrate;//new Integer(5000000);
	int inversetelecine;// = 0;
	int closedgop;// = 0;
	int vbr;// = 0;
	int outputstreamtype;// = 0;
	int width;// = 720;
	int height;// = 480;
	int audiobitrate;// = 384;
	int audiosampling;// = 48000;

	int disablefilter; // 1
	int medianfilter; // 3
	int fps; // 30, 25 or 15
	int ipb; // 0,1 is ipb, 1 is i, 2 is ip
	int deinterlace;
	int aspectratio; // 0 is 1:1, 1 is 4:3 and 2 is 16:9
} SageTVMPEG2EncodingParameters;

#define CARD_IVTV 0
#define CARD_HDPVR 1
typedef struct MyDevFDs
{
	int configFd; // for configuring the device
	int capFd; // for getting captured data from the device
	FILE* fd; // file to write the captured data to
	long circFileSize;
	char devName[256];
	int cardType; // 0: mpeg 2 ivtv 1: hdpvr
	BOOL dropNextSeq;
	unsigned char *buf;
	unsigned char *buf2;
	long circWritePos;
	struct bcast *freqarray; // must be set when the input is set
	int videoFormatCode;
	SageTVMPEG2EncodingParameters encodeParams;
	circBuffer capBuffer;
	ACL_mutex *capMutex;
	int capState; // 0: normal 1: discarding 2: exit
	ACL_Thread *capThread;
	int discardCount;
#ifdef FILETRANSITION
    FILE* newfd; // File that should be written to as soon as we have a good transition point
    int bytesTested; // We want to give up after some fixed amount of bytes if no transition found
#endif
} MyDevFDs;



static void sysOutPrint(JNIEnv *env, const char* cstr, ...)
{
    static jclass cls=NULL;
    static jfieldID outField=NULL;
    static jmethodID printMeth=NULL;
    if(env == NULL) return;
    jthrowable oldExcept = (*env)->ExceptionOccurred(env);
    if (oldExcept)
        (*env)->ExceptionClear(env);
    va_list args;
    va_start(args, cstr);
    char buf[1024*2];
    vsnprintf(buf, sizeof(buf), cstr, args);
    va_end(args);
    jstring jstr = (*env)->NewStringUTF(env, buf);
    if(cls==NULL)
    {
        cls = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/System"));
        outField = (*env)->GetStaticFieldID(env, cls, "out", "Ljava/io/PrintStream;");
        printMeth = (*env)->GetMethodID(env, (*env)->FindClass(env, "java/io/PrintStream"),
        "print", "(Ljava/lang/String;)V");
    }
    jobject outObj = (*env)->GetStaticObjectField(env, cls, outField);
    (*env)->CallVoidMethod(env, outObj, printMeth, jstr);
    (*env)->DeleteLocalRef(env, jstr);
    if (oldExcept)
        (*env)->Throw(env, oldExcept);
}

static void throwEncodingException(JNIEnv* env, jint errCode)
{
	static jclass encExcClass = 0;
	
	static jmethodID encConstMeth = 0;
	if (!encExcClass)
	{
		encExcClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "sage/EncodingException"));
		encConstMeth = (*env)->GetMethodID(env, encExcClass, "<init>", "(II)V");
	}
	jthrowable throwie = (jthrowable) (*env)->NewObject(env, encExcClass, encConstMeth, errCode, (jint) errno);
	(*env)->Throw(env, throwie);
}

// JFT, Is there a safe way to do logs from that thread?
// see native/common/jni-util.cpp for example from other thread
static void * CaptureThread(void *data)
{
    MyDevFDs *x=(MyDevFDs *) data;
    fd_set rfds;
    struct timeval tv;
    int maxfd;
    int retval;
    int state;
    struct sched_param scparam={0,};
    scparam.sched_priority=sched_get_priority_max(SCHED_FIFO);
    sched_setscheduler(0, SCHED_FIFO, &scparam);
    while(1)
    {
        // Remove locks and merge with the main loop?
        ACL_LockMutex(x->capMutex);
        state=x->capState;
        ACL_UnlockMutex(x->capMutex);
        if(state==2) break;

        FD_ZERO(&rfds);
        FD_SET(x->capFd, &rfds);
        maxfd=x->capFd;

        tv.tv_sec = 0;
        tv.tv_usec = 100000;
        retval = select(maxfd+1, &rfds, NULL, NULL, &tv);
        if (retval == -1)
            return (void *) -1;
        if(FD_ISSET(x->capFd, &rfds))
        {
            FD_CLR(x->capFd, &rfds);
            int numbytes;
            numbytes = read(x->capFd, x->buf2, BUFFERSIZE);
            ACL_LockMutex(x->capMutex);
            // Can we reenter record mode
            if(state==1 && freespaceCircBuffer(&x->capBuffer)>(CAPCIRCBUFFERSIZE/4))
            {
                state=0;
            }
            if(numbytes>0)
            {
                if(state==0 && freespaceCircBuffer(&x->capBuffer)>=numbytes)
                {
                    // We can add to our capture buffer
                    addCircBuffer(&x->capBuffer, x->buf2, numbytes);
                }
                else
                {
                    // Enter discard mode
                    state=1; 
                    x->discardCount+=numbytes;
                }
            }
            if(x->capState!=2)
            {
                x->capState=state;
            }
            ACL_UnlockMutex(x->capMutex);
        }
    }
    return (void *) 0;
}

/*
 * Class:     sage_IVTVCaptureDevice
 * Method:    createEncoder0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_IVTVCaptureDevice_createEncoder0
  (JNIEnv *env, jobject jo, jstring jdevname)
{
	MyDevFDs rv;
	int retval;
	struct v4l2_ext_controls ctrls;
	struct v4l2_ext_control ctrl;
	struct v4l2_capability caps;
	
	memset(&rv, 0, sizeof(MyDevFDs));
	const char* cdevname = (*env)->GetStringUTFChars(env, jdevname, NULL);
	strcpy(rv.devName, "/dev/");
	strcat(rv.devName, cdevname);
	sysOutPrint(env, "V4L: createEncoder %s\n",rv.devName);
	(*env)->ReleaseStringUTFChars(env, jdevname, cdevname);
	rv.configFd = open(rv.devName, O_RDONLY);
	if (rv.configFd < 0)
	{
		throwEncodingException(env, __LINE__/*sage_EncodingException_CAPTURE_DEVICE_INSTALL*/);
		return 0;
	}
	// Test if this is a HDPVR
	{
		if(ioctl(rv.configFd, VIDIOC_QUERYCAP, &caps) == 0)
		{
			if(strncmp(caps.driver, "hdpvr", 16)==0)
			{
				sysOutPrint(env, "V4L: detected hdpvr\n");
				rv.cardType=CARD_HDPVR;
			}
		}
	}
	if(createCircBuffer(&rv.capBuffer, CAPCIRCBUFFERSIZE)==0)
	{
		throwEncodingException(env, __LINE__/*sage_EncodingException_CAPTURE_DEVICE_INSTALL*/);
		return 0;
	}
	rv.capMutex = ACL_CreateMutex();
	MyDevFDs* realRv = (MyDevFDs*) malloc(sizeof(MyDevFDs));
	if(realRv!=NULL)
	{
		memcpy(realRv, &rv, sizeof(MyDevFDs));
		realRv->buf=malloc(BUFFERSIZE);
		realRv->buf2=malloc(BUFFERSIZE);
		if(realRv->buf==NULL || realRv->buf2==NULL)
		{
			if(realRv->buf) free(realRv->buf);
			if(realRv->buf2) free(realRv->buf2);
			free(realRv);
			realRv=NULL;
		}
	}
	return (jlong) realRv;
}

/*
 * Class:     sage_IVTVCaptureDevice
 * Method:    setupEncoding0
 * Signature: (JLjava/lang/String;J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_IVTVCaptureDevice_setupEncoding0
  (JNIEnv *env, jobject jo, jlong ptr, jstring jfilename, jlong circSize)
{
	if (ptr)
	{
		MyDevFDs* x = (MyDevFDs*) ptr;
		// Open the interface to the capture device
		x->capFd = open(x->devName, O_RDONLY, S_IWUSR);
		if (x->capFd == -1)
		{
			throwEncodingException(env, __LINE__/*sage_EncodingException_CAPTURE_DEVICE_INSTALL*/);
			return JNI_FALSE;
		}
		if(fcntl(x->capFd, F_SETFL, O_NONBLOCK) != 0)
		{
			sysOutPrint(env, "V4L: couldn't set nonblocking mode\n");
		}
		x->circFileSize = (long) circSize;
		x->circWritePos = 0;
		// Open up the file we're going to write to
		const char* cfilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
		sysOutPrint(env, "V4L: setup encoding %s\n",cfilename);
		x->fd = fopen(cfilename, "wb");
		(*env)->ReleaseStringUTFChars(env, jfilename, cfilename);
		if (!x->fd)
		{
			if(x->cardType==CARD_HDPVR)
			{
				struct v4l2_encoder_cmd v4lcmd;
				memset(&v4lcmd, 0, sizeof(struct v4l2_encoder_cmd));
				v4lcmd.cmd=V4L2_ENC_CMD_STOP;
				ioctl(x->capFd, VIDIOC_ENCODER_CMD, &v4lcmd);
			}
			close(x->capFd);
			x->capFd = 0;
			throwEncodingException(env, __LINE__/*sage_EncodingException_FILESYSTEM*/);
			return JNI_FALSE;
		}
		if(x->cardType==CARD_IVTV) x->dropNextSeq = 1;
		x->discardCount=0;
		x->capState=0;
		resetCircBuffer(&x->capBuffer);
		x->capThread = ACL_CreateThread(CaptureThread, x);
		return JNI_TRUE;
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_IVTVCaptureDevice
 * Method:    switchEncoding0
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_IVTVCaptureDevice_switchEncoding0
  (JNIEnv *env, jobject jo, jlong ptr, jstring jfilename)
{
	// Change the output file to be this new file
	if (ptr)
	{
		MyDevFDs* x = (MyDevFDs*) ptr;
#ifndef FILETRANSITION
		if (x->fd)
		{
			fclose(x->fd);
			x->fd = 0;
		}
#else
		int loopcount=0;
#endif

		// Open up the file we're going to write to
		const char* cfilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
        sysOutPrint(env, "V4L: switch encoding %s\n",cfilename);
#ifdef FILETRANSITION
		x->newfd = fopen(cfilename, "wb");
		x->bytesTested=0;
#else
		x->fd = fopen(cfilename, "wb");
#endif
		(*env)->ReleaseStringUTFChars(env, jfilename, cfilename);
#ifdef FILETRANSITION
		if (!x->newfd)
#else
		if (!x->fd)
#endif
		{
			// This can be true in FILETRANSITION mode when newfd failed
			if (x->fd)
			{
				fclose(x->fd);
				x->fd = 0;
			}
			if(x->cardType==CARD_HDPVR)
			{
				struct v4l2_encoder_cmd v4lcmd;
				memset(&v4lcmd, 0, sizeof(struct v4l2_encoder_cmd));
				v4lcmd.cmd=V4L2_ENC_CMD_STOP;
				ioctl(x->capFd, VIDIOC_ENCODER_CMD, &v4lcmd);
			}
			close(x->capFd);
			x->capFd = 0;
			throwEncodingException(env, __LINE__/*sage_EncodingException_FILESYSTEM*/);
			return JNI_FALSE;
		}
#ifdef FILETRANSITION
		// We must loop until we have switched file since the core expects no write to the old file after this point.
        sysOutPrint(env, "V4L: going into eatEncoderData0 until file switched\n");
		while(x->newfd && loopcount<5)
		{
			// At most this will wait 1 second
			if(Java_sage_IVTVCaptureDevice_eatEncoderData0(env, jo, ptr)==0)
				loopcount+=1;
		}
		if(x->newfd)
		{
			fclose(x->fd);
			x->fd=x->newfd;
			x->newfd=0;
		}
#else
		if(x->cardType==CARD_IVTV) x->dropNextSeq = 1;
#endif
		return JNI_TRUE;
	}
}

/*
 * Class:     sage_IVTVCaptureDevice
 * Method:    closeEncoding0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_IVTVCaptureDevice_closeEncoding0
  (JNIEnv *env, jobject jo, jlong ptr)
{
	sysOutPrint(env, "V4L: closeEncoding\n");
	if (ptr)
	{
		MyDevFDs* x = (MyDevFDs*) ptr;
		if(x->capThread)
		{
			ACL_LockMutex(x->capMutex);
			x->capState=2;
			ACL_UnlockMutex(x->capMutex);
			sysOutPrint(env, "V4L: join capture thread\n");
			ACL_ThreadJoin(x->capThread);
			sysOutPrint(env, "V4L: capture thread stopped\n");
			ACL_RemoveThread(x->capThread);
			x->capThread=NULL;
		}
		if (x->fd)
		{
			fclose(x->fd);
			x->fd = 0;
		}
#ifdef FILETRANSITION
		if (x->newfd)
		{
			fclose(x->newfd);
			x->newfd = 0;
		}
#endif
		if (x->capFd)
		{
			if(x->cardType==CARD_HDPVR)
			{
				struct v4l2_encoder_cmd v4lcmd;
				memset(&v4lcmd, 0, sizeof(struct v4l2_encoder_cmd));
				v4lcmd.cmd=V4L2_ENC_CMD_STOP;
				ioctl(x->capFd, VIDIOC_ENCODER_CMD, &v4lcmd);
			}
			close(x->capFd);
			x->capFd = 0;
		}
		sysOutPrint(env, "V4L: done closeEncoding\n");
		return JNI_TRUE;
	}
	else
		return JNI_FALSE;
}

/*
 * Class:     sage_IVTVCaptureDevice
 * Method:    destroyEncoder0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_IVTVCaptureDevice_destroyEncoder0
  (JNIEnv *env, jobject jo, jlong ptr)
{
	sysOutPrint(env, "V4L: destroyEncoder\n");
	if (ptr)
	{
		MyDevFDs* x = (MyDevFDs*) ptr;
		if(x->capThread)
		{
			ACL_LockMutex(x->capMutex);
			x->capState=2;
			ACL_UnlockMutex(x->capMutex);
			sysOutPrint(env, "V4L: join capture thread\n");
			ACL_ThreadJoin(x->capThread);
			sysOutPrint(env, "V4L: capture thread stopped\n");
			ACL_RemoveThread(x->capThread);
			x->capThread=NULL;
		}
		if (x->configFd)
		{
			close(x->configFd);
			x->configFd = 0;
		}
		if (x->capFd)
		{
			if(x->cardType==CARD_HDPVR)
			{
				struct v4l2_encoder_cmd v4lcmd;
				memset(&v4lcmd, 0, sizeof(struct v4l2_encoder_cmd));
				v4lcmd.cmd=V4L2_ENC_CMD_STOP;
				ioctl(x->capFd, VIDIOC_ENCODER_CMD, &v4lcmd);
			}
			close(x->capFd);
			x->capFd = 0;
		}
		free(x->capBuffer.data);
		ACL_RemoveMutex(x->capMutex);
		free(x->buf);
		free(x->buf2);
		free(x);
	}
	sysOutPrint(env, "V4L: done destroyEncoder\n");
}

// NOTE: This could get bad start codes.
static int verifyPSBlock(unsigned char *data)
{
    unsigned char b;
    unsigned int pos=0;
    int cur=0xFFFFFFFF;
    while(pos<2048)
    {
        b=data[pos];
        pos+=1;
        cur<<=8;
        cur|=b;
        if((cur&0xFFFFFF00)==0x00000100)
        {
            /* video */
            if((b==0xB3))
            {
                return 1;
            }
        }
    }
    return 0;
}

static int findTransitionPoint(unsigned char* data, int length, int flags)
{
    int numbytes=length;

    // HDPVR format
    if(flags==1)
    {
        int i, tsstart=-1, tsvidpacket=-1, seqstart=-1;
        // For the HDPVR our input is a tranport stream, we must find a valid start point
        // in the video pid 0x1011 of 00 00 01 09 10 00

        // First we try to locate ts packets
        for(i=0;i<numbytes;i++)
        {
            if(data[i]==0x47 && 
                (i+188)<numbytes && data[i+188]==0x47 &&
                (i+188*2)<numbytes && data[i+188*2]==0x47)
            {
                tsstart=i;
                break;
            }
        }

        // Second we find a ts packet with section start and pid 0x1011
        while((i+188)<=numbytes)
        {
            if(data[i]==0x47 &&
                data[i+1]==0x50 &&
                data[i+2]==0x11)
            {
                tsvidpacket=i;
                // Verify if that packet contains the magic sequence 00 00 00 01 09 10 00
                // If it does, the data up to the begining of this TS packet go in old file 
                // and the new data in the new file
                int j;
                for(j=4;j<188-7;j++)
                {
                    // NOTE: we could implement faster search but the number of
                    // matched packet that reach this point should be quite small...
                    if(data[i+j]==0x00 &&
                        data[i+j+1]==0x00 &&
                        data[i+j+2]==0x00 &&
                        data[i+j+3]==0x01 &&
                        data[i+j+4]==0x09 &&
                        data[i+j+5]==0x10 &&
                        data[i+j+6]==0x00)
                    {
                        // We have found the vid packet with the magic sequence, write that to old file
                        return tsvidpacket;
                    }
                }
            }
            i+=188;
        }
    }
    else
    {
        // For the IVTV cards we must find a sequence start inside the video stream
        // we are looking for 00 00 01 B3
        int i=0, psstart=-1;
        // IVTV use 2K blocks
        // First locate the 00 00 01 BA block
        while(i<=numbytes-2048)
        {
            if(data[i]==0x00 && 
                data[i+1]==0x00 &&
                data[i+2]==0x01 &&
                data[i+3]==0xBA)
            {
                psstart=i;
                if(verifyPSBlock(&data[i]))
                {
                    // We have found the sequence start
                    return psstart;
                }
                i+=2048;
            }
            else
            {
                i++;
            }
        }
    }
    return -1;
}

/*
 * Class:     sage_IVTVCaptureDevice
 * Method:    eatEncoderData0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_IVTVCaptureDevice_eatEncoderData0
  (JNIEnv *env, jobject jo, jlong ptr)
{
	int count=0;
	if (ptr)
	{
		MyDevFDs* x = (MyDevFDs*) ptr;

		// Read the data from the capture device, then check for seq hdr if we need to,
		// and then write it to the file; enforcing circularity if needed
		BOOL readMore = 1;
		int numbytes;
/*		while (readMore)
		{
			numbytes = read(x->capFd, x->buf, BUFFERSIZE);
			if (numbytes == -1)
			{
				if (errno == EBUSY || errno == EAGAIN)
				{
					// Device isn't ready yet, just wait a bit and it will be.
					ACL_Delay(10);
					count++;
					if(count>100) return 0;
					continue;
				}
				throwEncodingException(env, __LINE__);//sage_EncodingException_HW_VIDEO_COMPRESSION);
				return 0;
			}
			else
				readMore = 0;
		}*/

        // When doing a file transition we will want to get at least full buffersize
        // This should increase probabilities of match since it will contain at least
        // 86 ts packets or 7 program stream blocks
        while(readMore)
        {
            ACL_LockMutex(x->capMutex);
            numbytes = usedspaceCircBuffer(&x->capBuffer);
            numbytes = numbytes > BUFFERSIZE ?  BUFFERSIZE : numbytes;
#ifdef FILETRANSITION
            if(x->newfd)
            {
                // We dont' want less than full buffer so sleep a little if less than full
                if(numbytes!=BUFFERSIZE) numbytes=0;
            }
#endif
            getCircBuffer(&x->capBuffer, x->buf, numbytes);
            ACL_UnlockMutex(x->capMutex);
            if(numbytes==0)
            {
                ACL_Delay(10);
                count++;
                if(count>100) return 0;
            }
            else
                readMore = 0;
        }

        int bufSkip = 0;
#ifdef FILETRANSITION
        // We want to switch to the new fd as soon as we get a good starting point
        if(x->newfd)
        {
            // We need to parse the current data buffer hoping to find a starting point
            int transitionpos = findTransitionPoint(x->buf, numbytes, 
                x->cardType==CARD_HDPVR ? 1 : 0);
            if(transitionpos!=-1)
            {
                bufSkip=transitionpos;
                if (!x->circFileSize)
                {
                    // TODO: Do we care if this fails?
                    fwrite(x->buf, 1, bufSkip, x->fd);
                }
                sysOutPrint(env, "V4L: transition found after %d bytes switching fd on %s\n",
                    x->bytesTested+bufSkip,x->devName);
                fclose(x->fd);
                x->fd=x->newfd;
                x->newfd=0;
            }
            else
            {
                x->bytesTested+=numbytes;
                // We might want to have different numbers for IVTV/HDPVR or base it on bitrates
                if(x->bytesTested>16*1024*1024)
                {
                    sysOutPrint(env, "V4L: transition limit reached, switching fd on %s\n",x->devName);
                    fclose(x->fd);
                    x->fd=x->newfd;
                    x->newfd=0;
                }
            }
        }
#endif
		if (x->dropNextSeq)
		{
			int i = 0;
			// TODO: eventually we might want to check for things that go over different blocks
			for (i = 0; i + 14 + 8 < numbytes; i++)
			{
				// TODO: fix that for big endian cpus...
				DWORD currWord = DWORD_SWAP(*((DWORD*)&(x->buf[i])));
				if (currWord == PACK_START_CODE)
				{
					// Determine if it's MPEG1 or MPEG2
					DWORD nextWord;
					if (x->buf[4] >> 6)
					{
						// mpeg2
						int stuffLen = (x->buf[i + 13] & 0x07);
						nextWord = DWORD_SWAP(*(DWORD*)&(x->buf[i + 14 + stuffLen]));
					}
					else
					{
						// mpeg1
						nextWord = DWORD_SWAP(*(DWORD*)&(x->buf[i + 12]));
					}
					if (nextWord == SYSTEM_HEADER_START_CODE)
					{
						sysOutPrint(env, "V4L: Found next seq hdr code!\n");
						x->dropNextSeq = 0;
						numbytes -= i;
						bufSkip += i;
						break;
					}
				}
			}
			if(x->dropNextSeq) numbytes=0;
		}

		if (numbytes)
		{
			if (x->circFileSize)
			{
				if (x->circWritePos == x->circFileSize)
				{
					// Wrap it now
					fseek(x->fd, 0, 0);
					x->circWritePos = 0;
				}
				if (x->circWritePos + numbytes <= x->circFileSize)
				{
					// No wrapping this time
					if (fwrite(x->buf + bufSkip, 1, numbytes, x->fd) == EOF)
					{
						throwEncodingException(env, __LINE__);//sage_EncodingException_HW_VIDEO_COMPRESSION);
						return 0;
					}
					x->circWritePos += numbytes;
				}
				else
				{
					if (fwrite(x->buf + bufSkip, 1, x->circFileSize - x->circWritePos, x->fd) == EOF)
					{
						throwEncodingException(env, __LINE__);//sage_EncodingException_HW_VIDEO_COMPRESSION);
						return 0;
					}
					int remBytes = numbytes - (x->circFileSize - x->circWritePos);
					// Wrap it now
					fseek(x->fd, 0, 0);
					x->circWritePos = remBytes;
					if (fwrite(x->buf + bufSkip + (numbytes - remBytes), 1, remBytes, x->fd) == EOF)
					{
						throwEncodingException(env, __LINE__);//sage_EncodingException_HW_VIDEO_COMPRESSION);
						return 0;
					}
				}

			}
			else
			{
				if (fwrite(x->buf + bufSkip, 1, numbytes, x->fd) == -1)
				{
					throwEncodingException(env, __LINE__);//sage_EncodingException_HW_VIDEO_COMPRESSION);
					return 0;
				}
			}
		}
		fflush(x->fd);
		return numbytes;
	}
}

static jboolean setFrequencyNew(JNIEnv *env, MyDevFDs* x, unsigned long freq)
{
    struct v4l2_tuner tuner;
    struct v4l2_frequency vf;
    
    int precision = 1;
    int retval;
    
    sysOutPrint(env, "V4L: setChannel0 frequency %d (%d).\n",freq,freq*1000/16);
    
    tuner.type=2;
    
    if(ioctl(x->configFd, VIDIOC_G_TUNER, &tuner) == 0)
    {
        precision = (tuner.capability & V4L2_TUNER_CAP_LOW) ? 1000 : 1;
        sysOutPrint(env, "V4L: tuner detected precision mode %d.\n", precision);
    }
    
    vf.tuner = 0;
    vf.type = tuner.type;
    vf.frequency = freq * precision;
    retval = ioctl(x->configFd, VIDIOC_S_FREQUENCY, &vf);
    if (retval) 
    {
        sysOutPrint(env, "V4L: Failed to change channel (ioctl error).\n");
        return JNI_FALSE;
    }
    else 
    {
        sysOutPrint(env, "V4L: Channel change succesful.\n");
        return JNI_TRUE;
    }
}

/*
 * Class:     sage_IVTVCaptureDevice
 * Method:    setChannel0
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_IVTVCaptureDevice_setChannel0
  (JNIEnv *env, jobject jo, jlong ptr, jstring jchan)
{
	if (ptr)
	{
		MyDevFDs* x = (MyDevFDs*) ptr;
		if (x->configFd && x->freqarray)
		{
			int i;
			unsigned long freq, c, f;
			char chn[8];

			// set the default television standard (in case user defined standard
			// can't be determined

			// Default to this...
			if (!x->freqarray)
				x->freqarray = NTSC_CABLE;

			// Send channel frequency
			freq = 0;
			i = 0;

			const char* chann = (*env)->GetStringUTFChars(env, jchan, NULL);
			while (x->freqarray[i].fq != 0) {
				strcpy(chn, x->freqarray[i].ch);
				f = x->freqarray[i].fq;
				if (strcmp(chann, chn) == 0) {
					freq = (int)((f*16)/1000); 
					break;
				}
				i++;
			}
			(*env)->ReleaseStringUTFChars(env, jchan, chann);

			if (freq == 0) {
				return JNI_FALSE;
			}
			return setFrequencyNew(env, x, freq);
		}
	}
}

/*
 * Class:     sage_IVTVCaptureDevice
 * Method:    setInput0
 * Signature: (JIILjava/lang/String;II)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_IVTVCaptureDevice_setInput0
  (JNIEnv *env, jobject jo, jlong ptr, jint inputType, jint inputIndex,
	jstring tunerMode, jint countryCode, jint videoFormatCode)
{
	sysOutPrint(env, "V4L: setInput0 %d %d %d %d\n",inputType, inputIndex, countryCode, videoFormatCode);
	if (ptr)
	{
		MyDevFDs* x = (MyDevFDs*) ptr;
		x->videoFormatCode = videoFormatCode;
		const char* cform = (*env)->GetStringUTFChars(env, tunerMode, NULL);
		BOOL isCable = (strcmp("Cable", cform) == 0);
		BOOL isHrc = (strcmp("HRC", cform) == 0);
		(*env)->ReleaseStringUTFChars(env, tunerMode, cform);
		// Set the frequency array based on the standard
		if (videoFormatCode <= 1 && countryCode!=54 )
		{
			if (countryCode <= 1)
			{
				if (isCable)
					x->freqarray = NTSC_CABLE;
				else if (isHrc)
					x->freqarray = NTSC_HRC;
				else
					x->freqarray = NTSC_BCAST;
			}
			else
			{
				if (isCable)
					x->freqarray = NTSC_CABLE_JP;
				else
					x->freqarray = NTSC_BCAST_JP;
			}
		}
		else if (countryCode == 61)
			x->freqarray = PAL_AUSTRALIA;
		else if (countryCode == 39)
			x->freqarray = PAL_ITALY;
		else if (countryCode == 353)
			x->freqarray = PAL_IRELAND;
		else if (countryCode == 64)
			x->freqarray = PAL_NEWZEALAND;
		else if (countryCode==54)
		{
			if (isCable)
				x->freqarray = NTSC_CABLE;
			else
				x->freqarray = NTSC_BCAST;
		}
		else if (countryCode <= 50) // NOT ACCURATE
			x->freqarray = PAL_EUROPE;
		else // NOT ACCURATE
			x->freqarray = PAL_EUROPE_EAST;
		if (x->configFd)
		{
			// Set video standard
			// Note:  this was causing a strange audio problem and is currently disabled.
			// The standard setting is still used for finding the proper frequency.  This
			// may not be neceesary anyway, since it appears the tuner on the PVR-250 is
			// hard coded for the broadcast standard where it's marketed.
			v4l2_std_id std = 0x3000; // NTSC
			if(videoFormatCode < 8) { 
				std = 0x3000; // NTSC
			}
			else if(videoFormatCode >= 0x1000) {
				std = 0x7F0000; // SECAM
			}
			else  {
				std = 0xFF; // PAL
			}

            if(countryCode==54) // Argentina
            {
                std = 0x400;
            }
			sysOutPrint(env, "V4L: setting standard to 0x%X\n",std);
			if (ioctl(x->configFd, VIDIOC_S_STD, &std))
			{
				sysOutPrint(env, "V4L: Failed to set video standard to %d.\n", std);
				throwEncodingException(env, __LINE__);//sage_EncodingException_HW_VIDEO_COMPRESSION);
				return JNI_FALSE;
			}

			if (ioctl(x->configFd, VIDIOC_S_INPUT, &inputType) == -1)
			{
				sysOutPrint(env, "V4L: Failed setting the input on the capture device\n");
				throwEncodingException(env, __LINE__);//sage_EncodingException_HW_VIDEO_COMPRESSION);
				return JNI_FALSE;
			}
// NOTE: This may not work right on non-VWB systems, we haven't tested the latest IVTV on them yet
			int audioInput = 0;
			if(x->cardType==CARD_HDPVR)
			{
				if(inputIndex > 0)
				{
					audioInput = 2;
				}
				sysOutPrint(env, "V4L: setting audio to input %d\n",audioInput);
				if (ioctl(x->configFd, VIDIOC_S_AUDIO, &audioInput) == -1)
				{
					sysOutPrint(env, "V4L: Failed setting the audio input on the capture device\n");
					//throwEncodingException(env, __LINE__);
					//return JNI_FALSE;
				}
			}
			else if (inputType == 0 && inputIndex == 0)
			{
				// tuner on the card
				audioInput = 0;
				if (ioctl(x->configFd, VIDIOC_S_AUDIO, &audioInput) == -1)
				{
					sysOutPrint(env, "V4L: Failed setting the audio input on the capture device\n");
					//throwEncodingException(env, __LINE__);
					//return JNI_FALSE;
				}
			}
			else if ((inputType == 1 || inputType == 3) && inputIndex == 0)
			{
				// line in on the card
				audioInput = 1;
				if (ioctl(x->configFd, VIDIOC_S_AUDIO, &audioInput) == -1)
				{
					sysOutPrint(env, "V4L: Failed setting the audio input on the capture device\n");
					//throwEncodingException(env, __LINE__);
					//return JNI_FALSE;
				}
			}
			else if ((inputType == 2 || inputType == 4) && inputIndex >= 1)
			{
				// auxillary line in
				audioInput = 2;
				if (ioctl(x->configFd, VIDIOC_S_AUDIO, &audioInput) == -1)
				{
					sysOutPrint(env, "V4L: Failed setting the audio input on the capture device\n");
					//throwEncodingException(env, __LINE__);
					//return JNI_FALSE;
				}
			}
			else if (inputType == 0 && inputIndex == 1)
			{
				// spdif on hdpvr
				audioInput = 0;
				if (ioctl(x->configFd, VIDIOC_S_AUDIO, &audioInput) == -1)
				{
					sysOutPrint(env, "V4L: Failed setting the audio input on the capture device\n");
					//throwEncodingException(env, __LINE__);
					//return JNI_FALSE;
				}
			}


			return JNI_TRUE;
		}
	}
	return JNI_FALSE;
}

static jboolean setEncoding0NewInterface(JNIEnv *env, MyDevFDs* x, 
    SageTVMPEG2EncodingParameters* encodeParams)
{
	struct v4l2_control ctrl;
	struct v4l2_ext_controls ctrls2;
	struct v4l2_ext_control ctrl2;
	struct v4l2_format vfmt;
	int retval;
    //VIDIOC_S_EXT_CTRLS
    
    /*
    ; // stream_type
    encodeParams->vbr;     // video_bitrate_mode
    encodeParams->videobitrate;  // video_bitrate
    max(encodeParams->peakvideobitrate, codec.bitrate); //video_peak_bitrate
    encodeParams->audiocrc // audio_crc
    encodeParams->audiooutputmode // ??
    encodeParams->audiosampling // audio_sampling_frequency
    encodeParams->audiobitrate // audio_layer_ii_bitrate
    encodeParams->width // VIDIOC_S_FMT
    encodeParams->height // VIDIOC_S_FMT
    */

	ctrl2.id = V4L2_CID_MPEG_VIDEO_BITRATE_MODE;
	ctrl2.value = 1^encodeParams->vbr; // 0: vbr 1: cbr
    
	ctrls2.ctrl_class = V4L2_CTRL_CLASS_MPEG;
	ctrls2.count = 1;
	ctrls2.controls = &ctrl2;
	retval = ioctl(x->configFd, VIDIOC_S_EXT_CTRLS, &ctrls2);
	if(retval != 0)
	{
		sysOutPrint(env, "V4L: ioctl: failed to set VIDEO_BITRATE_MODE to %d.\n", 
			ctrl2.value);
	}

	ctrl2.id = V4L2_CID_MPEG_VIDEO_BITRATE;
	ctrl2.value = encodeParams->videobitrate;
    
	ctrls2.ctrl_class = V4L2_CTRL_CLASS_MPEG;
	ctrls2.count = 1;
	ctrls2.controls = &ctrl2;
	retval = ioctl(x->configFd, VIDIOC_S_EXT_CTRLS, &ctrls2);
	if(retval != 0)
	{
		sysOutPrint(env, "V4L: ioctl: failed to set VIDEO_BITRATE to %d.\n", 
			ctrl2.value);
	}

	ctrl2.id = V4L2_CID_MPEG_VIDEO_BITRATE_PEAK;
	ctrl2.value = max(encodeParams->peakvideobitrate, encodeParams->videobitrate);
	if(x->cardType==CARD_HDPVR)
	{
		if(ctrl2.value<=encodeParams->videobitrate+100000) 
			ctrl2.value=encodeParams->videobitrate+100000;
	}
	ctrls2.ctrl_class = V4L2_CTRL_CLASS_MPEG;
	ctrls2.count = 1;
	ctrls2.controls = &ctrl2;
	retval = ioctl(x->configFd, VIDIOC_S_EXT_CTRLS, &ctrls2);
	if(retval != 0)
	{
		sysOutPrint(env, "V4L: ioctl: failed to set VIDEO_BITRATE_PEAK to %d.\n", 
			ctrl2.value);
	}

    if(x->cardType==CARD_HDPVR)
    {
        // JFT, I don't remember what this was for
        //ACL_Delay(500);
    }

    if(x->cardType==CARD_IVTV)
    {
        sysOutPrint(env, "V4L: setEncoding0NewInterface\n");

        ctrl2.id = V4L2_CID_MPEG_STREAM_TYPE;
        ctrl2.value = encodeParams->outputstreamtype == 10 ?
            V4L2_MPEG_STREAM_TYPE_MPEG2_DVD :
            V4L2_MPEG_STREAM_TYPE_MPEG2_PS; 

        ctrls2.ctrl_class = V4L2_CTRL_CLASS_MPEG;
        ctrls2.count = 1;
        ctrls2.controls = &ctrl2;
        retval = ioctl(x->configFd, VIDIOC_S_EXT_CTRLS, &ctrls2);
        if(retval != 0)
        {
            sysOutPrint(env, "V4L: ioctl: failed to set MPEG_STREAM_TYPE to %d.\n", 
                ctrl2.value);
        }
        ctrl2.id = V4L2_CID_MPEG_AUDIO_SAMPLING_FREQ;
        ctrl2.value = (encodeParams->audiosampling == 32000) ? 
            V4L2_MPEG_AUDIO_SAMPLING_FREQ_32000 : 
            ((encodeParams->audiosampling == 44100) ? 
            V4L2_MPEG_AUDIO_SAMPLING_FREQ_44100 :
            V4L2_MPEG_AUDIO_SAMPLING_FREQ_48000);

        ctrls2.ctrl_class = V4L2_CTRL_CLASS_MPEG;
        ctrls2.count = 1;
        ctrls2.controls = &ctrl2;
        retval = ioctl(x->configFd, VIDIOC_S_EXT_CTRLS, &ctrls2);
        if(retval != 0)
        {
            sysOutPrint(env, "V4L: ioctl: failed to set AUDIO_SAMPLING_FREQ to %d.\n", 
                ctrl2.value);
        }

        ctrl2.id = V4L2_CID_MPEG_AUDIO_L2_BITRATE;
        switch(encodeParams->audiobitrate)
        {
            case 192:
                ctrl2.value = V4L2_MPEG_AUDIO_L2_BITRATE_192K;
                break;
            case 224:
                ctrl2.value = V4L2_MPEG_AUDIO_L2_BITRATE_224K;
                break;
            case 256:
                ctrl2.value = V4L2_MPEG_AUDIO_L2_BITRATE_256K;
                break;
            case 320:
                ctrl2.value = V4L2_MPEG_AUDIO_L2_BITRATE_320K;
                break;
            default: //384
                ctrl2.value = V4L2_MPEG_AUDIO_L2_BITRATE_384K;
                break;
        }

        ctrls2.ctrl_class = V4L2_CTRL_CLASS_MPEG;
        ctrls2.count = 1;
        ctrls2.controls = &ctrl2;
        retval = ioctl(x->configFd, VIDIOC_S_EXT_CTRLS, &ctrls2);
        if(retval != 0)
        {
            sysOutPrint(env, "V4L: ioctl: failed to set AUDIO_L2_BITRATE to %d.\n", 
                ctrl2.value);
        }

        vfmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        if(ioctl(x->configFd, VIDIOC_G_FMT, &vfmt) < 0)
        {
            sysOutPrint(env, "V4L: ioctl: VIDIOC_G_FMT failed.\n");
            return JNI_FALSE;
        }
        else
        {
            vfmt.fmt.pix.width = encodeParams->width;
            vfmt.fmt.pix.height = encodeParams->height;

            if(ioctl(x->configFd, VIDIOC_S_FMT, &vfmt) < 0)
            {
                sysOutPrint(env, "V4L: ioctl: VIDIOC_S_FMT failed.\n");
                return JNI_FALSE;
            }
        }
    }

    return JNI_TRUE;
}

/*
 * Class:     sage_IVTVCaptureDevice
 * Method:    setEncoding0
 * Signature: (JLjava/lang/String;Ljava/util/Map;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_IVTVCaptureDevice_setEncoding0
  (JNIEnv *env, jobject jo, jlong ptr, jstring jencName, jobject encodePropsMap)
{
	if (!ptr) return JNI_FALSE;
	MyDevFDs* x = (MyDevFDs*) ptr;
	if (!x->configFd) return JNI_FALSE;
	const char* tempStr = (*env)->GetStringUTFChars(env, jencName, NULL);
	sysOutPrint(env, "V4L: setEncodingProperties0 %s\n", tempStr);
	(*env)->ReleaseStringUTFChars(env, jencName, tempStr);

	SageTVMPEG2EncodingParameters* encodeParams = &(x->encodeParams);
	memset(encodeParams, 0, sizeof(SageTVMPEG2EncodingParameters));

	// Setup the non-zero defaults in the encoder properties
	encodeParams->gopsize = 15;
	encodeParams->audiosampling = 48000;
	encodeParams->audiobitrate = 384;
	encodeParams->videobitrate = 4000000;
	encodeParams->peakvideobitrate = 5000000;
	encodeParams->width = 720;
	encodeParams->height = IsNTSCVideoCode(x->videoFormatCode) ? 480 : 576;
	encodeParams->disablefilter = 1;
	encodeParams->fps = IsNTSCVideoCode(x->videoFormatCode) ? 30 : 25;
	encodeParams->aspectratio = 1; // 4:3

	if (encodePropsMap)
	{
		static jclass iteratorClass = 0;
		static jclass mapClass = 0;
		static jclass setClass = 0;
		static jclass mapEntryClass = 0;
		static jmethodID mapEntrySetMeth = 0;
		static jmethodID iteratorMeth = 0;
		static jmethodID hasNextMeth = 0;
		static jmethodID nextMeth = 0;
		static jmethodID getKeyMeth = 0;
		static jmethodID getValueMeth = 0;

		if (!iteratorClass)
		{
			iteratorClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/util/Iterator"));
			mapClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/util/Map"));
			setClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/util/Set"));
			mapEntryClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/util/Map$Entry"));
			mapEntrySetMeth = (*env)->GetMethodID(env, mapClass, "entrySet", "()Ljava/util/Set;");
			iteratorMeth = (*env)->GetMethodID(env, setClass, "iterator", "()Ljava/util/Iterator;");
			hasNextMeth = (*env)->GetMethodID(env, iteratorClass, "hasNext", "()Z");
			nextMeth = (*env)->GetMethodID(env, iteratorClass, "next", "()Ljava/lang/Object;");
			getKeyMeth = (*env)->GetMethodID(env, mapEntryClass, "getKey", "()Ljava/lang/Object;");
			getValueMeth = (*env)->GetMethodID(env, mapEntryClass, "getValue", "()Ljava/lang/Object;");
		}

		// Iterate over the name/value pairs in the map
		jobject walker = (*env)->CallObjectMethod(env, (*env)->CallObjectMethod(env, encodePropsMap, mapEntrySetMeth),
			iteratorMeth);
		char cPropName[512];
		char cPropValue[512];
		while ((*env)->CallBooleanMethod(env, walker, hasNextMeth))
		{
			jobject currEntry = (*env)->CallObjectMethod(env, walker, nextMeth);
			jstring propName = (jstring) (*env)->CallObjectMethod(env, currEntry, getKeyMeth);
			if (propName)
			{
				const char* tempName = (*env)->GetStringUTFChars(env, propName, NULL);
				strcpy(cPropName, tempName);
				(*env)->ReleaseStringUTFChars(env, propName, tempName);
				jstring propValue = (jstring) (*env)->CallObjectMethod(env, currEntry, getValueMeth);
				if (propValue)
				{
					const char* tempValue = (*env)->GetStringUTFChars(env, propValue, NULL);
					strcpy(cPropValue, tempValue);
					(*env)->ReleaseStringUTFChars(env, propValue, tempValue);
				
					int currValue = atoi(cPropValue);

					// Now that we have the char[] name and the value we can
					// set the appropriate values in the parameter object
					if (!strcmp(cPropName, "audiobitrate"))
						encodeParams->audiobitrate = currValue;
					else if (!strcmp(cPropName, "audiocrc"))
						encodeParams->audiocrc = currValue;
					else if (!strcmp(cPropName, "audiooutputmode"))
						encodeParams->audiooutputmode = currValue;
					else if (!strcmp(cPropName, "audiosampling"))
						encodeParams->audiosampling = currValue;
					else if (!strcmp(cPropName, "closedgop"))
						encodeParams->closedgop = currValue;
					else if (!strcmp(cPropName, "disablefilter"))
						encodeParams->disablefilter = currValue;
					else if (!strcmp(cPropName, "gopsize"))
						encodeParams->gopsize = currValue;
					else if (!strcmp(cPropName, "height"))
						encodeParams->height = currValue;
					else if (!strcmp(cPropName, "inversetelecine"))
						encodeParams->inversetelecine = currValue;
					else if (!strcmp(cPropName, "medianfilter"))
						encodeParams->medianfilter = currValue;
					else if (!strcmp(cPropName, "outputstreamtype"))
						encodeParams->outputstreamtype = currValue;
					else if (!strcmp(cPropName, "peakvideobitrate"))
						encodeParams->peakvideobitrate = currValue;
					else if (!strcmp(cPropName, "vbr"))
						encodeParams->vbr = currValue;
					else if (!strcmp(cPropName, "videobitrate"))
						encodeParams->videobitrate = currValue;
					else if (!strcmp(cPropName, "width"))
						encodeParams->width = currValue;
					else if (!strcmp(cPropName, "fps"))
						encodeParams->fps = currValue;
					else if (!strcmp(cPropName, "ipb"))
						encodeParams->ipb = currValue;
					else if (!strcmp(cPropName, "deinterlace"))
						encodeParams->deinterlace = currValue;
					else if (!strcmp(cPropName, "aspectratio"))
						encodeParams->aspectratio = currValue;
					else
						continue;

					sysOutPrint(env, "V4L: Set encoding property %s to %d\n", cPropName, currValue);
				}
			}
		}
	}
	
	return setEncoding0NewInterface(env, x, encodeParams);
}

/*
 * Class:     sage_IVTVCaptureDevice
 * Method:    updateColors0
 * Signature: (JIIIII)[I
 */
JNIEXPORT jintArray JNICALL Java_sage_IVTVCaptureDevice_updateColors0
  (JNIEnv *env, jobject jo, jlong ptr, jint brightness, jint contrast, jint huey, 
	jint saturation, jint sharpness)
{
	sysOutPrint(env, "V4L:  updateColors0 b=%d c=%d h=%d s=%d\n", brightness, contrast, huey, saturation);
	if (!ptr) return JNI_FALSE;
	MyDevFDs* x = (MyDevFDs*) ptr;
	if (!x->configFd) return JNI_FALSE;
	struct v4l2_queryctrl queryctrl;
	struct v4l2_control control;
	jint retColors[5];
	double br = min(255, brightness);
	double con = min(255, contrast);
	double hue = min(255, huey);
	double sat = min(255, saturation);
	double sha = min(255, sharpness);
	long pMin, pMax, val, pDefault;
	pMin = 0;
	pMax = 255;
	pDefault = 128;
	BOOL hasDefault = (br >= 0);
	if (br < 0) br = ((pDefault-pMin)*255.0)/(pMax-pMin);
	queryctrl.id = control.id = V4L2_CID_BRIGHTNESS;
	if (ioctl(x->configFd, VIDIOC_QUERYCTRL, &queryctrl) != -1 &&
		(queryctrl.flags & V4L2_CTRL_FLAG_DISABLED) == 0)
	{
		control.value = (int)(((pMax-pMin)*br)/255 + pMin);
		if(x->cardType==CARD_HDPVR)
			control.value=queryctrl.default_value;
		if (ioctl(x->configFd, VIDIOC_S_CTRL, &control) == -1)
		{
			sysOutPrint(env, "V4L: ERROR: VIDIOC_S_CTRL\n");
		}
	}
	if (hasDefault)
		retColors[0] = max(0, min(255, brightness));
	else
		retColors[0] = ((pDefault-pMin)*255)/(pMax-pMin);

	pMin = 0;
	pMax = 127;
	pDefault = 64;
	hasDefault = (con >= 0);
	if (con < 0) con = ((pDefault-pMin)*255.0)/(pMax-pMin);
	queryctrl.id = control.id = V4L2_CID_CONTRAST;
	if (ioctl(x->configFd, VIDIOC_QUERYCTRL, &queryctrl) != -1 &&
		(queryctrl.flags & V4L2_CTRL_FLAG_DISABLED) == 0)
	{
		control.value = (int)(((pMax-pMin)*con)/255 + pMin);
		if(x->cardType==CARD_HDPVR)
			control.value=queryctrl.default_value;
		if (ioctl(x->configFd, VIDIOC_S_CTRL, &control) == -1)
		{
			sysOutPrint(env, "V4L: ERROR: VIDIOC_S_CTRL\n");
		}
	}
	if (hasDefault)
		retColors[1] = max(0, min(255, contrast));
	else
		retColors[1] = ((pDefault-pMin)*255)/(pMax-pMin);

	pMin = -128;
	pMax = 127;
	pDefault = 0;
	hasDefault = (hue >= 0);
	if (hue < 0) hue = ((pDefault-pMin)*255.0)/(pMax-pMin);
	queryctrl.id = control.id = V4L2_CID_HUE;
	if (ioctl(x->configFd, VIDIOC_QUERYCTRL, &queryctrl) != -1 &&
		(queryctrl.flags & V4L2_CTRL_FLAG_DISABLED) == 0)
	{
		control.value = (int)(((pMax-pMin)*hue)/255 + pMin);
		if(x->cardType==CARD_HDPVR)
			control.value=queryctrl.default_value;
		if (ioctl(x->configFd, VIDIOC_S_CTRL, &control) == -1)
		{
			sysOutPrint(env, "V4L: ERROR: VIDIOC_S_CTRL\n");
		}
	}
	if (hasDefault)
		retColors[2] = max(0, min(255, huey));
	else
		retColors[2] = ((pDefault-pMin)*255)/(pMax-pMin);

	pMin = 0;
	pMax = 127;
	pDefault = 64;
	hasDefault = (sat >= 0);
	if (sat < 0) sat = ((pDefault-pMin)*255.0)/(pMax-pMin);
	queryctrl.id = control.id = V4L2_CID_SATURATION;
	if (ioctl(x->configFd, VIDIOC_QUERYCTRL, &queryctrl) != -1 &&
		(queryctrl.flags & V4L2_CTRL_FLAG_DISABLED) == 0)
	{
		control.value = (int)(((pMax-pMin)*sat)/255 + pMin);
		if(x->cardType==CARD_HDPVR)
			control.value=queryctrl.default_value;
		if (ioctl(x->configFd, VIDIOC_S_CTRL, &control) == -1)
		{
			sysOutPrint(env, "V4L: ERROR: VIDIOC_S_CTRL\n");
		}
	}
	if (hasDefault)
		retColors[3] = max(0, min(255, sat));
	else
		retColors[3] = ((pDefault-pMin)*255)/(pMax-pMin);

	// No sharpness on Linux-do sound volume instread
	pMin = 0;
	pMax = 65535;
	pDefault = 61000;
	hasDefault = (sha >= 0);
	if (sha < 0) sha = ((pDefault-pMin)*255.0)/(pMax-pMin);
	queryctrl.id = control.id = V4L2_CID_AUDIO_VOLUME;
	if (ioctl(x->configFd, VIDIOC_QUERYCTRL, &queryctrl) != -1 &&
		(queryctrl.flags & V4L2_CTRL_FLAG_DISABLED) == 0)
	{
		control.value = (int)(((pMax-pMin)*sha)/255 + pMin);
		if(x->cardType==CARD_HDPVR)
			control.value=queryctrl.default_value;
		if (ioctl(x->configFd, VIDIOC_S_CTRL, &control) == -1)
		{
			sysOutPrint(env, "V4L: ERROR: VIDIOC_S_CTRL\n");
		}
	}
	if (hasDefault)
		retColors[4] = max(0, min(255, sha));
	else
		retColors[4] = ((pDefault-pMin)*255)/(pMax-pMin);

	jintArray rv = (*env)->NewIntArray(env, 5);
	(*env)->SetIntArrayRegion(env, rv, 0, 5, retColors);
	return rv;
}

/*
 * Class:     sage_LinuxIVTVCaptureManager
 * Method:    getV4LInputName
 * Signature: (Ljava/lang/String;I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_LinuxIVTVCaptureManager_getV4LInputName
  (JNIEnv *env, jclass c, jstring jdevName, jint index)
{
	int fd;
	jstring jinputname;
	char inputname[33];
	struct v4l2_input input;
	const char* cdevname = (*env)->GetStringUTFChars(env, jdevName, NULL);
	sysOutPrint(env, "V4L: getV4LInputName %s %d\n",cdevname, index);
	fd = open(cdevname, O_RDONLY);
	(*env)->ReleaseStringUTFChars(env, jdevName, cdevname);
	if(fd<0) return NULL;

	memset(&input, 0, sizeof(struct v4l2_input));
	input.index=index;
	if(ioctl(fd, VIDIOC_ENUMINPUT, &input) == 0)
	{
		close(fd);
		strncpy(inputname, input.name, 32);
		inputname[32]=0;
		jinputname = (*env)->NewStringUTF(env, inputname);
		return jinputname;
	}
	close(fd);
	return NULL;
}

/*
 * Class:     sage_LinuxIVTVCaptureManager
 * Method:    getV4LCardType
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_LinuxIVTVCaptureManager_getV4LCardType
  (JNIEnv *env, jclass c, jstring jdevName)
{
	int fd;
	jstring jcardname;
	char cardname[33];
	struct v4l2_capability caps;
	const char* cdevname = (*env)->GetStringUTFChars(env, jdevName, NULL);
	sysOutPrint(env, "V4L: getV4LCardType %s\n",cdevname);
	fd = open(cdevname, O_RDONLY);
	(*env)->ReleaseStringUTFChars(env, jdevName, cdevname);
	if(fd<0) return NULL;

	if(ioctl(fd, VIDIOC_QUERYCAP, &caps) == 0)
	{
		close(fd);
		strncpy(cardname, caps.card, 32);
		cardname[32]=0;
		jcardname = (*env)->NewStringUTF(env,cardname);
		return jcardname;
	}
	close(fd);
	return NULL;
}

JNIEXPORT jstring JNICALL Java_sage_LinuxIVTVCaptureManager_getCardModelUIDForDevice
  (JNIEnv *env, jclass c, jstring jdevName)
{
    jstring jcardname=NULL;
    char fname[1024];
    char buf1[1024];
    struct v4l2_capability caps;
    fname[1023]=0;
    buf1[1023]=0;
    const char* cdevname = (*env)->GetStringUTFChars(env, jdevName, NULL);
    int handle=open(cdevname, O_RDONLY);

    if(handle>=0)
    {
        if(ioctl(handle, VIDIOC_QUERYCAP, &caps) == 0)
        {
            if((strlen(caps.bus_info)==0 || strncasecmp("usb", caps.bus_info, 3)==0) && strlen(cdevname)>5)
            {
                FILE *extrainfo;
                // We can try to find the serial through sys
                snprintf(fname,1023, "/sys/class/video4linux/%s/device/serial",
                    cdevname+5);
                extrainfo=fopen(fname,"r");
                if(extrainfo==NULL)
                {
                    snprintf(fname,1023, "/sys/class/video4linux/%s/device/../serial",
                        cdevname+5);
                    extrainfo=fopen(fname,"r");
                }
                snprintf(buf1, 1023, "%.32s ",caps.card);
                if(extrainfo!=NULL)
                {
                    fgets(buf1+strlen(buf1), 1023-strlen(buf1), extrainfo);
                    // remove any /r/n
                    while(strlen(buf1)>0 && 
                        ( buf1[strlen(buf1)-1]=='\r' || buf1[strlen(buf1)-1]=='\n' ))
                    {
                        buf1[strlen(buf1)-1]=0;
                    }
                    fclose(extrainfo);
                }
                else
                {
                    buf1[strlen(buf1)-1]=0; // Remove the extra space
                }
            }
            else // pci and default
            {
                snprintf(buf1, 1023, "%.32s %.32s",caps.card, caps.bus_info);
            }
        }
        close(handle);
        jcardname = (*env)->NewStringUTF(env,buf1);
    }
    (*env)->ReleaseStringUTFChars(env, jdevName, cdevname);
    return jcardname;
}

JNIEXPORT jint JNICALL Java_sage_LinuxIVTVCaptureManager_getCardI2CForDevice
  (JNIEnv *env, jclass c, jstring jdevName)
{
    jstring jcardname=NULL;
    char fname[1024];
    int i2cnum=-1;
    DIR *dirhandle;
    struct dirent* direntry;

    fname[1023]=0;
    const char* cdevname = (*env)->GetStringUTFChars(env, jdevName, NULL);

    // First try same level (PCI IVTV CARDS)
    snprintf(fname, 1023, "/sys/class/video4linux/%s/device/i2c-adapter",
            cdevname+5);
    if(dirhandle = opendir(fname))
    {
        while((direntry=readdir(dirhandle))!=NULL)
        {
            if(i2cnum==-1)
            {
                if(strncmp(direntry->d_name,"i2c-",4)==0)
                {
                    i2cnum=atoi(direntry->d_name+4);
                }
            }
        }
        closedir(dirhandle);
    }

    if(i2cnum==-1)
    {
        snprintf(fname, 1023, "/sys/class/video4linux/%s/device",
                cdevname+5);
        if(dirhandle = opendir(fname))
        {
            while((direntry=readdir(dirhandle))!=NULL)
            {
                if(i2cnum==-1)
                {
                    if(strncmp(direntry->d_name,"i2c-",4)==0)
                    {
                        i2cnum=atoi(direntry->d_name+4);
                    }
                }
            }
            closedir(dirhandle);
        }
    }

    if(i2cnum==-1)
    {
        // Try second level (USB HDPVR)
        snprintf(fname, 1023, "/sys/class/video4linux/%s/device/../i2c-adapter",
                cdevname+5);
        if(dirhandle = opendir(fname))
        {
            while((direntry=readdir(dirhandle))!=NULL)
            {
                if(i2cnum==-1)
                {
                    if(strncmp(direntry->d_name,"i2c-",4)==0)
                    {
                        i2cnum=atoi(direntry->d_name+4);
                    }
                }
            }
            closedir(dirhandle);
        }
    }

    if(i2cnum==-1)
    {
        // Try second level (USB HDPVR)
        snprintf(fname, 1023, "/sys/class/video4linux/%s/device/..",
                cdevname+5);
        if(dirhandle = opendir(fname))
        {
            while((direntry=readdir(dirhandle))!=NULL)
            {
                if(i2cnum==-1)
                {
                    if(strncmp(direntry->d_name,"i2c-",4)==0)
                    {
                        i2cnum=atoi(direntry->d_name+4);
                    }
                }
            }
            closedir(dirhandle);
        }
    }
    (*env)->ReleaseStringUTFChars(env, jdevName, cdevname);
    return i2cnum;
}

