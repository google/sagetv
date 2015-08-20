//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: Allocator.cpp
//
// Desc: DirectShow sample code - implementation of the CAllocator class
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

#include "stdafx.h"
#include <dshow.h>
#include <streams.h>
#include <d3d9.h>
#include "VMRAllocator.h"
#include "jni.h"
#include <d3dx9tex.h>
#include "jni-util.h"

#define FAIL_RET(x) do { if( FAILED( hr = ( x  ) ) ) \
    return hr; } while(0)


//////////////////////////////////////////////////////////////////////
// Construction/Destruction
//////////////////////////////////////////////////////////////////////

CVMRAllocator::CVMRAllocator(HRESULT& hr, IDirect3D9* d3d, IDirect3DDevice9* d3dd)
: m_refCount(1)
, m_D3D(d3d)
, m_D3DDev(d3dd)
, m_lpIVMRSurfAllocNotify(NULL)
, m_privateTexture(NULL)
{
    CAutoLock Lock(&m_ObjectLock);
    hr = S_OK;
	m_dwNumSurfaces = 0;
	ZeroMemory(m_surfaces, 16*sizeof(m_surfaces[0]));

	jsize numVMs;
	if (JNI_GetCreatedJavaVMs(&m_vmBuf, 1, &numVMs))
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
	m_arSize.cx = 720;
	m_arSize.cy = 540;
	m_videoSize.cx = 720;
	m_videoSize.cy = 540;
	m_videoRectSrc.top = 0;
	m_videoRectSrc.left = 0;
	m_videoRectSrc.right = 720;
	m_videoRectSrc.bottom = 480;
	m_videoRectDst.top = 0;
	m_videoRectDst.left = 0;
	m_videoRectDst.right = 720;
	m_videoRectDst.bottom = 480;
}

CVMRAllocator::~CVMRAllocator()
{
    DeleteSurfaces();
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

void CVMRAllocator::DeleteSurfaces()
{
    CAutoLock Lock(&m_ObjectLock);

	JNIEnv* env;
	jint threadState = m_vmBuf->GetEnv((void**)&env, JNI_VERSION_1_2);
	if (threadState == JNI_EDETACHED)
		m_vmBuf->AttachCurrentThread((void**)&env, NULL);
	// Indicate that this surface is no longer in use
	env->CallStaticVoidMethod(m_dx9SageClass, m_dx9UpdateMethodID, m_lastUserID, 0, 0, 0, 0, 0, 0);
	// Clear any pending exceptions, we can't handle them here
	env->ExceptionClear();
	//slog(("Deallocating %d VMR9 surfaces\r\n", m_dwNumSurfaces));
	if (threadState == JNI_EDETACHED)
		m_vmBuf->DetachCurrentThread();
	SAFE_RELEASE(m_privateTexture);

    for( size_t i = 0; i < m_dwNumSurfaces; i++ ) 
    {
		if (m_surfaces[i])
		{
			ULONG rCount = m_surfaces[i]->Release();
			if (rCount)
			{
//				slog(("VMR surface still has a ref count=%d\r\n", rCount));
			}
			m_surfaces[i] = NULL;
		}
    }
	m_dwNumSurfaces = 0;
}


//IVMRSurfaceAllocator9
HRESULT CVMRAllocator::InitializeDevice( 
            /* [in] */ DWORD_PTR dwUserID,
            /* [in] */ VMR9AllocationInfo *lpAllocInfo,
            /* [out][in] */ DWORD *lpNumBuffers)
{
    DWORD dwWidth = 1;
    DWORD dwHeight = 1;
    float fTU = 1.f;
    float fTV = 1.f;

    if( lpNumBuffers == NULL )
    {
        return E_POINTER;
    }

    if( m_lpIVMRSurfAllocNotify == NULL )
    {
        return E_FAIL;
    }

    HRESULT hr = S_OK;
	m_lastUserID = dwUserID;
    
    // NOTE:
    // we need to make sure that we create textures because
    // surfaces can not be textured onto a primitive.
    //lpAllocInfo->dwFlags |= VMR9AllocFlag_TextureSurface;
	//lpAllocInfo->dwFlags = VMR9AllocFlag_DXVATarget;

	// NOTE: JK when I tried offscreen surfaces, they failed because they weren't
	// render targets and the upstream decoder probably required a render target.

    DeleteSurfaces();
	m_dwNumSurfaces = *lpNumBuffers;
	char foobar[1024];
	sprintf(foobar, "VMR9 alloc %dx%d ", lpAllocInfo->dwWidth, lpAllocInfo->dwHeight);
	if (lpAllocInfo->dwFlags & VMR9AllocFlag_3DRenderTarget)
		strcat(foobar, "3DRenderTarget ");
	if (lpAllocInfo->dwFlags & VMR9AllocFlag_DXVATarget)
		strcat(foobar, "DXVATarget ");
	if (lpAllocInfo->dwFlags & VMR9AllocFlag_TextureSurface)
		strcat(foobar, "Texture ");
	if (lpAllocInfo->dwFlags & VMR9AllocFlag_OffscreenSurface)
		strcat(foobar, "Offscreen ");
	if (lpAllocInfo->Pool == D3DPOOL_DEFAULT)
		strcat(foobar, "DefaultPool ");
	if (lpAllocInfo->Pool == D3DPOOL_MANAGED)
		strcat(foobar, "ManagedPool ");
	if (lpAllocInfo->Pool == D3DPOOL_SYSTEMMEM)
		strcat(foobar, "SysMemPool ");
    HRESULT surfhr = m_lpIVMRSurfAllocNotify->AllocateSurfaceHelper(lpAllocInfo, lpNumBuffers, &m_surfaces[0]);
	slog(("%s format=%c%c%c%c minBuffs=%d arx=%d ary=%d nativeWidth=%d nativeHeight=%d reqNumBuff=%d allocNumBuff=%d hr=0x%x\r\n", 
		foobar, (lpAllocInfo->Format & 0xFF),
		(lpAllocInfo->Format & 0xFF00) >> 8, (lpAllocInfo->Format & 0xFF0000) >> 16, 
		(lpAllocInfo->Format & 0xFF000000) >> 24, lpAllocInfo->MinBuffers,
		lpAllocInfo->szAspectRatio.cx, lpAllocInfo->szAspectRatio.cy,
		lpAllocInfo->szNativeSize.cx, lpAllocInfo->szNativeSize.cy, m_dwNumSurfaces, *lpNumBuffers, surfhr/*, (lpAllocInfo->Format & 0xFF),
		(lpAllocInfo->Format & 0xFF00) >> 8, (lpAllocInfo->Format & 0xFF0000) >> 16, 
		(lpAllocInfo->Format & 0xFF000000) >> 24*/));
	/*if (lpAllocInfo->Format && FAILED(m_D3D->CheckDeviceFormatConversion(D3DADAPTER_DEFAULT, D3DDEVTYPE_HAL,
			lpAllocInfo->Format, D3DFMT_X8R8G8B8)))
	{
		slog(("Unsupported conversion for format\r\n"));
		return E_FAIL;
	}*/
/*	if (SUCCEEDED(hr))
	{
		D3DSURFACE_DESC surfDesc;
		m_surfaces[0]->GetDesc(&surfDesc);
		sprintf(foobar, "2-VMR9 alloc %dx%d ", surfDesc.Width, surfDesc.Height);
		if (surfDesc.Pool == D3DPOOL_DEFAULT)
			strcat(foobar, "DefaultPool ");
		if (surfDesc.Pool == D3DPOOL_MANAGED)
			strcat(foobar, "ManagedPool ");
		if (surfDesc.Pool == D3DPOOL_SYSTEMMEM)
			strcat(foobar, "SysMemPool ");
		slog(("%s format=%d %c%c%c%c\r\n", foobar, surfDesc.Format, (surfDesc.Format & 0xFF),
			(surfDesc.Format & 0xFF00) >> 8, (surfDesc.Format & 0xFF0000) >> 16, 
			(surfDesc.Format & 0xFF000000) >> 24));
	}
*///	if (FAILED(surfhr) /*|| ((lpAllocInfo->dwFlags & VMR9AllocFlag_TextureSurface) != VMR9AllocFlag_TextureSurface)*/)
/*	{
		// If we couldn't create a texture surface and 
		// the format is not an alpha format,
		// then we probably cannot create a texture.
		// So what we need to do is create a private texture
		// and copy the decoded images onto it.
		//if(FAILED(hr) && !(lpAllocInfo->dwFlags & VMR9AllocFlag_3DRenderTarget))
//		if (FAILED(surfhr) || ((lpAllocInfo->dwFlags & VMR9AllocFlag_TextureSurface) != VMR9AllocFlag_TextureSurface))
		{
//			if (FAILED(surfhr))
			{
//				DeleteSurfaces();            
			}
//			else
			{
//				SAFE_RELEASE(m_privateTexture);
			}

			// is surface YUV ?
			if (lpAllocInfo->Format > '0000') 
			{           
				D3DDISPLAYMODE dm; 
				hr = m_D3DDev->GetDisplayMode(NULL,  & dm );

				// create the private texture
				hr = m_D3DDev->CreateTexture(lpAllocInfo->dwWidth, lpAllocInfo->dwHeight,
										1, 
										D3DUSAGE_RENDERTARGET, 
										dm.Format, 
*///										D3DPOOL_DEFAULT /* default pool - usually video memory */, 
/*										&m_privateTexture, NULL );
				if (FAILED(hr))
				{
					slog(("VMR9 Failure1 allocating surface hr=0x%x\r\n", hr));
					return hr;
				}
			}

        
//			if (FAILED(surfhr))
			{
//				lpAllocInfo->dwFlags = VMR9AllocFlag_OffscreenSurface;

				hr = m_lpIVMRSurfAllocNotify->AllocateSurfaceHelper(lpAllocInfo, lpNumBuffers, &m_surfaces[0]);
				if (FAILED(hr))
				{
					slog(("VMR9 Failure2 allocating surface hr=0x%x\r\n", hr));
					return hr;
				}
			}
		}
	}
	if (SUCCEEDED(hr))
	{
		D3DSURFACE_DESC surfDesc;
		m_surfaces[0]->GetDesc(&surfDesc);
		sprintf(foobar, "2-VMR9 alloc %dx%d ", surfDesc.Width, surfDesc.Height);
		if (surfDesc.Pool == D3DPOOL_DEFAULT)
			strcat(foobar, "DefaultPool ");
		if (surfDesc.Pool == D3DPOOL_MANAGED)
			strcat(foobar, "ManagedPool ");
		if (surfDesc.Pool == D3DPOOL_SYSTEMMEM)
			strcat(foobar, "SysMemPool ");
		slog(("%s format=%d %c%c%c%c\r\n", foobar, surfDesc.Format, (surfDesc.Format & 0xFF),
			(surfDesc.Format & 0xFF00) >> 8, (surfDesc.Format & 0xFF0000) >> 16, 
			(surfDesc.Format & 0xFF000000) >> 24));
	}
*///	slog(("VMR9 Alloc hr=0x%x\r\n", hr));
    return hr;
}
            
HRESULT CVMRAllocator::TerminateDevice( 
        /* [in] */ DWORD_PTR dwID)
{
    DeleteSurfaces();
    return S_OK;
}
    
HRESULT CVMRAllocator::GetSurface( 
        /* [in] */ DWORD_PTR dwUserID,
        /* [in] */ DWORD SurfaceIndex,
        /* [in] */ DWORD SurfaceFlags,
        /* [out] */ IDirect3DSurface9 **lplpSurface)
{
    if( lplpSurface == NULL )
    {
        return E_POINTER;
    }

    if (SurfaceIndex >= m_dwNumSurfaces ) 
    {
        return E_FAIL;
    }

    CAutoLock Lock(&m_ObjectLock);

	if (!m_surfaces[SurfaceIndex])
		return E_FAIL;
	*lplpSurface = m_surfaces[SurfaceIndex];
	(*lplpSurface)->AddRef();
    return S_OK;
}
    
HRESULT CVMRAllocator::AdviseNotify( 
        /* [in] */ IVMRSurfaceAllocatorNotify9 *lpIVMRSurfAllocNotify)
{
    CAutoLock Lock(&m_ObjectLock);

    HRESULT hr;

    m_lpIVMRSurfAllocNotify = lpIVMRSurfAllocNotify;
	//m_lpIVMRSurfAllocNotify->AddRef();

    HMONITOR hMonitor = m_D3D->GetAdapterMonitor( D3DADAPTER_DEFAULT );
    hr = m_lpIVMRSurfAllocNotify->SetD3DDevice( m_D3DDev, hMonitor );
    return hr;
}

HRESULT CVMRAllocator::StartPresenting( 
    /* [in] */ DWORD_PTR dwUserID)
{
    CAutoLock Lock(&m_ObjectLock);

    ASSERT( m_D3DDev );
    if( m_D3DDev == NULL )
    {
        return E_FAIL;
    }

    return S_OK;
}

HRESULT CVMRAllocator::StopPresenting( 
    /* [in] */ DWORD_PTR dwUserID)
{
    return S_OK;
}

HRESULT CVMRAllocator::PresentImage( 
    /* [in] */ DWORD_PTR dwUserID,
    /* [in] */ VMR9PresentationInfo *lpPresInfo)
{
    HRESULT hr;
    CAutoLock Lock(&m_ObjectLock);

	// We've been deallocated already
	if (!m_lpIVMRSurfAllocNotify)
		return E_FAIL;

    hr = PresentHelper( lpPresInfo, dwUserID );

	// Device loss and recovery is handled at a higher level in the 3D rendering system

    return hr;
}

int fooCount = 0;
HRESULT CVMRAllocator::PresentHelper(VMR9PresentationInfo *lpPresInfo, DWORD_PTR dwUserID)
{
    // parameter validation
    if( lpPresInfo == NULL )
    {
        return E_POINTER;
    }
    else if( lpPresInfo->lpSurf == NULL )
    {
        return E_POINTER;
    }
	else if (m_surfaces[0] == NULL)
		return E_POINTER;

    CAutoLock Lock(&m_ObjectLock);
    HRESULT hr = S_OK;

// Get the Java VM, attach to it and send the event, then detach
	JNIEnv* env;
	if (m_vmBuf->AttachCurrentThread((void**)&env, NULL))
		return E_FAIL;
	if (m_privateTexture != NULL)
	{   
		// now, get the device of the sample passed in (it is not necessarily the same
		// device we created in the render engine
		CComPtr<IDirect3DDevice9>       pSampleDevice = NULL;
		hr = lpPresInfo->lpSurf->GetDevice(&pSampleDevice);
		HTESTPRINT(hr);
		if (FAILED(hr)) return hr;
		CComPtr<IDirect3DSurface9> surface;
		hr = m_privateTexture->GetSurfaceLevel(0, &surface);
		HTESTPRINT(hr);
		if (FAILED(hr)) return hr;
		// copy the full surface onto the texture's surface
		hr = pSampleDevice->StretchRect( lpPresInfo->lpSurf, NULL,
							 surface, NULL,
							 D3DTEXF_NONE );
		HTESTPRINT(hr);
		if (FAILED(hr)) return hr;
		D3DSURFACE_DESC vSurfDesc;
		surface->GetDesc(&vSurfDesc);
		m_arSize.cx = lpPresInfo->szAspectRatio.cx;
		m_arSize.cy = lpPresInfo->szAspectRatio.cy;
		m_videoSize.cx = vSurfDesc.Width;
		m_videoSize.cy = vSurfDesc.Height;
		env->CallStaticVoidMethod(m_dx9SageClass, m_dx9UpdateMethodID,
			(jint) dwUserID, (jlong)((IDirect3DSurface9*) surface), (jlong) m_privateTexture,
			(jlong) lpPresInfo->rtStart, (jlong) lpPresInfo->rtEnd,
			vSurfDesc.Width, vSurfDesc.Height, m_arSize.cx, m_arSize.cy);
	}
	else // this is the case where we have got the textures allocated by VMR
		 // all we need to do is to get them from the surface
	{
		D3DSURFACE_DESC vSurfDesc;
		lpPresInfo->lpSurf->GetDesc(&vSurfDesc);
		m_arSize.cx = lpPresInfo->szAspectRatio.cx;
		m_arSize.cy = lpPresInfo->szAspectRatio.cy;
		m_videoSize.cx = vSurfDesc.Width;
		m_videoSize.cy = vSurfDesc.Height;
		env->CallStaticVoidMethod(m_dx9SageClass, m_dx9UpdateMethodID,
			(jint) dwUserID, (jlong) lpPresInfo->lpSurf,
			(jlong) 0,//texture,
			(jlong) lpPresInfo->rtStart, (jlong) lpPresInfo->rtEnd,
			vSurfDesc.Width, vSurfDesc.Height, m_arSize.cx, m_arSize.cy);
	}

	env->ExceptionClear();
	m_vmBuf->DetachCurrentThread();

    return hr;
}

// IUnknown
HRESULT CVMRAllocator::QueryInterface( 
        REFIID riid,
        void** ppvObject)
{
    HRESULT hr = E_NOINTERFACE;
    if( ppvObject == NULL ) {
        hr = E_POINTER;
    } 
    else if( riid == IID_IVMRSurfaceAllocator9 ) {
        *ppvObject = static_cast<IVMRSurfaceAllocator9*>( this );
        AddRef();
        hr = S_OK;
    } 
    else if( riid == IID_IVMRImagePresenter9 ) {
        *ppvObject = static_cast<IVMRImagePresenter9*>( this );
        AddRef();
        hr = S_OK;
    } 
	else if ( riid == IID_IVMRWindowlessControl9 ) {
		*ppvObject = static_cast<IVMRWindowlessControl9*>( this );
		AddRef();
		hr = S_OK;
	}
    else if( riid == IID_IUnknown ) {
        *ppvObject = 
            static_cast<IUnknown*>( 
            static_cast<IVMRSurfaceAllocator9*>( this ) );
        AddRef();
        hr = S_OK;    
    }

    return hr;
}

ULONG CVMRAllocator::AddRef()
{
//slog(("VMR9 AP AddRef refCount=%d\r\n", m_refCount + 1));
	return InterlockedIncrement(& m_refCount);
}

ULONG CVMRAllocator::Release()
{
    ULONG ret = InterlockedDecrement(& m_refCount);
//slog(("VMR9 AP Release refCount=%d\r\n", m_refCount));
	if( ret == 0 )
    {
        delete this;
    }

    return ret;
}

//IVMRWindowlessControl	
HRESULT CVMRAllocator::GetNativeVideoSize(LONG* lpWidth, LONG* lpHeight, LONG* lpARWidth, LONG* lpARHeight)
{
	*lpWidth = m_videoSize.cx; *lpHeight = m_videoSize.cy;  *lpARWidth = m_arSize.cx; *lpARHeight = m_arSize.cy;
	return S_OK;
}
HRESULT CVMRAllocator::GetMinIdealVideoSize(LONG* lpWidth, LONG* lpHeight) { return VFW_E_WRONG_STATE; }
HRESULT CVMRAllocator::GetMaxIdealVideoSize(LONG* lpWidth, LONG* lpHeight) { return VFW_E_WRONG_STATE; }
HRESULT CVMRAllocator::SetVideoPosition(const LPRECT lpSRCRect, const LPRECT lpDSTRect) { return VFW_E_WRONG_STATE; }
HRESULT CVMRAllocator::GetVideoPosition(LPRECT lpSRCRect, LPRECT lpDSTRect)
{
	memcpy(lpSRCRect, &m_videoRectSrc, sizeof(RECT));
	memcpy(lpDSTRect, &m_videoRectDst, sizeof(RECT));
	return S_OK;
}
HRESULT CVMRAllocator::GetAspectRatioMode(DWORD* lpAspectRatioMode)
{ 
	*lpAspectRatioMode = VMR9ARMode_None; return S_OK; 
}
HRESULT CVMRAllocator::SetAspectRatioMode(DWORD AspectRatioMode) { return VFW_E_WRONG_STATE; }
HRESULT CVMRAllocator::SetVideoClippingWindow(HWND hwnd) { return VFW_E_WRONG_STATE; }
HRESULT CVMRAllocator::RepaintVideo(HWND hwnd, HDC hdc) { return VFW_E_WRONG_STATE; }
HRESULT CVMRAllocator::DisplayModeChanged() { return S_OK; }
HRESULT CVMRAllocator::GetCurrentImage(BYTE** lpDib) { return VFW_E_WRONG_STATE; }
HRESULT CVMRAllocator::SetBorderColor(COLORREF Clr)  { return VFW_E_WRONG_STATE; }
HRESULT CVMRAllocator::GetBorderColor(COLORREF* lpClr) { return VFW_E_WRONG_STATE; }
HRESULT CVMRAllocator::SetColorKey(COLORREF Clr) { return VFW_E_WRONG_STATE; }
HRESULT CVMRAllocator::GetColorKey(COLORREF* lpClr) { return VFW_E_WRONG_STATE; }

