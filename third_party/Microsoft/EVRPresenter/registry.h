//////////////////////////////////////////////////////////////////////////
//
// Registry.h: Registry helpers.
// 
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
//////////////////////////////////////////////////////////////////////////

#pragma once

namespace MediaFoundationSamples
{

    #ifndef CHARS_IN_GUID
    const DWORD CHARS_IN_GUID = 39;
    #endif


    // Forward declares
    HRESULT RegisterObject(HMODULE hModule, const GUID& guid, const TCHAR *sDescription, const TCHAR *sThreadingModel);
    HRESULT UnregisterObject(const GUID& guid);
    HRESULT CreateObjectKeyName(const GUID& guid, TCHAR *sName, DWORD cchMax);
    HRESULT SetKeyValue(HKEY hKey, const TCHAR *sName, const TCHAR *sValue);


//*************************************************************
//
//  RegDelnodeRecurse()
//
//  Purpose:    Deletes a registry key and all its subkeys / values.
//
//  Parameters: hKeyRoot    -   Root key
//              lpSubKey    -   SubKey to delete
//
//  Return:     TRUE if successful.
//              FALSE if an error occurs.
//
//*************************************************************

inline BOOL RegDelnodeRecurse (HKEY hKeyRoot, LPTSTR lpSubKey)
{
    LPTSTR lpEnd;
    LONG lResult;
    DWORD dwSize;
    TCHAR szName[MAX_PATH];
    HKEY hKey;
    FILETIME ftWrite;

    // First, see if we can delete the key without having
    // to recurse.

    lResult = RegDeleteKey(hKeyRoot, lpSubKey);

    if (lResult == ERROR_SUCCESS) 
        return TRUE;

    lResult = RegOpenKeyEx (hKeyRoot, lpSubKey, 0, KEY_READ, &hKey);

    if (lResult != ERROR_SUCCESS) 
    {
        if (lResult == ERROR_FILE_NOT_FOUND) {
            printf("Key not found.\n");
            return TRUE;
        } 
        else {
            printf("Error opening key.\n");
            return FALSE;
        }
    }

    // Check for an ending slash and add one if it is missing.

    lpEnd = lpSubKey + lstrlen(lpSubKey);

    if (*(lpEnd - 1) != TEXT('\\')) 
    {
        *lpEnd =  TEXT('\\');
        lpEnd++;
        *lpEnd =  TEXT('\0');
    }

    // Enumerate the keys

    dwSize = MAX_PATH;
    lResult = RegEnumKeyEx(hKey, 0, szName, &dwSize, NULL,
                           NULL, NULL, &ftWrite);

    if (lResult == ERROR_SUCCESS) 
    {
        do {

            StringCchCopy (lpEnd, MAX_PATH*2, szName);

            if (!RegDelnodeRecurse(hKeyRoot, lpSubKey)) {
                break;
            }

            dwSize = MAX_PATH;

            lResult = RegEnumKeyEx(hKey, 0, szName, &dwSize, NULL,
                                   NULL, NULL, &ftWrite);

        } while (lResult == ERROR_SUCCESS);
    }

    lpEnd--;
    *lpEnd = TEXT('\0');

    RegCloseKey (hKey);

    // Try again to delete the key.

    lResult = RegDeleteKey(hKeyRoot, lpSubKey);

    if (lResult == ERROR_SUCCESS) 
        return TRUE;

    return FALSE;
}

//*************************************************************
//
//  RegDelnode()
//
//  Purpose:    Deletes a registry key and all its subkeys / values.
//
//  Parameters: hKeyRoot    -   Root key
//              lpSubKey    -   SubKey to delete
//
//  Return:     TRUE if successful.
//              FALSE if an error occurs.
//
//*************************************************************

inline BOOL RegDelnode (HKEY hKeyRoot, LPTSTR lpSubKey)
{
    TCHAR szDelKey[2 * MAX_PATH];

    StringCchCopy (szDelKey, MAX_PATH*2, lpSubKey);
    return RegDelnodeRecurse(hKeyRoot, szDelKey);

}

    ///////////////////////////////////////////////////////////////////////
    // Name: RegisterObject
    // Desc: Creates the registry entries for a COM object.
    //
    // guid: The object's CLSID
    // sDescription: Description of the object
    // sThreadingMode: Threading model. e.g., "Both"
    ///////////////////////////////////////////////////////////////////////

    inline HRESULT RegisterObject(HMODULE hModule, const GUID& guid, const TCHAR *sDescription, const TCHAR *sThreadingModel)
    {
        HKEY hKey = NULL;
        HKEY hSubkey = NULL;

        TCHAR achTemp[MAX_PATH];

        // Create the name of the key from the object's CLSID
        HRESULT hr = CreateObjectKeyName(guid, achTemp, MAX_PATH);

        // Create the new key.
        if (SUCCEEDED(hr))
        {
            LONG lreturn = RegCreateKeyEx(
                HKEY_CLASSES_ROOT,
                (LPCTSTR)achTemp,     // subkey
                0,                    // reserved
                NULL,                 // class string (can be NULL)
                REG_OPTION_NON_VOLATILE,
                KEY_ALL_ACCESS,
                NULL,                 // security attributes
                &hKey,
                NULL                  // receives the "disposition" (is it a new or existing key)
                );

            hr = __HRESULT_FROM_WIN32(lreturn);
        }

        // The default key value is a description of the object.
        if (SUCCEEDED(hr))
        {
            hr = SetKeyValue(hKey, NULL, sDescription);
        }

        // Create the "InprocServer32" subkey
        if (SUCCEEDED(hr))
        {
            const TCHAR *sServer = TEXT("InprocServer32");

            LONG lreturn = RegCreateKeyEx(hKey, sServer, 0, NULL,
                REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, NULL, &hSubkey, NULL);

            hr = __HRESULT_FROM_WIN32(lreturn);
        }

        // The default value for this subkey is the path to the DLL.
        // Get the name of the module ...
        if (SUCCEEDED(hr))
        {
            DWORD res = GetModuleFileName(hModule, achTemp, MAX_PATH);
            if (res == 0)
            {
                hr = __HRESULT_FROM_WIN32(GetLastError());
            }
            if (res == MAX_PATH)
            {
                hr = E_FAIL; // buffer too small
            }
        }

        // ... and set the default key value.
        if (SUCCEEDED(hr))
        {
            hr = SetKeyValue(hSubkey, NULL, achTemp);
        }

        // Add a new value to the subkey, for "ThreadingModel" = <threading model>
        if (SUCCEEDED(hr))
        {
            hr = SetKeyValue(hSubkey, TEXT("ThreadingModel"), sThreadingModel);
        }

        // close hkeys

        if (hSubkey != NULL)
        {
            RegCloseKey( hSubkey );
        }

        if (hKey != NULL)
        {
            RegCloseKey( hKey );
        }

        return hr;



    }

    ///////////////////////////////////////////////////////////////////////
    // Name: UnregisterObject
    // Desc: Deletes the registry entries for a COM object.
    //
    // guid: The object's CLSID
    ///////////////////////////////////////////////////////////////////////

    inline HRESULT UnregisterObject(const GUID& guid)
    {
        TCHAR achTemp[MAX_PATH];

        HRESULT hr = CreateObjectKeyName(guid, achTemp, MAX_PATH);

        if (SUCCEEDED(hr))
        {
            // Delete the key recursively.
            DWORD res = RegDelnode(HKEY_CLASSES_ROOT, achTemp);

            if (res == ERROR_SUCCESS)
            {
                hr = S_OK;
            }
            else
            {
                hr = __HRESULT_FROM_WIN32(res);
            }
        }

        return hr;
    }


    ///////////////////////////////////////////////////////////////////////
    // Name: CreateObjectKeyName
    // Desc: Converts a CLSID into a string with the form "CLSID\{clsid}"
    ///////////////////////////////////////////////////////////////////////

    inline HRESULT CreateObjectKeyName(const GUID& guid, TCHAR *sName, DWORD cchMax)
    {
      // convert CLSID uuid to string 
      OLECHAR szCLSID[CHARS_IN_GUID];
      HRESULT hr = StringFromGUID2(guid, szCLSID, CHARS_IN_GUID);
      if (FAILED(hr))
      {
          return hr;
      }

      // Create a string of the form "CLSID\{clsid}"
      return StringCchPrintf(sName, cchMax, TEXT("CLSID\\%ls"), szCLSID);
    }

    ///////////////////////////////////////////////////////////////////////
    // Name: SetKeyValue
    // Desc: Sets a string value (REG_SZ) for a registry key
    //
    // hKey:   Handle to the registry key.
    // sName:  Name of the value. Use NULL for the default value.
    // sValue: The string value.
    ///////////////////////////////////////////////////////////////////////

    inline HRESULT SetKeyValue(HKEY hKey, const TCHAR *sName, const TCHAR *sValue)
    {
        size_t cch = 0;

        HRESULT hr = StringCchLength(sValue, MAXLONG, &cch);
        if (SUCCEEDED(hr))
        {
            // Size must include NULL terminator, which is not counted in StringCchLength
            DWORD  cbData = ((DWORD)cch + 1) * sizeof(TCHAR);

            // set description string
            LONG ret = RegSetValueEx(hKey, sName, 0, REG_SZ, (BYTE*)sValue, cbData);
            if (ret == ERROR_SUCCESS)
            {
                hr = S_OK;
            }
            else
            {
                hr = HRESULT_FROM_WIN32(ret);
            }
        }
        return hr;
    }

}; // namespace MediaFoundationSamples
