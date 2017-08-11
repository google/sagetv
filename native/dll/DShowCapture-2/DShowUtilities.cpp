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
#include "DShowUtilities.h"
#include <streams.h>
#include <ks.h>
#include <ksproxy.h>
 #include "guids.h"

#include <ks.h> // Must be included before ksmedia.h
#include <ksmedia.h> // Must be included before bdamedia.h
#include <bdamedia.h>
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "DivX/ReSyncSettings.h"
#include "DivX/ReSyncUID.h"
#include "DivX/encore.h"
#include "DivX/divxencfilterabout.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
#include "uniapi.h"

CLSID CLSID_DivXEncFilter = 
{
	0x3c9fdce9, 
	0xc930, 
	0x46d4, 
	{
		0xa4, 0x7f, 
		0x17, 0xd6, 0x8a, 0xe, 0x26, 0xc2
	}
};


HRESULT AddToRot(IUnknown *pUnkGraph, DWORD *pdwRegister) 
{
    IMoniker * pMoniker;
    IRunningObjectTable *pROT;
    if (FAILED(GetRunningObjectTable(0, &pROT))) {
        return E_FAIL;
    }
    WCHAR wsz[256];
    wsprintfW(wsz, L"FilterGraph %08x pid %08x", (DWORD_PTR)pUnkGraph, GetCurrentProcessId());
    HRESULT hr = CreateItemMoniker(L"!", wsz, &pMoniker);
    if (SUCCEEDED(hr)) {
        hr = pROT->Register(0, pUnkGraph, pMoniker, pdwRegister);
        pMoniker->Release();
    }
    pROT->Release();
    return hr;
}

void RemoveFromRot(DWORD pdwRegister)
{
    IRunningObjectTable *pROT;
    if (SUCCEEDED(GetRunningObjectTable(0, &pROT))) {
        pROT->Revoke(pdwRegister);
        pROT->Release();
    }
}

static char* ExtractMfg( char* mfg_string, int len )
{
	char *p;
	//in vista mfg is in format "%xxx.inf,%yyy%;mfg"
	if ( mfg_string == NULL || mfg_string[0] == 0x0 || len <5 )
		return "";

	if ( mfg_string[0] != '@' ) return mfg_string;
	if ( strstr( mfg_string, ",%" ) == NULL ) return mfg_string;
	if ( ( p = strstr( mfg_string, "%;" )) == NULL ) return mfg_string;

	p += 2;
	return p;

}

static char* ExtractHardwareID( char* id_string, int len )
{
	int i;
	//Stream\HCW88BDA.M69xx#PCI#VEN_14F1&DEV_8800&SUBSYS_69020070&REV_05
	if ( id_string == NULL || id_string[0] == 0x0  )
		return "";

	i = strlen( id_string);
	while ( i>0 && id_string[i] != '#' ) i--;

	return &id_string[i];

}

HRESULT GetDeviceInfo( char*  pCaptureDevName, DEVICE_DRV_INF* pDrvInfo )
{
	//char device_class[48]=(0};

	char *p;
	char *pe, *pf;
	char *pd[16];
	int  i;

	memset( pDrvInfo, 0, sizeof(DEVICE_DRV_INF) );
	strncpy( pDrvInfo->full_name, pCaptureDevName, sizeof(pDrvInfo->full_name) );

	//parser format @device:pnp:\\?\type#device_name#instance#{classid}\xxx"
	//parser format @device:sw:{classid}\xxx"  e.g. SilliconDust's HDHomeRun 

	if ( strstr ( pCaptureDevName, "fd0a5af4-b41d-11d2-9c95-00c04f7971e0" ) != NULL )
	{
		pDrvInfo->device_class = 3; // BDA Receiver components (TS Capture)
	} else
	if ( strstr ( pCaptureDevName, "71985f48-1ca1-11d3-9cc8-00c04f7971e0" ) != NULL )
	{
		pDrvInfo->device_class = 2; // BDA Source Filters (Tuners)
	} else
	if ( strstr ( pCaptureDevName, "71985f4b-1ca1-11d3-9cc8-00c04f7971e0" ) != NULL )
	{
		pDrvInfo->device_class = 1; // BDA Network Providers
	} 

	if ( p = strstr( pCaptureDevName, "@device:sw:" ) ) //software device
	{
		if ( ( p = strchr( pCaptureDevName, '{' ) ) != NULL )
			p = strstr( p+1, "}\\" );

		if ( p == NULL ) return E_FAIL;

		p += 2;
		strncpy( pDrvInfo->device_type, "*sw*",  sizeof(pDrvInfo->device_type) );
		strncpy( pDrvInfo->hardware_loc, p ,sizeof(pDrvInfo->hardware_loc) );
		pDrvInfo->state = 1;
		return S_OK;
	}

	pDrvInfo->state = 0;
	p = strstr( pCaptureDevName, "\\\\?\\" );
	if ( p == NULL )
		return E_FAIL;

	p += 4;
	//get device bus type: pci, usb
	pe =  strchr( p, '#' );
	if ( pe == NULL )
		return E_FAIL;
	strncpy( pDrvInfo->device_type, p, min(pe-p, sizeof(pDrvInfo->device_type)) );

	pd[0] = p = pe+1;
	for ( i=1; i<16; i++ )
	{
		pe =  strchr( p, '#' );
		if ( pe == NULL )
			break;
		pd[i] = pe+1;
		if ( *pd[i] == '{' )
			break;
		p = pe+1;
	}

	if ( i < 2 )
		return E_FAIL;

	//get device instance name
	p = pd[i-1];
	pe =  pd[i]-1;
	if ( pe == NULL )
		return E_FAIL;
	strncpy( pDrvInfo->device_inst, p, min(pe-p, sizeof(pDrvInfo->device_inst)) );


	if ( *pd[i] == '{' )
	{
		p = pd[i]+1;
		pe =  strchr( p, '}' );
		if ( pe != NULL )
		{
			strncpy( pDrvInfo->inst_guid1, p, min(pe-p, sizeof(pDrvInfo->inst_guid1)) );
			p =  strstr( pd[i]+1, "\\{" );
			if ( p != NULL )
			{
				p += 2;
				pe = pe =  strchr( p, '}' );
				if ( pe != NULL )
				{
					strncpy( pDrvInfo->inst_guid2, p, min(pe-p, sizeof(pDrvInfo->inst_guid2)) );
				}
			}
		}
	}
	//get vendor id tag
	p = pd[0];
	pe = strchr( p, '&' );
	if ( pe != NULL )
	{
		p = strstr( pd[0], "vid_" );
		if ( p == NULL )
			p = strstr( pd[0], "ven_" );
			if ( p == NULL )
				p = strstr( pd[0], "VEN_" );

		if ( p != NULL )
		{
			strncpy( pDrvInfo->vendor_id, p+4, min(pe-p-4,sizeof(pDrvInfo->vendor_id))  );
		}

	}

	//get device id tag
	p = pd[0];
	pe =  pd[i-1]-1;
	if ( pe == NULL )
		return E_FAIL;
	strncpy( pDrvInfo->device_name, p, min(pe-p,sizeof(pDrvInfo->device_name))  );

	for ( i=0; i<(int)min(strlen(pDrvInfo->device_inst),sizeof(pDrvInfo->device_inst)); i++ )
	{
		if ( pDrvInfo->device_inst[i] == '#')
			pDrvInfo->device_inst[i] = '/';
	}

	for ( i=0; i<(int)min(strlen(pDrvInfo->device_name),sizeof(pDrvInfo->device_name)); i++ )
	{
		if ( pDrvInfo->device_name[i] == '#')
			pDrvInfo->device_name[i] = '/';
	}

	//decode vendor subsys device id form device name
	p = pDrvInfo->device_name;
	pe = strchr( p, '&' );
	if ( pe != NULL )
	{
		//strncpy( pDrvInfo->venstring, p, min(pe-p, sizeof(pDrvInfo->venstring)) );

		p = pe+1;
		pe = strchr( p, '&' );
		if ( pe != NULL )
		{
			strncpy( pDrvInfo->device_id, p, min(pe-p, sizeof(pDrvInfo->device_id)) );
			p = pe+1;
			pe = strchr( p, '&' );
			if ( pe != NULL )
			{
				strncpy( pDrvInfo->subsys_name, p, min(pe-p,sizeof(pDrvInfo->subsys_name)) );
				strncpy( pDrvInfo->subsys_id, pe-4, 4 ); pDrvInfo->subsys_id[4] = 0x0;
				p = pe+1;
				if ( *p )
					strncpy( pDrvInfo->rev_tag, p, sizeof(pDrvInfo->rev_tag) );
			} else
			{
				if ( *p )
					strncpy( pDrvInfo->subsys_name, p, sizeof(pDrvInfo->subsys_name) );
			}
		} else
		{
			if ( *p )
				strncpy( pDrvInfo->device_id, p, sizeof(pDrvInfo->device_id) );
		}
	}

	p = pDrvInfo->device_inst;
	pe = strchr( p, '&' );
	if ( pe != NULL )
	{
		strncpy( pDrvInfo->inst1, p, min(pe-p, sizeof(pDrvInfo->inst1)) );
		p = pe+1;
		pe = strchr( p, '&' );
		if ( pe != NULL )
		{
			strncpy( pDrvInfo->inst2, p, min(pe-p, sizeof(pDrvInfo->inst2)) );
			p = pe+1;
			if ( *p )
				strncpy( pDrvInfo->inst3, p, sizeof(pDrvInfo->inst3) );
		} else
		{
			strncpy( pDrvInfo->inst2, p, sizeof(pDrvInfo->inst1) );
		}
	} else
	{
		strncpy( pDrvInfo->inst1, p, sizeof(pDrvInfo->inst1) );

	}
	

	HKEY hregkey;
	char regkey[256];
	char regval[256*2]={0};
	SPRINTF( regkey, sizeof(regkey), "SYSTEM\\CurrentControlSet\\Enum\\%s\\%s\\%s", 
			 pDrvInfo->device_type, pDrvInfo->device_name, pDrvInfo->device_inst );

	if ( RegOpenKeyEx(HKEY_LOCAL_MACHINE, regkey, 0, KEY_READ, &hregkey) == ERROR_SUCCESS )
	{
		    long ret;
			DWORD hType;
			DWORD hSize;

			memset( regval, 0x0, sizeof(regval) );
			hSize = sizeof(regval)-1;
			ret = RegQueryValueEx( hregkey, "DeviceDesc", 0, &hType, (LPBYTE)regval, &hSize);
			if ( ret == ERROR_SUCCESS )
			{
				strncpy( pDrvInfo->device_desc, regval, sizeof(pDrvInfo->device_desc) );
			}

			memset( regval, 0x0, sizeof(regval) );
			hSize = sizeof(regval)-1;
			ret = RegQueryValueEx( hregkey, "Mfg", 0, &hType, (LPBYTE)regval, &hSize);
			if ( ret == ERROR_SUCCESS )
			{
				strncpy( pDrvInfo->mfg, ExtractMfg( regval, sizeof(regval) ), sizeof(pDrvInfo->mfg) );
			}
			memset( regval, 0x0, sizeof(regval) );
			hSize = sizeof(regval)-1;
			ret = RegQueryValueEx( hregkey, "HardwareID", 0, &hType, (LPBYTE)regval, &hSize);
			if ( ret == ERROR_SUCCESS || ret == ERROR_MORE_DATA )
			{
				strncpy( pDrvInfo->hardware_id, ExtractHardwareID( regval, sizeof(regval) ),
					                            sizeof(pDrvInfo->hardware_id) );
			}
			hSize = sizeof(regval)-1;
			memset( regval, 0x0, sizeof(regval) );
			RegQueryValueEx( hregkey, "LocationInformation", 0, &hType, (LPBYTE)regval, &hSize);
			RegCloseKey( hregkey );
			pDrvInfo->state = 1;
	} else
	{
			pDrvInfo->state = -1;
	}


	if ( regval[0] != 0 )
	{
		if ( strlen( pDrvInfo->device_type ) + strlen(pDrvInfo->device_name) + strlen( pDrvInfo->device_name) < sizeof(pDrvInfo->regkey) )
		   SPRINTF( pDrvInfo->regkey, sizeof(pDrvInfo->regkey), "\\%s\\%s\\%s", pDrvInfo->device_type, pDrvInfo->device_name, pDrvInfo->device_inst );
		
		int pci=0, dev=0, fun=0;

		//vista has format as "@system32\drivers\pci.sys,#65536;PCI bus %1, device %2, function %3;(7,8,0)"
		if ( ( regval[0] == '@' ) && strstr( regval, "PCI bus %1, device %2, function %3;" ) != NULL )
		{
			p = strstr( regval, ";(" );
			if ( p != NULL )
				pci = atoi(p+2);

			pe = strstr( p+2, "," );
			if ( pe != NULL )
				dev = atoi( pe+1 );

			pf = strstr( pe+1, "," );
			if ( pf != NULL )
				fun = atoi( pf+1 );

		} else
		{
			p = strstr( regval, "PCI bus" );
			if ( p != NULL )
				pci = atoi(p+7);

			pe = strstr( regval, "device" );
			if ( pe != NULL )
				dev = atoi(pe+6);

			pf = strstr( regval, "function" );
			if ( pf != NULL )
				fun = atoi(pf+8);
		}

		if ( p != NULL && pe != NULL )
		{
			char tmp[128];
			SPRINTF( tmp, sizeof(tmp), "%s:%d-%d", pDrvInfo->device_type, pci, dev );
			strncpy( pDrvInfo->hardware_loc, tmp, sizeof(pDrvInfo->hardware_loc) );
		} else
		{
			char tmp[128];
			SPRINTF( tmp, sizeof(tmp), "%s:%s", pDrvInfo->device_type, regval );
			strncpy( pDrvInfo->hardware_loc, tmp, sizeof(pDrvInfo->hardware_loc) );
		}
	} else
	{
		pDrvInfo->state = -2;
	}

	return S_OK;

}


int mystrcmp(const char* s1, const char* s2)
{
	do
	{
		char c1 = *s1;
		char c2 = *s2; 
		s1++;
		s2++;
		if (!c1 && !c2)
			return 0;
		if (!c1)
			return -1;
		if (!c2)
			return 1;
		if (c1 < '0' || (c1 > '9' && c1 < 'A') || (c1 > 'Z' && c1 < 'a') || c1 > 'z')
			continue;
		if (c2 < '0' || (c2 > '9' && c2 < 'A') || (c2 > 'Z' && c2 < 'a') || c2 > 'z')
			continue;
		if (c1 >= 'A' && c1 <= 'Z' && c2 == c1 + 32) // case insensitive
			continue;
		if (c2 >= 'A' && c2 <= 'Z' && c1 == c2 + 32) // case insensitive
			continue;
		if (c1 != c2)
			return (int) (c1 - c2);
	}while (true);
	return 0;
}

HRESULT FindFilterByName(IBaseFilter** pFilter, REFCLSID filterCat,
	const char* filterName, int deviceIndex, char* ppDevName, int nDevNameSize)
{
    HRESULT hr;
    ICreateDevEnum *pSysDevEnum = NULL;
    IEnumMoniker *pEnum = NULL;
    IMoniker *pMoniker = NULL;
	// If the filter name starts with a '{' then we load it by CLSID instead
	if (filterName[0] == '{')
	{
		CLSID filterClsid;
		WCHAR filterClsidWsz[64];
		MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, filterName, -1, filterClsidWsz, 64);
		hr = CLSIDFromString(filterClsidWsz, &filterClsid);
		if (FAILED(hr))
			return hr;
		hr = CoCreateInstance(filterClsid, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void **)pFilter);
		return hr;
	}

    hr = CoCreateInstance(CLSID_SystemDeviceEnum, NULL, 
        CLSCTX_INPROC_SERVER, IID_ICreateDevEnum, 
        (void**)&pSysDevEnum);
	if (FAILED(hr))
		return E_FAIL;
    hr = pSysDevEnum->CreateClassEnumerator(filterCat, &pEnum, 0);
	if (FAILED(hr))
	{
		pSysDevEnum->Release();
		return E_FAIL;
	}
    
	if (ppDevName)
		ppDevName[0] = '\0';
	if (hr != S_OK)
	{
		// Nothing to enumerate
		SAFE_RELEASE(pSysDevEnum);
		return E_FAIL;
	}
    while (S_OK == pEnum->Next(1, &pMoniker, NULL))
    {
        IPropertyBag *pPropBag = NULL;
        hr = pMoniker->BindToStorage(0, 0, IID_IPropertyBag, 
                                (void **)&pPropBag);
		if (SUCCEEDED(hr))
		{
			VARIANT var;
			VariantInit(&var);
			hr = pPropBag->Read(L"FriendlyName", &var, 0);
			if (SUCCEEDED(hr))
			{
				char conv[512];
				WideCharToMultiByte(CP_UTF8, 0, var.bstrVal, -1, conv, 512, 0, 0);
				VariantClear(&var);
				if (mystrcmp(conv, filterName)/*lstrcmpi(conv, filterName)*/ == 0)
				{
					if (deviceIndex == 0)
					{
        				hr = BindMoniker(pMoniker, 0, IID_IBaseFilter,
        					(void**)pFilter);
        				if (FAILED(hr))
						{
							SAFE_RELEASE(pPropBag);
							SAFE_RELEASE(pMoniker);
							SAFE_RELEASE(pEnum);
							SAFE_RELEASE(pSysDevEnum);
        					return hr;
						}

						if (ppDevName)
						{
							VariantInit(&var);
							HRESULT hr2 = pPropBag->Read(L"DevicePath", &var, 0);
							if (SUCCEEDED(hr2))
							{
								WideCharToMultiByte(CP_ACP, 0, var.bstrVal, -1, conv, 512, 0, 0);
								STRCPY(ppDevName, nDevNameSize, conv);
								//slog((env, "FOUND DEVICE:%s\r\n", conv));
							} else
							{   
								//software network device doesn't have devicepath such as SillionDust' HDHomerun
								//I use display name as its device name
								LPOLESTR pStr = NULL;
								IBindCtx *pBindCtx;
								ppDevName[0] = 0; 
								hr2 = CreateBindCtx(0, &pBindCtx);
								if (SUCCEEDED(hr2))
								{
									hr2 = pMoniker->GetDisplayName(pBindCtx, NULL, &pStr);

									if (SUCCEEDED(hr2))
									{
										WideCharToMultiByte(CP_ACP, 0, pStr, -1, conv, 512, 0, 0);
										STRCPY(ppDevName,  nDevNameSize, conv);
										//slog((env, "Filter doesn't DevicePath, use display name as DevicePath:%s\r\n", ppDevName));
										CoTaskMemFree(pStr);
									}
									pBindCtx->Release();
								}
							}

							VariantClear(&var);  
						}
						SAFE_RELEASE(pPropBag);
						SAFE_RELEASE(pMoniker);
						SAFE_RELEASE(pEnum);
						SAFE_RELEASE(pSysDevEnum);
						return hr;
					}
					else
						deviceIndex--;
				}
			}
		}
        SAFE_RELEASE(pPropBag);
        SAFE_RELEASE(pMoniker);
    }

    SAFE_RELEASE(pEnum);
    SAFE_RELEASE(pSysDevEnum);
	*pFilter = NULL;
    return E_INVALIDARG; // name can't be found
}

HRESULT FindFilterByNameDebug( JNIEnv *env,IBaseFilter** pFilter, REFCLSID filterCat,
	const char* filterName, int deviceIndex, char* ppDevName, int nDevNameSize)
{
    HRESULT hr;
    ICreateDevEnum *pSysDevEnum = NULL;
    IEnumMoniker *pEnum = NULL;
    IMoniker *pMoniker = NULL;
	// If the filter name starts with a '{' then we load it by CLSID instead
	if (filterName[0] == '{')
	{
		CLSID filterClsid;
		WCHAR filterClsidWsz[64];
		MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, filterName, -1, filterClsidWsz, 64);
		hr = CLSIDFromString(filterClsidWsz, &filterClsid);
		if (FAILED(hr))
			return hr;
		hr = CoCreateInstance(filterClsid, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void **)pFilter);
		return hr;
	}

    hr = CoCreateInstance(CLSID_SystemDeviceEnum, NULL, 
        CLSCTX_INPROC_SERVER, IID_ICreateDevEnum, 
        (void**)&pSysDevEnum);
	if (FAILED(hr))
		return E_FAIL;
    hr = pSysDevEnum->CreateClassEnumerator(filterCat, &pEnum, 0);
	if (FAILED(hr))
	{
		pSysDevEnum->Release();
		return E_FAIL;
	}
    
	if (ppDevName)
		ppDevName[0] = '\0';
	if (hr != S_OK)
	{
		// Nothing to enumerate
		SAFE_RELEASE(pSysDevEnum);
		return E_FAIL;
	}
    while (S_OK == pEnum->Next(1, &pMoniker, NULL))
    {
        IPropertyBag *pPropBag = NULL;
        hr = pMoniker->BindToStorage(0, 0, IID_IPropertyBag, 
                                (void **)&pPropBag);
		if (SUCCEEDED(hr))
		{
			VARIANT var;
			VariantInit(&var);
			hr = pPropBag->Read(L"FriendlyName", &var, 0);
			if (SUCCEEDED(hr))
			{
				char conv[512];
				WideCharToMultiByte(CP_UTF8, 0, var.bstrVal, -1, conv, 512, 0, 0);
				VariantClear(&var);
				slog(( env, "@DEBUG@ device found '%s' (index:%d) for '%s'.\r\n", conv, deviceIndex, filterName ));
				if (mystrcmp(conv, filterName)/*lstrcmpi(conv, filterName)*/ == 0)
				{
					if (deviceIndex == 0)
					{
        				hr = BindMoniker(pMoniker, 0, IID_IBaseFilter,
        					(void**)pFilter);
        				if (FAILED(hr))
						{
							SAFE_RELEASE(pPropBag);
							SAFE_RELEASE(pMoniker);
							SAFE_RELEASE(pEnum);
							SAFE_RELEASE(pSysDevEnum);
        					return hr;
						}

						if (ppDevName)
						{
							VariantInit(&var);
							HRESULT hr2 = pPropBag->Read(L"DevicePath", &var, 0);
							if (SUCCEEDED(hr2))
							{
								WideCharToMultiByte(CP_ACP, 0, var.bstrVal, -1, conv, 512, 0, 0);
								STRCPY(ppDevName, nDevNameSize, conv);
								//slog((env, "FOUND DEVICE:%s\r\n", conv));
							} else
							{   
								//software network device doesn't have devicepath such as SillionDust' HDHomerun
								//I use display name as its device name
								LPOLESTR pStr = NULL;
								IBindCtx *pBindCtx;
								ppDevName[0] = 0; 
								hr2 = CreateBindCtx(0, &pBindCtx);
								if (SUCCEEDED(hr2))
								{
									hr2 = pMoniker->GetDisplayName(pBindCtx, NULL, &pStr);

									if (SUCCEEDED(hr2))
									{
										WideCharToMultiByte(CP_ACP, 0, pStr, -1, conv, 512, 0, 0);
										STRCPY(ppDevName, nDevNameSize, conv);
										slog((env, "@DEBUG@ Filter doesn't DevicePath, use display name as DevicePath:%s\r\n", ppDevName));
										CoTaskMemFree(pStr);
									}
									pBindCtx->Release();
								}
							}

							VariantClear(&var);  
						}
						SAFE_RELEASE(pPropBag);
						SAFE_RELEASE(pMoniker);
						SAFE_RELEASE(pEnum);
						SAFE_RELEASE(pSysDevEnum);
						return hr;
					}
					else
						deviceIndex--;
				}
			}
		}
        SAFE_RELEASE(pPropBag);
        SAFE_RELEASE(pMoniker);
    }

    SAFE_RELEASE(pEnum);
    SAFE_RELEASE(pSysDevEnum);
	*pFilter = NULL;
    return E_INVALIDARG; // name can't be found
}



HRESULT FindFilterByName2(IBaseFilter** pFilter, REFCLSID filterCat,
	const char* filterName, int deviceIndex, char* ppFriendName, int nFriendNameSize )
{
    HRESULT hr;
    ICreateDevEnum *pSysDevEnum = NULL;
    IEnumMoniker *pEnum = NULL;
    IMoniker *pMoniker = NULL;
	// If the filter name starts with a '{' then we load it by CLSID instead
	if (filterName[0] == '{')
	{
		CLSID filterClsid;
		WCHAR filterClsidWsz[64];
		MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, filterName, -1, filterClsidWsz, 64);
		hr = CLSIDFromString(filterClsidWsz, &filterClsid);
		if (FAILED(hr))
			return hr;
		hr = CoCreateInstance(filterClsid, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void **)pFilter);
		return hr;
	}

    hr = CoCreateInstance(CLSID_SystemDeviceEnum, NULL, 
        CLSCTX_INPROC_SERVER, IID_ICreateDevEnum, 
        (void**)&pSysDevEnum);
	if (FAILED(hr))
		return E_FAIL;
    hr = pSysDevEnum->CreateClassEnumerator(filterCat, &pEnum, 0);
	if (FAILED(hr))
	{
		pSysDevEnum->Release();
		return E_FAIL;
	}
    
	if (ppFriendName)
		ppFriendName[0] = '\0';
	if (hr != S_OK)
	{
		// Nothing to enumerate
		SAFE_RELEASE(pSysDevEnum);
		return E_FAIL;
	}
    while (S_OK == pEnum->Next(1, &pMoniker, NULL))
    {
        IPropertyBag *pPropBag = NULL;
        hr = pMoniker->BindToStorage(0, 0, IID_IPropertyBag, 
                                (void **)&pPropBag);
		if (SUCCEEDED(hr))
		{
			VARIANT var;
			VariantInit(&var);
			hr = pPropBag->Read(L"FriendlyName", &var, 0);
			if (SUCCEEDED(hr))
			{
				char conv[512];
				WideCharToMultiByte(CP_UTF8, 0, var.bstrVal, -1, conv, 512, 0, 0);
				VariantClear(&var);
				if (mystrcmp(conv, filterName)/*lstrcmpi(conv, filterName)*/ == 0)
				{
					if (deviceIndex == 0)
					{
        				hr = BindMoniker(pMoniker, 0, IID_IBaseFilter,
        					(void**)pFilter);
        				if (FAILED(hr))
						{
							SAFE_RELEASE(pPropBag);
							SAFE_RELEASE(pMoniker);
							SAFE_RELEASE(pEnum);
							SAFE_RELEASE(pSysDevEnum);
        					return hr;
						}

						if (ppFriendName)
						{
							LPOLESTR pStr = NULL;
							IBindCtx *pBindCtx;
							hr = CreateBindCtx(0, &pBindCtx);
							if (SUCCEEDED(hr))
							{
								hr = pMoniker->GetDisplayName(pBindCtx, NULL, &pStr);

								if (SUCCEEDED(hr))
								{
									WideCharToMultiByte(CP_ACP, 0, pStr, -1, conv, 512, 0, 0);
									//slog((env, "FOUND DEVICE:%s\r\n", conv));
									STRCPY( ppFriendName, nFriendNameSize, conv);
									CoTaskMemFree(pStr);
								}
								pBindCtx->Release();
							}
						}
						SAFE_RELEASE(pPropBag);
						SAFE_RELEASE(pMoniker);
						SAFE_RELEASE(pEnum);
						SAFE_RELEASE(pSysDevEnum);
						return hr;
					}
					else
						deviceIndex--;
				}
			}
		}
        SAFE_RELEASE(pPropBag);
        SAFE_RELEASE(pMoniker);
    }

    SAFE_RELEASE(pEnum);
    SAFE_RELEASE(pSysDevEnum);
	*pFilter = NULL;
    return E_INVALIDARG; // name can't be found
}

HRESULT FindFilterByDeviceName(IBaseFilter** pFilter, REFCLSID filterCat,
	int deviceIndex, char* pDeviceName)
{
    HRESULT hr;
    ICreateDevEnum *pSysDevEnum = NULL;
    IEnumMoniker *pEnum = NULL;
    IMoniker *pMoniker = NULL;

    hr = CoCreateInstance(CLSID_SystemDeviceEnum, NULL, 
        CLSCTX_INPROC_SERVER, IID_ICreateDevEnum, 
        (void**)&pSysDevEnum);
	if (FAILED(hr))
		return E_FAIL;
    hr = pSysDevEnum->CreateClassEnumerator(filterCat, &pEnum, 0);
    
	if (hr != S_OK)
	{
		// Nothing to enumerate
		SAFE_RELEASE(pSysDevEnum);
		return E_FAIL;
	}
    while (S_OK == pEnum->Next(1, &pMoniker, NULL))
    {
        IPropertyBag *pPropBag = NULL;
        hr = pMoniker->BindToStorage(0, 0, IID_IPropertyBag, 
                                (void **)&pPropBag);
		if (SUCCEEDED(hr))
		{
			VARIANT var;
			VariantInit(&var);
			hr = pPropBag->Read(L"DevicePath", &var, 0);
			if (SUCCEEDED(hr))
			{
				char conv[512];
				WideCharToMultiByte(CP_UTF8, 0, var.bstrVal, -1, conv, 512, 0, 0);
				if (strstr(conv, pDeviceName) == conv)
				{
					if (deviceIndex == 0)
					{
        				//slog((env, "FOUND DEVICE:%s\r\n", pDeviceName));
        				hr = pMoniker->BindToObject(NULL, NULL, IID_IBaseFilter,
        					(void**)pFilter);
        				if (FAILED(hr))
        				{
        					//elog((env, "Failed getting %s device hr=0x%x\r\n", pDeviceName, hr));
							SAFE_RELEASE(pPropBag);
							SAFE_RELEASE(pMoniker);
							SAFE_RELEASE(pEnum);
							SAFE_RELEASE(pSysDevEnum);
        					return hr;
        				}
						VariantClear(&var);
						/*VariantInit(&var);
						hr = pPropBag->Read(L"FriendlyName", &var, 0);
						if (SUCCEEDED(hr))
						{
							WideCharToMultiByte(CP_ACP, 0, var.bstrVal, -1, conv, 512, 0, 0);
							//slog((env, "FriendlyName:%s\r\n", conv));
						}
						VariantClear(&var);*/
						SAFE_RELEASE(pPropBag);
						SAFE_RELEASE(pMoniker);
						SAFE_RELEASE(pEnum);
						SAFE_RELEASE(pSysDevEnum);
						return hr;
					}
					else
						deviceIndex--;
				}
			}
			VariantClear(&var);
		}
        SAFE_RELEASE(pPropBag);
        SAFE_RELEASE(pMoniker);
    }

    SAFE_RELEASE(pEnum);
    SAFE_RELEASE(pSysDevEnum);
	*pFilter = NULL;
//	elog((env, "Failed to find:%s\r\n", pDeviceName));
    return E_INVALIDARG; // name can't be found
}

IPin* FindPinByName(IBaseFilter* pFilter, jstring jPinName, JNIEnv* env, PIN_DIRECTION dir)
{
	const char* cname = env->GetStringUTFChars(jPinName, 0);
	IPin* rv = FindPinByName(pFilter, cname, dir);
	env->ReleaseStringUTFChars(jPinName, cname);
	return rv;
}

IPin* FindPinByName(IBaseFilter* pFilter, LPCSTR szPinName, PIN_DIRECTION dir)
{
    HRESULT hr;
	if (!pFilter)
		return NULL;
	//
	// Enumerate all the pins
	//
    IEnumPins* pEm = NULL;
    hr = pFilter->EnumPins(&pEm);
    if (hr != NOERROR)
		return NULL;

	//
	// Examine each pin
	//
    pEm->Reset();
    ULONG cFetched;
    IPin* pPin = NULL;
    while(hr = pEm->Next(1, &pPin, &cFetched), hr == S_OK)
    {
		char szFriendlyName[128];
		PIN_INFO PinInfo;
		//
		// grab this, so we can examine its name field
		//
	    hr = pPin->QueryPinInfo(&PinInfo);
	    if(SUCCEEDED(hr))
		{
			WideCharToMultiByte(CP_UTF8, 0, PinInfo.achName, -1,
						szFriendlyName, 128, NULL, NULL);
			PinInfo.pFilter->Release();
			//
			// check direction
			//
			if (PinInfo.dir == dir)
			{
				//
				// perform name comparison -- did we get the right one?
				//
				if (!szPinName || strlen(szPinName) == 0 ||
                    (stricmp(szFriendlyName, szPinName)==0))
				{
					pEm->Release();
					// calling code should call pin's Release()
					return pPin;
				}
			}
		}
		pPin->Release();
	}
    pEm->Release();
	return NULL;
}

HRESULT ConnectPins(IGraphBuilder* pGraph, IBaseFilter* outputFilter,
					const char* outPinName, IBaseFilter* inputFilter,
					const char* inPinName, BOOL directConnect)
{
	IPin* outPin = FindPinByName(outputFilter, outPinName, PINDIR_OUTPUT);
	if (outPin == 0)
		return E_FAIL;
	IPin* inPin = FindPinByName(inputFilter, inPinName, PINDIR_INPUT);
	if (inPin == 0)
	{
		SAFE_RELEASE(outPin);
		return E_FAIL;
	}
	HRESULT hr;
	if (directConnect)
		hr = pGraph->ConnectDirect(outPin, inPin, NULL);
	else
		hr = pGraph->Connect(outPin, inPin);
	SAFE_RELEASE(inPin);
	SAFE_RELEASE(outPin);
	return hr;
}

HRESULT ConnectPinsWithDump(IGraphBuilder* pGraph, IPin* outputPin, IPin* inputPin)
{
	CComPtr<IBaseFilter> infTee = NULL;
	HRESULT hr = CoCreateInstance(CLSID_InfTee, NULL, CLSCTX_INPROC_SERVER,
		IID_IBaseFilter, (void**)&(infTee.p));
	if (FAILED(hr)) return hr;
	hr = pGraph->AddFilter(infTee, L"Infinite Tee");
	if (FAILED(hr)) return hr;

	// Don't set the filename, we're just using the debug log
	CComPtr<IBaseFilter> dump = NULL;
	hr = CoCreateInstance(CLSID_MPEG2Dump, NULL, CLSCTX_INPROC_SERVER,
		IID_IBaseFilter, (void**)&(dump.p));
	if (FAILED(hr)) return hr;
	hr = pGraph->AddFilter(dump, L"Dump");
	if (FAILED(hr)) return hr;

	IPin* infTeeIn = NULL;
	infTeeIn = FindPin(infTee, PINDIR_INPUT, NULL, NULL);
	ASSERT(infTeeIn);
	hr = pGraph->ConnectDirect(outputPin, infTeeIn, NULL);
	SAFE_RELEASE(infTeeIn);
	if (FAILED(hr)) return hr;

	IPin* infTeeOut = NULL;
	infTeeOut = FindPin(infTee, PINDIR_OUTPUT, NULL, NULL);
	ASSERT(infTeeOut);
	IPin* dumpIn = NULL;
	dumpIn = FindPin(dump, PINDIR_INPUT, NULL, NULL);
	ASSERT(dumpIn);
	hr = pGraph->ConnectDirect(infTeeOut, dumpIn, NULL);
	SAFE_RELEASE(infTeeOut);
	SAFE_RELEASE(dumpIn);
	if (FAILED(hr)) return hr;

	infTeeOut = FindUnconnectedPin(infTee, PINDIR_OUTPUT, FALSE);
	ASSERT(infTeeOut);
	hr = pGraph->ConnectDirect(infTeeOut, inputPin, NULL);
	SAFE_RELEASE(infTeeOut);
	return hr;
}

IPin* FindUnconnectedPin(IBaseFilter* pFilter, PIN_DIRECTION desiredDir, BOOL connectedInstead)
{
    IEnumPins* pEnum = NULL;
    HRESULT hr = pFilter->EnumPins(&pEnum);
    if(FAILED(hr)) 
        return NULL;

    ULONG ulFound;
    IPin *pPin = NULL;
    hr = E_FAIL;

    while(S_OK == pEnum->Next(1, &pPin, &ulFound))
    {
        PIN_DIRECTION pindir;
        pPin->QueryDirection(&pindir);
        if(pindir == desiredDir)
        {
			IPin* dummyPin = NULL;
			hr = pPin->ConnectedTo(&dummyPin);
			SAFE_RELEASE(dummyPin);
			if ((hr == VFW_E_NOT_CONNECTED && !connectedInstead) ||
				(hr == S_OK && connectedInstead))
			{
				// Found an unconnected one
				SAFE_RELEASE(pEnum);
				return pPin;
			}
        } 

        pPin->Release();
    } 
    SAFE_RELEASE(pEnum);

    return NULL;
}

IPin* FindPinByCategoryAndType(IBaseFilter* pFilter, const GUID* pinCategory, const GUID* majorType)
{
    IEnumPins* pEnum = NULL;
    HRESULT hr = pFilter->EnumPins(&pEnum);
    if(FAILED(hr)) 
        return NULL;

    ULONG ulFound;
    IPin *pPin = NULL;
    hr = E_FAIL;

    while(S_OK == pEnum->Next(1, &pPin, &ulFound))
    {
		IEnumMediaTypes *mtEnum = NULL;
		hr = pPin->EnumMediaTypes(&mtEnum);
		if (!SUCCEEDED(hr))
		{
			pPin->Release();
			continue;
		}
		AM_MEDIA_TYPE *pMT = NULL;
		while (S_OK == mtEnum->Next(1, &pMT, NULL))
		{
			if (!majorType || pMT->majortype == *majorType)
			{
				// Check the pins category
			    IKsPropertySet *pKs;
			    hr = pPin->QueryInterface(IID_IKsPropertySet, (void **)&pKs);
			    if (SUCCEEDED(hr))
			    {
					// Try to retrieve the pin category.
					DWORD cbReturned;
					GUID currPinCategory;
					hr = pKs->Get(AMPROPSETID_Pin, AMPROPERTY_PIN_CATEGORY, NULL, 0, 
						&currPinCategory, sizeof(GUID), &cbReturned);
				    pKs->Release();
					if (SUCCEEDED(hr) && (*pinCategory == currPinCategory))
					{
						DeleteMediaType(pMT);
						SAFE_RELEASE(mtEnum);
						SAFE_RELEASE(pEnum);
						return pPin;
					}
				}
			}
			DeleteMediaType(pMT);
        }
		SAFE_RELEASE(mtEnum);
        pPin->Release();
    } 
    SAFE_RELEASE(pEnum);

    return NULL;
}

IPin* FindPin(IBaseFilter* pFilter, PIN_DIRECTION desiredDir, const GUID* majorType, const GUID* subType)
{
    IEnumPins* pEnum = NULL;
    HRESULT hr = pFilter->EnumPins(&pEnum);
    if(FAILED(hr)) 
        return NULL;

    ULONG ulFound;
    IPin *pPin = NULL;
    hr = E_FAIL;

    while(S_OK == pEnum->Next(1, &pPin, &ulFound))
    {
        PIN_DIRECTION pindir;
        pPin->QueryDirection(&pindir);
        if(pindir == desiredDir)
        {
			if (!majorType && !subType)
			{
				SAFE_RELEASE(pEnum);
				return pPin;
			}
			IEnumMediaTypes *mtEnum = NULL;
			hr = pPin->EnumMediaTypes(&mtEnum);
			if (SUCCEEDED(hr))
			{
				AM_MEDIA_TYPE *pMT = NULL;
				while (S_OK == mtEnum->Next(1, &pMT, NULL))
				{
					if ((!majorType || pMT->majortype == *majorType) && (!subType || pMT->subtype == *subType))
					{
						DeleteMediaType(pMT);
						SAFE_RELEASE(mtEnum);
						SAFE_RELEASE(pEnum);
						return pPin;
					}
					DeleteMediaType(pMT);
				}
			}
			SAFE_RELEASE(mtEnum);
        } 

        pPin->Release();
    } 
    SAFE_RELEASE(pEnum);

    return NULL;
}

void GetAllOutputFilters(IFilterGraph* pGraph, IBaseFilter* pFilter, CGenericList<IBaseFilter> *outputFilters)
{
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
				IPin* pNextPin = NULL;
				hr = pPin->ConnectedTo(&pNextPin);
				if (SUCCEEDED(hr) && pNextPin)
				{
					PIN_INFO pInf;
					hr = pNextPin->QueryPinInfo(&pInf);
					if (SUCCEEDED(hr) && pInf.pFilter)
					{
						if (!outputFilters->Find(pInf.pFilter))
						{
							outputFilters->AddTail(pInf.pFilter);
							GetAllOutputFilters(pGraph, pInf.pFilter, outputFilters);
						}
						else
						{
							SAFE_RELEASE(pInf.pFilter);
						}
					}
				}
				SAFE_RELEASE(pNextPin);
			}
			SAFE_RELEASE(pPin);
		}
		SAFE_RELEASE(pPinEnum);
	}
}
void RemoveAllOutputFilters(IFilterGraph* pGraph, IBaseFilter* pFilter)
{
	// Determine what the filters are that were used before disconnecting them because
	// disconnecting one pin can cause other ones to be broken too.
    CGenericList<IBaseFilter> outputFilters("Filters");
	GetAllOutputFilters(pGraph, pFilter, &outputFilters);

	// Disconnect all the output pins on the capture filter
/*	IEnumPins* pPinEnum = NULL;
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
				pGraph->Disconnect(pPin);
			}
			SAFE_RELEASE(pPin);
		}
		SAFE_RELEASE(pPinEnum);
	}
*/
	// Disconnect all of the pins on the other filters and remove them from the graph
	IBaseFilter* currFilter = outputFilters.RemoveHead();
	while (currFilter)
	{
/*		IEnumPins* pPinEnum = NULL;
		HRESULT hr = currFilter->EnumPins(&pPinEnum);
		if (SUCCEEDED(hr))
		{
			IPin* pPin = NULL;
			while (S_OK == pPinEnum->Next(1, &pPin, NULL))
			{
				pGraph->Disconnect(pPin);
				SAFE_RELEASE(pPin);
			}
		}
		SAFE_RELEASE(pPinEnum);
*/		pGraph->RemoveFilter(currFilter);
		SAFE_RELEASE(currFilter);
		currFilter = outputFilters.RemoveHead();
	}
}

void WaitForEvent(JNIEnv* env, IFilterGraph* pGraph, long evtCode)
{
	if (WAIT_FOR_COMPLETIONS)
	{
		HANDLE  hEvent; 
		long    evCode, param1, param2;
		BOOLEAN bDone = FALSE;
		HRESULT hr = S_OK;
		CComPtr<IMediaEvent> pEvent = NULL;
		hr = pGraph->QueryInterface(IID_IMediaEvent, (void**)&(pEvent.p));
		hr = pEvent->GetEventHandle((OAEVENT*)&hEvent);
		if (SUCCEEDED(hr))
		{
			int numWaits = 0;
			while (!bDone && (numWaits++ < 30)) 
			{
				if (WAIT_OBJECT_0 == WaitForSingleObject(hEvent, 500))
				{ 
					while (hr = pEvent->GetEvent(&evCode, &param1, &param2, 0), SUCCEEDED(hr)) 
					{
						slog((env, "Event code: %#04x\n Params: %d, %d\r\n", evCode, param1, param2));
						pEvent->FreeEventParams(evCode, param1, param2);
						bDone = (evtCode == evCode);
					}
				}
			} 
		}
	}
}

BOOL WaitForState(JNIEnv* env, IMediaControl* pMC, FILTER_STATE graphState)
{
	if (WAIT_FOR_COMPLETIONS)
	{
		/*
		 * The only reason we added this was to help with PVR350OSD sync issues. But
		 * apparently its causing problems so disable it unless they're using the 350 decoder.
		 */
		slog((env, "Waiting for filtergraph to enter state %d\r\n", graphState));
		FILTER_STATE testGraphState;
		HRESULT hr = pMC->GetState(5000, (OAFilterState*)&testGraphState);
		if (FAILED(hr))
		{
			elog((env, "FAILED Waiting for filtergraph to enter state %d\r\n", graphState));
			return FALSE;
		}
		else
		{
			slog((env, "Filtergraph entered state %d hr=0x%x\r\n", testGraphState, hr));
			return graphState == testGraphState;
		}
	}
	else
		return TRUE;
}

void throwEncodingException(JNIEnv* env, jint errCode, HRESULT hr)
{
	static jclass encExcClass = (jclass) env->NewGlobalRef(env->FindClass("sage/EncodingException"));
	static jmethodID encConstMeth = env->GetMethodID(encExcClass, "<init>", "(II)V");
	jthrowable throwie = (jthrowable) env->NewObject(encExcClass, encConstMeth, errCode, (jint) hr);
	env->Throw(throwie);
}

void throwPlaybackException(JNIEnv* env, jint errCode, HRESULT hr)
{
	static jclass excClass = (jclass) env->NewGlobalRef(env->FindClass("sage/PlaybackException"));
	static jmethodID constMeth = env->GetMethodID(excClass, "<init>", "(II)V");
	jthrowable throwie = (jthrowable) env->NewObject(excClass, constMeth, errCode, (jint) hr);
	env->Throw(throwie);
}

void throwNPE(JNIEnv* env)
{
	static jclass npeExcClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/NullPointerException"));
	static jmethodID constMeth = env->GetMethodID(npeExcClass, "<init>", "()V");
	jthrowable throwie = (jthrowable) env->NewObject(npeExcClass, constMeth);
	env->Throw(throwie);
}

HRESULT GetPin( IBaseFilter * pFilter, PIN_DIRECTION dirrequired, int iNum, IPin **ppPin)
{
    IEnumPins* pEnum = NULL;
    *ppPin = NULL;
    HRESULT hr = pFilter->EnumPins(&pEnum);
    if(FAILED(hr)) 
        return hr;

    ULONG ulFound;
    IPin *pPin = NULL;
    hr = E_FAIL;

    while(S_OK == pEnum->Next(1, &pPin, &ulFound))
    {
        PIN_DIRECTION pindir = (PIN_DIRECTION)3;
        pPin->QueryDirection(&pindir);
        if(pindir == dirrequired)
        {
            if(iNum == 0)
            {
                *ppPin = pPin;
                // Found requested pin, so clear error
                hr = S_OK;
                break;
            }
            iNum--;
        } 

        pPin->Release();
    } 
    SAFE_RELEASE(pEnum);

    return hr;
}

IBaseFilter* FindRendererFilter(IGraphBuilder* pGraph, const GUID* testType)
{
	IEnumFilters *pEnum = NULL;
	HRESULT hr = pGraph->EnumFilters(&pEnum);
	if (SUCCEEDED(hr))
	{
		IBaseFilter *currFilt = NULL;
		while (pEnum->Next(1, &currFilt, NULL) == S_OK)
		{
			IPin *inPin = FindPinByName(currFilt, NULL, PINDIR_INPUT);
			IPin *outPin = FindPinByName(currFilt, NULL, PINDIR_OUTPUT);
			if (inPin && !outPin)
			{
				// Input and no output, check to see if it's audio
				IEnumMediaTypes *mtEnum = NULL;
				hr = inPin->EnumMediaTypes(&mtEnum);
				if (SUCCEEDED(hr))
				{
					AM_MEDIA_TYPE *pMT = NULL;
					while (S_OK == mtEnum->Next(1, &pMT, NULL))
					{
						if (pMT->majortype == *testType)
						{
							DeleteMediaType(pMT);
							SAFE_RELEASE(mtEnum);
							SAFE_RELEASE(inPin);
							SAFE_RELEASE(pEnum);
							return currFilt;
						}
						DeleteMediaType(pMT);
					}
				}
				SAFE_RELEASE(mtEnum);
			}
			SAFE_RELEASE(outPin);
			SAFE_RELEASE(inPin);
			SAFE_RELEASE(currFilt);
		}
	}
	SAFE_RELEASE(pEnum);
	return NULL;
}

HRESULT FindMatchingMedium(
    IPin *pPin, 
    REGPINMEDIUM *pMedium, 
    bool *pfMatch)
{
    IKsPin* pKsPin = NULL;
    KSMULTIPLE_ITEM *pmi;

    *pfMatch = false;
    HRESULT hr = pPin->QueryInterface(IID_IKsPin, (void **)&pKsPin);
    if (FAILED(hr)) 
        return hr;  // Pin does not support IKsPin.

    hr = pKsPin->KsQueryMediums(&pmi);
    pKsPin->Release();
    if (FAILED(hr))
        return hr;  // Pin does not support mediums.

    if (pmi->Count) 
    {
        // Use pointer arithmetic to reference the first medium structure.
        REGPINMEDIUM *pTemp = (REGPINMEDIUM*)(pmi + 1);
        for (ULONG i = 0; i < pmi->Count; i++, pTemp++) 
        {
            if (pMedium->clsMedium == pTemp->clsMedium &&
				pMedium->dw1 == pTemp->dw1)
            {
                *pfMatch = true;
                break;
            }
        }
    }        
    CoTaskMemFree(pmi);
    return S_OK;
}

HRESULT GetPinMedium( KSMULTIPLE_ITEM **ppmi, IPin* pPin )
{
    IKsPin* pKsPin = NULL;

    HRESULT hr = pPin->QueryInterface(IID_IKsPin, (void **)&pKsPin);
    if (FAILED(hr))
        return hr;  // Pin does not support IKsPin.

    hr = pKsPin->KsQueryMediums( ppmi);
    pKsPin->Release();

    return hr;  // Pin does not support mediums.
}

int MatchMedium( KSMULTIPLE_ITEM *pmi1, KSMULTIPLE_ITEM *pmi2 )
{
	ULONG i, j;
	REGPINMEDIUM *pTemp1 = (REGPINMEDIUM*)(pmi1 + 1);
	for ( j = 0; j<pmi1->Count; j++, pTemp1++ )
    {
        // Use pointer arithmetic to reference the first medium structure.
        REGPINMEDIUM *pTemp2 = (REGPINMEDIUM*)(pmi2 + 1);
        for ( i = 0; i < pmi2->Count; i++, pTemp2++) 
        {
            if (pTemp1->clsMedium == pTemp2->clsMedium &&
				pTemp1->dw1 == pTemp2->dw1)
            {
				char clasid[64];
				GetClsidString( pTemp1->clsMedium, clasid, sizeof(clasid) );
				slog(( "Filters medium matched %s-0x%x.\r\n", clasid, pTemp1->dw1 ));
				return 1;
            }
        }
    }        
	return 0;
}

void ReleaseMedium( KSMULTIPLE_ITEM *pmi )
{
	if ( pmi != NULL )
		CoTaskMemFree(pmi);
}

int  CheckFilterMedium( IBaseFilter* pUpFilter, IBaseFilter* pDownFilter )
{
	int ret = 0;
	HRESULT hr;
	IPin* pPin = NULL;
	KSMULTIPLE_ITEM *pmi2=NULL, *pmi1=NULL;
	hr = GetPin(pUpFilter, PINDIR_OUTPUT, 0, &pPin);
	if ( SUCCEEDED(hr) )
		GetPinMedium( &pmi1, pPin );
	SAFE_RELEASE( pPin );
	hr = GetPin(pDownFilter, PINDIR_INPUT, 0, &pPin);
	if ( SUCCEEDED(hr) )
	{
		if ( FAILED( hr = GetPinMedium( &pmi2, pPin ) ) )
		{
			SAFE_RELEASE( pPin );
			hr = GetPin(pDownFilter, PINDIR_OUTPUT, 0, &pPin);
			GetPinMedium( &pmi2, pPin );
		}
	}
	SAFE_RELEASE( pPin );

	if ( pmi2 != NULL && pmi1 != NULL )
	{
		if (  MatchMedium( pmi2, pmi1 )  )
		{
			ret = 1;
		} else
		{
			char clasid1[64], clasid2[64];
			REGPINMEDIUM *pTemp1 = (REGPINMEDIUM*)(pmi1 + 1);
			REGPINMEDIUM *pTemp2 = (REGPINMEDIUM*)(pmi2 + 1);
			GetClsidString( pTemp1->clsMedium, clasid1, sizeof(clasid1) );
			GetClsidString( pTemp2->clsMedium, clasid2, sizeof(clasid2) );
			slog(( "Filters medium not matched %s-0x%x %s-0x%x.\r\n", clasid1, pTemp1->dw1, clasid2, pTemp2->dw1 ));
			ret = 0;
		}
	} else
	{
		char clasid1[64]={0}, clasid2[64]={0}, tmp[32];;
		if ( pmi1 != NULL )
		{
			REGPINMEDIUM *pTemp1 = (REGPINMEDIUM*)(pmi1 + 1);
			GetClsidString( pTemp1->clsMedium, clasid1, sizeof(clasid1) );
			sprintf( tmp, "-0x%x", pTemp1->dw1 );
			strcat( clasid1, tmp );
		}
		if ( pmi2 != NULL )
		{
			REGPINMEDIUM *pTemp2 = (REGPINMEDIUM*)(pmi2 + 1);
			GetClsidString( pTemp2->clsMedium, clasid2, sizeof(clasid2) );
			sprintf( tmp, "-0x%x", pTemp2->dw1 );
			strcat( clasid2, tmp );
		}
		slog(( "Filters medium not found on filters, filter1:'%s' filter2:'%s'.\r\n", clasid1, clasid2 ));
		ret = -1;
	}
	
	ReleaseMedium( pmi2 ); pmi2 = NULL;
	ReleaseMedium( pmi1 ); pmi1 = NULL;

	return ret;
}

HRESULT FindFilterByPinMedium(IBaseFilter** ppFilter, REFCLSID filterCat, IPin* pPin)
{
    IKsPin* pKsPin = NULL;
    KSMULTIPLE_ITEM *pmi;

    HRESULT hr = pPin->QueryInterface(IID_IKsPin, (void **)&pKsPin);
    if (FAILED(hr))
        return hr;  // Pin does not support IKsPin.

	bool match = false;
    hr = pKsPin->KsQueryMediums(&pmi);
    pKsPin->Release();
    if (FAILED(hr))
        return hr;  // Pin does not support mediums.

    if (pmi->Count) 
    {
        // Use pointer arithmetic to reference the first medium structure.
        REGPINMEDIUM *pTemp = (REGPINMEDIUM*)(pmi + 1);
		// This loops over the medium types from the output pin on the filter
        for (ULONG i = 0; i < pmi->Count; i++, pTemp++) 
        {
			if (pTemp->clsMedium == GUID_NULL ||
				pTemp->clsMedium == KSMEDIUMSETID_Standard)
				continue;
			// Now we loop over the filters in the filter category
		    ICreateDevEnum *pSysDevEnum = NULL;
		    IEnumMoniker *pEnum = NULL;
		    IMoniker *pMoniker = NULL;

		    hr = CoCreateInstance(CLSID_SystemDeviceEnum, NULL, 
		        CLSCTX_INPROC_SERVER, IID_ICreateDevEnum, 
		        (void**)&pSysDevEnum);
			if (FAILED(hr))
				continue;
		    hr = pSysDevEnum->CreateClassEnumerator(filterCat, &pEnum, 0);
			if (hr != S_OK)
			{
				// Nothing to enumerate
				SAFE_RELEASE(pSysDevEnum);
				continue;
			}
		    while (S_OK == pEnum->Next(1, &pMoniker, NULL))
		    {
        		hr = pMoniker->BindToObject(NULL, NULL, IID_IBaseFilter,
        			(void**)ppFilter);
				////Get friendly name
				//CComVariant varBSTR;
				////Get filter PropertyBag
				//CComPtr <IPropertyBag> pBag;
				//if SUCCEEDED(hr = pMoniker->BindToStorage(NULL, NULL, IID_IPropertyBag, reinterpret_cast<void**>(&pBag)))
				//	pBag->Read(L"FriendlyName", &varBSTR, NULL);

				SAFE_RELEASE(pMoniker);
        		if (FAILED(hr)) continue;

				// Now we loop over the pins on the filter
			    IEnumPins* pPinEnum = NULL;
			    (*ppFilter)->EnumPins(&pPinEnum);
			    if(SUCCEEDED(hr))
				{
					ULONG ulFound;
					IPin *testPin = NULL;
					while(S_OK == pPinEnum->Next(1, &testPin, &ulFound))
					{
						if (SUCCEEDED(FindMatchingMedium(testPin, pTemp, &match)) && match)
						{
							SAFE_RELEASE(testPin);
							SAFE_RELEASE(pPinEnum);
							SAFE_RELEASE(pEnum);
							SAFE_RELEASE(pSysDevEnum);
							CoTaskMemFree(pmi);
							return S_OK;
						}
						SAFE_RELEASE(testPin);
					}
					SAFE_RELEASE(pPinEnum);
				}
				SAFE_RELEASE((*ppFilter));
			}
			SAFE_RELEASE(pEnum);
			SAFE_RELEASE(pSysDevEnum);
        }
    }        
    CoTaskMemFree(pmi);
    return E_FAIL;
}

// direction is currently ignored because it's irrelevant for matching pin mediums and such
HRESULT FindDeviceMatchedFilter(IBaseFilter** ppFilter, REFCLSID filterCat, IPin* pPin, char* pDeviceName, PIN_DIRECTION pinDir)
{
    IKsPin* pKsPin = NULL;
    KSMULTIPLE_ITEM *pmi;

    HRESULT hr = pPin->QueryInterface(IID_IKsPin, (void **)&pKsPin);
    if (FAILED(hr))
        return hr;  // Pin does not support IKsPin.

	IBaseFilter* pMatchedFilter = NULL;
    hr = pKsPin->KsQueryMediums(&pmi);
    pKsPin->Release();
    if (FAILED(hr))
        return hr;  // Pin does not support mediums.

	bool mediumsUnreliable = false;
    if (pmi->Count) 
    {
        // Use pointer arithmetic to reference the first medium structure.
        REGPINMEDIUM *pTemp = (REGPINMEDIUM*)(pmi + 1);
		// This loops over the medium types from the output pin on the filter
        for (ULONG i = 0; !mediumsUnreliable && i < pmi->Count; i++, pTemp++) 
        {
			if (pTemp->clsMedium == GUID_NULL ||
				pTemp->clsMedium == KSMEDIUMSETID_Standard)
				continue;
			// Now we loop over the filters in the filter category
		    ICreateDevEnum *pSysDevEnum = NULL;
		    IEnumMoniker *pEnum = NULL;
		    IMoniker *pMoniker = NULL;

		    hr = CoCreateInstance(CLSID_SystemDeviceEnum, NULL, 
		        CLSCTX_INPROC_SERVER, IID_ICreateDevEnum, 
		        (void**)&pSysDevEnum);
			if (FAILED(hr))
				continue;
		    hr = pSysDevEnum->CreateClassEnumerator(filterCat, &pEnum, 0);
			if (hr != S_OK)
			{
				// Nothing to enumerate
				SAFE_RELEASE(pSysDevEnum);
				continue;
			}
		    while (!mediumsUnreliable && S_OK == pEnum->Next(1, &pMoniker, NULL))
		    {
				bool currMatch = false;
        		hr = pMoniker->BindToObject(NULL, NULL, IID_IBaseFilter,
        			(void**)ppFilter);
        		if (FAILED(hr))
				{
					SAFE_RELEASE(pMoniker);
					continue;
				}

				// Now we loop over the pins on the filter
			    IEnumPins* pPinEnum = NULL;
			    (*ppFilter)->EnumPins(&pPinEnum);
			    if(SUCCEEDED(hr))
				{
					ULONG ulFound;
					IPin *testPin = NULL;
					while(!mediumsUnreliable &&S_OK == pPinEnum->Next(1, &testPin, &ulFound))
					{
						if (SUCCEEDED(FindMatchingMedium(testPin, pTemp, &currMatch)) && currMatch)
						{
							if (pMatchedFilter)
							{
								slog(("Multiple matches found, use legacy technique.\r\n"));
								mediumsUnreliable = true;
								SAFE_RELEASE(pMatchedFilter);
							}
							else
							{
								pMatchedFilter = *ppFilter;
								pMatchedFilter->AddRef();
							}
							SAFE_RELEASE(testPin);
							break;
//							SAFE_RELEASE(pPinEnum);
//							SAFE_RELEASE(pSysDevEnum);
//							SAFE_RELEASE(pEnum);
//							CoTaskMemFree(pmi);
//							return S_OK;
						}
						SAFE_RELEASE(testPin);
					}
					SAFE_RELEASE(pPinEnum);
				}
				SAFE_RELEASE((*ppFilter));
				SAFE_RELEASE(pMoniker);
			}
			SAFE_RELEASE(pEnum);
			SAFE_RELEASE(pSysDevEnum);
        }
    }        
    CoTaskMemFree(pmi);

	if (mediumsUnreliable)
	{
		// Use the device name to try to find the filter instead
		return FindFilterByDeviceName(ppFilter, filterCat, 0, pDeviceName);
	}
	else if (pMatchedFilter)
	{
		*ppFilter = pMatchedFilter;
		return S_OK;
	}
    return E_FAIL;
}

HRESULT FindUpstreamFilter(IBaseFilter** ppFilter, REFCLSID filterCat, IPin* pPin, char* pDeviceName)
{
	return FindDeviceMatchedFilter(ppFilter, filterCat, pPin, pDeviceName, PINDIR_OUTPUT);
}
HRESULT FindDownstreamFilter(IBaseFilter** ppFilter, REFCLSID filterCat, IPin* pPin, char* pDeviceName)
{
	return FindDeviceMatchedFilter(ppFilter, filterCat, pPin, pDeviceName, PINDIR_INPUT);
}

HRESULT DestroyGraph(IGraphBuilder* pGraph)
{
    CComPtr<IEnumFilters> pEnum = NULL;
    IBaseFilter* pFilter = NULL;
    HRESULT hr = pGraph->EnumFilters(&(pEnum.p));

    if(FAILED(hr))
    {
        return hr;
    }
/*
    hr = pEnum->Next(1, &pFilter, NULL);
    while(S_OK == hr && pFilter)
    {
		IEnumPins* pPinEnum = NULL;
		IPin* pPin = NULL;

		hr = pFilter->EnumPins(&pPinEnum);
		if(FAILED(hr))
		{
			SAFE_RELEASE(pFilter);
			return hr;
		}
		hr = pPinEnum->Next(1, &pPin, NULL);

		while(S_OK == hr && pPin)
		{
			IPin* connPin = NULL;
			hr = pPin->ConnectedTo(&connPin);
			if (hr == S_OK)
			{
				hr = pGraph->Disconnect(pPin);
				if (SUCCEEDED(hr))
				{
					hr = pGraph->Disconnect(connPin);
				}
				connPin->Release();
				if (SUCCEEDED(hr))
			        hr = pPinEnum->Reset();
			}
			SAFE_RELEASE(pPin);
			hr = pPinEnum->Next( 1, &pPin, NULL);
		}

		SAFE_RELEASE(pPin);
		SAFE_RELEASE(pPinEnum);

        SAFE_RELEASE(pFilter);
        hr = pEnum->Next(1, &pFilter, NULL);
    }
*/
    // remove filters
    hr = pEnum->Reset();
    hr = pEnum->Next(1, &pFilter, NULL);
    while( S_OK == hr && pFilter )
    {
		//ZQ...
		FILTER_INFO Info;
		ZeroMemory(&Info, sizeof(FILTER_INFO));
		char FilterName[128];
		hr = pFilter->QueryFilterInfo( &Info );
		if (SUCCEEDED(hr))
		{
			WideCharToMultiByte( CP_ACP, 0, Info.achName, -1,
							FilterName, 128, NULL, NULL);
			slog(( "....RemoveFilter [%s] .\r\n", FilterName ) );
			SAFE_RELEASE(Info.pGraph);
		}

        hr = pGraph->RemoveFilter(pFilter);
        SAFE_RELEASE(pFilter);

        hr = pEnum->Reset();
        hr = pEnum->Next(1, &pFilter, NULL);
    }
    return S_OK;
}

void LogFilterGraphInfo(IGraphBuilder* pGraph)
{
    CComPtr<IEnumFilters> pEnum = NULL;
    IBaseFilter* pFilter = NULL;
    HRESULT hr = pGraph->EnumFilters(&(pEnum.p));
    if(FAILED(hr))
    {
        return;
    }
	char gData[2048];
	gData[0] = 0;
	strcat(gData, "DShowGraphFilters=");
    hr = pEnum->Reset();
    hr = pEnum->Next(1, &pFilter, NULL);
    while(S_OK == hr && pFilter)
    {
		FILTER_INFO fi;
		ZeroMemory(&fi, sizeof(FILTER_INFO));
		hr = pFilter->QueryFilterInfo(&fi);
		if (SUCCEEDED(hr))
		{
			char szFriendlyName[128];
			WideCharToMultiByte(CP_ACP, 0, fi.achName, -1,
						szFriendlyName, 128, NULL, NULL);
			strcat(gData, szFriendlyName);
			strcat(gData, ",");
			if (fi.pGraph)
			{
				SAFE_RELEASE(fi.pGraph);
			}
		}
        SAFE_RELEASE(pFilter);

        hr = pEnum->Next(1, &pFilter, NULL);
    }
	strcat(gData, "\r\n");
	slog((gData));
    return;
}

IPin* FindVideoCaptureInputPin(IBaseFilter* pFilter)
{
	// NOTE: This is the right way to do it, but it fails for the CX2388x PCI-X boards so I'm
	// trying it another way. This is due to an MS bug.

	/* RIGHT WAY */
	IPin* rv = FindPin(pFilter, PINDIR_INPUT, &MEDIATYPE_AnalogVideo, NULL);
	if (rv)
		return rv;

	/* OTHER WAY
	   We assume that any pins w/out media types are actually video pins.
	   This accounts for a bug with the PVR150/500/Conexant MACO capture drivers
	   */
    IEnumPins* pEnum = NULL;
    HRESULT hr = pFilter->EnumPins(&pEnum);
    if(FAILED(hr)) 
        return NULL;

    ULONG ulFound;
    IPin *pPin = NULL;
    hr = E_FAIL;

    while(S_OK == pEnum->Next(1, &pPin, &ulFound))
    {
        PIN_DIRECTION pindir;
        pPin->QueryDirection(&pindir);
        if(pindir == PINDIR_INPUT)
        {
			IEnumMediaTypes *mtEnum = NULL;
			hr = pPin->EnumMediaTypes(&mtEnum);
			BOOL foundMTs = FALSE;
			if (SUCCEEDED(hr))
			{
				AM_MEDIA_TYPE *pMT = NULL;
				while (S_OK == mtEnum->Next(1, &pMT, NULL))
				{
					foundMTs = TRUE;
					/*if (pMT->majortype != MEDIATYPE_AnalogAudio &&
						pMT->majortype != MEDIATYPE_Audio)
					{
						slog(("Found the pin ;)\r\n"));
						DeleteMediaType(pMT);
						SAFE_RELEASE(mtEnum);
						SAFE_RELEASE(pEnum);
						return pPin;
					}*/
					DeleteMediaType(pMT);
				}
				SAFE_RELEASE(mtEnum);
			}
			if (!foundMTs)
			{
				slog(("Found the pin2 ;)\r\n"));
				SAFE_RELEASE(pEnum);
				return pPin;
			}
        } 

        pPin->Release();
    } 
    SAFE_RELEASE(pEnum);

    return NULL;

}

DWORD GetRegistryDword(HKEY rootKey, const char* keyName, const char* valueName, DWORD defaultValue)
{
	DWORD holder = defaultValue;
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(holder);
	if (RegCreateKeyEx(rootKey, keyName, 0, NULL, REG_OPTION_NON_VOLATILE,
		KEY_ALL_ACCESS, NULL, &myKey, &readType) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, valueName, 0, &readType, (LPBYTE) &holder, &hsize) != ERROR_SUCCESS)
		{
			// Set the variable
			hsize = sizeof(holder);
			RegSetValueEx(myKey, valueName, 0, REG_DWORD, (LPBYTE) &holder, hsize);
		}
		RegCloseKey(myKey);
	}
	return holder;
}

char* GetRegistryString(HKEY rootKey, const char* keyName, const char* valueName, char* szBuf, int dwSize )
{
	HKEY  hKey;
	BOOL  bHasValue = FALSE;
	DWORD dwValue = 0;
	
	if ( szBuf == NULL || dwSize == 0 )
		return (char*)"";

	*szBuf = 0x0;
	if ( RegOpenKeyEx( rootKey, keyName, 0, KEY_ALL_ACCESS, &hKey) == ERROR_SUCCESS )
	{
		DWORD hType;
		DWORD hSize = dwSize;
		RegQueryValueEx( hKey, valueName, 0, &hType, (LPBYTE)szBuf, &hSize );
		RegCloseKey( hKey );
	} 
	return szBuf;
}

HRESULT RenderStream(IGraphBuilder* pGraph, const GUID* pinCategory, const GUID* majorType, IBaseFilter* pCapFilter,
					 IBaseFilter* pCompFilter, IBaseFilter* pMuxFilter, IBaseFilter* pResync)
{
	// Find the desired output pin on the capture filter
	IPin* capFiltPin = FindPinByCategoryAndType(pCapFilter, pinCategory, majorType);
	if (!capFiltPin)
		return 0x80140000;

	// If our capture filter pin is already connected, it may be hooked up to a smart tee; in which
	// case we need to get the output on the smart tee filter.
	IPin* capFiltConnPin = NULL;
	HRESULT hr;
	hr = capFiltPin->ConnectedTo(&capFiltConnPin);
	if (SUCCEEDED(hr))
	{
		PIN_INFO altPinInfo;
		hr = capFiltConnPin->QueryPinInfo(&altPinInfo);
		if (SUCCEEDED(hr))
		{
			IPin* capFiltPin2 = FindUnconnectedPin(altPinInfo.pFilter, PINDIR_OUTPUT, FALSE);
			if (capFiltPin2)
			{
				// Use this pin instead of the other pin we got
				SAFE_RELEASE(capFiltPin);
				slog(("Found the output pin on the next filter to do RenderStream\r\n"));
				capFiltPin = capFiltPin2;
			}
			altPinInfo.pFilter->Release();
		}
		SAFE_RELEASE(capFiltConnPin);
	}

	// Check for resync
	if (pResync)
	{
		if (majorType == &MEDIATYPE_Video)
		{
			slog(("Setting up resync for video\r\n"));
			IPin* pResyncIn = FindPinByName(pResync, "Video In", PINDIR_INPUT);
			if (pResyncIn)
			{
				hr = pGraph->ConnectDirect(capFiltPin, pResyncIn, NULL);
				SAFE_RELEASE(pResyncIn);
				if (SUCCEEDED(hr))
				{
					IPin* pResyncOut = FindPinByName(pResync, "Video Out", PINDIR_OUTPUT);
					if (pResyncOut)
					{
						slog(("Connected video resync successfully\r\n"));
						SAFE_RELEASE(capFiltPin);
						capFiltPin = pResyncOut;
					}
					else
						slog(("Unable to find video output pin on resync\r\n"));
				}
				else
				{
					slog(("Failed connecting up resync video 0x%x\r\n", hr));
				}
			}
			else
			{
				slog(("Could not find resync video input pin\r\n"));
			}
		}
		else if (majorType == &MEDIATYPE_Audio)
		{
			slog(("Setting up resync for audio\r\n"));
			IPin* pResyncIn = FindPinByName(pResync, "Audio In", PINDIR_INPUT);
			if (pResyncIn)
			{
				// For audio, we need to put the ACM wrapper filter in there or it
				// won't connect to the resync filter
				CComPtr<IBaseFilter> pAcmWrap = NULL;
				hr = CoCreateInstance(CLSID_ACMWrapper, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&(pAcmWrap.p));
				ASSERT(SUCCEEDED(hr));
				hr = pGraph->AddFilter(pAcmWrap, L"ACM Wrapper");
				ASSERT(SUCCEEDED(hr));
				IPin* acmInput = FindPin(pAcmWrap.p, PINDIR_INPUT, NULL, NULL);
				ASSERT(acmInput);
				hr = pGraph->ConnectDirect(capFiltPin, acmInput, NULL);
				SAFE_RELEASE(acmInput);
				if (SUCCEEDED(hr))
				{
					IPin* acmOutput = FindPin(pAcmWrap.p, PINDIR_OUTPUT, NULL, NULL);
					ASSERT(acmOutput);
					hr = pGraph->ConnectDirect(acmOutput, pResyncIn, NULL);
					SAFE_RELEASE(acmOutput);
					SAFE_RELEASE(pResyncIn);
					if (SUCCEEDED(hr))
					{
						IPin* pResyncOut = FindPinByName(pResync, "Audio Out", PINDIR_OUTPUT);
						if (pResyncOut)
						{
							slog(("Connected audio resync successfully\r\n"));
							SAFE_RELEASE(capFiltPin);
							capFiltPin = pResyncOut;
						}
						else
							slog(("Unable to find audio output pin on resync\r\n"));
					}
					else
					{
						slog(("Failed connecting up resync audio 0x%x\r\n", hr));
					}
				}
				else
				{
					slog(("Failed connecting up 1st resync audio 0x%x\r\n", hr));
				}
			}
			else
			{
				slog(("Could not find resync audio input pin\r\n"));
			}
		}
	}
	IPin* compFiltInPin = NULL;
	IPin* compFiltOutPin = NULL;
	if (pCompFilter)
	{
		compFiltInPin = FindPin(pCompFilter, PINDIR_INPUT, majorType, NULL);
		if (!compFiltInPin)
			compFiltInPin = FindUnconnectedPin(pCompFilter, PINDIR_INPUT, FALSE);
		compFiltOutPin = FindPin(pCompFilter, PINDIR_OUTPUT, majorType, NULL);
		if (!compFiltOutPin)
			compFiltOutPin = FindUnconnectedPin(pCompFilter, PINDIR_OUTPUT, FALSE);
	}
	BOOL bConnected = FALSE;
	IPin* muxInPin = FindPin(pMuxFilter, PINDIR_INPUT, majorType, NULL);
	if (!muxInPin) 
	{
		if (compFiltInPin && compFiltOutPin)
		{
			hr = pGraph->ConnectDirect(capFiltPin, compFiltInPin, NULL);
			if (SUCCEEDED(hr))
			{
				muxInPin = FindUnconnectedPin(pMuxFilter, PINDIR_INPUT, FALSE);
				if (muxInPin) 
				{
					hr = pGraph->ConnectDirect(compFiltOutPin, muxInPin, NULL);
					if (SUCCEEDED(hr))	bConnected = TRUE;
				}
			}
		}
		else
		{
			hr = pGraph->ConnectDirect(capFiltPin, muxInPin, NULL);
			if (SUCCEEDED(hr))	bConnected = TRUE;
		}
		SAFE_RELEASE(muxInPin);
	} 
	if ( !bConnected )
	{
		muxInPin = FindUnconnectedPin(pMuxFilter, PINDIR_INPUT, FALSE);
		if ( muxInPin )
		{
			if (compFiltInPin && compFiltOutPin)
			{
				hr = pGraph->ConnectDirect(capFiltPin, compFiltInPin, NULL);
				if (SUCCEEDED(hr))
				{
					hr = pGraph->ConnectDirect(compFiltOutPin, muxInPin, NULL);
					if (SUCCEEDED(hr))	bConnected = TRUE;
				}
			}
			else
			{
				hr = pGraph->ConnectDirect(capFiltPin, muxInPin, NULL);
				if (SUCCEEDED(hr))	bConnected = TRUE;
			}
		} else
			hr = 0x80240000;

		SAFE_RELEASE(muxInPin);
	}
	
	//hr = 0x80240000;
	SAFE_RELEASE(muxInPin);
	SAFE_RELEASE(compFiltInPin);
	SAFE_RELEASE(compFiltOutPin);
	SAFE_RELEASE(capFiltPin);
	return hr;
}

HRESULT RenderStreamDebug(IGraphBuilder* pGraph, const GUID* pinCategory, const GUID* majorType, IBaseFilter* pCapFilter,
					 IBaseFilter* pCompFilter, IBaseFilter* pMuxFilter)
{
	// Find the desired output pin on the capture filter
	IPin* capFiltPin = FindPinByCategoryAndType(pCapFilter, pinCategory, majorType);
	if (!capFiltPin)
		return 0x80140000;

	// If our capture filter pin is already connected, it may be hooked up to a smart tee; in which
	// case we need to get the output on the smart tee filter.
	IPin* capFiltConnPin = NULL;
	HRESULT hr;
	hr = capFiltPin->ConnectedTo(&capFiltConnPin);
	if (SUCCEEDED(hr))
	{
		PIN_INFO altPinInfo;
		hr = capFiltConnPin->QueryPinInfo(&altPinInfo);
		if (SUCCEEDED(hr))
		{
			IPin* capFiltPin2 = FindUnconnectedPin(altPinInfo.pFilter, PINDIR_OUTPUT, FALSE);
			if (capFiltPin2)
			{
				// Use this pin instead of the other pin we got
				SAFE_RELEASE(capFiltPin);
				slog(("Found the output pin on the next filter to do RenderStream\r\n"));
				capFiltPin = capFiltPin2;
			}
			altPinInfo.pFilter->Release();
		}
		SAFE_RELEASE(capFiltConnPin);
	}
	IPin* compFiltInPin = NULL;
	IPin* compFiltOutPin = NULL;
	if (pCompFilter)
	{
		compFiltInPin = FindPin(pCompFilter, PINDIR_INPUT, majorType, NULL);
		if (!compFiltInPin)
			compFiltInPin = FindUnconnectedPin(pCompFilter, PINDIR_INPUT, FALSE);
		compFiltOutPin = FindPin(pCompFilter, PINDIR_OUTPUT, majorType, NULL);
		if (!compFiltOutPin)
			compFiltOutPin = FindUnconnectedPin(pCompFilter, PINDIR_OUTPUT, FALSE);
	}
	IPin* muxInPin = FindPin(pMuxFilter, PINDIR_INPUT, majorType, NULL);
	if (!muxInPin)
		muxInPin = FindUnconnectedPin(pMuxFilter, PINDIR_INPUT, FALSE);
	if (muxInPin)
	{
		if (compFiltInPin && compFiltOutPin)
		{
			hr = ConnectPinsWithDump(pGraph, capFiltPin, compFiltInPin);
			if (SUCCEEDED(hr))
				hr = ConnectPinsWithDump(pGraph, compFiltOutPin, muxInPin);
		}
		else
		{
			hr = ConnectPinsWithDump(pGraph, capFiltPin, muxInPin);
		}
	}
	else
		hr = 0x80240000;
	SAFE_RELEASE(muxInPin);
	SAFE_RELEASE(compFiltInPin);
	SAFE_RELEASE(compFiltOutPin);
	SAFE_RELEASE(capFiltPin);
	return hr;
}

typedef HRESULT STDAPICALLTYPE DLLGETCLASSFUNC (REFCLSID rclsid, REFIID riid, LPVOID * ppv);
HRESULT loadDivXFW(IBaseFilter **pFilter)
{
	HRESULT hr;

	// There's really no place to unload this
	HMODULE hInst = LoadLibrary("aviwriter.ax");
	if(!hInst)	{
		return E_FAIL;
	}	

	/// retrieve the decoder filter loading his library and querying directly
	/// for the filter interface. this should allow the filter to work without need 
	/// to register it.
	DLLGETCLASSFUNC *DllGetClassObject = (DLLGETCLASSFUNC *) GetProcAddress(hInst, "DllGetClassObject");

	IClassFactory *pClassFactory = 0;
	hr = DllGetClassObject(CLSID_AVIWriter, IID_IClassFactory, (void **) &pClassFactory);
	hr = pClassFactory->CreateInstance(NULL, IID_IBaseFilter, (void **) &(*pFilter));
	pClassFactory->Release();
 
	return hr;
}

//ZQ
/*
HRESULT AddFilter(IGraphBuilder* piGraphBuilder, REFCLSID rclsid, IBaseFilter **ppiFilter, LPCWSTR pName )
{
	HRESULT hr;
	if FAILED(hr = CoCreateInstance(rclsid, NULL, CLSCTX_INPROC_SERVER, IID_IBaseFilter, reinterpret_cast<void**>(ppiFilter)))
	{
		return hr;
	}

	if FAILED(hr = piGraphBuilder->AddFilter(*ppiFilter, pName))
	{
		(*ppiFilter)->Release();
		return hr;
	}
	return S_OK;
}

HRESULT AddFilterByName(IGraphBuilder* piGraphBuilder, IBaseFilter **ppiFilter, CLSID clsidDeviceClass, LPCWSTR friendlyName)
{
	HRESULT hr = S_OK;
	CComPtr <IEnumMoniker> pEnum;
	CComPtr <ICreateDevEnum> pSysDevEnum;
	CComPtr <IMoniker> pMoniker;

	if FAILED(hr = pSysDevEnum.CoCreateInstance(CLSID_SystemDeviceEnum))
	{
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
				break;
			}

		    if FAILED(hr = pBag->Read(L"FriendlyName", &varBSTR, NULL))
			{
				continue;
			}

			LPOLESTR pStr;
			IBindCtx *pBindCtx;
			hr = CreateBindCtx(0, &pBindCtx);

			hr = pMoniker->GetDisplayName(pBindCtx, NULL, &pStr);

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
				break;
			}

			//Add filter to graph
			if FAILED(hr = piGraphBuilder->AddFilter(*ppiFilter, varBSTR.bstrVal))
			{
				(*ppiFilter)->Release();
				return E_FAIL;
			}

			return S_OK;
		}
		break;
	case S_FALSE:
		return E_FAIL;
		break;
	case E_OUTOFMEMORY:
		return E_FAIL;
		break;
	case E_POINTER:
	  	return E_FAIL;
		break;
	}
	return E_FAIL;
}


HRESULT ConnectFilters(IGraphBuilder* piGraphBuilder, IPin* piPinUpstream, IBaseFilter* piFilterDownstream)
{
    HRESULT hr = S_OK;

	CComPtr <IEnumPins> pIEnumPinsDownstream;
	if FAILED(hr = piFilterDownstream->EnumPins(&pIEnumPinsDownstream))
	{
		return E_FAIL;
	}

	CComPtr <IPin> piPinDownstream;
	while (piPinDownstream.Release(), pIEnumPinsDownstream->Next (1, &piPinDownstream, 0) == S_OK)
	{
	    PIN_INFO PinInfoDownstream;
		if FAILED(hr = piPinDownstream->QueryPinInfo(&PinInfoDownstream))
		{
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


HRESULT CreateATSCTuneRequest( JNIEnv* env, CComPtr<ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long lMajorChannel, long lMinorChannel, long lPhysicalChannel, long lFreq )
{
	HRESULT hr = S_OK;

	if (!piTuningSpace)
	{
		slog( (env,"Tuning Space is NULL\n" ) );
		return E_FAIL;
	}

	//Get an interface to the TuningSpace
    CComQIPtr <IATSCTuningSpace> piATSCTuningSpace( piTuningSpace );
    if (piATSCTuningSpace == NULL )
	{
        slog( (env,"Can't Query Interface for an IATSCTuningSpace2\n" ) );
		return E_FAIL;
	}

	//Get new TuneRequest from tuning space
	if FAILED(hr = piATSCTuningSpace->CreateTuneRequest(&pExTuneRequest))
	{
		slog( (env,"Failed to create Tune Request\n" ) );
		piATSCTuningSpace.Release();
		return hr;
	}

	//Get interface to the TuneRequest
	CComQIPtr <IATSCChannelTuneRequest> piATSCTuneRequest(pExTuneRequest);
	if ( piATSCTuneRequest == NULL )
	{
		piATSCTuningSpace.Release();
		pExTuneRequest.Release();
        slog( (env,"Can't Query Interface for an IDVBTuneRequest.\n" ) );
		pExTuneRequest.Release();
		return E_FAIL;
	}

    //  Set the initial major and minor channels
    hr = piATSCTuneRequest->put_Channel(lMajorChannel);
    if(FAILED(hr))
    {
        slog( (env,"put_Channel failed\n" ) );
		piATSCTuningSpace.Release();
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
        return hr;
    }

    hr = piATSCTuneRequest->put_MinorChannel(lMinorChannel);
    if(FAILED(hr))
    {
        slog( (env,"put_MinorChannel failed\n" ) );
		piATSCTuningSpace.Release();
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
        return hr;
    }

	// setup ATSC locator
	CComPtr <IATSCLocator> pATSCLocator;
	hr = pATSCLocator.CoCreateInstance(CLSID_ATSCLocator);
	if ( FAILED( hr ) )
	{
        slog( (env,"Cannot create the ATSC locator\n" ) );
		piATSCTuningSpace.Release();
		pExTuneRequest.Release();
		piATSCTuneRequest.Release();
		return hr;
	}

    hr = pATSCLocator->put_PhysicalChannel(lPhysicalChannel);
    if (FAILED( hr))
    {
        slog( (env,"Cannot put the physical channel\n" ) );
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
        slog( (env,"Cannot put the locator on DVB Tune Request\n" ) );
		return hr;
    }

	piATSCTuningSpace.Release();
	pATSCLocator.Release();
	piATSCTuneRequest.Release();

	return S_OK;
}

HRESULT CreateDVBTTuneRequest( JNIEnv* env, CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long frequency, long bandwidth)
{
	HRESULT hr = S_OK;

	if (!piTuningSpace)
	{
		slog( (env,"Tuning Space is NULL\n" ) );
		return E_FAIL;
	}

	//Get an interface to the TuningSpace
	CComQIPtr <IDVBTuningSpace2> piDVBTuningSpace(piTuningSpace);
    if (!piDVBTuningSpace)
	{
        slog( (env,"Can't Query Interface for an IDVBTuningSpace2\n" ) );
		return E_FAIL;
	}

	//Get new TuneRequest from tuning space
	if FAILED(hr = piDVBTuningSpace->CreateTuneRequest(&pExTuneRequest))
	{
		slog( (env,"Failed to create Tune Request\n" ) );
		return hr;
	}

	//Get interface to the TuneRequest
	CComQIPtr <IDVBTuneRequest> piDVBTuneRequest(pExTuneRequest);
	if (!piDVBTuneRequest)
	{
		pExTuneRequest.Release();
        slog( (env,"Can't Query Interface for an IDVBTuneRequest.\n" ) );
		return E_FAIL;
	}

	//
	// Start
	//
	CComPtr <IDVBTLocator> pDVBTLocator;
	hr = pDVBTLocator.CoCreateInstance(CLSID_DVBTLocator);
	switch (hr)
	{ 
	case REGDB_E_CLASSNOTREG:
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		slog( (env,"The DVBTLocator class isn't registered in the registration database.\n" ) );
		return hr;

	case CLASS_E_NOAGGREGATION:
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		slog( (env,"The DVBTLocator class can't be created as part of an aggregate.\n" ) );
		return hr;
	}

	if FAILED(hr = pDVBTLocator->put_CarrierFrequency(frequency))
	{
		pDVBTLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		slog( (env,"Can't set Frequency on Locator.\n" ) );
		return hr;
	}
	if FAILED(hr = pDVBTLocator->put_SymbolRate(bandwidth))
	{
		pDVBTLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
		slog( (env,"Can't set Bandwidth on Locator.\n" ) );
		return hr;
	}
	//
	//End
	//

	// Bind the locator to the tune request.
	
    if FAILED(hr = piDVBTuneRequest->put_Locator(pDVBTLocator))
	{
		pDVBTLocator.Release();
		pExTuneRequest.Release();
		piDVBTuneRequest.Release();
        slog( (env,"Cannot put the locator on DVB Tune Request\n" ) );
		return hr;
    }

	pDVBTLocator.Release();
	piDVBTuneRequest.Release();

	return hr;
}
*/
//ZQ

//*****************************************************************************
//Load DivX filter from local directory
//*****************************************************************************
HRESULT loadDivX(IBaseFilter **pFilter)
{
	HRESULT hr = E_FAIL;

/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
	HMODULE hInst = LoadLibrary("DivXEnc.ax");
	if(!hInst)	{
		return hr;
	}	

	/// retrieve the decoder filter loading his library and querying directly
	/// for the filter interface. this should allow the filter to work without need 
	/// to register it.
	DLLGETCLASSFUNC *DllGetClassObject = (DLLGETCLASSFUNC *) GetProcAddress(hInst, "DllGetClassObject");

	IClassFactory *pClassFactory = 0;
	hr = DllGetClassObject(CLSID_DivXEncFilter, IID_IClassFactory, (void **) &pClassFactory);
	hr = pClassFactory->CreateInstance(NULL, IID_IBaseFilter, (void **) &(*pFilter));
	pClassFactory->Release();
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */

	return hr;
}

BOOL configureDivXVideoEncoder(IBaseFilter* pVidEnc, 
							 SageTVMPEG2EncodingParameters *pythonParams, JNIEnv *env,
							 BOOL aviEncoding)
{
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
	STATE_INFO pInfo;
	//assume pinfo is a valid STATE_INFO structure see encore.h is /src/include.
	IDivXEncFilterInterface *pEncSettings;
	HRESULT hr = pVidEnc->QueryInterface(IID_IDivXEncFilterInterface, (void **) &pEncSettings);
	if (FAILED(hr))
	{
		TEST_AND_PRINT
		return 0;
	}
	hr = pEncSettings->getUserSettings(pInfo);
	TEST_AND_PRINT

	if (pythonParams)
	{
		slog((env, "Configuring DivX SW encoder\r\n"));
		pInfo.settings.vbr_mode = RCMODE_VBV_1PASS;
		pInfo.settings.bitrate = pythonParams->videobitrate;
		pInfo.settings.quality = GetRegistryDword(HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\DirectShow", "DivXSWEncodeQuality", 3);
		pInfo.settings.packed_bitstream = aviEncoding;
		if (pythonParams->fps == 25)
		{
			pInfo.settings.input_clock = 25;
			pInfo.settings.input_frame_period = 1;
		}
		else if (pythonParams->fps == 15)
		{
			pInfo.settings.input_clock = 15;
			pInfo.settings.input_frame_period = 1;
		}
		else // 30 fps (actually 29.97)
		{
			pInfo.settings.input_clock = 30000;
			pInfo.settings.input_frame_period = 1001;
		}
		// VBV is 3 seconds of video in multiples of 400
		pInfo.settings.vbv_bitrate = pythonParams->peakvideobitrate * 3;
		pInfo.settings.vbv_bitrate -= (pInfo.settings.vbv_bitrate % 400);
		// We need constant GOP sizes for our mux
//		pInfo.settings.max_key_interval = pythonParams->gopsize;
//		pInfo.settings.key_frame_threshold = 100;
	}
	hr = pEncSettings->setUserSettings(pInfo);
	TEST_AND_PRINT
	pEncSettings->Release();
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
	return TRUE;
}

DWORD ReadAdditionalSettingDWord( JNIEnv *env, DShowCaptureInfo* pCapInfo, char* ValueName, DWORD dwDefault )
{
		HKEY  hCaptureKey;
		BOOL  bHasValue = FALSE;
		DWORD dwValue = 0;
		char  CaptureKeyName[1024];
		SPRINTF( CaptureKeyName, sizeof(CaptureKeyName), "%s%s-%d", "SOFTWARE\\Frey Technologies\\Common\\AdditionalCaptureSetups\\", 
			pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum );

		if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, CaptureKeyName, 0, KEY_ALL_ACCESS, &hCaptureKey) == ERROR_SUCCESS)
		{
			bHasValue = TRUE;
			DWORD hType;
			DWORD hSize = sizeof(dwValue);
			RegQueryValueEx( hCaptureKey, ValueName, 0, &hType, (LPBYTE)&dwValue, &hSize );
			RegCloseKey( hCaptureKey );
			slog((env, "Get %s's customized setting %s from registry, value=%d \r\n", 
				CaptureKeyName, ValueName, dwValue ) );
		} else
		{
			slog((env, "not find %s's customized setting %s from registry, use default value=%d \r\n", 
				CaptureKeyName, ValueName, dwValue ) );
		}

		if ( bHasValue )
			return dwValue;

		return dwDefault;
}

char* ReadAdditionalSettingString( JNIEnv *env, char* CaptureFilterName, char* ValueName, char* szBuf, int dwSize )
{
		HKEY  hCaptureKey;
		BOOL  bHasValue = FALSE;
		DWORD dwValue = 0;
		char  CaptureKeyName[1024];
		SPRINTF( CaptureKeyName, sizeof(CaptureKeyName), "%s%s", "SOFTWARE\\Frey Technologies\\Common\\AdditionalCaptureSetups\\", 
			CaptureFilterName );

		if ( szBuf == NULL || dwSize == 0 )
			return (char*)"";

		*szBuf = 0x0;
		if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, CaptureKeyName, 0, KEY_ALL_ACCESS, &hCaptureKey) == ERROR_SUCCESS)
		{
			bHasValue = TRUE;
			DWORD hType;
			DWORD hSize = dwSize;
			RegQueryValueEx( hCaptureKey, ValueName, 0, &hType, (LPBYTE)szBuf, &hSize );
			RegCloseKey( hCaptureKey );
			//slog((env, "Get %s's customized setting %s from registry, string=%s \r\n", 
			//	CaptureKeyName, ValueName, szBuf ) );
		} else
		{
			//slog((env, "not find %s's customized setting %s from registry\r\n", CaptureKeyName ));
		}

		if ( bHasValue )
			return szBuf;

		return "";
}


BOOL DoesPinSupportMediaType(IPin* pPin, const GUID* majorType, const GUID* subType)
{
	if (!majorType && !subType)
	{
		return TRUE;
	}
	IEnumMediaTypes *mtEnum = NULL;
	HRESULT hr = pPin->EnumMediaTypes(&mtEnum);
	if (SUCCEEDED(hr))
	{
		AM_MEDIA_TYPE *pMT = NULL;
		while (S_OK == mtEnum->Next(1, &pMT, NULL))
		{
			if ((!majorType || pMT->majortype == *majorType) && (!subType || pMT->subtype == *subType))
			{
				DeleteMediaType(pMT);
				SAFE_RELEASE(mtEnum);
				return TRUE;
			}
			DeleteMediaType(pMT);
		}
		SAFE_RELEASE(mtEnum);
	}
	return FALSE;
}

#define VIRTUAL_TUNE_FILE	"VirtualTuners.properties"
//list format: "tuner filter name", 0, param 
int LoadVirtualTunerList(  JNIEnv *env, VIRTUAL_TUNER* pVirtalTunerList, int maxListNum )
{
	FILE* fp;
	char buf[1024], tmp[80];
	int i, num;

	if ( pVirtalTunerList == NULL || maxListNum == 0 )
		return 0;
	fp = fopen( VIRTUAL_TUNE_FILE, "r+t" );
	if ( fp == NULL )
		return 0;

	num = 0;
	while( !feof( fp ) && num < maxListNum )
	{
		char *p;
		memset( buf, 0, sizeof(buf) );
		fgets( buf, sizeof(buf)-1, fp );
		
		//skip white space
		for ( i = 0; i<sizeof(buf)-1; i++ )
			if ( buf[i] != ' ' || buf[i] !='\t' ) break;
		p = buf+i;
		
		//skip comment line
		if ( *p == '#' || *p == '!' ) 
			continue; 

		//parse name
		while ( *p && *p != '\"' ) p++;   
		if ( *p != '\"' || *p == 0x0 ) continue;
		p++;
		i = 0;
		while ( *p && *p != '\"' && i <sizeof(tmp)-1 ) tmp[i++] = *p++;  
		tmp[i] = 0x0;
		if ( *p == '\"' && tmp[0] )
		{
			p++;
			strncpy( pVirtalTunerList[num].CaptureName, tmp, sizeof(pVirtalTunerList[num].CaptureName)-1 ); 
			pVirtalTunerList[num].Mask = 0;
			memset( pVirtalTunerList[num].Param, 0, sizeof(pVirtalTunerList[num].Param) );
	
			//parse mask
			while ( *p && ( *p < '0' || *p > '9' ) ) p++; 
			i = 0;
			while ( *p && *p >= '0' && *p <= '9' && i <sizeof(tmp)-1 )	 tmp[i++] = *p++;  
			tmp[i] = 0x0;
			if ( tmp[0] )	pVirtalTunerList[num].Mask = atoi( tmp );

			//parse param
			while ( *p && *p != '\r' && *p != '\n' && ( *p==' ' || *p==',' || *p==';' || *p=='\t' )  ) p++; //skip space
			if ( *p && *p != '\r' && *p != '\n' )
			{
				i = 0;
				while ( *p && *p != '\r' && *p != '\n' && i<sizeof(tmp) )  tmp[i++] = *p++; 
				tmp[i] = 0x0;
				if ( tmp[0] )	strncpy( pVirtalTunerList[num].Param, tmp, sizeof(pVirtalTunerList[num].Param) );
			}

			num++;
		}
	}

	fclose( fp );
	return num;
}

BOOL IsVirtualTuner(  JNIEnv *env, const char* capFiltName  )
{
	VIRTUAL_TUNER VirtalTunerList[8];
	int  numDevs = LoadVirtualTunerList( env, VirtalTunerList, 8 );
	if ( numDevs )
	{
		for (int i = 0; i < numDevs; i++)
		{
			if ( !strcmp( VirtalTunerList[i].CaptureName, capFiltName ) )
				return TRUE;
		}
	}

	return FALSE;
}

BOOL GetVirtualTunerDevName( JNIEnv *env, char* capFiltName, int CapFiltNum, 
							char* BDAFiltDevName, int BDAFiltDevNameSize,
							char* pParam, int dwParamSize, unsigned long* pMask )
{
	IEnumMoniker *pEnum = NULL;
	int i, index;
	char devName[512]={0};
	char captureName[256]={0};

	index = CapFiltNum;
	VIRTUAL_TUNER VirtalTunerList[8];
	int  numDevs = LoadVirtualTunerList( env, VirtalTunerList, 8 );
	if ( numDevs )
	{
		for ( i = 0; i < numDevs; i++)
		{
			if ( !strcmp( VirtalTunerList[i].CaptureName, capFiltName ) )
			{
				if ( index == 0 ) 
				{
					if ( pParam != NULL && dwParamSize > 0 )
						strncpy( pParam, VirtalTunerList[i].Param, dwParamSize );
					if ( pMask != NULL )
						*pMask = VirtalTunerList[i].Mask;

					break;
				}
				else index--;
			}
		}

		if ( i > numDevs )
			return FALSE;  //no found
	} else
		return FALSE; //no virtual tuner list file
	
	{
		IBaseFilter* pFilter = NULL;
		HRESULT hr = FindFilterByNameDebug( env, &pFilter, AM_KSCATEGORY_CAPTURE,
					capFiltName, CapFiltNum, devName, sizeof(devName) );
		if ( FAILED( hr ) )
		{
			hr = FindFilterByName(&pFilter, KSCATEGORY_BDA_RECEIVER_COMPONENT,
					capFiltName, CapFiltNum, devName, sizeof(devName) );
			if ( FAILED( hr ) )
			{
				hr = FindFilterByName(&pFilter, KSCATEGORY_BDA_NETWORK_TUNER,
						capFiltName, CapFiltNum, devName, sizeof(devName) ); 
			}
		}

		if ( FAILED( hr ) )
		{
			if ( !strncmp( capFiltName, "PUSH TS SOURCE", 14 ) ) //hard code "PUSH TS SOURCE"  for debug dump data
				hr = FindFilterByName(&pFilter, CLSID_LegacyAmFilterCategory,
									capFiltName, CapFiltNum, devName, sizeof(devName) );
		}

		SAFE_RELEASE(pFilter);
		if ( FAILED( hr ) )
			return FALSE;

		strncpy( BDAFiltDevName, devName, BDAFiltDevNameSize );
		//it's a virtual tuner device
		
	}

	return TRUE;
}


BOOL GetFilterDevName( JNIEnv *env, char* capFiltName, int CapFiltNum, 
						char* BDAFiltDevName, int BDAFiltDevNameSize )
{
	IEnumMoniker *pEnum = NULL;
	char devName[512]={0};
	char captureName[256]={0};
	{
		IBaseFilter* pFilter = NULL;
		HRESULT hr = FindFilterByNameDebug( env, &pFilter, AM_KSCATEGORY_CAPTURE,
					capFiltName, CapFiltNum, devName, sizeof(devName) );
		if ( FAILED( hr ) )
		{
			hr = FindFilterByName(&pFilter, KSCATEGORY_BDA_RECEIVER_COMPONENT,
					capFiltName, CapFiltNum, devName, sizeof(devName) );
			if ( FAILED( hr ) )
			{
				hr = FindFilterByName(&pFilter, KSCATEGORY_BDA_NETWORK_TUNER,
						capFiltName, CapFiltNum, devName, sizeof(devName) ); 
			}
		}

		if ( FAILED( hr ) )
		{
			if ( !strncmp( capFiltName, "PUSH TS SOURCE", 14 ) ) //hard code "PUSH TS SOURCE"  for debug dump data
				hr = FindFilterByName(&pFilter, CLSID_LegacyAmFilterCategory,
									capFiltName, CapFiltNum, devName, sizeof(devName) );
		}

		SAFE_RELEASE(pFilter);
		if ( FAILED( hr ) )
			return FALSE;

		strncpy( BDAFiltDevName, devName, BDAFiltDevNameSize );
		//it's a virtual tuner device
		
	}

	return TRUE;
}




char * GetClsidString( CLSID clsid, char* pBuf, int nBufferSize  )
{
	LPOLESTR bstCLSID;
	StringFromCLSID( clsid, &bstCLSID );
	WideCharToMultiByte(CP_UTF8, 0, bstCLSID, -1, pBuf, nBufferSize, 0, 0);
	CoTaskMemFree(bstCLSID);
	return pBuf;
}

int GetOSversion( )
{
	OSVERSIONINFOEX osInfo;
	ZeroMemory(&osInfo, sizeof(OSVERSIONINFOEX));
	osInfo.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);
	DWORD vistaPlus = 0;
	if (GetVersionEx((LPOSVERSIONINFO)&osInfo))
	{
		return osInfo.dwMajorVersion;
	}
	return 0;
}