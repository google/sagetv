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

#ifndef FILTERGRAPHTOOLS_H
#define FILTERGRAPHTOOLS_H

//#pragma warning (disable : 4312)
//#pragma warning (disable : 4995)
#include <dshow.h>
#include <initguid.h>
#include <atlbase.h>
#include <atlconv.h>
#include <tchar.h>
#include <math.h>
#include <bdatif.h>
#include <tuner.h>            
#include <tune.h>
#include <ks.h> // Must be included before ksmedia.h
#include <ksmedia.h> // Must be included before bdamedia.h
#include <bdamedia.h>
		 

//#include "streams.h"


#define SAFE_RELEASE_INTERFACE( x )    if ( x ) { x.Release(); x = NULL; }
#define SAFE_DELETE( x )     if ( x ) { delete x; x = NULL; }



enum TV_TYPE 
{
	UNKNOWN = 0x0000,
    ATSC  = 0x0003,
	DVBT  = 0x0004,
	DVBC  = 0x0005,
	DVBS  = 0x0006,
	QAM   = 0x0007,  //ATSC + QAM
	ATSC_N = 0x0008,
	//QAM   = 0x0008,
};

typedef enum _RequestedPinDirection
{
	REQUESTED_PINDIR_INPUT    = PINDIR_INPUT,
	REQUESTED_PINDIR_OUTPUT   = PINDIR_OUTPUT,
	REQUESTED_PINDIR_ANY      = PINDIR_OUTPUT + 1
} REQUESTED_PIN_DIRECTION;


class CLogMsg  
{
public:
	CLogMsg(){ m_output = NULL;};
	virtual ~CLogMsg(){};
	void SetLogOutput( void* output ){ m_output = output; };
   	void Log(LPWSTR sz,...);

private:
	void* m_output;
};


class FilterGraphTools : public CLogMsg
{
public:
	HRESULT AddFilter(IGraphBuilder* piGraphBuilder, REFCLSID rclsid, IBaseFilter **ppiFilter, LPCWSTR pName, BOOL bSilent = FALSE);
	HRESULT AddFilterByName(IGraphBuilder* piGraphBuilder, IBaseFilter **ppiFilter, CLSID clsidDeviceClass, LPCWSTR friendlyName);
	HRESULT AddFilterByDevicePath(IGraphBuilder* piGraphBuilder, IBaseFilter **piFilter, LPCWSTR pDevicePath, LPCWSTR pName);
	HRESULT AddFilterByDevicePath2(IGraphBuilder* piGraphBuilder, IBaseFilter **piFilter, LPCWSTR pDevicePath, LPCWSTR pName);

	HRESULT EnumPins(IBaseFilter* piSource);

	HRESULT FindPin(IBaseFilter* piSource, LPCWSTR Id, IPin **ppiPin, REQUESTED_PIN_DIRECTION eRequestedPinDir = REQUESTED_PINDIR_ANY);
	HRESULT FindPinByMediaType(IBaseFilter* piSource, GUID majortype, GUID subtype, IPin **ppiPin, REQUESTED_PIN_DIRECTION eRequestedPinDir = REQUESTED_PINDIR_ANY);
	HRESULT FindFirstFreePin(IBaseFilter* piSource, IPin **ppiPin, PIN_DIRECTION pinDirection);
    HRESULT GetPinByMediaType(IBaseFilter* piSource, int nPinNum, REQUESTED_PIN_DIRECTION eRequestedPinDir, AM_MEDIA_TYPE *pMediaType  );

	HRESULT FindFilter(IGraphBuilder* piGraphBuilder, LPCWSTR Id, IBaseFilter **ppiFilter);
	HRESULT FindFilter(IGraphBuilder* piGraphBuilder, CLSID rclsid, IBaseFilter **ppiFilter);

	HRESULT EnumFilterNameFirst( CLSID clsidDeviceClass, IEnumMoniker **ppEnum, LPWSTR *pName  );
	HRESULT EnumFilterNameNext(  IEnumMoniker *pEnum, LPWSTR *pName  );
	void    EnumFilterNameClose( IEnumMoniker *pEnum );

	HRESULT EnumFilterPathFirst( CLSID clsidDeviceClass, IEnumMoniker **ppEnum, LPWSTR *pName  );
	HRESULT EnumFilterPathNext(  IEnumMoniker *pEnum, LPWSTR *pName  );
	void    EnumFilterPathClose( IEnumMoniker *pEnum );

	HRESULT EnumFilterFirst( CLSID clsidDeviceClass, IEnumMoniker **ppEnum, IBaseFilter **ppFilter  );
	HRESULT EnumFilterNext(  IEnumMoniker *pEnum, IBaseFilter **ppFilter  );
	void    EnumFilterClose( IEnumMoniker *pEnum );

	HRESULT ConnectFilters(IGraphBuilder* piGraphBuilder, IBaseFilter* piFilterUpstream, LPCWSTR sourcePinName, IBaseFilter* piFilterDownstream, LPCWSTR destPinName);
	HRESULT ConnectFilters(IGraphBuilder* piGraphBuilder, IBaseFilter* piFilterUpstream, IBaseFilter* piFilterDownstream);
	HRESULT ConnectFilters(IGraphBuilder* piGraphBuilder, IBaseFilter* piFilterUpstream, IPin* piPinDownstream);
	HRESULT ConnectFilters(IGraphBuilder* piGraphBuilder, IPin* piPinUpstream, IBaseFilter* piFilterDownstream);
	HRESULT ConnectFilters(IGraphBuilder* piGraphBuilder, IPin* piPinUpstream, IPin* piPinDownstream);
	HRESULT RenderPin     (IGraphBuilder* piGraphBuilder, IBaseFilter* piSource, LPCWSTR pinName);
	HRESULT DisconnectFilter(IGraphBuilder* piGraphBuilder, IBaseFilter *pFilter );
	
	HRESULT DisconnectOutputPin(IGraphBuilder* piGraphBuilder, IBaseFilter* pFilter );
	HRESULT DisconnectInputPin(IGraphBuilder* piGraphBuilder, IBaseFilter* pFilter );
	HRESULT DisconnectAllPins(IGraphBuilder* piGraphBuilder);
	HRESULT RemoveAllFilters(IGraphBuilder* piGraphBuilder);
	HRESULT RemoveFilter(IGraphBuilder* piGraphBuilder, IBaseFilter* pFilter);

	HRESULT GetOverlayMixer(IGraphBuilder* piGraphBuilder, IBaseFilter **ppiFilter);
	HRESULT GetOverlayMixerInputPin(IGraphBuilder* piGraphBuilder, LPCWSTR pinName, IPin **ppiPin);

	HRESULT AddToRot(IUnknown *pUnkGraph, DWORD *pdwRegister);
	void RemoveFromRot(DWORD pdwRegister);

	//BDA functions
	HRESULT InitTuningSpace(CComPtr <ITuningSpace> &piTuningSpace, TV_TYPE NetworkType );
	HRESULT CreateATSCTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
											long lMajorChannel, long lMinorChannel, long lPhysicalChannel, long lFreq );

	HRESULT CreateDVBTTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
										long lOid, long lSid, long lTtsid );
	HRESULT CreateDVBTTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
										long frequency, long bandwidth);
	HRESULT CreateDVBCTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
										long frequency, long rate, long modualtion, int innerFEC=-1, int innerFECRate=-1, int outerFEC=-1, int outerFECRate =-1 );
	HRESULT CreateDVBSTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
										long frequency, long rate, long Polarisation, long modualtion, int innerFEC=-1, int innerFECRate=-1, int outerFEC=-1, int outerFECRate=-1  );

    HRESULT FilterGraphTools::CreateQAMTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long lPhysicalChannel, unsigned long lFrequency, long lModulation );

	void static strCopy(LPSTR &dest, LPCSTR src, long length);
	void static strCopy(LPWSTR &dest, LPCWSTR src, long length);
	void static strCopyA2W(LPWSTR &dest, LPCSTR src, long length);
	void static strCopyW2A(LPSTR &dest, LPCWSTR src, long length);

	void static strCopy(LPSTR &dest, LPCSTR src);
	void static strCopy(LPWSTR &dest, LPCWSTR src);
	void static strCopyA2W(LPWSTR &dest, LPCSTR src);
	void static strCopyW2A(LPSTR &dest, LPCWSTR src);

	void static strCopy(LPSTR &dest, long value);
	void static strCopy(LPWSTR &dest, long value);


};

class DirectShowSystemDevice : public CLogMsg
{
public:
	DirectShowSystemDevice();
	virtual ~DirectShowSystemDevice();

	const DirectShowSystemDevice &operator=(const DirectShowSystemDevice &right);

	HRESULT CreateInstance(CComPtr <IBaseFilter> &pFilter);

	LPWSTR strFriendlyName;
	LPWSTR strDevicePath;
	BOOL bValid;
};

class DirectShowSystemDeviceEnumerator : public CLogMsg
{
public:
	DirectShowSystemDeviceEnumerator(REFCLSID deviceClass);
	virtual ~DirectShowSystemDeviceEnumerator();

	HRESULT Next(DirectShowSystemDevice** ppDevice);
	HRESULT Reset();

private:
	CComPtr <IEnumMoniker> m_pEnum;
	CComPtr <ICreateDevEnum> m_pSysDevEnum;
};



#endif
