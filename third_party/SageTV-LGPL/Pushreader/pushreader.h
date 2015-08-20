/*
 *    Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 *   This library is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 *
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *   Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#ifndef __PUSHREADER_H__
#define __PUSHREADER_H__


#ifdef __cplusplus
extern "C" {
#endif


struct PushReader *OpenPushReader(const char *filename, URLProtocol *private_protocol, unsigned long option );

// 0 none
// 1 mpeg1
// 2 mpeg2
// 3 divx3
// 4 mpeg4
// 5 mpeg4flash
// 8 vc1
// 9 wmv9
// 10 h264
// -1 unsupported
int getVideoCodecSettings(struct PushReader *t1, int *width, int *height, int *misc);

// 0 none
// 1 mpeg layer 1,2,3
// 2 aac
// 3 ac3
// 4 dts
// 5 wma
// -1 unsupported
int getAudioCodecSettings(struct PushReader *t1, int *channels, int *bps, int *samplerate,
    int *bitrate, int *blockalign, int *misc);

void CloseReader(struct PushReader *t1);

int ProcessFrames(struct PushReader *t1, int * type, unsigned char *buffer, int bufferlen, 
    int *haspts, long long *pts, int *duration);
int ProcessVideoFrames(struct PushReader *t1, unsigned char *buffer, int bufferlen,
    int *haspts, long long *pts, int *duration );
int ProcessAudioFrames(struct PushReader *t1, unsigned char *buffer, int bufferlen,
    int *haspts, long long *pts, int *duration);
int ProcessSubPictureFrames(struct PushReader *t1, unsigned char *buffer, int bufferlen,
    int *haspts, long long *pts, int *duration);

long long getFirstTime(struct PushReader *t1);

long long getDuration(struct PushReader *t1);

int getEOF(struct PushReader *t1);

int prSeek(struct PushReader *t1, long long time);

int prGetStreamCounts(struct PushReader *t1, int *videostreams, int *audiostreams, int *spustreams);

int prSetVideoStream(struct PushReader *t1, int stream);
int prSetAudioStream(struct PushReader *t1, int stream);
int prSetSpuStream(struct PushReader *t1, int stream);

int prSelectAudioStream(struct PushReader *t1, int stream);
int getMpegVideoInfo(struct PushReader *t1, int *profile, int*level, int* interlaced, int *bitrate, int *misc );
int GetVideoExtraData(struct PushReader *t1, unsigned char* pBuffer, int nBufferSize );
int getMpegVideoStreamInf( struct PushReader *t1, int *frame_rate_num, int *frame_rate_den, unsigned long *language  );
int getAudioStreamInf( struct PushReader *t1, unsigned long *language  );
int GetAudioExtraData( struct PushReader *t1, unsigned char* pBuffer, int nBufferSize );
int getAudioMpegInfo( struct PushReader *t1, int *sampe_fmt, int *layer, int *frame_size, int* mode );
int getSpuStreamInf( struct PushReader *t1, unsigned long *language  );

int verfyVersion( int VersionOfLibAVFormat,  int SizeOFURLProtocol, int SizeOFURLContext );
int setFixH264( struct PushReader *t1, int val );
int stateFixH264( struct PushReader *t1 );
void disableDecodeAudio( struct PushReader *t1 );
void enableDecodeCookAudio( struct PushReader *t1 );

void setDebugTrace( char* pDebugLogName, int logCtrl );

#ifdef __cplusplus
}
#endif

#endif

