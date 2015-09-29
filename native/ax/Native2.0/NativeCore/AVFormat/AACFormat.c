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
#include "../NativeCore.h"
#include "../Bits.h"
#include "AACFormat.h"

static int  UnpackAACAudioPCEChannel( const uint8_t* pData, int Size );
static const int AAC_sampling_Frequency[16] = {96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,0,0,0,0};

static int UnpackAACAudioADTS( AAC_AUDIO *pAACAudio,  const uint8_t * pbData, int Size	)
{
	int ID, layer, protection_absent,version;
	int profile_objectType, sampling_frequency_index, private_bit, channel_configuration, original_copy, home;
	int copyright_identification_bit, copyright_identification_start, aac_frame_length, adts_buffer_fullness;
	int number_of_raw_data_blocks_in_frame;
	int raw_data_pos[4];
	const uint8_t *pData;
	int i, ret=0;
	pData = pbData;

	if ( pAACAudio->state == 3 ) return 1; //already knew foramat, skip parsing.
	if ( pAACAudio->state && pAACAudio->format != 1 ) return 0; //not ADTS format, LOAS

	if ( Size > 8 )
	{
		/*
		//search for SYNC bits (12bits of 1) of sync world
		for ( i = 0; i<Size; i++, pData++ ) 
			if ( *pData == 0xff && ( *(pData+1) & 0xf0 ) == 0xf0  )
				break;
		if ( i>Size-4 )
			return 0;
		*/
		//we don't search syncword,it's too dangerous for AAC, we assume ADTS syncword at start of a packet
		if ( !(*pData == 0xff && ( *(pData+1) & 0xf0 ) == 0xf0  ) )
		{
			pAACAudio->state = 0;
			pAACAudio->format = 0;
			return 0;
		}

		//ADTS fixed header is fixed for all ADTS header
		if ( pAACAudio->state && (
				pAACAudio->u.atds_header[1] != pData[1] || pAACAudio->u.atds_header[2] != pData[2] || 
				(pAACAudio->u.atds_header[3]&0xf0) != (pData[3]&0xf0) ) )
		{
			pAACAudio->state = 0;
			pAACAudio->format = 0;
			return 0;
		}

		//ID:1:MPEG2 AAC 13828-7; 0:MPEG4 AAC
		ID = (*(pData+1) >> 3)&0x01;							 //1 bits
		version = (ID == 1)?2:4;                                 //MPEG2 AAC or MPEG4 AAC
		layer = (*(pData+1) >> 1)&0x3;                           //2 bits
		protection_absent = (*(pData+1))&0x01;                   //1 bits

		//fixed header
		profile_objectType = (*(pData+2)>>6 ) & 0x03;			//2 bits
		sampling_frequency_index = (*(pData+2)>>2) & 0x0f;		//4 bits
		private_bit = (*(pData+2)>>1)&0x01;						//1 bits
		channel_configuration = (*(pData+2)&0x01)<<2;;	        //1 bits
		channel_configuration |= (*(pData+3)>>6)&0x03;          //2 bits
		original_copy = (*(pData+3)>>5)&0x01;			        //1 bits						
		home = (*(pData+3)>>4)&0x01;							//1 bits

		if ( sampling_frequency_index >= 12 ) return 0;

		//variable header
		copyright_identification_bit = (*(pData+3)>>3)&0x01;;   //1 bits
		copyright_identification_start = (*(pData+3)>>2)&0x01;; //1 bits
		aac_frame_length =  (*(pData+3)&0x03)<<12;              // 2/13 bits
		aac_frame_length |=  (*(pData+4))<<3;                   // 8/13 bits
		aac_frame_length |=  (*(pData+5)>>5)&0x07;              // 3/13 bits
		adts_buffer_fullness = (*(pData+5)&0x1f)<<6;            // 5/11 bits 
		adts_buffer_fullness |= (*(pData+6)&0x3f)>>2;           // 6/11 bits 
		number_of_raw_data_blocks_in_frame = (*(pData+6)&0x3);  // 2 bits 
		//end of adts_fixed_header + adts_variable_header

		memcpy( pAACAudio->u.atds_header, pData, sizeof(pAACAudio->u.atds_header) );
		//we need more parse more to get channel number
		pData += 7;
		if ( channel_configuration == 0 )
		{
			int id_syn_ele;
			if ( number_of_raw_data_blocks_in_frame == 0 )
			{
				
				if ( protection_absent == 0 )
					pData += 2;
				
				id_syn_ele = ((*pData)>>5)&0x07;
				if ( id_syn_ele == 0x05 /*ID_PCE */ )
					ret = UnpackAACAudioPCEChannel( pData, Size-(int)(pData-pbData) );
			} else
			{
				for ( i = 0; i<number_of_raw_data_blocks_in_frame; i++ )
				{
					raw_data_pos[i]=(pData[0]<<8)|pData[1];
					pData += 2;
				}
				if ( protection_absent == 0 )
					pData += 2;

				id_syn_ele = ((*pData)>>5)&0x07;
				if ( id_syn_ele == 5 /*ID_PCE */ )
					ret = UnpackAACAudioPCEChannel( pData, Size-(int)(pData-pbData) );
			}
			channel_configuration = ret;
		}
			
		ret = ( pAACAudio->version == version ) && 
			  ( pAACAudio->profile == layer ) &&
			  ( pAACAudio->channels == channel_configuration ) &&
			  ( pAACAudio->samples_per_sec == AAC_sampling_Frequency[sampling_frequency_index] );

		pAACAudio->version = version;
		pAACAudio->profile = layer;
		pAACAudio->channels = channel_configuration;
		pAACAudio->frame_length = aac_frame_length;
		pAACAudio->samples_per_sec = AAC_sampling_Frequency[sampling_frequency_index];
		if ( pAACAudio->samples_per_sec == 0 ) return 0;
		if ( pAACAudio->frames )
		{
			pAACAudio->bitrate = pAACAudio->samples_per_sec*aac_frame_length/1024;  //just guess
			pAACAudio->avgbytes_per_sec = pAACAudio->bitrate/8;
		}

		if ( ret )   //it's not invalid header, if two header is inconsistance
		{
			pAACAudio->format = 1;
			pAACAudio->state = 3; ; //ADTS
			return 1;
		}
	}
	return 0;
}

int  UnpackAACAudioPCEChannel( const uint8_t* pData, int Size )
{
	uint16_t element_inst_tag, profile, sample_frq_index;
	int num_front_channel_elements, num_side_channel_elements;
	int num_back_channel_elements, num_lfe_channel_elements;
	int channels = 0;

	element_inst_tag = ((*pData)>>4 )&0x0f;
	profile = ((*pData)>>2)&0x03;
	sample_frq_index = ((*pData)&0x03)<<2;
	sample_frq_index |= ((*pData+1)>>6)&0x3;
	num_front_channel_elements = ((*pData+1)>>2)&0xf;
	num_side_channel_elements  = ((*pData+1)&0x3)<<2;
	num_side_channel_elements |= ((*pData+2)>>6)&0x03;
	num_back_channel_elements = ((*pData+2)>>6)&0xf;
	num_lfe_channel_elements  = ((*pData+2)&0x3)<<2;
	num_lfe_channel_elements  |= ((*pData+3)>>6)&0x03;

	if ( num_front_channel_elements ) channels++;
	if ( num_side_channel_elements ) channels+=2;
	if ( num_back_channel_elements ) channels++;
	if ( num_lfe_channel_elements ) channels++;
	return channels;
}

//////////////////////////////////////////////////////////////////////////////////////////

typedef struct {
	char audioObjectType;
	char extensionAudioObjectType;
	int  extensionSamplingFrequency;
	int  channelConfiguration;
	int  SamplingFrequency;
	uint16_t profile;
	int  channel_num;
	int16_t frame_length;
} LATM_CFG;

static int StreamMuxConfig( AAC_AUDIO *pAACAudio, const uint8_t * pbData, int Size );

static int UnpackAACAudioLATM( AAC_AUDIO *pAACAudio, const uint8_t * pbData, int Size	)
{
	uint16_t pack_frame_length;
	const uint8_t* pData;

	if ( pAACAudio->state == 0 ) pAACAudio->expect_bytes = 0;
	if ( pAACAudio->state == 3 ) return 1; //already knew foramat, skip parsing.
	if ( pAACAudio->state && pAACAudio->format != 2 ) return 0; //not LATM (DVB) format
	if ( pAACAudio->state > 0 && pAACAudio->expect_bytes > 0 )
	{
		if ( pAACAudio->expect_bytes > Size )
		{
			pAACAudio->expect_bytes -= Size;
			return 0;
		} else 
		{
			pbData += pAACAudio->expect_bytes;
			Size  -= pAACAudio->expect_bytes;
			pAACAudio->expect_bytes = 0;
		}
	}

	pData = pbData;

	while ( Size > 8 )
	{
		//search LOAS head
		if ( !(pData[0] == 0x56 && ((pData[1] & 0xE0)==0xE0 )) )
		{
			pAACAudio->state = 0;
			pAACAudio->format = 0;
			return 0;
		}

		pack_frame_length = (pData[1]&0x1f)<<8;
		pack_frame_length |= pData[2];

		pAACAudio->expect_bytes = pack_frame_length;
		Size  -= 3;
		pData += 3;
		if ( pAACAudio->expect_bytes < 8 ) return 0;
		if ( ( pData[0] & 0x80 ) == 0x0 ) //useSameStreamMux
		{
			int ret = StreamMuxConfig( pAACAudio, pData, Size );
			if ( ret )
			{
				memcpy( pAACAudio->u.latm_header, pData-3, sizeof(pAACAudio->u.latm_header) );
			}
		}

		if ( pack_frame_length > Size )
		{
			pAACAudio->expect_bytes = pack_frame_length-Size;
			Size = 0;
		} else
		{
			pAACAudio->expect_bytes = 0;
			Size  -= pack_frame_length;
			pData += pack_frame_length;
		}

		if ( pAACAudio->state == 0 ) 
		{
			pAACAudio->frames = 0;
			pAACAudio->total_frame_bytes = 0;
		}
		pAACAudio->frames++;
		pAACAudio->total_frame_bytes += pack_frame_length;

		; //LOAS
		pAACAudio->format = 2;
		if ( ++pAACAudio->state >= 3  )
		{
			if ( pAACAudio->object_type )
			{
				if ( pAACAudio->frames )
				{
					pAACAudio->bitrate = pAACAudio->samples_per_sec*pAACAudio->total_frame_bytes/(1024*pAACAudio->frames);
					pAACAudio->avgbytes_per_sec = pAACAudio->bitrate/8;
				}
				pAACAudio->version = 0;
				return 1;
			} else
				pAACAudio->state = 2;
		}
	}
	return 0;
}

static int UnpackAACHEAudio( AAC_AUDIO *pAACAudio, const uint8_t * pbData, int nSize	)
{
	uint16_t pack_frame_length;
	int i, cfg_found = 0;
	const uint8_t* pData;
	int Size;

	Size = nSize;
	pData = pbData;

	//search LOAS head
	i = 0;
	while ( i < Size-1 && !(pData[i] == 0x56 && ((pData[i+1] & 0xE0)==0xE0 )) )
		i++;

	if ( !(pData[i] == 0x56 && ((pData[i+1] & 0xE0)==0xE0 )) )
	{
		pAACAudio->state = 0;
		pAACAudio->format = 0;
		return 0;
	}
	
	pbData += i;
	nSize -= i;

	pData = pbData;
	Size = nSize;

	//step 1:read MuxConfig
	while ( Size > 8 )
	{
		pack_frame_length = (pData[1]&0x1f)<<8;
		pack_frame_length |= pData[2];

		Size  -= 3;
		pData += 3;
		if ( pack_frame_length < 8 ) return 0;
		if ( ( pData[0] & 0x80 ) == 0x0 ) //useSameStreamMux
		{
			cfg_found = StreamMuxConfig( pAACAudio, pData, Size );
			if ( cfg_found )
			{
				memcpy( pAACAudio->u.latm_header, pData-3, sizeof(pAACAudio->u.latm_header) );
				break;
			}
		}

		if ( pack_frame_length > Size )
		{
			break;
		} else
		{
			Size  -= pack_frame_length;
			pData += pack_frame_length;
		}
	}

	if ( !cfg_found )
		return 0;

	pData = pbData;
	Size = nSize;

	//step 2:work out bitrate based on frames
	pAACAudio->frames = 0;
	pAACAudio->total_frame_bytes = 0;
	while ( Size > 8 )
	{
		pack_frame_length = (pData[1]&0x1f)<<8;
		pack_frame_length |= pData[2];

		pAACAudio->expect_bytes = pack_frame_length;
		Size  -= 3;
		pData += 3;

		pAACAudio->frames++;
		pAACAudio->total_frame_bytes += pack_frame_length;

		if ( pack_frame_length > Size )
		{
			break;
		} else
		{
			Size  -= pack_frame_length;
			pData += pack_frame_length;
		}
	}

	if ( pAACAudio->object_type )
	{
		if ( pAACAudio->frames )
		{
			pAACAudio->bitrate = pAACAudio->samples_per_sec*pAACAudio->total_frame_bytes/(1024*pAACAudio->frames);
			pAACAudio->avgbytes_per_sec = pAACAudio->bitrate/8;
		}
		pAACAudio->version = 0;
		return 1;
	}
	return 0;
}

/*
static int _ProgramConfigElement( LATM_CFG* cfg, const uint8_t * pbData, int* UsedBits, int* TotalBits  )
{
	int num_front_channel_elements, num_side_channel_elements;
	int num_back_channel_elements, num_lfe_channel_elements;
	int num_assoc_data_elements, num_valid_cc_elements;
	int comment_field_bytes;
	int channels = 0;

	int i;
	//int  bits_offset, total_bits
	BITS_I bits={0};
	bits.bits_offset = *UsedBits; bits.total_bits = *TotalBits;
	bits.buffer = pbData;

	//ReadBitsU( &bits,   );
	*UsedBits = 0;
	
	SkipBits( &bits, 4  ); //element_inst_tag
	SkipBits( &bits, 2  ); //object type
	SkipBits( &bits, 4  ); //sample frq inxe
	num_front_channel_elements = ReadBitsU( &bits, 4 );
	num_side_channel_elements  = ReadBitsU( &bits, 4 );
	num_back_channel_elements =  ReadBitsU( &bits, 4 );
	num_lfe_channel_elements  =  ReadBitsU( &bits, 2 );
	num_assoc_data_elements  =  ReadBitsU( &bits, 3 );
	num_valid_cc_elements  =  ReadBitsU( &bits, 4 );

	if ( num_front_channel_elements ) channels++;
	if ( num_side_channel_elements ) channels+=2;
	if ( num_back_channel_elements ) channels++;
	if ( num_lfe_channel_elements ) channels++;

    if ( ReadBitsU( &bits, 1 ) ) //mono_mixdown_present
	   SkipBits( &bits, 4 );        //mono_mixdown_element_number
    if ( ReadBitsU( &bits, 1 ) ) //stereo_mixdown_present
	   SkipBits( &bits, 4 );        //stereo_mixdown_element_number
    if ( ReadBitsU( &bits, 1 ) ) // matrix_mixdown_idx_present
    {
		SkipBits( &bits, 3 );		//matrix_mixdown_idx
		SkipBits( &bits, 1 );		//pseudo_surround_enable;
    }
    
    for ( i = 0; i<num_front_channel_elements; i++ )
	{
		SkipBits( &bits, 1 ); //front_element_is_cpe[i]
		SkipBits( &bits, 4 ); //front_element_tag_select[i]
	};

    for ( i = 0; i <num_side_channel_elements; i++) 
	{
		SkipBits( &bits, 1 ); //side_element_is_cpe[i]
		SkipBits( &bits, 4 ); //side_element_tag_select[i]
	};

    for ( i = 0; i<num_back_channel_elements; i++ )
	{
		SkipBits( &bits, 1 ); //back_element_is_cpe[i];
		SkipBits( &bits, 4 ); //back_element_tag_select[i];
	}
    for ( i = 0; i<num_lfe_channel_elements; i++ )
	{
		SkipBits( &bits, 4 ); //lfe_element_tag_select[i];
	}
    for ( i = 0; i<num_assoc_data_elements; i++ )
	{
		SkipBits( &bits, 4 ); //assoc_data_element_tag_select[i];
	}
    for ( i = 0; i<num_valid_cc_elements; i++ )
	{
		SkipBits( &bits, 1 ); //cc_element_is_ind_sw[i]
		SkipBits( &bits, 4 ); //valid_cc_element_tag_select[i];
	}

	SkipBits( &bits, (8-(bits.bits_offset&0x07)) ); //byte_alignment() relative to audio specific config

    comment_field_bytes = ReadBitsU( &bits, 8 );
    SkipBits( &bits, 8*comment_field_bytes ); //comment_field_data[i]
	*UsedBits = bits.bits_offset;	*TotalBits = bits.total_bits;
	return channels;
}

static int _GASpecificConfig( LATM_CFG* cfg, const uint8_t * pbData, int* UsedBits, int* TotalBits )
{
	//int  bits_offset, total_bits;
	int ext_flag;
	BITS_I bits={0};
	bits.bits_offset = *UsedBits; bits.total_bits = *TotalBits;
	bits.buffer = pbData;

	//bits_offset = *UsedBits; total_bits = *TotalBits;

	SkipBits( &bits, 1 );			  //framelen_flag
	if ( ReadBitsU( &bits, 1 ) ) //dependsOnCoder
		SkipBits( &bits, 14 );       //delay

	ext_flag = ReadBitsU( &bits, 1 );
	if ( cfg->channelConfiguration == 0 )
	{
		cfg->channel_num = _ProgramConfigElement( cfg, pbData, &bits.bits_offset, &bits.total_bits  );
	}
	if ( cfg->audioObjectType == 6 || cfg->audioObjectType == 20 ) 
		SkipBits( &bits, 3 ); //layerNr

	if ( ext_flag ) 
	{
		switch ( cfg->audioObjectType ) {
		case 22:
			SkipBits( &bits, 5 );  //numOfSubFrame 5);
			SkipBits( &bits, 11 ); //layer_length  11;
			break;
		case 17:
		case 19:
		case 20:
		case 23:
			SkipBits( &bits, 3 ); //stuff
			break;
		}
		SkipBits( &bits, 1 ); //extflag3
	}
	*UsedBits = bits.bits_offset;	*TotalBits = bits.total_bits;
	return 1;
}

static void _ReadAudioSpecificConfig( LATM_CFG* cfg, const uint8_t * pbData, int* UsedBits, int* TotalBits )
{
	int samplingFrequencyIndex;
	//int  bits_offset, total_bits;
	BITS_I bits={0};
	bits.bits_offset = *UsedBits; bits.total_bits = *TotalBits;
	bits.buffer = pbData;

	cfg->audioObjectType = ReadBitsU( &bits, 5 ); //(5);
	if ( cfg->audioObjectType == 31 )  //audioObjectTypeExt
		cfg->audioObjectType = 32 + ReadBitsU( &bits, 6 );

	samplingFrequencyIndex =  ReadBitsU( &bits, 4 );//(4);
	if ( samplingFrequencyIndex == 15 )
		cfg->SamplingFrequency = ReadBitsU( &bits, 24 );//(24)
	else
		cfg->SamplingFrequency = AAC_sampling_Frequency[samplingFrequencyIndex];

	cfg->channelConfiguration = ReadBitsU( &bits, 4 );//(4);
	//sbrPresentFlag = 0;
	cfg->extensionAudioObjectType = 0;
	if ( cfg->audioObjectType == 5 ) //explicit SBR signaling
	{
		int extensionSamplingFrequencyIndex;
		cfg->extensionAudioObjectType = cfg->audioObjectType;
		//sbrPresentFlag = 1;
		extensionSamplingFrequencyIndex = ReadBitsU( &bits, 4 );//(4);
		if ( extensionSamplingFrequencyIndex == 15 )
			cfg->extensionSamplingFrequency = ReadBitsU( &bits, 24 ); //(24)
		else
			cfg->extensionSamplingFrequency = AAC_sampling_Frequency[extensionSamplingFrequencyIndex];
	};
	switch ( cfg->audioObjectType ) {
	case 1:; case 2:; case 3:; case 4:; case 6:; case 7:; case 17:; case 19:; case 22:; case 23:
		_GASpecificConfig( cfg, pbData, &bits.bits_offset, &bits.total_bits  );
		break;
	};

	SkipBits( &bits, bits.bits_offset & 0x07 ); //align byte
	*UsedBits = bits.bits_offset; *TotalBits = bits.total_bits;
};
*/

static int ProgramConfigElement( LATM_CFG* cfg, BITS_I *pBits  )
{
	int num_front_channel_elements, num_side_channel_elements;
	int num_back_channel_elements, num_lfe_channel_elements;
	int num_assoc_data_elements, num_valid_cc_elements;
	int comment_field_bytes;
	int channels = 0;
	int i;
	
	SkipBits( pBits, 4  ); //element_inst_tag
	SkipBits( pBits, 2  ); //object type
	SkipBits( pBits, 4  ); //sample frq inxe
	num_front_channel_elements = ReadBitsU( pBits, 4 );
	num_side_channel_elements  = ReadBitsU( pBits, 4 );
	num_back_channel_elements =  ReadBitsU( pBits, 4 );
	num_lfe_channel_elements  =  ReadBitsU( pBits, 2 );
	num_assoc_data_elements  =  ReadBitsU( pBits, 3 );
	num_valid_cc_elements  =  ReadBitsU( pBits, 4 );

	if ( num_front_channel_elements ) channels++;
	if ( num_side_channel_elements ) channels+=2;
	if ( num_back_channel_elements ) channels++;
	if ( num_lfe_channel_elements ) channels++;

    if ( ReadBitsU( pBits, 1 ) ) //mono_mixdown_present
	   SkipBits( pBits, 4 );        //mono_mixdown_element_number
    if ( ReadBitsU( pBits, 1 ) ) //stereo_mixdown_present
	   SkipBits( pBits, 4 );        //stereo_mixdown_element_number
    if ( ReadBitsU( pBits, 1 ) ) // matrix_mixdown_idx_present
    {
		SkipBits( pBits, 3 );		//matrix_mixdown_idx
		SkipBits( pBits, 1 );		//pseudo_surround_enable;
    }
    
    for ( i = 0; i<num_front_channel_elements; i++ )
	{
		SkipBits( pBits, 1 ); //front_element_is_cpe[i]
		SkipBits( pBits, 4 ); //front_element_tag_select[i]
	};

    for ( i = 0; i <num_side_channel_elements; i++) 
	{
		SkipBits( pBits, 1 ); //side_element_is_cpe[i]
		SkipBits( pBits, 4 ); //side_element_tag_select[i]
	};

    for ( i = 0; i<num_back_channel_elements; i++ )
	{
		SkipBits( pBits, 1 ); //back_element_is_cpe[i];
		SkipBits( pBits, 4 ); //back_element_tag_select[i];
	}
    for ( i = 0; i<num_lfe_channel_elements; i++ )
	{
		SkipBits( pBits, 4 ); //lfe_element_tag_select[i];
	}
    for ( i = 0; i<num_assoc_data_elements; i++ )
	{
		SkipBits( pBits, 4 ); //assoc_data_element_tag_select[i];
	}
    for ( i = 0; i<num_valid_cc_elements; i++ )
	{
		SkipBits( pBits, 1 ); //cc_element_is_ind_sw[i]
		SkipBits( pBits, 4 ); //valid_cc_element_tag_select[i];
	}

	SkipBits( pBits, (8-(pBits->bits_offset&0x07)) ); //byte_alignment() relative to audio specific config

    comment_field_bytes = ReadBitsU( pBits, 8 );
    SkipBits( pBits, 8*comment_field_bytes ); //comment_field_data[i]
	//*UsedBits = bits.bits_offset;	*TotalBits = bits.total_bits;
	return channels;
}


static int GASpecificConfig( LATM_CFG* cfg, BITS_I *pBits )
{
	//int  bits_offset, total_bits;
	int ext_flag;

	SkipBits( pBits, 1 );			  //framelen_flag
	if ( ReadBitsU( pBits, 1 ) ) //dependsOnCoder
		SkipBits( pBits, 14 );       //delay

	ext_flag = ReadBitsU( pBits, 1 );
	if ( cfg->channelConfiguration == 0 )
	{
		//cfg->channel_num = _ProgramConfigElement( cfg, pBits->buffer, &pBits->bits_offset, &pBits->total_bits  );
		cfg->channel_num = ProgramConfigElement( cfg, pBits );
	}
	if ( cfg->audioObjectType == 6 || cfg->audioObjectType == 20 ) 
		SkipBits( pBits, 3 ); //layerNr

	if ( ext_flag ) 
	{
		switch ( cfg->audioObjectType ) {
		case 22:
			SkipBits( pBits, 5 );  //numOfSubFrame 5);
			SkipBits( pBits, 11 ); //layer_length  11;
			break;
		case 17:
		case 19:
		case 20:
		case 23:
			SkipBits( pBits, 3 ); //stuff
			break;
		}
		SkipBits( pBits, 1 ); //extflag3
	}
	//*UsedBits = bits.bits_offset;	*TotalBits = bits.total_bits;
	return 1;
}


static void ReadAudioSpecificConfig( LATM_CFG* cfg, BITS_I *pBits )
{
	int samplingFrequencyIndex;

	cfg->audioObjectType = ReadBitsU( pBits, 5 ); //(5);
	if ( cfg->audioObjectType == 31 )  //audioObjectTypeExt
		cfg->audioObjectType = 32 + ReadBitsU( pBits, 6 );

	samplingFrequencyIndex =  ReadBitsU( pBits, 4 );//(4);
	if ( samplingFrequencyIndex == 15 )
		cfg->SamplingFrequency = ReadBitsU( pBits, 24 );//(24)
	else
		cfg->SamplingFrequency = AAC_sampling_Frequency[samplingFrequencyIndex];

	cfg->channelConfiguration = ReadBitsU( pBits, 4 );//(4);
	//sbrPresentFlag = 0;
	cfg->extensionAudioObjectType = 0;
	if ( cfg->audioObjectType == 5 ) //explicit SBR signaling
	{
		int extensionSamplingFrequencyIndex;
		cfg->extensionAudioObjectType = cfg->audioObjectType;
		//sbrPresentFlag = 1;
		extensionSamplingFrequencyIndex = ReadBitsU( pBits, 4 );//(4);
		if ( extensionSamplingFrequencyIndex == 15 )
			cfg->extensionSamplingFrequency = ReadBitsU( pBits, 24 ); //(24)
		else
			cfg->extensionSamplingFrequency = AAC_sampling_Frequency[extensionSamplingFrequencyIndex];
	};
	switch ( cfg->audioObjectType ) {
	case 1:; case 2:; case 3:; case 4:; case 6:; case 7:; case 17:; case 19:; case 22:; case 23:
		//_GASpecificConfig( cfg, pBits->buffer, &pBits->bits_offset, &pBits->total_bits  );
		GASpecificConfig( cfg, pBits  );
		break;
	};

	SkipBits( pBits, pBits->bits_offset & 0x07 ); //align byte
};

#define MAX_LATM_STREAM_NUM    64
static int StreamMuxConfig( AAC_AUDIO *aac, const uint8_t * pbData, int Size )
{
	//LATM parsing according to ISO/IEC 14496-3
	int num, prog, lay, streamCnt;
	uint32_t frame_length_type;
	int use_same_config,  all_same_framing;
	int audio_mux_version, frameLength;
	int numProgram, numSubFrames;
	int objTypes[8];
	LATM_CFG cfgs[MAX_LATM_STREAM_NUM+32]={0};
	LATM_CFG* cfg;

	//int  bits_offset, total_bits;
	BITS_I bits={0};
	bits.bits_offset = 1; bits.total_bits = Size*8-1;
	bits.buffer = pbData;

	audio_mux_version = ReadBitsU( &bits, 1 ); //1bits 
	if ( audio_mux_version == 1 ) 
	{
		return 0;
		if ( ReadBitsU( &bits, 1 ) != 0 )
			return 0; //tbd

	}
	all_same_framing = ReadBitsU( &bits, 1 );//(1)
	numSubFrames = ReadBitsU( &bits, 6 );//(6);
	numProgram = ReadBitsU( &bits, 4 );//(4);

  //DVB TS takes multiplexing, only uses one subframe, one program and one layer 
  // and allStreamsSameTimeFraming is always = 1. Though
  //the StreamMuxConfig is parsed and stored completely (maybe we can use it later), the
  //parse first program first layer.

  streamCnt = 0;
  for ( prog = 0; prog <= numProgram; prog++ )
  {
    num = ReadBitsU( &bits, 3 );//(3);
    //numLayer[prog] = num;
    //SetLength(streams,Length(streams)+num+1);
    //SetLength(config,Length(config)+num+1);

    for ( lay = 0; lay <= num; lay++ )
    {
			if ( MAX_LATM_STREAM_NUM <= streamCnt ) return 0;
			
			cfg = &cfgs[streamCnt];
			frameLength = 0;

			//streamID[prog][lay] = streamCnt++;
			//progSIndex = prog;
			//laySIndex = lay;
			if ((prog == 0) && (lay == 0)) 
				use_same_config = 0;
			else
				use_same_config = ReadBitsU( &bits, 1 );

			if ( use_same_config )   // same as before
			{
			}
			else 
			if ( audio_mux_version == 0 ) //audio specific config.
			{
				//_ReadAudioSpecificConfig( cfg, pbData, &bits.bits_offset, &bits.total_bits );
				ReadAudioSpecificConfig( cfg, &bits );
				if ( lay == 0 )
				{
					aac->channels = cfg->channelConfiguration ? cfg->channelConfiguration : cfg->channel_num;
					aac->samples_per_sec = cfg->SamplingFrequency;
					aac->object_type = cfg->audioObjectType;
				}
			}
			else 
			{
				//ZQ, I don't process audio_mux_version > 0 stream
				SageLog(( _LOG_ERROR, 3, TEXT("ERROR: AAC Audio mux version should be 0. (LINE:%d\n" ), __LINE__ ));
				return 0;
			};

			objTypes[lay] = cfg->audioObjectType;
			
			frame_length_type = ReadBitsU( &bits, 3 ); //(3);
			switch ( frame_length_type ) {
				case  0:
					{
						int latmBufferFullness;
						latmBufferFullness = ReadBitsU( &bits, 8 );//(8);
						if ( all_same_framing == 0 )
							if ( (objTypes[lay] == 6 || objTypes[lay] == 20) &&
								 ( objTypes[lay-1] == 8 || objTypes[lay-1] == 24 ) )   
									SkipBits( &bits, 6 ); //(6);core_frame_offset
					}
					break;
				case 1:
					frameLength = ReadBitsU( &bits, 9 );//(9); 
					break;
				case 3: case 4: case 5:
					SkipBits( &bits, 6 );//(6); celp_table_index
					break;
				case 6: case 7:
					SkipBits( &bits, 1 );//(1); hvxc_table_index
					break;
			};
		 
			if ( lay == 0 && frameLength )  aac->frame_length = frameLength;

		streamCnt++;
    }; //for lay = 0 to numLayer[prog]
  }; //for prog = 0 to numProgram

  // other data
  if ( ReadBitsU( &bits, 1 ) ) // other data present
  {
		while ( ReadBitsU( &bits, 1 )  ) //esc
			SkipBits( &bits, 8 );//other_data_bits
  }  

  // CRC
  if ( ReadBitsU( &bits, 1 ) )
		SkipBits( &bits, 8 ); //config_crc (8);

  return 1;
}



int ReadAAC_AudioHeader( AAC_AUDIO *pAACAudio, const uint8_t* pStart, int nSize )
{
	int ret = 0;
	
	 //ADTS header is following GoupStart
	ret	= UnpackAACAudioADTS( pAACAudio, pStart, nSize );

	if ( !ret ) 
		ret = UnpackAACAudioLATM( pAACAudio, pStart, nSize );

	return ret;	
}

int ReadAACHE_AudioHeader( AAC_AUDIO *pAACAudio, const uint8_t* pStart, int nSize )
{
	int ret;
	ret = UnpackAACHEAudio( pAACAudio, pStart, nSize );
	return ret;	
}





