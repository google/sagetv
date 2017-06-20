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
#include <jni.h>
#include <stdio.h>
#include "sage_Sage.h"
#ifdef __APPLE__
#include <sys/param.h>
#include <sys/mount.h>
#include <machine/types.h>
#else
#include <sys/vfs.h>
#include <linux/types.h>
#endif

#include <sys/time.h>
#include <time.h>
#include <sys/timeb.h>
#include <sys/types.h>
#include <math.h>

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

/*
 * Class:     sage_Sage
 * Method:    postMessage0
 * Signature: (IIII)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_postMessage0(JNIEnv *env, jclass jo,
	jint winID, jint msg, jint param1, jint param2)
{
}

/*
 * Class:     sage_Sage
 * Method:    println0
 * Signature: (I, Ljava/lang/String;)Z
 */
JNIEXPORT void JNICALL Java_sage_Sage_println0(JNIEnv *env, jclass jc, jint handle, jstring s)
{
}

// ***************************************************************************
// Function Java_sage_Sage_getDiskFreeSpace0 finds the free disk space of the
// path provided in the java string jdisk, and returns a long integer with the 
// free disk space in bytes.  (Note:  C uses a data type (long long) for 
// integers over 4GB, which of course the free space can exceed.  We have to
// assume here the long type of the app calling this procedure can handle that.
// ***************************************************************************
JNIEXPORT jlong JNICALL Java_sage_Sage_getDiskFreeSpace0(
	JNIEnv *env, jclass jc, jstring jdisk)
{
  struct statfs fs;
  long long bytesize;

  // Create a string usable in C
  
  const char* str = (*env)->GetStringUTFChars(env, jdisk, NULL);
  
  printf("Finding free space for path: %s\n", str);

  // Call the stat C library function which returns varies file system info.
  statfs(str, &fs);
  
  // Release the memory used by the string we created.
  (*env)->ReleaseStringUTFChars(env, jdisk, str);

  bytesize = (long long)fs.f_bavail * fs.f_bsize;
  
  printf("Free space is: %lld\n", bytesize);

  return (bytesize);
}


// ***************************************************************************
// Function Java_sage_Sage_getDiskTotalSpace0 currently does the same as the
// Java_sage_Sage_getDiskSpace0 fucntion until the string from the calling
// app can be analyzed.
// ***************************************************************************
JNIEXPORT jlong JNICALL Java_sage_Sage_getDiskTotalSpace0(
	JNIEnv *env, jclass jc, jstring jdisk)
{
  struct statfs fs;
  long long bytesize;

  const char* str = (*env)->GetStringUTFChars(env, jdisk, NULL);
  
  printf("Finding total space for path: %s\n", str);
  statfs(str, &fs);
  
  (*env)->ReleaseStringUTFChars(env, jdisk, str);

  bytesize = (long long)fs.f_blocks * fs.f_bsize;
  
  printf("Total space is: %lld\n", bytesize);

  return (bytesize);
}

/*
 * Class:     sage_Sage
 * Method:    connectToInternet0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sage_Sage_connectToInternet0(JNIEnv *env, jclass jc)
{
	return 0;
}

/*
 * Class:     sage_Sage
 * Method:    disconnectInternet0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_Sage_disconnectInternet0(JNIEnv *env, jclass jc)
{
}

/*
 * Class:     sage_Sage
 * Method:    readStringValue
 * Signature: (ILjava/lang/String;Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_Sage_readStringValue(JNIEnv *env,
	jclass myClass, jint root, jstring key, jstring valueName)
{
	return 0;
}

/*
 * Class:     sage_Sage
 * Method:    readDwordValue
 * Signature: (ILjava/lang/String;Ljava/lang/String;)I;
 */
JNIEXPORT jint JNICALL Java_sage_Sage_readDwordValue(JNIEnv *env,
	jclass myClass, jint root, jstring key, jstring valueName)
{
	return 0;
}

/*
 * Class:     sage_Sage
 * Method:    writeStringValue
 * Signature: (ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_Sage_writeStringValue(JNIEnv *env,
	jclass myClass, jint root, jstring key, jstring valueName, jstring value)
{
	return JNI_FALSE;
}

/*
 * Class:     sage_Sage
 * Method:    writeDwordValue
 * Signature: (ILjava/lang/String;Ljava/lang/String;I)Z
 */
jboolean JNICALL Java_sage_Sage_writeDwordValue(JNIEnv *env,
	jclass myClass, jint root, jstring key, jstring valueName, jint value)
{
	return JNI_FALSE;
}

/*
 * Class:     sage_Sage
 * Method:    removeRegistryValue
 * Signature: (ILjava/lang/String;Ljava/lang/String;)Z
 */
jboolean JNICALL Java_sage_Sage_removeRegistryValue(JNIEnv *env,
	jclass myClass, jint root, jstring key, jstring valueName)
{
	return JNI_FALSE;
}

/*
 * Class:     sage_Sage
 * Method:    getRegistryNames
 * Signature: (ILjava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_Sage_getRegistryNames(JNIEnv *env, jclass jc, 
															jint root, jstring key)
{
	return 0;
}

// ***************************************************************************
// Function Java_sage_Sage_getFileSystemType returns a java string containing
// the type of file system of the directory tree listed in volRoot.  In Linux,
// this is a unique integer that can be translated into the type of file system
// involved.  Without more information, it's hard to know what the calling 
// app is looking for as far as a description, so send this for now.
// ***************************************************************************
JNIEXPORT jstring JNICALL Java_sage_Sage_getFileSystemType(JNIEnv *env, jclass jc, jstring volRoot)
{
  struct statfs fs;
  char filetype[128];
  jstring ftype;
  
  const char* str = (*env)->GetStringUTFChars(env, volRoot, NULL);
  
  statfs(str, &fs);
  
  (*env)->ReleaseStringUTFChars(env, volRoot, str);

  sprintf(filetype, "0x%x", fs.f_type);
  printf("Type of filesystem = %s\n", filetype);

  ftype = (*env)->NewStringUTF(env, filetype);

  return (ftype);
}

// ***************************************************************************
// Function Java_sage_Sage_getFileSystemIdentifier returns a java string with
// the unique file system ID of the directory tree listed in volRoot as used in
// Linux.  Without more information, it's hard to know what the calling 
// app is looking for as far as a description, so send this for now.
// ***************************************************************************
JNIEXPORT jint JNICALL Java_sage_Sage_getFileSystemIdentifier(JNIEnv *env, jclass jc, jstring volRoot)
{
  struct statfs fs;
  char fileID[128];
  jstring fID;
  
  const char* str = (*env)->GetStringUTFChars(env, volRoot, NULL);
  
  statfs(str, &fs);
  
  (*env)->ReleaseStringUTFChars(env, volRoot, str);

#ifdef __APPLE__
  return (fs.f_fsid.val[0]);
#else
  return (fs.f_fsid.__val[0]);
#endif
}

// ***************************************************************************
// Function Java_sage_Sage_setSystemTime sets the system time in seconds and 
// microseconds since the epoch (January 1, 1970) calculated from the data
// sent from the calling app.  This function will require root priveleges to
// run.
// ***************************************************************************
JNIEXPORT void JNICALL Java_sage_Sage_setSystemTime0(JNIEnv *env, jclass jc, jlong timems)
{
  struct timeval tm;
  tm.tv_sec = timems / 1000;
  tm.tv_usec = (timems % 1000) * 1000;
  
  settimeofday(&tm, 0);

}

/*
 * Class:     sage_Sage
 * Method:    getScreenArea0
 * Signature: ()Ljava/awt/Rectangle;
 */
JNIEXPORT jobject JNICALL Java_sage_Sage_getScreenArea0(JNIEnv *env, jclass jc)
{
	return 0;
}


/*
 * Class:     sage_Sage
 * Method:    addTaskbarIcon0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_addTaskbarIcon0(JNIEnv *env, jclass jc, jint jhwnd)
{
}

/*
 * Class:     sage_Sage
 * Method:    updateTaskbarIcon0
 * Signature: (ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_updateTaskbarIcon0(JNIEnv *env, jclass jc, jint jhwnd, jstring tipText)
{
}

/*
 * Class:     sage_Sage
 * Method:    removeTaskbarIcon0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_removeTaskbarIcon0(JNIEnv *env, jclass jc, jint jhwnd)
{
}

// ***************************************************************************
// Function Java_sage_Sage_getCpuResolution simply returns 1000 to indicate to
// the calling app that we are capable of providing system time to a precision
// of milliseconds.
// ***************************************************************************
JNIEXPORT jlong JNICALL Java_sage_Sage_getCpuResolution
  (JNIEnv *env, jclass jc)
{
	return (1000000);
}

// ***************************************************************************
// Function Java_sage_Sage_getCpuTime returns the time in milliseconds since
// the epoch (January 1, 1970).  Use this until we know more about what the
// calling app is looking for (hopefully just needs any type of relative
// time marker).
// ***************************************************************************
JNIEXPORT jlong JNICALL Java_sage_Sage_getCpuTime
  (JNIEnv *env, jclass jc)
{
  struct timeval tm;
  gettimeofday(&tm, 0);
  return tm.tv_sec * 1000000LL + (tm.tv_usec);
}

/*
 * Class:     sage_Sage
 * Method:    enableSSAndPM
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_enableSSAndPM(JNIEnv *env, jclass jc, jboolean x)
{
}

// ***************************************************************************
// Function Java_sage_Sage_getEventTime0 returns the time in milliseconds since
// the epoch (January 1, 1970).  Use this until we know more about what the
// calling app is looking for (hopefully just needs any type of relative
// time marker).
// ***************************************************************************
JNIEXPORT jlong JNICALL Java_sage_Sage_getEventTime0(JNIEnv *env, jclass jc)
{
  struct timeval tm;
  gettimeofday(&tm, 0);
  return tm.tv_sec * 1000LL + (tm.tv_usec/1000LL);
}
