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

#include "../../../third_party/Microsoft/StrmDeMux/alloc.h"
typedef _int64 int64_t;

#define MAX_DEMUX_TRACK_NUM  10
#define  UNITS			   (10000000)
#define  MPEG_TIME_DIVISOR (90000)
#define  UNITS_PTS		    MPEG_TIME_DIVISOR/UNITS   
#define  PTS_UNITS          UNITS/MPEG_TIME_DIVISOR   


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
#define AACH_ENABLE 0x40
#define LPCM_ENABLE 0x80
#define WMA_ENABLE  0x100
#define PCM_ENABLE  0x200
#define VRBS_ENABLE 0x400
#define FLAC_ENABLE 0x800
#define COOK_ENABLE 0x1000
#define MPEG_ENABLE 0x8000

#define VIDEO_DATA		1
#define AUDIO_DATA		2
#define SUBPICTURE_DATA 3

#define ACM_MPEG_LAYER1             (0x0001)
#define ACM_MPEG_LAYER2             (0x0002)
#define ACM_MPEG_LAYER3             (0x0004)
#define ACM_MPEG_STEREO             (0x0001)
#define ACM_MPEG_JOINTSTEREO        (0x0002)
#define ACM_MPEG_DUALCHANNEL        (0x0004)
#define ACM_MPEG_SINGLECHANNEL      (0x0008)
#define ACM_MPEG_PRIVATEBIT         (0x0001)
#define ACM_MPEG_COPYRIGHT          (0x0002)
#define ACM_MPEG_ORIGINALHOME       (0x0004)
#define ACM_MPEG_PROTECTIONBIT      (0x0008)
#define ACM_MPEG_ID_MPEG1           (0x0010)

#define FIX_H264_FLAG	0x01


typedef struct MPEG_VIDEO
{
	unsigned char  extension_present;
	short		   profile;			   //  profile - MPEG2 only	
	short		   level;			   //  level - MPEG2 only
	short		   ver_id;			   //  MPEG4 only
	long		   width;			   //  Native width	in pixels
	long		   height;			   //  Native height in	pixels
	long		   vbv;				   //  vbv
	long		   progressive;		   //  progressive flag	
	long		   ar_info;			   //  aspect ratio	code from stream
	long		   frame_num;	   //  Time	per	picture	in 100ns units
	long		   frame_den;	   //  Time	per	picture	in 100ns units
	long		   time_per_frame;	   //  Time per picture in MPEG units
	//unsigned char  FrameRateCode;     //  4 bits frame rate code
	unsigned short frame_rate_nomi;
	unsigned short frame_rate_deno;
	long		   bit_rate;			   //  Bits	per	second
	short		   actual_header_length;	   //  Length of valid bytes in	raw	seq	hdr	
	unsigned char  raw_header[150];	   //  The real	sequence header	

} MPEG_VIDEO;

typedef struct H264_VIDEO
{
	unsigned long  sps_id;
	unsigned short profile, level;
	unsigned short constraint_set0;
	unsigned short constraint_set1;
	unsigned short constraint_set2;
	unsigned short width, height;
	unsigned short progressive; //PAFF
	unsigned long  cbr;
	unsigned short frame_rate;
	unsigned long  frame_rate_nomi;
	unsigned long  frame_rate_deno;
	unsigned short ar_nomi;
	unsigned short ar_deno;
	unsigned char  sps[256];
	unsigned short sps_length;
	int sub_format;  //1: h264; 2:vc1
	unsigned short code_size;
} H264_VIDEO;

typedef struct AC3_AUDIO
{
	unsigned short channels;
	unsigned long  samples_per_sec;
	unsigned long  avgbytes_per_sec;
	unsigned short block_align;
} AC3_AUDIO;

typedef struct EAC3_AUDIO
{
	unsigned short channels;
	unsigned long  samples_per_sec;
	unsigned long  avgbytes_per_sec;
	unsigned short block_align;
} EAC3_AUDIO;

typedef struct LPCM_AUDIO
{
	unsigned short channels;
	unsigned long  samples_per_sec;
	unsigned long  avgbytes_per_sec;
	unsigned short block_align;
	unsigned short bits_per_sample;

} LPCM_AUDIO;

typedef struct PCM_AUDIO
{
	unsigned short channels;
	unsigned long  samples_per_sec;
	unsigned long  avgbytes_per_sec;
	unsigned long  bits_rate;
	unsigned short block_align;
	unsigned short bits_per_sample;

} PCM_AUDIO;

typedef struct MPEG_AUDIO
{
 	unsigned short  channels;
	unsigned long   samples_per_sec;
	unsigned long   avgbytes_per_sec;
	unsigned short  bits_per_sample;
	unsigned short  block_align;

	unsigned short	head_layer;
	unsigned long	head_bitrate;
	unsigned short	head_mode;	
	unsigned short	head_mode_ext;
	unsigned short	head_emphasis;
	unsigned short	head_flags;
	unsigned char	extra[256];
	unsigned short  extra_bytes;
} MPEG_AUDIO;

typedef struct AAC_AUDIO
{
	unsigned short channels;
	unsigned long  samples_per_sec;
	unsigned long  avgbytes_per_sec;
	unsigned short block_align;
	unsigned short bits_per_sample;

	int  state;   //0: unknow; 1:start, 2:need more data, 3:done;
	int  format;  //1: ADTS, 2:LOAS (DVB)
	char version; //2:mpeg2; 4:mepg4
	int header_bytes;
	union  {
		unsigned char  atds_header[70];
		unsigned char  latm_header[69];
	} u;
	unsigned short profile;
	unsigned short object_type;
	int  bitrate;
	short frame_length;
	unsigned long total_frame_bytes;
	int  frames;
	int  expect_bytes;

} AAC_AUDIO;

typedef struct DTS_AUDIO
{
	unsigned short channels;
	unsigned long  samples_per_sec;
	unsigned long  avgbytes_per_sec;
	unsigned short block_align;
	unsigned short bits_per_sample;

} DTS_AUDIO;

typedef struct 
{ 
  long left; 
  long top; 
  long right; 
  long bottom; 
} _RECT;

typedef struct
{
  unsigned long  biSize; 
  long    biWidth; 
  long    biHeight; 
  short   biPlanes; 
  short   biBitCount; 
  unsigned long  biCompression; 
  unsigned long  biSizeImage; 
  long   biXPelsPerMeter; 
  long   biYPelsPerMeter; 
  unsigned long  biClrUsed; 
  unsigned long  biClrImportant; 
} _BITMAPINFOHEADER;

typedef struct   {
    char      majortype[16];  //GUID
    char      subtype[16];    //GUID
    char      bFixedSizeSamples;
    char      bTemporalCompression;
    unsigned long  lSampleSize;
    char      formattype[16];   //GUID
	void*	  *pUnk;
    unsigned long  cbFormat;
	void*	  *pBFoarmt;
} _MEDIA_DATA;

typedef struct  {
    _RECT               rcSource;
    _RECT               rcTarget;
    unsigned long       dwBitRate;
    unsigned long       dwBitErrorRate;
    LONGLONG		    AvgTimePerFrame;
    _BITMAPINFOHEADER   bmiHeader;
	unsigned char		bmiExt[4096];
}  _VIDEOINFOHEADER;

typedef struct {
	_MEDIA_DATA		 Media;
	_VIDEOINFOHEADER Video;
} MEDIA_VIDEO;

typedef struct {
	unsigned short	code_type;
	unsigned long	language;
	unsigned char	extra[256];
	unsigned short  extra_bytes;
} SUBTITLE;

struct H264_VIDEO_I;
typedef struct AV
{
	unsigned short channel_index;	//index 
	unsigned char  content_type;	//1:video; 2:audio; 3:subpicture
	unsigned char  stream_index;	//keep stream in an order [main-video, sub-video1 sub-video2...][main-audio, sub-audio1, sub-audio2...][]
	unsigned long  format_fourcc;
	union 
	{
		union 
		{
			MPEG_VIDEO	mpeg_video;
			H264_VIDEO	h264;
		} v;
		union 
		{
			AC3_AUDIO	 ac3;
			EAC3_AUDIO	 eac3;
			PCM_AUDIO	 pcm;
			LPCM_AUDIO	 lpcm;
			MPEG_AUDIO	 mpeg_audio;
			AAC_AUDIO	 aac;
			DTS_AUDIO	 dts;
		} a;
		union
		{
			SUBTITLE	subt;
		} s;

	} d;
	unsigned char* private_data;
	unsigned short private_data_bytes;
} AV;

////////////////////////////////////////////////////////////////
#define SAGE_FOURCC( a ) (*((unsigned long*)( a )))
//#define SAGE_FOURCC(a)      ((a[0]<<24)|(a[1]<<16)|(a[2]<<8)|a[3])
char* _sagetv_fourcc_( unsigned long lFourCC, char* pFourCC );
////////////////////////////////////////////////////////////////////

typedef struct 
{
	bool		   stream_ready;   //when codec information is ready, it's set
	unsigned char  stream_id;      //mapping into pushreader video/audio channel
	unsigned char  stream_type;     //1 video; 2 audio;
	unsigned char  pin_index;    
	LONGLONG	   firstPTS;	   //first Picture Time Stamp
	LONGLONG	   lastPTS;		   //last Picture Time Stamp
	LONGLONG	   newPTS;         //new Picture Time Stamp, lastPTS - refernece PTS
	unsigned long  data_frames;    //send data frames
	unsigned long  language;
	AV			   av;
	CMediaType     *pmt;
	unsigned short pushreader_fix_flag;
} TRACK_INF;

class CParseReader;
class CSDeMuxFilter;

class CPushReader
{
	friend class CSDeMuxFilter;
public:
    CPushReader(CSDeMuxFilter *pFilter) :
	    m_pFilter( pFilter ),
		m_nPTSTrackIndex(0),
		m_dwRateBound(0),
		m_dwVideoRate(0),
		m_dwAudioRate(0),
		m_rtDur(0),
		m_dwBitRate(1),
		m_llFirstPTS(0),
		m_llLastPTS(0),
		m_dwAudioCtrl(0),
		m_dwH264FrameRate(0),
		m_bStopParser( FALSE ),
		m_llLastSeekPTS( 0 ),
		m_AudioStreamSelect(-1),
		m_dwAudioTypeEnableMask( -1 ),
		m_dwSubPcitureEnableMask( -1 ),
		m_bDisableDecodeAudio( 0 ),
		m_dwDecodeAudioType( 0 ),
		m_pPushReader( NULL ),
		m_nMainVideo(-1),
		m_nMainAudio(-1),
		m_nMainSpu(-1),
		m_nConnectedVideoTrack(-1),
		m_nConnectedAudioTrack(-1),
		m_nConnectedSubPictureTrack(-1),
		m_nTrackNum(0),
		m_nState( 0 )
    {
		m_uInputStreamFormat = 0;

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

		m_dwLanguage = SAGE_FOURCC( "eng" );
		memset( m_pTrackInf, 0, sizeof(m_pTrackInf) );

    }

	~CPushReader();
    HRESULT  Init( );
    /*  Call this to pass new stream data :

        pbData        - pointer to data
        lData         - length of data
        plProcessed   - Amount of data consumed
    */
	void    ConnectSourceReader(  );
	void    DisconnectSourceReader( );

	LONGLONG Seek( LONGLONG rtSeekPTS, DWORD dwBlockSize  );
 
	/*  Call this to get the estimated duration of the stream */
	REFERENCE_TIME GetDuration( );

	/* Gives the last timestamp that was passed through the demux */
	REFERENCE_TIME GetLastTimestamp( );
 
	void	PickupAudioStream( int nAudioTrack );
	void    PickupVideoStream( int nVideoTrack );
	void	PickupSubPictureStream( int nVideoTrack );
	void	DropStream( int nTrack );

	HRESULT SetRemuxOptimization(BOOL bRemuxOptimization)
	{
		return S_OK;
	}

	void Stop();
	void Run();
	
	LONGLONG  ReadFileLastPTS( ); //static file return a last PTS; live file returns "1-day"
	LONGLONG  ReadFileFirstPTS( ); //static file return a last PTS; live file returns "1-day"

	CSDeMuxFilter *m_pFilter;

	REFERENCE_TIME m_rtDur;
	BOOL     m_bExactDur;
	REFERENCE_TIME m_rtByteLength;
	DWORD    m_dwBitRate;


protected:

	int			m_nState;
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

	DWORD       m_dwWaitingTimeForData;
	DWORD       m_dwWaitingTimeForAudio;
	UCHAR		m_uInputStreamFormat;
	DWORD		m_dwAudioCtrl;
	DWORD		m_dwH264FrameRate;
	int		    m_AudioStreamSelect;
	BOOL	    m_bUseDataAlignForSync;
	DWORD		m_dwAudioTypeEnableMask;
	DWORD		m_dwSubPcitureEnableMask;
	int			m_bDisableDecodeAudio;
	DWORD		m_dwDecodeAudioType;

	int		   EOFOfPusher( );
	void       SetStreamPTS( int nTrackIndex, ULONGLONG* pllPTS, BOOL bUpdateDuration );
	LONGLONG   GetStreamPTS( int nTrackIndex );
	//LONGLONG   GetStreamLastPTS( int nTrackIndex );

	UCHAR	   DetectInputStreamFormat( PBYTE pData, DWORD dwSize );
	LONGLONG   ReadFileFirstPTS( CParseReader *pRdr );
	LONGLONG   ReadFileLastPTS( CParseReader *pRdr );

	void       ResetTracksPTS( );
	REFERENCE_TIME GetSeekPTS( ) { return m_llLastSeekPTS; }; //return last seeking PTS
	void	   SeekPTS( LONGLONG llPts );



	LONGLONG  m_llLastSeekPTS;
	
	BOOL	  m_bStopParser;

	void*	  m_pPushReader;
	DWORD	  m_dwLanguage;
	int		  m_nMainVideo;
	int		  m_nMainAudio;
	int		  m_nMainSpu;
	int		  m_nTrackNum;
	int		  m_nConnectedVideoTrack;
	int		  m_nConnectedAudioTrack;
	int		  m_nConnectedSubPictureTrack;
	TRACK_INF m_pTrackInf[ MAX_DEMUX_TRACK_NUM ];
	void	  FreeTrackInf();

	LONGLONG  m_pTrackPTS[ MAX_DEMUX_TRACK_NUM ]; //for PTS parseing

	HRESULT SetMpeg1VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetMpeg2VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetH264VideoMediaType( CMediaType *cmt, H264_VIDEO *pH264Video );
	HRESULT SetH264VC1VideoMediaType( CMediaType *cmt, H264_VIDEO *pH264Video );
	HRESULT SetH264VideoMediaType4Cyberlink( CMediaType *cmt, H264_VIDEO *pH264Video );
	HRESULT SetVC1VideoMediaType( CMediaType *cmt,  H264_VIDEO *pH264Video );
	HRESULT SetWVC1VideoMediaType( CMediaType *cmt,  H264_VIDEO *pH264Video );
	HRESULT SetFLV1VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetVP6FVideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetWMV9VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetWMV8VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetWMV7VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetMpeg4VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetRealVideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	
	HRESULT SetXVIDMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo );
	HRESULT SetSageDixVMediaType(CMediaType *cmt, MEDIA_VIDEO *pMediaVideo );
	HRESULT SetAC3AuidoMediaType( CMediaType *cmt, AC3_AUDIO *pAC3Audio );
	HRESULT SetTAC3AuidoMediaType( CMediaType *cmt, AC3_AUDIO *pAC3Audio );
	HRESULT SetAACAuidoMediaType( CMediaType *cmt, AAC_AUDIO *pAACAudio );
	HRESULT SetAACAuidoMediaType2( CMediaType *cmt, AAC_AUDIO *pAACAudio );
	HRESULT SetDTSAuidoMediaType( CMediaType *cmt, DTS_AUDIO *pDTSAudio );
	HRESULT SetMDTSAuidoMediaType( CMediaType *cmt, DTS_AUDIO *pDTSAudio );
	HRESULT SetLPCMAuidoMediaType( CMediaType *cmt, LPCM_AUDIO *pLPCMAudio );
	HRESULT SetPCMAuidoMediaType( CMediaType *cmt,  PCM_AUDIO *pLPCMAudio );
	HRESULT SetMpegAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio );
	HRESULT SetMp3AuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio );
	HRESULT SetMp3AuidoMediaType2( CMediaType *cmt, MPEG_AUDIO *pMpegAudio );
	HRESULT SetWMAAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio );
	HRESULT SetFLACSAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio );
	HRESULT SetAFLACSAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio );
	HRESULT SetVORBISAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio, 
		                       unsigned char* pExtraData, int nExtraDataSize );
	HRESULT SetCookAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio );
	HRESULT SetSubTitleMediaType( CMediaType *cmt, SUBTITLE *pSubtitle );

	HRESULT InitMPEG1Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitMPEG2Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitH264Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitVC1Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitDixVStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitFLV1Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitVP6FVStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitRVStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitXVIDStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitWMV9Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitWMV8Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitWMV7Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitDixVMediaStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitAC3Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitEAC3Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitTAC3Stream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitAACStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitDTSStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitMDTSStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitWMAStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitPCMStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitLPCMStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitMpegAudioStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitFLACStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitAFLACStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitVORBISStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitCookStream( int nTrackIndex, BOOL bSubPin );
	HRESULT InitSubPictureStream( int nTrackIndex, BOOL bSubPin );

	HRESULT InitStream( int nTrackIndex );
	void	UpdateStream(  );

	int		SelectMainVideo( );
	int		SelectMainAudio( );
	int		SelectMainSubpicture( );

	void SetAcceptMediaType( int nTrackIndex, CMediaType* pmt );
	BOOL IsMP3Audio( int nTrackIndex );

	int RePackData( int nTrack, unsigned char* pBuffer, int Bytes, unsigned char** pPacket, int* pGroupGlag );
	int ProcessVideo( unsigned char* pBuffer, int nBufferSize, LONGLONG *pllPts, int* pHasPts );
	int ProcessAudio( unsigned char* pBuffer, int nBufferSize, LONGLONG *pllPts, int* pHasPts );
	int ProcessSubPicture( unsigned char* pBuffer, int nBufferSize, LONGLONG *pllPts, int* pHasPts );
    int	Process( unsigned char* pBuffer, int nBufferSize, LONGLONG *pllPts, int* pHasPts, int* pIndex );

	int GetVideoPictureSize( int nTrackIndex );
	int GetAudioChannel( int nTrackIndex );
	unsigned long GetTrackFormat( int nTrackIndex );

	//int H264ToVC1Pack( unsigned char* src, unsigned char* tar, int bytes );
	int PackeH264( unsigned char* pBuffer, int Bytes );
	int MakeVC1ExtraData( unsigned char* src, unsigned char* tar, int bytes );
	int GetWVC1ExtraData( unsigned char *pData, int Length, unsigned char **WVC1ExtraData );
	int GetH264Format( unsigned char *pData, int Size, H264_VIDEO_I *pH264Video );

	int IsVC1Header( unsigned char *pData );

private:


};


#endif
