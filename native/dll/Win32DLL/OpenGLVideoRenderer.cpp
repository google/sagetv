/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "stdafx.h"
#include "SageTVWin32DLL.h"
#include "../../include/sage_miniclient_OpenGLVideoRenderer.h"

static int glVideoServerActive = 0;
char shmemPrefix[32] = "";
int shMemCounter = 0;
/*
 * Class:     sage_miniclient_OpenGLVideoRenderer
 * Method:    initVideoServer
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sage_miniclient_OpenGLVideoRenderer_initVideoServer
  (JNIEnv *env, jobject jo)
{
	fprintf(stderr, "Initializing the OpenGL video system.\r\n");
	jclass m_glSageClass = (jclass) env->NewGlobalRef(env->FindClass("sage/miniclient/OpenGLVideoRenderer"));
	if (env->ExceptionOccurred())
		return JNI_FALSE; // let the exception propogate
	jmethodID m_glUpdateMethodID = env->GetMethodID(m_glSageClass, "updateVideo", "(ILjava/nio/ByteBuffer;)Z");
	if (env->ExceptionOccurred())
		return JNI_FALSE; // let the exception propogate
	jmethodID m_glCreateMethodID = env->GetMethodID(m_glSageClass, "createVideo", "(III)Z");
	if (env->ExceptionOccurred())
		return JNI_FALSE; // let the exception propogate
	jmethodID m_glCloseMethodID = env->GetMethodID(m_glSageClass, "closeVideo", "()Z");
	if (env->ExceptionOccurred())
		return JNI_FALSE; // let the exception propogate
	sprintf(shmemPrefix, "SageTV-%d-%d", GetCurrentProcessId(), shMemCounter);
	shMemCounter++;
	glVideoServerActive = 1;
	HANDLE fileMap = CreateFileMapping(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE,
		0, 1920*540*3 + 1024, shmemPrefix);
	char buf[256];
	strcpy(buf, shmemPrefix);
	strcat(buf, "FrameReady");
	HANDLE evtReady = CreateEvent(NULL, FALSE, FALSE, buf);
	strcpy(buf, shmemPrefix);
	strcat(buf, "FrameDone");
	HANDLE evtDone = CreateEvent(NULL, FALSE, FALSE, buf);
	fprintf(stderr, "Created FileMap=0x%p evtReady=0x%p evtDone=0x%p\r\n", fileMap, evtReady, evtDone);
	unsigned char* myPtr = (unsigned char*)MapViewOfFile(fileMap, FILE_MAP_READ|FILE_MAP_WRITE, 0, 0, 0);
	unsigned int* myData = (unsigned int*) myPtr;
	jobject byteBuffer = env->NewDirectByteBuffer(myPtr + 1024, 1920*540*3);
	if (env->ExceptionOccurred())
		return JNI_FALSE; // let the exception propogate
	fprintf(stderr, "Starting to read...0x%p\r\n", myPtr);
	while (glVideoServerActive)
	{
		if (WAIT_OBJECT_0 != WaitForSingleObject(evtReady, 200))
		{
			continue;
		}
		int currCmd = (myData[0] >> 24) & 0xFF;
		//fprintf(stderr, "Got video cmd 0x%x\r\n", currCmd);
		if (currCmd == 0x80)
		{
			// Create Video
			int width = myData[1];
			int height = myData[2];
			fprintf(stderr, "Creating video of size %d x %d\r\n", width, height);
			env->CallBooleanMethod(jo, m_glCreateMethodID, width, height, 0);
			if (env->ExceptionOccurred())
				return JNI_FALSE; // let the exception propogate
			// Respond with offset/stride information
			myData[0] = 1024; // offset y
			myData[1] = width; // pitch y
			myData[2] = 1024 + width*height; // offset u
			myData[3] = width/2; // pitch u
			myData[4] = 1024 + width*height*5/4; // offset y
			myData[5] = width/2; // pitch v
			SetEvent(evtDone);
		}
		else if (currCmd == 0x81)
		{
			env->CallBooleanMethod(jo, m_glUpdateMethodID, myData[1], byteBuffer);
			if (env->ExceptionOccurred())
				return JNI_FALSE; // let the exception propogate
			SetEvent(evtDone);
		}
		else if (currCmd == 0x82)
		{
			env->CallBooleanMethod(jo, m_glCloseMethodID);
			if (env->ExceptionOccurred())
				return JNI_FALSE; // let the exception propogate
			ResetEvent(evtDone);
		}
	}
	UnmapViewOfFile(myPtr);
	CloseHandle(evtReady);
	CloseHandle(evtDone);
	CloseHandle(fileMap);
	return JNI_TRUE;
}

/*
 * Class:     sage_miniclient_OpenGLVideoRenderer
 * Method:    deinitVideoServer
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_miniclient_OpenGLVideoRenderer_deinitVideoServer
  (JNIEnv *env, jobject jo)
{
	glVideoServerActive = 0;
}

/*
 * Class:     sage_miniclient_OpenGLVideoRenderer
 * Method:    getServerVideoOutParams
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_miniclient_OpenGLVideoRenderer_getServerVideoOutParams
  (JNIEnv *env, jobject jo)
{
	if (glVideoServerActive)
		return env->NewStringUTF(shmemPrefix);
	else
		return 0;
}
