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
#include <memory.h>
#include <assert.h>
#include "PSParser.h"

static int DecodeSystemHeader( PS_PARSER* pParser, char* pData, int Bytes );

static int ProcessPacketData( PS_PARSER* pParser, char* pData, int Size, bool bPESStart );
static int ProcessNewPacketData( PS_PARSER* pParser, char* pData, int Size );

static int AddStreamIndex( PS_PARSER* pParser, unsigned char streamid, unsigned char subid );
static int GetStreamIndex( PS_PARSER* pParser, unsigned char streamid, unsigned char subid );
static int IsProprietaryStream( PS_PARSER* pParser, unsigned char streamid );
static int MarkProprietaryStream( PS_PARSER* pParser, unsigned char streamid );
static int ParseH264VideoStreamInf( void** stream_info, const unsigned char* desc );

static int DecodeSystemHeader( PS_PARSER* pParser, char* pHeader, int Bytes )
{
	int length = 0, header_len;
	unsigned long demux=0;
	unsigned char stream_id;
	unsigned short block_size = 0;
	unsigned char* pData;

	pData = (unsigned char*)pHeader;
	if ( !IsMPEG2StartCode( pData, SYSTEM_HEADER_START_CODE ) )
		return 0;

	header_len = ( pData[4] << 8 )|pData[5];
	header_len += 6;

	demux = pData[6] & 0x7f;
	demux <<= 8;
	demux |= pData[7];
	demux <<= 7;
	demux |= ( pData[8] >> 1 ) & 0x7f;
	demux *= 50;

	length = 12;

	stream_id = pData[length];
	pParser->demux_bound = demux;


	if ( ( pData[length+1] & 0xc0 ) != 0xc0 )
		return header_len;

	block_size = pData[length+1] & 0x1f;
	block_size <<= 8;
	block_size |= pData[length+2];

	if ( IsVideoStreamId( stream_id) )
	{
		//pParser->video_stream_id = stream_id; //don't relay on SYSTEM header
		pParser->video_block_size = block_size;
	}
	else
	if ( IsAudioStreamId( stream_id) )
	{
		//pParser->audio_stream_id = stream_id; //don't relay on SYSTEM header
		pParser->audio_block_size = block_size;
	}
	else
		return header_len;

	stream_id = pData[length];
	if ( ( pData[length+1] & 0xc0 )!= 0xc0 )
		return header_len;

	length += 3;
	block_size = pData[length+1] & 0x1f;
	block_size <<= 8;
	block_size |= pData[length+2];

	if ( IsVideoStreamId( stream_id) )
	{
		//pParser->video_stream_id = stream_id; //don't relay on SYSTEM header
		pParser->video_block_size = block_size;
	}
	else
	if ( IsAudioStreamId( stream_id) )
	{
		//pParser->audio_stream_id = stream_id; //don't relay on SYSTEM header
		pParser->audio_block_size = block_size;
	}
	else
		return header_len;

	return header_len;
}

bool ExtractSageInfo( PS_PARSER* pParser, unsigned char* pData, int Bytes )
{
	unsigned char* ptr;
	unsigned char ctrl_bits;
	ptr = pData + 6;
	if ( memcmp( ptr, "SAGE", 4 ) )
		return false;

	ptr += 4; Bytes -= 4;
	pParser->info_version = *ptr++;
	ctrl_bits =	*ptr++;
	pParser->video_track_num  =	*ptr++;
	pParser->audio_track_num  =	*ptr++;
	pParser->video_main_track =	*ptr++;
	pParser->audio_main_track = *ptr++;
	pParser->main_video_stream_type = *ptr++;
	pParser->main_audio_stream_type = *ptr++;
	Bytes -= 8;
	pParser->sage_info_embedded = 1;

	if ( pParser->info_version >= 4 && pParser->main_video_stream_type == H264_STREAM_TYPE )
	{
		char* pH264Info = ParserDescTag( ptr, Bytes, SAGETV_H264_INFO );
		if ( pH264Info != NULL )
			pParser->h264_frame_rate = *(pH264Info+2);
	}

	return true;
}

int GetMainTrack( PS_PARSER* pParser, int* video_track, int* audio_track )
{
	if ( !pParser->sage_info_embedded )
		return 0;

	*video_track = pParser->video_main_track;
	*audio_track = pParser->audio_main_track;
	return pParser->info_version;
}

int GetMainStreamType( PS_PARSER* pParser, int* video_stream_type, int* audio_stream_type )
{
	if ( !pParser->sage_info_embedded )
		return 0;
	if ( pParser->info_version < 4 )
		return 0;

	*video_stream_type = pParser->video_stream_type;
	*audio_stream_type = pParser->audio_stream_type;
	return pParser->info_version;
}


int GetH264FrameRate( PS_PARSER* pParser )
{
	if ( !pParser->sage_info_embedded )
		return 0;
	if ( pParser->info_version < 4 )
		return 0;
	return pParser->h264_frame_rate;
}

int GetTrackNum( PS_PARSER* pParser, int* video_num, int* audio_num )
{
	if ( !pParser->sage_info_embedded )
		return 0;

	*video_num = pParser->video_track_num;
	*audio_num = pParser->audio_track_num;
	return pParser->info_version;
}


static void SetupBlockDataBuffer( PS_PARSER* pParser, int Size )
{
	//resever 4 bytes as round buffer
	pParser->data_block_buffer = sagetv_malloc( DEFAULT_BLOCK_SIZE + 16 );
	pParser->data_block_remain = pParser->data_block_buffer;
	pParser->data_block_size = Size+16;

}
static void ReleaseBlockDataBuffer( PS_PARSER* pParser )
{
	if ( pParser->data_block_buffer != NULL )
		sagetv_free( pParser->data_block_buffer );
	pParser->data_block_buffer = NULL;
	pParser->data_block_size = 0;
}

void PSResetBlockBuffer( PS_PARSER* pParser )
{
	pParser->data_block_remain = pParser->data_block_buffer;
}

PS_PARSER* PSParserOpen( )
{
	PS_PARSER* pParser;
	
	pParser = (PS_PARSER*)sagetv_malloc( sizeof(PS_PARSER) );
	pParser->av_dumper = NULL;

	pParser->video_stream_type = 0;
	pParser->audio_stream_type = 0;
	pParser->video_stream_id = 0;
	pParser->audio_stream_id = 0;
	pParser->demux_bound = 0;
	pParser->video_out_bytes = 0;
	pParser->audio_out_bytes = 0;

	pParser->video_packets = 0;
	pParser->audio_packets = 0;
	pParser->other_packets = 0;
	pParser->video_block = 0;
	pParser->audio_block = 0;
	pParser->packets = 0;	
	pParser->env = 0;

	//embedded sage infomaion
	pParser->video_track_num = 0;
	pParser->audio_track_num = 0;
	pParser->video_main_track = 0;
	pParser->audio_main_track = 0;
	pParser->main_video_stream_type = 0;
	pParser->main_audio_stream_type = 0;
	pParser->sage_info_embedded = 0;
	pParser->info_version = 0;
	pParser->h264_frame_rate = 0;

	SetupBlockDataBuffer( pParser, DEFAULT_BLOCK_SIZE );

	pParser->parser_state = 0; 
	//pParser->cur_stream_id = 0;
	//pParser->cur_stream_type = 0;
	memset( pParser->stream_LUT, 0, sizeof(pParser->stream_LUT) );
	memset( pParser->proprietary_stream,  0, sizeof(pParser->proprietary_stream) );
	memset( pParser->psm, 0, sizeof(pParser->psm) );
	pParser->psm_num = 0;
	pParser->cur_stream_index = -1;

	return pParser;

}

void PSFlushout( PS_PARSER* pParser )
{
	//fllush out data in buffer before close
	if 	( pParser->data_block_remain > pParser->data_block_buffer )
	{
		ProcessPacketData( pParser, pParser->data_block_buffer, 
			               (int)(pParser->data_block_remain - pParser->data_block_buffer), false );
	}

}
void PSParserClose( PS_PARSER* pParser )
{
	int i;
	ReleaseBlockDataBuffer( pParser );
	for ( i = 0; i<pParser->psm_num; i++ )
	{
		if ( pParser->psm[i].stream_info != NULL )
		{
			sagetv_free( pParser->psm[i].stream_info );
			pParser->psm[i].stream_info = NULL;
		}
	}
	if ( pParser != NULL )
		sagetv_free( pParser );

}	

unsigned long GetStreamBoundRate( PS_PARSER* pParser )
{
	return pParser->demux_bound;
}

unsigned long GetVideoBlockSize( PS_PARSER* pParser )
{
	return pParser->video_block_size;
}

unsigned long GetAudioBlockSize( PS_PARSER* pParser )
{
	return pParser->audio_block_size;
}

int UnpackPackHeader( char* pData, int Bytes, unsigned long *pDemux, LONGLONG* pRefTime )
{
	int length = 0;
	unsigned long demux;
	unsigned int pad_bytes = 0;
	unsigned char c;
	LONGLONG ll = 0;
	unsigned long l;
	//mpeg2
	if ( (pData[4] & 0xC0 ) == 0x40 )
	{
		ll =  ((LONGLONG)((unsigned char)pData[4] & 0x38 ))>>3;   //3bits
		ll <<= 2;
		ll |= ((LONGLONG)((unsigned char)pData[4] & 0x03 ));   //2bits
		ll <<= 8;
		ll |= ((LONGLONG)((unsigned char)pData[5] ));          //8bits
		ll <<= 5;
		ll |= ((LONGLONG)((unsigned char)pData[6] &0xf8 ))>>3;    //5bits
		ll <<= 2;
		ll |= ((LONGLONG)((unsigned char)pData[6] &0x03 ));    //2bits
		ll <<= 8;
		ll |= ((LONGLONG)((unsigned char)pData[7]));       //8bits
		ll <<= 5;
		ll |= ((LONGLONG)((unsigned char)pData[8] &0xf8 ))>>3;     //5bits

		l = ((unsigned long)((unsigned char)pData[8] &0x03 ))<<7;  //2bits
		l |= ((unsigned long)((unsigned char)pData[9] &0xfe ))>>1;  //7bits
		*pRefTime = ll*300+l;
	
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
		*pRefTime = ll;

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

int UnpackPadPack( const unsigned char* pbData, int Bytes )
{
	unsigned short Len;
	Len = pbData[5] | (pbData[4] << 8);
	if ( Bytes < 6 )
		return Bytes;
	return Len+6;

}

int UnpackPsmPack( PS_PARSER* pParser, const unsigned char* pbData, int Bytes )
{
	unsigned int pack_len, psm_info_len, es_map_len;
	unsigned int pos, bytes, len;
	pack_len = pbData[5] | (pbData[4] << 8);
	if ( pack_len > 1018 || (int)pack_len > Bytes )
		return 0;

	if ( !(pbData[6] & 0x80 ) ) //skip not yet applicabe pack.
		return pack_len;

	pos = 8;
	psm_info_len = (pbData[8]<<8)|pbData[9];
	pos += 2;
	pos += psm_info_len; //skip psm_info
	if ( pos > pack_len ) return 0;	
	
	es_map_len =  pbData[pos+1] | (pbData[pos] << 8);
	pos += 2;
	if ( pos + es_map_len > pack_len+6-4 ) return 0;	

	bytes = 0;
	pParser->psm_num = 0;
	while ( bytes + 4 <= es_map_len  )
	{
		pParser->psm[pParser->psm_num].stream_type = pbData[pos];
		pParser->psm[pParser->psm_num].stream_id   = pbData[pos+1];
		pos += 2;
		bytes += 2;

		/*
		switch ( pParser->psm[pParser->psm_num].stream_type ) {
		case 0x1:
			pParser->psm[pParser->psm_num].stream_type = 1;
			break;
        case 0x2:
			pParser->psm[pParser->psm_num].stream_type = 2;
			break;
        case 0x3:
			pParser->psm[pParser->psm_num].stream_type = 3;
			break;
        case 0x4:
			pParser->psm[pParser->psm_num].stream_type = 4;
			break;
        case 0x0f:
			pParser->psm[pParser->psm_num].stream_type = 0x0f; //AAC MPEG2-AUDIO
			break;
        case 0x11:
			pParser->psm[pParser->psm_num].stream_type = 0x81; //AAC MPEG4-AUDIO
			break;
        case 0x10:
			pParser->psm[pParser->psm_num].stream_type =  MPEG4_STREAM_TYPE; //0x10
			break;
        case 0x1b:
			pParser->psm[pParser->psm_num].stream_type =  H264_STREAM_TYPE;	//0x1b
			break;
        case 0x81:
			pParser->psm[pParser->psm_num].stream_type = 0x81;
			break;
		}
		*/
		if ( pParser->psm[pParser->psm_num].stream_info != NULL ) 
		{
			 sagetv_free( pParser->psm[pParser->psm_num].stream_info );
			 pParser->psm[pParser->psm_num].stream_info = NULL;
		}

		len  = pbData[pos] | (pbData[pos+1] << 8 );
		pos += 2;
		bytes += 2;

		if ( pParser->psm[pParser->psm_num].stream_type ==  H264_STREAM_TYPE ) 
		{
			char* p;
			p = ParserDescTag( &pbData[pos], len, VIDEO_STREAM_DESC_TAG );
			if ( p != NULL )
			{
				ParseH264VideoStreamInf( &pParser->psm[pParser->psm_num].stream_info, (unsigned char*)p );
			}
		} else
		if ( IsVideoType( pParser->psm[pParser->psm_num].stream_type) )
		{
			char* p;
			p = ParserDescTag( &pbData[pos], len, VIDEO_WINDOW_DESC_TAG );
			{
				printf( "video window information changed\n" );
			}
		}

		if ( pParser->psm_num < MAX_PES_STREAM - 1 ) pParser->psm_num++; 

		pos += len;
		bytes += len;
	}
	
	//crc32 skip
	pos += 4;

	return pos;
}

int LookupStreamType( PS_PARSER* pParser, unsigned char stream_id )
{
	int i;
	if ( pParser->psm_num == 0 ) 
		return 0;
	for ( i = 0; i<pParser->psm_num; i++ )
	{
		if ( pParser->psm[i].stream_id == stream_id )
		{
			return ( pParser->psm[i].stream_type );
		}
	}

	return 0;
}

int ParseH264VideoStreamInf( void** stream_info, const unsigned char* desc )
{
	char *p;
	if ( stream_info == NULL || desc == NULL ) return 0;
	*stream_info = p = sagetv_malloc(1);
	if ( *stream_info )
		*p = (desc[2]>>3)&0x0f; 
	return desc[1];
}

static int ProcessNewPacketData( PS_PARSER* pParser, char* pData, int Size )
{
	unsigned long demux;
	LONGLONG ref_time;

	bool pes_found;
	int  stream_type = 0;
	int  bytes, used_bytes;
	const unsigned char* ptr;
	unsigned long code;
	int magic_data_len;
	PES_INFO PESInfo={0};
	int stream_index = -1;

	pParser->data_offset = 0;

	used_bytes = bytes = 0;

	//PS packets struct: PACK [SYS]PES PACK PES PACK [SYS]...
	pes_found = false;
	while ( !pes_found && used_bytes < Size )
	{
		code = *((unsigned long*)((char*)pData+used_bytes));	
		code = DWORD_SWAP( code	);

		if ( code == PACK_START_CODE )
		{
			bytes = UnpackPackHeader( pData+used_bytes, Size, &demux, &ref_time );
			if ( bytes == 0 ) bytes++;

			if ( used_bytes + bytes > Size ) 
				break;

			if ( pParser->signal_dumper )
			{
				PES_PACK_INF pes_pack_inf;
				SIGNAL_INFO Signal;
				Signal.pid  = 0;
				Signal.size = sizeof(PES_PACK_INF);
				Signal.data = &pes_pack_inf;
				pes_pack_inf.Demux = demux;
				pes_pack_inf.SCR = ref_time;
				pes_pack_inf.pData = pData+used_bytes;
				pes_pack_inf.Size = bytes;
				Signal.type = PES_PACKET_PACK;
				pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
			}

			used_bytes += bytes;

			continue;
		} else
		if ( code == SYSTEM_HEADER_START_CODE )
		{
			bytes = DecodeSystemHeader( pParser, pData+used_bytes, Size-used_bytes );
			if ( bytes && pParser->parser_state == 0 )
			{
				pParser->parser_state = 1;
				if ( pParser->signal_dumper )
				{
					SYSTEM_HEAD_INF system_head_inf;
					SIGNAL_INFO Signal;
					Signal.pid  = 0;
					Signal.size = sizeof(SYSTEM_HEAD_INF);
					Signal.data = &system_head_inf;
					system_head_inf.pData = pData+used_bytes;
					system_head_inf.Size = bytes;
					system_head_inf.Demux = pParser->demux_bound;
					system_head_inf.VideoBlockSize = pParser->video_block_size;
					system_head_inf.AudioBlockSize = pParser->audio_block_size;

					Signal.type = STREAM_START;
					pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
					Signal.type = PES_SYSTEM_PACK;
					pParser->signal_dumper( (void*)pParser, 0, &Signal ); 
				}
			}

			if ( used_bytes + bytes > Size ) 
				break;
			used_bytes += bytes;
			continue;
		} else
		if ( code == PADDING_START_CODE )
		{
			bytes = UnpackPadPack( (unsigned char*)pData+used_bytes, Size-used_bytes );

			if ( pParser->signal_dumper )
			{
				PADDING_INF padding_inf;
				SIGNAL_INFO Signal;
				Signal.pid  = 0;
				Signal.size = sizeof(PADDING_INF);
				Signal.data = &padding_inf;
				padding_inf.pData= pData+used_bytes;
				padding_inf.Size = bytes;
				Signal.type = PES_PADDING_PACK;
				pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
			}

			//extract embedded Sage infomation
			if ( ExtractSageInfo( pParser, (unsigned char*)pData+used_bytes, Size-used_bytes  ) )
			{
				if ( pParser->signal_dumper )
				{
					SAGE_PRIVATE_INF sage_inf;
					SIGNAL_INFO Signal;
					Signal.pid  = 0;
					Signal.data = &sage_inf;
					Signal.size = sizeof(SAGE_PRIVATE_INF);
					sage_inf.pData = pData+used_bytes+6;
					sage_inf.Size = 10;
					sage_inf.VideoMain = pParser->video_track_num;
					sage_inf.AudioMain = pParser->audio_track_num;
					sage_inf.VideoNum  = pParser->video_main_track;
					sage_inf.AudioNum  = pParser->audio_main_track;
					sage_inf.MainVideoStreamType = pParser->main_video_stream_type;
					sage_inf.MainAudioStreamType = pParser->main_audio_stream_type;
					sage_inf.Version   = pParser->info_version;
					Signal.type = STREAM_READY;
					pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
					Signal.type = PES_SAGE_PRIVATE_INF;
					pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
				}
			}
			if ( used_bytes + bytes > Size ) 
				break;
			used_bytes += bytes;
			continue;
		} else
		if ( code == PSM_START_CODE )
		{
			bytes = UnpackPsmPack( pParser, (unsigned char*)pData+used_bytes, Size-used_bytes );
			if ( bytes <= 0 ) used_bytes++;
			else
			if ( used_bytes + bytes > Size )	break;
			else used_bytes += bytes;
			continue;
		} else
		if ( code == ISO_11172_END_CODE )
		{
			used_bytes += 4;
			continue;
		}else
		if ( VALID_PACKET( code	) )	
		{
			int ret = UnpackPESHeader( (unsigned char*)pData+used_bytes, Size-used_bytes, &PESInfo );
			pes_found = ret > 0;
			if ( pes_found )
			{
				unsigned char private_sub_id;
				PESInfo.SCR = ref_time;
				if ( IsProprietaryStream( pParser, PESInfo.StreamId ) )
				{
					private_sub_id = 0;
				} else
				if ( CheckProprietaryStream( pData+used_bytes+PESInfo.HeaderLen, 
										  Size-(used_bytes+PESInfo.HeaderLen ) )  )
				{
					MarkProprietaryStream( pParser, PESInfo.StreamId );
					private_sub_id = 0;
				} else
				if ( IsPrivateStreamId( PESInfo.StreamId ) )
				{
					private_sub_id = GetPrivatePESSubID( (unsigned char*)pData+used_bytes, Size-used_bytes, &PESInfo );
				}
				else
					private_sub_id = 0;

				stream_index = GetStreamIndex( pParser, PESInfo.StreamId, private_sub_id );
				if ( stream_index < 0 )
					stream_index = AddStreamIndex( pParser, PESInfo.StreamId, private_sub_id );
				if ( stream_index < 0 )
				{
					//too many stream is PS, skip it.
					used_bytes += PESInfo.PacketLen;
					pes_found = false;
					continue;
				}

				//pParser->stream_data_size[stream_index] = PESInfo.PacketLen;
				pParser->data_offset = used_bytes;
				magic_data_len = SkipPrivateData( &pParser->AV[stream_index], &PESInfo, 
								   (unsigned char*)pData+used_bytes+PESInfo.HeaderLen, 
					                                 Size-(used_bytes+PESInfo.HeaderLen) );
				break;
			} else
			if ( ret == -2 ) //length is too short
				break;
			else
				used_bytes += 4;
		}

		if ( SearchMPEG2StartCode( (unsigned char*)pData+used_bytes, Size-used_bytes, PACK_START_CODE, &ptr )  )
		{
			used_bytes += (int)(ptr - ((unsigned char*)pData+used_bytes));
			bytes = UnpackPackHeader( pData+used_bytes, Size, &demux, &ref_time );
			if ( bytes == 0 ) bytes++;

			if ( used_bytes + bytes > Size ) 
				break;
			used_bytes += bytes;
		}
		else
			return Size;
	}

	if ( !pes_found )
		return used_bytes;

	if ( pParser->parser_state == 0 )
	{
		pParser->parser_state = 1;
		if ( pParser->signal_dumper )
		{
			SYSTEM_HEAD_INF system_head_inf;
			SIGNAL_INFO Signal;
			Signal.pid  = 0;
			Signal.size = sizeof(SYSTEM_HEAD_INF);
			Signal.data = &system_head_inf;
			system_head_inf.pData = pData+used_bytes;
			system_head_inf.Size = bytes;
			system_head_inf.Demux = pParser->demux_bound;
			system_head_inf.VideoBlockSize = pParser->video_block_size;
			system_head_inf.AudioBlockSize = pParser->audio_block_size;

			Signal.type = STREAM_START;
			pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
			Signal.type = PES_SYSTEM_PACK;
			pParser->signal_dumper( (void*)pParser, 0, &Signal ); 
		}
	}

	//skip no AV PES date.
	if ( !IsVideoStreamId( PESInfo.StreamId ) && !IsAudioStreamId( PESInfo.StreamId ) && 
		 !IsPrivateStreamId( PESInfo.StreamId ) && !IsVC1StreamId( PESInfo.StreamId ) )
	{
		stream_type = 0;
	} else
	if ( pParser->AV[stream_index].AudioType == 0 && pParser->AV[stream_index].VideoType == 0 )
	{
		//AV_CONTEXT av={0};
		//int packet_bytes;
		int stream_type_in_info = LookupStreamType( pParser, PESInfo.StreamId );
		if ( pParser->info_version >= 5 )
		{
			//new NativeCore relay on PSM, bypassing sagetv embeded infor ZQ.  
		} else
		if ( pParser->info_version < 4 )
		{
			if ( stream_index == pParser->video_main_track )
				stream_type_in_info = pParser->main_video_stream_type;
			else
			if ( stream_index == pParser->audio_main_track )
				stream_type_in_info = pParser->main_audio_stream_type;
		}

		pParser->stream_type[stream_index] = DecodeAVHeader( &pParser->AV[stream_index], &PESInfo, stream_type_in_info,
		                                          (const unsigned char*)pData+used_bytes, Size-used_bytes );

		//pParser->AV[stream_index] = av;
		pParser->PES[stream_index] = PESInfo;
		magic_data_len = SkipPrivateData( &pParser->AV[stream_index], &PESInfo, 
		                                  (unsigned char*)pData+used_bytes+PESInfo.HeaderLen, 
			                          Size-(used_bytes+PESInfo.HeaderLen) );
		pParser->AV[stream_index].MagicLen = magic_data_len;
		if ( magic_data_len )
			memcpy( pParser->AV[stream_index].PrivateHeader, pData+used_bytes+PESInfo.HeaderLen, 4 );

		stream_type = pParser->stream_type[stream_index];

	} else
	{
		stream_type = pParser->stream_type[stream_index];
		pParser->PES[stream_index] = PESInfo;
	} 

	used_bytes += PESInfo.HeaderLen;

	if ( IsVideoType( stream_type ) || IsAudioType( stream_type ) )
	{
		pParser->AV[stream_index].MagicLen = magic_data_len;
		pParser->parser_data_bytes = PESInfo.HeaderLen + magic_data_len;
		pParser->video_packets++;
		used_bytes += magic_data_len;
	} else
	{
		pParser->OtherPES = PESInfo;
		pParser->other_packets++;
		pParser->other_data_bytes = PESInfo.HeaderLen;
	}

	pParser->cur_stream_index = stream_index;
	return used_bytes + ProcessPacketData( pParser, pData+used_bytes, Size-used_bytes, true );


}

static int ProcessPacketData( PS_PARSER* pParser, char* pData, int Size, bool PESStart )
{
	PES_AV_INFO av_info;
	int bytes;
	int stream_index;

	//skip all data if paser is not ready
	if ( pParser->parser_state == 0 || pParser->cur_stream_index < 0 )
		return Size;

	stream_index = pParser->cur_stream_index;
	bytes = 0;

	pParser->packets++;

	if ( IsVideoType( pParser->stream_type[stream_index] ) || IsAudioType( pParser->stream_type[stream_index] ) )
	{
		if ( pParser->PES[stream_index].PacketLen )
		{
			if ( pParser->PES[stream_index].PacketLen > pParser->parser_data_bytes)
				bytes = _MIN( (unsigned long)Size, pParser->PES[stream_index].PacketLen - pParser->parser_data_bytes );
			else
				bytes = 0;
		}
		else
		{
			//in some case, video PES data length is 0, we have to search next PACK_HEADER following PES_HEADER
			//to decide end of current PES data
			/*
			if ( SearchMPEG2StartCode( pData, Size, PACK_START_CODE, &ptr )  )
			{
				int pack_bytes, skip;
				unsigned long demux;
				LONGLONG ref_time;
				PES_INFO PESInfo;
				skip = ptr - pData;
		
				pack_bytes = UnpackPackHeader( pData+skip, Size-skip, &demux, &ref_time );
				if ( UnpackPESHeader( pData+skip+pack_bytes, Size-(skip+pack_bytes), &PESInfo ) > 0 )
				{
					bytes = skip;
				}
			}*/
			bytes = Size;
		}

		av_info.stream_id = pParser->PES[stream_index].StreamId;
		av_info.stream_type = pParser->stream_type[stream_index];
		av_info.start_group_flag = PESStart;
		av_info.data = pData;
		av_info.bytes = bytes;
		av_info.PCR = pParser->PES[stream_index].SCR;
		av_info.has_PTS = pParser->PES[stream_index].HasPts;
		av_info.PTS = pParser->PES[stream_index].llPts;
		av_info.has_DTS = pParser->PES[stream_index].HasDts;
		av_info.DTS = pParser->PES[stream_index].llDts;
		av_info.header_bytes = PESStart ? (unsigned short)pParser->parser_data_bytes : 0;

		if ( pParser->av_dumper )
			pParser->av_dumper(( void*)pParser,  stream_index , (void*)&av_info );
		pParser->parser_data_bytes += bytes;

		if ( IsVideoType( pParser->stream_type[stream_index] ) )
		{
			pParser->video_block++;
			pParser->video_out_bytes += bytes;
		} else
		{
			pParser->audio_block++;
			pParser->audio_out_bytes += bytes;
		}

	} else
	{   //skip other PES packets data, such as PAD packets
		if ( pParser->OtherPES.PacketLen )
		{
			if ( pParser->OtherPES.PacketLen > pParser->other_data_bytes )
				bytes = _MIN( (unsigned long)Size, pParser->OtherPES.PacketLen - pParser->other_data_bytes );
			else
				bytes = 0;
		}
		else
		{
			bytes = Size;
		}
	}

	return bytes;
}

bool SearchStartCode( const unsigned char* pData, int Bytes, const unsigned char** Ptr )
{
	unsigned long code = DWORD_SWAP(*(unsigned long *)pData);
	*Ptr = pData;
	if ( !VALID_SYSTEM_START_CODE(code) )
	{
		if ( !SearchMPEG2StartCode( pData, Bytes, PACK_START_CODE, Ptr )  )
		{
			*Ptr = pData+Bytes;
			return false;
		}
	}
	return true;
}


//function does buffering and alignmanting data.
void PSProcessBlock( PS_PARSER* pParser, int Size, char* pData )
{
	int bytes, remain_bytes, total_bytes, used_bytes;
	const unsigned char *ptr;
	
	while ( Size > 0 )
	{
		remain_bytes = (int)( pParser->data_block_remain - pParser->data_block_buffer );
		bytes = _MIN( Size, pParser->data_block_size - remain_bytes );
		memcpy( pParser->data_block_remain, pData, bytes );
		pData += bytes;
		Size -= bytes;
		total_bytes = bytes+remain_bytes;

		//if ( SearchMPEG2StartCode( pParser->data_block_buffer, total_bytes, PACK_START_CODE, &ptr )  )
		if ( SearchStartCode( (unsigned char*)pParser->data_block_buffer, total_bytes, &ptr ) )
		{
			int uncommited_bytes;
			uncommited_bytes = (int)(ptr - (unsigned char*)pParser->data_block_buffer);
			if ( uncommited_bytes )
			{
				used_bytes = ProcessPacketData( pParser, pParser->data_block_buffer, uncommited_bytes, false );
				//alignment PACK packet start in data block start
				remain_bytes = total_bytes - uncommited_bytes;
				memcpy( pParser->data_block_buffer, ptr, remain_bytes );
				pParser->data_block_remain = pParser->data_block_buffer + remain_bytes;
				pParser->data_pos += used_bytes;
			} else
			{
				used_bytes = ProcessNewPacketData( pParser, (char*)ptr, total_bytes );
				if ( used_bytes == 0 && Size > 0 )
				{
					printf( ">>>>>>>>>>> Fatal Error in PSParser<<<<<<<<<<<<\n" );
					used_bytes = Size;;
				}

				if ( used_bytes < total_bytes )
				{
					memcpy( pParser->data_block_buffer, ptr+used_bytes, total_bytes - used_bytes );
					pParser->data_block_remain = pParser->data_block_buffer + total_bytes - used_bytes;
				} else
				{
					pParser->data_block_remain = pParser->data_block_buffer;
				}
				pParser->data_pos += used_bytes;
			}
		} else
		{
			used_bytes = ProcessPacketData( pParser, pParser->data_block_buffer, total_bytes-16, false );
			memcpy( pParser->data_block_buffer, pParser->data_block_buffer + total_bytes-16, 16 );
			pParser->data_block_remain = pParser->data_block_buffer + 16;
			pParser->data_pos += used_bytes;
		}
	}
}

//#define DUMP_RAW( pParser, start, size ) if ( pParser->raw_dumper ) { pParser->raw_dumper( pParser, start, size ); };
#define DUMP_RAW( pParser, start, size ) {}
//called does buffering and alignmanting data.
unsigned long PSProcessBlockWithoutBuffer( PS_PARSER* pParser, int Size, char* pData, int* pUsedBytes )
{
	unsigned long used_bytes, bytes;
	PES_INFO PESInfo={0};
	unsigned long code;
	LONGLONG ref_time;

	used_bytes = 0;

	while ( (int)used_bytes + 14 <= Size )
	{
		const unsigned char *ptr;
		ptr = (unsigned char*)pData + used_bytes;


		if ( !SearchStartCode( (unsigned char*)pData+used_bytes, Size-used_bytes, &ptr ) )
		{
				used_bytes += (Size-used_bytes);
				if ( pUsedBytes != NULL )
					*pUsedBytes = used_bytes;

				pParser->data_pos += used_bytes;
				pParser->data_offset = 0;
				return used_bytes;

		} else
		{
			used_bytes += (int)(ptr - ((unsigned char*)pData+used_bytes));
		}

		ref_time = 0;
		//if it's not valid PACK, PES or SYSTEM header, drop it
		ptr = (unsigned char*)pData + used_bytes;
		code = DWORD_SWAP(*(unsigned long *)ptr);
		if ( code == PACK_START_CODE )
		{
			unsigned long demux;
			
			bytes = UnpackPackHeader( pData+used_bytes, Size-used_bytes, &demux, &ref_time );
			if ( bytes == 0 ) bytes++;

			if ( used_bytes + bytes > (unsigned long)Size )
				break;

			if ( pParser->signal_dumper )
			{
				PES_PACK_INF pes_pack_inf;
				SIGNAL_INFO Signal;
				Signal.pid  = 0;
				Signal.size = sizeof(PES_PACK_INF);
				Signal.data = &pes_pack_inf;
				pes_pack_inf.Demux = demux;
				pes_pack_inf.SCR = ref_time;
				pes_pack_inf.pData = pData+used_bytes;
				pes_pack_inf.Size = (unsigned short)bytes;
				Signal.type = PES_PACKET_PACK;
				pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
			}

			DUMP_RAW( pParser, pData+used_bytes, bytes );
			used_bytes += bytes;


		} else
		//if it's SYSTEM header, unpack SYSTEM Header
		if ( code == SYSTEM_HEADER_START_CODE )
		{
			bytes = DecodeSystemHeader( pParser, (char*)pData+used_bytes, Size-used_bytes );
			if ( bytes != 0 )
			{
				pParser->parser_state = 1;
				pParser->data_offset = used_bytes;
				if ( used_bytes + bytes > (unsigned long)Size )
					break;

				if ( pParser->signal_dumper )
				{
					SYSTEM_HEAD_INF system_head_inf;
					SIGNAL_INFO Signal;
					Signal.pid  = 0;
					Signal.size = sizeof(SYSTEM_HEAD_INF);
					Signal.data = &system_head_inf;
					system_head_inf.pData = pData+used_bytes;
					system_head_inf.Size = (unsigned short)bytes;
					system_head_inf.Demux = pParser->demux_bound;
					system_head_inf.VideoBlockSize = pParser->video_block_size;
					system_head_inf.AudioBlockSize = pParser->audio_block_size;

					Signal.type = STREAM_START;
					pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
					Signal.type = PES_SYSTEM_PACK;
					pParser->signal_dumper( (void*)pParser, 0, &Signal ); 
				}

				DUMP_RAW( pParser, pData+used_bytes, bytes );
				used_bytes += bytes;
			}

		} else
		if ( code == PADDING_START_CODE )
		{
			bytes = UnpackPadPack( (unsigned char*)pData+used_bytes, Size-used_bytes );
			if ( used_bytes + bytes > (unsigned long)Size )
				break;

			if ( pParser->signal_dumper )
			{
				PADDING_INF padding_inf;
				SIGNAL_INFO Signal;
				Signal.pid  = 0;
				Signal.size = sizeof(PES_PACK_INF);
				Signal.data = &padding_inf;
				padding_inf.pData = pData+used_bytes;
				padding_inf.Size = (unsigned short)bytes;
				Signal.type = PES_PADDING_PACK;
				pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
			}

			//extract embedded Sage infomation
			if ( ExtractSageInfo( pParser, (unsigned char*)pData+used_bytes, Size-used_bytes ) )
			{
				if ( pParser->signal_dumper )
				{
					SAGE_PRIVATE_INF sage_inf;
					SIGNAL_INFO Signal;
					Signal.pid  = 0;
					Signal.data = &sage_inf;
					Signal.size = sizeof(SAGE_PRIVATE_INF);
					sage_inf.pData = pData+used_bytes+6;
					sage_inf.Size = 10;
					sage_inf.VideoMain = pParser->video_track_num;
					sage_inf.AudioMain = pParser->audio_track_num;
					sage_inf.VideoNum  = pParser->video_main_track;
					sage_inf.AudioNum  = pParser->audio_main_track;
					sage_inf.Version   = pParser->info_version;
					Signal.type = STREAM_READY;
					pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
					Signal.type = PES_SAGE_PRIVATE_INF;
					pParser->signal_dumper( (void*)pParser, -1, &Signal ); 
				}
			}

			DUMP_RAW( pParser, pData+used_bytes, bytes );
			used_bytes += bytes;

		} else
		if ( code == PSM_START_CODE )
		{
			bytes = UnpackPsmPack( pParser, (unsigned char*)pData+used_bytes, Size-used_bytes );
			if ( bytes <= 0 ) used_bytes++;
			else
			if ( (int)(used_bytes + bytes) > Size )	break;
			else used_bytes += bytes;
			continue;
		} else
		//if it's a PES packet
		if ( VALID_PACKET( code	) )	
		{
			int ret;
			memset( &PESInfo, 0, sizeof(PESInfo) );
			ret = UnpackPESHeader( (unsigned char*)pData+used_bytes, Size-used_bytes, &PESInfo );
			if ( ret > 0 )
			{
				unsigned short av_bytes, magic_data_len;
				unsigned char  private_sub_id;
				int stream_index = 0;
				PES_AV_INFO av_info = {0};

				//if data is not all here, wait for more incomming data(caller buffering data )
				if ( Size < (int)(used_bytes + PESInfo.PacketLen) )
					break;

				if ( IsProprietaryStream( pParser, PESInfo.StreamId ) )
				{
					private_sub_id = 0;
				} else
				if ( CheckProprietaryStream( pData+used_bytes+PESInfo.HeaderLen, 
										  Size-(used_bytes+PESInfo.HeaderLen ) ) )
				{
					MarkProprietaryStream( pParser, PESInfo.StreamId );
					private_sub_id = 0;
				} else
				if ( IsPrivateStreamId( PESInfo.StreamId ) )
					private_sub_id = GetPrivatePESSubID( (unsigned char*)pData+used_bytes, Size-used_bytes, &PESInfo  );
				else
					private_sub_id = 0;

				stream_index = GetStreamIndex( pParser, PESInfo.StreamId, private_sub_id );
				if ( stream_index < 0 )
					stream_index = AddStreamIndex( pParser, PESInfo.StreamId, private_sub_id );
				if ( stream_index < 0 )
				{
					//too many stream is PS, skip it.
					used_bytes += PESInfo.PacketLen;
					continue;
				}
				
				pParser->data_offset = used_bytes;

				////if data is not all here, wait for more incomming data(caller buffering data )
				//if ( Size < (int)(used_bytes + PESInfo.PacketLen) )
				//	break;

				//skip unkown PES date.
				if ( !IsVideoStreamId( PESInfo.StreamId ) && !IsAudioStreamId( PESInfo.StreamId ) && 
					 !IsPrivateStreamId( PESInfo.StreamId ) && !IsVC1StreamId( PESInfo.StreamId ))
				{
					used_bytes += PESInfo.PacketLen;
					continue;
				}

				//if video and audio codec is unkown, decode it
				if ( pParser->AV[stream_index].AudioType == 0 && pParser->AV[stream_index].VideoType == 0 )
				{
					//AV_CONTEXT av={0};
					int stream_type = LookupStreamType( pParser, PESInfo.StreamId );
					if ( stream_type == 0 && pParser->info_version >= 4 )
					{
						if ( stream_index == pParser->video_main_track )
							stream_type = pParser->main_video_stream_type;
						else
						if ( stream_index == pParser->audio_main_track )
							stream_type = pParser->main_audio_stream_type;
					}

					pParser->stream_type[stream_index] = DecodeAVHeader( &pParser->AV[stream_index], &PESInfo, stream_type,
											  (const unsigned char*)pData+used_bytes, (int)Size-used_bytes );
					if ( pParser->stream_type[stream_index] )
					{
						//pParser->AV[stream_index] = av;
						magic_data_len = SkipPrivateData( &pParser->AV[stream_index], &PESInfo, 
						                      (unsigned char*)pData+used_bytes+PESInfo.HeaderLen, 
								      Size-(used_bytes+PESInfo.HeaderLen) );
						if ( magic_data_len )
							memcpy( pParser->AV[stream_index].PrivateHeader, pData+used_bytes+PESInfo.HeaderLen, 4 );
					}

				}

				magic_data_len = SkipPrivateData( &pParser->AV[stream_index], &PESInfo, 
				                                  (unsigned char*)pData+used_bytes+PESInfo.HeaderLen, 
								  Size-(used_bytes+PESInfo.HeaderLen) );
				pParser->AV[stream_index].MagicLen = magic_data_len;

				assert( PESInfo.PacketLen >= (unsigned long)PESInfo.HeaderLen + magic_data_len );

				pParser->PES[stream_index] = PESInfo;

				used_bytes += PESInfo.HeaderLen + magic_data_len;;
				av_bytes = (unsigned short)PESInfo.PacketLen - PESInfo.HeaderLen - magic_data_len;
				av_bytes = (unsigned short)_MIN( av_bytes, (Size-used_bytes) );

				if ( IsVideoType( pParser->stream_type[stream_index] ) || IsAudioType( pParser->stream_type[stream_index] ) )
				{
					av_info.stream_id = PESInfo.StreamId;
					av_info.stream_type = pParser->stream_type[stream_index];
					av_info.start_group_flag = true;
					av_info.data = pData + used_bytes;
					av_info.bytes = av_bytes;
					av_info.PCR = ref_time;
					av_info.has_PTS = PESInfo.HasPts;
					av_info.PTS = PESInfo.llPts;
					av_info.has_DTS = PESInfo.HasDts;
					av_info.DTS = PESInfo.llDts;
					av_info.header_bytes= PESInfo.HeaderLen + magic_data_len;

					if ( pParser->av_dumper )
					{
						pParser->av_dumper( (void*)pParser,  stream_index , (void*)&av_info );
						if ( IsVideoType( pParser->stream_type[stream_index] ) )
						{
							pParser->video_block++;
							pParser->video_packets++;
							pParser->video_out_bytes += av_bytes;
						} else
						{
							pParser->audio_block++;
							pParser->audio_packets++;
							pParser->audio_out_bytes += av_bytes;
						}
					}
				}

				DUMP_RAW( pParser, pData+used_bytes, bytes );
				used_bytes += av_bytes;
			}
			else
			if ( ret == -2 ) //packet is too short
				break;
			else
			{
				used_bytes += 4; //error PES, skip 4 bytes
			}

		} else
		if ( code == ISO_11172_END_CODE )
		{
			DUMP_RAW( pParser, pData+used_bytes, 4 );
			used_bytes += 4;
			break;
		} 
	}

	used_bytes = _MIN( used_bytes, (unsigned long)Size );
	if ( pUsedBytes != NULL )
		*pUsedBytes = used_bytes;

	pParser->data_pos += used_bytes;
	pParser->data_offset = 0;
	return used_bytes;
}

static int GetStreamIndex( PS_PARSER* pParser, unsigned char streamid, unsigned char subid )
{
	int i;
	unsigned short code = ( streamid << 8 ) | subid;
	for ( i = 0; i<MAX_PES_STREAM; i++ )
		if ( pParser->stream_LUT[i] == code )
			return i;

	return -1;
}

static int AddStreamIndex( PS_PARSER* pParser, unsigned char streamid, unsigned char subid )
{
	int i, j;
	unsigned int index;
	unsigned short code = ( streamid << 8 ) | subid;
	//check if Stream is already in the table
	for ( i = 0; i<MAX_PES_STREAM; i++ )
	{
		if ( pParser->stream_LUT[i] == code )
			return i;
	}

	//keep stream order that is inhereted from SageTV encoded file, because I indicate main sound video track in embedded SageTV info in header 
	if ( IsPrivateStreamId(streamid) ) //if ( streamid == PRIVATE_STREAM_1 ) //don't care PRIVAITE_STREAM_2
		index = (unsigned char)subid & 0x7f;
	else
	if ( IsVideoStreamId( streamid ) )
		index = ( streamid & ~0xe0 );
	else
	if ( IsAudioStreamId( streamid ) )
		index = ( streamid & ~0xc0 );
	else
	if ( IsVC1StreamId( streamid ) )
		index = 0;                   //We support only one VC1 video in current version, in the next version add more ZQ!!!
	else
		return -1;

	if ( index < MAX_PES_STREAM && pParser->stream_LUT[index] == 0 )
	{
		pParser->stream_LUT[index] = code;
		return index;
	}

	//I can't maintain order, so I hight end of index range (0-MAX_PES_STREAM)
	j = index+1;
	if ( j >= MAX_PES_STREAM ) j = 0;
	for ( i = 0; i<MAX_PES_STREAM; i++  )
	{
		if ( pParser->stream_LUT[j] == 0 )
		{
			pParser->stream_LUT[j] = code;
			return j;
		}
		if ( ++j>= MAX_PES_STREAM ) j=0;
	}
	return -1;

	///////////////////////////////////////////////////////////////////
}


static int IsProprietaryStream( PS_PARSER* pParser, unsigned char streamid )
{
	int i;

	for ( i = 0; i<MAX_PROP_STREAM; i++ )
	{
		if ( pParser->proprietary_stream[i] == streamid )
			return 1;
	}
	return 0;

}
static int MarkProprietaryStream( PS_PARSER* pParser, unsigned char streamid )
{
	int i;
	unsigned char ch;
	for ( i = 0; i<MAX_PROP_STREAM; i++ )
	{
		if ( pParser->proprietary_stream[i] == 0 )
		{
			pParser->proprietary_stream[i] = streamid;
			break;
		}
	}
	for ( i = 0; i<MAX_PROP_STREAM; i++ )
	{
		ch = pParser->stream_LUT[i]>>8;
		if ( ch == streamid )
			pParser->stream_LUT[i] = 0;
	}

	return (i<MAX_PROP_STREAM);
}

LONGLONG PSDataPosition( PS_PARSER* pParser )
{
	return  pParser->data_pos + pParser->data_offset;
}

void   PSResetDataPosition( PS_PARSER* pParser )
{
	pParser->data_pos = 0;
	pParser->data_offset = 0;
}

void SetPSDumpAVCallback( PS_PARSER* pParser,  LPFNParserDump dumper )
{
	pParser->av_dumper = dumper;
}

void SetPSDumpSignalCallback( PS_PARSER* pParser,  LPFNParserDump dumper)
{
	pParser->signal_dumper = dumper;
}

void SetPSDumpRawCallback( PS_PARSER* pParser,  LPFNRawDump dumper)
{
	pParser->raw_dumper = dumper;
}

