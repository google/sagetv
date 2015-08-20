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


#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"
#include "ESAnalyzer.h"
#include "TSInfoParser.h"

int PidDump( void* Context, unsigned char* pData, int Size )
{
	TS_INFO_PARSER* pTSInfoParser = (TS_INFO_PARSER*)Context;
	PID_ITEM *pPidInf = (PID_ITEM*)pData;

	printf( "Got pid:0x%x type:%d\n", pPidInf->pid, pPidInf->type );
	return 1;
}
int AVInfDump( void* Context, unsigned char* pData, int Size )
{
	TS_INFO_PARSER* pTSInfoParser = (TS_INFO_PARSER*)Context;
	PID_ITEM *pPidInf = (PID_ITEM*)pData;

	printf( "AVINF:%s\n", pData );
	return 1;
}

int main( int argc, char* argv[] )
{
	FILE *fp = fopen( argv[1], "rb" );
	char buf[32*1024];
	int read_size = sizeof(buf)-sizeof(buf)/188;
	int wrote_bytes = 0, read_bytes = 0;
	int test_size = 60000*1024;
	TS_INFO_PARSER* pTSInfoParser;
	TUNE tune;

	_enable_native_log( );
	if ( fp == NULL )
	{
		printf( "can't open file %s\n", argv[1] );
		return 1;
	}
	tune.stream_format = ATSC_STREAM;
	tune.sub_format = TERRESTRIAL;
	tune.u.atsc.major_num = 7;
	tune.u.atsc.minor_num = 1;
	tune.u.atsc.physical_ch = 7;
	tune.u.atsc.program_id = 0;

	pTSInfoParser = CreateTSInfoParser( 32, ATSC_STREAM, TERRESTRIAL );
	SetupTSInfDump( pTSInfoParser, 
					PidDump, pTSInfoParser,
					NULL, pTSInfoParser,
					AVInfDump,pTSInfoParser,
					NULL, pTSInfoParser,
					NULL, pTSInfoParser );

	SetupTSTune( pTSInfoParser, &tune );
	while ( !feof(fp) )
	{
		int bytes, ct;
		bytes = (int)fread( buf, 1, read_size, fp );
		ct = PushTSPacketParser( pTSInfoParser, buf, bytes );
		wrote_bytes += bytes;
		read_bytes += read_size;
		if ( test_size < read_bytes )
			break;
	}
	GetAVInf( pTSInfoParser, buf, sizeof(buf) );
	printf( "%s", buf );
	fclose( fp );
	ReleaseTSInfoParser( pTSInfoParser );
	return 0;
}
