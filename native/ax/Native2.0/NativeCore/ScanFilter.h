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

#ifndef _SCAN_OBJ_H_
#define _SCAN_OBJ_H_
#include <stdio.h>
#include "ChannelScan.h"
#define TS_PACKET_MAX_LENGTH 256

#ifdef __cplusplus
extern "C" {
#endif

typedef struct _SCAN_FILTER
{
	int32_t			nStreamFormat;
	int32_t			nStreamSubFormat;
	int32_t			nScanState;
	struct SCAN* pScan;
	uint8_t     cAlignScanBuffer[ TS_PACKET_MAX_LENGTH ];
	int32_t     nExpectedScanBytes;
	int32_t     nAlignScanBytes;

	int32_t     bScanFileCache;
	TUNE		    Tune;

	uint32_t dwScanTunerData;
	uint32_t dwScanFileData;
} SCAN_FILTER;

SCAN_FILTER* CreateScanFilter();
void ReleaseScanFilter( SCAN_FILTER* pScanFilter );
void StartChannelScan( SCAN_FILTER* pScanFilter, struct TUNE* pTune );
void StopChannelScan( SCAN_FILTER* pScanFilter );
void ProcessScan( SCAN_FILTER* pScanFilter, unsigned char* pData, int32_t lDataLen );
int	 ScanChannelState( SCAN_FILTER* pScanFilter );
int	 ScanChannelNum( SCAN_FILTER* pScanFilter );
int	 ScanChannelTimeClock( SCAN_FILTER* pScanFilter, uint32_t lMillionSecond );
struct CHANNEL_LIST *GetScanChannelList( SCAN_FILTER* pScanFilter );
struct TUNE_LIST    *GetScanTuneList( SCAN_FILTER* pScanFilter );

#ifdef __cplusplus
}
#endif


#endif
