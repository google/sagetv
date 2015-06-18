//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: AsyncIo.cpp
//
// Desc: DirectShow sample code - base library with I/O functionality.
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

#include <streams.h>
#include "../../../native/include/isharedasync.h"
#include "AsyncIO.h"


CAsyncRequest::CAsyncRequest( DWORD dwBufferSize )
: m_hr(S_OK),
  m_dwSize(dwBufferSize),
  m_dwBytes(0),
  m_dwWritten(0),
  m_uCmd(0),
  m_hHandle(NULL)
{
	m_pBuffer = new BYTE[dwBufferSize];
}

CAsyncRequest::~CAsyncRequest( )
{
	delete m_pBuffer;
}

HRESULT
CAsyncRequest::Request(	CAsyncIo *pIo, HANDLE hHandle, WORD uCmd )
{
	m_pAsyncIO = pIo;
	m_dwBytes = 0;
	m_dwWritten = 0;
	m_uCmd = uCmd;
	m_hHandle = hHandle;
	m_hr = VFW_E_TIMEOUT;   
    return S_OK;
}

void CAsyncRequest::Complete( DWORD dwBytes )
{
	m_dwBytes = dwBytes;
}

void CAsyncRequest::Cancel( )
{
	if ( m_uCmd == CLOSE_FILE )
		CloseHandle(m_hHandle);
}

HRESULT CAsyncRequest::Process( )
{
	if ( m_hHandle == INVALID_HANDLE_VALUE  )
	{
		m_hr = ERROR_INVALID_HANDLE;
		return m_hr;
	}

	switch (m_uCmd) {
	case WRITE_CMD:
			m_hr = WriteFile( m_hHandle, (PVOID)m_pBuffer, (DWORD)m_dwBytes, &m_dwWritten, NULL );
		break;
	case CLOSE_FILE:
		CloseHandle(m_hHandle);
		m_hHandle = INVALID_HANDLE_VALUE;
		break;
	}
    return m_hr;
}



// --- CAsyncIo ---

// note - all events created manual reset

CAsyncIo::CAsyncIo( DWORD dwBufferSize )
 : m_hThread(NULL),
   m_evWork(TRUE),
   m_evDone(TRUE),
   m_evStop(TRUE),
   m_listWork(NAME("Work list")),
   m_bFlushing(FALSE),
   m_cItemsOut(0),
   m_bWaiting(FALSE),
   m_dwBufferSize(dwBufferSize),
   m_dwPeakBufferNum(0),
   m_dwRequestNum(0),
   m_pRequest(NULL)
{

}


CAsyncIo::~CAsyncIo()
{
    // move everything to the done list

    // shutdown worker thread
    CloseThread();
	if ( m_pRequest )
		delete m_pRequest;
    // empty the done list
    POSITION pos = m_listWork.GetHeadPosition();
    while (pos) {
        CAsyncRequest* pRequest = m_listWork.GetNext(pos);
		pRequest->Cancel();
        delete pRequest;
    }
    m_listWork.RemoveAll();
}



HRESULT CAsyncIo::Request( HANDLE hHandle, BYTE** ppBuffer, DWORD* pdwSize, WORD uCmd )
{
	//create a reuest to put into queue
	ASSERT( m_pRequest == NULL );
	if ( m_listWork.GetCount() > (int)(MAX_BUFFER_SIZE/m_dwBufferSize)  )
		return VFW_E_BUFFER_OVERFLOW;

    m_pRequest = new CAsyncRequest( m_dwBufferSize );
    m_pRequest->Request( this, hHandle, uCmd );
	*ppBuffer = m_pRequest->GetBuffer();
	*pdwSize = m_pRequest->GetSize();

    return S_OK;
}

HRESULT CAsyncIo::Complete(DWORD dwBytes)
{
	if ( m_pRequest == NULL )
		return VFW_E_BUFFER_NOTSET;

	m_pRequest->Complete( dwBytes );
    HRESULT hr = PutWorkItem(m_pRequest);
    if (FAILED(hr)) 
	{
        delete m_pRequest;
    }
	m_pRequest = NULL;
	
	return hr;
}



HRESULT CAsyncIo::BeginFlush()
{
    // hold the lock while emptying the work list
    {
        CAutoLock lock(&m_csLists);

        // prevent further requests being queued.
        // Also WaitForNext will refuse to block if this is set
        // unless m_bWaiting is also set which it will be when we release
        // the critsec if there are any outstanding).
        m_bFlushing = TRUE;

        CAsyncRequest * preq;
        while((preq = GetWorkItem()) != 0) {
            preq->Cancel();
        }

        // now wait for any outstanding requests to complete
        if (m_cItemsOut > 0) {

            // can be only one person waiting
            ASSERT(!m_bWaiting);

            // this tells the completion routine that we need to be
            // signalled via m_evAllDone when all outstanding items are
            // done. It also tells WaitForNext to continue blocking.
            m_bWaiting = TRUE;
        } else {
            // all done

            // force m_evDone set so that even if list is empty,
            // WaitForNext will not block
            // don't do this until we are sure that all
            // requests are on the done list.
            m_evDone.Set();
            return S_OK;
        }
    }

    ASSERT(m_bWaiting);

    // wait without holding critsec
    for (;;) {
        m_evAllDone.Wait();
        {
            // hold critsec to check
            CAutoLock lock(&m_csLists);

            if (m_cItemsOut == 0) {

                // now we are sure that all outstanding requests are on
                // the done list and no more will be accepted
                m_bWaiting = FALSE;

                // force m_evDone set so that even if list is empty,
                // WaitForNext will not block
                // don't do this until we are sure that all
                // requests are on the done list.
                m_evDone.Set();

                return S_OK;
            }
        }
    }
}

// end a flushing state
HRESULT
CAsyncIo::EndFlush()
{
    CAutoLock lock(&m_csLists);

    m_bFlushing = FALSE;

    ASSERT(!m_bWaiting);


    return S_OK;
}

// start the thread
HRESULT
CAsyncIo::StartThread(void)
{
    if (m_hThread) 
	{
        return S_OK;
    }

    // clear the stop event before starting
    m_evStop.Reset();

    DWORD dwThreadID;
    m_hThread = CreateThread(
                    NULL,
                    0,
                    InitialThreadProc,
                    this,
                    0,
                    &dwThreadID);
    if (!m_hThread) {
	DWORD dwErr = GetLastError();
        return HRESULT_FROM_WIN32(dwErr);
    }
    return S_OK;
}

// stop the thread and close the handle
HRESULT CAsyncIo::CloseThread(void)
{
    // signal the thread-exit object
    m_evStop.Set();

    if (m_hThread) {

        WaitForSingleObject(m_hThread, INFINITE);
        CloseHandle(m_hThread);
        m_hThread = NULL;
    }
    return S_OK;
}


// manage the list of requests. hold m_csLists and ensure
// that the (manual reset) event hevList is set when things on
// the list but reset when the list is empty.
// returns null if list empty
CAsyncRequest* CAsyncIo::GetWorkItem()
{
    CAutoLock lck(&m_csLists);

    CAsyncRequest * preq  = m_listWork.RemoveHead();

    // force event set correctly
    if (m_listWork.GetCount() == 0) 
	{
        m_evWork.Reset();
    }
    return preq;
}

// put an item on the work list - fail if bFlushing
HRESULT CAsyncIo::PutWorkItem(CAsyncRequest* pRequest)
{
    CAutoLock lock(&m_csLists);
    HRESULT hr;

    if (m_bFlushing) 
	{
        hr = VFW_E_WRONG_STATE;
    }
    else 
	if (m_listWork.AddTail(pRequest)) 
	{
		if ( m_listWork.GetCount() > (int)m_dwPeakBufferNum )
			m_dwPeakBufferNum = m_listWork.GetCount();
		m_dwRequestNum++;

        // event should now be in a set state - force this
        m_evWork.Set();

        // start the thread now if not already started
        hr = StartThread();

    } else {
        hr = E_OUTOFMEMORY;
    }

    return(hr);
}

int CAsyncIo::RequestInWaiting()
{
	return m_listWork.GetCount();
}

void CAsyncIo::ProcessRequests(void)
{
    // lock to get the item and increment the outstanding count
    CAsyncRequest * preq = NULL;
    for (;;) {
        {
            CAutoLock lock(&m_csLists);

            preq = GetWorkItem();
            if (preq == NULL) 
			{
                // done
                return;
            }

            // one more item not on the done or work list
            m_cItemsOut++;

            // release critsec
        }

		preq->Process();
		delete preq;
  
    }
}

// the thread proc - assumes that DWORD thread param is the
// this pointer
DWORD
CAsyncIo::ThreadProc(void)
{
    HANDLE ahev[] = {m_evStop, m_evWork};

    for (;;) {
	    DWORD dw = WaitForMultipleObjects(
		        2,
		        ahev,
		        FALSE,
		        INFINITE);
	    if (dw == WAIT_OBJECT_0+1) {

	        // requests need processing
	        ProcessRequests();
	    } else {
	        // any error or stop event - we should exit
	        return 0;
	    }
    }
}

