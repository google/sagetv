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
#include "Remuxer.h"
#include "TSFilter.h"
#include "TSParser.h"
#include "TSFilterDump.h"

#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <time.h>

///////////////////////////////////////////////////////////////////////////////////////////////////////////
//test pash parser
///////////////////////////////////////////////////////////////////////////////////////////////////////////
#define TEST_SCRIPT    0x100

int CheckFormat( const unsigned char* pData, int nBytes );
static int ParseFileName( char *pData, char* pFileName, int nMaxLen )
{
	char *p;
	int len;
	while ( *pData && ( *pData == ' ' || *pData == '\t' ) ) pData++;
	if ( *pData == 0 )
		return 0;
	if ( *pData == '\"' ) //"name abc-bb.c"
	{	p = pData;
		while ( *p && *p!= '\"' ) p++;
		if ( *p == '\"' && p - pData < nMaxLen )
		{
			len = _MIN( (int)(p - pData), nMaxLen-1 );
			memcpy( pFileName, pData, len );
			pFileName[len] = 0x0;
			return len;
		}
	} else
	{
		p =pData;
		while ( *p && *p!= ' ' ) p++;
		if ( p-pData < nMaxLen )
		{
			len = _MIN( (int)(p - pData), nMaxLen-1 );
			memcpy( pFileName, pData, len );
			pFileName[len] = 0x0;
			return len;
		}
	}

	return 0;
}

static int ParseTaskArg( char* pString )
{
	int task = 0;
	char* p = pString;
	if ( strstr( p, "remux_file" ) )
		task = REMUX_FILE;
	else
	if ( strstr( p, "remux_stream" ) )
		task = REMUX_STREAM;
	else
	if ( strstr( p, "quick_fix" ) )
		task = QUICK_FIX;
	else
	if ( strstr( p, "file_avinf" ) )
		task = FILE_AVINF;
	else
	if ( strstr( p, "stream_avinf" ) )
		task = STREAM_AVINF;
	else
	if ( strstr( p, "pts_check" ) )
		task = PTS_CHECK;
		
	return task;
}

static int ParseFormatArg( char* pString )
{
	char* p = pString;
	int format = 0;
	if ( strstr( p, "TS" ) )
		format = MPEG_TS;
	else
	if ( strstr( p, "M2TS" ) )
		format = MPEG_M2TS;
	else
	if ( strstr( p, "ASI" ) )
		format = MPEG_ASI;
	else
	if ( strstr( p, "PS" ) || strstr( p, "MPEG" ) )
		format = MPEG_PS;
		
	return format;
}

static int GetDigitsNum( char* pString, int *pNum )
{
	int i = 0;
	int num = 0;
	while( pString[i] == ' ' ) i++;

	while ( pString[i] >= '0' && pString[i] <= '9' )
	{
		num = num*10 + pString[i]-'0';
		i++;
	}

	if ( i > 0 ) 
		*pNum = num;

	return i;
}

static int GetDescString( char* pString, char* pDesc, int nMaxLen )
{
	int i = 0;
	while( pString[i] == ' ' ) i++;
	if ( pString[i] !='[' ) return 0;
	i++;

	while ( pString[i] != ']' && pString[i] )
	{
		if ( nMaxLen-- > 0 )
			*pDesc++ = pString[i];
		i++;
	}

	if ( pString[i] == ']' )
		return i+1;

	return 0;
}

//format: "Vnnn[desc] Annn[desc] Snnn[desc] Pnnn"
//nnn is pid; V for Video; A for Audio; P for PCR;
//desc is 16 bytes of information of codec and language (in form of :lll: ), 
//example: "V65 A67 P65", "V165[H264] A167[AC3:eng:] A166[MPGA:fra:] S168  P165"
static int ParsePidsString( PIDS_TABLE* pPidsTable, char *pPidsString )
{
	char *s, *t;
	char* p;
	int pid;
	int num = 0, ch_num;
	char desc[32];
	//p = strchr( pPidsString, 'V' );
	p = pPidsString;
	if ( p == NULL ) 
		return 0;

	//parse a video pid -Vnnn[desc]
	if ( (s=strstr( p, "V" )) != NULL )
	{
		ch_num = GetDigitsNum( s+1, &pid );
		if ( ch_num > 0 )
		{
			s += ch_num + 1;
			pPidsTable->pid_tbl[num].pid = (unsigned short)pid;
			pPidsTable->pid_tbl[num].type = 2;
			pPidsTable->pid_tbl[num].index = num;
			memset( desc, 0, sizeof(desc) );
			ch_num = GetDescString( s, desc, sizeof(desc) );
			if ( ch_num > 0 )
				memcpy( pPidsTable->pid_tbl[num].desc, desc, sizeof(pPidsTable->pid_tbl[num].desc) );
			num++;
		}
	}

	//parse audio pids -Annn[desc]
	t = p;
	while ( num < MAX_USER_PID_NUM )
	{
		if ( (s=strstr( t, "A" ) )!= NULL )
		{
			ch_num = GetDigitsNum( s+1, &pid );
			if ( ch_num > 0 )
			{
				s += ch_num + 1;
				pPidsTable->pid_tbl[num].pid = pid;
				pPidsTable->pid_tbl[num].type = 3;
				pPidsTable->pid_tbl[num].index = num;
				ch_num = GetDescString( s, desc, sizeof(desc) );
				s += ch_num; 
				if ( ch_num > 0 )
					memcpy( pPidsTable->pid_tbl[num].desc, desc, sizeof(pPidsTable->pid_tbl[num].desc) );
				num++;
			}

			t = s+1;
		} else
			break;
	}

	//parse subtitle pids
	t = p;
	while ( num < MAX_USER_PID_NUM )
	{
		if ( (s=strstr( t, "S" ) )!= NULL )
		{
			ch_num = GetDigitsNum( s+1, &pid );
			if ( ch_num > 0 )
			{
				s += ch_num+1;
				pPidsTable->pid_tbl[num].pid = pid;
				pPidsTable->pid_tbl[num].type = 6;
				pPidsTable->pid_tbl[num].index = num;
				ch_num = GetDescString( s, desc, sizeof(desc) );
				s += ch_num ;
				if ( ch_num > 0 )
					memcpy( pPidsTable->pid_tbl[num].desc, desc, sizeof(pPidsTable->pid_tbl[num].desc) );
				else
					memcpy( pPidsTable->pid_tbl[num].desc, "SUB", 4 );
				num++;
			}

			t = s;
		} else
			break;
	}

	//parse pcr pid
	t = p;
	if ( (s=strstr( t, "P" ) )!= NULL )
	{
		ch_num = GetDigitsNum( s+1, &pid );
		if ( ch_num > 0 )
			pPidsTable->pcr_pid = pid;
	}

	pPidsTable->pid_num = num;

   	
	return num;
}

static int ParseChannelPids( TUNE *pTune, char *pPidsString )
{
	int num = ParsePidsString( &pTune->pids_table, pPidsString );
	if ( num > 0 )
	{
		pTune->tune_string_type = USER_SET_PID;
		pTune->stream_format = FREE_STREAM;
	}
	return num;
}

static int ParseChannelString( TUNE *pTune, char *pTuneString )
{
	const char *major_p, *minor_p, *index_p;
	int physical_ch = 0, major_ch = -1, minor_ch  = -1, index_ch = -1;
	int tune_string_type = 0;  //0: "x" index type; 1: "xx-yy" channel type; 2: "xx-yy-cc" full string; 
	
	if (  pTuneString == NULL ) 
		return 0;
				
	while ( *pTuneString == ' ' || *pTuneString == '\t' ) pTuneString++;
	if ( *pTuneString == '-' && *(pTuneString+1) == 't' ) pTuneString+=2;

	physical_ch = atoi( pTuneString );
	if ( physical_ch < 0 )
		return 0;


	major_p = strpbrk( pTuneString, "-.*" );
	if ( major_p != NULL )
	{
		major_p++;
		major_ch = atoi( major_p );
		minor_p = strpbrk( major_p, "-.*" );
		if ( minor_p != NULL )
		{
			minor_p++;
			minor_ch = atoi( minor_p ); 
			index_p = strpbrk( minor_p, "-.*" );
			if ( index_p != NULL )
			{
				index_p++;
				index_ch = atoi( index_p ); 
				tune_string_type = 3;		//format: onid-tsid-sid-index   "48-50-1-12" (DVB-C/DVB-T)
			} else
			{
				tune_string_type = 3;		//format: physical-major_ch-minor_ch vs. onid-tsid-sid "48-50-1" (ATSC/DVB-T[tvtv])

				if ( minor_ch == 0 && major_ch == 0 )
					tune_string_type = 1;   //format: index-0-0            "12-0-0" (ATSC/DVB-T)
			}

		} else
		{
			minor_ch = major_ch;
			major_ch = physical_ch;  //format: major-minor   "50-1"
			physical_ch = -1;
			tune_string_type = 2;
		}
	}

	if ( tune_string_type == 0 )
	{
		pTune->channel = physical_ch;
		pTune->tune_string_type = 0;
	} else
	{
		pTune->u.unkn.data1 = physical_ch;
		pTune->u.unkn.data2 = major_ch;
		pTune->u.unkn.data3 = minor_ch;
		pTune->tune_string_type = tune_string_type;
	}

	return 1;
}


static int CheckNativeCoreScriptFile( char* pFileName )
{
	int ret = 0, bytes;
	char buf[1024*32]={0};
	FILE* fp = fopen( pFileName, "rb" );
	if ( fp == NULL )
		return 0;

	bytes = (int)fread( buf, 1, sizeof(buf)-1, fp );
	if ( strstr( buf, "NATIVECORE" ) || strstr( buf, "TEST_SCRIPT" ) )
	{
		ret = TEST_SCRIPT;
	} else
	{
		ret = CheckFormat( (unsigned char*)buf, bytes );
	}
	fclose( fp );
	return ret;
}
typedef struct
{
	unsigned short task;
	char input_file[256];
	char output_file[256];
	unsigned short input_format;
	unsigned short output_format;
	char channel_desc[128];
	unsigned short script_line;
} TASK;

int ReadNextLineScript( TASK *pTask, FILE* fd )
{
	int i=0, bytes = 0;
	char buf[2048]={0}, *p;
	p = buf;

	while ( !feof( fd) )
	{
		pTask->script_line++;
		bytes = 0;
		memset( buf, 0x0, sizeof(buf) );
		//read a line
		while ( !feof( fd ) && bytes<sizeof(buf)-1 )
		{
			if ( fread( buf+bytes, 1, 1, fd ) == 0 ) break;
			if ( buf[bytes] == '\n' ) break;
			bytes++;
		}
		if ( feof( fd ) && bytes == 0 ) 	return 0;
		buf[bytes] = 0x0;
		//skip white space
		while ( i < bytes && buf[i] && ( buf[i]==' ' || buf[i]=='\t' || buf[i] =='\a' ) ) i++;

		//skip a blank or comment line
		if ( buf[i] == 0x0 || buf[i] == '#' )  continue; 

		if ( ( p = strstr( buf+i, "task" ) )!= NULL )
		{
			pTask->task = ParseTaskArg( p+5 );
		} else
		if ( ( p = strstr( buf+i, "output_format" ) )!= NULL )
		{
			pTask->output_format = ParseFormatArg( p+14 );
		} else
		if ( ( p = strstr( buf+i, "output_file" ) )!= NULL )
		{
			ParseFileName( ( p+12 ), pTask->output_file, sizeof(pTask->output_file) );
		} else
		if ( ( p = strstr( buf+i, "input_file" ) )!= NULL )
		{
			int len = ParseFileName( p+11, pTask->input_file, sizeof(pTask->input_file) );
			p += 11+len;
			while ( p < buf+sizeof(buf)-1 && *p && (*p==' '||*p== '\t' )) p++;
			if ( *p ) 
				strncpy( pTask->channel_desc, p, sizeof( pTask->channel_desc ));
			else 
				pTask->channel_desc[0] =0x0;
			if ( len )
				return 1;
		} else
		if (  strstr( buf+i, "=" ) == NULL ) 
		{
			int len;
			p = buf+i;
			len = ParseFileName( p, pTask->input_file, sizeof(pTask->input_file) );
			p += len;
			while ( p < buf+sizeof(buf)-1 && *p && (*p==' '||*p== '\t' ) ) p++;
			if ( *p ) 
				strncpy( pTask->channel_desc, p, sizeof( pTask->channel_desc ));
			else 
				pTask->channel_desc[0] =0x0;
			if ( len )
				return 1;
		}


	}
	return 0;
}

////////////////////////////  memory alloc ///////////////////////////////////////////
/*
static void* _test_alloc_mem( void* pContext, void* pMemory, int nSize, int nCmd )
{
	unsigned char* mem_start = NULL;
	if ( nCmd == 1 && nSize > 0 )
	{
		mem_start = SAGETV_MALLOC( nSize );
		return mem_start;
	}
	if ( nCmd == 0 && pMemory != NULL )
	{
		SAGETV_FREE( pMemory );
		return NULL;
	}
	return NULL;
}
*/
//////////////////////////////////////////////////////////////////////////////////////

int EPG_Dump( void* pContext, void* pData, int Size )
{
	char* pEPG=(char*)pData;
	int  nBytes = Size;
	FILE *fp = fopen( "EPG_dump.log", "a" );
	if ( fp != NULL )
	{
		fwrite( pData, 1, Size, fp );
		fwrite( "\r\n", 1, 2, fp );
		fclose( fp );
	}
	return 1; 
}

////////////////////////////  push out ///////////////////////////////////////////
typedef struct PUSH_FILE_DUMPER
{
	FILE* fp;
	ULONGLONG bytes;

} PUSH_FILE_DUMPER;

int PushFileDumper( void* pContext, void* pData, int nSize )
{
	PUSH_FILE_DUMPER *file_dumper = (PUSH_FILE_DUMPER*)pContext;
	OUTPUT_DATA   *pOutputData = (OUTPUT_DATA*)pData;
	unsigned char* data_ptr = pOutputData->data_ptr;
	unsigned short bytes = pOutputData->bytes;

	if ( file_dumper->fp == NULL ) 
		return 0;

	if ( pOutputData->fourcc == SAGE_FOURCC( "SUB " ) )
	{
		printf( "subtittle" );
	}

	file_dumper->bytes += fwrite( data_ptr, 1, bytes, file_dumper->fp );

	//pDataBuffer->data_bytes = 0;
	return bytes;
}

//////////////////////////////////////////////////////////////////////////////////////

////////////////////////////  Fast filter dump ///////////////////////////////////////////
typedef struct TS_FILTER_FILE_DUMPER
{
	FILE* stream_fp;
	FILE* index_fp;
	ULONGLONG stream_bytes;
	unsigned long index_bytes;
	void*	 ts_filter_dump;
	int		data_buffer_bytes;
	int		index_buffer_bytes;
	unsigned char data_buffer[2048*2];
	unsigned char index_buffer[2048];
} TS_FILTER_FILE_DUMPER;

int TSFilterFileDumper( void* pContext, void* pData, int nSize )
{
	TS_FILTER_FILE_DUMPER *file_dumper = (TS_FILTER_FILE_DUMPER*)pContext;
	OUTPUT_DATA   *pOutputData = (OUTPUT_DATA*)pData;
	unsigned char* data_ptr = pOutputData->data_ptr;
	unsigned short bytes = pOutputData->bytes;

	if ( file_dumper->stream_fp == NULL ) 
		return 0;

	if ( file_dumper->data_buffer_bytes + 188 < sizeof( file_dumper->data_buffer ) )
	{
		memcpy( file_dumper->data_buffer+file_dumper->data_buffer_bytes, data_ptr, 188 );
		file_dumper->data_buffer_bytes += 188;
	} else
	{
		file_dumper->stream_bytes += fwrite( file_dumper->data_buffer, 1, 
			                             file_dumper->data_buffer_bytes, file_dumper->stream_fp );
		file_dumper->data_buffer_bytes = 0;
		memcpy( file_dumper->data_buffer+file_dumper->data_buffer_bytes, data_ptr, 188 );
		file_dumper->data_buffer_bytes += 188;
	}

	//pDataBuffer->data_bytes = 0;
	return bytes;
}

int TSFilterIndexDumper( void* pContext, void* pData, int nSize )
{
	TS_FILTER_FILE_DUMPER *file_dumper = (TS_FILTER_FILE_DUMPER*)pContext;
	OUTPUT_DATA   *pOutputData = (OUTPUT_DATA*)pData;
	unsigned char* data_ptr = pOutputData->data_ptr;
	unsigned short bytes = pOutputData->bytes;

	if ( file_dumper->index_fp == NULL ) 
		return 0;

	if ( file_dumper->index_buffer_bytes + 188 < sizeof( file_dumper->data_buffer ) )
	{
		memcpy( file_dumper->index_buffer + file_dumper->index_buffer_bytes, data_ptr, 188 );
		file_dumper->index_buffer_bytes += 188;
	} else
	{
		file_dumper->stream_bytes += fwrite( file_dumper->index_buffer, 1, 
			                             file_dumper->index_buffer_bytes, file_dumper->stream_fp );
		file_dumper->index_buffer_bytes = 0;
		memcpy( file_dumper->index_buffer + file_dumper->index_buffer_bytes, data_ptr, 188 );
		file_dumper->index_buffer_bytes += 188;
	}
	//file_dumper->index_bytes += (unsigned long)fwrite( data_ptr, 1, bytes, file_dumper->index_fp );

	//pDataBuffer->data_bytes = 0;
	return bytes;
}

int TSProgramDumper( void* pContext, void* pData, int nSize )
{
	TS_FILTER_FILE_DUMPER *file_dumper = (TS_FILTER_FILE_DUMPER*)pContext;
	if ( file_dumper == NULL || file_dumper->ts_filter_dump == NULL )
		return 0;
	file_dumper->data_buffer_bytes = 0;
	file_dumper->index_buffer_bytes = 0;

	return 1;
}

//////////////////////////////////////////////////////////////////////////////////////

static int DoBatchTest( char* pScriptFile );
#define _DATA_BUFFER_SIZE_    (1024*8)
static unsigned char data_buffer[ _DATA_BUFFER_SIZE_ ];
static unsigned char align_buf[_DATA_BUFFER_SIZE_*2];
int main( int argc, char* argv[] )
{
	TUNE tune={0};
	void* remuxer;
	PUSH_FILE_DUMPER foo_file_dumper={0};
	TS_FILTER_FILE_DUMPER foo_ts_filter_dumper={0};
	void *pTSPacketDump=NULL;

	char *output_file = NULL, *input_file = NULL, *script_file=NULL;
	int  task_cmd=REMUX_FILE, script_test = 0, input_format=0, output_format= MPEG_PS;
	int  i, set_channel=0, stream_type=0;
	FILE  *fd;

	_enable_native_log( );

	SageLog(( _LOG_TRACE, 3, TEXT(">>>> SageTV native core. ver. 1.0.0 Push Test. Qian<<<<") ));
	SageLog(( _LOG_TRACE, 3, TEXT("    %s    "), CodeConfig() ));

	if ( argc >= 2 )
	{
		input_format = CheckNativeCoreScriptFile( argv[1] );
		if ( input_format == TEST_SCRIPT )
		{
			script_test = 1;
			input_format = 0;
			script_file = argv[1];
		} else
		{
			input_file = argv[1];
		}

	} else
	{
		input_format = CheckNativeCoreScriptFile( "NATIVE_CORE.RUN" );
		if ( input_format == 0 )
		{
			puts( "Usage: NativeCore filename" );
			return 1;
		} else
		{
			if ( input_format == TEST_SCRIPT )
			{
				script_test = 1;
				input_format = 0;
				script_file = "NATIVE_CORE.RUN";
			}
		}
	}

	for ( i = 2; i<argc; i++ )
	{
		if ( *argv[i] == '-' || *argv[i] == '/' )
			switch ( *(argv[i]+1) )	{
			case 'o':
			case 'O': output_file =argv[i]+2;
				break;
			case 't':   //-t128-23980
			case 'T': set_channel = ParseChannelString( &tune, (argv[i]+2) );
				break;
			case 'f':   //-fDVB
			case 'F': stream_type = strstr( (argv[i]+2), "ATSC")?ATSC_STREAM : strstr( (argv[i]+2), "DVB")?DVB_STREAM : strstr( (argv[i]+2), "REMUX_FILE")?FREE_STREAM : 0;
				break;
			case 'r':   //-r"V128 A123 A568 S780"
					  set_channel = ParseChannelPids( &tune, (argv[i]+2) );
				break;
			case 'c':
			case 'C':
				task_cmd = ParseTaskArg( (argv[i]+2) );
				break;
			case 'k':
			case 'K':
				task_cmd = ParseFormatArg( (argv[i]+2) );

			}
	}


	if ( script_test )
	{
		DoBatchTest( script_file );
		return 0;
	}

	if ( set_channel == 0 )
	{
		tune.channel = 1; //select default channel 1
		stream_type = FREE_STREAM;
	}

	if ( input_format == 0 )
		input_format = CheckNativeCoreScriptFile( input_file );
	if ( input_format == 0 )
		input_format = MPEG_TS;

	if ( task_cmd == REMUX_FILE ) task_cmd = REMUX_STREAM;
   
   
	remuxer = OpenRemuxStream( task_cmd , &tune, 
		                       input_format, output_format, 
							   NULL /*_test_alloc_mem*/, NULL,
							   PushFileDumper, &foo_file_dumper ); 

	if ( stream_type )
	{
		ConsolidateTuneParam( &tune, stream_type, 0 );
	}

	if ( task_cmd == REMUX_FILE && output_file == NULL )
		output_file = "a.mpg";

	SetupRemuxTSStreamFormat(  remuxer, ATSC_STREAM, 0 );
	SetupEPGDump( remuxer, EPG_Dump, remuxer );
	SetupProgramDataDump( remuxer, TSProgramDumper, &foo_ts_filter_dumper );


	//prepare output file
	if ( foo_file_dumper.fp != NULL )
		fclose( foo_file_dumper.fp );
	foo_file_dumper.fp = fopen( output_file, "wb" );
	if ( foo_file_dumper.fp == NULL )
		SageLog(( _LOG_TRACE, 3, TEXT(" dumpfile file can't be open %s"), output_file ));


	if ( ( fd = fopen( input_file, "rb" ) ) != NULL )
	{
		int bytes, used_bytes, remain_bytes=0, expected_bytes = 0;
		unsigned char *p;
		SageLog(( _LOG_TRACE, 3, TEXT(" Test file %s"), input_file ));
		while ( !feof( fd ) )
		{
			bytes = (int)fread( data_buffer, 1, sizeof(data_buffer), fd );
			p = data_buffer;
			if ( expected_bytes )
			{
				int expected_bytes2;
				memcpy( align_buf+remain_bytes, data_buffer, expected_bytes );
				used_bytes = PushRemuxStreamData( remuxer, align_buf, remain_bytes+expected_bytes, &expected_bytes2 );
				if ( expected_bytes2 == 0 )
				{
					p     +=  expected_bytes;
					bytes -=  expected_bytes;
				}
			}

			used_bytes = PushRemuxStreamData( remuxer, p, bytes, &expected_bytes );
			remain_bytes = bytes - used_bytes;
			ASSERT( expected_bytes+remain_bytes <= sizeof(align_buf) );
			if ( remain_bytes > 0 && remain_bytes + expected_bytes <= sizeof(align_buf))
			{
				memcpy( align_buf, p+used_bytes, remain_bytes );
				if ( expected_bytes == 0 )
					expected_bytes = sizeof(data_buffer);
			} 
		}
		fclose( fd );
		FlushRemuxStream( remuxer );
		ResetRemuxStream( remuxer );
	} 
	else
	{
		SageLog(( _LOG_TRACE, 3, TEXT(" file can't be open, or unknown video file %s"), input_file ));
	}

	if ( foo_file_dumper.fp == NULL )
		fclose( foo_file_dumper.fp );

	CloseRemuxStream( remuxer );
	MEMORY_REPORT();
	{
		clock_t running_time = clock();
		printf( "running %d\r\n", (int)running_time );
		SageLog(( _LOG_TRACE, 3, TEXT("running %d\r\n"), (int)running_time ));

	}
	return 0;
	
}


static int DoBatchTest( char* pScriptFile )
{
	TUNE tune={0};
	void* remuxer;
	PUSH_FILE_DUMPER foo_file_dumper={0};
	TS_FILTER_FILE_DUMPER foo_ts_filter_dumper={0};
	void* pTSPacketDump = NULL;
	char *output_file = NULL, *input_file = NULL, *script_file=NULL;
	int  task_cmd=REMUX_FILE,  input_format=0, output_format= MPEG_PS;
	int  set_channel=0;
	FILE *script_fd=NULL, *fd;
	TASK task={0};
	char filename_out[256];

	int  count = 0;
	script_fd = fopen( pScriptFile, "rt" );		
	if ( script_fd == NULL )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Can't open nativecore script file %s."), script_file ));
		return 0;
	}

	if ( ReadNextLineScript( &task, script_fd ) > 0 )
	{
		task_cmd = task.task;
		input_file = task.input_file;
		input_format = task.input_format;
		output_file = task.output_file;
		output_format = task.output_format;
		if ( task.channel_desc[0] )
			set_channel = ParseChannelString( &tune, task.channel_desc );

		if ( task.output_file[0] )
		{
			snprintf( filename_out, sizeof(filename_out), task.output_file, count );
			output_file = filename_out;
		}
		
	}
	///////////////////////////////////////////////////////////////////////////

	if ( set_channel == 0 )
	{
		tune.channel = 1; //select default channel 1
	}

	if ( input_format == 0 )
		input_format = CheckNativeCoreScriptFile( input_file );
	if ( input_format == 0 )
		input_format = MPEG_TS;

	if ( task_cmd == REMUX_FILE ) task_cmd = REMUX_STREAM;
   
	remuxer = OpenRemuxStream( task_cmd , &tune, 
		                       input_format, output_format, 
							   NULL /*_test_alloc_mem */, NULL,
							   PushFileDumper, &foo_file_dumper ); 

	//SetupRemuxTSStreamFormat(  remuxer, ATSC_STREAM, 0 );
	SetupRemuxStreamTune( remuxer, &tune );
	EnableTSPSI( remuxer );
	SetupEPGDump( remuxer, EPG_Dump, remuxer );
	DisablePSBuildPading( remuxer );
	SetupProgramDataDump( remuxer, TSProgramDumper, &foo_ts_filter_dumper );

	while( 1 )
	{
		int format;

		SageLog(( _LOG_TRACE, 3, TEXT("-------------> Task %d (%d, line:%d) <-------------\n"), task_cmd, count, task.script_line ));

		ResetRemuxStream( remuxer );

		//prepare output file
		if ( foo_file_dumper.fp != NULL )
			fclose( foo_file_dumper.fp );
		foo_file_dumper.fp = fopen( output_file, "wb" );
		if ( foo_file_dumper.fp == NULL )
			SageLog(( _LOG_TRACE, 3, TEXT(" dumpfile file can't be open %s"), output_file ));



		//prepare input file
		format = CheckNativeCoreScriptFile( input_file );
		if ( format != input_format )
		{
			SageLog(( _LOG_TRACE, 3, TEXT(" Format %d isn't not expected, skip file %s"), format, input_file ));
		}
		else
		if ( ( fd = fopen( input_file, "rb" ) ) != NULL )
		{
			int bytes, used_bytes, remain_bytes, expected_bytes = 0;
			unsigned char *p;
			SageLog(( _LOG_TRACE, 3, TEXT(" Test file %s"), input_file ));
			while ( !feof( fd ) )
			{
				bytes = (int)fread( data_buffer, 1, sizeof(data_buffer), fd );
				if ( bytes == 0 ) break;
				p = data_buffer;
				if ( expected_bytes )
				{
					int expected_bytes2;
					memcpy( align_buf+remain_bytes, data_buffer, expected_bytes );
					used_bytes = PushRemuxStreamData( remuxer, align_buf, remain_bytes+expected_bytes, &expected_bytes2 );
					if ( expected_bytes2 == 0 )
					{
						p     +=  expected_bytes;
						bytes -=  expected_bytes;
					}
				}

				used_bytes = PushRemuxStreamData( remuxer, p, bytes, &expected_bytes );
				remain_bytes = bytes - used_bytes;
				ASSERT( expected_bytes+remain_bytes <= sizeof(align_buf) );
				if ( remain_bytes > 0 && remain_bytes + expected_bytes <= sizeof(align_buf))
				{
					memcpy( align_buf, p+used_bytes, remain_bytes );
					if ( expected_bytes == 0 )
						expected_bytes = sizeof(data_buffer);

				} 
			}
			fclose( fd );
			FlushRemuxStream( remuxer );
		} 
		else
		{
			SageLog(( _LOG_TRACE, 3, TEXT(" file can't be open, or unknown video file %s"), input_file ));
		}

		count++;
		//read next command to process
		if ( ReadNextLineScript( &task, script_fd ) == 0 )
			break;
		task_cmd = task.task;
		input_file = task.input_file;
		if ( task.input_format ) input_format = task.input_format;
		output_file = task.output_file;
		output_format = task.output_format;
		if ( task.channel_desc[0] )
			set_channel = ParseChannelString( &tune, task.channel_desc );
		else
			set_channel = 0;

		if ( set_channel == 0 )
			tune.channel = 1; //select default channel 1

		if ( task.output_file[0] )
		{
			snprintf( filename_out, sizeof(filename_out), task.output_file, count );
			output_file = filename_out;
		}
		
	}

	if ( script_fd != NULL )
		fclose( script_fd );

	if ( foo_file_dumper.fp == NULL )
		fclose( foo_file_dumper.fp );

	CloseRemuxStream( remuxer );
	MEMORY_REPORT();
	{
		clock_t running_time = clock();
		printf( "running %d\r\n", (int)running_time );
		SageLog(( _LOG_TRACE, 3, TEXT("running %d\r\n"), (int)running_time ));

	}
	return 0;
}



//////////////////////////////// TESTING SECTION ///////////////////////////////////
/*
static unsigned char* _search_data_( unsigned char* match, int len, unsigned char* data, int data_size )
{
	int i;
	for ( i = 0; i<data_size-len; i++ )
	
		if ( !memcmp( match, data+i, len ) )
			return data+i;
	
	return NULL;
}
*/


