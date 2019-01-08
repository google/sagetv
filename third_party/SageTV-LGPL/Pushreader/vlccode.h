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

inline unsigned int GetImcbpc(BitStream *s)
{
    int code=GetBits(s, 9, 0);
    // 000: 256-511 1 bits
    if(code>=256 && code<=511)
    {
        GetBits(s, 1, 1);
        return(0);
    }
    // 001: 64-127 3 bits
    else if(code>=64 && code<=127)
    {
        GetBits(s, 3, 1);
        return(1);
    }
    // 002: 128-191 3 bits
    else if(code>=128 && code<=191)
    {
        GetBits(s, 3, 1);
        return(2);
    }
    // 003: 192-255 3 bits
    else if(code>=192 && code<=255)
    {
        GetBits(s, 3, 1);
        return(3);
    }
    // 004: 32-63 4 bits
    else if(code>=32 && code<=63)
    {
        GetBits(s, 4, 1);
        return(4);
    }
    // 005: 8-15 6 bits
    else if(code>=8 && code<=15)
    {
        GetBits(s, 6, 1);
        return(5);
    }
    // 006: 16-23 6 bits
    else if(code>=16 && code<=23)
    {
        GetBits(s, 6, 1);
        return(6);
    }
    // 007: 24-31 6 bits
    else if(code>=24 && code<=31)
    {
        GetBits(s, 6, 1);
        return(7);
    }
    // 008: 1-1 9 bits
    else if(code>=1 && code<=1)
    {
        GetBits(s, 9, 1);
        return(8);
    }
    else
    {
        fprintf(stderr, "GetImcbpc error %d\n",code);
        s->error=2;
        return(0xFFFFFFFF);
    }
}

inline unsigned int GetPmcbpc(BitStream *s)
{
    int code=GetBits(s, 13, 0);
    // 000: 4096-8191 1 bits
    if(code>=4096 && code<=8191)
    {
        GetBits(s, 1, 1);
        return(0);
    }
    // 008: 2048-3071 3 bits
    else if(code>=2048 && code<=3071)
    {
        GetBits(s, 3, 1);
        return(8);
    }
    // 004: 3072-4095 3 bits
    else if(code>=3072 && code<=4095)
    {
        GetBits(s, 3, 1);
        return(4);
    }
    // 002: 1024-1535 4 bits
    else if(code>=1024 && code<=1535)
    {
        GetBits(s, 4, 1);
        return(2);
    }
    // 001: 1536-2047 4 bits
    else if(code>=1536 && code<=2047)
    {
        GetBits(s, 4, 1);
        return(1);
    }
    // 012: 768-1023 5 bits
    else if(code>=768 && code<=1023)
    {
        GetBits(s, 5, 1);
        return(12);
    }
    // 016: 512-639 6 bits
    else if(code>=512 && code<=639)
    {
        GetBits(s, 6, 1);
        return(16);
    }
    // 003: 640-767 6 bits
    else if(code>=640 && code<=767)
    {
        GetBits(s, 6, 1);
        return(3);
    }
    // 015: 192-255 7 bits
    else if(code>=192 && code<=255)
    {
        GetBits(s, 7, 1);
        return(15);
    }
    // 010: 256-319 7 bits
    else if(code>=256 && code<=319)
    {
        GetBits(s, 7, 1);
        return(10);
    }
    // 009: 320-383 7 bits
    else if(code>=320 && code<=383)
    {
        GetBits(s, 7, 1);
        return(9);
    }
    // 006: 384-447 7 bits
    else if(code>=384 && code<=447)
    {
        GetBits(s, 7, 1);
        return(6);
    }
    // 005: 448-511 7 bits
    else if(code>=448 && code<=511)
    {
        GetBits(s, 7, 1);
        return(5);
    }
    // 014: 96-127 8 bits
    else if(code>=96 && code<=127)
    {
        GetBits(s, 8, 1);
        return(14);
    }
    // 013: 128-159 8 bits
    else if(code>=128 && code<=159)
    {
        GetBits(s, 8, 1);
        return(13);
    }
    // 011: 160-191 8 bits
    else if(code>=160 && code<=191)
    {
        GetBits(s, 8, 1);
        return(11);
    }
    // 020: 16-31 9 bits
    else if(code>=16 && code<=31)
    {
        GetBits(s, 9, 1);
        return(20);
    }
    // 019: 32-47 9 bits
    else if(code>=32 && code<=47)
    {
        GetBits(s, 9, 1);
        return(19);
    }
    // 018: 48-63 9 bits
    else if(code>=48 && code<=63)
    {
        GetBits(s, 9, 1);
        return(18);
    }
    // 017: 64-79 9 bits
    else if(code>=64 && code<=79)
    {
        GetBits(s, 9, 1);
        return(17);
    }
    // 007: 80-95 9 bits
    else if(code>=80 && code<=95)
    {
        GetBits(s, 9, 1);
        return(7);
    }
    // 021: 8-11 11 bits
    else if(code>=8 && code<=11)
    {
        GetBits(s, 11, 1);
        return(21);
    }
    // 022: 12-12 13 bits
    else if(code>=12 && code<=12)
    {
        GetBits(s, 13, 1);
        return(22);
    }
    // 023: 14-14 13 bits
    else if(code>=14 && code<=14)
    {
        GetBits(s, 13, 1);
        return(23);
    }
    // 024: 15-15 13 bits
    else if(code>=15 && code<=15)
    {
        GetBits(s, 13, 1);
        return(24);
    }
    else
    {
        fprintf(stderr, "GetPmcbpc error %d\n",code);
        s->error=2;
        return(0xFFFFFFFF);
    }
}

inline unsigned int GetCBPY(BitStream *s)
{
    int code=GetBits(s, 6, 0);
    // 015: 48-63 2 bits
    if(code>=48 && code<=63)
    {
        GetBits(s, 2, 1);
        return(15);
    }
    // 000: 12-15 4 bits
    else if(code>=12 && code<=15)
    {
        GetBits(s, 4, 1);
        return(0);
    }
    // 012: 16-19 4 bits
    else if(code>=16 && code<=19)
    {
        GetBits(s, 4, 1);
        return(12);
    }
    // 010: 20-23 4 bits
    else if(code>=20 && code<=23)
    {
        GetBits(s, 4, 1);
        return(10);
    }
    // 014: 24-27 4 bits
    else if(code>=24 && code<=27)
    {
        GetBits(s, 4, 1);
        return(14);
    }
    // 005: 28-31 4 bits
    else if(code>=28 && code<=31)
    {
        GetBits(s, 4, 1);
        return(5);
    }
    // 013: 32-35 4 bits
    else if(code>=32 && code<=35)
    {
        GetBits(s, 4, 1);
        return(13);
    }
    // 003: 36-39 4 bits
    else if(code>=36 && code<=39)
    {
        GetBits(s, 4, 1);
        return(3);
    }
    // 011: 40-43 4 bits
    else if(code>=40 && code<=43)
    {
        GetBits(s, 4, 1);
        return(11);
    }
    // 007: 44-47 4 bits
    else if(code>=44 && code<=47)
    {
        GetBits(s, 4, 1);
        return(7);
    }
    // 008: 4-5 5 bits
    else if(code>=4 && code<=5)
    {
        GetBits(s, 5, 1);
        return(8);
    }
    // 004: 6-7 5 bits
    else if(code>=6 && code<=7)
    {
        GetBits(s, 5, 1);
        return(4);
    }
    // 002: 8-9 5 bits
    else if(code>=8 && code<=9)
    {
        GetBits(s, 5, 1);
        return(2);
    }
    // 001: 10-11 5 bits
    else if(code>=10 && code<=11)
    {
        GetBits(s, 5, 1);
        return(1);
    }
    // 006: 2-2 6 bits
    else if(code>=2 && code<=2)
    {
        GetBits(s, 6, 1);
        return(6);
    }
    // 009: 3-3 6 bits
    else if(code>=3 && code<=3)
    {
        GetBits(s, 6, 1);
        return(9);
    }
    else
    {
        s->error=2;
        fprintf(stderr, "GetCBPY error %d\n",code);
        return(0xFFFFFFFF);
    }
}

inline unsigned int GetINTRATCOEF(BitStream *s)
{
    int code=GetBits(s, 12, 0);
    // 000: 2048-3071 2 bits
    if(code>=2048 && code<=3071)
    {
        GetBits(s, 2, 1);
        return(0);
    }
    // 001: 3072-3583 3 bits
    else if(code>=3072 && code<=3583)
    {
        GetBits(s, 3, 1);
        return(1);
    }
    // 067: 1792-2047 4 bits
    else if(code>=1792 && code<=2047)
    {
        GetBits(s, 4, 1);
        return(67);
    }
    // 027: 3584-3839 4 bits
    else if(code>=3584 && code<=3839)
    {
        GetBits(s, 4, 1);
        return(27);
    }
    // 002: 3840-4095 4 bits
    else if(code>=3840 && code<=4095)
    {
        GetBits(s, 4, 1);
        return(2);
    }
    // 037: 1408-1535 5 bits
    else if(code>=1408 && code<=1535)
    {
        GetBits(s, 5, 1);
        return(37);
    }
    // 004: 1536-1663 5 bits
    else if(code>=1536 && code<=1663)
    {
        GetBits(s, 5, 1);
        return(4);
    }
    // 003: 1664-1791 5 bits
    else if(code>=1664 && code<=1791)
    {
        GetBits(s, 5, 1);
        return(3);
    }
    // 068: 768-831 6 bits
    else if(code>=768 && code<=831)
    {
        GetBits(s, 6, 1);
        return(68);
    }
    // 049: 832-895 6 bits
    else if(code>=832 && code<=895)
    {
        GetBits(s, 6, 1);
        return(49);
    }
    // 078: 896-959 6 bits
    else if(code>=896 && code<=959)
    {
        GetBits(s, 6, 1);
        return(78);
    }
    // 075: 960-1023 6 bits
    else if(code>=960 && code<=1023)
    {
        GetBits(s, 6, 1);
        return(75);
    }
    // 046: 1024-1087 6 bits
    else if(code>=1024 && code<=1087)
    {
        GetBits(s, 6, 1);
        return(46);
    }
    // 042: 1088-1151 6 bits
    else if(code>=1088 && code<=1151)
    {
        GetBits(s, 6, 1);
        return(42);
    }
    // 007: 1152-1215 6 bits
    else if(code>=1152 && code<=1215)
    {
        GetBits(s, 6, 1);
        return(7);
    }
    // 006: 1216-1279 6 bits
    else if(code>=1216 && code<=1279)
    {
        GetBits(s, 6, 1);
        return(6);
    }
    // 028: 1280-1343 6 bits
    else if(code>=1280 && code<=1343)
    {
        GetBits(s, 6, 1);
        return(28);
    }
    // 005: 1344-1407 6 bits
    else if(code>=1344 && code<=1407)
    {
        GetBits(s, 6, 1);
        return(5);
    }
    // 102: 96-127 7 bits
    else if(code>=96 && code<=127)
    {
        GetBits(s, 7, 1);
        return(102);
    }
    // 082: 512-543 7 bits
    else if(code>=512 && code<=543)
    {
        GetBits(s, 7, 1);
        return(82);
    }
    // 080: 544-575 7 bits
    else if(code>=544 && code<=575)
    {
        GetBits(s, 7, 1);
        return(80);
    }
    // 052: 576-607 7 bits
    else if(code>=576 && code<=607)
    {
        GetBits(s, 7, 1);
        return(52);
    }
    // 084: 608-639 7 bits
    else if(code>=608 && code<=639)
    {
        GetBits(s, 7, 1);
        return(84);
    }
    // 055: 640-671 7 bits
    else if(code>=640 && code<=671)
    {
        GetBits(s, 7, 1);
        return(55);
    }
    // 038: 672-703 7 bits
    else if(code>=672 && code<=703)
    {
        GetBits(s, 7, 1);
        return(38);
    }
    // 029: 704-735 7 bits
    else if(code>=704 && code<=735)
    {
        GetBits(s, 7, 1);
        return(29);
    }
    // 008: 736-767 7 bits
    else if(code>=736 && code<=767)
    {
        GetBits(s, 7, 1);
        return(8);
    }
    // 089: 304-319 8 bits
    else if(code>=304 && code<=319)
    {
        GetBits(s, 8, 1);
        return(89);
    }
    // 088: 320-335 8 bits
    else if(code>=320 && code<=335)
    {
        GetBits(s, 8, 1);
        return(88);
    }
    // 086: 336-351 8 bits
    else if(code>=336 && code<=351)
    {
        GetBits(s, 8, 1);
        return(86);
    }
    // 069: 352-367 8 bits
    else if(code>=352 && code<=367)
    {
        GetBits(s, 8, 1);
        return(69);
    }
    // 062: 368-383 8 bits
    else if(code>=368 && code<=383)
    {
        GetBits(s, 8, 1);
        return(62);
    }
    // 060: 384-399 8 bits
    else if(code>=384 && code<=399)
    {
        GetBits(s, 8, 1);
        return(60);
    }
    // 058: 400-415 8 bits
    else if(code>=400 && code<=415)
    {
        GetBits(s, 8, 1);
        return(58);
    }
    // 090: 416-431 8 bits
    else if(code>=416 && code<=431)
    {
        GetBits(s, 8, 1);
        return(90);
    }
    // 043: 432-447 8 bits
    else if(code>=432 && code<=447)
    {
        GetBits(s, 8, 1);
        return(43);
    }
    // 030: 448-463 8 bits
    else if(code>=448 && code<=463)
    {
        GetBits(s, 8, 1);
        return(30);
    }
    // 011: 464-479 8 bits
    else if(code>=464 && code<=479)
    {
        GetBits(s, 8, 1);
        return(11);
    }
    // 010: 480-495 8 bits
    else if(code>=480 && code<=495)
    {
        GetBits(s, 8, 1);
        return(10);
    }
    // 009: 496-511 8 bits
    else if(code>=496 && code<=511)
    {
        GetBits(s, 8, 1);
        return(9);
    }
    // 095: 136-143 9 bits
    else if(code>=136 && code<=143)
    {
        GetBits(s, 9, 1);
        return(95);
    }
    // 094: 144-151 9 bits
    else if(code>=144 && code<=151)
    {
        GetBits(s, 9, 1);
        return(94);
    }
    // 093: 152-159 9 bits
    else if(code>=152 && code<=159)
    {
        GetBits(s, 9, 1);
        return(93);
    }
    // 092: 160-167 9 bits
    else if(code>=160 && code<=167)
    {
        GetBits(s, 9, 1);
        return(92);
    }
    // 091: 168-175 9 bits
    else if(code>=168 && code<=175)
    {
        GetBits(s, 9, 1);
        return(91);
    }
    // 076: 176-183 9 bits
    else if(code>=176 && code<=183)
    {
        GetBits(s, 9, 1);
        return(76);
    }
    // 070: 184-191 9 bits
    else if(code>=184 && code<=191)
    {
        GetBits(s, 9, 1);
        return(70);
    }
    // 064: 192-199 9 bits
    else if(code>=192 && code<=199)
    {
        GetBits(s, 9, 1);
        return(64);
    }
    // 063: 200-207 9 bits
    else if(code>=200 && code<=207)
    {
        GetBits(s, 9, 1);
        return(63);
    }
    // 056: 208-215 9 bits
    else if(code>=208 && code<=215)
    {
        GetBits(s, 9, 1);
        return(56);
    }
    // 053: 216-223 9 bits
    else if(code>=216 && code<=223)
    {
        GetBits(s, 9, 1);
        return(53);
    }
    // 050: 224-231 9 bits
    else if(code>=224 && code<=231)
    {
        GetBits(s, 9, 1);
        return(50);
    }
    // 044: 232-239 9 bits
    else if(code>=232 && code<=239)
    {
        GetBits(s, 9, 1);
        return(44);
    }
    // 039: 240-247 9 bits
    else if(code>=240 && code<=247)
    {
        GetBits(s, 9, 1);
        return(39);
    }
    // 032: 248-255 9 bits
    else if(code>=248 && code<=255)
    {
        GetBits(s, 9, 1);
        return(32);
    }
    // 031: 256-263 9 bits
    else if(code>=256 && code<=263)
    {
        GetBits(s, 9, 1);
        return(31);
    }
    // 015: 264-271 9 bits
    else if(code>=264 && code<=271)
    {
        GetBits(s, 9, 1);
        return(15);
    }
    // 047: 272-279 9 bits
    else if(code>=272 && code<=279)
    {
        GetBits(s, 9, 1);
        return(47);
    }
    // 014: 280-287 9 bits
    else if(code>=280 && code<=287)
    {
        GetBits(s, 9, 1);
        return(14);
    }
    // 013: 288-295 9 bits
    else if(code>=288 && code<=295)
    {
        GetBits(s, 9, 1);
        return(13);
    }
    // 012: 296-303 9 bits
    else if(code>=296 && code<=303)
    {
        GetBits(s, 9, 1);
        return(12);
    }
    // 079: 16-19 10 bits
    else if(code>=16 && code<=19)
    {
        GetBits(s, 10, 1);
        return(79);
    }
    // 077: 20-23 10 bits
    else if(code>=20 && code<=23)
    {
        GetBits(s, 10, 1);
        return(77);
    }
    // 071: 24-27 10 bits
    else if(code>=24 && code<=27)
    {
        GetBits(s, 10, 1);
        return(71);
    }
    // 065: 28-31 10 bits
    else if(code>=28 && code<=31)
    {
        GetBits(s, 10, 1);
        return(65);
    }
    // 051: 32-35 10 bits
    else if(code>=32 && code<=35)
    {
        GetBits(s, 10, 1);
        return(51);
    }
    // 059: 36-39 10 bits
    else if(code>=36 && code<=39)
    {
        GetBits(s, 10, 1);
        return(59);
    }
    // 048: 40-43 10 bits
    else if(code>=40 && code<=43)
    {
        GetBits(s, 10, 1);
        return(48);
    }
    // 045: 44-47 10 bits
    else if(code>=44 && code<=47)
    {
        GetBits(s, 10, 1);
        return(45);
    }
    // 040: 48-51 10 bits
    else if(code>=48 && code<=51)
    {
        GetBits(s, 10, 1);
        return(40);
    }
    // 033: 52-55 10 bits
    else if(code>=52 && code<=55)
    {
        GetBits(s, 10, 1);
        return(33);
    }
    // 019: 56-59 10 bits
    else if(code>=56 && code<=59)
    {
        GetBits(s, 10, 1);
        return(19);
    }
    // 018: 60-63 10 bits
    else if(code>=60 && code<=63)
    {
        GetBits(s, 10, 1);
        return(18);
    }
    // 017: 128-131 10 bits
    else if(code>=128 && code<=131)
    {
        GetBits(s, 10, 1);
        return(17);
    }
    // 016: 132-135 10 bits
    else if(code>=132 && code<=135)
    {
        GetBits(s, 10, 1);
        return(16);
    }
    // 073: 8-9 11 bits
    else if(code>=8 && code<=9)
    {
        GetBits(s, 11, 1);
        return(73);
    }
    // 072: 10-11 11 bits
    else if(code>=10 && code<=11)
    {
        GetBits(s, 11, 1);
        return(72);
    }
    // 021: 12-13 11 bits
    else if(code>=12 && code<=13)
    {
        GetBits(s, 11, 1);
        return(21);
    }
    // 020: 14-15 11 bits
    else if(code>=14 && code<=15)
    {
        GetBits(s, 11, 1);
        return(20);
    }
    // 022: 64-65 11 bits
    else if(code>=64 && code<=65)
    {
        GetBits(s, 11, 1);
        return(22);
    }
    // 023: 66-67 11 bits
    else if(code>=66 && code<=67)
    {
        GetBits(s, 11, 1);
        return(23);
    }
    // 034: 68-69 11 bits
    else if(code>=68 && code<=69)
    {
        GetBits(s, 11, 1);
        return(34);
    }
    // 061: 70-71 11 bits
    else if(code>=70 && code<=71)
    {
        GetBits(s, 11, 1);
        return(61);
    }
    // 081: 72-73 11 bits
    else if(code>=72 && code<=73)
    {
        GetBits(s, 11, 1);
        return(81);
    }
    // 083: 74-75 11 bits
    else if(code>=74 && code<=75)
    {
        GetBits(s, 11, 1);
        return(83);
    }
    // 096: 76-77 11 bits
    else if(code>=76 && code<=77)
    {
        GetBits(s, 11, 1);
        return(96);
    }
    // 097: 78-79 11 bits
    else if(code>=78 && code<=79)
    {
        GetBits(s, 11, 1);
        return(97);
    }
    // 024: 80-80 12 bits
    else if(code>=80 && code<=80)
    {
        GetBits(s, 12, 1);
        return(24);
    }
    // 025: 81-81 12 bits
    else if(code>=81 && code<=81)
    {
        GetBits(s, 12, 1);
        return(25);
    }
    // 026: 82-82 12 bits
    else if(code>=82 && code<=82)
    {
        GetBits(s, 12, 1);
        return(26);
    }
    // 035: 83-83 12 bits
    else if(code>=83 && code<=83)
    {
        GetBits(s, 12, 1);
        return(35);
    }
    // 054: 84-84 12 bits
    else if(code>=84 && code<=84)
    {
        GetBits(s, 12, 1);
        return(54);
    }
    // 036: 85-85 12 bits
    else if(code>=85 && code<=85)
    {
        GetBits(s, 12, 1);
        return(36);
    }
    // 041: 86-86 12 bits
    else if(code>=86 && code<=86)
    {
        GetBits(s, 12, 1);
        return(41);
    }
    // 057: 87-87 12 bits
    else if(code>=87 && code<=87)
    {
        GetBits(s, 12, 1);
        return(57);
    }
    // 066: 88-88 12 bits
    else if(code>=88 && code<=88)
    {
        GetBits(s, 12, 1);
        return(66);
    }
    // 074: 89-89 12 bits
    else if(code>=89 && code<=89)
    {
        GetBits(s, 12, 1);
        return(74);
    }
    // 085: 90-90 12 bits
    else if(code>=90 && code<=90)
    {
        GetBits(s, 12, 1);
        return(85);
    }
    // 087: 91-91 12 bits
    else if(code>=91 && code<=91)
    {
        GetBits(s, 12, 1);
        return(87);
    }
    // 098: 92-92 12 bits
    else if(code>=92 && code<=92)
    {
        GetBits(s, 12, 1);
        return(98);
    }
    // 099: 93-93 12 bits
    else if(code>=93 && code<=93)
    {
        GetBits(s, 12, 1);
        return(99);
    }
    // 100: 94-94 12 bits
    else if(code>=94 && code<=94)
    {
        GetBits(s, 12, 1);
        return(100);
    }
    // 101: 95-95 12 bits
    else if(code>=95 && code<=95)
    {
        GetBits(s, 12, 1);
        return(101);
    }
    else
    {
        s->error=2;
        fprintf(stderr, "GetIntraTCOEFF error %d\n",code);
        return(0xFFFFFFFF);
    }
}

inline unsigned int GetTCOEF(BitStream *s)
{
    int code=GetBits(s, 12, 0);
    // 000: 2048-3071 2 bits
    if(code>=2048 && code<=3071)
    {
        GetBits(s, 2, 1);
        return(0);
    }
    // 012: 3072-3583 3 bits
    else if(code>=3072 && code<=3583)
    {
        GetBits(s, 3, 1);
        return(12);
    }
    // 058: 1792-2047 4 bits
    else if(code>=1792 && code<=2047)
    {
        GetBits(s, 4, 1);
        return(58);
    }
    // 018: 3584-3839 4 bits
    else if(code>=3584 && code<=3839)
    {
        GetBits(s, 4, 1);
        return(18);
    }
    // 001: 3840-4095 4 bits
    else if(code>=3840 && code<=4095)
    {
        GetBits(s, 4, 1);
        return(1);
    }
    // 028: 1408-1535 5 bits
    else if(code>=1408 && code<=1535)
    {
        GetBits(s, 5, 1);
        return(28);
    }
    // 025: 1536-1663 5 bits
    else if(code>=1536 && code<=1663)
    {
        GetBits(s, 5, 1);
        return(25);
    }
    // 022: 1664-1791 5 bits
    else if(code>=1664 && code<=1791)
    {
        GetBits(s, 5, 1);
        return(22);
    }
    // 065: 768-831 6 bits
    else if(code>=768 && code<=831)
    {
        GetBits(s, 6, 1);
        return(65);
    }
    // 064: 832-895 6 bits
    else if(code>=832 && code<=895)
    {
        GetBits(s, 6, 1);
        return(64);
    }
    // 063: 896-959 6 bits
    else if(code>=896 && code<=959)
    {
        GetBits(s, 6, 1);
        return(63);
    }
    // 061: 960-1023 6 bits
    else if(code>=960 && code<=1023)
    {
        GetBits(s, 6, 1);
        return(61);
    }
    // 038: 1024-1087 6 bits
    else if(code>=1024 && code<=1087)
    {
        GetBits(s, 6, 1);
        return(38);
    }
    // 036: 1088-1151 6 bits
    else if(code>=1088 && code<=1151)
    {
        GetBits(s, 6, 1);
        return(36);
    }
    // 034: 1152-1215 6 bits
    else if(code>=1152 && code<=1215)
    {
        GetBits(s, 6, 1);
        return(34);
    }
    // 031: 1216-1279 6 bits
    else if(code>=1216 && code<=1279)
    {
        GetBits(s, 6, 1);
        return(31);
    }
    // 013: 1280-1343 6 bits
    else if(code>=1280 && code<=1343)
    {
        GetBits(s, 6, 1);
        return(13);
    }
    // 002: 1344-1407 6 bits
    else if(code>=1344 && code<=1407)
    {
        GetBits(s, 6, 1);
        return(2);
    }
    // 102: 96-127 7 bits
    else if(code>=96 && code<=127)
    {
        GetBits(s, 7, 1);
        return(102);
    }
    // 069: 512-543 7 bits
    else if(code>=512 && code<=543)
    {
        GetBits(s, 7, 1);
        return(69);
    }
    // 068: 544-575 7 bits
    else if(code>=544 && code<=575)
    {
        GetBits(s, 7, 1);
        return(68);
    }
    // 067: 576-607 7 bits
    else if(code>=576 && code<=607)
    {
        GetBits(s, 7, 1);
        return(67);
    }
    // 066: 608-639 7 bits
    else if(code>=608 && code<=639)
    {
        GetBits(s, 7, 1);
        return(66);
    }
    // 043: 640-671 7 bits
    else if(code>=640 && code<=671)
    {
        GetBits(s, 7, 1);
        return(43);
    }
    // 042: 672-703 7 bits
    else if(code>=672 && code<=703)
    {
        GetBits(s, 7, 1);
        return(42);
    }
    // 040: 704-735 7 bits
    else if(code>=704 && code<=735)
    {
        GetBits(s, 7, 1);
        return(40);
    }
    // 003: 736-767 7 bits
    else if(code>=736 && code<=767)
    {
        GetBits(s, 7, 1);
        return(3);
    }
    // 077: 304-319 8 bits
    else if(code>=304 && code<=319)
    {
        GetBits(s, 8, 1);
        return(77);
    }
    // 076: 320-335 8 bits
    else if(code>=320 && code<=335)
    {
        GetBits(s, 8, 1);
        return(76);
    }
    // 075: 336-351 8 bits
    else if(code>=336 && code<=351)
    {
        GetBits(s, 8, 1);
        return(75);
    }
    // 074: 352-367 8 bits
    else if(code>=352 && code<=367)
    {
        GetBits(s, 8, 1);
        return(74);
    }
    // 073: 368-383 8 bits
    else if(code>=368 && code<=383)
    {
        GetBits(s, 8, 1);
        return(73);
    }
    // 072: 384-399 8 bits
    else if(code>=384 && code<=399)
    {
        GetBits(s, 8, 1);
        return(72);
    }
    // 071: 400-415 8 bits
    else if(code>=400 && code<=415)
    {
        GetBits(s, 8, 1);
        return(71);
    }
    // 070: 416-431 8 bits
    else if(code>=416 && code<=431)
    {
        GetBits(s, 8, 1);
        return(70);
    }
    // 045: 432-447 8 bits
    else if(code>=432 && code<=447)
    {
        GetBits(s, 8, 1);
        return(45);
    }
    // 044: 448-463 8 bits
    else if(code>=448 && code<=463)
    {
        GetBits(s, 8, 1);
        return(44);
    }
    // 019: 464-479 8 bits
    else if(code>=464 && code<=479)
    {
        GetBits(s, 8, 1);
        return(19);
    }
    // 014: 480-495 8 bits
    else if(code>=480 && code<=495)
    {
        GetBits(s, 8, 1);
        return(14);
    }
    // 004: 496-511 8 bits
    else if(code>=496 && code<=511)
    {
        GetBits(s, 8, 1);
        return(4);
    }
    // 085: 136-143 9 bits
    else if(code>=136 && code<=143)
    {
        GetBits(s, 9, 1);
        return(85);
    }
    // 084: 144-151 9 bits
    else if(code>=144 && code<=151)
    {
        GetBits(s, 9, 1);
        return(84);
    }
    // 083: 152-159 9 bits
    else if(code>=152 && code<=159)
    {
        GetBits(s, 9, 1);
        return(83);
    }
    // 082: 160-167 9 bits
    else if(code>=160 && code<=167)
    {
        GetBits(s, 9, 1);
        return(82);
    }
    // 081: 168-175 9 bits
    else if(code>=168 && code<=175)
    {
        GetBits(s, 9, 1);
        return(81);
    }
    // 080: 176-183 9 bits
    else if(code>=176 && code<=183)
    {
        GetBits(s, 9, 1);
        return(80);
    }
    // 079: 184-191 9 bits
    else if(code>=184 && code<=191)
    {
        GetBits(s, 9, 1);
        return(79);
    }
    // 078: 192-199 9 bits
    else if(code>=192 && code<=199)
    {
        GetBits(s, 9, 1);
        return(78);
    }
    // 059: 200-207 9 bits
    else if(code>=200 && code<=207)
    {
        GetBits(s, 9, 1);
        return(59);
    }
    // 053: 208-215 9 bits
    else if(code>=208 && code<=215)
    {
        GetBits(s, 9, 1);
        return(53);
    }
    // 052: 216-223 9 bits
    else if(code>=216 && code<=223)
    {
        GetBits(s, 9, 1);
        return(52);
    }
    // 051: 224-231 9 bits
    else if(code>=224 && code<=231)
    {
        GetBits(s, 9, 1);
        return(51);
    }
    // 050: 232-239 9 bits
    else if(code>=232 && code<=239)
    {
        GetBits(s, 9, 1);
        return(50);
    }
    // 049: 240-247 9 bits
    else if(code>=240 && code<=247)
    {
        GetBits(s, 9, 1);
        return(49);
    }
    // 048: 248-255 9 bits
    else if(code>=248 && code<=255)
    {
        GetBits(s, 9, 1);
        return(48);
    }
    // 047: 256-263 9 bits
    else if(code>=256 && code<=263)
    {
        GetBits(s, 9, 1);
        return(47);
    }
    // 046: 264-271 9 bits
    else if(code>=264 && code<=271)
    {
        GetBits(s, 9, 1);
        return(46);
    }
    // 026: 272-279 9 bits
    else if(code>=272 && code<=279)
    {
        GetBits(s, 9, 1);
        return(26);
    }
    // 023: 280-287 9 bits
    else if(code>=280 && code<=287)
    {
        GetBits(s, 9, 1);
        return(23);
    }
    // 006: 288-295 9 bits
    else if(code>=288 && code<=295)
    {
        GetBits(s, 9, 1);
        return(6);
    }
    // 005: 296-303 9 bits
    else if(code>=296 && code<=303)
    {
        GetBits(s, 9, 1);
        return(5);
    }
    // 089: 16-19 10 bits
    else if(code>=16 && code<=19)
    {
        GetBits(s, 10, 1);
        return(89);
    }
    // 088: 20-23 10 bits
    else if(code>=20 && code<=23)
    {
        GetBits(s, 10, 1);
        return(88);
    }
    // 087: 24-27 10 bits
    else if(code>=24 && code<=27)
    {
        GetBits(s, 10, 1);
        return(87);
    }
    // 086: 28-31 10 bits
    else if(code>=28 && code<=31)
    {
        GetBits(s, 10, 1);
        return(86);
    }
    // 039: 32-35 10 bits
    else if(code>=32 && code<=35)
    {
        GetBits(s, 10, 1);
        return(39);
    }
    // 037: 36-39 10 bits
    else if(code>=36 && code<=39)
    {
        GetBits(s, 10, 1);
        return(37);
    }
    // 035: 40-43 10 bits
    else if(code>=40 && code<=43)
    {
        GetBits(s, 10, 1);
        return(35);
    }
    // 032: 44-47 10 bits
    else if(code>=44 && code<=47)
    {
        GetBits(s, 10, 1);
        return(32);
    }
    // 029: 48-51 10 bits
    else if(code>=48 && code<=51)
    {
        GetBits(s, 10, 1);
        return(29);
    }
    // 024: 52-55 10 bits
    else if(code>=52 && code<=55)
    {
        GetBits(s, 10, 1);
        return(24);
    }
    // 020: 56-59 10 bits
    else if(code>=56 && code<=59)
    {
        GetBits(s, 10, 1);
        return(20);
    }
    // 015: 60-63 10 bits
    else if(code>=60 && code<=63)
    {
        GetBits(s, 10, 1);
        return(15);
    }
    // 008: 128-131 10 bits
    else if(code>=128 && code<=131)
    {
        GetBits(s, 10, 1);
        return(8);
    }
    // 007: 132-135 10 bits
    else if(code>=132 && code<=135)
    {
        GetBits(s, 10, 1);
        return(7);
    }
    // 062: 8-9 11 bits
    else if(code>=8 && code<=9)
    {
        GetBits(s, 11, 1);
        return(62);
    }
    // 060: 10-11 11 bits
    else if(code>=10 && code<=11)
    {
        GetBits(s, 11, 1);
        return(60);
    }
    // 010: 12-13 11 bits
    else if(code>=12 && code<=13)
    {
        GetBits(s, 11, 1);
        return(10);
    }
    // 009: 14-15 11 bits
    else if(code>=14 && code<=15)
    {
        GetBits(s, 11, 1);
        return(9);
    }
    // 011: 64-65 11 bits
    else if(code>=64 && code<=65)
    {
        GetBits(s, 11, 1);
        return(11);
    }
    // 016: 66-67 11 bits
    else if(code>=66 && code<=67)
    {
        GetBits(s, 11, 1);
        return(16);
    }
    // 054: 68-69 11 bits
    else if(code>=68 && code<=69)
    {
        GetBits(s, 11, 1);
        return(54);
    }
    // 055: 70-71 11 bits
    else if(code>=70 && code<=71)
    {
        GetBits(s, 11, 1);
        return(55);
    }
    // 090: 72-73 11 bits
    else if(code>=72 && code<=73)
    {
        GetBits(s, 11, 1);
        return(90);
    }
    // 091: 74-75 11 bits
    else if(code>=74 && code<=75)
    {
        GetBits(s, 11, 1);
        return(91);
    }
    // 092: 76-77 11 bits
    else if(code>=76 && code<=77)
    {
        GetBits(s, 11, 1);
        return(92);
    }
    // 093: 78-79 11 bits
    else if(code>=78 && code<=79)
    {
        GetBits(s, 11, 1);
        return(93);
    }
    // 017: 80-80 12 bits
    else if(code>=80 && code<=80)
    {
        GetBits(s, 12, 1);
        return(17);
    }
    // 021: 81-81 12 bits
    else if(code>=81 && code<=81)
    {
        GetBits(s, 12, 1);
        return(21);
    }
    // 027: 82-82 12 bits
    else if(code>=82 && code<=82)
    {
        GetBits(s, 12, 1);
        return(27);
    }
    // 030: 83-83 12 bits
    else if(code>=83 && code<=83)
    {
        GetBits(s, 12, 1);
        return(30);
    }
    // 033: 84-84 12 bits
    else if(code>=84 && code<=84)
    {
        GetBits(s, 12, 1);
        return(33);
    }
    // 041: 85-85 12 bits
    else if(code>=85 && code<=85)
    {
        GetBits(s, 12, 1);
        return(41);
    }
    // 056: 86-86 12 bits
    else if(code>=86 && code<=86)
    {
        GetBits(s, 12, 1);
        return(56);
    }
    // 057: 87-87 12 bits
    else if(code>=87 && code<=87)
    {
        GetBits(s, 12, 1);
        return(57);
    }
    // 094: 88-88 12 bits
    else if(code>=88 && code<=88)
    {
        GetBits(s, 12, 1);
        return(94);
    }
    // 095: 89-89 12 bits
    else if(code>=89 && code<=89)
    {
        GetBits(s, 12, 1);
        return(95);
    }
    // 096: 90-90 12 bits
    else if(code>=90 && code<=90)
    {
        GetBits(s, 12, 1);
        return(96);
    }
    // 097: 91-91 12 bits
    else if(code>=91 && code<=91)
    {
        GetBits(s, 12, 1);
        return(97);
    }
    // 098: 92-92 12 bits
    else if(code>=92 && code<=92)
    {
        GetBits(s, 12, 1);
        return(98);
    }
    // 099: 93-93 12 bits
    else if(code>=93 && code<=93)
    {
        GetBits(s, 12, 1);
        return(99);
    }
    // 100: 94-94 12 bits
    else if(code>=94 && code<=94)
    {
        GetBits(s, 12, 1);
        return(100);
    }
    // 101: 95-95 12 bits
    else if(code>=95 && code<=95)
    {
        GetBits(s, 12, 1);
        return(101);
    }
    else
    {
        s->error=2;
        fprintf(stderr, "GetTCOEFF error %d\n",code);
        return(0xFFFFFFFF);
    }
}

inline unsigned int Getmvd(BitStream *s)
{
    int code=GetBits(s, 12, 0);
    // 000: 2048-4095 1 bits
    if(code>=2048 && code<=4095)
    {
        GetBits(s, 1, 1);
        return(0);
    }
    // 001: 1024-2047 2 bits
    else if(code>=1024 && code<=2047)
    {
        GetBits(s, 2, 1);
        return(1);
    }
    // 002: 512-1023 3 bits
    else if(code>=512 && code<=1023)
    {
        GetBits(s, 3, 1);
        return(2);
    }
    // 003: 256-511 4 bits
    else if(code>=256 && code<=511)
    {
        GetBits(s, 4, 1);
        return(3);
    }
    // 004: 192-255 6 bits
    else if(code>=192 && code<=255)
    {
        GetBits(s, 6, 1);
        return(4);
    }
    // 007: 96-127 7 bits
    else if(code>=96 && code<=127)
    {
        GetBits(s, 7, 1);
        return(7);
    }
    // 006: 128-159 7 bits
    else if(code>=128 && code<=159)
    {
        GetBits(s, 7, 1);
        return(6);
    }
    // 005: 160-191 7 bits
    else if(code>=160 && code<=191)
    {
        GetBits(s, 7, 1);
        return(5);
    }
    // 010: 72-79 9 bits
    else if(code>=72 && code<=79)
    {
        GetBits(s, 9, 1);
        return(10);
    }
    // 009: 80-87 9 bits
    else if(code>=80 && code<=87)
    {
        GetBits(s, 9, 1);
        return(9);
    }
    // 008: 88-95 9 bits
    else if(code>=88 && code<=95)
    {
        GetBits(s, 9, 1);
        return(8);
    }
    // 024: 16-19 10 bits
    else if(code>=16 && code<=19)
    {
        GetBits(s, 10, 1);
        return(24);
    }
    // 023: 20-23 10 bits
    else if(code>=20 && code<=23)
    {
        GetBits(s, 10, 1);
        return(23);
    }
    // 022: 24-27 10 bits
    else if(code>=24 && code<=27)
    {
        GetBits(s, 10, 1);
        return(22);
    }
    // 021: 28-31 10 bits
    else if(code>=28 && code<=31)
    {
        GetBits(s, 10, 1);
        return(21);
    }
    // 020: 32-35 10 bits
    else if(code>=32 && code<=35)
    {
        GetBits(s, 10, 1);
        return(20);
    }
    // 019: 36-39 10 bits
    else if(code>=36 && code<=39)
    {
        GetBits(s, 10, 1);
        return(19);
    }
    // 018: 40-43 10 bits
    else if(code>=40 && code<=43)
    {
        GetBits(s, 10, 1);
        return(18);
    }
    // 017: 44-47 10 bits
    else if(code>=44 && code<=47)
    {
        GetBits(s, 10, 1);
        return(17);
    }
    // 016: 48-51 10 bits
    else if(code>=48 && code<=51)
    {
        GetBits(s, 10, 1);
        return(16);
    }
    // 015: 52-55 10 bits
    else if(code>=52 && code<=55)
    {
        GetBits(s, 10, 1);
        return(15);
    }
    // 014: 56-59 10 bits
    else if(code>=56 && code<=59)
    {
        GetBits(s, 10, 1);
        return(14);
    }
    // 013: 60-63 10 bits
    else if(code>=60 && code<=63)
    {
        GetBits(s, 10, 1);
        return(13);
    }
    // 012: 64-67 10 bits
    else if(code>=64 && code<=67)
    {
        GetBits(s, 10, 1);
        return(12);
    }
    // 011: 68-71 10 bits
    else if(code>=68 && code<=71)
    {
        GetBits(s, 10, 1);
        return(11);
    }
    // 030: 4-5 11 bits
    else if(code>=4 && code<=5)
    {
        GetBits(s, 11, 1);
        return(30);
    }
    // 029: 6-7 11 bits
    else if(code>=6 && code<=7)
    {
        GetBits(s, 11, 1);
        return(29);
    }
    // 028: 8-9 11 bits
    else if(code>=8 && code<=9)
    {
        GetBits(s, 11, 1);
        return(28);
    }
    // 027: 10-11 11 bits
    else if(code>=10 && code<=11)
    {
        GetBits(s, 11, 1);
        return(27);
    }
    // 026: 12-13 11 bits
    else if(code>=12 && code<=13)
    {
        GetBits(s, 11, 1);
        return(26);
    }
    // 025: 14-15 11 bits
    else if(code>=14 && code<=15)
    {
        GetBits(s, 11, 1);
        return(25);
    }
    // 032: 2-2 12 bits
    else if(code>=2 && code<=2)
    {
        GetBits(s, 12, 1);
        return(32);
    }
    // 031: 3-3 12 bits
    else if(code>=3 && code<=3)
    {
        GetBits(s, 12, 1);
        return(31);
    }
    else
    {
        s->error=2;
        fprintf(stderr, "GetMVD error %d\n",code);
        return(0xFFFFFFFF);
    }
}

//need these for 'inline' to work
unsigned int GetImcbpc(BitStream *s);
unsigned int GetPmcbpc(BitStream *s);
unsigned int GetCBPY(BitStream *s);
unsigned int GetINTRATCOEF(BitStream *s);
unsigned int GetTCOEF(BitStream *s);
unsigned int Getmvd(BitStream *s);
