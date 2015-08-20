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
#include "SageTVWin32DLL.h"
#include "../../include/sage_RoxioFileExport.h"

// Let's import type libraries. I put whole default path from Creator7 installation
// If you have the different path - change it
#pragma warning ( disable : 4278 )
#pragma warning ( disable : 4192 )
#import "C:\Program Files\Common Files\Roxio Shared\SharedCOM\CPSCommonInterfaces.tlb"	no_namespace named_guids exclude ("_SYSTEMTIME") exclude ("tagSIZE")
#import "C:\Program Files\Common Files\Roxio Shared\SharedCOM\CPSFileLoader.dll" no_namespace named_guids
#import "C:\Program Files\Common Files\Roxio Shared\SharedCOM\CPSAlbumCore.exe" no_namespace named_guids
#import "C:\Program Files\Common Files\Roxio Shared\SharedCOM\CPSAlbumObjects.dll" no_namespace named_guids

class RoxioCOMPtrs
{
public:
	RoxioCOMPtrs() : spFileLoader(CLSID_CPSFileLoader)
	{
	}
	~RoxioCOMPtrs()
	{
		_bstr_t bstrDBName = spAlbumOptions->GetDBName();
		spLibFactory->Close(bstrDBName);
		spFileLoader->Terminate(); // magic. Need this in the end of application
	}
	HRESULT Init(const jchar* collectionName)
	{
		HRESULT hr = spAlbumOptions.CreateInstance(CLSID_CPSAlbumOptions); // this is a singleton
		if(spAlbumOptions==NULL)
		{
			return hr;
		}
		_bstr_t bstrDBLocation = spFileLoader->GetPhotoSuiteXAppDataPath();
		hr = spLibFactory.CreateInstance(CLSID_CPSLibFactory); // this is a singleton. It can be created anytime
		if(spLibFactory==NULL)
		{
			return hr;
		}
		_bstr_t bstrDBName = spAlbumOptions->GetDBName();
		spLibrary=spLibFactory->Open(bstrDBName, bstrDBLocation); // you should keep this pointer if you add more then one file during your app execution
		if(spLibrary==NULL)
		{
			return hr;
		}
		long lRootAlbumID=spLibrary->GetSpecialAlbumID(SAT_ROOT);
		ICPSAlbumPtr spRootAlbum=spLibrary->GetAlbum(lRootAlbumID);

		// let's find our album
		_bstr_t bstrMyAlbumName(collectionName);
		long lMyAlbumID=spRootAlbum->FindItemByName(bstrMyAlbumName); // the Album name better be localized
		if(lMyAlbumID<0)
		{ // not found -  let's create it
			ICPSCategoryPtr pAllAlbums=spLibrary->GetAllAlbums();
			lMyAlbumID=pAllAlbums->CreateAlbum(_bstr_t(L"skin::AlbumImages\\AlbumCover.png"),bstrMyAlbumName,lRootAlbumID); // magic string
		}
		if(lMyAlbumID<0)
		{ 
			return E_FAIL;
		}
		spMyAlbum=spLibrary->GetAlbum(lMyAlbumID);
		return S_OK;
	}
	ICPSFileServerPtr spFileLoader;
	ICPSAlbumOptionsPtr spAlbumOptions;
 	ICPSLibFactoryPtr spLibFactory;
	ICPSLibraryPtr spLibrary;
	ICPSAlbumPtr spMyAlbum;
};
/*static ICPSFileServerPtr spFileLoader = NULL;
static ICPSAlbumOptionsPtr spAlbumOptions = NULL;
static ICPSLibFactoryPtr spLibFactory = NULL;
static ICPSLibraryPtr spLibrary = NULL;*/
/*
 * Class:     sage_Sage
 * Method:    exportFilesToExtNativeDB
 * Signature: (Ljava/lang/String;[Ljava/lang/String;)Z
 *//*
JNIEXPORT jboolean JNICALL Java_sage_Sage_exportFilesToExtNativeDB
  (JNIEnv *env, jclass jc, jstring dbName, jobjectArray jfilenames)
{
	static BOOL useNativeExport = TRUE;
	try
	{
		if (!dbName || !jfilenames) return JNI_FALSE;
		if (useNativeExport)
		{
			// need this bracket to destroy smart pointer before CoUninitialize();
			static BOOL firstRun = TRUE;
			if (firstRun)
			{
				HRESULT hRes = 	CoInitializeEx(NULL, COM_THREADING_MODE);
				if(FAILED(hRes)){
					slog((env, "COM failed\r\n"));
					return JNI_FALSE;
				}
				hRes = CoCreateInstance(CLSID_CPSFileLoader, NULL, CLSCTX_INPROC_SERVER,
					__uuidof(ICPSFileServerPtr), (void **)&spFileLoader);
//				spFileLoader = new ICPSFileServerPtr(CLSID_CPSFileLoader);
				if(FAILED(hRes) || spFileLoader==NULL){
					slog((env, "Native DB exporter is not needed-0\r\n"));
					useNativeExport = FALSE;
					return JNI_FALSE;
				}
				// let's find database path
				_bstr_t bstrDBLocation = spFileLoader->GetPhotoSuiteXAppDataPath();
				HRESULT hr=S_OK;

				hRes = CoCreateInstance(CLSID_CPSAlbumOptions, NULL, CLSCTX_INPROC_SERVER,
					__uuidof(ICPSAlbumOptionsPtr), (void **)&spAlbumOptions);
				//spAlbumOptions = new ICPSAlbumOptionsPtr();
				//hr=spAlbumOptions.CreateInstance(CLSID_CPSAlbumOptions); // this is a singleton
				if(FAILED(hRes) || spAlbumOptions==NULL){
					slog((env, "Native DB exporter is not needed-1\r\n"));
					useNativeExport = FALSE;
					return JNI_FALSE;
				}
				// open database
				hRes = CoCreateInstance(CLSID_CPSLibFactory, NULL, CLSCTX_INPROC_SERVER,
					__uuidof(ICPSLibFactoryPtr), (void **)&spLibFactory);
// 				spLibFactory = new ICPSLibFactoryPtr();
//				hr=spLibFactory->CreateInstance(CLSID_CPSLibFactory); // this is a singleton. It can be created anytime
				if(FAILED(hRes) || spLibFactory==NULL){
					slog((env, "Native DB exporter is not needed-2\r\n"));
					useNativeExport = FALSE;
					return JNI_FALSE;
				}
				// let's find database name
				_bstr_t bstrDBName = spAlbumOptions->GetDBName();
				spLibrary = spLibFactory->Open(bstrDBName, bstrDBLocation); // you should keep this pointer if you add more then one file during your app execution
				if(spLibrary==NULL){
					slog((env, "Native DB exporter is not needed-3\r\n"));
					useNativeExport = FALSE;
					return JNI_FALSE;
				}
				firstRun = FALSE;
			}

			// let's get root album

			long lRootAlbumID=spLibrary->GetSpecialAlbumID(SAT_ROOT);
			ICPSAlbumPtr spRootAlbum=spLibrary->GetAlbum(lRootAlbumID);

			// let's find our album
			const jchar* jname = env->GetStringChars(dbName, NULL);
			_bstr_t bstrMyAlbumName(jname);
			env->ReleaseStringChars(dbName, jname);
			long lMyAlbumID=spRootAlbum->FindItemByName(bstrMyAlbumName); // the Album name better be localized
			if(lMyAlbumID<0){ // not found -  let's create it
				ICPSCategoryPtr pAllAlbums=spLibrary->GetAllAlbums();
				lMyAlbumID=pAllAlbums->CreateAlbum(_bstr_t(L"skin::AlbumImages\\AlbumCover.png"),bstrMyAlbumName,lRootAlbumID); // magic string
			}
			if(lMyAlbumID<0){ 
				slog((env, "ERROR in db exporting.\r\n"));
			}
			ICPSAlbumPtr spMyAlbum=spLibrary->GetAlbum(lMyAlbumID);
			
			// no let's add or update the file. If full path name does exist the following code will update the file info and thumnbnail
			int numFiles = env->GetArrayLength(jfilenames);
			for(int i=0;i<numFiles;i++){
				_bstr_t fullMoniker(L"file::");
				jstring currjstr = (jstring) env->GetObjectArrayElement(jfilenames, i);
				const jchar* cname = env->GetStringChars(currjstr, NULL);
				fullMoniker+=cname;
				env->ReleaseStringChars(currjstr, cname);
				WCHAR *pExt=wcsrchr(fullMoniker,L'.');
				if(pExt!=NULL){
					long lType=spFileLoader->GetFitTypeByExtention(_bstr_t(pExt));
					if(lType>=0){
						HRESULT hr=spMyAlbum->Add2(lType,fullMoniker);
						if(FAILED(hr))
						{
							slog((env, "Currupt file on export\r\n"));
						}
					}else{
						slog((env, "Wrong file extention\r\n"));
					}
				}else{
					slog((env, "Your file doesn't have extention\r\n"));
				}
			}
			// Album Core generates thumbnails in background. If you close too soon and Media Manager is not running 
			// the thumbnail generation will fail. Dont worry: It will be re-generated later automatically.
			// But it is better to close the libary in the end of your application

//			spLibFactory->Close(bstrDBName);
			//::CoUninitialize();
			return JNI_TRUE;
		}
	}
	catch (...)
	{
		slog((env, "NATIVE exception in DB export\r\n"));
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_RoxioFileExport
 * Method:    openRoxioPlugin0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_RoxioFileExport_openRoxioPlugin0
  (JNIEnv *env, jobject jo, jstring jDBName)
{
	try
	{
		slog((env, "Opening Roxio Export Plugin\r\n"));
		HRESULT hr = CoInitializeEx(NULL, COM_THREADING_MODE);
		if (FAILED(hr)) return 0;
		RoxioCOMPtrs* ptrs = new RoxioCOMPtrs();
		if (ptrs->spFileLoader==NULL)
		{
			slog((env, "Disabling Roxio Export Plugin\r\n"));
			delete ptrs;
			CoUninitialize();
			return 0;
		}
		const jchar* cname = env->GetStringChars(jDBName, NULL);
		hr = ptrs->Init(cname);
		env->ReleaseStringChars(jDBName, cname);
		return (jlong) ptrs;
	}
	catch (...)
	{
		slog((env, "NATIVE exception in plugin creation\r\n"));
	}
	return 0;
}

/*
 * Class:     sage_RoxioFileExport
 * Method:    closeRoxioPlugin0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_RoxioFileExport_closeRoxioPlugin0
  (JNIEnv *env, jobject jo, jlong nativePtr)
{
	try
	{
		if (nativePtr)
		{
			slog((env, "Closing Roxio Export Plugin\r\n"));
			RoxioCOMPtrs* ptrs = (RoxioCOMPtrs*) nativePtr;
			delete ptrs;
			CoUninitialize();
		}
	}
	catch (...){}
}

/*
 * Class:     sage_RoxioFileExport
 * Method:    addFilesToLibrary0
 * Signature: (J[Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_RoxioFileExport_addFilesToLibrary0
  (JNIEnv *env, jobject jo, jlong nativePtr, jobjectArray jfilenames)
{
	if (!nativePtr) return;
	try
	{
		RoxioCOMPtrs* ptrs = (RoxioCOMPtrs*) nativePtr;

		// no let's add or update the file. If full path name does exist the following code will update the file info and thumnbnail
		int numFiles = env->GetArrayLength(jfilenames);
		slog((env, "Exporting %d files with Roxio plugin\r\n", numFiles));
		for(int i=0;i<numFiles;i++)
		{
			_bstr_t fullMoniker(L"file::");
			jstring currjstr = (jstring) env->GetObjectArrayElement(jfilenames, i);
			const jchar* cname = env->GetStringChars(currjstr, NULL);
			fullMoniker+=cname;
			env->ReleaseStringChars(currjstr, cname);
			WCHAR *pExt=wcsrchr(fullMoniker,L'.');
			if(pExt!=NULL)
			{
				long lType=ptrs->spFileLoader->GetFitTypeByExtention(_bstr_t(pExt));
				if(lType>=0)
				{
					HRESULT hr=ptrs->spMyAlbum->Add2(lType,fullMoniker);
					if(FAILED(hr))
					{
						slog((env, "Currupt file on export\r\n"));
					}
				}
				else
				{
					slog((env, "Wrong file extention\r\n"));
				}
			}
			else
			{
				slog((env, "Your file doesn't have extention\r\n"));
			}
		}
	}
	catch (...)
	{
		slog((env, "NATIVE exception in DB export\r\n"));
	}
}

