//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: Allocator.h
//
// Desc: DirectShow sample code - interface for the CAllocator class
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

#ifndef _VMR9ALLOCATOR_SAGE_
#define _VMR9ALLOCATOR_SAGE_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

#include <vmr9.h>
#include <Wxutil.h>

#pragma warning(push, 2)
#include <vector>
#pragma warning(pop)
using namespace std;

class CVMRAllocator  : public  IVMRSurfaceAllocator9, 
                            IVMRImagePresenter9,
							IVMRWindowlessControl9
{
public:
    CVMRAllocator(HRESULT& hr, IDirect3D9* d3d = NULL, IDirect3DDevice9* d3dd = NULL);
    virtual ~CVMRAllocator();

    // IVMRSurfaceAllocator9
    virtual HRESULT STDMETHODCALLTYPE InitializeDevice( 
            /* [in] */ DWORD_PTR dwUserID,
            /* [in] */ VMR9AllocationInfo *lpAllocInfo,
            /* [out][in] */ DWORD *lpNumBuffers);
            
    virtual HRESULT STDMETHODCALLTYPE TerminateDevice( 
        /* [in] */ DWORD_PTR dwID);
    
    virtual HRESULT STDMETHODCALLTYPE GetSurface( 
        /* [in] */ DWORD_PTR dwUserID,
        /* [in] */ DWORD SurfaceIndex,
        /* [in] */ DWORD SurfaceFlags,
        /* [out] */ IDirect3DSurface9 **lplpSurface);
    
    virtual HRESULT STDMETHODCALLTYPE AdviseNotify( 
        /* [in] */ IVMRSurfaceAllocatorNotify9 *lpIVMRSurfAllocNotify);

    // IVMRImagePresenter9
    virtual HRESULT STDMETHODCALLTYPE StartPresenting( 
        /* [in] */ DWORD_PTR dwUserID);
    
    virtual HRESULT STDMETHODCALLTYPE StopPresenting( 
        /* [in] */ DWORD_PTR dwUserID);
    
    virtual HRESULT STDMETHODCALLTYPE PresentImage( 
        /* [in] */ DWORD_PTR dwUserID,
        /* [in] */ VMR9PresentationInfo *lpPresInfo);
    
    // IUnknown
    virtual HRESULT STDMETHODCALLTYPE QueryInterface( 
        REFIID riid,
        void** ppvObject);

    virtual ULONG STDMETHODCALLTYPE AddRef();
    virtual ULONG STDMETHODCALLTYPE Release();

	// IVMRWindowlessControl
	virtual HRESULT STDMETHODCALLTYPE GetNativeVideoSize(LONG* lpWidth, LONG* lpHeight, LONG* lpARWidth, LONG* lpARHeight);
	virtual HRESULT STDMETHODCALLTYPE GetMinIdealVideoSize(LONG* lpWidth, LONG* lpHeight);
	virtual HRESULT STDMETHODCALLTYPE GetMaxIdealVideoSize(LONG* lpWidth, LONG* lpHeight);
	virtual HRESULT STDMETHODCALLTYPE SetVideoPosition(const LPRECT lpSRCRect, const LPRECT lpDSTRect);
    virtual HRESULT STDMETHODCALLTYPE GetVideoPosition(LPRECT lpSRCRect, LPRECT lpDSTRect);
	virtual HRESULT STDMETHODCALLTYPE GetAspectRatioMode(DWORD* lpAspectRatioMode);
	virtual HRESULT STDMETHODCALLTYPE SetAspectRatioMode(DWORD AspectRatioMode);
	virtual HRESULT STDMETHODCALLTYPE SetVideoClippingWindow(HWND hwnd);
	virtual HRESULT STDMETHODCALLTYPE RepaintVideo(HWND hwnd, HDC hdc);
	virtual HRESULT STDMETHODCALLTYPE DisplayModeChanged();
	virtual HRESULT STDMETHODCALLTYPE GetCurrentImage(BYTE** lpDib);
	virtual HRESULT STDMETHODCALLTYPE SetBorderColor(COLORREF Clr);
	virtual HRESULT STDMETHODCALLTYPE GetBorderColor(COLORREF* lpClr);
	virtual HRESULT STDMETHODCALLTYPE SetColorKey(COLORREF Clr);
	virtual HRESULT STDMETHODCALLTYPE GetColorKey(COLORREF* lpClr);

	void UpdateVideoPosition(RECT* pSrc, RECT *pDst)
	{
		if (!pSrc || !pDst) return;
		memcpy(&m_videoRectSrc, pSrc, sizeof(RECT));
		memcpy(&m_videoRectDst, pDst, sizeof(RECT));
	}

protected:
    // a helper function to erase every surface in the vector
    void DeleteSurfaces();

    // This function is here so we can catch the loss of surfaces.
    // All the functions are using the FAIL_RET macro so that they exit
    // with the last error code.  When this returns with the surface lost
    // error code we can restore the surfaces.
    HRESULT PresentHelper(VMR9PresentationInfo *lpPresInfo, DWORD_PTR dwUserID);

private:
    // needed to make this a thread safe object
    CCritSec    m_ObjectLock;
    long        m_refCount;

	DWORD_PTR m_lastUserID;

    IDirect3D9*                     m_D3D;
    IDirect3DDevice9*               m_D3DDev;
    IVMRSurfaceAllocatorNotify9*    m_lpIVMRSurfAllocNotify;
    IDirect3DSurface9*              m_surfaces[16];
	DWORD                           m_dwNumSurfaces;
    IDirect3DTexture9*              m_privateTexture;

	JavaVM* m_vmBuf;
	jclass m_dx9SageClass;
	jmethodID m_dx9UpdateMethodID;

	RECT m_videoRectSrc;
	RECT m_videoRectDst;
	SIZE m_arSize;
	SIZE m_videoSize;
};

#endif 
