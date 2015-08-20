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
#ifndef __DEMUXER_H__
#define __DEMUXER_H__

#include "../../../third_party/Microsoft/MpegDemux/alloc.h"

#define MAX_DEMUX_TRACK_NUM  10

#define PTS2MT( x ) ( (x)*PTS_UNITS )   //PTS to Media Time ( 0.1 ms )
#define MT2PTS( x ) ( (x)*UNITS_PTS )   //Media Time to PTS
#define PTS2SEC( x ) ( (x)/MPEG_TIME_DIVISOR )
#define SEC2PTS( x ) ( (x)*MPEG_TIME_DIVISOR )

#define ONE_DAY_PTS  ((LONGLONG)24*3600*MPEG_TIME_DIVISOR)
#define ONE_DAY_MT   ((LONGLONG)24*3600*UNITS)
#define PTS_OF_1SEC  (MPEG_TIME_DIVISOR) 
#define PTS_OF_1HOUR (3600*MPEG_TIME_DIVISOR) 
#define PTS_OF_100MS ((MPEG_TIME_DIVISOR/10) 
#define PTS_OF_3HOUR (3*3600*MPEG_TIME_DIVISOR) 
#define MAX_PTS_VALUE (0x1FFFFFFFF)
#define IS_PTS_ROUND_UP( LastPTS, FirstPTS ) (LastPTS + PTS_OF_3HOUR < FirstPTS &&  FirstPTS + PTS_OF_3HOUR > MAX_PTS_VALUE )
#define PTS_ROUND_UP( LastPTS, FirstPTS ) ((LastPTS + PTS_OF_3HOUR < FirstPTS &&  FirstPTS + PTS_OF_3HOUR > MAX_PTS_VALUE ) ? LastPTS+MAX_PTS_VALUE : LastPTS ) 
//#define PTS_ROUND_UP( LastPTS, FirstPTS ) LastPTS

#define AC3_ENABLE	0x01
#define EAC3_ENABLE	0x02
#define TAC3_ENABLE	0x04
#define DTS_ENABLE	0x08
#define MDTS_ENABLE	0x10
#define AAC_ENABLE  0x20
#define AACH_ENABLE  0x40
#define LPCM_ENABLE 0x80
#define MPEG_ENABLE 0x100

#define MEM_BLOCK_SIZE	ES_BUFFER_SIZE  

#define VIDEO_PRESENT			0x0010
#define AUDIO_PRESENT			0x0020
#define SUBTITLE_PRESENT		0x0040

#define VIDEO_CONNECTED			0x0001
#define AUDIO_CONNECTED			0x0002
#define SUBTITLE_CONNECTED		0x0004


typedef struct 
{
	bool		   stream_ready;   //when codec information is ready, it's set
	unsigned char  stream_id;      
	unsigned char  stream_type;     //1 video; 2 audio; 3 subtitle;
	unsigned char  pin_index;    
	LONGLONG	   firstPTS;	   //first Picture Time Stamp
	LONGLONG	   lastPTS;		   //last Picture Time Stamp
	LONGLONG	   newPTS;         //new Picture Time Stamp, lastPTS - refernece PTS
	unsigned long  data_frames;    //send data frames
	bool		   connected;
} TRACK_INF;


class CParseReader;
class CDeMuxFilter;

class CDemuxer
{
	friend class CDeMuxFilter;
public:
    CDemuxer(CDeMuxFilter *pFilter) :
	    m_pFilter( pFilter ),
		m_nPTSTrackIndex(0),
		m_dwRateBound(0),
		m_dwVideoRate(0),
		m_dwAudioRate(0),
		m_rtDur(0),
		m_rtGuessDur(0),
		m_dwBitRate(1),
		m_llFirstPTS(0),
		m_llLastPTS(0),
		m_bRemuxOptimization(FALSE),
		m_bUseDataAlignForSync(FALSE),
		m_dwRequestedTSChannel(0),
		m_dwRequestedByUser(0),
		m_dwAudioCtrl(0),
		m_bRebuildPMT(TRUE),
		m_dwH264FrameRate(0),
		m_uEntry(0),
		m_bStopParser( FALSE ),
		m_llLastSeekPTS( 0 ),
		m_AudioStreamSelect(-1),
		m_nState(0),
		m_pAllocator( NULL ),
		//m_nRemainBytes( 0 ),
		//m_nExpectedBytes( 0 ),
		m_dwAudioTypeEnableMask(-1),
		m_bFileCrashed( FALSE ),
		m_bHDAudioFlag( FALSE ),
		m_dwConnectedType( 0 ),
		m_bIsSagePVRData( FALSE ),
		m_nPVRDataType( 0 ),
		m_pDemuxer( NULL )
    {
		m_uInputStreamFormat = 0;
		m_dwWaitingTimeForAudio = 0;

		memset( m_Converter, 0, sizeof(m_Converter) );
		memset( m_pTrackInf, 0, sizeof(m_pTrackInf) );

		m_dwRegInitReadSize = 6*12*524288; // for a really bad DTV signal, this can occur

		HKEY root_key = HKEY_LOCAL_MACHINE;
		DWORD holder;
		HKEY my_key;
		DWORD readType;
		DWORD hsize = sizeof(holder);

		if (RegOpenKeyEx(root_key, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDeMux", 0, KEY_QUERY_VALUE, &my_key) == ERROR_SUCCESS)
		{
			if (RegQueryValueEx(my_key, "InitReadSize", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
			{
				m_dwRegInitReadSize = holder;
			}
			RegCloseKey(my_key);
		}

    }

	~CDemuxer();

    HRESULT  Init(  );
	void ResetTrack( int nTrack );
	void ZeroTrack( int nTrack );
	LONGLONG Seek( LONGLONG rtSeekPTS, DWORD dwBlockSize  );

	REFERENCE_TIME GetDuration( );
	REFERENCE_TIME GetLastTimestamp( );
 
	void	PickupAudioStream( int nAudioStream );

    HRESULT Process( const BYTE * pbData, LONG lBytes, REFERENCE_TIME rtStart, BOOL bSync, LONG *lUsedBytes );
	//void EmptyParserBuffer();

	HRESULT SetRemuxOptimization(BOOL bRemuxOptimization)
	{
		m_bRemuxOptimization = bRemuxOptimization;
		return S_OK;
	}

	void ResetDemuxerPTS( );
	void SetTSChannel( long nChannel );
 
	void Stop();
	void Run();
	
	LONGLONG  ReadFileLastPTS( ); //static file return a last PTS; live file returns "1-day"
	LONGLONG  ReadFileFirstPTS( ); //static file return a last PTS; live file returns "1-day"

	CDeMuxFilter *m_pFilter;
	void	  UpdateStream(  );

	int ConvertInPlace( int ConvertInPlace, unsigned char* pData, int Byte, unsigned short uTrackIndex, unsigned char** ppData );

	REFERENCE_TIME m_rtDur;
	REFERENCE_TIME m_rtGuessDur;
	BOOL     m_bExactDur;
	REFERENCE_TIME m_rtByteLength;
	DWORD    m_dwBitRate;
	BOOL	 m_bIsSagePVRData;
	int		 m_nPVRDataType;

protected:

	DWORD		m_dwRateBound;
	DWORD		m_dwVideoRate;
	DWORD		m_dwAudioRate;

	LONGLONG	m_llFirstPTS;				//first Picture Time Stamp
	LONGLONG    m_llLastPTS;                //Last  Picture Time Stamp
	CCritSec    m_csRecentPts;
	LONGLONG    m_llRecentBytePos;
	REFERENCE_TIME m_rtRecentTime;
	DWORD       m_nPTSTrackIndex;

	DWORD		m_dwRequestedByUser;
	DWORD		m_dwRequestedTSChannel;

	DWORD		m_dwRegInitReadSize;

	BOOL		m_bRemuxOptimization;
	BOOL		m_bUseDataAlignForSync;

	DWORD       m_dwWaitingTimeForData;
	DWORD       m_dwWaitingTimeForAudio;
	UCHAR		m_uInputStreamFormat;
	DWORD		m_dwAudioCtrl;
	BOOL        m_bRebuildPMT;
	DWORD		m_dwH264FrameRate;
	int		    m_AudioStreamSelect;
	BOOL		m_bFileCrashed;

	void	  UpdateDuration( ULONGLONG* pllPTS );
	void	  UpdateLastPTS( ULONGLONG* pllFirstPTS, ULONGLONG* pllLastPTS );
	void      SetStreamPTS( int nTrackIndex, ULONGLONG* pllPTS, BOOL bUpdateDuration );
	LONGLONG  GetStreamPTS( int nTrackIndex );
	LONGLONG  GetStreamLastPTS( int nTrackIndex );

	UCHAR	  DetectInputStreamFormat( PBYTE pData, DWORD dwSize );
	LONGLONG  ReadFileFirstPTS( CParseReader *pRdr );
	LONGLONG  ReadFileLastPTS( CParseReader *pRdr );
	void	  ResetLastPTS( );

	void      ResetTracksPTS( );
	REFERENCE_TIME GetSeekPTS( ) { return m_llLastSeekPTS; }; //return last seeking PTS

	double    m_dSeekRatio;
	LONGLONG  m_llLastSeekPTS;
	
	int		  m_uEntry;  //for debug
	BOOL	  m_bStopParser;

	void*	  m_pDemuxer;

	TRACK_INF m_pTrackInf[ MAX_DEMUX_TRACK_NUM ];

	LONGLONG  m_pTrackPTS[ MAX_DEMUX_TRACK_NUM ]; //for PTS parseing

	int		  PushData( unsigned char* pbData, int nBytes );
	DWORD	  m_dwConnectedType;

	ULONGLONG m_llUpdatedPTS;
	void	  EnableFastPTSParser();	
	void	  DisableFastPTSParser();	
	void	  ZeroDemuxer( );
	void	  DemuxerReset( );
	void	  DemuxerFlush( );
	LONGLONG  ParsePTS( CParseReader *pRdr, BYTE* pBuffer, LONGLONG* llPos, DWORD dwBytes );
	LONGLONG  FindFirstPTS( CParseReader *pRdr, BYTE* pBuffer, DWORD dwBufferSize, LONGLONG* llPos, LONGLONG llMaxLen );
	LONGLONG  BackwardFindFirstPTS( CParseReader *pRdr, BYTE* pBuffer, DWORD dwBufferSize, LONGLONG* llPos );
	LONGLONG  RangeSearchPTS( CParseReader *pRdr, BYTE* pBuffer, DWORD dwBufferSize, LONGLONG* llPos, DWORD dwBytes, LONGLONG llPTSSearch );
	LONGLONG  SetPTSOffset( LONGLONG llPTSOffset );

	//int		  m_nRemainBytes, m_nExpectedBytes;
	//unsigned char m_AlignBuf[1024*3];
	DWORD	  m_nState;
	DWORD	  m_dwAudioTypeEnableMask;
	BOOL	  m_bStreamRebuild;

	static int ESDataDumper( void* pContext, void* pData, int nSize );
	static int AVInfMessageDumper( void* pContext, void* pData, int nSize );
	static int PTSDataDumper( void* pContext, void* pData, int nSize );
	static int ATSDataDumper( void* pContext, void* pData, int nSize );
	static void* MemAllocatorHook( void* pContext, void* pMemory, int nSize, int nCmd );

	HRESULT SetMpeg1VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetMpeg2VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetH264VideoMediaType( CMediaType *cmt, H264_VIDEO *pH264Video );
	HRESULT SetH264VC1VideoMediaType( CMediaType *cmt, H264_VIDEO *pH264Video );
	HRESULT SetH264VideoMediaType4Cyberlink( CMediaType *cmt, H264_VIDEO *pH264Video );
	HRESULT SetVC1VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetWVC1VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetMpeg4VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetSageDixVMediaType(CMediaType *cmt, MEDIA_VIDEO *pMediaVideo );
	HRESULT SetAC3AuidoMediaType( CMediaType *cmt, AC3_AUDIO *pAC3Audio );
	HRESULT SetTAC3AuidoMediaType( CMediaType *cmt, AC3_AUDIO *pAC3Audio );
	HRESULT SetTrueHDAuidoMediaType( CMediaType *cmt, AC3_AUDIO *pAC3Audio );
	HRESULT SetAACAuidoMediaType( CMediaType *cmt, AAC_AUDIO *pAACAudio );
	HRESULT SetAACAuidoMediaType2( CMediaType *cmt, AAC_AUDIO *pAACAudio );
	HRESULT SetDTSAuidoMediaType( CMediaType *cmt, DTS_AUDIO *pDTSAudio );
	HRESULT SetDVDDTSAuidoMediaType( CMediaType *cmt, DTS_AUDIO *pDTSAudio );
	HRESULT SetMDTSAuidoMediaType( CMediaType *cmt, DTS_AUDIO *pDTSAudio );
	HRESULT SetLPCMAuidoMediaType( CMediaType *cmt, LPCM_AUDIO *pLPCMAudio );
	HRESULT SetMpegAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio );

	HRESULT InitMPEG1Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitMPEG2Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitH264Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitVC1Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitDixVStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitDixVMediaStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitAC3Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitEAC3Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitTAC3Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitAACStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitDTSStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitMDTSStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitLPCMStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitMpegAudioStream( int nTrackIndex, BOOL bSubPin );

	HRESULT InitStream( int nTrackIndex );
	void  SetupMemAllocatorHook( CSequentialAllocator *pOutputAllocator );
	CSequentialAllocator *m_pAllocator;

	BOOL m_bHDAudioFlag;
	void DisableHDAudio( );
	void SetHDAudioFlag( );

	void ConnectedTrack( int nTrack );
	void DisconnectedTrack( int nTrack );
	void FlushTrack(  );

	void* m_Converter[MAX_DEMUX_TRACK_NUM];
	void OpenConverter( int nTrack );
	void CloseConverter( int nTrack );
	void ResetConverter( int nTrack );

	int  GetSagePVRMetaInf( CParseReader *pRdr, void *pMeta );
	int  DTSCoreHeaderCheck( unsigned char* pData, int nBytes );
	int	 CheckPVRTag( const unsigned char* pData, int nBytes );

};


#endif
