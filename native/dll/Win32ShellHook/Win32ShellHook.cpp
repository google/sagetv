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
#include "Win32ShellHook.h"
#include "sage_UserEvent.h"

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
HWND hShellNotifyWnd = NULL;
HHOOK hShellHook = NULL;							// Handle to the Shell hook
#pragma data_seg( )

LRESULT CALLBACK ShellProc (int nCode, WPARAM wParam, LPARAM lParam);

DllExport BOOL InstallShellHook(HWND hWnd)
{
	if (hWnd == NULL) return FALSE;
	if (hShellNotifyWnd != NULL) return FALSE;

	// Add the ShellProc hook
	hShellHook = SetWindowsHookEx(
					WH_SHELL,					// Hook in before msg reaches app
					(HOOKPROC) ShellProc,			// Hook procedure
					g_hInstance,						// This DLL instance
					0L								// Hook in to all apps
					);
	if (hShellHook != NULL)
	{
		hShellNotifyWnd = hWnd;
		return TRUE;
	}
	return FALSE;
}

DllExport BOOL RemoveShellHook(HWND hWnd)
{
	if (hWnd != hShellNotifyWnd || !hWnd) return FALSE;
	UnhookWindowsHookEx(hShellHook);
	hShellHook = NULL;
	hShellNotifyWnd = NULL;
	return TRUE;
}

// Hook procedure for Shell hook
#define HSHELL_APPCOMMAND           12
#define FAPPCOMMAND_MASK  0xF000
#define GET_APPCOMMAND_LPARAM(lParam) ((short)(HIWORD(lParam) & ~FAPPCOMMAND_MASK))
#define WM_APPCOMMAND                   0x0319

#define APPCOMMAND_BROWSER_BACKWARD       1
#define APPCOMMAND_BROWSER_FORWARD        2
#define APPCOMMAND_BROWSER_REFRESH        3
#define APPCOMMAND_BROWSER_STOP           4
#define APPCOMMAND_BROWSER_SEARCH         5
#define APPCOMMAND_BROWSER_FAVORITES      6
#define APPCOMMAND_BROWSER_HOME           7
#define APPCOMMAND_VOLUME_MUTE            8
#define APPCOMMAND_VOLUME_DOWN            9
#define APPCOMMAND_VOLUME_UP              10
#define APPCOMMAND_MEDIA_NEXTTRACK        11
#define APPCOMMAND_MEDIA_PREVIOUSTRACK    12
#define APPCOMMAND_MEDIA_STOP             13
#define APPCOMMAND_MEDIA_PLAY_PAUSE       14
#define APPCOMMAND_LAUNCH_MAIL            15
#define APPCOMMAND_LAUNCH_MEDIA_SELECT    16
#define APPCOMMAND_LAUNCH_APP1            17
#define APPCOMMAND_LAUNCH_APP2            18
#define APPCOMMAND_BASS_DOWN              19
#define APPCOMMAND_BASS_BOOST             20
#define APPCOMMAND_BASS_UP                21
#define APPCOMMAND_TREBLE_DOWN            22
#define APPCOMMAND_TREBLE_UP              23
#define APPCOMMAND_MICROPHONE_VOLUME_MUTE 24
#define APPCOMMAND_MICROPHONE_VOLUME_DOWN 25
#define APPCOMMAND_MICROPHONE_VOLUME_UP   26
#define APPCOMMAND_HELP                   27
#define APPCOMMAND_FIND                   28
#define APPCOMMAND_NEW                    29
#define APPCOMMAND_OPEN                   30
#define APPCOMMAND_CLOSE                  31
#define APPCOMMAND_SAVE                   32
#define APPCOMMAND_PRINT                  33
#define APPCOMMAND_UNDO                   34
#define APPCOMMAND_REDO                   35
#define APPCOMMAND_COPY                   36
#define APPCOMMAND_CUT                    37
#define APPCOMMAND_PASTE                  38
#define APPCOMMAND_REPLY_TO_MAIL          39
#define APPCOMMAND_FORWARD_MAIL           40
#define APPCOMMAND_SEND_MAIL              41
#define APPCOMMAND_SPELL_CHECK            42
#define APPCOMMAND_DICTATE_OR_COMMAND_CONTROL_TOGGLE    43
#define APPCOMMAND_MIC_ON_OFF_TOGGLE      44
#define APPCOMMAND_CORRECTION_LIST        45
#define APPCOMMAND_MEDIA_PLAY             46
#define APPCOMMAND_MEDIA_PAUSE            47
#define APPCOMMAND_MEDIA_RECORD           48
#define APPCOMMAND_MEDIA_FAST_FORWARD     49
#define APPCOMMAND_MEDIA_REWIND           50
#define APPCOMMAND_MEDIA_CHANNEL_UP       51
#define APPCOMMAND_MEDIA_CHANNEL_DOWN     52

LRESULT CALLBACK ShellProc(int nCode, WPARAM wParam, LPARAM lParam)
{
	// Do we have to handle this message?
	if (nCode == HSHELL_APPCOMMAND)
	{
		// Process the hook if the hNotifyWnd window handle is valid
		if (hShellNotifyWnd != NULL)
		{
			short AppCommand = GET_APPCOMMAND_LPARAM(lParam);
			LPARAM testParam = 0;
			switch (AppCommand)
			{
				case APPCOMMAND_BROWSER_BACKWARD:
					testParam = sage_UserEvent_BACK;
					break;
				case APPCOMMAND_BROWSER_FORWARD:
					testParam = sage_UserEvent_FORWARD;
					break;
				case APPCOMMAND_VOLUME_MUTE:
					testParam = sage_UserEvent_MUTE;
					break;
				case APPCOMMAND_VOLUME_DOWN:
					testParam = sage_UserEvent_VOLUME_DOWN;
					break;
				case APPCOMMAND_VOLUME_UP:
					testParam = sage_UserEvent_VOLUME_UP;
					break;
				case APPCOMMAND_MEDIA_STOP:
					testParam = sage_UserEvent_STOP;
					break;
				case APPCOMMAND_MEDIA_PLAY_PAUSE:
					testParam = sage_UserEvent_PLAY_PAUSE;
					break;
				case APPCOMMAND_MEDIA_NEXTTRACK:
					testParam = sage_UserEvent_FF_2;
					break;
				case APPCOMMAND_MEDIA_PREVIOUSTRACK:
					testParam = sage_UserEvent_REW_2;
					break;
//				case APPCOMMAND_BROWSER_FAVORITES:
//					testParam = sage_UserEvent_RATE_UP;
//					break;
//				case APPCOMMAND_BROWSER_HOME:
//					testParam = sage_UserEvent_HOME;
//					break;
//				case APPCOMMAND_BROWSER_SEARCH:
//					testParam = sage_UserEvent_SEARCH;
//					break;
				case APPCOMMAND_MEDIA_PLAY:
					testParam = sage_UserEvent_PLAY;
					break;
				case APPCOMMAND_MEDIA_PAUSE:
					testParam = sage_UserEvent_PAUSE;
					break;
				case APPCOMMAND_MEDIA_RECORD:
					testParam = sage_UserEvent_RECORD;
					break;
				case APPCOMMAND_MEDIA_FAST_FORWARD:
					testParam = sage_UserEvent_FF;
					break;
				case APPCOMMAND_MEDIA_REWIND:
					testParam = sage_UserEvent_REW;
					break;
				case APPCOMMAND_MEDIA_CHANNEL_UP:
					testParam = sage_UserEvent_CHANNEL_UP;
					break;
				case APPCOMMAND_MEDIA_CHANNEL_DOWN:
					testParam = sage_UserEvent_CHANNEL_DOWN;
					break;
			}
			if (testParam)
			{
				PostMessage(hShellNotifyWnd,WM_USER + 234,wParam,testParam);
				return 1; // dont call CallNextHookEx, instead return non-zero, because we have handled the message (see MSDN doc)
			}
		}
	}

	// Call the next handler in the chain
    return CallNextHookEx (hShellHook, nCode, wParam, lParam);
}
