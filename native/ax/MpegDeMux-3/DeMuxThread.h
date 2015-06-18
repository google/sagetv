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
#ifndef __DEMUXTHREAD_H__
#define __DEMUXTHREAD_H__



class CDMThread : public CAMThread 
{
public:

    // *
    // * Worker Thread
    // *
    HRESULT Active(void);    // Starts up the worker thread
    HRESULT Inactive(void);  // Exits the worker thread.

    enum Command { CMD_INIT, CMD_PAUSE, CMD_RUN, CMD_STOP, CMD_EXIT };
    HRESULT ThreadInit(void) { return CallWorker(CMD_INIT); }
    HRESULT ThreadExit(void) { return CallWorker(CMD_EXIT); }
    HRESULT ThreadRun(void)  { return CallWorker(CMD_RUN);  }
    HRESULT ThreadPause(void){ return CallWorker(CMD_PAUSE);}
    HRESULT ThreadStop(void) { return CallWorker(CMD_STOP); }
    Command GetRequest(void) { return (Command) CAMThread::GetRequest(); }
    BOOL    CheckRequest(Command *pCom) { return CAMThread::CheckRequest( (DWORD *) pCom); }
	virtual HRESULT OnThreadCreate(void)    {return NOERROR;};
    virtual HRESULT OnThreadDestroy(void)   {return NOERROR;};
    virtual HRESULT OnThreadStartPlay(void) {return NOERROR;};

    // override these if you want to add thread commands
    virtual DWORD ThreadProc(void);  				 // the thread function
    virtual HRESULT DoThreadProcessingLoop(void)=0;    // the loop executed whilst running

	//void CheckHold( ) {  m_eventHold.Wait(); m_eventHold.Set(); };
	//void HoldThread( ) { m_eventHold.Reset(); };
	//void ResumeThread( ) { m_eventHold.Set(); }
	//CAMEvent m_eventHold; 
	char* CmdName( Command cmd );

};

#endif

