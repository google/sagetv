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

#include "StdAfx.h"
#include <stdio.h>
#include <winerror.h>
#include <vector>
#include "DShowUtilities.h"

#include "FilterGraphTools.h"
#include "uniapi.h"


//I put into uuid.h ZQ
//OUR_GUID_ENTRY(CLSID_CABLENetworkProvider,
//0x143827AB, 0xF77B, 0x498d, 0x81, 0xCA, 0x5A, 0x00, 0x7A, 0xEC, 0x28, 0xBF)

void CLogMsg::Log(LPWSTR sz,...)
{
	if ( m_output == NULL )
		return;

   	if (wcslen(sz) <= 0)
    	return;
    va_list va;
    va_start(va, sz);

	unsigned long currLength = 1024;
	wchar_t *pStr = new wchar_t[currLength];

	int size;
	size = _vsnwprintf( pStr, currLength, sz, va);
	if (size < 0)
	{
		pStr[currLength] = '\0';

		while (size < 0)
		{
			currLength += 64;
			delete[] pStr;
			pStr = new wchar_t[currLength];
			size = _vsnwprintf(pStr, currLength, sz, va);
		}
	}
	*(pStr+size) = 0x0;

    va_end(va);

    if ( m_output )
    {
//#ifdef UNICODE
		   char* dest; 
		   int length;
		   length = wcslen(pStr);
		   dest = new char[length + 2];
		   wcstombs(dest, pStr, length);
		   dest[length] = 0x0;
		   slog( ((JNIEnv*)m_output, dest ) );
           delete dest;
//         
    }
    delete pStr;
}



HRESULT FilterGraphTools::AddFilter(IGraphBuilder* piGraphBuilder, REFCLSID rclsid, IBaseFilter **ppiFilter, LPCWSTR pName, BOOL bSilent)
{
	HRESULT hr;
	if ( pName == NULL || *pName == 0x0 )
		return E_FAIL;

	if FAILED(hr = CoCreateInstance(rclsid, NULL, CLSCTX_INPROC_SERVER, IID_IBaseFilter, reinterpret_cast<void**>(ppiFilter)))
	{
		if (!bSilent)
		{
			if (hr == 0x80040154)
				Log( L"Failed to load filter: %s error:%x, filter is not registered.", pName, hr ); 
			else
				Log( L"Failed to load filter: %s error:%x.", pName, hr ); 
		}
		return hr;
	}

	if FAILED(hr = piGraphBuilder->AddFilter(*ppiFilter, pName))
	{
		(*ppiFilter)->Release();
		if (!bSilent)
		{
		 	Log( L"Failed to add filter: %s error:%x.", pName, hr ); 
		}
		return hr;
	}
	return S_OK;
}

HRESULT FilterGraphTools::AddFilterByName(IGraphBuilder* piGraphBuilder, IBaseFilter **ppiFilter, CLSID clsidDeviceClass, LPCWSTR friendlyName)
{
	HRESULT hr = S_OK;
	CComPtr <IEnumMoniker> pEnum;
	CComPtr <ICreateDevEnum> pSysDevEnum;
	CComPtr <IMoniker> pMoniker;

	if FAILED(hr = pSysDevEnum.CoCreateInstance(CLSID_SystemDeviceEnum))
	{
        Log( L"AddFilterByName: Cannot CoCreate ICreateDevEnum\r\n" );
        return E_FAIL;
    }

	BOOL FilterAdded = FALSE;
	hr = pSysDevEnum->CreateClassEnumerator(clsidDeviceClass, &pEnum, 0);
	switch(hr)
	{
	case S_OK:
		while (pMoniker.Release(), pEnum->Next(1, &pMoniker, 0) == S_OK)
		{
			//Get friendly name
			CComVariant varBSTR;

			//Get filter PropertyBag
			CComPtr <IPropertyBag> pBag;
			if FAILED(hr = pMoniker->BindToStorage(NULL, NULL, IID_IPropertyBag, reinterpret_cast<void**>(&pBag)))
			{
				Log( L"AddFilterByName: Cannot BindToStorage\r\n");
				break;
			}

		    if FAILED(hr = pBag->Read(L"FriendlyName", &varBSTR, NULL))
			{
				Log( L"AddFilterByName: Failed to get name of filter\r\n" );
				continue;
			}

//			LPOLESTR pStr;
//			IBindCtx *pBindCtx;
//			hr = CreateBindCtx(0, &pBindCtx);

//			hr = pMoniker->GetDisplayName(pBindCtx, NULL, &pStr);

			DWORD hash = 0;
			hr = pMoniker->Hash(&hash);

			//Compare names
			CComVariant tmp = friendlyName;
			if(varBSTR.operator !=(tmp))
			{
				continue;
			}
	
			//Load filter
			if FAILED(hr = pMoniker->BindToObject(NULL, NULL, IID_IBaseFilter, reinterpret_cast<void**>(ppiFilter)))
			{
				Log( L"AddFilterByName: Cannot BindToObject\r\n" );
				break;
			}

			//Add filter to graph
			if FAILED(hr = piGraphBuilder->AddFilter(*ppiFilter, varBSTR.bstrVal))
			{
				Log( L"AddFilterByName: Failed to add Filter\r\n" );
				(*ppiFilter)->Release();
				return E_FAIL;
			}

			return S_OK;
		}
		break;
	case S_FALSE:
		Log( L"AddFilterByName: Failed to create System Device Enumerator\r\n");   
		return E_FAIL;
		break;
	case E_OUTOFMEMORY:
		Log( L"AddFilterByName: There is not enough memory available to create a class enumerator.\r\n");
		return E_FAIL;
		break;
	case E_POINTER:
		Log( L"AddFilterByName: Class Enumerator, NULL pointer argument\r\n" );
	  	return E_FAIL;
		break;
	}

	Log( L"AddFilterByName: Failed to find matching filter %s.\r\n", friendlyName );
	return E_FAIL;
}

HRESULT FilterGraphTools::AddFilterByDevicePath2(IGraphBuilder* piGraphBuilder, IBaseFilter **ppiFilter, LPCWSTR pDevicePath, LPCWSTR pName)
{
	HRESULT hr = S_OK; 
	//if ( (wcsstr( pDevicePath, L"@device:sw:" ) == NULL ) ) //ZQ
	//{
	//	return AddFilterByDevicePath( piGraphBuilder, ppiFilter, pDevicePath, pName );
	//}

    /* These tuners are a mess.  
    On Win-x64, their installers didn't make 32-bit entries for them in
    HKEY_CLASSES_ROOT\Wow6432Node\CLSID\{FD0A5AF4-B41D-11D2-9C95-00C04F7971E0}\Instance
    (BDA Receiver Components), nor are there entries in
    HKEY_CLASSES_ROOT\Wow6432Node\CLSID\{71985F48-1CA1-11d3-9CC8-00C04F7971E0}\Instance
    (BDA Source Filters), so Sage-x86 on 64-bit Windows doesn't encounter them.
    But they DO have 64-bit registry entries for BDA Receiver Components & BDA Source Filters
    in  HKEY_CLASSES_ROOT\CLSID\{FD0A5AF4-B41D-11D2-9C95-00C04F7971E0}\Instance
    and HKEY_CLASSES_ROOT\CLSID\{71985F48-1CA1-11d3-9CC8-00C04F7971E0}\Instance
    so Sage-x64 does see these.  When we find these filter keys, it takes nearly 30 seconds
    on every attempt to add the filter.  Since we don't natively handle CableCARD
    tuners anyway (requires a *DCT shim), the best solution is to ignore them here.
    */
    if ((wcsncmp(pName, L"HDHomeRun Prime Tuner", 21) == 0) ||
        (wcsncmp(pName, L"Ceton InfiniTV PCIe", 19) == 0))
    {
        Log(L"Hardcode: ignore CableCARD tuner '%s' \r\n", pName);
        return (E_FAIL);
    }

	//software device
	hr = AddFilterByDevicePath( piGraphBuilder, ppiFilter, pDevicePath, pName );
	if ( hr != S_OK )
	{
		//some of device don't have devicepath, we have to use class id and friend name to create a filter
		CLSID clsid;   //HDHR F%@#$ 
		wchar_t CLSIDString[64]={0};
		const wchar_t *p, *pe, *pNewName; 
		p = wcschr( pDevicePath, L'{' );
		pe = wcsstr( pDevicePath, L"}\\" );
		wcsncpy( CLSIDString, p, pe-p+1 );
		pNewName = pe + 2;
		hr = CLSIDFromString( CLSIDString, &clsid  );
		if ( !FAILED( hr ) )
		{
			Log( L"Add Filter by classid:%s name:'%s'(sw or stream filter)\r\n", CLSIDString, pNewName );
			hr = AddFilterByName( piGraphBuilder, ppiFilter, clsid,  pNewName );
			if ( FAILED( hr ) )
			{
				Log( L"Add Filter by classid:%s name:'%s'(sw or stream filter)\r\n", CLSIDString, pName );
				hr = AddFilterByName( piGraphBuilder, ppiFilter, clsid,  pName );
			}
		}
	}
	return hr;
}


HRESULT FilterGraphTools::AddFilterByDevicePath(IGraphBuilder* piGraphBuilder, IBaseFilter **ppiFilter, LPCWSTR pDevicePath, LPCWSTR pName)
{
	HRESULT hr;
	CComPtr <IBindCtx> pBindCtx;
	CComPtr <IMoniker> pMoniker;
	DWORD dwEaten;

	if FAILED(hr = CreateBindCtx(0, &pBindCtx))
	{
		Log( L"AddFilterByDevicePath: Could not create bind context\n" );;
		return hr;
	}

	if (FAILED(hr = MkParseDisplayName(pBindCtx, pDevicePath, &dwEaten, &pMoniker)) || (pMoniker == NULL))
	{
		Log( L"AddFilterByDevicePath: Could not create moniker from device path '%s' '%s'\r\n",pDevicePath, pName ); 
		return hr;
	}

	LPWSTR pGraphName = NULL;
	if (pName != NULL)
	{
		strCopy(pGraphName, pName);
	}
	else
	{
		strCopy(pGraphName, pDevicePath);
	}

	if FAILED(hr = pMoniker->BindToObject(0, 0, IID_IBaseFilter, reinterpret_cast<void**>(ppiFilter)))
	{
		Log( L"Could Not Create Filter: %s", pName );
		SAFE_DELETE( pGraphName );
		return hr;
	}

	if FAILED(hr = piGraphBuilder->AddFilter(*ppiFilter, pGraphName))
	{
		(*ppiFilter)->Release();
		Log( L"Failed to add filter: %s\r\n", pName );
		SAFE_DELETE( pGraphName );
		return hr;
	}
	SAFE_DELETE( pGraphName );
	return S_OK;
} 
HRESULT FilterGraphTools::EnumFilterNameFirst( CLSID clsidDeviceClass, IEnumMoniker **ppEnum, LPWSTR *pName  )
{
	HRESULT hr = S_OK;
	CComPtr <IEnumMoniker> pEnum = NULL;
	CComPtr <ICreateDevEnum> pSysDevEnum;
	CComPtr <IMoniker> pMoniker;

	*ppEnum = NULL;
	if FAILED(hr = pSysDevEnum.CoCreateInstance(CLSID_SystemDeviceEnum))
	{
        Log( L"AddFilterByName: Cannot CoCreate ICreateDevEnum\n" );
        return E_FAIL;
    }

	BOOL FilterAdded = FALSE;
	hr = pSysDevEnum->CreateClassEnumerator(clsidDeviceClass, &pEnum, 0);
	if ( FAILED(hr) || pEnum == NULL )
		return hr;

	if ( ( hr = pEnum->Next(1, &pMoniker, 0) ) == S_OK )
	{
		//Get friendly name
		CComVariant varBSTR;

		//Get filter PropertyBag
		CComPtr <IPropertyBag> pBag;
		if FAILED(hr = pMoniker->BindToStorage(NULL, NULL, IID_IPropertyBag, reinterpret_cast<void**>(&pBag)))
		{
			Log( L"Cannot BindToStorage\n");
			return hr;
		}

	    if FAILED(hr = pBag->Read(L"FriendlyName", &varBSTR, NULL))
		{
			Log( L"Cannot Read FriendlyName\n");
			return hr;
		}

		/*
		int length = varBSTR.GetSize();
		*pName = new wchar_t[ length + 1]; 
		memset( *pName, 0x0, sizeof(length + 1) );
		wcsncpy( *pName, varBSTR.bstrVal, length );
		*(*pName+length) = 0x0;
		*/

		int length = wcslen( varBSTR.bstrVal );
		*pName = new wchar_t[length + 1]; 
		wcsncpy( *pName, varBSTR.bstrVal, length );
		*(*pName+length) = 0x0;

		*ppEnum = pEnum;
		(*ppEnum)->AddRef();


		return S_OK;
	}

	return hr;
}

HRESULT FilterGraphTools::EnumFilterNameNext(  IEnumMoniker *pEnum, LPWSTR *pName  )
{
	HRESULT hr = S_OK;
	if ( pEnum == NULL )
        return E_FAIL;

	CComPtr <IMoniker> pMoniker;
	if ( ( hr = pEnum->Next(1, &pMoniker, 0) ) == S_OK )
	{
		//Get friendly name
		CComVariant varBSTR;

		//Get filter PropertyBag
		CComPtr <IPropertyBag> pBag;
		if FAILED(hr = pMoniker->BindToStorage(NULL, NULL, IID_IPropertyBag, reinterpret_cast<void**>(&pBag)))
		{
			Log( L"Cannot BindToStorage\n");
			return hr;
		}

	    if FAILED(hr = pBag->Read(L"FriendlyName", &varBSTR, NULL))
		{
			Log( L"Cannot Read FriendlyName\n");
			return hr;
		}

		int length = wcslen( varBSTR.bstrVal );
		*pName = new wchar_t[length + 1];
		wcsncpy( *pName, varBSTR.bstrVal, length );
		*(*pName+length) = 0x0;


		return S_OK;
	}
	return hr;
}

void FilterGraphTools::EnumFilterNameClose( IEnumMoniker *pEnum )
{
	if ( pEnum != NULL )
		pEnum->Release();
}


HRESULT FilterGraphTools::EnumFilterPathFirst( CLSID clsidDeviceClass, IEnumMoniker **ppEnum, LPWSTR *pName  )
{
	HRESULT hr = S_OK;
	CComPtr <IEnumMoniker> pEnum;
	CComPtr <ICreateDevEnum> pSysDevEnum;
	CComPtr <IMoniker> pMoniker;

	*ppEnum = NULL;
	if FAILED(hr = pSysDevEnum.CoCreateInstance(CLSID_SystemDeviceEnum))
	{
        Log( L"AddFilterByName: Cannot CoCreate ICreateDevEnum\n" );
        return E_FAIL;
    }

	BOOL FilterAdded = FALSE;
	hr = pSysDevEnum->CreateClassEnumerator(clsidDeviceClass, &pEnum, 0);
	if ( FAILED(hr)  || pEnum == NULL )
		return hr;

	if ( ( hr = pEnum->Next(1, &pMoniker, 0) ) == S_OK )
	{

		*ppEnum = pEnum;
		(*ppEnum)->AddRef();

		LPOLESTR pStr;
		IBindCtx *pBindCtx;
		hr = CreateBindCtx(0, &pBindCtx);
		hr = pMoniker->GetDisplayName(pBindCtx, NULL, &pStr);

		int length = wcslen( pStr );
		*pName = new wchar_t[length + 1];
		wcsncpy( *pName, pStr, length );
		*(*pName+length) = 0x0;
		CoTaskMemFree(pStr);
		pBindCtx->Release();
		return S_OK;
	}

	return hr;
}

HRESULT FilterGraphTools::EnumFilterPathNext(  IEnumMoniker *pEnum, LPWSTR *pName  )
{
	HRESULT hr = S_OK;
	if ( pEnum == NULL )
        return E_FAIL;

	CComPtr <IMoniker> pMoniker;
	if ( ( hr = pEnum->Next(1, &pMoniker, 0) ) == S_OK )
	{

		LPOLESTR pStr;
		IBindCtx *pBindCtx;
		hr = CreateBindCtx(0, &pBindCtx);
		hr = pMoniker->GetDisplayName(pBindCtx, NULL, &pStr);

		int length = wcslen( pStr );
		*pName = new wchar_t[length + 1];
		wcsncpy( *pName, pStr, length );
		*(*pName+length) = 0x0;
		pBindCtx->Release();
		CoTaskMemFree(pStr);
		return S_OK;
	}
	return hr;
}

void FilterGraphTools::EnumFilterPathClose( IEnumMoniker *pEnum )
{
	if ( pEnum != NULL )
		pEnum->Release();
}

HRESULT FilterGraphTools::EnumFilterFirst( CLSID clsidDeviceClass, IEnumMoniker **ppEnum, IBaseFilter **ppFilter )
{
	HRESULT hr = S_OK;
	CComPtr <IEnumMoniker> pEnum = NULL;
	CComPtr <ICreateDevEnum> pSysDevEnum;
	CComPtr <IMoniker> pMoniker;

	*ppEnum = NULL;
	if FAILED(hr = pSysDevEnum.CoCreateInstance(CLSID_SystemDeviceEnum))
	{
        Log( L"AddFilterByName: Cannot CoCreate ICreateDevEnum\n" );
        return E_FAIL;
    }

	BOOL FilterAdded = FALSE;
	hr = pSysDevEnum->CreateClassEnumerator(clsidDeviceClass, &pEnum, 0);
	if ( FAILED(hr) || pEnum == NULL )
		return hr;

	if ( ( hr = pEnum->Next(1, &pMoniker, 0) ) == S_OK )
	{
	    hr = pMoniker->BindToObject(NULL, NULL, IID_IBaseFilter,	(void**)ppFilter);
		if FAILED( hr )
		{
			Log( L"Cannot BindToObject\n");
			return hr;
		}

		*ppEnum = pEnum;
		(*ppEnum)->AddRef();
		(*ppFilter)->AddRef();
		return S_OK;
	}

	return hr;
}

HRESULT FilterGraphTools::EnumFilterNext(  IEnumMoniker *pEnum, IBaseFilter** ppFilter  )
{
	HRESULT hr = S_OK;
	if ( pEnum == NULL )
        return E_FAIL;

	CComPtr <IMoniker> pMoniker;
	if ( ( hr = pEnum->Next(1, &pMoniker, 0) ) == S_OK )
	{
	    hr = pMoniker->BindToObject(NULL, NULL, IID_IBaseFilter,	(void**)ppFilter);
		if FAILED( hr )
		{
			Log( L"Cannot BindToObject\n");
			return hr;
		}
		return S_OK;
	}
	return hr;
}

void FilterGraphTools::EnumFilterClose( IEnumMoniker *pEnum )
{
	if ( pEnum != NULL )
		pEnum->Release();
}


HRESULT FilterGraphTools::EnumPins(IBaseFilter* piSource)
{
	HRESULT hr;
	CComPtr <IEnumPins> piEnumPins;

	if SUCCEEDED(hr = piSource->EnumPins( &piEnumPins ))
	{
		char* string = (char*)malloc(2048);
		LPOLESTR str = (LPOLESTR)malloc(128);

		CComPtr <IPin> piPin;
		while (piPin.Release(), piEnumPins->Next(1, &piPin, 0) == NOERROR )
		{
			string[0] = '\0';

			PIN_INFO pinInfo;
			piPin->QueryPinInfo(&pinInfo);
			if (pinInfo.pFilter)
				pinInfo.pFilter->Release();	//QueryPinInfo adds a reference to the filter.

			if (pinInfo.dir == PINDIR_INPUT)
				SPRINTF(string, 2048, "Input Pin Name: %S\n", pinInfo.achName);
			else if (pinInfo.dir == PINDIR_OUTPUT)
				SPRINTF(string, 2048, "Output Pin Name: %S\n", pinInfo.achName); 
			else
				SPRINTF(string, 2048, "Pin Name: %S\n", pinInfo.achName); 


			CComPtr <IEnumMediaTypes> piMediaTypes;
			hr = piPin->EnumMediaTypes(&piMediaTypes);
			if (hr == S_OK)
			{
				AM_MEDIA_TYPE *mediaType;
				while (piMediaTypes->Next(1, &mediaType, 0) == NOERROR)
				{
					StringFromGUID2(mediaType->majortype, str, 127);
					SPRINTF(string, 2048, "%s  MajorType: %S\n", string, str);

					StringFromGUID2(mediaType->subtype, str, 127);
					SPRINTF(string, 2048, "%s  SubType: %S\n", string, str);
					if (mediaType->bFixedSizeSamples)
						SPRINTF(string, 2048, "%s  Fixed Sized Samples\n", string);
					else
						SPRINTF(string, 2048, "%s  Not Fixed Sized Samples\n", string);

					if (mediaType->bTemporalCompression)
						SPRINTF(string, 2048, "%s  Temporal Compression\n", string);
					else
						SPRINTF(string, 2048, "%s  Not Temporal Compression\n", string);
					StringFromGUID2(mediaType->formattype, str, 127);
					SPRINTF(string, 2048, "%s  Format Type: %S\n\n", string, str);
				}
			}
			Log( L"%s", string );
		}
		free( string );
		free( str );
	}
	return hr;
}

HRESULT FilterGraphTools::FindPin(IBaseFilter* piSource, LPCWSTR Id, IPin **ppiPin, REQUESTED_PIN_DIRECTION eRequestedPinDir)
{
	*ppiPin = NULL;

	if (piSource == NULL)
		return E_FAIL;
	HRESULT hr;
	CComPtr <IEnumPins> piEnumPins;
	
	if SUCCEEDED(hr = piSource->EnumPins( &piEnumPins ))
	{
		CComPtr <IPin> piPins;
		while (piPins.Release(), piEnumPins->Next(1, &piPins, 0) == NOERROR )
		{
			PIN_INFO pinInfo;
			hr = piPins->QueryPinInfo(&pinInfo);
			if (pinInfo.pFilter)
				pinInfo.pFilter->Release();	//QueryPinInfo adds a reference to the filter.
			if (wcscmp(Id, pinInfo.achName) == 0)
			{
				if ((eRequestedPinDir == REQUESTED_PINDIR_ANY) || (eRequestedPinDir == pinInfo.dir))
				{
					*ppiPin = piPins;
					(*ppiPin)->AddRef();
					return S_OK;
				}
			}
		}
	}
	if (hr == S_OK)
		return E_FAIL;
	return hr;
}

HRESULT FilterGraphTools::FindPinByMediaType(IBaseFilter* piSource, GUID majortype, GUID subtype, IPin **ppiPin, REQUESTED_PIN_DIRECTION eRequestedPinDir)
{
	*ppiPin = NULL;

	if (piSource == NULL)
		return E_FAIL;
	HRESULT hr;
	CComPtr <IEnumPins> piEnumPins;

	if SUCCEEDED(hr = piSource->EnumPins( &piEnumPins ))
	{
		CComPtr <IPin> piPins;
		while (piPins.Release(), piEnumPins->Next(1, &piPins, 0) == NOERROR )
		{
			PIN_INFO pinInfo;
			hr = piPins->QueryPinInfo(&pinInfo);
			if (pinInfo.pFilter)
			{
				pinInfo.pFilter->Release();	//QueryPinInfo adds a reference to the filter.
			}
			if ((eRequestedPinDir == REQUESTED_PINDIR_ANY) || (eRequestedPinDir == pinInfo.dir))
			{
				CComPtr <IEnumMediaTypes> piMediaTypes;
				if SUCCEEDED(hr = piPins->EnumMediaTypes(&piMediaTypes))
				{
					AM_MEDIA_TYPE *mediaType;
					while (piMediaTypes->Next(1, &mediaType, 0) == NOERROR)
					{
/*{
	LPOLESTR major_clsid, subtype;
	StringFromCLSID( mediaType->majortype, &major_clsid );
	StringFromCLSID( mediaType->subtype, &subtype );
	Log( L">>>>Pin media type:%s subtype:%s\r\n", major_clsid, subtype );
    CoTaskMemFree(major_clsid);
	CoTaskMemFree(subtype);
}*/

						if ((mediaType->majortype == majortype) &&
							(mediaType->subtype == subtype || subtype == GUID_NULL ))
						{
							*ppiPin = piPins;
							(*ppiPin)->AddRef();
							DeleteMediaType(mediaType);
							return S_OK;
						}
						DeleteMediaType(mediaType);
					}
				}
			}
		}
	}
	if (hr == S_OK)
		return E_FAIL;
	return hr;
}

HRESULT FilterGraphTools::FindFirstFreePin(IBaseFilter* piSource, IPin **ppiPin, PIN_DIRECTION pinDirection)
{
	*ppiPin = NULL;

	if (piSource == NULL)
		return E_FAIL;
	HRESULT hr;
	CComPtr <IEnumPins> piEnumPins;

	if SUCCEEDED(hr = piSource->EnumPins( &piEnumPins ))
	{
		CComPtr <IPin> piPins;
		while (piPins.Release(), piEnumPins->Next(1, &piPins, 0) == NOERROR )
		{
			PIN_INFO pinInfo;
			hr = piPins->QueryPinInfo(&pinInfo);
			if (pinInfo.pFilter)
				pinInfo.pFilter->Release();	//QueryPinInfo adds a reference to the filter.
			if (pinInfo.dir == pinDirection)
			{
				//Check if pin is already connected
				CComPtr <IPin> pOtherPin;
				hr = piPins->ConnectedTo(&pOtherPin);
				if (FAILED(hr) && (hr != VFW_E_NOT_CONNECTED))
				{
					Log( L"Failed to Determin if Pin is already connected\n" );
					return E_FAIL;
				}
				if (pOtherPin == NULL)
				{
					*ppiPin = piPins;
					(*ppiPin)->AddRef();
					return S_OK;
				}
			}
		}
	}
	if (hr == S_OK)
		return E_FAIL;
	return hr;
}


HRESULT FilterGraphTools::GetPinByMediaType(IBaseFilter* piSource, int nPinNum, REQUESTED_PIN_DIRECTION eRequestedPinDir, AM_MEDIA_TYPE *pMediaType  )
{
	if (piSource == NULL)
		return E_FAIL;
	HRESULT hr;
	CComPtr <IEnumPins> piEnumPins;

	if SUCCEEDED(hr = piSource->EnumPins( &piEnumPins ))
	{
		CComPtr <IPin> piPins;
		int i = 0;
		while (piPins.Release(), piEnumPins->Next(1, &piPins, 0) == NOERROR )
		{
			PIN_INFO pinInfo;
		
			hr = piPins->QueryPinInfo(&pinInfo);
			if (pinInfo.pFilter)
			{
				pinInfo.pFilter->Release();	//QueryPinInfo adds a reference to the filter.
			}

			if ( (eRequestedPinDir == REQUESTED_PINDIR_ANY) || (eRequestedPinDir == pinInfo.dir ) )
			{
				if ( i++ == nPinNum ) 
				{
					CComPtr <IEnumMediaTypes> piMediaTypes;
					if SUCCEEDED(hr = piPins->EnumMediaTypes(&piMediaTypes))
					{
						AM_MEDIA_TYPE *mediaType;
						memset( pMediaType, 0, sizeof(AM_MEDIA_TYPE) );
						while (piMediaTypes->Next(1, &mediaType, 0) == NOERROR)
						{
							*pMediaType = *mediaType;
							DeleteMediaType(mediaType);
							return S_OK;
						}
						return S_OK;
					}
				}
			}
		}
	}
	return E_FAIL;
}

HRESULT FilterGraphTools::FindFilter(IGraphBuilder* piGraphBuilder, LPCWSTR Id, IBaseFilter **ppiFilter)
{
	*ppiFilter = NULL;
	if (piGraphBuilder == NULL)
		return E_FAIL;
	HRESULT hr;
	CComPtr <IEnumFilters> piEnumFilters;
	
	if SUCCEEDED(hr = piGraphBuilder->EnumFilters(&piEnumFilters))
	{
		CComPtr <IBaseFilter> piFilter;
		while (piFilter.Release(), piEnumFilters->Next(1, &piFilter, 0) == NOERROR )
		{
			FILTER_INFO filterInfo;
			ZeroMemory(&filterInfo, sizeof(FILTER_INFO));
			hr = piFilter->QueryFilterInfo(&filterInfo);
			if (SUCCEEDED(hr))
			{
				SAFE_RELEASE(filterInfo.pGraph);
				if (wcscmp(Id, filterInfo.achName) == 0)
				{
					*ppiFilter = piFilter;
					(*ppiFilter)->AddRef();
					return S_OK;
				}
			}
		}
	}
	if (hr == S_OK)
		return E_FAIL;
	return hr;
}

HRESULT FilterGraphTools::FindFilter(IGraphBuilder* piGraphBuilder, CLSID rclsid, IBaseFilter **ppiFilter)
{
	*ppiFilter = NULL;
	if (piGraphBuilder == NULL)
		return E_FAIL;
	HRESULT hr;

	CComPtr <IEnumFilters> piEnumFilters;
	
	if SUCCEEDED(hr = piGraphBuilder->EnumFilters(&piEnumFilters))
	{
		CComPtr <IBaseFilter> piFilter;
		while (piFilter.Release(), piEnumFilters->Next(1, &piFilter, 0) == NOERROR )
		{
			CLSID clsid = GUID_NULL;
			piFilter->GetClassID(&clsid);

			if (IsEqualCLSID(rclsid, clsid))
			{
				*ppiFilter = piFilter;
				(*ppiFilter)->AddRef();
				return S_OK;
			}
		}
	}
	if (hr == S_OK)
		return E_FAIL;
	return hr;
}

HRESULT FilterGraphTools::ConnectFilters(IGraphBuilder* piGraphBuilder, IBaseFilter* piFilterUpstream, LPCWSTR sourcePinName, IBaseFilter* piFilterDownstream, LPCWSTR destPinName)
{
	if (piFilterUpstream == NULL)
	{
		Log( L"ConnectPins: piFilterUpstream pointer is null\n" );
		return E_FAIL;
	}
	if (piFilterDownstream == NULL)
	{
		Log( L"ConnectPins: piFilterDownstream pointer is null\n" );
		return E_FAIL;
	}
	HRESULT hr;
	CComPtr <IPin> piOutput;
	CComPtr <IPin> piInput;

	if (S_OK != (hr = FindPin(piFilterUpstream, sourcePinName, &piOutput, REQUESTED_PINDIR_OUTPUT)))
	{
		Log( L"ConnectPins: Failed to find output pin named %s\r\n", sourcePinName );
		return E_FAIL;
	}

	if (S_OK != (hr = FindPin(piFilterDownstream, destPinName, &piInput, REQUESTED_PINDIR_INPUT)))
	{
		Log( L"ConnectPins: Failed to find input pin named %s\r\n", destPinName );
		return E_FAIL;
	}

	hr = ConnectFilters(piGraphBuilder, piOutput, piInput);

	return hr;
}

HRESULT FilterGraphTools::ConnectFilters(IGraphBuilder* piGraphBuilder, IBaseFilter* piFilterUpstream, IBaseFilter* piFilterDownstream)
{
    HRESULT hr = S_OK;
	int ConnectedPins = 0;


    CComPtr <IEnumPins> piEnumPinsUpstream;
    if FAILED(hr = piFilterUpstream->EnumPins(&piEnumPinsUpstream))
	{
        Log( L"Cannot Enumerate Pins on Upstream Filter\n" );
        return hr;
    }

    CComPtr <IPin> piPinUpstream;
    while (piPinUpstream.Release(), piEnumPinsUpstream->Next(1, &piPinUpstream, 0) == S_OK )
	{
	    PIN_INFO PinInfoUpstream;
        if FAILED(hr = piPinUpstream->QueryPinInfo (&PinInfoUpstream))
		{
            Log( L"Cannot Obtain Pin Info for Upstream Filter\n");
            return hr;
        }
		//QueryPinInfo increases the reference count on pFilter, so release it.
		if (PinInfoUpstream.pFilter != NULL)
			PinInfoUpstream.pFilter->Release();

		//Check if pin is an Output pin
		if (PinInfoUpstream.dir != PINDIR_OUTPUT)
		{
			continue;
		}


		//Check if pin is already connected
        CComPtr <IPin> pPinDown;
		hr = piPinUpstream->ConnectedTo(&pPinDown);
		if (FAILED(hr) && (hr != VFW_E_NOT_CONNECTED))
		{
			Log( L"Failed to Determin if Upstream Ouput Pin is already connected\n" );
            return hr;
		}
		if (pPinDown != NULL)
		{
			continue;
		}



		CComPtr <IEnumPins> pIEnumPinsDownstream;
		if FAILED(hr = piFilterDownstream->EnumPins(&pIEnumPinsDownstream))
		{
			Log( L"Cannot enumerate pins on downstream filter!\n" );
            return hr;
			//return E_FAIL;
		}

		CComPtr <IPin> piPinDownstream;
		while (piPinDownstream.Release(), pIEnumPinsDownstream->Next (1, &piPinDownstream, 0) == S_OK )
		{

		    PIN_INFO PinInfoDownstream;
			if FAILED(hr = piPinDownstream->QueryPinInfo(&PinInfoDownstream))
			{
				Log( L"Cannot Obtain Pin Info for Downstream Filter\n");
                return hr;
			}
			//QueryPinInfo increases the reference count on pFilter, so release it.
			if (PinInfoDownstream.pFilter != NULL)
				PinInfoDownstream.pFilter->Release();

			//Check if pin is an Input pin
			if (PinInfoDownstream.dir != PINDIR_INPUT)
			{
				continue;
			}

			//Check if pin is already connected
			CComPtr <IPin>  pPinUp;
			hr = piPinDownstream->ConnectedTo(&pPinUp);
			if (FAILED(hr) && (hr != VFW_E_NOT_CONNECTED))
			{
				Log( L"Failed to Find if Downstream Input Pin is already connected\r\n" );
                return hr;
			}
			if (pPinUp != NULL)
			{
				continue;
			}


			//Connect pins
			if SUCCEEDED(hr = ConnectFilters(piGraphBuilder, piPinUpstream, piPinDownstream))
			{
				ConnectedPins++;
			}

		}
    }


	if ( ConnectedPins == 0 )// FAILED(hr) )
		return E_FAIL;
    return S_OK;
}

HRESULT FilterGraphTools::ConnectFilters(IGraphBuilder* piGraphBuilder, IBaseFilter* piFilterUpstream, IPin* piPinDownstream)
{
    HRESULT hr = S_OK;

    CComPtr <IEnumPins> piEnumPinsUpstream;
    if FAILED(hr = piFilterUpstream->EnumPins(&piEnumPinsUpstream))
	{
        Log( L"Cannot Enumerate Pins on Upstream Filter\n");
        return hr;
    }

    CComPtr <IPin> piPinUpstream;
    while (piPinUpstream.Release(), piEnumPinsUpstream->Next(1, &piPinUpstream, 0) == S_OK)
	{
	    PIN_INFO PinInfoUpstream;
        if FAILED(hr = piPinUpstream->QueryPinInfo (&PinInfoUpstream))
		{
            Log( L"Cannot Obtain Pin Info for Upstream Filter\n" );
            return hr;
        }
		//QueryPinInfo increases the reference count on pFilter, so release it.
		if (PinInfoUpstream.pFilter != NULL)
			PinInfoUpstream.pFilter->Release();

		//Check if pin is an Output pin
		if (PinInfoUpstream.dir != PINDIR_OUTPUT)
		{
			continue;
		}

		//Check if pin is already connected
        CComPtr <IPin> pPinDown;
		hr = piPinUpstream->ConnectedTo(&pPinDown);
		if (FAILED(hr) && (hr != VFW_E_NOT_CONNECTED))
		{
			Log( L"Failed to Determin if Upstream Ouput Pin is already connected\n");
            return hr;
		}
		if (pPinDown != NULL)
		{
			continue;
		}

		if SUCCEEDED(hr = ConnectFilters(piGraphBuilder, piPinUpstream, piPinDownstream))
		{
			return S_OK;
		}

		piPinUpstream.Release();
    }
	if (hr == S_OK)
		return E_FAIL;
    return hr;
}

HRESULT FilterGraphTools::ConnectFilters(IGraphBuilder* piGraphBuilder, IPin* piPinUpstream, IBaseFilter* piFilterDownstream)
{
    HRESULT hr = S_OK;

	CComPtr <IEnumPins> pIEnumPinsDownstream;
	if FAILED(hr = piFilterDownstream->EnumPins(&pIEnumPinsDownstream))
	{
		Log( L"Cannot enumerate pins on downstream filter!\n" );
		return E_FAIL;
	}

	CComPtr <IPin> piPinDownstream;
	while (piPinDownstream.Release(), pIEnumPinsDownstream->Next (1, &piPinDownstream, 0) == S_OK)
	{
	    PIN_INFO PinInfoDownstream;
		if FAILED(hr = piPinDownstream->QueryPinInfo(&PinInfoDownstream))
		{
			Log( L"Cannot Obtain Pin Info for Downstream Filter\n");
            return hr;
		}
		//QueryPinInfo increases the reference count on pFilter, so release it.
		if (PinInfoDownstream.pFilter != NULL)
			PinInfoDownstream.pFilter->Release();

		//Check if pin is an Input pin
		if (PinInfoDownstream.dir != PINDIR_INPUT)
		{
			continue;
		}

		//Check if pin is already connected
		CComPtr <IPin>  pPinUp;
		hr = piPinDownstream->ConnectedTo(&pPinUp);
		if (FAILED(hr) && (hr != VFW_E_NOT_CONNECTED))
		{
			Log( L"Failed to Find if Downstream Input Pin is already connected\r\n");
            return hr;
		}
		if (pPinUp != NULL)
		{
			continue;
		}

		//Connect pins
		if SUCCEEDED(hr = ConnectFilters(piGraphBuilder, piPinUpstream, piPinDownstream))
		{
			return S_OK;
		}
		piPinDownstream.Release();
	}

	if (hr == S_OK)
		return E_FAIL;
    return hr;
}

HRESULT FilterGraphTools::ConnectFilters(IGraphBuilder* piGraphBuilder, IPin* piPinUpstream, IPin* piPinDownstream)
{
	HRESULT hr = piGraphBuilder->ConnectDirect(piPinUpstream, piPinDownstream, NULL);

	if SUCCEEDED(hr)
	{
		::OutputDebugString("Pin connection - SUCCEEDED\n");
	}
	else
	{
		::OutputDebugString("Pin connection - FAILED !!!\n");
	}

	return hr;
}

HRESULT FilterGraphTools::RenderPin(IGraphBuilder* piGraphBuilder, IBaseFilter* piSource, LPCWSTR pinName)
{
	HRESULT hr = S_OK;

	CComPtr <IPin> piPin;
	if (S_OK != (hr = FindPin(piSource, pinName, &piPin, REQUESTED_PINDIR_OUTPUT)))
		return hr;

	return piGraphBuilder->Render(piPin);
}

HRESULT FilterGraphTools::DisconnectOutputPin(IGraphBuilder* piGraphBuilder, IBaseFilter* pFilter )
{
	// Disconnect all the output pins on the capture filter
	IEnumPins* pPinEnum = NULL;
	HRESULT hr = pFilter->EnumPins(&pPinEnum);
	if (SUCCEEDED(hr))
	{
		IPin* pPin = NULL;
		while (S_OK == pPinEnum->Next(1, &pPin, NULL))
		{
			PIN_DIRECTION pDir;
			hr = pPin->QueryDirection(&pDir);
			if (SUCCEEDED(hr) && pDir == PINDIR_OUTPUT)
			{
				//piGraphBuilder->Disconnect(pPin);
				hr = pPin->Disconnect();
			}
			SAFE_RELEASE(pPin);
		}
		SAFE_RELEASE(pPinEnum);
	}
	return hr;
}

HRESULT FilterGraphTools::DisconnectInputPin(IGraphBuilder* piGraphBuilder, IBaseFilter* pFilter )
{
	// Disconnect all the output pins on the capture filter
	IEnumPins* pPinEnum = NULL;
	HRESULT hr = pFilter->EnumPins(&pPinEnum);
	if (SUCCEEDED(hr))
	{
		IPin* pPin = NULL;
		while (S_OK == pPinEnum->Next(1, &pPin, NULL))
		{
			PIN_DIRECTION pDir;
			hr = pPin->QueryDirection(&pDir);
			if (SUCCEEDED(hr) && pDir == PINDIR_INPUT)
			{
				hr = pPin->Disconnect();
				//pPin->Release();
				//piGraphBuilder->Disconnect(pPin);
			}
			SAFE_RELEASE(pPin);
		}
		SAFE_RELEASE(pPinEnum);
	}
	return hr;
}

HRESULT FilterGraphTools::DisconnectAllPins(IGraphBuilder* piGraphBuilder)
{
	if (piGraphBuilder == NULL)
		return E_FAIL;
	HRESULT hr;
	CComPtr <IEnumFilters> piEnumFilters;
	hr = piGraphBuilder->EnumFilters(&piEnumFilters);
	if SUCCEEDED(hr)
	{
		CComPtr <IBaseFilter> piFilter;
		while (piFilter.Release(), piEnumFilters->Next(1, &piFilter, 0) == NOERROR )
		{
			CComPtr <IEnumPins> piEnumPins;
			hr = piFilter->EnumPins( &piEnumPins );
			if SUCCEEDED(hr)
			{
				CComPtr <IPin> piPin;
				while (piPin.Release(), piEnumPins->Next(1, &piPin, 0) == NOERROR )
				{
					hr = piPin->Disconnect();
					if (hr == VFW_E_NOT_STOPPED)
						Log( L"Could not disconnect pin. The filter is active\n" );
				}
			}
		}
	}
	return hr;
}

HRESULT FilterGraphTools::DisconnectFilter(IGraphBuilder* piGraphBuilder, IBaseFilter *pFilter )
{
	if ( piGraphBuilder == NULL || pFilter == NULL )
		return E_FAIL;
	HRESULT hr;
	CComPtr <IEnumPins> piEnumPins;
	hr = pFilter->EnumPins( &piEnumPins );
	if SUCCEEDED(hr)
	{
		CComPtr <IPin> piPin;
		while (piPin.Release(), piEnumPins->Next(1, &piPin, 0) == NOERROR )
		{
			hr = piPin->Disconnect();
			if (hr == VFW_E_NOT_STOPPED)
				Log( L"Could not disconnect pin. The filter is active\n" );
		}
	
	}
	return hr;
}

HRESULT FilterGraphTools::RemoveFilter(IGraphBuilder* piGraphBuilder, IBaseFilter* pFilter)
{
	return piGraphBuilder->RemoveFilter(pFilter);
}

HRESULT FilterGraphTools::RemoveAllFilters(IGraphBuilder* piGraphBuilder)
{
	if (piGraphBuilder == NULL)
		return E_FAIL;
	HRESULT hr;

	CComPtr <IEnumFilters> piEnumFilters;
	hr = piGraphBuilder->EnumFilters(&piEnumFilters);
	if ((hr != S_OK) || (piEnumFilters == NULL))
	{
		Log( L"Error removing filters. Can't enumerate graph\n" );
		return hr;
	}

	IBaseFilter *piFilter = NULL;
	std::vector<IBaseFilter *> filterArray;

	piEnumFilters->Reset();
	while ((hr == S_OK) && ( piEnumFilters->Next(1, &piFilter, 0) == S_OK ) && (piFilter != NULL))
	{
		filterArray.push_back(piFilter);
	}

	std::vector<IBaseFilter *>::iterator it = filterArray.begin();
	for ( ; it < filterArray.end() ; it++ )
	{
		piFilter = *it;
		piGraphBuilder->RemoveFilter(piFilter);
		piFilter->Release();
	}

	return hr;
}

HRESULT FilterGraphTools::GetOverlayMixer(IGraphBuilder* piGraphBuilder, IBaseFilter **ppiFilter)
{
	HRESULT hr;

	*ppiFilter = NULL;
	hr = FindFilter(piGraphBuilder, CLSID_OverlayMixer, ppiFilter);
	if (hr != S_OK)
	{
		//Overlay Mixer 2
		CLSID clsid = GUID_NULL;
		CLSIDFromString(L"{A0025E90-E45B-11D1-ABE9-00A0C905F375}", &clsid);
		hr = FindFilter(piGraphBuilder, clsid, ppiFilter);
	}

	return hr;
}

HRESULT FilterGraphTools::GetOverlayMixerInputPin(IGraphBuilder* piGraphBuilder, LPCWSTR pinName, IPin **ppiPin)
{
	HRESULT hr;
	CComPtr <IBaseFilter> pfOverlayMixer;

	hr = GetOverlayMixer(piGraphBuilder, &pfOverlayMixer);
	if (hr == S_OK)
	{
		hr = FindPin(pfOverlayMixer, pinName, ppiPin, REQUESTED_PINDIR_INPUT);
		if (hr != S_OK)
			Log( L"Error: Could not find Input pin on Overlay Mixer\n" );
	}
	return hr;
}

HRESULT FilterGraphTools::AddToRot(IUnknown *pUnkGraph, DWORD *pdwRegister) 
{
    CComPtr <IMoniker> pMoniker;
    CComPtr <IRunningObjectTable> pROT;
    if FAILED(GetRunningObjectTable(0, &pROT))
	{
        return E_FAIL;
    }
	WCHAR *wsz = (WCHAR *)malloc(256);
	swprintf(wsz, L"FilterGraph %p pid %08x", pUnkGraph, GetCurrentProcessId());

    HRESULT hr = CreateItemMoniker(L"!", wsz, &pMoniker);
    if SUCCEEDED(hr)
	{
        hr = pROT->Register(ROTFLAGS_REGISTRATIONKEEPSALIVE, pUnkGraph, pMoniker, pdwRegister);
    }
	delete wsz;
    return hr;
}

void FilterGraphTools::RemoveFromRot(DWORD pdwRegister)
{
    CComPtr <IRunningObjectTable> pROT;
    if SUCCEEDED(GetRunningObjectTable(0, &pROT))
	{
        pROT->Revoke(pdwRegister);
    }
	pdwRegister = 0;
}


/////////////////////////////////////////////////////////////////////////////
//BDA functions
/////////////////////////////////////////////////////////////////////////////


HRESULT FilterGraphTools::InitTuningSpace(CComPtr <ITuningSpace> &piTuningSpace, TV_TYPE TVType )
{
	HRESULT hr = S_OK;
    CComPtr <ITuningSpaceContainer>  pITuningSpaceContainer;

	if ( TVType == ATSC )
	{
		IATSCTuningSpace*  piATSCTuningSpace;
		
		hr = CoCreateInstance( CLSID_ATSCTuningSpace, NULL, CLSCTX_INPROC_SERVER, 
							IID_IATSCTuningSpace, reinterpret_cast<void**>(&piATSCTuningSpace));
		if ( FAILED(hr) )
		{
			Log( L"Failed to creat ATSC TuningSpace instance\n" );
			return hr;
		}
		LPOLESTR bstrNetworkType;
		StringFromCLSID( CLSID_ATSCNetworkProvider, &bstrNetworkType );
		if (FAILED(hr = piATSCTuningSpace->put_NetworkType( bstrNetworkType)))
		{
			Log( L"Failed to set TuningSpace Network Type\n" );
			piATSCTuningSpace->Release();
			CoTaskMemFree(bstrNetworkType);
			return hr;
		}

		piTuningSpace = piATSCTuningSpace;
		CoTaskMemFree(bstrNetworkType);

	} else
	if ( TVType == QAM )
	{
		IATSCTuningSpace*  piATSCTuningSpace;
		
		hr = CoCreateInstance( CLSID_ATSCTuningSpace, NULL, CLSCTX_INPROC_SERVER, 
							IID_IATSCTuningSpace, reinterpret_cast<void**>(&piATSCTuningSpace));
		if ( FAILED(hr) )
		{
			Log( L"Failed to creat QAM TuningSpace instance\n" );
			return hr;
		}
		LPOLESTR bstrNetworkType;
		//StringFromCLSID( CLSID_CABLENetworkProvider, &bstrNetworkType );
		StringFromCLSID( CLSID_ATSCNetworkProvider, &bstrNetworkType );
		if (FAILED(hr = piATSCTuningSpace->put_NetworkType( bstrNetworkType)))
		{
			Log( L"Failed to set TuningSpace Network Type\n" );
			piATSCTuningSpace->Release();
			CoTaskMemFree(bstrNetworkType);
			return hr;
		}

		hr = piATSCTuningSpace->put_InputType(TunerInputCable);
		hr = piATSCTuningSpace->put_CountryCode(1);

		piTuningSpace = piATSCTuningSpace;
		CoTaskMemFree(bstrNetworkType);
	} else
    // DVB standard needed!
    if ( TVType == DVBT )
    {
		ITuningSpace*  piDVBTTuningSpace;
		

		hr = CoCreateInstance( CLSID_DVBTuningSpace, NULL, CLSCTX_INPROC_SERVER, 
							IID_ITuningSpace, reinterpret_cast<void**>(&piDVBTTuningSpace));
		if ( FAILED(hr) )
		{
			Log( L"Failed to creat instance DVBT TuningSpace \n" );
			return hr;
		}

		CComQIPtr <IDVBTuningSpace2> piDVBTuningSpace2(piDVBTTuningSpace);
	    if (!piDVBTuningSpace2)
    	{
	    	piDVBTTuningSpace->Release();
		    Log( L"Could not QI TuningSpace\n" );
    		return E_FAIL;
	    }

    	if FAILED(hr = piDVBTuningSpace2->put_SystemType(DVB_Terrestrial))
	    {
		    piDVBTTuningSpace->Release();
    		piTuningSpace.Release();
	    	Log( L"Could not put SystemType\n" );
		    return hr;
    	}
		LPOLESTR bstrNetworkType;
		StringFromCLSID( CLSID_DVBTNetworkProvider, &bstrNetworkType );
	    if FAILED(hr = piDVBTuningSpace2->put_NetworkType( bstrNetworkType) )
		{
			piDVBTTuningSpace->Release();
			CoTaskMemFree(bstrNetworkType);
			Log( L"Could not put NetworkType\n" );
			return hr;
		}


		CComPtr <IDVBTLocator> pDVBTLocator;
		hr = pDVBTLocator.CoCreateInstance(CLSID_DVBTLocator);
		if FAILED(hr) 
		{
			Log( L"The DVBTLocator class can't be created as part of an aggregate.\n" );
		} else
		{

			if FAILED(hr = pDVBTLocator->put_CarrierFrequency(-1))
			{
				pDVBTLocator.Release();
				Log( L"Can't set Frequency on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_Bandwidth(-1))
			{
				pDVBTLocator.Release();
				Log( L"Can't set Bandwith rate on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_SymbolRate(-1))
			{
				pDVBTLocator.Release();
				Log( L"Can't set symbol rate on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_Guard( BDA_GUARD_NOT_SET ) )
			{
				pDVBTLocator.Release();
				Log( L"Can't set Guard on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_HAlpha( BDA_HALPHA_NOT_SET ) )
			{
				pDVBTLocator.Release();
				Log( L"Can't set HAlpha on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_Mode( BDA_XMIT_MODE_NOT_SET) )
			{
				pDVBTLocator.Release();
				Log( L"Can't set HAlpha on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_Modulation( BDA_MOD_NOT_SET) )
			{
				pDVBTLocator.Release();
				Log( L"Can't set HAlpha on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_OtherFrequencyInUse( 0 ) )
			{
				pDVBTLocator.Release();
				Log( L"Can't set HAlpha on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_InnerFEC(BDA_FEC_METHOD_NOT_SET) )
			{
				pDVBTLocator.Release();
				Log( L"Can't set InnerFEC on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_InnerFECRate(BDA_BCC_RATE_NOT_SET) )
			{
				pDVBTLocator.Release();
				Log( L"Can't set InnerFEC on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_InnerFECRate(BDA_BCC_RATE_NOT_SET) )
			{
				pDVBTLocator.Release();
				Log( L"Can't set InnerFECRate on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_OuterFEC(BDA_FEC_METHOD_NOT_SET) )
			{
				pDVBTLocator.Release();
				Log( L"Can't set OuterFEC on Locator.\n" );
			}
			if FAILED(hr = pDVBTLocator->put_OuterFECRate(BDA_BCC_RATE_NOT_SET) )
			{
				pDVBTLocator.Release();
				Log( L"Can't set OuterFECRate on Locator.\n" );
			}
			// Bind the locator to the tune request.
			if FAILED(hr = piDVBTuningSpace2->put_DefaultLocator(pDVBTLocator))
			{
				pDVBTLocator.Release();
				Log( L"Cannot put the locator on DVB-T Tune Request\n" );
			}
			pDVBTLocator.Release();
		}

		piTuningSpace = piDVBTTuningSpace;
		CoTaskMemFree(bstrNetworkType);

    } else
    if ( TVType == DVBC )
    {
		ITuningSpace*  piDVBCTuningSpace;
		

		hr = CoCreateInstance( CLSID_DVBTuningSpace, NULL, CLSCTX_INPROC_SERVER, 
							IID_ITuningSpace, reinterpret_cast<void**>(&piDVBCTuningSpace));
		if ( FAILED(hr) )
		{
			Log( L"Failed to creat instance DVBC TuningSpace \n" );
			return hr;
		}

		CComQIPtr <IDVBTuningSpace2> piDVBTuningSpace2(piDVBCTuningSpace);
	    if (!piDVBTuningSpace2)
    	{
	    	piDVBCTuningSpace->Release();
		    Log( L"Could not QI TuningSpace\n" );
    		return E_FAIL;
	    }

    	if FAILED(hr = piDVBTuningSpace2->put_SystemType(DVB_Cable))
	    {
		    piDVBCTuningSpace->Release();
    		piTuningSpace.Release();
	    	Log( L"Could not put SystemType\n" );
		    return hr;
    	}
		LPOLESTR bstrNetworkType;
		StringFromCLSID( CLSID_DVBCNetworkProvider, &bstrNetworkType );
	    if FAILED(hr = piDVBTuningSpace2->put_NetworkType( bstrNetworkType) )
		{
			piDVBCTuningSpace->Release();
			CoTaskMemFree(bstrNetworkType);
			Log( L"Could not put NetworkType\n" );
			return hr;
		}

		CComPtr <IDVBCLocator> pDVBCLocator;
		hr = pDVBCLocator.CoCreateInstance(CLSID_DVBCLocator);
		if FAILED(hr) 
		{
			Log( L"The DVBCLocator class can't be created as part of an aggregate.\n" );
		} else
		{

			if FAILED(hr = pDVBCLocator->put_CarrierFrequency( -1 ))
			{
				pDVBCLocator.Release();
				Log( L"Can't set Frequency on Locator.\n" );
			}
			if FAILED(hr = pDVBCLocator->put_SymbolRate( -1 ))
			{
				pDVBCLocator.Release();
				Log( L"Can't set symbol rate on Locator.\n" );
			}

			if FAILED(hr = pDVBCLocator->put_Modulation( BDA_MOD_256QAM ) )
			{
				pDVBCLocator.Release();
				Log( L"Can't set HAlpha on Locator.\n" );
			}
			if FAILED(hr = pDVBCLocator->put_InnerFEC(BDA_FEC_METHOD_NOT_SET) )
			{
				pDVBCLocator.Release();
				Log( L"Can't set InnerFEC on Locator.\n" );
			}
			if FAILED(hr = pDVBCLocator->put_InnerFECRate(BDA_BCC_RATE_NOT_SET) )
			{
				pDVBCLocator.Release();
				Log( L"Can't set InnerFEC on Locator.\n" );
			}
			if FAILED(hr = pDVBCLocator->put_InnerFECRate(BDA_BCC_RATE_NOT_SET) )
			{
				pDVBCLocator.Release();
				Log( L"Can't set InnerFECRate on Locator.\n" );
			}
			if FAILED(hr = pDVBCLocator->put_OuterFEC(BDA_FEC_METHOD_NOT_SET) )
			{
				pDVBCLocator.Release();
				Log( L"Can't set OuterFEC on Locator.\n" );
			}
			if FAILED(hr = pDVBCLocator->put_OuterFECRate(BDA_BCC_RATE_NOT_SET) )
			{
				pDVBCLocator.Release();
				Log( L"Can't set OuterFECRate on Locator.\n" );
			}
			// Bind the locator to the tune request.
			if FAILED(hr = piDVBTuningSpace2->put_DefaultLocator(pDVBCLocator))
			{
				pDVBCLocator.Release();
				Log( L"Cannot put the locator on DVB-C Tune Request\n" );
			}
			pDVBCLocator.Release();
		}


		piTuningSpace = piDVBCTuningSpace;
		CoTaskMemFree(bstrNetworkType);

    } else
    if ( TVType == DVBS )
    {
		ITuningSpace*  pDVBSTuningSpace;
		

		hr = CoCreateInstance( CLSID_DVBSTuningSpace, NULL, CLSCTX_INPROC_SERVER, 
							IID_ITuningSpace, reinterpret_cast<void**>(&pDVBSTuningSpace));
		if ( FAILED(hr) )
		{
			Log( L"Failed to creat instance DVBS TuningSpace \n" );
			return hr;
		}

		CComQIPtr <IDVBSTuningSpace> piDVBSTuningSpace(pDVBSTuningSpace);
	    if (!piDVBSTuningSpace)
    	{
	    	pDVBSTuningSpace->Release();
		    Log( L"Could not QI TuningSpace\n" );
    		return E_FAIL;
	    }

    	if FAILED(hr = piDVBSTuningSpace->put_SystemType(DVB_Satellite))
	    {
		    pDVBSTuningSpace->Release();
    		piTuningSpace.Release();
	    	Log( L"Could not put SystemType\n" );
		    return hr;
    	}
		LPOLESTR bstrNetworkType;
		StringFromCLSID( CLSID_DVBSNetworkProvider, &bstrNetworkType );
	    if FAILED(hr = piDVBSTuningSpace->put_NetworkType( bstrNetworkType) )
		{
			pDVBSTuningSpace->Release();
			CoTaskMemFree(bstrNetworkType);
			Log( L"Could not put NetworkType\n" );
			return hr;
		}

		long LNBSwicth = 11700;
		long LowOscillator = 9750;
		long HighOscillator = 10600;
		piDVBSTuningSpace->put_LNBSwitch( LNBSwicth*1000 );
		piDVBSTuningSpace->put_SpectralInversion( BDA_SPECTRAL_INVERSION_AUTOMATIC  );
		piDVBSTuningSpace->put_LowOscillator(LowOscillator * 1000);
		piDVBSTuningSpace->put_HighOscillator(HighOscillator * 1000);
		piDVBSTuningSpace->put_SpectralInversion( BDA_SPECTRAL_INVERSION_NOT_SET );

		CComPtr <IDVBSLocator> pDVBSLocator;
		hr = pDVBSLocator.CoCreateInstance(CLSID_DVBSLocator);
		if FAILED( hr )
		{
			if ( hr == REGDB_E_CLASSNOTREG )
				Log( L"The DVBCLocator class isn't registered in the registration database.\n" );
			if ( hr == CLASS_E_NOAGGREGATION )
				Log( L"The DVBCLocator class can't be created as part of an aggregate.\n" );
		} else
		{

			if FAILED(hr = pDVBSLocator->put_CarrierFrequency(-1))
			{
				pDVBSLocator.Release();
				Log( L"Can't set Frequency on Locator.\n" );
			}
			if FAILED(hr = pDVBSLocator->put_SymbolRate(-1))
			{
				pDVBSLocator.Release();
				Log( L"Can't set symbol rate on Locator.\n" );
			}
			if FAILED(hr = pDVBSLocator->put_SignalPolarisation((Polarisation)-1) )
			{
				pDVBSLocator.Release();
				Log( L"Can't set modualtion on Locator.\n" );
			}
			if FAILED(hr = pDVBSLocator->put_InnerFEC(BDA_FEC_METHOD_NOT_SET) )
			{
				pDVBSLocator.Release();
				Log( L"Can't set InnerFEC on Locator.\n" );
			}
			if FAILED(hr = pDVBSLocator->put_InnerFECRate(BDA_BCC_RATE_NOT_SET) )
			{
				pDVBSLocator.Release();
				Log( L"Can't set InnerFEC on Locator.\n" );
			}
			if FAILED(hr = pDVBSLocator->put_InnerFECRate(BDA_BCC_RATE_NOT_SET) )
			{
				pDVBSLocator.Release();
				Log( L"Can't set InnerFECRate on Locator.\n" );
			}
			if FAILED(hr = pDVBSLocator->put_OuterFEC(BDA_FEC_METHOD_NOT_DEFINED) )
			{
				pDVBSLocator.Release();
				Log( L"Can't set OuterFEC on Locator.\n" );
			}
			if FAILED(hr = pDVBSLocator->put_OuterFECRate(BDA_BCC_RATE_NOT_SET) )
			{
				pDVBSLocator.Release();
				Log( L"Can't set OuterFECRate on Locator.\n" );
			}
			// Bind the locator to the tune request.
			if FAILED(hr = piDVBSTuningSpace->put_DefaultLocator(pDVBSLocator))
			{
				pDVBSLocator.Release();
				Log( L"Cannot put the locator on DVB-S Tune Request\n" );
			}
			pDVBSLocator.Release();
		}

		piTuningSpace = piDVBSTuningSpace;
		CoTaskMemFree(bstrNetworkType);

    } else
		return E_FAIL;
    
	return S_OK;
}

HRESULT FilterGraphTools::CreateATSCTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long lMajorChannel, long lMinorChannel, long lPhysicalChannel, long lFreq )
{
	HRESULT hr = S_OK;

	if (!piTuningSpace)
	{
		Log( L"Tuning Space is NULL\n" );
		return E_FAIL;
	}


	//Get new TuneRequest from tuning space
	//if FAILED(hr = piATSCTuningSpace->CreateTuneRequest(&pExTuneRequest))
	if FAILED(hr = piTuningSpace->CreateTuneRequest(&pExTuneRequest))
	{
		Log( L"Failed to create Tune Request\n" );
		return hr;
	}

	//Get interface to the TuneRequest
	CComQIPtr <IATSCChannelTuneRequest> piATSCTuneRequest(pExTuneRequest);
	if ( piATSCTuneRequest == NULL )
	{
		pExTuneRequest.Release();
        Log( L"Can't Query Interface for an IATSCTuneRequest.\n" );
		pExTuneRequest.Release();
		return E_FAIL;
	}

    //  Set the initial major and minor channels
    hr = piATSCTuneRequest->put_Channel(lMajorChannel);
    if(FAILED(hr))
    {
        Log( L"put_Channel failed\n" );
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
        return hr;
    }

    hr = piATSCTuneRequest->put_MinorChannel(lMinorChannel);
    if(FAILED(hr))
    {
        Log( L"put_MinorChannel failed\n" );
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
        return hr;
    }

	// setup ATSC locator
	CComPtr <IATSCLocator> pATSCLocator;
	hr = pATSCLocator.CoCreateInstance(CLSID_ATSCLocator);
	if ( FAILED( hr ) )
	{
        Log( L"Cannot create the ATSC locator\n" );
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
		return hr;
	}

	if ( lFreq > 1000 )
	{
		hr = pATSCLocator->put_CarrierFrequency( lFreq );
		if (FAILED( hr ))
		{
			 Log( L"Failed put CarrierFrequency %d\n", lFreq );
		}
	}

    hr = pATSCLocator->put_PhysicalChannel(lPhysicalChannel);
    if (FAILED( hr))
    {
        Log( L"Cannot put the physical channel\n" );
		pATSCLocator.Release();
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
        return hr;
    }


	// Bind the locator to the tune request.
    if FAILED(hr = piATSCTuneRequest->put_Locator(pATSCLocator))
	{
		pATSCLocator.Release();
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
        Log( L"Cannot put the locator on ATSC Tune Request\n" );
		return hr;
    }

	pATSCLocator.Release();
	piATSCTuneRequest.Release();

	return S_OK;
}

//it's used by Hauppauge to feed in QAM data
HRESULT FilterGraphTools::CreateQAMTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long lPhysicalChannel, unsigned long lFrequency,  long lModulation )
{
	HRESULT hr = S_OK;

	if (!piTuningSpace)
	{
		Log( L"Tuning Space is NULL\n" );
		return E_FAIL;
	}

	//Get an interface to the TuningSpace
	CComQIPtr <IATSCTuningSpace> piATSCTuningSpace( piTuningSpace );
	  if (piATSCTuningSpace == NULL )
	{
	   Log( L"Can't Query Interface for an IATSCTuningSpace2\n" );
		return E_FAIL;
	}

	hr = piATSCTuningSpace->put_InputType(TunerInputCable);
	hr = piATSCTuningSpace->put_CountryCode(1);

	//Get new TuneRequest from tuning space
	if FAILED(hr = piTuningSpace->CreateTuneRequest(&pExTuneRequest))
	{
		Log( L"Failed to create Tune Request\n" );
		//piATSCTuningSpace.Release();
		return hr;
	}

	//Get interface to the TuneRequest
	CComQIPtr <IATSCChannelTuneRequest> piATSCTuneRequest(pExTuneRequest);
	if ( piATSCTuneRequest == NULL )
	{
		piATSCTuningSpace.Release();
		pExTuneRequest.Release();
        Log( L"Can't Query Interface for an IATSCTuneRequest.\n" );
		pExTuneRequest.Release();
		return E_FAIL;
	}


	// setup ATSC locator
	CComPtr <IATSCLocator> pATSCLocator;
	hr = pATSCLocator.CoCreateInstance(CLSID_ATSCLocator);
	if ( FAILED( hr ) )
	{
        Log( L"Cannot create the ATSC locator\n" );
		piATSCTuningSpace.Release();
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
		return hr;
	}

	hr = pATSCLocator->put_InnerFEC(BDA_FEC_METHOD_NOT_SET);
	hr = pATSCLocator->put_InnerFECRate(BDA_BCC_RATE_NOT_SET);
	hr = pATSCLocator->put_OuterFEC(BDA_FEC_METHOD_NOT_SET);
	hr = pATSCLocator->put_OuterFECRate(BDA_BCC_RATE_NOT_SET);
    //hr = pATSCLocator->put_PhysicalChannel(lPhysicalChannel);
    //if (FAILED( hr))
    //{
    //    Log( L"Cannot put the physical channel\n" );
	//	piATSCTuningSpace.Release();
	//	pATSCLocator.Release();
	//	pExTuneRequest.Release();
	//	piATSCTuneRequest.Release();
    //    return hr;
    //}

	hr = pATSCLocator->put_CarrierFrequency(lFrequency);
	if (FAILED( hr))
    {
        Log( L"Cannot put the frequency channel\n" );
		piATSCTuningSpace.Release();
		pATSCLocator.Release();
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
		return hr;
    }

	//ModulationType Modulation=(ModulationType)lModulation;
    //hr = pATSCLocator->put_Modulation( Modulation );
	hr = pATSCLocator->put_Modulation(BDA_MOD_256QAM);
    if (FAILED( hr))
    {
		Log( L"Cannot put the physical channel\n" );
 		piATSCTuningSpace.Release();
 		pATSCLocator.Release();
 		pExTuneRequest.Release();
 		piATSCTuneRequest.Release();
		return hr;
    }

	// Bind the locator to the tune request.
    if FAILED(hr = piATSCTuneRequest->put_Locator(pATSCLocator))
	{
		piATSCTuningSpace.Release();
		pATSCLocator.Release();
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
        Log( L"Cannot put the locator on ATSC Tune Request\n" );
		return hr;
    }


	piATSCTuningSpace.Release();
	pATSCLocator.Release();
	piATSCTuneRequest.Release();

	return S_OK;
}



HRESULT FilterGraphTools::CreateDVBTTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long lOid, long lSid, long lTsid )
{
	HRESULT hr = S_OK;

	if (!piTuningSpace)
	{
		Log( L"Tuning Space is NULL\n" );
		return E_FAIL;
	}

	//Get an interface to the TuningSpace
	CComQIPtr <IDVBTuningSpace> piDVBTuningSpace(piTuningSpace);
    if (!piDVBTuningSpace)
	{
        Log( L"Can't Query Interface for an IDVBTuningSpace2\n" );
		return E_FAIL;
	}
	//Get new TuneRequest from tuning space
	if FAILED(hr = piDVBTuningSpace->CreateTuneRequest(&pExTuneRequest))
	{
		Log( L"Failed to create Tune Request\n" );
		piDVBTuningSpace.Release();
		return hr;
	}
	//Get interface to the TuneRequest
	CComQIPtr <IDVBTuneRequest> piDVBTuneRequest(pExTuneRequest);
	if ( piDVBTuneRequest == NULL )
	{
		piDVBTuningSpace.Release();
		pExTuneRequest.Release();
        Log( L"Can't Query Interface for an IDVBTuneRequest.\n" );
		pExTuneRequest.Release();
		return E_FAIL;
	}

    //  Set the initial oid, sid, tsid and minor channels
	hr = piDVBTuneRequest->put_ONID( lOid );
    if(FAILED(hr))
    {
        Log( L"put network id(oid) failed\n" );
		piDVBTuningSpace.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
        return hr;
    }

	hr = piDVBTuneRequest->put_SID( lSid );
    if(FAILED(hr))
    {
        Log( L"put service id(sid) failed\n" );
		piDVBTuningSpace.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
        return hr;
    }

	hr = piDVBTuneRequest->put_TSID( lTsid );
    if(FAILED(hr))
    {
        Log( L"put transport id(tsid) failed\n" );
		piDVBTuningSpace.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
        return hr;
    }

	return S_OK;

}



HRESULT FilterGraphTools::CreateDVBTTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long frequency, long bandwidth)
{
	HRESULT hr = S_OK;

	if (!piTuningSpace)
	{
		Log( L"Tuning Space is NULL\n" );
		return E_FAIL;
	}

	//Get an interface to the TuningSpace
	CComQIPtr <IDVBTuningSpace2> piDVBTuningSpace(piTuningSpace);
    if (!piDVBTuningSpace)
	{
        Log( L"Can't Query Interface for an IDVBTuningSpace2\n" );
		return E_FAIL;
	}

	//Get new TuneRequest from tuning space
	if FAILED(hr = piDVBTuningSpace->CreateTuneRequest(&pExTuneRequest))
	{
		Log( L"Failed to create Tune Request\n" );
		return hr;
	}

	//Get interface to the TuneRequest
	CComQIPtr <IDVBTuneRequest> piDVBTuneRequest(pExTuneRequest);
	if (!piDVBTuneRequest)
	{
		pExTuneRequest.Release();
        Log( L"Can't Query Interface for an IDVBTuneRequest.\n" );
		return E_FAIL;
	}

	CComPtr <IDVBTLocator> pDVBTLocator;
	hr = pDVBTLocator.CoCreateInstance(CLSID_DVBTLocator);
	switch (hr)
	{ 
	case REGDB_E_CLASSNOTREG:
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"The DVBTLocator class isn't registered in the registration database.\n" );
		return hr;

	case CLASS_E_NOAGGREGATION:
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"The DVBTLocator class can't be created as part of an aggregate.\n" );
		return hr;
	}

	if FAILED(hr = pDVBTLocator->put_CarrierFrequency(frequency))
	{
		pDVBTLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set Frequency on Locator.\n" );
		return hr;
	}
	if FAILED(hr = pDVBTLocator->put_Bandwidth(bandwidth))
	{
		pDVBTLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set Bandwidth on Locator.\n" );
		return hr;
	}

	// Bind the locator to the tune request.
    if FAILED(hr = piDVBTuneRequest->put_Locator(pDVBTLocator))
	{
		pDVBTLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
        Log( L"Cannot put the locator on DVB-T Tune Request\n" );
		return hr;
    }

	pDVBTLocator.Release();
	piDVBTuneRequest.Release();

	return hr;
}

HRESULT FilterGraphTools::CreateDVBCTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long frequency, long rate, long modualtion, int innerFEC, int innerFECRate, int outerFEC, int outerFECRate  )
{
	HRESULT hr = S_OK;

	if (!piTuningSpace)
	{
		Log( L"Tuning Space is NULL\n" );
		return E_FAIL;
	}

	//Get an interface to the TuningSpace
	CComQIPtr <IDVBTuningSpace2> piDVBTuningSpace(piTuningSpace);
    if (!piDVBTuningSpace)
	{
        Log( L"Can't Query Interface for an IDVBTuningSpace2\n" );
		return E_FAIL;
	}

	//Get new TuneRequest from tuning space
	if FAILED(hr = piDVBTuningSpace->CreateTuneRequest(&pExTuneRequest))
	{
		Log( L"Failed to create Tune Request\n" );
		return hr;
	}

	//Get interface to the TuneRequest
	CComQIPtr <IDVBTuneRequest> piDVBTuneRequest(pExTuneRequest);
	if (!piDVBTuneRequest)
	{
		pExTuneRequest.Release();
        Log( L"Can't Query Interface for an IDVBTuneRequest.\n" );
		return E_FAIL;
	}

	CComPtr <IDVBCLocator> pDVBCLocator;
	hr = pDVBCLocator.CoCreateInstance(CLSID_DVBCLocator);
	switch (hr)
	{ 
	case REGDB_E_CLASSNOTREG:
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"The DVBCLocator class isn't registered in the registration database.\n" );
		return hr;

	case CLASS_E_NOAGGREGATION:
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"The DVBCLocator class can't be created as part of an aggregate.\n" );
		return hr;
	}
	if FAILED(hr = pDVBCLocator->put_CarrierFrequency(frequency))
	{
		pDVBCLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set Frequency on Locator.\n" );
		return hr;
	}
	if FAILED(hr = pDVBCLocator->put_SymbolRate(rate))
	{
		pDVBCLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set symbol rate on Locator.\n" );
		return hr;
	}
	if FAILED(hr = pDVBCLocator->put_Modulation((ModulationType)modualtion))
	{
		pDVBCLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set modualtion on Locator.\n" );
		return hr;
	}

	if ( innerFEC >= 0 )
	{
		if FAILED(hr = pDVBCLocator->put_InnerFEC((FECMethod)innerFEC))
		{
			pDVBCLocator.Release();
			pExTuneRequest.Release();
			piDVBTuneRequest.Release();
			Log( L"Can't set InnerFEC on Locator.\n" );
			return hr;
		}
	} else
	{
		if FAILED(hr = pDVBCLocator->put_InnerFEC(BDA_FEC_METHOD_NOT_SET))
		{
			pDVBCLocator.Release();
			pExTuneRequest.Release();
			piDVBTuneRequest.Release();
			Log( L"Can't set InnerFEC on Locator.\n" );
			return hr;
		}
	}


	if ( innerFECRate >= 0 )
	{
		if FAILED(hr = pDVBCLocator->put_InnerFECRate((BinaryConvolutionCodeRate)innerFECRate))
		{
			pDVBCLocator.Release();
			pExTuneRequest.Release();
			piDVBTuneRequest.Release();
			Log( L"Can't set InnerFECRate on Locator.\n" );
			return hr;
		}
	} else
	{
		if FAILED(hr = pDVBCLocator->put_InnerFECRate(BDA_BCC_RATE_NOT_SET))
		{
			pDVBCLocator.Release();
			pExTuneRequest.Release();
			piDVBTuneRequest.Release();
			Log( L"Can't set InnerFECRate on Locator.\n" );
			return hr;
		}
	}

	if ( outerFEC >= 0 )
	{
		if FAILED(hr = pDVBCLocator->put_OuterFEC((FECMethod)outerFEC))
		{
			pDVBCLocator.Release();
			pExTuneRequest.Release();
			piDVBTuneRequest.Release();
			Log( L"Can't set outerFEC on Locator.\n" );
			return hr;
		}
	}else
	{
		if FAILED(hr = pDVBCLocator->put_OuterFEC(BDA_FEC_METHOD_NOT_SET))
		{
			pDVBCLocator.Release();
			pExTuneRequest.Release();
			piDVBTuneRequest.Release();
			Log( L"Can't set outerFEC on Locator.\n" );
			return hr;
		}
	}

	if ( outerFECRate >= 0 )
	{
		if FAILED(hr = pDVBCLocator->put_OuterFECRate((BinaryConvolutionCodeRate)outerFECRate))
		{
			pDVBCLocator.Release();
			pExTuneRequest.Release();
			piDVBTuneRequest.Release();
			Log( L"Can't set outerFECRate on Locator.\n" );
			return hr;
		}
	} else
	{
		if FAILED(hr = pDVBCLocator->put_OuterFECRate(BDA_BCC_RATE_NOT_SET))
		{
			pDVBCLocator.Release();
			pExTuneRequest.Release();
			piDVBTuneRequest.Release();
			Log( L"Can't set outerFECRate on Locator.\n" );
			return hr;
		}
	}

	// Bind the locator to the tune request.
    if FAILED(hr = piDVBTuneRequest->put_Locator(pDVBCLocator))
	{
		pDVBCLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
        Log( L"Cannot put the locator on DVB-C Tune Request\n" );
		return hr;
    }

	pDVBCLocator.Release();
	piDVBTuneRequest.Release();

	return S_OK;
}

HRESULT FilterGraphTools::CreateDVBSTuneRequest(CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long frequency, long rate, long polarisation, long modualtion, int innerFEC, int innerFECRate, int outerFEC, int outerFECRate  )
{
	HRESULT hr = S_OK;

	if (!piTuningSpace)
	{
		Log( L"Tuning Space is NULL\n" );
		return E_FAIL;
	}

	
	//Get an interface to the TuningSpace
	CComQIPtr <IDVBTuningSpace2> piDVBTuningSpace(piTuningSpace);
    if (!piDVBTuningSpace)
	{
        Log( L"Can't Query DVBS Interface for an IDVBTuningSpace\n" );
		return E_FAIL;
	}
   	//Get new TuneRequest from tuning space
	if FAILED(hr = piDVBTuningSpace->CreateTuneRequest(&pExTuneRequest))
	{
		Log( L"Failed to create Tune Request\n" );
		return hr;
	}

	////Get new TuneRequest from tuning space
	//if FAILED(hr = piTuningSpace->CreateTuneRequest(&pExTuneRequest))
	//{
	//	Log( L"Failed to create Tune Request\n" );
	//	return hr;
	//}

	//Get interface to the TuneRequest
	CComQIPtr <IDVBTuneRequest> piDVBTuneRequest(pExTuneRequest);
	if (!piDVBTuneRequest)
	{
		pExTuneRequest.Release();
        Log( L"Can't Query Interface for an IDVBSTuneRequest.\n" );
		return E_FAIL;
	}
	
	CComPtr <IDVBSLocator> pDVBSLocator;
	hr = pDVBSLocator.CoCreateInstance(CLSID_DVBSLocator);
	switch (hr)
	{ 
	case REGDB_E_CLASSNOTREG:
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"The DVBCLocator class isn't registered in the registration database.\n" );
		return hr;

	case CLASS_E_NOAGGREGATION:
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"The DVBCLocator class can't be created as part of an aggregate.\n" );
		return hr;
	}
	if FAILED(hr = pDVBSLocator->put_CarrierFrequency(frequency*1000))
	{
		pDVBSLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set Frequency on Locator.\n" );
		return hr;
	}
	if FAILED(hr = pDVBSLocator->put_SymbolRate(rate))
	{
		pDVBSLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set symbol rate on Locator.\n" );
		return hr;
	}

	if FAILED(hr = pDVBSLocator->put_SignalPolarisation((Polarisation)polarisation) )
	{
		pDVBSLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set modualtion on Locator.\n" );
		return hr;
	}

	if FAILED(hr = pDVBSLocator->put_Modulation((ModulationType)modualtion))
	{
		pDVBSLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set modualtion on Locator.\n" );
		return hr;
	}

	if FAILED(hr = pDVBSLocator->put_InnerFEC( (FECMethod)innerFEC ) )
	{
		pDVBSLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set innerFEC on Locator.\n" );
		return hr;
	}

	if FAILED(hr = pDVBSLocator->put_InnerFECRate( (BinaryConvolutionCodeRate)innerFECRate ) )
	{
		pDVBSLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set innerFEC rate on Locator.\n" );
		return hr;
	}

	if FAILED(hr = pDVBSLocator->put_OuterFECRate( (BinaryConvolutionCodeRate)outerFECRate ) )
	{
		pDVBSLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set outerFEC rate on Locator.\n" );
		return hr;
	}
	if FAILED(hr = pDVBSLocator->put_OuterFEC( (FECMethod)outerFEC ) )
	{
		pDVBSLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		Log( L"Can't set outerFEC on Locator.\n" );
		return hr;
	}


	// Bind the locator to the tune request.
    if FAILED(hr = piDVBTuneRequest->put_Locator(pDVBSLocator))
	{
		pDVBSLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
        Log( L"Cannot put the locator on DVB-S Tune Request\n" );
		return hr;
    }

	pDVBSLocator.Release();
	piDVBTuneRequest.Release();
	return S_OK;
}

void FilterGraphTools::strCopy(LPSTR &dest, LPCSTR src, long length)
{
	if (dest)
		delete[] dest;
	if (length < 0)
		length = strlen(src);
	dest = new char[length + 1];
	memcpy(dest, src, length);
	dest[length] = 0;
}

void FilterGraphTools::strCopy(LPWSTR &dest, LPCWSTR src, long length)
{
	if (dest)
		delete[] dest;
	if (length < 0)
		length = wcslen(src);
	dest = new wchar_t[length + 1];
	memcpy(dest, src, length*2);
	dest[length] = 0;
}

void FilterGraphTools::strCopyA2W(LPWSTR &dest, LPCSTR src, long length)
{
	if (dest)
		delete[] dest;
	if (length < 0)
		length = strlen(src);
	dest = new wchar_t[length + 1];
	mbstowcs(dest, src, length);
	dest[length] = 0;
}

void FilterGraphTools::strCopyW2A(LPSTR &dest, LPCWSTR src, long length)
{
	if (dest)
		delete[] dest;
	if (length < 0)
		length = wcslen(src);
	dest = new char[length + 1];
	wcstombs(dest, src, length);
	dest[length]=0;
}

void FilterGraphTools::strCopy(LPSTR &dest, LPCSTR src)
{
	strCopy(dest, src, -1);
}

void FilterGraphTools::strCopy(LPWSTR &dest, LPCWSTR src)
{
	strCopy(dest, src, -1);
}

void FilterGraphTools::strCopyA2W(LPWSTR &dest, LPCSTR src)
{
	strCopyA2W(dest, src, -1);
}

void FilterGraphTools::strCopyW2A(LPSTR &dest, LPCWSTR src)
{
	strCopyW2A(dest, src, -1);
}

void FilterGraphTools::strCopy(LPSTR &dest, long value)
{
	if (dest)
		delete[] dest;
	BOOL bNegative = (value < 0);
	value = abs(value);
	long length = (long)log10((double)value) + (bNegative ? 2 : 1);
	dest = new char[length + 1];

	for ( int i=length-1 ; i>=0 ; i-- )
	{
		dest[i] = '0' + (CHAR)(value % 10);
		value /= 10;
	}
	if (bNegative)
		dest[0] = '-';
	dest[length] = 0;
}

void FilterGraphTools::strCopy(LPWSTR &dest, long value)
{
	if (dest)
		delete[] dest;
	BOOL bNegative = (value < 0);
	value = abs(value);
	long length = (long)log10((double)value) + (bNegative ? 2 : 1);
	dest = new wchar_t[length + 1];

	for ( int i=length-1 ; i>=0 ; i-- )
	{
		dest[i] = '0' + (CHAR)(value % 10);
		value /= 10;
	}
	if (bNegative)
		dest[0] = '-';
	dest[length] = 0;
}

DirectShowSystemDevice::DirectShowSystemDevice()
{
	strFriendlyName = NULL;
	strDevicePath = NULL;
	bValid = FALSE;
}

DirectShowSystemDevice::~DirectShowSystemDevice()
{
	if (strFriendlyName)
		delete[] strFriendlyName;
	if (strDevicePath)
		delete[] strDevicePath;
}

const DirectShowSystemDevice &DirectShowSystemDevice::operator=(const DirectShowSystemDevice &right)
{
	if (&right != this)
	{
		FilterGraphTools::strCopy(strFriendlyName, right.strFriendlyName);
		FilterGraphTools::strCopy(strDevicePath, right.strDevicePath);
		bValid = right.bValid;
	}
	return *this;
}

HRESULT DirectShowSystemDevice::CreateInstance(CComPtr <IBaseFilter> &pFilter)
{
	HRESULT hr;
	CComPtr <IBindCtx> pBindCtx;
	CComPtr <IMoniker> pMoniker;
	DWORD dwEaten;

	if FAILED(hr = CreateBindCtx(0, &pBindCtx))
	{
		Log( L"AddFilterByDevicePath: Could not create bind context\n" );
		return hr;
	}

	if (FAILED(hr = MkParseDisplayName(pBindCtx, strDevicePath, &dwEaten, &pMoniker)) || (pMoniker == NULL))
	{
		Log( L"AddFilterByDevicePath: Could not create moniker from device path %s %s", strDevicePath, strFriendlyName);
		return hr;
	}

	if FAILED(hr = pMoniker->BindToObject(0, 0, IID_IBaseFilter, reinterpret_cast<void**>(&pFilter)))
	{
		Log( L"Could Not Create Filter: %s", strFriendlyName );
		return hr;
	}

	return S_OK;
}

//////////////////////////////////////////////////////////////////////
// DirectShowSystemDeviceEnumerator
//////////////////////////////////////////////////////////////////////

DirectShowSystemDeviceEnumerator::DirectShowSystemDeviceEnumerator(REFCLSID deviceClass)
{
	if FAILED(m_pSysDevEnum.CoCreateInstance(CLSID_SystemDeviceEnum))
	{
		Log( L"Could not create system device enumerator\n" );
	}
	else
	{
		if FAILED(m_pSysDevEnum->CreateClassEnumerator(deviceClass, &m_pEnum, 0))
		{
			Log( L"Could not create device class enumerator\n" );
			m_pSysDevEnum.Release();
		}
	}


}

DirectShowSystemDeviceEnumerator::~DirectShowSystemDeviceEnumerator()
{
	m_pEnum.Release();
	m_pSysDevEnum.Release();
}

HRESULT DirectShowSystemDeviceEnumerator::Next(DirectShowSystemDevice** ppDevice)
{
	if (!m_pSysDevEnum)
		return E_FAIL;
	if (!m_pEnum)
		return E_FAIL;

	HRESULT hr;
	CComPtr <IMoniker> pMoniker;
	if ((hr = m_pEnum->Next(1, &pMoniker, 0)) != S_OK)
		return hr;
	if (!pMoniker)
		return E_FAIL;

	CComPtr <IBindCtx> pBindCtx;
	if FAILED(hr = CreateBindCtx(0, &pBindCtx))
	{
		Log( L"Could not create bind context\n" );
		return hr;
	}

	CComPtr <IPropertyBag> pPropBag;
	if FAILED(hr = pMoniker->BindToStorage(pBindCtx, 0, IID_IPropertyBag, reinterpret_cast<void**>(&pPropBag)))
	{
		Log( L"Could not get property bag\n");
		return hr;
	}


	*ppDevice = new DirectShowSystemDevice();
	//(*ppDevice)->SetLogCallback(m_pLogCallback);

	VARIANT varName;
	VariantInit(&varName);
	if SUCCEEDED(hr = pPropBag->Read(L"FriendlyName", &varName, 0))
	{
		if (varName.vt == VT_BSTR)
		{
			FilterGraphTools::strCopy((*ppDevice)->strFriendlyName, varName.bstrVal);
		}
		else
		{
			Log( L"FriendlyName is not of type VT_BSTR. It's type %s", varName.vt );
			FilterGraphTools::strCopy((*ppDevice)->strFriendlyName, L"");
		}
	}
	else
	{
		Log( L"FriendlyName does not exist. Setting to blank string\n" );
		FilterGraphTools::strCopy((*ppDevice)->strFriendlyName, L"");
	}
	VariantClear(&varName);
	pPropBag.Release();

	LPWSTR pDisplayName;
	if FAILED(hr = pMoniker->GetDisplayName(pBindCtx, NULL, &pDisplayName))
	{
		delete *ppDevice;
		*ppDevice = NULL;
		Log( L"Could not get device path\n" );
		return hr;
	}
	FilterGraphTools::strCopy((*ppDevice)->strDevicePath, pDisplayName);

	IMalloc* memAlloc = NULL;
	CoGetMalloc(1, &memAlloc);
	memAlloc->Free(pDisplayName);

	(*ppDevice)->bValid = TRUE;

	return S_OK;
}

HRESULT DirectShowSystemDeviceEnumerator::Reset()
{
	return m_pEnum->Reset();
}


