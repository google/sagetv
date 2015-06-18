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
#define GFXCMD_INIT 1
// mode

#define GFXCMD_DEINIT 2

#define GFXCMD_DRAWRECT 16
// x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL

#define GFXCMD_FILLRECT 17
// x, y, width, height, argbTL, argbTR, argbBR, argbBL

#define GFXCMD_CLEARRECT 18
// x, y, width, height, argbTL, argbTR, argbBR, argbBL

#define GFXCMD_DRAWOVAL 19
// x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL, 
// clipX, clipY, clipW, clipH

#define GFXCMD_FILLOVAL 20
// x, y, width, height, argbTL, argbTR, argbBR, argbBL, 
// clipX, clipY, clipW, clipH

#define GFXCMD_DRAWROUNDRECT 21
// x, y, width, height, thickness, arcRadius, argbTL, argbTR, argbBR, argbBL,
// clipX, clipY, clipW, clipH

#define GFXCMD_FILLROUNDRECT 22
// x, y, width, height, arcRadius, argbTL, argbTR, argbBR, argbBL,
// clipX, clipY, clipW, clipH

#define GFXCMD_DRAWTEXT 23
// x, y, len, text, handle, argb, clipX, clipY, clipW, clipH

#define GFXCMD_DRAWTEXTURED 24
// x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend

#define GFXCMD_DRAWLINE 25
// x1, y1, x2, y2, argb1, argb2

#define GFXCMD_LOADIMAGE 26
// width, height + optional image type

#define GFXCMD_UNLOADIMAGE 27
// handle

#define GFXCMD_LOADFONT 28
// namelen, name, style, size

#define GFXCMD_UNLOADFONT 29
// handle

#define GFXCMD_FLIPBUFFER 30

#define GFXCMD_STARTFRAME 31

#define GFXCMD_LOADIMAGELINE 32
// handle, line, len, data

#define GFXCMD_PREPIMAGE 33
// width, height
// returns handle

#define GFXCMD_LOADIMAGECOMPRESSED 34
// handle, len, data
// returns handle?

#define GFXCMD_CREATESURFACE 37
// width, height
// returns handle

#define GFXCMD_SETTARGETSURFACE 38
// handle

#define GFXCMD_LOADIMAGEDIRECT 39
// handle, offset, length
// name length, filename

#define GFXCMD_DRAWTEXTUREDDIFFUSE 40
// x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend,
// diffhandle ,diffsrcx, diffsrcy, diffsrcwidth, diffsrcheight

#define GFXCMD_TEXTUREBATCH 43
// count, size

#define GFXCMD_SETMODE 128
// mode

#define GFXCMD_SETASPECT 129
// aspect*1000
// returns new size

#define GFXCMD_SETVIDEOPROP 130
// int mode, int sx, int sy, int swidth, int sheight, 
//    int ox, int oy, int owidth, int oheight, int alpha, int activewin

#define GFXCMD_RECONNECTMEDIA 131

#define GFXCMD_SETCURSORPROP 47
// int mode, int cx, int cy, int state, int width, int height, char *data

#define GFXCMD_LOADIMAGEDIRECTASYNC 48
// handle, offset, length
// name length, filename
