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
#ifndef __H_IMPEGDEMUX_H
#define __H_IMPEGDEMUX_H

#ifdef __cplusplus
extern "C" {
#endif

// {0B7922CC-6FAB-49fa-BB60-98100FA67CDC}
DEFINE_GUID(IID_IMpegDeMux,
0xb7922cc, 0x6fab, 0x49fa, 0xbb, 0x60, 0x98, 0x10, 0xf, 0xa6, 0x7c, 0xdc);
 

//----------------------------------------------------------------------------
// IDeMpegMux
//----------------------------------------------------------------------------
DECLARE_INTERFACE_(IMpegDeMux, IUnknown)
{
	STDMETHOD( SetRemuxOptimize) (THIS_
			   BOOL bRemuxOptimize
				) PURE;

    STDMETHOD(SetAFDDump) (THIS_
			void* pfn,
			void* context 
            )PURE;

    STDMETHOD(SetCCDump) (THIS_
			void* pfn,
			void* context 
            )PURE;

    STDMETHOD(Duration) (THIS_
			LONGLONG *pDur
            )PURE;

	STDMETHOD(GetPlayRate) (THIS_
			long *pRate
            )PURE;

	STDMETHOD(SetPlayRate) (THIS_
			long pRate
            )PURE;

	STDMETHOD(SelectChannel) (THIS_
			long dwChannel
            )PURE;

	STDMETHOD(GetSelectedChannel) (THIS_
			long* pChannel
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

    STDMETHOD(FileSwitch) (THIS_
			unsigned long dwOffsetPTS
            )PURE;

	STDMETHOD(SwitchPosition)(THIS_
			LONGLONG* pllPreLastPTS, LONGLONG* pllLastPTS  
			) PURE;
	

};
//----------------------------------------------------------------------------

#ifdef __cplusplus
}
#endif

#endif 