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

#include <windows.h>
#endif
#include <stdlib.h>
#include <stdio.h>
#include <memory.h>
#include <string.h>


#include "NativeCore.h"
#include "Remuxer.h"
#include "ChannelScan.h"
#include "Channel.h"

#include "../../include/uniapi.h"

#ifdef WIN32
//*********************** WINDOWS section *********************
#define rename( x, y )	MoveFile( x, y )
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

#include <errno.h>

// Apple OS/X has no features.h
// Apple OS/X has no linux/dvb/frontend.h
// Apple OS/X handles types via machine/types.h
#include <machine/types.h>

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

//*********************** LINUX section ***********************
#endif
#endif

#include <stdarg.h>
#include <time.h>


#define _BDA_MOD_256QAM  11
#define _BDA_MOD_64QAM   3

#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _flog x
#endif

#ifdef WIN32   
#include <time.h>
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}

void usleep( uint32_t usecond )
{
	Sleep( usecond/1000 );
}

#undef SageLog
#define SageLog(x)   _sagelog_l x

int sagelog_enabled_l=0;
int console_enabled_l = 1;
int trace_level_l = 3;
char log_filename_l[128]="Native.log";
void _sagelog_l( int type, int level, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
    char szInfo[1024*3];
    va_list va;
	FILE* fp;
	 
	if ( !sagelog_enabled_l ) return;
	if ( type == _LOG_TRACE && level > trace_level_l ) return;

	fp = fopen( log_filename_l, "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );

	
    va_start(va, cstr);

	snprintf( szInfo, sizeof(szInfo)-3, TEXT("%02d/%02d/%d %02d:%02d:%02d "), ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
			ltm.tm_hour, ltm.tm_min, ltm.tm_sec  );  

    vsnprintf(szInfo + lstrlen(szInfo), (int)sizeof(szInfo)-lstrlen(szInfo)-3, cstr, va);
    lstrcat(szInfo, TEXT("\r\n"));
	fwrite( (wchar_t*)szInfo, 1, lstrlen(szInfo),  fp ); 

	if ( console_enabled_l )
		printf( "%s", szInfo );

	va_end(args);
	fclose( fp );
}

void enable_channel_log()
{
	sagelog_enabled_l = true;
}

void disable_channel_log()
{
	sagelog_enabled_l = false;
}


#endif


//static bool flog_enabled=false;
//void _set_ChannelDataMng_log( bool enable );

/*
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


static void _flog_check_local()
{
	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
		_set_ChannelDataMng_log( true );
	}

}
*/
static int _CopyFile( char* src, char *tar );
static bool QAM64_Enabled = false;
static void _64QAM_Enable_check()
{
	FILE* fp = fopen( "64QAM.ENABLE", "r" );
	if ( fp != NULL )
	{
		QAM64_Enabled = true;
		fclose( fp );
		SageLog(( _LOG_TRACE, 3, "64QAM is enabled\r\n" ));
	}
}

static bool QAM_FRQ_Cache_enabled = false;
static void _QAMFrqCache_Enable_check()
{
	FILE* fp = fopen( "QAM_FRQ_CACHE.ENABLE", "r" );
	if ( fp != NULL )
	{
		QAM_FRQ_Cache_enabled = true;
		fclose( fp );
		SageLog(( _LOG_TRACE, 3, "QAM Cache is enabled\r\n" ));
	} else
	{
		SageLog(( _LOG_TRACE, 3, "QAM Cache is disabled\r\n" ));
		QAM_FRQ_Cache_enabled = false;
	}
}

static bool QAM_clear_check_disable = false;
static void _QAMClearCheck_Disable_check()
{
	FILE* fp = fopen( "QAM_CLEAR_CHECK.DISABLE", "r" );
	if ( fp != NULL )
	{
		QAM_clear_check_disable = true;
		fclose( fp );
		SageLog(( _LOG_TRACE, 3, "QAM clear check is disabled\r\n" ));
	} else
	{
		QAM_clear_check_disable = false;
	}
}

int TranslateJWideString2( char* buf_out, int buf_out_size, uint16_t* buf_in );
int TranslateJWideString( char* buf_out, int buf_out_size, uint16_t* buf_in );
int GrowFreqTable( CHANNEL_DATA *Channel );
int GrowPredefineFreqTable( CHANNEL_DATA *Channel );
int saveScanTuningTable( CHANNEL_DATA *Channel, char* file_ext );
int saveCacheQAMFreq(  CHANNEL_DATA *Channel, int freqType, struct bcast_qam *freqTbl );
void _set_ChannelDataMng_log( bool enable );

#define LAST_ATSC_CHANNEL	83
struct bcast ATSC_BRCAST[256] = {
	  1,	 51250,
      2,	 55250,
      3,	 61250,
      4,	 67250,
      5,	 77250,
      6,	 83250,
      7,	175250,
      8,	181250,
      9,	187250,
      10,	193250,
      11,	199250,
      12,	205250,
      13,	211250,
      14,	471250,
      15,	477250,
      16,	483250,
      17,	489250,
      18,	495250,
      19,	501250,
      20,	507250,
      21,	513250,
      22,	519250, 
      23,	525250,
      24,	531250,
      25,	537250,
      26,	543250,
      27,	549250,
      28,	555250,
      29,	561250,
      30,	567250,
      31,	573250,
      32,	579250,
      33,	585250,
      34,	591250,
      35,	597250,
      36,	603250,
      37,	609250,
      38,	615250,
      39,	621250,
      40,	627250,
      41,	633250,
      42,	639250,
      43,	645250,
      44,	651250,
      45,	657250,
      46,	663250,
      47,	669250,
      48,	675250,
      49,	681250,
      50,	687250,
      51,	693250,
      52,	699250,
      53,	705250,
      54,	711250,
      55,	717250,
      56,	723250,
      57,	729250,
      58,	735250,
      59,	741250,
      60,	747250,
      61,	753250,
      62,	759250,
      63,	765250,
      64,	771250,
      65,	777250,
      66,	783250,
      67,	789250,
      68,	795250,
      69,	801250,
      70,	807250,
      71,	813250,
      72,	819250,
      73,	825250,
      74,	831250,
      75,	837250,
      76,	843250,
      77,	849250,
      78,	855250,
      79,	861250,
      80,	867250,
      81,	873250,
      82,	879250,
      83,	885250,
	0,	0
};

#define LAST_NTSC_CABLE_CHANNEL	125
struct bcast NTSC_CABLE[256] = {
      1,	 73250,
      2,	 55250,
      3,	 61250,
      4,	 67250,
      5,	 77250,
      6,	 83250,
      7,	175250,
      8,	181250,
      9,	187250,
      10,	193250,
      11,	199250,
      12,	205250,

      13,	211250,
      14,	121250,
      15,	127250,
      16,	133250,
      17,	139250,
      18,	145250,
      19,	151250,
      20,	157250,

      21,	163250,
      22,	169250,
      23,	217250,
      24,	223250,
      25,	229250,
      26,	235250,
      27,	241250,
      28,	247250,
      29,	253250,
      30,	259250,
      31,	265250,
      32,	271250,
      33,	277250,
      34,	283250,
      35,	289250,
      36,	295250,
      37,	301250,
      38,	307250,
      39,	313250,
      40,	319250,
      41,	325250,
      42,	331250,
      43,	337250,
      44,	343250,
      45,	349250,
      46,	355250,
      47,	361250,
      48,	367250,
      49,	373250,
      50,	379250,
      51,	385250,
      52,	391250,
      53,	397250,
      54,	403250,
      55,	409250,
      56,	415250,
      57,	421250,
      58,	427250,
      59,	433250,
      60,	439250,
      61,	445250,
      62,	451250,
      63,	457250,
      64,	463250,
      65,	469250,
      66,	475250,
      67,	481250,
      68,	487250,
      69,	493250,

      70,	499250,
      71,	505250,
      72,	511250,
      73,	517250,
      74,	523250,
      75,	529250,
      76,	535250,
      77,	541250,
      78,	547250,
      79,	553250,
      80,	559250,
      81,	565250,
      82,	571250,
      83,	577250,
      84,	583250,
      85,	589250,
      86,	595250,
      87,	601250,
      88,	607250,
      89,	613250,
      90,	619250,
      91,	625250,
      92,	631250,
      93,	637250,
      94,	643250,
      95,	 91250,
      96,	 97250,
      97,	103250,
      98,	109250,
      99,	115250,
      100,	649250,
      101,	655250,
      102,	661250,
      103,	667250,
      104,	673250,
      105,	679250,
      106,	685250,
      107,	691250,
      108,	697250,
      109,	703250,
      110,	709250,
      111,	715250,
      112,	721250,
      113,	727250,
      114,	733250,
      115,	739250,
      116,	745250,
      117,	751250,
      118,	757250,
      119,	763250,
      120,	769250,
      121,	775250,
      122,	781250,
      123,	787250,
      124,	793250,
      125,	799250,
	  126,  805250,
	  127,  811250,
	  128,  817250,
	  129,  823250,
      130,  829250,
      131,  835250,
	  132,  841250,
	  133,  847250,
	  134,  853250,
	  0,	0
};

#define LAST_NTSC_HRC_CHANNEL	125
struct bcast NTSC_HRC[256] = {
      1,	  72000,
      2,	  54000,
      3,	  60000,
      4,	  66000,
      5,	  78000,
      6,	  84000,
      7,	 174000,
      8,	 180000,
      9,	 186000,
      10,	 192000,
      11,	 198000,
      12,	 204000,

      13,	 210000,
      14,	 120000,
      15,	 126000,
      16,	 132000,
      17,	 138000,
      18,	 144000,
      19,	 150000,
      20,	 156000,

      21,	 162000,
      22,	 168000,
      23,	 216000,
      24,	 222000,
      25,	 228000,
      26,	 234000,
      27,	 240000,
      28,	 246000,
      29,	 252000,
      30,	 258000,
      31,	 264000,
      32,	 270000,
      33,	 276000,
      34,	 282000,
      35,	 288000,
      36,	 294000,
      37,	 300000,
      38,	 306000,
      39,	 312000,
      40,	 318000,
      41,	 324000,
      42,	 330000,
      43,	 336000,
      44,	 342000,
      45,	 348000,
      46,	 354000,
      47,	 360000,
      48,	 366000,
      49,	 372000,
      50,	 378000,
      51,	 384000,
      52,	 390000,
      53,	 396000,
      54,	 402000,
      55,	 408000,
      56,	 414000,
      57,	 420000,
      58,	 426000,
      59,	 432000,
      60,	 438000,
      61,	 444000,
      62,	 450000,
      63,	 456000,
      64,	 462000,
      65,	 468000,
      66,	 474000,
      67,	 480000,
      68,	 486000,
      69,	 492000,

      70,	 498000,
      71,	 504000,
      72,	 510000,
      73,	 516000,
      74,	 522000,
      75,	 528000,
      76,	 534000,
      77,	 540000,
      78,	 546000,
      79,	 552000,
      80,	 558000,
      81,	 564000,
      82,	 570000,
      83,	 576000,
      84,	 582000,
      85,	 588000,
      86,	 594000,
      87,	 600000,
      88,	 606000,
      89,	 612000,
      90,	 618000,
      91,	 624000,
      92,	 630000,
      93,	 636000,
      94,	 642000,
      95,	 900000,
      96,	 960000,
      97,	 102000,
      98,	 108000,
      99,	 114000,
      100,	 648000,
      101,	 654000,
      102,	 660000,
      103,	 666000,
      104,	 672000,
      105,	 678000,
      106,	 684000,
      107,	 690000,
      108,	 696000,
      109,	 702000,
      110,	 708000,
      111,	 714000,
      112,	 720000,
      113,	 726000,
      114,	 732000,
      115,	 738000,
      116,	 744000,
      117,	 750000,
      118,	 756000,
      119,	 762000,
      120,	 768000,
      121,	 774000,
      122,	 780000,
      123,	 786000,
      124,	 792000,
      125,	 798000,
	0,	0
};

#define MAX_USER_DEFINED_QAM 200

struct bcast_qam user_defined_qam_tbl[MAX_USER_DEFINED_QAM]={0};

#define LOCK_FREQ_TIMEOUT    (1000/100)  /* 0.1 second */
#define SLEEP_100MS			 (100000)    /* 0.1 second */

static void initUseDefinedQAMFrq(  int freqTableType, int shiftFrq );
static int WaiteScanDone( CHANNEL_DATA *Channel );
static int IsInDVBPreDefinedFreqTable( FREQTBL* pPreDefinedFreqTable, uint32_t lFreq );
static struct bcast_qam * getUseDefinedQAMFrq( int channel );

int openChannel( CHANNEL_DATA *Channel, void* Dev )
{
	//_flog_check_local();
	_64QAM_Enable_check();
	_QAMFrqCache_Enable_check();
	_QAMClearCheck_Disable_check();

	Channel->Dev = Dev;
	Channel->lastTune = 0;
	Channel->outputFormat = 1;
	Channel->freqTableType = 0;	     //1:ATSC, 2:NTSC-CABLE, 3:NTSC-HRC, 4:userdefined
	Channel->sourceType[0]=0;        //ATSC, QAM, DVB-T, DVB-C, DVB-S, DVB-S2
	Channel->countryRegion[0] = 0;
	Channel->countryCode = 0;
	Channel->tunerMode[0] = 0;
	Channel->lockTimeout= 4*LOCK_FREQ_TIMEOUT ; //4 second
#ifdef WIN32
	Channel->freqShift = 0;
#else
	Channel->freqShift = 1;
#endif
	Channel->freqScheme = 0;
	memset( &Channel->FreqTable, 0, sizeof(Channel->FreqTable) );
	Channel->PreDefinedFreqTable = NULL;  
	Channel->scanType = 0;
	Channel->delayParse = 0;
	Channel->frqTableTimeStamp = 0;
	Channel->frqTableUpdateCheck = 0;
	Channel->frqTableFileName[0] =0;
	Channel->frqTableFilePath[0] = 0;
	Channel->guessFreqTableType = 0;
	Channel->freqTableType = 0;
			
	SageLog(( _LOG_TRACE, 3, "Channel is open (ver 3.0.4)\r\n" ));
	return 1;
}

void closeChannel( CHANNEL_DATA *Channel )
{
	if ( Channel == NULL ) return;
	if ( !strncmp( Channel->sourceType, "ATSC", 4 ) )
	{
		if ( Channel->FreqTable.tbl.atsc != NULL )
		{
			free( Channel->FreqTable.tbl.atsc );
			Channel->FreqTable.tbl.atsc = NULL;
		}
	} else
	if ( !strncmp( Channel->sourceType, "QAM", 3 ) )
	{
		if ( Channel->FreqTable.tbl.qam != NULL )
		{
			free( Channel->FreqTable.tbl.qam );
			Channel->FreqTable.tbl.qam = NULL;
		}
	}
	if ( !strncmp( Channel->sourceType, "DVB-T", 5 ) )
	{
		if ( Channel->FreqTable.tbl.dvbt != NULL )
		{
			free( Channel->FreqTable.tbl.dvbt );
			Channel->FreqTable.tbl.dvbt = NULL;
		}
	}
	if ( !strncmp( Channel->sourceType, "DVB-C", 5 ) )
	{
		if ( Channel->FreqTable.tbl.dvbc != NULL )
		{
			free( Channel->FreqTable.tbl.dvbc );
			Channel->FreqTable.tbl.dvbc = NULL;
		}
	}
	if ( !strncmp( Channel->sourceType, "DVB-S", 5 ) )
	{
		if ( Channel->FreqTable.tbl.dvbs != NULL )
		{
			free( Channel->FreqTable.tbl.dvbs );
			Channel->FreqTable.tbl.dvbs = NULL;
		}
	}
	if ( Channel->PreDefinedFreqTable )
	{
		if ( Channel->PreDefinedFreqTable->tbl.dvbt != NULL )
			free( Channel->PreDefinedFreqTable->tbl.dvbt );
		free( Channel->PreDefinedFreqTable );
	}

	SageLog((_LOG_TRACE, 3, "Channel closed. sourceType:%s\r\n", Channel->sourceType));
}

void* setChannelDev( CHANNEL_DATA *Channel, void* Dev )
{
	void *oldDev = Channel->Dev;
	Channel->Dev = Dev;
	return oldDev;
}

void  setSourceType( CHANNEL_DATA *Channel, char* type )
{
	strncpy( Channel->sourceType, type, sizeof(Channel->sourceType) );
}

char* getSourceType( CHANNEL_DATA *Channel )
{
	return Channel->sourceType;
}

void  setFreqType( CHANNEL_DATA *Channel,  int freqType )
{
	Channel->freqTableType = freqType;
}

int  getFreqType( CHANNEL_DATA *Channel )
{
	return Channel->freqTableType;
}

void  setOutputFormat( CHANNEL_DATA *Channel,  int format )
{
	Channel->outputFormat = format;
}

int   getOutputFormat( CHANNEL_DATA *Channel )
{
	return Channel->outputFormat;
}

void  setCountryCode( CHANNEL_DATA *Channel,  int CountryCode )
{
	Channel->countryCode = CountryCode;
}

int   getCountryCode( CHANNEL_DATA *Channel )
{
	return Channel->countryCode;
}

void  setCountryRegion( CHANNEL_DATA *Channel,  char* Country )
{
	strncpy( Channel->countryRegion, Country, sizeof(Channel->countryRegion) );
}

char*   getCountryRegion( CHANNEL_DATA *Channel )
{
	return Channel->countryRegion;
}

void  setTunerMode( CHANNEL_DATA *Channel,  char* tunerMode )
{
	strncpy( Channel->tunerMode, tunerMode, sizeof( Channel->tunerMode ) );
}

char* getTunerMode( CHANNEL_DATA *Channel )
{
	return Channel->tunerMode;
}

void  parserDelayTime( CHANNEL_DATA *Channel, uint32_t ms )
{
	Channel->delayParse = ms;
}

void enableFrqTableUpdate( CHANNEL_DATA *Channel )
{
	Channel->frqTableUpdateCheck = 1;
}

void setQAMScheme(  CHANNEL_DATA *Channel, int QAMScheme )
{
	Channel->guessFreqTableType = QAMScheme;
	Channel->freqTableType = QAMScheme;
}

void setFrqFilePath( CHANNEL_DATA *Channel, char* FileLocation )
{
	int len = strlen( FileLocation );
	if ( len == 0 )
	{
		 Channel->frqTableFilePath[0] = 0x0;
		 return;
	}
	
	if ( len > sizeof(Channel->frqTableFilePath)-2 )
	 len = sizeof(Channel->frqTableFilePath)-2;
	strncpy( Channel->frqTableFilePath, FileLocation, len );
#ifdef  WIN32 
	if ( Channel->frqTableFilePath[len-1] != '\\' )
	{
		Channel->frqTableFilePath[len++] = '\\'; 
		Channel->frqTableFilePath[len] = 0x0;
	}
#else
	if ( Channel->frqTableFilePath[len-1] != '/' )
	{
		Channel->frqTableFilePath[len++] = '/';
		Channel->frqTableFilePath[len] = 0x0;
	}
#endif	
}

static void initUseDefinedQAMFrq(  int freqTableType, int shift )
{
	struct bcast *freqTbl;
	int i=0;
	switch ( freqTableType )
	{
		case ATSC_FREQ_SCHEME: freqTbl = ATSC_BRCAST; break;
		case NTSC_FREQ_SCHEME: freqTbl = NTSC_CABLE;  break;
		case HRC_FREQ_SCHEME : freqTbl = NTSC_HRC;    break;
		default:
			freqTbl = NTSC_CABLE; break;
	}
	while ( freqTbl[i].fq != 0 && i<MAX_USER_DEFINED_QAM ) 
	{
		user_defined_qam_tbl[i].ch = freqTbl[i].ch;
		if ( shift == 1 )
			user_defined_qam_tbl[i].fq = (freqTbl[i].fq + 1750) * 1000;
		else
			user_defined_qam_tbl[i].fq = (freqTbl[i].fq) * 1000;
		user_defined_qam_tbl[i].mod = 0;
		user_defined_qam_tbl[i].inv = 0;
		i++;
	}
}

static void updateUseDefinedQAMFrq( int channel, uint32_t frequency, int mod, int inv )
{
	struct bcast_qam *freqTbl;
	int i = 0;
	freqTbl = user_defined_qam_tbl;

	while ( freqTbl[i].fq != 0 && i<MAX_USER_DEFINED_QAM ) 
	{
		if ( freqTbl[i].ch == channel ) 
		{
			freqTbl[i].ch = channel;
			freqTbl[i].fq = frequency;
			freqTbl[i].inv = inv;
			freqTbl[i].mod = mod;
			break;
		}
		i++;
	}
}

static struct bcast_qam * getUseDefinedQAMFrq( int channel )
{
	struct bcast_qam *freqTbl;
	int i = 0;
	freqTbl = user_defined_qam_tbl;

	while ( freqTbl[i].fq != 0 && i<MAX_USER_DEFINED_QAM ) 
	{
		if ( freqTbl[i].ch == channel ) 
		{
			return &freqTbl[i];
		}
		i++;
	}

	return NULL;
}


uint32_t getChannelFreq( int physical_ch, int freqTableType, int shift )
{
	struct bcast *freqTbl;
	uint32_t freq=0;
	int i = 0;
	
	switch ( freqTableType )
	{
		case ATSC_FREQ_SCHEME: freqTbl = ATSC_BRCAST; break;
		case NTSC_FREQ_SCHEME: freqTbl = NTSC_CABLE;  break;
		case HRC_FREQ_SCHEME : freqTbl = NTSC_HRC;    break;
		default:
			freqTbl = NTSC_CABLE; break;
	}
	while ( freqTbl[i].fq != 0 && i<256 ) 
	{
		if ( freqTbl[i].ch == physical_ch ) 
		{
			/* if ( freqTableType == NTSC_HRC_FREQ_TYPE )
			{
				if ( physical_ch == 1 )
					freq = freqTbl[i].fq * 1000;
				else
				if ( physical_ch == 5 || physical_ch == 6 )
					freq = (freqTbl[i].fq - 1250) *1000 + (physical_ch+8)*300 + 2;
				else
					freq = (freqTbl[i].fq - 1250) *1000 + (physical_ch+8)*300 ;
				
			} else */
			{
				freq = (freqTbl[i].fq);
				if ( shift == 1 ) freq += 1750;
				freq *= 1000; 
			}
			break;
		}
		i++;
	}

	if ( freq == 0 )
	{
		if ( physical_ch > LAST_NTSC_HRC_CHANNEL && physical_ch > LAST_NTSC_CABLE_CHANNEL )
			return 0;
		else
			return 0xffffffff;
	}

	return freq;
}

void* getTuningEntry(  CHANNEL_DATA *Channel, int tune_type, int major, int minor, int physical, int index )
{
	int i; 
	SageLog(( _LOG_TRACE, 3, "DVB:Get tuning Entry :(type:%d) major:%d  minor:%d  physical:%d (index:%d)\r\n",
	       								tune_type, major, minor, physical, index ));
									
	if ( !strncmp( Channel->sourceType, "ATSC", 4 ) )
	{
		switch ( tune_type ) {
		case 1:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
				if ( Channel->FreqTable.tbl.atsc[ i ].index == physical )
					return &Channel->FreqTable.tbl.atsc[ i ];
			return NULL;
		
		case 2:
		case 3:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
			{
				if ( major == 0 )
				{
					if ( Channel->FreqTable.tbl.atsc[i].major == major 
						&& Channel->FreqTable.tbl.atsc[i].minor == minor 
						&& Channel->FreqTable.tbl.atsc[i].physical_ch == physical )
					{
						return &Channel->FreqTable.tbl.atsc[ i ];
					}
				} else
				{
					if ( Channel->FreqTable.tbl.atsc[i].major == major && Channel->FreqTable.tbl.atsc[i].minor == minor )
					{
						return &Channel->FreqTable.tbl.atsc[ i ];
					}
				}
			}
			SageLog(( _LOG_TRACE, 3, "DVB: ATSC Tuning Entry not found.\r\n" ));
			return NULL;
		
		

		case 4:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
			{
				if ( Channel->FreqTable.tbl.atsc[i].major == major && Channel->FreqTable.tbl.atsc[i].minor == minor )
				{
					return &Channel->FreqTable.tbl.atsc[ i ];
				}
			}
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
				if ( Channel->FreqTable.tbl.atsc[ i ].index == index )
					return &Channel->FreqTable.tbl.atsc[ i ];
			
			SageLog(( _LOG_TRACE, 3, "DVB:ATSC Tuning Entry not found.\r\n" ));
			return NULL; 
		}
		
	} else
	if ( !strncmp( Channel->sourceType, "QAM", 3 ) )
	{
		switch ( tune_type ) {
		case 1:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
				if ( Channel->FreqTable.tbl.qam[ i ].index ==  physical )
					return &Channel->FreqTable.tbl.qam[ i ];
				
			SageLog(( _LOG_TRACE, 3, "DVB:QAM Tuning Entry not found (1: entry:%d).\r\n", Channel->FreqTable.entryNum ));
			return NULL;
		
		case 2:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
			{
				if ( Channel->FreqTable.tbl.qam[i].major == major && Channel->FreqTable.tbl.qam[i].minor == minor )
				{
					return &Channel->FreqTable.tbl.qam[ i ];
				}
			}
			
			SageLog(( _LOG_TRACE, 3, "DVB:QAM Tuning Entry not found (3: entry:%d).\r\n", Channel->FreqTable.entryNum ));
			return NULL;

		case 3:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
			{
				if ( Channel->FreqTable.tbl.qam[i].physical_ch == physical &&
				     Channel->FreqTable.tbl.qam[i].major == major && Channel->FreqTable.tbl.qam[i].minor == minor )
				{
					return &Channel->FreqTable.tbl.qam[ i ];
				}
			}
			
			SageLog(( _LOG_TRACE, 3, "DVB:QAM Tuning Entry not found (3: entry:%d).\r\n", Channel->FreqTable.entryNum ));
			return NULL;
		
		

		case 4:
			{
				int first_found_index = -1;
				for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
				{
					if (  Channel->FreqTable.tbl.qam[i].physical_ch == physical &&
						Channel->FreqTable.tbl.qam[i].major == major && Channel->FreqTable.tbl.qam[i].minor == minor )
					{
						if ( first_found_index < 0 )
							first_found_index = i;
						if ( i == index )
							return &Channel->FreqTable.tbl.qam[ i ];
					}
				}
				if ( first_found_index >= 0 )
					return &Channel->FreqTable.tbl.qam[ first_found_index ];

				for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
					if ( Channel->FreqTable.tbl.qam[ i ].index == index )
						return &Channel->FreqTable.tbl.qam[ i ];
			}
			SageLog(( _LOG_TRACE, 3, "DVB: QAM Tuning Entry not found(4).\r\n" ));
			return NULL;
		}
	
	} else
	if ( !strncmp( Channel->sourceType, "DVB-T", 5 ) )
	{
		uint16_t onid, tsid, sid;
		onid = physical; tsid = major; sid = minor;
		switch ( tune_type ) {
		case 1:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
				if ( Channel->FreqTable.tbl.dvbt[ i ].index == physical )
					return &Channel->FreqTable.tbl.dvbt[ i ];
			SageLog(( _LOG_TRACE, 3, "DVB: DVB-T Tuning Entry not found.\r\n" ));
			return NULL;
		case 3:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
			{
				if ( Channel->FreqTable.tbl.dvbt[i].onid == onid && 
				     Channel->FreqTable.tbl.dvbt[i].tsid == tsid && 
				     Channel->FreqTable.tbl.dvbt[i].sid == sid )
				{
					return &Channel->FreqTable.tbl.dvbt[ i ];
				}
			}
			SageLog(( _LOG_TRACE, 3, "DVB: DVB-T Tuning Entry not found.\r\n" ));
			return NULL;
		case 4:
			{
				int first_found_index = -1;
				for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
				{
					if ( Channel->FreqTable.tbl.dvbt[i].onid == onid && 
						 Channel->FreqTable.tbl.dvbt[i].tsid == tsid && 
						 Channel->FreqTable.tbl.dvbt[i].sid == sid )
					{
						if ( first_found_index < 0 )
							first_found_index = i;
						if ( i == index )
							return &Channel->FreqTable.tbl.dvbt[ i ];
					}
				}
				if ( first_found_index >= 0 )
					return &Channel->FreqTable.tbl.dvbt[ first_found_index ];

				for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
					if ( Channel->FreqTable.tbl.dvbt[ i ].index == index )
						return &Channel->FreqTable.tbl.dvbt[ i ];
			}			
			SageLog(( _LOG_TRACE, 3, "DVB: DVB-T Tuning Entry not found (%d, %d).\r\n", Channel->FreqTable.entryNum, index ));
			return NULL;
		}

		
	} else
	if ( !strncmp( Channel->sourceType, "DVB-C", 5 ) )
	{
		uint16_t onid, tsid, sid;
		onid = physical; tsid = major; sid = minor;
		switch ( tune_type ) {
		case 1:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
				if ( Channel->FreqTable.tbl.dvbc[ i ].index == physical )
					return &Channel->FreqTable.tbl.dvbc[ i ];
			SageLog(( _LOG_TRACE, 3, "DVB: DVB-C Tuning Entry not found.\r\n" ));
			return NULL;
		case 3:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
			{
				if ( Channel->FreqTable.tbl.dvbc[i].onid == onid && 
				     Channel->FreqTable.tbl.dvbc[i].tsid == tsid && 
				     Channel->FreqTable.tbl.dvbc[i].sid == sid )
				{
					return &Channel->FreqTable.tbl.dvbc[ i ];
				}
			}
			SageLog(( _LOG_TRACE, 3, "DVB: DVB-C Tuning Entry not found.\r\n" ));
			return NULL;
		case 4:
			{
				int first_found_index = -1;
				for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
				{
					if ( Channel->FreqTable.tbl.dvbc[i].onid == onid && 
						 Channel->FreqTable.tbl.dvbc[i].tsid == tsid && 
						 Channel->FreqTable.tbl.dvbc[i].sid == sid )
					{
						if ( first_found_index < 0 )
							first_found_index = i;
						if ( i == index )
							return &Channel->FreqTable.tbl.dvbc[ i ];
					}
				}
				if ( first_found_index >= 0 )
					return &Channel->FreqTable.tbl.dvbc[ first_found_index ];

				for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
					if ( Channel->FreqTable.tbl.dvbc[ i ].index == index )
						return &Channel->FreqTable.tbl.dvbc[ i ];
			}
			SageLog(( _LOG_TRACE, 3, "DVB: DVB-C Tuning Entry not found (%d, %d).\r\n", Channel->FreqTable.entryNum, index ));
			return NULL;
		}
	} else
	if ( !strncmp( Channel->sourceType, "DVB-S", 5) )
	{
		uint16_t onid, tsid, sid;
		onid = physical; tsid = major; sid = minor;
		switch ( tune_type ) {
		case 1:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
				if ( Channel->FreqTable.tbl.dvbs[ i ].index == physical )
					return &Channel->FreqTable.tbl.dvbs[ i ];
			SageLog(( _LOG_TRACE, 3, "DVB: DVB-S Tuning Entry not found.\r\n" ));
			return NULL;
		case 3:
			for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
			{
				if ( Channel->FreqTable.tbl.dvbs[i].onid == onid && 
				     Channel->FreqTable.tbl.dvbs[i].tsid == tsid && 
				     Channel->FreqTable.tbl.dvbs[i].sid == sid )
				{
					return &Channel->FreqTable.tbl.dvbs[ i ];
				}
			}
			SageLog(( _LOG_TRACE, 3, "DVB: DVB-S Tuning Entry not found.\r\n" ));
			return NULL;
		case 4:
			{
				int first_found_index = -1;
				for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
				{
					if ( Channel->FreqTable.tbl.dvbs[i].onid == onid && 
						 Channel->FreqTable.tbl.dvbs[i].tsid == tsid && 
						 Channel->FreqTable.tbl.dvbs[i].sid == sid )
					{
						if ( first_found_index < 0 )
							first_found_index = i;
						if ( i == index )
							return &Channel->FreqTable.tbl.dvbs[ i ];
					}
				}
				if ( first_found_index >= 0 )
					return &Channel->FreqTable.tbl.dvbs[ first_found_index ];
				for ( i = 0; i<Channel->FreqTable.entryNum; i++ )
					if ( Channel->FreqTable.tbl.dvbs[ i ].index == index )
						return &Channel->FreqTable.tbl.dvbs[ i ];
			}		
			SageLog(( _LOG_TRACE, 3, "DVB: DVB-S Tuning Entry not found (%d, %d).\r\n", Channel->FreqTable.entryNum, index ));
			return NULL;
		}
	} 
	SageLog(( _LOG_TRACE, 3, "DVB:Tuning Entry not found.\r\n" ));
	
	return NULL;
	
}
uint32_t fileTimeStamp( char* szFileName );
bool tuneChannel( CHANNEL_DATA *channel, const char* tune_string )
{
	//int status;
	const char *major_p, *minor_p, *index_p;
	int physical_ch = 0, major_ch = -1, minor_ch  = -1, index_ch = -1, program = -1;
	int tune_string_type = 1;  //0: "x" index type; 1: "xx-yy" channel type; 2: "xx-yy-cc" full string; 4:xx-yy-cc-ind; 15:xx*yy
	bool skip_tune = false;
	//bool locked = false;
	
	//DShowCaptureInfo *CDev = (DShowCaptureInfo *)Capture;
	
	if (  tune_string == NULL ) 
		return false;

				
	physical_ch = atoi( tune_string );
	if ( physical_ch < 0 )
		return false;

	major_p = strpbrk( tune_string, "-.*" );
	if ( major_p != NULL && *major_p == '*' )   //give ATSC/QAM a short cut to tune with "phy*program" on PVR
	{
		program = atoi( ++major_p );
		tune_string_type = 15;
	} else
	if ( major_p != NULL )
	{
		major_p++;
		major_ch = atoi( major_p );
		minor_p = strpbrk( major_p, "-." );
		if ( minor_p != NULL )
		{
			minor_p++;
			minor_ch = atoi( minor_p ); 
			index_p = strpbrk( minor_p, "-." );
			if ( index_p != NULL )
			{
				index_p++;
				index_ch = atoi( index_p ); 
				tune_string_type = 4;		//format: onid-tsid-sid-index   "48-50-1-12" (DVB-C/DVB-T)
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
	
	SageLog(( _LOG_TRACE, 3, "Tune channel (%s) %d-%d-%d on '%s'. <<=======\r\n",
		                         tune_string, physical_ch, major_ch, minor_ch, SageGetTunerDeviceName(channel->Dev)));
	SageStopParser( channel->Dev );
	SageSelectOuputFormat( channel->Dev, channel->outputFormat ); //0:TS; 1:PS

	if ( channel->frqTableUpdateCheck )
	{
		if ( channel->frqTableTimeStamp && channel->frqTableTimeStamp < fileTimeStamp( channel->frqTableFileName ) )
			loadScanTuningTable( channel );
	}
	
	if ( !strncmp( channel->sourceType, "ATSC", 4 ) )
	{
		ATSC_FREQ *ATSCFreq;
		TUNE tune={0};
		tune.stream_format = ATSC_STREAM;
		tune.sub_format = TERRESTRIAL;

		ATSCFreq = (ATSC_FREQ *)getTuningEntry( channel,  tune_string_type, major_ch, minor_ch, physical_ch, index_ch );

		if ( ATSCFreq == NULL )
		{
			ATSC_FREQ newATSCFreq={0};

			// if its a invalid channel number, always set to channel "1", as a bad frequency makes Happauge tuner stop working on Win7
			if (physical_ch <= 0) physical_ch = 2;

			newATSCFreq.physical_ch = physical_ch;

			newATSCFreq.frequency = getChannelFreq( physical_ch, ATSC_FREQ_SCHEME, 0 );

			skip_tune = false;
			SageLog(( _LOG_TRACE, 3, "Tune ATSC physical channel %d\r\n", physical_ch ));
			
			if ( SageTuneATSCChannel( channel->Dev, &newATSCFreq, skip_tune ) < 0 )  
			{
				SageLog(( _LOG_TRACE, 3, "Tuning ATSC channel '%d' failed.\r\n", physical_ch ));
				return false;
			}
			channel->lastTune = physical_ch;
			SageDelay( channel->Dev, channel->delayParse ); //in million second;

			tune.u.atsc.physical_ch = physical_ch;
			if ( tune_string_type == 15 )
			{
				tune.tune_string_type = 15;
				tune.u.atsc.program_id = program;
			} else
			if ( tune_string_type == 1 || tune_string_type == 2 ) //not specify major and minor
			{
				tune.tune_string_type = 0;
				tune.channel = minor_ch <= 0 ?  1 :  minor_ch ;
			}
			else
			{
				tune.tune_string_type = 2;
				tune.u.atsc.major_num = major_ch;
				tune.u.atsc.minor_num = minor_ch;
			}
			SageTVLockTSChannel( channel->Dev, &tune, 0 );
		}		
		else
		{
			int ch = ATSCFreq->physical_ch;
			skip_tune = channel->lastTune == ATSCFreq->physical_ch;
			skip_tune = false; //for HDHR
			
			SageLog(( _LOG_TRACE, 3, "ATSC Freq entry: index:%d major:%d minor:%d prog:%d channel:%d ctr:%d (skip:%d) name:%s.\r\n",
			                     ATSCFreq->index, ATSCFreq->major, ATSCFreq->minor, ATSCFreq->program, 
				                 ATSCFreq->physical_ch, ATSCFreq->ctrl, skip_tune, ATSCFreq->name ));
			
			if ( SageTuneATSCChannel( channel->Dev, ATSCFreq, skip_tune ) < 0 )  
			{
				SageLog(( _LOG_TRACE, 3, "Tuning ATSC channel '%d' %s (ch:%d) failed.\r\n", ch, ATSCFreq->name, ATSCFreq->index ));
				return false;
			}
			channel->lastTune = ATSCFreq->physical_ch;
			if ( !skip_tune )
			{
				SageDelay( channel->Dev, channel->delayParse ); //in million second;
			}

			tune.tune_string_type = 3;
			tune.u.atsc.physical_ch = ATSCFreq->physical_ch;
			tune.u.atsc.major_num   = ATSCFreq->major;
			tune.u.atsc.minor_num   = ATSCFreq->minor;
			tune.u.atsc.program_id  = ATSCFreq->program;

			SageTVLockTSChannel( channel->Dev, &tune, ATSCFreq->ctrl );

		}
	
	} else
	if ( !strncmp( channel->sourceType, "QAM", 3 ) )
	{
		QAM_FREQ *QAMFreq;
		TUNE tune={0};
		tune.stream_format = ATSC_STREAM;
		tune.sub_format = CABLE;
		QAMFreq = (QAM_FREQ *)getTuningEntry( channel,  tune_string_type, major_ch, minor_ch, physical_ch, index_ch );

		if ( QAMFreq == NULL )
		{
			QAM_FREQ newQam={0};

			// if its a invalid channel number, always set to channel "1", as a bad frequency makes Happauge tuner stop working on Win7
			if (physical_ch <= 0) 
				physical_ch = 1;

			skip_tune = false;
			if ( channel->freqScheme > 0 ) 
				newQam.frequency = getChannelFreq( physical_ch, channel->freqScheme, channel->freqShift );
			else
				newQam.frequency = getChannelFreq( physical_ch, channel->freqTableType, channel->freqShift );
			newQam.physical_ch = physical_ch;
			SageLog(( _LOG_TRACE, 3, "Tune QAM standard channel %d freq:%d (%d-%d)\r\n", physical_ch, newQam.frequency,
				                                                   channel->freqScheme, channel->freqTableType ));
			if ( SageTuneQAMFrequency( channel->Dev, &newQam, skip_tune ) < 0 )  
			{
				SageLog(( _LOG_TRACE, 3, "Tuning QAM channel %d (freq %d QAM256) failed.\r\n", physical_ch, newQam.frequency ));
				return false;
			}
			channel->lastTune = physical_ch;

			tune.u.atsc.physical_ch = physical_ch;
			tune.u.atsc.u.atsc.freq = newQam.frequency;
			if ( tune_string_type == 15 )
			{
				tune.tune_string_type = 15;
				tune.u.atsc.program_id = program;
			} else			
			if ( tune_string_type == 1 || tune_string_type == 2 ) //not specify major and minor
			{
				tune.tune_string_type = 0;
				tune.channel = minor_ch <= 0 ?  1 :  minor_ch ;
			}
			else
			{
				tune.tune_string_type = 2;
				tune.u.atsc.major_num = major_ch;
				tune.u.atsc.minor_num = minor_ch;
			}
			SageTVLockTSChannel( channel->Dev, &tune, 0 );
		}
		else
		{
			int ch;
			uint32_t freq; 

			//skip_tune = channel->lastTune == QAMFreq->physical_ch;
			skip_tune = false;
			if ( QAMFreq->modulation == 0 ) QAMFreq->modulation = 11;  //QAM256
			SageLog(( _LOG_TRACE, 3, "QAM Freq entry: index:%d major:%d minor:%d prog:%d phy:%d mod:%d inv:%d ctr:%d (skip:%d).\r\n", QAMFreq->index,
				    QAMFreq->major, QAMFreq->minor, QAMFreq->program, QAMFreq->physical_ch, QAMFreq->modulation, QAMFreq->inversal,
					QAMFreq->ctrl, skip_tune ));
			ch = QAMFreq->physical_ch;
			freq = QAMFreq->frequency;
			
			if (  SageTuneQAMFrequency( channel->Dev, QAMFreq, skip_tune ) < 0 )
			{	
				QAM_FREQ newQam={0};
				SageLog(( _LOG_TRACE, 3, "Tuning QAM Frquency (freq:%d) failed, try tuning default frequency based on channel\r\n", freq ));
				newQam.frequency = getChannelFreq( physical_ch, channel->freqTableType, channel->freqShift );
				if ( (freq = SageTuneQAMFrequency( channel->Dev, &newQam, skip_tune )) < 0 )  
				{
					SageLog(( _LOG_TRACE, 3, "Tuning QAM channel '%d' %s (ch:%d) failed.\r\n", channel, QAMFreq->name, QAMFreq->index ));
					return false;
				} else
					channel->lastTune = ch;
			} else
			{
				channel->lastTune = freq;
			}

			if ( !skip_tune )
			{
				SageDelay( channel->Dev, channel->delayParse ); //in million second;
			}
			tune.tune_string_type = 3;
			tune.u.atsc.u.atsc.freq = QAMFreq->frequency;
			tune.u.atsc.physical_ch = QAMFreq->physical_ch;;
			tune.u.atsc.major_num   = QAMFreq->major;
			tune.u.atsc.minor_num   = QAMFreq->minor;
			tune.u.atsc.program_id  = QAMFreq->program;
			if ( channel->tuneCtrl == 1 )
				tune.u.atsc.major_num = 0;  //disable using PSI to lock a channel

			SageTVLockTSChannel( channel->Dev, &tune, QAMFreq->ctrl );

		}

	} else
	if ( !strncmp( channel->sourceType, "DVB-T", 5 ) )
	{
		DVB_T_FREQ *DVBTFreq;
		TUNE tune={0};
		tune.stream_format = DVB_STREAM;
		tune.sub_format = TERRESTRIAL;

		DVBTFreq = (DVB_T_FREQ *)getTuningEntry( channel,  tune_string_type, major_ch, minor_ch, physical_ch, index_ch );
		if ( DVBTFreq == NULL )
		{
			DVB_T_FREQ newDVBTFreq;
			if ( tune_string_type < 3 )
			{
				SageLog(( _LOG_TRACE, 3, "Invalid tuning for Tuning DVB-T tune '%d'.\r\n", physical_ch ));
			}
			
			newDVBTFreq.frequency = 802000000;
			newDVBTFreq.bandwidth = 8;
			newDVBTFreq.code_rate_hp = 0xff;
			newDVBTFreq.code_rate_lp = 0xff;
			newDVBTFreq.trans_mode = 0xff;
			newDVBTFreq.guard_interval = 0xff;
			newDVBTFreq.hierarchy = 0xff;
			newDVBTFreq.constellation = 0xff;
			
			//Austrila's bandwidth is 7MHz (country code:61)
			if ( channel->countryCode == 61 ) newDVBTFreq.bandwidth = 7;
			
			SageLog(( _LOG_TRACE, 3, "Tune DVB-T freq:%d bandwith \r\n", physical_ch, newDVBTFreq.bandwidth ));
			
			skip_tune = false;
			if ( SageTuneDVBTFrequency( channel->Dev, &newDVBTFreq, skip_tune ) < 0 )  
			{
				SageLog(( _LOG_TRACE, 3, "Tuning DVB-T channel '%d' failed.\r\n", physical_ch ));
				return false;
			}
			channel->lastTune = physical_ch;
			
			tune.u.dvb.dvb_type = TERRESTRIAL;
			tune.u.dvb.dvb.t.freq = newDVBTFreq.frequency;
			tune.u.dvb.onid = physical_ch;
			tune.u.dvb.tsid = major_ch;
			tune.u.dvb.sid  = minor_ch;
			tune.tune_string_type = 3;

			if ( tune_string_type == 1 || tune_string_type == 2 ) //not specify major and minor
			{
				tune.tune_string_type = 0;
				tune.channel = 1;
			}
			SageTVLockTSChannel( channel->Dev, &tune, 0 );

		}
		else
		{
			int ch;
			uint32_t freq;
			skip_tune = false;
			SageLog(( _LOG_TRACE, 3, "DVB-T Freq entry: index:%d onid:%d tsid:%d sid:%d frq:%d ctr:%d (skip:%d) name:%s.\r\n", DVBTFreq->index,
			        DVBTFreq->onid, DVBTFreq->tsid, DVBTFreq->sid, DVBTFreq->frequency, DVBTFreq->ctrl, skip_tune, DVBTFreq->name ));
			ch = DVBTFreq->physical_ch;
			freq = DVBTFreq->frequency;
			
			if ( SageTuneDVBTFrequency( channel->Dev, DVBTFreq, skip_tune ) < 0 )
			{	
				SageLog(( _LOG_TRACE, 3, "Tuning DVB-T channel '%d' %s (freq:%d) failed.\r\n", ch, DVBTFreq->name, freq ));
			} else
			{
				channel->lastTune = DVBTFreq->frequency;
			}
			
			SageLog(( _LOG_TRACE, 3, "Tuning DVB-T channel '%d' %s (freq:%ld) successed.\r\n",
			                                        DVBTFreq->index, DVBTFreq->name, freq ));
			
			tune.tune_string_type = 3;
			tune.u.dvb.dvb_type = TERRESTRIAL;
			tune.u.dvb.dvb.t.freq = DVBTFreq->frequency;
			tune.u.dvb.onid = DVBTFreq->onid;
			tune.u.dvb.tsid = DVBTFreq->tsid;
			tune.u.dvb.sid  = DVBTFreq->sid;
			SageTVLockTSChannel( channel->Dev, &tune, DVBTFreq->ctrl );

			
		}
	
	} else
	if ( !strncmp( channel->sourceType, "DVB-C", 5 ) )
	{
		DVB_C_FREQ *DVBCFreq;
		TUNE tune={0};
		tune.stream_format = DVB_STREAM;
		tune.sub_format = CABLE;
		DVBCFreq = (DVB_C_FREQ *)getTuningEntry( channel,  tune_string_type, major_ch, minor_ch, physical_ch, index_ch );
		if ( DVBCFreq == NULL )
		{
			DVB_C_FREQ newDVBCFreq;
			if ( tune_string_type < 3 || physical_ch )
			{
				SageLog(( _LOG_TRACE, 3, "Invalid tuning for Tuning DVB-C tune '%d'.\r\n", physical_ch ));
			}
			
			newDVBCFreq.frequency = 156500000;
			newDVBCFreq.symbol_rate = 0;
			newDVBCFreq.fec_inner = 0xff;
			newDVBCFreq.fec_inner_rate = 0xff;
			newDVBCFreq.fec_outer = 0xff;
			newDVBCFreq.fec_outer_rate = 0xff;
			newDVBCFreq.modulation = 11;
			
			SageLog(( _LOG_TRACE, 3, "Tune DVB-C freq:%d \r\n", physical_ch ));
			
			skip_tune = false;
			if ( SageTuneDVBCFrequency( channel->Dev, &newDVBCFreq, skip_tune ) < 0 )  
			{
				SageLog(( _LOG_TRACE, 3, "Tuning DVB-C channel '%d' failed.\r\n", physical_ch ));
				return false;
			}
			channel->lastTune = physical_ch;
			
			tune.u.dvb.dvb_type = CABLE;
			tune.u.dvb.dvb.c.freq = newDVBCFreq.frequency;
			tune.u.dvb.onid = physical_ch;
			tune.u.dvb.tsid = major_ch;
			tune.u.dvb.sid  = minor_ch;
			tune.tune_string_type = 3;

			if ( tune_string_type == 1 || tune_string_type == 2 ) //not specify major and minor
			{
				tune.tune_string_type = 0;
				tune.channel = 1;
			}
			SageTVLockTSChannel( channel->Dev, &tune, 0 );

				
		}
		else
		{
			int ch;
			uint32_t freq;
			skip_tune = false;
			SageLog(( _LOG_TRACE, 3, "DVB-C Freq entry: index:%d onid:%d tsid:%d sid:%d frq:%d ctr:%d (skip:%d) name:%s.\r\n", DVBCFreq->index,
			        DVBCFreq->onid, DVBCFreq->tsid, DVBCFreq->sid, DVBCFreq->frequency, DVBCFreq->ctrl, skip_tune, DVBCFreq->name ));
			ch = DVBCFreq->physical_ch;
			freq = DVBCFreq->frequency;
			
			if ( SageTuneDVBCFrequency( channel->Dev, DVBCFreq, skip_tune ) < 0 )
			{	
				SageLog(( _LOG_TRACE, 3, "Tuning DVB-C channel '%d' %s (freq:%d) failed.\r\n", ch, DVBCFreq->name, freq ));
				return false;
			} else
			{
				channel->lastTune = DVBCFreq->frequency;
			}
			
			SageLog(( _LOG_TRACE, 3, "Tuning DVB-C channel '%d' %s (freq:%ld) successed.\r\n",
			                                        DVBCFreq->index, DVBCFreq->name, freq ));
			
			tune.tune_string_type = 3;
			tune.u.dvb.dvb_type = CABLE;
			tune.u.dvb.dvb.c.freq = DVBCFreq->frequency;
			tune.u.dvb.onid = DVBCFreq->onid;
			tune.u.dvb.tsid = DVBCFreq->tsid;
			tune.u.dvb.sid  = DVBCFreq->sid;
			SageTVLockTSChannel( channel->Dev, &tune, DVBCFreq->ctrl );
			
		}
	
	} else
	if ( !strncmp( channel->sourceType, "DVB-S", 5 ) )
	{
		DVB_S_FREQ *DVBSFreq;
		TUNE tune={0};
		tune.stream_format = DVB_STREAM;
		tune.sub_format = SATELLITE;

		DVBSFreq = (DVB_S_FREQ *)getTuningEntry( channel,  tune_string_type, major_ch, minor_ch, physical_ch, index_ch );
		if ( DVBSFreq == NULL )
		{
			DVB_S_FREQ newDVBSFreq;
			if ( tune_string_type < 3 || physical_ch == 0)
			{
				SageLog(( _LOG_TRACE, 3, "Invalid tuning for Tuning DVB-S tune '%d'.\r\n", physical_ch ));
			}
			
			newDVBSFreq.frequency = 156500000;
			newDVBSFreq.symbol_rate = 0;
			newDVBSFreq.modulation = 0xff;
			newDVBSFreq.fec_inner = 0xff;
			newDVBSFreq.fec_inner_rate = 0xff;
			newDVBSFreq.fec_outer = 0xff;
			newDVBSFreq.fec_outer_rate = 0xff;
			
			SageLog(( _LOG_TRACE, 3, "Tune DVB-S freq:%d \r\n", physical_ch ));
			
			skip_tune = false;
			if ( SageTuneDVBSFrequency( channel->Dev, &newDVBSFreq, skip_tune ) < 0 )  
			{
				SageLog(( _LOG_TRACE, 3, "Tuning DVB-S channel '%d' failed.\r\n", physical_ch ));
				return false;
			}
			channel->lastTune = physical_ch;
			
			tune.u.dvb.dvb_type = SATELLITE;
			tune.u.dvb.dvb.s.freq = newDVBSFreq.frequency;
			tune.u.dvb.onid = physical_ch;
			tune.u.dvb.tsid = major_ch;
			tune.u.dvb.sid  = minor_ch;
			tune.tune_string_type = 3;

			if ( tune_string_type == 1 || tune_string_type == 2 ) 
			{
				tune.tune_string_type = 0;
				tune.channel = 1;
			}

			SageTVLockTSChannel( channel->Dev, &tune, 0 );

		}
		else
		{
			int ch;
			uint32_t freq;
			skip_tune = false;
			ch = DVBSFreq->physical_ch;
			freq = DVBSFreq->frequency;
			//skip_tune = ( freq == channel->lastTune );  //skip tuning the same frequency, testing
			SageLog(( _LOG_TRACE, 3, "DVB-S Freq entry: index:%d onid:%d tsid:%d sid:%d frq:%d ctr:%d (skip:%d) v_pid:%d a_pid:%d name:%s.\r\n", DVBSFreq->index,
				DVBSFreq->onid, DVBSFreq->tsid, DVBSFreq->sid, DVBSFreq->frequency, DVBSFreq->ctrl, skip_tune, 
				DVBSFreq->video_pid, DVBSFreq->audio_pid, DVBSFreq->name ));

			if ( SageTuneDVBSFrequency( channel->Dev, DVBSFreq, skip_tune ) < 0 )
			{	
				SageLog(( _LOG_TRACE, 3, "Tuning DVB-S channel '%d' %s (freq:%d) failed.\r\n", ch, DVBSFreq->name, freq ));
				return false;
			} else
			{
				channel->lastTune = freq;
			}
			
			SageLog(( _LOG_TRACE, 3, "Tuning DVB-S channel '%d' %s (freq:%ld) successed.\r\n",
			                                        DVBSFreq->index, DVBSFreq->name, freq ));
			
			tune.tune_string_type = 3;
			tune.u.dvb.dvb_type = SATELLITE;
			tune.u.dvb.dvb.s.freq = DVBSFreq->frequency;
			tune.u.dvb.onid = DVBSFreq->onid;
			tune.u.dvb.tsid = DVBSFreq->tsid;
			tune.u.dvb.sid  = DVBSFreq->sid;
			if ( ( DVBSFreq->ctrl & 0x10 ) && DVBSFreq )
			{
				tune.pids_table.pid_num = 2;
				tune.pids_table.pid_tbl[0].pid = DVBSFreq->video_pid;
				tune.pids_table.pid_tbl[0].type = 0xf1; //UNKNOW_VIDEO_STREAM_TYPE
				tune.pids_table.pid_tbl[1].pid = DVBSFreq->audio_pid;
				tune.pids_table.pid_tbl[1].type = 0xf2; //UNKNOW_AUDIO_STREAM_TYPE
			}
			SageTVLockTSChannel( channel->Dev, &tune, DVBSFreq->ctrl );
		}
	
	} else
	{
	
		SageLog(( _LOG_TRACE, 3, "unknown source %s\r\n", channel->sourceType ));
	}

	SageStartParser( channel->Dev );
	return true;
}

uint16_t getProgramNum( void* pTune )
{
	if ( ((TUNE*)pTune)->stream_format == DVB_STREAM )
	{
		if ( ((TUNE*)pTune)->tune_string_type >= 3 )
			return ((TUNE*)pTune)->u.dvb.sid;
		else
			return 0;
	} else
	if ( ((TUNE*)pTune)->stream_format == ATSC_STREAM )
	{
		if ( ((TUNE*)pTune)->tune_string_type >= 3 )
			return ((TUNE*)pTune)->u.atsc.program_id;
		else return 0;
	} 

	return 0;
}

uint16_t getCtrlFlag( void* pTune )
{
	if ( ((TUNE*)pTune)->stream_format == DVB_STREAM )
	{
		if ( ((TUNE*)pTune)->tune_string_type >= 3 )
			return ((TUNE*)pTune)->u.dvb.sid;
		else
			return 0;
	} else
	if ( ((TUNE*)pTune)->stream_format == ATSC_STREAM )
	{
		if ( ((TUNE*)pTune)->tune_string_type >= 3 )
			return ((TUNE*)pTune)->u.atsc.program_id;
		else return 0;
	} 

	return 0;
}

int initialTuningTable( CHANNEL_DATA *Channel );
int LoadPreDefinedScanFreq( FREQTBL *pScanFreqTbl, const char* pCountryName, const char* sourceType );
char* scanChannel( CHANNEL_DATA* channel, char* deviceName, char* tuningString, char* countryRegion, int streamType )
{
	char  *scanResult = NULL;
	int   ret=1;	
	int physicalChannel, i;

	i = 0; 
	//filter out space, check tuning string valid
	while ( tuningString != NULL && tuningString[i] && ( tuningString[i] == ' ' || tuningString[i] == '\t' || tuningString[i] == '\n') ) i++;
	if ( tuningString == NULL || ( tuningString[i] < '0' && tuningString[i] > '9' ) ) //bad string
	{
		scanResult = (char*)malloc( 128 );
		SPRINTF( scanResult, 128, "ERROR:invalid tuning string %s\r\n", tuningString==NULL ? "NULL" : tuningString );
		return scanResult;
	}

	physicalChannel = atoi( tuningString+i );
	strncpy( channel->countryRegion, countryRegion, sizeof(channel->countryRegion) );

	if ( channel->FreqTable.totalNum == 0 )
		initialTuningTable( channel );
	
	if ( physicalChannel == 0 )  //0:start scanning, -1:stop scanning
	{
		char fileName[_MAX_PATH], backFileName[_MAX_PATH];
		int num;
		//prepare scanning data
		channel->lastTune = -1;
		fileName[0] = 0x0;
		backFileName[0] = 0x0;

		if ( channel->FreqTable.entryNum != 0 )	initialTuningTable( channel );

		if ( getSourceType(channel) != 0x0 )
		{
			SPRINTF( fileName, sizeof(fileName), "%s%s-%s.frq", channel->frqTableFilePath, deviceName, getSourceType(channel) );
			SPRINTF( backFileName, sizeof(backFileName), "%s%s-%s.frq.bak",channel->frqTableFilePath,  deviceName, getSourceType(channel) );
		}

		if ( !strncmp( getSourceType( channel), "DVB-T", 5 ) )
		{
			if ( channel->PreDefinedFreqTable )
			{
				if ( channel->PreDefinedFreqTable->tbl.dvbt ) free( channel->PreDefinedFreqTable->tbl.dvbt );
				free( channel->PreDefinedFreqTable );
			}
			channel->PreDefinedFreqTable= (FREQTBL*)malloc( sizeof(FREQTBL) );
			memset( channel->PreDefinedFreqTable, 0, sizeof(FREQTBL) );
			channel->PreDefinedFreqTable->tbl.dvbt = (DVB_T_FREQ*)malloc( sizeof(DVB_T_FREQ)*MAX_DVB_SCAN_ENTRY );
			memset( channel->PreDefinedFreqTable->tbl.dvbt, 0, (sizeof(DVB_T_FREQ)*MAX_DVB_SCAN_ENTRY) );
			channel->PreDefinedFreqTable->totalNum = MAX_DVB_SCAN_ENTRY;
			strcpy( channel->PreDefinedFreqTable->sourceType, "DVB-T" );
			num = LoadPreDefinedScanFreq( channel->PreDefinedFreqTable, channel->countryRegion, getSourceType(channel) );
			SageLog(( _LOG_TRACE, 3, "loaded predefined scan freq data %d entry (%d).\r\n", num, channel->FreqTable.totalNum ));
		} else
		if ( !strncmp( getSourceType( channel), "DVB-C", 5 ) )
		{
			if ( channel->PreDefinedFreqTable )
			{
				if ( channel->PreDefinedFreqTable->tbl.dvbc ) free( channel->PreDefinedFreqTable->tbl.dvbc );
				free( channel->PreDefinedFreqTable );
			}
			channel->PreDefinedFreqTable= (FREQTBL*)malloc( sizeof(FREQTBL) );
			memset( channel->PreDefinedFreqTable, 0, sizeof(FREQTBL) );
			channel->PreDefinedFreqTable->tbl.dvbc = (DVB_C_FREQ*)malloc( sizeof(DVB_C_FREQ)*MAX_DVB_SCAN_ENTRY );
			memset( channel->PreDefinedFreqTable->tbl.dvbc, 0, (sizeof(DVB_C_FREQ)*MAX_DVB_SCAN_ENTRY) );
			channel->PreDefinedFreqTable->totalNum = MAX_DVB_SCAN_ENTRY;
			strcpy( channel->PreDefinedFreqTable->sourceType, "DVB-C" );
			num = LoadPreDefinedScanFreq( channel->PreDefinedFreqTable, channel->countryRegion, getSourceType(channel) );
			SageLog(( _LOG_TRACE, 3, "loaded predefined scan freq data %d entry (%d).\r\n", num, channel->FreqTable.totalNum ));
		}else
		if ( !strncmp( getSourceType( channel), "DVB-S", 5 ) )
		{
			int sat_num;
			SAT_NAME sat_name[MAX_SATELLATE_NUM];
			if ( channel->PreDefinedFreqTable )
			{
				if ( channel->PreDefinedFreqTable->tbl.dvbs ) free( channel->PreDefinedFreqTable->tbl.dvbs );
				free( channel->PreDefinedFreqTable );
			}
			channel->PreDefinedFreqTable= (FREQTBL*)malloc( sizeof(FREQTBL) );
			memset( channel->PreDefinedFreqTable, 0, sizeof(FREQTBL) );
			channel->PreDefinedFreqTable->tbl.dvbs = (DVB_S_FREQ*)malloc( sizeof(DVB_S_FREQ)*MAX_DVBS_SCAN_ENTRY );
			memset( channel->PreDefinedFreqTable->tbl.dvbs, 0, (sizeof(DVB_S_FREQ)*MAX_DVBS_SCAN_ENTRY) );
			channel->PreDefinedFreqTable->totalNum = MAX_DVBS_SCAN_ENTRY;
			channel->PreDefinedFreqTable->entryNum = 0;
			strcpy( channel->PreDefinedFreqTable->sourceType, "DVB-S" );
			sat_num = SageGetSatelliteTble( channel->Dev, sat_name, MAX_SATELLATE_NUM );
			if ( sat_num > 0 )
			{
				char country_sat_name[80];
				for ( i = 0; i<sat_num && i <MAX_SATELLATE_NUM; i++ )
				{
					if ( sat_name[i].sat_no != 0xffff )
					{
						SPRINTF( country_sat_name, sizeof(country_sat_name), "none-%s#%d", sat_name[i].name, sat_name[i].sat_no );
						num = LoadPreDefinedScanFreq( channel->PreDefinedFreqTable, country_sat_name, getSourceType(channel) );
						SageLog(( _LOG_TRACE, 3, "loaded predefined scan freq data %d entry (%d) for %s.\r\n", num,
						             channel->PreDefinedFreqTable->entryNum, country_sat_name ));
					}
				}
			} else 
			{
				num = LoadPreDefinedScanFreq( channel->PreDefinedFreqTable, channel->countryRegion, getSourceType(channel) );
				SageLog(( _LOG_TRACE, 3, "loaded predefined scan freq data %d entry (%d).\r\n", num, channel->PreDefinedFreqTable->entryNum ));
			}
		} else

		if ( !strncmp( getSourceType( channel), "QAM", 3 ) )
		{
#ifdef WIN32	
			//OnAir donesn't need frequency
			if (!strncmp( deviceName, "USB HDTV-GT Video Capture", 12 ) )
			{
				channel->guessFreqTableType = PRIVTE1_FREQ_SCHEME;
				channel->freqTableType      = PRIVTE1_FREQ_SCHEME;
			} 
#else  //Linux & Mac
			if (strstr( deviceName, "HDHomeRun" ) != NULL )
			{
				channel->guessFreqTableType = NTSC_FREQ_SCHEME; //HDHR auto-lock channel by itself
				channel->freqTableType      = NTSC_FREQ_SCHEME;
			}
#endif

			memset( user_defined_qam_tbl, 0, sizeof(user_defined_qam_tbl) );
			if ( channel->freqTableType == 0 )
			{
				int freqScheme;
				if ( loadPredefinedQAMFreq(  channel, user_defined_qam_tbl, MAX_USER_DEFINED_QAM-1, &freqScheme ) > 0 )
				{
					channel->freqTableType = USER_FREQ_SCHEME;
					channel->guessFreqTableType = USER_FREQ_SCHEME;
				} else
				if ( QAM_FRQ_Cache_enabled )
				{
					if ( loadCacheQAMFreq( channel, user_defined_qam_tbl, MAX_USER_DEFINED_QAM-1 ) > 0 )
					{
						channel->freqTableType = USER_FREQ_SCHEME;
						channel->guessFreqTableType = USER_FREQ_SCHEME;
					}
				}
			}

			SageLog(( _LOG_TRACE, 3, "QAM start scan. (freq mod:%d, %d )\r\n", channel->freqTableType, channel->guessFreqTableType ));
		} 

		if ( ( num = loadScanDataFile( channel ) ) >= 0 )
		{
			channel->scanType = 1;
			SageLog(( _LOG_TRACE, 3, "Scan data overriding normal scanning!!!\r\n" ));
		}
		else
			channel->scanType = 0;

		if ( !strncmp( getSourceType( channel), "ATSC", 4 ) )
		{
			SageLog(( _LOG_TRACE, 3, "ATSC start scan. (freq:%d)\r\n", channel->freqTableType ));
		}

		{
			int flag = _CopyFile( fileName, backFileName );
			if ( flag == -1 )
				SageLog(( _LOG_TRACE, 3, "File '%s' isn't exist\r\n", fileName ));
			else
			if ( flag == -2 )
				SageLog(( _LOG_TRACE, 3, "File '%s' can't be open\r\n", backFileName ));
			else
			if ( flag == 0 )
				SageLog(( _LOG_TRACE, 3, "File '%s' is empty \r\n", fileName ));
			else
				SageLog(( _LOG_TRACE, 3, "File '%s' is backed up to '%s' \r\n", fileName, backFileName )); 
		}
	} else
	if (  physicalChannel == -1 )
	{
		if ( channel->PreDefinedFreqTable )
		{
			if ( channel->PreDefinedFreqTable->tbl.dvbt ) free( channel->PreDefinedFreqTable->tbl.dvbt );
			if ( channel->PreDefinedFreqTable ) free( channel->PreDefinedFreqTable );
		}
		channel->PreDefinedFreqTable = NULL;
	} else
	if (  physicalChannel == -2 )
	{
		int num;
		char fileName[_MAX_PATH], backFileName[_MAX_PATH];
		fileName[0] = 0x0;
		backFileName[0] = 0x0;
		
		if ( getSourceType(channel) != 0x0 )
		{
			SPRINTF( fileName, sizeof(fileName), "%s%s-%s.frq", channel->frqTableFilePath, deviceName, getSourceType(channel) );
			SPRINTF( backFileName, sizeof(backFileName), "%s%s-%s.frq.bak",channel->frqTableFilePath,  deviceName, getSourceType(channel) );
		}
		SageLog(( _LOG_TRACE, 3, "Channel scan is abort, restoring '%s' from '%s' \r\n", fileName, backFileName )); 
		{
			int flag = _CopyFile( backFileName, fileName );
			if ( flag == -1 )
				SageLog(( _LOG_TRACE, 3, "File '%s' isn't exist\r\n", backFileName ));
			else
			if ( flag == -2 )
				SageLog(( _LOG_TRACE, 3, "File '%s' can't be open\r\n", fileName ));
			else
			if ( flag == 0 )
				SageLog(( _LOG_TRACE, 3, "File '%s' is empty \r\n", backFileName ));
			else
				SageLog(( _LOG_TRACE, 3, "File '%s' is restored from '%s' \r\n", fileName, backFileName )); 
		}

		if ( channel->PreDefinedFreqTable )
		{
			if ( channel->PreDefinedFreqTable->tbl.dvbt ) free( channel->PreDefinedFreqTable->tbl.dvbt );
			if ( channel->PreDefinedFreqTable ) free( channel->PreDefinedFreqTable );
		}
		channel->PreDefinedFreqTable = NULL;

		if ( ( num = loadScanTuningTable( channel ) ) >= 0 )
		{
			channel->scanType = 1;
			SageLog(( _LOG_TRACE, 3, "Scan data overriding normal scanning!!!\r\n" ));
		}
		else
			channel->scanType = 0;

	}

//SageLog(( _LOG_TRACE, 3, "***************Scan Channel :%s\r\n", tuningString ));
	SageDisableCAM( channel->Dev );
	if ( !strncmp( getSourceType( channel), "ATSC", 4 ) )
	{
		channel->lockTimeout = LOCK_FREQ_TIMEOUT * 2; //2 sec
		ret = scanATSCChannel( channel, tuningString, &scanResult );
	} else
	if ( !strncmp( getSourceType( channel), "QAM", 3 ) )
	{
		channel->lockTimeout = LOCK_FREQ_TIMEOUT * 5; //5 sec
		ret = scanQAMChannel( channel, tuningString, &scanResult );
	} else
	if ( !strncmp( getSourceType( channel), "DVB-T", 5 ) )
	{
		channel->lockTimeout = LOCK_FREQ_TIMEOUT * 3; //3 sec
		ret = scanDVBTChannel(  channel, tuningString, &scanResult );
	} else
	if ( !strncmp( getSourceType( channel), "DVB-C", 5 ) )
	{
		channel->lockTimeout = LOCK_FREQ_TIMEOUT * 3; //3 sec
		ret = scanDVBCChannel( channel, tuningString, &scanResult );
	} else
	if ( !strncmp( getSourceType( channel), "DVB-S", 5) )
	{
		channel->lockTimeout = LOCK_FREQ_TIMEOUT * 4; //4 sec
		ret = scanDVBSChannel( channel, tuningString, &scanResult );
	} else
	{
		scanResult = (char*)malloc( 64 );
		SPRINTF( scanResult, 64, "ERROR:Unknow source format %s\r\n", getSourceType( channel) );
		SageLog(( _LOG_TRACE, 3, "ERROR:Unknow source format %s\r\n", getSourceType( channel) ));
	}
	SageEnableCAM( channel->Dev );

	if ( ret == 0 )
	{
		scanResult = (char*)malloc( 16 );
		strcpy( scanResult, "DONE\r\n" );
	} else
	if ( ret == -2 )
	{
		scanResult = (char*)malloc( 16 );
		strcpy( scanResult, "DONE\r\n" );
	}
	if ( scanResult == NULL )
	{
		scanResult = (char*)malloc( 16 );
		memset( scanResult, 0x0, 16 );
	}
	SageStopParser( channel->Dev );
	SageTVReleaseScanChannel( channel->Dev );
	SageLog(( _LOG_TRACE, 3, "Scan returns(%d) %s\r\n", ret, scanResult ));

	return scanResult;		
	
}



//scan channel return 0: done; -1:error; 1:scan success, no matter lock or not
int scanATSCChannel( CHANNEL_DATA *Channel, char* tuningString, char** scanResult )
{
	int i, j, ret;
	int channel;
	int freq;
	bool found = false;
	ATSC_FREQ atsc={0};
	TUNE tune={0};
	CHANNEL_LIST *channel_list;
	char *channels_buf;
	int   channels_size;
	int   channel_num, pos;
	

	if ( Channel == NULL )
	{
		SageLog(( _LOG_TRACE, 3, "ERROR: thread racing problem.\r\n" ));
		return 1;
	}
	
	*scanResult = NULL;
	
	channel = atoi( tuningString );

//channel += 45; //ZQ REMOVE ME
	if ( channel == 0 )
		return 1;

	if ( channel < 0 )
		return 0;

	if ( channel == 1 && Channel->scanType != 1 ) //skip channel 1 on ATSC, some of card has a trouble with channel 1
		return 1;

	//use scan data overide scanning
	if ( Channel->scanType == 1 )
	{
		int j, k, pos;
		if ( Channel == NULL || Channel->PreDefinedFreqTable == NULL )
		{
			SageLog(( _LOG_TRACE, 3, "Scan data entry empty.\r\n" ));
			return 1;
		}

		*scanResult = (char*)malloc( 64*5 );
		memset( *scanResult, 0, 64*5 );
		pos = 0;
		for ( k = (channel-1)*5; k <channel*5; k++ )
		{
		
			for ( i = 0; i<=Channel->PreDefinedFreqTable->entryNum; i++ )
				if ( Channel->PreDefinedFreqTable->tbl.atsc[i].index == k )
					break;

			if ( i > Channel->PreDefinedFreqTable->entryNum )
			{
				SageLog(( _LOG_TRACE, 3, "Scan data entry is not found for channel:%d.\r\n", k ));
				continue;
			}

			j = Channel->FreqTable.entryNum;
			if ( j>= Channel->FreqTable.totalNum ) GrowFreqTable( Channel );
			Channel->FreqTable.tbl.atsc[j] = Channel->PreDefinedFreqTable->tbl.atsc[i] ;
			Channel->FreqTable.tbl.atsc[j].index = j+1;
			Channel->FreqTable.tbl.atsc[j].ctrl = 1;

			if ( Channel->FreqTable.tbl.atsc[j].major == 0 && Channel->FreqTable.tbl.atsc[j].minor == 0 )
				Channel->FreqTable.tbl.atsc[j].minor = Channel->FreqTable.tbl.atsc[j].program;


			if ( Channel->FreqTable.tbl.atsc[j].minor > 0 )
			{
				if ( Channel->FreqTable.tbl.atsc[j].physical_ch > 0  )
				{   //use QAM for remapping first
					pos += SPRINTF( *scanResult+pos, 64, "%d-%d-%d(%s)ATSC;", Channel->FreqTable.tbl.atsc[j].physical_ch,
							 Channel->FreqTable.tbl.atsc[j].major, Channel->FreqTable.tbl.atsc[j].minor, 
							 Channel->FreqTable.tbl.atsc[j].name );
				} else
				{
					pos += SPRINTF( *scanResult+pos, 64, "0-%d-%d-%d(%s)ATSC;", Channel->FreqTable.tbl.atsc[j].major, 
							 Channel->FreqTable.tbl.atsc[j].minor, 
							 Channel->FreqTable.tbl.atsc[j].index, Channel->FreqTable.tbl.atsc[j].name );
				}
			}
			else
				pos += SPRINTF( *scanResult+pos, 64, "%d(%s)ATSC;", Channel->FreqTable.tbl.atsc[j].index, 
							 Channel->FreqTable.tbl.atsc[j].name );

			Channel->FreqTable.entryNum++;

		}

		saveScanTuningTable( Channel, "frq" );
		SageLog(( _LOG_TRACE, 3, "ATSC Scan Info:%s..\r\n", *scanResult ));
		return 1;
	}

	//tune a channel
	atsc.physical_ch = channel;
	atsc.frequency = getChannelFreq( channel, ATSC_FREQ_SCHEME, 0 );
	if (  (  SageTuneATSCChannel( Channel->Dev, &atsc, 0 ) )< 0 )
		return -1;
		
	freq = ATSC_BRCAST[channel-1].fq*1000;
	//scan channel on a stream
	tune.stream_format = ATSC_STREAM;
	tune.sub_format    = TERRESTRIAL;
	tune.u.atsc.physical_ch = atsc.physical_ch;
	tune.u.atsc.u.atsc.freq = atsc.frequency;
	SageTVScanChannel( Channel->Dev, &tune );
	SageStartParser( Channel->Dev );

	ret = WaiteScanDone( Channel );	
	if ( ret < 0 )
		SageStopParser( Channel->Dev );

	//get scan channel list
	ret = SageTVScanChannelList( Channel->Dev, (void**)&channel_list );

	//count valid channel number
	channel_num = 0;
	for ( i = 0; i<channel_list->channel_num; i++ )
		if ( channel_list->channel[i].state  ) channel_num++;

	//alloc result buffer 
	channels_size = channel_num*50+2;
	channels_buf = (char*)malloc ( channels_size );
	memset( channels_buf, 0, channels_size );
	*scanResult = channels_buf;
	pos = 0;

	for ( i = 0; i<channel_list->channel_num; i++ )
	{ 
		int duplicated = false;
		if ( channel_list->channel[i].state == 0 )
			continue;

		//if a channel name shares the same name, make an unique name
		for ( j = i; j>0; j-- )
		{
			if ( !strcmp( channel_list->channel[i].u.atsc.name, channel_list->channel[j-1].u.atsc.name ) )
			{
				snprintf( channel_list->channel[i].u.atsc.name, sizeof(channel_list->channel[i].u.atsc.name)-1, "%s%d",
						  channel_list->channel[j-1].u.atsc.name, channel_list->channel[i].u.atsc.minor_num );
				SageLog(( _LOG_TRACE, 3, "ATSC Channel:was assigned a new name %s, beacuse of duplicated name.\r\n", 
									channel_list->channel[i].u.atsc.name ));
			}
		}

		SageLog(( _LOG_TRACE, 3, "found ATSC Channel:%d; physical:%d; major:%d; minor:%d; program_id:%d; name:%s (%d)\r\n",
				channel, channel, channel_list->channel[i].u.atsc.major_num, channel_list->channel[i].u.atsc.minor_num, 
				channel_list->channel[i].u.atsc.program_id, channel_list->channel[i].u.atsc.name, 
				channel_list->channel[i].u.atsc.service_type ));

		//check duplicated channel
		for ( j = 0; j<Channel->FreqTable.entryNum; j++ )
		{
			if ( Channel->FreqTable.tbl.atsc[j].major == channel_list->channel[i].u.atsc.major_num &&
				 Channel->FreqTable.tbl.atsc[j].minor == channel_list->channel[i].u.atsc.minor_num &&
			     Channel->FreqTable.tbl.atsc[j].program == channel_list->channel[i].u.atsc.program_id &&
				 Channel->FreqTable.tbl.qam[j].physical_ch == channel )
			{
				duplicated = true;
				SageLog(( _LOG_TRACE, 3, "Drop duplicated ATSC Channel:%d; major:%d; minor:%d; program_id:%d;\r\n",
				channel, channel_list->channel[i].u.atsc.major_num, channel_list->channel[i].u.atsc.minor_num, 
				channel_list->channel[i].u.atsc.program_id ));
				break;	
			}
		}
		if ( duplicated ) continue;

		//add a new channel to the channel table
		j = Channel->FreqTable.entryNum;
		if ( j>= Channel->FreqTable.totalNum ) GrowFreqTable( Channel );
		Channel->FreqTable.tbl.atsc[j].index   = j+1;
		Channel->FreqTable.tbl.atsc[j].major   = channel_list->channel[i].u.atsc.major_num;
		Channel->FreqTable.tbl.atsc[j].minor   = channel_list->channel[i].u.atsc.minor_num;
		Channel->FreqTable.tbl.atsc[j].program = channel_list->channel[i].u.atsc.program_id;
		Channel->FreqTable.tbl.atsc[j].physical_ch = channel;
		Channel->FreqTable.tbl.atsc[j].frequency = freq;
		Channel->FreqTable.tbl.atsc[j].ctrl = 1;
		strncpy( Channel->FreqTable.tbl.atsc[j].name, channel_list->channel[i].u.atsc.name, sizeof(Channel->FreqTable.tbl.atsc[j].name) );
		Channel->FreqTable.entryNum++;
		found = true;
		
		pos += SPRINTF( channels_buf+pos, channels_size-pos, "%d-%d-%d(%s)ATSC%s;", channel, 
			channel_list->channel[i].u.atsc.major_num, channel_list->channel[i].u.atsc.minor_num,
			channel_list->channel[i].u.atsc.name, channel_list->channel[i].u.atsc.service_type==3?"":"" ); 
	} 
	SageStopParser( Channel->Dev );
	//SageTVReleaseScanChannel( Channel->Dev );
	if ( found ) 
	{
//		pos += SPRINTF( channels_buf+pos,  channels_size-pos, "\r\n" );
		saveScanTuningTable( Channel, "frq" );
	}
	SageLog(( _LOG_TRACE, 3, "ATSC Scan Info:%s %d.\r\n", *scanResult, found ));
	return 1;
}

int scanQAMChannel( CHANNEL_DATA *Channel, char* tuningString, char** scanResult )
{
	int i=0, j, ret=0;
	int count=0;
	int channel;
	int freq;
	bool found = false;

	QAM_FREQ qam={0};
	TUNE tune={0};
	CHANNEL_LIST *channel_list;
	char *channels_buf;
	int   channels_size;
	int   channel_num, pos;

	*scanResult = NULL;
	channel = atoi( tuningString );

//channel+=69; //ZQ REMOVE ME ********

	if ( channel == 0 )
		return 1;

	if ( channel < 0 )
		return 0;

	//use scan data overide scan
	if ( Channel->scanType == 1 )
	{
		int j, k, pos;
		if ( Channel == NULL || Channel->PreDefinedFreqTable == NULL )
		{
			SageLog(( _LOG_TRACE, 3, "Scan data entry empty.\r\n" ));
			return 1;
		}

		*scanResult = (char*)malloc( 64*8 );
		memset( *scanResult, 0, 64*8 );
		pos = 0;
		for ( k = (channel-1)*8; k<channel*8; k++ )
		{
			for ( i = 0; i<=Channel->PreDefinedFreqTable->entryNum; i++ )
				if ( Channel->PreDefinedFreqTable->tbl.qam[i].index == k )	break;

			if ( i > Channel->PreDefinedFreqTable->entryNum )
			{
				SageLog(( _LOG_TRACE, 3, "Scan data entry is not found for channel:%d.\r\n", channel ));
				continue;
			}

			j = Channel->FreqTable.entryNum;
			if ( j>= Channel->FreqTable.totalNum ) GrowFreqTable( Channel );
			Channel->FreqTable.tbl.qam[j] = Channel->PreDefinedFreqTable->tbl.qam[i] ;
			Channel->FreqTable.tbl.qam[j].index = j+1;
			Channel->FreqTable.tbl.qam[j].ctrl = 1;

			//HDHR scn file not filling major-minor (0-0) for a channel, which has a trouble to map a channel, we use a program for minor.
			if ( Channel->FreqTable.tbl.qam[j].major == 0 && Channel->FreqTable.tbl.qam[j].minor == 0 )
				Channel->FreqTable.tbl.qam[j].minor = Channel->FreqTable.tbl.qam[j].program;


			if ( Channel->FreqTable.tbl.qam[j].minor > 0 )
			{
				if ( Channel->FreqTable.tbl.qam[j].physical_ch > 0  )
				{
					pos += SPRINTF( *scanResult+pos, 64, "%d-%d-%d(%s)QAM;",  Channel->FreqTable.tbl.qam[j].physical_ch,
							  Channel->FreqTable.tbl.qam[j].major, Channel->FreqTable.tbl.qam[j].minor,
							  Channel->FreqTable.tbl.qam[j].name );
				} else
				{
					pos += SPRINTF( *scanResult+pos, 64, "0-%d-%d(%s)QAM;",     
							  Channel->FreqTable.tbl.qam[j].major, Channel->FreqTable.tbl.qam[j].minor,   
							  Channel->FreqTable.tbl.qam[j].name );
				}
			}
			else
				pos += SPRINTF( *scanResult+pos, 64, "%d(%s)QAM;",  Channel->FreqTable.tbl.qam[j].index, Channel->FreqTable.tbl.qam[j].name );

			Channel->FreqTable.entryNum++;
		}
		saveScanTuningTable( Channel, "frq" );
		SageLog(( _LOG_TRACE, 3, "QAM Scan Info:%s..\r\n", *scanResult ));
		return 1;
	}


	if ( Channel->guessFreqTableType == 0 )
	{
		char freq_type[]={ 0, NTSC_FREQ_SCHEME, ATSC_FREQ_SCHEME, HRC_FREQ_SCHEME, USER_FREQ_SCHEME };
		for ( i = 1; i<=3; i++ )
		{      //try Standard NTSC cable frequency
			qam.frequency = getChannelFreq( channel, freq_type[i], Channel->freqShift );
			qam.physical_ch = channel;
			qam.modulation = _BDA_MOD_256QAM;
			if ( qam.frequency == 0xffffffff )
				continue;  //no channel exist, skip
			if ( qam.frequency == 0 )
			{
				saveCacheQAMFreq( Channel, 0, (struct bcast_qam*)user_defined_qam_tbl );
				SageLog(( _LOG_TRACE, 3, "End of scan data (%d)\r\n", channel ));
				return -2;
			}

		local_retry1:
			SageLog(( _LOG_TRACE, 3, "Guess frq type type: frq:%d mod:%d (type:%d), shift:%d, channel:%d.\r\n", qam.frequency, qam.modulation, freq_type[i], Channel->freqShift, channel ));
			if (  ( freq = SageTuneQAMFrequency( Channel->Dev, &qam, 0 ) )< 0 )
				return -1;

			ret = 0;
			count = 0;
			while( count < Channel->lockTimeout )
			{
				if ( ( ret = SageCheckLocked( Channel->Dev ) )< 0 )
				{
					SageLog(( _LOG_TRACE, 3, "DVB couldn't read status.\r\n"));
					return -1;
				}
				else 
				if ( ret > 0 && channel > 2 )
				{
					SageLog(( _LOG_TRACE, 3, "DVB tuning locked freq:%d (%d).\r\n", freq, freq_type[i] ));
					if ( freq_type[i] == 1 ) SageLog(( _LOG_TRACE, 3, "QAM uses ATSC frequency\r\n" )); else
					if ( freq_type[i] == 2 ) SageLog(( _LOG_TRACE, 3, "QAM uses NTSC Cable frequency\r\n" )); else
					if ( freq_type[i] == 3 ) SageLog(( _LOG_TRACE, 3, "QAM uses NTSC HRC frequency\r\n" ));
					Channel->guessFreqTableType = freq_type[i];
					initUseDefinedQAMFrq( Channel->guessFreqTableType, Channel->freqShift );
					updateUseDefinedQAMFrq( channel, qam.frequency, qam.modulation, 0 );
					saveCacheQAMFreq( Channel, Channel->guessFreqTableType, user_defined_qam_tbl );
					break;
				}
				count+=1;                        
				usleep( SLEEP_100MS ); /* 0.1 second*/
			}
			if ( ret > 0 ) break;
			if ( QAM64_Enabled && qam.modulation == _BDA_MOD_256QAM )
			{
				qam.modulation = _BDA_MOD_64QAM;
				goto local_retry1;
			}

		} /* for ( i = 1; */
		if ( i >= 4 )
		{
			SageLog(( _LOG_TRACE, 3, "DVB tuning not locked frqs(QAM).\r\n" ));
			return 1;
		}
	} else
	{
		if ( Channel->freqTableType == USER_FREQ_SCHEME )
		{
			struct bcast_qam * qam_frq = getUseDefinedQAMFrq( channel );
			if ( qam_frq != NULL )
			{
				qam.physical_ch = channel;
				qam.frequency  = qam_frq->fq;
				qam.inversal   = qam_frq->inv;
				qam.modulation = qam_frq->mod;
				if ( qam_frq->mod == 0 || qam_frq->mod == -1 )
					qam.modulation = _BDA_MOD_256QAM;

			} else
			{
				SageLog(( _LOG_TRACE, 3, "DVB QAM frequency was not found for channel %d in PredefinedQAM.frq, skip.\r\n", channel ));
				return 1;
			}

		} else
		if ( Channel->freqTableType == PRIVTE1_FREQ_SCHEME )
		{
			qam.frequency  = getChannelFreq( channel, Channel->guessFreqTableType, Channel->freqShift );
			qam.inversal   = 0;
			qam.modulation = 0;
			qam.physical_ch = channel;
		} else
		{
			qam.frequency = getChannelFreq( channel, Channel->guessFreqTableType, Channel->freqShift );
			qam.modulation = _BDA_MOD_256QAM;
			qam.physical_ch = channel;
		}

		qam.physical_ch = channel;
		if ( qam.frequency == 0xffffffff )
				return 1;  //no  channel exist, skip
		if ( qam.frequency == 0 )
		{
			saveCacheQAMFreq( Channel, Channel->freqTableType, user_defined_qam_tbl );
			SageLog(( _LOG_TRACE, 3, "End of scan data (%d)\r\n", channel ));
			return -2;
		}

	local_retry2:

		if (  ( freq = SageTuneQAMFrequency( Channel->Dev, &qam, 0 ) )< 0 )
			return -1;
			
		ret = 0;
		while( count < Channel->lockTimeout )
		{
			if ( ( ret = SageCheckLocked( Channel->Dev ) )< 0 )
			{
				SageLog(( _LOG_TRACE, 3, "DVB couldn't read status.\r\n"));
				return -1;
			}
			else 
			if ( ret > 0 )
			{
				updateUseDefinedQAMFrq( channel, qam.frequency, qam.modulation, 0 );
				SageLog(( _LOG_TRACE, 3, "DVB tuning locked ret:%d %d mod:%d shift:%d (QAM channel:%d) frq type(%d-%d).\r\n",
							           freq, qam.frequency, qam.modulation, Channel->freqShift, channel,
									   Channel->freqTableType, Channel->guessFreqTableType ));
				break;
			}
			count+=1;                        
			usleep( SLEEP_100MS ); /* 0.1 seconds */
		}
		if ( ret <= 0 )
		{
			SageLog(( _LOG_TRACE, 3, "DVB tuning not locked frq:%d mode:%d shift:%d (QAM:%d).\r\n",
				                                       qam.frequency, qam.modulation, Channel->freqShift, channel ));
			if ( QAM64_Enabled && qam.modulation == _BDA_MOD_256QAM )
			{
				qam.modulation = _BDA_MOD_64QAM;
				goto local_retry2;
			}
			return 1;
		}	
	}
		
	//scan channel on a stream
	tune.stream_format = ATSC_STREAM;
	tune.sub_format    = CABLE;
	tune.u.atsc.physical_ch = qam.physical_ch;
	tune.u.atsc.u.atsc.freq = qam.frequency;
	SageTVScanChannel( Channel->Dev, &tune );
	SageStartParser( Channel->Dev );

	ret = WaiteScanDone( Channel );	
	if ( ret < 0 )
		SageStopParser( Channel->Dev );

	//get scan channel list
	ret = SageTVScanChannelList( Channel->Dev, (void**)&channel_list );

	//count valid channel number
	channel_num = 0;
	for ( i = 0; i<channel_list->channel_num; i++ )
		if ( channel_list->channel[i].state  ) channel_num++;

	//alloc result buffer 
	channels_size = channel_num*50+2;
	channels_buf = (char*)malloc ( channels_size );
	memset( channels_buf, 0, channels_size );
	*scanResult = channels_buf;
	pos = 0;

	for ( i = 0; i<channel_list->channel_num; i++ )
	{ 
		int duplicated = false;
		if ( channel_list->channel[i].state == 0 )
			continue;
		
		//reassign 1008 channel
		if ( channel_list->channel[i].u.atsc.major_num == 1008 )
		{
			SageLog(( _LOG_TRACE, 3, "Reassign 1008 QAM Channels name:%d; physical:%d; major:%d; minor:%d; program_id:%d; name:%s (%d)\r\n",
				channel, channel, channel_list->channel[i].u.atsc.major_num, channel_list->channel[i].u.atsc.minor_num, 
				channel_list->channel[i].u.atsc.program_id, channel_list->channel[i].u.atsc.name, 
				channel_list->channel[i].u.atsc.service_type ));
			channel_list->channel[i].u.atsc.major_num = 0;
			channel_list->channel[i].u.atsc.minor_num = channel_list->channel[i].u.atsc.program_id;
			snprintf( channel_list->channel[i].u.atsc.name, sizeof(channel_list->channel[i].u.atsc.name)-1, "DTV-%d-%d", 
				       channel, channel_list->channel[i].u.atsc.program_id );
		}

		SageLog(( _LOG_TRACE, 3, "found QAM Channel:%d; physical:%d; major:%d; minor:%d; program_id:%d; name:%s (%d)\r\n",
				channel, channel, channel_list->channel[i].u.atsc.major_num, channel_list->channel[i].u.atsc.minor_num, 
				channel_list->channel[i].u.atsc.program_id, channel_list->channel[i].u.atsc.name, 
				channel_list->channel[i].u.atsc.service_type ));

		//check duplicated channel
		for ( j = 0; j<Channel->FreqTable.entryNum; j++ )
		{
			if ( Channel->FreqTable.tbl.qam[j].major == channel_list->channel[i].u.atsc.major_num &&
				 Channel->FreqTable.tbl.qam[j].minor == channel_list->channel[i].u.atsc.minor_num &&
			     Channel->FreqTable.tbl.qam[j].program == channel_list->channel[i].u.atsc.program_id &&
				 Channel->FreqTable.tbl.qam[j].physical_ch == channel )
			{
				duplicated = true;
				SageLog(( _LOG_TRACE, 3, "Drop duplicated QAM Channel:%d; major:%d; minor:%d; program_id:%d;\r\n",
				channel, channel_list->channel[i].u.atsc.major_num, channel_list->channel[i].u.atsc.minor_num, 
				channel_list->channel[i].u.atsc.program_id ));
				break;	
			}
		}
		if ( duplicated ) continue;

		j = Channel->FreqTable.entryNum;
		if ( j>= Channel->FreqTable.totalNum )  GrowFreqTable( Channel );
		Channel->FreqTable.tbl.qam[j].index   = j+1;
		Channel->FreqTable.tbl.qam[j].major   = channel_list->channel[i].u.atsc.major_num;
		Channel->FreqTable.tbl.qam[j].minor   = channel_list->channel[i].u.atsc.minor_num;
		Channel->FreqTable.tbl.qam[j].program = channel_list->channel[i].u.atsc.program_id;
		Channel->FreqTable.tbl.qam[j].physical_ch = channel;
		Channel->FreqTable.tbl.qam[j].frequency  = qam.frequency;
		Channel->FreqTable.tbl.qam[j].modulation = qam.modulation;
		Channel->FreqTable.tbl.qam[j].inversal   = qam.inversal;
		Channel->FreqTable.tbl.qam[j].ctrl = 1;
		Channel->FreqTable.entryNum++;
		strncpy( Channel->FreqTable.tbl.qam[j].name, channel_list->channel[i].u.atsc.name, 
			                                    sizeof(Channel->FreqTable.tbl.qam[j].name) );
		found = true;

		pos += SPRINTF( channels_buf+pos, channels_size-pos, "%d-%d-%d(%s)QAM%s;", channel, 
			channel_list->channel[i].u.atsc.major_num , channel_list->channel[i].u.atsc.minor_num,
			channel_list->channel[i].u.atsc.name, channel_list->channel[i].u.atsc.service_type==3?"":"" ); 
	} 

	SageStopParser( Channel->Dev );
	//SageTVReleaseScanChannel( Channel->Dev );
	if ( found ) 
	{
		pos += SPRINTF( channels_buf+pos, channels_size-pos,  "\r\n" );
		saveScanTuningTable( Channel, "frq" );
	}
	SageLog(( _LOG_TRACE, 3, "QAM Scan Info:%s %d.\r\n", *scanResult, found ));

	return 1;

}

int scanDVBTChannel( CHANNEL_DATA *Channel, char* tuningString, char** scanResult )
{
	int i, j, ret;
	int count;
	int channel;
	int freq;
	bool found = false;

	DVB_T_FREQ *DVBTFreq;
	TUNE tune={0};
	CHANNEL_LIST *channel_list;
	char *channels_buf;
	int   channels_size;
	int   channel_num, pos;
	int	  encrypted;
	
	*scanResult = NULL;
	channel = atoi( tuningString );

	if ( channel == 0 )
		return 1;

	if ( channel < 0 )
		return 0;

	//use scan data to override scanning
	if ( Channel->scanType == 1 )
	{
		int j, k, pos;
		if ( Channel == NULL || Channel->PreDefinedFreqTable == NULL )
		{
			SageLog(( _LOG_TRACE, 3, "Scan data entry empty.\r\n" ));
			return 1;
		}

		*scanResult = (char*)malloc( 64*8 );
		memset( *scanResult, 0, 64*8 );
		pos = 0;
		for ( k = (channel-1)*8; k<channel*8; k++ )
		{
			for ( i = 0; i<=Channel->PreDefinedFreqTable->entryNum; i++ )
				if ( Channel->PreDefinedFreqTable->tbl.dvbt[i].index == k )	break;

			if ( i > Channel->PreDefinedFreqTable->entryNum )
			{
				SageLog(( _LOG_TRACE, 3, "Scan data entry is not found for channel:%d.\r\n", channel ));
				continue;
			}

			j = Channel->FreqTable.entryNum;
			if ( j>= Channel->FreqTable.totalNum ) GrowFreqTable( Channel );
			Channel->FreqTable.tbl.dvbt[j] = Channel->PreDefinedFreqTable->tbl.dvbt[i] ;
			Channel->FreqTable.tbl.dvbt[j].ctrl = 1;
			Channel->FreqTable.tbl.dvbt[j].index = j+1;

			pos += SPRINTF( *scanResult+pos, 64, "%d-%d-%d-%d(%s)DVB-T;",  Channel->FreqTable.tbl.dvbt[j].onid,
				Channel->FreqTable.tbl.dvbt[j].tsid,    Channel->FreqTable.tbl.dvbt[j].sid,
				Channel->FreqTable.tbl.dvbt[j].index,   Channel->FreqTable.tbl.dvbt[j].name );

			Channel->FreqTable.entryNum++;
		}
		saveScanTuningTable( Channel, "frq" );
		SageLog(( _LOG_TRACE, 3, "DVB-T Scan Info:%s..\r\n", *scanResult ));
		return 1;
	}

	if ( Channel == NULL || Channel->PreDefinedFreqTable == NULL || 
	     Channel->PreDefinedFreqTable->tbl.dvbt == NULL || Channel->PreDefinedFreqTable->entryNum == 0 )
	{
		SageLog(( _LOG_TRACE, 3, "Scan DVB is not initialized\r\n" ));
		return -2;
	}
	
	if ( Channel->PreDefinedFreqTable->entryNum < channel )
	{
		SageLog(( _LOG_TRACE, 3, "End of scan data (%d)\r\n", Channel->PreDefinedFreqTable->entryNum ));
		return -2;
	}
	
	DVBTFreq = &Channel->PreDefinedFreqTable->tbl.dvbt[ channel-1 ];
	if ( DVBTFreq->frequency == 0 )
	{
		SageLog(( _LOG_TRACE, 3, "invalid frquency.\r\n"));
		return 1;
	}

	//Channel->lockTimeout = LOCK_FREQ_TIMEOUT;
	for ( i = 0; i<3; i++ )
	{
		if (  ( freq = SageTuneDVBTFrequency( Channel->Dev, DVBTFreq, 0 ) )< 0 )
			return -1;

		ret = 0; 
		count = 0;
		while( count < Channel->lockTimeout )
		{
			if ( ( ret = SageCheckLocked( Channel->Dev ) )< 0 )
			{
				SageLog(( _LOG_TRACE, 3, "DVB-T couldn't read status.\r\n"));
				return -1;
			}
			else 
			if ( ret > 0 )
			{
				SageLog(( _LOG_TRACE, 3, "DVB-T tuning locked\r\n"));
				break;
			}
			count+=1;                        
			usleep( SLEEP_100MS ); /* 0.1 second */
		}
		if ( ret > 0 )
			break;

		//shift frequency to lock
		{
			uint32_t shift = 125000;
			if ( DVBTFreq->shift > 0 ) shift = DVBTFreq->shift;

			if ( i == 0 )
				DVBTFreq->frequency += shift;
			else
			if ( i == 1 )
				DVBTFreq->frequency -= (shift+shift);
		}
	}

	if ( ret <= 0 )
	{
		SageLog(( _LOG_TRACE, 3, "DVB-T tuning not locked.\r\n"));
		return 1;
	}	

	//scan channel on a stream
	tune.stream_format = DVB_STREAM;
	tune.sub_format    = TERRESTRIAL;
	tune.u.dvb.dvb_type = TERRESTRIAL;
	tune.u.dvb.dvb.t.freq = DVBTFreq->frequency;
	SageTVScanChannel( Channel->Dev, &tune );
	SageStartParser( Channel->Dev );

	ret = WaiteScanDone( Channel );	
	if ( ret < 0 )
		SageStopParser( Channel->Dev );

	//get scan channel list
	ret = SageTVScanChannelList( Channel->Dev, (void**)&channel_list );

	//count valid channel number
	channel_num = 0;
	for ( i = 0; i<channel_list->channel_num; i++ )
		if ( channel_list->channel[i].state  ) channel_num++;

	//alloc result buffer 
	channels_size = channel_num*50+2;
	channels_buf = (char*)malloc ( channels_size );
	memset( channels_buf, 0, channels_size );
	*scanResult = channels_buf;
	pos = 0;
	for ( i = 0; i<channel_list->channel_num; i++ )
	{ 
		int duplicated = false;
		if ( channel_list->channel[i].state == 0 )
			continue;

		SageLog(( _LOG_TRACE, 3, "found DVB-T Channel:%d; onid:%d tsid:%d sid:%d frq:%ld name:%s type:%d.\r\n",
				channel, channel_list->channel[i].u.dvb.onid, channel_list->channel[i].u.dvb.tsid,
				channel_list->channel[i].u.dvb.sid,	
				channel_list->channel[i].u.dvb.dvb.t.freq,
				channel_list->channel[i].u.dvb.name, channel_list->channel[i].u.dvb.service_type  ));

		//check duplicated channel
		for ( j = 0; j<Channel->FreqTable.entryNum; j++ )
		{
			if ( Channel->FreqTable.tbl.dvbt[j].onid == channel_list->channel[i].u.dvb.onid &&
				 Channel->FreqTable.tbl.dvbt[j].tsid == channel_list->channel[i].u.dvb.tsid &&
				 Channel->FreqTable.tbl.dvbt[j].sid  == channel_list->channel[i].u.dvb.sid )
			{
				duplicated = true;
				SageLog(( _LOG_TRACE, 3, "Drop duplicated DVB-T Channel:%d; onid:%d tsid:%d sid:%d name:%s type:%d.\r\n",
				channel, channel_list->channel[i].u.dvb.onid, channel_list->channel[i].u.dvb.tsid,
				channel_list->channel[i].u.dvb.sid, channel_list->channel[i].u.dvb.name, channel_list->channel[i].u.dvb.service_type ));
				break;	
			}
		}
		if ( duplicated ) continue;

		//add a new channel to the channel table
		j = Channel->FreqTable.entryNum;
		if ( j>= Channel->FreqTable.totalNum ) 
		{
			SageLog(( _LOG_TRACE, 3, "Expending tuning table (%d %d)\r\n", j, Channel->FreqTable.totalNum );
				GrowFreqTable( Channel ));
		}
		Channel->FreqTable.tbl.dvbt[j].index = j+1;
		Channel->FreqTable.tbl.dvbt[j].onid = channel_list->channel[i].u.dvb.onid;
		Channel->FreqTable.tbl.dvbt[j].tsid = channel_list->channel[i].u.dvb.tsid;
		Channel->FreqTable.tbl.dvbt[j].sid  = channel_list->channel[i].u.dvb.sid;
		Channel->FreqTable.tbl.dvbt[j].physical_ch = channel_list->channel[i].u.dvb.sid;

		Channel->FreqTable.tbl.dvbt[j].frequency = DVBTFreq->frequency;;
		Channel->FreqTable.tbl.dvbt[j].bandwidth = DVBTFreq->bandwidth;
		Channel->FreqTable.tbl.dvbt[j].code_rate_hp = DVBTFreq->code_rate_hp;
		Channel->FreqTable.tbl.dvbt[j].code_rate_lp = DVBTFreq->code_rate_lp;
		Channel->FreqTable.tbl.dvbt[j].trans_mode = DVBTFreq->trans_mode;
		Channel->FreqTable.tbl.dvbt[j].guard_interval = DVBTFreq->guard_interval;
		Channel->FreqTable.tbl.dvbt[j].hierarchy = DVBTFreq->hierarchy;
		Channel->FreqTable.tbl.dvbt[j].constellation = DVBTFreq->constellation;

		Channel->FreqTable.tbl.dvbt[j].ctrl = 1;
		encrypted = 0;
		if ( channel_list->channel[i].u.dvb.ca ) 
		{
			Channel->FreqTable.tbl.dvbt[j].ctrl |= 0x02;
			encrypted = 1;
		}

		Channel->FreqTable.entryNum++;
		strncpy( Channel->FreqTable.tbl.dvbt[j].name, channel_list->channel[i].u.dvb.name,
											sizeof(Channel->FreqTable.tbl.dvbt[j].name) );
		found = true;

		pos += SPRINTF( channels_buf+pos, channels_size-pos, "%d-%d-%d-%d(%s)DVB-T%s%s;", 
			channel_list->channel[i].u.dvb.onid, channel_list->channel[i].u.dvb.tsid,
			channel_list->channel[i].u.dvb.sid, 
			Channel->FreqTable.tbl.dvbt[j].index,
			channel_list->channel[i].u.dvb.name, encrypted?"-ENC":"",
			channel_list->channel[i].u.dvb.service_type == 2?"":"" ); 
	
	} 

	{   //retrevie NIT table to update DVBS scan table
		TUNE_LIST* pTuneList;
		if ( SageTVScanTuneList( Channel->Dev, (void*)&pTuneList ) )
		{
			int k;
			SageLog(( _LOG_TRACE, 3, "Get a Nit, total tuner entry in table:%d\r\n", pTuneList->tune_num ) );
			for ( i = 0; i<pTuneList->tune_num; i++ )
			{
				if ( IsInDVBPreDefinedFreqTable( Channel->PreDefinedFreqTable, pTuneList->tune[i].u.s.freq ) )
					continue;
				k = Channel->PreDefinedFreqTable->entryNum;
				if ( Channel->PreDefinedFreqTable->entryNum >= Channel->PreDefinedFreqTable->totalNum) 
					GrowFreqTable( Channel );
				Channel->PreDefinedFreqTable->tbl.dvbt[k].frequency    = pTuneList->tune[i].u.t.freq;
				Channel->PreDefinedFreqTable->tbl.dvbt[k].bandwidth    = DVBTFreq->bandwidth;
				
				SageLog(( _LOG_TRACE, 3, "add a scan entry %d scan entry  frq:%ld  \r\n", k, pTuneList->tune[i].u.s.freq	 ));
				Channel->PreDefinedFreqTable->entryNum++;
			}
		}
	} 

	SageStopParser( Channel->Dev );
	//SageTVReleaseScanChannel( Channel->Dev );
	if ( found ) 
	{
//		pos += SPRINTF( channels_buf+pos, channels_size-pos, "\r\n" );
		saveScanTuningTable( Channel, "frq" );
	}
	SageLog(( _LOG_TRACE, 3, "DVB-T Scan Info:%s %d.\r\n", *scanResult, found ));

	return 1;

}

int scanDVBCChannel( CHANNEL_DATA *Channel, char* tuningString, char** scanResult )
{
	int i, j, ret;
	int count=0;
	int channel;
	int freq;
	bool found = false;

	DVB_C_FREQ *DVBCFreq;
	TUNE tune={0};
	CHANNEL_LIST *channel_list;
	char *channels_buf;
	int   channels_size;
	int   channel_num, pos;
	int   encrypted;
	
	*scanResult = NULL;
	
	channel = atoi( tuningString );

	if ( channel == 0 )
		return 1;

	if ( channel < 0 )
		return 0;

	if ( Channel->scanType == 1 )
	{
		int j, k, pos;
		if ( Channel == NULL || Channel->PreDefinedFreqTable == NULL )
		{
			SageLog(( _LOG_TRACE, 3, "Scan data entry empty.\r\n" ));
			return 1;
		}

		*scanResult = (char*)malloc( 64*8 );
		memset( *scanResult, 0, 64*8 );
		pos = 0;
		for ( k = (channel-1)*8; k<channel*8; k++ )
		{
			for ( i = 0; i<=Channel->PreDefinedFreqTable->entryNum; i++ )
				if ( Channel->PreDefinedFreqTable->tbl.dvbc[i].index == k )	break;

			if ( i > Channel->PreDefinedFreqTable->entryNum )
			{
				SageLog(( _LOG_TRACE, 3, "Scan data entry is not found for channel:%d.\r\n", channel ));
				continue;
			}

			j = Channel->FreqTable.entryNum;
			if ( j>= Channel->FreqTable.totalNum ) GrowFreqTable( Channel );
			Channel->FreqTable.tbl.dvbc[j] = Channel->PreDefinedFreqTable->tbl.dvbc[i] ;
			Channel->FreqTable.tbl.dvbc[j].ctrl = 1;
			Channel->FreqTable.tbl.dvbc[j].index = j+1;

			pos += SPRINTF( *scanResult+pos, 64, "%d-%d-%d-%d(%s)DVB-C;",  Channel->FreqTable.tbl.dvbc[j].onid,
				Channel->FreqTable.tbl.dvbc[j].tsid,    Channel->FreqTable.tbl.dvbc[j].sid,
				Channel->FreqTable.tbl.dvbc[j].index,   Channel->FreqTable.tbl.dvbc[j].name );

			Channel->FreqTable.entryNum++;
		}
		saveScanTuningTable( Channel, "frq" );
		SageLog(( _LOG_TRACE, 3, "DVB-C Scan Info:%s..\r\n", *scanResult ));
		return 1;
	}


	if ( Channel == NULL || Channel->PreDefinedFreqTable == NULL || 
	     Channel->PreDefinedFreqTable->tbl.dvbt == NULL || Channel->PreDefinedFreqTable->entryNum == 0 )
	{
		SageLog(( _LOG_TRACE, 3, "Scan DVB-C is not initialized\r\n" ));
		return -2;
	}
	
	if ( Channel->PreDefinedFreqTable->entryNum < channel )
	{
		SageLog(( _LOG_TRACE, 3, "End of scan data (%d)\r\n", Channel->PreDefinedFreqTable->entryNum ));
		return -2;
	}

	DVBCFreq = &Channel->PreDefinedFreqTable->tbl.dvbc[ channel-1 ];
	if ( DVBCFreq->frequency == 0 )
	{
		SageLog(( _LOG_TRACE, 3, "invalid frquency (freq=0).\r\n"));
		return -2;
	}

	if (  ( freq = SageTuneDVBCFrequency( Channel->Dev, DVBCFreq, 0 ) )< 0 )
		return -1;

	ret = 0;
	Channel->lockTimeout = LOCK_FREQ_TIMEOUT + LOCK_FREQ_TIMEOUT/2;
	while( count < Channel->lockTimeout )
	{
		if ( ( ret = SageCheckLocked( Channel->Dev ) )< 0 )
		{
			SageLog(( _LOG_TRACE, 3, "DVB-C couldn't read status.\r\n"));
			return -1;
		}
		else 
		if ( ret > 0 )
		{
			SageLog(( _LOG_TRACE, 3, "DVB-C tuning locked\r\n"));
			break;
		}
		count+=1;                        
		usleep( SLEEP_100MS ); /* 0.1 second*/
	}
	
	if ( ret <= 0 )
	{
		SageLog(( _LOG_TRACE, 3, "DVB-C tuning not locked.\r\n"));
		return 1;
	}	
		
	//scan channel on a stream
	tune.stream_format = DVB_STREAM;
	tune.sub_format    = CABLE;
	tune.u.dvb.dvb_type = CABLE;
	tune.u.dvb.dvb.c.freq = DVBCFreq->frequency;
	tune.u.dvb.dvb.c.modulation = DVBCFreq->modulation;
	SageTVScanChannel( Channel->Dev, &tune );
	SageStartParser( Channel->Dev );

	ret = WaiteScanDone( Channel );	
	if ( ret < 0 )
		SageStopParser( Channel->Dev );

	//get scan channel list
	ret = SageTVScanChannelList( Channel->Dev, (void**)&channel_list );

	//count valid channel number
	channel_num = 0;
	for ( i = 0; i<channel_list->channel_num; i++ )
		if ( channel_list->channel[i].state  ) channel_num++;

	//alloc result buffer 
	channels_size = channel_num*50+2;
	channels_buf = (char*)malloc ( channels_size );
	memset( channels_buf, 0, channels_size );
	*scanResult = channels_buf;
	pos = 0;

	for ( i = 0; i<channel_list->channel_num; i++ )
	{ 
		int duplicated = false;
		if ( channel_list->channel[i].state == 0 )
			continue;

		SageLog(( _LOG_TRACE, 3, "found DVB-C Channel:%d; onid:%d tsid:%d sid:%d frq:%ld name:%s type:%d.\r\n",
				channel, channel_list->channel[i].u.dvb.onid, channel_list->channel[i].u.dvb.tsid,
				channel_list->channel[i].u.dvb.sid,	
				channel_list->channel[i].u.dvb.dvb.c.freq,
				channel_list->channel[i].u.dvb.name, channel_list->channel[i].u.dvb.service_type  ));

		//check duplicated channel
		for ( j = 0; j<Channel->FreqTable.entryNum; j++ )
		{
			if ( Channel->FreqTable.tbl.dvbc[j].onid == channel_list->channel[i].u.dvb.onid &&
				 Channel->FreqTable.tbl.dvbc[j].tsid == channel_list->channel[i].u.dvb.tsid &&
				 Channel->FreqTable.tbl.dvbc[j].sid  == channel_list->channel[i].u.dvb.sid )
			{
				duplicated = true;
				SageLog(( _LOG_TRACE, 3, "Drop duplicated DVB-C Channel:%d; onid:%d tsid:%d sid:%d name:%s type:%d.\r\n",
				channel, channel_list->channel[i].u.dvb.onid, channel_list->channel[i].u.dvb.tsid,
				channel_list->channel[i].u.dvb.sid, channel_list->channel[i].u.dvb.name, channel_list->channel[i].u.dvb.service_type ));
				break;	
			}
		}
		if ( duplicated ) continue;

		//add a new channel to the channel table
		j = Channel->FreqTable.entryNum;
		if ( j>= Channel->FreqTable.totalNum ) 
		{
			SageLog(( _LOG_TRACE, 3, "Expending tuning table (%d %d)\r\n", j, Channel->FreqTable.totalNum );
				GrowFreqTable( Channel ));
		}
		Channel->FreqTable.tbl.dvbc[j].index = j+1;
		Channel->FreqTable.tbl.dvbc[j].onid = channel_list->channel[i].u.dvb.onid;
		Channel->FreqTable.tbl.dvbc[j].tsid = channel_list->channel[i].u.dvb.tsid;
		Channel->FreqTable.tbl.dvbc[j].sid  = channel_list->channel[i].u.dvb.sid;
		Channel->FreqTable.tbl.dvbc[j].physical_ch = channel_list->channel[i].u.dvb.sid;
		
		Channel->FreqTable.tbl.dvbc[j].frequency = DVBCFreq->frequency;
		Channel->FreqTable.tbl.dvbc[j].symbol_rate = DVBCFreq->symbol_rate;
		Channel->FreqTable.tbl.dvbc[j].modulation = DVBCFreq->modulation;
		Channel->FreqTable.tbl.dvbc[j].fec_inner_rate = DVBCFreq->fec_inner_rate;

		Channel->FreqTable.tbl.dvbc[j].ctrl = 1;
		encrypted = 0;
		if ( channel_list->channel[i].u.dvb.ca ) 
		{
			Channel->FreqTable.tbl.dvbc[j].ctrl |= 0x02;
			encrypted = 1;
		}

		Channel->FreqTable.entryNum++;
		strncpy( Channel->FreqTable.tbl.dvbc[j].name, channel_list->channel[i].u.dvb.name, 
			                                      sizeof(Channel->FreqTable.tbl.dvbc[j].name) );
		found = true;
		pos += SPRINTF( channels_buf+pos, channels_size-pos, "%d-%d-%d-%d(%s)DVB-C%s%s;", 
			channel_list->channel[i].u.dvb.onid, channel_list->channel[i].u.dvb.tsid,
			channel_list->channel[i].u.dvb.sid, 
			Channel->FreqTable.tbl.dvbc[j].index,
			channel_list->channel[i].u.dvb.name, encrypted?"-ENC":"" ,
			channel_list->channel[i].u.dvb.service_type == 2?"":"" ); 
	
	} 

	{   //retrevie NIT table to update DVBC scan table
		TUNE_LIST* pTuneList;

		if ( SageTVScanTuneList( Channel->Dev, (void*)&pTuneList ) )
		{
			int k;
			SageLog(( _LOG_TRACE, 3, "Get a Nit, total tuner entry in table:%d\r\n", pTuneList->tune_num ) );
			for ( i = 0; i<pTuneList->tune_num; i++ )
			{
				if ( IsInDVBPreDefinedFreqTable( Channel->PreDefinedFreqTable, pTuneList->tune[i].u.s.freq ) )
					continue;

				k = Channel->PreDefinedFreqTable->entryNum;
				if ( Channel->PreDefinedFreqTable->entryNum >= Channel->PreDefinedFreqTable->totalNum ) 
					GrowPredefineFreqTable( Channel );
				assert( k<Channel->PreDefinedFreqTable->totalNum );
				Channel->PreDefinedFreqTable->tbl.dvbc[k].frequency    = pTuneList->tune[i].u.c.freq;
				Channel->PreDefinedFreqTable->tbl.dvbc[k].symbol_rate  = pTuneList->tune[i].u.c.symbol_rate;
				Channel->PreDefinedFreqTable->tbl.dvbc[k].modulation   = pTuneList->tune[i].u.c.modulation;
				Channel->PreDefinedFreqTable->tbl.dvbc[k].fec_inner_rate = pTuneList->tune[i].u.s.fec_rate;
				Channel->PreDefinedFreqTable->tbl.dvbc[k].fec_inner = 0xff; 
				Channel->PreDefinedFreqTable->tbl.dvbc[k].fec_inner_rate = 0xff;
				Channel->PreDefinedFreqTable->tbl.dvbc[k].fec_outer = 0xff;
				Channel->PreDefinedFreqTable->tbl.dvbc[k].fec_outer_rate = 0xff;
				SageLog(( _LOG_TRACE, 3, "add a scan entry %d scan entry frq:%ld  \r\n", k, pTuneList->tune[i].u.s.freq	 ));
				Channel->PreDefinedFreqTable->entryNum++;
			}
				
		}
	} 



	SageStopParser( Channel->Dev );
	//SageTVReleaseScanChannel( Channel->Dev );
	if ( found ) 
	{
		saveScanTuningTable( Channel, "frq" );
//		pos += SPRINTF( channels_buf+pos, channels_size-pos, "\r\n" );
	}
	SageLog(( _LOG_TRACE, 3, "DVB-C Scan Info:%s %d.\r\n", *scanResult, found ));

	return 1;
}

int scanDVBSChannel( CHANNEL_DATA *Channel, char* tuningString, char** scanResult )
{
	int i, j, ret;
	int channel;
	int freq;
	bool found = false;
	int sat_no;
	DVB_S_FREQ *DVBSFreq;
	TUNE tune={0};
	CHANNEL_LIST *channel_list;
	char *channels_buf;
	int   channels_size;
	int   channel_num, pos;
	int   encrypted;
	int   atsc_in_dvb;
	
	*scanResult = NULL;

	channel = atoi( tuningString );

	if ( channel == 0 )
		return 1;

	if ( channel < 0 )
	{
		return 0;
	}

	//use scan data to override scanning
	if ( Channel->scanType == 1 )
	{
		int j, k, pos;
		if ( Channel == NULL || Channel->PreDefinedFreqTable == NULL )
		{
			SageLog(( _LOG_TRACE, 3, "Scan data entry empty.\r\n" ));
			return 1;
		}

		*scanResult = (char*)malloc( 64*12 );
		memset( *scanResult, 0, 64*12 );
		pos = 0;
		for ( k = (channel-1)*12; k<channel*12; k++ )
		{
			for ( i = 0; i<=Channel->PreDefinedFreqTable->entryNum; i++ )
				if ( Channel->PreDefinedFreqTable->tbl.dvbs[i].index == k )	break;

			if ( i > Channel->PreDefinedFreqTable->entryNum )
			{
				SageLog(( _LOG_TRACE, 3, "Scan data entry is not found for channel:%d.\r\n", channel ));
				continue;
			}

			j = Channel->FreqTable.entryNum;
			if ( j>= Channel->FreqTable.totalNum ) GrowFreqTable( Channel );
			Channel->FreqTable.tbl.dvbs[j] = Channel->PreDefinedFreqTable->tbl.dvbs[i] ;
			Channel->FreqTable.tbl.dvbs[j].ctrl = 1;
			Channel->FreqTable.tbl.dvbs[j].index = j+1;

			pos += SPRINTF( *scanResult+pos, 64, "%d-%d-%d-%d(%s)DVB-S;",  Channel->FreqTable.tbl.dvbs[j].onid,
				Channel->FreqTable.tbl.dvbs[j].tsid,    Channel->FreqTable.tbl.dvbs[j].sid,
				Channel->FreqTable.tbl.dvbs[j].index,   Channel->FreqTable.tbl.dvbs[j].name );

			Channel->FreqTable.entryNum++;
		}
		saveScanTuningTable( Channel, "frq" );
		SageLog(( _LOG_TRACE, 3, "DVB-S Scan Info:%s..\r\n", *scanResult ));
		return 1;
	}

	if ( Channel == NULL || Channel->PreDefinedFreqTable == NULL || 
	     Channel->PreDefinedFreqTable->tbl.dvbt == NULL || Channel->PreDefinedFreqTable->entryNum == 0 )
	{
		SageLog(( _LOG_TRACE, 3, "Scan DVB-S is not initialized\r\n" ));
		return -2;
	}
	
	if ( Channel->PreDefinedFreqTable->entryNum < channel )
	{
		SageLog(( _LOG_TRACE, 3, "End of scan data (%d)\r\n", Channel->PreDefinedFreqTable->entryNum ));
		return -2;
	}
	
	DVBSFreq = &Channel->PreDefinedFreqTable->tbl.dvbs[ channel-1 ];
	if ( DVBSFreq->frequency == 0 )
	{
		SageLog(( _LOG_TRACE, 3, "invalid frquency (frq=0).\r\n"));
		return -2;
	}

	if (  ( freq = SageTuneDVBSFrequency( Channel->Dev, DVBSFreq, 0 ) )< 0 )
		return -1;

	ret = WaiteScanDone( Channel );
	if ( ret < 0 )
		SageStopParser( Channel->Dev );

	//scan channel on a stream
	tune.stream_format = DVB_STREAM;
	tune.sub_format    = SATELLITE;
	tune.u.dvb.dvb_type = SATELLITE;
	tune.u.dvb.dvb.s.freq = DVBSFreq->frequency;
	tune.u.dvb.dvb.s.modulation = DVBSFreq->modulation;
	sat_no = DVBSFreq->sat_no;
	SageTVScanChannel( Channel->Dev, &tune );
	SageStartParser( Channel->Dev );

	//wait scan data ready
	ret = WaiteScanDone( Channel );	
	if ( ret < 0 )
		SageStopParser( Channel->Dev );

	//get scan channel list
	ret = SageTVScanChannelList( Channel->Dev, (void**)&channel_list );

	//count valid channel number
	channel_num = 0;
	for ( i = 0; i<channel_list->channel_num; i++ )
		if ( channel_list->channel[i].state  ) channel_num++;

	//alloc result buffer 
	channels_size = channel_num*50+2;
	channels_buf = (char*)malloc ( channels_size );
	memset( channels_buf, 0, channels_size );
	*scanResult = channels_buf;
	pos = 0;

	//An ATSC stream is carried in DVB-S 
	atsc_in_dvb = 0; 
	if ( channel_list->stream_format == ATSC_STREAM )
	{
		int k;
		uint16_t major, minor, phy;
		char name[32];
		SageLog(( _LOG_TRACE, 3, "found ATSC channels in DVB-S stream\r\n" ));
		//do conversion from ATSC to DVB 
		for ( k = 0; k<channel_list->channel_num; k++ )
		{
			phy   = channel_list->channel[k].u.atsc.physical_ch;
			major = channel_list->channel[k].u.atsc.major_num;
			minor = channel_list->channel[k].u.atsc.minor_num;
			strncpy( name, channel_list->channel[k].u.atsc.name, sizeof(name) );
			channel_list->channel[k].u.dvb.onid = phy;
			channel_list->channel[k].u.dvb.tsid = major;
			channel_list->channel[k].u.dvb.sid  = minor;
			strncpy( channel_list->channel[k].u.dvb.name, name, sizeof(channel_list->channel[k].u.dvb.name) );
		}
	}

	for ( i = 0; i<channel_list->channel_num; i++ )
	{ 
		int duplicated = false;
		if ( channel_list->channel[i].state == 0 )
			continue;

		SageLog(( _LOG_TRACE, 3, "found DVB-S Channel:%d; onid:%d tsid:%d sid:%d frq:%ld name:%s type:%d.\r\n",
				channel, channel_list->channel[i].u.dvb.onid, channel_list->channel[i].u.dvb.tsid,
				channel_list->channel[i].u.dvb.sid,	
				channel_list->channel[i].u.dvb.dvb.s.freq,
				channel_list->channel[i].u.dvb.name, channel_list->channel[i].u.dvb.service_type  ));

		//check duplicated channel
		for ( j = 0; j<Channel->FreqTable.entryNum; j++ )
		{
			if ( Channel->FreqTable.tbl.dvbs[j].tsid == channel_list->channel[i].u.dvb.onid &&
				 Channel->FreqTable.tbl.dvbs[j].tsid == channel_list->channel[i].u.dvb.tsid &&
				 Channel->FreqTable.tbl.dvbs[j].sid  == channel_list->channel[i].u.dvb.sid )
			{
				duplicated = true;
				SageLog(( _LOG_TRACE, 3, "Drop duplicated DVB-S Channel:%d; onid:%d tsid:%d sid:%d name:%s type:%d.\r\n",
				channel, channel_list->channel[i].u.dvb.onid, channel_list->channel[i].u.dvb.tsid,
				channel_list->channel[i].u.dvb.sid, channel_list->channel[i].u.dvb.name, channel_list->channel[i].u.dvb.service_type ));
				break;	
			}
		}
		if ( duplicated ) continue;

		//add a new channel to the channel table
		j = Channel->FreqTable.entryNum;
		if ( j>= Channel->FreqTable.totalNum ) 
		{
			SageLog(( _LOG_TRACE, 3, "Expending tuning table (%d %d)\r\n", j, Channel->FreqTable.totalNum );
				GrowFreqTable( Channel ));
		}
		Channel->FreqTable.tbl.dvbs[j].index = j+1;
		Channel->FreqTable.tbl.dvbs[j].onid = channel_list->channel[i].u.dvb.onid;
		Channel->FreqTable.tbl.dvbs[j].tsid = channel_list->channel[i].u.dvb.tsid;
		Channel->FreqTable.tbl.dvbs[j].sid  = channel_list->channel[i].u.dvb.sid;
		Channel->FreqTable.tbl.dvbs[j].physical_ch = channel_list->channel[i].u.dvb.sid;
		
		Channel->FreqTable.tbl.dvbs[j].frequency = DVBSFreq->frequency;
		Channel->FreqTable.tbl.dvbs[j].symbol_rate = DVBSFreq->symbol_rate;
		Channel->FreqTable.tbl.dvbs[j].modulation = DVBSFreq->modulation;
		Channel->FreqTable.tbl.dvbs[j].polarisation = DVBSFreq->polarisation;
		Channel->FreqTable.tbl.dvbs[j].fec_inner = DVBSFreq->fec_inner;
		Channel->FreqTable.tbl.dvbs[j].fec_inner_rate = DVBSFreq->fec_inner_rate;
		Channel->FreqTable.tbl.dvbs[j].fec_outer = DVBSFreq->fec_outer;
		Channel->FreqTable.tbl.dvbs[j].fec_outer_rate = DVBSFreq->fec_outer_rate;
		Channel->FreqTable.tbl.dvbs[j].roll = DVBSFreq->roll;
		Channel->FreqTable.tbl.dvbs[j].pilot = DVBSFreq->pilot;
		Channel->FreqTable.tbl.dvbs[j].sat_no = DVBSFreq->sat_no;
		//Channel->FreqTable.tbl.dvbs[j].video_pid = 0;
		//Channel->FreqTable.tbl.dvbs[j].audio_pid = 0;

		Channel->FreqTable.tbl.dvbs[j].ctrl = 1;
		encrypted = 0;
		if ( channel_list->channel[i].u.dvb.ca ) 
		{
			Channel->FreqTable.tbl.dvbs[j].ctrl |= 0x02;
			encrypted = 1;
		} 
		if ( atsc_in_dvb )
			Channel->FreqTable.tbl.dvbs[j].ctrl |= 0x08;

		Channel->FreqTable.entryNum++;
		strncpy( Channel->FreqTable.tbl.dvbs[j].name, channel_list->channel[i].u.dvb.name, 
			                                      sizeof(Channel->FreqTable.tbl.dvbs[j].name) );
		found = true;
		pos += SPRINTF( channels_buf+pos, channels_size-pos, "%d-%d-%d-%d(%s)DVB-S%s%s;", 
			channel_list->channel[i].u.dvb.onid, channel_list->channel[i].u.dvb.tsid,
			channel_list->channel[i].u.dvb.sid, Channel->FreqTable.tbl.dvbs[j].index,
			channel_list->channel[i].u.dvb.name, encrypted?"-ENC":"" , 
			channel_list->channel[i].u.dvb.service_type == 2?"":"" ); 
	
	} 

	{   //retrevie NIT table to update DVBS scan table
		TUNE_LIST* pTuneList;
		if ( SageTVScanTuneList( Channel->Dev, (void*)&pTuneList ) )
		{
			int k;
			SageLog(( _LOG_TRACE, 3, "Get a Nit, total tuner entry in table:%d\r\n", pTuneList->tune_num ) );
			for ( i = 0; i<pTuneList->tune_num; i++ )
			{
				if ( IsInDVBPreDefinedFreqTable( Channel->PreDefinedFreqTable, pTuneList->tune[i].u.s.freq ) )
					continue;
				k = Channel->PreDefinedFreqTable->entryNum;
				if ( Channel->PreDefinedFreqTable->entryNum >= Channel->PreDefinedFreqTable->totalNum) 
					GrowPredefineFreqTable( Channel );
				Channel->PreDefinedFreqTable->tbl.dvbs[k].frequency    = pTuneList->tune[i].u.s.freq;
				Channel->PreDefinedFreqTable->tbl.dvbs[k].symbol_rate  = pTuneList->tune[i].u.s.symbol_rate;
				Channel->PreDefinedFreqTable->tbl.dvbs[k].polarisation = pTuneList->tune[i].u.s.pol;
				Channel->PreDefinedFreqTable->tbl.dvbs[k].modulation   = pTuneList->tune[i].u.s.modulation;
				Channel->PreDefinedFreqTable->tbl.dvbs[k].fec_inner_rate = pTuneList->tune[i].u.s.fec_rate;
				Channel->PreDefinedFreqTable->tbl.dvbs[k].fec_inner = 0x0;
				Channel->PreDefinedFreqTable->tbl.dvbs[k].fec_outer = 0x0;
				Channel->PreDefinedFreqTable->tbl.dvbs[k].fec_outer_rate = 0x0;
				Channel->PreDefinedFreqTable->tbl.dvbs[k].sat_no = sat_no;
				SageLog(( _LOG_TRACE, 3, "add a scan entry %d scan entry sat_no:0x%x frq:%ld  \r\n", k,
															sat_no, pTuneList->tune[i].u.s.freq	 ));
				Channel->PreDefinedFreqTable->entryNum++;
			}
		}
	} 



	SageStopParser( Channel->Dev );
	//SageTVReleaseScanChannel( Channel->Dev );
	if ( found ) 
	{
		saveScanTuningTable( Channel, "frq" );
//		pos += SPRINTF( channels_buf+pos, channels_size-pos, "\r\n" );
	}
	SageLog(( _LOG_TRACE, 3, "DVB-S Scan Info:%s %d.\r\n", *scanResult, found ));

	return 1;
}


//minimize scan waiting time
static int WaiteScanDone( CHANNEL_DATA *Channel )	//wait scan data ready
{
	int inprogress_ct=0, low_SN_ratio_ct=0, no_data_ct=0;
	int inprogress_set=0;//, low_SN_ratio_set=0, no_data_set=0;
	int count=0, timeout_count, max_count;
	int scan_state, scan_channel_num, last_scan_channel_num = 0;
	timeout_count = Channel->lockTimeout;
	max_count = LOCK_FREQ_TIMEOUT * 40 ; /* 40 seconds */
	while( 1 )
	{
		SageTVScanChannelState( Channel->Dev, &scan_state, &scan_channel_num, count );
		if ( scan_state == 0 )
		{ //in progress, no channel found
			inprogress_ct++;
		} else
		if ( scan_state == -2 )
		{  //low SN signa/nosise
			low_SN_ratio_ct++;
		}
		else
		if ( scan_state == -1 )
		{	//no data incoming between two state check
			no_data_ct++;
		} else
		if ( scan_state > 0 )
		{
			SageLog(( _LOG_TRACE, 3, "Channel scan done.\r\n" ));
			break;
		} 

		if ( count > timeout_count )
			break;

		if ( count > max_count )
			break;

		if ( inprogress_ct > 8 && !inprogress_set )
		{
			//if there are data, we need me time
			timeout_count += Channel->lockTimeout/2;
			inprogress_set = 1;
		}

		if ( last_scan_channel_num != scan_channel_num )
		{
			timeout_count += Channel->lockTimeout;
			last_scan_channel_num = scan_channel_num;
		}
		count++;
		usleep( SLEEP_100MS ); /* 0.1 second*/
	}
	
	SageLog(( _LOG_TRACE, 3, "Channel scan state. count:%d/%d (data:%d,low SN:%d,no data:%d) ret:%d %s.\r\n",
		   timeout_count, Channel->lockTimeout, inprogress_ct, low_SN_ratio_ct,  no_data_ct, scan_state, scan_state<=0?"timeout":"done" ));

	return 1;
}

static int IsInDVBPreDefinedFreqTable( FREQTBL* pPreDefinedFreqTable, uint32_t lFreq )
{
	int i;
	if ( !strcmp( pPreDefinedFreqTable->sourceType, "DVB-S" ) )
	{
		for ( i = 0; i<pPreDefinedFreqTable->entryNum; i++ )
			if ( pPreDefinedFreqTable->tbl.dvbs[i].frequency == lFreq )
				return 1;
	} else
	if ( !strcmp( pPreDefinedFreqTable->sourceType, "DVB-C" ) )
	{
		for ( i = 0; i<pPreDefinedFreqTable->entryNum; i++ )
			if ( pPreDefinedFreqTable->tbl.dvbc[i].frequency == lFreq )
				return 1;
	} else
	if ( !strcmp( pPreDefinedFreqTable->sourceType, "DVB-T" ) )
	{
		for ( i = 0; i<pPreDefinedFreqTable->entryNum; i++ )
			if ( pPreDefinedFreqTable->tbl.dvbt[i].frequency == lFreq )
				return 1;
	} else
	{
		return 1;
	}
	return 0;
}

static int _CopyFile( char* src, char *tar )
{
	FILE *fs, *ft;
	char buf[512];
	int n, sum=0;
	fs = fopen( src, "rb" );
	if ( fs == NULL )
		return -1;
	ft = fopen( tar, "wb" );
	if ( ft == NULL )
	{
		fclose( fs );
		return -2;
	}
	
	while ( !feof( fs ) )
	{
		n = fread( buf, 1, sizeof(buf), fs );
		if ( n == 0 ) break;
		sum += fwrite( buf, 1, n, ft );
	}
	fclose( fs );
	fclose( ft );
	return sum;
}
