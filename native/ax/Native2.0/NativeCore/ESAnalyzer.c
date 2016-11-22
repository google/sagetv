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
#include "PSParser.h"
#include "ESAnalyzer.h"

/*	Parse a	packet header when present */
/* return 1: success; 0:packet data is ready( length is short); -1:error PES packet */

static int UnpackTimeStamp(const unsigned char	* pData, ULONGLONG *Clock );
int ReadPESHeader( const unsigned char* pData, int nBytes, PES *pPES )	
{
	int i, has_pts = 0, has_dts = 0, format_type=ES_MPEG2;
	unsigned char* p;
	unsigned short packet_length, header_length;
	ULONGLONG pts = 0, dts = 0;
	unsigned char   stream_id;

	if ( nBytes < 9 )
		return 0;   //too short

	//make sure PES header start code is correct, the call must precede to SearchPESStart 
	//if ( pData[0] != 0x0 || pData[1] != 0x0 || pData[2] != 0x1 )
	//	return 0;

	//if ( IS_VIDEO_STREAM_ID(stream_id) ||
	//	 IS_AUDIO_STREAM_ID(stream_id) || 
	//	 IS_VC1_STREAM_ID(stream_id)    ||
	//	 IS_PRIVATE_STREAM_ID(stream_id) )
	//	stream_id = pData[3];
	//else
	//	return 0;

	stream_id = pData[3];

	header_length = 6;	 
	packet_length = pData[5] | (pData[4] << 8);

	//I have no choice to turn down audio streamid (1101 xxxx )  ZQ. 
	//if ( !IsVideoStreamId(stream_id) && !(stream_id & 0xf0 == 0xc0 ) && !IsPrivateStreamId( stream_id) )
	p = (unsigned char*)&pData[6];
	nBytes -= 6;

	//skip mpeg1 stuffing ff
	for ( i = 0; i<16 && *p == 0xff && nBytes>0; i++ )
	{
		p++; nBytes--;
		header_length++;
	}

	if ( nBytes == 0 ) return 0;

	if (  ( *p & 0xc0 )== 0x80 ) //mpeg2 
		format_type = ES_MPEG2;
	else
		format_type = ES_MPEG1;

	if ( format_type == ES_MPEG2 )
	{
		if ( pData[8] > 64 ) 
			return -1;
		
		/* PES	header */
		header_length += 3 + pData[8]; //6 bytes header + 3 bytes option + HeadExtensionLen (PTS bytes length)
		nBytes -= (3+pData[8]);
		if ( nBytes < 0 )
			return 0;

		/*	Extract	the	PTS	*/
		if ( ( pData[7] & 0x80 ) )	
		{
			has_pts = UnpackTimeStamp(	(const unsigned char*)pData + 9, &pts );
			if ( !has_pts )
				return -2;
		}
		/*	Extract	the	DTS	*/
		if ( ( pData[7] & 0x40 ) )	
		{
			has_dts = UnpackTimeStamp(	(const unsigned char*)pData + 9 + 5, &dts );
			if ( !has_dts )
				return -3;
		}


	} else
	if ( format_type == ES_MPEG1 )
	{
		unsigned char b;
		b = (*p)>>6;
		if ( b == 1 )
		{
			p += 2;
			b = (*p)>>6;
			header_length += 2;
			nBytes -= 2;
		} 
		if ( b == 0 )
		{
			if ( nBytes < 5 ) return 0;
			if ( 0 != (*p & 0x20 ) )
			{
				has_pts = UnpackTimeStamp(	(const unsigned char*)p, &pts );
				if ( !has_pts ) 
					return -2;
				header_length += 4;
				nBytes -= 4;
				if ( nBytes < 0 ) 
					return 0;
			}

			if ( nBytes < 5 ) return 0;
			if ( 0 != (*p & 0x10 ) )
			{
				has_dts = UnpackTimeStamp(	(const unsigned char*)p+5, &dts );
				if ( !has_dts ) 
					return -3;
				header_length += 5;
				nBytes -= 5;
				if ( nBytes < 0 ) 
					return 0;
			}
			header_length++;
		}
	} 

	pPES->header_length = header_length;	 
	pPES->packet_length = packet_length;
	pPES->has_pts    = has_pts;	
	pPES->has_dts    = has_dts;	
	pPES->pts        = pts;
	pPES->dts        = dts;
	pPES->stream_id  = stream_id;
	pPES->es_type    = format_type;


	return 1;
}

int UnpackTimeStamp(const unsigned char	* pData, ULONGLONG *Clock )
{
	ULONGLONG pts;

	//check format validation
	if ( ( pData[0] & 0x30 ) == 0x0 || (pData[2] & 1) != 1 || (pData[4] & 1) != 1) 
		return 0;

	pts  = ((ULONGLONG)(((pData[0] >> 1) & 7))) << 30;
  	pts |= (ULONGLONG)pData[1] << 22;
  	pts |= (ULONGLONG)(pData[2]>>1) << 15;
  	pts |=  pData[3] << 7;
  	pts |= (pData[4]>>1);

	*Clock = pts;

	return 1;
}

void ReadPESSubId( PES* pPES, const unsigned char *pData, int nBytes )
{
	if ( IS_VIDEO_STREAM_ID(pPES->stream_id) )
	{
		pPES->pes_sub_id = pPES->stream_id;
	} else
	if ( IS_AUDIO_STREAM_ID(pPES->stream_id) )
	{
		pPES->pes_sub_id = pPES->stream_id;
	} else
	if ( IS_PRIVATE_STREAM_ID(pPES->stream_id) )
	{
		pPES->pes_sub_id = pData[pPES->header_length];
		return ;
	} else
	if ( pPES->stream_id == 0xfd ) 
	{
		if ( (pData[7] & 0x01) ) //extension_flag ISO 13818 AMD2
		{
			int skip;
			skip = 9 + (pPES->has_pts ? 5 : 0) + (pPES->has_dts ? 5 : 0);
			if ( nBytes > skip && pData[skip] & 0x01 )
			{
				int flag2 = pData[skip];
				skip++;
				if ( ( flag2 & 80 ) )
					skip += 16;
				if ( ( flag2 & 40 ) && nBytes > skip )
					skip += 1 + pData[skip];
				if ( ( flag2 & 20 ) )
					skip += 2;
				if ( ( flag2 & 10 ) )
					skip += 2;
				if ( nBytes > skip && (pData[skip]&0x7f) > 1 && (pData[skip+1]&0x80)==0 ) //extension_field_length > 1 and extension_field_flag = 0
				{
					pPES->pes_sub_id = (pData[skip+1] & 0x7f);
					return ;
				}
			}
		} else
		{
			pPES->pes_sub_id = pData[pPES->header_length];
			return ;
		}

	} else
		pPES->pes_sub_id = 0;

	return ;
}

int AnylyzeTSESElement( ES_ELEMENT *pESElmnt )
{
	pESElmnt->es_type = pESElmnt->pes.es_type;

	if ( IS_VIDEO_STREAM_ID( pESElmnt->pes.stream_id ) )
	{
		pESElmnt->content_type = VIDEO_DATA;
	} else
	if ( IS_AUDIO_STREAM_ID( pESElmnt->pes.stream_id ) )
	{
		pESElmnt->content_type = AUDIO_DATA;
	} else
	if ( IS_OTHER_STREAM_ID( pESElmnt->pes.stream_id ) )
	{
		pESElmnt->content_type = PRIVATE_DATA;
	} else
	if ( IS_PRIVATE_STREAM_ID( pESElmnt->pes.stream_id ) )
	{
		pESElmnt->content_type = PRIVATE_DATA;
	} else
	{
		SageLog(( _LOG_TRACE, 2, TEXT("ERROR: unknown stream id:%02x in PES" ), pESElmnt->pes.stream_id ));
		ASSERT( 0 );
		return 0;
	}

	pESElmnt->es_type = pESElmnt->pes.es_type;

	return 1;
	
}

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

int AnylyzePSESElement( ES_ELEMENT *pESElmnt, PSM_LIST *pPSMList, const unsigned char* pData, int nSize )
{
	unsigned short stream_type = 0;
	unsigned char* stream_inf;
	int i;

	//look up stream type in psm if psm is available
	for ( i = 0; i<pPSMList->psm_num; i++ )
	{
		if ( pPSMList->psm[i].stream_id == pESElmnt->pes.pes_sub_id )
		{
			stream_type = pPSMList->psm[i].stream_type;
			stream_inf  = pPSMList->psm[i].stream_info;
			pESElmnt->language_code = GetLanguageCode( pPSMList->psm[i].stream_info, 
													   sizeof(pPSMList->psm[i].stream_info) );
			pESElmnt->audio_type = GetAudioType( pPSMList->psm[i].stream_info, 
													   sizeof(pPSMList->psm[i].stream_info) );
			break;
		}
	}

	if ( IS_PRIVATE1_STREAM_ID( pESElmnt->pes.stream_id ) )
	{
		if( ( pESElmnt->pes.pes_sub_id&0xf8 ) == 0x88 )
        {
			pESElmnt->content_type = AUDIO_DATA;
			pESElmnt->format_fourcc = SAGE_FOURCC( "DTS " );
			pESElmnt->private_bytes = 4;
			memcpy(	pESElmnt->private_data, pData+pESElmnt->pes.header_length, pESElmnt->private_bytes );
			//SageTV private tag for AC3T and AC3E
			if ( pESElmnt->private_data[1] == 0x1 && pESElmnt->private_data[2] == 'C' )
			{
				 if ( pESElmnt->private_data[3] == 2) //SAGETV defined
					pESElmnt->format_fourcc = SAGE_FOURCC( "DTSM" );
			}	
			return 1;
        } else
		if( (pESElmnt->pes.pes_sub_id&0xf8) == 0x98 )
        {
			pESElmnt->content_type = AUDIO_DATA;
			pESElmnt->format_fourcc = SAGE_FOURCC( "DTS " );
			pESElmnt->private_bytes = 4;
			memcpy(	pESElmnt->private_data, pData+pESElmnt->pes.header_length, pESElmnt->private_bytes );
			return 1;
        } else
		if( ( pESElmnt->pes.pes_sub_id&0xf8 ) == 0xE8 ) //SAGETV standard
        {
			pESElmnt->content_type = AUDIO_DATA;
			pESElmnt->format_fourcc = SAGE_FOURCC( "AAC " ); 
			pESElmnt->private_bytes = 4;
			memcpy(	pESElmnt->private_data, pData+pESElmnt->pes.header_length, pESElmnt->private_bytes );
			return 1;
        } else
		if( ( pESElmnt->pes.pes_sub_id&0xf0 ) == 0x80 || (pESElmnt->pes.pes_sub_id&0xf0 ) == 0xc0 )
        {
			pESElmnt->content_type = AUDIO_DATA;
			pESElmnt->format_fourcc = SAGE_FOURCC( "AC3 " );
			pESElmnt->private_bytes = 4;
			memcpy(	pESElmnt->private_data, pData+pESElmnt->pes.header_length, pESElmnt->private_bytes );
			return 1;
		} else
		if( ( pESElmnt->pes.pes_sub_id&0xf0 ) == 0xA0 )
        {
			pESElmnt->content_type = AUDIO_DATA;
			pESElmnt->format_fourcc = SAGE_FOURCC( "LPCM" );
			pESElmnt->private_bytes = 1;
			//memcpy(	pESElmnt->private_data, pData+pESElmnt->pes.header_length, pESElmnt->private_bytes );
			pESElmnt->private_data[0] = *(pData+pESElmnt->pes.header_length);
			return 1;
		} else
		if( ( pESElmnt->pes.pes_sub_id&0xff ) == 0x10 )
        {
			pESElmnt->content_type = TELETEXT_DATA;
			pESElmnt->format_fourcc = SAGE_FOURCC( "TTX " ); //teletext
			pESElmnt->private_bytes = 0;
			return 1;
		} else
		if( pESElmnt->pes.pes_sub_id >= 0x20 && pESElmnt->pes.pes_sub_id <= 0x3f ) 
        {
			pESElmnt->content_type = SUBTITLE_DATA;
			pESElmnt->format_fourcc = SAGE_FOURCC( "SUB " ); //DVB subtitle
			pESElmnt->language_code = GetSubtitleLanuage( pPSMList->psm[i].stream_info, 
													   sizeof(pPSMList->psm[i].stream_info) );
			pESElmnt->private_bytes = 1;
			pESElmnt->private_data[0] = *(pData+pESElmnt->pes.header_length);
			return 1;
		} else
		{
			pESElmnt->content_type = UNKNOWN_DATA;
			pESElmnt->format_fourcc = 0;
			pESElmnt->private_bytes = 4;
			memcpy(	pESElmnt->private_data, pData+pESElmnt->pes.header_length, pESElmnt->private_bytes );
			SageLog(( _LOG_TRACE, 3, TEXT(" \t UNKNOWN ES: stream_id:0x%02x sub_id0x%02x (0)"), pESElmnt->pes.stream_id, pESElmnt->pes.pes_sub_id ));
			return 1;
		}
	} else
	if ( IS_OTHER_STREAM_ID( pESElmnt->pes.stream_id ) ) //0xfd
	{
		if( pESElmnt->pes.pes_sub_id  >= 0x55 && pESElmnt->pes.pes_sub_id  <= 0x5f )
		{
			pESElmnt->content_type = VIDEO_DATA;
			pESElmnt->format_fourcc = SAGE_FOURCC( "VC1 " );
			pESElmnt->private_bytes = 0;
			return 1;

		} 
		if ( stream_type )
		{
			if ( (pESElmnt->format_fourcc = AudioFormatByStreamType( (unsigned char)stream_type )) != 0 )
			{
				pESElmnt->content_type = AUDIO_DATA;
				pESElmnt->private_bytes = 4;
				memcpy(	pESElmnt->private_data, pData+pESElmnt->pes.header_length, pESElmnt->private_bytes );
				return 1;
			}
		} 
		if( stream_type && pESElmnt->pes.pes_sub_id  >= 0x80 )
		{
			pESElmnt->content_type = AUDIO_DATA;
			pESElmnt->format_fourcc = 0;
			pESElmnt->private_bytes = 4;
			memcpy(	pESElmnt->private_data, pData+pESElmnt->pes.header_length, pESElmnt->private_bytes );

			//SageTV private tag for AC3T and AC3E
			if ( pESElmnt->private_data[1] == 1 && pESElmnt->private_data[2] == 'C' )
			{
				if ( pESElmnt->private_data[3] == 1 )
					pESElmnt->format_fourcc = SAGE_FOURCC( "AC3T" );
				else
				if ( pESElmnt->private_data[3] == 2 )
					pESElmnt->format_fourcc = SAGE_FOURCC( "AC3E" );
			}
			
			if ( pESElmnt->format_fourcc == 0 )
				SageLog(( _LOG_TRACE, 3, TEXT(" \t UNKNOWN ES: stream_id:0x%02x sub_id0x%02x (1)"), pESElmnt->pes.stream_id, pESElmnt->pes.pes_sub_id ));

			return 1;
		} else
		{
			pESElmnt->content_type = UNKNOWN_DATA;
			pESElmnt->format_fourcc = 0;
			pESElmnt->private_bytes = 4;
			memcpy(	pESElmnt->private_data, pData+pESElmnt->pes.header_length, pESElmnt->private_bytes );
			SageLog(( _LOG_TRACE, 3, TEXT(" \t UNKNOWN ES: stream_id:0x%02x sub_id0x%02x (2)"), pESElmnt->pes.stream_id, pESElmnt->pes.pes_sub_id ));
			return 1;
		}
	} else
	//if ( IS_SUBTITLE_STREAM_ID( pESElmnt->pes.stream_id ) && stream_type == 0x06 ) //ZQ old subtitle way, remove it
	//{
	//		pESElmnt->content_type = SUBTITLE_DATA;
	//		pESElmnt->format_fourcc = SAGE_FOURCC( "SUB " );;
	//		pESElmnt->language_code = GetSubtitleLanuage( pPSMList->psm[i].stream_info, 
	//												   sizeof(pPSMList->psm[i].stream_info) );
	//		
	//		pESElmnt->private_bytes = 0;
	//		return 1;
	//	
	//} else
	{
		if ( stream_type == 0 )
		{
			if ( IS_VIDEO_STREAM_ID( pESElmnt->pes.stream_id ) )
			{
				pESElmnt->content_type  = VIDEO_DATA;
				pESElmnt->format_fourcc = SAGE_FOURCC( "MPGV" );
				pESElmnt->private_bytes = 0;
				return 1;
			} else
			if ( IS_AUDIO_STREAM_ID( pESElmnt->pes.stream_id ) )
			{
				pESElmnt->content_type  = AUDIO_DATA;
				pESElmnt->format_fourcc = SAGE_FOURCC( "MPGA" );
				pESElmnt->private_bytes = 0;
				return 1;
			} else
			{
				pESElmnt->content_type = UNKNOWN_DATA;
				pESElmnt->format_fourcc = 0;
				pESElmnt->private_bytes = 0;
				SageLog(( _LOG_TRACE, 3, TEXT(" \t UNKNOWN ES: stream_id:0x%02x sub_id0x%02x (3)"), pESElmnt->pes.stream_id, pESElmnt->pes.pes_sub_id ));
				return 1;
			}
		} else 
		{
			if ( IS_VIDEO_STREAM_ID( pESElmnt->pes.stream_id ) )
			{
				pESElmnt->content_type  = VIDEO_DATA;
				pESElmnt->format_fourcc = SAGE_FOURCC( "MPGV" );
				pESElmnt->private_bytes = 0;
				if (  stream_type == H264_STREAM_TYPE )
					pESElmnt->format_fourcc = SAGE_FOURCC( "H264" );
				else
				if (  stream_type == VIDEO_STREAM_TYPE )
					pESElmnt->format_fourcc = SAGE_FOURCC( "MPGV" );

				return 1;
			} else
			if ( IS_AUDIO_STREAM_ID( pESElmnt->pes.stream_id ) )
			{
				pESElmnt->content_type  = AUDIO_DATA;
				pESElmnt->format_fourcc = SAGE_FOURCC( "MPGA" );
				pESElmnt->private_bytes = 0;
				if (  stream_type == 0x0f ) //0x0f AAC; 0x11 AAC-HE;
					pESElmnt->format_fourcc = SAGE_FOURCC( "AAC " ); //SAGE_FOURCC( "MP4A" );
				else
				if (  stream_type == 0x11 )
					pESElmnt->format_fourcc = SAGE_FOURCC( "AACH" ); //SAGE_FOURCC( "MP4A" );
				return 1;
			} else
			{
				pESElmnt->content_type = UNKNOWN_DATA;
				pESElmnt->format_fourcc = 0;
				pESElmnt->private_bytes = 0;
				SageLog(( _LOG_TRACE, 3, TEXT(" \t UNKNOWN ES: stream_id:0x%02x sub_id0x%02x (4)"), pESElmnt->pes.stream_id, pESElmnt->pes.pes_sub_id ));
				return 1;
			}
		}
	}


	return 0;
 
}


unsigned char* GetPSESDesc( ES_ELEMENT *pESElmnt, PSM_LIST *pPSMList )
{
	unsigned short stream_type = 0;
	unsigned char* stream_inf;
	int i;

	pESElmnt->es_type = pESElmnt->pes.es_type;

	//look up stream type in psm if psm is available
	for ( i = 0; i<pPSMList->psm_num; i++ )
	{
		if ( pPSMList->psm[i].stream_id == pESElmnt->pes.stream_id )
		{
			stream_type = pPSMList->psm[i].stream_type;
			stream_inf  = pPSMList->psm[i].stream_info;
			return pPSMList->psm[i].stream_info;
		}
	}
	return NULL;
}

//const    ULONGLONG UNITS = (10000000);				 // 10 ^ 7
char* _time_stamp( ULONGLONG llTime, char* pBuffer, int nSize )
{
		static char tmp[128];
		char *p;
		int pos=0;
		ULONGLONG div;
		if ( pBuffer == NULL || nSize == 0 )
		{
			p =tmp;
			nSize = sizeof(tmp);
		}
		else
			p = pBuffer;

		llTime = (llTime * PTS_UNITS);  //convert to standar time seconds;
		div = (ULONGLONG)(3600) * 10000000;
		if ( llTime >= div && nSize > pos ) {
			pos += snprintf( p+pos, nSize-pos,  TEXT("%ld"), (unsigned long)(llTime / div) );
			llTime = llTime % div;
		} else
			p[pos++]='0';

		p[pos++]=':';
		div = (ULONGLONG)60 * 10000000;
		if (llTime >= div) {
			pos +=  snprintf( p+pos, nSize-pos,  TEXT("%ld"), (unsigned long)(llTime / div) );
			llTime = llTime % div;
		} else
			p[pos++]='0';
		p[pos++]=':';

		pos += snprintf( p+pos, nSize-pos, TEXT("%ld."), (unsigned long)llTime/10000000 );
		llTime = llTime % 10000000;
		pos += snprintf( p+pos, nSize-pos, TEXT("%3.3ld'"), (unsigned long)(llTime/10000) );
		llTime = llTime % 10000;
		pos += snprintf( p+pos, nSize-pos, TEXT("%3.3ld.%ld"), (unsigned long)(llTime/10), (unsigned long)llTime%10 );

		return p;
}

char*  _data_content_( unsigned char content_type );
char* AudioType( unsigned char type );
void _prints_es_elmnt( ES_ELEMENT* elmnt, int slot_index, int scrambling_flag )
{
	SageLog(( _LOG_TRACE, 3, TEXT("\t ES:%d:%d strmid:0x%x-0x%x esid:0x%04x %s %s %s %s %s %s %s"), 
		     slot_index, elmnt->channel_index, 
			 elmnt->pes.stream_id, elmnt->pes.pes_sub_id, elmnt->es_id, _data_content_(elmnt->content_type),
			 _sagetv_fourcc_(elmnt->format_fourcc, NULL ),
			 elmnt->es_type == ES_MPEG1 ? "MPEG1":"MPEG2",
			 Language( elmnt->language_code, NULL ), 
			 elmnt->audio_type == 0 ? "":AudioType( elmnt->audio_type ),
			 elmnt->pes.packet_length == 0 ? "\"open\"":"",
			 scrambling_flag ? "scrambling":"" )); 
}
