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
#include "sage_PVR150Input.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <fcntl.h>
//#include <linux/compiler.h>
#include <linux/i2c.h>
#include <linux/i2c-dev.h>
#include <unistd.h>
#include <inttypes.h>
#include <ctype.h>
//#define IVTV_INTERNAL
//#include "ivtv.h"
#define IVTV_IOC_RESET_IR          _IO  ('@', 213)


static int i2creadarray(int i2cfile, int addr, int len, unsigned char *array)
{
    struct i2c_msg i2cmsg[2];
    struct i2c_rdwr_ioctl_data i2cmsgdata;
    
/*    i2cmsg[0].addr  = 0x71;
    i2cmsg[0].flags = 0;
    i2cmsg[0].buf   = (unsigned char *)&addr;
    i2cmsg[0].len   = 1;*/

    i2cmsg[0].addr  = 0x71;
    i2cmsg[0].flags = I2C_M_RD;
    i2cmsg[0].buf   = array;
    i2cmsg[0].len   = len;
         
    i2cmsgdata.msgs  = i2cmsg;
    i2cmsgdata.nmsgs = 1;

    ioctl(i2cfile, I2C_RDWR , &i2cmsgdata); 
    return len;    
}

static int i2cwritearray(int i2cfile, int addr, int len, unsigned char *array)
{
    unsigned char buf2[257];
    int i;
    buf2[0]=addr;
    for(i=0;i<len;i++)buf2[i+1]=array[i];
    
    struct i2c_msg i2cmsg[1];
    struct i2c_rdwr_ioctl_data i2cmsgdata;
    
    i2cmsg[0].addr  = 0x70;
    i2cmsg[0].flags = 0;
    i2cmsg[0].buf   = (unsigned char *)&buf2;
    i2cmsg[0].len   = len+1;

    i2cmsgdata.msgs  = i2cmsg;
    i2cmsgdata.nmsgs = 1;

    ioctl(i2cfile, I2C_RDWR , &i2cmsgdata); 
    return len;    
}

static void sysOutPrint(JNIEnv* env, const char* cstr, ...)
{
    jthrowable oldExcept = (*env)->ExceptionOccurred(env);
    if (oldExcept)
        (*env)->ExceptionClear(env);
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    vsnprintf(buf, sizeof(buf), cstr, args);
    va_end(args);
    jstring jstr = (*env)->NewStringUTF(env, buf);
    static jclass cls = 0;
    static jfieldID outField;
    static jmethodID printMeth;
    if (!cls)
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

/*
 * Class:     sage_LinuxInput
 * Method:    setupPVR150Input
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_PVR150Input_setupPVR150Input
  (JNIEnv *env, jobject o, jint i2cnumber)
{

    int i2cfile;
    char i2cfilename[256];
    int addr;
    
    sprintf(i2cfilename, "/dev/i2c/%d", i2cnumber);
    (*env)->MonitorEnter(env, o);
    if((i2cfile = open(i2cfilename, O_RDWR)) < 0)
    {
        printf("Could not open %s\n", i2cfilename);
	(*env)->MonitorExit(env, o);
        return JNI_FALSE;
    }
    
    addr = 0x71;
    if (ioctl(i2cfile,I2C_SLAVE,addr) < 0) 
    {
        printf("error setting slave address\n");   
        close(i2cfile);     
        (*env)->MonitorExit(env, o);
        return JNI_FALSE;
    }
    
    close(i2cfile);
    (*env)->MonitorExit(env, o);
    return JNI_TRUE;
}

/*
 * Class:     sage_LinuxInput
 * Method:    closePVR150Input
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_PVR150Input_closePVR150Input
  (JNIEnv *env, jobject o)
{

}

/*
 * Class:     sage_LinuxInput
 * Method:    PVR150InputThread
 * Signature: (Lsage/SageTVInputCallback;)V
 */
JNIEXPORT void JNICALL Java_sage_PVR150Input_PVR150InputThread
  (JNIEnv *env, jobject o1, jint i2cnumber, jobject o2)
{    
    jclass rtrClass = (*env)->GetObjectClass(env, o2);
    //jmethodID keyMeth = (*env)->GetMethodID(env, rtrClass, "recvKeystroke", "(CII)V");
    jmethodID irMeth = (*env)->GetMethodID(env, rtrClass, "recvInfrared", "([B)V");
    int curkey=0;
    int samecount=0;
    int i2cfile;
    char i2cfilename[256];
    int addr;
    sprintf(i2cfilename, "/dev/i2c/%d", i2cnumber);
    (*env)->MonitorEnter(env, o1);
    if((i2cfile = open(i2cfilename, O_RDWR)) < 0)
    {
        printf("Could not open %s\n", i2cfilename);
        (*env)->MonitorExit(env, o1);
        return;
    }
    (*env)->MonitorExit(env, o1);
    

    addr = 0x71;
    (*env)->MonitorEnter(env, o1);
    if (ioctl(i2cfile,I2C_SLAVE,addr) < 0)
    {
        printf("error setting slave address\n");
        (*env)->MonitorExit(env, o1);
        return;
    }
    (*env)->MonitorExit(env, o1);

// Process data if any

    int failureCount = 0;

    while(1)
    {        
        jbyte jb[6];
        (*env)->MonitorEnter(env, o1);

        // Try to minimize I2C transfers
        int readRes = read(i2cfile, jb, 1);
        if (readRes != 1)
        {
            sysOutPrint(env, "I2C HAS FAILED!!!! res=%d failCount=%d\n",
		 readRes, failureCount++);
            char devName[128];
            sprintf(devName, "/dev/video%d", i2cnumber);
	    int fd = open(devName, O_RDWR);
            if (fd > 0)
            {
                sysOutPrint(env, "RESETING I2C %s failCount=%d\n", 
			devName, failureCount);
                if (ioctl(fd, IVTV_IOC_RESET_IR) < 0)
                   sysOutPrint(env, "FAILED RESETING I2C\n");
                close(fd);
            }
        }
        
                
        if((jb[0]&0x80)) // Key pressed
        {
            int readRes = read(i2cfile, jb, 6);
            sysOutPrint(env, "PVR150 IR %02X %02X\n",jb[3],jb[4]);
            if(curkey!=(jb[3]&0x20))            
            {
               curkey=jb[3]&0x20;
               samecount=3;
            }
            else
            {
               samecount+=1;
            }
            if(samecount>=3)
            {
                jb[3] &= 0xDF; // ignore switch bit
                jbyteArray ja = (*env)->NewByteArray(env,2);
                (*env)->SetByteArrayRegion(env, ja, 0, 2, &jb[3]);
                (*env)->CallVoidMethod(env, o2, irMeth, ja);
            }
        }
        (*env)->MonitorExit(env, o1);
        usleep(200000); // seems too small makes the ir receive not work
    }    
    
    close(i2cfile);    
}

