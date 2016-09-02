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
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include "imageload.h"
#include "swscale.h"
#include "../../codecs/libpng/png.h"
#include "../../codecs/giflib/lib/gif_lib.h"
#include "../../codecs/jpeg-6b/jpeglib.h"
#include "../../codecs/tiff/libtiff/tiffio.h"

static int resizew;
static int resizeh;
static int resizedx;
static int resizedy;
static int resizeposx;
static int resizeposy;

int LoadPNGDimensions(FILE* fp, int *imgwidth, int *imgheight)
{
    char header[8];
    png_uint_32 width, height;
    int bit_depth, color_type, interlace_type;
    if(!fp) return 1;
    if(fread(header, 8, 1, fp)!=1) return 1;
    int is_png = !png_sig_cmp((png_bytep)header, 0, 8);
    if(!is_png) return 1;

    png_structp png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, 
        NULL, NULL, NULL);
    if(!png_ptr)
        return 1;

    png_infop info_ptr = png_create_info_struct(png_ptr);
    if(!info_ptr) 
    {
        png_destroy_read_struct(&png_ptr,
           (png_infopp)NULL, (png_infopp)NULL);
        return 1;
    }

    if(setjmp(png_jmpbuf(png_ptr)))
    {
        printf("Encountered an error reading the png...\n");
        fflush(stdout);
        png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);
        return 1;
    }
    
    png_init_io(png_ptr, fp);
    png_set_sig_bytes(png_ptr, 8);
    
    png_read_info(png_ptr, info_ptr);
    
    png_get_IHDR(png_ptr, info_ptr, &width, &height, &bit_depth, &color_type,
       &interlace_type, int_p_NULL, int_p_NULL);

    png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);

	if (imgwidth)
		*imgwidth = width;
	if (imgheight)
		*imgheight = height;
    return 0;
}

// Based on example from png library
RawImage_t* LoadPNG(FILE* fp, int imgwidth, int imgheight)
{
    RawImage_t *newimage;
    char header[8];
    png_uint_32 width, height;
    int bit_depth, color_type, interlace_type;
    int number_passes,pass,y;
    if(!fp) return 0;
    if(fread(header, 8, 1, fp)!=1) return 0;
    int is_png = !png_sig_cmp((png_bytep)header, 0, 8);
    if(!is_png) return 0;

    png_structp png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, 
        NULL, NULL, NULL);
    if(!png_ptr)
        return 0;

    png_infop info_ptr = png_create_info_struct(png_ptr);
    if(!info_ptr) 
    {
        png_destroy_read_struct(&png_ptr,
           (png_infopp)NULL, (png_infopp)NULL);
        return 0;
    }

    if(setjmp(png_jmpbuf(png_ptr)))
    {
        printf("Encountered an error reading the png...\n");
        fflush(stdout);
        png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);
        return 0;
    }
    
    png_init_io(png_ptr, fp);
    png_set_sig_bytes(png_ptr, 8);
    
    png_read_info(png_ptr, info_ptr);
    
    png_get_IHDR(png_ptr, info_ptr, &width, &height, &bit_depth, &color_type,
       &interlace_type, int_p_NULL, int_p_NULL);

    if (imgwidth == 0 || imgheight == 0) {
      imgwidth = width;
      imgheight = height;
    }

    newimage=(RawImage_t *) malloc(sizeof(RawImage_t));
    if(newimage==NULL)
    {
        printf("Couldn't allocate RawImage_t for new image\n");
        fflush(stdout);
        png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);
        return 0;
    }
    memset(newimage, 0, sizeof(RawImage_t));
    // TODO: add checks for odd width
    newimage->hasAlpha=1; // jpeg don't have alpha
    newimage->pPlane = (unsigned char*) malloc(imgwidth * imgheight * 4);
    newimage->uWidth = imgwidth;
    newimage->uHeight = imgheight;
    newimage->uBytePerLine = imgwidth*4;

    if(!newimage->pPlane)
    {
        printf("Couldn't create surface for new image\n");
        fflush(stdout);
        png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);
        free(newimage);
        return 0;
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
    if(color_type == PNG_COLOR_TYPE_GRAY ||
       color_type == PNG_COLOR_TYPE_GRAY_ALPHA)
      png_set_gray_to_rgb(png_ptr);
    /* flip the RGB pixels to BGR (or RGBA to BGRA) */
//    if(color_type & PNG_COLOR_MASK_COLOR)
//        png_set_bgr(png_ptr);

    /* swap the RGBA or GA data to ARGB or AG (or BGRA to ABGR) */
    png_set_swap_alpha(png_ptr);
    png_set_filler(png_ptr, 0xff, PNG_FILLER_BEFORE);
    number_passes = png_set_interlace_handling(png_ptr);
    png_read_update_info(png_ptr, info_ptr);
    
	// Create the image scaler if we're doing a resize
	if (imgwidth != width || imgheight != height)
	{
		struct SwsContext *sws = sws_getContext(width, height, PIX_FMT_RGB32, imgwidth, imgheight, PIX_FMT_RGB32, 0x0002, NULL);
		if (!sws)
		{
	        png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);
		    free(newimage->pPlane);
			free(newimage);
			return 0;
		}
	    unsigned char *buffer = (unsigned char *) malloc(png_get_rowbytes(png_ptr,info_ptr));
		for(pass = 0; pass < number_passes; pass++)
		{
			for(y = 0; y < height; y++)
			{
				png_read_rows(png_ptr, &buffer, png_bytepp_NULL, 1);
				sws_scale(sws, buffer, width*4, y, 1, newimage->pPlane, imgwidth*4);
			}
		}
	    free(buffer);
		sws_freeContext(sws);
	}
	else
	{
		for(pass = 0; pass < number_passes; pass++)
		{
			for(y = 0; y < height; y++)
			{
				unsigned char* buffer = newimage->pPlane + y*imgwidth*4;
				png_read_rows(png_ptr, &buffer, png_bytepp_NULL, 1);
			}
		}
	}

	// Now go through and premultiply the alpha channel data
	int x;
	for (x = 0; x < imgwidth*imgheight; x++)
	{
		int base = x*4;
		int alpha = newimage->pPlane[base];
		if (alpha < 0xFF)
		{
			newimage->pPlane[base + 1] = newimage->pPlane[base + 1] * alpha / 255;
			newimage->pPlane[base + 2] = newimage->pPlane[base + 2] * alpha / 255;
			newimage->pPlane[base + 3] = newimage->pPlane[base + 3] * alpha / 255;
		}
	}
    
    /* read rest of file, and get additional chunks in info_ptr - REQUIRED */
    png_read_end(png_ptr, info_ptr);

    /* clean up after the read, and free any memory allocated - REQUIRED */
    png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);

    /* that's it */
    return newimage;
}

// Based on example from png library
int SavePNG(RawImage_t* image, FILE* outfile)
{
    if(!outfile) return 1;

    png_structp png_ptr = png_create_write_struct(PNG_LIBPNG_VER_STRING, 
        NULL, NULL, NULL);
    if(!png_ptr)
        return 1;

    png_infop info_ptr = png_create_info_struct(png_ptr);
    if(!info_ptr) 
    {
        png_destroy_write_struct(&png_ptr,
           (png_infopp)NULL);
        return 1;
    }

    if(setjmp(png_jmpbuf(png_ptr)))
    {
        printf("Encountered an error writing the png...\n");
        fflush(stdout);
        png_destroy_write_struct(&png_ptr, &info_ptr);
        return 1;
    }
    
    png_init_io(png_ptr, outfile);
    
	png_set_IHDR(png_ptr, info_ptr, image->uWidth, image->uHeight, 8, 
		(image->ubpp == 24) ? PNG_COLOR_TYPE_RGB : PNG_COLOR_TYPE_RGB_ALPHA,
       PNG_INTERLACE_NONE, PNG_COMPRESSION_TYPE_DEFAULT, PNG_FILTER_TYPE_DEFAULT);

	png_bytep* row_pointers = png_malloc(png_ptr,
		image->uHeight*png_sizeof(png_bytep));
	int i;
	for (i=0; i<image->uHeight; i++)
		row_pointers[i]=image->pPlane + i*image->uBytePerLine;
	png_set_rows(png_ptr, info_ptr, row_pointers);

	png_write_png(png_ptr, info_ptr, PNG_TRANSFORM_IDENTITY | PNG_TRANSFORM_SWAP_ALPHA, NULL);

    /* clean up after the write, and free any memory allocated - REQUIRED */
    png_destroy_write_struct(&png_ptr, &info_ptr);

    /* that's it */
    return 0;
}

int LoadGIFDimensions(int fp, int *imgwidth, int *imgheight)
{
    GifFileType *GifFile;
    if((GifFile = DGifOpenFileHandle(fp)) == NULL)
    {
        return 1;
    }
    if (imgwidth)
		*imgwidth = GifFile->SWidth;
	if (imgheight)
		*imgheight = GifFile->SHeight;
    DGifCloseFile(GifFile);
	return 0;
}

// Based on example for libungif
RawImage_t* LoadGIF(int fp, int imgwidth, int imgheight)
{
    RawImage_t *newimage;
    
    int	i, j, Size, Row, Col, Width, Height, ExtCode, Count;
    GifRecordType RecordType;
    GifByteType *Extension;
    GifRowType *ScreenBuffer;
    GifFileType *GifFile;
    int ColorMapSize = 0;
    int InterlacedOffset[] = { 0, 4, 2, 1 }; 
    int InterlacedJumps[] = { 8, 8, 4, 2 };
	int transparentIndex = -1;
    ColorMapObject *ColorMap;

    if((GifFile = DGifOpenFileHandle(fp)) == NULL)
    {
        return 0;
    }
    
    printf("Loading gif file %dx%d\n",
       GifFile->SWidth, GifFile->SHeight);
    
    if((ScreenBuffer = (GifRowType *)
        malloc(GifFile->SHeight * sizeof(GifRowType *))) == NULL)
    {
        DGifCloseFile(GifFile);
        return 0;
    }

    Size = GifFile->SWidth * sizeof(GifPixelType);/* Size in bytes one row.*/
    
	if (imgwidth == 0 || imgheight == 0)
	{
		imgwidth = GifFile->SWidth;
		imgheight = GifFile->SHeight;
	}

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
    
    GifColorType *ColorMapEntry;
    
    newimage=(RawImage_t *) malloc(sizeof(RawImage_t));
    if(newimage==NULL)
    {
        printf("Couldn't allocate RawImage_t for new image\n");
        fflush(stdout);
        for(i = 0; i < GifFile->SHeight; i++)
        {
            if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
        }
        free(ScreenBuffer);
        DGifCloseFile(GifFile);
        return 0;
    }
	memset(newimage, 0, sizeof(RawImage_t));
    // TODO: add checks for odd width
    newimage->hasAlpha=1; // jpeg don't have alpha
	newimage->pPlane = (unsigned char*) malloc(imgwidth * imgheight * 4);
	newimage->uWidth = imgwidth;
	newimage->uHeight = imgheight;
	newimage->uBytePerLine = imgwidth*4;
   
    if(!newimage->pPlane)
    {
        printf("Couldn't create surface for new image\n");
        fflush(stdout);
        for(i = 0; i < GifFile->SHeight; i++)
        {
            if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
        }
        free(ScreenBuffer);
        DGifCloseFile(GifFile);
        free(newimage);
        return 0;
    }

	// Create the image scaler if we're doing a resize
	if (imgwidth != GifFile->SWidth || imgheight != GifFile->SHeight)
	{
		struct SwsContext *sws = sws_getContext(GifFile->SWidth, GifFile->SHeight, PIX_FMT_RGB32, imgwidth, imgheight, PIX_FMT_RGB32, 0x0002, NULL);
		if (!sws)
		{
	        for(i = 0; i < GifFile->SHeight; i++)
			{
				if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
			}
			free(ScreenBuffer);
			DGifCloseFile(GifFile);
			free(newimage->pPlane);
			free(newimage);
			return 0;
		}
	    unsigned char *buffer=malloc(GifFile->SWidth*4);
		for (i = 0; i < GifFile->SHeight; i++) 
		{
			for (j = 0; j < GifFile->SWidth; j++) 
			{
				int idx = ScreenBuffer[i][j];
				int j4 = j*4;
				if (idx == transparentIndex)
					buffer[j4] = buffer[j4+1] = buffer[j4+2] = buffer[j4+3] = 0;
				else
				{
					ColorMapEntry = &ColorMap->Colors[idx];
					buffer[j4] = 0xFF;
					buffer[j4+1] = ColorMapEntry->Red;
					buffer[j4+2] = ColorMapEntry->Green;
					buffer[j4+3] = ColorMapEntry->Blue;
				}
			}
			sws_scale(sws, buffer, GifFile->SWidth*4, i, 1, newimage->pPlane, imgwidth*4);
		}
	    free(buffer);
		sws_freeContext(sws);
	}
	else
	{
		for (i = 0; i < GifFile->SHeight; i++) 
		{
			for (j = 0; j < GifFile->SWidth; j++) 
			{
				int idx = ScreenBuffer[i][j];
				int j4 = i*imgwidth*4 + j*4;
				if (idx == transparentIndex)
					newimage->pPlane[j4] = newimage->pPlane[j4+1] = newimage->pPlane[j4+2] = newimage->pPlane[j4+3] = 0;
				else
				{
					ColorMapEntry = &ColorMap->Colors[idx];
					newimage->pPlane[j4] = 0xFF;
					newimage->pPlane[j4 + 1] = ColorMapEntry->Red;
					newimage->pPlane[j4 + 2] = ColorMapEntry->Green;
					newimage->pPlane[j4 + 3] = ColorMapEntry->Blue;
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
    
    return newimage;
}

// IMPORTANT NOTE: WE MUST UPDATE THE JPEG ERROR HANDLER BECAUSE IT CALLS exit() IF A FATAL ERROR OCCURS
/*
 * ERROR HANDLING:
 *
 * The JPEG library's standard error handler (jerror.c) is divided into
 * several "methods" which you can override individually.  This lets you
 * adjust the behavior without duplicating a lot of code, which you might
 * have to update with each future release.
 *
 * Our example here shows how to override the "error_exit" method so that
 * control is returned to the library's caller when a fatal error occurs,
 * rather than calling exit() as the standard error_exit method does.
 *
 * We use C's setjmp/longjmp facility to return control.  This means that the
 * routine which calls the JPEG library must first execute a setjmp() call to
 * establish the return point.  We want the replacement error_exit to do a
 * longjmp().  But we need to make the setjmp buffer accessible to the
 * error_exit routine.  To do this, we make a private extension of the
 * standard JPEG error handler object.  (If we were using C++, we'd say we
 * were making a subclass of the regular error handler.)
 *
 * Here's the extended error handler struct:
 */

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

int LoadJPEGDimensions(FILE* fp, int *imgwidth, int *imgheight)
{
    struct jpeg_decompress_struct cinfo;
	struct my_error_mgr jerr;
    JSAMPARRAY buffer;
    int row_stride;
    if(fp==NULL) return 0;
    
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
		return 1;
	}

	jpeg_create_decompress(&cinfo);
	jpeg_stdio_src(&cinfo, fp);
	(void) jpeg_read_header(&cinfo, TRUE);
	(void) jpeg_calc_output_dimensions(&cinfo);
	if (imgwidth)
		*imgwidth = cinfo.output_width;
	if (imgheight)
		*imgheight = cinfo.output_height;
	jpeg_destroy_decompress(&cinfo);
	return 0;
}
	
// Based on example from jpeg library
RawImage_t* LoadJPEG(FILE* infile, int imgwidth, int imgheight, int bpp, int rotation)
{
    RawImage_t *newimage;
    struct jpeg_decompress_struct cinfo;
	struct my_error_mgr jerr;
    JSAMPARRAY buffer;
    int row_stride;
    if(infile==NULL) return 0;
    
	// Allocate before the error handler so we can deallocate if there's an error
	newimage=(RawImage_t *) malloc(sizeof(RawImage_t));
	if(newimage==NULL)
	{
		printf("Couldn't allocate RawImage_t for new image\n");
		fflush(stdout);
		return 0;
	}
	newimage->pPlane = 0;

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
		if (newimage->pPlane)
			free(newimage->pPlane);
		free(newimage);
		return 0;
	}

	jpeg_create_decompress(&cinfo);
	jpeg_stdio_src(&cinfo, infile);
	(void) jpeg_read_header(&cinfo, TRUE);
	// We're supposed to use this to get the output size before calling jpeg_start_decompress if we're allocating buffers
	(void) jpeg_calc_output_dimensions(&cinfo);
	printf("Loading jpeg file %dx%dx%d\n",
	   cinfo.output_width, cinfo.output_height,cinfo.output_components);
	if (imgwidth == 0 || imgheight == 0)
	{
		imgwidth = cinfo.output_width;
		imgheight = cinfo.output_height;
	}
	else if (rotation != 0)
	{
		int tmp = imgwidth;
		imgwidth = imgheight;
		imgheight = tmp;
	}

	memset(newimage, 0xFF, sizeof(RawImage_t)); // so we don't have transparency problems
    newimage->hasAlpha=0; // jpeg don't have alpha, although we do use a byte for it
	newimage->pPlane = (unsigned char*) malloc(imgwidth * imgheight * (bpp == 32 ? 4 : 3));
	if (rotation != 0)
	{
		newimage->uWidth = imgheight;
		newimage->uHeight = imgwidth;
	}
	else
	{
		newimage->uWidth = imgwidth;
		newimage->uHeight = imgheight;
	}
	newimage->uBytePerLine = newimage->uWidth*(bpp == 32 ? 4 : 3);
	if(!newimage->pPlane)
	{
		printf("Couldn't create surface for new image\n");
		fflush(stdout);
		jpeg_destroy_decompress(&cinfo);
		free(newimage);
		return 0;
	}
          
	row_stride = cinfo.output_width * 3;//cinfo.output_components;
	cinfo.out_color_space = bpp == 16 ? JCS_YCbCr : JCS_RGB;

	// Create the image scaler if we're doing a resize
	buffer = (*cinfo.mem->alloc_sarray)((j_common_ptr) &cinfo, JPOOL_IMAGE, row_stride, 1);
	(void) jpeg_start_decompress(&cinfo);
	if (imgwidth != cinfo.output_width || imgheight != cinfo.output_height)
	{
		struct SwsContext *sws = sws_getContext(cinfo.output_width, cinfo.output_height, 
			PIX_FMT_RGB24,
			imgwidth, imgheight, (bpp == 32 ? PIX_FMT_RGB32 : PIX_FMT_RGB24), 0x0002, NULL);
		if (!sws)
		{
			jpeg_destroy_decompress(&cinfo);
			free(newimage->pPlane);
			free(newimage);
			return 0;
		}
		while (cinfo.output_scanline < cinfo.output_height)
		{
			(void) jpeg_read_scanlines(&cinfo, buffer, 1);
			sws_scale(sws, buffer[0], row_stride, cinfo.output_scanline - 1, 1, 
				newimage->pPlane, imgwidth*(bpp == 32 ? 4 : 3));
		}
		sws_freeContext(sws);
	}
	else
	{
		int x = 0;
		while (cinfo.output_scanline < cinfo.output_height)
		{
			(void) jpeg_read_scanlines(&cinfo, buffer, 1);
			if (bpp != 32)
				memcpy(newimage->pPlane + (cinfo.output_scanline - 1)*row_stride, buffer[0], row_stride);
			else
			{
				if (rotation == 0)
				{
					for (x = 0; x < cinfo.output_width; x++) 
					{
						int soffset = x*3;
						int doffset = (cinfo.output_scanline - 1)*imgwidth*4 + x*4;
						newimage->pPlane[doffset] = 0xFF;
						newimage->pPlane[doffset + 1] = ((unsigned char*)buffer[0])[soffset];
						newimage->pPlane[doffset + 2] = ((unsigned char*)buffer[0])[soffset + 1];
						newimage->pPlane[doffset + 3] = ((unsigned char*)buffer[0])[soffset + 2];
					}
				}
				else if (rotation == 90)
				{
					for (x = 0; x < cinfo.output_width; x++) 
					{
						int soffset = x*3;
						int doffset = x*imgheight*4 + (imgheight - cinfo.output_scanline)*4;
						newimage->pPlane[doffset] = 0xFF;
						newimage->pPlane[doffset + 1] = ((unsigned char*)buffer[0])[soffset];
						newimage->pPlane[doffset + 2] = ((unsigned char*)buffer[0])[soffset + 1];
						newimage->pPlane[doffset + 3] = ((unsigned char*)buffer[0])[soffset + 2];
					}
				}
				else // rotation == -90 or 270
				{
					for (x = 0; x < cinfo.output_width; x++) 
					{
						int soffset = x*3;
						int doffset = (imgwidth - x - 1)*imgheight*4 + (cinfo.output_scanline - 1)*4;
						newimage->pPlane[doffset] = 0xFF;
						newimage->pPlane[doffset + 1] = ((unsigned char*)buffer[0])[soffset];
						newimage->pPlane[doffset + 2] = ((unsigned char*)buffer[0])[soffset + 1];
						newimage->pPlane[doffset + 3] = ((unsigned char*)buffer[0])[soffset + 2];
					}
				}
			}
		}
		if (bpp == 32)
			rotation = 0; // because we already did the rotation
	}

	if (bpp == 16)
	{
		// Do the YUV -> YUY2 colorspace conversion
		int x=0, y=0;
		unsigned char* newPlane = (unsigned char*) malloc(imgwidth * imgheight * 2);
		int planarOffset = imgwidth*imgheight;
		if (rotation == 0)
		{
			for (y = 0; y < imgheight; y++)
			{
				int newyoff = y * imgwidth;
				int oldyoff = y * imgwidth * 3;
				for (x = 0; x < imgwidth; x+=2)
				{
					int x3 = x*3 + oldyoff;
					int newoff = newyoff + x;
					newPlane[newoff] = newimage->pPlane[x3]; // copy Y0
					newPlane[newoff + planarOffset] = newimage->pPlane[x3 + 1]; // copy U
					if (x + 1 < imgwidth)
					{
						newPlane[newoff + 1] = newimage->pPlane[x3 + 3]; // copy Y1
						newPlane[newoff + planarOffset + 1] = newimage->pPlane[x3 + 2]; // copy V
					}
				}
			}
		}
		else if (rotation == 90)
		{
			int imgw3 = imgwidth*3;
			for (x = 0; x < imgwidth; x++)
			{
				int ximgh = x*imgheight;
				int x3 = x*3;
				for (y = 0; y < imgheight; y+=2)
				{
					int newoff = ximgh + y;
					int oldoff = x3 + (imgheight - y - 1)*imgw3;
					newPlane[newoff] = newimage->pPlane[oldoff]; // copy Y0
					newPlane[newoff + planarOffset] = newimage->pPlane[oldoff + 1]; // copy U
					if (y + 1 < imgheight)
					{
						newPlane[newoff + 1] = newimage->pPlane[oldoff - imgw3]; // copy Y1
						newPlane[newoff + planarOffset + 1] = newimage->pPlane[oldoff + 2]; // copy V
					}
				}
			}
		}
		else // rotation == -90 or 270
		{
			int imgw3 = imgwidth*3;
			for (x = 0; x < imgwidth; x++)
			{
				int ximgh = x*imgheight;
				int xmx13 = (imgwidth - x - 1)*3;
				for (y = 0; y < imgheight; y+=2)
				{
					int newoff = ximgh + y;
					int oldoff = xmx13 + y*imgw3;
					newPlane[newoff] = newimage->pPlane[oldoff]; // copy Y0
					newPlane[newoff + planarOffset] = newimage->pPlane[oldoff + 1]; // copy U
					if (y + 1 < imgheight)
					{
						newPlane[newoff + 1] = newimage->pPlane[oldoff + imgw3]; // copy Y1
						newPlane[newoff + planarOffset + 1] = newimage->pPlane[oldoff + 2]; // copy V
					}
				}
			}
		}
		free(newimage->pPlane);
		newimage->pPlane = newPlane;
		newimage->uBytePerLine = 2 * newimage->uWidth;
	}
	else if (rotation != 0 && bpp == 32)
	{
		// We need to rotate the pixel data appropriately, we only do rotations when its
		// 32bpp so we can optimize based on that
		int x=0, y=0;
		uint32* newPlane = (uint32*) malloc(imgwidth * imgheight * 4);
		uint32* oldPlane = (uint32*) newimage->pPlane;
		if (rotation == 90)
		{
			for (x = 0; x < imgwidth; x++)
			{
				for (y = 0; y < imgheight; y++)
				{
					newPlane[(imgheight - y - 1) + x*imgheight] = oldPlane[y*imgwidth + x];
				}
			}
		}
		else
		{
			for (x = 0; x < imgwidth; x++)
			{
				for (y = 0; y < imgheight; y++)
				{
					newPlane[y + (imgwidth - x - 1)*imgheight] = oldPlane[y*imgwidth + x];
				}
			}
		}
		free(newimage->pPlane);
		newimage->pPlane = (unsigned char*) newPlane;
	}
    
	(void) jpeg_finish_decompress(&cinfo);
	jpeg_destroy_decompress(&cinfo);

	return newimage;
}

int SaveJPEG(RawImage_t* image, FILE* outfile)
{
	printf("Compressing %dx%d image to JPEG file\r\n", image->uWidth, image->uHeight);
	struct jpeg_compress_struct cinfo;
	struct my_error_mgr jerr;
	/* We set up the normal JPEG error routines, then override error_exit. */
	cinfo.err = jpeg_std_error(&jerr.pub);
	jerr.pub.error_exit = my_error_exit;
	/* Establish the setjmp return context for my_error_exit to use. */
	if (setjmp(jerr.setjmp_buffer)) 
	{
		/* If we get here, the JPEG code has signaled an error.
		 * We need to clean up the JPEG object, close the input file, and return.
		 */
		jpeg_destroy_compress(&cinfo);
		return 1;
	}
	jpeg_create_compress(&cinfo);

	jpeg_stdio_dest(&cinfo, outfile);

	cinfo.image_width = image->uWidth;
	cinfo.image_height = image->uHeight;
	cinfo.input_components =3;
	cinfo.in_color_space = JCS_RGB;

	jpeg_set_defaults(&cinfo);

	jpeg_start_compress(&cinfo, TRUE);

	JSAMPROW row_pointer[1];
	int row_stride;

	row_stride = image->uBytePerLine;
	if (row_stride / image->uWidth == 4)
	{
		unsigned char* buffer = malloc(image->uWidth * 3);
		int x;
		while (cinfo.next_scanline < cinfo.image_height)
		{
			row_pointer[0] = buffer;
			for (x = 0; x < image->uWidth; x++)
			{
				int offset = cinfo.next_scanline * row_stride + x*4;
				buffer[x*3+0] = image->pPlane[offset + 1];
				buffer[x*3+1] = image->pPlane[offset + 2];
				buffer[x*3+2] = image->pPlane[offset + 3];
			}
			jpeg_write_scanlines(&cinfo, row_pointer, 1);
		}
	}
	else
	{
		while (cinfo.next_scanline < cinfo.image_height)
		{
			row_pointer[0] = &(image->pPlane[cinfo.next_scanline * row_stride]);
			jpeg_write_scanlines(&cinfo, row_pointer, 1);
		}
	}
	jpeg_finish_compress(&cinfo);
	jpeg_destroy_compress(&cinfo);
	return 0;
}

int LoadTIFFDimensions(int fp, const char* filename, int *imgwidth, int *imgheight)
{
	TIFF* tifFile = TIFFFdOpen(fp, filename, "r");
    if(!tifFile)
    {
        return 1;
    }
    if (imgwidth)
		TIFFGetField(tifFile, TIFFTAG_IMAGEWIDTH, imgwidth);
	if (imgheight)
		TIFFGetField(tifFile, TIFFTAG_IMAGELENGTH, imgheight);
	TIFFClose(tifFile);
	return 0;
}

RawImage_t* LoadTIFF(int fp, const char* filename, int imgwidth, int imgheight)
{
    if(!fp) return 0;
	TIFF* tifFile = TIFFFdOpen(fp, filename, "r");
    if(!tifFile)
    {
        return 0;
    }
    RawImage_t *newimage = 0;
	int width, height;
	TIFFGetField(tifFile, TIFFTAG_IMAGEWIDTH, &width);
	TIFFGetField(tifFile, TIFFTAG_IMAGELENGTH, &height);

	if (imgwidth == 0 || imgheight == 0)
	{
		imgwidth = width;
		imgheight = height;
	}
	newimage=(RawImage_t *) malloc(sizeof(RawImage_t));
	if(newimage==NULL)
	{
		printf("Couldn't allocate RawImage_t for new image\n");
		fflush(stdout);
		TIFFClose(tifFile);
		return 0;
	}
	memset(newimage, 0, sizeof(RawImage_t));
	newimage->hasAlpha=1; // tiffs can have alpha
	newimage->pPlane = (unsigned char*) malloc(imgwidth * imgheight * 4);
	newimage->uWidth = imgwidth;
	newimage->uHeight = imgheight;
	newimage->uBytePerLine = imgwidth*4;
	if(!newimage->pPlane)
	{
		printf("Couldn't create surface for new image\n");
		fflush(stdout);
		TIFFClose(tifFile);
		free(newimage);
		return 0;
	}
	int rowsPerStrip=0;
	if(!TIFFGetField(tifFile, TIFFTAG_ROWSPERSTRIP, &rowsPerStrip))
	{
		fprintf(stderr,"Couldn't get rowsPerStrip\n");
		rowsPerStrip = height;
	}
	if (rowsPerStrip == 0)
		rowsPerStrip = height;
	// Create the image scaler if we're doing a resize
	int y;
	int r;
	if (imgwidth != width || imgheight != height)
	{
		struct SwsContext *sws = sws_getContext(width, height, PIX_FMT_RGB32, imgwidth, imgheight, PIX_FMT_RGB32, 0x0002, NULL);
		if (!sws)
		{
			TIFFClose(tifFile);
			free(newimage->pPlane);
			free(newimage);
			return 0;
		}
		uint32* buffer = (uint32*) _TIFFmalloc(rowsPerStrip * width * 4);
		for(y = 0; y < height; y+=rowsPerStrip)
		{
			TIFFReadRGBAStrip(tifFile, y, buffer);
			int currRows = (y + rowsPerStrip > height) ? (height - y) : rowsPerStrip;
			for (r = 0; r < currRows; r++)
				sws_scale(sws, ((unsigned char* )buffer) + (rowsPerStrip - r - 1)*width*4, 
					width*4, y + r, 1, newimage->pPlane, imgwidth*4);
		}
		_TIFFfree(buffer);
		sws_freeContext(sws);
	}
	else if (rowsPerStrip > 1)
	{
		uint32* buffer = (uint32*) _TIFFmalloc(rowsPerStrip * width * 4);
		for(y = 0; y < height; y+=rowsPerStrip)
		{
			int currRows = (y + rowsPerStrip > height) ? (height - y) : rowsPerStrip;
			TIFFReadRGBAStrip(tifFile, y, (uint32*)buffer);
			// Reverse vertical order
			for (r = 0; r < currRows; r++)
				memcpy(newimage->pPlane + (y + r)*imgwidth*4, 
				((unsigned char*)buffer) + ((rowsPerStrip - r - 1)*imgwidth*4), imgwidth*4);
		}
		_TIFFfree(buffer);
	}
	else
	{
		for(y = 0; y < height; y+=rowsPerStrip)
		{
			unsigned char* buffer = newimage->pPlane + y*imgwidth*4;
			TIFFReadRGBAStrip(tifFile, y, (uint32*)buffer);
		}
	}

	// Now go through and premultiply the alpha channel data & change from RGBA to ARGB
	int x;
	for (x = 0; x < imgwidth*imgheight; x++)
	{
		int base = x*4;
		int alpha = newimage->pPlane[base + 3];
		if (alpha < 0xFF)
		{
			newimage->pPlane[base + 3] = newimage->pPlane[base + 2] * alpha / 255;
			newimage->pPlane[base + 2] = newimage->pPlane[base + 1] * alpha / 255;
			newimage->pPlane[base + 1] = newimage->pPlane[base] * alpha / 255;
			newimage->pPlane[base] = alpha;
		}
		else
		{
			newimage->pPlane[base + 3] = newimage->pPlane[base + 2];
			newimage->pPlane[base + 2] = newimage->pPlane[base + 1];
			newimage->pPlane[base + 1] = newimage->pPlane[base];
			newimage->pPlane[base] = alpha;
		}
	}

	TIFFClose(tifFile);
    return newimage;
}
