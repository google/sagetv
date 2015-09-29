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
#include <string.h>
#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"
#include "ESAnalyzer.h"

void ConsolidateTuneParam( TUNE *pTune, int nStreamFormat, int SubFormat );
static int  UpdateTuneData( TS_PARSER *pTSParser, int nStreamFormat, int nSubFormat, int nData1, int nData2, int nData3, int nData4 );
static int  PickupChannel( TS_PARSER *pTSParser, int nChannel, int nTotalChannel, PMT_DATA* pPmtData, int nCmd );
static int  CheckTsid( TS_PARSER *pTSParser, int nTsid );
static int  CheckOnid( TS_PARSER *pTSParser, int nOnid, int nTsid );
static int  SetupNakeStreamTSFilter( TS_PARSER *pTSParser );
static int  CheckTSParseDone( TS_PARSER *pTSParser );
static void PostStatusMessage( TS_PARSER *pTSParser, char* pMessageText );
static void PtsFix( TS_PARSER *pTSParser, SLOT* pSlot, TRACK* pTrack );
static char* CAInfo( uint16_t CAPid, uint16_t CAID );
static int IsAllChannelsLocked( TS_PARSER *pTSParser );

int time_stamp( LONGLONG llTime, char* pBuffer, int nSize );
int long_long( ULONGLONG llVal, char* pBuffer, int nSize );
char* time_stamp_s( ULONGLONG llTime, char* pBuffer, int nSize );
char* long_long_s( ULONGLONG llVal, char* pBuffer, int nSize );
char* time_stamp_ss( LONGLONG llTime );
char* long_long_ss( ULONGLONG llVal );
//static int _s_(uint8_t*data, int size);

int SetupTSFilter( TS_PARSER *pTSParser, int nSlot, PIDS_TABLE* pPidsTable );
//////////////////////////////////////////// DUMPER Section //////////////////////////////////////////
static int PATDumper( void* pContext, uint8_t* pData, int nSize )
{

	TS_PARSER *pTSParser = (TS_PARSER*)pContext;
	PAT_DATA  *pPatData  = (PAT_DATA*)pData;
	TS_PAT    *pPat      = (TS_PAT*)pPatData->pat_table;
	short pat_index = pPatData->pat_index;
	uint16_t tsid  = pPat->tsid;
	int program_num = pPat->total_program_number;
	int i;

	ASSERT( pContext != NULL );
	ASSERT( nSize == sizeof(PAT_DATA) );

	//dump to the third party dumper
	if ( pTSParser->dumper.pat_dumper )
		pTSParser->dumper.pat_dumper( pTSParser->dumper.pat_dumper_context, pData, nSize );

	if ( pPatData->update_flag == 0 ) //skip unchange PMT
	{
		return 0;
	}

	pTSParser->state |= PAT_PRESENT;
	SageLog(( _LOG_TRACE, 3, TEXT("PAT-%d:Program (total channel:%d, tsid:%d )"), pat_index, program_num, tsid ));
	for ( i = 0; i<program_num; i++ )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("\tPMT pid:0x%04x program:%04d"), pPat->program_pid[i], pPat->program_number[i] ));
	}

	if ( CheckTsid( pTSParser, tsid ) || 
		( pTSParser->current_service_tsid > 0 && pTSParser->current_service_tsid == tsid ) )
	{
		//pTSParser->tsid_tbl[ pTSParser->tsid_num ].locked = 1;
		SageLog(( _LOG_TRACE, 3, TEXT("tsid %d is locked (%d)"), tsid, i));
		return 1;
	}
	else
		return 0;
}

static int PMTDumper( void* pContext, uint8_t* pData, int nSize )
{

	TS_PARSER *pTSParser = (TS_PARSER*)pContext;
	PMT_DATA  *pPmtData  = (PMT_DATA*)pData;
	TS_PMT    *pPmt      = (TS_PMT*)pPmtData->pmt_table;
	//TS_SECTION *pPmtSection = pPmtData->pmt_section;
	int channel = pPmtData->channel;
	int channel_num = pPmtData->total_channel_num;
	int stream_num  = pPmt->total_stream_number;
	int ret, i, tune_cmd = 0;

	ASSERT( pContext != NULL );
	ASSERT( (nSize == sizeof(PMT_DATA)) );

	if ( stream_num == 0 )		return 1;

	pTSParser->pmt_count++;

	//dump to the third party dumper
	if ( pTSParser->dumper.pmt_dumper )
		pTSParser->dumper.pmt_dumper( pTSParser->dumper.pmt_dumper_context, pData, nSize );

	if ( (pPmtData->update_flag & PMT_UPDATED) ) //skip unchanged PMT
	{
		pTSParser->state |= PMT_PRESENT;

		SageLog(( _LOG_TRACE, 3, TEXT("\tChannel:%d (tsid:%d) PMT pid:0x%04x Program:%04d PCR pid:0x%04x %s."),  channel, 
			pPmtData->tsid, pPmtData->pid, pPmt->program_number, pPmt->pcr_pid, pPmt->ca_pid ? CAInfo( pPmt->ca_pid, pPmt->ca_id) :"" ));
		for ( i = 0; i< stream_num; i++ )
		{
			SageLog(( _LOG_TRACE, 3,  TEXT("\t\tType:0x%02x Pid:0x%03x"), pPmt->stream_type[i], pPmt->stream_pid[i] ));
		}

		//PMT updated and repersent, we need reselect channel to change filter
		if ( ( pPmtData->update_flag & PMT_REPEAT ))
		{
			SageLog(( _LOG_TRACE, 3,  TEXT("PMT Updated, reselect channel" ) );
			tune_cmd = 1;
			if ( PickupChannel( pTSParser, channel, channel_num, pPmtData, tune_cmd  ) ) ) 
			{   //ask for reparsing information
				if ( !(pTSParser->state & PARSING_INFO) )
					pTSParser->state |= PARSING_INFO; 
				pTSParser->state |= CHANNEL_LOCKED;
			}
		}
	}

	////if no channel locked and PSI isn't present, force to pickup a channel
	//if ( !(pTSParser->state & (CSI_PRESENT|CHANNEL_LOCKED) ) &&
	//	  (int)pTSParser->pmt_count > channel_num + channel_num/2 &&
	//	  (int)pTSParser->pmt_count > 10  )
	//{
	//	tune_cmd = 2;
	//}

	ret = PickupChannel( pTSParser, channel, channel_num, pPmtData, tune_cmd );
	if ( ret > 0 ) 
	{
		pTSParser->state |= CHANNEL_LOCKED;
		DisablePatPmt(pTSParser);
	}
	return 1;
}

static int ChannelInfoDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_PARSER *pTSParser = (TS_PARSER*)pContext;
	CHANNEL_DATA *channel_data = (CHANNEL_DATA*)pData;
	int i;

	pTSParser->psi_count++;
	//dump to the third party dumper
	if ( pTSParser->dumper.channel_dumper )
		pTSParser->dumper.channel_dumper( pTSParser->dumper.channel_dumper_context, pData, nSize );

	if ( IsAllChannelsLocked( pTSParser ) && channel_data->update_flag == 0 )
		return 0;

	if ( channel_data->stream_format == ATSC_STREAM )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ATSC Channel Info (format:%s total:%d) pmt count:%d, psi:%d" ), 
					StreamFormatString(channel_data->stream_format, channel_data->sub_format), 
					channel_data->num_channel, pTSParser->pmt_count, pTSParser->psi_count )); 

		for ( i = 0; i<channel_data->num_channel; i++ )
		{
			if ( pTSParser->psi_count < 20 )
				SageLog(( _LOG_TRACE, 3,  TEXT("\tchannel (tsid:%d):%d-%d program:%d frq:%d mod:%d service:\"%s\" name:\"%s\" "), 
					channel_data->u.atsc[i].tsid,
					channel_data->u.atsc[i].major_num, channel_data->u.atsc[i].minor_num, channel_data->u.atsc[i].program_id, 
					channel_data->u.atsc[i].u.atsc.freq, channel_data->u.atsc[i].u.atsc.modulation, 
					_atsc_service_type_(channel_data->u.atsc[i].service_type),
					channel_data->u.atsc[i].name ));

			UpdateTuneData( pTSParser, ATSC_STREAM, 0,
				            channel_data->u.atsc[i].major_num, channel_data->u.atsc[i].minor_num, 
				            channel_data->u.atsc[i].tsid, channel_data->u.atsc[i].program_id );

			if ( pTSParser->dumper.channel_dumper == NULL )
				channel_data->command = 0;
				//channel_data->command = (channel_data->update_flag == 1); //save channel info in the PSIFilter, will not reapt send in the same info
		}

	} else
	{
		int local_channel = (channel_data->update_flag == 1);

		if ( !(pTSParser->state & PARSING_INFO) )
			return 0;

		SageLog(( _LOG_TRACE, 4-local_channel, TEXT("DVB Channel Info (format:%s total:%d) (%s)" ), 
			     StreamFormatString(channel_data->stream_format, channel_data->sub_format), channel_data->num_channel, channel_data->update_flag == 1? "*" : "other nit"  ));

		for ( i = 0; i<channel_data->num_channel; i++ )
		{
			char tune_param[64];
			if ( channel_data->u.dvb[i].dvb_type == 1 )
				snprintf( tune_param, sizeof(tune_param), "(T) frq:%d band:%d", channel_data->u.dvb[i].dvb.t.freq, channel_data->u.dvb[i].dvb.t.band );
			else
			if ( channel_data->u.dvb[i].dvb_type == 2 )
				snprintf( tune_param, sizeof(tune_param), "(C) frq:%d mod:%d", channel_data->u.dvb[i].dvb.c.freq, channel_data->u.dvb[i].dvb.c.modulation );
			else
			if ( channel_data->u.dvb[i].dvb_type == 3 )
				snprintf( tune_param, sizeof(tune_param), "(S) frq:%d mod:%d", channel_data->u.dvb[i].dvb.s.freq, channel_data->u.dvb[i].dvb.s.modulation );
			else 
				snprintf( tune_param, sizeof(tune_param), "--" );

			SageLog(( _LOG_TRACE, 4-local_channel,  TEXT("\tchannel %d-%d-%d %s service:\"%s\" ca:%d name:\"%s\" "), 
				channel_data->u.dvb[i].onid, channel_data->u.dvb[i].tsid, channel_data->u.dvb[i].sid, 
				tune_param,	
				_dvb_service_type_( channel_data->u.dvb[i].service_type ),
				channel_data->u.dvb[i].ca, 
				channel_data->u.dvb[i].name ));

			if ( channel_data->update_flag == 1 && channel_data->u.dvb[i].service_type > 0 && channel_data->u.dvb[i].running_status == 4 )
			{
				if ( pTSParser->current_service_tsid && 
					pTSParser->current_service_tsid != channel_data->u.dvb[i].tsid )
					SageLog(( _LOG_TRACE, 3, TEXT("DVB current service is changed (%d -> %d) "),
									pTSParser->current_service_tsid, channel_data->u.dvb[i].tsid )); 
				pTSParser->current_service_tsid = channel_data->u.dvb[i].tsid;
			}

			UpdateTuneData( pTSParser, DVB_STREAM, 0,
				            channel_data->u.dvb[i].onid, channel_data->u.dvb[i].tsid, channel_data->u.dvb[i].sid, 0 );

			if ( pTSParser->dumper.channel_dumper == NULL )
				channel_data->command = 0;
				//channel_data->command = (channel_data->update_flag == 1); //save channel info in the PSIFilter, will not reapt send in the same info
		}
		
	}
	return 0;
}

static int TuneInfoDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_PARSER *pTSParser = (TS_PARSER*)pContext;
	TUNE_DATA *tune_data = (TUNE_DATA*)pData;

	//dump to the third party dumper
	if ( pTSParser->dumper.tune_dumper )
		pTSParser->dumper.tune_dumper( pTSParser->dumper.tune_dumper_context, pData, nSize );

	if ( tune_data->stream_format == ATSC_STREAM )
	{
		//reserve for QAM channel infomation
	} else
	{
		char service_tble[128], tune_param[64];
		int pos;
		service_tble[0] = 0x0;
		
		if ( tune_data->u.dvb.dvb_type == 1 )
			snprintf( tune_param, sizeof(tune_param), "frq:%d band:%d", tune_data->u.dvb.dvb.t.freq, tune_data->u.dvb.dvb.t.band );
		else
		if ( tune_data->u.dvb.dvb_type == 2 )
			snprintf( tune_param, sizeof(tune_param), "frq:%d mod:%d", tune_data->u.dvb.dvb.c.freq, tune_data->u.dvb.dvb.c.modulation );
		else
		if ( tune_data->u.dvb.dvb_type == 3 )
			snprintf( tune_param, sizeof(tune_param), "frq:%d mod:%d", tune_data->u.dvb.dvb.s.freq, tune_data->u.dvb.dvb.s.modulation );
		else 
			snprintf( tune_param, sizeof(tune_param), "tune data not ready" );

		pos = snprintf( service_tble, sizeof(service_tble), "total service:%d ", tune_data->num_tune );
		//pos = 0;
		//for ( i = 0; i<tune_data->num_tune; i++ )
		//{
		//	pos += snprintf( service_tble+pos, sizeof(service_tble)-pos, "%d(%d) ", tune_data->s[i].sid, tune_data->s[i].type );
		//}

		
		SageLog(( _LOG_TRACE, 5, TEXT("DVB NIT inf format:%s (%c) network:%d onid:%d tsid:%d %s name:\"%s\" %s %s"), 
			     StreamFormatString(tune_data->stream_format, tune_data->sub_format), tune_data->update_flag == 1? '*' : 'x',
				 tune_data->u.dvb.nid,
				 tune_data->u.dvb.onid, tune_data->u.dvb.tsid, 
				 tune_param, tune_data->u.dvb.name, service_tble,
				 CheckOnid( pTSParser, tune_data->u.dvb.onid, tune_data->u.dvb.tsid )?"*":"other tune"  ));

		if ( pTSParser->dumper.tune_dumper == NULL )
			tune_data->command = 0;//(tune_data->update_flag == 1);  //0: drop tune in PIS; 1:save tune data in PSI

	}

	return 1;
}

static int MessageDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_PARSER *pTSParser = (TS_PARSER*)pContext;
	MESSAGE_DATA *msg = (MESSAGE_DATA*)pData;
	if ( !strcmp( msg->title, "FORMAT" ) )
	{
		if (!strcmp( (char*)msg->message, "ATSC" ))
		{
			pTSParser->stream_format = ATSC_STREAM;
			pTSParser->stream_subformat = TERRESTRIAL;
			UpdateTuneData( pTSParser, ATSC_STREAM, TERRESTRIAL, 0, 0, 0, 0 );
			if ( pTSParser->media_type == 0 )
				pTSParser->media_type = MEDIA_TYPE_TV;
			//ConsolidateTuneParam( &pTSParser->tune, ATSC_STREAM, TERRESTRIAL );
		} else
		if (!strcmp( (char*)msg->message, "QAM" ))
		{
			pTSParser->stream_format = ATSC_STREAM;
			pTSParser->stream_subformat = CABLE;
			UpdateTuneData( pTSParser, ATSC_STREAM, CABLE, 0, 0, 0, 0 );
			if ( pTSParser->media_type == 0 )
				pTSParser->media_type = MEDIA_TYPE_TV;
			//ConsolidateTuneParam( &pTSParser->tune, ATSC_STREAM, CABLE );
		} else
		if (!strcmp( (char*)msg->message, "DVB-T" ))
		{
			pTSParser->stream_format = DVB_STREAM;
			UpdateTuneData( pTSParser, DVB_STREAM, TERRESTRIAL, 0, 0, 0, 0 );
			if ( pTSParser->media_type == 0 )
				pTSParser->media_type = MEDIA_TYPE_TV;
			//ConsolidateTuneParam( &pTSParser->tune, DVB_STREAM, TERRESTRIAL );
		} else
		if (!strcmp( (char*)msg->message, "DVB-C" ))
		{
			pTSParser->stream_format = DVB_STREAM;
			pTSParser->stream_subformat = CABLE;
			UpdateTuneData( pTSParser, DVB_STREAM, CABLE, 0, 0, 0, 0 );
			if ( pTSParser->media_type == 0 )
				pTSParser->media_type = MEDIA_TYPE_TV;
			//ConsolidateTuneParam( &pTSParser->tune, DVB_STREAM, CABLE );
		} else
		if (!strcmp( (char*)msg->message, "DVB-S" ))
		{
			pTSParser->stream_format = DVB_STREAM;
			pTSParser->stream_subformat = SATELLITE;
			UpdateTuneData( pTSParser, DVB_STREAM, SATELLITE, 0, 0, 0, 0 );
			if ( pTSParser->media_type == 0 )
				pTSParser->media_type = MEDIA_TYPE_TV;
			//ConsolidateTuneParam( &pTSParser->tune, DVB_STREAM, SATELLITE );
		} else
		if (!strcmp( (char*)msg->message, "DVB" ))
		{
			pTSParser->stream_format = DVB_STREAM;
			pTSParser->stream_subformat = 0;
			UpdateTuneData( pTSParser, DVB_STREAM, 0, 0, 0, 0, 0 );
			//ConsolidateTuneParam( &pTSParser->tune, DVB_STREAM, UNKONW_TYPE );
		} 
		SageLog(( _LOG_TRACE, 3, TEXT("Stream Type:%s"), msg->message ));
	} else
	if ( !strcmp( msg->title, "LANGUAGE" ) )
	{
		uint32_t language_code = LanguageCode( msg->message );
		SageLog(( _LOG_TRACE, 3, TEXT("found a new language \"%s\" code=0x%x in DVB PSI."), msg->message, language_code ));
		if ( pTSParser->default_language == 0 )
			pTSParser->default_language = language_code;
		SetLanguage( pTSParser->ts_filter->psi_parser, pTSParser->default_language );
	} 

	pTSParser->dumper.message_dumper( pTSParser->dumper.message_dumper_context, pData, nSize );

	return 1;
}
static int PCRDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_PARSER *pTSParser = (TS_PARSER*)pContext;
	PCR_DATA  *pcr_data = (PCR_DATA *)pData;
	SLOT      *slot     = pcr_data->container;
	int		pts_fixed = 0;

	if ( pTSParser->pts_fix_ctrl == 0 )
	{
		slot->pcr = pcr_data->pcr;
		slot->pcr_cue = pTSParser->used_bytes;

		//dump to the third party dumper
		if ( pTSParser->dumper.pcr_dumper )
		pTSParser->dumper.pcr_dumper( pTSParser->dumper.pcr_dumper_context, pData, nSize );
		return 1;
	}


	if ( slot->pcr_start )
	{
		if (  slot->pcr && pcr_data->pcr )
		{
			LONGLONG delta = pcr_data->pcr - slot->pcr;
			if ( delta < 0 ) delta = -delta;
			if ( delta/300 > (LONGLONG)pTSParser->pts_fix_threshold )
			{	
				ULONGLONG est_pcr, bad_pos;
				LONGLONG  delta1, delta2;
				bad_pos = (slot->pcr - slot->pcr_start)/300;

				est_pcr = slot->pcr + (pTSParser->used_bytes - slot->pcr_cue) * slot->pcr_rate/16384; 
				delta1 = (LONGLONG)(pcr_data->pcr - slot->pcr);
				delta2 = (LONGLONG)(pcr_data->pcr - est_pcr);
				if ( delta2 > 0 )
					delta = delta2 > delta1 ? delta1 : delta2;
				else
					delta = delta2 > delta1 ? delta2 : delta1;
				slot->pcr_start += delta;

				{  char tmp1[40], tmp2[40], tmp3[40], tmp4[40], tmp5[40];
				   SageLog(( _LOG_TRACE, 3, TEXT("PTS-FIX: PCR is out of range at %s pos:%s, PCR is adjusted (%s).[%s->%s]."), 
					       time_stamp_s( bad_pos, tmp1, 40 ), long_long_s( slot->pcr_cue, tmp2, 40 ), 
						   time_stamp_s( delta/300, tmp3, 40 ),  time_stamp_s( slot->pcr/300, tmp4, 40 ), 
						   time_stamp_s( pcr_data->pcr/300, tmp5, 40 ) ));
				   pts_fixed = 1;
				}

			}
		}
	} else
	{
		slot->pcr_start = pcr_data->pcr;
	}
	
	if (  pcr_data->pcr > slot->pcr )
	{
		if ( pTSParser->used_bytes > slot->pcr_cue  )
			slot->pcr_rate = (uint32_t)((pTSParser->used_bytes-slot->pcr_cue)*16384/(pcr_data->pcr-slot->pcr));

	}
	slot->pcr = pcr_data->pcr;
	slot->pcr_cue = pTSParser->used_bytes;

	if ( pcr_data->pcr > slot->pcr_start )
	{
		PTSLog(( 1, (pcr_data->pcr - slot->pcr_start)/300, 0, pTSParser->used_bytes, 0 ) ); 
	}

	//dump to the third party dumper
	if ( pTSParser->dumper.pcr_dumper )
	{
		pcr_data->pcr =  (pcr_data->pcr - slot->pcr_start);
		pTSParser->dumper.pcr_dumper( pTSParser->dumper.pcr_dumper_context, pData, nSize );
	}

	return 1;
}

static inline void UpdateGetCurrentPCRCue( TS_PARSER *pTSParser, SLOT *pSlot, TRACK* pTrack )
{
	ASSERT( pTSParser->used_bytes >= pSlot->pcr_cue );
    pTrack->cue = pSlot->pcr + (pTSParser->used_bytes - pSlot->pcr_cue) * pSlot->pcr_rate/16384; 

	if ( pTSParser->pts_fix_ctrl )
	{
		if ( pTrack->cue < pSlot->pcr_start && pSlot->pcr_start >= 0 
			  && (pSlot->pcr - pSlot->pcr_start)/300 < pTSParser->pts_fix_threshold )
		{
			pTrack->cue = 0;
		} else
			pTrack->cue -= pSlot->pcr_start;
			
	}
}

inline void FillESBlock( TRACK *pTrack, uint8_t* pData, int nBytes )
{
	uint8_t* out_ptr  = pTrack->es_data_start + pTrack->es_data_bytes;
	if ( nBytes < 0 ) 
	{
		SageLog(( _LOG_TRACE, 1, TEXT("ERROR !Invaild filling block data size %d (bad data), "),  nBytes ));
		return;
	}
	if ( nBytes == 0 )
		return;

	//ZQ...
	//if ( pTrack->es_data_bytes == 0 )  
	//	pTrack->start_cue = pTrack->cue;

	if ( pTrack->es_data_bytes+nBytes <= pTrack->es_block_size )
	{
		memcpy( out_ptr, pData, nBytes );
		pTrack->es_data_bytes += nBytes;
	} else
	{
		SageLog(( _LOG_TRACE, 1, TEXT("ERROR ES block buffer is too small to hold an ES block! (data size:%d, block size:%d)"),  
			      pTrack->es_data_bytes+nBytes, pTrack->es_block_size ));
		//ASSERT( 0 );
	}
}

static inline void DumpESBlockOfTS( TS_PARSER *pTSParser, TRACK* pTrack )
{
	if (  pTrack->command == STOP_FILL_DATA )
		return;
	if ( pTrack->es_data_bytes == 0 )
		return;

	pTrack->command = DATA_READY;
	pTrack->es_blocks_counter++;
	pTrack->processed_bytes += pTrack->es_data_bytes;

//if ( pTrack->es_elmnt->content_type == 3 /*&& pTrack->channel_index == 1*/ )
// SageLog(( _LOG_TRACE, 1, TEXT(">>>>>>>>>>>>>>>> sub: data size:%d "),   pTrack->es_data_bytes));
	pTSParser->dumper.esblock_dumper( pTSParser->dumper.esblock_dumper_context, pTrack, sizeof( TRACK ) );
	ASSERT( pTrack->command == START_FILL_DATA || pTrack->command == STOP_FILL_DATA );
	
	if ( pTrack->command == START_FILL_DATA )
	{
		pTrack->command = REQUEST_BUFFER;
		pTSParser->dumper.esblock_dumper( pTSParser->dumper.esblock_dumper_context, pTrack, sizeof( TRACK ) );
		ASSERT( pTrack->es_data_bytes == 0 );
	} else
	{
		pTrack->es_data_bytes = 0;
	}
}

static inline int ParserPES( PES *pPES, TRACK *pTrack, uint8_t* pData, int nBytes, int bGroupStart )
{
	int  ret = 0;
	int  overflow_bytes;
	if ( pTrack->es_elmnt->pes.packet_length == 0 )
		overflow_bytes = 0;
	else
		overflow_bytes = (pTrack->es_pes_bytes+nBytes)-(pTrack->es_elmnt->pes.packet_length+6);

	if ( pTrack->es_elmnt->pes.packet_length == 0 || bGroupStart  )
	{   
		if ( IsPESHeaderInTs( pData, nBytes ) && //start a new es block
			 ReadPESHeader( pData, nBytes, pPES ) > 0 )
		{
			pTrack->es_elmnt->pes = *pPES;
			ret = 1;
			pTrack->es_pes_bytes = 0;
		} 
	} else
	if ( overflow_bytes > 0 )  //data not close at a TS packet (start group)
	{
		//fill in reset of data
		pTrack->es_pes_bytes += nBytes - overflow_bytes;
		pData   +=  nBytes - overflow_bytes;
		nBytes =  overflow_bytes;

		if ( IsPESHeaderInTs( pData, nBytes ) && //start a new es block
			 ReadPESHeader( pData, nBytes, pPES ) > 0 )
		{
			pTrack->es_elmnt->pes = *pPES;
			ret = 1;
		}
		pTrack->es_pes_bytes = 0;
		 
	}

	pTrack->es_pes_bytes += nBytes;

	return ret;

}

static int AudioTSPriorityHack( TRACK *pTrack, int TsPriority )
{
	//if ( track->av_elmnt != NULL && track->av_elmnt->format_fourcc )
	if ( pTrack->ts_elmnt->format_fourcc )
	{
		if ( pTrack->ts_elmnt->format_fourcc == SAGE_FOURCC( "DTSH" ) )
		{
			return TsPriority == 0;
		} else
		if ( pTrack->ts_elmnt->format_fourcc == SAGE_FOURCC( "DTSM" ) )
		{
			return TsPriority == 0;
		} else
		if ( pTrack->ts_elmnt->format_fourcc == SAGE_FOURCC( "AC3T" ) )
		{
			return TsPriority == 0;
		} 
	}

	return 0;
}

static int StreamDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_PARSER     *pTSParser     = (TS_PARSER*)pContext;
	STREAM_DATA   *stream_data   = (STREAM_DATA *)pData;
	TS_ELEMENT    *ts_elment     = stream_data->ts_elment;
	SLOT          *slot          = stream_data->container;
	TRACK		  *track;
	int			  slot_index;
	int			  in_bytes;
	uint8_t *in_ptr;
	int			   media_type; 
	ASSERT( ts_elment->channel_index < slot->tracks->total_track );

	slot_index = slot->tracks->slot_index;
	track = &slot->tracks->track[ts_elment->channel_index];
	track->ts_packets_counter++;

	//get media type to aide work out av format
	if ( pTSParser->media_type == MEDIA_TYPE_TV )
		media_type = SAGETV_TV_RECORDING;
	else
	if ( pTSParser->media_type == MEDIA_TYPE_DVD )
	{
		if ( pTSParser->packet_length == M2TS_PACKET_LENGTH )
			media_type = BLUERAY_DVD_MEDIA;
		else
			media_type = VOB_DVD_MEDIA;
	} else
		media_type = UNKNOWN_MEDIA;

	if ( track->scrambling_flag != (stream_data->scrambling>0) )
	{
		SageLog(( _LOG_TRACE, 1, TEXT("\tStream is %s (%d)(slot:%d pid:0x%x)"), stream_data->scrambling? "encrypted": "clean", 
				stream_data->scrambling, slot_index, ts_elment->pid ));
		track->scrambling_flag = stream_data->scrambling>0;
	}

	//processing PES only (mainly for fast extracting PTS 
	if ( pTSParser->dumper.pes_dumper )
	{
		ASSERT( stream_data->bytes <= 188+4 ); 
		track->es_elmnt->pes.packet_length = 0;
		if ( ParserPES( &track->es_elmnt->pes, track, stream_data->data_ptr, stream_data->bytes, stream_data->group_start ) )
			pTSParser->dumper.pes_dumper( pTSParser->dumper.pes_dumper_context, track, sizeof(TRACK) );
		return 0;
	}
	
	UpdateGetCurrentPCRCue( pTSParser, slot, track );

	if ( pTSParser->dumper.esblock_dumper == NULL )
		return 0;

	if ( track->command == STOP_FILL_DATA )
		return 0;

	//setup TRACK from first requesting buffer.
	if ( track->buffer_size == 0 )
	{
		track->command = REQUEST_BUFFER;
		pTSParser->dumper.esblock_dumper( pTSParser->dumper.esblock_dumper_context, track, sizeof( TRACK ) );
		pTSParser->state |= PUMPOUT_DATA;
	}

	if ( track->command != START_FILL_DATA )
		return 0;
	
	//Do TrueHD, DTS HD, DTS hack, drop packets
	if ( pTSParser->audio_ts_priority_hack && ts_elment->content_type == AUDIO_DATA &&
		  media_type != SAGETV_TV_RECORDING )
	{
		if ( AudioTSPriorityHack( track, stream_data->priority ) )
			return 0;
	}

	//drop trueHD AC3 code
	if ( !pTSParser->audio_ts_priority_hack && media_type != SAGETV_TV_RECORDING  &&
		 track->av_elmnt->format_fourcc  == SAGE_FOURCC( "AC3T" ) )
	{
		if ( stream_data->priority != 0 )
			return 0;
	} 

	//fill data to send into dumper
	in_bytes = stream_data->bytes;
	in_ptr   = stream_data->data_ptr;

	if ( track->command == START_FILL_DATA )
	{
		int  overflow_bytes;
		if ( track->es_elmnt->pes.packet_length == 0 )
			overflow_bytes = 0;
		else
			overflow_bytes = (track->es_data_bytes+in_bytes)-(track->es_elmnt->pes.packet_length+6);

		if ( track->es_elmnt->pes.packet_length == 0 || stream_data->group_start )
		{   
			PES pes={0};
			if ( IsPESHeaderOfTS( (uint8_t)track->es_elmnt->stream_id, in_ptr, in_bytes ) && //start a new es block
				 ReadPESHeader( in_ptr, in_bytes, &pes ) > 0 )
			{

				if ( track->es_data_bytes )
				{
					if ( (slot->state & PARSING_INFO) && 
						 track->av_elmnt->format_fourcc == 0 &&
						 AnylyzeAVElement( track->av_elmnt, track->es_elmnt, track->ts_elmnt, 
										   track->es_data_start, track->es_data_bytes, media_type ) )    
					{
						track->es_elmnt->stream_id = pes.stream_id;
						_prints_av_elmnt( track->av_elmnt, slot_index, track->processed_bytes );
						CheckTSParseDone( pTSParser );
					
					}

					DumpESBlockOfTS( pTSParser, track );  //dump out
				}
				track->es_elmnt->pes = pes;

				track->group_start = ES_GROUP_START | stream_data->group_start;
				PtsFix( pTSParser, slot, track );
				if ( track->es_elmnt->pes.has_pts || track->es_elmnt->pes.has_dts )
				{
					PTSLog(( 3, track->es_elmnt->pes.pts, track->es_elmnt->pes.dts, pTSParser->used_bytes, track->channel_index ) ); 
				}
				if ( track->es_elmnt->content_type == 0 && AnylyzeTSESElement( track->es_elmnt ) )
				{
					if ( track->ts_elmnt->content_type == UNIDENTIFIED && track->es_elmnt->content_type  )
						track->ts_elmnt->content_type  = track->es_elmnt->content_type;
					_prints_es_elmnt( track->es_elmnt, slot_index, track->scrambling_flag );
				} 
				if ( track->es_elmnt->content_type == 0 )
					return 0; //not getting a valid PES information, give up data

			} 
		} 

		if ( overflow_bytes > 0 )  //data not close at a TS packet (start group)
		{
			//fill in reset of data
			FillESBlock( track, in_ptr, in_bytes - overflow_bytes );

			if ( (slot->state & PARSING_INFO) && 
				  track->av_elmnt->format_fourcc == 0 &&
				  AnylyzeAVElement( track->av_elmnt, track->es_elmnt, track->ts_elmnt, 
									track->es_data_start, track->es_data_bytes, media_type ) )    
			{
				track->es_elmnt->stream_id = track->es_elmnt->pes.stream_id;
				_prints_av_elmnt( track->av_elmnt, slot_index, track->processed_bytes );
				CheckTSParseDone( pTSParser );
			}

			DumpESBlockOfTS( pTSParser, track );  //dump out

			track->group_start = stream_data->group_start;
			track->es_elmnt->pes.has_pts = track->es_elmnt->pes.has_dts = 0;
			track->es_elmnt->pes.packet_length = 0;

			in_ptr   +=  in_bytes - overflow_bytes;
			in_bytes =  overflow_bytes;

			if ( IsPESHeaderOfTS( (uint8_t)track->es_elmnt->stream_id, in_ptr, in_bytes ) && //start a new es block
				 ReadPESHeader( in_ptr, in_bytes, &track->es_elmnt->pes ) > 0 )
			{
				PtsFix( pTSParser, slot, track );
				if ( track->es_elmnt->pes.has_pts || track->es_elmnt->pes.has_dts )
				{ 
					PTSLog(( 3, track->es_elmnt->pes.pts, track->es_elmnt->pes.dts, pTSParser->used_bytes, track->channel_index ) ); 
				}

				track->group_start = ES_GROUP_START | stream_data->group_start;
			} else
			{
				pTSParser->bad_blocks++;
				in_ptr += in_bytes; //drop data till PES start;
				in_bytes = 0;
				CleanUpPES( &track->es_elmnt->pes );
			}
		}
		//fill in data
		if ( track->es_elmnt->pes.stream_id  )
		{
			overflow_bytes = ( in_bytes + track->es_data_bytes ) - track->es_block_size ;

			//not buffer subtitle stream, send out immediately. ZQ
			if ( ts_elment->content_type == SUBTITLE_DATA )
			{
				overflow_bytes = 0;
				stream_data->group_start = 0;
			}

			if ( overflow_bytes < 0 )
				FillESBlock( track, in_ptr, in_bytes );
			else
			{
				FillESBlock( track, in_ptr, in_bytes - overflow_bytes );

				if ( (slot->state & PARSING_INFO) && 
					 track->av_elmnt->format_fourcc == 0 &&
					 AnylyzeAVElement( track->av_elmnt, track->es_elmnt, track->ts_elmnt, 
									   track->es_data_start, track->es_data_bytes, media_type ) )    
				{
					track->es_elmnt->stream_id = track->es_elmnt->pes.stream_id;
					_prints_av_elmnt( track->av_elmnt, slot_index, pTSParser->input_packets*188 );
					CheckTSParseDone( pTSParser );
				}
				
				DumpESBlockOfTS( pTSParser, track );  //dump out

				track->group_start = stream_data->group_start;
				track->es_elmnt->pes.has_pts = track->es_elmnt->pes.has_dts = 0;
				track->es_elmnt->pes.packet_length = 0;

				in_ptr   +=  in_bytes - overflow_bytes;
				in_bytes = overflow_bytes;

				FillESBlock( track, in_ptr, in_bytes );

			}

			//missing PES head in crossing TS packets
			if ( (track->group_start & ES_GROUP_START) == 0x0 && 
				  track->es_data_bytes > 9 && track->es_data_bytes < 16 )
			{
				if ( IsPESHeaderOfTS( (uint8_t)track->es_elmnt->stream_id, track->es_data_start, track->es_data_bytes ) && //start a new es block
					 ReadPESHeader( track->es_data_start, track->es_data_bytes, &track->es_elmnt->pes ) > 0 )
				{
					PtsFix( pTSParser, slot, track );
					if ( track->es_elmnt->pes.has_pts || track->es_elmnt->pes.has_dts )
					{ 
						PTSLog(( 3, track->es_elmnt->pes.pts, track->es_elmnt->pes.dts, pTSParser->used_bytes, track->channel_index ) ); 
					}

					track->group_start |= ES_GROUP_START;
				} else
				{
					CleanUpPES( &track->es_elmnt->pes );
				}
			}
		}
	}

	return 1;
}


char* UTCFormat( uint32_t t, char* p, int len );
static int SystemTimeDumper( void* pContext, uint8_t* pData, int nSize )
{

	TS_PARSER *pTSParser = (TS_PARSER*)pContext;
	TIME_DATA *time_data = (TIME_DATA*)pData;
	uint32_t utc_sec;

	if ( time_data->gps_sec )
		utc_sec = time_data->gps_sec + 315993600;  //GPS start 1/6 0:0:0 1980, GMT UTC start 1/1 0:0:0 1970
	else
		utc_sec = time_data->utc_sec;


	if ( utc_sec > pTSParser->psi_time + 60  )
	{
		char time_dsp[32];
		UTCFormat( utc_sec, time_dsp, sizeof(time_dsp) );
		SageLog(( _LOG_TRACE, 3,  TEXT("stream clock (GMT):%s"), time_dsp  )); 

		//{ //gps time convert ZQ.
		//	time_t t = time(0);
		//	struct tm* data;
		//	time_t a, b;
		//	int gmtoffset;
		//	data = localtime(&t);
		//	data->tm_isdst = 0;
		//	a = mktime(data);
		//	data = gmtime(&t);
		//	data->tm_isdst = 0;
		//	b = mktime(data);
		//	gmtoffset = (int)(a - b)/3600;
		//	utc_sec += 3600*gmtoffset;
		//	SageLog(( _LOG_TRACE, 3,  TEXT("stream clock(local):%s"), ctime( &utc_sec)  )); 
		//}
	}

	pTSParser->psi_time = utc_sec;
	if ( pTSParser->psi_time_start == 0 ) 
		pTSParser->psi_time_start = utc_sec;

	return 1;
}

void UpdateTSParserClock( TS_PARSER *pTSParser, uint32_t lClock )
{
	if ( pTSParser->clock_time_start == 0 )
		pTSParser->clock_time_start = lClock;

	pTSParser->clock_time = lClock;
}

static uint32_t ParsingElapse( TS_PARSER *pTSParser )
{
	uint32_t psi_dt, clock_dt;
	if ( pTSParser->psi_time > pTSParser->psi_time_start )
		psi_dt   = pTSParser->psi_time - pTSParser->psi_time_start;
	else
		psi_dt = 0;
	clock_dt = (pTSParser->clock_time - pTSParser->clock_time_start)/1000;
	return psi_dt > clock_dt ? psi_dt : clock_dt;
}

static int PostProgramData( TS_PARSER *pTSParser, TS_STREAMS* pStreams, PMT_DATA * pPmtData, TUNE* pTune )
{
	int i, ret=0;
	if ( pTSParser->dumper.program_dumper != NULL )
	{
		int pid_num;
		PROGRAM_DATA program_data={0};
		program_data.pids_table.tsid    = pPmtData->tsid;
		program_data.pids_table.sid     = pPmtData->pmt_table->program_number;
		program_data.pids_table.pcr_pid = pPmtData->pmt_table->pcr_pid;
		program_data.pids_table.ca_pid  = pPmtData->pmt_table->ca_pid;
		program_data.pmt_section_data   = pPmtData->pmt_section->data;
		program_data.pmt_section_bytes  = pPmtData->pmt_section->total_bytes;

		program_data.pids_table.tune_id = pTune->tune_id;

		pid_num = _MIN( pStreams->num_stream, MAX_USER_PID_NUM );
		program_data.pids_table.pid_num = pid_num; 
		for ( i = 0; i<pid_num; i++ )
		{
			program_data.pids_table.pid_tbl[i].pid = pStreams->ts_element[i].pid;
			program_data.pids_table.pid_tbl[i].type = pStreams->ts_element[i].type;
		}
		SageLog(( _LOG_TRACE, 3, TEXT("TS Dump program Data  pmt bytes:%d pmt data:0x%x (%d)."), program_data.pmt_section_bytes, 
			                           program_data.pmt_section_data, sizeof(PROGRAM_DATA) ));
		ret = pTSParser->dumper.program_dumper( pTSParser->dumper.program_dumper_context, &program_data, sizeof(PROGRAM_DATA) );

	}
	return ret;
}

////////////////////////////////PUSH SECTION //////////////////////////////////
int PushDataTSParser( TS_PARSER *pTSParser, uint8_t* pData, int nSize )//, int *pExpectedBytes )
{
	int used_bytes = 0, size = nSize, start_offset;
	uint8_t* data = pData;

	pTSParser->block_count++;
	
	if ( pTSParser->block_count == 1 )
	{
		PostStatusMessage( pTSParser, "STREAM START" );
	}

	if ( pTSParser->status == PARSER_STOP )
	{
		pTSParser->status = PARSER_RUNNING;
		PostStatusMessage( pTSParser, "PARSING STREAM" );
	}
	if ( pTSParser->status == PARSER_RUNNING )
	{
		//if there is no PAT PMT aavaliable in stream (naked stream), we use PIDs histogram information to setup TSFilter
		if ( !(pTSParser->state & (PAT_PRESENT|PMT_PRESENT|TS_FILTER_READY) ) && 
			  ( pTSParser->rebuild_stream_ctrl && pTSParser->input_packets > pTSParser->naked_stream_threshold ) )  
		{
			if (  SetupNakeStreamTSFilter( pTSParser ) > 0 )
				pTSParser->state |= TS_FILTER_READY;
		}

		if ( (pTSParser->state & PARSING_INFO) && 
			 //(pTSParser->state & PUMPOUT_DATA )&&
			 (pTSParser->used_bytes > pTSParser->naked_stream_threshold ) )
			if ( (pTSParser->block_count & 0x000f ) == 0 ) //every 16 block to check onece
			{
				CheckTSParseDone( pTSParser );
			}
	}

	start_offset = pTSParser->packet_length-TS_PACKET_LENGTH;
	while ( size >= pTSParser->packet_length )
	{
		int ret;
		
		if ( pTSParser->command & (TS_PARSER_CMD_ABORT|TS_PARSER_CMD_ZERO|TS_PARSER_CMD_RESET ) )
			break;

		if ( *data != TS_SYNC ) //sync header
		{
			pTSParser->bad_packets++;
			for (  ; size>0 && *(data+start_offset) != TS_SYNC ; size--, data++ ) 
			{	
				pTSParser->used_bytes++;
				used_bytes++; 
			}
			if ( size < pTSParser->packet_length )
			{
				SageLog(( _LOG_ERROR, 3, TEXT("ERROR: TS SYNC header is lost!") ));
				break;
			}
		}

		ret = TSProcess( pTSParser->ts_filter, data+start_offset  ); //must to be 188 bytes of data

		if ( ret >= 0 )
			pTSParser->valid_pcakets++;
		pTSParser->input_packets++;

		data += pTSParser->packet_length;
		size -= pTSParser->packet_length;
		used_bytes += pTSParser->packet_length;
		pTSParser->used_bytes += pTSParser->packet_length;
	}

	pTSParser->used_bytes += used_bytes;

	if ( pTSParser->command & TS_PARSER_CMD_ABORT )
	{
		pTSParser->command &= ~TS_PARSER_CMD_ABORT; 
		used_bytes = nSize;
	}

	if ( pTSParser->command & TS_PARSER_CMD_ZERO  )
	{
		TSParserZero( pTSParser );
		pTSParser->command &= ~TS_PARSER_CMD_ZERO; 
		used_bytes = nSize;
	}

	if ( pTSParser->command & TS_PARSER_CMD_RESET  )
	{
		ResetTSParser( pTSParser );
		pTSParser->command &= ~TS_PARSER_CMD_RESET ; 
		used_bytes = nSize;
	}

	return used_bytes;
}

//////////////////////////////// API SECTION ///////////////////////////////////

TS_PARSER* CreateTSParser( int nStreamType )
{
	int i;
	TS_PARSER *pTSParser = SAGETV_MALLOC( sizeof(TS_PARSER) );
	pTSParser->ts_filter = CreateTSFilter( DEFAULT_PAT_NUM, DEFAULT_PMT_NUM, 0, 0 ); //unknow format, give it 0

	pTSParser->ts_filter->dumper.pat_dumper = (DUMP)PATDumper;
	pTSParser->ts_filter->dumper.pat_dumper_context = pTSParser;
	pTSParser->ts_filter->dumper.pmt_dumper = (DUMP)PMTDumper;
	pTSParser->ts_filter->dumper.pmt_dumper_context = pTSParser;
	pTSParser->ts_filter->dumper.pcr_dumper = (DUMP)PCRDumper;
	pTSParser->ts_filter->dumper.pcr_dumper_context = pTSParser;
	pTSParser->ts_filter->dumper.stream_dumper = (DUMP)StreamDumper;
	pTSParser->ts_filter->dumper.stream_dumper_context = pTSParser;

	pTSParser->ts_filter->psi_parser->dumper.channel_info_dumper  =	(DUMP)ChannelInfoDumper;
	pTSParser->ts_filter->psi_parser->dumper.channel_info_context =	pTSParser;
	pTSParser->ts_filter->psi_parser->dumper.system_time_dumper   =	(DUMP)SystemTimeDumper;
	pTSParser->ts_filter->psi_parser->dumper.system_time_context  =	pTSParser;
	pTSParser->ts_filter->psi_parser->dumper.tune_info_dumper     = (DUMP)TuneInfoDumper;
	pTSParser->ts_filter->psi_parser->dumper.tune_info_context    = pTSParser;
	pTSParser->ts_filter->psi_parser->dumper.message_dumper       = (DUMP)MessageDumper;
	pTSParser->ts_filter->psi_parser->dumper.message_context      = pTSParser;

	pTSParser->psi_time = 0;
	pTSParser->psi_time_start = 0;
	pTSParser->clock_time = 0;
	pTSParser->clock_time_start = 0;
	if ( nStreamType == MPEG_ASI )
		pTSParser->packet_length = ASI_PACKET_LENGTH;
	else
	if ( nStreamType == MPEG_M2TS )
		pTSParser->packet_length = M2TS_PACKET_LENGTH;
	else
		pTSParser->packet_length = TS_PACKET_LENGTH;

	if ( nStreamType == MPEG_M2TS )
		pTSParser->media_type = MEDIA_TYPE_DVD;

	pTSParser->epg_ctrl = 1;
	//pTSParser->channel_locked = 0; 
	for ( i=0; i<MAX_SLOT_NUM; i++ )
	{
		pTSParser->slot[i].tracks_id = i;
		pTSParser->slot[i].state = 0;
	}

	pTSParser->audio_ts_priority_hack = 0;
	pTSParser->wait_clean_stream = 0;
	pTSParser->subtitle_ctrl = 1;
	pTSParser->pts_fix_ctrl = 1;
	pTSParser->pts_fix_threshold = MILLSECOND2PTS(PTS_FIX_THRESHOLD); 
	pTSParser->naked_stream_threshold = NAKED_STREAM_THRESHOLD;
	pTSParser->rebuild_stream_ctrl = 1;
    //pTSParser->naked_stream_threshold = 0; //disable naked stream
	pTSParser->empty_sub_stream_threshold = EMPTY_SUB_STREAM_THRESHOLD;
	pTSParser->failed_channel_threshold = FAILED_CHANNEL_THRESHOLD;
	pTSParser->max_stream_check_time = MAX_STREAM_CHECK_TIME; //5 seconds
	//pTSParser->state = 0;
	//pTSParser->fast_filter_ctrl = 0;
	pTSParser->status = 0;
	pTSParser->state = PARSING_INFO;
#if defined(MINI_PVR)
	SageLog(( _LOG_TRACE, 3, TEXT("TS Parser is created  version 2.0.3 Mini-PVR .") ));
#else	
	SageLog(( _LOG_TRACE, 3, TEXT("TS Parser is created  version 2.0.4  .") ));
#endif	
	SageLog(( _LOG_TRACE, 3, TEXT("\tMaxium PAT number:%d PMT number:%d Packet length:%d"), 
				DEFAULT_PAT_NUM, DEFAULT_PMT_NUM, pTSParser->packet_length ));
	SageLog(( _LOG_TRACE, 3, TEXT("\tPTSFIX threshold:%s"), _time_stamp( pTSParser->pts_fix_threshold , NULL, 0 ) ));
	return pTSParser;
}

void ReleaseTSParser( TS_PARSER *pTSParser )
{
	int i;
	PostStatusMessage( pTSParser, "PARSER STOP" );
	ReleaseTSFilter( pTSParser->ts_filter );
	for ( i = 0; i<MAX_SLOT_NUM; i++ )
		CloseTSChannel( pTSParser, i );
	SAGETV_FREE( pTSParser );
	SageLog(( _LOG_TRACE, 3, TEXT("TS Parser is released  .") ));
}

void SetupTSStreamType( TS_PARSER *pTSParser, int nStreamType )
{
	uint16_t org_packet_length = pTSParser->packet_length;
	if ( nStreamType == MPEG_ASI )
		pTSParser->packet_length = ASI_PACKET_LENGTH;
	else
	if ( nStreamType == MPEG_M2TS )
		pTSParser->packet_length = M2TS_PACKET_LENGTH;
	else
		pTSParser->packet_length = TS_PACKET_LENGTH;

	SageLog(( _LOG_TRACE, 3, TEXT("TS Parser change streamType to %d, packet length change to %d from %d."),
		  nStreamType, pTSParser->packet_length, org_packet_length ));
}

void ResetTSParser( TS_PARSER *pTSParser )
{
	int i;
	pTSParser->status = 0;
	pTSParser->state = PARSING_INFO;
	pTSParser->used_bytes = 0;
	pTSParser->bad_packets = 0;
	pTSParser->bad_blocks = 0;
	pTSParser->input_packets = 0;
	pTSParser->valid_pcakets = 0;
	pTSParser->block_count = 0;
	pTSParser->pmt_count = 0;
	pTSParser->psi_count = 0;

	pTSParser->tsid_num = 0;
	pTSParser->current_service_tsid = 0;
	pTSParser->psi_time = 0;
	pTSParser->psi_time_start = 0;
	pTSParser->clock_time = 0;
	pTSParser->clock_time_start = 0;
	pTSParser->command = 0;
	//pTSParser->packet_length = TS_PACKET_LENGTH;

	for ( i = 0; i<MAX_SLOT_NUM; i++ )
		ResetTSChannel( pTSParser, i );

	ResetTSFilter( pTSParser->ts_filter );
	SageLog(( _LOG_TRACE, 3, TEXT("TS Parser Reset") ));
}

void ResetTSParserState( TS_PARSER *pTSParser )
{
	pTSParser->state = PARSING_INFO;
}

int OpenTSChannel( TS_PARSER *pTSParser, int nSlot, TRACKS* pTracks, TUNE *pTune  )
{
	int i;
	SLOT  *slot;
	if ( nSlot >= MAX_SLOT_NUM ) 
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: slot %d isn't avaliable, max slot:%d" ), nSlot, MAX_SLOT_NUM ));
		return 0;
	}

	ASSERT ( pTSParser->status == PARSER_STOP );

	if ( pTSParser->slot[nSlot].state != 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: slot %d is running" ), nSlot ));
		return 0;
	}

	slot = &pTSParser->slot[nSlot];

	if ( pTracks->total_track > slot->ts_streams.total_streams )
	{
		
		if ( slot->ts_streams.ts_element )
			SAGETV_FREE( slot->ts_streams.ts_element );
		if ( slot->es_streams.es_element )
			SAGETV_FREE( slot->es_streams.es_element );
		if ( slot->av_streams.av_element )
		{
			ReleaseAVElementData( slot->av_streams.av_element );
			SAGETV_FREE( slot->av_streams.av_element );
		}

		slot->ts_streams.total_streams =pTracks->total_track;
		slot->ts_streams.num_stream = 0;
		slot->ts_streams.ts_element = SAGETV_MALLOC( pTracks->total_track*sizeof(TS_ELEMENT) );
		slot->es_streams.total_streams = pTracks->total_track;
		slot->es_streams.num_stream = 0;
		slot->es_streams.es_element = SAGETV_MALLOC( pTracks->total_track*sizeof(ES_ELEMENT) );
		slot->av_streams.total_streams = pTracks->total_track;
		slot->av_streams.num_stream = 0;
		slot->av_streams.av_element = SAGETV_MALLOC( pTracks->total_track*sizeof(AV_ELEMENT) );
		for ( i = 0; i < pTracks->total_track; i++ )
		{
			slot->ts_streams.ts_element[i].channel_index = i;
			slot->es_streams.es_element[i].channel_index = i;
			slot->av_streams.av_element[i].channel_index = i;
			pTracks->track[i].ts_elmnt = &slot->ts_streams.ts_element[i];
			pTracks->track[i].es_elmnt = &slot->es_streams.es_element[i];
			pTracks->track[i].av_elmnt = &slot->av_streams.av_element[i];
		}
	}

	slot->state |= PARSING_INFO;
	slot->ts_streams.container = slot;
	slot->tracks = pTracks;
	slot->tune = *pTune;
	slot->tracks->slot_index = nSlot;
	slot->tracks->main_video_index = 0xffff;
	slot->tracks->main_audio_index = 0xffff;

	for ( i = 0; i < pTracks->total_track; i++ )
		slot->tracks->track[i].slot_index = nSlot;
	
	pTSParser->slot_num++;
	SageLog(( _LOG_TRACE, 3, TEXT("Open slot %d." ), nSlot ));

	if ( pTSParser->media_type == 0 )
	{
		if ( pTune->stream_format == ATSC_STREAM || pTune->sub_format != 0 )
			pTSParser->media_type = MEDIA_TYPE_TV;
	}

	if ( pTune->stream_format == FREE_STREAM && 
		pTune->tune_string_type == USER_SET_PID && pTune->pids_table.pid_num  )
	{
		SetupTSFilter( pTSParser, nSlot, &pTune->pids_table );
	}
	return 1;
}

void CloseTSChannel(  TS_PARSER *pTSParser, int nSlot )
{
	SLOT  *slot;
	if ( nSlot >= MAX_SLOT_NUM ) 
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: slot %d is invalid." ), nSlot ));
		return ;
	}
	
	slot = &pTSParser->slot[nSlot];

	if ( slot->ts_streams.ts_element )
	{
		SAGETV_FREE( slot->ts_streams.ts_element );
		slot->ts_streams.ts_element = NULL;
	}
	if ( slot->es_streams.es_element )
	{
		SAGETV_FREE( slot->es_streams.es_element );
		slot->es_streams.es_element = NULL;
	}
	if ( slot->av_streams.av_element )
	{
		ReleaseAVElementData( slot->av_streams.av_element );
		SAGETV_FREE( slot->av_streams.av_element );
		slot->av_streams.av_element = NULL;
	}

	if ( slot->tracks != NULL )
		pTSParser->slot_num--;

	slot->ts_streams.total_streams = 0;
	slot->ts_streams.num_stream = 0;
	slot->es_streams.total_streams = 0; 
	slot->es_streams.num_stream = 0;
	slot->av_streams.total_streams = 0;
	slot->av_streams.num_stream = 0;
	SageLog(( _LOG_TRACE, 3, TEXT("TS channel closed. slot:%d" ), nSlot ));
}

void ResetTSChannel(  TS_PARSER *pTSParser, int nSlot )
{
	int i;
	SLOT  *slot;
	if ( nSlot >= MAX_SLOT_NUM ) 
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: slot index %d is invalid." ), nSlot ));
		return ;
	}

	slot = &pTSParser->slot[nSlot];
	slot->ts_streams.num_stream = 0;
	slot->es_streams.num_stream = 0;
	slot->av_streams.num_stream = 0;
	for ( i = 0; i < slot->ts_streams.total_streams; i++ )
	{
		memset( &slot->ts_streams.ts_element[i], 0, sizeof(TS_ELEMENT) );
		slot->ts_streams.ts_element[i].channel_index = i;
		memset( &slot->es_streams.es_element[i], 0, sizeof(ES_ELEMENT) );
		slot->es_streams.es_element[i].channel_index = i;
		ReleaseAVElementData( slot->av_streams.av_element );
		memset( &slot->av_streams.av_element[i], 0, sizeof(AV_ELEMENT) );
		slot->av_streams.av_element[i].channel_index = i;
	}
	pTSParser->input_packets = 0;
	pTSParser->valid_pcakets = 0;
	pTSParser->pmt_count = 0;
	pTSParser->psi_count = 0;
	slot->ctrl = 0;
	slot->pcr = 0;
	slot->pcr_cue = 0;
	slot->pcr_start = 0;
	slot->pcr_rate = 0;
	slot->tracks->main_video_index = 0xffff;
	slot->tracks->main_audio_index = 0xffff;
	slot->state = PARSING_INFO;
}

void LockTSChannel(  TS_PARSER *pTSParser, int nSlot )
{
	if ( nSlot >= MAX_SLOT_NUM ) 
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: slot index %d is invalid." ), nSlot ));
		return ;
	}

	pTSParser->slot[nSlot].ctrl |= CTRL_LOCK_PMT;
}

static void ResetTSChannelTrack(  TS_PARSER *pTSParser, int nSlot )
{
	int i;
	SLOT  *slot;
	slot = &pTSParser->slot[nSlot];
	for ( i = 0; i < slot->ts_streams.total_streams; i++ )
	{
		slot->es_streams.es_element[i].content_type = 0;
		slot->es_streams.es_element[i].format_fourcc = 0;
		slot->es_streams.es_element[i].es_id = 0;
		slot->es_streams.es_element[i].stream_id = 0;
		slot->av_streams.av_element[i].content_type = 0;
		slot->av_streams.av_element[i].format_fourcc = 0;
	}
	slot->tracks->main_video_index = 0xffff;
	slot->tracks->main_audio_index = 0xffff;
	slot->state |= PARSING_INFO;
}

void UnlockTSChannel(  TS_PARSER *pTSParser, int nSlot )
{
	if ( nSlot >= MAX_SLOT_NUM ) 
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: slot index %d is invalid." ), nSlot ));
		return ;
	}

	pTSParser->slot[nSlot].ctrl &= ~CTRL_LOCK_PMT;
}

static uint32_t GetDesLanguageCode( char* pDesc, int nLen );
int SetupTSFilter( TS_PARSER *pTSParser, int nSlot, PIDS_TABLE* pPidsTable )
{
	int i, ret;
	SLOT *slot;
	if ( nSlot < 0 || nSlot >MAX_SLOT_NUM )
		return 0;

	slot = &pTSParser->slot[nSlot];
	slot->ts_streams.num_stream = pPidsTable->pid_num;
	for ( i = 0; i<pPidsTable->pid_num; i++ )
	{
		int index = pPidsTable->pid_tbl[i].index;
		slot->ts_streams.ts_element[index].pid  = pPidsTable->pid_tbl[i].pid;
		slot->ts_streams.ts_element[index].type = pPidsTable->pid_tbl[i].type;

		if ( pPidsTable->pid_tbl[i].type == 2 )
			slot->ts_streams.ts_element[index].content_type = VIDEO_DATA;
		else
		if ( pPidsTable->pid_tbl[i].type == 3 )
			slot->ts_streams.ts_element[index].content_type = AUDIO_DATA;
		else
		if ( pPidsTable->pid_tbl[i].type == 6 &&  strstr( pPidsTable->pid_tbl[i].desc, "SUB" ) )
			slot->ts_streams.ts_element[index].content_type = SUBTITLE_DATA;

		slot->ts_streams.ts_element[index].language_code = GetDesLanguageCode( pPidsTable->pid_tbl[i].desc, sizeof(pPidsTable->pid_tbl[i].desc) );

		if ( strstr( pPidsTable->pid_tbl[i].desc, "H264" ) )
		{
			slot->ts_streams.ts_element[index].type = H264_STREAM_TYPE;
			slot->ts_streams.ts_element[index].content_type = VIDEO_DATA;
			slot->ts_streams.ts_element[index].format_fourcc = SAGE_FOURCC( "H264" );
		}
		else
		if ( strstr( pPidsTable->pid_tbl[i].desc, "VC1" ) )
		{
			slot->ts_streams.ts_element[index].type = VC1_STREAM_TYPE;
			slot->ts_streams.ts_element[index].content_type = VIDEO_DATA;
			slot->ts_streams.ts_element[index].format_fourcc = SAGE_FOURCC( "VC1 " );
		}
		else
		if ( strstr( pPidsTable->pid_tbl[i].desc, "AAC-HE" ) )
		{
			slot->ts_streams.ts_element[index].type = AAC_HE_STREAM_TYPE;
			slot->ts_streams.ts_element[index].content_type = AUDIO_DATA;
			slot->ts_streams.ts_element[index].format_fourcc = SAGE_FOURCC( "AACH" );
		}
		else
		if ( strstr( pPidsTable->pid_tbl[i].desc, "AAC" ) )
		{
			slot->ts_streams.ts_element[index].type = AAC_STREAM_TYPE;
			slot->ts_streams.ts_element[index].content_type = AUDIO_DATA;
			slot->ts_streams.ts_element[index].format_fourcc = SAGE_FOURCC( "AAC " );
		}
		else
		if ( strstr( pPidsTable->pid_tbl[i].desc, "AC3" ) )
		{
			slot->ts_streams.ts_element[index].type = AC3_STREAM_TYPE;
			slot->ts_streams.ts_element[index].content_type = AUDIO_DATA;
			slot->ts_streams.ts_element[index].format_fourcc = SAGE_FOURCC( "AC3 " );
		}
		else
		if ( strstr( pPidsTable->pid_tbl[i].desc, "TRUEHD-AC3" ) )
		{
			slot->ts_streams.ts_element[index].type = TUREHD_AC3_STREAM_TYPE;
			slot->ts_streams.ts_element[index].content_type = AUDIO_DATA;
			slot->ts_streams.ts_element[index].format_fourcc = SAGE_FOURCC( "AC3T" );
		}
		else
		if ( strstr( pPidsTable->pid_tbl[i].desc, "E-AC3" ) )
		{
			slot->ts_streams.ts_element[index].type = E_AC3_STREAM_TYPE;
			slot->ts_streams.ts_element[index].content_type = AUDIO_DATA;
			slot->ts_streams.ts_element[index].format_fourcc = SAGE_FOURCC( "AC3E" );
		}
		else
		if ( strstr( pPidsTable->pid_tbl[i].desc, "HD-DTS" ) )
		{
			slot->ts_streams.ts_element[index].type = HD_DTS_STREAM_TYPE;
			slot->ts_streams.ts_element[index].content_type = AUDIO_DATA;
			slot->ts_streams.ts_element[index].format_fourcc = SAGE_FOURCC( "DTSH" );
		}
		else
		if ( strstr( pPidsTable->pid_tbl[i].desc, "DTS" ) )
		{
			slot->ts_streams.ts_element[index].type = DTS_STREAM_TYPE;
			slot->ts_streams.ts_element[index].content_type = AUDIO_DATA;
			slot->ts_streams.ts_element[index].format_fourcc = SAGE_FOURCC( "DTS " );
		}
		else
		if ( strstr( pPidsTable->pid_tbl[i].desc, "MASTER-DTS" ) )
		{
			slot->ts_streams.ts_element[index].type = HD_MASTER_DTS_STREAM_TYPE;
			slot->ts_streams.ts_element[index].content_type = AUDIO_DATA;
			slot->ts_streams.ts_element[index].format_fourcc = SAGE_FOURCC( "DTSM" );
		}
	}

	if ( pPidsTable->pcr_pid != 0 )
		slot->ts_streams.pcr_pid = pPidsTable->pcr_pid;

	SageLog(( _LOG_TRACE, 3, TEXT("Manually setup Channel TS Filter (slot:%d)"), nSlot ));
	ret = SetupChannelFilter( pTSParser->ts_filter, &slot->ts_streams  );
	if ( ret > 0 )
		pTSParser->state |= TS_FILTER_READY;
	
	return ret;
}

void SetupStreamFormat( TS_PARSER *pTSParser, int nFormat, int nSubFormat )
{
	SetStreamFormat( pTSParser->ts_filter->psi_parser, nFormat, nSubFormat );
	if ( pTSParser->media_type == 0 && nSubFormat != 0 )
		pTSParser->media_type = MEDIA_TYPE_TV;
}

static int _tune_data_( TUNE* pTune, char* buf, int buf_size );
void SetupTune( TS_PARSER *pTSParser, int nSlot, TUNE* pTune )
{
	SLOT *slot;
	if ( nSlot < 0 || nSlot >MAX_SLOT_NUM )
		return ;

	slot = &pTSParser->slot[nSlot];
	slot->tune = *pTune;
	ConsolidateTuneParam( &slot->tune, pTSParser->stream_format, pTSParser->stream_subformat );
	slot->tune.state = 0;
	UnlockTSChannel( pTSParser, nSlot );
	if ( pTSParser->media_type == 0 )
	{
		if ( pTune->stream_format == ATSC_STREAM || pTune->sub_format != 0 )
			pTSParser->media_type = MEDIA_TYPE_TV;
	}

	{
		char buf[100];
		_tune_data_( pTune, buf, sizeof(buf) );
		SageLog(( _LOG_TRACE, 3, TEXT("Tune Channel (slot:%d) %s (ch:%d)"), nSlot, buf, pTune->channel ));
	}
}

void DisablePSI( TS_PARSER *pTSParser  )
{
	pTSParser->ts_filter->disable_psi_parse = 1;
}

void EnablePSI( TS_PARSER *pTSParser  )
{
	pTSParser->ts_filter->disable_psi_parse = 0;
}

void ResetPSI( TS_PARSER *pTSParser )
{
	ResetPSIParser( pTSParser->ts_filter->psi_parser );
}

void DisablePatPmt( TS_PARSER *pTSParser  )
{
	pTSParser->ts_filter->disable_ts_table_parse = 1;
}

void EnablePatPmt( TS_PARSER *pTSParser  )
{
	pTSParser->ts_filter->disable_ts_table_parse = 0;
}

void DisableRebuildStream( TS_PARSER *pTSParser )
{ 
	pTSParser->rebuild_stream_ctrl = 0; 
	SageLog(( _LOG_TRACE, 3, TEXT("Rebuid stream is disabled")));  
}

void EnableRebuildStream( TS_PARSER *pTSParser )
{ 
	SageLog(( _LOG_TRACE, 3, TEXT("Rebuid stream is enabled") ));
	pTSParser->rebuild_stream_ctrl = 1; 
}

void SetupTSEPGDump( TS_PARSER *pTSParser, DUMP pfnEPGDump, void* pEPGDumpContext )
{
	if ( pTSParser->ts_filter->psi_parser == NULL )
		return;

	pTSParser->ts_filter->psi_parser->dumper.epg_dumper = pfnEPGDump;
	pTSParser->ts_filter->psi_parser->dumper.epg_dumper_context = pEPGDumpContext;
}

void SetupTSEPGDumpLanguage( TS_PARSER *pTSParser, uint32_t lLauguageCode )
{
	if ( pTSParser->ts_filter->psi_parser == NULL )
		return;
	SetLanguage( pTSParser->ts_filter->psi_parser, lLauguageCode );
} 

static void PostStatusMessage( TS_PARSER *pTSParser, char* pMessageText )
{
	if ( pTSParser->dumper.message_dumper != NULL )
	{
		MESSAGE_DATA message={0};
		strncpy( message.title, "STATUS", sizeof(message.title) );
		message.message = (uint8_t*)pMessageText;
		message.message_length = (uint16_t)strlen(pMessageText)+1 ;
		message.buffer = (uint8_t*)pMessageText;
		message.buffer_length = message.message_length ;
	
		pTSParser->dumper.message_dumper( pTSParser->dumper.message_dumper_context, &message, sizeof(message) );
	}
}

static int SetupNakeStreamTSFilter( TS_PARSER *pTSParser )
{
	int i, ret, slot_index;
	TS_STREAMS *ts_streams = NULL;

	if ( pTSParser->slot_num == 0 ) 
		return 0;
	for ( i = 0; i<MAX_SLOT_NUM; i++ )
	{
		if ( pTSParser->slot[i].tracks == NULL ) continue;
		ts_streams = &pTSParser->slot[i].ts_streams;
		slot_index = i;
		break;
	}

	if ( ts_streams == NULL )
		return 0;

	if ( BuildChannelTSFilter( pTSParser->ts_filter, ts_streams ) > 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Build channel TS Filter (slot:%d) packets:%d guessing content format."), 
					slot_index, pTSParser->ts_filter->ts_packet_counter ));
		ret = SetupChannelFilter( pTSParser->ts_filter, ts_streams  );
		if ( ret > 0 )
		{
			char buf[32];
			pTSParser->state |= TS_FILTER_READY;
			pTSParser->slot[slot_index ].state |= CHANNEL_LOCKED;
			snprintf( buf, sizeof(buf), "STREAM REBUILD (slot:%d)", slot_index );
			PostStatusMessage( pTSParser, buf );
		}
	}

	return 0;
}
static void _display_drop_tracks( TRACKS* tracks );
static int CheckTSParseDone( TS_PARSER *pTSParser )
{
	int i, k, total_channel=0, ready_channel = 0;
	int ret = 0;
	SLOT *slot;

	if ( pTSParser->slot_num == 0 )
		return 0;

	if ( (pTSParser->state & PARSING_INFO) == 0x0 )
		return 0;

	for ( k = 0; k<MAX_SLOT_NUM; k++ )
	{
		slot = &pTSParser->slot[k];
		if ( slot->tracks == NULL ) continue;
		if ( !(slot->state & PARSING_INFO ) )  continue;

		total_channel++;
		//if ( ( slot->state & (AV_DATA_READY | NO_AV_DATA) )  ) 
		//{
		//	ready_channel++;
		//	continue;
		//} 

		if ( !(slot->state & CHANNEL_LOCKED) && !(slot->state & FAILED_CHANNEL ) )
		{
			if ( pTSParser->input_packets > pTSParser->failed_channel_threshold )
			{
				char buf[32];
				slot->state |= FAILED_CHANNEL;
				snprintf( buf, sizeof(buf), "STREAM FAILED (slot:%d): err=100", k );
				PostStatusMessage( pTSParser, buf );
			}
		}

		if ( (slot->state & CHANNEL_LOCKED) )
		{
			uint32_t total_es_block_num = 0;
			int av_ready = 0, av_processing = 0, other_processing = 0, encrypted = 0;;
			for ( i = 0; i<slot->ts_streams.num_stream; i++ )
			{
				if ( slot->ts_streams.ts_element[i].content_type )
				{
					total_es_block_num += slot->tracks->track[i].es_blocks_counter;
				}
			}

			for ( i = 0; i<slot->ts_streams.num_stream; i++ )
			{
			
				switch ( slot->ts_streams.ts_element[i].content_type ) {
				case VIDEO_DATA:
				case AUDIO_DATA:
					//check  audio and video only
					if ( slot->es_streams.es_element[i].content_type == 0 ||
						 slot->av_streams.av_element[i].content_type == 0 )
					{
						av_processing++;

						//kick out an empty audio in DVB
						if ( pTSParser->media_type == MEDIA_TYPE_TV &&
							 slot->tracks->track[i].ts_packets_counter == 0 &&
							 slot->ts_streams.ts_element[i].content_type == AUDIO_DATA &&
							 ParsingElapse( pTSParser ) > 1  )
						{
							av_processing--;
							slot->tracks->track[i].state = NO_PACKET;
							SageLog(( _LOG_TRACE, 3, TEXT("No audio packet avaliable on pid:0x%02x The sub stream (ES:%d:%d) is ignored (total es blocks:%d)" ),
										slot->ts_streams.ts_element[i].pid, 
										k, slot->es_streams.es_element[i].channel_index,
										total_es_block_num ));
							SageLog(( _LOG_TRACE, 3, TEXT("Kick out an empty stream (elapse:%d)" ), ParsingElapse( pTSParser ) ));
						} else
						if ( slot->tracks->track[i].ts_packets_counter == 0 &&
							 total_es_block_num > pTSParser->empty_sub_stream_threshold )
						{
							av_processing--;
							slot->tracks->track[i].state = NO_PACKET;
							SageLog(( _LOG_TRACE, 3, TEXT("No packet avaliable on pid:0x%02x The sub stream (ES:%d:%d) is ignored (total es blocks:%d)" ),
										slot->ts_streams.ts_element[i].pid, 
										k, slot->es_streams.es_element[i].channel_index,
										total_es_block_num ));
							//no data avaliable in the stream, ignore it;
						} else
						if ( slot->tracks->track[i].es_blocks_counter == 0 &&
							 total_es_block_num > pTSParser->empty_sub_stream_threshold )
						{
							av_processing--;
							slot->tracks->track[i].state = NO_ES_BLOCK;
							SageLog(( _LOG_TRACE, 3, TEXT("No PES packet on pid:0x%02x, The sub stream (ES:%d:%d) is ignored (total es blocks:%d)" ),
										slot->ts_streams.ts_element[i].pid, 
										k, slot->es_streams.es_element[i].channel_index,
										total_es_block_num ));
							//no data avaliable in the stream, ignore it;
						} else
						if ( slot->tracks->track[i].scrambling_flag && slot->tracks->track[i].es_blocks_counter < 10 )
						{
							uint32_t threshold = 1200*4;
							if ( pTSParser->stream_format == DVB_STREAM )
							{
								threshold = 1200*8;
								if ( pTSParser->stream_subformat == SATELLITE )
									threshold = 1200*16;
								
								//if ( slot->ts_streams.ts_element[i].format_fourcc == SAGE_FOURCC( "H264" ) )
								if ( pTSParser->stream_subformat == CABLE )
									threshold = 1200*20;
								
							}
							if ( slot->tracks->track[i].ts_packets_counter > threshold )
							{
								av_processing--;
								encrypted++; 
								slot->tracks->track[i].state = ENCRYPTED;
								SageLog(( _LOG_TRACE, 3, TEXT("Encrypted stream on pid:0x%02x es blocks:%d ts_packet:%d" ),
										slot->ts_streams.ts_element[i].pid, 
										slot->tracks->track[i].es_blocks_counter, slot->tracks->track[i].ts_packets_counter	));
							}
						} else
						{
							if ( ParsingElapse( pTSParser ) > pTSParser->max_stream_check_time+1 )
							{
								av_processing--;
								if ( slot->tracks->track[i].state < 0x80 )
									slot->tracks->track[i].state = UNKONW_FORMAT_1;
								
							} else
							if (  slot->ts_streams.ts_element[i].content_type == AUDIO_DATA )
							{
								//DVB-S in "Hotbird 4 @000.0W-10.775H carrying channel "Pentagon channel"
								//uses type 0x85(DTS-H) 0x87(EAC3) transmit private stream;
								if ( pTSParser->media_type == MEDIA_TYPE_TV && slot->ts_streams.ts_element[i].format_fourcc == SAGE_FOURCC( "AC3E" ) )
								{
									//check if es packets size is too small
									if ( slot->tracks->track[i].state < 0x80  && slot->tracks->track[i].es_blocks_counter >= 10 )
									{
										int ratio = slot->tracks->track[i].ts_packets_counter/slot->tracks->track[i].es_blocks_counter; 
										if ( ratio < 3 )
										{
											av_processing--;
											slot->tracks->track[i].state = UNKONW_FORMAT_5;
										}
									}
									if ( slot->tracks->track[i].state < 0x80  && slot->tracks->track[i].processed_bytes > 40*1024 )
									{
										av_processing--;
										slot->tracks->track[i].state = UNKONW_FORMAT_3;
									}
								} else
								if ( slot->ts_streams.ts_element[i].format_fourcc == SAGE_FOURCC( "DTSH" ) )
								{
									//check if es packets is too small
									if ( slot->tracks->track[i].state < 0x80  && slot->tracks->track[i].es_blocks_counter >= 10 )
									{
										int ratio = slot->tracks->track[i].ts_packets_counter/slot->tracks->track[i].es_blocks_counter; 
										if ( ratio < 3 )
										{
											av_processing--;
											slot->tracks->track[i].state = UNKONW_FORMAT_5;
										}
									}
									if ( slot->tracks->track[i].state < 0x80  && slot->tracks->track[i].processed_bytes > 40*1024 )
									{
										av_processing--;
										slot->tracks->track[i].state = UNKONW_FORMAT_3;
									}
								} else
								if ( slot->tracks->track[i].processed_bytes > 80*1024 )
								{
									av_processing--;
									if ( slot->tracks->track[i].state < 0x80 )
										slot->tracks->track[i].state = UNKONW_FORMAT_2;
								} else
								{
									if ( slot->tracks->track[i].es_blocks_counter >= 16 )
									{ 
										int ratio = slot->tracks->track[i].ts_packets_counter/slot->tracks->track[i].es_blocks_counter; 
										if ( ratio < 2 )
										{
											av_processing--;
											if ( slot->tracks->track[i].state < 0x80 )
												slot->tracks->track[i].state = UNKONW_FORMAT_5;
										}
									}
								}
							} /* end of AUDIO_DATA */
						}
					} else
					{
						av_ready++;
						slot->tracks->track[i].state = 1;
					}
					break;
				case SUBTITLE_DATA:
					//as data may take long data to show up, we don't wait subtitle data to present, 
					//just assume ready
					if ( pTSParser->subtitle_ctrl && slot->av_streams.av_element[i].format_fourcc == 0 )
					{ 
						if ( slot->ts_streams.ts_element[i].type == 0x06 )
						{
							int ret = ParseDVBSubtitleDesc( &slot->av_streams.av_element[i].d.s.sub, 
								                   slot->ts_streams.ts_element[i].desc,
												   slot->ts_streams.ts_element[i].desc_len);
							if ( ret )
							{
								slot->av_streams.av_element[i].d.s.sub.type = DVB_SUBTITLE;
								slot->es_streams.es_element[i].content_type = SUBTITLE_DATA;
								slot->es_streams.es_element[i].format_fourcc = SAGE_FOURCC( "SUB " );
								slot->es_streams.es_element[i].language_code =  slot->ts_streams.ts_element[i].language_code;
								slot->av_streams.av_element[i].content_type = SUBTITLE_DATA;
								slot->av_streams.av_element[i].format_fourcc = SAGE_FOURCC( "SUB " );
								_prints_av_elmnt( &slot->av_streams.av_element[i], k, 0 );
							}

						} else
						if ( slot->ts_streams.ts_element[i].type == 0x90 )
						{
							slot->av_streams.av_element[i].d.s.sub.type = PGS_SUBTITLE;
							slot->es_streams.es_element[i].content_type = SUBTITLE_DATA;
							slot->es_streams.es_element[i].format_fourcc = SAGE_FOURCC( "SUB " );
							slot->es_streams.es_element[i].language_code =  slot->ts_streams.ts_element[i].language_code;
							slot->av_streams.av_element[i].content_type = SUBTITLE_DATA;
							slot->av_streams.av_element[i].format_fourcc = SAGE_FOURCC( "SUB " );
							_prints_av_elmnt( &slot->av_streams.av_element[i], k, 0 );
						}

					} 


					break;
				case TELETEXT_DATA:
					break;
				case PRIVAITE_DATA:
					break;
				case UNIDENTIFIED:
					av_processing++;

					if ( ( slot->tracks->track[i].ts_packets_counter == 0 || slot->tracks->track[i].es_blocks_counter == 0 )&&
							 total_es_block_num > pTSParser->empty_sub_stream_threshold )
					{
						av_processing--;
						slot->tracks->track[i].state = NO_PACKET;
						SageLog(( _LOG_TRACE, 3, TEXT("No ES data avaliable on pid:0x%02x, The unidentified sub stream (ES:%d:%d) is ignored (total es blocks:%d)" ),
										slot->ts_streams.ts_element[i].pid, 
										k, slot->es_streams.es_element[i].channel_index,
										total_es_block_num ));
							//no data avaliable in the stream, ignore it;
					} else
					if ( slot->tracks->track[i].scrambling_flag && slot->tracks->track[i].es_blocks_counter < 10 )
					{
						uint32_t threshold = 1200*2;
						if ( pTSParser->stream_format == DVB_STREAM )
							threshold = 1200*6;
						if ( slot->tracks->track[i].ts_packets_counter > threshold )
						{
							av_processing--;
							encrypted++; 
							slot->tracks->track[i].state = ENCRYPTED;
							SageLog(( _LOG_TRACE, 3, TEXT("Encrypted stream on pid:0x%02x, es blocks:%d, ts_packet:%d" ),
									slot->ts_streams.ts_element[i].pid, 
									slot->tracks->track[i].es_blocks_counter, slot->tracks->track[i].ts_packets_counter	));
						}
					} else
					if ( slot->tracks->track[i].processed_bytes > 640*1024 || slot->tracks->track[i].ts_packets_counter > 1200* 6 )
					//if ( slot->tracks->track[i].es_blocks_counter > 80 || slot->tracks->track[i].ts_packets_counter > 1200* 6 )
					{
						av_processing--;
						if ( slot->tracks->track[i].state < 0x80 )
							slot->tracks->track[i].state = UNKONW_FORMAT_2;
					} else
					{
						if ( slot->tracks->track[i].es_blocks_counter >= 16 )
						{ 
							int ratio = slot->tracks->track[i].ts_packets_counter/slot->tracks->track[i].es_blocks_counter; 
							if ( ratio < 2 )
							{
								av_processing--;
								if ( slot->tracks->track[i].state < 0x80 )
									slot->tracks->track[i].state = UNKONW_FORMAT_5;
							}
						}

						if ( ParsingElapse( pTSParser ) >= pTSParser->max_stream_check_time-1 )
						{
							av_processing--;
							if ( slot->tracks->track[i].state < 0x80 )
								slot->tracks->track[i].state = UNKONW_FORMAT_1;
						}
					}

					break;
				case UNKNOWN_DATA:
					other_processing++;
					if ( ( slot->tracks->track[i].ts_packets_counter == 0 || slot->tracks->track[i].es_blocks_counter == 0 )&&
							 total_es_block_num > pTSParser->empty_sub_stream_threshold )
					{
						other_processing--;
						SageLog(( _LOG_TRACE, 3, TEXT("No ES data avaliable on pid:0x%02x, The unknown sub stream (ES:%d:%d) is ignored (total es blocks:%d)" ),
										slot->ts_streams.ts_element[i].pid, 
										k, slot->es_streams.es_element[i].channel_index,
										total_es_block_num ));
							//no data avaliable in the stream, ignore it;
					}
					break;
					
				}
				
			}

			if ( encrypted )
			{
				pTSParser->state |= ENCRYPTED_CHANNEL;
				if ( pTSParser->wait_clean_stream )
				{
					av_processing = encrypted;
					encrypted = 0; 
				} else
				{
					SageLog(( _LOG_TRACE, 3, TEXT("The encrypted stream is ignored on (slot:%d)  "), k ));
				}
			} else
			{
				pTSParser->state &= ~ENCRYPTED_CHANNEL;
			}

			ASSERT( av_processing >= 0 );
			if ( av_processing == 0 || encrypted )
			{
				if ( !( slot->state & (AV_DATA_READY | NO_AV_DATA ) )  ) 
				{
					char buf[64];
					snprintf( buf, sizeof(buf), "STREAM READY (slot:%d) elapsed time:%d.", k,
						      (uint32_t)(pTSParser->psi_time - pTSParser->psi_time_start) );
					_display_drop_tracks( slot->tracks );


					if ( slot->pcr_start == 0 && pTSParser->pts_fix_ctrl  )
					{
						pTSParser->pts_fix_ctrl = 0;
						SageLog(( _LOG_TRACE, 3, TEXT( "No PCR is present on 0x%0x, PTS-FIX disabled!"), slot->ts_streams.pcr_pid  ));
					}
					{
						char time_dsp[32];
						UTCFormat( pTSParser->psi_time, time_dsp, sizeof(time_dsp) );
						SageLog(( _LOG_TRACE, 3,  TEXT("stream clock (GMT):%s"), time_dsp  )); 
					}

					PostStatusMessage( pTSParser, buf );
				}	

				if ( av_ready )
					slot->state |= AV_DATA_READY; 
				else
					slot->state |= NO_AV_DATA;

				ready_channel++;
				ret = 1;
			}
		}
	}

	if ( total_channel > 0 &&  total_channel == ready_channel )
	{
		pTSParser->state &= ~PARSING_INFO;
		SageLog(( _LOG_TRACE, 3, TEXT( "Parsing is Done!")  ));
		ret = 1;
	}
	
	//if ( total_channel > 0 )
	//	pTSParser->status = PARSER_RUNNING;

	return ret;
}

static int UpdateTuneData( TS_PARSER *pTSParser, int nStreamFormat, int nSubFormat, int nData1, int nData2, int nData3, int nData4 )
{
	int i, hit = 0;
	TUNE *tune;

	if ( nStreamFormat == 0 ) return 0;
	for ( i = 0; i<MAX_SLOT_NUM; i++ )
	{
		if ( pTSParser->slot[i].tracks == NULL ) continue;
		tune = &pTSParser->slot[i].tune;

		ConsolidateTuneParam( tune, nStreamFormat,  nSubFormat );
		if ( nData1 == 0 && nData2 == 0 ) continue;
		if ( tune->tune_string_type == 0 ) continue;

		//an USA Satellite carries ATSC stream, we need do tune data consverison
		if ( nStreamFormat == ATSC_STREAM && tune->stream_format == DVB_STREAM )
		{
			TUNE tune_tmp= *tune;
			tune->stream_format = nStreamFormat;
			tune->u.atsc.physical_ch = tune_tmp.u.dvb.onid;
			tune->u.atsc.major_num = tune_tmp.u.dvb.tsid;
			tune->u.atsc.minor_num = tune_tmp.u.dvb.sid;
			SageLog(( _LOG_TRACE, 3,  TEXT("An ATSC stream is carried in a DVB stream (phy:%d major:%d minor:%d)."),
					tune->u.atsc.physical_ch, tune->u.atsc.major_num, tune->u.atsc.minor_num   ));
			if ( pTSParser->media_type == 0 )
				pTSParser->media_type = MEDIA_TYPE_TV;
		}

		if ( nStreamFormat == ATSC_STREAM )
		{
			if ( tune->u.atsc.minor_num != 0 && tune->u.atsc.major_num == nData1 && tune->u.atsc.minor_num == nData2  )
			{
				if ( tune->u.atsc.program_id != nData4 )
				{
					SageLog(( _LOG_TRACE, 3,  TEXT("\tATSC channel program id was changed! %d=>%d(%d) (slot:%d)"),
						                              tune->u.atsc.program_id, nData4, tune->tune_string_type, i   ));
				}
				tune->u.atsc.tsid = nData3;
				tune->u.atsc.program_id = nData4;  //program id
				tune->tune_string_type++;
				hit++;
				SageLog(( _LOG_TRACE, 3,  TEXT("\tATSC channel major:%d minor:%d tsid:%d program:%d (%d) (slot:%d)"),
					tune->u.atsc.major_num, tune->u.atsc.minor_num,	tune->u.atsc.tsid, tune->u.atsc.program_id, 
					tune->tune_string_type, i   ));

				{
					char buf[100];
					_tune_data_( tune, buf, sizeof(buf) );
					SageLog(( _LOG_TRACE, 3, TEXT("\tTune Channel (slot:%d) %s "), i, buf ));
				}

			} else //QAM channel may have a trouble, they change channel information in PSI very often, we rely on program to get channel
			{
				if ( ( tune->u.atsc.major_num == 0 || tune->u.atsc.major_num == 1008 ) && nData1 > 0 )
				{
 					if ( ( tune->u.atsc.program_id && tune->u.atsc.program_id == nData4 ) )
					{
						SageLog(( _LOG_TRACE, 3,  TEXT("\tQAM channel %d-%d and program:%d was not matched!, based on program (%d). (type:%d) (slot:%d)"),
						                                      tune->u.atsc.major_num, tune->u.atsc.minor_num, tune->u.atsc.program_id, nData4, tune->tune_string_type, i   ));
						tune->u.atsc.tsid = nData3;
						tune->tune_string_type++;
						tune->u.atsc.major_num = nData1;
						tune->u.atsc.minor_num = nData2;
						hit++;
						SageLog(( _LOG_TRACE, 3,  TEXT("\tRemap QAM channel major:%d minor:%d tsid:%d program:%d (%d) (slot:%d)"),
							tune->u.atsc.major_num, tune->u.atsc.minor_num,	tune->u.atsc.tsid, tune->u.atsc.program_id, 
							tune->tune_string_type, i   ));

						{
							char buf[100];
							_tune_data_( tune, buf, sizeof(buf) );
							SageLog(( _LOG_TRACE, 3, TEXT("\tTune Channel (slot:%d) %s "), i, buf ));
						}
					}

					if ( pTSParser->media_type == 0 )
						pTSParser->media_type = MEDIA_TYPE_TV;

				} 
			}
			
		} else
		if ( nStreamFormat == DVB_STREAM )
		{
			if ( tune->u.dvb.onid == nData1 && tune->u.dvb.tsid == nData2 && tune->u.dvb.sid == nData3 )
			{
				hit++;
			}
		}
	}

	

	return hit;
}

static int CheckTsid( TS_PARSER *pTSParser, int nTsid )
{
	int i;
	TUNE *tune;
	for ( i = 0; i<MAX_SLOT_NUM; i++ )
	{
		if ( pTSParser->slot[i].tracks == NULL ) continue;
		tune = &pTSParser->slot[i].tune;

		if ( tune->stream_format == ATSC_STREAM )
		{
			if ( tune->u.atsc.tsid != 0 && tune->u.atsc.tsid == nTsid )
				return 1;
		} else
		if ( tune->stream_format == DVB_STREAM )
		{
			if ( tune->tune_string_type >= 2 && tune->u.dvb.tsid == nTsid )
				return 1;
		} else
		{
			if ( tune->tune_string_type >= 2 && tune->u.unkn.tsid == nTsid )
				return 1;
		}
	}

	return 0;
}

static int CheckOnid( TS_PARSER *pTSParser, int nOnid, int nTsid )
{
	int i;
	TUNE *tune;
	for ( i = 0; i<MAX_SLOT_NUM; i++ )
	{
		if ( pTSParser->slot[i].tracks == NULL ) continue;
		tune = &pTSParser->slot[i].tune;

		if ( tune->stream_format == DVB_STREAM )
		{
			if ( tune->tune_string_type >= 2 && tune->u.dvb.onid == nOnid && tune->u.dvb.tsid == nTsid )
				return 1;
		} 
	}

	return 0;
}

static int PickupChannel( TS_PARSER *pTSParser, int nChannel, int nTotalChannel, PMT_DATA* pPmtData, int nCmd )
{
	int i, ret, flag=0;
	TUNE *tune;
	TS_STREAMS *ts_streams;
	int tsid = pPmtData->tsid;
	int program_id = pPmtData->pmt_table->program_number;
	for ( i = 0; i<MAX_SLOT_NUM; i++ )
	{
		if ( pTSParser->slot[i].tracks == NULL ) continue;
		tune = &pTSParser->slot[i].tune;

		if ( pTSParser->slot[i].ctrl & CTRL_LOCK_PMT ) 
		{
			SageLog(( _LOG_TRACE, 3,  TEXT("Channel locked on slot:%d, do nothing" ), i )); 
			continue;
		}

		if ( tsid== 0 && program_id == 0 ) 
		{
			SageLog(( _LOG_TRACE, 3,  TEXT("Invalid tsid and program id slot:%d, do nothing" ), i )); 
			continue;
		}

		//if a channel was selected and PMT isn't updated, do nothing.
		if ( ( nCmd == 0 || nCmd == 2 ) && tune->state == 1 ) 
			continue;

		ts_streams = &pTSParser->slot[i].ts_streams;
		pTSParser->slot[i].num_of_channels = nTotalChannel;
		if ( pTSParser->first_channel == 0 )
		{
			if ( pPmtData->pmt_table->active_count )
				 pTSParser->first_channel = nChannel;
		} else
		if ( nChannel < pTSParser->first_channel )
		{
			if ( pPmtData->pmt_table->active_count )
				 pTSParser->first_channel = nChannel;
		}

		//for QAM:  PSI (present/absent), major-minor ( match/unmatched ), 
		if ( tune->state == 0 && tune->pids_table.pid_num == 2 && 
			 tune->pids_table.pid_tbl[0].pid && tune->pids_table.pid_tbl[1].pid )
		{
			ret = SetupChannelFilterByPids( pTSParser->ts_filter, ts_streams, &tune->pids_table, pTSParser->media_type );
			if ( ret > 0 )
			{
				tune->state = 1;
				tune->channel = nChannel;
				ts_streams->pmt_pid = pPmtData->pid;
				pTSParser->slot[i].state |= CHANNEL_LOCKED;
				SageLog(( _LOG_TRACE, 3,  TEXT("  *Select channel by pids (slot:%d, media:%d) pid:0x%x(0x%02x) pid:0x%x(0x%02x) (%d) stream:%d "), i, pTSParser->media_type,
						tune->pids_table.pid_tbl[0].pid, tune->pids_table.pid_tbl[0].type,
						tune->pids_table.pid_tbl[1].pid, tune->pids_table.pid_tbl[1].type,
						tune->tune_string_type, ret   ));
				PostProgramData( pTSParser, ts_streams, pPmtData, tune );
				flag++;
			}

		} else
		if ( tune->stream_format == ATSC_STREAM )
		{
			ret = 0;
			if ( tune->tune_string_type <= 1 ) //based on channel number to pick up a channel
			{
				if ( tune->state == 0 )
				{
					//if not specify any channel, pick up first one
					if ( tune->tune_string_type == 0 && tune->channel == 0 && tune->u.atsc.program_id == 0 )
					{
						if ( nChannel == 1 && pPmtData->pmt_table->active_count ) 
							tune->channel = 1;
						else
						if ( (int)pTSParser->pmt_count > nTotalChannel )
							tune->channel = pTSParser->first_channel;
						SageLog(( _LOG_TRACE, 3,  TEXT("\tNot specify a channel, pick up channel ONE. channel%d."), tune->channel ));
					}
					if ( tune->u.atsc.program_id > 0 && tune->u.atsc.program_id == program_id )
						ret =  SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
					else
					if ( tune->channel == nChannel ) //based on channel number to pickup a channel
						ret =  SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
				}

				if ( ret > 0 )
					SageLog(( _LOG_TRACE, 3,  TEXT("\tPickup a channel by channel:%d (pmt:%d)."), 
					          nChannel, pTSParser->pmt_count ));
				if ( ret > 0 )
				{
					tune->u.atsc.tsid = tsid;
					tune->u.atsc.program_id = program_id;
					if ( tune->sub_format == CABLE ) //if it's a QAM
					{
						pTSParser->current_service_tsid = tsid;
					}
				}
			} else
			if ( tune->u.atsc.tsid ) //PSI present and channel was found in PSI.
			{
				if ( tune->tune_string_type >= 2 )
				{
					//if ( tune->u.atsc.program_id == program_id && tune->u.atsc.tsid == tsid ) //QAM tsid may be wrong, tsid in PSI isn't inconsise with one in PMT
					if ( tune->u.atsc.program_id == program_id )
					{
						ret = SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type);
					} 
				}
			} else
			if ( tune->u.atsc.major_num == 0 ) //QAM channel
			{
				if ( tune->u.atsc.program_id )
				{
					if ( tune->u.atsc.program_id == program_id )
						ret = SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
				} else
					if ( tune->u.atsc.minor_num == nChannel )
						ret = SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );

			} else
			if ( tune->state == 0 && pTSParser->pmt_count > 6 && (int)pTSParser->pmt_count > (nTotalChannel+nTotalChannel/2)  )
			{
				if ( pTSParser->psi_count > 0 ) //PSI present
				{
					if ( pTSParser->psi_count >= 3 ) //no channel found in PSI
					{
						if (  tune->u.atsc.program_id > 0 && tune->u.atsc.program_id == program_id )
						{
							ret =  SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
							if ( ret > 0 )
								SageLog(( _LOG_TRACE, 3,  TEXT("\tPSI present, but channel not found, pickup a channel by program:%d (pmt:%d)."), 
								          program_id, pTSParser->pmt_count ));
						}
						//if ( ret <= 0 && tune->u.atsc.minor_num > 0 && tune->u.atsc.minor_num == nChannel )
						//{
						//	ret =  SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
						//	if ( ret > 0 )
						//		SageLog(( _LOG_TRACE, 3,  TEXT("\tPSI present, but channel not found, pickup a channel by channel:%d (pmt:%d)."), 
						//		          nChannel, pTSParser->pmt_count ));
						//}

					} else
					{
						//do nothing, wait more psi to get a specified channel descraption
						continue;
					}

				} else
				if ( pTSParser->pmt_count > 30  ) //no PSI present, force to pickup a channel
				{
					if (  tune->u.atsc.program_id > 0 )
					{
						if ( tune->u.atsc.program_id == program_id )
						{
							ret =  SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
							if ( ret > 0 )
								SageLog(( _LOG_TRACE, 3,  TEXT("\tno PSI present, pickup a channel by program:%d (pmt:%d)."), 
							          program_id, pTSParser->pmt_count ));
						}
					} else
					if ( ret <= 0 && tune->u.atsc.minor_num > 0 && tune->u.atsc.minor_num == nChannel )
					{
						ret =  SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
						if ( ret > 0 )
							SageLog(( _LOG_TRACE, 3,  TEXT("\tno PSI present, pickup a channel by channel:%d (pmt:%d)."), 
							          nChannel, pTSParser->pmt_count ));
					}

				}
				if ( ret > 0 )
				{
					tune->u.atsc.tsid = tsid;
					tune->u.atsc.program_id = program_id;
					if ( tune->sub_format == CABLE ) //if it's a QAM
						pTSParser->current_service_tsid = tsid;
				}
			}

			if ( ret > 0 )
			{
				tune->state = 1;
				tune->channel = nChannel;
				ts_streams->pmt_pid = pPmtData->pid;
				pTSParser->slot[i].state |= CHANNEL_LOCKED;
				if ( nCmd == 2 )
					SageLog(( _LOG_TRACE, 3,  TEXT("\tNo Channel Information Avaliable, force to pickup a channel (pmt count:%d)."), pTSParser->pmt_count ));
				SageLog(( _LOG_TRACE, 3,  TEXT("  *Select ATSC channel (slot:%d, media:%d) major:%d minor:%d tsid:%d program:%d (%d) stream:%d "), i, pTSParser->media_type,
						tune->u.atsc.major_num, tune->u.atsc.minor_num,	tune->u.atsc.tsid, tune->u.atsc.program_id, 
						tune->tune_string_type, ret   ));
				PostProgramData( pTSParser, ts_streams, pPmtData, tune );
				flag++;
			}

		} else
		if ( tune->stream_format == DVB_STREAM )
		{
			ret = 0;
			if ( tune->tune_string_type > 2 && nCmd != 2 )
			{
				if (  tune->u.dvb.tsid == tsid && tune->u.dvb.sid == program_id )
				{
					ret = SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
				}
			} else
			if ( tune->tune_string_type > 0 )
			{
				if ( tune->u.dvb.sid == program_id )
				{
					ret = SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
					if ( ret > 0 )
					{
						tune->u.dvb.tsid = tsid;
						tune->u.dvb.sid = program_id;
					}
				}
			} else
			if ( tune->tune_string_type == 0 )
			{
				if ( tune->state == 0 )
				{
					//if not specify any channel, pick up first one
					if ( tune->tune_string_type == 0 && tune->channel == 0 && tune->u.atsc.program_id == 0 )
					{
						if ( nChannel == 1 && pPmtData->pmt_table->active_count ) 
							tune->channel = 1;
						else
						if ( (int)pTSParser->pmt_count > nTotalChannel )
							tune->channel = pTSParser->first_channel;
						SageLog(( _LOG_TRACE, 3,  TEXT("\tNot specify a channel, pick up channel ONE. channel%d."), tune->channel ));

					}

					if ( tune->channel == nChannel )
					{
						ret =  SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
						if ( ret > 0 )
						{
							tune->u.atsc.tsid = tsid;
							tune->u.atsc.program_id = program_id;
						}
					} 
				}
			}

			if ( ret > 0 )
			{
				int post_ret;
				tune->state = 1;
				tune->channel = nChannel;
				ts_streams->pmt_pid = pPmtData->pid;
				pTSParser->slot[i].state |= CHANNEL_LOCKED;
				post_ret = PostProgramData( pTSParser, ts_streams, pPmtData, tune );
				SageLog(( _LOG_TRACE, 3,  TEXT("\t*Select DVB channel (slot:%d, media:%d) onid:%d tsid:%d sid:0x%04x (%d) post_ret:%d"), i, pTSParser->media_type, 
						tune->u.dvb.onid, tune->u.dvb.tsid, tune->u.dvb.sid, tune->tune_string_type, post_ret ));
				flag++;
				if ( post_ret == 2 )
				{
					tune->state = 0;
					pTSParser->slot[i].state &= ~CHANNEL_LOCKED;
					SageLog(( _LOG_TRACE, 3,  TEXT("DVB device isn't ready, need re-post program data later") ));
					flag = 0;
				} else
					SageLog(( _LOG_TRACE, 3,  TEXT("PostProgram Data ret:%d"), ret ));
			}

		} else
		if ( tune->tune_string_type == 0 )
		{
			if ( tune->channel == 0 && tune->u.unkn.tsid == 0 )
			{
				if ( nChannel == 1 && pPmtData->pmt_table->active_count ) 
					tune->channel = 1;
				else
				if ( (int)pTSParser->pmt_count > nTotalChannel )
					tune->channel = pTSParser->first_channel;
				SageLog(( _LOG_TRACE, 3,  TEXT("\tNot specify a channel, pick up channel ONE. channel%d."), tune->channel ));

			}
			if ( tune->channel == nChannel && ( tune->u.unkn.tsid == 0 || tsid == tune->u.unkn.tsid ) )
			{
				ret =  SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
				if ( ret > 0 )
				{
					tune->state = 1;
					ts_streams->pmt_pid = pPmtData->pid;
					pTSParser->slot[i].state |= CHANNEL_LOCKED;
					tune->u.unkn.tsid = tsid;
					tune->u.unkn.data1 = program_id;
					if ( nCmd == 2 )
						SageLog(( _LOG_TRACE, 3,  TEXT("\tNo Channel Information Avaliable, force to pickup a channel.")));
					SageLog(( _LOG_TRACE, 3,  TEXT("\t*Select PMT channel  (slot:%d, media:%d) channel:%d tsid:%d sid:%d (%d) stream:%d"), i, pTSParser->media_type,
								nChannel, tsid, program_id, tune->tune_string_type, ret ));
					if (  nCmd == 1 )
						ResetTSChannelTrack( pTSParser, i );
				}
			} 
		} else
		if ( tune->tune_string_type == 1 ) //we don't care stream format, it may be a naked stream
		{
			if ( tune->u.unkn.data1 == nChannel )
			{
				ret = SelectTSFilterChannel( pTSParser->ts_filter, ts_streams, tsid, program_id, pTSParser->media_type );
				if ( ret )
				{
					tune->u.unkn.data2 = tsid;
					tune->u.unkn.data2 = program_id;
				}
				if ( ret > 0 )
				{
					tune->state = 1;
					tune->channel = nChannel;
					ts_streams->pmt_pid = pPmtData->pid;
					pTSParser->slot[i].state |= CHANNEL_LOCKED;
					SageLog(( _LOG_TRACE, 3,  TEXT("\t*Select REMUX_FILE channel  (slot:%d, media:%d) channel:%d tsid:%d sid:%d (%d) stream:%d"), i, pTSParser->media_type, 
						nChannel, tsid, program_id, tune->tune_string_type, ret ));
					if ( ts_streams->num_stream == 0 )
						SageLog(( _LOG_TRACE, 3,  TEXT("\tIt's an empty channel") ));
					PostProgramData( pTSParser, ts_streams, pPmtData, tune );
					flag++;

				}
			} 
		}
	}
	return flag;
}

static uint32_t GetDesLanguageCode( char* pDesc, int nLen )
{
	int i = 0;
	while ( i < nLen && pDesc[i] != ':' ) i++;
	if ( i+4 < nLen && pDesc[i] == ':' &&  pDesc[i+4] != ':') 
    {
		return LanguageCode( (uint8_t*)&pDesc[i+1] );
	}
	return 0;
}

static int IsAllChannelsLocked( TS_PARSER *pTSParser )
{
	int i, unlocked_slot = 0; 
	for ( i = 0; i<MAX_SLOT_NUM; i++ )
	{
		if ( pTSParser->slot[i].tracks != NULL && (pTSParser->slot[i].state & CHANNEL_LOCKED) == 0 ) 
			unlocked_slot++;
	}
	return unlocked_slot==0;
}

void ConsolidateTuneParam( TUNE *pTune, int nStreamFormat, int nSubFormat )
{
	UNKNOWN_CHANNEL unkn;
	if ( nStreamFormat == 0 )
		return;
	if ( pTune->stream_format == ATSC_STREAM || pTune->stream_format == DVB_STREAM || 
		 pTune->stream_format == FREE_STREAM )
		return;

	pTune->stream_format = nStreamFormat;
	if ( nSubFormat )
		pTune->sub_format = nSubFormat;

	unkn = pTune->u.unkn;
	if ( nStreamFormat == ATSC_STREAM )
	{
		if ( pTune->tune_string_type == 3 ) //for tuning string "phy-major-minor"
		{
			pTune->u.atsc.physical_ch = unkn.data1;
			pTune->u.atsc.major_num   = unkn.data2;
			pTune->u.atsc.minor_num   = unkn.data3;
		} else
		if ( pTune->tune_string_type == 2 ) //for tuning string "major-minor"
		{
			pTune->u.atsc.physical_ch = 0xffff;
			pTune->u.atsc.major_num   = unkn.data2;
			pTune->u.atsc.minor_num   = unkn.data3;
		} else
		if ( pTune->tune_string_type == 1 ) //for tuning string "minor"
		{
			pTune->u.atsc.physical_ch = 0xffff;
			pTune->u.atsc.major_num   = 0xffff;
			pTune->u.atsc.minor_num   = unkn.data1;
		}

		if ( unkn.tsid > 0 )
			pTune->u.atsc.tsid = unkn.tsid;

	} else
	if ( nStreamFormat == DVB_STREAM )
	{
		if ( pTune->tune_string_type == 3 )		//for tuning string "onid-tsid-sid"
		{
			pTune->u.dvb.onid = unkn.data1;
			pTune->u.dvb.tsid = unkn.data2;
			pTune->u.dvb.sid  = unkn.data3;
			pTune->stream_format = nStreamFormat;
		} else
		if ( pTune->tune_string_type == 2 )		//for tuning string "tsid-sid"
		{
			pTune->u.dvb.onid = 0xffff;
			pTune->u.dvb.tsid = unkn.data1;
			pTune->u.dvb.sid  = unkn.data2;
			pTune->stream_format =  nStreamFormat; 
		} else
		if ( pTune->tune_string_type == 1 )    //for tuning string "sid"
		{
			pTune->u.dvb.onid = 0xffff;
			pTune->u.dvb.tsid = 0xffff;
			pTune->u.dvb.sid  = unkn.data1;
		}

		if ( unkn.tsid > 0 )
			pTune->u.dvb.tsid = unkn.tsid; 

	} else
	if ( nStreamFormat == FREE_STREAM )
	{

	}
}

static int _tune_data_( TUNE* pTune, char* buf, int buf_size )
{
	int pos = 0;	
	if ( pTune->stream_format == ATSC_STREAM  )
	{   pos += snprintf( buf+pos, buf_size-pos, "ATSC phy:%d major:%d minor:%d program:%d", 
	                     pTune->u.atsc.physical_ch, pTune->u.atsc.major_num, pTune->u.atsc.minor_num, pTune->u.atsc.program_id  );
	} else
	if ( pTune->stream_format == DVB_STREAM )
	{   pos += snprintf( buf+pos, buf_size-pos, "DVB onid:%d tsid:%d sid:%d", pTune->u.dvb.onid, pTune->u.dvb.tsid, pTune->u.dvb.sid );
	} else
	{
		pos += snprintf( buf+pos, buf_size-pos, "FREE type:%d  data1:%d data2:%d data3:%d tsid:%d", pTune->tune_string_type, 
						 pTune->u.unkn.data1, pTune->u.unkn.data2, pTune->u.unkn.data3, pTune->u.unkn.tsid  );
		if ( pTune->tune_string_type == 0 )
			pos += snprintf( buf+pos, buf_size-pos, " default channel:%d", pTune->channel );
	}

	return pos;
}

static void PtsFix( TS_PARSER *pTSParser, SLOT* pSlot, TRACK* pTrack )
{
	if ( pTSParser->pts_offset )
	{
		if ( pTrack->es_elmnt->pes.has_pts )
			pTrack->es_elmnt->pes.pts += pTSParser->pts_offset;
		if ( pTrack->es_elmnt->pes.has_dts )
			pTrack->es_elmnt->pes.dts += pTSParser->pts_offset;
	}

	if ( pTSParser->pts_fix_ctrl == 0 )
		return;

	//don't fix subtitle, teletext stream pts
	if ( pTrack->ts_elmnt != NULL && 
		 ( pTrack->ts_elmnt->content_type == TELETEXT_DATA 
			/*|| pTrack->ts_elmnt->content_type == SUBTITLE_DATA */ ) )
	{
		return ;
	}

	if ( pTrack->es_elmnt->pes.has_pts  )
	{
		if (  pTrack->es_elmnt->pes.pts < pSlot->pcr_start/300 && pSlot->pcr_start >= 0 
			  && (pSlot->pcr - pSlot->pcr_start)/300 < pTSParser->pts_fix_threshold ) 
		{
			pTrack->es_elmnt->pes.has_pts = 0;
		} else
		{
			LONGLONG delta;
			pTrack->es_elmnt->pes.pts -= pSlot->pcr_start/300; //correct PTS-FIX

			//if pts is out of range, drop it
			delta = pTrack->es_elmnt->pes.pts - pTrack->cue/300;
			if ( delta < 0 ) delta = -delta;
			if ( delta > (LONGLONG)pTSParser->pts_fix_threshold )
			{
				if ( pTrack->cue )
				{	
					char tmp1[40], tmp2[40], tmp3[40];
					SageLog(( _LOG_TRACE, 3, TEXT("PTS-FIX: PTS is out of range at %s pos:%s, the PTS (%s) is droppped (ch:%d)"), 
						       time_stamp_s( pTrack->cue/300, tmp1, 40 ), long_long_s( pSlot->pcr_cue, tmp2, 40 ), 
							   time_stamp_s( pTrack->es_elmnt->pes.pts, tmp3, 40 ),
							   pTrack->channel_index ));
				}
				pTrack->es_elmnt->pes.has_pts = 0;
				pTrack->es_elmnt->pes.has_dts = 0;
			}
		}
	}

	if ( pTrack->es_elmnt->pes.has_dts )
	{
		
		if ( pTrack->es_elmnt->pes.dts < pSlot->pcr_start/300 && pSlot->pcr_start >= 0 
			  && (pSlot->pcr - pSlot->pcr_start)/300 < pTSParser->pts_fix_threshold ) 
		{
			pTrack->es_elmnt->pes.has_dts = 0;
		} else
		{
			LONGLONG delta;
			pTrack->es_elmnt->pes.dts -= pSlot->pcr_start/300;

			//if dts is out of range, drop it
			delta = pTrack->es_elmnt->pes.dts - pTrack->cue/300;
			if ( delta < 0 ) delta = -delta;
			if ( delta > (LONGLONG)pTSParser->pts_fix_threshold )
			{
				pTrack->es_elmnt->pes.has_dts = 0;
			}
		}
	}
}

/* //ZQ
static void PtsFix0( TS_PARSER *pTSParser, SLOT* pSlot, TRACK* pTrack )
{
	ULONGLONG  cur_scr, cur_ts;
	ULONGLONG  delta_scr, delta_ts;
	ULONGLONG  cur_count;
	int need_fix = 0;

	if ( pTSParser->pts_fix_ctrl == 0 ) //skip fixing
		return ;
	
	if ( pTrack->es_elmnt->pes.has_pts )
	{
		if ( pTrack->es_elmnt->content_type == VIDEO_DATA && pTrack->es_elmnt->pes.has_dts )
			cur_ts = pTrack->es_elmnt->pes.dts;
		else
			cur_ts = pTrack->es_elmnt->pes.pts;
	} else
		cur_ts = 0;

	//wait till PTS shows up on a stream
	if ( pSlot->time_stamp.last_count == 0 && cur_ts == 0 )
	{
		pTrack->es_elmnt->pes.pts = 0; 
		pTrack->es_elmnt->pes.dts = 0; 
		pTrack->es_elmnt->scr = 0;
		return ;
	}

	if ( pSlot->time_stamp.last_count == 0 )
	{
		LONGLONG delta;
		int lag = 0;
		pSlot->time_stamp.count_offset = pSlot->pcr_cue;
		pSlot->time_stamp.scr_offset = pSlot->pcr;
		pSlot->time_stamp.ts_offset  = cur_ts;

		delta = pSlot->time_stamp.ts_offset - pSlot->time_stamp.scr_offset/300;
		if ( (lag = delta < 0) )  delta = delta;
		{
			char mesg[512], *p=mesg;
			int pos = 0, size = sizeof(mesg); 
			pos = snprintf( p+pos, size-pos, "delta:%s", _time_stamp( delta, NULL,0));
			SageLog(( _LOG_TRACE, 3, TEXT("PTS-FIX initial: %s (lag:%d )"), mesg, lag  ) );
		}
	}

	ASSERT( pSlot->pcr_cue >= pSlot->time_stamp.count_offset );
	cur_scr   = pSlot->pcr > pSlot->time_stamp.scr_offset ? pSlot->pcr - pSlot->time_stamp.scr_offset : 0;
	cur_ts    = cur_ts > pSlot->time_stamp.ts_offset ? cur_ts - pSlot->time_stamp.ts_offset : 0;
	cur_count = pSlot->pcr_cue - pSlot->time_stamp.count_offset;

	delta_scr = cur_scr > pSlot->time_stamp.last_scr ? cur_scr-pSlot->time_stamp.last_scr : pSlot->time_stamp.last_scr - cur_scr;
	delta_ts  = cur_ts > pSlot->time_stamp.last_ts ? cur_ts - pSlot->time_stamp.last_ts : pSlot->time_stamp.last_ts - cur_ts;
	if ( delta_scr/300 > pTSParser->pts_fix_threshold )
	{
		{
			char mesg[512], *p=mesg;
			int pos = 0, size = sizeof(mesg); 
			pos += snprintf( p+pos, size-pos, "pcr discontinue:%s -", _time_stamp( pSlot->time_stamp.last_scr/300, NULL,0));
			pos += snprintf( p+pos, size-pos, " %s ", _time_stamp( cur_scr/300, NULL,0));
			pos += snprintf( p+pos, size-pos, "^ %s ", _time_stamp( delta_scr/300, NULL,0));
			SageLog(( _LOG_TRACE, 3, TEXT("PTS (%d:%d): %s"), pSlot->tracks_id, pTrack->es_elmnt->channel_index, mesg  ) );
		}
		need_fix = 0x01;
	}

	if ( delta_ts > pTSParser->pts_fix_threshold )
	{
		{
			char mesg[512], *p=mesg;
			int pos = 0, size = sizeof(mesg); 
			pos += snprintf( p+pos, size-pos, "pts discontinue:%s -", _time_stamp( pSlot->time_stamp.last_ts, NULL,0));
			pos += snprintf( p+pos, size-pos, " %s ", _time_stamp( cur_ts, NULL,0));
			pos += snprintf( p+pos, size-pos, "^ %s ",   _time_stamp( delta_ts, NULL,0));
			SageLog(( _LOG_TRACE, 3, TEXT("PTS (%d:%d): %s"), pSlot->tracks_id, pTrack->es_elmnt->channel_index, mesg  ) );
		}
		need_fix |= 0x02;
	}

	if ( 0 && need_fix )
	{
		//interpolating scr
		ULONGLONG new_scr, new_ts;
		ULONGLONG last_count = pTSParser->input_packets - pSlot->time_stamp.count_offset;
		ASSERT( last_count >= cur_count );
		if ( cur_count + 4 < last_count )
		{
			new_scr = pSlot->time_stamp.last_scr * (last_count - cur_count)/pSlot->time_stamp.last_count;
			new_scr += pSlot->time_stamp.last_scr;
		} else
			new_scr = pSlot->time_stamp.last_scr;

		new_ts = (pSlot->time_stamp.ts_offset - pSlot->time_stamp.scr_offset/300) + new_scr/300;

		if ( need_fix == 0x02 ) //scr  OK
		{
			pSlot->time_stamp.ts_offset = cur_ts - new_ts; 
		} else
		if ( need_fix == 0x01 ) //pts OK
		{
			pSlot->time_stamp.scr_offset = cur_ts - new_scr; 

		} else                   //both scr and PTS is bad
		{
			pSlot->time_stamp.ts_offset = cur_ts - new_ts; 
			pSlot->time_stamp.scr_offset = cur_ts - new_scr; 
		}

		cur_scr = new_scr;
		cur_ts  = new_ts;
		{
			char mesg[512], *p=mesg;
			int pos = 0, size = sizeof(mesg); 
			pos += snprintf( p+pos, size-pos, "interpolating new pts:%s ", _time_stamp( new_ts, NULL,0));
			pos += snprintf( p+pos, size-pos, "pcr:%s |", _time_stamp( new_scr/300, NULL,0));
			pos += snprintf( p+pos, size-pos, "pts:%s", _time_stamp( pSlot->time_stamp.ts_offset, NULL,0));
			pos += snprintf( p+pos, size-pos, "pcr:%s", _time_stamp( pSlot->time_stamp.scr_offset/300, NULL,0));
			SageLog(( _LOG_TRACE, 3, TEXT("PTS (%d:%d): %s"), pSlot->tracks_id, pTrack->es_elmnt->channel_index, mesg  ) );
		}

	}

	if ( pTrack->es_elmnt->pes.has_pts )
		pTrack->es_elmnt->pes.pts -= pSlot->time_stamp.ts_offset; 
	if ( pTrack->es_elmnt->pes.has_dts )
		pTrack->es_elmnt->pes.dts -= pSlot->time_stamp.ts_offset; 
	pTrack->es_elmnt->scr = cur_scr;

	pSlot->time_stamp.last_scr = cur_scr; 
	pSlot->time_stamp.last_count = cur_count == 0 ? 1: cur_count; 
	if ( cur_ts != 0 ) pSlot->time_stamp.last_ts = cur_ts;
}
*/

void QueueTSParserAbort( TS_PARSER *pTSParser )
{
	pTSParser->command |= TS_PARSER_CMD_ABORT;
}

void QueueTSParserZero( TS_PARSER *pTSParser )
{
	pTSParser->command |= TS_PARSER_CMD_ZERO;
}
void QueueTSParserReset( TS_PARSER *pTSParser )
{
	pTSParser->command |= TS_PARSER_CMD_RESET;
}

void ResetTSParserCommand( TS_PARSER *pTSParser )
{
	pTSParser->command = 0;
}

void TSParserZero( TS_PARSER *pTSParser )
{
	int i, j;
	for ( i = 0; i<pTSParser->slot_num; i++ )
	{
		for ( j = 0; j < pTSParser->slot[i].tracks->number_track; j++ )
		{
			pTSParser->slot[i].tracks->track[j].es_data_bytes = 0;
			pTSParser->slot[i].tracks->track[j].buffer_size = 0;
			pTSParser->slot[i].tracks->track[j].es_blocks_counter = 0;
			pTSParser->slot[i].tracks->track[j].group_start = 0;
			pTSParser->slot[i].tracks->track[j].buffer_index = 0xffff;
			pTSParser->slot[i].tracks->track[j].command = 0;
			pTSParser->slot[i].tracks->track[j].processed_bytes = 0;
		}

		pTSParser->slot[i].pcr = 0;
		pTSParser->slot[i].pcr_cue = 0;
		pTSParser->slot[i].pcr_start = 0; //ZQ test
		pTSParser->slot[i].time_stamp.last_scr = 0;
		pTSParser->slot[i].time_stamp.last_count = 0;
		pTSParser->slot[i].time_stamp.last_ts = 0;
		//pTSParser->slot[i].time_stamp.scr_offset = 0
		//pTSParser->slot[i].time_stamp.count_offset = 0;
		//pTSParser->slot[i].time_stamp.ts_offset = 0;
	}

	
	//zero statistic
	pTSParser->used_bytes = 0;
	pTSParser->input_packets = 0;
	pTSParser->valid_pcakets = 0;
	pTSParser->bad_packets = 0;
	pTSParser->bad_blocks = 0;
	pTSParser->block_count = 0;
	pTSParser->command = 0;

	pTSParser->ts_filter->ts_packet_counter = 0;
}

ULONGLONG TSDataUsedBytes( TS_PARSER *pTSParser )
{
	return pTSParser->used_bytes;
}

uint32_t PacketsSNRatio( TS_PARSER *pTSParser )
{
	uint32_t ratio;
	uint32_t noise_packets = pTSParser->input_packets - pTSParser->valid_pcakets + pTSParser->bad_packets;
	if ( pTSParser->input_packets < 800 )
		return 10000;

	if ( noise_packets > pTSParser->input_packets ) 
		return 0;

	ratio = 100*pTSParser->input_packets/(1+noise_packets);
	return ratio;
}

int CheckTSFormat( const uint8_t* pData, int nBytes )
{
	int i = 0, count = 0;
	int score = 0;

	while ( i < nBytes )
	{
		while ( i<nBytes && pData[i] != TS_SYNC )
		{
			i++; count = 0; 
			score = 0; 
		}
		if ( i+188 < nBytes && pData[i+TS_PACKET_LENGTH] == TS_SYNC )
		{
			if ( pData[i+1]==TS_SYNC && pData[i+1+TS_PACKET_LENGTH]==TS_SYNC ) //for not fooled by an all 'G' data
			{
				i++;
			} else
			{
				if ( count++ >= 4 )
				{
					if ( (pData[i+1] & 0x80) == 0 ) //no error indicator in a ts packet.
						score = score*2+2;
					else
						score = score*2+1;

					if ( score > 1000 )
						break;
				}
			}
			i += TS_PACKET_LENGTH;
		} else
		{
			i++;
		}
	}

	if ( score > 1000 ) score = 1000;
	return score;
}

int CheckM2TFormat( const uint8_t* pData, int nBytes )
{
	int i = 0, count = 0;
	int score = 0;

	while ( i < nBytes )
	{
		while ( i<nBytes && pData[i] != TS_SYNC ) 
		{
			i++; count = 0; 
			score = 0; 
		}
		if ( i+188 < nBytes && pData[i+M2TS_PACKET_LENGTH] == TS_SYNC )
		{
			if ( pData[i+1]==TS_SYNC && pData[i+1+M2TS_PACKET_LENGTH]==TS_SYNC ) //for not fooled by an all 'G' data
			{
				i++;
			} else
			{
				if ( count++ >= 4 )
				{
					if ( (pData[i+1] & 0x80) == 0 ) //no error indicator in a ts packet.
						score = score*2+2;
					else
						score = score*2+1;

					if ( score > 1000 )
						break;
				}
			}
			i += M2TS_PACKET_LENGTH;
		} else
		{
			i++;
		}
	}

	if ( score > 1000 ) score = 1000;
	return score;
}

int CheckASIFormat( const uint8_t* pData, int nBytes )
{
	int i = 0, count = 0;
	int score = 0;

	while ( i < nBytes )
	{
		while ( i<nBytes && pData[i] != TS_SYNC ) { i++; count = 0; }
		if ( i+188 < nBytes && pData[i+ASI_PACKET_LENGTH] == TS_SYNC )
		{
			if ( pData[i+1]==TS_SYNC && pData[i+1+ASI_PACKET_LENGTH]==TS_SYNC ) //for not fooled by an all 'G' data
			{
				i++;
			} else
			{
				if ( count++ >= 4 )
				{
					if ( (pData[i+1] & 0x80) == 0 ) //no error indicator in a ts packet.
						score = score*2+2;
					else
						score = score*2+1;

					if ( score > 1000 )
						break;
				}
			}
			i += ASI_PACKET_LENGTH;
		} else
		{
			i++;
		}
	}
	if ( score > 1000 ) score = 1000;
	return score;
}

void AudioTSPriorityHackEnable( TS_PARSER *pTSParser )
{
	pTSParser->audio_ts_priority_hack = 1; 
}

void WaitCleanStream( TS_PARSER *pTSParser )
{
	pTSParser->wait_clean_stream = 1;
}

uint32_t GetTSParserState( TS_PARSER *pTSParser )
{
	return pTSParser->state;
}

void SetupATSDump( TS_PARSER *pTSParser, DUMP pfnATSDump, void* pATSDumpContext )
{
	pTSParser->ts_filter->dumper.ats_dumper = pfnATSDump;
	pTSParser->ts_filter->dumper.ats_dumper_context = pATSDumpContext;
}

static char* CAInfo( uint16_t CAPid, uint16_t CAID )
{
	static char _buf[16];
	snprintf( _buf, sizeof(_buf), "CA pid:0x%04x", CAPid );
	return _buf;
}

static void _display_drop_tracks( TRACKS* tracks )
{
	char *error_p;
	int i;
	for ( i = 0; i<tracks->total_track; i++ )
	{
		error_p = "";
		switch ( tracks->track[i].state ) {
		case NO_PACKET: 
			break;
		case NO_ES_BLOCK:	
			break;	
		case ENCRYPTED:		
			break;
		case UNKONW_FORMAT_1:	
			error_p = "Parsing stream timeout";
			break;
		case UNKONW_FORMAT_2:	
			error_p = "Failed Parsing format (maxium data)";
			break;
		case UNKONW_FORMAT_3:	
			error_p = "Unknown audio format";
			break;
		case UNKONW_FORMAT_4:	
			error_p = "private stream on DVB-S";
			break;
		case UNKONW_FORMAT_5:	
			error_p = "It isn't an audio and video stream";
			break;
		}
	if ( error_p[0] )
		SageLog(( _LOG_TRACE, 3, TEXT("%s, drop stream on pid:0x%02x es blocks:%d ts_packet:%d parsed_bytes:%d." ),
				error_p, tracks->track[i].ts_elmnt->pid,
			    tracks->track[i].es_blocks_counter, tracks->track[i].ts_packets_counter, tracks->track[i].processed_bytes));

	}
}

/*
static uint8_t* _search_data_( uint8_t* match, int len, uint8_t* data, int data_size )
{
	int i;
	for ( i = 0; i<data_size-len; i++ )
	
		if ( !memcmp( match, data+i, len ) )
			return data+i;
	
	return NULL;
}
static int _s_(uint8_t*data, int size)
{
	uint8_t pat1[]={	0x00, 0x00, 0x01, 0xb3, 0x50, 0x02, 0xd0, 0x37 };
	int len = 8;
	if ( _search_data_( pat1, len, data, size ) )
	{
		printf( "STOP" );
		return 1;
	}
	return 0;
}
*/
