//////////////////////////////////////////////////////////////////////////
// Copyright 2015 The SageTV Authors. All Rights Reserved.
//
// PresentEngine.cpp: Defines the D3DPresentEngine object.
// 
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
//
//////////////////////////////////////////////////////////////////////////
// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)
#pragma warning(disable : 4995)

#include "EVRPresenter.h"

static DWORD PRESENTER_BUFFER_COUNT = 2;

//-----------------------------------------------------------------------------
// Constructor
//-----------------------------------------------------------------------------

D3DPresentEngine::D3DPresentEngine(HRESULT& hr) : 
    m_hwnd(NULL),
    m_pDeviceManager(NULL),
    m_pSurfaceRepaint(NULL)
{
	SetRect(&m_rcDestRect, 0, 0, 720, 480);

    ZeroMemory(&m_DisplayMode, sizeof(m_DisplayMode));

	HKEY rootKey = HKEY_LOCAL_MACHINE;
	char currVer[16];
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(currVer);
	if (RegOpenKeyExA(rootKey, "Software\\JavaSoft\\Java Runtime Environment", 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
	{
		hr = E_FAIL;
		return;
	}
	
	if (RegQueryValueExA(myKey, "CurrentVersion", 0, &readType, (LPBYTE)currVer, &hsize) != ERROR_SUCCESS)
	{
		RegCloseKey(myKey);
		hr = E_FAIL;
		return;
	}
	RegCloseKey(myKey);
	char pathKey[1024];
	strcpy(pathKey, "Software\\JavaSoft\\Java Runtime Environment\\");
	strcat(pathKey, currVer);
	char jvmPath[1024];
	if (RegOpenKeyExA(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
	{
		hr = E_FAIL;
		return;
	}
	hsize = sizeof(jvmPath);
	if (RegQueryValueExA(myKey, "RuntimeLib", 0, &readType, (LPBYTE)jvmPath, &hsize) != ERROR_SUCCESS)
	{
		RegCloseKey(myKey);
		hr = E_FAIL;
		return;
	}
	RegCloseKey(myKey);

	// Go to the 2nd to last backslash, and append jawt.dll from there to complete the string
	HMODULE jvmMod = LoadLibraryA(jvmPath);

	jsize numVMs;
	typedef jint (JNICALL *JNIGetCreatedJavaVMsPROC)(JavaVM **, jsize, jsize *);
	
	JNIGetCreatedJavaVMsPROC lpfnProc = (JNIGetCreatedJavaVMsPROC)GetProcAddress(jvmMod, "JNI_GetCreatedJavaVMs");
	if (lpfnProc(&m_vmBuf, 1, &numVMs))
	{
		hr = E_FAIL;
		return;
	}
	JNIEnv* env;
	jint threadState = m_vmBuf->GetEnv((void**)&env, JNI_VERSION_1_2);
	if (threadState == JNI_EDETACHED)
		m_vmBuf->AttachCurrentThread((void**)&env, NULL);
	m_dx9SageClass = (jclass) env->NewGlobalRef(env->FindClass("sage/DirectX9SageRenderer"));
	if (m_dx9SageClass)
		m_dx9UpdateMethodID = env->GetStaticMethodID(m_dx9SageClass, "vmr9RenderNotify", "(IJJJJIIII)V");
	if (threadState == JNI_EDETACHED)
		m_vmBuf->DetachCurrentThread();
	if (!m_dx9SageClass)
	{
		hr = E_FAIL;
		return;
	}

	rootKey = HKEY_LOCAL_MACHINE;
	DWORD holder;
	hsize = sizeof(holder);
	if ( RegOpenKeyEx(rootKey, TEXT("SOFTWARE\\Frey Technologies\\SageTV\\DirectX9"), 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, TEXT("EVRBufferCount"), 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			PRESENTER_BUFFER_COUNT = holder;
		}
		RegCloseKey(myKey);
	}
}


//-----------------------------------------------------------------------------
// Destructor
//-----------------------------------------------------------------------------

D3DPresentEngine::~D3DPresentEngine()
{
	if (m_pDeviceManager)
		m_pDeviceManager->CloseDeviceHandle(m_hSharedDevice);
    SAFE_RELEASE(m_pSurfaceRepaint);
    SAFE_RELEASE(m_pDeviceManager);
	if (m_dx9SageClass)
	{
		JNIEnv* env;
		jint threadState = m_vmBuf->GetEnv((void**)&env, JNI_VERSION_1_2);
		if (threadState == JNI_EDETACHED)
			m_vmBuf->AttachCurrentThread((void**)&env, NULL);
		env->DeleteGlobalRef(m_dx9SageClass);
		if (threadState == JNI_EDETACHED)
			m_vmBuf->DetachCurrentThread();
	}
}

HRESULT D3DPresentEngine::set_D3DDeviceMgr(IDirect3DDeviceManager9* pD3DDevMgr) 
{ 
	HRESULT hr = pD3DDevMgr->OpenDeviceHandle(&m_hSharedDevice);
    D3DDEVICE_CREATION_PARAMETERS params;
	if (SUCCEEDED(hr))
	{
		m_pDeviceManager = pD3DDevMgr; 
		IDirect3DDevice9 *pDevice = NULL;
		hr = m_pDeviceManager->LockDevice(m_hSharedDevice, &pDevice, TRUE);
		if (hr == DXVA2_E_NEW_VIDEO_DEVICE)
		{
			m_pDeviceManager->CloseDeviceHandle(m_hSharedDevice);
			m_pDeviceManager->OpenDeviceHandle(&m_hSharedDevice);
			hr = m_pDeviceManager->LockDevice(m_hSharedDevice, &pDevice, TRUE);
		}
		if (SUCCEEDED(hr))
		{
			hr = pDevice->GetCreationParameters(&params);
			IDirect3D9* pD3D9 = NULL;
			pDevice->GetDirect3D(&pD3D9);
			hr = pD3D9->GetAdapterDisplayMode(params.AdapterOrdinal, &m_DisplayMode);
			SAFE_RELEASE(pD3D9);

			m_pDeviceManager->UnlockDevice(m_hSharedDevice, FALSE);
			SAFE_RELEASE(pDevice);
		}

		pD3DDevMgr->AddRef();
		return S_OK;
	}
	else
		return hr;
}



//-----------------------------------------------------------------------------
// GetService
//
// Returns a service interface from the presenter engine.
// The presenter calls this method from inside it's implementation of 
// IMFGetService::GetService.
//
// Classes that derive from D3DPresentEngine can override this method to return 
// other interfaces. If you override this method, call the base method from the 
// derived class.
//-----------------------------------------------------------------------------

HRESULT D3DPresentEngine::GetService(REFGUID guidService, REFIID riid, void** ppv)
{
    assert(ppv != NULL);

    HRESULT hr = S_OK;

	// The second part of this is needed for DXVA2 to work properly
    if (riid == __uuidof(IDirect3DDeviceManager9) || guidService == MR_VIDEO_ACCELERATION_SERVICE)
    {
        if (m_pDeviceManager == NULL)
        {
            hr = MF_E_UNSUPPORTED_SERVICE;
        }
        else
        {
            *ppv = m_pDeviceManager;
            m_pDeviceManager->AddRef();
        }
    }
    else
    {
        hr = MF_E_UNSUPPORTED_SERVICE;
    }

    return hr;
}


//-----------------------------------------------------------------------------
// CheckFormat
//
// Queries whether the D3DPresentEngine can use a specified Direct3D format.
//-----------------------------------------------------------------------------

HRESULT D3DPresentEngine::CheckFormat(D3DFORMAT format)
{
    HRESULT hr = S_OK;
	
    UINT uAdapter = D3DADAPTER_DEFAULT;
    D3DDEVTYPE type = D3DDEVTYPE_HAL;

    D3DDEVICE_CREATION_PARAMETERS params;
	if (m_pDeviceManager)
	{
		IDirect3DDevice9 *pDevice = NULL;
		hr = m_pDeviceManager->LockDevice(m_hSharedDevice, &pDevice, TRUE);
		if (hr == DXVA2_E_NEW_VIDEO_DEVICE)
		{
			m_pDeviceManager->CloseDeviceHandle(m_hSharedDevice);
			m_pDeviceManager->OpenDeviceHandle(&m_hSharedDevice);
			hr = m_pDeviceManager->LockDevice(m_hSharedDevice, &pDevice, TRUE);
		}
		if (SUCCEEDED(hr))
		{
			hr = pDevice->GetCreationParameters(&params);

			if (SUCCEEDED(hr))
			{
				uAdapter = params.AdapterOrdinal;
				type = params.DeviceType;

				IDirect3D9* pD3D9 = NULL;
				pDevice->GetDirect3D(&pD3D9);
				hr = pD3D9->GetAdapterDisplayMode(uAdapter, &m_DisplayMode);
				if (SUCCEEDED(hr))
				{
					hr = pD3D9->CheckDeviceType(uAdapter, type, m_DisplayMode.Format, format, TRUE);
				}
				SAFE_RELEASE(pD3D9);
			}
			m_pDeviceManager->UnlockDevice(m_hSharedDevice, FALSE);
			SAFE_RELEASE(pDevice);
		}
	}
//	slog(("EVRPresenter check format %d hr=0x%x\r\n", (int)format, hr));
    return hr;
}



//-----------------------------------------------------------------------------
// SetVideoWindow
// 
// Sets the window where the video is drawn.
//-----------------------------------------------------------------------------

HRESULT D3DPresentEngine::SetVideoWindow(HWND hwnd)
{
    HRESULT hr = S_OK;

    return hr;
}

//-----------------------------------------------------------------------------
// SetDestinationRect
// 
// Sets the region within the video window where the video is drawn.
//-----------------------------------------------------------------------------

HRESULT D3DPresentEngine::SetDestinationRect(const RECT& rcDest)
{
    if (EqualRect(&rcDest, &m_rcDestRect))
    {
        return S_OK; // No change.
    }

    HRESULT hr = S_OK;

    AutoLock lock(m_ObjectLock);

    m_rcDestRect = rcDest;

    return hr;
}



//-----------------------------------------------------------------------------
// CreateVideoSamples
// 
// Creates video samples based on a specified media type.
// 
// pFormat: Media type that describes the video format.
// videoSampleQueue: List that will contain the video samples.
//
// Note: For each video sample, the method creates a swap chain with a
// single back buffer. The video sample object holds a pointer to the swap
// chain's back buffer surface. The mixer renders to this surface, and the
// D3DPresentEngine renders the video frame by presenting the swap chain.
//-----------------------------------------------------------------------------

HRESULT D3DPresentEngine::CreateVideoSamples(
    IMFMediaType *pFormat, 
    VideoSampleList& videoSampleQueue
    )
{
    if (pFormat == NULL || m_pDeviceManager == NULL)
    {
        return MF_E_UNEXPECTED;
    }

	HRESULT hr = S_OK;
    UINT32 width = 0, height = 0;
    DWORD d3dFormat = 0;

	IMFSample *pVideoSample = NULL;            // Sampl
	IDirect3DSurface9* pSurface = NULL;
	
    AutoLock lock(m_ObjectLock);

    ReleaseResources();

	// Get the Direct3D device
	IDirect3DDevice9 *pDevice = NULL;
	hr = m_pDeviceManager->LockDevice(m_hSharedDevice, &pDevice, TRUE);
	if (hr == DXVA2_E_NEW_VIDEO_DEVICE)
	{
		m_pDeviceManager->CloseDeviceHandle(m_hSharedDevice);
		m_pDeviceManager->OpenDeviceHandle(&m_hSharedDevice);
		hr = m_pDeviceManager->LockDevice(m_hSharedDevice, &pDevice, TRUE);
	}
	if (FAILED(hr)) goto done;

	// Create the surface to use for the sample
    VideoTypeBuilder *pTypeHelper = NULL;

    // Create the helper object for reading the proposed type.
    CHECK_HR(hr = MediaTypeBuilder::Create(pFormat, &pTypeHelper));

    // Get some information about the video format.
    CHECK_HR(hr = pTypeHelper->GetFrameDimensions(&width, &height));
    CHECK_HR(hr = pTypeHelper->GetFourCC(&d3dFormat));
	UINT32 parNum, parDen;
	pTypeHelper->GetPixelAspectRatio(&parNum, &parDen);
	m_arSize.cx = width / parDen;
	m_arSize.cy = height / parNum;

//	slog(("EVRPresenter create video sample %dx%d format=%d\r\n", width, height, (int)d3dFormat));
    // Create the video samples.
    for (unsigned int i = 0; i < PRESENTER_BUFFER_COUNT; i++)
    {
		// Create the D3D surface for this media sample
		hr = pDevice->CreateRenderTarget(width, height, 
			(D3DFORMAT)d3dFormat, D3DMULTISAMPLE_NONE, 0, FALSE,
			&pSurface, NULL);
/*		IDirect3DTexture9 *pTexture = NULL;
		CHECK_HR(hr = pDevice->CreateTexture(width, height, 1, D3DUSAGE_RENDERTARGET, 
			(D3DFORMAT)d3dFormat, D3DPOOL_DEFAULT, &pTexture, NULL));
		CHECK_HR(hr = pTexture->GetSurfaceLevel(0, &pSurface));*/

		// Create the video sample from the surface.
        CHECK_HR(hr = CreateD3DSample(pSurface, pDevice, &pVideoSample));

        // Add it to the list.
		CHECK_HR(hr = videoSampleQueue.InsertBack(pVideoSample));

    	SAFE_RELEASE(pSurface);
		SAFE_RELEASE(pVideoSample);
//		SAFE_RELEASE(pTexture);
    }

done:
    if (FAILED(hr))
    {
        ReleaseResources();
    }
		
	SAFE_RELEASE(pTypeHelper);

	if (pDevice)
	{
		m_pDeviceManager->UnlockDevice(m_hSharedDevice, FALSE);
		SAFE_RELEASE(pDevice);
	}
    return hr;
}



//-----------------------------------------------------------------------------
// ReleaseResources
// 
// Released Direct3D resources used by this object. 
//-----------------------------------------------------------------------------

void D3DPresentEngine::ReleaseResources()
{
    // Let the derived class release any resources it created.
	OnReleaseResources();

	JNIEnv* env;
	jint threadState = m_vmBuf->GetEnv((void**)&env, JNI_VERSION_1_2);
	if (threadState == JNI_EDETACHED)
		m_vmBuf->AttachCurrentThread((void**)&env, NULL);
	// Indicate that this surface is no longer in use
	env->CallStaticVoidMethod(m_dx9SageClass, m_dx9UpdateMethodID, 0xCAFEBABE/*userid*/, 0, 0, 0, 0, 0, 0);
	// Clear any pending exceptions, we can't handle them here
	env->ExceptionClear();
	//slog(("Deallocating %d VMR9 surfaces\r\n", m_dwNumSurfaces));
	if (threadState == JNI_EDETACHED)
		m_vmBuf->DetachCurrentThread();

	SAFE_RELEASE(m_pSurfaceRepaint);
}

//-----------------------------------------------------------------------------
// PresentSample
//
// Presents a video frame.
//
// pSample:  Pointer to the sample that contains the surface to present. If 
//           this parameter is NULL, the method paints a black rectangle.
// llTarget: Target presentation time.
//
// This method is called by the scheduler and/or the presenter.
//-----------------------------------------------------------------------------

HRESULT D3DPresentEngine::PresentSample(IMFSample* pSample, LONGLONG llTarget)
{
    HRESULT hr = S_OK;

    IMFMediaBuffer* pBuffer = NULL;
    IDirect3DSurface9* pSurface = NULL;

    if (pSample)
    {
        // Get the buffer from the sample.
        CHECK_HR(hr = pSample->GetBufferByIndex(0, &pBuffer));

        // Get the surface from the buffer.
		IMFGetService* mfGetServ = NULL;
		CHECK_HR(hr = pBuffer->QueryInterface(IID_IMFGetService, (void**)&mfGetServ));
		CHECK_HR(hr = mfGetServ->GetService(MR_BUFFER_SERVICE, __uuidof(IDirect3DSurface9), (void**)&pSurface));
		SAFE_RELEASE(mfGetServ);
    }
    else if (m_pSurfaceRepaint)
    {
        // Redraw from the last surface.
        pSurface = m_pSurfaceRepaint;
        pSurface->AddRef();
    }

    if (pSurface)
    {
		JNIEnv* env;
		if (!m_vmBuf->AttachCurrentThread((void**)&env, NULL))
		{
			D3DSURFACE_DESC vSurfDesc;
			pSurface->GetDesc(&vSurfDesc);
			//m_arSize.cx = vSurfDesc.Width;//lpPresInfo->szAspectRatio.cx;
			//m_arSize.cy = vSurfDesc.Height;//lpPresInfo->szAspectRatio.cy;
			m_videoSize.cx = vSurfDesc.Width;
			m_videoSize.cy = vSurfDesc.Height;
			env->CallStaticVoidMethod(m_dx9SageClass, m_dx9UpdateMethodID,
				(jint) 0xCAFEBABE, (jlong) pSurface,
				(jlong) 0,//texture,
				(jlong) llTarget, (jlong) llTarget/*end*/,
				vSurfDesc.Width, vSurfDesc.Height, m_arSize.cx, m_arSize.cy);
			
			env->ExceptionClear();
			m_vmBuf->DetachCurrentThread();
		}

        // Store this pointer in case we need to repaint the surface.
        CopyComPointer(m_pSurfaceRepaint, pSurface);
    }
    else
    {
        // No surface.
    }

done:
    SAFE_RELEASE(pSurface);
    SAFE_RELEASE(pBuffer);

    if (FAILED(hr))
    {
        if (hr == D3DERR_DEVICELOST || hr == D3DERR_DEVICENOTRESET || hr == D3DERR_DEVICEHUNG)
        {
			// The device will get reset/rebuilt by the rendering system
            hr = S_OK;
        }
    }
    return hr;
}



//-----------------------------------------------------------------------------
// private/protected methods
//-----------------------------------------------------------------------------


//-----------------------------------------------------------------------------
// CreateD3DSample
//
// Creates an sample object (IMFSample) to hold a Direct3D swap chain.
//-----------------------------------------------------------------------------

HRESULT D3DPresentEngine::CreateD3DSample(IDirect3DSurface9 *pSurface, IDirect3DDevice9* pDevice,
										  IMFSample **ppVideoSample)
{
    // Caller holds the object lock.

	HRESULT hr = S_OK;
    D3DCOLOR clrBlack = D3DCOLOR_ARGB(0xFF, 0x00, 0x00, 0x00);

    IMFSample* pSample = NULL;

    // Fill it with black.
	CHECK_HR(hr = pDevice->ColorFill(pSurface, NULL, clrBlack));

    // Create the sample.
    CHECK_HR(hr = MFCreateVideoSampleFromSurface(pSurface, &pSample));

    // Return the pointer to the caller.
	*ppVideoSample = pSample;
	(*ppVideoSample)->AddRef();

done:
    SAFE_RELEASE(pSample);
	return hr;
}
