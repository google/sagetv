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

 /*
 *       ******************************************************************************************
 *       Microsoft Visual Studio project codepage intentionally set to MBCS
 *       The strings passed in the JavaVMOption struct use the platform default character encoding,
 *       so they can't be passed as 16-bit Unicode chars.
 *
 *       *******************************************************************************************
 */

#include <windows.h>
#include <stdlib.h>
#include <jni.h>
#include <stdio.h>
#include <direct.h>

#define JVM_MISSING MessageBox(NULL, "Could not get information on current JVM.\nPlease install Java Runtime Environment 1.7 or higher (Java 1.8 preferred)", "Java Missing", MB_OK);\

LRESULT CALLBACK WndProc( HWND hWnd, UINT messg,
								WPARAM wParam, LPARAM lParam );

typedef JNIEXPORT jboolean (JNICALL *LPFNSetupHooks)(JNIEnv*, jclass, jlong);

static JNIEnv* globalenv = 0;

JavaVM *vm;    

void sysOutPrint(const char* cstr, ...)
{
	JNIEnv* env;
	jint threadState = vm->GetEnv((void**)&env, JNI_VERSION_1_2); // JNI in JRE 1.2 and greater
	if (threadState == JNI_EDETACHED)
		vm->AttachCurrentThread((void**)&env, NULL);
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    vsprintf_s(buf, sizeof(buf), cstr, args);
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
    vsprintf_s(buf, sizeof(buf), cstr, args);
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
	size_t errStrlen = env->GetStringLength(throwStr) + 64;
	char *errStr = (char*)malloc(errStrlen);
	if (errStr != nullptr)  // Should never be NULL
	{
		sprintf_s(errStr, errStrlen, "An exception occured in Java:\n%s", cThrowStr);
		errorMsg(errStr, errTitle);
		free(errStr);
	}
	env->ReleaseStringUTFChars(throwStr, cThrowStr);
}


int APIENTRY WinMain(HINSTANCE hInstance,
                     HINSTANCE hPrevInstance,
                     LPSTR     lpCmdLine,
                     int       nCmdShow)
{
	WNDCLASS wc;
	if( !hPrevInstance )			/*set up window class and register it */
	{
		wc.lpszClassName 	= "SageApp";
		wc.hInstance 		= hInstance;
		wc.lpfnWndProc		= WndProc;
		wc.hCursor			= LoadCursor( NULL, IDC_ARROW );
		wc.hIcon			= LoadIcon( NULL, IDI_APPLICATION );
		wc.lpszMenuName		= NULL;
		wc.hbrBackground	= (HBRUSH)GetStockObject( WHITE_BRUSH );
		wc.style			= 0;
		wc.cbClsExtra		= 0;
		wc.cbWndExtra		= 0;

		if( !RegisterClass( &wc ) )
			return FALSE;
	}
	HWND hWnd = CreateWindow( "SageApp",
							"SageWin",
					WS_OVERLAPPEDWINDOW | WS_CAPTION | WS_CLIPCHILDREN,
							CW_USEDEFAULT,
							CW_USEDEFAULT,
							CW_USEDEFAULT,
							CW_USEDEFAULT,
							(HWND)NULL,
							(HMENU)NULL,
							(HINSTANCE)hInstance,
							(LPSTR)NULL		);

	/*
	* Explicitly load jvm.dll.
	*/
	char appPath[_MAX_PATH];
	GetModuleFileName(NULL, appPath, sizeof(appPath));
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
	char includedJRE[_MAX_PATH];
	strcpy_s(includedJRE, sizeof(includedJRE), appPath);
	strcat_s(includedJRE, sizeof(includedJRE), "jre\\bin\\client\\jvm.dll");
	HMODULE hLib = LoadLibrary(includedJRE);

	if (hLib == NULL)
	{
		// Failed to find JRE in SageTV directory, load the JVM from the registry instead, by using the Windows Registry to locate the current version to use.

		HKEY rootKey = HKEY_LOCAL_MACHINE;
		char currVer[16];
		HKEY myKey;  // myKey to NULL for RegQueryValueEx
		DWORD readType;  // readType to NULL for RegQueryValueEx
		DWORD hsize = sizeof(currVer);

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
		strcpy_s(pathKey, sizeof(pathKey), "Software\\JavaSoft\\Java Runtime Environment\\");
		strcat_s(pathKey, sizeof(pathKey), currVer);
		if (RegOpenKeyEx(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			JVM_MISSING;
			return FALSE;
		}
		char jvmPath[_MAX_PATH];
		hsize = sizeof(jvmPath);
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

	if (createJavaVM == NULL)
	{
		errorMsg("Could not execute jvm.dll.\nPlease install Java Runtime Environment 1.7 or higher (Java 1.8 preferred)", "Java VM Creation Failed");
		return FALSE;
	}

	// Set the current working directory to be the folder the EXE is in.
	errno_t err;
	if ((err = _chdir(appPath)) != 0) // If this returns 0 we have bigger problems
	{
		errorMsg("Could not set the working directory", "Change to working directory failed");
		return FALSE;
	}

	// Set up the JAVA VM
	char jarPath[_MAX_PATH];
	char libraryPath[_MAX_PATH];

	JNIEnv *env;       /* pointer to native method interface */
	JavaVMInitArgs vm_args;
	JavaVMOption options[32];
	vm_args.nOptions = 0;
	strcpy_s(jarPath, sizeof(jarPath), "-Djava.class.path=");
	strcat_s(jarPath, sizeof(jarPath), appPath);
	strcat_s(jarPath, sizeof(jarPath), "MiniClient.jar");
	options[vm_args.nOptions++].optionString = jarPath;
	strcpy_s(libraryPath, sizeof(libraryPath), "-Djava.library.path=");
	strcat_s(libraryPath, sizeof(libraryPath), appPath);
	options[vm_args.nOptions++].optionString = libraryPath;  /* set native library path */

	// With the new animation surfaces that increaese mem usage by about 30MB so we need to bump it
	// up another 30 above the default of 64
	char memString[32];
	strcpy_s(memString, sizeof(memString), "-Xmx128m");
	options[vm_args.nOptions++].optionString = memString;

	vm_args.version = JNI_VERSION_1_2;
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
    jclass cls = env->FindClass("sage/miniclient/MiniClient");
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
		jstring throwStr = (jstring)env->CallObjectMethod(mainThrow, toStr);
		const char* cThrowStr = env->GetStringUTFChars(throwStr, 0);
		size_t errStrlen = env->GetStringLength(throwStr) + 64;
		char *errStr = (char*)malloc(errStrlen);
		if (errStr != nullptr)  // Should never be NULL
		{
			sprintf_s(errStr, errStrlen, "An exception occured in Java:\n%s", cThrowStr);
			errorMsg(errStr, "Java Exception");
			free(errStr);
		}
		env->ReleaseStringUTFChars(throwStr, cThrowStr);
		return FALSE;
	}

	jclass miniConnClass = env->FindClass("sage/miniclient/MiniClientConnection");
	if (env->ExceptionCheck())
	{
		errorMsg("Could not find required class.\nPlease reinstall the SageTV application", "Java Error");
		return FALSE;
	}
	jfieldID currConnField = env->GetStaticFieldID(miniConnClass, "currConnection", "Lsage/miniclient/MiniClientConnection;");
	if (env->ExceptionCheck())
	{
		errorMsg("Could not find required class.\nPlease reinstall the SageTV application", "Java Error");
		return FALSE;
	}
	jmethodID recvCmdMeth = env->GetMethodID(miniConnClass, "postSageCommandEvent", "(I)V");
	if (env->ExceptionCheck())
	{
		errorMsg("Could not find required class.\nPlease reinstall the SageTV application", "Java Error");
		return FALSE;
	}

	// Now setup the shell hooks so the MCE remote control works
	HMODULE sageLib = LoadLibrary("SageTVWin32.dll");
	if (sageLib)
	{
#ifdef _WIN64
		LPFNSetupHooks hookProc = (LPFNSetupHooks)GetProcAddress(sageLib, "Java_sage_Sage_setupSystemHooks0");
#else
		LPFNSetupHooks hookProc = (LPFNSetupHooks)GetProcAddress(sageLib, "_Java_sage_Sage_setupSystemHooks0@16");
#endif
		if (hookProc)
		{
			hookProc(env, 0, (jlong)hWnd);
		}
	}

    MSG lpMsg;
	BOOL bRet;
	while((bRet = GetMessage( &lpMsg, NULL, 0, 0 )) != 0)					/* begin the message loop */
	{
		//		sysOutPrint(env, "Kick! msg=%d lparam=%d wParam=%d\r\n", lpMsg.message - 0x400,
//			lpMsg.lParam, lpMsg.wParam);
		if (bRet == -1)
		{
			// Error receiving the message
		}
		else if (lpMsg.message - 0x400 == 234 && recvCmdMeth)
		{
			jobject currConn = env->GetStaticObjectField(miniConnClass, currConnField);
			if (currConn)
			{
				env->CallVoidMethod(currConn, recvCmdMeth, (jint) lpMsg.lParam);
				if (env->ExceptionOccurred())
				{
					sysOutPrint(env, "Got exception during native callback 234\n");
					env->ExceptionClear();
				}
			}
		}
		else
		{
			TranslateMessage( &lpMsg );
			DispatchMessage( &lpMsg );
		}

	}

	FreeLibrary(hLib);
	return 0;
}

LRESULT CALLBACK WndProc( HWND hWnd, UINT messg,				/*callback procedure */
								WPARAM wParam, LPARAM lParam )
{
	switch(messg)
	{
		case WM_DESTROY:
			PostQuitMessage( 0 );
			break;

		case (WM_USER + 26):
		case (WM_USER + 234):
		case (WM_USER + 669):
			PostMessage(hWnd, messg, wParam, lParam);
			break;
		default:
			return( DefWindowProc( hWnd, messg, wParam, lParam ) );
	}

	return( 0L );
}

