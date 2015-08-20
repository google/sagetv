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
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>
#include <signal.h>
#include <float.h>
#include <math.h>

#include <unistd.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <sys/ioctl.h>

#include "../logo.h"
#include "stbgfx.h"
#include "../subdecoder.h"

typedef void* mspace;
extern mspace create_mspace_with_base(void* base, size_t capacity, int locked);
extern void* mspace_malloc(mspace msp, size_t bytes);
extern void mspace_free(mspace msp, void* mem);

//#define DEBUGRENDER
//#define STBPROFILE
//#define DEBUGMINMAX

#define BYTE unsigned char
#define ULONG unsigned long
// Note: this makes the Y range 16-235
void gfx_rgb2ycbcr(BYTE r, BYTE g,  BYTE b,  BYTE *y, BYTE *cb, BYTE *cr)
{
    // Y  =  0.257*R + 0.504*G + 0.098*B + 16
    // CB = -0.148*R - 0.291*G + 0.439*B + 128
    // CR =  0.439*R - 0.368*G - 0.071*B + 128
    *y  = (BYTE)((8432*(ULONG)r + 16425*(ULONG)g + 3176*(ULONG)b + 16*32768)>>15);
    *cb = (BYTE)((128*32768 + 14345*(ULONG)b - 4818*(ULONG)r -9527*(ULONG)g)>>15);
    *cr = (BYTE)((128*32768 + 14345*(ULONG)r - 12045*(ULONG)g-2300*(ULONG)b)>>15);
}

typedef struct {
    int fgfx;                 // handle to graphic device
    int surfaceheight;
    unsigned char *vidmem;
    unsigned char *buffer0;
    unsigned char *buffer1;
    unsigned char *workbuffer;
    unsigned char *workbuffer2;
    mspace texturespace;
    int cursurf;              // Surface currently seen
    long long rendertime;
    long long startframetime;
    long long optimes[9];
    char *imagebuffer;
    int minx,maxx,miny,maxy;
}STBRenderContext_t;

typedef struct {
    int hasAlpha; // Is there a third plane for alpha?
    unsigned int uBytePerLine;
    unsigned char * pPlane;
    unsigned int uWidth;
    unsigned int uHeight;
    unsigned int ubpp;
}STBImage_t;

STBRenderContext_t * RC;

int GFX_flipBuffer();
inline void drawBlend(STBRenderContext_t *RC, unsigned int offset,
    unsigned int r, unsigned int g, unsigned int b, unsigned int a);
    

updateMinMax(int x, int y,int width,int height)
{
    if(RC->minx>x) RC->minx=x;
    if(RC->miny>y) RC->miny=y;
    if(RC->maxx<x+width) RC->maxx=x+width;
    if(RC->maxy<y+height) RC->maxy=y+height;
    
    if(RC->minx<0) RC->minx=0;
    if(RC->miny<0) RC->miny=0;
    if(RC->maxx>720) RC->maxx=720;
    if(RC->maxy>RC->surfaceheight) RC->maxy=RC->surfaceheight;
}

int ShowLogo()
{
    int pos=0;
    int screenpos=40+(RC->surfaceheight-448)/2*720; // 640 in 720 so start at 40...
    
    while(pos<logo_data_len)
    {
        unsigned char cmd = logo_data[pos];
        pos+=1;
        if(cmd<0xC0)
        { // normal
            drawBlend(RC, screenpos, logo_pal[cmd*3], 
                logo_pal[cmd*3+1], logo_pal[cmd*3+2], 255);
            screenpos+=1;
            if((screenpos%720)>=680) screenpos+=80; // slow...
        }
        else
        { // len
            int len=cmd&0x3F;
            cmd = logo_data[pos];
            pos+=1;
            while(len)
            {
                drawBlend(RC, screenpos, logo_pal[cmd*3], 
                    logo_pal[cmd*3+1], logo_pal[cmd*3+2], 255);
                screenpos+=1;
                if((screenpos%720)>=680) screenpos+=80; // slow...
                len--;
            }
        }
    }
    RC->minx=0;
    RC->maxx=720;
    RC->miny=0;
    RC->maxy=RC->surfaceheight;
    GFX_flipBuffer();
}

int GFX_init(int mode)
{
#ifdef DEBUGRENDER
    fprintf(stderr"Initialising STB Renderer\n");
    fflush(stderr);
#endif
        
    int rtn = 0;
    
    int i,j;
    int error;
    GFXFB_T testparm;    
    
    STBRenderContext_t * RenderContext;
    
    RenderContext = (STBRenderContext_t *) malloc(sizeof(STBRenderContext_t));
    
    if(RenderContext == NULL)
    {    
        rtn=0;
        goto GFX_end;
    }
    
/*    if(FT_Init_FreeType( &RenderContext->library ))
    {
        fprintf(stderr"Error loading freetype library\n");
        fflush(stderr);
        free(RenderContext);
        return 0;
    }
*/        
    RenderContext->fgfx = -1;

    if(mode==0)
        RenderContext->surfaceheight=480;
    else
        RenderContext->surfaceheight=576;
    
    
    RenderContext->fgfx = open("/dev/stbgfx",O_RDWR);

    if(RenderContext->fgfx < 0)
    {
        fprintf(stderr, "Failed to open gfx device /dev/stbgfx\n");
        fflush(stderr);
        rtn=0;
        goto GFX_end;
    }

    // TODO: make it work on PAL...
    
    ioctl(RenderContext->fgfx, IOC_GFX_GET_FB, &testparm);

    fprintf(stderr, "%X %X %X %X %X %X %X\n",testparm.uBase, testparm.uSize, testparm.uFB0,
        testparm.uFB1, testparm.width, testparm.height, testparm.uFreeOffset);

    RenderContext->vidmem = mmap(0, testparm.uSize, 
            PROT_WRITE|PROT_READ, MAP_SHARED,  RenderContext->fgfx, testparm.uBase);
            
    if(MAP_FAILED == RenderContext->vidmem)
    {
        fprintf(stderr, "couldn't map video memory\n");
        rtn=0;
        goto GFX_end;
    }
    fprintf(stderr, "Mapped video memory at %X\n",RenderContext->vidmem);
    
    RenderContext->buffer0 = &RenderContext->vidmem[testparm.uFB0];
    RenderContext->buffer1 = &RenderContext->vidmem[testparm.uFB1];
    RenderContext->workbuffer = (unsigned char *) malloc(720*576*4); // R,G,B,A
    if(RenderContext->workbuffer==0)
    {
        fprintf(stderr, "couldn't allocate work buffer\n");
        rtn=0;
        goto GFX_end;
    }
    // TODO: see if we can use dma to speed things up?
    RenderContext->workbuffer2 = (unsigned char *) malloc(720*576*3); // Y, UV, A
    if(RenderContext->workbuffer2==0)
    {
        fprintf(stderr, "couldn't allocate work buffer 2\n");
        rtn=0;
        goto GFX_end;
    }
    
    fprintf(stderr, "Clearing vidmem\n");
    
    memset(&RenderContext->vidmem[testparm.uFreeOffset], 0, 
        testparm.uSize-testparm.uFreeOffset);
    
    fprintf(stderr, "Creating texture space\n");
    
    RenderContext->imagebuffer = malloc(4*1024*1024);
    if(RenderContext->imagebuffer==NULL)
    {
        fprintf(stderr, "couldn't allocate imagebuffer\n");
        rtn=0;
        goto GFX_end;
    }
    
    RenderContext->texturespace = create_mspace_with_base(
        &RenderContext->imagebuffer[0], //&RenderContext->vidmem[testparm.uFreeOffset]
        4*1024*1024, 0); //testparm.uSize-testparm.uFreeOffset
    if(RenderContext->texturespace==0)
    {
        fprintf(stderr, "couldn't allocate texture space\n");
        rtn=0;
        goto GFX_end;
    }
    
    fprintf(stderr, "Allocated texture space\n");
    // Initially we display the first surface...    
    RenderContext->cursurf=0; // We are displaying surface 0...    
    RenderContext->rendertime=0;

    // Set black data
    for(i=0;i<720*RenderContext->surfaceheight;i++)  
        ((char *)RenderContext->buffer0)[i]=16;
    
    // Set white chroma
    for(i=0;i<720*RenderContext->surfaceheight;i++)  
        ((char *)RenderContext->buffer0)[i+720*576]=128;
    
    // Set full alpha
    for(i=0;i<720*RenderContext->surfaceheight;i++)  
        ((char *)RenderContext->buffer0)[i+720*576*2]=255;

    RC=RenderContext;
    ioctl(RC->fgfx, IOC_GFX_FLIP_FB, 1);
    ShowLogo();
    return 1;
    
GFX_end:

    if(RenderContext != NULL)
    {
        if(RenderContext->fgfx >= 0)
        {
            close(RenderContext->fgfx);
        }
        free(RenderContext);
        RenderContext=NULL;
    }
    return rtn;
}

void GFX_deinit()
{
#ifdef DEBUGRENDER
    fprintf(stderr,"Stopping STB Renderer\n");
    fflush(stderr);
#endif
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;    
    }
    
    if(RC != NULL)
    {
        if(RC->fgfx >= 0)
        {
            close(RC->fgfx);        
        }    
        if(RC->workbuffer)
        {
            free(RC->workbuffer);
            RC->workbuffer=0;
        }
        if(RC->workbuffer2)
        {
            free(RC->workbuffer2);
            RC->workbuffer2=0;
        }
        if(RC->imagebuffer)
        {
            free(RC->imagebuffer);
            RC->imagebuffer=0;
        }
        free(RC);
        RC=NULL;
    }
    return;
}

#define tbl(x) __asm__ __volatile__ ("mftb %0,268\n\t":"=r" (x) )
#define tbu(x) __asm__ __volatile__ ("mftb %0,269\n\t":"=r" (x) )


#define mullhwu(x,y,z) __asm__ __volatile__ ("mullhwu %0,%1,%2\n\t":"=r"(x):"r"(y),"r"(z))
#define mullhw(x,y,z) __asm__ __volatile__ ("mullhw %0,%1,%2\n\t":"=r"(x):"r"(y),"r"(z))

#define maclhwsu(x,y,z) __asm__ __volatile__ ("maclhwsu %0,%2,%3\n\t":"=r"(x):"0"(x),"r"(y),"r"(z))
#define maclhws(x,y,z) __asm__ __volatile__ ("maclhws %0,%2,%3\n\t":"=r"(x):"0"(x),"r"(y),"r"(z))

#define rlw(x,y,z) __asm__ __volatile__ ("rlwnm %0,%1,%2,0,31\n\t":"=r"(x):"r"(y),"r"(z))
#define rlwimi(x,y) __asm__ __volatile__ ("rlwimi %0,%2,16,16,23\n\t":"=r"(x):"0"(x),"r"(y))


/*static unsigned long long get_timebase() {
  unsigned long long full;
  register unsigned int upper1;
  register unsigned int upper2;
  register unsigned int lower;

  do {
    tbu(upper1);
    tbl(lower);
    tbu(upper2);
  } while(upper1!=upper2);
  full = upper1;
  full = full << 32;
  return full|lower;
}*/
static long long get_timebase()
{
  struct timeval tm;
  gettimeofday(&tm, 0);
  return tm.tv_sec * 1000000LL + (tm.tv_usec);
}


int GFX_startFrame()
{
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
        fflush(stderr);
#endif
        return;    
    }
    RC->minx=720;
    RC->maxx=0;
    RC->miny=RC->surfaceheight;
    RC->maxy=0;
    RC->startframetime=get_timebase();
    return 0;
}

int GFX_flipBuffer()
{
    static int framecount=0;
    long long startfliptime,startfliptime2;
    int i,j;
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
        fflush(stderr);
#endif
        return;    
    }
#ifdef STBPROFILE
    startfliptime=get_timebase();
#endif
#ifdef DEBUGMINMAX
    fprintf(stderr, "Flipping with min max values: %d %d %d %d\n",RC->minx,RC->miny,RC->maxx,RC->maxy);
#endif    
    unsigned int color1,color2;
    unsigned char y1,u1,v1;
    unsigned char y2,u2,v2;
    int i2=0;
    for(i=RC->miny;i<RC->maxy;i++)
    {
        i2=720*i;
        for(j=RC->minx/2;j<(RC->maxx+1)/2;j++)
        {
            unsigned int tmp;
            unsigned int r,g,b;
            color1=((unsigned int *)RC->workbuffer)[j*2+i2+0];
            color2=((unsigned int *)RC->workbuffer)[j*2+i2+1];

            ((unsigned char *)RC->workbuffer2)[2*720*576+j*2+i2+0]=color1>>24;
            ((unsigned char *)RC->workbuffer2)[2*720*576+j*2+i2+1]=color2>>24;

            if((color2|color1)&0xFF000000)
            {
                // TODO: reorder the instructions for better performance and use fact it's 16 bit... 
                // also verify if the hardware can do more than 1 mac at a time, probably not..
                r=(color1>>16)&0xFF;
                g=(color1>>8)&0xFF;
                b=(color1>>0)&0xFF;

                tmp=16*32768;
                maclhwsu(tmp,8432, r);
                maclhwsu(tmp,16425, g);
                maclhwsu(tmp,3176, b);
                ((unsigned char *)RC->workbuffer2)[j*2+i2+0]=tmp>>15;
                //y1=;

                tmp=128*32768;
                maclhws(tmp,14345, b);
                maclhws(tmp,-4818, r);
                maclhws(tmp,-9527, g);
                u1=tmp>>15;

                tmp=128*32768;
                maclhws(tmp,14345, r);
                maclhws(tmp,-12045, g);
                maclhws(tmp,-2300, b);
                v1=tmp>>15;

                r=(color2>>16)&0xFF;
                g=(color2>>8)&0xFF;
                b=(color2>>0)&0xFF;

                tmp=128*32768;
                maclhws(tmp,14345, b);
                maclhws(tmp,-4818, r);
                maclhws(tmp,-9527, g);
                //u2=tmp>>15;
                ((unsigned char *)RC->workbuffer2)[720*576+j*2+i2+0]=((u1+(tmp>>15))/2);

                tmp=16*32768;
                maclhwsu(tmp,8432, r);
                maclhwsu(tmp,16425, g);
                maclhwsu(tmp,3176, b);
                //y2=
                ((unsigned char *)RC->workbuffer2)[j*2+i2+1]=tmp>>15;;

                tmp=128*32768;
                maclhws(tmp,14345, r);
                maclhws(tmp,-12045, g);
                maclhws(tmp,-2300, b);
                //v2=tmp>>15;
                ((unsigned char *)RC->workbuffer2)[720*576+j*2+i2+1]=((v1+(tmp>>15))/2);
            }
        }
    }

    /* Video level test...
    for(i=0;i<720;i++)
    {
        ((unsigned char *)RC->workbuffer2)[i]=i;
        ((unsigned char *)RC->workbuffer2)[720*576+i]=128;
        ((unsigned char *)RC->workbuffer2)[2*720*576+i]=255;
        ((unsigned char *)RC->workbuffer2)[720+i]=i;
        ((unsigned char *)RC->workbuffer2)[720+720*576+i]=128;
        ((unsigned char *)RC->workbuffer2)[720+2*720*576+i]=255;
        ((unsigned char *)RC->workbuffer2)[2*720+i]=i;
        ((unsigned char *)RC->workbuffer2)[2*720+720*576+i]=128;
        ((unsigned char *)RC->workbuffer2)[2*720+2*720*576+i]=255;
        ((unsigned char *)RC->workbuffer2)[3*720+i]=i;
        ((unsigned char *)RC->workbuffer2)[3*720+720*576+i]=128;
        ((unsigned char *)RC->workbuffer2)[3*720+2*720*576+i]=255;
    }*/
    
#ifdef STBPROFILE
    startfliptime2=get_timebase();
#endif
    if(1) //RC->cursurf==0)
    {
        // Write to buffer1
        if(GetSubpictureEnabled())
        {
            unsigned char * subpicbuffer = GetSubpictureScreen();
            unsigned int * clut = GetSubpictureCLUT();
            // Copy full buffer before subpictures...
            //memcpy(RC->buffer1, RC->workbuffer2, 720*576*3);
            int alpha1,alpha2;
            fprintf(stderr,"Drawing subpicture\n");
            for(i=0;i<RC->surfaceheight;i++)
            {
                i2=720*i;
                memcpy(&RC->buffer1[i2], &RC->workbuffer2[i2], 720);
                memcpy(&RC->buffer1[720*576+i2], &RC->workbuffer2[720*576+i2], 720);
                memcpy(&RC->buffer1[2*720*576+i2], &RC->workbuffer2[2*720*576+i2], 720);
                for(j=0;j<720/2;j++)
                {
                    alpha1=((unsigned char *)RC->workbuffer2)[j*2+i2+720*576*2+0];
                    alpha2=((unsigned char *)RC->workbuffer2)[j*2+i2+720*576*2+1];                    
                    if((alpha2|alpha1)==0)
                    {
                        // We can display subpicture if we have any
                        alpha1=subpicbuffer[j*2+i2+0]>>4;
                        alpha2=subpicbuffer[j*2+i2+1]>>4;
                        
                        if(alpha1|alpha2)
                        {   
                            RC->buffer1[j*2+i2+0]=clut[subpicbuffer[j*2+i2+0]&0xF]>>16;
                            RC->buffer1[j*2+i2+1]=clut[subpicbuffer[j*2+i2+1]&0xF]>>16;
                            RC->buffer1[720*576+j*2+i2+0]=clut[subpicbuffer[j*2+720*i+0]&0xF]>>0;
                            RC->buffer1[720*576+j*2+i2+1]=clut[subpicbuffer[j*2+720*i+1]&0xF]>>8;
                            RC->buffer1[2*720*576+j*2+i2+0]=alpha1|(alpha1<<4);
                            RC->buffer1[2*720*576+j*2+i2+1]=alpha2|(alpha2<<4);
                        }
                    }
                }
            }
        }
        else
        { // partial update
            for(i=RC->miny;i<RC->maxy;i++)
            {
                memcpy(&RC->buffer1[720*i+(RC->minx&~1)], &RC->workbuffer2[720*i+(RC->minx&~1)], ((RC->maxx+1)&~1)-(RC->minx&~1));
                memcpy(&RC->buffer1[720*576+720*i+(RC->minx&~1)], 
                    &RC->workbuffer2[720*576+720*i+(RC->minx&~1)], ((RC->maxx+1)&~1)-(RC->minx&~1));
                memcpy(&RC->buffer1[2*720*576+720*i+(RC->minx&~1)], 
                    &RC->workbuffer2[2*720*576+720*i+(RC->minx&~1)], ((RC->maxx+1)&~1)-(RC->minx&~1));
            }
            //memcpy(RC->buffer1, RC->workbuffer2, 720*576*3);
        }
        //ioctl(RC->fgfx, IOC_GFX_FLIP_FB, 1);
        RC->cursurf=1;
    }
    else
    {
        // Write to buffer0
        memcpy(RC->buffer0, RC->workbuffer2, 720*576*3);
        if(GetSubpictureEnabled())
        {
            unsigned char * subpicbuffer = GetSubpictureScreen();
            unsigned int * clut = GetSubpictureCLUT();
            int alpha1,alpha2;
            fprintf(stderr,"Drawing subpicture\n");
            for(i=0;i<RC->surfaceheight;i++)
            {
                for(j=0;j<720/2;j++)
                {
                    alpha1=((unsigned char *)RC->workbuffer2)[j*2+720*i+720*576*2+0];
                    alpha2=((unsigned char *)RC->workbuffer2)[j*2+720*i+720*576*2+1];                    
                    if((alpha2|alpha1)==0)
                    {
                        // We can display subpicture if we have any
                        alpha1=subpicbuffer[j*2+720*i+0]>>4;
                        alpha2=subpicbuffer[j*2+720*i+1]>>4;
                        
                        if(alpha1|alpha2)
                        {   
                            RC->buffer0[j*2+720*i+0]=clut[subpicbuffer[j*2+720*i+0]&0xF]>>16;
                            RC->buffer0[j*2+720*i+1]=clut[subpicbuffer[j*2+720*i+1]&0xF]>>16;
                            RC->buffer0[720*576+j*2+720*i+0]=clut[subpicbuffer[j*2+720*i+0]&0xF]>>0;
                            RC->buffer0[720*576+j*2+720*i+1]=clut[subpicbuffer[j*2+720*i+1]&0xF]>>8;
                            RC->buffer0[2*720*576+j*2+720*i+0]=alpha1|(alpha1<<4);
                            RC->buffer0[2*720*576+j*2+720*i+1]=alpha2|(alpha2<<4);
                        }
                    }
                }
            }
        }
        ioctl(RC->fgfx, IOC_GFX_FLIP_FB, 0);
        RC->cursurf=0;
    }
    framecount+=1;
// Only used when profiling...
//    if(framecount==2) exit(0);
#ifdef STBPROFILE
    fprintf(stderr, "Flipping, render time was : %lld, tot ftime was: %lld (%lld, %lld flipping)\n",RC->rendertime,
        (long long) (get_timebase()-RC->startframetime), (get_timebase()-startfliptime),
        (get_timebase()-startfliptime2));
    fprintf(stderr, "optimes: %lld %lld %lld %lld %lld %lld %lld %lld %lld\n",
        RC->optimes[0],RC->optimes[1],RC->optimes[2],RC->optimes[3],
        RC->optimes[4],RC->optimes[5],RC->optimes[6],RC->optimes[7], 
        RC->optimes[8]);
    RC->optimes[0]=0; RC->optimes[1]=0; RC->optimes[2]=0; RC->optimes[3]=0;
    RC->optimes[4]=0; RC->optimes[5]=0; RC->optimes[6]=0; RC->optimes[7]=0;
    RC->optimes[8]=0;
#endif
    RC->rendertime=0;
    return 0;
}



// Doesn't work very well with YUV since adding in u,v doesn't produce nice looking results...
inline void drawBlend(STBRenderContext_t *RC, unsigned int offset,
    unsigned int r, unsigned int g, unsigned int b, unsigned int a)
{    
    /*
    Cd = Cs + Cd*(1-As)    
    Ad = As + Ad*(1-As)
    */
    int tmp;
    int inva=65535-(a<<8);
    
    offset*=4;
    
    tmp=r<<24;
    maclhwsu(tmp,((unsigned int)((unsigned char *)RC->workbuffer)[offset+1])<<8,inva);    
    ((unsigned char *)RC->workbuffer)[offset+1]=tmp>>24;
    
    tmp=g<<24;
    maclhwsu(tmp,((unsigned int)((unsigned char *)RC->workbuffer)[offset+2])<<8,inva);    
    ((unsigned char *)RC->workbuffer)[offset+2]=tmp>>24;
    
    tmp=b<<24;
    maclhwsu(tmp,((unsigned int)((unsigned char *)RC->workbuffer)[offset+3])<<8,inva);    
    ((unsigned char *)RC->workbuffer)[offset+3]=tmp>>24;

    tmp=a<<24;
    maclhwsu(tmp,((unsigned int)((unsigned char *)RC->workbuffer)[offset+0])<<8,inva);    
    ((unsigned char *)RC->workbuffer)[offset+0]=tmp>>24;
}

inline void drawBlend2(STBRenderContext_t *RC, unsigned int offset,
    unsigned int r, unsigned int g, unsigned int b, unsigned int a)
{
    /*
    Cd = Cs + Cd*(1-As)    
    Ad = As + Ad*(1-As)
    */
    int tmp;
    int inva=65535-(a<<8);
    
    offset*=4;
    
    mullhwu(tmp,r<<8,a<<8);
    maclhwsu(tmp,((unsigned int)((unsigned char *)RC->workbuffer)[offset+1])<<8,inva);
    ((unsigned char *)RC->workbuffer)[offset+1]=tmp>>24;

    mullhwu(tmp,g<<8,a<<8);
    maclhwsu(tmp,((unsigned int)((unsigned char *)RC->workbuffer)[offset+2])<<8,inva);
    ((unsigned char *)RC->workbuffer)[offset+2]=tmp>>24;

    mullhwu(tmp,b<<8,a<<8);
    maclhwsu(tmp,((unsigned int)((unsigned char *)RC->workbuffer)[offset+3])<<8,inva);
    ((unsigned char *)RC->workbuffer)[offset+3]=tmp>>24;

    mullhwu(tmp,a<<8,a<<8);
    maclhwsu(tmp,((unsigned int)((unsigned char *)RC->workbuffer)[offset+0])<<8,inva);
    ((unsigned char *)RC->workbuffer)[offset+0]=tmp>>24;
}


inline void drawNoBlend(STBRenderContext_t *RC, unsigned int offset,
    unsigned int r, unsigned int g, unsigned int b, unsigned int a)
{    
    offset*=4;
    ((unsigned char *)RC->workbuffer)[offset+1]=r;
    ((unsigned char *)RC->workbuffer)[offset+2]=g;
    ((unsigned char *)RC->workbuffer)[offset+3]=b;
    ((unsigned char *)RC->workbuffer)[offset+0]=a;
}



void GFX_drawRect(int x, int y, int width, int height, 
    int thickness, int argbTL, int argbTR, int argbBR, int argbBL)
{
#ifdef STBPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
    int i,j;
    int x1,x2,y1,y2;

#ifdef DEBUGRENDER
    fprintf(stderr, "Drawing rectangle %d %d %d %d\n",x,y,width,height);
    fflush(stderr);    
#endif
    
    updateMinMax(x,y,width,height);
    
    y1=y;
    x1=x;
    x2=x+width;
    
    // All in S15.16 format
    int A,R,G,B;
    int AL,RL,BL,GL; // Left Side
    int AR,RR,BR,GR; // Right Side
    int AD1,RD1,BD1,GD1; // Delta going down on left
    int AD2,RD2,BD2,GD2; // Delta going down on right
    int AD3,RD3,BD3,GD3; // Delta going right
    
    AL=(argbTL>>24)&0xFF;
    RL=(argbTL>>16)&0xFF;
    GL=(argbTL>> 8)&0xFF;
    BL=(argbTL>> 0)&0xFF;
    
    AL<<=16; RL<<=16; GL<<=16; BL<<=16;
                
    AR=(argbTR>>24)&0xFF;
    RR=(argbTR>>16)&0xFF;
    GR=(argbTR>> 8)&0xFF;
    BR=(argbTR>> 0)&0xFF;
    
    AR<<=16; RR<<=16; GR<<=16; BR<<=16;
    
    AD1=((((argbBL>>24)&0xFF)<<16) - AL)/height;
    RD1=((((argbBL>>16)&0xFF)<<16) - RL)/height;
    GD1=((((argbBL>> 8)&0xFF)<<16) - GL)/height;
    BD1=((((argbBL>> 0)&0xFF)<<16) - BL)/height;

    AD2=((((argbBR>>24)&0xFF)<<16) - AR)/height;
    RD2=((((argbBR>>16)&0xFF)<<16) - RR)/height;
    GD2=((((argbBR>> 8)&0xFF)<<16) - GR)/height;
    BD2=((((argbBR>> 0)&0xFF)<<16) - BR)/height;
    

    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }
        
    
    // TODO: use 4 bytes unit and aligned writes...
    
    // Save multiplies by premultiplying all Y    
    
    y2=y+height;    
    y=y1;
    y*=720;
    y2*=720;
    
    
    while(y<y2)
    {        
        x=x1;
        A=AL; R=RL; G=GL; B=BL;
        AD3=(AR - AL)/width;
        RD3=(RR - RL)/width;
        GD3=(GR - GL)/width;
        BD3=(BR - BL)/width;
        
        if(y<(y1*720+thickness*720) || y>=(y2-thickness*720))
        {
            while(x<x2)
            {
                drawBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                x++;
                A+=AD3; R+=RD3; G+=GD3; B+=BD3;
            }
        }
        else
        {
            while(x<(x1+thickness))
            {
                drawBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                x++;
                A+=AD3; R+=RD3; G+=GD3; B+=BD3;
            }
            
            x=x2-thickness;
            A=AR-thickness*AD3;
            R=RR-thickness*RD3;
            G=GR-thickness*GD3;
            B=BR-thickness*BD3;
            
            while(x<x2)
            {
                drawBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                x++;
                A+=AD3; R+=RD3; G+=GD3; B+=BD3;
            }        
        }
        y+=720;
        AL+=AD1;
        RL+=RD1;
        GL+=GD1;
        BL+=BD1;
        AR+=AD2;
        RR+=RD2;
        GR+=GD2;
        BR+=BD2;        
    }    
#ifdef STBPROFILE
    t2=get_timebase();
#ifdef DEBUGRENDER    
    fprintf(stderr,"time: %d\n",(int)(t2-t1));
#endif    
    RC->rendertime+=(t2-t1);
    RC->optimes[0]+=(t2-t1);
#endif
    return;
}

void GFX_fillRect(int x, int y, int width, int height, 
    int argbTL, int argbTR, int argbBR, int argbBL)
{
#ifdef STBPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
    int i,j;
    int x1,x2,y2;
    x1=x;
    x2=x+width;
    y2=y+height;
    
    updateMinMax(x,y,width,height);

    // All in S15.16 format
    int A,R,G,B;
    int AL,RL,BL,GL; // Left Side
    int AR,RR,BR,GR; // Right Side
    int AD1,RD1,BD1,GD1; // Delta going down on left
    int AD2,RD2,BD2,GD2; // Delta going down on right
    int AD3,RD3,BD3,GD3; // Delta going right
    
    AL=(argbTL>>24)&0xFF;
    RL=(argbTL>>16)&0xFF;
    GL=(argbTL>> 8)&0xFF;
    BL=(argbTL>> 0)&0xFF;
    
    AL<<=16; RL<<=16; GL<<=16; BL<<=16;
                
    AR=(argbTR>>24)&0xFF;
    RR=(argbTR>>16)&0xFF;
    GR=(argbTR>> 8)&0xFF;
    BR=(argbTR>> 0)&0xFF;
    
    AR<<=16; RR<<=16; GR<<=16; BR<<=16;
    
    AD1=((((argbBL>>24)&0xFF)<<16) - AL)/height;
    RD1=((((argbBL>>16)&0xFF)<<16) - RL)/height;
    GD1=((((argbBL>> 8)&0xFF)<<16) - GL)/height;
    BD1=((((argbBL>> 0)&0xFF)<<16) - BL)/height;

    AD2=((((argbBR>>24)&0xFF)<<16) - AR)/height;
    RD2=((((argbBR>>16)&0xFF)<<16) - RR)/height;
    GD2=((((argbBR>> 8)&0xFF)<<16) - GR)/height;
    BD2=((((argbBR>> 0)&0xFF)<<16) - BR)/height;
        
    
#ifdef DEBUGRENDER
    fprintf(stderr,"Filling rectangle %d %d %d %d\n",x,y,width,height);
    fflush(stderr);
#endif
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }
    
    // TODO: use 4 bytes unit and aligned writes...
    
    // Save multiplies by premultiplying all Y    
    y*=720;
    y2*=720;
    
    while(y<y2)
    {
        x=x1;
        A=AL; R=RL; G=GL; B=BL;
        AD3=(AR - AL)/width;
        RD3=(RR - RL)/width;
        GD3=(GR - GL)/width;
        BD3=(BR - BL)/width;
        if(AD3==0 && RD3==0 && GD3==0 && BD3==0)
        {
            unsigned int drawcolors[4] = {
                ((A>>16)&0xFF),
                ((R>>16)&0xff),
                ((G>>16)&0xff),
                ((B>>16)&0xff) };
            unsigned int ptr;

            ptr = x+y;

            while(x<x2)
            {
                drawBlend(RC,ptr,drawcolors[1],drawcolors[2],drawcolors[3],drawcolors[0]);
                ptr++;
                x++;
            }
        }
        else
        {
            while(x<x2)
            {
                drawBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                x++;
                A+=AD3; R+=RD3; G+=GD3; B+=BD3;
            }
        }
        y+=720;
        AL+=AD1;
        RL+=RD1;
        GL+=GD1;
        BL+=BD1;
        AR+=AD2;
        RR+=RD2;
        GR+=GD2;
        BR+=BD2;        
    }    
#ifdef STBPROFILE
    t2=get_timebase();
#ifdef DEBUGRENDER    
    fprintf(stderr,"time: %d\n",(int)(t2-t1));
#endif    
    RC->rendertime+=(t2-t1);
    RC->optimes[1]+=(t2-t1);
#endif
}

void GFX_clearRect(int x, int y, int width, int height, 
    int argbTL, int argbTR, int argbBR, int argbBL)
{
#ifdef STBPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
    int i,j;
    int x1,x2,y2;
    x1=x;
    x2=x+width;
    y2=y+height;

    updateMinMax(x,y,width,height);
    
    // All in S15.16 format
    int A,R,G,B;
    int AL,RL,BL,GL; // Left Side
    int AR,RR,BR,GR; // Right Side
    int AD1,RD1,BD1,GD1; // Delta going down on left
    int AD2,RD2,BD2,GD2; // Delta going down on right
    int AD3,RD3,BD3,GD3; // Delta going right
    
    AL=(argbTL>>24)&0xFF;
    RL=(argbTL>>16)&0xFF;
    GL=(argbTL>> 8)&0xFF;
    BL=(argbTL>> 0)&0xFF;
    
    AL<<=16; RL<<=16; GL<<=16; BL<<=16;
                
    AR=(argbTR>>24)&0xFF;
    RR=(argbTR>>16)&0xFF;
    GR=(argbTR>> 8)&0xFF;
    BR=(argbTR>> 0)&0xFF;
    
    AR<<=16; RR<<=16; GR<<=16; BR<<=16;
    
    AD1=((((argbBL>>24)&0xFF)<<16) - AL)/height;
    RD1=((((argbBL>>16)&0xFF)<<16) - RL)/height;
    GD1=((((argbBL>> 8)&0xFF)<<16) - GL)/height;
    BD1=((((argbBL>> 0)&0xFF)<<16) - BL)/height;

    AD2=((((argbBR>>24)&0xFF)<<16) - AR)/height;
    RD2=((((argbBR>>16)&0xFF)<<16) - RR)/height;
    GD2=((((argbBR>> 8)&0xFF)<<16) - GR)/height;
    BD2=((((argbBR>> 0)&0xFF)<<16) - BR)/height;
        
    
#ifdef DEBUGRENDER
    fprintf(stderr,"Clearing rectangle %d %d %d %d\n",x,y,width,height);
    fflush(stderr);
#endif
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }
    
    // TODO: use 4 bytes unit and aligned writes...
    
    // Save multiplies by premultiplying all Y    
    y*=720;
    y2*=720;
    
    while(y<y2)
    {
        x=x1;
        A=AL; R=RL; G=GL; B=BL;
        AD3=(AR - AL)/width;
        RD3=(RR - RL)/width;
        GD3=(GR - GL)/width;
        BD3=(BR - BL)/width;
        if(AD3==0 && RD3==0 && GD3==0 && BD3==0)
        {
            unsigned int drawcolors[4] = {
                ((A>>16)&0xFF),
                ((R>>16)&0xff),
                ((G>>16)&0xff),
                ((B>>16)&0xff) };
            unsigned int ptr;

            ptr = x+y;

            while(x<x2)
            {
                drawNoBlend(RC,ptr,drawcolors[1],drawcolors[2],drawcolors[3],drawcolors[0]);
                ptr++;
                x++;
            }
        }
        else
        {
            while(x<x2)
            {
                drawNoBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                x++;
                A+=AD3; R+=RD3; G+=GD3; B+=BD3;
            }
        }
        y+=720;
        AL+=AD1;
        RL+=RD1;
        GL+=GD1;
        BL+=BD1;
        AR+=AD2;
        RR+=RD2;
        GR+=GD2;
        BR+=BD2;        
    }    
#ifdef STBPROFILE
    t2=get_timebase();
#ifdef DEBUGRENDER    
    fprintf(stderr,"time: %d\n",(int)(t2-t1));
#endif    
    RC->rendertime+=(t2-t1);
    RC->optimes[2]+=(t2-t1);
#endif
}

void GFX_drawOval(int x, int y, int width, int height, int thickness, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH)
{
#ifdef STBPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
    int i,j;
    int x1,y1,x2,y2;
    x1=x;
    y1=y;
    x2=x+width;
    y2=y+height;

    updateMinMax(clipX,clipY,clipW,clipH);
    int a2large=(((width+1)/2)*((width+1)/2));
    int b2large=(((height+1)/2)*((height+1)/2));
    int a2small=(((width-thickness)/2)*((width-thickness)/2));
    int b2small=(((height-thickness)/2)*((height-thickness)/2));
    int centerx=x+width/2;
    int centery=y+height/2;
    
    // All in S15.16 format
    int A,R,G,B;
    int AL,RL,GL,BL; // Left Side
    int AR,RR,GR,BR; // Right Side
    int AD1,RD1,GD1,BD1; // Delta going down on left
    int AD2,RD2,GD2,BD2; // Delta going down on right
    int AD3,RD3,GD3,BD3; // Delta going right
    int diff=0;
        
    AL=(argbTL>>24)&0xFF;
    RL=(argbTL>>16)&0xFF;
    GL=(argbTL>>8)&0xFF;
    BL=(argbTL>>0)&0xFF;
    
    AL<<=16; RL<<=16; GL<<=16; BL<<=16;
                
    AR=(argbTR>>24)&0xFF;
    RR=(argbTR>>16)&0xFF;
    GR=(argbTR>>8)&0xFF;
    BR=(argbTR>>0)&0xFF;
    
    AR<<=16; RR<<=16; GR<<=16; BR<<=16;
    
    AD1=((((argbBL>>24)&0xFF)<<16) - AL)/height;
    RD1=((((argbBL>>16)&0xFF)<<16) - RL)/height;
    GD1=((((argbBL>>8)&0xFF)<<16) - GL)/height;
    BD1=((((argbBL>>0)&0xFF)<<16) - BL)/height;
    
    AD2=((((argbBR>>24)&0xFF)<<16) - AR)/height;
    RD2=((((argbBR>>16)&0xFF)<<16) - RR)/height;
    GD2=((((argbBR>>8)&0xFF)<<16) - GR)/height;
    BD2=((((argbBR>>0)&0xFF)<<16) - BR)/height;
        
    
//#ifdef DEBUGRENDER
    fprintf(stderr,"Drawing oval %d %d %d %d %d %d %d %d\n",
        x,y,width,height,clipX,clipY,clipW,clipH);
    fprintf(stderr,"%08X %08X %08X %08X\n",argbTL, argbTR, argbBR, argbBL);
    fflush(stderr);
//#endif
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }
    
    // TODO: use 4 bytes unit and aligned writes...
    
    // Save multiplies by premultiplying all Y    
    y*=720;
    y2*=720;
    
    while(y<y2)
    {
        x=x1;
        A=AL; R=RL; G=GL; B=BL;
        AD3=(AR - AL)/width;
        RD3=(RR - RL)/width;
        GD3=(GR - GL)/width;
        BD3=(BR - BL)/width;
        if(y>=clipY*720 && y<(clipY+clipH)*720 && y>=0 && y<RC->surfaceheight*720)
        {
            int cy=(y/720-centery)*(y/720-centery)*65536;
            x=x1;
            x2=x+width;
            while(x<x2)
            {
                if(x>=clipX && x<(clipX+clipW))
                {
                    int cx=(x-centerx)*(x-centerx)*65536;
                    if((cx/a2large+
                        cy/b2large <= 65536) &&
                       (cx/a2small+
                        cy/b2small >= 65536))
                        drawBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                }
                x++;
                A+=AD3; R+=RD3; G+=GD3; B+=BD3;
            }
        }
        y+=720;
        AL+=AD1;
        RL+=RD1;
        GL+=GD1;
        BL+=BD1;
        AR+=AD2;
        RR+=RD2;
        GR+=GD2;
        BR+=BD2;        
    }    
#ifdef STBPROFILE
    t2=get_timebase();
#ifdef DEBUGRENDER    
    fprintf(stderr,"time: %d\n",(int)(t2-t1));
#endif    
    RC->rendertime+=(t2-t1);
    RC->optimes[3]+=(t2-t1);
#endif
}

void GFX_fillOval(int x, int y, int width, int height, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH)
{
#ifdef STBPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
    int i,j;
    int x1,y1,x2,y2;
    x1=x;
    y1=y;
    x2=x+width;
    y2=y+height;
    
    updateMinMax(clipX,clipY,clipW,clipH);

    int a2large=((width/2)*(width/2));
    int b2large=((height/2)*(height/2));
    int centerx=x+width/2;
    int centery=y+height/2;
    
    // All in S15.16 format
    int A,R,G,B;
    int AL,RL,GL,BL; // Left Side
    int AR,RR,GR,BR; // Right Side
    int AD1,RD1,GD1,BD1; // Delta going down on left
    int AD2,RD2,GD2,BD2; // Delta going down on right
    int AD3,RD3,GD3,BD3; // Delta going right
    int diff=0;
        
    AL=(argbTL>>24)&0xFF;
    RL=(argbTL>>16)&0xFF;
    GL=(argbTL>>8)&0xFF;
    BL=(argbTL>>0)&0xFF;
    
    AL<<=16; RL<<=16; GL<<=16; BL<<=16;
                
    AR=(argbTR>>24)&0xFF;
    RR=(argbTR>>16)&0xFF;
    GR=(argbTR>>8)&0xFF;
    BR=(argbTR>>0)&0xFF;
    
    AR<<=16; RR<<=16; GR<<=16; BR<<=16;
    
    AD1=((((argbBL>>24)&0xFF)<<16) - AL)/height;
    RD1=((((argbBL>>16)&0xFF)<<16) - RL)/height;
    GD1=((((argbBL>>8)&0xFF)<<16) - GL)/height;
    BD1=((((argbBL>>0)&0xFF)<<16) - BL)/height;
    
    AD2=((((argbBR>>24)&0xFF)<<16) - AR)/height;
    RD2=((((argbBR>>16)&0xFF)<<16) - RR)/height;
    GD2=((((argbBR>>8)&0xFF)<<16) - GR)/height;
    BD2=((((argbBR>>0)&0xFF)<<16) - BR)/height;
        
    
//#ifdef DEBUGRENDER
    fprintf(stderr,"Drawing oval %d %d %d %d %d %d %d %d\n",
        x,y,width,height,clipX,clipY,clipW,clipH);
    fprintf(stderr,"%08X %08X %08X %08X\n",argbTL, argbTR, argbBR, argbBL);
    fflush(stderr);
//#endif
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }
    
    // TODO: use 4 bytes unit and aligned writes...
    
    // Save multiplies by premultiplying all Y    
    y*=720;
    y2*=720;
    
    while(y<y2)
    {
        x=x1;
        A=AL; R=RL; G=GL; B=BL;
        AD3=(AR - AL)/width;
        RD3=(RR - RL)/width;
        GD3=(GR - GL)/width;
        BD3=(BR - BL)/width;
        if(y>=clipY*720 && y<(clipY+clipH)*720 && y>=0 && y<RC->surfaceheight*720)
        {
            int cy=(y/720-centery)*(y/720-centery)*65536;
            x=x1;
            x2=x+width;
            while(x<x2)
            {
                if(x>=clipX && x<(clipX+clipW))
                {
                    int cx=(x-centerx)*(x-centerx)*65536;
                    if((cx/a2large+
                        cy/b2large <= 65536))
                        drawBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                }
                x++;
                A+=AD3; R+=RD3; G+=GD3; B+=BD3;
            }
        }
        y+=720;
        AL+=AD1;
        RL+=RD1;
        GL+=GD1;
        BL+=BD1;
        AR+=AD2;
        RR+=RD2;
        GR+=GD2;
        BR+=BD2;        
    }    
#ifdef STBPROFILE
    t2=get_timebase();
#ifdef DEBUGRENDER    
    fprintf(stderr,"time: %d\n",(int)(t2-t1));
#endif    
    RC->rendertime+=(t2-t1);
    RC->optimes[4]+=(t2-t1);
#endif
}

static unsigned char arcslen[] = 
{
0, 22, 31, 39, 45, 50, 55, 59, 63, 67, 70, 74, 77, 80, 83, 86, 89, 91, 94, 96, 99, 101, 103, 106, 108, 110, 112, 114, 116, 118, 120, 122, 123, 125, 127, 129, 130, 132, 134, 135, 137, 138, 140, 142, 143, 144, 146, 147, 149, 150, 151, 153, 154, 155, 157, 158, 159, 161, 162, 163, 164, 165, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 179, 180, 181, 182, 183, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 194, 195, 196, 197, 198, 199, 199, 200, 201, 202, 202, 203, 204, 205, 205, 206, 207, 208, 208, 209, 210, 210, 211, 212, 213, 213, 214, 214, 215, 216, 216, 217, 218, 218, 219, 219, 220, 221, 221, 222, 222, 223, 223, 224, 225, 225, 226, 226, 227, 227, 228, 228, 229, 229, 230, 230, 231, 231, 232, 232, 233, 233, 233, 234, 234, 235, 235, 236, 236, 236, 237, 237, 238, 238, 238, 239, 239, 240, 240, 240, 241, 241, 241, 242, 242, 242, 243, 243, 243, 244, 244, 244, 245, 245, 245, 245, 246, 246, 246, 247, 247, 247, 247, 248, 248, 248, 248, 249, 249, 249, 249, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 252, 252, 252, 252, 252, 252, 253, 253, 253, 253, 253, 253, 253, 253, 254, 254, 254, 254, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255
};

void GFX_drawRoundRect(int x, int y, int width, int height, 
    int thickness, int arcRadius, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH)
{
#ifdef STBPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
    int i,j;
    int x1,x2,y1,y2;
    int diff=0;
    int diff2=0;
    y1=y;
    x1=x;
    x2=x+width;
    // All in S15.16 format
    int A,R,G,B;
    int AL,RL,GL,BL; // Left Side
    int AR,RR,GR,BR; // Right Side
    int AD1,RD1,GD1,BD1; // Delta going down on left
    int AD2,RD2,GD2,BD2; // Delta going down on right
    int AD3,RD3,GD3,BD3; // Delta going right
    
    updateMinMax(clipX,clipY,clipW,clipH);
    
    AL=(argbTL>>24)&0xFF;
    RL=(argbTL>>16)&0xFF;
    GL=(argbTL>> 8)&0xFF;
    BL=(argbTL>> 0)&0xFF;
    
    AL<<=16; RL<<=16; GL<<=16; BL<<=16;
                
    AR=(argbTR>>24)&0xFF;
    RR=(argbTR>>16)&0xFF;
    GR=(argbTR>> 8)&0xFF;
    BR=(argbTR>> 0)&0xFF;
    
    AR<<=16; RR<<=16; GR<<=16; BR<<=16;
    
    AD1=((((argbBL>>24)&0xFF)<<16) - AL)/height;
    RD1=((((argbBL>>16)&0xFF)<<16) - RL)/height;
    GD1=((((argbBL>> 8)&0xFF)<<16) - GL)/height;
    BD1=((((argbBL>> 0)&0xFF)<<16) - BL)/height;

    AD2=((((argbBR>>24)&0xFF)<<16) - AR)/height;
    RD2=((((argbBR>>16)&0xFF)<<16) - RR)/height;
    GD2=((((argbBR>> 8)&0xFF)<<16) - GR)/height;
    BD2=((((argbBR>> 0)&0xFF)<<16) - BR)/height;
    

#ifdef DEBUGRENDER
    fprintf(stderr,"Drawing round rectangle %d %d %d %d %d %d %d %d %08X %08X %08X %08X\n",
        x,y,width,height,clipX,clipY,clipW,clipH, argbTL, argbTR, argbBR, argbBL);
    fflush(stderr);
#endif
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }
        
    // TODO: use 4 bytes unit and aligned writes...
    
    // Save multiplies by premultiplying all Y    
    
    y2=y+height;
    y=y1;
    y*=720;
    y2*=720;
    
    
    while(y<y2)
    {
        A=AL; R=RL; G=GL; B=BL;
        AD3=(AR - AL)/width;
        RD3=(RR - RL)/width;
        GD3=(GR - GL)/width;
        BD3=(BR - BL)/width;
        if(y>=clipY*720 && y<(clipY+clipH)*720 && y>=0 && y<RC->surfaceheight*720)
        {
            x=x1;
            x2=x+width;
            if(y<(y1+arcRadius)*720 || y>(y1+height-arcRadius)*720)
            {
                diff=(y<(y1+arcRadius)*720) ? y/720-y1 : (y2/720)-(y/720);
                diff=arcRadius-arcslen[(diff+1)*255/arcRadius-1]*arcRadius/255;
                
//                diff=arcRadius-sqrtf(arcRadius*arcRadius-(arcRadius-diff)*(arcRadius-diff));
                x+=diff;
                A+=AD3*diff; R+=RD3*diff;
                G+=GD3*diff; B+=BD3*diff;
                x2-=diff+1;
            }
            else
            {
                diff=0;
            }
            {
                while(x<(x1+diff+thickness))
                {
                    if(x>=clipX && x<(clipX+clipW))
                    {
                        drawBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                    }
                    x++;
                    A+=AD3; R+=RD3; G+=GD3; B+=BD3;
                }
                
                
                if(y<(y1*720+thickness*720) || y>=(y2-thickness*720))
                {
                    while(x<x2)
                    {
                        if(x>=clipX && x<(clipX+clipW))
                        {
                            drawBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                        }
                        x++;
                        A+=AD3; R+=RD3; G+=GD3; B+=BD3;
                    }
                }
                else
                {
                    x=x2-thickness; // diff already subtracted
                    A=AR-(thickness+diff)*AD3;
                    R=RR-(thickness+diff)*RD3;
                    G=GR-(thickness+diff)*GD3;
                    B=BR-(thickness+diff)*BD3;
                }

                while(x<x2)
                {
                    if(x>=clipX && x<(clipX+clipW))
                    {
                        drawBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                    }
                    x++;
                    A+=AD3; R+=RD3; G+=GD3; B+=BD3;
                }
            }
        }
        y+=720;
        AL+=AD1;
        RL+=RD1;
        GL+=GD1;
        BL+=BD1;
        AR+=AD2;
        RR+=RD2;
        GR+=GD2;
        BR+=BD2;        
    }    
#ifdef STBPROFILE
    t2=get_timebase();
#ifdef DEBUGRENDER    
    fprintf(stderr,"time: %d\n",(int)(t2-t1));
#endif    
    RC->rendertime+=(t2-t1);
    RC->optimes[5]+=(t2-t1);
#endif
    /*GFX_drawOval(x1,y1,width,height,thickness, 
        argbTL, argbTR, argbBR, argbBL,
        clipX, clipY, clipW, clipH);*/
        
    return;
}

void GFX_fillRoundRect(int x, int y, int width, int height, int arcRadius, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH)
{
#ifdef STBPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
    int i,j;
    int x1,y1,x2,y2;
    int y3=y;
    int y4;
    x1=x;
    y1=y;
    x2=x+width;
    y2=y+height;
    y4=y2;

    updateMinMax(clipX,clipY,clipW,clipH);
    // All in S15.16 format
    int A,R,G,B;
    int AL,RL,GL,BL; // Left Side
    int AR,RR,GR,BR; // Right Side
    int AD1,RD1,GD1,BD1; // Delta going down on left
    int AD2,RD2,GD2,BD2; // Delta going down on right
    int AD3,RD3,GD3,BD3; // Delta going right
    int diff=0;
        
    AL=(argbTL>>24)&0xFF;
    RL=(argbTL>>16)&0xFF;
    GL=(argbTL>>8)&0xFF;
    BL=(argbTL>>0)&0xFF;
    
    AL<<=16; RL<<=16; GL<<=16; BL<<=16;
                
    AR=(argbTR>>24)&0xFF;
    RR=(argbTR>>16)&0xFF;
    GR=(argbTR>>8)&0xFF;
    BR=(argbTR>>0)&0xFF;
    
    AR<<=16; RR<<=16; GR<<=16; BR<<=16;
    
    AD1=((((argbBL>>24)&0xFF)<<16) - AL)/height;
    RD1=((((argbBL>>16)&0xFF)<<16) - RL)/height;
    GD1=((((argbBL>>8)&0xFF)<<16) - GL)/height;
    BD1=((((argbBL>>0)&0xFF)<<16) - BL)/height;
    
    AD2=((((argbBR>>24)&0xFF)<<16) - AR)/height;
    RD2=((((argbBR>>16)&0xFF)<<16) - RR)/height;
    GD2=((((argbBR>>8)&0xFF)<<16) - GR)/height;
    BD2=((((argbBR>>0)&0xFF)<<16) - BR)/height;
        
    
#ifdef DEBUGRENDER
    fprintf(stderr,"Filling round rectangle %d %d %d %d %d %d %d %d\n",
        x,y,width,height,clipX,clipY,clipW,clipH);
    fprintf(stderr,"%08X %08X %08X %08X\n",argbTL, argbTR, argbBR, argbBL);
    fflush(stderr);
#endif
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }
    
    // TODO: use 4 bytes unit and aligned writes...
    
    // Save multiplies by premultiplying all Y    
    y*=720;
    y2*=720;
    
    while(y<y2)
    {
        x=x1;
        A=AL; R=RL; G=GL; B=BL;
        AD3=(AR - AL)/width;
        RD3=(RR - RL)/width;
        GD3=(GR - GL)/width;
        BD3=(BR - BL)/width;
        if(AD3==0 && RD3==0 && GD3==0 && BD3==0)
        {
            unsigned int drawcolors[4] = {
                ((A>>16)&0xFF),
                ((R>>16)&0xff),
                ((G>>16)&0xff),
                ((B>>16)&0xff) };
            unsigned int ptr;

            if(y3>=clipY && y3<(clipY+clipH) && y>=0 && y<RC->surfaceheight*720)
            {
                x=x1;
                x2=x+width;
                if(y3<(y1+arcRadius) || y3>(y1+height-arcRadius))
                {
                    diff=(y3<(y1+arcRadius)) ? y3-y1 : (y4)-(y3);
                    diff=arcRadius-arcslen[diff*256/arcRadius]*arcRadius/256;
    //                diff=arcRadius-sqrtf(arcRadius*arcRadius-(arcRadius-diff)*(arcRadius-diff));
                    x+=diff;
                    x2-=diff;
                }
                
                if(x<clipX)
                {
                    x+=(clipX-x);
                }
                if(x2>(clipX+clipW))
                {
                    x2=(clipX+clipW);
                }
                
                ptr = x+y;

                while(x<x2)
                {
                    drawBlend(RC,ptr,drawcolors[1],drawcolors[2],drawcolors[3],drawcolors[0]);
                    ptr++;
                    x++;
                }
            }
        
        }
        else
        {
            if(y>=clipY*720 && y<(clipY+clipH)*720 && y>=0 && y<RC->surfaceheight*720)
            {
                x=x1;
                x2=x+width;
                if(y3<(y1+arcRadius) || y3>(y1+height-arcRadius))
                {
                    diff=(y3<(y1+arcRadius)) ? y3-y1 : (y4)-(y3);
                    diff=arcRadius-arcslen[diff*256/arcRadius]*arcRadius/256;
    //                diff=arcRadius-sqrtf(arcRadius*arcRadius-(arcRadius-diff)*(arcRadius-diff));
                    x+=diff;
                    A+=AD3*diff; R+=RD3*diff;
                    G+=GD3*diff; B+=BD3*diff;
                    x2-=diff;
                }
                else
                {
                    diff=0;
                }
                
                if(x<clipX)
                {
                    A+=AD3*(clipX-x); R+=RD3*(clipX-x); G+=GD3*(clipX-x); B+=BD3*(clipX-x);
                    x+=(clipX-x);
                }
                if(x2>(clipX+clipW))
                {
                    x2=(clipX+clipW);
                }
                
                while(x<x2)
                {
                    drawBlend(RC,x+y,R>>16,G>>16,B>>16,A>>16);
                    x++;
                    A+=AD3; R+=RD3; G+=GD3; B+=BD3;
                }
            }
        }
        y+=720;
        y3+=1;
        AL+=AD1;
        RL+=RD1;
        GL+=GD1;
        BL+=BD1;
        AR+=AD2;
        RR+=RD2;
        GR+=GD2;
        BR+=BD2;        
    }    
#ifdef STBPROFILE
    t2=get_timebase();
#ifdef DEBUGRENDER    
    fprintf(stderr,"time: %d\n",(int)(t2-t1));
#endif    
    RC->rendertime+=(t2-t1);
    RC->optimes[6]+=(t2-t1);
#endif
}


// TODO: Verify that some more, it has been a while since I have done line drawing in software
void GFX_drawLine(int x1, int y1, int x2, int y2, int argb1, int argb2)
{
#ifdef STBPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
    unsigned char r1,g1,b1,a1;
    unsigned char r2,g2,b2,a2;
    unsigned int r,g,b,a;
    int i,j;
    int m,x,y;
    int dx,dy,sx,sy;
    int red1,green1,blue1,alpha1;
    a1=(argb1>>24)&0xFF;
    r1=(argb1>>16)&0xFF;
    g1=(argb1>> 8)&0xFF;
    b1=(argb1>> 0)&0xFF;
    
    
    dx=x2-x1;
    dy=y2-y1;
    
    sx=1;
    sy=1;
    
    if(dx<0)
    {
        dx*=-1;
        sx=-1;
    }

    if(dy<0)
    {
        dy*=-1;
        sy=-1;
    }
    
    // So we can have 1 bit of extra precision...
    dx*=2;
    dy*=2;
    
#ifdef DEBUGRENDER    
    fprintf(stderr,"Drawing line %d %d %d %d\n", x1,y1,x2,y2);
    fflush(stderr);
#endif
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }
    
    // Save multiplies by premultiplying all Y    
    y1*=720;
    y2*=720;
    sy*=720;

    r=r1;
    g=g1;
    b=b1;
    a=a1;
    
    drawBlend(RC,x1+y1,r,g,b,a);

    if(dx > dy) // Slope is < 1
    {
        int d = dy - dx/2;
        while(x1 != x2)
        {
            if(d>=0)
            {
                y1+=sy;
                d-=dx;                
            }
            x1+=sx;
            d+=dy;
            drawBlend(RC,x1+y1,r,g,b,a);
        }        
    }
    else // Slope is >1
    {
        int d = dx - dy/2;
        while(y1 != y2)
        {
            if(d>=0)
            {
                x1+=sx;
                d-=dy;                
            }
            y1+=sy;
            d+=dx;
            drawBlend(RC,x1+y1,r,g,b,a);
        }        
        
    }    
#ifdef STBPROFILE
    t2=get_timebase();
#ifdef DEBUGRENDER    
    fprintf(stderr,"time: %d\n",(int)(t2-t1));
#endif    
    RC->rendertime+=(t2-t1);
#endif
}

int GFX_loadImage(int width, int height, int format)
{
    STBImage_t *newimage;
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return 0;
    }
    
    fprintf(stderr,"Trying to create image of size %dx%d\n",width,height);
    fflush(stderr);

    newimage=(STBImage_t *) malloc(sizeof(STBImage_t));
    if(newimage==NULL)
    {
        fprintf(stderr,"Couldn't allocate STBImage_t for new image\n");
        fflush(stderr);
        return 0;
    }
    
    newimage->hasAlpha=1;
    newimage->uBytePerLine=width*4;
    newimage->pPlane = mspace_malloc(RC->texturespace, width*height*4);
    
    if(newimage->pPlane == 0)
    {
        fprintf(stderr,"Couldn't create surface for new image\n");
        fflush(stderr);
        free(newimage);
        return 0;
    }
    fprintf(stderr,"Allocated image at %X\n",newimage->pPlane);
    fflush(stderr);

    return (int) newimage;
}

void GFX_loadImageLine(int handle, int line, int len, unsigned char *buffer)
{
    int i;
    unsigned char y1,u1,v1;
    unsigned char y2,u2,v2;
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }

    if(handle==0) return;

    STBImage_t *img=(STBImage_t *) (unsigned int) handle;

    memcpy(img->pPlane+line*img->uBytePerLine, buffer, len);
}

void GFX_unloadImage(int imghandle)
{
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }
    
    if(imghandle!=0)
    {
        STBImage_t *newimage=(STBImage_t *) (unsigned int) imghandle;
        fprintf(stderr,"Trying to release image handle %d\n",imghandle);
        fflush(stderr);
        mspace_free(RC->texturespace, newimage->pPlane);
        free(newimage);        
    }
    return;
}

void GFX_drawTexturedRect(int x, int y, int width, int height, int handle,
    int srcx, int srcy, int srcwidth, int srcheight, int blend)
{
#ifdef STBPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
    unsigned int blendred,blendgreen,blendblue,blendalpha;
    unsigned int br, bg, bb, ba;
        
    if(handle==0) return;
    STBImage_t *srcimage=(STBImage_t *) (unsigned int) handle;
    int x1,x2,y2;
    x1=x;
    x2=x+srcwidth;
    y2=y+srcheight;

    // width can be negative for text...
    updateMinMax(x,y,srcwidth,srcheight);
                
#ifdef DEBUGRENDER
    fprintf(stderr,"Drawing textured %d %d %d %d %d %d %d %d\n",x,y,width,height, 
        srcx, srcy, srcwidth, srcheight);
    fflush(stderr);
#endif
    
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr,"Invalid context\n");
        fflush(stderr);
#endif
        return;
    }
    
    blendalpha=(blend >> 24)&0xFF;
    blendred=(blend >> 16)&0xFF;
    blendgreen=(blend >> 8)&0xFF;
    blendblue=(blend >> 0)&0xFF;
    br=blendred<<8;
    bg=blendgreen<<8;
    bb=blendblue<<8;
    ba=blendalpha<<8;
    
    // TODO: use 4 bytes unit and aligned writes...
    
    // Save multiplies by premultiplying all Y    
    y*=720;
    y2*=720;

    if(width>=0) // not font
    {
        if(blendalpha==0xFF)
        {
            unsigned int *texture = (unsigned int *) srcimage->pPlane;
        
            unsigned int texturepitch = srcimage->uBytePerLine/4;
        
            unsigned int *screen = (unsigned int *) RC->workbuffer;
        
            texture+=texturepitch*srcy+srcx;
        
            while(y<y2)
            {
                x=x1;
                while(x<x2)
                {
                    unsigned int t;
                    t=*((unsigned int *)texture);
                    unsigned int ta=t>>16;
                    unsigned int inva=65535-(ta);
                    
                    int i;
                    int offset=x+y;
                    unsigned int data=screen[offset];
                    
                    unsigned int tmp;
                    
                    // g
                    tmp=t<<16;
//                    mullhwu(tmp,t,ta);
                    maclhwsu(tmp,data,inva);
                    rlwimi(data,tmp);
                    rlw(t,t,8);
                    rlw(data,data,8);

                    // b
                    tmp=t<<16;
//                    mullhwu(tmp,t,ta);
                    maclhwsu(tmp,data,inva);
                    rlwimi(data,tmp);
                    rlw(t,t,8);
                    rlw(data,data,8);
                    
                    // a
                    tmp=t<<16;
//                    mullhwu(tmp,t,ta);
                    maclhwsu(tmp,data,inva);
                    rlwimi(data,tmp);
                    rlw(t,t,8);
                    rlw(data,data,8);
                    
                    // r                    
                    tmp=t<<16;
//                    mullhwu(tmp,t,ta);
                    maclhwsu(tmp,data,inva);
                    rlwimi(data,tmp);
                    rlw(t,t,8);
                    rlw(data,data,8);
                    
                    
                    screen[offset]=data;
                    
                    texture+=1;
                    x++;
                }
                texture+=texturepitch-width;
                y+=720;
            }    
        }
        else // We need to use blend color...
        {
            unsigned int *texture = (unsigned int *) srcimage->pPlane;
        
            unsigned int texturepitch = srcimage->uBytePerLine/4;
        
            unsigned int *screen = (unsigned int *) RC->workbuffer;
        
            texture+=texturepitch*srcy+srcx;
        
            while(y<y2)
            {
                x=x1;
                while(x<x2)
                {
                    unsigned int t;
                    t=*((unsigned int *)texture);
                    
                    unsigned int ta=t>>16;
                    
                    ta*=ba;
                    ta>>=16;
                    
                    unsigned int inva=65535-(ta);
                    
                    int i;
                    int offset=x+y;
                    unsigned int data=screen[offset];
                    
                    unsigned int tmp, tmpc;
                    
                    // g
                    //tmp=t<<16;
                    mullhwu(tmp,t,ba);
                    maclhwsu(tmp,data,inva);
                    rlwimi(data,tmp);
                    rlw(t,t,8);
                    rlw(data,data,8);

                    // b
//                    tmp=t<<16;
                    mullhwu(tmp,t,ba);
                    maclhwsu(tmp,data,inva);
                    rlwimi(data,tmp);
                    rlw(t,t,8);
                    rlw(data,data,8);
                    
                    // a
//                    tmp=t<<16;
                    mullhwu(tmp,t,ba);
                    maclhwsu(tmp,data,inva);
                    rlwimi(data,tmp);
                    rlw(t,t,8);
                    rlw(data,data,8);
                    
                    // r                    
//                    tmp=t<<16;
                    mullhwu(tmp,t,ba);
                    maclhwsu(tmp,data,inva);
                    rlwimi(data,tmp);
                    rlw(t,t,8);
                    rlw(data,data,8);
                    
                    screen[offset]=data;
                    
                    texture+=1;
                    x++;
                }
                texture+=texturepitch-width;
                y+=720;
            }    
        
        }
#ifdef STBPROFILE
    t2=get_timebase();
#ifdef DEBUGRENDER    
    fprintf(stderr,"time: %d\n",(int)(t2-t1));
#endif    
    RC->rendertime+=(t2-t1);
    RC->optimes[7]+=(t2-t1);
#endif    
    }
    else // fonts rendering
    {
        int red,green,blue,alpha;
        unsigned int *texture = (unsigned int *) srcimage->pPlane;
        unsigned int texturepitch = srcimage->uBytePerLine/4;
        
        width=-width; // We don't use it anyway but ...
        
        texture+=texturepitch*srcy+srcx;
    
        alpha=(blend >> 24)&0xFF;
        red=(blend >> 16)&0xFF;
        green=(blend >> 8)&0xFF;
        blue=(blend >> 0)&0xFF;
        
        while(y<y2)
        {
            x=x1;
            while(x<x2)
            {
                if(*texture)
                    drawBlend(RC,
                        x+y,
                        (red * (*texture>>24))>>8,(green * (*texture>>24))>>8,(blue * (*texture>>24))>>8,
                        ((*texture>>24) * alpha)>>8);
                texture+=1;
                x++;
            }
            texture+=texturepitch-srcwidth;
            y+=720;
        }    
#ifdef STBPROFILE
    t2=get_timebase();
#ifdef DEBUGRENDER    
    fprintf(stderr,"time: %d\n",(int)(t2-t1));
#endif    
    RC->rendertime+=(t2-t1);
    RC->optimes[8]+=(t2-t1);
#endif    
    }
}

void GFX_drawText(int x, int y, int len, short *text, int handle, int argb,
    int clipX, int clipY, int clipW, int clipH)
{
}

int GFX_loadFont(char *name, int style, int size)
{
    return (int) 0;
}

void GFX_unloadFont(int handle)
{
}

void GFX_SetMode(int mode)
{
    fprintf(stderr,"Setting mode %d\n",mode);
    switch(mode)
    {
        case 0: // 480i
            break;
        case 1: // 576i
            break;
        default:
            fprintf(stderr,"unknown mode %d\n",mode);
            break;
    }
}

int GFX_createSurface(int width, int height)
{
    return 0;
}

void GFX_SetTargetSurface(int surface)
{
}

int GFX_SetAspect(int aspect)
{
    if(RC!=NULL)
    {
        return (RC->surfaceheight<<16)|720;
    }
    return 0;
}

int GFX_PrepImage(int width, int height)
{
    return 0;
}

int GFXCMD_LoadImageCompressed(int handle, int len, int socket, unsigned char *buffer,
    int bufferlevel, int buffersize, int bufferoffset)
{
    return 0;
}

int GFX_SetVideoProp(int mode, int sx, int sy, int swidth, int sheight,
    int ox, int oy, int owidth, int oheight, int alpha, int activewin)
{
    return 0;
}
