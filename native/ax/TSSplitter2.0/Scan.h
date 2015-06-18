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

#pragma once

#define MAX_SCAN_CACHE_DATA  (64*1024*1024)
//#define MAX_SCAN_CACHE_DATA  (50*1024*1024)
#define TS_PACKET_MAX_LENGTH  (188+4)

class CScan
{
public:
	CScan(void);
	~CScan(void);

	void	StartChannelScan( struct TUNE* pTune );
	void	CloseChannelScan( );
	void	ProcessScan( unsigned char* pData, long lDataLen );
	int		ScanChannelState( );
	int		ScanChannelTimeClock( unsigned long lMillionSecond );
	int		ScanChannelNum( );
	void	EnableFileCache( char* pszCacheFileName );
	CHANNEL_LIST*	GetChannelList( );
	TUNE_LIST*      GetTuneList( );

private:
	int			  m_nStreamFormat;
	int			  m_nStreamSubFormat;
	int			  m_nScanState;
	struct SCAN*  m_pScan;
	unsigned char m_cAlignScanBuffer[ TS_PACKET_MAX_LENGTH ];
	int		      m_nExpectedScanBytes;
	int		      m_nAlignScanBytes;

	int			  m_bScanFileCache;
	char		  m_szScanCacheDataFileName[_MAX_PATH];
	int			  m_hScanCacheDataFile;
	unsigned long m_dwScanCacheDataSize;
	unsigned long m_dwScanCacheDataMaxSize;
	TUNE		  m_Tune;

	unsigned long m_dwScanTunerData;
	unsigned long m_dwScanFileData;
	void	CacheChannelScanData( unsigned char* pData, unsigned long lData  );
	void	PushChannelScanTunerData( unsigned char* pData, unsigned long lData  );
	void	PushChannelScanFileData( );
	int		IsQAMStream( TUNE *pTune );
	int		IsDVBSStream( TUNE *pTune );
	int		IsDVBCStream( TUNE *pTune );
	int		IsCacheDataFull( );

};

