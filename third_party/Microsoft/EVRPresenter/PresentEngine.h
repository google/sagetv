//////////////////////////////////////////////////////////////////////////
//
// PresentEngine.h: Defines the D3DPresentEngine object.
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

#pragma once

//-----------------------------------------------------------------------------
// D3DPresentEngine class
//
// This class creates the Direct3D device, allocates Direct3D surfaces for
// rendering, and presents the surfaces. This class also owns the Direct3D
// device manager and provides the IDirect3DDeviceManager9 interface via
// GetService.
//
// The goal of this class is to isolate the EVRCustomPresenter class from
// the details of Direct3D as much as possible.
//-----------------------------------------------------------------------------

class D3DPresentEngine : public SchedulerCallback
{
public:

    // State of the Direct3D device.
    enum DeviceState
    {
        DeviceOK,
        DeviceReset,    // The device was reset OR re-created.
        DeviceRemoved,  // The device was removed.
    };

    D3DPresentEngine(HRESULT& hr);
    virtual ~D3DPresentEngine();

    // GetService: Returns the IDirect3DDeviceManager9 interface.
    // (The signature is identical to IMFGetService::GetService but 
    // this object does not derive from IUnknown.)
    virtual HRESULT GetService(REFGUID guidService, REFIID riid, void** ppv);
    virtual HRESULT CheckFormat(D3DFORMAT format);

    // Video window / destination rectangle:
    // This object implements a sub-set of the functions defined by the 
    // IMFVideoDisplayControl interface. However, some of the method signatures 
    // are different. The presenter's implementation of IMFVideoDisplayControl 
    // calls these methods.
    HRESULT SetVideoWindow(HWND hwnd);
    HWND    GetVideoWindow() const { return m_hwnd; }
    HRESULT SetDestinationRect(const RECT& rcDest);
    RECT    GetDestinationRect() const { return m_rcDestRect; };
	HRESULT GetNativeVideoSize(SIZE* pszVideo, SIZE* pszARVideo) 
	{ 
		if (pszVideo)
		{
			pszVideo->cx = m_videoSize.cx;
			pszVideo->cy = m_videoSize.cy;
		}
		if (pszARVideo)
		{
			pszARVideo->cx = m_arSize.cx;
			pszARVideo->cy = m_arSize.cy;
		}
		return S_OK;
	}

    HRESULT CreateVideoSamples(IMFMediaType *pFormat, VideoSampleList& videoSampleQueue);
    void    ReleaseResources();

    HRESULT PresentSample(IMFSample* pSample, LONGLONG llTarget); 

    UINT    RefreshRate() const { return m_DisplayMode.RefreshRate; }

	HRESULT set_D3DDeviceMgr(IDirect3DDeviceManager9* pD3DDevMgr); 

protected:
	HRESULT CreateD3DSample(IDirect3DSurface9 *pSurface, IDirect3DDevice9* pDevice, IMFSample **ppVideoSample);

	// A derived class can override these handlers to allocate any additional D3D resources.
	virtual HRESULT OnCreateVideoSamples(D3DPRESENT_PARAMETERS& pp) { return S_OK; }
	virtual void	OnReleaseResources() { }


protected:

    HWND                        m_hwnd;                 // Application-provided destination window.
	RECT						m_rcDestRect;           // Destination rectangle.
    D3DDISPLAYMODE              m_DisplayMode;          // Adapter's display mode.

    CritSec                     m_ObjectLock;           // Thread lock for the D3D device.

    // COM interfaces
	HANDLE						m_hSharedDevice;
    IDirect3DDeviceManager9     *m_pDeviceManager;        // Direct3D device manager.
    IDirect3DSurface9           *m_pSurfaceRepaint;       // Surface for repaint requests.

	JavaVM* m_vmBuf;
	jclass m_dx9SageClass;
	jmethodID m_dx9UpdateMethodID;
	SIZE m_arSize;
	SIZE m_videoSize;

};