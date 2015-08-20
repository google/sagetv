/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __ITSParser__
#define __ITSParser__

#ifdef __cplusplus
extern "C" {
#endif

//#include "psiobj.h"
#define EC_PROGRAM_CHANGED EC_USER + 100

#ifndef SI_DATA_STRUCT
#define SI_DATA_STRUCT

#define	MAX_CHANNEL_NAME	32
typedef struct 
{
	unsigned short a;  //ATSC:major;  DVB: ONID
	unsigned short b;  //ATSC:mainor; DVB: TSID
	unsigned short c;  //ATSC:program_id; DVB: program_id
} CHANNEL;

typedef struct
{
	CHANNEL channel;
	char name[MAX_CHANNEL_NAME];
	unsigned short type;
} CHANNEL_NAME;

#endif

#ifndef STREAM_TYPE_ENUM
#define STREAM_TYPE_ENUM
typedef enum
{
	UNKNOWN_STREAM_TYPE = 0,
	ATSC_STREAM_TYPE = 1,
	DVB_STREAM_TYPE = 2,
} STREAM_TYPE;
#endif


// {AE1A2888-540E-4077-B1AB-67A34A72298C}
DEFINE_GUID(IID_ITSParser, 
0xae1a2888, 0x540e, 0x4077, 0xb1, 0xab, 0x67, 0xa3, 0x4a, 0x72, 0x29, 0x8c);


    DECLARE_INTERFACE_(ITSParser, IUnknown)
    {

        STDMETHOD(GetCountOfPrograms) (THIS_
            WORD *pNumOfPrograms
			) PURE;


		STDMETHOD(GetListOfPrograms) (THIS_
            WORD *pListOfProgram,
			DWORD dxMaxBytes
			) PURE;

		STDMETHOD(CheckProgramStart)( THIS_
			BOOL* bStart
			) PURE;

		STDMETHOD(GetCurrentProgram) (THIS_
			WORD *wNumOfProgram
			) PURE;

		STDMETHOD(SelectChannel) (THIS_
            WORD wNumOfChannel
			) PURE;

		STDMETHOD(SelectProgram) (THIS_
            WORD wNumOfProgram
			) PURE;

        STDMETHOD(GetCountOfElementaryStreams) (THIS_
            WORD wProgramNumber, 
            WORD *pwVal
            )PURE;

        STDMETHOD(GetStreamType) (THIS_
            WORD wProgramNumber,
            WORD wIndex, 
            BYTE *pbVal
            )PURE;

        STDMETHOD(Reset) (THIS_
            )PURE;

        STDMETHOD(SetDumpSize) (THIS_
			DWORD dwDumpSizeLimit
            )PURE;

        STDMETHOD(SetDebugSourceFile) (THIS_
			CHAR *pszFileName
            )PURE;

        STDMETHOD(SetOutputFormat) (THIS_
			WORD wFormat
            )PURE;

        STDMETHOD(SetNetworkType) (THIS_
			WORD wFormat
            )PURE;

        STDMETHOD(GetNetworkType) (THIS_
			WORD* wFormat
            )PURE;

        STDMETHOD(GetValidChannelNum) (THIS_
			WORD* Num
            )PURE;

        STDMETHOD(GetChannelName) (THIS_
			void* pChannelNameList,
			DWORD dwMaxSize
            )PURE;

        STDMETHOD(ResetSIParser) (THIS_
            )PURE;

        STDMETHOD(GetVideoChannelNum) (THIS_
			WORD* Num
            )PURE;

        STDMETHOD(SetEPGDump) (THIS_
			void* pfn,
			void* context 
            )PURE;

        STDMETHOD(SetAVInfDump) (THIS_
			void* pfn,
			void* context 
            )PURE;

        STDMETHOD(SetPMTDataDump) (THIS_
			void* pfn,
			void* context 
            )PURE;

        STDMETHOD(StopParser) (THIS_
            )PURE;

        STDMETHOD(StartParser) (THIS_
            )PURE;

		STDMETHOD(IsScrambledChannel) (THIS_
			WORD Channel,
			BOOL *Ret
            )PURE;

		STDMETHOD(IsScrambledProgram) (THIS_
			WORD Program,
			BOOL *Ret
            )PURE;

		STDMETHOD(GetSIProgramName) (THIS_
			WORD Program,
			CHAR* Name,
			WORD Size,
			BOOL *Ret
            )PURE;

		STDMETHOD(PassThrusEnable) (THIS_
			BOOL Enable
            )PURE;

		STDMETHOD(SelectChannelWithoutReset) (THIS_
            WORD wNumOfChannel
			) PURE;

		STDMETHOD(SelectProgramWithoutReset) (THIS_
            WORD wNumOfProgram
			) PURE;

		STDMETHOD(PacketInputNum) (THIS_
			DWORD* dwTSPackets
            )PURE;

		STDMETHOD(GetChannelName) (THIS_
			void* pChannelName, 
			DWORD MaxBytes, 
			WORD* Num 
			) PURE;
		
		STDMETHOD(IsVideoStreamReady) (THIS_
			BOOL *Ret
			) PURE;

		STDMETHOD(IsAudioStreamReady) (THIS_
			BOOL *Ret
			) PURE;

		STDMETHOD(GetProgramState) (THIS_
			WORD wProgram,
			DWORD *State
			) PURE;

		STDMETHOD(GetVideoProgramList) (THIS_
			void* pProgramName, 
			DWORD MaxBytes, 
			WORD* Num 
			) PURE;

		STDMETHOD(PickupTSChannel) (THIS_
			WORD  wChannel, 
			WORD  wProgram, 
			char* Name,
			BOOL  Reset,
			BOOL* Ret
			) PURE;

		STDMETHOD(GetNetworkList) (THIS_
			char* Buf, 
			DWORD MaxBytes, 
			DWORD* Bytes 
			) PURE;

		STDMETHOD(GetNetworkNum) (THIS_
			DWORD* Num 
			) PURE;
	
		STDMETHOD(SelectTSTSID) (THIS_
			WORD tsid 
			) PURE;

		STDMETHOD(GetDebugInfo) (THIS_
			WORD cmd, char* Buf, DWORD BufSize
			) PURE;

		STDMETHOD(LockATSCChannel) (THIS_
			WORD major, WORD minor
			) PURE;

		STDMETHOD(PickupATSCChannel) (THIS_
			WORD  wMajor, 
			WORD  wMinor, 
			WORD  wProgram, 
			char* Name,
			BOOL  Reset,
			BOOL* Ret
			) PURE;

        STDMETHOD(SetPIDsDump) (THIS_
			void* pfn,
			void* context 
            )PURE;

        STDMETHOD(LockUpParser) (THIS_
            )PURE;

        STDMETHOD(UnLockUpParser) (THIS_
            )PURE;

        };

		


#ifdef __cplusplus
}
#endif

#endif // __ITSParser__

