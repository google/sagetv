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

//Plugin interface for a third party plugin to tune a channel, which is speciall design for a DVB-S device that needs switch control
#include "stdafx.h"
#include <bdatypes.h>
#include "jni-util.h"
#include <initguid.h>
#include <memory.h>
#include <string.h>
#include <streams.h>
#include <ks.h>
#include <ksproxy.h>
#include "guids.h"
#include "uniapi.h"
#include "sage_DShowCaptureDevice.h"
#include "FilterGraphTools.h"
#include "DShowUtilities.h"
#include "DShowCapture.h"
#include "TunerPlugin.h"

//#include "../ax/TSnative/TSnative.h"
#include "Channel.h"
#include "SageDTV.h"
//enum TV_TYPE 
//{
//	UNKNOWN = 0x0000,
//    ATSC  = 0x0003,
//	DVBT  = 0x0004,
//	DVBC  = 0x0005,
//	DVBS  = 0x0006,
//	QAM   = 0x0007,  //ATSC + QAM
//	ATSC_N = 0x0008,
//	//QAM   = 0x0008,
//};

TV_TYPE GetTVType( DShowCaptureInfo *pCapInfo );

int  SetupTunerPlugin( JNIEnv *env, DShowCaptureInfo* pCapInfo, int nTunerType )
{
	int ret = 0;
	HKEY hregkey;

	//initialize once
	if ( pCapInfo->pTunerPluginCtrlData != NULL )
		return 1;

	PLUGIN_CTRL* pPluginCtrl = (PLUGIN_CTRL* )new char[sizeof(PLUGIN_CTRL)];
	memset( pPluginCtrl, 0x0, sizeof(PLUGIN_CTRL) );
	pCapInfo->pTunerPluginCtrlData = pPluginCtrl;
	slog((env, "Tuner Plugin ctrl created \r\n"  ));

	char* regkey = "SOFTWARE\\Frey Technologies\\Common\\TunerPlugin";
	char* PluginKey = NULL;
	char  PluginName[256]={0};
	int TunerType = nTunerType ;

	if ( TunerType == DVBS )
	{
		PluginKey = "DVBSTunerPlugin";
	}
	else
	if ( TunerType == DVBC  )
	{
		PluginKey = "DVBCTunerPlugin";
	}
	else
	if ( TunerType == DVBT )
	{
		PluginKey = "DVBTTunerPlugin";
	}
	else
	if ( TunerType == QAM )
	{
		PluginKey = "QAMSTunerPlugin";
	}
	else
	if ( TunerType == ATSC )
	{
		PluginKey = "ATSCTunerPlugin";
	}
	else
	{
		return -1;
	}

	if ( RegOpenKeyEx(HKEY_LOCAL_MACHINE, regkey, 0, KEY_READ, &hregkey) == ERROR_SUCCESS )
	{
		DWORD hType;
		DWORD hSize = sizeof(PluginName);
		ret = RegQueryValueEx( hregkey, PluginKey, 0, &hType, (LPBYTE)&PluginName, &hSize);
		if ( ret )
		{
			slog((env, "Tuner Plugin is not specified in registery '%s\\%s' \r\n", regkey, PluginKey ));
			PluginName[0] = 0;
			ret = -2;
		} 
		RegCloseKey( hregkey );
	} 
	else
	{
		slog((env, "Tuner Plugin not setup in registery '%s' \r\n", regkey ));
		ret = -3;
	}

	if ( PluginName[0] == 0x0 )
		return ret;

	HINSTANCE hPluginLibrary = LoadLibrary(PluginName);
	if ( hPluginLibrary != NULL )
	{
		FARPROC lpfnProc = NULL;
		if ( TunerType == DVBS )
			lpfnProc = (FARPROC)GetProcAddress(hPluginLibrary, "DVBSTuneProcess");
		else
		if ( TunerType == DVBC )
			lpfnProc = (FARPROC)GetProcAddress(hPluginLibrary, "DVBSCTuneProcess");
		else
		if ( TunerType == DVBT )
			lpfnProc = (FARPROC)GetProcAddress(hPluginLibrary, "DVBTTuneProcess");
		else
		if ( TunerType == QAM )
			lpfnProc = (FARPROC)GetProcAddress(hPluginLibrary, "QAMTuneProcess");
		else
		if ( TunerType == ATSC )
			lpfnProc = (FARPROC)GetProcAddress(hPluginLibrary, "ATSCTuneProcess");

		if ( lpfnProc == NULL )
		{
			slog((env, "Plugin not found '%s\\%s' \r\n", regkey, PluginName ));
			FreeLibrary( hPluginLibrary );
			ret = -3;
		} else
		{
			pPluginCtrl->hPluginHandle = hPluginLibrary;
			pPluginCtrl->pfnProc = lpfnProc;
			pPluginCtrl->nTunerType = TunerType;
			pPluginCtrl->env = env; 
			slog((env, "Found Tuner Plugin '%s' \r\n", PluginName ));
			ret = 1;
		}
	} else
	{
		slog((env, "Tuner Plugin not found '%s\\%s' \r\n", regkey, PluginName ));
		ret = -4;
	}

	return ret;
}

void ReleaseTunerPlugin( JNIEnv *env, DShowCaptureInfo* pCapInfo )
{
	if ( pCapInfo->pTunerPluginCtrlData )
	{
		PLUGIN_CTRL* pPluginCtrl =(PLUGIN_CTRL*)pCapInfo->pTunerPluginCtrlData;
		if ( pPluginCtrl->hPluginHandle )
			FreeLibrary( (HINSTANCE)pPluginCtrl->hPluginHandle );
		delete pCapInfo->pTunerPluginCtrlData;
		pCapInfo->pTunerPluginCtrlData = NULL;
		slog((env, "Tuner Plugin released \r\n"  ));
	}
}

int TunerPluginDVBSCall( DShowCaptureInfo* pCapInfo, DVB_S_FREQ* dvbs )
{
	PLUGIN_CTRL* pPluginCtrl =(PLUGIN_CTRL*)pCapInfo->pTunerPluginCtrlData;
	if ( pPluginCtrl == NULL || pPluginCtrl->pfnProc == NULL ) return 0;
	JNIEnv *env = pPluginCtrl->env;
	char VideoCaptureName[256];
	char Channel[128];
	ASSERT( pPluginCtrl->nTunerType == DVBS );
	slog((env, "Tuner Plugin DVB-S was called on %s-%d \r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum  ));
	strncpy( VideoCaptureName, pCapInfo->videoCaptureFilterName, sizeof(VideoCaptureName) );
	SPRINTF( Channel, sizeof(Channel), "%d-%d-%d", dvbs->onid, dvbs->tsid, dvbs->sid );
	try {
		DVBSPROC pfnDVBSCall = (DVBSPROC)pPluginCtrl->pfnProc;
		pfnDVBSCall( VideoCaptureName, (unsigned short)pCapInfo->videoCaptureFilterNum, dvbs->sat_no, 
			         dvbs->frequency, (unsigned short)dvbs->symbol_rate, 
		             dvbs->modulation, dvbs->polarisation, Channel, "" );
	}
	catch (...)
	{
		slog((env, "Exception of DVBSTuneProcess in DVB-S tuner plugin \r\n"  ));
	}

	return 1;
}

int TunerPluginDVBCCall( DShowCaptureInfo* pCapInfo, DVB_C_FREQ* dvbc )
{
	PLUGIN_CTRL* pPluginCtrl =(PLUGIN_CTRL*)pCapInfo->pTunerPluginCtrlData;
	if ( pPluginCtrl == NULL || pPluginCtrl->pfnProc == NULL ) return 0;
	JNIEnv *env = pPluginCtrl->env;
	char VideoCaptureName[256];
	char Channel[128];
	ASSERT( pPluginCtrl->nTunerType == DVBC );
	strncpy( VideoCaptureName, pCapInfo->videoCaptureFilterName, sizeof(VideoCaptureName) );
	SPRINTF( Channel, sizeof(Channel), "%d-%d-%d", dvbc->onid, dvbc->tsid, dvbc->sid );
	slog((env, "Tuner Plugin DVB-C was called on %s-%d \r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum  ));
	try {
		DVBCPROC pfnDVBCCall = (DVBCPROC)pPluginCtrl->pfnProc;
		pfnDVBCCall( VideoCaptureName, (unsigned short)pCapInfo->videoCaptureFilterNum,  
			         dvbc->frequency, (unsigned short)dvbc->symbol_rate, 
		             dvbc->modulation, Channel, "" );
	}
	catch (...)
	{
		slog((env, "Exception of DVBCTuneProcess in DVB-C tuner plugin \r\n"  ));
	}
	return 1;
}

int TunerPluginDVBTCall( DShowCaptureInfo* pCapInfo, DVB_T_FREQ* dvbt )
{
	PLUGIN_CTRL* pPluginCtrl =(PLUGIN_CTRL*)pCapInfo->pTunerPluginCtrlData;
	if ( pPluginCtrl == NULL || pPluginCtrl->pfnProc == NULL ) return 0;
	JNIEnv *env = pPluginCtrl->env;
	char VideoCaptureName[256];
	char Channel[128];
	ASSERT( pPluginCtrl->nTunerType == DVBT );
	strncpy( VideoCaptureName, pCapInfo->videoCaptureFilterName, sizeof(VideoCaptureName) );
	SPRINTF( Channel, sizeof(Channel), "%d-%d-%d", dvbt->onid, dvbt->tsid, dvbt->sid );
	slog((env, "Tuner Plugin DVB-T was called on %s-%d \r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum  ));
	try {
		DVBTPROC pfnDVBTCall = (DVBTPROC)pPluginCtrl->pfnProc;
		pfnDVBTCall( VideoCaptureName, (unsigned short)pCapInfo->videoCaptureFilterNum,  
			         dvbt->frequency, (unsigned short)dvbt->bandwidth, 
		             Channel, "" );
	}
	catch (...)
	{
		slog((env, "Exception of DVBTTuneProcess in DVB-T tuner plugin \r\n"  ));
	}
	return 1;
}

int TunerPluginQAMCall( DShowCaptureInfo* pCapInfo, QAM_FREQ* qam )
{
	PLUGIN_CTRL* pPluginCtrl =(PLUGIN_CTRL*)pCapInfo->pTunerPluginCtrlData;
	if ( pPluginCtrl == NULL || pPluginCtrl->pfnProc == NULL ) return 0;
	JNIEnv *env = pPluginCtrl->env;
	char VideoCaptureName[256];
	char Channel[128];
	ASSERT( pPluginCtrl->nTunerType == QAM );
	strncpy( VideoCaptureName, pCapInfo->videoCaptureFilterName, sizeof(VideoCaptureName) );
	SPRINTF( Channel, sizeof(Channel), "%d-%d-%d-%d", qam->major, qam->minor, qam->tsid, qam->program );
	slog((env, "Tuner Plugin QAM was called on %s-%d \r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum  ));
	try {
		QAMPROC pfnQAMCall = (QAMPROC)pPluginCtrl->pfnProc;
		pfnQAMCall( VideoCaptureName, (unsigned short)pCapInfo->videoCaptureFilterNum,  
			         qam->frequency, (unsigned short)qam->physical_ch,  qam->modulation, 
					 Channel, "" );
	}
	catch (...)
	{
		slog((env, "Exception of QAMTuneProcess in QAM tuner plugin \r\n"  ));
	}
	return 1;
}

int TunerPluginATSCCall( DShowCaptureInfo* pCapInfo, ATSC_FREQ* atsc )
{
	PLUGIN_CTRL* pPluginCtrl =(PLUGIN_CTRL*)pCapInfo->pTunerPluginCtrlData;
	if ( pPluginCtrl == NULL || pPluginCtrl->pfnProc == NULL ) return 0;
	JNIEnv *env = pPluginCtrl->env;
	char VideoCaptureName[256];
	char Channel[128];
	ASSERT( pPluginCtrl->nTunerType == ATSC );
	strncpy( VideoCaptureName, pCapInfo->videoCaptureFilterName, sizeof(VideoCaptureName) );
	SPRINTF( Channel, sizeof(Channel), "%d-%d-%d", atsc->major, atsc->minor, atsc->program );
	slog((env, "Tuner Plugin ATSC was called on %s-%d \r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum  ));
	try {
		ATSCPROC pfnATSCCall = (ATSCPROC)pPluginCtrl->pfnProc;
		pfnATSCCall( VideoCaptureName, (unsigned short)pCapInfo->videoCaptureFilterNum,  
			         atsc->frequency, (unsigned short)atsc->physical_ch,
		             Channel, "" );
	}
	catch (...)
	{
		slog((env, "Exception of ATSCTuneProcess in ATSC tuner plugin \r\n"  ));
	}
	return 1;
}