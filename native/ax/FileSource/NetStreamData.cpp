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
#include "NetStreamData.h"
#include "DebugLog.h"

#pragma warning(disable : 4996)

const int RED_TIMEOUT = 30000; // 30 second timeout on the sockets

typedef struct addrinfo 
{  
	int ai_flags;  
	int ai_family;  
	int ai_socktype;  
	int ai_protocol;  
	size_t ai_addrlen;  
	char* ai_canonname;  
	struct sockaddr* ai_addr;  
	struct addrinfo* ai_next;
} addrinfo;

HRESULT CNetStreamData::Open( WCHAR* lpwszFileName )
{
	// reOpen if lpwszFileName == NULL
	if ( lpwszFileName != NULL && lpwszFileName[0] )
	{
		int cch = lstrlenW(lpwszFileName) + 1;
		if ( m_pFileName != NULL )
			delete m_pFileName;

		m_pFileName = new WCHAR[cch];
		if (!m_pFileName)
      		return E_OUTOFMEMORY;
	   	CopyMemory(m_pFileName, lpwszFileName, cch*sizeof(WCHAR));
	}

	return OpenConnection( );

}

HRESULT CNetStreamData::ReOpenConnection()
{
	if (!m_pFileName) return E_POINTER;
	CloseConnection();
	HRESULT hr = OpenConnection();
	return hr;
}
HRESULT CNetStreamData::OpenConnection( )
{
  
	DbgLog((LOG_TRACE, 2, TEXT("OpenConnection(%S) IN"), m_pFileName));
	WSADATA wsaData;
	if (WSAStartup(0x202,&wsaData) == SOCKET_ERROR) {
		DbgLog((LOG_TRACE, 2, TEXT("WSAStartup failed with error %d"), WSAGetLastError()));
		return E_FAIL;
	}
	DbgLog((LOG_TRACE, 2, TEXT("WSAStartup succeeded")));

	m_sd = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
	if (m_sd == INVALID_SOCKET)
	{
		WSACleanup();
		DbgLog((LOG_TRACE, 2, TEXT("Invalid socket descriptor")));
		return E_FAIL;
	}

	DbgLog((LOG_TRACE, 2, TEXT("socket was created %d"), (DWORD) m_sd));
	// Set the socket timeout option. If a timeout occurs, then it'll be just
	// like the server closed the socket.
	if (setsockopt(m_sd, SOL_SOCKET, SO_RCVTIMEO, (char*)&RED_TIMEOUT, sizeof(RED_TIMEOUT)) == SOCKET_ERROR)
	{
		DbgLog((LOG_TRACE, 2, "Error setting socket timeout, error: %d", WSAGetLastError()));
		return E_FAIL;
	}
	DbgLog((LOG_TRACE, 2, TEXT("set socket timeout")));
	// Set the socket linger option, this makes sure the QUIT message gets received
	// by the server before the TCP reset message does.
	LINGER lingonberry;
	lingonberry.l_onoff = TRUE;
	lingonberry.l_linger = 1;
	if (setsockopt(m_sd, SOL_SOCKET, SO_LINGER, (char*)&lingonberry, sizeof(LINGER)) == SOCKET_ERROR)
	{
		DbgLog((LOG_TRACE, 2, "Error setting socket close linger, error: %d", WSAGetLastError()));
		return E_FAIL;
	}
	DbgLog((LOG_TRACE, 2, TEXT("set socket linger on close")));

	struct sockaddr address;
	struct sockaddr_in* inetAddress;
	inetAddress = (struct sockaddr_in*) ( (void *) &address); // cast it to IPV4 addressing
	inetAddress->sin_family = PF_INET;
	inetAddress->sin_port = htons(7818);

	struct hostent* hostptr;
	hostptr = gethostbyname(m_pHostname);
	if (!hostptr)
	{
		DbgLog((LOG_TRACE, 2, TEXT("gethostbyname error 0x%x"), WSAGetLastError()));
		return E_FAIL;
	}
	DbgLog((LOG_TRACE, 2, TEXT("resolved host address")));
    memcpy(&inetAddress->sin_addr.s_addr, hostptr->h_addr, hostptr->h_length );
 
	DbgLog((LOG_TRACE, 2, TEXT("about to connect")));
    if (connect(m_sd, (struct sockaddr *) ((void *)&address), sizeof(address)) < 0)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Socket connection error host=%s"), m_pHostname));
		return E_FAIL;
	}


	DbgLog((LOG_TRACE, 2, TEXT("connect succeeded")));
	char data[512];
	// Check if we need to use the Unicode version
	size_t wlen = wcslen(m_pFileName);
	int needUni = 0;
	for (int i = 0; i < wlen; i++)
	{
		if (m_pFileName[i] > 0x7F)
		{
			needUni = 1;
			break;
		}
	}

	if (needUni)
		strcpy(data, "OPENW ");
	else
		strcpy(data, "OPEN ");
	size_t dataIdx = strlen(data);
	for (int i = 0; i < wlen; i++, dataIdx+=(needUni?2:1))
	{
		if (needUni)
		{
			data[dataIdx] = ((m_pFileName[i] & 0xFF00) >> 8);
			data[dataIdx + 1] = (m_pFileName[i] & 0xFF);
		}
		else
			data[dataIdx] = m_pFileName[i] & 0xFF;
	}
	data[dataIdx++] = '\r';
	data[dataIdx++] = '\n';
	//sprintf(data, "OPENW %S\r\n", m_pFileName);
	//int dataSize = strlen(data);
	if (send(m_sd, data, dataIdx, 0) < dataIdx)
	{
		DbgLog((LOG_TRACE, 2, TEXT("socket write failed")));
		return E_FAIL;
	}

	DbgLog((LOG_TRACE, 2, TEXT("send succeeded")));
	int res;
	if ((res = SockReadLine(m_sd, data, sizeof(data))) < 0 || strcmp(data, "OK"))
	{
		DbgLog((LOG_TRACE, 2, TEXT("socket readline failed res=%d data=%s"), res, data));
		return E_FAIL;
	}

	DbgLog((LOG_TRACE, 2, TEXT("OpenConnection() OUT")));

	//m_Stream.Init(NULL, m_sd);

	return S_OK;
}

void CNetStreamData::CloseConnection()
{
	DbgLog((LOG_TRACE, 3, TEXT("CloseConnection() IN")));
	if (m_sd != INVALID_SOCKET)
	{
		char* data = "QUIT\r\n";
		size_t dataSize = strlen(data);
		send(m_sd, data, dataSize, 0);
		closesocket(m_sd);
		m_sd = INVALID_SOCKET;
		WSACleanup();
	}
	DbgLog((LOG_TRACE, 3, TEXT("CloseConnection() OUT")));
}

// Reads data from a socket into the array until the "\r\n" character
// sequence is encountered. The returned value is the
// number of bytes read or SOCKET_ERROR if an error occurs, 0
// if the socket has been closed. The number of bytes will be
// 2 more than the actual string length because the \r\n chars
// are removed before this function returns.
int CNetStreamData::SockReadLine(SOCKET sd, char* buffer, int bufLen)
{
	int currRecv;
	int newlineIndex = 0;
	bool endFound = false;
	int offset = 0;
	while ( !endFound && offset < bufLen )  //ZQ fixs buffer overrun
	{
		currRecv = recv(sd, buffer + offset, bufLen-offset, MSG_PEEK);
		if (currRecv == SOCKET_ERROR)
		{
			return SOCKET_ERROR;
		}

		if (currRecv == 0)
		{
			return endFound ? 0 : SOCKET_ERROR;
		}

		// Scan the buffer for "\r\n" termination
		for (int i = 0; i < (currRecv + offset); i++)
		{
			if (buffer[i] == '\r')
			{
				if (buffer[i + 1] == '\n')
				{
					newlineIndex = i + 1;
					endFound = true;
					break;
				}
			}
		}
		if (!endFound)
		{
			currRecv = recv(sd, buffer + offset, currRecv, 0);
			if (currRecv == SOCKET_ERROR)
			{
				return SOCKET_ERROR;
			}
			if (currRecv == 0)
			{
				return endFound ? 0 : SOCKET_ERROR;
			}
			offset += currRecv;
		}
	}

	if ( endFound )
	{
		currRecv = recv(sd, buffer + offset, (newlineIndex + 1) - offset, 0);
		buffer[newlineIndex - 1] = '\0';
	} 
	return currRecv;
}


void CNetStreamData::Close()
{
	CloseConnection();
}

WCHAR* CNetStreamData::GetCurFile()
{
	return (WCHAR*)m_pFileName;
}

HRESULT CNetStreamData::SetPointer(LONGLONG llPos)
{
	CAutoLock lck(&m_pMediaStream->m_csLock);
	DbgLog((LOG_TRACE, 5, TEXT("Async SetPointer(%s) pos=%s"),
		(LPCSTR)Disp(llPos, CDISP_DEC), (LPCSTR)Disp(m_llPosition, CDISP_DEC)));
	llPos += m_pMediaStream->m_dwSkipHeaderBytes;

	if (llPos >= 0 && m_pMediaStream->m_pShareInfo)
	{
		m_llPosition = llPos;
		DbgLog((LOG_TRACE, 2, TEXT("Async1 partway SetPointer(%s) filePos=%s"),
			(LPCSTR)Disp(llPos, CDISP_DEC), (LPCSTR)Disp(m_llPosition, CDISP_DEC)));
		return S_OK;
	}
	char data[512];
	sprintf(data, "SIZE\r\n");
	size_t dataSize = strlen(data);
	if (send(m_sd, data, dataSize, 0) < dataSize)
	{
		DbgLog((LOG_TRACE, 2, TEXT("socket write failed, reopening...")));
		HRESULT hr = ReOpenConnection();
		if (FAILED(hr))
			return hr;
		if (send(m_sd, data, dataSize, 0) < dataSize)
		{
			DbgLog((LOG_TRACE, 2, TEXT("socket write failed")));
			return S_FALSE;
		}
	}

	int nbytes = SockReadLine(m_sd, data, sizeof(data));
	if (nbytes < 0)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Error reading from network, reopening")));
		HRESULT hr = ReOpenConnection();
		if (FAILED(hr))
			return hr;
		sprintf(data, "SIZE\r\n");
		if (send(m_sd, data, dataSize, 0) < dataSize)
		{
			DbgLog((LOG_TRACE, 2, TEXT("socket write failed")));
			return S_FALSE;
		}
		nbytes = SockReadLine(m_sd, data, sizeof(data));
		if (nbytes < 0)
		{
			DbgLog((LOG_TRACE, 2, TEXT("Error reading from network")));
			return S_FALSE;
		}
	}
	char* spacePtr = strchr(data, ' ');
	if (!spacePtr)
	{
		DbgLog((LOG_TRACE, 2, TEXT("No space in returned data of %s"), data));
		return S_FALSE;
	}
	*spacePtr = '\0';
	LONGLONG availSize = _atoi64(data);
	if (llPos >= 0 && (llPos < availSize || m_pMediaStream->m_pShareInfo))
	{
		m_llPosition = llPos;
		DbgLog((LOG_TRACE, 5, TEXT("Async partway SetPointer(%s) filePos=%s"),
			(LPCSTR)Disp(llPos, CDISP_DEC), (LPCSTR)Disp(m_llPosition, CDISP_DEC)));
		return S_OK;
	}
	else
	{
		DbgLog((LOG_TRACE, 2, TEXT("Async SetPointer FAILED2")));
		return S_FALSE;
	}
		
}

HRESULT CNetStreamData::Read(PBYTE pbBuffer, DWORD dwBytesToRead, BOOL bAlign, LPDWORD pdwBytesRead)
{
	CAutoLock lck(&m_pMediaStream->m_csLock);
	DbgLog((LOG_TRACE, 5, TEXT("Async Read(%d) pos=%s"), dwBytesToRead, (LPCSTR)Disp(m_llPosition, CDISP_DEC)));
	char data[512];
	LARGE_INTEGER li;
	li.QuadPart = m_llPosition;
	TCHAR  temp[20];
	int pos=20;
	temp[--pos] = 0;
	int digit;
	*pdwBytesRead = 0;
	// always output at least one digit
	do {
	// Get the rightmost digit - we only need the low word
		/*
		 * NOTE: THIS WAS A BUG IN MS CODE, FOR Disp they were doing
		 * lowPart % 10 for each digit they printed. But that only did
		 * the modulus on the lower 32 bits, and not the whole number, 
		 * resulting in errors. Now I modulus on the whole 64-bit number
		 * which will give the correct digit result.
		 */
		digit = (int)(li.QuadPart % 10); // was lowPart
		li.QuadPart /= 10;
		temp[--pos] = (TCHAR) digit+L'0';
	} while (li.QuadPart);
	sprintf(data, "READ %s %d\r\n", temp+pos, dwBytesToRead);
	size_t dataSize = strlen(data);
	if (send(m_sd, data, dataSize, 0) < dataSize)
	{
		DbgLog((LOG_TRACE, 2, TEXT("socket write failed, reopening connection...")));
		// Try to do it again...
		HRESULT hr = ReOpenConnection();
		if (FAILED(hr))
			return hr;
		if (send(m_sd, data, dataSize, 0) < dataSize)
		{
			DbgLog((LOG_TRACE, 2, TEXT("socket write failed, aborting.")));
			return E_FAIL;
		}
	}

	int nbytes;
	PBYTE pOriginalBuffer = pbBuffer;
	DWORD originaldwBytesToRead = dwBytesToRead;
	*pdwBytesRead = 0;
	nbytes = recv(m_sd, (char*)pbBuffer, dwBytesToRead, 0);
	while (nbytes >= 0 && dwBytesToRead > 0)
	{
		DbgLog((LOG_TRACE, 6, TEXT("Read %d bytes from network"), nbytes));
		dwBytesToRead -= nbytes;
		pbBuffer += nbytes;
		*pdwBytesRead += nbytes;
		if (dwBytesToRead > 0)
			nbytes = recv(m_sd, (char*)pbBuffer, dwBytesToRead, 0);
	}
	if (nbytes < 0)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Error reading from network, reopening connection...")));
		HRESULT hr = ReOpenConnection();
		if (FAILED(hr))
			return hr;
		if (send(m_sd, data, dataSize, 0) < dataSize)
		{
			DbgLog((LOG_TRACE, 2, TEXT("socket write failed, aborting.")));
			return E_FAIL;
		}
		*pdwBytesRead = 0;
		pbBuffer = pOriginalBuffer;
		dwBytesToRead = originaldwBytesToRead;
		nbytes = recv(m_sd, (char*)pbBuffer, dwBytesToRead, 0);
		while (nbytes >= 0 && dwBytesToRead > 0)
		{
			DbgLog((LOG_TRACE, 6, TEXT("Read %d bytes from network"), nbytes));
			dwBytesToRead -= nbytes;
			pbBuffer += nbytes;
			*pdwBytesRead += nbytes;
			if (dwBytesToRead > 0)
				nbytes = recv(m_sd, (char*)pbBuffer, dwBytesToRead, 0);
		}
		if (nbytes < 0)
		{
			DbgLog((LOG_TRACE, 2, TEXT("Error reading from network, aborting.")));
			return E_FAIL;
		}
	}
	DbgLog((LOG_TRACE, 5, TEXT("Read %d bytes from network"), *pdwBytesRead));
	m_llPosition += *pdwBytesRead;
	return S_OK;

}

LONGLONG CNetStreamData::Size( LONGLONG *pSizeAvailable , LONGLONG *pOverwritten )
{
	CAutoLock lck(&m_pMediaStream->m_csLock);
	DbgLog((LOG_TRACE, 5, TEXT("Async Size called")));
	LONGLONG SizeAvailable;
	char data[512];
	sprintf(data, "SIZE\r\n");
	size_t dataSize = strlen(data);
	if (send(m_sd, data, dataSize, 0) < dataSize)
	{
		DbgLog((LOG_TRACE, 3, TEXT("socket write failed, reopening...")));
		HRESULT hr = ReOpenConnection();
		if (FAILED(hr))
			return hr;
		if (send(m_sd, data, dataSize, 0) < dataSize)
		{
			DbgLog((LOG_TRACE, 2, TEXT("socket write failed")));
			return -1;
		}
	}

	int nbytes = SockReadLine(m_sd, data, sizeof(data));
	if (nbytes < 0)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Error reading from network, reopening")));
		HRESULT hr = ReOpenConnection();
		if (FAILED(hr))
			return hr;
		sprintf(data, "SIZE\r\n");
		if (send(m_sd, data, dataSize, 0) < dataSize)
		{
			DbgLog((LOG_TRACE, 2, TEXT("socket write failed")));
			return -1;
		}
		nbytes = SockReadLine(m_sd, data, sizeof(data));
		if (nbytes < 0)
		{
			DbgLog((LOG_TRACE, 2, TEXT("Error reading from network")));
			return -1;
		}
	}
	char* spacePtr = strchr(data, ' ');
	if (!spacePtr)
	{
		DbgLog((LOG_TRACE, 2, TEXT("No space in returned data of %s"), data));
		return -1;
	}
	*spacePtr = '\0';
	SizeAvailable = _atoi64(data) - m_pMediaStream->m_dwSkipHeaderBytes;
	LONGLONG totalSize = _atoi64(spacePtr + 1) - m_pMediaStream->m_dwSkipHeaderBytes;
	DbgLog((LOG_TRACE, 5, TEXT("Sizes of %s and %s"), (LPCSTR)Disp(SizeAvailable, CDISP_DEC),
		(LPCSTR)Disp(totalSize, CDISP_DEC)));
	if (pOverwritten)
	{
		*pOverwritten = 0;
		if (m_pMediaStream->m_pShareInfo && m_pMediaStream->m_dwCircFileSize && SizeAvailable > m_pMediaStream->m_dwCircFileSize )
		{
			*pOverwritten = SizeAvailable - m_pMediaStream->m_dwCircFileSize;
		}
	}
	if ( pSizeAvailable != NULL ) 
		*pSizeAvailable = SizeAvailable;
	return totalSize;

}

DWORD CNetStreamData::Alignment()
{
	return 1;
}

HRESULT  CNetStreamData::GetMediaTime( LONGLONG* pllMediaTimeStart, LONGLONG* pllMediaTimeStop )
{
	*pllMediaTimeStart = 0;
	*pllMediaTimeStop = 0;
	return S_OK;
}
//void CNetStreamData::Lock()
//{
//	m_csLock.Lock();
//}
//
//void CNetStreamData::Unlock()
//{
//	m_csLock.Unlock();
//}
