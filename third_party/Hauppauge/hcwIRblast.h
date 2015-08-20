/*
 * Copyright 2004 Hauppauge Computer Works, Inc. All Rights Reserved.
 *
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

#define I2C_IR_Z8MCU_OPTION	0xE2		/* FGR - Indicates Z8 Encore on board. */       

typedef enum UIRError {
	UIRError_Success,
	UIRError_Fail,
	UIRError_DeviceNotInDB,
	UIRError_CodeNotInDB,
	UIRError_KeyNotInDB,
	UIRError_NotSupported,
	UIRError_CorruptDataReceived,
	UIRError_TimeOut,
	UIRError_ChecksumFailed,
	UIRError_FWIncompatible,
	UIRError_InvalidParameter,
	UIRError_NotInitialized,
}UIRError;

#if !defined(__MKTYPLIB__) && !defined(__midl)
		   
#ifdef __cplusplus
	extern "C" {
#endif


typedef struct UIR_CFG {
	DWORD cfgDataSize;	// Size of this structure, to allow for future expansion
	DWORD cfgVersion;	// Version of this structure, to allow for future expansion
	long cfgRegion;		// Current "region" for device selection in UI
	long cfgDevice;		// Current Device Type, within Region
	long cfgVendor;		// Current Vendor, within DeviceType, for codeset selection in UI
	long cfgCodeset;	// Current CodeSet to use
	long SendPowerOn;	// Should we send power on each time
	long PowerOnDelay;	// Time to wait after sending power on
	long MinDigits;		// Minimum number of digits to send per channel change
	long DigitDelay;	// interdigit time to wait
	long NeedsEnter;	// Should send Enter after digits
	long EnterDelay;	// time to wait after Enter key
	long TuneDelay;		// time to wait after last digit, if NeedsEnter not set
	long OneDigitDelay; // time to wait after single digit channel, if NeedsEnter not set
} UIR_CFG;


WORD     WINAPI UIR_Open(BOOL bVerbose, WORD wIRPort );
BOOL     WINAPI UIR_Close();
UIRError WINAPI UIR_GetConfig(int device, int codeset, UIR_CFG *cfgPtr);
UIRError WINAPI UIR_SetConfig(UIR_CFG *cfgPtr);

// These API's require that UIR_Open has been called first, or they will return UIRError_NotInitialized
UIRError WINAPI UIR_SendKeyCode(const int device, const int codeset, const int key);
UIRError WINAPI	UIR_LearnKeyCode(const int device, const int codeset, const int key, const unsigned int uiTimeOut);
UIRError WINAPI	UIR_GetLearnedCode(const int device, const int codeset, const int key, unsigned char *pucRetBuffer, const int iSizeofBuffer, int *piReturnedSize);
UIRError WINAPI	UIR_SleepMode(const DWORD dwOption, BYTE *pbStatusByte);
UIRError WINAPI	UIR_UseGPIO(const DWORD dwOption, BYTE *pbStatusByte);
UIRError WINAPI UIR_GetVersionStr(char *pBuffer, DWORD *pBufSize);
UIRError WINAPI UIR_is_device_available(const int device);
UIRError WINAPI UIR_is_code_available(const int device, const int codeset);
UIRError WINAPI UIR_is_key_available(const int device, const int codeset, const int key);
UIRError WINAPI UIR_GotoChannel(int device, int codeset, int channel);
UIRError WINAPI UIR_GetKeyCodeRaw(LPBYTE pbBytes, DWORD *pSize);


#ifdef __cplusplus
	}
#endif

#endif //__MKTYPLIB__, __midl 
