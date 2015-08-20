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

#ifdef WIN32
#include <windows.h>
#endif

#if !defined( _UNIAPI_INCLUDED_ )
#define  _UNIAPI_INCLUDED_

#ifdef __cplusplus
extern "C" {
#endif

#ifdef WIN32
void  globalInvalidParameterHandler(const wchar_t* expression,  const wchar_t* function, const wchar_t* file, unsigned int line, uintptr_t pReserved);
#else
void  globalInvalidParameterHandler(const wchar_t* expression,  const wchar_t* function, const wchar_t* file, unsigned int line, unsigned int *pReserved);
#endif
int   sprintf_sage( const char* file, int line, char* buf ,int size, const char* str, ... );
char* strcpy_sage( const char* file, int line, char* tar, int size, const char* src );
				  
#ifdef WIN32
#define SPRINTF( ... )		sprintf_sage( __FILE__, __LINE__, __VA_ARGS__ )
#define STRCPY(x, y, z)	    strcpy_sage(__FILE__, __LINE__, x, y, z )
#else
#define SPRINTF( x, y, ... )	sprintf( x, __VA_ARGS__ )
#define STRCPY	    strcpy
#endif


#ifdef __cplusplus
 }
#endif
#endif
