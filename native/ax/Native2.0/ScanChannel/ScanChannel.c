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
#include "TSParser.h"
#include "ChannelScan.h"
#include "ScanFilter.h"
#include <stdlib.h>
#include <stdio.h>
#ifdef WIN32
#include <io.h>
#include <share.h>
#endif
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <stdarg.h>
#include <string.h>
#include <memory.h>	


///////////////////////////////////////////////////////////////////////////////////////////////////////////
//test scan channel parser
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

	pTune->tune_string_type = 0;

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

////////////////////////////  memory alloc ///////////////////////////////////////////
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
//////////////////////////////////////////////////////////////////////////////////////
static int DoBatchTest( char* pScriptFile );
static int DoBatchScanFilterTest( char* pScriptFile );
#define _DATA_BUFFER_SIZE_    (1024*8)
static unsigned char data_buffer[ _DATA_BUFFER_SIZE_ ];

static int GetScanChannelNum( SCAN *pScan )
{
	return GetChannelList( pScan )->channel_num;
}

static void OutputScanPSIResult( SCAN *pScan, char* pOutputFile, char *pComment )
{
	FILE* fp;
	char *channel_inf, *tune_inf, *nit_inf;
	int channel_inf_size, tune_inf_size, nit_inf_size;
	int bytes1 = 0, bytes2 = 0, bytes3 = 0;
	fp = fopen( pOutputFile, "a+t" );

	channel_inf_size = GuessChannelInfSize( GetChannelList( pScan ) )+20;
	tune_inf_size = GuessTuneInfSize( GetChannelList( pScan ) )+20;
	nit_inf_size = GuessNitInfSize( GetTuneList( pScan ) )+20;
	channel_inf = SAGETV_MALLOC(channel_inf_size);
	tune_inf = SAGETV_MALLOC(tune_inf_size);
	nit_inf = SAGETV_MALLOC(nit_inf_size);

	bytes1 += snprintf( channel_inf+bytes1, channel_inf_size-bytes1, "Channel:%d\n", GetChannelList( pScan )->channel_num );
	bytes2 += snprintf( tune_inf+bytes2, tune_inf_size-bytes2, "Tune:%d\n", GetChannelList( pScan )->channel_num );
	bytes3 += snprintf( nit_inf+bytes3, nit_inf_size-bytes3, "Nit:%d\n", GetTuneList( pScan )->tune_num );
	bytes1 += MakeChannelInf( GetChannelList( pScan ), channel_inf+bytes1, channel_inf_size-bytes1 );
	bytes2 += MakeTuneInf( GetChannelList( pScan ), tune_inf+bytes2, tune_inf_size-bytes2 );
	bytes3 += MakeNitInf( GetTuneList( pScan ), nit_inf+bytes3, nit_inf_size-bytes3 );
	
	//SageLog(( _LOG_TRACE, 3, "***%s", channel_inf ));
	//SageLog(( _LOG_TRACE, 3, "***%s", tune_inf ));
	//SageLog(( _LOG_TRACE, 3, "***%s", nit_inf ));

	bytes1 += snprintf( channel_inf+bytes1, channel_inf_size-bytes1, "\r\n" );
	bytes2 += snprintf( tune_inf+bytes2, tune_inf_size-bytes2, "\r\n" );
	bytes3 += snprintf( nit_inf+bytes3, nit_inf_size-bytes3, "\r\n" );
	if ( fp != NULL )
	{
		if ( pComment )
			fprintf( fp, "%s\n", pComment );
		fwrite( channel_inf, 1, bytes1, fp );
		fwrite( tune_inf, 1, bytes2, fp );
		fwrite( nit_inf, 1, bytes3, fp );
		fclose( fp );
	}

	SAGETV_FREE( channel_inf );
	SAGETV_FREE( tune_inf );
	SAGETV_FREE( nit_inf );

}

static void OutputScanProgramResult( SCAN *pScan, char* pOutputFile, char *pComment )
{
	FILE* fp;
	char *program_inf;
	int  program_inf_size;
	int bytes1 = 0;
	fp = fopen( pOutputFile, "a+t" );

	program_inf_size = GuessProgramInfSize( GetProgramList( pScan ) )+20;
	program_inf = SAGETV_MALLOC(program_inf_size);

	bytes1 += snprintf( program_inf+bytes1, program_inf_size-bytes1, "program:%d\n", GetProgramList( pScan )->program_num );
	bytes1 += MakeProgramInf( GetProgramList( pScan ), pScan, program_inf+bytes1, program_inf_size-bytes1 );
	
	//SageLog(( _LOG_TRACE, 3, "***%s", program_inf ));

	bytes1 += snprintf( program_inf+bytes1, program_inf_size-bytes1, "\r\n" );
	if ( fp != NULL )
	{
		if ( pComment )
			fprintf( fp, "%s\n", pComment );
		fwrite( program_inf, 1, bytes1, fp );
		fclose( fp );
	}

	SAGETV_FREE( program_inf );

}

static void OutputScanList( CHANNEL_LIST *pChannelList, TUNE_LIST* pTuneList, char* pOutputFile, char *pComment )
{
	FILE* fp;
	char *channel_inf, *tune_inf, *nit_inf;
	int channel_inf_size, tune_inf_size, nit_inf_size;
	int bytes1 = 0, bytes2 = 0, bytes3 = 0;
	fp = fopen( pOutputFile, "a+t" );

	channel_inf_size = GuessChannelInfSize( pChannelList )+20;
	tune_inf_size = GuessTuneInfSize( pChannelList )+20;
	nit_inf_size = GuessNitInfSize( pTuneList )+20;
	channel_inf = SAGETV_MALLOC(channel_inf_size);
	tune_inf = SAGETV_MALLOC(tune_inf_size);
	nit_inf = SAGETV_MALLOC(nit_inf_size);

	bytes1 += snprintf( channel_inf+bytes1, channel_inf_size-bytes1, "Channel:%d\n", pChannelList->channel_num );
	bytes2 += snprintf( tune_inf+bytes2, tune_inf_size-bytes2, "Tune:%d\n", pChannelList->channel_num );
	bytes3 += snprintf( nit_inf+bytes3, nit_inf_size-bytes3, "Nit:%d\n", pTuneList->tune_num );
	bytes1 += MakeChannelInf( pChannelList, channel_inf+bytes1, channel_inf_size-bytes1 );
	bytes2 += MakeTuneInf( pChannelList, tune_inf+bytes2, tune_inf_size-bytes2 );
	bytes3 += MakeNitInf( pTuneList, nit_inf+bytes3, nit_inf_size-bytes3 );
	
	//SageLog(( _LOG_TRACE, 3, "***%s", channel_inf ));
	//SageLog(( _LOG_TRACE, 3, "***%s", tune_inf ));
	//SageLog(( _LOG_TRACE, 3, "***%s", nit_inf ));

	bytes1 += snprintf( channel_inf+bytes1, channel_inf_size-bytes1, "\r\n" );
	bytes2 += snprintf( tune_inf+bytes2, tune_inf_size-bytes2, "\r\n" );
	bytes3 += snprintf( nit_inf+bytes3, nit_inf_size-bytes3, "\r\n" );
	if ( fp != NULL )
	{
		if ( pComment )
			fprintf( fp, "%s\n", pComment );
		fwrite( channel_inf, 1, bytes1, fp );
		fwrite( tune_inf, 1, bytes2, fp );
		fwrite( nit_inf, 1, bytes3, fp );
		fclose( fp );
	}

	SAGETV_FREE( channel_inf );
	SAGETV_FREE( tune_inf );
	SAGETV_FREE( nit_inf );

}

//////////////////////////////////////////////////////////////////////////////////////
static int PushData( int fd, SCAN *scan )
{
	int bytes, used_bytes, remain_bytes, expected_bytes = 0;
	unsigned char *p;
	unsigned char align_buf[1024*3];

	while ( fd > 0 )
	{

		bytes = (int)read( fd, data_buffer, sizeof(data_buffer) );
		if ( bytes == 0 )
			break;
		p = data_buffer;
		if ( expected_bytes )
		{
			int expected_bytes2;
			memcpy( align_buf+remain_bytes, data_buffer, expected_bytes );
			used_bytes = PushScanStreamData( scan, align_buf, remain_bytes+expected_bytes, &expected_bytes2 );
			if ( expected_bytes2 == 0 )
			{
				p     +=  expected_bytes;
				bytes -=  expected_bytes;
			}
		}

		used_bytes = PushScanStreamData( scan, p, bytes, &expected_bytes );
		remain_bytes = bytes - used_bytes;
		ASSERT( expected_bytes+remain_bytes <= sizeof(align_buf) );
		if ( remain_bytes > 0 && remain_bytes + expected_bytes <= sizeof(align_buf))
		{
			memcpy( align_buf, p+used_bytes, remain_bytes );
		} 
		if ( IsChannelInfoReady( scan ) )
			break;

		if ( scan->task == NAKED_SCAN )
		{
			FSEEK( fd, 0, SEEK_SET );
			//if ( scan->naked_channel_found == 0 )
			//	break;
		}

	}

	return 0;
}

//////////////////////////////////////////////////////////////////////////////////////
int main( int argc, char* argv[] )
{
	TUNE tune={0};
	
	char *output_file = NULL, *input_file = NULL, *script_file=NULL;
	int  script_test = 0, input_format=0;
	SCAN *scan = NULL;
	int fd=0;

	trace_level = 3;
	SageLog(( _LOG_TRACE, 3, TEXT(">>>> SageTV native core. ver. 1.0.0 scan channel. Qian<<<<") ));
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


	if ( script_test )
	{
		//DoBatchTest( script_file );
		DoBatchScanFilterTest( script_file );
		return 0;
	}


	if ( input_format == 0 )
		input_format = CheckNativeCoreScriptFile( input_file );
	if ( input_format != MPEG_TS )
	{
		SageLog(( _LOG_TRACE, 3, TEXT(" It's not TS data"), input_file ));
		return 1;
	}
		


	scan = CreateChannelScan( NULL, &tune );

	if ( output_file == NULL )
		output_file = "a.mpg";

	//SetupRemuxTSStreamFormat(  remuxer, ATSC_STREAM, 0 );

	//prepare output file
	//if ( ( fd = fopen( input_file, "rb" ) ) != NULL )
#ifdef WIN32
	fd = FOPEN( input_file, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
#endif
#ifdef Linux
	fd = open( input_file, O_RDONLY );
#endif

	if ( fd > 0 )
	{
		unsigned short stream_format, sub_format;
		SageLog(( _LOG_TRACE, 3, TEXT(" Test file %s"), input_file ));

		//scan PSI channel
		ResetChannelScan( scan );
		DoChannelScan( scan, PSI_SCAN );
		ChannelScanTune( scan, &tune ); 
		PushData( fd, scan );

		////scan working channel if no PSI information avaliable for QAM
		//if ( GetStreamFormat( scan, &stream_format, &sub_format ) &&
		//	 ( stream_format == ATSC_STREAM && sub_format == CABLE || stream_format == 0 ) )
		{
			stream_format = ATSC_STREAM;;
			sub_format = CABLE;

			if ( !GetStreamFormat( scan, &stream_format, &sub_format ) )
			{
				stream_format = ATSC_STREAM;;
				sub_format = CABLE;
			}

			if ( stream_format == ATSC_STREAM && sub_format == CABLE )
			{
				scan->fd = fd;
				FSEEK( fd, 0, SEEK_SET );
				ResetChannelScan( scan );
				tune.stream_format = (unsigned char)stream_format;
				tune.sub_format    = (unsigned char)sub_format;
				DoChannelScan( scan, NAKED_SCAN );
				ChannelScanTune( scan, &tune ); 
				PushData( fd, scan );
				MergeChannelListProgramList( scan );
			}
		} 

		OutputScanPSIResult( scan, output_file ,input_file );
		FCLOSE( fd );

	} 
	else
	{
		SageLog(( _LOG_TRACE, 3, TEXT(" file can't be open, or unknown video file %s"), input_file ));
	}

	ReleaseChannelScan( scan );
	MEMORY_REPORT();
	return 0;
	
}


static int DoBatchTest( char* pScriptFile )
{
	TUNE tune={0};
	char *output_file = NULL, *input_file = NULL, *script_file=NULL;
	int  input_format=0;
	//int  stream_type=FREE_STREAM;
	FILE *script_fd=NULL;
	int  fd;
	SCAN *scan;
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
		input_file = task.input_file;
		input_format = task.input_format;
		output_file = task.output_file;

		if ( task.output_file[0] )
		{
			snprintf( filename_out, sizeof(filename_out), task.output_file, count );
			output_file = filename_out;
		}
		
	}
	///////////////////////////////////////////////////////////////////////////


	if ( input_format == 0 )
		input_format = CheckNativeCoreScriptFile( input_file );
	if ( input_format != MPEG_TS )
	{
		SageLog(( _LOG_TRACE, 3, TEXT(" It's not TS data"), input_file ));
		return 1;
	}

	scan = CreateChannelScan(  NULL , &tune );

	if ( output_file == NULL )
		output_file = "a.frq";

	

	while( 1 )
	{
		int file_format;
		unsigned short stream_format, sub_format;

		SageLog(( _LOG_TRACE, 3, TEXT("-------------> Task  scan channel (%d, line:%d) <-------------\n"),  count, task.script_line ));

		//prepare input file
		file_format = CheckNativeCoreScriptFile( input_file );
		if ( file_format != input_format )
		{
			if ( file_format == MPEG_TS )
				SageLog(( _LOG_TRACE, 3, TEXT(" Format %d isn't not expected, skip file %s"), file_format, input_file ));
		}
		else
		{

#ifdef WIN32
			fd = FOPEN( input_file, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
#endif
#ifdef Linux
			fd = open( input_file, O_RDONLY );
#endif
		}

		if ( fd > 0 )
		{
			//TUNE_LIST *tune_list=NULL;
			SageLog(( _LOG_TRACE, 3, TEXT(" Test file %s"), input_file ));

			//scan PSI channel
			ResetChannelScan( scan );
			DoChannelScan( scan, PSI_SCAN );
			ChannelScanTune( scan, &tune ); 
			PushData( fd, scan );
			OutputScanPSIResult( scan, output_file ,input_file );

			////scan working channel if no PSI information avaliable for QAM
			//if ( GetStreamFormat( scan, &stream_format, &sub_format ) &&
			//	 ( stream_format == ATSC_STREAM && sub_format == CABLE || stream_format == 0 ) )
			{
				stream_format = ATSC_STREAM;;
				sub_format = CABLE;

				if ( !GetStreamFormat( scan, &stream_format, &sub_format ) )
				{
					stream_format = ATSC_STREAM;
					sub_format = CABLE;
				}

				if ( stream_format == ATSC_STREAM && sub_format == CABLE )
				{
					scan->fd = fd;
					FSEEK( fd, 0, SEEK_SET );
					ResetChannelScan( scan );
					tune.stream_format = (unsigned char)stream_format;
					tune.sub_format    = (unsigned char)sub_format;
					DoChannelScan( scan, NAKED_SCAN );
					ChannelScanTune( scan, &tune ); 
					PushData( fd, scan );
					MergeChannelListProgramList( scan );
				}
			} 

			OutputScanPSIResult( scan, output_file ,input_file );
			FCLOSE( fd );
		} 
		else
		{
			SageLog(( _LOG_TRACE, 3, TEXT(" file can't be open, or unknown video file %s"), input_file ));
		}

		count++;
		//read next command to process
		if ( ReadNextLineScript( &task, script_fd ) == 0 )
			break;
		input_file = task.input_file;
		if ( task.input_format ) input_format = task.input_format;
		output_file = task.output_file;

		if ( task.output_file[0] )
		{
			snprintf( filename_out, sizeof(filename_out), task.output_file, count );
			output_file = filename_out;
		}
		
	}

	if ( script_fd != NULL )
		fclose( script_fd );

	ReleaseChannelScan( scan );
	MEMORY_REPORT();
	return 0;
}

static int DoBatchScanFilterTest( char* pScriptFile )
{
	TUNE tune={0};
	char *output_file = NULL, *input_file = NULL, *script_file=NULL;
	int  input_format=0;
	FILE *script_fd=NULL;
	int  fd;
	SCAN_FILTER *scan_filter;
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
		input_file = task.input_file;
		input_format = task.input_format;
		output_file = task.output_file;

		if ( task.output_file[0] )
		{
			snprintf( filename_out, sizeof(filename_out), task.output_file, count );
			output_file = filename_out;
		}
		
	}
	///////////////////////////////////////////////////////////////////////////

	if ( input_format == 0 )
		input_format = CheckNativeCoreScriptFile( input_file );
	if ( input_format != MPEG_TS )
	{
		SageLog(( _LOG_TRACE, 3, TEXT(" It's not TS data"), input_file ));
		return 1;
	}


	if ( output_file == NULL )
		output_file = "a.frq";

	scan_filter = CreateScanFilter();

	while( 1 )
	{
		int file_format;
		SageLog(( _LOG_TRACE, 3, TEXT("-------------> Task  scan channel (%d, line:%d) <-------------\n"),  count, task.script_line ));

		//prepare input file
		file_format = CheckNativeCoreScriptFile( input_file );
		if ( file_format != input_format )
		{
			if ( file_format == MPEG_TS )
				SageLog(( _LOG_TRACE, 3, TEXT(" Format %d isn't not expected, skip file %s"), file_format, input_file ));
		}
		else
		//if ( ( fd = fopen( input_file, "rb" ) ) != NULL )
#ifdef WIN32
		fd = FOPEN( input_file, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );
#endif
#ifdef Linux
		fd = open( input_file, O_RDONLY );
#endif

		if ( fd > 0 )
		{
			int state;
			SageLog(( _LOG_TRACE, 3, TEXT(" Test file %s"), input_file ));

			StartChannelScan( scan_filter, &tune );
			while ( 1 )
			{
				int bytes = (int)read( fd, data_buffer, sizeof(data_buffer) );
				if ( bytes == 0 )break;
				ProcessScan( scan_filter, data_buffer, bytes );
				state = ScanChannelState( scan_filter );
				if ( state > 0 )	break;
			}
			SageLog(( _LOG_TRACE, 3, TEXT(" scan filter finished with state %d"), state ));
			{
				CHANNEL_LIST *channel_list = GetScanChannelList( scan_filter );
				TUNE_LIST* tune_list = GetScanTuneList( scan_filter );
				OutputScanList( channel_list, tune_list, output_file, "" );
			}
			FCLOSE( fd );
			StopChannelScan( scan_filter );
		} 
		else
		{
			SageLog(( _LOG_TRACE, 3, TEXT(" file can't be open, or unknown video file %s"), input_file ));
		}

		count++;
		//read next command to process
		if ( ReadNextLineScript( &task, script_fd ) == 0 )
			break;
		input_file = task.input_file;
		if ( task.input_format ) input_format = task.input_format;
		output_file = task.output_file;

		if ( task.output_file[0] )
		{
			snprintf( filename_out, sizeof(filename_out), task.output_file, count );
			output_file = filename_out;
		}
		
	}

	if ( script_fd != NULL )
		fclose( script_fd );

	ReleaseScanFilter( scan_filter );

	MEMORY_REPORT();
	return 0;
}



