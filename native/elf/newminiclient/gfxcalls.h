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
int GFX_init();
void GFX_drawRect(int x, int y, int width, int height, 
    int thickness, int argbTL, int argbTR, int argbBR, int argbBL);
void GFX_fillRect(int x, int y, int width, int height, 
    int argbTL, int argbTR, int argbBR, int argbBL);
void GFX_clearRect(int x, int y, int width, int height, 
    int argbTL, int argbTR, int argbBR, int argbBL);
void GFX_drawOval(int x, int y, int width, int height, int thickness, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH);
void GFX_drawRoundRect(int x, int y, int width, int height, 
    int thickness, int arcRadius, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH);
void GFX_fillRoundRect(int x, int y, int width, int height, int arcRadius, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH);
void GFX_drawLine(int x1, int y1, int x2, int y2, int argb1, int argb2);
int GFX_loadImage(int width, int height, int format);
void GFX_unloadImage(int imghandle);
void GFX_drawTexturedRect(int x, int y, int width, int height, int handle,
    int srcx, int srcy, int srcwidth, int srcheight, int blend);
void GFX_drawText(int x, int y, int len, short *text, int handle, int argb,
    int clipX, int clipY, int clipW, int clipH);
int GFX_loadFont(char *name, int style, int size);
void GFX_unloadFont(int handle);
int GFX_flipBuffer(int notInAnim);
int GFX_startFrame();
void GFX_loadImageLine(int handle, int line, int len, unsigned char *buffer);


void GFX_SetMode(int mode);
int GFX_SetAspect(int aspect);

int GFX_createSurface(int width, int height);
void GFX_SetTargetSurface(int surface);

int GFX_PrepImage(int width, int height);
int GFXCMD_LoadImageCompressed(int handle, int len, int socket, unsigned char *buffer,
    int bufferlevel, int buffersize, int bufferoffset);
int GFX_SetVideoProp(int mode, int sx, int sy, int swidth, int sheight, 
    int ox, int oy, int owidth, int oheight, int alpha, int activewin);

int GFX_ReconnnectMedia();
int GFXCMD_LoadImageDirect(int handle, int offset, int length, int namelength, unsigned char *name);

void GFX_drawTexturedDiffusedRect(int x, int y, int width, int height, int handle,
    int srcx, int srcy, int srcwidth, int srcheight, int blend,
    int diffhandle, int diffsrcx, int diffsrcy, int diffsrcwidth, int diffsrcheight);

void GFX_keepOSD();
void GFX_deinit();
int GFX_SetCursorProp(int mode, int cx, int cy, int state, int width, int height, char *data);

void GFXCMD_LoadImageDirectAsync(int handle, int offset, int length, int namelength, unsigned char *name);
