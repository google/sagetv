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

#ifndef		SECTION_DATA_H
#define SECTION_DATA_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct
{
	uint8_t	  section_type;     //the first byte section data
	uint8_t	  counter;		  //used for checking TS_HEADER.continuity_ct 4 bits
	uint16_t  bytes;			  //current bytes in data
	uint16_t  total_bytes;      //section data length
	uint16_t  data_size;        //data allocated size (data_size >= total_bytes >= bytes )
	uint16_t  left_over_size;   //left over bytes
	uint8_t   *data;            //maxium is 4K bytes bytes, 
	uint8_t   start_offset;
	uint8_t   padding;
	uint8_t	  left_over[200];   //section data doens't start align with a TS packet, it's left over of unconsumed bytes
	uint32_t	crc32;			  //crc32 for checking section data updated
} TS_SECTION;

typedef struct 
{
	uint16_t section_length;
	uint16_t tsid;
	uint8_t  table_id;
	uint8_t  version;
	uint8_t  current_next_indicator;
	uint8_t  section_number;
	uint8_t  last_section_number;
	uint8_t  *table_data;
	uint16_t table_bytes;
	TS_SECTION *section_ptr;
} SECTION_HEADER;


TS_SECTION* CreateSection( );
TS_SECTION* DupSection( TS_SECTION* pSection );
void ReleaseSection( TS_SECTION* pSection );
void ResetSection( TS_SECTION* pSection );
uint8_t GetSectionDataType( TS_SECTION* pSection );
int  UnpackSection( int bStartFlag, TS_SECTION* pSection, const uint8_t*  pData, int nSize );
void UnpackSectionDataHeader( SECTION_HEADER* pSectionHeader, TS_SECTION* pSection );
uint8_t *AllocSectionData( TS_SECTION* pSection, int nDataLength );
int  SealSectionData( TS_SECTION* pSection, int nDataLength );
uint8_t* StartSection( TS_SECTION* pSection, int nSectionLength );
int BuildSectionHeader( SECTION_HEADER* pSectionHeader, TS_SECTION* pSection );

#ifdef __cplusplus
}
#endif

#endif

