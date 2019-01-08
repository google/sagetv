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
#include <stdio.h>
#include <malloc.h>
#include <string.h>
#include <stdlib.h>
#include "flashmpeg4.h"


//#define DEBUGHEADER
//#define DEBUGMB
//#define DEBUG
//#define DEBUGMV
//#define DEBUGGETBITS
//#define DEBUGPUTBITS
//#define DEBUGDC
//#define NOPBLOCKS

// Read bits
// Read vlc
// Write bits
// Write vlc

// Value in first table and len in second table

// MCBPC
unsigned char Imcbpcvlc[2][9] = { 
    { 1, 1, 2, 3, 1, 1, 2, 3, 1 }, 
    { 1, 3, 3, 3, 4, 6, 6, 6, 9 }};
unsigned int Pmcbpcvlc[2][25] = {
    { 1, 3, 2, 5, 3, 7, 6, 5,
    2, 5, 4, 5, 3, 4, 3, 3,
    4, 4, 3, 2, 1, 2, 12, 14, 
    15
    },
    { 1, 4, 4, 6, 3, 7, 7, 9,
    3, 7, 7, 8, 5, 8, 8, 7,
    6, 9, 9, 9, 9, 11, 13, 13,
    13}
    };

// CBPY
unsigned char cbpyvlc[2][16] = { 
    { 3, 5, 4, 9, 3, 7, 2, 11, 2, 3, 5, 10, 4, 8, 6, 3},
    { 4, 5, 5, 4, 5, 4, 6, 4, 5, 6, 4, 4, 4, 4, 4, 2}};

// MVD
// We can do those by counting bits

// INTRATCOEF
// Doesn't include the last bit for sign
unsigned char intratcoefvlc[2][103] = {
    { 2, 6, 15, 13, 12, 21, 19, 18,
    23, 31, 30, 29, 37, 36, 35, 33,
    33, 32, 15, 14, 7, 6, 32, 33,
    80, 81, 82, 14, 20, 22, 28, 32,
    31, 13, 34, 83, 85, 11, 21, 30,
    12, 86, 17, 27, 29, 11, 16, 34,
    10, 13, 28, 8, 18, 27, 84, 20,
    26, 87, 25, 9, 24, 35, 23, 25,
    24, 7, 88, 7, 12, 22, 23, 6,
    5, 4, 89, 15, 22, 5, 14, 4,
    17, 36, 16, 37, 19, 90, 21, 91,
    20, 19, 26, 21, 20, 19, 18, 17,
    38, 39, 92, 93, 94, 95, 3},
    { 2, 3, 4, 5, 5, 6, 6, 6,
    7, 8, 8, 8, 9, 9, 9, 9,
    10, 10, 10, 10, 11, 11, 11, 11,
    12, 12, 12, 4, 6, 7, 8, 9,
    9, 10, 11, 12, 12, 5, 7, 9,
    10, 12, 6, 8, 9, 10, 6, 9,
    10, 6, 9, 10, 7, 9, 12, 7,
    9, 12, 8, 10, 8, 11, 8, 9,
    9, 10, 12, 4, 6, 8, 9, 10,
    11, 11, 12, 6, 9, 10, 6, 10,
    7, 11, 7, 11, 7, 12, 8, 12,
    8, 8, 8, 9, 9, 9, 9, 9,
    11, 11, 12, 12, 12, 12, 7}};

unsigned char intratcoeflevel[102] = {
  1,  2,  3,  4,  5,  6,  7,  8,
  9, 10, 11, 12, 13, 14, 15, 16,
 17, 18, 19, 20, 21, 22, 23, 24,
 25, 26, 27,  1,  2,  3,  4,  5,
  6,  7,  8,  9, 10,  1,  2,  3,
  4,  5,  1,  2,  3,  4,  1,  2,
  3,  1,  2,  3,  1,  2,  3,  1,
  2,  3,  1,  2,  1,  2,  1,  1,
  1,  1,  1,  1,  2,  3,  4,  5,
  6,  7,  8,  1,  2,  3,  1,  2,
  1,  2,  1,  2,  1,  2,  1,  2,
  1,  1,  1,  1,  1,  1,  1,  1,
  1,  1,  1,  1,  1,  1,
};

unsigned char intratcoefrun[102] = {
  0,  0,  0,  0,  0,  0,  0,  0,
  0,  0,  0,  0,  0,  0,  0,  0,
  0,  0,  0,  0,  0,  0,  0,  0,
  0,  0,  0,  1,  1,  1,  1,  1,
  1,  1,  1,  1,  1,  2,  2,  2,
  2,  2,  3,  3,  3,  3,  4,  4,
  4,  5,  5,  5,  6,  6,  6,  7,
  7,  7,  8,  8,  9,  9, 10, 11,
 12, 13, 14,  0,  0,  0,  0,  0,
  0,  0,  0,  1,  1,  1,  2,  2,
  3,  3,  4,  4,  5,  5,  6,  6,
  7,  8,  9, 10, 11, 12, 13, 14,
 15, 16, 17, 18, 19, 20,
};

// TCOEF
// Doesn't include the last bit for sign
unsigned char tcoefvlc[2][103] = { 
    { 2, 15, 21, 23, 31, 37, 36, 33, 32, 7,
    6, 32, 6, 20, 30, 15, 33, 80, 14, 29,
    14, 81, 13, 35, 13, 12, 34, 82, 11, 12,
    83, 19, 11, 84, 18, 10, 17, 9, 16, 8,
    22, 85, 21, 20, 28, 27, 33, 32, 31, 30,
    29, 28, 27, 26, 34, 35, 86, 87, 7, 25,
    5, 15, 4, 14, 13, 12, 19, 18, 17, 16,
    26, 25, 24, 23, 22, 21, 20, 19, 24, 23,
    22, 21, 20, 19, 18, 17, 7, 6, 5, 4,
    36, 37, 38, 39, 88, 89, 90, 91, 92, 93,
    94, 95, 3},
    { 2, 4, 6, 7, 8, 9, 9, 10, 10, 11,
    11, 11, 3, 6, 8, 10, 11, 12, 4, 8,
    10, 12, 5, 9, 10, 5, 9, 12, 5, 10,
    12, 6, 10, 12, 6, 10, 6, 10, 6, 10,
    7, 12, 7, 7, 8, 8, 9, 9, 9, 9,
    9, 9, 9, 9, 11, 11, 12, 12, 4, 9,
    11, 6, 11, 6, 6, 6, 7, 7, 7, 7,
    8, 8, 8, 8, 8, 8, 8, 8, 9, 9,
    9, 9, 9, 9, 9, 9, 10, 10, 10, 10,
    11, 11, 11, 11, 12, 12, 12, 12, 12, 12,
    12, 12, 7}};

unsigned char  tcoeflevel[102] = {
  1,  2,  3,  4,  5,  6,  7,  8,
  9, 10, 11, 12,  1,  2,  3,  4,
  5,  6,  1,  2,  3,  4,  1,  2,
  3,  1,  2,  3,  1,  2,  3,  1,
  2,  3,  1,  2,  1,  2,  1,  2,
  1,  2,  1,  1,  1,  1,  1,  1,
  1,  1,  1,  1,  1,  1,  1,  1,
  1,  1,  1,  2,  3,  1,  2,  1,
  1,  1,  1,  1,  1,  1,  1,  1,
  1,  1,  1,  1,  1,  1,  1,  1,
  1,  1,  1,  1,  1,  1,  1,  1,
  1,  1,  1,  1,  1,  1,  1,  1,
  1,  1,  1,  1,  1,  1};

unsigned char  tcoefrun[102] = {
  0,  0,  0,  0,  0,  0,  0,  0,
  0,  0,  0,  0,  1,  1,  1,  1,
  1,  1,  2,  2,  2,  2,  3,  3,
  3,  4,  4,  4,  5,  5,  5,  6,
  6,  6,  7,  7,  8,  8,  9,  9,
 10, 10, 11, 12, 13, 14, 15, 16,
 17, 18, 19, 20, 21, 22, 23, 24,
 25, 26,  0,  0,  0,  1,  1,  2,
  3,  4,  5,  6,  7,  8,  9, 10,
 11, 12, 13, 14, 15, 16, 17, 18,
 19, 20, 21, 22, 23, 24, 25, 26,
 27, 28, 29, 30, 31, 32, 33, 34,
 35, 36, 37, 38, 39, 40};

// MVD
unsigned int mvdvlc[2][33] = {
    {1, 1, 1, 1, 3, 5, 4, 3, 
      11, 10, 9, 17, 16, 15, 14, 13,
      12 ,11, 10, 9, 8, 7, 6, 5,
      4, 7, 6, 5, 4, 3, 2, 3,
      2},
    {1, 2, 3, 4, 6, 7, 7, 7,
      9, 9, 9, 10, 10, 10, 10, 10,
      10, 10, 10, 10, 10, 10, 10, 10,
      10, 11, 11, 11, 11, 11, 11, 12,
      12}
};

// We can optimize this later with some caching...
// Rules: we don't peek at more than 24 bits so we can keep 32bits in buffer down to 24 bits...
inline unsigned int GetBits(BitStream *s, int n, int use)
{
    unsigned int outval=0;
    unsigned int oldposition=s->position;
    #ifdef DEBUGGETBITS
    printf("Getbits %d %d at %d",n, use, oldposition);
    #endif
    if(s->error || s->position+n>s->size)
    {
        fprintf(stderr, "Getbits %d %d at %d",n, use, oldposition);
        fprintf(stderr, "GetBits Error %d found (pos %d, size %d)\n",s->error, s->position, s->size);
        s->error=1;
        return 0xFFFFFFFF;
    }
    while(n)
    {
        int bitsize = 8-(s->position&7);
        bitsize = (bitsize > n) ? n : bitsize;
        outval=(outval<<bitsize) | ( (s->dataptr[s->position>>3]>>(8-(s->position&7)-bitsize))&((1<<bitsize)-1));
        s->position+=bitsize;
        n-=bitsize;
    }
    if(!use) s->position=oldposition;
    #ifdef DEBUGGETBITS
    printf(" : %d\n", outval);
    #endif
    return outval;
}

// We can optimize this later with some caching...
// Rules: we don't peek at more than 24 bits so we can keep 32bits in buffer down to 24 bits...
inline unsigned int PutBits(BitStream *s, int n, int value)
{
    unsigned int outval=value;
    int valuelen=n;
    #ifdef DEBUGPUTBITS
    printf("Putbits %d %d at pos %d\n",n, value, s->position);
    #endif
    if(s->error || s->position+n>s->size)
    {
        printf("PutBits error\n");
        s->error=1;
        return 0xFFFFFFFF;
    }
    while(n)
    {
        int bitsize = 8-(s->position&7);
        bitsize = (bitsize > n) ? n : bitsize;
        // Store highest bitsize bits of value into dataptr
        #ifdef DEBUGPUTBITS
        printf("before byte at %d : %02X\n",s->position>>3,s->dataptr[s->position>>3]);
        #endif
        // Clear the bits we are going to set
        s->dataptr[s->position>>3]&=~ ( ((1<<bitsize)-1)<<(8-(s->position&7)-bitsize));
        // Set the news bits
        s->dataptr[s->position>>3]|=(((value>>(n-bitsize))&((1<<bitsize)-1)))<<(8-(s->position&7)-bitsize);
        #ifdef DEBUGPUTBITS
        printf("after byte at %d : %02X\n",s->position>>3,s->dataptr[s->position>>3]);
        #endif
        s->position+=bitsize;
        n-=bitsize;
    }
    #ifdef DEBUGPUTBITS
    printf("Putbits done, new pos %d\n",s->position);
    #endif
    
    return value;
}

// TODO: verify if that is right for all cases
int PadByte(BitStream *s)
{
    if(s->position&7)
    {
        PutBits(s, 1, 0);
    }
    while(s->position&7)
    {
        PutBits(s, 1, 1);
    }
}

// Used to pad between the 2 virtual frames
int PadByte2(BitStream *s)
{
    while((s->position&7)!=7)
    {
        PutBits(s, 1, 1);
    }
    PutBits(s, 1, 0);
}

inline unsigned int DiscardBits(BitStream *s, int n)
{
    if(s->error || s->position+n>s->size)
    {
        printf("DiscardBits error\n");
        s->error=1;
        return 0xFFFFFFFF;
    }
    s->position+=n;
    return 0;
}

// Generated by buildvlc...
#include "vlccode.h"


int PutImcbpc(BitStream *os, int mbtype, int cbpc)
{
    int code=0;
    switch(mbtype)
    {
        case 3:
            code=0;
            break;
        case 4:
            code=4;
            break;
        default:
            printf("Unsupported block type %dfor I-mcbpc\n", mbtype);
            return -1;
    }
    code+=(cbpc&3);
    PutBits(os, Imcbpcvlc[1][code],Imcbpcvlc[0][code]);
    return 0;
}

int PutPmcbpc(BitStream *os, int mbtype, int cbpc)
{
    int code=0;
    switch(mbtype)
    {
        case 0:
            code=0;
            break;
        case 1:
            code=4;
            break;
        case 2:
            code=8;
            break;
        case 3:
            code=12;
            break;
        case 4:
            code=16;
            break;
        case 5:
            code=21; // Code 20 is reserved for stuffing
            break;
        default:
            printf("Unsupported block type %d for P-mcbpc\n", mbtype);
            return -1;
    }
    code+=(cbpc&3);
    PutBits(os, Pmcbpcvlc[1][code],Pmcbpcvlc[0][code]);
    return 0;
}

int PutCBPY(BitStream *os, int cbpy)
{
    if(cbpy>15 || cbpy<0)
    {
            printf("Unsupported cbpy %X\n", cbpy);
            return -1;
    }
    PutBits(os, cbpyvlc[1][cbpy],cbpyvlc[0][cbpy]);
    return 0;
}

int PutINTRATCOEF(BitStream *os, int code)
{
    PutBits(os, intratcoefvlc[1][code],intratcoefvlc[0][code]);
    return 0;
}

int PutTCOEF(BitStream *os, int code)
{
    PutBits(os, tcoefvlc[1][code],tcoefvlc[0][code]);
    return 0;
}

int EncodeINTRATCOEF(BitStream *os, short level, int run, int last)
{
    int code=-1;
// For now we shouldn't need that...
/*    if(last==0)
    {
        int i;
        for(i=0;i<54;i++)
        {
        
        }
    }
    else
    {
    
    }*/

    if(code==-1) // code wasn't found
    {
        PutINTRATCOEF(os, 102); //Escape
        /*PutBits(os, 2, 3); // Type 3 Escape code
        PutBits(os, 1, last ? 1 : 0); // is last
        PutBits(os, 6, run); // run
        PutBits(os, 1, 1); // marker
        PutBits(os, 12, level); // level
        PutBits(os, 1, 1); // marker*/
        PutBits(os, 23, (3<<21)|(last ? 1<<20 : 0)|(run<<14)|(1<<13)|((level&0xFFF)<<1)|1);
    }
}

int EncodeTCOEF(BitStream *os, short level, int run, int last)
{
    int code=-1;
// For now we shouldn't need that...
/*    if(last==0)
    {
        int i;
        for(i=0;i<54;i++)
        {
        
        }
    }
    else
    {
    
    }*/

    if(code==-1) // code wasn't found
    {
        PutTCOEF(os, 102); //Escape
/*        PutBits(os, 2, 3); // Type 3 Escape code
        PutBits(os, 1, last ? 1 : 0); // is last
        PutBits(os, 6, run); // run
        PutBits(os, 1, 1); // marker
        PutBits(os, 12, level); // level
        PutBits(os, 1, 1); // marker*/
        PutBits(os, 23, (3<<21)|(last ? 1<<20: 0)|(run<<14)|(1<<13)|((level&0xFFF)<<1)|1);
    }
}

int PutMVD(BitStream *os, int code, int sign)
{
    PutBits(os, mvdvlc[1][code],mvdvlc[0][code]);
    if(code!=0) PutBits(os, 1, sign);
    return 0;
}

void readBlock(BitStream *is, BitStream *os, int mbx, int mby, int startpos, flv_state *state, int intra, int curquant)
{
    int pos=startpos;
    int run;
    int last=0;
    short level;
    int needextrarun=0; //intra;
    int needfix=0;
    int levelmul,leveladd;
    int newquant;
    if(intra)
    {
        needfix=1;
        levelmul=curquant;
        leveladd=(curquant-1)>>1;
    }
    while(last!=1)
    {
        int code=GetTCOEF(is);
        if(code==102) //escape
        {
            if(state->format==1)
            {
                int bits11=GetBits(is, 1, 1);
                last = GetBits(is, 1, 1);
                run = GetBits(is, 6, 1);
                if(bits11)
                {
                    level = GetBits(is, 11, 1); // Sign extends...
                    if(level&0x0400) level|=0xF800;
                }
                else
                {
                    level = GetBits(is, 7, 1); // Sign extends...
                    if(level&0x0040) level|=0xFF80;
                }
            }
            else
            {
                last = GetBits(is, 1, 1);
                run = GetBits(is, 6, 1);
                level = ((char)GetBits(is, 8, 1));
                if(level==0) // error
                {
                }
                if(level==-128)
                { // Extended codes from flash
                    level = GetBits(is, 5, 1);
                    level |= GetBits(is, 6, 1)<<5;
                    if(level&0x0400) level|0xF800;
                }
            }
        }
        else
        {
            if(code>57)
            {
                last = 1;
            }
            if(GetBits(is, 1, 1))
            {
                level = -tcoeflevel[code];
            }
            else
            {
                level = tcoeflevel[code];
            }
            run = tcoefrun[code];
        }
        if(needfix)
        {
            // Fix value for new quant
            level*=levelmul;
            level+=leveladd;
        }
        if(code==102)
        {
            // We need to recreate as mpeg4
            if(needextrarun)
            {
                run+=1;
                needextrarun=0;
            }
            if(intra)
            {
                EncodeINTRATCOEF(os, level, run, last);
            }
            else
            {
                EncodeTCOEF(os, level, run, last);
            }
        }
        else
        {
            if(needextrarun)
            {
                run+=1;
                if(intra)
                {
                    EncodeINTRATCOEF(os, level, run, last);
                }
                else
                {
                    EncodeTCOEF(os, level, run, last);
                }
                needextrarun=0;
            }
            else
            {
                if(intra)
                {
                    EncodeINTRATCOEF(os, level, run, last);
                }
                else
                {
                    // copy code as is
                    PutTCOEF(os, code);
                    PutBits(os, 1, level<0 ? 1 : 0);
                }
            }
        }
        pos+=run;
        #ifdef DEBUG
        printf("buf %d = %d\n",pos,level);
        #endif
        //buf[pos]=level;
        pos+=1;
    }
}

void putVolHeader(BitStream *s, flv_state *state)
{
    PutBits(s, 32, 0x100);
    PutBits(s, 32, 0x120);
    PutBits(s, 1, 0);
    PutBits(s, 8, 1); // Simple
    PutBits(s, 1, 1);
    PutBits(s, 4, 1);
    PutBits(s, 3, 1);
    PutBits(s, 4, 1); // aspect
    // TODO: add extended aspect if needed

    PutBits(s, 1, 1);
    PutBits(s, 2, 1);
    PutBits(s, 1, 0);
    PutBits(s, 1, 0);

    PutBits(s, 2, 0);   // Rectangle
    PutBits(s, 1, 1);
    PutBits(s, 16, 1000); // FLV use milliseconds

    PutBits(s, 1, 1);
    PutBits(s, 1, 0);
    PutBits(s, 1, 1);
    PutBits(s, 13, state->width);
    PutBits(s, 1, 1);
    PutBits(s, 13, state->height);
    PutBits(s, 1, 1);
    PutBits(s, 1, 0);  // Not interlaced
    PutBits(s, 1, 1);  // obmc disabled
    PutBits(s, 1, 0);  // sprite_enable
    PutBits(s, 1, 0); // 8 bits
    PutBits(s, 1, 0); // h263 quant

    PutBits(s, 1, 1); // complexity disable
    PutBits(s, 1, 1); // resync disable
    PutBits(s, 1, 0); // partitioning disable
    PutBits(s, 1, 0); // scalability

    //TODO: pad to next byte
    PadByte(s);
}

void putVopHeader(BitStream *s, flv_state *state, int virtualframe)
{
    PutBits(s, 32, 0x1B6);
    if(!virtualframe)
    {
        PutBits(s, 2, state->ptype);
    }
    else
    {
        PutBits(s, 2, 1);
    }
    PutBits(s, 1, 0); // 0 seconds since last frame...

    PutBits(s, 1, 1);
    if(!virtualframe)
    {
        PutBits(s, 10, 33); // For now force 30 fps so 1000/30 ticks
    }
    else
    {
        PutBits(s, 10, 33); // For now force 30 fps so 1000/30 ticks
    }
    PutBits(s, 1, 1);
    PutBits(s, 1, 1); // coded
    if(state->ptype==1 || virtualframe)
    {
        PutBits(s, 1, 0); // rounding
    }
    PutBits(s, 3, 7);

    PutBits(s, 5, state->pquant);

    if(state->ptype==1 || virtualframe)
    {
        PutBits(s, 3, 1); // fcode
    }
}

// We force scale to 8
short preddc(int bx, int by, int width, short dc, short *predbuffer)
{
    // b c
    // a x
    short a,b,c;
    short pred;
    if(bx==0)
    {
         a=1024;
    }
    else
    {
        a=predbuffer[bx-1+by*width];
    }

    if(bx==0 || by==0)
    {
         b=1024;
    }
    else
    {
        b=predbuffer[bx-1+(by-1)*width];
    }

    if(by==0)
    {
        c=1024;
    }
    else
    {
        c=predbuffer[bx+(by-1)*width];
    }

    if (abs(a - b) < abs(b - c))
    {
        pred=c;
    }
    else
    {
        pred=a;
    }

    predbuffer[bx+(by)*width]=dc*8;

    return dc-(pred+4)/8;
}

int convertFrame(unsigned char *input, int inlen, unsigned char *output, int outlen, int *used,
    short *predbuffer)
{
    BitStream is;
    BitStream os; // Used for IP part
    flv_state s;
    int mbx,mby;
    unsigned short blockbuf[6]; // We use it to store the 6 dc coefficients
    int curquant;
    int realptype;
    *used=0;
    
/*    {
        FILE *packetfile;
        packetfile=fopen("packet.bin","a+b");
        fwrite(input, inlen, 1, packetfile);
        fclose(packetfile);
    }*/
    memset(&s, 0, sizeof(flv_state));
    is.dataptr=input;
    is.position=0;
    is.size=inlen*8;
    is.error=0;

    os.dataptr=output;
    os.position=0;
    os.size=outlen*8;
    os.error=0;

    if(GetBits(&is, 17, 1)!=1)
    {
        fprintf(stderr, "Couldn't read flv header\n");
        return 0;
    }
    s.format=GetBits(&is, 5, 1);
    s.tr=GetBits(&is, 8, 1);
    switch(GetBits(&is, 3, 1))
    {
        case 0:
            s.width=GetBits(&is, 8, 1);
            s.height=GetBits(&is, 8, 1);
            break;
        case 1:
            s.width=GetBits(&is, 16, 1);
            s.height=GetBits(&is, 16, 1);
            break;
        case 2:
            s.width=352;
            s.height=288;
            break;
        case 3:
            s.width=176;
            s.height=144;
            break;
        case 4:
            s.width=128;
            s.height=96;
            break;
        case 5:
            s.width=320;
            s.height=240;
            break;
        case 6:
            s.width=160;
            s.height=120;
            break;
        default:
            fprintf(stderr, "Error reading picture width and height\n");
            return 0;
            break;
    }
    
    s.ptype=GetBits(&is, 2, 1);
    s.unk=GetBits(&is, 1, 1);
    s.pquant=GetBits(&is, 5, 1);
    
    curquant=s.pquant;
    
    while(GetBits(&is, 1, 1)==1)
    {
        GetBits(&is, 8, 1);
    }
    
    s.mbwidth=(s.width+15)/16;
    s.mbheight=(s.height+15)/16;
    
    putVolHeader(&os, &s);
    putVopHeader(&os, &s, 0);

    // TODO: add tests to see if we can parse this stuff...
    #ifdef DEBUGHEADER
    fprintf(stderr, "FLV header parsed format=%d width=%d height=%d ptype=%d unk=%d pquant=%d\n",
        s.format, s.width, s.height, s.ptype, s.unk, s.pquant);
    #endif
    for(mby=0;mby<s.mbheight;mby++)
    {
        for(mbx=0;mbx<s.mbwidth;mbx++)
        {
            // IFrame
            #ifdef DEBUGMB
            printf("MB %d, %d\n",mbx,mby);
            #endif
            // Y
            predbuffer[(mbx*2+mby*2*90)+0]=1024;
            predbuffer[(mbx*2+mby*2*90)+1]=1024;
            predbuffer[(mbx*2+mby*2*90+90)+0]=1024;
            predbuffer[(mbx*2+mby*2*90+90)+1]=1024;
            // U
            predbuffer[6480+(mbx+mby*45)]=1024;
            // V
            predbuffer[6480+1620+(mbx+mby*45)]=1024;
            if(s.ptype==0)
            {
                unsigned int mcbpc, cbpy, mbtype, cbpc, block, dquant=0;
                unsigned int startblockpos=0,endpos=0;
                int qcode=0;
                int coded;
                while((mcbpc=GetImcbpc(&is))==8);
                mbtype=(mcbpc>>2) + 3;
                cbpc=mcbpc&3;
                cbpy=GetCBPY(&is);
                coded=cbpc|(cbpy<<2);

                if(mbtype==4) // Has Q
                {
                    qcode=GetBits(&is, 2, 1);
                    dquant=qcode-1;
                    if(dquant==0) dquant=-2;
                    curquant+=dquant;
                }
                #ifdef DEBUG
                printf("I MB type %d CBPY:%d CBPC:%d dquant: %d curquant:%d\n",
                    mbtype, cbpy, cbpc, dquant, curquant);
                #endif
                #ifdef DEBUG
                printf("outputpos %d (%d bytes)\n", os.position, os.position/8);
                #endif
                // We can output our H263 I block header

                PutImcbpc(&os, mbtype, 3); // only DC coefs for color blocks...
                PutBits(&os, 1, 0); // ac_pred_flag
                PutCBPY(&os, 15); // For our I block we don't have any tcoefs, only DC coefs
                if(mbtype==4) // Has Q
                {
                    PutBits(&os, 2, qcode);
                }

                for(block=0;block<6;block++) // 4Y, 2C
                {
                    short dc;
                    // For I blocks, all have the 8 bit Intra-DC
                    dc=GetBits(&is,8,1);
                    if(dc==0)
                    {
                        // Error
                        fprintf(stderr, "Invalid dc coef\n");
                        return 0;
                    }
                    if(dc==255) dc=128;
                    #ifdef DEBUGDC
                    printf("dc: %d\n",dc);
                    #endif
                    if(block<4)
                    {
                        dc = preddc(mbx*2+(block&1), mby*2+((block&2)>>1), 90, dc, &predbuffer[0]);
                    }
                    else if (block==4)
                    {
                        dc = preddc(mbx, mby, 45, dc, &predbuffer[6480]);
                    }
                    else
                    {
                        dc = preddc(mbx, mby, 45, dc, &predbuffer[6480+1620]);
                    }

                    if(coded & (32>>block)) // block has tcoefs
                    {
                        PutBits(&os, 17, 0xF01); // Escape code + mode 3 escape + not last + run + marker
                        PutBits(&os, 12, dc);
                        PutBits(&os, 1, 1);
                        readBlock(&is, &os, mbx, mby, 0, &s, 1, curquant);
                    }
                    else
                    {
                        PutBits(&os, 17, 0xF81); // Escape code + mode 3 escape + last + run + marker
                        PutBits(&os, 12, dc);
                        PutBits(&os, 1, 1);
                    }
                }
            }
            else
            {
                unsigned int coded=GetBits(&is, 1, 1);
                PutBits(&os, 1, coded);
                if(!coded) // coded bit inverted
                {
                    unsigned int mcbpc, cbpy, mbtype, cbpc, block, dquant=0;
                    unsigned int startblockpos=0,endpos=0;
                    int qcode=0;
                    int coded;
                    while((mcbpc=GetPmcbpc(&is))==20);
                    #ifdef DEBUG
                    printf("pmcbpc %d\n",mcbpc);
                    #endif
                    if(mcbpc>20) mcbpc-=1;
                    cbpc=mcbpc&3;
                    mbtype=(mcbpc>>2);
                    cbpy=GetCBPY(&is);
                    if(mbtype!=3 && mbtype!=4)
                    {
                        cbpy^=0xF; // inverted bits for inter macroblocks
                    }
                    coded=cbpc|(cbpy<<2);
                    if(mbtype==1 || mbtype==4 || mbtype==5) // Has Q
                    {
                        qcode=GetBits(&is, 2, 1);
                        dquant=qcode-1;
                        if(dquant==0) dquant=-2;
                        curquant+=dquant;
                    }
                    #ifdef DEBUG
                    printf("P MB type %d CBPY:%d CBPC:%d dquant: %d curquant:%d\n",
                        mbtype, cbpy, cbpc, dquant, curquant);
                    #endif
                    if(mbtype==3 || mbtype == 4) // I block
                    {
                        // We can output our H263 I block header
                        PutPmcbpc(&os, mbtype, 3); // only DC coefs for color blocks...
                        PutBits(&os, 1, 0); // ac_pred_flag
                        PutCBPY(&os, 15); // For our I block we don't have any tcoefs, only DC coefs
                        if(mbtype==4) // Has Q
                        {
                            PutBits(&os, 2, qcode);
                        }
                        for(block=0;block<6;block++) // 4Y, 2C
                        {
                            short dc;
                            // For I blocks, all have the 8 bit Intra-DC
                            dc=GetBits(&is,8,1);
                            if(dc==0)
                            {
                                // Error
                                fprintf(stderr, "Invalid dc coef\n");
                                return 0;
                            }
                            if(dc==255) dc=128;
                            #ifdef DEBUGDC
                            printf("dc: %d\n",dc);
                            #endif
                            if(block<4)
                            {
                                dc = preddc(mbx*2+(block&1), mby*2+((block&2)>>1), 90, dc, &predbuffer[0]);
                            }
                            else if (block==4)
                            {
                                dc = preddc(mbx, mby, 45, dc, &predbuffer[6480]);
                            }
                            else
                            {
                                dc = preddc(mbx, mby, 45, dc, &predbuffer[6480+1620]);
                            }
        
                            if(coded & (32>>block)) // block has tcoefs
                            {
                                PutBits(&os, 17, 0xF01); // Escape code + mode 3 escape + not last + run + marker
                                PutBits(&os, 12, dc);
                                PutBits(&os, 1, 1);
                                readBlock(&is, &os, mbx, mby, 0, &s, 1, curquant);
                            }
                            else
                            {
                                PutBits(&os, 17, 0xF81); // Escape code + mode 3 escape + last + run + marker
                                PutBits(&os, 12, dc);
                                PutBits(&os, 1, 1);
                            }
                        }
                    }
                    else // This is an inter block
                    {
                        // Decode the motion vectors
                        int mvx,mvxs=0,mvy,mvys=0,mvcount=1,mv;
                        PutPmcbpc(&os, mbtype, cbpc); 
                        PutCBPY(&os, 15^cbpy); // P block cbpy, bits are inverted from intra block
                        if(mbtype==2 || mbtype==5)
                        {
                            // TODO: mpeg4 doesn't support block type 5, extend block type 2 with extra bit for quant
                            //PutBits(&os, 1, mbtype==5);
                            if(mbtype==5) printf("warning type 5 block present\n");
                            mvcount=4;
                        }
                        if(mbtype==1 || mbtype==5) // Has Q
                        {
                            PutBits(&os, 2, qcode);
                        }
                        for(mv=0;mv<mvcount;mv++)
                        {
                            mvx=Getmvd(&is);
                            if(mvx!=0) mvxs=GetBits(&is,1,1);
                            mvy=Getmvd(&is);
                            if(mvy!=0)mvys=GetBits(&is,1,1);
                            #ifdef DEBUGMV
                            printf("mv%d mvx : %d mvxs : %d mvy: %d mvys: %d\n", mv, mvx, mvxs, mvy, mvys);
                            #endif
                            // We don't need to do any processing of the motion vector,
                            // simply write them back as is
                            PutMVD(&os, mvx, mvxs);
                            PutMVD(&os, mvy, mvys);
                        }
                        for(block=0;block<6;block++) // 4Y, 2C
                        {
                            if(block<4 && cbpy&(8>>block)) // Y block has tcoefs
                            {
                                readBlock(&is, &os, mbx, mby, 0, &s, 0, curquant);
                            }
                            else if(block>=4 && cbpc&(2>>(block-4)))
                            {
                                readBlock(&is, &os, mbx, mby, 0, &s, 0, curquant);
                            }
                        }
                    }
                }
            }
            
        }
    }
    // TODO: pad output to byte
    PadByte(&os);
    *used=(is.position+7)/8;
    return os.position/8;
}
