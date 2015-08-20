//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: MpegMux.cpp
//
// Desc: DirectShow sample code - implementation of a renderer that MpegMuxs
//       the samples it receives into a text file.
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

#define MAX_BUFFY_SIZE 2048
#define VSTREAM_ID 224
#define ASTREAM_ID 189
#define AUD_CIRC_SIZE 256000
#define VID_CIRC_SIZE 4000000
//#define GOP_TICKS 45045
//#define GOPS_PER_SEC (2997./1500.)
#define FUDGE 0.90
#define DIFF_HIST_LEN 240
#define STCODE_Q_SIZE 10
#define MPEGFRAME_Q_SIZE 128
// 200 seemed a little too high for this, I know 150 is too low
#define DIFF_HIST_AVG_THRESH_TOP 175
#define DIFF_HIST_AVG_THRESH_BOTTOM -1000

// At 100 I noticed a lot of extra corrections in the first few minutes, it was
// even noticable when watching. It never got out of sync, but you could
// hear the discontinuities.
#define MINREGLENA 10
#define MAXREGLENA 50
#define MINREGLENV 40 //100
#define MAXREGLENV 60 //125
#define LINREGLEN 500 //200 11/21 I took it off 200, I noticed too much adjust there, Greatest Love Songs example

#define TABLE_SIZE 40

static BYTE number_of_frame_headers_table[TABLE_SIZE] =
{
	7, 6, 6, 7, 6, 6, 6, 7, 6, 6, 7, 6, 6, 6, 7, 6,
	6, 6, 7, 6, 6, 7, 6, 6, 6, 7, 6, 6, 6, 7, 6, 6,
	7, 6, 6, 6, 7, 6, 6, 6
};

static BYTE first_access_unit_pointer_table[TABLE_SIZE] =
{
	4, 236, 148, 60, 36, 204, 116, 28, 4, 172, 84, 60, 228, 140, 52, 28,
	196, 108, 20, 252, 164, 76, 52, 220, 132, 44, 20, 188, 100, 12, 244, 156,
	68, 44, 212, 124, 36, 12, 180, 92
};

struct MpegFrameOffset
{
	DWORD offset;
	DWORD frameType;
	LONGLONG dts;
};
typedef struct MpegFrameOffset MpegFrameOffset;	

struct DataRecvTime
{
	__int64 time;
	LONGLONG len;
};
typedef struct DataRecvTime DataRecvTime;	

class CMpegMuxInputPin;
class CMpegMux;
class CMpegMuxFilter;

// Main filter object

class CMpegMuxFilter : public CBaseFilter
{
    CMpegMux * const m_pMpegMux;

public:

    // Constructor
    CMpegMuxFilter(CMpegMux *pMpegMux,
                LPUNKNOWN pUnk,
                CCritSec *pLock,
                HRESULT *phr);

    // Pin enumeration
    CBasePin * GetPin(int n);
    int GetPinCount();

    // Open and close the file as necessary
    //STDMETHODIMP Run(REFERENCE_TIME tStart);
    STDMETHODIMP Pause();
    STDMETHODIMP Stop();
};


//  Pin object

class CMpegMuxInputPin : public CRenderedInputPin
{
protected:
    CMpegMux    * const m_pMpegMux;           // Main renderer object
    CCritSec * const m_pReceiveLock;    // Critical section for streaming

public:

    CMpegMuxInputPin(TCHAR *pObjectName,
		          CMpegMux *pMpegMux,
                  LPUNKNOWN pUnk,
                  CBaseFilter *pFilter,
                  CCritSec *pLock,
                  CCritSec *pReceiveLock,
                  HRESULT *phr,
				  LPCWSTR pName);

    STDMETHODIMP EndOfStream(void);
    STDMETHODIMP ReceiveCanBlock();
	HRESULT Inactive();
    CMediaType *MediaType()
    {
        return &m_mt;
    }
};

class CMpegMuxVideoInputPin : public CMpegMuxInputPin
{
public:
    CMpegMuxVideoInputPin(CMpegMux *pMpegMux,
                  LPUNKNOWN pUnk,
                  CBaseFilter *pFilter,
                  CCritSec *pLock,
                  CCritSec *pReceiveLock,
                  HRESULT *phr);

    // Do something with this media sample
    STDMETHODIMP Receive(IMediaSample *pSample);

	// Specify our preferred media type
	HRESULT GetMediaType(int iPosition, CMediaType *pMediaType);

    // Check if the pin can support this specific proposed type and format
    HRESULT CheckMediaType(const CMediaType *);


	BOOL m_foundFirstGroupStart;
	BOOL m_foundRecentSeqHdr;
};

class CMpegMuxAudioInputPin : public CMpegMuxInputPin
{
public:
    CMpegMuxAudioInputPin(CMpegMux *pMpegMux,
                  LPUNKNOWN pUnk,
                  CBaseFilter *pFilter,
                  CCritSec *pLock,
                  CCritSec *pReceiveLock,
                  HRESULT *phr);

    // Do something with this media sample
    STDMETHODIMP Receive(IMediaSample *pSample);

	// Specify our preferred media type
	HRESULT GetMediaType(int iPosition, CMediaType *pMediaType);

    // Check if the pin can support this specific proposed type and format
    HRESULT CheckMediaType(const CMediaType *);

};

//  CMpegMux object which has filter and pin members
class CMpegMux : public CUnknown, public IFileSinkFilter, public IMpegMux
{
    friend class CMpegMuxFilter;
    friend class CMpegMuxInputPin;
    friend class CMpegMuxVideoInputPin;
    friend class CMpegMuxAudioInputPin;

    CMpegMuxFilter *m_pFilter;         // Methods for filter interfaces
    CMpegMuxInputPin *m_pvPin;          // A simple rendered input video pin
    CMpegMuxInputPin *m_paPin;          // A simple rendered input video pin
    CCritSec m_Lock;                // Main renderer critical section
    CCritSec m_vReceiveLock;         // Sublock for received samples
    CCritSec m_aReceiveLock;         // Sublock for received samples
	CCritSec m_seqReadyLock;
	CCritSec m_videoLock;
	CCritSec m_audioLock;
	CCritSec m_fileLock;
    HANDLE m_hFile;                 // Handle to file for MpegMuxing
    LPOLESTR m_pFileName;           // The filename where we MpegMux to
	unsigned char m_buffy[MAX_BUFFY_SIZE];
	unsigned int m_bytePos;
	unsigned int m_bitPos;
	unsigned char m_spareByte;
	ULONGLONG m_nextVDTS;
	ULONGLONG m_nextVSCR;
	ULONGLONG m_nextASCR;
	DWORD m_nextASCRE;
	ULONGLONG m_nextAPTS;
	ULONGLONG m_lastWrittenAPTS;
	PBYTE m_seqBuf;
	DWORD m_seqBufLen;
	DWORD m_seqBufOffset;
	DWORD m_numSeqReady;
	PBYTE m_audBuf;
	DWORD m_audBufLen;
	DWORD m_audBufOffset;
	LONGLONG m_aBytesRecvd;
	LONGLONG m_gopsRecvd;
	LONGLONG m_aBytesWritten;
	LONGLONG m_gopsWritten;
	LONGLONG m_aFrameNum;
	DWORD m_aFrameRem;
	LONG m_diffHist[DIFF_HIST_LEN];
	DWORD m_diffHistPos;
	bool m_diffHistValid;
	LONG m_scrDiffHist[DIFF_HIST_LEN];
	DWORD m_scrDiffHistPos;
	bool m_scrDiffHistValid;
	DWORD m_buffy_size;

	LONGLONG m_videoBitrate; // in bits/second, video bit rate out of MPEG-2 encoder
	DWORD m_audioSampleRate; // in samples/second
	DWORD m_audioBitrate; // in bits/second
	DWORD m_aFrameSize; // in samples, dependent upon layer of MPEG audio
	DWORD m_muxRate;
	DWORD m_audioPacksPerGOP;
	DWORD m_audioBytesPerGOP;
	DWORD m_videoPacksPerGOP;
	DWORD m_stCodeQ[STCODE_Q_SIZE];
	DWORD m_stCodeIdx;
	MpegFrameOffset m_frameCodeQ[MPEGFRAME_Q_SIZE];
	DWORD m_frameCodeIdx;
	DWORD m_numFrameCodes;
	bool m_wroteSysHdr;
	DWORD m_frameTicks;
	DWORD m_gopFrames;
	DWORD m_ipFrameDist;
	DWORD m_numIPFramesPerGop;
	ShareInfo m_shareInfo;
	DWORD m_videoStatIdx;
	DWORD m_audioStatIdx;
	bool m_fullVideoStat;
	bool m_fullAudioStat;
	DataRecvTime m_videoStat[LINREGLEN];
	DataRecvTime m_audioStat[LINREGLEN];
	__int64 m_baseCpuTime;
	LONGLONG m_baseAudioTime;
	LONGLONG m_baseVideoTime;
	LONGLONG m_lastVideoRecvTime;
	LONGLONG m_totalAudioStatAdjust;
	DWORD m_audioByteGOPExtras;
	bool m_bDropNextSeq;

    CAMEvent m_evWork;         
    CAMEvent m_evStop;         // set when thread should exit
    HANDLE m_hThread;

	bool m_bRegBoostThreads;
	ULONGLONG m_llRegVDTS;
	ULONGLONG m_llRegAPTS;

	bool m_bMpegVideo;
	bool m_bPesVideo;
	bool m_bNextPesPacketFragmented;
	long m_circFileSize;
	long m_nextCircFileSize;

	bool m_bResyncAV;

	int m_lastFrameAdjust;
	int m_numFrameAdjustRepeat;
	LONGLONG m_extraDTS;

    // start the thread
    HRESULT StartThread(void);

    // stop the thread and close the handle
    HRESULT CloseThread(void);

    // called on thread to process any active requests
    void ProcessSeqs(void);

    // initial static thread proc calls ThreadProc with DWORD
    // param as this
    static DWORD WINAPI InitialThreadProc(LPVOID pv) {
	CMpegMux * pThis = (CMpegMux*) pv;
	return pThis->ThreadProc();
    };

    DWORD ThreadProc(void);

public:

    DECLARE_IUNKNOWN

    CMpegMux(LPUNKNOWN pUnk, HRESULT *phr);
    ~CMpegMux();

    static CUnknown * WINAPI CreateInstance(LPUNKNOWN punk, HRESULT *phr);

    // Write data streams to a file
    HRESULT Write(PBYTE pbData,LONG lData);

	// Write PS Headers
	HRESULT WritePSHeaders();
	void WritePESPaddingPacket(ULONG packSize);
	BOOL PackupVideoSequence();
	void WritePackHeader(ULONGLONG scr, ULONGLONG scre, ULONG numStuffs);
	void WritePESVideoPacket(ULONGLONG pts, ULONGLONG dts, DWORD len, BOOL bDataAlignment = FALSE);
	void WritePESAudioPacket(ULONGLONG pts, DWORD len, int frameAdjust);
	void WriteSystemHeader();
	void BufferBits(ULONGLONG value, unsigned int numBits);
	HRESULT WriteBuff(unsigned int numBytes);

    // Implements the IFileSinkFilter interface
    STDMETHODIMP SetFileName(LPCOLESTR pszFileName,const AM_MEDIA_TYPE *pmt);
    STDMETHODIMP GetCurFile(LPOLESTR * ppszFileName,AM_MEDIA_TYPE *pmt);

	// Implements the IMpegMux interface
	STDMETHODIMP get_ShareInfo(ShareInfo **sharin);
	STDMETHODIMP get_FileLength(LONGLONG *fileLength);
	STDMETHODIMP put_CircularSize(long lCircSize);
	STDMETHODIMP ForceCleanUp()
	{
		return S_OK;
	}



private:

    // Overriden to say what interfaces we support where
    STDMETHODIMP NonDelegatingQueryInterface(REFIID riid, void ** ppv);

    // Open and write to the file
    HRESULT OpenFile();
    HRESULT CloseFile();

	void PushSeqCode(DWORD idx);
	DWORD PopSeqLen();
	DWORD PeekSeqLen();
	void PushFrameOffset(MpegFrameOffset x);
	MpegFrameOffset PopFrameOffset();
	__int64 linRegTimeForBytes(DataRecvTime* pData, DWORD numPoints, LONGLONG byteLen, bool includeGlobalAudioOffset);
};

