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
#define _USE_32BIT_TIME_T
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <memory.h>
#include "stdlib.h"
#include "string.h"
#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "DVBPSIParser.h"
#include "ATSCPSIParser.h"

static STREAM_DETECT *CreateStreamDetect( );
static void ReleaseStreamDetect( STREAM_DETECT * pStreamDetect );

PSI_PARSER* CreatePSIParser( TS_FILTER* pTSFilter, int nFormat, int nSubFormat )
{
	PSI_PARSER* pPSIParser;
	SageLog(( _LOG_TRACE, 3, TEXT("PSI Filter created  ( ver.2.0.1 )") ));
	SageLog(( _LOG_TRACE, 3, TEXT("PSI format %s"), (nFormat==0)?"Unknown":(nFormat==1)?"ATSC":(nFormat==2)?"DVB":"ERROR"  ));
	pPSIParser = SAGETV_MALLOC( sizeof( PSI_PARSER ) );

	pPSIParser->ts_filter = pTSFilter;

	pPSIParser->stream_format = nFormat;
	if ( nFormat )
		SetStreamFormat( pPSIParser, nFormat, nSubFormat );

	//if we know format, creat psi

	return pPSIParser;
}



void ReleasePSIParser( PSI_PARSER* pPSIParser )
{
	if ( pPSIParser->atsc_psi  )
		ReleaseATSCPSI( pPSIParser->atsc_psi );
	else
	if ( pPSIParser->dvb_psi )
		ReleaseDVBPSI( pPSIParser->dvb_psi );

	if ( pPSIParser->stream_detect != NULL )
		ReleaseStreamDetect( pPSIParser->stream_detect );

	SAGETV_FREE( pPSIParser );
	return;
}

void ResetPSIParser( PSI_PARSER* pPSIParser )
{
	if ( pPSIParser->stream_format == ATSC_STREAM )
		ResetATSCPSI( (ATSC_PSI*)pPSIParser->atsc_psi );
	else
	if ( pPSIParser->stream_format == DVB_STREAM )
		ResetDVBPSI( pPSIParser->dvb_psi );

	pPSIParser->atsc_packet_count = pPSIParser->dvb_packet_count = 0; 
	pPSIParser->packet_count = 0;
	return;
}

void SetLanguage( PSI_PARSER* pPSIParser, uint32_t nLanguageCode )
{
	pPSIParser->language_code = nLanguageCode;
	if ( pPSIParser->dvb_psi != NULL )
		pPSIParser->dvb_psi->language_code = nLanguageCode;
}

void SetStreamFormat( PSI_PARSER* pPSIParser, int nFormat, int nSubFormat )
{
	if ( pPSIParser->stream_format != nFormat )
	{
		if ( pPSIParser->atsc_psi  )
		{
			ReleaseATSCPSI( pPSIParser->atsc_psi );
			pPSIParser->atsc_psi  = NULL;
		}
		else
		if ( pPSIParser->dvb_psi )
		{
			ReleaseDVBPSI( pPSIParser->dvb_psi );
			pPSIParser->dvb_psi = NULL;
		}
		pPSIParser->stream_format = 0;
		pPSIParser->sub_format = 0;
	}

	if ( nFormat == ATSC_STREAM )
	{
		pPSIParser->stream_format = nFormat;
		pPSIParser->sub_format = nSubFormat;

		if ( pPSIParser->atsc_psi == NULL )
			pPSIParser->atsc_psi = CreateATSCPSI( pPSIParser );

		if ( pPSIParser->dumper.message_dumper != NULL )
		{
			MESSAGE_DATA message_data;
			char buf[64];
			snprintf( buf, sizeof(buf), "%s", StreamFormatString( nFormat, nSubFormat) );
			memcpy( message_data.title, "FORMAT", 7 );
			message_data.message = (uint8_t*)buf;
			message_data.message_length = (uint16_t)strlen(buf);
			message_data.buffer = (uint8_t*)buf;
			message_data.buffer_length = (uint16_t)sizeof(buf);
			pPSIParser->dumper.message_dumper( pPSIParser->dumper.message_context, &message_data, sizeof(message_data) );
		}
	}
	else
	if ( nFormat == DVB_STREAM )
	{
		pPSIParser->stream_format = nFormat;
		pPSIParser->sub_format = nSubFormat;

		if ( pPSIParser->dvb_psi == NULL )
			pPSIParser->dvb_psi = CreateDVBPSI( pPSIParser );
		if ( pPSIParser->dumper.message_dumper != NULL )
		{
			MESSAGE_DATA message_data;
			char buf[64];
			snprintf( buf, sizeof(buf), "%s", StreamFormatString( nFormat, nSubFormat) );
			memcpy( message_data.title, "FORMAT", 7 );
			message_data.message = (uint8_t*)buf;
			message_data.message_length = (uint16_t)strlen(buf);
			message_data.buffer = (uint8_t*)buf;
			message_data.buffer_length = (uint16_t)sizeof(buf);
			pPSIParser->dumper.message_dumper( pPSIParser->dumper.message_context, &message_data, sizeof(message_data) );
		}
	}

}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
static STREAM_DETECT *CreateStreamDetect( )
{
	STREAM_DETECT * pStreamDetect = SAGETV_MALLOC( sizeof(STREAM_DETECT) );

	pStreamDetect->tsdt_section = CreateSection( );
	pStreamDetect->nit_section = CreateSection( );
	pStreamDetect->sdt_section = CreateSection( );
	pStreamDetect->eit_section = CreateSection( );
	pStreamDetect->rst_section = CreateSection( );
	pStreamDetect->tdt_section = CreateSection( );
	pStreamDetect->rnt_section = CreateSection( );
	pStreamDetect->dit_section = CreateSection( );
	pStreamDetect->sit_section = CreateSection( );
	pStreamDetect->mip_section = CreateSection( );
	pStreamDetect->psip_section = CreateSection( );
	pStreamDetect->scte_section = CreateSection( );

	pStreamDetect->tsdt_count = pStreamDetect->nit_count  =pStreamDetect->sdt_count = 0;
	pStreamDetect->eit_count  = pStreamDetect->rst_count   =pStreamDetect->tdt_count = 0 ;
	pStreamDetect->mip_count  = pStreamDetect->rnt_count   =pStreamDetect->dit_count = pStreamDetect->sit_count = 0;
	pStreamDetect->pat_e_count= pStreamDetect->stt_e_count =pStreamDetect->opera_count= 0 ;
	pStreamDetect->psip_count = pStreamDetect->scte_count  = 0;
	return pStreamDetect;
}

static void ReleaseStreamDetect( STREAM_DETECT * pStreamDetect )
{
	ReleaseSection( pStreamDetect->tsdt_section );
	ReleaseSection( pStreamDetect->nit_section );
	ReleaseSection( pStreamDetect->sdt_section );
	ReleaseSection( pStreamDetect->eit_section );
	ReleaseSection( pStreamDetect->rst_section );
	ReleaseSection( pStreamDetect->tdt_section );
	ReleaseSection( pStreamDetect->rnt_section );
	ReleaseSection( pStreamDetect->dit_section );
	ReleaseSection( pStreamDetect->sit_section );
	ReleaseSection( pStreamDetect->mip_section );
	ReleaseSection( pStreamDetect->psip_section );
	ReleaseSection( pStreamDetect->scte_section );
	SAGETV_FREE( pStreamDetect );
}

static int IsDVBPSIPacket( STREAM_DETECT * pStreamDetect, TS_PACKET *pTSPacket  )
{
	uint16_t pid = pTSPacket->pid;
	const uint8_t* payload_data; int payload_size;
	SECTION_HEADER section_header;

	payload_data = pTSPacket->data + pTSPacket->payload_offset;
	payload_size = pTSPacket->payload_bytes;

    switch ( pid ) {
    case 0x02:      //TSDT
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->tsdt_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->tsdt_section, payload_data, payload_size ) )
			return 0;
		UnpackSectionDataHeader( &section_header, pStreamDetect->tsdt_section );
		if ( section_header.table_id != 0x03  ) 
			return 0;
        return 1;
    case 0x10:      //NIT,ST 
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->nit_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->nit_section, payload_data, payload_size ) )
			return 0;
		UnpackSectionDataHeader( &section_header, pStreamDetect->nit_section );
		if ( section_header.table_id != 0x40 && section_header.table_id != 0x41 ) //0x40 actual nit; 0x41 other nit
			return 0;
        return 1;
    case 0x11:      //SDT, BAT, ST 
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->sdt_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->sdt_section, payload_data, payload_size ) )
			return 0;
		UnpackSectionDataHeader( &section_header, pStreamDetect->sdt_section );
		if ( section_header.table_id != 0x42 && section_header.table_id != 0x46 ) //0x42 actual nit; 0x46 other nit
			return 0;
        return 1;
    case 0x12: 		//EIT, ST, CIT
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->eit_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->eit_section, payload_data, payload_size ) )
			return 0;
		UnpackSectionDataHeader( &section_header, pStreamDetect->eit_section );
		if ( section_header.table_id < 0x4e && section_header.table_id > 0x6f )
			return 0;
        return 1;
    case 0x13:		//RST, ST
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->rst_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->rst_section, payload_data, payload_size ) )
			return 0;
		UnpackSectionDataHeader( &section_header, pStreamDetect->rst_section );
		if ( section_header.table_id != 0x71 && section_header.table_id != 0x72 ) 
			return 0;
        return 1;
    case 0x14:		//TDT, TOT, ST
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->tdt_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->tdt_section, payload_data, payload_size ) )
			return 0;
		UnpackSectionDataHeader( &section_header, pStreamDetect->tdt_section );
		if ( section_header.table_id != 0x70 &&  section_header.table_id != 0x73 && section_header.table_id != 0x72 ) 
			return 0;
        return 1;
    case 0x15:		//network sync
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->mip_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->mip_section, payload_data, payload_size ) )
			return 0;
        return 0;  //diable it because we don't it's table id
    case 0x16:		//RNT
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->rnt_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->rnt_section, payload_data, payload_size ) )
			return 0;
		UnpackSectionDataHeader( &section_header, pStreamDetect->rnt_section );
		if ( section_header.table_id != 0x79 ) 
			return 0;
        return 1;
    case 0x1E:		//DIT
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->dit_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->dit_section, payload_data, payload_size ) )
			return 0;
 		UnpackSectionDataHeader( &section_header, pStreamDetect->dit_section );
		if ( section_header.table_id != 0x7E ) 
			return 0;       
		return 1;
    case 0x1F:		//SIT
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->sit_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->sit_section, payload_data, payload_size ) )
			return 0;
 		UnpackSectionDataHeader( &section_header, pStreamDetect->sit_section );
		if ( section_header.table_id != 0x7F ) 
			return 0;       
        return 1;
    }
    return 0;
}

static int IsATSPSICPacket( STREAM_DETECT * pStreamDetect, TS_PACKET *pTSPacket  )
{
	uint16_t pid = pTSPacket->pid;
	const uint8_t* payload_data; int payload_size;
	
	payload_data = pTSPacket->data + pTSPacket->payload_offset;
	payload_size = pTSPacket->payload_bytes;

    switch ( pid ) {
    case 0x1ffb: 	//PSIP
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->psip_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->psip_section, payload_data, payload_size ) )
			return 0;
        return 1;
    case 0x1ffc:	//SCTE
		if ( !CheckPacketContinuity( pTSPacket, &pStreamDetect->scte_count ) )
			return 0;
		if ( !UnpackSection( pTSPacket->start, pStreamDetect->scte_section, payload_data, payload_size ) )
			return 0;
		pStreamDetect->scte_flag++;
        return 1;
  //  case 0x1ff7:    //PAT-E
		//pPSIParser->pat_e_count++; 
  //      return 1;
  //  case 0x1ff8:    //STT-E 
		//pPSIParser->stt_e_count++;
  //      return 1;
  //  case 0x1ffa:    //operational packets
		//pPSIParser->opera_count++;
  //      return 1;
    }
    return 0;
}


int ParseTSPSI( TS_FILTER* pTSFilter, TS_PACKET *pTSPacket )
{
	PSI_PARSER* pPSIParser = (PSI_PARSER*)pTSFilter->psi_parser;
	int ret = 0;
	
	pPSIParser->packet_count++;

	if ( pPSIParser->stream_format == 0 && pPSIParser->packet_count < 100000 )
	{ 
		int stream_format=0, sub_format=0;

		if ( pPSIParser->stream_detect == NULL )
			pPSIParser->stream_detect = CreateStreamDetect( );
		//guess stream format
		if ( IsATSPSICPacket( pPSIParser->stream_detect, pTSPacket  ) )
			pPSIParser->atsc_packet_count++;
		else
		if ( IsDVBPSIPacket( pPSIParser->stream_detect, pTSPacket   ) )
			pPSIParser->dvb_packet_count++; 

		if ( pPSIParser->dvb_packet_count >= pPSIParser->atsc_packet_count + 2 )
			stream_format = DVB_STREAM;
		if ( pPSIParser->dvb_packet_count + 2 <= pPSIParser->atsc_packet_count )
			stream_format = ATSC_STREAM;

		//if we know format, create psi
		if ( stream_format == ATSC_STREAM )
		{
			if ( pPSIParser->stream_detect->scte_flag > 1 )
				sub_format = CABLE;
		}

		if ( stream_format )
		{
			SetStreamFormat( pPSIParser, stream_format, sub_format );
			ReleaseStreamDetect( pPSIParser->stream_detect );
			pPSIParser->stream_detect = NULL;
		}

	} else
	if ( pPSIParser->stream_format == ATSC_STREAM )
	{
		ret = ProcessATSCPSI( pPSIParser->atsc_psi, pTSPacket );
		return ret;
	} else
	if ( pPSIParser->stream_format == DVB_STREAM )
	{
		ret = ProcessDVBPSI( pPSIParser->dvb_psi, pTSPacket );
	}
	return 0;
}

uint8_t* GetDescriptor( const uint8_t *pData, int Bytes, uint8_t Tag, int *pLength )
{
	uint8_t tag; 
	int len;
	int i=0;
	*pLength = 0;
	while ( i+2 <= Bytes ) 
	{
		tag = pData[i];
		len = pData[i+1];
		if ( tag == Tag )
		{
			*pLength = len;
			return (uint8_t*)pData+i;
		}
		i += 2+len;
	}
	return NULL;
}

char* StreamFormatString( int format, int sub_format )
{
	if ( format == ATSC_STREAM )
	{
		switch (sub_format) {
		case	TERRESTRIAL: return "ATSC";
		case	CABLE:		 return "QAM";
		default:   return "ATSC";
		}
	} else
	if ( format == DVB_STREAM )
	{
		switch (sub_format) {
		case	TERRESTRIAL: return "DVB-T";
		case	CABLE:		 return "DVB-C";
		case	SATELLITE:	 return "DVB-S";
		default:   return "DVB";
		}
	}
	return "UNKNOWN";
}


int TransJWideString( char* pBufOut, int nBufOutSize, uint16_t* pBufIn )
{
	
	uint16_t*  p = (uint16_t*)pBufIn;
	int len, ret;
	if ( p == NULL ) return 0;

	len = 0;
	while ( *p && len + MB_CUR_MAX < nBufOutSize ) 
	{
		// TODO validate the APPLE and BIGENDIAN behavior, instead of "len" it was using "i" but "i" was not defined anymore, so assume it would be "len"
		uint16_t wch;
#if defined(__APPLE__)
		wch = (wchar_t)OSReadBigInt16(p, len * 2);
#else
#if defined( BIGENDIAN )
		wch = p[len];
#else
		wch =  ( ((*p<<8)&0xff00) | *p >> 8 ); 
#endif
#endif 
		ret = (int)wctomb( pBufOut+len, wch );
		if ( ret < 0 ) break;
		len += ret;
		p++;
	}

	pBufOut[len] = 0x0;

	return len;
}


void ReleaseString( STRING* pString )
{
	ReleaseDescData( &pString->data );
}

void ReleaseMesg( MESG* pMesg )
{
	ReleaseDescData( &pMesg->title );
	ReleaseDescData( &pMesg->body );
}

char* Language( uint32_t lLanguageCode, char* pLanguage )
{
	static  uint8_t _language[4];
	if ( pLanguage == NULL )
		pLanguage = (char*)_language;

	pLanguage[0] = (uint8_t)(lLanguageCode & 0x0ff);
	pLanguage[1] = (uint8_t)(( lLanguageCode >> 8 )  & 0x0ff);
	pLanguage[2] = (uint8_t)(( lLanguageCode >> 16 ) & 0x0ff);
	pLanguage[3] = 0x0;
	return pLanguage;
}

uint32_t LanguageCode( uint8_t* pLanguage )
{
	return ( pLanguage[2] <<16 )|(pLanguage[1]<<8)|(pLanguage[0]);
}

char* UTCFormat( uint32_t t, char* p, int len )
{
	static char utc_time[30];
	struct tm *utc;
	utc = localtime( (const time_t *)&t );  
	if ( utc != NULL && utc->tm_isdst )
	{
		t -= 3600;
		utc = localtime( (const time_t *)&t );  //revseral from mktime()
	}
	if ( utc == NULL ) return 0;
	if ( p == NULL || len == 0)
	{
		p = utc_time;
		len = sizeof(utc_time);
	}
	//utc->tm_hour -= utc->tm_isdst > 0 ? 1 : 0;  //get ridee of saving time
	snprintf( p, len, "UTC:%02d/%02d/%d %02d:%02d:%02d", 
		     utc->tm_mon+1, utc->tm_mday, utc->tm_year+1900,
			 utc->tm_hour,utc->tm_min, utc->tm_sec );

	return p;
}



#include <string.h>
#include <stdio.h>
//#pragma warning(disable : 4172)
//debug utility
char* _descriptor_check( uint8_t* p, int bytes )
{
	static char buf[512]={0}, tmp[16];
	uint8_t tag; 
	int len;
	int i=0;
	sprintf( buf, "len:%d ", bytes );
	while ( i+2 <= bytes ) 
	{
		tag = p[i];
		len = p[i+1];
		sprintf( tmp, "%02X(%d) ", tag, len );
		if ( (int)strlen(buf)+ (int)strlen(tmp) < sizeof(buf) );
			strcat( buf, tmp );
		i += 2+len;
	}
	return buf;
}

