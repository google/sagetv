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
#include <stdio.h>
#include <time.h>
#include "../../include/isharedasync.h"
#include "..\..\..\third_party\Microsoft\FileSource\strconv.h"
#include "FileStreamData.h"
#include "NetStreamData.h"
#include "StreamData.h"
#include "..\..\..\third_party\Microsoft\FileSource\FileSourceFilter.h"
#include "PTSParser.h"
#include "DebugLog.h"

#pragma warning(disable : 4996)

CMediaStream::CMediaStream( CBaseFilter* pFilter, LPCSTR pHostName, BOOL bVirtalFile ):
	m_dwSkipHeaderBytes(0),
	m_pShareInfo(NULL),
	m_dwCircFileSize(0),
	m_pFilter(pFilter),
	m_bVirtualFile(bVirtalFile),
	m_pStream(NULL)
{
	m_largeFileSize = 900000000000L;  //ZQ 1:26:44 bug
	DWORD eStreamType = NONE_STREAM;
	if ( pHostName != NULL )
	{
		eStreamType = NET_STREAM;
	} else
		eStreamType = FILE_STREAM;

	if ( !m_bVirtualFile )
	{
		m_pStream = new CFileStream( this, eStreamType, pHostName );
	} else
	{
		m_pStream = new CVFileStream( this, eStreamType, pHostName );
	}
}

CMediaStream::~CMediaStream( )
{ 
	if ( m_pStream != NULL ) 
		delete m_pStream;
}


LONGLONG CMediaStream::GetTotalWrite(LPWSTR pwFileName)
{
	return ((CFileSourceFilter*)m_pFilter)->GetTotalWrite(pwFileName);
}

HRESULT CMediaStream::Open( WCHAR* lpwszFileName )
{
	if ( m_bVirtualFile )
		return E_FAIL;

	return ((CFileStream*)m_pStream)->Open( lpwszFileName );
}

void CMediaStream::Close( )
{
	if ( m_bVirtualFile )
		return;

	return ((CFileStream*)m_pStream)->Close();
}

WCHAR* CMediaStream::GetCurFile( )
{
	if ( m_bVirtualFile )
		return L"Virtual File";

	return ((CFileStream*)m_pStream)->GetCurFile();

}

HRESULT CMediaStream::OpenEx( int dwSerial, WCHAR* lpwszFileName )
{
	if ( !m_bVirtualFile )
		return E_FAIL;

	return ((CVFileStream*)m_pStream)->OpenEx( dwSerial, lpwszFileName );

}

void CMediaStream::CloseEx( int dwSerial )
{
	if ( !m_bVirtualFile )
		return;

	((CVFileStream*)m_pStream)->CloseEx( dwSerial );
}

void CMediaStream::SwitchFile( int dwSerial )
{
	if ( !m_bVirtualFile )
		return;

	((CVFileStream*)m_pStream)->SwitchFile( dwSerial );
}

WCHAR* CMediaStream::GetFileNameEx( int dwSerial )
{
	if ( !m_bVirtualFile )
		return L"";

	return ((CVFileStream*)m_pStream)->GetFileNameEx( dwSerial );
}

void CMediaStream::GetFileInf( int dwSerial, FILE_INF* pFileInf )
{
	if ( !m_bVirtualFile )
		return ;

	((CVFileStream*)m_pStream)->GetFileInf( dwSerial, pFileInf );
}

HRESULT CMediaStream::SetPointer(LONGLONG llPos)
{
	return m_pStream->SetPointer( llPos );
}

HRESULT CMediaStream::Read(PBYTE pbBuffer, DWORD dwBytesToRead, BOOL bAlign, LPDWORD pdwBytesRead)
{
	return m_pStream->Read(pbBuffer, dwBytesToRead, bAlign, pdwBytesRead);

}

LONGLONG CMediaStream::Size(LONGLONG *pSizeAvailable, LONGLONG *pOverwritten)
{
	return m_pStream->Size( pSizeAvailable, pOverwritten );
}

DWORD CMediaStream::Alignment()
{
	return m_pStream->Alignment();
}


HRESULT CMediaStream::GetMediaTime( LONGLONG* pllMediaTimeStart, LONGLONG* pllMediaTimeStop )
{
	return m_pStream->GetMediaTime( pllMediaTimeStart, pllMediaTimeStop );
}

void CMediaStream::Lock()
{
	m_csLock.Lock();
}

void CMediaStream::Unlock()
{
	m_csLock.Unlock();
}

//////////////////////////////////////////////////////////////////////////////////////////////////
CFileStream::CFileStream( CMediaStream *pMediaStream, DWORD dwStreamType, LPCSTR pHostName ) :
m_pStream(NULL),
m_pFileStreamData(NULL),
m_pNetStreamData(NULL),
m_eStreamType(dwStreamType)
{
	if ( m_eStreamType == FILE_STREAM )
	{
		m_pFileStreamData = new CFileStreamData( pMediaStream );
		m_pStream = m_pFileStreamData;
	}
	if ( m_eStreamType == NET_STREAM )
	{
		m_pNetStreamData = new CNetStreamData( pMediaStream, pHostName );
		m_pStream = m_pNetStreamData;
	}
}

CFileStream::~CFileStream()
{
	if ( m_eStreamType == FILE_STREAM )
		delete m_pFileStreamData;
	if ( m_eStreamType == NET_STREAM )
		delete m_pNetStreamData;
}

HRESULT CFileStream::Open( WCHAR* lpwszFileName )
{
	if ( m_eStreamType == FILE_STREAM )
		return m_pFileStreamData->Open( lpwszFileName );
	
	if ( m_eStreamType == NET_STREAM )
		return m_pNetStreamData->Open( lpwszFileName );

	return E_FAIL;
}

HRESULT  CFileStream::ReOpen( )
{
	if ( m_eStreamType == FILE_STREAM )
		return m_pFileStreamData->Open( NULL );
	
	if ( m_eStreamType == NET_STREAM )
		return m_pNetStreamData->Open( NULL );

		return E_FAIL;
}

WCHAR*  CFileStream::GetCurFile( )
{
	if ( m_eStreamType == FILE_STREAM )
		return m_pFileStreamData->GetCurFile();
		
	if ( m_eStreamType == NET_STREAM )
		return m_pNetStreamData->GetCurFile();

	return L"";
}

void CFileStream::Close( )
{
	if ( m_eStreamType == FILE_STREAM )
		 m_pFileStreamData->Close();
	
	if ( m_eStreamType == NET_STREAM )
		m_pNetStreamData->Close();
}


HRESULT CFileStream::SetPointer(LONGLONG llPos)
{
	return m_pStream->SetPointer( llPos );
}

HRESULT CFileStream::Read(PBYTE pbBuffer,
				DWORD dwBytesToRead,
                BOOL bAlign,
                LPDWORD pdwBytesRead)
{

	return m_pStream->Read( pbBuffer, dwBytesToRead, bAlign, pdwBytesRead );
}

LONGLONG CFileStream::Size(LONGLONG *pSizeAvailable, LONGLONG *pOverwritten)
{
	return m_pStream->Size( pSizeAvailable, pOverwritten );
}

DWORD CFileStream::Alignment()
{
	return m_pStream->Alignment();
}

void CFileStream::Lock()
{
	m_pStream->Lock();
}

void CFileStream::Unlock()
{
	m_pStream->Unlock();
}

HRESULT CFileStream::GetMediaTime( LONGLONG* pllMediaTimeStart, LONGLONG* pllMediaTimeStop )
{
	*pllMediaTimeStart = 0; 
	*pllMediaTimeStop = 0;
	return S_OK;
}

//////////////////////////////////////////////////////////////////////////////////////////////////

CVFileStream::CVFileStream(CMediaStream *pMediaStream, DWORD dwStreamType, LPCSTR pHostName ):
	m_dwGroupFileNum(0),
	m_dwWorkingFile(0),
	m_pPTSParser(NULL),
	m_llCurPos(0),
	m_nCurrentFile(-1),
	m_pHostName(NULL),
	m_eStreamType(dwStreamType),
	m_pMediaStream(pMediaStream)
{
	memset( m_GrpFileTbl, 0, sizeof(m_GrpFileTbl) );
	if ( pHostName != NULL )
	{
		m_pHostName = new char[strlen( pHostName )+1];
		strncpy( m_pHostName, pHostName, strlen(pHostName)+1 );
	} 
}

CVFileStream::~CVFileStream()
{
	if ( m_pPTSParser != NULL )
		delete m_pPTSParser;

	if ( m_pHostName != NULL )
		delete m_pHostName;

	int i;
	for ( i = 0; i<MAX_FILES_NUM; i++ )
	{
		if ( m_GrpFileTbl[i].pStreamData != NULL )
		{
			CloseEx( i );
			delete m_GrpFileTbl[i].pStreamData;
		}
	}
}

HRESULT CVFileStream::OpenEx( int dwSerial, WCHAR* lpwszFileName )
{
	HRESULT hr;

	if ( dwSerial >= MAX_FILES_NUM-1 )
		return E_FAIL;

	if ( m_GrpFileTbl[dwSerial].pStreamData == NULL )
		m_GrpFileTbl[dwSerial].pStreamData = new CFileStream( m_pMediaStream, m_eStreamType, m_pHostName );

	hr = ((CFileStream*)m_GrpFileTbl[dwSerial].pStreamData)->Open( lpwszFileName );
	if ( hr != S_OK ) return hr;

	CAsyncStream *pStream = (CAsyncStream*)m_GrpFileTbl[dwSerial].pStreamData; 

	//reset PTSParser at first file
	if ( dwSerial == 0 && m_pPTSParser != NULL  )
	{
		delete m_pPTSParser;
		m_pPTSParser = NULL;
	}
	if ( m_pPTSParser == NULL )
	{
		m_pPTSParser = new CPTSParser;
		HRESULT hr = m_pPTSParser->Init( pStream );
		if ( hr != S_OK )
		{
			delete m_pPTSParser;
			m_pPTSParser = NULL;
		}
	}
	if ( m_pPTSParser != NULL )
	{
		int i;
		LONGLONG llFirstPTS, llLastPTS, llSize;

		//read PTS, file size
		llFirstPTS = m_pPTSParser->ReadFileFirstPTS( pStream );
		llLastPTS = m_pPTSParser->ReadFileLastPTS( pStream );
		llSize = m_pPTSParser->ReadFileSize( pStream );
		DbgLog(( LOG_TRACE, 1, TEXT("ReadLastPTS  StartPTS:%s, LastPTS:%s, Size:%s."), 
			(LPCTSTR)Disp((CRefTime)PTS2MT((LONGLONG)llFirstPTS) ), (LPCTSTR)Disp((CRefTime)PTS2MT((LONGLONG)llLastPTS)),
			(LPCTSTR)Disp(llSize) )); 
		
		m_GrpFileTbl[dwSerial].llPTSStart = llFirstPTS;
		m_GrpFileTbl[dwSerial].llPTSStop = llLastPTS;
		m_GrpFileTbl[dwSerial].llFileSize = llSize;
		m_GrpFileTbl[dwSerial].dwState |= FILE_ACTIVE;
		m_nCurrentFile = dwSerial;
		if ( m_GrpFileTbl[dwSerial].llPTSStart > m_GrpFileTbl[dwSerial].llPTSStop )
		{
			m_GrpFileTbl[dwSerial].dwState = 0;
			DbgLog(( LOG_TRACE, 1, TEXT("Drop file, because PTS start goes ahead PTS end\r\n" ) ));
		}

		//update group files
		llLastPTS = 0;
		llSize = 0;
		for ( i = 0; i<MAX_FILES_NUM && m_GrpFileTbl[i].dwState; i++ )
		{
			LONGLONG llDur = m_GrpFileTbl[i].llPTSStop - m_GrpFileTbl[i].llPTSStart;
			if ( i+1 < MAX_FILES_NUM )
			{
				m_GrpFileTbl[i+1].llPTSOffset =    llDur + m_GrpFileTbl[i].llPTSOffset;
				m_GrpFileTbl[i+1].llLengthOffset = m_GrpFileTbl[i].llFileSize + m_GrpFileTbl[i].llLengthOffset;
				m_dwGroupFileNum = i+1;
			}
		}
	}

	return S_OK;

}

void CVFileStream::CloseEx( int dwSerial )
{
	if ( dwSerial >= MAX_FILES_NUM )
		return ;
	if ( m_GrpFileTbl[dwSerial].pStreamData == NULL )
		return ;
	((CFileStream*)m_GrpFileTbl[dwSerial].pStreamData)->Close();
	m_GrpFileTbl[dwSerial].dwState = 0;
	//m_GrpFileTbl[dwSerial].dwState &= ~FILE_OPEN;

}

void CVFileStream::SwitchFile( int dwSerial )
{
	return; //enable open files open
	if ( m_nCurrentFile == dwSerial )
		return ;

	if ( m_nCurrentFile >= 0 && m_nCurrentFile < MAX_FILES_NUM )
	{
		((CFileStream*)m_GrpFileTbl[m_nCurrentFile].pStreamData)->Close();
		m_GrpFileTbl[dwSerial].dwState &= ~FILE_OPEN;
		m_nCurrentFile = -1;
	}

	if ( dwSerial >= MAX_FILES_NUM || dwSerial < 0  )
		return;

	if ( m_GrpFileTbl[dwSerial].dwState & FILE_OPEN )
		return;

	HRESULT hr = ((CFileStream*)m_GrpFileTbl[dwSerial].pStreamData)->ReOpen();
	if ( hr == S_OK )
	{
		m_GrpFileTbl[dwSerial].dwState |= FILE_OPEN;
		m_nCurrentFile = dwSerial;
	}
}

WCHAR*  CVFileStream::GetFileNameEx( int dwSerial )
{
	if ( dwSerial >= MAX_FILES_NUM )
		return NULL;
	if ( m_GrpFileTbl[dwSerial].pStreamData == NULL )
		return NULL;

	return ((CFileStream*)m_GrpFileTbl[dwSerial].pStreamData)->GetCurFile();
}

void CVFileStream::GetFileInf( int dwSerial, FILE_INF* pFileInf )
{
	if ( dwSerial >= MAX_FILES_NUM )
		return;

	pFileInf->file_size = m_GrpFileTbl[dwSerial].llFileSize;
	pFileInf->timeline = m_GrpFileTbl[dwSerial].llPTSOffset;
	pFileInf->pts_start = m_GrpFileTbl[dwSerial].llPTSStart;
	pFileInf->pts_stop = m_GrpFileTbl[dwSerial].llPTSStop;
}

HRESULT CVFileStream::SetPointer(LONGLONG llPos)
{
	int i;
	for ( i = 0; i<MAX_FILES_NUM && m_GrpFileTbl[i].dwState; i++ )
	{
		if ( llPos >= m_GrpFileTbl[i].llLengthOffset && 
			 llPos < m_GrpFileTbl[i+1].llLengthOffset )
		{
			m_dwWorkingFile = i;
			m_llCurPos = llPos;
			LONGLONG llPosInFile = llPos - m_GrpFileTbl[i].llLengthOffset;
			SwitchFile( i );
			HRESULT hr = ((CFileStream*)m_GrpFileTbl[i].pStreamData)->SetPointer( llPosInFile );
			return hr;
		}
	}

	DbgLog(( LOG_TRACE, 1, TEXT("SetPoint out of range pos:%s, size:%s\r\n" ), (LPCTSTR)Disp(llPos), 
			  (LPCTSTR)Disp(m_GrpFileTbl[m_dwGroupFileNum].llLengthOffset)  ));
	return S_FALSE;

}
HRESULT CVFileStream::Read(PBYTE pbBuffer,
				DWORD dwBytesToRead,
                BOOL bAlign,
                LPDWORD pdwBytesRead)
{
	HRESULT hr;
	if ( dwBytesToRead == 0 )
	{
		*pdwBytesRead = 0; //hit end of file
		return S_OK;
	}

	if ( m_llCurPos >= m_GrpFileTbl[m_dwWorkingFile+1].llLengthOffset )
	{
		if ( m_GrpFileTbl[m_dwWorkingFile+1].dwState )
		{
			m_dwWorkingFile++;
			SwitchFile(m_dwWorkingFile);
			hr = ((CFileStream*)m_GrpFileTbl[m_dwWorkingFile].pStreamData)->SetPointer( m_llCurPos-m_GrpFileTbl[m_dwWorkingFile+1].llLengthOffset );
			if ( hr != S_OK )
				return hr;
		} else
		{
			*pdwBytesRead = 0; //hit end of file
			return S_OK;
		}
	}

	if ( m_llCurPos+dwBytesToRead  > m_GrpFileTbl[m_dwWorkingFile+1].llLengthOffset )
		dwBytesToRead = (DWORD)(m_GrpFileTbl[m_dwWorkingFile+1].llLengthOffset - m_llCurPos);

	ASSERT( m_llCurPos +dwBytesToRead <= m_GrpFileTbl[m_dwWorkingFile+1].llLengthOffset );
	SwitchFile(m_dwWorkingFile);
	hr = ((CFileStream*)m_GrpFileTbl[m_dwWorkingFile].pStreamData)->Read( pbBuffer, dwBytesToRead, bAlign, pdwBytesRead );
	if ( hr != S_OK )
		return hr;

	m_llCurPos += *pdwBytesRead;
	ASSERT( m_llCurPos <= m_GrpFileTbl[m_dwWorkingFile+1].llLengthOffset );

	return hr;

}

LONGLONG CVFileStream::Size(LONGLONG *pSizeAvailable, LONGLONG *pOverwritten)
{
	if ( pSizeAvailable != NULL )
		*pSizeAvailable = m_GrpFileTbl[m_dwGroupFileNum].llLengthOffset;
	if ( pOverwritten  != NULL )
		*pOverwritten = 0;
	return m_GrpFileTbl[m_dwGroupFileNum].llLengthOffset;

}
DWORD CVFileStream::Alignment()
{
	if ( m_GrpFileTbl[0].pStreamData )
	{
		if ( m_eStreamType == FILE_STREAM )
			return ((CFileStreamData*)m_GrpFileTbl[0].pStreamData)->Alignment();
		if ( m_eStreamType == NET_STREAM )
			return ((CNetStreamData*)m_GrpFileTbl[0].pStreamData)->Alignment();
	}

	return 1;
}

void CVFileStream::Lock()
{
	m_csLock.Lock();
}
void CVFileStream::Unlock()
{
	m_csLock.Unlock();
}

HRESULT CVFileStream::GetMediaTime( LONGLONG* pllMediaTimeStart, LONGLONG* pllMediaTimeStop )
{
	*pllMediaTimeStart = m_GrpFileTbl[m_dwWorkingFile].llPTSOffset;
	*pllMediaTimeStop  = *pllMediaTimeStart + m_GrpFileTbl[m_dwWorkingFile].llPTSStart;

	return S_OK;
}

