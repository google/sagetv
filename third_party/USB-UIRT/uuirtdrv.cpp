/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// trydrv.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"
#include "uuirtdrv.h"
#include "jni.h"

HINSTANCE		hinstUIRTLib = NULL; 

// Driver handle for UUIRT device
HUUHANDLE		hDrvHandle;

unsigned int	drvVersion;

// Globals to hold last-learned IR-Code and its format...
char	gIRCode[2048] = "0000 0071 0000 0032 0080 0040 0010 0010 0010 0030 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0030 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0030 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0030 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0030 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0010 0030 0010 0aad";
int		gIRCodeFormat = UUIRTDRV_IRFMT_PRONTO;

// Globals used during Learning...
char	gLearnBuffer[2048];
int		gLearnFormat;

// UUIRT .dll funtion pointers. These will be assigned when calling LoadDLL()
pfn_UUIRTOpen				fnUUIRTOpen;
pfn_UUIRTClose				fnUUIRTClose;
pfn_UUIRTGetDrvInfo			fn_UUIRTGetDrvInfo;
pfn_UUIRTGetUUIRTInfo		fn_UUIRTGetUUIRTInfo;
pfn_UUIRTGetUUIRTConfig		fn_UUIRTGetUUIRTConfig;
pfn_UUIRTSetUUIRTConfig		fn_UUIRTSetUUIRTConfig;
pfn_UUIRTSetReceiveCallback	fn_UUIRTSetReceiveCallback;
pfn_UUIRTTransmitIR			fn_UUIRTTransmitIR;
pfn_UUIRTLearnIR			fn_UUIRTLearnIR;

/*****************************************************************************/
/* unLoadDLL: Disconnects from .DLL and unloads it from memory				 */
/*																			 */
/* returns: none															 */
/*																			 */
/*****************************************************************************/
void unLoadUIRTDLL(void)
{
	if (hinstUIRTLib)
		FreeLibrary(hinstUIRTLib);
	hinstUIRTLib = NULL;
}

/*****************************************************************************/
/* loadDLL: Establish contact with the UUIRTDRV dll and assign function      */
/*			entry points													 */
/*																			 */
/* returns: TRUE on success, FALSE on failure								 */
/*																			 */
/*****************************************************************************/
BOOL loadUIRTDLL(void)
{
    // Get a handle to the DLL module.
 
    hinstUIRTLib = LoadLibrary("uuirtdrv"); 
 
    // If the handle is valid, try to get the function address.
 
    if (hinstUIRTLib != NULL) 
    { 
        fnUUIRTOpen = (pfn_UUIRTOpen) GetProcAddress(hinstUIRTLib, "UUIRTOpen");
        fnUUIRTClose = (pfn_UUIRTClose) GetProcAddress(hinstUIRTLib, "UUIRTClose");
		fn_UUIRTGetDrvInfo  = (pfn_UUIRTGetDrvInfo) GetProcAddress(hinstUIRTLib, "UUIRTGetDrvInfo");
		fn_UUIRTGetUUIRTInfo = (pfn_UUIRTGetUUIRTInfo) GetProcAddress(hinstUIRTLib, "UUIRTGetUUIRTInfo");
		fn_UUIRTGetUUIRTConfig = (pfn_UUIRTGetUUIRTConfig) GetProcAddress(hinstUIRTLib, "UUIRTGetUUIRTConfig");
		fn_UUIRTSetUUIRTConfig = (pfn_UUIRTSetUUIRTConfig) GetProcAddress(hinstUIRTLib, "UUIRTSetUUIRTConfig");
		fn_UUIRTSetReceiveCallback = (pfn_UUIRTSetReceiveCallback) GetProcAddress(hinstUIRTLib, "UUIRTSetReceiveCallback");
		fn_UUIRTTransmitIR = (pfn_UUIRTTransmitIR) GetProcAddress(hinstUIRTLib, "UUIRTTransmitIR");
		fn_UUIRTLearnIR = (pfn_UUIRTLearnIR) GetProcAddress(hinstUIRTLib, "UUIRTLearnIR");

		if (!fnUUIRTOpen || 
			!fnUUIRTClose || 
			!fn_UUIRTGetDrvInfo || 
			!fn_UUIRTGetUUIRTInfo || 
			!fn_UUIRTGetUUIRTConfig || 
			!fn_UUIRTSetUUIRTConfig || 
			!fn_UUIRTSetReceiveCallback || 
			!fn_UUIRTTransmitIR || 
			!fn_UUIRTLearnIR)
		{
			unLoadUIRTDLL();
			return FALSE;
		}

		return TRUE;
	}
	return FALSE;
}

/*****************************************************************************/
/* IRReceiveCallback: Receive IR Callback Procedure						     */
/*																			 */
/* This procedure is called by the UUIRT .dll whenever an IRcode is received */
/* The IRcode is passed to the callback in UIR format.						 */
/*																			 */
/*****************************************************************************/
void WINAPI IRReceiveCallback (char *IREventStr, void *userData)
{
	printf("<IR Receive: Code = %s, UserData = %08x!!!\n", IREventStr, (UINT32)userData);

	// Get the Java VM, attach to it and send the event, then detach
	JavaVM* vmBuf;
	jsize numVMs;
	if (!JNI_GetCreatedJavaVMs(&vmBuf, 1, &numVMs))
	{
		if (!numVMs) return;
		JNIEnv* env;
		if (!vmBuf->AttachCurrentThread((void**)&env, NULL))
		{
			jbyteArray ja = env->NewByteArray(12);
			jbyte jb[12];
			for (int i = 0; i < 12; i++) jb[i] = IREventStr[i];
			env->SetByteArrayRegion(ja, 0, 12, jb);
			static jclass evtClass = env->GetObjectClass((jobject) userData);
			static jmethodID procMeth = env->GetMethodID(evtClass, "recvInfrared", "([B)V");
			env->CallVoidMethod((jobject)userData, procMeth, ja);
			vmBuf->DetachCurrentThread();
		}
	}
}

void setupUIRTReceiveCallback(jobject router)
{
	// Register a callback function for IR receive...
	fn_UUIRTSetReceiveCallback(hDrvHandle, &IRReceiveCallback, (void *)router);
}

/*****************************************************************************/
/* IRLearnCallback: Learn IR Callback Procedure						         */
/*																			 */
/* This procedure is called by the UUIRT .dll during the LEARN process		 */
/* to allow user feedback on Learn progress, signal quality and (if needed)  */
/* carrier frequency.														 */
/*																			 */
/*****************************************************************************/
void WINAPI IRLearnCallback (unsigned int progress, unsigned int sigQuality, unsigned long carrierFreq, void *userData)
{
	printf("<Learn Progress: %d%%, Signal = %d%%, Freq = %ld, UserData = %08x!!!\n", progress, sigQuality & 0xff, carrierFreq, (UINT32)userData);
}


/*****************************************************************************/
/* LearnThread: Learn IR Thread function									 */
/*																			 */
/* This function executes as a separate thread which calls the UUIRTLearnIR  */
/* function.  In this example, the UUIRTLearnIR function is called from this */
/* separate thread to allow the main console thread to continue monitoring   */
/* the keyboard so that the user may abort the learn process. Depending on   */
/* the application, the UUIRTLearnIR may be called from the main thread if   */
/* an asynchronous method (such as a timer) is available to monitor user     */
/* input.																	 */
/*																			 */
/*****************************************************************************/
DWORD WINAPI LearnThread( LPVOID lpParameter )
{
	BOOL *pbAbortLearn = (BOOL *)lpParameter;

	printf("\nCalling LearnIR...");
	if (!fn_UUIRTLearnIR(hDrvHandle, gLearnFormat, gLearnBuffer, IRLearnCallback, (void *)0x5a5a5a5a, pbAbortLearn, 0, NULL, NULL))
	{
		printf("\n\t*** ERROR calling UUIRTLearnIR! ***\n");
	}
	else
	{
		if (!*pbAbortLearn)
		{
			printf("...Done...IRCode = %s\n",gLearnBuffer);
			strcpy(gIRCode, gLearnBuffer);
			gIRCodeFormat = gLearnFormat & 0xff;
		}
		else
		{
			printf("...*** LEARN ABORTED ***\n");
		}
	}
	return 0;
}

bool setupUIRTDriver()
{
	if (!loadUIRTDLL())
	{
		printf("ERROR: Unable to load uuirtdrv,dll!\n");
		return false;
	}

	if (!fn_UUIRTGetDrvInfo(&drvVersion))
	{
		printf("ERROR: Unable to retrieve uuirtdrv version!\n");
		unLoadUIRTDLL();
		return false;
	}

	if (drvVersion != 0x0100)
	{
		printf("ERROR: Invalid uuirtdrv version!\n");
		unLoadUIRTDLL();
		return false;
	}

	hDrvHandle = fnUUIRTOpen();
	if (hDrvHandle == INVALID_HANDLE_VALUE)
	{
		DWORD err;

		err = GetLastError();

		if (err == UUIRTDRV_ERR_NO_DLL)
		{
			printf("ERROR: Unable to find USB-UIRT Driver. Please make sure driver is Installed!\n");
		}
		else if (err == UUIRTDRV_ERR_NO_DEVICE)
		{
			printf("ERROR: Unable to connect to USB-UIRT device!  Please ensure device is connected to the computer!\n");
		}
		else if (err == UUIRTDRV_ERR_NO_RESP)
		{
			printf("ERROR: Unable to communicate with USB-UIRT device!  Please check connections and try again.  If you still have problems, try unplugging and reconnecting your USB-UIRT.  If problem persists, contact Technical Support!\n");
		}
		else if (err == UUIRTDRV_ERR_VERSION)
		{
			printf("ERROR: Your USB-UIRT's firmware is not compatible with this API DLL. Please verify you are running the latest API DLL and that you're using the latest version of USB-UIRT firmware!  If problem persists, contact Technical Support!\n");
		}
		else
		{
			printf("ERROR: Unable to initialize USB-UIRT (unknown error)!\n");
		}

		unLoadUIRTDLL();
		return false;
	}

	return true;
}

void killUIRTDriver()
{
	fnUUIRTClose(hDrvHandle);

	unLoadUIRTDLL();
}