//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: PsiParser.h
//
// Desc: DirectShow sample code - header file for the PsiParser filter.
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

#ifndef _TS_SPLITTER_H_
#define _TS_SPLITTER_H_

#include <stdio.h>
#include <time.h>

#define TS_PACKET_MAX_LENGTH  (188+4)


#define SAFE_RELEASE(pObject) if(pObject){ pObject->Release(); pObject = NULL;}

#define TS_BUFFER_PACKETS	32

// Class supporting the PsiParser input pin
class CTSParserFilter;
class CScan;
class CTSParserInputPin : public CRenderedInputPin
{
    
private:

    CTSParserFilter *m_pFilter;        // The filter that owns us
    CCritSec * const m_pReceiveLock;    // Sample critical section
	long		     m_lUnusedBytes;
	
    bool m_bCreated;

public:

    CTSParserInputPin(CTSParserFilter *pFilter,
                  LPUNKNOWN pUnk,
                  CCritSec *pLock,
                  CCritSec *pReceiveLock,
                  HRESULT *phr);

    ~CTSParserInputPin();

    // Check that we can support this input type
    HRESULT CheckMediaType(const CMediaType *pmt);
	HRESULT GetMediaType( int iPosition, CMediaType *pMediaType );

    // Here's the next block of data from the stream.
    // Do something with this media sample
    STDMETHODIMP Receive(IMediaSample *pSample);
    STDMETHODIMP EndOfStream(void);
    STDMETHODIMP ReceiveConnection( IPin * pConnector, const AM_MEDIA_TYPE *pmt   );

    HRESULT GetParser( struct REMUXER** m_pTSRemuxer );
	HRESULT GetChannelScan( CScan** ppScan );
	void    DrainData( );
	HRESULT GetAllocSize( DWORD* dwSize );
	HRESULT CompleteConnect(IPin *pReceivePin);
	STDMETHODIMP Disconnect();
	//CMediaType m_mt;

	BOOL m_bIsSagePushSource;
	struct REMUXER* m_pTSRemuxer;
	struct TUNE     m_Tune;
	unsigned char m_cAlignBuffer[ TS_PACKET_MAX_LENGTH ];
	int		      m_nExpectedBytes;
	int		      m_nAlignBytes;
	BOOL		  m_bScanChannel;
	CScan*		  m_pCScan;
	void StartChannelScan( struct TUNE* pTune );
	void CloseChannelScan( );

}; 

/////////////////////////////////////////////////////////////////////
class CTSParserOutputPin : public CBaseOutputPin
{
    
private:

    CTSParserFilter *m_pFilter;        // The filter that owns us

public:

    CTSParserOutputPin( LPCWSTR pPinName,
				  CTSParserFilter *pFilter,
                  LPUNKNOWN pUnk,
                  CCritSec *pLock,
                  HRESULT *phr);
	
    ~CTSParserOutputPin();
	void DefaultMediaType(CMediaType *pMediaType);
    STDMETHODIMP NonDelegatingQueryInterface( REFIID riid, void ** ppv )
	{
		return CBaseOutputPin::NonDelegatingQueryInterface(riid, ppv);
	};
    // Check that we can support this input type
	HRESULT GetMediaType(int iPosition, CMediaType *pMediaType);
	HRESULT CheckMediaType(const CMediaType *pmt);
    HRESULT DecideBufferSize(IMemAllocator *pAlloc, ALLOCATOR_PROPERTIES *pRequest);
	HRESULT GetAllocSize( DWORD* dwSize );
	HRESULT CompleteConnect(IPin *pReceivePin);
	STDMETHODIMP Connect( IPin * pReceivePin,  const AM_MEDIA_TYPE *pmt );

	//STDMETHODIMP EnumMediaTypes( IEnumMediaTypes **ppEnum );
	
	STDMETHODIMP EndOfStream(void);

	HRESULT SendOut( DWORD nGroupFlag, const BYTE* pData, DWORD dwBytes, DWORD* dwUsedBytes );
	//HRESULT CSourceStream::FillBuffer(IMediaSample *) {return E_FAIL;}
}; 


/////////////////////////////////////////////////////////////////////


class CTSParserFilter  :   public CBaseFilter,
                           public ITSParser2,
						   public ISpecifyPropertyPages
{
public:
    // Implements the IBaseFilter and IMediaFilter interfaces
    //DECLARE_IUNKNOWN
    STDMETHODIMP QueryInterface(REFIID riid, __deref_out void **ppv) 
	{      
        return GetOwner()->QueryInterface(riid,ppv);            
    };                                                          
    STDMETHODIMP_(ULONG) AddRef() 
	{
        return GetOwner()->AddRef();
    };
    STDMETHODIMP_(ULONG) Release() 
	{
        return GetOwner()->Release();
    };


    CTSParserFilter(LPUNKNOWN pUnk,HRESULT *phr);
    ~CTSParserFilter();

    // This goes in the factory template table to create new instances
    static CUnknown * WINAPI CreateInstance(LPUNKNOWN punk, HRESULT *phr);

    // Overriden to say what interfaces we support where:
    // ISpecifyPropertyPages, IMpeg2PsiParser
    STDMETHODIMP NonDelegatingQueryInterface(REFIID riid, void ** ppv);

    // Return the pins that we support
    int GetPinCount();
    CBasePin *GetPin(int n);
	void ReadRegistryCfg();

	DWORD   GetBlockSize( CTSParserOutputPin *pin );
	HRESULT SendPacket( DWORD nGroupFlag, BYTE* pPacket, DWORD dwSize, DWORD* dwUsedBytes );
	HRESULT PassThrough( BYTE* pPacket, DWORD dwSize, DWORD* dwUsedBytes );
	HRESULT DumpOut( BYTE* pPacket, DWORD dwSize, DWORD* dwUsedBytes );
	HRESULT GetPassThroughSize( DWORD* pdwSize );
	HRESULT GetDumpSize( DWORD* pdwSize );
	HRESULT GetOutputSize( DWORD* pdwSize );

    STDMETHODIMP GetStreamType				(WORD wProgramNumber, WORD dwIndex, BYTE *pbVal);
	STDMETHODIMP CheckProgramStart			( BOOL* bStart );
	STDMETHODIMP Reset						( );
	STDMETHODIMP SetDumpSize				(DWORD dwSizeLimit );
	STDMETHODIMP GetStatics					(DWORD *dwInputBytes, DWORD *dwOutBytes );
	STDMETHODIMP SetDebugSourceFile			(CHAR  *pszFilename );
	STDMETHODIMP SetOutputFormat			(WORD   wOutputFormat );
	STDMETHODIMP SetNetworkType				(WORD   wFormat, WORD wSubFormat );
	STDMETHODIMP GetNetworkType				(WORD*  pwFormat, WORD* pwSubFormat );
	STDMETHODIMP SetEPGDump				    (void* pfn, void* Context );
	STDMETHODIMP SetAVInfDump			    (void* pfn, void* Context );
	STDMETHODIMP SetProgramDataDump			    (void* pfn, void* Context );
	STDMETHODIMP StopParser				    ();
	STDMETHODIMP StartParser			    ();
	STDMETHODIMP PassThrusEnable            ( BOOL Enable );
	STDMETHODIMP SelectProgramWithoutReset  (WORD  ProgramNumber);
	STDMETHODIMP SelectChannelWithoutReset	(WORD  Channel );
	STDMETHODIMP PacketInputNum				(DWORD *dwTSPackets );
	STDMETHODIMP PickupTSChannel			( WORD  wChannel, WORD  wProgram, char* Name, BOOL Reset, BOOL* ret );
	STDMETHODIMP GetNetworkList				( char* Buf, DWORD MaxBytes, DWORD* Bytes );
	STDMETHODIMP GetDebugInfo				( WORD cmd, char* Buf, DWORD BufSize );
    STDMETHODIMP LockATSCChannel			( WORD major, WORD minor );
	STDMETHODIMP PickupATSCChannel			( WORD wMajor, WORD wMinor, WORD  wProgram, char* Name, BOOL Reset, BOOL* ret );
	STDMETHODIMP SetPIDsDump			    ( void* pfn, void* Context );
	STDMETHODIMP LockUpParser			    (  );
	STDMETHODIMP UnLockUpParser			    (  );
	STDMETHODIMP PickupDVBChannel			( WORD wMajor, WORD wMinor, WORD  wProgram, char* Name, BOOL Reset, BOOL* ret );
	STDMETHODIMP GetPages					( CAUUID *pPages);
	STDMETHODIMP LockTSChannel				( void* pTune, BOOL* pRet );
	STDMETHODIMP ScanChannel				( void* pScanTune, BOOL* ret );
	STDMETHODIMP ScanChannelState			( int* pScanState, int* pFoundChannelNum, int nClock );
	STDMETHODIMP ReleaseScanChannel			( );
	STDMETHODIMP GetScanChannelList			( void **pChannelList, BOOL* pRet );
	STDMETHODIMP GetScanTuneList			( void **pTuneList, BOOL* pRet );
	STDMETHODIMP SetAudioLanguage			( char *pAudioLanguageCode );
	STDMETHODIMP WaitCleanStream			( DWORD dwLimit );
	

	void InputPinConnected( const AM_MEDIA_TYPE *pmt );
	void OutpinConnecting( IPin * pReceivePin, CBasePin* pFilterPin, const AM_MEDIA_TYPE *pmt );
	void OutpinConnected(IPin *pReceivePin, CBasePin* pFilterPin );
    CCritSec        m_Lock;                // Main renderer critical section
    CCritSec        m_ReceiveLock;         // Sublock for received samples

    // Non interface locking critical section
    CCritSec        m_ParserLock;          // To serialise access.

	WORD   m_wFormat, m_wSubFormat;
	DWORD  m_dwDumpedBytes;
	DWORD  m_dwDumpSize;
	DWORD  m_dwInputBytes;
	DWORD  m_dwOutputBytes;
	WORD   m_wOutputFormat;
	BOOL   m_bRebultPMT;
	BOOL   m_bRebuildTSPMTEnabled;
	BOOL   m_bDisableClockTimeout;

	char  m_szDebugFileSource[_MAX_PATH];
	FILE* m_hDebugFileSource;

private:

    // The nested classes may access our private state
    friend class CTSParserInputPin;
	friend class CTSParserOutputPin;

    CTSParserInputPin  *m_pInputPin;      
	CTSParserOutputPin *m_pOutputPin;
	CTSParserOutputPin *m_pVideoPin;
    CTSParserOutputPin *m_pAudioPin;
	CTSParserOutputPin *m_pPassThrusPin;
	CTSParserOutputPin *m_pDumpPin;

	BOOL  m_bPassThroughEnabled;
	BOOL  m_bParserEnabled;
	BOOL  m_bFilterRunning;
	BOOL  m_bDebugFlag;
	BOOL  m_bSubtitleEnable;
	DWORD m_dwSelectProgram;
	DWORD m_dwSelectChannel;
	DWORD m_dwSelectTSID;
	DWORD m_dwPIDDumpDisable;

}; // CTSParserFilter



#endif


