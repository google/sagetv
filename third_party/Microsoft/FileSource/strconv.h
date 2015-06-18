//////////////////////////////////////////////////////////////////////////
// Copyright 2015 The SageTV Authors. All Rights Reserved.
//------------------------------------------------------------------------------
// File: WXDebug.cpp
//
// Desc: DirectShow base classes - implements ActiveX system debugging
//       facilities.    
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

#if !defined(_STRCONV_H_)
#define _STRCONV_H_

#include <tchar.h>
#include <stdio.h>

enum {  DISP_HEX = 0x01,
        DISP_DEC = 0x02};

class Disp 
{

public:
    Disp(LONGLONG ll, int Format = CDISP_DEC)
	{
		LARGE_INTEGER li;
		li.QuadPart = ll;
		memset( m_szString, 0, sizeof(m_szString) );
		
		switch (Format) {
		case CDISP_DEC:
		{
			TCHAR  temp[40];
			int pos=20;
			temp[--pos] = 0;
			int digit;
			// always output at least one digit
			do {
			// Get the rightmost digit - we only need the low word
				digit = li.LowPart % 10;
				li.QuadPart /= 10;
				temp[--pos] = (TCHAR) digit+L'0';
			} while (li.QuadPart);
			(void)strncpy_s( m_szString, sizeof(m_szString), temp+pos,  sizeof(m_szString) );
			break;
		}
		case CDISP_HEX:
		default:
			(void)sprintf_s( m_szString, sizeof(m_szString), TEXT("0x%X%8.8X"), li.HighPart, li.LowPart);
		}

	}

    Disp(CRefTime llTime)
	{
		char tmp[128];
		memset( m_szString, 0, sizeof(m_szString) );

		LONGLONG llDiv;
		if (llTime < 0) {

			llTime = -llTime;
			(void)strncpy_s( m_szString, sizeof(m_szString), TEXT("-"), sizeof(m_szString) );
		}
		llDiv = (LONGLONG)24 * 3600 * 10000000;
		if (llTime >= llDiv) {
			sprintf_s( tmp, sizeof(tmp),  TEXT("%d days "), (LONG)(llTime / llDiv) );
			strncat_s( m_szString+strlen(m_szString), sizeof(m_szString)-strlen(m_szString), tmp, sizeof(m_szString)-strlen(m_szString) );
			llTime = llTime % llDiv;
		}
		llDiv = (LONGLONG)3600 * 10000000;
		if (llTime >= llDiv) {
			//(void)StringCchPrintf(m_String + lstrlen(m_String), NUMELMS(m_String) - lstrlen(m_String), TEXT("%d hrs "), (LONG)(llTime / llDiv));
			sprintf_s( tmp, sizeof(tmp),  TEXT("%d hrs "), (LONG)(llTime / llDiv) );
			strncat_s( m_szString+strlen(m_szString), sizeof(m_szString)-strlen(m_szString), tmp, sizeof(m_szString)-strlen(m_szString) );
			llTime = llTime % llDiv;
		}
		llDiv = (LONGLONG)60 * 10000000;
		if (llTime >= llDiv) {
			//(void)StringCchPrintf(m_String + lstrlen(m_String), NUMELMS(m_String) - lstrlen(m_String), TEXT("%d mins "), (LONG)(llTime / llDiv));
			sprintf_s( tmp, sizeof(tmp),  TEXT("%d mins "), (LONG)(llTime / llDiv) );
			strncat_s( m_szString+strlen(m_szString), sizeof(m_szString)-strlen(m_szString), tmp, sizeof(m_szString)-strlen(m_szString) );
			llTime = llTime % llDiv;
		}
		sprintf_s( tmp, sizeof(tmp), TEXT("%d.%3.3d sec"), 
			      (LONG)llTime/10000000, (LONG)((llTime % 10000000)/ 10000) );
		strncat_s( m_szString+strlen(m_szString), sizeof(m_szString)-strlen(m_szString), tmp, sizeof(m_szString)-strlen(m_szString) );

		//(void)StringCchPrintf(m_String + lstrlen(m_String), NUMELMS(m_String) - lstrlen(m_String), TEXT("%d.%3.3d sec"),
		//		 (LONG)llTime / 10000000,
		//		 (LONG)((llTime % 10000000) / 10000));
	}

	~Disp(){ };

    //  Implement cast to (LPCTSTR) as parameter to logger
    operator LPCTSTR()
    {
        return (LPCTSTR)m_szString;
    };

	char m_szString[512];
};





#endif // !defined(_STRCONV_H_)
