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
#include "gfxcmd.h"
#include "gfxcalls.h"

extern unsigned char gfxcmdbuffer[];

static int readInt(int pos, unsigned char *cmddata)
{
    return (cmddata[pos+0]<<24)|(cmddata[pos+1]<<16)|(cmddata[pos+2]<<8)|(cmddata[pos+3]);
}

static short readShort(int pos, unsigned char *cmddata)
{
    return (cmddata[pos+0]<<8)|(cmddata[pos+1]);
}

int ExecuteGFXCommand(int cmd, int len, unsigned char *cmddata, int *hasret, int sd)
{
    *hasret=0; // Nothing to return by default
    
    switch(cmd)
    {
        case GFXCMD_INIT:
            *hasret=1;
            return GFX_init(readInt(0, cmddata));
            break;
        case GFXCMD_DEINIT:
            GFX_deinit();
            break;
        case GFXCMD_DRAWRECT:
            if(len==36)
            {
                int x, y, width, height, thickness, 
                    argbTL, argbTR, argbBR, argbBL;                                
                x=readInt(0, cmddata);
                y=readInt(4, cmddata);
                width=readInt(8, cmddata);
                height=readInt(12, cmddata);
                thickness=readInt(16, cmddata);
                argbTL=readInt(20, cmddata);
                argbTR=readInt(24, cmddata);
                argbBR=readInt(28, cmddata);
                argbBL=readInt(32, cmddata);                
                GFX_drawRect(x, y, width, height, 
                    thickness, argbTL, argbTR, argbBR, argbBL);
            }
            else
            {
                printf("Invalid len for GFXCMD_DRAWRECT : %d\n",len);
            }
            break;
        case GFXCMD_FILLRECT:
            // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL
            if(len==32)
            {
                int x, y, width, height, thickness, 
                    argbTL, argbTR, argbBR, argbBL;                                
                x=readInt(0, cmddata);
                y=readInt(4, cmddata);
                width=readInt(8, cmddata);
                height=readInt(12, cmddata);
                argbTL=readInt(16, cmddata);
                argbTR=readInt(20, cmddata);
                argbBR=readInt(24, cmddata);
                argbBL=readInt(28, cmddata);                
                GFX_fillRect(x, y, width, height, 
                    argbTL, argbTR, argbBR, argbBL);
            }
            else
            {
                printf("Invalid len for GFXCMD_FILLRECT : %d\n",len);
            }
            break;
        case GFXCMD_CLEARRECT:
            // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL
            if(len==32)
            {
                int x, y, width, height, thickness, 
                    argbTL, argbTR, argbBR, argbBL;                                
                x=readInt(0, cmddata);
                y=readInt(4, cmddata);
                width=readInt(8, cmddata);
                height=readInt(12, cmddata);
                argbTL=readInt(16, cmddata);
                argbTR=readInt(20, cmddata);
                argbBR=readInt(24, cmddata);
                argbBL=readInt(28, cmddata);                
                GFX_clearRect(x, y, width, height, 
                    argbTL, argbTR, argbBR, argbBL);
            }
            else
            {
                printf("Invalid len for GFXCMD_CLEARRECT : %d\n",len);
            }            
            break;
        case GFXCMD_DRAWOVAL:
            // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL, 
            // clipX, clipY, clipW, clipH
            if(len==52)
            {
                int x, y, width, height, thickness,
                    argbTL, argbTR, argbBR, argbBL,
                    clipX, clipY, clipW, clipH;
                x=readInt(0, cmddata);
                y=readInt(4, cmddata);
                width=readInt(8, cmddata);
                height=readInt(12, cmddata);
                thickness=readInt(16, cmddata);
                argbTL=readInt(20, cmddata);
                argbTR=readInt(24, cmddata);
                argbBR=readInt(28, cmddata);
                argbBL=readInt(32, cmddata);                
                clipX=readInt(36, cmddata);
                clipY=readInt(40, cmddata);
                clipW=readInt(44, cmddata);
                clipH=readInt(48, cmddata);
                GFX_drawOval(x, y, width, height, thickness, 
                    argbTL, argbTR, argbBR, argbBL,
                    clipX, clipY, clipW, clipH);
            }
            else
            {
                printf("Invalid len for GFXCMD_DRAWOVAL : %d\n",len);
            }
            
            break;
        case GFXCMD_FILLOVAL:
            // x, y, width, height, argbTL, argbTR, argbBR, argbBL, 
            // clipX, clipY, clipW, clipH
            if(len==48)
            {
                int x, y, width, height,
                    argbTL, argbTR, argbBR, argbBL,
                    clipX, clipY, clipW, clipH;
                x=readInt(0, cmddata);
                y=readInt(4, cmddata);
                width=readInt(8, cmddata);
                height=readInt(12, cmddata);
                argbTL=readInt(16, cmddata);
                argbTR=readInt(20, cmddata);
                argbBR=readInt(24, cmddata);
                argbBL=readInt(28, cmddata);                
                clipX=readInt(32, cmddata);
                clipY=readInt(36, cmddata);
                clipW=readInt(40, cmddata);
                clipH=readInt(44, cmddata);
                GFX_fillOval(x, y, width, height,
                    argbTL, argbTR, argbBR, argbBL,
                    clipX, clipY, clipW, clipH);
            }
            else
            {
                printf("Invalid len for GFXCMD_FILLOVAL : %d\n",len);
            }
            break;
        case GFXCMD_DRAWROUNDRECT:
            // x, y, width, height, thickness, arcRadius, argbTL, argbTR, argbBR, argbBL,
            // clipX, clipY, clipW, clipH
            if(len==56)
            {
                int x, y, width, height, thickness, arcRadius,
                    argbTL, argbTR, argbBR, argbBL,
                    clipX, clipY, clipW, clipH;
                x=readInt(0, cmddata);
                y=readInt(4, cmddata);
                width=readInt(8, cmddata);
                height=readInt(12, cmddata);
                thickness=readInt(16, cmddata);
                arcRadius=readInt(20, cmddata);
                argbTL=readInt(24, cmddata);
                argbTR=readInt(28, cmddata);
                argbBR=readInt(32, cmddata);
                argbBL=readInt(36, cmddata);                
                clipX=readInt(40, cmddata);
                clipY=readInt(44, cmddata);
                clipW=readInt(48, cmddata);
                clipH=readInt(52, cmddata);
                GFX_drawRoundRect(x, y, width, height,
                    thickness, arcRadius, 
                    argbTL, argbTR, argbBR, argbBL,
                    clipX, clipY, clipW, clipH);
            }
            else
            {
                printf("Invalid len for GFXCMD_DRAWROUNDRECT : %d\n",len);
            }
            break;
        case GFXCMD_FILLROUNDRECT:
            // x, y, width, height, arcRadius, argbTL, argbTR, argbBR, argbBL,
            // clipX, clipY, clipW, clipH
            if(len==52)
            {
                int x, y, width, height, arcRadius,
                    argbTL, argbTR, argbBR, argbBL,
                    clipX, clipY, clipW, clipH;
                x=readInt(0, cmddata);
                y=readInt(4, cmddata);
                width=readInt(8, cmddata);
                height=readInt(12, cmddata);
                arcRadius=readInt(16, cmddata);
                argbTL=readInt(20, cmddata);
                argbTR=readInt(24, cmddata);
                argbBR=readInt(28, cmddata);
                argbBL=readInt(32, cmddata);                
                clipX=readInt(36, cmddata);
                clipY=readInt(40, cmddata);
                clipW=readInt(44, cmddata);
                clipH=readInt(48, cmddata);
                GFX_fillRoundRect(x, y, width, height,
                    arcRadius, 
                    argbTL, argbTR, argbBR, argbBL,
                    clipX, clipY, clipW, clipH);
            }
            else
            {
                printf("Invalid len for GFXCMD_FILLROUNDRECT : %d\n",len);
            }
            break;
        case GFXCMD_DRAWTEXT:
            // x, y, len, text, handle, argb, clipX, clipY, clipW, clipH
            if(len>=36 && len>=(36+readInt(8, cmddata)*2))
            {
                int x, y, textlen,
                    fontHandle, argb,
                    clipX, clipY, clipW, clipH;
                short *text=NULL;
                int i;
                
                x=readInt(0, cmddata);
                y=readInt(4, cmddata);
                textlen=readInt(8, cmddata);
                text = (short *) malloc(textlen*2);
                if(!text) 
                {
                    printf("Out of memory allocating text of len %d\n",textlen);
                    break;
                }                
                for(i=0;i<textlen;i++)
                {
                    text[i]=readShort(12+i*2, cmddata);
                }
                fontHandle=readInt(textlen*2+12, cmddata);
                argb=readInt(textlen*2+16, cmddata);                
                clipX=readInt(textlen*2+20, cmddata);
                clipY=readInt(textlen*2+24, cmddata);
                clipW=readInt(textlen*2+28, cmddata);
                clipH=readInt(textlen*2+32, cmddata);
                GFX_drawText(x, y, textlen, text, fontHandle, argb,
                    clipX, clipY, clipW,clipH);
                if(text) free(text);
            }
            else
            {
                printf("Invalid len for GFXCMD_DRAWTEXT : %d\n",len);
            }
            break;
        case GFXCMD_DRAWTEXTURED:
            // x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend
            if(len==40)
            {
                int x, y, width, height, handle,
                    srcx, srcy, srcwidth, srcheight, blend;
                x=readInt(0, cmddata);
                y=readInt(4, cmddata);
                width=readInt(8, cmddata);
                height=readInt(12, cmddata);
                handle=readInt(16, cmddata);
                srcx=readInt(20, cmddata);
                srcy=readInt(24, cmddata);
                srcwidth=readInt(28, cmddata);
                srcheight=readInt(32, cmddata);
                blend=readInt(36, cmddata);
                GFX_drawTexturedRect(x, y, width, height, handle,
                    srcx, srcy, srcwidth, srcheight, blend);
            }
            else
            {
                printf("Invalid len for GFXCMD_DRAWTEXTURED : %d\n",len);
            }
            break;
        case GFXCMD_DRAWLINE:
            // x1, y1, x2, y2, argb1, argb2
            if(len==24)
            {
                int x1, y1, x2, y2, argb1, argb2;
                x1=readInt(0, cmddata);
                y1=readInt(4, cmddata);
                x2=readInt(8, cmddata);
                y2=readInt(12, cmddata);
                argb1=readInt(16, cmddata);
                argb2=readInt(20, cmddata);
                GFX_drawLine(x1, y1, x2, y2, argb1, argb2);
            }
            else
            {
                printf("Invalid len for GFXCMD_DRAWLINE : %d\n",len);
            }
            break;
        case GFXCMD_LOADIMAGE:
            // width, height
            if(len>=8 && len<=12)
            {
                int width, height,format=0;
                int imghandle;
                width=readInt(0, cmddata);
                height=readInt(4, cmddata);
                if(len>8) format=readInt(8, cmddata);
                imghandle=1;
                imghandle=GFX_loadImage(width, height, format);
                *hasret=1;
                return imghandle;
            }
            else
            {
                printf("Invalid len for GFXCMD_LOADIMAGE : %d\n",len);
            }
            break;
        case GFXCMD_UNLOADIMAGE:
            // handle
            if(len==4)
            {
                int handle;
                handle=readInt(0, cmddata);
                GFX_unloadImage(handle);
            }
            else
            {
                printf("Invalid len for GFXCMD_UNLOADIMAGE : %d\n",len);
            }
            break;
        case GFXCMD_LOADFONT:
            // namelen, name, style, size
            if(len>=12 && len>=(12+readInt(0, cmddata)))
            {
                int namelen, style, size;
                char *name=NULL;
                int i;
                int fonthandle;
                
                namelen=readInt(0, cmddata);
                name = (char *) malloc(namelen);
                if(!name) 
                {
                    printf("Out of memory allocating name of len %d\n",namelen);
                    break;
                }                
                for(i=0;i<namelen;i++)
                {
                    name[i]=cmddata[4+i];
                }
                style=readInt(namelen+4, cmddata);
                size=readInt(namelen+8, cmddata);                
                fonthandle=GFX_loadFont(name, style, size);
                if(name) free(name);
                *hasret=1;
                return fonthandle;
            }
            else
            {
                printf("Invalid len for GFXCMD_LOADFONT : %d\n",len);
            }
            
            break;
        case GFXCMD_UNLOADFONT:
            // handle
            if(len==4)
            {
                int handle;
                handle=readInt(0, cmddata);
                GFX_unloadFont(handle);
            }
            else
            {
                printf("Invalid len for GFXCMD_UNLOADFONT : %d\n",len);
            }            
            break;
        case GFXCMD_FLIPBUFFER:
            *hasret=1;
            if(len==4)
            {
                GFX_flipBuffer(readInt(0, cmddata));
            }
            else
            {
                GFX_flipBuffer(0);
            }
            return 0;
            break;
        case GFXCMD_STARTFRAME:
            GFX_startFrame();
            break;
        case GFXCMD_LOADIMAGELINE:
            // handle, line, len, data
            if(len>=12 && len>=(12+readInt(8, cmddata)))
            {
                int handle, line, len;
                unsigned char *data=&cmddata[12];
                handle=readInt(0, cmddata);
                line=readInt(4, cmddata);
                len=readInt(8, cmddata);
                GFX_loadImageLine(handle, line, len, data);
            }
            else
            {
                printf("Invalid len for GFXCMD_LOADIMAGELINE : %d\n",len);
            }            
            break;
        case GFXCMD_SETMODE:
            if(len==4)
            {
                int mode;
                mode=readInt(0, cmddata);
                GFX_SetMode(mode);
                fprintf(stderr,"returned from GFX_SetMode(%d)\n",mode);
            }
            break;
        case GFXCMD_SETASPECT:
            if(len==4)
            {
                int aspect;
                int newsize;
                aspect=readInt(0, cmddata);
                newsize = GFX_SetAspect(aspect);
                *hasret=1;
                return newsize;
            }
            break;
        case GFXCMD_CREATESURFACE:
            if(len==8)
            {
                int width, height;
                int imghandle;
                width=readInt(0, cmddata);
                height=readInt(4, cmddata);                
                imghandle=1;
                imghandle=GFX_createSurface(width, height);
                *hasret=1;
                return imghandle;
            }
            else
            {
                printf("Invalid len for GFXCMD_CREATESURFACE : %d\n",len);
            }
            break;
        case GFXCMD_SETTARGETSURFACE:
            if(len==4)
            {
                int surface;
                surface=readInt(0, cmddata);
                GFX_SetTargetSurface(surface);
            }
            break;
        case GFXCMD_PREPIMAGE:
            // width, height
            if(len==8)
            {
                int width,height,imghandle;
                width=readInt(0, cmddata);
                height=readInt(4, cmddata);
                imghandle=GFX_PrepImage(width, height);
                *hasret=1;
                return imghandle;
            }
            break;
        case GFXCMD_LOADIMAGECOMPRESSED:
            // handle, len, data
            if(len>=8)
            {
                int handle, len;
                handle=readInt(0, cmddata);
                len=readInt(4, cmddata);
                // TODO
                handle = GFXCMD_LoadImageCompressed(handle, len, sd, gfxcmdbuffer,
                    len>(12288-12) ? (12288-12) : len, 12288, 12);
                *hasret=1;
                return handle;
            }
            break;
        case GFXCMD_SETVIDEOPROP:
            if(len>=44)
            {
                int mode, sx, sy, swidth, sheight, ox, oy, owidth, oheight, alpha, activewin;

                mode=readInt(0, cmddata);
                sx=readInt(4, cmddata);
                sy=readInt(8, cmddata);
                swidth=readInt(12, cmddata);
                sheight=readInt(16, cmddata);
                ox=readInt(20, cmddata);
                oy=readInt(24, cmddata);
                owidth=readInt(28, cmddata);
                oheight=readInt(32, cmddata);
                alpha=readInt(36, cmddata);
                activewin=readInt(40, cmddata);
                // TODO
                GFX_SetVideoProp(mode, sx, sy, swidth, sheight, 
                    ox, oy, owidth, oheight, alpha, activewin);
            }
            break;
        case GFXCMD_RECONNECTMEDIA:
            GFX_ReconnnectMedia();
            break;
        case GFXCMD_LOADIMAGEDIRECTASYNC:
        case GFXCMD_LOADIMAGEDIRECT:
            // handle, offset, length
            // name length, filename
            if(len>=16)
            {
                int handle, offset, length, namelength;
                handle=readInt(0, cmddata);
                offset=readInt(4, cmddata);
                length=readInt(8, cmddata);
                namelength=readInt(12, cmddata);

                if(cmd==GFXCMD_LOADIMAGEDIRECT)
                {
                    handle = GFXCMD_LoadImageDirect(handle, offset, length, namelength, &cmddata[16]);
                    *hasret=1;
                    return handle;
                }
                else
                {
                    GFXCMD_LoadImageDirectAsync(handle, offset, length, namelength, &cmddata[16]);
                }
            }
            break;
        case GFXCMD_DRAWTEXTUREDDIFFUSE:
            // x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend
            if(len==60)
            {
                int x, y, width, height, handle,
                    srcx, srcy, srcwidth, srcheight, blend,
                    diffhandle, diffsrcx, diffsrcy, diffsrcwidth, diffsrcheight;
                x=readInt(0, cmddata);
                y=readInt(4, cmddata);
                width=readInt(8, cmddata);
                height=readInt(12, cmddata);
                handle=readInt(16, cmddata);
                srcx=readInt(20, cmddata);
                srcy=readInt(24, cmddata);
                srcwidth=readInt(28, cmddata);
                srcheight=readInt(32, cmddata);
                blend=readInt(36, cmddata);
                diffhandle=readInt(40, cmddata);
                diffsrcx=readInt(44, cmddata);
                diffsrcy=readInt(48, cmddata);
                diffsrcwidth=readInt(52, cmddata);
                diffsrcheight=readInt(56, cmddata);
                GFX_drawTexturedDiffusedRect(x, y, width, height, handle,
                    srcx, srcy, srcwidth, srcheight, blend,
                    diffhandle, diffsrcx, diffsrcy, diffsrcwidth, diffsrcheight);
            }
            else
            {
                printf("Invalid len for GFXCMD_DRAWTEXTUREDDIFFUSE : %d\n",len);
            }
            break;
        case GFXCMD_SETCURSORPROP:
            // int mode, int cx, int cy, int state, int width, int height, char *data
            if(len>=24 && len>=( 24+ ((readInt(0, cmddata)&4) ? (readInt(16, cmddata)*readInt(20, cmddata)/2+16*4): 0)))
            {
                int mode, cx, cy, state, width, height;
                unsigned char *data=&cmddata[24];
                mode=readInt(0, cmddata);
                cx=readInt(4, cmddata);
                cy=readInt(8, cmddata);
                state=readInt(12, cmddata);
                width=readInt(16, cmddata);
                height=readInt(20, cmddata);
                GFX_SetCursorProp(mode, cx, cy, state, width, height, data);
            }
            else
            {
                printf("Invalid len for GFXCMD_SETCURSORPROP: %d\n",len);
            }
            break;
        default:
            return -1;
    }
    return 0;
}
