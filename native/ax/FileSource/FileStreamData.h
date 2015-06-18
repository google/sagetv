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
#ifndef _FILE_STREAM_DATA_H_
#define _FILE_STREAM_DATA_H_

#include "StreamData.h"

void _flog( int type, int level, const char* cstr, ... );
#ifdef _DEBUG
	#ifdef ENABLE_SAGETV_LOG	
		#undef  DbgLog
		#define flog(x)    _flog x
		#define DbgLog(x)   flog( x )
	#endif
#endif

class CMediaStream;
class CFileStreamData : public CAsyncStream
{
public:
	CFileStreamData( CMediaStream* pMediaStream ) :  
	  m_pMediaStream( pMediaStream ),
	  m_hFile( NULL ),
	  m_llPosition( 0 ),
	  m_pFileName( NULL ) { };
	~CFileStreamData( ) { Close(); if ( m_pFileName != NULL ) delete m_pFileName; };

	HRESULT Open( WCHAR* lpwszFileName );
	void Close();
	WCHAR*  GetCurFile();
    HRESULT SetPointer(LONGLONG llPos);
    HRESULT Read(PBYTE pbBuffer,
                 DWORD dwBytesToRead,
                 BOOL bAlign,
                 LPDWORD pdwBytesRead);
    LONGLONG Size(LONGLONG *pSizeAvailable = NULL, LONGLONG *pOverwritten = NULL);
    DWORD    Alignment();
	HRESULT  GetMediaTime( LONGLONG* pllMediaTimeStart, LONGLONG* pllMediaTimeStop );
	void  Lock()   { m_csLock.Lock();  };
	void  Unlock() { m_csLock.Unlock();};

protected:
	HANDLE	  m_hFile;
	WCHAR*	  m_pFileName;
	LONGLONG  m_llPosition;
	CMediaStream* m_pMediaStream;
	CCritSec  m_csLock;
};

#endif
