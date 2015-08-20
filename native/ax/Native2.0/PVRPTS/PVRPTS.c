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
#include "GetAVInf.h"
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include "PVRTSParser.h"
#include "TSInfoParser.h"


///////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////
#define TEST_SCRIPT    0x100
#define ENABLE_ATS_OUPUT 0x02

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

//////////////////////////////////////////////////////////////
int video_pid = 0;
unsigned short stream_type = 0;
//////////////////////////////////////////////////////////////
// Dump APIs
int ErrorInfDump( void* context, unsigned char* mesg, int bytes )
{
	printf( "ERROR:%s\n", mesg );
	return 1;
}

int AVInfDump( void* context, unsigned char* mesg, int bytes )
{
	printf( "AVINF:%s\n", mesg );
	return 1;
}

int AtsDump( void* context, unsigned char* data, int bytes )
{
	ATS_TIME *pATS = (ATS_TIME*)data;
	printf( "ATS:%ld   %ld\n", pATS->t.ats, pATS->packets );
	return 1;
}

int PidDump( void* context, unsigned char* data, int bytes )
{
	PID_ITEM *pPidInf = (PID_ITEM*)data;
	printf( "PID:0x%04x type:%d\n", pPidInf->pid, pPidInf->type );

	if ( video_pid == 0 && pPidInf->type == PID_TYPE_VIDEO )
	{
		video_pid = pPidInf->pid;
		stream_type = pPidInf->service;
	}	
	return 1;
}

int NewPidDump( void* context, unsigned char* data, int bytes )
{
	PVR_INFO_PARSER *pTSInfoParse = (PVR_INFO_PARSER *)context;
	PID_ITEM *pPidInf = (PID_ITEM*)data;
	
	printf( "PID:0x%04x NEW PID (pos: %ld packets)\n", pPidInf->pid, pTSInfoParse->ts_packet_counter );
	return 1;
}

int MetaDump( void* context, unsigned char* data, int bytes )
{
	PVR_INFO_PARSER *pPVRParser = (PVR_INFO_PARSER *)context;
	PVR_META_INF *meta = (PVR_META_INF*)data;
	static int state = 0;
	if ( state == meta->state )
		return 0;

	state = meta->state;
	if ( state == 1 )
	{
		TUNE tune={0};
	
		printf( "META-%d:program:%d tsid:%d pid:0x%04x\n", 
				meta->state, meta->program_num, meta->tsid, meta->program_pid );
		tune.tune_string_type = 15;
		tune.stream_format = ATSC_STREAM;
		tune.u.atsc.tsid = meta->tsid;
		tune.u.atsc.program_id = meta->program_num;
		SetupPVRTSTune( pPVRParser, &tune );

	}
	else
	if ( state == 2 )
		printf( "META-%d:video pid:0x%04x video type:0x%02x audio pid:0x%04x audio type:0x%02x\n", 
				meta->state, meta->video_pid, meta->video_type, meta->audio_pid, meta->audio_type );
	return 1;
}

int EPGDump( void* context, unsigned char* data, int bytes )
{
	char *epg = (char*)data;
	printf( "EPG:%s\n", epg );
	return 1;
}

//////////////////////////////////////////////////////////////
static int inline GetTSPacketPid( const unsigned char* pData )	
{
	int error  = pData[1]&0x80	? 1 : 0;	
	unsigned int pid =	pData[2] | (pData[1]&0x1f)<<8 ;	
	if ( error ) return -1;
	return pid;
}

static int inline IsGroupStart( const unsigned char* pData )	
{
	return ( pData[1]&0x40 ? 1:0);
}

static int GetPayloadOffset( const unsigned char* pData )	
{
	int adaption_ctr   = ( pData[3] & 0x30 )>> 4;	

	if ( ( adaption_ctr & 0x01 ) == 0  )	
	    return 188;
	  
	if ( adaption_ctr == 1 )
		return 4;

	if ( (adaption_ctr & 0x02 ) )
	{
		int adaption_apapt_len = pData[4];	
		if ( adaption_ctr == 3 )
		  return 4 + adaption_apapt_len + 1;	
	}

	return 4;
}

//////////////////////////////////////////////////////////////
void PrintPidStatistic( PVR_INFO_PARSER *PVRParser )
{
	int i;
	printf( "Pid statistic\n" );
	for ( i = 0; i<(int)PVRParser->pid_hist_num; i++ )
	{
		printf( "pid 0x%04x\t %ld\n", PVRParser->pid_hist[i].pid, PVRParser->pid_hist[i].counter );
		//printf( "pid %d\t\t%d\n", PVRParser->pid_hist[i].pid, PVRParser->pid_hist[i].counter );
	}
}

#include "TSEPGParser.h"
#define _PID_TBL_NUM 32
unsigned short pid_filter[_PID_TBL_NUM];
int pid_num = 0;
int EPGPidDump( void* context, unsigned char* data, int bytes )
{
	PID_ITEM *pPidInf = (PID_ITEM*)data;
	printf( "PID:0x%04x new pid\n", pPidInf->pid );
	if ( pid_num < _PID_TBL_NUM )
		pid_filter[pid_num++] = pPidInf->pid;
	else
	{
		printf( "pid is full\n" );
	}
	return 1;
}

/*
int ScanEPG( FILE* fp )
{
	int ct= 0;
	char buf[188*22];

	TS_EPG_PARSER* epg_parser = CreateTSEPGParser( ATSC_STREAM, 0 );
	SetupTSEPGParserDump( epg_parser, EPGPidDump, epg_parser, EPGDump, epg_parser );
	pid_num = 0;
	StartTSEPGParser( epg_parser );
	fseek( fp, 0, SEEK_SET );
	while ( !feof( fp ) )
	{
		int bytes = (int)fread( buf, 1, sizeof(buf), fp );	
		int size = bytes;
		unsigned char* data = buf;

		if ( bytes == 0 ) break;

		while ( size >= TS_PACKET_LENGTH )
		{
			int i;
			unsigned short pid;
			if ( *data != TS_SYNC ) //sync header
			{
				for (  ; size>TS_PACKET_LENGTH && *(data) != TS_SYNC ; size--, data++ );
				if ( size < TS_PACKET_LENGTH )
					break;
			}
			pid = GetTSPacketPid( data );
			for ( i = 0; i<pid_num; i++ )
				if ( pid == pid_filter[i] ) break;
			if ( i<pid_num )
				PushTSEPGPacketParser( epg_parser, data, 188 );

			data += 188;
			size -= 188;
		}

		//PushTSEPGPacketParser( epg_parser, buf, bytes );
		ct += bytes;

	}
	StopTSEPGParser( epg_parser );
	ReleaseTSEPGParser( epg_parser );
	fseek( fp, 0, SEEK_SET );
	return ct;
}
*/

int ScanPVRMeta( FILE* fp )
{
	int ct= 0, state = 0;
	int bytes, next_offset, offset;
	char buf[188];
	PVR_META_INF meta;

	fseek( fp, 0, SEEK_SET );
	while ( !feof( fp ) )
	{
		bytes = (int)fread( buf, 1, sizeof(buf), fp );
		if ( bytes < 188 ) break;
		offset = SearchPVRMetaInf( &next_offset, (unsigned char*)buf, sizeof(buf) );
		if ( offset < 0 )
			break;
		if ( CheckSagePVRData( (unsigned char*)buf+offset, bytes-offset ) )
			ct++;
		if ( GetPVRMetaInf( &meta, (unsigned char*)buf+offset ) && meta.state > state )
		{
			state = meta.state;
			if ( state == 1 )
				printf( "META-%d:program:%d tsid:%d pid:0x%04x\n", 
						meta.state, meta.program_num, meta.tsid, meta.program_pid );
			else
			if ( state == 2 )
				printf( "META-%d:video pid:0x%04x video type:0x%02x audio pid:0x%04x audio type:0x%02x\n", 
						meta.state, meta.video_pid, meta.video_type, meta.audio_pid, meta.audio_type );
		}
		fseek( fp, next_offset+offset+(188-sizeof(buf)), SEEK_CUR );
	}

	return ct;
}



#define PICTURE_START_CODE       0x00
#define SEQUENCE_HEADER_CODE     0xB3
#define EXTENSION_START_CODE     0xB5
#define GROUP_START_CODE         0xB8

inline unsigned char* _SeekMPEG2StartCode( const unsigned char* pData, int nBytes, unsigned	long StartCode )
{
	unsigned long code;

	if ( nBytes < 4 )
		return NULL;

	code = 0xffffff00 |*pData++;
	while ( --nBytes )
	{
		if ( ( (code&0x00ffffff)==0x01 ) && *(pData)==StartCode  )
			return (unsigned char* )pData-3;
		code = (( code << 8 )| *pData++ );
	}

	return NULL;
}

static int findTransitionPoint(const unsigned char* data, int length, unsigned short pid, unsigned char type )
{
    int numbytes=length;
    const unsigned char *p;
    if ( type == 0x1b ) //H.264 to be done.
    	return 0;
    	
    p = data;
    while ( numbytes >= 188 )
    {
    	int payload_offset;
	    //find sync
    	while ( *p != 0x47 )
    	{
    		while ( *p != 0x47 && numbytes >= 188 ) { p++; numbytes--; }
    		if ( *p == 0x47 && *(p+188) == 0x47 )
    			break;
    		else
    			p++;
    	}
    	if ( *p != 0x47 )
    		return -1;

    	if ( GetTSPacketPid( p ) != pid || !IsGroupStart( p ) )
    	{
    		p += 188;
    		numbytes -= 188;
    	}

    	payload_offset = GetPayloadOffset( p );
    	if ( _SeekMPEG2StartCode( p+payload_offset, 188-payload_offset, SEQUENCE_HEADER_CODE ) )
    	{
    		return (int)(p-data);
    	}
    		
    	p += 188;
    	numbytes -= 188;
    	
    }
    return -1;
}

int transition_num = 3;
int ParsePVRData( PVR_INFO_PARSER* pPVRParser, FILE *fp )
{
	unsigned char buf[22*188];
	int bytes = 0;
	fseek( fp, 0, SEEK_SET );
	while ( !feof( fp ) )
	{
		int n;
		n = (int)fread( buf, 1, sizeof(buf), fp );
		if ( n == 0 )
		{
			break;
		}
		PushPVRTSPacketParser( pPVRParser, buf, n );

		//test	findTransition
		if ( video_pid > 0 && transition_num )
		{
			int trans_pos = findTransitionPoint( buf, n, video_pid, (unsigned char)stream_type );
			if ( trans_pos >= 0 )
			{
				printf( "Transition point %d (%d %d)\n", bytes + trans_pos, bytes, trans_pos );
				transition_num--;
			}
		}
		////test	findTransition
 
		
		bytes += n;
	}
	return bytes;
}




#define SCAN_PVR	2
#define PARSING_PVR 1
#define MAX_CHECK_BYTES  32*1024*1024
#define ENABLE_ATS_OUTPUT 0x02
#define ENABLE_NATIVE_LOG 0x04
int main( int argc, char* argv[] )
{
	char *input_file = NULL;
	int  i;
	int  cmd = PARSING_PVR;
	int  options = 0;

	console_enabled = 0;
	printf( TEXT(">>>> SageTV native core. ver. 1.0.1 PVR-PTS. Qian <<<<\n") );
	SageLog(( _LOG_TRACE, 3, TEXT(">>>> SageTV native core. ver. 1.0.0 PTSInfo. Qian <<<<") ));
	SageLog(( _LOG_TRACE, 3, TEXT("     %s     "), CodeConfig() ));

	if ( argc < 2 )
	{
		puts( "Usage PVRPTS pvr_recording_file options " );
		puts( "options:\t-s  scan only\n\t\t-a  ouput ATS times\n\t\t-d  enable Native.log\n" );
		return 1;
	}
	
	input_file = argv[1];

	for ( i = 1; i<argc; i++ )
	{
		if ( *argv[i] == '-' || *argv[i] == '/' )
			switch ( *(argv[i]+1) )	{
				break;
			case 's': cmd = SCAN_PVR;
				break;
			case 'a': options |= ENABLE_ATS_OUTPUT;
				break;
			case 'd': options |= ENABLE_NATIVE_LOG;
				break;

		}
	}
	SageLog(( _LOG_TRACE, 3, TEXT("PTSInf file %s"), input_file ));

	if ( options & ENABLE_NATIVE_LOG )
		_enable_native_log( );

	{
		PVR_INFO_PARSER* PVRParser = CreatePVRTSParser( 32, ATSC_STREAM, TERRESTRIAL );
		FILE *fp;
		int bytes = 0;
		int pvr_block_num = 0;
		ResetPVRTSParser( PVRParser );
		StartPVRTSParser( PVRParser, 0 );
		SetupPVRTSDump( PVRParser, 
					(DUMP)PidDump, PVRParser,
					NULL, PVRParser,
					(DUMP)AVInfDump, PVRParser,
					NULL, PVRParser,
					(options&ENABLE_ATS_OUTPUT)? (DUMP)AtsDump:NULL, PVRParser,
					(DUMP)NewPidDump, PVRParser,
					(DUMP)MetaDump, PVRParser,
					(DUMP)ErrorInfDump, PVRParser );

		//SetupPVRTSTune( PVRParser, &Tune );

		fp = fopen( input_file, "rb" );

////test scan EPG
//if ( fp != NULL )
//{
// ScanEPG( fp );
// fclose( fp );
// return 0;
//}
		if ( fp != NULL )
		{
			if ( cmd == SCAN_PVR )
				pvr_block_num = ScanPVRMeta( fp );
			else
				bytes = ParsePVRData( PVRParser, fp );

			fclose( fp );
			PrintPidStatistic( PVRParser );
		} else
			printf( "can't open file %s\n", input_file );
		StopPVRTSParser( PVRParser );
		ReleasePVRTSParser( PVRParser );

		if ( cmd == SCAN_PVR )
			printf( "\nfound pvr_block:%d \n", pvr_block_num );
		else
			printf( "\nProcessed total bytes:%d \n", bytes );

	}

	return 0;
}

