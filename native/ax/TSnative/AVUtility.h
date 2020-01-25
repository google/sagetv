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

#include "TSnative.h"

#if !defined( _TS_NATIVE_AVUTILITY_INCLUDED_ )
#define _TS_NATIVE_AVUTILITY_INCLUDED_

//data structure byte packing throughout
#ifdef WIN32
#include <pshpack1.h>
#else
//typedef _int64 long long
#ifndef _inline
 #define _inline inline
#endif
#endif


#ifdef __cplusplus
extern "C" {
#endif


#define MPEG_TIME_DIVISOR (90000)
#define MPEG_MAX_TIME ((LONGLONG)0x200000000)

#define PICTURE_START_CODE       0x00000100
#define USER_DATA_START_CODE     0x000001B2
#define SEQUENCE_HEADER_CODE     0x000001B3
#define SEQUENCE_ERROR_CODE      0x000001B4
#define EXTENSION_START_CODE     0x000001B5
#define SEQUENCE_END_CODE        0x000001B7
#define GROUP_START_CODE         0x000001B8

//MEPG4 IEC-14496
#define VISUAL_OBJECT_SEQUENCE_START_CODE     0x000001B0
#define GROUP_OF_VOP_START_CODE  0x000001B3
#define VISUAL_OBJECT_START_CODE 0x000001B5
#define VOP_START_CODE			 0x000001B5
#define VIDEO_OBJECT_START_CODE	 0x00000100

#define ISO_11172_END_CODE       0x000001B9
#define PACK_START_CODE          0x000001BA
#define SYSTEM_HEADER_START_CODE 0x000001BB
#define PADDING_START_CODE       0x000001BE
#define PSM_START_CODE			 0x000001BC

#define PACKET_START_CODE_MIN    0x000001BC
#define PACKET_START_CODE_MAX    0x000001FF

#define AUDIO_GLOBAL             0xB8
#define VIDEO_GLOBAL             0xB9 
#define RESERVED_STREAM          0xBC
#define PRIVATE_STREAM_1         0xBD
#define PADDING_STREAM           0xBE
#define PRIVATE_STREAM_2         0xBF
#define AUDIO_STREAM             0xC0
#define AUDIO_STREAM_MASK        0xE0
#define VIDEO_STREAM             0xE0
#define VIDEO_STREAM_MASK        0xF0
#define DATA_STREAM              0xF0
#define DATA_STREAM_MASK         0xF0
//#define PRIVATE_STREAM_3		 0xFD  //not private stream tag, it's the WVC1 tag

/*  MPEG-2 stuff */
#define PROGRAM_STREAM_DIRECTORY 0xFF
#define PROGRAM_STREAM_MAP       0xBC
#define ANCILLIARY_STREAM        0xF9
#define ECM_STREAM               0xF0
#define EMM_STREAM               0xF1

#define DSMCC_STREAM			 0xF2	
#define H222_E					 0xF8

#define MAGIC_DIXV_STREAM       0x74980312  //customized DixV stream
#define MAGIC_AC3_STREAM         0x80010002

#define ATSC_IDENTIFIER			0x47413934
#define AFD_IDENTIFIER			0x44544731

#define VALID_PACKET(data)      (((data) >= PACKET_START_CODE_MIN)  \
                              && ((data) <= PACKET_START_CODE_MAX))

#define VALID_SYSTEM_START_CODE(data)     \
       (VALID_PACKET(data)                \
    ||  (data) == SYSTEM_HEADER_START_CODE\
    ||  (data) == PACK_START_CODE         \
    ||  (data) == ISO_11172_END_CODE)



#define MAX_MPEG_PACKET_SIZE (65535+6)

/*  Lengths of the various structures */
#define PACK_HEADER_LENGTH 12
#define SYSTEM_HEADER_BASIC_LENGTH 12

//MPEG4 video
#define MPEG4_PICTURE_START_CODE		0x00000100
#define MPEG4_USER_DATA_START_CODE		0x000001B2
#define MPEG4_SEQUENCE_HEADER_CODE		0x000001B0
#define MPEG4_SEQUENCE_ERROR_CODE		0x000001B4
#define MPEG4_EXTENSION_START_CODE		0x000001B5
#define MPEG4_SEQUENCE_END_CODE			0x000001B7
#define MPEG4_GROUP_START_CODE			0x000001B8

//VC-1 video
#define VC1_CODE_RES0				    0x00000100
#define VC1_CODE_ENDOFSEQ				0x0000010A
#define VC1_CODE_SLICE					0x0000010B
#define VC1_CODE_FIELD					0x0000010C
#define VC1_CODE_FRAME					0x0000010D
#define VC1_CODE_ENTRYPOINT				0x0000010E
#define VC1_CODE_SEQHDR					0x0000010F

/*  Video definitions */

/*  Frame types as defined in a picture header */
#define I_Frame 1
#define D_Frame 4
#define P_Frame 2
#define B_Frame 3


#define	WAVE_FORMAT_DOLBY_AC3	0x2000	 //WAVE_FORMAT_DVM	ZQ(?)
#define	WAVE_FORMAT_MPEG		0x0050 /* MPEG Layer 1,2 */	
#define	ACM_MPEG_LAYER1				(0x0001)
#define	ACM_MPEG_LAYER2				(0x0002)
#define	ACM_MPEG_LAYER3				(0x0004)
#define	ACM_MPEG_STEREO				(0x0001)
#define	ACM_MPEG_JOINTSTEREO		(0x0002)
#define	ACM_MPEG_DUALCHANNEL		(0x0004)
#define	ACM_MPEG_SINGLECHANNEL		(0x0008)
#define	ACM_MPEG_PRIVATEBIT			(0x0001)
#define	ACM_MPEG_COPYRIGHT			(0x0002)
#define	ACM_MPEG_ORIGINALHOME		(0x0004)
#define	ACM_MPEG_PROTECTIONBIT		(0x0008)
#define	ACM_MPEG_ID_MPEG1			(0x0010)

#define  AC3_AUDIO	 5
#define  MPEG_AUDIO  3
#define  LPCM_AUDIO  4
#define  MSFT_MEDIA  6
#define  MPEG2_VIDEO 2
#define  MPEG1_VIDEO 1
#define  H264_VIDEO  10
#define  DIVX_VIDEO  11
#define  VC1_VIDEO   12
#define  DTS_AUDIO   7
#define  AAC_AUDIO	 8

#define TELTEXT_TYPE       0xC9
#define PRIVATE_DATA_TYPE  0xC3
#define MPEG4_AUDIO_STREAM 0x11
#define MPEG4_VIDEO_STREAM 0x12
//#ifndef _WINDEF_   //windows defined

//Sage private description tag
#define SAGETV_H264_INFO     0x40

typedef struct 
{
    unsigned short        wFormatTag;         /* format type */
    unsigned short        nChannels;          /* number of channels (i.e. mono, stereo...) */
    unsigned long         nSamplesPerSec;     /* sample rate */
    unsigned long         nAvgBytesPerSec;    /* for buffer estimation */
    unsigned short        nBlockAlign;        /* block size of data */
    unsigned short        wBitsPerSample;     /* number of bits per sample of mono data */
    unsigned short        cbSize;             /* the count in bytes of the size of */
				    /* extra information (after cbSize) */
} _WAVEFORMATEX;
//#endif

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

typedef struct 
{ 
  long left; 
  long top; 
  long right; 
  long bottom; 
} _RECT;


/* AC3Wav Descriptor Data*/	
typedef	struct	
{
	_WAVEFORMATEX   wfx;	
	unsigned char  BigEndian;
	unsigned char  bsid;
	unsigned char  lfeon;
	unsigned char  copyrightb;
	unsigned char  nAuxBitsCode;  /*  Aux bits per frame */	
} TS_AC3WAV;

typedef	struct	
{
	_WAVEFORMATEX   wfx;	
} DTSWAV;

typedef struct   {
    char      majortype[16];  //GUID
    char      subtype[16];    //GUID
    bool      bFixedSizeSamples;
    bool      bTemporalCompression;
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
} _MEDIA;

typedef	struct	
{
	_WAVEFORMATEX	wfx;
	unsigned short	fwHeadLayer;
	unsigned long	dwHeadBitrate;
	unsigned short	fwHeadMode;	
	unsigned short	fwHeadModeExt;
	unsigned short	wHeadEmphasis;
	unsigned short	fwHeadFlags;
	unsigned long	dwPTSLow;
	unsigned long	dwPTSHigh;
} TS_MPEGWAV;

typedef struct 
{
	_WAVEFORMATEX wfx;
} LPCM_WAV;

typedef	struct {
	short		   Profile;			   //  Profile - MPEG2 only	
	short		   Level;			   //  Level - MPEG2 only
	long		   Width;			   //  Native Width	in pixels
	long		   Height;			   //  Native Height in	pixels
	long		   vbv;				   //  vbv
	short		   progressive;		   //  progressive flag	
	long		   arInfo;			   //  aspect ratio	code from stream
	REFERENCE_TIME tPictureTime;	   //  Time	per	picture	in 100ns units
	float		   PictureRate;		   //  In frames	per	second
	long		   TimePerFrame;	   //  Time per picture in MPEG units
	//unsigned char  FrameRateCode;     //  4 bits frame rate code
	unsigned short FrameRateNomi;
	unsigned short FrameRateDeno;
	long		   BitRate;			   //  Bits	per	second
	long		   lXPelsPerMeter;	   //  Pel aspect ratio	
	long		   lYPelsPerMeter;	   //  Pel aspect ratio	
	unsigned long  StartTimeCode;	   //  First GOP time code (or -1)
	int		   ActualHeaderLen;	   //  Length of valid bytes in	raw	seq	hdr	
	unsigned char		   RawHeader[150];	   //  The real	sequence header	
} SEQHDR;

typedef struct {

	unsigned long  sps_id;
	unsigned short profile, level;
	unsigned short constraint_set0;
	unsigned short constraint_set1;
	unsigned short constraint_set2;
	unsigned short width, height;
	unsigned short progressive; //PAFF
	unsigned long  cbr;
	unsigned short frame_rate;
	int guessH264; //-100, 100, 0: is unsure, 100 sure is H264 

} H264_PARAM;

typedef struct {
	int  state;   //0: unknow; 1:start, 2:need more data, 3:done;
	int  format;  //1: ADTS, 2:LOAS (DVB)
	char version; //2:mpeg2; 4:mepg4
	union  {
		unsigned char  atds_header[70];
		unsigned char  latm_header[69];
	} u;
	unsigned short profile;
	unsigned short object_type;
	int  channel_num;
	int  sample_freq;
	int  bitrate;
	short frame_length;
	unsigned long total_frame_bytes;
	int  frames;
	int  expect_bytes;
} AAC_PARAM;

typedef	struct {
	bool		   ExtensionPresent;
	bool		   DisplayExtensionPresent;	
	bool		   ScalableExtensionPresent;
	SEQHDR		   SeqHdr;
} MPEG2_SEQHDR;	

#define  MPEG2_TYPE   2
#define  MPEG1_TYPE   1

typedef	struct	{
	unsigned char  StreamId;
	unsigned short HeaderLen;
	unsigned long  PacketLen;
	bool		   HasPts;
	bool		   HasDts;
	LONGLONG	   llPts;
	LONGLONG	   llDts;
	LONGLONG	   PTSOffset;  // integral promotion results in LONGLONG
	LONGLONG	   DTSOffset;  // integral promotion results in LONGLONG
	char		   type;	//0:unknowm, 1:mpeg1, 2:mpeg2
	LONGLONG       SCR;
	bool           bGroupStart;
} PES_INFO;


typedef	struct {

	//video	 data
	PES_INFO	 VideoPES;	
	MPEG2_SEQHDR Mpeg2Hdr;
	H264_PARAM   H264Param;
	_MEDIA		 MsftMedia;
	short		 VideoType;	 //1: MPEG-1 2:MPEG-2; MEIA_TYPE; 0x1b:H.264;

	//audio	data
	PES_INFO	AudioPES;
	TS_AC3WAV	AC3Wav;	
	LPCM_WAV    LPCMWav;
	TS_MPEGWAV	MpegWav;
	AAC_PARAM   AACParam;
	DTSWAV		DTSWav;	

	short		AudioType;	  //1:MPEG1; 2:MPEG2; 3:MPEG3; 4:LPCM; 5:AC3_AUDIO; 8:AAC_AUDIO

	unsigned short MagicLen;  //Magaic data length in PES packet (skip data)

	unsigned char  SubId;      //for DVD sub picture or sub voice
	unsigned char  PrivateHeader[4];  //DVD, AC3 private header

	unsigned short PacketBufLen;          //buffer for video header that cross packet boundary 
	unsigned char  PacketBuf[400];
	
} AV_CONTEXT;

#define MAX_CC_DATA_NUM		16
#define MAX_BAR_DATA_NUM	4


typedef struct {
	unsigned short top;
	unsigned short bottom;
	unsigned short left;
	unsigned short right;
} BAR_DATA;

typedef	struct {

	unsigned short DataType;  //present CC data 0x01, bar data 0x02, afd format 04, extral data 0x08
	unsigned short CC_data_num;
	unsigned char* CC_data_ptr[MAX_CC_DATA_NUM];
	unsigned short CC_data_byte[MAX_CC_DATA_NUM];

	unsigned short bar_data_num;
	BAR_DATA BarData[MAX_BAR_DATA_NUM];

	unsigned short afd_format;
} MPEG_USER_DATA;

typedef struct {
	unsigned long outbfr;  //save uncommited to buffer bits
	int outcnt;			   //bits in outbfr
	int	bytecnt;	       //in byte buffer 	
	unsigned char* buf;
	unsigned char* buf_start;
	int	buf_size;
} BITS_T;

#define TS_PACKET_LENGTH  188

#if defined(__APPLE__)
 #include <libkern/OSByteOrder.h>
 #define DWORD_SWAP(x) OSSwapBigToHostInt32(x)
#else
 unsigned long DWORD_SWAP(unsigned long x);
#endif // __APPLE__

//ZQ I define MSFT MEDIA TYPE Stream Type 0xf1.
//ZQ I H.264 video Stream Type 0x1b in SKY satellite UK; SCTE video stream 0x80 
#define SMPTE_VC1 0xfd
#define UNKOWN_STREAM_TYPE    0xff
#define IsTsHeader(  pStart	)	   ( (*pStart ==  SYNC) )
#define IsVideoType( StreamType	)  ( StreamType	== 2 ||	StreamType == 1	|| StreamType == 0xf1 || StreamType == 0x1b || StreamType == 0xea || StreamType == 0x80 || StreamType == 0x10  ) 	
#define IsAudioType( StreamType	)  ( StreamType	== 3 ||	StreamType == 4	|| StreamType == 6 || StreamType == 0x81 || StreamType == 0x82 ||  StreamType == 0x11 || StreamType == 0x0f ) 	
/*  Types of stream */
#define IsVideoStreamId( StreamId )  (  (StreamId & 0xF0) == 0xE0 )
#define IsAudioStreamId( StreamId)   (  (StreamId & 0xE0) == 0xC0 )
#define IsVC1StreamId( StreamId)   ( StreamId == 0xfd  )
#define IsPrivateStreamId( StreamId) (  (StreamId == PRIVATE_STREAM_1) || (StreamId == PRIVATE_STREAM_2) ) //(StreamId == PRIVATE_STREAM_3))

#define VC1_STREAM_TYPE     0xea
#define MPEG4_STREAM_TYPE   0x10
#define H264_STREAM_TYPE    0x1b
#define AAC_STREAM_TYPE     0x0f
#define AAC_HE_STREAM_TYPE  0x11
#define PCR_STREAM_TYPE     0xFE

bool CheckTSContinuity( unsigned char TSContinuity, unsigned char* pContinuity, bool StartFlag );

//_inline bool IsTsHeader( const unsigned	char* pStart );	
//_inline bool IsVideoType(	unsigned char StreamType);
//_inline bool IsAudioType(	unsigned char StreamType);

bool SearchMPEG2StartCode( const unsigned char* pStart, int Bytes, unsigned	long StartCode,	const unsigned char** ppSeqStart );
bool IsMPEG2StartCode( const unsigned char* pStart, unsigned long StartCode );
unsigned int SearFrameType(  char* pData, int Size ); //1 I-Frame, 2 P-Frame, 3 B-Frame

int  UnpackPESHeader( const unsigned char * pbData, int Bytes, PES_INFO *pPESInfo );
unsigned char GetPrivatePESSubID( const unsigned char* pbData, int Bytes, PES_INFO *pPESInfo );
int  DecodeAVHeader( AV_CONTEXT* av, PES_INFO* pPESInfo, unsigned char StreamType, const unsigned char* pbData, int Bytes );
int  DecodeUserHeader( MPEG_USER_DATA* ud, PES_INFO* pPESInfo, unsigned char StreamType, const unsigned char * pData, int Size );

int  SkipPrivateData( AV_CONTEXT* av, PES_INFO* pPESInfo, const unsigned char * pData, int Size );
int  CheckProprietaryStream( const char* pData, int Size );

void UpdatePESPTS( unsigned char* pbData, int Bytes, PES_INFO *pPESInfo, LONGLONG* llPTS, LONGLONG* llDTS );


void InitOutBits( BITS_T *bits, unsigned char* buf, int size );
void PutBits( BITS_T *bits, unsigned long val, int n );
int	 CloseOutBits( BITS_T *bits );
void AlignOutBits( BITS_T *bits );
long BitOutCount( BITS_T *bits );
long BytesOutCount( BITS_T *bits );
int  FlushOutBits( BITS_T *bits, char* out_buf, int size );

float Mepg2FrameRate( unsigned char code );
int   Mepg2FrameRateDeno( unsigned char code );
int   Mepg2FrameRateNomi( unsigned char code );
LONGLONG GetPCRTimePerFrame( AV_CONTEXT *av );


float Mepg2AspectRatio( unsigned char code, long width, long height );
int   Mepg2AspectRatioDeno( unsigned char code, long width, long height );
int   Mepg2AspectRatioNomi( unsigned char code, long width, long height );
float Mepg1AspectRatio( unsigned char code );

int  CheckMPEGPacket( const unsigned char* pStart, int Bytes );
bool CheckTSPacket( const unsigned char* pStart, int Bytes );

int  GuessAuidoType( char* descriptor );
bool IsTeletextType( char* descriptor );
bool IsSubtitleType( char* descriptor );
int  GetAuidoType( char* descriptor, int len  );
bool IsSubtitleDescriptor( char* descriptor, int len );
bool IsTeletextTypeDescriptor( char* descriptor, int len );
bool IsPrivateData( char* desc, int len );
unsigned long GetLauguage( char* descriptor );
unsigned short GetISO639LanguageType( char* descriptor );


int   FillSageDesc( unsigned char* ptr, int bytes, unsigned char tag, char* data, int len );
char* ParserDescTag( const unsigned char* ptr, int bytes, unsigned char tag );
char* PTS2HexString( LONGLONG pts, char* string, int size );
char* PTS2TimeString( char *buf, int len, LONGLONG* pllPTS ); //in format hs:mm:ss.nnn
char* PTS2TimeString2( char *buf, int len, LONGLONG* pllPTS ); //in format hs:mm:ss,nnn

void skip_bits( int* bits_offset, int* total_bits, int bits );
unsigned int read_ue( const unsigned char* buffer, int* bits_offset, int* total_bits  );
int read_se( const unsigned char* buffer, int* bits_offset, int* total_bits  );
int read_u( const unsigned char* buffer, int* bits_offset, int* total_bits, int bits  );

#ifdef WIN32
#include <poppack.h>
#endif

#ifdef __cplusplus
}
#endif

#endif


