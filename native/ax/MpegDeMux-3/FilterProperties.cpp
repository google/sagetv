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

#include <windows.h>
#include <commctrl.h>
#include <streams.h>
//#include <atlbase.h>
//#include <atlconv.h>
#include <bdaiface.h>
#include <bdatypes.h>
#include <stdio.h>

#include "../../../third_party/Microsoft/FileSource/strconv.h"
#include "resource.h"
#include "FilterProperties.h"
#include "DeMuxFilter.h"

#pragma warning(disable: 4702)


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
}

INT_PTR CFilterProperties::OnReceiveMessage( HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam )
{
    switch(uMsg)
    {
        case WM_INITDIALOG:
        {
            m_hwndDialog = hwnd;
			DisplayInfo();
            return (INT_PTR)TRUE;
        }

        case WM_DESTROY:
        {
            DestroyWindow(m_hwndDialog);
            return (INT_PTR)TRUE;
        }

        case WM_COMMAND:
        {
            switch (LOWORD (wParam)) {
			case IDOK: 
				SetDirty();
				break ;
			case IDC_BTN_UPDATE:
				DisplayInfo();
				break;
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
	HRESULT hr = pUnknown->QueryInterface(IID_IMpegDeMux, (void **) &m_pMpegDemux);
    if(FAILED(hr))
    {
        return E_NOINTERFACE;
    }
    ASSERT(m_pMpegDemux);

    //IBaseFilter * pParserFilter ;
    //hr = m_pMpegDemux->QueryInterface(IID_IBaseFilter, (void **) &pParserFilter);
    //RETURN_FALSE_IF_FAILED(TEXT("CFilterProperties::OnUpdate() QueryInterface() failed."), hr);

	/*
    FILTER_INFO Info;
    IFilterGraph * pGraph;
    hr = pParserFilter->QueryFilterInfo(&Info);

    RETURN_FALSE_IF_FAILED(TEXT("CFilterProperties::OnUpdate() QueryFilterInfo() failed."), hr);
    pGraph = Info.pGraph;
    pParserFilter->Release();

    hr = pGraph->QueryInterface(IID_IGraphBuilder, (void **) & m_pGraphBuilder);
    RETURN_FALSE_IF_FAILED(TEXT("CFilterProperties::OnUpdate() QueryInterface() failed."), hr);

    // get demux filter
    hr = GetDemuxFilter(pGraph, &m_pDemux);
    RETURN_FALSE_IF_FAILED(TEXT("CFilterProperties::OnUpdate() GetDemuxFilter() failed."), hr);
    pGraph->Release();

    // if there is no streaming, the following variables will not be initialized.
    if(m_pDemux != NULL && m_pGraphBuilder != NULL)
	{
        hr = m_pGraphBuilder->QueryInterface(IID_IMediaControl, (void **) & m_pMediaControl);
        RETURN_FALSE_IF_FAILED( TEXT(" CFilterProperties::OnUpdate():Failed to QI IMediaControl."), hr);
    }

    if(!OnUpdate())
        return FALSE;

	*/
	return S_OK;
}

HRESULT CFilterProperties::OnDisconnect()
{
	SAFE_RELEASE(m_pMpegDemux);
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
	char txt[100]={0};
	long rate;
	int i, channel;
	long block_size, buffer_size;

	GetDlgItemText( m_hwndDialog, IDC_EDIT_PLAY_RATE, txt, sizeof(txt) );
	rate = atoi( txt );
	m_pMpegDemux->SetPlayRate( rate );

	GetDlgItemText( m_hwndDialog, IDC_EDIT_TS_CHANNEL, txt, sizeof(txt) );
	for ( i = 0; i<(int)strlen(txt)-1 && i<(int)sizeof(txt); i++ )
		if ( txt[i] >= '0' && txt[i] <= '9' ) break;

	if ( txt[i] >= '0' && txt[i] <= '9' ) 
	{
		channel = atoi( txt )-1;
		if ( channel >= 0 )
			m_pMpegDemux->SelectChannel( channel );
		else
			SetDlgItemText( m_hwndDialog, IDC_EDIT_TS_CHANNEL, "" );
	}
	
	block_size = GetDlgItemInt( m_hwndDialog, IDC_EDIT_BLOCK_SIZE, FALSE, FALSE );
	buffer_size = GetDlgItemInt( m_hwndDialog, IDC_EDIT_BUFFER_SIZE, FALSE, FALSE );
	if ( block_size > 0 && buffer_size > 0 && block_size < buffer_size )
	{
		HRESULT hr = m_pMpegDemux->SetAlloProp( block_size*1024, buffer_size*1024 );
		if ( hr == S_OK )
		{
			HKEY root_key = HKEY_LOCAL_MACHINE;
			HKEY my_key;
			if ( RegOpenKeyEx(root_key, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDeMux", 0, KEY_SET_VALUE, &my_key) == ERROR_SUCCESS)
			{
				if ( ( hr = RegSetValueEx(my_key, "BlockSize", 0, REG_DWORD, (const BYTE*)&block_size, sizeof(block_size) )) == ERROR_SUCCESS)
					if ( ( hr = RegSetValueEx(my_key, "BufferSize", 0, REG_DWORD, (const BYTE*)&buffer_size, sizeof(buffer_size) )) == ERROR_SUCCESS)
					{
					}
				RegCloseKey(my_key);
			}
		}
	
	}
	else
	{
		SetDlgItemText( m_hwndDialog, IDC_EDIT_INFOMATION, "ERROR: block size or buffer size is invalid!!!" );
	}



	return S_OK;
}

HRESULT CFilterProperties::OnDeactivate(void)
{
	return S_OK;
}

void CFilterProperties::DisplayInfo()
{
	LONGLONG    rtDuration;
	STATISTIC   *pStatistic;
	long block_size, buffer_size;
	long rate;
	long channel;
	char txt[100];
	char msg[512]={0};
	if ( m_hwndDialog == NULL ) return ;
	m_pMpegDemux->Duration( &rtDuration );
	m_pMpegDemux->GetPlayRate( &rate );
	m_pMpegDemux->GetSelectedChannel( &channel );
	m_pMpegDemux->GetStatistic( (void**)&pStatistic );
	if ( pStatistic->message != NULL && pStatistic->message[0] )
		strncpy( msg, pStatistic->message, sizeof(msg) );
	
	m_pMpegDemux->GetAlloProp( &block_size, &buffer_size );

	SetDlgItemInt( m_hwndDialog, IDC_EDIT_BLOCK_SIZE, block_size/1024, FALSE );
	SetDlgItemInt( m_hwndDialog, IDC_EDIT_BUFFER_SIZE, buffer_size/1024, FALSE );

	sprintf( txt, "%d", rate );
	SetDlgItemText( m_hwndDialog, IDC_EDIT_PLAY_RATE, txt );
	//sprintf( txt, "Duration:%s", (LPCTSTR)Disp( (CRefTime)rtDuration) );
	SetDlgItemText( m_hwndDialog, IDC_EDIT_BITRATE, txt );
	sprintf( txt, "Duration:%s", (LPCTSTR)Disp( (CRefTime)rtDuration) );
	if ( channel >= 0 )
	{
		sprintf( txt, "%d", channel+1 );
		SetDlgItemText( m_hwndDialog, IDC_EDIT_TS_CHANNEL, txt );
	}


	SetDlgItemInt( m_hwndDialog, IDC_EDIT_BITRATE, pStatistic->bitrates/1000, FALSE );
	SetDlgItemInt( m_hwndDialog, IDC_EDIT_OVERFLOW, pStatistic->overflow, FALSE );
	int sec,ms;
	sec = pStatistic->test_data1/UNITS;
	ms = ( pStatistic->test_data1%UNITS )*100/UNITS;
	sprintf( txt, "%d.%02d s", sec, ms );
	SetDlgItemText( m_hwndDialog, IDC_EDIT_DATA1, txt );
	SetDlgItemInt( m_hwndDialog, IDC_EDIT_DATA2, pStatistic->test_data2/1000, FALSE );
	SetDlgItemText( m_hwndDialog, IDC_EDIT_DEBUG_MSG, msg );


}
