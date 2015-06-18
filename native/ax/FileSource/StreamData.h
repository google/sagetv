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

#ifndef _STREAM_DATA_H_
#define _STREAM_DATA_H_

#define MIN_FILE_SIZE 50000
#define NUM_LOOKS_FOR_DATA 100
#define WAIT_BETWEEN_LOOKS 100

class CFileStreamData;
class CNetStreamData;
class CPTSParser;
class CMediaStream;
#define MAX_FILES_NUM   128

typedef struct _FILE_INF
{
	LONGLONG file_size;
	LONGLONG timeline;
	LONGLONG pts_start;
	LONGLONG pts_stop;
} FILE_INF;


class CAsyncStream
{
public:
    virtual ~CAsyncStream() {};
    virtual HRESULT SetPointer(LONGLONG llPos) = 0;
    virtual HRESULT Read(PBYTE pbBuffer,
                         DWORD dwBytesToRead,
                         BOOL bAlign,
                         LPDWORD pdwBytesRead) = 0;
    virtual LONGLONG Size(LONGLONG *pSizeAvailable = NULL, LONGLONG *pOverwritten = NULL) = 0;
    virtual DWORD Alignment() = 0;
    virtual void Lock() = 0;
    virtual void Unlock() = 0;
	virtual HRESULT GetMediaTime( LONGLONG* pllMediaTimeStart, LONGLONG* pllMediaTimeStop ) = 0;

};

enum STREAM_TYPE {
	NONE_STREAM = 0x0,
	FILE_STREAM = 0x01,
	NET_STREAM  = 0x02
} ;

#define FILE_ACTIVE			0x01
#define FILE_OPEN			0x02

typedef struct {
	DWORD    dwState;  
	void*    pStreamData;
	LONGLONG llPTSStart;
	LONGLONG llPTSStop;
	LONGLONG llFileSize;
	LONGLONG llPTSOffset;
	LONGLONG llLengthOffset;
} GROUP_FILE_INF;


class CFileStream : public CAsyncStream
{
public:
	CFileStream( CMediaStream *pMediaStream, DWORD dwStreamType, LPCSTR pHostName );
    virtual ~CFileStream();

    virtual HRESULT SetPointer(LONGLONG llPos);
    virtual HRESULT Read(PBYTE pbBuffer,
                         DWORD dwBytesToRead,
                         BOOL bAlign,
                         LPDWORD pdwBytesRead);
    virtual LONGLONG Size(LONGLONG *pSizeAvailable = NULL, LONGLONG *pOverwritten = NULL);
    virtual DWORD Alignment();
    virtual void Lock();
    virtual void Unlock();
	virtual HRESULT GetMediaTime( LONGLONG* pllMediaTimeStart, LONGLONG* pllMediaTimeStop );

	HRESULT Open( WCHAR* lpwszFileName );
	HRESULT ReOpen( );
	void    Close( );
	WCHAR*  GetCurFile( );

protected:
	DWORD			 m_eStreamType; //network or file
	CAsyncStream*    m_pStream;
	CFileStreamData* m_pFileStreamData;
	CNetStreamData*  m_pNetStreamData;
};


class CVFileStream : public CAsyncStream
{
public:
	CVFileStream( CMediaStream *pMediaStream, DWORD StreamType, LPCSTR pHostName );
    virtual ~CVFileStream();
    virtual HRESULT SetPointer(LONGLONG llPos);
    virtual HRESULT Read(PBYTE pbBuffer,
                         DWORD dwBytesToRead,
                         BOOL bAlign,
                         LPDWORD pdwBytesRead);
    virtual LONGLONG Size(LONGLONG *pSizeAvailable = NULL, LONGLONG *pOverwritten = NULL);
    virtual DWORD Alignment();
    virtual void Lock();
    virtual void Unlock();
	virtual HRESULT GetMediaTime( LONGLONG* pllMediaTimeStart, LONGLONG* pllMediaTimeStop );

	HRESULT OpenEx( int dwSerial, WCHAR* lpwszFileName );
	void    CloseEx( int dwSerial );
	void	SwitchFile( int dwSerial );
	WCHAR*  GetFileNameEx( int dwSerial );
	void	GetFileInf( int dwSerial, FILE_INF* pFileInf );

protected:
	CMediaStream*	 m_pMediaStream;
	BOOL			 m_dwGroupFileNum;
	GROUP_FILE_INF   m_GrpFileTbl[MAX_FILES_NUM];
	int				 m_dwWorkingFile;
	LONGLONG		 m_llCurPos;
	CPTSParser*		 m_pPTSParser;
	LPSTR			 m_pHostName;
	DWORD			 m_eStreamType; //network or file
	int			     m_nCurrentFile;

	CCritSec  m_csLock;

};

class CMediaStream : public CAsyncStream
{
public:
	CMediaStream( CBaseFilter* pFilter, LPCSTR pHostName, BOOL bVirtualFile=FALSE );
	~CMediaStream( );

	HRESULT Open( WCHAR* lpwszFileName );
	void    Close( );
	HRESULT OpenEx( int dwSerial, WCHAR* lpwszFileName );
	void    CloseEx( int dwSerial );
	void    SwitchFile( int dwSerial );

	virtual WCHAR* GetCurFile( );
	virtual WCHAR* GetFileNameEx( int dwSerial );
	virtual void GetFileInf( int dwSerial, FILE_INF* pFileInf );

    virtual HRESULT SetPointer(LONGLONG llPos);
    virtual HRESULT Read(PBYTE pbBuffer,
                         DWORD dwBytesToRead,
                         BOOL bAlign,
                         LPDWORD pdwBytesRead);
    virtual LONGLONG Size(LONGLONG *pSizeAvailable = NULL, LONGLONG *pOverwritten = NULL);
    virtual DWORD Alignment();
    virtual void Lock();
    virtual void Unlock();
	virtual HRESULT GetMediaTime( LONGLONG* pllMediaTimeStart, LONGLONG* pllMediaTimeStop );


	LONGLONG   GetTotalWrite(LPWSTR pwFileName); //for a live file

	DWORD	   m_dwSkipHeaderBytes;
	ULONGLONG  m_largeFileSize;
	ShareInfo* m_pShareInfo;
	DWORD	   m_dwCircFileSize;
	CCritSec   m_csLock;

	CAsyncStream* m_pStream;

	CBaseFilter* m_pFilter;
	BOOL	   m_bVirtualFile;


};

#endif
