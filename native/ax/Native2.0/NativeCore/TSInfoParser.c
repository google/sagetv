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
#include "TSInfoParser.h"

static int CheckTsId( TS_INFO_PARSER *pTSInfoParser, int nTsid );
static int CheckProgramId( TS_INFO_PARSER *pTSInfoParser, int nProgramId, int nTsid );
static char* CAInfo( uint16_t CAPid, uint16_t CAID );
uint32_t TVVideoFormat( uint8_t StreamType, uint8_t* pDesc, int nBytes );
static char* _print_es_descriptor_( uint16_t pid, uint8_t* p, int bytes );
static int _tune_data_( TUNE* pTune, char* buf, int buf_size );
static int GetTrackAVInf( TS_INFO_PARSER *pTSInfoParser, int nIndex, char* pBuffer, int nSize );
static int GetMainAVIndex( TS_INFO_PARSER *pTSInfoParser, int *pVideoIndex, int *pAudioIndex );
static uint32_t ElapseSeconds( TS_INFO_PARSER *pTSInfoParser );
static uint32_t ElapseSecondsByPCR( TS_INFO_PARSER *pTSInfoParser );

static int PATDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_INFO_PARSER *pTSInfoParser = (TS_INFO_PARSER*)pContext;
	PAT_DATA  *pPatData  = (PAT_DATA*)pData;
	TS_PAT    *pPat      = (TS_PAT*)pPatData->pat_table;
	int16_t pat_index = pPatData->pat_index;
	uint16_t tsid  = pPat->tsid;
	int program_num = pPat->total_program_number;
	int i, tsid_locked=0, program_found=-1;

	ASSERT( pContext != NULL );
	ASSERT( nSize == sizeof(PAT_DATA) );
	
	//SageLog(( _LOG_TRACE, 3, TEXT("****************PAT ****************** %x"), pTSInfoParser->state ) );

	if ( (pTSInfoParser->state & PROGRAM_FOUND) ) 
	{
		return 0;
	}

	SageLog(( _LOG_TRACE, 3, TEXT("PAT-%d:Program (total channel:%d, tsid:%d )"), pat_index, program_num, tsid ));
	for ( i = 0; i<program_num; i++ )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("\tPMT pid:0x%04x program:%04d"), pPat->program_pid[i], pPat->program_number[i] ));
		if ( CheckProgramId( pTSInfoParser, pPat->program_number[i], tsid )>0 )
			program_found = i;
	}
	for ( i = 0; i<program_num; i++ )
	{
		if ( pTSInfoParser->dumper.pid_dumper )
		{
			PID_ITEM pid_inf;
			pid_inf.pid = pPat->program_pid[i];
			pid_inf.type = PID_TYPE_PROGRAM;
			pid_inf.service = 0;
			pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );
		}		
	}

	if ( program_found >= 0 )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("program %d is locked (%d)"), pPat->program_number[program_found], i));
		if ( !(pTSInfoParser->state & PROGRAM_FOUND )  )
		{
			pTSInfoParser->state |= PROGRAM_FOUND;
			if ( pTSInfoParser->dumper.pid_dumper )
			{
				PID_ITEM pid_inf;
				pid_inf.pid = pPat->program_pid[program_found];
				pid_inf.type = PID_TYPE_PROGRAM;
				pid_inf.service = tsid;
				pid_inf.data =  pPat->program_number[program_found];
				pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );
			}					
		}
	}

	tsid_locked = CheckTsId( pTSInfoParser, tsid )>0;
	if ( tsid_locked )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("tsid %d is locked (%d)"), tsid, i));
		pTSInfoParser->state |= TSID_LOCKED;
		return 1;
	}
	return 0;
}

static int PMTDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_INFO_PARSER *pTSInfoParser = (TS_INFO_PARSER*)pContext;
	PMT_DATA  *pPmtData  = (PMT_DATA*)pData;
	TS_PMT    *pPmt      = (TS_PMT*)pPmtData->pmt_table;
	//TS_SECTION *pPmtSection = pPmtData->pmt_section;
	int channel = pPmtData->channel;
	//int channel_num = pPmtData->total_channel_num;
	int stream_num  = pPmt->total_stream_number;
	int i;

	ASSERT( pContext != NULL );
	ASSERT( (nSize == sizeof(PMT_DATA)) );

	if ( stream_num == 0 )		return 1;

	if ( (pPmtData->update_flag & PMT_UPDATED) ) //skip unchanged PMT
	{
		SageLog(( _LOG_TRACE, 3, TEXT("\tChannel:%d (tsid:%d) PMT pid:0x%04x Program:%04d PCR pid:0x%04x %s."),  channel, 
			pPmtData->tsid, pPmtData->pid, pPmt->program_number, pPmt->pcr_pid, pPmt->ca_pid ? CAInfo( pPmt->ca_pid, pPmt->ca_id) :"" ));
		for ( i = 0; i< stream_num; i++ )
		{
			SageLog(( _LOG_TRACE, 3,  TEXT("\t\tType:0x%02x Pid:0x%03x"), pPmt->stream_type[i], pPmt->stream_pid[i] ));
		}

		//PMT updated and repersent, we need reselect channel to change filter
		if ( ( pPmtData->update_flag & PMT_REPEAT ))
		{
			SageLog(( _LOG_TRACE, 3,  TEXT("PMT Updated, reselect channel" )));
		}
	}

	if ( (pTSInfoParser->state & PROGRAM_FOUND) && 
		 (pTSInfoParser->state & CHANNEL_FOUND) == 0 ) 
	{
		if ( CheckProgramId( pTSInfoParser, pPmt->program_number, pPmtData->tsid )>0 ) 
		{
			PID_ITEM pid_inf;
			int len;
			pTSInfoParser->ts_streams.num_stream = 0;
			for ( i = 0; i< stream_num ; i++ )
			{
				if ( IsVideoType( pPmt->stream_type[i] ) || pPmt->stream_type[i] == PRIVATE_0x80_STREAM_TYPE  )  //QAM stream has DC-II video that uses 0x80 for video
				{
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].pid = pPmt->stream_pid[i];
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].type =  pPmt->stream_type[i];
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].content_type = VIDEO_DATA;
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].channel_index = 
						                                 (uint8_t)pTSInfoParser->ts_streams.num_stream;
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].format_fourcc = 
											TVVideoFormat( pPmt->stream_type[i],
														 pPmt->stream_desc[i].desc_ptr, 
														 pPmt->stream_desc[i].desc_bytes  );
					len = pPmt->stream_desc[i].desc_bytes;
					if ( len > sizeof(pTSInfoParser->ts_streams.ts_element[0].desc) )
						len = sizeof(pTSInfoParser->ts_streams.ts_element[0].desc);
					memcpy( pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].desc, 
						pPmt->stream_desc[i].desc_ptr, len );
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].desc_len = len;
					_print_es_descriptor_( pPmt->stream_pid[i], 
						                   pPmt->stream_desc[i].desc_ptr, 
							               pPmt->stream_desc[i].desc_bytes   ); //ZQ Debug
					pTSInfoParser->total_video_num++;

					if ( pTSInfoParser->dumper.pid_dumper )
					{
						pid_inf.pid = pPmt->stream_pid[i];
						pid_inf.type = PID_TYPE_VIDEO;
						pid_inf.service = pPmt->stream_type[i];
						pid_inf.data = 0;
						pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );
					}
					
					if ( ++pTSInfoParser->ts_streams.num_stream >= pTSInfoParser->ts_streams.total_streams-1 )
						break;

				} else
				if ( IsAudioType( pPmt->stream_type[i] ) )
				{
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].pid = pPmt->stream_pid[i];
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].type =  pPmt->stream_type[i];
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].content_type = AUDIO_DATA;
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].channel_index = 
						                                  (uint8_t)pTSInfoParser->ts_streams.num_stream;
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].format_fourcc =     
										AudioFormatByStreamType( pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].type );   
					if ( pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].format_fourcc == 0 )                    
						pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].format_fourcc = 
										HasAudioDesc(	pPmt->stream_desc[i].desc_ptr, 
														pPmt->stream_desc[i].desc_bytes  );
				
														 
					len = pPmt->stream_desc[i].desc_bytes;
					if ( len > sizeof(pTSInfoParser->ts_streams.ts_element[0].desc) )
						len = sizeof(pTSInfoParser->ts_streams.ts_element[0].desc);
					memcpy( pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].desc, 
						pPmt->stream_desc[i].desc_ptr, len );
					pTSInfoParser->ts_streams.ts_element[pTSInfoParser->ts_streams.num_stream].desc_len = len;
					_print_es_descriptor_( pPmt->stream_pid[i], 
						                   pPmt->stream_desc[i].desc_ptr, 
							               pPmt->stream_desc[i].desc_bytes   ); //ZQ Debug
					pTSInfoParser->total_audio_num++;

					if ( pTSInfoParser->dumper.pid_dumper )
					{
						pid_inf.pid = pPmt->stream_pid[i];
						pid_inf.type = PID_TYPE_AUDIO;
						pid_inf.service = pPmt->stream_type[i];
						pid_inf.data = 0;
						pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );
					}

					if ( ++pTSInfoParser->ts_streams.num_stream >= pTSInfoParser->ts_streams.total_streams-1 )
						break;
				} 
			}
			if ( pTSInfoParser->dumper.pid_dumper )
			{
				
				pTSInfoParser->ts_streams.pcr_pid = pPmt->pcr_pid;				
				pid_inf.pid = pPmt->pcr_pid;
				pid_inf.type = PID_TYPE_PCR;
				pid_inf.service = 0;
				pid_inf.data = 0;
				pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );
			
				/*
				pTSInfoParser->ts_streams.ca_pid = pPmt->ca_pid;
				pid_inf.pid = pPmt->pcr_pid;
				pid_inf.type = PID_TYPE_CA;
				pid_inf.service = 0;
				pid_inf.data = 0;
				pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );
				*/
				
			}
			SetupChannelFilter( pTSInfoParser->ts_filter, &pTSInfoParser->ts_streams  );

			pTSInfoParser->state |= CHANNEL_FOUND;
		}
	}
	return 1;
}

static int PSIPidDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_INFO_PARSER *pTSInfoParser = (TS_INFO_PARSER*)pContext;
	if ( (pTSInfoParser->state & CHANNEL_FOUND ) || pTSInfoParser->dumper.pid_dumper == NULL )
		return 0;
	//relay to upper
	return pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, pData, nSize );
}

static int UpdateTuneData( TUNE *pTune, int nData1, int nData2, int nData3, int nData4  )
{
	if ( pTune->stream_format == ATSC_STREAM )
	{
		if ( pTune->u.atsc.minor_num != 0 && pTune->u.atsc.major_num == nData1 && pTune->u.atsc.minor_num == nData2  )
		{
			if ( pTune->u.atsc.program_id != nData4 )
			{
				SageLog(( _LOG_TRACE, 3,  TEXT("\tATSC channel program id was changed! %d=>%d(%d)"),
					                              pTune->u.atsc.program_id, nData4, pTune->tune_string_type  ));
			}
			pTune->u.atsc.tsid = nData3;
			pTune->u.atsc.program_id = nData4;  //program id
			pTune->tune_string_type++;
			SageLog(( _LOG_TRACE, 3,  TEXT("\tATSC channel major:%d minor:%d tsid:%d program:%d (tune type%d)"),
				pTune->u.atsc.major_num, pTune->u.atsc.minor_num,	pTune->u.atsc.tsid, pTune->u.atsc.program_id, 
				pTune->tune_string_type ));

			{
				char buf[100];
				_tune_data_( pTune, buf, sizeof(buf) );
				SageLog(( _LOG_TRACE, 3, TEXT("\tTune Channel  %s "), buf ));

			}
			return 1;

		} else //QAM channel may have a trouble, they change channel information in PSI very often, we rely on program to get channel
		{
			if ( ( pTune->u.atsc.major_num == 0 || pTune->u.atsc.major_num == 1008 ) && nData1 > 0 )
			{
				if ( ( pTune->u.atsc.program_id && pTune->u.atsc.program_id == nData4 ) )
				{
					SageLog(( _LOG_TRACE, 3,  TEXT("\tQAM channel %d-%d and program:%d was not matched!, based on program (%d). (type:%d)"),
					                                      pTune->u.atsc.major_num, pTune->u.atsc.minor_num, pTune->u.atsc.program_id, nData4, pTune->tune_string_type));
					pTune->u.atsc.tsid = nData3;
					pTune->tune_string_type++;
					pTune->u.atsc.major_num = nData1;
					pTune->u.atsc.minor_num = nData2;
					SageLog(( _LOG_TRACE, 3,  TEXT("\tRemap QAM channel major:%d minor:%d tsid:%d program:%d (%d)"),
						pTune->u.atsc.major_num, pTune->u.atsc.minor_num,	pTune->u.atsc.tsid, pTune->u.atsc.program_id, 
						pTune->tune_string_type ));

					{
						char buf[100];
						_tune_data_( pTune, buf, sizeof(buf) );
						SageLog(( _LOG_TRACE, 3, TEXT("\tTune Channel  %s "), buf ));
					}
					return 1;
				}

			} 
		}
			
	} else
	if ( pTune->stream_format == DVB_STREAM )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("DVB Stream code is not done yet!")));
		if ( pTune->u.dvb.onid == nData1 && pTune->u.dvb.tsid == nData2 && pTune->u.dvb.sid == nData3 )
		{
		}
	} else
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: Unknow stream format or stream format is set yet!")));
	}
	return 0;
}

static int ChannelInfoDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_INFO_PARSER *pTSInfoParser = (TS_INFO_PARSER*)pContext;
	CHANNEL_DATA *channel_data = (CHANNEL_DATA*)pData;
	int i, ret;

	if ( (pTSInfoParser->state & (CHANNEL_FOUND|PROGRAM_FOUND|CHANNEL_INFO_FOUND) ) && channel_data->update_flag == 0 )
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
							pTSInfoParser->tune.u.atsc.major_num, pTSInfoParser->tune.u.atsc.minor_num, pTSInfoParser->tune.stream_format ));

			ret = UpdateTuneData( &pTSInfoParser->tune, 
							channel_data->u.atsc[i].major_num, channel_data->u.atsc[i].minor_num, 
				            channel_data->u.atsc[i].tsid, channel_data->u.atsc[i].program_id );
			if ( ret > 0 )
			{
				SageLog(( _LOG_TRACE, 3, TEXT("************** Blast PAT PMT ******************" )));
				if ( (pTSInfoParser->state & PROGRAM_FOUND) == 0 )
					BlastPatTable( pTSInfoParser->ts_filter, pTSInfoParser->tune.u.atsc.program_id, pTSInfoParser->tune.u.atsc.tsid );
				if ( (pTSInfoParser->state & CHANNEL_FOUND) == 0  )	
					BlastPmtTable( pTSInfoParser->ts_filter, pTSInfoParser->tune.u.atsc.program_id, pTSInfoParser->tune.u.atsc.tsid );
			
			}

		}

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
				if ( pTSInfoParser->tune.u.dvb.tsid && 
					pTSInfoParser->tune.u.dvb.tsid != channel_data->u.dvb[i].tsid )
					SageLog(( _LOG_TRACE, 3, TEXT("DVB current service is changed (%d -> %d) "),
									pTSInfoParser->tune.u.dvb.tsid, channel_data->u.dvb[i].tsid )); 
				pTSInfoParser->tune.u.dvb.tsid = channel_data->u.dvb[i].tsid;
			}

			ret = UpdateTuneData( &pTSInfoParser->tune, 
				            channel_data->u.dvb[i].onid, channel_data->u.dvb[i].tsid, channel_data->u.dvb[i].sid, 0 );
			if ( ret > 0 )
				pTSInfoParser->state |= CHANNEL_INFO_FOUND;
		}
		
	}
	return 0;
}


//ZQ 1.
static int StreamDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_INFO_PARSER *pTSInfoParser = (TS_INFO_PARSER*)pContext;
	STREAM_DATA   *stream_data   = (STREAM_DATA *)pData;
	TS_ELEMENT    *ts_elment     = stream_data->ts_elment;
	int index = stream_data->ts_elment->channel_index;
	ES_BUFFER *pESBuffer = &pTSInfoParser->es_buffer[index];
	int			  in_bytes;
	uint8_t *in_ptr;

	//fill data to send into dumper
	in_bytes = stream_data->bytes;
	in_ptr   = stream_data->data_ptr;

	pESBuffer->input_bytes += in_bytes;
	pTSInfoParser->total_bytes += in_bytes;

	if ( CheckTrackAVInfRead( pTSInfoParser ) )
	{
		int VideoIndex, AudioIndex;
		pTSInfoParser->state |= AVINF_READY;
		pTSInfoParser->avinf_size =
		GetAVInfo( pTSInfoParser, pTSInfoParser->avinf, sizeof(pTSInfoParser->avinf) );
		if ( pTSInfoParser->dumper.pid_dumper && GetMainAVIndex( pTSInfoParser, &VideoIndex, &AudioIndex ) )
		{
			PID_ITEM pid_inf;
			if ( pTSInfoParser->av_streams.av_element[VideoIndex].content_type == VIDEO_DATA )
			{
				pid_inf.pid = pTSInfoParser->ts_streams.ts_element[VideoIndex].pid;
				pid_inf.type = PID_TYPE_VIDEO;
				pid_inf.data = 1;  //mark it's main video
				pid_inf.service = pTSInfoParser->ts_streams.ts_element[VideoIndex].type;
				pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );
			}
			if ( pTSInfoParser->av_streams.av_element[AudioIndex].content_type == AUDIO_DATA )
			{
				pid_inf.pid = pTSInfoParser->ts_streams.ts_element[AudioIndex].pid;
				pid_inf.type = PID_TYPE_AUDIO;
				pid_inf.data = 1;  //mark it's main video
				pid_inf.service = pTSInfoParser->ts_streams.ts_element[AudioIndex].type;
				pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );
			}
		}
		if ( pTSInfoParser->dumper.avinf_dumper )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("AVINF is ready, Post message (%d)."), pTSInfoParser->avinf_size ));
			pTSInfoParser->dumper.avinf_dumper( pTSInfoParser->dumper.avinf_dumper_context,
												pTSInfoParser->avinf, pTSInfoParser->avinf_size );

		}
	}

	if ( pTSInfoParser->av_streams.av_element[index].format_fourcc )
		return 1;

	if (  stream_data->group_start )
	{   
		PES pes={0};
		if ( IsPESHeaderOfTS( (uint8_t)pESBuffer->stream_id, in_ptr, in_bytes ) && //start a new es block
			 ReadPESHeader( in_ptr, in_bytes, &pes ) > 0 )
		{
			if ( pESBuffer->bytes > 0 )
			{
				int ret = AnylyzeAVElement( &pTSInfoParser->av_streams.av_element[index], &pTSInfoParser->es_streams.es_element[index],
										ts_elment, pESBuffer->buffer, pESBuffer->bytes, SAGETV_TV_RECORDING );
				if ( ret )
				{
					pESBuffer->stream_id = pes.stream_id;
					pESBuffer->pes = pes;
					_prints_av_elmnt( &pTSInfoParser->av_streams.av_element[index], index, pTSInfoParser->total_bytes );
					if ( pTSInfoParser->av_streams.av_element[index].content_type == VIDEO_DATA ) 
						pTSInfoParser->found_video_num++;
					else
					if ( pTSInfoParser->av_streams.av_element[index].content_type == AUDIO_DATA ) 
						pTSInfoParser->found_audio_num++;
			
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

			int ret = AnylyzeAVElement( &pTSInfoParser->av_streams.av_element[index], &pTSInfoParser->es_streams.es_element[index],
									ts_elment, pESBuffer->buffer, pESBuffer->bytes, SAGETV_TV_RECORDING );
			if ( ret )
			{
				_prints_av_elmnt( &pTSInfoParser->av_streams.av_element[index], index, pTSInfoParser->total_bytes );
				if ( pTSInfoParser->av_streams.av_element[index].content_type == VIDEO_DATA ) 
					pTSInfoParser->found_video_num++;
				else
				if ( pTSInfoParser->av_streams.av_element[index].content_type == AUDIO_DATA ) 
					pTSInfoParser->found_audio_num++;

				return 1;
			}
			pESBuffer->bytes = 0;
		}
		memcpy( pESBuffer->buffer+pESBuffer->bytes, in_ptr, in_bytes );
		pESBuffer->bytes += in_bytes;
	}

	return 1;
}
#define PTS_OF_1SEC  (MPEG_TIME_DIVISOR) 
static int PCRDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_INFO_PARSER *pTSInfoParser = (TS_INFO_PARSER*)pContext;
	PCR_DATA *pcr_data=(PCR_DATA *)pData;

	pTSInfoParser->pcr_sec = (uint32_t)(pcr_data->pcr/(27000000));
	if ( pTSInfoParser->pcr_sec_start == 0 )
	{
		pTSInfoParser->pcr_sec_start = pTSInfoParser->pcr_sec;
	}
		
	return 1;
}
static uint32_t ElapseSecondsByPCR( TS_INFO_PARSER *pTSInfoParser )
{
	if ( pTSInfoParser->pcr_sec_start == 0 )
		return 0;
	return  pTSInfoParser->pcr_sec - pTSInfoParser->pcr_sec_start;
}

static int SystemTimeDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_INFO_PARSER *pTSInfoParser = SAGETV_MALLOC( sizeof(TS_INFO_PARSER) );
	TIME_DATA *time_data = (TIME_DATA*)pData;

	if ( time_data->gps_sec )
		pTSInfoParser->utc_sec = time_data->gps_sec + 315993600;  //GPS start 1/6 0:0:0 1980, GMT UTC start 1/1 0:0:0 1970
	else
		pTSInfoParser->utc_sec = time_data->utc_sec;
		
	if ( pTSInfoParser->utc_sec_start == 0 )
		pTSInfoParser->utc_sec_start = pTSInfoParser->utc_sec;
	
	if ( pTSInfoParser->dumper.time_dumper != NULL )
		pTSInfoParser->dumper.time_dumper( pTSInfoParser->dumper.time_dumper_context, &pTSInfoParser->utc_sec_start, sizeof(pTSInfoParser->utc_sec_start) );
		
	return 1;
}

static uint32_t ElapseSeconds( TS_INFO_PARSER *pTSInfoParser )
{
	if ( pTSInfoParser->utc_sec_start == 0 )
		return 0;
	return  pTSInfoParser->utc_sec-pTSInfoParser->utc_sec_start;
}


TS_INFO_PARSER* CreateTSInfoParser( int nMaxStreamNum, int nStreamFormat, int nSubFormat )
{
	int i;
	TS_INFO_PARSER *pTSInfoParser = SAGETV_MALLOC( sizeof(TS_INFO_PARSER) );
	pTSInfoParser->ts_filter = CreateTSFilter( DEFAULT_PAT_NUM, DEFAULT_PMT_NUM, nStreamFormat, nSubFormat ); //ATSC_STREAM, TERRESTRIAL;
	pTSInfoParser->max_stream_num = nMaxStreamNum;
	pTSInfoParser->ts_streams.ts_element = SAGETV_MALLOC( nMaxStreamNum*sizeof(TS_ELEMENT) );
	pTSInfoParser->ts_streams.total_streams = nMaxStreamNum;
	pTSInfoParser->es_streams.es_element = SAGETV_MALLOC( nMaxStreamNum*sizeof(ES_ELEMENT) );
	pTSInfoParser->es_streams.total_streams = nMaxStreamNum;
	pTSInfoParser->av_streams.av_element = SAGETV_MALLOC( nMaxStreamNum*sizeof(AV_ELEMENT) );
	pTSInfoParser->av_streams.total_streams = nMaxStreamNum;
	pTSInfoParser->es_buffer = SAGETV_MALLOC( nMaxStreamNum*sizeof(ES_BUFFER) );
	for ( i = 0; i<nMaxStreamNum; i++ )
	{
		pTSInfoParser->es_buffer[i].buffer = SAGETV_MALLOC( TMP_ES_BUFFER_SIZE );
		pTSInfoParser->es_buffer[i].buffer_size = TMP_ES_BUFFER_SIZE;
	}


	pTSInfoParser->ts_filter->dumper.pat_dumper = (DUMP)PATDumper;
	pTSInfoParser->ts_filter->dumper.pat_dumper_context = pTSInfoParser;
	pTSInfoParser->ts_filter->dumper.pmt_dumper = (DUMP)PMTDumper;
	pTSInfoParser->ts_filter->dumper.pmt_dumper_context = pTSInfoParser;
	pTSInfoParser->ts_filter->dumper.pcr_dumper = (DUMP)PCRDumper;
	pTSInfoParser->ts_filter->dumper.pcr_dumper_context = pTSInfoParser;
	pTSInfoParser->ts_filter->dumper.stream_dumper = (DUMP)StreamDumper;
	pTSInfoParser->ts_filter->dumper.stream_dumper_context = pTSInfoParser;

	pTSInfoParser->ts_filter->psi_parser->dumper.channel_info_dumper  =	(DUMP)ChannelInfoDumper;
	pTSInfoParser->ts_filter->psi_parser->dumper.channel_info_context =	pTSInfoParser;
	pTSInfoParser->ts_filter->psi_parser->dumper.pid_dumper  =	(DUMP)PSIPidDumper;
	pTSInfoParser->ts_filter->psi_parser->dumper.pid_context =	pTSInfoParser;
	pTSInfoParser->ts_filter->psi_parser->dumper.system_time_dumper   =	(DUMP)SystemTimeDumper;
	pTSInfoParser->ts_filter->psi_parser->dumper.system_time_context  =	pTSInfoParser;
	//pTSInfoParser->ts_filter->psi_parser->dumper.tune_info_dumper     = (DUMP)TuneInfoDumper;
	//pTSInfoParser->ts_filter->psi_parser->dumper.tune_info_context    = pTSInfoParser;
	//pTSInfoParser->ts_filter->psi_parser->dumper.message_dumper       = (DUMP)MessageDumper;
	//pTSInfoParser->ts_filter->psi_parser->dumper.message_context      = pTSInfoParser;
	pTSInfoParser->utc_sec_start = 0;
	pTSInfoParser->pcr_sec_start = 0;
	pTSInfoParser->max_check_limit = 1024*1024*32; //max check 64M data for avinfo
	SageLog(( _LOG_TRACE, 3, TEXT("TS Info Parser filter is created  version 1.0.2  .") ));
	return pTSInfoParser;
}

void ReleaseTSInfoParser( TS_INFO_PARSER *pTSInfoParser )
{
	int i;
	SAGETV_FREE( pTSInfoParser->ts_streams.ts_element );
	SAGETV_FREE( pTSInfoParser->es_streams.es_element );
	SAGETV_FREE( pTSInfoParser->av_streams.av_element );
	for ( i = 0; i<pTSInfoParser->max_stream_num; i++ )
		SAGETV_FREE( pTSInfoParser->es_buffer[i].buffer );
	SAGETV_FREE( pTSInfoParser->es_buffer );
	ReleaseTSFilter( pTSInfoParser->ts_filter );
	SAGETV_FREE( pTSInfoParser );

	SageLog(( _LOG_TRACE, 3, TEXT("TS Info Parser is released  .") ));
}

void ResetTSInfoParser( TS_INFO_PARSER *pTSInfoParser )
{
	int i;
	ResetTSFilter( pTSInfoParser->ts_filter );
	pTSInfoParser->ts_filter->disable_ts_table_parse = 0;
	pTSInfoParser->ts_filter->disable_stream_filter = 0;
	SageLog(( _LOG_TRACE, 3, TEXT("TS Info Parser is reset  .") ));
	pTSInfoParser->state = 0;
	for ( i = 0; i<pTSInfoParser->max_stream_num; i++ )
	{
		pTSInfoParser->es_buffer[i].bytes = 0;
		pTSInfoParser->es_buffer[i].main_track = -1;
		pTSInfoParser->es_buffer[i].input_bytes = 0;
		pTSInfoParser->es_buffer[i].stream_id = 0;
	}
	pTSInfoParser->ts_streams.num_stream = 0;
	pTSInfoParser->es_streams.num_stream = 0;
	pTSInfoParser->av_streams.num_stream = 0;
	memset( pTSInfoParser->ts_streams.ts_element, 0, pTSInfoParser->max_stream_num*sizeof(TS_ELEMENT) );
	memset( pTSInfoParser->es_streams.es_element, 0, pTSInfoParser->max_stream_num*sizeof(ES_ELEMENT) );
	memset( pTSInfoParser->av_streams.av_element, 0, pTSInfoParser->max_stream_num*sizeof(AV_ELEMENT) );
	pTSInfoParser->avinf[0] = 0x0;
	pTSInfoParser->avinf_size = 0;
	pTSInfoParser->total_bytes = 0;
	pTSInfoParser->total_video_num = 0;
	pTSInfoParser->total_audio_num = 0;
	pTSInfoParser->found_video_num = 0;
	pTSInfoParser->found_audio_num = 0;
	pTSInfoParser->utc_sec_start = 0;
	pTSInfoParser->pcr_sec_start = 0;
	memset( &pTSInfoParser->tune, 0x0, sizeof(pTSInfoParser->tune) );
}


void SetupTSInfDump( TS_INFO_PARSER *pTSInfoParser, 
					 DUMP pfnPidDump,     void* pfnPidDumpContext,
					 DUMP pfnPcrDump,     void* pfnPcrDumpContext,
					 DUMP pfnAVInfDump,   void* pfnAVInfDumpContext,
					 DUMP pfnStreamDump,  void* pStreamDumpContext, 
					 DUMP pfnTimeDump,    void* pTimeDumpContext )
{
	pTSInfoParser->dumper.pid_dumper = pfnPidDump;
	pTSInfoParser->dumper.pid_dumper_context = pfnPidDumpContext;
	pTSInfoParser->dumper.pcr_dumper = pfnPcrDump;
	pTSInfoParser->dumper.pcr_dumper_context = pfnPcrDumpContext;
	pTSInfoParser->dumper.avinf_dumper = pfnAVInfDump;
	pTSInfoParser->dumper.avinf_dumper_context = pfnAVInfDumpContext;

	pTSInfoParser->dumper.stream_dumper = pfnStreamDump;
	pTSInfoParser->dumper.stream_dumper_context = pStreamDumpContext;
	pTSInfoParser->dumper.time_dumper = pfnTimeDump;
	pTSInfoParser->dumper.time_dumper_context = pTimeDumpContext;

}

int StartTSInfoParser( TS_INFO_PARSER *pTSInfoParser, int nFlag )
{
	int i, ret=0;
	PID_ITEM pid_inf;
	pTSInfoParser->state = 0;

	pid_inf.pid = 0;
	pid_inf.type = 0;
	pid_inf.service = 0;
	pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );

	pid_inf.pid =  0x1ffb;
	pid_inf.type = PID_TYPE_PSI;
	pTSInfoParser->dumper.pid_dumper( pTSInfoParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );

	for ( i = 0; i<pTSInfoParser->max_stream_num; i++ )
		pTSInfoParser->es_buffer[i].stream_id = 0;

	return ret;
}

void StopTSInfoParser( TS_INFO_PARSER *pTSInfoParser )
{
	pTSInfoParser->state = 0;
}
void SetupTSTune( TS_INFO_PARSER *pTSInfoParser, TUNE *pTune )
{
	pTSInfoParser->tune = *pTune;
}

void DisableParseData( TS_INFO_PARSER *pTSInfoParser )
{
	pTSInfoParser->ts_filter->disable_ts_table_parse = 1;
	pTSInfoParser->ts_filter->disable_stream_filter = 1;
	SageLog(( _LOG_TRACE, 3, TEXT("TS Filter table and stream data parser disabled") ));
	return;
}

int TSProcessInfo( TS_FILTER* pTSFilter, uint8_t* pData );
int PushTSPacketParser( TS_INFO_PARSER *pTSInfoParser, uint8_t* pData, int nSize )
{
	int ret = 0;
	int used_bytes = 0, size = nSize;
	uint8_t* data = pData;

	while ( size >= TS_PACKET_LENGTH )
	{
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

		ret = TSProcessInfo( pTSInfoParser->ts_filter, data  ); //must to be 188 bytes of data

		data += TS_PACKET_LENGTH;
		size -= TS_PACKET_LENGTH;
		used_bytes += TS_PACKET_LENGTH;
	}

	return ret;
}

static int CheckTsId( TS_INFO_PARSER *pTSInfoParser, int nTsid )
{
	TUNE *tune = &pTSInfoParser->tune;
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

static int CheckProgramId( TS_INFO_PARSER *pTSInfoParser, int nProgramId, int nTsid )
{
	TUNE *tune = &pTSInfoParser->tune;
	if ( tune->stream_format == ATSC_STREAM )
	{
		if ( tune->u.atsc.program_id != 0 )
		{
			if ( tune->u.atsc.program_id == nProgramId )
				return 1;
			else 
				return -1;
		}
	} else
	if ( tune->stream_format == DVB_STREAM )
	{
		if ( tune->u.dvb.sid != 0 && tune->u.dvb.tsid == nTsid )
		{
			if ( tune->u.dvb.sid == nProgramId )
				return 1;
			else
				return -1;
		}
	} 
	return 0;
}
static char* CAInfo( uint16_t CAPid, uint16_t CAID )
{
	static char _buf[16];
	snprintf( _buf, sizeof(_buf), "CA pid:0x%04x", CAPid );
	return _buf;
}

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
	{REGISTRATION_DESC, "REG"}, {0, ""}, {0,""} };

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

int CheckTrackAVInfRead( TS_INFO_PARSER *pTSInfoParser )
{
	uint32_t sec;
	if ( pTSInfoParser->total_video_num && pTSInfoParser->found_video_num >= pTSInfoParser->total_video_num )
		if ( pTSInfoParser->total_audio_num && pTSInfoParser->found_audio_num >= pTSInfoParser->total_audio_num )
			return 1;
			
	if ( (sec = ElapseSeconds( pTSInfoParser )) > 8 || ( sec == 0 && ElapseSecondsByPCR(pTSInfoParser) > 8 ) )
	{
		SageLog(( _LOG_ERROR, 3, TEXT("AVINF checking timeout (%d)!"), ElapseSeconds( pTSInfoParser ) ));
		return 1;
	}
	
	if ( pTSInfoParser->total_bytes > pTSInfoParser->max_check_limit )
		return 1;

	if ( pTSInfoParser->total_bytes > pTSInfoParser->max_check_limit/2 )
	{
		int i, empty_video_track_num = 0, empty_audio_track_num = 0;
		for ( i = 0; i<pTSInfoParser->ts_streams.num_stream; i++ )
		{
			if ( pTSInfoParser->es_buffer[i].input_bytes == 0 )
			{
				if ( pTSInfoParser->ts_streams.ts_element[i].content_type == VIDEO_DATA )
					empty_video_track_num++;
				else
				if ( pTSInfoParser->ts_streams.ts_element[i].content_type == AUDIO_DATA )
					empty_audio_track_num++;
			}
		}

		if ( pTSInfoParser->found_video_num + empty_video_track_num>= pTSInfoParser->total_video_num )
			if ( pTSInfoParser->found_audio_num + empty_audio_track_num >= pTSInfoParser->total_audio_num )
			return 1;
	}
	return 0;
}

int AVElmntInfo( AV_ELEMENT *pAVElmnt, char* pBuffer, int nSize );
uint32_t GetAudioRate( AV_ELEMENT *pAVElmnt );
int TagInfo( int nOutputFormat, TS_ELEMENT *pTSElmnt, ES_ELEMENT *pESElmnt, char* pBuffer, int nSize );

static void TagMainTrack( TS_INFO_PARSER *pTSInfoParser )
{
	int i, video_main = -1, audio_main = -1;
	uint32_t bit_rate = 0;
	int weights[32]={0};
	for ( i = 0; i<pTSInfoParser->ts_streams.num_stream && i<32; i++ )
	{
		pTSInfoParser->es_buffer[i].main_track = 0;
		if ( pTSInfoParser->av_streams.av_element[i].content_type == VIDEO_DATA )
		{
			if ( video_main == -1 ) 
				video_main = i;
			else
			if ( pTSInfoParser->es_buffer[i].input_bytes > pTSInfoParser->es_buffer[video_main].input_bytes )
				video_main = i;
			bit_rate += GetAudioRate( &pTSInfoParser->av_streams.av_element[i]  );
		}
		else
		if ( pTSInfoParser->av_streams.av_element[i].content_type == AUDIO_DATA )
		{
			weights[i] = GetAudioSoundChannelNum( &pTSInfoParser->av_streams.av_element[i] )*100;
			weights[i] += GetAudioSoundBitRate( &pTSInfoParser->av_streams.av_element[i] ) / 10000; /* range 1-100 */
			if ( audio_main == -1 ) 
				audio_main = i;
			else
			{
				if ( weights[i] > weights[audio_main] )
					audio_main = i;
				bit_rate += GetAudioRate( &pTSInfoParser->av_streams.av_element[i]  );
			}
		}
	}
	if ( video_main >= 0 )
		pTSInfoParser->es_buffer[video_main].main_track = 1;
	if ( audio_main >= 0 )
		pTSInfoParser->es_buffer[audio_main].main_track = 1;

	pTSInfoParser->bit_rate = bit_rate;
}


static int GetTrackAVInf( TS_INFO_PARSER *pTSInfoParser, int nIndex, char* pBuffer, int nSize )
{
	int pos = 0;
	char *p = pBuffer;
	TS_ELEMENT *ts_elmnt = &pTSInfoParser->ts_streams.ts_element[nIndex];
	AV_ELEMENT *av_elmnt = &pTSInfoParser->av_streams.av_element[nIndex];
	ES_BUFFER  *es_buffer = &pTSInfoParser->es_buffer[nIndex];
	if ( nSize > pos ) pos += snprintf( p+pos, nSize-pos, "[" );
	pos += AVElmntInfo( av_elmnt, p+pos, nSize-pos );
	if ( es_buffer->main_track )
		if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "main=yes;"  );
	pos += TagInfo( 1, ts_elmnt, NULL, p+pos, nSize-pos );
	if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "index=%d;", nIndex );
	if ( nSize > pos+2 ) { p[pos++] = ']'; p[pos++] = ';';  } //terminator
	return pos;
}

int GetAVInfo( TS_INFO_PARSER *pTSInfoParser, char* pBuffer, int nSize )
{
	char *p = pBuffer;
	int  pos = 0;
	if ( !CheckTrackAVInfRead( pTSInfoParser ) )
		return 0;

	TagMainTrack( pTSInfoParser );
	pos += snprintf( p+pos, nSize-pos, "AV-INF|f=" );
	if ( pTSInfoParser->found_video_num == 0 && pTSInfoParser->found_audio_num == 0 )
	{
		pos += snprintf( pBuffer, nSize-pos, "NO-AV-TS;" );
		return pos;
	}
	else
	{
		int i;
		pos += snprintf( p+pos, nSize-pos, "MPEG2-TS;" );
		if ( nSize > pos && pTSInfoParser->bit_rate > 0 )
			pos += snprintf( p+pos, nSize-pos, "br=%d;", pTSInfoParser->bit_rate );
		if ( pTSInfoParser->total_video_num == 0 )
			pos += snprintf( p+pos, nSize-pos, "audioonly=1;" );
		else
		if ( pTSInfoParser->total_audio_num == 0 )
			pos += snprintf( p+pos, nSize-pos, "videoonly=1;" );

		for ( i = 0; i<pTSInfoParser->ts_streams.num_stream; i++ )
		{
			if ( pTSInfoParser->av_streams.av_element[i].content_type == VIDEO_DATA )
				pos += GetTrackAVInf( pTSInfoParser, i, p+pos, nSize-pos );
			if ( pTSInfoParser->av_streams.av_element[i].content_type == AUDIO_DATA )
				pos += GetTrackAVInf( pTSInfoParser, i, p+pos, nSize-pos );
		}
	}
	if ( pos < nSize ) p[pos]= 0x0;
	return pos;
}

static int GetMainAVIndex( TS_INFO_PARSER *pTSInfoParser, int *pVideoIndex, int *pAudioIndex )
{
	int i;
	*pAudioIndex = -1;
	*pVideoIndex = -1;
	for ( i = 0; i<pTSInfoParser->ts_streams.num_stream; i++ )
	{
		if ( pTSInfoParser->es_buffer[i].main_track == 1 )
		{
			if ( pTSInfoParser->av_streams.av_element[i].content_type == VIDEO_DATA )
				*pVideoIndex = i;
			else
			if ( pTSInfoParser->av_streams.av_element[i].content_type == AUDIO_DATA )
				*pAudioIndex = i;
		}		
	}

	if ( *pVideoIndex == -1 && *pAudioIndex == -1 )
		return 0;
	return 1;
}

//null packets carry ATS
int CheckSagePVRData( const uint8_t* pData, int nBytes )
{
	int i; 
	if ( nBytes < 14 ) return 0;
	for ( i = 0; i<nBytes; i++ )
	{
		if ( pData[i] == 0x47 && pData[i+1] == 0x1f && pData[i+2] == 0xff && pData[i+3] == 0x1f &&
			 pData[i+4] == 'S' && pData[i+5] == 'A' && pData[i+6] == 'G' && pData[i+7] == 'E' &&
			 pData[i+10] == 0xff && pData[i+13] == 0xff )
			 return 1;
	}

	return 0;
}

int GetPVRMetaInf( PVR_META_INF* pMetaInf, uint8_t *data )
{
	pMetaInf->state = 0;
	if ( data[NULL_PACKET_TAG_VER] == 0xff )
		return 0;
	pMetaInf->ver = data[NULL_PACKET_TAG_VER];
	pMetaInf->program_pid = (data[NULL_PACKET_TAG_P_PID]<<8)|data[NULL_PACKET_TAG_P_PID+1];
	pMetaInf->tsid = (data[NULL_PACKET_TAG_TSID]<<8)|data[NULL_PACKET_TAG_TSID+1];
	pMetaInf->program_num = (data[NULL_PACKET_TAG_PROG]<<8)|data[NULL_PACKET_TAG_PROG+1];
	pMetaInf->video_pid = (data[NULL_PACKET_TAG_V_PID]<<8)|data[NULL_PACKET_TAG_V_PID+1];
	pMetaInf->video_type =( data[NULL_PACKET_TAG_V_TYPE]<<8)|data[NULL_PACKET_TAG_V_TYPE+1];
	pMetaInf->audio_pid = (data[NULL_PACKET_TAG_A_PID]<<8)|data[NULL_PACKET_TAG_A_PID+1];
	pMetaInf->audio_type =( data[NULL_PACKET_TAG_A_TYPE]<<8)|data[NULL_PACKET_TAG_A_TYPE+1];
	if ( pMetaInf->program_pid != 0xffff ) 
		pMetaInf->state = 1;
	if ( pMetaInf->video_pid != 0xffff || pMetaInf->audio_pid != 0xffff )
		pMetaInf->state = 2;
	return 1;
}

int SearchPVRMetaInf( int* pNextStart, uint8_t *pData, int nBytes )
{
	int i; 
	if ( nBytes < 14 ) return 0;
	for ( i = 0; i<nBytes; i++ )
	{
		if ( pData[i] == 0x47 && pData[i+1] == 0x1f && pData[i+2] == 0xff && pData[i+3] == 0x1f &&
			 pData[i+4] == 'S' && pData[i+5] == 'A' && pData[i+6] == 'G' && pData[i+7] == 'E' &&
			 pData[i+10] == 0xff && pData[i+13] == 0xff )
		{
			*pNextStart = (pData[i+8]<<8)|pData[i+9];
			*pNextStart <<= 8;
			*pNextStart |= (pData[i+11]<<8)|pData[i+12];
			 return i;
		}
	}
	return -1;
}

