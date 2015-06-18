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

#ifndef PSIFILTER_H
#define PSIFILTER_H

#include "SectionData.h"
#include "TSFilter.h"
#include "TSParser.h"

#ifdef __cplusplus
extern "C" {
#endif

#define MAX_ATSC_CHANNEL	100
#define MAX_DVB_CHANNEL		100
#define MAX_TUNE_NUM		64
#define UNKNOWN_FORMAT	0

typedef struct {
	unsigned long language_code;
	unsigned char *charset_code;
	DESC_DATA data;
} STRING;

typedef struct {
	unsigned long  language_code;
	unsigned char  *charset_code;
	unsigned short bytes;
	unsigned char  data[256];
} STRING256;

typedef struct {
	unsigned long  language_code;
	unsigned char  *charset_code;
	unsigned short bytes;
	unsigned char  data[128];
} STRING128;

typedef struct {
	unsigned long  language_code;
	unsigned char  *charset_code;
	unsigned short title_bytes;
	unsigned char  title_data[256];
	unsigned short body_bytes;
	unsigned char  body_data[256];
} MESG256;

typedef struct {
	unsigned long language_code;
	unsigned char *charset_code;
	DESC_DATA	  title;
	DESC_DATA	  body;
} MESG;


////////////////////////////CHANNEL SECTINON/////////////////////////////////////////////

typedef struct CHANNEL_DATA
{
	unsigned char  stream_format;  //ATSC, DVB
	unsigned char  sub_format;     //T,S,C
	unsigned short num_channel;    //number of channel
	union {
		ATSC_CHANNEL atsc[MAX_ATSC_CHANNEL];
		DVB_CHANNEL  dvb[MAX_DVB_CHANNEL];
	} u ;
	struct TS_STREAMS *streams;   //optional
	unsigned char  update_flag;   //channel table updated flag;
	unsigned long  command;       //command return from dumper, save channel (command:1) or drop it (command:0).
} CHANNEL_DATA;

////////////////////////////TUNE SECTINON/////////////////////////////////////////////

typedef struct ATSC_TUNE
{
	unsigned short major;
	unsigned short minor;
	unsigned short sid;
	unsigned short atsc_type; //1:ATSC 2:QAM
	union {
		TERRESTRIAL_TUNE atsc;
		CABLE_TUNE		 qam;
	} u;
	char name[10*2];
} ATSC_TUNE;


typedef struct DVB_TUNE
{
	unsigned short onid;
	unsigned short tsid;
	unsigned short sid;
	unsigned short nid;
	unsigned short dvb_type; //1:terrestrial, 2:cable, 3:satellite
	union {
		TERRESTRIAL_TUNE t;
		CABLE_TUNE		 c;
		SATELLITE_TUNE   s;
	} dvb;
	char name[10*2];
} DVB_TUNE;


typedef struct TUNE_DATA
{
	unsigned char  stream_format;  //ATSC, DVB
	unsigned char  sub_format;     //T,C,S, QAM 
	unsigned short num_tune;       //number of tune entry
	union {
		ATSC_TUNE   atsc;
		DVB_TUNE	dvb;
	} u;

	struct {
		unsigned short sid;
		unsigned short type;
	} s[MAX_TUNE_NUM] ;

	unsigned short update_flag;   //channel table updated flag;
	unsigned long  command;       //command return from dumper, save channel (command:1) or drop it (command:0).
} TUNE_DATA;


typedef struct TIME_DATA
{
	unsigned long gps_sec;
	unsigned long utc_sec;

} TIME_DATA;

typedef struct PSI_PARSER_DUMPER
{
	DUMP  system_time_dumper;
	void* system_time_context;

	DUMP  channel_info_dumper;  //channel infoamtion
	void* channel_info_context;

	DUMP  tune_info_dumper;     //NIT of DVB, QAM of ATSC
	void* tune_info_context;  

	DUMP  epg_dumper;
	void* epg_dumper_context;

	DUMP  message_dumper;       //language, format
	void* message_context;  
  
	DUMP  pid_dumper;       //pid dump
	void* pid_context;  

} PSI_PARSER_DUMPER;

typedef struct STREAM_DETECT
{
	TS_SECTION     *tsdt_section;
	TS_SECTION     *nit_section;
	TS_SECTION     *sdt_section;
	TS_SECTION     *eit_section;
	TS_SECTION     *rst_section;
	TS_SECTION     *tdt_section;
	TS_SECTION     *rnt_section;
	TS_SECTION     *dit_section;
	TS_SECTION     *sit_section;
	TS_SECTION     *psip_section;
	TS_SECTION     *mip_section;
	TS_SECTION     *scte_section;
	unsigned char tsdt_count,nit_count,sdt_count,eit_count, rst_count,tdt_count,mip_count,rnt_count,dit_count,sit_count;
	unsigned char pat_e_count, stt_e_count, opera_count,psip_count, scte_count;
	unsigned short scte_flag;
} STREAM_DETECT;

typedef struct PSI_PARSER
{
	int stream_format; //1:ATSC; 2:DVB
	int sub_format;    //ATSC

	struct DVB_PSI  *dvb_psi;
	struct ATSC_PSI *atsc_psi;

	PSI_PARSER_DUMPER dumper;

	TS_FILTER* ts_filter;

	unsigned long language_code;

    //statistic
	int atsc_packet_count, dvb_packet_count;
	STREAM_DETECT *stream_detect;
	unsigned long packet_count;
} PSI_PARSER;


PSI_PARSER* CreatePSIParser( TS_FILTER* pTSFilter, int nFormat, int nSubFormat );
void  ReleasePSIParser( PSI_PARSER* pPSIParser );
void  ResetPSIParser( PSI_PARSER* pPSIParser );
char* StreamFormatString( int format, int sub_format );
void  SetLanguage( PSI_PARSER* pPSIParser, unsigned long nLanguageCode );
int   ParseTSPSI( TS_FILTER* pTSFilter, TS_PACKET *pTSPacket );
void  SetStreamFormat( PSI_PARSER* pPSIParser, int nFormat, int nSubFormat );


void ReleaseString( STRING* pString );
void ReleaseMesg( MESG* pMsg );

char* Language( unsigned long lLanguageCode, char* pBuf );
unsigned long LanguageCode( unsigned char* pLanguage );

char* UTCFormat( unsigned long t, char* p, int len );

unsigned char* GetDescriptor( const unsigned char *pData, int Bytes, unsigned char Tag, int *pLength );
int TransJWideString( char* pBufOut, int nBufOutSize, unsigned short* pBufIn );


char* _descriptor_check( unsigned char* p, int bytes );

#ifdef __cplusplus
}
#endif

#endif
