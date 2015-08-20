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

#ifndef _ANYSEE_TUNER_H_
#define _ANYSEE_TUNER_H_

/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "anyseeAPI.h"
#include "anysee_error.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */

////////////////////////////////////////////////////////////////////////////////
//CI/CAM
///////////////////////////////////////////////////////////////////////////////
struct DtvCIApi {
	virtual HRESULT WINAPI OpenCILib(HWND hwnd,int iPortIndex);
	virtual HRESULT WINAPI CI_Control(DWORD command, LPARAM *pParam, LPARAM *pRetParam);
};
#define MAX_DEVICES_NUM 32
typedef struct _anyseeCIDevicesInfo_
{
	CHAR  pBuffer[MAX_DEVICES_NUM][256]; // Filter graph device path(=DisplayName)
	DWORD dwLength[MAX_DEVICES_NUM]; // The length of pBuffer
	DWORD dwFGIndex[MAX_DEVICES_NUM]; // Filter graph device path(=DisplayName) index(1, 2, 3, ...)
	DWORD dwADIndex[MAX_DEVICES_NUM]; // anysee device number(1, 2, 3, ...)
}ANYSEECIDEVICESINFO, *PANYSEECIDEVICESINFO;


#define CIAPI_API 
typedef DWORD (WINAPI* pCreateDtvCIAPI)(DtvCIApi **);
CIAPI_API DWORD WINAPI CreateDtvCIAPI(DtvCIApi **);
typedef DWORD (WINAPI* pDestroyDtvCIAPI)(DtvCIApi **);
CIAPI_API DWORD WINAPI DestroyDtvCIAPI(DtvCIApi **);
typedef int (WINAPI* pGetanyseeNumberofDevices)();
CIAPI_API int WINAPI GetanyseeNumberofDevices();
typedef int (WINAPI* pGetanyseeNumberofDevicesEx)(PANYSEECIDEVICESINFO pList);
CIAPI_API int WINAPI GetanyseeNumberofDevicesEx(PANYSEECIDEVICESINFO pList);

// Add
typedef void ( *pCIStateFunc)(int nPort, int nCmd, char *str); // CI Status Callback Function.
typedef void ( *pCIMessageFunc)(int nPort, PVOID pData); // CI Munu Data Callback Function.

#define WM_ANYSEE_CI_MESSAGE WM_USER + 0x1620 // Menu Data sending Message
#define WM_ANYSEE_CI_STATES WM_USER + 0x1621 // CI Status sending Message

typedef struct _tagCIStatus
{
	int nSize; // struct size
	int nPortNum; // Device number
	char strMsg[256]; // string data
}tagCIStatus, *ptagCIStatus;

typedef struct {
	int line_count;
	int alloc_count;
	unsigned char strs[32][256]; // menu string data
} MMIStrsBlock;

typedef struct _tagCIMessages
{
	int nSize;		// struct size
	int nPortNum;	// Device Port Number
	int nSlotNum;	// Slot Number
	int nMuLines;	// Menu Lines
	int nOptLines;	// Option message Lines
	int nBlind;		// Requestion to input CAM = 1 , CAM Menu = 2
	int nBlindLen;	// nBlind == 1, Total input key.
	int nInputCount; // User input Key count
	char SlotTitle[256]; // CAM name
	MMIStrsBlock MMIMsg; // Menu Message 0 ~ 2 Index , Option Message 3 ~ nOptLines Index
	} tagCIMsgs, *ptagCIMsgs;

enum CI_KEY_MAP{
	BTN_NUM_0 = 1,
	BTN_NUM_1 = 2,
	BTN_NUM_2 = 3,
	BTN_NUM_3 = 4,
	BTN_NUM_4 = 5,
	BTN_NUM_5 = 6,
	BTN_NUM_6 = 7,
	BTN_NUM_7 = 8,
	BTN_NUM_8 = 9,
	BTN_NUM_9 = 10,
	BTN_MENU = 20,
	BTN_EXIT = 21,
	BTN_ARROW_UP = 22,
	BTN_ARROW_DOWN = 23,
	BTN_ARROW_LEFT = 24,
	BTN_ARROW_RIGHT = 25,
	BTN_SELECT = 26,
	BTN_CLEAR = 30
	};

enum CI_MESSAGE_COMMAND{
	CI_MSG_EXTRACTED_CAM = 2002, // CAM Extracting Message.
	CI_MSG_CLEAR = 2100, // Clear message
	CI_MSG_INITIALIZATION_CAM = 2101, // CAM Initializing message
	CI_MSG_INSERT_CAM = 2102, // CAM initialization finishing message.
	CI_SEND_PMT_COMPLET = 2103 // PMT Set up message.
	};

enum CI_CONTROL_COMMAND{
	CI_CONTROL_GET_DEVICE_NUM = 1100, // Connected Set number
	CI_CONTROL_IS_OPEN = 1104, // Check the status of connected set.
	CI_CONTROL_SET_KEY = 1105, // Key setting.
	CI_CONTROL_SET_TDT = 1106, // TDT setting.
	CI_CONTROL_SET_PMT = 1110, // PMT setting
	CI_CONTROL_IS_PLUG_OPEN = 2000 // Check the status of connected set and set callback func.
	};


void* AnyseeOpenLNBDevice( char* DeviceName );
void AnyseeCloseLNBDevice( void* device );
int  AnyseeDiSEqCNIMSetup( void* Device, int Horizon, int HiBand, unsigned long LowLOF, unsigned long HighLOF, int Pos, int Option );
int  AnyseeTunerSetup( void* Device, unsigned long dwFreqKHZ, int dwModulation, unsigned long SymRate );

#endif