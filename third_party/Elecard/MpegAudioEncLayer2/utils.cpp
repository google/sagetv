/*
 * utils for libavcodec
 * Copyright (c) 2001 Fabrice Bellard.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
#include <stdio.h>
#include <stdlib.h>
#include "common.h"
#include "avcodec.h"
#include "dsputil.h"
#include "mpegvideo.h"

void *av_mallocz(unsigned int size)
{
    void *ptr;
    
    if(size == 0) fprintf(stderr, "Warning, allocating 0 bytes\n");
    
    ptr = av_malloc(size);
    if (!ptr)
        return NULL;
    memset(ptr, 0, size);
    return ptr;
}

/* allocation of static arrays - do not use for normal allocation */
static int last_static = 0;
static char*** array_static = NULL;
static const unsigned int grow_static = 64; // ^2
void *__av_mallocz_static(void** location, unsigned int size)
{
    int l = (last_static + grow_static) & ~(grow_static - 1);
    void *ptr = av_mallocz(size);
    if (!ptr)
	return NULL;

    if (location)
    {
	if (l > last_static)
	    array_static = (char***)realloc(array_static, l);
	array_static[last_static++] = (char**) location;
	*location = ptr;
    }
    return ptr;
}
/* free all static arrays and reset pointers to 0 */
void av_free_static()
{
    if (array_static)
    {
	int i;
	for (i = 0; i < last_static; i++)
	{
	    free(*array_static[i]);
            *array_static[i] = NULL;
	}
	free(array_static);
	array_static = 0;
    }
    last_static = 0;
}

/* cannot call it directly because of 'void **' casting is not automatic */
void __av_freep(void **ptr)
{
    av_free(*ptr);
    *ptr = NULL;
}

/* encoder management */
AVCodec *first_avcodec;

void register_avcodec(AVCodec *format)
{
    AVCodec **p;
    p = &first_avcodec;
    while (*p != NULL) p = &(*p)->next;
    *p = format;
    format->next = NULL;
}

void avcodec_get_chroma_sub_sample(int fmt, int *h_shift, int *v_shift){
    switch(fmt){
    case PIX_FMT_YUV410P:
        *h_shift=2;
        *v_shift=2;
        break;
    case PIX_FMT_YUV420P:
        *h_shift=1;
        *v_shift=1;
        break;
    case PIX_FMT_YUV411P:
        *h_shift=2;
        *v_shift=0;
        break;
    case PIX_FMT_YUV422P:
    case PIX_FMT_YUV422:
        *h_shift=1;
        *v_shift=0;
        break;
    default: //RGB/...
        *h_shift=0;
        *v_shift=0;
        break;
    }
}

typedef struct DefaultPicOpaque{
    int last_pic_num;
    uint8_t *data[4];
}DefaultPicOpaque;

int avcodec_default_get_buffer(AVCodecContext *s, AVFrame *pic){
    int i;
    const int width = s->width;
    const int height= s->height;
    DefaultPicOpaque *opaque;
    
    assert(pic->data[0]==NULL);
//    assert(pic->type==0 || pic->type==FF_TYPE_INTERNAL);

    if(pic->opaque){
        opaque= (DefaultPicOpaque *)pic->opaque;
        for(i=0; i<3; i++)
            pic->data[i]= opaque->data[i];

//    printf("get_buffer %X coded_pic_num:%d last:%d\n", pic->opaque, pic->coded_picture_number, opaque->last_pic_num);    
        pic->age= pic->coded_picture_number - opaque->last_pic_num;
        opaque->last_pic_num= pic->coded_picture_number;
//printf("age: %d %d %d\n", pic->age, c->picture_number, pic->coded_picture_number);
    }else{
        int align, h_chroma_shift, v_chroma_shift;
        int w, h, pixel_size;
        
        avcodec_get_chroma_sub_sample(s->pix_fmt, &h_chroma_shift, &v_chroma_shift);
        
        switch(s->pix_fmt){
        case PIX_FMT_YUV422:
            pixel_size=2;
            break;
        case PIX_FMT_RGB24:
        case PIX_FMT_BGR24:
            pixel_size=3;
            break;
        case PIX_FMT_BGRA32:
        case PIX_FMT_RGBA32:
            pixel_size=4;
            break;
        default:
            pixel_size=1;
        }
        
        if(s->codec_id==CODEC_ID_SVQ1) align=63;
        else                           align=15;
    
        w= (width +align)&~align;
        h= (height+align)&~align;
    
        if(!(s->flags&CODEC_FLAG_EMU_EDGE)){
            w+= EDGE_WIDTH*2;
            h+= EDGE_WIDTH*2;
        }
        
        opaque= (DefaultPicOpaque*)av_mallocz(sizeof(DefaultPicOpaque));
        if(opaque==NULL) return -1;

        pic->opaque= opaque;
        opaque->last_pic_num= -256*256*256*64;

        for(i=0; i<3; i++){
            int h_shift= i==0 ? 0 : h_chroma_shift;
            int v_shift= i==0 ? 0 : v_chroma_shift;

            pic->linesize[i]= pixel_size*w>>h_shift;

            pic->base[i]= (uint8_t *)av_mallocz((pic->linesize[i]*h>>v_shift)+16); //FIXME 16
            if(pic->base[i]==NULL) return -1;

            memset(pic->base[i], 128, pic->linesize[i]*h>>v_shift);
        
            if(s->flags&CODEC_FLAG_EMU_EDGE)
                pic->data[i] = pic->base[i] + 16; //FIXME 16
            else
                pic->data[i] = pic->base[i] + (pic->linesize[i]*EDGE_WIDTH>>v_shift) + (EDGE_WIDTH>>h_shift) + 16; //FIXME 16
            
            opaque->data[i]= pic->data[i];
        }
        pic->age= 256*256*256*64;
        pic->type= FF_BUFFER_TYPE_INTERNAL;
    }

    return 0;
}

void avcodec_default_release_buffer(AVCodecContext *s, AVFrame *pic){
    int i;
    
    assert(pic->type==FF_BUFFER_TYPE_INTERNAL);
    
    for(i=0; i<3; i++)
        pic->data[i]=NULL;
//printf("R%X\n", pic->opaque);
}

void avcodec_get_context_defaults(AVCodecContext *s){
    s->bit_rate= 800*1000;
    s->bit_rate_tolerance= s->bit_rate*10;
    s->qmin= 2;
    s->qmax= 31;
    s->rc_eq= "tex^qComp";
    s->qcompress= 0.5;
    s->max_qdiff= 3;
    s->b_quant_factor=1.25;
    s->b_quant_offset=1.25;
    s->i_quant_factor=-0.8f; // resolve warning C4305 truncation from 'double' to 'float'
    s->i_quant_offset=0.0;
    s->error_concealment= 3;
    s->error_resilience= 1;
    s->workaround_bugs= FF_BUG_AUTODETECT;
    s->frame_rate = 25 * FRAME_RATE_BASE;
    s->gop_size= 50;
    s->me_method= ME_EPZS;
    s->get_buffer= avcodec_default_get_buffer;
    s->release_buffer= avcodec_default_release_buffer;
}

/**
 * allocates a AVCodecContext and set it to defaults.
 * this can be deallocated by simply calling free() 
 */
AVCodecContext *avcodec_alloc_context(void){
    AVCodecContext *avctx= (AVCodecContext *)av_mallocz(sizeof(AVCodecContext));
    
    if(avctx==NULL) return NULL;
    
    avcodec_get_context_defaults(avctx);
    
    return avctx;
}

/**
 * allocates a AVPFrame and set it to defaults.
 * this can be deallocated by simply calling free() 
 */
AVFrame *avcodec_alloc_frame(void){
    AVFrame *pic= (AVFrame *)av_mallocz(sizeof(AVFrame));
    
    return pic;
}

int avcodec_open(AVCodecContext *avctx, AVCodec *codec)
{
    int ret;

    avctx->codec = codec;
    avctx->codec_id = (CodecID)codec->id;
    avctx->frame_number = 0;
    if (codec->priv_data_size > 0) {
        avctx->priv_data = av_mallocz(codec->priv_data_size);
        if (!avctx->priv_data) 
            return -ENOMEM;
    } else {
        avctx->priv_data = NULL;
    }
    ret = avctx->codec->init(avctx);
    if (ret < 0) {
        av_freep(&avctx->priv_data);
        return ret;
    }
    return 0;
}

int avcodec_encode_audio(AVCodecContext *avctx, UINT8 *buf, int buf_size, 
                         const short *samples)
{
    int ret;

    ret = avctx->codec->encode(avctx, buf, buf_size, (void *)samples);
    avctx->frame_number++;
    return ret;
}

int avcodec_encode_video(AVCodecContext *avctx, UINT8 *buf, int buf_size, 
                         const AVFrame *pict)
{
    int ret;

    ret = avctx->codec->encode(avctx, buf, buf_size, (void *)pict);
    
    emms_c(); //needed to avoid a emms_c() call before every return;

    avctx->frame_number++;
    return ret;
}

/* decode a frame. return -1 if error, otherwise return the number of
   bytes used. If no frame could be decompressed, *got_picture_ptr is
   zero. Otherwise, it is non zero */
int avcodec_decode_video(AVCodecContext *avctx, AVFrame *picture, 
                         int *got_picture_ptr,
                         UINT8 *buf, int buf_size)
{
    int ret;
    
    ret = avctx->codec->decode(avctx, picture, got_picture_ptr, 
                               buf, buf_size);

    emms_c(); //needed to avoid a emms_c() call before every return;
    
    if (*got_picture_ptr)                           
        avctx->frame_number++;
    return ret;
}

/* decode an audio frame. return -1 if error, otherwise return the
   *number of bytes used. If no frame could be decompressed,
   *frame_size_ptr is zero. Otherwise, it is the decompressed frame
   *size in BYTES. */
int avcodec_decode_audio(AVCodecContext *avctx, INT16 *samples, 
                         int *frame_size_ptr,
                         UINT8 *buf, int buf_size)
{
    int ret;

    ret = avctx->codec->decode(avctx, samples, frame_size_ptr, 
                               buf, buf_size);
    avctx->frame_number++;
    return ret;
}

int avcodec_close(AVCodecContext *avctx)
{
    if (avctx->codec->close)
        avctx->codec->close(avctx);
    av_freep(&avctx->priv_data);
    avctx->codec = NULL;
    return 0;
}

AVCodec *avcodec_find_encoder(enum CodecID id)
{
    AVCodec *p;
    p = first_avcodec;
    while (p) {
        if (p->encode != NULL && p->id == id)
            return p;
        p = p->next;
    }
    return NULL;
}

AVCodec *avcodec_find_encoder_by_name(const char *name)
{
    AVCodec *p;
    p = first_avcodec;
    while (p) {
        if (p->encode != NULL && strcmp(name,p->name) == 0)
            return p;
        p = p->next;
    }
    return NULL;
}

AVCodec *avcodec_find_decoder(enum CodecID id)
{
    AVCodec *p;
    p = first_avcodec;
    while (p) {
        if (p->decode != NULL && p->id == id)
            return p;
        p = p->next;
    }
    return NULL;
}

AVCodec *avcodec_find_decoder_by_name(const char *name)
{
    AVCodec *p;
    p = first_avcodec;
    while (p) {
        if (p->decode != NULL && strcmp(name,p->name) == 0)
            return p;
        p = p->next;
    }
    return NULL;
}

AVCodec *avcodec_find(enum CodecID id)
{
    AVCodec *p;
    p = first_avcodec;
    while (p) {
        if (p->id == id)
            return p;
        p = p->next;
    }
    return NULL;
}

const char *pix_fmt_str[] = {
    "yuv420p",
    "yuv422",
    "rgb24",
    "bgr24",
    "yuv422p",
    "yuv444p",
    "rgba32",
    "bgra32",
    "yuv410p",
    "yuv411p",
};

void avcodec_string(char *buf, int buf_size, AVCodecContext *enc, int encode)
{
    const char *codec_name;
    AVCodec *p;
    char buf1[32];
    char channels_str[100];
    int bitrate;

    if (encode)
        p = avcodec_find_encoder(enc->codec_id);
    else
        p = avcodec_find_decoder(enc->codec_id);

    if (p) {
        codec_name = p->name;
    } else if (enc->codec_name[0] != '\0') {
        codec_name = enc->codec_name;
    } else {
        /* output avi tags */
        if (enc->codec_type == CODEC_TYPE_VIDEO) {
            snprintf(buf1, sizeof(buf1), "%c%c%c%c", 
                     enc->codec_tag & 0xff,
                     (enc->codec_tag >> 8) & 0xff,
                     (enc->codec_tag >> 16) & 0xff,
                     (enc->codec_tag >> 24) & 0xff);
        } else {
            snprintf(buf1, sizeof(buf1), "0x%04x", enc->codec_tag);
        }
        codec_name = buf1;
    }

    switch(enc->codec_type) {
    case CODEC_TYPE_VIDEO:
        snprintf(buf, buf_size,
                 "Video: %s%s",
                 codec_name, enc->flags & CODEC_FLAG_HQ ? " (hq)" : "");
        if (enc->codec_id == CODEC_ID_RAWVIDEO) {
            snprintf(buf + strlen(buf), buf_size - strlen(buf),
                     ", %s",
                     pix_fmt_str[enc->pix_fmt]);
        }
        if (enc->width) {
            snprintf(buf + strlen(buf), buf_size - strlen(buf),
                     ", %dx%d, %0.2f fps",
                     enc->width, enc->height, 
                     (float)enc->frame_rate / FRAME_RATE_BASE);
        }
        if (encode) {
            snprintf(buf + strlen(buf), buf_size - strlen(buf),
                     ", q=%d-%d", enc->qmin, enc->qmax);
        }
        bitrate = enc->bit_rate;
        break;
    case CODEC_TYPE_AUDIO:
        snprintf(buf, buf_size,
                 "Audio: %s",
                 codec_name);
        switch (enc->channels) {
            case 1:
                strcpy_s(channels_str, sizeof(channels_str), "mono");
                break;
            case 2:
                strcpy_s(channels_str, sizeof(channels_str), "stereo");
                break;
            case 6:
                strcpy_s(channels_str, sizeof(channels_str), "5:1");
                break;
            default:
                sprintf_s(channels_str, sizeof(channels_str), "%d channels", enc->channels);
                break;
        }
        if (enc->sample_rate) {
            snprintf(buf + strlen(buf), buf_size - strlen(buf),
                     ", %d Hz, %s",
                     enc->sample_rate,
                     channels_str);
        }
        
        /* for PCM codecs, compute bitrate directly */
        switch(enc->codec_id) {
        case CODEC_ID_PCM_S16LE:
        case CODEC_ID_PCM_S16BE:
        case CODEC_ID_PCM_U16LE:
        case CODEC_ID_PCM_U16BE:
            bitrate = enc->sample_rate * enc->channels * 16;
            break;
        case CODEC_ID_PCM_S8:
        case CODEC_ID_PCM_U8:
        case CODEC_ID_PCM_ALAW:
        case CODEC_ID_PCM_MULAW:
            bitrate = enc->sample_rate * enc->channels * 8;
            break;
        default:
            bitrate = enc->bit_rate;
            break;
        }
        break;
    default:
        av_abort();
    }
    if (encode) {
        if (enc->flags & CODEC_FLAG_PASS1)
            snprintf(buf + strlen(buf), buf_size - strlen(buf),
                     ", pass 1");
        if (enc->flags & CODEC_FLAG_PASS2)
            snprintf(buf + strlen(buf), buf_size - strlen(buf),
                     ", pass 2");
    }
    if (bitrate != 0) {
        snprintf(buf + strlen(buf), buf_size - strlen(buf), 
                 ", %d kb/s", bitrate / 1000);
    }
}

/* Picture field are filled with 'ptr' addresses */
void avpicture_fill(AVPicture *picture, UINT8 *ptr,
                    int pix_fmt, int width, int height)
{
    int size;

    size = width * height;
    switch(pix_fmt) {
    case PIX_FMT_YUV420P:
        picture->data[0] = ptr;
        picture->data[1] = picture->data[0] + size;
        picture->data[2] = picture->data[1] + size / 4;
        picture->linesize[0] = width;
        picture->linesize[1] = width / 2;
        picture->linesize[2] = width / 2;
        break;
    case PIX_FMT_YUV422P:
        picture->data[0] = ptr;
        picture->data[1] = picture->data[0] + size;
        picture->data[2] = picture->data[1] + size / 2;
        picture->linesize[0] = width;
        picture->linesize[1] = width / 2;
        picture->linesize[2] = width / 2;
        break;
    case PIX_FMT_YUV444P:
        picture->data[0] = ptr;
        picture->data[1] = picture->data[0] + size;
        picture->data[2] = picture->data[1] + size;
        picture->linesize[0] = width;
        picture->linesize[1] = width;
        picture->linesize[2] = width;
        break;
    case PIX_FMT_RGB24:
    case PIX_FMT_BGR24:
        picture->data[0] = ptr;
        picture->data[1] = NULL;
        picture->data[2] = NULL;
        picture->linesize[0] = width * 3;
        break;
    case PIX_FMT_RGBA32:
    case PIX_FMT_BGRA32:
        picture->data[0] = ptr;
        picture->data[1] = NULL;
        picture->data[2] = NULL;
        picture->linesize[0] = width * 4;
        break;
    case PIX_FMT_YUV422:
        picture->data[0] = ptr;
        picture->data[1] = NULL;
        picture->data[2] = NULL;
        picture->linesize[0] = width * 2;
        break;
    default:
        picture->data[0] = NULL;
        picture->data[1] = NULL;
        picture->data[2] = NULL;
        break;
    }
}

int avpicture_get_size(int pix_fmt, int width, int height)
{
    int size;

    size = width * height;
    switch(pix_fmt) {
    case PIX_FMT_YUV420P:
        size = (size * 3) / 2;
        break;
    case PIX_FMT_YUV422P:
        size = (size * 2);
        break;
    case PIX_FMT_YUV444P:
        size = (size * 3);
        break;
    case PIX_FMT_RGB24:
    case PIX_FMT_BGR24:
        size = (size * 3);
        break;
    case PIX_FMT_RGBA32:
    case PIX_FMT_BGRA32:
        size = (size * 4);
        break;
    case PIX_FMT_YUV422:
        size = (size * 2);
        break;
    default:
        size = -1;
        break;
    }
    return size;
}

unsigned avcodec_version( void )
{
  return LIBAVCODEC_VERSION_INT;
}

unsigned avcodec_build( void )
{
  return LIBAVCODEC_BUILD;
}

/* must be called before any other functions */
void avcodec_init(void)
{
    static int inited = 0;

    if (inited != 0)
	return;
    inited = 1;

    //dsputil_init();
}

/* this can be called after seeking and before trying to decode the next keyframe */
void avcodec_flush_buffers(AVCodecContext *avctx)
{
    int i;
    MpegEncContext *s = (MpegEncContext *)avctx->priv_data;
    
    switch(avctx->codec_id){
    case CODEC_ID_MPEG1VIDEO:
    case CODEC_ID_H263:
    case CODEC_ID_RV10:
    case CODEC_ID_MJPEG:
    case CODEC_ID_MJPEGB:
    case CODEC_ID_MPEG4:
    case CODEC_ID_MSMPEG4V1:
    case CODEC_ID_MSMPEG4V2:
    case CODEC_ID_MSMPEG4V3:
    case CODEC_ID_WMV1:
    case CODEC_ID_WMV2:
    case CODEC_ID_H263P:
    case CODEC_ID_H263I:
    case CODEC_ID_SVQ1:
        for(i=0; i<MAX_PICTURE_COUNT; i++){
           if(s->picture[i].data[0] && (   s->picture[i].type == FF_BUFFER_TYPE_INTERNAL
                                        || s->picture[i].type == FF_BUFFER_TYPE_USER))
            avctx->release_buffer(avctx, (AVFrame*)&s->picture[i]);
        }
        break;
    default:
        //FIXME
        break;
    }
}

static int raw_encode_init(AVCodecContext *s)
{
    return 0;
}

static int raw_decode_frame(AVCodecContext *avctx,
			    void *data, int *data_size,
			    UINT8 *buf, int buf_size)
{
    return -1;
}

static int raw_encode_frame(AVCodecContext *avctx,
			    unsigned char *frame, int buf_size, void *data)
{
    return -1;
}

AVCodec rawvideo_codec = {
    "rawvideo",
    CODEC_TYPE_VIDEO,
    CODEC_ID_RAWVIDEO,
    0,
    raw_encode_init,
    raw_encode_frame,
    NULL,
    raw_decode_frame,
};
