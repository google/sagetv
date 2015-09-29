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
#include "TSParser.h"
#include "AVAnalyzer.h"
#include "ESAnalyzer.h"
#include "AVTrack.h"

TRACKS *CreateTracks( int nTrackNum )
{
	int i;
	TRACKS *pTracks;
	pTracks = SAGETV_MALLOC( sizeof(TRACKS) );
	pTracks->track = SAGETV_MALLOC( sizeof(TRACK)* nTrackNum );
	pTracks->total_track = nTrackNum;
	for ( i = 0; i <nTrackNum; i++ )
	{
		pTracks->track[i].channel_index = i;
	}
	pTracks->main_video_index = 0xffff;
	pTracks->main_audio_index = 0xffff;
	return pTracks;
}

void ResetTracks( TRACKS* pTracks )
{
	int i;
	pTracks->main_video_index = 0xffff;
	pTracks->main_audio_index = 0xffff;
	pTracks->track_attr = 0;
	pTracks->number_track = 0;
	for ( i = 0; i <pTracks->total_track; i++ )
	{
		pTracks->track[i].channel_index = i;
		pTracks->track[i].buffer_index = 0;
		pTracks->track[i].buffer_size = 0;
		pTracks->track[i].buffer_start = NULL;
		pTracks->track[i].cue = 0;
		pTracks->track[i].start_cue = 0;
		pTracks->track[i].es_data_bytes = 0;
		pTracks->track[i].ts_packets_counter = 0;
		pTracks->track[i].es_blocks_counter = 0;
		pTracks->track[i].processed_bytes = 0;
		pTracks->track[i].scrambling_flag = 0;
		if ( pTracks->track[i].ts_elmnt )
			memset( pTracks->track[i].ts_elmnt, 0, sizeof(TS_ELEMENT) );
		if ( pTracks->track[i].es_elmnt )
			memset( pTracks->track[i].es_elmnt, 0, sizeof(ES_ELEMENT) );
		if ( pTracks->track[i].av_elmnt )
			memset( pTracks->track[i].av_elmnt, 0, sizeof(AV_ELEMENT) );
	}
}

void ReleaseTracks( TRACKS* pTracks )
{
	SAGETV_FREE( pTracks->track );
	SAGETV_FREE( pTracks );
}


void CheckTracksAttr( TRACKS* pTracks, uint32_t LanguageCode )
{
	int i;
	if ( IS_TS_TYPE( pTracks->track_type ) )
	{
		int video_track_num = 0, audio_track_num = 0;
		int video_scrambling_track_num = 0, audio_scrambling_track_num = 0;

		//count totoal track
		for ( i = 0; i<pTracks->total_track; i++ )
		{
			if ( pTracks->track[i].ts_elmnt == NULL )
				break;
		}
		pTracks->number_track = i;

		if ( pTracks->main_video_index == 0xffff )
		{
			//select main video and check tracks attribute
			for ( i = 0; i<pTracks->number_track; i++ )
			{
				if ( pTracks->track[i].ts_elmnt->content_type == VIDEO_DATA )
				{
					if ( pTracks->main_video_index == 0xffff )
						pTracks->main_video_index = i;
					else
					{
						if ( pTracks->track[i].ts_packets_counter > 
							 pTracks->track[ pTracks->main_video_index ].ts_packets_counter )
							pTracks->main_video_index = i;
					}
					if ( pTracks->track[i].scrambling_flag ) video_scrambling_track_num++;
					video_track_num++;
					if ( pTracks->track[i].es_elmnt->format_fourcc != 0 )
						pTracks->track_attr |= ATTR_VIDEO_PRESENT;
	
				}
			}
		}

		if ( pTracks->main_audio_index == 0xffff )
		{
			int weights[64]={0};
			//select main audio and check tracks attribute
			for ( i = 0; i<pTracks->number_track && i<64; i++ )
			{
				//skip no data stream;
				if ( pTracks->track[i].av_elmnt->content_type == 0 ) continue;
				if ( pTracks->track[i].ts_elmnt->content_type == AUDIO_DATA && pTracks->track[i].ts_packets_counter > 0 )
				{
					weights[i] = 0;
					if ( LanguageCode )
						weights[i] =  pTracks->track[i].ts_elmnt->language_code == LanguageCode ? 10000 :
								  pTracks->track[i].ts_elmnt->language_code == 0 ? 10000 : 0;
					weights[i] += pTracks->track[i].ts_elmnt->audio_type == 0 ? 9000 :   /* normal */
						          pTracks->track[i].ts_elmnt->audio_type == 3 ? 8000 :   /* silent */
								  pTracks->track[i].ts_elmnt->audio_type == 2 ? 7000 :    /* impaired */
								  pTracks->track[i].ts_elmnt->audio_type == 1 ? 6000 : 0; /* commentary */
					weights[i] += GetAudioSoundChannelNum( pTracks->track[i].av_elmnt ) * 100;
					weights[i] += GetAudioSoundBitRate( pTracks->track[i].av_elmnt ) / 10000; /* range 1-100 */

					//skip LPCM because parser isn't ready
					if ( pTracks->track[i].ts_elmnt->format_fourcc == SAGE_FOURCC( "LPCM" ) )
						weights[i] = -20000;

					if ( pTracks->main_audio_index == 0xffff )
						pTracks->main_audio_index = i;
					else
					{

						if ( weights[i] > weights[ pTracks->main_audio_index ] ) 
						{
							pTracks->main_audio_index = i;
						} else
						if ( weights[i] == weights[ pTracks->main_audio_index ] ) 
						{
							if ( pTracks->track[i].ts_packets_counter> 
								 pTracks->track[ pTracks->main_audio_index ].ts_packets_counter+3 )
								pTracks->main_audio_index = i;
						}
					}
					if ( pTracks->track[i].scrambling_flag ) audio_scrambling_track_num++;
					audio_track_num++;
					if ( pTracks->track[i].es_elmnt->format_fourcc != 0 )
						pTracks->track_attr |= ATTR_AUDIO_PRESENT;

				}
			}
		}

		if ( video_track_num && 
			!(pTracks->track_attr & ATTR_VIDEO_PRESENT) && 	video_scrambling_track_num )
			pTracks->track_attr |= ATTR_ENCRYPTED_FLAG ;

		if ( audio_track_num && 
			!(pTracks->track_attr & ATTR_AUDIO_PRESENT) && 	
			 audio_scrambling_track_num == audio_track_num )
			pTracks->track_attr |= ATTR_ENCRYPTED_FLAG ;

		//return ( pTracks->main_video_index != 0xffff || pTracks->main_audio_index != 0xffff );

	} else
	if ( IS_PS_TYPE ( pTracks->track_type ) )
	{
		if ( pTracks->main_video_index == 0xffff )
		{
			//select main video
			for ( i = 0; i<pTracks->number_track; i++ )
			{
				if ( pTracks->track[i].av_elmnt->content_type == VIDEO_DATA )
				{
					if ( pTracks->main_video_index == 0xffff )
						pTracks->main_video_index = i;
					else
					{
						if ( pTracks->track[i].es_blocks_counter > 
							 pTracks->track[ pTracks->main_video_index ].es_blocks_counter )
							pTracks->main_video_index = i;
					}
				}
			}
		} 

		if ( pTracks->main_audio_index == 0xffff )
		{
			//select main audio
			int weights[64]={0};
			for ( i = 0; i<pTracks->number_track && i <64; i++ )
			{
				if ( pTracks->track[i].av_elmnt->content_type == AUDIO_DATA  )
				{
					weights[i] = 0;
					if ( LanguageCode )
						weights[i] =  pTracks->track[i].es_elmnt->language_code == LanguageCode ? 10000 : 
								  pTracks->track[i].es_elmnt->language_code == 0 ? 10000 : 0;
					weights[i] += pTracks->track[i].es_elmnt->audio_type == 0 ? 9000 :    /* normal */
						          pTracks->track[i].es_elmnt->audio_type == 3 ? 8000 :    /* silent */
								  pTracks->track[i].es_elmnt->audio_type == 2 ? 7000 :    /* impaired */
								  pTracks->track[i].es_elmnt->audio_type == 1 ? 6000 : 0; /* commentary */
					weights[i] += GetAudioSoundChannelNum( pTracks->track[i].av_elmnt ) * 100;
					weights[i] += GetAudioSoundBitRate( pTracks->track[i].av_elmnt ) / 10000; /* range 1-100 */
					//skip LPCM because parser isn't ready
					if ( pTracks->track[i].es_elmnt->format_fourcc == SAGE_FOURCC( "LPCM" ) )
					{
						weights[i] = -20000;
					}

					if ( pTracks->main_audio_index == 0xffff )
						pTracks->main_audio_index = i;
					else
					{
						if ( weights[i] > weights[ pTracks->main_audio_index ] ) 
						{
							pTracks->main_audio_index = i;
						} else
						if ( weights[i] == weights[ pTracks->main_audio_index ] ) 
						{
							if ( pTracks->track[i].es_blocks_counter  > 
								 pTracks->track[ pTracks->main_audio_index ].es_blocks_counter  )
								pTracks->main_audio_index = i;
						}
					}
				}
			}
		}

		//check video audio present
		for ( i = 0; i<pTracks->number_track && i <64; i++ )
		{
			if ( pTracks->track[i].es_elmnt->format_fourcc != 0 )
			{
				if ( pTracks->track[i].av_elmnt->content_type == AUDIO_DATA  )
					pTracks->track_attr |= ATTR_AUDIO_PRESENT;
				else
				if ( pTracks->track[i].av_elmnt->content_type == VIDEO_DATA )
					pTracks->track_attr |= ATTR_VIDEO_PRESENT;

			}
		}

		//return ( pTracks->main_video_index != 0xffff || pTracks->main_audio_index != 0xffff );
	}

	return ;

}

static int MpegVideoInf( MPEG_VIDEO *pMpegVideo, char* pBuffer, int nSize )
{
	char* p = pBuffer;
	int pos = 0;
	if ( nSize <= 0 ) return 0;
	if ( pMpegVideo->extension_present )
	{
		char* chrome_format="";
		if ( pMpegVideo->chrome == 1  ) chrome_format = "cs=yuv420p;"; else
		if ( pMpegVideo->chrome == 2  ) chrome_format = "cs=yuv422p;"; else
		if ( pMpegVideo->chrome == 3  ) chrome_format = "cs=yuv444p;"; 

		pos += snprintf( p+pos, nSize-pos, "fps=%f;fpsn=%d;fpsd=%d;ar=%f;arn=%d;ard=%d;w=%d;h=%d;lace=%d;%s",
			              (float)pMpegVideo->frame_rate_nomi/pMpegVideo->frame_rate_deno,
						  pMpegVideo->frame_rate_nomi, 
						  pMpegVideo->frame_rate_deno,
						  Mepg2AspectRatioF( (uint8_t)(pMpegVideo->ar_info & 0x0f), pMpegVideo->width, pMpegVideo->height ),
						  Mepg2AspectRatioNomiValue( (uint8_t)(pMpegVideo->ar_info & 0x0f), pMpegVideo->width, pMpegVideo->height ),
						  Mepg2AspectRatioDenoValue( (uint8_t)(pMpegVideo->ar_info & 0x0f), pMpegVideo->width, pMpegVideo->height ),
						  pMpegVideo->width, 
						  pMpegVideo->height,
						  !pMpegVideo->progressive,
						  chrome_format );

	} else
	{
		pos += snprintf( p+pos, nSize-pos, "fps=%f;fpsn=%d;fpsd=%d;ar=%f;w=%d;h=%d;",
			              (float)pMpegVideo->frame_rate_nomi/pMpegVideo->frame_rate_deno,
						  pMpegVideo->frame_rate_nomi, 
						  pMpegVideo->frame_rate_deno,
						  Mepg1AspectRatioF( (uint8_t)(pMpegVideo->ar_info & 0x0f), pMpegVideo->width, pMpegVideo->height ),
						  pMpegVideo->width, pMpegVideo->height );
	}
	return pos;
}

static int H264VideoInf( H264_VIDEO *pH264Video, char* pBuffer, int nSize )
{
	char* p = pBuffer;
	int pos = 0;
	if ( nSize <= 0 ) return 0;

	pos += snprintf( p+pos, nSize-pos, "fps=%f;fpsn=%d;fpsd=%d;ar=%f;arn=%d;ard=%d;w=%d;h=%d;lace=%d;",
					 (float)pH264Video->frame_rate_nomi/pH264Video->frame_rate_deno,
					  (int)pH264Video->frame_rate_nomi,(int)pH264Video->frame_rate_deno,
					  H264AspectRatioF( pH264Video->ar_nomi, pH264Video->ar_deno,  pH264Video->width, pH264Video->height ),
					  H264AspectRatioNomiValue( pH264Video->ar_nomi, pH264Video->ar_deno,  pH264Video->width, pH264Video->height ),
					  H264AspectRatioDenoValue( pH264Video->ar_nomi, pH264Video->ar_deno,  pH264Video->width, pH264Video->height ),
					  pH264Video->width, pH264Video->height, pH264Video->progressive );

	return pos;
}


static int AC3AudioInf( AV_ELEMENT *pAVElmnt, char* pBuffer, int nSize )
{
	char* p = pBuffer;
	int pos = 0;
	if ( nSize <= 0 ) return 0;
	pos += snprintf( p+pos, nSize-pos, "sr=%d;ch=%d;br=%d;", pAVElmnt->d.a.ac3.samples_per_sec, 
		             pAVElmnt->d.a.ac3.channels, pAVElmnt->d.a.ac3.avgbytes_per_sec );
	return pos;
}
static int DTSAudioInf( AV_ELEMENT *pAVElmnt, char* pBuffer, int nSize )
{
	char* p = pBuffer;
	int pos = 0;
	if ( nSize <= 0 ) return 0;
	pos += snprintf( p+pos, nSize-pos, "sr=%d;ch=%d;br=%d;", pAVElmnt->d.a.dts.samples_per_sec, 
		             pAVElmnt->d.a.dts.channels, pAVElmnt->d.a.dts.avgbytes_per_sec );
	return pos;
}
static int AACAudioInf( AV_ELEMENT *pAVElmnt, char* pBuffer, int nSize )
{
	char* p = pBuffer;
	int pos = 0;
	if ( nSize <= 0 ) return 0;
	pos += snprintf( p+pos, nSize-pos, "sr=%d;ch=%d;br=%d;at=%s%s;", pAVElmnt->d.a.aac.samples_per_sec, 
		             pAVElmnt->d.a.aac.channels, pAVElmnt->d.a.aac.avgbytes_per_sec,
					 pAVElmnt->d.a.aac.format==1?"ADTS":"LATM", 
					 pAVElmnt->d.a.aac.version==0?"":(pAVElmnt->d.a.aac.version==2)?"-MPEG2":"-MPEG4" );
	return pos;
}
static int AACHEAudioInf( AV_ELEMENT *pAVElmnt, char* pBuffer, int nSize )
{
	char* p = pBuffer;
	int pos = 0;
	if ( nSize <= 0 ) return 0;
	pos += snprintf( p+pos, nSize-pos, "sr=%d;ch=%d;br=%d;at=%s%s;", pAVElmnt->d.a.aac.samples_per_sec, 
		             pAVElmnt->d.a.aac.channels, pAVElmnt->d.a.aac.avgbytes_per_sec,
					 pAVElmnt->d.a.aac.format==1?"ADTS":"LATM", 
					 pAVElmnt->d.a.aac.version==0?"":(pAVElmnt->d.a.aac.version==2)?"-MPEG2":"-MPEG4" );
	return pos;
}
static int MPEGAudioInf( AV_ELEMENT *pAVElmnt, char* pBuffer, int nSize )
{
	char* p = pBuffer;
	int pos = 0;
	if ( nSize <= 0 ) return 0;
	pos += snprintf( p+pos, nSize-pos, "sr=%d;ch=%d;br=%d;", pAVElmnt->d.a.mpeg_audio.samples_per_sec, 
		             pAVElmnt->d.a.mpeg_audio.channels, pAVElmnt->d.a.mpeg_audio.avgbytes_per_sec*8 );
	return pos;
}
static int LPCMAudioInf( AV_ELEMENT *pAVElmnt, char* pBuffer, int nSize )
{
	char* p = pBuffer;
	int pos = 0;
	if ( nSize <= 0 ) return 0;
	pos += snprintf( p+pos, nSize-pos, "sr=%d;ch=%d;br=%d,bits=%d;src=%d;", pAVElmnt->d.a.lpcm.samples_per_sec, 
					pAVElmnt->d.a.lpcm.channels, pAVElmnt->d.a.lpcm.avgbytes_per_sec, pAVElmnt->d.a.lpcm.bits_per_sample, pAVElmnt->d.a.lpcm.lpcm_source );
	return pos;
}

static int DVBSubtitleInf( AV_ELEMENT *pAVElmnt, char* pBuffer, int nSize )
{
	char* p = pBuffer;
	int pos = 0;
	if ( nSize <= 0 ) return 0;
	pos += snprintf( p+pos, nSize-pos, "cpgid=%d;apgid=%d;", pAVElmnt->d.s.sub.cpgid, pAVElmnt->d.s.sub.apgid );
	return pos;
}

int  AVElmntInfo( AV_ELEMENT *pAVElmnt, char* pBuffer, int nSize )
{
	char* p = pBuffer;
	int pos = 0;
	if ( nSize <= 0 ) return 0;

	if ( pAVElmnt->content_type == VIDEO_DATA )
	{
		pos += snprintf( p+pos, nSize-pos, "bf=vid;" );
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "MP1V" ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=MPEG1-Video;" );
			pos += MpegVideoInf( &pAVElmnt->d.v.mpeg_video, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "MPGV" ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=MPEG2-Video;" );
			pos += MpegVideoInf( &pAVElmnt->d.v.mpeg_video, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "H264" ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=H.264;" );
			pos += H264VideoInf( &pAVElmnt->d.v.h264, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "VC1 " ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=VC1;" );
			pos += MpegVideoInf( &pAVElmnt->d.v.mpeg_video, p+pos, nSize-pos );
		}
		
	} else
	if ( pAVElmnt->content_type == AUDIO_DATA )
	{
		pos += snprintf( p+pos, nSize-pos, "bf=aud;" );
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "AC3 " ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=AC3;" );
			pos += AC3AudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "AC3T" ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=DolbyTrueHD;" );
			pos += AC3AudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "AC3E" ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=EAC3;" );
			pos += AC3AudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "DTS " ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=DTS;" );
			pos += DTSAudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "DTSM" ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=DTS-MA;" );
			pos += DTSAudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "DTSH" ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=DTS-HD;" );
			pos += DTSAudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "AAC " ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=AAC;" );
			pos += AACAudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "AACH" ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=AAC-HE;" );
			pos += AACHEAudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "MPGA" ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=MP%d;", pAVElmnt->d.a.mpeg_audio.head_layer<3?2:3  );
			pos += MPEGAudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "AUD " ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=MP2;" );
			pos += MPEGAudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "LPCM " ) && nSize-pos > 0 )	
		{
			if ( pAVElmnt->d.a.lpcm.lpcm_source == 3 )
				pos += snprintf( p+pos, nSize-pos, "f=PCM_BD;" );
			else
				pos += snprintf( p+pos, nSize-pos, "f=PCM;" );
			pos += LPCMAudioInf( pAVElmnt, p+pos, nSize-pos );
		} else
		if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "MP4A" ) && nSize-pos > 0 )	
		{
			pos += snprintf( p+pos, nSize-pos, "f=MP4A;" );
		} else
		{
		}

	} else
	if ( pAVElmnt->content_type == SUBTITLE_DATA )
	{
		pos += snprintf( p+pos, nSize-pos, "bf=sub;" );
		if ( pAVElmnt->d.s.sub.type == 1 )
		{
			pos += snprintf( p+pos, nSize-pos, "f=dvbsub;" );
			pos += DVBSubtitleInf(pAVElmnt, p+pos, nSize-pos );
		} else
		{
			pos += snprintf( p+pos, nSize-pos, "f=pgssub;" );
		}
	} else
	if ( pAVElmnt->content_type == TELETEXT_DATA )
	{
		pos += snprintf( p+pos, nSize-pos, "bf=tex;" );
	} else
	if ( pAVElmnt->content_type == PRIVAITE_DATA )
	{
		pos += snprintf( p+pos, nSize-pos, "bf=prv;" );
	} else
	{
		return 0;
	}

	return pos;

}

static uint32_t GetVideoRate( AV_ELEMENT *pAVElmnt  )
{

	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "MP1V" ) )	
	{
		return pAVElmnt->d.v.mpeg_video.bit_rate/8;
	} else
	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "MPGV" ) )	
	{
		return pAVElmnt->d.v.mpeg_video.bit_rate/8;
	} else
	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "H264" ) )	
	{
		return pAVElmnt->d.v.h264.cbr/8;
	} else
	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "VC1 " ) )	
	{
		return pAVElmnt->d.v.mpeg_video.bit_rate/8;
	}

	return 0;
}

uint32_t GetAudioRate( AV_ELEMENT *pAVElmnt )
{
	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "AAC " ) )
	{
		return pAVElmnt->d.a.aac.avgbytes_per_sec;
	} else
	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "AACH" ) )
	{
		return pAVElmnt->d.a.aac.avgbytes_per_sec;
	} else
	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "DTS " ) )
	{
		return pAVElmnt->d.a.dts.avgbytes_per_sec;
	} else
	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "MPG4 " ) )
	{

	} else
	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "AC3 " ) )
	{
		return pAVElmnt->d.a.ac3.avgbytes_per_sec;
	} else
	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "MPGA" ) )
	{
		return pAVElmnt->d.a.mpeg_audio.avgbytes_per_sec;
	} else
	if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "LPCM" ) )
	{
		return pAVElmnt->d.a.lpcm.avgbytes_per_sec;
	}
	return 0;
}

int TagInfo( int nOutputFormat, TS_ELEMENT *pTSElmnt, ES_ELEMENT *pESElmnt, char* pBuffer, int nSize )
{
	int i, pos = 0;
	char *p = pBuffer;
	 
	if ( IS_TS_TYPE(nOutputFormat) )
	{
		return snprintf( p+pos, nSize-pos,  "tag=%04x;pid=%d;", pTSElmnt->pid, pTSElmnt->pid );
	} else
	{
		if ( pESElmnt->private_bytes == 0 )
		{
			return snprintf( p+pos, nSize-pos,  "tag=%02x;", pESElmnt->pes.stream_id  );
		} else
		{
			pos += snprintf( p+pos, nSize-pos,  "tag=%02x-", pESElmnt->pes.stream_id  );
			for ( i = 0; i<pESElmnt->private_bytes; i++ )
			{	
				pos += snprintf( p+pos, nSize-pos,  "%02x", pESElmnt->private_data[i] );
			}
			if ( pos <nSize ) p[pos++] = ';';
			return pos;
		}
	}
	return pos;
}

char* AudioType( uint8_t type );
int TracksInfo( TRACKS* pTracks, char* pBuffer, int nSize )
{
	int i, pos = 0, video_num=0, audio_num=0;
	int h_264_present=0, video_data_present=0, audio_data_present=0;
	char *p = pBuffer;
	ES_ELEMENT *pESElmnt; 

	uint32_t bit_rate = 0;

	if ( IS_TS_TYPE( pTracks->track_type ) )
	{
		if ( !(pTracks->track_attr & (ATTR_VIDEO_PRESENT|ATTR_AUDIO_PRESENT)) )
		{
			if ( ( pTracks->track_attr & ATTR_ENCRYPTED_FLAG ) )
			{
				pos += snprintf( pBuffer, nSize-pos, "ENCRYPTED-TS;" );
				return pos;
			}
			pos += snprintf( pBuffer, nSize-pos, "NO-AV-TS;" );
			return pos;
		}

		if ( pTracks->main_video_index != 0xffff )
			pESElmnt = pTracks->track[pTracks->main_video_index].es_elmnt;
		else
		if ( pTracks->main_audio_index != 0xffff )
			pESElmnt = pTracks->track[pTracks->main_audio_index].es_elmnt;
		else
		{
			pos += snprintf( p+pos, nSize-pos, "Error: no main video and audio specified."  );
			return 0;  //no main track found;
		}
		if ( pESElmnt->es_type == ES_MPEG1 )
			pos += snprintf( pBuffer, nSize-pos, "MPEG1-TS;" );
		else
		if ( pESElmnt->es_type == ES_MPEG2 )
			pos += snprintf( pBuffer, nSize-pos, "MPEG2-TS;" );
		else
		{
			if ( ( pTracks->track_attr & ATTR_ENCRYPTED_FLAG ) )
				pos += snprintf( pBuffer, nSize-pos, "ENCRYPTED-TS;" );
			else
				pos += snprintf( pBuffer, nSize-pos, "UNKNOWN-TS;" );
		}

	} else
	{
		if ( pTracks->main_video_index != 0xffff )
			pESElmnt = pTracks->track[pTracks->main_video_index].es_elmnt;
		else
		if ( pTracks->main_audio_index != 0xffff )
			pESElmnt = pTracks->track[pTracks->main_audio_index].es_elmnt;
		else
		{
			pos += snprintf( p+pos, nSize-pos, "Error: no main video and audio specified."  );
			return 0; //no main track found;
		}
		if ( pESElmnt->es_type == ES_MPEG1 )
			pos += snprintf( p, nSize-pos, "MPEG1-PS;" );
		else
		if ( pESElmnt->es_type == ES_MPEG2 )
			pos += snprintf( p, nSize-pos, "MPEG2-PS;" );
		else
			pos += snprintf( p, nSize-pos, "UNKNOWN-PS;" );
	}

	for ( i = 0; i<pTracks->number_track; i++ )
	{
		if ( pTracks->track[i].ts_elmnt && 
			 pTracks->track[i].ts_elmnt->content_type == VIDEO_DATA &&
			 pTracks->track[i].ts_packets_counter > 2 )
		{
			 video_data_present = 1;
			 if ( pTracks->track[i].ts_elmnt->format_fourcc == SAGE_FOURCC( "H264" ) )
				h_264_present=1;
		}
		if ( pTracks->track[i].ts_elmnt && 
			 pTracks->track[i].ts_elmnt->content_type == AUDIO_DATA &&
			 pTracks->track[i].ts_packets_counter > 2 )
		{
			 audio_data_present = 1;
		}
		if ( pTracks->track[i].av_elmnt->content_type == VIDEO_DATA )
		{
			bit_rate += GetVideoRate( pTracks->track[i].av_elmnt  );
			video_num++;
		}
		if ( pTracks->track[i].av_elmnt->content_type == AUDIO_DATA )
		{
			bit_rate += GetAudioRate( pTracks->track[i].av_elmnt  );
			audio_num++;
		}
	}

	if ( nSize > pos && bit_rate > 0 )
		pos += snprintf( p+pos, nSize-pos, "br=%d;", bit_rate );

	if ( IS_TS_TYPE( pTracks->track_type ) )
	{
		pos += snprintf( p+pos, nSize-pos, "%s", 
			 pTracks->track_type==MPEG_M2TS?"ps=192;":pTracks->track_type==MPEG_ASI?"ps=208;":"" );
	}

	if ( video_num == 0 )
	{
		if ( IS_TS_TYPE( pTracks->track_type ) )
		{
			if ( !video_data_present && audio_num > 0) //R5000 recording live tv has enough data to parse out video AV info,  
				pos += snprintf( p+pos, nSize-pos, "audioonly=1;" );

		} else
		{
			pos += snprintf( p+pos, nSize-pos, "audioonly=1;" );
		}

	}

	if ( audio_num == 0 )
	{
		if ( IS_TS_TYPE( pTracks->track_type ) )
		{
			if ( !audio_data_present && video_num > 0 ) //R5000 recording live tv has enough data to parse out video AV info,  
				pos += snprintf( p+pos, nSize-pos, "videoonly=1;" );

		} else
		{
			pos += snprintf( p+pos, nSize-pos, "videoonly=1;"  );
		}
	}

	for ( i = 0; i<pTracks->number_track  ; i++ )
	{
		if ( pTracks->track[i].av_elmnt->content_type == VIDEO_DATA )
		{
			if ( nSize > pos ) pos += snprintf( p+pos, nSize-pos, "[" );
			pos += AVElmntInfo( pTracks->track[i].av_elmnt, p+pos, nSize-pos );
			if ( i == pTracks->main_video_index )
				if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "main=yes;"  );
			pos += TagInfo( pTracks->track_type, pTracks->track[i].ts_elmnt, 
				                    pTracks->track[i].es_elmnt, p+pos, nSize-pos );
			if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "index=%d;", pTracks->track[i].av_elmnt->stream_index );
			if ( nSize > pos+2 ) { p[pos++] = ']'; p[pos++] = ';';  } //terminator
		}
	}

	for ( i = 0; i<pTracks->number_track  ; i++ )
	{
		if ( pTracks->track[i].av_elmnt->content_type == AUDIO_DATA )
		{
			char tmp[16];
			uint32_t language_code;
			uint8_t audio_type;
			if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "[" );
			pos += AVElmntInfo( pTracks->track[i].av_elmnt, p+pos, nSize-pos );
			if ( i == pTracks->main_audio_index )
				if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "main=yes;"  );
			if ( pTracks->track[i].ts_elmnt != NULL && pTracks->track[i].ts_elmnt->language_code )
			{
				language_code = pTracks->track[i].ts_elmnt->language_code;
				audio_type = pTracks->track[i].ts_elmnt->audio_type;
			}
			else
			{
				language_code = pTracks->track[i].es_elmnt->language_code;
				audio_type = pTracks->track[i].es_elmnt->audio_type;
			}
			if ( language_code )
			{
				if ( language_code == LanguageCode( (uint8_t*)"qaa" ) )
					strncpy( tmp, "original", sizeof(tmp) );
				else
					Language(language_code, tmp );
				if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "lang=%s;", tmp );
			}
			if ( audio_type )
				if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "ty=%s;",AudioType(audio_type) );
			pos += TagInfo( pTracks->track_type, pTracks->track[i].ts_elmnt, 
				                   pTracks->track[i].es_elmnt, p+pos, nSize-pos );
			if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "index=%d;", pTracks->track[i].av_elmnt->stream_index );
			if ( nSize > pos+3 ) { p[pos++] = ']'; p[pos++] = ';';  } //terminator
		}
	}
	for ( i = 0; i<pTracks->number_track  ; i++ )
	{
		if ( pTracks->track[i].av_elmnt->content_type == SUBTITLE_DATA )
		{
			char tmp[16];
			uint32_t language_code;
			if ( nSize > pos ) pos += snprintf( p+pos, nSize-pos, "[" );
			pos += AVElmntInfo( pTracks->track[i].av_elmnt, p+pos, nSize-pos );
			if ( pTracks->track[i].ts_elmnt != NULL && pTracks->track[i].ts_elmnt->language_code )
				language_code = pTracks->track[i].ts_elmnt->language_code;
			else
				language_code = pTracks->track[i].es_elmnt->language_code;
			if ( language_code )
			{
				Language(language_code, tmp );
				if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "lang=%s;", tmp );
			}
			pos += TagInfo( pTracks->track_type, pTracks->track[i].ts_elmnt, 
				                    pTracks->track[i].es_elmnt, p+pos, nSize-pos );
			if ( nSize > pos )	pos += snprintf( p+pos, nSize-pos, "index=%d;", pTracks->track[i].av_elmnt->stream_index );
			if ( nSize > pos+2 ) { p[pos++] = ']'; p[pos++] = ';';  } //terminator
		}
	}
	if ( pos < nSize ) p[pos]= 0x0;
	return pos;
}

char* AudioType( uint8_t type )
{
	if ( type == 1 )
		return "Silent";
	if ( type == 2 )
		return "hearing-impaired"; 
	if ( type == 3 )
		return "commentary";
	return "";
}
