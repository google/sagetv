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
#include "../../include/sage_Sage.h"
#include "../../include/sage_UIManager.h"
#include "../../include/sage_UIUtils.h"
#include "../../include/sage_DirectX9SageRenderer.h"
#include "../../include/sage_UserEvent.h"
#include <wininet.h>
#include <shellapi.h>
#pragma pack(push, 16)  // align JAWT data struct to DLL call
#pragma warning(disable:28159) // Static Code Analysis: When we stop supporting XP we can use GetTickCount64()

#include "jawt.h"
#include "jawt_md.h"

static HINSTANCE sageLoadedAwtLib = NULL;

/*
 * Class:     sage_Sage
 * Method:    postMessage0
 * Signature: (JIII)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_postMessage0(JNIEnv *env, jclass jo,
	jlong winID, jint msg, jint param1, jint param2)
{
	PostMessage((HWND) winID, (UINT) msg, (WPARAM) param1, (LPARAM) param2);
}

/*
 * Class:     sage_Sage
 * Method:    println0
 * Signature: (I, Ljava/lang/String;)Z
 */
JNIEXPORT void JNICALL Java_sage_Sage_println0(JNIEnv *env, jclass jc, jlong handle,
	jstring s)
{
	const jchar* x = env->GetStringChars(s, NULL);
	int len = env->GetStringLength(s);
	DWORD numWrit = 0;
	WriteConsoleW((HANDLE) handle, x, len, &numWrit, NULL);
	//WriteFile((HANDLE) handle, x, len, &numWrit, NULL);
	env->ReleaseStringChars(s, x);
}

/*
 * Class:     sage_Sage
 * Method:    getDiskFreeSpace0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_Sage_getDiskFreeSpace0(
	JNIEnv *env, jclass jc, jstring jdisk)
{
	const char* disk = env->GetStringUTFChars(jdisk, NULL);
	jlong availToMe = 0;
	jlong total = 0;
	jlong avail = 0;
	jboolean rv = GetDiskFreeSpaceEx(disk, (ULARGE_INTEGER*)&availToMe,
		(ULARGE_INTEGER*)&total, (ULARGE_INTEGER*)&avail);
	env->ReleaseStringUTFChars(jdisk, disk);
	return availToMe;
}

/*
 * Class:     sage_Sage
 * Method:    getDiskTotalSpace0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_Sage_getDiskTotalSpace0(
	JNIEnv *env, jclass jc, jstring jdisk)
{
	const char* disk = env->GetStringUTFChars(jdisk, NULL);
	jlong availToMe = 0;
	jlong total = 0;
	jlong avail = 0;
	jboolean rv = GetDiskFreeSpaceEx(disk, (ULARGE_INTEGER*)&availToMe,
		(ULARGE_INTEGER*)&total, (ULARGE_INTEGER*)&avail);
	env->ReleaseStringUTFChars(jdisk, disk);
	return total;
}

/*
 * Class:     sage_Sage
 * Method:    connectToInternet0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sage_Sage_connectToInternet0(JNIEnv *env, jclass jc)
{
	DWORD dwConnectionTypes = INTERNET_CONNECTION_LAN |
		INTERNET_CONNECTION_MODEM |
		INTERNET_CONNECTION_PROXY;
	if (!InternetGetConnectedState(&dwConnectionTypes, 0))
	{
		slog((env, "Internet connection not present, autodialing...\r\n"));
		if (InternetAutodial(INTERNET_AUTODIAL_FORCE_UNATTENDED, 0))
			return sage_Sage_DID_CONNECT;
		else
			return sage_Sage_CONNECT_ERR;
	}
	return 0;
}

/*
 * Class:     sage_Sage
 * Method:    disconnectInternet0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_Sage_disconnectInternet0(JNIEnv *env, jclass jc)
{
	InternetAutodialHangup(0);
}

/*
 * Class:     sage_Sage
 * Method:    readStringValue
 * Signature: (ILjava/lang/String;Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_Sage_readStringValue(JNIEnv *env,
	jclass myClass, jint root, jstring key, jstring valueName)
{
	const char* keyString = env->GetStringUTFChars(key, 0);
	const char* valueNameString = env->GetStringUTFChars(valueName, 0);
	HKEY rootKey = 0;
	char holder[1024];
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(holder);
	jstring rv = NULL;
	if (root == sage_Sage_HKEY_CLASSES_ROOT) rootKey = HKEY_CLASSES_ROOT;
	else if (root == sage_Sage_HKEY_CURRENT_CONFIG) rootKey = HKEY_CURRENT_CONFIG;
	else if (root == sage_Sage_HKEY_CURRENT_USER) rootKey = HKEY_CURRENT_USER;
	else if (root == sage_Sage_HKEY_LOCAL_MACHINE) rootKey = HKEY_LOCAL_MACHINE;
	else if (root == sage_Sage_HKEY_USERS) rootKey = HKEY_USERS;

	if (rootKey && RegOpenKeyEx(rootKey, keyString, 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, valueNameString, 0, &readType, (LPBYTE)holder, &hsize) == ERROR_SUCCESS)
		{
			rv = env->NewStringUTF(holder);
		}
		RegCloseKey(myKey);
	}
	
	env->ReleaseStringUTFChars(key, keyString);
	env->ReleaseStringUTFChars(valueName, valueNameString);
	return rv;
}

/*
 * Class:     sage_Sage
 * Method:    readDwordValue
 * Signature: (ILjava/lang/String;Ljava/lang/String;)I;
 */
JNIEXPORT jint JNICALL Java_sage_Sage_readDwordValue(JNIEnv *env,
	jclass myClass, jint root, jstring key, jstring valueName)
{
	const char* keyString = env->GetStringUTFChars(key, 0);
	const char* valueNameString = env->GetStringUTFChars(valueName, 0);
	HKEY rootKey = 0;
	DWORD holder = 0;
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(holder);
	if (root == sage_Sage_HKEY_CLASSES_ROOT) rootKey = HKEY_CLASSES_ROOT;
	else if (root == sage_Sage_HKEY_CURRENT_CONFIG) rootKey = HKEY_CURRENT_CONFIG;
	else if (root == sage_Sage_HKEY_CURRENT_USER) rootKey = HKEY_CURRENT_USER;
	else if (root == sage_Sage_HKEY_LOCAL_MACHINE) rootKey = HKEY_LOCAL_MACHINE;
	else if (root == sage_Sage_HKEY_USERS) rootKey = HKEY_USERS;

	if (rootKey && RegOpenKeyEx(rootKey, keyString, 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		RegQueryValueEx(myKey, valueNameString, 0, &readType, (LPBYTE) &holder, &hsize);
		RegCloseKey(myKey);
	}
	env->ReleaseStringUTFChars(key, keyString);
	env->ReleaseStringUTFChars(valueName, valueNameString);
	return (jint) holder;
}

/*
 * Class:     sage_Sage
 * Method:    writeStringValue
 * Signature: (ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_Sage_writeStringValue(JNIEnv *env,
	jclass myClass, jint root, jstring key, jstring valueName, jstring value)
{
	const char* keyString = env->GetStringUTFChars(key, 0);
	const char* valueNameString = env->GetStringUTFChars(valueName, 0);
	const char* valueString = env->GetStringUTFChars(value, 0);
	HKEY rootKey = NULL;
	HKEY myKey;
	jboolean rv = JNI_FALSE;
	if (root == sage_Sage_HKEY_CLASSES_ROOT) rootKey = HKEY_CLASSES_ROOT;
	else if (root == sage_Sage_HKEY_CURRENT_CONFIG) rootKey = HKEY_CURRENT_CONFIG;
	else if (root == sage_Sage_HKEY_CURRENT_USER) rootKey = HKEY_CURRENT_USER;
	else if (root == sage_Sage_HKEY_LOCAL_MACHINE) rootKey = HKEY_LOCAL_MACHINE;
	else if (root == sage_Sage_HKEY_USERS) rootKey = HKEY_USERS;

	if (rootKey && RegCreateKeyEx(rootKey, keyString, 0, 0, REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS,
		0, &myKey, 0) == ERROR_SUCCESS)
	{
		if (RegSetValueEx(myKey, valueNameString, 0, REG_SZ, (LPBYTE) valueString,
			env->GetStringLength(value) + 1) == ERROR_SUCCESS)
		{
			rv = JNI_TRUE;
		}
		RegCloseKey(myKey);
	}

	env->ReleaseStringUTFChars(key, keyString);
	env->ReleaseStringUTFChars(valueName, valueNameString);
	env->ReleaseStringUTFChars(value, valueString);
	return rv;
}

/*
 * Class:     sage_Sage
 * Method:    writeDwordValue
 * Signature: (ILjava/lang/String;Ljava/lang/String;I)Z
 */
jboolean JNICALL Java_sage_Sage_writeDwordValue(JNIEnv *env,
	jclass myClass, jint root, jstring key, jstring valueName, jint value)
{
	const char* keyString = env->GetStringUTFChars(key, 0);
	const char* valueNameString = env->GetStringUTFChars(valueName, 0);
	HKEY rootKey = NULL;
	DWORD dvalue = value;
	HKEY myKey;
	jboolean rv = JNI_FALSE;
	if (root == sage_Sage_HKEY_CLASSES_ROOT) rootKey = HKEY_CLASSES_ROOT;
	else if (root == sage_Sage_HKEY_CURRENT_CONFIG) rootKey = HKEY_CURRENT_CONFIG;
	else if (root == sage_Sage_HKEY_CURRENT_USER) rootKey = HKEY_CURRENT_USER;
	else if (root == sage_Sage_HKEY_LOCAL_MACHINE) rootKey = HKEY_LOCAL_MACHINE;
	else if (root == sage_Sage_HKEY_USERS) rootKey = HKEY_USERS;

	if (rootKey && RegCreateKeyEx(rootKey, keyString, 0, 0, REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS,
		0, &myKey, 0) == ERROR_SUCCESS)
	{
		if (RegSetValueEx(myKey, valueNameString, 0, REG_DWORD, (LPBYTE) &dvalue,
			sizeof(dvalue)) == ERROR_SUCCESS)
		{
			rv = JNI_TRUE;
		}
		RegCloseKey(myKey);
	}
	env->ReleaseStringUTFChars(key, keyString);
	env->ReleaseStringUTFChars(valueName, valueNameString);
	return rv;
}

/*
 * Class:     sage_Sage
 * Method:    removeRegistryValue
 * Signature: (ILjava/lang/String;Ljava/lang/String;)Z
 */
jboolean JNICALL Java_sage_Sage_removeRegistryValue(JNIEnv *env,
	jclass myClass, jint root, jstring key, jstring valueName)
{
	const char* keyString = env->GetStringUTFChars(key, 0);
	const char* valueNameString = env->GetStringUTFChars(valueName, 0);
	HKEY rootKey;
	rootKey = NULL;
	HKEY myKey;
	jboolean rv = JNI_FALSE;
	if (root == sage_Sage_HKEY_CLASSES_ROOT) rootKey = HKEY_CLASSES_ROOT;
	else if (root == sage_Sage_HKEY_CURRENT_CONFIG) rootKey = HKEY_CURRENT_CONFIG;
	else if (root == sage_Sage_HKEY_CURRENT_USER) rootKey = HKEY_CURRENT_USER;
	else if (root == sage_Sage_HKEY_LOCAL_MACHINE) rootKey = HKEY_LOCAL_MACHINE;
	else if (root == sage_Sage_HKEY_USERS) rootKey = HKEY_USERS;

	if (rootKey && RegOpenKeyEx(rootKey, keyString, 0, KEY_ALL_ACCESS, &myKey) == ERROR_SUCCESS)
	{
		rv = (RegDeleteValue(myKey, valueNameString) == ERROR_SUCCESS);
		RegCloseKey(myKey);
	}
	env->ReleaseStringUTFChars(key, keyString);
	env->ReleaseStringUTFChars(valueName, valueNameString);
	return rv;
}

/*
 * Class:     sage_Sage
 * Method:    getRegistryNames
 * Signature: (ILjava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_Sage_getRegistryNames(JNIEnv *env, jclass jc, 
															jint root, jstring key)
{
	const char* keyString = env->GetStringUTFChars(key, 0);
	static jclass strClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/String"));
	static jclass vecClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/Vector"));
	HKEY rootKey;
	HKEY myKey;
	LONG resErr;
	if (root == sage_Sage_HKEY_CLASSES_ROOT) rootKey = HKEY_CLASSES_ROOT;
	else if (root == sage_Sage_HKEY_CURRENT_CONFIG) rootKey = HKEY_CURRENT_CONFIG;
	else if (root == sage_Sage_HKEY_CURRENT_USER) rootKey = HKEY_CURRENT_USER;
	else if (root == sage_Sage_HKEY_LOCAL_MACHINE) rootKey = HKEY_LOCAL_MACHINE;
	else if (root == sage_Sage_HKEY_USERS) rootKey = HKEY_USERS;
	else
	{
		env->ReleaseStringUTFChars(key, keyString);
		return env->NewObjectArray(0, strClass, NULL);
	}
	if ((resErr = RegOpenKeyEx(rootKey, keyString, 0, KEY_ALL_ACCESS, &myKey)) != ERROR_SUCCESS)
	{
		env->ReleaseStringUTFChars(key, keyString);
		return env->NewObjectArray(0, strClass, NULL);
	}

	static jmethodID vecConstMeth = env->GetMethodID(vecClass, "<init>", "()V");
	static jmethodID vecAddMeth = env->GetMethodID(vecClass, "addElement", "(Ljava/lang/Object;)V");
	static jmethodID vecToArrayMeth  = env->GetMethodID(vecClass, "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;");
	static jmethodID vecSizeMeth  = env->GetMethodID(vecClass, "size", "()I");
	jobject vec = env->NewObject(vecClass, vecConstMeth);

	DWORD idx = 0;
	DWORD dwName = 512;
	LPTSTR pName = new TCHAR[dwName];
	LONG res = RegEnumValue(myKey, idx, pName, &dwName, NULL, NULL, NULL, NULL);
	while (res == ERROR_SUCCESS)
	{
		env->CallVoidMethod(vec, vecAddMeth, env->NewStringUTF(pName));
		idx++;
		dwName = 512;
		res = RegEnumValue(myKey, idx, pName, &dwName, NULL, NULL, NULL, NULL);
	}

	delete [] pName;
	
	RegCloseKey(myKey);
	env->ReleaseStringUTFChars(key, keyString);
	return (jobjectArray) env->CallObjectMethod(vec, vecToArrayMeth,
		env->NewObjectArray(env->CallIntMethod(vec, vecSizeMeth), strClass, NULL));
}

/*
 * Class:     sage_Sage
 * Method:    getRegistrySubkeys
 * Signature: (ILjava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_Sage_getRegistrySubkeys(JNIEnv *env, jclass jc, 
															jint root, jstring key)
{
	const char* keyString = env->GetStringUTFChars(key, 0);
	static jclass strClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/String"));
	static jclass vecClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/Vector"));
	HKEY rootKey;
	HKEY myKey;
	LONG resErr;
	if (root == sage_Sage_HKEY_CLASSES_ROOT) rootKey = HKEY_CLASSES_ROOT;
	else if (root == sage_Sage_HKEY_CURRENT_CONFIG) rootKey = HKEY_CURRENT_CONFIG;
	else if (root == sage_Sage_HKEY_CURRENT_USER) rootKey = HKEY_CURRENT_USER;
	else if (root == sage_Sage_HKEY_LOCAL_MACHINE) rootKey = HKEY_LOCAL_MACHINE;
	else if (root == sage_Sage_HKEY_USERS) rootKey = HKEY_USERS;
	else
	{
		env->ReleaseStringUTFChars(key, keyString);
		return env->NewObjectArray(0, strClass, NULL);
	}
	if ((resErr = RegOpenKeyEx(rootKey, keyString, 0, KEY_ALL_ACCESS, &myKey)) != ERROR_SUCCESS)
	{
		env->ReleaseStringUTFChars(key, keyString);
		return env->NewObjectArray(0, strClass, NULL);
	}

	static jmethodID vecConstMeth = env->GetMethodID(vecClass, "<init>", "()V");
	static jmethodID vecAddMeth = env->GetMethodID(vecClass, "addElement", "(Ljava/lang/Object;)V");
	static jmethodID vecToArrayMeth  = env->GetMethodID(vecClass, "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;");
	static jmethodID vecSizeMeth  = env->GetMethodID(vecClass, "size", "()I");
	jobject vec = env->NewObject(vecClass, vecConstMeth);

	DWORD idx = 0;
	DWORD dwName = 512;
	LPTSTR pName = new TCHAR[dwName];
	FILETIME fTime;
	LONG res = RegEnumKeyEx(myKey, idx, pName, &dwName, NULL, NULL, NULL, &fTime);
	while (res == ERROR_SUCCESS)
	{
		env->CallVoidMethod(vec, vecAddMeth, env->NewStringUTF(pName));
		idx++;
		dwName = 512;
		res = RegEnumKeyEx(myKey, idx, pName, &dwName, NULL, NULL, NULL, &fTime);
	}

	delete [] pName;
	
	RegCloseKey(myKey);
	env->ReleaseStringUTFChars(key, keyString);
	return (jobjectArray) env->CallObjectMethod(vec, vecToArrayMeth,
		env->NewObjectArray(env->CallIntMethod(vec, vecSizeMeth), strClass, NULL));
}

/*
 * Class:     sage_Sage
 * Method:    getFileSystemType
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_Sage_getFileSystemType(JNIEnv *env, jclass jc, jstring volRoot)
{
	const char* rootStr = env->GetStringUTFChars(volRoot, 0);
	DWORD maxNameLen;
	DWORD fsFlags;
	TCHAR daBuf[64];
	jstring rv;
	if (GetVolumeInformation(rootStr, NULL, 0, NULL, &maxNameLen, &fsFlags, daBuf, 64))
		rv = env->NewStringUTF(daBuf);
	else
		rv = NULL;
	env->ReleaseStringUTFChars(volRoot, rootStr);
	return rv;
}

/*
 * Class:     sage_Sage
 * Method:    getFileSystemIdentifier
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_sage_Sage_getFileSystemIdentifier(JNIEnv *env, jclass jc, jstring volRoot)
{
	const char* rootStr = env->GetStringUTFChars(volRoot, 0);
	DWORD maxNameLen;
	DWORD fsFlags;
	DWORD rv = 0;
	if (!GetVolumeInformation(rootStr, NULL, 0, &rv, &maxNameLen, &fsFlags, NULL, 0))
	{
		GetVolumeInformation(NULL, NULL, 0, &rv, &maxNameLen, &fsFlags, NULL, 0);
	}
	env->ReleaseStringUTFChars(volRoot, rootStr);
	return (jint) rv;
}

/*
 * Class:     sage_Sage
 * Method:    setSystemTime
 * Signature: (IIIIIII)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_setSystemTime(JNIEnv *env, jclass jc, jint year, jint month,
													jint day, jint hour, jint minute, jint second, jint millisecond)
{
	SYSTEMTIME sysTime;
	sysTime.wYear = (WORD) year;
	sysTime.wMonth = (WORD) month;
	sysTime.wDay = (WORD) day;
	sysTime.wHour = (WORD) hour;
	sysTime.wMinute = (WORD) minute;
	sysTime.wSecond = (WORD) second;
	sysTime.wMilliseconds = (WORD) millisecond;
	SetLocalTime(&sysTime);
}

/*
 * Class:     sage_Sage
 * Method:    getScreenArea0
 * Signature: ()Ljava/awt/Rectangle;
 */
JNIEXPORT jobject JNICALL Java_sage_Sage_getScreenArea0(JNIEnv *env, jclass jc)
{
	RECT rRW;
	if (SystemParametersInfo(SPI_GETWORKAREA, 0, (PVOID) &rRW, 0) != TRUE) 
	{
		rRW.bottom = rRW.left = rRW.right = rRW.top = 0;
	}
   APPBARDATA            abd;
   HWND                  hwndAutoHide;
 
   /* Find out if the Windows taskbar is set to auto-hide */
   ZeroMemory (&abd, sizeof (abd));
   abd.cbSize = sizeof (abd);
   if (SHAppBarMessage (ABM_GETSTATE, &abd) & ABS_AUTOHIDE)
   {
		slog((env, "winAdjustForAutoHide - Taskbar is auto hide\r\n"));
		
		/* Obtain the task bar window dimensions */
		hwndAutoHide = (HWND) SHAppBarMessage (ABM_GETTASKBARPOS, &abd);
		int taskwidth = abd.rc.right - abd.rc.left;
		int taskheight = abd.rc.bottom - abd.rc.top;

		/* Look for a TOP auto-hide taskbar */
		abd.uEdge = ABE_TOP;
		hwndAutoHide = (HWND) SHAppBarMessage (ABM_GETAUTOHIDEBAR, &abd);
		if (hwndAutoHide != NULL)
		{
			rRW.top += taskheight;
		}

		/* Look for a LEFT auto-hide taskbar */
		abd.uEdge = ABE_LEFT;
		hwndAutoHide = (HWND) SHAppBarMessage (ABM_GETAUTOHIDEBAR, &abd);
		if (hwndAutoHide != NULL)
		{
			rRW.left += taskwidth;
		}

		/* Look for a BOTTOM auto-hide taskbar */
		abd.uEdge = ABE_BOTTOM;
		hwndAutoHide = (HWND) SHAppBarMessage (ABM_GETAUTOHIDEBAR, &abd);
		if (hwndAutoHide != NULL)
		{
			rRW.bottom -= taskheight;
		}
		/* Look for a RIGHT auto-hide taskbar */
		abd.uEdge = ABE_RIGHT;
		hwndAutoHide = (HWND) SHAppBarMessage (ABM_GETAUTOHIDEBAR, &abd);
		if (hwndAutoHide != NULL)
		{
			rRW.right -= taskwidth;
		}
   }

	static jclass rectClass = (jclass) env->NewGlobalRef(env->FindClass("java/awt/Rectangle"));
	static jmethodID rectConst = env->GetMethodID(rectClass, "<init>",
		"(IIII)V");
	jobject myRect = env->NewObject(rectClass, rectConst, (jint)rRW.left,
		(jint)rRW.top, (jint)(rRW.right - rRW.left), (jint)(rRW.bottom - rRW.top));

	return myRect;
}


jboolean schedulerHasConflicts(JNIEnv *env)
{
	static jclass schedClass = (jclass) env->NewGlobalRef(env->FindClass("sage/Scheduler"));
	if (schedClass)
	{
		static jmethodID schedInstMeth = env->GetStaticMethodID(schedClass, "getInstance", "()Lsage/Scheduler;");
		if (schedInstMeth)
		{
			jobject schedobj = env->CallStaticObjectMethod(schedClass, schedInstMeth);
			if (schedobj)
			{
				static jmethodID hasConfMeth = env->GetMethodID(schedClass, "areThereDontKnows", "()Z");
				if (hasConfMeth)
				{
					return env->CallBooleanMethod(schedobj, hasConfMeth);
				}
			}
		}
	}
	env->ExceptionClear();
	return JNI_FALSE;
}

/*
 * Class:     sage_Sage
 * Method:    addTaskbarIcon0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_addTaskbarIcon0(JNIEnv *env, jclass jc, jlong jhwnd)
{
	NOTIFYICONDATA iconData;
	memset(&iconData, 0, sizeof(iconData));
	iconData.cbSize = sizeof(iconData);
	iconData.hWnd = (HWND) jhwnd;
	iconData.uID = 110;
	iconData.uFlags = NIF_ICON | NIF_TIP | NIF_MESSAGE;
	iconData.uCallbackMessage = 0x400 + 26;

	WORD iconID = schedulerHasConflicts(env) ? 111 : 110;
	iconData.hIcon = LoadIcon(GetModuleHandle(NULL), MAKEINTRESOURCE(iconID));
	static jclass sagetvClass = (jclass) env->NewGlobalRef(env->FindClass("sage/SageTV"));
	env->ExceptionClear();
	strcpy_s(iconData.szTip, sizeof(iconData.szTip), sagetvClass ? "SageTV" : "SageRecorder");
	BOOL res = Shell_NotifyIcon(NIM_ADD, &iconData);
}

/*
 * Class:     sage_Sage
 * Method:    updateTaskbarIcon0
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_updateTaskbarIcon0(JNIEnv *env, jclass jc, jlong jhwnd, jstring tipText)
{
	try
	{
		NOTIFYICONDATAW iconData;
		memset(&iconData, 0, sizeof(iconData));
		iconData.cbSize = sizeof(iconData);
		iconData.hWnd = (HWND) jhwnd;
		iconData.uID = 110;
		iconData.uFlags = NIF_TIP | NIF_ICON;

		const jchar* ctip = env->GetStringChars(tipText, NULL);
		wcsncpy_s(iconData.szTip, _countof(iconData.szTip), (const wchar_t*)ctip, _countof(iconData.szTip) - 1 );
		WORD iconID = schedulerHasConflicts(env) ? 111 : 110;
		iconData.hIcon = LoadIcon(GetModuleHandle(NULL), MAKEINTRESOURCE(iconID));
		BOOL res = Shell_NotifyIconW(NIM_MODIFY, &iconData);
		env->ReleaseStringChars(tipText, ctip);
	}
	catch (...)
	{
		elog((env, "ERROR updating taskbar icon\r\n"));
	}
}

/*
 * Class:     sage_Sage
 * Method:    removeTaskbarIcon0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_removeTaskbarIcon0(JNIEnv *env, jclass jc, jlong jhwnd)
{
	NOTIFYICONDATA iconData;
	memset(&iconData, 0, sizeof(iconData));
	iconData.cbSize = sizeof(iconData);
	iconData.hWnd = (HWND) jhwnd;
	iconData.uID = 110;
	BOOL res = Shell_NotifyIcon(NIM_DELETE, &iconData);
}

/*
 * Class:     sage_Sage
 * Method:    getCpuResolution
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_Sage_getCpuResolution
  (JNIEnv *env, jclass jc)
{
	jlong rv;
	QueryPerformanceFrequency((LARGE_INTEGER*)&rv);
	return rv;
}

/*
 * Class:     sage_Sage
 * Method:    getCpuTime
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_Sage_getCpuTime
  (JNIEnv *env, jclass jc)
{
	jlong rv;
	QueryPerformanceCounter((LARGE_INTEGER*)&rv);
	return rv;
}

static UINT dss_GetList[] = {SPI_GETLOWPOWERTIMEOUT, 
    SPI_GETPOWEROFFTIMEOUT, SPI_GETSCREENSAVETIMEOUT};
static UINT dss_SetList[] = {SPI_SETLOWPOWERTIMEOUT, 
    SPI_SETPOWEROFFTIMEOUT, SPI_SETSCREENSAVETIMEOUT};
static const int dss_ListCount = _countof(dss_GetList);

/*
 * Class:     sage_Sage
 * Method:    enableSSAndPM
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sage_Sage_enableSSAndPM(JNIEnv *env, jclass jc, jboolean x)
{
	static bool disabledSSandPM = false;
	static int m_pValue[dss_ListCount];
	if (x)
	{
		if (disabledSSandPM)
		{
			slog((env, "Enabling screensaver and powermanagement.\r\n"));
			for (int x=0;x<dss_ListCount;x++)
			{
				// Set the old value
				SystemParametersInfo (dss_SetList[x], 
					m_pValue[x], NULL, 0);
			}
		}
		disabledSSandPM = false;
	}
	else if (!disabledSSandPM)
	{
		disabledSSandPM = true;
		slog((env, "Disabling screensaver and powermanagement.\r\n"));
		for (int x=0;x<dss_ListCount;x++)
		{
			// Get the current value
			SystemParametersInfo (dss_GetList[x], 0, 
				&m_pValue[x], 0);

			// Turn off the parameter
			SystemParametersInfo (dss_SetList[x], 0, 
				NULL, 0);
		}
	}
}

#define FT2INT64(ft) \
  (((jlong)(ft).dwHighDateTime << 32 | (jlong)(ft).dwLowDateTime) / 10000)

/*
 * Class:     sage_Sage
 * Method:    getEventTime0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_Sage_getEventTime0(JNIEnv *env, jclass jc)
{
//    DWORD event_offset = GetMessageTime();
//    if (event_offset <= 0) {
//        event_offset = GetTickCount();
//    }
	// NARFLEX - I have no idea why this was using GetMessageTime; but that causes the value to
	// vary between threads which is very, very bad so we don't use that at all anymore.
	DWORD event_offset = GetTickCount();
    // All computations and stored values are in milliseconds. The Win32
    // FILETIME structure is in 100s of nanoseconds, so the FT2INT64 macro
    // divides by 10^4. (10^7 / 10^4 = 10^3).
    //
    // UTC time is milliseconds since January 1, 1970.
    // Windows system time is 100s of nanoseconds since January 1, 1601.
    // Windows event time is milliseconds since boot, but wraps every 49.7
    // days because it is only a DWORD.

    static const jlong WRAP_TIME_MILLIS = (jlong)((DWORD)-1);
    static jlong boot_time_utc = 0;
    static jlong boot_time_1601 = 0;
    static jlong utc_epoch_1601 = 0;

    if (utc_epoch_1601 == 0) {
        SYSTEMTIME sys_epoch_1601;
        FILETIME file_epoch_1601;
        
        memset(&sys_epoch_1601, 0, sizeof(SYSTEMTIME));
        sys_epoch_1601.wYear = 1970;
        sys_epoch_1601.wMonth = 1;
        sys_epoch_1601.wDay = 1;

        SystemTimeToFileTime(&sys_epoch_1601, &file_epoch_1601);
        utc_epoch_1601 = FT2INT64(file_epoch_1601);
    }

    SYSTEMTIME current_sys_time_1601;
    FILETIME current_file_time_1601;
    jlong current_time_1601;

    GetSystemTime(&current_sys_time_1601);
    SystemTimeToFileTime(&current_sys_time_1601, &current_file_time_1601);
    current_time_1601 = FT2INT64(current_file_time_1601);

    if ((current_time_1601 - boot_time_1601) > WRAP_TIME_MILLIS) {
        // Need to reset boot time
        DWORD since_boot_millis = GetTickCount();
        boot_time_1601 = current_time_1601 - since_boot_millis;
        boot_time_utc = boot_time_1601 - utc_epoch_1601;
    }

    return boot_time_utc + event_offset;
}

/*
 * Class:     sage_UIManager
 * Method:    setCompoundWindowRegion
 * Signature: (J[Ljava/awt/Rectangle;[IZ)V
 */
JNIEXPORT void JNICALL Java_sage_UIManager_setCompoundWindowRegion(JNIEnv *env, jclass jc,
																   jlong winID, jobjectArray jrects, jintArray roundness,
																   jboolean dontRepaint)
{
	HWND hwnd = (HWND) winID;
	HRGN compRgn = NULL;
	jint arrayLen;
	if (jrects == NULL || ((arrayLen = env->GetArrayLength(jrects)) == 0))
	{
		if (!SetWindowRgn(hwnd, CreateRectRgn(0,0,0,0), TRUE))
		{
			slog((env, "SetWindowRgn failed with %d\r\n", GetLastError()));
		}
		return;
	}
	
	jint* nativeRound = env->GetIntArrayElements(roundness, NULL);
	static jclass rectClass = (jclass) env->NewGlobalRef(env->FindClass("java/awt/Rectangle"));
	static jfieldID rectX = env->GetFieldID(rectClass, "x", "I");
	static jfieldID rectY = env->GetFieldID(rectClass, "y", "I");
	static jfieldID rectW = env->GetFieldID(rectClass, "width", "I");
	static jfieldID rectH = env->GetFieldID(rectClass, "height", "I");
	for (int i = 0; i < arrayLen; i++)
	{
		jobject currRect = env->GetObjectArrayElement(jrects, i);
		jint currX, currY;
		currX = env->GetIntField(currRect, rectX);
		currY = env->GetIntField(currRect, rectY);
		HRGN currRegn;
		if (nativeRound[i])
		{
			currRegn = CreateRoundRectRgn(currX, currY, currX + env->GetIntField(currRect, rectW),
				currY + env->GetIntField(currRect, rectH), nativeRound[i], nativeRound[i]);
		}
		else
		{
			currRegn = CreateRectRgn(currX, currY, currX + env->GetIntField(currRect, rectW),
				currY + env->GetIntField(currRect, rectH));
		}
		if (!currRegn)
		{
			slog((env, "CreateRectRgn failed with %d\r\n", GetLastError()));
		}
		if (arrayLen == 1)
		{
			// This used to be TRUE, but I think that's the double flashy cause
			// when the OSD is being displayed cause it triggers a second repaint
			if (!SetWindowRgn(hwnd, currRegn, !dontRepaint))
			{
				slog((env, "SetWindowRgn 2 failed with %d\r\n", GetLastError()));
			}
			env->ReleaseIntArrayElements(roundness, nativeRound, NULL);
			return;
		}
		if (i == 0)
		{
			compRgn = currRegn;
		}
		else
		{
			if (!CombineRgn(compRgn, compRgn, currRegn, RGN_OR))
			{
				slog((env, "CombineRgn 2 failed %d\r\n", GetLastError()));
			}
			if (currRegn != NULL)
				DeleteObject(currRegn);
		}
	}
	// This used to be TRUE, but I think that's the double flashy cause
	// when the OSD is being displayed cause it triggers a second repaint
	if (!SetWindowRgn(hwnd, compRgn, !dontRepaint))
	{
		slog((env, "SetWindowRgn 3 failed with %d\r\n", GetLastError()));
	}
	env->ReleaseIntArrayElements(roundness, nativeRound, NULL);
}

/*
 * Class:     sage_UIManager
 * Method:    clearWindowRegion
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_UIManager_clearWindowRegion(JNIEnv *env, jclass jc, jlong winID)
{
	HWND hwnd = (HWND) winID;
	//RECT grc;
    //GetClientRect(hwnd, &grc);
	// This used to be true, again I think it only would cause double repaints
	// NOTE: When repaints are enabled, they get sent back up to Java as a
	// system repaint event.
	SetWindowRgn(hwnd, NULL/*CreateRectRgnIndirect(&grc)*/, FALSE);
}

/*
 * Class:     sage_UIManager
 * Method:    setCursorClip
 * Signature: (Ljava/awt/Rectangle;)V
 */
JNIEXPORT void JNICALL Java_sage_UIManager_setCursorClip(
	JNIEnv *env, jclass jc, jobject inRect)
{
	RECT myClip;
	static jclass rectClass = (jclass) env->NewGlobalRef(env->FindClass("java/awt/Rectangle"));
	static jfieldID rectX = env->GetFieldID(rectClass, "x", "I");
	static jfieldID rectY = env->GetFieldID(rectClass, "y", "I");
	static jfieldID rectW = env->GetFieldID(rectClass, "width", "I");
	static jfieldID rectH = env->GetFieldID(rectClass, "height", "I");
	if (inRect)
	{
		LONG width = (LONG) env->GetIntField(inRect, rectW);
		LONG height = (LONG) env->GetIntField(inRect, rectH);
		myClip.left = (LONG) env->GetIntField(inRect, rectX);
		myClip.top = (LONG) env->GetIntField(inRect, rectY);
		myClip.right = myClip.left + width;
		myClip.bottom = myClip.top + height;
		ClipCursor(&myClip);
	}
	else
		ClipCursor(NULL);
}

/*
 * Class:     sage_UIManager
 * Method:    sendMessage
 * Signature: (JII)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_UIManager_sendMessage(
	JNIEnv *env, jclass jc, jlong winID, jint msgID, jint msgData)
{
	DWORD_PTR msgRes;
	if (SendMessageTimeout((HWND) winID, msgID + WM_USER, msgData, msgData,
		SMTO_ABORTIFHUNG | SMTO_BLOCK, 15000, &msgRes) == 0)
		return JNI_FALSE;
	else
		return JNI_TRUE;
}

/*
 * Class:     sage_UIManager
 * Method:    findWindow
 * Signature: (Ljava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_UIManager_findWindow(
	JNIEnv *env, jclass jc, jstring jname, jstring jwinclass)
{
	jlong retVal;
	const char* name = env->GetStringUTFChars(jname, (unsigned char*) NULL);
	if (jwinclass == NULL)
	{
		retVal = (jlong) FindWindow((const char*) NULL, name);
	}
	else
	{
		const char* winClass = env->GetStringUTFChars(jwinclass, (unsigned char*) NULL);
		retVal = (jlong) FindWindow(winClass, name);
		env->ReleaseStringUTFChars(jwinclass, winClass);
	}
	env->ReleaseStringUTFChars(jname, name);
	return retVal;
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    hasDirectX90
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_hasDirectX90
  (JNIEnv *env, jclass jc)
{
	TCHAR szPath[_MAX_PATH];
	TCHAR szFile[_MAX_PATH];
	if (GetSystemDirectory(szPath, sizeof(szPath)) != 0)
	{
		strncpy_s(szFile, sizeof(szFile), szPath, sizeof(szFile)-1);
		strcat_s(szFile, sizeof(szFile), TEXT("\\d3d9.dll"));
		HANDLE fooHand = CreateFile(szFile, 0, 0, NULL, OPEN_EXISTING, 0, NULL);
		if (fooHand == INVALID_HANDLE_VALUE)
		{
			return JNI_FALSE;
		}
		else
		{
			CloseHandle(fooHand);
			return JNI_TRUE;
		}
	}
	return JNI_FALSE;
}

void loadAWTLib()
{
	if (!sageLoadedAwtLib)
	{
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
		strcat_s(includedJRE, sizeof(includedJRE), "jre\\bin\\jawt.dll");
		sageLoadedAwtLib = LoadLibrary(includedJRE);
		if (sageLoadedAwtLib)
			return;
		/*
		 * Failed to find JRE in SageTV directory, load jawt.dll by using the Windows Registry to locate the current version to use.
		 */
		HKEY rootKey = HKEY_LOCAL_MACHINE;
		char currVer[16];
		HKEY myKey;
		DWORD readType;
		DWORD hsize = sizeof(currVer);
		if (RegOpenKeyEx(rootKey, "Software\\JavaSoft\\Java Runtime Environment", 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return;
		}
		
		if (RegQueryValueEx(myKey, "CurrentVersion", 0, &readType, (LPBYTE)currVer, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return;
		}
		RegCloseKey(myKey);
		char pathKey[1024];
		strcpy_s(pathKey, sizeof(pathKey), "Software\\JavaSoft\\Java Runtime Environment\\");
		strcat_s(pathKey, sizeof(pathKey), currVer);
		if (RegOpenKeyEx(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return;
		}
		char jvmPath[_MAX_PATH];
		hsize = sizeof(jvmPath);
		if (RegQueryValueEx(myKey, "RuntimeLib", 0, &readType, (LPBYTE)jvmPath, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return;
		}
		RegCloseKey(myKey);

		// Go to the 2nd to last backslash, and append jawt.dll from there to complete the string
		char* goodSlash = strrchr(jvmPath, '\\');
		if (!goodSlash) return;
		*goodSlash = 0;
		goodSlash = strrchr(jvmPath, '\\');
		if (!goodSlash) return;
		strcpy_s(jvmPath, sizeof(jvmPath), "jawt.dll");

		sageLoadedAwtLib = LoadLibrary(jvmPath);
	}
}

/*
 * Class:     sage_UIUtils
 * Method:    getHWND
 * Signature: (Ljava/awt/Canvas;)J
 */
JNIEXPORT jlong JNICALL Java_sage_UIUtils_getHWND
	(JNIEnv *env, jclass jc, jobject canvas)
{
	JAWT awt;
	JAWT_DrawingSurface* ds = NULL;
	JAWT_DrawingSurfaceInfo* dsi = NULL;
	JAWT_Win32DrawingSurfaceInfo* dsi_win = NULL;
	jboolean result;
	jint lock;

	env->MonitorEnter(canvas);

	loadAWTLib();

	// Get the AWT, we have to explicitly load it otherwise it'll try it load it
	// when we execute and the link will fail.
	typedef jboolean (JNICALL *AWTPROC)(JNIEnv* env, JAWT* awt);
	
	awt.version = JAWT_VERSION_1_4;
#ifdef _WIN64
	AWTPROC lpfnProc = (AWTPROC)GetProcAddress(sageLoadedAwtLib, "JAWT_GetAWT");
#else
	AWTPROC lpfnProc = (AWTPROC)GetProcAddress(sageLoadedAwtLib, "_JAWT_GetAWT@8");
#endif
	result = lpfnProc(env, &awt);
	if (result == JNI_FALSE)
	{
		slog((env, "Failed loading JAWT lib\r\n"));
		env->MonitorExit(canvas);
		return 0;
	}

	awt.Lock(env);

	// Get the drawing surface
	ds = awt.GetDrawingSurface(env, canvas);
	if (ds == NULL)
	{
		slog((env, "Failed getting drawing surface for AWT\r\n"));
		env->MonitorExit(canvas);
		awt.Unlock(env);
		return 0;
	}

	// Lock the drawing surface
	lock = ds->Lock(ds);
	if ((lock & JAWT_LOCK_ERROR) != 0)
	{
		slog((env, "Failed locking the drawing surface for AWT\r\n"));
		// Free the drawing surface
		awt.FreeDrawingSurface(ds);
		env->MonitorExit(canvas);
		awt.Unlock(env);
		return 0;
	}

	// Get the drawing surface info
	dsi = ds->GetDrawingSurfaceInfo(ds);
	if (dsi == NULL)
	{
		slog((env, "Failed getting dsi for AWT\r\n"));
		// Unlock the drawing surface
		ds->Unlock(ds);
		// Free the drawing surface
		awt.FreeDrawingSurface(ds);
		env->MonitorExit(canvas);
		awt.Unlock(env);
		return 0;
	}

	// Get the platform-specific drawing info
	dsi_win = (JAWT_Win32DrawingSurfaceInfo*)dsi->platformInfo;

	jlong rv = (jlong) dsi_win->hwnd;

	// Free the drawing surface info
	ds->FreeDrawingSurfaceInfo(dsi);

	// Unlock the drawing surface
	ds->Unlock(ds);

	// Free the drawing surface
	awt.FreeDrawingSurface(ds);

	env->MonitorExit(canvas);
	awt.Unlock(env);
	return rv;
}


/*
 * Class:     sage_UIManager
 * Method:    setAlwaysOnTop
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL Java_sage_UIManager_setAlwaysOnTop
	(JNIEnv *env, jclass jc, jlong winID, jboolean state)
{
	HWND winWin = (HWND) winID;
	HWND parentWin = GetParent(winWin);
	while (parentWin)
	{
		winWin = parentWin;
		parentWin = GetParent(winWin);
	}
	if (state == JNI_TRUE)
	{
		SetWindowPos(winWin, HWND_TOPMOST, 0, 0, 0, 0,
			SWP_NOMOVE | SWP_NOSIZE);
	}
	else
	{
		SetWindowPos(winWin, HWND_NOTOPMOST , 0, 0, 0, 0,
			SWP_NOMOVE | SWP_NOSIZE);
	}
}

/*
 * Class:     sage_UIManager
 * Method:    setAppTaskbarState0
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL Java_sage_UIManager_setAppTaskbarState0
  (JNIEnv *env, jclass jc, jlong winID, jboolean visible)
{
	HWND winWin = (HWND) winID;
	HWND parentWin = GetParent(winWin);
	while (parentWin)
	{
		winWin = parentWin;
		parentWin = GetParent(winWin);
	}
	ShowWindow(winWin, SW_HIDE);
	if (visible == JNI_FALSE)
	{
		SetWindowLong(winWin, GWL_EXSTYLE, (GetWindowLong(winWin, GWL_EXSTYLE) | WS_EX_TOOLWINDOW) & ~WS_EX_APPWINDOW);
	}
	else
	{
		SetWindowLong(winWin, GWL_EXSTYLE, (GetWindowLong(winWin, GWL_EXSTYLE) | WS_EX_APPWINDOW) & ~WS_EX_TOOLWINDOW);
	}
	ShowWindow(winWin, SW_SHOW);
}

/*
 * Class:     sage_UIManager
 * Method:    getCursorPosX0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sage_UIManager_getCursorPosX0
  (JNIEnv *env, jclass jc)
{
	POINT mousePos;
	GetCursorPos(&mousePos);
	return mousePos.x;
}

/*
 * Class:     sage_UIManager
 * Method:    getCursorPosY0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sage_UIManager_getCursorPosY0
  (JNIEnv *env, jclass jc)
{
	POINT mousePos;
	GetCursorPos(&mousePos);
	return mousePos.y;
}

static char _buff1[32]="ZQZQZQ";  //ZQ, protection buffer for testing a crash fix, 4/13/06
static HANDLE hMutexMessaging = NULL;
static HINSTANCE hShellHookLib = NULL;
static HINSTANCE hRawInputLib = NULL;
static HINSTANCE hKeybaordLib = NULL;

void CloseRawInputProxyWnd( HANDLE hHandle );
HANDLE OpenRawInputProxyWnd(HWND hWnd) ;
static HANDLE _hRawInputHandle = NULL;

/*

/*
 * Class:     sage_Sage
 * Method:    setupSystemHooks0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_Sage_setupSystemHooks0
  (JNIEnv *env, jclass jc, jlong jhwnd)
{
	BOOL ret = TRUE;
	HKEY hregkey;
	DWORD dwWaitResult;
	char* regkey = "SOFTWARE\\Frey Technologies\\Common";

	if (!jhwnd ) return JNI_FALSE;
	hMutexMessaging = CreateMutex(NULL, TRUE, "Global\\SageTVMessagingSink");
	if ( hMutexMessaging == NULL ) 
	{
		slog((env, "setup system shell hook failed (lock can't be open)\r\n"));
		return JNI_FALSE;
	}

	dwWaitResult = WaitForSingleObject(  hMutexMessaging, 8000L );   // five-second time-out interval
 	if ( WAIT_OBJECT_0 != dwWaitResult )
	{
		slog((env, "setup system shell hook failed (object is locked)\r\n"));
		CloseHandle( hMutexMessaging );
		return JNI_FALSE;
	}

	typedef BOOL (__cdecl *HOOKINSTALLPROC)(HWND hWnd);
	HOOKINSTALLPROC lpfnProc;
	BOOL hookRes=FALSE;

	BOOL Win32ShellHookEnable = TRUE;

	if ( RegOpenKeyEx(HKEY_LOCAL_MACHINE, regkey, 0, KEY_READ, &hregkey) == ERROR_SUCCESS )
	{
		DWORD hType;
		DWORD dwEnable;
		DWORD hSize = sizeof(dwEnable);
		ret = RegQueryValueEx( hregkey, "Win32ShellHookEnable", 0, &hType, (LPBYTE)&dwEnable, &hSize);
		if ( !ret )
		{
			Win32ShellHookEnable = dwEnable;
			elog((env, "specify Win32ShellHookEnable Enable %d in registry\r\n", Win32ShellHookEnable ));
		} 
		RegCloseKey( hregkey );
	}

	if ( Win32ShellHookEnable )
	{
		// Get the address of the hook install function and call it
		hShellHookLib = LoadLibrary("Win32ShellHook");
		if (!hShellHookLib)
		{
			elog((env, "ERROR Unable to load shell hook library\r\n"));
			CloseHandle( hMutexMessaging );
			hMutexMessaging = NULL;
			return JNI_FALSE;
		} else
		{
			lpfnProc = (HOOKINSTALLPROC)GetProcAddress(hShellHookLib, "InstallShellHook");
			if (!lpfnProc)
			{
				elog((env, "ERROR Finding InstallShellHook procedure.\r\n"));
				ret = JNI_FALSE;
			} else
			{
				hookRes = lpfnProc((HWND) jhwnd);

				// Check that it worked
				if (hookRes)
				{
					slog((env, "Successfully setup system shell hook\r\n"));
				}
				else
				{
					// The hook failed
					elog((env, "Failed setting up system shell hook\r\n"));
					ret = JNI_FALSE;
				}
			}
		}
	} else
	{
		elog((env, "Win32ShellHookEnable is Disabled\r\n" ));
	}

	//ZQ
	//check OS if support RegisterRawInputDevices(), (Win2K doesn't)
	BOOL WinRawInputEnable = FALSE;
	HINSTANCE hUser32Lib = GetModuleHandle( "USER32" );
	if ( hUser32Lib )
	{
		lpfnProc = (HOOKINSTALLPROC)GetProcAddress(hUser32Lib, "RegisterRawInputDevices");
		if ( lpfnProc )
		{
			if ( RegOpenKeyEx(HKEY_LOCAL_MACHINE, regkey, 0, KEY_READ, &hregkey) == ERROR_SUCCESS )
			{
				DWORD hType;
				DWORD dwEnable;
				DWORD hSize = sizeof(dwEnable);
				ret = RegQueryValueEx( hregkey, "WinRawInputHookEnable", 0, &hType, (LPBYTE)&dwEnable, &hSize);
				if ( !ret )
				{
					WinRawInputEnable = dwEnable;
					elog((env, "specify RawInputHook Enable %d in registry\r\n", WinRawInputEnable ));
				} 
				RegCloseKey( hregkey );
			}
		}
	}
	
	if ( WinRawInputEnable )
	{
		hRawInputLib = LoadLibrary("WinRawInput");

		if (!hRawInputLib)
		{
			elog((env, "ERROR Unable to load winRawinput library\r\n"));
			ret = JNI_FALSE;
		} else
		{
			lpfnProc = (HOOKINSTALLPROC)GetProcAddress(hRawInputLib, "InstallWinRawInput");
			if (!lpfnProc)
			{
				elog((env, "ERROR Finding InstallWinRawInput procedure.\r\n"));
				ret = JNI_FALSE;
			} else
			{
				hookRes = lpfnProc((HWND) jhwnd);
				if (hookRes)
				{
					slog((env, "Successfully setup win raw input\r\n" ));
				}
				else
				{
					// The hook failed
					elog((env, "Failed setting up win raw input\r\n"));
					ret = JNI_FALSE;
				}
			}
		}
	}

	char KeyboardHookName[100]={0};
	if ( RegOpenKeyEx(HKEY_LOCAL_MACHINE, regkey, 0, KEY_READ, &hregkey) == ERROR_SUCCESS )
	{
		DWORD hType;
		DWORD hSize = sizeof(KeyboardHookName);
		ret = RegQueryValueEx( hregkey, "WinKeyboardHook", 0, &hType, (LPBYTE)KeyboardHookName, &hSize);
		if ( ret )
		{
			//strcpy( KeyboardHookName, "WinKeyboardHook" );
			elog((env, "no specific WinkeyboardHook in registry, load default one\r\n"));
		} else
		{
			elog((env, "load specific WinkeyboardHook in registry '%s'\r\n", KeyboardHookName ));
		}
		RegCloseKey( hregkey );
	}

	if ( KeyboardHookName[0] )
	{
		hKeybaordLib= LoadLibrary(KeyboardHookName);
		if (!hKeybaordLib)
		{
			elog((env, "ERROR Unable to load WinKeyboardHook library:%s\r\n", KeyboardHookName ));
			ret = JNI_FALSE;
		} else
		{
			lpfnProc = (HOOKINSTALLPROC)GetProcAddress(hKeybaordLib, "InstallWinKeyboardHook");
			if (!lpfnProc)
			{
				elog((env, "ERROR Finding InstallWinKeyboardHook procedure.\r\n"));
				ret = JNI_FALSE;
			}else
			{
				hookRes = lpfnProc((HWND) jhwnd);
				if (hookRes)
				{
					slog((env, "Successfully setup WinKeyboard hook\r\n"));
				}
				else
				{
					// The hook failed
					elog((env, "Failed setting up WinKeyboard hook\r\n"));
					ret = JNI_FALSE;
				}
			}
		}
	}

	ReleaseMutex(hMutexMessaging);
	CloseHandle( hMutexMessaging );
	hMutexMessaging = NULL;
	return ret;


}

/*
 * Class:     sage_Sage
 * Method:    releaseSystemHooks0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_Sage_releaseSystemHooks0
	(JNIEnv *env, jclass jc, jlong jhwnd)
{
	jboolean rv = JNI_FALSE;
	DWORD dwWaitResult;

	if ( hMutexMessaging == NULL )
	{
		hMutexMessaging = CreateMutex(NULL, FALSE, "Global\\SageTVMessagingSink");
		if ( hMutexMessaging == NULL  )	
		{
			slog((env, "Removing system shell hook failed (lock can't be open)\r\n"));
			return JNI_FALSE;
		}
		dwWaitResult = WaitForSingleObject(  hMutexMessaging, 8000L );   // five-second time-out interval
	 	if ( WAIT_OBJECT_0 != dwWaitResult )
		{
			slog((env, "Removing system shell hook failed (object is locked)\r\n"));
			CloseHandle( hMutexMessaging );
			return JNI_FALSE;
		}
	}

	if ( hShellHookLib )
	{	
		typedef BOOL (__cdecl *HOOKREMOVEPROC)(HWND hWnd);
		HOOKREMOVEPROC lpfnProc = (HOOKREMOVEPROC)GetProcAddress(hShellHookLib, "RemoveShellHook");
		if (!lpfnProc)
		{
			elog((env, "ERROR Finding RemoveShellHook procedure.\r\n"));
		}
		else
		{
			BOOL hookRes = lpfnProc((HWND) jhwnd);
			slog((env, "Removed system shell hook\r\n"));
			rv = JNI_TRUE;
		}

		FreeLibrary( hShellHookLib );
		hShellHookLib = NULL;
	}

	if ( hRawInputLib )
	{
		typedef BOOL (__cdecl *HOOKREMOVEPROC)();
		HOOKREMOVEPROC lpfnProc = (HOOKREMOVEPROC)GetProcAddress(hRawInputLib, "RemoveWinRawInput");
		if (!lpfnProc)
		{
			elog((env, "ERROR Finding RemoveWinRawInput procedure.\r\n"));
		}
		else
		{
			BOOL hookRes = lpfnProc();
			slog((env, "Removed WinRawInput\r\n"));
			rv = JNI_TRUE;
		}
		FreeLibrary( hRawInputLib );
		hRawInputLib= NULL;
	}

	if ( hKeybaordLib )
	{
		typedef BOOL (__cdecl *HOOKREMOVEPROC)();
		HOOKREMOVEPROC lpfnProc = (HOOKREMOVEPROC)GetProcAddress(hKeybaordLib, "RemoveWinKeyboardHook");
		if (!lpfnProc)
		{
			elog((env, "ERROR Finding RemoveWinKeyboard procedure.\r\n"));
		}
		else
		{
			BOOL hookRes = lpfnProc();
			slog((env, "Removed WinRinkeyboard\r\n"));
			rv = JNI_TRUE;
		}
		FreeLibrary( hKeybaordLib );
		hKeybaordLib = NULL;
	}

	ReleaseMutex(hMutexMessaging);
	CloseHandle( hMutexMessaging );
	hMutexMessaging = NULL;
	return rv;
}

typedef DWORD (STDAPICALLTYPE *lpfnWNetGetConnectionA)(
    IN  LPCSTR,
	OUT LPSTR,
	LPDWORD
    );
typedef DWORD (STDAPICALLTYPE *lpfnWNetCancelConnection2A)(
    IN  LPCSTR,
	IN DWORD,
	IN BOOL
    );
	
typedef DWORD (STDAPICALLTYPE *lpfnWNetAddConnection2A)(
    IN  NETRESOURCE*,
	IN LPCSTR,
	IN LPCSTR,
	IN DWORD
    );
/*
 * Class:     sage_Sage
 * Method:    fixFailedWinDriveMappings
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_Sage_fixFailedWinDriveMappings
  (JNIEnv *env, jclass jc)
{
	char lwszSysErr[512];
	DWORD dw_syserr = sizeof(lwszSysErr);
	lpfnWNetGetConnectionA myWNetGetConnectionA = NULL;
	lpfnWNetCancelConnection2A myWNetCancelConnectionA = NULL;
	lpfnWNetAddConnection2A myWNetAddConnectionA = NULL;
	HMODULE mprLib = NULL;
	mprLib = LoadLibrary("mpr.dll");
	if (!mprLib)
	{
		slog((env, "ERROR Cannot load mpr.dll!\r\n"));
		return;
	}
	myWNetGetConnectionA = (lpfnWNetGetConnectionA)GetProcAddress(mprLib, "WNetGetConnectionA");
	if (!myWNetGetConnectionA)
	{
		slog((env, "ERROR Cannot find function WNetGetConnectionA in mpr.dll\r\n"));
		FreeLibrary(mprLib);
		return;
	}
	myWNetCancelConnectionA = (lpfnWNetCancelConnection2A)GetProcAddress(mprLib, "WNetCancelConnection2A");
	if (!myWNetCancelConnectionA)
	{
		slog((env, "ERROR Cannot find function WNetCancelConnection2A in mpr.dll\r\n"));
		FreeLibrary(mprLib);
		return;
	}
	myWNetAddConnectionA = (lpfnWNetAddConnection2A)GetProcAddress(mprLib, "WNetAddConnection2A");
	if (!myWNetAddConnectionA)
	{
		slog((env, "ERROR Cannot find function WNetAddConnection2A in mpr.dll\r\n"));
		FreeLibrary(mprLib);
		return;
	}

	// First go through the registry and find all the mapped drives
	HKEY myKey;
	if (RegOpenKeyEx(HKEY_CURRENT_USER, "Network", 0, KEY_ALL_ACCESS, &myKey) == ERROR_SUCCESS)
	{
		DWORD idx = 0;
		DWORD dwName = 512;
		LPTSTR pName = new TCHAR[dwName];
		FILETIME fTime;
		LONG res = RegEnumKeyEx(myKey, idx, pName, &dwName, NULL, NULL, NULL, &fTime);
		while (res == ERROR_SUCCESS)
		{
			slog((env, "Found mapped drive name of: %s\r\n", pName));
			pName[1] = ':';
			pName[2] = 0;
			DWORD dwNameRemote = 512;
			LPTSTR pNameRemote = new TCHAR[dwNameRemote];
			DWORD res2 = myWNetGetConnectionA(pName, pNameRemote, &dwNameRemote);
			if (res2 == ERROR_CONNECTION_UNAVAIL)
			{
				slog((env, "Mapped drive is offline!\r\n"));
				// Disconnect and reconnect the drive.
				res2 = myWNetCancelConnectionA(pName, 0, FALSE);
				if (res2 != NO_ERROR && res2 != ERROR_NOT_CONNECTED)
				{
					FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
						NULL,  res2, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
						(LPTSTR) lwszSysErr, dw_syserr, NULL);
					slog((env, "ERROR cancelling drive mapping of %s\r\n", lwszSysErr));
				}
				else
				{
					NETRESOURCE netrc;
					ZeroMemory(&netrc, sizeof(NETRESOURCE));
					netrc.dwType = RESOURCETYPE_DISK;
					netrc.lpLocalName = pName;
					netrc.lpRemoteName = pNameRemote;
					res2 = myWNetAddConnectionA(&netrc, NULL, NULL, 0);
					if (res2 != NO_ERROR)
					{
						FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
							NULL,  res2, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
							(LPTSTR) lwszSysErr, dw_syserr, NULL);
						slog((env, "ERROR restoring drive mapping of %s\r\n", lwszSysErr));
					}
					else
					{
						slog((env, "Successfully restored connection to mapped drive!\r\n"));
					}
				}
			}
			delete [] pNameRemote;
			idx++;
			dwName = 512;
			res = RegEnumKeyEx(myKey, idx, pName, &dwName, NULL, NULL, NULL, &fTime);
		}

		delete [] pName;
		RegCloseKey(myKey);
	}
	else
	{
		slog((env, "ERROR Cannot open HKCU\\Network key!\r\n"));
	}
	
	if (mprLib)
		FreeLibrary(mprLib);
}

BOOL APIENTRY DllMain( HANDLE hModule, 
                       DWORD  ul_reason_for_call, 
                       LPVOID lpReserved
					 )
{
    switch (ul_reason_for_call)
	{
		case DLL_PROCESS_ATTACH:
		case DLL_THREAD_ATTACH:
		case DLL_THREAD_DETACH:
		case DLL_PROCESS_DETACH:
			break;
    }
    return TRUE;
}

#pragma pack(pop)
