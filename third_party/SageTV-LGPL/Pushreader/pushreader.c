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
#include <stdio.h>
#include <math.h>
#define HAVE_AV_CONFIG_H
#include <avcodec.h>
#include <avformat.h>
#include <audioconvert.h>
//#include <allformats.h>
#include "PushReader.h"
//#include "allformats.h"
#include "flashmpeg4.h"
#include "feeder.h"

void *av_memcpy(void *dest, const void *src, unsigned int size)
{
  if ((dest != NULL) && (src != NULL) && (size != 0)) (memcpy(dest, src, size));
}


//#define DEBUGAAC
//#define DEBUGH264
//#define DEBUGMPEG4PACK
//#define DEBUGPROCESSFRAMES
//#define DEBUGPROCESSFRAMESOUTPUT
#define FLVBUFSIZE 512*1024
struct PushReader
{
    AVFormatContext *context;
    AVPacket packet;
    int audiostream;
    int videostream;
    int spustream;
    long long lastPTSmsec;
    unsigned char *packetData; // data of the packet
    int packetType; // 0: video  1: audio  2: spu  -1: discard
    int packetLen;
    int packetBytesleft;  // how many bytes are left in current packet
    int eof;
    int flashconversion; // 0: no conversion 1:use flash conversion
    unsigned char *flvbuffer; // used for output in flv conversion
    short *predbuffer; // prediction buffer
    int fixPacked;
    int foundPacked;
    int frameCount;
    int countedBFrames; // Used to discard extra non-coded frames in packed bitstreams AVI
    int fixH264;
    int fixAAC;
    int sentAudioHeader;
    int sentVideoHeader;
    int sentSpuHeader;
    char tmpH264[4]; // Used for storing 00 00 00 01
    unsigned char *h264header; // Contains the modified sequence and picture headers...
    unsigned char *aacheader;
    int avcLength;
    int nalLen; // number of bytes left in current nal block
    int disableDecodeAudio; // disable decoding flac, vorbis, aflc
    int decodeAudio;        // Set to 1 if we are sending decoded pcm after decoding flac,vorbis,aflac
	unsigned long enableDecodeAudioType; //bit mask of audio be decoded
    AVCodec *audiocodec;
    unsigned char *audiobuffer;
    int vc1seqoffset;
    int vc1seqsize;
    int vc1entoffset;
    int vc1entsize;
    AVAudioConvert *convert_ctx;
    int bufferlen2;
    int bufferpos2;
};

#define COOK_AUDIO_MASK 0x0001

static FILE *logFile = NULL;
static int logControl = 0;
#define flog(x) _flog x

void _flog( int level, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	 
	if ( logControl < level || logFile == NULL ) return;

    char szInfo[1024*2];
    va_list va;
    va_start(va, cstr);

    vsnprintf(szInfo, sizeof(szInfo)-4, cstr, va);
    strncat(szInfo, "\r\n",  sizeof(szInfo) );
	fwrite( szInfo, 1, strlen(szInfo),  logFile ); 

	va_end(args);
}


//int bug_broken_dts=0;
static int countMpeg4Frames(unsigned char *data, int len, int *nFrames, int *nBFrames);
static int readH264Length(struct PushReader *t1, unsigned char *data,
                    int pos, int len);
static unsigned char * createH264Header(struct PushReader *t1, unsigned char *headerdata, int headerlen, int *lenout);
static int writeAACHeader(struct PushReader *t1, unsigned char *headerdata, int headerlen, unsigned char *dataout, int *lenout, int framelen);
static int parseVC1Header(struct PushReader *t1, unsigned char *headerdata, int headerlen);

#undef fprintf

int verfyVersion( int VersionOfLibAVFormat,  int SizeOFURLProtocol, int SizeOFURLContext )
{
	if ( VersionOfLibAVFormat != LIBAVFORMAT_VERSION_MAJOR )
		return 1;

	if ( SizeOFURLProtocol != sizeof( URLProtocol ))
		return 2;

	if (  SizeOFURLContext != sizeof(URLContext) )
		return 3;

	return 0;
}

struct PushReader *OpenPushReader( const char *filename, URLProtocol *private_protocol, unsigned long option )
{
    int retval;
    int i;
    struct PushReader *t1;
    int audiostream = -1;
    int videostream = -1;
    int spustream = -1;
    URLContext *uc;

    //av_log_level = AV_LOG_DEBUG;

    t1 = (struct PushReader *) av_malloc(sizeof(struct PushReader));
    if(t1 == NULL) return 0;
    memset(t1, 0, sizeof(struct PushReader));
    av_register_all();


    if ( private_protocol != NULL )
    {
    	av_register_protocol( private_protocol );
    	av_log(NULL, AV_LOG_ERROR,  "Set up SageTV private protocol \r\n" );
    } 

    av_register_protocol( &feeder_protocol );
    av_log(NULL, AV_LOG_ERROR,  "Set up  private file protocol for \r\n" );

    retval = av_open_input_file(&t1->context, filename, NULL, 0, NULL);
    
    //retval = av_open_input_file(&t1->context, filename, NULL, 0, NULL);
    if(retval<0)
    {
        av_log(NULL, AV_LOG_ERROR,  "retval %d\n",retval);
        return NULL;
    }

    uc = t1->context->pb->opaque;
    if(strstr(filename,".flv")!=NULL)
    {
        av_log(NULL, AV_LOG_ERROR, "Setting active file\n");
        uc->flags|=URL_ACTIVEFILE;
    }

    av_log(NULL, AV_LOG_ERROR, "find stream info\n");
    retval = av_find_stream_info(t1->context);
    av_log(NULL, AV_LOG_ERROR, "found stream info\n");
    av_read_play(t1->context);
    av_log(NULL, AV_LOG_ERROR,  "av_read_play done\n");

    t1->packetType=-1;
    t1->audiostream=-1;
    t1->videostream=-1;
    t1->spustream=-1;
    audiostream = -1;
    videostream = -1;

    for(i = 0; i < t1->context->nb_streams; i++)
    {
        AVCodecContext *enc = t1->context->streams[i]->codec;
        switch(enc->codec_type)
        {
            case CODEC_TYPE_AUDIO:
                if (audiostream < 0)
                    audiostream = i;
                break;
            case CODEC_TYPE_VIDEO:
                if (videostream < 0)
                    videostream = i;
                break;
            case CODEC_TYPE_SUBTITLE:
            	if (spustream < 0)
            		spustream = i;

            default:
                break;
        }
    }
    av_log(NULL, AV_LOG_ERROR,  "dump_format\n");
    dump_format(t1->context, 0, "", 0);
    if(audiostream >= 0)
    {
        t1->audiostream=audiostream;
    }
    if(videostream >= 0)
    {
        t1->videostream=videostream;
    }
    if (spustream >= 0 )
    	t1->spustream=spustream;
    return t1;
}

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
int getVideoCodecSettings(struct PushReader *t1, int *width, int *height, int *misc)
{
    int codecid;
    if(t1->videostream==-1)
    {
        return 0;
    }
    *width=t1->context->streams[t1->videostream]->codec->width;
    *height=t1->context->streams[t1->videostream]->codec->height;
    *misc=0;
    codecid=t1->context->streams[t1->videostream]->codec->codec_id;
    t1->flashconversion=0;
    switch(codecid)
    {
        case CODEC_ID_MPEG1VIDEO:
            return 2;
        case CODEC_ID_MPEG2VIDEO:
            return 2;
        case CODEC_ID_MSMPEG4V3:
            return 3;
        case CODEC_ID_MPEG4:
            fprintf(stderr, "Format is %s\n",t1->context->iformat->name);
            if(!strcmp(t1->context->iformat->name, "avi"))
            {
                int g= av_gcd(t1->context->streams[t1->videostream]->codec->time_base.num, 
                    t1->context->streams[t1->videostream]->codec->time_base.den);

                t1->fixPacked=1;
                av_log(NULL, AV_LOG_ERROR,  "MPEG4 video inside avi, enabling fixpacked\n");
                *misc=((t1->context->streams[t1->videostream]->codec->time_base.num/g)<<16) |
                    (t1->context->streams[t1->videostream]->codec->time_base.den/g)&0xFFFF;
            }
            return 4;
        case CODEC_ID_FLV1:
            //t1->flashconversion=1;                      //ZQ ffdshow don't need convert
            //if(*width>720 || *height>576) return -1;
            //if(!initFlashConversion(t1)) return -1;
            return 5;
		case CODEC_ID_WMV1:
			return 6;
		case CODEC_ID_WMV2:
			return 7;
        case CODEC_ID_VC1:
            return 8;
        case CODEC_ID_WMV3:
            if(t1->context->streams[t1->videostream]->codec->extradata && 
                t1->context->streams[t1->videostream]->codec->extradata_size>=4)
            {
                unsigned char *wmvheader = (unsigned char *)misc;
                wmvheader[0]=
                    t1->context->streams[t1->videostream]->codec->extradata[0];
                wmvheader[1]=
                    t1->context->streams[t1->videostream]->codec->extradata[1];
                wmvheader[2]=
                    t1->context->streams[t1->videostream]->codec->extradata[2];
                wmvheader[3]=
                    t1->context->streams[t1->videostream]->codec->extradata[3];
            }
            else
            {
                fprintf(stderr, "WMV9 without extradata?\n");
            }
            return 9;
        case CODEC_ID_H264:
            if((!strcmp(t1->context->iformat->name, "mov,mp4,m4a,3gp,3g2,mj2")) ||
                (!strcmp(t1->context->iformat->name, "matroska"))||
                (!strcmp(t1->context->iformat->name, "flv")))
            {
                t1->fixH264=1;
                t1->tmpH264[0]=0;
                t1->tmpH264[1]=0;
                t1->tmpH264[2]=0;
                t1->tmpH264[3]=1;
                av_log(NULL, AV_LOG_ERROR,  "H264 inside mp4\n");
            }
            return 10;
        case CODEC_ID_MJPEG:
            return 11;
        case CODEC_ID_VP6F:
        	return 12;
        case CODEC_ID_RV40:
        	return 13;
        default:
            av_log(NULL, AV_LOG_ERROR,  "Unsupported video codec %d\n", codecid);
            break;
    }
    t1->videostream==-1;
    return -1;
}

int getMpegVideoStreamInf( struct PushReader *t1, int *frame_rate_num, int *frame_rate_den, unsigned long *language  )
{
    int codecid;
    if(t1->videostream==-1)
    {
        return 0;
    }	
    if ( frame_rate_num )
    	*frame_rate_num = t1->context->streams[t1->videostream]->r_frame_rate.num;
    if ( frame_rate_den )
    	*frame_rate_den = t1->context->streams[t1->videostream]->r_frame_rate.den;
    *language = t1->context->streams[t1->videostream]->language[3];
    *language = (*language<<8)|t1->context->streams[t1->videostream]->language[2];
    *language = (*language<<8)|t1->context->streams[t1->videostream]->language[1];
    *language = (*language<<8)|t1->context->streams[t1->videostream]->language[0];
    
    return 1;
}

int getAudioStreamInf( struct PushReader *t1, unsigned long *language  )
{
    int codecid;
    if(t1->audiostream==-1)
    {
        return 0;
    }	 
    *language = t1->context->streams[t1->audiostream]->language[3];
    *language = (*language<<8)|t1->context->streams[t1->audiostream]->language[2];
    *language = (*language<<8)|t1->context->streams[t1->audiostream]->language[1];
    *language = (*language<<8)|t1->context->streams[t1->audiostream]->language[0];

    return 1;
}

int getSpuStreamInf( struct PushReader *t1, unsigned long *language  )
{
    int codecid;
    if(t1->spustream==-1)
    {
        return 0;
    }  
    *language = t1->context->streams[t1->spustream]->language[3];
    *language = (*language<<8)|t1->context->streams[t1->spustream]->language[2];
    *language = (*language<<8)|t1->context->streams[t1->spustream]->language[1];
    *language = (*language<<8)|t1->context->streams[t1->spustream]->language[0];

    return 1;
}

int GetVideoExtraData( struct PushReader *t1, unsigned char* pBuffer, int nBufferSize )
{
	int size =t1->context->streams[t1->videostream]->codec->extradata_size;
	if ( size > nBufferSize ) size = nBufferSize;
	av_memcpy( pBuffer, t1->context->streams[t1->videostream]->codec->extradata, size );
	return size;
}

int GetAudioExtraData( struct PushReader *t1, unsigned char* pBuffer, int nBufferSize )
{
	int size =t1->context->streams[t1->audiostream]->codec->extradata_size;
	if ( size > nBufferSize ) size = nBufferSize;
	av_memcpy( pBuffer, t1->context->streams[t1->audiostream]->codec->extradata, size );
	return size;
}

int getMpegVideoInfo(struct PushReader *t1, int *profile, int*level, int* interlaced, int *bitrate, int *misc )
{
	int codecid;
    if(t1->videostream==-1)
    {
        return 0;
    }
	*misc = 0;
    *profile = t1->context->streams[t1->videostream]->codec->profile;
    *level   = t1->context->streams[t1->videostream]->codec->level;
    *interlaced = t1->context->streams[t1->videostream]->codec->interlaced;
    *bitrate = t1->context->streams[t1->videostream]->codec->bit_rate;
    return t1->context->streams[t1->videostream]->codec->codec_id;;
}

int getAudioMpegInfo( struct PushReader *t1, int *sampe_fmt, int *layer, int *frame_size, int* mode )
{
    if(t1->audiostream==-1)
    {
        return 0;
    }
    *sampe_fmt = t1->context->streams[t1->audiostream]->codec->sample_fmt;
    *layer = t1->context->streams[t1->audiostream]->codec->sub_id;
    *frame_size = t1->context->streams[t1->audiostream]->codec->frame_size;
    *mode = t1->context->streams[t1->audiostream]->codec->me_method;
    return t1->context->streams[t1->audiostream]->codec->codec_id;
}
// 0 none
// 1 mpeg layer 1,2,3
// 2 aac
// 3 ac3
// 4 dts
// 5 wma
// 6 pcm
// 7 wma pro
// -1 unsupported
// TODO: what about dvd lpcm?
int getAudioCodecSettings(struct PushReader *t1, int *channels, int *bps, int *samplerate,
    int *bitrate, int *blockalign, int *misc)
{
    int codecid;
    if(t1->audiostream==-1)
    {
        return 0;
    }
    *channels=t1->context->streams[t1->audiostream]->codec->channels;
    *bps=16; // For now...
    *samplerate=t1->context->streams[t1->audiostream]->codec->sample_rate;
    *bitrate=t1->context->streams[t1->audiostream]->codec->bit_rate;
    *blockalign=t1->context->streams[t1->audiostream]->codec->block_align;
    *misc=0;
    fprintf(stderr, "getAudioCodecSettings %d %d %d %d %d\n",
        *channels,*bps,*samplerate,*bitrate,*blockalign);
    codecid=t1->context->streams[t1->audiostream]->codec->codec_id;

    switch(codecid)
    {
        case CODEC_ID_MP2:
        case CODEC_ID_MP3:
            return 1;
        case CODEC_ID_AAC:
            if(!strcmp(t1->context->iformat->name, "mov,mp4,m4a,3gp,3g2,mj2") ||
             !strcmp(t1->context->iformat->name, "matroska")||
             !strcmp(t1->context->iformat->name, "flv"))
            {
                t1->fixAAC=1;
            }
            return 2;
        case CODEC_ID_AC3:
            return 3;
        case CODEC_ID_DTS:
            return 4;
        // WMAV1 is not supported by the hardware playback?
        //case CODEC_ID_WMAV1:
        //    return -1;
        case CODEC_ID_WMALOSSLESS:
        case CODEC_ID_WMAPRO:
            if(t1->context->streams[t1->audiostream]->codec->extradata && 
                t1->context->streams[t1->audiostream]->codec->extradata_size>=16)
            {
                int i, headerlen,validbit,channelmask,options;
                unsigned char *headerdata =
                    t1->context->streams[t1->audiostream]->codec->extradata;
                headerlen=t1->context->streams[t1->audiostream]->codec->extradata_size;
                fprintf(stderr, "WMAPRO has %d bytes extradata\n", headerlen);
                for(i=0;i<headerlen;i++)
                {
                    fprintf(stderr, "%02X ",headerdata[i]);
                }
                validbit=t1->context->streams[t1->audiostream]->codec->extradata[0] |
                    (t1->context->streams[t1->audiostream]->codec->extradata[1]<<8);
                channelmask=(t1->context->streams[t1->audiostream]->codec->extradata[2]<<0)|
                    (t1->context->streams[t1->audiostream]->codec->extradata[3]<<8)|
                    (t1->context->streams[t1->audiostream]->codec->extradata[4]<<16)|
                    (t1->context->streams[t1->audiostream]->codec->extradata[5]<<24);
                options= (t1->context->streams[t1->audiostream]->codec->extradata[15]<<8)|
                    (t1->context->streams[t1->audiostream]->codec->extradata[14]);
                fprintf(stderr, "WMAPRO %X %X %X\n",validbit, channelmask, options);
                fprintf(stderr, "\n");
                *misc=
                    (t1->context->streams[t1->audiostream]->codec->extradata[0]<<24)|
                    (t1->context->streams[t1->audiostream]->codec->extradata[2]<<16)|
                    (t1->context->streams[t1->audiostream]->codec->extradata[15]<<8)|
                    (t1->context->streams[t1->audiostream]->codec->extradata[14]);
                fprintf(stderr, "misc set to %X\n",*misc);
            }
            return 7;
        case CODEC_ID_WMAV2:
            //t1->audiostream=-1;
            if(t1->context->streams[t1->audiostream]->codec->extradata && 
                t1->context->streams[t1->audiostream]->codec->extradata_size>=6)
            {
                int i, headerlen;
                unsigned char *headerdata =
                    t1->context->streams[t1->audiostream]->codec->extradata;
                headerlen=t1->context->streams[t1->audiostream]->codec->extradata_size;
                fprintf(stderr, "WMAV2 has %d bytes extradata\n", headerlen);
                for(i=0;i<headerlen;i++)
                {
                    fprintf(stderr, "%02X ",headerdata[i]);
                }
                fprintf(stderr, "\n");
                *misc=
                    (t1->context->streams[t1->audiostream]->codec->extradata[5]<<8)|
                    (t1->context->streams[t1->audiostream]->codec->extradata[4]);
                fprintf(stderr, "misc set to %X\n",*misc);
            }
            return 5;
        case CODEC_ID_PCM_S16BE ... CODEC_ID_PCM_S16LE_PLANAR:
        case CODEC_ID_PCM_S16LE:
        case CODEC_ID_ADPCM_IMA_QT ... CODEC_ID_ADPCM_EA_XAS:
			t1->decodeAudio = 1;
			return 6;
        case CODEC_ID_VORBIS:
			if ( t1->disableDecodeAudio ) return 11;
        case CODEC_ID_ALAC:
			if ( t1->disableDecodeAudio ) return 12;
        case CODEC_ID_FLAC:
			if ( t1->disableDecodeAudio ) return 13;
            t1->decodeAudio = 1;
            return 6;
        case CODEC_ID_EAC3:
        	return 8;
        case CODEC_ID_TRUEHD:
        	return 9;
        case CODEC_ID_COOK:
			if ( t1->enableDecodeAudioType&COOK_AUDIO_MASK )
			{
				t1->decodeAudio = 1; //ZQ too diffcult to get cook codec that isn't inclued in ffdshow
				return 6;
			}
        	return 10;
        default:
            av_log(NULL, AV_LOG_ERROR,  "Unsupported audio codec %d\n", codecid);
            t1->audiostream=-1;
            break;
    }
    return -1;
}

void CloseReader(struct PushReader *t1)
{
    if(t1->packetType!=-1)
    {
        av_free_packet(&t1->packet);
    }
    if(t1->flvbuffer) av_free(t1->flvbuffer);
    if(t1->predbuffer) av_free(t1->predbuffer);
    if(t1->h264header) av_free(t1->h264header);
    if(t1->aacheader) av_free(t1->aacheader);
    if (t1->convert_ctx)
    {
        av_audio_convert_free(t1->convert_ctx);
        t1->convert_ctx=NULL;
    }
    if(t1->decodeAudio && t1->audiocodec!=NULL)
    {
        avcodec_close(t1->context->streams[t1->audiostream]->codec);
    }
    if(t1->audiobuffer) av_free(t1->audiobuffer);
    av_close_input_file(t1->context);
    av_free(t1);
}

// Read data from the stream to the user buffer
// returns number of bytes put into buffer
int ProcessFrames(struct PushReader *t1, int *type, unsigned char *buffer, int bufferlen,
    int *haspts, long long *pts, int *duration)
{
    int retval;
    int bytesread=0;
    int sendPTS=1;
    int firstpacket=1;
    *type=-1;
    *haspts=0;
    *duration=0;

    #ifdef DEBUGPROCESSFRAMES
    fprintf(stderr, "Entered process frame\n");
    #endif
    while(bufferlen && !t1->eof)
    {
        if(t1->packetType==-1)
        {
    #ifdef DEBUGPROCESSFRAMES
            fprintf(stderr, "Calling av_read_frame\n");
            fflush(stderr);
    #endif
            retval = av_read_frame(t1->context, &t1->packet);
            if (retval < 0)
            {
//                fprintf(stderr, "Got retval %d reading frame\n",retval);
                t1->eof=1;
                return 0;
            }
    #ifdef DEBUGPROCESSFRAMESOUTPUT
            fprintf(stderr, "Got packet with stream_index %d len %d (V:%d A:%d S:%d) dts %lld pts %lld\n",
                t1->packet.stream_index, t1->packet.size, t1->videostream, t1->audiostream, t1->spustream, t1->packet.dts, t1->packet.pts);
            fflush(stderr);
    #endif
            // Packet size of 0 was confusing the audio decoder
            if(t1->packet.size<=0)
            {
                t1->packet.stream_index = -1;
            }
            if(t1->packet.stream_index == t1->videostream)
            {
                // we have a video packet
                if(t1->flashconversion)
                {
                    int used=0,outlen=0;
                    //av_log(NULL, AV_LOG_ERROR,  "convertframe %d bytes\n",t1->packet.size);
                    outlen=convertFrame(t1->packet.data, t1->packet.size+4, // TODO: verify if it is safe to read +4
                        t1->flvbuffer, FLVBUFSIZE, &used, t1->predbuffer);
                    //av_log(NULL, AV_LOG_ERROR,  "used %d, output %d\n",used, outlen);
                    t1->packetData = t1->flvbuffer;
                    t1->packetLen = outlen;
                    t1->packetBytesleft = outlen;
                    t1->packetType=0;
                }
                else if(t1->fixPacked)
                {
                    int nFrames=0;
                    int nBFrames=0;
                    t1->packetData = t1->packet.data;
                    t1->packetLen = t1->packet.size;
                    t1->packetBytesleft = t1->packet.size;
                    t1->packetType=0;
                    
                    // See how many B frames there 
                    // Skip if it is a non-coded extra frame
                    countMpeg4Frames(t1->packetData, t1->packetLen, &nFrames, &nBFrames);
                    if(nFrames>1)
                    {
                        t1->foundPacked=1;
                        sendPTS=0;
                    }
    #ifdef DEBUGMPEG4PACK
                    fprintf(stderr, "Found %d frames of which %d are B-frames. sendPTS: %d\n",nFrames, nBFrames, sendPTS);
    #endif
                    t1->countedBFrames+=nBFrames;
                    t1->frameCount+=nFrames;
                    if(nBFrames==1 && nFrames==1)
                    {
                        t1->countedBFrames-=1;
                    }
                    if(t1->packetLen<=12 && t1->countedBFrames)
                    {
    #ifdef DEBUGMPEG4PACK
                        fprintf(stderr, "Packet is short (%d) and we have %d bframes, discarding it\n", 
                            t1->packetLen, t1->countedBFrames);
    #endif
                        t1->countedBFrames-=1;
                        t1->frameCount-=1;
                        av_free_packet(&t1->packet);
                        t1->packetType=-1;
                        t1->packetData = NULL;
                        t1->packetLen = 0;
                        t1->packetBytesleft = 0;
                    }
                    // Since we don't get the PTS anymore in this case for AVI using packed frames we must derive it from DTS
                    else if(t1->packet.pts == AV_NOPTS_VALUE && nFrames==1 && t1->packet.dts != AV_NOPTS_VALUE)
                    {
                            t1->packet.pts = t1->packet.dts;
                            
                            /*fprintf(stderr, "video packet time using dts as pts %lld\n",
                                (long long) (av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                                t1->packet.pts*90000LL));*/
                    }
                }
                else
                {
                    t1->packetData = t1->packet.data;
                    t1->packetLen = t1->packet.size;
                    t1->packetBytesleft = t1->packet.size;
                    t1->packetType=0;
                }
                //av_log(NULL, AV_LOG_ERROR, "Got video packet %d bytes\n",t1->packet.size);
            }
            else if(t1->packet.stream_index == t1->audiostream)
            {
                // we have audio packet
                t1->packetData = t1->packet.data;
                t1->packetLen = t1->packet.size;
                t1->packetBytesleft = t1->packet.size;
                t1->packetType=1;
                //av_log(NULL, AV_LOG_ERROR, "Got audio packet %d bytes\n",t1->packet.size);
            }
            else if(t1->packet.stream_index == t1->spustream)
            {
                // we have subpicture packet
                t1->packetData = t1->packet.data;
                t1->packetLen = t1->packet.size;
                t1->packetBytesleft = t1->packet.size;
                t1->packetType=2;
            }
            else
            {
                av_free_packet(&t1->packet);
                t1->packetType=-1;
            }
        }

        if(t1->packetType!=-1 && ((t1->packetType==*type) || (*type==-1)))
        {
            if(t1->packetType==0 && t1->sentVideoHeader==0)
            {
                t1->sentVideoHeader=1;
                if(t1->videostream!=-1 && t1->fixH264 &&
                    t1->context->streams[t1->videostream]->codec->extradata_size)
                {
                    int hlen;
                    fprintf(stderr, "Parsing h264 avc extra header\n");
                    t1->h264header = createH264Header(t1,
                        t1->context->streams[t1->videostream]->codec->extradata,
                        t1->context->streams[t1->videostream]->codec->extradata_size,
                        &hlen);
                    if(t1->h264header && hlen<=bufferlen)
                    {
                        av_memcpy(buffer, t1->h264header, hlen);
                        bufferlen-=hlen;
                        buffer+=hlen;
                        bytesread+=hlen;
                        t1->sentVideoHeader=1;
                    }
                    else
                    {
                        fprintf(stderr, "buffer too short for h264 header\n");
                        goto bufferOut;
                    }
                }
                else if(t1->videostream!=-1 && 
                    t1->context->streams[t1->videostream]->codec->codec_id == CODEC_ID_MPEG4 && 
                    t1->context->streams[t1->videostream]->codec->extradata_size)
                {
                    int hlen=t1->context->streams[t1->videostream]->codec->extradata_size;
                    av_memcpy(buffer, 
                        t1->context->streams[t1->videostream]->codec->extradata,
                        hlen);
                    bufferlen-=hlen;
                    buffer+=hlen;
                    bytesread+=hlen;
                    t1->sentVideoHeader=1;
                }
                else if(t1->videostream!=-1 && 
                    t1->context->streams[t1->videostream]->codec->codec_id == CODEC_ID_VC1 && 
                    t1->context->streams[t1->videostream]->codec->extradata_size)
                {
                    int i;
                    int hlen=t1->context->streams[t1->videostream]->codec->extradata_size;
                    unsigned char *headerdata=t1->context->streams[t1->videostream]->codec->extradata;
                    for(i=0;i<hlen;i++)
                    {
                        fprintf(stderr, "%02X ",headerdata[i]);
                    }
                    fprintf(stderr, "\n");
                    parseVC1Header(t1, headerdata, hlen);
                    t1->sentVideoHeader=1;
                }
                else
                {
                    t1->sentVideoHeader=1;
                }
            }
            if(t1->packetType==2 && t1->sentSpuHeader==0)
            {
                t1->sentSpuHeader=1;
                if(t1->spustream!=-1 &&
                    t1->context->streams[t1->spustream]->codec->extradata_size)
                {
                    int hlen=t1->context->streams[t1->spustream]->codec->extradata_size;
                    av_memcpy(buffer, 
                        t1->context->streams[t1->spustream]->codec->extradata,
                        hlen);
                    bufferlen-=hlen;
                    buffer+=hlen;
                    bytesread+=hlen;
                }
            }
            if(t1->packetBytesleft==t1->packetLen) // First part of packet
            {
                if(sendPTS)
                {
                    if(t1->packet.pts != AV_NOPTS_VALUE)
                    {
                        if(firstpacket!=1) goto bufferOut;
                        *pts = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                            t1->packet.pts*90000LL;
                        *haspts=1;
                        firstpacket=0;
                    }
                    else if(t1->packet.dts != AV_NOPTS_VALUE)
                    {
                        if(firstpacket!=1) goto bufferOut;
                        *pts = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                            t1->packet.dts*90000LL;
                        *haspts=1;
                        firstpacket=0;
                    }
                }
                *duration = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                        t1->packet.convergence_duration*90000LL;

                if(0 && t1->packetType==0 && t1->videostream!=-1 &&
                    t1->context->streams[t1->videostream]->codec->codec_id == CODEC_ID_VC1 && 
                    t1->context->streams[t1->videostream]->codec->extradata_size)
                {
                    int i;
                    int hlen=4;
                    unsigned char headerdata[4]={0,0,1,0xD};
                    int hasheader=0;
                    // Find which headers are present in stream
                    if(t1->packetBytesleft >= 4)
                    {
                        if(t1->packetData[0]==0x00 &&
                            t1->packetData[1]==0x00 &&
                            t1->packetData[2]==0x01)
                        {
                            hasheader=1;
                        }
                        else if(bufferlen<128) // We must have enough space to write the header
                        {
                            goto bufferOut;
                        }
                        if(!hasheader || t1->packetData[3]!=0x0F)
                        {
                            av_memcpy(buffer, 
                                &t1->context->streams[t1->videostream]->codec->extradata[t1->vc1seqoffset],
                                t1->vc1seqsize);
                            bufferlen-=t1->vc1seqsize;
                            buffer+=t1->vc1seqsize;
                            bytesread+=t1->vc1seqsize;
                        }
                        if(!hasheader || (t1->packetData[3]!=0x0F && t1->packetData[3]!=0x0E))
                        {
                            av_memcpy(buffer, 
                                &t1->context->streams[t1->videostream]->codec->extradata[t1->vc1entoffset],
                                t1->vc1entsize);
                            bufferlen-=t1->vc1entsize;
                            buffer+=t1->vc1entsize;
                            bytesread+=t1->vc1entsize;
                        }
                        if(!hasheader || (t1->packetData[3]!=0x0F && t1->packetData[3]!=0x0E 
                            && t1->packetData[3]!=0x0D))
                        {
                            av_memcpy(buffer, 
                                headerdata,
                                hlen);
                            bufferlen-=hlen;
                            buffer+=hlen;
                            bytesread+=hlen;
                        }
                    }
                }
            }

            if(t1->decodeAudio && t1->packetType==1)
            {
                AVCodecContext *ctx = t1->context->streams[t1->audiostream]->codec;
                if(t1->audiocodec==NULL)
                {
                    t1->audiocodec = avcodec_find_decoder(ctx->codec_id);
                    if(t1->audiocodec==NULL)
                    {
                        fprintf(stderr, "Error on avcodec_find_decoder\n");
                        return 0;
                    }
                    if(avcodec_open(ctx, t1->audiocodec)<0)
                    {
                        fprintf(stderr, "Error on avcodec_open\n");
                        return 0;
                    }
                    t1->audiobuffer = (unsigned char *) av_malloc(AVCODEC_MAX_AUDIO_FRAME_SIZE);
                    if(t1->audiobuffer==NULL)
                    {
                        fprintf(stderr, "Error allocating audio buffer\n");
                        return 0;
                    }
                    if (ctx->sample_fmt != SAMPLE_FMT_S16) {
                        if (t1->convert_ctx)
                            av_audio_convert_free(t1->convert_ctx);
                        t1->convert_ctx = av_audio_convert_alloc(SAMPLE_FMT_S16, 1,
                                                                ctx->sample_fmt, 1, NULL, 0);
                        if (!t1->convert_ctx) {
                            fprintf(stderr, "Couldn't allocate audio converter  from %s\n",
                                avcodec_get_sample_fmt_name(ctx->sample_fmt));
                        }
                    }
                }
                {
                    int len;
                    if(t1->bufferlen2==0)
                    {
                        t1->bufferlen2=AVCODEC_MAX_AUDIO_FRAME_SIZE;
                        t1->bufferpos2=0;
    //                    fprintf(stderr, "avcodec_decode_audio2 %d %d\n",bufferlen2, t1->packetBytesleft);
                        len = avcodec_decode_audio2(ctx,
                            (int16_t *)t1->audiobuffer, &t1->bufferlen2,
                            &t1->packetData[t1->packetLen-t1->packetBytesleft],
                            t1->packetBytesleft);
    //                    fprintf(stderr, "after avcodec_decode_audio2 %d %d at %lld %d\n", len, bufferlen2, t1->packet.pts, 
    //                        t1->packetBytesleft);
                        // If we have read too much or got error, discard
                        if(len<0 || len>t1->packetBytesleft)
                        {
                            t1->packetBytesleft=0;
                            t1->bufferlen2=0;
                            t1->bufferpos2=0;
                        }
                        else
                        {
                            t1->packetBytesleft-=len;
                        }
                    }

                    // If we have decoded samples ready
                    if(t1->bufferlen2>0)
                    {
                        if (t1->convert_ctx) 
                        {
                            const void *ibuf[6]= {&t1->audiobuffer[t1->bufferpos2]};
                            void *obuf[6]= {buffer};
                            int istride[6]= {av_get_bits_per_sample_format(ctx->sample_fmt)/8};
                            int ostride[6]= {2};
                            int len= t1->bufferlen2/istride[0];
                            // We must limit conversion to what will fit in bufferlen
                            if(ostride[0]*len > bufferlen)
                            {
                                len=bufferlen/ostride[0];
                            }
                            if (av_audio_convert(t1->convert_ctx, obuf, ostride, ibuf, istride, len)<0) 
                            {
                                fprintf(stderr, "av_audio_convert() error\n");
                            }
                            bytesread+=len*2;
                            buffer+=len*2;
                            bufferlen-=len*2;
                            t1->bufferpos2+=len*istride[0];
                            t1->bufferlen2-=len*istride[0];
                        }
                        else
                        {
                            int len;
                            if(t1->bufferlen2<bufferlen)
                            {
                                len=t1->bufferlen2;
                            }
                            else
                            {
                                len=bufferlen;
                            }
                            av_memcpy(buffer, &t1->audiobuffer[t1->bufferpos2], len);
                            bytesread+=len;
                            buffer+=len;
                            bufferlen-=len;
                            t1->bufferpos2+=len;
                            t1->bufferlen2-=len;
                        }
                        firstpacket=0;
                    }
                }
            }
            else if(t1->fixH264 && t1->avcLength!=0 && t1->packetType==0)
            {
                int i;
                if(t1->nalLen==0) // We don't already started sending a packet
                {
                    int copySize;
                    // Write the 00 00 00 01 header
                    if(bufferlen<4) goto bufferOut;
                    av_memcpy(buffer, t1->tmpH264, 4);
                    buffer+=4;
                    bytesread+=4;
                    bufferlen-=4;
                    // get the size from the stream
                    t1->nalLen = readH264Length(t1, t1->packetData,
                        t1->packetLen-t1->packetBytesleft, t1->packetLen);
    #ifdef DEBUGH264
                    fprintf(stderr, "Block of nalLen %d at %d in block of size %d\n",
                        t1->nalLen, t1->packetLen-t1->packetBytesleft, t1->packetLen);
    #endif
                    copySize=bufferlen;
                    if(copySize>t1->nalLen) copySize=t1->nalLen;
                    av_memcpy(buffer, &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
                    bytesread+=copySize;
                    buffer+=copySize;
                    bufferlen-=copySize;
                    t1->packetBytesleft-=copySize;
                    t1->nalLen-=copySize;
                }
                else
                {
                    int copySize;
    #ifdef DEBUGH264
                    fprintf(stderr, "Block from nalLen left %d, at %d\n",
                        t1->nalLen, t1->packetLen-t1->packetBytesleft);
    #endif
                    copySize=bufferlen;
                    if(copySize>t1->nalLen) copySize=t1->nalLen;
                    av_memcpy(buffer, &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
                    bytesread+=copySize;
                    buffer+=copySize;
                    bufferlen-=copySize;
                    t1->packetBytesleft-=copySize;
                    t1->nalLen-=copySize;
                }
            }
            else if(t1->fixAAC && t1->packetType==1)
            {
                if(t1->context->streams[t1->audiostream]->codec->extradata_size)
                {
                    int hlen;
                    int retval;
                    retval = writeAACHeader(t1,
                        t1->context->streams[t1->audiostream]->codec->extradata,
                        t1->context->streams[t1->audiostream]->codec->extradata_size,
                        buffer,
                        &hlen, t1->packetBytesleft);
                    bytesread+=7;
                    buffer+=7;
                    bufferlen-=7;
                    if(retval)
                    {
                        int copySize=bufferlen;
                        if(copySize>t1->packetBytesleft) copySize=t1->packetBytesleft;
                        #ifdef DEBUGAAC
                        fprintf(stderr, "fixAAC memcpy %X %X %X\n", buffer,
                            &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
                        #endif
                        av_memcpy(buffer, &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
                        bytesread+=copySize;
                        buffer+=copySize;
                        bufferlen-=copySize;
                        t1->packetBytesleft-=copySize;
                    }
                    else
                    {
                        fprintf(stderr, "error on create aac header\n");
                        goto bufferOut;
                    }
                }
            }
            else
            {
                int copySize=bufferlen;
                if(copySize>t1->packetBytesleft) copySize=t1->packetBytesleft;
                av_memcpy(buffer, &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
                bytesread+=copySize;
                buffer+=copySize;
                bufferlen-=copySize;
                t1->packetBytesleft-=copySize;
            }
            if(bytesread>0)
            {
                *type=t1->packetType;
                firstpacket=0;
            }
            if(t1->packetBytesleft==0)
            {
                av_free_packet(&t1->packet);
                t1->packetType=-1;
                t1->packetData = NULL;
                t1->packetLen = 0;
                t1->packetBytesleft = 0;
            }
            if(t1->decodeAudio && bytesread>0 && *type==1)
            {
                goto bufferOut;
            }
            // Don't merge subpicture packets
            if(bytesread>0 && *type==2)
            {
                goto bufferOut;
            }
        }
        else
        {
            goto bufferOut;
        }
        // We need to follow the SendPTS flag...
        if(t1->fixPacked) 
            break;
    }
bufferOut:
    #ifdef DEBUGPROCESSFRAMES
    fprintf(stderr, "ProcessFrames with packetype %d bytesread %d\n",*type, bytesread);
    fflush(stderr);
    #endif
    return bytesread;
}

//ZQ.
static int ReadFrame( struct PushReader *t1 )
{
	int retval;
	if(t1->packetType!=-1)	
	{
		fprintf(stderr, "Error: a frame isn't processed and released!\n");
		return -1;
	}

	while ( !t1->eof &&  t1->packetType==-1)
	{
		retval = av_read_frame(t1->context, &t1->packet);
		if (retval < 0)
		{
			t1->eof=1;
			return 0;
		}
		//flog(( 1, "%x\t%d", t1->packet.size, t1->packet.stream_index  ));
		// Packet size of 0 was confusing the audio decoder
		if(t1->packet.size<=0)
		{
			t1->packet.stream_index = -1;
			flog(( 1, "trace: size is negtive ", t1->packet.size, t1->packetType  ));
		} 

		
		t1->packetData = t1->packet.data;
		t1->packetLen = t1->packet.size;
		t1->packetBytesleft = t1->packet.size;
		

		if(t1->packet.stream_index == t1->videostream && t1->videostream >=0)
			t1->packetType = 0;
		else
		if(t1->packet.stream_index == t1->audiostream && t1->audiostream >= 0 )
			t1->packetType = 1;
		else
		if(t1->packet.stream_index == t1->spustream && t1->spustream >= 0 )
		{
			t1->packetType = 2;
		}
		else
		{
			//flog(( 1, "Unkown stream:%d %d %d", t1->spustream, t1->packet.size, t1->packetType  ));
			//free unknown packet
			av_free_packet(&t1->packet);
			t1->packetType=-1;
			t1->packetData = NULL;
			t1->packetLen = 0;
			t1->packetBytesleft = 0;
		}
	}
	return t1->packetType >= 0;
}

// Read data from the stream to the user buffer
// returns number of bytes put into buffer
int ProcessVideoFrames(struct PushReader *t1, unsigned char *buffer, int bufferlen,
    int *haspts, long long *pts, int *duration )
{
    int bytesread=0;
    int sendPTS=1;
    int firstpacket=1;
    *haspts=0;
    *duration=0;

	if(t1->packetType>=0 && t1->packetType != 0)
		return 0;

    while(bufferlen && !t1->eof)
    {
        if(t1->packetType==-1) //if no data avaliable
        {
			int retval = ReadFrame( t1 );
			if ( retval <= 0 )
				return retval;
    
			//flog(( 3, "trace1: video read:0x%x type:%d ", t1->packet.size, t1->packetType  ));
        } /* end of read a fream */

		//it is not video frame, yield to other frame processing
        if( t1->packetType !=0  )
			return bytesread;
		
        {
            if(t1->flashconversion)
            {
                int used=0,outlen=0;
                outlen=convertFrame(t1->packet.data, t1->packet.size+4, // TODO: verify if it is safe to read +4
                    t1->flvbuffer, FLVBUFSIZE, &used, t1->predbuffer);
                t1->packetData = t1->flvbuffer;
                t1->packetLen = outlen;
                t1->packetBytesleft = outlen;
            }
            else if(t1->fixPacked)
            {
                int nFrames=0;
                int nBFrames=0;
                t1->packetData = t1->packet.data;
                t1->packetLen = t1->packet.size;
                t1->packetBytesleft = t1->packet.size;
                
                // See how many B frames there 
                // Skip if it is a non-coded extra frame
                countMpeg4Frames(t1->packetData, t1->packetLen, &nFrames, &nBFrames);
                if(nFrames>1)
                {
                    t1->foundPacked=1;
                    sendPTS=0;
                }
                t1->countedBFrames+=nBFrames;
                t1->frameCount+=nFrames;
                if(nBFrames==1 && nFrames==1)
                {
                    t1->countedBFrames-=1;
                }
                if(t1->packetLen<=12 && t1->countedBFrames)
                {
                    t1->countedBFrames-=1;
                    t1->frameCount-=1;
					//release a frame
                    av_free_packet(&t1->packet);
                    t1->packetType=-1;
                    t1->packetData = NULL;
                    t1->packetLen = 0;
                    t1->packetBytesleft = 0;
                }
                // Since we don't get the PTS anymore in this case for AVI using packed frames we must derive it from DTS
                else if(t1->packet.pts == AV_NOPTS_VALUE && nFrames==1 && t1->packet.dts != AV_NOPTS_VALUE)
                {
                     t1->packet.pts = t1->packet.dts;
                }
            }

        }
		if( t1->sentVideoHeader==0 ) //send a header
		{
			t1->sentVideoHeader=1;
			if(t1->videostream!=-1 && t1->fixH264 &&
				t1->context->streams[t1->videostream]->codec->extradata_size)
			{
				int hlen;
				fprintf(stderr, "Parsing h264 avc extra header\n");
				t1->h264header = createH264Header(t1,
					t1->context->streams[t1->videostream]->codec->extradata,
					t1->context->streams[t1->videostream]->codec->extradata_size,
					&hlen);
				if(t1->h264header && hlen<=bufferlen)
				{
					av_memcpy(buffer, t1->h264header, hlen);
					bufferlen-=hlen;
					buffer+=hlen;
					bytesread+=hlen;
					t1->sentVideoHeader=1;
				}
				else
				{
					fprintf(stderr, "buffer too short for h264 header\n");
					break;
					//goto bufferOut;
				}
			}
			else if(t1->videostream!=-1 && 
				t1->context->streams[t1->videostream]->codec->codec_id == CODEC_ID_MPEG4 && 
				t1->context->streams[t1->videostream]->codec->extradata_size)
			{
				int hlen=t1->context->streams[t1->videostream]->codec->extradata_size;
				av_memcpy(buffer, 
					t1->context->streams[t1->videostream]->codec->extradata,
					hlen);
				bufferlen-=hlen;
				buffer+=hlen;
				bytesread+=hlen;
				t1->sentVideoHeader=1;
			}
			else if( t1->context->streams[t1->videostream]->codec->codec_id == CODEC_ID_VC1 && 
					 t1->context->streams[t1->videostream]->codec->extradata_size)
			{
				int i;
				int hlen=t1->context->streams[t1->videostream]->codec->extradata_size;
				unsigned char *headerdata=t1->context->streams[t1->videostream]->codec->extradata;
				for(i=0;i<hlen;i++)
				{
					fprintf(stderr, "%02X ",headerdata[i]);
				}
				fprintf(stderr, "\n");
				parseVC1Header(t1, headerdata, hlen);
				t1->sentVideoHeader=1;
			}
			else
			{
				t1->sentVideoHeader=1;
			}
		}

        if(t1->packetBytesleft==t1->packetLen) // First part of packet
        {
            if( sendPTS )
            {
                if( t1->packet.pts != AV_NOPTS_VALUE)
                {
                	if ( firstpacket!=1 ) break;
                    *pts = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                        t1->packet.pts*90000LL;
                    *haspts=1;
                    firstpacket=0;
                }
                else if(t1->packet.dts != AV_NOPTS_VALUE)
                {
                	if ( firstpacket!=1 ) break;
                    *pts = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                        t1->packet.dts*90000LL;
                    *haspts=1;
                    firstpacket=0;
                }
            }
            *duration = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                    t1->packet.convergence_duration*90000LL;

            if( 0 && t1->videostream!=-1 &&
                t1->context->streams[t1->videostream]->codec->codec_id == CODEC_ID_VC1 && 
                t1->context->streams[t1->videostream]->codec->extradata_size)
            {
                int i;
                int hlen=4;
                unsigned char headerdata[4]={0,0,1,0xD};
                int hasheader=0;
                // Find which headers are present in stream
                if(t1->packetBytesleft >= 4)
                {
                    if(t1->packetData[0]==0x00 &&
                        t1->packetData[1]==0x00 &&
                        t1->packetData[2]==0x01)
                    {
                        hasheader=1;
                    }
                    else if(bufferlen<128) // We must have enough space to write the header
                    {
                       break;
                    }
                    if(!hasheader || t1->packetData[3]!=0x0F)
                    {
                        av_memcpy(buffer, 
                            &t1->context->streams[t1->videostream]->codec->extradata[t1->vc1seqoffset],
                            t1->vc1seqsize);
                        bufferlen-=t1->vc1seqsize;
                        buffer+=t1->vc1seqsize;
                        bytesread+=t1->vc1seqsize;
                    }
                    if(!hasheader || (t1->packetData[3]!=0x0F && t1->packetData[3]!=0x0E))
                    {
                        av_memcpy(buffer, 
                            &t1->context->streams[t1->videostream]->codec->extradata[t1->vc1entoffset],
                            t1->vc1entsize);
                        bufferlen-=t1->vc1entsize;
                        buffer+=t1->vc1entsize;
                        bytesread+=t1->vc1entsize;
                    }
                    if(!hasheader || (t1->packetData[3]!=0x0F && t1->packetData[3]!=0x0E 
                        && t1->packetData[3]!=0x0D))
                    {
                        av_memcpy(buffer, 
                            headerdata,
                            hlen);
                        bufferlen-=hlen;
                        buffer+=hlen;
                        bytesread+=hlen;
                    }
                }
            }
        }
		if(t1->fixH264 && t1->avcLength!=0)
        {
            int i;
            if(t1->nalLen==0) // We don't already started sending a packet
            {
                int copySize;
                // Write the 00 00 00 01 header
                if(bufferlen<4) 
					break; //goto bufferOut;
                av_memcpy(buffer, t1->tmpH264, 4);
                buffer+=4;
                bytesread+=4;
                bufferlen-=4;
                // get the size from the stream
                t1->nalLen = readH264Length(t1, t1->packetData,
                    t1->packetLen-t1->packetBytesleft, t1->packetLen);
                copySize=bufferlen;
                if(copySize>t1->nalLen) copySize=t1->nalLen;
                av_memcpy(buffer, &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
                bytesread+=copySize;
                buffer+=copySize;
                bufferlen-=copySize;
                t1->packetBytesleft-=copySize;
                t1->nalLen-=copySize;
            }
            else
            {
                int copySize;
                copySize=bufferlen;
                if(copySize>t1->nalLen) copySize=t1->nalLen;
                av_memcpy(buffer, &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
                bytesread+=copySize;
                buffer+=copySize;
                bufferlen-=copySize;
                t1->packetBytesleft-=copySize;
                t1->nalLen-=copySize;
            }
        }
        else
        {
            int copySize=bufferlen;
            if(copySize>t1->packetBytesleft) copySize=t1->packetBytesleft;
            av_memcpy(buffer, &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
            bytesread+=copySize;
            buffer+=copySize;
            bufferlen-=copySize;
            t1->packetBytesleft-=copySize;
        }
        if(bytesread>0)
            firstpacket=0;

		if(t1->packetBytesleft==0)
        {
            av_free_packet(&t1->packet);
            t1->packetType=-1;
            t1->packetData = NULL;
            t1->packetLen = 0;
            t1->packetBytesleft = 0;
        }

        if(t1->fixPacked) 
            break;
    }

    return bytesread;
}
// end processVideoFrames

int ProcessAudioFrames(struct PushReader *t1, unsigned char *buffer, int bufferlen,
    int *haspts, long long *pts, int *duration)
{
    int bytesread=0;
    int sendPTS=1;
    int firstpacket=1;
    *haspts=0;
    *duration=0;

    while( bufferlen && !t1->eof )
    {
        if( t1->packetType==-1)
        {
			int retval = ReadFrame( t1 );
			if ( retval <= 0 )
				return retval;
			//flog(( 3, "trace0: audio read:0x%x type:%d ", t1->packet.size, t1->packetType  ));
        }

		//it is not audio frame, yield to other frame processing
        if( t1->packetType != 1  )
			return bytesread;
        
        if( t1->packetBytesleft==t1->packetLen ) // First part of packet
        {
            if(sendPTS)
            {
                if(t1->packet.pts != AV_NOPTS_VALUE)
                {
                	if ( firstpacket!=1 ) break;
                    *pts = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                        t1->packet.pts*90000LL;
                    *haspts=1;
                    firstpacket=0;
                }
                else if(t1->packet.dts != AV_NOPTS_VALUE)
                {
                	if ( firstpacket!=1 ) break;
                    *pts = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                        t1->packet.dts*90000LL;
                    *haspts=1;
                    firstpacket=0;
                }
            }
            *duration = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                    t1->packet.convergence_duration*90000LL;


        }
        if( t1->decodeAudio )
        {
            AVCodecContext *ctx = t1->context->streams[t1->audiostream]->codec;
            if(t1->audiocodec==NULL)
            {
                t1->audiocodec = avcodec_find_decoder(ctx->codec_id);
                if(t1->audiocodec==NULL)
                {
                    fprintf(stderr, "Error on avcodec_find_decoder\n");
                    return 0;
                }
                if(avcodec_open(ctx, t1->audiocodec)<0)
                {
                    fprintf(stderr, "Error on avcodec_open\n");
                    return 0;
                }
                t1->audiobuffer = (unsigned char *) av_malloc(AVCODEC_MAX_AUDIO_FRAME_SIZE);
                if( t1->audiobuffer==NULL )
                {
                    fprintf(stderr, "Error allocating audio buffer\n");
                    return 0;
                }
                if (ctx->sample_fmt != SAMPLE_FMT_S16) {
                    if (t1->convert_ctx)
                        av_audio_convert_free(t1->convert_ctx);
                    t1->convert_ctx = av_audio_convert_alloc(SAMPLE_FMT_S16, 1,
                                                            ctx->sample_fmt, 1, NULL, 0);
                    if (!t1->convert_ctx) {
                        fprintf(stderr, "Couldn't allocate audio converter  from %s\n",
                            avcodec_get_sample_fmt_name(ctx->sample_fmt));
                    }
                }
            }
            {
                int len;
                if(t1->bufferlen2==0)
                {
                    t1->bufferlen2=AVCODEC_MAX_AUDIO_FRAME_SIZE;
                    t1->bufferpos2=0;

                    len = avcodec_decode_audio2(ctx,
                        (int16_t *)t1->audiobuffer, &t1->bufferlen2,
                        &t1->packetData[t1->packetLen-t1->packetBytesleft],
                        t1->packetBytesleft);
					//flog(( 3, "trace1: audio decode: %d %d ", t1->bufferlen2, len  ));
                    if(len<0 || len>t1->packetBytesleft)
                    {
                        t1->packetBytesleft=0;
                        t1->bufferlen2=0;
                        t1->bufferpos2=0;
                    }
                    else
                    {
                        t1->packetBytesleft-=len;
                    }
					
                }

                // If we have decoded samples ready
                if(t1->bufferlen2>0)
                {
                    if (t1->convert_ctx) 
                    {
                        const void *ibuf[6]= {&t1->audiobuffer[t1->bufferpos2]};
                        void *obuf[6]= {buffer};
                        int istride[6]= {av_get_bits_per_sample_format(ctx->sample_fmt)/8};
                        int ostride[6]= {2};
                        int len= t1->bufferlen2/istride[0];
                        // We must limit conversion to what will fit in bufferlen
                        if(ostride[0]*len > bufferlen)
                        {
                            len=bufferlen/ostride[0];
                        }
                        if (av_audio_convert(t1->convert_ctx, obuf, ostride, ibuf, istride, len)<0) 
                        {
                            fprintf(stderr, "av_audio_convert() error\n");
                        }
                        bytesread+=len*2;
                        buffer+=len*2;
                        bufferlen-=len*2;
                        t1->bufferpos2+=len*istride[0];
                        t1->bufferlen2-=len*istride[0];
                    }
                    else
                    {
                        int len;
                        if(t1->bufferlen2<bufferlen)
                        {
                            len=t1->bufferlen2;
                        }
                        else
                        {
                            len=bufferlen;
                        }
                        av_memcpy(buffer, &t1->audiobuffer[t1->bufferpos2], len);
                        bytesread+=len;
                        buffer+=len;
                        bufferlen-=len;
                        t1->bufferpos2+=len;
                        t1->bufferlen2-=len;
						//flog(( 3, "trace2: audio output: %d %d ",  bytesread ));
                    }
                    firstpacket=0;
                }
            }
        } else
        if( t1->fixAAC && t1->packetType==1 )
        {
            if(t1->context->streams[t1->audiostream]->codec->extradata_size)
            {
                int hlen;
                int retval;
                retval = writeAACHeader(t1,
                    t1->context->streams[t1->audiostream]->codec->extradata,
                    t1->context->streams[t1->audiostream]->codec->extradata_size,
                    buffer,
                    &hlen, t1->packetBytesleft);
                bytesread+=7;
                buffer+=7;
                bufferlen-=7;
                if(retval)
                {
                    int copySize=bufferlen;
                    if(copySize>t1->packetBytesleft) copySize=t1->packetBytesleft;
                    #ifdef DEBUGAAC
                    fprintf(stderr, "fixAAC memcpy %X %X %X\n", buffer,
                        &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
                    #endif
                    av_memcpy(buffer, &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
                    bytesread+=copySize;
                    buffer+=copySize;
                    bufferlen-=copySize;
                    t1->packetBytesleft-=copySize;
                }
                else
                {
                    fprintf(stderr, "error on create aac header\n");
                    break;//goto bufferOut;
                }
            }
        }
        else 
        {
            int copySize=bufferlen;
            if(copySize>t1->packetBytesleft) copySize=t1->packetBytesleft;
            av_memcpy(buffer, &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
            bytesread+=copySize;
            buffer+=copySize;
            bufferlen-=copySize;
            t1->packetBytesleft-=copySize;
        }

        if(bytesread>0)
            firstpacket=0;

        if(t1->packetBytesleft==0)
        {
            av_free_packet(&t1->packet);
            t1->packetType=-1;
            t1->packetData = NULL;
            t1->packetLen = 0;
            t1->packetBytesleft = 0;
        }

        //if(t1->decodeAudio && bytesread>0 )
		if( bytesread>0 )
			break;

        if(t1->fixPacked) 
            break;
    }


    return bytesread;
}
//end of ProcessAudio

// Read data from the stream to the user buffer
// returns number of bytes put into buffer
int ProcessSubPictureFrames(struct PushReader *t1, unsigned char *buffer, int bufferlen,
    int *haspts, long long *pts, int *duration)
{
    int bytesread=0;
    int sendPTS=1;
    int firstpacket=1;
    *haspts=0;
    *duration=0;

    while(bufferlen && !t1->eof)
    {
        if(t1->packetType==-1)
        {
  			int retval = ReadFrame( t1 );
			if ( retval <= 0 )
				return retval;
        }

		//it is not subpicture frame, yield to other frame processing
        if( t1->packetType != 2 )
			return bytesread;
      
		if(t1->packetType==2 && t1->sentSpuHeader==0)
		{
			t1->sentSpuHeader=1;
			if(t1->spustream!=-1 &&
				t1->context->streams[t1->spustream]->codec->extradata_size)
			{
				int hlen=t1->context->streams[t1->spustream]->codec->extradata_size;
				av_memcpy(buffer, 
					t1->context->streams[t1->spustream]->codec->extradata,
					hlen);
				bufferlen-=hlen;
				buffer+=hlen;
				bytesread+=hlen;
			}
		}
		if(t1->packetBytesleft==t1->packetLen) // First part of packet
		{
			if(sendPTS)
			{
				if(t1->packet.pts != AV_NOPTS_VALUE)
				{
					if ( firstpacket!=1 ) break;
					*pts = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
						t1->packet.pts*90000LL;
					*haspts=1;
					firstpacket=0;
				}
				else if(t1->packet.dts != AV_NOPTS_VALUE)
				{
					if ( firstpacket!=1 ) break;
					*pts = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
						t1->packet.dts*90000LL;
					*haspts=1;
					firstpacket=0;
				}
			}
			*duration = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
					t1->packet.convergence_duration*90000LL;


		}
		
		{
			int copySize=bufferlen;
			if(copySize>t1->packetBytesleft) copySize=t1->packetBytesleft;
			av_memcpy(buffer, &t1->packetData[t1->packetLen-t1->packetBytesleft], copySize);
			//flog(( 1, "trace spu packet:%d %s, left:%d",  copySize, buffer, t1->packetBytesleft ));
			bytesread+=copySize;
			buffer+=copySize;
			bufferlen-=copySize;
			t1->packetBytesleft-=copySize;
		}

		if(bytesread>0)
			firstpacket=0;

		if(t1->packetBytesleft==0)
		{
			av_free_packet(&t1->packet);
			t1->packetType=-1;
			t1->packetData = NULL;
			t1->packetLen = 0;
			t1->packetBytesleft = 0;
			//flog(( 1, "trace 1 %d.", bytesread ));
		}
		// Don't merge subpicture packets
		if( bytesread>0 )
			break;

		if(t1->fixPacked) 
            break;
    }

	//flog(( 1, "trace 2 byte read%d.", bytesread ));
	fprintf(stdout,"Process SPU  byte read%d\n", bytesread );
	return bytesread;
}

//end of ProcessSubPicture
long long getFirstTime(struct PushReader *t1)
{
    return t1->context->start_time/AV_TIME_BASE*1000;
}

long long getDuration(struct PushReader *t1)
{
    return t1->context->duration*1000/AV_TIME_BASE;
}

int getEOF(struct PushReader *t1)
{
    return t1->eof;
}

int setFixH264( struct PushReader *t1, int val )
{
	int fixH264_flag = t1->fixH264;
	t1->fixH264 = val;
	return fixH264_flag;
}

int stateFixH264( struct PushReader *t1 )
{
	return t1->fixH264;
}

void disableDecodeAudio( struct PushReader *t1 )
{
	t1->disableDecodeAudio = 1;
}

void enableDecodeCookAudio( struct PushReader *t1 )
{
	t1->enableDecodeAudioType |= COOK_AUDIO_MASK;
}

int prSeek(struct PushReader *t1, long long time)
{
    int retval, index=0;
    av_log(NULL, AV_LOG_ERROR,  "PushReader trying to seek to %lld\n", time);
    retval = av_seek_frame(t1->context, -1, time*AV_TIME_BASE/1000, AVSEEK_FLAG_BACKWARD);
    av_log(NULL, AV_LOG_ERROR,  "retval %d\n", retval);
    while(retval<0 && index<t1->context->nb_streams)
    {
        av_log(NULL, AV_LOG_ERROR,  "Fallback PushReader trying to seek to %lld with index %d\n", time, index);
        retval = av_seek_frame(t1->context, index,
            av_rescale(time, t1->context->streams[index]->time_base.den, 1000 * t1->context->streams[index]->time_base.num), AVSEEK_FLAG_BACKWARD);
        av_log(NULL, AV_LOG_ERROR,  "retval %d\n", retval);
        index++;
    }
    if(t1->packetType!=-1)
    {
        av_free_packet(&t1->packet);
        t1->packetType=-1;
        t1->packetData = NULL;
        t1->packetLen = 0;
        t1->packetBytesleft = 0;
    }
    if(t1->fixPacked)
    {
        t1->countedBFrames=0; // Reset our B-frame counter
        t1->frameCount=0;
    }
    t1->sentVideoHeader=0;
    t1->sentAudioHeader=0;
    t1->sentSpuHeader=0;
    t1->nalLen=0;
    t1->eof=0;
    if(t1->decodeAudio && t1->audiocodec!=NULL)
    {
        AVCodecContext *ctx = t1->context->streams[t1->audiostream]->codec;
        avcodec_flush_buffers(ctx);
    }
    return 1;
}

int prGetStreamCounts(struct PushReader *t1, int *videostreams, int *audiostreams, int *spustreams)
{
    int i;
    *videostreams=0;
    *audiostreams=0;
    *spustreams=0;
    for(i = 0; i < t1->context->nb_streams; i++)
    {
        AVCodecContext *enc = t1->context->streams[i]->codec;
        switch(enc->codec_type)
        {
            case CODEC_TYPE_AUDIO:
                *audiostreams+=1;
                break;
            case CODEC_TYPE_VIDEO:
                *videostreams+=1;
                break;
            case CODEC_TYPE_SUBTITLE:
                *spustreams+=1;
                break;
            default:
                break;
        }
    }
    return 1;
}

//prSetAudioStream scares me, because of avcodec_close, I create this API for switching audio
int prSelectAudioStream(struct PushReader *t1, int stream)
{
    int i;
    int videostreams=0;
    int audiostreams=0;
    int spustreams=0;
    t1->audiostream=-1;
    for(i = 0; i < t1->context->nb_streams; i++)
    {
        AVCodecContext *enc = t1->context->streams[i]->codec;
        switch(enc->codec_type)
        {
            case CODEC_TYPE_AUDIO:
                if(audiostreams==stream)
                    t1->audiostream=i;
                audiostreams+=1;
                break;
            case CODEC_TYPE_VIDEO:
                videostreams+=1;
                break;
            default:
                break;
        }
    }
	if ( t1->audiostream >= 0 )
	{
		int codecid=t1->context->streams[t1->audiostream]->codec->codec_id;
		if ( ( CODEC_ID_ADPCM_IMA_QT <= codecid && codecid <= CODEC_ID_ADPCM_EA_XAS ) )
		{
			t1->decodeAudio = 1;
		}
		else
		if ( ( codecid == CODEC_ID_VORBIS || codecid == CODEC_ID_ALAC || codecid == CODEC_ID_FLAC ) )
		{
			if ( !t1->disableDecodeAudio )
				t1->decodeAudio = 1;
			else
				t1->decodeAudio = 0;
		} else
		if ( codecid == CODEC_ID_COOK && ( t1->enableDecodeAudioType&COOK_AUDIO_MASK ) ) 
		{
			t1->decodeAudio = 1;
		}
		else
			t1->decodeAudio = 0;
	}

    return t1->audiostream==-1 ? 0 : 1;
}

int prSetVideoStream(struct PushReader *t1, int stream)
{
    int i;
    int videostreams=0;
    int audiostreams=0;
    int spustreams=0;
    t1->videostream=-1;
    for(i = 0; i < t1->context->nb_streams; i++)
    {
        AVCodecContext *enc = t1->context->streams[i]->codec;
        switch(enc->codec_type)
        {
            case CODEC_TYPE_AUDIO:
                audiostreams+=1;
                break;
            case CODEC_TYPE_VIDEO:
                if(videostreams==stream)
                    t1->videostream=i;
                videostreams+=1;
                break;
            case CODEC_TYPE_SUBTITLE:
                spustreams+=1;
                break;
            default:
                break;
        }
    }
    return t1->videostream==-1 ? 0 : 1;
}

int prSetAudioStream(struct PushReader *t1, int stream)
{
    int i;
    int videostreams=0;
    int audiostreams=0;
    int spustreams=0;
    if(t1->decodeAudio && t1->audiobuffer)
    {
        t1->decodeAudio = 0;
        if (t1->convert_ctx)
        {
            av_audio_convert_free(t1->convert_ctx);
            t1->convert_ctx=NULL;
        }
        avcodec_close(t1->context->streams[t1->audiostream]->codec);
        av_free(t1->audiobuffer);
        t1->audiocodec=NULL;
        t1->audiobuffer=NULL;
    }
    t1->audiostream=-1;
    for(i = 0; i < t1->context->nb_streams; i++)
    {
        AVCodecContext *enc = t1->context->streams[i]->codec;
        switch(enc->codec_type)
        {
            case CODEC_TYPE_AUDIO:
                if(audiostreams==stream)
                    t1->audiostream=i;
                audiostreams+=1;
                break;
            case CODEC_TYPE_VIDEO:
                videostreams+=1;
                break;
            default:
                break;
        }
    }

	if ( t1->audiostream >= 0 )
	{
		int codecid=t1->context->streams[t1->audiostream]->codec->codec_id;
		if ( CODEC_ID_ADPCM_IMA_QT <= codecid && codecid <= CODEC_ID_ADPCM_EA_XAS )
		{
			t1->decodeAudio = 1;
		}
		else
		if ( ( codecid == CODEC_ID_VORBIS || codecid == CODEC_ID_ALAC || codecid == CODEC_ID_FLAC ) )
		{
			if ( !t1->disableDecodeAudio )
				t1->decodeAudio = 1;
			else
				t1->decodeAudio = 0;
		}
		else
		if ( codecid == CODEC_ID_COOK && ( t1->enableDecodeAudioType&COOK_AUDIO_MASK ) ) 
		{
			t1->decodeAudio = 1;
		}
		else
			t1->decodeAudio = 0;
	}

    return t1->audiostream==-1 ? 0 : 1;
}

int prSetSpuStream(struct PushReader *t1, int stream)
{
    int i;
    int videostreams=0;
    int audiostreams=0;
    int spustreams=0;
    t1->spustream=-1;
    for(i = 0; i < t1->context->nb_streams; i++)
    {
        AVCodecContext *enc = t1->context->streams[i]->codec;
        switch(enc->codec_type)
        {
            case CODEC_TYPE_AUDIO:
                audiostreams+=1;
                break;
            case CODEC_TYPE_VIDEO:
                videostreams+=1;
                break;
            case CODEC_TYPE_SUBTITLE:
                if(spustreams==stream)
                    t1->spustream=i;
                spustreams+=1;
                break;
            default:
                break;
        }
    }
    return t1->spustream==-1 ? 0 : 1;
}

/*// bad implementation, temporary
long long int llrint(double x)
{
    return (long long int)(x + (x < 0 ? -0.5 : 0.5));
}*/

int initFlashConversion(struct PushReader *t1)
{
    t1->flvbuffer=(unsigned char *) av_malloc(FLVBUFSIZE);
    if(t1->flvbuffer==NULL) return 0;
    t1->predbuffer=(short *) av_malloc(720*576/16/16*6*2*2); // 2 shorts per block, 6 block per macroblock
    if(t1->predbuffer==NULL)
    {
        av_free(t1->flvbuffer);
        t1->flvbuffer=0;
        return 0;
    }
    return 1;
}

static int countMpeg4Frames(unsigned char *data, int len, int *nFrames, int *nBFrames)
{
    int pos=0;
    // Search for VOPHeaders
    int pattern=0xFFFFFFFF;
    *nFrames=0;
    *nBFrames=0;
//    fprintf(stderr, "countMpeg4 : ");
    while(pos<len)
    {
        pattern<<=8;
        pattern|=data[pos];
        pos+=1;
        if(pattern==0x1B6)
        {
            // Parse begining of vop header
            // vop_coding_type 2 bits
            // modulo_time_base (while not 0)
            // marker bit 1 bit
            // vop_time_increment
            // marker_bit 1 bit
            // vop_coded 1 bit

            // For now ignore all but vop_coding_type since we don't store the time info to read vop_time_increment
            if((len-pos) > 0)
            {
                *nFrames+=1;
//                fprintf(stderr, "%d ", data[pos]>>6);
                if((data[pos]>>6)==2)
                {
                    *nBFrames+=1;
                }
            }
        }
    }
//    fprintf(stderr, "\n");
    return *nFrames;
}

static unsigned char * createH264Header(struct PushReader *t1, unsigned char *headerdata, int headerlen, int *lenout)
{
    int profile;
    int profilecomp;
    int avclevel;
    int length;
    int nSequenceParam;
    int nPictureParam;
    int pictureStart;
    int paramlen;
    unsigned char *dataout;
    int i;
    *lenout=0;
    if(headerlen<7)
    {
        fprintf(stderr, "avc header too short %d\n", headerlen);
        return NULL;
    }
    if(headerdata[0]!=1)
    {
        fprintf(stderr, "avc header wrong version %d\n", headerdata[0]);
        return NULL;
    }
    for(i=0;i<headerlen;i++)
    {
        fprintf(stderr, "%02X ",headerdata[i]);
    }
    fprintf(stderr, "\n");
    
    profile=headerdata[1];
    profilecomp=headerdata[2];
    avclevel=headerdata[3];
    t1->avcLength=(headerdata[4]&0x3)+1;
    nSequenceParam=headerdata[5]&0x1F;
    pictureStart=6;
    for(i=0;i<nSequenceParam;i++)
    {
        if((pictureStart+2)>headerlen) // At least 2 bytes for size
        {
            fprintf(stderr, "Header data too short seq %d %d\n",(pictureStart+2), headerlen);
            return NULL;
        }
        pictureStart+=((headerdata[pictureStart]<<8)+headerdata[pictureStart+1]+2);
    }
    if((pictureStart+1)>headerlen) // At least 1 bytes for number of picture params
    {
        fprintf(stderr, "Header data too short pic count %d %d\n",(pictureStart+1), headerlen);
        return NULL;
    }
    nPictureParam=(headerdata[pictureStart])&0xFF;
    pictureStart+=1;
    for(i=0;i<nPictureParam;i++)
    {
        if((pictureStart+2)>headerlen) // At least 2 bytes for size
        {
            fprintf(stderr, "Header data too short pic %d %d\n",(pictureStart+2), headerlen);
            return NULL;
        }
        paramlen=((headerdata[pictureStart]<<8)+headerdata[pictureStart+1]);
        pictureStart+=paramlen+2;
    }
    if(pictureStart>headerlen)
    {
            fprintf(stderr, "Header data too short final %d %d\n",(pictureStart), headerlen);
            return NULL;
    }
    fprintf(stderr, "AVC profile %d compatibility %d level %d length %d\n",
        profile, profilecomp, avclevel, t1->avcLength);
    fprintf(stderr, "number of sequence parameters %d number of picture parameters %d\n",
        nSequenceParam, nPictureParam);
    // we need enough size for header + 4 bytes per params
    dataout = av_malloc(headerlen+4*nSequenceParam+4*nPictureParam);
    pictureStart=6;
    for(i=0;i<nSequenceParam;i++)
    {
        if((pictureStart+2)>headerlen) // At least 2 bytes for size
        {
            fprintf(stderr, "Header data too short %d %d\n",(pictureStart+3), headerlen);
            return NULL;
        }
        paramlen=((headerdata[pictureStart]<<8)+headerdata[pictureStart+1]);
        dataout[*lenout+0]=0x00;
        dataout[*lenout+1]=0x00;
        dataout[*lenout+2]=0x00;
        dataout[*lenout+3]=0x01;
        *lenout+=4;
        av_memcpy(&dataout[*lenout], &headerdata[pictureStart+2], paramlen);
        *lenout+=paramlen;
        pictureStart+=paramlen+2;
    }
    pictureStart+=1;
    for(i=0;i<nPictureParam;i++)
    {
        if((pictureStart+2)>headerlen) // At least 2 bytes for size
        {
            fprintf(stderr, "Header data too short %d %d\n",(pictureStart+3), headerlen);
            return NULL;
        }
        paramlen=((headerdata[pictureStart]<<8)+headerdata[pictureStart+1]);
        dataout[*lenout+0]=0x00;
        dataout[*lenout+1]=0x00;
        dataout[*lenout+2]=0x00;
        dataout[*lenout+3]=0x01;
        *lenout+=4;
        av_memcpy(&dataout[*lenout], &headerdata[pictureStart+2], paramlen);
        *lenout+=paramlen;
        pictureStart+=paramlen+2;
    }
    fprintf(stderr, "Sending h264 header size %d\n",*lenout);
    for(i=0;i<*lenout;i++)
    {
        fprintf(stderr, "%02X ",dataout[i]);
    }
    fprintf(stderr, "\n");
    return dataout;
}

static int parseVC1Header(struct PushReader *t1, unsigned char *headerdata, int headerlen)
{
    int i;
    if(headerlen<4) return;

    for(i=3;i<headerlen;i++)
    {
        if(headerdata[i-3]==0 && headerdata[i-2]==0 && headerdata[i-1]==1 && headerdata[i]==0xF)
        {
            t1->vc1seqoffset=i-3;
        }

        if(headerdata[i-3]==0 && headerdata[i-2]==0 && headerdata[i-1]==1 && headerdata[i]==0xE)
        {
            t1->vc1entoffset=i-3;
            if(t1->vc1seqoffset) t1->vc1seqsize=t1->vc1entoffset-t1->vc1seqoffset;
        }
    }
    if(t1->vc1seqoffset && t1->vc1seqsize==0)
    {
        t1->vc1seqsize=headerlen-t1->vc1seqoffset;
    }
    if(t1->vc1entoffset && t1->vc1entsize==0)
    {
        t1->vc1entsize=headerlen-t1->vc1entoffset;
    }
    if(t1->vc1seqsize)
    {
        fprintf(stderr, "SEQ: ");
        for(i=0;i<t1->vc1seqsize;i++)
        {
            fprintf(stderr, "%02X ",headerdata[t1->vc1seqoffset+i]);
        }
        fprintf(stderr, "\n");
    }
    if(t1->vc1entsize)
    {
        fprintf(stderr, "ENT: ");
        for(i=0;i<t1->vc1entsize;i++)
        {
            fprintf(stderr, "%02X ",headerdata[t1->vc1entoffset+i]);
        }
        fprintf(stderr, "\n");
    }
    fprintf(stderr, "%d SEQ %d ENT\n",t1->vc1seqsize, t1->vc1entsize);
}
static int readH264Length(struct PushReader *t1, unsigned char *data,
                    int pos, int len)
{
    switch(t1->avcLength)
    {
        case 1:
            if(pos<len)
            {
                t1->packetBytesleft-=1;
                return(data[pos]);
            }
            break;
        case 2:
            if(pos+1<len)
            {
                t1->packetBytesleft-=2;
                return((data[pos]<<8) | (data[pos+1]) );
            }
            break;
        case 3:
            if(pos+2<len)
            {
                t1->packetBytesleft-=3;
                return((data[pos]<<16) | (data[pos+1]<<8) | (data[pos+2]<<0));
            }
            break;
        case 4:
            if(pos+3<len)
            {
                t1->packetBytesleft-=4;
                return((data[pos]<<24) | (data[pos+1]<<16) | (data[pos+2]<<8) | (data[pos+3]<<0));
            }
            break;
        default:
            fprintf(stderr, "Unknown avcLength type %d\n", t1->avcLength);
    }
    fprintf(stderr, "Error reading packet length\n");
    return 0;
}

static int AdtsFreq(int freq)
{
    const int adts_freq[] =
        {96000,88200,64000,48000,
          44100,32000,24000,22050,
          16000,12000,11025,8000,
          7350,0,0,0};
    int i;
    for (i=0;i<16;i++)
    {
        if (freq == adts_freq[i])
            return i;
    }
    return 0xf;
}

int writeAACHeader(struct PushReader *t1, unsigned char *headerdata, int headerlen, unsigned char *dataout, int *lenout, int framelen)
{
    int i;
    int objId;
    int freq;
    int channels;
    int freqs[] = { 96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
        16000, 12000, 11025, 8000, 44100, 44100,44100, -1 };
    *lenout=0;

#ifdef DEBUGAAC
    for(i=0;i<headerlen;i++)
    {
        fprintf(stderr, "%02X ",headerdata[i]);
    }
    fprintf(stderr, "\n");
#endif

    if(headerlen<2)
    {
        fprintf(stderr, "Header too short %d\n",headerlen);
        return 0;
    }
    objId=(headerdata[0]>>3)&0x1F;
    freq=freqs[((headerdata[0]&0x7)<<1)|((headerdata[1]>>7)&1)];
    if(freq==-1)
    {
        if(headerlen<5)
        {
            fprintf(stderr, "Header too short for extended rate %d\n",headerlen);
            return 0;
        }
        freq=((headerdata[1]&0x7F)<<17)|((headerdata[2]&0xFF)<<9)|
            ((headerdata[3]&0xFF)<<1)|((headerdata[4]&0x80)>>7);
        channels=(headerdata[4]>>3)&0xF;
    }
    else
    {
        channels=(headerdata[1]>>3)&0xF;
    }
#ifdef DEBUGAAC
    fprintf(stderr, "objID %d freq %d channels %d\n",objId, freq, channels);
#endif
    if(channels==0)
    {
        fprintf(stderr, "channels %d not supported\n", channels);
        return 0;
    }

    framelen+=7;

    dataout[0]=0xFF;
    dataout[1]=0xF1;
    dataout[2]=((((objId - 1) & 0x3) << 6) & 0xC0);
    dataout[2]|=((AdtsFreq(freq) << 2) & 0x3C);
    dataout[2]|= ((channels >> 2) & 0x1);
    dataout[3] = ((channels << 6) & 0xC0);
    dataout[3]|= ((framelen >> 11) & 0x3);
    dataout[4] = ((framelen >> 3) & 0xFF);	
    dataout[5] = ((framelen << 5) & 0xE0);
    dataout[5]|= ((0x7FF >> 6) & 0x1F);
    dataout[6] = ((0x7FF << 2) & 0xFC);

    *lenout=7;

#ifdef DEBUGAAC
    for(i=0;i<*lenout;i++)
    {
        fprintf(stderr, "%02X ",dataout[i]);
    }
    fprintf(stderr, "\n");
#endif
    return 1;
}

float powf(float x, float y)
{
   return (float) pow((double)x,(double)y);
}

void setDebugTrace( char* pDebugLogName, int logCtrl )
{
	if ( logFile )
		fclose( logFile );
	logFile = NULL;

	if ( pDebugLogName != NULL && pDebugLogName[0] )
		logFile = fopen( pDebugLogName, "w" );

	logControl = logCtrl;
}
