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
// from a DLL simpler. All files within this DLL are compiled with the DSHOWCAPTURE_EXPORTS
// symbol defined on the command line. this symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// DSHOWCAPTURE_API functions as being imported from a DLL, wheras this DLL sees symbols
// defined with this macro as being exported.

#ifndef _H_INCLUDE_DSHOWCAPTURE_H_
#define _H_INCLUDE_DSHOWCAPTURE_H_

#ifdef DSHOWCAPTURE_EXPORTS
#define DSHOWCAPTURE_API __declspec(dllexport)
#else
#define DSHOWCAPTURE_API __declspec(dllimport)
#endif

#include <bdatif.h>   //ZQ
#include <tuner.h>    //ZQ        
#include <tune.h>     //ZQ       

#ifdef WIN32
#include <pshpack1.h>
#endif

#define CAPTURE_NUM 4
typedef struct TUNING_TBL
{
	unsigned short  channel;
	unsigned short  onid;
	unsigned short  tsid;
	unsigned short  program;
	unsigned long	freq;
	unsigned long	band;
	unsigned long   ctrl;
	unsigned short  modulation;
	//unsigned char	audio;
	char			comment[20];
} TUNING_TBL;

typedef struct DShowCaptureInfo
{
	char TAG[8];
	ICaptureGraphBuilder2* pBuilder;
	IGraphBuilder* pGraph;
	IBaseFilter* pVideoCaptureFilter;
	char videoCaptureFilterName[256];
	int  videoCaptureFilterNum;
	char videoCaptureFilterNameOrg[256];  //we use a tag<> at end of Filter name, need save orignal name for posting messge
	char audioCaptureFilterName[256];
	int  audioCaptureFilterNum;
	int  audioCaptureFilterSrcIndex;
	char videoProcessorName[256];
	char audioProcessorName[256];
	char bdaCaptureFilterName[256];  //ZQ for BDA
	char bdaTunerFilterName[256];    //ZQ for BDA
	int captureConfig;
	DWORD dwRegister;
	IMediaControl* pMC;
	IBaseFilter* pSink;
	IBaseFilter* pCCSink;
	IBaseFilter* pTVTuner;
	IBaseFilter* pTVAudio;
	IBaseFilter* pCrossbar;
	IBaseFilter* pEncoder;
	IBaseFilter* pVideoCompressor;
	IBaseFilter* pAudioCompressor;
	IBaseFilter* pAudioCaptureFilter;

    //ZQ for BDA
	int	filterState;    
	DWORD dwBDAType;
	DWORD dwTuneState;
	ITuningSpace* pTuningSpace;         
	IScanningTuner* pTuner;              
	IBaseFilter* pBDANetworkProvider;  
	IBaseFilter* pBDATuner;
	IBaseFilter* pBDADemod;
	IBaseFilter* pBDACapture;
	IBaseFilter* pBDADemux;
	IBaseFilter* pBDATIF;
	IBaseFilter* pBDASectionTbl;

	//IBaseFilter* pTeeFilter;
	IBaseFilter* pSplitter;
	IBaseFilter* pDebugSink;
	IBaseFilter* pDebugSrcSink;
	IBaseFilter* pPrePlugin;  //MDAPI filter may use

 	IBaseFilter* pResync;
	char encodeQuality[256];
	void* encodeParams;
	jlong recordBufferSize;
	DWORD videoFormatCode;
	char deviceName[1024];
	char TuningMode[16];
	char tvType[16];
	char Country[80];
	IVideoWindow* pPreviewVideo;
	
	void* channel; //BDA channel
	void* pCamCtrlData;
	void* pQAMCtrlData;
	void* pTunerPluginCtrlData;
	char  TunerIDName[256];

	void* pDiSEQC;    //for DVB-S DisSEQC
	char  virtualTunerParam[128];
	unsigned long virtualTunerMask;

	unsigned long EPG_post_counter;
	unsigned long EPG_post_time;
	int EPG_post_flow_rate_ctrl;

	int captureNum;
	DShowCaptureInfo* captures[CAPTURE_NUM];

} DShowCaptureInfo;

//define SageTV error code that starts from 0x81000xxx  ZQ.
//we'll put into sage java code later
#define E_FREQUENCY_NOT_UNDEFINED  0x81000201
#define E_CHANNEL_DISABLED		   0x81000202

#ifdef WIN32
#include <poppack.h>
#endif

extern HANDLE mutex350Encoder;
#endif
