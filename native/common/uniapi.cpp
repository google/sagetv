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
#include <string.h>
#include <stdio.h>
#include <time.h>
#include "uniapi.h"

void globalInvalidParameterHandler(const wchar_t* expression,
   const wchar_t* function, 
   const wchar_t* file, 
   unsigned int line, 
#ifdef WIN32
   uintptr_t pReserved)
#else
   unsigned int * pReserved)
#endif
{
	FILE* fp;
	time_t ct;	struct tm ltm;
	if ( file == NULL ) return;
	time(&ct); ltm = *localtime( &ct );
	fp = fopen( "ErrorCatch.log", "a+" );
	fwprintf( fp,  L"%02d/%02d/%d %02d:%02d:%02d (tid %06x) ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
				   ltm.tm_hour, ltm.tm_min, ltm.tm_sec, GetCurrentThreadId() );  
	fwprintf( fp,  L"Buffer overrun detected in function:%s, File:%s, Line:%d ", function, file, line );
	fwprintf( fp,  L"Expression: %s\n", expression );
	fclose( fp );
}

int   sprintf_sage( const char* file, int line, char* buf, int size, const char* cstr, ... )
{
	va_list args;
    va_start(args, cstr);
	int num = vsprintf_s( buf, size , cstr, args );
	if ( num < 0 || num > size )
	{
		FILE* fp;
		time_t ct;	struct tm ltm;
		time(&ct); ltm = *localtime( &ct );
		fp = fopen( "ErrorCatch.log", "a+" );
		fprintf( fp,  "%02d/%02d/%d %02d:%02d:%02d (tid %06x) ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
				   ltm.tm_hour, ltm.tm_min, ltm.tm_sec, GetCurrentThreadId() );  
		fprintf( fp,  "Buffer overrun detected in SPRINTF(). File: %s Line: %d ", file, line );
		fprintf( fp,  "Expression: \"%s\" \n", cstr );
		fclose( fp );
	}

	va_end(args);
	return num;
}

char* strcpy_sage( const char* file, int line, char* tar, int size, const char* src )
{
	int num = strcpy_s( tar, size , src );
	if ( num )
	{
		FILE* fp;
		time_t ct;	struct tm ltm;
		time(&ct); ltm = *localtime( &ct );
		fp = fopen( "ErrorCatch.log", "a+" );
		fprintf( fp,  "%02d/%02d/%d %02d:%02d:%02d (tid %06x) ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
				      ltm.tm_hour, ltm.tm_min, ltm.tm_sec, GetCurrentThreadId() );  
		fprintf( fp,  "Buffer overrun detected in STRCPY(). File: %s Line: %d ", file, line );
		fprintf( fp,  "tar_size:%d, src_size:%d src:'%p' \n", size, (int) strlen(src), src );
		fclose( fp );
		return "";
	}
	return tar;
}

