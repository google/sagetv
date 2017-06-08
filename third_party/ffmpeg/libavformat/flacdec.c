/*
 * Raw FLAC demuxer
 * Copyright (c) 2001 Fabrice Bellard
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

#include "libavcodec/flac.h"
#include "avformat.h"
#include "raw.h"
#include "id3v2.h"
#include "oggdec.h"
#include "vorbiscomment.h"

typedef struct FlacSeekPoint {
    uint64_t samplenum; // time in the stream (in samples)
    uint64_t offset; // offset from first frame
    uint16_t samplecount; // number of samples in that frame
} FlacSeekPoint;

typedef struct FlacContext {
    int hasseektable;
    int firstpacket; // send a dts on first packets after seek
    uint64_t firstdts;
    int64_t frameoffset; // Offset of the first frame
    uint32_t seekcount;
    FlacSeekPoint *seekpoints;
} FlacContext;

static int flac_read_header(AVFormatContext *s,
                             AVFormatParameters *ap)
{
    FlacContext *flac = s->priv_data;
    uint8_t buf[ID3v2_HEADER_SIZE];
    int ret, metadata_last=0, metadata_type, metadata_size, found_streaminfo=0;
    uint8_t header[4];
    uint8_t *buffer=NULL;
    AVStream *st = av_new_stream(s, 0);
    if (!st)
        return AVERROR(ENOMEM);
    st->codec->codec_type = AVMEDIA_TYPE_AUDIO;
    st->codec->codec_id = CODEC_ID_FLAC;
    st->need_parsing = AVSTREAM_PARSE_FULL;
    /* the parameters will be extracted from the compressed bitstream */

    flac->hasseektable=0;
    flac->firstpacket=1;
    flac->firstdts=0;
    flac->seekcount=0;

    /* skip ID3v2 header if found */
    ret = get_buffer(s->pb, buf, ID3v2_HEADER_SIZE);
    if (ret == ID3v2_HEADER_SIZE && ff_id3v2_match(buf, ID3v2_DEFAULT_MAGIC)) {
        int len = ff_id3v2_tag_len(buf);
        url_fseek(s->pb, len - ID3v2_HEADER_SIZE, SEEK_CUR);
    } else {
        url_fseek(s->pb, 0, SEEK_SET);
    }

    /* if fLaC marker is not found, assume there is no header */
    if (get_le32(s->pb) != MKTAG('f','L','a','C')) {
        url_fseek(s->pb, -4, SEEK_CUR);
        return 0;
    }

    /* process metadata blocks */
    while (!url_feof(s->pb) && !metadata_last) {
        get_buffer(s->pb, header, 4);
        ff_flac_parse_block_header(header, &metadata_last, &metadata_type,
                                   &metadata_size);
        switch (metadata_type) {
        /* allocate and read metadata block for supported types */
        case FLAC_METADATA_TYPE_STREAMINFO:
        case FLAC_METADATA_TYPE_VORBIS_COMMENT:
            buffer = av_mallocz(metadata_size + FF_INPUT_BUFFER_PADDING_SIZE);
            if (!buffer) {
                return AVERROR(ENOMEM);
            }
            if (get_buffer(s->pb, buffer, metadata_size) != metadata_size) {
                av_freep(&buffer);
                return AVERROR(EIO);
            }
            break;
		// SageTV: report picture size and offset for external extraction
		case FLAC_METADATA_TYPE_PICTURE: {
			char value[64];
			uint32_t length;
			get_le32(s->pb);            // Skip picture type
			length = get_be32(s->pb);
			url_fskip(s->pb, length);   // mime type
			length = get_be32(s->pb);
			url_fskip(s->pb, length);   // description
			url_fskip(s->pb, 16);       // DWORDS: width, height, depth, palette size
			length = get_be32(s->pb);
			snprintf(value, sizeof(value), "%d", length);
			av_metadata_set(&s->metadata, "ThumbnailSize", value);
			snprintf(value, sizeof(value), "%"PRIi64, url_ftell(s->pb));
			av_metadata_set(&s->metadata, "ThumbnailOffset", value);
			ret = url_fseek(s->pb, length, SEEK_CUR);
			if (ret < 0)
				return ret;
			}
			break;
        case FLAC_METADATA_TYPE_SEEKTABLE:
            // Each entry is 64 bits sample number, 64bits offset from first frame
            // 16 bit number of samples
            {
                int32_t ind=0;
                flac->seekcount=metadata_size/18;
                flac->seekpoints = av_mallocz(flac->seekcount*sizeof(FlacSeekPoint));
                if (!flac->seekpoints)
                    return AVERROR(ENOMEM);
                for(ind=0;ind<flac->seekcount;ind++)
                {
                    flac->seekpoints[ind].samplenum=get_be64(s->pb);
                    flac->seekpoints[ind].offset=get_be64(s->pb);
                    flac->seekpoints[ind].samplecount=get_be16(s->pb);
                    metadata_size-=18;
                }
                flac->hasseektable=1;
            }
            break;
			break;
        /* skip metadata block for unsupported types */
        default:
            ret = url_fseek(s->pb, metadata_size, SEEK_CUR);
            if (ret < 0)
                return ret;
        }

        if (metadata_type == FLAC_METADATA_TYPE_STREAMINFO) {
            FLACStreaminfo si;
            /* STREAMINFO can only occur once */
            if (found_streaminfo) {
                av_freep(&buffer);
                return AVERROR_INVALIDDATA;
            }
            if (metadata_size != FLAC_STREAMINFO_SIZE) {
                av_freep(&buffer);
                return AVERROR_INVALIDDATA;
            }
            found_streaminfo = 1;
            st->codec->extradata      = buffer;
            st->codec->extradata_size = metadata_size;
            buffer = NULL;

            /* get codec params from STREAMINFO header */
            ff_flac_parse_streaminfo(st->codec, &si, st->codec->extradata);

            /* set time base and duration */
            if (si.samplerate > 0) {
                av_set_pts_info(st, 64, 1, si.samplerate);
                if (si.samples > 0)
                    st->duration = si.samples;
            }
        } else {
            /* STREAMINFO must be the first block */
            if (!found_streaminfo) {
                av_freep(&buffer);
                return AVERROR_INVALIDDATA;
            }
            /* process supported blocks other than STREAMINFO */
            if (metadata_type == FLAC_METADATA_TYPE_VORBIS_COMMENT) {
                if (ff_vorbis_comment(s, &s->metadata, buffer, metadata_size)) {
                    av_log(s, AV_LOG_WARNING, "error parsing VorbisComment metadata\n");
                }
            }
            av_freep(&buffer);
        }
    }
    // The index we have built doesn't use the right base
    flac->frameoffset=url_ftell(s->pb);
    return 0;
}

static int flac_probe(AVProbeData *p)
{
    uint8_t *bufptr = p->buf;
    uint8_t *end    = p->buf + p->buf_size;

    if(ff_id3v2_match(bufptr, ID3v2_DEFAULT_MAGIC))
        bufptr += ff_id3v2_tag_len(bufptr);

    if(bufptr > end-4 || memcmp(bufptr, "fLaC", 4)) return 0;
    else                                            return AVPROBE_SCORE_MAX/2;
}

#define FLAC_PACKET_SIZE 1024

static int flac_read_partial_packet(AVFormatContext *s, AVPacket *pkt)
{
    FlacContext *flac = s->priv_data;
    int ret, size;

    size = FLAC_PACKET_SIZE;

    if (av_new_packet(pkt, size) < 0)
        return AVERROR(EIO);

    if(flac->firstpacket)
    {
        pkt->dts=flac->firstdts;
        flac->firstpacket=0;
    }
    pkt->pos= url_ftell(s->pb);
    pkt->stream_index = 0;
    ret = get_partial_buffer(s->pb, pkt->data, size);
    if (ret <= 0) {
        av_free_packet(pkt);
        return AVERROR(EIO);
    }
    pkt->size = ret;
    return ret;
}

static int flac_read_seek(AVFormatContext *s, int stream_index, int64_t sample_time, int flags)
{
    FlacContext *flac = s->priv_data;
    AVStream *st;
    int i, ret, ind;
    uint64_t pos;
    if (stream_index >= s->nb_streams)
        return -1;
    if (sample_time < 0)
        sample_time = 0;
    st = s->streams[stream_index];

    for(ind=0;ind<flac->seekcount;ind++)
    {
        if(flac->seekpoints[ind].samplenum>=sample_time)
            break;
    }
    ind-=1;
    
    if(ind<0)
    {
        pos=0;
        flac->firstdts=0;
    }
    else
    {
        pos=flac->seekpoints[ind].offset;
        flac->firstdts=flac->seekpoints[ind].samplenum;
    }
    pos+=flac->frameoffset;

    if ((ret = url_fseek(s->pb, pos, SEEK_SET)) < 0)
        return ret;

    flac->firstpacket=1;

    /* for each stream, reset read state */
    for(i = 0; i < s->nb_streams; i++) {
        st = s->streams[i];

        if (st->parser) {
            av_parser_close(st->parser);
            st->parser = NULL;
            av_free_packet(&st->cur_pkt);
        }
        st->last_IP_pts = AV_NOPTS_VALUE;
        st->cur_dts = AV_NOPTS_VALUE; /* we set the current DTS to an unspecified origin */
        st->reference_dts = AV_NOPTS_VALUE;
        /* fail safe */
        st->cur_ptr = NULL;
        st->cur_len = 0;
    }
    return 0;
}

static int flac_read_close(AVFormatContext *s)
{
    FlacContext *flac = s->priv_data;
    av_freep(&flac->seekpoints);
    return 0;
}

AVInputFormat flac_demuxer = {
    "flac",
    NULL_IF_CONFIG_SMALL("raw FLAC"),
    sizeof(FlacContext),
    flac_probe,
    flac_read_header,
    flac_read_partial_packet,
    flac_read_close,
    flac_read_seek,
    .flags= AVFMT_GENERIC_INDEX,
    .extensions = "flac",
    .value = CODEC_ID_FLAC,
    .metadata_conv = ff_vorbiscomment_metadata_conv,
};
