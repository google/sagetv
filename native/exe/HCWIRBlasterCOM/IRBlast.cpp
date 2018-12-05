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
// IRBlast.cpp : Implementation of CIRBlast

#include "stdafx.h"
#include "IRBlast.h"
#include "../../../third_party/Hauppauge/hcwIRBlast.h"

// CIRBlast
typedef WORD (WINAPI *pfn_UIR_Open)(BOOL, WORD);
typedef UIRError (WINAPI *pfn_UIR_GetConfig)(int, int, UIR_CFG *);
typedef UIRError (WINAPI *pfn_UIR_GotoChannel)(int, int, int);
typedef bool (WINAPI *pfn_UIR_Close)();

// Global vars
HMODULE hHCWBlastDll = NULL;
WORD      m_IRBlasterPort;      // IRblaster port,
UIR_CFG   m_IRBlasterCFG;       // IRblaster config data
pfn_UIR_Open        pUIR_Open;
pfn_UIR_GetConfig   pUIR_GetConfig;
pfn_UIR_GotoChannel pUIR_GotoChannel;
pfn_UIR_Close       pUIR_Close;

STDMETHODIMP CIRBlast::OpenDevice(VARIANT_BOOL* ret)
{
  m_IRBlasterPort = 0;
  if (!hHCWBlastDll)
  {
    hHCWBlastDll = LoadLibrary(_T("hcwIRblast"));
    if (hHCWBlastDll)
    {
      pUIR_Open        = (pfn_UIR_Open)        GetProcAddress(hHCWBlastDll, "UIR_Open");
      pUIR_GetConfig   = (pfn_UIR_GetConfig)   GetProcAddress(hHCWBlastDll, "UIR_GetConfig");
      pUIR_GotoChannel = (pfn_UIR_GotoChannel) GetProcAddress(hHCWBlastDll, "UIR_GotoChannel");
      pUIR_Close       = (pfn_UIR_Close)       GetProcAddress(hHCWBlastDll, "UIR_Close");
    }
  }
  if (hHCWBlastDll && pUIR_Open && pUIR_GetConfig && pUIR_Close)
  {
    m_IRBlasterPort = pUIR_Open(FALSE, 0);
    //Try to open default IRBlaster device
    m_IRBlasterCFG.cfgDataSize = sizeof(UIR_CFG);
    // Load the previously saved configuration params
    UIRError uerr = pUIR_GetConfig(-1, -1, &m_IRBlasterCFG);
    if(uerr != UIRError_Success)
    {
      m_IRBlasterPort = 0;
    }
    pUIR_Close();
  }

  *ret = m_IRBlasterPort ? VARIANT_TRUE : VARIANT_FALSE;

  return S_OK;
}


STDMETHODIMP CIRBlast::MacroTune(LONG nChannel)
{
  m_IRBlasterPort = 0;
  if (!hHCWBlastDll)
  {
    hHCWBlastDll = LoadLibrary(_T("hcwIRblast"));
    if (hHCWBlastDll)
    {
      pUIR_Open        = (pfn_UIR_Open)        GetProcAddress(hHCWBlastDll, "UIR_Open");
      pUIR_GetConfig   = (pfn_UIR_GetConfig)   GetProcAddress(hHCWBlastDll, "UIR_GetConfig");
      pUIR_GotoChannel = (pfn_UIR_GotoChannel) GetProcAddress(hHCWBlastDll, "UIR_GotoChannel");
      pUIR_Close       = (pfn_UIR_Close)       GetProcAddress(hHCWBlastDll, "UIR_Close");
    }
  }
  if (hHCWBlastDll && pUIR_Open && pUIR_GetConfig && pUIR_GotoChannel && pUIR_Close)
  {
    m_IRBlasterPort = pUIR_Open(FALSE, 0);
    //Try to open default IRBlaster device
    m_IRBlasterCFG.cfgDataSize = sizeof(UIR_CFG);
    // Load the previously saved configuration params
    UIRError uerr = pUIR_GetConfig(-1, -1, &m_IRBlasterCFG);
    if(uerr != UIRError_Success)
    {
      m_IRBlasterPort = 0;
    }

    if (m_IRBlasterPort && pUIR_GotoChannel)
    {
      UIRError uerr = pUIR_GotoChannel(m_IRBlasterCFG.cfgDevice, m_IRBlasterCFG.cfgCodeset, (int) nChannel);
    }
  }

  return S_OK;
}
