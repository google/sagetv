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
// ServiceControlLaunch.cpp : Defines the entry point for the application.
//

#include "stdafx.h"


// ServiceControlLauncher.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"


/*
 * Copyright 2001-2004 Frey Technologies, LLC. All rights reserved.
 */
#include <windows.h>
#include <stdlib.h>
#include <jni.h>
#include <stdio.h>
#include <direct.h>


#define JVM_MISSING MessageBox(NULL, "Could not get information on current JVM.\nPlease install Java Runtime Environment 1.4","Java Missing", MB_OK);\


static JNIEnv* globalenv = 0;

JavaVM *vm;    

void sysOutPrint(const char* cstr, ...)
{
	JNIEnv* env;
	jint threadState = vm->GetEnv((void**)&env, JNI_VERSION_1_2);
	if (threadState == JNI_EDETACHED)
		vm->AttachCurrentThread((void**)&env, NULL);
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    vsprintf(buf, cstr, args);
    va_end(args);
	jstring jstr = env->NewStringUTF(buf);
	static jclass cls = (jclass) env->NewGlobalRef(env->FindClass("java/lang/System"));
	static jfieldID outField = env->GetStaticFieldID(cls, "out", "Ljava/io/PrintStream;");
	static jmethodID printMeth = env->GetMethodID(env->FindClass("java/io/PrintStream"),
		"print", "(Ljava/lang/String;)V");
	jobject outObj = env->GetStaticObjectField(cls, outField);
	env->CallVoidMethod(outObj, printMeth, jstr);
	env->DeleteLocalRef(jstr);
	if (oldExcept)
		env->Throw(oldExcept);
	if (threadState == JNI_EDETACHED)
		vm->DetachCurrentThread();
}

void sysOutPrint(JNIEnv* env, const char* cstr, ...)
{
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    vsprintf(buf, cstr, args);
    va_end(args);
	jstring jstr = env->NewStringUTF(buf);
	static jclass cls = (jclass) env->NewGlobalRef(env->FindClass("java/lang/System"));
	static jfieldID outField = env->GetStaticFieldID(cls, "out", "Ljava/io/PrintStream;");
	static jmethodID printMeth = env->GetMethodID(env->FindClass("java/io/PrintStream"),
		"print", "(Ljava/lang/String;)V");
	jobject outObj = env->GetStaticObjectField(cls, outField);
	env->CallVoidMethod(outObj, printMeth, jstr);
	env->DeleteLocalRef(jstr);
	if (oldExcept)
		env->Throw(oldExcept);
}


void errorMsg(char* msg, char* title)
{
	MessageBox(NULL, msg, title, MB_OK);
}

void popupExceptionError(JNIEnv* env, jthrowable thrower, char* errTitle)
{
    env->ExceptionClear();
	jmethodID toStr = env->GetMethodID(env->GetObjectClass(thrower), "toString", "()Ljava/lang/String;");
	jstring throwStr = (jstring)env->CallObjectMethod(thrower, toStr);
	const char* cThrowStr = env->GetStringUTFChars(throwStr, 0);
	char *errStr = (char*) malloc(env->GetStringLength(throwStr) + 64);
	sprintf(errStr, "An exception occured in Java:\n%s", cThrowStr);
	errorMsg(errStr, errTitle);
	free(errStr);
}


int APIENTRY WinMain(HINSTANCE hInstance,
                     HINSTANCE hPrevInstance,
                     LPSTR     lpCmdLine,
                     int       nCmdShow)
{
	/*
	 * Explicitly load jvm.dll by using the Windows Registry to locate the current version to use.
	 */
	HKEY rootKey = HKEY_LOCAL_MACHINE;
	char currVer[16];
	HKEY myKey;
	DWORD readType;
	DWORD dwRead = 0;
	DWORD hsize = sizeof(dwRead);
	hsize = sizeof(currVer);
	if (RegOpenKeyEx(rootKey, "Software\\JavaSoft\\Java Runtime Environment", 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
	{
		JVM_MISSING;
		return FALSE;
	}
	
	if (RegQueryValueEx(myKey, "CurrentVersion", 0, &readType, (LPBYTE)currVer, &hsize) != ERROR_SUCCESS)
	{
		RegCloseKey(myKey);
		JVM_MISSING;
		return FALSE;
	}
	RegCloseKey(myKey);
	char pathKey[1024];
	strcpy(pathKey, "Software\\JavaSoft\\Java Runtime Environment\\");
	strcat(pathKey, currVer);
	char jvmPath[1024];
	if (RegOpenKeyEx(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
	{
		JVM_MISSING;
		return FALSE;
	}
	hsize = sizeof(jvmPath);
	if (RegQueryValueEx(myKey, "RuntimeLib", 0, &readType, (LPBYTE)jvmPath, &hsize) != ERROR_SUCCESS)
	{
		RegCloseKey(myKey);
		JVM_MISSING;
		return FALSE;
	}
	RegCloseKey(myKey);

	HMODULE hLib = LoadLibrary(jvmPath);
	if(hLib == NULL) 
	{
		errorMsg("Could not find jvm.dll.\nPlease install Java Runtime Environment 1.4", "Java Missing");
		return FALSE;
	}
	
	// Retrieve address of JNI_CreateJavaVM()
	typedef  jint (JNICALL *P_JNI_CreateJavaVM)
		(JavaVM **pvm, void** penv, void *args);
	
	P_JNI_CreateJavaVM createJavaVM = (P_JNI_CreateJavaVM) 
		GetProcAddress(hLib, "JNI_CreateJavaVM");
	
	if (createJavaVM==NULL)
	{
		errorMsg("Could not execute jvm.dll.\nPlease install Java Runtime Environment 1.4", "Java Missing");
		return FALSE;
	}

	HMODULE exeMod = GetModuleHandle(NULL);
	LPTSTR appPath = new TCHAR[512];
	GetModuleFileName(exeMod, appPath, 512);
	size_t appLen = strlen(appPath);
	for (size_t i = appLen; i-- > 0;) // this does the check for continuation on the value before decrement, but still uses the decremented value inside the loop 
	{
		if (appPath[i] == '\\')
		{
			appPath[i + 1] = 0;
			break;
		}
	}

	// Set the current working directory to be the folder the EXE is in.
	chdir(appPath);

	LPTSTR jarPath = new TCHAR[1024];
	LPTSTR libraryPath = new TCHAR[512];
	
    JNIEnv *env;       /* pointer to native method interface */
	JavaVMInitArgs vm_args;
	JavaVMOption options[32];
	vm_args.nOptions = 0;
	strcpy(jarPath, "-Djava.class.path=");
	strcat(jarPath, appPath);
#ifdef SAGE_TV_LITE
	strcat(jarPath, "SageLite.jar");
#else
	strcat(jarPath, "Sage.jar");
#endif
	options[vm_args.nOptions++].optionString = jarPath;
	strcpy(libraryPath, "-Djava.library.path=");
	strcat(libraryPath, appPath);
	options[vm_args.nOptions++].optionString = libraryPath;  /* set native library path */

	vm_args.version = JNI_VERSION_1_2;
	vm_args.options = options;
	vm_args.ignoreUnrecognized = true;

    /* Note that in the Java 2 SDK, there is no longer any need to call 
	 * JNI_GetDefaultJavaVMInitArgs. 
	 */

	int res = (*createJavaVM)(&vm, (void**) &env, &vm_args); 
	if (res != 0)
	{
		errorMsg("Could not create JVM.\nPlease reinstall Java Runtime Environment 1.7 or greater", "Java Missing");
		return FALSE;
	}
	globalenv = env;

    /* invoke the Main.test method using the JNI */
    jclass cls = env->FindClass("sage/WindowsServiceControl");
	jthrowable clsThrow =env->ExceptionOccurred();
	if (clsThrow)
	{
		popupExceptionError(env, clsThrow, "Could not find JAR file.");
		return FALSE;
	}
	
    jmethodID mid = env->GetStaticMethodID(cls, "main", "([Ljava/lang/String;)V");
	if (env->ExceptionCheck())
	{
		errorMsg("Could not find main in class.\nPlease reinstall the Sage application", "Java Error");
		return FALSE;
	}
    
	env->CallStaticVoidMethod(cls, mid, NULL);
	jthrowable mainThrow = env->ExceptionOccurred();
	if (mainThrow != NULL)
	{
		env->ExceptionClear();
		jmethodID toStr = env->GetMethodID(env->GetObjectClass(mainThrow), "toString", "()Ljava/lang/String;");
//env->ExceptionDescribe();
		jstring throwStr = (jstring)env->CallObjectMethod(mainThrow, toStr);
		const char* cThrowStr = env->GetStringUTFChars(throwStr, 0);
		char *errStr = new char[env->GetStringLength(throwStr) + 64];
		sprintf(errStr, "An exception occured in Java:\n%s", cThrowStr);
		errorMsg(errStr, "Java Exception");
		delete [] errStr;
		return FALSE;
	}

	while (true)
	{
		Sleep(60000);
	}

	delete [] appPath;
	delete [] jarPath;
	delete [] libraryPath;

	FreeLibrary(hLib);
	return 0;
}

