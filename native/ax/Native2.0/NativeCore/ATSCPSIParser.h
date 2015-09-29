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
	uint16_t type;
	uint16_t pid;
	uint32_t  tbl_size;
} MGT;

typedef struct 
{
	uint16_t flag;
	char name[9*2];
	//char extended_name[32];
	uint16_t major_num;
	uint16_t minor_num;
	uint16_t tsid;
	uint16_t program_id;
	uint16_t service_type;
	uint16_t source_id;
	uint16_t modulation;
	uint32_t freq;

} VCT;

typedef struct {
	char  dimension_name[20];
	int32_t graduate_scale;
	uint8_t rating_num;
	char  abbrev_name[16][8];
	char  rating_value[16][150];
} RATING;

typedef struct {
	uint16_t rating_region;
	char     region_name[32];
	uint8_t dimension_num;
	RATING   *rating;
} RTT;

#define MAX_CAD_RIGION_NUM 4
typedef struct {
	uint8_t region_num;

	uint8_t rating_region[MAX_CAD_RIGION_NUM];
	uint8_t rating_num[MAX_CAD_RIGION_NUM];
	uint8_t rating_index[MAX_CAD_RIGION_NUM][16];
	uint8_t value_index[MAX_CAD_RIGION_NUM][16];
	STRING256     description[MAX_CAD_RIGION_NUM];
} CAD; //content_advisory_descriptor

typedef struct 
{
	uint16_t ett_ext_id;
	uint16_t event_id;
	uint16_t source_id;
} ETT;

typedef struct 
{
	uint16_t  event_id;
	uint16_t  source_id;
	uint32_t  start_time;
	uint32_t  during_length;
	uint8_t   etm_loc;
	DESC_DATA	title;
	DESC_DATA	program;
	uint32_t  program_crc32;
	DESC_DATA	cad;
	uint8_t   genre[MAX_GENRE_NUM];
	int32_t   need_updated;
} EIT;

typedef struct 
{
	int32_t eit_num;
	EIT eit[MAX_EPG_NUM_PER_CH];
} EIT_COL;


typedef struct ATSC_PSI
{
	TS_SECTION *atsc_psi_section;

	uint32_t system_time;
	uint16_t mgt_num;
	MGT			 mgt[MAX_MGT_TBL];
	uint32_t mgt_section_crc32; //for checking mgt updating
	uint8_t  mgt_update_flag;

	uint16_t vct_num;
	uint16_t vct_program;
	VCT			 vct[MAX_VCT_TBL];
	uint32_t vct_section_crc32; ////for checking vct updating
	uint8_t  vct_update_flag;


	RTT			 rtt[MAX_RTT_TBL];
	uint32_t rtt_section_crc32; ////for checking vct updating
	uint8_t  rtt_update_flag;

	uint16_t eit_blk_num;
	EIT_COL	*eit_blk;
	uint32_t eit_section_crc32[MAX_EIT_NUM]; ////for checking eit updating
	uint8_t  eit_update_flag;

	uint32_t ett_section_crc32[MAX_EIT_NUM+1]; ////for checking ett updating
	uint8_t  ett_update_flag;

	uint32_t language_code;
	struct PSI_PARSER*	psi_parser;

	uint8_t not_save_epg_message;
	uint8_t disable_channel_inf;

	TS_STREAMS     streams; //passing channel streams information in VCT to TSFilter

} ATSC_PSI;

ATSC_PSI* CreateATSCPSI(  );
void ReleaseATSCPSI( ATSC_PSI* pATSCPSI );
void ResetATSCPSI( ATSC_PSI* pATSCPSI );
int  ProcessATSCPSI( ATSC_PSI* pATSCPSI, TS_PACKET *pTSPacket );

void ReleaseString( STRING* pString );
uint8_t* NewDescData( DESC_DATA* pDesc, int nBytes );
uint8_t* FillDescData( DESC_DATA *pDesc, uint8_t* pData, int nBytes );
void EraseDescData( DESC_DATA *pDesc );
void ReleaseDescData( DESC_DATA *pDesc );

char* _descriptor_check( uint8_t* p, int bytes );

#ifdef __cplusplus
extern "C" }
#endif

#endif

