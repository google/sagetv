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
#include "CaptureMsg.h"
#include <jni.h>

static JavaVM* vmBuf = 0;
static jsize numVMs;
static jclass msgMgrClass = 0;
static jmethodID postMsgMeth = 0;
static jclass msgClass = 0;
static jmethodID msgConstructor = 0;
static unsigned long msg_id = 0;


long postMessage( char* pSrc, char* pData, int nDataLen, int MsgType, int Priority )
{
	JNIEnv* env;

	//we sould have a lock here, for mutiple thread

	if (!vmBuf)
	{
		if (JNI_GetCreatedJavaVMs(&vmBuf, 1, &numVMs))
		{
			return -1;
		}
	}

	jint threadState = vmBuf->GetEnv((void**)&env, JNI_VERSION_1_2);
	if (threadState == JNI_EDETACHED)
	{
		vmBuf->AttachCurrentThread((void**)&env, NULL);
	}

	if ( !msgMgrClass )
	{
		msgMgrClass = (jclass) env->NewGlobalRef(env->FindClass("sage/msg/MsgManager"));
		if (msgMgrClass)
			postMsgMeth = env->GetStaticMethodID(msgMgrClass, "postMessage", "(Lsage/msg/SageMsg;)V");
		msgClass = (jclass) env->NewGlobalRef(env->FindClass("sage/msg/SageMsg"));
		if (msgClass)
			msgConstructor = env->GetMethodID(msgClass, "<init>", "(ILjava/lang/Object;Ljava/lang/Object;I)V");
	}

	int dataLen = nDataLen;
	jbyteArray javaData = env->NewByteArray(dataLen);
	env->SetByteArrayRegion(javaData, 0, dataLen, (const jbyte*)pData);
	jobject msgObj = env->NewObject( msgClass, msgConstructor, MsgType, env->NewStringUTF(pSrc), 
										javaData, Priority );
	env->CallStaticVoidMethod( msgMgrClass, postMsgMeth, msgObj );
	msg_id++;

	if ( threadState == JNI_EDETACHED )
		vmBuf->DetachCurrentThread();

	return 0;
}
