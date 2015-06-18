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

#ifndef __SAGETV_h__
#define __SAGETV_h__

extern "C" { 

#define MAX_USER_PID_NUM    8


typedef struct PID_ENTRY
{
	unsigned short pid;
	unsigned char  type;
	unsigned char  index;
	char desc[16];
} PID_ENTRY;

typedef struct PIDS_TABLE
{
	unsigned short tune_id;
	unsigned short tsid;
	unsigned short sid;
	unsigned short pcr_pid;
	unsigned short ca_pid;
	unsigned short pid_num;
	PID_ENTRY pid_tbl[MAX_USER_PID_NUM];
} PIDS_TABLE;

typedef struct PROGRAM_DATA
{
	PIDS_TABLE pids_table;
	unsigned char* pmt_section_data;
	unsigned short pmt_section_bytes;
	unsigned short padding;
} PROGRAM_DATA;

/*
typedef struct PIDS_TABLE
{
	unsigned short tune_id;
	unsigned short tsid;
	unsigned short sid;
	unsigned short pcr_pid;
	unsigned short ca_pid;
	unsigned short pid_num;
	PID_ENTRY pid_tbl[MAX_USER_PID_NUM];
} PIDS_TABLE;



typedef struct PID_ENTRY
{
	unsigned short pid;
	unsigned char  type;
	unsigned char  index;
	unsigned char  padding1;
	unsigned char  padding2;
	char desc[16];
} PID_ENTRY;

typedef struct PIDS_TABLE
{
	unsigned short tune_id;
	unsigned short tsid;
	unsigned short sid;
	unsigned short pcr_pid;
	unsigned short ca_pid;
	unsigned short pid_num;
	PID_ENTRY pid_tbl[MAX_USER_PID_NUM];
} PIDS_TABLE;



typedef struct PROGRAM_DATA
{
	PIDS_TABLE pids_table;
	unsigned char* pmt_section_data;
	unsigned short pmt_section_bytes;
	unsigned short padding;
} PROGRAM_DATA;

*/

	int SageNativeLogCheck(  );
	void EnableNativeLog( bool Enable  );
	void DisableNativeLog( bool Enable  );
int SageTuneATSCChannel( void* Capture, ATSC_FREQ* atsc, int dryTune );
int SageTuneQAMFrequency( void* Capture, QAM_FREQ* qam , int dryTune ); 
int SageTuneDVBTFrequency( void* Capture, DVB_T_FREQ* dvbt, int dryTune);
int SageTuneDVBCFrequency( void* Capture, DVB_C_FREQ* dvbc, int dryTune);
int SageTuneDVBSFrequency( void* Capture, DVB_S_FREQ* dvbs, int dryTune);
int SageGetSatelliteTble( void* Capture, SAT_NAME *sat_name, int max_sat );
void SageLockUpParser( void* Capture );
void SageUnLockUpParser( void* Capture );
}

#endif