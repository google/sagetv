//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: MPEG2Dump.h
//
// Desc: DirectShow sample code - definitions for dump renderer.
//
// Copyright (c) Microsoft Corporation. All rights reserved.

class CDumpInputPin;
class CAsyncIo;
class CMPEG2Dump;
class CMPEG2DumpFilter;

// Main filter object

class CMPEG2DumpFilter : public CBaseFilter
{
    CMPEG2Dump * const m_pDump;

public:

    // Constructor
    CMPEG2DumpFilter(CMPEG2Dump *pDump,
                LPUNKNOWN pUnk,
                CCritSec *pLock,
                HRESULT *phr);

    // Pin enumeration
    CBasePin * GetPin(int n);
    int GetPinCount();

    // Open and close the file as necessary
    STDMETHODIMP Pause();
    STDMETHODIMP Stop();
};


//  Pin object

class CDumpInputPin : public CRenderedInputPin
{
    CMPEG2Dump    * const m_pDump;           // Main renderer object
    CCritSec * const m_pReceiveLock;    // Sample critical section
    REFERENCE_TIME m_tLast;             // Last sample receive time

public:

    CDumpInputPin(CMPEG2Dump *pDump,
                  LPUNKNOWN pUnk,
                  CBaseFilter *pFilter,
                  CCritSec *pLock,
                  CCritSec *pReceiveLock,
                  HRESULT *phr);
	~CDumpInputPin();

	// Overridden to expose IStream
    STDMETHODIMP NonDelegatingQueryInterface(REFIID id, void **ppObject);
 
    // Do something with this media sample
    STDMETHODIMP Receive(IMediaSample *pSample);
    STDMETHODIMP EndOfStream(void);
    STDMETHODIMP ReceiveCanBlock();
    HRESULT WriteStringInfo(IMediaSample *pSample);

    // Check if the pin can support this specific proposed type and format
    HRESULT CheckMediaType(const CMediaType *);

    // Break connection
    HRESULT BreakConnect();

    // Track NewSegment
    STDMETHODIMP NewSegment(REFERENCE_TIME tStart,
                            REFERENCE_TIME tStop,
                            double dRate);
    };


//  CMPEG2Dump object which has filter and pin members

class CMPEG2Dump : public CUnknown, public IFileSinkFilter, public IMpegMux, public IStream
{
    friend class CMPEG2DumpFilter;
    friend class CDumpInputPin;

    CMPEG2DumpFilter *m_pFilter;         // Methods for filter interfaces
    CDumpInputPin *m_pPin;          // A simple rendered input pin
    CCritSec m_Lock;                // Main renderer critical section
    CCritSec m_ReceiveLock;         // Sublock for received samples
    CCritSec m_fileLock;         
    CAMEvent m_switchNotify;        // Used for notification when the file switch is complete
    HANDLE m_hFile;                 // Handle to file for dumping
    LPOLESTR m_pFileName;           // The filename where we dump to
	LPOLESTR m_pNextFileName;		// If we're switching files; this is the next file we write to at the proper stream boundary
	bool m_bDropNextSeq;
	ShareInfo m_shareInfo;
	long m_circFileSize;
	long m_nextCircFileSize;
	BOOL m_bIgnoreData;
	BOOL m_bIStreamMode;
	bool m_bRegBoostThreads;
	bool m_bRegOptimizeTransfers;

public:

    DECLARE_IUNKNOWN

    CMPEG2Dump(LPUNKNOWN pUnk, HRESULT *phr);
    ~CMPEG2Dump();

    static CUnknown * WINAPI CreateInstance(LPUNKNOWN punk, HRESULT *phr);

    // Write data streams to a file
    void WriteString(TCHAR *pString);
    HRESULT Write(PBYTE pbData,LONG lData);

    // Implements the IFileSinkFilter interface
    STDMETHODIMP SetFileName(LPCOLESTR pszFileName,const AM_MEDIA_TYPE *pmt);
    STDMETHODIMP GetCurFile(LPOLESTR * ppszFileName,AM_MEDIA_TYPE *pmt);

	// Implements the IMpegMux interface
	STDMETHODIMP get_ShareInfo(ShareInfo **sharin);
	STDMETHODIMP get_FileLength(LONGLONG *fileLength);
	STDMETHODIMP put_CircularSize(long lCircSize);
#ifdef LAZER_XCODE
	STDMETHODIMP SetBuffering(BOOL enable) { return S_OK; }
	STDMETHODIMP get_buffer(void *buf, int size, int* retSize) { return S_OK; }
#endif
    // IStream Methods
    STDMETHODIMP Read(void *pv, ULONG cb, ULONG *pcbRead);
    STDMETHODIMP Write(const void *pv, ULONG cb, ULONG *pcbWritten);
    STDMETHODIMP Seek(LARGE_INTEGER dlibMove,
		      DWORD dwOrigin,
		      ULARGE_INTEGER *plibNewPos);
    STDMETHODIMP SetSize(ULARGE_INTEGER libNewSize);
    STDMETHODIMP CopyTo(IStream *pstm,
			ULARGE_INTEGER cb,
			ULARGE_INTEGER *pcbRead,
			ULARGE_INTEGER *pcbWritten);
    STDMETHODIMP Commit(DWORD grfCommitFlags);
    STDMETHODIMP Revert(void);
    STDMETHODIMP LockRegion(ULARGE_INTEGER libOffset,
			    ULARGE_INTEGER cb,
			    DWORD dwLockType);
    STDMETHODIMP UnlockRegion(ULARGE_INTEGER libOffset,
			      ULARGE_INTEGER cb,
			      DWORD dwLockType);
    STDMETHODIMP Stat(STATSTG *pstatstg,
		      DWORD grfStatFlag);
    STDMETHODIMP Clone(IStream **ppStream);

	STDMETHODIMP ForceCleanUp(); //a dirty way to walk around AverMedia M780 bug  ZQ.

private:

    // Overriden to say what interfaces we support where
    STDMETHODIMP NonDelegatingQueryInterface(REFIID riid, void ** ppv);

    // Open and write to the file, without buffer and Aysnc
    HRESULT OpenFile();
    HRESULT CloseFile(BOOL bAsync);
	HRESULT WriteFile( const void *pv, ULONG cb );

	HRESULT OpenConnection();
	HRESULT CloseConnection();
	HRESULT ReOpenConnection();
	HRESULT WriteNetwork(PBYTE data, DWORD len);
	SOCKET  sd;
	TCHAR   m_pHostname[256];
	DWORD	m_dwUploadKey;
	BOOL	m_bRemoteFile;
    LONGLONG       m_llPosition;
	int    m_bytesLeftInNetworkWrite;

	CAsyncIo *m_pAsyncIO;
	HRESULT AsyncWriteFile( BYTE* pbData, DWORD lData );
	HRESULT AsyncCloseFile( );
	HRESULT AsyncFlushFile( );
	DWORD m_dwBufferSize;
	DWORD m_dwByteInBuffer;
	BYTE* m_pWriteBuffer;

};

