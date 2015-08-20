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

static int resizew;
static int resizeh;
static int resizedx;
static int resizedy;
static int resizeposx;
static int resizeposy;

static char * StartScanlineResize(int datawidth, int imgw, int imgh, int neww, int newh)
{
    resizedx=(imgw<<16)/neww;
    resizedy=(imgh<<16)/newh;
    resizeposx=0;
    resizeposy=0;
    resizew=neww;
    resizeh=newh;
    
    return (char *)malloc(datawidth);
}

static void StopScanlineResize(unsigned char *resizebuf)
{
    resizedx=0;
    resizedy=0;
    resizeposx=0;
    resizeposy=0;
    resizew=0;
    resizeh=0;
    if(resizebuf!=NULL)
    {
        free(resizebuf);
    }
}

static void ScanlineRGBtoYUV(unsigned char *buffer, int datawidth, int line, STBImage_t *img, 
    unsigned char *resizebuf)
{
    int width=datawidth/3;
    int i;
    int posx=0,posxt=0;
    unsigned char y1,u1,v1;
    unsigned char y2,u2,v2;
    line=(line<<16)/resizedy;
    
    for(i=0;i<resizew/2;i++)
    {
        posx=posxt>>16;
        gfx_rgb2ycbcr(buffer[0+posx/2*6],buffer[1+posx/2*6],buffer[1+posx/2*6],&y1,&u1,&v1);
        gfx_rgb2ycbcr(buffer[3+posx/2*6],buffer[4+posx/2*6],buffer[5+posx/2*6],&y2,&u2,&v2);
        ((unsigned char *)img->s.plane[0].pPlane)[line*img->s.plane[0].uBytePerLine+i*2+0]=y1;
        ((unsigned char *)img->s.plane[0].pPlane)[line*img->s.plane[0].uBytePerLine+i*2+1]=y2;
        ((unsigned char *)img->s.plane[1].pPlane)[line*img->s.plane[1].uBytePerLine+i*2+0]=(u1+u2)/2;
        ((unsigned char *)img->s.plane[1].pPlane)[line*img->s.plane[1].uBytePerLine+i*2+1]=(v1+v2)/2;
        posxt+=resizedx*2;
    }
}

static void ScanlineRGBAtoYUV(unsigned char *buffer, int datawidth, int line, STBImage_t *img, 
    unsigned char *resizebuf)
{
    int width=datawidth/4;
    int i;
    unsigned char y1,u1,v1;
    unsigned char y2,u2,v2;
    int posx=0,posxt=0;
    line=(line<<16)/resizedy;
    
    for(i=0;i<resizew/2;i++)
    {
        posx=posxt>>16;
        gfx_rgb2ycbcr(buffer[3+posx/2*8],buffer[2+posx/2*8],buffer[1+posx/2*8],&y1,&u1,&v1);
        gfx_rgb2ycbcr(buffer[7+posx/2*8],buffer[6+posx/2*8],buffer[5+posx/2*8],&y2,&u2,&v2);
        ((unsigned char *)img->s.plane[0].pPlane)[line*img->s.plane[0].uBytePerLine+i*2+0]=y1;
        ((unsigned char *)img->s.plane[0].pPlane)[line*img->s.plane[0].uBytePerLine+i*2+1]=y2;
        ((unsigned char *)img->s.plane[1].pPlane)[line*img->s.plane[1].uBytePerLine+i*2+0]=(u1+u2)/2;
        ((unsigned char *)img->s.plane[1].pPlane)[line*img->s.plane[1].uBytePerLine+i*2+1]=(v1+v2)/2;
        ((unsigned char *)img->s.plane[2].pPlane)[line*img->s.plane[2].uBytePerLine+i*2+0]=
            buffer[0+posx/2*8];
        ((unsigned char *)img->s.plane[2].pPlane)[line*img->s.plane[2].uBytePerLine+i*2+1]=
            buffer[4+posx/2*8];
        posxt+=resizedx*2;
    }
}

// Based on example from png library
int LoadPNG(STBRenderContext_t *RC, const char *filename, int imgwidth, int imgheight)
{
    STBImage_t *newimage;
    char header[8];
    png_uint_32 width, height;
    int bit_depth, color_type, interlace_type;
    int number_passes,pass,y;
    FILE *fp = fopen(filename, "rb");
    if(!fp) return 0;
    if(fread(header, 8, 1, fp)!=1) return 0;
    int is_png = !png_sig_cmp(header, 0, 8);
    if(!is_png) return 0;

    printf("Found png header\n");    
    fflush(stdout);
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
        fclose(fp);
        return 0;
    }
    
    printf("png init io\n");    
    fflush(stdout);
    
    png_init_io(png_ptr, fp);
    png_set_sig_bytes(png_ptr, 8);
    
    png_read_info(png_ptr, info_ptr);
    
    png_get_IHDR(png_ptr, info_ptr, &width, &height, &bit_depth, &color_type,
       &interlace_type, int_p_NULL, int_p_NULL);

    printf("Loading png file %dx%d\n",
        width, height);
    fflush(stdout);
    newimage=(STBImage_t *) malloc(sizeof(STBImage_t));
    if(newimage==NULL)
    {
        printf("Couldn't allocate STBImage_t for new image\n");
        fflush(stdout);
        png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);
        fclose(fp);
        return 0;
    }
    // TODO: add checks for odd width
    newimage->hasAlpha=1; // jpeg don't have alpha
    newimage->h = gfx_create_surface(RC->fgfx, imgwidth, imgheight,
        GFX_SURFACE_AYCBCR_422_8888, GFX_VDEV_NULL, 0);

    if(newimage->h < 0)
    {
        printf("Couldn't create surface for new image\n");
        fflush(stdout);
        png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);
        fclose(fp);
        free(newimage);
        return 0;
    }
          
    if(gfx_lock_surface(RC->fgfx, newimage->h, &newimage->s) < 0)
    {
        printf("Couldn't lock surface for new image\n");
        fflush(stdout);
        gfx_destroy_surface(RC->fgfx, newimage->h);
        png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);
        fclose(fp);
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
    /* flip the RGB pixels to BGR (or RGBA to BGRA) */
    if(color_type & PNG_COLOR_MASK_COLOR)
        png_set_bgr(png_ptr);

    /* swap the RGBA or GA data to ARGB or AG (or BGRA to ABGR) */
    png_set_swap_alpha(png_ptr);
    png_set_filler(png_ptr, 0xff, PNG_FILLER_BEFORE);
    number_passes = png_set_interlace_handling(png_ptr);
    png_read_update_info(png_ptr, info_ptr);
    
    printf("starting to read for %d passes, buflen %d\n",number_passes,
        png_get_rowbytes(png_ptr,info_ptr));
    fflush(stdout);
    unsigned char *buffer = (unsigned char *) malloc(png_get_rowbytes(png_ptr,info_ptr));
    unsigned char *resizebuffer = 
        (unsigned char *) StartScanlineResize(png_get_rowbytes(png_ptr,info_ptr),width, height,
        imgwidth,imgheight);
    
    /* Now it's time to read the image.  One of these methods is REQUIRED */
    for(pass = 0; pass < number_passes; pass++)
    {
        for(y = 0; y < height; y++)
        {
            png_read_rows(png_ptr, &buffer, png_bytepp_NULL, 1);
            ScanlineRGBAtoYUV(buffer,
                png_get_rowbytes(png_ptr,info_ptr), y, newimage, resizebuffer);
        }
    }
    StopScanlineResize(resizebuffer);
    free(buffer);
    
    /* read rest of file, and get additional chunks in info_ptr - REQUIRED */
    png_read_end(png_ptr, info_ptr);

    /* clean up after the read, and free any memory allocated - REQUIRED */
    png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);

    /* close the file */
    fclose(fp);

    /* that's it */
    return (int) (UINT) newimage;
}

// Based on example for libungif
int LoadGIF(STBRenderContext_t *RC, const char *filename, int imgwidth, int imgheight)
{
    STBImage_t *newimage;
    
    int	i, j, Size, Row, Col, Width, Height, ExtCode, Count;
    GifRecordType RecordType;
    GifByteType *Extension;
    GifRowType *ScreenBuffer;
    GifFileType *GifFile;
    int ColorMapSize = 0;
    int InterlacedOffset[] = { 0, 4, 2, 1 }; 
    int InterlacedJumps[] = { 8, 8, 4, 2 };
    ColorMapObject *ColorMap;

    if((GifFile = DGifOpenFileName(filename)) == NULL)
    {
        return 0;
    }
    
    printf("Loading gif file %dx%d\n",
       GifFile->SWidth, GifFile->SHeight);
    fflush(stdout);
    
    if((ScreenBuffer = (GifRowType *)
        malloc(GifFile->SHeight * sizeof(GifRowType *))) == NULL)
    {
        DGifCloseFile(GifFile);
        return 0;
    }

    Size = GifFile->SWidth * sizeof(GifPixelType);/* Size in bytes one row.*/
    
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
    
    newimage=(STBImage_t *) malloc(sizeof(STBImage_t));
    if(newimage==NULL)
    {
        printf("Couldn't allocate STBImage_t for new image\n");
        fflush(stdout);
        for(i = 0; i < GifFile->SHeight; i++)
        {
            if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
        }
        free(ScreenBuffer);
        DGifCloseFile(GifFile);
        return 0;
    }
    // TODO: add checks for odd width
    newimage->hasAlpha=0; // TODO: gif can have transparent color 
    newimage->h = gfx_create_surface(RC->fgfx, imgwidth, imgheight,
        GFX_SURFACE_YCBCR_422_888, GFX_VDEV_NULL, 0);
   
    if(newimage->h < 0)
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
          
    if(gfx_lock_surface(RC->fgfx, newimage->h, &newimage->s) < 0)
    {
        printf("Couldn't lock surface for new image\n");
        fflush(stdout);
        gfx_destroy_surface(RC->fgfx, newimage->h);
        for(i = 0; i < GifFile->SHeight; i++)
        {
            if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
        }
        free(ScreenBuffer);
        DGifCloseFile(GifFile);
        free(newimage);
        return 0;
    }

    unsigned char *buffer=malloc(GifFile->SWidth*3);
    
    unsigned char *resizebuffer = 
         (unsigned char *) StartScanlineResize(GifFile->SWidth*3, 
             GifFile->SWidth, GifFile->SHeight,
             imgwidth,imgheight);

    for (i = 0; i < GifFile->SHeight; i++) 
    {
        for (j = 0; j < GifFile->SWidth; j++) 
        {
            ColorMapEntry = &ColorMap->Colors[ScreenBuffer[i][j]];
            buffer[j*3+0] = ColorMapEntry->Red;
            buffer[j*3+1] = ColorMapEntry->Green;
            buffer[j*3+2] = ColorMapEntry->Blue;
        }
        ScanlineRGBtoYUV(&buffer[0],GifFile->SWidth*3, i, newimage, resizebuffer);
    }
    StopScanlineResize(resizebuffer);
    free(buffer);
    for(i = 0; i < GifFile->SHeight; i++)
    {
        if(ScreenBuffer[i]!=NULL) free(ScreenBuffer[i]);
    }
    free(ScreenBuffer);
    DGifCloseFile(GifFile);
    
    return (int) (UINT) newimage;
}

// Based on example from jpeg library
int LoadJPEG(STBRenderContext_t *RC, const char *filename, int imgwidth, int imgheight)
{
    STBImage_t *newimage;
    struct jpeg_decompress_struct cinfo;
    struct jpeg_error_mgr jerr;
    JSAMPARRAY buffer;
    int row_stride;
    FILE * infile;
    infile=fopen(filename,"rb");
    if(infile==NULL) return 0;
    
    cinfo.err = jpeg_std_error(&jerr);
   jpeg_create_decompress(&cinfo);
   jpeg_stdio_src(&cinfo, infile);
   (void) jpeg_read_header(&cinfo, TRUE);
   (void) jpeg_start_decompress(&cinfo);
   printf("Loading jpeg file %dx%dx%d\n",
       cinfo.output_width, cinfo.output_height,cinfo.output_components);
   fflush(stdout);
    
   newimage=(STBImage_t *) malloc(sizeof(STBImage_t));
   if(newimage==NULL)
   {
       printf("Couldn't allocate STBImage_t for new image\n");
       fflush(stdout);
       (void) jpeg_finish_decompress(&cinfo);
       jpeg_destroy_decompress(&cinfo);
       fclose(infile);   
       return 0;
   }
   // TODO: add checks for odd width
   newimage->hasAlpha=0; // jpeg don't have alpha
   newimage->h = gfx_create_surface(RC->fgfx, imgwidth, imgheight,
       GFX_SURFACE_YCBCR_422_888, GFX_VDEV_NULL, 0);
   
   if(newimage->h < 0)
   {
       printf("Couldn't create surface for new image\n");
       fflush(stdout);
       (void) jpeg_finish_decompress(&cinfo);
       jpeg_destroy_decompress(&cinfo);
       fclose(infile);      
       free(newimage);
       return 0;
   }
          
   if(gfx_lock_surface(RC->fgfx, newimage->h, &newimage->s) < 0)
   {
       printf("Couldn't lock surface for new image\n");
       fflush(stdout);
       gfx_destroy_surface(RC->fgfx, newimage->h);
       (void) jpeg_finish_decompress(&cinfo);
       jpeg_destroy_decompress(&cinfo);
       fclose(infile);      
       free(newimage);
       return 0;
   }

   row_stride = cinfo.output_width * cinfo.output_components;
   buffer = (*cinfo.mem->alloc_sarray)
        ((j_common_ptr) &cinfo, JPOOL_IMAGE, row_stride, 1);
   unsigned char *resizebuffer = 
        (unsigned char *) StartScanlineResize(row_stride, cinfo.output_width, cinfo.output_height,
        imgwidth,imgheight);

   while (cinfo.output_scanline < cinfo.output_height)
   {
       (void) jpeg_read_scanlines(&cinfo, buffer, 1);
       // Convert the scanline to YUV...
       ScanlineRGBtoYUV(buffer[0],row_stride,cinfo.output_scanline,newimage, resizebuffer);
   }
   StopScanlineResize(resizebuffer);
   (void) jpeg_finish_decompress(&cinfo);
   jpeg_destroy_decompress(&cinfo);

   fclose(infile);

   return (int) (UINT) newimage;
}
