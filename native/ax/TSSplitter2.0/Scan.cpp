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
#include <time.h>
#include <memory.h>
#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <io.h>
#include <fcntl.h>
#include <share.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <string.h>

#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"
#include "Remuxer.h"
#include "ChannelScan.h"
//#include "TSSplitFilter.h"
#include "Scan.h"

#ifdef REMOVE_LOG	
#define SageLog(x)
#else
#define SageLog(x)   _sagelog x
#endif
void _sagelog( int type, int level, const char* cstr, ... );


CScan::CScan(void)
{
	m_pScan = NULL;
	m_nExpectedScanBytes = 0;
	m_nAlignScanBytes = 0;
	m_bScanFileCache = 0;
	m_szScanCacheDataFileName[0] = 0x0;
	m_hScanCacheDataFile = 0;
	m_dwScanCacheDataSize = 0;
	m_dwScanCacheDataMaxSize = MAX_SCAN_CACHE_DATA;
	m_nStreamFormat = 0;
	m_nStreamSubFormat = 0;
	m_dwScanTunerData = 0;
	m_dwScanFileData = 0;
	m_nScanState = 0;
}

CScan::~CScan(void)
{
	if ( m_pScan != NULL )
	{
		if ( m_hScanCacheDataFile > 0 )
			FCLOSE( m_hScanCacheDataFile );
		SageLog(( _LOG_TRACE, 3, TEXT("*********** ChanneScan tuner close: data:%d File data:%d"), 
					m_dwScanTunerData, m_dwScanFileData  ));
		ReleaseChannelScan( m_pScan );
	}
}

void CScan::EnableFileCache( char* pszCacheFileName )
{
	strncpy( m_szScanCacheDataFileName, pszCacheFileName, sizeof(m_szScanCacheDataFileName) );
	m_bScanFileCache = 1;
}

void CScan::StartChannelScan( struct TUNE* pTune )
{
	if ( pTune == NULL )
		return;
	if ( m_pScan == NULL )
	{
		m_pScan = CreateChannelScan( NULL, pTune );
		if ( m_bScanFileCache )
		{
			m_hScanCacheDataFile = _sopen( m_szScanCacheDataFileName, 
						 _O_RDWR|_O_BINARY|_O_CREAT|_O_TRUNC, _SH_DENYNO , _S_IREAD|_S_IWRITE );
			m_bScanFileCache = m_hScanCacheDataFile > 0;
		}
		DoChannelScan( m_pScan, PSI_SCAN );
		m_Tune = *pTune;
		m_nScanState = PSI_SCAN;
		return;
	}
	m_Tune = *pTune;
	ResetChannelScan( m_pScan );
	ResetChannelScanList( m_pScan );
	DoChannelScan( m_pScan, PSI_SCAN );
	m_nStreamFormat = pTune->stream_format;
	m_nStreamSubFormat = pTune->sub_format;
	ChannelScanTune( m_pScan, (TUNE*)pTune ); 
	if ( m_bScanFileCache )
	{
		m_hScanCacheDataFile = _sopen( m_szScanCacheDataFileName, 
			         _O_RDWR|_O_BINARY|_O_CREAT|_O_TRUNC, _SH_DENYNO , _S_IREAD|_S_IWRITE );
		m_bScanFileCache = m_hScanCacheDataFile > 0;
	}
	m_nScanState = PSI_SCAN;
}

void CScan::CloseChannelScan( )
{
	if ( m_hScanCacheDataFile > 0 )
	{
		FCLOSE( m_hScanCacheDataFile );
		m_hScanCacheDataFile = 0;
	}
	SageLog(( _LOG_TRACE, 3, TEXT("*********** ChanneScan tuner close: data:%d File data:%d"), 
				m_dwScanTunerData, m_dwScanFileData  ));
	ReleaseChannelScan( m_pScan );
	m_pScan = NULL;
}

void CScan::CacheChannelScanData( unsigned char* pData, unsigned long lDataLen  )
{
	if ( m_hScanCacheDataFile > 0 )
	{
		if ( m_dwScanCacheDataSize < m_dwScanCacheDataMaxSize )
		{
			int ct;
			if ( ( ct = write( m_hScanCacheDataFile, pData, lDataLen ) ) >  0 )
				m_dwScanCacheDataSize += ct;
		} else
		{
			FSEEK( m_hScanCacheDataFile, 0, SEEK_SET );
		
		}
	}
	
}

void CScan::PushChannelScanTunerData( unsigned char* pData, unsigned long lDataLen  )
{
	int nUsedBytes;
	unsigned char *pStart;
	unsigned long dwLength;
	pStart   = pData;
	dwLength = lDataLen;
	if ( m_nExpectedScanBytes )
	{
		int nExpectedBytes2;
		memcpy( m_cAlignScanBuffer+m_nAlignScanBytes, pStart, m_nExpectedScanBytes );
		nUsedBytes = PushScanStreamData( m_pScan, m_cAlignScanBuffer, m_nAlignScanBytes+m_nExpectedScanBytes, &nExpectedBytes2 );
		if ( nExpectedBytes2 == 0 )
		{
			pStart   +=  m_nExpectedScanBytes;
			dwLength -=  m_nExpectedScanBytes;
		}
	}

	nUsedBytes = PushScanStreamData( m_pScan, pStart, dwLength, &m_nExpectedScanBytes );
	m_nAlignScanBytes = dwLength - nUsedBytes;
	ASSERT( m_nExpectedScanBytes+m_nAlignScanBytes <= sizeof(m_cAlignScanBuffer) );
	if ( m_nAlignScanBytes > 0 && m_nAlignScanBytes + m_nExpectedScanBytes <= sizeof(m_cAlignScanBuffer))
	{
		memcpy( m_cAlignScanBuffer, pStart+nUsedBytes, m_nAlignScanBytes );

	} else
	{
		//drop data, ask too many
		m_nExpectedScanBytes = 0;
	}

	m_dwScanTunerData +=lDataLen;
}

void CScan::PushChannelScanFileData(   )
{
	int nUsedBytes;
	unsigned long  lDataLen = 1024*8;
	unsigned char* pData = (unsigned char* )new char[lDataLen];
	unsigned char *pStart;
	unsigned long dwLength;
	while ( !eof( m_hScanCacheDataFile ) )
	{
		dwLength = (int)read( m_hScanCacheDataFile, pData, lDataLen );
		pStart   = pData;
		m_dwScanFileData += dwLength;
		if ( m_nExpectedScanBytes )
		{
			int nExpectedBytes2;
			memcpy( m_cAlignScanBuffer+m_nAlignScanBytes, pStart, m_nExpectedScanBytes );
			nUsedBytes = PushScanStreamData( m_pScan, m_cAlignScanBuffer, m_nAlignScanBytes+m_nExpectedScanBytes, &nExpectedBytes2 );
			if ( nExpectedBytes2 == 0 )
			{
				pStart   +=  m_nExpectedScanBytes;
				dwLength -=  m_nExpectedScanBytes;
			}
		}

		nUsedBytes = PushScanStreamData( m_pScan, pStart, dwLength, &m_nExpectedScanBytes );
		m_nAlignScanBytes = dwLength - nUsedBytes;
		ASSERT( m_nExpectedScanBytes+m_nAlignScanBytes <= sizeof(m_cAlignScanBuffer) );
		if ( m_nAlignScanBytes > 0 && m_nAlignScanBytes + m_nExpectedScanBytes <= sizeof(m_cAlignScanBuffer))
		{
			memcpy( m_cAlignScanBuffer, pStart+nUsedBytes, m_nAlignScanBytes );

		} else
		{
			//drop data, ask too many
			m_nExpectedScanBytes = 0;
		}

		if ( IsChannelInfoReady( m_pScan ) )
			break;

		if ( eof( m_hScanCacheDataFile ) && m_pScan->task == NAKED_SCAN )
		{
			FSEEK( m_hScanCacheDataFile, 0, SEEK_SET );
		}

	}
	delete [] pData;
}

int CScan::IsCacheDataFull( )
{
	return m_dwScanCacheDataSize > m_dwScanCacheDataMaxSize;
}

int CScan::IsQAMStream( TUNE *pTune )
{
	return ( pTune->stream_format == ATSC_STREAM && pTune->sub_format == CABLE );
}

int CScan::IsDVBSStream( TUNE *pTune )
{
	return ( pTune->stream_format == DVB_STREAM && pTune->sub_format == SATELLITE );
}

int CScan::IsDVBCStream( TUNE *pTune )
{
	return ( pTune->stream_format == DVB_STREAM && pTune->sub_format == CABLE );
}


void CScan::ProcessScan( unsigned char* pData, long lDataLen )
{
	int  nScanState;
	if ( m_nScanState == DONE_SCAN ) 
		return;
		
	if ( m_bScanFileCache )
		CacheChannelScanData( pData, lDataLen );

	if ( !m_bScanFileCache || m_bScanFileCache && !IsCacheDataFull( ) )
		PushChannelScanTunerData( pData, lDataLen );
	else
		PushChannelScanFileData( );

	nScanState = IsChannelInfoReady( m_pScan );
	if ( nScanState == 2 ) //psi channels is ready
	{
		unsigned short iStreamFormat, iSubFormat;
		if ( GetStreamFormat( m_pScan, &iStreamFormat, &iSubFormat ) )
		{
			m_nStreamFormat = (unsigned char)iStreamFormat;
			m_nStreamSubFormat = (unsigned char)iSubFormat;
		}
		if ( IsQAMStream( &m_pScan->tune ) )
		{
			m_Tune.stream_format = (unsigned char)m_nStreamFormat;
			m_Tune.sub_format    = (unsigned char)m_nStreamSubFormat;
			ResetChannelScan( m_pScan );
			DoChannelScan( m_pScan, NAKED_SCAN );
			ChannelScanTune( m_pScan, &m_Tune ); 
			m_nScanState = NAKED_SCAN;
		} else
			m_nScanState = DONE_SCAN;
	} else
	if ( nScanState == 3 ) //naked channels is ready
	{
		MergeChannelListProgramList( m_pScan );
		m_nScanState = DONE_SCAN;
		SageLog(( _LOG_TRACE, 3, TEXT("*********** Scan done ************") )); 
	} else
	if ( nScanState > 0 ) //maxium parsing data
	{
		if ( nScanState == 4 )
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop PSI parsing (maxium bytes searching).") ));
		else
		if ( nScanState == 5 )
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop PSI parsing, no nit, no channel PSI (%d ms)"), UpdateTimeClock( m_pScan, 0 ) ));
		else
		if ( nScanState == 10 )
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop PSI parsing, timeout (%d ms)."), UpdateTimeClock( m_pScan, 0 ) ));
		else
		if ( nScanState == 6 )
		    SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop naked parsing (maxium bytes searching), no pmt.).") ));
		else
		if ( nScanState == 7 )
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop naked parsing (maxium bytes searching), no clear channel.).") ));
		else
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop PSI parsing, unkonw %d (%d ms)."), nScanState, UpdateTimeClock( m_pScan, 0 ) ));

		if ( m_nScanState == PSI_SCAN && 
			( IsQAMStream( &m_pScan->tune ) || 
			  IsDVBSStream( &m_pScan->tune ) && IsNakedStream( m_pScan ) ) ) //naked DVB-S stream in USA
		{
			SageLog(( _LOG_TRACE, 3, TEXT("*********** Start scaning naked channels ************") )); 
			ResetChannelScan( m_pScan );
			DoChannelScan( m_pScan, NAKED_SCAN );
			ChannelScanTune( m_pScan, &m_Tune ); 
			m_nScanState = NAKED_SCAN; 

		} else
		{
			SageLog(( _LOG_TRACE, 3, TEXT("*********** Scan done (state:%d %s naked:%d )************"),
				m_nScanState, StreamFormatString(m_pScan->tune.stream_format, m_pScan->tune.sub_format), 
				IsNakedStream( m_pScan ) )); 
			m_nScanState = DONE_SCAN;
		}
	} 

}

CHANNEL_LIST* CScan::GetChannelList( )
{
	MergeChannelListProgramList( m_pScan );
	return ::GetChannelList( m_pScan );
}

TUNE_LIST* CScan::GetTuneList( )
{
	return ::GetTuneList( m_pScan );
}

int CScan::ScanChannelState( )
{
	int flag;
	flag = ChannelInfoState( m_pScan );
	if ( flag <= 0 )
		return flag; //error or not ready

	if ( m_nScanState == DONE_SCAN )
		return flag;

	return 0;
}

int CScan::ScanChannelTimeClock( unsigned long lMillionSecond )
{
	if ( m_nScanState == PSI_SCAN || m_nScanState == NAKED_SCAN )
		return UpdateTimeClock( m_pScan, lMillionSecond );
	return 0;
}

int CScan::ScanChannelNum( )
{
	return ChannelInfoChannelNum( m_pScan );
}
