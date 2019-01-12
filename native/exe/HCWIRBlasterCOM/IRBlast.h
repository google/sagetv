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
// IRBlast.h : Declaration of the CIRBlast

#pragma once
#include "resource.h"       // main symbols



#include "HCWIRBlasterCOM_i.h"



#if defined(_WIN32_WCE) && !defined(_CE_DCOM) && !defined(_CE_ALLOW_SINGLE_THREADED_OBJECTS_IN_MTA)
#error "Single-threaded COM objects are not properly supported on Windows CE platform, such as the Windows Mobile platforms that do not include full DCOM support. Define _CE_ALLOW_SINGLE_THREADED_OBJECTS_IN_MTA to force ATL to support creating single-thread COM object's and allow use of it's single-threaded COM object implementations. The threading model in your rgs file was set to 'Free' as that is the only threading model supported in non DCOM Windows CE platforms."
#endif

using namespace ATL;


// CIRBlast

class ATL_NO_VTABLE CIRBlast :
	public CComObjectRootEx<CComSingleThreadModel>,
	public CComCoClass<CIRBlast, &CLSID_IRBlast>,
	public IDispatchImpl<IIRBlast, &IID_IIRBlast, &LIBID_HCWIRBlasterCOMLib, /*wMajor =*/ 1, /*wMinor =*/ 0>
{
public:
	CIRBlast()
	{
	}

DECLARE_REGISTRY_RESOURCEID(IDR_IRBLAST)


BEGIN_COM_MAP(CIRBlast)
	COM_INTERFACE_ENTRY(IIRBlast)
	COM_INTERFACE_ENTRY(IDispatch)
END_COM_MAP()



	DECLARE_PROTECT_FINAL_CONSTRUCT()

	HRESULT FinalConstruct()
	{
		return S_OK;
	}

	void FinalRelease()
	{
	}

public:



	STDMETHOD(OpenDevice)(VARIANT_BOOL* ret);
	STDMETHOD(MacroTune)(LONG nChannel);
};

OBJECT_ENTRY_AUTO(__uuidof(IRBlast), CIRBlast)
