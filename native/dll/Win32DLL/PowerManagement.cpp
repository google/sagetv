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
#include "../../include/sage_PowerManagement.h"

/*
 * Class:     sage_PowerManagement
 * Method:    setPowerState0
 * Signature: (I)V
 */
static BOOL disabledSS = FALSE;
JNIEXPORT void JNICALL Java_sage_PowerManagement_setPowerState0
  (JNIEnv *env, jobject jo, jint powerState)
{
	EXECUTION_STATE es = 0;
	if (powerState & sage_PowerManagement_SYSTEM_POWER)
		es |= ES_SYSTEM_REQUIRED;
	if (powerState & sage_PowerManagement_DISPLAY_POWER)
		es |= ES_DISPLAY_REQUIRED;
	// This one will reset the idle timer so we'll be up for the PM timeout; which is good for when there's user activity
	if (powerState & sage_PowerManagement_USER_ACTIVITY)
		SetThreadExecutionState(es);
	// This one doesn't involve timeouts; it just tells the system if we need display or system power
	es |= ES_CONTINUOUS;
	SetThreadExecutionState(es);

	// See if we need to enable the SS
	if (disabledSS && ((powerState & sage_PowerManagement_DISPLAY_POWER) == 0))
	{
		// Enable the SS
		SystemParametersInfo(SPI_SETSCREENSAVEACTIVE,
		     TRUE,
             0,
             SPIF_SENDWININICHANGE);
		disabledSS = FALSE;
		slog((env, "Enabled SS\r\n"));
	}
	else if (!disabledSS && ((powerState & sage_PowerManagement_DISPLAY_POWER) != 0))
	{
		// Check if the screen saver is enabled, disable it if it is
		BOOL pvParam = 0;
		SystemParametersInfo(SPI_GETSCREENSAVEACTIVE,
                         0,
                         &pvParam,
                         0
                       );
		if (pvParam)
		{
			if (SystemParametersInfo(SPI_SETSCREENSAVEACTIVE,
		         FALSE,
                 0,
                 SPIF_SENDWININICHANGE))
			{
				slog((env, "Disabled SS\r\n"));
				disabledSS = TRUE;
			}
		}
	}
}

/*
 * Class:     sage_PowerManagement
 * Method:    setWakeupTime0
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL Java_sage_PowerManagement_setWakeupTime0
  (JNIEnv *env, jobject jo, jlong jhandle, jlong jtime)
{
	if (!jtime)
	{
		if (jhandle)
			CloseHandle((HANDLE) jhandle);
		return 0;
	}

	HANDLE hTimer = NULL;
	if (jhandle)
	{
		hTimer = (HANDLE) jhandle;
	}
	else
	{
		hTimer = CreateWaitableTimer(
			NULL, 
			TRUE, 
			"SagePMWakeup");
		if (!hTimer)
		{
			slog((env, "CreateWaitableTimer failed (%d)\r\n", GetLastError()));
			return 0;
		}
	}

	LARGE_INTEGER modTime;
	// Taken from the JDK source
	modTime.QuadPart = (jtime + 11644473600000L) * 10000L;

    if (!SetWaitableTimer(hTimer, &modTime, 0, NULL, NULL, 1))
    {
        slog((env, "SetWaitableTimer failed (%d)\r\n", GetLastError()));
		CloseHandle(hTimer);
        return 0;
    }

	return (jlong) hTimer;
}

