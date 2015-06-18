/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define TITLE_HUFFMAN_TBL_SIZE		1940
#define PROGRAM_HUFFMAN_TBL_SIZE	1652

// extern unsigned char title_huffman_tbl[];
// extern unsigned char program_huffman_tbl[];
// extern char *genre_code[];

//walking around gcc bug of "symbol is already defined"
char* GenreCode( int code );
unsigned char* TitleHuffmanTbl();
unsigned char* ProgramHuffmanTbl();

