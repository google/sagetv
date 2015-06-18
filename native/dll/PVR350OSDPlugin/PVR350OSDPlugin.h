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
// from a DLL simpler. All files within this DLL are compiled with the PVR350OSDPLUGIN_EXPORTS
// symbol defined on the command line. this symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// PVR350OSDPLUGIN_API functions as being imported from a DLL, wheras this DLL sees symbols
// defined with this macro as being exported.
#ifdef PVR350OSDPLUGIN_EXPORTS
#define PVR350OSDPLUGIN_API __declspec(dllexport)
#else
#define PVR350OSDPLUGIN_API __declspec(dllimport)
#endif

typedef struct
{
	DWORD lastOSDMemWrite;// = 0;
	DWORD ntscModeFor350;// = 1;
	IBaseFilter* pOSD;
	IFilterGraph* pGraph;
	HANDLE mutex350OSD;
} PVR350OSDPluginNativeData;

static const GUID PROPSETID_IVAC_CONTROL = 
{ 0xee6c0342, 0xe3a9, 0x11d2, { 0xa5, 0x3e, 0x00, 0xa0, 0xc9, 0xef, 0x50, 0x6a } };

#undef TEST_AND_PRINT
#ifdef _DEBUG
#define TEST_AND_PRINT if (FAILED(hr)){ elog((env, "350OSD NATIVE WARNING (non-FAILURE) line %d hr=0x%x\r\n", __LINE__, hr));}
#else
#define TEST_AND_PRINT 0;
#endif

#define TEST_AND_BAIL if (FAILED(hr)){elog((env, "350OSD NATIVE FAILURE line %d hr=0x%x\r\n", __LINE__, hr));return JNI_FALSE;}

jboolean Print350OSDInfo(PVR350OSDPluginNativeData* pOSD, JNIEnv* env, BOOL clearMem);
