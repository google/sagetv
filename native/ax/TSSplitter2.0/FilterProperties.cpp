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

#pragma warning(disable : 4996)

#ifndef _WIN64
  #define _USE_32BIT_TIME_T
#endif

#include <windows.h>
#include <commctrl.h>
#include <streams.h>
//#include <atlbase.h>
//#include <atlconv.h>
#include <bdaiface.h>
#include <bdatypes.h>
#include <stdio.h>


#include "../../../third_party/Microsoft/TSSplitter/dbg.h"
#include <stdarg.h>

#include "resource.h"
#include "FilterProperties.h"
#include "iTSSplitter.h"
#include "TSSplitterGuid.h"
#include "NativeCore.h"
#include "ChannelScan.h"
#include "ChannelScanInc.h"
#include "TSSplitFilter.h"

#pragma warning(disable: 4702)

static int MakeChannelInfString( CHANNEL_LIST *pChannelList, int nIndex, char* pInfBuf, int nInfBufSize );
CUnknown * WINAPI CFilterProperties::CreateInstance(LPUNKNOWN lpunk, HRESULT *phr)
{
    ASSERT(phr);
    CFilterProperties *pNewObj = new CFilterProperties(lpunk, phr);
	DbgLog((LOG_TRACE, 3, TEXT("FilterPropterties created ")));
    return pNewObj;

} // CreateInstance


CFilterProperties::CFilterProperties(LPUNKNOWN pUnk, HRESULT *phr) 
: CBasePropertyPage(NAME("Filter Property Page"), pUnk, IDD_DIALOG1, IDS_TITLE),
m_hwndDialog(NULL)
{
	m_bScanInProgress = FALSE;
}


void CFilterProperties::EnableATSCInput()
{
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_MAJOR ), TRUE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_MINOR ), TRUE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_PROGRAM ),  TRUE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_ONID ), FALSE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_TSID ), FALSE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_SID ),  FALSE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_VPID ), FALSE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_VPID ), FALSE );
}

void CFilterProperties::EnableDVBInput()
{
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_MAJOR ), FALSE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_MINOR ), FALSE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_PROGRAM ),  FALSE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_ONID ), TRUE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_TSID ), TRUE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_SID ),  TRUE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_VPID ),  TRUE );
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_VPID ),  TRUE );

}

#define IDT_TIMER 2000
CFilterProperties *g_pCFilterProperties = NULL;
 void CALLBACK   LocalTimerProc( HWND hwnd, UINT uMsg, UINT uTimerID, DWORD dwTime )           //   current   system   time     
 {    
	static DWORD ct=0;
	if ( uTimerID != IDT_TIMER ) return;
	ct++;
	g_pCFilterProperties->OnTimer( ct );
}     

 INT_PTR CFilterProperties::OnReceiveMessage( HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam )
{
	int checked;
    switch(uMsg)
    {
        case WM_INITDIALOG:
        {
            m_hwndDialog = hwnd;
			g_pCFilterProperties = this;
			SetForegroundWindow( hwnd );
			DisplayInfo();
		    SetTimer( hwnd, IDT_TIMER, 1000, (TIMERPROC)LocalTimerProc );  
  
            return (INT_PTR)TRUE;
        }

        case WM_DESTROY:
        {
			KillTimer(hwnd,   IDT_TIMER );     
            DestroyWindow(m_hwndDialog);
            return (INT_PTR)TRUE;
        }

        case WM_COMMAND:
        {
            switch (LOWORD (wParam)) {
			case IDOK: 
				SetDirty();
				break ;
			case IDC_RADIO_ATSC:
				//checked = IsDlgButtonChecked ( GetDlgItem( m_hwndDialog, IDC_RADIO_ATSC), IDC_RADIO_ATSC );
				checked = IsDlgButtonChecked ( m_hwndDialog, IDC_RADIO_ATSC );
				EnableATSCInput();
				break;
			case IDC_RADIO_DVB:
				checked = IsDlgButtonChecked ( m_hwndDialog, IDC_RADIO_DVB );
				EnableDVBInput();
				break;
			case IDC_RADIO_UNKOWN:
				checked = IsDlgButtonChecked ( m_hwndDialog, IDC_RADIO_UNKOWN );
				break;

			case IDC_BTN_SCAN_CHANNEL_PSI:
				OnBtnScanChannel( 0 );
				break;

			case IDC_BTN_SCAN_CHANNEL_RAW:
				OnBtnScanChannel( 1 );
				break;

			case IDC_BTN_CHANNEL_LIST:
				OnBtnChannelList( );
				break;

			case IDC_BTN_SCAN_DONE:
				OnBtnScanDone( );
			default:;
            };
            return (INT_PTR)TRUE;
        }

        case WM_NOTIFY:
        {
            switch (wParam){
			case -1: break;
			default:;
            } // end switch (wParam)
			OnUpdate();
        }

        default:
            return (INT_PTR)FALSE;
    }
    
	return CBasePropertyPage::OnReceiveMessage(hwnd,uMsg,wParam,lParam);
    //return TRUE;

} // OnReceiveMessage

HRESULT CFilterProperties::OnConnect(IUnknown *pUnknown)
{
	HRESULT hr = pUnknown->QueryInterface(IID_ITSParser2, (void **) &m_pTSSplitter);
    if(FAILED(hr))
    {
        return E_NOINTERFACE;
    }
    ASSERT(m_pTSSplitter);

	
    //IBaseFilter *pTSSpliter;
    //hr = m_pTSSplitter->QueryInterface(IID_IBaseFilter, (void **) &pTSSpliter);
    ////RETURN_FALSE_IF_FAILED(TEXT("CFilterProperties::OnUpdate() QueryInterface() failed."), hr);
	
	return S_OK;
}

HRESULT CFilterProperties::OnDisconnect()
{
	SAFE_RELEASE(m_pTSSplitter);
	return S_OK;
}

void CFilterProperties::SetDirty()
{
    m_bDirty = TRUE;

    if(m_pPageSite)
    {
        m_pPageSite->OnStatusChange(PROPPAGESTATUS_DIRTY);
    }

} // SetDirty

BOOL CFilterProperties::OnUpdate()
{
	return TRUE;
}

HRESULT CFilterProperties::OnApplyChanges()
{
	BOOL dwRet;
	int bATSCChecked, bDVBChecked, bUnKownChecked;
	char buf[64];
	bATSCChecked = IsDlgButtonChecked ( m_hwndDialog,   IDC_RADIO_ATSC   );
	bDVBChecked = IsDlgButtonChecked (  m_hwndDialog,   IDC_RADIO_DVB    );
	bUnKownChecked = IsDlgButtonChecked ( m_hwndDialog, IDC_RADIO_UNKOWN );

	if ( bATSCChecked )
	{
		int nMajor, nMinor, nProgram;
		GetDlgItemText( m_hwndDialog, IDC_EDIT_MAJOR, buf, sizeof(buf) );
		if ( buf[0] ) nMajor = atoi(buf);	else nMajor = -1;
		GetDlgItemText( m_hwndDialog, IDC_EDIT_MINOR, buf, sizeof(buf) );
		if ( buf[0] ) nMinor = atoi(buf); else nMinor = -1;
		GetDlgItemText( m_hwndDialog, IDC_EDIT_PROGRAM, buf, sizeof(buf) );
		if ( buf[0] ) nProgram = atoi(buf); else nProgram = -1;
		m_pTSSplitter->StopParser();
		m_pTSSplitter->PickupATSCChannel( nMajor, nMinor, nProgram, "", 1, &dwRet );
		m_pTSSplitter->StartParser();
		
	} else
	if ( bDVBChecked ) 
	{
		int nOnid, nTsid, nSid, nVpid, nApid;
		GetDlgItemText( m_hwndDialog, IDC_EDIT_ONID, buf, sizeof(buf) );
		nOnid = atoi(buf);
		GetDlgItemText( m_hwndDialog, IDC_EDIT_TSID, buf, sizeof(buf) );
		nTsid = atoi(buf);
		GetDlgItemText( m_hwndDialog, IDC_EDIT_SID, buf, sizeof(buf) );
		nSid = atoi(buf);
		GetDlgItemText( m_hwndDialog, IDC_EDIT_VPID, buf, sizeof(buf) );
		nVpid = atoi(buf);
		GetDlgItemText( m_hwndDialog, IDC_EDIT_APID, buf, sizeof(buf) );
		nApid = atoi(buf);
		m_pTSSplitter->StopParser();
		if ( nVpid && nApid )
		{
			TUNE tune={0};
			tune.tune_string_type = 3;
			tune.u.dvb.dvb_type = SATELLITE;
			tune.u.dvb.dvb.s.freq = 0;
			tune.u.dvb.onid = nOnid;
			tune.u.dvb.tsid = nTsid;
			tune.u.dvb.sid  = nSid;
			{
				tune.pids_table.pid_num = 2;
				tune.pids_table.pid_tbl[0].pid = nVpid;
				tune.pids_table.pid_tbl[0].type = 0xf1; //UNKNOW_VIDEO_STREAM_TYPE
				tune.pids_table.pid_tbl[1].pid = nApid;
				tune.pids_table.pid_tbl[1].type = 0xf2; //UNKNOW_AUDIO_STREAM_TYPE
			}
			m_pTSSplitter->LockTSChannel( &tune, 0 );
		} else
		{
			m_pTSSplitter->PickupDVBChannel( nOnid, nTsid, nSid, "", 1, &dwRet );
		}
		m_pTSSplitter->StartParser();
	} else
	if ( bUnKownChecked )
	{
		int nChannel, nProgram;
		GetDlgItemText( m_hwndDialog, IDC_EDIT_CHANNEL, buf, sizeof(buf) );
		nChannel = atoi(buf);
		GetDlgItemText( m_hwndDialog, IDC_EDIT_CHANNEL_PROGRAM, buf, sizeof(buf) );
		nProgram = atoi(buf);
		m_pTSSplitter->StopParser();
		m_pTSSplitter->PickupTSChannel( nChannel, nProgram, "", 1, &dwRet );
		m_pTSSplitter->StartParser();
	}

	return S_OK;
}

HRESULT CFilterProperties::OnDeactivate(void)
{
	return S_OK;
}

void CFilterProperties::OnTimer( DWORD count )
{
	int bRet, nChannelNum;
	if ( m_bScanInProgress )
	{
		char buf[64];
		HRESULT hr = m_pTSSplitter->ScanChannelState( &bRet, &nChannelNum, count );
		if ( hr != NOERROR )
			snprintf( buf, sizeof(buf), "Scan not ruuning" );
		else
		if ( bRet == 0 )
			snprintf( buf, sizeof(buf), "Scanning in progress" );
		else
		if ( bRet == 1 )
			snprintf( buf, sizeof(buf), "Scan stopped" );
		else
		if ( bRet == 2 )
			snprintf( buf, sizeof(buf), "PSI channel info ready" );
		else
		if ( bRet == 3 )
			snprintf( buf, sizeof(buf), "naked channel info ready" );
		else
		if ( bRet == 4 )
			snprintf( buf, sizeof(buf), "maxium psi search" );
		else
		if ( bRet == 5 )
			snprintf( buf, sizeof(buf), "no psi info" );
		else
		if ( bRet == 6 )
			snprintf( buf, sizeof(buf), "naked channel info ready" );
		else
		if ( bRet == 7 )
			snprintf( buf, sizeof(buf), "naked channel info ready" );
		else
		if ( bRet == 10 )
			snprintf( buf, sizeof(buf), "maxium timeout" );
		else
		if ( bRet == -1 )
			snprintf( buf, sizeof(buf), "no data incoming" );
		else
		if ( bRet == -2 )
			snprintf( buf, sizeof(buf), "low SN" );
		else
			snprintf( buf, sizeof(buf), "error unkown channel state %d", bRet );
		
		SetDlgItemText( m_hwndDialog, IDC_EDIT_CHANNEL_STATE, buf );
		if ( bRet > 0 )
			m_bScanInProgress = FALSE;
	}
}

void CFilterProperties::OnBtnScanChannel( int nType )
{
	BOOL ret;
	TUNE tune={0};
	if ( nType == 1 )  //QAM channel
	{
		tune.stream_format = ATSC_STREAM;
		tune.sub_format = CABLE;
	}
	m_pTSSplitter->ScanChannel( &tune, &ret );
	m_bScanInProgress = TRUE;
	return ;
}

void CFilterProperties::OnBtnScanDone( )
{
	m_pTSSplitter->ReleaseScanChannel( );
	m_bScanInProgress = FALSE;
	SetDlgItemText( m_hwndDialog, IDC_EDIT_CHANNEL_STATE, "Scan was released" );
	return ;
}

void CFilterProperties::OnBtnChannelList( )
{
	HRESULT hr;
	BOOL ret;
	CHANNEL_LIST* pChannelList;
	TUNE_LIST* pTuneList;
	int i;
	FILE *fp;
	hr = m_pTSSplitter->GetScanChannelList( (void**)&pChannelList, &ret );
	if ( hr != NOERROR ) 
	{
		SetDlgItemText( m_hwndDialog, IDC_EDIT_CHANNEL_STATE, "the channel scan was close" );
	}
	hr = m_pTSSplitter->GetScanTuneList( (void**)&pTuneList, &ret );
	if ( hr != NOERROR ) 
		return;

	fp = fopen( "scan.frq", "w" );
	if ( fp == NULL ) return;
	for ( i = 0; i<pChannelList->channel_num; i++ )
	{
		char buf[1024];
		int pos;
		pos = MakeChannelInfString( pChannelList, i, buf, sizeof(buf)-1 );
		buf[pos]=0x0;
		fprintf( fp, buf );
	}

	fclose( fp );
	return ;
}



void CFilterProperties::DisplayInfo()
{
	WORD wFormat, wSubFormat;
	m_pTSSplitter->GetNetworkType( &wFormat, &wSubFormat );
	if ( wFormat == 1 )
	{
		if ( wSubFormat == 2 )
			SetDlgItemText( m_hwndDialog, IDC_EDIT_STREAM_FORMAT, "QAM stream" );
		else
			SetDlgItemText( m_hwndDialog, IDC_EDIT_STREAM_FORMAT, "ATSC stream" );
		CheckRadioButton( m_hwndDialog, IDC_RADIO_ATSC, IDC_RADIO_UNKOWN, IDC_RADIO_ATSC );
		EnableATSCInput();
	} else
	if ( wFormat == 2 )
	{
		if ( wSubFormat == 1 )
			SetDlgItemText( m_hwndDialog, IDC_EDIT_STREAM_FORMAT, "DVB-T" );
		else
		if ( wSubFormat == 2 )
			SetDlgItemText( m_hwndDialog, IDC_EDIT_STREAM_FORMAT, "DVB-C" );
		else
		if ( wSubFormat == 3 )
			SetDlgItemText( m_hwndDialog, IDC_EDIT_STREAM_FORMAT, "DVB-S" );
		else
			SetDlgItemText( m_hwndDialog, IDC_EDIT_STREAM_FORMAT, "DVB" );
		CheckRadioButton( m_hwndDialog, IDC_RADIO_ATSC, IDC_RADIO_UNKOWN, IDC_RADIO_DVB );
		EnableDVBInput();
	}
}

static int MakeChannelInfString( CHANNEL_LIST *pChannelList, int nIndex, char* pInfBuf, int nInfBufSize )
{
	int i, pos = 0;
	i = nIndex;
	if ( pChannelList->stream_format == ATSC_STREAM )
	{
		if ( pChannelList->sub_format == TERRESTRIAL )
		{
			if ( i<pChannelList->channel_num )
			{
				if (  pChannelList->channel[i].state == 0x03 )
					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "CH:%d major:%d minor:%d prog:%d phy:%d ", i,
						pChannelList->channel[i].u.atsc.major_num, 
						pChannelList->channel[i].u.atsc.minor_num, 
						pChannelList->channel[i].u.atsc.program_id, 
						pChannelList->channel[i].u.atsc.physical_ch	);

					pos += STR_FREQ( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.atsc.u.atsc.freq ); 
					pos += STR_BAND( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.atsc.u.atsc.band ); 

					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "ctrl:%d #:%s %s\n", 1,
						pChannelList->channel[i].u.atsc.name,
						pChannelList->channel[i].u.atsc.service_type==3?"(r)":"" );
			}

		} else
		if ( pChannelList->sub_format == CABLE )
		{
			if ( i<pChannelList->channel_num )
			{
				if (  pChannelList->channel[i].state == 0x03 )
					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "CH:%d major:%d minor:%d prog:%d phy:%d ", i,
						pChannelList->channel[i].u.atsc.major_num, 
						pChannelList->channel[i].u.atsc.minor_num, 
						pChannelList->channel[i].u.atsc.program_id, 
						pChannelList->channel[i].u.atsc.physical_ch	);

					pos += STR_FREQ(        pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.atsc.u.qam.freq        ); 
					pos += STR_SYMBOL_RATE( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.atsc.u.qam.symbol_rate );
					pos += STR_MODULATION(  pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.atsc.u.qam.modulation  );

					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "ctrl:%d #:%s %s\n", 1,
						pChannelList->channel[i].u.atsc.name,
						pChannelList->channel[i].u.atsc.service_type==3?"(r)":"" );
			}
		}

	} else
	if ( pChannelList->stream_format == DVB_STREAM )
	{
		if ( pChannelList->sub_format == TERRESTRIAL )
		{
			if ( i<pChannelList->channel_num )
				if (  pChannelList->channel[i].state == 0x03 )
				{
					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "CH:%d onid:%d tsid:%d sid:%d ", i,
						pChannelList->channel[i].u.dvb.onid, 
						pChannelList->channel[i].u.dvb.tsid,
						pChannelList->channel[i].u.dvb.sid );
	
					pos += STR_FREQ( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.t.freq ); 
					pos += STR_BAND( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.t.band ); 
					pos += STR_MODE( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.t.trans_mode ); 

					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "ctrl:%d #:%s %s\n", 
						pChannelList->channel[i].u.dvb.ca ? 2:1,
						pChannelList->channel[i].u.dvb.name,
						pChannelList->channel[i].u.dvb.service_type==2?"(r)":"" );

				}
				             
		} else
		if ( pChannelList->sub_format == CABLE )
		{
			if ( i<pChannelList->channel_num )
				if (  pChannelList->channel[i].state == 0x03 )
				{
					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "CH:%d onid:%d tsid:%d sid:%d ", i,
						pChannelList->channel[i].u.dvb.onid, 
						pChannelList->channel[i].u.dvb.tsid,
						pChannelList->channel[i].u.dvb.sid );

					pos += STR_FREQ(        pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.c.freq        ); 
					pos += STR_SYMBOL_RATE( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.c.symbol_rate );
					pos += STR_MODULATION(  pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.c.modulation  );
					pos += STR_FEC_RATE_IN( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.c.fec_in_rate );
					pos += STR_FEC_OUT(     pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.c.fec_out     );

					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "ctrl:%d #:%s %s\n", 
						pChannelList->channel[i].u.dvb.ca ? 2:1,
						pChannelList->channel[i].u.dvb.name,
						pChannelList->channel[i].u.dvb.service_type==2?"(r)":"" );
				}

		} else
		if ( pChannelList->sub_format == SATELLITE  )
		{
			for ( i = 0; i<pChannelList->channel_num; i++ )
				if (  pChannelList->channel[i].state == 0x03 )
				{
					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "CH:%d onid:%d tsid:%d sid:%d ", i,
						pChannelList->channel[i].u.dvb.onid, 
						pChannelList->channel[i].u.dvb.tsid,
						pChannelList->channel[i].u.dvb.sid );

					pos += STR_FREQ(        pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.freq );
					pos += STR_SYMBOL_RATE( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.symbol_rate);
					pos += STR_MODULATION(  pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.modulation );
					pos += STR_FEC_RATE_IN( pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.fec_rate   );
					pos += STR_FEC_IN(		pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.fec		  );
					pos += STR_POL(         pInfBuf+pos, nInfBufSize-pos, pChannelList->channel[i].u.dvb.dvb.s.pol		  );


					pos += snprintf( pInfBuf+pos, nInfBufSize-pos, "ctrl:%d #:%s %s\n", 
						pChannelList->channel[i].u.dvb.ca ? 2:1,
						pChannelList->channel[i].u.dvb.name,
						pChannelList->channel[i].u.dvb.service_type==2?"(r)":"" );
				}
		}
	}
	return pos;
}
