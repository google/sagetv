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
#define _USE_32BIT_TIME_T
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <memory.h>
#include "TSCRC32.h"
#include "NativeCore.h"
#include "SectionData.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "DVBPSIParser.h"


//DVB-T descriptor tag
#define NETWORK_NAME_TAG		0x40
#define SERVICE_LIST_TAG		0x41
#define SERVICE_TAG				0x48
#define LINKAGE_TAG				0x4A
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
#define DVB_LOCAL_TIME_OFFSET   0x58

static uint32_t UnpackBCDcode( char* p, int bytes );
static uint32_t MJD2Locatime( uint8_t* pMJD );
static int32_t  BCDTime( uint8_t* pMJD );

static NIT *CreateNit(  );
static void ReleaseNit( NIT* pNit );
static void AddNitToList( DVB_PSI* pDVBPSI, NIT *pNit );
static SDT *CreateSdt(  );
static void ReleaseSdt( SDT* pSdt );
static void AddSdtToList( DVB_PSI* pDVBPSI, SDT *pSdt );
static void ResetNitList( DVB_PSI* pDVBPSI );

static int ParserDVBTextCharSet( uint8_t* code, int bytes );
static char* DVBTextCharSetName( uint8_t* code, int bytes );

static void ReleaseEnvtList( EVNT_LIST *pEvntList );

#define MESSAGE_INDEX	255
static int NumberOfShortEeventDesc( DESC_DATA *pDescData );
static uint32_t LanguageOfShortEeventDesc( DESC_DATA *pDescData, int nEeventIndex );
//static int BytesOfShortEeventDesc( DESC_DATA *pDescData, int nEeventIndex );
static int UnpackShortEeventDesc( DESC_DATA *pDescData, int nEeventIndex, MESG256* pMesg  );

static int NumberOfExtendEeventDesc( DESC_DATA *pDescData );
static int ItemsOfExtendEeventDesc( DESC_DATA *pDescData, int nEeventIndex );
static uint32_t LanguageOfExtendEeventDesc( DESC_DATA *pDescData, int nEeventIndex );
static int BytesOfExtendEeventDesc( DESC_DATA *pDescData, int nEeventIndex, int nItemIndex );
static int UnpackExtendEeventDesc( DESC_DATA *pDescData, int nEeventIndex, int nItemIndex, MESG256* pMesg  );

static int UnpackPrentalRating( DESC_DATA *pDescData, STRING256 *pString ) ;
static int UnpackContentGenre( DESC_DATA *pDescData, STRING256 *pString );

static int FilterDVBTextCtrlCode( uint8_t* code, int bytes ) ;
//static int TotalDVBTextCtrlCodeNum( uint8_t* code, int bytes );

char* DVBGenreString( uint8_t code );

void DumpAllNits( DVB_PSI* pDVBPSI );
void DumpAllSdts( DVB_PSI* pDVBPSI );
void DumpAllEvnts( DVB_PSI* pDVBPSI );

static int DVBFormatEPG( uint16_t onid, uint16_t tsid, uint16_t sid, uint32_t language_code, EVNT *evnt, DESC_DATA* desc );

DVB_PSI*  CreateDVBPSI( PSI_PARSER* pPSIParser )
{
	DVB_PSI *pDVBPSI = SAGETV_MALLOC( sizeof(DVB_PSI) );

	SageLog(( _LOG_TRACE, 3, TEXT("It's an DVB Stream, DVB PSI parser ver.1.0.2 is created") ));
	SageLog(( _LOG_TRACE, 4, TEXT("Memory footprint of PSIP of DVB %d"), sizeof(DVB_PSI) ));

	pDVBPSI->psi_parser = pPSIParser;
	pDVBPSI->nit_section = CreateSection( );
	pDVBPSI->sdt_section = CreateSection( );
	pDVBPSI->eit_section = CreateSection( );

	//pDVBPSI->language_code = pPSIParser->language_code;
	pDVBPSI->not_save_epg_message = 1;

	pDVBPSI->total_evnt_cell = 0;
	pDVBPSI->disable_tune_inf = 0;
	pDVBPSI->disable_channel_inf = 0;
	

	return pDVBPSI;
}

void ReleaseDVBPSI( DVB_PSI* pDVBPSI )
{
	int i;

DumpAllNits( pDVBPSI );
DumpAllSdts( pDVBPSI );
//DumpAllEvnts(pDVBPSI );

	ReleaseSection( pDVBPSI->nit_section );
	ReleaseSection( pDVBPSI->sdt_section );
	ReleaseSection( pDVBPSI->eit_section );
	for ( i = 0; i<pDVBPSI->nit_list.nit_num; i++ )
		ReleaseNit( pDVBPSI->nit_list.nit_list[i] );
	SAGETV_FREE( pDVBPSI->nit_list.nit_list );

	for ( i = 0; i<pDVBPSI->sdt_list.sdt_num; i++ )
		ReleaseSdt( pDVBPSI->sdt_list.sdt_list[i] );
	SAGETV_FREE( pDVBPSI->sdt_list.sdt_list );

	ReleaseEnvtList( &pDVBPSI->evnt_list );

	SAGETV_FREE( pDVBPSI );
}


void ResetDVBPSI( DVB_PSI* pDVBPSI  )
{
	//pDVBPSI->nit_update_flag = 0;
	//pDVBPSI->sdt_update_flag = 0;
	//pDVBPSI->eit_update_flag = 0;

	ResetNitList( pDVBPSI );
	ReleaseEnvtList( &pDVBPSI->evnt_list );
	pDVBPSI->total_evnt_cell = 0;
	pDVBPSI->disable_tune_inf = 0;
	pDVBPSI->disable_channel_inf = 0;

	
}

static void ResetNitList( DVB_PSI* pDVBPSI )
{
	int i;
	for ( i = 0; i<pDVBPSI->nit_list.nit_num; i++ )
	{
		ReleaseNit( pDVBPSI->nit_list.nit_list[i] );
		pDVBPSI->nit_list.nit_list[i] = NULL;
	}
}

////////////////////////////////////////////
static NIT *CreateNit(  )
{
	NIT* pNit = SAGETV_MALLOC( sizeof(NIT) );
	return pNit;
}

static void ReleaseNit( NIT* pNit )
{
	if ( pNit ) 
	{
		if ( pNit->service_list )
		{
			SAGETV_FREE( pNit->service_list );
		}
		SAGETV_FREE( pNit );
	}
}


static void AddNitToList( DVB_PSI* pDVBPSI, NIT *pNit )
{
	if ( pDVBPSI->nit_list.nit_num >= pDVBPSI->nit_list.nit_total_num )
	{
		uint16_t new_list_num = pDVBPSI->nit_list.nit_total_num + NIT_LIST_NODE_NUM;	
		NIT** new_list = (NIT**)SAGETV_MALLOC(  sizeof( NIT* )*new_list_num );
		memcpy( new_list, pDVBPSI->nit_list.nit_list,  sizeof( NIT* )*pDVBPSI->nit_list.nit_num );
		SAGETV_FREE( pDVBPSI->nit_list.nit_list ); //release old one.
		pDVBPSI->nit_list.nit_list = new_list;
		pDVBPSI->nit_list.nit_total_num = new_list_num;
	}
	pDVBPSI->nit_list.nit_list[pDVBPSI->nit_list.nit_num++] = pNit;
}


void DumpAllNits( DVB_PSI* pDVBPSI )
{
	int i, k, service_sum = 0, type=0;
	NIT* nit;
	for ( i = 0; i<pDVBPSI->nit_list.nit_num; i++ )
	{
		nit = pDVBPSI->nit_list.nit_list[i];
		type = nit->type;
		service_sum += nit->service_num;
	}
	SageLog(( _LOG_TRACE, 3, TEXT("Dump NIT type:0x%x ( NIT total:%d, service num in NIT:%d )"), 
		       type, pDVBPSI->nit_list.nit_num, service_sum ));
	k = 0;
	for ( i = 0; i<pDVBPSI->nit_list.nit_num; i++ )
	{
		nit = pDVBPSI->nit_list.nit_list[i];	
		SageLog(( _LOG_TRACE, 3, TEXT("NIT:%d netid:%d; tsid:%d; onid:%d; (name:%s) frq:%d; services-num:%d"), k++,
				nit->network_id, nit->tsid, nit->onid, nit->network_name, nit->dvb.t.freq, nit->service_num ));
		
	}
}
////////////////////////////////////////////

static SDT *CreateSdt( )
{
	SDT* pSdt = SAGETV_MALLOC( sizeof(SDT) );
	return pSdt;
}

static void ReleaseSdt( SDT* pSdt )
{
	if ( pSdt )
	{
		if ( pSdt->service_num )
		{
			SAGETV_FREE( pSdt->service );
		}
		if ( pSdt->linkage )
		{
			SAGETV_FREE( pSdt->linkage );
		}
		SAGETV_FREE( pSdt );
	}
}

static void AddSdtToList( DVB_PSI* pDVBPSI, SDT *pSdt )
{
	if ( pDVBPSI->sdt_list.sdt_num >= pDVBPSI->sdt_list.sdt_total_num )
	{
		uint16_t new_list_num = pDVBPSI->sdt_list.sdt_total_num + SDT_LIST_NODE_NUM;	
		SDT** new_list = (SDT**)SAGETV_MALLOC(  sizeof( SDT* )*new_list_num );
		memcpy( new_list, pDVBPSI->sdt_list.sdt_list,  sizeof( SDT* )*pDVBPSI->sdt_list.sdt_num );
		SAGETV_FREE( pDVBPSI->sdt_list.sdt_list ); //release old one.
		pDVBPSI->sdt_list.sdt_list = new_list;
		pDVBPSI->sdt_list.sdt_total_num = new_list_num;
	}

	pDVBPSI->sdt_list.sdt_list[pDVBPSI->sdt_list.sdt_num++] = pSdt;
	//ASSERT( pDVBPSI->sdt_list.sdt_num <= pDVBPSI->sdt_list.sdt_total_num );
}

SDT* GetSdtFromList( DVB_PSI* pDVBPSI, uint16_t onid, uint16_t tsid )
{
	int i;
	for ( i = 0; i<pDVBPSI->sdt_list.sdt_num; i++ )
	{
		if ( pDVBPSI->sdt_list.sdt_list[i]->onid == onid && pDVBPSI->sdt_list.sdt_list[i]->tsid == tsid )
			return pDVBPSI->sdt_list.sdt_list[i];
	}
	return NULL;
}

void DumpAllSdts( DVB_PSI* pDVBPSI )
{
	int i,j,k, service_sum = 0;
	SDT* sdt;
	for ( i = 0; i<pDVBPSI->sdt_list.sdt_num; i++ )
	{
		sdt = pDVBPSI->sdt_list.sdt_list[i];
		service_sum += sdt->service_num;
	}
	SageLog(( _LOG_TRACE, 3, TEXT("Dump SDT ( SDT total:%d, service num in SDT:%d )"), 
						pDVBPSI->sdt_list.sdt_num, service_sum ));
	k = 0;
	for ( i = 0; i<pDVBPSI->sdt_list.sdt_num; i++ )
	{
		sdt = pDVBPSI->sdt_list.sdt_list[i];
		for ( j=0; j<sdt->service_num; j++ )
		{
			
				SageLog(( _LOG_TRACE, 3, TEXT("Channel:%d tsid:%d onid:%d sid:%d (name:%s) type:%d ca:%d epg:%d flag:%d"), k++,
						 sdt->tsid, sdt->onid,  sdt->service[j].service_id, sdt->service[j].service_name.data, 
						 sdt->service[j].type, sdt->service[j].CA_flag, sdt->service[j].EIT_flag, sdt->service[j].running_status ));
		}
	}
}

////////////////////////////////////////////////

static NIT* GetNit( DVB_PSI* pDVBPSI, uint16_t onid, uint16_t tsid, uint16_t sid )
{
	int i, j;
	for ( i = 0; i<pDVBPSI->nit_list.nit_num; i++ )
	{
		if ( pDVBPSI->nit_list.nit_list[i]->tsid == tsid && 
			 pDVBPSI->nit_list.nit_list[i]->onid == onid   )
		{
			for ( j = 0; j<pDVBPSI->nit_list.nit_list[i]->service_num; j++ )
			{
				if ( pDVBPSI->nit_list.nit_list[i]->service_list[j].sevice_id == sid )
					return pDVBPSI->nit_list.nit_list[i];
			}
		}
	}
	return NULL;
}


static int UnpackNIT( DVB_PSI* pDVBPSI, TS_SECTION* pSection )
{
	SECTION_HEADER section_header;
	int  desc_bytes, ts_loop_bytes, total_bytes, used_bytes;
	uint16_t network_id;
	uint8_t *pData;
	uint8_t *desc_ptr;
	int desc_len;
	NIT *nit;
	int i, drop_nit = 0;
	int nit_update_flag;

	char  network_name[NETWORK_NAME_LEN];			
	UnpackSectionDataHeader( &section_header, pSection );
	if ( section_header.table_id != 0x40 && section_header.table_id != 0x41 ) //0x40 actual nit; 0x41 other nit
		return 0;

	if ( section_header.table_id == 0x40 ) 
	{
		nit_update_flag  = 1;
	} else
	{
		nit_update_flag  = 2;
	}

	pData = section_header.table_data;
	total_bytes = section_header.table_bytes;
	network_id = section_header.tsid;

	desc_bytes = (( pData[0]&0x0f ) << 8 ) | pData[1];
	pData += 2;
	if ( ( desc_ptr = GetDescriptor( pData, desc_bytes, NETWORK_NAME_TAG, &desc_len ) )!= NULL )
	{
		int len = _MIN( desc_len, sizeof(network_name)-1);
		memcpy( network_name, desc_ptr+2, len );
		network_name[len ] = 0x0;
	}

	pData += desc_bytes;
	ts_loop_bytes = (( pData[0]&0x0f ) << 8 ) | pData[1]; //transport stream loop bytes
	pData += 2;
	used_bytes = 0;
	while ( used_bytes < ts_loop_bytes && (int)(pData - section_header.table_data) <  total_bytes )
	{
		int duplicated_nit;

		if ( used_bytes + 6 >= ts_loop_bytes )
			break;

		nit = CreateNit( );
		nit->service_num = 0;
		nit->network_id = network_id;
		memcpy ( nit->network_name, network_name, sizeof(nit->network_name) ); ;

		nit->tsid  = ( ((uint8_t)pData[0])<<8 ) | (uint8_t)pData[1];
		nit->onid  = ( ((uint8_t)pData[2])<<8 ) | pData[3];
		desc_bytes = (( (uint8_t)pData[4]&0x0f ) << 8 ) | pData[5];
		if ( desc_bytes == 0 ) 
		{
			break;
		}

		pData += 6;
		used_bytes += 6;
		duplicated_nit = 0;
		for ( i = 0; i<pDVBPSI->nit_list.nit_num; i++ )
		{
			if ( pDVBPSI->nit_list.nit_list[i]->network_id == nit->network_id && 
				 pDVBPSI->nit_list.nit_list[i]->tsid == nit->tsid && 
				 pDVBPSI->nit_list.nit_list[i]->onid == nit->onid   )
			{
				duplicated_nit = 1;
			}
		}
		
		if ( !duplicated_nit )
		{
			if ( ( desc_ptr = GetDescriptor( pData, desc_bytes, NETWORK_NAME_TAG, &desc_len ) )!= NULL )
			{
				int len = _MIN( sizeof(nit->network_name)-1, desc_len );
				memcpy( nit->network_name, desc_ptr, len );
				nit->network_name[len] = 0x0;
			}

			if ( ( desc_ptr = GetDescriptor( pData, desc_bytes, SERVICE_LIST_TAG, &desc_len ) )!= NULL )
			{
				uint8_t* p = desc_ptr +2;
				int service_num = desc_len / 3;
				int k=0;
				nit->service_list = SAGETV_MALLOC( sizeof( NIT_SERVICE ) * service_num );
				for ( i = 0; i <service_num; i++, k++ )
				{
					nit->service_list[k].sevice_id =  (p[0]<<8) | p[1];
					nit->service_list[k].type = p[2];
					p += 3;
					if ( nit->service_list[k].type == 0x04 || nit->service_list[k].type == 0x05 ||
						 nit->service_list[k].type == 0x17 || nit->service_list[k].type == 0x18 ||
						 nit->service_list[k].type == 0x1a || nit->service_list[k].type == 0x1b   ) 
						k--; //drop those we don't expect service
				}
				nit->service_num = k;
			}

			if ( ( desc_ptr = GetDescriptor( pData, desc_bytes, TERRESTRIAL_DELIVERY_TAG, &desc_len ) )!= NULL )
			{
				uint8_t* p = desc_ptr+2;
				nit->type = 1;
				nit->dvb.t.freq = (p[0]<<24) | (p[1]<<16) | (p[2]<<8) | p[3];
				nit->dvb.t.freq *= 10;
				nit->dvb.t.band = (p[4]>>5) & 0x7;
				switch ( nit->dvb.t.band )	{ 
				case 0: nit->dvb.t.band = 8; break;
				case 1: nit->dvb.t.band = 7; break;
				case 2: nit->dvb.t.band = 6; break;
				case 3: nit->dvb.t.band = 5; break;
				default: nit->dvb.t.band = 100;
				}
			}
			else
			if ( ( desc_ptr = GetDescriptor( pData, desc_bytes, CABLE_DELIVERY_TAG, &desc_len ) )!= NULL )
			{
				uint8_t* p = desc_ptr+2;
				nit->type = 2;
				nit->dvb.c.freq = UnpackBCDcode( (char*)p, 4 );
				nit->dvb.c.freq *= 10;
				nit->dvb.c.fec_out = p[5]&0x0f;
				if ( nit->dvb.c.fec_out != 2 ) nit->dvb.c.fec_out = 0;
				nit->dvb.c.modulation = p[6];
				if ( nit->dvb.c.modulation == 1 ) nit->dvb.c.modulation = 1; else  //16QAM
				if ( nit->dvb.c.modulation == 2 ) nit->dvb.c.modulation = 2; else  //32QAM
				if ( nit->dvb.c.modulation == 3 ) nit->dvb.c.modulation = 3; else  //64QAm
				if ( nit->dvb.c.modulation == 4 ) nit->dvb.c.modulation = 7; else  //128QAM
				if ( nit->dvb.c.modulation == 5 ) nit->dvb.c.modulation = 11; else //256QAM
					nit->dvb.c.modulation = 99;
				nit->dvb.c.symbol_rate = UnpackBCDcode( (char*)p+7, 4 );
				nit->dvb.c.symbol_rate /= 100;
				nit->dvb.c.fec_in_rate = (p[10]&0x0f);
				if ( nit->dvb.c.fec_in_rate > 6 )  nit->dvb.c.fec_in_rate = 0; else
				if ( nit->dvb.c.fec_in_rate == 1 ) nit->dvb.c.fec_in_rate = 1; else //fec 1/2
				if ( nit->dvb.c.fec_in_rate == 2 ) nit->dvb.c.fec_in_rate = 2; else //fec 2/3
				if ( nit->dvb.c.fec_in_rate == 3 ) nit->dvb.c.fec_in_rate = 3; else //fec 3/4
				if ( nit->dvb.c.fec_in_rate == 4 ) nit->dvb.c.fec_in_rate = 6; else //fec 5/6
				if ( nit->dvb.c.fec_in_rate == 5 ) nit->dvb.c.fec_in_rate = 8; else //fec 7/8
				if ( nit->dvb.c.fec_in_rate == 6 ) nit->dvb.c.fec_in_rate = 9; //fec 8/9

			}
			else
			if ( ( desc_ptr = GetDescriptor( pData, desc_bytes, SATELLITE_DELIVERY_TAG, &desc_len ) )!= NULL )
			{
				uint8_t* p = desc_ptr+2;
				nit->type = 3;

				nit->dvb.s.freq = UnpackBCDcode( (char*)p, 4 );
				nit->dvb.s.freq *= 10;

				//skip 2 bytes orbit
				 nit->dvb.s.orbit = (short)UnpackBCDcode( (char*)p+4, 2 );
				if ( (p[6] & 0x80) )  nit->dvb.s.orbit = - nit->dvb.s.orbit;
				 nit->dvb.s.pol = (p[6]>>5)&0x03;
				 nit->dvb.s.pol++;
				 nit->dvb.s.modulation = p[6] & 0x1f;
				if (  nit->dvb.s.modulation == 1 )  nit->dvb.s.modulation = 20; else //QPSK
				if (  nit->dvb.s.modulation == 2 )  nit->dvb.s.modulation = 22; else //OQPSK
				if (  nit->dvb.s.modulation == 3 )  nit->dvb.s.modulation = 1;  else //16QAM
				if (  nit->dvb.s.modulation == 5 ) 
					nit->dvb.s.modulation = 30; else //8PSK for DVB-S2
				if (  nit->dvb.s.modulation == 6 )  
					nit->dvb.s.modulation = 86; else //unknown
				if (  nit->dvb.s.modulation == 14 ) 
					nit->dvb.s.modulation = 94; else //unknown
					  nit->dvb.s.modulation = 99;

				 nit->dvb.s.symbol_rate = UnpackBCDcode( (char*)p+7, 4 );
				 nit->dvb.s.symbol_rate /= 100;
				 nit->dvb.s.fec_rate = (p[10]&0x0f);
				if (  nit->dvb.s.fec_rate > 6 )   nit->dvb.s.fec_rate = 0; else
				if (  nit->dvb.s.fec_rate == 1 )  nit->dvb.s.fec_rate = 1; else //fec 1/2
				if (  nit->dvb.s.fec_rate == 2 )  nit->dvb.s.fec_rate = 2; else //fec 2/3
				if (  nit->dvb.s.fec_rate == 3 )  nit->dvb.s.fec_rate = 3; else //fec 3/4
				if (  nit->dvb.s.fec_rate == 4 )  nit->dvb.s.fec_rate = 6; else //fec 5/6
				if (  nit->dvb.s.fec_rate == 5 )  nit->dvb.s.fec_rate = 8; else //fec 7/8
				if (  nit->dvb.s.fec_rate == 6 )  nit->dvb.s.fec_rate = 9;      //fec 8/9
			}
			//SageLog(( _LOG_TRACE, 3, TEXT("NIT: type:%d netid:%d; tsid:%d; onid:%d; (name:%s) frq:%d; services-num:%d"),
			//	nit->type, nit->network_id, nit->tsid, nit->onid, nit->network_name, nit->dvb.t.freq, nit->service_num ));

			if ( pDVBPSI->psi_parser->sub_format == 0 ) 
			{
				pDVBPSI->psi_parser->sub_format = nit->type;
				if ( pDVBPSI->psi_parser->dumper.message_dumper != NULL )
				{
					MESSAGE_DATA message_data;
					char buf[32];
					snprintf( buf, sizeof(buf), "%s", StreamFormatString( pDVBPSI->psi_parser->stream_format, pDVBPSI->psi_parser->sub_format ) );
					memcpy( message_data.title, "FORMAT", 7 );
					message_data.message = (uint8_t*)buf;
					message_data.message_length = (uint16_t)strlen(buf);
					message_data.buffer = (uint8_t*)buf;
					message_data.buffer_length = (uint16_t)sizeof(buf);
					pDVBPSI->psi_parser->dumper.message_dumper( pDVBPSI->psi_parser->dumper.message_context, &message_data, sizeof(message_data) );
				}
			}

 			//dump channel information here
			if ( pDVBPSI->psi_parser->dumper.tune_info_dumper != NULL )
			{
				int i;
				TUNE_DATA tune_data={0};

				tune_data.update_flag = nit_update_flag;
				tune_data.stream_format = DVB_STREAM;
				tune_data.sub_format = pDVBPSI->psi_parser->sub_format;
				tune_data.u.dvb.onid     = nit->onid;
				tune_data.u.dvb.tsid     = nit->tsid;
				tune_data.u.dvb.nid		 = nit->network_id;
				tune_data.u.dvb.dvb_type = nit->type;
				if ( nit->type == 1 )
					tune_data.u.dvb.dvb.t = nit->dvb.t;
				else
				if ( nit->type == 2 )
					tune_data.u.dvb.dvb.c = nit->dvb.c;
				else
				if ( nit->type == 3 )
					tune_data.u.dvb.dvb.s = nit->dvb.s;
				
				memcpy( tune_data.u.dvb.name, nit->network_name,
							sizeof(tune_data.u.dvb.name)-1 );

				tune_data.num_tune = _MIN( nit->service_num, MAX_TUNE_NUM );
				for ( i = 0; i<nit->service_num; i++ )
				{
					tune_data.s[i].sid = nit->service_list[i].sevice_id;
					tune_data.s[i].type = nit->service_list[i].type;
				}
				
				pDVBPSI->psi_parser->dumper.tune_info_dumper( pDVBPSI->psi_parser->dumper.channel_info_context, 
																  (uint8_t*)&tune_data, sizeof(tune_data) );
				drop_nit = ( tune_data.command != 1 );
			}

			if ( !drop_nit )
				AddNitToList( pDVBPSI, nit );
			else			
				ReleaseNit( nit );

			nit = NULL;
		}

		if ( nit != NULL ) 
			ReleaseNit( nit );
		nit = NULL;
		used_bytes += desc_bytes;
		pData += desc_bytes;
		i++;
	}
		
	return 1;
}


static int ParserService( SERVICE* pService, char* pData, int Bytes )
{
	uint8_t*p;
	int len1, len2;
	int code_byte, bytes;
	if ( pData == NULL || Bytes <= 2 )
		return 0;

	p = (uint8_t*)pData;
	pService->type = pData[0];
	
	len1 = ((uint8_t)pData[1]);
	if ( len1+2 > Bytes )
		return 1;

	code_byte = ParserDVBTextCharSet( p+2, len1 );
	pService->provider_name.charset_code = (uint8_t*)DVBTextCharSetName( p+2, len1 );
	if ( len1-code_byte >=0 )
	{
		bytes = _MIN( (len1-code_byte), sizeof(pService->provider_name.data)-1 );
		memcpy( pService->provider_name.data, p+2+code_byte, bytes );
		pService->provider_name.bytes = bytes;
		pService->provider_name.data[bytes] = 0x0;
	}

	len2 = ((uint8_t)pData[len1+2]);
	if ( len2+len1+2+1 > Bytes )
		return 0;
	
	if ( len2-code_byte >= 0 )
	{
		p = (uint8_t*)pData+1+len1+2;
		code_byte = ParserDVBTextCharSet( p, len2 );
		pService->service_name.charset_code = (uint8_t*)DVBTextCharSetName( p, len2 );
		bytes = _MIN( len2-code_byte, (int)sizeof(pService->service_name.data)-1 );
		memcpy( pService->service_name.data, p+code_byte, bytes );
		pService->service_name.data[bytes] = 0x0;
	}

	return 1;
}

static int ParserLinkage( LINKAGE* pLinkage, char* pData, int Bytes )
{
	uint8_t *p = (uint8_t*)pData+7;
	pLinkage->tsid = (p[0]<<8)|p[1];
	pLinkage->onid = (p[2]<<8)|p[3];
	pLinkage->sid  = (p[4]<<8)|p[5];
	pLinkage->linkage_type = pData[6];
	p = (uint8_t*)pData+7;
	if ( pLinkage->linkage_type == 8 )
	{
		int	orignal_type = *p & 0x01; //orignal type, 0=nit 1=SDT
		pLinkage->hand_over_type = (*p>>4) & 0x0f;
		p++;
		if ( pLinkage->hand_over_type == 1 ||
			 pLinkage->hand_over_type == 2 ||
			 pLinkage->hand_over_type == 3 )
		{
			pLinkage->nit = (*p<<8 )|*(p+1);;
			p += 2;
		}
		if ( orignal_type == 0 )
		{
			pLinkage->inital_service_id = (*p<<8 )|*(p+1);
		}
	} else
	{
		//skip private data of bytes pData[7].
	}

	return 1;
}

//ZQ remove me
//int sagetv_mem_loc( void* p );
//char* sagetv_mem_inf( int loc, char* buf, int size );
//ZQ remove me

static int UnpackSDT( DVB_PSI* pDVBPSI, TS_SECTION* pSection )
{
	SECTION_HEADER section_header;
	int  desc_bytes, bytes, total_bytes;
	uint8_t *pData;
	uint8_t* p;
	uint8_t *desc_ptr;
	int desc_len;
	int i, n, found_sdt=0, drop_sdt=1;
	SDT	 *sdt;
	int sdt_update_flag;
	//int _mem_loc;


	UnpackSectionDataHeader( &section_header, pSection );
	if ( section_header.table_id != 0x42 && section_header.table_id != 0x46 ) //0x42 actual nit; 0x46 other nit
		return 0;

	if ( section_header.table_id == 0x42 ) 
	{
		sdt_update_flag  = 1; 
	} else
	{
		sdt_update_flag  = 2; 
	}

	//memset( &sdt, 0, sizeof(sdt) );

	pData = section_header.table_data;
	total_bytes = section_header.table_bytes;

	if ( total_bytes <= 0 )	return 0;

	sdt = CreateSdt( );
	sdt->tsid = section_header.tsid;
	sdt->onid = (((uint8_t)pData[0])<<8 ) | (uint8_t)pData[1];
	sdt->service_num = 0;
	p = pData+2+1; //skip a reserved byte

	total_bytes -=2+1;
	if ( total_bytes <= 0 )	
	{
		ReleaseSdt( sdt );
		return 0;
	}

	//count total service record in section
	bytes = 0;
	n = 0;
	while ( bytes < total_bytes )
	{
		if ( ((p[0]<<8)|p[1]) == 0 ) break;
		desc_bytes = (( p[3]&0x0f ) << 8 ) | p[4];
		if ( desc_bytes == 0 || desc_bytes + bytes > total_bytes ) break;
		p     += 5+desc_bytes;
		bytes += 5+desc_bytes;
		n++; 
	}
	if ( n == 0 )
	{
		ReleaseSdt( sdt );
		return 0;
	}

	sdt->service = SAGETV_MALLOC( sizeof(SERVICE)*n );
	sdt->service_num = n;

//if ((_mem_loc = sagetv_mem_loc( sdt->service ))<0 )
//SageLog(( _LOG_TRACE, 3, "ERROR: lost memory index %s\r\n", _mem_loc ));


	bytes = 0; 
	p = pData+2+1;
	for ( i = 0; i<n; i++ )
	{  
		if ( ((p[0]<<8)|p[1]) == 0 ) break;
		sdt->service[i].service_id = (p[0]<<8)|p[1];
		sdt->service[i].EIT_flag   = p[2]&0x03; 
		sdt->service[i].running_status       = p[3]>>5; 
		sdt->service[i].CA_flag    = (p[3]>>4)&0x01; 
		desc_bytes = (( p[3]&0x0f ) << 8 ) | p[4];

		if ( ( desc_ptr = GetDescriptor( p+5, desc_bytes, SERVICE_TAG, &desc_len ) )!= NULL )
		{
			ParserService( &sdt->service[i], (char*)desc_ptr+2, desc_len );
		}
		if ( ( desc_ptr = GetDescriptor( p+5, desc_bytes, LINKAGE_TAG, &desc_len ) )!= NULL )
		{
			if ( sdt->linkage == NULL )
				sdt->linkage = SAGETV_MALLOC( sizeof(LINKAGE)*n );
			ParserLinkage( &sdt->linkage[i], (char*)desc_ptr+2, desc_len );
		}

		p     += 5+desc_bytes;
		bytes += 5+desc_bytes;
	}
	sdt->service_num = i;
	found_sdt = GetSdtFromList( pDVBPSI, sdt->onid, sdt->tsid ) != NULL;
	drop_sdt = found_sdt;

 	//dump channel information here
	if ( !found_sdt && pDVBPSI->psi_parser->dumper.channel_info_dumper != NULL )
	{
		int i;
		CHANNEL_DATA channel_data={0};

		channel_data.update_flag = sdt_update_flag;
		channel_data.stream_format = DVB_STREAM;
		channel_data.sub_format = pDVBPSI->psi_parser->sub_format;
		channel_data.num_channel = _MIN( sdt->service_num, MAX_DVB_CHANNEL );
		for ( i = 0; i<channel_data.num_channel; i++ )
		{	//ZQ I didn't dump out linkage information to user, if they need, add it late
			NIT* nit = GetNit( pDVBPSI, sdt->onid, sdt->tsid, sdt->service[i].service_id  );
			channel_data.u.dvb[i].service_type = sdt->service[i].type;
			channel_data.u.dvb[i].onid = sdt->onid;
			channel_data.u.dvb[i].tsid = sdt->tsid;
			channel_data.u.dvb[i].sid  = sdt->service[i].service_id;
			channel_data.u.dvb[i].ca = sdt->service[i].CA_flag;
			channel_data.u.dvb[i].running_status = sdt->service[i].running_status;
			if ( nit != NULL )
			{
				channel_data.u.dvb[i].dvb_type = nit->type;
				if ( nit->type == 1 )
					channel_data.u.dvb[i].dvb.t = nit->dvb.t;
				else
				if ( nit->type == 2 )
					channel_data.u.dvb[i].dvb.c = nit->dvb.c;
				else
				if ( nit->type == 3 )
					channel_data.u.dvb[i].dvb.s = nit->dvb.s;
				else
					channel_data.u.dvb[i].dvb_type = 0;

			} else
				channel_data.u.dvb[i].dvb_type = 0;

			memcpy( channel_data.u.dvb[i].name, 
				    sdt->service[i].service_name.data, 
					sizeof(channel_data.u.dvb[i].name)-1 );

		}
		pDVBPSI->psi_parser->dumper.channel_info_dumper( pDVBPSI->psi_parser->dumper.channel_info_context, 
			                                              (uint8_t*)&channel_data, sizeof(channel_data) );
		drop_sdt = ( channel_data.command != 1 );
	}

	if ( !drop_sdt && !found_sdt )
	{
		AddSdtToList( pDVBPSI, sdt );
	} else
	{

//if ( sagetv_mem_loc( sdt->service ) < 0 )
//{
//	char buf[128];
//	//sagetv_mem_inf( _mem_loc, buf, sizeof(buf) );
//	SageLog(( _LOG_TRACE, 3, "ERROR: lost mem in tabel %s\r\n", buf ));
//}

		ReleaseSdt( sdt );
	}

	return 1;
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////
static EVNT* CreateEvnt( )
{
	EVNT* pEvnt;
	pEvnt = SAGETV_MALLOC( sizeof(EVNT) );
	return pEvnt;
}

static void ReleaseEvnt( EVNT *pEvnt )
{
	SAGETV_FREE( pEvnt );
}

static void ReleaseEvntData( EVNT *pEvnt )
{
	ReleaseDescData( &pEvnt->title );
	ReleaseDescData( &pEvnt->message );
	ReleaseDescData( &pEvnt->rating );
	ReleaseDescData( &pEvnt->content_desc );
}

static int CompareEvnt( EVNT *pEvnt1, EVNT *pEvnt2, int bNotSaveMesg )
{
	if ( pEvnt1->start_time != pEvnt2->start_time )
		return 1;
	if ( pEvnt1->duration_length != pEvnt2->duration_length )
		return 1;

	if ( bNotSaveMesg == 0 )
	{
		if ( pEvnt2->title.desc_bytes &&
			 ( pEvnt1->title.desc_bytes != pEvnt2->title.desc_bytes ||
			   memcmp( pEvnt1->title.desc_ptr, pEvnt2->title.desc_ptr, pEvnt1->title.desc_bytes ) ) )
			return 1;
			 
		if ( pEvnt2->message.desc_bytes && 
			 ( pEvnt1->message.desc_bytes != pEvnt2->message.desc_bytes ||
			   memcmp( pEvnt1->message.desc_ptr, pEvnt2->message.desc_ptr, pEvnt1->message.desc_bytes ) ) )
			return 1;

		if ( pEvnt2->rating.desc_bytes &&
			 ( pEvnt1->rating.desc_bytes != pEvnt2->rating.desc_bytes ||
			   memcmp( pEvnt1->rating.desc_ptr, pEvnt2->title.desc_ptr, pEvnt1->rating.desc_bytes ) ) )
			return 1;

		if ( pEvnt2->content_desc.desc_bytes &&
			 ( pEvnt1->content_desc.desc_bytes != pEvnt2->content_desc.desc_bytes ||
			   memcmp( pEvnt1->content_desc.desc_ptr, pEvnt2->content_desc.desc_ptr, pEvnt1->content_desc.desc_bytes ) ) )
			return 1;
	} else
	{
		if ( pEvnt1->title_crc32   != CalTSCRC32( pEvnt2->title.desc_ptr, pEvnt2->title.desc_bytes ) )
			return 1;
		if ( pEvnt1->message_crc32 != CalTSCRC32( pEvnt2->message.desc_ptr, pEvnt2->message.desc_bytes ) )
			return 1;
		if ( pEvnt1->rating_crc32  != CalTSCRC32( pEvnt2->rating.desc_ptr, pEvnt2->rating.desc_bytes  ) )
			return 1;
		if ( pEvnt1->content_desc_crc32 != CalTSCRC32( pEvnt2->content_desc.desc_ptr, pEvnt2->content_desc.desc_bytes ) )
			return 1;
	}

	return 0;
}

static void UpdateEvnt( EVNT *pEvnt1, EVNT *pEvnt2, int bNotSaveMesg )
{
	pEvnt1->start_time = pEvnt2->start_time;
	pEvnt1->duration_length = pEvnt2->duration_length;

	if ( bNotSaveMesg == 0 )
	{
		if ( pEvnt2->title.desc_bytes )
			FillDescData( &pEvnt1->title, pEvnt2->title.desc_ptr, pEvnt2->title.desc_bytes );
			 
		if ( pEvnt2->message.desc_bytes )
			FillDescData( &pEvnt1->message, pEvnt2->message.desc_ptr, pEvnt2->message.desc_bytes );

		if ( pEvnt2->rating.desc_bytes )
			FillDescData( &pEvnt1->rating, pEvnt2->rating.desc_ptr, pEvnt2->rating.desc_bytes );

		if ( pEvnt2->content_desc.desc_bytes )
			FillDescData( &pEvnt1->content_desc, pEvnt2->content_desc.desc_ptr, pEvnt2->content_desc.desc_bytes );
	} else
	{
		pEvnt1->title_crc32		   = CalTSCRC32( pEvnt2->title.desc_ptr, pEvnt2->title.desc_bytes );
		pEvnt1->message_crc32	   = CalTSCRC32( pEvnt2->message.desc_ptr, pEvnt2->message.desc_bytes );
		pEvnt1->rating_crc32	   = CalTSCRC32( pEvnt2->rating.desc_ptr, pEvnt2->rating.desc_bytes  );
		pEvnt1->content_desc_crc32 = CalTSCRC32( pEvnt2->content_desc.desc_ptr, pEvnt2->content_desc.desc_bytes );
	}

}

static void ExpendEnvtRow( EVNT_COL *pEvntCol )
{
	int new_row_num;
	EVNT *new_evnt_row;
	new_row_num = pEvntCol->total_row_num + EVEN_COL_NUM;
	new_evnt_row = SAGETV_MALLOC( sizeof(EVNT)*new_row_num ) ;
	if ( pEvntCol->evnt )
	{
		memcpy( new_evnt_row, pEvntCol->evnt, sizeof(EVNT)*pEvntCol->total_row_num );
		SAGETV_FREE( pEvntCol->evnt );
	}
	pEvntCol->evnt = new_evnt_row;
	pEvntCol->total_row_num = new_row_num;
}

static void ReleaseEvntRow( EVNT_COL *pEvntCol )
{
	int i;
	for ( i = 0; i<pEvntCol->row_num; i++ )
	{
		ReleaseEvntData( &pEvntCol->evnt[i] );
	}
	SAGETV_FREE( pEvntCol->evnt );
	pEvntCol->evnt = NULL;
	pEvntCol->row_num = 0;
	pEvntCol->total_row_num = 0;
}

static EVNT* FindEvntCell( EVNT_COL *pEvntCol, uint16_t EventId )
{
	int i;
	for ( i = 0; i<pEvntCol->row_num; i++ )
	{
		if ( pEvntCol->evnt[i].event_id == EventId )
			return &pEvntCol->evnt[i];
	}
	return NULL;
}

static EVNT* AddEvnt( EVNT_COL *pEvntCol, uint16_t EeventId )
{
	int i, k;
	for ( i = 0; i<pEvntCol->row_num; i++ )
	{
		if ( pEvntCol->evnt[i].event_id == EeventId )
			return &pEvntCol->evnt[i];
	}
	if ( pEvntCol->row_num >= pEvntCol->total_row_num )
		ExpendEnvtRow( pEvntCol );

	k = pEvntCol->row_num++;
	pEvntCol->evnt[k].event_id = EeventId;

	return &pEvntCol->evnt[k];
}

static void ExpendEnvtList( EVNT_LIST *pEvntList )
{
	int new_col_num;
	EVNT_COL* new_evnt_list;
	new_col_num = pEvntList->total_col_num + EVEN_COL_NUM;
	new_evnt_list = SAGETV_MALLOC( sizeof(EVNT_COL)*new_col_num );
	if ( pEvntList->evnt_col )
	{
		memcpy( new_evnt_list, pEvntList->evnt_col, sizeof(EVNT_COL)*pEvntList->total_col_num );
		SAGETV_FREE( pEvntList->evnt_col );
	}
	pEvntList->evnt_col = new_evnt_list;
	pEvntList->total_col_num = new_col_num;
}


static void ReleaseEnvtList( EVNT_LIST *pEvntList )
{
	int i;
	for ( i = 0; i<pEvntList->col_num; i++ )
	{
		ReleaseEvntRow( &pEvntList->evnt_col[i] );
	}
	SAGETV_FREE( pEvntList->evnt_col );
	pEvntList->evnt_col = NULL;
	pEvntList->col_num = 0;
	pEvntList->total_col_num = 0;

	pEvntList->total_even_num = 0;
}


EVNT* FindEvnt( EVNT_LIST *pEvntList, uint16_t ONID, uint16_t TSID, uint16_t SID, uint16_t EeventId )
{
	int i;
	for ( i = 0; i<pEvntList->col_num; i++ )
	{
		if ( pEvntList->evnt_col[i].onid == ONID && pEvntList->evnt_col[i].tsid == TSID && pEvntList->evnt_col[i].sid == SID )
		{
			return FindEvntCell( &pEvntList->evnt_col[i], EeventId );
		} 
	}
	return NULL;
}

EVNT* AddNewEvnt( EVNT_LIST *pEvntList, uint16_t ONID, uint16_t TSID, uint16_t SID, uint16_t EeventId )
{
	int i, k;
	for ( i = 0; i<pEvntList->col_num; i++ )
	{
		if ( pEvntList->evnt_col[i].onid == ONID && pEvntList->evnt_col[i].tsid == TSID && pEvntList->evnt_col[i].sid == SID )
		{
			return AddEvnt( &pEvntList->evnt_col[i], EeventId );
		} 
	}
	if ( pEvntList->col_num >= pEvntList->total_col_num )
		ExpendEnvtList( pEvntList );
	
	k = pEvntList->col_num++;
	pEvntList->evnt_col[k].onid = ONID;
	pEvntList->evnt_col[k].tsid = TSID;
	pEvntList->evnt_col[k].sid  = SID;

	return AddEvnt( &pEvntList->evnt_col[k], EeventId );
}

int TotalEnvtNum( EVNT_LIST *pEvntList )
{
	int i, sum;
	sum = 0;
	for ( i = 0; i<pEvntList->col_num; i++ )
		 sum += pEvntList->evnt_col[i].row_num;

	return sum;
}

int TotalEnvtCellNum( EVNT_LIST *pEvntList )
{
	int i, sum;
	sum = 0;
	for ( i = 0; i<pEvntList->total_col_num; i++ )
		 sum += pEvntList->evnt_col[i].total_row_num;

	return sum;
}

void SortEvntRow( EVNT_COL *pEvntCol )
{
	int i, j;
	for ( i = 0; i<pEvntCol->row_num; i++ )
	{
		for ( j = i+1; j<pEvntCol->row_num; j++ )
		{
			if ( pEvntCol->evnt[i].start_time > pEvntCol->evnt[j].start_time )
			{
				EVNT e_swap;
				e_swap = pEvntCol->evnt[i];
				pEvntCol->evnt[i] = pEvntCol->evnt[j];
				pEvntCol->evnt[j] = e_swap;
			}
		}
	}
}

void SortEvnts( EVNT_LIST *pEvntList )
{
	int i, j;
	for ( i = 0; i<pEvntList->total_col_num; i++ )
	{
		
		for ( j= i+1; j<pEvntList->total_col_num; j++ )
		{
			uint16_t onid1, tsid1, sid1;
			uint16_t onid2, tsid2, sid2;
			onid1 = pEvntList->evnt_col[i].onid;
			tsid1 = pEvntList->evnt_col[i].tsid;
			sid1 = pEvntList->evnt_col[i].sid;

			onid2 = pEvntList->evnt_col[j].onid;
			tsid2 = pEvntList->evnt_col[j].tsid;
			sid2 = pEvntList->evnt_col[j].sid;

			if ( onid1 && tsid1  && onid2 && tsid2  && 
				 ( onid1 > onid2 || tsid1 > tsid2 || sid1 > sid2 ) )
			{
				EVNT_COL ec_swap;
				ec_swap = pEvntList->evnt_col[i];
				pEvntList->evnt_col[i] = pEvntList->evnt_col[j];
				pEvntList->evnt_col[j] = ec_swap;
			}
		}
		
		SortEvntRow( &pEvntList->evnt_col[i] );
	}
}

int DVBFormatEPG( uint16_t onid, uint16_t tsid, uint16_t sid, uint32_t language_code, EVNT *evnt, DESC_DATA* desc );
void DumpAllEvnts( DVB_PSI* pDVBPSI )
{
	int i,j;
	EVNT *evnt;
	DESC_DATA* desc= CreateDesc( );

	SageLog(( _LOG_TRACE, 3, TEXT("EPG num:%d of total:%d"), 
		        TotalEnvtNum(&pDVBPSI->evnt_list), TotalEnvtCellNum(&pDVBPSI->evnt_list) ));

	//if ( pDVBPSI->not_save_epg_message )
	//	return;

	SortEvnts( &pDVBPSI->evnt_list );
	for ( i = 0; i<pDVBPSI->evnt_list.col_num; i++ )
	{
		for ( j = 0; j<pDVBPSI->evnt_list.evnt_col[i].row_num; j++ )
		{
			uint16_t onid, tsid, sid;
			onid = pDVBPSI->evnt_list.evnt_col[i].onid;
			tsid = pDVBPSI->evnt_list.evnt_col[i].tsid;
			sid =  pDVBPSI->evnt_list.evnt_col[i].sid;
			evnt = &pDVBPSI->evnt_list.evnt_col[i].evnt[j];
			DVBFormatEPG(  onid, tsid, sid, pDVBPSI->language_code, evnt, desc );
			SageLog(( _LOG_TRACE, 3, TEXT("%s"), desc->desc_ptr ) );
			ReleaseDescData( desc );
		}
	}
	ReleaseDesc( desc );

}

static int DVBFormatEPG( uint16_t onid, uint16_t tsid, uint16_t sid, uint32_t language_code, EVNT *evnt, DESC_DATA* desc )
{
	int len, pos, event_num;
	uint8_t *p;
	int event_index, item_index;
	int bytes;
	uint8_t tmp[256];

	MESG256 title;
	MESG256 mesg;
	STRING256 rating;
	STRING256 genre;


	len = 64;
	bytes = 0;
	{
		int k = 0;
		int n = NumberOfShortEeventDesc( &evnt->title );
		for ( k = 0; k<n; k++ )
		{
			bytes = UnpackShortEeventDesc( &evnt->title, 0, &title  )+64;
			if ( language_code == title.language_code )
				break;
		}
		len += bytes;
	}

	event_num = NumberOfExtendEeventDesc( &evnt->message );
	bytes = 0;
	for ( event_index = 0; event_index<event_num; event_index++ )
	{
		int mesg_items;
		if ( LanguageOfExtendEeventDesc( &evnt->message, event_index ) == language_code )
		{
			bytes += BytesOfExtendEeventDesc( &evnt->message, event_index, MESSAGE_INDEX )+26;
			if (( mesg_items = ItemsOfExtendEeventDesc( &evnt->message, event_index )) > 0 )
			{
				for ( item_index = 0; item_index<mesg_items; item_index++ )
				{
					bytes += BytesOfExtendEeventDesc( &evnt->message, event_index, item_index )+64+3;
				}
			}
		}
	}
	len += bytes;

	len += UnpackPrentalRating( &evnt->rating, &rating );
	len += UnpackContentGenre( &evnt->content_desc, &genre );

	p = NewDescData( desc, len ); //alloc buffer for message
	pos = snprintf( (char*)p, len,  "EPG-1|%d-%d-%d %s|%s|%d|",
					onid, tsid, sid, "DT", 	UTCFormat(evnt->start_time, (char*)tmp, sizeof(tmp)), evnt->duration_length );

	pos += snprintf( (char*)p+pos, len-pos, "%s", Language(language_code, (char*)tmp ) );
	p[pos++] = '|';

	if ( title.title_bytes && title.title_bytes + pos+32 < len)
	{
		int n;
		bytes = FilterDVBTextCtrlCode( title.title_data, title.title_bytes );
		n = snprintf( (char*)p+pos, len-pos, "%s[len=%d]", title.charset_code,  bytes );
		pos += n;
		memcpy( p+pos, title.title_data, bytes );
		pos += bytes;
	}
	p[pos++] = '|';
	if ( title.body_bytes && title.body_bytes + pos+32 < len )
	{
		int n;
		bytes = FilterDVBTextCtrlCode( title.body_data, title.body_bytes );
		n = snprintf( (char*)p+pos, len-pos, "%s[len=%d]", title.charset_code, bytes );
		pos += n;
		memcpy( p+pos, title.body_data, bytes );
		pos += bytes;
	}


	//extend message
	for ( event_index = 0; event_index < event_num; event_index++ )
	{
		if ( LanguageOfExtendEeventDesc( &evnt->message, event_index ) == language_code )
		{
			if ( UnpackExtendEeventDesc( &evnt->message, event_index, MESSAGE_INDEX, &mesg  ) > 0 )
			{
				int n;
				bytes = FilterDVBTextCtrlCode( mesg.body_data, mesg.body_bytes );
				n = snprintf( (char*)p+pos, len-pos, "%s[len=%d]", mesg.charset_code, bytes );
				pos += n;
				memcpy( p+pos, mesg.body_data, bytes );
				pos += bytes;
			}
		}
	}

	for ( event_index = 0; event_index < event_num; event_index++ )
	{
		if ( LanguageOfExtendEeventDesc( &evnt->message, event_index ) == language_code )
		{
			int mesg_items;
			if (( mesg_items = ItemsOfExtendEeventDesc( &evnt->message, event_index )) > 0 )
			{
				int k;
				for ( k = 0; k<mesg_items; k++ )
				{
					int n;
					UnpackExtendEeventDesc( &evnt->message, event_index, k, &mesg  );
					p[pos++]='{';
					n = snprintf( (char*)p+pos, len-pos, "%s[len=%d]", mesg.charset_code, mesg.title_bytes );
					pos += n;
					bytes = FilterDVBTextCtrlCode( mesg.title_data, mesg.title_bytes );
					memcpy( p+pos, mesg.title_data, bytes );
					pos += bytes;
					p[pos++]=':';
					bytes = FilterDVBTextCtrlCode( mesg.body_data, mesg.body_bytes );
					n = snprintf( (char*)p+pos, len-pos, "%s[len=%d]", mesg.charset_code, bytes );
					pos += n;
					memcpy( p+pos, mesg.body_data, bytes );
					pos += bytes;
					
					p[pos++]='}';
				}
			}
		}
	}

	p[pos++] = '|';
	memcpy( p+pos, rating.data, rating.bytes );
	pos += rating.bytes ;

	p[pos++] = '|';
	memcpy( p+pos, genre.data, genre.bytes );
	pos += genre.bytes ;

	p[pos++] = '|';
	p[pos++] = 0x0;
	desc->desc_bytes = pos+1;

	return pos;

}
//////////////////////////////////////////////////////////////////////
static int NumberOfShortEeventDesc( DESC_DATA *pDescData )
{
	int total_bytes = pDescData->desc_bytes;
	uint8_t *pData = pDescData->desc_ptr;
	int desc_len;
	int event_number = 0;

	if ( total_bytes == 0 )
		return 0;

	while ( ( pData = GetDescriptor( pData, total_bytes, SHORT_EVENT_TAG, &desc_len ) )!= NULL && desc_len > 0 )
	{
		event_number++;
		pData += desc_len + 2;
		total_bytes -= desc_len + 2;
	}

	return event_number;
}

static uint8_t* GetShortEeventDesc( uint8_t *pData, uint16_t nBytes, int nIndex, int *pLength )
{
	int event_number = 0;
	*pLength = 0;
	if ( nBytes == 0 )
		return NULL;

	while ( ( pData = GetDescriptor( pData, nBytes, SHORT_EVENT_TAG, pLength ) )!= NULL && *pLength > 0 )
	{
		if ( event_number++ == nIndex )
		{
			*pLength += 2; //include tag header 2 bytes;
			return pData;
		}

		pData  += *pLength + 2;
		nBytes -= *pLength + 2;
	}

	*pLength = 0;
	return NULL;
}

/*
static int BytesOfShortEeventDesc( DESC_DATA *pDescData, int nEeventIndex )
{
	int title_length, mesg_length;
	int total_bytes;
	int code_byte;
	uint8_t *pData;

	pData = GetShortEeventDesc( pDescData->desc_ptr, pDescData->desc_bytes, nEeventIndex, &total_bytes );

	//skip 2 bytes of descriptor and length
	pData += 2;
	total_bytes -= 2;

	if ( total_bytes < 4 )
		return 0;

	title_length = (uint8_t)pData[3];
	if ( title_length == 0 ) 
		return 0;

	code_byte = ParserDVBTextCharSet( pData+4, title_length );
	title_length -= code_byte;
	if ( title_length + 1 > total_bytes-4 )
		return title_length;

	mesg_length  = pData[title_length+4];
	if (  mesg_length + title_length+1 <= total_bytes - 4 )
	{
		code_byte = ParserDVBTextCharSet( pData+4+title_length+1, mesg_length );
		mesg_length -= code_byte;
	}

	return title_length + mesg_length;
}
*/

static uint32_t LanguageOfShortEeventDesc( DESC_DATA *pDescData, int nEeventIndex )
{
	int total_bytes;
	uint8_t *pData;

	pData = GetShortEeventDesc( pDescData->desc_ptr, pDescData->desc_bytes, nEeventIndex, &total_bytes );

	//skip 2 bytes of descriptor and length
	pData += 2;
	total_bytes -= 2;

	if ( total_bytes < 3 )		return 0;
	return  LanguageCode( pData );//( pData[2] <<16 )|(pData[1]<<8)|(pData[0]);
}


static int UnpackShortEeventDesc( DESC_DATA *pDescData, int nEeventIndex, MESG256* pMesg  )
{
	int title_length, mesg_length, len;
	int total_bytes;
	int code_byte;
	uint8_t *pData;

	pData = GetShortEeventDesc( pDescData->desc_ptr, pDescData->desc_bytes, nEeventIndex, &total_bytes );

	//skip 2 bytes of descriptor and length
	pData += 2;
	total_bytes -= 2;

	pMesg->title_bytes = 0;
	pMesg->body_bytes = 0;
	if ( total_bytes < 4 )
		return 0;

	pMesg->language_code = LanguageCode( pData ); //( pData[2] <<16 )|(pData[1]<<8)|(pData[0]);
	pMesg->charset_code = (uint8_t*)"";

	title_length = (uint8_t)pData[3];
	if ( title_length == 0 ) return 0;
	if ( title_length < total_bytes-4 )
	{
		uint8_t *p = pData+4;
		len = title_length;
		code_byte = ParserDVBTextCharSet( p, len );
		pMesg->charset_code = (uint8_t*)DVBTextCharSetName( p, len );
		len -= code_byte;
		memcpy( pMesg->title_data, p+code_byte, len );
		pMesg->title_bytes = len;
	}

	if ( title_length + 4 + 1 >= total_bytes )
		return pMesg->title_bytes;

	mesg_length  = pData[title_length+4];
	if ( mesg_length == 0 )
		return pMesg->title_bytes;

	if (  mesg_length + title_length+1 <= total_bytes - 4 )
	{
		uint8_t *p = pData+4+title_length+1;
		len = mesg_length;
		code_byte = ParserDVBTextCharSet( p, len );
		pMesg->charset_code = (uint8_t*)DVBTextCharSetName( p, len );
		len -= code_byte;
		memcpy( pMesg->body_data, p+code_byte, len );
		pMesg->body_bytes = len;
	}

	return pMesg->title_bytes+pMesg->body_bytes;
}

////////////////////////////////////////////////////////////////
static int NumberOfExtendEeventDesc( DESC_DATA *pDescData )
{
	int total_bytes = pDescData->desc_bytes;
	uint8_t *pData = pDescData->desc_ptr;
	int desc_len;
	int event_number = 0;

	if ( total_bytes == 0 )
		return 0;

	while ( ( pData = GetDescriptor( pData, total_bytes, EXTENDED_EVENT_TAG, &desc_len ) )!= NULL && desc_len > 0 )
	{
		event_number++;
		pData += desc_len + 2;
		total_bytes -= desc_len + 2;
	}

	return event_number;
}

static uint8_t* GetExtendEeventDesc( uint8_t *pData, uint16_t nBytes, int nIndex, int *pLength )
{
	int event_number = 0;

	*pLength = 0;
	if ( nBytes == 0 )
		return NULL;

	while ( ( pData = GetDescriptor( pData, nBytes, EXTENDED_EVENT_TAG, pLength ) )!= NULL && *pLength > 0 )
	{
		if ( event_number++ == nIndex )
		{
			*pLength += 2; //include tag header 2 bytes;
			return pData;
		}

		pData  += *pLength + 2;
		nBytes -= *pLength + 2;
	}

	*pLength = 0;
	return NULL;
}

static int ItemsOfExtendEeventDesc( DESC_DATA *pDescData, int nEeventIndex )
{
	int len, i;
	int item_bytes;
	int total_bytes;
	uint8_t *pData;
	uint8_t* p;

	pData = GetExtendEeventDesc( pDescData->desc_ptr, pDescData->desc_bytes, nEeventIndex, &total_bytes );

	//skip 2 bytes of descriptor and length
	pData += 2;
	total_bytes -= 2;

	if ( total_bytes < 4 )		return 0;

	item_bytes = (uint8_t)pData[4];
	
	p = (uint8_t*)pData+5; 
	total_bytes -= 5;
	item_bytes = _MIN( total_bytes, item_bytes );

	i = 0; 
	len = 0; 
	while ( len < item_bytes  )
	{
		//skip item description data
		if ( p[0] == 0 || len+1+(int)p[0] > item_bytes )
			return i;

		len += 1 + (uint8_t)p[0];
		p   += 1 + (uint8_t)p[0];

		//skip item char data
		if ( p[0] == 0 || len+1+(int)p[0] > item_bytes )
			return i;

		len += 1 + (uint8_t)p[0];
		p += 1 + (uint8_t)p[0];
		i++;
	}

	return i;
}

//if nItemIndex = MESSAGE_INDEX, read text into message body
static int BytesOfExtendEeventDesc( DESC_DATA *pDescData, int nEeventIndex, int nItemIndex )
{
	int len, bytes, i;
	int total_bytes;
	uint8_t *pData;
	int item_bytes;
	int title_bytes, body_bytes;
	int code_byte; 
	uint8_t* p;

	pData = GetExtendEeventDesc( pDescData->desc_ptr, pDescData->desc_bytes, nEeventIndex, &total_bytes );

	//skip 2 bytes of descriptor and length
	pData += 2;
	total_bytes -= 2;

	title_bytes = 0;
	body_bytes = 0;

	if ( total_bytes < 4 )		return 0;

	item_bytes = (uint8_t)pData[4];
	
	if ( nItemIndex == MESSAGE_INDEX )
	{
		p = (uint8_t*)pData+5+item_bytes; 
		if ( p[0] && 5+item_bytes + p[0] < total_bytes )
		{
			bytes = (int)p[0];
			code_byte = ParserDVBTextCharSet( p+1, bytes );
			bytes -= code_byte;
			return  bytes;
		}
		return 0;
	} else
	if ( item_bytes > 0 )
	{
		p = (uint8_t*)pData+5; 
		total_bytes -= 5;
		item_bytes = _MIN( total_bytes, item_bytes );

		i = 0; 
		len = 0; 
		while ( len < item_bytes  )
		{
			if ( nItemIndex == i )
				break;

			//skip item description data
			if ( p[0] == 0 || len+1+(int)p[0] > item_bytes )
				return 0;
			len += 1 + (uint8_t)p[0];
			p   += 1 + (uint8_t)p[0];

			//skip item char data
			if ( p[0] == 0 || len+1+(int)p[0] > item_bytes )
				return 0;
			len += 1 + (uint8_t)p[0];
			p += 1 + (uint8_t)p[0];
			i++;
		}

		bytes = p[0];
		if ( p[0] && len+1+bytes <= item_bytes )
		{
			code_byte = ParserDVBTextCharSet( p+1, bytes );
			title_bytes = bytes-code_byte;
		}

		len += 1 + (uint8_t)p[0];
		p   += 1 + (uint8_t)p[0];
		bytes = p[0];
		if ( p[0] && len+1+bytes <= item_bytes )
		{
			code_byte = ParserDVBTextCharSet( p+1, bytes );
			body_bytes = bytes-code_byte;
		}

	}

	return title_bytes + body_bytes;

}

static uint32_t LanguageOfExtendEeventDesc( DESC_DATA *pDescData, int nEeventIndex )
{
	int total_bytes;
	uint8_t *pData;

	pData = GetExtendEeventDesc( pDescData->desc_ptr, pDescData->desc_bytes, nEeventIndex, &total_bytes );

	//skip 2 bytes of descriptor and length
	pData += 2;
	total_bytes -= 2;


	if ( total_bytes < 4 )		return 0;
	return  LanguageCode( pData+1 );//( pData[3] <<16 )|(pData[2]<<8)|(pData[1]);
}

static int UnpackExtendEeventDesc( DESC_DATA *pDescData, int nEeventIndex, int nItemIndex, MESG256* pMesg  )
{
	int len, bytes, code_byte;
	int total_bytes;
	uint8_t *pData;
	int item_no, item_last_no, item_bytes;
	uint8_t* p;
	int i;

	pData = GetExtendEeventDesc( pDescData->desc_ptr, pDescData->desc_bytes, nEeventIndex, &total_bytes );

	//skip 2 bytes of descriptor and length
	pData += 2;
	total_bytes -= 2;

	pMesg->title_bytes = 0;
	pMesg->body_bytes = 0;

	if ( total_bytes < 4 )		return 0;

	pMesg->language_code = LanguageCode( pData+1 );//( pData[3] <<16 )|(pData[2]<<8)|(pData[1]);
	item_no = ( pData[0] >> 4 ) & 0x0f;
	item_last_no = ( pData[0] & 0x0f );
	item_bytes = (uint8_t)pData[4];
	
	//read text
	if ( nItemIndex == MESSAGE_INDEX )
	{
		p = (uint8_t*)pData+5+item_bytes; 
		if ( p[0] && 5+item_bytes + p[0] < total_bytes )
		{
			bytes = (int)p[0];
			code_byte = ParserDVBTextCharSet( p+1, bytes );
			pMesg->charset_code = (uint8_t*)DVBTextCharSetName( p+1, bytes );
			bytes -= code_byte;
			memcpy( pMesg->body_data, p+1+code_byte, bytes );
			pMesg->body_bytes = bytes;
			
		}
		return pMesg->body_bytes;
	} else
	if ( item_bytes > 0 )
	{
		p = (uint8_t*)pData+5; 
		total_bytes -= 5;
		item_bytes = _MIN( total_bytes, item_bytes );

		i = 0; 
		len = 0; 
		while ( len < item_bytes  )
		{
			if ( nItemIndex == i )
				break;

			//skip item description data
			if ( p[0] == 0 || len+1+(int)p[0] > item_bytes )
				return 0;
			len += 1 + (uint8_t)p[0];
			p   += 1 + (uint8_t)p[0];

			//skip item char data
			if ( p[0] == 0 || len+1+(int)p[0] > item_bytes )
				return 0;
			len += 1 + (uint8_t)p[0];
			p += 1 + (uint8_t)p[0];
			i++;
		}

		bytes = p[0];
		if ( p[0] && len+1+bytes <= item_bytes )
		{
			code_byte = ParserDVBTextCharSet( p+1, bytes );
			pMesg->charset_code = (uint8_t*)DVBTextCharSetName( p+1, bytes );
			bytes -= code_byte;
			memcpy( pMesg->title_data, p+1+code_byte, bytes );
			pMesg->title_bytes = bytes;
		}

		len += 1 + (uint8_t)p[0];
		p   += 1 + (uint8_t)p[0];
		bytes = p[0];
		if ( p[0] && len+1+bytes <= item_bytes )
		{
			code_byte = ParserDVBTextCharSet( p+1, bytes );
			pMesg->charset_code = (uint8_t*)DVBTextCharSetName( p+1, bytes );
			bytes -= code_byte;
			memcpy( pMesg->body_data, p+1+code_byte, bytes );
			pMesg->body_bytes = bytes;
		}
		len += 1 + (uint8_t)p[0];
		p   += 1 + (uint8_t)p[0];
	}

	return pMesg->title_bytes + pMesg->body_bytes;

}

static int UnpackPrentalRating( DESC_DATA *pDescData, STRING256 *pString ) 
{
	int i, bytes, pos, size;
	uint8_t* p, *out_p;
	uint32_t languahe_code;
	int age;
	char tmp[16];

	p = pDescData->desc_ptr;
	bytes = pDescData->desc_bytes;
	out_p = pString->data;
	size  = sizeof(pString->data);
	pos = 0;

	//skip decriptor 2 bytes.
	p += 2;
	bytes -= 2;

	for ( i = 0; i < bytes/4; i++ )
	{
		languahe_code = LanguageCode( p );
		age = (int32_t)p[3];
		if ( languahe_code && age <100 )
		{
			if ( pos )
				out_p[pos++] = ',';
			pos += snprintf( (char*)out_p+pos, size-pos, "%s-%02d", Language( languahe_code, tmp ), age );
		}
		if ( pos > size ) break;
		p += 4;
	}

	pString->bytes = pos;
	return pos;
}

char* DVBGenreCode( uint8_t code );
static int UnpackContentGenre( DESC_DATA *pDescData, STRING256 *pString ) 
{
	int i, bytes, pos, size;
	uint8_t* p, *out_p;
	uint8_t genre_code;

	p = pDescData->desc_ptr;
	bytes = pDescData->desc_bytes;
	out_p = pString->data;
	size  = sizeof(pString->data);
	pos = 0;

	//skip decriptor 2 bytes.
	p += 2;
	bytes -= 2;

	for ( i = 0; i < bytes/2; i++ )
	{
		genre_code = p[0];
		if ( genre_code )
		{
			if ( pos )
				out_p[pos++] = ',';
			pos += snprintf( (char*)out_p+pos, size-pos, "%s", DVBGenreCode( genre_code ) );
		}
		//skip user defined content description code 
		if ( pos > size ) break;
		p += 2;
	}

	pString->bytes = pos;
	return pos;

}



static int UnpackDVBEIT( DVB_PSI* pDVBPSI, TS_SECTION* pSection )
{
	SECTION_HEADER section_header;
	uint16_t sid, tsid, onid, last_table_id;
	int  bytes, total_bytes;
	uint8_t *pData;
	uint8_t* p;
	uint8_t *desc_ptr;
	int desc_len;
	int  n;
	EVNT *evnt;
	//int _mem_loc;

	UnpackSectionDataHeader( &section_header, pSection );
	if ( section_header.table_id < 0x4e && section_header.table_id > 0x6f )
		return 0;

	//if ( section_header.table_id != 0x4e && section_header.table_id != 0x4f     //0x4e actual TS present/following EIT; 0x4f other TS present/following EIT; 
	//	&& section_header.table_id != 0x5f && section_header.table_id != 0x6f ) //0x5f actual TS schedule EIT; 0x6f otherl TS schedule EIT
	//	return 0;


	pData = section_header.table_data;
	total_bytes = section_header.table_bytes;
	if ( total_bytes <= 0 )		return 0;

	evnt = CreateEvnt( );
//if ((_mem_loc=sagetv_mem_loc( evnt ))< 0 )
//SageLog(( _LOG_TRACE, 3, "ERROR: lost memory index %s.\r\n", _mem_loc ));

	sid = section_header.tsid;
	tsid       = ( pData[0] << 8 ) | pData[1];
	onid       = ( pData[2] << 8 ) | pData[3];	
	last_table_id = pData[4];
	p = pData + 6;
	bytes = 6;
	while ( bytes+10 < total_bytes )
	{
		int found;
		int running_status, free_ca_mode;
		evnt->event_id = ( p[0]<< 8 )|p[1];
		evnt->start_time      = (uint32_t) MJD2Locatime( p+2 );
		evnt->duration_length = BCDTime( p+7 );
		running_status = (p[10] >>5 )&0x07;
		free_ca_mode = (p[10] >> 4 )&0x01;
		n = (( p[10]& 0x0f )<<8 )|p[11];
		if ( n == 0 ||  bytes + n >  total_bytes ) 
			break;

		evnt->title.desc_bytes = 0;
		evnt->message.desc_bytes = 0;
		evnt->rating.desc_bytes = 0;
		evnt->content_desc.desc_bytes = 0;

		//0x:4d short event; 0x4e:extended event; 0x50:component desc; 0x54:content desc; 0x55:parent rating.
		bytes += 12;	p += 12;   
		if ( bytes > total_bytes ) break;

		found = 0;
		
		{  //handle multiple short event and extend events in EIT
			//parse short event in EIT
			uint8_t *pn = p;
			int ct = n;
			while ( ( desc_ptr = GetDescriptor( pn, ct, SHORT_EVENT_TAG, &desc_len ) )!= NULL && desc_len > 0 )
			{
				AppendDescData( &evnt->title, desc_ptr, desc_len+2 );
				found += 1;
				pn = desc_ptr + desc_len + 2;
				ct = n - (int)(pn-p);
			}

			//parse extend event in EIT
			pn = p;
			ct = n;
			while ( ( desc_ptr = GetDescriptor( pn, ct, EXTENDED_EVENT_TAG, &desc_len ) )!= NULL && desc_len > 0 )
			{
				AppendDescData( &evnt->message, desc_ptr, desc_len+2 );
				found += 1;
				pn = desc_ptr + desc_len + 2;
				ct = n - (int)(pn-p);
			}
		}

		if ( ( desc_ptr = GetDescriptor( p, n, DVB_PRENTAL_RATING_TAG, &desc_len ) )!= NULL && desc_len > 0 )
		{
			FillDescData( &evnt->rating, desc_ptr, desc_len+2 );
			found += 1;
		}

		if ( ( desc_ptr = GetDescriptor( p, n, DVB_CONTENT_DESC_TAG, &desc_len ) )!= NULL && desc_len > 0 )
		{
			FillDescData( &evnt->content_desc, desc_ptr, desc_len+2 );
			found += 1;
		}

		if ( found ) 
		{
			EVNT* evnt_cell;

			//language setting
			if ( pDVBPSI->language_code == 0 ) 
			{
				char tmp[8];
				uint32_t default_lan_code=0, language_code;
				int k, n;
				n = NumberOfShortEeventDesc( &evnt->title );

				//check user setup default lauguage exits
				if ( pDVBPSI->psi_parser->language_code )
				{
					for ( k = 0; k<n; k++ )
					{
						language_code = LanguageOfShortEeventDesc( &evnt->title, k );
						if ( pDVBPSI->psi_parser->language_code == language_code )
							pDVBPSI->language_code = pDVBPSI->psi_parser->language_code;
					}
				}

				if ( pDVBPSI->language_code == 0 ) 
				{
					for ( k = 0; k<n; k++ )
					{
						language_code = LanguageOfShortEeventDesc( &evnt->title, k );
						//SageLog(( _LOG_TRACE, 3, TEXT("found a new language \"%s\" in DVB PSI."),  Language(language_code, tmp) ));
						if ( pDVBPSI->psi_parser->dumper.message_dumper != NULL )
						{
							MESSAGE_DATA message_data;
							char buf[32];
							snprintf( buf, sizeof(buf), "%s", Language(language_code, tmp) );
							memcpy( message_data.title, "LANGUAGE", 9 );
							message_data.message = (uint8_t*)buf;
							message_data.message_length = (uint16_t)strlen(buf);
							message_data.buffer = (uint8_t*)buf;
							message_data.buffer_length = (uint16_t)sizeof(buf);
							pDVBPSI->psi_parser->dumper.message_dumper( pDVBPSI->psi_parser->dumper.message_context, &message_data, sizeof(message_data) );
						}
						if ( default_lan_code == 0 ) //use first one as default lanuage
							default_lan_code = language_code;
					}
				}
				if ( pDVBPSI->language_code == 0 && default_lan_code ) 
				{
					pDVBPSI->language_code = default_lan_code;
					SageLog(( _LOG_TRACE, 3, TEXT("Set default language as \"%s\". in DVB PSI"),  Language(pDVBPSI->language_code, tmp) ));
				}
			}

			evnt_cell = FindEvnt( &pDVBPSI->evnt_list, onid, tsid, sid, evnt->event_id );
			if ( evnt_cell == NULL ) 
				evnt_cell = AddNewEvnt( &pDVBPSI->evnt_list, onid, tsid, sid, evnt->event_id );
			if ( evnt_cell != NULL ) 
			{
				ASSERT( evnt_cell->event_id == evnt->event_id );
				if ( CompareEvnt( evnt_cell, evnt, pDVBPSI->not_save_epg_message ) )
				{
					DESC_DATA desc={0};
					UpdateEvnt( evnt_cell, evnt, pDVBPSI->not_save_epg_message );
					
					//DumpEPG here
					if ( pDVBPSI->psi_parser->dumper.epg_dumper != NULL )
					{
						DVBFormatEPG(  onid, tsid, sid, pDVBPSI->language_code, evnt, &desc );
						pDVBPSI->psi_parser->dumper.epg_dumper( pDVBPSI->psi_parser->dumper.epg_dumper_context, desc.desc_ptr, desc.desc_bytes );
					}
					//SageLog(( _LOG_TRACE, 3, TEXT("%s"), desc.desc_ptr ) );

					ReleaseDescData( &desc );
				
				}
			}

		}

		p     += n;
		bytes += n;

	}

	ReleaseEvntData( evnt );

//if ( sagetv_mem_loc( evnt) < 0 )
//{
//	char buf[128];
//	//sagetv_mem_inf( _mem_loc, buf, sizeof(buf) );
//	SageLog(( _LOG_TRACE, 3, "ERROR: lost memory in tabel %s\r\n", buf ));
//}

	ReleaseEvnt( evnt );
	return 1;
}

static int UnpackDVBTime( DVB_PSI* pDVBPSI, uint8_t *pData, int nBytes )
{
	int bytes;
	uint32_t utc_sec;
	//table id 0x70 is TDT; table id 0x73 is TOT
	if ( pData[0] != 0x70 && pData[0] != 0x73 )
		return 0;

	if ( nBytes < 8 )		
		return 0;

	bytes = (( pData[1] & 0x0f )<<8 ) | pData[2];
	utc_sec = (uint32_t) MJD2Locatime( (uint8_t*)pData+3 );

	if ( utc_sec != pDVBPSI->system_time && pDVBPSI->psi_parser->dumper.system_time_dumper )
	{
		TIME_DATA time_data={0};
		time_data.utc_sec = utc_sec;
		pDVBPSI->psi_parser->dumper.system_time_dumper( pDVBPSI->psi_parser->dumper.system_time_context, 
		                                                 &time_data, sizeof(time_data) );
	}

	pDVBPSI->system_time = utc_sec; 

	if ( 0 ) {
		char tmp[32];
		UTCFormat( pDVBPSI->system_time, tmp, sizeof(tmp) );
		SageLog(( _LOG_TRACE, 3, TEXT("%s"), tmp ));
	}

	/* ZQ
	//skip local time offset descriptor, 
	if ( pData[0] == 0x73 && bytes + 1 <= nBytes )
	{
		bytes = (( pData[8] & 0x0f )<<8 ) | pData[9];
		if ( bytes + 8 <= nBytes && pData[10] == DVB_LOCAL_TIME_OFFSET )
		{
			uint8_t *p = pData+10+2;
			uint32_t contry_code = LanguageCode( p );
			uint8_t country_region_id = (p[3] & 0xfc)>>2;
			uint8_t local_time_offset_polarity = p[3]&0x1;
			uint16_t local_time_offset =  (uint16_t)UnpackBCDcode( p+4, 2 );
			uint32_t  time_of_change = MJD2Locatime( (uint8_t*)p+6 );
			uint16_t next_local_time_offset = (uint16_t)UnpackBCDcode( p+11, 2 );;
		}
	}
	*/

	return 1;
}

int ProcessDVBPSI( DVB_PSI* pDVBPSI, TS_PACKET *pTSPacket )
{
	uint16_t pid = pTSPacket->pid;
	const uint8_t* payload_data; int payload_size;

	payload_data = pTSPacket->data + pTSPacket->payload_offset;
	payload_size = pTSPacket->payload_bytes;

	if (  pid == 0x0010 ) //DVB NIT packets
	{   
		if ( pDVBPSI->disable_tune_inf )
			return 0;

		if ( !CheckPacketContinuity( pTSPacket, &pDVBPSI->nit_section->counter ) )
			return 0;

		if ( !UnpackSection( pTSPacket->start, pDVBPSI->nit_section, payload_data, payload_size ) )
			return 0;

		UnpackNIT(  pDVBPSI, pDVBPSI->nit_section ); 

		return 1;

	} else
	if ( pid == 0x0011 ) //DVB SDT packets
	{
		if ( pDVBPSI->disable_channel_inf )
			return 0;

		if ( !CheckPacketContinuity( pTSPacket, &pDVBPSI->sdt_section->counter ) )
			return 0;

		if ( !UnpackSection( pTSPacket->start, pDVBPSI->sdt_section, payload_data, payload_size ) )
			return 0;

		UnpackSDT(  pDVBPSI, pDVBPSI->sdt_section ); 

		return 1;
	} else
	if ( pid == 0x0012 )  //EIT
	{
		if ( pDVBPSI->psi_parser->dumper.epg_dumper == NULL )
			return 0;

		if ( !CheckPacketContinuity( pTSPacket, &pDVBPSI->eit_section->counter ) )
			return 0;

		if ( !UnpackSection( pTSPacket->start, pDVBPSI->eit_section, payload_data, payload_size ) )
			return 0;

		UnpackDVBEIT(  pDVBPSI, pDVBPSI->eit_section ); 

		return 1;

	} else
	if ( pid == 0x0014 )  //TDT and TOT (time date)
	{
		//TDT doesn't use section pack data
		uint8_t *p = (uint8_t *)payload_data;
		int size = payload_size;
		p    += *((uint8_t *)p) + 1;
		size -= *((uint8_t *)p) + 1;
		UnpackDVBTime(  pDVBPSI, p, size ); 
		return 1;
	}


	return 0;
}

///////////////////////////////////////////////////////////////////////////////////////////////
static uint32_t UnpackBCDcode( char* p, int bytes )
{
	int i;
	uint32_t val = 0;
	for ( i=0; i<bytes; i++, p++ )
	{
		val *= 10;
		val += (*p>>4) & 0x0f;
		val *= 10;
		val += (*p) & 0x0f;
	}
	return val;
}

static uint32_t MJD2Locatime( uint8_t* pMJD )
{
	uint32_t mjd;
	struct tm lt={0};
	int32_t y,m,d,wd;

	mjd = ( pMJD[0]<<8 ) | (pMJD[1]);

	y = (int32_t)((mjd - 15078.2)/365.25);
	m = (int32_t)((mjd - 14956.1-(int32_t)(y*365.25))/30.6001);
	d = (int32_t)(mjd)-14956-(int32_t)(y*365.25)-(int32_t)(m*30.6001);
	wd = (int32_t)((mjd+2)%7+1);
	if ( m == 14 || m == 15 )
	{
		y++;
		m = m-1-12;
	} else
		m = m-1;

	lt.tm_year = y;
	lt.tm_mon = m-1;
	lt.tm_mday = d;
	lt.tm_wday = wd;

	lt.tm_hour = ( pMJD[2] >> 4 )*10 + (pMJD[2] & 0x0f);
	lt.tm_min  = ( pMJD[3] >> 4 )*10 + (pMJD[3] & 0x0f);
	lt.tm_sec  = ( pMJD[4] >> 4 )*10 + (pMJD[4] & 0x0f);
	lt.tm_isdst = 0;
	return (uint32_t)mktime( &lt );
}

static int32_t BCDTime( uint8_t* pMJD )
{
	int h,m,s;
	h = ( pMJD[0] >> 4 )*10  + (pMJD[0] & 0x0f);
	m  = ( pMJD[1] >> 4 )*10 + (pMJD[1] & 0x0f);
	s  = ( pMJD[2] >> 4 )*10 + (pMJD[2] & 0x0f);
	
	return h*3600+m*60+s;
}



static int ParserDVBTextCharSet( uint8_t* code, int bytes )
{
	if ( code == NULL || bytes < 1 ) return 0;
	if ( code[0] != 0xff && code[0] >= 0x20 ) return 0;
	if ( code[0] >= 1 && code[0] <= 0x05 ) return 1;
	if ( ( code[0] >= 0x06 && code[0] <= 0x09 ) || code[0] == 0x0A || code[0] == 0x0B ||
		 ( code[0] >= 0x11 && code[0] <= 0x15 ) )  
	{
		if ( bytes > 1 )
			return 2;
		else
			return 1;
	}
	if ( code[0] == 0x10 && bytes >= 3 ) 
	{
		uint16_t chset = (code[1]<<8)|code[2];
		//if ( chset >= 0 && chset < 0x0f )
		if ( chset < 0x0f )
			return 3;
	}
	return 1;
}

static char* DVBTextCharSetName( uint8_t* code, int bytes )
{
	if ( bytes == 0 )
		return "[set=UTF-8]"; 

	if ( code[0] != 0xff && code[0] >= 0x20 )
	{
		return "[set=ISO-8859-1]";
	} else
	if ( code[0] == 1 )
	{
		return "[set=ISO-8859-5]";
	}else
	if ( code[0] == 2 )
	{
		return "[set=ISO-8859-6]";
	}else
	if ( code[0] == 3 )
	{
		return "[set=ISO-8859-7]";
	}else
	if ( code[0] == 4 )
	{
		return "[set=ISO-8859-8]";
	}else
	if ( code[0] == 5 )
	{
		return "[set=ISO-8859-9]";
	}else
	if ( code[0] == 6 )
	{
		return "[set=ISO-8859-10]";
	}else
	if ( code[0] == 7 )
	{
		return "[set=ISO-8859-11]";
	}else
	if ( code[0] == 9 )
	{
		return "[set=ISO-8859-13]";
	}else
	if ( code[0] == 0xa )
	{
		return "[set=ISO-8859-14]";
	}else
	if ( code[0] == 0xb )
	{
		return "[set=ISO-8859-15]";
	}else
	if ( code[0] == 0x11 )
	{
		return "[set=iso-10646-ucs-2]";
	}else
	if ( code[0] == 0x12 )
	{
		return "[set=ksc5601-1987]";
	}else
	if ( code[0] == 0x13 )
	{
		return "[set=gb2312-1980]";
	}else
	if ( code[0] == 0x14 )
	{
		return "[set=big5]";
	}else
	if ( code[0] == 0x15 )
	{
		return "[set=UTF-8]";
	}else
	if ( code[0] == 0x10 )
	{
		uint16_t chset = (code[1]<<8)|code[2];
		if ( chset == 0x01 )
			return "[set=ISO-8859-1]";
		else
		if ( chset == 0x02 )
			return "[set=ISO-8859-2]";
		else
		if ( chset == 0x03 )
			return "[set=ISO-8859-3]";
		else
		if ( chset == 0x04 )
			return "[set=ISO-8859-4]";
		else
		if ( chset == 0x05 )
			return "[set=ISO-8859-5]";
		else
		if ( chset == 0x06 )
			return "[set=ISO-8859-6]";
		else
		if ( chset == 0x07 )
			return "[set=ISO-8859-7]";
		else
		if ( chset == 0x08 )
			return "[set=ISO-8859-8]";
		else
		if ( chset == 0x09 )
			return "[set=ISO-8859-9]";
		else
		if ( chset == 0x0A )
			return "[set=ISO-8859-10]";
		else
		if ( chset == 0x0B )
			return "[set=ISO-8859-11]";
		else
		if ( chset == 0x0C )
			return "[set=ISO-8859-12]";
		else
		if ( chset == 0x0D )
			return "[set=ISO-8859-13]";
		else
		if ( chset == 0x0E )
			return "[set=ISO-8859-14]";
		else
		if ( chset == 0x0F )
			return "[set=ISO-8859-15]";
		else
			return "[set=UTF-8]";

	}else
	{
		return "[set=ISO-8859-1]";
	}

	return "[unknown]";
}


static int FilterDVBTextCtrlCode( uint8_t* code, int bytes ) 
{
	uint8_t* ps, *pt;
	int length = 0;
	int code_byte;

	if ( code == NULL || bytes <= 0 ) return 0;
	if ( bytes < 2 ) { return bytes; }
	
	code_byte = ParserDVBTextCharSet( code, bytes );

	ps = pt = code;
	if ( code_byte == 1 || code_byte == 0 )
	{
		while ( bytes-- > 0 )
		{
			if ( *ps != 0x86 && *ps != 0x87 && *ps != 0x8a )
			{
				*pt++ = *ps++;
				length++;
			} else
				ps++;
		}
	} else
	{
		uint16_t wd;
		while ( bytes > 2 )
		{
			wd = (*ps<<8)|*(ps+1);
			if ( wd != 0xe086 && wd != 0xe087 && wd != 0xe08a )
			{
				*pt++ = *ps++;
				*pt++ = *ps++;
				length++; length++;
			} else
			{
				ps++; ps++;
			}

			bytes--; bytes--;
		}
	}
	return length;
}

/*
static int TotalDVBTextCtrlCodeNum( uint8_t* code, int bytes )
{
	uint8_t* ps;
	int total = 0;
	int code_byte;

	if ( code == NULL || bytes <= 0 ) return 0;
	if ( bytes < 2 ) { return 0; }

	code_byte = ParserDVBTextCharSet( code, bytes );

	ps = code;
	if ( code_byte == 1 )
	{
		while ( bytes-- > 0 )
		{
			if ( *ps == 0x86 || *ps == 0x87 || *ps == 0x8a )
				total++;
			ps++;
		}
	} else
	{
		uint16_t wd;
		while ( bytes > 2 )
		{
			wd = (*ps<<8)|*(ps+1);
			if ( wd == 0xe086 || wd == 0xe087 || wd == 0xe08a )
			{
				total++; total++;
			} 

			ps++; ps++;
			bytes--; bytes--;
		}
	}
	return total;
}
*/
