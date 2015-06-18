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
#include <initguid.h>   // Make our GUID get defined
#include <stdio.h>
#include "..\..\..\third_party\Microsoft\FileSource\strconv.h"
#include "DebugLog.h"

#include "StreamType.h"



#define REG_STRING_READ_SIZE 64



BOOL CStreamType::CheckMPEGPacket( const unsigned char* pStart, int Bytes )
{
	const unsigned char	*pbData, *p1, *p2;
	int	 len;
	unsigned long code;
	unsigned long StartCode = 0x000001BA;

	//probe pattern: 00 00 01 BA xxxxxx  00 00 01[BB|BE|En|Cn|BD|BF] xxxx
	if ( Bytes < 4 )
		return false;

	pbData = pStart;
	len = Bytes;
	code = 0xffffff00;

	pbData = pStart;
	code |=  *pbData++;
	while ( --len && code != StartCode )
	{
		code = ( code << 8 )| *pbData++;
	}

	if ( code == StartCode )
	{
		p1 = pbData;
		// Check MPEG2-PS marker so we don't detect MPEG1 as MEPG2
		if ((*p1 & 0x40) != 0x40)
			return false;
		code = 0xffffff00;
		code |=  *pbData++;
		while ( --len && ( code & 0x00ffffff ) != 0x01  )
		{
			code = ( code << 8 )| *pbData++;
		}
		p2 = pbData;
		if (  ( code & 0x00ffffff ) == 0x01  && p2-p1 < 30 )
		{
			if ( *pbData == 0xBB || *pbData == 0xBE || *pbData == 0xBD || *pbData == 0xBF 
				|| ( *pbData & 0xc0 ) == 0xc0 || ( *pbData & 0xE0 ) == 0xe0 )
			return true;
		}
	}

	return false;

}

BOOL CStreamType::CheckTSPacket( const unsigned char* pStart, int Bytes ) 
{
	int	 i;

	if ( Bytes < 188*3 )
		return false;
	//TS packets
	for ( i = 0; i<=Bytes-188*2; i++ )
	{
		if ( pStart[i] == 0x47 && pStart[i+188] == 0x47 && pStart[i+2*188] == 0x47 )
		{
			return true;
		}
	}
	//M2TS packets
	for ( i = 0; i<=Bytes-192*2; i++ )
	{
		if ( pStart[i] == 0x47 && pStart[i+192] == 0x47 && pStart[i+2*192] == 0x47 )
		{
			return true;
		}
	}

	return false;
}

BOOL CStreamType::CheckTypeByData( char *pData, int dwSize )
{
	BOOL foundPattern = FALSE;
	if ( m_bFoundType )
		return m_bFoundType;

	if ( CheckMPEGPacket( (const unsigned char*)pData, (int)dwSize ) )
	{
		m_mt.SetType(&MEDIATYPE_Stream);
		m_mt.SetSubtype(&MEDIASUBTYPE_MPEG2_PROGRAM);
		foundPattern = TRUE;
	}

	if (!foundPattern &&  CheckTSPacket( (const unsigned char*)pData, (int)dwSize ) )
	{
		m_mt.SetType(&MEDIATYPE_Stream);
		m_mt.SetSubtype(&MEDIASUBTYPE_MPEG2_TRANSPORT);
		foundPattern = TRUE;
	}

	m_bFoundType = foundPattern;
	return m_bFoundType;
}

BOOL CStreamType::CheckTypeByExtension( char* szFileExt, char* readBuf, int readSize )
{
	// First get the major type classes
	HKEY rootKey = HKEY_CLASSES_ROOT;
	HKEY myKey;
	LONG resErr = RegOpenKeyEx(rootKey, "Media Type", 0, KEY_READ, &myKey);
	BOOL foundPattern = FALSE;
	DWORD dwMajorName = REG_STRING_READ_SIZE;
	TCHAR pMajorName[REG_STRING_READ_SIZE];
	DWORD dwMinorName = REG_STRING_READ_SIZE;
	TCHAR pMinorName[REG_STRING_READ_SIZE];
	HRESULT hr;
	if ( m_bFoundType )
		return m_bFoundType;

	if ( szFileExt[0] != '\0' )
	{
		DbgLog((LOG_TRACE, 2, TEXT("Checking file extension %s"), szFileExt));
		HKEY fileExtKey;
		resErr = RegOpenKeyEx(myKey, szFileExt, 0, KEY_READ, &fileExtKey);
		if (resErr == ERROR_SUCCESS)
		{
			resErr = RegQueryValueEx(fileExtKey, "Media Type", NULL, NULL, (LPBYTE)pMajorName, &dwMajorName);
			if (resErr == ERROR_SUCCESS)
			{
				CLSID majorClsid;
				WCHAR majorWsz[64];
				MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, pMajorName, -1, majorWsz, 64);
				hr = CLSIDFromString(majorWsz, &majorClsid);
				if (SUCCEEDED(hr))
				{
					m_mt.SetType(&majorClsid);
					m_mt.SetSubtype(&MEDIASUBTYPE_NULL);
					foundPattern = TRUE;
					resErr = RegQueryValueEx(fileExtKey, "Subtype", NULL, NULL, (LPBYTE)pMinorName, &dwMinorName);
					if (resErr == ERROR_SUCCESS)
					{
						CLSID minorClsid;
						WCHAR minorWsz[64];
						MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, pMinorName, -1, minorWsz, 64);
						hr = CLSIDFromString(minorWsz, &minorClsid);
						if (SUCCEEDED(hr))
						{
							m_mt.SetSubtype(&minorClsid);
						}
					}
				}

			}
			RegCloseKey(fileExtKey);
		}
	}

	DWORD majoridx = 0;
	dwMajorName = REG_STRING_READ_SIZE;
	LONG res = RegEnumKeyEx(myKey, majoridx, pMajorName, &dwMajorName, NULL, NULL, NULL, NULL);
	while (res == ERROR_SUCCESS && !foundPattern)
	{
		DbgLog((LOG_TRACE, 2, TEXT("Major Key=%s"), pMajorName));
		HKEY majorKey;
		DWORD minoridx = 0;
		resErr = RegOpenKeyEx(myKey, pMajorName, 0, KEY_READ, &majorKey);
		if (resErr == ERROR_SUCCESS)
		{
			dwMinorName = REG_STRING_READ_SIZE;
			res = RegEnumKeyEx(majorKey, minoridx, pMinorName, &dwMinorName, NULL, NULL, NULL, NULL);
			while (res == ERROR_SUCCESS && !foundPattern)
			{
				DbgLog((LOG_TRACE, 2, TEXT("Minor Key=%s"), pMinorName));
				HKEY minorKey;
				resErr = RegOpenKeyEx(majorKey, pMinorName, 0, KEY_READ, &minorKey);
				if (resErr == ERROR_SUCCESS)
				{
					// Now check each of the 0, 1, 2....entries for possible byte patterns to match against
					for (int mtNum = 0; !foundPattern; mtNum++)
					{
						TCHAR mtNumName[3];
						_snprintf_s(mtNumName, sizeof(mtNumName), "%d", mtNum);
						TCHAR bytePatStr[256];
						DWORD dwBytePatStr = 256;
						BYTE currByteMask[128];
						BYTE currBytePattern[128];
						resErr = RegQueryValueEx(minorKey, mtNumName, NULL, NULL, (LPBYTE)bytePatStr, &dwBytePatStr);
						if (resErr == ERROR_SUCCESS)
						{
							DbgLog((LOG_TRACE, 2, TEXT("MTNum=%d"), mtNum));
							BOOL patternFailed = FALSE;
							// We've got a byte pattern to test against now
							TCHAR* strp = bytePatStr;
							TCHAR* nextComma = strchr(strp, ',');
							if (!nextComma || nextComma == strp) continue;
							while (nextComma && !patternFailed)
							{
								*nextComma = '\0';
								int patOffset = atoi(strp);
								strp = nextComma + 1;
								nextComma = strchr(strp, ',');
								if (!nextComma || nextComma == strp) break;
								*nextComma = '\0';
								int patSize = atoi(strp);
								DbgLog((LOG_TRACE, 2, TEXT("Pat offset=%d size=%d"), patOffset, patSize));
								strp = nextComma + 1;
								nextComma = strchr(strp, ',');
								if (!nextComma) break;
								*nextComma = '\0';
								memset(currByteMask, 0xFF, patSize);
								if (nextComma != strp)
								{
									// A mask was specified, strip any whitespace
									while (*strp == ' ') strp++;
									while (strp[strlen(strp) - 1] == ' ') strp[strlen(strp) - 1] = '\0';
									if (strp[0] != '\0')
									{
										DbgLog((LOG_TRACE, 2, TEXT("Pat Mask=%s"), strp));
										for (int byteNum = 0; byteNum < patSize; byteNum++)
										{
											TCHAR oldChar = strp[2];
											strp[2] = '\0';
											currByteMask[byteNum] = (BYTE) strtol(strp, NULL, 16);
											strp[2] = oldChar;
											strp += 2;
										}
									}
								}
								strp = nextComma + 1;
								// If this is the last one, it won't be here
								nextComma = strchr(strp, ',');
								if (nextComma) *nextComma = '\0';

								// Read the value, strip any whitespace
								while (*strp == ' ') strp++;
								while (strp[strlen(strp) - 1] == ' ') strp[strlen(strp) - 1] = '\0';
								DbgLog((LOG_TRACE, 2, TEXT("Pat Value=%s"), strp));
								for (int byteNum = 0; byteNum < patSize; byteNum++)
								{
									TCHAR oldChar = strp[2];
									strp[2] = '\0';
									currBytePattern[byteNum] = (BYTE) strtol(strp, NULL, 16);
									strp[2] = oldChar;
									strp += 2;
								}

								if (patOffset >= 0 && patOffset + patSize < 256) // 256 is the file read size
								{
									for (int byteNum = 0; byteNum < patSize && patOffset + byteNum < readSize; byteNum++)
									{
										if ((readBuf[patOffset + byteNum] & currByteMask[byteNum]) != 
											currBytePattern[byteNum])
										{
											patternFailed = TRUE;
											break;
										}
									}
								}

								// prepare for the next one
								if (nextComma) 
								{
									strp = nextComma + 1;
									nextComma = strchr(strp, ',');
								}
							}

							if (!patternFailed)
							{
								CLSID majorClsid;
								CLSID minorClsid;
								WCHAR majorWsz[64];
								WCHAR minorWsz[64];
								MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, pMajorName, -1, majorWsz, 64);
								MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, pMinorName, -1, minorWsz, 64);
								hr = CLSIDFromString(majorWsz, &majorClsid);
								if (SUCCEEDED(hr))
								{
									m_mt.SetType(&majorClsid);
									m_mt.SetSubtype(&MEDIASUBTYPE_NULL);
									foundPattern = TRUE;
									hr = CLSIDFromString(minorWsz, &minorClsid);
									if (SUCCEEDED(hr))
									{
										m_mt.SetSubtype(&minorClsid);
									}
								}
							}
						}
						else
							break; // out of patterns for this subtype
					}
					RegCloseKey(minorKey);
				}
				minoridx++;
				dwMinorName = REG_STRING_READ_SIZE;
				res = RegEnumKeyEx(majorKey, minoridx, pMinorName, &dwMinorName, NULL, NULL, NULL, NULL);
			}
			RegCloseKey(majorKey);

		}
		majoridx++;
		dwMajorName = REG_STRING_READ_SIZE;
		res = RegEnumKeyEx(myKey, majoridx, pMajorName, &dwMajorName, NULL, NULL, NULL, NULL);
	}
	RegCloseKey(myKey);
	m_bFoundType = foundPattern;
	return m_bFoundType;
}
		

