/*
 *    Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 *   This library is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 *
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *   Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
#ifndef __FLASHMPEG4_H__

// We need to decode each frame from flv to coefficients then 
// back from coefficients to I263 and Pmpeg4 blocks.

typedef struct
{
    int format;
    int tr;
    int width;
    int height;
    int ptype;
    int unk;
    int pquant;
    int mbwidth;
    int mbheight;
} flv_state;

typedef struct
{
    unsigned char *dataptr;
    int position; // in bits
    int size; // in bits
    int error;
} BitStream;


int convertFrame(unsigned char *input, int inlen, unsigned char *output, int outlen, int *used,
    short *predbuffer);

//need these for 'inline' to work
unsigned int GetBits(BitStream *s, int n, int use);
unsigned int PutBits(BitStream *s, int n, int value);
unsigned int DiscardBits(BitStream *s, int n);

#define __FLASHMPEG4_H__
#endif
