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

// The following ifdef block is the standard way of creating macros which make exporting 
// from a DLL simpler. All files within this DLL are compiled with the UUIRTDRV_EXPORTS
// symbol defined on the command line. this symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// UUIRTDRV_API functions as being imported from a DLL, wheras this DLL sees symbols
// defined with this macro as being exported.
#ifdef UUIRTDRV_EXPORTS
#define UUIRTDRV_API __declspec(dllexport)
#else
#define UUIRTDRV_API __declspec(dllimport)
#endif

// This class is exported from the uuirtdrv.dll
//class UUIRTDRV_API CUuirtdrv {
//public:
//  CUuirtdrv(void);
//  // TODO: add your methods here.
//};

typedef struct
{
  unsigned int fwVersion;
  unsigned int protVersion;
  unsigned char fwDateDay;
  unsigned char fwDateMonth;
  unsigned char fwDateYear;
} UUINFO, *PUUINFO;

typedef struct UUGPIO
{
  BYTE  irCode[6];
  BYTE  action;
  BYTE  duration;
} *PUUGPIO;

typedef void *HUUHANDLE;

typedef void (WINAPI *PUUCALLBACKPROC) (char *IREventStr, void *userData);
typedef void (WINAPI *PLEARNCALLBACKPROC) (unsigned int progress, unsigned int sigQuality, unsigned long carrierFreq, void *userData);

//typedef void (__stdcall *PUUCALLBACKPROC)(BSTR pbstr);


extern UUIRTDRV_API int nUuirtdrv;

UUIRTDRV_API HUUHANDLE PASCAL UUIRTOpen(void);

UUIRTDRV_API HUUHANDLE PASCAL UUIRTOpenEx(char *devName, DWORD dwFlagsAndAttributes, void *reserved0, void *reserved1);

#define UUIRTDRV_OPENEX_ATTRIBUTE_EXCLUSIVE 0x0001

UUIRTDRV_API BOOL PASCAL UUIRTClose(HUUHANDLE hHandle);

UUIRTDRV_API BOOL PASCAL UUIRTGetDrvInfo(unsigned int *puAPIVersion);
//UUIRTDRV_API BOOL PASCAL UUIRTGetDrvInfo2(LPDWORD puDrvVersion);

UUIRTDRV_API BOOL PASCAL UUIRTGetDrvVersion(unsigned int *puDLLVersion);

UUIRTDRV_API BOOL PASCAL UUIRTGetUUIRTInfo(HUUHANDLE hHandle, PUUINFO puuInfo);

UUIRTDRV_API BOOL PASCAL UUIRTGetUUIRTConfig(HUUHANDLE hHandle, PUINT32 puConfig);

UUIRTDRV_API BOOL PASCAL UUIRTSetUUIRTConfig(HUUHANDLE hHandle, UINT32 uConfig);

UUIRTDRV_API BOOL PASCAL UUIRTTransmitIR(HUUHANDLE hHandle, char *IRCode, int codeFormat, int repeatCount, int inactivityWaitTime, HANDLE hEvent, void *reserved0, void *reserved1);

UUIRTDRV_API BOOL PASCAL UUIRTLearnIR(HUUHANDLE hHandle, int codeFormat, char *IRCode, PLEARNCALLBACKPROC progressProc, void *userData, BOOL *pAbort, unsigned int param1, HANDLE hEvent, void *reserved0);

UUIRTDRV_API BOOL PASCAL UUIRTSetReceiveCallback(HUUHANDLE hHandle, PUUCALLBACKPROC receiveProc, void *userData);

UUIRTDRV_API BOOL PASCAL UUIRTGetUUIRTGPIOCfg(HUUHANDLE hHandle, int *pNumSlots, UINT32 *pdwPortPins, PUUGPIO pGPIOStruct);

UUIRTDRV_API BOOL PASCAL UUIRTSetUUIRTGPIOCfg(HUUHANDLE hHandle, int index, PUUGPIO pGpioSt);

#if 0
UUIRTDRV_API BOOL PASCAL UUIRTEERead(HUUHANDLE hHandle, FT_PROGRAM_DATA *ftData);

UUIRTDRV_API BOOL PASCAL UUIRTEEProgram(HUUHANDLE hHandle, FT_PROGRAM_DATA *ftData);
#endif

#define UUIRTDRV_ERR_NO_DEVICE 0x20000001
#define UUIRTDRV_ERR_NO_RESP   0x20000002
#define UUIRTDRV_ERR_NO_DLL    0x20000003
#define UUIRTDRV_ERR_VERSION   0x20000004
#define UUIRTDRV_ERR_IN_USE    0x20000005



typedef HUUHANDLE (WINAPI *pfn_UUIRTOpen)(void);
typedef HUUHANDLE (WINAPI *pfn_UUIRTOpenEx)(char *devName, DWORD dwFlagsAndAttributes, void *reserved0, void *reserved1);
typedef BOOL    (WINAPI *pfn_UUIRTClose)(HUUHANDLE hHandle);
typedef BOOL    (WINAPI *pfn_UUIRTGetDrvInfo)(unsigned int *puDrvVersion);
typedef BOOL    (WINAPI *pfn_UUIRTGetDrvVersion)(unsigned int *puDLLVersion);
typedef BOOL    (WINAPI *pfn_UUIRTGetUUIRTInfo)(HUUHANDLE hHandle, PUUINFO puuInfo);
typedef BOOL    (WINAPI *pfn_UUIRTGetUUIRTConfig)(HUUHANDLE hHandle, PUINT32 puConfig);
typedef BOOL    (WINAPI *pfn_UUIRTSetUUIRTConfig)(HUUHANDLE hHandle, UINT32 uConfig);
typedef BOOL    (WINAPI *pfn_UUIRTSetReceiveCallback)(HUUHANDLE hHandle, PUUCALLBACKPROC receiveProc, void *userData);
typedef BOOL    (WINAPI *pfn_UUIRTTransmitIR)(HUUHANDLE hHandle, char *IRCode, int codeFormat, int repeatCount, int inactivityWaitTime, HANDLE hEvent, void *reserved0, void *reserved1);
typedef BOOL    (WINAPI *pfn_UUIRTLearnIR)(HUUHANDLE hHandle, int codeFormat, char *IRCode, PLEARNCALLBACKPROC progressProc, void *userData, BOOL *pAbort, unsigned int param1, void *reserved0, void *reserved1);
typedef BOOL    (WINAPI *pfn_UUIRTGetUUIRTGPIOCfg)(HUUHANDLE hHandle, int *pNumSlots, UINT32 *pdwPortPins, PUUGPIO pGPIOStruct);
typedef BOOL    (WINAPI *pfn_UUIRTSetUUIRTGPIOCfg)(HUUHANDLE hHandle, int index, PUUGPIO pGpioSt);
#if 0
typedef BOOL    (WINAPI *pfn_UUIRTEERead)(HUUHANDLE hHandle, FT_PROGRAM_DATA *ftData);
typedef BOOL    (WINAPI *pfn_UUIRTEEProgram)(HUUHANDLE hHandle, FT_PROGRAM_DATA *ftData);
#endif

#define UUIRTDRV_CFG_LEDRX    0x0001
#define UUIRTDRV_CFG_LEDTX    0x0002
#define UUIRTDRV_CFG_LEGACYRX 0x0004

#define UUIRTDRV_IRFMT_UUIRT  0x0000
#define UUIRTDRV_IRFMT_PRONTO 0x0010

#define UUIRTDRV_IRFMT_LEARN_FORCERAW   0x0100
#define UUIRTDRV_IRFMT_LEARN_FORCESTRUC 0x0200
#define UUIRTDRV_IRFMT_LEARN_FORCEFREQ  0x0400
#define UUIRTDRV_IRFMT_LEARN_FREQDETECT 0x0800
#define UUIRTDRV_IRFMT_LEARN_UIR        0x4000
#define UUIRTDRV_IRFMT_LEARN_DEBUG      0x8000

#define UUIRTDRV_IRFMT_TRANSMIT_DC      0x0080
