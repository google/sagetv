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
// stdafx.h : include file for standard system include files,
//  or project specific include files that are used frequently, but
//      are changed infrequently
//

#if !defined(AFX_STDAFX_H__020E1BEE_F088_42F7_918D_937B8E35364C__INCLUDED_)
#define AFX_STDAFX_H__020E1BEE_F088_42F7_918D_937B8E35364C__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)
#pragma warning(disable : 4995)

// Insert your headers here
#define WIN32_LEAN_AND_MEAN		// Exclude rarely-used stuff from Windows headers

#define _WIN32_DCOM

#include <windows.h>
#include <objbase.h>
#include <atlbase.h>
#include <jni.h>
#include <dshow.h>
#include "../../include/jni-util.h"

// TODO: reference additional headers your program requires here

// COINIT_MULTITHREADED seemed like it locked up on 350 calls because I wasn't syncing the COM calls
#define COM_THREADING_MODE COINIT_APARTMENTTHREADED

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_STDAFX_H__020E1BEE_F088_42F7_918D_937B8E35364C__INCLUDED_)
