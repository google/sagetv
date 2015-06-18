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
#ifndef __H_IMPEGMUX_H
#define __H_IMPEGMUX_H

#ifdef __cplusplus
extern "C" {
#endif

#ifndef _SHAREINFODEF_
#define _SHAREINFODEF_
struct ShareInfo
{
	PBYTE ptr;
	CCritSec shareLock;
	ULONGLONG totalWrite;
	DWORD ptrSize;
};
typedef struct ShareInfo ShareInfo;
#endif

// {A3E5ECBD-C9C3-4416-85B7-269A7B7DB96D}
DEFINE_GUID(IID_IMpegMux, 
0xa3e5ecbd, 0xc9c3, 0x4416, 0x85, 0xb7, 0x26, 0x9a, 0x7b, 0x7d, 0xb9, 0x6d);
  

//----------------------------------------------------------------------------
// IMpegMux
//----------------------------------------------------------------------------
DECLARE_INTERFACE_(IMpegMux, IUnknown)
{

    STDMETHOD(get_ShareInfo) (THIS_
    				  ShareInfo **sharin
				 ) PURE;

    STDMETHOD(get_FileLength) (THIS_
    				  LONGLONG *fileLength
				 ) PURE;

	STDMETHOD(put_CircularSize) (THIS_
					   long lCircSize
				) PURE;

	STDMETHOD(ForceCleanUp) (THIS_
				) PURE;

/* LAZER - enable this for LAZER support
	STDMETHOD(SetBuffering) (THIS_
					   BOOL enable
				) PURE;

	STDMETHOD(get_buffer) (THIS_
					   void* buf,
					   int size,
					   int* retSize
				) PURE;
*/

};
//----------------------------------------------------------------------------

#ifdef __cplusplus
}
#endif

#endif // __H_ISHAREDASYNC_H