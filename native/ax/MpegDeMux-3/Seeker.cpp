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

#include <streams.h>
#include <pullpin.h>
#include <limits.h>
#include <initguid.h>
#include <ks.h>
#include <dvdmedia.h>
#include "../../../third_party/Microsoft/FileSource/strconv.h"

#include "Seeker.h"
#include "DeMuxInPin.h"
#include "DeMuxOutPin.h"
#include "DeMuxFilter.h"


#define PICTURE_START_CODE       0x00000100
#define USER_DATA_START_CODE     0x000001B2
#define SEQUENCE_HEADER_CODE     0x000001B3
#define SEQUENCE_ERROR_CODE      0x000001B4
#define EXTENSION_START_CODE     0x000001B5
#define SEQUENCE_END_CODE        0x000001B7
#define GROUP_START_CODE         0x000001B8

BOOL CMPEG2FrameParser::SeekMPEG2StartCode( const unsigned char* pStart, int Bytes, unsigned	long StartCode,	const unsigned char** ppSeqStart )
{
	const unsigned char	*pbData;
	int	 len;
	unsigned long code;

	if ( Bytes < 4 )
		return false;

	pbData = pStart;
	code = 0xffffff00;
	len = Bytes;

	code |=  *pbData++;
	while ( --len && code != StartCode )
	{
		code = ( code << 8 )| *pbData++;
	}

	if ( code == StartCode )
	{
		*ppSeqStart	= pbData-4;
		return true;
	}

	*ppSeqStart	= NULL;
	return false;

}

int CMPEG2FrameParser::SeekFrameType( const unsigned char* pData, int Size, const unsigned char **ppStart )
{
	const unsigned char* ptr;
	*ppStart = NULL;
	if ( pData == NULL ) return 0;
	if ( SeekMPEG2StartCode( (const unsigned char*)pData, Size, PICTURE_START_CODE, &ptr ) )
	{
		unsigned char picture_coding_type = ( (*(ptr+5)&0x38 )>>3 );
		if ( picture_coding_type == 1 || picture_coding_type == 2 || picture_coding_type == 3 )
		{
			*ppStart = ptr;
			return picture_coding_type;
		}
	}
	return 0;
} 

int CMPEG2FrameParser::SeekSequenceHeader( const unsigned char* pData, int Size, const unsigned char **ppStart )
{
	const unsigned char* ptr;
	*ppStart = NULL;
	if ( pData == NULL ) return 0;
	if ( SeekMPEG2StartCode( (const unsigned char*)pData, Size, SEQUENCE_HEADER_CODE, &ptr ) )
	{
		*ppStart = ptr;
		return 1;
	}
	return 0;
} 

int CMPEG2FrameParser::SeekExtensionHeader( const unsigned char* pData, int Size, const unsigned char **ppStart )
{
	const unsigned char* ptr;
	*ppStart = NULL;
	if ( pData == NULL ) return 0;
	if ( SeekMPEG2StartCode( (const unsigned char*)pData, Size, EXTENSION_START_CODE, &ptr ) )
	{
		*ppStart = ptr;
		return 1;
	}
	return 0;
} 

int CMPEG2FrameParser::SeekGroupHeader( const unsigned char* pData, int Size, const unsigned char **ppStart )
{
	const unsigned char* ptr;
	*ppStart = NULL;
	if ( pData == NULL ) return 0;
	if ( SeekMPEG2StartCode( (const unsigned char*)pData, Size, GROUP_START_CODE, &ptr ) )
	{
		*ppStart = ptr;
		return 1;
	}
	return 0;
} 

//////////////////////////////////////////////////////////////////////////////////////////////

CMPEG2FrameParser::CMPEG2FrameParser( ):
	m_dwTotalFrame(0),
	m_dwTotalDropFrame(0),
	m_dwFrameLength(0),
	m_dwIFrameNum(0),
	m_dwPFrameNum(0),
	m_dwBFrameNum(0),
	m_nPlayRate(1),
	m_bParserReady( FALSE )
{
	memset( m_szBoundary, 0xff, sizeof(m_szBoundary) );
	m_nSession = -1;
	m_nNextStartOffset = 0;
	m_nIFramePass = 0;
	m_nPFramePass = 0;
	m_nBFramePass = 0;
	m_nIFrameDrop = 0;
	m_nPFrameDrop = 0;
	m_nBFrameDrop = 0;
	m_dwSeqFrameNum = 0;
	m_bDropFrame = TRUE;
	m_nPlayRate = 1;  //ZQ debug

}

void CMPEG2FrameParser::Reset()
{
	m_dwTotalFrame = 0;
	m_dwTotalDropFrame = 0;
	m_dwFrameLength = 0;
	m_nSession = -1;
	m_nSessionState = CLOSE_FLAG;
	m_nNextStartOffset = 0;
	m_nIFramePass = 0;
	m_nPFramePass = 0;
	m_nBFramePass = 0;
	m_nIFrameDrop = 0;
	m_nPFrameDrop = 0;
	m_nBFrameDrop = 0;
	m_dwIFrameNum = 0;
	m_dwPFrameNum = 0;
	m_dwBFrameNum = 0;
	m_dwSeqFrameNum = 0;
	m_bParserReady = FALSE;
	m_bDropFrame = TRUE;
	m_nIFrameDropFlag  = 0;
	m_nPFrameDropFlag  = 0;
	m_nBFrameDropFlag  = 0;
	memset( m_szSeqHdr, 0x0, sizeof(m_szSeqHdr) );
}
void CMPEG2FrameParser::SetPlayRate( int nRate )
{
	if ( nRate <= 1 )
		m_nPlayRate = 1;
	else
	if ( nRate > 16 ) 
		m_nPlayRate = 16;
	else
		m_nPlayRate = nRate;
	CalRateControl( );
}

void CMPEG2FrameParser::CalRateControl( )
{
	int IFr = 0, PFr = 0, BFr = 0, Rate = 0;
	int TotalFrame;
	if ( m_nPlayRate == 1 ) return;

	TotalFrame = (m_nIFrameDrop+m_nIFramePass) + (m_nPFrameDrop+m_nPFramePass) + (m_nBFrameDrop+m_nBFramePass);

	if ( TotalFrame == 0 ) return;

	IFr = 100 * (m_nIFrameDrop+m_nIFramePass)/TotalFrame;
	PFr = 100 * (m_nPFrameDrop+m_nPFramePass)/TotalFrame;
	BFr = 100 * (m_nBFrameDrop+m_nBFramePass)/TotalFrame;

	if ( m_nIFramePass+m_nPFramePass+m_nBFramePass > 0 )
		Rate = 100 *( m_nIFramePass+m_nPFramePass+m_nBFramePass )/TotalFrame;
	else 
		Rate = 10000;

	m_nIFrameDropFlag  = 0;
	m_nPFrameDropFlag  = 0;
	m_nBFrameDropFlag  = 0;

	{
		if ( IFr > 100/m_nPlayRate )
		{
			m_nIFrameDropFlag = 1;
			m_nPFrameDropFlag = 1;
			m_nBFrameDropFlag = 1;
		} else
		if ( IFr + PFr > 100/m_nPlayRate )
		{
			m_nPFrameDropFlag = 1;
			m_nBFrameDropFlag = 1;
		} else
			m_nBFrameDropFlag = 1;
	}

	//DbgLog((LOG_TRACE, 3, TEXT("====>Drop PlayRate:%d I:%d P:%d B:%d; Real Rate:%d %d %d %d "), 100/m_nPlayRate, IFr, PFr, BFr,
	//	Rate, m_nIFrameDropFlag, m_nBFrameDropFlag, m_nPFrameDropFlag ));

	m_nIFrameDrop  = 0;
	m_nPFrameDrop  = 0;
	m_nBFrameDrop  = 0;
	m_nIFramePass  = 0;
	m_nPFramePass  = 0;
	m_nBFramePass  = 0;
}

void CMPEG2FrameParser::UpdateState( int nFrameType )
{
		int Rate;
		m_dwTotalFrame++;
		if ( nFrameType == 1 ) m_dwIFrameNum++; else
		if ( nFrameType == 2 ) m_dwPFrameNum++; else
		if ( nFrameType == 3 ) m_dwBFrameNum++; 
		if ( m_dwTotalFrame % FRAME_RATE_COUNT == 0 )
		{
			//DbgLog((LOG_TRACE, 3, TEXT("====>Total:%d I:%d P:%d B:%d "), FRAME_RATE_COUNT,  m_nIFrameNum, m_dwPFrameNum, m_dwBFrameNum  ));
			CalRateControl( );
		}

		m_nFrameType = nFrameType;
		m_bDropFrame = FALSE;

		Rate = 100 * (m_dwTotalFrame-m_dwTotalDropFrame)/m_dwTotalFrame;
		if ( nFrameType == 3 )
		{
			if ( m_nBFrameDropFlag )
			{
				if ( Rate > 100/m_nPlayRate ) 
				{
					m_nBFrameDrop++;
					m_bDropFrame = TRUE;
				} else
					m_nBFramePass++;
			} else
				m_nBFramePass++;

		} else
		if ( nFrameType == 2 )
		{
			if ( m_nPFrameDropFlag && m_nBFramePass == 0 )
			{
				if ( Rate > 100/m_nPlayRate ) 
				{
					m_nPFrameDrop++;
					m_bDropFrame = TRUE;
				} else
					m_nPFramePass++;
			} else
				m_nPFramePass++;
		} else
		if ( nFrameType == 1 )
		{
			if ( m_nIFrameDropFlag && m_nPFramePass == 0  )
			{
				if ( Rate > 100/m_nPlayRate ) 
				{
					m_nIFrameDrop++;
					m_bDropFrame = FALSE;
				} else
					m_nIFramePass++;		
			} else
				m_nIFramePass++;
		}

		if ( !m_bParserReady ) m_bDropFrame = TRUE;
		if ( m_bDropFrame ) m_dwTotalDropFrame++;
}

int  CMPEG2FrameParser::ParseData( int nSession, const unsigned char *pbData, int nBytes, 
								                        int *pStartOffset, int *pStopOffset )
{
	int ret, type;
	const unsigned char* pStart;

	ret = (m_bDropFrame ? 0 : VALID_FLAG);

	*pStartOffset = m_nNextStartOffset, 
	*pStopOffset = nBytes;

	if ( nSession != m_nSession  )
	{
		//processing boundary
		m_szBoundary[3]=pbData[0];
		m_szBoundary[4]=pbData[1];
		m_szBoundary[5]=pbData[2];
		if ( (type = SeekFrameType(  m_szBoundary, 6,  &pStart ) ) )
		{
			if ( m_nSessionState == OPEN_FLAG ) //OPEN_FLAG, last frame hasn't end yet
			{
				*pStartOffset = int(pStart - m_szBoundary)-3;
				*pStopOffset = *pStartOffset;
				ret |=  CLOSE_FLAG | CONTINUE_FLAG;
				m_nNextStartOffset = *pStartOffset;
				m_nSessionState = CLOSE_FLAG ;
				return ret;
			} else
			{
				UpdateState( type );
				ret = m_bDropFrame ? 0 : VALID_FLAG;
				*pStartOffset = int(pStart - m_szBoundary)-3;
				*pStopOffset = *pStartOffset;
				ret |= OPEN_FLAG;
				m_nNextStartOffset = 0;
				m_nSessionState = OPEN_FLAG;
			} 
		} else
		{
			m_nNextStartOffset = 0;
			*pStartOffset = 0;
		}

		m_nSession = nSession;
		if ( nBytes >= 3 )
		{
			m_szBoundary[0]=pbData[nBytes-3];
			m_szBoundary[1]=pbData[nBytes-2];
			m_szBoundary[2]=pbData[nBytes-1];
		}
	}

	while ( m_nNextStartOffset < nBytes ) //repeat once
	{
		//if ( !m_bParserReady ) 
		{   //extract sequence header
			const unsigned char *pSeqHdr, *pExtHdr, *pGrpHdr, *pNext;
			int Bytes;
			if ( SeekSequenceHeader( pbData+m_nNextStartOffset, nBytes-m_nNextStartOffset, &pSeqHdr ) )
			{
				pNext = pSeqHdr+4; Bytes = nBytes-int(pNext-pbData);
				if ( SeekExtensionHeader( pNext, Bytes, &pExtHdr ) )
				{
					pNext = pExtHdr+4; Bytes = nBytes-int(pNext-pbData);
				} 
				if ( SeekGroupHeader( pNext, Bytes, &pGrpHdr ) )
				{
					pNext = pGrpHdr+4; Bytes = nBytes-int(pNext-pbData);

					if ( SeekFrameType( pNext, Bytes,  &pStart ) == 1 )
					{
						m_nSeqHdrBytes = int(pStart - pSeqHdr);

						if ( m_nSessionState == OPEN_FLAG )
						{
							*pStopOffset = int(pSeqHdr - pbData);
							m_nNextStartOffset = *pStopOffset;
							ret |= CLOSE_FLAG ;
							ret |= CONTINUE_FLAG;
							m_nSessionState = CLOSE_FLAG ;
							return ret; 
						} else
						{
							m_dwSeqFrameNum++;

							m_nSeqHdrBytes = int(pGrpHdr - pSeqHdr);

							if ( !m_bParserReady || memcmp( m_szSeqHdr, pSeqHdr, m_nSeqHdrBytes ) )
							{
								if ( m_nSeqHdrBytes < sizeof( m_szSeqHdr ) )
									memcpy( m_szSeqHdr, pSeqHdr, m_nSeqHdrBytes );
								*pStartOffset = int(pSeqHdr - pbData);
								*pStopOffset  = int(pGrpHdr- pbData);
								m_nFrameType = 4;
								m_bParserReady = TRUE;
								ret = VALID_FLAG;
								ret |= OPEN_FLAG | CLOSE_FLAG ;
								ret |= CONTINUE_FLAG;
								m_nNextStartOffset = int(pStart-pbData);
								return ret;

							}
						}
					} //If I-Frame
				}
			}
		}

		if ( ( type = SeekFrameType(  pbData+m_nNextStartOffset, nBytes-m_nNextStartOffset,  &pStart ) ) )
		{
			if ( m_nSessionState == OPEN_FLAG )
			{
				*pStopOffset = int(pStart - pbData);
				m_nNextStartOffset = *pStopOffset;
				ret |= CLOSE_FLAG ;
				ret |= CONTINUE_FLAG;
				m_nSessionState = CLOSE_FLAG ;
				return ret; 
			} else
			{
				UpdateState( type );
				ret = m_bDropFrame ? 0 : VALID_FLAG;
				*pStartOffset = int(pStart - pbData);
				m_nNextStartOffset = int(pStart - pbData)+4;
				ret |= OPEN_FLAG ;
				m_nSessionState = OPEN_FLAG ;

			}
		} else
		{
			m_nNextStartOffset = nBytes;
			*pStopOffset = nBytes;
			return ret;
		}
	}

	return 0;
}

void CSeeker::SeekReset( CDeMuxOutputPin* pVideoPin, REFERENCE_TIME rtStart, double dPlayRate  )
{
	m_dwSeekSampleCount = 0;
	pVideoPin->DropSample( );
	Reset();
	DbgLog((LOG_TRACE, 3, TEXT(" ---------> Get Seeker reset at %s."), (LPCTSTR)Disp( (CRefTime)rtStart ) ));
	m_bSync = 1;
	m_dPlayRate = dPlayRate;
	SetPlayRate( (int)m_dPlayRate );
}

void CSeeker::FastRewindReset()
{
	m_nRewindState = -1;
	m_rtPrevStart = -1;
	m_bFirstSample = FALSE;
	m_dwRewindIFrameNum = 0;
}

int CSeeker::FastRewindStep( int nAdjust )
{
	m_nRewindState = 0;
	m_dwRewindBlocks += nAdjust;
	if ( nAdjust ) DbgLog((LOG_TRACE, 3, TEXT("Inc Rewind block %d"), m_dwRewindBlocks  ));
	m_dwRewindIFrameNum = 0;
	 m_rtIFrameStart = 0;
	Reset();
	return m_dwRewindBlocks;
}

int CSeeker::FastForward( CDeMuxOutputPin* pVideoPin, REFERENCE_TIME rtStart, REFERENCE_TIME rtStop, const BYTE *pData, long lBytes, BOOL bSync )
{
	HRESULT hr;
	int flag;
	int nStartOffset, nStopOffset;

	if ( pVideoPin == NULL || !pVideoPin->IsConnected() ) return 0;

	do {
		flag = ParseData( m_dwSeekSampleCount, pData, lBytes, &nStartOffset, &nStopOffset );
		if ( (flag & VALID_FLAG) )
		{
			if ( (flag & (CLOSE_FLAG|OPEN_FLAG))==(CLOSE_FLAG|OPEN_FLAG) )
			{
				if ( GetFrameType( ) == 4 )
				{
					DbgLog((LOG_TRACE, 3, TEXT(" ---------> Get Sequency header, start output at  %d."), 
						    m_dwSeekSampleCount ));
					pVideoPin->NewSegment( rtStart, rtStop, m_dPlayRate );
				}

				hr = pVideoPin->OpenSample( rtStart, m_bSync  );
				if ( nStartOffset < 0 )
				{
					if ( FAILED( hr = pVideoPin->LoadSampleData( m_szBoundary+3+nStartOffset,-nStartOffset) ) )
						if ( FAILED( hr = pVideoPin->SendPartialSample( m_szBoundary+3+nStartOffset,-nStartOffset )))
							DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );

					nStartOffset = 0;
				} else
				if ( FAILED( hr = pVideoPin->LoadSampleData( pData+nStartOffset, nStopOffset-nStartOffset ) ) )
					if ( FAILED( hr =  pVideoPin->SendPartialSample(  pData+nStartOffset, nStopOffset-nStartOffset ) ) ) 
						DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );

				hr = pVideoPin->SendSample();
				

			} else
			if ( flag & OPEN_FLAG )
			{
				hr = pVideoPin->OpenSample( rtStart, m_bSync );
				if ( nStartOffset < 0 )
				{
					if ( FAILED( hr = pVideoPin->LoadSampleData( m_szBoundary+3+nStartOffset,-nStartOffset ) ) )
						if ( FAILED( hr = pVideoPin->SendPartialSample( m_szBoundary+3+nStartOffset,-nStartOffset )))
							DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );

					nStartOffset = 0;
				}
				if ( FAILED( hr = pVideoPin->LoadSampleData( pData+nStartOffset, nStopOffset-nStartOffset ) ) )
					if ( FAILED( hr =  pVideoPin->SendPartialSample(pData+nStartOffset, nStopOffset-nStartOffset  ))) 
						DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );
	
				//if ( GetFrameType( ) == 1 )
				//	DbgLog((LOG_TRACE, 3, TEXT(" ---------> Get I, start output at  %d."),  m_dwSeekSampleCount ));

			} else
			if ( flag & CLOSE_FLAG )
			{
				if ( nStartOffset < 0 )
				{
					pVideoPin->DropSampleData( nStartOffset );
				} else
				if ( FAILED( hr = pVideoPin->LoadSampleData( pData+nStartOffset, nStopOffset-nStartOffset ) ) )
					if ( FAILED( hr =  pVideoPin->SendPartialSample( pData+nStartOffset, nStopOffset-nStartOffset ))) 
						DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );	

				hr = pVideoPin->SendSample();

			} else
			{
				if ( FAILED( hr = pVideoPin->LoadSampleData( pData+nStartOffset, nStopOffset-nStartOffset ) ) )
					if ( FAILED( hr =  pVideoPin->SendPartialSample( pData+nStartOffset, nStopOffset-nStartOffset ) ) ) 
						DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );	

			}
			if ( m_bSync ) m_bSync = 0;
		}
	} while ( flag &  CONTINUE_FLAG );

	m_dwSeekSampleCount++;

	return 1;
}

int CSeeker::FastRewind( CDeMuxOutputPin* pVideoPin, REFERENCE_TIME rtStart, REFERENCE_TIME rtStop, const BYTE *pData, long lBytes, BOOL bSync )
{
	HRESULT hr;
	int flag;
	int nStartOffset, nStopOffset;

	if ( pVideoPin == NULL || !pVideoPin->IsConnected() ) return 0;

	if ( m_rtPrevStart == -1  ) 
		m_rtPrevStart = rtStop;

	do {

		flag = ParseData( m_dwSeekSampleCount, pData, lBytes, &nStartOffset, &nStopOffset );
		if ( GetFrameType( ) == 1 && ( flag & OPEN_FLAG ) )
		{
			DbgLog((LOG_TRACE, 5, TEXT(" ---> I open time: %s %s"), (LPCTSTR)Disp(m_rtPrevStart), (LPCTSTR)Disp(rtStart) ));
			if ( rtStart >= m_rtPrevStart  )
			{
				if ( m_rtIFrameStart )
					m_rtPrevStart = m_rtIFrameStart;
				if ( m_bFirstSample )
				{
					m_nRewindState = -3;
					DbgLog((LOG_TRACE, 5, TEXT(" break foward -3" ))); 
					return -3;
				}
				if ( m_dwRewindIFrameNum <= 0 )
				{
					m_nRewindState = -2;
					DbgLog((LOG_TRACE, 5, TEXT(" break foward -2, %d"), m_dwRewindIFrameNum )); 
					return -2;
				}
				else
				{
					m_nRewindState = -1;
					DbgLog((LOG_TRACE, 5, TEXT(" break foward -1 %d"),  m_dwRewindIFrameNum )); 
					return -1;
				}
			}
			
			if ( m_rtIFrameStart == 0 )
				m_rtIFrameStart = rtStart;

		}

		if ( (flag & VALID_FLAG) )
		{
			if ( (flag & (CLOSE_FLAG|OPEN_FLAG))==(CLOSE_FLAG|OPEN_FLAG) )
			{
				if ( GetFrameType( ) == 4 )
				{
					DbgLog((LOG_TRACE, 3, TEXT(" ---------> Get Sequency header, start output at  %d."), 
						    m_dwSeekSampleCount ));
					pVideoPin->NewSegment( rtStart, rtStop, m_dPlayRate );
				}

				hr = pVideoPin->OpenSample( rtStart, m_bSync );
				if ( nStartOffset < 0 )
				{
					if ( FAILED( hr = pVideoPin->LoadSampleData( m_szBoundary+3+nStartOffset,-nStartOffset) ) )
						if ( FAILED( hr = pVideoPin->SendPartialSample( m_szBoundary+3+nStartOffset,-nStartOffset )))
							DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );

					nStartOffset = 0;
				} else
				if ( FAILED( hr = pVideoPin->LoadSampleData( pData+nStartOffset, nStopOffset-nStartOffset ) ) )
					if ( FAILED( hr =  pVideoPin->SendPartialSample(  pData+nStartOffset, nStopOffset-nStartOffset ) ) ) 
						DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );

				hr = pVideoPin->SendSample();
				if ( GetFrameType( ) == 1 )
				{
					DbgLog((LOG_TRACE, 3, TEXT(" ---------> Start Sent I Frame, start output at...  %d."),  m_dwSeekSampleCount ));
					m_nRewindState = 1;
				}

			} else
			if ( flag & OPEN_FLAG )
			{
				if ( GetFrameType( ) == 1 )
					DbgLog((LOG_TRACE, 3, TEXT(" ---------> Start I Frame, start output at  %d."),  m_dwSeekSampleCount ));

				hr = pVideoPin->OpenSample( rtStart, m_bSync );
				if ( nStartOffset < 0 )
				{
					if ( FAILED( hr = pVideoPin->LoadSampleData( m_szBoundary+3+nStartOffset,-nStartOffset ) ) )
						if ( FAILED( hr = pVideoPin->SendPartialSample( m_szBoundary+3+nStartOffset,-nStartOffset )))
							DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );

					nStartOffset = 0;
				}
				if ( FAILED( hr = pVideoPin->LoadSampleData( pData+nStartOffset, nStopOffset-nStartOffset ) ) )
					if ( FAILED( hr =  pVideoPin->SendPartialSample(pData+nStartOffset, nStopOffset-nStartOffset  ))) 
						DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );


			} else
			if ( flag & CLOSE_FLAG )
			{
				if ( nStartOffset < 0 )
				{
					pVideoPin->DropSampleData( nStartOffset );
				} else
				if ( FAILED( hr = pVideoPin->LoadSampleData( pData+nStartOffset, nStopOffset-nStartOffset ) ) )
					if ( FAILED( hr =  pVideoPin->SendPartialSample( pData+nStartOffset, nStopOffset-nStartOffset ))) 
						DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );	

				hr = pVideoPin->SendSample();
				if ( GetFrameType( ) == 1 )
				{
					DbgLog((LOG_TRACE, 3, TEXT(" ---------> Sent I Frame, start output at.  %d."),  m_dwSeekSampleCount ));
					m_nRewindState = 1;
				}


			} else
			{
				if ( FAILED( hr = pVideoPin->LoadSampleData( pData+nStartOffset, nStopOffset-nStartOffset ) ) )
					if ( FAILED( hr =  pVideoPin->SendPartialSample( pData+nStartOffset, nStopOffset-nStartOffset ) ) ) 
						DbgLog((LOG_TRACE, 5, TEXT(" Failed sending partial sample 0x%x."), hr) );	

			}
			if ( m_bSync ) m_bSync = 0;
		}

		if ( GetFrameType( ) == 1 && ( flag & CLOSE_FLAG ))
		{
			m_dwRewindIFrameNum++;

			if ( rtStart >= m_rtPrevStart  )
			{
				m_rtPrevStart = m_rtIFrameStart;
				DbgLog((LOG_TRACE, 5, TEXT(" ---> I close Time: %s %s"), (LPCTSTR)Disp(m_rtPrevStart), (LPCTSTR)Disp(m_rtIFrameStart) ));

				if ( m_bFirstSample )
				{
					m_nRewindState = -3;
					DbgLog((LOG_TRACE, 5, TEXT(" .break foward -3" ))); 
					return -3;
				}
				if ( m_dwRewindIFrameNum <= 1 )
				{
					m_nRewindState = -2;
					DbgLog((LOG_TRACE, 5, TEXT(" .break foward -2, %d"), m_dwRewindIFrameNum )); 
					return -2;
				}
				else
				{
					m_nRewindState = -1;
					DbgLog((LOG_TRACE, 5, TEXT(" .break foward -1 %d"),  m_dwRewindIFrameNum )); 
					return -1;
				}
			}
		}
	} while ( flag &  CONTINUE_FLAG );

	m_dwSeekSampleCount++;

	return 1;
}

////////////////////////////////////////////////////////////////////
PosCache::PosCache():
	m_nIndex1(0),
	m_nIndex2(0),
	m_nInterval1(1),
	m_nInterval2(2),
	m_dwLastPTS1(0),
	m_dwLastPOS1(0),
	m_dwLastPTS2(0),
	m_dwLastPOS2(0)
{
	memset( m_tPosCache1, 0, sizeof(m_tPosCache1) ); 
	memset( m_tPosCache2, 0, sizeof(m_tPosCache2) ); 
}

void PosCache::Reset( ) 
{ 
	memset( m_tPosCache1, 0, sizeof(m_tPosCache1) ); 
	memset( m_tPosCache2, 0, sizeof(m_tPosCache2) ); 
	m_nIndex1 = 0;
	m_nIndex2 = 0;
	m_nInterval1 = 1;
	m_nInterval1 = 2;
	m_dwLastPTS1 = 0;
	m_dwLastPOS1 = 0;
	m_dwLastPTS2 = 0;
	m_dwLastPOS2 = 0;
};

void PosCache::CachePos( DWORD pts, DWORD pos )
{
	if (m_nIndex1 <NUM_POS_CACHE1 )
	{
		if ( pts > m_dwLastPTS1 + m_nInterval1 ) 
		{
			m_tPosCache1[m_nIndex1].PTS = pts;
			m_tPosCache1[m_nIndex1].POS = pos;
			m_dwLastPTS1 = pts;
			m_dwLastPOS1 = pos;
			m_nIndex1++;
		} else
		if ( pts < m_dwLastPTS1 - m_nInterval1 )
		{
			int i;
			int index = GetCache1( pts );
			if ( index >= 0 && m_tPosCache1[index+1].PTS-m_tPosCache1[index].PTS >= (DWORD)m_nInterval1*2 )
			{
				if ( m_nIndex1 <NUM_POS_CACHE1+1 )
					m_nIndex1++;

				for ( i = m_nIndex1; i>= index+2; i-- )
					m_tPosCache1[i]=m_tPosCache1[i-1];

				m_tPosCache1[index+1].PTS = pts;
				m_tPosCache1[index+1].POS = pos;
			}
		}
	}
}

int PosCache::GetCache1( DWORD pts )
{
	int i;
	for ( i = 0; i<m_nIndex1++; i++ )
	{
		if ( pts >= m_tPosCache1[i].PTS )
			return i;
	}
	return -1;
}