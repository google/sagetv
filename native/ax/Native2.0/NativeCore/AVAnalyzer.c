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

// defined format_fourcc
/***
 //VIDEO
 SAGE_FOURCC( "H264" )
 SAGE_FOURCC( "VC1 " )
 SAGE_FOURCC( "MP4V" )
 SAGE_FOURCC( "MPGV" )
 SAGE_FOURCC( "MPE1" )
 //AUDIO
 SAGE_FOURCC( "AC3 " )
 SAGE_FOURCC( "DTS " )
 SAGE_FOURCC( "AUD " )
 SAGE_FOURCC( "MPGA" )
 SAGE_FOURCC( "MP4A" )
 SAGE_FOURCC( "AAC " )
 SAGE_FOURCC( "AACH" )
 SAGE_FOURCC( "LPCM" )

 SAGE_FOURCC( "SUB " ); //sub title
 SAGE_FOURCC( "TTX " ); //teletext
***/
static int GuessVideoFormat( AV_ELEMENT *pAVElmnt, const uint8_t* pData, int nBytes  );
void ReleaseAVElementData( AV_ELEMENT *pAVEelement )
{
	if ( pAVEelement->private_data != NULL )
		SAGETV_FREE( pAVEelement->private_data );
	pAVEelement->private_data = NULL;
}


static int AnylyzeVideoFormat( AV_ELEMENT *pAVElmnt, uint32_t FormatFourCC, const uint8_t* pData, int nBytes  )
{
	if ( FormatFourCC == SAGE_FOURCC( "H264" ) )
	{
		H264_VIDEO H264Video={0};
		if ( ReadH264VideoHeader( &H264Video, pData, nBytes ) > 0 )
		{
			pAVElmnt->content_type = VIDEO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "H264" );
			pAVElmnt->d.v.h264 = H264Video;
			return 1;
		}
	} else
	if ( FormatFourCC == SAGE_FOURCC( "VC1 " ) )
	{
		MPEG_VIDEO mpeg_video;
		if ( ReadVC1VideoHeader( &mpeg_video, pData, nBytes ) )
		{
			pAVElmnt->content_type = VIDEO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "VC1 " );
			pAVElmnt->d.v.mpeg_video = mpeg_video;
			return 1;
		}

	} else
	if ( FormatFourCC == SAGE_FOURCC( "MPG4 " ) )
	{

	} else
	if ( FormatFourCC == SAGE_FOURCC( "MPGV" ) ) 
	{
		MPEG_VIDEO mpeg_video;
		if ( ReadMpegVideoHeader( &mpeg_video, pData, nBytes ) > 0 )
		{
			pAVElmnt->content_type = VIDEO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "MPGV" );
			pAVElmnt->d.v.mpeg_video = mpeg_video;

			return 1;
		}
	} else
	if ( FormatFourCC  == 0 )
	{
		return GuessVideoFormat( pAVElmnt, pData, nBytes  );
	}

	return 0;

}

static int GuessVideoFormat( AV_ELEMENT *pAVElmnt, const uint8_t* pData, int nBytes  )
{
	//guess H264
	{
		H264_VIDEO H264Video = {0};
		if ( ReadH264VideoHeader( &H264Video, pData, nBytes ) > 0 )
		{
			pAVElmnt->content_type = VIDEO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "H264" );
			pAVElmnt->d.v.h264 = H264Video;
			return 1;
		}
	} 
	//guess VC1
	if ( 0 ){
		MPEG_VIDEO mpeg_video = { 0 };
		if ( ReadVC1VideoHeader( &mpeg_video, pData, nBytes ) )
		{
			pAVElmnt->content_type = VIDEO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "VC1 " );
			pAVElmnt->d.v.mpeg_video = mpeg_video;
			return 1;
		}

	} 
	//guess  MPEG2
	{
		MPEG_VIDEO mpeg_video = { 0 };
		if ( ReadMpegVideoHeader( &mpeg_video, pData, nBytes ) > 0 )
		{
			pAVElmnt->content_type = VIDEO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "MPGV" );
			pAVElmnt->d.v.mpeg_video = mpeg_video;

			return 1;
		}
	} 
	return 0;

}


static int AnylyzeAudioFormat( AV_ELEMENT *pAVElmnt, uint32_t FormatFourCC, const uint8_t* pData, int nBytes, int nMediaType  )
{
	if ( FormatFourCC  == SAGE_FOURCC( "AAC " ) )
	{
		AAC_AUDIO AACAudio=pAVElmnt->d.a.aac;
		if (  ReadAAC_AudioHeader( &AACAudio, pData, nBytes ) )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "AAC " );
			pAVElmnt->d.a.aac = AACAudio;
			return 1;
		}
		//Because we need verfy the AAC-ADTS header in ReadAAC_AudioHeader,
		//we save the header into the AVElmnt. 
		//if the saved header in AVELmnt is destroyed by other header parser code, 
		//we can save AAC-ADTS header into pAVElment->private_data in future ZQ. 
		pAVElmnt->d.a.aac = AACAudio;  
		return 0;
	} else
	if ( FormatFourCC  == SAGE_FOURCC( "AACH" ) )
	{
		AAC_AUDIO AACAudio={0};
		//if (  ReadAAC_AudioHeader( &AACAudio, pData, nBytes ) )
		if ( ReadAACHE_AudioHeader( &AACAudio, pData, nBytes ) )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "AACH" );
			pAVElmnt->d.a.aac = AACAudio;
			return 1;
		}
		return 0;
	} else
	if ( FormatFourCC  == SAGE_FOURCC( "DTS " ) )
	{
		DTS_AUDIO DTSAudio;
		if ( ReadDTS_AudioHeader( &DTSAudio, pData, nBytes ) )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "DTS " );
			pAVElmnt->d.a.dts = DTSAudio;
			return 1;
		}

	} else
	if ( FormatFourCC  == SAGE_FOURCC( "DTSM" ) )
	{
		DTS_AUDIO DTSAudio;
		if ( ReadDTS_AudioHeader( &DTSAudio, pData, nBytes ) )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			//pAVElmnt->format_fourcc = SAGE_FOURCC( "DTS " );
			pAVElmnt->format_fourcc = FormatFourCC;
			pAVElmnt->d.a.dts = DTSAudio;
			return 1;
		}
	} else
	if ( FormatFourCC  == SAGE_FOURCC( "DTSH" ) )
	{
		DTS_AUDIO DTSAudio;
		if ( ReadDTS_AudioHeader( &DTSAudio, pData, nBytes ) )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			//pAVElmnt->format_fourcc = SAGE_FOURCC( "DTS " );
			pAVElmnt->format_fourcc = FormatFourCC;
			pAVElmnt->d.a.dts = DTSAudio;
			return 1;
		}
	} else
	if ( FormatFourCC  == SAGE_FOURCC( "AC3 " ) 
		|| FormatFourCC  == SAGE_FOURCC( "AC3T" ) )
	{
		AC3_AUDIO AC3Audio;
		if ( ReadAC3AudioHeader( &AC3Audio, pData, nBytes) > 0 )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = FormatFourCC;
			pAVElmnt->d.a.ac3 = AC3Audio;
			return 1;
		}
	} else
	if ( FormatFourCC  == SAGE_FOURCC( "AC3E" ) )
	{
		EAC3_AUDIO EAC3Audio;
		//if ( ReadEAC3AudioHeader( &AC3Audio, pData, nBytes) > 0 )
		if ( ReadEAC3AudioHeader( &EAC3Audio, pData, nBytes) > 0 )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = FormatFourCC;
			pAVElmnt->d.a.eac3 = EAC3Audio;
			return 1;
		}
	} else
	if ( FormatFourCC  == SAGE_FOURCC( "MPGA" ) )
	{
		MPEG_AUDIO  MpegAudio;
		if ( ReadMpegAudioHeader( &MpegAudio, pData, nBytes) > 0 )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "MPGA" );
			pAVElmnt->d.a.mpeg_audio = MpegAudio;
			return 1;
		}
	} else
	if ( FormatFourCC  == SAGE_FOURCC( "LPCM" ) )
	{
		LPCM_AUDIO LPCMAudio={0};
		int ret = 0;
		if ( nMediaType == BLUERAY_DVD_MEDIA )
		{
			LPCMAudio.lpcm_source = 3;
			ret = ReadLPCM_AudioHeader( &LPCMAudio, pData, nBytes );
		}
		else
		if ( nMediaType == VOB_DVD_MEDIA )
		{
			LPCMAudio.lpcm_source = 1;
			ret = ReadLPCM_AudioHeader( &LPCMAudio, pData, nBytes );
		}
		else{
			LPCMAudio.lpcm_source = 1;
			ret = ReadLPCM_AudioHeader( &LPCMAudio, pData, nBytes );
			if ( ret == 0 )
			{
				LPCMAudio.lpcm_source = 2;
				ret = ReadLPCM_AudioHeader( &LPCMAudio, pData, nBytes );
			}
		}
			
		if ( ret )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "LPCM" );
			pAVElmnt->d.a.lpcm = LPCMAudio;
			return 1;
		}

	} else
	if ( FormatFourCC  == SAGE_FOURCC( "MPG4 " ) )
	{

	} else	
	{
		AC3_AUDIO   AC3Audio;
		MPEG_AUDIO  MpegAudio;
		if ( ReadMpegAudioHeader( &MpegAudio, pData, nBytes) > 0 )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "MPGA" );
			pAVElmnt->d.a.mpeg_audio = MpegAudio;
			return 1;
		} 
		if ( ReadAC3AudioHeader( &AC3Audio, pData, nBytes) > 0 )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "AC3 " );
			pAVElmnt->d.a.ac3 = AC3Audio;
			return 1;
		} 
	}

	return 0;
}

static int GuessAudioFormat( AV_ELEMENT *pAVElmnt, int nContentType, const uint8_t* pData, int nBytes  )
{
	{
		AAC_AUDIO AACAudio=pAVElmnt->d.a.aac;
		if (  ReadAAC_AudioHeader( &AACAudio, pData, nBytes ) )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "AAC " );
			pAVElmnt->d.a.aac = AACAudio;
			return 1;
		}
		//Because we need verfy the AAC-ADTS header in ReadAAC_AudioHeader,
		//we save the header into the AVElmnt. 
		//if the saved header in AVELmnt is destroyed by other header parser code, 
		//we can save AAC-ADTS header into pAVElment->private_data in future ZQ. 
		pAVElmnt->d.a.aac = AACAudio;  
	} 

	{
		AAC_AUDIO AACAudio={0};
		if (  ReadAAC_AudioHeader( &AACAudio, pData, nBytes ) )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "AACH" );
			pAVElmnt->d.a.aac = AACAudio;
			return 1;
		}

	} 

	{
		DTS_AUDIO DTSAudio;
		if ( ReadDTS_AudioHeader( &DTSAudio, pData, nBytes ) )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "DTS " );
			pAVElmnt->d.a.dts = DTSAudio;
			return 1;
		}

	} 
	{
		DTS_AUDIO DTSAudio;
		if ( ReadDTS_AudioHeader( &DTSAudio, pData, nBytes ) )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "DTS " );
			pAVElmnt->d.a.dts = DTSAudio;
			return 1;
		}
	} 

	{
		AC3_AUDIO AC3Audio;
		if ( ReadAC3AudioHeader( &AC3Audio, pData, nBytes) > 0 )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "AC3 " );
			pAVElmnt->d.a.ac3 = AC3Audio;
			return 1;
		}
	} 
	
	if ( nContentType == AUDIO_DATA )
	{
		MPEG_AUDIO  MpegAudio;
		if ( ReadMpegAudioHeader( &MpegAudio, pData, nBytes) > 0 )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "MPGA" );
			pAVElmnt->d.a.mpeg_audio = MpegAudio;
			return 1;
		}
	} 
	
	if ( 0 ){ //don't guess LPCM
		LPCM_AUDIO LPCMAudio;
		if ( ReadLPCM_AudioHeader( &LPCMAudio, pData, nBytes ) )
		{
			pAVElmnt->content_type = AUDIO_DATA;
			pAVElmnt->format_fourcc = SAGE_FOURCC( "LPCM" );
			pAVElmnt->d.a.lpcm = LPCMAudio;
			return 1;
		}

	} 

	return 0;
}



int AnylyzeAVElement( AV_ELEMENT *pAVElmnt, ES_ELEMENT *pESElmnt, TS_ELEMENT *pTSElmnt, 
					  const uint8_t* pData, int nBytes, int nMediaType ) 
{
	ASSERT( pESElmnt );
	if ( nBytes <= 0 )
		return 0;
	pData  += pESElmnt->pes.header_length + pESElmnt->private_bytes;
	nBytes -= pESElmnt->pes.header_length + pESElmnt->private_bytes;
	//process video
	if ( pTSElmnt != NULL )
	{
		if ( pTSElmnt->content_type == VIDEO_DATA )
		{
			int ret = AnylyzeVideoFormat( pAVElmnt, pTSElmnt->format_fourcc, pData, nBytes );
			if ( ret )
			{
				if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "MPGV" ) )
				{
					if ( !pAVElmnt->d.v.mpeg_video.extension_present || pESElmnt->es_type == ES_MPEG1 )
						pAVElmnt->format_fourcc = SAGE_FOURCC( "MP1V" );
				}
				if ( pTSElmnt && pTSElmnt->format_fourcc == 0 ) pTSElmnt->format_fourcc = pAVElmnt->format_fourcc;
				if ( pESElmnt && pESElmnt->format_fourcc == 0 ) pESElmnt->format_fourcc = pAVElmnt->format_fourcc;
			}
			return ret;
		} 
	} else
	if ( pESElmnt != NULL ) 
	{
		if ( pESElmnt->content_type == VIDEO_DATA )
		{
			int ret = AnylyzeVideoFormat( pAVElmnt, pESElmnt->format_fourcc, pData, nBytes );
			if ( ret )
			{
				if ( pAVElmnt->format_fourcc == SAGE_FOURCC( "MPGV" ) )
				{
					if ( !pAVElmnt->d.v.mpeg_video.extension_present || pESElmnt->es_type == ES_MPEG1 )
						pAVElmnt->format_fourcc = SAGE_FOURCC( "MP1V" );
				}
				if ( pTSElmnt && pTSElmnt->format_fourcc == 0 ) pTSElmnt->format_fourcc = pAVElmnt->format_fourcc;
				if ( pESElmnt && pESElmnt->format_fourcc == 0 ) pESElmnt->format_fourcc = pAVElmnt->format_fourcc;
			} else
			if ( UNKNOWN_MEDIA == nMediaType && pESElmnt->format_fourcc == SAGE_FOURCC( "MPGV" ) )
			{
				ret = AnylyzeVideoFormat( pAVElmnt, SAGE_FOURCC( "H264" ), pData, nBytes );
				if ( ret )
				{
					if ( pTSElmnt && pTSElmnt->format_fourcc == 0 ) pTSElmnt->format_fourcc = pAVElmnt->format_fourcc;
					if ( pESElmnt && pESElmnt->format_fourcc == 0 ) pESElmnt->format_fourcc = pAVElmnt->format_fourcc;
				}
			}
			return ret;
		}
	}

	//process audio
	if ( pTSElmnt != NULL )
	{
		if (  pTSElmnt->content_type == AUDIO_DATA )
		{
			int ret = AnylyzeAudioFormat( pAVElmnt,  pTSElmnt->format_fourcc, pData, nBytes, nMediaType );
			if ( ret )
			{
				if ( pTSElmnt && pTSElmnt->format_fourcc == 0 ) pTSElmnt->format_fourcc = pAVElmnt->format_fourcc;
				if ( pESElmnt && pESElmnt->format_fourcc == 0 ) pESElmnt->format_fourcc = pAVElmnt->format_fourcc;
			}
			return ret;
		}
	}
	else
	if ( pESElmnt != NULL )
	{
		if (  pESElmnt->content_type == AUDIO_DATA || pESElmnt->content_type == PRIVAITE_DATA )
		{
			int ret = AnylyzeAudioFormat( pAVElmnt,  pESElmnt->format_fourcc, pData, nBytes, nMediaType );
			if ( ret )
			{
				if ( pTSElmnt && pTSElmnt->format_fourcc == 0 ) pTSElmnt->format_fourcc = pAVElmnt->format_fourcc;
				if ( pESElmnt && pESElmnt->format_fourcc == 0 ) pESElmnt->format_fourcc = pAVElmnt->format_fourcc;
			}
			return ret;
		}
	}

		//process subtitle
	if ( pTSElmnt != NULL )
	{
		if (  pTSElmnt->content_type == SUBTITLE_DATA )
		{
			int ret = ParseDVBSubtitleDesc( &pAVElmnt->d.s.sub, pTSElmnt->desc, pTSElmnt->desc_len );
			if ( ret )
			{
				pAVElmnt->d.s.sub.type = DVB_SUBTITLE;
				pAVElmnt->content_type = SUBTITLE_DATA;
				pAVElmnt->format_fourcc = SAGE_FOURCC( "SUB " );
				if ( pESElmnt )
				{
					pESElmnt->content_type = SUBTITLE_DATA;
					pESElmnt->language_code =  pTSElmnt->language_code;
					pESElmnt->format_fourcc = SAGE_FOURCC( "SUB " );
				}
				//_prints_av_elmnt( pAVElmnt, 0, 0 );
			}
			return 1;
		}
	}
	else
	if ( pESElmnt != NULL )
	{
		if (  pESElmnt->content_type == SUBTITLE_DATA )
		{
			//pAVElmnt->content_type = SUBTITLE_DATA;
			//pAVElmnt->format_fourcc = SAGE_FOURCC( "SUB " );
			//if ( pTSElmnt && pTSElmnt->format_fourcc == 0 ) pTSElmnt->format_fourcc = pAVElmnt->format_fourcc;
			//if ( pESElmnt && pESElmnt->format_fourcc == 0 ) pESElmnt->format_fourcc = pAVElmnt->format_fourcc;
			return 1;
		}
	}
	
	//guess format
	if ( pTSElmnt != NULL )
	{
		if (  pTSElmnt->content_type == UNIDENTIFIED )
		{
			int ret=0;
			if ( pESElmnt->content_type == VIDEO_DATA )
			{
				ret = GuessVideoFormat( pAVElmnt, pData, nBytes );
				if ( ret )
				{
					pTSElmnt->content_type = VIDEO_DATA;
					pTSElmnt->format_fourcc = pAVElmnt->format_fourcc;
					if ( pTSElmnt && pTSElmnt->format_fourcc == 0 ) pTSElmnt->format_fourcc = pAVElmnt->format_fourcc;
					if ( pESElmnt && pESElmnt->format_fourcc == 0 ) pESElmnt->format_fourcc = pAVElmnt->format_fourcc;

				}
			}
			if ( pESElmnt->content_type == AUDIO_DATA || pESElmnt->content_type == PRIVAITE_DATA )
			{
				ret = GuessAudioFormat( pAVElmnt, pESElmnt->content_type, pData, nBytes );
				if ( ret )
				{
					pTSElmnt->content_type = AUDIO_DATA;
					pTSElmnt->format_fourcc = pAVElmnt->format_fourcc;
					if ( pTSElmnt && pTSElmnt->format_fourcc == 0 ) pTSElmnt->format_fourcc = pAVElmnt->format_fourcc;
					if ( pESElmnt && pESElmnt->format_fourcc == 0 ) pESElmnt->format_fourcc = pAVElmnt->format_fourcc;
				}
			}

			return ret;
		}
	}

	//teletext
	if ( pTSElmnt != NULL )
	{
		if (  pTSElmnt->content_type == TELETEXT_DATA )
		{
			//pAVElmnt->content_type = TELETEXT_DATA;
			//pAVElmnt->format_fourcc = SAGE_FOURCC( "TTX " );
			//return 1;
		}
	}
	else
	if ( pESElmnt != NULL )
	{
		if (  pESElmnt->content_type == TELETEXT_DATA )
		{
		}
	}


	return 0;
}

//used to select main audio
int GetAudioSoundChannelNum( AV_ELEMENT *pAVElmnt )
{
	if ( pAVElmnt == NULL )
		return -1;

	if ( pAVElmnt->content_type != AUDIO_DATA )
		return 0;

	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AC3 " ))
	{
		return pAVElmnt->d.a.ac3.channels;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AC3E" ))
	{
		return pAVElmnt->d.a.ac3.channels;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AC3T" ))
	{
		return pAVElmnt->d.a.ac3.channels;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "DTS " ))
	{
		return pAVElmnt->d.a.dts.channels;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "DTSM" ))
	{
		return pAVElmnt->d.a.dts.channels;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "DTSH" ))
	{
		return pAVElmnt->d.a.dts.channels;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AUD " ))
	{
		return pAVElmnt->d.a.mpeg_audio.channels;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "MPGA" ))
	{
		return pAVElmnt->d.a.mpeg_audio.channels;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "MP4A" ))
	{
		return 0; //not done yet.
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AAC " ))
	{
		return pAVElmnt->d.a.aac.channels;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AACH" ))
	{
		return pAVElmnt->d.a.aac.channels;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "LPCM" ))
	{
		return pAVElmnt->d.a.lpcm.channels;
	}
	return 0;
}

//used to select main audio
int GetAudioSoundBitRate( AV_ELEMENT *pAVElmnt )
{
	if ( pAVElmnt == NULL )
		return -1;

	if ( pAVElmnt->content_type != AUDIO_DATA )
		return 0;

	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AC3 " ))
	{
		return pAVElmnt->d.a.ac3.avgbytes_per_sec;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AC3E" ))
	{
		return pAVElmnt->d.a.ac3.avgbytes_per_sec;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AC3T" ))
	{
		return pAVElmnt->d.a.ac3.avgbytes_per_sec;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "DTS " ))
	{
		return pAVElmnt->d.a.dts.avgbytes_per_sec;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "DTSM" ))
	{
		return pAVElmnt->d.a.dts.avgbytes_per_sec;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "DTSH" ))
	{
		return pAVElmnt->d.a.dts.avgbytes_per_sec;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AUD " ))
	{
		return pAVElmnt->d.a.mpeg_audio.avgbytes_per_sec;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "MPGA" ))
	{
		return pAVElmnt->d.a.mpeg_audio.avgbytes_per_sec;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "MP4A" ))
	{
		return 0; //not done yet.
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AAC " ))
	{
		return pAVElmnt->d.a.aac.avgbytes_per_sec;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "AACH" ))
	{
		return pAVElmnt->d.a.aac.avgbytes_per_sec;
	}
	if ( pAVElmnt->format_fourcc ==  SAGE_FOURCC( "LPCM" ))
	{
		return pAVElmnt->d.a.lpcm.avgbytes_per_sec;
	}
	return 0;
}


//debug utility
char*  _data_content_( uint8_t content_type );
char* _sagetv_fourcc_( uint32_t lFourCC, char* pFourCC );
void _prints_av_elmnt( AV_ELEMENT* elmnt, int slot_index, int process_bytes )
{
	char tmp[16];
	SageLog(( _LOG_TRACE, 3, TEXT("\t AV:%d:%d %s %s\t\t (pos:%d)"), 
		     slot_index, elmnt->channel_index, 
			 _data_content_(elmnt->content_type), _sagetv_fourcc_(elmnt->format_fourcc, tmp ), process_bytes ));

}

