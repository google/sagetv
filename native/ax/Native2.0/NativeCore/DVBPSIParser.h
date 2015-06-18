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
	unsigned short sevice_id;
	unsigned char  type;
	unsigned char  padding;
} NIT_SERVICE;

typedef struct {

	unsigned short network_id;
	unsigned short tsid;
	unsigned short onid;
	unsigned short service_num;
	NIT_SERVICE   *service_list;
	char           network_name[NETWORK_NAME_LEN];
	unsigned char  type; //1:TERRESTRIAL; 2:CABLE; 3:SATELLITE_TUNE
	union {
		TERRESTRIAL_TUNE	t;
		CABLE_TUNE			c;
		SATELLITE_TUNE		s;
	} dvb;
} NIT;

typedef struct {
	unsigned short nit_num;
	unsigned short nit_total_num;
	NIT      **nit_list;
} NIT_LIST;

typedef struct {
	unsigned char  linkage_type;
	unsigned char  hand_over_type;
	unsigned short tsid;
	unsigned short onid;
	unsigned short sid;
	unsigned short nit;
	unsigned short inital_service_id;
} LINKAGE;

typedef struct {
	unsigned short running_status;
	unsigned short service_id;
	unsigned char  EIT_flag;
	unsigned char  CA_flag;
	unsigned char  type;
	unsigned char  padding;
	STRING128      service_name;
	STRING128      provider_name; //we don't need it, skip ZQ.
} SERVICE;

typedef struct {
	unsigned short tsid;
	unsigned short onid;
	unsigned short service_num;
	SERVICE  *service;
	LINKAGE  *linkage;
} SDT;

typedef struct {
	unsigned short sdt_num;
	unsigned short sdt_total_num;
	SDT      **sdt_list;
} SDT_LIST;


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
	unsigned char  padding; //alignment
	union {
		TERRESTRIAL_TUNE	t;
		CABLE_TUNE			c;
		SATELLITE_TUNE		s;
	} dvb;
} SVT;


typedef struct 
{
	unsigned short event_id;
	unsigned long  start_time;
	unsigned long  duration_length;
	DESC_DATA	   title;
	DESC_DATA	   message;
	DESC_DATA      rating;
	DESC_DATA	   content_desc;
	unsigned long  title_crc32;
	unsigned long  message_crc32;
	unsigned long  rating_crc32;
	unsigned long  content_desc_crc32;
} EVNT;

typedef struct EVNT_COL
{
	unsigned short onid;
	unsigned short tsid;
	unsigned short sid;
	unsigned short row_num;
	unsigned short total_row_num;
	EVNT *evnt;
} EVNT_COL;

typedef struct EVNT_LIST
{
	unsigned short col_num;
	unsigned short total_col_num;
	unsigned long  total_even_num;
	EVNT_COL *evnt_col;
} EVNT_LIST;

typedef struct DVB_FILTER
{
	unsigned short nid;
	unsigned short onid;
	unsigned short tsid;
	unsigned short sid;
}DVB_PSI_FILTER;

/////////////////////////////////////////////////////////////////

typedef struct DVB_PSI
{
	unsigned long   system_time;

	TS_SECTION     *nit_section;
	//unsigned char	nit_update_flag;
	NIT_LIST		nit_list;

	TS_SECTION     *sdt_section;
	//unsigned char	sdt_update_flag;
	SDT_LIST		sdt_list;

	TS_SECTION     *eit_section;
	//unsigned char	eit_update_flag;

	unsigned short  total_evnt_cell;
	EVNT_LIST		evnt_list;
	DVB_PSI_FILTER	evnt_filter;

	struct PSI_PARSER*	psi_parser;

	unsigned long   language_code;
	unsigned char	not_save_epg_message; //don't save epg message into a cell to save space

	unsigned char disable_channel_inf;
	unsigned char disable_tune_inf;
	unsigned char padding;                //alignment

} DVB_PSI;

DVB_PSI*  CreateDVBPSI( PSI_PARSER* pPSIParser );
void ReleaseDVBPSI( DVB_PSI* pDVBPSI );
void ResetDVBPSI( DVB_PSI* pDVBPSI  );
int  ProcessDVBPSI( DVB_PSI* pDVBPSI, TS_PACKET *pTSPacket );

#ifdef __cplusplus
}
#endif

#endif


