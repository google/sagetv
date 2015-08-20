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

#ifndef __ITSParser__
#define __ITSParser__

#ifdef __cplusplus
extern "C" {
#endif

// {AE1A2888-540E-4077-B1AB-67A34A72298C}
//DEFINE_GUID(IID_ITSParser, 
//0xae1a2888, 0x540e, 0x4077, 0xb1, 0xab, 0x67, 0xa3, 0x4a, 0x72, 0x29, 0x8c);

DEFINE_GUID(FORMAT_SAGETVInfo,
	0x53414745, 0x5456, 0x11ce, 0xbf, 0x01, 0x00, 0xaa, 0x00, 0x55, 0x59, 0x5a);

DEFINE_GUID(IID_ITSParser2, 
0xae1a2999, 0x540e, 0x4077, 0xb1, 0xab, 0x67, 0xa3, 0x4a, 0x72, 0x29, 0x8c);


    DECLARE_INTERFACE_(ITSParser2, IUnknown)
    {
        STDMETHOD(SetDumpSize) (THIS_
			DWORD dwDumpSizeLimit
            )PURE;

        STDMETHOD(SetDebugSourceFile) (THIS_
			CHAR *pszFileName
            )PURE;

        STDMETHOD(SetEPGDump) (THIS_
			void* pfn,
			void* context 
            )PURE;

        STDMETHOD(SetAVInfDump) (THIS_
			void* pfn,
			void* context 
            )PURE;

        STDMETHOD(SetProgramDataDump) (THIS_
			void* pfn,
			void* context 
            )PURE;

        STDMETHOD(SetPIDsDump) (THIS_
			void* pfn,
			void* context 
            )PURE;

		STDMETHOD(GetDebugInfo) (THIS_
			WORD cmd, char* Buf, DWORD BufSize
			) PURE;


		STDMETHOD(PassThrusEnable) (THIS_
			BOOL Enable
            )PURE;

        STDMETHOD(Reset) (THIS_
            )PURE;

		STDMETHOD(PacketInputNum) (THIS_
			DWORD* dwTSPackets
            )PURE;

		
        STDMETHOD(StopParser) (THIS_
            )PURE;

        STDMETHOD(StartParser) (THIS_
            )PURE;

        STDMETHOD(LockUpParser) (THIS_
            )PURE;

        STDMETHOD(UnLockUpParser) (THIS_
            )PURE;

        STDMETHOD(SetOutputFormat) (THIS_
			WORD wFormat
            )PURE;

        STDMETHOD(SetNetworkType) (THIS_
			WORD wFormat, WORD wSubFormat
            )PURE;

        STDMETHOD(GetNetworkType) (THIS_
			WORD* wFormat, WORD* wSubFormat
            )PURE;

		STDMETHOD(PickupTSChannel) (THIS_
			WORD  wChannel, 
			WORD  wProgram, 
			char* Name,
			BOOL  Reset,
			BOOL* Ret
			) PURE;

		STDMETHOD(PickupATSCChannel) (THIS_
			WORD  wMajor, 
			WORD  wMinor, 
			WORD  wProgram, 
			char* Name,
			BOOL  Reset,
			BOOL* Ret
			) PURE;

		STDMETHOD(PickupDVBChannel) (THIS_
			WORD  wOnid, 
			WORD  wTsid, 
			WORD  wSid, 
			char* Name,
			BOOL  Reset,
			BOOL* Ret
			) PURE;

		STDMETHOD(LockTSChannel) (THIS_
			void *pTune,
			BOOL* Ret
			) PURE;

		STDMETHOD(ScanChannel) (THIS_
			void *pTune,
			BOOL* Ret
			) PURE;

		//return -1:no data incoming; 0: inprogress; 1: timeout, not channel found; 2: PSI channels info ready; naked channels found;
		STDMETHOD(ScanChannelState) (THIS_
			int *pScanState, int *pFoundChannelNum, int nClock
			) PURE;

		STDMETHOD(ReleaseScanChannel) (THIS_
			) PURE;

		STDMETHOD(GetScanChannelList) (THIS_
			void **pChannelList,
			BOOL* Ret
			) PURE;

		STDMETHOD(GetScanTuneList) (THIS_
			void **pTuneList,
			BOOL* Ret
			) PURE;
		
       STDMETHOD(SetAudioLanguage) (THIS_
			char* pLanguagecode
            )PURE;
	
	   	STDMETHOD(WaitCleanStream) (THIS_
			DWORD dwLimit
			) PURE;

        };



#ifdef __cplusplus
}
#endif

#endif // __ITSParser__

