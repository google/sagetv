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
#include "H264Format.h"

#define NAL_SLICE                1
#define NAL_DPA                  2
#define NAL_DPB                  3
#define NAL_DPC                  4
#define NAL_IDR_SLICE            5
#define NAL_SEI                  6
#define NAL_SPS                  7
#define NAL_PPS                  8
#define NAL_AUD                  9
#define NAL_END_SEQUENCE        10
#define NAL_END_STREAM          11
#define NAL_FILLER_DATA         12
#define NAL_SPS_EXT             13
#define NAL_AUXILIARY_SLICE     19

float H264AspectRatioF( uint16_t nomi, uint16_t deno, int32_t width, int32_t height )
{
	if ( height == 0 || width == 0 ) return 0;
	if ( nomi == 0 || deno == 0 ) return 0;

	if ( nomi == 1 && deno == 1 )
		return (float)((1.0*width)/(1.0*height));

	return ((float)nomi*width/(deno*height));

}

static int GCD( int n, int m )
{
	if ( m )
		return GCD( m, n % m );
	else
		return n;
}

static int LCMNomiCalculator( int n, int m )
{
	int gcd = GCD( n, m );
	return n/gcd;
}

static int LCMDenoCalculator( int n, int m )
{
	int gcd = GCD( n, m );
	return m/gcd;
}

int H264AspectRatioNomiValue( uint16_t nomi, uint16_t deno, int32_t width, int32_t height )
{
	int n, d;
	if ( height == 0 || width == 0 ) return 0;
	if ( nomi == 0 || deno == 0 ) return 0;

	if ( nomi == 1 && deno == 1 )
		return LCMNomiCalculator( width, height );

	n = nomi * width;
	d = deno * height;

	return LCMNomiCalculator( n, d );
}

int H264AspectRatioDenoValue( uint16_t nomi, uint16_t deno, int32_t width, int32_t height )
{
	int n, d;
	if ( height == 0 || width == 0 ) return 0;
	if ( nomi == 0 || deno == 0 ) return 0;

	if ( nomi == 1 && deno == 1 )
		return LCMDenoCalculator( width, height );

	n = nomi * width;
	d = deno * height;

	return LCMDenoCalculator( n, d );
}



static int NAL2RBSP( unsigned char* src, unsigned char* dst, int size )
{
	int si=0, di=0;
  while(si<size)
	{    
        if(si+2<size && src[si]==0 && src[si+1]==0 && src[si+2]<=3){
            if(src[si+2]==3){ //escape
                dst[di++]= 0;
                dst[di++]= 0;
                si+=3;
                continue;
            }
			//else //next start code
   //             return di;
        }

        dst[di++]= src[si++];
    }
	return di;
}

static void skip_scaling_List(  int sizeOfScalingList, BITS_I *pBits )                                                                                
{      
	const unsigned char ZZ_SCAN[16]  =                                                                                                                                                                 
	{  0,  1,  4,  8,  5,  2,  3,  6,  9, 12, 13, 10,  7, 11, 14, 15                                                                                                                          
	};                                                                                                                                                                                        
                                                                                                                                                                                          
	const unsigned char ZZ_SCAN8[64] =                                                                                                                                                                 
		{  0,  1,  8, 16,  9,  2,  3, 10, 17, 24, 32, 25, 18, 11,  4,  5,                                                                                                                         
   		12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13,  6,  7, 14, 21, 28,                                                                                                                        
   		35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,                                                                                                                        
   		58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63                                                                                                                         
		};    

	int scalingList[64];

	int j,     scanj;                                                                                                                                                                           
	int delta_scale, lastScale, nextScale;  
	lastScale = 8;                                                                                                                                                                     
	nextScale = 8;                                                                                                                                                                     
	for(j=0; j<sizeOfScalingList; j++)                                                                                                                                                      
	{                                                                                                                                                                                       
		scanj = (sizeOfScalingList==16) ? ZZ_SCAN[j]:ZZ_SCAN8[j];                                                                                                                             
		                                                                                                                                                                                        
		if(nextScale!=0)                                                                                                                                                                      
		{                                                                                                                                                                                     
			delta_scale = ReadSE( pBits );
			nextScale = (lastScale + delta_scale + 256) % 256;                                                                                                                                  
		}                                                                                                                                                                                     
		scalingList[scanj] = (nextScale==0) ? lastScale:nextScale;                                                                                                                            
		lastScale = scalingList[scanj];                                                                                                                                                       
	}    

}  

static int InterpretProfileLevel( H264_VIDEO *pH264Video )
{
	//int ret = 1; 
	if ( pH264Video->profile == 66 )
	{
		//A.2.1	Baseline profile
	} else
	if ( pH264Video->profile == 77 )
	{
		//A.2.2	Main profile

	} else
	if ( pH264Video->profile == 88 )
	{
		//A.2.3	Extended profile

	} else
	if ( pH264Video->profile == 83 )
	{
	} else
	if ( pH264Video->profile == 100 )
	{
		//A.2.4	High profile

	} else
	if ( pH264Video->profile == 110 )
	{
		//A.2.5	High 10 profile

	} else
	if ( pH264Video->profile == 122 )
	{
		//A.2.6	High 4:2:2 profile

	} else
	if ( pH264Video->profile == 144 )
	{
		//A.2.7	High 4:4:4 profile
	} else
	{
		//printf( "unkonw profile %d\n", pH264Video->profile );
		return -1;
	}

	if ( pH264Video->level == 10 ) //1
	{
		pH264Video->cbr = 64000;
	} else
	if ( pH264Video->level == 11 && pH264Video->constraint_set2 ) //1.b
	{
		pH264Video->cbr = 128000;
	} else
	if ( pH264Video->level == 11 ) //1.1
	{
		pH264Video->cbr = 192000;
	} else
	if ( pH264Video->level == 12 ) //1.2
	{
		pH264Video->cbr = 384000;
	} else
	if ( pH264Video->level == 13 ) //1.3
	{
		pH264Video->cbr = 768000;
	} else
	if ( pH264Video->level == 20 ) //2.0
	{
		pH264Video->cbr = 2000000;
	} else
	if ( pH264Video->level == 21 ) //2.1
	{
		pH264Video->cbr = 4000000;
	} else
	if ( pH264Video->level == 22 ) //2.2
	{
		pH264Video->cbr = 4000000;
	} else
	if ( pH264Video->level == 30 ) //3.0
	{
		pH264Video->cbr = 10000000;
	} else
	if ( pH264Video->level == 31 ) //3.1
	{
		pH264Video->cbr = 14000000;
	} else
	if ( pH264Video->level == 32 ) //3.2
	{
		pH264Video->cbr = 20000000;
	} else
	if ( pH264Video->level == 40 ) //4.0
	{
		pH264Video->cbr = 20000000;
	} else
	if ( pH264Video->level == 41 ) //4.1
	{
		pH264Video->cbr = 50000000;
	} else
	if ( pH264Video->level == 42 ) //4.2
	{
		pH264Video->cbr = 50000000;
	} else
	if ( pH264Video->level == 50 ) //5.0
	{
		pH264Video->cbr = 135000000;
	} else
	if ( pH264Video->level == 51 ) //5.1
	{
		pH264Video->cbr = 24000000;
	} else
	{
		pH264Video->cbr = -1;
		//printf( "unknow level %d\n", pH264Video->level );
		return -1;
	} 

	return 1;
}

typedef struct  {
	uint16_t nom;
	uint16_t den;
} ASPECT_RATIO;
static const ASPECT_RATIO pixel_aspect[17]={ {0, 1}, {1, 1}, {12, 11}, {10, 11}, {16, 11}, {40, 33},
 {24, 11}, {20, 11}, {32, 11}, {80, 33}, {18, 11}, {15, 11}, {64, 33}, {160,99}, {4, 3}, {3, 2}, {2, 1} };

int ReadH264VideoHeader( H264_VIDEO *pH264Video, const unsigned char* pData, int Size )
{
	const unsigned char* p;
	unsigned char rbsp[256*2];
	int zero_bytes;
	BITS_I bits;
	zero_bytes  = 0;
	p = pData;

	bits.error_flag = 0;
	
	while ( 1 )
	{
		//start code prefix search
		while ( p < pData+Size-1 )
		{
			if ( *p == 0 ) 
				zero_bytes++;
			if ( zero_bytes >= 3 && *p )
			{
				if ( *p != 1 && *p != 3 )
				{
					//illeag byte codes in H.264
					if ( pH264Video->guessH264 > -200 ) pH264Video->guessH264 -= 20; //not H.264 stream
					return 0;
				} else
				if ( *p == 1 ) 
				{
					if ( *(p+1) & 0x80 )  //forbidden bits
					{
						if ( pH264Video->guessH264 > -200 )  pH264Video->guessH264 -= 20; //not H.264 stream
						return 0;
					} else
					{
						p++;
						zero_bytes = 0;
						break;                        //found HAL header
					}
				}
			}
			if ( *p ) zero_bytes = 0;
			p++;
		}

		if ( p < pData+Size-1 )
		{
			int nal_ref_idc, nal_unit_type;
			int bit_num;
			nal_ref_idc = *p>>5;
			nal_unit_type = *p&0x1F;
			p++;

			//static_type[nal_unit_type]++;
			if ( nal_unit_type == NAL_SPS )
			{
				int bytes = ( Size-(int)(p-pData) < (int)sizeof(rbsp) ) ? Size-(int)(p-pData) : (int)sizeof(rbsp) ;
				bytes = NAL2RBSP( (unsigned char*)p, (unsigned char*)rbsp, bytes );
				p += bytes;
				if ( bytes < 4 )
					return 0;  //too little data to parse information

				pH264Video->profile = rbsp[0]; 
				pH264Video->constraint_set0 = rbsp[1] & 0x80 ?  1:0;
				pH264Video->constraint_set1 = rbsp[1] & 0x40 ?  1:0;
				pH264Video->constraint_set2 = rbsp[1] & 0x20 ?  1:0;
				pH264Video->level = rbsp[2];

				if ( ( rbsp[1] & 0x1f ) )
				{
					if ( pH264Video->guessH264 > -200 )  pH264Video->guessH264 -= 20; //not H.264 stream
					return 0;
				}
				
				bits.buffer = rbsp;
				bits.bits_offset = 24;
				bits.total_bits = bytes * 8 - 24;
				
				pH264Video->sps_id = UE( &bits, &bit_num  );
				if ( bit_num > 0 )
				{
					bits.bits_offset += bit_num;
					bits.total_bits  -= bit_num;
				} else
				{
					if ( pH264Video->guessH264 > -200 ) pH264Video->guessH264 -= 10; 
					return 0;
				}
				
				if ( pH264Video->guessH264 < 200 )	pH264Video->guessH264 += 20;

				////////////////////////////////////////////////////////////

				if(( pH264Video->profile == 100 ) ||                                                                  
					(pH264Video->profile == 110 ) ||                                                                       
					(pH264Video->profile ==122 )  ||                                                                       
					(pH264Video->profile ==144 ))                                                                      
				{                                                                                                        
				     // ue("SPS: chroma_format_idc")                                                                             
					int chroma_format_idc  = ReadUE( &bits );
					 // Residue Color Transform                                                                             
					if( chroma_format_idc == 3 ) 
					{	
						ReadBitsU( &bits, 1 ); //int residue_transform_flag = ReadBitsU( &bits, 1 );
					}
					{        
						int seq_scaling_matrix_present_flag;
						ReadUE( &bits );  //int bit_depth_luma_minus8   = ReadUE( &bits );
						ReadUE( &bits );  //int bit_depth_chroma_minus8 = ReadUE( &bits );  
						ReadBitsU( &bits, 1 );  //int lossless_qpprime_flag   = ReadBitsU( &bits, 1 );
						seq_scaling_matrix_present_flag  = ReadBitsU( &bits, 1 );
						if( seq_scaling_matrix_present_flag )           
						{   int i;                                                                                      
							for(i=0; i<8; i++)         
							{                                                                                       
								int seq_scaling_list_present_flag   = ReadBitsU( &bits, 1 );
								if( seq_scaling_list_present_flag )                                   
								{      
									if(i<6)                             
										skip_scaling_List( 16, &bits );              
									else                          
										skip_scaling_List( 64, &bits );    
								}                 
							}                                                                                       
						}                                                                                 
					}  
				}
				{                         
					int pic_order_cnt_type;
					ReadUE( &bits ); //int log2_max_frame_num_minus4              = ReadUE( &bits );
					pic_order_cnt_type  = ReadUE( &bits );
				                                                                                                                                                                                        
					if ( pic_order_cnt_type == 0) 
					{
						ReadUE( &bits );; //int log2_max_pic_order_cnt_lsb_minus4 = ReadUE( &bits );
					}
					else if ( pic_order_cnt_type == 1 )                                                                       
					{        
						int i;
						int num_ref_frames_in_pic_order_cnt_cycle;
						ReadBitsU( &bits, 1 );//int delta_pic_order_always_zero_flag      = ReadBitsU( &bits, 1 );
						ReadSE( &bits ); //int offset_for_non_ref_pic                = ReadSE( &bits );
						ReadSE( &bits ); //int offset_for_top_to_bottom_field        = ReadSE( &bits );
						num_ref_frames_in_pic_order_cnt_cycle = ReadUE( &bits );
						for( i=0; i<num_ref_frames_in_pic_order_cnt_cycle; i++) 
						{
							ReadSE( &bits ); //int offset_for_ref_frame          = ReadSE( &bits );
						}
					} 
				}
				{
					int pic_width_in_mbs_minus1;
					int pic_height_in_map_units_minus1;
					int frame_mbs_only_flag;
					int frame_cropping_flag, frame_cropping_rect_right_offset=0, frame_cropping_rect_bottom_offset=0;

					ReadUE( &bits ); //int num_ref_frames                        = ReadUE( &bits );
					ReadBitsU( &bits, 1 ); //int gaps_in_frame_num_value_allowed_flag  = ReadBitsU( &bits, 1 ); 
					pic_width_in_mbs_minus1               = ReadUE( &bits );
					pic_height_in_map_units_minus1        = ReadUE( &bits );
					frame_mbs_only_flag                   = ReadBitsU( &bits, 1 ); 
					pH264Video->progressive = 0;
					if (!frame_mbs_only_flag)                                                                     
					{                                                                           
						ReadBitsU( &bits, 1 );  //int mb_adaptive_frame_field_flag        = ReadBitsU( &bits, 1 ); 
						pH264Video->progressive = 1;
					}
					pH264Video->width = (pic_width_in_mbs_minus1+1) * 16;
					pH264Video->height = (pic_height_in_map_units_minus1+1) * (2-frame_mbs_only_flag) * 16;

					if ( ( pH264Video->width < 48 || pH264Video->height < 32 ) || /* rule out too small picture 48x32 */
						 ( pH264Video->width * 3 < pH264Video->height * 2 ) ||    /* rule out ratio <2/3 */
						 ( pH264Video->width    > pH264Video->height * 4 )  )      /* rule out ratio > 4 */
					{
						if ( pH264Video->guessH264 > -200 ) pH264Video->guessH264 -= 10; 
						return 0;
					}

					ReadBitsU( &bits, 1 );   //int direct_8x8_inference_flag             = ReadBitsU( &bits, 1 ); 
					frame_cropping_flag                   = ReadBitsU( &bits, 1 ); 
				    
					if (frame_cropping_flag)                                                                      
					{                                                                          
						ReadUE( &bits ); //int frame_cropping_rect_left_offset      = ReadUE( &bits );
						frame_cropping_rect_right_offset     = ReadUE( &bits );
						ReadUE( &bits ); //int frame_cropping_rect_top_offset       = ReadUE( &bits );
						frame_cropping_rect_bottom_offset    = ReadUE( &bits );
					}                                                                          
 
					pH264Video->width -= 2*_MIN( frame_cropping_rect_right_offset, 7 );
					if ( frame_mbs_only_flag )  
						pH264Video->height -= 2 * _MIN( frame_cropping_rect_bottom_offset, 7 );
					else
						pH264Video->height -= 4 * _MIN( frame_cropping_rect_bottom_offset, 3 );

					if ( ReadBitsU( &bits, 1 ) ) //vui_parameters_present_flag
					{
						 if ( ReadBitsU( &bits, 1 ) ) //aspect_ratio_info_present_flag
						 {
					        unsigned int aspect_ratio_idc= ReadBitsU(&bits, 8);
							if( aspect_ratio_idc == 0xff ) 
							{
								pH264Video->ar_nomi = ReadBitsU(&bits, 16);
								pH264Video->ar_deno = ReadBitsU(&bits, 16);
							}else 
							if(aspect_ratio_idc < sizeof(pixel_aspect))
							{
								pH264Video->ar_nomi =  pixel_aspect[aspect_ratio_idc].nom;
								pH264Video->ar_deno =  pixel_aspect[aspect_ratio_idc].den;
							}
							else{
								if ( pH264Video->guessH264 > -200 ) pH264Video->guessH264 -= 10; 
								return 0;
							}
						}
					}
				    if( ReadBitsU( &bits, 1 ) )      // overscan_info_present_flag
						ReadBitsU( &bits, 1 );     // overscan_appropriate_flag 
    
					if( ReadBitsU( &bits, 1 ) )      // video_signal_type_present_flag
					{
						ReadBitsU( &bits, 3 );    // video_format
						ReadBitsU( &bits, 1 );     // video_full_range_flag
						if(ReadBitsU( &bits, 1 ))   // colour_description_present_flag 
						{  
							ReadBitsU( &bits, 8 ); // colour_primaries
							ReadBitsU( &bits, 8 ); // transfer_characteristics
							ReadBitsU( &bits, 8 ); // matrix_coefficients
						}
					 }

					if( ReadBitsU( &bits, 1 ) )	// chroma_location_info_present_flag 
					{      
						ReadUE( &bits );  // chroma_sample_location_type_top_field 
						ReadUE( &bits );  // chroma_sample_location_type_bottom_field 
					}

					if( ReadBitsU( &bits, 1 ) )
					{
						uint32_t num_units_in_tick = ReadBitsU( &bits, 32 );
						uint32_t time_scale        = ReadBitsU( &bits, 32 );
						int fixed_frame_rate_flag       = ReadBitsU( &bits, 1 );
						if ( fixed_frame_rate_flag )
						{
							int nomi, deno;
							nomi = time_scale;
							deno = 2*num_units_in_tick;
							pH264Video->frame_rate_nomi = LCMNomiCalculator( nomi, deno );
							pH264Video->frame_rate_deno = LCMDenoCalculator( nomi, deno );
						}
					}
					pH264Video->sps_length=_MIN((uint16_t)bytes, sizeof(pH264Video->sps)-5) ;
					memcpy( pH264Video->sps+5, rbsp, pH264Video->sps_length);
					pH264Video->sps[0]=pH264Video->sps[1]=pH264Video->sps[2]=0;
					pH264Video->sps[3]=1; pH264Video->sps[4]=0x27;
					pH264Video->sps_length += 5;
					pH264Video->guessH264 = 200;
					break;
				}


				//printf( "profile:%d level:%d width:%d height:%d(spsid:%d)\n", pH264Video->profile, pH264Video->level, 
				//	pH264Video->width, pH264Video->height, pH264Video->sps_id );

/////////////////////////////////////////////////////////////
			} else
			if ( nal_unit_type == NAL_SEI )
			{
				int bytes = ( Size-(int)(p-pData) < (int)sizeof(rbsp) ) ? Size-(int)(p-pData) : (int)sizeof(rbsp) ;
				//printf( "NALU SEI:%d\n", nal_unit_type );
				bytes = NAL2RBSP( (unsigned char*)p, (unsigned char*)rbsp, bytes );
				p += bytes;
			} else
			{
				//skip NALU
				//printf( "NALU type:%d\n", nal_unit_type );
			}
		} else
			break;

	}

	if ( pH264Video->guessH264 >= 20 && pH264Video->level && pH264Video->profile )
	{
		if ( InterpretProfileLevel( pH264Video ) >  0 && !bits.error_flag )
		{
			return 1;
		} else
		{
			if ( pH264Video->guessH264 >=-200 )
				pH264Video->guessH264--;
		}
	}

	return 0;
}


////////////////////////////////////////////////////////////////////////////////////////////

//////////////////////////////////////////////////////////////////





