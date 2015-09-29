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
	uint32_t language_code;
	uint8_t  *charset_code;
	DESC_DATA data;
} STRING;

typedef struct {
	uint32_t language_code;
	uint8_t  *charset_code;
	uint16_t bytes;
	uint8_t  data[256];
} STRING256;

typedef struct {
	uint32_t language_code;
	uint8_t  *charset_code;
	uint16_t bytes;
	uint8_t  data[128];
} STRING128;

typedef struct {
	uint32_t language_code;
	uint8_t  *charset_code;
	uint16_t title_bytes;
	uint8_t  title_data[256];
	uint16_t body_bytes;
	uint8_t  body_data[256];
} MESG256;

typedef struct {
	uint32_t language_code;
	uint8_t  *charset_code;
	DESC_DATA	  title;
	DESC_DATA	  body;
} MESG;


////////////////////////////CHANNEL SECTINON/////////////////////////////////////////////

typedef struct CHANNEL_DATA
{
	uint8_t  stream_format;  //ATSC, DVB
	uint8_t  sub_format;     //T,S,C
	uint16_t num_channel;    //number of channel
	union {
		ATSC_CHANNEL atsc[MAX_ATSC_CHANNEL];
		DVB_CHANNEL  dvb[MAX_DVB_CHANNEL];
	} u ;
	struct TS_STREAMS *streams;   //optional
	uint8_t  update_flag;   //channel table updated flag;
	uint32_t  command;       //command return from dumper, save channel (command:1) or drop it (command:0).
} CHANNEL_DATA;

////////////////////////////TUNE SECTINON/////////////////////////////////////////////

typedef struct ATSC_TUNE
{
	uint16_t major;
	uint16_t minor;
	uint16_t sid;
	uint16_t atsc_type; //1:ATSC 2:QAM
	union {
		TERRESTRIAL_TUNE atsc;
		CABLE_TUNE		 qam;
	} u;
	char name[10*2];
} ATSC_TUNE;


typedef struct DVB_TUNE
{
	uint16_t onid;
	uint16_t tsid;
	uint16_t sid;
	uint16_t nid;
	uint16_t dvb_type; //1:terrestrial, 2:cable, 3:satellite
	union {
		TERRESTRIAL_TUNE t;
		CABLE_TUNE		 c;
		SATELLITE_TUNE   s;
	} dvb;
	char name[10*2];
} DVB_TUNE;


typedef struct TUNE_DATA
{
	uint8_t  stream_format;  //ATSC, DVB
	uint8_t  sub_format;     //T,C,S, QAM 
	uint16_t num_tune;       //number of tune entry
	union {
		ATSC_TUNE   atsc;
		DVB_TUNE	dvb;
	} u;

	struct {
		uint16_t sid;
		uint16_t type;
	} s[MAX_TUNE_NUM] ;

	uint16_t update_flag;   //channel table updated flag;
	uint32_t  command;       //command return from dumper, save channel (command:1) or drop it (command:0).
} TUNE_DATA;


typedef struct TIME_DATA
{
	uint32_t gps_sec;
	uint32_t utc_sec;

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
	uint8_t tsdt_count,nit_count,sdt_count,eit_count, rst_count,tdt_count,mip_count,rnt_count,dit_count,sit_count;
	uint8_t pat_e_count, stt_e_count, opera_count,psip_count, scte_count;
	uint16_t scte_flag;
} STREAM_DETECT;

typedef struct PSI_PARSER
{
	int32_t stream_format; //1:ATSC; 2:DVB
	int32_t sub_format;    //ATSC

	struct DVB_PSI  *dvb_psi;
	struct ATSC_PSI *atsc_psi;

	PSI_PARSER_DUMPER dumper;

	TS_FILTER* ts_filter;

	uint32_t language_code;

    //statistic
	int atsc_packet_count, dvb_packet_count;
	STREAM_DETECT *stream_detect;
	uint32_t packet_count;
} PSI_PARSER;


PSI_PARSER* CreatePSIParser( TS_FILTER* pTSFilter, int nFormat, int nSubFormat );
void  ReleasePSIParser( PSI_PARSER* pPSIParser );
void  ResetPSIParser( PSI_PARSER* pPSIParser );
char* StreamFormatString( int format, int sub_format );
void  SetLanguage( PSI_PARSER* pPSIParser, uint32_t nLanguageCode );
int   ParseTSPSI( TS_FILTER* pTSFilter, TS_PACKET *pTSPacket );
void  SetStreamFormat( PSI_PARSER* pPSIParser, int nFormat, int nSubFormat );


void ReleaseString( STRING* pString );
void ReleaseMesg( MESG* pMsg );

char* Language( uint32_t lLanguageCode, char* pBuf );
uint32_t LanguageCode( uint8_t* pLanguage );

char* UTCFormat( uint32_t t, char* p, int len );

uint8_t* GetDescriptor( const uint8_t *pData, int Bytes, uint8_t Tag, int *pLength );
int TransJWideString( char* pBufOut, int nBufOutSize, uint16_t* pBufIn );


char* _descriptor_check( uint8_t* p, int bytes );

#ifdef __cplusplus
}
#endif

#endif
