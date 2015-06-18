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
// FormatDect.cpp : Defines the entry point for the console application.
//

#include <stdio.h>
#include <stdlib.h>	
#include <memory.h>	
#include <string.h>	

#include "TSnative.h"

#ifdef __cplusplus
extern "C" {
#endif
extern int GetAVInf( char* FileName, unsigned long CheckSize, bool bLiveFile, int RequestedTSProgram, 
			   char* FormatBuf, int FormatSize, char* DurationBuf, int DurationSize, int* Program );
#ifdef __cplusplus
}

#else
extern int GetAVInf( char* FileName, unsigned long CheckSize, bool bLiveFile, int RequestedTSProgram, 
			   char* FormatBuf, int FormatSize, char* DurationBuf, int DurationSize, int* Program );

#endif


int  main(int argc, char* argv[])
{
	//TS_PARSER* ts = TSParserOpen(); 
	char Format[512]={0}, Duration[32];
	int ProgramNum = 0;
	int channel = 0;
	int SearchSize = 1207959552;///10;
	int ret;
	if ( argc < 2 )
	{
		puts( "Usage: FormatDect filename" );
		return 0;
	}

	//retreve programs av information from TS/PS info
	do {
		ret = GetAVInf( argv[1], SearchSize, false, channel, Format, sizeof(Format), Duration, sizeof(Duration), &ProgramNum );
		if ( ret < 0 ) break;
		channel++;
		printf( "File:%s\n%d. Format:%s Duration:%s Program Num:%d\n", argv[1], channel, Format, Duration, ProgramNum );
	}
	while ( channel < ProgramNum );

	if ( ret < 0 )
		printf( "%s is not found\n", argv[1] );
	return 0;	
}

#ifdef __cplusplus
}
#endif

