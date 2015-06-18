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

#ifndef __TUNER_PLUGIN_CTRL_H__
#define __TUNER_PLUGIN_CTRL_H__


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

#include "channel.h"


typedef struct PLUGIN_CTRL {
	void* hPluginHandle;//dll handle
	int   nTunerType;   //DVB-S/DVB-C/DVB-T/ATSC/QAM
	FARPROC pfnProc;    //Plugin call
	JNIEnv *env;		//for log message
} PLUGIN_CTRL;


typedef int (__cdecl *DVBSPROC)( char*pDeviceName, unsigned short nDeviceNum, unsigned short nSatNo, unsigned long lFreq, unsigned short nSymRate,
								 unsigned short nModulation, unsigned short nPolarity, char* szChannel, char* szReserve );
typedef int (__cdecl *DVBCPROC)( char*pDeviceName, unsigned short nDeviceNum, unsigned long lFreq, unsigned short nSymRate,
								 unsigned short nModulation, char* szChannel, char* szReserve );
typedef int (__cdecl *DVBTPROC)( char*pDeviceName, unsigned short nDeviceNum, unsigned long lFreq, unsigned short nBandWidth,
								 char* szChannel, char* szReserve );
typedef int (__cdecl *QAMPROC)( char*pDeviceName, unsigned short nDeviceNum, unsigned long lFreq, unsigned short nChannel,
								 unsigned short nModulation, char* szChannel, char* szReserve );
typedef int (__cdecl *ATSCPROC)( char*pDeviceName, unsigned short nDeviceNum, unsigned long lFreq, unsigned short nChannel,
								 char* szChannel, char* szReserve );

int  SetupTunerPlugin( JNIEnv *env, DShowCaptureInfo* pCapInfo, int nTunerType );
void ReleaseTunerPlugin( JNIEnv *env, DShowCaptureInfo* pCapInfo );
int TunerPluginDVBSCall( DShowCaptureInfo* pCapInfo, DVB_S_FREQ* dvbs );
int TunerPluginDVBCCall( DShowCaptureInfo* pCapInfo, DVB_C_FREQ* dvbs );
int TunerPluginDVBTCall( DShowCaptureInfo* pCapInfo, DVB_T_FREQ* dvbs );
int TunerPluginQAMCall( DShowCaptureInfo* pCapInfo, QAM_FREQ* dvbs );
int TunerPluginATSCCall( DShowCaptureInfo* pCapInfo, ATSC_FREQ* dvbs );

#ifdef __cplusplus
 }
#endif

#endif


