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
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include "MiniImage.h"
#include "png.h"
#include "jpeglib.h"
#ifdef EM8634
#include <stdint.h>
#include "../../../so/swscale/swscale.h"
#include "gif_lib.h"
#include "tiffio.h"

#define ALLOW_OS_CODE 1
#include <rua/include/rua.h>
#include <dcc/include/dcc.h>
#include <dcc/src/dcc_common.h>
#include <gbuslib/include/gbus_fifo.h>
#endif

#ifdef EM8654
#define IOP_PMEM_SIZE 0x6000
#define IOP_DMEM_SIZE 0x4000
#define IOP_DATA_BASE 0x84000000
#define IOP_DATA_TEXT (IOP_DATA_BASE+0x70000)
#define IOP_REG_BASE 0xB0000
#define IOP_PMEM_BASE 0x160000
#define IOP_DMEM_BASE 0x170000
#else // 8634
#define IOP_PMEM_SIZE 0x3000
#define IOP_DMEM_SIZE 0xC000
#define IOP_DATA_BASE 0x17A00000
#define IOP_DATA_TEXT (IOP_DATA_BASE+0x70000)
#define IOP_REG_BASE 0xD0000
#define IOP_PMEM_BASE 0x1A0000
#define IOP_DMEM_BASE 0x1B0000
#endif

#ifdef EM8634
#define HWJPEG
#endif

#ifdef HWJPEG
#define HUFF_LOOKAHEAD 8

long long sleepcount=0;

int waitcount=0;
int blockcount=0;

// Total length 18+17+17+256 = 308 (1232 bytes)
typedef struct {
  /* Basic tables: (element [0] of each array is unused) */
  int maxcode[18];		/* largest code of length k (-1 if none) */
  /* (maxcode[17] is a sentinel to ensure jpeg_huff_decode terminates) */
  int valoffset[17];		/* huffval[] offset for codes of length k */
  /* valoffset[k] = huffval[] index of 1st symbol of code length k, less
   * the smallest code of length k; so given a code of length k, the
   * corresponding symbol is huffval[code + valoffset[k]]
   */

  /* These two fields directly represent the contents of a JPEG DHT marker */
  int bits[17];		/* bits[k] = # of symbols with codes of */
				/* length k bits; bits[0] is unused */

//  int huffval[256];		/* The symbols, in order of incr code length */

  /* Lookahead tables: indexed by the next HUFF_LOOKAHEAD bits of
   * the input data stream.  If the next Huffman code is no more
   * than HUFF_LOOKAHEAD bits long, we can obtain its length and
   * the corresponding symbol directly from these tables.
   */
//  int look_nbits[1<<HUFF_LOOKAHEAD]; /* # bits, or 0 if too long */
//  int look_sym[1<<HUFF_LOOKAHEAD]; /* symbol, or unused */

// We merged the 3 tables in one
// look_nbits is the lower 8 bits, then look_sym at 8-15 and finally huffval at bits 16-23
    int hufftable[256];
} d_derived_tbl;

#define D_MAX_BLOCKS_IN_MCU   10

// This is part of the jpeg infifo image state structure
// 2+308*4+10+10+10+80+256+4=1604
typedef struct {
	int blocks_in_MCU;
	int scaling;
	d_derived_tbl tables[4]; // 2DC then 2AC
	int dc_cur_tbls[D_MAX_BLOCKS_IN_MCU];
	int ac_cur_tbls[D_MAX_BLOCKS_IN_MCU];
	int coef_cur_tbls[D_MAX_BLOCKS_IN_MCU];
	int jpeg_natural_order[64+16];
	int coeff[256]; // Contains 4 sets 8 bits each interleaved
	int MCU_membership[D_MAX_BLOCKS_IN_MCU];
	int last_dc_val[D_MAX_BLOCKS_IN_MCU];
} jpeg_image_state;

typedef struct
{
    struct gbus *pgbus;
    unsigned int *hwmem;
    struct gbus_fifo *jpeg_commandfifo; // Note this is a gbus address
    struct gbus_fifo *jpeg_bitstreamfifo; // Note this is a gbus address
    unsigned int jpeg_commandfifo_base;
    unsigned int jpeg_commandfifo_size;
    RMuint32 * jpegcommandfifo_map;
    jpeg_image_state jpegstate;
    RMuint8 *next_input_byte;
    RMuint32 bytes_in_buffer;
    RMint32 nbits;
    RMuint32 bitbuffer;
    RMuint32 width;
    RMuint32 height;
    RMuint32 restarts_to_go;
} JPEGDecoder;

extern struct RUA *pRUA;
extern struct gbus *pgbus;
extern struct llad *pllad;

int JPEGSetHuffTable(JPEGDecoder *H, int index, d_derived_tbl *dtbl)
{
    memcpy(&H->jpegstate.tables[index], dtbl, sizeof(d_derived_tbl));
    return 0;
}

int JPEGSetCoeffTable(JPEGDecoder *H, int index, unsigned short *quantval)
{
    int i;
    unsigned int mask[4] = {0xFF, 0xFF00, 0xFF0000, 0xFF000000 };
    if(index>3) return -1;
    for(i=0;i<256;i++)
    {
        H->jpegstate.coeff[i]&=~mask[index];
        H->jpegstate.coeff[i]|=quantval[i]<<(index*8);
    }
    return 0;
}

static int jpeg_natural_order[64+16] = {
  0,  1,  8, 16,  9,  2,  3, 10,
 17, 24, 32, 25, 18, 11,  4,  5,
 12, 19, 26, 33, 40, 48, 41, 34,
 27, 20, 13,  6,  7, 14, 21, 28,
 35, 42, 49, 56, 57, 50, 43, 36,
 29, 22, 15, 23, 30, 37, 44, 51,
 58, 59, 52, 45, 38, 31, 39, 46,
 53, 60, 61, 54, 47, 55, 62, 63,
 63, 63, 63, 63, 63, 63, 63, 63, /* extra entries for safety in decoder */
 63, 63, 63, 63, 63, 63, 63, 63
};

int JPEGIsDecodingMCU(JPEGDecoder *H)
{
    RMuint32 wr_ptr1, wr_ptr2, wr_size1;
    if(gbus_fifo_get_readable_size(H->pgbus, H->jpeg_commandfifo, &wr_ptr1, &wr_size1, &wr_ptr2)<8)
    {
        return 0;
    }
    return 1;
}

// Todo: destination/scaling...
int JPEGDecodeMCU(JPEGDecoder *H, int mcucount, int blocks_in_MCU, 
    int *dc_cur_tbls, int *ac_cur_tbls, int *coef_cur_tbls, int *MCU_membership,
    int *last_dc_val, int scale, unsigned int ybufferphys, unsigned int uvbufferphys)
{
    int i;
    RMuint32 wr_ptr1, wr_ptr2, wr_size1;
    int count=0;
    if(blocks_in_MCU>D_MAX_BLOCKS_IN_MCU)
        return -1;

    // We might want to verify there is no command in progress...
    while(gbus_fifo_get_writable_size(H->pgbus, H->jpeg_commandfifo, &wr_ptr1, &wr_size1, &wr_ptr2)<8)
    {
        usleep(1);
        count++;
        if(count>1000000)
        {
            fprintf(stderr, "timeout decode mcu\n");
            return -2;
        }
    }

    H->jpegstate.blocks_in_MCU=blocks_in_MCU;
    H->jpegstate.scaling=8; // in 1/8 units?

    for(i=0;i<blocks_in_MCU;i++)
    {
        H->jpegstate.dc_cur_tbls[i]=dc_cur_tbls[i];
        H->jpegstate.ac_cur_tbls[i]=ac_cur_tbls[i];
        H->jpegstate.coef_cur_tbls[i]=coef_cur_tbls[i];
        H->jpegstate.MCU_membership[i]=MCU_membership[i];
        H->jpegstate.last_dc_val[i]=last_dc_val[i];
    }

    for(i=0;i<64+16;i++)
    {
        H->jpegstate.jpeg_natural_order[i]=jpeg_natural_order[i];
    }

    // Copy jpeg_image_state to hw mem
    //memcpy(H->jpegmemvirt,&H->jpegstate, sizeof(jpeg_image_state));

    // Create jpeg command to decode X mcus
    // We assume it will wrap around nicely because command buffer size is multiple of command size

    gbus_write_uint32(H->pgbus, IOP_DMEM_BASE+wr_ptr1*4, mcucount);
    gbus_write_uint32(H->pgbus, IOP_DMEM_BASE+wr_ptr1*4+4, ybufferphys);
    gbus_write_uint32(H->pgbus, IOP_DMEM_BASE+wr_ptr1*4+8, uvbufferphys);
    gbus_write_uint32(H->pgbus, IOP_DMEM_BASE+wr_ptr1*4+12,
        H->width|(H->height<<16));
    gbus_write_uint32(H->pgbus, IOP_DMEM_BASE+wr_ptr1*4+16,8/scale);
    gbus_write_uint32(H->pgbus, IOP_DMEM_BASE+wr_ptr1*4+20,(blocks_in_MCU==6) ? 0 : 1);
    gbus_write_uint32(H->pgbus, IOP_DMEM_BASE+wr_ptr1*4+24,0);
    gbus_write_uint32(H->pgbus, IOP_DMEM_BASE+wr_ptr1*4+28,0);

    gbus_fifo_incr_write_ptr(H->pgbus, H->jpeg_commandfifo, 8);
    return 0;
}

JPEGDecoder *OpenJPEGDecoder(struct gbus *pgbus)
{
    JPEGDecoder *H;

    H=malloc(sizeof(JPEGDecoder));
    if(H==NULL)
    {
        goto fail1;
    }

    memset(H, 0, sizeof(JPEGDecoder));
    H->pgbus=pgbus;

    // Those are in gbus space
    H->jpeg_commandfifo = (struct gbus_fifo *) (IOP_DMEM_BASE+0xC0*4);
    H->jpeg_bitstreamfifo = (struct gbus_fifo *) (IOP_DMEM_BASE+0xC4*4);

    H->jpeg_commandfifo_base = gbus_read_uint32(H->pgbus, (RMuint32) &(H->jpeg_commandfifo->base));
    H->jpeg_commandfifo_size = gbus_read_uint32(H->pgbus, (RMuint32) &(H->jpeg_commandfifo->size));

    // The real base is passed in the decode command
    gbus_write_uint32(pgbus, (RMuint32) &(H->jpeg_bitstreamfifo->base), 0);
    #ifdef EM8654
    gbus_write_uint32(pgbus, (RMuint32) &(H->jpeg_bitstreamfifo->size), 0x400);
    #else
    gbus_write_uint32(pgbus, (RMuint32) &(H->jpeg_bitstreamfifo->size), 0x200);
    #endif
    gbus_write_uint32(pgbus, (RMuint32) &(H->jpeg_bitstreamfifo->rd), 0);
    gbus_write_uint32(pgbus, (RMuint32) &(H->jpeg_bitstreamfifo->wr), 0);

    // Clear bit end
    gbus_write_uint32(pgbus, IOP_DMEM_BASE+0xC8*4, 0);

    {
        int memfd;
        unsigned int *hwmem;
        memfd=open("/dev/mem", O_RDWR|O_SYNC);
        if(memfd<0)
        {
            fprintf(stderr, "Couldn't open hwmem\n");
            return(0);
        }
        hwmem = mmap(0, 0x200000, PROT_READ|PROT_WRITE, MAP_SHARED, 
            memfd, 0x0); 
        if(hwmem==MAP_FAILED)
        {
            fprintf(stderr,"Couldn't map hwmem\n");
            close(memfd);
            goto fail2;
        }

        H->hwmem=hwmem;
        close(memfd);
    }

    return H;
fail2:
    free(H);
    H=NULL;
fail1:
    return 0;
}

int CloseJPEGDecoder(JPEGDecoder *H)
{
    munmap(H->hwmem, 0x200000);
    free(H);
    return 0;
}

#endif


typedef struct {
  struct jpeg_source_mgr pub;
  MiniImageState *state;
} socket_source_mgr;

static long long get_timebase()
{
  struct timeval tm;
  gettimeofday(&tm, 0);
  return tm.tv_sec * 1000000LL + (tm.tv_usec);
}

typedef socket_source_mgr * socket_src_ptr;

extern int fullrecv(int sock, void *vbuffer, int size);

static int discardData(MiniImageState *state, int len)
{
    int count=0;
    while(len)
    {
        int status;
        int readlen=len>state->buffersize ? state->buffersize : len;
        if(!state->isfile)
        {
            status=fullrecv(state->sd, state->buffer, readlen);
        }
        else
        {
            status=read(state->sd, state->buffer, readlen);
        }
        if(status<=0) return -1;
        state->usedlen+=status;
        len-=status;
    }
}

int readData(MiniImageState *state, unsigned char *data, int len)
{
    int count=0;
    //fprintf(stderr, "readData %d\n",len);
    while(len)
    {
        int readlen=len;
        // Read from buffer first
        if(state->bufferlevel)
        {
            if(readlen>state->bufferlevel) readlen=state->bufferlevel;
            memcpy(data, &state->buffer[state->bufferoffset], readlen);
            state->bufferoffset+=readlen;
            state->bufferlevel-=readlen;
            len-=readlen;
            count+=readlen;
            data+=readlen;
            //fprintf(stderr, "Read %d bytes from buffer\n",readlen);
        }
        else
        {
            int status;
            if(readlen>(state->datalen-state->usedlen)) readlen=state->datalen-state->usedlen;
            if(!state->isfile)
            {
                status=fullrecv(state->sd, data, readlen);
            }
            else
            {
                status=read(state->sd, data, readlen);
            }
            if(status<=0) return -1;
            state->usedlen+=status;
            len-=status;
            count+=status;
            data+=status;
            //fprintf(stderr, "Read %d bytes from socket\n",readlen);
        }
    }
    return count;
}

static void user_read_data(png_structp png_ptr,
        png_bytep data, png_size_t length)
{
    MiniImageState *state = (MiniImageState *)png_ptr->io_ptr;
    readData(state, data, length);
}

// Based on example from png library
static int LoadPNG(MiniImageState *state)
{
    unsigned char header[8];
    png_uint_32 width, height;
    int bit_depth, color_type, interlace_type;
    int number_passes,pass,y;
    int is_png;
    png_structp png_ptr;
    png_infop info_ptr;

    if(readData(state, header, 8)!=8) return 0;
    is_png = !png_sig_cmp(header, 0, 8);
    if(!is_png) return 0;

    png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, 
        NULL, NULL, NULL);
    if(!png_ptr)
        return 0;

    info_ptr = png_create_info_struct(png_ptr);
    if(!info_ptr) 
    {
        png_destroy_read_struct(&png_ptr,
           (png_infopp)NULL, (png_infopp)NULL);
        return 0;
    }

    if(setjmp(png_jmpbuf(png_ptr)))
    {
        fprintf(stderr, "Encountered an error reading the png...\n");
        fflush(stderr);
        png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);
        return 0;
    }

    png_set_read_fn(png_ptr, (void *)state, user_read_data);
    //png_init_io(png_ptr, fp);
    png_set_sig_bytes(png_ptr, 8);
    
    png_read_info(png_ptr, info_ptr);
    
    png_get_IHDR(png_ptr, info_ptr, &width, &height, &bit_depth, &color_type,
       &interlace_type, int_p_NULL, int_p_NULL);

    fprintf(stderr, "PNG size %dx%d\n",width,height);
    if(height!=state->height || width!=state->width)
    {
        fprintf(stderr, "size doesn't match, can't decode PNG\n");
    }
    // We don't want 16 bit colors
    png_set_strip_16(png_ptr);
    png_set_packing(png_ptr);
    if(color_type == PNG_COLOR_TYPE_PALETTE)
        png_set_palette_to_rgb(png_ptr);
    if(color_type == PNG_COLOR_TYPE_GRAY && bit_depth < 8)
        png_set_gray_1_2_4_to_8(png_ptr);
    if(png_get_valid(png_ptr, info_ptr, PNG_INFO_tRNS))
        png_set_tRNS_to_alpha(png_ptr);
    /* flip the RGB pixels to BGR (or RGBA to BGRA) */
    if(color_type & PNG_COLOR_MASK_COLOR)
        png_set_bgr(png_ptr);

    /* swap the RGBA or GA data to ARGB or AG (or BGRA to ABGR) */
//    png_set_swap_alpha(png_ptr);
    png_set_filler(png_ptr, 0xff, PNG_FILLER_AFTER);
    number_passes = png_set_interlace_handling(png_ptr);
    png_read_update_info(png_ptr, info_ptr);
    
	{
		for(pass = 0; pass < number_passes; pass++)
		{
			for(y = 0; y < height; y++)
			{
				if(state->output2==NULL)
				{
					unsigned char* buffer = state->output + y*state->destwidth*4;
					png_read_rows(png_ptr, &buffer, png_bytepp_NULL, 1);
				}
			}
		}
	}


    /* read rest of file, and get additional chunks in info_ptr - REQUIRED */
    png_read_end(png_ptr, info_ptr);

    /* clean up after the read, and free any memory allocated - REQUIRED */
    png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);

    /* that's it */
    return 0;
}


void socket_init_source (j_decompress_ptr cinfo)
{
  socket_src_ptr src = (socket_src_ptr) cinfo->src;

}


boolean socket_fill_input_buffer (j_decompress_ptr cinfo)
{
  socket_src_ptr src = (socket_src_ptr) cinfo->src;
  size_t nbytes;
  nbytes = src->state->buffersize > src->state->datalen-src->state->usedlen ?
    src->state->datalen-src->state->usedlen : src->state->buffersize;
  nbytes = readData(src->state, src->state->buffer, nbytes );

  if (nbytes <= 0) {
    fprintf(stderr, "Inserting fake EOI\n");
    /* Insert a fake EOI marker */
    src->state->buffer[0] = (JOCTET) 0xFF;
    src->state->buffer[1] = (JOCTET) JPEG_EOI;
    nbytes = 2;
  }

  src->pub.next_input_byte = src->state->buffer;
  src->pub.bytes_in_buffer = nbytes;

  return TRUE;
}

void socket_skip_input_data (j_decompress_ptr cinfo, long num_bytes)
{
  socket_src_ptr src = (socket_src_ptr) cinfo->src;

  if (num_bytes > 0) 
  {
    while (num_bytes > (long) src->pub.bytes_in_buffer) 
    {
      num_bytes -= (long) src->pub.bytes_in_buffer;
      (void) socket_fill_input_buffer(cinfo);
    }
    src->pub.next_input_byte += (size_t) num_bytes;
    src->pub.bytes_in_buffer -= (size_t) num_bytes;
  }
}

void socket_term_source (j_decompress_ptr cinfo)
{
}

struct my_error_mgr {
  struct jpeg_error_mgr pub;	/* "public" fields */

  jmp_buf setjmp_buffer;	/* for return to caller */
};

typedef struct my_error_mgr * my_error_ptr;

/*
 * Here's the routine that will replace the standard error_exit method:
 */

static void my_error_exit (j_common_ptr cinfo)
{
  /* cinfo->err really points to a my_error_mgr struct, so coerce pointer */
  my_error_ptr myerr = (my_error_ptr) cinfo->err;

  /* Always display the message. */
  /* We could postpone this until after returning, if we chose. */
  (*cinfo->err->output_message) (cinfo);

  /* Return control to the setjmp point */
  longjmp(myerr->setjmp_buffer, 1);
}

#ifdef HWJPEG
static int JPEGInitReadRaw(JPEGDecoder *H, struct jpeg_decompress_struct  *cinfo,
    int width, int height, int scale, unsigned int ybufferphys, unsigned int uvbufferphys)
{
    int i,isDC, tblno;
    H->next_input_byte = (RMuint8 *) cinfo->src->next_input_byte;
    H->bytes_in_buffer = cinfo->src->bytes_in_buffer;
    H->width=width;
    H->height=height;
    fprintf(stderr, "init 1\n");
    // Build the needed information
    for(isDC=0;isDC<2;isDC++)
    {
        for(tblno=0;tblno<2;tblno++)
        {
            JHUFF_TBL *htbl;
            d_derived_tbl dtbl;
            int p, l, si, numsymbols;
            int lookbits, ctr;
            char huffsize[257];
            unsigned int huffcode[257];
            unsigned int code;
            htbl =
                isDC ? cinfo->dc_huff_tbl_ptrs[tblno] : cinfo->ac_huff_tbl_ptrs[tblno];
            p = 0;
            for (l = 1; l <= 16; l++) {
                i = (int) htbl->bits[l];
                if (i < 0 || p + i > 256)	/* protect against table overrun */
                {
                    fprintf(stderr, "invalid huffman table\n");
                }
                while (i--)
                    huffsize[p++] = (char) l;
            }
            huffsize[p] = 0;
            numsymbols = p;
            /* Figure C.2: generate the codes themselves */
            /* We also validate that the counts represent a legal Huffman code tree. */

            code = 0;
            si = huffsize[0];
            p = 0;
            while (huffsize[p]) {
                while (((int) huffsize[p]) == si) {
                huffcode[p++] = code;
                code++;
                }
                /* code is now 1 more than the last code used for codelength si; but
                * it must still fit in si bits, since no code is allowed to be all ones.
                */
                if (((INT32) code) >= (((INT32) 1) << si))
                {
                    fprintf(stderr, "invalid huffman table\n");
                }
                code <<= 1;
                si++;
            }

            /* Figure F.15: generate decoding tables for bit-sequential decoding */

            p = 0;
            for (l = 1; l <= 16; l++) {
                if (htbl->bits[l]) {
                /* valoffset[l] = huffval[] index of 1st symbol of code length l,
                * minus the minimum code of length l
                */
                dtbl.valoffset[l] = (INT32) p - (INT32) huffcode[p];
                p += htbl->bits[l];
                dtbl.maxcode[l] = (huffcode[p-1]<<(16-l))|((1<<(16-l))-1); /* maximum code of length l */
                } else {
                dtbl.maxcode[l] = -1;	/* -1 if no codes of this length */
                }
            }
            int maxval=-1;
            for (l = 1; l <= 16; l++) 
            {
                if(dtbl.maxcode[l]<maxval) dtbl.maxcode[l]=maxval;
                if(maxval<dtbl.maxcode[l]) maxval=dtbl.maxcode[l];
            }
            dtbl.maxcode[17] = 0xFFFFFL; /* ensures jpeg_huff_decode terminates */

            /* Compute lookahead tables to speed up decoding.
            * First we set all the table entries to 0, indicating "too long";
            * then we iterate through the Huffman codes that are short enough and
            * fill in all the entries that correspond to bit sequences starting
            * with that code.
            */

            memset(dtbl.hufftable, 0, sizeof(dtbl.hufftable));
            for (l = 0; l < 256; l++)
            {
                dtbl.hufftable[l]=htbl->huffval[l] /* <<16*/;
            }

            p = 0;
            for (l = 1; l <= HUFF_LOOKAHEAD; l++) {
                for (i = 1; i <= (int) htbl->bits[l]; i++, p++) {
                /* l = current code's length, p = its index in huffcode[] & huffval[]. */
                /* Generate left-justified code followed by all possible bit sequences */
                lookbits = huffcode[p] << (HUFF_LOOKAHEAD-l);
                for (ctr = 1 << (HUFF_LOOKAHEAD-l); ctr > 0; ctr--) {
                /* dtbl.hufftable[lookbits]|=(htbl->huffval[p]<<8)|l; */
//                dtbl->look_nbits[lookbits] = l;
//                dtbl->look_sym[lookbits] = htbl->huffval[p];
                lookbits++;
                }
                }
            }

            /* Validate symbols as being reasonable.
            * For AC tables, we make no check, but accept all byte values 0..255.
            * For DC tables, we require the symbols to be in range 0..15.
            * (Tighter bounds could be applied depending on the data depth and mode,
            * but this is sufficient to ensure safe decoding.)
            */
            if (isDC) {
                for (i = 0; i < numsymbols; i++) {
                    int sym = htbl->huffval[i];
                    if (sym < 0 || sym > 15)
                    {
                        fprintf(stderr, "invalid huffman table\n");
                    }
                }
            }

            JPEGSetHuffTable(H, (isDC<<1)+tblno, &dtbl);
        }
    }
    fprintf(stderr, "init 10\n");

    for(i=0;i<4;i++)
    {
        if(cinfo->quant_tbl_ptrs[i]!=NULL)
        {
            JPEGSetCoeffTable(H, i, cinfo->quant_tbl_ptrs[i]->quantval);
        }
    }

    fprintf(stderr, "init 11\n");

    {
        int dc_cur_tbls[D_MAX_BLOCKS_IN_MCU];
        int ac_cur_tbls[D_MAX_BLOCKS_IN_MCU];
        int coef_cur_tbls[D_MAX_BLOCKS_IN_MCU];
        int MCU_membership[D_MAX_BLOCKS_IN_MCU];
        int last_dc_val[D_MAX_BLOCKS_IN_MCU];
        int ci, blkn, dctbl, actbl;

        for (blkn = 0; blkn < cinfo->blocks_in_MCU; blkn++)
        {
            jpeg_component_info * compptr;
            ci = cinfo->MCU_membership[blkn];
            compptr = cinfo->cur_comp_info[ci];
            dc_cur_tbls[blkn]=compptr->dc_tbl_no;
            ac_cur_tbls[blkn]=compptr->ac_tbl_no;
            coef_cur_tbls[blkn]=compptr->quant_tbl_no;
            MCU_membership[blkn]=cinfo->MCU_membership[blkn];
            last_dc_val[blkn]=0;
        }


        // cinfo->MCUs_per_row*cinfo->total_iMCU_rows
        if(JPEGDecodeMCU(H, cinfo->MCUs_per_row*cinfo->total_iMCU_rows, cinfo->blocks_in_MCU, 
            dc_cur_tbls, ac_cur_tbls, coef_cur_tbls, MCU_membership,
            last_dc_val, scale,
            ybufferphys, uvbufferphys)<0)
        {
            fprintf(stderr, "error with JPEGDecodeMCU\n");
        }
    }
    H->restarts_to_go = cinfo->restart_interval;
}

#define HUFF_EXTEND(x,s)  ((x) < (1<<((s)-1)) ? (x) + (((-1)<<(s)) + 1) : (x))

#define UPDATE_BITS() \
{ \
    if (cinfo->unread_marker == 0) \
    { \
        int c; \
        if (H->bytes_in_buffer == 0) \
        { \
            if (! (*cinfo->src->fill_input_buffer) (cinfo)) \
            { \
                fprintf(stderr, "Error filling buffer\n"); \
                return 0; \
            } \
            H->next_input_byte = cinfo->src->next_input_byte; \
            H->bytes_in_buffer = cinfo->src->bytes_in_buffer; \
        } \
        H->bytes_in_buffer--; \
        c = GETJOCTET(*H->next_input_byte++); \
        if (c == 0xFF) \
        { \
            do \
            { \
                if (H->bytes_in_buffer == 0) \
                { \
                    if (! (*cinfo->src->fill_input_buffer) (cinfo)) \
                    { \
                        fprintf(stderr, "Error filling buffer\n"); \
                        return 0; \
                    } \
                    H->next_input_byte = cinfo->src->next_input_byte; \
                    H->bytes_in_buffer = cinfo->src->bytes_in_buffer; \
                } \
                H->bytes_in_buffer--; \
                c = GETJOCTET(*H->next_input_byte++); \
            } while (c == 0xFF); \
            if (c == 0) \
            { \
                c = 0xFF; \
            } \
            else \
            { \
                cinfo->unread_marker = c; \
                if(c==0xE9) fprintf(stderr, "Found marker FF%02X\n",c); \
                c = 0x00; \
            } \
        } \
        bitbuffer|=(c<<(24-nbits)); \
        nbits+=8; \
    } \
    else \
    { \
        nbits+=8; \
    } \
}


#define PEEK_BITS(result, count) \
{ \
    while(nbits<count) \
    { \
       UPDATE_BITS();\
    } \
    result=bitbuffer>>(32-count); \
}

#define GET_BITS(result, count) \
{ \
    while(nbits<count) \
    { \
       UPDATE_BITS();\
    } \
    result=bitbuffer>>(32-count); \
    bitbuffer<<=count; \
    nbits-=count; \
}

static RMuint32 dsp_get_writable_size(JPEGDecoder *H, RMuint32 fifo, RMuint32 *wr_ptr1, RMuint32 *wr_size1, RMuint32 *wr_ptr2)
{
	RMuint32 base, size, rd, wr;
	base=0;
#ifdef EM8654
    size=0x400;
#else
    size=0x200;
#endif
	rd = H->hwmem[fifo/4+2];
	wr = H->hwmem[fifo/4+3];
	
	*wr_ptr1 = (RMuint32) (base + wr);
	if (wr >= rd) {
		if (rd > 0) {
			*wr_size1 = size - wr;
			*wr_ptr2 = (RMuint32) base;
			return (*wr_size1 + rd - 1);
		}
		else {
			*wr_size1 = size - 1 - wr;
			*wr_ptr2 = 0;
			return (*wr_size1);
		}			
	}
	else {
		*wr_size1 = rd - 1 - wr;
		*wr_ptr2 = 0;
		return (*wr_size1);
	}
}

static void dsp_incr_write_ptr(JPEGDecoder *H, RMuint32 fifo, RMuint32 incr)
{
	RMuint32 size, wr;
#ifdef EM8654
    size=0x400;
#else
    size=0x200;
#endif
	wr = H->hwmem[fifo/4+3];
	
	wr += incr;
	if (wr >= size)
		wr -= size;
	H->hwmem[fifo/4+3]=wr;
}

struct jpeg_marker_reader {
  JMETHOD(void, reset_marker_reader, (j_decompress_ptr cinfo));
  /* Read markers until SOS or EOI.
   * Returns same codes as are defined for jpeg_consume_input:
   * JPEG_SUSPENDED, JPEG_REACHED_SOS, or JPEG_REACHED_EOI.
   */
  JMETHOD(int, read_markers, (j_decompress_ptr cinfo));
  /* Read a restart marker --- exported for use by entropy decoder only */
  jpeg_marker_parser_method read_restart_marker;

  /* State of marker reader --- nominally internal, but applications
   * supplying COM or APPn handlers might like to know the state.
   */
  boolean saw_SOI;		/* found SOI? */
  boolean saw_SOF;		/* found SOF? */
  int next_restart_num;		/* next restart number expected (0-7) */
  unsigned int discarded_bytes;	/* # of bytes skipped looking for a marker */
};

typedef struct {
  struct jpeg_marker_reader pub; /* public fields */

  /* Application-overridable marker processing methods */
  jpeg_marker_parser_method process_COM;
  jpeg_marker_parser_method process_APPn[16];

  /* Limit on marker data length to save for each marker type */
  unsigned int length_limit_COM;
  unsigned int length_limit_APPn[16];

  /* Status of COM/APPn marker saving */
  jpeg_saved_marker_ptr cur_marker;	/* NULL if not processing a marker */
  unsigned int bytes_read;		/* data bytes read so far in marker */
  /* Note: cur_marker is not linked into marker_list until it's all read. */
} my_marker_reader;

typedef my_marker_reader * my_marker_ptr;

static int process_restart (JPEGDecoder *H, j_decompress_ptr cinfo)
{
    int ci;
    jpeg_image_state *is=&H->jpegstate;

    /* Advance past the RSTn marker */
    if (! (*cinfo->marker->read_restart_marker) (cinfo))
        return FALSE;

    /* Re-initialize DC predictions to 0 */
    for (ci = 0; ci < cinfo->comps_in_scan; ci++)
        is->last_dc_val[ci] = 0;

    /* Reset restart counter */
    H->restarts_to_go = cinfo->restart_interval;

    return TRUE;
}


static int JPEGreadRaw(JPEGDecoder *H, struct jpeg_decompress_struct  *cinfo, 
    JDIMENSION max_lines)
{
    // Go through data until we reach a marker or end
    unsigned int bitbuffer=H->bitbuffer;
    int nbits=H->nbits;
    JDIMENSION MCU_col_num;	/* index of current MCU within row */
    JDIMENSION last_MCU_col = cinfo->MCUs_per_row - 1;
    int blkn, ci;
    jpeg_component_info *compptr;
    jpeg_image_state *is=&H->jpegstate;
    // TEST, this should be taken from idct fifo
    unsigned int *block; 

    for (MCU_col_num = 0; MCU_col_num <= last_MCU_col;
        MCU_col_num++) 
    {
        // Decode the mcu
        int count=0;
        RMuint32 wr_ptr1, wr_ptr2, wr_size1=0, wr_size2=0;
        if(cinfo->restart_interval)
        {
            if(H->restarts_to_go == 0)
            {
                // discard bits in mem
                bitbuffer=0;
                nbits=0;
                if (!process_restart(H, cinfo))
                    break;
            }
        }
        for (blkn = 0; blkn < cinfo->blocks_in_MCU; blkn++)
        {
            int r, bs, k;
            int look,nb;
            // dc tables start at index 2
            d_derived_tbl * dctbl = &is->tables[2+is->dc_cur_tbls[blkn]];
            d_derived_tbl * actbl = &is->tables[is->ac_cur_tbls[blkn]];
            int *coefs = is->coeff;
            int coefshift=is->coef_cur_tbls[blkn]*8;

            count=0;
            if(wr_size1<64)
            {
                while((wr_size2=dsp_get_writable_size(H, (unsigned int) H->jpeg_bitstreamfifo,
                    &wr_ptr1, &wr_size1, &wr_ptr2))<64)
                {
                    #ifdef EM8654
                    usleep(1);
                    // Too much dmem access causes very large slowdown on 8654...
                    // We might want to use a large dram buffer and interrupts?
                    sleepcount+=1;
                    #endif
                    //fprintf(stderr, "blah\n");
                    count+=1;
                    if(count>10000000)
                    {
                        fprintf(stderr, "bitstream too full for buffer\n");
                        count=0;
                        //return 0;
                    }
                }
                waitcount+=count;
                if(wr_size1==0)
                {
                    wr_ptr1=wr_ptr2; // We assume it doesn't go over from end
                    wr_size1=wr_size2;
                }
            }
            block=&H->hwmem[IOP_DMEM_BASE/4+0x800+wr_ptr1];

/*                // Update bitstream buffer
            if(UpdateBitBuffer(s)!=1)
            {
                // We don't have enough bits to proceed
                return 0;
            }*/

            {
                int l;
                // We know this is aligned and assume int size is 4 bytes...
                unsigned char *ptr=&dctbl->maxcode[8];
                // Use binary search for codes instead of lookahead
                //l=GET_VLC((int) &dctbl->maxcode[0]);
                PEEK_BITS(look,16);
                // TODO: inline asm version
/*                l=8;
                l += ((( look > dctbl->maxcode[l] ) ? 1 : 0) << 3);
                l -=4;
                l += ((( look > dctbl->maxcode[l] ) ? 1 : 0) << 2);
                l -= 2;
                l += ((( look > dctbl->maxcode[l] ) ? 1 : 0) << 1);
                l -= 1;
                l += ((( look > dctbl->maxcode[l] ) ? 1 : 0) << 0);*/
                ptr += ((( look > *((int *)ptr) ) ? 1 : 0) << 5);
                ptr -= 16;
                ptr += ((( look > *((int *)ptr) ) ? 1 : 0) << 4);
                ptr -= 8;
                ptr += ((( look > *((int *)ptr) ) ? 1 : 0) << 3);
                ptr -= 4;
                ptr += ((( look > *((int *)ptr) ) ? 1 : 0) << 2);
                l=(((unsigned int)ptr)-((unsigned int) &dctbl->maxcode[0]))>>2;
                nbits-=l;
                look>>=(16-l);
                bitbuffer<<=(l);
//                GET_BITS(look,l);
                bs = (dctbl->hufftable[ (int) (look + dctbl->valoffset[l])]);
//                fprintf(stderr, "bits %d %d = %d\n",look, l, bs);
            }
            /* Section F.2.2.1: decode the DC coefficient difference */
    /*        look = PEEK_BITS(HUFF_LOOKAHEAD);
            if ((nb = dctbl->hufftable[look]&0xFF) != 0) 
            {
                GET_BITS(nb);
                bs = (dctbl->hufftable[look]>>8)&0xFF;
            }
            else
            {
                int l=9;
                look=PEEK_BITS(9);
                while (look > dctbl->maxcode[l])
                {
                    //look <<= 1;
                    l++;
                    look = PEEK_BITS(l);
                }
                GET_BITS(l);

                if (l > 16) // Out of range
                {
                    bs=0; // use 0 as default value
                }
                else
                {
                    bs = (dctbl->hufftable[ (int) (look + dctbl->valoffset[l])]>>16)&0xFF;
                }
            }*/
    
            int ci = is->MCU_membership[blkn];
            if(bs)
            {
                GET_BITS(r,bs);
                bs = HUFF_EXTEND(r, bs);
                bs += is->last_dc_val[ci];
                is->last_dc_val[ci] = bs;
            }
            else
            {
                bs = is->last_dc_val[ci];
            }
            /* Convert DC difference to actual value, update last_dc_val */

            /* Output the DC coefficient (assumes jpeg_natural_order[0] = 0) */
            (block)[0] = (JCOEF) bs * ((coefs[0]>>coefshift)&0xFF);

            /* Section F.2.2.2: decode the AC coefficients */
            /* Since zeroes are skipped, output area must be cleared beforehand */
            for (k = 1; k < DCTSIZE2; k++) 
            {
                int l;
                // We know this is aligned and assume int size is 4 bytes...
                unsigned char *ptr=&actbl->maxcode[8];
                // Use binary search for codes instead of lookahead
                //l=GET_VLC((int) &actbl->maxcode[0]);
                PEEK_BITS(look,16);
                // TODO: inline asm version
/*                l=8;
                l += ((( look > actbl->maxcode[l] ) ? 1 : 0) << 3);
                l -=4;
                l += ((( look > actbl->maxcode[l] ) ? 1 : 0) << 2);
                l -= 2;
                l += ((( look > actbl->maxcode[l] ) ? 1 : 0) << 1);
                l -= 1;
                l += ((( look > actbl->maxcode[l] ) ? 1 : 0) << 0);*/
                ptr += ((( look > *((int *)ptr) ) ? 1 : 0) << 5);
                ptr -= 16;
                ptr += ((( look > *((int *)ptr) ) ? 1 : 0) << 4);
                ptr -= 8;
                ptr += ((( look > *((int *)ptr) ) ? 1 : 0) << 3);
                ptr -= 4;
                ptr += ((( look > *((int *)ptr) ) ? 1 : 0) << 2);
                l=(((unsigned int)ptr)-((unsigned int) &actbl->maxcode[0]))>>2;
                nbits-=l;
                look>>=(16-l);
                bitbuffer<<=(l);
//                GET_BITS(look,l);
                bs = (actbl->hufftable[ (int) (look + actbl->valoffset[l])]);
    /*            if ((nb = actbl->hufftable[look]&0xFF) != 0) 
                {
                    GET_BITS(nb);
                    bs = (actbl->hufftable[look]>>8)&0xFF;
                }
                else
                {
                    int l=9;
                    look=PEEK_BITS(9);
                    while (look > actbl->maxcode[l])
                    {
    //                    look <<= 1;
                        l++;
                        look = PEEK_BITS(l);
                    }
                    GET_BITS(l);
    
                    if (l > 16) // Out of range
                    {
                        bs=0; // use 0 as default value
                    }
                    else
                    {
                        bs = (actbl->hufftable[ (int) (look + actbl->valoffset[l])]>>16)&0xFF;
                    }
                }*/

                r = bs >> 4;
                bs &= 15;

                if (bs) {
                    k += r;
                    GET_BITS(r,bs);
                    bs = HUFF_EXTEND(r, bs);
                    /* Output coefficient in natural (dezigzagged) order.
                    * Note: the extra entries in jpeg_natural_order[] will save us
                    * if k >= DCTSIZE2, which could happen if the data is corrupted.
                    */
                    (block)[is->jpeg_natural_order[k]] = (JCOEF) bs *
                        ((coefs[is->jpeg_natural_order[k]]>>coefshift)&0xFF);

    //                WriteGBUS(0x00110000+is->jpeg_natural_order[k]*4,
    //                (JCOEF) bs * ((coefs[is->jpeg_natural_order[k]]>>coefshift)&0xFF));
//                    printf("block %d = %d\n",is->jpeg_natural_order[k],
//                        block[is->jpeg_natural_order[k]]);
                } else {
                    if (r != 15)
                    break;
                    k += 15;
                }
            }
            dsp_incr_write_ptr(H, (unsigned int) H->jpeg_bitstreamfifo, 64);
            blockcount+=1;
            wr_size1-=64;
            wr_ptr1+=64;
        }
        H->restarts_to_go--;
    }
    cinfo->output_scanline+=8*cinfo->cur_comp_info[0]->v_samp_factor/cinfo->scale_denom;
    H->bitbuffer=bitbuffer;
    H->nbits=nbits;
    return 0;
}
#endif

// Based on example from jpeg library
static int LoadJPEG(MiniImageState *state)
{
    struct jpeg_decompress_struct cinfo;
    struct my_error_mgr jerr;
    socket_source_mgr src;
    JSAMPARRAY buffer;
    unsigned char *bufferraw[3];
    unsigned char *bufferraw2[24];
    int row_stride;
    int imgwidth, imgheight;
    int i;
#ifdef HWJPEG
    JPEGDecoder *jdec=NULL;
#endif
	/* We set up the normal JPEG error routines, then override error_exit. */
	cinfo.err = jpeg_std_error(&jerr.pub);
	jerr.pub.error_exit = my_error_exit;
	/* Establish the setjmp return context for my_error_exit to use. */
	if (setjmp(jerr.setjmp_buffer)) 
	{
		/* If we get here, the JPEG code has signaled an error.
		 * We need to clean up the JPEG object, close the input file, and return.
		 */
		jpeg_destroy_decompress(&cinfo);
		return 0;
	}

	jpeg_create_decompress(&cinfo);
    cinfo.src = (struct jpeg_source_mgr *) &src;
    src.pub.init_source = socket_init_source;
    src.pub.fill_input_buffer = socket_fill_input_buffer;
    src.pub.skip_input_data = socket_skip_input_data;
    src.pub.resync_to_restart = jpeg_resync_to_restart;
    src.pub.term_source = socket_term_source;
    src.state= state;
    src.pub.bytes_in_buffer = state->bufferlevel; /* forces fill_input_buffer on first read */
    src.pub.next_input_byte = &state->buffer[state->bufferoffset]; /* until buffer loaded */
    state->bufferlevel=0;
	(void) jpeg_read_header(&cinfo, TRUE);
	while(state->width<(cinfo.image_width+cinfo.scale_denom-1)/cinfo.scale_denom || 
		state->height<(cinfo.image_height+cinfo.scale_denom-1)/cinfo.scale_denom)
	{
		cinfo.scale_denom*=2;
	}
	if(cinfo.scale_denom>8)
	{
		fprintf(stderr, "Can't process image, scale %d\n",cinfo.scale_denom);
		jpeg_destroy_decompress(&cinfo);
		return 0;
    }
	// We're supposed to use this to get the output size before calling jpeg_start_decompress if we're allocating buffers
	(void) jpeg_calc_output_dimensions(&cinfo);
	imgwidth = cinfo.output_width ;
	imgheight = cinfo.output_height;
	fprintf(stderr,"Loading jpeg file %dx%dx%d\n",
	   cinfo.output_width, cinfo.output_height,cinfo.output_components);
	row_stride = cinfo.output_width * 3;//cinfo.output_components;
	if(state->output2!=NULL)
	{
		cinfo.out_color_space = JCS_YCbCr;
	}
	else
	{
		cinfo.out_color_space = JCS_RGB;
	}

	// Create the image scaler if we're doing a resize
	buffer = (*cinfo.mem->alloc_sarray)((j_common_ptr) &cinfo, JPOOL_IMAGE, row_stride, 1);

	// Don't enable this unless needed since it crash 1 component JPEG files...
	/*fprintf(stderr, "jpeg info %d %d %d %d %d %d %d %d %d %d %d\n",
	cinfo.jpeg_color_space, cinfo.num_components,
		cinfo.cur_comp_info[0]->h_samp_factor, cinfo.cur_comp_info[1]->h_samp_factor,
		cinfo.cur_comp_info[2]->h_samp_factor, cinfo.cur_comp_info[0]->v_samp_factor,
		cinfo.cur_comp_info[1]->v_samp_factor, cinfo.cur_comp_info[2]->v_samp_factor,
		((cinfo.output_width&(8/cinfo.scale_denom-1))==0),cinfo.output_width,
		cinfo.scale_denom);*/
	if(state->output2!=NULL && cinfo.jpeg_color_space==JCS_YCbCr && cinfo.num_components==3 &&
		cinfo.cur_comp_info[0]->h_samp_factor==2 && cinfo.cur_comp_info[1]->h_samp_factor==1 &&
		cinfo.cur_comp_info[2]->h_samp_factor==1 && 
		(cinfo.cur_comp_info[0]->v_samp_factor==1
		#ifdef HWJPEG
		||cinfo.cur_comp_info[0]->v_samp_factor==2
		#endif
		) &&
		cinfo.cur_comp_info[1]->v_samp_factor==1 && cinfo.cur_comp_info[2]->v_samp_factor==1 
		/* && ((cinfo.output_width&(8/cinfo.scale_denom-1))==0) &&
		((cinfo.output_height&(8/cinfo.scale_denom-1))==0)*/)
	{
		cinfo.do_block_smoothing=0;
		cinfo.raw_data_out=1;
	}
	
	{
		(void) jpeg_start_decompress(&cinfo);
	}
#ifdef HWJPEG
	if(cinfo.raw_data_out==1)
	{
		jdec=OpenJPEGDecoder(pgbus);
		fprintf(stderr,"Using jpeg decoder %X\n", jdec);
		if(jdec!=NULL && state->outputphys && state->outputphys2)
		{
			fprintf(stderr,"hw jpeg decoder to %X %X\n", state->outputphys,
				state->outputphys2);
			JPEGInitReadRaw(jdec, &cinfo, state->width, state->height,
				cinfo.scale_denom, (unsigned int) state->outputphys, 
				(unsigned int) state->outputphys2);
		}
	}
#endif
	{
		long long startframetime=get_timebase();
        sleepcount=0;
		int x = 0;
			
		if(cinfo.raw_data_out==1)
		{
			fprintf(stderr,"jpeg YUV 422 or 420\n");
			bufferraw[0]=&bufferraw2[0];
			bufferraw[1]=&bufferraw2[8];
			bufferraw[2]=&bufferraw2[16];
			while (cinfo.output_scanline < cinfo.output_height)
			{
#ifdef HWJPEG
				if(jdec!=NULL && state->outputphys && state->outputphys2)
				{
					JPEGreadRaw(jdec, &cinfo, 8);
				}
				else
#endif
				{
				for(i=0;i<8/cinfo.scale_denom;i++)
				{
				    bufferraw2[i]=&((unsigned char *)state->output)[(cinfo.output_scanline+i)*state->destwidth];
				    // Use the rgb current buffer for the last line
				    if(i==((8/cinfo.scale_denom)-1))
				    {
				        bufferraw2[8+i]=&((unsigned char*)buffer[0])[0];
				        bufferraw2[16+i]=&((unsigned char*)buffer[0])[state->destwidth/2];
				    }
				    else
				    {
						bufferraw2[8+i]=&((unsigned char *)state->output2)[(cinfo.output_scanline+i+1)*state->destwidth];
						bufferraw2[16+i]=&((unsigned char  *)state->output2)
							[(cinfo.output_scanline+i+1)*state->destwidth+state->destwidth/2];
				    }
				}
				jpeg_read_raw_data(&cinfo, bufferraw, 8/cinfo.scale_denom);
				{
					int pos=(cinfo.output_scanline-8/cinfo.scale_denom)*state->destwidth;
					for(i=0;i<8/cinfo.scale_denom;i++)
					{
						if(i==((8/cinfo.scale_denom)-1))
						{
							for (x = 0; x < cinfo.output_width/2; x++) 
							{
								((unsigned char *)state->output2)[pos+i*state->destwidth+x*2] = 
									((unsigned char *)buffer[0])[x];
								((unsigned char *)state->output2)[pos+i*state->destwidth+x*2+1] = 
									((unsigned char *)buffer[0])[state->destwidth/2+x];
							}
						}
						else
						{
							for (x = 0; x < cinfo.output_width/2; x++) 
							{
								((unsigned char *)state->output2)[pos+i*state->destwidth+x*2] = 
									((unsigned char *)state->output2)[pos+i*state->destwidth+state->destwidth+x];
								((unsigned char *)state->output2)[pos+i*state->destwidth+x*2+1] = 
									((unsigned char *)state->output2)[pos+i*state->destwidth+state->destwidth+state->destwidth/2+x];
							}
						}
					}
				}
				}
			}
		}
		else
		{
			fprintf(stderr,"jpeg default mode\n");
			while (cinfo.output_scanline < cinfo.output_height)
			{
				(void) jpeg_read_scanlines(&cinfo, buffer, 1);
				{
					if(state->output2!=NULL)
					{
						for (x = 0; x < cinfo.output_width; x++) 
						{
							int soffset = x*3;
							int doffset = (cinfo.output_scanline - 1)*state->destwidth + x;
							((unsigned char *)state->output)[doffset + 0] = ((unsigned char*)buffer[0])[soffset];
							((unsigned char *)state->output2)[doffset + 0] = ((unsigned char*)buffer[0])[soffset+ 1 + (x&1)];
						}
					}
					else
					{
						for (x = 0; x < cinfo.output_width; x++) 
						{
							int soffset = x*3;
							int doffset = (cinfo.output_scanline - 1)*state->destwidth*4 + x*4;
							((unsigned char *)state->output)[doffset + 3] = 0xFF;
							((unsigned char *)state->output)[doffset + 2] = ((unsigned char*)buffer[0])[soffset];
							((unsigned char *)state->output)[doffset + 1] = ((unsigned char*)buffer[0])[soffset + 1];
							((unsigned char *)state->output)[doffset + 0] = ((unsigned char*)buffer[0])[soffset + 2];
						}
					}
				}
			}
		}
		fprintf(stderr, "scanlines took %lld %lld\n",get_timebase()-startframetime, sleepcount);
	}
#ifdef HWJPEG
	if(jdec!=NULL)
	{
		fprintf(stderr, "Sent %d blocks, waiting for decoder\n", blockcount);
		while(JPEGIsDecodingMCU(jdec));
		CloseJPEGDecoder(jdec);
	}
#endif
	(void) jpeg_finish_decompress(&cinfo);
	jpeg_destroy_decompress(&cinfo);

	return 0;
}

#ifdef EM8634

int gifInputFunc(GifFileType *gifFile, GifByteType *gifData, int gifLen)
{
    return readData(gifFile->UserData, gifData, gifLen);
}

static int LoadGIF(MiniImageState *state)
{
    int	i, j, Size, Row, Col, Width, Height, ExtCode, Count;
    GifRecordType RecordType;
    GifByteType *Extension;
    GifRowType *ScreenBuffer;
    GifFileType *GifFile;
    GifColorType *ColorMapEntry;
    int ColorMapSize = 0;
    int InterlacedOffset[] = { 0, 4, 2, 1 }; 
    int InterlacedJumps[] = { 8, 8, 4, 2 };
	int transparentIndex = -1;
    ColorMapObject *ColorMap;
    int imgwidth=0, imgheight=0;

    if((GifFile = DGifOpen(state, gifInputFunc)) == NULL)
    {
        fprintf(stderr, "Failed DGifOpen\n");
        return 0;
    }

    fprintf(stderr, "Loading gif file %dx%d\n",
       GifFile->SWidth, GifFile->SHeight);

    if((ScreenBuffer = (GifRowType *)
        malloc(GifFile->SHeight * sizeof(GifRowType *))) == NULL)
    {
        DGifCloseFile(GifFile);
        return 0;
    }

    Size = GifFile->SWidth * sizeof(GifPixelType);/* Size in bytes one row.*/
    
    imgwidth = GifFile->SWidth;
    imgheight = GifFile->SHeight;

    if((ScreenBuffer[0] = (GifRowType) malloc(Size)) == NULL) /* First row. */
    {
        free(ScreenBuffer);
        DGifCloseFile(GifFile);
        return 0;
    }
    
    for (i = 0; i < GifFile->SWidth; i++)  /* Set its color to BackGround. */
    {
        ScreenBuffer[0][i] = GifFile->SBackGroundColor;
    }
    
    for (i = 1; i < GifFile->SHeight; i++) 
    {
        /* Allocate the other rows, and set their color to background too: */
        if ((ScreenBuffer[i] = (GifRowType) malloc(Size)) == NULL)
        {
            for(i = 0; i < GifFile->SHeight; i++)
            {
                if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
            }
            free(ScreenBuffer);
            DGifCloseFile(GifFile);
            return 0;
        }
        memcpy(ScreenBuffer[i], ScreenBuffer[0], Size);
    }

    do 
    {
        if (DGifGetRecordType(GifFile, &RecordType) == GIF_ERROR) 
        {
            for(i = 0; i < GifFile->SHeight; i++)
            {
                if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
            }
            free(ScreenBuffer);
            DGifCloseFile(GifFile);
            return 0;
        }
        switch (RecordType) 
        {
            case IMAGE_DESC_RECORD_TYPE:
                if (DGifGetImageDesc(GifFile) == GIF_ERROR) 
                {
                    for(i = 0; i < GifFile->SHeight; i++)
                    {
                        if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
                    }
                    free(ScreenBuffer);
                    DGifCloseFile(GifFile);
                    return 0;
                }
                Row = GifFile->Image.Top; /* Image Position relative to Screen. */
                Col = GifFile->Image.Left;
                Width = GifFile->Image.Width;
                Height = GifFile->Image.Height;
                if (GifFile->Image.Left + GifFile->Image.Width > GifFile->SWidth ||
                    GifFile->Image.Top + GifFile->Image.Height > GifFile->SHeight) 
                {
                    for(i = 0; i < GifFile->SHeight; i++)
                    {
                        if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
                    }
                    free(ScreenBuffer);
                    DGifCloseFile(GifFile);
                    return 0;
                }
                if (GifFile->Image.Interlace) 
                {
                    /* Need to perform 4 passes on the images: */
                    for (Count = i = 0; i < 4; i++)
                        for (j = Row + InterlacedOffset[i]; j < Row + Height;
                            j += InterlacedJumps[i]) 
                        {
                            if (DGifGetLine(GifFile, &ScreenBuffer[j][Col],
                                Width) == GIF_ERROR) 
                            {
                                for(i = 0; i < GifFile->SHeight; i++)
                                {
                                    if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
                                }
                                free(ScreenBuffer);
                                DGifCloseFile(GifFile);
                                return 0;
                            }
                        }
                }
                else 
                {
                    for (i = 0; i < Height; i++) 
                    {
                        if (DGifGetLine(GifFile, &ScreenBuffer[Row++][Col],
                            Width) == GIF_ERROR) 
                        {
                            for(i = 0; i < GifFile->SHeight; i++)
                            {
                                if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
                            }
                            free(ScreenBuffer);
                            DGifCloseFile(GifFile);
                            return 0;
                        }
                    }
                }
                break;
	    case EXTENSION_RECORD_TYPE:
                /* Skip any extension blocks in file: */
                if (DGifGetExtension(GifFile, &ExtCode, &Extension) == GIF_ERROR) 
                {
                    for(i = 0; i < GifFile->SHeight; i++)
                    {
                        if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
                    }
                    free(ScreenBuffer);
                    DGifCloseFile(GifFile);
                    return 0;
                }
                while (Extension != NULL) 
                {
					// Check this Extension for the transparency index
					if (ExtCode == 0xF9 && Extension[0] == 0x4 &&
						((Extension[1] & 0x1) == 0x1))
					{
						transparentIndex = Extension[4];
					}

                    if (DGifGetExtensionNext(GifFile, &Extension) == GIF_ERROR)
                    {
                        for(i = 0; i < GifFile->SHeight; i++)
                        {
                            if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
                        }
                        free(ScreenBuffer);
                        DGifCloseFile(GifFile);
                        return 0;
                    }
                }
                break;
            case TERMINATE_RECORD_TYPE:
                break;
            default:    /* Should be traps by DGifGetRecordType. */
                break;
        }
    }
    while (RecordType != TERMINATE_RECORD_TYPE);
        
    ColorMap = (GifFile->Image.ColorMap
        ? GifFile->Image.ColorMap
        : GifFile->SColorMap);
    ColorMapSize = ColorMap->ColorCount;
    

	{
		for (i = 0; i < GifFile->SHeight; i++) 
		{
			for (j = 0; j < GifFile->SWidth; j++) 
			{
				int idx = ScreenBuffer[i][j];
				int j4 = i*imgwidth*4 + j*4;
				if (idx == transparentIndex)
					((unsigned char *)state->output)[j4] = ((unsigned char *)state->output)[j4+1] = 
						((unsigned char *)state->output)[j4+2] = ((unsigned char *)state->output)[j4+3] = 0;
				else
				{
					ColorMapEntry = &ColorMap->Colors[idx];
					((unsigned char *)state->output)[j4 + 3] = 0xFF;
					((unsigned char *)state->output)[j4 + 2] = ColorMapEntry->Red;
					((unsigned char *)state->output)[j4 + 1] = ColorMapEntry->Green;
					((unsigned char *)state->output)[j4 + 0] = ColorMapEntry->Blue;
				}
				ColorMapEntry = &ColorMap->Colors[ScreenBuffer[i][j]];
			}
		}
	}

    for(i = 0; i < GifFile->SHeight; i++)
    {
        if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
    }
    free(ScreenBuffer);
    DGifCloseFile(GifFile);
    return 1;
}

static int LoadTIFF(const char* filename, MiniImageState *state)
{
    int fp;
    TIFF* tifFile;
    fp=open(filename, O_RDONLY);
    if(fp<0) return 0;
	tifFile = TIFFFdOpen(fp, filename, "r");
    if(!tifFile)
    {
        return 0;
    }
	int width, height;
	TIFFGetField(tifFile, TIFFTAG_IMAGEWIDTH, &width);
	TIFFGetField(tifFile, TIFFTAG_IMAGELENGTH, &height);

    fprintf(stderr, "image width %d height %d\n",width, height);
	int rowsPerStrip=-1;
	if(!TIFFGetField(tifFile, TIFFTAG_ROWSPERSTRIP, &rowsPerStrip))
	{
		fprintf(stderr, "Couldn't get rowsPerStrip\n");
		TIFFClose(tifFile);
		return 0;
	}
		if (rowsPerStrip == 0)
		rowsPerStrip = height;
	// Create the image scaler if we're doing a resize
	int y;
	int r;
	if(rowsPerStrip * width * 4 > 4*1024*1024)
	{
        fprintf(stderr, "Image too large for rowsPerStrip 0 mode\n");
        TIFFClose(tifFile);
        return 1;
	}
	if (state->width != width || state->height != height)
	{
		struct SwsContext *sws = sws_getContext(width, height, PIX_FMT_RGB32, 
			state->width, state->height, PIX_FMT_RGB32, 0x0002, NULL);
		if (!sws)
		{
			TIFFClose(tifFile);
			return 0;
		}
		uint32* buffer = (uint32*) _TIFFmalloc(rowsPerStrip * width * 4);
		for(y = 0; y < height; y+=rowsPerStrip)
		{
			fprintf(stderr, "Scaling image at %d\n",y);
			TIFFReadRGBAStrip(tifFile, y, buffer);
			int currRows = (y + rowsPerStrip > height) ? (height - y) : rowsPerStrip;
			for (r = 0; r < currRows; r++)
				sws_scale(sws, ((unsigned char* )buffer) + (rowsPerStrip - r - 1)*width*4, 
					width*4, y + r, 1, ((unsigned char *)state->output), state->width*4);
		}
		_TIFFfree(buffer);
		sws_freeContext(sws);
	}
	else if (rowsPerStrip > 1)
	{
		uint32* buffer = (uint32*) _TIFFmalloc(rowsPerStrip * width * 4);
		for(y = 0; y < height; y+=rowsPerStrip)
		{
			fprintf(stderr, "Reading image at %d\n",y);
			int currRows = (y + rowsPerStrip > height) ? (height - y) : rowsPerStrip;
			TIFFReadRGBAStrip(tifFile, y, (uint32*)buffer);
			// Reverse vertical order
			for (r = 0; r < currRows; r++)
				memcpy(((unsigned char *)state->output) + (y + r)*state->width*4, 
				((unsigned char*)buffer) + ((rowsPerStrip - r - 1)*state->width*4), state->width*4);
		}
		_TIFFfree(buffer);
	}
	else
	{
		for(y = 0; y < height; y+=rowsPerStrip)
		{
			fprintf(stderr, "Reading image at %d\n",y);
			unsigned char* buffer = ((unsigned char *)state->output) + y*state->width*4;
			TIFFReadRGBAStrip(tifFile, y, (uint32*)buffer);
		}
	}

	// Now go through and premultiply the alpha channel data & change from RGBA to ARGB
	// Don't premultiply on 8634
	int x;
	int count=state->width*state->height;
	// TODO: speed that up if tiff is used more often
	fprintf(stderr, "Reordering image\n");
	for (x = 0; x < count; x++)
	{
		int base = x*4;
		unsigned char tmp = ((unsigned char *)state->output)[base + 0];
		((unsigned char *)state->output)[base + 0]=((unsigned char *)state->output)[base + 2];
		((unsigned char *)state->output)[base + 2]=tmp;
	}
	fprintf(stderr, "Done reordering\n");

	TIFFClose(tifFile);
    return 1;
}

#endif

int loadMiniImage(int sd, unsigned char *buffer, int bufferoffset, int bufferlevel, int buffersize, 
    void *output, void *output2, int width, int height, int datalen, int destwidth,
    void *outputphys, void *outputphys2, int isfile)
{
    MiniImageState state;
    unsigned char *header=&buffer[bufferoffset];
    int headerlen=bufferlevel;
    int dataleft=datalen-bufferlevel;
    state.sd=sd;
    state.buffer=buffer;
    state.bufferoffset=bufferoffset;
    state.bufferlevel=bufferlevel;
    state.buffersize=buffersize;
    state.output=output;
    state.output2=output2;
    state.width=width;
    state.height=height;
    state.datalen=datalen;
    state.usedlen=bufferlevel;
    state.destwidth=destwidth;
    state.outputphys=outputphys;
    state.outputphys2=outputphys2;
    state.isfile=isfile;

#ifdef HWJPEG
    waitcount=0;
    blockcount=0;
#endif

    // fprintf(stderr, "header %02X %02X %02X %02X\n",header[0],header[1],header[2],header[3]);
    // Figure out which type of image we have
    if(headerlen<8)
    {
        discardData(&state,state.datalen-state.usedlen);
        return 0;
    }

    if (header[0] == 0xFF && header[1] == 0xD8 && header[2] == 0xFF)
    {
        // JPEG file
        // fprintf(stderr, "Loading JPG file\n");
/*        #ifdef EM8654
        extern int LoadVideoJPEG(MiniImageState *state);
        if(outputphys2!=NULL && LoadVideoJPEG(&state)==RM_OK)
        {
            // 
        }
        else
        #endif */
        {
            LoadJPEG(&state);
        }
    }
    else if (header[0] == 'G' && header[1] == 'I' && 
        header[2] == 'F' && header[3] == '8')
    {
        // GIF file
        fprintf(stderr, "GIF header\n");
#ifdef EM8634
        LoadGIF(&state);
#endif
    }
    else if (header[0] == 0x89 && header[1] == 0x50 && header[2] == 0x4E &&
        header[3] == 0x47 && header[4] == 0x0D && header[5] == 0x0A &&
        header[6] == 0x1A && header[7] == 0x0A)
    {
        // PNG file
        // fprintf(stderr, "Loading png file\n");
        LoadPNG(&state);
    }
    else if ((header[0] == 0x49 && header[1] == 0x49 && header[2] == 0x2A && header[3] == 0) ||
        (header[0] == 0x4D && header[1] == 0x4D && header[2] == 0 && header[3] == 0x2A))
    {
        fprintf(stderr, "TIFF header\n");
#ifdef EM8634
        {
            int filelen=0;
            unsigned char databuf[512];
            FILE *imagefile=fopen("/tmp/image.bin","wb");
            while(filelen<datalen)
            {
                int blocklen=512;
                if((filelen+blocklen)>datalen) blocklen=datalen-filelen;
                readData(&state, databuf, blocklen);
                fwrite(databuf, blocklen, 1, imagefile);
                filelen+=blocklen;
            }
            fclose(imagefile);
        }
        LoadTIFF("/tmp/image.bin", &state);
        unlink("/tmp/image.bin");
#endif
    }
    //fprintf(stderr, "Done loading\n");
    if(state.datalen-state.usedlen != 0)
    {
        fprintf(stderr, "Discarding loadMiniImage extra data %d (%d %d)\n",
            state.datalen-state.usedlen, state.datalen,state.usedlen);
        discardData(&state,state.datalen-state.usedlen);
    }
}
