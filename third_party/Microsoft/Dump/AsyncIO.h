//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: AsyncIo.h
//
// Desc: DirectShow sample code - base library for I/O functionality.
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

#ifndef __ASYNCIO_OUT_H__
#define __ASYNCIO_OUT_H__

class CAsyncIo;

#define MAX_BUFFER_SIZE  (1024*1024*80)   //80M

#define WRITE_CMD		0x01
#define CLOSE_FILE		0x03

class CAsyncRequest
{
public:
	CAsyncRequest( DWORD BufferSize );
	~CAsyncRequest( );
    CAsyncIo *m_pAsyncIO;
    HRESULT   m_hr;
	PBYTE	  m_pBuffer;
	DWORD	  m_dwSize;
	DWORD	  m_dwBytes;
	DWORD	  m_dwWritten;
	WORD	  m_uCmd;
	HANDLE	  m_hHandle;

    HRESULT Request( CAsyncIo *pIo, HANDLE hFileHandle, WORD uCmd );
	HRESULT Process();
    void Complete(DWORD dwBytes);
	void Cancel( );

	HRESULT GetHResult() 
	{
		return m_hr;
	};

	PBYTE GetBuffer( )
	{
		return m_pBuffer;
	}

	DWORD GetSize( )
	{
		return m_dwSize;
	}

	LONG GetActualWritten() 
	{
		return m_dwWritten;
	};

};

typedef CGenericList<CAsyncRequest> CRequestList;

class CAsyncIo
{
    CCritSec m_csReader;
	CAsyncRequest *m_pRequest;

    CCritSec m_csLists;      // locks access to the list and events
    BOOL m_bFlushing;        // true if between BeginFlush/EndFlush
    CRequestList m_listWork;
    CAMEvent m_evWork;         // set when list is not empty
    CAMEvent m_evDone;
	DWORD    m_dwBufferSize;

    // for correct flush behaviour: all protected by m_csLists
    LONG    m_cItemsOut;    // nr of items not on listDone or listWork
    BOOL    m_bWaiting;     // TRUE if someone waiting for m_evAllDone
    CAMEvent m_evAllDone;   // signal when m_cItemsOut goes to 0 if m_cWaiting


    CAMEvent m_evStop;         // set when thread should exit
    HANDLE m_hThread;

    // start the thread
    HRESULT StartThread(void);

    // stop the thread and close the handle
    HRESULT CloseThread(void);

    // manage the list of requests. hold m_csLists and ensure
    // that the (manual reset) event hevList is set when things on
    // the list but reset when the list is empty.
    // returns null if list empty
    CAsyncRequest* GetWorkItem();

    // put an item on the work list
    HRESULT PutWorkItem(CAsyncRequest* pRequest);


    // called on thread to process any active requests
    void ProcessRequests(void);

    // initial static thread proc calls ThreadProc with DWORD
    // param as this
    static DWORD WINAPI InitialThreadProc(LPVOID pv) {
	CAsyncIo * pThis = (CAsyncIo*) pv;
	return pThis->ThreadProc();
    };

    DWORD ThreadProc(void);
	DWORD m_dwPeakBufferNum;
	DWORD m_dwRequestNum;
public:

    CAsyncIo( DWORD BufferSize );
    ~CAsyncIo();

    // queue a requested read. must be aligned.
    HRESULT Request( HANDLE hHandle, BYTE** ppBuffer, DWORD* pdwSize, WORD uCmd );

	HRESULT Complete(DWORD dwBytes);
  
    HRESULT BeginFlush();
    HRESULT EndFlush();

	int RequestInWaiting();

    //statistic
    HANDLE StopEvent() const { return m_evDone; }
	DWORD PeakBufferNum() { return m_dwPeakBufferNum; }
	DWORD GetBufferSize() { return m_dwBufferSize; }
	DWORD GetRequestNum() { return m_dwRequestNum; }

};

#endif // __ASYNCIO_OUT_H__
