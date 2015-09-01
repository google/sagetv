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

#include <stdlib.h>
#include <memory.h>
#include "NativeCore.h"
#include "SectionData.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"

///////////////////////////////////////////////////////////////////////////////////////////
TS_FILTER* CreateTSFilter( int nPatNum, int nPmtNum, int nStreamFormat, int nSubFormat  )
{
	int i;
	int map_num, total_size;
	TS_FILTER* pTSFilter;
#if defined(MINI_PVR)
	SageLog(( _LOG_TRACE, 3, TEXT("TS Filter created ( ver.2.0.9 ) Mini PVR") ));
#else
	SageLog(( _LOG_TRACE, 3, TEXT("TS Filter created  ( ver.2.0.9 )") ));
#endif 
	//memory profile
	map_num = 256 ;//nPatNum * nPmtNum;
	total_size = sizeof(TS_PAT)*nPatNum + sizeof(TS_PMT)*nPmtNum + sizeof(TS_PMT_MAP)*map_num +sizeof(TS_FILTER); 
	SageLog(( _LOG_TRACE, 4, TEXT("Memory footprint of TS Parser pat:%d, pmt:%d,  map:%d, total:%d"), 
	          sizeof(TS_PAT)*nPatNum, sizeof(TS_PMT)*nPmtNum, sizeof(TS_PMT_MAP)*map_num, total_size ));

	assert( MAX_PMT_NUM >= nPmtNum );
	if ( MAX_PMT_NUM < nPmtNum )
	{
		SageLog(( _LOG_ERROR, 3, TEXT("ERROR: PMT number %d is too big, maxium limit is :%d\n"), nPmtNum, MAX_PMT_NUM ));
	}

	pTSFilter = SAGETV_MALLOC( sizeof(TS_FILTER) );
	memcpy( pTSFilter->_tag_, TSFILTER_TAG, sizeof(pTSFilter->_tag_) );
	SET_SAGE_TAG( pTSFilter, TSFILTER_TAG );

	pTSFilter->pat_num = nPatNum;
	pTSFilter->pmt_num = nPmtNum;
	pTSFilter->map_num = map_num;

	pTSFilter->pat = SAGETV_MALLOC( sizeof(TS_PAT)*pTSFilter->pat_num );
	pTSFilter->pmt = SAGETV_MALLOC( sizeof(TS_PMT)*pTSFilter->pmt_num );
	pTSFilter->pmt_section   = SAGETV_MALLOC( sizeof(TS_SECTION*)*pTSFilter->pmt_num );
	pTSFilter->pmt_map = SAGETV_MALLOC( sizeof(TS_PMT_MAP )*pTSFilter->map_num );

	pTSFilter->pat_section = CreateSection();
	for ( i = 0; i<pTSFilter->pmt_num; i++ )
		pTSFilter->pmt_section[i] = CreateSection();

	pTSFilter->ts_streams_num = 0;

	ASSERT( pTSFilter->pat );
	ASSERT( pTSFilter->pmt );
	ASSERT( pTSFilter->pmt_section );
	ASSERT( pTSFilter->pmt_map );

	pTSFilter->psi_parser = CreatePSIParser( pTSFilter, nStreamFormat, nSubFormat );

	for ( i = 0; i<pTSFilter->pat_num; i++ )
	{
		pTSFilter->pat[i].tsid = 0xffff;
	}

	for ( i = 0; i<pTSFilter->map_num; i++ )
	{
		pTSFilter->pmt_map[i].pat_index = 0xffff;
		pTSFilter->pmt_map[i].pid = 0;
		pTSFilter->pmt_map[i].channel = 0xff;
		pTSFilter->pmt_map[i].program = 0;
	}
	pTSFilter->mapped_num = 0;

	return pTSFilter;
}

void ReleaseTSFilter( TS_FILTER* pTSFilter )
{
	int i, j;
	ReleaseSection( pTSFilter->pat_section );

	for ( i = 0; i<pTSFilter->pmt_num; i++ )
	{
		ReleaseSection( pTSFilter->pmt_section[i] );
		ReleaseDescData( &pTSFilter->pmt[i].program_desc );
		for ( j = 0; j<pTSFilter->pmt[i].total_stream_number; j++ )
		{
			ReleaseDescData( &pTSFilter->pmt[i].stream_desc[j] );
		}
	}
	ReleasePSIParser( pTSFilter->psi_parser );
	//SAGETV_FREE( pTSFilter->ts_streams.ts_element );
	SAGETV_FREE( pTSFilter->pat );
	SAGETV_FREE( pTSFilter->pmt );
	SAGETV_FREE( pTSFilter->pmt_section );
	SAGETV_FREE( pTSFilter->pmt_map );
	SAGETV_FREE( pTSFilter );
}

void ResetTSFilter( TS_FILTER* pTSFilter )
{
	int i,j;
	CHECK_SAGE_TAG( pTSFilter, TSFILTER_TAG ); //check PTSfilter is created by CreateTSFilter()
	
	pTSFilter->disable_ts_table_parse = 0;
	pTSFilter->disable_stream_filter = 0;
	pTSFilter->disable_pid_hist = 0;
	pTSFilter->pid_hist_ctrl = 0;

	//pTSFilter->disable_psi_parse = 0;
	pTSFilter->ts_packet_counter = 0;
	memset( pTSFilter->pat, 0x0, sizeof(TS_PAT)*pTSFilter->pat_num  );
	ResetSection( pTSFilter->pat_section );


	for ( i = 0; i<pTSFilter->pmt_num; i++ )
	{
		ResetSection( pTSFilter->pmt_section[i] );
		ReleaseDescData( &pTSFilter->pmt[i].program_desc );
		for ( j = 0; j<pTSFilter->pmt[i].total_stream_number; j++ )
		{
			ReleaseDescData( &pTSFilter->pmt[i].stream_desc[j] );
		}
	}

	memset( pTSFilter->pmt, 0, sizeof(TS_PMT)*pTSFilter->pmt_num );

	memset( pTSFilter->pmt_map, 0,  sizeof(TS_PMT_MAP )*pTSFilter->map_num );

	for ( i = 0; i<pTSFilter->pat_num; i++ )
	{
		pTSFilter->pat[i].tsid = 0xffff;
	}

	for ( i = 0; i<pTSFilter->map_num; i++ )
	{
		pTSFilter->pmt_map[i].pat_index = 0xffff;
		pTSFilter->pmt_map[i].pid = 0;
		pTSFilter->pmt_map[i].channel = 0xff;
		pTSFilter->pmt_map[i].program = 0;
	}
	pTSFilter->mapped_num = 0;

	pTSFilter->ts_streams_num = 0;
}


static ULONGLONG GetPCR( const uint8_t* pkt )
{
	ULONGLONG PCR;
	ULONGLONG PCR_base;
	uint16_t PCR_ext;
	PCR_base =  ((ULONGLONG)( pkt[0]) << 25);
	PCR_base |= ((ULONGLONG)( pkt[1]) << 17);
	PCR_base |= ((ULONGLONG)( pkt[2]) << 9 );
	PCR_base |= ((ULONGLONG)( pkt[3]) << 1 );
	PCR_base |= ((ULONGLONG)(0x80 & pkt[4]) >> 7);     //33 bits
	PCR_ext = ((uint16_t)(pkt[4] & 0x01) << 8);   
	PCR_ext |= ((uint16_t)(pkt[5]));			   //9 bits
	PCR = (ULONGLONG)300* PCR_base + PCR_ext;

	return PCR;
}


static int UnpackTSPacket( TS_PACKET *pTSPacket, const uint8_t* pData )	
{
	pTSPacket->data   = (uint8_t*)pData;
	pTSPacket->sync	  =	pData[0];			
	pTSPacket->error  =	pData[1]&0x80	? 1 : 0;	
	if ( pTSPacket->error ) return 0;
	pTSPacket->start  =	pData[1]&0x40	? 1 : 0;	
	pTSPacket->priority =	pData[1]&0x20 ? 1 : 0;	
	pTSPacket->pid	  =	pData[2] | (pData[1]&0x1f)<<8 ;	
	pTSPacket->scrambling_ctr = ( pData[3] & 0xc0 )>> 6;	
	pTSPacket->adaption_ctr   = ( pData[3] & 0x30 )>> 4;	
	pTSPacket->continuity_ct  = ( pData[3] & 0x0f );	
	pTSPacket->pcr = pTSPacket->opcr  = 0;
	pTSPacket->pcr_flag = 0;
	

	// adaption_ctr: 00:no payload; 01: payload; 10: adaption; 11:adaption+payload  ZQ
	if ( ( pTSPacket->adaption_ctr & 0x01 ) == 0  )	//there	is no payload in packet	
	{
		pTSPacket->payload_bytes = 0;
	    pTSPacket->payload_offset = TS_PACKET_LENGTH;
	}
	  
	if ( pTSPacket->adaption_ctr == 1 )
	{
		pTSPacket->payload_offset = 4;
		pTSPacket->payload_bytes  = TS_PACKET_LENGTH - pTSPacket->payload_offset;
		return 1;
	}

	if ( (pTSPacket->adaption_ctr & 0x02 ) )
	{
		pTSPacket->adaption.apapt_len			=	pData[4];	
		pTSPacket->adaption.discontinute		=	pData[5] & 0x80 ?	1 : 0;
		pTSPacket->adaption.random_acceess		=	pData[5] & 0x40 ?	1 : 0;
		pTSPacket->adaption.elem_stream_prio	=	pData[5] & 0x20 ?	1 : 0;
		pTSPacket->adaption.flags				=	pData[5] & 0x1f;

		if ( ( pTSPacket->adaption.flags & 0x10 ) && pTSPacket->adaption.apapt_len > 6 )
		{
		  pTSPacket->pcr = GetPCR( &pData[6] );
		  pTSPacket->pcr_flag = 1;
		} 

		if ( ( pTSPacket->adaption.flags & 0x08 ) && pTSPacket->adaption.apapt_len > 6 )
		{
		  if (  pTSPacket->adaption.flags & 0x10  )
			pTSPacket->opcr = GetPCR( &pData[6+6] );
		  else
			pTSPacket->opcr = GetPCR( &pData[6] );
		}

		if ( pTSPacket->adaption_ctr == 3 )
		{
		  pTSPacket->payload_offset = 4 + pTSPacket->adaption.apapt_len + 1;	
		  pTSPacket->payload_bytes  = TS_PACKET_LENGTH - pTSPacket->payload_offset;
		  if ( 4 + pTSPacket->adaption.apapt_len > TS_PACKET_LENGTH	)
			return 0;
		}
	}

	return 1;
}

int CheckPacketContinuity( TS_PACKET *pTSPacket, uint8_t* pContinuity )
{
	if ( pTSPacket->start )
	{
		*pContinuity = pTSPacket->continuity_ct;
		return 1;
	}

	(*pContinuity)++;
	(*pContinuity) &= 0x0f;
	return ( pTSPacket->continuity_ct == *pContinuity );

}

static inline int LookupPMT( TS_FILTER* pTSFilter, uint16_t uPid )
{
	int i, j; 
	for ( i = 0; i<pTSFilter->pmt_num; i++ )
	{
		for ( j =0; j< pTSFilter->pmt[i].total_stream_number; j++ )
		{
			if ( pTSFilter->pmt[i].stream_pid[j] == uPid )
				return i;
		}
	}
	return -1;
}

static void SortPidHistTable( TS_FILTER* pTSFilter )
{
	int i, j;
	//sort histgram table
	for ( i = 0; i<pTSFilter->pid_hist_num; i++ )
	{
		for ( j = i+1; j<pTSFilter->pid_hist_num; j++ )
		{
			if ( pTSFilter->pid_hist[i].count < pTSFilter->pid_hist[j].count )
			{
				PID_HIST n;
				n = pTSFilter->pid_hist[i];
				pTSFilter->pid_hist[i] = pTSFilter->pid_hist[j];
				pTSFilter->pid_hist[j] = n;
			}
		}
	}
}			

//catch most active 10 pids in histogram table, when the most active pid counter hits threshold number disable histgraming
static void HistogramPid( TS_FILTER* pTSFilter, TS_PACKET *pTSPacket )
{
	int i;
	uint16_t max_num, min_num, min_index = 0;
	if ( pTSFilter->disable_pid_hist == 1 || pTSPacket->pid == 0 )
		return;

	if ( pTSFilter->ts_streams_num == 0  ) //filter isn't setup yet, ccount data packet for PMT
	{
		int pmt_index = LookupPMT( pTSFilter, pTSPacket->pid );
		if ( pmt_index >= 0 )
		{
			pTSFilter->pmt[pmt_index].active_count++;
		}
	}

	for ( i = 0; i<pTSFilter->pid_hist_num; i++ )
	{
		if ( pTSFilter->pid_hist[i].pid == pTSPacket->pid )
		{
			pTSFilter->pid_hist[i].count++;
			if ( pTSPacket->pcr_flag )		 
				pTSFilter->pid_hist[i].flag |= PCR_FLAG;
			if ( pTSPacket->scrambling_ctr ) 
				pTSFilter->pid_hist[i].flag |= SCRAMB_FLAG;

			//stop histgramming
			if ( pTSFilter->pid_hist[i].count > PID_HIST_THRESHOLD + 100 )
				pTSFilter->disable_pid_hist = 1;

			return;
		}
	}

	if ( pTSFilter->pid_hist_ctrl == 1 )
		return;

	if ( pTSFilter->pid_hist_num < MAX_PID_HIST_NUM )
	{
		pTSFilter->pid_hist[pTSFilter->pid_hist_num].pid = pTSPacket->pid;
		pTSFilter->pid_hist[pTSFilter->pid_hist_num].count = 1;
		pTSFilter->pid_hist_num++;
		return;
	} else
	{
		max_num = 0;
		min_num = 0xffff;
		for ( i = 0; i<pTSFilter->pid_hist_num; i++ )
		{
			if ( pTSFilter->pid_hist[i].count > max_num )
			{
				max_num = pTSFilter->pid_hist[i].count;
			} else
			if ( pTSFilter->pid_hist[i].count < min_num )
			{
				min_num = pTSFilter->pid_hist[i].count;
				min_index = i;
			} 
		}

		//replace at least one
		pTSFilter->pid_hist[min_index].pid = pTSPacket->pid;
		pTSFilter->pid_hist[min_index].count = 1;
		if ( max_num > PID_HIST_THRESHOLD )
		{
			SortPidHistTable( pTSFilter );
			//stop histgramming
			pTSFilter->disable_pid_hist = 1;
		}
	}
}


static int UnpackPAT( TS_FILTER* pTSFilter, TS_PACKET *pTSPacket )
{
	const uint8_t* payload_data; int payload_size;
	uint8_t* data;
	uint16_t tsid;
	int	packet_offset, i;
	SECTION_HEADER section_header;
	TS_PAT *pPat;

	payload_data = pTSPacket->data + pTSPacket->payload_offset;
	payload_size = pTSPacket->payload_bytes;

	if ( !CheckPacketContinuity( pTSPacket, &pTSFilter->pat_section->counter ) )
		return -1;

	if ( !UnpackSection( pTSPacket->start, pTSFilter->pat_section, payload_data, payload_size ) )
		return -1;

	UnpackSectionDataHeader( &section_header, pTSFilter->pat_section );
	if ( section_header.table_id != 0 )  //it's invalid PAT
		return -1;

	//get tsid first to find a pat in a pat list
	tsid = section_header.tsid;
	for ( i = 0; i<pTSFilter->pat_num; i++ )
	{
		if ( pTSFilter->pat[i].tsid == tsid )
			break;
	}
	if ( i >= pTSFilter->pat_num )
	{
		for ( i = 0; i<pTSFilter->pat_num ; i++ )
			if ( pTSFilter->pat[i].tsid == 0xffff )
				break;
		if ( i >= pTSFilter->pat_num  )
		{
			for ( i = 0; i<pTSFilter->pat_num ; i++ )
				if ( pTSFilter->pat[i].locked == 0 )
					break;
			if ( i >= pTSFilter->pat_num  )
				SageLog(( _LOG_ERROR, 3, TEXT("ERROR: too many PATs in a stream (max:%d, cur tsid:%d). "), pTSFilter->pat_num, tsid  ));
		}
	}

	pPat = &pTSFilter->pat[i];
	pPat->stamp = pTSFilter->ts_packet_counter;

	pPat->update_flag = pTSFilter->pat_section->crc32 != pPat->section_crc32;
	pPat->section_crc32 = pTSFilter->pat_section->crc32;
	if ( pPat->update_flag == 0 ) 
	{
		return i; //pat unchange, skip it
	}

	pPat->tsid = section_header.tsid;
	data = section_header.table_data;

	packet_offset = 0;
	pPat->total_program_number	= 0;
	while (	pPat->total_program_number < MAX_PMT_NUM && packet_offset+4 <= (int)section_header.table_bytes )	
	{
		pPat->program_number[ pPat->total_program_number ] = 
					(data[packet_offset] << 8)|data[packet_offset +	1];	
		pPat->program_pid[ pPat->total_program_number ]	= 
					((data[packet_offset + 2] & 0x1f) << 8)|data[packet_offset + 3];
		pPat->total_program_number++;
		packet_offset += 4;
	}
	return i;
}

static void ParseCADesc( TS_PMT *pPmt, DESC_DATA *pDesc );
static int GetPmtIndex2( TS_FILTER* pTSFilter, uint16_t uPid, uint16_t uProgram );
int UnpackPMT( TS_FILTER *pTSFilter, int nPmtIndex, TS_PACKET *pTSPacket )	
{
	const uint8_t* payload_data; 
	int payload_size;
	uint8_t* data;
	uint16_t packet_offset, data_length;
	int stream_num, section_bytes, section_start, update_pmt = 0;
	SECTION_HEADER section_header;
	TS_PMT *pPmt;

	payload_data = pTSPacket->data + pTSPacket->payload_offset;
	payload_size = pTSPacket->payload_bytes;

	if ( pTSFilter->pmt_num <= nPmtIndex )
		return 0;

	pPmt = &pTSFilter->pmt[nPmtIndex]; 

	if ( !CheckPacketContinuity( pTSPacket, &pTSFilter->pmt_section[nPmtIndex]->counter ) )
		return 0;

	section_start = pTSPacket->start;
	while ( payload_size > 0 )
	{
		//check if it's a vaild PMT table
		if ( section_start )
		{
			const uint8_t* section_p = (section_start==1)? payload_data+payload_data[0]+1 : payload_data ;
			if ( section_p[0] != 0x02 || (section_p[1]& 0x80) != 0x80 ) //PMT table id is 2; a valid indicator 
				break;
		}

		//a packet contains multiple sections, (ResearchCH.ts DVB-S of USA)
		section_bytes = UnpackSection( section_start, pTSFilter->pmt_section[nPmtIndex], payload_data, payload_size );
		if ( section_bytes == 0 ) //section isn't completed.
			return update_pmt;

		update_pmt++;
		payload_data += section_bytes;
		payload_size -= section_bytes;
		section_start = 2;
		
		pPmt = &pTSFilter->pmt[nPmtIndex]; 
		pPmt->update_flag = pTSFilter->pmt_section[nPmtIndex]->crc32 != pPmt->section_crc32;
		pPmt->section_crc32 = pTSFilter->pmt_section[nPmtIndex]->crc32;
		if ( pPmt->update_flag == 0 && payload_size == 0 ) 
			return update_pmt; //skip unpacking pmt to save time as it's unchange table

		UnpackSectionDataHeader( &section_header, pTSFilter->pmt_section[nPmtIndex] );

		if ( pTSFilter->pmt_map[nPmtIndex].group_flag )
		{
			int uNewIndex;
			TS_SECTION *pSection;
			uint32_t crc32_tmp;
			uNewIndex = GetPmtIndex2( pTSFilter, pTSPacket->pid, section_header.tsid );
			if ( uNewIndex == -1 ||  pTSFilter->pmt_num <= uNewIndex )
			{
				SageLog(( _LOG_ERROR, 3, TEXT("WARNING:not found PMT in PMT-MAP (pid:0x%x program:%d)."), 
											pTSPacket->pid, section_header.tsid  ));
				continue;
			}
			pPmt = &pTSFilter->pmt[uNewIndex]; 
			pPmt->update_flag = pTSFilter->pmt_section[nPmtIndex]->crc32 != pPmt->section_crc32;;
			pPmt->section_crc32 = pTSFilter->pmt_section[nPmtIndex]->crc32;
			if ( pPmt->update_flag == 0 )		 //skip unpacking pmt to save time as it's unchange table
				continue;

			//swap section 
			crc32_tmp = pTSFilter->pmt[uNewIndex].section_crc32;
			pTSFilter->pmt[uNewIndex].section_crc32 = pTSFilter->pmt[nPmtIndex].section_crc32;
			pTSFilter->pmt[nPmtIndex].section_crc32 = crc32_tmp;
			pSection = pTSFilter->pmt_section[uNewIndex];
			pTSFilter->pmt_section[uNewIndex] = pTSFilter->pmt_section[nPmtIndex];
			pTSFilter->pmt_section[nPmtIndex] = pSection;
		}

		pPmt->program_number = section_header.tsid;
		data = section_header.table_data;

		pPmt->pcr_pid	     = ((data[0] &	0x1f) << 8)	| data[1];
		data_length          = ((data[2] &	0x03) << 8)	| data[3];

		if ( data_length > section_header.table_bytes )
		{
			SageLog(( _LOG_ERROR, 3, TEXT("WARNING:drop a bad PMT (pid:0x%x program:%d) data len:%d; data in section :%d."), 
				 pTSPacket->pid, pPmt->program_number, data_length,section_header.table_bytes ));
			return 0;
		}

		if ( data_length )
		{
			FillDescData( &pPmt->program_desc, data + 4, data_length );
			ParseCADesc( pPmt, &pPmt->program_desc );
		}

		packet_offset = 4 + data_length;
		stream_num = 0;
		while ( packet_offset + 6 <= section_header.table_bytes )
		{
			uint16_t pid;
			uint8_t  type;
			type =  data[packet_offset];	
			pid = ((data[packet_offset+1] & 0x1F) << 8) | data[packet_offset+2];

	#ifdef PACK_PMT
			if ( (  type == 0x05 ||	type == 0x0b /*private stream or DSM-CC stream*/ )
			//if ( (  type == 0x05 ||	type == 0x90 /*BlueRay PGS subtitle*/ || type == 0x0b /*private stream or DSM-CC stream*/ )
			//if ( (  type == 0x05 ||	type == 0x0b /*private stream or DSM-CC stream*/ )
				&& pPmt->pcr_pid != pid ) 
			{
				data_length = ((data[packet_offset + 3] & 0x03) << 8) | data[packet_offset + 4];
				packet_offset += 5 + data_length;
				continue;
			}
	#endif
			if ( !( pPmt->update_flag & PMT_UPDATED ) &&
				  ( pPmt->stream_type[stream_num] != type || 
					pPmt->stream_pid[stream_num]  != pid  ) )
			{
				pPmt->update_flag |= PMT_UPDATED;
			}

			pPmt->stream_type[stream_num] = type;	 
			pPmt->stream_pid[stream_num]  = pid;
			data_length = ((data[packet_offset + 3] & 0x03) << 8) | data[packet_offset + 4];
			if ( data_length )
				FillDescData( &pPmt->stream_desc[stream_num], data + packet_offset+5, data_length );

			stream_num++;
			packet_offset += 5 + data_length;
			if ( stream_num >= MAX_ES )
			{
				SageLog(( _LOG_ERROR, 3, TEXT("ERROR: The number of stream (%d) in PMT is out of limit (%d), there are streams are dropped . file:\"%s\" line:%d. "), 
								  stream_num, MAX_ES, __FILE__, __LINE__ ));
				break;
			
			}
		}

		//clean up extral stream data
		if ( pPmt->total_stream_number > stream_num )
		{
			int i;
			for ( i = stream_num; i<pPmt->total_stream_number; i++ )
			{
				pPmt->stream_pid[i] = 0;
				pPmt->stream_type[i] = 0;
				ReleaseDescData( &pPmt->stream_desc[i] );
			}
		}

		pPmt->total_stream_number = stream_num;

	}
	return update_pmt;

}

//return 0: PAT table unchange; 1:PAT is a new PAT.

static inline int AddPMRMapEntry( TS_FILTER* pTSFilter, int nPatIndex, uint16_t nPid, uint16_t nProgram )
{
	int i;
	for ( i = 0; i<pTSFilter->map_num; i++ )
	{
		if ( pTSFilter->pmt_map[i].pid == 0 )
		{
			pTSFilter->pmt_map[i].pat_index = nPatIndex;
			pTSFilter->pmt_map[i].pid = nPid;
			pTSFilter->pmt_map[i].program = nProgram;
			pTSFilter->pmt_map[i].channel = i;
			pTSFilter->mapped_num++;
			return i;
		}
	}
	return -1;
}
//return 0: PAT table unchange; 1:PAT is a new PAT.
int UpdatePMTMap( TS_FILTER* pTSFilter, int nPatIndex )
{
	int update_flag = 0;
	uint16_t pat_index[MAX_PMT_NUM]={0};
	int i, j, num;
	TS_PAT *pPat;

	num = 0;
	//select current pat mapping 
	for ( i = 0; i<pTSFilter->mapped_num; i++ )
	{
		if ( pTSFilter->pmt_map[i].pat_index == nPatIndex && pTSFilter->pmt_map[i].program )
			pat_index[num++] = i;
	}

	//check if pat isn't changed
	pPat = &pTSFilter->pat[nPatIndex];
	for ( i = 0; i< pPat->total_program_number; i++ )
	{
		if ( pPat->program_number[i] == 0 ) //skip program = 0, NIT
			continue;
		for ( j = 0; j<num; j++ )
		{
			if ( pTSFilter->pmt_map[pat_index[j]].pid == pPat->program_pid[i] )
				break;
		}
		if ( j >= num ) //if pid not found, it's a new pat
		{
			update_flag = 1;
			break;
		}
	}

	//we use this stupid way to verify because I saw DVB-S has two programs share a pid in a PAT
	if ( update_flag == 0 ) //check all pids of map are in pat
	{
		for ( j = 0; j<num; j++ )
		{
			for ( i = 0; i< pPat->total_program_number; i++ )
				if ( pTSFilter->pmt_map[pat_index[j]].pid == pPat->program_pid[i] )
					break;

			if ( i >= pPat->total_program_number ) //if pid not found, it's a new pat
			{
				update_flag = 1;
				break;
			}
		}
	}

	//if no update, do nothing.
	if ( !update_flag )
		return update_flag;

	//clearn up old ones
	for ( j = 0; j<num; j++ )
	{
		pTSFilter->pmt_map[pat_index[j]].pid = 0;
		pTSFilter->pmt_map[pat_index[j]].pat_index = 0;
		pTSFilter->pmt_map[pat_index[j]].program = 0;
		pTSFilter->mapped_num--;
	}

	//add new ones into the map
	for ( j = 0; j < pPat->total_program_number; j++ )
	{
		//if (!( pPat->program_number[j] == 0 && pPat->program_pid[i] == 0x10 ) ) //NIT
		if ( pPat->program_number[j] != 0 ) //Drop a PMT of program=0 !!! 
		{
			if( AddPMRMapEntry( pTSFilter, nPatIndex, pPat->program_pid[j], pPat->program_number[j] ) < 0 )
				SageLog(( _LOG_ERROR, 3, TEXT("ERROR:PMT-MAP overflow, drop a map entry pid:0x%x program:%d."), 
		                    pPat->program_pid[j], pPat->program_number[j], nPatIndex ));
		}
	}

	//grouping the programs sharing the same pid
	for ( i = 0; i<pTSFilter->mapped_num; i++ )
	{
		if ( pTSFilter->pmt_map[i].pid && !pTSFilter->pmt_map[i].group_flag )
		{
			for ( j = i+1; j<pTSFilter->mapped_num; j++ )
			{
				if ( pTSFilter->pmt_map[i].pid == pTSFilter->pmt_map[j].pid &&
					 pTSFilter->pmt_map[i].program != pTSFilter->pmt_map[j].program  ) 
				{
					pTSFilter->pmt_map[i].group_flag = 1;
					pTSFilter->pmt_map[j].group_flag = 1;
				}
			}

			// add a virtual channel on top to save a section data
			if ( pTSFilter->pmt_map[i].group_flag )
			{
				int k, pid;
				pid = pTSFilter->pmt_map[i].pid;
				for ( k = 0; k < pTSFilter->map_num; k++ )
					if ( pTSFilter->pmt_map[k].pid == 0 ) break;
				if ( k <  pTSFilter->map_num )
				{
					for ( ; k > 0; k-- )
						pTSFilter->pmt_map[k] = pTSFilter->pmt_map[k-1];
					pTSFilter->pmt_map[0].pid = 0;
					AddPMRMapEntry( pTSFilter, nPatIndex, pid, 0 );

					pTSFilter->pmt_map[0].group_flag = 1;
					SageLog(( _LOG_TRACE, 3, TEXT("Found shared pid for a PMT pid:0x%x."), pid ));
				} else
					SageLog(( _LOG_ERROR, 3, TEXT("ERROR:PMT-MAP overflow, drop a map entry pid:0x%x program:%d."), 
					           pid, 0, 0 ));
			}
		}
	}
	

	//sorting pmt map by program number to put pmt in order
	//select new add ones 
	num = 0; 
	for ( i = 0; i<pTSFilter->mapped_num; i++ )
	{
		if ( pTSFilter->pmt_map[i].pat_index == nPatIndex )
			pat_index[num++] = i;
	}

	//sorting by program nnumber
	for ( j = 0; j<num; j++ )
	{
		for ( i = j; i<num; i++ )
		{
			if ( pTSFilter->pmt_map[ i ].program && pTSFilter->pmt_map[ j ].program > pTSFilter->pmt_map[ i ].program )
			{
				//swap data
				uint16_t prog, pid;
				prog = pTSFilter->pmt_map[ j ].program;
				pid  = pTSFilter->pmt_map[ j ].pid;
				pTSFilter->pmt_map[ j ].program = pTSFilter->pmt_map[ i ].program;
				pTSFilter->pmt_map[ j ].pid     = pTSFilter->pmt_map[ i ].pid;
				pTSFilter->pmt_map[ i ].program = prog;
				pTSFilter->pmt_map[ i ].pid     = pid;

			}
		}
	}

	SageLog(( _LOG_ERROR, 3, TEXT("PMT-MAP Table is updated" ) ));
	return update_flag;
}


static int GetPatIndex( TS_FILTER* pTSFilter, uint16_t uPid, uint16_t uProgram )
{
	int i, pat_index = -1;
	uint32_t stamp=0;
	for ( i = 0; i<pTSFilter->mapped_num && pTSFilter->pmt_map[i].pid ; i++ )
	{
		if ( pTSFilter->pmt_map[i].pid == uPid )
		{
			if ( pTSFilter->pmt_map[i].group_flag == 0 )
			{
				if ( pTSFilter->pmt_map[i].program == uProgram )
				{	//find the latest pat, if a pat is updated
					int k = pTSFilter->pmt_map[i].pat_index;
					if ( stamp < pTSFilter->pat[k].stamp )
					{
						pat_index = k;
						stamp = pTSFilter->pat[k].stamp;
					}
				}
			}
			else
			{
				if ( pTSFilter->pmt_map[i].program == uProgram )
					return pTSFilter->pmt_map[i].pat_index;
			}
		}

	}
	return pat_index;
}

static int GetPmtIndex( TS_FILTER* pTSFilter, uint16_t uPid )
{
	int i;
	for ( i = 0; i<pTSFilter->mapped_num ; i++ )
	{
		if ( pTSFilter->pmt_map[i].pid == uPid )
			return i;
	}
	return -1;
}

static int GetPmtIndex2( TS_FILTER* pTSFilter, uint16_t uPid, uint16_t uProgram )
{
	int i;
	for ( i = 0; i<pTSFilter->mapped_num ; i++ )
	{
		if ( pTSFilter->pmt_map[i].pid == uPid && pTSFilter->pmt_map[i].program == uProgram )
			return i;
	}
	return -1;
}

static void DumpPMTTable( TS_FILTER* pTSFilter, int pmt_index, int pid )
{
	int pat_index;
	PMT_DATA pmt_data;
	pmt_data.pmt_section = pTSFilter->pmt_section[pmt_index];
	pmt_data.pmt_table   = &pTSFilter->pmt[pmt_index];
	pmt_data.channel     = pTSFilter->pmt_map[pmt_index].channel+1;
	pmt_data.total_channel_num = pTSFilter->mapped_num;
	pmt_data.pid         = pid;

	if ( pmt_index >= pTSFilter->pmt_num )
		return;

	pmt_data.update_flag = pTSFilter->pmt[pmt_index].update_flag;
	if ( pTSFilter->pmt_map[pmt_index].hit_flag & 0x01 )
		pmt_data.update_flag |= PMT_REPEAT;
	pTSFilter->pmt_map[pmt_index].hit_flag |= 0x01;

	if ( ( pat_index = GetPatIndex( pTSFilter, pid, pTSFilter->pmt[pmt_index].program_number ) ) >= 0 ) 
	{
		pmt_data.tsid = pTSFilter->pat[pat_index].tsid;
	}
	else
	{
		//ZQ expected PAT isn't ready (multiple DVB-S most likely )
		return;
		pmt_data.tsid = 0xffff;		
		pmt_data.total_channel_num = 0;
	}

	pTSFilter->dumper.pmt_dumper( pTSFilter->dumper.pmt_dumper_context, (void*)&pmt_data, sizeof(PMT_DATA) );

}

static inline int ParseTSTable( TS_FILTER* pTSFilter, TS_PACKET *pTSPacket )
{
	int pat_index, pmt_index;
	int ret;

	//process PAT
	if ( pTSPacket->pid == 0 )
	{                                      
		pat_index = UnpackPAT( pTSFilter, pTSPacket );
		if ( pat_index >= 0 )
		{
			//if PAT updated, dump new PAT table
			ret = UpdatePMTMap( pTSFilter, pat_index );
			
			if ( pTSFilter->dumper.pat_dumper != NULL )
			{
				PAT_DATA pat_data;
				pat_data.pat_section = pTSFilter->pat_section;
				pat_data.pat_table = &pTSFilter->pat[pat_index];
				pat_data.pat_index = pat_index;
				pat_data.update_flag = ret;
				ret = pTSFilter->dumper.pat_dumper( pTSFilter->dumper.pat_dumper_context, (void*)&pat_data, sizeof(PAT_DATA) );
				pTSFilter->pat[pat_index].locked = ret;
			}
		}

		return 1;
	} else
	//if it's PMT pid, process PMT
	if ( ( pmt_index = GetPmtIndex( pTSFilter, pTSPacket->pid ) ) >= 0 )
	{
		int update_num;
		if ( (update_num = UnpackPMT( pTSFilter, pmt_index, pTSPacket )) > 0 )
		{
			if ( pTSFilter->dumper.pmt_dumper != NULL )
			{
				//a normall PMT that doens't share a pid with other PMTs
				if ( pTSFilter->pmt_map[pmt_index].group_flag == 0 )
				{
					DumpPMTTable( pTSFilter, pmt_index, pTSPacket->pid );
				} else
				{
					int i;
					for ( i = 0; i<pTSFilter->pmt_num; i++ )
					{
						int pmt_index = GetPmtIndex2( pTSFilter, pTSPacket->pid, pTSFilter->pmt[i].program_number );
						if ( pmt_index > 0 )
						{
							DumpPMTTable( pTSFilter, pmt_index, pTSPacket->pid );
							pTSFilter->pmt[i].update_flag = 0;
						}
					}
				}
			}
		}

		return 1;
	} 

	return 0;
}

static inline int FilterStreaming( TS_FILTER* pTSFilter, TS_PACKET *pTSPacket )
{
	int i, k = 0, ret = 0;
	if ( pTSFilter->ts_streams_num )
	{
		TS_STREAMS *ts_streams_ptr = pTSFilter->ts_streams[k];
		
		//if ( ts_streams_ptr == NULL  ) continue;

		for ( i = 0; i<ts_streams_ptr->num_stream; i++ )
		{
			if ( pTSPacket->pid == ts_streams_ptr->ts_element[i].pid )
			{
				STREAM_DATA stream_data;
				stream_data.bytes	 = pTSPacket->payload_bytes;
				stream_data.data_ptr = pTSPacket->data + pTSPacket->payload_offset;
				stream_data.group_start = pTSPacket->start;
				stream_data.scrambling  = pTSPacket->scrambling_ctr;
				stream_data.priority    = pTSPacket->priority;
				stream_data.ts_elment   = &ts_streams_ptr->ts_element[i];
				stream_data.container   = ts_streams_ptr->container;
				pTSFilter->dumper.stream_dumper( pTSFilter->dumper.stream_dumper_context, (void*)&stream_data, sizeof(STREAM_DATA) );
				ret = 1;
			}
		}
	}

	return ret;
}

/*
static inline void FastFiltering( TS_FILTER* pTSFilter, TS_PACKET* pTSPacket )
{
	int i;
	if ( pTSPacket->pid == 0 ) //PAT packet;
	{
		PACKET_DATA packet_data= {0};
		packet_data.packet = pTSPacket;
		pTSFilter->fast_filter.packet_dumper( pTSFilter->fast_filter.packet_dumper_context, 
			                                  (void*)&packet_data, sizeof(packet_data) );
		return;
	}

	for ( i = 0; i<pTSFilter->fast_filter.pid_tbl_num; i++ )
	{
		if( pTSPacket->pid == pTSFilter->fast_filter.pid_tbl[i].pid )
		{
			PACKET_DATA packet_data;
			if ( pTSPacket->pcr_flag )
			{
				PCR_DATA pcr_data;
				pcr_data.pcr = pTSPacket->pcr;
				pcr_data.pid = pTSPacket->pid;
				pcr_data.ts_packet_counter = pTSFilter->ts_packet_counter;
				pTSFilter->fast_filter.pcr_dumper( pTSFilter->fast_filter.pcr_dumper_context, 
					                                (void*)&pcr_data, sizeof(PCR_DATA) );
			}
			packet_data.packet = pTSPacket;
			packet_data.pid_item = pTSFilter->fast_filter.pid_tbl[i];
			pTSFilter->fast_filter.packet_dumper( pTSFilter->fast_filter.packet_dumper_context, 
				                                  (void*)&packet_data, sizeof(packet_data) );
			return;
		}
	}
}
*/

static inline void FilterPCRnCA( TS_FILTER* pTSFilter, TS_PACKET *pTSPacket )
{
	int k = 0;

	if ( pTSFilter->ts_streams_num )
	{
		TS_STREAMS *ts_streams_ptr = pTSFilter->ts_streams[k];
		
		if ( pTSFilter->dumper.pcr_dumper )
		{ 
			if ( ts_streams_ptr->pcr_pid == pTSPacket->pid )
			{ 
				if ( pTSPacket->pcr_flag )
				{
					PCR_DATA pcr_data;
					pcr_data.pcr = pTSPacket->pcr;
					pcr_data.pid = pTSPacket->pid;
					pcr_data.ts_packet_counter = pTSFilter->ts_packet_counter;
					pcr_data.container = ts_streams_ptr->container;
					pTSFilter->dumper.pcr_dumper( pTSFilter->dumper.pcr_dumper_context, (void*)&pcr_data, sizeof(PCR_DATA) );
				}
			}
		}

		if ( pTSFilter->dumper.ca_dumper )
		{
			if ( ts_streams_ptr->ca_pid && ts_streams_ptr->ca_pid == pTSPacket->pid )
			{
				CA_DATA ca_data;
				ca_data.pid = pTSPacket->pid;
				ca_data.ca_id = 0;
				ca_data.bytes	 = pTSPacket->payload_bytes;
				ca_data.data_ptr = pTSPacket->data;
				ca_data.data_ptr = pTSPacket->data + pTSPacket->payload_offset;
				ca_data.container   = ts_streams_ptr->container;
				pTSFilter->dumper.ca_dumper( pTSFilter->dumper.ca_dumper_context, (void*)&ca_data, sizeof(CA_DATA) );
			}
		}
	}
	
}

static inline int FilterSageNullPacket( TS_FILTER* pTSFilter, TS_PACKET *pTSPacket )
{
	if ( pTSPacket->data[4] == 'S' && pTSPacket->data[5] == 'A' && 
		 pTSPacket->data[6] == 'G' && pTSPacket->data[7] == 'E' )
	{
		if ( pTSFilter->dumper.ats_dumper )
		{
			ATS_TIME ats;
			ats.type = 1;
			ats.packets = pTSFilter->ts_packet_counter;
			ats.t.ats = (pTSPacket->data[14]<<24) | (pTSPacket->data[15]<<16) |
						(pTSPacket->data[17]<<8) | (pTSPacket->data[18]);
			pTSFilter->dumper.ats_dumper( pTSFilter->dumper.ats_dumper_context, &ats, sizeof(ats) );
		}
		return 1;
	}
	return 0;
}

int TSProcess( TS_FILTER* pTSFilter, uint8_t* pData )
{
	TS_PACKET TSPacket;

	pTSFilter->ts_packet_counter++;

	//parse ts to get data
	if ( !UnpackTSPacket( &TSPacket, pData ) )
	{
		return -1;
	}

	if ( TSPacket.pid == 0x1fff ) //null packet may caary PCR
	{
		//it's SageTV null packets.
		if ( FilterSageNullPacket( pTSFilter, &TSPacket ) )
			return 0;

		FilterPCRnCA( pTSFilter, &TSPacket );
		return 0;
	}

	//parse PAT PMT table, to get channel information
	if ( !pTSFilter->disable_ts_table_parse )
	{
		if ( ParseTSTable( pTSFilter, &TSPacket ) > 0 )
		{
			return 1;
		}
	}

	HistogramPid( pTSFilter, &TSPacket );

	//filte out a packet to dumper
	if ( !pTSFilter->disable_stream_filter )
	{
		FilterPCRnCA( pTSFilter, &TSPacket );
		if ( FilterStreaming( pTSFilter, &TSPacket ) > 0 )
		{
			return 1;
		}
	}

	//parse PSI data 
	if ( !pTSFilter->disable_psi_parse )
	{
		if (  ParseTSPSI( pTSFilter, &TSPacket ) > 0 )
		{
			return 1;
		}
	}

	return 0;
}

//it called by TSInfoParser only
int TSProcessInfo( TS_FILTER* pTSFilter, uint8_t* pData )
{
	TS_PACKET TSPacket;

	pTSFilter->ts_packet_counter++;

	//parse ts to get data
	if ( !UnpackTSPacket( &TSPacket, pData ) )
	{
		return -1;
	}

	if ( TSPacket.pid == 0x1fff ) //null packet may caary PCR
	{
		FilterPCRnCA( pTSFilter, &TSPacket );
		return 0;
	}

	//parse PAT PMT table, to get channel information
	if ( !pTSFilter->disable_ts_table_parse )
	{
		if ( ParseTSTable( pTSFilter, &TSPacket ) > 0 )
		{
			return 1;
		}
	}

	//filte out a packet to dumper
	if ( !pTSFilter->disable_stream_filter )
	{
		FilterPCRnCA( pTSFilter, &TSPacket );
		if ( FilterStreaming( pTSFilter, &TSPacket ) > 0 )
		{
			return 2;
		}
	}

	//parse PSI data 
	if ( !pTSFilter->disable_psi_parse )
	{
		if (  ParseTSPSI( pTSFilter, &TSPacket ) > 0 )
		{
			return 3;
		}
	}

	return 0;
}

int BlastPatTable( TS_FILTER* pTSFilter, int nProgram, int nTsid )
{
	int pat_index = -1;
	int i;

	for ( i = 0; i<pTSFilter->mapped_num; i++ )
	{
		if ( pTSFilter->pmt_map[i].program == nProgram )
		{
			pat_index = pTSFilter->pmt_map[i].pat_index;
			if ( pTSFilter->pat[pat_index].tsid == nTsid )
			{
				break;
			} else
				pat_index = -1;
		}
	}
	
	if ( pat_index < 0 )
		return 0;

	SageLog(( _LOG_TRACE, 3, TEXT("Blast a PAT, program%d tsid:%d index:%d\r\n"),  nProgram, nTsid, pat_index ));
	if ( pTSFilter->dumper.pat_dumper != NULL )
	{
		PAT_DATA pat_data;
		int ret;
		pat_data.pat_section = pTSFilter->pat_section;
		pat_data.pat_table = &pTSFilter->pat[pat_index];
		pat_data.pat_index = pat_index;
		pat_data.update_flag = 0;
		ret = pTSFilter->dumper.pat_dumper( pTSFilter->dumper.pat_dumper_context, (void*)&pat_data, sizeof(PAT_DATA) );
		pTSFilter->pat[pat_index].locked = ret;
	}
	return 1;
}

int BlastPmtTable( TS_FILTER* pTSFilter, int nProgram, int nTsid )
{
	int pmt_index = -1, pid;
	int i;

	for ( i = 0; i<pTSFilter->mapped_num; i++ )
	{	
		if ( pTSFilter->pmt_map[i].program == nProgram )
		{
			int pat_index =  pTSFilter->pmt_map[i].pat_index;
			if ( pTSFilter->pat[pat_index].tsid == nTsid )
			{
				pmt_index = i;
				pid = pTSFilter->pmt_map[i].pid;
				break;
			}
		}
	}	
	
	if ( pmt_index < 0 )
		return 0;

	if ( pTSFilter->dumper.pmt_dumper != NULL )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Blast a PMT, program:%d tsid:%d pid%d\r\n"),  nProgram, nTsid, pid ));
		//a normall PMT that doens't share a pid with other PMTs
		if ( pTSFilter->pmt_map[pmt_index].group_flag == 0 )
		{
			DumpPMTTable( pTSFilter, pmt_index, pid );
		} else
		{
			int i;
			for ( i = 0; i<pTSFilter->pmt_num; i++ )
			{
				int pmt_index = GetPmtIndex2( pTSFilter, pid, nProgram );
				if ( pmt_index > 0 )
				{
					DumpPMTTable( pTSFilter, pmt_index, pid );
					pTSFilter->pmt[i].update_flag = 0;
				}
			}
		}
		
	}
	return 1;
}


uint32_t GetLanguageCode( uint8_t* pData, int nBytes )
{
	uint8_t* desc_ptr;
	int desc_len;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, ISO639_LANGUAGE_DESC, &desc_len ) )!= NULL && desc_len >= 3 )
	{
		return LanguageCode( desc_ptr+2 );
	}
	return 0;
}
uint8_t GetAudioType( uint8_t* pData, int nBytes )
{
	uint8_t* desc_ptr;
	int desc_len;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, ISO639_LANGUAGE_DESC, &desc_len ) )!= NULL && desc_len >= 3 )
	{
		if ( desc_len >= 4 ) 
			return desc_ptr[5]; //1: clean effects (no language); 2:hearing impaired; 3: visually impaired commentary
	}
	return 0;
}

static int HasTeletextDesc( uint8_t *pData, int nBytes )
{
	uint8_t* desc_ptr;
	int desc_len;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, TELTEXT_DESC, &desc_len ) )!= NULL  )
		return 1;
	return 0;
}

static int HasVbiDesc( uint8_t *pData, int nBytes )
{
	uint8_t* desc_ptr;
	int desc_len;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, VBI_DATA_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return 1;
	return 0;
}

static int HasSubtitleDesc( uint8_t *pData, int nBytes )
{
	uint8_t* desc_ptr;
	int desc_len;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, SUBTITLE_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return 1;
	return 0;
}

uint32_t GetSubtitleLanuage( uint8_t *pData, int nBytes )
{
	uint8_t* desc_ptr;
	int desc_len;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, SUBTITLE_DESC, &desc_len ) )!= NULL && desc_len > 0 )
	{
		return LanguageCode( desc_ptr+2 );
	}
	return 0;
}

static uint8_t REG_H264[8]={ 0x48, 0x44, 0x4d, 0x56, 0xff, 0x1b, 0x61, 0x3f };
static uint8_t REG_LPCM[8]={ 0x48, 0x44, 0x4d, 0x56, 0xff, 0x80, 0x61, 0xff };
static int HasVideoDesc( uint8_t *pData, int nBytes )
{
	uint8_t* desc_ptr;
	int desc_len;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, VIDEO_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return 1;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, ATSC_CAPTION_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return 1;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, REGISTRATION_DESC, &desc_len ) )!= NULL && desc_len > 0 )
	{
		if ( desc_len >= 8  && !memcmp( desc_ptr+2, REG_H264, 6 ) )
			return 1;
	}
	return 0;
}

static int HasDCIIVideoDesc( uint8_t *pData, int nBytes )
{
	uint8_t* desc_ptr;
	int desc_len;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, VIDEO_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return 1;
	return 0;
}
uint32_t HasAudioDesc( uint8_t *pData, int nBytes )
{
	uint8_t* desc_ptr;
	int desc_len;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, DVB_AC3_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return SAGE_FOURCC("AC3 ");
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, ATSC_AC3_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return SAGE_FOURCC("AC3 ");
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, MPEG4_AUDIO_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return SAGE_FOURCC("MP4V");
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, DVB_DTS_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return SAGE_FOURCC("DVB_DTS ");
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, DVB_AAC_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return SAGE_FOURCC("AAC ");
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, DVB_EAC3_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return SAGE_FOURCC("AC3E");
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, DTS_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return SAGE_FOURCC("DTS ");
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, AUDIO_DESC, &desc_len ) )!= NULL && desc_len > 0 )
		return SAGE_FOURCC("MPGA"); 
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, REGISTRATION_DESC, &desc_len ) )!= NULL && desc_len > 0 )
	{
		if ( desc_len >= 8  && !memcmp( desc_ptr+2, REG_LPCM, 6 ) )
			return SAGE_FOURCC("LPCM"); 
	}

	return 0;
}

uint32_t VideoFormat( uint8_t StreamType, uint8_t* pDesc, int nBytes )
{
	if ( StreamType == H264_STREAM_TYPE )
	{
		return SAGE_FOURCC( "H264" );
	}
	else
	if ( StreamType == VC1_STREAM_TYPE )
	{
		return SAGE_FOURCC( "VC1 " );
	}
	else
	if ( StreamType == MPEG4_STREAM_TYPE )
	{
		return SAGE_FOURCC( "MP4A" );
	}

	return 0;
}

uint32_t TVVideoFormat( uint8_t StreamType, uint8_t* pDesc, int nBytes )
{
	if ( StreamType == H264_STREAM_TYPE )
	{
		return SAGE_FOURCC( "H264" );
	}
	else
	//if ( StreamType == VC1_STREAM_TYPE )
	//{
	//	return SAGE_FOURCC( "VC1 " );
	//}
	//else
	//if ( StreamType == MPEG4_STREAM_TYPE )
	//{
	//	return SAGE_FOURCC( "MP4A" );
	//}
	//else
	if ( StreamType == VIDEO_STREAM_TYPE )
	{
		return SAGE_FOURCC( "MPGV" );
	}

	return 0;
}

uint32_t AudioFormatByStreamType( uint8_t StreamType )
{
	if ( StreamType == AC3_STREAM_TYPE )
	{
		return SAGE_FOURCC( "AC3 " );
	}
	if ( StreamType == AUDIO_STREAM_TYPE )
	{
		return SAGE_FOURCC( "MPGA" );
	}
	if ( StreamType == AUDIO_STREAM1_TYPE )
	{
		return SAGE_FOURCC( "MPGA" );
	}
	if ( StreamType == AAC_STREAM_TYPE )
	{
		return SAGE_FOURCC( "AAC " );
	}
	if ( StreamType == AAC_HE_STREAM_TYPE )
	{
		return SAGE_FOURCC( "AACH" );
	}
	if ( StreamType == DTS_STREAM_TYPE )
	{
		return SAGE_FOURCC( "DTS " );
	}
	if ( StreamType == HD_DTS_STREAM_TYPE )
	{
		return SAGE_FOURCC( "DTSH" );
	}
	if ( StreamType == HD_MASTER_DTS_STREAM_TYPE )
	{
		return SAGE_FOURCC( "DTSM" );
	}
	if ( StreamType == TUREHD_AC3_STREAM_TYPE )
	{
		return SAGE_FOURCC( "AC3T" );  
	}
	if ( StreamType == E_AC3_STREAM_TYPE || StreamType == E_AC3_STREAM2_TYPE )
	{
		return SAGE_FOURCC( "AC3E" );  
	}
	if ( StreamType == LPCM_STREAM_TYPE )
	{
		return SAGE_FOURCC( "LPCM" );  
	}
	
	
	return 0;

}

#define IsDVDMedia( x ) ( x == MEDIA_TYPE_DVD )
#define IsTVMedia( x )  ( x == MEDIA_TYPE_TV  )

static void ParseCADesc( TS_PMT *pPmt, DESC_DATA *pDesc )
{
	int desc_len;
	uint8_t *desc_ptr;
	if ( ( desc_ptr = GetDescriptor( pDesc->desc_ptr, pDesc->desc_bytes, CA_DESC, &desc_len ) )!= NULL && desc_len >= 4 )
	{
		pPmt->ca_id  = ( desc_ptr[2]<<8 )|desc_ptr[3];
		pPmt->ca_pid = ( ( desc_ptr[4]&0x1f )<<8 )|desc_ptr[5];
	}
}

static char* _print_es_descriptor_( uint16_t pid, uint8_t* p, int bytes );
static void  _prints_ts_elmnt( TS_ELEMENT* elmnt );
int SelectTSFilterChannel( TS_FILTER* pTSFilter, TS_STREAMS* pTsStreams, uint16_t nTsid, uint16_t nProgram, uint16_t nMediaType )
{
	int i, j;
	int pmt_stream_index[MAX_ES]={0};
	for ( i = 0; i<pTSFilter->mapped_num && pTSFilter->pmt_map[i].pid ; i++ )
	{
		int pat_index = GetPatIndex( pTSFilter, pTSFilter->pmt_map[i].pid, nProgram );
		if ( pat_index < 0 ) continue;
		if ( pTSFilter->pat[pat_index].tsid == nTsid &&	pTSFilter->pmt[i].program_number == nProgram )
		{
			int k = 0, n = _MIN( pTSFilter->pmt[i].total_stream_number, pTsStreams->total_streams );
			//setup stream filter
			pTsStreams->pcr_pid = pTSFilter->pmt[i].pcr_pid;
			if ( pTSFilter->pmt[i].ca_pid > 0 )
				pTsStreams->ca_pid = pTSFilter->pmt[i].ca_pid;
			else
				pTsStreams->ca_pid = 0;
			
			//prepare video stream elemnet first
			for ( j = 0; j < n && k < pTsStreams->total_streams ; j++ )
			{
				if ( pTSFilter->pmt[i].stream_type[j] == 0 ) continue;
				
				if ( IsVideoType( pTSFilter->pmt[i].stream_type[j] ) )
				{
					pTsStreams->ts_element[k].content_type = VIDEO_DATA; 
					pTsStreams->ts_element[k].channel_index = k;
					pTsStreams->ts_element[k].pid = pTSFilter->pmt[i].stream_pid[j];
					pTsStreams->ts_element[k].type = pTSFilter->pmt[i].stream_type[j];
					if ( IsTVMedia( nMediaType ) )
						pTsStreams->ts_element[k].format_fourcc = 
											TVVideoFormat( pTsStreams->ts_element[k].type,
														 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
														 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					else
						pTsStreams->ts_element[k].format_fourcc = 
											VideoFormat( pTsStreams->ts_element[k].type,
														 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
														 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );

					pTsStreams->ts_element[k].desc_len = pTSFilter->pmt[i].stream_desc[j].desc_bytes;
					if ( pTsStreams->ts_element[k].desc_len > sizeof(pTsStreams->ts_element[k].desc) )
						pTsStreams->ts_element[k].desc_len = sizeof(pTsStreams->ts_element[k].desc);
					memcpy( pTsStreams->ts_element[k].desc, pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTsStreams->ts_element[k].desc_len );

					_print_es_descriptor_( pTSFilter->pmt[i].stream_pid[j], 
						                   pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
							               pTSFilter->pmt[i].stream_desc[j].desc_bytes   ); //ZQ Debug
					k++;
					pmt_stream_index[j] = k;
				} else
				if (  (( pTSFilter->pmt[i].stream_type[j] == PRIVATE_STREAM_TYPE || pTSFilter->pmt[i].stream_type[j] == PRIVATE_0x80_STREAM_TYPE ) && 
                         HasVideoDesc( (uint8_t*)pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTSFilter->pmt[i].stream_desc[j].desc_bytes  ))  || 
                      ( pTSFilter->pmt[i].stream_type[j] == PRIVATE_0x80_STREAM_TYPE && IsTVMedia( nMediaType ) ) ||
					  ( pTSFilter->pmt[i].stream_type[j] == PRIVATE_0x80_STREAM_TYPE && 
					     HasDCIIVideoDesc((uint8_t*)pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTSFilter->pmt[i].stream_desc[j].desc_bytes )) )
				{
					pTsStreams->ts_element[k].content_type = VIDEO_DATA; 
					pTsStreams->ts_element[k].channel_index = k;
					pTsStreams->ts_element[k].pid = pTSFilter->pmt[i].stream_pid[j];
					pTsStreams->ts_element[k].type = pTSFilter->pmt[i].stream_type[j];
					if ( IsTVMedia( nMediaType ) )
						pTsStreams->ts_element[k].format_fourcc = 
											TVVideoFormat( pTsStreams->ts_element[k].type,
														 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
														 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					else
						pTsStreams->ts_element[k].format_fourcc = 
											VideoFormat( pTsStreams->ts_element[k].type,
														 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
														 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );

					pTsStreams->ts_element[k].desc_len = pTSFilter->pmt[i].stream_desc[j].desc_bytes;
					if ( pTsStreams->ts_element[k].desc_len > sizeof(pTsStreams->ts_element[k].desc) )
						pTsStreams->ts_element[k].desc_len = sizeof(pTsStreams->ts_element[k].desc);
					memcpy( pTsStreams->ts_element[k].desc, pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTsStreams->ts_element[k].desc_len );

					_print_es_descriptor_(   pTSFilter->pmt[i].stream_pid[j], 
						                     pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
							                 pTSFilter->pmt[i].stream_desc[j].desc_bytes   ); //ZQ Debug
					k++;
					pmt_stream_index[j] = k;
				} 
			}

			//prepare audio stream elemnet first
			for ( j = 0; j < n && k < pTsStreams->total_streams ; j++  )
			{
				if ( pTSFilter->pmt[i].stream_type[j] == 0 ) continue;
				if ( (IsTVMedia( nMediaType ) && IsTVAuidoType( pTSFilter->pmt[i].stream_type[j] )) ||
					 (!IsTVMedia( nMediaType ) && IsAudioType( pTSFilter->pmt[i].stream_type[j] )  ) )
				{
					pTsStreams->ts_element[k].content_type = AUDIO_DATA; 
					pTsStreams->ts_element[k].channel_index = k;
					pTsStreams->ts_element[k].pid = pTSFilter->pmt[i].stream_pid[j];
					pTsStreams->ts_element[k].type = pTSFilter->pmt[i].stream_type[j];
					pTsStreams->ts_element[k].language_code = GetLanguageCode( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
						                                                           pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTsStreams->ts_element[k].audio_type = GetAudioType( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
						                                                           pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTsStreams->ts_element[k].format_fourcc = AudioFormatByStreamType( pTsStreams->ts_element[k].type );

					if ( pTsStreams->ts_element[k].format_fourcc == 0 )
						pTsStreams->ts_element[k].format_fourcc = 
						       HasAudioDesc( (uint8_t*)pTSFilter->pmt[i].stream_desc[j].desc_ptr,  pTSFilter->pmt[i].stream_desc[j].desc_bytes  );

					pTsStreams->ts_element[k].desc_len = pTSFilter->pmt[i].stream_desc[j].desc_bytes;
					if ( pTsStreams->ts_element[k].desc_len > sizeof(pTsStreams->ts_element[k].desc) )
						pTsStreams->ts_element[k].desc_len = sizeof(pTsStreams->ts_element[k].desc);
					memcpy( pTsStreams->ts_element[k].desc, pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTsStreams->ts_element[k].desc_len );

					_print_es_descriptor_(   pTSFilter->pmt[i].stream_pid[j], 
											 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
							                 pTSFilter->pmt[i].stream_desc[j].desc_bytes   ); //ZQ Debug
					k++;
					pmt_stream_index[j] = k;
				} else
				if ( ( pTSFilter->pmt[i].stream_type[j] == PRIVATE_STREAM_TYPE || pTSFilter->pmt[i].stream_type[j] == PRIVATE_0x80_STREAM_TYPE ) &&
					 (  IsDVDMedia( nMediaType ) || ( pTsStreams->ts_element[k].format_fourcc = HasAudioDesc( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
																								    pTSFilter->pmt[i].stream_desc[j].desc_bytes  )))  )
				{
					if ( pTsStreams->ts_element[k].format_fourcc == 0 )
						pTsStreams->ts_element[k].format_fourcc = HasAudioDesc( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
																			    pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTsStreams->ts_element[k].content_type = AUDIO_DATA; 
					pTsStreams->ts_element[k].channel_index = k;
					pTsStreams->ts_element[k].pid = pTSFilter->pmt[i].stream_pid[j];
					pTsStreams->ts_element[k].type = pTSFilter->pmt[i].stream_type[j];
					pTsStreams->ts_element[k].language_code = GetLanguageCode( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
																						 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTsStreams->ts_element[k].audio_type = GetAudioType( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
						                                                                 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );

					pTsStreams->ts_element[k].desc_len = pTSFilter->pmt[i].stream_desc[j].desc_bytes;
					if ( pTsStreams->ts_element[k].desc_len > sizeof(pTsStreams->ts_element[k].desc) )
						pTsStreams->ts_element[k].desc_len = sizeof(pTsStreams->ts_element[k].desc);
					memcpy( pTsStreams->ts_element[k].desc, pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTsStreams->ts_element[k].desc_len );

					_print_es_descriptor_(   pTSFilter->pmt[i].stream_pid[j], 
											 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
							                 pTSFilter->pmt[i].stream_desc[j].desc_bytes   ); //ZQ Debug
					k++;
					pmt_stream_index[j] = k;

				}


			}

			//fill subtitle
			for ( j = 0; j < n && k < pTsStreams->total_streams ; j++  )
			{
				if ( pTSFilter->pmt[i].stream_type[j] == 0 ) continue; 
				if ( HasSubtitleDesc( (uint8_t*)pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTSFilter->pmt[i].stream_desc[j].desc_bytes  ) )
				{
					pTsStreams->ts_element[k].content_type = SUBTITLE_DATA; 
					pTsStreams->ts_element[k].channel_index = k;
					pTsStreams->ts_element[k].pid = pTSFilter->pmt[i].stream_pid[j];
					pTsStreams->ts_element[k].type = pTSFilter->pmt[i].stream_type[j];

					pTsStreams->ts_element[k].desc_len = pTSFilter->pmt[i].stream_desc[j].desc_bytes;
					if ( pTsStreams->ts_element[k].desc_len > sizeof(pTsStreams->ts_element[k].desc) )
						pTsStreams->ts_element[k].desc_len = sizeof(pTsStreams->ts_element[k].desc);
					memcpy( pTsStreams->ts_element[k].desc, pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTsStreams->ts_element[k].desc_len );
					//pTsStreams->ts_element[k].language_code = GetLanguageCode( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
					//	                                                                 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTsStreams->ts_element[k].language_code =
							GetSubtitleLanuage( (uint8_t*)pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTSFilter->pmt[i].stream_desc[j].desc_bytes );

					_print_es_descriptor_(   pTSFilter->pmt[i].stream_pid[j], 
											 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
							                 pTSFilter->pmt[i].stream_desc[j].desc_bytes   ); //ZQ Debug
					k++;
					pmt_stream_index[j] = k;
				} 
			}
			
			//fill subtitle of PGS (blueray)
			for ( j = 0; j < n && k < pTsStreams->total_streams ; j++  )
			{
				if ( pTSFilter->pmt[i].stream_type[j] == 0x90 ) 
				{
					pTsStreams->ts_element[k].content_type = SUBTITLE_DATA; 
					pTsStreams->ts_element[k].channel_index = k;
					pTsStreams->ts_element[k].pid = pTSFilter->pmt[i].stream_pid[j];
					pTsStreams->ts_element[k].type = pTSFilter->pmt[i].stream_type[j];
					pTsStreams->ts_element[k].desc_len = pTSFilter->pmt[i].stream_desc[j].desc_bytes;
					if ( pTsStreams->ts_element[k].desc_len > sizeof(pTsStreams->ts_element[k].desc) )
						pTsStreams->ts_element[k].desc_len = sizeof(pTsStreams->ts_element[k].desc);
					memcpy( pTsStreams->ts_element[k].desc, pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTsStreams->ts_element[k].desc_len );
					//pTsStreams->ts_element[k].language_code = GetLanguageCode( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
					//	                                                                 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTsStreams->ts_element[k].language_code =
							GetSubtitleLanuage( (uint8_t*)pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTSFilter->pmt[i].stream_desc[j].desc_bytes );

					_print_es_descriptor_(   pTSFilter->pmt[i].stream_pid[j], 
											 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
							                 pTSFilter->pmt[i].stream_desc[j].desc_bytes   ); //ZQ Debug
					k++;
					pmt_stream_index[j] = k;
				} 
			}

			//fill teletext
			for ( j = 0; j < n && k < pTsStreams->total_streams ; j++ )
			{
				if ( pTSFilter->pmt[i].stream_type[j] == 0 ) continue; 
				if ( HasTeletextDesc( (uint8_t*)pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTSFilter->pmt[i].stream_desc[j].desc_bytes  ) )
				{
					pTsStreams->ts_element[k].content_type = TELETEXT_DATA; 
					pTsStreams->ts_element[k].channel_index = k;
					pTsStreams->ts_element[k].pid = pTSFilter->pmt[i].stream_pid[j];
					pTsStreams->ts_element[k].type = pTSFilter->pmt[i].stream_type[j];
					pTsStreams->ts_element[k].language_code = GetLanguageCode( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
						                                                                 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTsStreams->ts_element[k].audio_type = GetAudioType( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
						                                                                 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTsStreams->ts_element[k].desc_len = pTSFilter->pmt[i].stream_desc[j].desc_bytes;
					if ( pTsStreams->ts_element[k].desc_len > sizeof(pTsStreams->ts_element[k].desc) )
						pTsStreams->ts_element[k].desc_len = sizeof(pTsStreams->ts_element[k].desc);
					memcpy( pTsStreams->ts_element[k].desc, pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTsStreams->ts_element[k].desc_len );

					_print_es_descriptor_(   pTSFilter->pmt[i].stream_pid[j], 
											 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
							                 pTSFilter->pmt[i].stream_desc[j].desc_bytes   ); //ZQ Debug
					k++;
					pmt_stream_index[j] = k;
				} 
			}

			//fill vbi
			for ( j = 0; j < n && k < pTsStreams->total_streams ; j++ )
			{
				if ( pTSFilter->pmt[i].stream_type[j] == 0 ) continue; 
				if ( HasVbiDesc( (uint8_t*)pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTSFilter->pmt[i].stream_desc[j].desc_bytes  ) )
				{
					pTsStreams->ts_element[k].content_type = VBI_DATA; 
					pTsStreams->ts_element[k].channel_index = k;
					pTsStreams->ts_element[k].pid = pTSFilter->pmt[i].stream_pid[j];
					pTsStreams->ts_element[k].type = pTSFilter->pmt[i].stream_type[j];
					pTsStreams->ts_element[k].language_code = GetLanguageCode( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
						                                                                 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTsStreams->ts_element[k].audio_type = GetAudioType( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
						                                                                 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTsStreams->ts_element[k].desc_len = pTSFilter->pmt[i].stream_desc[j].desc_bytes;
					if ( pTsStreams->ts_element[k].desc_len > sizeof(pTsStreams->ts_element[k].desc) )
						pTsStreams->ts_element[k].desc_len = sizeof(pTsStreams->ts_element[k].desc);
					memcpy( pTsStreams->ts_element[k].desc, pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTsStreams->ts_element[k].desc_len );

					_print_es_descriptor_(   pTSFilter->pmt[i].stream_pid[j], 
											 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
							                 pTSFilter->pmt[i].stream_desc[j].desc_bytes   ); //ZQ Debug
					k++;
					pmt_stream_index[j] = k;
				} 
			}			
			//processing stream_type=0x06 unknown type
			for ( j = 0; j < n && k < pTsStreams->total_streams ; j++ )
			{
				if (  (pTSFilter->pmt[i].stream_type[j] == PRIVATE_STREAM_TYPE || pTSFilter->pmt[i].stream_type[j] == PRIVATE_0x80_STREAM_TYPE )
					  && pmt_stream_index[j] == 0 )
				{
					pTsStreams->ts_element[k].channel_index = k;
					pTsStreams->ts_element[k].pid = pTSFilter->pmt[i].stream_pid[j];
					pTsStreams->ts_element[k].type = pTSFilter->pmt[i].stream_type[j];

					if ( pTsStreams->ts_element[k].content_type == 0 )
					if (  pTSFilter->pmt[i].stream_type[j] == PRIVATE_STREAM_TYPE )
					{
						pTsStreams->ts_element[k].content_type = UNIDENTIFIED;
					}

					pTsStreams->ts_element[k].desc_len = pTSFilter->pmt[i].stream_desc[j].desc_bytes;
					if ( pTsStreams->ts_element[k].desc_len > sizeof(pTsStreams->ts_element[k].desc) )
						pTsStreams->ts_element[k].desc_len = sizeof(pTsStreams->ts_element[k].desc);
					memcpy( pTsStreams->ts_element[k].desc, pTSFilter->pmt[i].stream_desc[j].desc_ptr, pTsStreams->ts_element[k].desc_len );

					_print_es_descriptor_(   pTSFilter->pmt[i].stream_pid[j], 
											 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
							                 pTSFilter->pmt[i].stream_desc[j].desc_bytes   ); //ZQ Debug
					k++;
					pmt_stream_index[j] = k;
				}
			}

			pTsStreams->num_stream = k;

			if ( k <= 0 )
				return 1;

			//registry TS_STREAMS for filterring
			for ( j = 0; j<pTSFilter->ts_streams_num; j++ )
			{
				if( pTSFilter->ts_streams[j] == pTsStreams )
					break;
			}

			if ( j >= pTSFilter->ts_streams_num )
			{
				 if ( pTSFilter->ts_streams_num < MAX_SLOT_NUM )
					pTSFilter->ts_streams[pTSFilter->ts_streams_num++] = pTsStreams; 
				else
					if ( pTSFilter->ts_streams_num >= MAX_SLOT_NUM ) 
						return -1; //filter is full
			}

			for ( j = 0; j<k; j++ )
			{
				_prints_ts_elmnt( &pTsStreams->ts_element[j] );
				pTSFilter->pid_hist[j].pid = pTsStreams->ts_element[j].pid; 
				pTSFilter->pid_hist[j].count = 0;
			}
			SageLog(( _LOG_TRACE, 3, TEXT("\t PCR:\tpid:0x%02x"), pTsStreams->pcr_pid ));

			pTSFilter->pid_hist_num = k;
			pTSFilter->pid_hist_ctrl = 1; //fix pid table
			for ( j = 0; j<MAX_PID_HIST_NUM; j++ )
			{
				if ( j < k )
					pTSFilter->pid_hist[j].pid = pTsStreams->ts_element[j].pid; 
				else
					pTSFilter->pid_hist[j].pid = 0;
				pTSFilter->pid_hist[j].count = 0;
			}

			return k;
						
		}
	}

	return 0;
}


static int LookUpTSElemnt( TS_FILTER* pTSFilter, uint16_t uPid, TS_ELEMENT *pTSElemnt, int nMediaType )
{
	int i, j;
	//int pmt_stream_index[MAX_ES]={0};
	for ( i = 0; i<pTSFilter->mapped_num && pTSFilter->pmt_map[i].pid ; i++ )
	{ 
		for ( j = 0; j < pTSFilter->pmt[i].total_stream_number; j++ )
			if ( pTSFilter->pmt[i].stream_pid[j] == uPid ) 
			{
				if ( IsVideoType( pTSFilter->pmt[i].stream_type[j] ) )
				{
					pTSElemnt->content_type = VIDEO_DATA; 
					pTSElemnt->pid = uPid;
					pTSElemnt->type = pTSFilter->pmt[i].stream_type[j];
					if ( IsTVMedia( nMediaType ) )
						pTSElemnt->format_fourcc = TVVideoFormat( pTSElemnt->type,
															 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
															 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					else
						pTSElemnt->format_fourcc = VideoFormat( pTSElemnt->type,
															 pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
															 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
				} else
				if ( IsAudioType( pTSFilter->pmt[i].stream_type[j] ) )
				{
					pTSElemnt->content_type = AUDIO_DATA; 
					pTSElemnt->pid = uPid;
					pTSElemnt->type = pTSFilter->pmt[i].stream_type[j];
					pTSElemnt->language_code = GetLanguageCode( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
															   pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTSElemnt->audio_type = GetAudioType( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
														   pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTSElemnt->format_fourcc = AudioFormatByStreamType( pTSElemnt->type );

					if ( pTSElemnt->format_fourcc == 0 )
						pTSElemnt->format_fourcc = HasAudioDesc( (uint8_t*)pTSFilter->pmt[i].stream_desc[j].desc_ptr,  
																 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
				} else
				if ( ( pTSFilter->pmt[i].stream_type[j] == PRIVATE_STREAM_TYPE ||
					   pTSFilter->pmt[i].stream_type[j] == PRIVATE_0x80_STREAM_TYPE ) &&
						(  IsDVDMedia( nMediaType ) || 
						   ( pTSElemnt->format_fourcc = HasAudioDesc( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
														pTSFilter->pmt[i].stream_desc[j].desc_bytes  )))  )
				{
					pTSElemnt->content_type = AUDIO_DATA; 
					pTSElemnt->pid = uPid;
					pTSElemnt->type = pTSFilter->pmt[i].stream_type[j];
					pTSElemnt->language_code = GetLanguageCode( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
																 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
					pTSElemnt->audio_type = GetAudioType( pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
														 pTSFilter->pmt[i].stream_desc[j].desc_bytes  );
				}
				_print_es_descriptor_( pTSFilter->pmt[i].stream_pid[j], 
						   pTSFilter->pmt[i].stream_desc[j].desc_ptr, 
						   pTSFilter->pmt[i].stream_desc[j].desc_bytes   ); //ZQ Debug

				return i;

			}
	}
	return -1;
}


int SetupChannelFilterByPids( TS_FILTER* pTSFilter, TS_STREAMS* pTsStreams, PIDS_TABLE *pPidTbl, int nMediaType  )
{
	int ret, i, n = 0;
	for ( i = 0; i<pPidTbl->pid_num && i<pTsStreams->total_streams; i++ )
	{
		ret = LookUpTSElemnt( pTSFilter, pPidTbl->pid_tbl[i].pid, &pTsStreams->ts_element[i],  nMediaType );
		if ( ret >= 0 )
		{
			pTsStreams->ts_element[i].channel_index = n++;
			if ( pTsStreams->pcr_pid == 0 )
			{
				pTsStreams->pcr_pid = pTSFilter->pmt[ret].pcr_pid;
				if ( pTSFilter->pmt[i].ca_pid > 0 )
					pTsStreams->ca_pid = pTSFilter->pmt[ret].ca_pid;
				else
					pTsStreams->ca_pid = 0;
			}
		}
	}

	if ( n == pPidTbl->pid_num )
	{
		int j;
		pTsStreams->num_stream = n;
		pTSFilter->ts_streams_num = 1;
		pTSFilter->ts_streams[0] = pTsStreams; 

		for ( j = 0; j<n && j<MAX_PID_HIST_NUM ; j++ )
		{
			if ( j < n )
			{
				_prints_ts_elmnt( &pTsStreams->ts_element[j] );
				pTSFilter->pid_hist[j].pid = pTsStreams->ts_element[j].pid; 
			}
			pTSFilter->pid_hist[j].count = 0;
		}
		SageLog(( _LOG_TRACE, 3, TEXT("\t PCR:\tpid:0x%02x"), pTsStreams->pcr_pid ));

	}

	return n;
}

int SetupChannelFilter( TS_FILTER* pTSFilter, TS_STREAMS* pTsStreams  )
{
	int j;
	//registry TS_STREAMS for filterring
	for ( j = 0; j<pTSFilter->ts_streams_num; j++ )
	{
		if( pTSFilter->ts_streams[j] == pTsStreams )
			break;
	}
	if ( j >= pTSFilter->ts_streams_num )
	{
		 if ( pTSFilter->ts_streams_num < MAX_SLOT_NUM )
			pTSFilter->ts_streams[pTSFilter->ts_streams_num++] = pTsStreams; 
		else
			if ( pTSFilter->ts_streams_num >= MAX_SLOT_NUM ) 
				return -1; //filter is full
	}
	pTSFilter->pid_hist_num = pTsStreams->num_stream;
	pTSFilter->pid_hist_ctrl = 1; //fix pid table
	for ( j = 0; j<pTsStreams->num_stream ; j++ )
	{
		_prints_ts_elmnt( &pTsStreams->ts_element[j] );
		pTSFilter->pid_hist[j].pid = pTsStreams->ts_element[j].pid; 
		pTSFilter->pid_hist[j].count = 0;
	}

	if( pTsStreams->pcr_pid )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("\t PCR:\tpid:0x%02x"), pTsStreams->pcr_pid ));
	}

	return 1;
}

int BuildChannelTSFilter( TS_FILTER* pTSFilter, TS_STREAMS* pTsStreams )
{
	int i, num = 0;;
	SortPidHistTable( pTSFilter );
	for ( i = 0; i<pTsStreams->total_streams; i++ )
	{
		if ( pTSFilter->pid_hist[i].count )
		{
			pTsStreams->ts_element[num].pid = pTSFilter->pid_hist[i].pid;
			pTsStreams->ts_element[num].type = 0;
			pTsStreams->ts_element[num].content_type = UNIDENTIFIED;
			if ( pTsStreams->pcr_pid == 0 && ( pTSFilter->pid_hist[i].flag & PCR_FLAG ) )
				pTsStreams->pcr_pid = pTSFilter->pid_hist[i].pid;
			num++;
		}
	}
	
	pTsStreams->num_stream = num;
	return num;
}

///////////////////////////////////////////////
// DESC section
DESC_DATA* CreateDesc( )
{
	DESC_DATA* pDesc = SAGETV_MALLOC( sizeof(DESC_DATA) );
	return pDesc;
}
void ReleaseDesc( DESC_DATA* pDesc )
{
	ReleaseDescData( pDesc );
	SAGETV_FREE( pDesc );
}

void EraseDescData( DESC_DATA *pDesc )
{
	if ( pDesc->buffer_size == 0 || pDesc->desc_ptr == NULL )
		return;
	memset( pDesc->desc_ptr, 0, pDesc->buffer_size );
	pDesc->desc_bytes = 0;
}

#ifdef DEBUG_DESC
uint8_t* NewDescData( DESC_DATA* pDesc, int nBytes )
{
	assert( pDesc && nBytes > 0 );
	if ( pDesc->buffer_size < nBytes )
	{
		if (  pDesc->buffer_size > 0 )	
			SAGETV_FREE( pDesc->desc_ptr-4 );
		pDesc->desc_ptr = (uint8_t*)SAGETV_MALLOC( nBytes+4 )+4;
		pDesc->buffer_size = nBytes;
	}
	return pDesc->desc_ptr;
}

uint8_t* FillDescData( DESC_DATA *pDesc, uint8_t* pData, int nBytes )
{
	assert( pDesc && nBytes > 0 );
	if ( pDesc->buffer_size < nBytes )
	{
		if (  pDesc->buffer_size > 0 )	
			SAGETV_FREE( pDesc->desc_ptr-4 );
		pDesc->desc_ptr = (uint8_t*)SAGETV_MALLOC( nBytes+4 )+4;
		pDesc->buffer_size = nBytes;
	}
	memcpy( pDesc->desc_ptr, pData, nBytes );
	pDesc->desc_bytes = nBytes;
	return pDesc->desc_ptr;
}



void ReleaseDescData( DESC_DATA *pDesc )
{
	if ( pDesc->buffer_size == 0 || pDesc->desc_ptr == NULL )
		return;
	SAGETV_FREE( pDesc->desc_ptr-4 );
	pDesc->buffer_size = 0;
	pDesc->desc_bytes = 0;
	pDesc->desc_ptr = NULL;

}
#else
uint8_t* NewDescData( DESC_DATA* pDesc, int nBytes )
{
	assert( pDesc && nBytes > 0 );
	if ( pDesc->buffer_size < nBytes )
	{
		if (  pDesc->buffer_size > 0 )	
			SAGETV_FREE( pDesc->desc_ptr );
		pDesc->desc_ptr = (uint8_t*)SAGETV_MALLOC( nBytes);
		pDesc->buffer_size = nBytes;
	}
	return pDesc->desc_ptr;
}

uint8_t* FillDescData( DESC_DATA *pDesc, uint8_t* pData, int nBytes )
{
	assert( pDesc && nBytes > 0 && nBytes <= 4096 );
	if ( pDesc->buffer_size < nBytes )
	{
		if (  pDesc->buffer_size > 0 )	
			SAGETV_FREE( pDesc->desc_ptr );
		pDesc->desc_ptr = (uint8_t*)SAGETV_MALLOC( nBytes );
		pDesc->buffer_size = nBytes;
	}
	memcpy( pDesc->desc_ptr, pData, nBytes );
	pDesc->desc_bytes = nBytes;
	return pDesc->desc_ptr;
}

uint8_t* AppendDescData( DESC_DATA *pDesc, uint8_t* pData, int nBytes )
{
	assert( pDesc && nBytes > 0 );
	if ( pDesc->buffer_size < pDesc->desc_bytes + nBytes )
	{
		uint8_t* new_buf = (uint8_t*)SAGETV_MALLOC( pDesc->desc_bytes + nBytes );
		memcpy( new_buf, pDesc->desc_ptr, pDesc->desc_bytes );
		if (  pDesc->buffer_size > 0 )	
			SAGETV_FREE( pDesc->desc_ptr );
		pDesc->desc_ptr = new_buf;
		pDesc->buffer_size = pDesc->desc_bytes + nBytes;
	}
	memcpy( pDesc->desc_ptr+pDesc->desc_bytes, pData, nBytes );
	pDesc->desc_bytes = pDesc->desc_bytes + nBytes;
	return pDesc->desc_ptr;
}


void ReleaseDescData( DESC_DATA *pDesc )
{
	if ( pDesc->buffer_size == 0 || pDesc->desc_ptr == NULL )
		return;
	SAGETV_FREE( pDesc->desc_ptr );
	pDesc->buffer_size = 0;
	pDesc->desc_bytes = 0;
	pDesc->desc_ptr = NULL;

}

#endif

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//debug utility
#include <stdio.h>
#include <stdlib.h>
typedef struct {
	uint8_t tag;
	char* name;
} DESC_TAG_NAME;
static DESC_TAG_NAME _tag_name[21]={ 
	{ISO639_LANGUAGE_DESC, "LANGUGAE"}, {DVB_AC3_DESC, "DVB_AC3"},
	{ATSC_AC3_DESC, "ATSC_AC3"},		{MPEG4_AUDIO_DESC,"MPEG4_AUDIO"},
	{DTS_DESC, "DTS"},					{VIDEO_DESC,"VIDEO"},
	{AUDIO_DESC, "AUDIO"},				{TELTEXT_DESC,"TELTEXT"},
	{SUBTITLE_DESC, "SUBTITLE"},		{VBI_DATA_DESC,"VBI"},
	{VBI_TELTEXT	, "VBI_TELX"},		{ATSC_CAPTION_DESC,"CAPTION"},
	{DVB_STREAM_ID,"ID"}, {DVB_BITRATE, "BIT_RATE"}, 
	{DVB_PRIVATE_INDICATOR, "PRIVATE_IND"}, {DVB_USER_DESC, "USER DESC"}, 
	{DVB_PRIVATE_SPEC, "PRIVATE_SPEC"}, {DVB_REFERENCE_ID, "REF ID"},
	{REGISTRATION_DESC, "REG"}, {SCTE_EXTENDED_VIDEO, "DCII Ext Video"}, {0,""} };

static char* _look_up_tag_name_( uint8_t tag )
{
	static char tmp[8];
	int i=0;
	while (	_tag_name[i].tag && _tag_name[i].tag != tag ) i++;
	if ( _tag_name[i].tag )
		return _tag_name[i].name;

	snprintf( tmp, sizeof(tmp), "0x%02x", tag );
	return tmp;
}

static char* _audio_type_( uint8_t type )
{
	if ( type == 1 )
		return "\"clean effects\"";
	if ( type == 2 )
		return "\"hearing impaired\""; 
	if ( type == 3 )
		return "\"visually impaired commentary\"";
	return "";
}

static char* _print_es_descriptor_( uint16_t pid, uint8_t* p, int bytes )
{
	static char buf[1024]={0}; 
	uint8_t tag; 
	int len;
	int i=0, pos;
	if ( bytes == 0 ) return "";
	pos = snprintf( buf, sizeof(buf), "\t\tPid:0x%02x descriptor(%02d) ", pid, bytes );
	while ( i+2 <= bytes ) 
	{
		tag = p[i];
		len = p[i+1];
		if ( tag == REGISTRATION_DESC )
		{
			char tmp[16]={0};
			memcpy( tmp, p+2, _MIN( sizeof(tmp)-1, len) );
			pos += snprintf( buf+pos, sizeof(buf)-pos, "%s(%s) ", _look_up_tag_name_(tag),  tmp );
		} else
			pos += snprintf( buf+pos, sizeof(buf)-pos, "%s(%d) ", _look_up_tag_name_(tag), len );
		i += 2+len;
		if ( pos > sizeof(buf)-1 ) break;

	}
	SageLog(( _LOG_TRACE, 3, TEXT("%s"), buf ));
	return buf;
}

char*  _dvb_service_type_( uint16_t type )
{
	if ( type == 0 ) return "ERROR";
	if ( type == 1 ) return "Digital TV";
	if ( type == 2 ) return "Digital Radio";
	return "other";
}

char*  _atsc_service_type_( uint16_t type )
{
	if ( type == 0 ) return "ERROR";
	if ( type == 2 ) return "Digital TV";
	if ( type == 1 ) return "Analog TV";
	if ( type == 3 ) return "Digital Audio";
	if ( type == 3 ) return "Data service";
	return "other";
}

char*  _data_content_( uint8_t content_type )
{
	if ( content_type == VIDEO_DATA )	 return "video   ";
	if ( content_type == AUDIO_DATA )	 return "audio   ";
	if ( content_type == SUBTITLE_DATA ) return "subtitle";
	if ( content_type == TELETEXT_DATA ) return "teletext";
	if ( content_type == PRIVAITE_DATA ) return "private ";
	if ( content_type == VBI_DATA )		 return "vbi     ";
	if ( content_type == UNIDENTIFIED  ) return "unidentified";
	if ( content_type == UNKNOWN_DATA   ) return "unknown  ";
	return "[unknown]";
}

static void _prints_ts_elmnt( TS_ELEMENT* elmnt )
{
	SageLog(( _LOG_TRACE, 3, TEXT("\t TS:%d type:0x%02x pid:0x%03x %s %s %s %s"), elmnt->channel_index, 
			  elmnt->type, elmnt->pid, 
			  _data_content_(elmnt->content_type), Language( elmnt->language_code, NULL ), 
			  _audio_type_( elmnt->audio_type ),
			  _sagetv_fourcc_( elmnt->format_fourcc, NULL ) ));
}


