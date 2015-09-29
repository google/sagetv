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

#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"
#include "PSParser.h"
#include "ESAnalyzer.h"
#include "PSBuilder.h"
#include "TSBuilder.h"
#include "BlockBuffer.h"
#include <stdarg.h>
#include <stdio.h>
#ifdef WIN32
#include <io.h>
#include <fcntl.h>
#include <share.h>
#endif
#include <string.h>
#include <stdlib.h>	
#include <memory.h>	
#include <sys/types.h>
#include <sys/stat.h>
#include "Demuxer.h"
#include "ChannelScan.h"
#include "ChannelScanInc.h"

static int InitScanData( SCAN* pScan, int nStreamType, int nSubType );
static int AddTuneList( TUNE_LIST *pTuneList, TUNE_DATA *pTuneData, int *pUpdatedFlag  );
static int AddChannelList( CHANNEL_LIST *pChannelList, CHANNEL_DATA *pChannelData, TUNE_LIST *pTuneList );
static uint32_t MaxPSICheckBytes( int nStreamType, int nSubType );
static int CheckChannelStateReady( CHANNEL_LIST *pChannelList );
static void _display_av_inf( TRACKS *pTracks );

static int  UpdateChannelList( CHANNEL_LIST *pChannelList, TUNE_DAT* pTuneData );
static void AssignTuneData( DVB_CHANNEL* pDVBChannel, TUNE_DAT* pTuneData );
static int  AddProgrmToList( PROGRAM_LIST *pProgramList, uint16_t nTsid, uint16_t nProgramId, uint16_t nChannel );
static void ChannelScanZero( SCAN* pScan  );
static int  FindProgram( PROGRAM_LIST *pProgramList, int nChannel );
int HasCADesc( uint8_t *pData, int nBytes );
////////////////////////////////////////////////////////////////////////////////////////////////////////////////

static int ChannelDataDumper( void* pContext, uint8_t* pData, int nSize )
{
	SCAN *pScan = (SCAN*)pContext;
	CHANNEL_DATA *channel_data = (CHANNEL_DATA*)pData;
	int local_channel = (channel_data->update_flag == 1);
	int ret;

	pScan->psi_counter++;
	if ( pScan->task == NAKED_SCAN ) //naked scan don't care about psi channel information
		return 1;

	channel_data->command = 0;
	if ( channel_data->stream_format == ATSC_STREAM )
	{
		channel_data->u.atsc[0].physical_ch = pScan->tune.u.atsc.physical_ch;
		InitScanData( pScan, channel_data->stream_format, channel_data->sub_format );
		ret = AddChannelList( &pScan->channel_list, channel_data, &pScan->tune_list );
	
		pScan->state = 0x03;
		SageLog(( _LOG_TRACE, 3, TEXT("\t**** PSI ATSC/QAM scan channel ready.") ));

	} else
	{
		if ( !local_channel )
			return 0;

		//We find the QAM stream carries DVB-C PSI in Lubbock TX, which messes up channel  information
		if ( channel_data->stream_format == DVB_STREAM && pScan->tune.stream_format == ATSC_STREAM && pScan->tune.sub_format == CABLE )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("\t****The QAM stream carries DVB PSI, droppped!") ));
			return 0;
		}

		ret = AddChannelList( &pScan->channel_list, channel_data, &pScan->tune_list );
		if ( ret == 0 )
		{
			pScan->state |= 0x0001;
			if ( CheckChannelStateReady( &pScan->channel_list ) )
			{
				pScan->state |= 0x0002;
				SageLog(( _LOG_TRACE, 3, TEXT("\t**** PSI DVB scan channel ready.") ));
			}

		}
		
	}
	return 1;
}

static int TuneDataDumper( void* pContext, uint8_t* pData, int nSize )
{
	SCAN *pScan = (SCAN*)pContext;
	TUNE_DATA *tune_data = (TUNE_DATA*)pData;
	int local_nit = tune_data->update_flag == 1;
	int ret=0, index=-1;

	tune_data->command = 0;
	pScan->nit_counter++;
	if ( tune_data->stream_format == ATSC_STREAM )
	{
		//nothing happens here
		ASSERT( 0 );
	} else
	{

		if ( !local_nit )
		{
			//tune_data->command = 0;
			//return 0;
		}

		if ( tune_data->u.dvb.dvb_type == TERRESTRIAL )
		{
			if ( pScan->tune_list.tune == NULL ) InitScanData( pScan, DVB_STREAM, TERRESTRIAL );
			index = AddTuneList( &pScan->tune_list, tune_data, &ret );
			if ( index >= 0 )
				ret = UpdateChannelList( &pScan->channel_list, &pScan->tune_list.tune[index] );

			if ( ret > 0  &&
				( pScan->tune.u.dvb.dvb.t.freq == tune_data->u.dvb.dvb.t.freq || 
				               pScan->tune.u.dvb.dvb.t.freq == 0 ))
			{
				pScan->local_nid  = tune_data->u.dvb.nid;
				pScan->local_onid = tune_data->u.dvb.onid;
				pScan->local_tsid = tune_data->u.dvb.tsid;
				pScan->selected_tune_index = ret;
				if ( pScan->tune.u.dvb.dvb.t.freq  == 0 )
					pScan->tune.u.dvb.dvb.t.freq  = tune_data->u.dvb.dvb.t.freq;
				SageLog(( _LOG_TRACE, 2, "DVB local NIT found nid:%d frq:%d  onid:%d tsid:%d", tune_data->u.dvb.nid, 
					                     tune_data->u.dvb.dvb.t.freq, tune_data->u.dvb.onid, tune_data->u.dvb.tsid ));
			}
		}
		else
		if ( tune_data->u.dvb.dvb_type == CABLE )
		{
			if ( pScan->tune_list.tune == NULL ) InitScanData( pScan, DVB_STREAM, CABLE );
			index = AddTuneList( &pScan->tune_list, tune_data, &ret );
			if ( index >= 0 )
				ret = UpdateChannelList( &pScan->channel_list, &pScan->tune_list.tune[index] );

			if ( ret > 0 &&
				  ( pScan->tune.u.dvb.dvb.c.freq == tune_data->u.dvb.dvb.c.freq || 
				               pScan->tune.u.dvb.dvb.c.freq == 0 ))
			{
				pScan->local_nid  = tune_data->u.dvb.nid;
				pScan->local_onid = tune_data->u.dvb.onid;
				pScan->local_tsid = tune_data->u.dvb.tsid;
				pScan->selected_tune_index = ret;
				if ( pScan->tune.u.dvb.dvb.c.freq  == 0 )
					pScan->tune.u.dvb.dvb.c.freq  = tune_data->u.dvb.dvb.c.freq;
				SageLog(( _LOG_TRACE, 2, "DVB local NIT found nid:%d frq:%d  onid:%d tsid:%d", tune_data->u.dvb.nid, 
					                     tune_data->u.dvb.dvb.c.freq, tune_data->u.dvb.onid, tune_data->u.dvb.tsid ));
			}

		}
		else
		if ( tune_data->u.dvb.dvb_type == SATELLITE )
		{
			if ( pScan->tune_list.tune == NULL ) InitScanData( pScan, DVB_STREAM, SATELLITE );
			index = AddTuneList( &pScan->tune_list, tune_data, &ret );
			if ( index >= 0 )
				ret = UpdateChannelList( &pScan->channel_list, &pScan->tune_list.tune[index] );
			if ( ret > 0 &&
				   (( pScan->tune.u.dvb.dvb.s.freq == tune_data->u.dvb.dvb.s.freq &&
					  pScan->tune.u.dvb.dvb.s.pol == tune_data->u.dvb.dvb.s.pol )  || 
				      pScan->tune.u.dvb.dvb.s.freq == 0 ))
			{
				pScan->local_nid  = tune_data->u.dvb.nid;
				pScan->local_onid = tune_data->u.dvb.onid;
				pScan->local_tsid = tune_data->u.dvb.tsid;
				pScan->selected_tune_index = ret;
				if ( pScan->tune.u.dvb.dvb.s.freq  == 0 )
					pScan->tune.u.dvb.dvb.s.freq  = tune_data->u.dvb.dvb.s.freq;
				SageLog(( _LOG_TRACE, 2, "DVB local NIT found nid:%d frq:%d  onid:%d tsid:%d", tune_data->u.dvb.nid, 
					                     tune_data->u.dvb.dvb.s.freq, tune_data->u.dvb.onid, tune_data->u.dvb.tsid ));


			}
		}

		if (  ret > 0  ) 
		{
			tune_data->command = 0;
			pScan->state |= 0x0002;
		}
	
	}

	return 1;
}

/*
static int PATDumper( void* pContext, uint8_t* pData, int nSize )
{
	SCAN *pScan = (SCAN*)pContext;
	pScan->pat_counter++;
}
*/

static int PMTDumper( void* pContext, uint8_t* pData, int nSize )
{
	SCAN *pScan = (SCAN*)pContext;
	PMT_DATA  *pPmtData  = (PMT_DATA*)pData;
	TS_PMT    *pPmt      = (TS_PMT*)pPmtData->pmt_table;
	//TS_SECTION *pPmtSection = pPmtData->pmt_section;
	//int channel = pPmtData->channel;
	int stream_num = pPmt->total_stream_number;
	int ret, i;

	pScan->pmt_counter++;

	if  ( !pScan->pmt_dump_enabled )
		return 1;

	if ( pScan->program_list.program == NULL )
		return 1;

	//if ( (pPmtData->update_flag & PMT_UPDATED) ) //skip unchanged PMT
	if ( ( pPmtData->update_flag ) ) //skip unchanged PMT
	{
		//clear QAM channel
		int valid_stream = 0, encrypted_stream = 0, valid_channel;

		if ( pPmt->ca_pid != 0 && pPmt->pcr_pid != 0x1fff )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Skip not clear program(CA QAM) tsid:%d program:%d CA pid:0x03x PCR:0x03x "),  
				   pPmtData->tsid, pPmt->program_number, pPmt->ca_pid, pPmt->pcr_pid ));
		}

		for ( i = 0; i<stream_num; i++ )
		{
			int has_ca_desc = HasCADesc( pPmt->stream_desc[i].desc_ptr, pPmt->stream_desc[i].desc_bytes )? 1 : 0;
			if ( has_ca_desc ) 
				encrypted_stream++;
			if ( IsVideoType( pPmt->stream_type[i] ) || IsAudioType( pPmt->stream_type[i] ) ) 
				valid_stream++;
			//SageLog(( _LOG_TRACE, 3,  TEXT("\t\t-- Type:0x%02x Pid:0x%03x  %s --"), pPmt->stream_type[i], pPmt->stream_pid[i], has_ca_desc?"encrypted":" " ));
		}
		
		if ( pScan->stream_format == ATSC_STREAM )
			valid_channel = ( pPmt->ca_pid == 0 || pPmt->pcr_pid == 0x1fff ) && encrypted_stream == 0 && valid_stream > 0; //skip unclear QAM channel
		else
			valid_channel = valid_stream > 0;

		if ( !valid_channel )
		{
			SageLog(( _LOG_TRACE, 3,  TEXT("\t****  drop program: %d channel:%d"),  pPmt->program_number, pPmtData->channel ));
			pScan->program_list.drop_program_num++;
		} else
		{
			if ( (ret = AddProgrmToList( &pScan->program_list, pPmtData->tsid, pPmt->program_number, pPmtData->channel )) >= 0 )
			{
				SageLog(( _LOG_TRACE, 3, TEXT("\t**** Add program to check list (%d) tsid:%d program:%d "),  ret,
						   pPmtData->tsid, pPmt->program_number ));

				pScan->program_list.flag = 0;
				//initial the first check channel
				if ( pScan->task == NAKED_SCAN && pScan->tune.tune_string_type == 0  )
				{
					pScan->program_list.stream_format = pScan->tune.stream_format;
					pScan->program_list.sub_format    = pScan->tune.sub_format;
					pScan->stream_format = pScan->tune.stream_format;
					pScan->sub_format    = pScan->tune.sub_format;
					pScan->tune.channel = 0;;
					//pScan->tune.u.atsc.minor_num = pScan->program_list.program[0].program_id;
					if ( pScan->stream_format == ATSC_STREAM ||  pScan->stream_format == 0 )
					{
						pScan->tune.tune_string_type = 3;
						pScan->tune.u.atsc.major_num = 0;
						pScan->tune.u.atsc.minor_num = pScan->program_list.program[0].channel;
						pScan->tune.u.atsc.tsid = pScan->program_list.program[0].tsid;
						pScan->tune.u.atsc.program_id = pScan->program_list.program[0].program_id;
						pScan->tune.channel = pScan->program_list.program[0].channel;
					}
					else
					if ( pScan->stream_format == DVB_STREAM )
					{
						pScan->tune.tune_string_type = 3;
						pScan->tune.u.dvb.tsid = pScan->program_list.program[0].tsid;
						pScan->tune.u.dvb.sid = pScan->program_list.program[0].program_id;
						pScan->tune.channel = pScan->program_list.program[0].channel;
					}

					SetupDemuxTSTune( pScan->demuxer, 0, &pScan->tune );
				}
			} else
			{
				if ( ret == - 1 )
				{
					pScan->program_list.flag++;
					if ( pScan->program_list.flag < 5 )
						SageLog(( _LOG_TRACE, 3, TEXT("\t**** program already is in list (%d) tsid:%d program:%d "),  ret,
							   pPmtData->tsid, pPmt->program_number ));
					if ( 0 && pScan->task == NAKED_SCAN )
					{
						pScan->state |= 0x0010;
						SageLog(( _LOG_TRACE, 3, TEXT("\t**** Naked scan channel ready.") ));
					}
				}
			}
		}
	}

	return 1;
}

static int AVInfMEssageDumper( void* pContext, void* pData, int nSize )
{
	SCAN *pScan = (SCAN*)pContext;
	DEMUXER *pDemuxer = pScan->demuxer;
	MESSAGE_DATA *message = (MESSAGE_DATA*)pData;
	if ( !strcmp( message->title, "STATUS" ) )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("*********** CHANNEL SCAN (%s) PARSER STATUS: %s ***********"), 
			pScan->task == NAKED_SCAN ? "naked":"psi",  message->message ));
		if ( strstr( (char*)message->message, "STREAM START" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
		} else
		if ( strstr( (char*)message->message, "STREAM FAILED" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

			pScan->state = 0x0100; //tuning failed, a channel not found

		} else
		if ( strstr( (char*)message->message, "STREAM READY" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );
			{
				TRACKS *tracks;
				//ULONGLONG pos = DemuxUsedBytes( pDemuxer );
				tracks = GetTracks( pDemuxer, slot_index );
				CheckTracksAttr( tracks , 0 );
				TracksIndexing( tracks );
				_display_av_inf( tracks );
				if (  tracks->track_attr & ATTR_ENCRYPTED_FLAG )
				{
					SageLog(( _LOG_TRACE, 3, TEXT("It is an encrypted channel (channel:%d)!"), pScan->tune.channel  ));
				} else
				if ( tracks->track_attr & (ATTR_VIDEO_PRESENT|ATTR_AUDIO_PRESENT ) )
				{
					SageLog(( _LOG_TRACE, 3, TEXT("It is a valid channel (channel:%d)!"), pScan->tune.channel  ));
					CheckTracksAttr( tracks , 0 );
				} else
				{
					SageLog(( _LOG_TRACE, 3, TEXT("It is an invalid channel (channel:%d)!"), pScan->tune.channel  ));
				}

				if ( pScan->task == NAKED_SCAN )
				{
					//register a channel
					int i = FindProgram( &pScan->program_list, pScan->tune.channel );
					if ( i < 0 )
						SageLog(( _LOG_TRACE, 3, TEXT("ERROR: Not found channel on program list (channel:%d)!"), pScan->tune.channel  ));
					else
					if (  tracks->track_attr & ATTR_ENCRYPTED_FLAG )
						pScan->program_list.program[i].service = 0;
					else
					if (  tracks->track_attr & (ATTR_VIDEO_PRESENT|ATTR_AUDIO_PRESENT ) )
						pScan->program_list.program[i].service = 1;
					else
					if (  tracks->track_attr & (ATTR_AUDIO_PRESENT ) )
						pScan->program_list.program[i].service = 3; //radio or music channel
					else
						pScan->program_list.program[i].service = 0;

					pScan->naked_channel_found++;
					if ( pScan->program_list.flag > 4 &&
						 pScan->naked_channel_found > pScan->program_list.program_num )
					{	//scan is done.
						pScan->state = 0x0010;
						SageLog(( _LOG_TRACE, 3, TEXT("Naked channel scanning is done (total:%d)."),
									                   pScan->program_list.program_num));
					}
					else
					{   
						//check next channel
						i++;
						//setup for next channel checking.
						if ( pScan->stream_format == ATSC_STREAM  )
						{
							if ( pScan->program_list.program[i].program_id > 0 )
							{
								pScan->tune.tune_string_type = 3;
								pScan->tune.u.atsc.major_num = 0;
								pScan->tune.u.atsc.minor_num = pScan->program_list.program[i].channel;
								pScan->tune.u.atsc.tsid = pScan->program_list.program[i].tsid;
								pScan->tune.u.atsc.program_id = pScan->program_list.program[i].program_id;
								pScan->tune.channel = pScan->program_list.program[i].channel;
							} else
							{
								pScan->tune.tune_string_type = 0; //the next program isn't ready yet.
							}
						}
						else
						if ( pScan->stream_format == DVB_STREAM )
						{
							if ( pScan->program_list.program[i].program_id > 0 )
							{
								pScan->tune.tune_string_type = 3;
								pScan->tune.u.dvb.tsid = pScan->program_list.program[i].tsid;
								pScan->tune.u.dvb.sid = pScan->program_list.program[i].program_id;
								pScan->tune.channel = pScan->program_list.program[i].channel;
							} else
							{
								pScan->tune.tune_string_type = 0; //the next program isn't ready yet.
							}
						}			
						if ( pScan->tune.tune_string_type )
							SetupDemuxTSTune( pScan->demuxer, 0, &pScan->tune );
					}
					QueueChannelScanZero( pScan ); //clean up SCAN
					//if ( pScan->fd )
					//	fseek( pScan->fd, 0, SEEK_SET );

				}
			} 
		} else
		if ( strstr( (char*)message->message, "STREAM END" ) )
		{
			int slot_index = 0;
			const char *p;
			if ( ( p = strstr( (char*)message->message, "slot:" ) )!= NULL )
				slot_index = atoi( p+5 );

		} else
		{

		}
	}
	return 1;
}

//static int AVInfProgressCallback( void* pContext, void* pData, int nSize )
//{
//	SCAN *pScan = (SCAN*)pContext;
//	ULONGLONG *plTotalBytes = (ULONGLONG *)pData;
//	ASSERT( sizeof(ULONGLONG) == nSize );
//	return 1;
//}

////////////////////////////////////////////////////////////////////////////////////////////////
int	PushScanStreamData( SCAN* pScan, uint8_t *pData, int nBytes, int *nExpectedBytes )
{
	int ret;
	ret = PushDemuxStreamData( pScan->demuxer, pData, nBytes, nExpectedBytes );
	pScan->parse_bytes += ret;
	if ( pScan->command & SCAN_COMMAND_ZERO )
	{
		ChannelScanZero( pScan  );
		pScan->command &= ~SCAN_COMMAND_ZERO;
	}
	return ret;
}


////////////////////////////////////////////////////////////////////////////////////////////////
SCAN *CreateChannelScan(  void* pDemuxer, TUNE *pTune )
{
	SCAN* pScan = SAGETV_MALLOC( sizeof(SCAN) );

	SageLog(( _LOG_TRACE, 3, TEXT("ChannelScan ver. 2.0.5 is created") ));

	InitScanData( pScan, pTune->stream_format, pTune->sub_format );
	pScan->tune = *pTune;
	if ( pDemuxer == NULL )
	{
		int ret;
		pScan->demuxer_myown_flag = 1;
		pScan->demuxer =  CreateDemuxer( MPEG_TS, MAX_TRACK_NUM, ES_BUFFER_SIZE  );
		ret = OpenStreamSource( pScan->demuxer, MPEG_TS,  pTune );
		if ( !ret )
		{
			ReleaseDemuxer( pScan->demuxer );
			SAGETV_FREE( pScan );
			return NULL;
		}
	} else
	{
		pScan->demuxer_myown_flag = 0;
		pScan->demuxer = pDemuxer;
	}

	pScan->demuxer->ts_parser->dumper.tune_dumper            = (DUMP)TuneDataDumper;
	pScan->demuxer->ts_parser->dumper.tune_dumper_context    = pScan;
	pScan->demuxer->ts_parser->dumper.channel_dumper         = (DUMP)ChannelDataDumper;
	pScan->demuxer->ts_parser->dumper.channel_dumper_context = pScan;
	pScan->demuxer->ts_parser->dumper.message_dumper		 = (DUMP)AVInfMEssageDumper;
	pScan->demuxer->ts_parser->dumper.message_dumper_context = pScan;
	pScan->demuxer->ts_parser->dumper.pmt_dumper			 = (DUMP)PMTDumper;
	pScan->demuxer->ts_parser->dumper.pmt_dumper_context	 = pScan;

	pScan->maxium_psi_checking_bytes = SCAN_MAX_CHECK_BYTES;
	pScan->pat_counter = 0;
	pScan->pmt_counter = 0;
	pScan->psi_counter = 0;
	pScan->nit_counter = 0;
	pScan->pmt_dump_enabled = 0;
	pScan->last_time_clock = 0;
	pScan->time_elapse = 0;

	DisableRebuildTSStream( pScan->demuxer );
	return pScan;
}

void ReleaseChannelScan( SCAN* pScan )
{
	
	SageLog(( _LOG_TRACE, 3, TEXT("ChannelScan is released parsing bytes:%d (max psi bytes:%d, time:%d)"), 
		 pScan->parse_bytes, pScan->maxium_psi_checking_bytes, pScan->last_time_clock ));
	pScan->demuxer->ts_parser->dumper.tune_dumper            = NULL;
	pScan->demuxer->ts_parser->dumper.tune_dumper_context    = 0;
	pScan->demuxer->ts_parser->dumper.channel_dumper         = NULL;
	pScan->demuxer->ts_parser->dumper.channel_dumper_context = 0;
	pScan->demuxer->ts_parser->dumper.message_dumper		 = NULL;
	pScan->demuxer->ts_parser->dumper.message_dumper_context = 0;

	if ( pScan->demuxer != NULL && pScan->demuxer_myown_flag )
	{
		CloseStreamSource( pScan->demuxer );
		CloseDemuxStream( pScan->demuxer );
	}
	if ( pScan->program_list.program )
		SAGETV_FREE( pScan->program_list.program );
	if ( pScan->channel_list.channel  )
		SAGETV_FREE( pScan->channel_list.channel );
	if ( pScan->tune_list.tune  )
		SAGETV_FREE( pScan->tune_list.tune );
	SAGETV_FREE( pScan );

}

void ResetChannelScan( SCAN* pScan )
{
	SageLog(( _LOG_TRACE, 3, TEXT("ChannelScan is reset parsing bytes:%d (max psi bytes:%d, time:%d)"), 
		 pScan->parse_bytes, pScan->maxium_psi_checking_bytes, pScan->last_time_clock ));

	pScan->state = 0;
	pScan->command = 0;

	pScan->local_nid = 0;
	pScan->local_onid = 0;
	pScan->local_tsid = 0;

	pScan->parse_bytes = 0;
	pScan->last_parser_bytes = 0;
	//pScan->naked_channel_found = 0;

	pScan->pat_counter = 0;
	pScan->pmt_counter = 0;
	pScan->last_time_clock = 0;
	pScan->time_elapse = 0;
	pScan->psi_counter = 0;
	pScan->nit_counter = 0;
	pScan->stream_format = 0;
	pScan->sub_format = 0;   

	ResetDemuxStream( pScan->demuxer );

}

//clean up channel scan result
void ResetChannelScanList( SCAN* pScan )
{
	pScan->channel_list.channel_num = 0;
	pScan->program_list.program_num = 0;
	pScan->program_list.flag = 0;
	pScan->program_list.drop_program_num = 0;
	pScan->naked_channel_found = 0;
}

void ResetTuneList( SCAN* pScan )
{
	pScan->tune_list.tune_num = 0;
}

void QueueChannelScanZero( SCAN *pScan )
{
	QueueAbortDemux( pScan->demuxer );
	pScan->command = SCAN_COMMAND_ZERO;
}

static void ChannelScanZero( SCAN* pScan  )
{
	ResetDemuxStream( pScan->demuxer );
}

static int  FindProgram( PROGRAM_LIST *pProgramList, int Channel )
{
	int i;
	for ( i = 0; i<pProgramList->program_num; i++ )
	{
		if ( Channel == pProgramList->program[i].channel )
			return i;
	}
	return -1;
}

void  EnableProgramList( SCAN *pScan )
{
	if ( pScan->program_list.program == NULL )
	{
		pScan->program_list.total_list_num = MAX_SCAN_PROGRAM_NUM;
		pScan->program_list.program = SAGETV_MALLOC( sizeof(PROGRAM_DAT)*MAX_SCAN_PROGRAM_NUM );
	}
	pScan->program_list.program_num = 0;
	pScan->program_list.flag = 0;
	pScan->program_list.drop_program_num = 0;

}

void DoChannelScan( SCAN* pScan, int nTask )
{
	pScan->task = nTask;
	if ( nTask == PSI_SCAN )
	{
		EnableDemuxTSPSI( pScan->demuxer );
		pScan->pmt_dump_enabled = 0;
	} else
	if ( nTask == NAKED_SCAN )
	{
		DisableDemuxTSPSI( pScan->demuxer );
		UnLockDemuxTSPPmt( pScan->demuxer );
		EnableProgramList( pScan );
		pScan->tune.u.atsc.minor_num = 0xffff;
		pScan->tune.channel = 0;
		pScan->pmt_dump_enabled = 1;

	} else
	{
		ASSERT( 0 );
	}
	pScan->command = 0;
	pScan->state = 0;
}

void ChannelScanTune( SCAN* pScan, TUNE *pTune )
{
	pScan->command = 0;
	pScan->state = 0;
	pScan->tune = *pTune;
	pScan->tune.u.atsc.minor_num = 0xffff;
	pScan->tune.channel = 0;

	if ( pScan->task == PSI_SCAN )
	{
		pScan->maxium_psi_checking_bytes = MaxPSICheckBytes( pTune->stream_format, pTune->sub_format );
	}

	SetupDemuxTSTune( pScan->demuxer, 0, pTune );
}

static uint32_t MaxPSICheckBytes( int nStreamType, int nSubType )
{
	if ( nStreamType == ATSC_STREAM )
	{
		if ( nSubType == CABLE )
			return (43*1024*1024/8)+(43*1024*1024/8)/2; //1.5 seconds= sec*(256QAM bitrates)/8;
		else
			return 2*(19*1024*1024/8); //2 seconds= sec*(VSB-8)/8;
	}
	else
	if ( nStreamType == DVB_STREAM ) 
	{
		if ( nSubType == SATELLITE )
			return 15*(34*1024*1024/8); //15 seconds of 34M bitrate
		else
		if ( nSubType == TERRESTRIAL )
			return 2*(24*1024*1024/8);  //2 seconds of 24M bitrate
		else
		if ( nSubType == CABLE )
			return 2*(38*1024*1024/8);  //2 seconds of 38M bitrate

	} 

	return  SCAN_MAX_CHECK_BYTES;

}

static uint32_t MaxPSICheckTimeOut( int nStreamType, int nSubType )
{
		if ( nStreamType == ATSC_STREAM )
	{
		if ( nSubType == CABLE )
			return 30000; //30 sec
		else
			return 15000; //15 sec
	}
	else
	if ( nStreamType == DVB_STREAM ) 
	{
		if ( nSubType == SATELLITE )
			return 30000; //30 sec

		if ( nSubType == TERRESTRIAL )
			return 15000; //15 sec
		else
		if ( nSubType == CABLE )
			return 20000; //20 sec
	} 

	return  30*1000; //30 second

}

int IsNakedStream( SCAN* pScan )
{
	return ( pScan->psi_counter == 0 && pScan->pmt_counter > 3 );
}

int IsChannelInfoReady( SCAN* pScan )
{
	if ( (pScan->state & 0x0003)== 0x0003 )
		return 2;  //psi channels is ready

	if ( (pScan->state & 0x0010) )
		return 3;  //a naked channel ready

	if ( pScan->task == PSI_SCAN  )
	{
		if ( pScan->parse_bytes > pScan->maxium_psi_checking_bytes )  
		{
			return 4;
		}

		if ( pScan->psi_counter == 0 && pScan->nit_counter == 0 && pScan->time_elapse > 3500 ) //3.5 sec
		{
			return 5;
		}

		if ( pScan->time_elapse > pScan->psi_timeout )
		{
			//if ( ChannelInfoReadyNum( pScan ) > 0 )
			//	return 2;
			return 10;
		}
	}

	if ( pScan->task == NAKED_SCAN )
	{    //no PAT/PMT in stream
		 if ( pScan->pat_counter==0 && pScan->pmt_counter==0 && pScan->parse_bytes > pScan->maxium_psi_checking_bytes ) 
		 {
			return 6;
		}
		 //no clear channel in stream
		 if ( pScan->program_list.program_num == 0 &&  pScan->parse_bytes > 2*pScan->maxium_psi_checking_bytes )
		 {
			 return 7;
		 }
	}

	if ( (pScan->state & 0x0100) )
		return 1; // a tune channel not found

	return 0;
}

int UpdateTimeClock( SCAN* pScan, uint32_t lMillionSecond )
{
	if ( lMillionSecond > pScan->last_time_clock )
	{
		if ( pScan->last_time_clock != 0 )
			pScan->time_elapse += lMillionSecond - pScan->last_time_clock;
		pScan->last_time_clock = lMillionSecond;
	}
	return pScan->time_elapse;
}

int ChannelInfoState( SCAN* pScan )
{
	int data_avaliable;
	int flag = IsChannelInfoReady( pScan );

	if (flag > 0 )
		return flag;

	//ration range 100-inf
	if ( PacketsSNRatio( pScan->demuxer->ts_parser ) < 115 ) 
	{
		return -2;
	}

	data_avaliable = pScan->parse_bytes && pScan->parse_bytes != pScan->last_parser_bytes;
	pScan->last_parser_bytes = pScan->parse_bytes;
	if ( !data_avaliable )
	{
		return -1;
	}

	return 0;
}

int ChannelInfoReadyNum( SCAN* pScan )
{
	int i, num = 0;
	for ( i = 0; i < pScan->channel_list.channel_num; i++ )
	{
		if ( (pScan->channel_list.channel[i].state & 0x03) )
			num++;
	}
	return num;
}

int ChannelInfoChannelNum( SCAN* pScan )
{
	return pScan->naked_channel_found + pScan->channel_list.channel_num;
}

struct DEMUXER* GetChannelScanDemuxer( SCAN* pScan  )
{	
	return pScan->demuxer;
}

int GetChannelNum( SCAN *pScan )
{
	return pScan->channel_list.channel_num;
}

static int InitScanData( SCAN* pScan, int nStreamType, int nSubType )
{
	pScan->stream_format = nStreamType;
	pScan->sub_format = nSubType;
	pScan->tune_list.stream_format = nStreamType;
	pScan->tune_list.sub_format = nSubType;
	pScan->channel_list.stream_format = nStreamType;
	pScan->channel_list.sub_format = nSubType;
	pScan->program_list.stream_format = nStreamType;
	pScan->program_list.sub_format = nSubType;
	pScan->maxium_psi_checking_bytes = MaxPSICheckBytes( nStreamType, nSubType );
	pScan->psi_timeout = MaxPSICheckTimeOut( nStreamType, nSubType );

	if ( nStreamType == DVB_STREAM )
	{
		if ( nSubType == TERRESTRIAL )
		{
			if ( pScan->tune_list.tune == NULL )
			{
				pScan->tune_list.total_list_num = 5;
				pScan->tune_list.tune = SAGETV_MALLOC( 5 * sizeof( TUNE_DAT ) );
				return 1;
			}

		} else
		if ( nSubType == CABLE )
		{
			if ( pScan->tune_list.tune == NULL )
			{
				pScan->tune_list.total_list_num = 15;
				pScan->tune_list.tune = SAGETV_MALLOC( 15 * sizeof( TUNE_DAT ) );
				return 1;
			}

		} else
		if ( nSubType == SATELLITE )
		{
			if ( pScan->tune_list.tune == NULL )
			{
				pScan->tune_list.total_list_num = 25;
				pScan->tune_list.tune = SAGETV_MALLOC( 25 * sizeof( TUNE_DAT ) );
				return 1;
			}
		} 
	} else
	if ( nStreamType == ATSC_STREAM )
	{
		if ( nSubType == TERRESTRIAL ) //ATSC
		{
			if ( pScan->channel_list.channel == NULL )
			{
				pScan->channel_list.total_list_num = 10;
				pScan->channel_list.channel = SAGETV_MALLOC( 10 * sizeof(CHANNEL_DAT) );
				return 1;
			}

		} else
		if ( nSubType == CABLE ) //QAM
		{
			if ( pScan->channel_list.channel == NULL )
			{
				pScan->channel_list.total_list_num = 20;
				pScan->channel_list.channel = SAGETV_MALLOC( 20 * sizeof(CHANNEL_DAT) );
				return 1;
			}
		} 
	}

	return 0;
}

static TUNE_DAT *GetTuneData( TUNE_LIST *pTuneList, uint16_t nOnid, uint16_t nTsid )
{
	int i;
	for ( i = 0; i<pTuneList->tune_num; i++ )
	{
		if ( pTuneList->tune[i].onid == nOnid && pTuneList->tune[i].tsid == nTsid )
			return &pTuneList->tune[i];
	}

	return NULL;
}

static int AddTuneList( TUNE_LIST *pTuneList, TUNE_DATA *pTuneData, int *pUpdatedFlag )
{
	TUNE_DAT *tune_dat_p;
	int i=0, freq=0, mod=0;
	
	*pUpdatedFlag = 0;

	if ( pTuneData->u.dvb.onid == 0 || pTuneData->u.dvb.tsid == 0 )
		return -1;

	if ( pTuneData->u.dvb.dvb_type == TERRESTRIAL )
	{
		for ( i = 0; i<pTuneList->tune_num; i++ )
			if ( pTuneList->tune[i].u.t.freq == pTuneData->u.dvb.dvb.t.freq )
				break;
	} else
	if ( pTuneData->u.dvb.dvb_type == CABLE )
	{
		for ( i = 0; i<pTuneList->tune_num; i++ )
			if ( pTuneList->tune[i].u.c.freq == pTuneData->u.dvb.dvb.c.freq )
				break;
	} else
	if ( pTuneData->u.dvb.dvb_type == SATELLITE )
	{
		for ( i = 0; i<pTuneList->tune_num; i++ )
			if ( pTuneList->tune[i].u.s.freq == pTuneData->u.dvb.dvb.s.freq )
				break;
	} 

	if ( i<pTuneList->tune_num )
		return i;

	//expend table
	if ( pTuneList->tune_num == pTuneList->total_list_num )
	{
		TUNE_DAT *old_tune = pTuneList->tune;
		pTuneList->tune = SAGETV_MALLOC( (pTuneList->total_list_num+5)*sizeof( TUNE_DAT ) );
		memcpy( pTuneList->tune, old_tune, pTuneList->total_list_num * sizeof(TUNE_DAT) );
		pTuneList->total_list_num += 5;
		SAGETV_FREE( old_tune );
	}

	tune_dat_p = &pTuneList->tune[ pTuneList->tune_num ];
	i = pTuneList->tune_num++;

	tune_dat_p->nid = pTuneData->u.dvb.nid;
	tune_dat_p->onid = pTuneData->u.dvb.onid;
	tune_dat_p->tsid = pTuneData->u.dvb.tsid;
	if ( pTuneData->u.dvb.dvb_type == TERRESTRIAL )
	{
		tune_dat_p->u.t = pTuneData->u.dvb.dvb.t;
		freq = pTuneData->u.dvb.dvb.t.freq;
		mod = pTuneData->u.dvb.dvb.t.modulation;
	}
	else
	if ( pTuneData->u.dvb.dvb_type == CABLE )
	{
		tune_dat_p->u.c = pTuneData->u.dvb.dvb.c;
		freq = pTuneData->u.dvb.dvb.c.freq;
		mod = pTuneData->u.dvb.dvb.c.modulation;
	}
	else
	if ( pTuneData->u.dvb.dvb_type == SATELLITE )
	{
		tune_dat_p->u.s = pTuneData->u.dvb.dvb.s;
		freq = pTuneData->u.dvb.dvb.s.freq;
		mod = pTuneData->u.dvb.dvb.s.modulation;
	}

	SageLog(( _LOG_TRACE, 3, TEXT("DVB NIT nid:%d freq:%d mod:%d onid:%d tsid:%d %d" ), 
		        pTuneData->u.dvb.nid, freq, mod, tune_dat_p->onid, tune_dat_p->tsid, i ));

	*pUpdatedFlag = 1;
	return i;
}

void AssignTuneData( DVB_CHANNEL* pDVBChannel, TUNE_DAT* pTuneData )
{
	ASSERT( sizeof(TERRESTRIAL_TUNE) <= sizeof(SATELLITE_TUNE) );
	ASSERT( sizeof(CABLE_TUNE) <= sizeof(SATELLITE_TUNE) );
	pDVBChannel->dvb.s = pTuneData->u.s;
}

int UpdateChannelList( CHANNEL_LIST *pChannelList, TUNE_DAT* pTuneData )
{
	int i,flag = 0;;
	if ( pChannelList->stream_format == ATSC_STREAM )
	{
	} else
	{
		for ( i = 0; i<pChannelList->channel_num; i++ )
		{
			if ( pChannelList->channel[i].u.dvb.onid == pTuneData->onid &&
				 pChannelList->channel[i].u.dvb.tsid == pTuneData->tsid &&
				 pChannelList->channel[i].state && (pChannelList->channel[i].state & 0x02)==0 )
			{
				AssignTuneData( &pChannelList->channel[i].u.dvb, pTuneData );
				pChannelList->channel[i].state |= 0x02;
				flag = 1;
			}
		}
	}
	return flag;
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

inline static int IsInDVBChannelList( CHANNEL_LIST *pChannelList, int nOnid, int nTsid, int nSid )
{
	int i;
	for ( i = 0; i<pChannelList->channel_num; i++ )
	{
		if ( pChannelList->channel[i].u.dvb.onid == nOnid &&
			 pChannelList->channel[i].u.dvb.tsid == nTsid && 
			 pChannelList->channel[i].u.dvb.sid  == nSid )
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
	int i, k, updated = 0;

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
		{	
			TUNE_DAT *tune_p;


			tune_p = GetTuneData( pTuneList, pChannelData->u.dvb[0].onid, 
											 pChannelData->u.dvb[0].tsid );
		
			k = pChannelList->channel_num;
			for ( i = 0; i<pChannelData->num_channel; i++ )
			{
				if ( IsInDVBChannelList( pChannelList, pChannelData->u.dvb[i].onid, 
								  pChannelData->u.dvb[i].tsid, pChannelData->u.dvb[i].sid ) )
					continue;
				//expend channel list
				if ( pChannelList->total_list_num <= pChannelList->channel_num )
					ExpendChannelList( pChannelList, 10 );

				pChannelList->channel[k].u.dvb = pChannelData->u.dvb[i];  
				pChannelList->channel[k].state = 0x01;
				if ( tune_p != NULL )
				{
					AssignTuneData( &pChannelList->channel[k].u.dvb, tune_p );
					pChannelList->channel[k].state |= 0x02;
				}
				SageLog(( _LOG_TRACE, 2, "found DVB channel %d name:%s onid:%d tsid:%d sid:%d  service:%d", k, 
						pChannelList->channel[k].u.dvb.name, pChannelList->channel[k].u.dvb.onid,
						pChannelList->channel[k].u.dvb.tsid, pChannelList->channel[k].u.dvb.sid,
						pChannelList->channel[k].u.dvb.service_type ));
				k++;
				pChannelList->channel_num++;
				updated = 1;

			}
		}
	}

	return updated;
}

static int CheckChannelStateReady( CHANNEL_LIST *pChannelList )
{
	int i;
	for ( i = 0; i<pChannelList->channel_num; i++ )
	{
		if ( pChannelList->channel[i].state != 0x03 )
			return 0;
	}
	return 1;
}
static int AddProgrmToList( PROGRAM_LIST *pProgramList, uint16_t nTsid, uint16_t nProgramId, uint16_t nChannel )
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
	pProgramList->program[i].service = 0;
	pProgramList->program_num++;
	return i;
}

int MergeChannelListProgramList(  SCAN *pScan )
{
	PROGRAM_LIST *pProgramList = &pScan->program_list;
	CHANNEL_LIST *pChannelList = &pScan->channel_list;
	TUNE *pTune = &pScan->tune;

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
					  pScan->tune.u.atsc.physical_ch, program_id );
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

int GetStreamFormat( SCAN *pScan, uint16_t* pStreamFormat, uint16_t *pSubFormat )
{
	*pStreamFormat = pScan->stream_format;
	*pSubFormat    = pScan->sub_format;
	return  ( (pScan->state & 0xff) > 0 );
}

CHANNEL_LIST* GetChannelList( SCAN *pScan )
{
	return &pScan->channel_list;
}

TUNE_LIST* GetTuneList( SCAN *pScan )
{
	return &pScan->tune_list;
}

PROGRAM_LIST* GetProgramList( SCAN *pScan )
{
	return &pScan->program_list;
}

int GuessChannelInfSize( CHANNEL_LIST *pChannelList )
{
	if ( pChannelList == NULL )	return 0;
	return pChannelList->channel_num * 50;
}
int  GuessTuneInfSize( CHANNEL_LIST *pChannelList )
{
	if ( pChannelList == NULL )	return 0;
	return pChannelList->channel_num * 125;
}
int  GuessNitInfSize( TUNE_LIST *pTuneList )
{
	if ( pTuneList == NULL ) return 0;
	return pTuneList->tune_num * 80;
}
int  GuessProgramInfSize( PROGRAM_LIST *pProgramList )
{
	int i, num = 0;;
	for ( i=0; i<pProgramList->program_num; i++ )
	{
		if ( pProgramList->program[i].service )
			num++;
	}
	return num * 30;
}

int MakeChannelInf( CHANNEL_LIST *pChannelList, char* pInfBuf, int nInfBufSize )
{
	int i;
	int pos = 0;
	if ( pChannelList->stream_format == ATSC_STREAM )
	{
		for ( i = 0; i <pChannelList->channel_num && nInfBufSize > pos; i++ )
			if (  pChannelList->channel[i].state == 0x03 )
				pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "%d-%d-%d-%d(%s)%s%s;\n", 
						 pChannelList->channel[i].u.atsc.physical_ch, 
						 pChannelList->channel[i].u.atsc.major_num, 
						 pChannelList->channel[i].u.atsc.minor_num, i, 
						 pChannelList->channel[i].u.atsc.name,
						 pChannelList->sub_format==2?"QAM":"ATSC",
						 pChannelList->channel[i].u.atsc.service_type == 3?"(r)":"" );
						 //pChannelList->channel[i].u.atsc.service_type == 3?"":"" ); //disable radio tag

	} else
	if ( pChannelList->stream_format == DVB_STREAM )
	{
		for ( i = 0; i <pChannelList->channel_num && nInfBufSize > pos; i++ )
			if (  pChannelList->channel[i].state == 0x03 )
				pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "%d-%d-%d-%d(%s)DVB%s%s;\n", 
						 pChannelList->channel[i].u.dvb.onid, 
						 pChannelList->channel[i].u.dvb.tsid, 
						 pChannelList->channel[i].u.dvb.sid, i, 
						 pChannelList->channel[i].u.dvb.name, 
						 pChannelList->sub_format==1?"-T":pChannelList->sub_format==2?"-C":pChannelList->sub_format==3?"-S":"",
						 pChannelList->channel[i].u.dvb.service_type == 2?"(r)":"" );
						 //pChannelList->channel[i].u.dvb.service_type == 2?"":"" );//disable radio tag
	}
	
	return pos;
}

int MakeTuneInf( CHANNEL_LIST *pChannelList, char* pInfBuf, int nInfBufSize )
{
	int i, pos = 0;
	if ( pChannelList->stream_format == ATSC_STREAM )
	{
		if ( pChannelList->sub_format == TERRESTRIAL )
		{
			for ( i = 0; i<pChannelList->channel_num; i++ )
			{
				if (  pChannelList->channel[i].state == 0x03 )
					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "CH:%d major:%d minor:%d prog:%d phy:%d ", i,
						pChannelList->channel[i].u.atsc.major_num, 
						pChannelList->channel[i].u.atsc.minor_num, 
						pChannelList->channel[i].u.atsc.program_id, 
						pChannelList->channel[i].u.atsc.physical_ch	);

					pos += STR_FREQ( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.atsc.u.atsc.freq ); 
					pos += STR_BAND( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.atsc.u.atsc.band ); 

					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "ctrl:%d #:%s %s\n", 1,
						pChannelList->channel[i].u.atsc.name,
						pChannelList->channel[i].u.atsc.service_type==3?"(r)":"" );
						//pChannelList->channel[i].u.atsc.service_type==3?"":"" );//disable radio tag
			}

		} else
		if ( pChannelList->sub_format == CABLE )
		{
			for ( i = 0; i<pChannelList->channel_num; i++ )
			{
				if (  pChannelList->channel[i].state == 0x03 )
					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "CH:%d major:%d minor:%d prog:%d phy:%d ", i,
						pChannelList->channel[i].u.atsc.major_num, 
						pChannelList->channel[i].u.atsc.minor_num, 
						pChannelList->channel[i].u.atsc.program_id, 
						pChannelList->channel[i].u.atsc.physical_ch	);

					pos += STR_FREQ(        pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.atsc.u.qam.freq        ); 
					pos += STR_SYMBOL_RATE( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.atsc.u.qam.symbol_rate );
					pos += STR_MODULATION(  pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.atsc.u.qam.modulation  );

					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "ctrl:%d #:%s %s\n", 1,
						pChannelList->channel[i].u.atsc.name,
						pChannelList->channel[i].u.atsc.service_type==3?"(r)":"" );
						//pChannelList->channel[i].u.atsc.service_type==3?"":"" ); //disable radio tag
			}
		}

	} else
	if ( pChannelList->stream_format == DVB_STREAM )
	{
		if ( pChannelList->sub_format == TERRESTRIAL )
		{
			for ( i = 0; i<pChannelList->channel_num; i++ )
				if (  pChannelList->channel[i].state == 0x03 )
				{
					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "CH:%d onid:%d tsid:%d sid:%d ", i,
						pChannelList->channel[i].u.dvb.onid, 
						pChannelList->channel[i].u.dvb.tsid,
						pChannelList->channel[i].u.dvb.sid );
	
					pos += STR_FREQ( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.t.freq ); 
					pos += STR_BAND( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.t.band ); 
					pos += STR_MODE( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.t.trans_mode ); 

					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "ctrl:%d #:%s %s\n", 
						pChannelList->channel[i].u.dvb.ca ? 2:1,
						pChannelList->channel[i].u.dvb.name,
						pChannelList->channel[i].u.dvb.service_type==2?"(r)":"" );
						//pChannelList->channel[i].u.dvb.service_type==2?"":"" );//disable radio tag

				}
				             
		} else
		if ( pChannelList->sub_format == CABLE )
		{
			for ( i = 0; i<pChannelList->channel_num; i++ )
				if (  pChannelList->channel[i].state == 0x03 )
				{
					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "CH:%d onid:%d tsid:%d sid:%d ", i,
						pChannelList->channel[i].u.dvb.onid, 
						pChannelList->channel[i].u.dvb.tsid,
						pChannelList->channel[i].u.dvb.sid );

					pos += STR_FREQ(        pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.c.freq        ); 
					pos += STR_SYMBOL_RATE( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.c.symbol_rate );
					pos += STR_MODULATION(  pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.c.modulation  );
					pos += STR_FEC_RATE_IN( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.c.fec_in_rate );
					pos += STR_FEC_OUT(     pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.c.fec_out     );

					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "ctrl:%d #:%s %s\n", 
						pChannelList->channel[i].u.dvb.ca ? 2:1,
						pChannelList->channel[i].u.dvb.name,
						pChannelList->channel[i].u.dvb.service_type==2?"(r)":"" );
						//pChannelList->channel[i].u.dvb.service_type==2?"":"" ); //disable radio tag
				}

		} else
		if ( pChannelList->sub_format == SATELLITE  )
		{
			for ( i = 0; i<pChannelList->channel_num; i++ )
				if (  pChannelList->channel[i].state == 0x03 )
				{
					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "CH:%d onid:%d tsid:%d sid:%d ", i,
						pChannelList->channel[i].u.dvb.onid, 
						pChannelList->channel[i].u.dvb.tsid,
						pChannelList->channel[i].u.dvb.sid );

					pos += STR_FREQ(        pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.freq );
					pos += STR_SYMBOL_RATE( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.symbol_rate);
					pos += STR_MODULATION(  pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.modulation );
					pos += STR_FEC_RATE_IN( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.fec_rate   );
					pos += STR_FEC_IN(		pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.fec		  );
					pos += STR_POL(         pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.pol		  );


					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "ctrl:%d #:%s %s\n", 
						pChannelList->channel[i].u.dvb.ca ? 2:1,
						pChannelList->channel[i].u.dvb.name,
						pChannelList->channel[i].u.dvb.service_type==2?"(r)":"" );
						//pChannelList->channel[i].u.dvb.service_type==2?"":"" ); //disable radio tag
				}
		}
	}
	return pos;
}

int MakeProgramInf( PROGRAM_LIST *pProgramList, SCAN* pScan, char* pInfBuf, int nInfBufSize )
{
	int i;
	int pos = 0;
	if ( pProgramList->stream_format == ATSC_STREAM )
	{
		for ( i = 0; i <pProgramList->program_num && nInfBufSize > pos; i++ )
			if (  pProgramList->program[i].service )
				pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "%d-%d-%d-%d(DTV-%d-%d)%s%s;\n", 
						 pScan->tune.u.atsc.physical_ch, pProgramList->program[i].program_id, i, i, 
						 pScan->tune.u.atsc.physical_ch, pProgramList->program[i].program_id,  
						 pProgramList->sub_format==2?"QAM":"ATSC", 
						 pProgramList->program[i].service == 3?"(r)":"" ); 
						 //pProgramList->program[i].service == 3?"":"" ); //disable radio tag

	} else
	if ( pProgramList->stream_format == DVB_STREAM )
	{
		for ( i = 0; i <pProgramList->program_num && nInfBufSize > pos; i++ )
			if (  pProgramList->program[i].service )
				pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "0-%d-%d-%d(DVT-0-%d-%d)DVB%s%s;\n", 
						 pProgramList->program[i].tsid,	 pProgramList->program[i].program_id, i, 
						 pProgramList->program[i].tsid,	 pProgramList->program[i].program_id,
						 pProgramList->sub_format==1?"-T":pProgramList->sub_format==2?"-C":pProgramList->sub_format==3?"-S":"",
						 pProgramList->program[i].service == 3?"(r)":"" );
						 //pProgramList->program[i].service == 3?"":"" ); //disable radio tag
	}
	
	return pos;
}

int MakeNitInf( TUNE_LIST *pTuneList, char* pInfBuf, int nInfBufSize )
{
	int i, pos = 0;
	if ( pTuneList->sub_format == TERRESTRIAL )
	{
		for ( i = 0; i<pTuneList->tune_num; i++ )
		{
			if ( pTuneList->tune[i].onid == 0 || pTuneList->tune[i].tsid == 0 )
				continue;

			pos += STR_FREQ( pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.t.freq ); 
			pos += STR_BAND( pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.t.band ); 
			pos += STR_MODE( pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.t.trans_mode ); 

			//pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "onid:%d tsid:%d ", 
			//	pTuneList->tune[i].onid, 
			//	pTuneList->tune[i].tsid );
			pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "\n" );
		}
	} else
	if ( pTuneList->sub_format == CABLE )
	{
		for ( i = 0; i<pTuneList->tune_num; i++ )
		{
			if ( pTuneList->tune[i].onid == 0 || pTuneList->tune[i].tsid == 0 )
				continue;
			pos += STR_FREQ(        pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.c.freq        ); 
			pos += STR_SYMBOL_RATE( pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.c.symbol_rate );
			pos += STR_MODULATION(  pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.c.modulation  );
			pos += STR_FEC_RATE_IN( pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.c.fec_in_rate );
			pos += STR_FEC_OUT(     pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.c.fec_out	 );

			//pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "onid:%d tsid:%d ", 
			//	pTuneList->tune[i].onid, 
			//	pTuneList->tune[i].tsid );
			pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "\n" ); 

		}
	} else
	if ( pTuneList->sub_format == SATELLITE )
	{
		for ( i = 0; i<pTuneList->tune_num; i++ )
		{
			if ( pTuneList->tune[i].onid == 0 || pTuneList->tune[i].tsid == 0 )
				continue;

			pos += STR_FREQ(        pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.s.freq );
			pos += STR_SYMBOL_RATE( pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.s.symbol_rate );
			pos += STR_MODULATION(  pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.s.modulation );
			pos += STR_FEC_RATE_IN( pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.s.fec_rate );
			pos += STR_FEC_IN(		pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.s.fec		 );
			pos += STR_POL(         pInfBuf+pos, nInfBufSize-pos, pTuneList->tune[i].u.s.pol );

			//pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "onid:%d tsid:%d ", 
			//	pTuneList->tune[i].onid, 
			//	pTuneList->tune[i].tsid );
			pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "\n"  ); 
		}
	}
	return pos;
}


CHANNEL_LIST* DupChannelList( CHANNEL_LIST *pChannelList )
{
	int bytes;
	CHANNEL_LIST* channel_list = SAGETV_MALLOC( sizeof(CHANNEL_LIST) );
	bytes = pChannelList->total_list_num * sizeof(CHANNEL_DAT);
	if ( bytes )
	{
		channel_list->channel = SAGETV_MALLOC( bytes );
		memcpy( channel_list->channel, pChannelList->channel, bytes );
	}
	channel_list->channel_num    = pChannelList->total_list_num;
	channel_list->total_list_num = pChannelList->total_list_num;;
	channel_list->stream_format  = pChannelList->stream_format;
	channel_list->sub_format     = pChannelList->sub_format;
	return channel_list;
}

void ReleaseChannelList( CHANNEL_LIST *pChannelList )
{
	if ( pChannelList->channel )
		SAGETV_FREE( pChannelList->channel );
	SAGETV_FREE( pChannelList );
}

TUNE_LIST* DupTuneList( TUNE_LIST *pTuneList )
{
	int bytes;
	TUNE_LIST* tune_list = SAGETV_MALLOC( sizeof(TUNE_LIST) );
	bytes = pTuneList->total_list_num * sizeof(TUNE_DAT);
	if ( bytes )
	{
		tune_list->tune = SAGETV_MALLOC( bytes );
		memcpy( tune_list->tune, pTuneList->tune, bytes );
	}
	tune_list->stream_format  = pTuneList->stream_format;
	tune_list->sub_format     = pTuneList->sub_format;
	tune_list->tune_num       = pTuneList->tune_num;
	tune_list->total_list_num = pTuneList->tune_num;
	return tune_list;
}

void ReleaseTuneList( TUNE_LIST *pTuneList )
{
	if ( pTuneList->tune )
		SAGETV_FREE( pTuneList->tune );
	SAGETV_FREE( pTuneList );
}

uint16_t GetTracksAttr( SCAN*  pScan )
{
	return pScan->demuxer->tracks[0]->track_attr;
}

static void _display_av_inf( TRACKS *pTracks )
{
	//int slot_index = 0;
	char avinf[1024];

	TracksInfo( pTracks, avinf, sizeof(avinf) );
	SageLog(( _LOG_TRACE, 3, TEXT("INPUT-AVINF|%s"), avinf ));
}
