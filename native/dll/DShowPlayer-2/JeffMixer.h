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
#ifndef _H_JEFFMIXER_H_
#define _H_JEFFMIXER_H_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

#include <mmsystem.h>

#define NO_SOURCE ((MIXERLINE_COMPONENTTYPE_SRC_LAST + 1))

class CJeffMixer
{
protected:
	HMIXER m_HMixer;
	INT m_iMixerControlID;
	MMRESULT mmr;
	DWORD m_dwChannels;
	BOOL m_bSuccess;
	void ZeroAll();
public:
	BOOL IsOk() {return m_bSuccess;};
	BOOL On();
	BOOL Off();
	DWORD GetControlValueB();
	BOOL SetControlValueB(DWORD dw);
	DWORD GetControlValueD();
	BOOL SetControlValueD(DWORD dw);
	CJeffMixer(DWORD DstType, DWORD SrcType, DWORD ControlType, bool useIndex=true);
	CJeffMixer(HWND hwnd, DWORD DstType, DWORD SrcType, DWORD ControlType);
	virtual ~CJeffMixer();
};

#endif 
