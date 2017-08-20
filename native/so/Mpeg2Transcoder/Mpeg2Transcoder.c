/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <stdio.h>

 #include <stdio.h>
 #include <string.h>
 #include "libavcodec/avcodec.h"
 #include "libavformat/avformat.h"
 #include "libswscale/swscale.h"

// no more allcodecs.h file...
 extern AVInputFormat mp3_demuxer;
 extern AVInputFormat mpegps_demuxer;

#include "sage_Mpeg2Transcoder.h"

// later ffmpeg snapshots don't define these in the usual places
extern AVCodec mp3_decoder;
extern URLProtocol file_protocol;
extern AVCodecParser mpegaudio_parser;

#define DECLARE_ALIGNED(n,t,v)      t v __attribute__ ((aligned (n)))
#define DECLARE_ALIGNED_8(t, v) DECLARE_ALIGNED(16, t, v)
#define PHASE_BITS 4
#define NB_PHASES  (1 << PHASE_BITS)
#define NB_TAPS    4

typedef struct 
{
    AVFormatContext *context;
    AVPacket packet;    
    int audiostream;
    int videostream;
    unsigned char *outputbuffer; // Circular buffer of outputlen bytes
    int outputlen;
    int outputstart;
    int outputend;    
    long long lastPTSmsec;

    unsigned char *packetData; // data of the packet
    int packetType; // C0/E0
    int packetBytesleft;  // how many bytes are left in current packet
    
    int transcodevideo; // If we need to transcode video 
    AVFrame *tcFrame; // frame for video decoding
    double nextvideopts;
    AVCodecContext *ovcontext;
    AVCodec *ovcodec;
    unsigned char *ovbuffer;
    int ovbuflen;
    unsigned char * picturedata;
    AVFrame *ovFrame; // frame for video encoding
	int srcHeight;
    struct SwsContext *imgscaler;
    int transcodeaudio; // If we need to transcode audio
    int eof;
} Transcoder;

static void sysOutPrint(JNIEnv *env, const char* cstr, ...) 
{
    static jclass cls=NULL;
    static jfieldID outField=NULL;
    static jmethodID printMeth=NULL;
    if(env == NULL) return;
    jthrowable oldExcept = (*env)->ExceptionOccurred(env);
    if (oldExcept)
        (*env)->ExceptionClear(env);
    va_list args;
    va_start(args, cstr);
    char buf[1024*2];
    vsnprintf(buf, sizeof(buf), cstr, args);
    va_end(args);
    jstring jstr = (*env)->NewStringUTF(env, buf);
    if(cls==NULL)
    {
        cls = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/System"));
        outField = (*env)->GetStaticFieldID(env, cls, "out", "Ljava/io/PrintStream;");
        printMeth = (*env)->GetMethodID(env, (*env)->FindClass(env, "java/io/PrintStream"),
        "print", "(Ljava/lang/String;)V");
    }
    jobject outObj = (*env)->GetStaticObjectField(env, cls, outField);
    (*env)->CallVoidMethod(env, outObj, printMeth, jstr);
    (*env)->DeleteLocalRef(env, jstr);
    if (oldExcept)
        (*env)->Throw(env, oldExcept);
}

static int open_stream(JNIEnv *env, Transcoder *t1, int streamn)
{
    AVCodec *codec;
    AVCodecContext *codeccontext;
    int retval;
    
    codeccontext = t1->context->streams[streamn]->codec;
    
    codec = avcodec_find_decoder(codeccontext->codec_id);
    if (codec == NULL) return -1;
        
    retval = avcodec_open(codeccontext, codec);
    
    if(retval < 0)
        return -1;
    
    switch(codeccontext->codec_type)
    {
        case CODEC_TYPE_AUDIO:
            t1->audiostream=streamn;
            break;
        case CODEC_TYPE_VIDEO:
            t1->videostream=streamn;
            break;
        default:
            break;
    }
    
    return 0;
}

static int open_video_encoder(JNIEnv *env, Transcoder *t1, int streamn)
{
    sysOutPrint(env, "Opening video encoder\n");
    t1->tcFrame = avcodec_alloc_frame(); // Verify if we really need this...
    t1->nextvideopts=0;
    t1->ovcontext = avcodec_alloc_context();
    
    // Minimum set of parameters for now...
    t1->ovcontext->codec_id = CODEC_ID_MPEG2VIDEO;
    t1->ovcontext->codec_type = CODEC_TYPE_VIDEO;
    
    t1->ovcontext->bit_rate = 10000000;    
    t1->ovcontext->width = 720;
    t1->ovcontext->height = 480;
    t1->ovcontext->time_base.den = 24000;
    t1->ovcontext->time_base.num = 1001;
    t1->ovcontext->gop_size = 15;
    t1->ovcontext->pix_fmt = PIX_FMT_YUV420P;
    t1->ovcontext->max_b_frames = 0;
    t1->ovcontext->me_method = ME_ZERO;
    
    sysOutPrint(env, "Trying to find codec\n");
    t1->ovcodec = avcodec_find_encoder(t1->ovcontext->codec_id);
    if(t1->ovcodec==NULL)
    {
        sysOutPrint(env, "output video codec not found\n");
        return -1;
    }
    sysOutPrint(env, "Opening codec\n");
    if(avcodec_open(t1->ovcontext, t1->ovcodec) < 0)
    {
        sysOutPrint(env, "error opening codec\n");
        return -2;
    }
    
    //sysOutPrint(env, "Allocation frame\n");
    t1->ovFrame = avcodec_alloc_frame();    
    int size = avpicture_get_size(t1->ovcontext->pix_fmt, 
        t1->ovcontext->width, t1->ovcontext->height);
    t1->picturedata = av_malloc(size);
    
    if(t1->picturedata==NULL)
    {
        return -3;
    }
    //A frame contains a picture...
    avpicture_fill((AVPicture *)t1->ovFrame, t1->picturedata, 
                   t1->ovcontext->pix_fmt, 
                   t1->ovcontext->width, t1->ovcontext->height);
    
    t1->ovbuffer = (unsigned char *) av_malloc(4*65536);
    t1->ovbuflen = 4*65536;
    
	t1->srcHeight = t1->context->streams[streamn]->codec->height;
    t1->imgscaler = sws_getContext(t1->context->streams[streamn]->codec->width, t1->srcHeight, PIX_FMT_YUV420P,
								   720, 480, PIX_FMT_YUV420P,
								   SWS_BICUBIC, NULL, NULL, NULL);
	
    //sysOutPrint(env, "Encoder open done\n");
    return 0;
}

static int close_video_encoder(JNIEnv *env, Transcoder *t1)
{
    //sysOutPrint(env, "Closing video encoder\n");
    avcodec_close(t1->ovcontext);
    av_free(t1->tcFrame);
    av_free(t1->picturedata);
    av_free(t1->ovFrame);
    if (t1->imgscaler) sws_freeContext(t1->imgscaler);
    //sysOutPrint(env, "Encoder close done\n");
}

static int close_stream(JNIEnv *env, Transcoder *t1, int streamn)
{
    AVCodecContext *codeccontext;
    
    codeccontext = t1->context->streams[streamn]->codec;
    avcodec_close(codeccontext);
    switch(codeccontext->codec_type)
    {
        case CODEC_TYPE_AUDIO:
            t1->audiostream=-1;
            break;
        case CODEC_TYPE_VIDEO:
            t1->videostream=-1;
            break;
        default:
            break;
   }
}

static int BufferSpace(Transcoder *t1)
{
    return t1->outputend < t1->outputstart ? 
        t1->outputstart-t1->outputend:
        t1->outputstart + t1->outputlen - t1->outputend ;
}


static int BufferWrite(Transcoder *t1, unsigned char *data, int len)
{    
    // Write at most free space size
    int datalen;
    if(len>BufferSpace(t1)) len = BufferSpace(t1);
    datalen=len;
    
    while(len)
    {
        int maxlen= t1->outputend+len > t1->outputlen ? t1->outputlen-t1->outputend : len;
        memcpy(t1->outputbuffer+t1->outputend, data, maxlen);
        data+=maxlen;
        t1->outputend+=maxlen;
        t1->outputend=t1->outputend%t1->outputlen;
        len-=maxlen;
    }
    
    return datalen;
}

static int BufferRead(Transcoder *t1, unsigned char *data, int len)
{
    // Read at most what is left
    if(len>(t1->outputlen-BufferSpace(t1))) len = t1->outputlen-BufferSpace(t1);

    while(len)
    {
        int maxlen= t1->outputstart+len > t1->outputlen ? t1->outputlen-t1->outputstart : len;
        memcpy(data, t1->outputbuffer+t1->outputstart, maxlen);
        data+=maxlen;
        t1->outputstart+=maxlen;
        t1->outputstart=t1->outputstart%t1->outputlen;
        len-=maxlen;
    }
}

//Note: call only when the data you want to send can fit in the buffer
static int output_packet(JNIEnv *env, Transcoder *t1, int mpegstream, unsigned char *data, 
    int len, int haspts, double pts)
{
    unsigned char header[16];
    unsigned char header2[16];
    int headerlen=0;
    int size=len;
    

    header[0]=0x00;
    header[1]=0x00;
    header[2]=0x01;
    header[3]=mpegstream;
    
    header[4]=0; // Fill later
    header[5]=0; // Fill later

    header[6]=0x80;
    if(haspts)
    {
        long long llscr = pts*90000.0;
        unsigned int muxrate=1024*1024/8/50; // 1mbit/sec
        header2[0]=0x00;
        header2[1]=0x00;
        header2[2]=0x01;
        header2[3]=0xBA;
        header2[4]=0x40|(((llscr>>30)&0x7)<<3)|0x04|(((llscr>>28)&0x3)<<0);
        header2[5]=(((llscr>>20)&0xFF)<<0);
        header2[6]=(((llscr>>15)&0x1F)<<3)|0x04|(((llscr>>13)&0x3)<<0);
        header2[7]=(((llscr>>5)&0xFF)<<0);
        header2[8]=(((llscr>>0)&0x1F)<<3)|0x04;
        header2[9]=1;
        header2[10]=(((llscr>>14)&0xFF)<<0);
        header2[11]=(((llscr>>6)&0xFF)<<0);;
        header2[12]=(((llscr>>0)&0x3F)<<2)|0x3;
        header2[13]=0xF8;
        BufferWrite(t1, header2, 14);
        
        pts+=0.3; // add 0.3 delay for scr/pts delay...
        long long llpts = pts*90000.0;
        //sysOutPrint(env, "Outputing packet of %X with pts %f\n",mpegstream, pts);
        header[7]=0x80;
        header[8]=5;
        header[9]=0x21 | (((llpts>>30)&3)<<1);
        header[10]=(((llpts>>22)&0xFF)<<0);
        header[11]=1 | (((llpts>>15)&0x7F)<<1);
        header[12]=(((llpts>>7)&0xFF)<<0);
        header[13]=1 | (((llpts>>0)&0x7F)<<1);
        headerlen=14;
    }
    else
    {
        header[7]=0x80;    
        header[8]=0x00;    
        headerlen=9;
    }
    
    size+=headerlen;
    
    if(size>65535) size=65535;
    header[4]=(size-6)>>8;
    header[5]=(size-6)&0xFF;
    
    BufferWrite(t1, header, headerlen);
    BufferWrite(t1, data, size-headerlen);    
    
    return size-headerlen;
}

static void ProcessFrames(JNIEnv *env, Transcoder *t1)
{
    int retval;
    while(BufferSpace(t1)>65536)
    {
        if(t1->packetBytesleft)
        {
            int sentBytes = output_packet(env, t1, t1->packetType,
                t1->packetData, t1->packetBytesleft,
                0, 0.0);
            t1->packetBytesleft -= sentBytes;
            t1->packetData += sentBytes;
        }
        
        if(t1->packetBytesleft==0)
        {
            double pts;
            retval = av_read_frame(t1->context, &t1->packet);
            if (retval < 0)
            {
                t1->eof=1;
                break;
            }
            if(t1->packet.pts != AV_NOPTS_VALUE)
            {
                pts = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) *
                    t1->packet.pts;
                t1->lastPTSmsec=pts*1000;
            }
            
            if(t1->packet.stream_index == t1->videostream && t1->transcodevideo)
            {
                int got_picture;
                //sysOutPrint(env, "decoding\n");
                avcodec_decode_video(t1->context->streams[t1->videostream]->codec, 
                                     t1->tcFrame, &got_picture, 
                                     t1->packet.data, t1->packet.size);
                //sysOutPrint(env, "done\n");
                if(got_picture)
                {
                    int sentBytes=0;
                    pts = av_q2d(t1->context->streams[t1->packet.stream_index]->time_base) * 
                        t1->tcFrame->pts;
/*                    t1->ovFrame->pts = t1->tcFrame->pts / av_q2d(t1->ovcontext->time_base) *
                        av_q2d(t1->context->streams[t1->packet.stream_index]->time_base);
                    sysOutPrint(env, "Frame pts : %f %f %f %d\n",pts,
                        av_q2d(t1->ovcontext->time_base),
                        av_q2d(t1->context->streams[t1->packet.stream_index]->time_base),
                        t1->ovFrame->pts);*/
                    //sysOutPrint(env, "encoding\n");
                    
					sws_scale(t1->imgscaler, 
							  t1->tcFrame->data, t1->tcFrame->linesize, 0, t1->srcHeight,
							  t1->ovFrame->data, t1->ovFrame->linesize);
                    t1->ovFrame->pict_type=FF_I_TYPE;
                    int packetSize = avcodec_encode_video(
                        t1->ovcontext, t1->ovbuffer, 
                        t1->ovbuflen, t1->ovFrame);
                    //sysOutPrint(env, "done %d\n", packetSize);
                    t1->packetData = t1->ovbuffer;
                    t1->packetType = 0xE0;
                    t1->packetBytesleft = packetSize;
                    sentBytes = output_packet(env, t1, t1->packetType,
                        t1->packetData, t1->packetBytesleft,
                        t1->tcFrame->pts != AV_NOPTS_VALUE, pts );
                    t1->packetBytesleft -= sentBytes;
                    t1->packetData += sentBytes;
                }
            }
            else if(t1->packet.stream_index == t1->audiostream ||
                    t1->packet.stream_index == t1->videostream)
            {
                int sentBytes=0;
                t1->packetData = t1->packet.data;
                if(t1->packet.stream_index == t1->audiostream)
                    t1->packetType = 0xC0;
                if(t1->packet.stream_index == t1->videostream)
                    t1->packetType = 0xE0;
                t1->packetBytesleft = t1->packet.size;
                
                sentBytes = output_packet(env, t1, t1->packetType,
                    t1->packetData, t1->packetBytesleft,
                    t1->packet.pts != AV_NOPTS_VALUE, pts );
                t1->packetBytesleft -= sentBytes;
                t1->packetData += sentBytes;
            }
        }
    }
}

static void FlushBuffer(JNIEnv *env, Transcoder *t1)
{
    t1->outputstart=0;
    t1->outputend=0;
    t1->packetBytesleft=0;
    if(t1->transcodevideo)
    {
        close_video_encoder(env, t1);
        open_video_encoder(env, t1, t1->videostream);
    }
}


static void closeTranscoder(JNIEnv *env, Transcoder *t1)
{
    // Closing
    if(t1->audiostream >= 0)
    {
        close_stream(env, t1, t1->audiostream);
    }

    if(t1->videostream >= 0)
    {
        close_stream(env, t1, t1->videostream);
    }
    
    av_close_input_file(t1->context);

    if(t1->outputbuffer) av_free(t1->outputbuffer);
    
    av_free(t1);    
}

static Transcoder * openTranscoder(JNIEnv *env, const char *filename)
{    
    Transcoder *t1;
    
    t1 = (Transcoder *) av_malloc(sizeof(Transcoder));
    if(t1 == NULL) return 0;
    memset(t1, 0, sizeof(Transcoder));
        
    int retval,i;
    int audiostream = -1;
    int videostream = -1;
    
    t1->outputbuffer=av_malloc(256*1024);
    if(t1->outputbuffer==NULL)
    {
        av_free(t1);
        return 0;
    }
    
    t1->outputlen=256*1024;
    
//    av_register_all();    
    avcodec_init();
    //avcodec_register_all();
    register_avcodec(&mp3_decoder);
    av_register_codec_parser(&mpegaudio_parser);

    av_register_input_format(&mpegps_demuxer);
    av_register_input_format(&mp3_demuxer);
    register_protocol(&file_protocol);
//    const char* filename = (*env)->GetStringUTFChars(env, jfilename, NULL);
    sysOutPrint(env, "Trying to open %s\n",filename);
    retval = av_open_input_file(&t1->context, filename, NULL, 0, NULL);
//    (*env)->ReleaseStringUTFChars(env, jfilename, filename);
    if(retval < 0)
    {
        sysOutPrint(env, "Error opening file\n");
        av_free(t1->outputbuffer);
        av_free(t1);
        return NULL;
    }

    sysOutPrint(env, "find stream info\n");
    retval = av_find_stream_info(t1->context);
    
    av_read_play(t1->context);

    sysOutPrint(env, "go through streams\n");
    // TODO: add selection of which streams to play back...
    t1->audiostream=-1;
    t1->videostream=-1;
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
            default:
                break;
        }
    }
    
    
    dump_format(t1->context, 0, "", 0);
    
    if(audiostream >= 0)
    {
        open_stream(env, t1, audiostream);
    }

    if(videostream >= 0)
    {
        open_stream(env, t1, videostream);
        if(t1->context->streams[videostream]->codec->codec_id != CODEC_ID_MPEG2VIDEO)
        {
            t1->transcodevideo=1;
            open_video_encoder(env, t1, videostream);
        }
    } 

    return t1;
}

static long long getFirstTime(Transcoder *t1)
{
    return t1->context->start_time/AV_TIME_BASE*1000;
}

static long long getDuration(Transcoder *t1)
{
    return t1->context->duration/AV_TIME_BASE*1000;
}

static void seek(JNIEnv *env, Transcoder *t1, long long time)
{
    int retval;
    retval = av_seek_frame(t1->context, -1, time*AV_TIME_BASE/1000, AVSEEK_FLAG_BACKWARD);
    avcodec_flush_buffers(t1->context->streams[t1->videostream]->codec);
    FlushBuffer(env, t1);
    ProcessFrames(env, t1);
}

static int availableData(JNIEnv *env, Transcoder *t1)
{
    int len;
    ProcessFrames(env, t1);
    len=t1->outputend < t1->outputstart ? 
        t1->outputend + t1->outputlen - t1->outputstart :
        t1->outputend-t1->outputstart;
    if(len==0 && t1->eof)
        return -1;
    return len;
}  

/*
 * Class:     sage_Mpeg2Transcoder
 * Method:    openTranscode0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_Mpeg2Transcoder_openTranscode0
  (JNIEnv *env, jobject jo, jstring jfilename)
{
    Transcoder *t1;
    sysOutPrint(env, "openTranscode\n");
    const char* filename = (*env)->GetStringUTFChars(env, jfilename, NULL);
    t1 = openTranscoder(env, filename);
    (*env)->ReleaseStringUTFChars(env, jfilename, filename);
    return (jlong) (int) t1;
}

/*
 * Class:     sage_Mpeg2Transcoder
 * Method:    closeTranscode0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_Mpeg2Transcoder_closeTranscode0
  (JNIEnv *env, jobject jo, jlong handle)
{
    if(handle==0) return;
    Transcoder *t1 = (Transcoder *) (int) handle;
    closeTranscoder(env, t1);
}

/*
 * Class:     sage_Mpeg2Transcoder
 * Method:    getFirstTime0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sage_Mpeg2Transcoder_getFirstTime0
  (JNIEnv *env, jobject jo, jlong handle)
{
    if(handle==0) return (jlong) 0;
    Transcoder *t1 = (Transcoder *) (int) handle;
    
    return t1->context->start_time/AV_TIME_BASE*1000;
}
/*
 * Class:     sage_Mpeg2Transcoder
 * Method:    getLastParsedTime0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sage_Mpeg2Transcoder_getLastParsedTime0
  (JNIEnv *env, jobject jo, jlong handle)
{
    if(handle==0) return (jlong) 0;
    Transcoder *t1 = (Transcoder *) (int) handle;

    //sysOutPrint(env, "getLastParsedTime0 returning %lld\n",t1->lastPTSmsec);
    return t1->lastPTSmsec;
}
/*
 * Class:     sage_Mpeg2Transcoder
 * Method:    getDurationMillis0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sage_Mpeg2Transcoder_getDurationMillis0
  (JNIEnv *env, jobject jo, jlong handle)
{
    if(handle==0) return;
    Transcoder *t1 = (Transcoder *) (int) handle;

    return t1->context->duration/AV_TIME_BASE*1000;
}
/*
 * Class:     sage_Mpeg2Transcoder
 * Method:    seek0
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_sage_Mpeg2Transcoder_seek0
  (JNIEnv *env, jobject jo, jlong handle, jlong time)
{
    int retval;
    if(handle==0) return;
    Transcoder *t1 = (Transcoder *) (int) handle;
    retval = av_seek_frame(t1->context, -1, time*AV_TIME_BASE/1000, AVSEEK_FLAG_BACKWARD);
    FlushBuffer(env, t1);
    ProcessFrames(env, t1);
    return;
}
  
/*
 * Class:     sage_Mpeg2Transcoder
 * Method:    availableToRead0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sage_Mpeg2Transcoder_availableToRead0
  (JNIEnv *env, jobject jo, jlong handle)
{
    int len;
    if(handle==0) return;
    Transcoder *t1 = (Transcoder *) (int) handle;
    
    ProcessFrames(env, t1);
    len=t1->outputend < t1->outputstart ? 
        t1->outputend + t1->outputlen - t1->outputstart :
        t1->outputend-t1->outputstart;
    if(len==0 && t1->eof)
        return -1;
    return len;
}
/*
 * Class:     sage_Mpeg2Transcoder
 * Method:    read0
 * Signature: (J[BII)I
 */
//FILE *outfile=NULL;
JNIEXPORT jint JNICALL Java_sage_Mpeg2Transcoder_read0
  (JNIEnv *env, jobject jo, jlong handle, jbyteArray buf, jint off, jint len)
{
    unsigned char* critArr;
    if(handle==0) return;
    Transcoder *t1 = (Transcoder *) (int) handle;

    critArr = (unsigned char*)(*env)->GetPrimitiveArrayCritical(env, buf, NULL);
    if(critArr!=NULL)
    {
        BufferRead(t1, critArr+off, len);
        //if(outfile==NULL)
        //    outfile=fopen("out.dat","wb");
        //fwrite(critArr+off, len, 1, outfile);            
        (*env)->ReleasePrimitiveArrayCritical(env, buf, critArr, 0);
        return len;
    }
    return 0;
}
