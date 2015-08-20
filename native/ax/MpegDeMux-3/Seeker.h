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

#ifndef __SEEKER_H__
#define __SEEKER_H__


#define	  FRAME_RATE_COUNT  40

#define OPEN_FLAG		0x01
#define CLOSE_FLAG		0x02
#define CONTINUE_FLAG	0x04
#define VALID_FLAG		0x08

#include <stdio.h>



class CMPEG2FrameParser
{
public:
	CMPEG2FrameParser();
	void Reset();
	void WaitIFrame() { m_bParserReady = FALSE; };
	void SetPlayRate( int nRate );
	int  ParseData( int nSession, const unsigned char *pbData, int nBytes, 
								             int *pStartOffset, int *pStopOffset );
	int  GetFrameType( ) { return m_nFrameType; };

	DWORD m_dwTotalFrame;
	DWORD m_dwTotalDropFrame;

	DWORD  m_dwIFrameNum;     
	DWORD  m_dwPFrameNum;		
	DWORD  m_dwBFrameNum;		
	DWORD  m_dwSeqFrameNum;

	DWORD m_dwFrameLength;

	int	  m_nPlayRate;			//x1, x2 x4 x8 x16
	unsigned char m_szBoundary[8];

protected:
	BOOL SeekMPEG2StartCode( const unsigned char* pStart, int Bytes, unsigned	long StartCode,	const unsigned char** ppSeqStart );
	int  SeekFrameType( const unsigned char* pData, int Size, const unsigned char **ppStart );
	int  SeekSequenceHeader( const unsigned char* pData, int Size, const unsigned char **ppStart );
	int  SeekExtensionHeader( const unsigned char* pData, int Size, const unsigned char **ppStart );
	int  SeekGroupHeader( const unsigned char* pData, int Size, const unsigned char **ppStart );

private:
	void UpdateState( int nFrameType ); 
	void CalRateControl( );
	BOOL m_bParserReady;
	int  m_nFrameType;
	int  m_nPrevFrameType;
	BOOL m_bDropFrame;
	int  m_nSession;       //new flash data with a new session number
	int  m_nNextStartOffset;
	int  m_nSessionState;
	unsigned char m_szSeqHdr[128];
	int  m_nSeqHdrBytes;

	int  m_nIFrameDrop;
	int  m_nPFrameDrop;
	int  m_nBFrameDrop;
	int  m_nIFramePass;
	int  m_nPFramePass;
	int  m_nBFramePass;

	int  m_nIFrameDropFlag;
	int  m_nPFrameDropFlag;
	int  m_nBFrameDropFlag;

};

#define NUM_POS_CACHE1  (20*60) //20 minutes in sec
#define NUM_POS_CACHE2  (3*3600/2) //3 hours in 2 sec

typedef struct 
{
	DWORD PTS;
	DWORD POS;
} POS_CACHE;

class PosCache
{
public:
	PosCache();
	void Reset( );
	void CachePos( DWORD pts, DWORD pos );
	int  GetCache1( DWORD pts );
private:
	int m_nIndex1;
	int m_nIndex2;
	int m_nInterval1;
	int m_nInterval2;
	DWORD m_dwLastPTS1;
	DWORD m_dwLastPTS2;
	DWORD m_dwLastPOS1;
	DWORD m_dwLastPOS2;

	POS_CACHE m_tPosCache1[NUM_POS_CACHE1];  //position index table cache
	POS_CACHE m_tPosCache2[NUM_POS_CACHE2];  //position index table cache

}; 



#define REWIND_BLOCKS (150*1024)/4096 
class CDeMuxOutputPin;
class CSeeker : public CMPEG2FrameParser
{
public:
	CSeeker() :  CMPEG2FrameParser(), m_dwSeekSampleCount(0),m_bSync(0),m_dPlayRate(1.0)
	            { m_dwRewindBlocks = REWIND_BLOCKS; m_rtPrevStart = -1; m_nRewindState = 0; };

	void SeekReset( CDeMuxOutputPin *pVideoPin, REFERENCE_TIME rtStart,  double dPlayRate  );
	void FastRewindReset();
	int  FastRewindStep( int nAdjust );
	int  FastRewindState() { return m_nRewindState; }
	void FirstSample() { m_bFirstSample = TRUE; };
	void LastSample()  { if ( m_dwIFrameNum == 0 ) m_nRewindState = -2; else m_nRewindState = -1;  };

	int  FastForward( CDeMuxOutputPin *pVideoPin, REFERENCE_TIME rtStart, REFERENCE_TIME rtStop, const BYTE *pData, 
		              long lBytes, BOOL bSync );
	int  FastRewind( CDeMuxOutputPin *pVideoPin, REFERENCE_TIME rtStart, REFERENCE_TIME rtStop, const BYTE *pData, 
		              long lBytes, BOOL bSync );
	DWORD  m_dwSeekSampleCount;
	BOOL   m_bSync;
	double m_dPlayRate;
	int    m_dwRewindIFrameNum;   

private:
	int		 m_nRewindState;
	DWORD	 m_dwRewindBlocks;
	LONGLONG m_rtPrevStart;
	BOOL     m_bFirstSample;
	LONGLONG m_rtIFrameStart;


};


#endif
