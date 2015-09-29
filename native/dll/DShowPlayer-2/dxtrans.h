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



/**
 * FILE: dxtrans.h
 *
 *
 * kludge alert! 
 *
 * DirectX 9.0 SDK (from 2004) had originally provided the file dxtrans.h
 * which is required by
 * C:\Program Files (x86)\Microsoft SDKs\Windows\v6.1\Include\qedit.h 
 *
 * Unfortunately, Microsoft somehow failed to include it in their
 * final release of "DirectX SDK (June 2010)".
 * Without it, " C1083: can't open include file: 'dxtrans.h' ".
 *
 * In a development environment that consists of VS2005, SDK6.1 and
 * DirectX SDK (2010), it seems that the reasonable solution to this 
 * issue is to simply supply our own version of the "dxtrans.h" header 
 * file in the relevant project's include directory.  That way, if
 * the SDK ever does supply dxtrans.h again, their own SDK 
 * (the SDK's qedit.h uses "#include <dxtrans.h>" )
 * should pickup the SDK's dxtrans.h instead of this local one. 
 * So...
 
 * This dummy file has been created per:
 *
 * "dxtrans.h missing in Microsoft DirectX SDK"
 * https://social.msdn.microsoft.com/Forums/windowsdesktop/en-US/ed097d2c-3d68-4f48-8448-277eaaf68252/dxtransh-missing-in-microsoft-directx-sdk-november-2007?forum=windowssdk
 *
 * See proposed answer by jpvanoosten Wednesday, January 20, 2010 3:08 PM 
 *
 */

#define __IDxtCompositor_INTERFACE_DEFINED__
#define __IDxtAlphaSetter_INTERFACE_DEFINED__
#define __IDxtJpeg_INTERFACE_DEFINED__
#define __IDxtKey_INTERFACE_DEFINED__
