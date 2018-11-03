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

#pragma warning(disable : 4996)

#ifndef _WIN64
  #define _USE_32BIT_TIME_T
#endif

#include <streams.h>

#include <bdaiface.h>
#include <bdatypes.h>

#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <io.h>
#include <fcntl.h>
#include <share.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <string.h>
#include <dvdmedia.h>
#include "H264Format.h"
#include "Mpeg4Format.h"
#include "CPushReader.h"

#include "SDeMuxInPin.h"
#include "SDeMuxOutPin.h"
#include "SDeMuxFilter.h"
#include "../../../third_party/Microsoft/FileSource/strconv.h"


////////////////////////////////////////////////////////////////////
#define LIBAVFORMAT_VERSION_MAJOR  52

struct URLContext {
#if LIBAVFORMAT_VERSION_MAJOR >= 53
    const AVClass *av_class; ///< information for av_log(). Set by url_open().
#endif
    struct URLProtocol *prot;
    int flags;
    int is_streamed;  /**< true if streamed (no seek possible), default = false */
    int max_packet_size;  /**< if non zero, the stream is packetized with this max packet size */
    void *priv_data;
    char *filename; /**< specified filename */
};

typedef struct URLContext URLContext;

typedef struct URLProtocol {
    const char *name;
    int (*url_open)(URLContext *h, const char *filename, int flags);
    int (*url_read)(URLContext *h, unsigned char *buf, int size);
    int (*url_write)(URLContext *h, unsigned char *buf, int size);
    int64_t (*url_seek)(URLContext *h, int64_t pos, int whence);
    int (*url_close)(URLContext *h);
    struct URLProtocol *next;
    int (*url_read_pause)(URLContext *h, int pause);
    int64_t (*url_read_seek)(URLContext *h, int stream_index,
                             int64_t timestamp, int flags);
    int (*url_get_file_handle)(URLContext *h);
} URLProtocol;

typedef struct URLPath
{
	char  filename[32];
	unsigned long tag_fourcc;
	void* context_ptr;
} URLPath;

#include "../../../third_party/SageTV-LGPL/PushReader/pushreader.h"

////////////////////////////////////////////////////////////////////

#define	WAVE_FORMAT_DOLBY_AC3	0x2000	 //WAVE_FORMAT_DVM	ZQ(?)
#define	WAVE_FORMAT_DTS			0x2001	 //WAVE_FORMAT_DVM	ZQ(?)
#define	WAVE_FORMAT_MPEG		0x0050   /* MPEG Layer 1,2 */	
#define	WAVE_FORMAT_MPEG3		0x0055   /* MPEG Layer 3 */	
#define	WAVE_FORMAT_MPEG_ADTS_AAC 0x1600
#define WAVE_FORMAT_MPEG_LOAS	  0x1602

static const GUID  CLSID_MpegH264VC1 =
{ 0x31435641, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71} };

static const GUID  CLSID_DTS_AUDIO=
{ 0xAD08B891, 0xEA90, 0x40DC, {0x93, 0xEE, 0x73, 0xC7, 0xC4, 0x61, 0x92, 0xE3} };

static const GUID  CLSID_DTS_MASTER_AUDIO=
{ 0x00002001, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71} };

static const GUID  MEDIASUBTYPE_DOLBY_DDPLUS=
{ 0xa7fb87af, 0x2d02, 0x42fb, {0xa4, 0xd4, 0x5, 0xcd, 0x93, 0x84, 0x3b, 0xdd } };

static const GUID  MEDIASUBTYPE_DTS_HD = 
{ 0xa2e58eb7, 0xfa9, 0x48bb,  {0xa4, 0xc, 0xfa, 0xe, 0x15, 0x6d, 0x6, 0x45 } };

static const GUID  MEDIASUBTYPE_DOLBY_TRUEHD=
{ 0xeb27cec4, 0x163e, 0x4ca3, {0x8b, 0x74, 0x8e, 0x25, 0xf9, 0x1b, 0x51, 0x7e } };

static const GUID  FORMAT_DIVX_VIDEO =
{ 0x05589f80, 0xC356, 0x11CE, {0xbf, 0x01, 0x00, 0xaa, 0x00, 0x55, 0x59, 0x5a} };

static const GUID  CLSID_DIVX=
{ 0x58564944, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID  FORMAT_MPEG4_VIDEO =
{ 0x05589f80, 0xc356, 0x11ce, {0xbf, 0x01, 0x00, 0xaa, 0x00, 0x55, 0x59, 0x5a } };

static const GUID  CLSID_MP4V =
{ 0x6d703476, 0x0000, 0x0010, {0xb5, 0x37, 0x98, 0x08, 0x00, 0x00, 0x00, 0x00} };

static const GUID  CLSID_MpegVC1 =
{ 0x629B40AD, 0xAD74, 0x4EF4, {0xA9, 0x85, 0xF0, 0xC8, 0xD9, 0x2E, 0x5E, 0xCA } };

static const GUID  CLSID_MpegWVC1 =
{ 0x31435657, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID CLSID_MpegH264 =
{ 0x8D2D71CB, 0x243F, 0x45E3, {0xB2, 0xD8, 0x5F, 0xD7, 0x96, 0x7E, 0xC0, 0x9B } };

static const GUID CLSID_MpegXVID =
{ 0x44495658, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID CLSID_MpegDixV =
{ 0x58564944, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID CLSID_MpegDX50 =
{ 0x30355844, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID CLSID_MpegWMV1 =
{ 0x31564D57, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID CLSID_MpegWMV2 =
{ 0x32564D57, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID CLSID_MpegWMV3 =
{ 0x33564D57, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID MEDIASUBTYP_MpegWMA =
{ 0x00000161, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID MEDIASUBTYP_MpegWMAPRO =
{ 0x00000162, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID MEDIASUBTYP_VORBIS =
{ 0x8D2FD10B, 0x5841, 0x4A6B, {0x89, 0x05, 0x58, 0x8F, 0xEC, 0x1A, 0xDE, 0xD9 } };

static const GUID MEDIASUBTYPE_AAC =
{ 0x000000ff, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_LATM_AAC =
{ 0x000001ff, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_MPEG_ADTS_AAC = 
{ 0x00001600, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_MPEG_LATM_AAC = 
{ 0x00001602, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_WVC1 = 
{ 0x31435657, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_FLV1 = 
{ 0x31564C46, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_COOK = 
{ 0x4b4f4f43, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_FLV4 = 
{ 0x34564C46, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_RV40 = 
{ 0x30345652, 0x0000, 0x0010, { 0x80, 0x00, 0x00,  0xaa, 0x00, 0x38, 0x9b, 0x71 } };

static const GUID MEDIASUBTYPE_MPEG3AudioPayload =
{ 0x00000055, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };

static const GUID MEDIATYPE_Subtitle =
{ 0xe487eb08, 0x6b26, 0x4be9, { 0x9d, 0xd3, 0x99, 0x34, 0x34, 0xd3, 0x13, 0xfd } };

static const GUID MEDIASUBTYPE_UTF8 =
{ 0x87c0b230, 0x3a8, 0x4fdf, { 0x80, 0x10, 0xb2, 0x7a, 0x58, 0x48, 0x20, 0xd } };

static const GUID MEDIASUBTYPE_SSA =
{ 0x3020560f, 0x255a, 0x4ddc, { 0x80, 0x6e, 0x6c, 0x5c, 0xc6, 0xdc, 0xd7, 0xa } };

static const GUID MEDIASUBTYPE_ASS =
{ 0x326444f7, 0x686f, 0x47ff, { 0xa4, 0xb2, 0xc8, 0xc9, 0x63, 0x7, 0xb4, 0xc2 } };

static const GUID MEDIASUBTYPE_ASS2 =
{ 0x370689e7, 0xb226, 0x4f67, { 0x97, 0x8d, 0xf1, 0xb, 0xc1, 0xa9, 0xc6, 0xae } };

static const GUID MEDIASUBTYPE_USF =
{ 0xb753b29a, 0xa96, 0x45be, { 0x98, 0x5f, 0x68, 0x35, 0x1d, 0x9c, 0xab, 0x90 } };

static const GUID MEDIASUBTYPE_VOBSUB =
{ 0xf7239e31, 0x9599, 0x4e43, { 0x8d, 0xd5, 0xfb, 0xaf, 0x75, 0xcf, 0x37, 0xf1 } };

static const GUID MEDIASUBTYPE_VOBSUB2 =
{ 0xb10e53f, 0xabf9, 0x4581, { 0xbe, 0x9c, 0x2c, 0x9a, 0x5e, 0xc6, 0xf2, 0xe0 } };

static const GUID MEDIASUBTYPE_DXRSub =
{ 0x5965e924, 0x63f9, 0x4a64, { 0xb7, 0x1e, 0xf7, 0x51, 0x88, 0xfd, 0x63, 0x84 } };

static const GUID FORMAT_SubtitleInfo =
{ 0xa33d2f7d, 0x96bc, 0x4337, { 0xb2, 0x3b, 0xa8, 0xb9, 0xfb, 0xc2, 0x95, 0xe9 } };


//DEFINE_GUID(MEDIASUBTYPE_wvc1, 0x31637677, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
//DEFINE_GUID(MEDIASUBTYPE_NOKIA_MPEG_ADTS_AAC, 0x00001608, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
//DEFINE_GUID(MEDIASUBTYPE_NOKIA_MPEG_RAW_AAC,  0x00001609, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
//DEFINE_GUID(MEDIASUBTYPE_MPEG_RAW_AAC,  0x00001601, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
//DEFINE_GUID(MEDIASUBTYPE_VODAFONE_MPEG_ADTS_AAC, 0x0000160A, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
//DEFINE_GUID(MEDIASUBTYPE_VODAFONE_MPEG_RAW_AAC,  0x0000160B, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);


///////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////
//Setup data feeder for push reader
extern "C" {
	static int feeder_open(URLContext *h, const char *filename, int flags);
	static int feeder_read(URLContext *h, unsigned char *buf, int size);
	static int feeder_write(URLContext *h, unsigned char *buf, int size);
	static int64_t feeder_seek(URLContext *h, int64_t pos, int whence);
	static int feeder_close(URLContext *h);
	static int feeder_get_handle(URLContext *h);
	URLProtocol* setupFeeder();
}

//ZQ.-
//I hate use global varibale to pass data into feeder! 
//after confirming it's safe, I change to use the filename to pass in.
CSDeMuxInPin* gpSageTVInPin=NULL;

#undef _FILE_TEST_
//#define _FILE_TEST_
#ifdef _FILE_TEST_
static int feeder_open(URLContext *h, const char *filename, int flags)
{
    int fd;
	const char *p;
	DbgLog( (LOG_TRACE, 2, TEXT("Feeder open %s"), filename  ) );  
    if ( strstr( filename, "sagetv:") != NULL )
    	p = filename + 7;
	else
		p = filename;
    fd = _sopen( p,  _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
    if (fd < 0)
        return -2;//AVERROR(ENOENT);
    h->priv_data = (void *) fd;
    return 0;
}

static int feeder_read(URLContext *h, unsigned char *buf, int size)
{
    int fd = (intptr_t) h->priv_data;
    int rv;
	rv = read(fd, buf, size);
	DbgLog( (LOG_TRACE, 2, TEXT("Feeder read %d got:%d"), size, rv  ) );  
	return rv;
}

static int feeder_write(URLContext *h, unsigned char *buf, int size)
{
	DbgLog( (LOG_TRACE, 2, TEXT("Feeder write is called %d"), size  ) );  
    return 0;
}

static int64_t feeder_seek(URLContext *h, int64_t pos, int whence)
{
    int fd = (intptr_t) h->priv_data;
	int64_t file_pos;
    file_pos = _lseeki64(fd, pos, whence);
	DbgLog( (LOG_TRACE, 2, TEXT("Feeder Seek whence:%d pos:%d, file:%d "), whence, (long)pos, (long)file_pos  ) ); 
	return file_pos;
}

static int feeder_close(URLContext *h)
{
    int fd = (intptr_t) h->priv_data;
	DbgLog( (LOG_TRACE, 2, TEXT("Feeder is closed %d") ) );  
    return close(fd);
}
#else
static int feeder_open(URLContext *h, const char *filename, int flags)
{
	DbgLog( (LOG_TRACE, 2, TEXT("Source feeder open."), filename  ) );  
	URLPath *url_path_ptr = (URLPath *)filename;
	if ( url_path_ptr->tag_fourcc == SAGE_FOURCC( "SAGE" ) )
	{
		h->priv_data = url_path_ptr->context_ptr; //it's gpSageTVInPin
	}
	int64_t length = gpSageTVInPin->GetLength();
    return 0;
}

static int feeder_read(URLContext *h, unsigned char *buf, int size)
{
	if ( gpSageTVInPin == NULL ) 
		return 0;

	int bytes = gpSageTVInPin->ReadSampleData( buf, size );
	//DbgLog( (LOG_TRACE, 2, TEXT("Feeder Read size:%d got:%d"), size, bytes  ) ); 
	return bytes;
}

static int feeder_write(URLContext *h, unsigned char *buf, int size)
{
	DbgLog( (LOG_TRACE, 2, TEXT("Feeder write is called %d"), size  ) );  
    return 0;
}

#define AVSEEK_SIZE 0x10000
static int64_t feeder_seek(URLContext *h, int64_t pos, int whence)
{
	int64_t file_pos;
	if ( gpSageTVInPin == NULL ) 
		return 0;
	if ( whence == SEEK_SET )
	{
		file_pos = pos;
		gpSageTVInPin->SetCurrentPos( file_pos );
	}
	else
	if ( whence == SEEK_CUR )
	{
		int64_t cur_pos = gpSageTVInPin->GetCurrentPos();
		file_pos = cur_pos + pos;
		gpSageTVInPin->SetCurrentPos( file_pos );
	
	}else
	if ( whence == SEEK_END )
	{
		int64_t length = gpSageTVInPin->GetLength();
		file_pos = length + pos;
		gpSageTVInPin->SetCurrentPos( file_pos );
	} else
	if ( whence == AVSEEK_SIZE )
	{
		return gpSageTVInPin->GetActualLength();
	}
	//DbgLog( (LOG_TRACE, 2, TEXT("------------------->Feeder Seek whence:%d pos:%d, file:%d "), whence, (long)pos, (long)file_pos  ) ); 
	return file_pos;
}

static int feeder_close(URLContext *h)
{
	return 0;
}

#endif 

static int feeder_get_handle(URLContext *h)
{
    return (int) h->priv_data;
}

URLProtocol sagetv_protocol = {
    "sagetv",
    feeder_open,
    feeder_read,
    feeder_write,
    feeder_seek,
    feeder_close,
	NULL,
	NULL,
	NULL,
    feeder_get_handle
};

URLProtocol* setupFeeder()
{
	return &sagetv_protocol;
}
/////////////////////////////////////////////////////////////////////////////////////////////////////////


CPushReader::~CPushReader()
{
	if ( m_pPushReader != NULL )
		CloseReader( (struct PushReader *)m_pPushReader );
}

void CPushReader::FreeTrackInf()
{
	int i;
	for ( i = 0; i<MAX_DEMUX_TRACK_NUM; i++ )
	{
		if ( m_pTrackInf[i].av.private_data_bytes )
		{
			delete m_pTrackInf[i].av.private_data;
			m_pTrackInf[i].av.private_data = NULL;
			m_pTrackInf[i].av.private_data_bytes = 0;
		}
	}
	memset( m_pTrackInf, 0, sizeof(m_pTrackInf) );
}

void LogExtraData( unsigned char *pData, int Bytes );
HRESULT CPushReader::Init( )
{
	int TrackNum, ret=0;
	DWORD StartTick = 0;  //for checking timeout of data
	DbgLog( (LOG_TRACE, 2, TEXT("PushReader Initilization Ver 0.0.1")  ) );  
	FreeTrackInf();

	ret = verfyVersion( LIBAVFORMAT_VERSION_MAJOR,  sizeof( URLProtocol ), sizeof(URLContext) );
	if ( ret )
	{
		DbgLog( (LOG_ERROR, 1, TEXT("Error PushReader FFMpeg version isn't matched.")  ) );  
		return E_FAIL;
	}

	URLProtocol* sagetv_protocol = setupFeeder();
	struct PushReader *pPushReader;
	URLPath url_path;
	strncpy( url_path.filename, "sagetv:source.mpg", sizeof(url_path.filename) );
	//strncpy( url_path.filename, "sagetv:24.mkv", sizeof(url_path.filename) );
	url_path.tag_fourcc = SAGE_FOURCC( "SAGE" );
	url_path.context_ptr = m_pFilter->InputPin();
	pPushReader =OpenPushReader( url_path.filename, sagetv_protocol, 0 );
	if ( pPushReader == NULL )
	{
		DbgLog( (LOG_ERROR, 1, TEXT("Can't open push reader.")  ) ); 
		return VFW_E_NOT_FOUND;
	}

	if ( m_pPushReader != NULL )
		CloseReader( (struct PushReader *)m_pPushReader );
	m_pPushReader = (void*)pPushReader;

	//config decoders in PushReader
	if ( m_bDisableDecodeAudio )
		disableDecodeAudio( pPushReader );

	if ( m_dwDecodeAudioType & COOK_ENABLE )
		enableDecodeCookAudio( pPushReader );


	int i;
	int VideoStreamsNum, AudioStreamsNum, SpuStreamsNum;
    int Width, Height, Miscv;
    int Channels, Bps, SampleRate, Bitrate, BlockAlign, Misca;
	prGetStreamCounts( pPushReader, &VideoStreamsNum, &AudioStreamsNum, &SpuStreamsNum );
	DbgLog( (LOG_TRACE, 2, TEXT("Found video stream:%d; audio stream:%d; suptitle:stream:%d "), 
		                            VideoStreamsNum, AudioStreamsNum, SpuStreamsNum ) );  

	TrackNum = 0;
	for ( i=0;i<VideoStreamsNum && TrackNum<MAX_DEMUX_TRACK_NUM; i++ )
	{
		int VideoCodec;
		prSetVideoStream(pPushReader, i);
	    VideoCodec = getVideoCodecSettings( pPushReader, &Width, &Height, &Miscv );
		switch ( VideoCodec ) {
		case 1:  //MPEG1
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "MP1V" );
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;;
			getMpegVideoStreamInf( pPushReader, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno, 
								   &m_pTrackInf[TrackNum].language  );
			getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate, &Miscv );
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.width  = Width;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.height = Height;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive = !m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive;
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: MPEG1 %dx%d frame_rate(%d/%d) progress:%d bitrate:%d, lan:'%lu'"),  Width, Height,
				                   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_num, 
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_den,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate,
								   m_pTrackInf[TrackNum].language) );  
			break;
		case 2:  //MPEG2
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "MPGV" );
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;;;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.width  = Width;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.height = Height;
			getMpegVideoStreamInf( pPushReader, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno, 
								   &m_pTrackInf[TrackNum].language  );
			getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate, &Miscv );
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: MPEG2 %dx%d frame_rate(%d/%d) progress:%d, bitrate:%d, lan:'%s'"),  Width, Height,
				                   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate,
								   &m_pTrackInf[TrackNum].language) );  
			{
				int bytes, size;
				unsigned char *extra_data;
				size = sizeof( m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header );
				extra_data = m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header;
				bytes = GetVideoExtraData( pPushReader, extra_data, size );
				m_pTrackInf[TrackNum].av.d.v.mpeg_video.actual_header_length = bytes;
			}

			break;
		case 3:  //DIVX3
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: DIVX3 %dx%d "), Width, Height  ) );  
			break;
		case 4:  //MPEG4
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "MP4V");
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;;;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.width  = Width;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.height = Height;
			getMpegVideoStreamInf( pPushReader, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno, 
								   &m_pTrackInf[TrackNum].language  );
			getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate, &Miscv );
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: MPEG4 %dx%d frame_rate(%d/%d) progress:%d, bitrate:%d, lan:'%s'"),  Width, Height,
				                   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate,
								   &m_pTrackInf[TrackNum].language) );  
			{
				int bytes, size;
				unsigned char *extra_data;
				size = sizeof( m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header );
				extra_data = m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header;
				bytes = GetVideoExtraData( pPushReader, extra_data, size );
				m_pTrackInf[TrackNum].av.d.v.mpeg_video.actual_header_length = bytes;
				LogExtraData( extra_data, bytes );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: MPEG4 %dx%d "), Width, Height  ) );  
			break;
		case 5:  //MPEG4Flash
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "FLV1" );
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.width  = Width;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.height = Height;
			getMpegVideoStreamInf( pPushReader, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno, 
								   &m_pTrackInf[TrackNum].language  );
			getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate, &Miscv );
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: MPEG4 %dx%d frame_rate(%d/%d) progress:%d, bitrate:%d, lan:'%s'"),  Width, Height,
				                   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate,
								   &m_pTrackInf[TrackNum].language) );  
			{
				int bytes, size;
				unsigned char *extra_data;
				size = sizeof( m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header );
				extra_data = m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header;
				bytes = GetVideoExtraData( pPushReader, extra_data, size );
				m_pTrackInf[TrackNum].av.d.v.mpeg_video.actual_header_length = bytes;
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: FLV1 (MPEG4 Flash) %dx%d "), Width, Height  ) );  
			break;
		case 6:  //WMV1
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "WMV1" );
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.width  = Width;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.height = Height;
			getMpegVideoStreamInf( pPushReader, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno, 
								   &m_pTrackInf[TrackNum].language  );
			getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate, &Miscv );
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: WMV6 %dx%d frame_rate(%d/%d) progress:%d, bitrate:%d, lan:'%s'"),  Width, Height,
				                   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate,
								   &m_pTrackInf[TrackNum].language) );  
			{
				int bytes, size;
				unsigned char *extra_data;
				size = sizeof( m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header );
				extra_data = m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header;
				bytes = GetVideoExtraData( pPushReader, extra_data, size );
				m_pTrackInf[TrackNum].av.d.v.mpeg_video.actual_header_length = bytes;
			}
			break;
		case 7:  //WMV2
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "WMV2" );
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.width  = Width;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.height = Height;
			getMpegVideoStreamInf( pPushReader, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno, 
								   &m_pTrackInf[TrackNum].language  );
			getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate, &Miscv );
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: WMV8 %dx%d frame_rate(%d/%d) progress:%d, bitrate:%d, lan:'%s'"),  Width, Height,
				                   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate,
								   &m_pTrackInf[TrackNum].language) );  
			{
				int bytes, size;
				unsigned char *extra_data;
				size = sizeof( m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header );
				extra_data = m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header;
				bytes = GetVideoExtraData( pPushReader, extra_data, size );
				m_pTrackInf[TrackNum].av.d.v.mpeg_video.actual_header_length = bytes;
			}
			break;
		case 8:  //VC1
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "VC1 " );
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;
			m_pTrackInf[TrackNum].av.d.v.h264.width = Width;
			m_pTrackInf[TrackNum].av.d.v.h264.height = Height;

			
			{
				int frame_num, frame_den;
				getMpegVideoStreamInf( pPushReader, 
				                   (int*)&frame_num, 
								   (int*)&frame_den, 
								   &m_pTrackInf[TrackNum].language  );
				m_pTrackInf[TrackNum].av.d.v.h264.frame_rate_nomi = frame_num;
				m_pTrackInf[TrackNum].av.d.v.h264.frame_rate_deno = frame_den;
			}
			{
				int bit_rate;
				getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.h264.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.h264.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.h264.progressive, 
								(int*)&bit_rate, &Miscv );
			}
			{
				int bytes, size;
				unsigned char *extra_data;
				size = sizeof( m_pTrackInf[TrackNum].av.d.v.h264.sps );
				extra_data = m_pTrackInf[TrackNum].av.d.v.h264.sps;
				bytes = GetVideoExtraData( pPushReader, extra_data , size );
				LogExtraData( extra_data, bytes );
				m_pTrackInf[TrackNum].av.d.v.h264.sps_length = bytes;
			}
			//m_pTrackInf[TrackNum].av.d.v.h264.progressive = 1;
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: VC1 %dx%d frame_rate:(%d/%d) progress:%d profile:%d level:%d lan:'%s'"),  Width, Height,
								   m_pTrackInf[TrackNum].av.d.v.h264.frame_rate_nomi, m_pTrackInf[TrackNum].av.d.v.h264.frame_rate_deno,
								   m_pTrackInf[TrackNum].av.d.v.h264.progressive,
								   m_pTrackInf[TrackNum].av.d.v.h264.profile,
								   m_pTrackInf[TrackNum].av.d.v.h264.level,
								   &m_pTrackInf[TrackNum].language) );  


			break;
		case 9:  //WMV9
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "WMV3" );
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;;;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.width  = Width;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.height = Height;
			getMpegVideoStreamInf( pPushReader, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno, 
								   &m_pTrackInf[TrackNum].language  );
			getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate, &Miscv );
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: WMV9 %dx%d frame_rate(%d/%d) progress:%d, bitrate:%d, lan:'%s'"),  Width, Height,
				                   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate,
								   &m_pTrackInf[TrackNum].language) );  
			{
				int bytes, size;
				unsigned char *extra_data;
				size = sizeof( m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header );
				extra_data = m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header;
				bytes = GetVideoExtraData( pPushReader, extra_data, size );
				m_pTrackInf[TrackNum].av.d.v.mpeg_video.actual_header_length = bytes;
				LogExtraData( extra_data, bytes );
			}
			break;
		case 10: //H.264
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "H264" );
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;
			m_pTrackInf[TrackNum].av.d.v.h264.width = Width;
			m_pTrackInf[TrackNum].av.d.v.h264.height = Height;

			
			{
				int frame_num, frame_den;
				getMpegVideoStreamInf( pPushReader, 
				                   (int*)&frame_num, 
								   (int*)&frame_den, 
								   &m_pTrackInf[TrackNum].language  );
				m_pTrackInf[TrackNum].av.d.v.h264.frame_rate_nomi = frame_num;
				m_pTrackInf[TrackNum].av.d.v.h264.frame_rate_deno = frame_den;
			}
			{
				int bit_rate;
				getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.h264.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.h264.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.h264.progressive, 
								(int*)&bit_rate, &Miscv );
			}
			{
				int bytes, size;
				unsigned char *extra_data;
				size = sizeof( m_pTrackInf[TrackNum].av.d.v.h264.sps );
				extra_data = m_pTrackInf[TrackNum].av.d.v.h264.sps;
				bytes = GetVideoExtraData( pPushReader, extra_data , size );
				LogExtraData( extra_data, bytes );
				m_pTrackInf[TrackNum].av.d.v.h264.sps_length = bytes;
			}
			if ( stateFixH264( pPushReader ) )
				m_pTrackInf[TrackNum].pushreader_fix_flag = FIX_H264_FLAG;

			DbgLog( (LOG_TRACE, 2, TEXT("Found video: H.264 %dx%d frame_rate:(%d/%d) progress:%d profile:%d level:%d lan:'%s' %s"),  Width, Height,
								   m_pTrackInf[TrackNum].av.d.v.h264.frame_rate_nomi, m_pTrackInf[TrackNum].av.d.v.h264.frame_rate_deno,
								   m_pTrackInf[TrackNum].av.d.v.h264.progressive,
								   m_pTrackInf[TrackNum].av.d.v.h264.profile,
								   m_pTrackInf[TrackNum].av.d.v.h264.level,
								   &m_pTrackInf[TrackNum].language, 
								   m_pTrackInf[TrackNum].pushreader_fix_flag?"fixH264 on":"fixH264 off" ));  


			break;
		case 12:  //VP6F
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "VP6F" );
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.width  = Width;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.height = Height;
			getMpegVideoStreamInf( pPushReader, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno, 
								   &m_pTrackInf[TrackNum].language  );
			getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate, &Miscv );
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: VP6F %dx%d frame_rate(%d/%d) progress:%d, bitrate:%d, lan:'%s'"),  Width, Height,
				                   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate,
								   &m_pTrackInf[TrackNum].language) );  
			{
				int bytes, size;
				unsigned char *extra_data;
				size = sizeof( m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header );
				extra_data = m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header;
				bytes = GetVideoExtraData( pPushReader, extra_data, size );
				m_pTrackInf[TrackNum].av.d.v.mpeg_video.actual_header_length = bytes;
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: VP6F (FLV4) %dx%d "), Width, Height  ) );  
			break;
		case 13:  //RealVideo40
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "RV40" );
			m_pTrackInf[TrackNum].av.content_type = VIDEO_DATA;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.width  = Width;
			m_pTrackInf[TrackNum].av.d.v.mpeg_video.height = Height;
			getMpegVideoStreamInf( pPushReader, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   (int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno, 
								   &m_pTrackInf[TrackNum].language  );
			getMpegVideoInfo( pPushReader, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.profile, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.level, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive, 
								(int*)&m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate, &Miscv );
			{
				int bytes, size;
				unsigned char *extra_data;
				size = sizeof( m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header );
				extra_data = m_pTrackInf[TrackNum].av.d.v.mpeg_video.raw_header;
				bytes = GetVideoExtraData( pPushReader, extra_data, size );
				m_pTrackInf[TrackNum].av.d.v.mpeg_video.actual_header_length = bytes;
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found video: RV40 %dx%d frame_rate(%d/%d) progress:%d, bitrate:%d, lan:'%s'"),  Width, Height,
				                   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_nomi, 
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.frame_rate_deno,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.progressive,
								   m_pTrackInf[TrackNum].av.d.v.mpeg_video.bit_rate,
								   &m_pTrackInf[TrackNum].language) );  
			break;
	case 0:  //NO video
			DbgLog( (LOG_TRACE, 2, TEXT("Not found video.")  ) );  
			break;
		case -1: //unsupport
			DbgLog( (LOG_TRACE, 2, TEXT("Unsupport video.")  ) );  
			break;
		default: //Unkown
			DbgLog( (LOG_TRACE, 2, TEXT("Unkown format video.")  ) );  
			break;
		}
		if ( VideoCodec >= 1 )
		{
			if (Miscv ) DbgLog( (LOG_TRACE, 2, TEXT("miscv:0x%x"), &Miscv  ) );  
			m_pTrackInf[TrackNum].stream_ready = 1;
			m_pTrackInf[TrackNum].stream_type = 1;
			m_pTrackInf[TrackNum].stream_id = i;      
			TrackNum++;
		}

	}
	for ( i=0; i<AudioStreamsNum && TrackNum<MAX_DEMUX_TRACK_NUM; i++ )
	{
		int AudioCodec;
		int Bytes;
		int SampleFmt, Layer, FrameSize, Mode;
		prSelectAudioStream(pPushReader, i);
		AudioCodec = getAudioCodecSettings( pPushReader, &Channels, &Bps, &SampleRate, &Bitrate, &BlockAlign, &Misca );
		switch ( AudioCodec ) {
		case 1:  //MPEG layer 1,2,3
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "MPGA" );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.samples_per_sec = SampleRate;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.avgbytes_per_sec = Bitrate;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.block_align = BlockAlign;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			if ( getAudioMpegInfo( pPushReader, &SampleFmt, &Layer, &FrameSize, &Mode ) )
			{
				m_pTrackInf[TrackNum].av.d.a.mpeg_audio.head_layer = Layer;
				if ( SampleFmt == 0 )  m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 8; else
				if ( SampleFmt == 1 )  m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 16; else
				if ( SampleFmt == 2 )  m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 32; else
					m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 0;
			}
			{
				unsigned char* extra_data = m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra; 
				int bytes = sizeof(m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra);
				bytes = m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra_bytes = GetAudioExtraData( pPushReader, extra_data , bytes );
				LogExtraData( extra_data, bytes );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: MPEG layer:%d ch:%d sr:%d br:%d bps:"), Layer, Channels, SampleRate, Bitrate, Bps ) );  
			break;
		case 2:  //AAC
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "AAC " );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.aac.avgbytes_per_sec = Bps;
			m_pTrackInf[TrackNum].av.d.a.aac.samples_per_sec = SampleRate;
			m_pTrackInf[TrackNum].av.d.a.aac.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.aac.bitrate = Bitrate;
			m_pTrackInf[TrackNum].av.d.a.aac.format = 1;
			m_pTrackInf[TrackNum].av.d.a.aac.block_align = BlockAlign;
			{
				unsigned char* extra_data = m_pTrackInf[TrackNum].av.d.a.aac.u.atds_header; 
				int bytes = sizeof(m_pTrackInf[TrackNum].av.d.a.aac.u.atds_header);
				bytes = m_pTrackInf[TrackNum].av.d.a.aac.header_bytes = GetAudioExtraData( pPushReader, extra_data , bytes );
				LogExtraData( extra_data, bytes );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: AAC ch:%d sr:%d br:%d bps:%d extra:%d"), Channels, SampleRate, Bitrate, Bps, Bytes  ) );  
			break;
		case 3:  //AC3
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "AC3 " );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.ac3.avgbytes_per_sec = Bps;
			m_pTrackInf[TrackNum].av.d.a.ac3.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.ac3.block_align = BlockAlign;
			m_pTrackInf[TrackNum].av.d.a.ac3.samples_per_sec = SampleRate;
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: AC3 ch:%d sr:%d br:%d bps:%d"), Channels, SampleRate, Bitrate, Bps  ) );  
			break;
		case 4:  //DTS
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "DTS " );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.dts.samples_per_sec = SampleRate;
			m_pTrackInf[TrackNum].av.d.a.dts.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.dts.block_align = BlockAlign;
			m_pTrackInf[TrackNum].av.d.a.dts.avgbytes_per_sec = Bps;
			{
				unsigned char extra_data[256]; int size = sizeof(extra_data);
				Bytes = GetAudioExtraData( pPushReader, extra_data , size );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: DTS ch:%d sr:%d br:%d bps:%d extra:%d"), Channels, SampleRate, Bitrate, Bps, Bytes  ) );  
			break;
		case 5:  //WMA
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "WMA " );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.samples_per_sec = SampleRate;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.avgbytes_per_sec = Bitrate;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.block_align = BlockAlign;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.head_layer = 0x81; //WMA
			if ( getAudioMpegInfo( pPushReader, &SampleFmt, &Layer, &FrameSize, &Mode ) )
			{
				if ( SampleFmt == 0 )  m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 8; else
				if ( SampleFmt == 1 )  m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 16; else
				if ( SampleFmt == 2 )  m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 32; else
					m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 0;
			}
			{
				unsigned char* extra_data = m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra; 
				int bytes = sizeof(m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra);
				bytes = m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra_bytes = GetAudioExtraData( pPushReader, extra_data , bytes );
				LogExtraData( extra_data, bytes );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: WMA layer:%d ch:%d sr:%d br:%d bps:"), Layer, Channels, SampleRate, Bitrate, Bps ) );  
			break;
		case 6:  //PCM
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "PCM " );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.pcm.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.pcm.bits_rate = Bitrate;
			m_pTrackInf[TrackNum].av.d.a.pcm.bits_per_sample = 0;
			m_pTrackInf[TrackNum].av.d.a.pcm.block_align = BlockAlign;
			m_pTrackInf[TrackNum].av.d.a.pcm.samples_per_sec = SampleRate;
			if ( getAudioMpegInfo( pPushReader, &SampleFmt, &Layer, &FrameSize, &Mode ) )
			{
				if ( SampleFmt == 0 )  m_pTrackInf[TrackNum].av.d.a.pcm.bits_per_sample = 8; else
				if ( SampleFmt == 1 )  m_pTrackInf[TrackNum].av.d.a.pcm.bits_per_sample = 16; else
				if ( SampleFmt == 2 )  m_pTrackInf[TrackNum].av.d.a.pcm.bits_per_sample = 32; 
			}

			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: PCM ch:%d sr:%d br:%d bps:%d align:%d"), Channels, SampleRate, Bitrate, Bps, BlockAlign  ) );
			break;
		case 7:  //WMA PRO
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "WMA " );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.samples_per_sec = SampleRate;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.avgbytes_per_sec = Bitrate;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.block_align = BlockAlign;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.head_layer = 0x82; //WMA PRO
			if ( getAudioMpegInfo( pPushReader, &SampleFmt, &Layer, &FrameSize, &Mode ) )
			{
				if ( SampleFmt == 0 )  m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 8; else
				if ( SampleFmt == 1 )  m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 16; else
				if ( SampleFmt == 2 )  m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 32; else
					m_pTrackInf[TrackNum].av.d.a.mpeg_audio.bits_per_sample = 0;
			}
			{
				unsigned char* extra_data = m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra; 
				int bytes = sizeof(m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra);
				bytes = m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra_bytes = GetAudioExtraData( pPushReader, extra_data , bytes );
				LogExtraData( extra_data, bytes );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: WMA PRO layer:%d ch:%d sr:%d br:%d bps:"), Layer, Channels, SampleRate, Bitrate, Bps ) );  
			break;

			break;
		case 8:  //EAC3
			m_pTrackInf[TrackNum].av.format_fourcc = SAGE_FOURCC( "AC3E" );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.eac3.avgbytes_per_sec = Bps;
			m_pTrackInf[TrackNum].av.d.a.eac3.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.eac3.block_align = BlockAlign;
			m_pTrackInf[TrackNum].av.d.a.eac3.samples_per_sec = SampleRate;
			{
				unsigned char extra_data[256]; int size = sizeof(extra_data);
				Bytes = GetAudioExtraData( pPushReader, extra_data , size );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: EAC3 ch:%d sr:%d br:%d bps:%d extra:%d"), Channels, SampleRate, Bitrate, Bps, Bytes  ) );  
			break;
		case 9:  //TrueHD
			m_pTrackInf[TrackNum].av.format_fourcc =  SAGE_FOURCC( "AC3T" );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.ac3.avgbytes_per_sec = Bps;
			m_pTrackInf[TrackNum].av.d.a.ac3.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.ac3.block_align = BlockAlign;
			m_pTrackInf[TrackNum].av.d.a.ac3.samples_per_sec = SampleRate;
			{
				unsigned char extra_data[256]; int size = sizeof(extra_data);
				Bytes = GetAudioExtraData( pPushReader, extra_data , size );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: TrueHD ch:%d sr:%d br:%d bps:%d extra:%d"), Channels, SampleRate, Bitrate, Bps, Bytes  ) );  
			break;
		case 10: //Cooker
			m_pTrackInf[TrackNum].av.format_fourcc =  SAGE_FOURCC( "COOK" );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.avgbytes_per_sec = Bps;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.block_align = BlockAlign;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.samples_per_sec = SampleRate;
			{
				unsigned char* extra_data=m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra; 
				int size = sizeof(m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra);
				Bytes = GetAudioExtraData( pPushReader, extra_data , size );
				m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra_bytes = Bytes;
				LogExtraData( extra_data, Bytes );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: COOKER ch:%d sr:%d br:%d bps:%d extra:%d"), Channels, SampleRate, Bitrate, Bps, Bytes  ) );  
			break;
		case 11:  //VORBIS
			m_pTrackInf[TrackNum].av.format_fourcc =  SAGE_FOURCC( "VRBS" );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.avgbytes_per_sec = Bps;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.block_align = BlockAlign;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.samples_per_sec = SampleRate;
			{
				unsigned char* extra_data=m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra; 
				int size = sizeof(m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra);
				Bytes = GetAudioExtraData( pPushReader, extra_data , size );
				m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra_bytes = Bytes;
				if ( Bytes == size ) //private data size is bigger than extra buffer, we need allocate a buffer.
				{
					size = 4096;
					if ( size > Bytes )
					{
						m_pTrackInf[TrackNum].av.d.a.mpeg_audio.extra_bytes = 0;
						extra_data = new unsigned char[size];
						if ( extra_data != NULL )
						{
							Bytes = GetAudioExtraData( pPushReader, extra_data , size );
							m_pTrackInf[TrackNum].av.private_data = extra_data;
							m_pTrackInf[TrackNum].av.private_data_bytes = Bytes;
						}
					}
				}
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: VORBIS ch:%d sr:%d br:%d bps:%d extra:%d"), Channels, SampleRate, Bitrate, Bps, Bytes  ) );  
			break;
		case 12:  //AFLAC
			m_pTrackInf[TrackNum].av.format_fourcc =  SAGE_FOURCC( "AFLC" );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.avgbytes_per_sec = Bps;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.block_align = BlockAlign;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.samples_per_sec = SampleRate;
			{
				unsigned char extra_data[256]; int size = sizeof(extra_data);
				Bytes = GetAudioExtraData( pPushReader, extra_data , size );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: AFLC ch:%d sr:%d br:%d bps:%d extra:%d"), Channels, SampleRate, Bitrate, Bps, Bytes  ) );  
			break;
		case 13:  //FLAC
			m_pTrackInf[TrackNum].av.format_fourcc =  SAGE_FOURCC( "FLAC" );
			m_pTrackInf[TrackNum].av.content_type = AUDIO_DATA;
			getAudioStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.avgbytes_per_sec = Bps;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.channels = Channels;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.block_align = BlockAlign;
			m_pTrackInf[TrackNum].av.d.a.mpeg_audio.samples_per_sec = SampleRate;
			{
				unsigned char extra_data[256]; int size = sizeof(extra_data);
				Bytes = GetAudioExtraData( pPushReader, extra_data , size );
			}
			DbgLog( (LOG_TRACE, 2, TEXT("Found audio: FLAC ch:%d sr:%d br:%d bps:%d extra:%d"), Channels, SampleRate, Bitrate, Bps, Bytes  ) );  
			break;

		case 0:  //NO audio
			DbgLog( (LOG_TRACE, 2, TEXT("Not found audio.")  ) );  
			break;
		case -1: //unsupport
			DbgLog( (LOG_TRACE, 2, TEXT("unsupport audio: ch:%d sr:%d br:%d bps:%d align:%d"), Channels, SampleRate, Bitrate, Bps, BlockAlign, Bytes  ) );  
			break;
		default: //Unkown
			DbgLog( (LOG_TRACE, 2, TEXT("Unkown format audio: ch:%d sr:%d br:%d bps:%d align:%d"), Channels, SampleRate, Bitrate, Bps, BlockAlign, Bytes  ) );  
			break;
		}
		if ( AudioCodec >= 1 )
		{
			m_pTrackInf[TrackNum].stream_ready = 1;
			m_pTrackInf[TrackNum].stream_type = 2;
			m_pTrackInf[TrackNum].stream_id = i;      
			TrackNum++;
		}
	}
	for ( i=0; i<SpuStreamsNum && TrackNum<MAX_DEMUX_TRACK_NUM; i++ )
	{
		prSetSpuStream(pPushReader, i);
		m_pTrackInf[TrackNum].av.format_fourcc =  SAGE_FOURCC( "SPU " );
		m_pTrackInf[TrackNum].av.content_type = SUBPICTURE_DATA;
		m_pTrackInf[TrackNum].av.d.s.subt.language = SAGE_FOURCC( "eng" );
		DbgLog( (LOG_TRACE, 2, TEXT("SupPicture stream")  ) );  
		getSpuStreamInf( pPushReader, &m_pTrackInf[TrackNum].language );
		m_pTrackInf[TrackNum].stream_ready = 1;
		m_pTrackInf[TrackNum].stream_type = 3;
		m_pTrackInf[TrackNum].stream_id = i;      
		TrackNum++;
	}
	m_nTrackNum = TrackNum;

	m_nMainVideo = SelectMainVideo( );
	m_nMainAudio = SelectMainAudio( );
	m_nMainSpu   = SelectMainSubpicture( );
	if ( m_nMainVideo >= 0 )
		prSetVideoStream( pPushReader, m_pTrackInf[m_nMainVideo].stream_id );
	if ( m_nMainAudio >= 0 )
		prSelectAudioStream( pPushReader, m_pTrackInf[m_nMainAudio].stream_id );
	if ( m_nMainSpu >= 0 )
		prSetSpuStream( pPushReader, m_pTrackInf[m_nMainSpu].stream_id );
	DbgLog( (LOG_TRACE, 2, TEXT("Main Video:%d (%d); Main Audio:%d (%d)"), 
		m_nMainVideo, m_pTrackInf[m_nMainVideo].stream_id, m_nMainAudio, m_pTrackInf[m_nMainAudio].stream_id  ));  


	m_rtDur = getDuration( pPushReader );
	m_rtDur = m_rtDur * 10000; 

	m_llFirstPTS = getFirstTime( pPushReader );
	if ( !m_dwRequestedByUser )
		m_dwRequestedTSChannel = 0;

	UpdateStream( );

	prSeek( pPushReader, 0 );

	return S_OK;
}

void CPushReader::SeekPTS( LONGLONG llPts )
{
	LONGLONG llDur =  getDuration( (struct PushReader *)m_pPushReader );
	llPts /= 10000;
	if ( llDur < llPts )
	{
		llPts = llDur;
	}
	prSeek( (struct PushReader *)m_pPushReader, llPts );
}

void CPushReader::ConnectSourceReader( )
{
	//should locked by called, not race conflicting to process
	gpSageTVInPin = m_pFilter->InputPin();
}

void CPushReader::DisconnectSourceReader( )
{
	//should locked by called, not race to process
	gpSageTVInPin = NULL;
}


REFERENCE_TIME CPushReader::GetDuration( )
{
	return  m_rtDur;
}

REFERENCE_TIME CPushReader::GetLastTimestamp( )
{
	return  1;
}

int CPushReader::SelectMainVideo( )
{
	int i, score, max_score=-1, main_video=-1;

	for ( i = 0; i<m_nTrackNum; i++ )
	{
		if ( m_pTrackInf[i].stream_type != 1 )
			continue;
		//select the bigest picture as main video, if the same pciture, priority H.264->MPEG2->MPEG1
		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "MPGV" ) )
		{
			score = m_pTrackInf[i].av.d.v.mpeg_video.height * m_pTrackInf[i].av.d.v.mpeg_video.width;
			score = score * 100 + 2;
		} else
		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "MP1V" ) )
		{
			score = m_pTrackInf[i].av.d.v.mpeg_video.height * m_pTrackInf[i].av.d.v.mpeg_video.width;
			score = score * 100 + 1;
		} else
		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "H.264" ) )
		{
			score = m_pTrackInf[i].av.d.v.h264.height * m_pTrackInf[i].av.d.v.h264.width;
			score = score * 100 + 1;
		} else
		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "VC1 " ) )
		{
			score = m_pTrackInf[i].av.d.v.h264.height * m_pTrackInf[i].av.d.v.h264.width;
			score = score * 100 + 1;
		} else
		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "MP4V") )
		{
			score = m_pTrackInf[i].av.d.v.mpeg_video.height * m_pTrackInf[i].av.d.v.mpeg_video.width;
			score = score * 100 + 1;
		} else
		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "WMV3" ) ||
			 m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "WMV2" ) ||
			 m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "WMV1" ) )
		{
			score = m_pTrackInf[i].av.d.v.mpeg_video.height * m_pTrackInf[i].av.d.v.mpeg_video.width;
			score = score * 100 + 1;
		} else
		{
			score = 0;
		}

		if ( score > max_score ) main_video = i;

	}
	return main_video;
}

int CPushReader::SelectMainAudio( )
{
	int i, score, max_score=-1, main_audio=-1;

	for ( i = 0; i<m_nTrackNum; i++ )
	{
		if ( m_pTrackInf[i].stream_type != 2 )
			continue;
		//select based weighing language, channel, sample rate 
		score = 0;
		if ( m_pTrackInf[i].language == m_dwLanguage )
		{
			score = 100;
		}

		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "AC3 " ) )
		{
			score = score*100 + m_pTrackInf[i].av.d.a.ac3.channels;
			score = score*20 + m_pTrackInf[i].av.d.a.ac3.avgbytes_per_sec;
		} else
		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "MPGA" ) )
		{
			score = m_pTrackInf[i].av.d.a.mpeg_audio.channels;
			score = score*20 + m_pTrackInf[i].av.d.a.mpeg_audio.avgbytes_per_sec;
		} else
		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "DTS " ) )
		{
			score = m_pTrackInf[i].av.d.a.dts.channels;
			score = score*20 + m_pTrackInf[i].av.d.a.dts.avgbytes_per_sec;
		} else
		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "WMA " ) )
		{
			score = m_pTrackInf[i].av.d.a.mpeg_audio.channels;
			score = score*20 + m_pTrackInf[i].av.d.a.mpeg_audio.avgbytes_per_sec;
		} else
		if ( m_pTrackInf[i].av.format_fourcc == SAGE_FOURCC( "PCM " ) )
		{
			score = m_pTrackInf[i].av.d.a.pcm.channels;
			score = score*20 + m_pTrackInf[i].av.d.a.pcm.avgbytes_per_sec;
		} else
		{
			score = 0;
		}

		if ( score > max_score ) 
		{
			main_audio = i;
			max_score = score;
		}

	}
	return main_audio;

}

int CPushReader::SelectMainSubpicture( )
{
	int i, score, max_score=-1, main_spu=-1;

	for ( i = 0; i<m_nTrackNum; i++ )
	{
		if ( m_pTrackInf[i].stream_type != 3 )
			continue;
		//select based weighing language, channel, sample rate 
		score = 0;
		if ( m_pTrackInf[i].language == m_dwLanguage )
		{
			score = 100;
		}

		if ( score > max_score ) 
		{
			main_spu = i;
			max_score = score;
		}

	}
	return main_spu;

}

int CPushReader::EOFOfPusher( )
{
	return getEOF( (struct PushReader*)  m_pPushReader );
}

//////////////////////////////////////////////////////////////////
HRESULT CPushReader::SetMpeg1VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
    cmt->majortype = MEDIATYPE_Video;
    cmt->subtype =   MEDIASUBTYPE_MPEG1Payload;
	const DWORD MPEG1_FOURCC = 0x3147504d; //DWORD('MPG1');
    VIDEOINFO *videoInfo =
        (VIDEOINFO *)cmt->AllocFormatBuffer(FIELD_OFFSET(MPEG1VIDEOINFO, bSequenceHeader[pMpegVideo->actual_header_length]));
    if (videoInfo == NULL) 
        return E_OUTOFMEMORY;
    
    RESET_HEADER(videoInfo);
	ZeroMemory(videoInfo, sizeof(videoInfo) ); 
	videoInfo->bmiHeader.biCompression = MPEG1_FOURCC;
	videoInfo->dwBitRate          = pMpegVideo->bit_rate;
    videoInfo->rcSource.right     = pMpegVideo->width;
    videoInfo->bmiHeader.biWidth  = pMpegVideo->width;
    videoInfo->rcSource.bottom    = pMpegVideo->height;
    videoInfo->bmiHeader.biHeight = pMpegVideo->height;
    videoInfo->bmiHeader.biXPelsPerMeter = 0;//pMpegVideo->lXPelsPerMeter;
    videoInfo->bmiHeader.biYPelsPerMeter = 0;//pMpegVideo->lYPelsPerMeter;
    videoInfo->bmiHeader.biSize   = sizeof(BITMAPINFOHEADER);

    videoInfo->AvgTimePerFrame = pMpegVideo->time_per_frame;
    MPEG1VIDEOINFO *mpgvideoInfo = (MPEG1VIDEOINFO *)videoInfo;
	mpgvideoInfo->cbSequenceHeader = pMpegVideo->actual_header_length;
	CopyMemory((PVOID)mpgvideoInfo->bSequenceHeader,
               (PVOID)pMpegVideo->raw_header,
			   pMpegVideo->actual_header_length);
    mpgvideoInfo->dwStartTimeCode = 0;//pMpegVideo->StartTimeCode;

    cmt->SetFormatType(&FORMAT_MPEGVideo);
    return S_OK;
}


HRESULT CPushReader::SetMpeg2VideoMediaType(CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
    cmt->majortype = MEDIATYPE_Video;
    cmt->subtype = MEDIASUBTYPE_MPEG2_VIDEO;
	const DWORD MPEG2_FOURCC = 0x3247504d; //DWORD('MPG2');

    MPEG2VIDEOINFO *videoInfo = // This macro finds the pointer to the last element in the sedhdr block to determine size
        //(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(FIELD_OFFSET(MPEG2VIDEOINFO, dwSequenceHeader[pMpegVideo->lActualHeaderLen]));
		// There's already a DWORD in the block for the sequence header, so subtract 4 bytes
		(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(sizeof(MPEG2VIDEOINFO) + pMpegVideo->actual_header_length - 4);
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	// reset the header's memory
	ZeroMemory((PVOID)(videoInfo), sizeof(MPEG2VIDEOINFO) + pMpegVideo->actual_header_length - 4);

	videoInfo->hdr.bmiHeader.biCompression = MPEG2_FOURCC;
    videoInfo->hdr.dwBitRate          = pMpegVideo->bit_rate;
    videoInfo->hdr.rcSource.right     = pMpegVideo->width;
    videoInfo->hdr.bmiHeader.biWidth  = pMpegVideo->width;
    videoInfo->hdr.rcSource.bottom    = pMpegVideo->height;
    videoInfo->hdr.bmiHeader.biHeight = pMpegVideo->height;
    videoInfo->hdr.bmiHeader.biXPelsPerMeter = 1;
    videoInfo->hdr.bmiHeader.biYPelsPerMeter = 1;
    videoInfo->hdr.bmiHeader.biSize   = sizeof(BITMAPINFOHEADER);
	cmt->bFixedSizeSamples = 0;
	cmt->SetTemporalCompression( FALSE );
	//guess ar ratio
	{
		float r_min=100, rr, ratio_tbl[4]={ (float)1.0/1, (float)4.0/3, (float)16.0/9, (float)20.0/11 };
		int i, ri;
		rr =  (float)pMpegVideo->width/(pMpegVideo->height);
		for ( i=0; i<4; i++ )
		{
			float dif = ( rr-ratio_tbl[i] );
			if ( dif < 0 ) dif = -dif;
			if ( dif < r_min )
			{
				ri = i; r_min = dif;
			}
		}

		switch (ri+1)
		{
			case 1: // SAR=1:1
				videoInfo->hdr.dwPictAspectRatioX = 1;
				videoInfo->hdr.dwPictAspectRatioY = 1;
				break;
			case 2: // DAR=3/4
				videoInfo->hdr.dwPictAspectRatioX = 4;
				videoInfo->hdr.dwPictAspectRatioY = 3;
				break;
			case 3: // DAR=9/16
				videoInfo->hdr.dwPictAspectRatioX = 16;
				videoInfo->hdr.dwPictAspectRatioY = 9;
				break;
			case 4: 
				videoInfo->hdr.dwPictAspectRatioX = 20;
				videoInfo->hdr.dwPictAspectRatioY = 11;
				break;
		}
	}
	videoInfo->hdr.dwInterlaceFlags = pMpegVideo->progressive ? 0 : (AMINTERLACE_IsInterlaced);
    videoInfo->cbSequenceHeader = pMpegVideo->actual_header_length;
    CopyMemory((PVOID)videoInfo->dwSequenceHeader,
			   (PVOID)pMpegVideo->raw_header,
               pMpegVideo->actual_header_length);


    //videoInfo->dwStartTimeCode = pMpegVideo->StartTimeCode;
	switch (pMpegVideo->profile)
	{
		case 5:
			videoInfo->dwProfile = AM_MPEG2Profile_Simple;
			break;
		case 4:
			videoInfo->dwProfile = AM_MPEG2Profile_Main;
			break;
		case 3:
			videoInfo->dwProfile = AM_MPEG2Profile_SNRScalable;
			break;
		case 2:
			videoInfo->dwProfile = AM_MPEG2Profile_SpatiallyScalable;
			break;
		case 1:
			videoInfo->dwProfile = AM_MPEG2Profile_High;
			break;
	}
	switch (pMpegVideo->level)
	{
		case 10:
			videoInfo->dwLevel = AM_MPEG2Level_Low;
			break;
		case 8:
			videoInfo->dwLevel = AM_MPEG2Level_Main;
			break;
		case 6:
			videoInfo->dwLevel = AM_MPEG2Level_High1440;
			break;
		case 4:
			videoInfo->dwLevel = AM_MPEG2Level_High;
			break;
	}
	{
		if ( pMpegVideo->frame_rate_nomi && pMpegVideo->frame_rate_deno ) 
			videoInfo->hdr.AvgTimePerFrame = (LONGLONG)10000000*pMpegVideo->frame_rate_deno/pMpegVideo->frame_rate_nomi;  
		else	
			videoInfo->hdr.AvgTimePerFrame = 10000000/25;

	}

    cmt->SetFormatType(&FORMAT_MPEG2Video);
    return S_OK;
}

HRESULT CPushReader::SetH264VideoMediaType(CMediaType *cmt, H264_VIDEO *pH264Video )
{
    cmt->majortype = MEDIATYPE_Video;
    cmt->subtype = MEDIASUBTYPE_H264; 

	const DWORD H264FOURCC = 0x34363248; //DWORD('H264');


	//VIDEOINFOHEADER2 *videoInfo = (VIDEOINFOHEADER2*)cmt->AllocFormatBuffer( sizeof(VIDEOINFOHEADER2)+pH264Video->sps_length-4 );
	 MPEG2VIDEOINFO *videoInfo = // This macro finds the pointer to the last element in the sedhdr block to determine size
        //(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(FIELD_OFFSET(MPEG2VIDEOINFO, dwSequenceHeader[pMpegVideo->lActualHeaderLen]));
		// There's already a DWORD in the block for the sequence header, so subtract 4 bytes
		(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(sizeof(MPEG2VIDEOINFO) + pH264Video->sps_length-4);
	ZeroMemory((PVOID)(videoInfo), sizeof(MPEG2VIDEOINFO) + pH264Video->sps_length-4);
	cmt->bFixedSizeSamples = 1;
	cmt->SetTemporalCompression( FALSE );
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(videoInfo), sizeof(VIDEOINFOHEADER2));
    //cmt->SetFormatType(&FORMAT_VideoInfo2);
	cmt->SetFormatType(&FORMAT_MPEG2Video);
    videoInfo->hdr.rcSource.right  = pH264Video->width;
    videoInfo->hdr.rcSource.bottom = pH264Video->height;
    videoInfo->hdr.rcTarget.right  = pH264Video->width;
    videoInfo->hdr.rcTarget.bottom = pH264Video->height;
    videoInfo->hdr.dwBitRate       = 0;
	videoInfo->hdr.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
	videoInfo->hdr.bmiHeader.biWidth = pH264Video->width;
	videoInfo->hdr.bmiHeader.biHeight= pH264Video->height;
	videoInfo->hdr.bmiHeader.biPlanes = 1;
	videoInfo->hdr.bmiHeader.biBitCount = 0;
	videoInfo->hdr.bmiHeader.biCompression = H264FOURCC;
	videoInfo->hdr.bmiHeader.biSizeImage = 0;
	videoInfo->hdr.bmiHeader.biClrUsed = 0;//0x73; //DIBSIZE(videoInfo->bmiHeader); //6619248
    videoInfo->hdr.bmiHeader.biXPelsPerMeter = 1;
    videoInfo->hdr.bmiHeader.biYPelsPerMeter = 1;
	//videoInfo->cbSequenceHeader = pH264Video->sps_length;
    //CopyMemory((PVOID)videoInfo->dwSequenceHeader,
			 //  (PVOID)pH264Video->sps,  pH264Video->sps_length);
	videoInfo->cbSequenceHeader = 0;
	videoInfo->dwProfile = pH264Video->profile;
	videoInfo->dwLevel = pH264Video->level;
	videoInfo->dwFlags = pH264Video->code_size;

	// Determine the aspect ratio
	//guess ar ratio
	{
		float r_min=100, rr, ratio_tbl[4]={ (float)1.0/1, (float)4.0/3, (float)16.0/9, (float)20.0/11 };
		int i, ri;
		rr =  (float)pH264Video->width/(pH264Video->height-8);
		for ( i=0; i<4; i++ )
		{
			float dif = ( rr-ratio_tbl[i] );
			if ( dif < 0 ) dif = -dif;
			if ( dif < r_min )
			{
				ri = i; r_min = dif;
			}
		}

		switch (ri+1)
		{
			case 1: // SAR=1:1
				videoInfo->hdr.dwPictAspectRatioX = 1;
				videoInfo->hdr.dwPictAspectRatioY = 1;
				break;
			case 2: // DAR=3/4
				videoInfo->hdr.dwPictAspectRatioX = 4;
				videoInfo->hdr.dwPictAspectRatioY = 3;
				break;
			case 3: // DAR=9/16
				videoInfo->hdr.dwPictAspectRatioX = 16;
				videoInfo->hdr.dwPictAspectRatioY = 9;
				break;
			case 4: 
				videoInfo->hdr.dwPictAspectRatioX = 20;
				videoInfo->hdr.dwPictAspectRatioY = 11;
				break;
		}
	}
	{
		if ( pH264Video->frame_rate_nomi && pH264Video->frame_rate_deno ) 
			videoInfo->hdr.AvgTimePerFrame = (LONGLONG)10000000*pH264Video->frame_rate_deno/pH264Video->frame_rate_nomi;  //10000000/frame_rate; (400000)
		else	
			videoInfo->hdr.AvgTimePerFrame = 10000000/25;

		videoInfo->hdr.dwInterlaceFlags = pH264Video->progressive; 
	}
    return S_OK;
}

HRESULT CPushReader::SetH264VideoMediaType4Cyberlink(CMediaType *cmt, H264_VIDEO *pH264Video )
{
    cmt->majortype = MEDIATYPE_Video;
	cmt->subtype = CLSID_MpegH264; 

	const DWORD H264FOURCC = 0x34363248; //DWORD('H264');

	//VIDEOINFOHEADER2 *videoInfo = (VIDEOINFOHEADER2*)cmt->AllocFormatBuffer( sizeof(VIDEOINFOHEADER2)+pH264Video->sps_length-4 );
	 MPEG2VIDEOINFO *videoInfo = // This macro finds the pointer to the last element in the sedhdr block to determine size
        //(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(FIELD_OFFSET(MPEG2VIDEOINFO, dwSequenceHeader[pMpegVideo->lActualHeaderLen]));
		// There's already a DWORD in the block for the sequence header, so subtract 4 bytes
		(MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(sizeof(MPEG2VIDEOINFO) + pH264Video->sps_length-4);
	ZeroMemory((PVOID)(videoInfo), sizeof(MPEG2VIDEOINFO) + pH264Video->sps_length-4);
	cmt->bFixedSizeSamples = 0;
	cmt->SetTemporalCompression( FALSE );
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(videoInfo), sizeof(VIDEOINFOHEADER2));
    //cmt->SetFormatType(&FORMAT_VideoInfo2);
	cmt->SetFormatType(&FORMAT_MPEG2Video);

    videoInfo->hdr.rcSource.right  = pH264Video->width;
    videoInfo->hdr.rcSource.bottom = pH264Video->height; 
	videoInfo->hdr.rcTarget.right  = pH264Video->width;
    videoInfo->hdr.rcTarget.bottom = pH264Video->height; 
    videoInfo->hdr.dwBitRate       = 0;
	videoInfo->hdr.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
	videoInfo->hdr.bmiHeader.biWidth = pH264Video->width;
	videoInfo->hdr.bmiHeader.biHeight= pH264Video->height;
	videoInfo->hdr.bmiHeader.biPlanes = 1;
	videoInfo->hdr.bmiHeader.biBitCount = 0;
	videoInfo->hdr.bmiHeader.biCompression = H264FOURCC;
	videoInfo->hdr.bmiHeader.biSizeImage = 0;
	videoInfo->hdr.bmiHeader.biClrUsed = 4;//0x73; //DIBSIZE(videoInfo->bmiHeader); //6619248
    videoInfo->hdr.bmiHeader.biXPelsPerMeter = 1;
    videoInfo->hdr.bmiHeader.biYPelsPerMeter = 1;
	videoInfo->dwProfile = pH264Video->profile;
	videoInfo->dwLevel = pH264Video->level;
	videoInfo->dwFlags = pH264Video->code_size;;
	videoInfo->cbSequenceHeader = pH264Video->sps_length;
	CopyMemory((PVOID)videoInfo->dwSequenceHeader,
			   (PVOID)(pH264Video->sps),  pH264Video->sps_length);
	//guess ration
	{
		float r_min=100, rr, ratio_tbl[4]={ (float)1.0/1, (float)4.0/3, (float)16.0/9, (float)20.0/11 };
		int i, ri;
		rr =  (float)pH264Video->width/(pH264Video->height-8);
		for ( i=0; i<4; i++ )
		{
			float dif = ( rr-ratio_tbl[i] );
			if ( dif < 0 ) dif = -dif;
			if ( dif < r_min )
			{
				ri = i; r_min = dif;
			}
		}

		switch (ri+1)
		{
			case 1: // SAR=1:1
				videoInfo->hdr.dwPictAspectRatioX = 1;
				videoInfo->hdr.dwPictAspectRatioY = 1;
				break;
			case 2: // DAR=3/4
				videoInfo->hdr.dwPictAspectRatioX = 4;
				videoInfo->hdr.dwPictAspectRatioY = 3;
				break;
			case 3: // DAR=9/16
				videoInfo->hdr.dwPictAspectRatioX = 16;
				videoInfo->hdr.dwPictAspectRatioY = 9;
				break;
			case 4: 
				videoInfo->hdr.dwPictAspectRatioX = 20;
				videoInfo->hdr.dwPictAspectRatioY = 11;
				break;
		}
	}
	{
		if ( pH264Video->frame_rate_nomi && pH264Video->frame_rate_deno ) 
			videoInfo->hdr.AvgTimePerFrame = (LONGLONG)10000000*pH264Video->frame_rate_deno/pH264Video->frame_rate_nomi;  //10000000/frame_rate; (400000)
		else	
			videoInfo->hdr.AvgTimePerFrame = 10000000/25;

		videoInfo->hdr.dwInterlaceFlags = pH264Video->progressive; 
	}
    return S_OK;
}

//ZQ.
HRESULT CPushReader::SetH264VC1VideoMediaType(CMediaType *cmt, H264_VIDEO *pH264Video )
{
    cmt->majortype = MEDIATYPE_Video;
	cmt->subtype = CLSID_MpegH264VC1;
	const DWORD AVC1FOURCC = 0x31435641; //DWORD('AVC1'); 0x31637661;  //DWORD('avc1');
	unsigned char extra_buf[256];
	int extra_bytes = MakeVC1ExtraData( pH264Video->sps, extra_buf, pH264Video->sps_length );

	MPEG2VIDEOINFO *videoInfo = (MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(sizeof(MPEG2VIDEOINFO) + extra_bytes-4);
	ZeroMemory((PVOID)(videoInfo), sizeof(MPEG2VIDEOINFO) + extra_bytes-4);
	cmt->bFixedSizeSamples = 0;
	cmt->SetTemporalCompression( FALSE );
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(videoInfo), sizeof(VIDEOINFOHEADER2));
    //cmt->SetFormatType(&FORMAT_VideoInfo2);
	cmt->SetFormatType(&FORMAT_MPEG2Video);

    videoInfo->hdr.rcSource.right  = pH264Video->width;
    videoInfo->hdr.rcSource.bottom = pH264Video->height; 
    videoInfo->hdr.rcTarget.right  = pH264Video->width;
    videoInfo->hdr.rcTarget.bottom = pH264Video->height;
    videoInfo->hdr.dwBitRate       = 0;
	videoInfo->hdr.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
	videoInfo->hdr.bmiHeader.biWidth = pH264Video->width;
	videoInfo->hdr.bmiHeader.biHeight= pH264Video->height;
	videoInfo->hdr.bmiHeader.biPlanes = 1;
	videoInfo->hdr.bmiHeader.biBitCount = 24;
	videoInfo->hdr.bmiHeader.biCompression = AVC1FOURCC;
	videoInfo->hdr.bmiHeader.biSizeImage = 0;
    videoInfo->hdr.bmiHeader.biXPelsPerMeter = 1;
    videoInfo->hdr.bmiHeader.biYPelsPerMeter = 1;
	videoInfo->dwProfile = pH264Video->profile;
	videoInfo->dwLevel = pH264Video->level;
	videoInfo->dwFlags = pH264Video->code_size;
	videoInfo->cbSequenceHeader = extra_bytes;
    CopyMemory((PVOID)videoInfo->dwSequenceHeader, (PVOID)extra_buf, extra_bytes );
	//guess ration
	{
		float r_min=100, rr, ratio_tbl[4]={ (float)1.0/1, (float)4.0/3, (float)16.0/9, (float)20.0/11 };
		int i, ri;
		rr =  (float)pH264Video->width/(pH264Video->height-8);
		for ( i=0; i<4; i++ )
		{
			float dif = ( rr-ratio_tbl[i] );
			if ( dif < 0 ) dif = -dif;
			if ( dif < r_min )
			{
				ri = i; r_min = dif;
			}
		}

		switch (ri+1)
		{
			case 1: // SAR=1:1
				videoInfo->hdr.dwPictAspectRatioX = 1;
				videoInfo->hdr.dwPictAspectRatioY = 1;
				break;
			case 2: // DAR=3/4
				videoInfo->hdr.dwPictAspectRatioX = 4;
				videoInfo->hdr.dwPictAspectRatioY = 3;
				break;
			case 3: // DAR=9/16
				videoInfo->hdr.dwPictAspectRatioX = 16;
				videoInfo->hdr.dwPictAspectRatioY = 9;
				break;
			case 4: 
				videoInfo->hdr.dwPictAspectRatioX = 20*16;
				videoInfo->hdr.dwPictAspectRatioY = 11*16;
				break;
		}
	}
	if ( 0 ) {
		if ( pH264Video->frame_rate_nomi && pH264Video->frame_rate_deno ) 
			videoInfo->hdr.AvgTimePerFrame = (LONGLONG)10000000*pH264Video->frame_rate_deno/pH264Video->frame_rate_nomi;  //10000000/frame_rate; (400000)
		else	
			videoInfo->hdr.AvgTimePerFrame = 10000000/25;

		videoInfo->hdr.dwInterlaceFlags = pH264Video->progressive; 
	}
    return S_OK;

	///////////////////////////////////////////////////////////////////////////////////////////////
}


HRESULT CPushReader::SetVC1VideoMediaType(CMediaType *cmt, H264_VIDEO *pH264Video )
{
	typedef struct tagMPEG2VIDEOINFO 
	{
		VIDEOINFOHEADER2    hdr;
	} _MPEG2VIDEOINFO;

    cmt->majortype = MEDIATYPE_Video;
    cmt->subtype = CLSID_MpegVC1; 

	const DWORD VC1FOURCC = 0x312d4356; //DWORD('VC-1');

	int size = sizeof(_MPEG2VIDEOINFO);// + pH264Video->sps_length;
	_MPEG2VIDEOINFO *videoInfo = (_MPEG2VIDEOINFO*)cmt->AllocFormatBuffer(size);
	ZeroMemory((PVOID)(videoInfo), size);
	cmt->bFixedSizeSamples = 1;
	cmt->SetTemporalCompression( FALSE );
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(videoInfo), sizeof(VIDEOINFOHEADER2));
	cmt->SetFormatType(&FORMAT_VIDEOINFO2);
    videoInfo->hdr.rcSource.right  = pH264Video->width;
    videoInfo->hdr.rcSource.bottom = pH264Video->height;
    videoInfo->hdr.rcTarget.right  = pH264Video->width;
    videoInfo->hdr.rcTarget.bottom = pH264Video->height;
    videoInfo->hdr.dwBitRate       = 0;
	videoInfo->hdr.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
	videoInfo->hdr.bmiHeader.biWidth = pH264Video->width;
	videoInfo->hdr.bmiHeader.biHeight= pH264Video->height;
	videoInfo->hdr.bmiHeader.biPlanes = 1;
	videoInfo->hdr.bmiHeader.biBitCount = 0;
	videoInfo->hdr.bmiHeader.biCompression = VC1FOURCC;
	videoInfo->hdr.bmiHeader.biSizeImage = 0;
	videoInfo->hdr.bmiHeader.biClrUsed = 843666497;
    videoInfo->hdr.bmiHeader.biXPelsPerMeter = 0;
    videoInfo->hdr.bmiHeader.biYPelsPerMeter = 0;
	//videoInfo->cbSequenceHeader = pH264Video->sps_length;
    //CopyMemory((PVOID)videoInfo->dwSequenceHeader,
			 //  (PVOID)pH264Video->sps,  pH264Video->sps_length);

	//videoInfo->cbSequenceHeader = 0;
	//videoInfo->dwProfile = pH264Video->profile;
	//videoInfo->dwLevel = pH264Video->level;

	// Determine the aspect ratio
	//guess ar ratio
	{
		float r_min=100, rr, ratio_tbl[4]={ (float)1.0/1, (float)4.0/3, (float)16.0/9, (float)20.0/11 };
		int i, ri;
		rr =  (float)pH264Video->width/(pH264Video->height-8);
		for ( i=0; i<4; i++ )
		{
			float dif = ( rr-ratio_tbl[i] );
			if ( dif < 0 ) dif = -dif;
			if ( dif < r_min )
			{
				ri = i; r_min = dif;
			}
		}

		switch (ri+1)
		{
			case 1: // SAR=1:1
				videoInfo->hdr.dwPictAspectRatioX = 1;
				videoInfo->hdr.dwPictAspectRatioY = 1;
				break;
			case 2: // DAR=3/4
				videoInfo->hdr.dwPictAspectRatioX = 4;
				videoInfo->hdr.dwPictAspectRatioY = 3;
				break;
			case 3: // DAR=9/16
				videoInfo->hdr.dwPictAspectRatioX = 16;
				videoInfo->hdr.dwPictAspectRatioY = 9;
				break;
			case 4: 
				videoInfo->hdr.dwPictAspectRatioX = 20;
				videoInfo->hdr.dwPictAspectRatioY = 11;
				break;
		}
	}
	{
		if ( pH264Video->frame_rate_nomi && pH264Video->frame_rate_deno ) 
			videoInfo->hdr.AvgTimePerFrame = (LONGLONG)10000000*pH264Video->frame_rate_deno/pH264Video->frame_rate_nomi;  //10000000/frame_rate; (400000)
		else	
			videoInfo->hdr.AvgTimePerFrame = 10000000/25;
	}
    return S_OK;

}


HRESULT CPushReader::SetWVC1VideoMediaType(CMediaType *cmt, H264_VIDEO *pH264Video )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

	const DWORD WVC1FOURCC = 0x31435657;// [WVC1]

    cmt->majortype = MEDIATYPE_Video;
	cmt->SetType( &MEDIATYPE_Video ); 
    cmt->subtype = CLSID_MpegWVC1; 
	cmt->SetFormatType( &FORMAT_VideoInfo );

	cmt->SetTemporalCompression( 0);
	cmt->lSampleSize = 1;
	cmt->bFixedSizeSamples = 0;

	int extra_data_size;
	unsigned char *extra_data;
	extra_data_size = GetWVC1ExtraData( pH264Video->sps, pH264Video->sps_length, &extra_data );
	//extra_data_size = pH264Video->sps_length;
	//extra_data = pH264Video->sps;
	int size = sizeof(_VIDEOINFOHEADER) + extra_data_size;

	 _VIDEOINFOHEADER*  VideoHdr = ( _VIDEOINFOHEADER*)cmt->AllocFormatBuffer( size );
    if ( VideoHdr == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(VideoHdr), size  );
    VideoHdr->rcSource.right  = pH264Video->width;
    VideoHdr->rcSource.bottom = pH264Video->height;
    VideoHdr->rcTarget.right  = pH264Video->width;
    VideoHdr->rcTarget.bottom = pH264Video->height;
    VideoHdr->dwBitRate       = 0;
	VideoHdr->bmiHeader.biSize = sizeof(_BITMAPINFOHEADER)+extra_data_size;
	VideoHdr->bmiHeader.biWidth = pH264Video->width;
	VideoHdr->bmiHeader.biHeight= pH264Video->height;
	VideoHdr->bmiHeader.biPlanes = 1;
	VideoHdr->bmiHeader.biBitCount = 24;
	VideoHdr->bmiHeader.biCompression = WVC1FOURCC;
	VideoHdr->bmiHeader.biXPelsPerMeter = 1;
	VideoHdr->bmiHeader.biYPelsPerMeter = 1;
	VideoHdr->bmiHeader.biSizeImage = pH264Video->width*pH264Video->height*3;//DIBSIZE(VideoHdr->bmiHeader);;
	if ( pH264Video->frame_rate_nomi && pH264Video->frame_rate_deno ) 
		VideoHdr->AvgTimePerFrame = (LONGLONG)10000000*pH264Video->frame_rate_deno/pH264Video->frame_rate_nomi;  //10000000/frame_rate; (400000)
	else	
		VideoHdr->AvgTimePerFrame = 10000000/25;

	if (extra_data_size)
		CopyMemory((char*)VideoHdr+sizeof(_VIDEOINFOHEADER), (PVOID)extra_data,  extra_data_size );


	return S_OK;
}


/*
HRESULT CPushReader::SetWVC1VideoMediaType(CMediaType *cmt, H264_VIDEO *pH264Video )
{
	const DWORD WVC1FOURCC = 0x31435657;// [WVC1]

    cmt->majortype = MEDIATYPE_Video;
	cmt->SetType( &MEDIATYPE_Video ); 
    cmt->subtype = CLSID_MpegWVC1; 
	cmt->SetFormatType( &FORMAT_VIDEOINFO2 );

	cmt->SetTemporalCompression( 0);
	cmt->lSampleSize = 1;
	cmt->bFixedSizeSamples = 0;

	int extra_data_size;
	unsigned char *extra_data;
	extra_data_size = GetWVC1ExtraData( pH264Video->sps, pH264Video->sps_length, &extra_data );
	int size = sizeof(VIDEOINFOHEADER2) + extra_data_size;

	 VIDEOINFOHEADER2*  VideoHdr = (VIDEOINFOHEADER2*)cmt->AllocFormatBuffer( size );
    if ( VideoHdr == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(VideoHdr), size  );
    //VideoHdr->rcSource.right  = pH264Video->width;
    //VideoHdr->rcSource.bottom = pH264Video->height;
    //VideoHdr->rcTarget.right  = pH264Video->width;
    //VideoHdr->rcTarget.bottom = pH264Video->height;
    VideoHdr->dwBitRate       = 0;
	VideoHdr->bmiHeader.biSize = sizeof(BITMAPINFOHEADER)+extra_data_size;
	VideoHdr->bmiHeader.biWidth = pH264Video->width;
	VideoHdr->bmiHeader.biHeight= pH264Video->height;
	VideoHdr->bmiHeader.biPlanes = 1;
	VideoHdr->bmiHeader.biBitCount = 24;
	VideoHdr->bmiHeader.biCompression = WVC1FOURCC;
	VideoHdr->bmiHeader.biXPelsPerMeter = 1;
	VideoHdr->bmiHeader.biYPelsPerMeter = 1;
	VideoHdr->bmiHeader.biSizeImage = pH264Video->width*pH264Video->height*3;//DIBSIZE(VideoHdr->bmiHeader);;
	VideoHdr->dwPictAspectRatioX = pH264Video->width;
	VideoHdr->dwPictAspectRatioY = pH264Video->height;
	if ( pH264Video->frame_rate_nomi && pH264Video->frame_rate_deno ) 
		VideoHdr->AvgTimePerFrame = (LONGLONG)10000000*pH264Video->frame_rate_deno/pH264Video->frame_rate_nomi;  //10000000/frame_rate; (400000)
	else	
		VideoHdr->AvgTimePerFrame = 10000000/25;

	if (extra_data_size)
		CopyMemory((char*)VideoHdr+sizeof(VIDEOINFOHEADER2), (PVOID)extra_data,  extra_data_size );


	return S_OK;
}
*/

HRESULT CPushReader::SetFLV1VideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

	const DWORD FLV1FOURCC = 0x31564c46; //DWORD('FLV1');
	cmt->SetType( &MEDIATYPE_Video ); 
	cmt->subtype = MEDIASUBTYPE_FLV1;
	cmt->SetFormatType( &FORMAT_VideoInfo );

	cmt->SetTemporalCompression( 0);
	cmt->lSampleSize = 0;
	cmt->bFixedSizeSamples = 0;

	 _VIDEOINFOHEADER*  VideoHdr = ( _VIDEOINFOHEADER*)cmt->AllocFormatBuffer( sizeof(_VIDEOINFOHEADER) );
    if ( VideoHdr == NULL) {
        return E_OUTOFMEMORY;
    }

	// reset the header's memory
	ZeroMemory((PVOID)(VideoHdr), sizeof(_VIDEOINFOHEADER)  );

    VideoHdr->rcSource.right  = pMpegVideo->width;
    VideoHdr->rcSource.bottom = pMpegVideo->height;
    VideoHdr->rcTarget.right  = pMpegVideo->width;
    VideoHdr->rcTarget.bottom = pMpegVideo->height;
    VideoHdr->dwBitRate       = 0;
	if ( pMpegVideo->frame_rate_deno > 0 && pMpegVideo->frame_rate_nomi > pMpegVideo->frame_rate_deno )
		VideoHdr->AvgTimePerFrame = (LONGLONG)(UNITS/(pMpegVideo->frame_rate_nomi/pMpegVideo->frame_rate_deno));
	VideoHdr->bmiHeader.biSize = sizeof(_BITMAPINFOHEADER);
	VideoHdr->bmiHeader.biWidth = pMpegVideo->width;
	VideoHdr->bmiHeader.biHeight= pMpegVideo->height;
	VideoHdr->bmiHeader.biPlanes = 1;
	VideoHdr->bmiHeader.biBitCount = 0;
	VideoHdr->bmiHeader.biCompression = FLV1FOURCC;
	VideoHdr->bmiHeader.biSizeImage = DIBSIZE(VideoHdr->bmiHeader);

    return S_OK;
}

HRESULT CPushReader::SetVP6FVideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

	const DWORD FLV1FOURCC = 0x34564c46; //DWORD('FLV4');
	cmt->SetType( &MEDIATYPE_Video ); 
	cmt->subtype = MEDIASUBTYPE_FLV4;
	cmt->SetFormatType( &FORMAT_VideoInfo );

	cmt->SetTemporalCompression( 0);
	cmt->lSampleSize = 0;
	cmt->bFixedSizeSamples = 0;

	 _VIDEOINFOHEADER*  VideoHdr = ( _VIDEOINFOHEADER*)cmt->AllocFormatBuffer( sizeof(_VIDEOINFOHEADER) );
    if ( VideoHdr == NULL) {
        return E_OUTOFMEMORY;
    }

	// reset the header's memory
	ZeroMemory((PVOID)(VideoHdr), sizeof(_VIDEOINFOHEADER)  );

    VideoHdr->rcSource.right  = pMpegVideo->width;
    VideoHdr->rcSource.bottom = pMpegVideo->height;
    VideoHdr->rcTarget.right  = pMpegVideo->width;
    VideoHdr->rcTarget.bottom = pMpegVideo->height;
    VideoHdr->dwBitRate       = 0;
	if ( pMpegVideo->frame_rate_deno > 0 && pMpegVideo->frame_rate_nomi > pMpegVideo->frame_rate_deno )
		VideoHdr->AvgTimePerFrame = (LONGLONG)(UNITS/(pMpegVideo->frame_rate_nomi/pMpegVideo->frame_rate_deno));
	VideoHdr->bmiHeader.biSize = sizeof(_BITMAPINFOHEADER);
	VideoHdr->bmiHeader.biWidth = pMpegVideo->width;
	VideoHdr->bmiHeader.biHeight= pMpegVideo->height;
	VideoHdr->bmiHeader.biPlanes = 1;
	VideoHdr->bmiHeader.biBitCount = 0;
	VideoHdr->bmiHeader.biCompression = FLV1FOURCC;
	VideoHdr->bmiHeader.biSizeImage = DIBSIZE(VideoHdr->bmiHeader);

    return S_OK;
}

HRESULT CPushReader::SetRealVideoMediaType( CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

	const DWORD RV40FOURCC = 0x30345652; //DWORD('RV40');
	cmt->SetType( &MEDIATYPE_Video ); 
	cmt->subtype = MEDIASUBTYPE_RV40;
	cmt->SetFormatType( &FORMAT_VideoInfo );

	cmt->SetTemporalCompression( 0);
	cmt->lSampleSize = 0;
	cmt->bFixedSizeSamples = 0;

	 _VIDEOINFOHEADER*  VideoHdr = ( _VIDEOINFOHEADER*)cmt->AllocFormatBuffer( sizeof(_VIDEOINFOHEADER) );
    if ( VideoHdr == NULL) {
        return E_OUTOFMEMORY;
    }

	// reset the header's memory
	ZeroMemory((PVOID)(VideoHdr), sizeof(_VIDEOINFOHEADER)  );

    VideoHdr->rcSource.right  = pMpegVideo->width;
    VideoHdr->rcSource.bottom = pMpegVideo->height;
    VideoHdr->rcTarget.right  = pMpegVideo->width;
    VideoHdr->rcTarget.bottom = pMpegVideo->height;
    VideoHdr->dwBitRate       = 0;
	if ( pMpegVideo->frame_rate_deno > 0 && pMpegVideo->frame_rate_nomi > pMpegVideo->frame_rate_deno )
		VideoHdr->AvgTimePerFrame = (LONGLONG)(UNITS/(pMpegVideo->frame_rate_nomi/pMpegVideo->frame_rate_deno));
	VideoHdr->AvgTimePerFrame  /= 2;
	VideoHdr->bmiHeader.biSize = sizeof(_BITMAPINFOHEADER);
	VideoHdr->bmiHeader.biWidth = pMpegVideo->width;
	VideoHdr->bmiHeader.biHeight= pMpegVideo->height;
	VideoHdr->bmiHeader.biPlanes = 1;
	VideoHdr->bmiHeader.biBitCount = 0;
	VideoHdr->bmiHeader.biCompression = RV40FOURCC;
	VideoHdr->bmiHeader.biSizeImage = DIBSIZE(VideoHdr->bmiHeader);

    return S_OK;
}
HRESULT CPushReader::SetWMV7VideoMediaType(CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

 	const DWORD WMA3FOURCC = 0x31564d57; //WMV1
	cmt->SetType( &MEDIATYPE_Video ); 
	cmt->subtype = CLSID_MpegWMV1;
	cmt->SetFormatType(&FORMAT_VideoInfo);
	

	cmt->SetTemporalCompression( 1);
	cmt->lSampleSize = 0;
	cmt->bFixedSizeSamples = 0;
	int size = sizeof(_VIDEOINFOHEADER) + pMpegVideo->actual_header_length;
	_VIDEOINFOHEADER *videoInfo = (_VIDEOINFOHEADER*)cmt->AllocFormatBuffer( size );
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(videoInfo), sizeof(_VIDEOINFOHEADER));

    videoInfo->rcSource.right  = pMpegVideo->width;
    videoInfo->rcSource.bottom = pMpegVideo->height;
    videoInfo->rcTarget.right  = pMpegVideo->width;
    videoInfo->rcTarget.bottom = pMpegVideo->height;

    videoInfo->dwBitRate       = 1368000;//8000000;
	videoInfo->bmiHeader.biSize = sizeof(BITMAPINFOHEADER)+pMpegVideo->actual_header_length;
	videoInfo->bmiHeader.biWidth = pMpegVideo->width;
	videoInfo->bmiHeader.biHeight= pMpegVideo->height;
	videoInfo->bmiHeader.biPlanes = 1;
	videoInfo->bmiHeader.biBitCount = 24;
	videoInfo->bmiHeader.biCompression = WMA3FOURCC;
	videoInfo->bmiHeader.biSizeImage = pMpegVideo->width*pMpegVideo->height*24/8;
	videoInfo->bmiHeader.biClrUsed = 0;
	videoInfo->bmiHeader.biXPelsPerMeter = 0;
	videoInfo->bmiHeader.biYPelsPerMeter = 0;
	if ( pMpegVideo->frame_rate_nomi && pMpegVideo->frame_rate_deno ) 
		videoInfo->AvgTimePerFrame = (LONGLONG)10000000*pMpegVideo->frame_rate_deno/pMpegVideo->frame_rate_nomi;  
	if ( pMpegVideo->actual_header_length )
		memcpy( (char*)videoInfo+size-pMpegVideo->actual_header_length, pMpegVideo->raw_header, pMpegVideo->actual_header_length );

    return S_OK;
}

HRESULT CPushReader::SetWMV8VideoMediaType(CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

 	const DWORD WMA3FOURCC = 0x32564d57; //WMV2
	cmt->SetType( &MEDIATYPE_Video ); 
	cmt->subtype = CLSID_MpegWMV2;
	cmt->SetFormatType(&FORMAT_VideoInfo);
	

	cmt->SetTemporalCompression( 1);
	cmt->lSampleSize = 0;
	cmt->bFixedSizeSamples = 0;
	int size = sizeof(_VIDEOINFOHEADER) + pMpegVideo->actual_header_length;
	_VIDEOINFOHEADER *videoInfo = (_VIDEOINFOHEADER*)cmt->AllocFormatBuffer( size );
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(videoInfo), sizeof(_VIDEOINFOHEADER));

    videoInfo->rcSource.right  = pMpegVideo->width;
    videoInfo->rcSource.bottom = pMpegVideo->height;
    videoInfo->rcTarget.right  = pMpegVideo->width;
    videoInfo->rcTarget.bottom = pMpegVideo->height;

    videoInfo->dwBitRate       = 1368000;//8000000;
	videoInfo->bmiHeader.biSize = sizeof(BITMAPINFOHEADER)+pMpegVideo->actual_header_length;
	videoInfo->bmiHeader.biWidth = pMpegVideo->width;
	videoInfo->bmiHeader.biHeight= pMpegVideo->height;
	videoInfo->bmiHeader.biPlanes = 1;
	videoInfo->bmiHeader.biBitCount = 24;
	videoInfo->bmiHeader.biCompression = WMA3FOURCC;
	videoInfo->bmiHeader.biSizeImage = pMpegVideo->width*pMpegVideo->height*24/8;
	videoInfo->bmiHeader.biClrUsed = 0;
	videoInfo->bmiHeader.biXPelsPerMeter = 0;
	videoInfo->bmiHeader.biYPelsPerMeter = 0;
	if ( pMpegVideo->frame_rate_nomi && pMpegVideo->frame_rate_deno ) 
		videoInfo->AvgTimePerFrame = (LONGLONG)10000000*pMpegVideo->frame_rate_deno/pMpegVideo->frame_rate_nomi;  
	if ( pMpegVideo->actual_header_length )
		memcpy( (char*)videoInfo+size-pMpegVideo->actual_header_length, pMpegVideo->raw_header, pMpegVideo->actual_header_length );

    return S_OK;
}


HRESULT CPushReader::SetWMV9VideoMediaType(CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

 	const DWORD WMA3FOURCC = 0x33564d57; //0x33564d57 WMV3
	cmt->SetType( &MEDIATYPE_Video ); 
	cmt->subtype = CLSID_MpegWMV3;
	cmt->SetFormatType(&FORMAT_VideoInfo);
	

	cmt->SetTemporalCompression( 1);
	cmt->lSampleSize = 0;
	cmt->bFixedSizeSamples = 0;
	int size = sizeof(_VIDEOINFOHEADER) + pMpegVideo->actual_header_length;
	_VIDEOINFOHEADER *videoInfo = (_VIDEOINFOHEADER*)cmt->AllocFormatBuffer( size );
    if (videoInfo == NULL) {
        return E_OUTOFMEMORY;
    }
	ZeroMemory((PVOID)(videoInfo), sizeof(_VIDEOINFOHEADER));

    videoInfo->rcSource.right  = pMpegVideo->width;
    videoInfo->rcSource.bottom = pMpegVideo->height;
    videoInfo->rcTarget.right  = pMpegVideo->width;
    videoInfo->rcTarget.bottom = pMpegVideo->height;

    videoInfo->dwBitRate       = 8000000;
	videoInfo->bmiHeader.biSize = sizeof(BITMAPINFOHEADER)+pMpegVideo->actual_header_length;
	videoInfo->bmiHeader.biWidth = pMpegVideo->width;
	videoInfo->bmiHeader.biHeight= pMpegVideo->height;
	videoInfo->bmiHeader.biPlanes = 1;
	videoInfo->bmiHeader.biBitCount = 24;
	videoInfo->bmiHeader.biCompression = WMA3FOURCC;
	videoInfo->bmiHeader.biSizeImage = pMpegVideo->width*pMpegVideo->height*24/8;
	videoInfo->bmiHeader.biClrUsed = 0;
	videoInfo->bmiHeader.biXPelsPerMeter = 0;
	videoInfo->bmiHeader.biYPelsPerMeter = 0;
	if ( pMpegVideo->frame_rate_nomi && pMpegVideo->frame_rate_deno ) 
		videoInfo->AvgTimePerFrame = (LONGLONG)10000000*pMpegVideo->frame_rate_deno/pMpegVideo->frame_rate_nomi;  
	if ( pMpegVideo->actual_header_length )
		memcpy( (char*)videoInfo+size-pMpegVideo->actual_header_length, pMpegVideo->raw_header, pMpegVideo->actual_header_length );

    return S_OK;
}


HRESULT CPushReader::SetMpeg4VideoMediaType(CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

	const DWORD XVIDFOURCC = 0x44495658; //DWORD('XVID');

	cmt->SetType( &MEDIATYPE_Video );
	cmt->subtype = CLSID_MpegXVID;;
	cmt->SetFormatType( &FORMAT_VideoInfo );

	cmt->SetTemporalCompression( 0);
	cmt->lSampleSize = 0;
	cmt->bFixedSizeSamples = 0;

	 _VIDEOINFOHEADER*  VideoHdr = ( _VIDEOINFOHEADER*)cmt->AllocFormatBuffer( sizeof(_VIDEOINFOHEADER) );
    if ( VideoHdr == NULL) {
        return E_OUTOFMEMORY;
    }

	// reset the header's memory
	ZeroMemory((PVOID)(VideoHdr), sizeof(_VIDEOINFOHEADER)  );

    VideoHdr->rcSource.right  = pMpegVideo->width;
    VideoHdr->rcSource.bottom = pMpegVideo->height;
    VideoHdr->rcTarget.right  = pMpegVideo->width;
    VideoHdr->rcTarget.bottom = pMpegVideo->height;
    VideoHdr->dwBitRate       = 0;
	VideoHdr->bmiHeader.biSize = sizeof(_BITMAPINFOHEADER);
	VideoHdr->bmiHeader.biWidth = pMpegVideo->width;
	VideoHdr->bmiHeader.biHeight= pMpegVideo->height;
	VideoHdr->bmiHeader.biPlanes = 1;
	VideoHdr->bmiHeader.biBitCount = 24;
	VideoHdr->bmiHeader.biCompression = XVIDFOURCC;
	VideoHdr->bmiHeader.biSizeImage = DIBSIZE(VideoHdr->bmiHeader);
	if ( pMpegVideo->frame_rate_nomi && pMpegVideo->frame_rate_deno ) 
		VideoHdr->AvgTimePerFrame = (LONGLONG)10000000*pMpegVideo->frame_rate_deno/pMpegVideo->frame_rate_nomi;  

    return S_OK;
}  

HRESULT CPushReader::SetXVIDMediaType(CMediaType *cmt, MPEG_VIDEO *pMpegVideo )
{
	typedef struct tagVIDEOINFOHEADER 
	{
		RECT            rcSource;          // The bit we really want to use
		RECT            rcTarget;          // Where the video should go
		DWORD           dwBitRate;         // Approximate bit data rate
		DWORD           dwBitErrorRate;    // Bit error rate for this stream
		REFERENCE_TIME  AvgTimePerFrame;   // Average time per frame (100ns units)
		BITMAPINFOHEADER bmiHeader;
	} _VIDEOINFOHEADER;

	DWORD XVIDFOURCC=0;

	//cmt->subtype = CLSID_MpegDixV;
	//const DWORD XVIDFOURCC = 0x30355844;// [DX50]
	//cmt->subtype = CLSID_MpegDX50;;
	if ( pMpegVideo->profile <=0 && pMpegVideo->ver_id <= 0 )
	{
		XVIDFOURCC = 0x44495658; //DWORD('XVID');
		cmt->subtype = CLSID_MpegXVID;
	} else
	if ( pMpegVideo->profile == 0xf0 ) //simple_adv;
	{
		XVIDFOURCC = 0x44495658; //DWORD('XVID');
		cmt->subtype = CLSID_MpegXVID;

	} else
	if ( pMpegVideo->profile == 0x00 ) //simple 
	{
		XVIDFOURCC = 0x44495658; //DWORD('XVID');
		cmt->subtype = CLSID_MpegXVID;
	}

	if ( XVIDFOURCC == 0 && pMpegVideo->ver_id == 2 )
	{
		XVIDFOURCC = 0x30355844; //[DX50] DivX5
		cmt->subtype = CLSID_MpegDX50;;
	} else
	if ( XVIDFOURCC == 0 && pMpegVideo->ver_id == 1 )
	{
		XVIDFOURCC = 0x58564944; //[DIVX] DivX4
		cmt->subtype = CLSID_MpegDixV;
	} else
	{
		XVIDFOURCC = 0x44495658; //DWORD('XVID');
		cmt->subtype = CLSID_MpegXVID;
	}


	cmt->SetType( &MEDIATYPE_Video ); 
	cmt->SetFormatType( &FORMAT_VideoInfo );

	cmt->SetTemporalCompression( 0);
	cmt->lSampleSize = 0;
	cmt->bFixedSizeSamples = 0;

	 _VIDEOINFOHEADER*  VideoHdr = ( _VIDEOINFOHEADER*)cmt->AllocFormatBuffer( sizeof(_VIDEOINFOHEADER) );
    if ( VideoHdr == NULL) {
        return E_OUTOFMEMORY;
    }

	// reset the header's memory
	ZeroMemory((PVOID)(VideoHdr), sizeof(_VIDEOINFOHEADER)  );

    VideoHdr->rcSource.right  = pMpegVideo->width;
    VideoHdr->rcSource.bottom = pMpegVideo->height;
    VideoHdr->rcTarget.right  = pMpegVideo->width;
    VideoHdr->rcTarget.bottom = pMpegVideo->height;
    //VideoHdr->dwBitRate       = 0x32c9c3; //pMpegVideo->BitRate;
	
	VideoHdr->bmiHeader.biSize = sizeof(_BITMAPINFOHEADER);
	VideoHdr->bmiHeader.biWidth = pMpegVideo->width;
	VideoHdr->bmiHeader.biHeight= pMpegVideo->height;
	VideoHdr->bmiHeader.biPlanes = 1;
	VideoHdr->bmiHeader.biBitCount = 24;
	VideoHdr->bmiHeader.biCompression = XVIDFOURCC;
	VideoHdr->bmiHeader.biSizeImage = DIBSIZE(VideoHdr->bmiHeader);

	if ( pMpegVideo->frame_rate_nomi && pMpegVideo->frame_rate_deno ) 
		VideoHdr->AvgTimePerFrame = (LONGLONG)10000000*pMpegVideo->frame_rate_deno/pMpegVideo->frame_rate_nomi;  

    return S_OK;
}

HRESULT CPushReader::SetSageDixVMediaType(CMediaType *cmt, MEDIA_VIDEO *pMediaVideo )
{
	return S_OK;
}
///////////////////////////////// AUDIO ////////////////////////////////////////
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
} __WAVEFORMATEX;
#define SIZEOF_WAVEFORMATEX  18 //ZQ don't use sizeof(_WAVEFORMATEX), as struct byte aligment casue a trouble

#define _WAVEFORMATEX WAVEFORMATEX

HRESULT CPushReader::SetAACAuidoMediaType( CMediaType *cmt, AAC_AUDIO *pAACAudio )
{
	_WAVEFORMATEX*   pWfx;	
	cmt->SetType( &MEDIATYPE_Audio ); 
	if ( pAACAudio->format == 1 )
	{
		cmt->SetSubtype( &MEDIASUBTYPE_AAC );
		cmt->SetFormatType(&FORMAT_WaveFormatEx);

		int extradata_size = pAACAudio->header_bytes;
		pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX + pAACAudio->header_bytes );
		memset( pWfx, 0, SIZEOF_WAVEFORMATEX  );
		pWfx->nSamplesPerSec = pAACAudio->samples_per_sec;
		pWfx->nChannels      = pAACAudio->channels;
		pWfx->wFormatTag     = 0xff;
		pWfx->nAvgBytesPerSec = 0;
		pWfx->nBlockAlign = 0;
		pWfx->wBitsPerSample = 0;
		pWfx->cbSize = extradata_size;
		memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, pAACAudio->u.atds_header, extradata_size );

	}
	else
	{
		cmt->subtype = MEDIASUBTYPE_LATM_AAC; 
		cmt->SetFormatType(&FORMAT_WaveFormatEx);

		int extradata_size = sizeof( pAACAudio->u.latm_header );
		pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX + extradata_size );
		memset( (char*)pWfx, 0, SIZEOF_WAVEFORMATEX + extradata_size  );
		pWfx->cbSize = extradata_size;
		pWfx->nSamplesPerSec = pAACAudio->samples_per_sec;
		pWfx->nChannels      = pAACAudio->channels;
		pWfx->wFormatTag     = 0x1ff;
		memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, pAACAudio->u.latm_header, extradata_size );

	}
	return S_OK;
}


HRESULT CPushReader::SetAACAuidoMediaType2( CMediaType *cmt, AAC_AUDIO *pAACAudio )
{
	_WAVEFORMATEX*   pWfx;	
	cmt->SetType( &MEDIATYPE_Audio ); 
	if ( pAACAudio->format == 1 ) //ADTS
	{
		pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX  );
		memset( pWfx, 0, SIZEOF_WAVEFORMATEX  );
		pWfx->cbSize = 0;
		pWfx->nSamplesPerSec = pAACAudio->samples_per_sec;
		pWfx->nChannels      = pAACAudio->channels;

		pWfx->wFormatTag = WAVE_FORMAT_MPEG_ADTS_AAC;
		cmt->SetSubtype( &MEDIASUBTYPE_MPEG_ADTS_AAC);
		cmt->SetFormatType(&FORMAT_WaveFormatEx);
	}
	else
	if ( pAACAudio->format == 2 ) //LOAS
	{
		int extradata_size = sizeof( pAACAudio->u.latm_header );
		pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX + extradata_size );
		memset( (char*)pWfx, 0, SIZEOF_WAVEFORMATEX + extradata_size  );
		pWfx->cbSize = extradata_size;
		pWfx->nSamplesPerSec = pAACAudio->samples_per_sec;
		pWfx->nChannels      = pAACAudio->channels;
		pWfx->wFormatTag = WAVE_FORMAT_MPEG_LOAS;
		memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, pAACAudio->u.latm_header, extradata_size );
		cmt->SetSubtype( &MEDIASUBTYPE_MPEG_LATM_AAC );
		cmt->SetFormatType(&FORMAT_WaveFormatEx);
	} else
	{
		pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX  );
		memset( pWfx, 0, SIZEOF_WAVEFORMATEX  );
		pWfx->cbSize = 0;
		pWfx->nSamplesPerSec = pAACAudio->samples_per_sec;
		pWfx->nChannels      = pAACAudio->channels;

		cmt->SetSubtype( &MEDIASUBTYPE_AAC );
		cmt->SetFormatType(&FORMAT_WaveFormatEx);
	}
	return S_OK;
}



HRESULT CPushReader::SetAC3AuidoMediaType( CMediaType *cmt, AC3_AUDIO *pAC3Audio )
{
	typedef	struct	
	{
		_WAVEFORMATEX   wfx;	
		unsigned char  BigEndian;
		unsigned char  bsid;
		unsigned char  lfeon;
		unsigned char  copyrightb;
		unsigned char  nAuxBitsCode;  /*  Aux bits per frame */	
	} TS_AC3WAV;

	TS_AC3WAV AC3Wav = {0};
	AC3Wav.wfx.cbSize =	sizeof(TS_AC3WAV) -	sizeof(_WAVEFORMATEX );
	AC3Wav.wfx.wFormatTag =	WAVE_FORMAT_DOLBY_AC3;
	AC3Wav.wfx.nSamplesPerSec = pAC3Audio->samples_per_sec;
	AC3Wav.wfx.nAvgBytesPerSec = pAC3Audio->avgbytes_per_sec;
	AC3Wav.wfx.nChannels = pAC3Audio->channels;
	AC3Wav.wfx.nBlockAlign = pAC3Audio->block_align;

	cmt->SetFormat((PBYTE)&AC3Wav, sizeof(TS_AC3WAV));
	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype(&MEDIASUBTYPE_DOLBY_AC3);
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}

HRESULT CPushReader::SetTAC3AuidoMediaType( CMediaType *cmt, AC3_AUDIO *pAC3Audio )
{
	typedef	struct	
	{
		_WAVEFORMATEX   wfx;	
		unsigned char  BigEndian;
		unsigned char  bsid;
		unsigned char  lfeon;
		unsigned char  copyrightb;
		unsigned char  nAuxBitsCode;  /*  Aux bits per frame */	
	} TS_AC3WAV;

	TS_AC3WAV AC3Wav = {0};
	AC3Wav.wfx.cbSize =	sizeof(TS_AC3WAV) -	sizeof(_WAVEFORMATEX);
	AC3Wav.wfx.wFormatTag =	WAVE_FORMAT_DOLBY_AC3;
	AC3Wav.wfx.nSamplesPerSec = pAC3Audio->samples_per_sec;
	AC3Wav.wfx.nAvgBytesPerSec = pAC3Audio->avgbytes_per_sec;
	AC3Wav.wfx.nChannels = pAC3Audio->channels;
	AC3Wav.wfx.nBlockAlign = pAC3Audio->block_align; 
	cmt->SetFormat((PBYTE)&AC3Wav, sizeof(TS_AC3WAV));

	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype(&MEDIASUBTYPE_DOLBY_TRUEHD); 
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}

HRESULT CPushReader::SetDTSAuidoMediaType( CMediaType *cmt, DTS_AUDIO *pDTSAudio )
{
	_WAVEFORMATEX   Wfx={0};
	Wfx.wFormatTag = WAVE_FORMAT_DTS;
	Wfx.cbSize = sizeof( _WAVEFORMATEX );
	Wfx.nSamplesPerSec = pDTSAudio->samples_per_sec;
	Wfx.nAvgBytesPerSec = pDTSAudio->avgbytes_per_sec;
	Wfx.nChannels = pDTSAudio->channels;
	Wfx.nBlockAlign = 0; //pcm_resolution < 2 ? 2 : 3;
	Wfx.wBitsPerSample = 16;
	cmt->SetFormat((PBYTE)&Wfx, SIZEOF_WAVEFORMATEX);

	//cmt->SetType(&MEDIATYPE_MPEG2_PES);
	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype(&CLSID_DTS_AUDIO);
	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	cmt->bTemporalCompression = TRUE;
	cmt->bFixedSizeSamples = FALSE;

	return S_OK;
}

HRESULT CPushReader::SetMDTSAuidoMediaType( CMediaType *cmt, DTS_AUDIO *pDTSAudio )
{
	_WAVEFORMATEX   Wfx={0};
	Wfx.wFormatTag = WAVE_FORMAT_DTS;
	Wfx.cbSize = 0;//sizeof( _WAVEFORMATEX );
	Wfx.nSamplesPerSec = pDTSAudio->samples_per_sec;
	//Wfx.nAvgBytesPerSec = pDTSAudio->avgbytes_per_sec;
	Wfx.nChannels = pDTSAudio->channels;
	Wfx.nBlockAlign = 0; 
	Wfx.wBitsPerSample = 0;
	cmt->SetFormat((PBYTE)&Wfx, SIZEOF_WAVEFORMATEX);

	//cmt->SetType(&MEDIATYPE_MPEG2_PES);
	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype(&CLSID_DTS_MASTER_AUDIO);
	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	cmt->bTemporalCompression = FALSE;
	cmt->bFixedSizeSamples = FALSE;
	cmt->cbFormat = 18;

	return S_OK;
}

HRESULT CPushReader::SetWMAAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio )
{
	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	_WAVEFORMATEX*   pWfx;	
	int extradata_size = pMpegAudio->extra_bytes;
	pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX + extradata_size );
	memset( pWfx, 0, SIZEOF_WAVEFORMATEX  );
	pWfx->nSamplesPerSec = pMpegAudio->samples_per_sec;
	pWfx->nChannels      = pMpegAudio->channels;
	pWfx->nAvgBytesPerSec = pMpegAudio->avgbytes_per_sec/8;
	pWfx->nBlockAlign     = pMpegAudio->block_align;
	pWfx->cbSize = extradata_size;
	memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, pMpegAudio->extra, extradata_size );
	cmt->SetSampleSize( pWfx->nBlockAlign );

	if ( pMpegAudio->head_layer == 0x82 ) //WMA PRO
	{
		cmt->SetSubtype( &MEDIASUBTYP_MpegWMAPRO );
		pWfx->wFormatTag     = 0x162;
		pWfx->wBitsPerSample = 24;

	} else
	{
		cmt->SetSubtype( &MEDIASUBTYP_MpegWMA );
		pWfx->wFormatTag     = 0x161;
		pWfx->wBitsPerSample = 16;
	}

	return S_OK;
}

//WK
HRESULT CPushReader::SetFLACSAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio )
{
	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype( &MEDIASUBTYP_MpegWMA );
	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	_WAVEFORMATEX*   pWfx;	
	int extradata_size = pMpegAudio->extra_bytes;
	pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX + extradata_size );
	memset( pWfx, 0, SIZEOF_WAVEFORMATEX  );
	pWfx->nSamplesPerSec = pMpegAudio->samples_per_sec;
	pWfx->nChannels      = pMpegAudio->channels;
	pWfx->nAvgBytesPerSec = pMpegAudio->avgbytes_per_sec/8;
	pWfx->nBlockAlign     = pMpegAudio->block_align;
	pWfx->cbSize = extradata_size;
	memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, pMpegAudio->extra, extradata_size );
	cmt->SetSampleSize( pWfx->nBlockAlign );

	return S_OK;
}

HRESULT CPushReader::SetAFLACSAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio )
{
	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype( &MEDIASUBTYP_MpegWMA );
	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	_WAVEFORMATEX*   pWfx;	
	int extradata_size = pMpegAudio->extra_bytes;
	pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( SIZEOF_WAVEFORMATEX + extradata_size );
	memset( pWfx, 0, SIZEOF_WAVEFORMATEX  );
	pWfx->nSamplesPerSec = pMpegAudio->samples_per_sec;
	pWfx->nChannels      = pMpegAudio->channels;
	pWfx->nAvgBytesPerSec = pMpegAudio->avgbytes_per_sec/8;
	pWfx->nBlockAlign     = pMpegAudio->block_align;
	pWfx->cbSize = extradata_size;
	memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, pMpegAudio->extra, extradata_size );
	cmt->SetSampleSize( pWfx->nBlockAlign );

	return S_OK;
}

static const GUID FORMAT_VorbisFormat2 =
{ 0xB36E107F, 0xA938, 0x4387, {0x93, 0xC7, 0x55, 0xE9, 0x66, 0x75, 0x74, 0x73 } };
//DEFINE_GUID(FORMAT_VorbisFormat         ,0x6bddfa7e, 0x9f22, 0x46a9, 0xab, 0x5e, 0x88, 0x4e, 0xff, 0x29, 0x4d, 0x9f);
//DEFINE_GUID(FORMAT_VorbisFormat2        ,0xb36e107f, 0xa938, 0x4387, 0x93, 0xc7, 0x55, 0xe9, 0x66, 0x75, 0x74, 0x73);
//DEFINE_GUID(FORMAT_VorbisFormatIll      ,0x44e04f43, 0x58b3, 0x4de1, 0x9b, 0xaa, 0x89, 0x01, 0xf8, 0x52, 0xda, 0xe4);
//DEFINE_GUID(MEDIASUBTYPE_Vorbis         ,0xcddca2d5, 0x6d75, 0x4f98, 0x84, 0x0e, 0x73, 0x7b, 0xed, 0xd5, 0xc6, 0x3b);
//DEFINE_GUID(MEDIASUBTYPE_Vorbis2        ,0x8d2fd10b, 0x5841, 0x4a6b, 0x89, 0x05, 0x58, 0x8f, 0xec, 0x1a, 0xde, 0xd9);
//DEFINE_GUID(MEDIASUBTYPE_VorbisIll      ,0x8a0566ac, 0x42b3, 0x4ad9, 0xac, 0xa3, 0x93, 0xb9, 0x06, 0xdd, 0xf9, 0x8a);
static unsigned char* GetVorbisHeader( unsigned char* pExtraData, int nExtraDataSize, unsigned long pHeader[3] )
{
	unsigned char *headers=pExtraData;
	unsigned int headers_len=nExtraDataSize;
	unsigned char *header_start[3];
	int header_len[3];
	if (headers[0]==0 && headers[1]==30)
	{
		for (int i=0;i<3;i++)
		{
			header_len[i]=*headers++<<8;
			header_len[i]+=*headers++;
			header_start[i]=headers;
			headers+=header_len[i];
		}
	}
	else 
	if (headers[0]==2)
	{
		size_t i,j;
		for (j=1,i=0;i<2;++i, ++j)
		{
			header_len[i]=0;
			while (j<headers_len && headers[j]==0xff)
			{
				header_len[i]+=0xff;
				++j;
			}
			if (j>=headers_len)
				return NULL;
			header_len[i]+=headers[j];
		}
		header_len[2]=int(headers_len-header_len[0]-header_len[1]-j);
		headers+=j;
		header_start[0]=headers;
		header_start[1]=header_start[0]+header_len[0];
		header_start[2]=header_start[1]+header_len[1];
	}
	pHeader[0] = header_len[0];
	pHeader[1] = header_len[1];
	pHeader[2] = header_len[2];
	return header_start[0];
}

HRESULT CPushReader::SetVORBISAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio, unsigned char* pExtraData, int nExtraDataSize )
{
	typedef struct _VORBISFORMAT2
	{
	 DWORD nChannels;
	 DWORD nSamplesPerSec;
	 DWORD nBitsPerSample;
	 DWORD HeaderSize[3]; // 0: Identification, 1: Comment, 2: Setup
	} VORBISFORMAT2;
	DWORD HeaderSize[3];
	unsigned char* pHeader;
	pHeader = GetVorbisHeader(  pExtraData, nExtraDataSize, HeaderSize );
	if ( pHeader == NULL )
		nExtraDataSize = 0;
	else
	{
		nExtraDataSize -= pHeader-pExtraData;//HeaderSize[0]+HeaderSize[1]+HeaderSize[2];
	}
	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype( &MEDIASUBTYP_VORBIS );
	cmt->SetFormatType(&FORMAT_VorbisFormat2);
	cmt->SetVariableSize();
	VORBISFORMAT2*   pVRBS;	
	pVRBS = (VORBISFORMAT2*)cmt->AllocFormatBuffer( sizeof(VORBISFORMAT2) + nExtraDataSize );
	memset( pVRBS, 0, sizeof(VORBISFORMAT2)  );
	pVRBS->nSamplesPerSec = pMpegAudio->samples_per_sec;
	pVRBS->nChannels      = pMpegAudio->channels;
	if ( pHeader != NULL )
	{
		pVRBS->HeaderSize[0] = HeaderSize[0];
		pVRBS->HeaderSize[1] = HeaderSize[1];
		pVRBS->HeaderSize[2] = HeaderSize[2];
		memcpy( (char*)pVRBS+sizeof(VORBISFORMAT2), pHeader, nExtraDataSize );
	}


	return S_OK;
}


HRESULT CPushReader::SetPCMAuidoMediaType( CMediaType *cmt, PCM_AUDIO *pPCMAudio )
{

	_WAVEFORMATEX Wfx={0};
	Wfx.cbSize = 0;//sizeof(_WAVEFORMATEXTENSIBLE)-sizeof(_WAVEFORMATEX);
	Wfx.wFormatTag = WAVE_FORMAT_PCM;//WAVE_FORMAT_EXTENSIBLE;
	Wfx.nSamplesPerSec = pPCMAudio->samples_per_sec;
	Wfx.nAvgBytesPerSec = pPCMAudio->avgbytes_per_sec;
	Wfx.nChannels = pPCMAudio->channels;
	Wfx.nBlockAlign = pPCMAudio->block_align;
	if ( Wfx.nBlockAlign == 0 )
		Wfx.nBlockAlign = pPCMAudio->channels * (pPCMAudio->bits_per_sample/8);
	Wfx.wBitsPerSample = pPCMAudio->bits_per_sample;
	if ( Wfx.nAvgBytesPerSec == 0 )
		Wfx.nAvgBytesPerSec = pPCMAudio->samples_per_sec*pPCMAudio->channels*pPCMAudio->bits_per_sample/8;
	cmt->SetFormat((PBYTE)&Wfx, sizeof(_WAVEFORMATEX));

	cmt->SetType(&MEDIATYPE_Audio);
	cmt->SetSubtype(&MEDIASUBTYPE_PCM);
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}


HRESULT CPushReader::SetLPCMAuidoMediaType( CMediaType *cmt, LPCM_AUDIO *pLPCMAudio )
{
	_WAVEFORMATEX   Wfx={0};
	Wfx.cbSize = sizeof( _WAVEFORMATEX );
	Wfx.nSamplesPerSec = pLPCMAudio->samples_per_sec;
	Wfx.nAvgBytesPerSec = pLPCMAudio->avgbytes_per_sec;
	Wfx.nChannels = pLPCMAudio->channels;
	Wfx.nBlockAlign = pLPCMAudio->block_align;
	Wfx.wBitsPerSample = pLPCMAudio->bits_per_sample;
	cmt->SetFormat((PBYTE)&Wfx, SIZEOF_WAVEFORMATEX);

	cmt->SetType(&MEDIATYPE_MPEG2_PES);
	cmt->SetSubtype(&MEDIASUBTYPE_DVD_LPCM_AUDIO);
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}


HRESULT CPushReader::SetMpegAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio )
{
	typedef	struct	
	{
	    unsigned short  wFormatTag;         /* format type */
		unsigned short  nChannels;          /* number of channels (i.e. mono, stereo...) */
		unsigned long   nSamplesPerSec;     /* sample rate */
		unsigned long   nAvgBytesPerSec;    /* for buffer estimation */
		unsigned short  nBlockAlign;        /* block size of data */
		unsigned short  wBitsPerSample;     /* number of bits per sample of mono data */
		unsigned short  cbSize;             /* the count in bytes of the size of */
		unsigned short	fwHeadLayer;
		unsigned long	dwHeadBitrate;
		unsigned short	fwHeadMode;	
		unsigned short	fwHeadModeExt;
		unsigned short	wHeadEmphasis;
		unsigned short	fwHeadFlags;
		unsigned long	dwPTSLow;
		unsigned long	dwPTSHigh;
	} _MPEGWAV;
	typedef struct 
	{
		_MPEGWAV MpegWav;
		unsigned char extra[256];
	} _MPEGAUDIO;

	//TS_MPEGWAV   MpegWav={0};	
	_MPEGAUDIO MpegAudio={0};
	MpegAudio.MpegWav.cbSize = 22;//2*9;//sizeof( _WAVEFORMATEX )-2;
	MpegAudio.MpegWav.wFormatTag = WAVE_FORMAT_MPEG;
	MpegAudio.MpegWav.nSamplesPerSec = pMpegAudio->samples_per_sec;
	MpegAudio.MpegWav.nAvgBytesPerSec = pMpegAudio->avgbytes_per_sec;
	MpegAudio.MpegWav.nChannels = pMpegAudio->channels;
	MpegAudio.MpegWav.nBlockAlign = pMpegAudio->block_align;
	MpegAudio.MpegWav.wBitsPerSample = pMpegAudio->bits_per_sample;
	MpegAudio.MpegWav.dwPTSLow = 0;
	MpegAudio.MpegWav.dwPTSHigh = 0;
	MpegAudio.MpegWav.dwHeadBitrate = pMpegAudio->avgbytes_per_sec;//pMpegAudio->head_bitrate;
	MpegAudio.MpegWav.fwHeadFlags = pMpegAudio->head_flags;
	MpegAudio.MpegWav.fwHeadLayer = pMpegAudio->head_layer;
	MpegAudio.MpegWav.fwHeadMode = pMpegAudio->head_mode;
	MpegAudio.MpegWav.fwHeadModeExt = pMpegAudio->head_mode_ext;
	MpegAudio.MpegWav.wHeadEmphasis = pMpegAudio->head_emphasis;
	if ( pMpegAudio->head_layer == 1 )
		MpegAudio.MpegWav.fwHeadLayer = ACM_MPEG_LAYER1;
	else
	if ( pMpegAudio->head_layer == 2 )
		MpegAudio.MpegWav.fwHeadLayer = ACM_MPEG_LAYER2;
	else
	if ( pMpegAudio->head_layer == 3 )
		MpegAudio.MpegWav.fwHeadLayer = ACM_MPEG_LAYER3;
	
	cmt->SetFormat((PBYTE)&MpegAudio, sizeof(_MPEGWAV) );
	cmt->SetType(&MEDIATYPE_Audio);
	cmt->SetSubtype(&MEDIASUBTYPE_MPEG2_AUDIO); //ZQ.
	//cmt->SetSubtype(&MEDIASUBTYPE_MPEG1AudioPayload ); 
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}

HRESULT CPushReader::SetMp3AuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio )
{
	typedef	struct	
	{
	    unsigned short	wFormatTag;         /* format type */
		unsigned short	nChannels;          /* number of channels (i.e. mono, stereo...) */
		unsigned long	nSamplesPerSec;     /* sample rate */
		unsigned long	nAvgBytesPerSec;    /* for buffer estimation */
		unsigned short	nBlockAlign;        /* block size of data */
		unsigned short	wBitsPerSample;     /* number of bits per sample of mono data */
		unsigned short  cbSize;             /* the count in bytes of the size of */

		unsigned short	fwHeadLayer;
		unsigned long	dwHeadBitrate;
		unsigned short	fwHeadMode;	
		unsigned short	fwHeadModeExt;
		unsigned short	wHeadEmphasis;
		unsigned short	fwHeadFlags;
		unsigned long	dwPTSLow;
		unsigned long	dwPTSHigh;
	} _MPEGWAV;
	typedef struct 
	{
		_MPEGWAV MpegWav;
		unsigned char extra[256];
	} _MPEGAUDIO;

	_MPEGAUDIO MpegAudio={0};
	MpegAudio.MpegWav.cbSize = 22;
	MpegAudio.MpegWav.wFormatTag = WAVE_FORMAT_MPEG;
	MpegAudio.MpegWav.nSamplesPerSec = pMpegAudio->samples_per_sec;
	MpegAudio.MpegWav.nAvgBytesPerSec = pMpegAudio->avgbytes_per_sec;
	MpegAudio.MpegWav.nChannels = pMpegAudio->channels;
	MpegAudio.MpegWav.nBlockAlign = pMpegAudio->block_align;
	MpegAudio.MpegWav.wBitsPerSample = pMpegAudio->bits_per_sample;
	MpegAudio.MpegWav.dwPTSLow = 0;
	MpegAudio.MpegWav.dwPTSHigh = 0;
	MpegAudio.MpegWav.dwHeadBitrate = pMpegAudio->avgbytes_per_sec;//pMpegAudio->head_bitrate;
	MpegAudio.MpegWav.fwHeadFlags = pMpegAudio->head_flags;
	MpegAudio.MpegWav.fwHeadLayer = pMpegAudio->head_layer;
	MpegAudio.MpegWav.fwHeadMode = pMpegAudio->head_mode;
	MpegAudio.MpegWav.fwHeadModeExt = pMpegAudio->head_mode_ext;
	MpegAudio.MpegWav.wHeadEmphasis = pMpegAudio->head_emphasis;
	if ( pMpegAudio->head_layer == 1 )
		MpegAudio.MpegWav.fwHeadLayer = ACM_MPEG_LAYER1;
	else
	if ( pMpegAudio->head_layer == 2 )
		MpegAudio.MpegWav.fwHeadLayer = ACM_MPEG_LAYER2;
	else
	if ( pMpegAudio->head_layer == 3 )
		MpegAudio.MpegWav.fwHeadLayer = ACM_MPEG_LAYER3;
	MpegAudio.MpegWav.fwHeadFlags = 0x0c;	//original_holder|protection
	cmt->SetFormat((PBYTE)&MpegAudio, sizeof(_MPEGWAV) );
	cmt->SetType(&MEDIATYPE_Audio);
	//cmt->SetSubtype(&MEDIASUBTYPE_MPEG2_AUDIO); //ZQ.
	cmt->SetSubtype(&MEDIASUBTYPE_MPEG1AudioPayload ); //
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}


HRESULT CPushReader::SetMp3AuidoMediaType2( CMediaType *cmt, MPEG_AUDIO *pMpegAudio )
{

	//typedef	struct	
	//{
	//    unsigned short	wFormatTag;         /* format type */
	//	unsigned short	nChannels;          /* number of channels (i.e. mono, stereo...) */
	//	unsigned long	nSamplesPerSec;     /* sample rate */
	//	unsigned long	nAvgBytesPerSec;    /* for buffer estimation */
	//	unsigned short	nBlockAlign;        /* block size of data */
	//	unsigned short	wBitsPerSample;     /* number of bits per sample of mono data */
	//	unsigned short  cbSize;             /* the count in bytes of the size of */
	//} _MPEGLAYER3WAVEFORMAT;


	//typedef struct 
	//{
	//	WAVEFORMATEX MpegWav;
	//   //_MPEGLAYER3WAVEFORMAT MpegWav;
	//} _MPEGAUDIO;

	//_MPEGAUDIO MpegAudio={0};
	//MpegAudio.MpegWav.cbSize = 0;
	//MpegAudio.MpegWav.wFormatTag = WAVE_FORMAT_MPEG3;
	//MpegAudio.MpegWav.nSamplesPerSec = pMpegAudio->samples_per_sec;
	//MpegAudio.MpegWav.nAvgBytesPerSec = pMpegAudio->avgbytes_per_sec;
	//MpegAudio.MpegWav.nChannels = pMpegAudio->channels;
	//MpegAudio.MpegWav.nBlockAlign = pMpegAudio->block_align;
	//MpegAudio.MpegWav.wBitsPerSample = pMpegAudio->bits_per_sample;
	//MpegAudio.wID = 0;
	//MpegAudio.fdwFlags = 0;
	//MpegAudio.nFramesPerBlock = 0;
	//MpegAudio.nCodecDelay = 0;
	
	typedef struct {
		WAVEFORMATEX  wfx;
		WORD          wID;
		DWORD         fdwFlags;
		WORD          nBlockSize;
		WORD          nFramesPerBlock;
		WORD          nCodecDelay;
	} MPEGLAYER3WAVEFORMAT;


	MPEGLAYER3WAVEFORMAT MpegWav={0};
	MpegWav.wfx.cbSize = 12;
	MpegWav.wfx.wFormatTag = WAVE_FORMAT_MPEG3;
	MpegWav.wfx.nSamplesPerSec = pMpegAudio->samples_per_sec;
	MpegWav.wfx.nAvgBytesPerSec = pMpegAudio->avgbytes_per_sec;
	MpegWav.wfx.nChannels = pMpegAudio->channels;
	MpegWav.wfx.nBlockAlign = 1;//pMpegAudio->block_align;
	//MpegWav.wfx.wBitsPerSample = pMpegAudio->bits_per_sample;
	MpegWav.wID = 1;//MPEGLAYER3_ID_MPEG;
	MpegWav.fdwFlags = 0;
	MpegWav.nBlockSize = 1;
	MpegWav.nFramesPerBlock = 1;
	MpegWav.nCodecDelay = 0;
	cmt->SetFormat((PBYTE)&MpegWav, sizeof(MPEGLAYER3WAVEFORMAT)-2 );
	cmt->SetType(&MEDIATYPE_Audio);
	cmt->SetSubtype(&MEDIASUBTYPE_MPEG2_AUDIO);
	cmt->SetFormatType(&FORMAT_WaveFormatEx);

	return S_OK;
}

unsigned char cook[]= {0x2E, 0x72, 0x61, 0xFD, 0x00, 0x05, 0x00, 0x00, 0x2E, 0x72, 0x61, 0x35, 0x00, 0x00, 0x00, 0x10, 0x00, 0x05, 0x00, 0x00, 0x00, 0x4E, 0x00, 0x18, 0x00, 0x00, 0x03, 0xA2, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07, 0x55, 0x67, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x03, 0xA2, 0x00, 0xBA, 0x00, 0x00, 0x00, 0x00, 0xAC, 0x44, 0x00, 0x00, 0xAC, 0x44, 0x00, 0x00, 0x00, 0x10, 0x00, 0x02, 0x67, 0x65, 0x6E, 0x72, 0x63, 0x6F, 0x6F, 0x6B, 0x01, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x01, 0x00, 0x00, 0x03, 0x08, 0x00, 0x00, 0x25, 0x00, 0x00, 0x00, 0x00, 0x00, 0x06, 0x00, 0x05 };
HRESULT CPushReader::SetCookAuidoMediaType( CMediaType *cmt, MPEG_AUDIO *pMpegAudio )
{
	cmt->SetType( &MEDIATYPE_Audio ); 
	cmt->SetSubtype( &MEDIASUBTYPE_COOK );
	cmt->SetFormatType(&FORMAT_WaveFormatEx);
	WAVEFORMATEX*   pWfx;	
	int extradata_size = pMpegAudio->extra_bytes;
extradata_size = sizeof(cook);
	pWfx = (_WAVEFORMATEX*)cmt->AllocFormatBuffer( sizeof(WAVEFORMATEX) + extradata_size );
	memset( pWfx, 0, SIZEOF_WAVEFORMATEX  );
	pWfx->wFormatTag = 0x2004;//COOK
	pWfx->nSamplesPerSec = pMpegAudio->samples_per_sec;
	pWfx->nChannels      = pMpegAudio->channels;
	pWfx->cbSize = 0;
	//pWfx->nAvgBytesPerSec = pMpegAudio->avgbytes_per_sec/8;
	pWfx->nBlockAlign     = pMpegAudio->block_align;
	cmt->SetSampleSize( pWfx->nBlockAlign );
	//pWfx->cbSize = extradata_size;
	//memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, "cook", 4 );
	//memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, pMpegAudio->extra, extradata_size );
	memcpy( (char*)pWfx+sizeof(WAVEFORMATEX), pMpegAudio->extra, extradata_size );

	//cmt->SetSampleSize( 14880 );
	//pWfx->nAvgBytesPerSec = 0;
	//pWfx->wBitsPerSample = 16;
	//pWfx->nBlockAlign = 930;
memcpy( (char*)pWfx+SIZEOF_WAVEFORMATEX, cook, extradata_size );

	return S_OK;
}

HRESULT CPushReader::SetSubTitleMediaType( CMediaType *cmt, SUBTITLE *pSubtitle )
{
	typedef struct {
		DWORD dwOffset;
		CHAR IsoLang[4]; // three letter lang code + terminating zero
		WCHAR TrackName[256]; // 256 chars ought to be enough for everyone :)
	} SUBTITLEINFO;
	cmt->InitMediaType();
	//cmt->SetType(&MEDIATYPE_Text);
	cmt->SetType( &MEDIATYPE_Subtitle ); 
	cmt->SetSubtype( &MEDIASUBTYPE_UTF8 );
	cmt->SetFormatType(&FORMAT_SubtitleInfo);
	cmt->bTemporalCompression = FALSE;
	cmt->bFixedSizeSamples = FALSE;
	cmt->cbFormat = 0;
	SUBTITLEINFO* psi = (SUBTITLEINFO*)cmt->AllocFormatBuffer( sizeof(SUBTITLEINFO)  );
	memset(psi, 0, cmt->FormatLength());
	psi->dwOffset = sizeof(SUBTITLEINFO);
	strcpy(psi->IsoLang, "eng");
	char *name = "Subtitles";
	int size = MultiByteToWideChar(CP_ACP, 0, name, strlen(name), psi->TrackName, sizeof(psi->TrackName)*2 );
	//SUBTITLEINFO subinfo={0};
	//subinfo.dwOffset = sizeof(SUBTITLEINFO);
	//subinfo.IsoLang[0] = (CHAR)((pSubtitle->language >>24)&0xff);
	//subinfo.IsoLang[0] = (CHAR)((pSubtitle->language >>16)&0xff);
	//subinfo.IsoLang[1] = (CHAR)((pSubtitle->language >>8)&0xff);
	//subinfo.IsoLang[2] = (CHAR)((pSubtitle->language)&0xff);
	//subinfo.IsoLang[0]=0x67;
	//subinfo.IsoLang[1]=0x6e;
	//subinfo.IsoLang[2]=0x65;
	//cmt->SetFormat( (PBYTE)&subinfo, sizeof(SUBTITLEINFO) );

	return S_OK;
}

////////////////////////////////////////////////

HRESULT CPushReader::InitMPEG1Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &m_pTrackInf[nTrackIndex].av.d.v.mpeg_video;
	DbgLog((LOG_TRACE, 3, TEXT("Progressive=%d"), pMpegVideo->progressive));

	HRESULT hr = SetMpeg1VideoMediaType( &cmt,  pMpegVideo );
	if (FAILED(hr)) 
	{
		return hr;
	}
	m_dwVideoRate = pMpegVideo->bit_rate/8; // it's in bits/second

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;

}

HRESULT CPushReader::InitMPEG2Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &m_pTrackInf[nTrackIndex].av.d.v.mpeg_video;

	DbgLog((LOG_TRACE, 3, TEXT( "Mpeg2 %s : %s"),
		pMpegVideo->profile == 5 ? TEXT("Simple Profile") :
		pMpegVideo->profile == 4 ? TEXT("Main Profile")   :
		pMpegVideo->profile == 3 ? TEXT("SNR Scalable Profile") :
		pMpegVideo->profile == 2 ? TEXT("Spatially Scalable Profile") :
		pMpegVideo->profile == 1 ? TEXT("High Profile") : TEXT("Unrecognized Profile"),
		pMpegVideo->level  == 10 ? TEXT("Low Level") :
		pMpegVideo->level  == 8  ? TEXT("Main Level") :
		pMpegVideo->level  == 6  ? TEXT("High 1440 Level") :
		pMpegVideo->level  == 4  ? TEXT("High Level") :	TEXT("Unrecognized Level")));
	DbgLog((LOG_TRACE, 3, TEXT("Progressive=%d"), pMpegVideo->progressive));

	HRESULT hr = SetMpeg2VideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;
	// set the video rate for seeking purposes
	m_dwVideoRate = pMpegVideo->bit_rate/8; // it's in bits/second

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;

}

HRESULT CPushReader::InitH264Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	H264_VIDEO *pH264Video = &m_pTrackInf[nTrackIndex].av.d.v.h264;

	DbgLog((LOG_TRACE, 3, TEXT(" MPEG H.264") ));
	HRESULT hr;

	H264_VIDEO_I H264VideoI={0};
	int H264Format = GetH264Format( pH264Video->sps, pH264Video->sps_length, &H264VideoI );
	pH264Video->profile = H264VideoI.profile;
	pH264Video->level = H264VideoI.level;
	pH264Video->code_size = H264VideoI.code_size;
	if ( pH264Video->code_size == 0 ) pH264Video->code_size =4;
	if ( H264Format ) pH264Video->progressive = H264VideoI.progressive;
	pH264Video->sub_format = H264Format;

	if ( H264Format == 1 )
		DbgLog((LOG_TRACE, 3, TEXT(" MPEG H.264") ));
	else
	if ( H264Format == 2 )
		DbgLog((LOG_TRACE, 3, TEXT(" MPEG VC1 in H.264 format %d  profile:%d level: code_size:%d"), 
		                      H264Format, pH264Video->profile, pH264Video->level, pH264Video->code_size ));
	else
	{
		pH264Video->sps_length = 0;
		DbgLog((LOG_TRACE, 3, TEXT(" Can't parse extra data to get H.264 information, drop extra data, setup H.264") ));
	}

	if ( H264Format == 1 || H264Format == 0 )
		SetH264VideoMediaType4Cyberlink( &cmt, pH264Video );
	else
		SetH264VC1VideoMediaType( &cmt, pH264Video );

	m_dwVideoRate = pH264Video->cbr/8;
	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr = m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );	
	if ( H264Format == 1 )
	{
		SetH264VC1VideoMediaType( &cmt, pH264Video );
		m_pFilter->AddMediaType( (BYTE)nTrackIndex, &cmt );
	}
	else
	if ( H264Format == 2 )
	{
		SetH264VideoMediaType4Cyberlink( &cmt, pH264Video );
		m_pFilter->AddMediaType( (BYTE)nTrackIndex, &cmt );
	}

	

	return hr;
}

HRESULT CPushReader::InitVC1Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	HRESULT hr;
	H264_VIDEO *pH264Video = &m_pTrackInf[nTrackIndex].av.d.v.h264;

	DbgLog((LOG_TRACE, 3, TEXT("VC-1") ));
	SetVC1VideoMediaType( &cmt, pH264Video );

	m_dwVideoRate = pH264Video->cbr/8;
	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr = m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );	
	SetWVC1VideoMediaType( &cmt, pH264Video );
	m_pFilter->AddMediaType( (BYTE)nTrackIndex, &cmt );

	return hr;

}


HRESULT CPushReader::InitDixVStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &m_pTrackInf[nTrackIndex].av.d.v.mpeg_video;;

	DbgLog((LOG_TRACE, 3, TEXT(" MPEG DIVX") ));
	HRESULT hr = SetMpeg4VideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMpegVideo->bit_rate / 8;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;
}

HRESULT CPushReader::InitXVIDStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO_I Mpeg4I={0};
	MPEG_VIDEO *pMpegVideo = &m_pTrackInf[nTrackIndex].av.d.v.mpeg_video;;

	DbgLog((LOG_TRACE, 3, TEXT(" MPEG-4") ));
	if ( ReadMPEG4VideoHeader( &Mpeg4I, pMpegVideo->raw_header, pMpegVideo->actual_header_length ) )
	{
		pMpegVideo->profile = Mpeg4I.profile;
		pMpegVideo->ver_id = Mpeg4I.ver_id;
		DbgLog((LOG_TRACE, 3, TEXT(" MPEG-4 profile_level:%d ver_id:%d"), pMpegVideo->profile, pMpegVideo->ver_id  ));
	}

	HRESULT hr = SetXVIDMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMpegVideo->bit_rate / 8;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;
}

HRESULT CPushReader::InitWMV9Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &m_pTrackInf[nTrackIndex].av.d.v.mpeg_video;;

	DbgLog((LOG_TRACE, 3, TEXT(" WMV9") ));
	HRESULT hr = SetWMV9VideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMpegVideo->bit_rate / 8;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;
}

HRESULT CPushReader::InitWMV8Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &m_pTrackInf[nTrackIndex].av.d.v.mpeg_video;;

	DbgLog((LOG_TRACE, 3, TEXT(" WMV8") ));
	HRESULT hr = SetWMV8VideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMpegVideo->bit_rate / 8;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;
}

HRESULT CPushReader::InitWMV7Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &m_pTrackInf[nTrackIndex].av.d.v.mpeg_video;;

	DbgLog((LOG_TRACE, 3, TEXT(" WMV7") ));
	HRESULT hr = SetWMV7VideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMpegVideo->bit_rate / 8;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;
}

HRESULT CPushReader::InitFLV1Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &m_pTrackInf[nTrackIndex].av.d.v.mpeg_video;;

	DbgLog((LOG_TRACE, 3, TEXT(" FLV1 Mpeg4 flash ") ));
	HRESULT hr = SetFLV1VideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMpegVideo->bit_rate / 8;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
	if ( 0 ) {
		H264_VIDEO H264Video={0};
		H264Video.width  = (unsigned short)pMpegVideo->width;
		H264Video.height = (unsigned short)pMpegVideo->height;
		H264Video.frame_rate_nomi =  pMpegVideo->frame_rate_nomi; 
		H264Video.frame_rate_deno =  pMpegVideo->frame_rate_deno; 
		H264Video.cbr =	pMpegVideo->bit_rate;

		//HRESULT hr1 = SetH264VC1VideoMediaType( &cmt, &H264Video );
		HRESULT hr1 = SetH264VideoMediaType4Cyberlink( &cmt, &H264Video );
		m_pFilter->AddMediaType( (BYTE)nTrackIndex, &cmt );
	}
	return hr;
}

HRESULT CPushReader::InitVP6FVStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &m_pTrackInf[nTrackIndex].av.d.v.mpeg_video;;

	DbgLog((LOG_TRACE, 3, TEXT(" FLV VP6F") ));
	HRESULT hr = SetVP6FVideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMpegVideo->bit_rate / 8;
	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
	return hr;
}

HRESULT CPushReader::InitRVStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_VIDEO *pMpegVideo = &m_pTrackInf[nTrackIndex].av.d.v.mpeg_video;;

	DbgLog((LOG_TRACE, 3, TEXT(" RealVideo") ));
	HRESULT hr = SetRealVideoMediaType( &cmt, pMpegVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMpegVideo->bit_rate / 8;
	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
	return hr;
}

HRESULT CPushReader::InitDixVMediaStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MEDIA_VIDEO *pMediaVideo = { 0 }; //to do

	DbgLog((LOG_TRACE, 3, TEXT(" SageTV DIVX Media") ));
	HRESULT hr = SetSageDixVMediaType( &cmt, pMediaVideo );
	if (FAILED(hr)) 
		return hr;

	m_dwVideoRate = pMediaVideo->Video.dwBitRate / 8; // it's in bits/second

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	//give Pin a name
	if ( bSubPin )
		sprintf( szPinName, "Video-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Video" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr =  m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	return hr;
}


/////////////////////////////////////////////////////////
HRESULT CPushReader::InitAC3Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	AC3_AUDIO *pAC3Audio = &m_pTrackInf[nTrackIndex].av.d.a.ac3;

	DbgLog((LOG_TRACE, 3, TEXT("AC3 Audio") ));

	HRESULT hr = SetAC3AuidoMediaType( &cmt, pAC3Audio );
	if ( FAILED(hr) ) 
		return hr;

	m_dwAudioRate = pAC3Audio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	if ( bSubPin )
		sprintf( szPinName, "AC3-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "AC3" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

/////////////////////////////////////////////////////////
HRESULT CPushReader::InitEAC3Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	AC3_AUDIO *pAC3Audio = &m_pTrackInf[nTrackIndex].av.d.a.ac3;

	DbgLog((LOG_TRACE, 3, TEXT("EAC3 Audio") ));

	HRESULT hr = SetAC3AuidoMediaType( &cmt, pAC3Audio );
	if ( FAILED(hr) ) 
		return hr;

	m_dwAudioRate = pAC3Audio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;


	if ( bSubPin )
		sprintf( szPinName, "EAC3-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "EAC3" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

HRESULT CPushReader::InitTAC3Stream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	AC3_AUDIO *pAC3Audio = &m_pTrackInf[nTrackIndex].av.d.a.ac3;;

	DbgLog((LOG_TRACE, 3, TEXT("TrueHD AC3 Audio") ));

	HRESULT hr = SetTAC3AuidoMediaType( &cmt, pAC3Audio );
	if ( FAILED(hr) ) 
		return hr;

	m_dwAudioRate = pAC3Audio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	if ( bSubPin )
		sprintf( szPinName, "TrueHD-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "TrueHD" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

HRESULT CPushReader::InitAACStream( int nTrackIndex, BOOL bSubPin )
{
	HRESULT hr;
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	AAC_AUDIO *pAACAudio = &m_pTrackInf[nTrackIndex].av.d.a.aac;

	DbgLog((LOG_TRACE, 3, TEXT("AAC Audio") ));
	hr = SetAACAuidoMediaType( &cmt, pAACAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pAACAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	if ( bSubPin )
		sprintf( szPinName, "AAC-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "AAC" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	hr = m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

	//if ( !FAILED ( SetAACAuidoMediaType2( &cmt, pAACAudio ) ) )
	//	m_pFilter->AddMediaType( nTrackIndex, &cmt );

	return hr;
}

HRESULT CPushReader::InitDTSStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	DTS_AUDIO *pDTSAudio = &m_pTrackInf[nTrackIndex].av.d.a.dts;;

	DbgLog((LOG_TRACE, 3, TEXT("DTS Audio") ));
	HRESULT hr = SetDTSAuidoMediaType( &cmt, pDTSAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pDTSAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	if ( bSubPin )
		sprintf( szPinName, "DTS-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "DTS" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

HRESULT CPushReader::InitMDTSStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	DTS_AUDIO *pDTSAudio= &m_pTrackInf[nTrackIndex].av.d.a.dts;;

	DbgLog((LOG_TRACE, 3, TEXT("DTS MASTER Audio") ));
	HRESULT hr = SetMDTSAuidoMediaType( &cmt, pDTSAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pDTSAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	if ( bSubPin )
		sprintf( szPinName, "DTSM-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "DTS-M" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

HRESULT CPushReader::InitWMAStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	char *pszWMAType="WMA";
	MPEG_AUDIO *pWMAAudio = &m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio;;
	if ( pWMAAudio->head_layer == 0x82 ) //WMA PRO
		pszWMAType="WMA Pro";

	DbgLog((LOG_TRACE, 3, TEXT("WMA Audio") ));
	HRESULT hr = SetWMAAuidoMediaType( &cmt, pWMAAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pWMAAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	if ( bSubPin )
		sprintf( szPinName, "%s-%d", pszWMAType, m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "%s", pszWMAType );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

HRESULT CPushReader::InitPCMStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	PCM_AUDIO *pPCMAudio = &m_pTrackInf[nTrackIndex].av.d.a.pcm;;

	DbgLog((LOG_TRACE, 3, TEXT("PCM Audio") ));
	HRESULT hr = SetPCMAuidoMediaType( &cmt, pPCMAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pPCMAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	if ( bSubPin )
		sprintf( szPinName, "PCM-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "PCM" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}


HRESULT CPushReader::InitLPCMStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	LPCM_AUDIO *pLPCMAudio = &m_pTrackInf[nTrackIndex].av.d.a.lpcm;;

	DbgLog((LOG_TRACE, 3, TEXT("LPCM Audio") ));
	HRESULT hr = SetLPCMAuidoMediaType( &cmt, pLPCMAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pLPCMAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;


	if ( bSubPin )
		sprintf( szPinName, "LPCM-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "LPCM" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}


HRESULT CPushReader::InitMpegAudioStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	HRESULT hr;

	MPEG_AUDIO *pMpegAudio = &m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio;;
	if ( pMpegAudio->head_layer == 3 )
	{
		DbgLog((LOG_TRACE, 3, TEXT("MP3 %d Audio"), pMpegAudio->head_layer  ));
		HRESULT hr = SetMp3AuidoMediaType( &cmt, pMpegAudio );
		if (FAILED(hr)) 
			return hr;
	} else
	{
		DbgLog((LOG_TRACE, 3, TEXT("MPEG %d Audio"), pMpegAudio->head_layer  ));
		hr = SetMpegAuidoMediaType( &cmt, pMpegAudio ); 
		if (FAILED(hr)) 
			return hr;
	}
	m_dwAudioRate = pMpegAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;

	if ( pMpegAudio->head_layer == 3 )
	{
		if ( bSubPin )
			sprintf( szPinName, "MP3-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
		else
			sprintf( szPinName, "MP3" );
	} else
	{
		if ( bSubPin )
			sprintf( szPinName, "Audio-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
		else
			sprintf( szPinName, "Audio" );
	}

	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );
	hr = m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
	if ( hr == S_OK )
	{
		if ( pMpegAudio->head_layer == 3 )
		{
			HRESULT hr1 = SetMp3AuidoMediaType2( &cmt, pMpegAudio );
			m_pFilter->AddMediaType( (BYTE)nTrackIndex, &cmt );
		}
	}

	return hr;
}

HRESULT CPushReader::InitCookStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	unsigned char *pExtraData;
	int nExtraDataSize;
	MPEG_AUDIO *pMPEGAudio = &m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio;;
	DbgLog((LOG_TRACE, 3, TEXT("Cook Audio/Real Network audio") ));
	pExtraData = m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio.extra;
	nExtraDataSize = m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio.extra_bytes;

	HRESULT hr = SetCookAuidoMediaType( &cmt, pMPEGAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pMPEGAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;


	if ( bSubPin )
		sprintf( szPinName, "cook-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "cook" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

//WK
HRESULT CPushReader::InitVORBISStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	unsigned char *pExtraData;
	int nExtraDataSize;
	MPEG_AUDIO *pMPEGAudio = &m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio;;
	DbgLog((LOG_TRACE, 3, TEXT("VORBIS Audio") ));
	pExtraData = m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio.extra;
	nExtraDataSize = m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio.extra_bytes;
	if ( nExtraDataSize == 0 )
	{
		pExtraData = m_pTrackInf[nTrackIndex].av.private_data;
		nExtraDataSize = m_pTrackInf[nTrackIndex].av.private_data_bytes;
	}

	HRESULT hr = SetVORBISAuidoMediaType( &cmt, pMPEGAudio, pExtraData, nExtraDataSize );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pMPEGAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;


	if ( bSubPin )
		sprintf( szPinName, "vorbis-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "vorbis" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

HRESULT CPushReader::InitAFLACStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_AUDIO *pMPEGAudio = &m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio;;

	DbgLog((LOG_TRACE, 3, TEXT("AFLAC Audio") ));
	HRESULT hr = SetAFLACSAuidoMediaType( &cmt, pMPEGAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pMPEGAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;


	if ( bSubPin )
		sprintf( szPinName, "aflac-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "aflac" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

HRESULT CPushReader::InitFLACStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	MPEG_AUDIO *pMPEGAudio = &m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio;;

	DbgLog((LOG_TRACE, 3, TEXT("FLAC Audio") ));
	HRESULT hr = SetFLACSAuidoMediaType( &cmt, pMPEGAudio );
	if (FAILED(hr)) 
		return hr;

	m_dwAudioRate = pMPEGAudio->avgbytes_per_sec;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;


	if ( bSubPin )
		sprintf( szPinName, "flac-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "flac" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );
}

HRESULT CPushReader::InitSubPictureStream( int nTrackIndex, BOOL bSubPin )
{
	CMediaType cmt;
	char  szPinName[16];
	wchar_t wzPinName[16];
	HRESULT hr;

	SUBTITLE* pSubtitle = &m_pTrackInf[nTrackIndex].av.d.s.subt;
		DbgLog((LOG_TRACE, 3, TEXT("LPCM Audio") ));
	hr = SetSubTitleMediaType( &cmt, pSubtitle );
	if (FAILED(hr)) 
		return hr;

	BYTE uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	BYTE uStreamId = m_pTrackInf[nTrackIndex].stream_id;
	BYTE uPinIndex = m_pTrackInf[nTrackIndex].pin_index;


	if ( bSubPin )
		sprintf( szPinName, "Sub-%d", m_pTrackInf[nTrackIndex].stream_id+1 );
	else
		sprintf( szPinName, "Sub" );
	mbstowcs( wzPinName, szPinName, sizeof(szPinName) );

	return m_pFilter->CreateStream( uContentType, uStreamId, uPinIndex, (BYTE)nTrackIndex, &cmt, wzPinName );

}

int CPushReader::GetVideoPictureSize( int nTrackIndex )
{
	if ( m_pTrackInf[nTrackIndex].av.content_type != VIDEO_DATA )
		return 0;
	DWORD dwFormatFourCC =  m_pTrackInf[nTrackIndex].av.format_fourcc;

	if ( dwFormatFourCC == SAGE_FOURCC( "MPGV" ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.height * m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.width;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "MP1V" ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.height * m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.width;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "H264" ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.v.h264.height * m_pTrackInf[nTrackIndex].av.d.v.h264.width;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "VC1 " ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.v.h264.height * m_pTrackInf[nTrackIndex].av.d.v.h264.width;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "DIXV" ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.height * m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.width;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "MP4V") )
	{
		return m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.height * m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.width;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "FLV1" ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.height * m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.width;
	}else
	if ( dwFormatFourCC == SAGE_FOURCC( "VP6F" ) || dwFormatFourCC == SAGE_FOURCC( "RV40" ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.height * m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.width;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "WMV3" ) || dwFormatFourCC == SAGE_FOURCC( "WMV2" ) || dwFormatFourCC == SAGE_FOURCC( "WMV1" )  )
	{
		return m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.height * m_pTrackInf[nTrackIndex].av.d.v.mpeg_video.width;
	} 
	return 0;

}
int CPushReader::GetAudioChannel( int nTrackIndex )
{
	if ( m_pTrackInf[nTrackIndex].av.content_type != AUDIO_DATA )
		return 0;
	DWORD dwFormatFourCC =  m_pTrackInf[nTrackIndex].av.format_fourcc;
	if ( dwFormatFourCC == SAGE_FOURCC( "AC3 " ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.a.ac3.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "MPGA" ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AAC " ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.a.aac.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AACH" ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.a.aac.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "DTS " ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.a.dts.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "DTSM" ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.a.dts.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "LPCM" ) )
	{
		return m_pTrackInf[nTrackIndex].av.d.a.lpcm.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AC3E" ) ) //EAC3
	{
		return m_pTrackInf[nTrackIndex].av.d.a.ac3.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AC3T" ) ) //TrueHD
	{
		return m_pTrackInf[nTrackIndex].av.d.a.ac3.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "WMA " ) ) //PCM
	{
		return m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "PCM " ) ) //PCM
	{
		return m_pTrackInf[nTrackIndex].av.d.a.pcm.channels;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "VBRS" ) || dwFormatFourCC == SAGE_FOURCC( "AFLC" ) || 
		 dwFormatFourCC == SAGE_FOURCC( "FLAC" )  ) //PCM
	{
		return m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio.channels;
	}
	 

	return 0;

}

unsigned long CPushReader::GetTrackFormat( int nTrackIndex )
{
	return m_pTrackInf[nTrackIndex].av.format_fourcc;
}

/////////////////////////////////////////////////////////
/*  Initialize a stream */
HRESULT CPushReader::InitStream( int nTrackIndex )
{
    UCHAR uStreamId, uContentType, uPinIndex;
	BOOL bSubPin;
	bSubPin = m_nMainVideo != nTrackIndex && m_nMainAudio != nTrackIndex && m_nMainSpu != nTrackIndex;

	uContentType = m_pTrackInf[nTrackIndex].av.content_type;
	uStreamId = nTrackIndex;
	uPinIndex = m_pTrackInf[ nTrackIndex ].pin_index;
	DbgLog((LOG_TRACE, 2, TEXT("InitStream called for track:%d streamID=0x%x pinIndex=%d contentType=%d"), 
				                   nTrackIndex , uStreamId, uPinIndex, uContentType ));


    /*  Do nothing if we've already seen this one */
    if ( NULL != m_pFilter->FindOutputPin( uStreamId, uPinIndex ) ) 
	{
		DbgLog((LOG_TRACE, 2, TEXT("Steam is already initialized for track:%d"), nTrackIndex ));
        return E_FAIL;
	}

	DWORD dwFormatFourCC =  m_pTrackInf[nTrackIndex].av.format_fourcc;
	HRESULT hr = E_FAIL;
	
	if ( dwFormatFourCC == SAGE_FOURCC( "MPGV" ) )
	{
		hr = InitMPEG2Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "MP1V" ) )
	{
		hr = InitMPEG1Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "H264" ) )
	{
		hr = InitH264Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "VC1 " ) )
	{
		hr = InitVC1Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "DIXV" ) ) //not use yet
	{
		hr = InitDixVStream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "MP4V") ) 
	{
		hr = InitXVIDStream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "WMV3" ) ) 
	{
		hr = InitWMV9Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "WMV2" ) ) 
	{
		hr = InitWMV8Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "WMV1" ) ) 
	{
		hr = InitWMV7Stream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "FLV1" ) )
	{
		hr = InitFLV1Stream( nTrackIndex, bSubPin );
	}else
	if ( dwFormatFourCC == SAGE_FOURCC( "VP6F" ) )
	{
		hr = InitVP6FVStream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "RV40" ) )
	{
		hr = InitRVStream( nTrackIndex, bSubPin );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AC3 " ) )
	{
		if ( m_dwAudioTypeEnableMask & AC3_ENABLE )
			hr = InitAC3Stream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("AC3 Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "MPGA" ) )
	{
		if ( m_dwAudioTypeEnableMask & MPEG_ENABLE )
			hr = InitMpegAudioStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("MPEG Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AAC " ) )
	{
		if ( m_dwAudioTypeEnableMask & AAC_ENABLE )
			hr = InitAACStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("AAC Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AACH" ) )
	{
		if ( m_dwAudioTypeEnableMask & AACH_ENABLE )
			hr = InitAACStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("AAC-HE Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "DTS " ) )
	{
		if ( m_dwAudioTypeEnableMask & DTS_ENABLE )
			hr = InitDTSStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("DTS Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "DTSM" ) )
	{
		if ( m_dwAudioTypeEnableMask & MDTS_ENABLE )
			hr = InitMDTSStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("DTS-Master Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "LPCM" ) )
	{
		if ( m_dwAudioTypeEnableMask & LPCM_ENABLE )
			hr = InitLPCMStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("LPCM Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AC3E" ) ) //EAC3
	{
		if ( m_dwAudioTypeEnableMask & EAC3_ENABLE )
			hr = InitEAC3Stream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("EAC3 Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AC3T" ) ) //TrueHD
	{
		if ( m_dwAudioTypeEnableMask & TAC3_ENABLE )
			hr = InitTAC3Stream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("TrueHD Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "WMA " ) ) //WMA
	{
		if ( m_dwAudioTypeEnableMask & WMA_ENABLE )
			hr = InitWMAStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("PCM Audio Disabled by registry setting for track:%d"), nTrackIndex ));

	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "PCM " ) ) //PCM
	{
		if ( m_dwAudioTypeEnableMask & PCM_ENABLE )
			hr = InitPCMStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("PCM Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "COOK" ) )
	{
		if ( m_dwAudioTypeEnableMask & COOK_ENABLE )
			hr = InitCookStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("COOK Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "VRBS" ) )
	{
		if ( m_dwAudioTypeEnableMask & VRBS_ENABLE )
			hr = InitVORBISStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("VORBIS Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AFLC" ) )
	{
		if ( m_dwAudioTypeEnableMask & FLAC_ENABLE )
			hr = InitAFLACStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("AFLAC Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "FLAC" )  ) 
	{
		if ( m_dwAudioTypeEnableMask & FLAC_ENABLE )
			hr = InitFLACStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("FLAC Audio Disabled by registry setting for track:%d"), nTrackIndex ));
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "SPU " ) ) //TrueHD
	{
		if ( m_dwSubPcitureEnableMask )
			hr = InitSubPictureStream( nTrackIndex, bSubPin );
		else
			DbgLog((LOG_TRACE, 3, TEXT("Sub picture Disabled by registry setting for track:%d"), nTrackIndex ));
	} 
	//hr = InitDixVMediaStream( nTrackIndex, bSubPin );
	if ( !FAILED(hr) )
	{
		m_pTrackInf[ nTrackIndex ].stream_ready = true;
		m_pTrackInf[ nTrackIndex ].data_frames = 0;
		DbgLog((LOG_TRACE, 2, TEXT("InitStream called sucessfull for track:%d"), nTrackIndex ));
	} else
		DbgLog((LOG_TRACE, 2, TEXT("InitStream called failed for track:%d"), nTrackIndex ));

    return hr; 
}

void CPushReader::UpdateStream(  )
{
	int i, pin_num = 1;
	int pin_map[MAX_DEMUX_TRACK_NUM]={0};

	/*
	//sorting tracks for pin order.
	for ( i = 0; i<m_nTrackNum; i++ )
	{
		if ( m_pTrackInf[ i ].stream_ready == 0 ) continue;
		if ( m_pTrackInf[ i ].av.format_fourcc == 0 ) continue;
		if ( m_nMainVideo >= 0 && m_nMainVideo == i )
		{
			pin_map[pin_num-1] = i;
			m_pTrackInf[i].pin_index = pin_num++;
		}
	}

	for ( i = 0; i<m_nTrackNum; i++ )
	{
		if ( m_pTrackInf[ i ].stream_ready == 0 ) continue;
		if ( m_pTrackInf[ i ].av.format_fourcc == 0 ) continue;
		if ( m_nMainAudio >= 0 && m_nMainAudio == i )
		{
			pin_map[pin_num-1] = i;
			m_pTrackInf[i].pin_index = pin_num++;
		}
	}

	for ( i = 0; i<m_nTrackNum; i++ )
	{
		if ( m_pTrackInf[ i ].stream_ready == 0 ) continue;
		if ( m_pTrackInf[ i ].av.format_fourcc == 0 ) continue;
		if ( m_pTrackInf[i].pin_index > 0 ) continue;
		if ( m_pTrackInf[i].stream_type == 1 )
		{
			pin_map[pin_num-1] = i;
			m_pTrackInf[i].pin_index = pin_num++;
		}
	}
	for ( i = 0; i<m_nTrackNum; i++ )
	{
		if ( m_pTrackInf[ i ].stream_ready == 0 ) continue;
		if ( m_pTrackInf[ i ].av.format_fourcc == 0 ) continue;
		if ( m_pTrackInf[i].pin_index > 0 ) continue;
		if ( m_pTrackInf[i].stream_type == 2 )
		{
			pin_map[pin_num-1] = i;
			m_pTrackInf[i].pin_index = pin_num++;
		}
	}
	for ( i = 0; i<m_nTrackNum; i++ )
	{
		if ( m_pTrackInf[ i ].stream_ready == 0 ) continue;
		if ( m_pTrackInf[ i ].av.format_fourcc == 0 ) continue;
		if ( m_pTrackInf[i].pin_index > 0 ) continue;
		pin_map[pin_num-1] = i;
		m_pTrackInf[i].pin_index = pin_num++;

	}
	*/
	
	
	for ( i = 0; i<m_nTrackNum; i++ )
	{	
		if ( m_pTrackInf[ i ].stream_ready == 0 ) continue;
		if ( m_pTrackInf[ i ].av.format_fourcc == 0 ) continue;
		pin_map[i] = i;
		m_pTrackInf[i].pin_index = pin_num;
		pin_num++;
	}

	for ( i = 0; i<m_nTrackNum; i++ )
	{	
		if ( m_pTrackInf[ pin_map[i] ].av.format_fourcc )
		{
			HRESULT hr = InitStream( pin_map[i] );
		}
	}

}
  
void CPushReader::SetAcceptMediaType( int nTrackIndex, CMediaType* pmt )
{
	m_pTrackInf[ nTrackIndex ].pmt = pmt;
	if ( m_pTrackInf[nTrackIndex].av.format_fourcc == SAGE_FOURCC( "H264" ) )
	{
		if ( pmt->subtype == CLSID_MpegH264VC1 )
		{
			if ( m_pTrackInf[ nTrackIndex ].pushreader_fix_flag & FIX_H264_FLAG )
			{
				setFixH264( (struct PushReader*)m_pPushReader, 0 ); //disable FixH264
				DbgLog((LOG_TRACE, 2, TEXT("Pushreader fixH264 disabled") ));
			}
		}
		else
		if ( pmt->subtype == CLSID_MpegH264 )
		{
			if ( m_pTrackInf[ nTrackIndex ].pushreader_fix_flag & FIX_H264_FLAG )
			{
				setFixH264( (struct PushReader*)m_pPushReader, 1 ); //Enable FixH264
				DbgLog((LOG_TRACE, 2, TEXT("Pushreader fixH264 enabled") ));
			}
		}
	}
}

void CPushReader::PickupAudioStream( int nAudioTrack )
{
	 m_nConnectedAudioTrack = nAudioTrack;
	 //prSelectAudioStream( (PushReader*)m_pPushReader, m_pTrackInf[nAudioTrack].stream_id );
	 prSetAudioStream( (PushReader*)m_pPushReader, m_pTrackInf[nAudioTrack].stream_id );
}

void CPushReader::PickupVideoStream( int nVideoTrack )
{
	 m_nConnectedVideoTrack = nVideoTrack;
	 prSetVideoStream( (PushReader*)m_pPushReader, m_pTrackInf[nVideoTrack].stream_id );
}

void CPushReader::PickupSubPictureStream( int nVideoTrack )
{
	 m_nConnectedSubPictureTrack = nVideoTrack;
}

void CPushReader::DropStream( int nTrack )
{
	if ( m_nConnectedVideoTrack == nTrack )
		m_nConnectedVideoTrack = -1;
	else
	if ( m_nConnectedAudioTrack == nTrack )
		m_nConnectedAudioTrack = -1;
	else
	if ( m_nConnectedSubPictureTrack == nTrack )
		m_nConnectedSubPictureTrack = -1;
}

BOOL CPushReader::IsMP3Audio( int nTrackIndex )
{
	if ( nTrackIndex  < 0 || nTrackIndex  >= MAX_DEMUX_TRACK_NUM )
		return FALSE;

	if ( m_pTrackInf[nTrackIndex].av.format_fourcc == 	SAGE_FOURCC( "MPGA" ) && 
		 m_pTrackInf[nTrackIndex].av.d.a.mpeg_audio.head_layer == 3 )
	{
		return TRUE;
	}

	return FALSE;
}


void CPushReader::Stop( ) 
{
	m_bStopParser = TRUE;   
	DbgLog((LOG_TRACE, 2, TEXT("Parser Stop") ));
}

void CPushReader::Run( )
{
	m_bStopParser = FALSE;
	DbgLog((LOG_TRACE, 2, TEXT("Parser Run") ));
}

int CPushReader::Process( unsigned char* pBuffer, int nBufferSize, LONGLONG *pllPts, int* pHasPts, int* pIndex  )
{
	int DataLen, Duration;
	DataLen = ProcessFrames( (struct PushReader *)m_pPushReader, pIndex, pBuffer, nBufferSize, pHasPts, pllPts, &Duration );
	return DataLen; 

	DataLen = ProcessVideoFrames( (struct PushReader *)m_pPushReader, pBuffer, nBufferSize, pHasPts, pllPts, &Duration );
	if ( DataLen > 0 ) 
	{
		*pIndex = 0;
		return DataLen;
	}
	DataLen = ProcessAudioFrames( (struct PushReader *)m_pPushReader, pBuffer, nBufferSize, pHasPts, pllPts, &Duration );
	if ( DataLen > 0 ) 
	{
		*pIndex = 1;
		return DataLen;
	}
	DataLen = ProcessSubPictureFrames( (struct PushReader *)m_pPushReader, pBuffer, nBufferSize, pHasPts, pllPts, &Duration );
	if ( DataLen > 0 ) 
	{
		*pIndex = 2;
		return DataLen;
	}
    return DataLen;

}

int CPushReader::ProcessVideo( unsigned char* pBuffer, int nBufferSize, LONGLONG *pllPts, int* pHasPts )
{
	int Duration;
	return  ProcessVideoFrames( (struct PushReader *)m_pPushReader, pBuffer, nBufferSize, pHasPts, pllPts, &Duration );
}

int CPushReader::ProcessAudio( unsigned char* pBuffer, int nBufferSize, LONGLONG *pllPts, int* pHasPts )
{
	int Duration;
	return ProcessAudioFrames( (struct PushReader *)m_pPushReader, pBuffer, nBufferSize, pHasPts, pllPts, &Duration );
}

int CPushReader::ProcessSubPicture( unsigned char* pBuffer, int nBufferSize, LONGLONG *pllPts, int* pHasPts )
{
	int Duration;
	return ProcessSubPictureFrames( (struct PushReader *)m_pPushReader, pBuffer, nBufferSize, pHasPts, pllPts, &Duration );
}


static int CheckFrameType( unsigned char* buf, int bytes, unsigned char** pDFrame);
static int _s_(unsigned char*data, int size);
int CPushReader::RePackData( int nTrackIndex, unsigned char* pBuffer, int Bytes, unsigned char** pPacket, int *pGroupFlag )
{
	int Length = Bytes;
	*pPacket = pBuffer;
	if ( nTrackIndex < 0 )
		return 0;
	DWORD dwFormatFourCC =  m_pTrackInf[nTrackIndex].av.format_fourcc;
	*pGroupFlag = -1;
	if ( dwFormatFourCC == SAGE_FOURCC( "H264" ) && 
		 m_pTrackInf[nTrackIndex].pmt->subtype == CLSID_MpegH264VC1 )
	{
		static int _ct = 0;
		if ( _ct++ == 0 )
			LogExtraData( pBuffer, Bytes>64 ? 64 : Bytes );
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "AAC " ) )
	{
		*pPacket = pBuffer+7; //strip 7 bytes header
		Length -= 7;
	} else
	if ( dwFormatFourCC == SAGE_FOURCC( "VC1 " ) )
	{
		
		*pGroupFlag = 0;
		if ( Bytes > 4 && pBuffer[0]==0 && pBuffer[1]==0 && pBuffer[2]==1 ) 
		{
			if ( pBuffer[3]==0x0E || pBuffer[3]==0x0F  )
				*pGroupFlag = 1;
		} else
			*pGroupFlag = 1;  //WVC1

		{
		/*	static int frame_num = 0, offset;
			unsigned char *pFrame;
			int n =  CheckFrameType( pBuffer, Bytes, &pFrame );
			if ( n >= 3 && frame_num && pFrame != NULL )
			{
				offset = pFrame - pBuffer;
				*pPacket = pFrame;
				 Length -= offset;

			}
			if ( ++frame_num > 24 )
				frame_num = 0;
		*/
		}
		//DbgLog((LOG_TRACE, 2, TEXT(">>>%d %d"), Bytes, n ));
	}

	return Length;
}


int CPushReader::PackeH264( unsigned char* pBuffer, int Bytes )
{
	unsigned char *p=pBuffer, *s;
	int i, n = 0;
	unsigned long sync=0xffffffff;

	while ( p[0] == 0 && p[1] == 0 && p[2] == 0 && p[3] == 1 )
	{
		n += 4;
		s = p+4;
		for ( i = 0; i<Bytes-n; i++ )
		{
			sync <<= 8;
			sync |= s[i];
			if ( sync == 0x00000001 )
				break;
		}
		if ( sync == 0x00000001 )
		{
			i -= 3;
			s += i;
		}
		p[2] = (unsigned char)((i>>8)&0xff);
		p[3] = (unsigned char)((i)&0xff);
		p = s;
		n += i;
	}

	return Bytes;
}

int CPushReader::GetH264Format( unsigned char *pData, int Size, H264_VIDEO_I *pH264Video  )
{
	int ret = ReadH264VideoHeader( pH264Video, pData, Size );
	if ( ret > 0 )
	{
		pH264Video->code_size = (pData[4]&0x03)+1; //ref: ffmpeg/h264_mp4toannexb_bsf.c line:75
		if ( pH264Video->code_size == 3 )
		{
			pH264Video->code_size = 0;
			DbgLog((LOG_TRACE, 2, TEXT("ERROR: wrong code size.") ));
		}
		return 1;
	} 

	if ( pData[0] == 0x01 )
	{
		unsigned char buf[256];
		int bytes =  sizeof(buf)-4 > Size ?  Size : sizeof(buf)-4;
		buf[0] = 0; buf[1] = 0; buf[2] = 0; buf[3] = 1; 
		memcpy( buf+4, pData+6+2, bytes );
		ret = ReadH264VideoHeader( pH264Video, (const unsigned char*)&buf, bytes+4 );
		if ( ret > 0 )
		{
			pH264Video->code_size = (pData[4]&0x03)+1; //ref: ffmpeg/h264_mp4toannexb_bsf.c line:75
			if ( pH264Video->code_size == 3 )
			{	
				pH264Video->code_size = 0;
				DbgLog((LOG_TRACE, 2, TEXT("ERROR: wrong code size.") ));
			}
			return 2;
		}
	} 

	pH264Video->profile = pData[1];
	pH264Video->level = pData[3];
	return 0;
}


int CPushReader::MakeVC1ExtraData( unsigned char* src, unsigned char* tar, int bytes )
{
	unsigned char *t = tar;
	unsigned char *p = src;
	int num = 0;
	if ( p[0] == 0 && p[1] == 0 && p[2] == 0 && p[3] == 1 )
	{	
		while ( p[0] == 0 && p[1] == 0 && p[2] == 0 && p[3] == 1 )
		{
			unsigned int i, n;
			//count packet length
			p += 4;
			for ( i = 0; (int)i<bytes-4; i++ )
				if ( p[i+0] == 0 && p[i+1] == 0 && p[i+2] == 0 && p[i+3] == 1 )
					break;
			
			t[0] = (i>>8) & 0xff;
			t[1] = (i) & 0xff;
			for ( n = 0; n<i; n++ )
				t[2+n] = p[i];
			
			p += i;
			t += i;
			num += 2+i;

			if ( p >= src+bytes ) 
				return num;
		}		

	} else
	if ( src[0] == 0x01 )
	{
		int i, packet_len = 0;
		//skip VC1 6 bytes
		p += 6;
		while ( 1 )
		{
			packet_len = p[0]<<8 | (p[1] & 0xff );
			if ( packet_len > bytes-num-6-2 )
				return num;

			packet_len += 2;
			for ( i = 0; i<packet_len; i++ )
				t[i] = p[i];

			t += i;
			p += i;
			num += i;
			if ( p[0] != 0x01 )
				return num;

			p++;
			if ( p >= src+bytes ) 
				return num;
		}
	}

	return 0;
}

int CPushReader::GetWVC1ExtraData( unsigned char *pData, int Length,  unsigned char **WVC1ExtraData )
{
	int bytes = 0, i;
	//for ( i = 0; i<Length-5; i++ )
	//{
	//	if ( pData[i] == 0 && pData[i+1] == 0 && pData[i+2] == 0 && pData[i+3] == 01 &&
	//		 pData[i+4] == 0x0f  )
	//	{
	//		*WVC1ExtraData = pData+i;
	//		return Length-i;
	//	}
	//}
	//return 0;
	for ( i = 0; i<Length-4; i++ )
	{
		if ( pData[i] == 0 && pData[i+1] == 0 && pData[i+2] == 01 &&
			 pData[i+3] == 0x0f  )
		{
			*WVC1ExtraData = pData+i-1;
			return Length-i+1;
		}
	}

	return 0;
}

int CPushReader::IsVC1Header( unsigned char *pData )
{
	if (  pData[0] == 0 && pData[1] == 0 && pData[2] == 1  
		  && ( pData[3]==0xf || pData[3]==0xd || pData[3]==0xe ) )
		return pData[3];

	return 0;
}

void LogExtraData( unsigned char *pData, int Bytes )
{
	char buf[1024], *p= buf;
	int pos=0, size = sizeof(buf), i, n=0;
	pos = snprintf( p+pos, size-pos, "Extra(%d): ", Bytes );
	for ( i = 0; i<Bytes; i++ )
	{
		pos += snprintf( p+pos, size-pos, "%02x", pData[i] );
		//if ( ++n == 32 )
		//	pos += snprintf( p+pos, size-pos, "\n" );
		//else
		pos += snprintf( p+pos, size-pos, " " );
		if ( size-pos < 4 )
			break;
	}
	pos += snprintf( p+pos, size-pos, "." );
	DbgLog((LOG_TRACE, 3, buf ));
}

////////////////////////////////////////////////////////////////////
static int CheckFrameType( unsigned char* buf, int bytes, unsigned char** pDFrame )
{
	int i, ct = 0;
	*pDFrame = NULL;
	for ( i = 0; i<bytes; i++ )
	{
		if ( buf[i+0] == 0 && buf[i+1] == 0 && buf[i+2] == 1 )
		{
			//printf( "%x", buf[i+3] );
			if ( buf[i+3] == 0x0d && *pDFrame == NULL )
				*pDFrame = buf+i;

			ct++;
		}
	}
	return ct;
}

static unsigned char* _search_data_( unsigned char* match, int len, unsigned char* data, int data_size )
{
	int i;
	for ( i = 0; i<data_size-len; i++ )
	
		if ( !memcmp( match, data+i, len ) )
			return data+i;
	
	return NULL;
}
static int _s_(unsigned char*data, int size)
{
	unsigned char pat1[]={	0xfe, 0x18, 0x18, 0x1f, 0xe3, 0xc2, 0xf0, 0xbc, 0x2f, 0x0b, 0xc2, 0xf0, 0xbc, 0x2f };
	int len = sizeof(pat1);
	if ( _search_data_( pat1, len, data, size ) )
	{
		printf( "STOP" );
		return 1;
	}
	return 0;
}