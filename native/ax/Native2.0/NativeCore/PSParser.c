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
#include "ESAnalyzer.h"
#include "AVAnalyzer.h"
#include "TSFilter.h"
#include "PSParser.h"

#define DROP_PARSING_THRESHOLD  120*8
void PSParserZero( PS_PARSER *pPSParser );
static int CreateESId( uint8_t cStreamId, uint8_t cPESSubId );

PS_PARSER* CreatePSParser( TRACKS* pTracks  )
{
	int i;
	PS_PARSER *pPSParser = SAGETV_MALLOC( sizeof(PS_PARSER) );

	pPSParser->psm_list.psm_total_num = pTracks->total_track;
	pPSParser->psm_list.psm = SAGETV_MALLOC( pTracks->total_track*sizeof(PSM) );

	pPSParser->ts_streams.total_streams = 0;
	pPSParser->ts_streams.num_stream = 0;
	pPSParser->ts_streams.ts_element = NULL;
	pPSParser->es_streams.total_streams = pTracks->total_track;
	pPSParser->es_streams.num_stream = 0;
	pPSParser->es_streams.es_element = SAGETV_MALLOC( pTracks->total_track*sizeof(ES_ELEMENT) );
	pPSParser->av_streams.total_streams = pTracks->total_track;
	pPSParser->av_streams.num_stream = 0;
	pPSParser->av_streams.av_element = SAGETV_MALLOC( pTracks->total_track*sizeof(AV_ELEMENT) );
	for ( i = 0; i < pTracks->total_track; i++ )
	{
		pPSParser->es_streams.es_element[i].channel_index = i;
		pPSParser->av_streams.av_element[i].channel_index = i;

		pTracks->track[i].es_elmnt = &pPSParser->es_streams.es_element[i];
		pTracks->track[i].av_elmnt = &pPSParser->av_streams.av_element[i];
	}
	pTracks->main_video_index = 0xffff;
	pTracks->main_audio_index = 0xffff;
	pPSParser->empty_sub_stream_threshold = EMPTY_SUB_STREAM_THRESHOLD;
	pPSParser->tracks = pTracks;

	SageLog(( _LOG_TRACE, 3, TEXT("PS Parser is created  version 2.0.1  ZQ.") ));
	pPSParser->state |= PARSING_INFO;
	return pPSParser;
}

void ReleasePSParser( PS_PARSER *pPSParser )
{
	SAGETV_FREE( pPSParser->psm_list.psm );
	SAGETV_FREE( pPSParser->es_streams.es_element );
	SAGETV_FREE( pPSParser->av_streams.av_element );
	SAGETV_FREE( pPSParser );
}

void ResetPSParser( PS_PARSER *pPSParser )
{
	pPSParser->tracks->main_video_index = 0xffff;
	pPSParser->tracks->main_audio_index = 0xffff;
	pPSParser->used_bytes = 0;
	pPSParser->input_blocks = 0;
	pPSParser->bad_blocks = 0;
	pPSParser->block_count = 0;
	pPSParser->state |= PARSING_INFO;
	pPSParser->scr_cue = 0;
	pPSParser->scr = 0;
	pPSParser->command = 0;
	pPSParser->pack_type = 0;
	
}

/////////////////////////////////////////////////////////////////////////////////////////
//Sage private description tag
#define SAGETV_H264_INFO		 0x40
#define ISO_11172_END_CODE       0xB9
#define PACK_START_CODE          0xBA
#define SYSTEM_START_CODE		 0xBB
#define PADDING_START_CODE       0xBE
#define PSM_START_CODE			 0xBC
#define IS_END_CODE(x)			( x == ISO_11172_END_CODE )
#define IS_PACK_START_CODE(x)	( x == PACK_START_CODE )
#define IS_SYSTEM_START_CODE(x) ( x == SYSTEM_START_CODE )
#define IS_PADDING_START_CODE(x)( x == PADDING_START_CODE )
#define IS_PSM_START_CODE(x)	( x == PSM_START_CODE )
#define IS_PS_STREAM_ID(x) (  IS_PACK_START_CODE(x)    || IS_SYSTEM_START_CODE(x) ||\
	                          IS_PADDING_START_CODE(x) || IS_PSM_START_CODE(x)    ||\
                              IS_PES_STREAM_ID(x) || IS_END_CODE( x ) )

static int ProcessPESData( PS_PARSER *pPSParser, const uint8_t* pData, int nSize,  PES *pPES );
static int DumpPESDHeader( PS_PARSER *pPSParser, const uint8_t* pData, int nSize,  PES *pPES );
static int CheckPSParseDone( PS_PARSER *pPSParser );
static void PostStatusMessage( PS_PARSER *pPSParser, char* pMessageText );
inline uint8_t* SearchPSStart( const uint8_t* pData, int nBytes )

#define	END_PACKET_TYPE			0x01
#define	PSM_PACKET_TYPE			0x02
#define	PADDING_PACKET_TYPE		0x03
#define	SYSTEM_PACKET_TYPE		0x04
#define	PACK_PACKET_TYPE		0x05
#define	PES_PACKET_TYPE			0x06
#define	UNKNOWN_PACKET_TYPE		0x0f

{
	uint32_t code;

	if ( nBytes < 4 )
		return NULL;

	code = 0xffffff00 |*pData++;
	while ( --nBytes )
	{
		if ( ( (code&0x00ffffff)==0x01 ) && IS_PS_STREAM_ID( *(pData) ) )
			return (uint8_t* )pData-3;
		code = (( code << 8 )| *pData++ );
	}

	return NULL;
}

static int UnpackPackHeader( const uint8_t* pData, int nBytes, uint32_t *pDemux, ULONGLONG* pSCR )
{
	int length = 0;
	uint32_t demux;
	uint32_t pad_bytes = 0;
	uint8_t c;
	ULONGLONG ll = 0;
	uint32_t l;
	//mpeg2
	if ( nBytes < 12 ) return 0;
	if ( (pData[4] & 0xC0 ) == 0x40 )
	{
		if ( nBytes < 14 ) return -14;
		ll =  ((ULONGLONG)((uint8_t)pData[4] & 0x38 ))>>3;   //3bits
		ll <<= 2;
		ll |= ((LONGLONG)((uint8_t)pData[4] & 0x03 ));   //2bits
		ll <<= 8;
		ll |= ((LONGLONG)((uint8_t)pData[5] ));          //8bits
		ll <<= 5;
		ll |= ((LONGLONG)((uint8_t)pData[6] &0xf8 ))>>3;    //5bits
		ll <<= 2;
		ll |= ((LONGLONG)((uint8_t)pData[6] &0x03 ));    //2bits
		ll <<= 8;
		ll |= ((LONGLONG)((uint8_t)pData[7]));       //8bits
		ll <<= 5;
		ll |= ((LONGLONG)((uint8_t)pData[8] &0xf8 ))>>3;     //5bits

		l = ((uint32_t)((uint8_t)pData[8] &0x03 ))<<7;  //2bits
		l |= ((uint32_t)((uint8_t)pData[9] &0xfe ))>>1;  //7bits
		*pSCR = ll*300+l;
	
        //skip system_clock_reference_extension
		demux = pData[10];
		demux <<= 8;
		demux |= pData[11];
		demux <<= 6;
		demux |= ( pData[12] >> 2 ) & 0x3f;
		*pDemux = demux;
		pad_bytes = pData[13] & 0x7;
		length = 14 + pad_bytes;
		return length;

	} else
	if ( ( pData[4] & 0xF0 ) == 0x20 ) //mpeg1
	{
		c = pData[4];
		ll = ( c & 0xf ) >> 1;
		ll <<= 8;

		c = pData[5];
		ll |= c;
		ll <<= 7;

		c = pData[6];
		ll |= (c >> 1);
		ll <<= 8;

	    c = pData[7];
		ll |= c;
		ll <<= 7;

		c = pData[8];
		ll |= (c>>1);
		*pSCR = ll;

		demux = pData[9] & 0x7f;
		demux <<= 8;
		demux |= pData[10];
		demux <<= 7;
		c = pData[11];
		demux |=(  c >> 1 );

		*pDemux = demux;
		return 12;
	} 
	
	return 0;
}

static int DecodeSystemHeader( PS_PARSER* pPSParser, const uint8_t* pData, int nBytes )
{
	int length = 0, header_len;
	uint32_t demux=0;
	uint8_t stream_id;
	uint16_t block_size = 0;
	//uint8_t* pData;

	if ( nBytes < 9 ) return -9;
	header_len = ( pData[4] << 8 )|pData[5];
	header_len += 6;

	demux = pData[6] & 0x7f;
	demux <<= 8;
	demux |= pData[7];
	demux <<= 7;
	demux |= ( pData[8] >> 1 ) & 0x7f;
	demux *= 50;

	length = 13;

	pPSParser->demux_bound = demux;

	if ( header_len > 2048 ) return 0;
	if ( nBytes < header_len ) return -header_len;
	if ( ( pData[length+1] & 0xc0 ) != 0xc0 )
		return header_len;

	stream_id = pData[length];
	block_size = pData[length+1] & 0x1f;
	block_size <<= 8;
	block_size |= pData[length+2];

	if ( IS_VIDEO_STREAM_ID( stream_id) )
	{
		//pPSParser->video_stream_id = stream_id; //don't relay on SYSTEM header
		pPSParser->video_block_size = block_size;
	}
	else
	if ( IS_AUDIO_STREAM_ID( stream_id) )
	{
		//pPSParser->audio_stream_id = stream_id; //don't relay on SYSTEM header
		pPSParser->audio_block_size = block_size;
	}
	else
		return header_len;

	length += 3;
	//if ( ( pData[length+1] & 0xc0 )!= 0xc0 )
	//	return header_len;

	stream_id = pData[length];
	block_size = pData[length+1] & 0x1f;
	block_size <<= 8;
	block_size |= pData[length+2];

	if ( IS_VIDEO_STREAM_ID( stream_id) )
	{
		//pPSParser->video_stream_id = stream_id; //don't relay on SYSTEM header
		pPSParser->video_block_size = block_size;
	}
	else
	if ( IS_AUDIO_STREAM_ID( stream_id) )
	{
		//pPSParser->audio_stream_id = stream_id; //don't relay on SYSTEM header
		pPSParser->audio_block_size = block_size;
	}
	else
		return header_len;

	return header_len;
}

static int UnpackPadPack( const uint8_t* pbData, int nBytes )
{
	uint16_t Len;
	if ( nBytes < 6 ) return -6;
	Len = pbData[5] | (pbData[4] << 8);
	return Len+6;

}

uint8_t* GetDescriptor( const uint8_t *pData, int Bytes, uint8_t Tag, int *pLength );
static int ExtractSageInfo( SAGETV_PRIVATE_DATA *pSageTVPrivate, uint8_t* pData, int Bytes )
{
	uint8_t* ptr;
	uint8_t ctrl_bits;
	ptr = pData + 6;
	if ( memcmp( ptr, "SAGE", 4 ) )
		return 0;

	ptr += 4; Bytes -= 4;
	pSageTVPrivate->info_version = *ptr++;
	ctrl_bits =	*ptr++;
	pSageTVPrivate->video_track_num  =	*ptr++;
	pSageTVPrivate->audio_track_num  =	*ptr++;
	pSageTVPrivate->video_main_track =	*ptr++;
	pSageTVPrivate->audio_main_track = *ptr++;
	pSageTVPrivate->main_video_stream_type = *ptr++;
	pSageTVPrivate->main_audio_stream_type = *ptr++;
	Bytes -= 8;
	pSageTVPrivate->sage_info_embedded = 1;

	if ( pSageTVPrivate->info_version >= 4 && pSageTVPrivate->main_video_stream_type == H264_STREAM_TYPE )
	{
		int len;
		char* pH264Info = (char*)GetDescriptor( ptr, Bytes, SAGETV_H264_INFO, &len );
		if ( pH264Info != NULL )
			pSageTVPrivate->h264_frame_rate = pH264Info[2];
	}

	return 1;
}

static int ExtractSageMetaData( char **pSageTVMetaData, uint8_t* pData, int Bytes )
{
	uint8_t* ptr;
	ptr = pData + 6;
	if ( Bytes > 10 && memcmp( ptr, "META", 4 ) )
		return 0;

	*pSageTVMetaData = (char*)ptr;
	return 1;

}

#define  VIDEO_STREAM_DESC_TAG			02          //ISO 13818-1-200
#define  AUDIO_STREAM_DESC_TAG			03          //ISO 13818-1-200
#define  MPEG4_VIDEO_STREAM_DESC_TAG    27          //ISO 13818-1-200
#define  MPEG4__STREAM_DESC_TAG			28          //ISO 13818-1-200
#define  VIDEO_WINDOW_DESC_TAG			 8          //ISO 13818-1-200


static int UnpackPsmPack( PS_PARSER* pPSParser, const uint8_t* pbData, int nBytes )
{
	uint32_t pack_len, psm_info_len, es_map_len;
	uint32_t pos, bytes, len;

	if ( nBytes < 6 ) return 0;
	pack_len = pbData[5] | (pbData[4] << 8);
	if ( pack_len > 1018 || (int)pack_len > nBytes )
		return 0;

	if ( !(pbData[6] & 0x80 ) ) //skip not yet applicabe pack.
		return pack_len;

	pos = 8;
	psm_info_len = (pbData[8]<<8)|pbData[9];
	pos += 2;
	if ( psm_info_len > 0 )
	{
		int n = _MIN( psm_info_len, sizeof(pPSParser->psm_list.psm_info) );
		memcpy( pPSParser->psm_list.psm_info, pbData+pos, n ); 
	}
	pos += psm_info_len; //skip psm_info
	if ( pos > pack_len ) return 0;	
	
	es_map_len =  (pbData[pos] << 8) | pbData[pos+1];
	pos += 2;
	if ( pos + es_map_len > pack_len+6-4 ) return 0;	

	bytes = 0;
	pPSParser->psm_list.psm_num = 0;
	while ( bytes + 4 <= es_map_len && pPSParser->psm_list.psm_num < pPSParser->psm_list.psm_total_num )
	{
		int n;
		pPSParser->psm_list.psm[pPSParser->psm_list.psm_num].stream_type = pbData[pos];
		pPSParser->psm_list.psm[pPSParser->psm_list.psm_num].stream_id   = pbData[pos+1];
		pos += 2;
		bytes += 2;

		len  = pbData[pos] <<8 | (pbData[pos+1]);
		pos += 2;
		bytes += 2;
		
		if ( len )
		{
			n = _MIN( len, sizeof(pPSParser->psm_list.psm[pPSParser->psm_list.psm_num].stream_info) );
			memcpy( pPSParser->psm_list.psm[pPSParser->psm_list.psm_num].stream_info, pbData+pos, n );
		}

		if ( pPSParser->psm_list.psm_num < MAX_PES_STREAM - 1 ) 
			pPSParser->psm_list.psm_num++; 
		else
		{
			SageLog(( _LOG_ERROR, 2, TEXT("ERROR: psm table overflow (%d)"), MAX_PES_STREAM ));
		}

		pos += len;
		bytes += len;
	}
	
	//crc32 skip
	pos += 4;

	return pos;
}

///////////////////////////////// PUSH SECTION  //////////////////////////////////////////
int PushDataPSParser( PS_PARSER *pPSParser, const uint8_t* pData, int nSize, int* pExpectedBytes )
{
	int used_bytes = 0, bytes;
	const uint8_t *p;
	uint8_t code;

	pPSParser->block_count++;

	if ( pPSParser->block_count == 1 )
	{
		PostStatusMessage( pPSParser, "STREAM START" );
	}

	p = pData;
	*pExpectedBytes = 0;
	//processing data here
	while ( nSize > 0 && used_bytes < nSize )
	{
		if ( pPSParser->command & (PS_PARSER_CMD_ABORT|PS_PARSER_CMD_ZERO|PS_PARSER_CMD_RESET ) )
			break;

		p = SearchPSStart( pData+used_bytes, nSize-used_bytes );
		if ( p == NULL )
		{
			if ( nSize > 0 && used_bytes == 0 )
			{
				pPSParser->bad_blocks++;
				//SageLog(( _LOG_TRACE, 3, TEXT( "ERROR: No MEPG start code found in a block (%d bytes), data is dropped" ), nSize  ));
			}
			if ( used_bytes == 0 )
				used_bytes = nSize;

			break;
		}

		//if ( (int)(p-(pData+used_bytes) ) )
		//{
		//	pPSParser->bad_blocks++;
		//	//SageLog(( _LOG_TRACE, 3, TEXT( "WARNING: data missing re-sync, drop data %d" ), (int)(p-(pData+used_bytes)) ));;
		//}

		code = p[3];
		used_bytes += (int)(p-(pData+used_bytes));
		pPSParser->used_bytes += (int)(p-(pData+used_bytes));

		if ( IS_PES_STREAM_ID(code) )
		{
			PES pes={0};
			int ret;
			pPSParser->pack_type = PES_PACKET_TYPE;
			if ( ( ret=ReadPESHeader( p, nSize-used_bytes, &pes ) ) > 0 )
			{
				//for fast seeking PTS only
				if ( pPSParser->dumper.pes_dumper != NULL )
				{
					ret = DumpPESDHeader( pPSParser, p, pes.packet_length+6, &pes );
					if ( ret == 0 && used_bytes + pes.packet_length + 6 > nSize )
					{
						*pExpectedBytes = (used_bytes + pes.packet_length + 6)-nSize;
						break;
					}
				}
				else
				{ 
					if ( used_bytes + pes.packet_length + 6 > nSize )
					{
						*pExpectedBytes = (used_bytes + pes.packet_length + 6)-nSize;
						break;
					}

					if ( pes.packet_length )
						ret = ProcessPESData( pPSParser, p, pes.packet_length+6, &pes );
					else
						ret = ProcessPESData( pPSParser, p, nSize-used_bytes, &pes );
				}
				used_bytes += pes.packet_length + 6;
				pPSParser->used_bytes += pes.packet_length + 6;
			} else
			{
				if ( ret == 0 && nSize-used_bytes > 0 ) //data is too short
					break;
				
				used_bytes += 4;
				pPSParser->used_bytes += 4;
				SageLog(( _LOG_TRACE, 3, TEXT( "WARNING: drop 4 bytes to resync PES (%d)" ), ret  ));;
			}
		
		} else
		if ( IS_PACK_START_CODE(code) )
		{
			uint32_t demux;
			ULONGLONG scr;
			bytes = UnpackPackHeader( p, nSize-used_bytes, &demux, &scr );
			if ( bytes > 0 )
			{
				pPSParser->pack_type = PACK_PACKET_TYPE;
				PTSLog(( 2, scr/300, 0, pPSParser->used_bytes, 0 )); 

				if ( pPSParser->dumper.scr_dumper )
				{
					PCR_DATA pcr_data;
					pcr_data.pcr = scr;
					pcr_data.pid = 0;
					pcr_data.ts_packet_counter = (uint32_t)pPSParser->used_bytes;
					pcr_data.container = &pPSParser->es_streams;
					pPSParser->dumper.scr_dumper( pPSParser->dumper.scr_dumper_context, (void*)&pcr_data, sizeof(PCR_DATA) );
				}

				pPSParser->scr = scr;
				pPSParser->scr_cue = pPSParser->used_bytes;

				if ( used_bytes + bytes > nSize )
					break;
				used_bytes += bytes;
				pPSParser->used_bytes += bytes;;
			} else
			{
				if ( bytes == 0 )
				{
					used_bytes += 4;
					pPSParser->used_bytes += 4;
				} else
				{
					*pExpectedBytes = -bytes;
					break;
				}
			}
		} else
		if ( IS_SYSTEM_START_CODE(code) )
		{
			bytes = DecodeSystemHeader( pPSParser, p, nSize-used_bytes );
			if ( bytes > 0 )
			{
				pPSParser->pack_type = SYSTEM_PACKET_TYPE;
				//ZQ check buffer size and request buffer
				if ( used_bytes + bytes > nSize )
					break;
				used_bytes += bytes;
				pPSParser->used_bytes += bytes;
			} else
			{
				if ( bytes == 0 )
				{
					used_bytes += 4;
					pPSParser->used_bytes += 4;
				} else
				{
					*pExpectedBytes = -bytes;
					break;
				}
			}

		} else
		if ( IS_PADDING_START_CODE(code) )
		{
			bytes = UnpackPadPack( p, nSize-used_bytes );
			if ( bytes > 0 )
			{ 
				SAGETV_PRIVATE_DATA sagetv_private_data={0};
				uint16_t last_pack_type = pPSParser->pack_type;
				pPSParser->pack_type = PADDING_PACKET_TYPE;
				//extract embedded Sage infomation
				if ( used_bytes + bytes > nSize )
				{
					*pExpectedBytes = (used_bytes + bytes ) - nSize;
					break;
				}
				if ( ExtractSageInfo( &sagetv_private_data, (uint8_t*)pData+used_bytes, nSize-used_bytes  ) )
				{
					pPSParser->sagetv_private_data = sagetv_private_data;
					SageLog(( _LOG_TRACE, 3, TEXT("**** SageTV Recording Data ver. %d (v:%d a:%d)****"), 
						sagetv_private_data.info_version, sagetv_private_data.video_track_num, sagetv_private_data.audio_track_num ));
				}
				if (  END_PACKET_TYPE ==last_pack_type ) 
				{
					char *meta_data_p = NULL;
					if ( ExtractSageMetaData( &meta_data_p, (uint8_t*)pData+used_bytes, nSize-used_bytes ) )
					{
						SageLog(( _LOG_TRACE, 3, TEXT("**** SageTV Metadata found %d ****"), meta_data_p ));
					}
				}
				used_bytes += bytes;
				pPSParser->used_bytes += bytes;
			} else
			{
				if ( bytes == 0 )
				{
					used_bytes += 4;
					pPSParser->used_bytes += 4;
				} else
				{
					*pExpectedBytes = -bytes;
					break;
				}
			}
		} else
		if ( IS_PSM_START_CODE(code) )
		{
			bytes = UnpackPsmPack( pPSParser, p, nSize-used_bytes );
			if ( bytes > 0 )
			{	
				int i;
				pPSParser->pack_type = PSM_PACKET_TYPE;
				if ( used_bytes + bytes > nSize )
					break;
				SageLog(( _LOG_TRACE, 3, TEXT("**** PSM found, total stream %d ****"), pPSParser->psm_list.psm_num ));
				for ( i = 0; i<pPSParser->psm_list.psm_num; i++ )
					SageLog(( _LOG_TRACE, 3, TEXT("\t %d stream_id:0x%02x type:0x%02x"), i,
							 pPSParser->psm_list.psm[i].stream_id, pPSParser->psm_list.psm[i].stream_type ));
				used_bytes += bytes;
				pPSParser->used_bytes += bytes;
			} else
			{
				used_bytes += 4;
				pPSParser->used_bytes += 4;
			}
		} else
		if ( IS_END_CODE(code) )
		{
			//PostStatusMessage( pPSParser, "STREAM END (slot:0)" );
			pPSParser->pack_type = END_PACKET_TYPE;
			pPSParser->state &= ~PARSING_INFO;

			used_bytes += 4;
			pPSParser->used_bytes += 4;
		} else
		{
			pPSParser->pack_type = UNKNOWN_PACKET_TYPE;
			used_bytes += 4;
			pPSParser->used_bytes += 4;
		} 

	}

	if ( pPSParser->state & PARSING_INFO )
	{
		if ( CheckPSParseDone(  pPSParser ) )
		{
			PostStatusMessage( pPSParser, "STREAM READY (slot:0)" );
			pPSParser->state &= ~PARSING_INFO;
		}
	}
	if ( pPSParser->command & PS_PARSER_CMD_ABORT )
	{
		pPSParser->command &= ~PS_PARSER_CMD_ABORT; 
		used_bytes = nSize;
	}

	if ( pPSParser->command & PS_PARSER_CMD_ZERO  )
	{
		PSParserZero( pPSParser );
		pPSParser->command &= ~PS_PARSER_CMD_ZERO; 
		used_bytes = nSize;
	}

	if ( pPSParser->command & PS_PARSER_CMD_RESET  )
	{
		ResetPSParser( pPSParser );
		pPSParser->command &= ~PS_PARSER_CMD_RESET; 
		used_bytes = nSize;
	}

	return used_bytes;
}

///////////////////////////////// PUSH SECTION  //////////////////////////////////////////

inline static void DumpESBlockOfPS( PS_PARSER *pPSParser, TRACK* pTrack )
{
	if ( pTrack->command == STOP_FILL_DATA )
		return;
	pTrack->command = DATA_READY;
	pPSParser->dumper.esblock_dumper( pPSParser->dumper.esblock_dumper_context, pTrack, sizeof( TRACK ) );
	ASSERT( pTrack->command == START_FILL_DATA || pTrack->command == STOP_FILL_DATA );
}


static int ProcessPESData( PS_PARSER *pPSParser, const uint8_t* pData, int nSize,  PES *pPES )
{
	ES_ELEMENT  *es_element_ptr = NULL;
	AV_ELEMENT  *av_element_ptr = NULL;
	TRACK		*track = NULL;
	int i, ret=0;
	
	ReadPESSubId( pPES, pData, nSize );
	if ( pPSParser->pts_offset )
	{
		if ( pPES->has_pts )
			pPES->pts += pPSParser->pts_offset;
		if ( pPES->has_dts )
			pPES->dts += pPSParser->pts_offset;
	}


	//look up es_element in track/streams
	for ( i = 0; i <pPSParser->es_streams.num_stream; i++ )
	{
		if ( pPSParser->es_streams.es_element[i].pes.pes_sub_id == pPES->pes_sub_id )
		{
			es_element_ptr = &pPSParser->es_streams.es_element[i];
			es_element_ptr->pes = *pPES;
			es_element_ptr->scr = pPSParser->scr;
			break;
		}
	}

	// es_element is not found, put into a track/streams
	if (  es_element_ptr == NULL )
	{
		ES_ELEMENT  es_element={0};
		es_element.pes = *pPES;

		if ( AnylyzePSESElement( &es_element, &pPSParser->psm_list, pData, nSize ) )
		{
			es_element.es_id = CreateESId( pPES->stream_id, pPES->pes_sub_id );
			es_element.es_type = pPES->es_type;
			if ( pPSParser->es_streams.num_stream < pPSParser->es_streams.total_streams )
			{
				pPSParser->es_streams.es_element[ pPSParser->es_streams.num_stream ] = es_element;
				es_element_ptr = &pPSParser->es_streams.es_element[ pPSParser->es_streams.num_stream ];
				es_element_ptr->scr = pPSParser->scr;
				pPSParser->es_streams.es_element[ pPSParser->es_streams.num_stream ].channel_index = pPSParser->es_streams.num_stream;
				pPSParser->es_streams.num_stream++;
				pPSParser->tracks->number_track++;
				_prints_es_elmnt( es_element_ptr, 0, 0 );

			}
			else
			{
				SageLog(( _LOG_TRACE, 3, TEXT("ERROR: tracks/streams is full (%d) a stream is dropped.(stream_id:%d sub_id"), 
						 pPSParser->es_streams.total_streams, pPES->stream_id, pPES->pes_sub_id ));
				return 0;
			}
		} 
	}
	
	//get av information
	if (  es_element_ptr != NULL )
	{
		int index = es_element_ptr->channel_index;
		track = &pPSParser->tracks->track[index];
		track->es_blocks_counter++;
		track->processed_bytes += nSize;

		av_element_ptr = &pPSParser->av_streams.av_element[ index ];

		//there is a unknown av format, go to parsing it
		if ( pPSParser->state & PARSING_INFO )
		{
			if ( av_element_ptr->content_type == 0  )
			{
				int media_type = pPSParser->sagetv_private_data.sage_info_embedded ? SAGETV_TV_RECORDING : UNKNOWN_MEDIA;
				ret = AnylyzeAVElement( av_element_ptr, es_element_ptr, NULL, pData, nSize, media_type ); 
				if ( ret && av_element_ptr->format_fourcc != 0x0 )
				{
					av_element_ptr->stream_index = index;
					_prints_av_elmnt( av_element_ptr, 0, track->processed_bytes );
				} else
				if ( es_element_ptr->content_type == SUBTITLE_DATA )
				{
					SUBTITLE sub;
					uint8_t* pDesc=GetPSESDesc( es_element_ptr, &pPSParser->psm_list );
					if ( pDesc && ParseDVBSubtitleDesc( &sub, pDesc, sizeof( pPSParser->psm_list.psm->stream_info) ) )
					{
						sub.type = 1;
						av_element_ptr->d.s.sub = sub;
						av_element_ptr->content_type = SUBTITLE_DATA;
						av_element_ptr->format_fourcc = SAGE_FOURCC( "SUB " );
						es_element_ptr->content_type = SUBTITLE_DATA;
						es_element_ptr->format_fourcc = SAGE_FOURCC( "SUB " );
						_prints_av_elmnt( av_element_ptr, 0, track->processed_bytes );
					}

				}

				if ( CheckPSParseDone(  pPSParser ) )
				{
					PostStatusMessage( pPSParser, "STREAM READY (slot:0)" );
					pPSParser->state &= ~PARSING_INFO;
				}
			}
		}
	}

	//dump data
	if ( track != NULL && pPSParser->dumper.esblock_dumper && pPES->packet_length )
	{
		//ASSERT( pPES->packet_length );
		track->buffer_start = (uint8_t*)pData;
		track->buffer_size  = 6+ pPES->packet_length;
		track->es_data_start = (uint8_t*)pData;
		track->es_data_bytes = 6+ pPES->packet_length;

		track->buffer_index = 0xffff;
		track->group_start  |= ES_GROUP_START;
		track->cue = pPSParser->scr;
		track->start_cue = pPSParser->scr;

		DumpESBlockOfPS( pPSParser, track );
	}
	
	//dumpdata here
	
	return ret;

}

static int DumpPESDHeader( PS_PARSER *pPSParser, const uint8_t* pData, int nSize,  PES *pPES )
{
	ES_ELEMENT  *es_element_ptr = NULL;
	//AV_ELEMENT  *av_element_ptr = NULL;
	TRACK		*track = NULL;
	int			index;
	int i;
	
	ReadPESSubId( pPES, pData, nSize );

	//look up es_element in track/streams
	for ( i = 0; i <pPSParser->es_streams.num_stream; i++ )
	{
		if ( pPSParser->es_streams.es_element[i].pes.pes_sub_id == pPES->pes_sub_id )
		{
			es_element_ptr = &pPSParser->es_streams.es_element[i];
			break;
		}
	}

	// es_element is not found, put into a track/streams
	if (  es_element_ptr == NULL )
		return -1;
	
	index = es_element_ptr->channel_index;
	track = &pPSParser->tracks->track[index];

	if ( es_element_ptr->pes.es_type != pPES->es_type )
		return -2;

	es_element_ptr->pes = *pPES;
	es_element_ptr->scr = pPSParser->scr;
	if ( track != NULL && pPSParser->dumper.pes_dumper )
		pPSParser->dumper.pes_dumper( pPSParser->dumper.pes_dumper_context, track, sizeof(TRACK) );

	return 0;
}

void QueuePSParserAbort( PS_PARSER *pPSParser )
{
	pPSParser->command |= PS_PARSER_CMD_ABORT;
}

void QueuePSParserZero( PS_PARSER *pPSParser )
{
	pPSParser->command |= PS_PARSER_CMD_ZERO;
}

void QueuePSParserReset( PS_PARSER *pPSParser )
{
	pPSParser->command |= PS_PARSER_CMD_RESET;
}

void ResetPSParserCommand( PS_PARSER *pPSParser )
{
	pPSParser->command = 0;
}

void PSParserZero( PS_PARSER *pPSParser )
{
	int j;
	for ( j = 0; j < pPSParser->tracks->number_track; j++ )
	{
		pPSParser->tracks->track[j].es_data_bytes = 0;
		pPSParser->tracks->track[j].es_blocks_counter = 0;
		pPSParser->tracks->track[j].group_start = 0;
		pPSParser->tracks->track[j].command = 0;
		pPSParser->tracks->track[j].processed_bytes = 0;
	}

		//pTSParser->slot[i].time_stamp.last_scr = 0;
		//pTSParser->slot[i].time_stamp.last_count = 0;
		//pTSParser->slot[i].time_stamp.last_ts = 0;
	
	//zero statistic
	pPSParser->used_bytes = 0;
	pPSParser->input_blocks = 0;
	pPSParser->bad_blocks = 0;
	pPSParser->block_count = 0;
	pPSParser->command = 0;
}


static void CreateSubtitileStream( PS_PARSER *pPSParser, PSM_LIST *pPSMList )
{
	uint8_t  stream_id;
	uint16_t stream_type;
	uint8_t* stream_inf;
	int i, j;
	ES_ELEMENT  es_element;

	//look up stream type in psm if psm is available
	for ( i = 0; i<pPSMList->psm_num; i++ )
	{
		stream_type = 0;
		stream_id = pPSMList->psm[i].stream_id;
		stream_type = pPSMList->psm[i].stream_type;
		//if ( IS_SUBTITLE_STREAM_ID( stream_id ) && stream_type == 0x06 )
		if ( stream_id >= 0x20 && stream_id <= 0x3f && stream_type == 0x06 )
		{
			memset( &es_element, 0, sizeof(es_element) );

			stream_inf  = pPSMList->psm[i].stream_info;
			es_element.language_code = GetLanguageCode( pPSMList->psm[i].stream_info, 
										   sizeof(pPSMList->psm[i].stream_info) );
			es_element.pes.stream_id = 0xbd;
			es_element.pes.pes_sub_id = stream_id;
			es_element.private_bytes = 1;
			es_element.private_data[0] = stream_id;
			es_element.es_id = CreateESId( es_element.pes.stream_id, es_element.pes.pes_sub_id );

			//check if stream was created
			for ( j = 0; j <pPSParser->es_streams.num_stream; j++ )
				if ( pPSParser->es_streams.es_element[j].pes.pes_sub_id == es_element.pes.pes_sub_id )
					break;
			if ( j < pPSParser->es_streams.num_stream )
				continue;

			es_element.content_type = SUBTITLE_DATA;
			es_element.format_fourcc = SAGE_FOURCC( "SUB " );

			if ( pPSParser->es_streams.num_stream < pPSParser->es_streams.total_streams )
			{
				ES_ELEMENT *es_element_ptr;
				AV_ELEMENT *av_element_ptr;
				SUBTITLE sub={0};
				pPSParser->es_streams.es_element[ pPSParser->es_streams.num_stream ] = es_element;
				es_element_ptr = &pPSParser->es_streams.es_element[ pPSParser->es_streams.num_stream ];
				av_element_ptr = &pPSParser->av_streams.av_element[ pPSParser->es_streams.num_stream ];
				es_element_ptr->scr = pPSParser->scr;
				es_element_ptr->channel_index = pPSParser->es_streams.num_stream;
				pPSParser->es_streams.num_stream++;
				pPSParser->tracks->number_track++;
				_prints_es_elmnt( es_element_ptr, 0, 0 );

				if ( stream_inf && ParseDVBSubtitleDesc( &sub, stream_inf, sizeof( pPSParser->psm_list.psm->stream_info) ) )
				{
					sub.type = 1;
					av_element_ptr->d.s.sub = sub;
					av_element_ptr->content_type = SUBTITLE_DATA;
					av_element_ptr->format_fourcc = SAGE_FOURCC( "SUB " );
					es_element_ptr->content_type = SUBTITLE_DATA;
					es_element_ptr->format_fourcc = SAGE_FOURCC( "SUB " );
					_prints_av_elmnt( av_element_ptr, 0, 0 );
				}
			}
		}
	}

	//if psm not availabe, create subtitle anyway
	for ( j = 0; j <pPSParser->es_streams.num_stream; j++ )
	{
		if ( pPSParser->es_streams.es_element[j].content_type == SUBTITLE_DATA &&
			 pPSParser->av_streams.av_element[j].content_type == 0 )
		{
			SUBTITLE sub={0};
			ES_ELEMENT *es_element_ptr;
			AV_ELEMENT *av_element_ptr;
			es_element_ptr = &pPSParser->es_streams.es_element[ j ];
			av_element_ptr = &pPSParser->av_streams.av_element[ j ];
			sub.type = 1;
			av_element_ptr->d.s.sub = sub;
			av_element_ptr->content_type = SUBTITLE_DATA;
			av_element_ptr->format_fourcc = SAGE_FOURCC( "SUB " );
			es_element_ptr->content_type = SUBTITLE_DATA;
			es_element_ptr->format_fourcc = SAGE_FOURCC( "SUB " );
			_prints_av_elmnt( av_element_ptr, 0, 0 );

		}
	}

}

static int CheckPSParseDone( PS_PARSER *pPSParser )
{
	int i, total_es_blocks = 0;
	int drop_parsing_num = 0, parsing_done_num = 0;
	int video_ready = 0, audio_ready = 0, subtitle_ready = 0;
	TRACK *track = NULL;
	if ( pPSParser->used_bytes < 1024*1024 && pPSParser->sagetv_private_data.sage_info_embedded == 0 )
		return 0;

	for ( i = 0; i<pPSParser->es_streams.num_stream; i++ )
	{
		track = &pPSParser->tracks->track[i];
		total_es_blocks += track->es_blocks_counter;
		if ( track->av_elmnt->content_type != 0 )
		{
			if ( track->av_elmnt->content_type == VIDEO_DATA )
				video_ready++;
			else
			if ( track->av_elmnt->content_type == AUDIO_DATA )
				audio_ready++;
			else
			if ( track->av_elmnt->content_type == SUBTITLE_DATA )
				subtitle_ready++;

			parsing_done_num++;
		}
		else
		if ( track->es_blocks_counter > DROP_PARSING_THRESHOLD )
			parsing_done_num++;
	}


	if ( pPSParser->sagetv_private_data.sage_info_embedded )
	{
		int total_stream = pPSParser->sagetv_private_data.video_track_num + pPSParser->sagetv_private_data.audio_track_num;
		if ( parsing_done_num >= total_stream 
			|| ( pPSParser->sagetv_private_data.info_version >= 5 && 
			     pPSParser->psm_list.psm_num && parsing_done_num >= pPSParser->psm_list.psm_num )  )
		{

			CreateSubtitileStream( pPSParser, &pPSParser->psm_list );

			SageLog(( _LOG_TRACE, 3, TEXT("SageTV recording: video:%d auido:%d; video-main:%d auido-main:%d (v:%d a:%d s:%d)."),
				                    pPSParser->sagetv_private_data.video_track_num,
									pPSParser->sagetv_private_data.audio_track_num,
									pPSParser->sagetv_private_data.video_main_track,
									pPSParser->sagetv_private_data.audio_main_track,
									video_ready, audio_ready, subtitle_ready ));

			SageLog(( _LOG_TRACE, 3, TEXT("Stream Ready pos:%d"), (uint32_t)pPSParser->used_bytes ));
			return 1;
		} else
		{
			if ( pPSParser->block_count > pPSParser->empty_sub_stream_threshold ) 
			{
				if ( pPSParser->sagetv_private_data.info_version >= 5 )
				{
					CreateSubtitileStream( pPSParser, &pPSParser->psm_list );

					if ( pPSParser->tracks->track[pPSParser->sagetv_private_data.video_main_track].av_elmnt->format_fourcc &&
						 pPSParser->tracks->track[pPSParser->sagetv_private_data.audio_main_track].av_elmnt->format_fourcc )
					{
						SageLog(( _LOG_TRACE, 3, TEXT("SageTV recording: video:%d auido:%d; video-main:%d auido-main:%d (v:%d a:%d s:%d)."),
				                    pPSParser->sagetv_private_data.video_track_num,
									pPSParser->sagetv_private_data.audio_track_num,
									pPSParser->sagetv_private_data.video_main_track,
									pPSParser->sagetv_private_data.audio_main_track,
									video_ready, audio_ready, subtitle_ready  ));
						SageLog(( _LOG_TRACE, 3, TEXT("WARNING: found %d stream of total %d"), parsing_done_num, total_stream ));
						SageLog(( _LOG_TRACE, 3, TEXT("Stream Ready pos:%d"), (uint32_t)pPSParser->used_bytes ));
						return 1;
					}

				} 
			}
		}
	} else
	{
		int drop_track_num = 0;
		//if it's not sagetv recording.

		CreateSubtitileStream( pPSParser, &pPSParser->psm_list );

		for ( i = 0; i<pPSParser->es_streams.num_stream; i++ )
		{
			track = &pPSParser->tracks->track[i];
			if ( track->es_elmnt->format_fourcc == 0 && 
				 track->es_elmnt->content_type == UNKNOWN_DATA )
			{
				if ( track->es_blocks_counter > 16 && video_ready && audio_ready )
					drop_track_num++;
			}
		}
		
		if (  drop_parsing_num + parsing_done_num + drop_track_num >= pPSParser->es_streams.num_stream )
		{
			if ( drop_track_num )
			{
				for ( i = 0; i<pPSParser->es_streams.num_stream; i++ )
				{
					track = &pPSParser->tracks->track[i];
					if ( track->es_elmnt->format_fourcc == 0 && 
						 track->es_elmnt->content_type == UNKNOWN_DATA )
					{
						SageLog(( _LOG_TRACE, 3, TEXT("Drop unknown stream:%d 0x%x after (blocks:%d)"), 
							i, track->es_elmnt->es_id, track->es_blocks_counter  ));
					}
				}
			}
			CreateSubtitileStream( pPSParser, &pPSParser->psm_list );

			SageLog(( _LOG_TRACE, 3, TEXT("Stream Ready pos:%d"), (uint32_t)pPSParser->used_bytes ));
			return 1;
		}
	}
	return 0;
}

int PickupMainTrack( PS_PARSER *pPSParser, TRACKS* pTracks )
{
	int i;
	if ( pPSParser->sagetv_private_data.info_version == 0x0 || pPSParser->sagetv_private_data.info_version == 0xff )
		return 0;

	for ( i = 0; i<pTracks->total_track; i++ )
	{
		if ( pTracks->track[i].av_elmnt->content_type == AUDIO_DATA )
		{
			if ( (pTracks->track[i].es_elmnt->es_id & 0xff) == pPSParser->sagetv_private_data.audio_main_track )  //### main audio/video
				pTracks->main_audio_index = i;
		} else
		if ( pTracks->track[i].av_elmnt->content_type == VIDEO_DATA )
		{
			if ( (pTracks->track[i].es_elmnt->es_id & 0xff) == pPSParser->sagetv_private_data.video_main_track )  //### main audio/video
				pTracks->main_video_index = i;
		}
	}
	return 0;
}

static int CreateESId( uint8_t cStreamId, uint8_t cPESSubId )
{
	if ( IS_VIDEO_STREAM_ID( cStreamId ) )
	{
		return ( cStreamId << 8 )|(cStreamId & 0x0f);
	} else
	if ( IS_AUDIO_STREAM_ID( cStreamId ) )
	{
		return ( cStreamId << 8 )|(cStreamId & 0x1f);
	} else
	if ( IS_PRIVATE1_STREAM_ID( cStreamId ) )
	{
		if ( ( cPESSubId & 0xf8 )== 0x88 )  //DTS DTSM
			return ( cStreamId << 8 ) | (cPESSubId &0x07);
		if ( ( cPESSubId & 0xf8 )== 0x80 )  //AC3, AC3T, AC3E
			return ( cStreamId << 8 ) | (cPESSubId &0x0f);
		if ( ( cPESSubId & 0xf8 )== 0xE8 )  //AAC
			return ( cStreamId << 8 ) | (cPESSubId &0x07);
		if ( ( cPESSubId & 0xf0 )== 0xA0 )  //LPCM
			return ( cStreamId << 8 ) | (cPESSubId &0x0F);
		if ( ( cPESSubId & 0xf0 )== 0x20 )  //SUB
			return ( cStreamId << 8 ) | (cPESSubId &0x0F);
		if ( ( cPESSubId & 0xf0 )== 0x10 )  //TELX
			return ( cStreamId << 8 ) | (cPESSubId &0x0F);

		return ( cStreamId << 8 ) | (cPESSubId &0x0F);
	}

	return ( cStreamId << 8 ) | (cPESSubId &0x0F);;	
}



int CheckPSFormat( const uint8_t* pData, int nSize)
{
	int score = 0, new_score=0, state = 0;
	int used_bytes = 0, bytes;
	const uint8_t *p;
	uint8_t code;
	int pack_packet_present = 0, psm_packet_present = 0, sysme_packet_present = 0;

	p = pData;
	//processing data here
	while ( nSize > 0 && used_bytes < nSize )
	{
		p = SearchPSStart( pData+used_bytes, nSize-used_bytes );
		if ( p == NULL )
		{
			break;
		}
		code = p[3];
		used_bytes += (int)(p-(pData+used_bytes));
		
		if ( IS_PACK_START_CODE(code) )
		{
			if ( state == 2 )
				new_score += 2;
			else
			if ( state == 3 )
				new_score += 3;
			state = 1;
			used_bytes += 4;
			pack_packet_present=1;
		} else
		if ( IS_SYSTEM_START_CODE(code) )
		{
			if ( state == 1 )
				new_score = 10;
			used_bytes += 4;
			state = 2;
			sysme_packet_present=1;
		} else
		if ( IS_PADDING_START_CODE(code) )
		{
			bytes = UnpackPadPack( p, nSize-used_bytes );
			if ( bytes > 0 )
			{
				SAGETV_PRIVATE_DATA sagetv_private_data;
				//extract embedded Sage infomation
				if ( ExtractSageInfo( &sagetv_private_data, (uint8_t*)pData+used_bytes, nSize-used_bytes  ) )
				{
					score = 900;
				}
				used_bytes += bytes;
			} else
				used_bytes += 4;
		} else
		if ( IS_PES_STREAM_ID(code) )
		{
			PES pes;
			if ( ReadPESHeader( p, nSize-used_bytes, &pes ) > 0 )
			{
				if ( state == 1 ) //the last is pack
					score = score * 3 + new_score;
				else
				if ( state == 2 ) //the last is system
					score = score * 4 + new_score*10;
				else
				if ( state == 3 ) //the last is itself
				{
					if ( pack_packet_present + psm_packet_present + sysme_packet_present >= 2 )
						score = score * 4 + 10;
					if ( pack_packet_present + psm_packet_present + sysme_packet_present == 0 )
						score = 1;
				} 
				used_bytes += pes.packet_length + 6;

			} else
			{
				if ( state == 1 )
					score = score + new_score;
				else
					score = 0;
				used_bytes += 4;
			}
			new_score = 0;
			state = 3;
		} else
		if ( code == PSM_START_CODE )
		{
			if ( state == 2 )
			{
				new_score += 10;
				score += 20;
			}
			used_bytes += 4;
			psm_packet_present = 1;
		} else
		{
			used_bytes += 4;
			state = 0;
		} 

		if ( score > 1000 ) 
		{
			score = 1000;
			break;
		}
	}

	if ( score <= 2 ) score = 0;
	return score;
}

static void PostStatusMessage( PS_PARSER *pPSParser, char* pMessageText )
{
	if ( pPSParser->dumper.message_dumper != NULL )
	{
		MESSAGE_DATA message={0};
		strncpy( message.title, "STATUS", sizeof(message.title) );
		message.message = (uint8_t*)pMessageText;
		message.message_length = (uint16_t)strlen(pMessageText)+1 ;
		message.buffer = (uint8_t*)pMessageText;
		message.buffer_length = message.message_length ;
	
		pPSParser->dumper.message_dumper( pPSParser->dumper.message_dumper_context, &message, sizeof(message) );
	}
}

ULONGLONG PSDataUsedBytes( PS_PARSER *pPSParser )
{
	return pPSParser->used_bytes;
}

uint32_t PSStreamBoundRate( PS_PARSER* pParser )
{
	return pParser->demux_bound;
}

char* _pes_header_( uint8_t* p )
{
	static char buf[38];
	snprintf( buf, sizeof(buf), "ES:%02x %02x %02x %02x %02x %02x %02x %2x %02x ", 
		                       p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8] );
	return buf;
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
static void _s_(uint8_t*data, int size)
{
	uint8_t pat1[]= { 0x2f, 0xaf, 0xb5, 0x3d, 0x41, 0xcf, 0x2f, 0x49, 0x4d, 0x5e, 0x38, 0xdb, 0x92, 0x7c, 0xcb, 0x9a, 0xf3, 0xab, 0xe4,
		0x1e, 0xa9, 0x2c, 0x07, 0xb0, 0x3e, 0x7e, 0xec, 0x57, 0xb9, 0x1a, 0xd3, 0x0d, 0x26 };
	if ( _search_data_( pat1, sizeof(pat1)-1, data, size ) )
	{
		printf( "STOP" );
	}
}
*/
/*{
	uint8_t pat1[]= { 0x0, 0x0, 0x01 ,0xe0 ,0x00 ,0x00 ,0x81 ,0xc0 ,0x0b ,0x33 ,0xc6 ,0x33 ,0x77 ,0xf1 ,0x13 ,0xc6 ,0x33 ,0x23 ,0x91 ,0xff };
	uint8_t pat2[]= { 00, 00, 01, 0xb3 };
	if ( _search_data_( pat2, 4, data, 188 ) && _search_data_( pat1, 7, data, 188 ) )
	{
		printf( "STOP" );
	}
} */

/*
if ( pPES->stream_id == 0xe0 )
{
	uint8_t *np = track->es_data_start+track->es_data_bytes;
	if ( np[0] || np[1] || np[2] != 0x01 || np[3] != 0xba && np[3] != 0xbe )
		printf( "STOP" );

}

*/

/*
     0xbc    = "program stream map",
     0xbd    = "private stream 1",
     0xbe    = "padding stream",
     0xbf    = "private stream 2",
     0xc0 - 0xdf = "audio stream",
     0xe0 - 0xef = "video stream",
     0xf0    = "ECM stream",
     0xf1    = "EMM stream",
     0xf2    = "DSMCC stream",
     0xf3    = "13522 stream",
     0xf4    = "H.222.1 type A",
     0xf5    = "H.222.1 type B",
     0xf6    = "H.222.1 type C",
     0xf7    = "H.222.1 type D",
     0xf8    = "H.222.1 type E",
     0xf9    = "ancillary stream",
     0xfa - 0xfe = "reserved data stream",
     0xff    = "program stream directory",
*/

		/*{
			unsigned language_code;
			uint8_t audio_type;
			language_code = GetLanguageCode( &pbData[pos], len );
			if ( language_code )
			{
				audio_type = GetAudioType( &pbData[pos], len );
				
			}
		} */
