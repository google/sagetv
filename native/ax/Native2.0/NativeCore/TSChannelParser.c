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
#include "ChannelScan.h"
#include "TSChannelParser.h" 


static int CheckTsId( TS_CHANNEL_PARSER *pTSChannelParser, int nTsid );
static char* CAInfo( uint16_t CAPid, uint16_t CAID );
uint32_t VideoFormat( uint8_t StreamType, uint8_t* pDesc, int nBytes );
static int GetTrackAVInf( TS_CHANNEL_PARSER *pTSChannelParser, int nIndex, char* pBuffer, int nSize );
static int InitScanData(  TS_CHANNEL_PARSER *pTSChannelParser, int nStreamType, int nSubType );
static int AddProgrmToList( PROGRAM_LIST *pProgramList, uint16_t nTsid, uint16_t nProgramId, uint16_t nChannel, int service );
int HasCADesc( uint8_t *pData, int nBytes );
static int PATDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_CHANNEL_PARSER *pTSChannelParser = (TS_CHANNEL_PARSER*)pContext;
	PAT_DATA  *pPatData  = (PAT_DATA*)pData;
	TS_PAT    *pPat      = (TS_PAT*)pPatData->pat_table;
	int16_t pat_index = pPatData->pat_index;
	uint16_t tsid  = pPat->tsid;
	int program_num = pPat->total_program_number;
	int i, tsid_locked=0;

	ASSERT( pContext != NULL );
	ASSERT( nSize == sizeof(PAT_DATA) );

	if ( (pTSChannelParser->state & PROGRAM_FOUND) ) 
	{
		return 0;
	}

	SageLog(( _LOG_TRACE, 3, TEXT("PAT-%d:Program (total channel:%d, tsid:%d )"), pat_index, program_num, tsid ));
	for ( i = 0; i<program_num; i++ )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("\tPMT pid:0x%04x program:%04d"), pPat->program_pid[i], pPat->program_number[i] ));
		if ( pPat->program_number[i] >= 0 )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("program %d is added into pid table (%d)"), pPat->program_number[i], i));
			{
				PID_ITEM pid_inf;
				pid_inf.pid = pPat->program_pid[i];
				pid_inf.type = PID_TYPE_PROGRAM;
				pid_inf.service = 0;
				pTSChannelParser->dumper.pid_dumper( pTSChannelParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );
				pTSChannelParser->state |= PROGRAM_FOUND;
			}
		}
	}
	
	tsid_locked = CheckTsId( pTSChannelParser, tsid )>0;
	if ( tsid_locked )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("tsid %d is locked (%d)"), tsid, i));
		pTSChannelParser->state |= TSID_LOCKED;
		return 1;
	}
	return 0;
}

static int PMTDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_CHANNEL_PARSER *pTSChannelParser = (TS_CHANNEL_PARSER*)pContext;
	PMT_DATA  *pPmtData  = (PMT_DATA*)pData;
	TS_PMT    *pPmt      = (TS_PMT*)pPmtData->pmt_table;
	//TS_SECTION *pPmtSection = pPmtData->pmt_section;
	int channel = pPmtData->channel;
	//int channel_num = pPmtData->total_channel_num;
	int stream_num  = pPmt->total_stream_number;
	int i, encrypted = 0, valid_stream = 0;

	ASSERT( pContext != NULL );
	ASSERT( (nSize == sizeof(PMT_DATA)) );

	if ( stream_num == 0 )		return 1;

	if ( (pPmtData->update_flag & PMT_UPDATED) ) //skip unchanged PMT
	{
		SageLog(( _LOG_TRACE, 3, TEXT("\tChannel:%d (tsid:%d) PMT pid:0x%04x Program:%04d PCR pid:0x%04x %s."),  channel, 
			pPmtData->tsid, pPmtData->pid, pPmt->program_number, pPmt->pcr_pid, pPmt->ca_pid ? CAInfo( pPmt->ca_pid, pPmt->ca_id) :"" ));
		for ( i = 0; i< stream_num; i++ )
		{	
			int has_ca_desc = HasCADesc( pPmt->stream_desc[i].desc_ptr, pPmt->stream_desc[i].desc_bytes )? 1 : 0;
			if ( has_ca_desc ) 
				encrypted++;
			if ( IsVideoType( pPmt->stream_type[i] ) || IsAudioType( pPmt->stream_type[i] ) ) 
				valid_stream++;
			SageLog(( _LOG_TRACE, 3,  TEXT("\t\tType:0x%02x Pid:0x%03x  %s"), pPmt->stream_type[i], pPmt->stream_pid[i], has_ca_desc?"encrypted":" " ));
		}

		//PMT updated and repersent, we need reselect channel to change filter
		if ( ( pPmtData->update_flag & PMT_REPEAT ))
		{
			SageLog(( _LOG_TRACE, 3,  TEXT("PMT Updated, reselect channel" ) ));
		}
	}
	
	if ( pPmt->ca_pid == 0 && encrypted == 0 && valid_stream > 0 )
	{
		if ( AddProgrmToList( &pTSChannelParser->program_list, pPmtData->tsid, pPmt->program_number, pPmtData->channel, 1 ) >= 0 )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("Uncrypted channel is found (channel:%d program:%d tsid:%d)."), pPmtData->channel, pPmt->program_number, pPmtData->tsid ));
		} 
	}

	return 1;
}

static int PSIPidDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_CHANNEL_PARSER *pTSChannelParser = (TS_CHANNEL_PARSER*)pContext;
	if ( (pTSChannelParser->state & CHANNEL_FOUND )  )
		return 0;
	//relay to upper
	return pTSChannelParser->dumper.pid_dumper( pTSChannelParser->dumper.pid_dumper_context, pData, nSize );
}


inline static int IsInATSCChannelList( CHANNEL_LIST *pChannelList, int nMajor, int nMinor, int nProgram )
{
	int i;
	for ( i = 0; i<pChannelList->channel_num; i++ )
	{
		if ( pChannelList->channel[i].u.atsc.major_num == nMajor &&
			 pChannelList->channel[i].u.atsc.minor_num == nMinor && 
			 pChannelList->channel[i].u.atsc.program_id == nProgram )
			return 1;
	}
	return 0;
}

inline static void ExpendChannelList( CHANNEL_LIST *pChannelList, int nExpendNum )
{
	CHANNEL_DAT *old_channel_p = pChannelList->channel;
	int total_num = pChannelList->total_list_num + nExpendNum;
	pChannelList->channel = SAGETV_MALLOC( total_num * sizeof(CHANNEL_DAT) );
	if ( pChannelList->channel )
	{
		if ( old_channel_p )
		{
			memcpy( pChannelList->channel, old_channel_p, 
				    pChannelList->total_list_num * sizeof(CHANNEL_DAT) );
			SAGETV_FREE( old_channel_p );
		}
		pChannelList->total_list_num = total_num;
	} 
}

static int AddChannelList( CHANNEL_LIST *pChannelList, CHANNEL_DATA *pChannelData, TUNE_LIST *pTuneList )
{
	int i, updated = 0;

	if ( pChannelData->stream_format == ATSC_STREAM )
	{
		for ( i = 0; i<pChannelData->num_channel; i++ )
		{
			if ( IsInATSCChannelList( pChannelList, pChannelData->u.atsc[i].major_num
												  , pChannelData->u.atsc[i].minor_num
												  , pChannelData->u.atsc[i].program_id ) )
			   continue;

			if ( pChannelData->sub_format == CABLE && pChannelData->u.atsc[0].u.qam.modulation == 0xfe ) //analog modualtion
				continue;

			//expend channel list
			if ( pChannelList->total_list_num < pChannelData->num_channel )
				ExpendChannelList( pChannelList, 5 );

			if ( pChannelData->u.atsc[i].major_num == 1008 && pChannelData->u.atsc[i].minor_num == 0 )
			{
				SageLog(( _LOG_TRACE, 2, "Drop invalid channel %d name:%s major:%d minor:%d prog:%d  service:%d!", i, 
			 			pChannelData->u.atsc[i].name, pChannelData->u.atsc[i].major_num,
						pChannelData->u.atsc[i].minor_num, pChannelData->u.atsc[i].program_id,
						pChannelData->u.atsc[i].service_type  ));
				continue;
			}

			pChannelList->channel[pChannelList->channel_num].u.atsc  = pChannelData->u.atsc[i];
			pChannelList->channel[pChannelList->channel_num].u.atsc.physical_ch = pChannelData->u.atsc[0].physical_ch;
			pChannelList->channel[pChannelList->channel_num].state = 3;
			pChannelList->channel_num++;
			SageLog(( _LOG_TRACE, 2, "found ATSC channel %d name:%s major:%d minor:%d prog:%d  service:%d", i, 
			 			pChannelData->u.atsc[i].name, pChannelData->u.atsc[i].major_num,
						pChannelData->u.atsc[i].minor_num, pChannelData->u.atsc[i].program_id,
						pChannelData->u.atsc[i].service_type  ));
			updated = 1;
		}


	} else
	if ( pChannelData->stream_format == DVB_STREAM )
	{
		SageLog(( _LOG_TRACE, 2, "ERROR: Unimplmented yet" ));
	}

	return updated;
}

static int AddProgrmToList( PROGRAM_LIST *pProgramList, uint16_t nTsid, uint16_t nProgramId, uint16_t nChannel, int service )
{
	int i;
	if ( nProgramId == 0xffff ) 
		return -3;

	if ( pProgramList->program_num >= pProgramList->total_list_num )
	{
		SageLog(( _LOG_TRACE, 2, "WARING: program list is overflow, drop program %d", nProgramId ) );
		return -2;
	}

	for ( i = 0; i<pProgramList->program_num; i++ )
	{
		if ( pProgramList->program[i].program_id == nProgramId && pProgramList->program[i].tsid == nTsid )
			return -1;
	}

	pProgramList->program[i].channel = (uint8_t)nChannel;
	pProgramList->program[i].program_id = nProgramId;
	pProgramList->program[i].tsid = nTsid;
	pProgramList->program[i].service = service;
	pProgramList->program_num++;
	return i;
}

inline static int ProgramInATSCChannelList( CHANNEL_LIST *pChannelList,  int nProgram )
{
	int i;
	for ( i = 0; i<pChannelList->channel_num; i++ )
	{
		if ( pChannelList->channel[i].u.atsc.program_id == nProgram )
			return 1;
	}
	return 0;
}

inline static int ProgramInDVBChannelList( CHANNEL_LIST *pChannelList, int nTsid, int nSid )
{
	int i;
	for ( i = 0; i<pChannelList->channel_num; i++ )
	{
		if ( pChannelList->channel[i].u.dvb.tsid == nTsid &&
			 pChannelList->channel[i].u.dvb.sid == nSid  )
			return 1;
	}
	return 0;
}

int MergeTSChannelListProgramList( TS_CHANNEL_PARSER *pTSChannelParser )
{
	PROGRAM_LIST *pProgramList = &pTSChannelParser->program_list;
	CHANNEL_LIST *pChannelList = &pTSChannelParser->channel_list;
	TUNE *pTune = &pTSChannelParser->tune;

	int k, program_id, tsid, service;

	if ( pChannelList->channel_num == 0 )
	{
		pChannelList->stream_format = pProgramList->stream_format;
		pChannelList->sub_format = pProgramList->sub_format;
	}

	for ( k = 0; k<pProgramList->program_num; k++ )
	{
		service = pProgramList->program[k].service;
		program_id = pProgramList->program[k].program_id;
		tsid = pProgramList->program[k].tsid;
		if ( service == 0  )	continue;
		
		if ( pChannelList->stream_format == ATSC_STREAM )
		{
			if ( ProgramInATSCChannelList( pChannelList, program_id ) )
			   continue;

			if ( pChannelList->total_list_num <= pChannelList->channel_num )
				ExpendChannelList( pChannelList, 5 );

			pChannelList->channel[pChannelList->channel_num].u.atsc = pTune->u.atsc;
			pChannelList->channel[pChannelList->channel_num].u.atsc.major_num  = 0;
			pChannelList->channel[pChannelList->channel_num].u.atsc.minor_num  = program_id;
			pChannelList->channel[pChannelList->channel_num].u.atsc.program_id = program_id;
			pChannelList->channel[pChannelList->channel_num].u.atsc.service_type = service;
			pChannelList->channel[pChannelList->channel_num].u.atsc.tsid = tsid;

			snprintf( pChannelList->channel[pChannelList->channel_num].u.atsc.name, 
				      sizeof(pChannelList->channel[pChannelList->channel_num].u.atsc.name), "DTV-%d-%d", 
					  pTSChannelParser->tune.u.atsc.physical_ch, program_id );
			pChannelList->channel[pChannelList->channel_num].state = 3;
			pChannelList->channel_num++;


		} else
		if ( pChannelList->stream_format == DVB_STREAM )
		{
			if ( ProgramInDVBChannelList( pChannelList, tsid, program_id ) )
				continue;
	
			//expend channel list
			if ( pChannelList->total_list_num <=  pChannelList->channel_num+1 )
				ExpendChannelList( pChannelList, 5 );

			pChannelList->channel[pChannelList->channel_num].u.dvb = pTune->u.dvb;
			pChannelList->channel[pChannelList->channel_num].u.dvb.onid = 0;
			pChannelList->channel[pChannelList->channel_num].u.dvb.tsid = tsid;
			pChannelList->channel[pChannelList->channel_num].u.dvb.sid  = program_id;
			pChannelList->channel[pChannelList->channel_num].u.dvb.service_type = service==3?2:1;
			pChannelList->channel[pChannelList->channel_num].state = 0x03;
			snprintf( pChannelList->channel[pChannelList->channel_num].u.dvb.name, 
				      sizeof(pChannelList->channel[pChannelList->channel_num].u.dvb.name), "DTV-0-%d-%d", tsid, program_id );

			pChannelList->channel_num++;
		}
	}
	
	return pChannelList->channel_num;
}


static int ChannelInfoDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_CHANNEL_PARSER *pTSChannelParser = (TS_CHANNEL_PARSER*)pContext;
	CHANNEL_DATA *channel_data = (CHANNEL_DATA*)pData;
	int i, ret;

	if ( (pTSChannelParser->state & (CHANNEL_FOUND|PROGRAM_FOUND|CHANNEL_INFO_FOUND) ) && channel_data->update_flag == 0 )
		return 0;

	if ( channel_data->stream_format == ATSC_STREAM )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ATSC Channel Info (format:%s total:%d) " ), 
					StreamFormatString(channel_data->stream_format, channel_data->sub_format), 
					channel_data->num_channel  )); 

		for ( i = 0; i<channel_data->num_channel; i++ )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("\t major:%d minor:%d tsid:%d prog:%d, (tune:%d.%d, type:%d)" ), 
							channel_data->u.atsc[i].major_num, channel_data->u.atsc[i].minor_num, 
				            channel_data->u.atsc[i].tsid, channel_data->u.atsc[i].program_id,
							pTSChannelParser->tune.u.atsc.major_num, pTSChannelParser->tune.u.atsc.minor_num, pTSChannelParser->tune.stream_format ));

		}
		
		channel_data->u.atsc[0].physical_ch = pTSChannelParser->tune.u.atsc.physical_ch;
		ret = AddChannelList( &pTSChannelParser->channel_list, channel_data, &pTSChannelParser->tune_list );
		pTSChannelParser->state |= CHANNEL_INFO_READY;

	} else
	{
		int local_channel = (channel_data->update_flag == 1);

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
				if ( pTSChannelParser->tune.u.dvb.tsid && 
					pTSChannelParser->tune.u.dvb.tsid != channel_data->u.dvb[i].tsid )
					SageLog(( _LOG_TRACE, 3, TEXT("DVB current service is changed (%d -> %d) "),
									pTSChannelParser->tune.u.dvb.tsid, channel_data->u.dvb[i].tsid )); 
				pTSChannelParser->tune.u.dvb.tsid = channel_data->u.dvb[i].tsid;
			}

		}
		
	}
	return 0;
}


//ZQ 1.
static int StreamDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_CHANNEL_PARSER *pTSChannelParser = (TS_CHANNEL_PARSER*)pContext;
	STREAM_DATA   *stream_data   = (STREAM_DATA *)pData;
	TS_ELEMENT    *ts_elment     = stream_data->ts_elment;
	int index = stream_data->ts_elment->channel_index;
	ES_BUFFER_ *pESBuffer = &pTSChannelParser->es_buffer[index];
	int			  in_bytes;
	uint8_t *in_ptr;

	//fill data to send into dumper
	in_bytes = stream_data->bytes;
	in_ptr   = stream_data->data_ptr;

	pESBuffer->input_bytes += in_bytes;
	pTSChannelParser->total_bytes += in_bytes;

	if ( pTSChannelParser->av_streams.av_element[index].format_fourcc )
		return 1;

	if (  stream_data->group_start )
	{   
		PES pes={0};
		if ( IsPESHeaderOfTS( (uint8_t)pESBuffer->stream_id, in_ptr, in_bytes ) && //start a new es block
			 ReadPESHeader( in_ptr, in_bytes, &pes ) > 0 )
		{
			if ( pESBuffer->bytes > 0 )
			{
				int ret = AnylyzeAVElement( &pTSChannelParser->av_streams.av_element[index], &pTSChannelParser->es_streams.es_element[index],
										ts_elment, pESBuffer->buffer, pESBuffer->bytes, SAGETV_TV_RECORDING );
				if ( ret )
				{
					pESBuffer->stream_id = pes.stream_id;
					pESBuffer->pes = pes;
					_prints_av_elmnt( &pTSChannelParser->av_streams.av_element[index], index, pTSChannelParser->total_bytes );
					if ( pTSChannelParser->av_streams.av_element[index].content_type == VIDEO_DATA ) 
						pTSChannelParser->found_video_num++;
					else
					if ( pTSChannelParser->av_streams.av_element[index].content_type == AUDIO_DATA ) 
						pTSChannelParser->found_audio_num++;

					if ( CheckChannelTrackAVInfRead( pTSChannelParser ) )
					{
						pTSChannelParser->state |= AVINF_READY;
						pTSChannelParser->avinf_size =
							GetChannelAVInf( pTSChannelParser, pTSChannelParser->avinf, sizeof(pTSChannelParser->avinf) );
						if ( 0 && pTSChannelParser->dumper.avinf_dumper )
							pTSChannelParser->dumper.avinf_dumper( pTSChannelParser->dumper.avinf_dumper_context,
															pTSChannelParser->avinf, pTSChannelParser->avinf_size );
						SageLog(( _LOG_TRACE, 3, TEXT("AVINF IS READY, Message posted\r\n. %s."), pTSChannelParser->avinf ));
					}

					return 1;
				}
			}
			pESBuffer->pes = pes;
			pESBuffer->bytes = 0;
			memcpy( pESBuffer->buffer+pESBuffer->bytes, in_ptr, in_bytes );
			pESBuffer->bytes += in_bytes;
		} 
	} else
	{
		if ( pESBuffer->bytes + in_bytes >= pESBuffer->buffer_size )
		{

			int ret = AnylyzeAVElement( &pTSChannelParser->av_streams.av_element[index], &pTSChannelParser->es_streams.es_element[index],
									ts_elment, pESBuffer->buffer, pESBuffer->bytes, SAGETV_TV_RECORDING );
			if ( ret )
			{
				_prints_av_elmnt( &pTSChannelParser->av_streams.av_element[index], index, pTSChannelParser->total_bytes );
				if ( pTSChannelParser->av_streams.av_element[index].content_type == VIDEO_DATA ) 
					pTSChannelParser->found_video_num++;
				else
				if ( pTSChannelParser->av_streams.av_element[index].content_type == AUDIO_DATA ) 
					pTSChannelParser->found_audio_num++;

				if ( CheckChannelTrackAVInfRead( pTSChannelParser ) )
				{
					pTSChannelParser->state |= AVINF_READY;
					pTSChannelParser->avinf_size =
						GetChannelAVInf( pTSChannelParser, pTSChannelParser->avinf, sizeof(pTSChannelParser->avinf) );
					if ( 0 && pTSChannelParser->dumper.avinf_dumper )
						pTSChannelParser->dumper.avinf_dumper( pTSChannelParser->dumper.avinf_dumper_context,
															pTSChannelParser->avinf, pTSChannelParser->avinf_size );
					SageLog(( _LOG_TRACE, 3, TEXT("AVINF IS READY, Message posted.\r\n %s."), pTSChannelParser->avinf ));
				}

				return 1;
			}
			pESBuffer->bytes = 0;
		}
		memcpy( pESBuffer->buffer+pESBuffer->bytes, in_ptr, in_bytes );
		pESBuffer->bytes += in_bytes;
	}

	return 1;
}



TS_CHANNEL_PARSER* CreateTSChannelParser( int nMaxStreamNum, int nStreamFormat, int nSubFormat )
{
	int i;
	TS_CHANNEL_PARSER *pTSChannelParser = SAGETV_MALLOC( sizeof(TS_CHANNEL_PARSER) );
	pTSChannelParser->ts_filter = CreateTSFilter( DEFAULT_PAT_NUM, DEFAULT_PMT_NUM, nStreamFormat, nSubFormat ); //ATSC_STREAM, TERRESTRIAL;
	pTSChannelParser->max_stream_num = nMaxStreamNum;
	pTSChannelParser->ts_streams.ts_element = SAGETV_MALLOC( nMaxStreamNum*sizeof(TS_ELEMENT) );
	pTSChannelParser->ts_streams.total_streams = nMaxStreamNum;
	pTSChannelParser->es_streams.es_element = SAGETV_MALLOC( nMaxStreamNum*sizeof(ES_ELEMENT) );
	pTSChannelParser->es_streams.total_streams = nMaxStreamNum;
	pTSChannelParser->av_streams.av_element = SAGETV_MALLOC( nMaxStreamNum*sizeof(AV_ELEMENT) );
	pTSChannelParser->av_streams.total_streams = nMaxStreamNum;
	pTSChannelParser->es_buffer = SAGETV_MALLOC( nMaxStreamNum*sizeof(ES_BUFFER_) );
	for ( i = 0; i<nMaxStreamNum; i++ )
	{
		pTSChannelParser->es_buffer[i].buffer = SAGETV_MALLOC( TMP_ES_BUFFER_SIZE );
		pTSChannelParser->es_buffer[i].buffer_size = TMP_ES_BUFFER_SIZE;
	}


	pTSChannelParser->ts_filter->dumper.pat_dumper = (DUMP)PATDumper;
	pTSChannelParser->ts_filter->dumper.pat_dumper_context = pTSChannelParser;
	pTSChannelParser->ts_filter->dumper.pmt_dumper = (DUMP)PMTDumper;
	pTSChannelParser->ts_filter->dumper.pmt_dumper_context = pTSChannelParser;
	pTSChannelParser->ts_filter->dumper.stream_dumper = (DUMP)StreamDumper;
	pTSChannelParser->ts_filter->dumper.stream_dumper_context = pTSChannelParser;

	pTSChannelParser->ts_filter->psi_parser->dumper.channel_info_dumper  =	(DUMP)ChannelInfoDumper;
	pTSChannelParser->ts_filter->psi_parser->dumper.channel_info_context =	pTSChannelParser;
	pTSChannelParser->ts_filter->psi_parser->dumper.pid_dumper  =	(DUMP)PSIPidDumper;
	pTSChannelParser->ts_filter->psi_parser->dumper.pid_context =	pTSChannelParser;

	InitScanData( pTSChannelParser,  nStreamFormat, nSubFormat );

	pTSChannelParser->max_check_limit = 1024*1024*12; //max check 12M data for avinfo
	SageLog(( _LOG_TRACE, 3, TEXT("TS Channel Parser is created  version 1.0.0  .") ));
	return pTSChannelParser;
}

void ReleaseTSChannelParser( TS_CHANNEL_PARSER *pTSChannelParser )
{
	int i;

	if ( pTSChannelParser->program_list.program )
		SAGETV_FREE( pTSChannelParser->program_list.program );
	if ( pTSChannelParser->channel_list.channel  )
		SAGETV_FREE( pTSChannelParser->channel_list.channel );
	if ( pTSChannelParser->tune_list.tune  )
		SAGETV_FREE( pTSChannelParser->tune_list.tune );

	SAGETV_FREE( pTSChannelParser->ts_streams.ts_element );
	SAGETV_FREE( pTSChannelParser->es_streams.es_element );
	SAGETV_FREE( pTSChannelParser->av_streams.av_element );
	for ( i = 0; i<pTSChannelParser->max_stream_num; i++ )
		SAGETV_FREE( pTSChannelParser->es_buffer[i].buffer );
		
	SAGETV_FREE( pTSChannelParser->es_buffer );
	ReleaseTSFilter( pTSChannelParser->ts_filter );
	SAGETV_FREE( pTSChannelParser );

	SageLog(( _LOG_TRACE, 3, TEXT("TS Channel Parser is released  .") ));
}

void ResetTSChannelParser( TS_CHANNEL_PARSER *pTSChannelParser )
{
	int i;
	ResetTSFilter( pTSChannelParser->ts_filter );
	pTSChannelParser->ts_filter->disable_ts_table_parse = 0;
	pTSChannelParser->ts_filter->disable_stream_filter = 0;
	SageLog(( _LOG_TRACE, 3, TEXT("TS Channel Parser is reset  .") ));
	pTSChannelParser->state = 0;
	for ( i = 0; i<pTSChannelParser->max_stream_num; i++ )
	{
		pTSChannelParser->es_buffer[i].bytes = 0;
		pTSChannelParser->es_buffer[i].main_track = -1;
		pTSChannelParser->es_buffer[i].input_bytes = 0;
		pTSChannelParser->es_buffer[i].stream_id = 0;
	}
	pTSChannelParser->ts_streams.num_stream = 0;
	pTSChannelParser->es_streams.num_stream = 0;
	pTSChannelParser->av_streams.num_stream = 0;
	memset( pTSChannelParser->ts_streams.ts_element, 0, pTSChannelParser->max_stream_num*sizeof(TS_ELEMENT) );
	memset( pTSChannelParser->es_streams.es_element, 0, pTSChannelParser->max_stream_num*sizeof(ES_ELEMENT) );
	memset( pTSChannelParser->av_streams.av_element, 0, pTSChannelParser->max_stream_num*sizeof(AV_ELEMENT) );
	pTSChannelParser->avinf[0] = 0x0;
	pTSChannelParser->avinf_size = 0;
	pTSChannelParser->total_bytes = 0;
	pTSChannelParser->total_video_num = 0;
	pTSChannelParser->total_audio_num = 0;
	pTSChannelParser->found_video_num = 0;
	pTSChannelParser->found_audio_num = 0;

	pTSChannelParser->channel_list.channel_num = 0;
	pTSChannelParser->program_list.program_num = 0;
	pTSChannelParser->program_list.flag = 0;
	pTSChannelParser->program_list.drop_program_num = 0;
}


void SetupTSChannelDump( TS_CHANNEL_PARSER *pTSChannelParser, 
					 DUMP pfnPidDump,     void* pfnPidDumpContext,
					 DUMP pfnAVInfDump,   void* pfnAVInfDumpContext,
					 DUMP pfnStreamDump,  void* pStreamDumpContext )
{
	pTSChannelParser->dumper.pid_dumper = pfnPidDump;
	pTSChannelParser->dumper.pid_dumper_context = pfnPidDumpContext;
	pTSChannelParser->dumper.avinf_dumper = pfnAVInfDump;
	pTSChannelParser->dumper.avinf_dumper_context = pfnAVInfDumpContext;

	pTSChannelParser->dumper.stream_dumper = pfnStreamDump;
	pTSChannelParser->dumper.stream_dumper_context = pStreamDumpContext;
}

int StartTSChannelParser( TS_CHANNEL_PARSER *pTSChannelParser, TUNE* pTune )
{
	int i, ret=0;
	PID_ITEM pid_inf;
	pTSChannelParser->state = 0;
	pTSChannelParser->tune = *pTune;
	
	pid_inf.pid = 0;
	pid_inf.type = 0;
	pid_inf.service = 0;
	pTSChannelParser->dumper.pid_dumper( pTSChannelParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );

	pid_inf.pid =  0x1ffb;
	pid_inf.type = 0;//PID_TYPE_PSI;
	pid_inf.service = 0;
	pTSChannelParser->dumper.pid_dumper( pTSChannelParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );

	for ( i = 0; i<pTSChannelParser->max_stream_num; i++ )
		pTSChannelParser->es_buffer[i].stream_id = 0;

	return ret;
}

void StopTSChannelParser( TS_CHANNEL_PARSER *pTSChannelParser )
{
	pTSChannelParser->state = 0;
}

void SetupTSChannelTune( TS_CHANNEL_PARSER *pTSChannelParser, TUNE *pTune )
{
	pTSChannelParser->tune = *pTune;
}

int IsTSChannelInfoReady( TS_CHANNEL_PARSER *pTSChannelParser )
{
	return ( pTSChannelParser->state & CHANNEL_INFO_READY );
}

int GetTSProgramScanNum( TS_CHANNEL_PARSER *pTSChannelParser )
{
	return pTSChannelParser->program_list.program_num;
}

int GetTSChannelScanState( TS_CHANNEL_PARSER *pTSChannelParser )
{
	if ( pTSChannelParser->state & CHANNEL_INFO_READY )
		return 2;
	return 0;
}

int TSChannelScanNum( TS_CHANNEL_PARSER *pTSChannelParser )
{
	return pTSChannelParser->channel_list.channel_num;
}

CHANNEL_LIST* GetTSChannelScanList( TS_CHANNEL_PARSER *pTSChannelParser )
{
	return &pTSChannelParser->channel_list;
}

TUNE_LIST *GetTSTuneScanList( TS_CHANNEL_PARSER *pTSChannelParser )
{
	return &pTSChannelParser->tune_list;

}

int TSProcessInfo( TS_FILTER* pTSFilter, uint8_t* pData );
int PushTSChannelPacketParser( TS_CHANNEL_PARSER *pTSChannelParser, uint8_t* pData, int nSize )
{
	int used_bytes = 0, size = nSize;
	uint8_t* data = pData;

	while ( size >= TS_PACKET_LENGTH )
	{
		int ret;

		if ( *data != TS_SYNC ) //sync header
		{
			for (  ; size>TS_PACKET_LENGTH && *(data) != TS_SYNC ; size--, data++ )
				used_bytes++;

			if ( size < TS_PACKET_LENGTH )
			{
				SageLog(( _LOG_ERROR, 3, TEXT("ERROR: TS SYNC header is lost!") ));
				return used_bytes;
			}
		}

		ret = TSProcessInfo( pTSChannelParser->ts_filter, data  ); //must to be 188 bytes of data

		data += TS_PACKET_LENGTH;
		size -= TS_PACKET_LENGTH;
		used_bytes += TS_PACKET_LENGTH;
	}

	return used_bytes;
}

static int InitScanData(  TS_CHANNEL_PARSER *pTSChannelParser, int nStreamType, int nSubType )
{
	pTSChannelParser->stream_format = nStreamType;
	pTSChannelParser->sub_format = nSubType;
	pTSChannelParser->tune_list.stream_format = nStreamType;
	pTSChannelParser->tune_list.sub_format = nSubType;
	pTSChannelParser->channel_list.stream_format = nStreamType;
	pTSChannelParser->channel_list.sub_format = nSubType;
	pTSChannelParser->program_list.stream_format = nStreamType;
	pTSChannelParser->program_list.sub_format = nSubType;
	//pTSChannelParser->maxium_psi_checking_bytes = MaxPSICheckBytes( nStreamType, nSubType );
	//pTSChannelParser->psi_timeout = MaxPSICheckTimeOut( nStreamType, nSubType );
	pTSChannelParser->channel_list.channel_num = 0;
	pTSChannelParser->program_list.program_num = 0;
	pTSChannelParser->program_list.drop_program_num = 0;
	pTSChannelParser->program_list.flag = 0;

	if ( nStreamType == DVB_STREAM )
	{
		SageLog((  _LOG_ERROR, 3, "DVB Stream isn't support yet" ));
	} else
	if ( nStreamType == ATSC_STREAM )
	{
		if ( nSubType == TERRESTRIAL ) //ATSC
		{
			if ( pTSChannelParser->channel_list.channel == NULL )
			{
				pTSChannelParser->channel_list.total_list_num = MAX_ATSC_CHANNEL_NUM;
				pTSChannelParser->channel_list.channel = SAGETV_MALLOC( sizeof(CHANNEL_DAT)*pTSChannelParser->channel_list.total_list_num );
				pTSChannelParser->program_list.total_list_num = MAX_ATSC_CHANNEL_NUM+10;
				pTSChannelParser->program_list.program = SAGETV_MALLOC( sizeof(PROGRAM_DAT)*pTSChannelParser->program_list.total_list_num );
				return 1;
			}

		} else
		if ( nSubType == CABLE ) //QAM
		{
			if ( pTSChannelParser->channel_list.channel == NULL )
			{
				pTSChannelParser->channel_list.total_list_num = MAX_QAM_CHANNEL_NUM ;
				pTSChannelParser->channel_list.channel = SAGETV_MALLOC( sizeof(CHANNEL_DAT)*pTSChannelParser->channel_list.total_list_num );
				pTSChannelParser->program_list.total_list_num = MAX_QAM_CHANNEL_NUM +10;
				pTSChannelParser->program_list.program = SAGETV_MALLOC( sizeof(PROGRAM_DAT)*pTSChannelParser->program_list.total_list_num );
				return 1;
			}
		} 
	}

	return 0;
}


static int CheckTsId( TS_CHANNEL_PARSER *pTSChannelParser, int nTsid )
{
	TUNE *tune = &pTSChannelParser->tune;
	if ( tune->stream_format == ATSC_STREAM )
	{
		if ( tune->u.atsc.tsid != 0 )
		{
			if ( tune->u.atsc.tsid == nTsid )
				return 1;
			else 
				return -1;
		}
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

	return 0;
}

#define CA_DESCRIPTOR	0x09
uint8_t* GetDescriptor( const uint8_t *pData, int Bytes, uint8_t Tag, int *pLength );
int HasCADesc( uint8_t *pData, int nBytes )
{
	uint8_t* desc_ptr;
	int desc_len;
	if ( ( desc_ptr = GetDescriptor( pData, nBytes, CA_DESCRIPTOR, &desc_len ) )!= NULL && desc_len > 0 )
		return 1;
	return 0;

}

static char* CAInfo( uint16_t CAPid, uint16_t CAID )
{
	static char _buf[16];
	snprintf( _buf, sizeof(_buf), "CA pid:0x%04x", CAPid );
	return _buf;
}


int CheckChannelTrackAVInfRead( TS_CHANNEL_PARSER *pTSChannelParser )
{
	if ( pTSChannelParser->total_video_num && pTSChannelParser->found_video_num >= pTSChannelParser->total_video_num )
		if ( pTSChannelParser->total_audio_num && pTSChannelParser->found_audio_num >= pTSChannelParser->total_audio_num )
			return 1;

	if ( pTSChannelParser->total_bytes > pTSChannelParser->max_check_limit )
		return 1;

	if ( pTSChannelParser->total_bytes > pTSChannelParser->max_check_limit/2 )
	{
		int i, empty_video_track_num = 0, empty_audio_track_num = 0;
		for ( i = 0; i<pTSChannelParser->ts_streams.num_stream; i++ )
		{
			if ( pTSChannelParser->es_buffer[i].input_bytes == 0 )
			{
				if ( pTSChannelParser->ts_streams.ts_element[i].content_type == VIDEO_DATA )
					empty_video_track_num++;
				else
				if ( pTSChannelParser->ts_streams.ts_element[i].content_type == AUDIO_DATA )
					empty_audio_track_num++;
			}
		}

		if ( pTSChannelParser->found_video_num + empty_video_track_num>= pTSChannelParser->total_video_num )
			if ( pTSChannelParser->found_audio_num + empty_audio_track_num >= pTSChannelParser->total_audio_num )
			return 1;
	}
	return 0;
}

int AVElmntInfo( AV_ELEMENT *pAVElmnt, char* pBuffer, int nSize );
uint32_t GetAudioRate( AV_ELEMENT *pAVElmnt );
int TagInfo( int nOutputFormat, TS_ELEMENT *pTSElmnt, ES_ELEMENT *pESElmnt, char* pBuffer, int nSize );

static void TagMainTrack( TS_CHANNEL_PARSER *pTSChannelParser )
{
	int i, video_main = -1, audio_main = -1;
	uint32_t bit_rate = 0;
	for ( i = 0; i<pTSChannelParser->ts_streams.num_stream; i++ )
	{
		if ( pTSChannelParser->av_streams.av_element[i].content_type == VIDEO_DATA )
		{
			if ( video_main == -1 ) 
				video_main = i;
			else
			if ( pTSChannelParser->es_buffer[i].input_bytes > pTSChannelParser->es_buffer[video_main].input_bytes )
				video_main = i;
			bit_rate += GetAudioRate( &pTSChannelParser->av_streams.av_element[i]  );
		}
		else
		if ( pTSChannelParser->av_streams.av_element[i].content_type == AUDIO_DATA )
		{
			if ( audio_main == -1 ) 
				audio_main = i;
			else
			if ( pTSChannelParser->es_buffer[i].input_bytes > pTSChannelParser->es_buffer[audio_main].input_bytes )
				audio_main = i;
			bit_rate += GetAudioRate( &pTSChannelParser->av_streams.av_element[i]  );
		}
	}
	if ( video_main >= 0 )
		pTSChannelParser->es_buffer[video_main].main_track = 1;
	if ( audio_main >= 0 )
		pTSChannelParser->es_buffer[audio_main].main_track = 1;

	pTSChannelParser->bit_rate = bit_rate;
}


static int GetTrackAVInf( TS_CHANNEL_PARSER *pTSChannelParser, int nIndex, char* pBuffer, int nSize )
{
	int pos = 0;
	char *p = pBuffer;
	TS_ELEMENT *ts_elmnt = &pTSChannelParser->ts_streams.ts_element[nIndex];
	AV_ELEMENT *av_elmnt = &pTSChannelParser->av_streams.av_element[nIndex];
	ES_BUFFER_  *es_buffer = &pTSChannelParser->es_buffer[nIndex];
	if ( nSize > pos ) pos += snprintf( p+pos, nSize-pos, "[" );
	pos += AVElmntInfo( av_elmnt, p+pos, nSize-pos );
	if ( es_buffer->main_track )
		if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "main=yes;"  );
	pos += TagInfo( 1, ts_elmnt, NULL, p+pos, nSize-pos );
	if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "index=%d;", nIndex );
	if ( nSize > pos+2 ) { p[pos++] = ']'; p[pos++] = ';';  } //terminator
	return pos;
}

int GetChannelAVInf( TS_CHANNEL_PARSER *pTSChannelParser, char* pBuffer, int nSize )
{
	char *p = pBuffer;
	int pos = 0;
	if ( !CheckChannelTrackAVInfRead( pTSChannelParser ) )
		return 0;

	TagMainTrack( pTSChannelParser );
	pos += snprintf( p+pos, nSize-pos, "AV-INF|f=" );
	if ( pTSChannelParser->found_video_num == 0 && pTSChannelParser->found_audio_num == 0 )
	{
		pos += snprintf( pBuffer, nSize-pos, "NO-AV-TS;" );
		return pos;
	}
	else
	{
		int i;
		pos += snprintf( p+pos, nSize-pos, "MPEG2-TS;" );
		if ( nSize > pos && pTSChannelParser->bit_rate > 0 )
			pos += snprintf( p+pos, nSize-pos, "br=%d;", pTSChannelParser->bit_rate );
		if ( pTSChannelParser->total_video_num == 0 )
			pos += snprintf( p+pos, nSize-pos, "audioonly=1;" );
		else
		if ( pTSChannelParser->total_audio_num == 0 )
			pos += snprintf( p+pos, nSize-pos, "videoonly=1;" );


		for ( i = 0; i<pTSChannelParser->ts_streams.num_stream; i++ )
		{
			if ( pTSChannelParser->av_streams.av_element[i].content_type == VIDEO_DATA )
				pos += GetTrackAVInf( pTSChannelParser, i, p+pos, nSize-pos );
			if ( pTSChannelParser->av_streams.av_element[i].content_type == AUDIO_DATA )
				pos += GetTrackAVInf( pTSChannelParser, i, p+pos, nSize-pos );
		}
	}
	if ( pos < nSize ) p[pos]= 0x0;
	return pos;
}

