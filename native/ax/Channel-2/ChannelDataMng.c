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
#ifdef WIN32
#pragma warning(disable : 4996)
#pragma warning(disable: 4702)
#define _USE_32BIT_TIME_T
#endif
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <memory.h>
#include "../../include/uniapi.h" 

#include "NativeCore.h"
#include "Remuxer.h"
#include "ChannelScan.h"
#include "Channel.h"

#include "Channel.h"


#ifdef WIN32
//*********************** WINDOWS section *********************

#if( _MSC_VER <= 800 )
#pragma pack(1)  
#else
#include <pshpack1.h>
#endif

//*********************** WINDOWS section *********************
#else
#ifdef __APPLE__
//*********************** MAC section *************************
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/errno.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <getopt.h>
#include <errno.h>
#include <sys/time.h>
#include <math.h>
#include <time.h>
#include <sys/timeb.h>
#include <sys/types.h>
#include <sys/un.h>
#include <stdarg.h>
int TranslateJWideString2( char* buf_out, int buf_out_size, unsigned short* buf_in );
int TranslateJWideString( char* buf_out, int buf_out_size, unsigned short* buf_in );

// Apple OS/X has no features.h
// Apple OS/X handles types via machine/types.h
#include <machine/types.h>
#include <libkern/OSByteOrder.h>

//*********************** MAC section *************************
#else
//*********************** LINUX section ***********************
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/errno.h>
#include <unistd.h>
#include <features.h> 
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <getopt.h>
#include <errno.h>
#include <sys/time.h>
#include <math.h>
#include <linux/types.h>
#include <time.h>
#include <sys/timeb.h>
#include <sys/types.h>
#include <sys/un.h>
#include <stdarg.h>

#include <linux/dvb/frontend.h>
int TranslateJWideString2( char* buf_out, int buf_out_size, uint16_t* buf_in );
int TranslateJWideString( char* buf_out, int buf_out_size, uint16_t* buf_in );


//*********************** LINUX section ***********************
#endif
#endif

#ifndef min
#define min(x,y)   (x)>(y)? (y):(x)
#endif

#include <stdarg.h>
#include <time.h>

#ifdef WIN32   
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}

char *strtok_r (char *s, const char *delim, char **save_ptr)
{
  char *token;

  if (s == NULL)
    s = *save_ptr;

  /* Scan leading delimiters.  */
  s += strspn (s, delim);
  if (*s == '\0')
    {
      *save_ptr = s;
      return NULL;
    }

  /* Find the end of the token.  */
  token = s;
  s = strpbrk (token, delim);
  if (s == NULL)
    /* This token finishes the string.  */
    *save_ptr = strchr (token, '\0');
  else
    {
      /* Terminate the token and make *SAVE_PTR point past it.  */
      *s = '\0';
      *save_ptr = s + 1;
    }
  return token;
}
#undef SageLog
#define SageLog(x)   _sagelog_l x
void _sagelog_l( int type, int level, const char* cstr, ... );
#endif

/*
#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _flog x
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

*/
/*
static void _flog_check()
{
	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
	}
}

void _set_ChannelDataMng_log( bool enable )
{
	flog_enabled = enable;
}
*/
static int  getNextTextField( char* p_in, char **p_out, char* text, int max_len );
static bool getNextIntField( char* p_in, char **p_out, int* int_val );
static bool getNextShortField( char* p_in, char **p_out, uint16_t* short_val );
static bool parseATSCFreqEntry( char* p_in, ATSC_FREQ* atsc_freq ) ;
static bool parseQAMFreqEntry( char* p_in, QAM_FREQ* qam_freq );
static bool parseUserQAMFreqData( char* p_in, struct bcast_qam *Freq, char **error );
static bool parseScanATSCFreqData( char* p, ATSC_FREQ *Freq, char **error );
static bool parseScanQAMFreqData( char* p, QAM_FREQ *Freq, char **error );
char* makeATSCFreqString( char*Buf, int Size,  ATSC_FREQ * Freq  );
char* makeQAMFreqString( char*Buf, int Size,  QAM_FREQ * Freq  );
static bool  parseDVBTFreqEntry( char* p_in, DVB_T_FREQ * Freq, char** error  );
char* makeDVBTFreqString( char*Buf, int Size,  DVB_T_FREQ * Freq  );
static bool  parseDVBCFreqEntry( char* p_in, DVB_C_FREQ * Freq, char** error  );
char* makeDVBCFreqString( char*Buf, int Size,  DVB_C_FREQ * Freq  );
static bool  parseDVBSFreqEntry( char* p_in, DVB_S_FREQ * Freq, char** error  );
char* makeDVBSFreqString( char*Buf, int Size,  DVB_S_FREQ * Freq  );
bool GetString( char*p, const char* Name, char* Str, int MaxLen );
uint32_t fileTimeStamp( char* szFileName );
static char version_string[] ="VERSION 3.0, country:%s\r\n";
static char type_string[] ="TYPE %s\r\n";
 
static char desc_string_ATSC[] =
"\
#fec_rate:     1:fec1/2, 2:fec2/3, 3:fec3/4, 4:fec3/5, 5:fec4/5, 6:fec5/6, 7:fec5/11, 8:fec7/8 9:fec1/4 10:fec1/3\n\
#			   11:fec2/5 12:fec6/7 13:fec8/9 14:fec8/9 15:max\n\
#fec:          1:VBC, 2:RS/2048, 3:maximum	\n\
#polarisation: 1:linear_H, 2:Linear_V, 3:Circular_L, 4:Circular_R\n\
#modualtion:   1:16QAM:1, 2:32QAM, 3:64QAM, 4:80QAM, 5:96QAM, 6:112QAM, 7:128QAM, 8:160QAM, 9:192QAM, 10:224QAM, \n\
#              11:256QAM, 12:320QAM, 13:384QAM, 14:448QAM, 15:512QAM, 16:640QAM, 17:768QAM, 18:896QAM, 19:1024QAM, \n\
#              20:QPSK, 21:BPSK, 22:OQPSK, 23:8VSB:23, 24:16VSB 31:NBC_QPSK 32:NBC_8PSK   \n\
#ctrlbits:     0:disable, 1:enable, 3:encrypted  \n\
";

static char desc_string_DVB[] =
"\
#fec_rate:     1:fec1/2, 2:fec2/3, 3:fec3/4, 4:fec3/5, 5:fec4/5, 6:fec5/6, 7:fec5/11, 8:fec7/8 9:fec8/9\n\
#fec:          1:VBC, 2:RS/2048, 3:maximum	\n\
#polarisation: 1:linear_H, 2:Linear_V, 3:Circular_L, 4:Circular_R\n\
#modualtion:   0:QPSK, 1:16QAM, 2:32QAM, 3:64QAM, 4:128QAM, 5:256QAM, 6:QAM_AUTO, 7:8VSB 8:16VSB \n\
#ctrlbits:     0:diasable, 1:enable, 3:encrypted  \n\
";
static char dvbt_tuning_format[] =
"\
#format:  \n\
#  ch:index onid:networkid tsid:transport_id sid:sevrice_id frq:frequency band:band_width ctrl:ctrlbits\n\
#example: \n\
#  CH:11 onid:123 sid:456 tsid:789 frq:670000 band:8 ctrl:1  #:BBC-WORD\n\n\n\
";

static char dvbc_tuning_format[] =
"\
#format:  \n\
#  ch:index onid:networkid tsid:transport_id sid:sevrice_id frq:frequency rate:symbl_rate mod:moduration fec_rate_in:FEC_inner_rate ctrl:ctrlbits\n\
#example: \n\
#  CH:11 onid:123 sid:456 tsid:789 frq:670000 rate:2200 fec_rate_in:6 mod:3 ctrl:1  #:BBC-WORD\n\n\n\
";
static char dvbs_tuning_format[] =
"\
#format:  \n\
#  ch:index onid:networkid tsid:transport_id sid:sevrice_id frq:frequency rate:symbl_rate pol:polarisation fec_rate_in:FEC_inner_rate ctrl:ctrlbits\n\
#example: \n\
#  CH:11 onid:123 sid:456 tsid:789 frq:670000 rate:2200 fec_rate_in:6 pol:1 ctrl:1  #:BBC-WORD\n\n\n\
";

static char atsc_tuning_format[] =
"\
#format:  \n\
";

static char qam_tuning_format[] =
"\
#format:  \n\
";

int ConvertIllegalChar( char* filename );

int initialTuningTable( CHANNEL_DATA *Channel )
{
	if ( !strcmp( Channel->sourceType, "ATSC" ) )
	{
		int size;
		SageLog(( _LOG_TRACE, 3, "ATSC Frequency initialize 0x%0x\r\n", Channel->FreqTable.tbl.atsc ));
		if ( Channel->FreqTable.tbl.atsc != NULL )
		{
			
			free( Channel->FreqTable.tbl.atsc );
			Channel->FreqTable.tbl.atsc = NULL;
		}
		Channel->FreqTable.totalNum = MAX_ATSC_FREQ;
		Channel->FreqTable.entryNum = 0;
		strcpy( Channel->FreqTable.sourceType, "ATSC" );
		size = sizeof(ATSC_FREQ) * Channel->FreqTable.totalNum;
		Channel->FreqTable.tbl.atsc = (ATSC_FREQ*)calloc( 1, size+16 );
		
	} else
	if ( !strcmp( Channel->sourceType, "QAM" ) )
	{
		int size;
		SageLog(( _LOG_TRACE, 3, "QAM Frequency initialize :%0x\r\n", Channel->FreqTable.tbl.qam ));
		if ( Channel->FreqTable.tbl.qam != NULL )
		{
			free( Channel->FreqTable.tbl.qam );
			Channel->FreqTable.tbl.qam = NULL;
		}
		Channel->FreqTable.totalNum = MAX_QAM_FREQ;
		Channel->FreqTable.entryNum = 0;
		strcpy( Channel->FreqTable.sourceType, "QAM" );
		size = sizeof(QAM_FREQ) * Channel->FreqTable.totalNum;
		Channel->FreqTable.tbl.qam = (QAM_FREQ*)malloc( size+16 );
		memset( Channel->FreqTable.tbl.qam, 0, size );
	} else
	if ( !strcmp( Channel->sourceType, "DVB-T" ) )
	{
		int size;
		SageLog(( _LOG_TRACE, 3, "DVB-T Frequency initialize :%0x\r\n", Channel->FreqTable.tbl.dvbt ));
		if ( Channel->FreqTable.tbl.dvbt != NULL )
		{
			free( Channel->FreqTable.tbl.dvbt );
			Channel->FreqTable.tbl.dvbt = NULL;
		}
		Channel->FreqTable.totalNum = MAX_QAM_FREQ;
		Channel->FreqTable.entryNum = 0;
		strcpy( Channel->FreqTable.sourceType, "DVB-T" );
		size = sizeof(DVB_T_FREQ) * Channel->FreqTable.totalNum;
		Channel->FreqTable.tbl.dvbt = (DVB_T_FREQ*)malloc( size+16 );
		memset( Channel->FreqTable.tbl.dvbt, 0, size );
	} else
	if ( !strcmp( Channel->sourceType, "DVB-C" ) )
	{
		int size;
		SageLog(( _LOG_TRACE, 3, "DVB-C Frequency initialize :%0x\r\n", Channel->FreqTable.tbl.dvbc ));
		if ( Channel->FreqTable.tbl.dvbc != NULL )
		{
			free( Channel->FreqTable.tbl.dvbc );
			Channel->FreqTable.tbl.dvbc = NULL;
		}
		Channel->FreqTable.totalNum = MAX_QAM_FREQ;
		Channel->FreqTable.entryNum = 0;
		strcpy( Channel->FreqTable.sourceType, "DVB-C" );
		size = sizeof(DVB_C_FREQ) * Channel->FreqTable.totalNum;
		Channel->FreqTable.tbl.dvbc = (DVB_C_FREQ*)malloc( size+16 );
		memset( Channel->FreqTable.tbl.dvbc, 0, size );
	} else
	if ( !strcmp( Channel->sourceType, "DVB-S" ) )
	{
		int size;
		SageLog(( _LOG_TRACE, 3, "DVB-S Frequency initialize :%0x\r\n", Channel->FreqTable.tbl.dvbs ));
		if ( Channel->FreqTable.tbl.dvbs != NULL )
		{
			free( Channel->FreqTable.tbl.dvbs );
			Channel->FreqTable.tbl.dvbs = NULL;
		}
		Channel->FreqTable.totalNum = MAX_DVBS_FREQ;
		Channel->FreqTable.entryNum = 0;
		strcpy( Channel->FreqTable.sourceType, "DVB-S" );
		size = sizeof(DVB_S_FREQ) * Channel->FreqTable.totalNum;
		Channel->FreqTable.tbl.dvbs = (DVB_S_FREQ*)malloc( size+16 );
		memset( Channel->FreqTable.tbl.dvbs, 0, size );
	} else
	{
		SageLog(( _LOG_TRACE, 3, "Unknow source type for initialize frq table %s\r\n", Channel->sourceType ));
		return 0;
	}

	return 1;
}

int GrowFreqTable( CHANNEL_DATA *Channel )
{
	if ( !strcmp( Channel->sourceType, "ATSC" ) )
	{
		int size;
		ATSC_FREQ* newTbl;
		Channel->FreqTable.totalNum += MAX_ATSC_FREQ/2;
		size = sizeof(ATSC_FREQ) * Channel->FreqTable.totalNum;
		newTbl = (ATSC_FREQ*)malloc( size );
		memset( newTbl, 0, size );
		
		size = sizeof(ATSC_FREQ) * Channel->FreqTable.entryNum;
		memcpy( newTbl, Channel->FreqTable.tbl.atsc, size );
		
		SageLog(( _LOG_TRACE, 3, "ATSC Frequency initilized 0x%0x\r\n", Channel->FreqTable.tbl.atsc ));
		if ( Channel->FreqTable.tbl.atsc != NULL )
			free( Channel->FreqTable.tbl.atsc );
		Channel->FreqTable.tbl.atsc = newTbl;
		
		SageLog(( _LOG_TRACE, 3, "ATSC Frequency table is expended 0x%0x (size:%d)\r\n", Channel->FreqTable.tbl.atsc, size ));
	} else
	if ( !strcmp( Channel->sourceType, "QAM" ) )
	{
		int size;
		QAM_FREQ* newTbl;
		Channel->FreqTable.totalNum += MAX_QAM_FREQ/2;
		size = sizeof(QAM_FREQ) * Channel->FreqTable.totalNum;
		newTbl = (QAM_FREQ*)malloc( size );
		memset( newTbl, 0, size );
		
		size = sizeof(QAM_FREQ) * Channel->FreqTable.entryNum;
		memcpy( newTbl, Channel->FreqTable.tbl.qam, size );
		
		if ( Channel->FreqTable.tbl.qam != NULL )
			free( Channel->FreqTable.tbl.qam );
			
		Channel->FreqTable.tbl.qam = newTbl;
		SageLog(( _LOG_TRACE, 3, "QAM Frequency table is expended 0x%0x (size:%d)\r\n", Channel->FreqTable.tbl.qam, size ));
		
	} else
	if ( !strcmp( Channel->sourceType, "DVB-T" ) )
	{
		int size;
		DVB_T_FREQ* newTbl;
		Channel->FreqTable.totalNum += MAX_QAM_FREQ/2;
		size = sizeof(DVB_T_FREQ) * Channel->FreqTable.totalNum;
		newTbl = (DVB_T_FREQ*)malloc( size );
		memset( newTbl, 0, size );
		
		size = sizeof(DVB_T_FREQ) * Channel->FreqTable.entryNum;
		memcpy( newTbl, Channel->FreqTable.tbl.dvbt, size );
		
		if ( Channel->FreqTable.tbl.dvbt != NULL )
			free( Channel->FreqTable.tbl.dvbt );
			
		Channel->FreqTable.tbl.dvbt = newTbl;
		SageLog(( _LOG_TRACE, 3, "DVB-T Frequency table is expended 0x%0x (size:%d)\r\n", Channel->FreqTable.tbl.dvbt, size ));
	} else
	if ( !strcmp( Channel->sourceType, "DVB-C" ) )
	{
		int size;
		DVB_C_FREQ* newTbl;
		Channel->FreqTable.totalNum += MAX_QAM_FREQ/2;
		size = sizeof(DVB_C_FREQ) * Channel->FreqTable.totalNum;
		newTbl = (DVB_C_FREQ*)malloc( size );
		memset( newTbl, 0, size );
		
		size = sizeof(DVB_C_FREQ) * Channel->FreqTable.entryNum;
		memcpy( newTbl, Channel->FreqTable.tbl.dvbc, size );
		
		if ( Channel->FreqTable.tbl.dvbc != NULL )
			free( Channel->FreqTable.tbl.dvbc );
			
		Channel->FreqTable.tbl.dvbc = newTbl;
		SageLog(( _LOG_TRACE, 3, "DVB-C Frequency table is expended 0x%0x (size:%d)\r\n", Channel->FreqTable.tbl.dvbc, size ));
	} else
	if ( !strcmp( Channel->sourceType, "DVB-S" ) )
	{
		int size;
		DVB_S_FREQ* newTbl;
		Channel->FreqTable.totalNum += MAX_DVBS_FREQ/2;
		size = sizeof(DVB_S_FREQ) * Channel->FreqTable.totalNum;
		newTbl = (DVB_S_FREQ*)malloc( size );
		memset( newTbl, 0, size );
		
		size = sizeof(DVB_S_FREQ) * Channel->FreqTable.entryNum;
		memcpy( newTbl, Channel->FreqTable.tbl.dvbs, size );
		
		if ( Channel->FreqTable.tbl.dvbs != NULL )
			free( Channel->FreqTable.tbl.dvbs );
			
		Channel->FreqTable.tbl.dvbs = newTbl;
		SageLog(( _LOG_TRACE, 3, "DVB-S Frequency table is expended 0x%0x (size:%d)\r\n", Channel->FreqTable.tbl.dvbs, size ));
	} else
	{
		SageLog(( _LOG_TRACE, 3, "unimplemented expending tuning table '%s'\r\n", Channel->sourceType ));
	}

	return 1;

}

int GrowPredefineFreqTable( CHANNEL_DATA *Channel )
{
	if ( !strcmp( Channel->sourceType, "ATSC" ) )
	{
		int size;
		ATSC_FREQ* newTbl;
		Channel->PreDefinedFreqTable->totalNum += MAX_ATSC_FREQ/2;
		size = sizeof(ATSC_FREQ) * Channel->PreDefinedFreqTable->totalNum;
		newTbl = (ATSC_FREQ*)malloc( size );
		memset( newTbl, 0, size );
		
		size = sizeof(ATSC_FREQ) * Channel->PreDefinedFreqTable->entryNum;
		memcpy( newTbl, Channel->PreDefinedFreqTable->tbl.atsc, size );
		
		SageLog(( _LOG_TRACE, 3, "ATSC Frequency initilized 0x%0x\r\n", Channel->PreDefinedFreqTable->tbl.atsc ));
		if ( Channel->PreDefinedFreqTable->tbl.atsc != NULL )
			free( Channel->PreDefinedFreqTable->tbl.atsc );
		Channel->PreDefinedFreqTable->tbl.atsc = newTbl;
		
		SageLog(( _LOG_TRACE, 3, "ATSC Frequency table is expended 0x%0x (size:%d)\r\n", Channel->PreDefinedFreqTable->tbl.atsc, size ));
	} else
	if ( !strcmp( Channel->sourceType, "QAM" ) )
	{
		int size;
		QAM_FREQ* newTbl;
		Channel->PreDefinedFreqTable->totalNum += MAX_QAM_FREQ/2;
		size = sizeof(QAM_FREQ) * Channel->PreDefinedFreqTable->totalNum;
		newTbl = (QAM_FREQ*)malloc( size );
		memset( newTbl, 0, size );
		
		size = sizeof(QAM_FREQ) * Channel->PreDefinedFreqTable->entryNum;
		memcpy( newTbl, Channel->PreDefinedFreqTable->tbl.qam, size );
		
		if ( Channel->PreDefinedFreqTable->tbl.qam != NULL )
			free( Channel->PreDefinedFreqTable->tbl.qam );
			
		Channel->PreDefinedFreqTable->tbl.qam = newTbl;
		SageLog(( _LOG_TRACE, 3, "QAM Frequency table is expended 0x%0x (size:%d)\r\n", Channel->PreDefinedFreqTable->tbl.qam, size ));
		
	} else
	if ( !strcmp( Channel->sourceType, "DVB-T" ) )
	{
		int size;
		DVB_T_FREQ* newTbl;
		Channel->PreDefinedFreqTable->totalNum += MAX_QAM_FREQ/2;
		size = sizeof(DVB_T_FREQ) * Channel->PreDefinedFreqTable->totalNum;
		newTbl = (DVB_T_FREQ*)malloc( size );
		memset( newTbl, 0, size );
		
		size = sizeof(DVB_T_FREQ) * Channel->PreDefinedFreqTable->entryNum;
		memcpy( newTbl, Channel->PreDefinedFreqTable->tbl.dvbt, size );
		
		if ( Channel->PreDefinedFreqTable->tbl.dvbt != NULL )
			free( Channel->PreDefinedFreqTable->tbl.dvbt );
			
		Channel->PreDefinedFreqTable->tbl.dvbt = newTbl;
		SageLog(( _LOG_TRACE, 3, "DVB-T Frequency table is expended 0x%0x (size:%d)\r\n", Channel->PreDefinedFreqTable->tbl.dvbt, size ));
	} else
	if ( !strcmp( Channel->sourceType, "DVB-C" ) )
	{
		int size;
		DVB_C_FREQ* newTbl;
		Channel->PreDefinedFreqTable->totalNum += MAX_QAM_FREQ/2;
		size = sizeof(DVB_C_FREQ) * Channel->PreDefinedFreqTable->totalNum;
		newTbl = (DVB_C_FREQ*)malloc( size );
		memset( newTbl, 0, size );
		
		size = sizeof(DVB_C_FREQ) * Channel->PreDefinedFreqTable->entryNum;
		memcpy( newTbl, Channel->PreDefinedFreqTable->tbl.dvbc, size );
		
		if ( Channel->PreDefinedFreqTable->tbl.dvbc != NULL )
			free( Channel->PreDefinedFreqTable->tbl.dvbc );
			
		Channel->PreDefinedFreqTable->tbl.dvbc = newTbl;
		SageLog(( _LOG_TRACE, 3, "DVB-C Frequency table is expended 0x%0x (size:%d)\r\n", Channel->PreDefinedFreqTable->tbl.dvbc, size ));
	} else
	if ( !strcmp( Channel->sourceType, "DVB-S" ) )
	{
		int size;
		DVB_S_FREQ* newTbl;
		Channel->PreDefinedFreqTable->totalNum += MAX_DVBS_FREQ/2;
		size = sizeof(DVB_S_FREQ) * Channel->PreDefinedFreqTable->totalNum;
		newTbl = (DVB_S_FREQ*)malloc( size );
		memset( newTbl, 0, size );
		
		size = sizeof(DVB_S_FREQ) * Channel->PreDefinedFreqTable->entryNum;
		memcpy( newTbl, Channel->PreDefinedFreqTable->tbl.dvbs, size );
		
		if ( Channel->PreDefinedFreqTable->tbl.dvbs != NULL )
			free( Channel->PreDefinedFreqTable->tbl.dvbs );
			
		Channel->PreDefinedFreqTable->tbl.dvbs = newTbl;
		SageLog(( _LOG_TRACE, 3, "DVB-S Frequency table is expended 0x%0x (size:%d)\r\n", Channel->PreDefinedFreqTable->tbl.dvbs, size ));
	} else
	{
		SageLog(( _LOG_TRACE, 3, "unimplemented expending tuning table '%s'\r\n", Channel->sourceType ));
	}

	return 1;

}

int saveScanTuningTable( CHANNEL_DATA *Channel, char* file_ext )
{
	int i=0;
	FILE* fp;
	char FileName[_MAX_PATH];
	char DeviceName[_MAX_PATH];
	
	if ( Channel == NULL )
		return 0;

	strncpy( DeviceName, SageGetTunerDeviceName(Channel->Dev), sizeof(DeviceName) );
	ConvertIllegalChar( DeviceName );
	SPRINTF( FileName, sizeof(FileName), "%s%s-%s.%s", Channel->frqTableFilePath, DeviceName, getSourceType(Channel), file_ext );
	
	fp = fopen( FileName, "w" );
	if ( fp == NULL )
	{
		SageLog(( _LOG_TRACE, 3, "Can't open '%s' to save scan data\r\n", FileName ));
		return -1;
	}
	
	fprintf( fp, version_string, Channel->countryRegion );
	
	if ( !strcmp( Channel->sourceType, "ATSC" ) )
	{
		ATSC_FREQ* ATSCFreq = Channel->FreqTable.tbl.atsc;
		fprintf( fp, type_string, "ATSC" );
		fprintf( fp, "%s", desc_string_ATSC );
		fprintf( fp, "%s", atsc_tuning_format );
		
		for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
		{
			if ( ATSCFreq->index == 0xffff ) break;
			//if ( !strcmp( file_ext, "scn" ) || !strcmp( file_ext, "sav" ) )
			{
				char buf[512];
				fprintf( fp, "%s\r\n", makeATSCFreqString( buf, sizeof(buf),  (ATSC_FREQ *)&ATSCFreq[i]  ) );
			} 
			/*else
				fprintf( fp, "ch:%d %d %d %d %d %d %d #%s\r\n",
			          ATSCFreq[i].index,   ATSCFreq[i].major,       ATSCFreq[i].minor,
				      ATSCFreq[i].program, ATSCFreq[i].physical_ch, ATSCFreq[i].frequency, 
				      ATSCFreq[i].ctrl,    ATSCFreq[i].name );	
		    */
		}		
	} else
	if ( !strcmp( Channel->sourceType, "QAM" ) )
	{
		int freqTableType = 0;
		QAM_FREQ* QAMFreq = Channel->FreqTable.tbl.qam;
		fprintf( fp, type_string, "QAM" );
		fprintf( fp, "%s", desc_string_ATSC );
		fprintf( fp, "%s", qam_tuning_format );
		if ( Channel->freqTableType == 0 )
			freqTableType = Channel->guessFreqTableType;
		switch ( freqTableType )
		{
			case 1: fprintf( fp, "%s", "SCHEME:ATSC Frequency\r\n" );	break;
			case 2: fprintf( fp, "%s", "SCHEME:NTSC Frequency\r\n" );	break;
			case 3: fprintf( fp, "%s", "SCHEME:HRC  Frequency\r\n" );  break;
			case 4: return 0; break;
			default: fprintf( fp, "#SCHEME:UNKNOW (%d)\r\n",  freqTableType  );  break;
		}
		for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
		{
			if ( QAMFreq->index == 0xffff ) break;
			//if ( !strcmp( file_ext, "scn" ) || !strcmp( file_ext, "sav" ) )
			{
				char buf[512];
				fprintf( fp, "%s\r\n", makeQAMFreqString( buf, sizeof(buf),  (QAM_FREQ *)&QAMFreq[i]  ) );
			}
			/*
			else
				fprintf( fp, "ch:%d %d %d %d %d %d %d %d %d #%s\r\n",
			          QAMFreq[i].index,   QAMFreq[i].major,       QAMFreq[i].minor,
				      QAMFreq[i].program, QAMFreq[i].physical_ch, QAMFreq[i].frequency,
				      QAMFreq[i].modulation, QAMFreq[i].inversal, 
				      QAMFreq[i].ctrl,    QAMFreq[i].name );	
		     */
		}
	} else
	if ( !strcmp( Channel->sourceType, "DVB-T" ) )
	{
		DVB_T_FREQ* DVBTFreq = Channel->FreqTable.tbl.dvbt;
		fprintf( fp, type_string, "DVB-T" );
		fprintf( fp, "%s", desc_string_DVB );
		fprintf( fp, "%s", dvbt_tuning_format );
		for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
		{
			char buf[512];
			if ( DVBTFreq->index == 0xffff ) break;
			fprintf( fp, "%s\r\n", makeDVBTFreqString( buf, sizeof(buf),  (DVB_T_FREQ *)&DVBTFreq[i]  ) );
		}
	} else
	if ( !strcmp( Channel->sourceType, "DVB-C" ) )
	{
		DVB_C_FREQ* DVBCFreq = Channel->FreqTable.tbl.dvbc;
		fprintf( fp, type_string, "DVB-C" );
		fprintf( fp, "%s", desc_string_DVB );
		fprintf( fp, "%s", dvbc_tuning_format );
		for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
		{
			char buf[512];
			if ( DVBCFreq->index == 0xffff ) break;
			fprintf( fp, "%s\r\n", makeDVBCFreqString( buf, sizeof(buf),  (DVB_C_FREQ *)&DVBCFreq[i]  ) );
		}
	} else
	if ( !strcmp( Channel->sourceType, "DVB-S" ) )
	{
		DVB_S_FREQ* DVBSFreq = Channel->FreqTable.tbl.dvbs;
		fprintf( fp, type_string, "DVB-S" );
		fprintf( fp, "%s", desc_string_DVB );
		fprintf( fp, "%s", dvbs_tuning_format );
		for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
		{
			char buf[512];
			if ( DVBSFreq->index == 0xffff ) break;
			fprintf( fp, "%s\r\n", makeDVBSFreqString( buf, sizeof(buf),  (DVB_S_FREQ *)&DVBSFreq[i]  ) );
		}
	}else
	{
		SageLog(( _LOG_TRACE, 3, "Unknown sounceType\r\n", Channel->sourceType ));
	}


	fflush( fp );
	fclose( fp );
	SageLog(( _LOG_TRACE, 3, "Save '%d' tuning data into '%s'\r\n", i, FileName ));
	return 1;	
}

int loadScanTuningTable( CHANNEL_DATA *Channel )
{
	int i, line;
	FILE* fp;
	char FileName[_MAX_PATH];
	char DeviceName[_MAX_PATH];
	char buf[1024], text[32];
	char *p;
	int  make_copy = 0;
	
	if ( Channel == NULL )
		return -1;

	strncpy( DeviceName, SageGetTunerDeviceName(Channel->Dev), sizeof(DeviceName) );
	ConvertIllegalChar( DeviceName );
	SPRINTF( FileName, sizeof(FileName), "%s%s-%s.frq", Channel->frqTableFilePath, DeviceName,getSourceType(Channel) );
	fp = fopen( FileName, "r" );
	if ( fp == NULL )
	{
		SageLog(( _LOG_TRACE, 3, "Can't open '%s' to load scan data for tuner.\r\n", FileName ));
		initialTuningTable( Channel );
		return -1;
	}

	if ( Channel->FreqTable.totalNum == 0 || strcmp( getSourceType(Channel), Channel->FreqTable.sourceType ) )
	{
		initialTuningTable( Channel );			
	}
	
	
	line = 0;
	Channel->FreqTable.entryNum = 0;
	while( !feof( fp ) )
	{
		memset( buf, 0, sizeof(buf) );
		fgets( buf, sizeof(buf), fp );
		line++;
		
		//rip off '\n'
		for ( i = strlen(buf); i>=0 ; i-- )
			if ( buf[i] == '\n' || buf[i] == '\r' ) buf[i] = ' ';
			
		//skip white space
		i = 0;
		while ( (buf[i] == ' ' || buf[i] == '\t' ) && i<sizeof(buf) ) i++;
		if ( !STRNICMP( buf+i, "VERSION+7", 7 ) )
		{
			
		} else
		if ( !STRNICMP( buf+i, "TYPE", 4 ) )
		{
			if ( getNextTextField( buf+i+4, &p, text, sizeof(text) ) )
			{
				if ( !STRICMP( text, "QAM-NAKED" ) && !STRICMP( "QAM", Channel->FreqTable.sourceType ) )
				{
					Channel->tuneCtrl = 1;
					SageLog(( _LOG_TRACE, 3, "Skip QAM PSI, lock a channel by program. (%s,%s)\r\n",
					       text, Channel->FreqTable.sourceType ));					
				} else
				if ( STRICMP( text, Channel->FreqTable.sourceType ) )
					SageLog(( _LOG_TRACE, 3, "Error: tune type '%s' is not expected. (expect:%s) in '%s'  ('%s').\r\n",
					       text, Channel->FreqTable.sourceType, FileName, buf+i ));
					
			}
		} else
		if ( !STRNICMP( buf+i, "MAKE_SCN", 8 ) )
		{
			make_copy |= 2;
		} else
		if ( !STRNICMP( buf+i, "MAKE_COPY", 8 ) )
		{
			make_copy |= 1;
		} else
		if ( ( buf[i] == '!' ) || ( buf[i] == '#' ) )
		{	//skip comment line
			continue;
		} else
		if ( !STRNICMP( buf+i, "CH:", 3 ) )
		{
			char *p;

			if ( Channel->FreqTable.entryNum >= Channel->FreqTable.totalNum)  //ZQ
				GrowFreqTable( Channel );

			if ( !strcmp( Channel->sourceType, "ATSC" ) )
			{
				ATSC_FREQ* ATSCFreq = Channel->FreqTable.tbl.atsc;
				char* error="";
				p = buf+i;

				//new format
				if ( parseScanATSCFreqData( p, &ATSCFreq[  Channel->FreqTable.entryNum ], &error ) )
				{
					Channel->FreqTable.entryNum++;
				} else //old format
				if ( parseATSCFreqEntry( p+3, &ATSCFreq[  Channel->FreqTable.entryNum ] ) )
				{
					Channel->FreqTable.entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d file:%s\r\n", line, FileName ));
			} else
			if ( !strcmp( Channel->sourceType, "QAM" ) )
			{
				QAM_FREQ* QAMFreq = Channel->FreqTable.tbl.qam;
				char* error;
				p = buf+i;
				if ( parseScanQAMFreqData( p, &QAMFreq[  Channel->FreqTable.entryNum ], &error ) )
				{
					Channel->FreqTable.entryNum++;
				} else
				if ( parseQAMFreqEntry( p+3, &QAMFreq[  Channel->FreqTable.entryNum ] ) )
				{
					Channel->FreqTable.entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d file:%s\r\n", line, FileName ));
			} else
			if ( !strcmp( Channel->sourceType, "DVB-T" ) )
			{
				DVB_T_FREQ* DVBTFreq = Channel->FreqTable.tbl.dvbt;
				char* error;
				p = buf+i;

				if ( parseDVBTFreqEntry( p, &DVBTFreq[  Channel->FreqTable.entryNum ], &error ) )
				{
					Channel->FreqTable.entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d file %s (err:%s)\r\n",
					        line, FileName, error ));
			} else
			if ( !strcmp( Channel->sourceType, "DVB-C" ) )
			{
				DVB_C_FREQ* DVBCFreq = Channel->FreqTable.tbl.dvbc;
				char* error;
				p = buf+i;

				if ( parseDVBCFreqEntry( p, &DVBCFreq[  Channel->FreqTable.entryNum ], &error ) )
				{
					Channel->FreqTable.entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d file %s (err:%s)\r\n",
					        line, FileName, error ));

			} else
			if ( !strcmp( Channel->sourceType, "DVB-S" ) )
			{
				DVB_S_FREQ* DVBSFreq = Channel->FreqTable.tbl.dvbs;
				char* error;
				p = buf+i;

				if ( parseDVBSFreqEntry( p, &DVBSFreq[  Channel->FreqTable.entryNum ], &error ) )
				{
					Channel->FreqTable.entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d file %s (err:%s)\r\n",
					        line, FileName, error ));

			} else
			{
				SageLog(( _LOG_TRACE, 3, "Unknown sourceType '%s'", Channel->sourceType ));
				break;
			}
		} 
		
		
	}

	fclose( fp );
	SageLog(( _LOG_TRACE, 3, "Loaded '%d' tuning data from '%s'\r\n", Channel->FreqTable.entryNum, FileName ));

	if ( Channel->FreqTable.entryNum > 0 )
	{
		if ( make_copy & 0x01 )
			saveScanTuningTable( Channel, "sav" );
		else
		if ( make_copy & 0x02 )
			saveScanTuningTable( Channel, "scn" );
	}

	if ( Channel->frqTableUpdateCheck )
	{
		Channel->frqTableTimeStamp = fileTimeStamp( FileName );
		strncpy( Channel->frqTableFileName, FileName, sizeof(Channel->frqTableFileName) );
	}

	return Channel->FreqTable.entryNum;
}

int loadScanDataFile( CHANNEL_DATA *Channel )
{
	int i, line;
	FILE* fp;
	char FileName[_MAX_PATH];
	char DeviceName[_MAX_PATH];
	char buf[1024], text[32];
	char *p;
	
	
	if ( Channel == NULL )
		return -1;

	strncpy( DeviceName, SageGetTunerDeviceName(Channel->Dev), sizeof(DeviceName) );
	ConvertIllegalChar( DeviceName );
	SPRINTF( FileName, sizeof(FileName), "%s%s-%s.scn", Channel->frqTableFilePath, DeviceName,getSourceType(Channel) );
	fp = fopen( FileName, "r" );

	if ( fp == NULL ) 
	{
		SageLog(( _LOG_TRACE, 3, "Can't open '%s' to load scan data for tuner.\r\n", FileName ));
		return -1;
	}

	//initilize table to load data
	if ( Channel->PreDefinedFreqTable == NULL || Channel->PreDefinedFreqTable->totalNum == 0  )
	{
		if ( !strncmp( Channel->sourceType, "ATSC", 5 ) )
		{
			if ( Channel->PreDefinedFreqTable )
			{
				if ( Channel->PreDefinedFreqTable->tbl.dvbt ) free( Channel->PreDefinedFreqTable->tbl.dvbt );
				free( Channel->PreDefinedFreqTable );
			}
			Channel->PreDefinedFreqTable= (FREQTBL*)malloc( sizeof(FREQTBL) );
			memset( Channel->PreDefinedFreqTable, 0, sizeof(FREQTBL) );
			Channel->PreDefinedFreqTable->tbl.atsc = (ATSC_FREQ*)malloc( sizeof(ATSC_FREQ)*MAX_ATSC_FREQ );
			memset( Channel->PreDefinedFreqTable->tbl.atsc, 0, (sizeof(ATSC_FREQ)*MAX_ATSC_FREQ) );
			Channel->PreDefinedFreqTable->totalNum = MAX_ATSC_FREQ ;

		} else		
		if ( !strncmp( Channel->sourceType, "QAM", 5 ) )
		{
			if ( Channel->PreDefinedFreqTable )
			{
				if ( Channel->PreDefinedFreqTable->tbl.dvbt ) free( Channel->PreDefinedFreqTable->tbl.dvbt );
				free( Channel->PreDefinedFreqTable );
			}
			Channel->PreDefinedFreqTable= (FREQTBL*)malloc( sizeof(FREQTBL) );
			memset( Channel->PreDefinedFreqTable, 0, sizeof(FREQTBL) );
			Channel->PreDefinedFreqTable->tbl.qam = (QAM_FREQ*)malloc( sizeof(QAM_FREQ)*MAX_QAM_FREQ );
			memset( Channel->PreDefinedFreqTable->tbl.qam, 0, (sizeof(QAM_FREQ)*MAX_QAM_FREQ) );
			Channel->PreDefinedFreqTable->totalNum = MAX_QAM_FREQ;

		} else
		if ( !strncmp( Channel->sourceType, "DVB-T", 5 ) )
		{
			if ( Channel->PreDefinedFreqTable )
			{
				if ( Channel->PreDefinedFreqTable->tbl.dvbt ) free( Channel->PreDefinedFreqTable->tbl.dvbt );
				free( Channel->PreDefinedFreqTable );
			}
			Channel->PreDefinedFreqTable= (FREQTBL*)malloc( sizeof(FREQTBL) );
			memset( Channel->PreDefinedFreqTable, 0, sizeof(FREQTBL) );
			Channel->PreDefinedFreqTable->tbl.dvbt = (DVB_T_FREQ*)malloc( sizeof(DVB_T_FREQ)*MAX_DVB_SCAN_ENTRY );
			memset( Channel->PreDefinedFreqTable->tbl.dvbt, 0, (sizeof(DVB_T_FREQ)*MAX_DVB_SCAN_ENTRY) );
			Channel->PreDefinedFreqTable->totalNum = MAX_DVB_SCAN_ENTRY;

		} else
		if ( !strncmp( Channel->sourceType, "DVB-C", 5 ) )
		{
			if ( Channel->PreDefinedFreqTable )
			{
				if ( Channel->PreDefinedFreqTable->tbl.dvbc ) free( Channel->PreDefinedFreqTable->tbl.dvbc );
				free( Channel->PreDefinedFreqTable );
			}
			Channel->PreDefinedFreqTable= (FREQTBL*)malloc( sizeof(FREQTBL) );
			memset( Channel->PreDefinedFreqTable, 0, sizeof(FREQTBL) );
			Channel->PreDefinedFreqTable->tbl.dvbc = (DVB_C_FREQ*)malloc( sizeof(DVB_C_FREQ)*MAX_DVB_SCAN_ENTRY );
			memset( Channel->PreDefinedFreqTable->tbl.dvbc, 0, (sizeof(DVB_C_FREQ)*MAX_DVB_SCAN_ENTRY) );
			Channel->PreDefinedFreqTable->totalNum = MAX_DVB_SCAN_ENTRY;

		}else

		if ( !strncmp( Channel->sourceType, "DVB-S", 5 ) )
		{
			if ( Channel->PreDefinedFreqTable )
			{
				if ( Channel->PreDefinedFreqTable->tbl.dvbs ) free( Channel->PreDefinedFreqTable->tbl.dvbs );
				free( Channel->PreDefinedFreqTable );
			}
			Channel->PreDefinedFreqTable= (FREQTBL*)malloc( sizeof(FREQTBL) );
			memset( Channel->PreDefinedFreqTable, 0, sizeof(FREQTBL) );
			Channel->PreDefinedFreqTable->tbl.dvbs = (DVB_S_FREQ*)malloc( sizeof(DVB_S_FREQ)*MAX_DVBS_SCAN_ENTRY );
			memset( Channel->PreDefinedFreqTable->tbl.dvbs, 0, (sizeof(DVB_S_FREQ)*MAX_DVBS_SCAN_ENTRY) );
			Channel->PreDefinedFreqTable->totalNum = MAX_DVBS_SCAN_ENTRY;

		}  else
		{
			SageLog(( _LOG_TRACE, 3, "Unknown sourceType '%s'", Channel->sourceType ));
			return -1;
		}
	}
	
	
	line = 0;
	Channel->PreDefinedFreqTable->entryNum = 0;
	while( !feof( fp ) )
	{
		memset( buf, 0, sizeof(buf) );
		fgets( buf, sizeof(buf), fp );
		line++;

		if (  Channel->PreDefinedFreqTable->totalNum <= Channel->PreDefinedFreqTable->entryNum )
			GrowPredefineFreqTable( Channel );
		
		//rip off '\n'
		for ( i = strlen(buf); i>=0 ; i-- )
			if ( buf[i] == '\n' || buf[i] == '\r' ) buf[i] = ' ';
			
		//skip white space
		i = 0;
		while ( (buf[i] == ' ' || buf[i] == '\t' ) && i<sizeof(buf) ) i++;
		if ( !STRNICMP( buf+i, "VERSION+7", 7 ) )
		{
			
		} else
		if ( !STRNICMP( buf+i, "TYPE", 4 ) )
		{
			if ( getNextTextField( buf+i+4, &p, text, sizeof(text) ) )
			{
				if ( STRICMP( text, Channel->sourceType ) )
					SageLog(( _LOG_TRACE, 3, "Error: tune type '%s' is not expected. (expect:%s) in '%s'  ('%s')\r\n",
					       text, Channel->sourceType, FileName, buf+i ));
					
			}
		} else
		if ( ( buf[i] == '!' ) || ( buf[i] == '#' ) )
		{	//skip comment line
			continue;
		} else
		if ( !STRNICMP( buf+i, "CH:", 3 ) )
		{
			char *p;

			if ( Channel->PreDefinedFreqTable->entryNum >= Channel->PreDefinedFreqTable->totalNum)  //ZQ
				GrowFreqTable( Channel );

			if ( !strcmp( Channel->sourceType, "ATSC" ) )
			{
				ATSC_FREQ* ATSCFreq = &Channel->PreDefinedFreqTable->tbl.atsc[Channel->PreDefinedFreqTable->entryNum];
				char* error;
				p = buf+i;
				memset( ATSCFreq, 0, sizeof(ATSC_FREQ) );
				parseScanATSCFreqData( p, ATSCFreq, &error );
				if ( ATSCFreq->major || ATSCFreq->minor || ATSCFreq->physical_ch )
				{
					Channel->PreDefinedFreqTable->entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d file:%s (err:%s)\r\n",
					line, FileName, error ));
			} else
			if ( !strcmp( Channel->sourceType, "QAM" ) )
			{
				QAM_FREQ* QAMFreq = &Channel->PreDefinedFreqTable->tbl.qam[  Channel->PreDefinedFreqTable->entryNum ];
				char* error;
				p = buf+i;
				parseScanQAMFreqData( p, QAMFreq, &error );
				if ( QAMFreq->major || QAMFreq->major || QAMFreq->physical_ch )
				{
					Channel->PreDefinedFreqTable->entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d file:%s (err:%s) '%s'\r\n",
						line, FileName, error, p ));
			} else
			if ( !strcmp( Channel->sourceType, "DVB-T" ) )
			{
				DVB_T_FREQ* DVBTFreq = Channel->PreDefinedFreqTable->tbl.dvbt;
				char* error;
				p = buf+i;

				if ( parseDVBTFreqEntry( p, &DVBTFreq[  Channel->PreDefinedFreqTable->entryNum ], &error ) )
				{
					Channel->PreDefinedFreqTable->entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d file %s (err:%s)\r\n",
					        line, FileName, error ));
			} else
			if ( !strcmp( Channel->sourceType, "DVB-C" ) )
			{
				DVB_C_FREQ* DVBCFreq = Channel->PreDefinedFreqTable->tbl.dvbc;
				char* error;
				p = buf+i;

				if ( parseDVBCFreqEntry( p, &DVBCFreq[  Channel->PreDefinedFreqTable->entryNum ], &error ) )
				{
					Channel->PreDefinedFreqTable->entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d file %s (err:%s)\r\n",
					        line, FileName, error ));

			} else
			if ( !strcmp( Channel->sourceType, "DVB-S" ) )
			{
				DVB_S_FREQ* DVBSFreq = Channel->PreDefinedFreqTable->tbl.dvbs;
				char* error;
				p = buf+i;

				if ( parseDVBSFreqEntry( p, &DVBSFreq[  Channel->PreDefinedFreqTable->entryNum ], &error ) )
				{
					Channel->PreDefinedFreqTable->entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d file %s (err:%s)\r\n",
					        line, FileName, error ));

			} else
			{
				SageLog(( _LOG_TRACE, 3, "Unknown sourceType '%s'", Channel->sourceType ));
				break;
			}
		} 
	}

	for ( i = Channel->PreDefinedFreqTable->entryNum; i<Channel->PreDefinedFreqTable->totalNum; i++ )
	{
			if ( !strcmp( Channel->sourceType, "ATSC" ) )
			{
				Channel->PreDefinedFreqTable->tbl.atsc[i].index = 0xffff;
			} else
			if ( !strcmp( Channel->sourceType, "QAM" ) )
			{
				Channel->PreDefinedFreqTable->tbl.qam[i].index = 0xffff;
			} else
			if ( !strcmp( Channel->sourceType, "DVB-T" ) )
			{
				Channel->PreDefinedFreqTable->tbl.dvbt[i].index = 0xffff;
			} else
			if ( !strcmp( Channel->sourceType, "DVB-C" ) )
			{
				Channel->PreDefinedFreqTable->tbl.dvbc[i].index = 0xffff;
			} else
			if ( !strcmp( Channel->sourceType, "DVB-S" ) )
			{
				Channel->PreDefinedFreqTable->tbl.dvbs[i].index = 0xffff;
			} 
	}

	fclose( fp );
	SageLog(( _LOG_TRACE, 3, "Loaded '%d' scan data from '%s'.\r\n", Channel->PreDefinedFreqTable->entryNum, FileName ));

	
	return Channel->PreDefinedFreqTable->entryNum;

}


int saveCacheQAMFreq(  CHANNEL_DATA *Channel, int freqTableType, struct bcast_qam* freqTbl )
{
	int i;
	FILE* fp;
	char FileName[_MAX_PATH];
	char DeviceName[_MAX_PATH];
	
	if ( Channel == NULL )
		return -1;

	strncpy( DeviceName, SageGetTunerDeviceName(Channel->Dev), sizeof(DeviceName) );
	ConvertIllegalChar( DeviceName );
	SPRINTF( FileName, sizeof(FileName), "%sQAMScan-%s.frq", Channel->frqTableFilePath, DeviceName  );
	fp = fopen( FileName, "w" );
	if ( fp == NULL )
	{
		SageLog(( _LOG_TRACE, 3, "Can't open '%s' to write scan frequency.\r\n", FileName ));
		return -1;
	}

	fprintf( fp, "#This QAM frequency cache file will be used to speed QAM scanning channel, if an empty file of QAM_FRQ_CACHE.ENABLE exist in sagetv directory.\r\n\r\n" );
	switch ( freqTableType )
	{
		case 1: fprintf( fp, "#SCHEME:ATSC Frequency\r\n" );	break;
		case 2: fprintf( fp, "#SCHEME:NTSC Frequency\r\n" );	break;
		case 3: fprintf( fp, "#SCHEME:HRC Frequency\r\n" );  break;
		case 4: return 0; break;
		default: fprintf( fp, "#SCHEME:UNKNOW (%d)\r\n", freqTableType );  break;
	}

	fprintf( fp, "#TUNER:%s\r\n", SageGetTunerDeviceName(Channel->Dev) );
	fprintf( fp, "#Format: CH:nn frq:nnn [mod:nnn] [inv:nnn] \r\n\r\n" );

	i = 0;
	while ( freqTbl[i].fq != 0 && i<256 ) 
	{
		fprintf( fp, "CH:%d frq:%d \r\n", freqTbl[i].ch, freqTbl[i].fq );
		i++;
	}
	fprintf( fp, "CH:%d frq:0000 \r\n", 999 );
	fprintf( fp, "#END \r\n" );
	fclose( fp );
	SageLog(( _LOG_TRACE, 3, "Save QAM Frq data into '%s'\r\n", FileName ));
	return 1;
}


static int loadQAMFreqData(  FILE *fp, struct bcast_qam *freqTbl, int max, int *scheme );

int loadPredefinedQAMFreq( CHANNEL_DATA* Channel,  struct bcast_qam *freqTbl, int max, int* scheme )
{
	FILE* fp;
	char FileName[_MAX_PATH];
	int num;
	

	SPRINTF( FileName, sizeof(FileName), "%sPredefinedQAM.frq", Channel->frqTableFilePath );
	fp = fopen( FileName, "r" );
	if ( fp == NULL )
	{
		SageLog(( _LOG_TRACE, 3, "QAM FRQ file %s not found, user default frequency.\r\n", FileName ));
		return 0;
	}
	
	num = loadQAMFreqData(  fp, freqTbl,  max, scheme );

	SageLog(( _LOG_TRACE, 3, "Load %d predefined QAM frq data entries from '%s'\r\n", num, FileName ));
	fclose( fp );

	return num;
}

int loadCacheQAMFreq(  CHANNEL_DATA *Channel,  struct bcast_qam *freqTbl, int max )
{
	FILE* fp;
	char FileName[_MAX_PATH];
	char DeviceName[_MAX_PATH];
	int num;
	int scheme;

	if ( Channel == NULL )
		return 0;

	strncpy( DeviceName, SageGetTunerDeviceName(Channel->Dev), sizeof(DeviceName) );
	ConvertIllegalChar( DeviceName );
	SPRINTF( FileName, sizeof(FileName), "%sQAMScan-%s.frq", Channel->frqTableFilePath, DeviceName );
	fp = fopen( FileName, "r" );
	if ( fp == NULL )
	{
		SageLog(( _LOG_TRACE, 3, "QAM FRQ file %s not found, user default frequency.\r\n", FileName ));
		return 0;
	}
	Channel->freqScheme = scheme = 0;
	
	num = loadQAMFreqData(  fp, freqTbl,  max, &scheme );
	if ( scheme )
		Channel->freqScheme = scheme;


	SageLog(( _LOG_TRACE, 3, "Load %d cache QAM frq data entries from '%s'\r\n", num, FileName ));
	fclose( fp );
	return num;
}
static int loadQAMFreqData(  FILE *fp, struct bcast_qam *freqTbl, int max, int* scheme )
{
	int num, line;

	char buf[1024];
	num = 0;
	line = 0;
	while( !feof( fp ) && num < max )
	{
		int i;
		memset( buf, 0, sizeof(buf) );
		fgets( buf, sizeof(buf), fp );
		line++;
		
		//rip off '\n'
		for ( i = strlen(buf); i>=0 ; i-- )
			if ( buf[i] == '\n' || buf[i] == '\r' ) buf[i] = ' ';
			
		//skip white space
		i = 0;
		while ( (buf[i] == ' ' || buf[i] == '\t' ) && i<sizeof(buf) ) i++;
		if ( ( buf[i] == '!' ) || ( buf[i] == '#' ) )
		{	//skip comment line
			if ( !STRNICMP( buf+i, "SCHEME", 6 )  || !STRNICMP( buf+i, "scheme", 6 ))
			{
				char tmp[12];
				*scheme = 0;
				if ( GetString( buf+i+1, "SCHEME", tmp, sizeof(tmp) ) || GetString( buf+i+1, "scheme", tmp, sizeof(tmp) )  )
				{
					if ( !strncmp( tmp, "ATSC", 4 ) ) *scheme = 1; else
					if ( !strncmp( tmp, "NTSC", 4 ) ) *scheme = 2; else
					if ( !strncmp( tmp, "HRC",  3 ) ) *scheme = 3; 

				}
			}
			continue;
		} else
		if ( !STRNICMP( buf+i, "SCHEME", 6 )  || !STRNICMP( buf+i, "scheme", 6 ))
		{	
			char tmp[12];
			*scheme = 0;
			if ( GetString( buf+i, "SCHEME", tmp, sizeof(tmp) ) || GetString( buf+i+1, "scheme", tmp, sizeof(tmp) )  )
			{
				if ( !strncmp( tmp, "ATSC", 4 ) ) *scheme = 1; else
				if ( !strncmp( tmp, "NTSC", 4 ) ) *scheme = 2; else
				if ( !strncmp( tmp, "HRC",  3 ) ) *scheme = 3; 

			}
			continue;
		} else
		if ( !STRNICMP( buf+i, "CH:", 3 ) || !STRNICMP( buf+i, "ch:", 3 ) )
		{
			char *error="";
			if ( parseUserQAMFreqData( buf+i, &freqTbl[num], &error ) )
			{
				num++;
			} else
				if ( error[0] )
					SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d (err:%s)\r\n",
					    line, error ));

		} 
		else
		{

		}
	}

	return num;
}

static const char* delima = " \t,;\r\n";
//#define STRNICMP  strncasecmp
//#define min(x,y)   ( (x)>(y)?(y):(x))

static int getNextTextField( char* p_in, char **p_out, char* text, int max_len )
{
	char *token, *p;
	int bytes = 0;
	token = (char*)strtok_r( p_in, delima, &p );
	if ( token == NULL ) 
		return 0;
		
	if ( p != NULL )
	{
		bytes = min( p - token, max_len-1 );
		strncpy( text, token, bytes );
		text[bytes] = 0x0;
	}
	*p_out = p;
	return bytes;
}

static bool getNextIntField( char* p_in, char **p_out, int* int_val )
{
	char *token, *p;
	token = (char*)strtok_r( p_in, delima, &p );
	if ( token == NULL ) 
		return false;
	*int_val = atoi( token );
	*p_out = p;
	return true;
}

static bool getNextShortField( char* p_in, char **p_out, uint16_t* short_val )
{
	char *token, *p;
	token = (char*)strtok_r( p_in, delima, &p );
	if ( token == NULL ) 
		return false;
	*short_val = (uint16_t)atoi( token );
	*p_out = p;
	return true;
}

static bool parseATSCFreqEntry( char* p_in, ATSC_FREQ* atsc_freq ) 
{
	char *p;
	p = p_in;
    if ( getNextShortField( p, &p, &atsc_freq->index ) )
	if ( getNextShortField( p, &p, &atsc_freq->major ) )
	if ( getNextShortField( p, &p, &atsc_freq->minor ) )
	if ( getNextShortField( p, &p, &atsc_freq->program ) )
	if ( getNextShortField( p, &p, &atsc_freq->physical_ch ) )
	if ( getNextIntField( p, &p, (int*)&atsc_freq->frequency ) )
	if ( getNextIntField( p, &p, (int*)&atsc_freq->ctrl ) )
	{
		char text[32];
		int len = getNextTextField( p, &p, text, sizeof(text) );
		atsc_freq->name[0] =0;
		if ( len > 0 && text[0] == '#' )
			strncpy( atsc_freq->name, text+1, sizeof(atsc_freq->name) );
			
		return true;
	}
	
	return false;
}

static bool parseQAMFreqEntry( char* p_in, QAM_FREQ* qam_freq ) 
{
	char *p;
	p = p_in;
    if ( getNextShortField( p, &p, &qam_freq->index ) )
	if ( getNextShortField( p, &p, &qam_freq->major ) )
	if ( getNextShortField( p, &p, &qam_freq->minor ) )
	if ( getNextShortField( p, &p, &qam_freq->program ) )
	if ( getNextShortField( p, &p, &qam_freq->physical_ch ) )
	if ( getNextIntField( p, &p, (int*)&qam_freq->frequency ) )
	if ( getNextIntField( p, &p, (int*)&qam_freq->modulation ) )
	if ( getNextIntField( p, &p, (int*)&qam_freq->inversal ) )
	if ( getNextIntField( p, &p, (int*)&qam_freq->ctrl ) )
	{
		char text[32];
		int len = getNextTextField( p, &p, text, sizeof(text) );
		qam_freq->name[0] =0;
		if ( len > 0 && text[0] == '#' )
			strncpy( qam_freq->name, text+1, sizeof(qam_freq->name) );
		return true;
	}
		
	return false;
}

static int  ParsecountryRegionName( const char *p, const char **p_next, char* Country,  int CountryLen, 
								   char* RegionName, int RegionNameLen, char* ExtNo, int ExtNoLen  );
static int  GetProviderName( const char *p, char* Country,  int CountryLen );
static bool GetExtParameter( char* p, char* Parameter, int MaxLen  );
const char* DVBT_predined_file="PredefinedDVBT.frq";
const char* DVBC_predined_file="PredefinedDVBC.frq";
const char* DVBS_predined_file="PredefinedDVBS.frq";
#define _MAX_PROVIDER_LEN	64
#define _MAX_PROVIDER_NUM	6
#define _MAX_SAT_NO_LEN		8
int LoadPreDefinedScanFreq( FREQTBL *pScanFreqTbl, const char* pCountryRegionName, const char* sourceType )
{
	char buf[1024], Parameter[512];
	char provider[_MAX_PROVIDER_LEN]={0};
	char country[_MAX_PROVIDER_NUM][_MAX_PROVIDER_LEN]={0};
	char region[_MAX_PROVIDER_NUM][_MAX_PROVIDER_LEN]={0};
	char sat_no[_MAX_PROVIDER_NUM][_MAX_SAT_NO_LEN]={0};
	const char *ps, *pe;
	bool parse_data = false;
	int  index = 0, i, country_num, cur_provider = 0;
	FILE* fp;
	const char* FileName; 

	ps = pCountryRegionName; i = 0;

	while ( i < _MAX_PROVIDER_NUM &&
		    ParsecountryRegionName( ps, &pe, country[i], _MAX_PROVIDER_LEN, 
			                                 region[i], _MAX_PROVIDER_LEN, sat_no[i], _MAX_SAT_NO_LEN ) > 0  )
	{
		i++; ps = pe;
	} 

	if ( i == 0 )
	{
		SageLog(( _LOG_TRACE, 3, "Invalid Country-Region/provider name '%s' for loadind predefined frq table!\r\n",
			pCountryRegionName )); 
		return 0;
	}

	country_num = i;
	index = pScanFreqTbl->entryNum;

	if ( !strcmp( sourceType, "DVB-T" ) )
		FileName = DVBT_predined_file;
	else
	if ( !strcmp( sourceType, "DVB-C" ) )
		FileName = DVBC_predined_file;
	else
	if ( !strcmp( sourceType, "DVB-S" ) )
		FileName = DVBS_predined_file;
	else
		FileName = "Unknow";

	fp = fopen( FileName, "r" );
	if ( fp == NULL )
	{
		SageLog(( _LOG_TRACE, 3, "file not found '%s'\r\n", FileName ));
		return 0;
	}

	SageLog(( _LOG_TRACE, 3, "Loading predefined freq table %s for %s in '%s'.\r\n",
		      FileName, sourceType, pCountryRegionName )); 

	for ( i = 0; i<country_num; i++ )
	{
		SageLog(( _LOG_TRACE, 3, " \tcountry region/provider (sat_no):'%s' '%s' '%s'.\r\n", country[i], region[i], sat_no[i] ));
	}

	while( !feof( fp ) )
	{
		char *p;
		int k;
		memset( buf, 0, sizeof(buf) );
		fgets( buf, sizeof(buf)-1, fp );
		
		//skip white space
		for ( i = 0; i<sizeof(buf)-1; i++ )
			if ( buf[i] != ' ' || buf[i] !='\t' ) break;
		p = buf+i;
		
		//skip comment line
		if ( *p == '#' || *p == '!' ) 
			continue; 

		provider[0] = 0x0;
		if ( GetProviderName( p, provider, sizeof(provider) ) )
		{
			for ( k = 0; k<country_num; k++ )
			{
				if ( !strcmp( sourceType, "DVB-S" ) ) //Satellite doesn't have region, treat it as a provider
				{
					if ( !STRICMP( country[k], provider ) )
					{
						parse_data = true;
						strncpy( sat_no[k], region[k], sizeof(sat_no[k]) );  //for compatiable old format (provider#sat_no)
						strncpy( region[k], country[k], sizeof(region[k]) );
						cur_provider = k;
					} else
					if ( !STRICMP( region[k], provider ) )
					{
						parse_data = true;
						cur_provider = k;
					}

				} else
				{
					char country_region[_MAX_PROVIDER_LEN*2];
					strncpy( country_region, country[k], sizeof(country_region) );
					if ( region[k][0] )
					{
						strncat( country_region, "-", sizeof(country_region) );
						strncat( country_region, region[k], sizeof(country_region) );
					}
					parse_data = !STRICMP( country_region, provider );
				}
			
				if ( parse_data )
				{
					SageLog(( _LOG_TRACE, 3, "Found '%s' frequency section for '%s-%s' (total:%d)\r\n",
								           provider, country[k], region[k], pScanFreqTbl->totalNum ));
					break;
				}
			}
			
		}
		else
		if ( GetExtParameter( p, Parameter, sizeof(Parameter) ) )
		{
			printf( "Ext: %s\r\n", p );
		} else
		if ( parse_data && pScanFreqTbl->totalNum > index )
		{
			if ( !strcmp( sourceType, "DVB-T" ) )
			{
				char* error;
				 parseDVBTFreqEntry( p, &pScanFreqTbl->tbl.dvbt[index], &error );
				 if ( pScanFreqTbl->tbl.dvbt[index].frequency == 0 )
				 	parse_data = false;
			} else
			if ( !strcmp( sourceType, "DVB-C" ) )
			{
				char* error;
				 parseDVBCFreqEntry( p, &pScanFreqTbl->tbl.dvbc[index], &error );
				 if ( pScanFreqTbl->tbl.dvbc[index].frequency == 0 )
				 	parse_data = false;

			} else
			if ( !strcmp( sourceType, "DVB-S" ) )
			{
				char* error;
				 parseDVBSFreqEntry( p, &pScanFreqTbl->tbl.dvbs[index], &error );
				 if ( region[cur_provider][0] )
					pScanFreqTbl->tbl.dvbs[index].sat_no = atoi( sat_no[cur_provider] );
				 else
					pScanFreqTbl->tbl.dvbs[index].sat_no = 0xffff;
				 if ( pScanFreqTbl->tbl.dvbs[index].frequency == 0 )
				 	parse_data = false;
			}

			if ( parse_data ) index++;
		}
	}

	i = index - pScanFreqTbl->entryNum;
	pScanFreqTbl->entryNum = index;

	fclose( fp );

	return i;
}

static const char* delimiter = " \t,;\r\n:|'\"#=";
static bool IsDelimiter( char ch )
{
	int i;
	if ( ch == 0 )	return true;
	for ( i = 0; delimiter[i]; i++ )
		if ( ch == delimiter[i] ) return true;
	return false;
}

//parse name in format [country | country-region | country-provider | country-provider#sat_no ]; 
static int  ParsecountryRegionName( const char *p, const char **p_end, char* Country,  int CountryLen, 
								      char* RegionName, int RegionNameLen, char* ExtNo, int ExtNoLen  )
{
	int len;
	int i, j, ret;

	len=(int)strlen( p );
	if ( len == 0 )
	{
		*p_end = p;
		return 0;
	}

	if ( CountryLen > 0 )  Country[0] = 0;
	if ( RegionNameLen > 0 ) RegionName[0] = 0;

	i = 0;
	//skip space and delima
	while (  p[i] == ' ' &&  p[i] == ';' && p[i] == ',' && i < len ) i++;
	if ( p[i] == 0x0 || i >= len )
	{
		*p_end = p+i;
		return 0;
	}

	ret = 1;
    j = 0;
	while ( p[i] && p[i] != '-' && p[i] != '#' && p[i] != '\n' && p[i] != ';' && p[i] != ',' && i<len && j<CountryLen  )
		Country[j++] = p[i++];
	Country[j] = 0x0;

	if ( p[i] == '-' )
	{
		i++;
		j = 0;
		while ( p[i] && p[i] != '\n' && p[i] != '#' && p[i] != ';' && p[i] != ',' && i<len && j<RegionNameLen  )
			RegionName[j++] = p[i++];

		RegionName[j] = 0;
		ret = 2;
	}

	if ( p[i] == '#' )
	{
		i++;
		j = 0;
		while ( p[i] && p[i] != '\n' && p[i] != ';' && p[i] != ',' && i<len && j<ExtNoLen  )
			ExtNo[j++] = p[i++];

		ExtNo[j] = 0;
		ret = 3;
	}


	*p_end = p+i;
	return ret;

}

static int GetProviderName( const char *p, char* Country,  int CountryLen  )
{
	int len;
	int i, j, ret;
	
	len=(int)strlen( p );
	if ( len == 0 )
		return 0;

	if ( CountryLen > 0 )  Country[0] = 0;

	i = 0; ret = 0;
	//skip space
	while (  p[i] != '"' && i < len ) i++;
	if ( p[i] != '"' )
		return 0;

	i++; j = 0;
	while ( p[i] && p[i] != '"' && p[i] != '\n' && i<len && j<CountryLen  )
	{
		Country[j++] = p[i++];
		ret++;
	}


	Country[j] = 0x0;

	return ret;
}

static bool GetExtParameter( char* p, char* Parameter, int MaxLen  )
{
	int len;
	int i=0, j=0;
	
	len=(int)strlen( p );
	while (  p[i] != '{' && i < len ) i++;
	if ( p[i] != '{' )
		return false;

	i++;
	while ( p[i] && p[i] != '}' && i<len && j<MaxLen  )
		Parameter[j++] = p[i++];

	Parameter[j] = 0x0;
	return true;
}


bool GetIntVal( char*p, const char* Name, uint32_t* pVal )
{
	char *s, *e;
	e = p;
	
	while( e != NULL && *e )
	{
		s = strstr( e, Name );
		if ( s == NULL ) return false;
		if ( ( (s > p && IsDelimiter(*(s-1)) ) || s == p ) && IsDelimiter( *(s+strlen(Name)) )  ) //is a token name
		{
			s += strlen(Name);
			while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
			if ( *s == '=' || *s == ':' )
			{
				s++;
				while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
				if ( *s )
				{
					*pVal = atoi( s );
					return true;
				}
			}
		}
		e = ++s;	
	}
	return false;
}

bool GetWordVal( char*p, const char* Name, uint16_t* pVal )
{
	char *s, *e;
	e = p;
	
	while ( e != NULL && *e )
	{
		s = strstr( e, Name );
		if ( s == NULL ) return false;
		if ( ((s > p && IsDelimiter(*(s-1))) || s == p ) && IsDelimiter( *(s+strlen(Name)) )  ) //is a token name
		{
			s += strlen(Name);
			while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
			if ( *s == '=' || *s == ':' )
			{
				s++;
				while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
				if ( *s )
				{
					*pVal = (uint16_t)atoi( s );
					return true;
				}
			}
		}
		e = ++s;
	}
	return false;
}

bool GetString( char*p, const char* Name, char* Str, int MaxLen )
{
	char *s, *e;
	e = p;
	
	while ( e != NULL && *e )
	{
		s = strstr( e, Name );
		if ( s == NULL ) return false;
		if ( ((s > p && IsDelimiter(*(s-1))) || s == p ) && IsDelimiter( *(s+strlen(Name)) )  )  //is a token name
		{
			s += (int)strlen(Name);
			while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
			if ( *s == '=' || *s == ':' )
			{
				s++;
				while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
				if ( *s )
				{
					int i=0;
					while ( i++<MaxLen && *s && *s != ' ' && *s != '\t' )
						*Str++ = *s++;
	
					*Str = 0x0; //terminator
					return true;
				}
			}
		}
		e = ++s;
	}
	return false;
}

bool GetQuoteString( char*p, const char* Name, char* Str, int MaxLen )
{
	char *s, *e;
	e = p;
	
	while ( e != NULL && *e )
	{
		s = strstr( e, Name );
		if ( s == NULL ) return false;
		if ( ((s > p && IsDelimiter(*(s-1))) || s == p ) && IsDelimiter( *(s+strlen(Name)) )  )  //is a token name
		{
			s += (int)strlen(Name);
			while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
			if ( *s == '=' || *s == ':' )
			{
				s++;
				while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
				if ( *s == '\"' ) //quote string "..zvx.."
				{
					int i=0;
					s++;
					while ( i++<MaxLen && *s && *s != '\"' )
						*Str++ = *s++;
	
					*Str = 0x0; //terminator
					return true;

				} else
				if ( *s )
				{
					int i=0;
					while ( i++<MaxLen && *s && *s != ' ' && *s != '\t' )
						*Str++ = *s++;
	
					*Str = 0x0; //terminator
					return true;
				}
			}
		}
		e = ++s;
	}
	return false;
}

static bool parseDVBTFreqEntry( char* p, DVB_T_FREQ * Freq, char** error  )
{
	bool found = true;
	uint32_t val;
	if ( Freq == NULL )
		return false;

	if ( error ) *error = 0x0;
	Freq->frequency = 0; Freq->onid = 0; Freq->tsid = 0; Freq->sid = 0; Freq->ctrl = 0; Freq->index = 0;
	if ( !GetWordVal( p, "CH",  &Freq->index) && !GetWordVal( p, "c",  &Freq->index) )  
	{ 
		found = false; 
		if ( error && *error == 0x0 ) *error = "index not found 'CH:xx'"; 
	}
	if ( !GetWordVal( p, "onid", &Freq->onid ) )  { found = false; if ( error && *error == 0x0 ) *error = "onid not fooun"; }   
	if ( !GetWordVal( p, "tsid", &Freq->tsid ) )  { found = false; if ( error && *error == 0x0 ) *error = "tsid not found"; }
	if ( !GetWordVal( p, "sid",  &Freq->sid  ) )  { found = false; if ( error && *error == 0x0 ) *error = "sid not found"; }
	if ( !GetIntVal( p, "ctrl",  &Freq->ctrl ) )  { found = false; if ( error && *error == 0x0 ) *error = "ctrl not found"; }
	if ( !GetIntVal( p, "frq",   &Freq->frequency ) ) { found = false; if ( error && *error == 0x0 ) *error = "frequency not found"; }
	
	if ( GetIntVal( p, "band", &val ) )
	{
		Freq->bandwidth = val;
	} else
		Freq->bandwidth = 8;

	if ( GetIntVal( p, "mod", &val ) )
		Freq->constellation = (char)val;
	else
		Freq->constellation = 0xff;

	if ( GetIntVal( p, "mode", &val ) )
		Freq->trans_mode = (char)val;
	else
		Freq->trans_mode = 0xff;

	if ( GetIntVal( p, "rate_hp", &val ) )
		Freq->code_rate_hp = (char)val;
	else
		Freq->code_rate_hp = 0xff;
	if ( GetIntVal( p, "rate_lp", &val ) )
		Freq->code_rate_lp = (char)val;
	else
		Freq->code_rate_lp = 0xff;
	if ( GetIntVal( p, "guard", &val ) )
		Freq->guard_interval = (char)val;
	else
		Freq->guard_interval = 0xff;
	if ( GetIntVal( p, "hierarchy",  &val ) )
		Freq->hierarchy = (char)val;
	else
		Freq->hierarchy = 0xff;

	if ( GetIntVal( p, "shift",  &val ) )
		Freq->shift = val;
	else
		Freq->shift = 125000;

	if ( GetIntVal( p, "v_pid", &val ) )
		Freq->video_pid = (char)val;
	else
		Freq->video_pid = 0;

	if ( GetIntVal( p, "a_pid", &val ) )
		Freq->audio_pid = (char)val;
	else
		Freq->audio_pid = 0;


	memset( Freq->name, 0, sizeof(Freq->name) );
	GetString( p, "#", Freq->name, sizeof(Freq->name)-1 );		
	
	return found;
}

static bool parseDVBCFreqEntry( char* p, DVB_C_FREQ * Freq, char** error  )
{
	bool found = true;
	uint32_t val;
	if ( Freq == NULL )
		return false;

	if ( error ) *error=0x0;
	
	Freq->frequency = 0; Freq->onid = 0; Freq->tsid = 0; Freq->sid = 0; Freq->ctrl = 0; Freq->index = 0;
	Freq->symbol_rate = 0;
	if ( !GetWordVal( p, "CH",   &Freq->index) && !GetWordVal( p, "ch",   &Freq->index) )  { found = false; if ( error && *error == 0x0 ) *error = "index not found 'CH:xx'"; }
	if ( !GetWordVal( p, "onid", &Freq->onid ) )  { found = false; if ( error && *error == 0x0 ) *error = "onid not fooun"; }   
	if ( !GetWordVal( p, "tsid", &Freq->tsid ) )  { found = false; if ( error && *error == 0x0 ) *error = "tsid not found"; }
	if ( !GetWordVal( p, "sid",  &Freq->sid  ) )  { found = false; if ( error && *error == 0x0 ) *error = "sid not found"; }
	if ( !GetIntVal( p, "ctrl",  &Freq->ctrl ) )  { found = false; if ( error && *error == 0x0 ) *error = "ctrl not found"; }
	if ( !GetIntVal( p, "frq",   &Freq->frequency ) ) { found = false; if ( error && *error == 0x0 ) *error = "frequency not found"; }
	if ( !GetIntVal( p, "rate",  &Freq->symbol_rate ) ) { found = false; if ( error && *error == 0x0 ) *error = "symbol rate not found"; }

	if ( GetIntVal( p, "mod", &val ) )
		Freq->modulation = (char)val;
	else
	{
		found = false; if ( error && *error == 0x0 ) *error = "modualtion not found (mod)";
	}

	if ( GetIntVal( p, "fec_in", &val ) )
		Freq->fec_inner = (char)val;
	else
		Freq->fec_inner = 0xff;

	if ( GetIntVal( p, "fec_out", &val ) )
		Freq->fec_outer = (char)val;
	else
		Freq->fec_outer = 0xff;

	if ( GetIntVal( p, "fec_rate_in", &val ) )
		Freq->fec_inner_rate = (char)val;
	else
		Freq->fec_inner_rate = 0xff;

	if ( GetIntVal( p, "fec_rate_out", &val ) )
		Freq->fec_outer_rate = (char)val;
	else
		Freq->fec_outer_rate = 0xff;

	if ( GetIntVal( p, "v_pid", &val ) )
		Freq->video_pid = (char)val;
	else
		Freq->video_pid = 0;

	if ( GetIntVal( p, "a_pid", &val ) )
		Freq->audio_pid = (char)val;
	else
		Freq->audio_pid = 0;

	memset( Freq->name, 0, sizeof(Freq->name) );
	GetString( p, "#", Freq->name, sizeof(Freq->name) );		
	
	return found;
}

static bool parseDVBSFreqEntry( char* p, DVB_S_FREQ * Freq, char** error  )
{
	bool found = true;
	uint32_t val;
	if ( Freq == NULL )
		return false;

	if ( error ) *error=0x0;
	
	Freq->frequency = 0; Freq->onid = 0; Freq->tsid = 0; Freq->sid = 0; Freq->ctrl = 0; ; Freq->index = 0;
	Freq->symbol_rate = 0;
	if ( !GetWordVal( p, "CH",   &Freq->index) && !GetWordVal( p, "ch",   &Freq->index) )  { found = false; if ( error && *error == 0x0 ) *error = "index not found 'CH:xx'"; }
	if ( !GetWordVal( p, "onid", &Freq->onid ) )  { found = false; if ( error && *error == 0x0 ) *error = "onid not fooun"; }   
	if ( !GetWordVal( p, "tsid", &Freq->tsid ) )  { found = false; if ( error && *error == 0x0 ) *error = "tsid not found"; }
	if ( !GetWordVal( p, "sid",  &Freq->sid  ) )  { found = false; if ( error && *error == 0x0 ) *error = "sid not found"; }
	if ( !GetIntVal( p, "ctrl",  &Freq->ctrl ) )  { found = false; if ( error && *error == 0x0 ) *error = "ctrl not found"; }
	if ( !GetIntVal( p, "frq",   &Freq->frequency ) ) { found = false; if ( error && *error == 0x0 ) *error = "frequency not found"; }
	if ( !GetIntVal( p, "rate",  &Freq->symbol_rate ) ) { found = false; if ( error && *error == 0x0 ) *error = "symbol rate not found"; }
	

	if ( GetIntVal( p, "mod", &val ) )
		Freq->modulation = (char)val;
	else
		Freq->modulation = 0xff;

	if ( GetIntVal( p, "pol", &val ) ) 
		Freq->polarisation = (char)val;
	else
	{
		Freq->polarisation = 0xff;
		found = false; if ( error && *error == 0x0 ) *error = " polarisation (pol) not found";
	}

	if ( GetIntVal( p, "fec_in", &val ) )
		Freq->fec_inner = (char)val;
	else
		Freq->fec_inner = 0xff;

	if ( GetIntVal( p, "fec_out", &val ) )
		Freq->fec_outer = (char)val;
	else
		Freq->fec_outer = 0xff;

	if ( GetIntVal( p, "fec_rate_in", &val ) )
		Freq->fec_inner_rate = (char)val;
	else
		Freq->fec_inner_rate = 0xff;

	if ( GetIntVal( p, "fec_rate_out", &val ) )
		Freq->fec_outer_rate = (char)val;
	else
		Freq->fec_outer_rate = 0xff;

	if ( GetIntVal( p, "roll", &val ) )
		Freq->roll = (char)val;
	else
		Freq->roll = 0xff;

	if ( GetIntVal( p, "pilot", &val ) )
		Freq->pilot = (char)val;
	else
		Freq->pilot = 0xff;

	if ( GetIntVal( p, "orbit", &val ) )
		Freq->orbit = (char)val;
	else
		Freq->orbit = -1;

	if ( GetIntVal( p, "v_pid", &val ) )
		Freq->video_pid = (char)val;
	else
		Freq->video_pid = 0;

	if ( GetIntVal( p, "a_pid", &val ) )
		Freq->audio_pid = (char)val;
	else
		Freq->audio_pid = 0;

	if ( GetIntVal( p, "sat", &val ) )
		Freq->sat_no = (char)val;
	else
		Freq->audio_pid = 0xffff;


	memset( Freq->name, 0, sizeof(Freq->name) );
	GetString( p, "#", Freq->name, sizeof(Freq->name) );		
	
	return found;
}

static bool parseUserQAMFreqData( char* p, struct bcast_qam *Freq, char **error )
{
	bool found = true;
	uint32_t val;
	int frq;
	uint16_t ch;
	if ( Freq == NULL )
		return false;

	if ( error ) *error=0x0;
	
	Freq->fq = 0; 	Freq->ch = 0;  Freq->inv = 0;  Freq->mod = 0; 
	if ( !GetWordVal( p, "CH",   &ch) && !GetWordVal( p, "ch",   &ch ) )  { found = false; if ( error && *error == 0x0 ) *error = "index not found 'CH:xx'"; }
	if ( !GetIntVal( p, "frq",   (uint32_t*)&frq) ) { found = false; if ( error && *error == 0x0 ) *error = "frequency not found"; }

	if ( !found )
		return found;

	Freq->ch = (int)ch;
	Freq->fq = (uint32_t)frq;
	if ( GetIntVal( p, "mod", &val ) )
		Freq->mod = (char)val;

	if ( GetIntVal( p, "inv", &val ) ) 
		Freq->inv = (char)val;

	return found;
}

static bool parseScanATSCFreqData( char* p, ATSC_FREQ *Freq, char **error )
{
	bool found = false;
	uint32_t val;
	uint16_t n;
	if ( Freq == NULL )
		return false;

	if ( error ) *error="";
	
	//memset( Freq, 0, sizeof(ATSC_FREQ) );
	if ( GetWordVal( p, "CH",   &n ) || GetWordVal( p, "ch",   &n ) ) 
		Freq->index = n;

	if ( GetWordVal( p, "major", &n) ) 
		Freq->major = n;

	if ( GetWordVal( p, "minor", &n) ) 
		Freq->minor = n;

	if ( GetWordVal( p, "prog", &n) ) 
		Freq->program = n;

	if ( GetWordVal( p, "phy", &n) ) 
	{
		Freq->physical_ch = n;
		found = true;
	}

	if ( GetIntVal( p, "frq", &val) ) 
		Freq->frequency = (uint32_t)val;

	if ( GetIntVal( p, "frq", &val) ) 
		Freq->frequency = (uint32_t)val;

	if ( GetIntVal( p, "v_pid", &val ) )
		Freq->video_pid = (char)val;
	else
		Freq->video_pid = 0;

	if ( GetIntVal( p, "a_pid", &val ) )
		Freq->audio_pid = (char)val;
	else
		Freq->audio_pid = 0;

	
	GetString( p, "#", Freq->name, sizeof(Freq->name) );

	return found;
}

static bool parseScanQAMFreqData( char* p, QAM_FREQ *Freq, char **error )
{
	bool found = false;
	uint32_t val;
	uint16_t n;
	if ( Freq == NULL )
		return false;

	if ( error ) *error="";
	
	//memset( Freq, 0, sizeof(QAM_FREQ) );
	if ( GetWordVal( p, "CH",   &n) || GetWordVal( p, "ch",   &n ) ) 
		Freq->index = n;

	if ( GetWordVal( p, "major", &n) ) 
		Freq->major = n;

	if ( GetWordVal( p, "minor", &n) ) 
		Freq->minor = n;

	if ( GetWordVal( p, "prog", &n) ) 
		Freq->program = n;

	if ( GetWordVal( p, "phy", &n) ) 
	{
		Freq->physical_ch = n;
		found = true;
	}

	if ( GetIntVal( p, "frq", &val) ) 
		Freq->frequency = (uint32_t)val;

	if ( GetIntVal( p, "mod", &val ) )
		Freq->modulation = (char)val;

	if ( GetIntVal( p, "inv", &val ) ) 
		Freq->inversal = (char)val;

	if ( GetIntVal( p, "v_pid", &val ) )
		Freq->video_pid = (char)val;
	else
		Freq->video_pid = 0;

	if ( GetIntVal( p, "a_pid", &val ) )
		Freq->audio_pid = (char)val;
	else
		Freq->audio_pid = 0;

		
	GetString( p, "#", Freq->name, sizeof(Freq->name) );

	return found;
}

char* makeATSCFreqString( char*Buf, int Size,  ATSC_FREQ * Freq  )
{
	char tmp[100], *p;
	if ( Buf == NULL || Size <= 0 ) return "";
	p = Buf;
	*p = 0x0;

	SPRINTF( tmp, sizeof(tmp), "CH:%d ", Freq->index );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "major:%d ", Freq->major );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "minor:%d ", Freq->minor );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "prog:%d ", Freq->program );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "phy:%d ", Freq->physical_ch );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "frq:%d ", Freq->frequency );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );

	if ( Freq->name[0] )
	{
		int i;
		SPRINTF( tmp, sizeof(tmp), "#:%s", Freq->name );
		for ( i = 0; i<sizeof(Freq->name)+1 && tmp[i]; i++ )
			if ( tmp[i] == ' ' ) tmp[i] = '-';
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	
	return Buf;
}


char* makeQAMFreqString( char*Buf, int Size,  QAM_FREQ * Freq  )
{
	char tmp[100], *p;
	if ( Buf == NULL || Size <= 0 ) return "";
	p = Buf;
	*p = 0x0;

	SPRINTF( tmp, sizeof(tmp), "CH:%d ", Freq->index );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "major:%d ", Freq->major );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "minor:%d ", Freq->minor );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "prog:%d ", Freq->program );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "phy:%d ", Freq->physical_ch );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "frq:%d ", Freq->frequency );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "mod:%d ", Freq->modulation );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "inv:%d ", Freq->inversal );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );

	if ( Freq->name[0] )
	{
		int i;
		SPRINTF( tmp, sizeof(tmp), "#:%s", Freq->name );
		for ( i = 0; i<sizeof(Freq->name)+1 && tmp[i]; i++ )
			if ( tmp[i] == ' ' ) tmp[i] = '-';
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	return Buf;
}

char* makeDVBTFreqString( char*Buf, int Size,  DVB_T_FREQ * Freq  )
{
	char tmp[100], *p;
	if ( Buf == NULL || Size <= 0 ) return "";
	p = Buf;
	*p = 0x0;

	SPRINTF( tmp, sizeof(tmp), "CH:%d ", Freq->index );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "onid:%d ", Freq->onid );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "tsid:%d ", Freq->tsid );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "sid:%d ", Freq->sid );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "frq:%d ", Freq->frequency );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "band:%d ", Freq->bandwidth );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	if ( Freq->trans_mode != 0xff ) 
	{
		SPRINTF( tmp, sizeof(tmp), "mode:%d ", Freq->trans_mode );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	}
	if ( Freq->code_rate_hp != 0xff && Freq->code_rate_hp != (char)0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "rate_hp:%d ", Freq->code_rate_hp );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->code_rate_lp != 0xff && Freq->code_rate_lp != (char)0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "rate_lp:%d ", Freq->code_rate_lp );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->guard_interval != 0xff && Freq->guard_interval != 0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "guard:%d ", Freq->guard_interval );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->hierarchy != 0xff && Freq->hierarchy!= 0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "hierarchy:%d ", Freq->hierarchy );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->video_pid != 0xffff && Freq->video_pid != 0 )
	{
		SPRINTF( tmp, sizeof(tmp), "v_pid:%d ", Freq->video_pid );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->audio_pid != 0xffff && Freq->audio_pid != 0 )
	{
		SPRINTF( tmp, sizeof(tmp), "a_pid:%d ", Freq->audio_pid );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}


	SPRINTF( tmp, sizeof(tmp), "ctrl:%d ", Freq->ctrl );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	if ( Freq->name[0] )
	{
		int i;
		SPRINTF( tmp, sizeof(tmp), "#:%s", Freq->name );
		for ( i = 0; i<sizeof(Freq->name)+1 && tmp[i]; i++ )
			if ( tmp[i] == ' ' ) tmp[i] = '-';
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	return Buf;
}

char* makeDVBCFreqString( char*Buf, int Size,  DVB_C_FREQ * Freq  )
{
	char tmp[100], *p;
	if ( Buf == NULL || Size <= 0 ) return "";
	p = Buf;
	*p = 0x0;

	SPRINTF( tmp, sizeof(tmp), "CH:%d ", Freq->index );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "onid:%d ", Freq->onid );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "tsid:%d ", Freq->tsid );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "sid:%d ", Freq->sid );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "frq:%d ", Freq->frequency );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "rate:%d ", Freq->symbol_rate );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	if ( Freq->modulation != 0xff ) 
	{
		SPRINTF( tmp, sizeof(tmp), "mod:%d ", Freq->modulation );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( Freq->fec_inner != 0xff && Freq->fec_inner != (char)0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "fec_in:%d ", Freq->fec_inner );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->fec_inner_rate != 0xff && Freq->fec_inner_rate != (char)0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "fec_rate_in:%d ", Freq->fec_inner_rate );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->fec_outer != 0xff && Freq->fec_outer != 0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "fec_out:%d ", Freq->fec_outer );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->fec_outer_rate != 0xff && Freq->fec_outer_rate != 0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "fec_rate_out:%d ", Freq->fec_outer_rate );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( Freq->video_pid != 0xffff && Freq->video_pid != 0 )
	{
		SPRINTF( tmp, sizeof(tmp), "v_pid:%d ", Freq->video_pid );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( Freq->audio_pid != 0xffff && Freq->audio_pid != 0 )
	{
		SPRINTF( tmp, sizeof(tmp), "a_pid:%d ", Freq->audio_pid );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	SPRINTF( tmp, sizeof(tmp), "ctrl:%d ", Freq->ctrl );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	if ( Freq->name[0] )
	{
		int i;
		SPRINTF( tmp, sizeof(tmp), "#:%s", Freq->name );
		for ( i = 0; i<sizeof(Freq->name)+1 && tmp[i]; i++ )
			if ( tmp[i] == ' ' ) tmp[i] = '-';
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	return Buf;
}

char* makeDVBSFreqString( char*Buf, int Size,  DVB_S_FREQ * Freq  )
{
	char tmp[100], *p;
	if ( Buf == NULL || Size <= 0 ) return "";
	p = Buf;
	*p = 0x0;

	SPRINTF( tmp, sizeof(tmp), "CH:%d ", Freq->index );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "onid:%d ", Freq->onid );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "tsid:%d ", Freq->tsid );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "sid:%d ", Freq->sid );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "frq:%d ", Freq->frequency );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	SPRINTF( tmp, sizeof(tmp), "rate:%d ", Freq->symbol_rate );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	if ( Freq->modulation != 0xff ) 
	{
		SPRINTF( tmp, sizeof(tmp), "mod:%d ", Freq->modulation );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( Freq->polarisation != 0xff ) 
	{
		SPRINTF( tmp, sizeof(tmp), "pol:%d ", Freq->polarisation );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}


	if ( Freq->fec_inner != 0xff && Freq->fec_inner != (char)0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "fec_in:%d ", Freq->fec_inner );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->fec_inner_rate != 0xff && Freq->fec_inner_rate != (char)0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "fec_rate_in:%d ", Freq->fec_inner_rate );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->fec_outer != 0xff && Freq->fec_outer != 0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "fec_out:%d ", Freq->fec_outer );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->fec_outer_rate != 0xff && Freq->fec_outer_rate != 0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "fec_rate_out:%d ", Freq->fec_outer_rate );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->roll != 0xff && Freq->roll != 0  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "roll:%d ", Freq->roll );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	if ( Freq->pilot != 0xff  ) 
	{
		SPRINTF( tmp, sizeof(tmp), "pilot:%d ", Freq->pilot );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( Freq->orbit != -1 && Freq->orbit != 0 ) 
	{
		SPRINTF( tmp, sizeof(tmp), "orbit:%d ", Freq->orbit );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( Freq->video_pid != 0xffff && Freq->video_pid != 0 )
	{
		SPRINTF( tmp, sizeof(tmp), "v_pid:%d ", Freq->video_pid );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( Freq->audio_pid != 0xffff && Freq->audio_pid != 0 )
	{
		SPRINTF( tmp, sizeof(tmp), "a_pid:%d ", Freq->audio_pid );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}

	if ( Freq->sat_no != 0xffff )
	{
		SPRINTF( tmp, sizeof(tmp), "sat:%d ", Freq->sat_no );
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}


	SPRINTF( tmp, sizeof(tmp), "ctrl:%d ", Freq->ctrl );
	if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	
	if ( Freq->name[0] )
	{
		int i;
		SPRINTF( tmp, sizeof(tmp), "#:%s", Freq->name );
		for ( i = 0; i<sizeof(Freq->name)+1 && tmp[i]; i++ )
			if ( tmp[i] == ' ' ) tmp[i] = '-';
		if ( (int)strlen( tmp )+ (int)strlen( Buf ) + 1 < Size ) strcat( Buf, tmp );
	}
	return Buf;
}

/*
int UpdateDVBPreDefinedFreqTable( CHANNEL_DATA *Channel, char* NIT_list, int total_bytes, int List_num )
{
	char *buf;
	char *p_in, *p_next, *token;
	int  i, updated = 0, line = 0;

	SageLog(( _LOG_TRACE, 3, "------------>Update Predefined scan table with NIT:%d\r\n", List_num ));
	
	if ( Channel->PreDefinedFreqTable == NULL )
		return 0;

	p_in = NIT_list;
	while( p_in != NULL )
	{
		token = (char*)strtok_r( p_in, "\r\n", &p_next );
		if ( token == NULL ) 
			return updated;

		buf = token;

		p_in = p_next;
	
		//rip off '\n'
		for ( i = strlen(buf); i>=0 ; i-- )
			if ( buf[i] == '\n' || buf[i] == '\r' ) buf[i] = ' ';
			
		//skip white space
		i = 0;
		while ( (buf[i] == ' ' || buf[i] == '\t' ) && i<sizeof(buf) ) i++;

		if ( !STRNICMP( buf+i, "VERSION+7", 7 ) )
		{
			
		} else
		if ( !STRNICMP( buf+i, "TYPE", 4 ) )
		{
			char* p, text[16];
			if ( getNextTextField( buf+i+4, &p, text, sizeof(text) ) )
			{
				if ( STRICMP( text, Channel->FreqTable.sourceType ) )
					SageLog(( _LOG_TRACE, 3, "Error: tune type '%s' is not expected. (expect:%s) in NIT_TABLE\r\n",
					       text, Channel->FreqTable.sourceType));
					
			}
		} else
		if ( ( buf[i] == '!' ) || ( buf[i] == '#' ) )
		{	//skip comment line
			continue;
		} else
		if ( !STRNICMP( buf+i, "CH:", 3 ) )
		{
			char *p;
			if ( Channel->FreqTable.entryNum >= Channel->FreqTable.totalNum) 
				GrowFreqTable( Channel );

				
			if ( !strcmp( Channel->sourceType, "ATSC" ) )
			{
				ATSC_FREQ ATSCFreq;
				p = buf+i+3;
				if ( parseATSCFreqEntry( p, &ATSCFreq ) )
				{
					;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d \r\n", line ));
			} else
			if ( !strcmp( Channel->sourceType, "QAM" ) )
			{
				QAM_FREQ QAMFreq;
				p = buf+i+3;
				if ( parseQAMFreqEntry( p, &QAMFreq ) )
				{
					;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d \r\n", line ));
			} else
			if ( !strcmp( Channel->sourceType, "DVB-T" ) )
			{
				DVB_T_FREQ DVBTFreq={0};
				char* error="";
				p = buf+i;
				parseDVBTFreqEntry( p, &DVBTFreq, &error );
				if ( DVBTFreq.frequency )
				{
					Channel->FreqTable.entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d  (err:%s)\r\n",
					        line,  error ));
			} else
			if ( !strcmp( Channel->sourceType, "DVB-C" ) )
			{
				DVB_C_FREQ DVBCFreq={0};
				char* error="";
				p = buf+i;
				parseDVBCFreqEntry( p, &DVBCFreq, &error );
				if ( DVBCFreq.frequency )
				{
					Channel->FreqTable.entryNum++;
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d (err:%s)\r\n",
					        line,  error ));
			} else
			if ( !strcmp( Channel->sourceType, "DVB-S" ) )
			{
				DVB_S_FREQ DVBSFreq={0};
				char* error="";
				p = buf+i;
				parseDVBSFreqEntry( p, &DVBSFreq, &error );
				if ( DVBSFreq.frequency )
				{
					if ( DVBSFreq.modulation )
					{
						for ( i = 0; i<Channel->PreDefinedFreqTable->entryNum; i++ )
						{
							if ( Channel->PreDefinedFreqTable->tbl.dvbs[i].frequency == DVBSFreq.frequency )
								break;
						}

						if ( i >= Channel->PreDefinedFreqTable->entryNum  )
						{
							if (  Channel->PreDefinedFreqTable->entryNum < Channel->PreDefinedFreqTable->totalNum )
							{
								SageLog(( _LOG_TRACE, 3, "----->Add frq:%d rate:%d pol:%d fec_in_rate:%d into scan table %d\r\n",
									DVBSFreq.frequency, DVBSFreq.symbol_rate, DVBSFreq.polarisation, DVBSFreq.fec_inner_rate, Channel->PreDefinedFreqTable->entryNum ) );

								Channel->PreDefinedFreqTable->tbl.dvbs[Channel->PreDefinedFreqTable->entryNum].frequency    = DVBSFreq.frequency;
								Channel->PreDefinedFreqTable->tbl.dvbs[Channel->PreDefinedFreqTable->entryNum].symbol_rate  = DVBSFreq.symbol_rate;
								Channel->PreDefinedFreqTable->tbl.dvbs[Channel->PreDefinedFreqTable->entryNum].polarisation = DVBSFreq.polarisation;
								Channel->PreDefinedFreqTable->tbl.dvbs[Channel->PreDefinedFreqTable->entryNum].modulation   = DVBSFreq.modulation;
								Channel->PreDefinedFreqTable->tbl.dvbs[Channel->PreDefinedFreqTable->entryNum].fec_inner_rate = DVBSFreq.fec_inner_rate;
								Channel->PreDefinedFreqTable->tbl.dvbs[Channel->PreDefinedFreqTable->entryNum].fec_inner = 0x0;
								Channel->PreDefinedFreqTable->tbl.dvbs[Channel->PreDefinedFreqTable->entryNum].fec_outer = 0x0;
								Channel->PreDefinedFreqTable->tbl.dvbs[Channel->PreDefinedFreqTable->entryNum].fec_outer_rate = 0x0;
								Channel->PreDefinedFreqTable->tbl.dvbs[Channel->PreDefinedFreqTable->entryNum].sat_no = 0xffff;
								Channel->PreDefinedFreqTable->entryNum++;
								updated++;
							}
						}
					}
				} else
				  	SageLog(( _LOG_TRACE, 3, "Error: data entry is not expected at line:%d  (err:%s)\r\n",
					        line,  error ));
			} else
			{
				SageLog(( _LOG_TRACE, 3, "Unknown sourceType '%s' for Update PredefineTable", Channel->sourceType ));
				break;
			}
		} 
		
		line++;
	}

	return updated;
}
*/

int SaveDVBSPreDefinedFreqTable( CHANNEL_DATA *Channel )
{
	return 0;	
}

int TranslateJWideString( char* buf_out, int buf_out_size, uint16_t* buf_in )
{
	int len=0, i=0;
	uint16_t* p = (uint16_t*)buf_in;
	wchar_t* v;
	int ret;
	if ( buf_out == NULL ) return 0;
	while (  *p++  ) len++;

	len = min( buf_out_size-1, len+1 );
	v= (wchar_t*)malloc( len*sizeof(wchar_t) );
	p = (uint16_t*)buf_in;
	
#if defined(__APPLE__)
	for ( i = 0; i<len; i++ ) v[i] = OSReadLittleInt16(p, i * 2);
#else
	for ( i = 0; i<len; i++ ) v[i] = p[i]; 
#endif
	ret = wcstombs( buf_out, (wchar_t*)v, len+1 );
	
	
	if ( ret == (size_t)-1 )
	{
		p = (uint16_t*)buf_in;
		for ( i = 0; i<len; i++ ) 
		     buf_out[i]= (char)( 0xff & p[i] ); 
		buf_out[len] = 0x0;
	}
	free( v );
	return strlen( buf_out );
}

#if !defined(__APPLE__)
//translate big endian charactter
int TranslateJWideString2( char* buf_out, int buf_out_size, uint16_t* buf_in )
{
	int len=0, i=0;
	uint16_t* p = (uint16_t*)buf_in;
	wchar_t* v;
	int ret;
	if ( buf_out == NULL ) return 0;
	while (  *p++  ) len++;

	len = min( buf_out_size-1, len+1 );
	v= (wchar_t*)malloc( len*sizeof(wchar_t) );
	p = (uint16_t*)buf_in;
	
	for ( i = 0; i<len; i++ ) v[i] = ( ((p[i]<<8)&0xff00) | p[i] >> 8 ); 
	
	ret = wcstombs( buf_out, (wchar_t*)v, len+1 );
	
	
	if ( ret == (size_t)-1 )
	{
		p = (uint16_t*)buf_in;
		for ( i = 0; i<len; i++ ) 
		     buf_out[i]= (char)( 0xff & p[i] ); 
		buf_out[len] = 0x0;
	}
	free( v );
	return strlen( buf_out );
}
#endif

int ConvertIllegalChar( char* filename )
{
	int i, num = 0;
	//convert  illegal characters
	for ( i = 0; i<(int)strlen(filename) && i <_MAX_PATH; i++ )
	{
		if ( filename[i] == '\\' || filename[i] == '/' || filename[i] == ':' || filename[i] == '?' || filename[i] == '.' ||
				filename[i] == '*'  || filename[i] == '<' || filename[i] == '>'  || filename[i] == '|' )
		{
			filename[i] = '-'; num++;
		}
	}
	return num;
}

// ConvertIllegalChar2 tanslate "space" into -, but ConvertIllegalChar not
int ConvertIllegalChar2( char* filename )
{
	int i, num = 0;
	//convert  illegal characters
	for ( i = 0; i<(int)strlen(filename) && i <_MAX_PATH; i++ )
	{
		if ( filename[i] == '\\' || filename[i] == '/' || filename[i] == ':' || filename[i] == '?' || filename[i] == '.' ||
				filename[i] == '*'  || filename[i] == '<' || filename[i] == '>'  || filename[i] == '|' || filename[i] == ' ' ||
				filename[i] == '(' || filename[i] == ')' )
		{
			filename[i] = '-'; num++;
		}
	}
	return num;
}

#include <sys/stat.h>
uint32_t fileTimeStamp( char* szFileName )
{
	struct stat st;
	if ( szFileName == NULL || szFileName[0] == 0 )
		return 0;
	stat( szFileName, &st );
	return st.st_mtime;
}
