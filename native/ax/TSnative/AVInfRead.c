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

#include "AVUtility.h"
#include "TSnative.h"
#include "TSParser.h"
#include "PSParser.h"
#include "AVInfRead.h"


#ifdef WIN32

#include <stdio.h>
#include <io.h>
#include <string.h>
#include <stdlib.h>	
#include <memory.h>	
#include <fcntl.h>
#include <share.h>
#include <sys/types.h>
#include <sys/stat.h>

#define FOPEN	sopen
#define FCLOSE	close
#define FSEEK	_lseeki64
#define FTELL	_telli64
#define ATOI	atoi

#else



#ifdef __GNUC__
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/errno.h>
#include <unistd.h>
#include <memory.h>	
#include <stdlib.h>

#if defined(__APPLE__)

#define FOPEN	open
#define FCLOSE	close
	// darwin off_t is 64 bit
#define FSEEK	lseek
#define FTELL(x)	lseek(x, 0, SEEK_END)

#else // !defined(__APPLE__)

#define FOPEN	open64
#define FCLOSE	close
#define FSEEK	lseek64
#define FTELL(x)	lseek64(x, 0, SEEK_END) 

typedef long long off64_t;
extern off64_t lseek64( int filedes, off64_t offset, int whence );
extern off64_t tell64( int filedes );

#endif // __APPLE__

#define ATOI	atoi

#endif // __GNUC__

#endif




/* ///////////////////////////////////////////////////////////////////////////////////////////// 
parser hooker section
 ////////////////////////////////////////////////////////////////////////////////////////////*/
static	long TSAVStreamHook( void* handle, short channel, void* data );
static	long TSAVSignalHook( void* handle, short channel, void* data );
static	long PESAVStreamHook( void* handle, short channel, void* data );
static	long PESAVSignalHook( void* handle, short channel, void* data );
static  void SetStreamPTS( ENV* env, int channel, LONGLONG* pllPTS );
static  LONGLONG  GetStreamLastPTS( ENV* env, int channel  );
static  bool StreamReady( ENV* env );
static  bool StreamPlayable( ENV* env );
static  LONGLONG  ReadFileLastPTS( ENV* env, int fd, LONGLONG *filesize );
static unsigned long GetStreamTotalRate( ENV* env );
static char* BuildFormatDesc( ENV* env, char* buf, int size );
static char* BuildDurationDesc( LONGLONG llDur, char* buf, int size );

static int DectTSMainAudioPid( ENV* env, int channel );
static int DectPSMainAudioTrk( ENV* env );
long TSAVStreamHook( void* handle, short channel, void* data )
{
	TS_AV_INFO* av_info = (TS_AV_INFO*)data;
	TS_PARSER * parser;
	ENV*		env;
	int			stream_index;
	const unsigned char* pData;
	long        Size;
	LONGLONG    llPTS;
	PES_INFO	PESInfo ={0};
	bool        bPESInfoReady=false;

	if ( av_info == NULL || handle == NULL )
		return -1;

	parser = (TS_PARSER *)handle;
	env = (ENV*)parser->env;
	pData = (const unsigned char*)av_info->data;
	Size = av_info->bytes;

	if ( pData == NULL || Size == 0 ) 
		return 0;

	if ( av_info->sub_channel >= MAX_STREAM_NUM  || av_info->sub_channel < 0 )
		return -1;

	PESInfo.bGroupStart = av_info->start_group_flag;

	//expected parser to anlyze stream type for unkown stream that is needed by TSParser
	if ( channel == -1 )
	{
		if ( av_info->start_group_flag )
		{
			int stream_type;
			//AV_CONTEXT av={0};

			if ( (bPESInfoReady = UnpackPESHeader( (const unsigned char*)av_info->data, av_info->bytes, &PESInfo ))>0 )
			{
				//in case bad code in PES header
				if ( PESInfo.HeaderLen > Size ) PESInfo.HeaderLen = (unsigned short)Size;
				//rip off PES header
				pData += PESInfo.HeaderLen;
				Size  -= PESInfo.HeaderLen;
			}
			stream_type = DecodeAVHeader( &env->stream[av_info->sub_channel ].av, &PESInfo, (unsigned char)av_info->type,
										(const unsigned char*)av_info->data, (int)av_info->bytes );
			if ( stream_type )
			{
				av_info->type = stream_type;
				return 1;
			} else
				return 0;
			
		}
		return 0;
	}

	if ( env == NULL )
		return -1;

	stream_index = av_info->sub_channel;

	if ( channel != env->selected_channel || stream_index >= MAX_STREAM_NUM || stream_index < 0 )
		return 0;

	llPTS = 0;
	env->stream[stream_index].data_frames++; 
	//env->last_tick = GetTickCount();
	//if stream codec information is not availabe, we decode it.
	if (  av_info->start_group_flag )
	{
		if ( (bPESInfoReady = UnpackPESHeader( (const unsigned char*)av_info->data, av_info->bytes, &PESInfo )) > 0 )
		{
			//in case bad code in PES header
			if ( PESInfo.HeaderLen > Size ) 
				PESInfo.HeaderLen = (unsigned short)Size;

			env->stream[stream_index].stream_id = PESInfo.StreamId;

			//rip off PES header
			pData += PESInfo.HeaderLen;
			Size  -= PESInfo.HeaderLen;

			//if it has PTS, adjust PTS
			if ( PESInfo.HasPts )
			{
				llPTS = PESInfo.llPts;
				//if ( env->stream[stream_index].stream_ready )
				SetStreamPTS( env, stream_index, &llPTS );
			}
		}
	}

	if ( !( env->stream_state & PARSER_STREAM_DONE )  )
	{
		int stream_type = 0;
		//AV_CONTEXT	   av={0};

		if ( !bPESInfoReady )
		{
			PESInfo.HeaderLen = 0; 
			PESInfo.PacketLen = av_info->bytes;
			PESInfo.StreamId = env->stream[stream_index].stream_id;
		}

		if ( env->stream[stream_index].av.VideoType == 0x0 && env->stream[stream_index].av.AudioType == 0x0  )
		{
			unsigned char* desc;int length;
			if ( av_info->type == 0x06 && GetPMTDescData( parser, av_info->pid, &desc, &length ) &&
				( IsTeletextTypeDescriptor( (char*)desc, length ) || IsSubtitleDescriptor((char*)desc, length) ) )
				stream_type = TELTEXT_TYPE;
			else
				stream_type = DecodeAVHeader( &env->stream[stream_index].av, &PESInfo, (unsigned char)av_info->type,
		                     (const unsigned char*)av_info->data, (int)av_info->bytes );			
		}
		//stream_type = DecodeAVHeader( &env->stream[stream_index].av, &PESInfo, (unsigned char)av_info->type,
		//						(const unsigned char*)av_info->data, (int)av_info->bytes );

		if ( stream_type && !env->stream[stream_index].stream_ready ) 
		{
			if ( IsVideoType( av_info->type ) )
			{
				//env->stream[stream_index].av = av;
				env->stream[stream_index].stream_id = env->stream[stream_index].av.VideoPES.StreamId;
				env->stream[stream_index].stream_ready= true;
				env->stream[stream_index].pid = av_info->pid;
				env->video_state = 1;
				//DbgLog( (LOG_TRACE, 3, TEXT( "found video pid:0x%02x\n") , av_info->pid ) );
			} else 
			if ( IsAudioType( av_info->type ) )
			{
				//env->stream[stream_index].av = av;
				env->stream[stream_index].stream_id = env->stream[stream_index].av.AudioPES.StreamId;
				env->stream[stream_index].stream_ready = true;
				env->stream[stream_index].pid = av_info->pid;
				env->audio_state = 1;

				//DbgLog( (LOG_TRACE, 3, TEXT( "found audio pid:0x%02x\n") , av_info->pid ) );
			}
			//when video and first audio stream is ready, start dump data
			if ( env->video_state  )//&& env->audio_state )
			{
				int i;
				for ( i = 0; i<env->stream_num; i++ )
				{
					if ( IsAudioType( env->stream[i].stream_type ) && env->stream[i].stream_ready )
						env->parser_state = 2 ;
				}
			}

			//all stream is ready
			if ( env->stream_state & PARSER_STREAM_DONE )
				env->parser_state = 3 ;
		}

	} //end of if ( !env->stream_state  )


	return 1;
}

long TSAVSignalHook( void* handle, short channel, void* data )
{
	int i;
	TS_PARSER * parser;
	ENV* env;

	SIGNAL_INFO* signal_info;
	TS_AV_INFO* av_info;

	signal_info = (SIGNAL_INFO*)data;
	av_info = (TS_AV_INFO*)data;

	if ( signal_info == NULL || handle == NULL )
		return -1;

	parser = (TS_PARSER *)handle;
	env =  (ENV*)parser->env;

	if ( env == NULL )
		return -1;

	if ( signal_info->type == STREAM_START )
	{
		int total_channel;
		PROGRAM_INFO *Channels;

		total_channel = GetProgramNumber( (TS_PARSER*)env->parser );
		Channels = (PROGRAM_INFO *)sagetv_malloc( sizeof(PROGRAM_INFO)*total_channel );
		total_channel = GetProgramList( parser, Channels, total_channel );

		//DbgLog( (LOG_TRACE, 3, TEXT( "TS Program (total channel:%d)"), total_channel ));
		for ( i = 0; i<total_channel; i++ )
		{
			//DbgLog( (LOG_TRACE, 3, TEXT("Channel %02d  PMT pid:0x%04x program:0x%04x service:%d"), 
			//		Channels[i].channelID, Channels[i].pid, Channels[i].programID, Channels[i].type ) );  
		}
		sagetv_free( Channels );
		env->audio_state = 0, env->video_state = 0;
		env->stream_state |= PARSER_STREAM_START;
		
	}

	if ( signal_info->type == STREAM_READY && -1 != channel )
	{	
		int stream_num;
		AVSTREAM_INFO* AVStreamInfo;
		stream_num = GetStreamNum( (TS_PARSER*)env->parser, channel );
		if ( stream_num <= 0 ) return 0;
		if ( stream_num >  MAX_STREAM_NUM ) stream_num = MAX_STREAM_NUM;
		AVStreamInfo = (AVSTREAM_INFO*)sagetv_malloc( sizeof(AVSTREAM_INFO)*(stream_num) );
		if( AVStreamInfo == NULL ) return 0;

		stream_num = GetStreamList( (TS_PARSER*)parser, channel, AVStreamInfo, stream_num );
		//DbgLog( (LOG_TRACE, 3, TEXT( "Channel:%d streams: ( PMT pid:0x%04x, Program:0x%04x )"), 
		//		 channel,    AVStreamInfo[0].pid, AVStreamInfo[0].programID ));
		//for ( i = 1; i< stream_num; i++ )
		//{
			//DbgLog( (LOG_TRACE, 3, TEXT( "\tType:%d Pid:0x%03x" ), 
			//         AVStreamInfo[i].streamType, AVStreamInfo[i].pid ) );
		//}
		if ( env->selected_channel == channel )
		{
			env->video_stream_num = 0;
			env->audio_stream_num = 0;
			env->parser_state = 1;
			env->stream_num = stream_num-1;
			//env->start_tick = GetTickCount();
			memcpy( env->program_desc,  AVStreamInfo[0].Desc, sizeof(env->program_desc) ); 
			for ( i = 1; i< stream_num; i++ )
			{
				int size;
				env->stream[i-1].stream_present = true;
				env->stream[i-1].stream_type = (unsigned char)AVStreamInfo[i].streamType;

				size = _MIN( sizeof(env->stream[i-1].stream_desc), AVStreamInfo[i].Desc[0] );
				memcpy( env->stream[i-1].stream_desc, AVStreamInfo[i].Desc, size );
				env->stream[i-1].stream_desc[0] = size;

				if ( IsVideoType( AVStreamInfo[i].streamType ) )
					env->video_stream_num++;
				if ( IsAudioType( AVStreamInfo[i].streamType ) )
					env->audio_stream_num++;

				if ( env->stream[i-1].stream_type == 0x06 && 
					( IsTeletextType( AVStreamInfo[i].Desc ) || IsSubtitleType(AVStreamInfo[i].Desc) ) )
				{
					env->stream[i-1].stream_type = TELTEXT_TYPE;
					if ( env->audio_stream_num > 0 ) env->audio_stream_num--;
				}
			}

			if ( env->video_stream_num == 0 ) env->channel_type &= ~VIDEO_PRESENT_MASK;
			if ( env->audio_stream_num == 0 ) env->channel_type &= ~AUDIO_PRESENT_MASK;
		} 

		if ( -1 == channel )
		{
			//found PMT before PAT that is ready to be used
		}

		env->stream_state |= PARSER_STREAM_READY;
		sagetv_free( AVStreamInfo );

	}

	if ( signal_info->type == STREAM_CLOSE )
	{
		env->stream_state = 0;
	}

	return 0;
}


long PESAVStreamHook( void* handle, short channel, void* data )
{
	PES_AV_INFO* av_info = (PES_AV_INFO*)data;
	PS_PARSER * parser;
	ENV*		env;
	int			stream_index;
	LONGLONG llPTS = 0;


	if ( av_info == NULL || handle == NULL )
		return -1;

	parser = (PS_PARSER *)handle;
	env = (ENV*)parser->env;

	if ( env == NULL )
		return -1;

	stream_index = channel;
	//is video data
	if ( av_info->start_group_flag && stream_index < MAX_STREAM_NUM )
	{
		env->stream[stream_index].stream_type = av_info->stream_type; //parser->stream_type[stream_index];
		env->stream[stream_index].stream_id   = av_info->stream_id; //parser->PES[stream_index].StreamId;
		env->stream[stream_index].av = parser->AV[stream_index];
		env->stream[stream_index].stream_present = 1;
		env->stream[stream_index].pid = -1;

		if ( parser->AV[stream_index].VideoType && !env->stream[stream_index].stream_ready )
		{
			env->stream[stream_index].stream_ready = true;
			env->video_state = 1;
		}
		if ( parser->AV[stream_index].AudioType && !env->stream[stream_index].stream_ready )
		{
			env->stream[stream_index].stream_ready = true;
			env->audio_state = 1;
		}
	}

	if ( av_info->start_group_flag && av_info->has_PTS )
	{
		llPTS = av_info->PTS;
		SetStreamPTS( env, stream_index, &llPTS ); 
	}

	
	return 1;
}

long PESAVSignalHook( void* handle, short channel, void* data )
{
	PS_PARSER * parser;
	ENV* env;
	SIGNAL_INFO* signal_info;
	TS_AV_INFO* av_info;

	signal_info = (SIGNAL_INFO*)data;
	av_info = (TS_AV_INFO*)data;

	if ( signal_info == NULL || handle == NULL )
		return -1;

	parser = (PS_PARSER *)handle;
	env =  (ENV*)parser->env;

	if ( env == NULL )
		return -1;

	if ( signal_info->type == STREAM_START )
	{
		//DbgLog( (LOG_TRACE, 3, TEXT( "PES Stream Start" ) ));
		env->parser_state = 1;
		env->stream_num = -1;
		env->stream_state |= PARSER_STREAM_START;
	}

	if ( signal_info->type == STREAM_READY )
	{
		int video_track, audio_track;//, video_num, audio_num;
		int video_stream_type, audio_stream_type;
		if ( GetMainTrack( parser, &video_track, &audio_track ) > 0 )
		{
			env->sage_inf_flag = true;
			if ( video_track >= 0  )
				env->video_main_track = video_track;

			if ( audio_track >= 0  )
				env->audio_main_track = audio_track;

			//H.264 inforamation
		    if ( GetMainStreamType( parser, &video_stream_type, &audio_stream_type ) > 0 )
			{
				//env->main_video_stream_type = video_stream_type;
				//env->main_audio_stream_type = audio_stream_type;
				//if ( video_stream_type == H264_STREAM_TYPE )
				//	 env->h264_frame_rate = GetH264FrameRate( parser );
			}

			if ( GetTrackNum( parser, &env->video_stream_num, &env->audio_stream_num ) > 0 )
			{
				if ( env->video_stream_num == 0 && video_stream_type == 0 )
					env->channel_type &= ~VIDEO_PRESENT_MASK;
				if ( env->audio_stream_num == 0 && audio_stream_type == 0 )
					env->channel_type &= ~AUDIO_PRESENT_MASK;

				env->stream_num = env->video_stream_num + env->audio_stream_num;
				if ( env->stream_num >= MAX_STREAM_NUM ) 
					env->stream_num = MAX_STREAM_NUM;

			}

		}

		//DbgLog( (LOG_TRACE, 3, TEXT( "PES Stream Ready [%s]" ), found_sage_info ? "SageTV encoded": "" ));
		env->parser_state = 1;
	}

	if ( signal_info->type == STREAM_CLOSE )
	{
		env->stream_state = 0;
		//DbgLog( (LOG_TRACE, 3, TEXT( "PES Stream close\n" ) ));
	}

	return 0;
}

int DetectInputStreamFormat( unsigned char* pData, long dwSize )
{
	int proTS  = 0;
	int proPES = 0;
	unsigned char uType = 0;

	proTS = CheckTSPacket(  pData, dwSize );
	if ( proTS )
	{
		//check if it's m2t format
		//uType = TYPE_M2T_FORMAT;
		uType = TS_FORMAT;
		return uType;
	} 

	proPES = CheckMPEGPacket( pData, dwSize );
 	if ( proPES && proPES < 1024 )
		return PES_FORMAT;

	if ( proPES == 0 &&  proTS == 0 ) return 0;

	if ( proPES == 0 )  
		uType = TS_FORMAT;
	else
	if ( proTS == 0 )  
		uType = PES_FORMAT;
	else
	if ( proTS > proPES ) 
		uType = PES_FORMAT;
	else
		uType = TS_FORMAT;

	return uType;
}

int GetAVInf( char* FileName, unsigned long CheckSize, bool bLiveFile, int RequestedTSChannel, 
			   char* FormatBuf, int FormatSize, char* DurationBuf, int DurationSize, int* ProgramNum )
{
	ENV env={0};
	unsigned long dwTotalInitLen; 
	unsigned long dwIncLen;
	unsigned char *pbData;
	unsigned uInputStreamFormat = 0;
	int Bytes;
	unsigned char* pData;
	bool bReparse = false;
	bool bScrambledChannel = false;
	bool bStreamReady = false;

	int BlockSize;
	int Size;
	unsigned long dwCheckBytes;
	unsigned long dwPushBytes;
	LONGLONG rtDur = 0;

#ifdef WIN32	

	int fd = FOPEN( FileName, _O_RDONLY|_O_BINARY, _SH_DENYNO , _S_IREAD|_S_IWRITE );

#else

	int fd = open( FileName, O_RDONLY );	

#endif	

	if ( fd < 0 )

		return -1;


	if ( CheckSize == 0 )
		dwTotalInitLen = 4*12*524288; // for a really bad DTV signal, this can occur
	else
		dwTotalInitLen =  CheckSize;

    /*  Just read 32K and look for interesting stuff */
	dwIncLen = 32*1024;  
    pbData = sagetv_malloc(dwIncLen);
    if ( pbData == NULL ) 
	{
		FCLOSE(fd);
		return -2;
	}

	memset( pbData, 0, dwIncLen );
	Bytes = (int)read( fd, pbData, dwIncLen );
    if ( Bytes == 0 ) 
	{
        sagetv_free( pbData );
		FCLOSE(fd);
		return 0;
    }
	dwTotalInitLen = dwTotalInitLen > dwIncLen ? dwTotalInitLen - dwIncLen : 0;

	//detect input format first
	while (  ( uInputStreamFormat == 0 ) && dwTotalInitLen )
	{
		uInputStreamFormat = DetectInputStreamFormat( pbData, dwIncLen );
		if ( uInputStreamFormat )
			break;

		memset( pbData, 0, dwIncLen );
		Bytes = (int)read( fd, pbData, dwIncLen );
		if ( Bytes == 0 )
			break;

		dwTotalInitLen = dwTotalInitLen > dwIncLen ? dwTotalInitLen - dwIncLen : 0;
	}

	if ( uInputStreamFormat == 0 )
	{
		sagetv_free( pbData );
		FCLOSE(fd);
		return 0;
	}

	FSEEK( fd, 0, SEEK_SET );
	memset( pbData, 0, dwIncLen );
	Bytes = (int)read( fd, pbData, dwIncLen );
	if ( Bytes == 0 ) 
	{
        sagetv_free( pbData );
		FCLOSE(fd);
		return 0;
    }

	memset( &env, 0, sizeof(env) );
	env.video_main_track = -1;
	env.audio_main_track = -1;
	env.uInputStreamFormat = uInputStreamFormat;
	env.channel_type = VIDEO_PRESENT_MASK|AUDIO_PRESENT_MASK;

	if ( uInputStreamFormat == TS_FORMAT )
	{
		TS_PARSER* parser;
		parser = TSParserOpen();

		parser->env = &env;
		env.parser = parser;

		//setup hook
		SetTSDumpAVCallback( parser, TSAVStreamHook );
		SetTSDumpSignalCallback( parser, TSAVSignalHook );

		//setup selected channel
		env.selected_channel = RequestedTSChannel;
		StartParser( parser );
		SelectChannel( parser, (unsigned short)env.selected_channel );
		TSPidStatEnable( parser, true );

	} else
	if ( uInputStreamFormat == PES_FORMAT )
	{
		PS_PARSER* parser;
		parser = PSParserOpen( );

		parser->env = &env;
		env.parser = parser;

		//setup hook
		SetPSDumpAVCallback( parser, PESAVStreamHook );
		SetPSDumpSignalCallback( parser, PESAVSignalHook );

	} else
	{
		//DbgLog((LOG_TRACE, 2, TEXT("MPEG2 DeMux Init failed: unknown data format")));
		sagetv_free( pbData );
		FCLOSE(fd);
		return 0;
	}

	pData = (unsigned char*)pbData;
	//for best performace not overheating on buffer alligement, set block size
	if ( uInputStreamFormat == TS_FORMAT || uInputStreamFormat == M2T_FORMAT )
		BlockSize = TS_PACKET_LENGTH * 20;
	else
		BlockSize = 1024*4;

	dwPushBytes = 0;
	dwCheckBytes = dwTotalInitLen;
	do 
	{
		while ( Bytes > 0 )
		{
			Size = _MIN( Bytes, BlockSize );

			if ( uInputStreamFormat == TS_FORMAT )
				TSProcessBlock( (TS_PARSER*)env.parser, Size, (char*)pData );
			else
				PSProcessBlock( (PS_PARSER*)env.parser, Size, (char*)pData );

			pData += Size;
			Bytes -= Size;
			dwPushBytes += Size;

			if ( (bStreamReady = StreamReady( &env )) )
				break;
		}

		if ( ( bStreamReady = StreamReady( &env ) ) )
			break;

		memset( pbData, 0, dwIncLen );
		Bytes = (int)read( fd, pbData, dwIncLen );
		if ( Bytes == 0 ) 
			break;

		pData = (unsigned char*)pbData;

		dwCheckBytes = dwCheckBytes > dwIncLen ? dwCheckBytes - dwIncLen : 0;

	} while ( dwCheckBytes > 0 );


	//if first failed with no PAT/PMT, construct fake PAT PMT table based on stream type, or there is a playable channel,
	//reparse one more
	if ( !( bStreamReady = StreamReady( &env )) )
	{
		if ( !( env.stream_state & (PARSER_STREAM_START|PARSER_STREAM_READY) ) )
		{
			if ( uInputStreamFormat == TS_FORMAT )
				bReparse = ConstructPMT( (TS_PARSER*)env.parser );
		}
	}
	if ( bReparse )
	{
		//rewind data, reparse data again.
		FSEEK( fd, 0, SEEK_SET );
		memset( pbData, 0, dwIncLen );
		Bytes = (int)read( fd, pbData, dwIncLen );
		bReparse = Bytes > 0;

		dwPushBytes = 0;
		pData = (unsigned char*)pbData;
		dwCheckBytes = dwTotalInitLen;
		if ( bReparse )
		{
			do	{
				while ( Bytes > 0 )
				{
					Size = _MIN( Bytes, BlockSize );

					if ( uInputStreamFormat == TS_FORMAT )
						TSProcessBlock( (TS_PARSER*)env.parser, Size, (char*)pData );
					else
						PSProcessBlock( (PS_PARSER*)env.parser, Size, (char*)pData );

					pData += Size;
					Bytes -= Size;
					dwPushBytes += Size;
					
					if ( StreamReady( &env ) )
						break;
				}

				if ( StreamReady( &env ) )
					break;

				memset( pbData, 0, dwIncLen );
				Bytes = (int)read( fd, pbData, dwIncLen );
				if ( Bytes <= 0 ) 
					break;

				pData = (unsigned char*)pbData;

				dwCheckBytes = dwCheckBytes > dwIncLen ? dwCheckBytes - dwIncLen : 0;

			} while ( dwCheckBytes > 0 );
		}
	}

	sagetv_free( pbData );	
	if ( uInputStreamFormat == TS_FORMAT )
	{
		*ProgramNum = GetProgramNumber( (TS_PARSER*)env.parser );
	} else
	if ( uInputStreamFormat == PES_FORMAT )
	{
		*ProgramNum = 1;
	} 

	if ( !bStreamReady && uInputStreamFormat == TS_FORMAT )
		bScrambledChannel = IsScrambledChannel( (TS_PARSER*)env.parser, RequestedTSChannel );


	if ( env.stream_num && !bScrambledChannel )
	{
		unsigned long lRateBound;
		LONGLONG llByteLength;
		//caculate data play duration
		if ( uInputStreamFormat == PES_FORMAT )
			lRateBound = GetStreamBoundRate( (PS_PARSER*)env.parser );
		else
			lRateBound = GetStreamTotalRate( &env );

		if ( lRateBound == 0 )
			lRateBound = 200000; // 2MBps about for DTV stuff

		if ( !bLiveFile )
		{
			//for best performace not overheating on buffer alligement, set block size
			rtDur = ReadFileLastPTS( &env, fd, &llByteLength );
			if ( rtDur )
			{
				env.avg_byte_rate = (unsigned long)( llByteLength*UNITS/(rtDur) );
			}
		} else
		{
			//Hauppauge HDPVR H.264 gives a wrong video bps, confuses player, I use avg bps for it, remove it after Hauppauge fixing  //ZQ
			int i, isH264 = 0;
			for ( i = 0; i<MAX_STREAM_NUM; i++ )
				if (  env.stream[i].stream_present && env.stream[i].stream_type == 0x1B )
				{	isH264 = true;
					break;
				}

			rtDur = 0;
			if ( uInputStreamFormat == TS_FORMAT && isH264  && lRateBound * 8 == 20000000 )
			{
				rtDur = ReadFileLastPTS( &env, fd, &llByteLength );
				if ( rtDur )
				{
					env.avg_byte_rate = (unsigned long)( llByteLength*UNITS/(rtDur) );
				}
			} 

			if ( rtDur == 0 )
			{
				llByteLength = FTELL( fd );
				rtDur = (llByteLength * UNITS)/lRateBound;
			}
			
		}

	
	}

	FCLOSE( fd );
	if ( !bStreamReady && uInputStreamFormat == TS_FORMAT )
		bScrambledChannel = IsScrambledChannel( (TS_PARSER*)env.parser, RequestedTSChannel );

	if ( StreamPlayable( &env) && bScrambledChannel )
	{
		sprintf( FormatBuf, "SCRAMBLED" );
		DurationBuf[0] = 0x0;
	}	
	else
	if ( env.stream_num == 0  )
	{
		if ( FormatSize )   FormatBuf[0] = 0x0;
		if ( DurationSize ) DurationBuf[0] = 0x0;
	} else
	{
		BuildFormatDesc( &env, FormatBuf, FormatSize );
		BuildDurationDesc( rtDur, DurationBuf, DurationSize );
	}

	if ( uInputStreamFormat == TS_FORMAT )
	{
		TSParserClose( (TS_PARSER*)env.parser );
	} else
	if ( uInputStreamFormat == PES_FORMAT )
	{
		PSParserClose( (PS_PARSER*)env.parser );
	} 


	return 1;
}

bool StreamReady( ENV* env )
{
	int i, present_num = 0;
	if ( env->stream_num == 0  ) 
		return false;

	if ( env->uInputStreamFormat == TS_FORMAT )
	{
		for ( i = 0; i<MAX_STREAM_NUM; i++ )
		{
			if (  env->stream[i].stream_present )
			{
				if ( IsVideoType( env->stream[i].stream_type ) || IsAudioType( env->stream[i].stream_type ) ) 
				{
					if ( !env->stream[i].stream_ready )
						return false;
				}
				present_num++;
			}
		}
		if ( present_num == 0 )
			return false;

		return true;

	}
	else
	{
		if ( env->stream_num == -1 )
		  return  StreamPlayable( env );
		else
		{
			for ( i = 0; i<MAX_STREAM_NUM; i++ )
			{
				if (  env->stream[i].stream_present )
					present_num++;
			}

			return ( present_num >= env->stream_num );

		}
	}
	return true;
}


bool StreamPlayable( ENV* env )
{
	int i;
	bool video_ready = false, audio_ready = false;
	int  audio_tracking = 0;
	unsigned long ellipse_tick;

	env->nTrackPTSChannel = 0;

	if ( env->channel_type & VIDEO_PRESENT_MASK )
	{
		// check if both video codec is available
		for ( i = 0; i<MAX_STREAM_NUM; i++ )
			if ( env->stream[i].stream_present && IsVideoType( env->stream[i].stream_type ) )
			{
				//if video track is specified
				if ( env->video_main_track >= 0  )
				{
					video_ready =   env->stream[i].stream_ready && (  i == env->video_main_track );
				} else
				{
					video_ready =   env->stream[i].stream_ready ;
				}
				if ( video_ready )
				{
					env->nTrackPTSChannel = i;
					break;
				}
			}
	} else
	{
		video_ready = true;
	}

	// check if first audio stream codec is available, we think first audio stream is main stream
	// if first audio stream doesn't received any data for long time, give up it search second audio stream
	// some TV station put audio on first audio stream, and others are silence 
	if ( env->channel_type & AUDIO_PRESENT_MASK )
	{
		ellipse_tick = env->last_tick - env->start_tick;
		for ( i = 0; i<MAX_STREAM_NUM; i++ )
		{
			if ( env->stream[i].stream_present && IsAudioType( env->stream[i].stream_type ) )
			{
				if ( env->audio_main_track >= 0 )
				{
					audio_ready = ( env->stream[i].stream_ready && env->stream[i].firstPTS && 
						( i == env->audio_main_track ) );
				} else
				{
					audio_ready = ( env->stream[i].stream_ready && env->stream[i].firstPTS );
				}

				if ( env->dwAudioCtrl == 0 )
				{	//waiting for first audio stream in stream table ready to use it as audio output, 
					//if waiting is timeout, pick up other other stream.
					if ( audio_ready && audio_tracking == 0 )
						break;

				} else
				{
					//pick up first ready audio stream
					if ( audio_ready && !(env->channel_type & VIDEO_PRESENT_MASK) )
					{
						env->nTrackPTSChannel = i;
						break;
					}
				}
				audio_tracking++;
			}
		}
	} else
	{
		ellipse_tick = env->last_tick - env->start_tick;
		audio_ready = true;
	}

	//because audio PTS is accurate than video, I use it as PTS tracking if it's availabe
	//if ( audio_ready )
	//	env->nTrackPTSChannel = i;

	if ( env->stream[env->nTrackPTSChannel].firstPTS == 0 )
		return false;

	if ( video_ready && audio_ready )
		return true;

	return false;
}

LONGLONG  GetStreamLastPTS( ENV* env, int channel  )
{
	LONGLONG deltaPTS;
	LONGLONG lastPTS;
	if ( env->stream[channel].lastPTS == 0 )
		return 0;

	if ( env->stream[channel].lastPTS < env->llFirstPTS )
	{
		//PTS round up
		lastPTS = 0x200000000ULL + env->stream[channel].lastPTS;
	} else
	{
		lastPTS = env->stream[channel].lastPTS;
	}

	deltaPTS = lastPTS - env->llFirstPTS;
	
	if ( deltaPTS & 0x100000000ULL )
		deltaPTS = ( deltaPTS & 0x00000000ffffffffULL ) | 0xffffffff00000000ULL;

	return  (deltaPTS * UNITS) / MPEG_TIME_DIVISOR;
}

void SetStreamPTS( ENV* env, int channel, LONGLONG* pllPTS )
{
	if ( env->stream[channel].firstPTS == 0 ) // || env->stream[channel].firstPTS > *pllPTS ) //round up cause problem
		env->stream[channel].firstPTS = *pllPTS;

	env->stream[channel].lastPTS = *pllPTS;
}


LONGLONG  ReadFileLastPTS( ENV* env, int fd, LONGLONG *pllBytes )
{
	int i,j ;
	int Bytes, Size, BlockSize;
	char* pData;
	LONGLONG llByteLength, rtDur;
	unsigned long dwIncLen = 32*1024;  
	unsigned char* pbData = sagetv_malloc( dwIncLen );
	int pmt_locked;

    if (pbData == NULL) 
		return 0;

	if ( env->uInputStreamFormat == TS_FORMAT || env->uInputStreamFormat == M2T_FORMAT )
		BlockSize = TS_PACKET_LENGTH * 20;
	else
		BlockSize = 1024*2;

	FSEEK( fd, 0, SEEK_END );
	llByteLength = FTELL( fd );
	*pllBytes = llByteLength;

	//if it's data file, we read its last PTS to get accurate duration
	if ( llByteLength > (LONGLONG)dwIncLen )
		for ( j = 0; j<MAX_STREAM_NUM; j++ )
			env->stream[j].lastPTS = 0;

	//lock Pat Pmt table not to be updated; if program's changed or finished, Pat Pmt is updated at file end part, 
	//so we can't find correct stream anymore with backward search
	if ( env->uInputStreamFormat == TS_FORMAT || env->uInputStreamFormat == M2T_FORMAT )
		pmt_locked = TSLockPatPmt( (TS_PARSER*)env->parser );
	i = 1;
	while (  i<350 && llByteLength > (LONGLONG)i*dwIncLen )
	{
		FSEEK( fd, llByteLength-i*dwIncLen, SEEK_SET );
		memset( pbData, 0, dwIncLen );
		Bytes = (int)read( fd, pbData, dwIncLen );
		if ( Bytes <= 0 )
			break;
		
		if ( env->uInputStreamFormat == TS_FORMAT )
			TSResetBlockBuffer( (TS_PARSER*)env->parser );
		else
			PSResetBlockBuffer( (PS_PARSER*)env->parser );

		pData = (char*)pbData;
		while ( Bytes > 0 )
		{
			Size = _MIN( Bytes, BlockSize );

			if ( env->uInputStreamFormat == TS_FORMAT )
				TSProcessBlock( (TS_PARSER*)env->parser, Size, pData );
			else
				PSProcessBlock( (PS_PARSER*)env->parser, Size, pData );

			pData += Size;
			Bytes -= Size;
			
		}

		//last PTS is found
		if ( env->stream[env->nTrackPTSChannel].lastPTS != 0 )
		{
			//in the case that PTS doesn't change, another PTS track stream
			if ( env->stream[env->nTrackPTSChannel].firstPTS == env->stream[env->nTrackPTSChannel].lastPTS )
			{
				env->nTrackPTSChannel = 0;
				for ( j = 0; j<MAX_STREAM_NUM; j++ )
				{
					if (  env->stream[j].lastPTS != 0 && env->stream[j].firstPTS < env->stream[j].lastPTS )
					{
						env->nTrackPTSChannel = j;
						break;
					}
				}
				if ( j < MAX_STREAM_NUM && GetStreamLastPTS( env, env->nTrackPTSChannel ) != 0  )
					break;
			} else
			  break;
		}

		i++;
	}
	if ( env->uInputStreamFormat == TS_FORMAT || env->uInputStreamFormat == M2T_FORMAT )
		if ( !pmt_locked )	TSUnLockPatPmt( (TS_PARSER*)env->parser );


	sagetv_free( pbData );

	env->llFirstPTS = env->stream[env->nTrackPTSChannel].firstPTS; 
	rtDur=GetStreamLastPTS( env, env->nTrackPTSChannel );
	for ( j = 0; j<MAX_STREAM_NUM; j++ )
	{
		if ( env->stream[j].firstPTS !=0 && env->stream[j].lastPTS != 0 && env->stream[j].firstPTS < env->llFirstPTS
			 && env->stream[j].lastPTS > env->stream[j].firstPTS  )
		{
			env->llFirstPTS = env->stream[j].firstPTS;
			rtDur = GetStreamLastPTS( env, j );
		}
	}
	return rtDur;
}



static unsigned long GetVideoRate( unsigned short uStreamID, unsigned short uStreamType, AV_CONTEXT *pAV )
{
	unsigned long lVideoRate = 0;
	if ( uStreamType == 0xf1 ) //customized DixV stream
	{
		_MEDIA* pMsftMedia;
		pMsftMedia = &pAV->MsftMedia;
		lVideoRate = pMsftMedia->Video.dwBitRate / 8; // it's in bits/second
	} else
	if ( pAV->VideoType == MPEG1_VIDEO )
	{
		SEQHDR*    pMpegInfo;
		pMpegInfo  = &pAV->Mpeg2Hdr.SeqHdr;
		lVideoRate = pMpegInfo->BitRate/8; // it's in bits/second
	} else
	if ( pAV->VideoType == MPEG2_VIDEO )
	{
		MPEG2_SEQHDR* pMpegInfo;
		pMpegInfo = &pAV->Mpeg2Hdr;
		lVideoRate = pMpegInfo->SeqHdr.BitRate/8;
	}else
	if ( pAV->VideoType == H264_VIDEO )
	{
		H264_PARAM *pH264Param;
		pH264Param = &pAV->H264Param;
		lVideoRate = pH264Param->cbr/8;

	}


	return lVideoRate;
}

static unsigned long GetAudioRate( unsigned short uStreamID, unsigned short uStreamType, AV_CONTEXT *pAV )
{
	unsigned long lAudioRate = 0;
	if ( uStreamType == 0x3 || uStreamType == 0x4 ) 
	{
		TS_MPEGWAV*   pWfx;	
		pWfx = &pAV->MpegWav;
		lAudioRate = pWfx->wfx.nAvgBytesPerSec;
	} else
	if ( uStreamType == 0x81 || uStreamType == 0x06 ) //AC3 stream
	{
		TS_AC3WAV* pAC3Wav;
		pAC3Wav = &pAV->AC3Wav;
		lAudioRate = pAC3Wav->wfx.nAvgBytesPerSec;
	} 
	return lAudioRate;
}

unsigned long GetStreamTotalRate( ENV* env )
{
	int i;
	unsigned long TotalRate = 0;
	for ( i = 0; i<env->stream_num && i < MAX_STREAM_NUM ; i++ )
		if ( env->stream[i].stream_ready  )
		{
			if ( IsAudioStreamId( env->stream[i].stream_id ) ) 
			{
				TotalRate += GetAudioRate( env->stream[i].stream_id, 
					                       env->stream[i].stream_type, 
										   &env->stream[i].av );
			}
			if ( IsVideoStreamId( env->stream[i].stream_id ) || IsVC1StreamId( env->stream[i].stream_id ) ) 
			{
				TotalRate += GetVideoRate( env->stream[i].stream_id, 
					                       env->stream[i].stream_type, 
										   &env->stream[i].av );
				
			}
			if ( env->stream[i].stream_id == PRIVATE_STREAM_1 || env->stream[i].stream_id == PRIVATE_STREAM_2 ) 
			{
				TotalRate += GetAudioRate( env->stream[i].stream_id, 
					                       env->stream[i].stream_type, 
										   &env->stream[i].av );
				
			}
		}
	return TotalRate;
}


char* BuildFormatDesc( ENV* env, char* buf, int size )
{
	char tmp[512]={0};
	char stream_tag[MAX_STREAM_NUM][60]={0};

	int video_type = 0;
	unsigned br= 0;
	AV_CONTEXT *av;
	int i;

	if ( size < 20 ) return "";

	buf[0] = tmp[0] = 0;
	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{
		if ( IsVideoType( env->stream[i].stream_type ) )
		{
			video_type = env->stream[i].stream_type;
			break;
		}
	}
	br = GetStreamTotalRate( env ) * 8;

	//Hauppauge HDPVR H.264 gives a wrong video bps, confuses player, I use avg bps for it, remove it after Hauppauge fixing  //ZQ
	if ( br == 20000000 && video_type == 0x1b)
	  br = env->avg_byte_rate * 8; 

	// Narflex-04/29/09
	// Only use valid container format names here (we removed UNKNOWN, DIVX, AUDIO)
	if (video_type == 1)
		sprintf( buf, "AV-INF|f=MPEG1-PS;br=%d;", br );
	else
		sprintf( buf, "AV-INF|f=MPEG2-%s;br=%d;", env->uInputStreamFormat == TS_FORMAT ? "TS":"PS", br );

	if ( env->uInputStreamFormat == TS_FORMAT )
		DectTSMainAudioPid( env, env->selected_channel );
	else
		DectPSMainAudioTrk( env );

	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{
		char *main_audio, *main_video;
		char lauguage[16];
		unsigned long lauguage_code;

		if ( !env->stream[i].stream_ready )
			continue;

		lauguage[0] = 0;
		if ( env->uInputStreamFormat == TS_FORMAT )
		{
			if ( env->audio_main_track < 0 ) main_audio = ""; else
				main_audio = env->audio_main_track == env->stream[i].pid  ? "main=yes;" : "main=no;";
			if ( env->video_main_track < 0 ) main_video = ""; else
				main_video = env->video_main_track == env->stream[i].pid  ? "main=yes;" : "main=no;";
			lauguage_code= GetLauguage( env->stream[i].stream_desc );	
			if ( lauguage_code ) 
			{   char laug[4];
				laug[0] = (char)(lauguage_code >> 24); laug[1] = (char)(lauguage_code >> 16);
				laug[2] = (char)(lauguage_code >> 8) ; laug[3] = 0;
				sprintf( lauguage, ";lang=%s", laug );
			}

		} else
		{
			if ( env->audio_main_track < 0 ) main_audio = ""; else
				main_audio = env->audio_main_track == i  ? "main=yes;" : "main=no;";
			if ( env->video_main_track < 0 ) main_video = ""; else
				main_video = env->video_main_track == i  ? "main=yes;" : "main=no;";
		}

		av = &env->stream[i].av;
		switch ( env->stream[i].stream_type ) 
		{
			case 1: 
			case 2:
			case 0x1b:
			case 0x10:
			case 0xea:
				if ( env->uInputStreamFormat != TS_FORMAT )
				{
					sprintf( stream_tag[i], "index=%d;%stag=%02x", i, main_video,
						     av->VideoPES.StreamId );
				} else
				{
					sprintf( stream_tag[i], "index=%d;%stag=%04x", i, main_video, 
						     env->stream[i].pid  );
				}
				break;
			case 0xf1:
				sprintf( stream_tag[i], "index=%d;tag=dixv", i  );
				break;
			case 0x11:
			case 0x0f:
			case 3:
			case 4:
				if ( env->uInputStreamFormat != TS_FORMAT )
				{
					sprintf( stream_tag[i], "index=%d;%stag=%02x%s", i, main_audio,
						               av->AudioPES.StreamId, lauguage );
				} else
				{
					sprintf( stream_tag[i], "index=%d;%stag=%04x%s", i, main_audio,
						     env->stream[i].pid, lauguage );
				}
				break;
			case 6:
			case 0x81:
			case 0x82:
				if ( env->uInputStreamFormat != TS_FORMAT )
				{
					if ( IsPrivateStreamId(av->AudioPES.StreamId)  )
						sprintf( stream_tag[i], "index=%d;%stag=%02x-%02x%02x%02x%02x%s", i, main_audio,
								  av->AudioPES.StreamId,
								  av->PrivateHeader[0], av->PrivateHeader[1], av->PrivateHeader[2], av->PrivateHeader[3],
								  lauguage );
					else
						sprintf( stream_tag[i], "index=%d;%stag=%02x%s", i, main_audio,
						          av->AudioPES.StreamId, lauguage );
				} else
				{
					sprintf( stream_tag[i], "index=%d;%stag=%04x%s", i, main_audio,
					          env->stream[i].pid, lauguage );

				}
				break;
			default:
				if ( env->uInputStreamFormat != TS_FORMAT )
				{
					sprintf( stream_tag[i], "index=%d;[unknown type]", i );
				} else
				{
					sprintf( stream_tag[i], "index=%d;pid=%d;[unknown type]", i, env->stream[i].pid );
				}
		}
	}

	for ( i = 0; i<MAX_STREAM_NUM; i++ )
	{
		if ( !env->stream[i].stream_ready )
			continue;

		av = &env->stream[i].av;
		switch ( env->stream[i].stream_type ) 
		{
		case 1: 
			sprintf( tmp, "[bf=vid;f=MPEG1-Video;fps=%f;fpsn=%d;fpsd=%d;ar=%f;w=%ld;h=%ld;%s];", 
						  (float)av->Mpeg2Hdr.SeqHdr.FrameRateNomi/av->Mpeg2Hdr.SeqHdr.FrameRateDeno,
						  av->Mpeg2Hdr.SeqHdr.FrameRateNomi, 
						  av->Mpeg2Hdr.SeqHdr.FrameRateDeno, 
						  Mepg1AspectRatio( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0x0f) ),
						  av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height,stream_tag[i] );

			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
				strcat( buf, tmp );
			break;
		case 0x80:   //digiCipher video
		case 2:
			sprintf( tmp, "[bf=vid;f=MPEG2-Video;fps=%f;fpsn=%d;fpsd=%d;ar=%f;arn=%d;ard=%d;w=%ld;h=%ld;lace=%d;%s];", 
						  (float)av->Mpeg2Hdr.SeqHdr.FrameRateNomi/av->Mpeg2Hdr.SeqHdr.FrameRateDeno,
						  av->Mpeg2Hdr.SeqHdr.FrameRateNomi, 
						  av->Mpeg2Hdr.SeqHdr.FrameRateDeno, 
						  Mepg2AspectRatio( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0x0f), av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height ),
						  Mepg2AspectRatioNomi( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0xf), av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height ),
						  Mepg2AspectRatioDeno( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0xf), av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height ),
						  av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height,
						  !av->Mpeg2Hdr.SeqHdr.progressive, stream_tag[i] );
			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
				strcat( buf, tmp );
			break;

		case 0x1b:
			sprintf( tmp, "[bf=vid;f=H.264;w=%d;h=%d;lace=%d;%s];", 
				     av->H264Param.width, av->H264Param.height, av->H264Param.progressive, stream_tag[i] );
			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
				strcat( buf, tmp );
			break;
		case 0x10:  //Dixv-video
			sprintf( tmp, "[bf=vid;f=MPEG4-Video;fps=%f;ar=%f;arn=%d;ard=%d;w=%ld;h=%ld;lace=%d;%s];", 
						  av->Mpeg2Hdr.SeqHdr.PictureRate, 
						  Mepg2AspectRatio( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0x0f), (av->Mpeg2Hdr.SeqHdr.arInfo>>8)&0xff, (av->Mpeg2Hdr.SeqHdr.arInfo>>16)&0xff ),
						  Mepg2AspectRatioNomi( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0xf), (av->Mpeg2Hdr.SeqHdr.arInfo>>8)&0xff, (av->Mpeg2Hdr.SeqHdr.arInfo>>16)&0xff ),
						  Mepg2AspectRatioDeno( (unsigned char)(av->Mpeg2Hdr.SeqHdr.arInfo & 0xf), (av->Mpeg2Hdr.SeqHdr.arInfo>>8)&0xff, (av->Mpeg2Hdr.SeqHdr.arInfo>>16)&0xff ),
						  av->Mpeg2Hdr.SeqHdr.Width, av->Mpeg2Hdr.SeqHdr.Height,
						  !av->Mpeg2Hdr.SeqHdr.progressive, stream_tag[i] );
			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
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
			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
				strcat( buf, tmp );
			break;
		case 0xf1:  //Divx-video
			sprintf( tmp, "[bf=vid;f=MPEG4-Video;fps=%f;w=%ld;h=%ld;%s];", 
						  1000000.0/av->MsftMedia.Video.AvgTimePerFrame,
						  av->MsftMedia.Video.bmiHeader.biWidth,
						  av->MsftMedia.Video.bmiHeader.biHeight,stream_tag[i] );
			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
				strcat( buf, tmp );
			break;
		case 3:
		case 4:
			sprintf( tmp, "[bf=aud;f=MP%d;sr=%ld;ch=%d;br=%ld;%s];", av->MpegWav.fwHeadLayer<3?2:3,
				av->MpegWav.wfx.nSamplesPerSec, av->MpegWav.wfx.nChannels, av->MpegWav.wfx.nAvgBytesPerSec*8, stream_tag[i] );
			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
				strcat( buf, tmp );
			break;
		case 0x0f:
			sprintf( tmp, "[bf=aud;f=AAC;sr=%d;ch=%d;br=%d;at=%s%s;%s];", av->AACParam.sample_freq, av->AACParam.channel_num, 
				av->AACParam.bitrate, 
				av->AACParam.format==1?"ADTS":"LATM", av->AACParam.version==0?"":(av->AACParam.version==2)?"-MPEG2":"-MPEG4", 
				stream_tag[i] );
			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
				strcat( buf, tmp );
			break;

		case 0x11: //Norway
			sprintf( tmp, "[bf=aud;f=AAC-HE;sr=%d;ch=%d;br=%d;at=%s%s;%s];", av->AACParam.sample_freq, av->AACParam.channel_num, 
			               av->AACParam.bitrate, 
						   av->AACParam.format==1?"ADTS":"LATM", av->AACParam.version==0?"":(av->AACParam.version==2)?"-MPEG2":"-MPEG4",
						   stream_tag[i] );
			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
				strcat( buf, tmp );
			break;			
		case 0x06:
		case 0x81:
			if ( av->AudioType == AC3_AUDIO ) 
			{
				sprintf( tmp, "[bf=aud;f=AC3;sr=%ld;ch=%d;br=%ld;%s];", 
						av->AC3Wav.wfx.nSamplesPerSec, av->AC3Wav.wfx.nChannels, av->AC3Wav.wfx.nAvgBytesPerSec*8, 
						stream_tag[i] ); //nAvgBytesPerSec is bit_rate*8 for AC3
			} else
			if ( av->AudioType == LPCM_AUDIO ) 
			{
				sprintf( tmp, "[bf=aud;f=LPCM;sr=%ld;ch=%d;br=%ld;%s];", 
						av->LPCMWav.wfx.nSamplesPerSec, av->LPCMWav.wfx.nChannels, av->LPCMWav.wfx.nAvgBytesPerSec*8, stream_tag[i] ); //nAvgBytesPerSec is bit_rate*8 for AC3
			} else
			if ( av->AudioType == MPEG_AUDIO ) 
			{
				sprintf( tmp, "[bf=aud;f=MP%d;sr=%ld;ch=%d;%s];", av->MpegWav.fwHeadLayer<3 ? 2:3,
							av->MpegWav.wfx.nSamplesPerSec, av->MpegWav.wfx.nChannels,stream_tag[i] );
			} else
				tmp[0] = 0x0;
			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
				strcat( buf, tmp );
			
			break;
		case 0x82:
			if ( av->AudioType == DTS_AUDIO ) 
			{
				sprintf( tmp, "[bf=aud;f=DTS;sr=%ld;ch=%d;br=%ld;%s];", 
						av->DTSWav.wfx.nSamplesPerSec, av->DTSWav.wfx.nChannels, av->DTSWav.wfx.nAvgBytesPerSec, 
						stream_tag[i] ); 
			}
			if ( strlen( buf) + strlen( tmp ) < (unsigned int)size )
				strcat( buf, tmp );
		case TELTEXT_TYPE:
			printf( "Teltext stream\n" );
			break;
		default:
			break;
		}
	}
	return buf;
}

char* BuildDurationDesc( LONGLONG llDur, char* buf, int size )
{
	char tmp[64];
	PTS2HexString( llDur, tmp, sizeof( tmp ) );
	strncpy( buf, tmp, size );
	return buf;
}



int DectPSMainAudioTrk( ENV* env )
{
	int i;
	int video_track, audio_track;
	bool found_sage_info;
	found_sage_info = GetMainTrack( env->parser, &video_track, &audio_track ) > 0;
	if ( found_sage_info )
	{
		env->sage_inf_flag = true;
		if ( video_track >= 0  )
			env->video_main_track = video_track;

		if ( audio_track >= 0  )
			env->audio_main_track = audio_track;
	} else
	{
		AV_CONTEXT *av;
		int priority, first_prior = 10;
		env->audio_main_track = -1;
		for ( i = 0; i<MAX_STREAM_NUM; i++ )
		{
			if ( !env->stream[i].stream_ready )
				continue;

			if ( env->stream[i].stream_type != 3 && env->stream[i].stream_type != 4 &&   
				 env->stream[i].stream_type != 0x81  ) 
				 continue;

			av = &env->stream[i].av;
			if ( IsPrivateStreamId( av->AudioPES.StreamId ) )
			{
				if ( av->AudioType == AC3_AUDIO )
					priority = 1;
				else
				if ( av->AudioType == DTS_AUDIO )
					priority = 2;
				else								
					priority = 8;
			} else
			if ( av->AudioPES.StreamId >= 0xc0 )
			{
				priority = 0x3f & av->AudioPES.StreamId;
			} else
				priority = 100;

			if ( first_prior > priority )
			{
				first_prior = priority;
				env->audio_main_track = i;
			}
		}
	}
	return	env->audio_main_track;
}


int DectTSMainAudioPid( ENV* env, int channel )
{
	int i;
	int stream_num;
	AVSTREAM_INFO* StreamInfo;
	unsigned long lauguagecode;
	stream_num = GetStreamNum( env->parser, channel );
	if ( stream_num <= 0 ) return 0;
	if ( stream_num >  MAX_STREAM_NUM ) stream_num = MAX_STREAM_NUM;
	StreamInfo = (AVSTREAM_INFO*)sagetv_malloc( sizeof(AVSTREAM_INFO)*(stream_num) );
	if ( StreamInfo == NULL ) return 0;
	stream_num = GetStreamList( env->parser, channel, StreamInfo, stream_num );

printf( "Channel:%d streams: ( PMT pid:0x%04x, Program:0x%04x )\n", channel, 
	StreamInfo[0].pid, StreamInfo[0].programID  );
for ( i = 1; i< stream_num; i++ )
	printf( "\tType:%x Pid:0x%03x\n", StreamInfo[i].streamType, StreamInfo[i].pid );

	{
		int priority, first_prior;
		//select first prior  audio sound track
		first_prior = priority = 100; 
		for ( i = 1; i< stream_num; i++ )
		{
			if ( IsAudioType( StreamInfo[i].streamType ) )
			{
				if ( StreamInfo[i].streamType == 0x81 )
					priority = 10;
				else 
				if ( StreamInfo[i].streamType == 0x0f ) //AAC
					priority = 40;
				else 
				if ( StreamInfo[i].streamType == 0x11 ) //AAC/MPEG4-AUDIO
					priority = 40;
				else 
				if ( StreamInfo[i].streamType == 0x03 )
						priority = 20;
				else
				if ( StreamInfo[i].streamType == 0x04 )
					priority = 30;
				else
				if ( StreamInfo[i].streamType == 0x06 )
				{
					AV_CONTEXT *av=NULL;
					int k, guess_type;
					
					guess_type = GuessAuidoType( StreamInfo[i].Desc );
					if ( guess_type < 0 ) 
					{
						priority = 100;
					}
					
					for ( k = 0; k<MAX_STREAM_NUM; k++ )
					{
						if ( env->stream[k].pid == StreamInfo[i].pid )
						{
							av = &env->stream[k].av;
							break;
						}
					}

					if ( av == NULL ) priority = 100;
					else
					if ( av->AudioType == AC3_AUDIO ) 
						priority = 10;
					else
					if ( av->AudioType == DTS_AUDIO ) 
						priority = 20;
					else
					if ( av->AudioType == AAC_AUDIO ) 
						priority = 40;
					else
					if ( av->AudioType == MPEG_AUDIO) 
						priority = 20;
					else
					if ( av->AudioType == LPCM_AUDIO ) 
						priority = 30;
					else
					if ( av->AudioType == 0 )
						priority = 100;
					else
						priority = 40;
					
				}
				else
					priority = 50;
				
				lauguagecode= GetLauguage( StreamInfo[i].Desc );

				if ( lauguagecode== 0x656e6700 && !IsDVBStream( env->parser ) ) //pick up "english" fisrt for ATSC
					priority -= 5;


				if ( first_prior > priority )
				{
					if ( PidHits( env->parser, StreamInfo[i].pid ) == 0  )
					{
						//missing audio stream but have desc in PMT 
						if ( GetPacketCounter( env->parser ) < 800 ) 
						{
							printf( "Warning: need more packet to decide audio main track\n" );
						}

					} else
					{
						first_prior = priority;
						env->audio_main_track = StreamInfo[i].pid;
					}
				} else
				if ( first_prior == priority )
				{ //if the same priority, pick up packet rate higher one
					unsigned long rate1, rate2;
					rate1 = PidHits( env->parser, StreamInfo[i].pid );
					rate2 = PidHits( env->parser, env->audio_main_track );
					if ( rate1 > rate2  )
						env->audio_main_track = StreamInfo[i].pid;
				}
			}
		}
	}
	sagetv_free( StreamInfo );
	return env->audio_main_track;
}

LONGLONG GetPCRTimePerFrame( AV_CONTEXT *av )
{
	if ( av->VideoType != MPEG2_VIDEO && av->VideoType != MPEG1_VIDEO  ) 
		return 0;
	return (LONGLONG)27000000 * av->Mpeg2Hdr.SeqHdr.FrameRateNomi/av->Mpeg2Hdr.SeqHdr.FrameRateDeno;
}

