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

#ifndef _FILE_PARSEPTS_H_
#define _FILE_PARSEPTS_H_

#define  UNITS			   (10000000)
#define  MPEG_TIME_DIVISOR (90000)
#define  PTS_UNITS   UNITS/MPEG_TIME_DIVISOR   
#define  UNITS_PTS   MPEG_TIME_DIVISOR/UNITS   
#define PTS2MT( x ) ( (x)*PTS_UNITS )   //PTS to Media Time ( 0.1 ms )
#define MT2PTS( x ) ( (x)*UNITS_PTS )   //Media Time to PTS
#define PTS2SEC( x ) ( (x)/MPEG_TIME_DIVISOR )
#define SEC2PTS( x ) ( (x)*MPEG_TIME_DIVISOR )

#define ONE_DAY_PTS  ((LONGLONG)24*3600*MPEG_TIME_DIVISOR)
#define ONE_DAY_MT   ((LONGLONG)24*3600*UNITS)
#define PTS_OF_1SEC  (MPEG_TIME_DIVISOR) 
#define PTS_OF_1HOUR (3600*MPEG_TIME_DIVISOR) 
#define PTS_OF_100MS ((MPEG_TIME_DIVISOR/10) 
#define PTS_OF_3HOUR (3*3600*MPEG_TIME_DIVISOR) 
#define MAX_PTS_VALUE (0x1FFFFFFFF)
#define IS_PTS_ROUND_UP( LastPTS, FirstPTS ) (LastPTS + PTS_OF_3HOUR < FirstPTS &&  FirstPTS + PTS_OF_3HOUR > MAX_PTS_VALUE )
#define PTS_ROUND_UP( LastPTS, FirstPTS ) ((LastPTS + PTS_OF_3HOUR < FirstPTS &&  FirstPTS + PTS_OF_3HOUR > MAX_PTS_VALUE ) ? LastPTS+MAX_PTS_VALUE : LastPTS ) 


class CPTSParser
{
public:
	CPTSParser():
	    m_bStreamRebuild(FALSE),
		m_nState(0),
		m_uInputStreamFormat(0),
		m_llUpdatedPTS(0),
		m_pDemuxer(NULL)
	{
	};

	~CPTSParser() {};
	HRESULT Init( CAsyncStream *pStream );
	LONGLONG  ReadFileFirstPTS( CAsyncStream *pStream );
	LONGLONG  ReadFileLastPTS( CAsyncStream *pStream );
	LONGLONG  ReadFileSize( CAsyncStream *pStream );
	LONGLONG  ParsePTS( CAsyncStream *pStream, BYTE* pBuffer, LONGLONG* llPos, DWORD dwBytes );

private:
	void ResetDemuxerPTS( );
	void ResetTracksPTS( );
	void EnableFastPTSParser();
	void DisableFastPTSParser();
	void ZeroDemuxer( );
	void DemuxerReset( );
	int  PushData( unsigned char* pbData, int nBytes );

	static int PTSDataDumper( void* pContext, void* pData, int nSize );
	static int AVInfMessageDumper( void* pContext, void* pData, int nSize );

	void*	  m_pDemuxer;

	int		  m_nUpdatedPTSTrack;
	ULONGLONG m_llUpdatedPTS;

	WORD m_uInputStreamFormat;
	WORD m_nState;
	BOOL m_bStreamRebuild;

	LONGLONG	m_llFirstPTS;				//first Picture Time Stamp
	LONGLONG    m_llLastPTS;                //Last  Picture Time Stamp
	CCritSec    m_csRecentPts;
	LONGLONG    m_llRecentBytePos;
	REFERENCE_TIME m_rtRecentTime;
	DWORD       m_nPTSTrackIndex;

};

#endif