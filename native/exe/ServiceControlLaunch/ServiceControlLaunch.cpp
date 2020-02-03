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

/*
*		*********************************************************
*		Microsoft Visual Studio project codepage set to MBCS 
*		as JAVA does not support being passed Unicode parameters
*		*********************************************************
*/

#include "stdafx.h"

#include <windows.h>
#include <stdlib.h>
#include <jni.h>
#include <stdio.h>
#include <direct.h>


#define JVM_MISSING MessageBox(NULL, "Could not get information on current JVM.\nPlease install Java Runtime Environment 1.7 or higher (Java 1.8 preferred)", "Java Missing", MB_OK);\

#ifndef _MSC_VER // Cater for compiling not in MSVC
#define strcpy_s(dst, dst_length, src) strcpy(dst, src)
#define strcat_s(dst, dst_length, src) strcat(dst, src)
#define vsprintf_s(dst, dst_length, format, args) vsprintf(dst, format, args)
#define sprintf_s(dst, dst_length, format, args) sprintf(dst, format, args)
#endif

static JNIEnv* globalenv = 0;

JavaVM *vm;    

void sysOutPrint(const char* cstr, ...)
{
	JNIEnv* env;
	jint threadState = vm->GetEnv((void**)&env, JNI_VERSION_1_6); // JNI in JRE 1.6 and greater
	if (threadState == JNI_EDETACHED)
		vm->AttachCurrentThread((void**)&env, NULL);
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    int const bufsize = 1024;
    char buf[bufsize];
    vsprintf_s(buf, bufsize, cstr, args);
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
    int const bufsize = 1024;
    char buf[bufsize];
    vsprintf_s(buf, bufsize, cstr, args);
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
	size_t errStrlen = strlen(cThrowStr) + 64;  // Need to know this size for the sprintf_s
	sprintf_s(errStr, errStrlen, "An exception occured in Java:\n%s", cThrowStr);
	errorMsg(errStr, errTitle);
	free(errStr);
	env->ReleaseStringUTFChars(throwStr, cThrowStr);
}


int APIENTRY WinMain(HINSTANCE hInstance,
                     HINSTANCE hPrevInstance,
                     LPSTR     lpCmdLine,
                     int       nCmdShow)
{
	/*
	 * Explicitly load jvm.dll.
	 */
	HKEY rootKey = HKEY_LOCAL_MACHINE;
	char currVer[16];
	HKEY myKey;
	DWORD readType;
	DWORD dwRead = 0;
	DWORD hsize = sizeof(dwRead);
	hsize = sizeof(currVer);
	HMODULE exeMod = GetModuleHandle(NULL);
	size_t const appPathsize = 512;
	LPTSTR appPath = new TCHAR[appPathsize];
	GetModuleFileName(exeMod, appPath, appPathsize);
	size_t appLen = strlen(appPath);
	if (appLen > 0)  // Shouldn't be 0 as the following would be an infinite loop
	{
		for (size_t i = appLen - 1; i > 0; i--)
		{
			if (appPath[i] == '\\')
			{
				appPath[i + 1] = 0;
				break;
			}
		}
	}

	// See if we've got a JVM in our own directory to load
	size_t const includedJREsize = 1024;
	TCHAR includedJRE[includedJREsize];
	strcpy_s(includedJRE, includedJREsize, appPath);
	strcat_s(includedJRE, includedJREsize, "jre\\bin\\client\\jvm.dll");
	HMODULE hLib = LoadLibrary(includedJRE);

	if (hLib == NULL)
	{
		// Failed to find JRE in SageTV directory, load the JVM from the registry instead, by using the Windows Registry to locate the current version to use.

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
		int const pathKeysize = 1024;
		char pathKey[pathKeysize];
		strcpy_s(pathKey, pathKeysize, "Software\\JavaSoft\\Java Runtime Environment\\");
		strcat_s(pathKey, pathKeysize, currVer);
		int const jvmPathsize = 1024;
		char jvmPath[jvmPathsize];
		if (RegOpenKeyEx(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			JVM_MISSING;
			return FALSE;
		}
		hsize = jvmPathsize;
		if (RegQueryValueEx(myKey, "RuntimeLib", 0, &readType, (LPBYTE)jvmPath, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			JVM_MISSING;
			return FALSE;
		}
		RegCloseKey(myKey);

		hLib = LoadLibrary(jvmPath);
		if (hLib == NULL)
		{
			errorMsg("Could not find jvm.dll.\nPlease install Java Runtime Environment 1.7 or higher (Java 1.8 preferred)", "Java Missing");
			return FALSE;
		}
	}

	// Retrieve address of JNI_CreateJavaVM()
	typedef  jint (JNICALL *P_JNI_CreateJavaVM)
		(JavaVM **pvm, void** penv, void *args);
	
	P_JNI_CreateJavaVM createJavaVM = (P_JNI_CreateJavaVM) 
		GetProcAddress(hLib, "JNI_CreateJavaVM");
	
	if (createJavaVM==NULL)
	{
		errorMsg("Could not execute jvm.dll.\nPlease install Java Runtime Environment 1.7 or higher (Java 1.8 preferred)", "Java VM Creation Failed");
		return FALSE;
	}

	// Set the current working directory to be the folder the EXE is in.
	_chdir(appPath);

	// Set up the JAVA VM
	int const jarPathsize = 1024; // Define the size of jarPath
	int const libraryPathsize = 512; // Define the size of libraryPath
	LPTSTR jarPath = new TCHAR[jarPathsize];
	LPTSTR libraryPath = new TCHAR[libraryPathsize];
	
    JNIEnv *env;       /* pointer to native method interface */
	JavaVMInitArgs vm_args;
	JavaVMOption options[32];
	vm_args.nOptions = 0;
	strcpy_s(jarPath, jarPathsize, "-Djava.class.path=");
	strcat_s(jarPath, jarPathsize, appPath);

#ifdef SAGE_TV_LITE
	strcat_s(jarPath, jarPathsize, "SageLite.jar");
#else
	strcat_s(jarPath, jarPathsize, "Sage.jar");
#endif
	options[vm_args.nOptions++].optionString = jarPath;
	strcpy_s(libraryPath, libraryPathsize, "-Djava.library.path=");
	strcat_s(libraryPath, libraryPathsize, appPath);
	options[vm_args.nOptions++].optionString = libraryPath;  /* set native library path */

	vm_args.version = JNI_VERSION_1_6;
	vm_args.options = options;
	vm_args.ignoreUnrecognized = true;

	int res = (*createJavaVM)(&vm, (void**) &env, &vm_args); 
	if (res != 0)
	{
		errorMsg("Could not create JVM.\nPlease reinstall Java Runtime Environment 1.7 or higher (Java 1.8 preferred)", "Java VM Creation Failed");
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
		size_t errStrlen = strlen(cThrowStr) + 64;  // Need to know this size for the sprintf_s
		sprintf_s(errStr, errStrlen, "An exception occured in Java:\n%s", cThrowStr);
		errorMsg(errStr, "Java Exception");
		delete [] errStr;
		env->ReleaseStringUTFChars(throwStr, cThrowStr);
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

