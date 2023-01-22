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
#ifndef __DEMUXFILTER_H__
#define __DEMUXFILTER_H__

#include <stdio.h>
#include "ISDeMux.h"
#include "SDeMuxThread.h"


// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)
#pragma warning(disable : 4995)

#if (defined(_MSC_VER) && (_MSC_VER < 1900))
  #define snprintf _snprintf
  #define vsnprintf  vsnprintf
#endif


#include "ISDeMux.h"

#include "../../../third_party/Microsoft/StrmDeMux/alloc.h"

#define ENABLE_LOG	1
#define DEBUG_LEVEL	4


//#ifndef DEBUG
	#ifdef ENABLE_LOG	
		#undef  DbgLog
		void _flog( int type, int level, const char* cstr, ... );
		#define flog(x)    _flog x
		#define DbgLog(x)   flog( x )
	#endif
//#endif

//#define  TEST_CASE_1 1

#define TS_FORMAT   1 //ZQ

typedef int (*EVENT_DUMP)( void* pContext, short nBytes, void* pData );

#define VIDEO_DATA		1
#define AUDIO_DATA		2
#define SUBPICTURE_DATA 3


//ZQ orignal
#define PUSH_READER_BLOCK_SIZE (32*1024*6)
#define BLOCK_SIZE   (16*1024)
#define BLOCK_NUM    (240)

#define DISBALE_SAMPLE_PTS_FLAG		0x10

typedef struct
{
	unsigned long bitrates;
	unsigned long overflow;
	long test_data1;
	long test_data2;
	char* message;

} STATISTIC;

typedef struct {
	LONGLONG start;
	LONGLONG stop;
} SEEK_POS;

class CMPEG2FrameParser;
class CSeeker;
class CPushReader;
//class COutputBuffer;

class CSDeMuxFilter : public CBaseFilter, public CDMThread,  public ISDeMux, public IMediaSeeking, public ISpecifyPropertyPages, public IAMClockAdjust
{
	friend class CSDeMuxInPin;
	friend class CSDeMuxOutPin;
	friend class CPushReader;
	friend class CSubAllocator;
	friend class CBukAllocator;

public:
	CSDeMuxFilter( LPUNKNOWN pUnk, HRESULT *phr);
	~CSDeMuxFilter( );
	static CUnknown * WINAPI CreateInstance(LPUNKNOWN, HRESULT *);
	DECLARE_IUNKNOWN
	STDMETHODIMP NonDelegatingQueryInterface(REFIID riid, void **ppvoid);

	// IMpegDeMux methods
	STDMETHODIMP SetEventDump( void* pfn, void* context );
	STDMETHODIMP Duration( LONGLONG* prtDuration );
	STDMETHODIMP GetPlayRate( long* rate );
	STDMETHODIMP SetPlayRate( long rate );
	STDMETHODIMP GetStatistic( void** pData );
	STDMETHODIMP SetAlloProp( long dwBlockSize, long dwBufferSize );
	STDMETHODIMP GetAlloProp( long* pdwBlockSize, long* pdwBufferSize );

	// IMediaSeeking methods
    STDMETHODIMP GetCapabilities( DWORD * pCapabilities );
    STDMETHODIMP CheckCapabilities( DWORD * pCapabilities );
    STDMETHODIMP SetTimeFormat(const GUID * pFormat);
    STDMETHODIMP GetTimeFormat(GUID *pFormat);
    STDMETHODIMP IsUsingTimeFormat(const GUID * pFormat);
    STDMETHODIMP IsFormatSupported( const GUID * pFormat);
    STDMETHODIMP QueryPreferredFormat( GUID *pFormat);
    STDMETHODIMP ConvertTimeFormat(LONGLONG * pTarget, const GUID * pTargetFormat,
                                   LONGLONG    Source, const GUID * pSourceFormat );
    STDMETHODIMP SetPositions( LONGLONG * pCurrent, DWORD CurrentFlags
                             , LONGLONG * pStop, DWORD StopFlags );

    STDMETHODIMP GetPositions( LONGLONG * pCurrent, LONGLONG * pStop );
    STDMETHODIMP GetCurrentPosition( LONGLONG * pCurrent );
    STDMETHODIMP GetStopPosition( LONGLONG * pStop );
    STDMETHODIMP SetRate( double dRate);
    STDMETHODIMP GetRate( double * pdRate);
    STDMETHODIMP GetDuration( LONGLONG *pDuration);
    STDMETHODIMP GetAvailable( LONGLONG *pEarliest, LONGLONG *pLatest );
    STDMETHODIMP GetPreroll( LONGLONG *pllPreroll );

	// IAMClockDelta methods - used for audio delay
	STDMETHODIMP SetClockDelta(REFERENCE_TIME rtDelta);

	// ISpecifyPropertyPages methods
	STDMETHODIMP GetPages(CAUUID *pPages);

	// used methods by MEPG2Parer
	HRESULT CreateStream( BYTE uStreamType, BYTE uStreamId, BYTE uSubId, BYTE uTrackIndex,
		                  CMediaType *pMT, LPCWSTR pName );
	CSDeMuxOutPin* FindOutputPin( BYTE uStreamId, BYTE uSubId );
	CSDeMuxOutPin* FindOutputPin( BYTE uTrackIndex );
	CSDeMuxInPin*  InputPin() { return m_pInputPin; };
	HRESULT GetMediaType( BYTE uStreamId, BYTE uSubId, CMediaType **pMediaType, int nIndex = 0 );
	HRESULT AddMediaType( BYTE uTrackIndex, CMediaType *pMediaType );
	HRESULT SendSampleData( BYTE uTrackIndex, 
							const BYTE *pData, LONG lBytes, REFERENCE_TIME rtStart, int nSync );
	
	//CBaseFilter
	int GetPinCount();     
    CBasePin* GetPin(int iPin);
	CCritSec* pStateLock( ) { return &m_csFilter; };
	CCritSec* ParserLock( ) { return &m_csParser; };
	CCritSec* QueueLock( )  { return &m_csQueue; } 
	CSequentialAllocator* GetAllocator() { return m_pAllocator; };

	STATISTIC* GetStatistic( ) { return  &m_Statistic ; };
	double GetSeekPosRatio( ) { return m_dSeekRatio; };


protected:
    virtual HRESULT CompleteConnect(IPin *pReceivePin, CBasePin* pFilterPin );
	virtual HRESULT DisconnectConnection(CBasePin* pFilterPin);
	virtual HRESULT CheckInputType(const CMediaType *pmt);
    virtual void BreakConnect(); /*  Called when BreakConnect called on the input pin */
	HRESULT Active();
	HRESULT CreateOutputAllocator( );
	HRESULT SetupOutputAllocator( int nContentType, LONG dwBuffers, LONG dwBufferSize );
	HRESULT OutputAllocatorCommit( int uContentType );
	HRESULT OutputAllocatorDecommit(int nContentType);

	void    ReleasedSample( IMediaSample * pSample ); //called by CSubAllocator;

	STDMETHODIMP Run(REFERENCE_TIME tStart);
    STDMETHODIMP Pause();
    STDMETHODIMP Stop();

	HRESULT DoThreadProcessingLoop(void); 
	bool NeedSampleGroup( int nTrack );
private:
    CCritSec  m_csFilter;    //  Filter locking
	CCritSec  m_csParser;
	CCritSec  m_csQueue;

	CSequentialAllocator *m_pAllocator;
	CSDeMuxInPin *m_pInputPin;
	//CSequentialAllocator *m_pOutputAllocator;
	CSequentialAllocator *m_pVideoAllocator;
	CSequentialAllocator *m_pAudioAllocator;
	CSequentialAllocator *m_pSubPictureAllocator;
	CBukAllocator		 *m_pVideoBukAllocator;

	CGenericList<CSDeMuxOutPin>	 m_lOutputPin;
	CSDeMuxOutPin* m_pPassThrough;

	DWORD m_dwSeekingCaps;
	unsigned int   m_uSourceFormat;

	BOOL	  m_bRemuxOptimization;
	void*     m_pfnAFDDump;
	void*     m_pfnCCDump;
	void*     m_AFDDumpContext;
	void*	  m_CCDumpContext;
	DWORD	  m_dwDefaultChannel;
	void*	  m_pfnEventDump;
	void*	  m_EventDumpContext;

	BOOL	  m_bPostSubMsg;

	//used by Seeker 
	double	  m_dPlayRate;  //Seeking rate
	DWORD	  m_dwBitsRate;  //stream bit rate,  units (100 nanoseconds)
	BOOL      m_bPlayRewind;			

	//Position
	SEEK_POS m_SeekPos;
	BOOL	 m_bSeekPos;

	LONGLONG m_rtLastSeekPos;
	unsigned long m_dwLastSeekActionTime;
	LONGLONG m_rtSeekStartPos, m_rtSeekStartPTS; 


	//MPGE2Parer
	LONGLONG  m_rtAudioDelay;  //audio delay

	BOOL	  m_bSyncPTS;     // notify output pin sync PTS, first PTS,just once.
	BOOL	  m_bSyncOutpinPTS;//after seeking, notify outpin pinsync PTS
	LONGLONG  m_rtStartSync;  // output pin sync PTS
	LONGLONG  m_rtStartPOS;   

	//PTS seeking
	LONGLONG  m_rtCurrPTS;
	LONGLONG  m_rtCurrPOS;
	LONGLONG  m_rtLastPTS;	  // last PTS
	LONGLONG  m_dwLastPOS;
	DWORD	  m_dwIndexPTS1;
	DWORD	  m_dwIndexPTS2;
	BOOL	  m_bStaticFile;
	double	  m_dSeekRatio;
	DWORD	  m_dwPTSCount;

	LONGLONG  m_rtPreStartMediaTimeOffset; //file switch
	LONGLONG  m_rtStartMediaTimeOffset; //file switch
	LONGLONG  m_rtSeekMediaTimeOffset;  //file switch

	//Alloctor buffer for video samples
	DWORD	  m_dwBlockSize;
	DWORD	  m_dwBlockNum;
	DWORD	  m_dwReaderBlockSize;

	CPushReader* m_pPushReader;
	
	BOOL	  m_bUseBuffer;

	STATISTIC m_Statistic;

	void SetSeekPos( LONGLONG *pStart, LONGLONG* pStop );
	void GetSeekPos( LONGLONG *pStart, LONGLONG* pStop );
	LONGLONG SeekStartPos( LONGLONG StartTime, LONGLONG* pStartPTS );

	void FlushStreamsBegin( );
	void FlushStreamsEnd( );
	void DeliverEndOfStream( );
	void SendAnyway( );
	BOOL IsQutputPinActive( );
	void AllocatorStop();
	void AllocatorRun();
	int  AllocatorInUsedSampleNum();

	void  ResetOutputSampleCounter( );
	DWORD TotalOutputSampleCounter( );

	CSDeMuxOutPin* CreateOutputPin( BYTE uContentType, BYTE uStreamId, BYTE uSubId, BYTE uTrackIndex,
		                              CMediaType *pMT, LPCWSTR pName );
	HRESULT  RegisterOutputPin( CSDeMuxOutPin* pOutputPin);
	HRESULT  UnRegisterOutputPin( CSDeMuxOutPin* pOutputPin );

	void LoadCfgFromRegistry( );
	BOOL m_bAudioClockFix;

	BOOL  IsDeclineFilter(  char* pFilterName, CSDeMuxOutPin* pPin );
//	char* GetCLSIDname(const CLSID &CLSID, char *Buf, int BufLen );

	void  PostSubMesage( BYTE uTrackIndex, 
			const BYTE *pData, LONG lBytes, REFERENCE_TIME rtStart, int nSync );

	DWORD m_dwFileSwitchFlag;
	BOOL  m_bEndOfData;
	BOOL  m_bEndOfStream;
	DWORD m_dwReleasedSampleCount;

	PBYTE m_dwTrashBuffer;
	DWORD m_dwTrashSize;

	typedef struct {
		int state;
		IMediaSample *pSample;
	} _BLOCK;
	CGenericList<_BLOCK> m_lMergeList;
	int  MergeVideoDataIntoFrame( IMediaSample* pSample, unsigned char** pFrameData, int* nFrameSize, LONGLONG *pllPts );
	void ReleaseVideoFrame( );
	void FlatBlock( );
	void ReleaseAllVideoFrame( );

	//for debug 
	DWORD _video_busy_, _video_empty_, _video_free_num_, _video_counter_; 
	DWORD _audio_busy_, _audio_empty_, _audio_free_num_, _audio_counter_; 
	DWORD _max_video_size_;

	FILE* fd;
	size_t offset;
	void open_dump();
	void close_dump();
	void rewind_dump( );
	void dump_data( char* pData, int dwBytes );
	void dump_sample( IMediaSample *pSample );

	void CSDeMuxFilter::_AllocatorBufferTrace();  //ZQ REMOVE ME

};




#endif
