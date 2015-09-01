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
#ifndef DVBPSI_PARSER
#define DVBPSI_PARSER

#define NIT_LIST_NODE_NUM  32
#define SDT_LIST_NODE_NUM  32

#define MAX_EVENT_NUM  (4096*3)     //maxuim DVB EPG cell number
#define EVEN_ROW_NUM    24*3
#define EVEN_COL_NUM    64

#define	NETWORK_NAME_LEN   32
#define SERVICE_NAME_LEN   64
#define PROVIDER_NAME_LEN  64

#ifdef __cplusplus
extern "C" {
#endif


typedef struct {
	uint16_t sevice_id;
	int32_t  type;
	int32_t  padding;
} NIT_SERVICE;

typedef struct {

	uint16_t network_id;
	uint16_t tsid;
	uint16_t onid;
	uint16_t service_num;
	NIT_SERVICE   *service_list;
	char           network_name[NETWORK_NAME_LEN];
	int32_t  type; //1:TERRESTRIAL; 2:CABLE; 3:SATELLITE_TUNE
	union {
		TERRESTRIAL_TUNE	t;
		CABLE_TUNE			c;
		SATELLITE_TUNE		s;
	} dvb;
} NIT;

typedef struct {
	uint16_t nit_num;
	uint16_t nit_total_num;
	NIT      **nit_list;
} NIT_LIST;

typedef struct {
	int32_t  linkage_type;
	int32_t  hand_over_type;
	uint16_t tsid;
	uint16_t onid;
	uint16_t sid;
	uint16_t nit;
	uint16_t inital_service_id;
} LINKAGE;

typedef struct {
	uint16_t running_status;
	uint16_t service_id;
	int32_t  EIT_flag;
	int32_t  CA_flag;
	int32_t  type;
	int32_t  padding;
	STRING128      service_name;
	STRING128      provider_name; //we don't need it, skip ZQ.
} SERVICE;

typedef struct {
	uint16_t tsid;
	uint16_t onid;
	uint16_t service_num;
	SERVICE  *service;
	LINKAGE  *linkage;
} SDT;

typedef struct {
	uint16_t sdt_num;
	uint16_t sdt_total_num;
	SDT      **sdt_list;
} SDT_LIST;


typedef struct 
{
	uint16_t flag;
	char Name[40];
	char Provider[40];
	uint16_t ONID;
	uint16_t TSID;
	uint16_t service_id;
	uint16_t service_type;
	int32_t  delivery_type; //1:DVB-T, 2:DVB-C, 3:DVB-S
	int32_t  padding; //alignment
	union {
		TERRESTRIAL_TUNE	t;
		CABLE_TUNE			c;
		SATELLITE_TUNE		s;
	} dvb;
} SVT;


typedef struct 
{
	uint16_t event_id;
	uint32_t  start_time;
	uint32_t  duration_length;
	DESC_DATA	   title;
	DESC_DATA	   message;
	DESC_DATA      rating;
	DESC_DATA	   content_desc;
	uint32_t  title_crc32;
	uint32_t  message_crc32;
	uint32_t  rating_crc32;
	uint32_t  content_desc_crc32;
} EVNT;

typedef struct EVNT_COL
{
	uint16_t onid;
	uint16_t tsid;
	uint16_t sid;
	uint16_t row_num;
	uint16_t total_row_num;
	EVNT *evnt;
} EVNT_COL;

typedef struct EVNT_LIST
{
	uint16_t col_num;
	uint16_t total_col_num;
	uint32_t  total_even_num;
	EVNT_COL *evnt_col;
} EVNT_LIST;

typedef struct DVB_FILTER
{
	uint16_t nid;
	uint16_t onid;
	uint16_t tsid;
	uint16_t sid;
}DVB_PSI_FILTER;

/////////////////////////////////////////////////////////////////

typedef struct DVB_PSI
{
	uint32_t   system_time;

	TS_SECTION     *nit_section;
	//int32_t	nit_update_flag;
	NIT_LIST		nit_list;

	TS_SECTION     *sdt_section;
	//int32_t	sdt_update_flag;
	SDT_LIST		sdt_list;

	TS_SECTION     *eit_section;
	//int32_t	eit_update_flag;

	uint16_t  total_evnt_cell;
	EVNT_LIST		evnt_list;
	DVB_PSI_FILTER	evnt_filter;

	struct PSI_PARSER*	psi_parser;

	uint32_t   language_code;
	int32_t	not_save_epg_message; //don't save epg message into a cell to save space

	int32_t disable_channel_inf;
	int32_t disable_tune_inf;
	int32_t padding;                //alignment

} DVB_PSI;

DVB_PSI*  CreateDVBPSI( PSI_PARSER* pPSIParser );
void ReleaseDVBPSI( DVB_PSI* pDVBPSI );
void ResetDVBPSI( DVB_PSI* pDVBPSI  );
int  ProcessDVBPSI( DVB_PSI* pDVBPSI, TS_PACKET *pTSPacket );

#ifdef __cplusplus
}
#endif

#endif


