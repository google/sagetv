//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: Dump.cpp
//
// Desc: DirectShow sample code - implementation of a renderer that dumps
//       the samples it receives into a text file.
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------


// Summary
//
// We are a generic renderer that can be attached to any data stream that
// uses IMemInputPin data transport. For each sample we receive we write
// its contents including its properties into a dump file. The file we
// will write into is specified when the dump filter is created. GraphEdit
// creates a file open dialog automatically when it sees a filter being
// created that supports the IFileSinkFilter interface.
//
//
// Implementation
//
// Pretty straightforward really, we have our own input pin class so that
// we can override Receive, all that does is to write the properties and
// data into a raw data file (using the Write function). We don't keep
// the file open when we are stopped so the flags to the open function
// ensure that we open a file if already there otherwise we create it.
//
//
// Demonstration instructions
//
// Start GraphEdit, which is available in the SDK DXUtils folder. Drag and drop
// an MPEG, AVI or MOV file into the tool and it will be rendered. Then go to
// the filters in the graph and find the filter (box) titled "Video Renderer"
// This is the filter we will be replacing with the dump renderer. Then click
// on the box and hit DELETE. After that go to the Graph menu and select the
// "Insert Filters", from the dialog box find and select the "Dump Filter".
//
// You will be asked to supply a filename where you would like to have the
// data dumped, the data we receive in this filter is dumped in text form.
// Then dismiss the dialog. Back in the graph layout find the output pin of
// the filter that used to be connected to the input of the video renderer
// you just deleted, right click and do "Render". You should see it being
// connected to the input pin of the dump filter you just inserted.
//
// Click Pause and Run and then a little later stop on the GraphEdit frame and
// the data being passed to the renderer will be dumped into a file. Stop the
// graph and dump the filename that you entered when inserting the filter into
// the graph, the data supplied to the renderer will be displayed as raw data
//
//
// Files
//
// dump.cpp             Main implementation of the dump renderer
// dump.def             What APIs the DLL will import and export
// dump.h               Class definition of the derived renderer
// dump.rc              Version information for the sample DLL
// dumpuids.h           CLSID for the dump filter
// makefile             How to build it...
//
//
// Base classes used
//
// CBaseFilter          Base filter class supporting IMediaFilter
// CRenderedInputPin    An input pin attached to a renderer
// CUnknown             Handle IUnknown for our IFileSinkFilter
// CPosPassThru         Passes seeking interfaces upstream
// CCritSec             Helper class that wraps a critical section
//
//

// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)

#include <windows.h>
#include <commdlg.h>
#include <streams.h>
#include <initguid.h>
#include <stdio.h>
#include "../../../native/include/impegmux.h"
#include "MPEG2Dumpuids.h"
#include "AsyncIO.h"
#include "MPEG2Dump.h"
#include "../../../native/include/ftmpegdef.h"

// Setup data

#ifdef WIN32   
#include <time.h>
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}
#endif

#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _flog x
#endif

static bool flog_enabled=false;


static void _flog( char* logname, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	
	if ( !flog_enabled ) return;

	fp = fopen( logname, "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );
	fprintf( fp, "%02d/%02d/%d %02d:%02d:%02d  ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
	ltm.tm_hour, ltm.tm_min, ltm.tm_sec );  
	va_start(args, cstr);
	vfprintf( fp, cstr, args );
	va_end(args);
	fclose( fp );
}
static void _flog_check()
{
	FILE* fp = fopen( "MPEG2Dump_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
	}
}

class CWtoT
{
    LPTSTR m_ptsz;

public:
    CWtoT(LPCWSTR pwsz) : m_ptsz(NULL)
    {
        ASSERT(pwsz != NULL);
        const int size = WideCharToMultiByte(CP_ACP, 0, pwsz, -1,
            NULL, 0, NULL, NULL);
        m_ptsz = (LPSTR) malloc(sizeof(CHAR)*size);
        ASSERT(m_ptsz != NULL);
        memset(m_ptsz, 0, sizeof(CHAR)*size);
        WideCharToMultiByte(CP_ACP, 0, pwsz, -1,
            m_ptsz, size, NULL, NULL);
    }

    ~CWtoT()
    {
        free(m_ptsz);
    }
    size_t GetLength() const { return strlen(m_ptsz); }
    operator LPCTSTR() const { return m_ptsz; }
};

const AMOVIESETUP_MEDIATYPE sudPinTypes =
{
    &MEDIATYPE_Stream,            // Major type
    &MEDIASUBTYPE_MPEG2_PROGRAM          // Minor type
};

const AMOVIESETUP_PIN sudPins =
{
    L"Input",                   // Pin string name
    FALSE,                      // Is it rendered
    FALSE,                      // Is it an output
    FALSE,                      // Allowed none
    FALSE,                      // Likewise many
    &CLSID_NULL,                // Connects to filter
    L"Output",                  // Connects to pin
    1,                          // Number of types
    &sudPinTypes                // Pin information
};

const AMOVIESETUP_FILTER sudDump =
{
    &CLSID_MPEG2Dump,                // Filter CLSID
    L"MPEG2Dump",                    // String name
    MERIT_DO_NOT_USE,           // Filter merit
    1,                          // Number pins
    &sudPins                    // Pin details
};


//
//  Object creation stuff
//
CFactoryTemplate g_Templates[]= {
    L"MPEG2Dump", &CLSID_MPEG2Dump, CMPEG2Dump::CreateInstance, NULL, &sudDump
};
int g_cTemplates = 1;


// Reads data from a socket into the array until the "\r\n" character
// sequence is encountered. The returned value is the
// number of bytes read or SOCKET_ERROR if an error occurs, 0
// if the socket has been closed. The number of bytes will be
// 2 more than the actual string length because the \r\n chars
// are removed before this function returns.
int sockReadLine(SOCKET sd, char* buffer, int bufLen)
{
	int currRecv;
	int newlineIndex = 0;
	bool endFound = false;
	int offset = 0;
	while (!endFound)
	{
		currRecv = recv(sd, buffer + offset, bufLen, MSG_PEEK);
		if (currRecv == SOCKET_ERROR)
		{
			return SOCKET_ERROR;
		}

		if (currRecv == 0)
		{
			return endFound ? 0 : SOCKET_ERROR;
		}

		// Scan the buffer for "\r\n" termination
		for (int i = 0; i < (currRecv + offset); i++)
		{
			if (buffer[i] == '\r')
			{
				if (buffer[i + 1] == '\n')
				{
					newlineIndex = i + 1;
					endFound = true;
					break;
				}
			}
		}
		if (!endFound)
		{
			currRecv = recv(sd, buffer + offset, currRecv, 0);
			if (currRecv == SOCKET_ERROR)
			{
				return SOCKET_ERROR;
			}
			if (currRecv == 0)
			{
				return endFound ? 0 : SOCKET_ERROR;
			}
			offset += currRecv;
		}
	}

	currRecv = recv(sd, buffer + offset, (newlineIndex + 1) - offset, 0);
	buffer[newlineIndex - 1] = '\0';
	return currRecv;
}

const int RED_TIMEOUT = 30000; // 30 second timeout on the sockets

typedef struct addrinfo 
{  
	int ai_flags;  
	int ai_family;  
	int ai_socktype;  
	int ai_protocol;  
	size_t ai_addrlen;  
	char* ai_canonname;  
	struct sockaddr* ai_addr;  
	struct addrinfo* ai_next;
} addrinfo;

// Constructor

CMPEG2DumpFilter::CMPEG2DumpFilter(CMPEG2Dump *pDump,
                         LPUNKNOWN pUnk,
                         CCritSec *pLock,
                         HRESULT *phr) :
    CBaseFilter(NAME("CMPEG2DumpFilter"), pUnk, pLock, CLSID_MPEG2Dump),
    m_pDump(pDump)
{
}


//
// GetPin
//
CBasePin * CMPEG2DumpFilter::GetPin(int n)
{
    if (n == 0) {
        return m_pDump->m_pPin;
    } else {
        return NULL;
    }
}


//
// GetPinCount
//
int CMPEG2DumpFilter::GetPinCount()
{
    return 1;
}


//
// Stop
//
// Overriden to close the dump file
//
STDMETHODIMP CMPEG2DumpFilter::Stop()
{
    CAutoLock cObjectLock(m_pLock);
	DbgLog((LOG_TRACE, 2, TEXT("Dump Stop() called")));
    return CBaseFilter::Stop();
}


//
// Pause
//
// Overriden to open the dump file
//
STDMETHODIMP CMPEG2DumpFilter::Pause()
{
    CAutoLock cObjectLock(m_pLock);
	DbgLog((LOG_TRACE, 2, TEXT("Dump Pause() called")));
//    m_pDump->OpenFile();
    return CBaseFilter::Pause();
}


//
// Run
//
// Overriden to open the dump file
//
//We don't need this since Pause will always be called and handle opening the file
/*STDMETHODIMP CMPEG2DumpFilter::Run(REFERENCE_TIME tStart)
{
    CAutoLock cObjectLock(m_pLock);
    m_pDump->OpenFile();
    return CBaseFilter::Run(tStart);
}*/


//
//  Definition of CDumpInputPin
//
CDumpInputPin::CDumpInputPin(CMPEG2Dump *pDump,
                             LPUNKNOWN pUnk,
                             CBaseFilter *pFilter,
                             CCritSec *pLock,
                             CCritSec *pReceiveLock,
                             HRESULT *phr) :

    CRenderedInputPin(NAME("CDumpInputPin"),
                  pFilter,                   // Filter
                  pLock,                     // Locking
                  phr,                       // Return code
                  L"Input"),                 // Pin name
    m_pReceiveLock(pReceiveLock),
    m_pDump(pDump),
    m_tLast(0)
{
}

CDumpInputPin::~CDumpInputPin()
{
}

STDMETHODIMP CDumpInputPin::NonDelegatingQueryInterface(REFIID id, void **ppObject)
{
    if (id == IID_IStream) {
        return GetInterface(static_cast<IStream *>(m_pDump), ppObject);
    } else {
	return CRenderedInputPin::NonDelegatingQueryInterface(id, ppObject);
    }
}

//
// CheckMediaType
//
// Check if the pin can support this specific proposed type and format
//
//#include <dvdmedia.h>
HRESULT CDumpInputPin::CheckMediaType(const CMediaType *pmt)
{
//ASSERT(0);
//MPEG2VIDEOINFO* mvh = (MPEG2VIDEOINFO*) pmt->pbFormat;
    return S_OK;
}


//
// BreakConnect
//
// Break a connection
//
HRESULT CDumpInputPin::BreakConnect()
{
    return CRenderedInputPin::BreakConnect();
}


//
// ReceiveCanBlock
//
// We don't hold up source threads on Receive
//
STDMETHODIMP CDumpInputPin::ReceiveCanBlock()
{
    return S_FALSE;
}

// NOTE: This could get bad start codes.
static int verifyPSBlock(unsigned char *data, int bytes )
{
    unsigned char b;
    int pos=0;
    int cur=0xFFFFFFFF;
    while(pos<bytes)
    {
        b=data[pos];
        pos+=1;
        cur<<=8;
        cur|=b;
        if((cur&0xFFFFFF00)==0x00000100)
        {
            /* video */
            if((b==0xB3))
            {
                return 1;
            }
        }
		else if (cur == 0x00000001 && pos + 2 < bytes) // H264 test
		{
			if (data[pos] == 0x09 && data[pos+1] == 0x10 && data[pos+2] == 0x00)
			{
				return 1;
			}
		}
    }
    return 0;
}

static int findTransitionPoint(unsigned char* data, int length, int flags)
{
    int numbytes=length;
    // HDPVR format
    if(flags==1)
    {
        int i, tsstart=-1, tsvidpacket=-1, seqstart=-1;
        // For the HDPVR our input is a tranport stream, we must find a valid start point
        // in the video pid 0x1011 of 00 00 01 09 10 00

        // First we try to locate ts packets
        for(i=0;i<numbytes;i++)
        {
            if(data[i]==0x47 && 
                (i+188)<numbytes && data[i+188]==0x47 &&
                (i+188*2)<numbytes && data[i+188*2]==0x47)
            {
                tsstart=i;
                break;
            }
        }

        // Second we find a ts packet with section start and pid 0x1011
        while((i+188)<numbytes)
        {
            if(data[i]==0x47 &&
                data[i+1]==0x50 &&
                data[i+2]==0x11)
            {
                tsvidpacket=i;
                // Verify if that packet contains the magic sequence 00 00 00 01 09 10 00
                // If it does, the data up to the begining of this TS packet go in old file 
                // and the new data in the new file
                int j;
                for(j=4;j<188-7;j++)
                {
                    // NOTE: we could implement faster search but the number of
                    // matched packet that reach this point should be quite small...
                    if(data[i+j]==0x00 &&
                        data[i+j+1]==0x00 &&
                        data[i+j+2]==0x00 &&
                        data[i+j+3]==0x01 &&
                        data[i+j+4]==0x09 &&
                        data[i+j+5]==0x10 &&
                        data[i+j+6]==0x00)
                    {
                        // We have found the vid packet with the magic sequence, write that to old file
                        return tsvidpacket;
                    }
                }
            }
            i+=188;
        }
    }
//    else
    {
        // For the IVTV cards we must find a sequence start inside the video stream
        // we are looking for 00 00 01 B3
        int i=0, psstart=-1;
        // IVTV use 2K blocks
        // First locate the 00 00 01 BA block
        while(i<=numbytes-32)
        {
            if(data[i]==0x00 && 
                data[i+1]==0x00 &&
                data[i+2]==0x01 &&
                data[i+3]==0xBA)
            {
                psstart=i;
                if(verifyPSBlock(&data[i], numbytes-i))
                {
                    // We have found the sequence start
                    return psstart;
                }
                i+=2048;
            }
            else
            {
                i++;
            }
        }
    }
    return -1;
}


//
// Receive
//
// Do something with this media sample
//
STDMETHODIMP CDumpInputPin::Receive(IMediaSample *pSample)
{
    CAutoLock lock(m_pReceiveLock);
    PBYTE pbData;

	if (!pSample)
		return S_OK;
	// Check for base class errors, added on 7/31/04
	HRESULT hr = CBaseInputPin::Receive(pSample);
	if (FAILED(hr))
		return hr;

	if (m_pDump->m_bRegBoostThreads)
		SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_HIGHEST);

	REFERENCE_TIME tStart, tStop, mtStart, mtStop;
    pSample->GetTime(&tStart, &tStop);
	pSample->GetMediaTime(&mtStart, &mtStop);

    //DbgLog((LOG_TRACE, 5, TEXT("tStart(%s), tStop(%s), Diff(%d ms), Bytes(%d) mtStart(%s) mtStop(%s) sync=%d pre=%d disc=%d"),
    //       (LPCTSTR) CDisp(tStart, CDISP_DEC),
    //       (LPCTSTR) CDisp(tStop, CDISP_DEC),
    //       (LONG)((tStart - m_tLast) / 10000),
    //       pSample->GetActualDataLength(),
    //       (LPCTSTR) CDisp(mtStart, CDISP_DEC),
    //       (LPCTSTR) CDisp(mtStop, CDISP_DEC),
		  // (pSample->IsSyncPoint() == S_OK),
		  // (pSample->IsPreroll() == S_OK),
		  // (pSample->IsDiscontinuity() == S_OK)
		  // ));

    m_tLast = tStart;

	// Narflex: We need to have this lock before we check the m_hFile variable since it gets changed
	// within the context of that lock on fast file switches
	CAutoLock lock2(&(m_pDump->m_fileLock));
    // Has the filter been stopped yet
    if (m_pDump->m_hFile == INVALID_HANDLE_VALUE && !m_pDump->m_bRemoteFile) {
        return NOERROR;
    }

	if (m_pDump->m_bIStreamMode)
	{
		// First do a seek to whatever the start time is, that's where the data should be written
		// MS is an ass for doing something like this in their Mux where it calls both interfaces IMO
		LARGE_INTEGER filePos = {0};
		filePos.QuadPart = tStart;
		m_pDump->Seek(filePos, STREAM_SEEK_SET, NULL);
	}

    // Copy the data to the file

	DWORD currLen = pSample->GetActualDataLength();
	if (currLen == 0 || currLen == 0xFFFFFFFF)
		return S_OK;

	hr = pSample->GetPointer(&pbData);
    if (FAILED(hr) || !pbData) {
        return hr;
    }

	BOOL isTS = (m_mt.subtype == MEDIASUBTYPE_MPEG2_TRANSPORT);
	BOOL isPS = (m_mt.subtype == MEDIASUBTYPE_MPEG2_PROGRAM);
	// Don't wait for the sequence header if we're not doing MPEG2, this is for
	// when we're just dumping something else.
	if (!isTS && !isPS)
		m_pDump->m_bDropNextSeq = false;
	if (m_pDump->m_bDropNextSeq)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Waiting for next seq hdr code...len=%d"), currLen));
		// Skip over bytes until we're at the start of a pack that contains the system header packet
		for (ULONG i = 0; i + 14 < currLen; i++)
		{
			DWORD currWord = DWORD_SWAP(*(UNALIGNED DWORD *)&pbData[i]);
//			DbgLog((LOG_TRACE, 2, TEXT("CurrWord=%x"), currWord));
			if (pbData[i] == 0x47 && isTS)
			{
				// Transport Stream sync byte
				DbgLog((LOG_TRACE, 2, TEXT("MPEG2 TS stream detected")));
				m_pDump->m_bDropNextSeq = false;
				currLen -= i;
				pbData += i;
				break;
			}
			else if (currWord == PACK_START_CODE)
			{
				// Determine if it's MPEG1 or MPEG2
				DWORD nextWord;
				if ( pbData[i+4] >> 6 )
				{
					DbgLog((LOG_TRACE, 2, TEXT("MPEG2 stream detected")));
					// mpeg2
					int stuffLen = (pbData[i + 13] & 0x07);
					if (i + 14 + stuffLen + 4 >= currLen) // don't walk off our memory area
						break;
					nextWord = DWORD_SWAP(*(UNALIGNED DWORD *)&pbData[i + 14 + stuffLen]);
				}
				else
				{
					DbgLog((LOG_TRACE, 2, TEXT("MPEG1 stream detected")));
					nextWord = DWORD_SWAP(*(UNALIGNED DWORD *)&pbData[i + 12]);
				}
//				DbgLog((LOG_TRACE, 2, TEXT("NextWord=%x"), nextWord));
				if (nextWord == SYSTEM_HEADER_START_CODE)
				{
					DbgLog((LOG_TRACE, 2, TEXT("Found next seq hdr code!")));
					m_pDump->m_bDropNextSeq = false;
					currLen -= i;
					pbData += i;
					break;
				}
			}
		}
	}
	if (m_pDump->m_pNextFileName)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Searching for the transition point for the seamless switch...")));
		// See if the transition point exists in the file; and if so then close it off and write the new one at that boundary
		int tranny = findTransitionPoint(pbData, currLen, isTS ? 1 : 0);
		if (tranny >= 0)
		{
			// Found the transition point!
			DbgLog((LOG_TRACE, 2, TEXT("Found the transition point for the seamless switch!")));
			if (tranny > 0)
				m_pDump->Write( pbData, tranny );
			m_pDump->CloseFile( TRUE );
			m_pDump->m_pFileName = m_pDump->m_pNextFileName;
			m_pDump->m_pNextFileName = NULL;
			m_pDump->m_switchNotify.Set();
			m_pDump->OpenFile();
			return m_pDump->Write( pbData + tranny, currLen - tranny );
		}
	}

	{
		if (m_pDump->m_bDropNextSeq)
			return S_OK;
		else
			return m_pDump->Write( pbData, currLen );
	}
}

//
// EndOfStream
//
STDMETHODIMP CDumpInputPin::EndOfStream(void)
{
    CAutoLock lock(m_pReceiveLock);
    return CRenderedInputPin::EndOfStream();

} // EndOfStream


//
// NewSegment
//
// Called when we are seeked
//
STDMETHODIMP CDumpInputPin::NewSegment(REFERENCE_TIME tStart,
                                       REFERENCE_TIME tStop,
                                       double dRate)
{
    m_tLast = 0;
    return S_OK;

} // NewSegment


//
//  CMPEG2Dump class
//
CMPEG2Dump::CMPEG2Dump(LPUNKNOWN pUnk, HRESULT *phr) :
    CUnknown(NAME("CMPEG2Dump"), pUnk),
    m_pFilter(NULL),
    m_pPin(NULL),
    m_hFile(INVALID_HANDLE_VALUE),
    m_pFileName(0),
    m_pNextFileName(0),
	m_switchNotify(TRUE),
	m_circFileSize(0),
	m_nextCircFileSize(0),
	m_bIStreamMode(FALSE),
	m_bRemoteFile(FALSE),
	m_pWriteBuffer(NULL),
	m_llPosition(0),
	m_bytesLeftInNetworkWrite(0),
	m_pAsyncIO(NULL)
{
    m_pFilter = new CMPEG2DumpFilter(this, GetOwner(), &m_Lock, phr);
    if (m_pFilter == NULL) {
        *phr = E_OUTOFMEMORY;
        return;
    }

    m_pPin = new CDumpInputPin(this,GetOwner(),
                               m_pFilter,
                               &m_Lock,
                               &m_ReceiveLock,
                               phr);
    if (m_pPin == NULL) {
        *phr = E_OUTOFMEMORY;
        return;
    }
	m_bDropNextSeq = true; // It doesn't always start clean, so clean it up!
	m_shareInfo.totalWrite = 0;
	m_bIgnoreData = FALSE;
	m_bRegBoostThreads = true;
	m_bRegOptimizeTransfers = false;
	DWORD dwAysncBufferSize = 64*1024; 

	HKEY rootKey = HKEY_LOCAL_MACHINE;
	DWORD holder;
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(holder);
	if (RegOpenKeyEx(rootKey, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegMux", 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "BoostThreadPriorities", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bRegBoostThreads = (holder == 1);
		}
		if (RegQueryValueEx(myKey, "OptimizeNetTransfers", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			m_bRegOptimizeTransfers = (holder == 1);
		}
		if (RegQueryValueEx(myKey, "BufferSize", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			if ( holder <512 && holder > 0 )
				dwAysncBufferSize = holder *1024;
		}
		RegCloseKey(myKey);
	}
	m_pWriteBuffer = NULL;//(BYTE*)new char[m_dwBufferSize];
	m_dwBufferSize = 0;
	m_dwByteInBuffer = 0;
	m_pAsyncIO = new CAsyncIo( dwAysncBufferSize );
	_flog( "MPEG2Dump.LOG", "Dmup is running. buffer size:%d\n", dwAysncBufferSize ) ;
	
}


//
// SetFileName
//
// Implemented for IFileSinkFilter support
//
// JAK 6/10/3 Added: NULL filenames allow you to keep the filter graph running, but not write
// the output data anywhere. It just dumps to nowhere.
STDMETHODIMP CMPEG2Dump::SetFileName(LPCOLESTR pszFileName,const AM_MEDIA_TYPE *pmt)
{
	HRESULT hr = S_OK;
	{
		// Is this a valid filename supplied
		CAutoLock lock(&m_fileLock);
		if (pszFileName)
		{
			DbgLog((LOG_TRACE, 2, TEXT("Setting file name for dumper to %ls"), pszFileName));
			if(wcslen(pszFileName) > MAX_PATH)
				return ERROR_FILENAME_EXCED_RANGE;
		}
		// If a file's already open, then this is being called while
		// we're executing and we need to do a switch.
		if (m_bIgnoreData)
		{
			m_bDropNextSeq = true;
		}
		else if (m_hFile != INVALID_HANDLE_VALUE)
		{
			if (m_pNextFileName)
			{
				delete m_pNextFileName;
				m_pNextFileName = NULL;
			}
			m_pNextFileName = new WCHAR[1+lstrlenW(pszFileName)];
			if (m_pNextFileName == 0)
				return E_OUTOFMEMORY;
			lstrcpyW(m_pNextFileName, pszFileName);
			m_switchNotify.Reset();
		}

		// Take a copy of the filename

		if (pszFileName)
		{
			// See if it's a URL to a remote file upload
			if (!wcsncmp(pszFileName, L"stv://", 6))
			{
				m_bRemoteFile = TRUE;
				// The URL format is as follows:
				// stv://uploadID@serverHost/filepath
				// where uploadID will be a positive signed 32 bit integer, serverHost is the hostname of the server to connect to on port 7818,
				// and the string after the / after the serverHost is the file path on the remote server
				const WCHAR* atSign = wcschr(pszFileName, L'@');
				if (atSign)
				{
					m_dwUploadKey = wcstol(pszFileName + 6, NULL, 10);
					DbgLog((LOG_TRACE, 2, TEXT("Upload key=%d"), m_dwUploadKey));
					const WCHAR* firstSlash = wcschr(atSign + 1, L'/');
					if (firstSlash)
					{
						// Now extract the hostname and the file path
						WideCharToMultiByte(GetACP(), 0, atSign + 1, (firstSlash - atSign) - 1,
    							m_pHostname, 256, NULL, NULL);
						m_pHostname[(int)(firstSlash - atSign - 1)] = L'\0';
						DbgLog((LOG_TRACE, 2, TEXT("Upload host=%s"), m_pHostname));
						pszFileName = firstSlash + 1;
						_flog( "MPEG2Dump.LOG", "Dmup file:%s\n", pszFileName ) ;
						if (m_pFileName) {
				                        if (m_pNextFileName)
				                        {
				                                delete m_pNextFileName;
				                                m_pNextFileName = NULL;
				                        }
				                        m_pNextFileName = new WCHAR[1+lstrlenW(pszFileName)];
				                        if (m_pNextFileName == 0)
                                				return E_OUTOFMEMORY;
				                        lstrcpyW(m_pNextFileName, pszFileName);
				                        m_switchNotify.Reset();
						}

					}
					else
					{
						DbgLog((LOG_ERROR, 1, TEXT("Missing the / in remote file URL")));
						return E_FAIL;
					}
				}
				else
				{
					DbgLog((LOG_ERROR, 1, TEXT("Missing the @ in remote file URL")));
						return E_FAIL;
				}
			}
			else
			{
				m_bRemoteFile = FALSE;
			}
			if (!m_pNextFileName)
			{
				m_pFileName = new WCHAR[1+lstrlenW(pszFileName)];
				if (m_pFileName == 0)
					return E_OUTOFMEMORY;
				lstrcpyW(m_pFileName,pszFileName);

				// Open the file now
				hr = OpenFile();
				_flog( "MPEG2Dump.LOG", "Dmup file:%s hr=0x%x \n", CWtoT(m_pFileName), hr ) ;
			}
			m_bIgnoreData = FALSE;
		}
		else
			m_bIgnoreData = TRUE;
		m_circFileSize = m_nextCircFileSize;
		m_nextCircFileSize = 0;
	}
	if (m_pNextFileName)
	{
		DbgLog((LOG_ERROR, 1, TEXT("Waiting for file switch to complete now that we've set the new filename...")));
		// Wait for this filename to be consumed; then we can return...but don't wait forever
		DWORD startWait = timeGetTime();
		if (m_pNextFileName)
		{
			// Wait on the object
			m_switchNotify.Wait(5000);
		}
		if (m_pNextFileName)
		{
			CAutoLock lock(&m_fileLock);
			if (m_pNextFileName)
			{
				DbgLog((LOG_ERROR, 1, TEXT("File switch didn't find boundary in time; switch the old non-seamless way")));
				// The switch didn't complete in time; just force it like the old way
				CloseFile( TRUE );
				m_bDropNextSeq = true;
				delete m_pFileName;
				m_pFileName = NULL;
				m_pFileName = m_pNextFileName;
				m_pNextFileName = NULL;
				hr = OpenFile();
				_flog( "MPEG2Dump.LOG", "Dmup file:%s hr=0x%x  \n", CWtoT(m_pFileName), hr ) ;
			}
		}
	}
	return hr;
} // SetFileName


//
// GetCurFile
//
// Implemented for IFileSinkFilter support
//
STDMETHODIMP CMPEG2Dump::GetCurFile(LPOLESTR * ppszFileName,AM_MEDIA_TYPE *pmt)
{
    CheckPointer(ppszFileName, E_POINTER);
    *ppszFileName = NULL;
    if (m_pFileName != NULL) {
        *ppszFileName = (LPOLESTR)
        QzTaskMemAlloc(sizeof(WCHAR) * (1+lstrlenW(m_pFileName)));
        if (*ppszFileName != NULL) {
            lstrcpyW(*ppszFileName, m_pFileName);
        }
    }

    if(pmt) {
        ZeroMemory(pmt, sizeof(*pmt));
        pmt->majortype = MEDIATYPE_NULL;
        pmt->subtype = MEDIASUBTYPE_NULL;
    }
    return S_OK;

} // GetCurFile


// Destructor

CMPEG2Dump::~CMPEG2Dump()
{
	AsyncFlushFile();
	DWORD dwPeakBufferNum  = m_pAsyncIO->PeakBufferNum();
	DWORD dwPeakBufferSize = dwPeakBufferNum*m_pAsyncIO->GetBufferSize();
	DWORD dwTotalRequest = m_pAsyncIO->GetRequestNum();
	delete m_pAsyncIO;

    CloseFile( FALSE );
	if (m_pPin)
	{
	    delete m_pPin;
		m_pPin = NULL;
	}
	if (m_pFilter)
	{
	    delete m_pFilter;
		m_pFilter = NULL;
	}
	if (m_pFileName)
	{
	    delete m_pFileName;
		m_pFileName = NULL;
	}
	_flog( "MPEG2Dump.LOG", "Total request num:%d, peak buffer number:%d, peak buffer size:%d\n\n", 
							dwTotalRequest,  dwPeakBufferNum, dwPeakBufferSize  ) ;

}


//
// CreateInstance
//
// Provide the way for COM to create a dump filter
//
CUnknown * WINAPI CMPEG2Dump::CreateInstance(LPUNKNOWN punk, HRESULT *phr)
{
	_flog_check(); //ZQ
    CMPEG2Dump *pNewObject = new CMPEG2Dump(punk, phr);
    if (pNewObject == NULL) {
        *phr = E_OUTOFMEMORY;
    }
    return pNewObject;

} // CreateInstance


//
// NonDelegatingQueryInterface
//
// Override this to say what interfaces we support where
//
STDMETHODIMP CMPEG2Dump::NonDelegatingQueryInterface(REFIID riid, void ** ppv)
{
    CheckPointer(ppv,E_POINTER);
    CAutoLock lock(&m_Lock);

    // Do we have this interface

    if (riid == IID_IFileSinkFilter) {
        return GetInterface((IFileSinkFilter *) this, ppv);
    } 
	else if (riid == IID_IMpegMux) {
		return GetInterface((IMpegMux *) this, ppv);
	}
    else if (riid == IID_IBaseFilter || riid == IID_IMediaFilter || riid == IID_IPersist) {
	    return m_pFilter->NonDelegatingQueryInterface(riid, ppv);
    } 

    return CUnknown::NonDelegatingQueryInterface(riid, ppv);

} // NonDelegatingQueryInterface

STDMETHODIMP CMPEG2Dump::get_ShareInfo(ShareInfo **sharin)
{
	*sharin = &m_shareInfo;
	return S_OK;
}

STDMETHODIMP CMPEG2Dump::get_FileLength(LONGLONG *fileLength)
{
	CAutoLock(&(m_shareInfo.shareLock));
	// 64-bit data assignment, which is NOT guaranteed to be atomic!
	*fileLength = m_shareInfo.totalWrite;
	return S_OK;
}

STDMETHODIMP CMPEG2Dump::put_CircularSize(long lCircSize)
{
	if (m_pFilter->IsStopped())
		m_circFileSize = lCircSize;
	else
		m_nextCircFileSize = lCircSize;
	return S_OK;
}

//
// OpenFile
//
// Opens the file ready for dumping
//
HRESULT CMPEG2Dump::OpenFile()
{
    WCHAR *pFileName = NULL;

    // Is the file already opened
    if (m_hFile != INVALID_HANDLE_VALUE) {
        return NOERROR;
    }

    // Has a filename been set yet
    if (m_pFileName == NULL) {
        return ERROR_INVALID_NAME;
    }

    // Convert the UNICODE filename if necessary

    pFileName = m_pFileName;

    // Try to open the file

	HRESULT hr = S_OK;
	if (m_bRemoteFile)
	{
		hr = OpenConnection();
	}
	else
	{
		m_hFile = CreateFileW((LPCWSTR) pFileName,   // The filename
							GENERIC_WRITE | GENERIC_READ,         // File access
							FILE_SHARE_READ | FILE_SHARE_WRITE,             // Share access
							NULL,                  // Security
							OPEN_ALWAYS,         // Open flags
							(DWORD) 0,             // More flags
							NULL);                 // Template

		if (m_hFile == INVALID_HANDLE_VALUE) {
			DWORD dwErr = GetLastError();
			return HRESULT_FROM_WIN32(dwErr);
		}
	}

	m_shareInfo.totalWrite = 0;
	m_llPosition = 0;
	m_dwByteInBuffer = 0;
	return S_OK;

} // Open


//
// CloseFile
// Closes any dump file we have opened
//
HRESULT CMPEG2Dump::CloseFile( BOOL bAsync )
{
	CAutoLock lock(&m_fileLock);
	if (m_bRemoteFile)
	{
		CloseConnection();
	    m_hFile = INVALID_HANDLE_VALUE;
		return NOERROR;
	}
    if (m_hFile == INVALID_HANDLE_VALUE) {
        return NOERROR;
    }

	if ( !bAsync )
		CloseHandle(m_hFile);
	else
		AsyncCloseFile( );

    m_hFile = INVALID_HANDLE_VALUE;
    return NOERROR;

} // Open


HRESULT CMPEG2Dump::AsyncWriteFile( BYTE* pbData, DWORD lData )
{
	if ( m_dwBufferSize == 0 )
	{
		HRESULT hr = m_pAsyncIO->Request( m_hFile, &m_pWriteBuffer, &m_dwBufferSize, WRITE_CMD );
		m_dwByteInBuffer = 0;
		if ( hr != S_OK )
		{
			_flog( "MPEG2Dump.LOG", "Data dropped due to buffer maximum limition hr=0x%x\n", hr );
			return hr;
		}
	}

	while ( lData > 0 )
	{
		if ( lData + m_dwByteInBuffer >= m_dwBufferSize )
		{
			DWORD bytes = min( lData, m_dwBufferSize - m_dwByteInBuffer ) ;
			memcpy( m_pWriteBuffer + m_dwByteInBuffer, pbData, bytes );

			m_pAsyncIO->Complete( m_dwBufferSize );
			HRESULT hr = m_pAsyncIO->Request( m_hFile, &m_pWriteBuffer, &m_dwBufferSize, WRITE_CMD );
			m_dwByteInBuffer = 0;
			if ( hr != S_OK )
				return hr;

			pbData += bytes;
			lData -= bytes;
			bytes = min( lData, m_dwBufferSize );
			memcpy( m_pWriteBuffer, (BYTE*)pbData, bytes );
			m_dwByteInBuffer = bytes;
			pbData += bytes;
			lData -= bytes;

		} else
		{
			memcpy( m_pWriteBuffer + m_dwByteInBuffer, pbData, lData );
			m_dwByteInBuffer += lData;
			pbData += lData;
			lData = 0;
		}
	}
	return S_OK;
}


HRESULT CMPEG2Dump::AsyncCloseFile(  )
{
	PBYTE pWriteBuffer;
	DWORD dwBufferSize;
	m_pAsyncIO->Complete( m_dwByteInBuffer );
	m_pWriteBuffer = NULL;
	m_dwBufferSize = 0;
	m_dwByteInBuffer = 0;
	HRESULT hr = m_pAsyncIO->Request( m_hFile, &pWriteBuffer, &dwBufferSize, CLOSE_FILE );
	if ( hr != S_OK )
		return hr;
	m_pAsyncIO->Complete( 0 );
	m_hFile =INVALID_HANDLE_VALUE;
	delete m_pFileName;
	m_pFileName = NULL;
	return S_OK;
}

HRESULT CMPEG2Dump::AsyncFlushFile( )
{
	HRESULT hr=S_OK;
	if ( m_dwBufferSize )
		hr = m_pAsyncIO->Complete( m_dwByteInBuffer );
	m_pWriteBuffer = NULL;
	m_dwBufferSize = 0;
	m_dwByteInBuffer = 0;

	DWORD dwTimeOut = 10*60; //60 sec;
	while ( dwTimeOut-- )
	{
		if ( m_pAsyncIO->RequestInWaiting( ) )
			Sleep( 100 );
	}

	if ( m_pAsyncIO->RequestInWaiting( ) )
		return VFW_E_TIMEOUT;

	return hr;
}

HRESULT CMPEG2Dump::WriteFile( const void *pv, ULONG cb )
{
	ULONG wb=0;
	HRESULT hr = ::WriteFile( m_hFile,(PBYTE)pv,cb, &wb, NULL ) ? S_OK: E_FAIL;
	return hr;
}


#define BYTES_PER_NET_WRITE 32768
// Writes data to the network socket using the current file position
HRESULT CMPEG2Dump::WriteNetwork(PBYTE data, DWORD len)
{
	DWORD initialLen = len;
	char request[512];
	if (m_bRegOptimizeTransfers && m_bytesLeftInNetworkWrite > 0)
	{
		int dataSent = send(sd, (const char*)data, min((int)len, m_bytesLeftInNetworkWrite), 0);
		m_bytesLeftInNetworkWrite -= dataSent;
		while (dataSent < 0 || ((len - dataSent) > 0 && m_bytesLeftInNetworkWrite > 0))
		{
			if (dataSent < 0)
			{
				DbgLog((LOG_TRACE, 2, TEXT("socket write failed, reopening connection...")));
				// Connection died, try to reopen it
				HRESULT hr = ReOpenConnection();
				if (FAILED(hr))
					return hr;
			}
			else
			{
				len -= dataSent;
				data += dataSent;
			}
			dataSent = send(sd, (const char*)data, min((int)len, m_bytesLeftInNetworkWrite), 0);
			m_bytesLeftInNetworkWrite -= dataSent;
		}
		if ((len - dataSent) == 0)
		{
			DbgLog((LOG_TRACE, 2, TEXT("Wrote out %d bytes to socket"), initialLen));
			m_llPosition += initialLen;
			return S_OK;
		}
	}
	if (m_bRegOptimizeTransfers)
		m_bytesLeftInNetworkWrite = max(len, BYTES_PER_NET_WRITE);
	LARGE_INTEGER li;
	li.QuadPart = m_llPosition;
	TCHAR  temp[20];
	int pos=20;
	temp[--pos] = 0;
	int digit;
	// always output at least one digit
	do {
	// Get the rightmost digit - we only need the low word
		/*
			* NOTE: THIS WAS A BUG IN MS CODE, FOR CDisp they were doing
			* lowPart % 10 for each digit they printed. But that only did
			* the modulus on the lower 32 bits, and not the whole number, 
			* resulting in errors. Now I modulus on the whole 64-bit number
			* which will give the correct digit result.
			*/
		digit = (int)(li.QuadPart % 10); // was lowPart
		li.QuadPart /= 10;
		temp[--pos] = (TCHAR) digit+L'0';
	} while (li.QuadPart);
	sprintf(request, "WRITE %s %d\r\n", temp+pos, m_bRegOptimizeTransfers ? m_bytesLeftInNetworkWrite : len);
	// strlen cast as int to prevent warning C4267, as send needs dataSize to be int
	int dataSize = (int)strlen(request);
	if (send(sd, request, dataSize, 0) < dataSize)
	{
		DbgLog((LOG_TRACE, 2, TEXT("socket write failed, reopening connection...")));
		// Try to do it again...
		HRESULT hr = ReOpenConnection();
		if (FAILED(hr))
			return hr;
		if (send(sd, request, dataSize, 0) < dataSize)
		{
			DbgLog((LOG_TRACE, 2, TEXT("socket write failed, aborting.")));
			return E_FAIL;
		}
	}
	int dataSent = send(sd, (const char*)data, len, 0);
	if (m_bRegOptimizeTransfers)
		m_bytesLeftInNetworkWrite -= dataSent;
	while (dataSent < 0 || (len - dataSent) > 0)
	{
		if (dataSent < 0)
		{
			DbgLog((LOG_TRACE, 2, TEXT("socket write failed, reopening connection...")));
			// Connection died, try to reopen it
			HRESULT hr = ReOpenConnection();
			if (FAILED(hr))
				return hr;
		}
		else
		{
			len -= dataSent;
			data += dataSent;
		}
		dataSent = send(sd, (const char*)data, len, 0);
		if (m_bRegOptimizeTransfers)
			m_bytesLeftInNetworkWrite -= dataSent;
	}
	DbgLog((LOG_TRACE, 2, TEXT("Wrote out %d bytes to socket"), initialLen));
	m_llPosition += initialLen;
	return S_OK;
}

//
// Write
//
// Write stuff to the file
//
HRESULT CMPEG2Dump::Write(PBYTE pbData,LONG lData)
{
	if (!m_bDropNextSeq)
	{
		LONGLONG startSize = (LONGLONG) m_shareInfo.totalWrite;
		if (m_bIgnoreData)
			return S_OK;

		//cicular buffer file
		if (m_circFileSize > 0)
		{
			startSize = startSize % m_circFileSize;
			if (startSize == 0)
			{
				if (m_bRemoteFile)
				{
					m_llPosition = 0;
				}
				else 
				{ 
					if (SetFilePointer(m_hFile, 0, NULL, FILE_BEGIN) == INVALID_FILE_SIZE)
					{
						DWORD dwErr = GetLastError();
						return HRESULT_FROM_WIN32(dwErr);
					}
				}
			}
			if (startSize + lData > m_circFileSize)
			{
				// We're going to go over on this write
				DWORD chunk1 = (DWORD)(((LONGLONG)m_circFileSize) - startSize);
				if (m_bRemoteFile)
				{
					HRESULT hr = WriteNetwork(pbData, chunk1);
					if (FAILED(hr)) return hr;
				}
				else { 
					HRESULT hr;
					if ( (hr = WriteFile( pbData, chunk1 )) != S_OK ) 
						return hr;				}
				if (m_bRemoteFile)
				{
					m_llPosition = 0;
				}
				else 
				{
					if (SetFilePointer(m_hFile, 0, NULL, FILE_BEGIN) == INVALID_FILE_SIZE)
					{
						DWORD dwErr = GetLastError();
						return HRESULT_FROM_WIN32(dwErr);
					}
				}
				if (m_bRemoteFile)
				{
					HRESULT hr = WriteNetwork(pbData + chunk1, lData - chunk1);
					if (FAILED(hr)) return hr;
				}
				else
				{
					HRESULT hr;
					if ( (hr = WriteFile( (pbData + chunk1),(DWORD)(lData - chunk1) )) != S_OK )
						return hr;
				}
			}
			else
			{
				// This write is clear
				if (m_bRemoteFile)
				{
					HRESULT hr = WriteNetwork(pbData, lData);
					if (FAILED(hr)) return hr;
				}
				else 
				{	
					HRESULT hr;
					if ( (hr = WriteFile( pbData,(DWORD)lData )) != S_OK ) 
						return hr;
				}
			}
		}
		else
		{
			if (m_bRemoteFile)
			{
				HRESULT hr = WriteNetwork(pbData, lData);
				if (FAILED(hr)) return hr;
			}
			else 
			{
				HRESULT hr;
				if (( hr = AsyncWriteFile( pbData, (DWORD)lData )) != S_OK ) 
					return hr;
			}
		}

		{
			CAutoLock(&(m_shareInfo.shareLock));
			m_shareInfo.totalWrite += lData;
		}
	}
    return S_OK;
}


// IStream methods for CMPEG2Dump

STDMETHODIMP CMPEG2Dump::Read(void *pv, ULONG cb, ULONG *pcbRead)
{
    DbgLog((LOG_TRACE, 2, TEXT("CMPEG2Dump::Read %d"), cb));
	CAutoLock lock2(&m_fileLock);
	ULONG actualRead = 0;
	HRESULT hr = ReadFile(m_hFile, pv, cb, &actualRead, NULL);
	if (pcbRead)
		*pcbRead = actualRead;
    DbgLog((LOG_TRACE, 2, TEXT("CDumpInputPin::Read hr=0x%x"), hr));
    return S_OK;
}

STDMETHODIMP CMPEG2Dump::Write(const void *pv, ULONG cb, ULONG *pcbWritten)
{
	m_bIStreamMode = TRUE;
	if (cb == 0) return S_OK;
	CAutoLock lock2(&m_fileLock);
	ULONG wb;
	m_bDropNextSeq = false;
	DbgLog((LOG_TRACE, 2, TEXT("Write called cb=%d"), cb));
	//HRESULT hr = WriteFile( m_hFile,(PBYTE)pv,cb, &wb, NULL ); //if wrong, change back to 
	HRESULT hr = Write( (PBYTE)pv, cb );
	wb=cb;
	if (SUCCEEDED(hr))
	{
		{
			CAutoLock(&(m_shareInfo.shareLock));
			m_shareInfo.totalWrite += cb;
		}
		if (pcbWritten)
			*pcbWritten = wb;
	}
    DbgLog((LOG_TRACE, 2, TEXT("CDumpInputPin::Write hr=0x%x"), hr));
	return hr;
}

STDMETHODIMP CMPEG2Dump::Seek(LARGE_INTEGER dlibMove,
		       DWORD dwOrigin,
		       ULARGE_INTEGER *plibNewPos)
{
	m_bIStreamMode = TRUE;
	CAutoLock lock2(&m_fileLock);
	DbgLog((LOG_TRACE, 2, TEXT("Seek called pos=%s orig=%d"), (LPCTSTR) CDisp(dlibMove.QuadPart, CDISP_DEC), dwOrigin));
	HRESULT hr = S_OK;
	LARGE_INTEGER filePos = {0};
	filePos.QuadPart = dlibMove.QuadPart;
	if ((filePos.LowPart = SetFilePointer(m_hFile, filePos.LowPart, &(filePos.HighPart), dwOrigin == STREAM_SEEK_SET ? FILE_BEGIN :
		(dwOrigin == STREAM_SEEK_END ? FILE_END : FILE_CURRENT))) == INVALID_FILE_SIZE)
	{
		DWORD dwErr = GetLastError();
		hr = HRESULT_FROM_WIN32(dwErr);
		DbgLog((LOG_TRACE, 2, TEXT("Seek failure hr=0x%x"), hr));
		return hr;
	}
    
	{
		CAutoLock(&(m_shareInfo.shareLock));
		m_shareInfo.totalWrite = filePos.QuadPart;
	}
    if (plibNewPos) 
	{
		(*plibNewPos).QuadPart = filePos.QuadPart;
    }
    return S_OK;
}

STDMETHODIMP CMPEG2Dump::SetSize(ULARGE_INTEGER libNewSize)
{
	CAutoLock lock2(&m_fileLock);
	// Get the current file position
	DbgLog((LOG_TRACE, 2, TEXT("SetSize called size=%s"), (LPCTSTR) CDisp((LONGLONG)libNewSize.QuadPart, CDISP_DEC)));
	LARGE_INTEGER filePos = {0};
	filePos.LowPart = SetFilePointer(m_hFile, 0, &(filePos.HighPart), FILE_CURRENT);
	if (filePos.LowPart == INVALID_FILE_SIZE)
	{
		return STG_E_INVALIDFUNCTION;
	}
	LARGE_INTEGER theSize;
	theSize.QuadPart= libNewSize.QuadPart;
	SetFilePointer(m_hFile, theSize.LowPart, &(theSize.HighPart), FILE_BEGIN);
	SetEndOfFile(m_hFile);
	if (filePos.QuadPart < (LONGLONG)libNewSize.QuadPart)
	{
		SetFilePointer(m_hFile, filePos.LowPart, &(filePos.HighPart), FILE_BEGIN);
	}
    return S_OK;
}

STDMETHODIMP CMPEG2Dump::CopyTo(IStream *pstrm,
			 ULARGE_INTEGER cb,
			 ULARGE_INTEGER *pcbRead,
			 ULARGE_INTEGER *pcbWritten)
{
    DbgLog((LOG_TRACE, 2, TEXT("CDumpInputPin::CopyTo")));
    return S_OK;
}

STDMETHODIMP CMPEG2Dump::Commit(DWORD grfCommitFlags)
{
    DbgLog((LOG_TRACE, 2, TEXT("CMPEG2Dump::Commit")));
    return S_OK;
}

STDMETHODIMP CMPEG2Dump::Revert(void)
{
    DbgLog((LOG_TRACE, 2, TEXT("CMPEG2Dump::Revert")));
    return S_OK;
}

STDMETHODIMP CMPEG2Dump::LockRegion(ULARGE_INTEGER libOffset,
			     ULARGE_INTEGER cb,
			     DWORD dwLockType)
{
    DbgLog((LOG_TRACE, 2, TEXT("CMPEG2Dump::LockRegion")));
    return S_OK;
}

STDMETHODIMP CMPEG2Dump::UnlockRegion(ULARGE_INTEGER libOffset,
			       ULARGE_INTEGER cb,
			       DWORD dwLockType)
{
    DbgLog((LOG_TRACE, 2, TEXT("CMPEG2Dump::UnlockRegion")));
    return S_OK;
}

STDMETHODIMP CMPEG2Dump::Stat(STATSTG *pstatstg,
		       DWORD grfStatFlag)
{
    DbgLog((LOG_TRACE, 2, TEXT("CMPEG2Dump::Stat")));
	CAutoLock lock2(&m_fileLock);

	ULARGE_INTEGER fileSize = {0};
	fileSize.LowPart = GetFileSize(m_hFile, &(fileSize.HighPart));

    return S_OK;
}


STDMETHODIMP CMPEG2Dump::Clone(IStream **ppstm)
{
    DbgLog((LOG_TRACE, 2, TEXT("CMPEG2Dump::Clone")));
    return S_OK;
}

STDMETHODIMP CMPEG2Dump::ForceCleanUp()
{ 
	//a walk around way for M780 bug  ZQ.
	int ct = GetOwner()->Release();
	GetOwner()->AddRef();

	_flog( "MPEG2Dump.LOG", "ForceCleanUp %d\n", ct ); 

	DbgLog((LOG_TRACE, 2, TEXT("Refernce %x"), ct ));
	if ( ct > 1 )	ct = GetOwner()->Release(); 

	return S_OK; 
};


HRESULT CMPEG2Dump::ReOpenConnection()
{
	CloseConnection();
	HRESULT hr = OpenConnection();
	return hr;
}

HRESULT CMPEG2Dump::OpenConnection()
{
	if (!m_bRemoteFile) return E_POINTER;
	if (!m_pFileName) return E_POINTER;
	WSADATA wsaData;
	if (WSAStartup(0x202,&wsaData) == SOCKET_ERROR) {
		DbgLog((LOG_TRACE, 2, TEXT("WSAStartup failed with error %d"), WSAGetLastError()));
		return E_FAIL;
	}
	DbgLog((LOG_TRACE, 2, TEXT("WSAStartup succeeded")));
	_flog( "MPEG2Dump.LOG", "OpenConnection\n" ) ;

	sd = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
	if (sd == INVALID_SOCKET)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Invalid socket descriptor")));
		return E_FAIL;
	}

	DbgLog((LOG_TRACE, 2, TEXT("socket was created %d"), (DWORD) sd));
	// Set the socket timeout option. If a timeout occurs, then it'll be just
	// like the server closed the socket.
	if (setsockopt(sd, SOL_SOCKET, SO_RCVTIMEO, (char*)&RED_TIMEOUT, sizeof(RED_TIMEOUT)) == SOCKET_ERROR)
	{
		DbgLog((LOG_TRACE, 2, "Error setting socket timeout, error: %d", WSAGetLastError()));
		return E_FAIL;
	}
	DbgLog((LOG_TRACE, 2, TEXT("set socket timeout")));
	// Set the socket linger option, this makes sure the QUIT message gets received
	// by the server before the TCP reset message does.
	LINGER lingonberry;
	lingonberry.l_onoff = TRUE;
	lingonberry.l_linger = 1;
	if (setsockopt(sd, SOL_SOCKET, SO_LINGER, (char*)&lingonberry, sizeof(LINGER)) == SOCKET_ERROR)
	{
		DbgLog((LOG_TRACE, 2, "Error setting socket close linger, error: %d", WSAGetLastError()));
		return E_FAIL;
	}
	DbgLog((LOG_TRACE, 2, TEXT("set socket linger on close")));
	struct sockaddr address;
	struct sockaddr_in* inetAddress;
	inetAddress = (struct sockaddr_in*) ( (void *) &address); // cast it to IPV4 addressing
	inetAddress->sin_family = PF_INET;
	inetAddress->sin_port = htons(7818);

	struct hostent* hostptr;
	hostptr = gethostbyname(m_pHostname);
	if (!hostptr)
	{
		DbgLog((LOG_TRACE, 2, TEXT("gethostbyname error 0x%x"), WSAGetLastError()));
		return E_FAIL;
	}
	DbgLog((LOG_TRACE, 2, TEXT("resolved host address")));
    memcpy(&inetAddress->sin_addr.s_addr, hostptr->h_addr, hostptr->h_length );
 
	DbgLog((LOG_TRACE, 2, TEXT("about to connect")));
    if (connect(sd, (struct sockaddr *) ((void *)&address), sizeof(address)) < 0)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Socket connection error host=%s"), m_pHostname));
		return E_FAIL;
	}

	DbgLog((LOG_TRACE, 2, TEXT("connect succeeded")));
    TCHAR *lpszFileName=0;
    int cch = lstrlenW(m_pFileName) + 1;
    lpszFileName = new char[cch * 2];
    if (!lpszFileName) {
      	return E_OUTOFMEMORY;
    }
    WideCharToMultiByte(GetACP(), 0, m_pFileName, -1,
    		lpszFileName, cch, NULL, NULL);
	DbgLog((LOG_TRACE, 2, TEXT("OpenConnection(%s) IN"), lpszFileName));
	char data[512];
	sprintf(data, "WRITEOPEN %s %d\r\n", lpszFileName, m_dwUploadKey);
	delete [] lpszFileName;
	// strlen cast as int to prevent warning C4267, as send needs dataSize to be int
	int dataSize = (int)strlen(data);
	if (send(sd, data, dataSize, 0) < dataSize)
	{
		DbgLog((LOG_TRACE, 2, TEXT("socket write failed")));
		return E_FAIL;
	}

	DbgLog((LOG_TRACE, 2, TEXT("send succeeded")));
	int res;
	if ((res = sockReadLine(sd, data, sizeof(data))) < 0 || strcmp(data, "OK"))
	{
		DbgLog((LOG_TRACE, 2, TEXT("socket readline failed res=%d data=%s"), res, data));
		return E_FAIL;
	}

	DbgLog((LOG_TRACE, 2, TEXT("OpenConnection() OUT")));
	return S_OK;
}

HRESULT CMPEG2Dump::CloseConnection()
{
	DbgLog((LOG_TRACE, 2, TEXT("CloseConnection() IN")));
	char* data = "QUIT\r\n";
	// strlen cast as int to prevent warning C4267, as send needs dataSize to be int
	int dataSize = (int)strlen(data);
	send(sd, data, dataSize, 0);
	closesocket(sd);
	WSACleanup();
	DbgLog((LOG_TRACE, 2, TEXT("CloseConnection() OUT")));
	_flog( "MPEG2Dump.LOG", "CloseConnection\n" ) ;
	return S_OK;
}

//
// DllRegisterSever
//
// Handle the registration of this filter
//
STDAPI DllRegisterServer()
{
	_flog( "MPEG2Dump.LOG", "Register called\n" ) ;
    return AMovieDllRegisterServer2( TRUE );

} // DllRegisterServer


//
// DllUnregisterServer
//
STDAPI DllUnregisterServer()
{
	_flog( "MPEG2Dump.LOG", "Unregister called\n" ) ;
    return AMovieDllRegisterServer2( FALSE );

} // DllUnregisterServer
