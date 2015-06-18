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
// Win32ShellHook.cpp : Defines the entry point for the DLL application.
//

#include "stdafx.h"
#include <Windows.h>
#include <Winuser.h>

#include "WinKeyboardHook.h"
#include "sage_UserEvent.h"

//#define DEBUG_KB_HOOK_STV
//#define FUJITSU_BUILD
//#define ACER_BUILD
static HINSTANCE g_hInstance = NULL;
#ifdef FUJITSU_BUILD
static HANDLE hEvtKillerKB = NULL;
#endif

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
HWND hWinKeybaordNotifyWnd = NULL;
HHOOK hWinKeybaordHook = NULL;							// Handle to the Keyboard hook
#ifdef ACER_BUILD
HHOOK hWinMouseHook = NULL;							// Handle to the Mouse hook
#endif
#pragma data_seg( )

LRESULT CALLBACK WinKeyboardProc (int nCode, WPARAM wParam, LPARAM lParam);
#ifdef ACER_BUILD
LRESULT CALLBACK WinMouseProc (int nCode, WPARAM wParam, LPARAM lParam);
#endif

DllExport BOOL InstallWinKeyboardHook(HWND hWnd)
{
	if (hWnd == NULL) return FALSE;
	if (hWinKeybaordNotifyWnd != NULL) return FALSE;

	// Add the ShellProc hook
	hWinKeybaordHook = SetWindowsHookEx(
					WH_KEYBOARD_LL,						// Hook in before msg reaches app
					(HOOKPROC)WinKeyboardProc,			// Hook procedure
					g_hInstance,					// This DLL instance
					0L								// Hook in to all apps
					);

	if (hWinKeybaordHook != NULL)
	{
		hWinKeybaordNotifyWnd = hWnd;
#ifdef FUJITSU_BUILD
		hEvtKillerKB = CreateEvent(NULL, FALSE, FALSE, "Global\\STVEventKiller");
#endif
#ifdef ACER_BUILD
		hWinMouseHook = SetWindowsHookEx(
						WH_MOUSE_LL,						// Hook in before msg reaches app
						(HOOKPROC)WinMouseProc,			// Hook procedure
						g_hInstance,					// This DLL instance
						0L								// Hook in to all apps
						);

#endif
		return TRUE;
	}
	return FALSE;
}

DllExport BOOL RemoveWinKeyboardHook(HWND hWnd)
{
	if (hWnd != hWinKeybaordNotifyWnd || !hWnd) return FALSE;
	UnhookWindowsHookEx(hWinKeybaordHook);
	hWinKeybaordHook = NULL;
	hWinKeybaordNotifyWnd = NULL;
#ifdef FUJITSU_BUILD
	CloseHandle(hEvtKillerKB);
#endif
#ifdef ACER_BUILD
	if (hWinMouseHook)
	{
		UnhookWindowsHookEx(hWinMouseHook);
		hWinMouseHook = NULL;
	}
#endif
	return TRUE;
}

extern "C" {
#undef sage_UserEvent_LEFT
#define sage_UserEvent_LEFT 2L
#undef sage_UserEvent_RIGHT
#define sage_UserEvent_RIGHT 3L
#undef sage_UserEvent_UP
#define sage_UserEvent_UP 4L
#undef sage_UserEvent_DOWN
#define sage_UserEvent_DOWN 5L
#undef sage_UserEvent_PAUSE
#define sage_UserEvent_PAUSE 6L
#undef sage_UserEvent_PLAY
#define sage_UserEvent_PLAY 7L
#undef sage_UserEvent_FF
#define sage_UserEvent_FF 8L
#undef sage_UserEvent_REW
#define sage_UserEvent_REW 9L
#undef sage_UserEvent_TIME_SCROLL
#define sage_UserEvent_TIME_SCROLL 10L
#undef sage_UserEvent_CHANNEL_UP
#define sage_UserEvent_CHANNEL_UP 11L
#undef sage_UserEvent_CHANNEL_DOWN
#define sage_UserEvent_CHANNEL_DOWN 12L
#undef sage_UserEvent_VOLUME_UP
#define sage_UserEvent_VOLUME_UP 13L
#undef sage_UserEvent_VOLUME_DOWN
#define sage_UserEvent_VOLUME_DOWN 14L
#undef sage_UserEvent_TV
#define sage_UserEvent_TV 15L
#undef sage_UserEvent_FASTER
#define sage_UserEvent_FASTER 16L
#undef sage_UserEvent_SLOWER
#define sage_UserEvent_SLOWER 17L
#undef sage_UserEvent_GUIDE
#define sage_UserEvent_GUIDE 18L
#undef sage_UserEvent_POWER
#define sage_UserEvent_POWER 19L
#undef sage_UserEvent_SELECT
#define sage_UserEvent_SELECT 20L
#undef sage_UserEvent_WATCHED
#define sage_UserEvent_WATCHED 21L
#undef sage_UserEvent_RATE_UP
#define sage_UserEvent_RATE_UP 22L
#undef sage_UserEvent_RATE_DOWN
#define sage_UserEvent_RATE_DOWN 23L
#undef sage_UserEvent_INFO
#define sage_UserEvent_INFO 24L
#undef sage_UserEvent_RECORD
#define sage_UserEvent_RECORD 25L
#undef sage_UserEvent_MUTE
#define sage_UserEvent_MUTE 26L
#undef sage_UserEvent_FULL_SCREEN
#define sage_UserEvent_FULL_SCREEN 27L
#undef sage_UserEvent_HOME
#define sage_UserEvent_HOME 28L
#undef sage_UserEvent_OPTIONS
#define sage_UserEvent_OPTIONS 29L
#undef sage_UserEvent_NUM0
#define sage_UserEvent_NUM0 30L
#undef sage_UserEvent_NUM1
#define sage_UserEvent_NUM1 31L
#undef sage_UserEvent_NUM2
#define sage_UserEvent_NUM2 32L
#undef sage_UserEvent_NUM3
#define sage_UserEvent_NUM3 33L
#undef sage_UserEvent_NUM4
#define sage_UserEvent_NUM4 34L
#undef sage_UserEvent_NUM5
#define sage_UserEvent_NUM5 35L
#undef sage_UserEvent_NUM6
#define sage_UserEvent_NUM6 36L
#undef sage_UserEvent_NUM7
#define sage_UserEvent_NUM7 37L
#undef sage_UserEvent_NUM8
#define sage_UserEvent_NUM8 38L
#undef sage_UserEvent_NUM9
#define sage_UserEvent_NUM9 39L
#undef sage_UserEvent_SEARCH
#define sage_UserEvent_SEARCH 40L
#undef sage_UserEvent_SETUP
#define sage_UserEvent_SETUP 41L
#undef sage_UserEvent_LIBRARY
#define sage_UserEvent_LIBRARY 42L
#undef sage_UserEvent_POWER_ON
#define sage_UserEvent_POWER_ON 43L
#undef sage_UserEvent_POWER_OFF
#define sage_UserEvent_POWER_OFF 44L
#undef sage_UserEvent_MUTE_ON
#define sage_UserEvent_MUTE_ON 45L
#undef sage_UserEvent_MUTE_OFF
#define sage_UserEvent_MUTE_OFF 46L
#undef sage_UserEvent_AR_FILL
#define sage_UserEvent_AR_FILL 47L
#undef sage_UserEvent_AR_4X3
#define sage_UserEvent_AR_4X3 48L
#undef sage_UserEvent_AR_16X9
#define sage_UserEvent_AR_16X9 49L
#undef sage_UserEvent_AR_SOURCE
#define sage_UserEvent_AR_SOURCE 50L
#undef sage_UserEvent_VOLUME_UP2
#define sage_UserEvent_VOLUME_UP2 51L
#undef sage_UserEvent_VOLUME_DOWN2
#define sage_UserEvent_VOLUME_DOWN2 52L
#undef sage_UserEvent_CHANNEL_UP2
#define sage_UserEvent_CHANNEL_UP2 53L
#undef sage_UserEvent_CHANNEL_DOWN2
#define sage_UserEvent_CHANNEL_DOWN2 54L
#undef sage_UserEvent_PAGE_UP
#define sage_UserEvent_PAGE_UP 55L
#undef sage_UserEvent_PAGE_DOWN
#define sage_UserEvent_PAGE_DOWN 56L
#undef sage_UserEvent_PAGE_RIGHT
#define sage_UserEvent_PAGE_RIGHT 57L
#undef sage_UserEvent_PAGE_LEFT
#define sage_UserEvent_PAGE_LEFT 58L
#undef sage_UserEvent_PLAY_PAUSE
#define sage_UserEvent_PLAY_PAUSE 59L
#undef sage_UserEvent_PREV_CHANNEL
#define sage_UserEvent_PREV_CHANNEL 60L
#undef sage_UserEvent_FF_2
#define sage_UserEvent_FF_2 61L
#undef sage_UserEvent_REW_2
#define sage_UserEvent_REW_2 62L
#undef sage_UserEvent_LIVE_TV
#define sage_UserEvent_LIVE_TV 63L
#undef sage_UserEvent_DVD_REVERSE_PLAY
#define sage_UserEvent_DVD_REVERSE_PLAY 64L
#undef sage_UserEvent_DVD_CHAPTER_NEXT
#define sage_UserEvent_DVD_CHAPTER_NEXT 65L
#undef sage_UserEvent_DVD_CHAPTER_PREV
#define sage_UserEvent_DVD_CHAPTER_PREV 66L
#undef sage_UserEvent_DVD_MENU
#define sage_UserEvent_DVD_MENU 67L
#undef sage_UserEvent_DVD_TITLE_MENU
#define sage_UserEvent_DVD_TITLE_MENU 68L
#undef sage_UserEvent_DVD_RETURN
#define sage_UserEvent_DVD_RETURN 69L
#undef sage_UserEvent_DVD_SUBTITLE_CHANGE
#define sage_UserEvent_DVD_SUBTITLE_CHANGE 70L
#undef sage_UserEvent_DVD_SUBTITLE_TOGGLE
#define sage_UserEvent_DVD_SUBTITLE_TOGGLE 71L
#undef sage_UserEvent_DVD_AUDIO_CHANGE
#define sage_UserEvent_DVD_AUDIO_CHANGE 72L
#undef sage_UserEvent_DVD_ANGLE_CHANGE
#define sage_UserEvent_DVD_ANGLE_CHANGE 73L
#undef sage_UserEvent_DVD
#define sage_UserEvent_DVD 74L
#undef sage_UserEvent_BACK
#define sage_UserEvent_BACK 75L
#undef sage_UserEvent_FORWARD
#define sage_UserEvent_FORWARD 76L
#undef sage_UserEvent_CUSTOMIZE
#define sage_UserEvent_CUSTOMIZE 77L
#undef sage_UserEvent_CUSTOM1
#define sage_UserEvent_CUSTOM1 78L
#undef sage_UserEvent_CUSTOM2
#define sage_UserEvent_CUSTOM2 79L
#undef sage_UserEvent_CUSTOM3
#define sage_UserEvent_CUSTOM3 80L
#undef sage_UserEvent_CUSTOM4
#define sage_UserEvent_CUSTOM4 81L
#undef sage_UserEvent_CUSTOM5
#define sage_UserEvent_CUSTOM5 82L
#undef sage_UserEvent_DELETE
#define sage_UserEvent_DELETE 83L
#undef sage_UserEvent_MUSIC
#define sage_UserEvent_MUSIC 84L
#undef sage_UserEvent_SCHEDULE
#define sage_UserEvent_SCHEDULE 85L
#undef sage_UserEvent_RECORDINGS
#define sage_UserEvent_RECORDINGS 86L
#undef sage_UserEvent_PICTURE_LIBRARY
#define sage_UserEvent_PICTURE_LIBRARY 87L
#undef sage_UserEvent_VIDEO_LIBRARY
#define sage_UserEvent_VIDEO_LIBRARY 88L
#undef sage_UserEvent_STOP
#define sage_UserEvent_STOP 89L
#undef sage_UserEvent_EJECT
#define sage_UserEvent_EJECT 90L
#undef sage_UserEvent_STOP_EJECT
#define sage_UserEvent_STOP_EJECT 91L
#undef sage_UserEvent_INPUT
#define sage_UserEvent_INPUT 92L
#undef sage_UserEvent_SMOOTH_FF
#define sage_UserEvent_SMOOTH_FF 93L
#undef sage_UserEvent_SMOOTH_REW
#define sage_UserEvent_SMOOTH_REW 94L
#undef sage_UserEvent_VIDEO_OUTPUT
#define sage_UserEvent_VIDEO_OUTPUT 104L
#undef sage_UserEvent_ANYTHING
#define sage_UserEvent_ANYTHING 105L
#undef sage_UserEvent_MIN_EVT_ID
#define sage_UserEvent_MIN_EVT_ID 2L
#undef sage_UserEvent_MAX_EVT_ID
#define sage_UserEvent_MAX_EVT_ID 104L
}

#define EXT_CODE2  0xfff1
#define EXT_CODE3  0xfff2
typedef struct {
	unsigned char code;
	DWORD    SageEvnt;
	unsigned char toggle;
} EVNT_CODE;
EVNT_CODE CodeTbl1[] ={ 
	{0x50, sage_UserEvent_PLAY, 1}, 
	{0x50, sage_UserEvent_PAUSE,0},
    {0x42, sage_UserEvent_REW,0 },
	{0x46, sage_UserEvent_FF,0},
	{0x53, sage_UserEvent_STOP,0}, 
	{0x52, sage_UserEvent_RECORD,0}, 
	{0xa4, EXT_CODE2,0 },
	{0,0,0}
};

EVNT_CODE CodeTbl2[] ={ 
    {0x42, sage_UserEvent_SMOOTH_REW,0},
	{0x46, sage_UserEvent_SMOOTH_FF,0},
	{0xa0, EXT_CODE3,0 },
	{0,0}
};

EVNT_CODE CodeTbl3[] ={ 
	{0x24, sage_UserEvent_HOME,0},
	{0x32, sage_UserEvent_LIVE_TV,0},
	{0x33, sage_UserEvent_MUSIC,0},
	{0x34, sage_UserEvent_PICTURE_LIBRARY,0},
	{0x35, sage_UserEvent_VIDEO_LIBRARY,0 },
	{0x31, sage_UserEvent_DVD_MENU,0},
	{0x31, sage_UserEvent_INFO,0},
	{0,0}
};


LRESULT CALLBACK WinKeyboardProc(int nCode, WPARAM wParam, LPARAM lParam)
{
	static DWORD dwLastVKCode = 0;
	static DWORD dwEvtKillVKCode = 0;
	LPARAM SageEvent=0;
	// Call the next handler in the chain
	if (nCode == HC_ACTION && hWinKeybaordNotifyWnd != NULL ) 
	{
		KBDLLHOOKSTRUCT* p=(KBDLLHOOKSTRUCT*)lParam;
		DWORD vkCode = p->vkCode;
		int i;
		//code format: 0xa2 0x50 'play'; 0xa2 0x42 rewind; 0xa2 0x46; ff...
		//code series: 0xa2,0xa4,0xa0,0x31 'DVD';
#ifdef DEBUG_KB_HOOK_STV
		printf("KB Hook vkCode=%d scanCode=%d flags=%d\r\n", (int)p->vkCode, (int)p->scanCode, (int)p->flags);
#endif
		if ( (wParam & 0x01) == 0 && GetForegroundWindow() == hWinKeybaordNotifyWnd ) //key down
		{
			if ( dwLastVKCode == 0xa2  )
			{
				for ( i = 0; CodeTbl1[i].code; i++ )
					if ( CodeTbl1[i].code == vkCode )
					{
						if ( !CodeTbl1[i].toggle )
							SageEvent = CodeTbl1[i].SageEvnt;
						else
						{
							if ( CodeTbl1[i].toggle & 0x80 )
							{
								CodeTbl1[i].toggle &= ~0x80 ;//reset toggle, find next code
								continue;
							}
							else
							{	
								CodeTbl1[i].toggle |= 0x80;
								SageEvent = CodeTbl1[i].SageEvnt;

							}

						}
						break;
					}
			} else
			if ( dwLastVKCode == EXT_CODE2  )
			{
					for ( i = 0; CodeTbl2[i].code; i++ )
					if ( CodeTbl2[i].code == vkCode )
					{
						if ( !CodeTbl2[i].toggle )
							SageEvent = CodeTbl2[i].SageEvnt;
						else
						{
							if ( CodeTbl2[i].toggle & 0x80 )
							{
								CodeTbl2[i].toggle &= ~0x80 ;//reset toggle, find next code
								continue;
							}
							else
							{	
								CodeTbl2[i].toggle |= 0x80;
								SageEvent = CodeTbl2[i].SageEvnt;

							}
						}
						break;
					}

			} else
			if ( dwLastVKCode == EXT_CODE3  )
			{
					for ( i = 0; CodeTbl3[i].code; i++ )
					if ( CodeTbl3[i].code == vkCode )
					{
						if ( !CodeTbl3[i].toggle )
							SageEvent = CodeTbl3[i].SageEvnt;
						else
						{
							if ( CodeTbl3[i].toggle & 0x80 )
							{
								CodeTbl3[i].toggle &= ~0x80 ;//reset toggle, find next code
								continue;
							}
							else
							{	
								CodeTbl3[i].toggle |= 0x80;
								SageEvent = CodeTbl3[i].SageEvnt;
							}

						}
						break;
					}
			}

			dwLastVKCode = vkCode;
			
			if ( SageEvent )
			{
				if ( SageEvent != EXT_CODE2 && SageEvent != EXT_CODE3 )
				{
					PostMessage( hWinKeybaordNotifyWnd, WM_USER + 234,wParam, SageEvent);
					return 1;
				} else
				{
					dwLastVKCode = SageEvent;
				}
			}
		}
#ifdef ACER_BUILD
		if ((wParam & 0x01) == 0 && !SageEvent && vkCode == 255) // key down
		{
			switch (p->scanCode)
			{
				case 89:
					SageEvent = sage_UserEvent_LIBRARY; // touchstrip awake
					break;
				case 19:
					SageEvent = sage_UserEvent_SETUP; // touchstrip hide
					break;
				case 100:
					SageEvent = sage_UserEvent_CUSTOM1; // touchstrip 1
					break;
				case 68:
					SageEvent = sage_UserEvent_CUSTOM2; // touchstrip 2
					break;
				case 66:
					SageEvent = sage_UserEvent_CUSTOM3; // touchstrip 3
					break;
				case 64:
					SageEvent = sage_UserEvent_CUSTOM4; // touchstrip 4
					break;
				case 62:
					SageEvent = sage_UserEvent_CUSTOM5; // touchstrip 5
					break;
				case 15:
					SageEvent = sage_UserEvent_INPUT; // touchstrip 6
					break;
				case 41:
					SageEvent = sage_UserEvent_VIDEO_OUTPUT; // touchstrip 7
					break;
			}
			if (SageEvent)
			{
				PostMessage( hWinKeybaordNotifyWnd, WM_USER + 234,wParam, SageEvent);
				//return 1;
			}
		}
#endif

#ifdef FUJITSU_BUILD
		if (dwEvtKillVKCode == vkCode)
		{
			if (p->flags == 128)
			{
				// Released, stop killing this key event
				dwEvtKillVKCode = 0;
			}
			return 1;
		}
		if (vkCode == 27 || vkCode == 13)
		{
			// Check for the signal to kill this event
			if (WaitForSingleObject(hEvtKillerKB, 0) == WAIT_OBJECT_0)
			{
				dwEvtKillVKCode = vkCode;
				return 1;
			}
		}
#endif
	}

    return CallNextHookEx (hWinKeybaordHook, nCode, wParam, lParam);
}

#ifdef ACER_BUILD
LRESULT CALLBACK WinMouseProc(int nCode, WPARAM wParam, LPARAM lParam)
{
	BOOL bottomHover = FALSE;
	if (wParam == WM_MOUSEMOVE)
	{
		MSLLHOOKSTRUCT* p=(MSLLHOOKSTRUCT*)lParam;
		int yPos = p->pt.y;
		int screenHeight = GetSystemMetrics(SM_CYSCREEN);
		if (screenHeight - yPos < 3)
		{
			bottomHover = TRUE;
		}
	}
	if (bottomHover)
	{
		PostMessage( hWinKeybaordNotifyWnd, WM_USER + 234,wParam, sage_UserEvent_SCHEDULE);
	}
    return CallNextHookEx (hWinMouseHook, nCode, wParam, lParam);
}
#endif
