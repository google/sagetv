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
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>


///////////////////////////////////////////////////////////////////////////////////////////////////////////
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
	} else
	{
		if ( strpbrk( pTuneString, "dvb" ) || strpbrk( pTuneString, "DVB" ) )
		{
			pTune->stream_format = DVB_STREAM;
			pTune->tune_string_type = tune_string_type;
			pTune->u.dvb.onid = physical_ch;
			pTune->u.dvb.tsid = major_ch;
			pTune->u.dvb.sid  = minor_ch;

		} else
		if ( strpbrk( pTuneString, "atsc" ) || strpbrk( pTuneString, "ATSC" ) ||
			 strpbrk( pTuneString, "qam" ) || strpbrk( pTuneString, "QAM" ) )
		{
			pTune->stream_format = ATSC_STREAM;
			pTune->u.atsc.physical_ch = physical_ch;
			pTune->u.atsc.major_num = major_ch;
			pTune->u.atsc.minor_num = minor_ch;
		} else
		{
			pTune->u.unkn.data1 = physical_ch;
			pTune->u.unkn.data2 = major_ch;
			pTune->u.unkn.data3 = minor_ch;
			pTune->tune_string_type = tune_string_type;
		}
	}
	return 1;
}


static int CheckNativeCoreScriptFile( char* pFileName )
{
	int ret = 0, bytes;
	char buf[1024*4]={0};
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


static int DoBatchTest( char* pScriptFile );
int main( int argc, char* argv[] )
{
	TUNE tune={0};
	char *output_file = NULL, *input_file = NULL, *script_file=NULL;
	int  task_cmd=REMUX_FILE, script_test = 0, input_format=0, output_format= MPEG_PS;
	int i, set_channel=0, stream_type=0;
	int option = 0;
	TASK task={0};

	_enable_native_log( );

	SageLog(( _LOG_TRACE, 3, TEXT(">>>> SageTV native core. ver. 1.0.0 FileRemuxer. Qian <<<<") ));
	SageLog(( _LOG_TRACE, 3, TEXT("     %s     "), CodeConfig() ));
	
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
			case 'T': set_channel = ParseChannelString( &tune, argv[i] );
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
		DoBatchTest( script_file  );
		return 0;
	}
   
	if ( set_channel == 0 )
	{
		tune.channel = 1; //select default channel 1
		stream_type = FREE_STREAM;
	}

	if ( stream_type )
	{
		ConsolidateTuneParam( &tune, stream_type, 0 );
	}

	if ( task_cmd == REMUX_FILE && output_file == NULL )
		output_file = "a.mpg";

	task.task = task_cmd;
	RemuxFile( task.task, input_file, &tune, input_format, output_file, output_format, option );
	MEMORY_REPORT();
	return 0;
}

static int DoBatchTest( char* pScriptFile )
{
	TUNE tune={0};
	char *output_file = NULL, *input_file = NULL;
	int  task_cmd=REMUX_FILE, input_format=0, output_format= MPEG_PS;
	int  set_channel=0, stream_type=0;
	FILE* script_fd=NULL;
	TASK task={0};
	int option = 0;
	char filename_out[256];
	int  count = 0;


	script_fd = fopen( pScriptFile, "rt" );	

	while( script_fd != NULL )
	{
		if ( ReadNextLineScript( &task, script_fd ) == 0 )
			break;
		memset( &tune, 0x0, sizeof(tune) );
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

		stream_type = 0;
		if ( set_channel == 0 )
		{
			tune.channel = 1; //select default channel 1
			stream_type = FREE_STREAM;
		} 
		
		if ( stream_type )
		{
			ConsolidateTuneParam( &tune, stream_type, 0 );
		}

		if ( task_cmd == REMUX_FILE && output_file == NULL )
			output_file = "a.mpg";

		SageLog(( _LOG_TRACE, 3, TEXT("-------------> Task %d (%d, line:%d) <-------------\n"), task_cmd, count, task.script_line ));

		RemuxFile( task.task, input_file, &tune, input_format, output_file, output_format, option );

		count++;
	}

	if ( script_fd != NULL )
		fclose( script_fd );
	MEMORY_REPORT();
	return 0;
}
