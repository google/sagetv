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
	unsigned char	section_type;     //the first byte section data
	unsigned char	counter;		  //used for checking TS_HEADER.continuity_ct 4 bits
	unsigned short  bytes;			  //current bytes in data
	unsigned short  total_bytes;      //section data length
	unsigned short  data_size;        //data allocated size (data_size >= total_bytes >= bytes )
	unsigned short  left_over_size;   //left over bytes
	unsigned char   *data;            //maxium is 4K bytes bytes, 
	unsigned char   start_offset;
	unsigned char   padding;
	unsigned char	left_over[200];   //section data doens't start align with a TS packet, it's left over of unconsumed bytes
	unsigned long	crc32;			  //crc32 for checking section data updated
} TS_SECTION;

typedef struct 
{
	unsigned short section_length;
	unsigned short tsid;
	unsigned char  table_id;
	unsigned char  version;
	unsigned char  current_next_indicator;
	unsigned char  section_number;
	unsigned char  last_section_number;
	unsigned char  *table_data;
	unsigned short table_bytes;
	TS_SECTION     *section_ptr;
} SECTION_HEADER;


TS_SECTION* CreateSection( );
TS_SECTION* DupSection( TS_SECTION* pSection );
void ReleaseSection( TS_SECTION* pSection );
void ResetSection( TS_SECTION* pSection );
unsigned char GetSectionDataType( TS_SECTION* pSection );
int  UnpackSection( int bStartFlag, TS_SECTION* pSection, const unsigned char*  pData, int nSize );
void UnpackSectionDataHeader( SECTION_HEADER* pSectionHeader, TS_SECTION* pSection );
unsigned char *AllocSectionData( TS_SECTION* pSection, int nDataLength );
int  SealSectionData( TS_SECTION* pSection, int nDataLength );
unsigned char* StartSection( TS_SECTION* pSection, int nSectionLength );
int BuildSectionHeader( SECTION_HEADER* pSectionHeader, TS_SECTION* pSection );

#ifdef __cplusplus
}
#endif

#endif

