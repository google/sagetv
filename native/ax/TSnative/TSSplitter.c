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
// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)

#include <stdio.h>
#include <stdlib.h>	
#include <memory.h>	
#include <string.h>
	
#include "TSSplitter.h"

#define TS_STREAM 1
#define PS_STREAM 2

#include <stdarg.h>
#include <time.h>

int TranslateJWideString2( char* buf_out, int buf_out_size, unsigned short* buf_in );

#define ATSC_CHANNL_LOCKING_TIMEOUT  4000

#define	REMOVE_PTSFIX_LOG 1

#ifdef WIN32   
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}
#endif

#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _flog x
#endif

static bool flog_enabled=false;
void enable_tssplitter_log()
{
	flog_enabled = true;
}

void disable_tssplitter_log()
{
	flog_enabled = false;
}

static void _flog( char* logname, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	
	if ( !flog_enabled ) return;

	fp = fopen( logname, "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );
	fprintf( fp, "%02d/%02d/%d %02d:%02d:%02d  ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
	ltm.tm_hour, ltm.tm_min, ltm.tm_sec );  
	va_start(args, cstr);
	vfprintf( fp, cstr, args );
	va_end(args);
	fclose( fp );
}
static void _flog_check()
{
	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
	}
}

static bool _PTSFIX_DISABLE()
{
	FILE* fp = fopen( "PTSFIX.DISABLE", "r" );
	if ( fp != NULL )
	{
		fclose( fp );
		return true;
	}
	return false;
}

static bool _ENABLE_TELETEXT()
{
	FILE* fp = fopen( "TELETEXT.ENABLE", "r" );
	if ( fp != NULL )
	{
		fclose( fp );
		return true;
	}
	return false;
}

//static void logfile( char* str )
//{
//	FILE* fp = fopen( "TSSplitter.log", "a+" ); 
//	if ( fp != NULL )
//	{
//		fprintf( fp, "**##%d %s\r\n", GetTickCount(), str );
//		fclose( fp );
//	}
//}

//static void RebuildPTS( TSSPLT* Parser, int Contrl, int streamIndex, LONGLONG *llPTS, LONGLONG *llDTS );
//static void TracePTSJittle( TSSPLT* parser, int Contrl, int streamIndex, LONGLONG *llPTS, LONGLONG *llDTS );
static bool AVInfoReady( TSSPLT* ts, bool *done  );
static void NotifySteamInfo(  TSSPLT* ts );
static long EPG_dump(void* handle, short, void*);
static long PMT_dump(void* handle, short bytes, void* data );
static int PickupMainAudioTrack( TSSPLT* ts, AVSTREAM_INFO* StreamInfo, int stream_num  );
static int FindMainAudioTrack( TSSPLT* ts, int channel );
static void DumpChannelPid( TSSPLT* ts, AVSTREAM_INFO* StreamInfo, int stream_num );
static void ResetPTSFIX( TSSPLT* ts );

int    GetBlockSize();
//////////////////////////////////////////////////////////////////////////////////////////////////
unsigned long TSAVStreamHook( void* handle, short channel, void* data )
{
	TS_AV_INFO* av_info = (TS_AV_INFO*)data;
	int stream_index;
	unsigned char* desc;int length;
	TS_PARSER* parser;
	TSSPLT* ts;

	if ( av_info == NULL || handle == NULL )
		return -1;

	parser = (TS_PARSER *)handle;
	ts = (TSSPLT*)parser->env;

	stream_index = av_info->sub_channel;

	if ( ts == NULL || stream_index >= MAX_STREAM_NUM || stream_index < 0  )
		return -1;

	if ( ts->channel_picked && channel == ts->selected_channel )
	{
		PES_INFO PESInfo={0};
		int ret = 0;
		bool bPESInfoReady = false;

		if ( av_info->start_group_flag ) 
			bPESInfoReady = UnpackPESHeader( (const unsigned char*)av_info->data, av_info->bytes, &PESInfo ) > 0;
		PESInfo.bGroupStart = av_info->start_group_flag;

		if ( ts->stream_type[stream_index] == 0x0 )
		{
			if ( av_info->type == 0x06 && GetPMTDescData( parser, av_info->pid, &desc, &length ) &&
				( IsTeletextTypeDescriptor( (char*)desc, length ) || IsSubtitleDescriptor((char*)desc, length) ) )
				ret = TELTEXT_TYPE;
			else
				ret = DecodeAVHeader( &ts->av[av_info->sub_channel], &PESInfo, (unsigned char)av_info->type,
		                     (const unsigned char*)av_info->data, (int)av_info->bytes );			
		}

		if ( ret )
		{
			ts->stream_type[stream_index] = ret;
			ts->stream_ts_type[stream_index]= av_info->type;
			ts->stream_pid[stream_index]= av_info->pid; 
			if ( IsAudioType( av_info->type ) )
			{
				unsigned long language; unsigned char type;
				if ( GetPMTDescData( parser, av_info->pid, &desc, &length ) && 
					 ( GetAudioLanguageInfo( desc, length, &language, &type ) || 
					 GetTelexLanguageInfo( desc, length, &language, &type )) )
				{	
					sprintf( (char*)ts->stream_desc[av_info->sub_channel], "lang=%c%c%c %s", 
							 (char)((language>>24)&0xff), (char)(language>>16)&0xff, (char)(language>>8)& 0xff, 
							 type == 1 ? "(HI)" : type == 2 ? "(VI)":"" );
				}
			}
		}

		if ( ts->builder_state < 2 ) 
		{
			if ( bPESInfoReady )
				ts->stream_id[stream_index] = PESInfo.StreamId;

			if ( !bPESInfoReady )
			{
				PESInfo.HeaderLen = 0; 
				PESInfo.PacketLen = av_info->bytes;
				PESInfo.StreamId = ts->stream_id[stream_index];
			}

			if ( IsVideoType( ts->stream_type[stream_index] )  ) 
			{
				ts->video_state = 1;
				ts->video_stream_index = stream_index;
				//printf( "ParserTS found video pid:0x%02x\n", av_info->pid );
			}
			
			//if( IsAudioType( ts->stream_type[stream_index] )  )
			//{
			//	ts->audio_state = 2;
			//	printf( "found audio pid:0x%02x\n", av_info->pid );
			//}
			if ( ts->video_state  )
			{
				ts->builder_state = 2;
				TSPidStatEnable( parser, false );
			}
		}
		
		if ( PESInfo.HasPts )
		{
			ts->pts_inf[stream_index].newPTS = PESInfo.llPts;
			if ( ts->pts_inf[stream_index].syncPTS == 0 ) 
				ts->pts_inf[stream_index].syncPTS = PESInfo.llPts;
			if ( ts->pts_inf[stream_index].lastPTS < PESInfo.llPts ) 
				ts->pts_inf[stream_index].lastPTS = PESInfo.llPts;
		}
		if ( PESInfo.HasDts )
		{
			ts->pts_inf[stream_index].newDTS = PESInfo.llDts;
			if ( ts->pts_inf[stream_index].syncDTS == 0 ) 
				ts->pts_inf[stream_index].syncPTS = PESInfo.llDts;
			if ( ts->pts_inf[stream_index].lastDTS < PESInfo.llDts ) 
				ts->pts_inf[stream_index].lastDTS = PESInfo.llDts;
		}

		//rebuild PTS
		/*
		if ( ts->rebuild_pts && PESInfo.HasPts )
		{
			LONGLONG    llPTS=-1, llDTS=-1;
			llPTS = PESInfo.llPts;
			if ( PESInfo.HasDts )
				llDTS = PESInfo.llDts;

			if ( ts->rebuild_pts == 4 )
			{
				TracePTSJittle( ts, ts->rebuild_pts, stream_index, &llPTS, &llDTS );
			} else
			{
				RebuildPTS( ts, ts->rebuild_pts, stream_index, &llPTS, &llDTS );
				UpdatePESPTS( (unsigned char*)av_info->data, av_info->bytes, &PESInfo, &llPTS, &llDTS );
			}
		}
		*/

		if (  ts->stream_type[av_info->sub_channel] == TELTEXT_TYPE && ts->exclusive_teletext )	
			return 0;

		//start build video or audio from start frame (PES header)
		if ( ts->builder_state >= 2 ) 
		{
			int packet_increment = 1;

			// Insert PAT and PMT packets at interval.
			if (ts->ts_packet_num % 2000 == 0)
			{				
				PushPat( ts->ts_builder );
				packet_increment++;
			}

			if (ts->ts_packet_num % 8000 == 0)
			{
				PushPmt( ts->ts_builder, ts->program_id );
				packet_increment++;
			}

			PushAVPacketData( ts->ts_builder, ts->program_id, av_info->sub_channel,
					  av_info->start_group_flag, av_info->data, av_info->bytes );

			ts->ts_packet_num += packet_increment;
		}

		ts->stream_counter[stream_index]++;
		{
			bool done;
			if (  ts->builder_state >= 2 && !ts->AVinfo_notify_posted && AVInfoReady( ts, &done ) )
			{
				//Notify caller, stream type information
				NotifySteamInfo( ts );
				//flog(( "native.log", "AVINF was posted.\n" ));
				ts->AVinfo_notify_posted = done;

				//dump pid table for hardware demulx
				if ( ts->pfnPidTblDump != NULL  )
				{
					int stream_num = GetStreamNum( ts->parser, ts->selected_channel );
					if ( stream_num > MAX_STREAM_NUM ) stream_num = MAX_STREAM_NUM;
					if ( stream_num > 0 ) 
					{
						AVSTREAM_INFO* StreamInfo = (AVSTREAM_INFO*)sagetv_malloc( sizeof(AVSTREAM_INFO)*stream_num );
						if ( StreamInfo != NULL ) 
							stream_num = GetStreamList( parser, channel, StreamInfo, stream_num );				
						else
							stream_num = 0;
						DumpChannelPid( ts, StreamInfo, stream_num );
						sagetv_free( StreamInfo );
					}
				}
			} 
		}
	}

	return 0;
}

unsigned long TSAVSignalHook( void* handle, short channel, void* data )
{
	TS_PARSER* parser;
	TSSPLT* ts;
	SIGNAL_INFO* signal_info = (SIGNAL_INFO*)data;
	int i;
	//TS_AV_INFO* av_info = (TS_AV_INFO*)data;
	if ( signal_info == NULL || handle == NULL )
		return -1;

	parser = (TS_PARSER *)handle;
	ts =  (TSSPLT*)parser->env;

	if ( ts == NULL )
		return -1;

	if ( signal_info->type == STREAM_START )
	{
		bool program_updated = false;
		unsigned short tsid = GetProgramTSID( parser );
		for ( i = 0; i<ts->tsids_num; i++ )
			if ( ts->tsids[i] == tsid )
				break;
		if ( ts->tsids[i] != tsid || ts->tsids_num == 0 )
		{
			if ( i<MAX_SERVICE_NUM )
			{
				ts->tsids[ts->tsids_num++] = tsid;
				program_updated = true;
			}
		}

		if ( program_updated )
		{
			int total_channel;
			
			PROGRAM_INFO *Channels;
			total_channel = GetProgramNumber( parser );
			
			Channels = (PROGRAM_INFO *)sagetv_malloc( sizeof(PROGRAM_INFO)*total_channel );
			total_channel = GetProgramList( parser, Channels, total_channel );
			{
				printf( "TS Program (total channel:%d, tsid:%d )\n", total_channel, tsid );
				flog(( "native.log",  " TS Program (total channel:%d, tsid:%d )\n", total_channel, tsid ));
				for ( i = 0; i<total_channel; i++ )
				{
					printf( "Channel %02d  PMT pid:0x%04x program:0x%04x service:%d \n", 
						Channels[i].channelID+1, Channels[i].pid, (unsigned short)Channels[i].programID, Channels[i].type );

					flog(( "native.log",  "  Channel %02d  PMT pid:0x%04x program:0x%04x (%d) service:%d  \n", 
						Channels[i].channelID+1, Channels[i].pid, 
						(unsigned short)Channels[i].programID, (unsigned short)Channels[i].programID,
						Channels[i].type ));
						
				}
			}
			
			printf( "\n" );
			if ( ts->tsid == 0 && ts->tsids_num > 1 )
			{
				printf( "There are multiple service in this stream, tsid%d. \n", tsid );
				flog((  "native.log",  "There are multiple services in this stream, tsid:%d \n", tsid ));
				if ( ts->builder_state == 0 )//&& ts->stream_network_type != ATSC_STREAM_TYPE )
				{
					SelectTSID( parser, tsid );
					ts->cur_tsid = tsid;
					printf( "pick up a service tsid:%d. \n", tsid );
					flog((  "native.log",  "pick up a service . tsid:%d \n", tsid ));
				}

			}

			sagetv_free( Channels );
			//debug use only, remove it for relaese
		}
	}
	

	if ( signal_info->type == STREAM_READY && -1 != channel) 
	{	
		int ret = 0;
		int stream_num;
		bool encrypted;
		AVSTREAM_INFO* StreamInfo;
		stream_num = GetStreamNum( ts->parser, channel );
		if ( stream_num <= 0 ) return 0;
		if ( stream_num > MAX_STREAM_NUM ) stream_num = MAX_STREAM_NUM;
		StreamInfo = (AVSTREAM_INFO*)sagetv_malloc( sizeof(AVSTREAM_INFO)*stream_num );
		if ( StreamInfo == NULL ) return 0;

		stream_num = GetStreamList( parser, channel, StreamInfo, stream_num );
		encrypted = IsScrambleTSdProgram( ts, (unsigned short)StreamInfo[0].programID );
		printf( "Channel:%d  ( PMT pid:0x%04x, Program:0x%04x ) (%d).\n", channel+1, 
				StreamInfo[0].pid, StreamInfo[0].programID, encrypted  );
		flog(( "native.log", "      Channel:%d streams: ( PMT pid:0x%04x, Program:0x%04x ) (%d).\n", channel+1, 
				StreamInfo[0].pid, StreamInfo[0].programID, encrypted  ));
					
		for ( i = 1; i< stream_num; i++ )
			printf( "\tType:0x%02x Pid:0x%03x\n", StreamInfo[i].streamType, StreamInfo[i].pid );

		////pick up channel
		if( !ts->channel_picked )
		{
			int channel;

			//use ATSC 1000-minor as program channel selection (for testing) 
			if ( ts->lock_ATSC_channel && ts->ATSC_major == 1000 )
			{
				ts->selected_program = ts->ATSC_minor;
				flog(( "native.log", "pickup up channel with program %d (major 1000 revserved as program id selection).\n", ts->ATSC_minor ));
			} else
			if ( ts->lock_ATSC_channel && ts->ATSC_major != 0 )
			{
				int program = GetSIProgramByMajorMinor( ts->si_parser,  ts->ATSC_major,  ts->ATSC_minor );
				if ( program > 0 )
				{
					 char temp[32];
					 ts->selected_program = program;
					 GetSIProgramName( ts->si_parser, program, ts->channel_name, sizeof(ts->channel_name) );
					 TranslateJWideString2( temp, sizeof(temp), (unsigned short*)ts->channel_name );
					 flog(( "native.log", "locked ATSC channel by major:%d, minor:%d, program:%d name:%s\n",
						     ts->ATSC_major, ts->ATSC_minor, program, temp  ));
				}
				else
				if ( program == 0 )
				{
					unsigned long packets;
					if ( ( packets = PacketInputNum( ts ) )<  ATSC_CHANNL_LOCKING_TIMEOUT )
					{
						ret = 3;
						flog(( "native.log", "wait for more packets to lock channel by major-minor  (2)!\n"  ));
					}
					else
					{
						ts->lock_ATSC_channel = 0;
						flog(( "native.log", "Timeout locking ATSC channel by major:%d, minor:%d (timeout:%ld)\n", 
						                                      ts->ATSC_major, ts->ATSC_minor, packets ));
					}

				} else
				if ( program < 0 )
				{
					 ts->lock_ATSC_channel = 0;
					 flog(( "native.log", "Abort locking ATSC channel by major:%d, minor:%d number\n", 
						                                      ts->ATSC_major, ts->ATSC_minor ));
				}
			}


			if (  ret != 3 &&ts->selected_program != 0 )
			{
				channel = GetTSChannelNum( parser, ts->selected_program );
				
				if ( channel < 0 )
				{
					if ( ts->selected_channel == 0xffff  )
					{
						int new_program = -1;
						if ( ts->channel_name[0] )
							new_program = GetSIProgramByName( ts->si_parser, ts->channel_name );

						if ( new_program == 0 )
						{
							ret = 3;
							flog(( "native.log", "wait for more packets to lock channel by program_id (sid)!\n"  ));
						} else
						if ( new_program > 0 )
						{
							int ret = GetTSChannelNum( parser, new_program );
							if ( ret >= 0 )
							{
								ts->selected_channel = (unsigned short)ret;
								ts->selected_program = new_program;
								flog(( "native.log", "Channel %d is found by channel name, program %d!\n", 
									ts->selected_channel+1, ts->selected_program  ));
								ts->channel_picked = true;
								SelectChannel( ts->parser, ts->selected_channel );
							} else
							{
								flog(( "native.log", "Channel is not found for program %d!\n",ts->selected_program  ));
							}
								
						} else
						{
							flog(( "native.log", "Channel is not found for program %d abort.\n", ts->selected_program  ));
							ts->channel_picked = true;
						}

					} else
					{
						flog(( "native.log", "Channel is not found for program:%d, use channel:%d.\n",
							ts->selected_program, ts->selected_channel+1 ));
						ts->channel_picked = true;
						SelectChannel( ts->parser, ts->selected_channel );
					}
				} else
				{

					if ( ts->selected_channel == 0xffff )
					{
						flog(( "native.log", "Channel %d is found for program %d.\n", 
								channel+1, ts->selected_program  ));
						ts->channel_picked = true;
						ts->selected_channel = channel;
						SelectChannel( ts->parser, channel );
					}
					else
					{
						if ( ts->selected_channel == channel )
						{
							flog(( "native.log", "Channel %d is found for program %d!\n", 
									channel+1, ts->selected_program  ));
							ts->channel_picked = true;
							ts->selected_channel = channel;
						}
						else
						{
							int new_program = -1;
							flog(( "native.log", "Channel %d is found for program:%d, but inconsistance with specified channel:%d!!!\n", 
									channel+1, ts->selected_program, ts->selected_channel+1 ));

							new_program = GetSIProgramByName( ts->si_parser, ts->channel_name );

							if ( new_program > 0 )
							{
								int ret = GetTSChannelNum( parser, new_program );
								if ( ret >= 0 )
								{
									ts->selected_channel = (unsigned short)ret;
									ts->selected_program = new_program;
									flog(( "native.log", "Channel %d is found by channel name %s, program %d!\n", 
										ts->selected_channel+1, ts->channel_name, ts->selected_program  ));
									ts->channel_picked = true;
									SelectChannel( ts->parser, ts->selected_channel );

								} else
								{
									ts->selected_channel = channel;
									ts->channel_picked = true;
									SelectChannel( ts->parser, channel );
									flog(( "native.log", "Set Channel %d!\n", ts->selected_channel+1 ));
								}
							} else
							if ( new_program == 0 )
							{
								ts->selected_channel = channel;
								ts->channel_picked = true;
								SelectChannel( ts->parser, channel );
								flog(( "native.log", "Set Channel %d!!\n", ts->selected_channel+1 ));
							}
							else
							{
								unsigned long packets;
								if ( ( packets = PacketInputNum( ts ) )<  ATSC_CHANNL_LOCKING_TIMEOUT )
								{
									ret = 3;
									flog(( "native.log", "wait for more packets to get channel name (%d).!\n", packets  ));
								} else
								{
									ts->selected_channel = channel;
									ts->channel_picked = true;
									SelectChannel( ts->parser, channel );
									flog(( "native.log", "Force set Channel %d!\n", ts->selected_channel+1 ));
								}
							}
						}
					}
				}
			}
			else
			if (  ret != 3 && ts->selected_program == 0 )
			{   //use select channel anyway
				ts->channel_picked = true;
			}
		}

		if ( channel >=0 && channel < MAX_STREAM_NUM && ts->channel_flag[channel] == 0 )
		{
			printf( "\tChannel:%d  ( PMT pid:0x%04x, Program:0x%04x ) (%d).\n", channel+1, 
					StreamInfo[0].pid, StreamInfo[0].programID, encrypted  );
			flog(( "native.log", "\tChannel:%d streams: ( PMT pid:0x%04x, Program:0x%04x ) (%d).\n", channel+1, 
					StreamInfo[0].pid, (unsigned short)StreamInfo[0].programID, encrypted  ) );
			       
			for ( i = 1; i< stream_num; i++ )
			{
				printf( "\t\tType:0x%02x Pid:0x%03x\n", StreamInfo[i].streamType, StreamInfo[i].pid );
				flog(( "native.log", "\t\tType:0x%02x Pid:0x%03x\n", StreamInfo[i].streamType, StreamInfo[i].pid ));
			}
			ts->channel_flag[channel]=1;
		}


		if ( ts->channel_picked && ts->builder_state == 0 )
		{
			short *StreamTypeList;
			short *StreamPidList;
			DESC  *ESDescList;
			DESC   Desc;

			//re-get stream info from parser
			if ( ts->selected_channel != channel  )
			{
				sagetv_free( StreamInfo );
				StreamInfo = NULL;

				channel = ts->selected_channel;
				stream_num = GetStreamNum( ts->parser, channel );
				if ( stream_num > MAX_STREAM_NUM ) stream_num = MAX_STREAM_NUM;
				if ( stream_num > 0 ) 
				{
					StreamInfo = (AVSTREAM_INFO*)sagetv_malloc( sizeof(AVSTREAM_INFO)*stream_num );
					if ( StreamInfo != NULL ) 
						stream_num = GetStreamList( parser, channel, StreamInfo, stream_num );				
					else
						stream_num = 0;
				}
			}

			if ( StreamInfo != NULL )
			{
				StreamTypeList = (short*)sagetv_malloc(sizeof(short)*stream_num);
				StreamPidList  = (short*)sagetv_malloc(sizeof(short)*stream_num);
				ESDescList     = (DESC*)sagetv_malloc(sizeof(DESC)*stream_num);

				Desc.DescLength = (unsigned short)StreamInfo[0].Desc[0];
				Desc.DescInfo   = (unsigned char*)&StreamInfo[0].Desc[1];
				ts->audio_stream_num = 0;
				ts->video_stream_num = 0;
				for ( i = 1; i< stream_num; i++ )
				{
					StreamTypeList[i-1] = StreamInfo[i].streamType;
					StreamPidList[i-1] = StreamInfo[i].pid;
					ESDescList[i-1].DescLength = (short)StreamInfo[i].Desc[0];
					ESDescList[i-1].DescInfo = (unsigned char*)&StreamInfo[i].Desc[1];

					if ( IsAudioType( StreamInfo[i].streamType ) )
					{
						ts->audio_stream_num++;

						//exclusive teletex
						if ( StreamInfo[i].streamType == 6 &&
							( IsTeletextType( (char*)ESDescList[i-1].DescInfo ) || 
							  IsSubtitleType( (char*)ESDescList[i-1].DescInfo ) ) )
						{
							if ( ts->audio_stream_num ) ts->audio_stream_num--;
						}

					}
					if ( IsVideoType( StreamInfo[i].streamType ) )
						ts->video_stream_num++;
				}

				if ( ts->video_stream_num ) ts->channel_type |= VIDEO_PRESENT_MASK;
				if ( ts->audio_stream_num ) ts->channel_type |= AUDIO_PRESENT_MASK;

				if ( AddProgram( ts->ts_builder, ts->program_id, GetProgramPcrPID(parser), 
					                           &Desc, stream_num-1,  StreamTypeList, StreamPidList, ESDescList  ) >= 0  )
					ts->builder_state = 1;

				if ( ts->builder_state == 1 )
				{
					PushPat( ts->ts_builder );
					PushPmt( ts->ts_builder, ts->program_id );
					PushPat( ts->ts_builder );
					PushPmt( ts->ts_builder, ts->program_id );
				}

				sagetv_free( StreamTypeList );
				sagetv_free( StreamPidList );
				sagetv_free( ESDescList     );


			}
		}

		sagetv_free( StreamInfo );

		return ret;
	}

	
	if ( signal_info->type == PCR_READY )
	{
		flog(( "native.log", "\t\tPCR PID: 0x%x READY (reset StartPCR).\n", signal_info->pid ));
		ResetPTSFIX( ts );
	}

	if ( signal_info->type == STREAM_CLOSE )
	{
		//close
	}



	return 0;
}

///////////////////////////////////////////////////////////////////////////////////////////////////
//static int  _log_max_num = 100;
#define IS_MAIN_AV_STREAM ( ts->video_stream_index == av_info->sub_channel || ts->audio_stream_index == av_info->sub_channel )
static LONGLONG PTSFix( TS_PARSER * parser, TS_AV_INFO* av_info, PES_INFO* pPESInfo );
long PSAVStreamHook( void* handle, short channel, void* data )
{
	TS_AV_INFO* av_info = (TS_AV_INFO*)data;
	TS_PARSER * parser;
	LONGLONG llSCR = 0;
	TSSPLT* ts;
	PES_INFO PESInfo = {0};

	unsigned char* desc;int length;

	if ( av_info == NULL || handle == NULL )
		return -1;

	parser = (TS_PARSER *)handle;
	ts = (TSSPLT*)parser->env;

	if ( ts == NULL )
		return -1;

	//PCR sub_channel is 0xffff
	if ( av_info->type == PCR_STREAM_TYPE && av_info->sub_channel != -1 )
	{
		if ( ts->enable_ptsfix )
			PTSFix( parser, av_info, NULL );
		return 0;
	}

	if ( av_info->sub_channel >= MAX_STREAM_NUM  || av_info->sub_channel < 0 )
		return -1;

	PESInfo.bGroupStart = av_info->start_group_flag;

	//expected parser to anlyze stream type for unkown stream
	if ( channel == -1 )
	{
		if ( av_info->start_group_flag )
		{
			int stream_type;
			bool bPESInfoReady = false;

			PESInfo.bGroupStart = av_info->start_group_flag;
			if ( (bPESInfoReady = UnpackPESHeader( (const unsigned char*)av_info->data, av_info->bytes, &PESInfo ))>0 )
			{

				//in case bad code in PES header
				if ( PESInfo.HeaderLen > av_info->bytes ) PESInfo.HeaderLen = (unsigned short)av_info->bytes;

				stream_type = DecodeAVHeader( &ts->av[av_info->sub_channel], &PESInfo, (unsigned char)av_info->type,
										(const unsigned char*)av_info->data, (int)av_info->bytes );
				if ( stream_type )
				{
					av_info->type = stream_type;
					return 1;
				} else
					return 0;
			}
		}
		return 0;
	}


	if ( ts->channel_picked && channel == ts->selected_channel )
	{
		int ret = 0;
		bool bPESInfoRead = false;

		if ( ts->stream_pack_byte[av_info->sub_channel] == 0 || av_info->start_group_flag )
			bPESInfoRead = UnpackPESHeader(  (const unsigned char*)av_info->data, av_info->bytes, &PESInfo ) > 0;

		if ( bPESInfoRead )
		{
			if ( PESInfo.type == MPEG1_TYPE )
			{
				printf( "Warning: it's MPEG1 format in stream %d, skip it.\n", ts->selected_channel );
				memset( &PESInfo, 0x0, sizeof(PESInfo) );
				bPESInfoRead = false;
			} 
		    if ( IsAudioStreamId( PESInfo.StreamId ) && ((PESInfo.StreamId & 0xf0) == 0xd0 ) ) 
			{
				printf( "Warning: Auido stream ID is %d (should be 1100 xxxx (0xC0-0xCf) ) stream:%d, skip it.\n", 
					                  PESInfo.StreamId, ts->selected_channel );
				memset( &PESInfo, 0x0, sizeof(PESInfo) );
				bPESInfoRead = false;
			}

			if ( ts->stream_pack_byte[av_info->sub_channel] != 0 )
			{
				printf( "Warning: packet broken on stream:%d\n", av_info->sub_channel );
			}

			ts->stream_pack_byte[av_info->sub_channel] = (unsigned short)PESInfo.PacketLen;
		}

		if ( ts->stream_pack_byte[av_info->sub_channel] >= av_info->bytes )
			ts->stream_pack_byte[av_info->sub_channel] -= av_info->bytes;
		else
			ts->stream_pack_byte[av_info->sub_channel] = 0;


		if ( av_info->sub_channel < MAX_STREAM_NUM && ts->stream_type[av_info->sub_channel] == 0x0 )
		{
			if ( av_info->type == 0x06 && GetPMTDescData( parser, av_info->pid, &desc, &length ) )
			{
				if ( IsTeletextTypeDescriptor( (char*)desc, length ) || 
					 IsSubtitleDescriptor( (char*)desc, length ) ) 
					ret = TELTEXT_TYPE;
				else
				if ( GetAuidoType( (char*)desc, length ) <= 0 && IsPrivateData( (char*)desc, length ) )
					ret = PRIVATE_DATA_TYPE;
			
			}

			if ( IsAudioType( av_info->type )&& ts->disable_multi_audio_stream && ts->selected_audio_pid != av_info->pid )
			{
				//skip no selected audio
			}
			else
			if ( ret == 0 )
				ret = DecodeAVHeader( &ts->av[av_info->sub_channel], &PESInfo, (unsigned char)av_info->type,
		                     (const unsigned char*)av_info->data, (int)av_info->bytes );			
		}

		if ( ret )
		{
			ts->stream_type[av_info->sub_channel] = ret;
			ts->stream_ts_type[av_info->sub_channel] = av_info->type;
			ts->stream_pid[av_info->sub_channel] = av_info->pid; 
			flog(( "native.log", "DVB:Decoded:0x%x at stream index:%d pid:0x%x\n", 
			               (unsigned char)ret, av_info->sub_channel, av_info->pid ));

			//if ( ret == TELTEXT_TYPE && ts->exclusive_teletext )
			//	if ( ts->audio_stream_num ) ts->audio_stream_num--;

			if ( ts->selected_video_pid == 0 && IsVideoType( av_info->type ) )
			{
				ts->selected_video_pid = av_info->pid;
				ts->pcr_per_frame = GetPCRTimePerFrame( &ts->av[av_info->sub_channel] );
				ts->frame_pcr_inc = 0;
				flog(( "native.log", "decoded video pid:0x%02x  index:%d\n", av_info->pid, av_info->sub_channel  ) );
			}

			if ( IsAudioType( av_info->type ) && ret != TELTEXT_TYPE )
			{
				unsigned long language; unsigned char type;
				if ( GetPMTDescData( parser, av_info->pid, &desc, &length ) ) 
				{
		            if ( GetAudioLanguageInfo( desc, length, &language, &type ) || 
				                          GetTelexLanguageInfo( desc, length, &language, &type ) )
					{	
						sprintf( (char*)ts->stream_desc[av_info->sub_channel], "lang=%c%c%c %s", 
								 (char)((language>>24)&0xff), (char)(language>>16)&0xff, (char)(language>>8)& 0xff, 
								 type == 1 ? "(HI)" : type == 2 ? "(VI)":"" );
					}
				}

				if ( ts->builder_state == 0  ) //main audio track no found yet? (R5000 recording, has 2 PMTs)
					FindMainAudioTrack( ts, channel );

				flog(( "native.log", "decoded audio pid:0x%02x  index:%d\n", av_info->pid, av_info->sub_channel  ));
			}
		}

		if ( ( av_info->start_group_flag ) && ( ts->builder_state == 1 ) &&
			(!ts->video_state || !ts->audio_state ) ) 
		{
			if ( bPESInfoRead )
			{
				if ( IsVideoType( av_info->type ) && ts->stream_type[av_info->sub_channel] )
				{
					ts->video_stream_id = PESInfo.StreamId;
					ts->video_stream_type = av_info->type;
					ts->video_state = 1;
					ts->video_stream_index = (unsigned char)av_info->sub_channel;

					//printf( "found video pid:0x%02x\n", av_info->pid );

				}
				else
				if ( IsAudioType( av_info->type ) &&  ts->selected_audio_pid == av_info->pid )
				{
					ts->audio_stream_id = PESInfo.StreamId;
					ts->audio_stream_type = av_info->type;
					ts->audio_state = 1;
					ts->audio_stream_index = (unsigned char)av_info->sub_channel;

					//printf( "found audio pid:0x%02x\n", av_info->pid );

				}
			}

			if ( ts->video_state && ts->audio_state  )
			{
				int bytes;
				ts->builder_state = 2 ;
				if ( ts->disable_multi_audio_stream ) 
				{
					bytes = CreatSageStreamHeader( ts->ps_builder, 
								ts->video_stream_id,    ts->audio_stream_id,
								ts->video_stream_type,  ts->audio_stream_type, 
								ts->video_stream_num,   1,
								ts->video_stream_index, ts->audio_stream_index,
								0 );

				} else
					bytes = CreatSageStreamHeader( ts->ps_builder, 
								ts->video_stream_id,    ts->audio_stream_id,
								ts->video_stream_type,  ts->audio_stream_type, 
								ts->video_stream_num,   ts->audio_stream_num,
								ts->video_stream_index, ts->audio_stream_index,
								0 );
			}
		}

		//FIX in Autrila '7 digital' channel's teltext PTS not SYNC to Video, 
		//I disable teltext packing into PES temporarly. //ZQ
		if (  ts->stream_type[av_info->sub_channel] == TELTEXT_TYPE && ts->exclusive_teletext )	
			return 0;

		/* ZQ !!! rely on checking video frame type
		if ( ts->selected_video_pid == av_info->pid && 
			 ts->stream_type[av_info->sub_channel] == MPEG2_VIDEO || ts->stream_type[av_info->sub_channel] == MPEG1_VIDEO )
		{
			int type;
			if ( (type = SearFrameType(  (unsigned char*)av_info->data, av_info->bytes ) )>0 )
			{
				ts->frame_counter++;
				ts->frame_pcr_inc += ts->pcr_per_frame; 
				//printf( "%c ", type == 1 ? 'I' : type == 2 ? 'P' : type == 3 ? 'B' : 'X' );
			}
		}
		*/

		if ( PESInfo.HasPts )
		{
			ts->pts_inf[av_info->sub_channel].newPTS = PESInfo.llPts;
			if ( ts->pts_inf[av_info->sub_channel].syncPTS == 0 ) 
				ts->pts_inf[av_info->sub_channel].syncPTS = PESInfo.llPts;
			if ( ts->pts_inf[av_info->sub_channel].lastPTS < PESInfo.llPts ) 
				ts->pts_inf[av_info->sub_channel].lastPTS = PESInfo.llPts;
		}
		if ( PESInfo.HasDts )
		{
			ts->pts_inf[av_info->sub_channel].newDTS = PESInfo.llDts;
			if ( ts->pts_inf[av_info->sub_channel].syncDTS == 0 ) 
				ts->pts_inf[av_info->sub_channel].syncPTS = PESInfo.llDts;
			if ( ts->pts_inf[av_info->sub_channel].lastDTS < PESInfo.llDts ) 
				ts->pts_inf[av_info->sub_channel].lastDTS = PESInfo.llDts;
		}
		

		//////////////////////////////////////// SCR TimeStamp fix /////////////////////////////////////////////////////////

		if ( ts->enable_ptsfix )
			llSCR = PTSFix( parser, av_info, &PESInfo );

		if (  ts->builder_state == 2 ) 
		{			
			if ( IsVideoType( ts->stream_type[av_info->sub_channel] ) )
			{
				OutputVideoData( ts->ps_builder, 
					             av_info->sub_channel, 
					             av_info->type,
								 av_info->start_group_flag, 
								 av_info->data, av_info->bytes, llSCR );
			}	


			if ( IsAudioType( ts->stream_type[av_info->sub_channel] )  )
			{
				if ( ts->disable_multi_audio_stream && ts->selected_audio_pid != av_info->pid )
				{
					//drop audio stream here
				} else
				{
				   OutputAudioData( ts->ps_builder, 
					             av_info->sub_channel, 
								 av_info->type,
								 av_info->start_group_flag, 
								 av_info->data, av_info->bytes, llSCR );
				}
			} 
			ts->ts_packet_num++;
		}
		
	
		//Notify caller, stream type information
		ts->stream_counter[av_info->sub_channel]++;
		{
			bool done;
			if ( ts->builder_state == 2 && !ts->AVinfo_notify_posted && AVInfoReady( ts, &done ) )
			{
				bool video_ready, audio_ready;
				if ( ts->channel_type & VIDEO_PRESENT_MASK )
					video_ready = GetVideoNewStreamId( ts->ps_builder, ts->video_stream_index, ts->video_stream_type ) > 0;
				else
					video_ready = true;

				if ( ts->channel_type & AUDIO_PRESENT_MASK )
					audio_ready = GetAudioNewStreamId( ts->ps_builder, ts->audio_stream_index, ts->audio_stream_type ) > 0;
				else
					audio_ready = true;

				//have to wait utill ps_builder av ready 
				if (  audio_ready && video_ready )
				{				
					//Notify caller, stream type information
					NotifySteamInfo( ts );
					//flog(( "native.log", "AVINF was posted.\n" ));
					ts->AVinfo_notify_posted = done;

					//dump pid table for hardware demulx
					if ( ts->pfnPidTblDump != NULL  )
					{
						int stream_num = GetStreamNum( ts->parser, ts->selected_channel );
						if ( stream_num > MAX_STREAM_NUM ) stream_num = MAX_STREAM_NUM;
						if ( stream_num > 0 ) 
						{
							AVSTREAM_INFO* StreamInfo = (AVSTREAM_INFO*)sagetv_malloc( sizeof(AVSTREAM_INFO)*stream_num );
							if ( StreamInfo != NULL ) 
								stream_num = GetStreamList( parser, channel, StreamInfo, stream_num );				
							else
								stream_num = 0;
							DumpChannelPid( ts, StreamInfo, stream_num );
							sagetv_free( StreamInfo );
						}
					}
				}
			}
		}
		
    }
	return 0;
}

long PSAVSignalHook( void* handle, short channel, void* data )
{
	int i;
	TS_PARSER * parser;
	SIGNAL_INFO* signal_info;
	TSSPLT* ts;

	signal_info = (SIGNAL_INFO*)data;

	if ( signal_info == NULL || handle == NULL )
		return -1;

	parser = (TS_PARSER *)handle;

	ts = (TSSPLT*)parser->env;

	if ( ts == NULL )
		return -1;

	if ( signal_info->type == STREAM_START )
	{
		bool program_updated = false;
		unsigned short tsid = GetProgramTSID( parser );
		for ( i = 0; i<ts->tsids_num; i++ )
			if ( ts->tsids[i] == tsid )
				break;
		if ( ts->tsids[i] != tsid || ts->tsids_num == 0 )
		{
			if ( i<MAX_SERVICE_NUM )
			{
				ts->tsids[ts->tsids_num++] = tsid;
				program_updated = true;
			}
		}

		if ( program_updated )
		{
			int total_channel;
			
			PROGRAM_INFO *Channels;
			total_channel = GetProgramNumber( parser );
			
			Channels = (PROGRAM_INFO *)sagetv_malloc( sizeof(PROGRAM_INFO)*total_channel );
			total_channel = GetProgramList( parser, Channels, total_channel );
			{
				printf( "TS Program (total channel:%d, tsid:%d )\n", total_channel, tsid );
				flog(( "native.log",  "  TS Program (total channel:%d, tsid:%d )\n", total_channel, tsid ));
				for ( i = 0; i<total_channel; i++ )
				{
					printf( "Channel %02d  PMT pid:0x%04x program:0x%04x service:%d \n", 
						Channels[i].channelID+1, Channels[i].pid, (unsigned short)Channels[i].programID, Channels[i].type );

					flog(( "native.log",  "  Channel %02d  PMT pid:0x%04x program:0x%04x (%d) service:%d  \n", 
						Channels[i].channelID+1, Channels[i].pid, 
						(unsigned short)Channels[i].programID, (unsigned short)Channels[i].programID,
						Channels[i].type ));
						
				}
			}
			
			printf( "\n" );
			if ( ts->tsid == 0 && ts->tsids_num > 1 )
			{
				printf( "There are multiple service in this stream. tsid:%d. \n", tsid );
				flog((  "native.log",  "There are multiple services in this stream. tsid:%d \n", tsid ));
				if ( ts->builder_state == 0 )//&& ts->stream_network_type != ATSC_STREAM_TYPE )
				{
					SelectTSID( parser, tsid );
					ts->cur_tsid = tsid;
					printf( "pick up a service tsid:%d. \n", tsid );
					flog((  "native.log",  "pick up a service . tsid:%d \n", tsid ));
				}

			}

			sagetv_free( Channels ); 
			//debug use only, remove it for relaese
		}

	}

	if ( signal_info->type == STREAM_READY && -1 != channel )
	{	
		int ret = 0;
		int stream_num;
		bool encrypted;
		AVSTREAM_INFO* StreamInfo;
		stream_num = GetStreamNum( ts->parser, channel );
		if ( stream_num <= 0 ) return 0;
		if ( stream_num > MAX_STREAM_NUM ) stream_num = MAX_STREAM_NUM;
		StreamInfo = (AVSTREAM_INFO*)sagetv_malloc( sizeof(AVSTREAM_INFO)*stream_num );
		if ( StreamInfo == NULL ) return 0;

		stream_num = GetStreamList( parser, channel, StreamInfo, stream_num );
		encrypted = IsScrambleTSdProgram( ts, (unsigned short)StreamInfo[0].programID );

		//channel pick up strategy: program is higher prior, if no program avaliable/channel not found by program, use channel.
		//if both program, channel are invaild, go to try by name
		if( !ts->channel_picked )
		{
			int channel;

			if ( ts->lock_ATSC_channel && ts->ATSC_major != 0 )
			{
				int program = GetSIProgramByMajorMinor( ts->si_parser,  ts->ATSC_major,  ts->ATSC_minor );
				if ( program > 0 )
				{
					 char temp[32];
					 ts->selected_program = program;
					 GetSIProgramName( ts->si_parser, program, ts->channel_name, sizeof(ts->channel_name) );
					 TranslateJWideString2( temp, sizeof(temp), (unsigned short*)ts->channel_name );
					 flog(( "native.log", "locked ATSC channel by major:%d, minor:%d, program:%d name:%s\n",
						     ts->ATSC_major, ts->ATSC_minor, program, temp ));
				}
				else
				if ( program == 0 )
				{
					unsigned long packets;
					if ( ( packets = PacketInputNum( ts ) ) <  ATSC_CHANNL_LOCKING_TIMEOUT )
					{
						ret = 3;
						flog(( "native.log", "wait for more packets to get major minor number (2)!\n"  ));
					}
					else
					{
						ts->lock_ATSC_channel = 0;
						flog(( "native.log", "Timeout locking ATSC channel by major:%d, minor:%d (timeout:%ld)\n", 
						                                      ts->ATSC_major, ts->ATSC_minor, packets ));
					}

				} else
				if ( program < 0 )
				{
					 ts->lock_ATSC_channel = 0;
					 flog(( "native.log", "Abort locking ATSC channel by major:%d, minor:%d number\n", 
						                                      ts->ATSC_major, ts->ATSC_minor ));
				}
			}

			if (  ret != 3 && ts->selected_program != 0 )
			{
				channel = GetTSChannelNum( parser, ts->selected_program );
				
				if ( channel < 0 )
				{
					if ( ts->selected_channel == 0xffff  )
					{
						int new_program = -1;
						if ( ts->channel_name[0] )
							new_program = GetSIProgramByName( ts->si_parser, ts->channel_name );

						if ( new_program == 0 )
						{
							ret = 3;
							flog(( "native.log", "wait for more packets to lock channel by sid (1)!\n"  ));
						} else
						if ( new_program > 0 )
						{
							unsigned short tsid = GetSIProgramTSID( ts->si_parser, new_program );
							int ret = GetTSChannelNum( parser, new_program );
							if ( ret >= 0 )
							{
								ts->selected_channel = (unsigned short)ret;
								ts->selected_program = new_program;
								flog(( "native.log", "Channel %d is found by channel name %s, program %d (tsid:%d)!\n", 
									ts->selected_channel+1, ts->channel_name, ts->selected_program, tsid  ));
								ts->channel_picked = true;
								SelectChannel( ts->parser, ts->selected_channel );
							} else
								flog(( "native.log", "Channel is not found for program %d!\n",ts->selected_program  ));
						} else
						{
							flog(( "native.log", "Channel is not found for program %d abort.\n", ts->selected_program  ));
							ts->channel_picked = true;
						}

					} else
					{
						flog(( "native.log", "Channel is not found for program:%d, use channel:%d.\n",
							ts->selected_program, ts->selected_channel+1 ));
						ts->channel_picked = true;
						SelectChannel( ts->parser, ts->selected_channel );
					}
				} else
				{

					if ( ts->selected_channel == 0xffff )
					{
						unsigned short tsid = GetTSTSID( ts->parser );
						flog(( "native.log", "Channel %d is found for program %d (tsid:%d).\n", 
								channel+1, ts->selected_program, tsid  ));
						ts->channel_picked = true;
						ts->selected_channel = channel;
						SelectChannel( ts->parser, channel );
						if ( ts->tsid == 0  )
							SelectTSTSID( ts, tsid );
					}
					else
					{
						if ( ts->selected_channel == channel )
						{
							unsigned short tsid = GetTSTSID( ts->parser );
							flog(( "native.log", "Channel %d is found for program %d (tsid:%d) OK.\n", 
									channel+1, ts->selected_program, tsid  ));
							ts->channel_picked = true;
							ts->selected_channel = channel;
							if ( ts->tsid == 0  )
								SelectTSTSID( ts, tsid );

						}
						else
						{
							int new_program = -1;
							flog(( "native.log", "Channel %d is found for program:%d, but inconsistance with specified  channel:%d!!!\n",
								                      channel+1, ts->selected_program, ts->selected_channel+1 ));

							new_program = GetSIProgramByName( ts->si_parser, ts->channel_name );

							if ( new_program > 0 )
							{
								unsigned short tsid = GetSIProgramTSID( ts->si_parser, new_program );
								int ret  = GetTSChannelNum( parser, new_program );
								if ( ret >= 0 )
								{
									ts->selected_channel = (unsigned short)ret;
									ts->selected_program = new_program;
									flog(( "native.log", "Channel %d is found by channel name %s, program %d (tsid:%d) OK.\n", 
										ts->selected_channel+1, ts->channel_name, ts->selected_program, tsid  ));
									ts->channel_picked = true;
									SelectChannel( ts->parser, ts->selected_channel );

								} else
								{
									ts->selected_channel = channel;
									ts->channel_picked = true;
									SelectChannel( ts->parser, channel );
									flog(( "native.log", "Set Channel %d!\n", ts->selected_channel+1 ));
								}

								if (  ts->stream_network_type != ATSC_STREAM_TYPE )
										SelectTSTSID( ts, tsid );

							} else
							if ( new_program == 0 )
							{
								ts->selected_channel = channel;
								ts->channel_picked = true;
								SelectChannel( ts->parser, channel );
								flog(( "native.log", "Set Channel %d!!\n", ts->selected_channel+1 ));
							}
							else
							{
								unsigned long packets;
								if ( ( packets = PacketInputNum( ts ) )<  ATSC_CHANNL_LOCKING_TIMEOUT )
								{
									ret = 3;
									flog(( "native.log", "wait for more packets to get channel name (%d)...!\n", packets  ));
								} else
								{
									ts->selected_channel = channel;
									ts->channel_picked = true;
									SelectChannel( ts->parser, channel );
									flog(( "native.log", "Force set Channel %d!!\n", ts->selected_channel+1 ));
								}
							}
						}
					}
				}
			}
			else
			if (  ret != 3 && ts->selected_program == 0 )
			{   //use select channel anyway
				ts->channel_picked = true;
				SelectChannel( ts->parser, ts->selected_channel );
			}

		}

		if ( channel >=0 && channel < MAX_STREAM_NUM && ts->channel_flag[channel] == 0 )
		{
			printf( "\tChannel:%d  ( PMT pid:0x%04x, Program:0x%04x ) (%d).\n", channel+1, 
					StreamInfo[0].pid, StreamInfo[0].programID, encrypted  );
			flog(( "native.log", "\tChannel:%d streams: ( PMT pid:0x%04x, Program:0x%04x ) (%d).\n", channel+1, 
					StreamInfo[0].pid, (unsigned short)StreamInfo[0].programID, encrypted  ) );
			       
			for ( i = 1; i< stream_num; i++ )
			{
				printf( "\t\tType:0x%02x Pid:0x%03x\n", StreamInfo[i].streamType, StreamInfo[i].pid );
				flog(( "native.log", "\t\tType:0x%02x Pid:0x%03x\n", StreamInfo[i].streamType, StreamInfo[i].pid ));
			}
			ts->channel_flag[channel]=1;
		}


		if ( ts->channel_picked && ts->builder_state == 0 )
		{
			int flag;

			//if ( ts->selected_channel == channel  )
			//{
			//	unsigned short PCR_pid = GetProgramPcrPID( parser );
			//	if ( IsValidPcrPID( parser,  PCR_pid ) )
			//	{
			//		ts->startPCR = 0; //reset PCR;
			//		flog(( "native.log", "\t\tPCR PID: 0x%x\n", PCR_pid ));
			//	} else
			//		flog(( "native.log", "\t\tPCR PID is not ready\n" ));
			//}

			//re-get stream info from parser
			if ( ts->selected_channel != channel  )
			{
				sagetv_free( StreamInfo );
				StreamInfo = NULL;

				channel = ts->selected_channel;
				stream_num = GetStreamNum( ts->parser, channel );
				if ( stream_num > MAX_STREAM_NUM ) stream_num = MAX_STREAM_NUM;
				if ( stream_num > 0 ) 
				{
					StreamInfo = (AVSTREAM_INFO*)sagetv_malloc( sizeof(AVSTREAM_INFO)*stream_num );
					if ( StreamInfo != NULL ) 
						stream_num = GetStreamList( parser, channel, StreamInfo, stream_num );	
					else
						stream_num = 0;
				}
			}

			if ( StreamInfo != NULL )
			{
				ts->video_stream_num = 0;
				ts->audio_stream_num = 0;
				for ( i = 0; i<stream_num; i++ )
				{
					if ( IsVideoType( StreamInfo[i].streamType ) )
					{
						ts->video_stream_num++;
					}

					if ( IsAudioType( StreamInfo[i].streamType ) )
					{
						//unsigned long language; unsigned char type;
						ts->audio_stream_num++;
						//exclusive teletex
						if ( StreamInfo[i].streamType == 6 && 
							 ( IsTeletextType( StreamInfo[i].Desc )|| IsSubtitleType( StreamInfo[i].Desc ) ) )
						{
							if ( ts->audio_stream_num ) ts->audio_stream_num--;
						}
					}
				}

				if ( ts->video_stream_num ) ts->channel_type |= VIDEO_PRESENT_MASK;
				if ( ts->audio_stream_num ) ts->channel_type |= AUDIO_PRESENT_MASK;

				//pick up a main audio
				ts->AVinfo_notify_posted = false;
				if ( ( ts->channel_type & AUDIO_PRESENT_MASK ) ) 
				{
					flag = PickupMainAudioTrack( ts, StreamInfo, stream_num );
					if ( flag == 1 )
					{
						ts->builder_state = 1;
					} else
					{
						ts->builder_state = 0;
						ret = 4;
					}
				} else
				{
					//no auido only stream
					ts->builder_state = 1;
					ts->audio_state = 1;
					flog(( "native.log", "No audio present in stream of channel:%d\n", channel+1  ));
				}

				if ( !( ts->channel_type & VIDEO_PRESENT_MASK ) ) 
				{
					ts->video_state = 1;
					flog(( "native.log", "No video present in stream of channel:%d\n", channel+1  ));
				}
			}
		}

		if ( StreamInfo )	sagetv_free( StreamInfo );

		return ret;
	}

	if ( signal_info->type == PCR_READY )
	{
		flog(( "native.log", "\t\tPCR PID: 0x%x READY (reset StartPCR).\n", signal_info->pid ));
		ResetPTSFIX( ts );


	}
	return 0;
}

static bool AVInfoReady( TSSPLT* ts, bool *done )
{
	int i, audio_ready_stream_num = 0, video_ready_stream_num = 0;
	int avinf_posted_num = 0;
	unsigned long video_counter=0, audio_counter = 0;
	bool need_updated, auido_ready, video_ready, main_audio_found;

////ZQ Trace!
//int zq = 0;
//for ( i = 0; i<MAX_STREAM_NUM; i++ )
//{
//if ( IsVideoType( ts->stream_type[i] ) )
//{
//  zq = 1;
//  break;
//}
//}

	*done = false;
	// NARFLEX - 7/25/09 - While Qian is on vacation; I had to fix an issue here for ATSC but it
	// broke something for DVB; since I don't really understand the basis for a lot of this I'll just
	// make it conditional for now until he gets back (it's probably fixed in his new code anyways)

	main_audio_found = ts->output_select ? false : true;
	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{
		if ( IsVideoType( ts->stream_type[i] ) )
		{
			if (ts->parser->StreamType == ATSC_STREAM_TYPE) // NARFLEX - temp fix
			{
				if ( ts->output_select && GetVideoNewStreamId( ts->ps_builder, i, ts->stream_ts_type[i]) <= 0) //PS stream
				{
					continue;
				}
			}
			video_ready_stream_num++;
			video_counter =  ts->stream_counter[i] > video_counter ? ts->stream_counter[i] : video_counter;
		} else
		if ( IsAudioType( ts->stream_type[i] ) )
		{
			if (ts->parser->StreamType == ATSC_STREAM_TYPE) // NARFLEX - temp fix
			{
				int newAudStream = 0;
				if ( ts->output_select && (newAudStream = GetAudioNewStreamId( ts->ps_builder, i, ts->stream_ts_type[i])) <= 0) //PS stream
				{
					continue;
				}
				if (ts->output_select && newAudStream == ts->audio_stream_id)
					main_audio_found = true;
			}
			audio_ready_stream_num++;
			audio_counter = ts->stream_counter[i] > audio_counter ? ts->stream_counter[i] : audio_counter;
		}

		if ( ts->stream_posted[i] )
			avinf_posted_num++;
	}


	if (ts->parser->StreamType != ATSC_STREAM_TYPE) // NARFLEX - temp fix
	{
		if ( ts->output_select ) //PS stream 
		{
			for ( i = 0; i<MAX_STREAM_NUM; i++ )
			{
				if ( IsVideoType( ts->stream_type[i] ) )
				{
					//if ( LookupVideoTrack( ts->ps_builder, i, ts->stream_ts_type[i]) < 0 )
					if ( GetVideoNewStreamId( ts->ps_builder, i, ts->stream_ts_type[i]) <= 0 )
					{
	////ZQ Trace!
	//flog(( "native.log", "builder: new video stream id not ready (%d)\n", ts->stream_type[i] ));
						return false;
					}
				}
				else
				if ( IsAudioType( ts->stream_type[i] ) )
				{
					//if ( LookupAudioTrack( ts->ps_builder, i, ts->stream_ts_type[i]) < 0 )
					if ( GetAudioNewStreamId( ts->ps_builder, i, ts->stream_ts_type[i]) <= 0 )
					{
	////ZQ Trace!
	//if ( zq ) flog(( "native.log", "builder: new audio stream id not ready (%d)\n", ts->stream_type[i] ));
						return false;
					}
				}
			}
		}
	}

	if (ts->parser->StreamType == ATSC_STREAM_TYPE) // NARFLEX - temp fix
	{
		if (ts->audio_stream_num > 0 && !main_audio_found)
			return false;
	}

	if ( ts->channel_type & AUDIO_PRESENT_MASK ) 
		auido_ready = ts->audio_stream_num <= audio_ready_stream_num ;
	else
		auido_ready = true;

	if (  ts->channel_type & VIDEO_PRESENT_MASK )
		video_ready = ts->video_stream_num <= video_ready_stream_num ;
	else
		video_ready = true;

	*done = auido_ready && video_ready;
    
	if ( *done )
		return true;

	need_updated = avinf_posted_num < ( audio_ready_stream_num + video_ready_stream_num );

	if ( audio_ready_stream_num && video_ready_stream_num && avinf_posted_num == 0 ) 
		return true;

	if (  need_updated && audio_ready_stream_num && video_ready_stream_num )
		return true;

	if ( video_counter > 10000*15 || audio_counter > 10000 )
	{
		*done = true;
		return true;
	}

	if ( video_ready_stream_num && ts->ts_packet_num > (1024*1024*60/188) )
	{
		*done = true;
		return true;
	}

////ZQ Trace!
//if ( zq )
//{
//	flog(( "native.log", "builder: false post:%d, a:%d v:%d %d\n", avinf_posted_num, audio_ready_stream_num, video_ready_stream_num, video_counter   ));
//}

	return false;
}

#define MAX_STREAM_INFO_LENGTH  (512+128)
char* GetStreamInfo( TSSPLT* ts, char* buf, int buf_len );
static void NotifySteamInfo( TSSPLT* ts )
{
	char buf[MAX_STREAM_INFO_LENGTH];
	if ( ts == NULL  )
		return;
	memset( buf, 0, sizeof(buf) );
	
	GetStreamInfo( ts, buf, sizeof(buf) );

	if ( ts->pfnAVInfDump != NULL &&  ts->avinfo_ctrl != 0 )
	{
		ts->pfnAVInfDump( ts->AVInfContext, (short)strlen(buf)+1, buf );
	}
	flog(( "native.log", "AVINF was posted to 0x%x (ctrl:%d): %s\n", ts->pfnAVInfDump, ts->avinfo_ctrl, buf ));
}

char* GetStreamInfo( TSSPLT* ts, char* buf, int buf_len )
{
	char tmp[MAX_STREAM_INFO_LENGTH];
	int  trk[MAX_STREAM_NUM]={0};
	char stream_tag[MAX_STREAM_NUM][20]={0};
	int  video_type = 0;
	unsigned br= 0;
	AV_CONTEXT *av;
	int i;

	buf[0] = tmp[0] = 0;
	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{
		if ( IsVideoType( ts->stream_type[i] ) )
		{
			video_type = ts->stream_type[i];
			break;
		}
	}

	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{
		av = &ts->av[i];
		switch ( ts->stream_type[i] ) {
		case 1:	br += av->Mpeg2Hdr.SeqHdr.BitRate;   break;
		case 2: br += av->Mpeg2Hdr.SeqHdr.BitRate;   break;
		case 0xea: br += av->Mpeg2Hdr.SeqHdr.BitRate;   break;
		case 3:
		case 4: br += av->MpegWav.dwHeadBitrate;	 break;
		case 0x1b: br += av->H264Param.cbr/8;		 break;
		case 0xf1: br += av->MsftMedia.Video.dwBitRate; break;
		case 0x81: br += av->AC3Wav.wfx.nAvgBytesPerSec*8; break;
		}

		//creat stream index
		if ( ts->output_select ) //PS stream
		{
			unsigned short stream_id;
			AV_CONTEXT* av;
			trk[i] = -1;
			stream_tag[i][0]=0x0;
			if ( IsVideoType( ts->stream_type[i] ) )
			{
				trk[i] = LookupVideoTrack( ts->ps_builder, i, ts->stream_ts_type[i]);
				//av = GetVideoAVInfo( ts->ps_builder, i, ts->stream_ts_type[i] );
				stream_id = GetVideoNewStreamId( ts->ps_builder, i, ts->stream_ts_type[i] );
				if ( stream_id )
					sprintf( stream_tag[i], "tag=%02x", stream_id );
				else	
					sprintf( stream_tag[i], "err=av-not-ready" );
			}
			else
			if ( IsAudioType( ts->stream_type[i] ) )
			{			
				trk[i] = LookupAudioTrack( ts->ps_builder, i, ts->stream_ts_type[i] );
				av = GetAudioAVInfo( ts->ps_builder, i, ts->stream_ts_type[i] );
				if ( av )
				{
					stream_id = GetAudioNewStreamId( ts->ps_builder, i, ts->stream_ts_type[i] );
					if ( stream_id )
					{
						if ( IsPrivateStreamId( stream_id )  )
							sprintf( stream_tag[i], "tag=%02x-%02x%02x%02x%02x", stream_id, 
									av->PrivateHeader[0], av->PrivateHeader[1], 
									av->PrivateHeader[2], av->PrivateHeader[3]  );
						else
							sprintf( stream_tag[i], "tag=%02x", stream_id );
					}
				} else
				{
					sprintf( stream_tag[i], "err=av-no-ready:%02x", ts->stream_type[i] );
				}
			} else
			{
				sprintf( stream_tag[i], "err=unknown type:%02x", ts->stream_type[i] );
			}
		}
		else //TS stream
		{
			trk[i] = -1;
			stream_tag[i][0]=0x0;
			if ( IsVideoType( ts->stream_type[i] ) )
			{
				trk[i] = i;
				//sprintf( stream_tag[i], "tag=%02x;pid=%d", ts->av[i].VideoPES.StreamId, ts->stream_pid[i] );
				sprintf( stream_tag[i], "tag=%04x;pid=%d", ts->stream_pid[i], ts->stream_pid[i] );
			}
			else
			if ( IsAudioType( ts->stream_type[i] ) )
			{
				trk[i] = i;
				//sprintf( stream_tag[i], "tag=%02x;pid=%d", ts->av[i].AudioPES.StreamId, ts->stream_pid[i] );
				sprintf( stream_tag[i], "tag=%04x;pid=%d", ts->stream_pid[i], ts->stream_pid[i] );
			} else
			{
				stream_tag[i][0] = 0x0;
			}
		}
	}

	switch ( video_type ) {
	case 1: 
		sprintf( buf, "AV-INF|f=MPEG1-%s;br=%d;", ts->output_select == 0 ? "TS":"PS", br );
		break;
	case 2:
		sprintf( buf, "AV-INF|f=MPEG2-%s;br=%d;", ts->output_select == 0 ? "TS":"PS", br );
		break;
	case 0x10:
	case 0xea:
	case 0x1b: //h264 mepg
		sprintf( buf, "AV-INF|f=MPEG2-%s;br=%d;", ts->output_select == 0 ? "TS":"PS", br );
		break;
	case 0xf1:
		sprintf( buf, "AV-INF|f=DIXV-%s;br=%d;", ts->output_select == 0 ? "TS":"PS", br );
		break;
	default:
		sprintf( buf, "AV-INF|f=UNKOWN-%s(%d);br=%d;", ts->output_select == 0 ? "TS":"PS", br, video_type );
		break;
	}

	for ( i = 0; i<MAX_STREAM_NUM; i++ ) 
	{
		if ( ts->stream_type[i] == 0 )
			continue;
		ts->stream_posted[i] = 1;
		av = &ts->av[i];
		switch ( ts->stream_type[i] ) 
		{
		case 1: 
			sprintf( tmp, "[bf=vid;f=MPEG1-Video;fps=%f;fpsn=%d;fpsd=%d;ar=%f;w=%ld;h=%ld;index=%d;%s;%s];", 
						  (float)av->Mpeg2Hdr.SeqHdr.FrameRateNomi/av->Mpeg2Hdr.SeqHdr.FrameRateDeno,
						  av->Mpeg2Hdr.SeqHdr.FrameRateNomi, 
						  av->Mpeg2Hdr.SeqHdr.FrameRateDeno, 
						  Mepg1AspectRatio( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0x0f) ),
						  av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height,
						  trk[i], stream_tag[i], (ts->video_stream_index == i) ? "main=yes":"main=no" );

			if ( (int)strlen( buf) + (int)strlen( tmp )-1 < buf_len )
				strcat( buf, tmp );
			break;
		case 2:
			sprintf( tmp, "[bf=vid;f=MPEG2-Video;fps=%f;fpsn=%d;fpsd=%d;ar=%f;arn=%d;ard=%d;w=%ld;h=%ld;lace=%d;index=%d;%s;%s];", 
						  (float)av->Mpeg2Hdr.SeqHdr.FrameRateNomi/av->Mpeg2Hdr.SeqHdr.FrameRateDeno,
						  av->Mpeg2Hdr.SeqHdr.FrameRateNomi, 
						  av->Mpeg2Hdr.SeqHdr.FrameRateDeno, 
						  Mepg2AspectRatio( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0x0f), 
						                     av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height ),
						  Mepg2AspectRatioNomi( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0xf), 
						                     av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height ),
						  Mepg2AspectRatioDeno( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0xf),
						                     av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height ),
						  av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height,
						  !av->Mpeg2Hdr.SeqHdr.progressive,
						  trk[i], stream_tag[i],(ts->video_stream_index == i) ? "main=yes":"main=no" );
			if ( (int)strlen( buf) + (int)strlen( tmp )-1 < buf_len )
				strcat( buf, tmp );
			break;
		case 0x1b:
			sprintf( tmp, "[bf=vid;f=H.264;w=%d;h=%d;lace=%d;%s];", 
				     av->H264Param.width, av->H264Param.height, av->H264Param.progressive, stream_tag[i] );
			if ( (int)strlen( buf) + (int)strlen( tmp ) < buf_len )
				strcat( buf, tmp );
			break;
		case 0x10:  //Divx-vdeio
			sprintf( tmp, "[bf=vid;f=MPEG4-Video;fps=%f;ar=%f;arn=%d;ard=%d;w=%ld;h=%ld;lace=%d;%s];", 
						  av->Mpeg2Hdr.SeqHdr.PictureRate,
						  Mepg2AspectRatio( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0x0f), (av->Mpeg2Hdr.SeqHdr.arInfo>>8)&0xff, (av->Mpeg2Hdr.SeqHdr.arInfo>>16)&0xff ),
						  Mepg2AspectRatioNomi( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0xf), (av->Mpeg2Hdr.SeqHdr.arInfo>>8)&0xff, (av->Mpeg2Hdr.SeqHdr.arInfo>>16)&0xff ) ,
						  Mepg2AspectRatioDeno( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0xf), (av->Mpeg2Hdr.SeqHdr.arInfo>>8)&0xff, (av->Mpeg2Hdr.SeqHdr.arInfo>>16)&0xff ) ,
						  av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height,
						  !av->Mpeg2Hdr.SeqHdr.progressive, stream_tag[i] );
			if ( (int)strlen( buf) + (int)strlen( tmp ) < buf_len )
				strcat( buf, tmp );
			break;
		case 0xea: //VC-1  
			sprintf( tmp, "[bf=vid;f=VC1;fps=%f;fpsn=%d;fpsd=%d;ar=%f;arn=%d;ard=%d;w=%ld;h=%ld;lace=%d;%s];", 
						  (float)av->Mpeg2Hdr.SeqHdr.FrameRateNomi/av->Mpeg2Hdr.SeqHdr.FrameRateDeno,
						  av->Mpeg2Hdr.SeqHdr.FrameRateNomi, 
						  av->Mpeg2Hdr.SeqHdr.FrameRateDeno, 
						  Mepg2AspectRatio( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0x0f), (av->Mpeg2Hdr.SeqHdr.arInfo>>8)&0xff, (av->Mpeg2Hdr.SeqHdr.arInfo>>16)&0xff ),
						  Mepg2AspectRatioNomi( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0xf), (av->Mpeg2Hdr.SeqHdr.arInfo>>8)&0xff, (av->Mpeg2Hdr.SeqHdr.arInfo>>16)&0xff ),
						  Mepg2AspectRatioDeno( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0xf), (av->Mpeg2Hdr.SeqHdr.arInfo>>8)&0xff, (av->Mpeg2Hdr.SeqHdr.arInfo>>16)&0xff ),
						  av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height,
						  !av->Mpeg2Hdr.SeqHdr.progressive, stream_tag[i] );
			if ( (int)strlen( buf) + (int)strlen( tmp ) < buf_len )
				strcat( buf, tmp );
			break;
		case 0xf1:  //Divx-video
			sprintf( tmp, "[bf=vid;f=MPEG4-Video;fps=%f;w=%ld;h=%ld;index=%d;%s;%s];", 
						  1000000.0/av->MsftMedia.Video.AvgTimePerFrame,
						  av->MsftMedia.Video.bmiHeader.biWidth,
						  av->MsftMedia.Video.bmiHeader.biHeight,
						  trk[i],stream_tag[i],(ts->video_stream_index == i) ? "main=yes":"main=no");
			if ( (int)strlen( buf) + (int)strlen( tmp )-1< buf_len )
				strcat( buf, tmp );
			break;
		case 4:
			sprintf( tmp, "[bf=aud;f=MP%d;sr=%ld;ch=%d;index=%d;%s;%s;%s];", av->MpegWav.fwHeadLayer,
						  av->MpegWav.wfx.nSamplesPerSec, av->MpegWav.wfx.nChannels,
						  trk[i], stream_tag[i], ts->stream_desc[i],
						  (ts->audio_stream_index == i) ? "main=yes":"main=no" );
			if ( (int)strlen( buf) + (int)strlen( tmp )-1 < buf_len )
				strcat( buf, tmp );
			break;
		case 0x0f:
			sprintf( tmp, "[bf=aud;f=AAC;sr=%d;br=%d;ch=%d;index=%d;at=%s%s;%s;%s;%s];", av->AACParam.sample_freq,
						  av->AACParam.bitrate, av->AACParam.channel_num,
						  trk[i], 
						  av->AACParam.format==1?"ADTS":"LATM", av->AACParam.version==0?"":(av->AACParam.version==2)?"-MPEG2":"-MPEG4",
						  stream_tag[i], ts->stream_desc[i],
						  (ts->audio_stream_index == i) ? "main=yes":"main=no" );
			if ( (int)strlen( buf) + (int)strlen( tmp )-1 < buf_len )
				strcat( buf, tmp );
			break;
		case 0x11:
			sprintf( tmp, "[bf=aud;f=AAC-HE;sr=%d;br=%d;ch=%d;index=%d;at=%s%s;%s;%s;%s];", av->AACParam.sample_freq,
						  av->AACParam.bitrate, av->AACParam.channel_num,
						  trk[i],
						  av->AACParam.format==1?"ADTS":"LATM", av->AACParam.version==0?"":(av->AACParam.version==2)?"-MPEG2":"-MPEG4",
						  stream_tag[i], ts->stream_desc[i],
						  (ts->audio_stream_index == i) ? "main=yes":"main=no" );
			if ( (int)strlen( buf) + (int)strlen( tmp )-1 < buf_len )
				strcat( buf, tmp );
			break;
		case 0x81:
		case 0xbd:
			sprintf( tmp, "[bf=aud;f=AC3;sr=%ld;br=%ld;ch=%d;index=%d;%s;%s;%s];", 
						  av->AC3Wav.wfx.nSamplesPerSec, 
						  av->AC3Wav.wfx.nAvgBytesPerSec*8, 
						  av->AC3Wav.wfx.nChannels,
						  trk[i],stream_tag[i],ts->stream_desc[i],
						  (ts->audio_stream_index == i) ? "main=yes":"main=no");
			if ( (int)strlen( buf) + (int)strlen( tmp )-1 < buf_len )
				strcat( buf, tmp );
			break;
		case 0x82:
		case 0xfd:
			sprintf( tmp, "[bf=aud;f=DTS;sr=%ld;ch=%d;br=%ld;index=%d;%s;%s;%s];", 
						  av->DTSWav.wfx.nSamplesPerSec, av->DTSWav.wfx.nChannels, av->DTSWav.wfx.nAvgBytesPerSec,
						  trk[i],stream_tag[i],ts->stream_desc[i],
						  (ts->audio_stream_index == i) ? "main=yes":"main=no");
			if ( (int)strlen( buf) + (int)strlen( tmp )-1 < buf_len )
				strcat( buf, tmp );
			break;

		default:
			break;
		}
	}
	return buf;
}

static void ResetPTSFIX( TSSPLT* ts )
{
	ts->last_pcr = 0; 
	ts->last_PTS = 0; 
	ts->cur_pcr = 0;
	ts->cur_PTS = 0;
	ts->pcr_rate = 0;
	ts->last_sync_pcr = 0;
	ts->last_pcr_packet_num = 0;
	ts->last_pts_packet_num = 0;
}
static LONGLONG GeneratePCR( TSSPLT* ts, TS_AV_INFO* av_info, PES_INFO* pPESInfo )
{
	//recreat SCR based on bits rate and audio PTS
	LONGLONG pcr = 0;
	if ( pPESInfo != NULL && ( pPESInfo->HasPts || pPESInfo->HasDts ) ) 
	{
		LONGLONG DTS=0, PTS=0 ;
		if ( IsAudioType( av_info->type ) ) //based audio PTS to recreate PCR
		{
			if (pPESInfo->HasDts )
			{
				LONGLONG DTS_inc;
				DTS_inc =  pPESInfo->llDts - ts->last_PTS; 
				//checking DTS jumpping
				if ( DTS_inc > 4*90000 || -DTS_inc > 4*90000  )
				{
					DTS_inc = ts->pcr_rate * (GetPacketCounter(ts->parser) - ts->last_pts_packet_num)/300;
					flog(( "native.log", "DTS discontinue on pid:0x%x DTS:%ldms (PCR missing case).\n", 
								av_info->pid, (long)((ts->cur_PTS + DTS_inc)/90) ) );
				}

				DTS = ts->cur_PTS + DTS_inc;
				if ( IS_MAIN_AV_STREAM )
				{
					ts->last_pts_packet_num = GetPacketCounter(ts->parser);
					ts->last_PTS = pPESInfo->llDts;
					ts->cur_PTS = DTS;
					pcr = DTS * 300;
				}
			}
			
			if (pPESInfo->HasPts )
			{
				LONGLONG PTS_inc;
				PTS_inc =  pPESInfo->llPts - ts->last_PTS; 
				//checking DTS jumpping
				if ( PTS_inc > 4*90000 || -PTS_inc > 4*90000  )
				{
					PTS_inc = ts->pcr_rate * (GetPacketCounter(ts->parser) - ts->last_pts_packet_num)/300;
					flog(( "native.log", "PTS discontinue on pid:0x%x PTS:%ldms (PCR missing case).\n", 
								av_info->pid, (long)((ts->cur_PTS + PTS_inc)/90) ) );
				}

				PTS = ts->cur_PTS + PTS_inc;
				if ( IS_MAIN_AV_STREAM )
				{
					ts->last_pts_packet_num = GetPacketCounter(ts->parser);
					ts->last_PTS = pPESInfo->llPts;
					ts->cur_PTS = PTS;
					pcr = PTS * 300;
				}
			}
		} else
		if ( IsVideoType( av_info->type ) )
		{
			if (pPESInfo->HasDts )
			{
				LONGLONG DTS_inc;
				DTS_inc =  pPESInfo->llDts - ts->last_PTS; 
				//checking DTS jumpping
				if ( DTS_inc > 4*90000 || -DTS_inc > 4*90000  )
				{
					DTS_inc = ts->pcr_rate * (GetPacketCounter(ts->parser) - ts->last_pts_packet_num)/300;
					flog(( "native.log", "video DTS discontinue on pid:0x%x DTS:%ldms (PCR missing case).\n", 
								av_info->pid, (long)((ts->cur_PTS + DTS_inc)/90) ) );
				}

				DTS = ts->cur_PTS + DTS_inc;
			}
		
			if (pPESInfo->HasPts )
			{
				LONGLONG PTS_inc;
				PTS_inc =  pPESInfo->llPts - ts->last_PTS; 
				//checking DTS jumpping
				if ( PTS_inc > 4*90000 || -PTS_inc > 4*90000  )
				{
					PTS_inc = ts->pcr_rate * (GetPacketCounter(ts->parser) - ts->last_pts_packet_num)/300;
					flog(( "native.log", "video PTS discontinue on pid:0x%x PTS:%ldms (PCR missing case).\n", 
								av_info->pid, (long)((ts->cur_PTS + PTS_inc)/90) ) );
				}

				PTS = ts->cur_PTS + PTS_inc;
			}
		}



		if ( av_info->data != NULL && av_info->bytes > 5 )
			UpdatePESPTS( (unsigned char*)av_info->data, av_info->bytes, pPESInfo, &PTS, &DTS );		
	}


	if ( pcr != 0 && IS_MAIN_AV_STREAM  )
	{
		ts->pcr_rate = ( pcr - ts->last_pcr )/(GetPacketCounter(ts->parser) - ts->last_pcr_packet_num);
		ts->last_pcr = pcr ;
		ts->inc_pcr = pcr;
		ts->cur_pcr = pcr;
		ts->last_pcr_packet_num = GetPacketCounter(ts->parser);
	} else
	{
		pcr = ts->last_pcr + ts->pcr_rate * (GetPacketCounter(ts->parser) - ts->last_pcr_packet_num);
		ts->cur_pcr = pcr;
	}

	return pcr;
}
static LONGLONG PTSFix( TS_PARSER * parser, TS_AV_INFO* av_info, PES_INFO* pPESInfo )
{
	TSSPLT* ts;
	ts = (TSSPLT*)parser->env;

	if (  av_info->PCR == 1 ) //no PCR present in stream
		return GeneratePCR( ts, av_info, pPESInfo );

	if ( ts->last_pcr == 0 && av_info->PCR  )
	{
		ts->last_pcr = av_info->PCR ; 
		//ts->last_PTS = -8000 + av_info->PCR/300;   //PTS locking PCR.  
		ts->last_PTS = av_info->PCR/300;   //PTS locking PCR. //ZQ.
		ts->cur_PTS = ts->last_PTS;
		ts->inc_pcr = 0;
	}

	if ( av_info->PCR != ts->last_pcr )
	{
		if ( ts->last_pcr != 0  ) 
		{
			//check PCR discontinuety
			LONGLONG delta = ( av_info->PCR - ts->last_pcr )/27000;
			if ( ( delta > 4000 || -delta > 5 ) ) //4 seconds shifiting
			{
				//drop jumping PCR
				unsigned long inc = EstimatePCRIncrease( parser, GetPacketCounter(ts->parser)-ts->last_pcr_packet_num );
				ts->cur_pcr = ts->inc_pcr + inc;
				flog(( "native.log", "PCR discontinue on pid:0x%x. (%06ldms:%06ldms)\n", av_info->pid, 
					                                                   (long)delta, (long)inc/27000 ) );
			} else
			{
				//if ( ts->cur_pcr > ts->inc_pcr + ( av_info->PCR - ts->last_pcr ) )
				//	flog(( "native.log", "Warning: pcr go ahead\n" ));

				ts->cur_pcr = ts->inc_pcr + ( av_info->PCR - ts->last_pcr );
			}

			//flog(( "native.log", " >PCR:%08ld\tPTS:%06ld\n", 
			//	              (long)(ts->cur_pcr/27000), 
			//				  (long)(ts->cur_PTS)/90  ));

			ts->last_pcr = av_info->PCR;
			ts->inc_pcr  = ts->cur_pcr;
			ts->last_pcr_packet_num = GetPacketCounter(ts->parser);
		}
	} else
	{
		if ( ts->last_pcr )
		{
			unsigned long inc = EstimatePCRIncrease(parser, GetPacketCounter(ts->parser)-ts->last_pcr_packet_num);
			ts->cur_pcr = ts->inc_pcr + inc;
			//{
			//	static int ct = 0;
			//	if ( ct++ % 20 == 0 )
			//		flog(( "native.log", "**PCR:%08ld inc:%08ld\n", (long)(ts->cur_pcr/27000), inc/27000 ));
			//}
		}
	}
	
	if ( pPESInfo != NULL && ( pPESInfo->HasPts ||  pPESInfo->HasDts ) )
	{
		LONGLONG DTS=0, PTS=0 ;
		if ( ts->last_pcr != 0 )  //PTS-Fix isn't start yet.
		{
			if ( pPESInfo->HasDts  )
			{
				LONGLONG DTS_inc;
				DTS_inc =  pPESInfo->llDts - ts->last_PTS; 
				//checking DTS jumpping
				if ( DTS_inc > 4*90000 || -DTS_inc > 4*90000  ) //4 seconds
				{
					DTS_inc = EstimatePCRIncrease(parser, GetPacketCounter(ts->parser)-ts->last_pts_packet_num)/300;
					flog(( "native.log", "DTS discontinue on pid:0x%x DTS:%ld ms.\n", 
								av_info->pid, (long)((ts->cur_PTS + DTS_inc)/90) ) );
				}

				DTS = ts->cur_PTS + DTS_inc;
				
				
				//checking syncing to PCR
				DTS_inc = DTS - ts->cur_pcr/300;
				if ( DTS_inc > 90000*4 || -DTS_inc > 90000*4  )  //4 seconds
				{
					flog(( "native.log", "DTS resync pcr on pid:0x%x (diff:%lds ms).\n", av_info->pid, (long)(DTS_inc/90) ) );
					//DTS = 5000 + ts->cur_pcr/300; 
					DTS =  ts->cur_pcr/300;   //ZQ.
					
				}
				

				if ( IS_MAIN_AV_STREAM )
				{
					ts->cur_PTS = DTS;
					ts->last_PTS = pPESInfo->llDts;
					ts->last_pts_packet_num = GetPacketCounter(ts->parser);
					ts->last_sync_pcr = ts->cur_pcr;
				}
			}

			if ( pPESInfo->HasPts  )
			{
				LONGLONG PTS_inc;
				PTS_inc =  pPESInfo->llPts - ts->last_PTS; 
				//checking PTS jumpping
				if ( PTS_inc > 4*90000 || -PTS_inc > 4*90000  )
				{
					PTS_inc = EstimatePCRIncrease(parser, GetPacketCounter(ts->parser)-ts->last_pts_packet_num)/300;
					flog(( "native.log", "PTS discontinue on pid:0x%x PTS:%ldms.\n", 
						 av_info->pid, (long)((ts->cur_PTS + PTS_inc)/90 )) );
				}

				PTS = ts->cur_PTS + PTS_inc;
				
				
				PTS_inc = PTS - ts->cur_pcr/300;
				if ( PTS_inc > 90000*4 || -PTS_inc > 90000*4  ) //4 seconds
				{
					flog(( "native.log", "PTS resync pcr on pid:0x%x (diff:%lds ms). \n", av_info->pid, (long)(PTS_inc/90) ) );
					//PTS = 5000 + ts->cur_pcr/300; 
					PTS =  ts->cur_pcr/300; //ZQ.
				}

				if ( IS_MAIN_AV_STREAM )
				{
					ts->cur_PTS = PTS;
					ts->last_PTS = pPESInfo->llPts;
					ts->last_pts_packet_num = GetPacketCounter(ts->parser);
					ts->last_sync_pcr = ts->cur_pcr;
				}
			}

		}
		if ( av_info->data != NULL && av_info->bytes > 5 )
			UpdatePESPTS( (unsigned char*)av_info->data, av_info->bytes, pPESInfo, &PTS, &DTS );
	}

	return ts->cur_pcr;
}

///////////////////////////////////////////////////////////////////////////////////////////////////
void* OpenTSSplitter( int OutputSel )
{
	int i;
	TSSPLT* splt;
	_flog_check();
	flog(( "native.log", "TSSplitter ver 1.71\n" ));  
	splt = (TSSPLT*)sagetv_malloc( sizeof(TSSPLT) );
	
	splt->output_select = OutputSel;	
	splt->program_id = 21;
	splt->selected_channel = 0;	
	splt->selected_program = 0;
	splt->builder_state = 0;
	splt->channel_type = 0;
	splt->ts_packet_num = 0;
	memset( &splt->av, 0x0, sizeof( AV_CONTEXT ) );
	splt->ts_builder = NULL;
	splt->ps_builder = NULL;
	
	splt->parser = TSParserOpen( );
	splt->parser->env = splt;
	
	splt->ts_builder = TSBuilderOpen( );
	splt->ts_builder->env = splt;
	
	splt->ps_builder = PSBuilderOpen( );
	splt->ps_builder->env = splt;
	
	splt->si_parser = SIParerOpen( splt->parser, 0 );
	splt->parser->si_env = splt->si_parser;
	splt->parser->si_dumper = SI_dumper;
	
	SetStreamType( splt->parser, DVB_STREAM_TYPE ); 
	//SetDumpSectionCallback( parser, DBVSectionHook  ); //for testing
	
	if ( splt->output_select == 0 )
	{
		SetTSDumpAVCallback( splt->parser, (LPFNParserDump)TSAVStreamHook );
		SetTSDumpSignalCallback( splt->parser, (LPFNParserDump)TSAVSignalHook );

	} else
	{
		SetTSDumpAVCallback( splt->parser, (LPFNParserDump)PSAVStreamHook );
		SetTSDumpSignalCallback( splt->parser, (LPFNParserDump)PSAVSignalHook );
	}
	
	splt->video_state = 0;
	splt->audio_state = 0;
	memset( splt->stream_id, 0, sizeof(splt->stream_id) );
	//memset( splt->new_stream_id, 0, sizeof(splt->new_stream_id) );
	memset( splt->pts_inf, 0, sizeof(splt->pts_inf) );
	memset( splt->stream_type, 0, sizeof(splt->stream_type) );
	memset( splt->stream_ts_type, 0, sizeof(splt->stream_ts_type) );
	memset( splt->stream_desc, 0, sizeof(splt->stream_desc) );
	memset( splt->stream_counter, 0, sizeof(splt->stream_counter) );
	memset( splt->stream_posted, 0, sizeof(splt->stream_posted) );
	memset( splt->stream_pack_byte, 0, sizeof(splt->stream_pack_byte) );
	memset( splt->channel_flag, 0, sizeof(splt->channel_flag) );
	memset( splt->tsids, 0, sizeof(splt->tsids) );
	splt->tsids_num = 0;
	splt->cur_tsid = 0;
	splt->stream_network_type = 0;

	
	memset( splt->av, 0, sizeof(splt->av) );
	splt->last_pcr = 0;
	splt->cur_pcr = 0;
	splt->last_PTS = 0;
	splt->cur_PTS = 0;
	splt->last_sync_pcr = 0;
	splt->pcr_rate = 0;
	splt->last_pcr_packet_num = 0;

	splt->frame_counter = 0;
	splt->frame_pcr_inc = 0;
	splt->pcr_per_frame = 0; 
	splt->selected_video_pid = 0;
	
	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{
		splt->pts_inf[i].syncPTS = -1;
		splt->pts_inf[i].syncDTS = -1;
	}
	
	splt->AVinfo_notify_posted = false;
	splt->pfnEPGDump = NULL;
	splt->pfnAVInfDump = NULL;
	splt->pfnPidTblDump = NULL;
	splt->pfnPMTDataDump = NULL;
	splt->rebuild_pts = 0; 
	splt->EPG_parser_ctrl = 1;
	splt->avinfo_ctrl = 1;
	splt->disable_multi_audio_stream = false; 
	splt->exclusive_teletext = true;
	splt->enable_ptsfix = true;
	memset( splt->channel_name, 0, sizeof(splt->channel_name) );
	splt->channel_picked = false;

	if ( _PTSFIX_DISABLE() )
	{
		splt->enable_ptsfix = false;
		flog(( "native.log", "ptsfix is disabled by 'PTSFIX.DISABLE'\n" ));
	} else
	{
		flog(( "native.log", "ptsfix is enabled by default.\n" ));
	}

	if ( _ENABLE_TELETEXT() )
	{
		splt->exclusive_teletext = false;
		flog(( "native.log", "teletext is enabled by 'TELETEXT.ENABLE'\n" ));
	}

	splt->lock_ATSC_channel = false;
	splt->ATSC_major = splt->ATSC_minor = 0;

	splt->state = SPLT_RUNNING;
	splt->busy = 0;
	StartParser( splt->parser );
	splt->plungin_enabled = true;
		
	return (void*)splt;
}

void CloseTSSplitter( void* handle )
{
	TSSPLT* splt = (TSSPLT*)handle;
	TSParserClose( splt->parser );
	SIParserClose( splt->si_parser);
	TSBuilderClose( splt->ts_builder );
	PSBuilderClose( splt->ps_builder );
	sagetv_free( splt );
}

void ResetTSSplitter( TSSPLT *splt )
{
	int i;
	splt->selected_channel = 0xffff;
	splt->selected_program = 0;
	splt->builder_state = 0;
	splt->channel_type = 0;
	splt->video_state = 0;
	splt->audio_state = 0;
	splt->ts_packet_num = 0;
	splt->AVinfo_notify_posted = false;
	
	memset( splt->stream_id, 0, sizeof(splt->stream_id) );
	//memset( splt->new_stream_id, 0, sizeof(splt->new_stream_id) );
	memset( splt->pts_inf, 0, sizeof(splt->pts_inf) );
	memset( splt->stream_type, 0, sizeof(splt->stream_type) );
	memset( splt->stream_ts_type, 0, sizeof(splt->stream_ts_type) );
	memset( splt->stream_desc, 0, sizeof(splt->stream_desc) );
	memset( splt->stream_counter, 0, sizeof(splt->stream_counter) );
	memset( splt->stream_posted, 0, sizeof(splt->stream_posted) );
	memset( splt->stream_pack_byte, 0, sizeof(splt->stream_pack_byte) );
	memset( splt->channel_flag, 0, sizeof(splt->channel_flag) );
	memset( splt->channel_name, 0, sizeof(splt->channel_name) );
	memset( splt->tsids, 0, sizeof(splt->tsids) );
	memset( splt->av, 0, sizeof(splt->av) );
	splt->tsids_num = 0;
	splt->cur_tsid = 0;
	splt->stream_network_type = 0;

	memset( splt->av, 0, sizeof(splt->av) );

	splt->channel_picked = false;

	splt->lock_ATSC_channel = false;
	splt->ATSC_major = splt->ATSC_minor = 0;

	splt->last_pcr = 0;
	splt->cur_pcr = 0;
	splt->last_PTS = 0;
	splt->cur_PTS = 0;
	splt->last_sync_pcr = 0;
	splt->pcr_rate = 0;
	splt->last_pcr_packet_num = 0;

	splt->frame_counter = 0;
	splt->frame_pcr_inc = 0;
	splt->pcr_per_frame = 0; 
	splt->selected_video_pid = 0;
	
	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{
		splt->pts_inf[i].syncPTS = -1;
		splt->pts_inf[i].syncDTS = -1;
	}
	if ( splt->output_select == 0 )
	{
		TSResetBuilder( splt->ts_builder );
		StopParser( splt->parser );
		SetTSDumpAVCallback( splt->parser, (LPFNParserDump)TSAVStreamHook );
		SetTSDumpSignalCallback( splt->parser, (LPFNParserDump)TSAVSignalHook );
		StartParser( splt->parser );
	}
	else
	{
		PSResetBuilder( splt->ps_builder );
		StopParser( splt->parser );
		SetTSDumpAVCallback( splt->parser, (LPFNParserDump)PSAVStreamHook );
		SetTSDumpSignalCallback( splt->parser, (LPFNParserDump)PSAVSignalHook );
		StartParser( splt->parser );
	}
}

int GetBlockSize( TSSPLT* splt )
{
	if ( splt->output_select == 0 )
		return TS_PACKET_LENGTH;
	else
	{
		return PSGetBlockSize( splt->ps_builder );
	}
}

void  SelectOuputFormat( TSSPLT* splt, int OutputSel )
{
	splt->output_select = OutputSel;	
}

//obsolte
bool  SelectTSChannel( TSSPLT* splt, unsigned short channel, bool reset )
{
	int ret;
	channel -= 1;
	flog(( "native.log", "select channel:%d (reset:%d)\n", channel+1, reset )); 
	if ( reset ) 
	{
		if ( splt->state == SPLT_RUNNING ) { flog(( "native.log", "*WARNING* Parser is running, when reset\n"  )); };
		ResetParser( splt->parser );
		ResetTSSplitter( splt );
		splt->selected_channel = channel;	
		ret = SelectChannel( splt->parser, channel );
	} 
	else
	{
		ResetTSSplitter( splt );
		splt->selected_channel = channel;	
		ret = PickupChannel( splt->parser, channel );
	}
	
	return true;
}

//obsolte
bool  SelectTSProgram( TSSPLT* splt, unsigned short Prgoram, bool reset )
{
	//hr_time( &splt->hrtime_start );
	flog(( "native.log", "select program:%d (reset:%d)\n", Prgoram, reset )); 
	if ( reset ) 
	{
		if ( splt->state == SPLT_RUNNING ) { flog(( "native.log", "*WARNING* Parser is running, when reset\n"  )); };
		ResetParser( splt->parser );
		ResetTSSplitter( splt );
		splt->selected_channel = 0xffff;
		splt->selected_program = Prgoram;
		if ( ( splt->selected_channel = (unsigned short)SelectProgram( splt->parser, Prgoram ) ) != 0xffff )
		{
			return true;
		}
	} 
	else
	{
		ResetTSSplitter( splt );
		splt->selected_program = Prgoram;
		splt->selected_channel = (unsigned short)GetTSChannelNum( splt->parser, Prgoram );
		flog(( "native.log", "Selected channel (b):%d\n",  splt->selected_channel+1 ));			
		if ( ( splt->selected_channel = PickupProgram( splt->parser, Prgoram ) ) != 0 )
		{
			return true;
		}
	}
	return false;
}

bool PickupTSChannel( TSSPLT* splt, unsigned short Channel, unsigned short Program, char *ChannelName, bool reset )
{
	//hr_time( &splt->hrtime_start );
	flog(( "native.log", "pickup channel-> channel:%d program:%d name:%s(reset:%d)\n", 
		Channel, Program, ChannelName != NULL ? ChannelName:"", reset )); 
	
	if ( Channel != 0xffff )
		Channel -= 1;

	if ( reset ) 
	{
		if ( splt->state == SPLT_RUNNING ) { flog(( "native.log", "*WARNING* Parser is running, when reset\n"  )); };
		ResetParser( splt->parser );
		ResetTSSplitter( splt );
		splt->selected_program = Program;
		if ( ChannelName != NULL )
			strncpy( splt->channel_name, ChannelName, sizeof(splt->channel_name) );
		splt->selected_channel = Channel;
		if ( Channel != 0xffff )
			SelectChannel( splt->parser, Channel );

	} 
	else
	{
		int ready_channel;
		ResetTSSplitter( splt );
		splt->selected_program = Program;
		ready_channel = GetTSChannelNum( splt->parser, Program );
		if ( ChannelName != NULL )
			strncpy( splt->channel_name, ChannelName, sizeof(splt->channel_name) );

		if ( Channel == ready_channel )
		{
			if ( ( splt->selected_channel = (unsigned short)PickupProgram( splt->parser, Program ) ) != 0xffff )
			{
				return true;
			}
			splt->selected_channel = Channel;
		} else
		{
			//reset all
			if ( splt->state == SPLT_RUNNING ) { flog(( "native.log", "*WARNING* Parser is running, when reset\n"  )); };
			ResetParser( splt->parser );
			ResetTSSplitter( splt );
			splt->selected_program = Program;
			splt->selected_channel = Channel;
			if ( ChannelName != NULL )
				strncpy( splt->channel_name, ChannelName, sizeof(splt->channel_name) );
			if ( Channel != 0xffff )
				SelectChannel( splt->parser, Channel );
		}
	}
	return false;
}

bool PickupATSCChannel( TSSPLT* splt, unsigned short major, unsigned short minor, unsigned short Program, char *ChannelName, bool reset )
{
	int Channel;
	//hr_time( &splt->hrtime_start );
	flog(( "native.log", "pickup ATSC channel-> major:%d minor:%d program:%d name:%s(reset:%d)\n", 
			major, minor, Program, ChannelName != NULL ? ChannelName:"", reset )); 

	Channel = minor;

	if ( Channel != 0xffff && Channel != 0 )
		Channel -= 1;

	if ( reset ) 
	{
		if ( splt->state == SPLT_RUNNING ) { flog(( "native.log", "*WARNING* Parser is running, when reset\n"  )); };
		ResetParser( splt->parser );
		ResetTSSplitter( splt );
		if ( major != 0 && minor != 0 && major != 0xffff && minor != 0xffff)
			LockATSCChannel( splt, major, minor );
		splt->selected_program = Program;
		if ( ChannelName != NULL )
			strncpy( splt->channel_name, ChannelName, sizeof(splt->channel_name) );
		splt->selected_channel = Channel;
		if ( Channel != 0xffff )
			SelectChannel( splt->parser, Channel );

	} 
	else
	{
		int ready_channel;
		ResetTSSplitter( splt );
		if ( major != 0 && minor != 0 && major != 0xffff && minor != 0xffff)
			LockATSCChannel( splt, major, minor );
		splt->selected_program = Program;
		ready_channel = GetTSChannelNum( splt->parser, Program );
		if ( ChannelName != NULL )
			strncpy( splt->channel_name, ChannelName, sizeof(splt->channel_name) );

		if ( Channel == ready_channel )
		{
			if ( ( splt->selected_channel = (unsigned short)PickupProgram( splt->parser, Program ) ) != 0xffff )
			{
				return true;
			}
			splt->selected_channel = Channel;
		} else
		{
			//reset all
			if ( splt->state == SPLT_RUNNING ) { flog(( "native.log", "*WARNING* Parser is running, when reset\n"  )); };
			ResetParser( splt->parser );
			ResetTSSplitter( splt );
			splt->selected_program = Program;
			splt->selected_channel = Channel;
			if ( ChannelName != NULL )
				strncpy( splt->channel_name, ChannelName, sizeof(splt->channel_name) );
			if ( Channel != 0xffff )
				SelectChannel( splt->parser, Channel );
		}
	}
	return false;
}




bool  _ExtractStream( TSSPLT* splt, PID_TBL* Pids, int numPids )
{
	SelectTSChannel( splt, 0, true );
	return CreatePMT( splt->parser, Pids, numPids );
}

bool  PopupPacket( TSSPLT* splt, unsigned char* pData, unsigned int* Size )
{
	short type, playload;

	if ( splt->output_select == 0 )
	{
		if ( *Size >= TS_PACKET_LENGTH )
			if ( PopPacket( splt->ts_builder, (char*)pData, &type, &playload ) )
			{
				*Size = TS_PACKET_LENGTH;
				return true;
			}

	} else
	{
			//check data 
			unsigned long   size = (unsigned long)*Size;
			if ( PopPSPacket( splt->ps_builder, &type, (char*)pData , &size )  )
			{
				*Size = size;
				return true;
			}
	}

	return false;
}

bool  DrainPacket( TSSPLT* splt, unsigned char* pData, unsigned int* Size )
{
	bool ret = false;
	short type, playload;

	if ( splt->output_select == 0 )
	{
		if ( *Size >= TS_PACKET_LENGTH )
			ret = PopPacket( splt->ts_builder, (char*)pData, &type, &playload );

		if ( !ret )
			*Size = 0;
		else
			*Size = TS_PACKET_LENGTH;

	} else
	{
		//check data 
		unsigned long   size = *Size;
		ret = FlushOutPSPacket( splt->ps_builder, &type, (char*)pData , &size );
		if ( ret )
			*Size = size;
		else
			*Size = 0;
	}

	return ret;
}

bool  CheckProgramStart( TSSPLT* splt )
{
	return ( splt->builder_state > 0 );	
}
int   GetTotalProgramNum( TSSPLT* splt ) 
{ 
	int total_channel = GetProgramNumber( splt->parser );
	return total_channel; 
}

int   NumOfPacketsInPool( TSSPLT* splt )
{
	if ( splt->output_select == 0 )
	{
		return GetPacketNumInPool( splt->ts_builder );
	}
	else
	{
		return GetPSReadyPacketNumInPool( splt->ps_builder );
	}
}

int   GetTSProgramList( TSSPLT* splt, unsigned short* ProgramList, unsigned long MaxBytes )
{
	int total_channel, i;
	unsigned long size = 0;
	PROGRAM_INFO *Channels;
	total_channel = GetProgramNumber( splt->parser );
	Channels = (PROGRAM_INFO *)sagetv_malloc( sizeof(PROGRAM_INFO)*total_channel );
	total_channel = GetProgramList( splt->parser, Channels, total_channel );
	for ( i = 0; i<total_channel && ( size + sizeof(short) <= MaxBytes ); i++ )
	{
		ProgramList[i] = Channels[i].programID;
		size += sizeof(short);
	}
	sagetv_free( Channels );
	return total_channel;
}


bool  GetCountOfStreams( TSSPLT* splt, int Program, unsigned short* pVal )
{
	int	stream_num = GetStreamNum( splt->parser, Program );
	if ( stream_num < 0 || stream_num >= MAX_PROGRAM )
		return false;
	*pVal = stream_num;

	return true;
}

int   GetVideoProgramList( TSSPLT* splt, unsigned short* ProgramList, unsigned long MaxBytes )
{
	int total_channel, i, k = 0;
	unsigned long size = 0;
	PROGRAM_INFO *Channels;
	total_channel = GetProgramNumber( splt->parser );
	Channels = (PROGRAM_INFO *)sagetv_malloc( sizeof(PROGRAM_INFO)*total_channel );
	total_channel = GetProgramList( splt->parser, Channels, total_channel );
	for ( i = 0; i<total_channel; i++ )
	{
		if (  size + sizeof(short) <= MaxBytes && ( Channels[i].type & 0x01 ) ) 
		{
			ProgramList[k++] = Channels[i].programID;
			size += sizeof(short);
		}
	}
	if (  size + sizeof(short) <= MaxBytes ) 
		ProgramList[k] = 0;

	sagetv_free( Channels );
	return k;
}

int   GetChannelName( TSSPLT* splt, void* pChannelName , unsigned long MaxBytes )
{
	if ( pChannelName == NULL || MaxBytes <= 0 )
		return 0;

	return GetSIChannelName( splt->si_parser, (CHANNEL_NAME*)pChannelName, MaxBytes );
}

int  GetValidChannelNum( TSSPLT* splt )
{
	return GetSIChannelNum( splt->si_parser );
}

int  GetVideoChannelNum( TSSPLT* splt )
{
	return GetVideoProgramNumber( splt->parser );
}

bool  StreamType( TSSPLT* splt, int Program, unsigned short index, unsigned char* pVal )
{
	int	stream_num = GetStreamNum( splt->parser, Program );
	AVSTREAM_INFO* StreamInfo;
	if ( stream_num < 0 || stream_num >= MAX_PROGRAM )
		return false;

	if ( index > stream_num )
		return false;

	StreamInfo = (AVSTREAM_INFO*)sagetv_malloc( sizeof(AVSTREAM_INFO)*stream_num );
	stream_num = GetStreamList( splt->parser, Program, StreamInfo, stream_num );

	*pVal = (unsigned char)StreamInfo[index+1].streamType;
	sagetv_free( StreamInfo );

	return true;
}

bool  ParseData( TSSPLT* splt, const unsigned char* pData, long dwBytes )
{
	TSProcessBlock( splt->parser, dwBytes, (char*)pData );
	return true;
}

void SetupRebuildPTS( TSSPLT* splt, int contrl )
{
	splt->rebuild_pts = contrl; 
}

void ResetSIParser( TSSPLT* splt )
{
	SIParserReset( splt->si_parser );
}

void SetEPGDump( TSSPLT* splt, EPG_DUMP pfn, void* Context )
{
	splt->pfnEPGDump = (EPG_DUMP)pfn;
	splt->EPGContext = Context;
	if ( pfn != NULL && splt->EPG_parser_ctrl )
	{
		SetupEPGDumper( splt->si_parser, EPG_dump, splt );
	}
}


void SetAVInfDump( TSSPLT* splt, EPG_DUMP pfn, void* Context )
{
	splt->pfnAVInfDump = (EPG_DUMP)pfn;
	splt->AVInfContext = Context;
}

void SetPidTblDump( TSSPLT* splt, PID_DUMP pfn, void* Context )
{
	splt->pfnPidTblDump = (PID_DUMP)pfn;
	splt->PidContext = Context;
}

void SetPMTDataDump( TSSPLT* splt, DATA_DUMP pfn, void* Context )
{
	if ( splt->parser == NULL )
		return;

	splt->pfnPMTDataDump = (DATA_DUMP)pfn;
	splt->PMTDataContext = Context;
	SetTSDumpPMTCallback( splt->parser, PMT_dump, splt );
}



int SetSoftPidFilter( TSSPLT* splt, unsigned short pid )
{
	int ret = 0;
	if ( pid == 0x2000 )
		ResetTSSoftPidFilter( splt->parser );  //the same way Linux DVB reset demulex filter
	else
		ret = SetTSSoftPidFilter( splt->parser, pid );

	return ret;
}

void StopTSParser( TSSPLT* splt )
{
	if ( splt->parser == NULL )
		return;
	//flush data in buffer

	flog(( "native.log", "Parser stopped, processed pkt:%d \n", splt->parser->packets ));
	StopParser( splt->parser );
	splt->state = SPLT_STOP;
	
}

void StartTSParser( TSSPLT* splt )
{
	if ( splt->parser == NULL )
		return;
	StartParser( splt->parser );
	splt->state = SPLT_RUNNING;
	flog(( "native.log", "Parser started \n" ));
}

bool IsTSParserStopped( TSSPLT* splt )
{
	if ( splt->parser == NULL )
		return true;
	if ( splt->state != SPLT_STOP )
		return false;
	return (splt->busy == 0);
}

bool IsScrambledTSChannel( TSSPLT* splt, unsigned short Channel )
{
	if ( splt->parser == NULL )
		return false;
	return IsScrambledChannel( splt->parser, Channel );
}

bool IsScrambleTSdProgram( TSSPLT* splt, unsigned short Program )
{
	if ( splt->parser == NULL )
		return false;
	return IsScrambledProgram( splt->parser, Program );
}

bool RetreveSIProgramName( TSSPLT* splt, unsigned short program, char* name, unsigned short size )
{
	if  ( splt->parser == NULL && splt->si_parser == NULL )
		return false;
	return GetSIProgramName( splt->si_parser, program, name, (int)size );
}

long PMT_dump(void* handle, short bytes, void* data )
{
	TSSPLT* splt = (TSSPLT*)handle;
	if ( splt->pfnPMTDataDump != NULL )
	{
		return splt->pfnPMTDataDump( splt->PMTDataContext, bytes, data );
	}
	return 0;
}

long EPG_dump(void* handle, short bytes, void* message )
{
	TSSPLT* splt = (TSSPLT*)handle;
	if ( splt->pfnEPGDump != NULL && splt->EPG_parser_ctrl )
	{
		return splt->pfnEPGDump( splt->EPGContext, bytes, message );
	
	}

	return 0;
}

void  EPGParserCtrl( TSSPLT* splt, unsigned long contrl )
{
	splt->EPG_parser_ctrl = contrl;
}

void  AVInfoCtrl( TSSPLT* splt, unsigned long contrl )
{
	splt->avinfo_ctrl = contrl;
}

void DisableMultiAudio( TSSPLT* splt, int flag )
{
	splt->disable_multi_audio_stream = (bool)flag; 
}

LONGLONG GetLastPTS( TSSPLT* splt )
{
	if ( splt->video_stream_index > MAX_STREAM_NUM )
		return 0;
		
	return splt->pts_inf[splt->video_stream_index].lastPTS;
}

LONGLONG GetCurPTS( TSSPLT* splt )
{
	if ( splt->video_stream_index > MAX_STREAM_NUM )
		return 0;
		
	return splt->pts_inf[splt->video_stream_index].newPTS;
}

bool IsVideoStreamReady( TSSPLT* splt )
{
	int i;
	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{
		if ( splt->stream_type[i] == 2 || splt->stream_type[i] == 1 )
			return true; 
	}
	return false;
}

bool IsAudioStreamReady( TSSPLT* splt )
{
	int i;
	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{
		if ( IsAudioType( splt->stream_type[i] ) )
			return true;
	}
	return false;
}

unsigned long PacketInputNum( TSSPLT* splt )
{
	return TotalPidHits( splt->parser );
}

int GetProgramState( TSSPLT* splt, unsigned short Program )
{
	return  CheckProgramState( splt->parser, Program );
}

void SetTSStreamType( TSSPLT* splt, int type )
{
	flog(( "native.log", "Set NetworkType:%d \n", type ));
	if ( type < 0 || type >2  )
		flog(( "native.log", "invalid Network Type:%d skiped.\n", type ));

	SetStreamType( splt->parser, type );	
	splt->stream_network_type = type;
}

void SetSIStreamType( TSSPLT* splt, int type )
{
	SIParserSetStreamType( splt->si_parser, type );
}

int  GetSIStreamType( TSSPLT* splt )
{
    return SIParserGetStreamType( splt->si_parser );
}

//obsolute function, I didn't into TSSplitter.h Q>
int GetCurProgram( TSSPLT* splt )
{
	return splt->selected_channel;
}

int PushData( TSSPLT* splt, const unsigned char* data, int len, unsigned char* out_buf, int buf_size, 
			  OUTPUT_DUMP pfnOutput, void* context )
{
	const unsigned char* pStart;
	unsigned char* pOutbuf;
	unsigned long TotalBytes, Bytes;
	unsigned int Size;
	int 	nBufferIndex;
	int		nTSPacketNum;

	splt->busy = 1;
	TotalBytes = 0;
	nTSPacketNum = buf_size/TS_PACKET_LENGTH;
		
	pStart = (const unsigned char*)data;
	while ( 1 )
	{
		if ( splt->state == SPLT_STOP )	
		{
			splt->busy = 0;
			return TotalBytes;
		}

		if ( len > 0 && pStart != NULL )
		{
			Bytes = _MIN(  TS_PACKET_LENGTH*3, len );
			ParseData( splt, pStart, Bytes );
			pStart += Bytes;
			len -= Bytes;
		}

		if ( splt->output_select == 0 )  //TS output
		{
			if (  nTSPacketNum  > 0 )
			{
				while ( NumOfPacketsInPool( splt ) >= nTSPacketNum  )
				{ 
					nBufferIndex = 0;
					pOutbuf = (unsigned char*)out_buf;
					Size = TS_PACKET_LENGTH;
					while ( PopupPacket( splt, pOutbuf, &Size ) )
					{
						pOutbuf += TS_PACKET_LENGTH;
						nBufferIndex++;
						if ( nBufferIndex >= nTSPacketNum )
							break;
					}
					Bytes = nBufferIndex * TS_PACKET_LENGTH;
					if ( pfnOutput != NULL )
						pfnOutput( context, out_buf, Bytes );
					TotalBytes += Bytes;
				}
			}
		} else    //PS output
		if ( buf_size > 0 )
		{
				Size = buf_size;
				while ( PopupPacket( splt, out_buf, &Size ) )
				{
					if ( pfnOutput != NULL )
						pfnOutput( context, out_buf, Size );
					TotalBytes += Size;
				}
		}

		if ( len <= 0 ) break;
	}
	splt->busy = 0;
	return TotalBytes;

}

int FlashData( TSSPLT* splt, unsigned char* out_buf, int buf_size, 
			  OUTPUT_DUMP pfnOutput, void* context )
{

	unsigned char* pOutbuf;
	unsigned long TotalBytes, Bytes;
	unsigned int Size;
	int 	nBufferIndex;
	int		nTSPacketNum;

	splt->busy = 1;
	TotalBytes = 0;
	nTSPacketNum = buf_size/TS_PACKET_LENGTH;
		
	if ( splt->output_select == 0 )  //TS output
	{
		if (  nTSPacketNum  > 0 )
		{
			while ( NumOfPacketsInPool( splt ) )
			{ 
				nBufferIndex = 0;
				pOutbuf = (unsigned char*)out_buf;
				Size = TS_PACKET_LENGTH;
				while ( PopupPacket( splt, pOutbuf, &Size ) )
				{
					pOutbuf += TS_PACKET_LENGTH;
					nBufferIndex++;
					if ( nBufferIndex >= nTSPacketNum )
						break;
				}
				Bytes = nBufferIndex * TS_PACKET_LENGTH;
				if ( pfnOutput != NULL )
					pfnOutput( context, out_buf, Bytes );
				TotalBytes += Bytes;
			}
		}
	} else    //PS output
	if ( buf_size > 0 )
	{
		Size = buf_size;
		while ( PopupPacket( splt, out_buf, &Size ) )
		{
			if ( pfnOutput != NULL )
				pfnOutput( context, out_buf, Size );
			TotalBytes += Size;
		}
	}

	splt->busy = 0;
	return TotalBytes;

	//return  PushData( splt, NULL, 0, out_buf, buf_size, pfnOutput, context );

}

/////////////////////////////////////////////////////////////////////////////////////////////
//out put buffer
/////////////////////////////////////////////////////////////////////////////////////////////
int PushData2( TSSPLT* splt, const unsigned char* data, int len, ALLOC_BUFFER pfnAllocBuffer, void* context_alloc,
			                  OUTPUT_DUMP pfnOutput, void* context_output )
{
	const unsigned char* pStart;
	unsigned char* pOutbuf;
	unsigned long TotalBytes, Bytes;
	unsigned int Size;
	int 	nBufferIndex;
	int		nTSPacketNum;
	unsigned char* out_buf;
	int buf_size;

	splt->busy = 1;
	TotalBytes = 0;
	
	pStart = (const unsigned char*)data;
	while ( 1 )
	{
		if ( splt->state == SPLT_STOP || pStart == NULL )
		{
			splt->busy = 0;
			return TotalBytes;
		}

		if ( len > 0 )
		{
			Bytes = _MIN(  TS_PACKET_LENGTH*3, len );
			ParseData( splt, pStart, Bytes );
			pStart += Bytes;
			len -= Bytes;
		}

		if ( splt->output_select == 0 )  //TS output
		{
			bool ret = true, data_ready = true;
			while ( ret && data_ready )
			{
				buf_size = pfnAllocBuffer( context_alloc, &out_buf, 0 );
				if ( out_buf == NULL || buf_size == 0 ) break;

				pOutbuf = (unsigned char*)out_buf;
				nTSPacketNum = buf_size/TS_PACKET_LENGTH;
				data_ready = nTSPacketNum  > 0 && NumOfPacketsInPool( splt ) >= nTSPacketNum;
				if (  data_ready  )
				{
						nBufferIndex = 0;
						Size = TS_PACKET_LENGTH;
						while ( (ret = PopupPacket( splt, pOutbuf, &Size )) )
						{
							pOutbuf += TS_PACKET_LENGTH;
							nBufferIndex++;
							if ( nBufferIndex >= nTSPacketNum )
								break;
						}
						Bytes = nBufferIndex * TS_PACKET_LENGTH;
						if ( pfnOutput != NULL )
							pfnOutput( context_output, out_buf, Bytes );
						TotalBytes += Bytes;
				} 
				
				pfnAllocBuffer( context_alloc, &out_buf, 1 );
			}

		} else    //PS output
		{
			bool ret = true;
			while ( ret )
			{   //I should do peekpspacket to save over heat of alloc.PeekPSPacket( PS_BUILDER* pBuilder, short* pType, NULL, unsigned long* pSize )
				buf_size = pfnAllocBuffer( context_alloc, &out_buf, 0 );
				if ( out_buf == NULL || buf_size == 0 )
				{
					flog(( "native.log", "Error: Calling AllocBuffer returns size 0. \n"));
					break;
				}

				Size = buf_size;
				ret = PopupPacket( splt, out_buf, &Size );
				if ( pfnOutput != NULL && ret )
				{
					pfnOutput( context_output, out_buf, Size );
					TotalBytes += Size;
				}
				
				pfnAllocBuffer( context_alloc, &out_buf, 1 );
			}
				
		}

		if ( len <= 0 ) break;
	}
	splt->busy = 0;
	return TotalBytes;

}

int FlashData2( TSSPLT* splt, ALLOC_BUFFER pfnAllocBuffer, void* context_alloc,
			                               OUTPUT_DUMP pfnOutput, void* context_output )
{
	unsigned char* pOutbuf;
	unsigned long TotalBytes, Bytes;
	unsigned int Size;
	int 	nBufferIndex;
	int		nTSPacketNum;
	unsigned char* out_buf;
	int buf_size;

	splt->busy = 1;
	TotalBytes = 0;
	
	if ( splt->output_select == 0 )  //TS output
	{
		bool ret = true, data_ready = true;
		while ( ret && data_ready )
		{
			buf_size = pfnAllocBuffer( context_alloc, &out_buf, 0 );
			if ( out_buf == NULL || buf_size == 0 ) break;

			pOutbuf = (unsigned char*)out_buf;
			nTSPacketNum = buf_size/TS_PACKET_LENGTH;
			data_ready = nTSPacketNum  > 0 && NumOfPacketsInPool( splt ) > 0;
			if (  data_ready  )
			{
					nBufferIndex = 0;
					Size = TS_PACKET_LENGTH;
					while ( (ret = PopupPacket( splt, pOutbuf, &Size )) )
					{
						pOutbuf += TS_PACKET_LENGTH;
						nBufferIndex++;
						if ( nBufferIndex >= nTSPacketNum )
							break;
					}
					Bytes = nBufferIndex * TS_PACKET_LENGTH;
					if ( pfnOutput != NULL )
						pfnOutput( context_output, out_buf, Bytes );
					TotalBytes += Bytes;
			} 
			
			pfnAllocBuffer( context_alloc, &out_buf, 1 );
		}

	} else    //PS output
	{
		bool ret = true;
		while ( ret )
		{
			buf_size = pfnAllocBuffer( context_alloc, &out_buf, 0 );
			if ( out_buf == NULL || buf_size == 0 ) break;

			Size = buf_size;
			ret = PopupPacket( splt, out_buf, &Size );
			if ( pfnOutput != NULL && ret )
				pfnOutput( context_output, out_buf, Size );
			TotalBytes += Size;
			
			pfnAllocBuffer( context_alloc, &out_buf, 1 );
		}
			
	}

	splt->busy = 0;
	return TotalBytes;

	//return  PushData2( splt, NULL, 0, pfnAllocBuffer, context_alloc, pfnOutput, context_output );

}

int RebuildTSPMT( TSSPLT* splt )
{
	flog(( "native.log", "rebuild PMT\n" ));	
	splt->builder_state = 0;
	if (  ConstructPMT( splt->parser ) > 0 )
	{
		int new_channel = FindPlayableChannel( splt->parser );
		if ( new_channel >= 0 )
		{
			flog(( "native.log", "Selected new channel after rebuild PMT(b):%d\n",  new_channel ));			
			//new_channel++;
			SelectChannel( splt->parser, new_channel );
			
			return new_channel;
		}
	}
	flog(( "native.log", "rebuild PMT failed\n" ));			
	return -1;
}


bool InitPushDone( TSSPLT* splt, const unsigned char* data, int len, char* AVInfoBuf, int BufSize )
{
	char buf[PACKETSIZE];
	PushData( splt, data, len, (unsigned char*)buf, sizeof(buf), NULL, NULL );
	if ( splt->AVinfo_notify_posted )
	{
		if ( AVInfoBuf != NULL && BufSize > 0 )
		{
			memset( AVInfoBuf, 0, sizeof(BufSize) );
			GetStreamInfo( splt, AVInfoBuf, BufSize );
		}
		return true;
	}
	return false;
}

void FlushPush( TSSPLT* splt )
{
	splt->last_pcr = 0;
	splt->cur_pcr = 0;
	splt->last_PTS = 0;
	splt->cur_PTS = 0;
	splt->last_sync_pcr = 0;
	splt->pcr_rate = 0;
	splt->last_pcr_packet_num = 0;
	flog(( "native.log", "Flush Push...\n" ));	
}

void DisablePTSFIX( TSSPLT* splt )
{
	splt->enable_ptsfix = false;
	flog(( "native.log", "PTSFix disabled\n" ));	
}

void EnablePTSFIX( TSSPLT* splt )
{
	splt->enable_ptsfix = true; 
	flog(( "native.log", "PTSFix enabled\n" ));	
}

int  GetNetworkNum( TSSPLT* splt )
{
	if ( splt == NULL || splt->si_parser == NULL ) return -1;
	return  GetSINetworkNum( splt->si_parser );
}

int  GetNetworkList( TSSPLT* splt , char* Buf, int MaxBufSize )
{
	if ( splt == NULL || splt->si_parser == NULL ) return -1;
	return  GetSINetworkList( splt->si_parser, Buf, MaxBufSize );
}
void  SelectTSTSID( TSSPLT* splt, unsigned short tsid )
{
	splt->tsid = tsid;
	SelectTSID( splt->parser, tsid );
	flog(( "native.log", "Select TSID:0x%04x(%d) \n", tsid,tsid  ));
}

bool ChannelInfoReady( TSSPLT* splt )
{
	return SIChannelInfoReady( splt->si_parser );
}

void LockATSCChannel( TSSPLT* splt, unsigned short major, unsigned short minor )
{
	splt->lock_ATSC_channel = true;
	splt->ATSC_major = major;
	splt->ATSC_minor = minor;
	flog(( "native.log", "Lock ATSC channel by major:%d minor:%d\n", major, minor ));
}

void LockUpParser( TSSPLT* splt )
{
	TSLockPatPmt( splt->parser );
	flog(( "native.log", "Parser lockup\n" ));
}


void UnLockUpParser( TSSPLT* splt )
{
	TSUnLockPatPmt( splt->parser );
	flog(( "native.log", "Parser unlockup\n" ));
}


static void DumpChannelPid( TSSPLT* ts, AVSTREAM_INFO* StreamInfo, int stream_num )
{
	int i;
	int index, main_audio_index = 0;
	int size;
	PID_TBL *pid_tbl;
	PID_INF pid_info;
	size = sizeof(PID_TBL)*(stream_num+3);

	pid_tbl = (PID_TBL*)sagetv_malloc( size );

	pid_info.channelID = StreamInfo[0].channelID;
	pid_info.programID = StreamInfo[0].programID;
	pid_info.pidNum = stream_num+1;
	pid_info.pids = pid_tbl;

	pid_tbl[0].pid = StreamInfo[0].pid;  //PMT pid
	pid_tbl[0].type = 0;
	//sort pid table as sequence pmt_pid, video_pid, man_audio_pid, audio_pid2,...
	index = 1;
	for ( i = 1; i< stream_num; i++ )
	{
		if ( IsVideoType( StreamInfo[i].streamType ) )
		{
			pid_tbl->pid = StreamInfo[i].pid;
			pid_tbl[index].type = StreamInfo[i].streamType;
			index++;
		}

		if ( StreamInfo[i].pid == ts->selected_audio_pid )
			main_audio_index = i;
	}

	pid_tbl[index].pid = StreamInfo[main_audio_index].pid;
	pid_tbl[index].type = StreamInfo[main_audio_index].streamType;
	index++;

	for ( i = 1; i< stream_num; i++ )
	{
		if ( IsAudioType( StreamInfo[i].streamType ) && 
					ts->selected_audio_pid != StreamInfo[i].pid )
		{
			pid_tbl[index].pid = StreamInfo[i].pid;
			pid_tbl[index].type = StreamInfo[i].streamType;
			index++;
		}
	}

	pid_tbl[index].pid = ts->parser->pcr_pid;	pid_tbl[index].type = 0xfe;
	index++;
	pid_tbl[index].pid = 0;	pid_tbl[index].type = 0;

	ts->pfnPidTblDump( ts->AVInfContext, size, &pid_info );

	sagetv_free( pid_tbl );
}

static int FindMainAudioTrack( TSSPLT* ts, int channel )
{
	AVSTREAM_INFO* StreamInfo;
	int ret = 0;
	int stream_num = GetStreamNum( ts->parser, channel );
	if ( stream_num > MAX_STREAM_NUM ) stream_num = MAX_STREAM_NUM;
	if ( stream_num > 0 ) 
	{
		StreamInfo = (AVSTREAM_INFO*)sagetv_malloc( sizeof(AVSTREAM_INFO)*stream_num );
		if ( StreamInfo != NULL ) 
		{
			stream_num = GetStreamList( ts->parser, channel, StreamInfo, stream_num );	
			ret = PickupMainAudioTrack( ts, StreamInfo,stream_num );
			if ( ret == 1 ) 
			{	ts->builder_state = 1;
			} else
				ret = 0;

			sagetv_free( StreamInfo );
		}
	}

	return ret;
}


static int PickupMainAudioTrack( TSSPLT* ts, AVSTREAM_INFO* StreamInfo, int stream_num )
{
	int i, ret = 0;
	int priority, first_prior;
	unsigned long lauguagecode;
	bool IsATSC;
	int stream_type;
	ts->audio_state = 0, 
	ts->video_state = 0;
	ts->video_stream_id = 0;
	ts->audio_stream_id = 0;
	ts->video_stream_index = 0;
	ts->audio_stream_index = 0;



	if ( ( stream_type = SIParserGetStreamType( ts->si_parser ) ) == 1 ) //ATSC
		IsATSC = true;
	else
	if ( stream_type == 2 ) //DVB
		IsATSC = false;  //DVB
	else
	{
		IsATSC = !IsDVBStream( ts->parser );
	}

	first_prior = 100; 

	if ( ts->audio_stream_num == 1 )
	{
		for ( i = 1; i< stream_num; i++ )
		{
			if ( IsAudioType( StreamInfo[i].streamType ) && !IsTeletextType( StreamInfo[i].Desc )
				&& !IsSubtitleType( StreamInfo[i].Desc ) )
			{
				ts->selected_audio_pid = StreamInfo[i].pid;
				break;
			}
		}
		ret =  1;
	} else
	for ( i = 1; i< stream_num; i++ )
	{
		if ( IsAudioType( StreamInfo[i].streamType ) )
		{
			if ( StreamInfo[i].streamType == 0x81 )
				priority = 10;
			else 
			if ( StreamInfo[i].streamType == 0x03 )
					priority = 20;
			else
			if ( StreamInfo[i].streamType == 0x04 )
			{
				unsigned short language_type;
				priority = 30;
				language_type = GetISO639LanguageType( StreamInfo[i].Desc );
				if ( language_type == 1 ) //clean effects (no language) 
					priority += 30;
				else
				if ( language_type == 2 ) //for the hearing impaired  
					priority += 20;
				else
				if ( language_type == 3 ) //visually impaired commentary 
					priority += 10;
			}
			else
			if ( StreamInfo[i].streamType == 0x82 )
			{
				priority = 40;
			}
			else 
			if ( ( StreamInfo[i].streamType == 0x06 )
				   && !IsTeletextType( StreamInfo[i].Desc )	&& !IsSubtitleType( StreamInfo[i].Desc ) )
			{

				int guess_type;

				guess_type = GuessAuidoType( StreamInfo[i].Desc );   
				if ( guess_type <= 0 ) 
				{
					//StreamInfo[i].streamType = 0;
					//if ( ts->audio_stream_num ) ts->audio_stream_num--;
					priority = 150;
				}
				else
				if ( guess_type == AC3_AUDIO) 
					priority = 15;
				else
				if ( guess_type == DTS_AUDIO) 
					priority = 35;
				else
				if ( guess_type == MPEG_AUDIO) 
					priority = 40;
				else
				if ( guess_type == LPCM_AUDIO ) 
					priority = 50;
				else
					priority = 60;
				
			}
			else
				priority = 250;

			lauguagecode = GetLauguage( StreamInfo[i].Desc );
			if ( lauguagecode== 0x656e6700 && IsATSC ) //pick up "english" fisrt for ATSC
				priority -= 5;
			else
			if ( lauguagecode== 0x4e415200 ) //not pick up "NAR" narrate fisrt
				priority += 5;

			if ( first_prior > priority )
			{
				if ( PidHits( ts->parser, StreamInfo[i].pid ) == 0 )
				{
					//missing audio stream but have desc in PMT 
					if ( GetPacketCounter( ts->parser ) < 1000 ) 
					{
						ret = 4; //re send PMT
						//printf( "Wait more packet to decide audio main track\n" );
						flog(( "native.log", "Wait more packet to decide audio main track.\n"));
					} else
					{
						first_prior = priority;  
						ts->selected_audio_pid = StreamInfo[i].pid;
						ret =  1;
						flog(( "native.log", "Force to pick up audio main track.\n"));
					}
				} else
				{
					first_prior = priority;
					ts->selected_audio_pid = StreamInfo[i].pid;
					ret =  1;
				}
			} else
			if ( first_prior == priority )
			{ //if the same priority, pick up packet rate higher one
				unsigned long rate1, rate2;
				rate1 = PidHits( ts->parser, StreamInfo[i].pid );
				rate2 = PidHits( ts->parser, ts->selected_audio_pid );
				if ( _MAX( rate1, rate2 ) < 60 )
				{
					ret = 4; //re send PMT
					//printf( "Wait more packet to decide audio main track\n" );
					flog(( "native.log", "Wait more packet to decide audio main track\n"));	
				} else
				if ( rate1 > rate2  )
				{
					ts->selected_audio_pid = StreamInfo[i].pid;
					ret =  1;
				}
			}

		}
	}
	flog(( "native.log", "\t\t*main audio pid:0x%x ret:%d\n", ts->selected_audio_pid, ret ));
	return ret;
}


void GetDebugInfo( TSSPLT* splt, unsigned short cmd, char* Buf, unsigned long BufSize )
{
	GetSIDebugInfo( splt->si_parser, cmd, Buf, BufSize );
}


unsigned short ShiftTSID( TSSPLT* ts, unsigned short tsid )
{
	int i;
	for ( i = 0; i<ts->tsids_num; i++ )
		if ( ts->tsids[i] == tsid )
				break;

	if ( i >= ts->tsids_num )
		return ts->tsids[0];

	if ( ++i >= ts->tsids_num ) i = 0;
		return ts->tsids[i];
}

bool IsMultipleTSDI(  TSSPLT* ts )
{
	return ts->tsids_num > 1;
}

unsigned short CurTSID( TSSPLT* ts )
{
	return ts->cur_tsid;
}

int TSSpltBuildState( TSSPLT* splt )
{
	return splt->builder_state;
}


/*
void StartupPluginName( TSSPLT* splt, char* device_name, char* version )
{
	int ret;
	strncpy( splt->device_name, device_name, sizeof(splt->device_name) );
	if ( splt->plungin_enabled )
	{
		ret = CallPluginStartup(  splt->plugin, device_name, version ); 
		if ( ret <= 0 )
			splt->plungin_enabled = false;
	}
}
*/
