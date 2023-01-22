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
#include <windows.h>
#include <winuser.h>
#include "sage_UserEvent.h"
#include "WinRawInput.h"

//#define FUJITSU_BUILD
static HINSTANCE g_hInstance = NULL;


BOOL APIENTRY DllMain( HANDLE hModule, 
                       DWORD  ul_reason_for_call, 
                       LPVOID lpReserved
					 )
{
    switch (ul_reason_for_call)
	{
		case DLL_PROCESS_ATTACH:
			g_hInstance = (HINSTANCE)hModule;
		case DLL_THREAD_ATTACH:
		case DLL_THREAD_DETACH:
		case DLL_PROCESS_DETACH:
			break;
    }
    return TRUE;
}

#pragma data_seg(".shared")
#pragma data_seg( )
void   CloseRawInputProxyWnd( HANDLE hHandle );
HANDLE OpenRawInputProxyWnd(HWND hWnd) ;
static HANDLE hRawInputHandle = NULL;
int ignorePowerOff = 0;

DllExport BOOL InstallWinRawInput(HWND hWnd)
{
	if ( hWnd == NULL ) return FALSE;
	if ( hRawInputHandle!= NULL ) return FALSE;
		hRawInputHandle = OpenRawInputProxyWnd((HWND)hWnd);
	HKEY rootKey = HKEY_LOCAL_MACHINE;
	HKEY myKey;
	DWORD readType;
	DWORD dwRead = 0;
	DWORD hsize = sizeof(dwRead);
	if (RegOpenKeyEx(rootKey, "Software\\Frey Technologies\\SageTV", 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "IgnoreMCEPowerOff", 0, &readType, (LPBYTE)&dwRead, &hsize) == ERROR_SUCCESS)
		{
			if (dwRead)
				ignorePowerOff = 1;
		}
		RegCloseKey(myKey);
	}
	return hRawInputHandle != NULL;
}

DllExport BOOL RemoveWinRawInput()
{
	CloseRawInputProxyWnd( hRawInputHandle );
	hRawInputHandle= NULL;
	return TRUE;
}

#define RAWINPUT_DETAILS		0x09
#define RAWINPUT_GUIDE			0x8D
#define RAWINPUT_TVJUMP			0x25
#define RAWINPUT_STANDBY		0x82
#define RAWINPUT_OEM1			0x80
#define RAWINPUT_OEM2			0x81
#define RAWINPUT_MYTV			0x46
#define RAWINPUT_MYVIDEOS		0x4A
#define RAWINPUT_MYPICTURES		0x49
#define RAWINPUT_MYMUSIC		0x47
#define RAWINPUT_RECORDEDTV		0x48
#define RAWINPUT_DVDANGLE		0x4B
#define RAWINPUT_DVDAUDIO		0x4C
#define RAWINPUT_DVDMENU		0x24
#define RAWINPUT_DVDSUBTITLE	0x4D
#define RAWINPUT_OEM_RED		0x5B
#define RAWINPUT_OEM_GREEN		0x5C
#define RAWINPUT_OEM_YELLOW		0x5D
#define RAWINPUT_OEM_BLUE		0x5E
#define RAWINPUT_GREEN_HOME		0x0d
#define RAWINPUT_QUIT			0x68


/*
 * Class:     sage_Sage
 * Method:    setupRawInput0
 * Signature: (J)Z
 */
JNIEXPORT jlong JNICALL Java_sage_Sage_setupRawInput0
  (JNIEnv *env, jclass jc, jlong jhwnd)
{
	if (!jhwnd ) return JNI_FALSE;
	hRawInputHandle = OpenRawInputProxyWnd((HWND)jhwnd);
	return (jlong)hRawInputHandle;
}

/*
 * Class:     sage_Sage
 * Method:    releaseSystemHooks0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_Sage_releaseRawInput0
  (JNIEnv *env, jclass jc )
{
	CloseRawInputProxyWnd( hRawInputHandle );
	hRawInputHandle = NULL;
}

static WORD GetRawInputCode( void* RawInData, BYTE* pCode )
{
	WORD ret = 0;
	UINT dwSize=0;
	int flag;
	HRAWINPUT hRawInput = (HRAWINPUT)RawInData;
	flag = GetRawInputData( hRawInput, RID_INPUT, NULL, &dwSize, 
                    sizeof(RAWINPUTHEADER));

	if ( flag < 0 || dwSize == 0 || dwSize > 16*1024 )
		return 0;

    LPBYTE lpb = new BYTE[dwSize];

	*pCode = 0;
    if (lpb == NULL) 
    {
        return 0;
    } 

    if (  GetRawInputData( hRawInput, RID_INPUT, lpb, &dwSize, 
									sizeof(RAWINPUTHEADER)) != dwSize )
	{
		delete [] lpb;
		return 0;
	}
    RAWINPUT* raw = (RAWINPUT*)lpb;

	if (raw->header.dwType == RIM_TYPEHID ) 
    {
		*pCode = raw->data.hid.bRawData[0];
		if ( raw->data.hid.dwSizeHid > 1 )
			ret = raw->data.hid.bRawData[1];
	}

	delete [] lpb;
	return ret;
}
static HWND gRawInputWnd = NULL;
static WORD LastCmd = 0;
static char* WndClassName = "ProxyWClass";
#ifdef FUJITSU_BUILD
static HANDLE hEvtKiller = NULL;
#endif

static LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)
{
	if ( message ==  WM_DESTROY )
	{
		DestroyWindow( (HWND)hWnd );
		UnregisterClass(  WndClassName, g_hInstance );
	}

	if ( WM_INPUT == message && gRawInputWnd != NULL  )
	{
		BYTE state;
		WORD code = GetRawInputCode( (void*)lParam, &state );
		LPARAM Param = 0;
#ifdef FUJITSU_BUILD
		if (code == 94 || code == 96)
		{
			// Check to make sure we're the foreground application
			HWND fWin = GetForegroundWindow();
			if (fWin)
			{
				char nameBuf[512];
				int numChars = GetWindowModuleFileName(fWin, nameBuf, 512);
				nameBuf[numChars] = '\0';
				if (strstr(nameBuf, "awt.dll"))
				{
					SetEvent(hEvtKiller);
				}
			}
			return 1;
		}
#endif
		switch (code) {
			case RAWINPUT_DETAILS:	
				 Param = sage_UserEvent_INFO; ; break;
			case RAWINPUT_GUIDE:	
				 Param = sage_UserEvent_GUIDE;  break;
			case RAWINPUT_TVJUMP:	
				 Param = sage_UserEvent_LIVE_TV; break;	
			case RAWINPUT_QUIT:
				 Param = sage_UserEvent_POWER_OFF; break;
			case RAWINPUT_STANDBY:	
				 Param = 0; break;
			case RAWINPUT_OEM1:		
				 Param = sage_UserEvent_CUSTOM1; break;
			case RAWINPUT_OEM2:		
				 Param = sage_UserEvent_CUSTOM2; break;
			case RAWINPUT_MYTV:		
				 Param = sage_UserEvent_RECORDINGS; break;
			case RAWINPUT_MYVIDEOS:	
				 Param = sage_UserEvent_VIDEO_LIBRARY; break;
			case RAWINPUT_MYPICTURES:
				 Param = sage_UserEvent_PICTURE_LIBRARY; break;	
			case RAWINPUT_MYMUSIC:	
				 Param = sage_UserEvent_MUSIC; break;
			case RAWINPUT_RECORDEDTV:
				 Param = sage_UserEvent_RECORDINGS; break;	
			case RAWINPUT_DVDANGLE:	
				 Param = sage_UserEvent_DVD_ANGLE_CHANGE; break;
			case RAWINPUT_DVDAUDIO:	
				 Param = sage_UserEvent_DVD_AUDIO_CHANGE; break;
			case RAWINPUT_DVDMENU:	
				 Param = sage_UserEvent_DVD_MENU; break;
			case RAWINPUT_DVDSUBTITLE:
				 Param = sage_UserEvent_DVD_TITLE_MENU; break;	
			case RAWINPUT_OEM_RED:
				 Param = sage_UserEvent_RATE_DOWN; break;	
			case RAWINPUT_OEM_GREEN:
				 Param = sage_UserEvent_RATE_UP; break;	
			case RAWINPUT_OEM_YELLOW:
				 Param = sage_UserEvent_OPTIONS; break;	
			case RAWINPUT_OEM_BLUE:
				 Param = sage_UserEvent_WATCHED; break;	
			case RAWINPUT_GREEN_HOME:
				 Param = sage_UserEvent_HOME; break;	
			case 0:
#ifndef FUJITSU_BUILD
				if ( LastCmd == 0 )
					 Param = sage_UserEvent_HOME;
				 else
#endif
					 Param = 0;
				 break;

		}
		if (!ignorePowerOff || Param != sage_UserEvent_POWER_OFF)
		{
			LastCmd = code;
			if ( Param )
				PostMessage(gRawInputWnd,WM_USER + 234,wParam,Param);
			return 1;
		}		
	}
	
 	return DefWindowProc(hWnd, message, wParam, lParam);
}
ATOM regcls;
HANDLE OpenRawInputProxyWnd(HWND hMsgWnd) 
{ 
	HINSTANCE hinstance = g_hInstance;
	WNDCLASSEX wcx={0}; 
	HWND hWnd=NULL;
	gRawInputWnd = hMsgWnd;

	wcx.cbSize = sizeof(wcx);          // size of structure 
	wcx.style = CS_GLOBALCLASS ;					   // redraw if size changes 
	wcx.lpfnWndProc = WndProc;		   // points to window procedure 
	wcx.hInstance = hinstance;         // handle to instance 
	wcx.lpszMenuName =  "";    // name of menu resource 
	wcx.lpszClassName = WndClassName;  // name of window class 

	if ( !RegisterClassEx(&wcx) ) 
	{
		DWORD code = GetLastError();
		return 0;
	}

	hWnd = CreateWindow(
		"ProxyWClass",        // name of window class 
		"",            // title-bar string 
		0, // top-level window 
		0,		   // default horizontal position 
		0,        // default vertical position 
		0,        // default width 
		0,        // default height 
		(HWND) NULL,         // no owner window 
		(HMENU) NULL,        // use class menu 
		hinstance,				 // handle to application instance 
		(LPVOID) NULL);      // no window-creation data 

	if ( hWnd != NULL )
	{
		RAWINPUTDEVICE rid[3]={0};
		UINT num=3;

		rid[0].usUsagePage = 0xFFBC;      // adds HID remote control
		rid[0].usUsage = 0x88;
		rid[0].dwFlags = RIDEV_INPUTSINK;
		rid[0].hwndTarget = hWnd;

		rid[1].usUsagePage = 0x0C;      // adds HID remote control
		rid[1].usUsage = 0x01;
		rid[1].dwFlags = RIDEV_INPUTSINK;
		rid[1].hwndTarget = hWnd;

		rid[2].usUsagePage = 0x0C;      // adds HID remote control
		rid[2].usUsage = 0x80;
		rid[2].dwFlags = RIDEV_INPUTSINK;
		rid[2].hwndTarget = hWnd;
		if ( RegisterRawInputDevices(rid, 3, sizeof(RAWINPUTDEVICE) )== FALSE) 
		{	
			CloseRawInputProxyWnd( hWnd );
			hWnd = NULL;
		}
	}
	LastCmd = 0;

#ifdef FUJITSU_BUILD
	hEvtKiller = CreateEvent(NULL, FALSE, FALSE, "Global\\STVEventKiller");
#endif
	return  (HANDLE)hWnd;

}

void CloseRawInputProxyWnd( HANDLE hHandle )
{
	if ( hHandle != NULL )
	{
		RAWINPUTDEVICE rid[3]={0};
		UINT num=3;

		rid[0].usUsagePage = 0xFFBC;      // adds HID remote control
		rid[0].usUsage = 0x88;
		rid[0].dwFlags = RIDEV_REMOVE;
		rid[0].hwndTarget = 0;

		rid[1].usUsagePage = 0x0C;      // adds HID remote control
		rid[1].usUsage = 0x01;
		rid[1].dwFlags = RIDEV_REMOVE;
		rid[1].hwndTarget = 0;

		rid[2].usUsagePage = 0x0C;      // adds HID remote control
		rid[2].usUsage = 0x80;
		rid[2].dwFlags = RIDEV_REMOVE;
		rid[2].hwndTarget = 0;
		RegisterRawInputDevices(rid, 3, sizeof(RAWINPUTDEVICE) ); 
		SendMessage((HWND)hHandle, WM_DESTROY, NULL, NULL );
#ifdef FUJITSU_BUILD
		if (hEvtKiller)
			CloseHandle(hEvtKiller);
#endif
	}
}


