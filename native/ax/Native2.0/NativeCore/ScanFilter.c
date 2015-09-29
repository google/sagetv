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
#include <memory.h>
#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"
#include "Remuxer.h"
#include "ChannelScan.h"
#include "ScanFilter.h"

SCAN_FILTER* CreateScanFilter()
{
	SCAN_FILTER *pScanFilter = SAGETV_MALLOC( sizeof(SCAN_FILTER) );
	SageLog(( _LOG_TRACE, 3, TEXT("Scan Filter is created: 0x%x"), pScanFilter ));
	pScanFilter->nExpectedScanBytes = 0;
	pScanFilter->nAlignScanBytes = 0;
	pScanFilter->nStreamFormat = 0;
	pScanFilter->nStreamSubFormat = 0;
	pScanFilter->dwScanTunerData = 0;
	pScanFilter->dwScanFileData = 0;
	pScanFilter->pScan = NULL;
	pScanFilter->nScanState = 0;
	return pScanFilter;
}

void ReleaseScanFilter( SCAN_FILTER* pScanFilter )
{
	if ( pScanFilter->pScan != NULL )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("Scan Filter will be released: data:%d File data:%d"),
					pScanFilter->dwScanTunerData, pScanFilter->dwScanFileData  ));
		ReleaseChannelScan( pScanFilter->pScan );
	}

	SAGETV_FREE( pScanFilter );
	SageLog(( _LOG_TRACE, 3, TEXT("Scan Filter was released: 0x%x"), pScanFilter ));
}

void StartChannelScan( SCAN_FILTER* pScanFilter, struct TUNE* pTune )
{
	ASSERT( pScanFilter->pScan == NULL );
	pScanFilter->pScan = CreateChannelScan( NULL, pTune );
	pScanFilter->Tune = *pTune;
	DoChannelScan( pScanFilter->pScan, PSI_SCAN );
	pScanFilter->nScanState = PSI_SCAN;
	return;
}

void StopChannelScan( SCAN_FILTER* pScanFilter )
{
	if ( pScanFilter->pScan == NULL )
		return;
	SageLog(( _LOG_TRACE, 3, TEXT("ChanneScan tuner close: data:%d File data:%d"),
				pScanFilter->dwScanTunerData, pScanFilter->dwScanFileData  ));
	ReleaseChannelScan( pScanFilter->pScan );
	pScanFilter->pScan = NULL;
}

static int IsQAMStream( TUNE *pTune )
{
	return ( pTune->stream_format == ATSC_STREAM && pTune->sub_format == CABLE );
}

static int IsDVBSStream( TUNE *pTune )
{
	return ( pTune->stream_format == DVB_STREAM && pTune->sub_format == SATELLITE );
}

//static int IsDVBCStream( TUNE *pTune )
//{
//	return ( pTune->stream_format == DVB_STREAM && pTune->sub_format == CABLE );
//}

static void PushChannelScanTunerData( SCAN_FILTER* pScanFilter, uint8_t* pData, uint32_t lDataLen  )
{
	int nUsedBytes;
	uint8_t *pStart;
	uint32_t dwLength;
	pStart   = pData;
	dwLength = lDataLen;
	if ( pScanFilter->nExpectedScanBytes )
	{
		int nExpectedBytes2;
		memcpy( pScanFilter->cAlignScanBuffer+pScanFilter->nAlignScanBytes, pStart, pScanFilter->nExpectedScanBytes );
		nUsedBytes = PushScanStreamData( pScanFilter->pScan, pScanFilter->cAlignScanBuffer, pScanFilter->nAlignScanBytes+pScanFilter->nExpectedScanBytes, &nExpectedBytes2 );
		if ( nExpectedBytes2 == 0 )
		{
			pStart   +=  pScanFilter->nExpectedScanBytes;
			dwLength -=  pScanFilter->nExpectedScanBytes;
		}
	}

	nUsedBytes = PushScanStreamData( pScanFilter->pScan, pStart, dwLength, &pScanFilter->nExpectedScanBytes );
	pScanFilter->nAlignScanBytes = dwLength - nUsedBytes;
	ASSERT( pScanFilter->nExpectedScanBytes+pScanFilter->nAlignScanBytes <= sizeof(pScanFilter->cAlignScanBuffer) );
	if ( pScanFilter->nAlignScanBytes > 0 && pScanFilter->nAlignScanBytes + pScanFilter->nExpectedScanBytes <= sizeof(pScanFilter->cAlignScanBuffer))
	{
		memcpy( pScanFilter->cAlignScanBuffer, pStart+nUsedBytes, pScanFilter->nAlignScanBytes );

	} else
	{
		//drop data, ask too many
		pScanFilter->nExpectedScanBytes = 0;
	}

	pScanFilter->dwScanTunerData +=lDataLen;
}


void ProcessScan( SCAN_FILTER* pScanFilter, uint8_t* pData, int32_t lDataLen )
{
	int  nScanState;
	if ( pScanFilter->nScanState == DONE_SCAN ) 
		return;
		
	PushChannelScanTunerData( pScanFilter, pData, lDataLen );
	nScanState = IsChannelInfoReady( pScanFilter->pScan );
	if ( nScanState == 2 ) //psi channels is ready
	{
		uint16_t iStreamFormat, iSubFormat;
		if ( GetStreamFormat( pScanFilter->pScan, &iStreamFormat, &iSubFormat ) )
		{
			pScanFilter->nStreamFormat = (uint8_t)iStreamFormat;
			pScanFilter->nStreamFormat = (uint8_t)iSubFormat;
		}
		if ( IsQAMStream( &pScanFilter->pScan->tune ) )
		{
			pScanFilter->Tune.stream_format = (uint8_t)pScanFilter->nStreamFormat;
			pScanFilter->Tune.sub_format    = (uint8_t)pScanFilter->nStreamFormat;
			ResetChannelScan( pScanFilter->pScan );
			DoChannelScan( pScanFilter->pScan, NAKED_SCAN );
			ChannelScanTune( pScanFilter->pScan, &pScanFilter->Tune ); 
			pScanFilter->nScanState = NAKED_SCAN;
		} else
			pScanFilter->nScanState = DONE_SCAN;
	} else
	if ( nScanState == 3 ) //naked channels is ready
	{
		MergeChannelListProgramList( pScanFilter->pScan );
		pScanFilter->nScanState = DONE_SCAN;
		SageLog(( _LOG_TRACE, 3, TEXT("*********** Scan done ************") )); 
	} else
	if ( nScanState > 0 ) //maxium parsing data
	{
		if ( nScanState == 4 )
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop PSI parsing (maxium bytes searching).") ));
		else
		if ( nScanState == 5 )
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop PSI parsing, no nit, no channel PSI (%d ms)"), UpdateTimeClock( pScanFilter->pScan, 0 ) ));
		else
		if ( nScanState == 10 )
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop PSI parsing, timeout (%d ms)."), UpdateTimeClock( pScanFilter->pScan, 0 ) ));
		else
		if ( nScanState == 6 )
		    SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop naked parsing (maxium bytes searching), no pmt.).") ));
		else
		if ( nScanState == 7 )
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop naked parsing (maxium bytes searching), no clear channel.).") ));
		else
			SageLog(( _LOG_TRACE, 3, TEXT("\t**** Stop PSI parsing, unkonw %d (%d ms)."), nScanState, UpdateTimeClock( pScanFilter->pScan, 0 ) ));

		if ( pScanFilter->nScanState == PSI_SCAN && 
			( IsQAMStream( &pScanFilter->pScan->tune ) ||
			  (IsDVBSStream( &pScanFilter->pScan->tune) && IsNakedStream( pScanFilter->pScan ) ) ) )
		{
			SageLog(( _LOG_TRACE, 3, TEXT("*********** Start scaning naked channels ************") )); 
			ResetChannelScan( pScanFilter->pScan );
			DoChannelScan( pScanFilter->pScan, NAKED_SCAN );
			ChannelScanTune( pScanFilter->pScan, &pScanFilter->Tune ); 
			pScanFilter->nScanState = NAKED_SCAN; 

		} else
		{
			SageLog(( _LOG_TRACE, 3, TEXT("*********** Scan done (state:%d %s naked:%d )************"),
				pScanFilter->nScanState, 
				StreamFormatString(pScanFilter->Tune.stream_format, pScanFilter->Tune.sub_format), 
				IsNakedStream( pScanFilter->pScan ) )); 
			pScanFilter->nScanState = DONE_SCAN;
		}
	} 
}

int	 ScanChannelTimeClock( SCAN_FILTER* pScanFilter, uint32_t lMillionSecond )
{
	if ( pScanFilter->nScanState == PSI_SCAN || pScanFilter->nScanState == NAKED_SCAN )
		return UpdateTimeClock( pScanFilter->pScan, lMillionSecond );
	return 0;
}

int	ScanChannelState( SCAN_FILTER* pScanFilter  )
{
	int flag;
	flag = ChannelInfoState( pScanFilter->pScan );
	if ( flag <= 0 )
		return flag; //error or not ready

	if ( pScanFilter->nScanState == DONE_SCAN )
		return flag;

	return 0;
}

int	ScanChannelNum( SCAN_FILTER* pScanFilter  )
{
	return ChannelInfoChannelNum( pScanFilter->pScan );
}

CHANNEL_LIST *GetScanChannelList( SCAN_FILTER* pScanFilter )
{
	MergeChannelListProgramList( pScanFilter->pScan );
	return GetChannelList( pScanFilter->pScan );
}

TUNE_LIST *GetScanTuneList( SCAN_FILTER* pScanFilter )
{
	return GetTuneList( pScanFilter->pScan );
}

