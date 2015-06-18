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
#include <streams.h>
#include <pullpin.h>
#include <limits.h>
#include <initguid.h>
#include <ks.h>
#include <dvdmedia.h>

#include "SDeMuxThread.h"

///////////////////////////////////////////////////////////////////////////////////////
//for debug
char* CDMThread::CmdName( Command cmd )
{
	switch (cmd) {
		case CMD_INIT:  return "INIT";
		case CMD_PAUSE: return "PAUSE";
		case CMD_RUN:   return "RUN";
		case CMD_STOP:  return "EXIT"; 
	}
	return "UNKONW NAME";
}

DWORD CDMThread::ThreadProc(void) {

    HRESULT hr;  // the return code from calls
    Command com;

    do {
		com = GetRequest();
		if (com != CMD_INIT) 
		{
			DbgLog((LOG_ERROR, 1, TEXT("Thread expected init command")));
			Reply((DWORD) E_UNEXPECTED);
		}
    } while (com != CMD_INIT);

    DbgLog((LOG_TRACE, 1, TEXT("worker thread initializing")));

    hr = OnThreadCreate(); // perform set up tasks
    if (FAILED(hr)) {
        DbgLog((LOG_ERROR, 1, TEXT("OnThreadCreate failed. Aborting thread.")));
        OnThreadDestroy();
	Reply(hr);	// send failed return code from OnThreadCreate
        return 1;
    }

    // Initialisation suceeded
    Reply(NOERROR);

    Command cmd;
    do {
		cmd = GetRequest();

		switch (cmd) {

		case CMD_EXIT:
			DbgLog((LOG_ERROR, 1, TEXT("Thread CMD_EXIT")));
			Reply(NOERROR);
			break;

		case CMD_RUN:
			DbgLog((LOG_ERROR, 1, TEXT("Thread CMD_RUN")));
			Reply(NOERROR);
			DoThreadProcessingLoop();
			break;
		
		case CMD_PAUSE:
			DbgLog((LOG_ERROR, 1, TEXT("Thread CMD_PAUSE")));
			Reply(NOERROR);
			//DoThreadProcessingLoop();
			break;

		case CMD_STOP:
			DbgLog((LOG_ERROR, 1, TEXT("Thread CMD_STOP")));
			Reply(NOERROR);
			break;

		default:
			DbgLog((LOG_ERROR, 1, TEXT("Thread Unknown command %d received!"), cmd));
			Reply((DWORD) E_NOTIMPL);
			break;
		}
    } while (cmd != CMD_EXIT);

    hr = OnThreadDestroy();	// tidy up.
    if (FAILED(hr)) 
	{
        DbgLog((LOG_ERROR, 1, TEXT("OnThreadDestroy failed. Exiting thread.")));
        return 1;
    }

    DbgLog((LOG_TRACE, 1, TEXT("worker thread exiting")));
    return 0;
}
