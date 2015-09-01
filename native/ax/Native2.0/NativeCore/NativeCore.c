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

#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"
#include "PSParser.h"
#include "ESAnalyzer.h"
#include "Remuxer.h"
#include "Demuxer.h"
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>


#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _sagelog x
#endif

#include <time.h>
#ifdef WIN32   
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}
#endif

int sagelog_enabled=0;
int console_enabled = 1;
int trace_level = 3;
char log_filename[128]="Native.log";

void _enable_native_log( )
{
	sagelog_enabled=1;
}
void _disable_native_log( )
{
	sagelog_enabled=0;
}
void _sagelog( int type, int level, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
    char szInfo[1024*3];
    va_list va;
	FILE* fp;
	 
	if ( !sagelog_enabled ) return;
	if ( type == _LOG_TRACE && level > trace_level ) return;

	fp = fopen( log_filename, "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );

	
    va_start(va, cstr);

	snprintf( szInfo, sizeof(szInfo)-3, TEXT("%02d/%02d/%d %02d:%02d:%02d "), ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
			ltm.tm_hour, ltm.tm_min, ltm.tm_sec  );  

    vsnprintf(szInfo + lstrlen(szInfo), (int)sizeof(szInfo)-lstrlen(szInfo)-3, cstr, va);
    lstrcat(szInfo, TEXT("\r\n"));
	fwrite( (wchar_t*)szInfo, 1, lstrlen(szInfo),  fp ); 

	if ( console_enabled )
		printf( "%s", szInfo );

	va_end(args);
	fclose( fp );
}

void _flog_setup( char* filename, int enable )
{
	if ( filename != NULL && filename[0] )
		strncpy( log_filename, filename, sizeof( log_filename ) );

	sagelog_enabled = enable;
}

char* _get_flog_path(  )
{
	return log_filename;
}

int _get_flog_state( )
{
	return sagelog_enabled;
}

int _flog_check()
{
	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		sagelog_enabled = 1;
		fclose( fp );
		return 1;
	}
	return 0;
}

char* CodeConfig( )
{
	int config = 0;
	static char config_string[128], *p=config_string;
	int len=sizeof(config_string), pos = 0;
	//int tune_size = sizeof( TUNE );

	TUNE_SIZE_CHECK( );
#ifdef MEMORY_CHECK
	config |= 0x02;
#endif
#ifndef REMOVE_LOG	
	config |= 0x01;
#endif

	pos += snprintf( p+pos, len-pos, "log:%s ", (config & 0x1)? "ON":"OFF"  );
	pos += snprintf( p+pos, len-pos, "mem tracing:%s ", (config & 0x2)? "ON":"OFF" );
	//pos += snprintf( p+pos, len-pos, "console:%s ", (config & 0x4)? "ON":"OFF" );
	return config_string;
}


//#define MAX_TRACK_NUM	 6
//#define ES_BLOCK_SZIE	 4*1024
//#define ES_BUFFER_SIZE   (ES_BLOCK_SZIE+512)

// static  memory buffer
//unsigned char es_buffer[MAX_SLOT_NUM][MAX_TRACK_NUM][ES_BUFFER_SIZE];

char* _sagetv_fourcc_( uint32_t lFourCC, char* pFourCC )
{
	static unsigned char _tmp_[5], *p;
	if ( lFourCC == 0 )
		return "";

	if ( pFourCC == NULL )
		p = _tmp_;
	else
		p = (unsigned char*)pFourCC;

#ifdef BIG_EDIAN
	p[0] = (unsigned char)((lFourCC >> 24) & 0xff);
	p[1] = (unsigned char)((lFourCC >> 16) & 0xff);
	p[2] = (unsigned char)((lFourCC >>  8) & 0xff);
	p[3] = (unsigned char)((lFourCC ) & 0xff);
#else
	p[3] = (unsigned char)((lFourCC >> 24) & 0xff);
	p[2] = (unsigned char)((lFourCC >> 16) & 0xff);
	p[1] = (unsigned char)((lFourCC >>  8) & 0xff);
	p[0] = (unsigned char)((lFourCC ) & 0xff);
#endif
	p[4] = 0x0;

	return (char*)p;
}

char* _data_alignment_check_( char* buf, int buf_len )
{
	int es_elmnt_size = sizeof( ES_ELEMENT );
	int ts_elmnt_size = sizeof( TS_ELEMENT );
	int av_elmnt_size = sizeof( AV_ELEMENT );
	int es_streams_size = sizeof( ES_STREAMS );
	int ts_streams_size = sizeof( TS_STREAMS );
	int av_streams_size = sizeof( AV_STREAMS );

	int tune_size = sizeof( TUNE );
	int pid_table_size = sizeof( PIDS_TABLE );
	int pid_entry_size = sizeof( PID_ENTRY );
	int output_data_size = sizeof( OUTPUT_DATA );
	int data_buffer_size = sizeof( DATA_BUFFER );
	int message_size = sizeof( MESSAGE_DATA );
	int program_data_size = sizeof( PROGRAM_DATA );
	int ts_parser_size = sizeof(TS_PARSER);
	int ts_filter_size = sizeof(TS_FILTER);

	snprintf( buf, buf_len, "ts:%d,%d es:%d,%d, av:%d,%d tune:%d pid:%d,%d buf:%d,%d mesg:%d prog:%d, ts_p:%d ts_f:%d",
		ts_elmnt_size, ts_streams_size, es_elmnt_size, es_streams_size, av_elmnt_size, av_streams_size, 
		tune_size, pid_table_size, pid_entry_size, data_buffer_size, output_data_size, message_size, program_data_size,
		ts_parser_size, ts_filter_size );

	return buf;
}

char* _access_alignment_check_( char* buf, int buf_len )
{
	typedef struct {
		char     a;
		char     b;
		int16_t  c;
		int16_t  d;
		int32_t  e;
	} A;
	typedef struct {
		char    a;
		int16_t b;
		int32_t c;
	} B;

	A a;
	B b;
	char* base_a = &a.a;
	char* a_b_p  = &a.b;
	char* a_c_p  = (char*)&a.c;
	char* a_d_p  = (char*)&a.d;
	char* a_e_p  = (char*)&a.e;

	char* base_b = (char*)&b.a;
	char* b_b_p  = (char*)&b.b;
	char* b_c_p  = (char*)&b.c;
	//char* b_d_p  = (char*)&b.d;
	snprintf( buf, buf_len, "a: %ld %ld %ld %ld; b:%ld %ld", 
		      a_b_p-base_a, a_c_p-base_a, a_d_p-base_a, a_e_p-base_a, 
			    b_b_p-base_b, b_c_p-base_b );

	return buf;

}

///////////////////////////////////////////////////////////////////////////////////////////////////////////

