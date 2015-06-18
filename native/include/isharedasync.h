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
#ifndef __H_ISHAREDASYNC_H
#define __H_ISHAREDASYNC_H

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

// {573D5172-A718-4840-9E0D-7FE83D9EAA8F}
DEFINE_GUID(IID_ISharedAsyncFile, 
0x573d5172, 0xa718, 0x4840, 0x9e, 0xd, 0x7f, 0xe8, 0x3d, 0x9e, 0xaa, 0x8f);
  

//----------------------------------------------------------------------------
// ISharedAsyncFile
//----------------------------------------------------------------------------
DECLARE_INTERFACE_(ISharedAsyncFile, IUnknown)
{

    STDMETHOD(put_ShareInfo) (THIS_
    				  ShareInfo* sharin
				 ) PURE;

    STDMETHOD(put_CircularSize) (THIS_
    				  long circSize
				 ) PURE;

    STDMETHOD(put_RemoteHostname) (THIS_
    				  LPCSTR pHostname
				 ) PURE;

	STDMETHOD(Reset) ( THIS_ );
	STDMETHOD(LoadEx) ( THIS_ 
					  DWORD dwSerial, LPCOLESTR lpwszFileName, const AM_MEDIA_TYPE *pmt ) ;

	STDMETHOD(GetFileName) ( THIS_ 
					 DWORD dwSerial, WCHAR* pFileName, DWORD dwSizeOfFileName  ) ;

	STDMETHOD(GetFileInf) ( THIS_ 
					 DWORD dwSerial, void* pInfo, DWORD dwSizeOfInfo ) ;


};
//----------------------------------------------------------------------------

#ifdef __cplusplus
}
#endif

#endif // __H_ISHAREDASYNC_H