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
#ifndef  ATSCPSI_HEADER
#define  ATSCPSI_HEADER

#ifdef __cplusplus
extern "C" {
#endif


//#define MAX_TSID_NUM		6
#define MAX_MGT_TBL			370
#define MAX_VCT_TBL			370
#define MAX_EIT_NUM			(0x180-0x100)
#define MAX_EPG_NUM_PER_CH  24*3*2
#define	MAX_RTT_TBL			7
#define MAX_GENRE_NUM		32

//#define MAX_CHANNEL			MAX_VCT_TBL

typedef struct 
{
	unsigned short type;
	unsigned short pid;
	unsigned long  tbl_size;
} MGT;

typedef struct 
{
	unsigned short flag;
	char name[9*2];
	//char extended_name[32];
	unsigned short major_num;
	unsigned short minor_num;
	unsigned short tsid;
	unsigned short program_id;
	unsigned short service_type;
	unsigned short source_id;
	unsigned short modulation;
	unsigned long  freq;

} VCT;

typedef struct {
	char  dimension_name[20];
	int   graduate_scale;
	unsigned char rating_num;
	char  abbrev_name[16][8];
	char  rating_value[16][150];
} RATING;

typedef struct {
	unsigned short rating_region;
	char     region_name[32];
	unsigned char dimension_num;
	RATING   *rating;
} RTT;

#define MAX_CAD_RIGION_NUM 4
typedef struct {
	unsigned char region_num;

	unsigned char rating_region[MAX_CAD_RIGION_NUM];
	unsigned char rating_num[MAX_CAD_RIGION_NUM];
	unsigned char rating_index[MAX_CAD_RIGION_NUM][16];
	unsigned char value_index[MAX_CAD_RIGION_NUM][16];
	STRING256     description[MAX_CAD_RIGION_NUM];
} CAD; //content_advisory_descriptor

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
	DESC_DATA	   title;
	DESC_DATA	   program;
	unsigned long  program_crc32;
	DESC_DATA	   cad;
	unsigned char  genre[MAX_GENRE_NUM];
	int 	       need_updated;
} EIT;

typedef struct 
{
	int eit_num;
	EIT eit[MAX_EPG_NUM_PER_CH];
} EIT_COL;


typedef struct ATSC_PSI
{
	TS_SECTION     *atsc_psi_section;

	unsigned long  system_time;
	unsigned short mgt_num;
	MGT			   mgt[MAX_MGT_TBL];
	unsigned long  mgt_section_crc32; //for checking mgt updating
	unsigned char  mgt_update_flag;

	unsigned short vct_num;
	unsigned short vct_program;
	VCT			   vct[MAX_VCT_TBL];
	unsigned long  vct_section_crc32; ////for checking vct updating
	unsigned char  vct_update_flag;


	RTT			   rtt[MAX_RTT_TBL];
	unsigned long  rtt_section_crc32; ////for checking vct updating
	unsigned char  rtt_update_flag;

	unsigned short eit_blk_num;
	EIT_COL		   *eit_blk;
	unsigned long  eit_section_crc32[MAX_EIT_NUM]; ////for checking eit updating
	unsigned char  eit_update_flag;

	unsigned long  ett_section_crc32[MAX_EIT_NUM+1]; ////for checking ett updating
	unsigned char  ett_update_flag;

	unsigned long  language_code;
	struct PSI_PARSER*	psi_parser;

	unsigned char not_save_epg_message;
	unsigned char disable_channel_inf;

	TS_STREAMS     streams; //passing channel streams information in VCT to TSFilter

} ATSC_PSI;

ATSC_PSI* CreateATSCPSI(  );
void ReleaseATSCPSI( ATSC_PSI* pATSCPSI );
void ResetATSCPSI( ATSC_PSI* pATSCPSI );
int  ProcessATSCPSI( ATSC_PSI* pATSCPSI, TS_PACKET *pTSPacket );

void ReleaseString( STRING* pString );
unsigned char* NewDescData( DESC_DATA* pDesc, int nBytes );
unsigned char* FillDescData( DESC_DATA *pDesc, unsigned char* pData, int nBytes );
void EraseDescData( DESC_DATA *pDesc );
void ReleaseDescData( DESC_DATA *pDesc );

char* _descriptor_check( unsigned char* p, int bytes );

#ifdef __cplusplus
extern "C" }
#endif

#endif

