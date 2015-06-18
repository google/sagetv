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
#pragma warning(disable : 4996)


#include <stdio.h>
#include <time.h>
#include <stdarg.h>
#include <stdlib.h>
#include <memory.h>
#include <assert.h>
#include "TSParser.h"
#include "AVUtility.h" 

#include "SIParser.h"

#include <stdarg.h>
#include <time.h>

//static _log_data( char* txt )
//{
//	FILE* fp = fopen( "TSParser.log", "a+" );
//	if ( fp != NULL )
//	{
//		fprintf( fp, "%s\r\n", txt );
//		fclose( fp );
//	}
//}
#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _flog x
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

static bool flog_enabled=false;
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

//
//section data api
static unsigned long CalCrcCheck( const unsigned char *pData, int len );
static LONGLONG GetPCR( TS_PARSER* pParser, const unsigned char* pData );

//ts parser data api

static	bool  SearchTsHeader( const unsigned	char* pStart, int nCheckBytes, const unsigned char** ppHeader );

static	bool  UnpackTsHeader( TS_PARSER* pParser, const unsigned	char* pHeader, const unsigned char** pPlayload );
static	bool  UnpackPAT( TS_PARSER* pParser, const unsigned char* pStart, int nSize, unsigned short tsid_filter	);
static	unsigned short  UnpackPMT( TS_PARSER* pParser, const unsigned char* pStart, int nSize, short ProgramNum, short* PmtIndex, TS_SECTION** pSection );
static	bool  IsPMTPacket( TS_PARSER* pParser, short	Pid, short *pProgram );	
//static	short LookupProgramInPmt( TS_PARSER* pParser, short ProgramNum );
static  bool  IsSelectedProgram( TS_PARSER* pParser, short Pid, unsigned char *StreamType );
static	int   SeekPmtByChannel( TS_PARSER* pParser, short ChannelNum );
_inline int   SeekPmtByProgram( TS_PARSER* pParser, unsigned short Program ); 
static  short GetChannelNum( TS_PARSER* pParser, short ProgramNum );
//static  bool  IsSelectedChannel( TS_PARSER* pParser, short ChannelNum );
static  int   SetSeletedChannelByProgramNum( TS_PARSER* pParser, unsigned short ProgramNum );
static  int   ParseData( TS_PARSER* pParser, const unsigned char* pStartData, long dwBytes );
static  void  CalcStreamPacketInterval( TS_PARSER* pParser, LONGLONG PCR, unsigned long packets );
static void SavePAT( TS_PARSER* pParser, TS_PAT* Pat );
int static LookUpPidHit( TS_PARSER* pParser, unsigned pid );
static unsigned short GetSelectedProgramPcrPID( TS_PARSER* pParser );
static void NotifyPcrPID( TS_PARSER* pParser );

static void _log_error( char* mesg )
{
	FILE* fp=fopen( "ErrorCatch.log", "a+");
	if ( fp != NULL )
	{
		fprintf( fp, "%s.", mesg );
		fflush( fp );
		fclose( fp );
	}
}
#define DEBUG_MALLOC 1
#ifdef DEBUG_MALLOC
/*************************************************************/
#define SAGETV_MALLOC( x )		sagetv_malloc2( x, __LINE__ )
#define SAGETV_FREE( x )		sagetv_free2( x,  __LINE__ )
/*************************************************************/
#else
/*************************************************************/
#define SAGETV_MALLOC( x )		sagetv_malloc( x )
#define SAGETV_FREE( x )		sagetv_free( x )
/*************************************************************/
#endif

/* put memory alloc under control (leak check) */
#ifdef  MEMORY_CHECK
#define MAX_ALLOC_BLOCK 1024
unsigned long _totla_memory_alloc = 0;
void* alloc_ptr_tbl[MAX_ALLOC_BLOCK]={0};
void* alloc_size_tbl[MAX_ALLOC_BLOCK]={0};
int alloc_num = 0;
bool overflow_flag = false;
static void _raise_exception()
{
	char *p;
	p = (char*)01;
	*p = 99;  //raise a exception for Dr.Watson
}


_inline void* sagetv_malloc( int size )
{
	int i = 0;
	char* p = (char*)malloc( size );
	if ( p ) memset( p, 0, size );
	else
	{
		_log_error( "Out of memory in TSParser" )
	}

	for ( i=0; i<MAX_ALLOC_BLOCK; i++ )
		if ( alloc_ptr_tbl[i] == 0 )
			break;

	if ( i<MAX_ALLOC_BLOCK )
	{
		alloc_ptr_tbl[i] = p;
		alloc_size_bl[i] = size;
	} else
		overflow_flag = true;

	return (void*)p; 

}

_inline void sagetv_free( void* p  )
{
	int i;
	if ( !overflow_flag )
	{
		for ( i=0; i<MAX_ALLOC_BLOCK; i++ )
			if ( p == alloc_ptr_tbl[i] )
				break;
		
		if ( i<MAX_ALLOC_BLOCK )
		{
			_totla_memory_alloc -= alloc_size_bl[i];
			alloc_ptr_tbl[i] = 0;
			alloc_size_bl[i] = 0;
		} else
		{
			printf( "try to free unkown memory block 0x%x\n", p );
		}
	}
	free( p );
}


#else
_inline void* sagetv_malloc( int size )
{
	char* p = (char*)malloc( size );
	if ( p ) memset( p, 0, size );
	return (void*)p; 
}

_inline void  sagetv_free( void* p )
{
	free( p );
}
#endif

_inline void* sagetv_malloc2( int size, int line )
{
	char* p;;
	if ( size < 0 || size > 1024*64 )
	{
		char buf[128];
		sprintf( buf, "!!!Memeory alloc size invalid size:%d TSParser.c::line %d\n", size, line ); 
		_log_error( buf );
#ifdef DEBUG_LINUX
		flog(( "native.log", buf ));
#endif
		return NULL;
	}

	p = (char*)malloc( size );
	if ( p ) memset( p, 0, size );
	else
	{
		char buf[128];
		sprintf( buf, "!!!Memeory alloc failed  size:%d TSParser.c::line %d\n", size, line ); 
		_log_error( buf );
#ifdef DEBUG_LINUX
		flog(( "native.log", buf ));
#endif
	}

	return (void*)p; 
}

_inline void  sagetv_free2( void* p )
{
	free( p );
}

/////////////////////////////////////////////////////////////////////////////////////////
//section data processing
/////////////////////////////////////////////////////////////////////////////////////////

void StartSectionData( TS_SECTION* pSection, int nSectionLength )
{
	pSection->total_bytes = 0;
	pSection->total_bytes = nSectionLength + 4; // 4 Byte CRC
	pSection->bytes = 0;
	//memset( pSection->data, 0x00, pSection->data_size );
}


bool PushSectionData( TS_SECTION* pSection, char* pData, int Bytes )
{
	Bytes = _MIN( pSection->total_bytes - pSection->bytes, Bytes );
	if ( Bytes > 0 )
	{
		memcpy( pSection->data + pSection->bytes, pData, Bytes );
		pSection->bytes += Bytes;
	}
	return  ( pSection->bytes == pSection->total_bytes );
}


bool BuildSectionData( TS_SECTION* pSection, int nDataLength, char* pData )
{
	unsigned long crc;
	unsigned char *p;
	pSection->total_bytes = 0;
	if ( nDataLength+4 > pSection->data_size  )
		return false;
	pSection->total_bytes = nDataLength + 4;  //add 4 bytes for crc32
	pSection->bytes = 0;
	memcpy( pSection->data, pData, nDataLength );
	crc = CalCrcCheck( (const unsigned char*)pSection->data, nDataLength );
	p = (unsigned char *)( pSection->data + nDataLength );
	*(p++) = (unsigned char)((crc >> 24) & 0xff);
	*(p++) = (unsigned char)(crc >> 16) & 0xff;
	*(p++) = (unsigned char)(crc >> 8) & 0xff;
	*(p++) = (unsigned char)(crc) &  0xff;
	return true;
}


int PopSectionData( TS_SECTION* pSection, char* pData, int Bytes )
{
	Bytes = _MIN( pSection->total_bytes - pSection->bytes, Bytes );
	if ( Bytes > 0 )
	{
		memcpy( pData, pSection->data + pSection->bytes, Bytes );
		pSection->bytes += Bytes;
	}
	return  Bytes;
}


bool SectionCrcCheck( TS_SECTION* pSection )
{
	return ( CalCrcCheck( (const unsigned char*)pSection->data, pSection->total_bytes ) == 0 );
}


static unsigned long crc32_table[256] =
{
  0x00000000, 0x04c11db7, 0x09823b6e, 0x0d4326d9,
  0x130476dc, 0x17c56b6b, 0x1a864db2, 0x1e475005,
  0x2608edb8, 0x22c9f00f, 0x2f8ad6d6, 0x2b4bcb61,
  0x350c9b64, 0x31cd86d3, 0x3c8ea00a, 0x384fbdbd,
  0x4c11db70, 0x48d0c6c7, 0x4593e01e, 0x4152fda9,
  0x5f15adac, 0x5bd4b01b, 0x569796c2, 0x52568b75,
  0x6a1936c8, 0x6ed82b7f, 0x639b0da6, 0x675a1011,
  0x791d4014, 0x7ddc5da3, 0x709f7b7a, 0x745e66cd,
  0x9823b6e0, 0x9ce2ab57, 0x91a18d8e, 0x95609039,
  0x8b27c03c, 0x8fe6dd8b, 0x82a5fb52, 0x8664e6e5,
  0xbe2b5b58, 0xbaea46ef, 0xb7a96036, 0xb3687d81,
  0xad2f2d84, 0xa9ee3033, 0xa4ad16ea, 0xa06c0b5d,
  0xd4326d90, 0xd0f37027, 0xddb056fe, 0xd9714b49,
  0xc7361b4c, 0xc3f706fb, 0xceb42022, 0xca753d95,
  0xf23a8028, 0xf6fb9d9f, 0xfbb8bb46, 0xff79a6f1,
  0xe13ef6f4, 0xe5ffeb43, 0xe8bccd9a, 0xec7dd02d,
  0x34867077, 0x30476dc0, 0x3d044b19, 0x39c556ae,
  0x278206ab, 0x23431b1c, 0x2e003dc5, 0x2ac12072,
  0x128e9dcf, 0x164f8078, 0x1b0ca6a1, 0x1fcdbb16,
  0x018aeb13, 0x054bf6a4, 0x0808d07d, 0x0cc9cdca,
  0x7897ab07, 0x7c56b6b0, 0x71159069, 0x75d48dde,
  0x6b93dddb, 0x6f52c06c, 0x6211e6b5, 0x66d0fb02,
  0x5e9f46bf, 0x5a5e5b08, 0x571d7dd1, 0x53dc6066,
  0x4d9b3063, 0x495a2dd4, 0x44190b0d, 0x40d816ba,
  0xaca5c697, 0xa864db20, 0xa527fdf9, 0xa1e6e04e,
  0xbfa1b04b, 0xbb60adfc, 0xb6238b25, 0xb2e29692,
  0x8aad2b2f, 0x8e6c3698, 0x832f1041, 0x87ee0df6,
  0x99a95df3, 0x9d684044, 0x902b669d, 0x94ea7b2a,
  0xe0b41de7, 0xe4750050, 0xe9362689, 0xedf73b3e,
  0xf3b06b3b, 0xf771768c, 0xfa325055, 0xfef34de2,
  0xc6bcf05f, 0xc27dede8, 0xcf3ecb31, 0xcbffd686,
  0xd5b88683, 0xd1799b34, 0xdc3abded, 0xd8fba05a,
  0x690ce0ee, 0x6dcdfd59, 0x608edb80, 0x644fc637,
  0x7a089632, 0x7ec98b85, 0x738aad5c, 0x774bb0eb,
  0x4f040d56, 0x4bc510e1, 0x46863638, 0x42472b8f,
  0x5c007b8a, 0x58c1663d, 0x558240e4, 0x51435d53,
  0x251d3b9e, 0x21dc2629, 0x2c9f00f0, 0x285e1d47,
  0x36194d42, 0x32d850f5, 0x3f9b762c, 0x3b5a6b9b,
  0x0315d626, 0x07d4cb91, 0x0a97ed48, 0x0e56f0ff,
  0x1011a0fa, 0x14d0bd4d, 0x19939b94, 0x1d528623,
  0xf12f560e, 0xf5ee4bb9, 0xf8ad6d60, 0xfc6c70d7,
  0xe22b20d2, 0xe6ea3d65, 0xeba91bbc, 0xef68060b,
  0xd727bbb6, 0xd3e6a601, 0xdea580d8, 0xda649d6f,
  0xc423cd6a, 0xc0e2d0dd, 0xcda1f604, 0xc960ebb3,
  0xbd3e8d7e, 0xb9ff90c9, 0xb4bcb610, 0xb07daba7,
  0xae3afba2, 0xaafbe615, 0xa7b8c0cc, 0xa379dd7b,
  0x9b3660c6, 0x9ff77d71, 0x92b45ba8, 0x9675461f,
  0x8832161a, 0x8cf30bad, 0x81b02d74, 0x857130c3,
  0x5d8a9099, 0x594b8d2e, 0x5408abf7, 0x50c9b640,
  0x4e8ee645, 0x4a4ffbf2, 0x470cdd2b, 0x43cdc09c,
  0x7b827d21, 0x7f436096, 0x7200464f, 0x76c15bf8,
  0x68860bfd, 0x6c47164a, 0x61043093, 0x65c52d24,
  0x119b4be9, 0x155a565e, 0x18197087, 0x1cd86d30,
  0x029f3d35, 0x065e2082, 0x0b1d065b, 0x0fdc1bec,
  0x3793a651, 0x3352bbe6, 0x3e119d3f, 0x3ad08088,
  0x2497d08d, 0x2056cd3a, 0x2d15ebe3, 0x29d4f654,
  0xc5a92679, 0xc1683bce, 0xcc2b1d17, 0xc8ea00a0,
  0xd6ad50a5, 0xd26c4d12, 0xdf2f6bcb, 0xdbee767c,
  0xe3a1cbc1, 0xe760d676, 0xea23f0af, 0xeee2ed18,
  0xf0a5bd1d, 0xf464a0aa, 0xf9278673, 0xfde69bc4,
  0x89b8fd09, 0x8d79e0be, 0x803ac667, 0x84fbdbd0,
  0x9abc8bd5, 0x9e7d9662, 0x933eb0bb, 0x97ffad0c,
  0xafb010b1, 0xab710d06, 0xa6322bdf, 0xa2f33668,
  0xbcb4666d, 0xb8757bda, 0xb5365d03, 0xb1f740b4
};


#ifdef CRC32_TBALE
//testing
static unsigned long crc32_table2[256] = {
	0x00000000L, 0x77073096L, 0xee0e612cL, 0x990951baL, 0x076dc419L,
	0x706af48fL, 0xe963a535L, 0x9e6495a3L, 0x0edb8832L, 0x79dcb8a4L,
	0xe0d5e91eL, 0x97d2d988L, 0x09b64c2bL, 0x7eb17cbdL, 0xe7b82d07L,
	0x90bf1d91L, 0x1db71064L, 0x6ab020f2L, 0xf3b97148L, 0x84be41deL,
	0x1adad47dL, 0x6ddde4ebL, 0xf4d4b551L, 0x83d385c7L, 0x136c9856L,
	0x646ba8c0L, 0xfd62f97aL, 0x8a65c9ecL, 0x14015c4fL, 0x63066cd9L,
	0xfa0f3d63L, 0x8d080df5L, 0x3b6e20c8L, 0x4c69105eL, 0xd56041e4L,
	0xa2677172L, 0x3c03e4d1L, 0x4b04d447L, 0xd20d85fdL, 0xa50ab56bL,
	0x35b5a8faL, 0x42b2986cL, 0xdbbbc9d6L, 0xacbcf940L, 0x32d86ce3L,
	0x45df5c75L, 0xdcd60dcfL, 0xabd13d59L, 0x26d930acL, 0x51de003aL,
	0xc8d75180L, 0xbfd06116L, 0x21b4f4b5L, 0x56b3c423L, 0xcfba9599L,
	0xb8bda50fL, 0x2802b89eL, 0x5f058808L, 0xc60cd9b2L, 0xb10be924L,
	0x2f6f7c87L, 0x58684c11L, 0xc1611dabL, 0xb6662d3dL, 0x76dc4190L,
	0x01db7106L, 0x98d220bcL, 0xefd5102aL, 0x71b18589L, 0x06b6b51fL,
	0x9fbfe4a5L, 0xe8b8d433L, 0x7807c9a2L, 0x0f00f934L, 0x9609a88eL,
	0xe10e9818L, 0x7f6a0dbbL, 0x086d3d2dL, 0x91646c97L, 0xe6635c01L,
	0x6b6b51f4L, 0x1c6c6162L, 0x856530d8L, 0xf262004eL, 0x6c0695edL,
	0x1b01a57bL, 0x8208f4c1L, 0xf50fc457L, 0x65b0d9c6L, 0x12b7e950L,
	0x8bbeb8eaL, 0xfcb9887cL, 0x62dd1ddfL, 0x15da2d49L, 0x8cd37cf3L,
	0xfbd44c65L, 0x4db26158L, 0x3ab551ceL, 0xa3bc0074L, 0xd4bb30e2L,
	0x4adfa541L, 0x3dd895d7L, 0xa4d1c46dL, 0xd3d6f4fbL, 0x4369e96aL,
	0x346ed9fcL, 0xad678846L, 0xda60b8d0L, 0x44042d73L, 0x33031de5L,
	0xaa0a4c5fL, 0xdd0d7cc9L, 0x5005713cL, 0x270241aaL, 0xbe0b1010L,
	0xc90c2086L, 0x5768b525L, 0x206f85b3L, 0xb966d409L, 0xce61e49fL,
	0x5edef90eL, 0x29d9c998L, 0xb0d09822L, 0xc7d7a8b4L, 0x59b33d17L,
	0x2eb40d81L, 0xb7bd5c3bL, 0xc0ba6cadL, 0xedb88320L, 0x9abfb3b6L,
	0x03b6e20cL, 0x74b1d29aL, 0xead54739L, 0x9dd277afL, 0x04db2615L,
	0x73dc1683L, 0xe3630b12L, 0x94643b84L, 0x0d6d6a3eL, 0x7a6a5aa8L,
	0xe40ecf0bL, 0x9309ff9dL, 0x0a00ae27L, 0x7d079eb1L, 0xf00f9344L,
	0x8708a3d2L, 0x1e01f268L, 0x6906c2feL, 0xf762575dL, 0x806567cbL,
	0x196c3671L, 0x6e6b06e7L, 0xfed41b76L, 0x89d32be0L, 0x10da7a5aL,
	0x67dd4accL, 0xf9b9df6fL, 0x8ebeeff9L, 0x17b7be43L, 0x60b08ed5L,
	0xd6d6a3e8L, 0xa1d1937eL, 0x38d8c2c4L, 0x4fdff252L, 0xd1bb67f1L,
	0xa6bc5767L, 0x3fb506ddL, 0x48b2364bL, 0xd80d2bdaL, 0xaf0a1b4cL,
	0x36034af6L, 0x41047a60L, 0xdf60efc3L, 0xa867df55L, 0x316e8eefL,
	0x4669be79L, 0xcb61b38cL, 0xbc66831aL, 0x256fd2a0L, 0x5268e236L,
	0xcc0c7795L, 0xbb0b4703L, 0x220216b9L, 0x5505262fL, 0xc5ba3bbeL,
	0xb2bd0b28L, 0x2bb45a92L, 0x5cb36a04L, 0xc2d7ffa7L, 0xb5d0cf31L,
	0x2cd99e8bL, 0x5bdeae1dL, 0x9b64c2b0L, 0xec63f226L, 0x756aa39cL,
	0x026d930aL, 0x9c0906a9L, 0xeb0e363fL, 0x72076785L, 0x05005713L,
	0x95bf4a82L, 0xe2b87a14L, 0x7bb12baeL, 0x0cb61b38L, 0x92d28e9bL,
	0xe5d5be0dL, 0x7cdcefb7L, 0x0bdbdf21L, 0x86d3d2d4L, 0xf1d4e242L,
	0x68ddb3f8L, 0x1fda836eL, 0x81be16cdL, 0xf6b9265bL, 0x6fb077e1L,
	0x18b74777L, 0x88085ae6L, 0xff0f6a70L, 0x66063bcaL, 0x11010b5cL,
	0x8f659effL, 0xf862ae69L, 0x616bffd3L, 0x166ccf45L, 0xa00ae278L,
	0xd70dd2eeL, 0x4e048354L, 0x3903b3c2L, 0xa7672661L, 0xd06016f7L,
	0x4969474dL, 0x3e6e77dbL, 0xaed16a4aL, 0xd9d65adcL, 0x40df0b66L,
	0x37d83bf0L, 0xa9bcae53L, 0xdebb9ec5L, 0x47b2cf7fL, 0x30b5ffe9L,
	0xbdbdf21cL, 0xcabac28aL, 0x53b39330L, 0x24b4a3a6L, 0xbad03605L,
	0xcdd70693L, 0x54de5729L, 0x23d967bfL, 0xb3667a2eL, 0xc4614ab8L,
	0x5d681b02L, 0x2a6f2b94L, 0xb40bbe37L, 0xc30c8ea1L, 0x5a05df1bL,
	0x2d02ef8dL
};
#endif
//
unsigned long CalCrcCheck( const unsigned char *pData, int len )
{
    unsigned long  crc = 0xffffffff;
    unsigned char* p_byte = (unsigned char*)pData;

    while( len-- )
    {
      crc = (crc << 8) ^ crc32_table[ (crc >> 24) ^ (*p_byte) ];
      p_byte++;
    }
    return crc;
}

LONGLONG GetPCR( TS_PARSER* pParser, const unsigned char* pkt )
{
	LONGLONG PCR;
	LONGLONG PCR_base, PCR_ext;
	PCR_base =  ((LONGLONG)(0xff & pkt[0]) << 25);
	PCR_base |= ((LONGLONG)(0xff & pkt[1]) << 17);
	PCR_base |= ((LONGLONG)(0xff & pkt[2]) << 9 );
	PCR_base |= ((LONGLONG)(0xff & pkt[3]) << 1 );
	PCR_base |= ((LONGLONG)(0x80 & pkt[4]) >> 7);
	PCR_ext = ((LONGLONG)(pkt[4] & 0x7f) << 8);
	PCR_ext |= ((LONGLONG)(0xff & pkt[5]));
	PCR = (LONGLONG)300* PCR_base + PCR_ext;
	return PCR;
}

unsigned short GetProgramPcrPID( TS_PARSER* pParser )
{
	return pParser->pcr_pid;
}

static unsigned short GetSelectedProgramPcrPID( TS_PARSER* pParser )
{
	short index;

	index = SeekPmtByProgram( pParser, pParser->WorkingProgram );
	if ( index < 0 ) return 0;

	if ( pParser->Pmt[index].PCRPID == 0x1fff )
	{
		int i, j;
		for ( i = 0; i< pParser->Pmt[index].NumStreams; i++ )
		{
			for ( j = 0; j<MAX_PCR_PIDS; j++ )
				if (  pParser->Pmt[index].ElementaryPID[i] == pParser->pcr_pids[j] )
					return  pParser->Pmt[index].ElementaryPID[i];
		}

	} else
		return pParser->Pmt[index].PCRPID;

	return 0;
}

static void RegisterPCRPid( TS_PARSER* pParser, unsigned short pid )
{
	int i;
	for ( i = 0; i<MAX_PCR_PIDS; i++ )
		if ( pParser->pcr_pids[i] == 0 )
		{
			pParser->pcr_pids[i] = pid;
			i++; //in case of table overflow 
			if ( i >= MAX_PCR_PIDS ) i = 0;
			pParser->pcr_pids[i] = 0;
			break;
		}
}

static void ResetPCRPid( TS_PARSER* pParser )
{
	memset( pParser->pcr_pids, 0, sizeof( pParser->pcr_pids ) );
}

static void NotifyPcrPID( TS_PARSER* pParser )
{
	if ( pParser->signal_dumper )
	{
		SIGNAL_INFO Signal;
		Signal.type = PCR_READY;
		Signal.pid  = pParser->pcr_pid;
		pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
	}
}

bool IsValidPcrPID( TS_PARSER* pParser, unsigned short pid )
{
	int i;
	for ( i = 0; i<MAX_PCR_PIDS; i++ )
	{
		if ( pid == pParser->pcr_pids[i] )
			break;
	}

	return pid == pParser->pcr_pids[i]; 
}

//if PCR indicated in PMT isn't a valid, we need reselect one
unsigned short ReselectPCRPID( TS_PARSER* pParser )
{
	int i,j;
	short index;

	index = SeekPmtByProgram( pParser, pParser->WorkingProgram );
	if ( index < 0 ) return 0x0;

	if ( pParser->Pmt[index].PCRPID !=  0x1fff )
	{
		for ( j = 0; j<MAX_PCR_PIDS; j++ )
			if ( pParser->Pmt[index].PCRPID == pParser->pcr_pids[j] )
				return pParser->Pmt[index].PCRPID;
	}

	for ( i = 0; i< pParser->Pmt[index].NumStreams; i++ )
	{
		for ( j = 0; j<MAX_PCR_PIDS; j++ )
			if (  pParser->Pmt[index].ElementaryPID[i] == pParser->pcr_pids[j] )
				return  pParser->Pmt[index].ElementaryPID[i];
	}
	return  0x0;
}

	
	//double clock = pcrBaseHigh/45000.0;
	//if ((pkt[10]&0x80) != 0) clock += 1/90000.0; // add in low-bit (if set)
	//clock += pcrExt/27000000.0;

//first	TS packet, we need 2*TS_PACKET_LENGTH(188) bytes data to help find correct TS header
bool SearchTsHeader( const unsigned	char* pStart, int nCheckBytes, const unsigned char** ppHeader )	
{
	*ppHeader = NULL;
	while ( (nCheckBytes-- - TS_PACKET_LENGTH) > 0 ) {
		*ppHeader = pStart;
		if ( *pStart ==	SYNC &&	*(pStart+TS_PACKET_LENGTH )	== SYNC	)
			return true;
		pStart++;
	}
	return false;
}


bool IsSelectedProgram( TS_PARSER* pParser, short Pid, unsigned char *StreamType ) 
{
	int	i;
	short index;

	index = SeekPmtByProgram( pParser, pParser->WorkingProgram );
	if ( index >= 0 ) 
	{
		for	( i	= 0; i<pParser->Pmt[index].NumStreams;	i++	)
			if ( pParser->Pmt[index].ElementaryPID[i] == Pid )	
			{
				*StreamType	= pParser->Pmt[index].StreamType[i];
				return true;
			}
	}

	return false;
}


//bool IsSelectedProgram( TS_PARSER* pParser, short Pid, unsigned char *StreamType, short *ChannelNum ) 
//{
//	int	i, k;
//	short index;
//
//	for ( k = 0; k<pParser->SelectedChannelNum; k++ )
//	{	
//		//index = SeekPmt( pParser, pParser->SelectedChannel[k] ); //ZQ****
//		index = GetChannelNum( pParser, pParser->SelectedProgram[k] );
//		if ( index >= 0 ) 
//		{
//			for	( i	= 0; i<pParser->Pmt[index].NumStreams;	i++	)
//				if ( pParser->Pmt[index].ElementaryPID[i] == Pid )	
//				{
//					*StreamType	= pParser->Pmt[index].StreamType[i];
//					*ChannelNum = pParser->SelectedChannel[k];
//					return true;
//				}
//		}
//	}
//
//	return false;
//}

short GetProgramNum( TS_PARSER* pParser, short ChannelNum )
{
	if ( ChannelNum >= MAX_PROGRAM )
		return -1;
									   
	return pParser->Channel2Program[ChannelNum] ;
}

short GetChannelNum( TS_PARSER* pParser, short ProgramNum )
{
	int i;

	for ( i = 0; i<MAX_PROGRAM; i++ )
		if ( pParser->Channel2Program[i] == (unsigned short)ProgramNum )
			return i;

	return -1;
}

_inline int SeekPmtByChannel( TS_PARSER* pParser, short ChannelNum )
{
	register int	i;
	unsigned short Program;

	if ( ChannelNum >=	MAX_PROGRAM	|| ChannelNum < 0 )
		return -1;

	Program = pParser->Channel2Program[ChannelNum];

	for	( i	= 0; i<MAX_PROGRAM;	i++	)
		if ( pParser->Pmt[i].ProgramNumber && pParser->Pmt[i].ProgramNumber == Program )	
			return i;

	return -1;
}

_inline int SeekPmtByProgram( TS_PARSER* pParser, unsigned short Program )
{
	register int	i;
	for	( i	= 0; i<MAX_PROGRAM;	i++	)
		if ( pParser->Pmt[i].ProgramNumber && pParser->Pmt[i].ProgramNumber == Program )	
			return i;

	return -1;
}

void ReleasePMTdata( TS_PMT* pPmt )
{
	int i;
	if ( pPmt->ProgramInfo	)
	{
		sagetv_free( pPmt->ProgramInfo );
		pPmt->ProgramInfo = NULL;
	}

	for	( i	= 0; i<MAX_ES; i++ )
	{
		if ( pPmt->ESInfo[i] )
		{
			sagetv_free( pPmt->ESInfo[i] );
			pPmt->ESInfo[i] = NULL;
			pPmt->ESInfoLength[i] = 0;
		}
	}
}

static bool IsChannelInPAT( TS_PARSER* pParser )
{
	if ( pParser->WorkingProgram != 0xffff && pParser->WorkingProgram != 0x0 )
	{
		int i;
		for	( i = 0; i<pParser->Pat.NumPrograms;	i++	)
		{
			if ( pParser->Pat.ProgramNumber[ i ] == pParser->WorkingProgram )	
				return true;
		}
		return false;
	}else
	if ( pParser->SelectedChannel[0] != -1 )
	{
		return pParser->Pat.NumPrograms >= pParser->SelectedChannel[0];
		//return SeekPmtByChannel( pParser, pParser->SelectedChannel[0] ) >= 0;
	} 

	return true;
}


TS_PARSER* TSParserOpen() 
{
	int i;
	TS_PARSER* pParser;
	pParser = (TS_PARSER* )sagetv_malloc( sizeof(TS_PARSER) );

	pParser->abort = true;
	pParser->packets	= 0;
	pParser->audio_frames = 0;
	pParser->video_frames = 0;
	pParser->pat_frames = 0;	
	pParser->pmt_frames = 0;	
	pParser->cat_frames = 0;	
	pParser->err_frames = 0;	
	pParser->pats_num = 0;
	pParser->pats_index = 0;

	memset(	&pParser->Pat,	0, sizeof(pParser->Pat) );
	memset(	&pParser->Pmt,	0, sizeof(pParser->Pmt) );
	pParser->Pat.section.data = (char*)sagetv_malloc( 1024 );
	pParser->Pat.section.data_size = 1024;

	for	( i = 0; i<MAX_PROGRAM;	i++	)
	{
		pParser->Pmt[i].section = NULL;
		pParser->SelectedChannel[i] = -1;
		pParser->SelectedProgram[i] = -1;
		pParser->WorkingProgram = 0;
	}

	for	( i = 0; i<MAX_PID_COLLECT; i++	)
	{
		pParser->pid_array[i].state = 0;
		pParser->pid_array[i].hits = 0;
		pParser->pid_array[i].type = 0;
		pParser->pid_array[i].pid = -1;
		pParser->pid_array[i].counter = 0;
		pParser->pid_array[i].scrambled =0;
	}
	memset( pParser->pcr_pids, 0, sizeof(pParser->pcr_pids) );

	pParser->SelectedTSID = 0;
	pParser->WorkingTSID = 0;
	pParser->SelectedChannelNum = 0;
	pParser->SelectedProgramNum = 0;
	pParser->TSChannelNum = 0;
	pParser->TSBytes = 0;
	pParser->pcr_pid = 0;

	pParser->signal_dumper = 0;
	pParser->av_dumper = 0;
	pParser->si_dumper = 0;
	pParser->section_dumper = 0;
	pParser->PAT_dumper = 0;
	pParser->PMT_dumper = 0; 
	pParser->section_dumper_context = NULL;
	pParser->PMT_dumper_context = NULL;
	pParser->PMT_dumper_context = NULL;
	pParser->PatState = 0;
	pParser->LockPmtPat = 0;
	pParser->enable_pid_stat = true;

	pParser->packet_pcr_rate = 2090;				   
	pParser->pcr_packet_syn = 0;						
	pParser->StreamIntervalSum = 0;              
	pParser->StreamIntervalNum = 0;  		
	memset( pParser->ESPidMap, 0, sizeof(pParser->ESPidMap) );


//ZQ test, remove if after test
/*{
SI_PARSER *SIparser;
printf( "testing SI parer\n" );
SIparser = SIParerOpen( pParser, 0 );
pParser->si_env = SIparser;
pParser->si_dumper = SI_dumper;
}*/

	_flog_check();
	return pParser;
}

void TSPidStatEnable( TS_PARSER* pParser, bool bEnable )
{
	if ( pParser == NULL )
		return;
	pParser->enable_pid_stat = bEnable;
}

void TSParserClose( TS_PARSER* pParser ) 
{
	int	i,j;
	pParser->abort = true;
	if ( pParser->signal_dumper )
	{
		SIGNAL_INFO Signal;
		Signal.type = STREAM_CLOSE;
		Signal.pid  = 0;
		pParser->signal_dumper( (void*)pParser, 0, &Signal ); 
	}
	
	if ( pParser->Pat.section.data != NULL  )
	{
		sagetv_free( pParser->Pat.section.data );
		pParser->Pat.section.data = NULL;
	}
	for	( j	= 0; j<MAX_PROGRAM;	j++	)
	{
		ReleasePMTdata( &pParser->Pmt[j] );

		if ( pParser->Pmt[j].section != NULL  )	
		{ 
			if ( pParser->Pmt[j].section->data ) 
			{
				sagetv_free( pParser->Pmt[j].section->data ); 
				pParser->Pmt[j].section->data = NULL;
			}
			sagetv_free( pParser->Pmt[j].section ); 
			pParser->Pmt[j].section = NULL;
		}
	}

	for	( i	= 0; i<MAX_PROGRAM;	i++	)
	{
		if ( pParser->pid_array[i].section.data != NULL )
		{
			sagetv_free( pParser->pid_array[i].section.data );
				pParser->pid_array[i].section.data = NULL;
		}
	}

//ZQ test
/*{
bool ret = SIChannelInfoReady(  pParser->si_env );
int num = GetSIChannelNum( pParser->si_env );
CHANNEL_NAME* pChName;
int MaxSize = sizeof( CHANNEL_NAME ) * num;
pChName = sagetv_malloc( MaxSize );
ret = GetSIChannelName( pParser->si_env, pChName, MaxSize );
SIParserClose( pParser->si_env );
}*/

	if ( pParser != NULL )
		sagetv_free( pParser );

}

void  ResetParser( TS_PARSER* pParser )
{ 
	int i, j;
	if ( pParser == NULL )
		return;

	SIParserReset( pParser->si_env ); 

	if ( pParser->Pat.section.data != NULL  )
	{
		sagetv_free( pParser->Pat.section.data );
		pParser->Pat.section.data = NULL;
	}
	for	( j	= 0; j<MAX_PROGRAM;	j++	)
	{
		ReleasePMTdata( &pParser->Pmt[j] );

		if ( pParser->Pmt[j].section != NULL  )	
		{ 
			if ( pParser->Pmt[j].section->data ) 
			{
				sagetv_free( pParser->Pmt[j].section->data ); 
				pParser->Pmt[j].section->data = NULL;
			}
			sagetv_free( pParser->Pmt[j].section ); 
			pParser->Pmt[j].section = NULL;
		}
	}

	for	( i	= 0; i<MAX_PROGRAM;	i++	)
	{
		if ( pParser->pid_array[i].section.data != NULL )
		{
			sagetv_free( pParser->pid_array[i].section.data );
				pParser->pid_array[i].section.data = NULL;
		}
	}

	pParser->packets	= 0;
	pParser->audio_frames = 0;
	pParser->video_frames = 0;
	pParser->pat_frames = 0;	
	pParser->pmt_frames = 0;	
	pParser->cat_frames = 0;	
	pParser->err_frames = 0;	
	pParser->pats_num = 0;
	pParser->pats_index = 0;

	memset( &pParser->Pat,	0, sizeof(pParser->Pat) );
	memset( &pParser->Pmt,	0, sizeof(pParser->Pmt) );

	for	( i	= 0; i<MAX_PROGRAM;	i++	)
	{
		pParser->SelectedChannel[i] = -1;
		pParser->SelectedProgram[i] = -1;
		pParser->Channel2Program[i] = 0;
		pParser->ChannelState[i] = 0;
	}

	for	( i	= 0; i<MAX_PID_COLLECT;	i++	)
	{
		pParser->pid_array[i].state = 0;
		pParser->pid_array[i].hits = 0;
		pParser->pid_array[i].type = 0;
		pParser->pid_array[i].pid = -1;
		pParser->pid_array[i].counter = 0;
		pParser->pid_array[i].scrambled = 0;
	}
	memset( pParser->pcr_pids, 0, sizeof(pParser->pcr_pids) );

	pParser->WorkingProgram = 0;
	pParser->SelectedTSID = 0;
	pParser->WorkingTSID = 0;
	pParser->SelectedChannelNum = 0;
	pParser->SelectedProgramNum = 0;
	pParser->TSChannelNum = 0;
	pParser->TSBytes = 0;
	pParser->PatState = 0;
	pParser->LockPmtPat = 0;
	pParser->enable_pid_stat = true;
	pParser->pcr_pid = 0;

	pParser->packet_pcr_rate = 2090;				   
	pParser->pcr_packet_syn = 0;						
	pParser->StreamIntervalSum = 0;              
	pParser->StreamIntervalNum = 0;  
	memset( pParser->ESPidMap, 0, sizeof(pParser->ESPidMap) );

	pParser->bChannelInfReady = false;
	pParser->pid_filter_num = 0;
	memset( pParser->pid_filter_tbl, 0, sizeof(pParser->pid_filter_tbl) );
}

	
bool UnpackTsHeader( TS_PARSER* pParser, const unsigned char* pHeader, const	unsigned char**	ppPayload )	
{
	pParser->TSHeader.sync	  =	pHeader[0];			
	pParser->TSHeader.error	  =	pHeader[1]&0x80	? true : false;	
	pParser->TSHeader.start	  =	pHeader[1]&0x40	? true : false;	
	pParser->TSHeader.priority =	pHeader[1]&0x20	? true : false;	
	pParser->TSHeader.pid	  =	pHeader[2] | (pHeader[1]&0x1f)<<8 ;	
	pParser->TSHeader.scrambling_ctr = ( pHeader[3] &	0xc0 )>> 6;	
	pParser->TSHeader.adaption_ctr  = ( pHeader[3] &	0x30 )>> 4;	
	pParser->TSHeader.continuity_ct = ( pHeader[3] &	0x0f );	
	pParser->PCR = pParser->OPCR = 0;
	pParser->TSHeader.pcr_flag = 0;
	
	if ( pParser->TSHeader.error )
	{
		return false;
	}

	// adaption_ctr: 00:no payload; 01: payload; 10: adaption; 11:adaption+payload  ZQ
	if ( pParser->TSHeader.adaption_ctr == 0 || pParser->TSHeader.adaption_ctr == 2 )	//there	is no payload in packet	
	{
	  *ppPayload = NULL;
	}
	  
	if ( pParser->TSHeader.adaption_ctr == 1 )
	  *ppPayload = pHeader + 4;	


	if ( pParser->TSHeader.adaption_ctr == 3 || pParser->TSHeader.adaption_ctr == 2)
	{
	  pParser->TSAdapation.apapt_len		=	pHeader[4];	
	  pParser->TSAdapation.discontinute		=	pHeader[5] & 0x80 ?	true : false;;
	  pParser->TSAdapation.random_acceess   =	pHeader[5] & 0x40 ?	true : false;;
	  pParser->TSAdapation.elem_stream_prio =	pHeader[5] & 0x20 ?	true : false;;
	  pParser->TSAdapation.flags		    =	pHeader[5] & 0x1f;

	  if ( ( pParser->TSAdapation.flags & 0x10 ) && pParser->TSAdapation.apapt_len > 6 )
	  {
		  pParser->PCR = GetPCR( pParser, &pHeader[6] );
		  pParser->TSHeader.pcr_flag = 1;
	  } 
	  
	  if ( ( pParser->TSAdapation.flags & 0x08 ) && pParser->TSAdapation.apapt_len > 6 )
	  {
		  if (  pParser->TSAdapation.flags & 0x10  )
	  		pParser->OPCR = GetPCR( pParser, &pHeader[6+6] );
		  else
		    pParser->OPCR = GetPCR( pParser, &pHeader[6] );
	  }

	  pParser->TSOption.splice =	pHeader[6+6+6];	
	  
	  if ( pParser->TSHeader.adaption_ctr == 3 )
	  {
		  *ppPayload  =  pHeader + 4 + pParser->TSAdapation.apapt_len + 1;	
		  if ( 4 + pParser->TSAdapation.apapt_len > TS_PACKET_LENGTH	)
			return false;
	  }
	}

	return true;
}

bool  PCRAvailable( TS_PARSER* pParser )
{
	return ( ( pParser->TSHeader.adaption_ctr == 3 || pParser->TSHeader.adaption_ctr == 2 ) 
		       && (pParser->TSAdapation.flags & 0x10 ) );
}
_inline bool CheckContinuity( TS_HEADER* pTSHeader, unsigned char* pContinuity )
{
	bool valid;
	if ( pTSHeader->start )
	{
		*pContinuity = pTSHeader->continuity_ct;
		return true;
	}

	(*pContinuity)++;
	(*pContinuity) &= 0x0f;
	valid = pTSHeader->continuity_ct == *pContinuity;
	return valid;

}

bool FlushSectionData( TS_SECTION* pSection )
{
	const unsigned char* section_data;
	int nSize;
	unsigned short section_length;

	nSize = pSection->left_over_size;
	pSection->left_over_size = 0;
	section_data = (unsigned char*)pSection->left_over;

	if ( ( section_data[1] & 0x80)	!= 0x80	) //missing section syntax indicator	
		return false;

	section_length = (((section_data[1] & 0x0f) << 8) | section_data[2]) - 1 ;
	if ( section_length > 4096+64 ) //invalid section
		return false;

	if ( pSection->data == NULL || pSection->data_size < section_length )
	{
		if ( pSection->data != NULL )
			sagetv_free( pSection->data );
		pSection->data_size = 0;
		pSection->data = (char*)SAGETV_MALLOC( section_length + 64 );
		if ( pSection->data != NULL )
			pSection->data_size = section_length;
	}

	StartSectionData( pSection, section_length );

	if ( PushSectionData( pSection, (char*)section_data, nSize ) )
	{
		if ( SectionCrcCheck( pSection ) )
			return true;
	}

	return false;
}

/*
bool UnpackSectionData1( bool bStartFlag, TS_SECTION* pSection, const unsigned char*  pbData, int nSize )
{
	const unsigned char* section_data; 
	bool  section_close;
	int section_length;

	if ( bStartFlag )
	{
	  section_data = pbData + *pbData	+ 1; 

	  if ( ( section_data[1]	& 0x80)	!= 0x80	) //missing section syntax indicator	
		return false;

	  section_length = (((section_data[1] & 0x0f) << 8) | section_data[2]) - 1 ;
	  if ( section_length < 0 ) section_length = 0;

	  if ( pSection->data == NULL || pSection->data_size < section_length )
	  {
		if ( pSection->data != NULL )
			sagetv_free( pSection->data );
		pSection->data = (char*)SAGETV_MALLOC( section_length + 64 );
		pSection->data_size = section_length;
	  }

	  if ( pSection->data == NULL || pSection->data_size > 4096+64 )
		  return false;

	  StartSectionData( pSection, section_length );

	} else
	  section_data = pbData;

    if ( pSection->data == NULL || pSection->data_size > 4096+64 )
		  return false;

	section_close = PushSectionData( pSection, (char*)section_data, nSize-(section_data-pbData) );

	// data is ready yet, waiting next section packet
	if ( !section_close )
		return false;

	if ( !SectionCrcCheck( pSection ) )
		return false;

	return true;

}*/   

bool UnpackSectionData( bool bStartFlag, TS_SECTION* pSection, const unsigned char*  pbData, int nSize )
{
	const unsigned char* section_data; 
	int   section_length;
	int   last_bytes;

	if ( pSection->left_over_size > 0 )
	{
		if ( FlushSectionData( pSection ) )
		{
			if ( bStartFlag && nSize < sizeof(pSection->left_over) )
			{
				memcpy( pSection->left_over, pbData, nSize );
				pSection->left_over_size = nSize;
			}
			return true;
		}
	}

	if ( bStartFlag )
	{
		section_data = pbData + *((unsigned char *)pbData) + 1; 
		if ( section_data > pbData + nSize )
			return false;

		last_bytes = section_data - pbData - 1;
		if ( last_bytes > 0 )
		{
			//if ( pSection->data_size == pSection->bytes + last_bytes && pSection->data != NULL )
			if ( pSection->data != NULL )
			{
				if ( PushSectionData( pSection, (char*)pbData+1, last_bytes ) )
				{
					if ( SectionCrcCheck( pSection ) )
					{
						if ( nSize - last_bytes - 1 < sizeof(pSection->left_over ) )
						{
							memcpy( pSection->left_over, section_data, nSize - last_bytes - 1 );
							pSection->left_over_size = nSize - last_bytes - 1;
						}
						return true;
					}
				}
			}
		}

		if ( ( section_data[1] & 0x80)	!= 0x80	) //missing section syntax indicator	
			return false;

		section_length = (((section_data[1] & 0x0f) << 8) | section_data[2]) - 1 ;
		if ( section_length < 0 || section_length > 4096+64 ) //invalid section
			return false;

		if ( pSection->data == NULL || pSection->data_size < section_length )
		{
			if ( pSection->data != NULL )
				sagetv_free( pSection->data );
			pSection->data_size = 0;
			pSection->data = (char*)SAGETV_MALLOC( section_length + 64 );
			if ( pSection->data != NULL )
				pSection->data_size = section_length;
		}

		StartSectionData( pSection, section_length );

	} else
	  section_data = pbData;

    if ( pSection->data == NULL )
		  return false;

	if ( PushSectionData( pSection, (char*)section_data, nSize-(section_data-pbData) ) )
	{
		if ( SectionCrcCheck( pSection ) )
			return true;
		else
			return false;
	}

	return false;
}

unsigned char GetSectionDataType(  TS_SECTION* pSection )
{
	return *((unsigned char*)pSection->data);
}


bool UnpackPAT( TS_PARSER* pParser, const unsigned char* pbData, int nSize, unsigned short tsid_filter )
{
	unsigned char* data;
	int	packetOffset;

	if ( !CheckContinuity( &pParser->TSHeader, &pParser->Pat.counter ) )
		return false;

	if ( !UnpackSectionData( pParser->TSHeader.start, &pParser->Pat.section, pbData, nSize ) )
		return false;

	data = (unsigned char *)pParser->Pat.section.data;

	if ( data[0] != 0 )  //it's invalid PAT
		return false;

	//filter out unpected tsid
	if ( tsid_filter != 0  )
	{
		unsigned short tsid;
		tsid = (data[3] << 8) | data[4];
		if ( tsid_filter != tsid )
			return false;  //unexpected PAT
	}

	memset( pParser->Pat.ProgramNumber, 0x0, sizeof(pParser->Pat.ProgramNumber) );
	memset( pParser->Pat.ProgramPID, 0x0, sizeof(pParser->Pat.ProgramPID) );	  
	pParser->Pat.SectionLength =	((data[1] & 0x03) << 8) | data[2];
	pParser->Pat.TSID = (data[3] << 8)	| data[4];
	pParser->Pat.VersionNumber =	(data[5] & 0x3E) >> 1;
	pParser->Pat.CurrentNextIndicator = data[5] & 0x01;
	pParser->Pat.SectionNumber =	data[6];
	pParser->Pat.LastSectionNumber =	data[7];

	packetOffset = 8;
	pParser->Pat.NumPrograms	= 0;

	while (	pParser->Pat.NumPrograms < MAX_ES && packetOffset <= (int)pParser->Pat.SectionLength-4 )	
	{
		pParser->Pat.ProgramNumber[ pParser->Pat.NumPrograms ] = 
					(data[packetOffset] << 8)	| data[packetOffset +	1];	
		pParser->Pat.ProgramPID[ pParser->Pat.NumPrograms ]	= 
					((data[packetOffset + 2] & 0x1f) << 8) |  data[packetOffset + 3];
		pParser->Pat.NumPrograms++;
		packetOffset +=	4;
	}
	
	return true;
}


//short LookupProgramInPmt( TS_PARSER* pParser, short ProgramNum )
//{
//	int i;
//	for ( i = 0; i<MAX_PROGRAM; i++ )
//	{
//		if ( pParser->Pat.ProgramNumber[i] == (unsigned short)ProgramNum )
//			return i;
//	}
//
//	return (short)-1;
//}
//

unsigned short UnpackPMT( TS_PARSER* pParser, const unsigned char* pbData, int nSize, short ProgramNum, short* PmtIndex, TS_SECTION** pSection )	
{
	int i ;
	unsigned char* data;
	unsigned short packetOffset;
	unsigned short ProgramNumInPmt;
	short NumStreams;

	*PmtIndex = MAX_PROGRAM-1; //for safe heck

	//i = LookupProgramInPmt( pParser, ProgramNum );
	i = GetChannelNum( pParser, ProgramNum );
	if ( i == -1 )
	{
		for ( i = 0; i<MAX_PROGRAM; i++ )
			if ( pParser->Pmt[i].ProgramNumber == 0 )
				break;
	}
	if ( i >= MAX_PROGRAM )
		return 0;  //program is full

	if ( !CheckContinuity( &pParser->TSHeader, &pParser->Pmt[i].counter ) )
		return 0;

	if ( pParser->Pmt[i].section == NULL ) 
		  pParser->Pmt[i].section = (TS_SECTION*)sagetv_malloc ( sizeof(TS_SECTION) ); 

	if ( !UnpackSectionData( pParser->TSHeader.start, pParser->Pmt[i].section, pbData, nSize ) )
		return 0;


	if ( pParser->Pmt[i].section->data == NULL )
		return 0;

	*pSection = pParser->Pmt[i].section;
	data = (unsigned char*)pParser->Pmt[i].section->data;

	ProgramNumInPmt = (data[3] << 8) + data[4];
	//in a case two PMT tables are shared one pid. //e.g. WSYR-DT of ATSC, DR2_43 of DVB-C,   
	if ( ProgramNumInPmt != ProgramNum )
	{
		i = GetChannelNum( pParser, ProgramNumInPmt );
		if ( i == -1 )
		{
			int j;
			//if pmt exist
			for ( j = 0; j<MAX_PROGRAM; j++ )
				if ( pParser->Pmt[j].ProgramNumber == ProgramNumInPmt )
					break;

			//if pmt isn't exist, create a new one.
			if ( j >= MAX_PROGRAM )
			{
				for ( j = 0; j<MAX_PROGRAM; j++ )
					if ( pParser->Pmt[j].ProgramNumber == 0 )
						break;
			}
			if ( j >= MAX_PROGRAM )
				return 0;

			i = j;
		}
	}

	pParser->Pmt[i].TableId	     = data[0];
	pParser->Pmt[i].SectionLength = ((data[1] & 0x0f) << 8) | data[2];
	pParser->Pmt[i].ProgramNumber = (data[3] << 8)	+ data[4];
	pParser->Pmt[i].VersionNumber = (data[5]	& 0x3E)	>> 1;
	pParser->Pmt[i].CurrentNextIndicator =	data[5] &	0x01;
	pParser->Pmt[i].SectionNum = data[6];    
	pParser->Pmt[i].LastSectionNum = data[7];
	pParser->Pmt[i].PCRPID	= ((data[8] &	0x1f) << 8)	| data[9];
	pParser->Pmt[i].ProgramInfoLength = ((data[10] &	0x03) << 8)	| data[11];

	if ( pParser->Pmt[i].ProgramInfo != NULL ) 
	{
		sagetv_free( pParser->Pmt[i].ProgramInfo );
		pParser->Pmt[i].ProgramInfo = NULL;
	}
	if ( pParser->Pmt[i].ProgramInfoLength	)
	{
		pParser->Pmt[i].ProgramInfo = (unsigned char*)SAGETV_MALLOC( pParser->Pmt[i].ProgramInfoLength );
		if ( pParser->Pmt[i].ProgramInfo != NULL )
			memcpy( pParser->Pmt[i].ProgramInfo, (const unsigned char*)data + 12, pParser->Pmt[i].ProgramInfoLength );
	}

	packetOffset = 12 + pParser->Pmt[i].ProgramInfoLength;
	NumStreams = 0;
	while (packetOffset	+ 6	<= pParser->Pmt[i].SectionLength)
	{
		pParser->Pmt[i].StreamType[NumStreams]	 = data[packetOffset];	
		pParser->Pmt[i].ElementaryPID[NumStreams] = ((data[packetOffset+1] & 0x1F) << 8) | data[packetOffset+2];
		pParser->Pmt[i].ESInfoLength[NumStreams]  = ((data[packetOffset + 3] & 0x03) << 8) | data[packetOffset + 4];
		if ( pParser->Pmt[i].ESInfo[NumStreams] != NULL )
		{
			sagetv_free( pParser->Pmt[i].ESInfo[NumStreams] );
			pParser->Pmt[i].ESInfo[NumStreams] = NULL;
		}
		if ( pParser->Pmt[i].ESInfoLength[NumStreams] )
		{
			pParser->Pmt[i].ESInfo[NumStreams] = (unsigned char*)SAGETV_MALLOC( pParser->Pmt[i].ESInfoLength[NumStreams] );
			if ( pParser->Pmt[i].ESInfo[NumStreams] != NULL )
				memset( pParser->Pmt[i].ESInfo[NumStreams], 0x0, pParser->Pmt[i].ESInfoLength[NumStreams] );
			memcpy( pParser->Pmt[i].ESInfo[NumStreams], (char*)data + packetOffset+5, pParser->Pmt[i].ESInfoLength[NumStreams] );
		} 

		packetOffset +=	5 +	pParser->Pmt[i].ESInfoLength[NumStreams];
		NumStreams++;
	}
	pParser->Pmt[i].NumStreams	= NumStreams;
	*PmtIndex = i;

	return ProgramNumInPmt;

}

bool ExtractPMT( TS_PMT *pPmt, TS_SECTION *pSection )
{
	unsigned char* data;
	unsigned short packetOffset;
	short NumStreams;

	data = (unsigned char*)pSection->data;

	pPmt->TableId	    = data[0];
	pPmt->SectionLength = ((data[1] & 0x0f) << 8) | data[2];
	pPmt->ProgramNumber = (data[3] << 8)	+ data[4];
	pPmt->VersionNumber = (data[5]	& 0x3E)	>> 1;
	pPmt->CurrentNextIndicator =	data[5] &	0x01;
	pPmt->SectionNum = data[6];    
	pPmt->LastSectionNum = data[7];
	pPmt->PCRPID	= ((data[8] &	0x1f) << 8)	| data[9];
	pPmt->ProgramInfoLength = ((data[10] &	0x03) << 8)	| data[11];

	if ( pPmt->ProgramInfo != NULL ) 
	{
		sagetv_free( pPmt->ProgramInfo );
		pPmt->ProgramInfo = NULL;
	}
	if ( pPmt->ProgramInfoLength	)
	{
		pPmt->ProgramInfo = (unsigned char*)SAGETV_MALLOC( pPmt->ProgramInfoLength );
		if ( pPmt->ProgramInfo != NULL )
			memcpy( pPmt->ProgramInfo, (const unsigned char*)data + 12, pPmt->ProgramInfoLength );
	}

	packetOffset = 12 + pPmt->ProgramInfoLength;
	NumStreams = 0;
	while (packetOffset	+ 6	<= pPmt->SectionLength)
	{
		pPmt->StreamType[NumStreams]	 = data[packetOffset];	
		pPmt->ElementaryPID[NumStreams] = ((data[packetOffset+1] & 0x1F) << 8) | data[packetOffset+2];
		pPmt->ESInfoLength[NumStreams]  = ((data[packetOffset + 3] & 0x03) << 8) | data[packetOffset + 4];
		if ( pPmt->ESInfo[NumStreams] != NULL )
		{
			sagetv_free( pPmt->ESInfo[NumStreams] );
			pPmt->ESInfo[NumStreams] = NULL;
		}
		if ( pPmt->ESInfoLength[NumStreams] )
		{
			pPmt->ESInfo[NumStreams] = (unsigned char*)SAGETV_MALLOC( pPmt->ESInfoLength[NumStreams] );
			if ( pPmt->ESInfo[NumStreams] != NULL )
				memset( pPmt->ESInfo[NumStreams], 0x0, pPmt->ESInfoLength[NumStreams] );
			memcpy( pPmt->ESInfo[NumStreams], (char*)data + packetOffset+5, pPmt->ESInfoLength[NumStreams] );
		} 

		packetOffset +=	5 +	pPmt->ESInfoLength[NumStreams];
		NumStreams++;
		if ( NumStreams >= MAX_ES ) break;
	}
	pPmt->NumStreams	= NumStreams;

	return true;
}

int SetSeletedChannelByProgramNum( TS_PARSER* pParser, unsigned short ProgramNum )
{
	int i;
	if ( ProgramNum == 0x0 || ProgramNum == 0xffff )
		return false;

	for	( i = 0; i<pParser->TSChannelNum;	i++	)
	{
		if ( pParser->Channel2Program[i] == ProgramNum )
		{
			pParser->SelectedChannel[0] = i;
			pParser->SelectedChannelNum = 1;
			pParser->ChannelState[0] = 0;
			pParser->WorkingProgram = ProgramNum;
			pParser->pcr_pid = GetSelectedProgramPcrPID( pParser );
			if ( pParser->pcr_pid != 0 ) flog(( "native.log", "\t\t pcr pid reset at 'SetSeletedChannelByProgramNum' \n" ));
			if ( pParser->pcr_pid != 0 ) NotifyPcrPID( pParser );
			return i;
		}
	}
	return -1;
}	

static int BuildChannelMapping( TS_PARSER* pParser )
{
	//filter NIT Stream, and sort by Program ID
	int i, j, k=0;
	unsigned short program, cur, small;
	unsigned short ChannelPatMap[MAX_PROGRAM] = {0};

	pParser->TSChannelNum = 0;
	ChannelPatMap[0] = 0;
	for ( j = 0; j<pParser->Pat.NumPrograms; j++ )
	{
		if ( j == 0 )
			cur = 0;
		else
			cur = pParser->Pat.ProgramNumber[ ChannelPatMap[j-1] ];

		small = 0xffff;
		for ( i = 0; i<pParser->Pat.NumPrograms; i++ )
		{
			program = pParser->Pat.ProgramNumber[i];
			if ( program == 0  ) //filter out NIT stream
				continue;
			
			if ( small > program && program > cur )
			{
				k = i;
				small = program;
			}
		}
		if ( small > cur && small != 0xffff )
		{
			ChannelPatMap[j] = k;
			pParser->TSChannelNum++;
		}
	}


	for ( i = 0; i<pParser->TSChannelNum; i++ )
	{
		pParser->Channel2Program[i] = pParser->Pat.ProgramNumber[ ChannelPatMap[i] ];
	}


	//convert selected programnum into selected channel
	//for ( i = 0; i<pParser->SelectedProgramNum; i++ )
	if ( pParser->SelectedProgram[0] == -1 && pParser->SelectedChannel[0] >= 0 && pParser->SelectedChannel[0] < MAX_PROGRAM )
		pParser->WorkingProgram = pParser->Channel2Program[ pParser->SelectedChannel[0] ];
	else
		pParser->WorkingProgram = pParser->SelectedProgram[0];

	pParser->pcr_pid = GetSelectedProgramPcrPID( pParser );
	if ( pParser->pcr_pid != 0 ) flog(( "native.log", "\t\t pcr pid reset at 'BuildChannelMapping' \n" ));
	if ( pParser->pcr_pid != 0 ) NotifyPcrPID( pParser );
	pParser->WorkingTSID = pParser->Pat.TSID;
	return 1;
}

static bool BuildAVMappingTable( TS_PARSER* pParser, short ChannelNum, int PmtIndex )
{
	int i,j;
	int    StreamNum;
	unsigned char  StreamType;

	if ( PmtIndex < 0 || PmtIndex > MAX_PROGRAM || ChannelNum > MAX_PROGRAM )
	{
		printf( "error: PMT is not received\n" );
		return false;
	}

	StreamNum = pParser->Pmt[PmtIndex].NumStreams;
	//memset( pParser->ESPidMap, 0, sizeof(pParser->ESPidMap) );
	j = 0;
	for ( i = 0; i<StreamNum; i++ )
	{
		StreamType = pParser->Pmt[PmtIndex].StreamType[i];
		if ( IsAudioType(	StreamType ) || IsVideoType( StreamType ) )
		{
			pParser->ESPidMap[ChannelNum][j] = pParser->Pmt[PmtIndex].ElementaryPID[i];
			j++;
		}
	}
	return true;
}

static _inline short GetPMTPid( TS_PARSER* pParser, short Channel )
{
	int i;
	unsigned short program;
	if ( Channel >= MAX_PROGRAM || Channel < 0 )
		return -1;

	program = pParser->Channel2Program[Channel];
	for ( i = 0; i<pParser->Pat.NumPrograms; i++ )
	{
		if ( pParser->Pat.ProgramNumber[i] == program )
			return pParser->Pat.ProgramPID[i];
	}
	return -1;
}

static _inline short  GetMappindESIndex( TS_PARSER* pParser, short ChannelNum,  unsigned short PID )
{
	int i;
	if ( ChannelNum >= MAX_PROGRAM || ChannelNum < 0 )
		return -1;

	for ( i = 0; i<MAX_ES; i++ )
	{
		if ( pParser->ESPidMap[ChannelNum][i] == PID )
			return i;
	}
	return -1;
}

void TSProcessBlock( TS_PARSER* pParser, int size, char* data )
{
	char *Start;
	int	 Bytes, UsedBytes;	

	Start =	pParser->TSBuf + pParser->TSBytes;

	while ( size > 0 )
	{
		if ( pParser->abort ) return;
		//feed data into a 3 packet buffer to parse
		Bytes = _MIN( (int)sizeof(pParser->TSBuf) - (int)pParser->TSBytes, size );
		memcpy( (char*)Start, data, Bytes);
		UsedBytes = ParseData( pParser, (const unsigned	char*)pParser->TSBuf, Bytes+pParser->TSBytes );
		//if ( UsedBytes > Bytes+pParser->TSBytes )
		//	UsedBytes = Bytes+pParser->TSBytes;
		pParser->TSBytes = Bytes+pParser->TSBytes - UsedBytes;
		if ( pParser->TSBytes > 0 )
		{
			memcpy(	pParser->TSBuf, pParser->TSBuf + UsedBytes, pParser->TSBytes	);
			Start =	pParser->TSBuf + pParser->TSBytes;
		} else
		{
			Start =	pParser->TSBuf;
		}

		pParser->data_pos += UsedBytes;
		data += Bytes;
		size -= Bytes;
	}
}

void TSResetBlockBuffer( TS_PARSER* pParser )
{
	pParser->TSBytes = 0;
}


int static LookUpPidHit( TS_PARSER* pParser, unsigned pid )
{
	int i;
	for ( i = 0; i<MAX_PID_COLLECT && pParser->pid_array[i].state; i++ )
	{
		if ( pid == pParser->pid_array[i].pid )
		{
			pParser->pid_array[i].hits++;
			return i;
		}
	}
	return -1;
}

int static AddPidArray( TS_PARSER* pParser, unsigned pid ) 
{
	register int i;
	for ( i = 0; i<MAX_PID_COLLECT; i++ )
	{
		if ( pParser->pid_array[i].state == 0  )
		{
			pParser->pid_array[i].hits = 0;
			pParser->pid_array[i].state = 1;
			pParser->pid_array[i].pid = pid;
			return i;
		}
	}
	return -1;
}
void static UpdatePidArrayType( TS_PARSER* pParser, TS_PMT* pPmt ) 
{
	int i, j, num;
	num = pPmt->NumStreams;
	if ( num > MAX_PROGRAM )
		return;

	for ( j=0; j<num; j++ )
	{
		for ( i = 0; i<MAX_PROGRAM; i++ )
		{
			if ( pPmt->ElementaryPID[j] == pParser->pid_array[i].pid )
				if ( pParser->pid_array[i].type == 0 )
				{
					pParser->pid_array[i].type = pPmt->StreamType[j];
				}
		}
	}
}

//check all PMT Elemntary pid is already in stream
bool PMTStreamReady( TS_PARSER *pParser, TS_PMT* pPmt )
{
	int i, j, num;
	num = pPmt->NumStreams;
	if ( num > MAX_PROGRAM )
		return false;

	for ( j=0; j<num; j++ )
	{
		for ( i = 0; i<MAX_PROGRAM && pParser->pid_array[i].state; i++ )
		{
			if ( pPmt->ElementaryPID[j] == pParser->pid_array[i].pid )
				break;
		}

		if ( !( i<MAX_PROGRAM && pParser->pid_array[i].state ) )
			return false;
	}

	return true;
}

int  IsPlayableChannel( TS_PARSER* pParser, int channel )
{
	int num, ret=0;
	int pid_array_id;
	int index, k;

	if ( pParser == NULL )
		return 0;
	
	num = 0;
	index = SeekPmtByChannel( pParser, channel );
	if ( index >= 0 )
	{
		for ( k = 0; k<pParser->Pmt[index].NumStreams; k++ )
		{
			if ( IsVideoType( pParser->Pmt[index].StreamType[k] ) )
			{
				ret |= MASK_VIDEO_READY;
				pid_array_id = LookUpPidHit( pParser, pParser->Pmt[index].ElementaryPID[k] );
				if ( pid_array_id >= 0 && pParser->pid_array[pid_array_id].hits > 3 )
				{
					ret |= MASK_VIDEO_DATA_EXIST;
					break;
				}
			}
		}
		for ( k = 0; k<pParser->Pmt[index].NumStreams; k++ )
		{
			if ( IsAudioType( pParser->Pmt[index].StreamType[k] ) )
			{
				ret |= MASK_AUDIO_READY;
				pid_array_id = LookUpPidHit( pParser, pParser->Pmt[index].ElementaryPID[k] );
				if ( pid_array_id >= 0 && pParser->pid_array[pid_array_id].hits > 2 )
				{
					ret |= MASK_AUDIO_DATA_EXIST;
					break;
				}
			}
		}
	}
	
	return ret;
}


int  FindPlayableChannel( TS_PARSER* pParser )
{
	int num, i;
	bool video_ready = false, audio_ready = false;
	int pid_array_id;
	if ( pParser == NULL )
		return 0;
	
	num = 0;
	for ( i = 0; i<pParser->TSChannelNum; i++ )
	{
		int index, k;
		index = SeekPmtByChannel( pParser, i );
		if ( index >= 0 )
		{
			for ( k = 0; k<pParser->Pmt[index].NumStreams; k++ )
			{
				if ( IsVideoType( pParser->Pmt[index].StreamType[k] ) )
				{
					pid_array_id = LookUpPidHit( pParser, pParser->Pmt[index].ElementaryPID[k] );
					if ( pid_array_id >= 0 && pParser->pid_array[pid_array_id].hits > 3 )
					{
						video_ready = true;
						break;
					}
				}
			}
			for ( k = 0; k<pParser->Pmt[index].NumStreams; k++ )
			{
				if ( IsAudioType( pParser->Pmt[index].StreamType[k] ) )
				{
					pid_array_id = LookUpPidHit( pParser, pParser->Pmt[index].ElementaryPID[k] );
					if ( pid_array_id >= 0 && pParser->pid_array[pid_array_id].hits > 2 )
					{
						audio_ready = true;
						break;
					}
				}
			}
			if ( audio_ready && video_ready )
				return i;
		}
	}
	return -1;
}

bool ConstructPMT( TS_PARSER* pParser )
{
	int	i,j;
	//bool type_exist = false;
	short NumStreams = 0;
	PID_REC pid_tmp;

	pParser->Channel2Program[0] = 0;
	pParser->Pat.NumPrograms = 1;
	pParser->Pat.ProgramNumber[0]= 1;
	pParser->Pmt[0].SectionLength = 1;

	if ( pParser->Pat.ProgramNumber[0] == 0 ) //if no pat, we need a program anyway
		pParser->Pat.ProgramNumber[0] = 1;
	pParser->Pmt[0].ProgramNumber = pParser->Pat.ProgramNumber[0];

	//sort streams order based on hits so that video is first pin
	for	( i	= 0; i<MAX_PROGRAM && pParser->pid_array[i].state;	i++	)
	{
		for	( j	= 0; j<MAX_PROGRAM &&  pParser->pid_array[j].state;	j++	)
		{
			if ( pParser->pid_array[i].hits > pParser->pid_array[j].hits )
			{
				pid_tmp = pParser->pid_array[i];
				pParser->pid_array[i] = pParser->pid_array[j];
				pParser->pid_array[j] = pid_tmp;
			}
		}
	}

	for	( i	= 0; i<MAX_PROGRAM && pParser->pid_array[i].state ;	i++	)
	{
		if ( IsVideoType( pParser->pid_array[i].type ) || IsAudioType( pParser->pid_array[i].type ) )
		{
			pParser->Pmt[0].ElementaryPID[NumStreams] = pParser->pid_array[i].pid;
			pParser->Pmt[0].StreamType[NumStreams] = pParser->pid_array[i].type;
			NumStreams++;
		}
	}

	/*
	//if stream is found,  pid type not avaliable, make a wild guess
	if ( NumStreams == 0 && pParser->pid_array[0].type == 0 )
	{
		pParser->pid_array[0].type = 2;
		for	( i	= 1; i<MAX_PROGRAM && pParser->pid_array[i].state && pParser->pid_array[i].pid != 0;	i++	)
			pParser->pid_array[i].type = 4;

		for	( i	= 0; i<MAX_PROGRAM && pParser->pid_array[i].state ;	i++	)
		{
			if ( IsVideoType( pParser->pid_array[i].type ) || IsAudioType( pParser->pid_array[i].type ) )
			{
				pParser->Pmt[0].ElementaryPID[NumStreams] = pParser->pid_array[i].pid;
				pParser->Pmt[0].StreamType[NumStreams] = pParser->pid_array[i].type;
				NumStreams++;
			}
		}
	}
	*/

	pParser->Pmt[0].NumStreams = NumStreams;
	BuildChannelMapping( pParser );
	if ( NumStreams > 0 )
	{
		pParser->ChannelState[0] |= MASK_PMT_READY;
		if ( pParser->signal_dumper )
		{
			SIGNAL_INFO Signal;
			Signal.type = STREAM_READY;
			Signal.pid  = -1;
			pParser->signal_dumper( (void*)pParser, pParser->SelectedChannel[0], &Signal ); 
		}
	}

	if ( NumStreams > 0 )
	{
		if ( BuildAVMappingTable( pParser, 0, 0 ) )
			pParser->ChannelState[0] |= MASK_MAPPING_READY;

		pParser->LockPmtPat = true;
	}

	return NumStreams > 0;
}

bool CreatePMT( TS_PARSER* pParser, PID_TBL* pPidInf, int numPids )
{
	int	i;
	//bool type_exist = false;
	short NumStreams = 0;

	pParser->Channel2Program[0] = 0;
	pParser->Pat.NumPrograms = 1;
	pParser->Pat.ProgramNumber[0]= 1;
	pParser->Pmt[0].SectionLength = 1;

	pParser->Pat.ProgramNumber[0] = 1;
	pParser->Pmt[0].ProgramNumber = pParser->Pat.ProgramNumber[0];

	for	( i	= 0; i<numPids && pPidInf[i].pid ;	i++	)
	{
		if ( IsVideoType( pPidInf[i].type ) || IsAudioType( pPidInf[i].type ) )
		{
			pParser->Pmt[0].ElementaryPID[NumStreams] = pPidInf[i].pid;
			pParser->Pmt[0].StreamType[NumStreams] = (char)pPidInf[i].type;
			NumStreams++;
		}
	}

	pParser->Pmt[0].NumStreams = NumStreams;
	BuildChannelMapping( pParser );
	if ( NumStreams > 0 )
	{
		pParser->PatState = 1;
		pParser->ChannelState[0] |= MASK_PMT_READY;
		if ( pParser->signal_dumper )
		{
			SIGNAL_INFO Signal;
			Signal.type = STREAM_READY;
			Signal.pid  = -1;
			pParser->signal_dumper( (void*)pParser, pParser->SelectedChannel[0], &Signal ); 
		}

	}
	if ( NumStreams > 0 )
	{
		if ( BuildAVMappingTable( pParser, 0, 0 ) )
			pParser->ChannelState[0] |= MASK_MAPPING_READY;

		pParser->LockPmtPat = true;
	}

	return NumStreams ;
}

void StopParser( TS_PARSER* pParser )
{
	pParser->abort = true; 
}

void StartParser( TS_PARSER* pParser )
{
	pParser->abort = false;
}

static int ParseData( TS_PARSER* pParser, const unsigned char* pStartData, long dwBytes )
{
	const unsigned char	*pData;
	const unsigned char	*pPayload;	
	int	 dwSearchBytes;	
	unsigned short used_bytes;
	//bool bPacketParsed = false;	
	bool bSyncFound;
	int ret;
	

	pParser->data_offset = 0;
    used_bytes = 0;	
	pData =	pStartData;	

	if (  dwBytes <	TS_PACKET_LENGTH )
		return used_bytes;

	while (	1 )	
	{
		const unsigned char	*pHeader;
		ret = 0;

		if ( pParser->abort ) return used_bytes;

		/* if we don't have	a whole	packet,	we don't parse it */
		dwSearchBytes =	( pStartData + dwBytes ) - pData;
		if (  dwSearchBytes	< (TS_PACKET_LENGTH*2)  )	
			break;

		/*search SYNC header */
		pHeader	= pData;
		bSyncFound = true;

		//debug check
		assert( pStartData + used_bytes == pHeader );
        
		if ( !IsTsHeader( pData	) )	
			bSyncFound = SearchTsHeader( pData, dwSearchBytes, &pHeader );

		if ( bSyncFound ) {
			used_bytes += (pHeader - pData);
			pData = pHeader;
		} else {
			if(pHeader)
				used_bytes += (pHeader - pData);	// only skip past what we've already searched
			else
				used_bytes += dwSearchBytes;
		}
		
		 if	( !bSyncFound || used_bytes + TS_PACKET_LENGTH > dwBytes )
			 return	used_bytes;

		 pParser->packets++;	
		 /* start parse packet*/
		 if	( ( bSyncFound = UnpackTsHeader( pParser, pHeader, &pPayload ) ) && pPayload != NULL )  
		 {
			 short ProgramNum, ChannelNum;
			 unsigned char StreamType;
			 int Bytes = 0;
			 short map_channel;
			 int pid_array_id = -1;
			 unsigned header_bytes = pPayload - pHeader;

			 pParser->data_offset = used_bytes;

			 //soft pid filter
			 if ( pParser->pid_filter_num )
			 {
				int i;
				for ( i = 0; i<pParser->pid_filter_num; i++ )
					if ( pParser->TSHeader.pid == pParser->pid_filter_tbl[i] )
						break;
				//skip unexpected pids
				if ( pParser->TSHeader.pid != pParser->pid_filter_tbl[i] )
				{
					//bPacketParsed = true;
					
					if ( dwBytes >= used_bytes + TS_PACKET_LENGTH )
					{
						used_bytes += TS_PACKET_LENGTH;
						pData += TS_PACKET_LENGTH;
					} else
						return used_bytes;
					continue;	
				}
			 }

			 if ( pPayload != NULL )
				 Bytes = TS_PACKET_LENGTH - (pPayload - pHeader);
			 if ( pParser->enable_pid_stat )
			 {
				if ( ( pid_array_id = LookUpPidHit( pParser, pParser->TSHeader.pid ) )<0 )
					pid_array_id = AddPidArray( pParser, pParser->TSHeader.pid );
				if ( pid_array_id >= 0 ) 
				{
					pParser->pid_array[pid_array_id].hits++;
					if ( pParser->TSHeader.scrambling_ctr )
						pParser->pid_array[pid_array_id].scrambled = pParser->TSHeader.scrambling_ctr;
				}
			 }

			 //processing PCR
			 if ( pParser->PCR != 0  )
			 {
				if (  pParser->pcr_pid != 0 )
				{
					if ( pParser->pcr_pid == pParser->TSHeader.pid  ) 
					{
						//calc stream rate
						CalcStreamPacketInterval( pParser, pParser->PCR, pParser->packets );
						pParser->StreamPCR = pParser->PCR; 
						pParser->pcr_packet_syn = pParser->packets;
					} 
				} else
				{
					if ( pParser->TSHeader.pcr_flag ) 
						RegisterPCRPid( pParser, pParser->TSHeader.pid );

					if ( pParser->video_frames > 30 )
					{
						pParser->pcr_pid = ReselectPCRPID( pParser );
						if ( pParser->pcr_pid != 0 ) flog(( "native.log", "\t\t pcr pid reset at 'TS parser core' \n" ));
						if ( pParser->pcr_pid != 0 ) NotifyPcrPID( pParser );
					}
				}
			 } else
			 if ( pParser->StreamPCR == 0 )
			 {   //if it's no PCR stream
				if ( pParser->video_frames + pParser->audio_frames > 1000 )
				{
					pParser->StreamPCR = 1;
				}
			 }

			 //if TS file doesn't carry PAT, we need build PMT by myself, or ask up level to parse stream type to feed back
			 if ( pParser->PatState == 0 )
			 {
				if ( pid_array_id >= 0 && pid_array_id < MAX_PID_COLLECT )
				{
					if ( CheckContinuity(  &pParser->TSHeader, &pParser->pid_array[pid_array_id].counter ) )
					{
						if ( UnpackSectionData( pParser->TSHeader.start, &pParser->pid_array[pid_array_id].section, 
						                        pPayload, Bytes ) )
						{
							unsigned char SectionType = GetSectionDataType( &pParser->pid_array[pid_array_id].section );
							if ( SectionType ==  0 )
							{
								//it's PAT section data
							}
							else
							if ( SectionType == 2 )
							{
								//it's PMT section data
								TS_PMT pmt={0};
								if ( ExtractPMT( &pmt, &pParser->pid_array[pid_array_id].section ) )
								{
									//ripped stream that has a PAT and a PMT at the header of recording  
									bool ripped_stream = ( pParser->packets - pParser->pat_frames) == 1 ;
									if ( ripped_stream || PMTStreamReady( pParser, &pmt ) ) 
									{
										UpdatePidArrayType( pParser, &pmt );
										pParser->pid_array[pid_array_id].state |= 0x80;
										if ( pParser->signal_dumper )
										{
											SIGNAL_INFO Signal;
											Signal.type = STREAM_READY;
											Signal.pid  = pParser->TSHeader.pid;
											pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
										}
									}
								}
								ReleasePMTdata( &pmt );

							}
						} else
						{   //if it's not section data, we ask up-level to parse stream type for me
							int ret;
							TS_AV_INFO av_info;
							av_info.type = 0;
							av_info.data = (char*)pPayload;
							av_info.bytes = Bytes; 
							av_info.sub_channel = -1;
							av_info.start_group_flag = pParser->TSHeader.start ? 1 : 0;
							av_info.pid = pParser->TSHeader.pid;
							av_info.continuity = pParser->TSHeader.continuity_ct;
							av_info.header_bytes = header_bytes;
							ret = pParser->av_dumper( (void*)pParser, -1, (void*)&av_info );
							if ( ret > 0 )
								pParser->pid_array[pid_array_id].type = av_info.type;
						}
					}
				}
			 }

			 //PAT packet
			 if	(  pParser->TSHeader.pid == 0 && pPayload != NULL && !pParser->LockPmtPat )  
			 {
			    unsigned short oldTSID, newTSID;

				pParser->pat_frames++;

				oldTSID = pParser->Pat.TSID;
				ret	= UnpackPAT( pParser, pPayload, Bytes, pParser->SelectedTSID );
				newTSID = pParser->Pat.TSID;

				if ( ret )
				{	
					if ( oldTSID != newTSID )
					{
						RegisterTSID( pParser->si_env, newTSID ); //register tsid for SI parser to pick up SDT
						SavePAT( pParser, &pParser->Pat );
						pParser->PATs[pParser->pats_index] = pParser->Pat;
						if ( ++pParser->pats_index >= MAX_PAT_NUM	) pParser->pats_index = 0;
						if ( pParser->pats_num < MAX_PAT_NUM ) pParser->pats_num++;
					}
					//if PAT updated, or first PAT, we need build channel mapping
					if ( !pParser->PatState || oldTSID != newTSID  )
					{
						pParser->PatState = 0x01;
						//flog(( "native.log", "rebuild channel mapping, tsid:%d->%d\n", oldTSID, newTSID ));
						BuildChannelMapping( pParser );
						
						if ( pParser->signal_dumper )
						{
							SIGNAL_INFO Signal;
							Signal.type = STREAM_START;
							Signal.pid  = 0;
							pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
						}

					} else
					if ( pParser->PatState )
					{   //when PAT updated in middle of stream with the same TSID
						if ( !IsChannelInPAT( pParser )  )
						{
							int i;
							for	( i = 0; i<pParser->TSChannelNum; i++	)
								pParser->ChannelState[i] = 0; 
							pParser->pcr_pid = 0;
							ResetPCRPid( pParser );   //reselect PCRPID
							BuildChannelMapping( pParser );
							if ( pParser->signal_dumper )
							{
								SIGNAL_INFO Signal;
								Signal.type = STREAM_START;
								Signal.pid  = 0;
								pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
							}
						}
					}
					
					//dump PAT table 
					if ( pParser->PAT_dumper != NULL  )
					{
						SECTION_INFO section_info;
						section_info.pid = pParser->TSHeader.pid;
						section_info.data   = pParser->Pat.section.data;;
						section_info.length = pParser->Pat.section.bytes;
						section_info.parser = pParser;
						pParser->PAT_dumper( pParser->PAT_dumper_context, sizeof(section_info), (void*)&section_info );
					}
				} 

				if ( ret )	
				{
					if ( pParser->section_dumper != NULL )
					{
						SECTION_INFO section_info;
						section_info.pid = pParser->TSHeader.pid;
						section_info.service= pParser->WorkingProgram;
						section_info.channel = pParser->SelectedChannel[0];
						section_info.data   = pParser->Pat.section.data;
						section_info.length = pParser->Pat.section.bytes;
						section_info.parser = pParser;
						section_info.tbl    = &pParser->Pat;
						pParser->section_dumper( pParser->section_dumper_context, sizeof(section_info), (void*)&section_info );
					}
				}
				
			 } else	
			 //CAT packet
			 if	(  pParser->TSHeader.pid == 1 )	
			 {
				 //we skip it CAT packets
				pParser->cat_frames++;

			 } else	
			 //PMT packet
			 if	( pPayload != NULL && pParser->PatState && IsPMTPacket( pParser, pParser->TSHeader.pid, &ProgramNum ) && !pParser->LockPmtPat ) 
			 {
				short PmtIndex;
				TS_SECTION* pSection = NULL;
				pParser->pmt_frames++;
				ret	= UnpackPMT( pParser, pPayload, Bytes, ProgramNum, &PmtIndex, &pSection );

				if ( ret > 0 )
				{
					ProgramNum = (short)ret; //more than two PMT may share a pid 
					UpdatePidArrayType( pParser, &pParser->Pmt[PmtIndex] );
					if ( ( ChannelNum = GetChannelNum( pParser, ProgramNum ) ) >= 0 )
					{ 
						if ( !( pParser->ChannelState[ChannelNum] & MASK_PMT_READY ) || !pParser->bChannelInfReady )
						{
						  	pParser->ChannelState[ChannelNum] |= MASK_PMT_READY;

							if ( pParser->signal_dumper )
							{
								int ret;
								SIGNAL_INFO Signal;
								Signal.type = STREAM_READY;
								Signal.pid  = pParser->TSHeader.pid;
								ret = pParser->signal_dumper( (void*)pParser, ChannelNum, &Signal ); 
								if ( ret == 3 ) //repeat PMT for TSSplitter parse channel information
									pParser->bChannelInfReady = false;
								else
									pParser->bChannelInfReady = true;

								if ( ret == 4 )  //repeat PMT for a channel to parse channel information
									pParser->ChannelState[ChannelNum] &= ~MASK_PMT_READY;
							}
						}
					}

					if ( pParser->section_dumper != NULL && PmtIndex >= 0 && pSection != NULL )
					{
					   SECTION_INFO section_info;
					   section_info.pid    = pParser->TSHeader.pid;
					   section_info.service= ProgramNum;
					   section_info.channel = ChannelNum;
					   section_info.data   = pSection->data;;
					   section_info.length = pSection->bytes;
					   section_info.parser = pParser;
					   pParser->section_dumper( (void*)pParser, sizeof(section_info), (void*)&section_info );
					}

					//dump PMT table for CAM
					if ( pParser->PMT_dumper != NULL && PmtIndex >= 0 && pSection != NULL )
					{
					   SECTION_INFO section_info;
					   section_info.pid     = pParser->TSHeader.pid;
   					   section_info.service = ProgramNum;
					   section_info.channel = ChannelNum;
					   section_info.tbl     = &pParser->Pmt[PmtIndex];
					   section_info.data   = pSection->data;;
					   section_info.length = pSection->bytes;
					   section_info.parser = pParser;
					   pParser->PMT_dumper(  pParser->PMT_dumper_context, sizeof(section_info), (void*)&section_info );

					}
				}

			 } else	
			 if	( pPayload != NULL && IsSelectedProgram( pParser, pParser->TSHeader.pid, &StreamType ) )
			 {
				 ChannelNum = GetChannelNum( pParser, pParser->WorkingProgram );
				 if (  !( pParser->ChannelState[ChannelNum] & MASK_MAPPING_READY ) )
				 {
					if ( BuildAVMappingTable( pParser, ChannelNum, SeekPmtByChannel( pParser, ChannelNum ) ) )
						pParser->ChannelState[ChannelNum] |= MASK_MAPPING_READY;
				 }
				


				if ( IsVideoType( StreamType ) )	
				{
					pParser->video_frames++;

					if ( !(pParser->ChannelState[ChannelNum] & MASK_VIDEO_READY ) )
					{
						pParser->ChannelState[ChannelNum] |= MASK_VIDEO_READY;
						if ( pParser->signal_dumper )
						{
							SIGNAL_INFO Signal;
							Signal.type = STREAM_VIDEO_START;
							Signal.pid  = pParser->TSHeader.pid;
							pParser->signal_dumper( (void*)pParser, ChannelNum, &Signal ); 
						}
					}
				}

				if ( IsAudioType( StreamType )  )
				{
					pParser->audio_frames++;

					if ( !(pParser->ChannelState[ChannelNum] & MASK_AUDIO_READY ) )
					{
						pParser->ChannelState[ChannelNum] |= MASK_AUDIO_READY;
						if ( pParser->signal_dumper )
						{
							SIGNAL_INFO Signal;
							Signal.type = STREAM_AUDIO_START;
							Signal.pid  = pParser->TSHeader.pid;
							pParser->signal_dumper( (void*)pParser, ChannelNum, &Signal ); 
						}
					}

				}

				map_channel = GetMappindESIndex( pParser, ChannelNum, pParser->TSHeader.pid );
				if ( pParser->av_dumper != NULL && map_channel >= 0 )
				{
					TS_AV_INFO av_info;
					av_info.type = StreamType;
					av_info.data = (char*)pPayload;
					av_info.bytes = Bytes; 
					av_info.sub_channel = map_channel;
					av_info.start_group_flag = pParser->TSHeader.start ? 1 : 0;
					av_info.pid = pParser->TSHeader.pid;
					av_info.continuity = pParser->TSHeader.continuity_ct;
					av_info.PCR = pParser->StreamPCR; //pParser->PCR;
					av_info.header_bytes = header_bytes;


					pParser->av_dumper( (void*)pParser, ChannelNum, (void*)&av_info );


				}

			 } 

			 if ( pParser->si_dumper != NULL  )
			 { 
				 pParser->si_dumper( (void*)pParser->si_env, pParser->TSHeader.pid,  
					                 &pParser->TSHeader, (char*)pPayload, Bytes );

			 }
			 //printf( "pid:%02x type:%02d ct:%02d \n",	TSHeader.pid, StreamType , TSHeader.continuity_ct);	
			 //printf( "%04d(%03d)	", TSHeader.pid, TSHeader.continuity_ct	);

			 //processing next	packet data	
			 //bPacketParsed = true;
			 if ( dwBytes >= used_bytes + TS_PACKET_LENGTH )
			 {
				 pData += TS_PACKET_LENGTH;
				 used_bytes += TS_PACKET_LENGTH;
			 }
		
		 } else	
		 {
			 if ( !bSyncFound )
			 {
				 pParser->err_frames++;				
				 pData += 1;
				 used_bytes += 1;
			 }
			 else //not playload packet, it is served as PCR packets
			 {
				if ( pParser->enable_pid_stat )
				{
					int pid_array_id;
					if ( ( pid_array_id = LookUpPidHit( pParser, pParser->TSHeader.pid ) )<0 )
						pid_array_id = AddPidArray( pParser, pParser->TSHeader.pid );
					if ( pid_array_id >= 0 ) 
					{
						pParser->pid_array[pid_array_id].hits++;
						if ( pParser->TSHeader.scrambling_ctr )
							pParser->pid_array[pid_array_id].scrambled = pParser->TSHeader.scrambling_ctr;
					}
				}

				if ( pParser->PCR != 0 )
				{
					if ( pParser->pcr_pid == pParser->TSHeader.pid  )
					{
						//calc stream rate
						CalcStreamPacketInterval( pParser, pParser->PCR, pParser->packets );
						pParser->StreamPCR = pParser->PCR; 
						pParser->pcr_packet_syn = pParser->packets;

						if ( pParser->av_dumper != NULL  )
						{
							TS_AV_INFO av_info;
							short ChannelNum = GetChannelNum( pParser, pParser->WorkingProgram );
							av_info.type = PCR_STREAM_TYPE;;
							av_info.data = NULL;
							av_info.bytes = 0; 
							av_info.sub_channel = -1;
							av_info.start_group_flag = 0;
							av_info.pid = pParser->TSHeader.pid;
							av_info.continuity = pParser->TSHeader.continuity_ct;
							av_info.PCR = pParser->StreamPCR; 
							av_info.header_bytes = 0;
							pParser->av_dumper( (void*)pParser, ChannelNum, (void*)&av_info );
						}
					} else 
					{
					if ( pParser->TSHeader.pcr_flag ) 
						RegisterPCRPid( pParser, pParser->TSHeader.pid );
						
						if ( pParser->video_frames > 10 && pParser->pcr_pid == 0  ) 
						{
							pParser->pcr_pid = ReselectPCRPID( pParser );
							if ( pParser->pcr_pid != 0 ) flog(( "native.log", "\t\t pcr pid reset at 'PCR stream packet '\n" ));
							if ( pParser->pcr_pid != 0 ) NotifyPcrPID( pParser );
						}
					}
				}
				 //processing next	packet data	
				//bPacketParsed = true;
				if ( dwBytes >= used_bytes + TS_PACKET_LENGTH )
				{
					pData += TS_PACKET_LENGTH;
					used_bytes += TS_PACKET_LENGTH;
				}
			 }

		 }

	}


	return used_bytes;

}


bool IsPMTPacket( TS_PARSER* pParser, short Pid, short *pProgram	)
{
	register int i;

	for	( i	= 0; i<pParser->Pat.NumPrograms;	i++	)
	{
		if ( pParser->Pat.ProgramPID[i] == Pid && pParser->Pat.ProgramNumber[i] !=0 )	//ProgramNum 0 is NIT packet
		{
			*pProgram =	pParser->Pat.ProgramNumber[i];
			return true;
		}
	}
	return false;
}

//DVB stream may carries multiple PAT tables with different TSID, we lock a PAT with this API
void SelectTSID(  TS_PARSER* pParser, unsigned short tsid )
{
	pParser->SelectedTSID = tsid;
}

bool SelectChannel( TS_PARSER* pParser, unsigned short ChannelNum )
{
	pParser->SelectedChannel[0] = ChannelNum;
	pParser->SelectedChannelNum = 1;
	pParser->ChannelState[0] = 0;
	pParser->WorkingProgram = pParser->Channel2Program[ChannelNum];
	pParser->pcr_pid = GetSelectedProgramPcrPID( pParser );
	//if ( pParser->pcr_pid != 0 ) flog(( "native.log", "\t\t pcr pid reset at 'SelectChannel' \n" ));
	if ( pParser->pcr_pid != 0 ) NotifyPcrPID( pParser );
	return true;
}

//bool SelectChannel( TS_PARSER* pParser, unsigned short ChannelNum )
//{
//	int i;
//	for ( i = 0; i<pParser->SelectedChannelNum; i++ )
//		if ( pParser->SelectedChannel[i] == ChannelNum )
//			break;
//
//	//if selected channel in table, add it to it
//	if ( i>=pParser->SelectedChannelNum && i < MAX_PROGRAM )
//	{
//		i = pParser->SelectedChannelNum++;
//		pParser->SelectedChannel[i] = ChannelNum;
//		pParser->ChannelState[i] = 0;
//		return true;
//	}
//	return false;
//}	

int SelectProgram( TS_PARSER* pParser, unsigned short ProgramNum )
{
	pParser->SelectedProgram[0] = ProgramNum;
	pParser->SelectedProgramNum =1;
	return SetSeletedChannelByProgramNum( pParser, ProgramNum );
}

//int SelectProgram( TS_PARSER* pParser, unsigned short ProgramNum )
//{
//	int i;
//	for ( i = 0; i<pParser->SelectedProgramNum; i++ )
//		if ( pParser->SelectedProgram[i] == ProgramNum )
//			break;
//
//	//if selected channel in table, add it to it
//	if ( i>=pParser->SelectedProgramNum && i < MAX_PROGRAM )
//	{
//		i = pParser->SelectedProgramNum++;
//		pParser->SelectedProgram[i] = ProgramNum;
//	}
//	
//	return SetSeletedChannelByProgramNum( pParser, ProgramNum );
//
//}	
//
bool PickupChannel( TS_PARSER* pParser, unsigned short ChannelNum )
{
	int PmtIndex;
	pParser->SelectedChannelNum = 1;
	pParser->SelectedChannel[0] = ChannelNum;
	if ( pParser->PatState == 0 || !( pParser->ChannelState[ChannelNum] & MASK_PMT_READY ) )
	{
		return false;
	}

	PmtIndex = SeekPmtByChannel( pParser, ChannelNum );	
	if ( PmtIndex < 0 ) return false;

	pParser->WorkingProgram = pParser->Channel2Program[ChannelNum];
	pParser->pcr_pid = GetSelectedProgramPcrPID( pParser );
	if ( pParser->pcr_pid != 0 ) flog(( "native.log", "\t\t pcr pid reset at 'PickupChannel' \n" ));
	if ( pParser->pcr_pid != 0 ) NotifyPcrPID( pParser );

	pParser->ChannelState[ChannelNum] &= ~MASK_VIDEO_READY;
	pParser->ChannelState[ChannelNum] &= ~MASK_AUDIO_READY;

	if ( pParser->signal_dumper )
	{
		int ret;
		SIGNAL_INFO Signal;
		Signal.type = STREAM_READY;
		Signal.pid  = GetPMTPid( pParser, ChannelNum );
		ret = pParser->signal_dumper( (void*)pParser, ChannelNum, &Signal ); 
	}
	
	if ( pParser->section_dumper != NULL && PmtIndex >= 0 )
	{
		SECTION_INFO section_info;
		section_info.pid    = GetPMTPid( pParser, ChannelNum );
		section_info.service= pParser->Channel2Program[ChannelNum];
		section_info.channel = ChannelNum;
		section_info.data   = pParser->Pmt[PmtIndex].section->data;;
		section_info.length = pParser->Pmt[PmtIndex].section->bytes;
		section_info.parser = pParser;
		pParser->section_dumper( (void*)pParser, sizeof(section_info), (void*)&section_info );
	}
	
	//dump PMT table for CAM
	if ( pParser->PMT_dumper != NULL && PmtIndex >= 0 )
	{
		SECTION_INFO section_info;
		section_info.pid     = GetPMTPid( pParser, ChannelNum );
		section_info.service = pParser->Channel2Program[ChannelNum];
		section_info.channel = ChannelNum;
		section_info.tbl     = &pParser->Pmt[PmtIndex];
		section_info.data   = pParser->Pmt[PmtIndex].section->data;;
		section_info.length = pParser->Pmt[PmtIndex].section->bytes;
		section_info.parser = pParser;
		pParser->PMT_dumper(  pParser->PMT_dumper_context, sizeof(section_info), (void*)&section_info );

	}
	return true;
}	

int PickupProgram( TS_PARSER* pParser, unsigned short ProgramNum )
{
	int PmtIndex;
	unsigned short ChannelNum;
	if ( ProgramNum == 0x0 || ProgramNum == 0xffff )
		return -1;

	pParser->SelectedProgramNum = 1;
	pParser->SelectedProgram[0] = ProgramNum;
	
	if ( pParser->PatState == 0 ) 
		return -1;
	
	ChannelNum = GetChannelNum(pParser, ProgramNum );
	if ( ChannelNum == 0xffff )
		return -1;
	PmtIndex = SeekPmtByProgram( pParser, ProgramNum );	
	if ( PmtIndex < 0 )
		return -1;

	pParser->WorkingProgram = pParser->Channel2Program[ChannelNum];
	pParser->pcr_pid = GetSelectedProgramPcrPID( pParser );
	if ( pParser->pcr_pid != 0 ) flog(( "native.log", "\t\t pcr pid reset at 'PickupProgram' \n" ));
	if ( pParser->pcr_pid != 0 ) NotifyPcrPID( pParser );
    assert( pParser->WorkingProgram == ProgramNum );

	pParser->SelectedChannelNum = 1;
	pParser->SelectedChannel[0] = ChannelNum;
	pParser->ChannelState[ChannelNum] &= ~MASK_VIDEO_READY;
	pParser->ChannelState[ChannelNum] &= ~MASK_AUDIO_READY;
	
	if ( !( pParser->ChannelState[ChannelNum] & MASK_PMT_READY ) )
		return ChannelNum;

	if ( pParser->signal_dumper )
	{
		int ret;
		SIGNAL_INFO Signal;
		Signal.type = STREAM_READY;
		Signal.pid  = GetPMTPid( pParser, ChannelNum );;
		ret = pParser->signal_dumper( (void*)pParser, ChannelNum, &Signal ); 
	}
	
	if ( pParser->section_dumper != NULL && PmtIndex >= 0 )
	{
		SECTION_INFO section_info;
		section_info.pid     = GetPMTPid( pParser, ChannelNum );;
		section_info.service = pParser->Channel2Program[ChannelNum];
		section_info.channel = ChannelNum;
		section_info.data    = pParser->Pmt[PmtIndex].section->data;;
		section_info.length  = pParser->Pmt[PmtIndex].section->bytes;
		section_info.parser  = pParser;
		pParser->section_dumper( (void*)pParser, sizeof(section_info), (void*)&section_info );
	}
	
	//dump PMT table for CAM
	if ( pParser->PMT_dumper != NULL && PmtIndex >= 0 )
	{
		SECTION_INFO section_info;
		section_info.pid     = GetPMTPid( pParser, ChannelNum );
		section_info.service = pParser->Channel2Program[ChannelNum];
		section_info.channel = ChannelNum;
		section_info.tbl     = &pParser->Pmt[PmtIndex];
		section_info.data   = pParser->Pmt[PmtIndex].section->data;;
		section_info.length = pParser->Pmt[PmtIndex].section->bytes;
		section_info.parser = pParser;
		pParser->PMT_dumper(  pParser->PMT_dumper_context, sizeof(section_info), (void*)&section_info );
	}
	return ChannelNum;
}	


int  GetTSProgramNum( TS_PARSER* pParser, unsigned short Channel )
{
	if ( Channel > MAX_PROGRAM )
		return 0;

	return pParser->Channel2Program[ Channel ];
}

int  GetTSChannelNum( TS_PARSER* pParser, unsigned short ProgramNum )
{
	int i;
	for	( i	= 0; i<pParser->TSChannelNum;	i++	)
	{
		if ( pParser->Channel2Program[ i ] == ProgramNum )	
				return i;
	}
	return -1;
}

unsigned short GetTSTSID( TS_PARSER* pParser )
{
	return pParser->WorkingTSID;
}

int GetProgramNumber( TS_PARSER* pParser )
{
	if ( pParser == NULL )
		return 0;

	return pParser->TSChannelNum;
}

unsigned short GetProgramTSID( TS_PARSER* pParser )
{
	return pParser->Pat.TSID;
}

int GetProgramList( TS_PARSER* pParser, PROGRAM_INFO* outProgramInfo, int numPrograms )
{
	int i, num;
	if ( outProgramInfo == NULL || pParser == NULL )
		return -1;

	num = _MIN( numPrograms, pParser->TSChannelNum );
	for ( i = 0; i<num; i++ )
	{
		int index, k;
		outProgramInfo[i].channelID = i;
		outProgramInfo[i].programID = pParser->Channel2Program[i];
		outProgramInfo[i].pid = GetPMTPid( pParser, i );
		outProgramInfo[i].type = 0;
		index = SeekPmtByChannel( pParser, i );
		if ( index >= 0 )
		{
			for ( k = 0; k<pParser->Pmt[index].NumStreams; k++ )
			{
				if ( IsVideoType( pParser->Pmt[index].StreamType[k] ) )
					outProgramInfo[i].type = 1;
				if ( IsAudioType( pParser->Pmt[index].StreamType[k] ) )
					outProgramInfo[i].type |= 2;
			}
		}
	}
	return num;
}

int GetVideoProgramNumber( TS_PARSER* pParser )
{
	int num, i, k;
	if ( pParser == NULL )
		return 0;

	num = 0;
	for ( i = 0; i<(int)pParser->Pat.NumPrograms && i<MAX_PROGRAM; i++ )
	{
		for ( k = 0; k<pParser->Pmt[i].NumStreams && k < MAX_ES; k++ )
		{
			if ( IsVideoType( pParser->Pmt[i].StreamType[k] ) )
			{
				num++;
				break;
			}
		}
	}
	return num;
}

int CheckProgramVaild( TS_PARSER* pParser, unsigned short ProgramId )
{
	int i;
	for ( i = 0; i<pParser->Pat.NumPrograms; i++ )
	{
		if ( pParser->Pat.ProgramNumber[ i ] == ProgramId )
			return true;
	}
	return false;

}

int GetStreamNum( TS_PARSER* pParser, short ChannelNum )
{
	int index, i, num;
	if ( pParser == NULL || ChannelNum < 0 || ChannelNum > MAX_PROGRAM )
		return -1;

	num = 0;
	index = SeekPmtByChannel( pParser, ChannelNum );
	if ( index >= 0 ) 
	{
		for ( i = 0; i<pParser->Pmt[index].NumStreams; i++ )
		{
			if ( IsVideoType( pParser->Pmt[index].StreamType[i] ) ||
				 IsAudioType( pParser->Pmt[index].StreamType[i] )  )
				num++;
		}
		return num+1;
	}

	return 0;
}

int GetStreamList( TS_PARSER* pParser, short ChannelNum , AVSTREAM_INFO* outStreamInfo, int numPrograms )
{
	int index, i, num, len; 
	short ProgramID;

	if ( pParser == NULL || ChannelNum < 0 || ChannelNum > MAX_PROGRAM )
		return -1;

	if ( numPrograms < 1 || outStreamInfo == NULL )
		return 0;

	num = 0;
	ProgramID = pParser->Channel2Program[ ChannelNum ];
	index = SeekPmtByChannel( pParser, ChannelNum );
	if ( index >= 0 ) 
	{
		outStreamInfo[num].channelID = ChannelNum;
		outStreamInfo[num].programID = ProgramID;
		outStreamInfo[num].streamIndex = -1;
		outStreamInfo[num].streamType  = -1;
		outStreamInfo[num].pid = GetPMTPid( pParser, ChannelNum );
		len = _MIN( (char)pParser->Pmt[index].ProgramInfoLength, sizeof(outStreamInfo[num].Desc) );
		if ( pParser->Pmt[index].ProgramInfo && len > 0 )
		{
			outStreamInfo[num].Desc[0] = len;
			memcpy( outStreamInfo[num].Desc+1, pParser->Pmt[index].ProgramInfo, pParser->Pmt[index].ProgramInfoLength );
		} else
		{
			outStreamInfo[num].Desc[0] = outStreamInfo[num].Desc[1] = 0x0;
		}
		num++;

		for ( i = 0; i<pParser->Pmt[index].NumStreams && num < numPrograms; i++ )
		{
			unsigned char StreamType = pParser->Pmt[index].StreamType[i];
			if ( IsVideoType( StreamType )|| IsAudioType( StreamType  ) )
			{
				outStreamInfo[num].channelID = ChannelNum;
				outStreamInfo[num].programID = ProgramID;
				outStreamInfo[num].streamIndex = i;
				outStreamInfo[num].streamType  = StreamType;
				outStreamInfo[num].pid = pParser->Pmt[index].ElementaryPID[i];
				len = _MIN((int)pParser->Pmt[index].ESInfoLength[i], sizeof(outStreamInfo[num].Desc)-1 );
				if ( pParser->Pmt[index].ESInfo[i] != NULL && len > 0 )
				{
					outStreamInfo[num].Desc[0] = len;
					memcpy( outStreamInfo[num].Desc+1, pParser->Pmt[index].ESInfo[i], len );
				} else
				{
					outStreamInfo[num].Desc[0] = outStreamInfo[num].Desc[1] = 0x0;
				}
				num++;
			}
		}
		return num;
	}

	return 0;

}

int TSLockPatPmt( TS_PARSER* pParser )
{
	int old_state = pParser->LockPmtPat;
	pParser->LockPmtPat = 1;
	return old_state;
}

int TSUnLockPatPmt( TS_PARSER* pParser )
{
	int old_state = pParser->LockPmtPat;
	pParser->LockPmtPat = 0;
	return old_state;
}


LONGLONG TSDataPosition( TS_PARSER* pParser )
{
	return  pParser->data_pos + pParser->data_offset;
}

void   TSResetDataPosition( TS_PARSER* pParser )
{
	pParser->data_pos = 0;
	pParser->data_offset = 0;
}

void SetTSDumpAVCallback( TS_PARSER* pParser,  LPFNParserDump dumper )
{
	pParser->av_dumper = dumper;
}

void SetTSDumpSignalCallback( TS_PARSER* pParser,  LPFNParserDump dumper )
{
	pParser->signal_dumper = dumper;
}



void SetStreamType( TS_PARSER* pParser, STREAM_TYPE StreamType )
{
    pParser->StreamType = StreamType;
}

void SetTSDumpSectionCallback(TS_PARSER* pParser,  LPFNParserDump dumper, void* context)
{
	pParser->section_dumper_context = context;
	pParser->section_dumper = dumper;
}

void SetTSDumpPATCallback(TS_PARSER* pParser,  LPFNParserDump dumper, void* context )
{
	pParser->PAT_dumper = dumper;
	pParser->PAT_dumper_context = context;
}

void SetTSDumpPMTCallback(TS_PARSER* pParser,  LPFNParserDump dumper, void* context)
{
	pParser->PMT_dumper = dumper;
	pParser->PMT_dumper_context = context;
}

bool IsScrambledProgram( TS_PARSER* pParser, unsigned short Program )
{
	int j, i, pid_array_id;
	bool video_clear = false, audio_clear = false;
	int  video_flag = 0, audio_flag = 0;

	if ( pParser == NULL || Program == 0xffff  )
		return false;

	for	( i	= 0; i<MAX_PROGRAM; i++	)
	{
		if ( pParser->Pmt[i].SectionLength && pParser->Pmt[i].ProgramNumber == Program )
		{
			for ( j = 0; j < pParser->Pmt[i].NumStreams; j++ )
			{
				if ( IsVideoType( pParser->Pmt[i].StreamType[j] ) )
				{
					if ( ( pid_array_id = LookUpPidHit( pParser, pParser->Pmt[i].ElementaryPID[j] ) ) >= 0 )
					{
						video_flag = pParser->pid_array[pid_array_id].scrambled;
						if (  video_flag != 0x02 && video_flag != 03 ) video_clear = true;
					}
				}

				if (  IsAudioType( pParser->Pmt[i].StreamType[j] ) )
				{
					if ( ( pid_array_id = LookUpPidHit( pParser, pParser->Pmt[i].ElementaryPID[j] ) ) >= 0 )
					{
						audio_flag = pParser->pid_array[pid_array_id].scrambled;
						if (  audio_flag != 0x02 && audio_flag != 03 ) audio_clear = true;
					}
				}

				if ( video_clear && audio_clear ) //there is some kind of stream partial scrambled, but watchable.
					return false;
			}
		}
	}

	if ( !video_clear && !audio_clear ) return (audio_flag<<4)|video_flag;

	return false;
}

static bool LookupStream( TS_PARSER* pParser, unsigned short Pid, unsigned short *Program,  unsigned short* Stream )
{
	int j, i, k;

	if ( pParser == NULL || Pid == 0xffff  )
		return false;

	for	( k	= 0; k<pParser->Pat.NumPrograms; k++	)
	{
		if ( pParser->Pat.ProgramPID[k] == Pid )
		{
			*Program = pParser->Pat.ProgramNumber[k];
			*Stream = 0xffff;
			return true;
		}
		for ( i = 0; i<MAX_PROGRAM; i++ )
		{
			if ( pParser->Pmt[i].NumStreams && pParser->Pmt[i].ProgramNumber == pParser->Pat.ProgramPID[k] )
			{
				for ( j = 0; j < pParser->Pmt[i].NumStreams; j++ )
				{
					if ( pParser->Pmt[i].ElementaryPID [j]== Pid )
					{
						*Program = pParser->Pat.ProgramNumber[k];
						*Stream = j;
						return true;
					}
				}
			}
		}
	}
	return false;
}

unsigned long PidHits( TS_PARSER* pParser, unsigned short pid )
{
	int pid_array_id;
	pid_array_id = LookUpPidHit( pParser, pid );
	if ( pid_array_id < 0 )
		return 0;
	return pParser->pid_array[pid_array_id].hits;
}
unsigned long TotalPidHits( TS_PARSER* pParser )
{
	int i;
	unsigned long sum  = 0;
	for ( i = 0; i<MAX_PID_COLLECT; i++ )
	{
		if ( pParser->pid_array[i].state )
		{
			sum += pParser->pid_array[i].hits;
		}
	}
	return sum;
}

unsigned long ProgramPidHits( TS_PARSER* pParser, unsigned short Program )
{
	int j, i, pid_array_id;
	unsigned long sum_hits = 0;

	if ( pParser == NULL || Program == 0xffff  )
		return false;

	for	( i	= 0; i<MAX_PROGRAM; i++	)
	{
		if ( pParser->Pmt[i].SectionLength && pParser->Pmt[i].ProgramNumber == Program )
		{
			for ( j = 0; j < pParser->Pmt[i].NumStreams; j++ )
			{
				if ( IsVideoType( pParser->Pmt[i].StreamType[j] ) ||  IsAudioType( pParser->Pmt[i].StreamType[j] ) )
				{
					if ( ( pid_array_id = LookUpPidHit( pParser, pParser->Pmt[i].ElementaryPID[j] ) ) >= 0 )
					{
						sum_hits += pParser->pid_array[pid_array_id].hits;
					}
				}
			}
		}
	}
	return sum_hits;
}

unsigned long ChannelPidHits( TS_PARSER* pParser, unsigned short Channel )
{
	if ( Channel > MAX_PROGRAM )
		return false;
	return ProgramPidHits( pParser, pParser->Channel2Program[Channel] );
}

bool IsScrambledChannel( TS_PARSER* pParser, unsigned short Channel )
{
	if ( Channel > MAX_PROGRAM )
		return false;
	return IsScrambledProgram( pParser, pParser->Channel2Program[Channel] );
}

bool IsDVBStream( TS_PARSER* pParser )
{
	//check if there NIT,SDT,EIT,TDT packet
	unsigned long NIT_hits, SDT_hits, EIT_hits, TDT_hits;
	NIT_hits = PidHits( pParser, 0x010 );

	if ( NIT_hits > 1 ) 
	{
		unsigned short program, stream;
		if ( IsPMTPacket( pParser, 0x010, (short*)&program	) )
			return false;
		if ( LookupStream( pParser, 0x010, &program, &stream ) )
			return false;
	}
	SDT_hits = PidHits( pParser, 0x011 );
	if ( SDT_hits > 1 ) 
	{
		unsigned short program, stream;
		if ( LookupStream( pParser, 0x011, &program, &stream ) )
			return false;
	}

	EIT_hits = PidHits( pParser, 0x012 );
	if ( EIT_hits > 1 ) 
	{
		unsigned short program, stream;
		if ( LookupStream( pParser, 0x012, &program, &stream ) )
			return false;
	}

	TDT_hits = PidHits( pParser, 0x014 );
	if ( TDT_hits > 1 ) 
	{
		unsigned short program, stream;
		if ( LookupStream( pParser, 0x014, &program, &stream ) )
			return false;
	}

	if ( NIT_hits + TDT_hits + EIT_hits + SDT_hits > 10 )
		return true	;

	return false;
}

unsigned long GetPacketCounter( TS_PARSER* pParser )
{
	return pParser->packets;
}

void CalcStreamPacketInterval( TS_PARSER* pParser, LONGLONG PCR, unsigned long packets )
{
	LONGLONG k;
	if ( pParser->StreamIntervalSum > 0xf0000000 || packets == pParser->pcr_packet_syn ) 
		return;

	k = ( PCR - pParser->StreamPCR )/(packets - pParser->pcr_packet_syn );
	if ( k < 4000 && k > 800 )
	{
		pParser->StreamIntervalSum += (unsigned long)k;
		pParser->StreamIntervalNum++;
		pParser->packet_pcr_rate = pParser->StreamIntervalSum / pParser->StreamIntervalNum;
	}

//{
//	LONGLONG delta =  PCR - (pParser->StreamPCR + ( packets - pParser->pcr_packet_syn ) * pParser->packet_pcr_rate);
//	printf( "k:%d %d;  delta:%lld\n", (long)k,   pParser->packet_pcr_rate, (long)delta );
//}
}

//LONGLONG GetEstimatePCR( TS_PARSER* pParser )
//{
//	return pParser->StreamPCR + ( pParser->packets - pParser->pcr_packet_syn ) * pParser->packet_pcr_rate;
//}

unsigned long EstimatePCRIncrease( TS_PARSER* pParser, unsigned long packets )
{
	return  pParser->packet_pcr_rate * packets;
}

//parser descriptor information
#define ISO639_LANGUAGE_DESC  0x0a
#define TARGET__BKGRD_DESC	  0x07
#define VIDEO_WINDOW_DESC	  0x08	
#define TELETEX_DESC		  0x56
#define SUBTILTLE_DESC		  0x59

int GetPMTDescData( TS_PARSER* pParser, unsigned short pid, unsigned char** desc, int *bytes )
{
	int i, j;
	if ( pParser == NULL || pid == 0 ) return 0;
	for ( i = 0; i<MAX_PROGRAM; i++ )
		for ( j = 0; j<pParser->Pmt[i].NumStreams; j++ )
		{
			if ( pParser->Pmt[i].ElementaryPID[j] == pid )
			{
				*desc = pParser->Pmt[i].ESInfo[j];
				*bytes = pParser->Pmt[i].ESInfoLength[j];
				return 1;
			}
		}
	*desc = NULL;
	*bytes = 0;
	return 0;
}

int GetAudioLanguageInfo( unsigned char* desc, int bytes, unsigned long* language, unsigned char *type )
{
	unsigned char tag;
	int desc_bytes, i;
	if ( bytes < 3 || language == NULL ) return 0;
	i = 0;
	*language = 0;
	*type = 0;
	while ( i < bytes )
	{
		tag = desc[i];
		desc_bytes = (int)desc[i+1];
		i += 2;
		if ( tag == ISO639_LANGUAGE_DESC && i+desc_bytes <= bytes  )
		{
			*language = (desc[i]<<24)|(desc[i+1]<<16)|(desc[i+2]<<8);
			*type  = desc[i+3];
			return 1;
		}
		i += desc_bytes;
	}
	return 0;
}

int GetTelexLanguageInfo( unsigned char* desc, int bytes, unsigned long* language, unsigned char *type )
{
	unsigned char tag;
	int desc_bytes, i;
	if ( bytes < 3 || language == NULL ) return 0;
	i = 0;
	*language = 0;
	*type = 0;
	while ( i < bytes )
	{
		tag = desc[i];
		desc_bytes = (int)desc[i+1];
		i += 2;
		if ( ( tag == TELETEX_DESC || tag == SUBTILTLE_DESC ) && i+desc_bytes <= bytes  )
		{
			*language = (desc[i]<<24)|(desc[i+1]<<16)|(desc[i+2]<<8);
			*type  = desc[i+3];
			return tag;
		}
		i += desc_bytes;
	}
	return 0;
}

int GetVideoWindowInfo( unsigned char* desc, int bytes, unsigned short *hor, unsigned short *ver, unsigned char* priority )
{
	unsigned char tag;
	int desc_bytes, i;
	if ( bytes < 3 || desc == NULL ) return 0;
	i = 0;
	while ( i < bytes )
	{
		tag = desc[i];
		desc_bytes = (int)desc[i+1];
		i += 2;
		if ( tag == VIDEO_WINDOW_DESC && i+2+desc_bytes <= bytes )
		{
			*hor = ( desc[i] << 6 ) | ( (desc[i+1]&0xfc) >>2  );
			*ver = (( desc[i+1]& 0x03)<<12) | (desc[i+2]<<4) | ((desc[i+3]&0xf0)>>4);
			*priority = desc[i+3]&0x0f;
			return 1;
		}
		i += desc_bytes;
	}
	return 0;
}

int GetVideoBkgrdInfo( unsigned char* desc, int bytes, unsigned short *hor, unsigned short *ver, unsigned char* aspect )
{
	unsigned char tag;
	int desc_bytes, i;
	if ( bytes < 3 || desc == NULL ) return 0;
	i = 0;
	while ( i < bytes )
	{
		tag = desc[i];
		desc_bytes = (int)desc[i+1];
		i += 2;
		if ( tag == TARGET__BKGRD_DESC && i+2+desc_bytes <= bytes )
		{
			*hor = ( desc[i] << 6 ) | ( (desc[i+1]& 0xfc) >>2  );
			*ver = (( desc[i+1] & 0x03)<<12) | (desc[i+2]<<4) | ((desc[i+3]&0xf0)>>4);
			*aspect = desc[i+3]&0x0f;
			return 1;
		}
		i += desc_bytes;
	}
	return 0;
}
//0: no AV; 1:video only; 2:audio only; 3:video+audio; -1:unkown;
int GetProgramAVType( TS_PARSER* pParser, unsigned short Program )
{
	int i, j, type = -1;
	for	( i	= 0; i<MAX_PROGRAM; i++	)
	{
		if ( pParser->Pmt[i].SectionLength && pParser->Pmt[i].ProgramNumber == Program )
		{
			type = 0;
			for ( j = 0; j < pParser->Pmt[i].NumStreams; j++ )
			{
				if ( IsVideoType( pParser->Pmt[i].StreamType[j] ) )
					type |= 0x1;
				if ( IsAudioType( pParser->Pmt[i].StreamType[j] ) )
					type |= 0x2;
			}
		}
	}
	return type;
}

int CheckProgramState( TS_PARSER* pParser, unsigned short Program )
{
	int j, i, pid_array_id;
	int av_stream_present = 0;
	int packet_present = 0;

	if ( pParser == NULL || Program == 0xffff  )
		return -1;

	for	( i	= 0; i<MAX_PROGRAM; i++	)
	{
		if ( pParser->Pmt[i].SectionLength && pParser->Pmt[i].ProgramNumber == Program )
		{
			for ( j = 0; j < pParser->Pmt[i].NumStreams; j++ )
			{
				if ( IsVideoType( pParser->Pmt[i].StreamType[j] ) || IsAudioType( pParser->Pmt[i].StreamType[j] ) )
				{
					if ( ( pid_array_id = LookUpPidHit( pParser, pParser->Pmt[i].ElementaryPID[j] ) ) >= 0 )
					{
						unsigned long flag = pParser->pid_array[pid_array_id].scrambled;
						packet_present++;
					        if ( flag == 0x02 || flag == 03 )
							return -5; //scrambled data
					} else
					
					av_stream_present++;
				}
			}
			if ( av_stream_present == 0 )
				return -3; //program found, but stream is not ready
			else
			{
				if ( packet_present == 0 )
					return -4; //program found, but stream ready, but data is not present;
				else
				return 0; //program found,  stream ready, data present;
			}
			
		}
	}
	return -2; //program is not found
}

void ResetTSSoftPidFilter( TS_PARSER* pParser )
{
	pParser->pid_filter_num = 0;
}

int SetTSSoftPidFilter( TS_PARSER* pParser, unsigned short pid )
{
	if ( pParser->pid_filter_num >= MAX_PID_COLLECT )
		return -1;
	pParser->pid_filter_tbl[pParser->pid_filter_num] = pid;
	pParser->pid_filter_num++;
	return pParser->pid_filter_num;
}

unsigned long GetCRC32( const unsigned char *pData, int len )
{
	return CalCrcCheck( pData, len );
}

//check service TSID is valid, all pmts in PAT are present.
//valid tsid: return 1; uncertainty: return 0; invalid: returnn -1
static void SavePAT( TS_PARSER* pParser, TS_PAT* Pat )
{
	int i;
	for ( i = 0; i<pParser->pats_num; i++ )
	{
		if ( pParser->PATs[i].TSID == Pat->TSID )
			return;
	}
	pParser->PATs[pParser->pats_index] = *Pat;
	if ( ++pParser->pats_index >= MAX_PAT_NUM	) pParser->pats_index = 0;
	if ( pParser->pats_num < MAX_PAT_NUM ) pParser->pats_num++;
}
int CheckTSIDValid( TS_PARSER* pParser, unsigned short tsid )
{
	int i, j, num;
	if ( pParser->pmt_frames == 0 || pParser->packets < 2000 )
		return 0;

	for ( i = 0; i<pParser->pats_num; i++ )
	{
		if ( pParser->PATs[i].TSID == tsid )
		{
			num = 0;
			for ( j=0; j<pParser->PATs[i].NumPrograms; j++ )
			{
				if ( LookUpPidHit( pParser, pParser->PATs[i].ProgramPID[j] ) >0 )
					num++;
			}
			if ( num >= pParser->PATs[i].NumPrograms )
				return 1;
			if ( num == 0 )
				return -1;
			return 0;
		}
	}

	return -1;
}

int GetTSIDList( TS_PARSER* pParser, unsigned short* tsid_list, int max_size )
{
	int i;
	int max_list_num = max_size/2;
	for ( i = 0; i<pParser->pats_num && i<max_list_num; i++ )
	{
		tsid_list[i] = pParser->PATs[i].TSID;
	}
	if ( i<max_list_num ) tsid_list[i]=0x0;
	return i;
}
