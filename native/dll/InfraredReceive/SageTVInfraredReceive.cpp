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
#include "SageTVInfraredReceive.h"
#include "../../include/sage_SageTVInfraredReceive.h"
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "../../include/StreamMachine/ksProptyUtl.h"
#include "../../include/AVerMedia/averapi.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
#include "../../../third_party/USB-UIRT/uuirtdrv.h"

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

typedef BOOL (*lpfnPV_ReadPicByte)(BYTE *pValue);

/*
 * Class:     sage_SageTVInfraredReceive
 * Method:    pvIRPortThread0
 * Signature: (Lsage/SageTVInputCallback;)V
 */
JNIEXPORT void JNICALL Java_sage_SageTVInfraredReceive_pvIRPortThread0
  (JNIEnv *env, jobject jo, jobject router)
{
	BYTE value;
	HMODULE pvLib = LoadLibrary("ksProptyUtl.dll");
	if (!pvLib)
		return;
	lpfnPV_ReadPicByte readPicByte = (lpfnPV_ReadPicByte)GetProcAddress(pvLib, "PV_ReadPicByte");
	if (!readPicByte)
		return;
	slog((env, "PV IR Receive Native Thread is running...\r\n"));
	BOOL readRes;
	jclass rtrClass = env->GetObjectClass(router);
	jmethodID irMeth = env->GetMethodID(rtrClass, "recvInfrared", "([B)V");
	for (;;)
	{
		// Sync our calls with the MMC ops because we're using the same hardware
		env->MonitorEnter(jo);
		readRes = readPicByte(&value);
		env->MonitorExit(jo);
		if(readRes && value != 0xaa)
		{
			// Process data if any
			jbyteArray ja = env->NewByteArray(1);
			jbyte jb[1];
			jb[0] = value;
			env->SetByteArrayRegion(ja, 0, 1, jb);
			env->CallVoidMethod(router, irMeth, ja);
			continue;
		}
		Sleep(50);
	}
}

/*
 * Class:     sage_SageTVInfraredReceive
 * Method:    dvcrIRPortThread0
 * Signature: (Lsage/SageTVInputCallback;)V
 */
JNIEXPORT void JNICALL Java_sage_SageTVInfraredReceive_dvcrIRPortThread0
  (JNIEnv *env, jobject jo, jobject router)
{
	HANDLE hFile; 
	char buffer[64]="";
	DWORD dwCount = 0;
	DWORD key;

	hFile = CreateFile("\\\\.\\tar",           // open DVCR device
					GENERIC_READ,              // open for reading 
					FILE_SHARE_READ,           // share for reading 
					NULL,                      // no security 
					OPEN_EXISTING,             // existing file only 
					FILE_ATTRIBUTE_NORMAL,     // normal file 
					NULL);						// no attr. template 

	if (hFile == INVALID_HANDLE_VALUE)
	{
		slog((env, "Error opening handle to VBDVCR device\r\n"));
		return;
	}
	slog((env, "VBDVCR IR Receive Native Thread is running...\r\n"));

	jclass rtrClass = env->GetObjectClass(router);
	jmethodID irMeth = env->GetMethodID(rtrClass, "recvInfrared", "([B)V");
	while (TRUE)
	{		// do forever...

		Sleep(30);		// check every 30ms for button press
		
		// check for button press
		env->MonitorEnter(jo);
		DeviceIoControl(hFile, 0x222008, NULL,0, &key, 4,&dwCount,NULL);
		env->MonitorExit(jo);
		
		if (dwCount != 0) 
		{			// button has been pressed

			key = key & 0x0000f0ff;		// filter out extra stuff (timestamp?)
			slog((env, "VBDVCR IR Receive of %d\r\n", key));
			jbyteArray ja = env->NewByteArray(2);
			jbyte jb[2];
			jb[0] = (jbyte)((key & 0xFF00) >> 8);
			jb[1] = (jbyte) (key & 0xFF);
			env->SetByteArrayRegion(ja, 0, 2, jb);
			env->CallVoidMethod(router, irMeth, ja);
			Sleep(100);  // pause after button press (debounce)
		}
	}
}

typedef __declspec(dllimport) int (_cdecl *lpfnAVER_HWInit)();
typedef __declspec(dllimport) BOOL (_cdecl *lpfnAVER_GetRemoteData)(BYTE *);
/*
 * Class:     sage_SageTVInfraredReceive
 * Method:    avrIRPortThread0
 * Signature: (Lsage/SageTVInputCallback;)V
 */
JNIEXPORT void JNICALL Java_sage_SageTVInfraredReceive_avrIRPortThread0
  (JNIEnv *env, jobject jo, jobject router)
{
	BYTE value;
	HMODULE averLib = LoadLibrary("averapi_182.dll");
	if (!averLib)
		return;
	lpfnAVER_HWInit hwinit = (lpfnAVER_HWInit)GetProcAddress(averLib, "AVER_HWInit");
	if (!hwinit)
		return;
	lpfnAVER_GetRemoteData getRemote = (lpfnAVER_GetRemoteData)GetProcAddress(averLib, "AVER_GetRemoteData");
	if (!getRemote)
		return;
	int initRes = hwinit();
	slog((env, "AverMedia IR Receive Native Thread is running...init=%d\r\n", initRes));
	//if (!initRes)
	//	return;
	BOOL readRes;
	jclass rtrClass = env->GetObjectClass(router);
	jmethodID irMeth = env->GetMethodID(rtrClass, "recvInfrared", "([B)V");
	for (;;)
	{
		Sleep(50);

		// Sync our calls with the MMC ops because we're using the same hardware
		env->MonitorEnter(jo);
		readRes = getRemote(&value);
		env->MonitorExit(jo);
		//slog((env, "Remote res=%d val=0x%x\r\n", readRes, value));
		if (!readRes)
		{
			// We need to reinit the hw because it got cleared from some other thread
			hwinit();
		}
		if(readRes && value)
		{
			// Process data if any
			jbyteArray ja = env->NewByteArray(1);
			jbyte jb[1];
			jb[0] = value;
			env->SetByteArrayRegion(ja, 0, 1, jb);
			env->CallVoidMethod(router, irMeth, ja);
			continue;
		}
	}
}

/*
 * Class:     sage_SageTVInfraredReceive
 * Method:    usbuirtRecvPortInit0
 * Signature: (Lsage/SageTVInputCallback;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_SageTVInfraredReceive_usbuirtRecvPortInit0
  (JNIEnv *env, jobject jo, jobject router)
{
	if (!setupUIRTDriver())
		return JNI_FALSE;

	// USB-UIRT, setup the callback
	setupUIRTReceiveCallback(env->NewGlobalRef(router));

	return JNI_TRUE;
}

/*
 * Class:     sage_SageTVInfraredReceive
 * Method:    closeUsbuirtRecvPort0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_SageTVInfraredReceive_closeUsbuirtRecvPort0
  (JNIEnv *env, jobject jo)
{
	killUIRTDriver();
}

