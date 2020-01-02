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
// Eliminate silly MS compiler security warnings about using POSIX functions
#ifndef _WIN64
  #define _USE_32BIT_TIME_T
#endif
#pragma warning(disable : 4996)

#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include <assert.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include <string.h>
#include "AVUtility.h" 
#include "TSParser.h"
#include "SIParser.h"
#include "ConstData.h"


#include <stdarg.h>
#include <time.h>

#if defined(__APPLE__)
#include <libkern/OSByteOrder.h>
#endif

#ifdef WIN32   
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}
#endif

#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _flog x
#endif

static bool flog_enabled=true;
void enable_siparser_log()
{
	flog_enabled = true;
}

void disable_siparser_log()
{
	flog_enabled = false;
}

static void _flog( char* logname, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	
	if ( !flog_enabled ) return;

	fp = fopen( logname, "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );
	fprintf( fp, "%02d/%02d/%d %02d:%02d:%02d  ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
	ltm.tm_hour, ltm.tm_min, ltm.tm_sec );  
	va_start(args, cstr);
	vfprintf( fp, cstr, args );
	va_end(args);
	fclose( fp );
}
static void _flog_check()
{
	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
	}
}


#ifdef WIN32
#define stricmp  sage_stricmp                  //ZQ
#endif

static int TotalDescriptor( SI_PARSER* pParser, char *pData, int Bytes, unsigned char ExpectedTag );
static unsigned char* SeekDescriptor( SI_PARSER* pParser, char *pData, int Bytes, unsigned char ExpectedTag );
//static int DescriptorBytes( SI_PARSER* pParser, char *pData, int Bytes );
static int DescriptorParser( SI_PARSER* pParser, char *pData, int Bytes, unsigned char ExpectedTag, DESCRIPTOR* pDesc, char** pPos );

static  bool IsDVBPacket( SI_PARSER* pParser, unsigned short pid  );
static  bool IsATSCPacket( SI_PARSER* pParser, unsigned short pid  );
static void UnpackMGT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData );
static void UnpackVCT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData );
static int  ProcessVCT( SI_PARSER* pParser, TS_HEADER* ts_header, char* data, int bytes );
static int  ProcessEIT( SI_PARSER* pParser, TS_HEADER* ts_header, int type, char* data, int bytes );
static int  ProcessETT( SI_PARSER* pParser, TS_HEADER* ts_header, int type, char* data, int bytes );
static int  ProcessRTT( SI_PARSER* pParser, TS_HEADER* ts_header, char* data, int bytes );

static int  UnpackNIT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData );
static void FreeNITData( SI_PARSER* pParser );
static int  UnpackSDT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData );
static void FreeSDTData( SI_PARSER* pParser, SDT* sdt );
static int  UnpackDVBEIT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData );
static void FreeDVBEIT( SI_PARSER* pParser );
static int  UnpackEIT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, int type, unsigned char* pData, int Bytes );
static int  UnpackETT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, int type, unsigned char* pData, int Bytes );
static void FreeEITData( SI_PARSER* pParser );
static void FreeETTData( SI_PARSER* pParser );
static bool EquMultiString( SI_PARSER* pParser, MSS* mss1, MSS* mss2 );
static void UnpackMultiString( SI_PARSER* pParser, MSS** pMss, unsigned char* pData, int Length );
static void FreeMultiString( SI_PARSER* pParser, MSS* mss );
static int  UnpackRTT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData );
static void FreeRTTData( SI_PARSER* pParser );
static int  UnpackGenre( SI_PARSER* pParser, unsigned char* genre, unsigned char* pData, int Length  );
//static void FreeGenre( SI_PARSER* pParser, unsigned char* genre );

int TranslateJWideString2( char* buf_out, int buf_out_size, unsigned short* buf_in );

static time_t MJD2Locatime( unsigned char* pMJD );
static long BCDTime( unsigned char* pBCDcode );

static int UncompressHuffman( int Type, unsigned char* pOut, int MaxSize, unsigned char* pData, int Length );
static char* genre_string( unsigned char code );
static char* dvb_genre_string( unsigned char code );

static bool parser_network_name( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData );
static void free_network_name( SI_PARSER* pParser, void* DescData );

static bool parser_service_list( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData );
static void free_service_list( SI_PARSER* pParser, void* DescData );

static bool parser_terrestrial_delivery( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData );
static void free_terrestrial_delivery( SI_PARSER* pParser, void* pDescData );

static bool parser_cable_delivery( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData  );
static void free_cable_delivery( SI_PARSER* pParser, void* pDescData );

static bool parser_satellite_delivery( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData  );
static void free_satellite_delivery( SI_PARSER* pParser, void* pDescData );

static bool parser_service( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData );
static void free_service( SI_PARSER* pParser, void* pDescData );

static bool parser_short_event( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData );
static void free_short_event( SI_PARSER* pParser, void* pDescData );
//static bool short_event_updated( S_EVENT *s_event1, S_EVENT *s_event2 );

static bool parser_extended_event( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData );
static void free_extended_event( SI_PARSER* pParser, void* pDescData );

static bool parser_content_advisory( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData );
static void free_content_advisory( SI_PARSER* pParser, void* pDescData );
static bool extended_event_updated( E_EVENT *s_event1, E_EVENT *s_event2 );

//static bool parser_channel_long_name( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData );
//static void ( SI_PARSER* pParser, void* pDescData );

static bool parser_prental_rating( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData );
static void free_prental_rating( SI_PARSER* pParser, void* pDescData );

static bool parser_content_desc( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData );
static void free_content_desc( SI_PARSER* pParser, void* pDescData );

#ifdef SAGE_MEMORY_CHECK
/*************************************************************/
//memory managment for leaking checking ZQ
/*************************************************************/
#define SAGETV_MALLOC( x )		sagetv_malloc2( x, pParser, __LINE__ )
#define SAGETV_FREE( x )		sagetv_free2( x, pParser, __LINE__ )

#define MEM_CLOSE_CHECK(c)		_mem_close_check(c)
#define MEM_CHECK_INIT(c)		_mem_ckeck_init(c)
#define MEM_RANGE_CHEK( size, l )     _mem_range(  size, l )
#define MEM_ADD_BLOCK( p, s, c, l )   _mem_add( p, s, c, l )
#define MEM_FREE_BLOCK( p, c, l )     if ( _mem_release( p, c, l ) ) return;
/*************************************************************/
#else
#define SAGETV_MALLOC( x )		sagetv_malloc1( x )
#define SAGETV_FREE( x )		sagetv_free1( x )

#define MEM_CLOSE_CHECK(c)		
#define MEM_CHECK_INIT(c)		
#define MEM_RANGE_CHEK( size, l )     
#define MEM_ADD_BLOCK( p, s, c, l )   
#define MEM_FREE_BLOCK( p, c, l )     
#endif

void _raise_exception()
{
	char *p;
	p = (char*)01;
	*p = 99;  //raise a exception for Dr.Watson
}

void _log_error( char* mesg )
{
	FILE* fp=fopen( "ErrorCatch.log", "a+");
	if ( fp != NULL )
	{
		fprintf( fp, "%s.", mesg );
		fflush( fp );
		fclose( fp );
	}
}

void _mem_ckeck_init( void* ctx )
	{
		int size = sizeof(MEM_BLK) *MAX_HEAP_SIZE;
		SI_PARSER* cls = (SI_PARSER* )ctx;

		cls->_mem_blk = malloc( size );
		memset( cls->_mem_blk, 0, size );
		cls->_mem_blk_usage = 0;
	}

void _mem_close_check( void* ctx )
	{
		int i,sum = 0, num = 0;
		for ( i = 0; i<MAX_HEAP_SIZE; i++ )
		{
			if ( ((SI_PARSER* )ctx)->_mem_blk[i].state ) 
			{
				char buf[64];
				sprintf( buf, "Caught a unfree memory block size:%ld line:%d \n", 
					((SI_PARSER* )ctx)->_mem_blk[i].size, ((SI_PARSER* )ctx)->_mem_blk[i].line	);
				_log_error( buf );

				sum += ((SI_PARSER* )ctx)->_mem_blk[i].size;
				num++;
			}
		}
		if ( sum )
		{
			char buf[64];
			sprintf( buf, "Caught total a unfree memory block %d size:%d\n", num, sum );
			_log_error( buf );
		}

		if ( ((SI_PARSER* )ctx)->_mem_blk )
			free( ((SI_PARSER* )ctx)->_mem_blk );
	} 

void _mem_add( char* p, int size, void* ctx, int line)
	{
		//ZQ trap
		if ( ((SI_PARSER* )ctx)->_mem_blk_usage >= MAX_HEAP_SIZE )
		{
			char buf[64];
			sprintf( buf, "Too many block allocated:%d\n", ((SI_PARSER* )ctx)->_mem_blk_usage );
			_log_error( buf );
		} else
		{
			int i;
			for ( i = 0; i<MAX_HEAP_SIZE; i++ )
			{
				if ( ((SI_PARSER* )ctx)->_mem_blk[i].state == 0 )
				{
					((SI_PARSER* )ctx)->_mem_blk[i].state = 1;
					((SI_PARSER* )ctx)->_mem_blk[i].size = size;
					((SI_PARSER* )ctx)->_mem_blk[i].addr = p;
					((SI_PARSER* )ctx)->_mem_blk[i].line = line;
					((SI_PARSER* )ctx)->_mem_blk_usage++;
					break;
				}
			}
		} //ZQ
	}

int _mem_release( char* p, void* ctx, int line )
	{	int i;
		for ( i = 0; i<MAX_HEAP_SIZE; i++ )
		{
			if ( ((SI_PARSER* )ctx)->_mem_blk[i].state && ((SI_PARSER* )ctx)->_mem_blk[i].addr == p )
			{
				((SI_PARSER* )ctx)->_mem_blk[i].state = 0; 
				((SI_PARSER* )ctx)->_mem_blk_usage--;
				break;
			}
		}
		if ( i >= MAX_HEAP_SIZE )
		{
			char buf[64];
			sprintf( buf, "Caught a illegal memory free %p at line:%d\n", p, line );
			_log_error( buf );
			return 1;
		}
		return 0;
	}

void _mem_range( int size, int line )
{
	//ZQ trap
	if ( size <= 0 || size > 64*1024 )
	{
		char buf[64];
		sprintf( buf, "catch a invalid malloc size:%d line:%d\n", size, line );
		 _log_error( buf );
		_raise_exception();
	} 
}
/*************************************************************/
static long _mem_alloc = 0; 
#include <signal.h>
_inline static void* sagetv_malloc1( int size )
{
	char *p;
	p = (char*)malloc( size );
	if ( p ) memset( p, 0, size );
	return (void*)p; 
}

_inline static void  sagetv_free1( void* p )
{
	if ( p ) free( p );
}

_inline static void* sagetv_malloc2( int size, void* ctx, int line )
{
	char *p;
	
	//ZQ
	MEM_RANGE_CHEK( size, line );

	p = (char*)malloc( size );
	if ( p )
	{
		memset( p, 0, size );
		_mem_alloc++;
	} else
	{
		char buf[64];
		sprintf( buf, "malloc failed size:%d SIParser.c line:%d\n", size, line );
		 _log_error( buf );
		_raise_exception();
	}

	//ZQ
	MEM_ADD_BLOCK( p, size, ctx, line );

	return (void*)p; 
}

_inline static void  sagetv_free2( void* p, void* ctx, int line )
{
	MEM_FREE_BLOCK( p, ctx, line );

	if ( p ) 
	{
		_mem_alloc--;
		free( p );
	}
}


/*************************************************************/

SI_PARSER* SIParerOpen( TS_PARSER* pTSParser, int StreamType )
{
	SI_PARSER* pSIParser;

	//ZQ
	pSIParser = sagetv_malloc( sizeof(SI_PARSER) );  //ZQ
	if ( pSIParser == NULL )
		return NULL;
	memset( pSIParser, 0, sizeof(SI_PARSER) );
	pSIParser->pTSParser = pTSParser;
	pSIParser->DVB_count = 0;
	pSIParser->ATSC_count = 0;
	pSIParser->stream_type = StreamType;
	pSIParser->epg_context = NULL;
	pSIParser->epg_dumper = NULL;


	MEM_CHECK_INIT( pSIParser );

	return pSIParser;
}

void SIParserClose( SI_PARSER* pParser )
{
	int i;
	if ( pParser == NULL )
		return;

	FreeRTTData( pParser );
	FreeNITData( pParser );
	FreeEITData( pParser );
	FreeETTData( pParser );
	FreeDVBEIT( pParser );
	

	//ZQ
	MEM_CLOSE_CHECK( pParser );

	if ( pParser->PSIPSection.data )
	{
		sagetv_free( pParser->PSIPSection.data );  //ZQ memory is allocted outside
		pParser->PSIPSection.data = NULL;
		pParser->PSIPSection.data_size = 0;
	}
	if ( pParser->VCTSection.data )
	{
		sagetv_free( pParser->VCTSection.data );  //ZQ memory is allocted outside
		pParser->VCTSection.data = NULL;
	}
	if ( pParser->RTTSection.data )
	{
		sagetv_free( pParser->RTTSection.data );  //ZQ memory is allocted outside
		pParser->RTTSection.data = NULL;
		pParser->RTTSection.data_size = 0;
	}

	for ( i = 0; i<MAX_EIT_NUM; i++ )
	{
		if ( pParser->EITSection[i].data )
		{
			sagetv_free( pParser->EITSection[i].data );  //ZQ memory is allocted outside
			pParser->EITSection[i].data = NULL;
			pParser->EITSection[i].data_size = 0;
		}
	}

	if ( pParser->NITSection.data )
	{
		sagetv_free( pParser->NITSection.data );  //ZQ memory is allocted outside
		pParser->NITSection.data = NULL;
		pParser->NITSection.data_size = 0;
	}
	if ( pParser->SDTSection.data )
	{
		sagetv_free( pParser->SDTSection.data );  //ZQ memory is allocted outside
		pParser->SDTSection.data = NULL;
		pParser->SDTSection.data_size = 0;
	}

	sagetv_free( pParser );
}

void SIParserReset( SI_PARSER* pParser )
{
	int i,j;
	if ( pParser == NULL )
		return ;

	pParser->stream_type = 0;
	pParser->sub_stream_type = 0;

	memset( pParser->tsid_tbl, 0, sizeof(pParser->tsid_tbl) );
	memset( pParser->mgt, 0x0, sizeof(pParser->mgt) );
	memset( pParser->vct, 0x0, sizeof(pParser->vct) );
	memset( pParser->svt, 0x0, sizeof(pParser->svt) );
	memset( pParser->dvb_check, 0x0, sizeof(pParser->dvb_check) );
	pParser->VCTNum = 0;
	pParser->MGTNum = 0;
	pParser->SVTNum = 0;
	pParser->DVBCheckNum = 0;
	pParser->VCTSectionCount = 0;
	pParser->MGTSectionCount = 0;
	pParser->NITSectionCount = 0;
	pParser->SDTSectionCount = 0;
	pParser->EITSectionCount = 0;
	pParser->RTTSectionCount = 0;
	pParser->DEITNum = 0;

	for ( i = 0; i<MAX_EIT_NUM; i++ )
	{
		for ( j = 0; j<MAX_EPG_NUM_PER_CH; j++ )
		{
			pParser->eit[i][j].event_id  = 0;
			pParser->eit[i][j].source_id = 0;
			memset( pParser->eit[i][j].genre, 0, sizeof(pParser->eit[i][j].genre) );
		}
	}

	for ( i = 0; i < MAX_DEIT_NUM; i++ )
	{
		pParser->deit[i].event_id = 0;
		pParser->deit[i].ONID = 0;
		pParser->deit[i].TSID = 0;
		pParser->deit[i].service_id = 0;
		pParser->deit[i].start_time = 0;
		pParser->deit[i].during_length =0;
	}

	pParser->TSDT_count=pParser->NIT_count=pParser->SDT_count = 0;
	pParser->EIT_count=pParser->RST_count=pParser->TDT_count= 0 ;
	pParser->MIP_count=pParser->RNT_count=pParser->DIT_count=pParser->SIT_count = 0;
	pParser->PAT_E_count=pParser->STT_E_count=pParser->OPERA_count= 0 ;
	pParser->PSIP_count=pParser->SCTE_count = 0;

	FreeNITData( pParser );
	FreeRTTData( pParser );
	FreeDVBEIT( pParser );
	FreeEITData( pParser );
//pParser->SDTSectionCount2 = 0;
// pParser->SIct= pParser->SDTct= pParser->NITct=0;
// pParser->SDTct2=pParser->SDTct3=pParser->SDTct4=0;
} 

void SIParserSetStreamType( SI_PARSER* pParser, int type )
{
	pParser->stream_type = type;
}

int SIParserGetStreamType( SI_PARSER* pParser )
{
	return pParser->stream_type;
}


int  GetSIChannelNum( SI_PARSER* pParser )
{
	int num, i;
	if ( pParser == NULL )
		return 0;

	TSUnLockPatPmt( pParser->pTSParser ); //left over data from last channel, may lock PAT table
	//SelectTSID( pParser->pTSParser, 0 );  //unlock tsid

	num = 0;
	if ( pParser->stream_type == 0 ) return 0;

	if ( pParser->stream_type == ATSC_STREAM_TYPE )
	{
		for ( i = 0; i<pParser->VCTNum; i++ )
			if ( pParser->vct[i].flag )
				num++;
	} else
	if ( pParser->stream_type == DVB_STREAM_TYPE )
	{
		for ( i = 0; i<pParser->SVTNum; i++ )
		{
			if ( pParser->svt[i].flag > 0  )
			{
				int service_type;
				service_type = GetProgramAVType( pParser->pTSParser, pParser->svt[i].service_id );
				if ( service_type == 1 || service_type == 3 ) //service_type == 2 auido only
					num++;
			}
		}

		if ( num && SIChannelInfoReady( pParser ) )
			return num;

		if (  pParser->NITSectionCount > 2 )
		{
			if ( pParser->SDTSectionCount )  //
				return num;
			else
				return -1; //timeout, there is no SDT packet;
		}

		return 0;  //wait for more SDT packet, give all channel information
	}

	return num;
}

int GetSIProgramName( SI_PARSER* pParser, unsigned short ProgramID, char* pNameBuf, int nNameSize )
{
	int j;
	if ( pParser == NULL || ProgramID == 0 )
		return 0;

	memset( pNameBuf, 0, nNameSize );
	if ( pParser->stream_type == ATSC_STREAM_TYPE )
	{
		for ( j = 0; j<pParser->VCTNum; j++ )
		{
			if (  pParser->vct[j].program_id == ProgramID )
			{
				memcpy( pNameBuf, pParser->vct[j].Name, _MIN( nNameSize, sizeof(pParser->vct[j].Name) ) );
				return 1;
			}
		}
	} else
	if ( pParser->stream_type == DVB_STREAM_TYPE )
	{
		for ( j = 0; j<pParser->SVTNum ; j++ )
		{
			if ( ProgramID == pParser->svt[j].service_id )
			{
				memcpy( pNameBuf, pParser->svt[j].Name, _MIN( nNameSize, sizeof(pParser->svt[j].Name) ) );
				return pParser->svt[j].service_type;
			}
		}
	} 
	
	return 0;
}

int GetSIProgramByName( SI_PARSER* pParser, char* name )
{
	int j;
	if ( name[0] == 0x0 || pParser == NULL )
		return -1;

	if ( pParser->stream_type == ATSC_STREAM_TYPE )
	{
		for ( j = 0; j<pParser->VCTNum; j++ )
		{
			char temp[sizeof(pParser->vct[j].Name)];
			TranslateJWideString2( temp, sizeof(temp), (unsigned short*)pParser->vct[j].Name );
			if ( !stricmp( name, temp ) )
				return pParser->vct[j].program_id;
		}
		if ( pParser->VCTNum > 0 )
			return -1;
		else 
		{
			//there is no vct at all (in QAM), 
			if ( pParser->pTSParser->pmt_frames < 40 )
				return -1;

			return 0;
		}

	} else
	if ( pParser->stream_type == DVB_STREAM_TYPE )
	{
		int num =0;
		for ( j = 0; j<pParser->SVTNum ; j++ )
		{
			if (  pParser->svt[j].flag ) 
			{
				if ( !stricmp( name, pParser->svt[j].Name ) )
					return pParser->svt[j].service_id;
				num++;
			}
		}
		//if ( num == pParser->SVTNum )
		//	return -1;
		//else
		//	return 0;
		if ( pParser->pTSParser->pmt_frames > 80 )
			return -1;

		return 0;
	} else
		return 0;
	
	return -1;

}

int GetSIProgramByMajorMinor( SI_PARSER* pParser, unsigned short major, unsigned short minor )
{
	int j;
	if ( pParser->stream_type != ATSC_STREAM_TYPE && pParser->stream_type != 0 )
		return -1;

	for ( j = 0; j<pParser->VCTNum; j++ )
	{
		if ( pParser->vct[j].major_num == major && pParser->vct[j].minor_num == minor )
		{
			return pParser->vct[j].program_id;
		}
	}

	return 0;
}

unsigned short GetSIProgramTSID( SI_PARSER* pParser, unsigned short program )
{
	if ( pParser->stream_type == ATSC_STREAM_TYPE )
	{
		if ( pParser->VCTNum )
		{
			int j;
			if ( program == 0 )
				return pParser->VCTTSID;
			
			for ( j = 0; j<pParser->VCTNum; j++ )
			{
				if ( pParser->vct[j].program_id == program )
					return pParser->vct[j].TSID;
			}
			return 0;
		} else
			return 0;
	}
	else
	if ( pParser->stream_type == DVB_STREAM_TYPE )
	{
		int j;
		for ( j = 0; j<pParser->SVTNum; j++ )
		{
			if ( pParser->svt[j].service_id == program  )
			{
				return pParser->svt[j].TSID;
			}
		}
	}

	return 0;
}



int  GetSIChannelName( SI_PARSER* pParser, CHANNEL_NAME* pChName, int MaxSize )
{
	int i=0, j;
	int size = 0;
	if ( pParser == NULL || pChName == NULL )
		return 0;

	if ( pParser->stream_type == ATSC_STREAM_TYPE )
	{
		i = 0;
		for ( j = 0; j<pParser->VCTNum && size + (int)sizeof(CHANNEL_NAME) <= MaxSize; j++ )
		{
			if ( pParser->vct[j].flag  )
			{
				pChName[i].channel.a = pParser->vct[j].major_num;
				pChName[i].channel.b = pParser->vct[j].minor_num;
				pChName[i].channel.c = pParser->vct[j].program_id;
				pChName[i].type = pParser->vct[j].service_type;
				memcpy( pChName[i].name, pParser->vct[j].Name, 
						_MIN( sizeof(pParser->vct[j].Name), sizeof(pParser->vct[j].Name) ) );
				size += sizeof(CHANNEL_NAME);
				i++;
			}
		}

		while ( size + (int)sizeof(CHANNEL_NAME)< MaxSize )
		{
			pChName[i].channel.a = 0;
			pChName[i].channel.b = 0;
			pChName[i].channel.c = 0;
			memset( pChName[i].name, 0, sizeof(pChName[i].name) );
			size += sizeof(CHANNEL_NAME);
			i++;
		}
		
	} else
	if ( pParser->stream_type == DVB_STREAM_TYPE )
	{
		i = 0;
		for ( j = 0; j<pParser->SVTNum && size + (int)sizeof(CHANNEL_NAME) <= MaxSize; j++ )
		{
			int service_type;
			service_type = GetProgramAVType( pParser->pTSParser, pParser->svt[j].service_id );
			if ( service_type == 1 || service_type == 3 ) //service_type == 2 auido only
			//if ( pParser->svt[j].service_type == 0x01  && CheckProgramVaild( pParser->pTSParser, pParser->svt[j].service_id) )
			{
				pChName[i].channel.a = pParser->svt[j].ONID;
				pChName[i].channel.b = pParser->svt[j].TSID;
				pChName[i].channel.c = pParser->svt[j].service_id;
				pChName[i].type = pParser->svt[j].service_type;
				memcpy( pChName[i].name, pParser->svt[j].Name, 
							_MIN( sizeof(pParser->svt[j].Name), sizeof(pChName[i].name) ) );
				size += sizeof(CHANNEL_NAME);
				i++;
			}
		}

		while ( size + (int)sizeof(CHANNEL_NAME)< MaxSize )
		{
			pChName[i].channel.a = 0;
			pChName[i].channel.b = 0;
			pChName[i].channel.c = 0;
			memset( pChName[i].name, 0, sizeof(pChName[i].name) );
			size += sizeof(CHANNEL_NAME);
			i++;
		}

	} else
		return 0;

	return i;

}

bool  SIChannelInfoReady( SI_PARSER* pParser )
{
	int i, j;
	if ( pParser == NULL )
		return 0;

	if ( pParser->stream_type == 0 ) return false;

	if ( pParser->stream_type == ATSC_STREAM_TYPE )
	{
		return pParser->VCTNum > 0;
	} else
	if ( pParser->stream_type == DVB_STREAM_TYPE )
	{
		TS_PAT *pat;
		if ( pParser->SVTNum == 0 ) return false;
		pat = &pParser->pTSParser->Pat;
		if ( pat->NumPrograms == 0 ) return false;

		//check all PAT is in SVT
		for ( i = 0; i<pat->NumPrograms; i++ )
		{
			unsigned short service_id;
			int service_type;
			service_id = pat->ProgramNumber[i];
			if ( service_id && service_id != 0xffff )
			{
				service_type = GetProgramAVType( pParser->pTSParser, service_id );
				if ( service_type < 0 ) //pmt isn't ready
					return false;
				if ( service_type == 1 || service_type == 3 ) //service_type == 2 auido only
				{
					for ( j = 0; j<pParser->SVTNum; j++ )
					{
						if ( pParser->svt[j].service_id == service_id )
							break;
					}
					if ( j >= pParser->SVTNum )
						return false;
				}
			}
		}
		//for ( i = 0; i<pParser->SVTNum; i++ )
		//{
		//	if ( pParser->svt[i].service_type == 0x01 && pParser->svt[i].flag == 0x01 ) 
		//		return false;
		//}
		return true;
	}

	return false;
}

void SetupEPGDumper( SI_PARSER* pParser, LPFNMesgDump dump, void* context )
{
	pParser->epg_context = context;
	pParser->epg_dumper = dump;
}

static VCT* LookupVCT( SI_PARSER* pParser, unsigned short SourceId )
{
	int i;
	for ( i = 0; i<MAX_VCT_TBL; i++ )
	{
		if ( pParser->vct[i].source_id == SourceId )
			return &pParser->vct[i];
	}

	return NULL;
}

static int ContentAdvisoryBytes( SI_PARSER* pParser, CAD *cad )
{
	int i, j;
	int total_bytes;
	RTT *rtt;

	if ( cad == NULL )
		return 0;

	total_bytes = 0;
	for ( i = 0; i<cad->region_num ; i++ )
	{
		rtt = NULL;
		for ( j = 0; j<MAX_RTT_TBL; j++ )
		{
			if ( pParser->rtt[j].rating_region == cad->rating[i].rating_region )
			{
				rtt = &pParser->rtt[j];
				break;
			}
		}
		if ( rtt != NULL )
		{
			for ( j = 0; j <cad->rating[i].rating_num; j++ )
			{
				int m, n;
				char* cad_name = "";
				char* cad_value = "";
				m = cad->rating[i].rating_index[j];
				n = cad->rating[i].value_index[j];
				if ( m < rtt->dimension_num )
				{
					cad_name = rtt->rating[m].dimension_name;
					if ( n < rtt->rating[m].rating_num )
						cad_value = rtt->rating[m].rating_value[n];
				}

				if ( cad_name[0] && cad_value )
				{
					total_bytes += strlen( cad_name) + strlen( cad_value ) + 2;
				}

				total_bytes += strlen((char*)cad->rating[i].description)+3;
			}
		}
	}
	return total_bytes;
	
}

static int ContentAdvisoryString( SI_PARSER* pParser, CAD *cad, char* buf, int max_bytes )
{
	int i, j;
	char* p; 
	int total_bytes;
	RTT *rtt;
	p = buf;

	if ( cad == NULL )
		return 0;

	total_bytes = 0;
	for ( i = 0; i<cad->region_num ; i++ )
	{
		rtt = NULL;
		for ( j = 0; j<MAX_RTT_TBL; j++ )
		{
			if ( pParser->rtt[j].rating_region == cad->rating[i].rating_region )
			{
				rtt = &pParser->rtt[j];
				break;
			}
		}
		for ( j = 0; j <cad->rating[i].rating_num; j++ )
		{
			if ( rtt != NULL )
			{

				int m, n;
				char* cad_name = "";
				char* cad_value = "";
				m = cad->rating[i].rating_index[j];
				n = cad->rating[i].value_index[j];
				if ( m < rtt->dimension_num )
				{
					cad_name = rtt->rating[m].dimension_name;
					if ( n < rtt->rating[m].rating_num )
						cad_value = rtt->rating[m].rating_value[n];
				}

				if ( cad_name[0] && cad_value )
				{
					if ( strlen( p ) + strlen( cad_name) + strlen( cad_value ) + 2 < (unsigned int)max_bytes )
					{
						strcat( p, cad_name );
						strcat( p, ":" );
						strcat( p, cad_value );
						strcat( p, "," );
						total_bytes += 2 + strlen(cad_name)+strlen(cad_value);
					}
				}
			}

			if ( cad->rating[i].description[0] && strlen( p )+strlen((char*)cad->rating[i].description)+3< (unsigned int)max_bytes  )
			{
				strcat(p,"(");
				strcat( p,  (char*)cad->rating[i].description );
				strcat(p,")," );
				total_bytes += 3 + strlen((char*)cad->rating[i].description);
			}
		}
	}
	return total_bytes;
}

int EPGNotifyATSC( SI_PARSER* pParser, EIT* pEit )
{
	VCT* pVct;
	char* buf, *p;
	int bytes, used_bytes;
	int size;
	int i, j, ret=0;
	char language[4]={0};

	if ( pEit == NULL )  return 0;
	////EIT Time GPS: 1/6/1980, local time: 1/1/1970

	pVct= LookupVCT( pParser, pEit->source_id );
	if ( pVct == NULL )
		return 0;

	bytes = 0;
	for ( i = 0; pEit->title != NULL && i< pEit->title->num_string; i++ )
	{
		for ( j = 0; j<pEit->title->string_list[i].num_segment; j++ )
		{
			if ( pEit->title->string_list[i].seg_list[j].compression_type == 0 )
				bytes += pEit->title->string_list[i].seg_list[j].byte_length;
			else
				bytes += pEit->title->string_list[i].seg_list[j].byte_length * 8;
		}
	}
	
	for ( i = 0; pEit->program != NULL && i< pEit->program->num_string; i++ )
	{
		for ( j = 0; j<pEit->program->string_list[i].num_segment; j++ )
		{
			if ( pEit->program->string_list[i].seg_list[j].compression_type == 0 )
				bytes += pEit->program->string_list[i].seg_list[j].byte_length;
			else
				bytes += pEit->program->string_list[i].seg_list[j].byte_length * 8;
		}
	}

	for ( i = 0; pEit->title != NULL && i< pEit->title->num_string; i++ )
	{
		bytes += 4;
		strncpy( language, (char*)pEit->title->string_list[i].lanuage_code, 4 );
		break;
	}

	for ( i = 0; i<MAX_GENRE_NUM; i++ )
	{
		bytes += strlen( genre_string(pEit->genre[i]))+1;
	}
	
	bytes += ContentAdvisoryBytes( pParser, pEit->cad );

	bytes += 64;
	buf = SAGETV_MALLOC( bytes );
	memset( buf, 0x0, bytes );
	used_bytes = 0; 

	sprintf( buf, "EPG-0|%d-%d %s|GPS:%lld|%ld|%s|", 
		pVct->major_num, pVct->minor_num, pVct->minor_num == 0 ? "AN" :"DT", 
		(long long) pEit->start_time, pEit->during_length, language	);

	used_bytes += strlen( buf );
	if ( used_bytes >= bytes )
		return ret;

	p = buf+strlen( buf );
	for ( i = 0; pEit->title != NULL && i< pEit->title->num_string; i++ )
	{
		for ( j = 0; j<pEit->title->string_list[i].num_segment; j++ )
		{
			if ( used_bytes >= bytes )
			{
				printf( "Warning: EPG buffer is too small, data lost\n" );
				break;
			}

			if ( pEit->title->string_list[i].seg_list[j].compression_type == 0 )
			{
				memcpy( p, pEit->title->string_list[i].seg_list[j].mesg,
						pEit->title->string_list[i].seg_list[j].byte_length );
				p += pEit->title->string_list[i].seg_list[j].byte_length;
				used_bytes += pEit->title->string_list[i].seg_list[j].byte_length;
			} else
			{
				size = UncompressHuffman( pEit->title->string_list[i].seg_list[j].compression_type,
							(unsigned char*)p, bytes-used_bytes, 
					        	pEit->title->string_list[i].seg_list[j].mesg,
							pEit->title->string_list[i].seg_list[j].byte_length );
				p += size;
				used_bytes += size;
				if ( size && *p == 0 ) //remove terminator
				{
					p--; used_bytes--;
				}

			}
		}
	}

	*p++ = '|'; used_bytes++;
	for ( i = 0; pEit->program != NULL && i< pEit->program->num_string; i++ )
	{
		for ( j = 0; j<pEit->program->string_list[i].num_segment; j++ )
		{
			if ( used_bytes >= bytes )
			{
				printf( "Warning: EPG buffer is too small, data lost\n" );
				break;
			}

			if ( pEit->program->string_list[i].seg_list[j].compression_type == 0 )
			{
				memcpy( p, pEit->program->string_list[i].seg_list[j].mesg,
						   pEit->program->string_list[i].seg_list[j].byte_length );
				p += pEit->program->string_list[i].seg_list[j].byte_length;
				used_bytes += pEit->program->string_list[i].seg_list[j].byte_length;
			} else
			{
				int size;
				size = UncompressHuffman(    pEit->program->string_list[i].seg_list[j].compression_type,
							(unsigned char*)p, bytes-used_bytes, 
							pEit->program->string_list[i].seg_list[j].mesg,
							pEit->program->string_list[i].seg_list[j].byte_length );
				p += size;
				used_bytes += size;
				if ( size && *p == 0 ) //remove terminator
				{
					p--; used_bytes--;
				}
			}
		}
	}

	if ( used_bytes+1 < bytes )
	{
		*p++ = '|';
		used_bytes++;
	}

	size = ContentAdvisoryString( pParser, pEit->cad, p, bytes - used_bytes );
	used_bytes += size;
	p += size;

	if ( used_bytes+1 < bytes )
	{
		*p++ = '|';
		used_bytes++;
	}

	for ( i = 0; i<MAX_GENRE_NUM; i++ )
	{
		char* string = genre_string(pEit->genre[i]);
		if ( string[0] && used_bytes + (int)strlen(string) + 1 < bytes  )
		{
			strcat( p, string );
			strcat( p, "," );
			p += strlen(string) + 1;
			used_bytes += strlen(string) + 1;
		}
	}

	if ( used_bytes+1 < bytes )
	{
		*p++ = '|';
		used_bytes++;
	}

	if ( used_bytes+1 < bytes )
		*p++ = 0x0;

	//printf( "Got a EPG :%s \n", buf );

	if ( pParser->epg_dumper )
		ret = pParser->epg_dumper( pParser->epg_context, used_bytes, buf );

	SAGETV_FREE( buf );
	return ret;
	
}

static int GetDVBTextCharSet( unsigned char* code, int bytes, char* desc,  int desc_size, int* offset );
static int FilterDVBTextCtrlCode( unsigned char* code, int bytes, int code_byte );
static int TotalDVBTextCtrlCodeNum( unsigned char* code, int bytes, int code_byte );
static int MakeTextMesgString ( char* buffer, int buffer_size, char* message, int mesg_length );
int EPGNotifyDVB( SI_PARSER* pParser, DEIT* pEit )
{
	S_EVENT* pEvent;
	char* buf, *p;
	int bytes;
	int i, ret = 0;
	struct tm *utc;
	char utc_time[30];
	int  ext_mesg_length;
	//time_t lt;

	if ( pEit == NULL ) return 0;
	pEvent = pEit->event;
	if ( pEvent == NULL )  return 0;
	
	//lt = pEit->start_time - 315532800+6*3600*24;    //convert to start time 1/6/1980 from 1/1/1970  
	//lt = pEit->start_time - 315964800;
	utc = localtime( (const time_t *)&pEit->start_time );  //revseral from mktime()
	if ( utc == NULL ) return 0;

	utc->tm_hour -= utc->tm_isdst > 0 ? 1 : 0;  //get ridee of saving time
	sprintf( utc_time, "UTC:%02d/%02d/%d %02d:%02d:%02d", 
		     utc->tm_mon+1, utc->tm_mday, utc->tm_year+1900,
			 utc->tm_hour,utc->tm_min, utc->tm_sec );

	
	bytes = pEvent->title_length + pEvent->mesg_length  + 18;
	if ( pEit->rating != NULL ) bytes += pEit->rating->num*8;
	if ( pEit->content_desc != NULL )
	{
		int len = 0;
		for ( i = 0; i<MAX_GENRE_NUM && pEit->content_desc[i]; i++ )
			len += strlen( dvb_genre_string(pEit->content_desc[i]) );

		bytes += len;
	}

	//get extended message length
	ext_mesg_length = 0;
	if ( pEit->ext_event_flag )
	{
		for ( i = 0; pEit->ext_event[0] && i<=pEit->ext_event[0]->item_last_no; i++ )
		{
			if ( pEit->ext_event[i] && pEit->ext_event[i]->text_length )
				ext_mesg_length += pEit->ext_event[i]->text_length + 26;

			if ( pEit->ext_event[i] && pEit->ext_event[i]->item_number && pEit->ext_event[i]->items != NULL )
			{
				int j;
				for ( j = 0; j<pEit->ext_event[i]->item_number; j++ )
				{
					ext_mesg_length += pEit->ext_event[i]->items[j].item_desc_length+
							pEit->ext_event[i]->items[j].item_text_length + 58;
				}
			}
		}
	}

	bytes += ext_mesg_length;

	if ( bytes < 0 || bytes > 4096*2 ) return 0;

	bytes += 150;
	buf = SAGETV_MALLOC( bytes );
	memset( buf, 0x0, bytes );
	sprintf( buf, "EPG-1|%d-%d-%d %s|%s|%ld|%s|", 
		pEit->ONID, pEit->TSID, pEit->service_id,
		pEit->service_id ? "DT":"AN",
		utc_time,	pEit->during_length,
		pEvent->lanuage_code		);

	if ( strlen( buf ) > 150 )
	{
		SAGETV_FREE( buf );
		return 0;
	}

	p = buf+strlen( buf );
	if ( pEvent->title != NULL && pEvent->title_length > 0  )
	{
		p += MakeTextMesgString( p, bytes-(p-buf), (char*)pEvent->title, pEvent->title_length );
		*p++ = '|';
		*p = 0x0;
	} else
	{
		*p++ = '|';
		*p = 0x0;
	}

	if ( (pEvent->message!= NULL && pEvent->mesg_length > 0 ) || (ext_mesg_length) > 0 )
	{
		if ( pEvent->message!= NULL && pEvent->mesg_length > 0 )
		{
			p += MakeTextMesgString( p, bytes-(p-buf), (char*)pEvent->message, pEvent->mesg_length );
		}
		if ( ext_mesg_length ) 
		{
			for ( i = 0; pEit->ext_event[0] && i<=pEit->ext_event[0]->item_last_no; i++ )
			{
				if ( pEit->ext_event[i]->text_length )
				{
					p  += MakeTextMesgString( p, bytes-(p-buf), (char*)pEit->ext_event[i]->text, 
						                                   pEit->ext_event[i]->text_length );
				}
			}
			
			for ( i = 0; pEit->ext_event[0] && i<=pEit->ext_event[0]->item_last_no; i++ )
			{

				if ( pEit->ext_event[i] && pEit->ext_event[i]->item_number && pEit->ext_event[i]->items != NULL )
				{
					int j;
					for ( j = 0; j<pEit->ext_event[i]->item_number; j++ )
					{
						if ( pEit->ext_event[i]->items[j].item_desc_length && pEit->ext_event[i]->items[j].item_text_length &&
							bytes-(p-buf) > 6 )
						{
							int len;
							*p++ = '{';
							len = MakeTextMesgString( p, bytes-(p-buf), 
								(char*)pEit->ext_event[i]->items[j].item_desc, 
								pEit->ext_event[i]->items[j].item_desc_length );
							p += len;
							*p++ = ':';
							len = MakeTextMesgString( p, bytes-(p-buf),
								(char*)pEit->ext_event[i]->items[j].item_text, 
								pEit->ext_event[i]->items[j].item_text_length );
							p += len;
							*p++ = '}'; 
						}
					}
				}
			} 
		}

		*p++ = '|';
		*p = 0x0;
	}else
	{
		*p++ = '|';
		*p = 0x0;
	}
	//rating
	if ( pEit->rating != NULL ) 
	{
		char temp[16];
		for ( i = 0; i<pEit->rating->num; i++ )
		{
			sprintf( temp, "%s-%02d, ", pEit->rating->rating[i].country, pEit->rating->rating[i].age  );
			if ( (int)strlen( buf) + (int)strlen( temp ) < bytes )
			{
				strcat( p, temp );
				p += strlen( temp );
			}
		}
	}
	
	if ( (int)strlen( buf )+2 < bytes ) 
		*p++ = '|';
	//genre code 
	if ( pEit->content_desc )
	{
		for ( i = 0; i<MAX_GENRE_NUM && pEit->content_desc[i]; i++ )
		{
			char* string = dvb_genre_string(pEit->content_desc[i]);
			if ( string[0] && (int)strlen(buf) + (int)strlen(string) + 1 < bytes  )
			{
				strcat( p, string );
				p += strlen(string);
				string = dvb_genre_string(pEit->content_desc[i+1]);
				if ( string[0] )
				{
					*p++ = ',';
				}
			}
		}
	}

	if ( (int)strlen( buf )+2 < bytes ) 
		*p++ = '|';
	*p++ = 0x0;
	//printf( "Got a EPG :%s \n", buf );

	if ( pParser->epg_dumper )
		ret = pParser->epg_dumper( pParser->epg_context, bytes, buf );

	SAGETV_FREE( buf );
	return ret;
	
}

void RegisterTSID( void* context, unsigned short tsid )
{
	SI_PARSER* pParser = (SI_PARSER*)context;
	int i;
	if ( pParser == NULL )
		return;
	for ( i=0; i<MAX_TSID_NUM; i++ )
	{
		if ( pParser->tsid_tbl[i] == tsid )
			return ;
		if ( pParser->tsid_tbl[i] == 0 )
		{
			pParser->tsid_tbl[i] = tsid;
			return ;
		}
	}
	return ;
}

bool CheckTSIDVaild( SI_PARSER* pParser, unsigned short tsid )
{
	int i;
	for ( i=0; i<MAX_TSID_NUM && pParser->tsid_tbl[i]; i++ )
	{
		if ( pParser->tsid_tbl[i] == tsid )
			return true;
	}
	return false;
}

//int test_ct = 0;
int SI_dumper( void* context, unsigned short pid, TS_HEADER* ts_header, char* data, int bytes )
{
	SI_PARSER* pParser = (SI_PARSER*)context;
	SECTION_HEADER section_header;

//pParser->SIct++;
	if ( pParser == NULL ) 
		return 0;

	if ( pid == 0x1fff ) //nulll packet
		return 0;

	if ( pParser->stream_type == 0 )
	{
		//guess stream type ATSC DVB
		if ( IsDVBPacket(pParser,pid)  )  pParser->DVB_count++;
		if ( IsATSCPacket(pParser,pid) )  pParser->ATSC_count++;
	
		if ( pParser->DVB_count > pParser->ATSC_count + 4 )
			pParser->stream_type = DVB_STREAM_TYPE;
		if ( pParser->DVB_count + 4 < pParser->ATSC_count )
			pParser->stream_type = ATSC_STREAM_TYPE;
		//flog(( "native.log", "stream type is not specified, guess :%d\n", pParser->stream_type ));
	}

	//DVB stream
	if ( pParser->stream_type == DVB_STREAM_TYPE )
	{
		if (  pid == 0x0010 ) //DVB NIT packets
		{   //pParser->NITct++;
			if ( !CheckContinuity( ts_header, &pParser->NITCountinuty ) )
			{
				//return 0;
			}

			if ( !UnpackSectionData( ts_header->start, &pParser->NITSection, (unsigned char*)data, bytes ) )
				return 0;

			UnpackSectionHeader( &section_header, &pParser->NITSection );
			if ( section_header.TableId == 0x40  || section_header.TableId == 0x41 )
			{
				UnpackNIT( pParser, &section_header, (unsigned char*)pParser->NITSection.data ); 
				return 1;
			}

		} else
		if ( pid == 0x0011 ) //DVB SDT packets
		{   //pParser->SDTct++;
			if ( !CheckContinuity( ts_header, &pParser->SDTCountinuty ) )
			{
				//pParser->SDTct3++;
				//return 0;  DVB-S2
			}

			if ( !UnpackSectionData( ts_header->start, &pParser->SDTSection, (unsigned char*)data, bytes ) )
			{
				//pParser->SDTct4++;
				return 0;
			}

			UnpackSectionHeader( &section_header, &pParser->SDTSection );
			if ( section_header.TableId == 0x42 || section_header.TableId == 0x46 )
			{
				UnpackSDT( pParser, &section_header, (unsigned char*)pParser->SDTSection.data ); 
				return 1;
			} else
			{
				//pParser->SDTct2++;
				return 0;
			}
		}
		if ( pid == 0x0012 )
		{

			if ( !CheckContinuity( ts_header, &pParser->DEITCountinuty ) )
				return 0;

			if ( !UnpackSectionData( ts_header->start, &pParser->DEITSection, (unsigned char*)data, bytes ) )
				return 0;

			UnpackSectionHeader( &section_header, &pParser->DEITSection );

			//DVB EIT  packets
			//if ( section_header.TableId == 0x4e || section_header.TableId == 0x4f 
			//	  || section_header.TableId == 0x5f || section_header.TableId == 0x6f )
			if ( section_header.TableId >= 0x4e && section_header.TableId <= 0x6f )
			{
				UnpackDVBEIT( pParser, &section_header, (unsigned char*)pParser->DEITSection.data );
			}

		} 
	} else  //ATSC stream
	{
		if ( pid == 0x1ffb ) //ATSC packets
		{
			if ( !CheckContinuity( ts_header, &pParser->PSIPCountinuty ) )
				return 0;

			if ( !UnpackSectionData( ts_header->start, &pParser->PSIPSection, (unsigned char*)data, bytes ) )
				return 0;

			UnpackSectionHeader( &section_header, &pParser->PSIPSection );
			switch ( section_header.TableId ) {
			case 0xc5:  //STT
				break;
			case 0xc7:  //MGT
				UnpackMGT( pParser, &section_header, (unsigned char*)pParser->PSIPSection.data );
				break;

			case 0xc8:  //TVCT
				UnpackVCT( pParser, &section_header, (unsigned char*)pParser->PSIPSection.data );
				pParser->sub_stream_type = 1; //terrestrial
				break;

			case 0xc9:  //CVCT
				UnpackVCT( pParser, &section_header, (unsigned char*)pParser->PSIPSection.data );
				pParser->sub_stream_type = 2; //cable
				break;

			case 0xca: //RTT
				UnpackRTT( pParser, &section_header, (unsigned char*)pParser->PSIPSection.data );
				break;

			}

			return 1;
		} else
		{
			int i;
			for ( i = 0; i<pParser->MGTNum; i++ )
			{
				if ( pParser->mgt[i].pid == pid )
				{
					if (  pParser->mgt[i].type == 0 ) 
					{
						return ProcessVCT( pParser, ts_header, data, bytes );
					}
					else
					if (  pParser->mgt[i].type >= 0x100 &&  pParser->mgt[i].type <= 0x17f ) 
					{
						//EIT0 (0x100); EIT1 (0x101)
						return ProcessEIT(  pParser, ts_header, pParser->mgt[i].type-0x100, data, bytes );
					}
					else
					if (  pParser->mgt[i].type >= 0x200 &&  pParser->mgt[i].type <= 0x27f ) 
					{
						//ETT0 (0x100); ETT1 (0x101) //event ETT
						return ProcessETT(  pParser, ts_header, pParser->mgt[i].type-0x200, data, bytes );
					
					} else
					if (  pParser->mgt[i].type == 4 ) 
					{
						//channel ETT
						return ProcessETT(  pParser, ts_header,0x180, data, bytes );
					} else
					if (  pParser->mgt[i].type >= 0x300 &&  pParser->mgt[i].type <= 0x37f ) 
					{
						return ProcessRTT( pParser, ts_header, data, bytes );
					}

				}
			}
		}
	}
	return 0;
}

static int ProcessEIT( SI_PARSER* pParser, TS_HEADER* ts_header, int type, char* data, int bytes )
{
	SECTION_HEADER section_header;
	if ( type >= MAX_EIT_NUM || type < 0  )
		return 0;

	if ( !CheckContinuity( ts_header, &pParser->EITCountinuty[type] ) )
		return 0;

	if ( !UnpackSectionData( ts_header->start, &pParser->EITSection[type], (unsigned char*)data, bytes ) )
		return 0;

	UnpackSectionHeader( &section_header, &pParser->EITSection[type] );
	if ( section_header.TableId != 0xcb ) 
		return 0;

	UnpackEIT( pParser, &section_header, type, (unsigned char*)pParser->EITSection[type].data, pParser->EITSection[type].total_bytes );

	return 1;
}

static int ProcessETT( SI_PARSER* pParser, TS_HEADER* ts_header, int type, char* data, int bytes )
{
	SECTION_HEADER section_header;
	if ( type >= MAX_EIT_NUM+1 || type < 0  )
		return 0;

	if ( !CheckContinuity( ts_header, &pParser->ETTCountinuty[type] ) )
		return 0;

	if ( !UnpackSectionData( ts_header->start, &pParser->ETTSection[type], (unsigned char*)data, bytes ) )
		return 0;

	UnpackSectionHeader( &section_header, &pParser->ETTSection[type] );
	if ( section_header.TableId != 0xcc ) 
		return 0;

	UnpackETT( pParser, &section_header, type, (unsigned char*)pParser->ETTSection[type].data, pParser->ETTSection[type].total_bytes  );

	return 1;
}


static int ProcessVCT( SI_PARSER* pParser, TS_HEADER* ts_header, char* data, int bytes )
{
	SECTION_HEADER section_header;
	if ( !CheckContinuity( ts_header, &pParser->VCTCountinuty ) )
		return 0;

	if ( !UnpackSectionData( ts_header->start, &pParser->VCTSection, (unsigned char*)data, bytes ) )
		return 0;

	UnpackSectionHeader( &section_header, &pParser->VCTSection );
	if ( section_header.TableId != 0xc7 ) 
		return 0;

	UnpackVCT( pParser, &section_header, (unsigned char*)pParser->VCTSection.data );

	return 1;
}

static int ProcessRTT( SI_PARSER* pParser, TS_HEADER* ts_header, char* data, int bytes )
{
	SECTION_HEADER section_header;
	if ( !CheckContinuity( ts_header, &pParser->RTTCountinuty ) )
		return 0;

	if ( !UnpackSectionData( ts_header->start, &pParser->RTTSection, (unsigned char*)data, bytes ) )
		return 0;

	UnpackSectionHeader( &section_header, &pParser->RTTSection );
	if ( section_header.TableId != 0xca ) 
		return 0;

	UnpackRTT( pParser, &section_header, (unsigned char*)pParser->RTTSection.data );

	return 1;

}

static void UnpackMGT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData )
{
	int i;
	unsigned char* p;
	int  desc_bytes;
	pData += SECTION_OFFSET;
	pParser->MGTProtocal = pData[0];
	pParser->MGTNum = pData[1]<<8 | pData[2];
	pParser->MGTNum = _MIN( pParser->MGTNum, MAX_MGT_TBL );
	p = &pData[3];
	for ( i = 0; i<pParser->MGTNum; i++ )
	{
		pParser->mgt[i].type = (p[0] << 8) | p[1];
		pParser->mgt[i].pid  = ((p[2]&0x1f)<<8) | p[3];
		pParser->mgt[i].tbl_size = (p[5]<<24) | (p[6]<<16) | (p[7]<<8) | p[8];
		desc_bytes = (( p[9] & 0x0F ) << 8 ) | p[10];
		p += 11 + desc_bytes;
	}
	pParser->MGTSectionCount++;
}

static void UnpackVCT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData )
{
	int i;
	unsigned char* p;
	bool hidden;
	VCT* pVCT;
	int  desc_bytes;
	pData += SECTION_OFFSET;
	pParser->VCTProtocal = pData[0];
	pParser->VCTNum =   pData[1]; // MAX_VCT_TBL);
	pParser->VCTTSID = pSectionHeader->ProgramNumber;
	
	pVCT = pParser->vct;
	p = &pData[2];
	for ( i = 0; i<pParser->VCTNum; i++ )
	{
		memcpy( pParser->vct[i].Name, p, 7*2 ); pParser->vct[i].Name[7*2] = 0x0;
		pParser->vct[i].major_num = ((p[14]&0x0f) << 6) | ( p[15] >> 2 );
		pParser->vct[i].minor_num = ((p[15] &0x03) << 8) | p[16];
		pParser->vct[i].modulation = p[17];
		pParser->vct[i].freq = ( p[18]<<24 ) | (p[19]<<16) | ( p[20] << 8 ) | p[21];
		pParser->vct[i].TSID = (p[22]<<8) | p[23];
		pParser->vct[i].program_id = (p[24]<<8) | p[25];
		hidden = (p[26] & 0x10) != 0;
		pParser->vct[i].service_type = (p[27] & 0x3f);
		pParser->vct[i].source_id = (p[28]<<8) | p[29];
		desc_bytes = (( p[30] & 0x03 ) << 8 ) | p[31];
		//skip descpt
		p += 32 + desc_bytes;

		//check valid channel: no hidden, in program is in PAT, ATSC digitalTV full service
		if ( !hidden && pParser->vct[i].program_id && pParser->vct[i].service_type == 2  )
			pParser->vct[i].flag = 1;
		//skip analog channel
		if ( pParser->vct[i].modulation == 0x01 )
			pParser->vct[i].flag = 0;

	}

	desc_bytes = (( p[0] & 0x03 ) << 8 ) | p[1];
	//skip descpt
	p += desc_bytes;
	pParser->VCTSectionCount++;

}
static int find_eit_cell( SI_PARSER* pParser, int type, int source_id, int event_id )
{
	int i, oldest=0;
	unsigned long max_stample=0, min_stample = 0xffffffff;

	//oldest eit cell
	for ( i = 0; i<MAX_EPG_NUM_PER_CH; i++ )
	{
		if ( pParser->eit[type][i].stample > max_stample )
			max_stample = pParser->eit[type][i].stample; 

		if ( pParser->eit[type][i].stample < min_stample )
		{
			min_stample = pParser->eit[type][i].stample; 
			oldest = i;
		}
	}

	for ( i = 0; i<MAX_EPG_NUM_PER_CH; i++ )
	{
		if ( pParser->eit[type][i].source_id == source_id && 
			 pParser->eit[type][i].event_id  == event_id )
		{
		     oldest = i; 
			 break;
		}
	}

	//round back
	if ( max_stample == 0xffffffff )
	{
		for ( i = 0; i<MAX_EPG_NUM_PER_CH; i++ )
			pParser->eit[type][i].stample -= min_stample;
		max_stample -= min_stample;
		min_stample = 0;
	}

	//update time stampe
	pParser->eit[type][oldest].stample = max_stample+1; 
	return oldest;
}

static int UnpackEIT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, int type, unsigned char* pData, int Bytes )
{
	int i, j, len ;
	unsigned char* p;
	unsigned char* genre_p;
	int  num_event;
	unsigned short source_id;
	unsigned short desc_length;

	if ( pParser->epg_dumper == NULL ) //parse epg is not enabled
		return 0;

	len = Bytes;
	source_id = (pData[3]<<8)|pData[4];
	p = pData + SECTION_OFFSET;
	num_event = p[1];
	p+=2;
	len = 0;
	for ( j = 0; j<num_event; j++ )
	{
		DESCRIPTOR Desc;

		unsigned short event_id = ( (p[0] & 0x3f) << 8 ) | p[1];
		i = find_eit_cell( pParser, type, source_id, event_id );

		pParser->eit[type][i].source_id = source_id;
		pParser->eit[type][i].event_id  = event_id; 
		pParser->eit[type][i].start_time = ( p[2] << 24) | ( p[3]<<16 ) | ( p[4]<<8 ) | p[5];
		pParser->eit[type][i].etm_loc = (p[6]&30)>>4;
		pParser->eit[type][i].during_length = ((p[6]&0x0f)<<16) | (p[7]<<8) | p[8];
		pParser->eit[type][i].title_length = p[9];
		{
			MSS* mss = NULL;
			UnpackMultiString( pParser, &mss , p+10, pParser->eit[type][i].title_length );
			if ( !EquMultiString( pParser, pParser->eit[type][i].title, mss ) )
			{
				FreeMultiString( pParser, pParser->eit[type][i].title );
				pParser->eit[type][i].title = mss;
				
				FreeMultiString( pParser, pParser->eit[type][i].program );
				pParser->eit[type][i].program = NULL;
				
				pParser->eit[type][i].need_updated = true;

			} else
			{
				FreeMultiString( pParser, mss ); mss = NULL;
				pParser->eit[type][i].need_updated = false;
			}
		}

		//skip descriptor content_advisory_descriptor(), caption_service_descriptor(), AC-3 audio_stream_descriptor()
		p   += 10 + pParser->eit[type][i].title_length;
		len += 10 + pParser->eit[type][i].title_length;
		if ( len > Bytes )
		{
			printf( "error EIT packet\n" );
			break;
		}
				
		desc_length = ( (p[0]&0x0f)<<8 )|p[1]; 
		if ( ( p[0]& 0xf0 ) != 0xf0 )
		{
			printf( "error EIT packet\n" );
			break;
		}

		p += 2;

		if ( pParser->eit[type][i].cad != NULL )
		{
			free_content_advisory( pParser, pParser->eit[type][i].cad );
			pParser->eit[type][i].cad = NULL;
		}

		//unpack secriptor
		if ( TotalDescriptor( pParser, (char*)p, desc_length, ATSC_CONTENT_ADVISORY ) )
		{
			DescriptorParser( pParser, (char*)p, desc_length, ATSC_CONTENT_ADVISORY, &Desc, NULL );
			if ( Desc.tag == ATSC_CONTENT_ADVISORY )
			{
				pParser->eit[type][i].cad = (CAD*)Desc.data;
			}
		}
		if ( ( genre_p = SeekDescriptor( pParser, (char*)p, desc_length, ATSC_GENRE ) )!= NULL )
		{
			UnpackGenre( pParser, pParser->eit[type][i].genre, p, desc_length );
		}

		//skip descriptors
		p   +=  desc_length;
		len += desc_length;;
		if ( len > Bytes )
		{
			printf( "error EIT packet\n" );
			break;
		}

		//if there is no ETT, dump EIT only here
		if ( pParser->eit[type][i].etm_loc == 0x0 && pParser->eit[type][i].need_updated )
			EPGNotifyATSC(pParser, &pParser->eit[type][i] );
	}

	pParser->EITSectionCount++;
	return 1;
}

static void FreeEITData( SI_PARSER* pParser )
{
	int i, j;
	for ( i = 0; i<MAX_EIT_NUM; i++ )
	{
		for ( j = 0; j<MAX_EPG_NUM_PER_CH; j++ )
		{
			FreeMultiString( pParser, pParser->eit[i][j].title ); 
			pParser->eit[i][j].title = NULL;
			FreeMultiString( pParser, pParser->eit[i][j].program ); 
			pParser->eit[i][j].program = NULL;
			FreeMultiString( pParser, pParser->eit[i][j].channel_name );
			pParser->eit[i][j].channel_name = NULL;
			if ( pParser->eit[i][j].cad != NULL )
			{
				free_content_advisory( pParser, pParser->eit[i][j].cad );
				pParser->eit[i][j].cad = NULL;
			}
		}
	}
}


static int UnpackETT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, int type, unsigned char* pData, int Bytes )
{
	unsigned char* p;
	ETT	 ett;
	int found = 0;
	int i, j;


	ett.ett_ext_id = (pData[3]<<8)|pData[4];
	p = pData + SECTION_OFFSET;
	p++;
	ett.source_id = (p[0]<<8)|p[1];
	ett.event_id = (p[2]<<8)|p[3];
	if ( ( ett.event_id & 0x3 ) == 0x2 )
		ett.event_id >>= 2;

	for ( j = 0; j<MAX_EIT_NUM; j++ )
		for ( i = 0; i<MAX_EPG_NUM_PER_CH; i++ )
		{
			if ( pParser->eit[j][i].event_id == ett.event_id && pParser->eit[j][i].source_id == ett.source_id )
			{
				MSS *mss = NULL;
				UnpackMultiString( pParser, &mss , p+4, Bytes-4-SECTION_OFFSET );
				if ( !EquMultiString( pParser, pParser->eit[j][i].program, mss ) )
				{	//program is updated
					FreeMultiString( pParser, pParser->eit[j][i].program );
					pParser->eit[j][i].program = mss;
					EPGNotifyATSC( pParser, &pParser->eit[j][i]);
				} else
				{
					FreeMultiString( pParser, mss ); mss = NULL;
				}
				found++;	 
			}
		}

	pParser->ETTSectionCount++;
	return 1;
}

static void FreeETTData( SI_PARSER* pParser )
{
	//nothing to free
}

static bool EquMultiString( SI_PARSER* pParser, MSS* mss1, MSS* mss2 )
{
	int i,j;
	if ( mss1 == NULL && mss2 == NULL ) return true;
	if ( mss1 == NULL || mss2 == NULL ) return false;

	if ( mss1->num_string != mss2->num_string )
		return false;


	if ( ( mss1->num_string> 0 && memcmp( mss1->_tag, "STRG", 4 ) ) ||
	     ( mss2->num_string> 0 && memcmp( mss2->_tag, "STRG", 4 ) ) )
	{
		char buf[128];
		sprintf( buf, "illeag mutil string usage. mss1:%s mss2:%s\n", mss1->_tag, mss2->_tag );
		_log_error( buf );
		return false;
	}

	if ( mss1->num_string > 0 && mss1->string_list != NULL && mss2->string_list != NULL )
	{
		for ( i = 0; i<mss1->num_string; i++ )
		{
			if ( mss1->string_list[i].num_segment != mss2->string_list[i].num_segment )
				return false;

			for ( j = 0; j<mss1->string_list[i].num_segment; j++ )
			{
				if ( mss1->string_list[i].seg_list != NULL && mss2->string_list[i].seg_list  != NULL )
				{
					if ( mss1->string_list[i].seg_list->byte_length != mss2->string_list[i].seg_list->byte_length )
						return false;
					if ( memcmp( mss1->string_list[i].seg_list->mesg,
						         mss2->string_list[i].seg_list->mesg, 
								 mss1->string_list[i].seg_list->byte_length ) )
						return false;
				} 
			}

		}
	} else
		return false;

	return true;
}

static void UnpackMultiString( SI_PARSER* pParser, MSS** pMss, unsigned char* pData, int Length )
{
	int i, j, bytes;
	MSS *mss;
	unsigned char*p;
	if ( pMss == NULL ) return;
	mss = SAGETV_MALLOC( sizeof(MSS) );
	*pMss = mss;

	p = pData;
	mss->num_string = p[0];
	if ( mss->num_string > 0 )
		mss->string_list = SAGETV_MALLOC( sizeof(SEGS)*mss->num_string );
	memcpy ( mss->_tag, "STRG", 4 ); //for safe check

	bytes = 0;
	p++; 
	for ( i = 0; i<mss->num_string; i++ )
	{
		mss->string_list[i].lanuage_code[0] = p[0];
		mss->string_list[i].lanuage_code[1] = p[1];
		mss->string_list[i].lanuage_code[2] = p[2];
		mss->string_list[i].lanuage_code[3] = 0;
		mss->string_list[i].num_segment = p[3];

		if ( mss->string_list[i].num_segment > 0  )
			mss->string_list[i].seg_list = SAGETV_MALLOC( sizeof(SEG)*mss->string_list[i].num_segment );
		else
		{
		    mss->string_list[i].seg_list = NULL;
			continue;
		}
		p += 4;
		for ( j = 0; j<mss->string_list[i].num_segment; j++ )
		{
			mss->string_list[i].seg_list[j].compression_type = p[0];
			mss->string_list[i].seg_list[j].mode = p[1];
			mss->string_list[i].seg_list[j].byte_length = p[2];
			if (  mss->string_list[i].seg_list[j].byte_length )
			{
				mss->string_list[i].seg_list[j].mesg = (unsigned char*)SAGETV_MALLOC( mss->string_list[i].seg_list[j].byte_length );
				memcpy( mss->string_list[i].seg_list[j].mesg, p+3, mss->string_list[i].seg_list[j].byte_length );
				p += 3+mss->string_list[i].seg_list[j].byte_length;
				bytes += 3+mss->string_list[i].seg_list[j].byte_length;
				if ( bytes > Length )
				{
					printf( "ETT multi string data wrong\n" );
					return;
				}
			} else
			{
				mss->string_list[i].seg_list[j].mesg = NULL;
			}
		}
	}
}

static void FreeMultiString( SI_PARSER* pParser, MSS* mss )
{
	int i, j;
	if ( mss == NULL ) return;

	if ( mss->num_string> 0 && memcmp( mss->_tag, "STRG", 4 ) )
	{
		char buf[128];
		sprintf( buf, "illeag mutil string usage %s\n", mss->_tag );
		_log_error( buf );
		return;
	}

	if ( mss->num_string > 0 && mss->string_list != NULL )
	{
		for ( i = 0; i<mss->num_string; i++ )
		{
			if ( mss->string_list[i].num_segment > 0 && mss->string_list[i].seg_list != NULL )
			{
				for ( j = 0; j<mss->string_list[i].num_segment; j++ )
				{
					if ( mss->string_list[i].seg_list[j].mesg != NULL )
					{
						SAGETV_FREE( mss->string_list[i].seg_list[j].mesg );
						mss->string_list[i].seg_list[j].mesg = NULL;
					}
				}
			}
			if ( mss->string_list[i].seg_list )
			{
				SAGETV_FREE( mss->string_list[i].seg_list );
				mss->string_list[i].seg_list = NULL;
			}

			SAGETV_FREE( mss->string_list );
			mss->num_string = 0;
			mss->string_list = NULL;
		}
	}
	SAGETV_FREE( mss );
}

static int UnpackGenre( SI_PARSER* pParser, unsigned char* genre, unsigned char* pData, int Length  )
{
	unsigned int i, n;
	memset( genre, 0, MAX_GENRE_NUM );
	n = pData[0] & 0x1f;
	for ( i = 0; i<n && (int)i<Length-1; i++ )
	{
		genre[i] = pData[1+i];
	}
	return i;
}

// static void  FreeGenre( SI_PARSER* pParser, unsigned char* genre )
// {
// 	//nothing to free
// }

static int UnpackRTT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData )
{
	int i;
	unsigned char* p;
	unsigned char rating_region;
	RTT* pRTT;
	MSS  *mss=NULL;
	int  length, num;
	rating_region = pData[4];
	for ( i = 0; i<MAX_RTT_TBL; i++ )
	{
		if ( pParser->rtt[i].rating_region == rating_region || pParser->rtt[i].rating_region == 0 )
			break;
	}
	
	//find RTT instnace or get a empty instance table
	if ( i >= MAX_RTT_TBL )  //RTT tble  is full, drop it
	{
		printf( "too many  RTT table instance in stream, I drop it" );
		return 0;
	}

	pRTT = &pParser->rtt[i];
	pRTT->rating_region = rating_region;
	p = pData + SECTION_OFFSET +1;
	length = *p;
	UnpackMultiString( pParser, &mss, p+1, length );
	if ( mss->string_list != NULL && mss->string_list[0].seg_list != NULL  )
		memcpy( pRTT->region_name, 
				mss->string_list[0].seg_list[0].mesg, 
				_MIN( mss->string_list[0].seg_list[0].byte_length, sizeof(pRTT->region_name)  )  );
	FreeMultiString( pParser, mss ); mss = NULL;
	
	p += length + 1;
	num = *p;

	if ( num == 0 )
		return 0;

	//re alloc table if table is bigger than current one.
	if ( num > pRTT->dimension_num && pRTT->rating )
	{
		SAGETV_FREE( pRTT->rating );
		pRTT->rating = NULL;
		pRTT->dimension_num = 0;
	}
	if ( pRTT->rating == NULL )
	{
		pRTT->rating = SAGETV_MALLOC( num * sizeof(RATING) );
		pRTT->dimension_num = num;
	}

	p++;
	memset( pRTT->rating,  0,   sizeof(RATING) * pRTT->dimension_num );
	for ( i = 0; i<num; i++ )
	{
		int j;
		length = *p;
		UnpackMultiString( pParser, &mss, p+1, length );
		if ( mss->string_list != NULL && mss->string_list[0].seg_list != NULL  )
			memcpy( pRTT->rating[i].dimension_name, 
				    mss->string_list[0].seg_list[0].mesg, 
					_MIN( mss->string_list[0].seg_list[0].byte_length, sizeof(pRTT->rating[i].dimension_name) ) );
		FreeMultiString( pParser, mss ); mss = NULL;
		p += 1 + length;
		pRTT->rating[i].graduate_scale = ( *p & 0x10 ) != 0;
		pRTT->rating[i].rating_num = *p & 0x0f;
		p++;
		for ( j = 0; j<pRTT->rating[i].rating_num; j++ )
		{
			length = *p;
			UnpackMultiString( pParser, &mss, p+1, length ); 
			if ( mss->string_list != NULL && mss->string_list[0].seg_list != NULL  )
					memcpy( pRTT->rating[i].abbrev_name[j], 
							mss->string_list[0].seg_list[0].mesg, 
							_MIN( mss->string_list[0].seg_list[0].byte_length, 8 )  );
			FreeMultiString( pParser, mss ); mss = NULL;
			p += 1+length;
			length = *p;

			UnpackMultiString( pParser, &mss, p+1, length ); 
			if ( mss->string_list != NULL && mss->string_list[0].seg_list != NULL  )
				memcpy( pRTT->rating[i].rating_value[j], 
						mss->string_list[0].seg_list[0].mesg, 
						_MIN( mss->string_list[0].seg_list[0].byte_length, 150 )  );
			FreeMultiString( pParser, mss ); mss = NULL;
			p += 1+length;
		}
	}
	
	//skep decsiptor
	FreeMultiString( pParser, mss );
	pParser->RTTSectionCount++;	
	return 1;
}



static void FreeRTTData( SI_PARSER* pParser ) 
{
	int i;
	for ( i = 0; i<MAX_RTT_TBL; i++ )
	{
		pParser->rtt[i].rating_region = 0;
		if ( pParser->rtt[i].rating != NULL )
		{
			SAGETV_FREE( pParser->rtt[i].rating );
			pParser->rtt[i].rating = NULL;
			pParser->rtt[i].dimension_num = 0;
		}
	}
}

static  bool IsDVBPacket( SI_PARSER* pParser, unsigned short pid  )
{
    switch ( pid ) {
    case 0x02:      //TSDT
		pParser->TSDT_count++;
        return true;
    case 0x10:      //NIT,ST 
		pParser->NIT_count++;
        return true;
    case 0x11:      //SDT, BAT, ST 
		pParser->SDT_count++;
        return true;
    case 0x12: 		//EIT, ST, CIT
		pParser->EIT_count++;
        return true;
    case 0x13:		//RST, ST
		pParser->RST_count++;
        return true;
    case 0x14:		//TDT, TOT, ST
		pParser->TDT_count++;
        return true;
    case 0x15:		//network sync
		pParser->MIP_count++;
        return true;
    case 0x16:		//RNT
		pParser->RNT_count++;
        return true;
    case 0x1E:		//DIT
		pParser->DIT_count++;
        return true;
    case 0x1F:		//SIT
		pParser->SIT_count++;
        return true;
    }
    return false;
}

static  bool IsATSCPacket( SI_PARSER* pParser, unsigned short pid  )
{
    switch ( pid ) {
    case 0x1ff7:    //PAT-E
		pParser->PAT_E_count++; 
        return true;
    case 0x1ff8:    //STT-E 
		pParser->STT_E_count++;
        return true;
    case 0x1ffa:    //operational packets
		pParser->OPERA_count++;
        return true;
    case 0x1ffb: 	//PSIP
		pParser->PSIP_count++;
        return true;
    case 0x1ffc:	//SCTE
		pParser->SCTE_count++;
        return true;
    }
    return false;
}

void UnpackSectionHeader( SECTION_HEADER* pSectionHeader, TS_SECTION* pSection )
{
	unsigned char* data;
	data = (unsigned char*)pSection->data;
	pSectionHeader->TableId	    = data[0];
	pSectionHeader->SectionLength = ((data[1] & 0x0f) << 8) | data[2];
	pSectionHeader->ProgramNumber = (data[3] << 8)	+ data[4];
	pSectionHeader->VersionNumber = (data[5]	& 0x3E)	>> 1;
	pSectionHeader->CurrentNextIndicator =	data[5] &	0x01;
	pSectionHeader->SectionNum = data[6];    
	pSectionHeader->LastSectionNum = data[7];
	pSectionHeader->pSection = pSection;
}



static NIT_INF* GetNITInfo( SI_PARSER* pParser, int ONID, int TSID )
{
	int i;
	for ( i = 0; i<pParser->nit.NIT_inf_num; i++ )
	{
		if ( pParser->nit.NIT_inf[i].ONID == ONID && pParser->nit.NIT_inf[i].TSID == TSID )
		{
			return &pParser->nit.NIT_inf[i];
		}
	}
	return NULL;
}

/*
static unsigned short GetServiceType( SI_PARSER* pParser, int ONID, int TSID, int service_id )
{
	int i;
	for ( i = 0; i<pParser->nit.NIT_inf_num; i++ )
	{
		int j;
		if ( pParser->nit.NIT_inf[i].service_list == NULL )
			continue;
		
		for ( j = 0; j<pParser->nit.NIT_inf[i].service_list->service_num; j++ )
		{
			if ( pParser->nit.NIT_inf[i].service_list[j].service->seviceid == service_id )
				return pParser->nit.NIT_inf[i].service_list[j].service->type;
		}
	}
	return -1;
}
*/
DEIT* LookUpDEIT( SI_PARSER* pParser, unsigned short service_id, unsigned short TSID, 
				  unsigned short ONID, unsigned short event_id, time_t start_time )
{
	int i, latest_event;
	time_t latest_start_time;
	for ( i = 0; i<pParser->DEITNum; i++ )
	{
		if ( pParser->deit[i].service_id == service_id &&
			pParser->deit[i].TSID == TSID &&
			pParser->deit[i].ONID == ONID &&
			pParser->deit[i].event_id == event_id &&
			pParser->deit[i].start_time == start_time )
		return &pParser->deit[i];
	}
	if ( i < MAX_DEIT_NUM )
	{
		pParser->DEITNum++;
		return &pParser->deit[i];
	}

	//find a latest start_time deit to use
	latest_start_time = 0; 
	latest_event = MAX_DEIT_NUM-1;
	for ( i=MAX_DEIT_NUM-1; i>=0; i-- )
	{
		if ( pParser->deit[i].start_time > latest_start_time )
		{
			latest_start_time = pParser->deit[i].start_time;
			latest_event = i;;
		}
	}

	return &pParser->deit[latest_event];
}


static void FillSVTInfo( SI_PARSER* pParser, NIT_INF* nit_inf )
{
	int i;
	int onid, tsid;
	if ( nit_inf == NULL )
		return;
	onid = nit_inf->ONID;
	tsid = nit_inf->TSID;
	

	for ( i = 0; i<pParser->SVTNum; i++ )
	{
		if ( onid == pParser->svt[i].ONID && tsid == pParser->svt[i].TSID )
		{
			pParser->svt[i].delivery_type = nit_inf->delivery_type;
			if ( pParser->svt[i].delivery_type == 1 )
				pParser->svt[i].dvb.t = nit_inf->dvb.t;
			else
			if ( pParser->svt[i].delivery_type == 2 )
				pParser->svt[i].dvb.c = nit_inf->dvb.c;
			else
			if ( pParser->svt[i].delivery_type == 2 )
				pParser->svt[i].dvb.s = nit_inf->dvb.s;

			pParser->svt[i].flag |= 0x2;
		}
	}

	//check if onid, tsid is already in check table
	for ( i = 0; i<MAX_DVB_NETOWRK_NUM; i++ )
	{
		if ( pParser->dvb_check[i].ONID == onid && pParser->dvb_check[i].TSID == tsid )
			break;
	}
	//if not in check table, add into it
	if ( i >= MAX_DVB_NETOWRK_NUM && pParser->DVBCheckNum < MAX_DVB_NETOWRK_NUM )
	{
		i = pParser->DVBCheckNum++;
		pParser->dvb_check[i].ONID = onid;
		pParser->dvb_check[i].TSID = tsid;
		pParser->dvb_check[i].flag = 1;
		pParser->dvb_check[i].counter = 0;
	}
}

static int UnpackNIT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData )
{
	int i;
	int  desc_bytes, ts_loop_bytes, bytes, used_bytes;
	unsigned short TSID, ONID;
	DVB_SERVICE_LIST  *service_list;
	TERRESTRIAL_DATA	t;
	CABLE_DATA			c;
	SATELLITE_DATA		s;
			
	pData += SECTION_OFFSET;

	FreeNITData( pParser );

	pParser->nit.network_id = pSectionHeader->ProgramNumber;
	desc_bytes = (( pData[0]&0x0f ) << 8 ) | pData[1];
	pData += 2;

	if ( TotalDescriptor( pParser, (char*)pData, desc_bytes, NETWORK_NAME_TAG ) )
	{
		DESCRIPTOR Desc;
		DescriptorParser( pParser, (char*)pData, desc_bytes, NETWORK_NAME_TAG, &Desc, NULL );
		if ( Desc.tag == NETWORK_NAME_TAG )
			pParser->nit.network_name = (char*)Desc.data;
	}

	pData += desc_bytes;
	ts_loop_bytes = (( pData[0]&0x0f ) << 8 ) | pData[1]; //transport stream loop bytes
	pData += 2;
	used_bytes = 0;
	while ( used_bytes < ts_loop_bytes )
	{
		if ( pParser->nit.NIT_inf_num >= MAX_DVB_NETOWRK_NUM )
			break;

		if ( used_bytes + 6 >= ts_loop_bytes )
			break;

		TSID = ( ((unsigned char)pData[0])<<8 ) | (unsigned char)pData[1];
		ONID = ( ((unsigned char)pData[2])<<8 ) | pData[3];
		desc_bytes = (( (unsigned char)pData[4]&0x0f ) << 8 ) | pData[5];
		if ( desc_bytes == 0 ) 
		{
			TSID = 0;
			ONID = 0;
			break;
		}

		pData += 6;
		used_bytes += 6;
		service_list = NULL;
		if ( TotalDescriptor( pParser, (char*)pData, desc_bytes, SERVICE_LIST_TAG ) )  
		{
			DESCRIPTOR  Desc;
			bytes = DescriptorParser( pParser, (char*)pData, desc_bytes, SERVICE_LIST_TAG, &Desc, NULL );
			if ( Desc.tag == SERVICE_LIST_TAG )
				service_list = (DVB_SERVICE_LIST*)Desc.data;
		}

		if ( pParser->nit.type ==  0 )
		{
			if ( TotalDescriptor( pParser, (char*)pData, desc_bytes, TERRESTRIAL_DELIVERY_TAG ) )
				 pParser->nit.type = 1;
			else
			if ( TotalDescriptor( pParser, (char*)pData, desc_bytes, CABLE_DELIVERY_TAG ) )
				pParser->nit.type = 2;
			else
			if ( TotalDescriptor( pParser, (char*)pData, desc_bytes, SATELLITE_DELIVERY_TAG ) )
				pParser->nit.type = 3;
		}

		if (   pParser->nit.type == 1 )
		{
			memset( &t, 0, sizeof(t) );
			if ( TotalDescriptor( pParser, (char*)pData, desc_bytes, TERRESTRIAL_DELIVERY_TAG ) )
			{
				DESCRIPTOR  Desc;
				bytes = DescriptorParser( pParser,  (char*)pData, desc_bytes, TERRESTRIAL_DELIVERY_TAG, &Desc, NULL );
				if ( Desc.tag == TERRESTRIAL_DELIVERY_TAG )
				{
					t = *(TERRESTRIAL_DATA*)Desc.data;
					free_terrestrial_delivery( pParser,  Desc.data );
				}
			}
		} else
		if ( pParser->nit.type == 2 ) 
		{
			memset( &c, 0, sizeof(c) );
			if ( TotalDescriptor( pParser, (char*)pData, desc_bytes, CABLE_DELIVERY_TAG ) )
			{
				DESCRIPTOR  Desc;
				bytes = DescriptorParser( pParser,  (char*)pData, desc_bytes, CABLE_DELIVERY_TAG, &Desc, NULL );
				if ( Desc.tag == CABLE_DELIVERY_TAG )
				{
					c = *(CABLE_DATA*)Desc.data;
					free_cable_delivery( pParser,  Desc.data );
				}
			}

		} else
		if (  pParser->nit.type == 3 ) 
		{
			memset( &s, 0, sizeof(s) );
			if ( TotalDescriptor( pParser, (char*)pData, desc_bytes, SATELLITE_DELIVERY_TAG ) )
			{
				DESCRIPTOR  Desc;
				bytes = DescriptorParser( pParser, (char*)pData, desc_bytes, SATELLITE_DELIVERY_TAG, &Desc, NULL );
				if ( Desc.tag == SATELLITE_DELIVERY_TAG )
				{
					s = *(SATELLITE_DATA*)Desc.data;
					free_satellite_delivery( pParser,  Desc.data );
				}
			}
		} 

		for ( i = 0; i<pParser->nit.NIT_inf_num; i++ )
			if ( pParser->nit.NIT_inf[i].TSID == TSID && pParser->nit.NIT_inf[i].ONID == ONID )
				break;

		if ( i >= pParser->nit.NIT_inf_num && i < MAX_DVB_NETOWRK_NUM )
		{
			pParser->nit.NIT_inf[i].TSID = TSID;
			pParser->nit.NIT_inf[i].ONID = ONID;
			pParser->nit.NIT_inf[i].service_list = service_list;
			pParser->nit.NIT_inf[i].delivery_type = pParser->nit.type;
			if ( pParser->nit.type == 1 )
				pParser->nit.NIT_inf[i].dvb.t = t;
			else
			if ( pParser->nit.type == 2 )
				pParser->nit.NIT_inf[i].dvb.c = c;
			else
			if ( pParser->nit.type == 3 )
				pParser->nit.NIT_inf[i].dvb.s = s;

			FillSVTInfo( pParser, &pParser->nit.NIT_inf[i] );
			pParser->nit.NIT_inf_num++;
		} else
		{
			free_service_list( pParser, service_list ); 
		}

		used_bytes += desc_bytes;
		pData += desc_bytes;
		i++;
	}
	
	pParser->NITSectionCount++;	
	return pParser->nit.NIT_inf_num;
}

static void FreeNITData( SI_PARSER* pParser )
{
	int i;
	if ( pParser == NULL ) 
		return;

	for ( i=0; i<pParser->nit.NIT_inf_num; i++ )
	{
		free_service_list( pParser, pParser->nit.NIT_inf[i].service_list ); 
		pParser->nit.NIT_inf[i].service_list = NULL;
	}
	free_network_name( pParser, pParser->nit.network_name );
	pParser->nit.network_name = NULL;
	pParser->nit.network_id = 0;
	pParser->nit.NIT_inf_num = 0;

}


static int UnpackSDT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData )
{
	int  total_bytes, bytes, desc_bytes, i, n;
	unsigned char* p;
	SDT			   sdt;
	memset( &sdt, 0, sizeof(sdt) );

	pData += SECTION_OFFSET;

	total_bytes = pSectionHeader->pSection->total_bytes-4;
	if ( total_bytes <= 0 )
		return 0;

	sdt.TSID = pSectionHeader->ProgramNumber;
	sdt.ONID = (((unsigned char)pData[0])<<8 ) | (unsigned char)pData[1];
	sdt.SDT_inf_num = 0;

	//count total service record in section
	bytes = 0;
	p = pData+2+1;
	n = 0;
	while ( bytes < total_bytes )
	{
		if ( ((p[0]<<8)|p[1]) == 0 ) break;
		desc_bytes = (( p[3]&0x0f ) << 8 ) | p[4];
		if ( desc_bytes == 0 || desc_bytes + bytes > total_bytes ) break;
		p     += 5+desc_bytes;
		bytes += 5+desc_bytes;
		n++; 
	}
	if ( n == 0 )
	{
		FreeSDTData( pParser, &sdt );
		return 0;
	}
	sdt.SDT_inf = SAGETV_MALLOC( sizeof(SDT_INF)*n );
	sdt.SDT_inf_num = n;
	bytes = 0; 
	p = pData+2+1;
	for ( i = 0; i<n; i++ )
	{  
		DESCRIPTOR Desc;
		if ( ((p[0]<<8)|p[1]) == 0 ) break;
		sdt.SDT_inf[i].service_id = (p[0]<<8)|p[1];
		sdt.SDT_inf[i].EIT_flag = p[2]&0x03; 
		sdt.SDT_inf[i].flag = p[3]>>5; 
		sdt.SDT_inf[i].CA_flag = (p[3]>>4)&0x01; 
		desc_bytes = (( p[3]&0x0f ) << 8 ) | p[4];
		DescriptorParser( pParser, (char*)p+5, desc_bytes, SERVICE_TAG, &Desc, NULL );
		if ( Desc.tag == SERVICE_TAG )
			sdt.SDT_inf[i].service_data = (SERVICE_DATA*)Desc.data;
		else
			sdt.SDT_inf[i].service_data = NULL;

		p     += 5+desc_bytes;
		bytes += 5+desc_bytes;
	}

	//fill in svt table
	for ( i = 0; i<n; i++ )
	{
		int j;

		//drop those unrelevant SDT table
		if ( !CheckTSIDVaild( pParser, sdt.TSID ) )
		{
			continue;
		}

		//check if it's already in SVT table
		for ( j = 0; j<(int)pParser->SVTNum; j++ )
		{
			if ( sdt.ONID == pParser->svt[j].ONID && sdt.TSID == pParser->svt[j].TSID && 
				 sdt.SDT_inf[i].service_id == pParser->svt[j].service_id )
			{
				break;
			}
		}
		if ( j >= (int)pParser->SVTNum && pParser->SVTNum < MAX_SVT_TBL  )
		{
			j = pParser->SVTNum++;
			pParser->svt[j].flag = 0x00;
			pParser->svt[j].ONID = sdt.ONID;
			pParser->svt[j].TSID = sdt.TSID;
			pParser->svt[j].service_id = sdt.SDT_inf[i].service_id;
		}

		if ( j < MAX_SVT_TBL && sdt.SDT_inf[i].service_data != NULL )
		{
			NIT_INF*  nit_inf;
			strncpy( pParser->svt[j].Name,     sdt.SDT_inf[i].service_data->service_name, sizeof(pParser->svt[j].Name)-1);
			strncpy( pParser->svt[j].Provider, sdt.SDT_inf[i].service_data->provider_name, sizeof(pParser->svt[j].Provider)-1);
			pParser->svt[j].flag = 0x01;
			pParser->svt[j].service_type = sdt.SDT_inf[i].service_data->type;  //1 for digital TV; 2 for radio
			if ( (nit_inf = GetNITInfo( pParser, sdt.ONID, sdt.TSID )) != NULL  )
			{
				pParser->svt[i].delivery_type = nit_inf->delivery_type;
				if ( pParser->svt[i].delivery_type == 1 )
					pParser->svt[i].dvb.t = nit_inf->dvb.t;
				else
				if ( pParser->svt[i].delivery_type == 2 )
					pParser->svt[i].dvb.c = nit_inf->dvb.c;
				else
				if ( pParser->svt[i].delivery_type == 2 )
					pParser->svt[i].dvb.s = nit_inf->dvb.s;

				pParser->svt[j].flag = 0x03;
			}
		}

		for ( j = 0; j<MAX_DVB_NETOWRK_NUM; j++ )
		{
			if ( pParser->dvb_check[j].ONID == sdt.ONID&& pParser->dvb_check[j].TSID == sdt.TSID )
			{
				pParser->dvb_check[j].counter++;
				break;
			}
		}

	}

	pParser->SDTSectionCount++;

	FreeSDTData( pParser, &sdt );
	return 1;
}


static void FreeSDTData( SI_PARSER* pParser, SDT* sdt )
{
	int i;
	if ( sdt->SDT_inf == NULL ) 
		return;
	
	for ( i = 0; i<sdt->SDT_inf_num; i++ )
	{
		free_service( pParser, sdt->SDT_inf[i].service_data );
		sdt->SDT_inf[i].service_data = NULL;
	}

	SAGETV_FREE( sdt->SDT_inf );
	sdt->SDT_inf = NULL;
}


static int UnpackDVBEIT( SI_PARSER* pParser, SECTION_HEADER* pSectionHeader, unsigned char* pData )
{
	unsigned char* p;
	unsigned short service_id, TSID, ONID, event_id;
	time_t        start_time;
	unsigned long duration_length;
	int total_bytes, bytes;
	int n;

	service_id = ( pData[3] << 8 ) | pData[4];
	TSID    = ( pData[8] << 8 ) | pData[9];
	ONID    = ( pData[10] << 8 ) | pData[11];	

	total_bytes = pSectionHeader->pSection->total_bytes-4;
	if ( total_bytes <= 0 )
		return 0;

	p = pData + 14;
	bytes = 14;
	while ( bytes < total_bytes )
	{
		DESCRIPTOR Desc;

		event_id = ( p[0]<< 8 )|p[1];

		start_time = MJD2Locatime( p+2 );
		duration_length = BCDTime( p+7 );
		n = (( p[10]& 0x0f )<<8 )|p[11];
		if ( n == 0 ||  bytes + n >  total_bytes ) 
			break;

		//0x:4d short event; 0x4e:extended event; 0x50:component desc; 0x54:content desc; 0x55:parent rating.
		bytes += 12;	p += 12;   
		if ( bytes > total_bytes ) break;

		if ( pParser->epg_dumper != NULL ) //parse epg is enabled
		{
			S_EVENT* s_event;
			E_EVENT* e_event;
			RATING_TBL *rating_tbl = NULL;
			unsigned char* content_desc=NULL; //DVB genre code
			DEIT* deit = NULL;
			char *p_ext;
			int   p_ext_bytes, bytes_used;
			bool updated = false;

			DescriptorParser( pParser, (char*)p, n, DVB_PRENTAL_RATING_TAG, &Desc, NULL );

			if ( Desc.tag == DVB_PRENTAL_RATING_TAG )
			{
				rating_tbl = (RATING_TBL*)Desc.data;
			}

			DescriptorParser( pParser, (char*)p, n, DVB_CONTENT_DESC_TAG, &Desc, NULL );
			if ( Desc.tag == DVB_CONTENT_DESC_TAG )
			{
				content_desc = (unsigned char*)Desc.data; //DVB genre code
			}

			DescriptorParser( pParser, (char*)p, n, SHORT_EVENT_TAG, &Desc, NULL );
			if ( Desc.tag == SHORT_EVENT_TAG )
			{
				deit = LookUpDEIT( pParser, service_id, TSID, ONID, event_id, start_time );
				deit->service_id = service_id; 
				deit->TSID    = TSID;
				deit->ONID    = ONID;
				deit->event_id = event_id;
				deit->start_time = start_time;
				deit->during_length = duration_length;

				free_prental_rating( pParser, deit->rating );
				free_content_desc( pParser, deit->content_desc );
				deit->rating = rating_tbl;
				deit->content_desc=content_desc;
				rating_tbl = NULL;
				content_desc= NULL;

				s_event = (S_EVENT*)Desc.data;

				//if ( short_event_updated( deit->event, s_event ) )
				if ( !EQU_GUID( deit->event_guid, s_event->guid ) )
				{
					free_short_event( pParser, deit->event );
					deit->event = s_event;
					deit->event_guid = s_event->guid;
					updated = true;
				} else
				{
					free_short_event( pParser, s_event );
				}
				
			} else
			{
				//no short event;
			}

			p_ext = (char*)p;
			p_ext_bytes = 0;
			bytes_used = 0;
			while ( bytes_used < n )
			{
				p_ext_bytes = DescriptorParser( pParser, p_ext, n-bytes_used,  EXTENDED_EVENT_TAG, &Desc, &p_ext );
				p_ext += p_ext_bytes;
				bytes_used += p_ext_bytes;
				if ( Desc.tag ==  EXTENDED_EVENT_TAG )
				{
					int ext_event_serial_num, ext_event_last_serial;

					if ( deit == NULL )
					{
						deit = LookUpDEIT( pParser, service_id, TSID, ONID, event_id, start_time );
						deit->service_id = service_id; 
						deit->TSID    = TSID;
						deit->ONID    = ONID;
						deit->event_id = event_id;
						deit->start_time = start_time;
						deit->during_length = duration_length;
					}

					e_event = (E_EVENT*)Desc.data;
					ext_event_serial_num = e_event->item_no;
					ext_event_last_serial = e_event->item_last_no;
					if ( extended_event_updated( deit->ext_event[ext_event_serial_num], e_event ) )
					{
						free_extended_event( pParser, deit->ext_event[ext_event_serial_num] );
						deit->ext_event[ext_event_serial_num] = e_event;
						if ( ext_event_serial_num  == ext_event_last_serial )
						{
							int i;
							//check all extended events in serials are ready
							for ( i = 0; i<ext_event_last_serial; i++ )
								if ( deit->ext_event[i] == NULL || 
									deit->ext_event[i]->item_last_no != ext_event_last_serial  ) 
									break;

							if ( i >= ext_event_last_serial )
							{
								updated = deit->ext_event_flag = true;
							} 

						} else
							deit->ext_event_flag = false;

					} else
						free_extended_event( pParser, e_event );
				} else
					break;
					
			}

			if ( updated ) //ZQ
			{
				int ret;
				ret = EPGNotifyDVB( pParser, deit );
				free_short_event( pParser, deit->event );
				deit->event = NULL;
				if ( ret != 0 ) //if post failed, re post this enevnt later when it comes again  
					memset( &deit->event_guid, 0, sizeof(deit->event_guid) );

			}

			free_prental_rating( pParser, rating_tbl );
			free_content_desc( pParser, content_desc );

		}

		p     += n;
		bytes += n;
	}


	return 1;
}

static void FreeDVBEIT( SI_PARSER* pParser )
{
	int i, j;
	for ( i = 0; i < MAX_DEIT_NUM; i++ )
	{
		free_short_event( pParser, pParser->deit[i].event );
		pParser->deit[i].event = NULL;
		free_prental_rating( pParser, pParser->deit[i].rating );
		free_content_desc( pParser, pParser->deit[i].content_desc );
		pParser->deit[i].rating = NULL;
		pParser->deit[i].content_desc = NULL;
		for ( j = 0; j <16; j++ )
		{
			free_extended_event( pParser, pParser->deit[i].ext_event[j] );
			pParser->deit[i].ext_event[j] = NULL;
		}
	}
}

static time_t MJD2Locatime( unsigned char* pMJD )
{
	unsigned long mjd;
	struct tm lt={0};
	unsigned int y,m,d,wd;

	mjd = ( pMJD[0]<<8 ) | (pMJD[1]);

	y = (unsigned int)((mjd - 15078.2)/365.25);
	m = (unsigned int)((mjd - 14956.1-(unsigned int)(y*365.25))/30.6001);
	d = (unsigned int)(mjd)-14956-(unsigned int)(y*365.25)-(unsigned int)(m*30.6001);
	wd = (unsigned int)((mjd+2)%7+1);
	if ( m == 14 || m == 15 )
	{
		y++;
		m = m-1-12;
	} else
		m = m-1;

	lt.tm_year = y;
	lt.tm_mon = m-1;
	lt.tm_mday = d;
	lt.tm_wday = wd;

	lt.tm_hour = ( pMJD[2] >> 4 )*10 + (pMJD[2] & 0x0f);
	lt.tm_min  = ( pMJD[3] >> 4 )*10 + (pMJD[3] & 0x0f);
	lt.tm_sec  = ( pMJD[4] >> 4 )*10 + (pMJD[4] & 0x0f);
	lt.tm_isdst = 0;
	return mktime( &lt );
}



static long BCDTime( unsigned char* pMJD )
{
	int h,m,s;
	h = ( pMJD[0] >> 4 )*10  + (pMJD[0] & 0x0f);
	m  = ( pMJD[1] >> 4 )*10 + (pMJD[1] & 0x0f);
	s  = ( pMJD[2] >> 4 )*10 + (pMJD[2] & 0x0f);
	
	return h*3600+m*60+s;
}


static int TotalDescriptor( SI_PARSER* pParser, char *pData, int Bytes, unsigned char ExpectedTag )
{
	int total = 0, total_bytes=0;
	unsigned char tag;
	unsigned int len;
	unsigned char* p;
	if ( Bytes < 2 )
		return 0;
	tag = pData[0];
	len = (unsigned char)pData[1];
	p = (unsigned char*)pData+2;
	total_bytes += 2+len;
	while ( total_bytes <= Bytes )
	{
		if ( ExpectedTag != 0xff && ExpectedTag == tag )
			total++;
		else
		if ( ExpectedTag == 0xff )
			total++;

		if ( total_bytes >= Bytes )
			break;

		p += len;
		tag =  p[0];
		len = (unsigned char)p[1];
		p += 2;
		total_bytes += 2+len;
		if ( total_bytes > 12*1024 ) break; //session wrong
	}
	return total;
}

static unsigned char* SeekDescriptor( SI_PARSER* pParser, char *pData, int Bytes, unsigned char ExpectedTag )
{
	int total_bytes=0;
	unsigned char tag;
	unsigned int len;
	unsigned char* p, *ps;
	ps = (unsigned char*)pData;
	if ( Bytes < 2 )
		return NULL;
	tag = pData[0];
	len = (unsigned char)pData[1];
	p = (unsigned char*)pData+2;
	total_bytes += 2+len;
	while ( total_bytes <= Bytes )
	{
		if ( ExpectedTag == tag )
			break;

		if ( total_bytes >= Bytes )
			break;

		p += len;
		ps = p;
		tag =  p[0];
		len = (unsigned char)p[1];
		p += 2;
		total_bytes += 2+len;
		if ( total_bytes > 12*1024 ) break; //session wrong
	}

	if ( *ps == ExpectedTag ) 
		return ps;
	else
		return NULL;
}

/*
static int DescriptorBytes( SI_PARSER* pParser, char *pData, int Bytes )
{
	int total_bytes = 0;
	unsigned char* p;
	unsigned char tag;
	int len;
	tag = pData[0];
	len = (unsigned char)pData[1];
	p = (unsigned char*)pData+2;
	total_bytes += 2+len;
	while ( total_bytes < Bytes )
	{
		p += len;
		tag = pData[0];
		len = ((unsigned char)p[1]);
		p += 2;
		total_bytes += 2+len;
		if ( total_bytes > 8*1024 || tag == 0xff ) 
			return -1;
	}
	return total_bytes;
}
*/


static int DescriptorParser( SI_PARSER* pParser, char *pData, int Bytes, unsigned char ExpectedTag, DESCRIPTOR* pDesc, char** pPos )
{
	unsigned char tag;
	unsigned int len;
	int i;

	if ( pDesc == NULL || pData == NULL ) return 0;

	pDesc->data = NULL;
	pDesc->tag = 0;

	if (  Bytes < 2  ) return 0;
	tag = pData[0];
	len = ((unsigned char)pData[1]);
	i = 0;
	while ( ExpectedTag != tag && i+2+len + 2 < (unsigned int)Bytes )
	{
		i += 2+len;
		tag = pData[i];
		len = ((unsigned char)pData[i+1]);
	}

	if ( i + (int)len + 2 > Bytes || ExpectedTag != tag )
		return 0;

	if ( pPos != NULL ) *pPos = pData+i;

	switch ( tag )	{
	case NETWORK_NAME_TAG:
		if ( parser_network_name( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;

	case SERVICE_LIST_TAG:
		if ( parser_service_list( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;

	case TERRESTRIAL_DELIVERY_TAG:
		if ( parser_terrestrial_delivery( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;

	case CABLE_DELIVERY_TAG:
		if ( parser_cable_delivery( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;

	case SATELLITE_DELIVERY_TAG:
		if ( parser_satellite_delivery( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;

	case SERVICE_TAG	:
		if ( parser_service( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;

	case FREQUENCY_LIST_TAG:
		break;

	case CELL_LIST_TAG:
		break;

	case CELL_FREQ_LINK_TAG:
		break;

	case SHORT_EVENT_TAG:
		if ( parser_short_event( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;

	case EXTENDED_EVENT_TAG:
		if ( parser_extended_event( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;

	case DVB_PRENTAL_RATING_TAG:
		if ( parser_prental_rating( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;

	case ATSC_CONTENT_ADVISORY:
		if ( parser_content_advisory( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;

	case DVB_CONTENT_DESC_TAG:
		if ( parser_content_desc( pParser, pData+i+2, len, &pDesc->data ) )
			pDesc->tag = tag;
		break;


	case ATSC_CHANNEL_NAME:
		//if ( parser_channel_long_name( pParser, pData+i+2, len, &pDesc->data ) )
		//	pDesc->tag = tag;

		break;
	
	default:
		pDesc->tag = 0;
		return 0;
	}
	return len+2;
}


//DVB descriptor paser
static bool parser_network_name( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData )
{
	if ( pDescData == NULL && Bytes < 0 && Bytes > 6*1024 )
		return false;
	*pDescData = SAGETV_MALLOC( Bytes+1 );
	memcpy( *pDescData, pData, Bytes );
	*((char*)*pDescData+Bytes) = 0x0;
	return true;
}

static void free_network_name( SI_PARSER* pParser, void* pDescData )
{
	if ( pDescData == NULL )
		return;
	SAGETV_FREE( pDescData );
}


static bool parser_service_list( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData )
{
	int i;
	unsigned char* p;
	DVB_SERVICE_LIST *dvb_service_list;

	if ( pDescData == NULL ) 
		return false;

	if ( Bytes < 3 )
		return false;
	
	*pDescData = SAGETV_MALLOC( sizeof(DVB_SERVICE_LIST) );
	dvb_service_list = (DVB_SERVICE_LIST*)*pDescData;
	dvb_service_list->service_num = Bytes/3;
	dvb_service_list->service = SAGETV_MALLOC( sizeof(DVB_SERVICE) * dvb_service_list->service_num );
	p = (unsigned char*)pData;
	for ( i = 0; i<dvb_service_list->service_num; i++ )
	{
		dvb_service_list->service[i].seviceid = (p[0]<<8) | p[1];
		dvb_service_list->service[i].type = p[2];
		p += 3;
	}
	return true;
}

static void free_service_list( SI_PARSER* pParser,void* pDescData )
{
	DVB_SERVICE_LIST *dvb_service_list;
	if ( pDescData == NULL )
		return;

	dvb_service_list = (DVB_SERVICE_LIST *)pDescData;
	if ( dvb_service_list->service )
		SAGETV_FREE( dvb_service_list->service );
	SAGETV_FREE( dvb_service_list );
}

static bool parser_terrestrial_delivery( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData )
{
	unsigned char* p;
	TERRESTRIAL_DATA *terrestrial;

	if ( pDescData == NULL ) 
		return false;

	*pDescData = SAGETV_MALLOC( sizeof(TERRESTRIAL_DATA) );
	terrestrial = (TERRESTRIAL_DATA *)*pDescData;

	p = (unsigned char*)pData;
	terrestrial->freq = (p[0]<<24) | (p[1]<<16) | (p[2]<<8) | p[3];
	terrestrial->freq *= 10;
	terrestrial->band = (p[4]>>5) & 0x7;
	switch ( terrestrial->band )
	{ case 0: terrestrial->band = 8; break;
	  case 1: terrestrial->band = 7; break;
	  case 2: terrestrial->band = 6; break;
	  case 3: terrestrial->band = 5; break;
	  default: terrestrial->band = 100;
	}
	return true;
}


static void free_terrestrial_delivery( SI_PARSER* pParser, void* pDescData )
{
	if ( pDescData == NULL )
		return;

	SAGETV_FREE( pDescData);
}

static unsigned long unpackBCDcode( char* p, int bytes );
static bool parser_cable_delivery( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData  )
{
	unsigned char* p;
	CABLE_DATA *cable;

	if ( pDescData == NULL ) 
		return false;

	*pDescData = SAGETV_MALLOC( sizeof(CABLE_DATA) );
	cable = (CABLE_DATA *)*pDescData;

	p = (unsigned char*)pData;
	cable->freq = unpackBCDcode( (char*)p, 4 );
	cable->freq *= 10;

	cable->fec_out = p[5]&0x0f;
	if ( cable->fec_out != 2 ) cable->fec_out = 0;
	cable->modulation = p[6];
	if ( cable->modulation == 1 ) cable->modulation = 1; else  //16QAM
	if ( cable->modulation == 2 ) cable->modulation = 2; else  //32QAM
	if ( cable->modulation == 3 ) cable->modulation = 3; else  //64QAm
	if ( cable->modulation == 4 ) cable->modulation = 7; else  //128QAM
	if ( cable->modulation == 5 ) cable->modulation = 11; else //256QAM
		cable->modulation = 0;
	cable->symbol_rate = unpackBCDcode( (char*)p+7, 4 );
	cable->symbol_rate /= 100;
	cable->fec_in_rate = (p[10]&0x0f);
	if ( cable->fec_in_rate > 6 )  cable->fec_in_rate = 0; else
	if ( cable->fec_in_rate == 1 ) cable->fec_in_rate = 1; else //fec 1/2
	if ( cable->fec_in_rate == 2 ) cable->fec_in_rate = 2; else //fec 2/3
	if ( cable->fec_in_rate == 3 ) cable->fec_in_rate = 3; else //fec 3/4
	if ( cable->fec_in_rate == 4 ) cable->fec_in_rate = 6; else //fec 5/6
	if ( cable->fec_in_rate == 5 ) cable->fec_in_rate = 8; else //fec 7/8
	if ( cable->fec_in_rate == 6 ) cable->fec_in_rate = 9; //fec 8/9

	return true;
}

static void free_cable_delivery( SI_PARSER* pParser, void* pDescData )
{
	if ( pDescData == NULL )
		return;

	SAGETV_FREE( pDescData);
}

static bool parser_satellite_delivery( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData  )
{
	unsigned char* p;
	SATELLITE_DATA *satellite;
	

	if ( pDescData == NULL ) 
		return false;

	*pDescData = SAGETV_MALLOC( sizeof(SATELLITE_DATA) );
	satellite = (SATELLITE_DATA *)*pDescData;

	p = (unsigned char*)pData;
	satellite->freq = unpackBCDcode( (char*)p, 4 );
	satellite->freq *= 10;

	//skip 2 bytes orbit
	satellite->orbit = (short)unpackBCDcode( (char*)p+4, 2 );
	if ( (p[6] & 0x80) ) satellite->orbit = -satellite->orbit;
	satellite->pol = (p[6]>>5)&0x03;
	satellite->pol++;
	satellite->modulation = p[6] & 0x1f;
	if ( satellite->modulation == 1 ) satellite->modulation = 20; else //QPSK
	if ( satellite->modulation == 2 ) satellite->modulation = 22; else //OQPSK
	if ( satellite->modulation == 3 ) satellite->modulation = 1;  else //16QAM
	if ( satellite->modulation == 5 ) satellite->modulation = 30; else //8PSK for DVB-S2
		satellite->modulation = 0;


	satellite->symbol_rate = unpackBCDcode( (char*)p+7, 4 );
	satellite->symbol_rate /= 100;
	satellite->fec_rate = (p[10]&0x0f);
	if ( satellite->fec_rate > 6 )  satellite->fec_rate = 0; else
	if ( satellite->fec_rate == 1 ) satellite->fec_rate = 1; else //fec 1/2
	if ( satellite->fec_rate == 2 ) satellite->fec_rate = 2; else //fec 2/3
	if ( satellite->fec_rate == 3 ) satellite->fec_rate = 3; else //fec 3/4
	if ( satellite->fec_rate == 4 ) satellite->fec_rate = 6; else //fec 5/6
	if ( satellite->fec_rate == 5 ) satellite->fec_rate = 8; else //fec 7/8
	if ( satellite->fec_rate == 6 ) satellite->fec_rate = 9;      //fec 8/9
	return true;
}

static void free_satellite_delivery( SI_PARSER* pParser, void* pDescData )
{
	if ( pDescData == NULL )
		return;

	SAGETV_FREE( pDescData);
}

static bool parser_service( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData )
{
	unsigned char*p;
	SERVICE_DATA* service;
	char charset[64];
	int start_offset;
	int len, len2;
	int code_byte;
	if ( pData == NULL || Bytes <= 2 )
		return false;
	*pDescData = SAGETV_MALLOC( sizeof(SERVICE_DATA) );
	service = (SERVICE_DATA*)*pDescData;

	p = (unsigned char*)pData;
	service->type = pData[0];
	
	len = ((unsigned char)pData[1]);
	if ( len+2 > Bytes )
		return true;

	code_byte = GetDVBTextCharSet( p+2, len, charset, sizeof(charset), &start_offset );
	//I should add charset description tag [set=xxx][len=yyy] here, when Jeff's code ready ZQ
	service->provider_name = SAGETV_MALLOC( len+1 );
	if ( len-start_offset >=0 )
	{
		memcpy( service->provider_name, p+2+start_offset, len-start_offset );
		service->provider_name[len-start_offset] = 0x0;
	}

	len2 = ((unsigned char)pData[2+len]);
	if ( len2+len+2+1 > Bytes )
		return true;
	GetDVBTextCharSet( p+2+len+1, len2, charset, sizeof(charset), &start_offset );
	//I should add charset description tag here, when Jeff's code ready ZQ.
	service->service_name = SAGETV_MALLOC( len2+1 );
	if ( len2-start_offset >= 0 )
	{
		memcpy( service->service_name, p+2+len+1+start_offset, len2-start_offset );
		len = FilterDVBTextCtrlCode( (unsigned char*)service->service_name, len2-start_offset, code_byte );
		service->service_name[len] = 0x0;
	}
	return true;
}

static void free_service( SI_PARSER* pParser, void* pDescData )
{
	SERVICE_DATA *service;
	if ( pDescData == NULL )
		return;
	
	service = ( SERVICE_DATA*)pDescData ;
	if ( service->provider_name )
		SAGETV_FREE( service->provider_name );
	if ( service->service_name )
		SAGETV_FREE( service->service_name );

	SAGETV_FREE( service);
}

static bool parser_short_event( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData )
{
	S_EVENT *s_event;
	unsigned long guid;

	if ( pData == NULL )
		return false;

	s_event = SAGETV_MALLOC( sizeof(S_EVENT) );
	*pDescData = s_event;
	s_event->lanuage_code[0] = pData[0];
	s_event->lanuage_code[1] = pData[1];
	s_event->lanuage_code[2] = pData[2];
	s_event->lanuage_code[3] = 0;

	s_event->title_length = (unsigned char)pData[3];
	s_event->mesg_length  = (unsigned char)pData[4+s_event->title_length];

	if ( s_event->title_length )
	{
		s_event->title = SAGETV_MALLOC( s_event->title_length+1 );
		memcpy( s_event->title, pData+4, s_event->title_length );
	}

	if ( s_event->mesg_length )
	{
		s_event->message = SAGETV_MALLOC( s_event->mesg_length+1 );
		memcpy( s_event->message, pData+4+s_event->title_length+1, s_event->mesg_length );
	}

	guid = GetCRC32( s_event->message, s_event->mesg_length );
	guid += GetCRC32( s_event->title, s_event->title_length );
	s_event->guid.c = guid;
	s_event->guid.a = s_event->title_length;
	s_event->guid.b = s_event->mesg_length;

	return true;
}

static void free_short_event( SI_PARSER* pParser, void* pDescData )
{
	S_EVENT *s_event;
	if ( pDescData == NULL )
	return;

	s_event = pDescData;
	if ( s_event->title_length && s_event->title )
		SAGETV_FREE( s_event->title );

	if ( s_event->mesg_length && s_event->message )
		SAGETV_FREE( s_event->message );

	SAGETV_FREE( s_event );
}

/*
bool short_event_updated( S_EVENT *s_event1, S_EVENT *s_event2 )
{
	if ( s_event1 == NULL || s_event2 == NULL )
		return true;

	if ( s_event1->mesg_length != s_event2->mesg_length ||
		 s_event1->title_length != s_event2->title_length||
		 memcmp( s_event1->lanuage_code, s_event2->lanuage_code,3	) )
 		return true;
	
	if ( memcmp( s_event1->title, s_event2->title, s_event1->title_length ) )
		return true;
	if ( memcmp( s_event2->message, s_event2->message, s_event1->mesg_length ) )
		return true;

	return false;
}
*/

static bool parser_extended_event( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData )
{
	E_EVENT *e_event;
	unsigned char* p;
	int i, len;
	int item_bytes;
	
	if ( pData == NULL && Bytes < 5 )
		return false;

	p=(unsigned char*)pData;
	e_event = SAGETV_MALLOC( sizeof(E_EVENT) );
	*pDescData = e_event;
	e_event->item_no = ( p[0] >> 4 ) & 0x0f;
	e_event->item_last_no = ( p[0] & 0x0f );
	e_event->lanuage_code[0] = p[1];
	e_event->lanuage_code[1] = p[2];
	e_event->lanuage_code[2] = p[3];
	e_event->lanuage_code[3] = 0;
	item_bytes = (unsigned char)p[4];
	e_event->item_number = 0;

	if ( item_bytes > 0 )
	{
		i = 0; 
		len = 0; 
		p = (unsigned char*)pData+5; 
		while ( len < item_bytes && len < Bytes )
		{
			if ( (unsigned char)p[0] == 0 )
				break;

			p += 1 + (unsigned char)p[0];
			len += 1 + (unsigned char)p[0];

			if ( (unsigned char)p[0] == 0 )
				break;

			p += 1 + (unsigned char)p[0];
			len += 1 + (unsigned char)p[0];
			i++;
		}

		if ( i > 0 )
		{
			e_event->item_number = i;
			e_event->items = SAGETV_MALLOC( sizeof( ITEM )*i );
			i = 0;
			p = (unsigned char*)pData+5; 
			while ( i < e_event->item_number )
			{
				e_event->items[i].item_desc_length = (unsigned char)p[0];
				if ( e_event->items[i].item_desc_length )
				{
					e_event->items[i].item_desc = SAGETV_MALLOC( e_event->items[i].item_desc_length + 2 );
					memcpy( e_event->items[i].item_desc, p+1, e_event->items[i].item_desc_length );
				}
				p += 1 + e_event->items[i].item_desc_length;
				len += 1 + e_event->items[i].item_desc_length;

				e_event->items[i].item_text_length = (unsigned char)p[0];
				if ( e_event->items[i].item_text_length )
				{
					e_event->items[i].item_text = SAGETV_MALLOC( e_event->items[i].item_text_length + 2 );
					memcpy( e_event->items[i].item_text, p+1, e_event->items[i].item_text_length );
				}
				p += 1 + e_event->items[i].item_text_length;
				len += 1 + e_event->items[i].item_text_length;
					
				{   //add one more character for display, that should be up a layer job
					e_event->items[i].item_desc[ e_event->items[i].item_desc_length ] = ':';
					e_event->items[i].item_desc_length++;
					e_event->items[i].item_text[ e_event->items[i].item_text_length ] = ' ';
					e_event->items[i].item_text_length++;
				}

				i++;
			}
		}
		
	}
	p = (unsigned char*)pData+5+item_bytes; 
	if ( 5+item_bytes < Bytes )
	{
		e_event->text_length = (unsigned short)p[0];
		if ( e_event->text_length )
		{
			e_event->text = SAGETV_MALLOC( e_event->text_length + 1 );
			memcpy( e_event->text, p+1, min( Bytes-(5+item_bytes), e_event->text_length) );
		}
	}

	return true;

}

static void free_extended_event( SI_PARSER* pParser, void* pDescData )
{
	E_EVENT *e_event;
	int i;
	if ( pDescData == NULL )
		return;

	e_event = pDescData;
	if ( e_event->items != NULL )
	{
		for ( i = 0; i<e_event->item_number; i++ )
		{
			if ( e_event->items[i].item_desc != NULL && e_event->items[i].item_desc_length > 0 )
				SAGETV_FREE( e_event->items[i].item_desc );
			if ( e_event->items[i].item_text != NULL && e_event->items[i].item_text_length > 0 )
				SAGETV_FREE( e_event->items[i].item_text );
		}
		SAGETV_FREE( e_event->items );
	}

	if ( e_event->text != NULL && e_event->text_length > 0  )
		SAGETV_FREE( e_event->text );

	SAGETV_FREE( e_event );
}

bool extended_event_updated( E_EVENT *e_event1, E_EVENT *e_event2 )
{
	int i;
	if ( e_event1 == NULL || e_event2 == NULL )
		return true;

	if ( e_event1->item_no != e_event2->item_no ||
		 e_event1->item_last_no != e_event2->item_last_no ||
		 e_event1->item_number != e_event2->item_number ||
		 e_event1->text_length != e_event2->text_length || 
		 memcmp( e_event1->lanuage_code, e_event2->lanuage_code,3	) || 
		 memcmp( e_event1->text, e_event2->text, e_event2->text_length 	) )
 		return true;
	
	for ( i = 0; i<e_event1->item_number; i++ )
	{
		if ( e_event1->items[i].item_desc_length != e_event2->items[i].item_desc_length ||
			 e_event1->items[i].item_text_length != e_event2->items[i].item_text_length ||
			 memcmp( e_event1->items[i].item_desc, e_event2->items[i].item_desc, e_event1->items[i].item_desc_length ) ||
			 memcmp( e_event1->items[i].item_text, e_event2->items[i].item_text, e_event1->items[i].item_text_length ) )
			return true;
	}

	return false;
}

//static int cad_num( SI_PARSER* pParser )
//{
//	int num = 0, i, j;
//	for ( i = 0; i<MAX_EIT_NUM; i++ )
//	{
//		for ( j = 0; j<MAX_EPG_NUM_PER_CH; j++ )
//			if ( pParser->eit[i][j].cad != NULL )
//				num++;
//	}
//	return num;
//}

static bool parser_content_advisory( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData )
{
	int i,j;
	unsigned char* p;
	CAD *cad;

	if ( pDescData == NULL ) 
		return false;

	*pDescData = SAGETV_MALLOC( sizeof(CAD) );
	cad = (CAD *)*pDescData;

	p = (unsigned char*)pData;
	cad->region_num = *p & 0x3f;
	if ( cad->region_num == 0 )
	{
		cad->rating = NULL;
		return true;
	}
	p++;
	cad->rating = SAGETV_MALLOC( sizeof(CAD_RATING)* cad->region_num );
	for ( i = 0; i<cad->region_num; i++ )
	{
		int length, dimension;
		cad->rating[i].rating_region = *p;
		dimension = *(p+1);
		dimension = _MIN( dimension, 16 );
		cad->rating[i].rating_num = dimension;
		p += 2;
		for ( j = 0; j<dimension; j++ )
		{
			cad->rating[i].rating_index[j] = (*p++);
			cad->rating[i].value_index[j] =  (*p++) & 0x0f;
		}

		length = *p;
		if ( length > 0 )
		{	MSS *mss = NULL;
			UnpackMultiString( pParser, &mss, p+1, length );
			if ( mss->string_list && mss->string_list[0].seg_list )
			{
				memcpy( cad->rating[i].description, 
					    mss->string_list[0].seg_list[0].mesg,
						_MIN( mss->string_list[0].seg_list[0].byte_length, sizeof(cad->rating[i].description) ) );
			}
			FreeMultiString( pParser, mss ); mss = NULL;
		}
		p += 1+length;
	}
	return true;
}

static void free_content_advisory( SI_PARSER* pParser, void* pDescData )
{
	CAD *cad;
	if ( pDescData == NULL )
		return;
	cad = (CAD*)pDescData;
	if ( cad->region_num && cad->rating )
		SAGETV_FREE( cad->rating );

	SAGETV_FREE( cad );
}

static bool parser_prental_rating( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData )
{
	RATING_TBL* rating_tbl;
	int rating_num, i;
	unsigned char* p;

	rating_num = Bytes/4;
	if ( rating_num > 256 )
		return false;

	*pDescData = SAGETV_MALLOC( sizeof(RATING_TBL) );
	rating_tbl = (RATING_TBL*)*pDescData;
	rating_tbl->num = rating_num;
	rating_tbl->rating = SAGETV_MALLOC( sizeof(DVB_RATING)*rating_num );

	p = (unsigned char*)pData;;
	for ( i = 0; i< rating_num; i++ )
	{
		rating_tbl->rating[i].country[0] = *p;
		rating_tbl->rating[i].country[1] = *(p+1);
		rating_tbl->rating[i].country[2] = *(p+2);
		rating_tbl->rating[i].country[3] = 0;
		rating_tbl->rating[i].age = *(p+3);
		p += 4;
	}
	return true;
}

static void free_prental_rating( SI_PARSER* pParser, void* pDescData )
{
	RATING_TBL* rating_tbl;
	if ( pDescData == NULL )
		return;
	rating_tbl = (RATING_TBL*)pDescData;
	if ( rating_tbl->num != 0 && rating_tbl->rating != NULL )
		SAGETV_FREE( rating_tbl->rating );

	SAGETV_FREE( rating_tbl );
}

static bool parser_content_desc( SI_PARSER* pParser, char* pData, int Bytes, void** pDescData )
{
	unsigned char* content_code;
	int num, i;
	unsigned char* p;

	num = Bytes/2;
	if ( num > 256 )
		return false;

	*pDescData = SAGETV_MALLOC( num+1 );
	content_code = (unsigned char*)*pDescData;

	p = (unsigned char*)pData;;
	for ( i = 0; i< num; i++ )
	{
		content_code[i] = *p;
		p += 2;
	}
	return true;
}

static void free_content_desc( SI_PARSER* pParser, void* pDescData )
{
	if ( pDescData == NULL )
		return;
	SAGETV_FREE( pDescData );
}

static unsigned char GetNext8Bits( unsigned char* pData, int pos, unsigned char bit )
{
	int i;
	unsigned char ch;
	ch = 0;
	for ( i = 0; i<8; i++ )
	{
		bit >>= 1;
		ch <<= 1;
		if ( bit == 0 ) 	
		{    pos++; 
		     bit = 0x80; 
		} 
		if ( bit & pData[pos] ) ch |= 1;
	}

	return ch;
}

static int UncompressHuffman( int Type, unsigned char* pOut, int MaxSize, unsigned char* pData, int Length )
{
	int i, bytes;
	unsigned char *huffman, *tree_root;
	unsigned char lch, cch, ch;
	unsigned char bit;
	int offset, index;

	if ( Length <= 0 || pData == NULL  || MaxSize <= 1 )
		return 0;


	if ( Type == 1  ) 
		huffman = TitleHuffmanTbl();
	else
	if ( Type == 2  ) 
		huffman = ProgramHuffmanTbl();
	else
	{
		//unknow format, just output
		bytes = _MIN( Length, MaxSize );
		memcpy( pOut, pData, bytes );
		return bytes;
	}

	bytes = 0;	i = 0;
	index = 0;
	lch = 0; bit = 0; cch = 0;
	index = (huffman[lch*2]<<8)|huffman[lch*2+1];
	tree_root = &huffman[index];
	offset = 0;
	while ( bytes < MaxSize  )
	{
		unsigned char node;

		if ( i >= Length && bit == 0 )
			break;

		if ( bit == 0 ) 
		{
			cch = pData[i++];
			bit = 0x80;
		}

		if ( bit & cch )
			node = tree_root[offset+1];  //right child branch
		else
			node = tree_root[offset];    //left child branch

		if ( node & 0x80 )
		{
			ch = node & 0x7f;
			if ( ch == 27 )
			{
				lch = GetNext8Bits( pData, i-1, bit );  //get uncompressed 8 bits to output
				cch = pData[i++];  //skip 8 bits
			} else
			{
				lch = ch; 
			}
			*pOut++ = lch;
			bytes++;
			
			if ( bytes >= MaxSize )
			{
				printf( "uncompress buffer is too small\n" );
			}

			if ( lch == 0 )
				break;

			index = (huffman[lch*2]<<8)|huffman[lch*2+1];
			tree_root = &huffman[index];

			if ( (Type == 2 && index > 1781) || (Type == 1 && index > 1939) ) 
			{
				printf( "Error uncompress\n" );
				return bytes;
			}
			offset = 0;

		}
		else
		{
			offset = node*2 ;
		}

		bit >>= 1;
		
	}

	return bytes;
}
	

char* genre_string( unsigned char code )
{
	if ( code < 0x20 ) //reserved
		return "";
	if ( code > 0xad ) //reserved
		return "";

	return GenreCode(code-0x20);
}

char* DVBGenreCode( unsigned char code );
char* dvb_genre_string( unsigned char code )
{
	return DVBGenreCode( code );
}

static int FilterDVBTextCtrlCode( unsigned char* code, int bytes, int code_byte )
{
	unsigned char* ps, *pt;
	int length = 0;

	if ( code == NULL || bytes <= 0 ) return 0;
	
	ps = pt = code;
	if ( code_byte == 1 )
	{
		while ( bytes-- > 0 )
		{
			if ( *ps != 0x86 && *ps != 0x87 && *ps != 0x8a )
			{
				*pt++ = *ps++;
				length++;
			} else
				ps++;
		}
	} else
	{
		unsigned short wd;
		while ( bytes > 2 )
		{
			wd = (*ps<<8)|*(ps+1);
			if ( wd != 0xe086 && wd != 0xe087 && wd != 0xe08a )
			{
				*pt++ = *ps++;
				*pt++ = *ps++;
				length++; length++;
			} else
			{
				ps++; ps++;
			}

			bytes--; bytes--;
		}
	}
	return length;
}

static int TotalDVBTextCtrlCodeNum( unsigned char* code, int bytes, int code_byte )
{
	unsigned char* ps;
	int total = 0;

	if ( code == NULL || bytes <= 0 ) return 0;
	
	ps = code;
	if ( code_byte == 1 )
	{
		while ( bytes-- > 0 )
		{
			if ( *ps == 0x86 || *ps == 0x87 || *ps == 0x8a )
				total++;
			ps++;
		}
	} else
	{
		unsigned short wd;
		while ( bytes > 2 )
		{
			wd = (*ps<<8)|*(ps+1);
			if ( wd == 0xe086 || wd == 0xe087 || wd == 0xe08a )
			{
				total++; total++;
			} 

			ps++; ps++;
			bytes--; bytes--;
		}
	}
	return total;
}

static int GetDVBTextCharSet( unsigned char* code, int bytes, char* desc,  int desc_size, int* offset )
{
	*offset = 0;
	if ( desc == NULL || desc_size < 46 ) return 0;
	desc[0] = 0x0;
	if ( code == NULL || bytes < 1 ) return 0;
	if ( code[0] != 0xff && code[0] >= 0x20 )
	{
		strncpy( desc, "[set=ISO-8859-1]", desc_size );
		*offset = 0;
		return 1;
	} else
	if ( code[0] == 1 )
	{
		strncpy( desc, "[set=ISO-8859-5]", desc_size );
		*offset = 1;
		return 1;
	}else
	if ( code[0] == 2 )
	{
		strncpy( desc, "[set=ISO-8859-6]", desc_size );
		*offset = 1;
		return 1;
	}else
	if ( code[0] == 3 )
	{
		strncpy( desc, "[set=ISO-8859-7]", desc_size );
		*offset = 1;
		return 1;
	}else
	if ( code[0] == 4 )
	{
		strncpy( desc, "[set=ISO-8859-8]", desc_size );
		*offset = 1;
		return 1;
	}else
	if ( code[0] == 5 )
	{
		strncpy( desc, "[set=ISO-8859-9]", desc_size );
		*offset = 1;
		return 1;
	}else
	if ( code[0] == 6 )
	{
		strncpy( desc, "[set=ISO-8859-10]", desc_size );
		*offset = 2;
		return 2;
	}else
	if ( code[0] == 7 )
	{
		strncpy( desc, "[set=ISO-8859-11]", desc_size );
		*offset = 2;
		return 2;
	}else
	if ( code[0] == 9 )
	{
		strncpy( desc, "[set=ISO-8859-13]", desc_size );
		*offset = 2;
		return 2;
	}else
	if ( code[0] == 0xa )
	{
		strncpy( desc, "[set=ISO-8859-14]", desc_size );
		*offset = 2;
		return 2;
	}else
	if ( code[0] == 0xb )
	{
		strncpy( desc, "[set=ISO-8859-15]", desc_size );
		*offset = 2;
		return 2;
	}else
	if ( code[0] == 0x11 )
	{
		strncpy( desc, "[set=iso-10646-ucs-2]", desc_size );
		*offset = 2;
		return 2;
	}else
	if ( code[0] == 0x12 )
	{
		strncpy( desc, "[set=ksc5601-1987]", desc_size );
		*offset = 2;
		return 2;
	}else
	if ( code[0] == 0x13 )
	{
		strncpy( desc, "[set=gb2312-1980]", desc_size );
		*offset = 2;
		return 2;
	}else
	if ( code[0] == 0x14 )
	{
		strncpy( desc, "[set=big5]", desc_size );
		*offset = 2;
		return 2;
	}else
	if ( code[0] == 0x15 )
	{
		strncpy( desc, "[set=UTF-8]", desc_size );
		*offset = 2;
		return 2;
	}else
	if ( code[0] == 0x10 )
	{
		unsigned short chset = (code[1]<<8)||code[2];
		if ( chset == 0x01 )
			strncpy( desc, "[set=ISO-8859-1]", desc_size );
		else
		if ( chset == 0x02 )
			strncpy( desc, "[set=ISO-8859-2]", desc_size );
		else
		if ( chset == 0x03 )
			strncpy( desc, "[set=ISO-8859-3]", desc_size );
		else
		if ( chset == 0x04 )
			strncpy( desc, "[set=ISO-8859-4]", desc_size );
		else
		if ( chset == 0x05 )
			strncpy( desc, "[set=ISO-8859-5]", desc_size );
		else
		if ( chset == 0x06 )
			strncpy( desc, "[set=ISO-8859-6]", desc_size );
		else
		if ( chset == 0x07 )
			strncpy( desc, "[set=ISO-8859-7]", desc_size );
		else
		if ( chset == 0x08 )
			strncpy( desc, "[set=ISO-8859-8]", desc_size );
		else
		if ( chset == 0x09 )
			strncpy( desc, "[set=ISO-8859-9]", desc_size );
		else
		if ( chset == 0x0A )
			strncpy( desc, "[set=ISO-8859-10]", desc_size );
		else
		if ( chset == 0x0B )
			strncpy( desc, "[set=ISO-8859-11]", desc_size );
		else
		if ( chset == 0x0C )
			strncpy( desc, "[set=ISO-8859-12]", desc_size );
		else
		if ( chset == 0x0D )
			strncpy( desc, "[set=ISO-8859-13]", desc_size );
		else
		if ( chset == 0x0E )
			strncpy( desc, "[set=ISO-8859-14]", desc_size );
		else
		if ( chset == 0x0F )
			strncpy( desc, "[set=ISO-8859-15]", desc_size );
		else
			strncpy( desc, "[set=UTF-8]", desc_size );
		*offset = 3;
		return 2;

	}else
	{
		strncpy( desc, "[set=ISO-8859-1]", desc_size );
		*offset = 1;
		return 1;
	}

	return 0;
}

static int  MakeTextMesgString ( char* buffer, int buffer_size, char* message, int mesg_length )
{
	char data_desc[60];
	int code_byte, length, start_offset, bytes;
	char *p;
	p = buffer; bytes = 0;

	if ( message == NULL || mesg_length == 0 )
		return 0;

	code_byte = GetDVBTextCharSet( (unsigned char*)message, mesg_length, data_desc, sizeof(data_desc), &start_offset );
	if ( mesg_length-start_offset <= 0 ) 
		return 0;
	if ( bytes + (int)strlen(data_desc) > buffer_size ) return 0;
	strcpy( p, data_desc );
	p += strlen(data_desc); bytes += strlen(data_desc);

	length = TotalDVBTextCtrlCodeNum( (unsigned char*)message+start_offset, mesg_length-start_offset, code_byte );
	sprintf( data_desc, "[len=%d]", mesg_length-start_offset-length );
	if ( bytes + (int)strlen(data_desc) > buffer_size ) return 0;

	strcat( p, data_desc );
	p += strlen(data_desc); bytes += strlen(data_desc);

	if ( bytes + mesg_length-start_offset > buffer_size-1 ) return 0;
	
	memcpy( p, message+start_offset, mesg_length-start_offset );
	length = FilterDVBTextCtrlCode( (unsigned char*)p, mesg_length-start_offset, code_byte );
	p += length; bytes += length;
	*p = 0x0;
	
	return  bytes;
}
static unsigned long unpackBCDcode( char* p, int bytes )
{
	int i;
	unsigned long val = 0;
	for ( i=0; i<bytes; i++, p++ )
	{
		val *= 10;
		val += (*p>>4) & 0x0f;
		val *= 10;
		val += (*p) & 0x0f;
	}
	return val;
}

int TranslateJWideString2( char* buf_out, int buf_out_size, unsigned short* buf_in )
{
	int len=0, i=0;
	unsigned short* p = (unsigned short*)buf_in;
	wchar_t* v;
	int ret;
	if ( buf_out == NULL ) return 0;
	while (  *p++  ) len++;

	len = min( buf_out_size-1, len+1 );
	v= (wchar_t*)malloc( len*sizeof(wchar_t) );
	p = (unsigned short*)buf_in;
	
#if defined(__APPLE__)
	for ( i = 0; i<len; i++ ) v[i] = (wchar_t)OSReadBigInt16(p, i * 2);
#else
	for ( i = 0; i<len; i++ ) v[i] = ( ((p[i]<<8)&0xff00) | p[i] >> 8 ); 
#endif
	
	ret = wcstombs( buf_out, (wchar_t*)v, len+1 );
	
	
	if ( ret == (size_t)-1 )
	{
		p = (unsigned short*)buf_in;
		for ( i = 0; i<len; i++ ) 
		     buf_out[i]= (char)( 0xff & p[i] ); 
		buf_out[len] = 0x0;
	}
	free( v );
	return strlen( buf_out );
}

///////////////////////////////////////////////////////////////////////////////////
static int MakeTERRESTRIALString( char* Buf, int Size,  TERRESTRIAL_DATA* data  )
{
	char tmp[100], *p;
	if ( Buf == NULL || Size <= 0 ) return 0;
	p = Buf;
	*p = 0x0;

	sprintf( tmp, "frq:%ld ", data->freq );
	if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	sprintf( tmp, "band:%d ", data->band );
	if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	if ( data->trans_mode != 0xff ) 
	{
		sprintf( tmp, "mode:%d ", data->trans_mode );
		if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	return strlen(Buf);
}

static int MakeCABLEString( char* Buf, int Size,  CABLE_DATA* data  )
{
	char tmp[100], *p;
	if ( Buf == NULL || Size <= 0 ) return 0;
	p = Buf;
	*p = 0x0;

	sprintf( tmp, "frq:%ld ", data->freq );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	sprintf( tmp, "rate:%ld ", data->symbol_rate );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	if ( data->modulation != 0xff ) 
	{
		sprintf( tmp, "mod:%d ", data->modulation );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( data->fec_out != 0xff && data->fec_out != (char)0  ) 
	{
		sprintf( tmp, "fec_out:%d ", data->fec_out );
		if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( data->fec_in_rate != 0xff && data->fec_in_rate != (char)0  ) 
	{
		sprintf( tmp, "fec_rate_in:%d ", data->fec_in_rate );
		if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	return strlen(Buf);
}

static int MakeSATELLITEString( char* Buf, int Size,  SATELLITE_DATA* data  )
{
	char tmp[100], *p;
	if ( Buf == NULL || Size <= 0 ) return 0;
	p = Buf;
	*p = 0x0;

	sprintf( tmp, "frq:%ld ", data->freq );
	if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	sprintf( tmp, "rate:%ld ", data->symbol_rate );
	if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	if ( data->modulation != 0xff ) 
	{
		sprintf( tmp, "mod:%d ", data->modulation );
		if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( data->pol!= 0xff ) 
	{
		sprintf( tmp, "pol:%d ", data->pol );
		if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( data->fec != 0xff && data->fec != (char)0  ) 
	{
		sprintf( tmp, "fec_in:%d ", data->fec );
		if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( data->fec_rate != 0xff && data->fec_rate != (char)0  ) 
	{
		sprintf( tmp, "fec_rate_in:%d ", data->fec_rate );
		if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( data->orbit != -1  ) 
	{
		sprintf( tmp, "orbit:%d ", data->orbit );
		if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	return strlen(Buf);
}

static int MakeDVBSNIT_INFString( char*Buf, int Size, NIT_INF *nit_inf )
{
	char tmp[100], *p;
	size_t len;
	if ( Buf == NULL || Size <= 0 ) return 0;
	p = Buf;
	*p = 0x0;

	sprintf( tmp, "onid:%d ", nit_inf->ONID );
	if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	sprintf( tmp, "tsid:%d ", nit_inf->TSID );
	if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	len = strlen(Buf);
	p = Buf+len;
	
	switch ( nit_inf->delivery_type )
	{ 
	case 1:
		MakeTERRESTRIALString( p, Size-len,  &nit_inf->dvb.t  );
		break;
	case 2:
		MakeCABLEString( p, Size-len,  &nit_inf->dvb.c  );
		break;
	case 3:
		MakeSATELLITEString( p, Size-len,  &nit_inf->dvb.s  );
		break;
	}
	
	return strlen(Buf);
}

static int MakeDVBSNITString( char*Buf, int Size, NIT *nit )
{
	char tmp[100], *p;
	size_t len, i;
	char *type = "";
	if ( Buf == NULL || Size <= 0 ) return 0;
	p = Buf;
	*p = 0x0;

	if ( nit->type == 1 ) type = "DVB-T"; else
	if ( nit->type == 2 ) type = "DVB-C"; else
	if ( nit->type == 3 ) type = "DVB-S"; else
		type = "UNKONW";

	sprintf( tmp, "#Network:%s ID:%d \nTYPE %s\n", nit->network_name, nit->network_id, type  );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	sprintf( tmp, "#Total:%d\n", nit->NIT_inf_num );
	if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	for ( i=0; i<nit->NIT_inf_num; i++ )
	{
		sprintf( tmp, "CH:%d ", i+1 );
		if ( (size_t)strlen( tmp )+ (size_t)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
		size_t len = strlen(Buf);
		p = Buf+len;
		if ( Size <= len ) break;
		MakeDVBSNIT_INFString( p, Size-len,  &nit->NIT_inf[i] );
		if ( (size_t)strlen( Buf ) +2 <Size )
			strcat( Buf, "\n" );
	}
	return strlen(Buf);
}

int  GetSINetworkNum( SI_PARSER* pParser )
{
	if ( pParser == NULL )
		return 0;
	return pParser->nit.NIT_inf_num;
}

int  GetSINetworkList( SI_PARSER* pParser, char* Buf, int MaxBufSize )
{
	if ( pParser == NULL )
		return 0;

	return MakeDVBSNITString( Buf, MaxBufSize, &pParser->nit );

}


		 

void GetSIDebugInfo( SI_PARSER* pParser, unsigned short cmd, char* Buf, unsigned long BufSize )
{
	char tmp[128];
	int i, channel_num = 0, valid_channel_num = 0, found_valid_channel_num = 0;
	if ( Buf == NULL || BufSize == 0 )
		return ;


	Buf[0] = 0;
	
	if ( pParser->stream_type == ATSC_STREAM_TYPE )
		strcpy( tmp, "ATSC " );
	else
	if ( pParser->stream_type == DVB_STREAM_TYPE )
		strcpy( tmp,  "DVB " );
	else
		strcpy( tmp, "unknown" );
	if ( strlen( tmp ) + strlen( Buf ) < BufSize )	strcat( Buf, tmp );

	if ( pParser->stream_type == ATSC_STREAM_TYPE )
	{
		channel_num = pParser->VCTNum;
		for ( i = 0; i<pParser->VCTNum; i++ )
			if ( pParser->vct[i].flag )
				valid_channel_num++;

	}


	if ( pParser->stream_type == ATSC_STREAM_TYPE )
	{
		channel_num = pParser->SVTNum;
		for ( i = 0; i<pParser->VCTNum; i++ )
			if ( pParser->vct[i].flag )
				valid_channel_num++;
	} else
	if ( pParser->stream_type == DVB_STREAM_TYPE )
	{
		channel_num = pParser->SVTNum;
		for ( i = 0; i<pParser->SVTNum; i++ )
		{
			if (  pParser->svt[i].flag > 0  )
			{
				int service_type;
				valid_channel_num++;
				service_type = GetProgramAVType( pParser->pTSParser, pParser->svt[i].service_id );
				if ( service_type == 1 || service_type == 3 ) //service_type == 2 auido only
					found_valid_channel_num++;
					
			}
		}
	}

	sprintf( tmp, "ch:%d valid ch:%d found ch:%d ", 
		                      channel_num, valid_channel_num, found_valid_channel_num ); 
	if ( strlen( tmp ) + strlen( Buf ) < BufSize )	strcat( Buf, tmp );


	if ( pParser->stream_type == ATSC_STREAM_TYPE )
	{
		sprintf( tmp, "mgt-num:%d VCT:%d MGT:%d EIT:%d ETT:%d", (int)pParser->MGTNum, (int)pParser->VCTSectionCount, 
						     (int)pParser->MGTSectionCount, (int)pParser->EITSectionCount, 
						     (int)pParser->ETTSectionCount );
		if ( strlen( tmp ) + strlen( Buf ) < BufSize )	strcat( Buf, tmp );
	} else
	if ( pParser->stream_type == DVB_STREAM_TYPE )
	{
		sprintf( tmp, "SDT:%d nit#:%d NIT:%d EIT:%d", (int)pParser->SDTSectionCount,
							(int)pParser->nit.NIT_inf_num, (int)pParser->NITSectionCount,  
							(int)pParser->DETISectionCount );
		if ( strlen( tmp ) + strlen( Buf ) < BufSize )	strcat( Buf, tmp );
	}

/*{
	char buf[1024]; int i;
	//TS_PAT* pat = &pParser->pTSParser->Pat;

	buf[0] = 0;
	for ( i = 0; i<MAX_TSID_NUM && pParser->tsid_tbl[i]; i++ )
	{
		if ( strlen(buf)+8 <sizeof(buf) )
			sprintf( buf+strlen(buf), "%d ", pParser->tsid_tbl[i] ); 
	}
	flog(( "native.log", "@@@@ tsid:%s\n", buf ));
	buf[0] = 0;
	sprintf( buf, "SVT:%d|", pParser->SVTNum );
	for ( i = 0; i<pParser->SVTNum; i++ )
	{
		int service_type = GetProgramAVType( pParser->pTSParser, pParser->svt[i].service_id );
		if ( strlen(buf)+60 <sizeof(buf) )
			sprintf( buf+strlen(buf), "(%s,%d %d %x,%d)", pParser->svt[i].Name, pParser->svt[i].flag, 
			        pParser->svt[i].service_type, pParser->svt[i].service_id, service_type ); 
	}
	flog(( "native.log", "@@@@%s\n", buf ));

	buf[0] = 0;
	sprintf( buf, "@@@@CT:SI:%d NIT:%d SDT:%d (%d %d %d)\n", pParser->SIct, pParser->NITct, pParser->SDTct,
		                  pParser->SDTct2, pParser->SDTct3, pParser->SDTct4 );
	flog(( "native.log", buf ));
} */


}

///////
