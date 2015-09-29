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
#include "NativeCore.h"
#include "TSCRC32.h"
#include "SectionData.h"

static int SectionCrcCheck( TS_SECTION* pSection );
static int PushSectionData( TS_SECTION* pSection, char* pData, int Bytes );
static uint32_t GetCrc32( uint8_t* p );
//static int PopSectionData( TS_SECTION* pSection, char* pData, int Bytes );
//static void SetCrc32( uint8_t* p, uint32_t crc32 );
TS_SECTION* CreateSection(  )
{
	TS_SECTION* pSection = SAGETV_MALLOC( sizeof(TS_SECTION) );
	pSection->data = NULL;
	pSection->section_type = 0xff;
	pSection->total_bytes = 0;
	pSection->bytes = 0;
	pSection->crc32 = 0;
	pSection->start_offset = 0;
	return pSection;
}

void ReleaseSection( TS_SECTION* pSection )
{
	if ( pSection->data != NULL )
		SAGETV_FREE( pSection->data );
	SAGETV_FREE( pSection );
}

void ResetSection( TS_SECTION* pSection )
{
	pSection->section_type = 0xff;
	pSection->total_bytes = 0;
	pSection->bytes = 0;
	pSection->crc32 = 0;
	pSection->start_offset = 0;
}

static int FlushSectionData( TS_SECTION* pSection )
{
	const uint8_t* section_data;
	int nSize;
	uint16_t section_length;

	nSize = pSection->left_over_size;
	pSection->left_over_size = 0;
	section_data = (uint8_t*)pSection->left_over;

	if ( ( section_data[1] & 0x80)	!= 0x80	) //missing section syntax indicator	
		return 0;

	section_length = (((section_data[1] & 0x0f) << 8) | section_data[2]) - 1 ;
	if ( section_length > 4096+64 ) //invalid section
		return 0;

	//if ( pSection->data == NULL || pSection->data_size < section_length )
	//{
	//	if ( pSection->data != NULL )
	//		SAGETV_FREE( pSection->data );
	//	pSection->data_size = 0;
	//	pSection->data = (char*)SAGETV_MALLOC( section_length + 64 );
	//	if ( pSection->data != NULL )
	//		pSection->data_size = section_length;
	//}
    AllocSectionData( pSection, section_length + 64  );
	StartSection( pSection, section_length );

	if ( PushSectionData( pSection, (char*)section_data, nSize ) )
	{
		if ( SectionCrcCheck( pSection ) )
		{
			pSection->section_type = *pSection->data;
			pSection->crc32 = GetCrc32( pSection->data + pSection->total_bytes -4 );
			return pSection->bytes;
		}
	}

	return 0;
}

int UnpackSection( int bStartFlag, TS_SECTION* pSection, const uint8_t*  pbData, int nSize )
{
	const uint8_t* section_data; 
	int   section_length;
	int   last_bytes, offset = 0;

	if ( pSection->left_over_size > 0 )
	{
		if ( FlushSectionData( pSection ) )
		{
			if ( bStartFlag && nSize < sizeof(pSection->left_over) )
			{
				memcpy( pSection->left_over, pbData, nSize );
				pSection->left_over_size = nSize;
			}
			return pSection->bytes;
		}
	}

	if ( bStartFlag )
	{
		section_data = pbData;
		if ( bStartFlag == 1 ) 
		{
			offset = 1;
			section_data += *((uint8_t *)pbData) + 1; 
			if ( section_data > pbData + nSize )
				return 0;
		}

		last_bytes = (int)(section_data - pbData) - 1;
		if ( last_bytes > 0 )
		{
			//if ( pSection->data_size == pSection->bytes + last_bytes && pSection->data != NULL )
			if ( pSection->data != NULL )
			{
				if ( PushSectionData( pSection, (char*)pbData+1, last_bytes ) )
				{
					if ( SectionCrcCheck( pSection ) )
					{
						if ( nSize - last_bytes - 1 < sizeof(pSection->left_over ) )
						{
							memcpy( pSection->left_over, section_data, nSize - last_bytes - 1 );
							pSection->left_over_size = nSize - last_bytes - 1;
						}
						pSection->section_type = *pSection->data;
						pSection->crc32 = GetCrc32( pSection->data + pSection->total_bytes -4 );
						return pSection->bytes + offset;
					}
				}
			}
		}

		if ( ( section_data[1] & 0x80)	!= 0x80	) //missing section syntax indicator	
			return 0;

		section_length = (((section_data[1] & 0x0f) << 8) | section_data[2]) - 1 ;
		if ( section_length < 0 ) //invalid section
			return 0;

		AllocSectionData( pSection, section_length + 64  );
		StartSection( pSection, section_length );

	} else
	  section_data = pbData;

    if ( pSection->data == NULL )
		  return 0;

	if ( PushSectionData( pSection, (char*)section_data, nSize-(int)(section_data-pbData) ) )
	{
		if ( SectionCrcCheck( pSection ) )
		{
			pSection->section_type = *pSection->data;
			pSection->crc32 = GetCrc32( pSection->data + pSection->total_bytes -4 );
			return pSection->bytes + offset;
		}
		else
			return 0;
	}

	return 0;
}


uint8_t* StartSection( TS_SECTION* pSection, int nSectionLength )
{
	ASSERT( nSectionLength + 4 <= pSection->data_size );
	pSection->section_type = 0xff;
	pSection->total_bytes = nSectionLength + 4; // 4 Byte CRC
	pSection->bytes = 0;
	pSection->crc32 = 0;
	return pSection->data;
}


static int PushSectionData( TS_SECTION* pSection, char* pData, int Bytes )
{
	Bytes = _MIN( pSection->total_bytes - pSection->bytes, Bytes );
	if ( Bytes > 0 )
	{
		memcpy( pSection->data + pSection->bytes, pData, Bytes );
		pSection->bytes += Bytes;
	}
	return  ( pSection->bytes == pSection->total_bytes );
}

//static int PopSectionData( TS_SECTION* pSection, char* pData, int Bytes )
//{
//	Bytes = _MIN( pSection->total_bytes - pSection->bytes, Bytes );
//	if ( Bytes > 0 )
//	{
//		memcpy( pData, pSection->data + pSection->bytes, Bytes );
//		pSection->bytes += Bytes;
//	}
//	return  Bytes;
//}

static int SectionCrcCheck( TS_SECTION* pSection )
{
	return ( CalTSCRC32( (const uint8_t*)pSection->data, pSection->total_bytes ) == 0 );
}

//static void SetCrc32( uint8_t* p, uint32_t crc )
//{
//	*(p++) = (uint8_t)((crc >> 24) & 0xff);
//	*(p++) = (uint8_t)(crc >> 16) & 0xff;
//	*(p++) = (uint8_t)(crc >> 8) & 0xff;
//	*(p++) = (uint8_t)(crc) &  0xff;
//}

static uint32_t GetCrc32( uint8_t* p )
{
	uint32_t crc;
	crc =  *(p++)<<24;
	crc |= *(p++)<<16;
	crc |= *(p++)<<8;
	crc |= *(p++);
	return crc;
}


void UnpackSectionDataHeader( SECTION_HEADER* pSectionHeader, TS_SECTION* pSection )
{
	uint8_t* data;
	data = (uint8_t*)pSection->data + pSection->start_offset;
	pSectionHeader->table_id	  = data[0];
	pSectionHeader->section_length= ((data[1] & 0x0f) << 8) | data[2];
	pSectionHeader->tsid		  = (data[3] << 8)	+ data[4];
	pSectionHeader->version		  = (data[5]	& 0x3E)	>> 1;
	pSectionHeader->current_next_indicator=	data[5] &	0x01;
	pSectionHeader->section_number = data[6];    
	pSectionHeader->last_section_number = data[7];
	pSectionHeader->table_data = data+8;
	pSectionHeader->table_bytes = pSectionHeader->section_length-8;
	pSectionHeader->section_ptr = pSection;
}

uint8_t *AllocSectionData( TS_SECTION* pSection, int nDataLength )
{
	nDataLength += 4; //4 bytes CRC
	if ( pSection->data == NULL || pSection->data_size < nDataLength )
	{
		if ( pSection->data != NULL )
			SAGETV_FREE( pSection->data );
		pSection->data = (uint8_t*)SAGETV_MALLOC( nDataLength );
		if ( pSection->data != NULL )
			pSection->data_size = nDataLength;
	}
	pSection->bytes = 0;
	return pSection->data;
}

int SealSectionData( TS_SECTION* pSection, int nDataLength )
{
	uint32_t crc;
	uint8_t *p;
	int len;
	if ( nDataLength+4 > pSection->data_size  )
		return 0;

	p = (uint8_t*)pSection->data;
	*p = pSection->start_offset > 0 ? pSection->start_offset-1 : 0;

	p = (uint8_t*)pSection->data + pSection->start_offset;
	pSection->total_bytes = nDataLength+4;  //add 4 bytes for crc32
	len = nDataLength-pSection->start_offset+1;
	p[1] |= ( nDataLength  >> 8 ) & 0x0f;
	p[2]  = ( nDataLength ) & 0xff;
	
	crc = CalTSCRC32( p, nDataLength-pSection->start_offset );
	p = (uint8_t *)(  p + nDataLength-pSection->start_offset);
	*(p++) = (uint8_t)((crc >> 24) & 0xff);
	*(p++) = (uint8_t)(crc >> 16) & 0xff;
	*(p++) = (uint8_t)(crc >> 8) & 0xff;
	*(p++) = (uint8_t)(crc) &  0xff;
	return 1;
}

TS_SECTION* DupSection( TS_SECTION* pSection )
{
	TS_SECTION* pNewSection = CreateSection(  );
	pNewSection->data = SAGETV_MALLOC( pSection->total_bytes );
	memcpy( pNewSection->data, pSection->data, pSection->total_bytes );
	pNewSection->total_bytes =  pSection->total_bytes;
	pNewSection->section_type = pSection->section_type;
	pNewSection->bytes = pSection->bytes;
	pNewSection->crc32 = pSection->crc32;
	return pNewSection;
}

int BuildSectionHeader( SECTION_HEADER* pSectionHeader, TS_SECTION* pSection )
{
	uint8_t* data;
	data = (uint8_t*)pSection->data + pSection->start_offset;
	data[0] = pSectionHeader->table_id;
	data[1] = 0xb0;
	data[1] |= ( pSectionHeader->section_length >> 8 ) & 0x0f;
	data[2] = ( pSectionHeader->section_length ) & 0xff;
	data[3] = pSectionHeader->tsid >> 8;
	data[4] = pSectionHeader->tsid & 0xff;
	data[5] = 0xc0| (( pSectionHeader->version << 1 ) & 0x3e);
	data[5] |= pSectionHeader->current_next_indicator & 0x01;
	data[6] = pSectionHeader->section_number;
	data[7] = pSectionHeader->last_section_number;
	return 8+pSection->start_offset;
}

