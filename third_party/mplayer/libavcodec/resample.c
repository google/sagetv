/*
 * Sample rate convertion for both audio and video
 * Copyright (c) 2000 Fabrice Bellard.
 *
 * This file is part of FFmpeg.
 *
 * FFmpeg is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * FFmpeg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with FFmpeg; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

/**
 * @file resample.c
 * Sample rate convertion for both audio and video.
 */

#include "avcodec.h"

struct AVResampleContext;

struct ReSampleContext {
    struct AVResampleContext *resample_context;
    short *temp[6];
    int temp_len;
    float ratio;
    /* channel convert */
    int input_channels, output_channels, filter_channels;
};

/* n1: number of samples */
static void stereo_to_mono(short *output, short *input, int n1)
{
    short *p, *q;
    int n = n1;

    p = input;
    q = output;
    while (n >= 4) {
        q[0] = (p[0] + p[1]) >> 1;
        q[1] = (p[2] + p[3]) >> 1;
        q[2] = (p[4] + p[5]) >> 1;
        q[3] = (p[6] + p[7]) >> 1;
        q += 4;
        p += 8;
        n -= 4;
    }
    while (n > 0) {
        q[0] = (p[0] + p[1]) >> 1;
        q++;
        p += 2;
        n--;
    }
}

/* n1: number of samples */
static void mono_to_stereo(short *output, short *input, int n1)
{
    short *p, *q;
    int n = n1;
    int v;

    p = input;
    q = output;
    while (n >= 4) {
        v = p[0]; q[0] = v; q[1] = v;
        v = p[1]; q[2] = v; q[3] = v;
        v = p[2]; q[4] = v; q[5] = v;
        v = p[3]; q[6] = v; q[7] = v;
        q += 8;
        p += 4;
        n -= 4;
    }
    while (n > 0) {
        v = p[0]; q[0] = v; q[1] = v;
        q += 2;
        p += 1;
        n--;
    }
}

static void multichannel_split(short **outputs, short *input, int n, int chancount)
{
    int i,j;

    for(i=0;i<n;i++) 
    {
        for(j=0;j<chancount;j++)
        {
            outputs[j][i] = *input++;
        }
        // HACK to get around the fact decode/encode don't expect same channel order
        if(chancount==6)
        {   // input is lfe, left, center, right, left surround, right surround 
            // output is left, center, right, left surround, right surround, lfe
            j=outputs[0][i];
            outputs[0][i]=outputs[1][i];
            outputs[1][i]=outputs[2][i];
            outputs[2][i]=outputs[3][i];
            outputs[3][i]=outputs[4][i];
            outputs[4][i]=outputs[5][i];
            outputs[5][i]=j;
        }
    }
}

static void multichannel_mux(short *output, short **inputs, int n, int chancount)
{
    int i,j;

    for(i=0;i<n;i++) 
    {
        for(j=0;j<chancount;j++)
        {
            *output++ = inputs[j][i];
        }
    }
}

static void ac3_5p1_mux(short *output, short *input1, short *input2, int n)
{
    int i;
    short l,r;

    for(i=0;i<n;i++) {
      l=*input1++;
      r=*input2++;
      *output++ = l;           /* left */
      *output++ = (l/2)+(r/2); /* center */
      *output++ = r;           /* right */
      *output++ = 0;           /* left surround */
      *output++ = 0;           /* right surroud */
      *output++ = 0;           /* low freq */
    }
}

ReSampleContext *audio_resample_init(int output_channels, int input_channels,
                                      int output_rate, int input_rate)
{
    ReSampleContext *s;

    if ( input_channels!=output_channels && input_channels > 2 )
      {
        av_log(NULL, AV_LOG_ERROR, "Resampling with input channels greater than 2 unsupported if input count is not output count.");
        return NULL;
      }
    if ( input_channels > 6 || output_channels > 6 )
    {
        av_log(NULL, AV_LOG_ERROR, "Resampling can't handle more than 6 channels.");
        return NULL;
      }

    s = av_mallocz(sizeof(ReSampleContext));
    if (!s)
      {
        av_log(NULL, AV_LOG_ERROR, "Can't allocate memory for resample context.");
        return NULL;
      }

    s->ratio = (float)output_rate / (float)input_rate;

    s->input_channels = input_channels;
    s->output_channels = output_channels;

    s->filter_channels = s->input_channels;
    if (s->output_channels < s->filter_channels)
        s->filter_channels = s->output_channels;

/*
 * ac3 output is the only case where filter_channels could be greater than 2.
 * input channels can't be greater than 2, so resample the 2 channels and then
 * expand to 6 channels after the resampling.
 */
    if(input_channels!=output_channels && s->filter_channels>2)
      s->filter_channels = 2;

    av_log(NULL, AV_LOG_ERROR, "s->filter_channels %d\n", s->filter_channels);
#define TAPS 16
    s->resample_context= av_resample_init(output_rate, input_rate, TAPS, 10, 0, 0.8);

    return s;
}

/* resample audio. 'nb_samples' is the number of input samples */
/* XXX: optimize it ! */
int audio_resample(ReSampleContext *s, short *output, short *input, int nb_samples)
{
    int i, nb_samples1, j;
    short *bufin[6];
    short *bufout[6];
    short *buftmp2[6], *buftmp3[6];
    int lenout;

    if (s->input_channels == s->output_channels && s->ratio == 1.0 && 0) {
        /* nothing to do */
        memcpy(output, input, nb_samples * s->input_channels * sizeof(short));
        return nb_samples;
    }

    /* XXX: move those malloc to resample init code */
    for(i=0; i<s->filter_channels; i++){
        bufin[i]= (short*) av_malloc( (nb_samples + s->temp_len) * sizeof(short) );
        memcpy(bufin[i], s->temp[i], s->temp_len * sizeof(short));
        buftmp2[i] = bufin[i] + s->temp_len;
    }

    /* make some zoom to avoid round pb */
    lenout= (int)(nb_samples * s->ratio) + 16;
    for(j=0;j<s->filter_channels;j++)
    {
        bufout[j]= (short*) av_malloc( lenout * sizeof(short) );
    }

    if (s->input_channels == 2 &&
        s->output_channels == 1) {
        buftmp3[0] = output;
        stereo_to_mono(buftmp2[0], input, nb_samples);
    } else if (s->output_channels >= 2 && s->input_channels == 1) {
        buftmp3[0] = bufout[0];
        memcpy(buftmp2[0], input, nb_samples*sizeof(short));
    } else if (s->output_channels >= 2) {
        for(j=0;j<s->filter_channels;j++)
        {
            buftmp3[j] = bufout[j];
        }
        multichannel_split((short **)&buftmp2[0], input, nb_samples, s->filter_channels);
    } else {
        buftmp3[0] = output;
        memcpy(buftmp2[0], input, nb_samples*sizeof(short));
    }

    nb_samples += s->temp_len;

    /* resample each channel */
    nb_samples1 = 0; /* avoid warning */
    for(i=0;i<s->filter_channels;i++) {
        int consumed;
        int is_last= i+1 == s->filter_channels;

        nb_samples1 = av_resample(s->resample_context, buftmp3[i], bufin[i], &consumed, nb_samples, lenout, is_last);
        s->temp_len= nb_samples - consumed;
        s->temp[i]= av_realloc(s->temp[i], s->temp_len*sizeof(short));
        memcpy(s->temp[i], bufin[i] + consumed, s->temp_len*sizeof(short));
    }

    if (s->output_channels == 2 && s->input_channels == 1) {
        mono_to_stereo(output, buftmp3[0], nb_samples1);
    } else if (s->output_channels == s->input_channels) {
        multichannel_mux(output, (short **) &buftmp3[0], nb_samples1, s->input_channels);
    } else if (s->output_channels == 6) {
        ac3_5p1_mux(output, buftmp3[0], buftmp3[1], nb_samples1);
    }

    for(i=0; i<s->filter_channels; i++)
        av_free(bufin[i]);

    for(j=0;j<s->filter_channels;j++)
    {
        av_free(bufout[j]);
    }
    return nb_samples1;
}

void audio_resample_close(ReSampleContext *s)
{
    int j;
    av_resample_close(s->resample_context);
    for(j=0; j<s->filter_channels; j++)
    {
        av_freep(&s->temp[j]);
    }
    av_free(s);
}
