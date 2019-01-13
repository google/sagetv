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
// HCWIRBlasterCOM.cpp : Implementation of WinMain


#include "stdafx.h"
#include "resource.h"
#include "HCWIRBlasterCOM_i.h"


using namespace ATL;


class CHCWIRBlasterCOMModule : public ATL::CAtlExeModuleT< CHCWIRBlasterCOMModule >
{
public :
	DECLARE_LIBID(LIBID_HCWIRBlasterCOMLib)
	DECLARE_REGISTRY_APPID_RESOURCEID(IDR_HCWIRBLASTERCOM, "{9CA9D5C7-DBBE-4D4E-A135-DF12D4FC5550}")
};

CHCWIRBlasterCOMModule _AtlModule;



//
extern "C" int WINAPI _tWinMain(HINSTANCE /*hInstance*/, HINSTANCE /*hPrevInstance*/, LPTSTR /*lpCmdLine*/, int nShowCmd)
{
	return _AtlModule.WinMain(nShowCmd);
}

