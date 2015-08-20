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
#ifndef __H_ISTREAMDEMUX_H
#define __H_ISTREAMDEMUX_H

#ifdef __cplusplus
extern "C" {
#endif

 
DEFINE_GUID(IID_ISDeMux,
    0xabd65eff, 0x5702, 0x4f21, 0x86, 0x3b, 0x59, 0x1b, 0x05, 0x7d, 0x22, 0x68 );

//----------------------------------------------------------------------------
// IDeMpegMux
//----------------------------------------------------------------------------
DECLARE_INTERFACE_(ISDeMux, IUnknown)
{

    STDMETHOD(Duration) (THIS_
			LONGLONG *pDur
            )PURE;

	STDMETHOD(GetPlayRate) (THIS_
			long *pRate
            )PURE;

	STDMETHOD(SetPlayRate) (THIS_
			long pRate
            )PURE;

	STDMETHOD(GetStatistic) (THIS_
			void** pData
			)PURE;

	STDMETHOD(SetAlloProp) (THIS_
			long dwBlockSize, long dwBufferSize
			)PURE;

	STDMETHOD(GetAlloProp) (THIS_
			long* pdwBlockSize, long* pdwBufferSize
            )PURE;

    STDMETHOD(SetEventDump) (THIS_
			void* pfn,
			void* context 
            )PURE;


};
//----------------------------------------------------------------------------

#ifdef __cplusplus
}
#endif

#endif 