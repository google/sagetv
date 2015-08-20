/*
 * AC-3 Audio Decoder Dummy
 */

#include <stdio.h>
#include <stddef.h>
#include <math.h>
#include <string.h>

#include "avcodec.h"
#include "bitstream.h"
#include "crc.h"
#include "dsputil.h"
#include "random.h"

static int ac3_decode_dummy_init(AVCodecContext *avctx)
{
    return 0;
}

static int ac3_decode_dummy_frame(AVCodecContext * avctx, void *data, int *data_size, uint8_t *buf, int buf_size)
{
    return 0;
}

static int ac3_decode_dummy_end(AVCodecContext *avctx)
{
    return 0;
}

AVCodec ac3dummy_decoder = {
    .name = "ac3",
    .type = CODEC_TYPE_AUDIO,
    .id = CODEC_ID_AC3,
    .priv_data_size = 0,
    .init = ac3_decode_dummy_init,
    .close = ac3_decode_dummy_end,
    .decode = ac3_decode_dummy_frame,
};
