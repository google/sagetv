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
// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)

#define _USE_32BIT_TIME_T

#include <streams.h>
#include <initguid.h>

//#include "psiobj.h"
#include "iTSSplitter.h"
#include "TSSplitterGuid.h"

#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"
#include "Remuxer.h"
#include "FilterProperties.h"
#include "../../../third_party/Microsoft/TSSplitter/TSSplitFilter.h"

// Filter setup data

const AMOVIESETUP_MEDIATYPE sudOpPinTypes =
{
    &MEDIATYPE_Video,       // Major type
    &MEDIASUBTYPE_MPEG2_TRANSPORT      // Minor type
};

const AMOVIESETUP_PIN sudOutputPins[] = 
{
  {
    L"TSPin",   // Obsolete, not used.
    FALSE,          // Is this pin rendered?
    TRUE,           // Is it an output pin?
    FALSE,          // Can the filter create zero instances?
    FALSE,          // Does the filter create multiple instances?
    &CLSID_NULL,    // Obsolete.
    NULL,           // Obsolete.
    1,              // Number of media types.
    &sudOpPinTypes  // Pointer to media types.
 },
  {
    L"VideoPin",   // Obsolete, not used.
    FALSE,          // Is this pin rendered?
    TRUE,           // Is it an output pin?
    FALSE,          // Can the filter create zero instances?
    FALSE,          // Does the filter create multiple instances?
    &CLSID_NULL,    // Obsolete.
    NULL,           // Obsolete.
    1,              // Number of media types.
    &sudOpPinTypes  // Pointer to media types.
 },
  {
    L"AudioPin",   // Obsolete, not used.
    FALSE,          // Is this pin rendered?
    TRUE,           // Is it an output pin?
    FALSE,          // Can the filter create zero instances?
    FALSE,          // Does the filter create multiple instances?
    &CLSID_NULL,    // Obsolete.
    NULL,           // Obsolete.
    1,              // Number of media types.
    &sudOpPinTypes  // Pointer to media types.
 },
  {
    L"PassThrusPin",      // Obsolete, not used.
    FALSE,          // Is this pin rendered?
    TRUE,           // Is it an output pin?
    FALSE,          // Can the filter create zero instances?
    FALSE,          // Does the filter create multiple instances?
    &CLSID_NULL,    // Obsolete.
    NULL,           // Obsolete.
    1,              // Number of media types.
    &sudOpPinTypes  // Pointer to media types.
 }


};

// Setup data
const AMOVIESETUP_FILTER sudTsParser =
{
    &CLSID_TSParser,                // Filter CLSID
    L"SageTV TS Splitter 3.0",      // String name
    MERIT_DO_NOT_USE,               // Filter merit
    4,
    sudOutputPins
};
                
        
// List of class IDs and creator functions for class factory
CFactoryTemplate g_Templates []  = {
    { L"SageTV TS Splitter 1.0", &CLSID_TSParser, CTSParserFilter::CreateInstance, NULL, &sudTsParser },
	{ L"Filter Property Page", &CLSID_FilterPropertyPage , CFilterProperties::CreateInstance, NULL, NULL }
};
int g_cTemplates = sizeof(g_Templates) / sizeof(g_Templates[0]);

STDAPI DllRegisterServer()
{
    return AMovieDllRegisterServer2( TRUE );
}

STDAPI DllUnregisterServer()
{
    return AMovieDllRegisterServer2( FALSE );
}

//
// DllEntryPoint
//
extern "C" BOOL WINAPI DllEntryPoint(HINSTANCE, ULONG, LPVOID);
#include <process.h>
extern "C" void __security_init_cookie();
BOOL APIENTRY DllMain(HANDLE hModule, 
                      DWORD  dwReason, 
                      LPVOID lpReserved)
{
	static bool initialized = false;

	if ( !initialized )
		__security_init_cookie();
	initialized = true;
	return DllEntryPoint((HINSTANCE)(hModule), dwReason, lpReserved);
}

 
