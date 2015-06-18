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

#if !defined( _TS_NATIVE_SI_PARSER_INCLUDED_ )
#define _TS_NATIVE_SI_PARSER_INCLUDED_

#include "TSParser.h"
#include "TSnative.h"

#define ATSC_TYPE 1
#define DVB_TYPE  2

#ifdef __cplusplus
extern "C" {
#endif

//DVB-T descriptor tag
#define NETWORK_NAME_TAG		0x40
#define SERVICE_LIST_TAG		0x41
#define SERVICE_TAG				0x48
#define TERRESTRIAL_DELIVERY_TAG 0x5a
#define CABLE_DELIVERY_TAG		0x44
#define SATELLITE_DELIVERY_TAG	0x43
#define FREQUENCY_LIST_TAG		0x62
#define CELL_LIST_TAG			0x6c
#define CELL_FREQ_LINK_TAG		0x6d
#define SHORT_EVENT_TAG			0x4d
#define EXTENDED_EVENT_TAG		0x4e
#define DVB_PRENTAL_RATING_TAG	0x55
#define DVB_CONTENT_DESC_TAG	0x54  //genre code

//ATSC descriptor tag
#define ATSC_CONTENT_ADVISORY   0x87
#define ATSC_CHANNEL_NAME		0xa0
#define ATSC_GENRE				0xab

#define EQU_GUID( guid1, guid2 ) ( guid1.a == guid2.a &&  guid1.b == guid2.b &&  guid1.c == guid2.c )

//check memory leaking
#define MAX_HEAP_SIZE	1024*40
typedef struct {
	int   state;
	char* addr;
	long  size;
	int	  line;
} MEM_BLK;


typedef struct 
{
	unsigned char  TableId;
	unsigned short SectionLength;
	unsigned short ProgramNumber;
	unsigned char  VersionNumber;
	bool		   CurrentNextIndicator;
	unsigned char  SectionNum;
	unsigned char  LastSectionNum;
	TS_SECTION     *pSection;
} SECTION_HEADER;

#define SECTION_OFFSET  8
#define MAX_TSID_NUM	6
#define MAX_MGT_TBL   370
#define MAX_VCT_TBL   370
#define MAX_EIT_NUM   (0x180-0x100)
#define MAX_EPG_NUM_PER_CH   30
#define	MAX_RTT_TBL	  7
#define MAX_GENRE_NUM  32

#define MAX_CHANNEL	  MAX_VCT_TBL

#define MAX_DVB_NETOWRK_NUM  256
//#define MAX_SVT_TBL   380
#define MAX_SVT_TBL   580

#define MAX_DEIT_NUM  (4096*3)     //maxuim DVB EPG cell number

typedef struct {
	unsigned short a;
	unsigned short b;
	unsigned long  c;
} EGUID;

typedef struct 
{
	unsigned short flag;
	char Name[8*2];
	unsigned short major_num;
	unsigned short minor_num;
	unsigned short TSID;
	unsigned short program_id;
	unsigned short service_type;
	unsigned short source_id;
	unsigned short modulation;
	unsigned long  freq;
} VCT;

typedef struct 
{
	unsigned short type;
	unsigned short pid;
	unsigned long  tbl_size;
} MGT;

typedef struct 
{
	unsigned char compression_type;
	unsigned char mode;
	unsigned char byte_length;
	unsigned char *mesg;
} SEG;

typedef struct 
{
	unsigned char lanuage_code[4];
	int num_segment;
	SEG *seg_list;
} SEGS;

typedef struct 
{
	char _tag[4];  //debug check 
	int num_string;
	SEGS *string_list;
} MSS;

typedef struct {
	unsigned char rating_region;
	unsigned char rating_num;
	unsigned char rating_index[16];
	unsigned char value_index[16];
	unsigned char description[16];
} CAD_RATING;

typedef struct {
	unsigned char region_num;
	CAD_RATING *rating;
} CAD;

typedef struct 
{
	unsigned short ett_ext_id;
	unsigned short event_id;
	unsigned short source_id;
} ETT;

typedef struct 
{
	unsigned short event_id;
	unsigned short source_id;
	unsigned long  start_time;
	unsigned long  during_length;
	unsigned char  etm_loc;
	unsigned char  title_length;
	unsigned char  program_length;
	MSS*		   title;
	MSS*		   program;
	CAD*		   cad;
	MSS*		   channel_name;
	unsigned char  genre[MAX_GENRE_NUM];
	unsigned long  stample;
	bool	       need_updated;
} EIT;


typedef struct {
	char  dimension_name[20];
	bool  graduate_scale;
	unsigned char rating_num;
	char  abbrev_name[16][8];
	char  rating_value[16][150];
} RATING;

typedef struct {
	unsigned char rating_region;
	char     region_name[32];
	unsigned char dimension_num;
	RATING *rating;
} RTT;

typedef struct 
{
	char   channel[16];
	unsigned long  start_time;
	unsigned long  during_length;
	char*  title_text;
	char*  program_text;
} EPG;

typedef struct {
	unsigned char type;
	char* provider_name;
	char* service_name;
} SERVICE_DATA;

typedef struct {
	unsigned char tag;
	void* data;
}DESCRIPTOR;

typedef struct {
	unsigned short seviceid;
	unsigned char  type;
} DVB_SERVICE;


typedef struct {
	unsigned short service_num;
	DVB_SERVICE*   service;
} DVB_SERVICE_LIST;

typedef struct {
	unsigned long  freq;
	unsigned short band;
	unsigned char  trans_mode;
} TERRESTRIAL_DATA;

typedef struct {
	unsigned long  freq;
	unsigned long  symbol_rate;
	unsigned char  modulation;
	unsigned char  fec_out;
	unsigned char  fec_in_rate;
} CABLE_DATA;

typedef struct {
	unsigned long  freq;
	unsigned long  symbol_rate;
	unsigned char  fec;
	unsigned char  fec_rate;
	unsigned char  pol;
	unsigned char  modulation;
	short orbit;  //positive Esat; negative West
} SATELLITE_DATA;

typedef struct {
	unsigned short TSID;
	unsigned short ONID;
	DVB_SERVICE_LIST  *service_list;
	unsigned char  delivery_type; //1:DVB-T, 2:DVB-C, 3:DVB-S
	union {
		TERRESTRIAL_DATA	t;
		CABLE_DATA			c;
		SATELLITE_DATA		s;
	} dvb;
} NIT_INF;

typedef struct {
	char* network_name;
	unsigned short network_id;
	unsigned short NIT_inf_num;
	unsigned char  type; //1:TERRESTRIAL; 2:CABLE; 3:SATELLITE_DATA
	NIT_INF        NIT_inf[MAX_DVB_NETOWRK_NUM];
} NIT;

typedef struct {
	unsigned short flag;
	unsigned short service_id;
	unsigned char  EIT_flag;
	unsigned char  CA_flag;
	SERVICE_DATA*  service_data;
} SDT_INF;

typedef struct {
	unsigned short TSID;
	unsigned short ONID;
	unsigned short SDT_inf_num;
	SDT_INF  *SDT_inf;
} SDT;

typedef struct {
	unsigned char  lanuage_code[4];
	unsigned short title_length;
	unsigned short mesg_length;
	unsigned char* title;
	unsigned char* message;
	EGUID  guid;
} S_EVENT;


typedef struct {
	unsigned short item_desc_length;
	unsigned char* item_desc;
	unsigned short item_text_length;
	unsigned char* item_text;
} ITEM;

typedef struct {
	unsigned char  lanuage_code[4];
	unsigned short item_no;
	unsigned short item_last_no;
	unsigned short item_number;
	ITEM *items;
	unsigned short text_length;
	unsigned char* text;
} E_EVENT;

typedef struct 
{
	unsigned short flag;
	char Name[40];
	char Provider[40];
	unsigned short ONID;
	unsigned short TSID;
	unsigned short service_id;
	unsigned short service_type;
	unsigned char  delivery_type; //1:DVB-T, 2:DVB-C, 3:DVB-S
	union {
		TERRESTRIAL_DATA	t;
		CABLE_DATA			c;
		SATELLITE_DATA		s;
	} dvb;
} SVT;

typedef struct
{
	unsigned char country[4];
	unsigned char age;
} DVB_RATING;

typedef struct
{
	unsigned short num;
	DVB_RATING*  rating;
} RATING_TBL;

typedef struct 
{
	//unsigned short update_flag;
	unsigned short event_id;
	unsigned short ONID;
	unsigned short TSID;
	unsigned short service_id;
	unsigned long  start_time;
	unsigned long  during_length;
	EGUID		   event_guid;
	S_EVENT		  *event; 
	bool           ext_event_flag;
	E_EVENT		  *ext_event[16]; 
	RATING_TBL    *rating;
	unsigned char *content_desc;
} DEIT;


typedef struct
{
	int flag;
	unsigned short ONID;
	unsigned short TSID;
	unsigned long  counter;
} DVB_CHECK;

typedef struct
{
	int stream_type; //ATSC:1, DVB:2
	int sub_stream_type; //Terrestrial:1; Cable:2; Satelite:3

	unsigned short tsid_tbl[MAX_TSID_NUM];
	LPFNMesgDump   epg_dumper; 
	void*		   epg_context;

	//***** DVB section *****
	TS_SECTION     NITSection;
	unsigned char  NITCountinuty;  //continuty
	NIT			   nit;
	unsigned long  NITSectionCount; 

	TS_SECTION     SDTSection;
	unsigned char  SDTCountinuty;  //continuty
	unsigned long  SDTSectionCount; 

	unsigned short SVTNum;
	SVT			   svt[MAX_SVT_TBL];

	TS_SECTION     DEITSection;
	unsigned char  DEITCountinuty;  //continuty
	DEIT		   deit[MAX_DEIT_NUM];
	unsigned short DEITNum;
	unsigned long  DETISectionCount; 

	unsigned short DVBCheckNum;
	DVB_CHECK      dvb_check[MAX_DVB_NETOWRK_NUM];
	//***** DVB section *****

	//***** ASTC section *****
	TS_SECTION     PSIPSection;
	unsigned char  PSIPCountinuty;  //continuty
	unsigned char  MGTProtocal;
	unsigned short MGTNum;
	MGT			   mgt[MAX_MGT_TBL];
	unsigned char  MGTSectionCount;

	TS_SECTION     VCTSection;
	unsigned char  VCTCountinuty;   //continuty
	unsigned char  VCTProtocal;
	unsigned short VCTNum;
	unsigned short VCTTSID;
	VCT			   vct[MAX_VCT_TBL];
	unsigned long  VCTSectionCount; 


	TS_SECTION     EITSection[MAX_EIT_NUM];
	unsigned char  EITCountinuty[MAX_EIT_NUM];   //continuty
	unsigned char  EITProtocal[MAX_EIT_NUM];
	EIT			   eit[MAX_EIT_NUM][MAX_EPG_NUM_PER_CH];	
	unsigned long  EITSectionCount; 


	TS_SECTION     ETTSection[MAX_EIT_NUM+1];
	unsigned char  ETTCountinuty[MAX_EIT_NUM+1];   //continuty
	unsigned char  ETTProtocal[MAX_EIT_NUM+1];
	unsigned long  ETTSectionCount; 


	TS_SECTION     RTTSection;
	unsigned char  RTTCountinuty;     //continuty
	RTT			   rtt[MAX_RTT_TBL];
	unsigned long  RTTSectionCount; 
	//***** ASTC section *****

	TS_PARSER*	  pTSParser; //reserve
	unsigned long DVB_count;
	unsigned long ATSC_count;

	//DVB data

    //statistic
	unsigned long TSDT_count,NIT_count,SDT_count,EIT_count, RST_count,TDT_count,MIP_count,RNT_count,DIT_count,SIT_count;
	unsigned long PAT_E_count, STT_E_count, OPERA_count,PSIP_count, SCTE_count;

	MEM_BLK* _mem_blk;
	int _mem_blk_usage;

//debug
//unsigned long  SDTSectionCount2;
//unsigned long  SIct, SDTct, NITct,SDTct2, SDTct3,SDTct4; 
} SI_PARSER;

SI_PARSER* SIParerOpen( TS_PARSER* pTSParser, int StreamType );
void SIParserClose( SI_PARSER* pParser );
void SIParserReset( SI_PARSER* pParser );
void SIParserSetStreamType( SI_PARSER* pParser, int type );
int  SIParserGetStreamType( SI_PARSER* pParser );
int  SI_dumper( void* context, unsigned short pid, TS_HEADER* ts_header, char* data, int bytes );
int  GetSIChannelNum( SI_PARSER* pParser);
int  GetSIChannelName( SI_PARSER* pParser, CHANNEL_NAME* pChName, int MaxSize );
int  GetSIProgramName( SI_PARSER* pParser, unsigned short ProgramID, char* pNameBuf, int nNameSize );
bool SIChannelInfoReady( SI_PARSER* pParser );
int  GetSIProgramByName( SI_PARSER* pParser, char* name );
void UnpackSectionHeader( SECTION_HEADER* pSectionHeader, TS_SECTION* pSection );
void SetupEPGDumper( SI_PARSER* pParser, LPFNMesgDump dump, void* context );
int  GetSINetworkNum( SI_PARSER* pParser);
int  GetSINetworkList( SI_PARSER* pParser, char* Buf, int MaxBufSize );
unsigned short GetSIProgramTSID( SI_PARSER* pParser, unsigned short program );
void RegisterTSID( void* context, unsigned short tsid );
int GetSIProgramByMajorMinor( SI_PARSER* pParser, unsigned short major, unsigned short minor );
void GetSIDebugInfo( SI_PARSER* pParser, unsigned short cmd, char* Buf, unsigned long BufSize );

#ifdef __cplusplus
}
#endif

#endif


