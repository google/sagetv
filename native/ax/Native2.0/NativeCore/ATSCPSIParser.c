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

#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include <string.h>
#include "NativeCore.h"
#include "SectionData.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSCRC32.h"
#include "ATSCPSIParser.h"

#define TAG_EXTENDED_CHANNEL_NAME  0xA0
#define TAG_SERVICE_LOCATION	   0xA1

//ATSC descriptor tag
#define ATSC_CONTENT_ADVISORY   0x87
#define ATSC_CHANNEL_NAME		0xa0
#define ATSC_GENRE				0xab

uint8_t* _FillDescData_( DESC_DATA *pDesc, uint8_t* pData, int nBytes, int Line );

int UnpackMultipleString( uint8_t* p, int Bytes, int nCol, int nRow, STRING* pString  );
int UnpackMultipleString256( uint8_t* p, int Bytes, int nCol, int nRow, STRING256* pString  );
static uint8_t* GetStringFromMutilString( uint8_t* p, int Bytes, int nCol, int nRow, int *pStringLen );
static int UncompressHuffman( int Type, uint8_t* pOut, int MaxSize, uint8_t* pData, int Length );
static int GetUncompressBytes( int Type, uint8_t* pData, int Length );

//DESC_DATA* CreateDesc( );
//void ReleaseDesc( DESC_DATA* pDsec );
static void ReleaseEitCell( EIT *pEit );
static void ReleaseEitCells( ATSC_PSI* pATSCPSI );
static void CreateEitCells( ATSC_PSI* pATSCPSI, int nChannelNum );
static void FlushEPG( ATSC_PSI* pATSCPSI );
void DumpEPG( ATSC_PSI* pATSCPSI );

ATSC_PSI* CreateATSCPSI( PSI_PARSER* pPSIParser )
{
	ATSC_PSI *pATSCPSI = SAGETV_MALLOC( sizeof(ATSC_PSI) );
	pATSCPSI->psi_parser = pPSIParser;

	pATSCPSI->atsc_psi_section = CreateSection();

	SageLog(( _LOG_TRACE, 3, TEXT("It's an ATSC Stream") ));
	SageLog(( _LOG_TRACE, 4, TEXT("Memory footprint of PSIP of ATSC %d"), sizeof(ATSC_PSI) ));

	pATSCPSI->language_code = 0;
	pATSCPSI->disable_channel_inf = 0;
	pATSCPSI->not_save_epg_message = 0;

	return pATSCPSI;
}


void ReleaseATSCPSI( ATSC_PSI* pATSCPSI )
{
	int i;
	//DumpEPG( pATSCPSI );
	FlushEPG( pATSCPSI );
	ReleaseSection( pATSCPSI->atsc_psi_section );
	for ( i = 0; i<MAX_RTT_TBL; i++ )
	{
		if ( pATSCPSI->rtt[i].rating != NULL )
		{
			SAGETV_FREE( pATSCPSI->rtt[i].rating );
		}
	}
	ReleaseEitCells( pATSCPSI );
	SAGETV_FREE( pATSCPSI );
}

void ResetATSCPSI( ATSC_PSI* pATSCPSI )
{
	pATSCPSI->system_time = 0;
	memset( pATSCPSI->mgt, 0x0, sizeof(pATSCPSI->mgt) );
	memset( pATSCPSI->vct, 0x0, sizeof(pATSCPSI->vct) );
	pATSCPSI->vct_num = 0;
	pATSCPSI->mgt_num = 0;
	ReleaseEitCells( pATSCPSI );
	memset( pATSCPSI->eit_section_crc32, 0, sizeof(pATSCPSI->eit_section_crc32) );
	pATSCPSI->mgt_section_crc32 = 0;
	pATSCPSI->vct_section_crc32 = 0;
	pATSCPSI->rtt_section_crc32 = 0;

}

static void UnpackSTT( ATSC_PSI* pATSCPSI, TS_SECTION* pSection )
{
	uint8_t *p;
	uint32_t gps_sec, gps_sec_offset;
	int  daylight_saving, day_of_month_transition, hour_of_day_transition;
	SECTION_HEADER section_header;

	UnpackSectionDataHeader( &section_header, pSection );
	if ( section_header.table_id != 0xCD )
		return ;

	p = section_header.table_data;
	p += 1; //skip protocal version 1 bytes

	gps_sec = (p[0]<<24) | (p[1]<<16) | (p[2]<<8) | p[3] ;
	gps_sec_offset = p[4];
	daylight_saving = ( p[5] & 0x80  ) != 0 ;
	day_of_month_transition = p[5] & 0x1f;
	hour_of_day_transition = p[6] ;
	gps_sec -= gps_sec_offset;

	pATSCPSI->system_time = gps_sec;

	//if ( !daylight_saving && day_of_month_transition  && hour_of_day_transition ||  daylight_saving )
	//	gps_sec -= 3600;

	if ( pATSCPSI->psi_parser->dumper.system_time_dumper )
	{
		TIME_DATA time_data={0};
		time_data.gps_sec = gps_sec;
		pATSCPSI->psi_parser->dumper.system_time_dumper( pATSCPSI->psi_parser->dumper.system_time_context, 
		                                                 &time_data, sizeof(time_data) );
	}
}



static void UnpackMGT( ATSC_PSI* pATSCPSI, TS_SECTION* pSection )
{
	int i;
	uint8_t *p;
	int  desc_bytes;
	SECTION_HEADER section_header;

	UnpackSectionDataHeader( &section_header, pSection );
	if ( section_header.table_id != 0xC7 )
		return ;

	//we may use version to check MGT updating, but ...
	pATSCPSI->mgt_update_flag  = pATSCPSI->mgt_section_crc32 != pSection->crc32;
	pATSCPSI->mgt_section_crc32 = pSection->crc32;
	if ( pATSCPSI->mgt_update_flag == 0 ) //mgt unchange, skip it
		return;

	p = section_header.table_data;
	p += 1; //skip protocal version 1 bytes
	pATSCPSI->mgt_num = p[0]<<8 | p[1];
	p += 2;
	pATSCPSI->mgt_num = _MIN( pATSCPSI->mgt_num, MAX_MGT_TBL );
	for ( i = 0; i<pATSCPSI->mgt_num; i++ )
	{
		pATSCPSI->mgt[i].type = (p[0] << 8) | p[1];
		pATSCPSI->mgt[i].pid  = ((p[2]&0x1f)<<8) | p[3];
		pATSCPSI->mgt[i].tbl_size = (p[5]<<24) | (p[6]<<16) | (p[7]<<8) | p[8];
		desc_bytes = (( p[9] & 0x0F ) << 8 ) | p[10];
		p += 11 + desc_bytes;
		if ( pATSCPSI->psi_parser->dumper.pid_dumper )
		{	
			PID_ITEM pid_inf;
			pid_inf.pid = pATSCPSI->mgt[i].pid;
			pid_inf.type = PID_TYPE_PSI;
			pid_inf.service = pATSCPSI->mgt[i].type;
			pATSCPSI->psi_parser->dumper.pid_dumper( pATSCPSI->psi_parser->dumper.pid_context, &pid_inf, sizeof(pid_inf) );
		}
	}
}

static void UnpackVCT( ATSC_PSI* pATSCPSI, TS_SECTION* pSection )
{
	int i;
	uint8_t *p;
	int hidden;
	VCT* pVCT;
	int32_t  desc_bytes;
	uint8_t* desc_ptr; 
	int            desc_len;
	SECTION_HEADER section_header;
	int update_flag = 0;

	UnpackSectionDataHeader( &section_header, pSection );
	if ( section_header.table_id != 0xC8 && section_header.table_id != 0xC9 )
		return ;

	//we may use version to check VCT updating, but ...
	pATSCPSI->vct_update_flag  = pATSCPSI->vct_section_crc32 != pSection->crc32;
	pATSCPSI->vct_section_crc32 = pSection->crc32;
	if ( pATSCPSI->vct_update_flag == 0 ) //pat unchange, skip it
		return;

	if ( pATSCPSI->psi_parser->sub_format == 0 ) 
	{
		pATSCPSI->psi_parser->sub_format = section_header.table_id == 0xC8 ? TERRESTRIAL : section_header.table_id == 0xC9 ? CABLE : 0 ;
		if ( pATSCPSI->psi_parser->dumper.message_dumper != NULL )
		{
			MESSAGE_DATA message_data;
			uint8_t buf[32];
			snprintf( (char*)buf, sizeof(buf), "%s", StreamFormatString( pATSCPSI->psi_parser->stream_format, pATSCPSI->psi_parser->sub_format ) );
			memcpy( message_data.title, "FORMAT", 7 );
			message_data.message = buf;
			message_data.message_length = (uint16_t)strlen((char*)buf);
			message_data.buffer = buf;
			message_data.buffer_length = (uint16_t)sizeof(buf);
			pATSCPSI->psi_parser->dumper.message_dumper( pATSCPSI->psi_parser->dumper.message_context, &message_data, sizeof(message_data) );
		}
	}


	p = section_header.table_data;  // points to byte[8], 'protocol version' field
	p += 1;                 // skip protocol version (1 byte)
	pATSCPSI->vct_num = *p; // MAX_VCT_TBL; num_channels_in_section
	p += 1;                 // point to start of table

	pATSCPSI->vct_program = section_header.tsid;
	pVCT = pATSCPSI->vct;
	for ( i = 0; i<pATSCPSI->vct_num; i++ )
	{
		VCT	vct={0};
		int check_flag;
//		memcpy( vct.name, p, sizeof(vct.name) ); vct.name[sizeof(vct.name)-2] = 0x0;
		memcpy( vct.name, p, 7*2 );
		vct.major_num  = ((p[14]&0x0f)<<6) | ( p[15]>> 2 );
		vct.minor_num  = ((p[15]&0x03)<<8) | p[16];
		vct.modulation = p[17];
		vct.freq       = ( p[18]<<24 ) | (p[19]<<16) | ( p[20] << 8 ) | p[21];
		vct.tsid       = (p[22]<<8) | p[23];
		vct.program_id = (p[24]<<8) | p[25];
		hidden		   = (p[26] & 0x10) != 0;
		vct.service_type = (p[27] & 0x3f);
		vct.source_id    = (p[28]<<8) | p[29];
		desc_bytes       = (( p[30] & 0x03 ) << 8 ) | p[31];

		if ( vct.modulation == 1 ) vct.modulation = 0xfffe;  else // analog
		if ( vct.modulation == 2 ) vct.modulation = 3;       else //64 QAM
		if ( vct.modulation == 3 ) vct.modulation = 11;      else //256   QAM
		if ( vct.modulation == 4 ) vct.modulation = 23;      else //8VSB  QAM
		if ( vct.modulation == 5 ) vct.modulation = 24;      else //16VSB QAM
		if ( vct.modulation == 0 ) vct.modulation = 0;		 else
			vct.modulation = 0xffff;

		check_flag = pATSCPSI->vct[i].major_num != vct.major_num   ||
					  pATSCPSI->vct[i].minor_num != vct.minor_num   ||
					  pATSCPSI->vct[i].program_id != vct.program_id ||
					  pATSCPSI->vct[i].service_type != vct.service_type ||
					  pATSCPSI->vct[i].source_id  != vct.source_id  ||
					  pATSCPSI->vct[i].tsid  != vct.tsid ;

		if ( check_flag )
		{
			update_flag = 1;
			pATSCPSI->vct[i] = vct;
		}

		pATSCPSI->streams.num_stream = 0;
		desc_ptr = GetDescriptor( p+32, desc_bytes, TAG_SERVICE_LOCATION, &desc_len );
		if ( desc_ptr != NULL )
		{	
			int k;
			desc_ptr += 2; //skip tag and length
			pATSCPSI->streams.pcr_pid = desc_ptr[1] | (desc_ptr[0]&0x1f)<<8 ;	
			pATSCPSI->streams.num_stream = desc_ptr[2];
			desc_ptr += 3;
			if ( pATSCPSI->streams.num_stream > pATSCPSI->streams.total_streams ) 
				pATSCPSI->streams.num_stream = pATSCPSI->streams.total_streams;
			for ( k = 0; k<pATSCPSI->streams.num_stream; k++ )
			{
				pATSCPSI->streams.ts_element[k].channel_index = k;
				pATSCPSI->streams.ts_element[k].type = desc_ptr[0];
				pATSCPSI->streams.ts_element[k].pid  = desc_ptr[2] | (desc_ptr[1]&0x1f)<<8;
				pATSCPSI->streams.ts_element[k].language_code = ( desc_ptr[3] << 16 )|( desc_ptr[4] << 8 )|(desc_ptr[5]);
				desc_ptr += 6;
			}
		}
	
		//skip descpt
		p += 32 + desc_bytes;
		//service Location Descriptor 0xA1 page-72 A65/B

		//check valid channel: no hidden, in program is in PAT, ATSC digitalTV full service
		if ( !hidden && pATSCPSI->vct[i].program_id && pATSCPSI->vct[i].service_type == 2  )
			pATSCPSI->vct[i].flag = 1;

		//skip analog channel
		if ( pATSCPSI->vct[i].modulation == 0x01 )
			pATSCPSI->vct[i].flag = 0;
	}

	desc_bytes = (( p[0] & 0x03 ) << 8 ) | p[1];
	//skip descpt
	p += desc_bytes;

	//creat or renew eit cells if channel number updated
	if ( pATSCPSI->vct_num > pATSCPSI->eit_blk_num )
	{
		ReleaseEitCells( pATSCPSI );
		CreateEitCells( pATSCPSI, pATSCPSI->vct_num+4 ); 
	}

 	//dump channel information here
	if ( pATSCPSI->psi_parser->dumper.channel_info_dumper != NULL  )
	{
		int i, ret;
		CHANNEL_DATA channel_data={0};
		channel_data.update_flag   = update_flag;
		channel_data.stream_format = ATSC_STREAM;
		channel_data.sub_format  = pATSCPSI->psi_parser->sub_format;
		channel_data.num_channel = pATSCPSI->vct_num;
		for ( i = 0; i<channel_data.num_channel; i++ )
		{
			channel_data.u.atsc[i].tsid              = pATSCPSI->vct[i].tsid;
			channel_data.u.atsc[i].major_num         = pATSCPSI->vct[i].major_num;
			channel_data.u.atsc[i].minor_num         = pATSCPSI->vct[i].minor_num;
			channel_data.u.atsc[i].program_id        = pATSCPSI->vct[i].program_id;
			channel_data.u.atsc[i].u.atsc.modulation = pATSCPSI->vct[i].modulation;
			channel_data.u.atsc[i].u.atsc.freq       = pATSCPSI->vct[i].freq;
			channel_data.u.atsc[i].service_type      = pATSCPSI->vct[i].service_type;
			//memcpy( channel_data.u.atsc[i].name, pATSCPSI->vct[i].name, sizeof(channel_data.u.atsc[i].name) );
			TransJWideString( channel_data.u.atsc[i].name, sizeof(channel_data.u.atsc[i].name), 
				              (uint16_t*)pATSCPSI->vct[i].name );
			channel_data.streams = &pATSCPSI->streams;
		}
		ret = pATSCPSI->psi_parser->dumper.channel_info_dumper( pATSCPSI->psi_parser->dumper.channel_info_context, 
			                                              (uint8_t*)&channel_data, sizeof(channel_data) );
		//re resend next vct;
		if (  channel_data.command != 1  )
			pATSCPSI->vct_section_crc32 = 0; 
	}
}

static int GetChannelNum( ATSC_PSI* pATSCPSI, int nSourceId )
{
	int i;
	for ( i = 0; i<pATSCPSI->vct_num; i++ )
	{
		if ( pATSCPSI->vct[i].source_id == nSourceId )
			return i;
	}

	return -1;
}

static int UnpackRTT( ATSC_PSI* pATSCPSI, TS_SECTION* pSection )
{
	int i, len;
	uint8_t *p, *s, *p_last;
	uint16_t rating_region;
	RTT* pRTT;
	SECTION_HEADER section_header;
	int  length, num;

	UnpackSectionDataHeader( &section_header, pSection );
	if ( section_header.table_id != 0xCA )
		return 0;

	pATSCPSI->rtt_update_flag  = pATSCPSI->rtt_section_crc32 != pSection->crc32;
	pATSCPSI->rtt_section_crc32 = pSection->crc32;
	if ( pATSCPSI->rtt_update_flag == 0 ) //pat unchange, skip it
		return 1;

	rating_region = section_header.tsid & 0x00ff;
	p = section_header.table_data;
	p_last = p + section_header.table_bytes;

	//find RTT instnace or get an empty instance table
	for ( i = 0; i<MAX_RTT_TBL; i++ )
	{
		if ( pATSCPSI->rtt[i].rating_region == rating_region || pATSCPSI->rtt[i].rating_region == 0 )
			break;
	}
	
	if ( i >= MAX_RTT_TBL )  //RTT tble  is full, drop it
	{
		SageLog(( _LOG_TRACE, 2, TEXT("too many  RTT table instance in stream, I drop it")  ));
		return 0;
	}

	pRTT = &pATSCPSI->rtt[i];
	pRTT->rating_region = rating_region;
	p += 1; //skip protocal version 1 bytes

	length = *p;

	s = GetStringFromMutilString( p+1, length, 0, 0, &len );
	if ( len > 0 && s != NULL )
		memcpy( pRTT->region_name, s, _MIN(  len, sizeof(pRTT->region_name)  )  );
	
	p += length + 1;
	if ( p >= p_last ) return 0;
	num = *p;

	if ( num == 0 )
		return 0;

	//re alloc table if table is bigger than current one.
	if ( num > pRTT->dimension_num && pRTT->rating )
	{
		SAGETV_FREE( pRTT->rating );
		pRTT->rating = NULL;
		pRTT->dimension_num = 0;
	}
	if ( pRTT->rating == NULL )
	{
		pRTT->rating = SAGETV_MALLOC( num * sizeof(RATING) );
		pRTT->dimension_num = num;
	}

	p++;
	memset( pRTT->rating,  0,   sizeof(RATING) * pRTT->dimension_num );
	for ( i = 0; i<num; i++ )
	{
		int j;
		length = *p;
		s = GetStringFromMutilString( p+1, length, 0, 0, &len );
		if ( len > 0 && s != NULL )
			memcpy( pRTT->rating[i].dimension_name, s, _MIN( len, sizeof(pRTT->rating[i].dimension_name) ) );
		p += 1 + length;
		if ( p >= p_last ) return 0;

		pRTT->rating[i].graduate_scale = ( *p & 0x10 ) != 0;
		pRTT->rating[i].rating_num = *p & 0x0f;
		p++;

		for ( j = 0; j<pRTT->rating[i].rating_num; j++ )
		{
			length = *p;
			s = GetStringFromMutilString( p+1, length, 0, 0, &len );
			if ( len > 0 && s != NULL )
				memcpy( pRTT->rating[i].abbrev_name[j], s, _MIN( len, 8 )  );
			p += 1+length;
			if ( p >= p_last ) return 0;


			length = *p;
			s = GetStringFromMutilString( p+1, length, 0, 0, &len );
			if ( len > 0 && s != NULL )
				memcpy( pRTT->rating[i].rating_value[j], s, _MIN( len, 150 )  );
			p += 1+length;
			if ( p >= p_last ) return 0;

		}
	}
	
	//skip decsiptor
	return 1;
}

static int UnpackContentAdvisory( CAD *cad, uint8_t* pData, int Bytes )
{
	int i,j;
	uint8_t* p;

	p = (uint8_t*)pData;
	cad->region_num = *p & 0x3f;

	p++;
	for ( i = 0; i < cad->region_num && i < MAX_CAD_RIGION_NUM; i++ )
	{
		int length, dimension;
		cad->rating_region[i] = *p;
		dimension = *(p+1);
		dimension = _MIN( dimension, 16 );
		cad->rating_num[i] = dimension;
		p += 2;
		for ( j = 0; j<dimension; j++ )
		{
			cad->rating_index[i][j] = (*p++);
			cad->value_index[i][j] =  (*p++) & 0x0f;
		}

		length = *p++;
		if ( length > 0 )
		{	
			 UnpackMultipleString256( p, length, 0, 0, &cad->description[i] );
		}
		p += length;

		if ( p > pData + Bytes )
			break;
	}
	return 1;
}

static int ConvertAdvisoryInfo( ATSC_PSI* pATSCPSI, CAD *cad, char* buf, int max_bytes )
{
	int i, j;
	char* p; 
	int total_bytes;
	RTT *rtt;
	p = buf;

	total_bytes = 0;
	for ( i = 0; i<cad->region_num ; i++ )
	{
		rtt = NULL;
		for ( j = 0; j<MAX_RTT_TBL; j++ )
		{
			if ( pATSCPSI->rtt[j].rating_region == cad->rating_region[i] )
			{
				rtt = &pATSCPSI->rtt[j];
				break;
			}
		}
		for ( j = 0; j <cad->rating_num[i]; j++ )
		{
			if ( rtt != NULL && rtt->rating !=NULL )
			{
				int m, n;
				char* cad_name = "";
				char* cad_value = "";
				m = cad->rating_index[i][j];
				n = cad->value_index[i][j];
				if ( m < rtt->dimension_num )
				{
					cad_name = rtt->rating[m].dimension_name;
					if ( n < rtt->rating[m].rating_num )
						cad_value = rtt->rating[m].rating_value[n];
				}

				if ( cad_name[0] && cad_value[0] )
				{
					if ( strlen( p ) + strlen( cad_name) + strlen( cad_value ) + 2 < (int32_t)max_bytes )
					{
						strcat( p, cad_name ); p += (int)strlen(cad_name);
						strcat( p, ":" );	   p++;
						strcat( p, cad_value );p += (int)strlen(cad_value);
						strcat( p, "," );	   p++;
						total_bytes += 2 + (int)strlen(cad_name)+(int)strlen(cad_value);
					}
				}
			}

			if ( cad->description[i].bytes && strlen( p )+ cad->description[i].bytes+3< (int32_t)max_bytes  )
			{
				strcat(p,"(");   p++;
				memcpy( p,  (char*)cad->description[i].data, cad->description[i].bytes );
				p += cad->description[i].bytes;
				strcat(p,")," ); p +=2;
				total_bytes += 3 + cad->description[i].bytes ;
			}
		}
	}
	return total_bytes;
}

static int ConvertAdvisoryInfoBytes( ATSC_PSI* pATSCPSI, CAD *cad )
{
	int i, j;
	int total_bytes;
	RTT *rtt;

	total_bytes = 0;
	for ( i = 0; i<cad->region_num ; i++ )
	{
		rtt = NULL;
		for ( j = 0; j<MAX_RTT_TBL; j++ )
		{
			if ( pATSCPSI->rtt[j].rating_region == cad->rating_region[i] )
			{
				rtt = &pATSCPSI->rtt[j];
				break;
			}
		}
		for ( j = 0; j <cad->rating_num[i]; j++ )
		{
			if ( rtt != NULL && rtt->rating !=NULL)
			{
				int m, n;
				char* cad_name = "";
				char* cad_value = "";
				m = cad->rating_index[i][j];
				n = cad->value_index[i][j];
				if ( m < rtt->dimension_num )
				{
					cad_name = rtt->rating[m].dimension_name;
					if ( n < rtt->rating[m].rating_num )
						cad_value = rtt->rating[m].rating_value[n];
				}
				if ( cad_name[0] && cad_value[0] )
				{
					total_bytes += 2 + (int)strlen(cad_name)+(int)strlen(cad_value);
				}
			}

			if ( cad->description[i].bytes  )
			{
				total_bytes += 3 + cad->description[i].bytes;
			}
		}
	}
	return total_bytes;
}

char* GenreCode( int code );
char* GenreString( uint8_t code )
{
	if ( code < 0x20 ) //reserved
		return "";
	if ( code > 0xad ) //reserved
		return "";

	return GenreCode(code-0x20);
}

static void ReleaseEitCell( EIT *pEit )
{
	ReleaseDescData( &pEit->title );
	ReleaseDescData( &pEit->program );
	ReleaseDescData( &pEit->cad );
}

static void MoveEitCell( EIT *pEitTar, EIT *pEitSrc ) 
{
	ReleaseEitCell( pEitTar );
	*pEitTar = *pEitSrc;
	pEitSrc->title.desc_ptr = NULL;
	pEitSrc->title.desc_bytes = 0;
	pEitSrc->title.buffer_size = 0;
	pEitSrc->program.desc_ptr = NULL;
	pEitSrc->program.desc_bytes = 0;
	pEitSrc->program.buffer_size = 0;
	pEitSrc->cad.desc_ptr = NULL;
	pEitSrc->cad.desc_bytes = 0;
	pEitSrc->cad.buffer_size = 0;
	pEitSrc->event_id = 0;
	pEitSrc->source_id = 0;
	pEitSrc->start_time = 0;
	pEitSrc->during_length = 0;
	pEitSrc->program_crc32 = 0;
	pEitSrc->need_updated = 0;
	pEitSrc->genre[0] = 0;
	
}

static void CreateEitCells( ATSC_PSI* pATSCPSI, int nChannelNum )
{
	pATSCPSI->eit_blk = SAGETV_MALLOC( sizeof(EIT_COL)* nChannelNum );
	pATSCPSI->eit_blk_num = nChannelNum;
}

static void ReleaseEitCells( ATSC_PSI* pATSCPSI )
{
	int i, j;
	for ( i = 0; i<pATSCPSI->eit_blk_num; i++ )
	{
		for ( j = 0; j<pATSCPSI->eit_blk[i].eit_num; j++ )
		{
/*		
SageLog(( _LOG_TRACE, 3, TEXT("FlushEPG trace K (release EitCell %d %d 0x%x 0x%x 0x%x 0x%x 0x%x 0x%x)."), i, j, 
	&pATSCPSI->eit_blk[i].eit[j].program,  pATSCPSI->eit_blk[i].eit[j].program.desc_ptr,
	&pATSCPSI->eit_blk[i].eit[j].title,  pATSCPSI->eit_blk[i].eit[j].title.desc_ptr,
	&pATSCPSI->eit_blk[i].eit[j].cad,  pATSCPSI->eit_blk[i].eit[j].cad.desc_ptr
	 ));
*/	  			
			ReleaseEitCell( &pATSCPSI->eit_blk[i].eit[j] );
		}
	}

	SAGETV_FREE( pATSCPSI->eit_blk );
	pATSCPSI->eit_blk = NULL;
	pATSCPSI->eit_blk_num = 0;
}


static EIT* FindEitCell( ATSC_PSI* pATSCPSI, int source_id, int event_id )
{
	int i, ch;
	ch = GetChannelNum( pATSCPSI, source_id );
	if ( ch < 0 )
	{
		SageLog(( _LOG_TRACE, 2, TEXT("ERROR: invalid source id, channel not found %d!"), source_id ));
		return NULL;
	}
	for ( i = 0; i<pATSCPSI->eit_blk[ch].eit_num; i++ )
	{
		if ( pATSCPSI->eit_blk[ch].eit[i].event_id == event_id )
			return &pATSCPSI->eit_blk[ch].eit[i];
	}
	return NULL;
}


static EIT* GeEmptyEitCell( ATSC_PSI* pATSCPSI, int source_id, int event_id )
{
	int ch = GetChannelNum( pATSCPSI, source_id );
	if ( ch < 0 ) return NULL;
	if ( pATSCPSI->eit_blk[ch].eit_num < MAX_EPG_NUM_PER_CH )
	{
		return &pATSCPSI->eit_blk[ch].eit[ pATSCPSI->eit_blk[ch].eit_num++ ];
	}
	SageLog(( _LOG_TRACE, 2, TEXT("ERROR:EPG cells is full or, need remove oldest one. (ch:%d src_id:%d, evt:%d)"),
				  ch, source_id, event_id  ));

	return NULL;
}


static void UpdateEitCell( ATSC_PSI* pATSCPSI, int source_id )
{
	int i, j, ch;
	EIT_COL* eit_col;
	ch = GetChannelNum( pATSCPSI, source_id );
	if ( ch < 0 )
	{
		SageLog(( _LOG_TRACE, 2, TEXT("ERROR invalid source id, channel not found!")  ));
		return ;
	}

	eit_col = &pATSCPSI->eit_blk[ch];
	//sort Cells by start time
	{
		for ( i = 0; i<eit_col->eit_num; i++ )
		{
			for ( j = i+1; j<eit_col->eit_num; j++ )
			{
				if (  (eit_col->eit[j].start_time) < (eit_col->eit[i].start_time) )
				{
					EIT eit; //swap
					eit = eit_col->eit[i];
					eit_col->eit[i] = eit_col->eit[j];
					eit_col->eit[j] = eit;
				}
			}
		}
	}

	//cell will be full, remove old one
	if ( eit_col->eit_num >= MAX_EPG_NUM_PER_CH-1 && eit_col->eit[0].start_time < pATSCPSI->system_time )
	{
		for ( i = 1; i<eit_col->eit_num; i++  )
		{
			MoveEitCell( &eit_col->eit[i-1], &eit_col->eit[i] );
			//eit_col->eit[i-1] = eit_col->eit[i];
		}

	}

	return;
}


void DumpEPG( ATSC_PSI* pATSCPSI )
{
	int i, j, ch;
	int32_t source_id;
	STRING256 title={0};
	STRING program={0};
	CAD cad={0};
	EIT *pEit;
	int major_num, minor_num;
	VCT *pVct=NULL;

	SageLog(( _LOG_TRACE, 3, TEXT("system time GPS:%d."), pATSCPSI->system_time ));
	for ( j = 0; j<pATSCPSI->eit_blk_num; j++ )
	{
		source_id = pATSCPSI->eit_blk[j].eit[0].source_id;
		if ( source_id == 0 ) continue;
		UpdateEitCell( pATSCPSI, source_id );
		ch = GetChannelNum( pATSCPSI, source_id );
		if ( ch >= 0 )
		{
			pVct = &pATSCPSI->vct[ch];
			SageLog(( _LOG_TRACE, 3, TEXT("EPG cells on Channel (%d) %d-%d %s"), ch, pVct->major_num, pVct->minor_num, pVct->name  ));
			major_num = pVct->major_num;
			minor_num = pVct->minor_num;
		} else
		{
			major_num = 0;
			minor_num = 0;
			SageLog(( _LOG_TRACE, 2, TEXT("ERROR: invalid source id, channel not found!")  ));
		}

		for ( i = 0; i<pATSCPSI->eit_blk[j].eit_num; i++ )
		{
			pEit = &pATSCPSI->eit_blk[j].eit[i];
			if ( pEit != NULL && pEit->event_id )
			{
				int len, bytes, k;
				len = 0;

				title.bytes = 0;
				program.data.desc_bytes = 0;
				cad.region_num = 0;

				//unpack title
				if ( pEit->title.desc_bytes )
					len += UnpackMultipleString256( pEit->title.desc_ptr, pEit->title.desc_bytes, 0, 0, &title  );
				//unpack program
				if ( pEit->program.desc_bytes )
					len += UnpackMultipleString( pEit->program.desc_ptr, pEit->program.desc_bytes, 0, 0, &program  );
				//unpack CAD
				if ( pEit->cad.desc_bytes )
				{
					UnpackContentAdvisory( &cad, pEit->cad.desc_ptr+2, pEit->cad.desc_bytes-2 );
					len += ConvertAdvisoryInfoBytes( pATSCPSI, &cad );
				}
				//unpack genre
				for ( k = 0; k<MAX_GENRE_NUM; k++ )
					len += (int)strlen( GenreString(pEit->genre[k]));

				//print EPG cell
				{
					int pos;
					uint8_t* p;
					DESC_DATA *desc= CreateDesc( );

					len += 64;
					p = NewDescData( desc, len );

					pos = snprintf( (char*)p, len,  "%d|EPG-0|%d-%d %s|GPS:%d|%d|", pEit->event_id,
								major_num, minor_num, pVct==NULL ? "": pVct->minor_num == 0 ? "AN" :"DT", 
								pEit->start_time, pEit->during_length );

					memcpy( p+pos, &title.language_code, 3 );
					pos += 3;
					p[pos++] = '|';
					if ( title.bytes )
					{
						memcpy( p+pos, title.data, title.bytes );
						pos += title.bytes;
					}
					p[pos++] = '|';
					if ( program.data.desc_bytes )
					{
						memcpy( p+pos, program.data.desc_ptr, program.data.desc_bytes );
						pos += program.data.desc_bytes;
					}
					p[pos++] = '|';
					if ( pEit->cad.desc_bytes )
					{
						bytes = ConvertAdvisoryInfo( pATSCPSI, &cad, (char*)p+pos, len-64 );
						p += bytes;
					}
					p[pos++] = '|';
					{
						for ( k = 0; k<MAX_GENRE_NUM; k++ )
						{
							char *genre = GenreString(pEit->genre[k]);
							if ( (bytes = (int)strlen( genre ))>0 )
							{
								memcpy( p+pos, genre, bytes );
								if ( k+1 < MAX_GENRE_NUM && pEit->genre[k+1] )
									p[pos++] = ',';
							}
						}
					}
					p[pos++] = '|';


					SageLog(( _LOG_TRACE, 3, TEXT("%s"), desc->desc_ptr ) );

					ReleaseDescData( desc );
					ReleaseDesc( desc );

				}
				
			}
		}
	}

	ReleaseString( &program );
	return ;

}


static int ATSCFormatEPG( ATSC_PSI* pATSCPSI, EIT *pEit, DESC_DATA *pProgram,  DESC_DATA* desc )
{		
	STRING256 title={0};
	STRING program={0};
	CAD cad={0};
	VCT *pVct;
	int pos;
	uint8_t* p;
	int len, bytes, k;
	int major_num, minor_num, ch;
	len = 0;

	if ( pEit->event_id == 0 )
		return 0;

	ch = GetChannelNum( pATSCPSI, pEit->source_id );
	if ( ch >= 0 )
	{
		pVct = &pATSCPSI->vct[ch];
		//SageLog(( _LOG_TRACE, 3, TEXT("EPG cells on Channel (%d) %d-%d %s"), ch, pVct->major_num, pVct->minor_num, pVct->name  ));
		major_num = pVct->major_num;
		minor_num = pVct->minor_num;
	} else
	{
		major_num = 0;
		minor_num = 0;
		//SageLog(( _LOG_TRACE, 2, TEXT("ERROR: invalid source id, channel not found!")  ));
	}

	

	title.bytes = 0;
	program.data.desc_bytes = 0;
	cad.region_num = 0;

	//unpack title
	if ( pEit->title.desc_bytes )
		len += UnpackMultipleString256( pEit->title.desc_ptr, pEit->title.desc_bytes, 0, 0, &title  );
	//unpack program

	if ( pProgram != NULL && pProgram->desc_bytes  )
		len += UnpackMultipleString( pProgram->desc_ptr, pProgram->desc_bytes, 0, 0, &program  );
	 else
	if ( pEit->program.desc_bytes )
		len += UnpackMultipleString( pEit->program.desc_ptr, pEit->program.desc_bytes, 0, 0, &program  );
	//unpack CAD
	if ( pEit->cad.desc_bytes )
	{
		UnpackContentAdvisory( &cad, pEit->cad.desc_ptr+2, pEit->cad.desc_bytes-2 );
		len += ConvertAdvisoryInfoBytes( pATSCPSI, &cad );
	}
	//unpack genre
	for ( k = 0; k<MAX_GENRE_NUM; k++ )
		len += (int)strlen( GenreString(pEit->genre[k]));

	//print EPG cell
	{

		len += 64;
		p = NewDescData( desc, len );

		pos = snprintf( (char*)p, len,  "EPG-0|%d-%d %s|GPS:%d|%d|", 
					major_num, minor_num, minor_num == 0 ? "AN" :"DT", 
					pEit->start_time, pEit->during_length );

		memcpy( p+pos, &title.language_code, 3 );
		pos += 3;
		p[pos++] = '|';
		if ( title.bytes )
		{
			memcpy( p+pos, title.data, title.bytes );
			pos += title.bytes;
		}
		p[pos++] = '|';
		if ( program.data.desc_bytes )
		{
			memcpy( p+pos, program.data.desc_ptr, program.data.desc_bytes );
			pos += program.data.desc_bytes;
		}
		p[pos++] = '|';
		if ( pEit->cad.desc_bytes )
		{
			bytes = ConvertAdvisoryInfo( pATSCPSI, &cad,  (char*)p+pos, len-64 );
			pos += bytes;
		}
		p[pos++] = '|';
		{
			for ( k = 0; k<MAX_GENRE_NUM; k++ )
			{
				char *genre = GenreString(pEit->genre[k]);
				if ( (bytes = (int)strlen( genre )) > 0 )
				{
					memcpy( p+pos, genre, bytes );
					if ( k+1 < MAX_GENRE_NUM && pEit->genre[k+1] )
						p[pos++] = ',';
				}
			}
		}
		p[pos++] = '|';

		//SageLog(( _LOG_TRACE, 3, TEXT("%s"), desc->desc_ptr ) );
		ReleaseString( &program );

		desc->desc_bytes = pos+1;
		ASSERT( desc->desc_bytes <= desc->buffer_size );
	}
	
	return pos;
}

//flush out all epg that has no program infotmation
static void FlushEPG( ATSC_PSI* pATSCPSI )
{
	int i, j, ch;
	int32_t source_id;
	STRING program={0};
	EIT *pEit;
	int major_num, minor_num;
	VCT *pVct=NULL;

	if ( pATSCPSI->psi_parser->dumper.epg_dumper == NULL )
		return;

	for ( j = 0; j<pATSCPSI->eit_blk_num; j++ )
	{
		source_id = pATSCPSI->eit_blk[j].eit[0].source_id;
		if ( source_id == 0 ) continue;
		UpdateEitCell( pATSCPSI, source_id );
		ch = GetChannelNum( pATSCPSI, source_id );
		if ( ch >= 0 )
		{
			pVct = &pATSCPSI->vct[ch];
			SageLog(( _LOG_TRACE, 3, TEXT("EPG cells on Channel (%d) %d-%d %s."), ch, pVct->major_num, pVct->minor_num, pVct->name  ));
			major_num = pVct->major_num;
			minor_num = pVct->minor_num;
		} else
		{
			continue;
		}

		for ( i = 0; i<pATSCPSI->eit_blk[j].eit_num; i++ )
		{
			pEit = &pATSCPSI->eit_blk[j].eit[i];
			if ( pEit->event_id )
			{
				if ( pEit->program.desc_bytes == 0 )
				{
					DESC_DATA desc={0};
					DESC_DATA program={0};
					ATSCFormatEPG( pATSCPSI, pEit, &program, &desc );
					pATSCPSI->psi_parser->dumper.epg_dumper( pATSCPSI->psi_parser->dumper.epg_dumper_context, desc.desc_ptr, desc.desc_bytes );
					ReleaseDescData( &desc );
				}
			}
		}
	}
	SageLog(( _LOG_TRACE, 3, TEXT("FlushEPG is done.") ));	
	ReleaseString( &program );
	return ;

}


static int UnpackEIT( ATSC_PSI* pATSCPSI, TS_SECTION* pSection, int nType  )
{
	uint8_t *p;
	SECTION_HEADER section_header;
	uint16_t source_id;
	int  num_event;
	int  bytes, len, j, update_flag = 0;
	uint8_t* desc_ptr; 
	int            desc_len;
	uint16_t desc_length;
	EIT  eit_tmp={0};
	EIT *pEit;

	if ( pATSCPSI->vct_num == 0 ) //virtual table isn't ready
		return 0;

	UnpackSectionDataHeader( &section_header, pSection );
	if ( section_header.table_id != 0xCB )
		return 0;

	pATSCPSI->eit_update_flag  = pATSCPSI->eit_section_crc32[nType] != pSection->crc32;
	pATSCPSI->eit_section_crc32[nType] = pSection->crc32;
	if ( pATSCPSI->eit_update_flag == 0 ) //pat unchange, skip it
		return 1;

	source_id = section_header.tsid;
	p = section_header.table_data;
	bytes = section_header.table_bytes;

	//if ( pParser->message_dumper == NULL ) //parse epg is not enabled
	//	return 0;

	num_event = p[1];
	p += 2;
	len = 0;
	for ( j = 0; j<num_event; j++ )
	{
		uint16_t event_id = ( (p[0] & 0x3f) << 8 ) | p[1];
		pEit = FindEitCell( pATSCPSI, source_id, event_id );
		if ( pEit == NULL )
		{
			pEit = GeEmptyEitCell( pATSCPSI, source_id, event_id   );
			//SageLog(( _LOG_TRACE, 3, TEXT("EPG EIT src_id:%d evt_id%d"), source_id, event_id  ));
		}
		if ( pEit == NULL )
			pEit = &eit_tmp;
		pEit->source_id  = source_id;
		pEit->event_id   = event_id; 
		pEit->start_time = ( p[2] << 24) | ( p[3]<<16 ) | ( p[4]<<8 ) | p[5];
		pEit->etm_loc    = (p[6]&30)>>4;
		pEit->during_length = ((p[6]&0x0f)<<16) | (p[7]<<8) | p[8];
		desc_length = p[9];
		{
			if ( (desc_length && desc_length != pEit->title.desc_bytes) || memcmp( pEit->title.desc_ptr, p+10, desc_length ) )
			{
				FillDescData( &pEit->title, p+10, desc_length );
				EraseDescData( 	&pEit->program );	
				update_flag = 1;
			}
		}

		//skip descriptor content_advisory_descriptor(), caption_service_descriptor(), AC-3 audio_stream_descriptor()
		p   += 10 + desc_length;
		len += 10 + desc_length;
		if ( len > bytes )
		{
			SageLog(( _LOG_TRACE, 2, TEXT("broken EIT section (1), drop it")  ));
			break;
		}
				
		desc_length = ( (p[0]&0x0f)<<8 )|p[1]; 
		if ( ( p[0]& 0xf0 ) != 0xf0 )
		{
			SageLog(( _LOG_TRACE, 2, TEXT("broken EIT section (2), drop it")  ));
			break;
		}

		p += 2;

		//unpack ATSC_CONTENT_ADVISORY descraptor
		desc_ptr = GetDescriptor( p, desc_length, ATSC_CONTENT_ADVISORY, &desc_len );
		if ( desc_ptr != NULL )
		{
			FillDescData( &pEit->cad, desc_ptr, desc_len+2 );
		}

		//unpack genre
		desc_ptr = GetDescriptor( p, desc_length, ATSC_GENRE, &desc_len );
		if ( desc_ptr!= NULL )
		{
			int k;
			for ( k = 0; k<desc_len && k < sizeof(pEit->genre ); k++ )
				pEit->genre[k] = desc_ptr[k+2];
			for ( ;k < sizeof(pEit->genre ); k++ )
				pEit->genre[k] = 0;
		}

		//skip descriptors
		p   +=  desc_length;
		len += desc_length;;
		if ( len > bytes )
		{
			SageLog(( _LOG_TRACE, 2, TEXT("broken EIT section (3), drop it")  ));
			break;
		}

		////if there is no ETT, dump EIT only here
		//if ( pEit->etm_loc == 0x0 && update_flag )
		//	EPGNotifyATSC(pATSCPSI, &pATSCPSI->eit[nType][i] );
	}
	ReleaseEitCell( &eit_tmp );

	return 1;
}

static int UnpackETT( ATSC_PSI* pATSCPSI, TS_SECTION* pSection, int nType )
{
	uint8_t *p;
	SECTION_HEADER section_header;
	int  bytes;
	EIT *pEit;
	ETT	 ett={0};

	if ( pATSCPSI->vct_num == 0 ) //virtual table isn't ready
		return 0;

	UnpackSectionDataHeader( &section_header, pSection );
	if ( section_header.table_id != 0xCC )
		return 0;

	pATSCPSI->ett_update_flag  = pATSCPSI->ett_section_crc32[nType] != pSection->crc32;
	pATSCPSI->ett_section_crc32[nType] = pSection->crc32;
	if ( pATSCPSI->ett_update_flag == 0 ) //pat unchange, skip it
		return 1;

	ett.ett_ext_id = section_header.tsid;
	p = section_header.table_data;
	bytes = section_header.table_bytes;

	//if ( pParser->message_dumper == NULL ) //parse epg is not enabled
	//	return 0;

	p++;
	bytes--;
	ett.source_id = (p[0]<<8)|p[1];
	ett.event_id = (p[2]<<8)|p[3];
	if ( ( ett.event_id & 0x3 ) == 0x2 )
		ett.event_id >>= 2;

	pEit = FindEitCell( pATSCPSI, ett.source_id, ett.event_id );
	if ( pEit != NULL )
	{
		if ( pEit->event_id == ett.event_id && pEit->source_id == ett.source_id )
		{
			int update_flag = 0;
			DESC_DATA program={0};
			if ( !pATSCPSI->not_save_epg_message )
			{
				if ( pEit->program.desc_bytes != bytes-5 || 
					 memcmp( pEit->program.desc_ptr, p+4, _MIN( pEit->program.desc_bytes, bytes-5 )) )
				{
					update_flag = 1;
					FillDescData( &pEit->program, p+4, bytes-5 );	
				}
			} else
			{
				uint32_t crc32 = CalTSCRC32( p+4, bytes-5 );
				if ( pEit->program_crc32 != crc32 )	  
				{
					pEit->program_crc32	 = crc32;
					FillDescData( &program, p+4, bytes-5 );
					update_flag = 1;
				}
			}

			if ( pATSCPSI->psi_parser->dumper.epg_dumper != NULL && update_flag )
			{
				DESC_DATA desc={0};
				ATSCFormatEPG( pATSCPSI, pEit, &program, &desc );
				pATSCPSI->psi_parser->dumper.epg_dumper( pATSCPSI->psi_parser->dumper.epg_dumper_context, desc.desc_ptr, desc.desc_bytes );
				ReleaseDescData( &desc );
			}

			ReleaseDescData( &program );
			return 1;
		}
	}
	return 0;
}


int ProcessATSCPSI( ATSC_PSI* pATSCPSI, TS_PACKET *pTSPacket )
{
	uint16_t pid = pTSPacket->pid;
	const uint8_t* payload_data; int payload_size;

	payload_data = pTSPacket->data + pTSPacket->payload_offset;
	payload_size = pTSPacket->payload_bytes;

	if ( pid == 0x1ffb ) //ATSC packets
	{
		if ( !CheckPacketContinuity( pTSPacket, &pATSCPSI->atsc_psi_section->counter ) )
			return 0;

		if ( !UnpackSection( pTSPacket->start, pATSCPSI->atsc_psi_section, payload_data, payload_size ) )
			return 0;

		switch ( pATSCPSI->atsc_psi_section->section_type ) {
		case 0xc5:  //STT
			break;
		case 0xc7:  //MGT
			UnpackMGT( pATSCPSI, pATSCPSI->atsc_psi_section );
			break;

		case 0xc8:  //TVCT
			if ( pATSCPSI->disable_channel_inf )
				break;
			UnpackVCT( pATSCPSI, pATSCPSI->atsc_psi_section );
			pATSCPSI->psi_parser->sub_format = TERRESTRIAL;
			break;

		case 0xc9:  //CVCT
			if ( pATSCPSI->disable_channel_inf )
				break;
			UnpackVCT( pATSCPSI, pATSCPSI->atsc_psi_section );
			pATSCPSI->psi_parser->sub_format = CABLE;
			break;

		case 0xca: //RTT
			UnpackRTT( pATSCPSI, pATSCPSI->atsc_psi_section );
			break;

		case 0xcd: //RTT
			UnpackSTT( pATSCPSI, pATSCPSI->atsc_psi_section );
			break;

		}

		return 1;
	} else
	{
		int i;
		for ( i = 0; i<pATSCPSI->mgt_num; i++ )
		{
			if ( pATSCPSI->mgt[i].pid == pid )
			{
				if ( !CheckPacketContinuity( pTSPacket, &pATSCPSI->atsc_psi_section->counter ) )
					return 0;

				if ( !UnpackSection( pTSPacket->start, pATSCPSI->atsc_psi_section, payload_data, payload_size ) )
					return 0;

				if (  pATSCPSI->mgt[i].type == 0 ) 
				{
					if ( pATSCPSI->atsc_psi_section->section_type == 0xc8 )
						pATSCPSI->psi_parser->sub_format = TERRESTRIAL;
					else
					if ( pATSCPSI->atsc_psi_section->section_type == 0xc9 )
						pATSCPSI->psi_parser->sub_format = CABLE;

					if ( pATSCPSI->disable_channel_inf )
						break;
					UnpackVCT( pATSCPSI, pATSCPSI->atsc_psi_section );
				}
				else
				if (  pATSCPSI->mgt[i].type >= 0x100 &&  pATSCPSI->mgt[i].type <= 0x17f ) 
				{
					if ( pATSCPSI->psi_parser->dumper.epg_dumper == NULL ) 
						break;
					//EIT0 (0x100); EIT1 (0x101)
					return UnpackEIT( pATSCPSI, pATSCPSI->atsc_psi_section, pATSCPSI->mgt[i].type-0x100  );
				}
				else
				if (  pATSCPSI->mgt[i].type >= 0x200 &&  pATSCPSI->mgt[i].type <= 0x27f ) 
				{
					if ( pATSCPSI->psi_parser->dumper.epg_dumper == NULL ) 
						break;
					//ETT0 (0x100); ETT1 (0x101) //event ETT
					return UnpackETT( pATSCPSI, pATSCPSI->atsc_psi_section, pATSCPSI->mgt[i].type-0x200  );
				
				} else
				if (  pATSCPSI->mgt[i].type == 4 ) 
				{
					if ( pATSCPSI->psi_parser->dumper.epg_dumper == NULL ) 
						break;
					//channel ETT
					return UnpackETT( pATSCPSI, pATSCPSI->atsc_psi_section, MAX_EIT_NUM );
				} else
				if (  pATSCPSI->mgt[i].type >= 0x300 &&  pATSCPSI->mgt[i].type <= 0x37f ) 
				{
					UnpackRTT( pATSCPSI, pATSCPSI->atsc_psi_section );
				}
			}
		}
	}
	return 1;
}


static uint8_t* GetStringFromMutilString( uint8_t* p, int Bytes, int nCol, int nRow, int *pStringLen )
{
	int32_t i, j, num_strings, num_segments, offset, len;
	//uint32_t language_code;
	offset = 0;
	num_strings = p[offset];
	offset++;
	for ( i = 0; i<num_strings && offset+4 <= (int32_t)Bytes; i++ )
	{
		//language_code = (p[offset]<<16)|(p[offset+1]<<8)|p[offset+2];
		num_segments  = p[offset+3];
		offset += 4;
		for ( j = 0; j<num_segments && offset+3<=(int32_t)Bytes ; j++ )
		{
			//skip compression, mode 2 bytes;
			len = p[offset+2];
			offset += 3;
			if ( i == nCol && j == nRow )
			{
				//lLanguage = language_code;
				if ( offset+len <= (int32_t)Bytes ) 
				{
					*pStringLen = (int32_t)len;
					return p+offset;
				} else
				{
					*pStringLen = 0;
					return NULL;
				}
			}
			offset += len;
		}
	}
	*pStringLen = 0;
	return NULL;
}

int UnpackMultipleString( uint8_t* p, int Bytes, int nCol, int nRow, STRING* pString  )
{
	int32_t i, j, num_strings, num_segments, offset;
	uint32_t language_code;
	offset = 0;
	num_strings = p[offset];
	offset++;
	for ( i = 0; i<num_strings && offset+4 <= (int32_t)Bytes; i++ )
	{
		language_code = (p[offset+2]<<16)|(p[offset+1]<<8)|p[offset];
		num_segments  = p[offset+3];
		offset += 4;
		for ( j = 0; j<num_segments && offset+3<=(int32_t)Bytes ; j++ )
		{
			uint8_t compression, mode;
			int len;
			compression = p[offset+0];
			mode = p[offset+1];
			len  = p[offset+2];
			offset += 3;
			if ( offset+len > (int32_t)Bytes ) return 0;
			if ( i == nCol && j == nRow && len )
			{
				uint8_t* data_p;
				int size, num;
				pString->language_code = language_code;
				pString->charset_code =  (uint8_t*)"[set=UTF-8]";

				size = GetUncompressBytes( compression, p+offset,len );
				data_p = NewDescData( &pString->data, size+1 );
				num = UncompressHuffman( compression, data_p, size, p+offset,len );
				data_p[num] = 0x0;
				pString->data.desc_bytes = num;
				return num;
			}
			offset += len;
		}
	}
	return 0;

}

int UnpackMultipleString256( uint8_t* p, int Bytes, int nCol, int nRow, STRING256* pString  )
{
	int32_t i, j, num_strings, num_segments, offset;
	uint32_t language_code;
	offset = 0;
	num_strings = p[offset];
	offset++;
	for ( i = 0; i<num_strings && offset+4 <= (int32_t)Bytes; i++ )
	{
		language_code = (p[offset+2]<<16)|(p[offset+1]<<8)|p[offset];
		num_segments  = p[offset+3];
		offset += 4;
		for ( j = 0; j<num_segments && offset+3<=(int32_t)Bytes ; j++ )
		{
			uint8_t compression, mode;
			int len;
			compression = p[offset+0];
			mode = p[offset+1];
			len  = p[offset+2];
			offset += 3;
			if ( offset+len > (int32_t)Bytes ) return 0;
			if ( i == nCol && j == nRow )
			{
				int num;
				pString->language_code = language_code;
				pString->charset_code = (uint8_t*)"[set=UTF-8]";

				num = UncompressHuffman( compression, pString->data, sizeof(pString->data), p+offset,len );
				if ( sizeof(pString->data) > num ) 
					pString->data[num] = 0x0;
				pString->bytes = num;
				return num;
			}
			offset += len;
		}
	}
	return 0;

}

extern uint8_t program_huffman_tbl[];
extern uint8_t title_huffman_tbl[];
static uint8_t GetNext8Bits( uint8_t* pData, int pos, uint8_t bit )
{
	int i;
	uint8_t ch;
	ch = 0;
	for ( i = 0; i<8; i++ )
	{
		bit >>= 1;
		ch <<= 1;
		if ( bit == 0 ) 	
		{    pos++; 
		     bit = 0x80; 
		} 
		if ( bit & pData[pos] ) ch |= 1;
	}
	return ch;
}
static int UncompressHuffman( int Type, uint8_t* pOut, int MaxSize, uint8_t* pData, int Length )
{
	int i, bytes;
	uint8_t *huffman, *tree_root;
	uint8_t lch, cch, ch;
	uint8_t bit;
	int offset, index;

	if ( Length <= 0 || pData == NULL  || MaxSize <= 1 )
		return 0;

	if ( Type == 1  ) 
		huffman = title_huffman_tbl;
	else
	if ( Type == 2  ) 
		huffman = program_huffman_tbl;
	else
	{
		//unknow format, just output
		bytes = _MIN( Length, MaxSize );
		memcpy( pOut, pData, bytes );
		return bytes;
	}

	bytes = 0;	i = 0;
	index = 0;
	lch = 0; bit = 0; cch = 0;
	index = (huffman[lch*2]<<8)|huffman[lch*2+1];
	tree_root = &huffman[index];
	offset = 0;
	while ( bytes < MaxSize  )
	{
		uint8_t node;

		if ( i >= Length && bit == 0 )
			break;

		if ( bit == 0 ) 
		{
			cch = pData[i++];
			bit = 0x80;
		}

		if ( bit & cch )
			node = tree_root[offset+1];  //right child branch
		else
			node = tree_root[offset];    //left child branch

		if ( node & 0x80 )
		{
			ch = node & 0x7f;
			if ( ch == 27 )
			{
				lch = GetNext8Bits( pData, i-1, bit );  //get uncompressed 8 bits to output
				cch = pData[i++];  //skip 8 bits
			} else
			{
				lch = ch; 
			}
			*pOut++ = lch;
			bytes++;
			
			if ( bytes >= MaxSize )
			{
				//printf( "uncompress buffer is too small\n" );
			}

			if ( lch == 0 )
				break;

			index = (huffman[lch*2]<<8)|huffman[lch*2+1];
			tree_root = &huffman[index];

			if ( (Type == 2 && index > 1781) || (Type == 1 && index > 1939) ) 
			{
				//printf( "Error uncompress\n" );
				return bytes;
			}
			offset = 0;

		}
		else
		{
			offset = node*2 ;
		}

		bit >>= 1;
		
	}

	return bytes;
}
	
static int GetUncompressBytes( int Type, uint8_t* pData, int Length )
{
	int i, bytes;
	uint8_t *huffman, *tree_root;
	uint8_t lch, cch, ch;
	uint8_t bit;
	int offset, index;

	if ( Type == 1  ) 
		huffman = title_huffman_tbl;
	else
	if ( Type == 2  ) 
		huffman = program_huffman_tbl;
	else
	{
		//unknow format, just output
		return Length;
	}

	bytes = 0;	i = 0;
	index = 0;
	lch = 0; bit = 0; cch = 0;
	index = (huffman[lch*2]<<8)|huffman[lch*2+1];
	tree_root = &huffman[index];
	offset = 0;
	while ( 1  )
	{
		uint8_t node;
		if ( i >= Length && bit == 0 )
			break;

		if ( bit == 0 ) 
		{
			cch = pData[i++];
			bit = 0x80;
		}

		if ( bit & cch )
			node = tree_root[offset+1];  //right child branch
		else
			node = tree_root[offset];    //left child branch

		if ( node & 0x80 )
		{
			ch = node & 0x7f;
			if ( ch == 27 )
			{
				lch = GetNext8Bits( pData, i-1, bit );  //get uncompressed 8 bits to output
				cch = pData[i++];  //skip 8 bits
			} else
			{
				lch = ch; 
			}
			
			bytes++;
			if ( lch == 0 )
				break;

			index = (huffman[lch*2]<<8)|huffman[lch*2+1];
			tree_root = &huffman[index];

			if ( (Type == 2 && index > 1781) || (Type == 1 && index > 1939) ) 
			{
				//printf( "Error uncompress\n" );
				return bytes;
			}
			offset = 0;

		}
		else
		{
			offset = node*2 ;
		}

		bit >>= 1;
		
	}

	return bytes;
}

