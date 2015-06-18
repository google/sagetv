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
#include "StreamData.h"
#include "FileStreamData.h"
#include "DebugLog.h"


HRESULT CFileStreamData::Open( WCHAR* lpwszFileName )
{
	//reOpen if lpwszFileName == NULL
	if ( lpwszFileName != NULL && lpwszFileName[0] )
	{
		DbgLog((LOG_TRACE, 2, TEXT("Open File:%S "), lpwszFileName ));
		if ( m_pFileName != NULL )
			delete m_pFileName;

		int cch = lstrlenW(lpwszFileName) + 1;
		m_pFileName = new WCHAR[cch];
		if (!m_pFileName)
      		return E_OUTOFMEMORY;

   		CopyMemory(m_pFileName, lpwszFileName, cch*sizeof(WCHAR));
	}

	m_hFile = CreateFileW(m_pFileName, GENERIC_READ,
		FILE_SHARE_WRITE | FILE_SHARE_READ, NULL,
		OPEN_EXISTING, 0, NULL);
	if ( m_hFile == INVALID_HANDLE_VALUE) 
	{
		return E_FAIL;
	}

	return S_OK;
}

void CFileStreamData::Close( )
{
	if ( m_hFile != NULL )
		CloseHandle( m_hFile );
	 m_hFile = NULL;
}

WCHAR* CFileStreamData::GetCurFile( )
{
	return m_pFileName;
}

HRESULT CFileStreamData::SetPointer(LONGLONG llPos)
{
	DWORD fileSizeHi, fileSizeLo;
	fileSizeLo = GetFileSize(m_hFile, &fileSizeHi);
	if (fileSizeLo == INVALID_FILE_SIZE)
	{
		if ( GetLastError() != NO_ERROR )
			return S_FALSE;
	}
	LONGLONG fileSize = fileSizeHi;
	fileSize = fileSize << 32;
	fileSize += fileSizeLo;

	llPos += m_pMediaStream->m_dwSkipHeaderBytes;
	m_llPosition = llPos;

	DbgLog((LOG_TRACE, 5, TEXT("Async partway SetPointer(%s) fileSize=%s"),
		(LPCSTR)Disp(llPos, CDISP_DEC), (LPCSTR)Disp(fileSize, CDISP_DEC)));
	if (llPos >= 0 && (llPos < fileSize || m_pMediaStream->m_pShareInfo))
	{
		LONG filePosLo, filePosHi;
		if (m_pMediaStream->m_dwCircFileSize > 0)
		{
			llPos %= m_pMediaStream->m_dwCircFileSize;
		}

		filePosHi = (LONG) (llPos >> 32);
		filePosLo = (LONG) (llPos & 0xFFFFFFFF);
		
		filePosLo = SetFilePointer(m_hFile, filePosLo, &filePosHi, FILE_BEGIN);
		// NOTE: We let these through because it tries to set the file
		// larger than it is when you open up a live one and the allocators
		// will fail if you don't let it through
		if ( m_pMediaStream->m_pShareInfo || filePosLo != INVALID_FILE_SIZE)
			return S_OK;
	}
	
	DbgLog((LOG_TRACE, 5, TEXT("Async SetPointer FAILED")));
	return S_FALSE;
}

HRESULT CFileStreamData::Read(PBYTE pbBuffer, DWORD dwBytesToRead, BOOL bAlign, LPDWORD pdwBytesRead)
{
	CAutoLock lck(&m_pMediaStream->m_csLock);
	DbgLog((LOG_TRACE, 5, TEXT("Async Read(%d) pos=%s"), dwBytesToRead, (LPCSTR)Disp(m_llPosition, CDISP_DEC)));

	*pdwBytesRead = 0;
	if (dwBytesToRead <= 0) return S_FALSE;
	DWORD fileSizeHi, fileSizeLo;
	int numTries = NUM_LOOKS_FOR_DATA;
	if (m_pMediaStream->m_pShareInfo && m_pMediaStream->m_dwCircFileSize)
	{
		LONGLONG totalWritten = m_pMediaStream->GetTotalWrite( m_pFileName );
		if (totalWritten < 0)
		{
			DbgLog((LOG_TRACE, 2, TEXT("Circular file writing has stopped")));
			return VFW_E_FILE_TOO_SHORT;
		}
		fileSizeHi = (DWORD) (totalWritten >> 32);
		fileSizeLo = (DWORD) (totalWritten & 0xFFFFFFFF);
	}
	else
	{
		fileSizeLo = GetFileSize(m_hFile, &fileSizeHi);
	}

	while (((((LONGLONG)fileSizeLo) | (((LONGLONG)fileSizeHi) << 32)) - m_llPosition) < dwBytesToRead &&
		m_pMediaStream->m_pShareInfo && numTries > 0)
	{
		DbgLog((LOG_TRACE, 6, TEXT("Async Read PAUSING for data share=%d fileSize=%d redReq=%d"),
			m_pMediaStream->m_pShareInfo, fileSizeLo, (int)(m_llPosition + dwBytesToRead)));
		// There's not enough data in the file yet, but there
		// should be VERY shortly
		Sleep(WAIT_BETWEEN_LOOKS);
		fileSizeLo = GetFileSize(m_hFile, &fileSizeHi);
		numTries--;
		if (m_pMediaStream->m_pShareInfo && m_pMediaStream->m_dwCircFileSize)
		{
			LONGLONG totalWritten = m_pMediaStream->GetTotalWrite(m_pFileName);
			if (totalWritten < 0)
			{
				DbgLog((LOG_TRACE, 2, TEXT("Circular file writing has stopped")));
				return VFW_E_FILE_TOO_SHORT;
			}
			fileSizeHi = (DWORD) (totalWritten >> 32);
			fileSizeLo = (DWORD) (totalWritten & 0xFFFFFFFF);
		}
		else
		{
			fileSizeLo = GetFileSize(m_hFile, &fileSizeHi);
		}
	}

    //ZQ
	if (m_pMediaStream->m_pShareInfo && m_pMediaStream->m_dwCircFileSize && numTries < 0 )
	{
		DbgLog((LOG_TRACE, 2, TEXT("Circular file waiting timeout")));
		return VFW_E_FILE_TOO_SHORT;
	}

	DWORD readFromFront = 0;
	if (m_pMediaStream->m_dwCircFileSize > 0 && (m_llPosition % m_pMediaStream->m_dwCircFileSize) + dwBytesToRead > m_pMediaStream->m_dwCircFileSize)
	{
		readFromFront = dwBytesToRead - ((DWORD) (m_pMediaStream->m_dwCircFileSize - (m_llPosition % m_pMediaStream->m_dwCircFileSize)));
		dwBytesToRead -= readFromFront;
	}

	if (dwBytesToRead > 0)
	{
		if (!ReadFile(m_hFile, (PVOID)pbBuffer, dwBytesToRead,
			pdwBytesRead, NULL))
		{
			DbgLog((LOG_TRACE, 2, TEXT("Read FAILED")));
			return S_FALSE;
		}
		m_llPosition += *pdwBytesRead;
	}
	
	if (readFromFront > 0)
	{
		if (SetFilePointer(m_hFile, 0, NULL, FILE_BEGIN) == INVALID_FILE_SIZE)
		{
			DbgLog((LOG_TRACE, 2, TEXT("Failed seeking back to front of circ file")));
			return S_FALSE;
		}
		DWORD extraRead = 0;
		if (!ReadFile(m_hFile, (PVOID)(pbBuffer + dwBytesToRead), readFromFront, &extraRead, NULL))
		{
			DbgLog((LOG_TRACE, 2, TEXT("REad FAILED")));
			return S_FALSE;
		}
		*pdwBytesRead = *pdwBytesRead + extraRead;
		m_llPosition += extraRead;
	}
	else if (m_pMediaStream->m_dwCircFileSize > 0 && m_llPosition % m_pMediaStream->m_dwCircFileSize == 0)
	{
		if (SetFilePointer(m_hFile, 0, NULL, FILE_BEGIN) == INVALID_FILE_SIZE)
		{
			DbgLog((LOG_TRACE, 2, TEXT("Failed seeking back to front of circ file")));
			return S_FALSE;
		}
		//m_llPosition = 0;
	}
	DbgLog((LOG_TRACE, 5, TEXT("Completed Async Read(%d)"), *pdwBytesRead));
	return S_OK;

}

LONGLONG CFileStreamData::Size( LONGLONG *pSizeAvailable, LONGLONG *pOverwritten )
{
	DbgLog((LOG_TRACE, 5, TEXT("Async Size called")));
	DWORD fileSizeHi, fileSizeLo;
	LONGLONG SizeAvailable;
	fileSizeLo = GetFileSize(m_hFile, &fileSizeHi);
	if (fileSizeLo == INVALID_FILE_SIZE)
	{
		if ( GetLastError() != NO_ERROR )
			return -1;
	}

	int numTries = NUM_LOOKS_FOR_DATA;
	while (fileSizeLo < MIN_FILE_SIZE && fileSizeHi == 0 && m_pMediaStream->m_pShareInfo && numTries > 0)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Async Size PAUSING for data fileSize=%d"), fileSizeLo));
		// There's no data in the file yet, but there
		// should be VERY shortly
		Sleep(WAIT_BETWEEN_LOOKS);
		fileSizeLo = GetFileSize(m_hFile, &fileSizeHi);
		numTries--;
	}
	if (m_pMediaStream->m_pShareInfo && m_pMediaStream->m_dwCircFileSize)
	{
		LONGLONG totalWritten = m_pMediaStream->GetTotalWrite(m_pFileName);
		if (totalWritten < 0)
		{
			DbgLog((LOG_TRACE, 2, TEXT("Circular file writing has stopped")));
			return VFW_E_FILE_TOO_SHORT;
		}
		fileSizeHi = (DWORD) (totalWritten >> 32);
		fileSizeLo = (DWORD) (totalWritten & 0xFFFFFFFF);
	}
	else
	{
		fileSizeLo = GetFileSize(m_hFile, &fileSizeHi);
	}

	SizeAvailable = fileSizeHi;
	SizeAvailable = (SizeAvailable) << 32;
	SizeAvailable += fileSizeLo;
	SizeAvailable -= m_pMediaStream->m_dwSkipHeaderBytes;

	if ( pOverwritten != NULL )
	{
		*pOverwritten = 0;
		if (m_pMediaStream->m_pShareInfo && m_pMediaStream->m_dwCircFileSize && SizeAvailable > m_pMediaStream->m_dwCircFileSize)
		{
			*pOverwritten = SizeAvailable - m_pMediaStream->m_dwCircFileSize;
		}
	}
	
	// No data in the file yet.
	if (m_pMediaStream->m_pShareInfo && SizeAvailable == 0)
		SizeAvailable = 1000000; // 100MB

	if ( pSizeAvailable != NULL )
		*pSizeAvailable = SizeAvailable;

	DbgLog((LOG_TRACE, 5, TEXT("Async Size=%d"), (LONG)SizeAvailable));

	if (m_pMediaStream->m_pShareInfo)
	{
		return m_pMediaStream->m_largeFileSize; // 1 TB, bigger than anything we'd ever deal with is the intent here
	}
	else
		return SizeAvailable;  

}

DWORD CFileStreamData::Alignment()
{
	return 1;
}

HRESULT  CFileStreamData::GetMediaTime( LONGLONG* pllMediaTimeStart, LONGLONG* pllMediaTimeStop )
{
	*pllMediaTimeStart = 0;
	*pllMediaTimeStop = 0;
	return S_OK;
}

//void CFileStreamData::Lock()
//{
//	m_csLock.Lock();
//}
//
//void CFileStreamData::Unlock()
//{
//	m_csLock.Unlock();
//}
