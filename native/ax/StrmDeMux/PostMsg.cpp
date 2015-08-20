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

//#include "stdafx.h"
#include "PostMsg.h"
#include <streams.h>
#include <stdio.h>
#include <jni.h>

#pragma warning(disable : 4996)
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

	if (!vmBuf)
	{
		HKEY rootKey = HKEY_LOCAL_MACHINE;
		char currVer[16];
		HKEY myKey;
		DWORD readType;
		DWORD hsize = sizeof(currVer);
		if (RegOpenKeyEx(rootKey, "Software\\JavaSoft\\Java Runtime Environment", 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return 0;
		}
		
		if (RegQueryValueEx(myKey, "CurrentVersion", 0, &readType, (LPBYTE)currVer, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return 0;
		}
		RegCloseKey(myKey);
		char pathKey[1024];
		strncpy(pathKey, "Software\\JavaSoft\\Java Runtime Environment\\", sizeof(pathKey));
		strncat(pathKey, currVer, sizeof(pathKey));
		char jvmPath[1024];
		if (RegOpenKeyEx(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return 0;
		}
		hsize = sizeof(jvmPath);
		if (RegQueryValueEx(myKey, "RuntimeLib", 0, &readType, (LPBYTE)jvmPath, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return 0;
		}
		RegCloseKey(myKey);

		// Go to the 2nd to last backslash, and append jawt.dll from there to complete the string
		HMODULE jvmMod = LoadLibrary(jvmPath);

		jsize numVMs;
		typedef jint (JNICALL *JNIGetCreatedJavaVMsPROC)(JavaVM **, jsize, jsize *);
		
		JNIGetCreatedJavaVMsPROC lpfnProc = (JNIGetCreatedJavaVMsPROC)GetProcAddress(jvmMod, "JNI_GetCreatedJavaVMs");
		if (lpfnProc(&vmBuf, 1, &numVMs))
		{
			if ( jvmMod ) FreeLibrary( jvmMod );
			return 0;
		}
		if ( jvmMod ) FreeLibrary( jvmMod );
	}

	if ( vmBuf == NULL )
		return 0;

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

	return 1;
}

int checkMessageRecipient( )
{
	JNIEnv* env;

	if (!vmBuf)
	{
		HKEY rootKey = HKEY_LOCAL_MACHINE;
		char currVer[16];
		HKEY myKey;
		DWORD readType;
		DWORD hsize = sizeof(currVer);
		if (RegOpenKeyEx(rootKey, "Software\\JavaSoft\\Java Runtime Environment", 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return 0;
		}
		
		if (RegQueryValueEx(myKey, "CurrentVersion", 0, &readType, (LPBYTE)currVer, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return 0;
		}
		RegCloseKey(myKey);
		char pathKey[1024];
		strncpy(pathKey, "Software\\JavaSoft\\Java Runtime Environment\\", sizeof(pathKey));
		strncat(pathKey, currVer, sizeof(pathKey));
		char jvmPath[1024];
		if (RegOpenKeyEx(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return 0;
		}
		hsize = sizeof(jvmPath);
		if (RegQueryValueEx(myKey, "RuntimeLib", 0, &readType, (LPBYTE)jvmPath, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return 0;
		}
		RegCloseKey(myKey);

		// Go to the 2nd to last backslash, and append jawt.dll from there to complete the string
		HMODULE jvmMod = LoadLibrary(jvmPath);

		jsize numVMs;
		typedef jint (JNICALL *JNIGetCreatedJavaVMsPROC)(JavaVM **, jsize, jsize *);
		
		JNIGetCreatedJavaVMsPROC lpfnProc = (JNIGetCreatedJavaVMsPROC)GetProcAddress(jvmMod, "JNI_GetCreatedJavaVMs");
		if (lpfnProc(&vmBuf, 1, &numVMs))
		{
			if ( jvmMod ) FreeLibrary( jvmMod );
			return 0;
		}
		if ( jvmMod ) FreeLibrary( jvmMod );
	}

	if ( vmBuf == NULL )
		return 0;

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

		if ( postMsgMeth == 0 || msgConstructor == 0 )
			return 0;
	}

	if ( threadState == JNI_EDETACHED )
		vmBuf->DetachCurrentThread();

	return 1;
}
