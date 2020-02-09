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

#include "..\..\..\third_party\Microsoft\FileSource\strconv.h"
#include "resource.h"
#include "FilterProperties.h"
#include "..\..\..\third_party\Microsoft\FileSource\FileSourceFilter.h"
#include "PTSParser.h"
#include "DebugLog.h"


#pragma warning(disable: 4702)
#pragma warning(disable: 4996)

#if (defined(_MSC_VER) && (_MSC_VER < 1900))
  #define snprintf _snprintf
  #define vsnprintf  vsnprintf
#endif

CUnknown * WINAPI CFilterProperties::CreateInstance(LPUNKNOWN lpunk, HRESULT *phr)
{
    ASSERT(phr);
    CFilterProperties *pNewObj = new CFilterProperties(lpunk, phr);
	DbgLog((LOG_TRACE, 3, TEXT("FilterPropterties created ")));
    return pNewObj;

} // CreateInstance


CFilterProperties::CFilterProperties(LPUNKNOWN pUnk, HRESULT *phr) 
: CBasePropertyPage(NAME("Filter Property Page"), pUnk, IDD_PROPPAGE_SMALL, IDS_TITLE),
m_hwndDialog(NULL)
{
	m_szListFileName[0] = 0;
	m_szServerHost[0] = 0;
	m_bRemoteHost = FALSE; 
}

CFilterProperties::~CFilterProperties()
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
			case IDC_BTN_FILE:
				FindLoadList();
				break;
			case IDC_BTN_LOAD_FILES:
				LoadFiles();
				break;
			case IDC_CHECK_REMOTE:
				m_bRemoteHost = IsDlgButtonChecked ( m_hwndDialog, IDC_CHECK_REMOTE );
				if ( m_bRemoteHost )
					EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_SERVER_HOST ), TRUE );
				else
					EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_SERVER_HOST ), FALSE );
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
            }
			OnUpdate();
        }

        default:
            return (INT_PTR)FALSE;
    }
    
	return CBasePropertyPage::OnReceiveMessage(hwnd,uMsg,wParam,lParam);
    //return TRUE;

}

HRESULT CFilterProperties::OnConnect(IUnknown *pUnknown)
{
	HRESULT hr;
	hr = pUnknown->QueryInterface(IID_IFileSourceFilter, (void **)&m_pFSFilter);
    if(FAILED(hr))
    {
        return E_NOINTERFACE;
    }
	hr = pUnknown->QueryInterface(IID_ISharedAsyncFile, (void **)&m_pShareAsync);
    if(FAILED(hr))
    {
        return E_NOINTERFACE;
    }
    ASSERT(m_pShareAsync);
	ASSERT(m_pFSFilter);
	return S_OK;

}

HRESULT CFilterProperties::OnDisconnect()
{
	SAFE_RELEASE(m_pFSFilter);
	SAFE_RELEASE(m_pShareAsync);
	return S_OK;
}

void CFilterProperties::SetDirty()
{
    m_bDirty = TRUE;

    if(m_pPageSite)
    {
        m_pPageSite->OnStatusChange(PROPPAGESTATUS_DIRTY);
    }

}

BOOL CFilterProperties::OnUpdate()
{
	return TRUE;
}

HRESULT CFilterProperties::OnApplyChanges()
{
	return S_OK;
}

HRESULT CFilterProperties::SaveSettings(void)
{
	GetDlgItemText( m_hwndDialog, IDC_EDIT_FILENAME, m_szListFileName,  sizeof( m_szListFileName ) );
	GetDlgItemText( m_hwndDialog, IDC_EDIT_SERVER_HOST, m_szServerHost, sizeof(m_szServerHost) );
	m_bRemoteHost = IsDlgButtonChecked ( m_hwndDialog, IDC_CHECK_REMOTE );

	WriteProfileString( "FileSource", "FileList", m_szListFileName );
	WriteProfileString( "FileSource", "RemoteHostEnabled", m_bRemoteHost? "TRUE" :"FALSE" );
	WriteProfileString( "FileSource", "RemoteHost", m_szServerHost  );
	return S_OK;
}

void CFilterProperties::DisplayInfo()
{
	GetProfileString( "FileSource", "FileList", "", m_szListFileName, sizeof(m_szListFileName) );
	GetProfileString( "FileSource", "RemoteHost", "", m_szServerHost, sizeof(m_szServerHost)  );
	char temp[32];
	GetProfileString( "FileSource", "RemoteHostEnabled", "", temp, sizeof(temp) );
	m_bRemoteHost = !strncmp( temp, "TRUE", 4 );
	CheckDlgButton( m_hwndDialog, IDC_CHECK_REMOTE, m_bRemoteHost );
	if ( m_bRemoteHost )
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_SERVER_HOST ), TRUE );
	else
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_SERVER_HOST ), FALSE );
	if ( m_szListFileName[0] )
		SetDlgItemText( m_hwndDialog, IDC_EDIT_FILENAME, m_szListFileName );
	if ( m_szServerHost[0] )
		SetDlgItemText( m_hwndDialog, IDC_EDIT_SERVER_HOST, m_szServerHost );
	if ( m_bRemoteHost )
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_SERVER_HOST ), TRUE );
	else
		EnableWindow( GetDlgItem( m_hwndDialog, IDC_EDIT_SERVER_HOST ), FALSE );
}

char* FileNameStripPath( const char* pszFullFileName, char* pszFileName, int nSizeOfFileName )
{
	char driver[_MAX_DRIVE], dir[_MAX_DIR], filename[_MAX_FNAME], ext[_MAX_EXT];
	pszFileName;
	_splitpath( pszFullFileName, driver, dir, filename, ext );
	strncpy( pszFileName, filename, nSizeOfFileName );
	return pszFileName;
}

void CFilterProperties::LoadFiles()
{
	FILE *fp;
	char *pFileName;
	char buf[1024]={0};
	char *pEditText=NULL;
	int  nEditTextSize=2048, nEditTextLen=0;
	char FName[256];

	int line=0, i, serial = 0;
	GetDlgItemText( m_hwndDialog, IDC_EDIT_FILENAME, m_szListFileName,  sizeof( m_szListFileName ) );
	fp = fopen( m_szListFileName, "r" );
	if ( fp == NULL )
		return;

	m_bRemoteHost = IsDlgButtonChecked ( m_hwndDialog, IDC_CHECK_REMOTE );
	if ( m_bRemoteHost )
	{
		GetDlgItemText( m_hwndDialog, IDC_EDIT_SERVER_HOST, m_szServerHost, sizeof(m_szServerHost) );
		m_pShareAsync->put_RemoteHostname( m_szServerHost ); 
	}

	m_pShareAsync->Reset();

	pEditText = new char[nEditTextSize];
	while( !feof( fp ) )
	{
		memset( buf, 0, sizeof(buf) );
		fgets( buf, sizeof(buf)-1, fp );
		line++;
		
		//rip off '\n'
		for ( i = lstrlenA(buf); i>=0 ; i-- )
			if ( buf[i] == '\n' || buf[i] == '\r' ) buf[i] = 0x0;
			
		//skip white space
		i = 0;
		while ( (buf[i] == ' ' || buf[i] == '\t' ) && i<sizeof(buf) ) i++;

		pFileName = buf+i;
		if ( pFileName && pFileName[0] )
		{
			FileNameStripPath( pFileName, FName, sizeof(FName ) );
			int cc =  lstrlenA(pFileName)+1;
			LPCOLESTR pszW = (LPOLESTR)CoTaskMemAlloc(cc*2);
			if ( MultiByteToWideChar(CP_ACP, 0, pFileName, cc, (LPWSTR)pszW, cc ))
			{
				HRESULT hr = m_pShareAsync->LoadEx( serial, (LPCOLESTR)pszW, NULL );
				CoTaskMemFree( (void*)pszW );
				if ( hr == S_OK )
				{
					FILE_INF FileInf;
					m_pShareAsync->GetFileInf( serial, (void*)&FileInf, sizeof(FILE_INF) );
					nEditTextLen += snprintf( pEditText+nEditTextLen, nEditTextSize-nEditTextLen, "%s:%s\r\n",
						FName, (LPCTSTR)Disp( (CRefTime)PTS2MT(FileInf.timeline) ) );
					serial++;
				} else
				{
					nEditTextLen += snprintf( pEditText+nEditTextLen, nEditTextSize-nEditTextLen, "%s Failed\r\n", FName );
				}
			}
			SetDlgItemText( m_hwndDialog, IDC_EDIT_LIST, pEditText );
		}
	
	}

	fclose( fp );
	if ( pEditText != NULL )
		delete [] pEditText;

	SaveSettings();

}


void CFilterProperties::FindLoadList()
{
    OPENFILENAME opf;
	char ListFileName[_MAX_PATH]={"*.lst"};
    opf.hwndOwner = 0;
    opf.lpstrFilter = "List Files, (*.lst)";
    opf.lpstrCustomFilter = 0;
    opf.nMaxCustFilter = 0L;
    opf.nFilterIndex = 1L;
    opf.lpstrFile = ListFileName;
    opf.nMaxFile = sizeof(ListFileName);
    opf.lpstrFileTitle = 0;
    opf.nMaxFileTitle=50;
	opf.lpstrInitialDir = "";
    opf.lpstrTitle = "Select File List";
    opf.nFileOffset = 0;
    opf.nFileExtension = 0;
    opf.lpstrDefExt = "*.lst";
    opf.lpfnHook = NULL;
    opf.lCustData = 0;
    opf.Flags = (OFN_PATHMUSTEXIST | OFN_OVERWRITEPROMPT) & ~OFN_ALLOWMULTISELECT;
    opf.lStructSize = sizeof(OPENFILENAME);

    if( GetOpenFileName(&opf) )
    {
		SetDlgItemText( m_hwndDialog, IDC_EDIT_FILENAME, opf.lpstrFile );
    }

}
