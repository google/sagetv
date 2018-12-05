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

#ifndef __CAM_CTRL_h__
#define __CAM_CTRL_h__


#ifdef __cplusplus
extern "C" {
#endif

#ifdef WIN32
#if( _MSC_VER <= 800 )
#pragma pack(1)  
#else
#include <pshpack1.h>
#endif
#endif


#define FIREDTV_CAM			"FIREDTV "  //8 bytes tag
#define TECHNOTREND_CAM		"TECHNTD "  //8 bytes tag
#define ANYSEE_CAM			"ANYSEE  "
#define UNKNOWN_CAM		    "UNKNOWN "  //8 bytes tag

#define TECHNOTREND_VENDOR_ID	"1131"
#define FIREDTV_VENDOR_ID	    "xxxx"

#define FIREDTV_MFG				"Digital Everywhere"
#define TECHNOTREND_MFG			"TechnoTrend"

#define ANYSEE_MFG				"Advanced Multimedia Technology Co.,Ltd. Korea"
#define ANYSEE_VENDOR_ID		"1c73"

//typedef struct
//{
//	short pid;
//	short service;
//	short channel;
//	void* parser;
//	void* tbl;
//	char* data;
//	short length;
//} SECTION_INFO;

typedef long (*DATA_DUMP)( void* context, short bytes, void* mesg );

DEFINE_GUID(IID_KSPROPSETID_FIREDTV,
0xab132414, 0xd060, 0x11d0, 0x85, 0x83, 0x00, 0xc0, 0x4f, 0xd9, 0xba, 0xf3);
#define		   KSPROPERTY_FIRESAT_SELECT_MULTIPLEX_DVB_S  0
#define        KSPROPERTY_FIRESAT_SELECT_SERVICE_DVB_S    1
#define        KSPROPERTY_FIRESAT_SELECT_PIDS_DVB_S       2
#define        KSPROPERTY_FIRESAT_SIGNAL_STRENGTH_TUNER   3
#define        KSPROPERTY_FIRESAT_DRIVER_VERSION          4
#define		   KSPROPERTY_FIRESAT_SELECT_MULTIPLEX_DVB_T  5
#define        KSPROPERTY_FIRESAT_SELECT_PIDS_DVB_T       6
#define        KSPROPERTY_FIRESAT_SELECT_MULTIPLEX_DVB_C  7
#define        KSPROPERTY_FIRESAT_SELECT_PIDS_DVB_C       8
#define        KSPROPERTY_FIRESAT_GET_FRONTEND_STATUS     9
#define        KSPROPERTY_FIRESAT_GET_SYSTEM_INFO         10
#define        KSPROPERTY_FIRESAT_GET_FIRMWARE_VERSION    11
#define        KSPROPERTY_FIRESAT_LNB_CONTROL             12
#define        KSPROPERTY_FIRESAT_GET_LNB_PARAM           13
#define        KSPROPERTY_FIRESAT_SET_LNB_PARAM           14
#define        KSPROPERTY_FIRESAT_SET_POWER_STATUS        15
#define        KSPROPERTY_FIRESAT_SET_AUTO_TUNE_STATUS    16
#define        KSPROPERTY_FIRESAT_FIRMWARE_UPDATE         17
#define        KSPROPERTY_FIRESAT_FIRMWARE_UPDATE_STATUS  18
#define        KSPROPERTY_FIRESAT_CI_RESET                19
#define        KSPROPERTY_FIRESAT_CI_WRITE_TPDU           20
#define        KSPROPERTY_FIRESAT_CI_READ_TPDU            21
#define        KSPROPERTY_FIRESAT_HOST2CA                 22
#define        KSPROPERTY_FIRESAT_CA2HOST                 23
#define        KSPROPERTY_FIRESAT_GET_BOARD_TEMP          24
#define        KSPROPERTY_FIRESAT_TUNE_QPSK               25
#define        KSPROPERTY_FIRESAT_REMOTE_CONTROL_REGISTER 26
#define        KSPROPERTY_FIRESAT_REMOTE_CONTROL_CANCEL   27
#define        KSPROPERTY_FIRESAT_GET_CI_STATUS           28
#define    	   KSPROPERTY_FIRESAT_TEST_INTERFACE          29

#define		CI_MMI_REQUEST        0x0100
#define		CI_PMT_REPLY          0x0080
#define		CI_DATE_TIME_REQEST   0x0040
#define		CI_APP_INFO_AVAILABLE 0x0020
#define		CI_MODULE_PRESENT     0x0010
#define		CI_MODULE_IS_DVB      0x0008
#define		CI_MODULE_ERROR       0x0004
#define		CI_MODULE_INIT_READY  0x0002
#define		CI_ERR_MSG_AVAILABLE  0x0001

#define    CAM_CLOSE    0
#define    CAM_INITIALIZED 1
#define    CAM_OPEN	    2
#define    CAM_PENDING  3
#define    CAM_ENABLED  4
#define    CAM_SKIP	    5
#define    CAM_ERROR    -1
#define    CAM_NULL	    0x80


#define MAX_PMT_SIZE (1024+12)

typedef struct {
	char TAG[8];                        //common TAG
	int  state;
	void *env;
	void *pCapInfo;
	bool OnPMTEnable;
} CAM_CTRL_HEADER;

typedef struct {
      unsigned long bCurrentTransponder;   //4
      unsigned long bFullTransponder;      //8  
      unsigned long uLnb;				   //12
      unsigned long uFrequency;            //16
      unsigned long uSymbolRate;           //20
      unsigned char uFecInner;			   //21
      unsigned char uPolarization;         //22
      unsigned char dummy1;           //23
      unsigned char dummy2;                //24  
      unsigned char uNumberOfValidPids;    //25
      unsigned char dummy3;                //26
	  unsigned short pids[16];
    } DVB_S_PIDS;

typedef struct {
      unsigned long bCurrentTransponder; //Set TRUE
      unsigned long bFullTransponder;    //Set FALSE when selecting PIDs
      unsigned long uFrequency;          // kHz 47.000-860.000
      unsigned char uBandwidth;          // BANDWIDTH_8_MHZ, BANDWIDTH_7_MHZ, BANDWIDTH_6_MHZ
      unsigned char uConstellation;      // CONSTELLATION_DVB_T_QPSK,CONSTELLATION_QAM_16,CONSTELLATION_QAM_64,OFDM_AUTO
      unsigned char uCodeRateHP;         // CR_12,CR_23,CR_34,CR_56,CR_78,OFDM_AUTO
      unsigned char uCodeRateLP;         // CR_12,CR_23,CR_34,CR_56,CR_78,OFDM_AUTO
      unsigned char uGuardInterval;      // GUARD_INTERVAL_1_32,GUARD_INTERVAL_1_16,GUARD_INTERVAL_1_8,GUARD_INTERVAL_1_4,OFDM_AUTO
      unsigned char uTransmissionMode;   // TRANSMISSION_MODE_2K, TRANSMISSION_MODE_8K, OFDM_AUTO
      unsigned char uHierarchyInfo;      // HIERARCHY_NONE,HIERARCHY_1,HIERARCHY_2,HIERARCHY_4,OFDM_AUTO
      unsigned char dummy;               //pading 
      unsigned char uNumberOfValidPids;  // 1-16
      unsigned char dummy2; // 
      unsigned short pids[16];
	} DVB_T_PIDS;    


//typedef struct {
//	unsigned  char slot;
//	unsigned  char tag;
//	unsigned  char pad1;
//	unsigned  char pad2;
//	unsigned  char more;
//	unsigned  char pad3;
//	unsigned  char pad4;
//	unsigned  char pad5;
//	unsigned short length;
//  } FIREDTV_CA_HEAD;

typedef struct {
	unsigned char   data1[MAX_PMT_SIZE];
	unsigned char   data2[MAX_PMT_SIZE];
  } FIREDTV_CA;

#define FIREDTV_CA_OFFSET 10

typedef struct {
	CAM_CTRL_HEADER header;
	unsigned serviceID;
	FIREDTV_CA ca_data;
} FIREDTV;
///////////////////////////////////////////////////////////////////////
//Anysee
///////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
//CI/CAM
///////////////////////////////////////////////////////////////////////////////
struct DtvCIApi {
	virtual HRESULT WINAPI OpenCILib(HWND hwnd,int iPortIndex);
	virtual HRESULT WINAPI CI_Control(DWORD command, LPARAM *pParam, LPARAM *pRetParam);
	virtual HRESULT WINAPI SetanyseeCIPMTSection(long lSize, BYTE *pBuf) PURE;
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


typedef struct {
	CAM_CTRL_HEADER header;
	unsigned serviceID;
	int state;
	int portNum;
	DtvCIApi *CILib;
	HINSTANCE hLibHandle;
	pCreateDtvCIAPI  CreateCI;
	pDestroyDtvCIAPI DestroyCI;
	pGetanyseeNumberofDevicesEx GetanyseeNumberofDevicesEx;

} ANYSEE;

////////////////////////////////////////////////////////////////////////
//Technotrend
////////////////////////////////////////////////////////////////////////
typedef enum _tt_ConnectionType
{
	LscPhone		=1,
	LscCable		=2,
	LscInternet		=3,
	LscSerial
} TTCONNTYPE;

typedef enum _TechnoTrendDeviceType
{
    TypeUnknown = 0,
    DevTypeB2=1, // Budget 2
    DevTypeB3=2, // Budget 3 aka TT-budget T-3000
    DevTypeUsb2=3, // USB 2.0
    DevTypeUsb2Pinnacle=4 // USB 2.0 Pinnacle
} TTDEVTYPE;

typedef struct 
{
	char	nStatus;					// CI status
	char*	pMenuTitleString;			// menu title string
	unsigned short*	pCaSystemIDs;		// cam system ID's
	unsigned short  wNoOfCaSystemIDs;   // number of cam system ID's
} SLOTINF;


typedef void (__stdcall* PFN_CI_OnSlotStatus)(void *Context, char nSlot, char nStatus, SLOTINF* csInfo);
typedef void (__stdcall* PFN_CI_OnCAStatus)(void * Context, char nSlot, char nReplyTag, unsigned short wStatus);
typedef struct 
{
      PFN_CI_OnSlotStatus onSlotStatus;
      void* onSlotStatusContext;
      PFN_CI_OnCAStatus onCAStatus;
      void* onCAStatusContext;
} CallbackFunctionsSlim;

typedef  unsigned int (__stdcall* pfn_bdaapiOpenHWIdx)(unsigned int DevType, unsigned int uiDevID);
typedef  int (__stdcall* pfn_bdaapiOpenCISlim)(unsigned int hOpen, CallbackFunctionsSlim CbFuncPointer);
typedef  int (__stdcall* pfn_bdaapiOpenCIWithoutPointer)(unsigned int hOpen);
typedef  int (__stdcall* pfn_bdaapiCIGetSlotStatus)(unsigned int hOpen, char nSlot);
typedef  int (__stdcall* pfn_bdaapiCloseCI)(unsigned int hOpen);
typedef  void(__stdcall* pfn_bdaapiClose)(unsigned int hOpen);
typedef  int (__stdcall* pfn_bdaapiCIReadPSIFastDrvDemux)(unsigned int hOpen, int PNR);
typedef  int (__stdcall* pfn_bdaapiSetDiSEqCMsg)(unsigned int hOpen,  int* data, char length, char repeat, char toneburst, int polarity);
typedef  int (__stdcall* pfn_bdaapiSetDVBTAntPwr)(unsigned int hOpen, bool bAntPwrOnOff);
typedef  int (__stdcall* pfn_bdaapiGetDVBTAntPwr)(unsigned int hOpen, int* uiAntPwrOnOff);

typedef struct {
	CAM_CTRL_HEADER header;
	char status;
	int serviceID;
	int deviceType;
	int	handle;
	HMODULE hLibHandle;
	CallbackFunctionsSlim		CFS;
	pfn_bdaapiOpenHWIdx			bdaapiOpenHWIdx;
	pfn_bdaapiOpenCISlim		bdaapiOpenCISlim;
	pfn_bdaapiOpenCIWithoutPointer bdaapiOpenCIWithoutPointer;
	pfn_bdaapiCIGetSlotStatus   bdaapiCIGetSlotStatus;
	pfn_bdaapiCloseCI			bdaapiCloseCI;
	pfn_bdaapiClose				bdaapiClose;
	pfn_bdaapiCIReadPSIFastDrvDemux  bdaapiCIReadPSIFastDrvDemux;
	pfn_bdaapiSetDiSEqCMsg      bdaapiSetDiSEqCMsg;
	pfn_bdaapiSetDVBTAntPwr     bdaapiSetDVBTAntPwr;
	pfn_bdaapiGetDVBTAntPwr     bdaapiGetDVBTAntPwr;
} TECHNOTREND;

int IsCAMInitialized( JNIEnv *env, DShowCaptureInfo* pCapInfo );
int IsCAMValid( JNIEnv *env, DShowCaptureInfo* pCapInfo );
int ReleaseCAM( JNIEnv *env, DShowCaptureInfo* pCapInfo );
int InitialCAM( JNIEnv *env, DShowCaptureInfo* pCapInfo, char* pDeviceName );
int OpenCAM( JNIEnv *env, DShowCaptureInfo* pCapInfo );
int CloseCAM( JNIEnv *env, DShowCaptureInfo* pCapInfo );
long OnCamPMT( void* context, short bytes, void* mesg );
int  SwitchCamChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId, int encryptionFlag );
void EnableCAMPMT( JNIEnv *env, DShowCaptureInfo* pCapInfo );
void DisableCAMPMT( JNIEnv *env, DShowCaptureInfo* pCapInfo );

#ifdef __cplusplus
 }
#endif

#endif



