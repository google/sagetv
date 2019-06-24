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
#include "stdafx.h"
#include "JeffMixer.h"
//using namespace std; 	//introduces namespace std

void CJeffMixer::ZeroAll()
{
	m_HMixer = NULL;
	m_iMixerControlID = 0;
	mmr = MMSYSERR_NOERROR;
	m_dwChannels = 0;
	m_bSuccess = FALSE;
}

CJeffMixer::CJeffMixer(DWORD DstType, DWORD SrcType, DWORD ControlType, bool useIndex)
{
	ZeroAll();
	int numDevs = mixerGetNumDevs();
	if(numDevs < 1) return;
	mmr = mixerOpen(&m_HMixer, 0, (DWORD_PTR)0, (DWORD_PTR)0, CALLBACK_NULL);
	if (mmr != MMSYSERR_NOERROR) return;
// get dwLineID
	MIXERLINE mxl;
	mxl.cbStruct = sizeof(MIXERLINE);
// DstType
	mxl.dwComponentType = DstType;
	if (mixerGetLineInfo((HMIXEROBJ)m_HMixer, &mxl, MIXER_OBJECTF_HMIXER | MIXER_GETLINEINFOF_COMPONENTTYPE)
	!= MMSYSERR_NOERROR) return;
// SrcType
	if( SrcType != NO_SOURCE )
	{
		UINT nconn = mxl.cConnections;
		DWORD DstIndex = mxl.dwDestination;
		for( UINT j = 0; j < nconn; j++ )
		{
			mxl.cbStruct = sizeof( MIXERLINE );
			mxl.dwSource = j;
			mxl.dwDestination = DstIndex;
			if(mixerGetLineInfo( ( HMIXEROBJ )m_HMixer,
			&mxl, MIXER_GETLINEINFOF_SOURCE ) != MMSYSERR_NOERROR) return;
			/*
			 * I had an issue with it selecting the "Mono In" input instead
			 * of the line in. This clears it up by ensuring it's stereo input.
			 */
			if((useIndex && j == SrcType) || (!useIndex && mxl.dwComponentType == SrcType && mxl.cChannels == 2)) break;
		}
	}
// get dwControlID
	MIXERCONTROL mxc;
	MIXERLINECONTROLS mxlc;
	mxlc.cbStruct = sizeof(MIXERLINECONTROLS);
	mxlc.dwLineID = mxl.dwLineID;
	mxlc.dwControlType = ControlType;
	mxlc.cControls = 1;
	mxlc.cbmxctrl = sizeof(MIXERCONTROL);
	mxlc.pamxctrl = &mxc;
	if (mixerGetLineControls((HMIXEROBJ)m_HMixer, &mxlc, MIXER_OBJECTF_HMIXER | MIXER_GETLINECONTROLSF_ONEBYTYPE) != MMSYSERR_NOERROR) return;
	m_iMixerControlID = mxc.dwControlID;
	m_dwChannels = mxl.cChannels;
	m_bSuccess = TRUE;
}

CJeffMixer::CJeffMixer(HWND hwnd, DWORD DstType, DWORD SrcType, DWORD ControlType)
{
	ZeroAll();
	if(mixerGetNumDevs() < 1) return;
	mmr = mixerOpen(&m_HMixer, 0, (DWORD_PTR)hwnd, (DWORD_PTR)0, CALLBACK_WINDOW);
	if (mmr != MMSYSERR_NOERROR) return;
// get dwLineID
	MIXERLINE mxl;
	mxl.cbStruct = sizeof(MIXERLINE);
// DstType
	mxl.dwComponentType = DstType;
	if (mixerGetLineInfo((HMIXEROBJ)m_HMixer, &mxl, MIXER_OBJECTF_HMIXER | MIXER_GETLINEINFOF_COMPONENTTYPE)
	!= MMSYSERR_NOERROR) return;
// SrcType
	if( SrcType != NO_SOURCE )
	{
		UINT nconn = mxl.cConnections;
		DWORD DstIndex = mxl.dwDestination;
		for( UINT j = 0; j < nconn; j++ )
		{
			mxl.cbStruct = sizeof( MIXERLINE );
			mxl.dwSource = j;
			mxl.dwDestination = DstIndex;
			if(mixerGetLineInfo( ( HMIXEROBJ )m_HMixer,
			&mxl, MIXER_GETLINEINFOF_SOURCE ) != MMSYSERR_NOERROR) return;
			if( mxl.dwComponentType == SrcType ) break;
		}
	}
// get dwControlID
	MIXERCONTROL mxc;
	MIXERLINECONTROLS mxlc;
	mxlc.cbStruct = sizeof(MIXERLINECONTROLS);
	mxlc.dwLineID = mxl.dwLineID;
	mxlc.dwControlType = ControlType;
	mxlc.cControls = 1;
	mxlc.cbmxctrl = sizeof(MIXERCONTROL);
	mxlc.pamxctrl = &mxc;
	if (mixerGetLineControls((HMIXEROBJ)m_HMixer, &mxlc, MIXER_OBJECTF_HMIXER | MIXER_GETLINECONTROLSF_ONEBYTYPE) != MMSYSERR_NOERROR) return;
	m_iMixerControlID = mxc.dwControlID;
	m_bSuccess = TRUE;
}

CJeffMixer::~CJeffMixer()
{
	if (m_HMixer) mixerClose(m_HMixer);
}

////////////////////////////////////////

DWORD CJeffMixer::GetControlValueB()
{
	if (!m_bSuccess) return 0;
	m_bSuccess = FALSE;
	MIXERCONTROLDETAILS mxcd;
	MIXERCONTROLDETAILS_BOOLEAN mxcd_u;
	mxcd.cbStruct = sizeof(mxcd);
	mxcd.dwControlID = m_iMixerControlID;
	mxcd.cChannels = 1;
	mxcd.cMultipleItems = 0;
	mxcd.cbDetails = sizeof(mxcd_u);
	mxcd.paDetails = &mxcd_u;
	mmr = mixerGetControlDetails((HMIXEROBJ)m_HMixer, &mxcd,
		MIXER_GETCONTROLDETAILSF_VALUE | MIXER_OBJECTF_HMIXER);
	if (MMSYSERR_NOERROR != mmr) return 0;
	m_bSuccess = TRUE;
	return mxcd_u.fValue;
}

BOOL CJeffMixer::SetControlValueB(DWORD dw)
{
	if (!m_bSuccess) return m_bSuccess;
	m_bSuccess = FALSE;
	MIXERCONTROLDETAILS mxcd;
	MIXERCONTROLDETAILS_BOOLEAN mxcd_u;
	mxcd.cbStruct = sizeof(mxcd);
	mxcd.dwControlID = m_iMixerControlID;
	mxcd.cChannels = 1;
	mxcd.cMultipleItems = 0;
	mxcd.cbDetails = sizeof(mxcd_u);
	mxcd.paDetails = &mxcd_u;
	mmr = mixerGetControlDetails((HMIXEROBJ)m_HMixer, &mxcd, 
		MIXER_GETCONTROLDETAILSF_VALUE | MIXER_OBJECTF_HMIXER);
	if (MMSYSERR_NOERROR != mmr) return m_bSuccess;
	mxcd_u.fValue  = dw;
	mmr = mixerSetControlDetails((HMIXEROBJ)m_HMixer, &mxcd, 
		MIXER_SETCONTROLDETAILSF_VALUE | MIXER_OBJECTF_HMIXER);
	if (MMSYSERR_NOERROR != mmr) return m_bSuccess;
	m_bSuccess = TRUE;
	return m_bSuccess;
}

BOOL CJeffMixer::On()
{
	return SetControlValueB(1);
}

BOOL CJeffMixer::Off()
{
	return SetControlValueB(0);
}

DWORD CJeffMixer::GetControlValueD()
{
	if (!m_bSuccess) return 0;
	m_bSuccess = FALSE;
	MIXERCONTROLDETAILS mxcd;
	MIXERCONTROLDETAILS_UNSIGNED mxcd_u;
	mxcd.cbStruct = sizeof(mxcd);
	mxcd.dwControlID = m_iMixerControlID;
	mxcd.cChannels = 1;
	mxcd.cMultipleItems = 0;
	mxcd.cbDetails = sizeof(mxcd_u);
	mxcd.paDetails = &mxcd_u;
	mmr = mixerGetControlDetails((HMIXEROBJ)m_HMixer, &mxcd,
		MIXER_GETCONTROLDETAILSF_VALUE | MIXER_OBJECTF_HMIXER);
	if (MMSYSERR_NOERROR != mmr) return 0;
	m_bSuccess = TRUE;
	return mxcd_u.dwValue;
}

BOOL CJeffMixer::SetControlValueD(DWORD dw)
{
	if (!m_bSuccess) return m_bSuccess;
	m_bSuccess = FALSE;
	MIXERCONTROLDETAILS mxcd;
	MIXERCONTROLDETAILS_UNSIGNED mxcd_u;
	mxcd.cbStruct = sizeof(mxcd);
	mxcd.dwControlID = m_iMixerControlID;
	mxcd.cChannels = 1;
	mxcd.cMultipleItems = 0;
	mxcd.cbDetails = sizeof(mxcd_u);
	mxcd.paDetails = &mxcd_u;
	mmr = mixerGetControlDetails((HMIXEROBJ)m_HMixer, &mxcd, 
		MIXER_GETCONTROLDETAILSF_VALUE | MIXER_OBJECTF_HMIXER);
	if (MMSYSERR_NOERROR != mmr) return m_bSuccess;
	mxcd_u.dwValue  = dw;
	mmr = mixerSetControlDetails((HMIXEROBJ)m_HMixer, &mxcd, 
		MIXER_GETCONTROLDETAILSF_VALUE | MIXER_OBJECTF_HMIXER);
	if (MMSYSERR_NOERROR != mmr) return m_bSuccess;
	m_bSuccess = TRUE;
	return m_bSuccess;
}

