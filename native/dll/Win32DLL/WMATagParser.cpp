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
#include "wmsdk.h"
#include "../../include/sage_WMATagParser.h"

#define SAFECLOSE_HANDLE(p) if ( p ) { CloseHandle( p ); p = NULL; }

static HINSTANCE sageLoadedWMLib = NULL;
static BOOL failedLoadingWMLib = FALSE;

class CWMProp : public IWMReaderCallback
{
public:
	CWMProp( HRESULT *phr )
	{
        m_pReader = NULL;
        m_pHeaderInfo = NULL;

        *phr = S_OK;

        m_hOpenEvent = CreateEvent( NULL, TRUE, FALSE, NULL );
        if ( NULL == m_hOpenEvent )
        {
                *phr = E_OUTOFMEMORY;
        }

        m_hCloseEvent = CreateEvent( NULL, TRUE, FALSE, NULL );
        if ( NULL == m_hCloseEvent )
        {
                *phr = E_OUTOFMEMORY;
        }

        m_bIsDRM = FALSE;
	}
	~CWMProp()
	{
		if (m_pReader)
		{
			if (SUCCEEDED(m_pReader->Close()))
				WaitForSingleObject(m_hCloseEvent, 1000);
		}
        SAFE_RELEASE( m_pReader );
        SAFE_RELEASE( m_pHeaderInfo );

        SAFECLOSE_HANDLE( m_hOpenEvent );
        SAFECLOSE_HANDLE( m_hCloseEvent );
	}
	HRESULT Open( LPCWSTR pwszFileName )
	{
		HRESULT hr;
        hr = m_pReader->Open( pwszFileName, this, NULL );
        if ( FAILED( hr ) )
        {
			return hr;
        }

        WaitForSingleObject( m_hOpenEvent, 5000 );
        if ( FAILED( m_hr ) )
        {
			return hr;
        }
		return S_OK;
	}
	jobject ExtractProperties(JNIEnv* env)
	{
		HRESULT hr = m_pReader->QueryInterface(IID_IWMHeaderInfo3, (void**)&m_pHeaderInfo);
		if (FAILED(hr))
			return NULL;

		static jclass mapClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/HashMap"));
		static jmethodID mapConstruct = env->GetMethodID(mapClass, "<init>", "()V");
		static jmethodID mapPut = env->GetMethodID(mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
		jobject rvMap = env->NewObject(mapClass, mapConstruct);
		
		WORD    cAttributes = 0;
		WCHAR*  pwszName    = NULL;
		WORD    cchName     = 0;
		BYTE*   pbValue     = NULL;
		DWORD   cbValue     = 0;
		WORD    langIndex   = 0;
		WORD    attIndex    = 0;

		WMT_ATTR_DATATYPE attType;

		// Get the total number of attributes in the file.

		hr = m_pHeaderInfo->GetAttributeCountEx(0xFFFF, &cAttributes);
		if (FAILED(hr))
			return NULL;

		// Loop through all the attributes, retrieving and displaying each.
		for(attIndex = 0; attIndex < cAttributes; attIndex++)
		{
			// Get the required buffer lengths for the name and value.

			hr = m_pHeaderInfo->GetAttributeByIndexEx(0xFFFF,
													attIndex,
													NULL,
													&cchName,
													NULL,
													NULL,
													NULL,
													&cbValue);
			if (FAILED(hr))
				continue;

			// Allocate the buffers.

			pwszName = new WCHAR[cchName];
			pbValue = new BYTE[cbValue];

			// Get the attribute.

			hr = m_pHeaderInfo->GetAttributeByIndexEx(0xFFFF,
													attIndex,
													pwszName,
													&cchName,
													&attType,
													&langIndex,
													pbValue,
													&cbValue);
			if (FAILED(hr))
			{
				delete [] pwszName;
				delete [] pbValue;
				continue;
			}

			// Display the attribute global index and name.

			// Display the attribute depending upon type.
			WCHAR* attValueName = new WCHAR[cbValue + 64];
			switch(attType)
			{
			case WMT_TYPE_QWORD:
				_ui64tow_s( *((QWORD*)pbValue), attValueName, sizeof(attValueName), 10);
				break;
			case WMT_TYPE_DWORD:
			case WMT_TYPE_WORD:
				swprintf_s(attValueName, sizeof(attValueName), L"%u", (DWORD) *pbValue);
				break;
			case WMT_TYPE_STRING:
				swprintf_s(attValueName, sizeof(attValueName), L"%s", (WCHAR*) pbValue);
				break;
			case WMT_TYPE_BOOL:
				swprintf_s(attValueName, sizeof(attValueName), L"%s", ((BOOL) *pbValue == TRUE) ? L"True" : L"False");
				break;
//			case WMT_TYPE_BINARY:
//			case WMT_TYPE_GUID:
			default:
				attValueName[0] = '\0';
				break;
			}

			if (attValueName[0] != '\0')
			{
				env->CallObjectMethod(rvMap, mapPut, env->NewString(reinterpret_cast<const jchar*>(pwszName), (int) wcslen(pwszName)), env->NewString(reinterpret_cast<const jchar*>(attValueName), (int) wcslen(attValueName)));
			}

			// Release allocated memory for the next pass.

			delete [] pwszName;
			delete [] pbValue;
			delete [] attValueName;
			cchName = 0;
			cbValue = 0;
		} // End for attIndex.

		return rvMap;
	}
	IWMReader          *m_pReader;
private:
	HRESULT STDMETHODCALLTYPE QueryInterface( REFIID riid,
													  void **ppvObject )
	{
		return( E_NOINTERFACE );
	}
	// Don't implement AddRef and Release. They are only used by the callback
	// in the reader, and the CWMProp object owns the reader, so there's no need
	// to pay attention to AddRef/Release calls.
	ULONG STDMETHODCALLTYPE AddRef() { return 1; }
    ULONG STDMETHODCALLTYPE Release() { return 1; }
	HRESULT STDMETHODCALLTYPE OnSample( /* [in] */ DWORD dwOutputNum,
										/* [in] */ QWORD cnsSampleTime,
										/* [in] */ QWORD cnsSampleDuration,
										/* [in] */ DWORD dwFlags,
										/* [in] */ INSSBuffer __RPC_FAR *pSample,
										/* [in] */ void __RPC_FAR *pvContext)
	{
		return S_OK;
	}
    HRESULT STDMETHODCALLTYPE OnStatus( /* [in] */ WMT_STATUS Status,
										/* [in] */ HRESULT hr,
										/* [in] */ WMT_ATTR_DATATYPE dwType,
										/* [in] */ BYTE __RPC_FAR *pValue,
										/* [in] */ void __RPC_FAR *pvContext)
	{
			switch ( Status )
			{
					case WMT_OPENED:
							{
									m_hr = hr;
									SetEvent( m_hOpenEvent );
									break;
							}

					case WMT_CLOSED:
							{
									m_hr = hr;
									SetEvent( m_hCloseEvent );
									break;
							}
					default:
							break;
			}
			return( S_OK );
	}
	

private:
	IWMHeaderInfo3      *m_pHeaderInfo;
	HANDLE             m_hOpenEvent;
	HANDLE             m_hCloseEvent;
	BOOL               m_bIsDRM;
	HRESULT            m_hr;
};

/*
 * Class:     sage_WMATagParser
 * Method:    extractProperties0
 * Signature: (Ljava/lang/String;)Ljava/util/Map;
 */
JNIEXPORT jobject JNICALL Java_sage_WMATagParser_extractProperties0
  (JNIEnv *env, jobject jo, jstring jfilename)
{
	if (failedLoadingWMLib)
		return NULL;
	HRESULT hr;
	CWMProp myProps(&hr);
	if (FAILED(hr))
	{
		slog((env, "Failure with creating CWMProp 0x%x\r\n", hr));
		return NULL;
	}

	if (!sageLoadedWMLib)
	{
		sageLoadedWMLib = LoadLibrary("WMVCORE.DLL");
		if (!sageLoadedWMLib)
		{
			failedLoadingWMLib = TRUE;
			slog((env, "Failed loading WMVCORE.DLL, cannot perform WMA tag parsing\r\n"));
			return NULL;
		}
	}

	// Dynamically resolve the WMCreateReader call so we don't require WMP10 to load this DLL
	typedef HRESULT (STDMETHODCALLTYPE *FUNC_PTRWMCreateReader)(IUnknown* pUnkCert, DWORD dwRights, IWMReader **ppReader);
	
	FUNC_PTRWMCreateReader lpfnProc = (FUNC_PTRWMCreateReader)GetProcAddress(sageLoadedWMLib, "WMCreateReader");
	hr = lpfnProc(NULL, 0, &myProps.m_pReader);
	if (FAILED(hr))
	{
		slog((env, "Failure with WMCreateReader 0x%x\r\n", hr));
		return NULL;
	}

	const jchar* cFilename = env->GetStringChars(jfilename, NULL);
	hr = myProps.Open(reinterpret_cast<LPCWSTR>(cFilename));
	env->ReleaseStringChars(jfilename, cFilename);
	if (FAILED(hr))
	{
		slog((env, "Failure with WMA Open 0x%x\r\n", hr));
		return NULL;
	}

	return myProps.ExtractProperties(env);
}