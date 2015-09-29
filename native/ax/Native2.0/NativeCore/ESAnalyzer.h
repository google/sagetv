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

#ifndef ES_ANALYZER_H
#define ES_ANALYZER_H

#include "TSParser.h"
#include "PSParser.h"

#ifdef __cplusplus
extern "C" {
#endif

//const    ULONGLONG NANOSECONDS = (1000000000);       // 10 ^ 9
//const    ULONGLONG UNITS = (10000000);				 // 10 ^ 7
//const    ULONGLONG UNITS;
#define  UNITS			   (10000000)
#define  MPEG_TIME_DIVISOR (90000)
#define  PTS_UNITS   UNITS/MPEG_TIME_DIVISOR   
#define  UNITS_PTS   MPEG_TIME_DIVISOR/UNITS   
//Beware of using this, you should use in this way "a = a*PTS_UNITS" wrong in a *= PTS_UNITS or a = a*(PTS_UNITS)

#define  MILLSECOND2PTS( x )  ( x*MPEG_TIME_DIVISOR/1000 )
#define  SECOND2PTS( x )	  ( x*MPEG_TIME_DIVISOR )

enum {
	ES_MPEG1   = 0x01,
	ES_MPEG2   = 0x02,
	ES_VC1     = 0x03,
} ;

#define IS_VIDEO_STREAM_ID( StreamId )  ( (StreamId & 0xF0) == 0xE0 )
#define IS_AUDIO_STREAM_ID( StreamId )  ( (StreamId & 0xE0) == 0xC0 )
//#define IS_SUBTITLE_STREAM_ID( StreamId)( (StreamId & 0xF0) == 0x20 )  /* PS stream id, TS use 0xbd for subtitle */
#define IS_PRIVATE1_STREAM_ID( StreamId) ( (StreamId == 0xBD) )
#define IS_OTHER_STREAM_ID( StreamId) ( (StreamId == 0xFD) )
#define IS_PRIVATE2_STREAM_ID( StreamId) ( (StreamId == 0xBF) )
#define IS_PRIVATE_STREAM_ID( StreamId)  ( (StreamId == 0xBD ) || (StreamId == 0xBF ) ) 
#define IS_PES_STREAM_ID(x)	  (  IS_VIDEO_STREAM_ID( x ) ||  IS_AUDIO_STREAM_ID( x ) ||  IS_OTHER_STREAM_ID( x ) || IS_PRIVATE_STREAM_ID( x ) /*|| IS_SUBTITLE_STREAM_ID( x )*/ )
#define IS_TS_PES_STREAM_ID(x)(  IS_VIDEO_STREAM_ID( x ) ||  IS_AUDIO_STREAM_ID( x ) ||  IS_OTHER_STREAM_ID( x ) || IS_PRIVATE_STREAM_ID( x )  )
static inline int IsPESHeaderInPs( const uint8_t* pData, int nBytes )
{
	if ( nBytes < 4 )
		return 0;

	if ( pData[0] != 0x0 || pData[1] != 0x0 || pData[2] != 0x1 )
		return 0;

	return IS_PES_STREAM_ID( pData[3] ) /*|| IS_SUBTITLE_STREAM_ID( pData[3] )*/;
}

static inline int IsPESHeaderInTs( const uint8_t* pData, int nBytes )
{
	if ( nBytes < 4 )
		return 0;

	if ( pData[0] != 0x0 || pData[1] != 0x0 || pData[2] != 0x1 )
		return 0;

	return IS_TS_PES_STREAM_ID( pData[3] );
}

//check if it's expected stream id or TS stream id
static inline int IsPESHeaderOfTS( uint8_t StrmID, const uint8_t* pData, int nBytes )
{
	if ( nBytes < 4 )
		return 0;

	if ( pData[0] != 0x0 || pData[1] != 0x0 || pData[2] != 0x1 )
		return 0;

	if ( StrmID )
		return (pData[3]==StrmID);

	return IS_TS_PES_STREAM_ID( pData[3] );
}

static inline void CleanUpPES( PES* pPES )
{
	pPES->header_length = 0;
	pPES->packet_length = 0; 
}

static inline uint16_t MakeESId( uint8_t cStreamId, uint8_t cStreamSubId )
{
	if ( IS_VIDEO_STREAM_ID(cStreamId) )
	{
		return 0xE000 | cStreamSubId;
	} else
	if ( IS_AUDIO_STREAM_ID(cStreamId) )
	{
		return 0xC000 | cStreamSubId;
	} else
		return ( cStreamId << 8 ) | cStreamSubId;
}

static inline void AssignPTS( PES *pPES1, PES *pPES2 )
{
	if ( (pPES1->has_pts = pPES2->has_pts) != 0 )
		pPES1->pts = pPES2->pts;
	if ( (pPES1->has_dts = pPES2->has_dts) != 0 )
		pPES1->dts = pPES2->dts;
}

int  ReadPESHeader( const uint8_t* pData, int nBytes, PES *pPES );
void ReadPESSubId( PES* pPES, const uint8_t *pData, int nBytes );

int AnylyzePSESElement( ES_ELEMENT *pESEelement, struct PSM_LIST* pPSMList,const uint8_t* pData, int nSize  ); 
int AnylyzeTSESElement( ES_ELEMENT *pESElmnt );
uint8_t* GetPSESDesc( ES_ELEMENT *pESElmnt, PSM_LIST *pPSMList );
//debug utility
void _prints_es_elmnt( ES_ELEMENT* elmnt, int slot_index, int scrambling_flag );
char* _time_stamp( ULONGLONG llTime, char* pBuffer, int nSize );

#ifdef __cplusplus
}
#endif


#endif


