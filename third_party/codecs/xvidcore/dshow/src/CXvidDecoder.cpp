/*****************************************************************************
 *
 *  XVID MPEG-4 VIDEO CODEC
 *  - Xvid Decoder part of the DShow Filter  -
 *
 *  Copyright(C) 2002-2011 Peter Ross <pross@xvid.org>
 *               2003-2012 Michael Militzer <michael@xvid.org>
 *
 *  This program is free software ; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation ; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY ; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program ; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
 *
 * $Id: CXvidDecoder.cpp 2059 2012-02-22 19:00:26Z Isibaar $
 *
 ****************************************************************************/

 /* 
	this requires the directx sdk
	place these paths at the top of the Tools|Options|Directories list

	headers:
	C:\DX90SDK\Include
	C:\DX90SDK\Samples\C++\DirectShow\BaseClasses
	
	C:\DX90SDK\Samples\C++\DirectShow\BaseClasses\Release
	C:\DX90SDK\Samples\C++\DirectShow\BaseClasses\Debug
*/

#ifdef ENABLE_MFT
#define XVID_USE_MFT
#endif

#include <windows.h>

#include <streams.h>
#include <initguid.h>
#include <olectl.h>
#if (1100 > _MSC_VER)
#include <olectlid.h>
#endif
#include <dvdmedia.h>	// VIDEOINFOHEADER2

#if defined(XVID_USE_MFT)
#define MFT_UNIQUE_METHOD_NAMES
#include <mftransform.h>
#include <mfapi.h>
#include <mferror.h>
#include <shlwapi.h>
#endif

#include <shellapi.h>

#include <xvid.h>		// Xvid API

#include "resource.h"

#include "IXvidDecoder.h"
#include "CXvidDecoder.h"
#include "CAbout.h"
#include "config.h"
#include "debug.h"

static bool USE_IYUV;
static bool USE_YV12;
static bool USE_YUY2;
static bool USE_YVYU;
static bool USE_UYVY;
static bool USE_RGB32;
static bool USE_RGB24;
static bool USE_RG555;
static bool USE_RG565;

const AMOVIESETUP_MEDIATYPE sudInputPinTypes[] =
{
    { &MEDIATYPE_Video, &CLSID_XVID },
	{ &MEDIATYPE_Video, &CLSID_XVID_UC },
	{ &MEDIATYPE_Video, &CLSID_DIVX },
	{ &MEDIATYPE_Video, &CLSID_DIVX_UC },
	{ &MEDIATYPE_Video, &CLSID_DX50 },
	{ &MEDIATYPE_Video, &CLSID_DX50_UC },
	{ &MEDIATYPE_Video, &CLSID_3IVX },
	{ &MEDIATYPE_Video, &CLSID_3IVX_UC },
	{ &MEDIATYPE_Video, &CLSID_3IV0 },
	{ &MEDIATYPE_Video, &CLSID_3IV0_UC },
	{ &MEDIATYPE_Video, &CLSID_3IV1 },
	{ &MEDIATYPE_Video, &CLSID_3IV1_UC },
	{ &MEDIATYPE_Video, &CLSID_3IV2 },
	{ &MEDIATYPE_Video, &CLSID_3IV2_UC },
	{ &MEDIATYPE_Video, &CLSID_LMP4 },
	{ &MEDIATYPE_Video, &CLSID_LMP4_UC },
	{ &MEDIATYPE_Video, &CLSID_RMP4 },
	{ &MEDIATYPE_Video, &CLSID_RMP4_UC },
	{ &MEDIATYPE_Video, &CLSID_SMP4 },
	{ &MEDIATYPE_Video, &CLSID_SMP4_UC },
	{ &MEDIATYPE_Video, &CLSID_HDX4 },
	{ &MEDIATYPE_Video, &CLSID_HDX4_UC },
	{ &MEDIATYPE_Video, &CLSID_MP4V },
  { &MEDIATYPE_Video, &CLSID_MP4V_UC },
};

const AMOVIESETUP_MEDIATYPE sudOutputPinTypes[] =
{
    { &MEDIATYPE_Video, &MEDIASUBTYPE_NULL }
};


const AMOVIESETUP_PIN psudPins[] =
{
	{
		L"Input",           // String pin name
		FALSE,              // Is it rendered
		FALSE,              // Is it an output
		FALSE,              // Allowed none
		FALSE,              // Allowed many
		&CLSID_NULL,        // Connects to filter
		L"Output",          // Connects to pin
		sizeof(sudInputPinTypes) / sizeof(AMOVIESETUP_MEDIATYPE), // Number of types
		&sudInputPinTypes[0]	// The pin details
	},
	{ 
		L"Output",          // String pin name
		FALSE,              // Is it rendered
		TRUE,               // Is it an output
		FALSE,              // Allowed none
		FALSE,              // Allowed many
		&CLSID_NULL,        // Connects to filter
		L"Input",           // Connects to pin
		sizeof(sudOutputPinTypes) / sizeof(AMOVIESETUP_MEDIATYPE),	// Number of types
		sudOutputPinTypes	// The pin details
	}
};


const AMOVIESETUP_FILTER sudXvidDecoder =
{
	&CLSID_XVID,			// Filter CLSID
	XVID_NAME_L,			// Filter name
	MERIT_PREFERRED+2,		// Its merit
	sizeof(psudPins) / sizeof(AMOVIESETUP_PIN),	// Number of pins
	psudPins				// Pin details
};


// List of class IDs and creator functions for the class factory. This
// provides the link between the OLE entry point in the DLL and an object
// being created. The class factory will call the static CreateInstance

CFactoryTemplate g_Templates[] =
{
	{ 
		XVID_NAME_L,
		&CLSID_XVID,
		CXvidDecoder::CreateInstance,
		NULL,
		&sudXvidDecoder
	},
	{
		XVID_NAME_L L"About",
		&CLSID_CABOUT,
		CAbout::CreateInstance
	}

};

/* note: g_cTemplates must be global; used by strmbase.lib(dllentry.cpp,dllsetup.cpp) */
int g_cTemplates = sizeof(g_Templates) / sizeof(CFactoryTemplate);

extern HINSTANCE g_xvid_hInst;

static int GUI_Page = 0;
static int Tray_Icon = 0;
extern "C" void CALLBACK Configure(HWND hWndParent, HINSTANCE hInstParent, LPSTR lpCmdLine, int nCmdShow );

LRESULT CALLBACK msg_proc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	switch ( uMsg )
	{
	case WM_ICONMESSAGE:
		switch(lParam)
		{
		case WM_LBUTTONDBLCLK:
			if (!GUI_Page) {
				GUI_Page = 1;
				Configure(hwnd, g_xvid_hInst, "", 1);
				GUI_Page = 0;
			}
			break;
		default:
			return DefWindowProc(hwnd, uMsg, wParam, lParam);
		};
		break;

	case WM_DESTROY:
		NOTIFYICONDATA nid;
		ZeroMemory(&nid,sizeof(NOTIFYICONDATA));

		nid.cbSize = NOTIFYICONDATA_V1_SIZE;
		nid.hWnd = hwnd;
		nid.uID = 1456;
	
		Shell_NotifyIcon(NIM_DELETE, &nid);
		Tray_Icon = 0;
	default:
		return DefWindowProc(hwnd, uMsg, wParam, lParam);
	}
	
	return TRUE; /* ok */
}

STDAPI DllRegisterServer()
{
#if defined(XVID_USE_MFT)
	int inputs_num = sizeof(sudInputPinTypes) / sizeof(AMOVIESETUP_MEDIATYPE);
	int outputs_num = sizeof(sudOutputPinTypes) / sizeof(AMOVIESETUP_MEDIATYPE);
	MFT_REGISTER_TYPE_INFO * mft_bs = new MFT_REGISTER_TYPE_INFO[inputs_num];
	MFT_REGISTER_TYPE_INFO * mft_csp = new MFT_REGISTER_TYPE_INFO[outputs_num];
	
	{
		int i;
		for(i=0;i<inputs_num;i++) {
			mft_bs[i].guidMajorType = *sudInputPinTypes[i].clsMajorType;
			mft_bs[i].guidSubtype = *sudInputPinTypes[i].clsMinorType;
		}
		for(i=0;i<outputs_num;i++) {
			mft_csp[i].guidMajorType = *sudOutputPinTypes[i].clsMajorType;
			mft_csp[i].guidSubtype = *sudOutputPinTypes[i].clsMinorType; // MFT and AM GUIDs really the same?
		}
	}
	
	/* Register the MFT decoder */
	MFTRegister(CLSID_XVID,                          // CLSID
		        MFT_CATEGORY_VIDEO_DECODER,          // Category
		        const_cast<LPWSTR>(XVID_NAME_L),     // Friendly name
		        0,                                   // Flags
		        inputs_num,                          // Number of input types
		        mft_bs,                              // Input types
		        outputs_num,                         // Number of output types
		        mft_csp,                             // Output types
		        NULL                                 // Attributes (optional)
		        );

	delete[] mft_bs;
	delete[] mft_csp;
#endif /* XVID_USE_MFT */

    return AMovieDllRegisterServer2( TRUE );
}


STDAPI DllUnregisterServer()
{
#if defined(XVID_USE_MFT)
	MFTUnregister(CLSID_XVID);
#endif
    return AMovieDllRegisterServer2( FALSE );
}


/* create instance */

CUnknown * WINAPI CXvidDecoder::CreateInstance(LPUNKNOWN punk, HRESULT *phr)
{
    CXvidDecoder * pNewObject = new CXvidDecoder(punk, phr);
    if (pNewObject == NULL)
	{
        *phr = E_OUTOFMEMORY;
    }
    return pNewObject;
}


/* query interfaces */

STDMETHODIMP CXvidDecoder::NonDelegatingQueryInterface(REFIID riid, void **ppv)
{
	CheckPointer(ppv, E_POINTER);

	if (riid == IID_IXvidDecoder)
	{
		return GetInterface((IXvidDecoder *) this, ppv);
	} 
	
	if (riid == IID_ISpecifyPropertyPages)
	{
        return GetInterface((ISpecifyPropertyPages *) this, ppv); 
	} 

#if defined(XVID_USE_MFT)
	if (riid == IID_IMFTransform)
	{
		return GetInterface((IMFTransform *) this, ppv);
	}
#endif

	return CVideoTransformFilter::NonDelegatingQueryInterface(riid, ppv);
}



/* constructor */

CXvidDecoder::CXvidDecoder(LPUNKNOWN punk, HRESULT *phr) :
    CVideoTransformFilter(NAME("CXvidDecoder"), punk, CLSID_XVID), m_hdll (NULL)
{
	DPRINTF("Constructor");

    xvid_decore_func = NULL; // Hmm, some strange errors appearing if I try to initialize...
    xvid_global_func = NULL; // ...this in constructor's init-list. So, they assigned here.

#if defined(XVID_USE_MFT)
	InitializeCriticalSection(&m_mft_lock);
	m_pInputType = NULL;
	m_pOutputType = NULL;
	m_rtFrame = 0;
	m_duration = 0;
	m_discont = 0;
	m_frameRate.Denominator = 1;
	m_frameRate.Numerator = 1;
#endif

    LoadRegistryInfo();

    *phr = OpenLib();
	
	{
		TCHAR lpFilename[MAX_PATH];
		int sLen = GetModuleFileName(NULL, lpFilename, MAX_PATH);
#ifdef _UNICODE
		if ((sLen >= 11) && (_wcsnicmp(&(lpFilename[sLen - 11]), TEXT("dllhost.exe"), 11) == 0)) {
#else
		if ((sLen >= 11) && (_strnicmp(&(lpFilename[sLen - 11]), TEXT("dllhost.exe"), 11) == 0)) {
#endif
			if (Tray_Icon == 0) Tray_Icon = -1; // create no tray icon upon thumbnail generation
		}
		else
			if (Tray_Icon == -1) Tray_Icon = 0; // can show tray icon
	}

}

HRESULT CXvidDecoder::OpenLib()
{
    DPRINTF("OpenLib");

    if (m_hdll != NULL)
		return E_UNEXPECTED; // Seems, that library already opened.

	xvid_gbl_init_t init;
	memset(&init, 0, sizeof(init));
	init.version = XVID_VERSION;
	init.cpu_flags = g_config.cpu;

	xvid_gbl_info_t info;
	memset(&info, 0, sizeof(info));
	info.version = XVID_VERSION;

	m_hdll = LoadLibrary(XVID_DLL_NAME);
	if (m_hdll == NULL) {
		DPRINTF("dll load failed");
		MessageBox(0, XVID_DLL_NAME " not found","Error", MB_TOPMOST);
		return E_FAIL;
	}

	xvid_global_func = (int (__cdecl *)(void *, int, void *, void *))GetProcAddress(m_hdll, "xvid_global");
	if (xvid_global_func == NULL) {
        FreeLibrary(m_hdll);
        m_hdll = NULL;
		MessageBox(0, "xvid_global() not found", "Error", MB_TOPMOST);
		return E_FAIL;
	}

	xvid_decore_func = (int (__cdecl *)(void *, int, void *, void *))GetProcAddress(m_hdll, "xvid_decore");
	if (xvid_decore_func == NULL) {
        xvid_global_func = NULL;
        FreeLibrary(m_hdll);
        m_hdll = NULL;
		MessageBox(0, "xvid_decore() not found", "Error", MB_TOPMOST);
		return E_FAIL;
	}

	if (xvid_global_func(0, XVID_GBL_INIT, &init, NULL) < 0)
	{
        xvid_global_func = NULL;
        xvid_decore_func = NULL;
        FreeLibrary(m_hdll);
        m_hdll = NULL;
		MessageBox(0, "xvid_global() failed", "Error", MB_TOPMOST);
		return E_FAIL;
	}

	if (xvid_global_func(0, XVID_GBL_INFO, &info, NULL) < 0)
	{
        xvid_global_func = NULL;
        xvid_decore_func = NULL;
        FreeLibrary(m_hdll);
        m_hdll = NULL;
		MessageBox(0, "xvid_global() failed", "Error", MB_TOPMOST);
		return E_FAIL;
	}

	memset(&m_create, 0, sizeof(m_create));
	m_create.version = XVID_VERSION;
	m_create.handle = NULL;
    /* Decoder threads */
    if (g_config.cpu & XVID_CPU_FORCE) {
		m_create.num_threads = g_config.num_threads;
	}
	else {
        m_create.num_threads = info.num_threads; /* Autodetect */
		g_config.num_threads = info.num_threads;
	}

	memset(&m_frame, 0, sizeof(m_frame));
	m_frame.version = XVID_VERSION;

	USE_IYUV = false;
	USE_YV12 = false;
	USE_YUY2 = false;
	USE_YVYU = false;
	USE_UYVY = false;
	USE_RGB32 = false;
	USE_RGB24 = false;
	USE_RG555 = false;
	USE_RG565 = false;

	switch ( g_config.nForceColorspace )
	{
	case FORCE_NONE:
		USE_IYUV = true;
		USE_YV12 = true;
		USE_YUY2 = true;
		USE_YVYU = true;
		USE_UYVY = true;
		USE_RGB32 = true;
		USE_RGB24 = true;
		USE_RG555 = true;
		USE_RG565 = true;
		break;
	case FORCE_YV12:
		USE_IYUV = true;
		USE_YV12 = true;
		break;
	case FORCE_YUY2:
		USE_YUY2 = true;
		break;
	case FORCE_RGB24:
		USE_RGB24 = true;
		break;
	case FORCE_RGB32:
		USE_RGB32 = true;
		break;
	}

	switch (g_config.aspect_ratio)
	{
	case 0:
	case 1:
		break;
	case 2:
		ar_x = 4;
		ar_y = 3;
		break;
	case 3:
		ar_x = 16;
		ar_y = 9;
		break;
	case 4:
		ar_x = 47;
		ar_y = 20;
		break;
	}
	
	return S_OK;
}

void CXvidDecoder::CloseLib()
{
	DPRINTF("CloseLib");

	if ((m_create.handle != NULL) && (xvid_decore_func != NULL))
	{
		xvid_decore_func(m_create.handle, XVID_DEC_DESTROY, 0, 0);
		m_create.handle = NULL;
	}

	if (m_hdll != NULL) {
		FreeLibrary(m_hdll);
		m_hdll = NULL;
	}
    xvid_decore_func = NULL;
    xvid_global_func = NULL;
}

/* destructor */

CXvidDecoder::~CXvidDecoder()
{
    DPRINTF("Destructor");

	if (Tray_Icon > 0) { /* Destroy tray icon */
		NOTIFYICONDATA nid;
		ZeroMemory(&nid,sizeof(NOTIFYICONDATA));

		nid.cbSize = NOTIFYICONDATA_V1_SIZE;
		nid.hWnd = MSG_hwnd;
		nid.uID = 1456;
	
		Shell_NotifyIcon(NIM_DELETE, &nid);
		Tray_Icon = 0;
	}

	/* Close xvidcore library */
	CloseLib();

#if defined(XVID_USE_MFT)
	DeleteCriticalSection(&m_mft_lock);
#endif
}



/* check input type */

HRESULT CXvidDecoder::CheckInputType(const CMediaType * mtIn)
{
	DPRINTF("CheckInputType");
	BITMAPINFOHEADER * hdr;

	ar_x = ar_y = 0;
	
	if (*mtIn->Type() != MEDIATYPE_Video)
	{
		DPRINTF("Error: Unknown Type");
		CloseLib();
		return VFW_E_TYPE_NOT_ACCEPTED;
	}

    if (m_hdll == NULL)
    {
		HRESULT hr = OpenLib();

        if (FAILED(hr) || (m_hdll == NULL)) // Paranoid checks.
			return VFW_E_TYPE_NOT_ACCEPTED;
    }

	if (*mtIn->FormatType() == FORMAT_VideoInfo)
	{
		VIDEOINFOHEADER * vih = (VIDEOINFOHEADER *) mtIn->Format();
		hdr = &vih->bmiHeader;
	}
	else if (*mtIn->FormatType() == FORMAT_VideoInfo2)
	{
		VIDEOINFOHEADER2 * vih2 = (VIDEOINFOHEADER2 *) mtIn->Format();
		hdr = &vih2->bmiHeader;
		if (g_config.aspect_ratio == 0 || g_config.aspect_ratio == 1) {
			ar_x = vih2->dwPictAspectRatioX;
			ar_y = vih2->dwPictAspectRatioY;
		}
		DPRINTF("VIDEOINFOHEADER2 AR: %d:%d", ar_x, ar_y);
	}
  else if (*mtIn->FormatType() == FORMAT_MPEG2Video) {
    MPEG2VIDEOINFO * mpgvi = (MPEG2VIDEOINFO*)mtIn->Format();
    VIDEOINFOHEADER2 * vih2 = &mpgvi->hdr;
		hdr = &vih2->bmiHeader;
		if (g_config.aspect_ratio == 0 || g_config.aspect_ratio == 1) {
			ar_x = vih2->dwPictAspectRatioX;
			ar_y = vih2->dwPictAspectRatioY;
		}
		DPRINTF("VIDEOINFOHEADER2 AR: %d:%d", ar_x, ar_y);

    /* haali media splitter reports VOL information in the format header */

    if (mpgvi->cbSequenceHeader>0) {

      xvid_dec_stats_t stats;
	    memset(&stats, 0, sizeof(stats));
	    stats.version = XVID_VERSION;

	    if (m_create.handle == NULL) {
		    if (xvid_decore_func == NULL)
			    return E_FAIL;
		    if (xvid_decore_func(0, XVID_DEC_CREATE, &m_create, 0) < 0) {
          DPRINTF("*** XVID_DEC_CREATE error");
			    return E_FAIL;
		    }
	    }

      m_frame.general = 0;
      m_frame.bitstream = (void*)mpgvi->dwSequenceHeader;
      m_frame.length = mpgvi->cbSequenceHeader;
      m_frame.output.csp = XVID_CSP_NULL;

      int ret = 0;
      if ((ret=xvid_decore_func(m_create.handle, XVID_DEC_DECODE, &m_frame, &stats)) >= 0) {
        /* honour video dimensions reported in VOL header */
	      if (stats.type == XVID_TYPE_VOL) {
          hdr->biWidth = stats.data.vol.width;
          hdr->biHeight = stats.data.vol.height;
		  }
	  }
      if (ret == XVID_ERR_MEMORY) return E_FAIL;
	}
  }
  else
  {
		DPRINTF("Error: Unknown FormatType");
		CloseLib();
		return VFW_E_TYPE_NOT_ACCEPTED;
  }
  if (hdr->biHeight < 0)
  {
	  DPRINTF("colorspace: inverted input format not supported");
  }
  
  m_create.width = hdr->biWidth;
  m_create.height = hdr->biHeight;
  
  switch(hdr->biCompression)
  {
	case FOURCC_mp4v :
	case FOURCC_MP4V :
	case FOURCC_lmp4 :
	case FOURCC_LMP4 :
	case FOURCC_rmp4 :
	case FOURCC_RMP4 :
	case FOURCC_smp4 :
	case FOURCC_SMP4 :
	case FOURCC_hdx4 :
	case FOURCC_HDX4 :
		if (!(g_config.supported_4cc & SUPPORT_MP4V)) {
			CloseLib();
			return VFW_E_TYPE_NOT_ACCEPTED;
		}
		break;	
	case FOURCC_divx :
	case FOURCC_DIVX :
	case FOURCC_dx50 :
	case FOURCC_DX50 :
		if (!(g_config.supported_4cc & SUPPORT_DIVX)) {
			CloseLib();
			return VFW_E_TYPE_NOT_ACCEPTED;
		}
		break;
	case FOURCC_3ivx :
	case FOURCC_3IVX :
	case FOURCC_3iv0 :
	case FOURCC_3IV0 :
	case FOURCC_3iv1 :
	case FOURCC_3IV1 :
	case FOURCC_3iv2 :
	case FOURCC_3IV2 :
		if (!(g_config.supported_4cc & SUPPORT_3IVX)) {
			CloseLib();
			return VFW_E_TYPE_NOT_ACCEPTED;
		}
	case FOURCC_xvid :
	case FOURCC_XVID :
		break;
		

	default :
		DPRINTF("Unknown fourcc: 0x%08x (%c%c%c%c)",
			hdr->biCompression,
			(hdr->biCompression)&0xff,
			(hdr->biCompression>>8)&0xff,
			(hdr->biCompression>>16)&0xff,
			(hdr->biCompression>>24)&0xff);
		CloseLib();
		return VFW_E_TYPE_NOT_ACCEPTED;
	}
	
	m_create.fourcc = hdr->biCompression;

	return S_OK;
}


/* get list of supported output colorspaces */


HRESULT CXvidDecoder::GetMediaType(int iPosition, CMediaType *mtOut)
{
	BITMAPINFOHEADER * bmih;
	DPRINTF("GetMediaType");

	if (m_pInput->IsConnected() == FALSE)
	{
		return E_UNEXPECTED;
	}

	if (!g_config.videoinfo_compat) {
		VIDEOINFOHEADER2 * vih = (VIDEOINFOHEADER2 *) mtOut->ReallocFormatBuffer(sizeof(VIDEOINFOHEADER2));
		if (vih == NULL) return E_OUTOFMEMORY;

		ZeroMemory(vih, sizeof (VIDEOINFOHEADER2));
		bmih = &(vih->bmiHeader);
		mtOut->SetFormatType(&FORMAT_VideoInfo2);

		if (ar_x != 0 && ar_y != 0) {
			vih->dwPictAspectRatioX = ar_x;
			vih->dwPictAspectRatioY = ar_y;
			forced_ar = true;
		} else { // just to be safe
			vih->dwPictAspectRatioX = m_create.width;
			vih->dwPictAspectRatioY = abs(m_create.height);
			forced_ar = false;
		} 
	} else {

		VIDEOINFOHEADER * vih = (VIDEOINFOHEADER *) mtOut->ReallocFormatBuffer(sizeof(VIDEOINFOHEADER));
		if (vih == NULL) return E_OUTOFMEMORY;

		ZeroMemory(vih, sizeof (VIDEOINFOHEADER));
		bmih = &(vih->bmiHeader);
		mtOut->SetFormatType(&FORMAT_VideoInfo);
	}

	bmih->biSize = sizeof(BITMAPINFOHEADER);
	bmih->biWidth	= m_create.width;
	bmih->biHeight = m_create.height;
	bmih->biPlanes = 1;

	if (iPosition < 0) return E_INVALIDARG;

	switch(iPosition)
	{

	case 0:
if ( USE_YUY2 )
{
		bmih->biCompression = MEDIASUBTYPE_YUY2.Data1;
		bmih->biBitCount = 16;
		mtOut->SetSubtype(&MEDIASUBTYPE_YUY2);
		break;
}
	case 1 :
if ( USE_YVYU )
{
		bmih->biCompression = MEDIASUBTYPE_YVYU.Data1;
		bmih->biBitCount = 16;
		mtOut->SetSubtype(&MEDIASUBTYPE_YVYU);
		break;
}
	case 2 :
if ( USE_UYVY )
{
		bmih->biCompression = MEDIASUBTYPE_UYVY.Data1;
		bmih->biBitCount = 16;
		mtOut->SetSubtype(&MEDIASUBTYPE_UYVY);
		break;
}
	case 3	:
		if ( USE_IYUV )
{
		bmih->biCompression = CLSID_MEDIASUBTYPE_IYUV.Data1;
		bmih->biBitCount = 12;
		mtOut->SetSubtype(&CLSID_MEDIASUBTYPE_IYUV);
		break;
}
	case 4	:
if ( USE_YV12 )
{
		bmih->biCompression = MEDIASUBTYPE_YV12.Data1;
		bmih->biBitCount = 12;
		mtOut->SetSubtype(&MEDIASUBTYPE_YV12);
		break;
}
	case 5 :
if ( USE_RGB32 )
{
		bmih->biCompression = BI_RGB;
		bmih->biBitCount = 32;
		mtOut->SetSubtype(&MEDIASUBTYPE_RGB32);
		break;
}
	case 6 :
if ( USE_RGB24 )
{
		bmih->biCompression = BI_RGB;
		bmih->biBitCount = 24;	
		mtOut->SetSubtype(&MEDIASUBTYPE_RGB24);
		break;
}
	case 7 :
if ( USE_RG555 )
{
		bmih->biCompression = BI_RGB;
		bmih->biBitCount = 16;	
		mtOut->SetSubtype(&MEDIASUBTYPE_RGB555);
		break;
}
	case 8 :
if ( USE_RG565 )
{
		bmih->biCompression = BI_RGB;
		bmih->biBitCount = 16;	
		mtOut->SetSubtype(&MEDIASUBTYPE_RGB565);
		break;
}	
	default :
		return VFW_S_NO_MORE_ITEMS;
	}

	bmih->biSizeImage = GetBitmapSize(bmih);

	mtOut->SetType(&MEDIATYPE_Video);
	mtOut->SetTemporalCompression(FALSE);
	mtOut->SetSampleSize(bmih->biSizeImage);

	return S_OK;
}


/* (internal function) change colorspace */
#define CALC_BI_STRIDE(width,bitcount)  ((((width * bitcount) + 31) & ~31) >> 3)

HRESULT CXvidDecoder::ChangeColorspace(GUID subtype, GUID formattype, void * format, int noflip)
{
	DWORD biWidth;

	if (formattype == FORMAT_VideoInfo)
	{
		VIDEOINFOHEADER * vih = (VIDEOINFOHEADER * )format;
		biWidth = vih->bmiHeader.biWidth;
		out_stride = CALC_BI_STRIDE(vih->bmiHeader.biWidth, vih->bmiHeader.biBitCount);
		rgb_flip = (vih->bmiHeader.biHeight < 0 ? 0 : XVID_CSP_VFLIP);
	}
	else if (formattype == FORMAT_VideoInfo2)
	{
		VIDEOINFOHEADER2 * vih2 = (VIDEOINFOHEADER2 * )format;
		biWidth = vih2->bmiHeader.biWidth;
		out_stride = CALC_BI_STRIDE(vih2->bmiHeader.biWidth, vih2->bmiHeader.biBitCount);
		rgb_flip = (vih2->bmiHeader.biHeight < 0 ? 0 : XVID_CSP_VFLIP);
	}
	else
	{
		return S_FALSE;
	}

	if (noflip) rgb_flip = 0;

	if (subtype == CLSID_MEDIASUBTYPE_IYUV)
	{
		DPRINTF("IYUV");
		rgb_flip = 0;
		m_frame.output.csp = XVID_CSP_I420;
		out_stride = CALC_BI_STRIDE(biWidth, 8);	/* planar format fix */
	}
	else if (subtype == MEDIASUBTYPE_YV12)
	{
		DPRINTF("YV12");
		rgb_flip = 0;
		m_frame.output.csp = XVID_CSP_YV12;
		out_stride = CALC_BI_STRIDE(biWidth, 8);	/* planar format fix */
	}
	else if (subtype == MEDIASUBTYPE_YUY2)
	{
		DPRINTF("YUY2");
		rgb_flip = 0;
		m_frame.output.csp = XVID_CSP_YUY2;
	}
	else if (subtype == MEDIASUBTYPE_YVYU)
	{
		DPRINTF("YVYU");
		rgb_flip = 0;
		m_frame.output.csp = XVID_CSP_YVYU;
	}
	else if (subtype == MEDIASUBTYPE_UYVY)
	{
		DPRINTF("UYVY");
		rgb_flip = 0;
		m_frame.output.csp = XVID_CSP_UYVY;
	}
	else if (subtype == MEDIASUBTYPE_RGB32)
	{
		DPRINTF("RGB32");
		m_frame.output.csp = rgb_flip | XVID_CSP_BGRA;
	}
	else if (subtype == MEDIASUBTYPE_RGB24)
	{
		DPRINTF("RGB24");
		m_frame.output.csp = rgb_flip | XVID_CSP_BGR;
	}
	else if (subtype == MEDIASUBTYPE_RGB555)
	{
		DPRINTF("RGB555");
		m_frame.output.csp = rgb_flip | XVID_CSP_RGB555;
	}
	else if (subtype == MEDIASUBTYPE_RGB565)
	{
		DPRINTF("RGB565");
		m_frame.output.csp = rgb_flip | XVID_CSP_RGB565;
	}
	else if (subtype == GUID_NULL)
	{
		m_frame.output.csp = XVID_CSP_NULL;
	}
	else
	{
		return S_FALSE;
	}

	return S_OK;
}


/* set output colorspace */

HRESULT CXvidDecoder::SetMediaType(PIN_DIRECTION direction, const CMediaType *pmt)
{
	DPRINTF("SetMediaType");
	
	if (direction == PINDIR_OUTPUT)
	{
		return ChangeColorspace(*pmt->Subtype(), *pmt->FormatType(), pmt->Format(), 0);
	}
	
	return S_OK;
}


/* check input<->output compatiblity */

HRESULT CXvidDecoder::CheckTransform(const CMediaType *mtIn, const CMediaType *mtOut)
{
	DPRINTF("CheckTransform");

	return S_OK;
}

/* input/output pin connection complete */

HRESULT CXvidDecoder::CompleteConnect(PIN_DIRECTION direction, IPin *pReceivePin)
{
	DPRINTF("CompleteConnect");

	if ((direction == PINDIR_OUTPUT) && (Tray_Icon == 0)&& (g_config.bTrayIcon != 0)) 
	{
		WNDCLASSEX wc; 

		wc.cbSize = sizeof(WNDCLASSEX);
		wc.lpfnWndProc = msg_proc;
		wc.style = CS_HREDRAW | CS_VREDRAW;
		wc.cbWndExtra = 0;
		wc.cbClsExtra = 0;
		wc.hInstance = (HINSTANCE) g_xvid_hInst;
		wc.hbrBackground = (HBRUSH) GetStockObject(NULL_BRUSH);
		wc.lpszMenuName = NULL;
		wc.lpszClassName = "XVID_MSG_WINDOW";
		wc.hIcon = NULL;
		wc.hIconSm = NULL;
		wc.hCursor = NULL;
		RegisterClassEx(&wc);

		MSG_hwnd = CreateWindowEx(0, "XVID_MSG_WINDOW", NULL, 0, CW_USEDEFAULT, 
                                  CW_USEDEFAULT, 0, 0, HWND_MESSAGE, NULL, (HINSTANCE) g_xvid_hInst, NULL);

		/* display the tray icon */
		NOTIFYICONDATA nid;    
		ZeroMemory(&nid,sizeof(NOTIFYICONDATA));

		nid.cbSize = NOTIFYICONDATA_V1_SIZE;
		nid.hWnd = MSG_hwnd;  
		nid.uID = 1456;  
		nid.uCallbackMessage = WM_ICONMESSAGE;  
		nid.hIcon = LoadIcon(g_xvid_hInst, MAKEINTRESOURCE(IDI_ICON));  
		strcpy_s(nid.szTip, 19, "Xvid Video Decoder");  
		nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
	
		Shell_NotifyIcon(NIM_ADD, &nid); 

		DestroyIcon(nid.hIcon);
		Tray_Icon = 1;
	}

	return S_OK;
}

/* input/output pin disconnected */
HRESULT CXvidDecoder::BreakConnect(PIN_DIRECTION direction)
{
	DPRINTF("BreakConnect");

	return S_OK;
}

/* alloc output buffer */

HRESULT CXvidDecoder::DecideBufferSize(IMemAllocator *pAlloc, ALLOCATOR_PROPERTIES *ppropInputRequest)
{
	DPRINTF("DecideBufferSize");
	HRESULT result;
	ALLOCATOR_PROPERTIES ppropActual;

	if (m_pInput->IsConnected() == FALSE)
	{
		return E_UNEXPECTED;
	}

	ppropInputRequest->cBuffers = 1;
	ppropInputRequest->cbBuffer = m_create.width * m_create.height * 4;
	// cbAlign causes problems with the resize filter */
	// ppropInputRequest->cbAlign = 16;	
	ppropInputRequest->cbPrefix = 0;
		
	result = pAlloc->SetProperties(ppropInputRequest, &ppropActual);
	if (result != S_OK)
	{
		return result;
	}

	if (ppropActual.cbBuffer < ppropInputRequest->cbBuffer)
	{
		return E_FAIL;
	}

	return S_OK;
}


/* decode frame */

HRESULT CXvidDecoder::Transform(IMediaSample *pIn, IMediaSample *pOut)
{
	DPRINTF("Transform");
	xvid_dec_stats_t stats;
    int length;

	memset(&stats, 0, sizeof(stats));
	stats.version = XVID_VERSION;

	if (m_create.handle == NULL)
	{
		if (xvid_decore_func == NULL)
			return E_FAIL;

		if (xvid_decore_func(0, XVID_DEC_CREATE, &m_create, 0) < 0)
		{
            DPRINTF("*** XVID_DEC_CREATE error");
			return E_FAIL;
		}
	}

	AM_MEDIA_TYPE * mtOut;
	pOut->GetMediaType(&mtOut);
	if (mtOut != NULL)
	{
		HRESULT result;

		result = ChangeColorspace(mtOut->subtype, mtOut->formattype, mtOut->pbFormat, 0);
		DeleteMediaType(mtOut);

		if (result != S_OK)
		{
            DPRINTF("*** ChangeColorspace error");
			return result;
		}
	}
	
	m_frame.length = pIn->GetActualDataLength();
	if (pIn->GetPointer((BYTE**)&m_frame.bitstream) != S_OK)
	{
		return S_FALSE;
	}

	if (pOut->GetPointer((BYTE**)&m_frame.output.plane[0]) != S_OK)
	{
		return S_FALSE; 
	}

	m_frame.general = XVID_LOWDELAY;

	if (pIn->IsDiscontinuity() == S_OK)
		m_frame.general |= XVID_DISCONTINUITY;

	if (g_config.nDeblock_Y)
		m_frame.general |= XVID_DEBLOCKY;

	if (g_config.nDeblock_UV)
		m_frame.general |= XVID_DEBLOCKUV;

	if (g_config.nDering_Y)
		m_frame.general |= XVID_DERINGY;

	if (g_config.nDering_UV)
		m_frame.general |= XVID_DERINGUV;

	if (g_config.nFilmEffect)
		m_frame.general |= XVID_FILMEFFECT;

	m_frame.brightness = g_config.nBrightness;

	m_frame.output.csp &= ~XVID_CSP_VFLIP;
	m_frame.output.csp |= rgb_flip^(g_config.nFlipVideo ? XVID_CSP_VFLIP : 0);
	m_frame.output.stride[0] = out_stride;

    // Paranoid check.
    if (xvid_decore_func == NULL)
		return E_FAIL;


repeat :

	if (pIn->IsPreroll() != S_OK)
	{
		length = xvid_decore_func(m_create.handle, XVID_DEC_DECODE, &m_frame, &stats);
                
		if (length == XVID_ERR_MEMORY)
			return E_FAIL;
		else if (length < 0)
		{
            DPRINTF("*** XVID_DEC_DECODE");
			return S_FALSE;
		} else 
			if (g_config.aspect_ratio == 0 || g_config.aspect_ratio == 1 && forced_ar == false) {
        
      if (stats.type != XVID_TYPE_NOTHING) {  /* dont attempt to set vmr aspect ratio if no frame was returned by decoder */
			// inspired by minolta! works for VMR 7 + 9
			IMediaSample2 *pOut2 = NULL;
			AM_SAMPLE2_PROPERTIES outProp2;
			if (SUCCEEDED(pOut->QueryInterface(IID_IMediaSample2, (void **)&pOut2)) &&
				SUCCEEDED(pOut2->GetProperties(FIELD_OFFSET(AM_SAMPLE2_PROPERTIES, tStart), (PBYTE)&outProp2)))
			{
				CMediaType mtOut2 = m_pOutput->CurrentMediaType();
				VIDEOINFOHEADER2* vihOut2 = (VIDEOINFOHEADER2*)mtOut2.Format();

				if (*mtOut2.FormatType() == FORMAT_VideoInfo2 && 
					vihOut2->dwPictAspectRatioX != ar_x && vihOut2->dwPictAspectRatioY != ar_y)
				{
					vihOut2->dwPictAspectRatioX = ar_x;
					vihOut2->dwPictAspectRatioY = ar_y;
					pOut2->SetMediaType(&mtOut2);
					m_pOutput->SetMediaType(&mtOut2);
				}
				pOut2->Release();
			}
      }
		}
	}
	else
	{	/* Preroll frame - won't be displayed */
		int tmp = m_frame.output.csp;
		int tmp_gen = m_frame.general;

		m_frame.output.csp = XVID_CSP_NULL;

		/* Disable postprocessing to speed-up seeking */
		m_frame.general &= ~XVID_DEBLOCKY;
		m_frame.general &= ~XVID_DEBLOCKUV;
		/*m_frame.general &= ~XVID_DERING;*/
		m_frame.general &= ~XVID_FILMEFFECT;

		length = xvid_decore_func(m_create.handle, XVID_DEC_DECODE, &m_frame, &stats);
		if (length == XVID_ERR_MEMORY)
			return E_FAIL;
		else if (length < 0)
		{
            DPRINTF("*** XVID_DEC_DECODE");
			return S_FALSE;
		}

		m_frame.output.csp = tmp;
		m_frame.general = tmp_gen;
	}

	if (stats.type == XVID_TYPE_NOTHING && length > 0) {
		DPRINTF(" B-Frame decoder lag");
		return S_FALSE;
	}


	if (stats.type == XVID_TYPE_VOL)
	{
		if (stats.data.vol.width != m_create.width ||
			stats.data.vol.height != m_create.height)
		{
			DPRINTF("TODO: auto-resize");
			return S_FALSE;
		}

		pOut->SetSyncPoint(TRUE);

		if (g_config.aspect_ratio == 0 || g_config.aspect_ratio == 1) { /* auto */
			int par_x, par_y;
			if (stats.data.vol.par == XVID_PAR_EXT) {
				par_x = stats.data.vol.par_width;
				par_y = stats.data.vol.par_height;
			} else {
				par_x = PARS[stats.data.vol.par-1][0];
				par_y = PARS[stats.data.vol.par-1][1];
			}

			ar_x = par_x * stats.data.vol.width;
			ar_y = par_y * stats.data.vol.height;
		}

		m_frame.bitstream = (BYTE*)m_frame.bitstream + length;
		m_frame.length -= length;
		goto repeat;
	}

	if (pIn->IsPreroll() == S_OK) {
		return S_FALSE;
	}

	return S_OK;
}


/* get property page list */

STDMETHODIMP CXvidDecoder::GetPages(CAUUID * pPages)
{
	DPRINTF("GetPages");

	pPages->cElems = 1;
	pPages->pElems = (GUID *)CoTaskMemAlloc(pPages->cElems * sizeof(GUID));
	if (pPages->pElems == NULL)
	{
		return E_OUTOFMEMORY;
	}
	pPages->pElems[0] = CLSID_CABOUT;

	return S_OK;
}


/* cleanup pages */

STDMETHODIMP CXvidDecoder::FreePages(CAUUID * pPages)
{
	DPRINTF("FreePages");
	CoTaskMemFree(pPages->pElems);
	return S_OK;
}

/*===============================================================================
// MFT Interface
//=============================================================================*/
#if defined(XVID_USE_MFT)
#include <limits.h> // _I64_MAX
#define INVALID_TIME  _I64_MAX

HRESULT CXvidDecoder::MFTGetStreamLimits(DWORD *pdwInputMinimum, DWORD *pdwInputMaximum, DWORD *pdwOutputMinimum, DWORD *pdwOutputMaximum)
{
	DPRINTF("(MFT)GetStreamLimits");

	if ((pdwInputMinimum == NULL) || (pdwInputMaximum == NULL) || (pdwOutputMinimum == NULL) || (pdwOutputMaximum == NULL))
		return E_POINTER;

	/* Just a fixed number of streams allowed */
	*pdwInputMinimum = *pdwInputMaximum = 1;
	*pdwOutputMinimum = *pdwOutputMaximum = 1;

	return S_OK;
}

HRESULT CXvidDecoder::MFTGetStreamCount(DWORD *pcInputStreams, DWORD *pcOutputStreams)
{
	DPRINTF("(MFT)GetStreamCount");

	if ((pcInputStreams == NULL) || (pcOutputStreams == NULL))
		return E_POINTER;

	/* We have a fixed number of streams */
	*pcInputStreams = 1;
	*pcOutputStreams = 1;

	return S_OK;
}

HRESULT CXvidDecoder::MFTGetStreamIDs(DWORD dwInputIDArraySize, DWORD *pdwInputIDs, DWORD dwOutputIDArraySize, DWORD *pdwOutputIDs)
{
	DPRINTF("(MFT)GetStreamIDs");
	return E_NOTIMPL; /* We have fixed number of streams, so stream ID match stream index */
}

HRESULT CXvidDecoder::MFTGetInputStreamInfo(DWORD dwInputStreamID, MFT_INPUT_STREAM_INFO *pStreamInfo)
{
	DPRINTF("(MFT)GetInputStreamInfo");

	if (pStreamInfo == NULL)
		return E_POINTER;

	if (dwInputStreamID != 0)
		return MF_E_INVALIDSTREAMNUMBER;

	EnterCriticalSection(&m_mft_lock);

	pStreamInfo->dwFlags = MFT_INPUT_STREAM_WHOLE_SAMPLES | MFT_INPUT_STREAM_SINGLE_SAMPLE_PER_BUFFER;
	pStreamInfo->hnsMaxLatency = 0;

	pStreamInfo->cbSize = 1; /* Need atleast 1 byte input */
	pStreamInfo->cbMaxLookahead = 0;
	pStreamInfo->cbAlignment = 1;

	LeaveCriticalSection(&m_mft_lock);
	return S_OK;
}

HRESULT CXvidDecoder::MFTGetOutputStreamInfo(DWORD dwOutputStreamID, MFT_OUTPUT_STREAM_INFO *pStreamInfo)
{
	DPRINTF("(MFT)GetOutputStreamInfo");

	if (pStreamInfo == NULL)
		return E_POINTER;

	if (dwOutputStreamID != 0)
		return MF_E_INVALIDSTREAMNUMBER;

	EnterCriticalSection(&m_mft_lock);

	pStreamInfo->dwFlags = MFT_OUTPUT_STREAM_WHOLE_SAMPLES | MFT_OUTPUT_STREAM_SINGLE_SAMPLE_PER_BUFFER | MFT_OUTPUT_STREAM_FIXED_SAMPLE_SIZE | MFT_OUTPUT_STREAM_DISCARDABLE;

	if (m_pOutputType == NULL) {
		pStreamInfo->cbSize = 0;
		pStreamInfo->cbAlignment = 0;
	}
	else {
		pStreamInfo->cbSize = m_create.width * abs(m_create.height) * 4; // XXX
		pStreamInfo->cbAlignment = 1;
	}

	LeaveCriticalSection(&m_mft_lock);
	return S_OK;
}

HRESULT CXvidDecoder::GetAttributes(IMFAttributes** pAttributes)
{
	DPRINTF("(MFT)GetAttributes");
	return E_NOTIMPL; /* We don't support any attributes */
}

HRESULT CXvidDecoder::GetInputStreamAttributes(DWORD dwInputStreamID, IMFAttributes **ppAttributes)
{
	DPRINTF("(MFT)GetInputStreamAttributes");
	return E_NOTIMPL; /* We don't support any attributes */
}

HRESULT CXvidDecoder::GetOutputStreamAttributes(DWORD dwOutputStreamID, IMFAttributes **ppAttributes)
{
	DPRINTF("(MFT)GetOutputStreamAttributes");
	return E_NOTIMPL; /* We don't support any attributes */
}

HRESULT CXvidDecoder::MFTDeleteInputStream(DWORD dwStreamID)
{
	DPRINTF("(MFT)DeleteInputStream");
	return E_NOTIMPL; /* We have a fixed number of streams */
}

HRESULT CXvidDecoder::MFTAddInputStreams(DWORD cStreams, DWORD *adwStreamIDs)
{
	DPRINTF("(MFT)AddInputStreams");
	return E_NOTIMPL; /* We have a fixed number of streams */
}

HRESULT CXvidDecoder::MFTGetInputAvailableType(DWORD dwInputStreamID, DWORD dwTypeIndex, IMFMediaType **ppType)
{
	DPRINTF("(MFT)GetInputAvailableType");

	if (dwInputStreamID != 0)
		return MF_E_INVALIDSTREAMNUMBER;

	DWORD i = 0;
	GUID *bs_guid_table[8];

	bs_guid_table[i++] = (GUID *)&CLSID_XVID;
	bs_guid_table[i++] = (GUID *)&CLSID_XVID_UC;
	
	if (g_config.supported_4cc & SUPPORT_3IVX) {
		bs_guid_table[i++] = (GUID *)&CLSID_3IVX;
		bs_guid_table[i++] = (GUID *)&CLSID_3IVX_UC;
		bs_guid_table[i++] = (GUID *)&CLSID_3IV0;
		bs_guid_table[i++] = (GUID *)&CLSID_3IV0_UC;
		bs_guid_table[i++] = (GUID *)&CLSID_3IV1;
		bs_guid_table[i++] = (GUID *)&CLSID_3IV1_UC;
		bs_guid_table[i++] = (GUID *)&CLSID_3IV2;
		bs_guid_table[i++] = (GUID *)&CLSID_3IV2_UC;
	}
	if (g_config.supported_4cc & SUPPORT_DIVX) {
		bs_guid_table[i++] = (GUID *)&CLSID_DIVX;
		bs_guid_table[i++] = (GUID *)&CLSID_DIVX_UC;
		bs_guid_table[i++] = (GUID *)&CLSID_DX50;
		bs_guid_table[i++] = (GUID *)&CLSID_DX50_UC;
	}
	if (g_config.supported_4cc & SUPPORT_MP4V) {
		bs_guid_table[i++] = (GUID *)&CLSID_MP4V;
		bs_guid_table[i++] = (GUID *)&CLSID_MP4V_UC;
		bs_guid_table[i++] = (GUID *)&CLSID_LMP4;
		bs_guid_table[i++] = (GUID *)&CLSID_LMP4_UC;
		bs_guid_table[i++] = (GUID *)&CLSID_RMP4;
		bs_guid_table[i++] = (GUID *)&CLSID_RMP4_UC;
		bs_guid_table[i++] = (GUID *)&CLSID_SMP4;
		bs_guid_table[i++] = (GUID *)&CLSID_SMP4_UC;
		bs_guid_table[i++] = (GUID *)&CLSID_HDX4;
		bs_guid_table[i++] = (GUID *)&CLSID_HDX4_UC;
	}

	const GUID *subtype;
	if (dwTypeIndex < i) {
		subtype = bs_guid_table[dwTypeIndex];
	}
	else {
		return MF_E_NO_MORE_TYPES;
	}

	EnterCriticalSection(&m_mft_lock);

	HRESULT hr = S_OK;

	if (ppType) {
		IMFMediaType *pInputType = NULL;
		hr = MFCreateMediaType(&pInputType);

		if (SUCCEEDED(hr))
			hr = pInputType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);

		if (SUCCEEDED(hr))
			hr = pInputType->SetGUID(MF_MT_SUBTYPE, *subtype);

		if (SUCCEEDED(hr)) {
			*ppType = pInputType;
			(*ppType)->AddRef();
		}
		if (pInputType) pInputType->Release();
	}
	
	LeaveCriticalSection(&m_mft_lock);
	
	return hr;
}

HRESULT CXvidDecoder::MFTGetOutputAvailableType(DWORD dwOutputStreamID, DWORD dwTypeIndex, IMFMediaType **ppType)
{
	DPRINTF("(MFT)GetOutputAvailableType");

	if (ppType == NULL)
		return E_INVALIDARG;

	if (dwOutputStreamID != 0)
		return MF_E_INVALIDSTREAMNUMBER;
  
	if (dwTypeIndex < 0) return E_INVALIDARG;

	GUID csp;
	int bitdepth = 8;
	switch(dwTypeIndex)
	{
	case 0:
if ( USE_YUY2 )
{
		csp = MFVideoFormat_YUY2;
		bitdepth = 4;
		break;
}
	case 1 :
if ( USE_UYVY )
{
		csp = MFVideoFormat_UYVY;
		bitdepth = 4;
		break;
}
	case 2	:
		if ( USE_IYUV )
{
		csp = MFVideoFormat_IYUV;
		bitdepth = 3;
		break;
}
	case 3	:
if ( USE_YV12 )
{
		csp = MFVideoFormat_YV12;
		bitdepth = 3;
		break;
}
	case 4 :
if ( USE_RGB32 )
{
		csp = MFVideoFormat_RGB32;
		bitdepth = 8;
		break;
}
	case 5 :
if ( USE_RGB24 )
{
		csp = MFVideoFormat_RGB24;
		bitdepth = 6;
		break;
}
	case 6 :
if ( USE_RG555 )
{
		csp = MFVideoFormat_RGB555;
		bitdepth = 4;
		break;
}
	case 7 :
if ( USE_RG565 )
{
		csp = MFVideoFormat_RGB565;
		bitdepth = 4;
		break;
}	
	default :
		return MF_E_NO_MORE_TYPES;
	}

	if (m_pInputType == NULL)
		return MF_E_TRANSFORM_TYPE_NOT_SET;

	EnterCriticalSection(&m_mft_lock);

	HRESULT hr = S_OK;

	IMFMediaType *pOutputType = NULL;
	hr = MFCreateMediaType(&pOutputType);

	if (SUCCEEDED(hr)) {
		hr = pOutputType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
	}
	
	if (SUCCEEDED(hr)) {
		hr = pOutputType->SetGUID(MF_MT_SUBTYPE, csp);
    }
	
	if (SUCCEEDED(hr)) {
		hr = pOutputType->SetUINT32(MF_MT_FIXED_SIZE_SAMPLES, TRUE);
	}
	
	if (SUCCEEDED(hr)) {
		hr = pOutputType->SetUINT32(MF_MT_ALL_SAMPLES_INDEPENDENT, TRUE);
	}
	
	if (SUCCEEDED(hr)) {
		hr = pOutputType->SetUINT32(MF_MT_SAMPLE_SIZE, (m_create.height * m_create.width * bitdepth)>>1);
	}
	
	if (SUCCEEDED(hr)) {
		hr = MFSetAttributeSize(pOutputType, MF_MT_FRAME_SIZE, m_create.width, m_create.height);
	}
	
	if (SUCCEEDED(hr)) {
		hr = MFSetAttributeRatio(pOutputType, MF_MT_FRAME_RATE, m_frameRate.Numerator, m_frameRate.Denominator);
	}
	
	if (SUCCEEDED(hr)) {
		hr = pOutputType->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
	}
	
	if (SUCCEEDED(hr)) {
		hr = MFSetAttributeRatio(pOutputType, MF_MT_PIXEL_ASPECT_RATIO, ar_x, ar_y);
	}
	
	if (SUCCEEDED(hr)) {
		*ppType = pOutputType;
		(*ppType)->AddRef();
	}
	
	if (pOutputType) pOutputType->Release();
	
	LeaveCriticalSection(&m_mft_lock);
	return hr;
}

HRESULT CXvidDecoder::MFTSetInputType(DWORD dwInputStreamID, IMFMediaType *pType, DWORD dwFlags)
{
	DPRINTF("(MFT)SetInputType");

	if (dwInputStreamID != 0)
		return MF_E_INVALIDSTREAMNUMBER;

	if (dwFlags & ~MFT_SET_TYPE_TEST_ONLY)
		return E_INVALIDARG;

	EnterCriticalSection(&m_mft_lock);

	HRESULT hr = S_OK;

	/* Actually set the type or just test it? */
	BOOL bReallySet = ((dwFlags & MFT_SET_TYPE_TEST_ONLY) == 0);

	/* If we have samples pending the type can't be changed right now */
	if (HasPendingOutput())
		hr = MF_E_TRANSFORM_CANNOT_CHANGE_MEDIATYPE_WHILE_PROCESSING;

	if (SUCCEEDED(hr)) { 
        if (pType) { // /* Check the type */
            hr = OnCheckInputType(pType);
        }
	}
	
	if (SUCCEEDED(hr)) {
		if (bReallySet) { /* Set the type if needed */
			hr = OnSetInputType(pType);
		}
	}
	
	LeaveCriticalSection(&m_mft_lock);
	return hr;
}

HRESULT CXvidDecoder::MFTSetOutputType(DWORD dwOutputStreamID, IMFMediaType *pType, DWORD dwFlags)
{
	DPRINTF("(MFT)SetOutputType");

	if (dwOutputStreamID != 0)
		return MF_E_INVALIDSTREAMNUMBER;

	if (dwFlags & ~MFT_SET_TYPE_TEST_ONLY)
		return E_INVALIDARG;

	HRESULT hr = S_OK;
	
	EnterCriticalSection(&m_mft_lock);
	
	/* Actually set the type or just test it? */
	BOOL bReallySet = ((dwFlags & MFT_SET_TYPE_TEST_ONLY) == 0);
	
	/* If we have samples pending the type can't be changed right now */
	if (HasPendingOutput())
		hr = MF_E_TRANSFORM_CANNOT_CHANGE_MEDIATYPE_WHILE_PROCESSING;

	if (SUCCEEDED(hr)) { 
		if (pType) { /* Check the type */
			AM_MEDIA_TYPE *am;
			hr = MFCreateAMMediaTypeFromMFMediaType(pType, GUID_NULL, &am);
			
			if (SUCCEEDED(hr)) {
				if (FAILED(ChangeColorspace(am->subtype, am->formattype, am->pbFormat, 1))) {
					DPRINTF("(MFT)InternalCheckOutputType (MF_E_INVALIDTYPE)");
					return MF_E_INVALIDTYPE;
				}

			CoTaskMemFree(am->pbFormat);
			CoTaskMemFree(am);
			}
		}
	}
	
	if (SUCCEEDED(hr)) {
		if (bReallySet) { /* Set the type if needed */
			hr = OnSetOutputType(pType);
		}
	}

	if (SUCCEEDED(hr) && (Tray_Icon == 0) && (g_config.bTrayIcon != 0))  /* Create message passing window */
	{
		WNDCLASSEX wc; 

		wc.cbSize = sizeof(WNDCLASSEX);
		wc.lpfnWndProc = msg_proc;
		wc.style = CS_HREDRAW | CS_VREDRAW;
		wc.cbWndExtra = 0;
		wc.cbClsExtra = 0;
		wc.hInstance = (HINSTANCE) g_xvid_hInst;
		wc.hbrBackground = (HBRUSH) GetStockObject(NULL_BRUSH);
		wc.lpszMenuName = NULL;
		wc.lpszClassName = "XVID_MSG_WINDOW";
		wc.hIcon = NULL;
		wc.hIconSm = NULL;
		wc.hCursor = NULL;
		RegisterClassEx(&wc);

		MSG_hwnd = CreateWindowEx(0, "XVID_MSG_WINDOW", NULL, 0, CW_USEDEFAULT, 
                                  CW_USEDEFAULT, 0, 0, HWND_MESSAGE, NULL, (HINSTANCE) g_xvid_hInst, NULL);

		/* display the tray icon */
		NOTIFYICONDATA nid;    
		ZeroMemory(&nid,sizeof(NOTIFYICONDATA));

		nid.cbSize = NOTIFYICONDATA_V1_SIZE;
		nid.hWnd = MSG_hwnd;  
		nid.uID = 1456;  
		nid.uCallbackMessage = WM_ICONMESSAGE;  
		nid.hIcon = LoadIcon(g_xvid_hInst, MAKEINTRESOURCE(IDI_ICON));  
		strcpy_s(nid.szTip, 19, "Xvid Video Decoder");  
		nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
		
		Shell_NotifyIcon(NIM_ADD, &nid); 

		DestroyIcon(nid.hIcon);
		Tray_Icon = 1;
	}

	LeaveCriticalSection(&m_mft_lock);
	return hr;
}

HRESULT CXvidDecoder::MFTGetInputCurrentType(DWORD dwInputStreamID, IMFMediaType **ppType)
{
	DPRINTF("(MFT)GetInputCurrentType");
	
	if (ppType == NULL)
		return E_POINTER;
	
	if (dwInputStreamID != 0)
		return MF_E_INVALIDSTREAMNUMBER;
	
	EnterCriticalSection(&m_mft_lock);
	
	HRESULT hr = S_OK;
	
	if (!m_pInputType)
		hr = MF_E_TRANSFORM_TYPE_NOT_SET;

	if (SUCCEEDED(hr)) {
		*ppType = m_pInputType;
		(*ppType)->AddRef();
	}
	
	LeaveCriticalSection(&m_mft_lock);
	return hr;
}

HRESULT CXvidDecoder::MFTGetOutputCurrentType(DWORD dwOutputStreamID, IMFMediaType **ppType)
{
	DPRINTF("(MFT)GetOutputCurrentType");
	
	if (ppType == NULL)
		return E_POINTER;
	
	if (dwOutputStreamID != 0)
		return MF_E_INVALIDSTREAMNUMBER;
	
	EnterCriticalSection(&m_mft_lock);
	
	HRESULT hr = S_OK;
	
	if (!m_pOutputType)
		hr = MF_E_TRANSFORM_TYPE_NOT_SET;
	
	if (SUCCEEDED(hr)) {
		*ppType = m_pOutputType;
		(*ppType)->AddRef();
	}
	
	LeaveCriticalSection(&m_mft_lock);
	return hr;
}

HRESULT CXvidDecoder::MFTGetInputStatus(DWORD dwInputStreamID, DWORD *pdwFlags)
{
	DPRINTF("(MFT)GetInputStatus");
	
	if (pdwFlags == NULL)
		return E_POINTER;
	
	if (dwInputStreamID != 0)
		return MF_E_INVALIDSTREAMNUMBER;
	
	EnterCriticalSection(&m_mft_lock);
	
	/* If there's pending output sampels we don't accept new
	   input data until ProcessOutput() or Flush() was called */
	if (!HasPendingOutput()) {
		*pdwFlags = MFT_INPUT_STATUS_ACCEPT_DATA;
	}
	else {
		*pdwFlags = 0;
	}
	
	LeaveCriticalSection(&m_mft_lock);
	
	return S_OK;
}

HRESULT CXvidDecoder::MFTGetOutputStatus(DWORD *pdwFlags)
{
	DPRINTF("(MFT)GetOutputStatus");
	
	if (pdwFlags == NULL)
		return E_POINTER;
	
	EnterCriticalSection(&m_mft_lock);
	
	/* We can render an output sample only after we
	   have decoded one */
	if (HasPendingOutput()) {
		*pdwFlags = MFT_OUTPUT_STATUS_SAMPLE_READY;
	}
	else {
		*pdwFlags = 0;
	}
	
	LeaveCriticalSection(&m_mft_lock);
	
	return S_OK;
}

HRESULT CXvidDecoder::MFTSetOutputBounds(LONGLONG hnsLowerBound, LONGLONG hnsUpperBound)
{
	DPRINTF("(MFT)SetOutputBounds");
	return E_NOTIMPL;
}

HRESULT CXvidDecoder::MFTProcessEvent(DWORD dwInputStreamID, IMFMediaEvent *pEvent)
{
	DPRINTF("(MFT)ProcessEvent");
	return E_NOTIMPL; /* We don't handle any stream events */
}

HRESULT CXvidDecoder::MFTProcessMessage(MFT_MESSAGE_TYPE eMessage, ULONG_PTR ulParam)
{
	DPRINTF("(MFT)ProcessMessage");
	HRESULT hr = S_OK;

	EnterCriticalSection(&m_mft_lock);
	
	switch (eMessage)
	{
	case MFT_MESSAGE_COMMAND_FLUSH:
		if (m_create.handle != NULL) {
			DPRINTF("(MFT)CommandFlush");

			xvid_dec_stats_t stats;
			int used_bytes;
			
			memset(&stats, 0, sizeof(stats));
			stats.version = XVID_VERSION;

			int csp = m_frame.output.csp;

			m_frame.output.csp = XVID_CSP_INTERNAL;
			m_frame.bitstream = NULL;
			m_frame.length = -1;
			m_frame.general = XVID_LOWDELAY;

			do {
				used_bytes = xvid_decore_func(m_create.handle, XVID_DEC_DECODE, &m_frame, &stats);
			} while(used_bytes>=0 && stats.type <= 0);

			m_frame.output.csp = csp;
			m_frame.output.plane[1] = NULL; /* Don't display flushed samples */

			//m_timestamp = INVALID_TIME;
			//m_timelength = INVALID_TIME;
			//m_rtFrame = 0;
	}
    break;

	case MFT_MESSAGE_COMMAND_DRAIN:
		m_discont = 1; /* Set discontinuity flag */
		m_rtFrame = 0;
		break;
	
	case MFT_MESSAGE_SET_D3D_MANAGER:
		hr = E_NOTIMPL;
		break;
	
	case MFT_MESSAGE_NOTIFY_BEGIN_STREAMING:
	case MFT_MESSAGE_NOTIFY_END_STREAMING:
		break;
		
	case MFT_MESSAGE_NOTIFY_START_OF_STREAM:
	case MFT_MESSAGE_NOTIFY_END_OF_STREAM:
		break;
	}
	
	LeaveCriticalSection(&m_mft_lock);

	return hr;
}

HRESULT CXvidDecoder::MFTProcessInput(DWORD dwInputStreamID, IMFSample *pSample, DWORD dwFlags)
{
	DPRINTF("(MFT)ProcessInput");
	
	if (pSample == NULL)
		return E_POINTER;
	
	if (dwInputStreamID != 0)
		return MF_E_INVALIDSTREAMNUMBER;
	
	if (dwFlags != 0)
		return E_INVALIDARG;
	
	if (!m_pInputType || !m_pOutputType) {
		return MF_E_NOTACCEPTING;   /* Must have set input and output types */
	}
	else if (HasPendingOutput()) {
		return MF_E_NOTACCEPTING;   /* We still have output samples to render */
	}
	
	xvid_dec_stats_t stats;
	int length;
		
	memset(&stats, 0, sizeof(stats));
	stats.version = XVID_VERSION;
		
	if (m_create.handle == NULL)
	{
		if (xvid_decore_func == NULL)
			return E_FAIL;
		if (xvid_decore_func(0, XVID_DEC_CREATE, &m_create, 0) < 0)
		{
			DPRINTF("*** XVID_DEC_CREATE error");
			return E_FAIL;
		}
	}
	
	EnterCriticalSection(&m_mft_lock);

	HRESULT hr = S_OK;
	IMFMediaBuffer *pBuffer;

	if (SUCCEEDED(hr)) {
		hr = pSample->ConvertToContiguousBuffer(&pBuffer);
	}

	if (SUCCEEDED(hr)) {
		hr = pBuffer->Lock((BYTE**)&m_frame.bitstream, NULL, (DWORD *)&m_frame.length);
	}

	m_frame.general = XVID_LOWDELAY;

	if (m_discont == 1) {
		m_frame.general |= XVID_DISCONTINUITY;
		m_discont = 0;
	}

	if (g_config.nDeblock_Y)
		m_frame.general |= XVID_DEBLOCKY;

	if (g_config.nDeblock_UV)
		m_frame.general |= XVID_DEBLOCKUV;

	if (g_config.nDering_Y)
		m_frame.general |= XVID_DERINGY;

	if (g_config.nDering_UV)
		m_frame.general |= XVID_DERINGUV;

	if (g_config.nFilmEffect)
		m_frame.general |= XVID_FILMEFFECT;

	m_frame.brightness = g_config.nBrightness;

	m_frame.output.csp &= ~XVID_CSP_VFLIP;
	m_frame.output.csp |= rgb_flip^(g_config.nFlipVideo ? XVID_CSP_VFLIP : 0);

	int csp = m_frame.output.csp;
	m_frame.output.csp = XVID_CSP_INTERNAL;

    // Paranoid check.
	if (xvid_decore_func == NULL) {
		hr = E_FAIL;
		goto END_LOOP;
	}

repeat :
	length = xvid_decore_func(m_create.handle, XVID_DEC_DECODE, &m_frame, &stats);
                
	if (length == XVID_ERR_MEMORY) {
		hr = E_FAIL;
		goto END_LOOP;
	}
	else if (length < 0)
	{
		DPRINTF("*** XVID_DEC_DECODE");
		goto END_LOOP;
	}

	if (stats.type == XVID_TYPE_NOTHING && length > 0) {
		DPRINTF(" B-Frame decoder lag");
		m_frame.output.plane[1] = NULL;
		goto END_LOOP;
	}

	if (stats.type == XVID_TYPE_VOL)
	{
		if (stats.data.vol.width != m_create.width ||
			stats.data.vol.height != m_create.height)
		{
			DPRINTF("TODO: auto-resize");
			m_frame.output.plane[1] = NULL;
			hr = E_FAIL;
		}

		if (g_config.aspect_ratio == 0 || g_config.aspect_ratio == 1) { /* auto */
			int par_x, par_y;
			if (stats.data.vol.par == XVID_PAR_EXT) {
				par_x = stats.data.vol.par_width;
				par_y = stats.data.vol.par_height;
			} else {
				par_x = PARS[stats.data.vol.par-1][0];
				par_y = PARS[stats.data.vol.par-1][1];
			}

			ar_x = par_x * stats.data.vol.width;
			ar_y = par_y * stats.data.vol.height;
		}

		m_frame.bitstream = (BYTE*)m_frame.bitstream + length;
		m_frame.length -= length;
		goto repeat;
	}

END_LOOP:
	m_frame.output.csp = csp;

	if (pBuffer) {
		pBuffer->Unlock();
		pBuffer->Release();
	}

	if (SUCCEEDED(hr)) {
		/* Try to get a timestamp */
		if (FAILED(pSample->GetSampleTime(&m_timestamp)))
			m_timestamp = INVALID_TIME;
		
		if (FAILED(pSample->GetSampleDuration(&m_timelength))) {
			m_timelength = INVALID_TIME;
		}
		if (m_timestamp != INVALID_TIME && stats.type == XVID_TYPE_IVOP) {
			m_rtFrame = m_timestamp;
		}
	}
	
	LeaveCriticalSection(&m_mft_lock);

	return hr;
}

HRESULT CXvidDecoder::MFTProcessOutput(DWORD dwFlags, DWORD cOutputBufferCount, MFT_OUTPUT_DATA_BUFFER *pOutputSamples, DWORD *pdwStatus)
{
	DPRINTF("(MFT)ProcessOutput");
	
	/* Preroll in MFT ??
	   Flags ?? -> TODO... */
	if (dwFlags != 0)
		return E_INVALIDARG;
	
	if (pOutputSamples == NULL || pdwStatus == NULL)
		return E_POINTER;
	
	if (cOutputBufferCount != 1) /* Must be exactly one output buffer */
		return E_INVALIDARG;
	
	if (pOutputSamples[0].pSample == NULL) /* Must have a sample */
		return E_INVALIDARG;
	
	if (!HasPendingOutput()) { /* If there's no sample we need to decode one first */
		return MF_E_TRANSFORM_NEED_MORE_INPUT;
	}

	EnterCriticalSection(&m_mft_lock);
	
	HRESULT hr = S_OK;
	
	BYTE *Dst = NULL;
	DWORD buffer_size;
	
	IMFMediaBuffer *pOutput = NULL;
	
	if (SUCCEEDED(hr)) {
		hr = pOutputSamples[0].pSample->GetBufferByIndex(0, &pOutput); /* Get output buffer */
	}
	
	if (SUCCEEDED(hr)) {
		hr = pOutput->GetMaxLength(&buffer_size);
	}
	
	if (SUCCEEDED(hr))
		hr = pOutput->Lock(&Dst, NULL, NULL);

	if (SUCCEEDED(hr)) {
		xvid_gbl_convert_t convert;

		memset(&convert, 0, sizeof(convert));
		convert.version = XVID_VERSION;

		convert.input.csp = XVID_CSP_INTERNAL;
		convert.input.plane[0] = m_frame.output.plane[0];
		convert.input.plane[1] = m_frame.output.plane[1];
		convert.input.plane[2] = m_frame.output.plane[2];
		convert.input.stride[0] = m_frame.output.stride[0];
		convert.input.stride[1] = m_frame.output.stride[1];
		convert.input.stride[2] = m_frame.output.stride[2];

		convert.output.csp = m_frame.output.csp;
		convert.output.plane[0] = Dst;
		convert.output.stride[0] = out_stride;

		convert.width = m_create.width;
		convert.height = m_create.height;
		convert.interlacing = 0;
		
		if (m_frame.output.plane[1] != NULL && Dst != NULL && xvid_global_func != NULL) 
			if (xvid_global_func(0, XVID_GBL_CONVERT, &convert, NULL) < 0) /* CSP convert into output buffer */
				hr = E_FAIL;

		m_frame.output.plane[1] = NULL;
	}

	*pdwStatus = 0;
	
	if (SUCCEEDED(hr)) {
		if (SUCCEEDED(hr))
			hr = pOutputSamples[0].pSample->SetUINT32(MFSampleExtension_CleanPoint, TRUE); // key frame
		
		if (SUCCEEDED(hr)) { /* Set timestamp of output sample */
			if (m_timestamp != INVALID_TIME)
				hr = pOutputSamples[0].pSample->SetSampleTime(m_timestamp);
			else
				hr = pOutputSamples[0].pSample->SetSampleTime(m_rtFrame);
			
			if (m_timelength != INVALID_TIME)
				hr = pOutputSamples[0].pSample->SetSampleDuration(m_timelength);
			else
				hr = pOutputSamples[0].pSample->SetSampleDuration(m_duration);
			
			m_rtFrame += m_duration;
		}
		
		if (SUCCEEDED(hr))
			hr = pOutput->SetCurrentLength(m_create.width * abs(m_create.height) * 4); // XXX
	}
	
	if (pOutput) { 
		pOutput->Unlock(); 
		pOutput->Release(); 
	}
	
	LeaveCriticalSection(&m_mft_lock);

	return hr;
}

HRESULT CXvidDecoder::OnCheckInputType(IMFMediaType *pmt)
{
	DPRINTF("(MFT)CheckInputType");
	
	HRESULT hr = S_OK;
	
	/*  Check if input type is already set. Reject any type that is not identical */
	if (m_pInputType) {
		DWORD dwFlags = 0;
		if (S_OK == m_pInputType->IsEqual(pmt, &dwFlags)) {
			return S_OK;
		}
		else {
			return MF_E_INVALIDTYPE;
		}
	}
	
	GUID majortype = {0}, subtype = {0};
	UINT32 width = 0, height = 0;
	
	hr = pmt->GetMajorType(&majortype);
	
	if (SUCCEEDED(hr)) {
		if (majortype != MFMediaType_Video) { /* Must be Video */
			hr = MF_E_INVALIDTYPE;
		}
	}
	
	if (m_hdll == NULL) {
		HRESULT hr = OpenLib();
		
		if (FAILED(hr) || (m_hdll == NULL)) // Paranoid checks.
			hr = MF_E_INVALIDTYPE;
	}
	
	if (SUCCEEDED(hr)) {
		hr = MFGetAttributeSize(pmt, MF_MT_FRAME_SIZE, &width, &height);
	}
	
	/* Check the frame size */
	if (SUCCEEDED(hr)) {
		if (width > 4096 || height > 4096) {
			hr = MF_E_INVALIDTYPE;
		}
	}
	m_create.width = width;
	m_create.height = height;
	
	if (SUCCEEDED(hr)) {
		if (g_config.aspect_ratio == 0 || g_config.aspect_ratio == 1) {
			hr = MFGetAttributeRatio(pmt, MF_MT_PIXEL_ASPECT_RATIO, (UINT32*)&ar_x, (UINT32*)&ar_y);
		}
	}
	
	/* TODO1: Make sure there really is a frame rate after all!
	   TODO2: Use the framerate for something! */
	MFRatio fps = {0};
	if (SUCCEEDED(hr)) {
		hr = MFGetAttributeRatio(pmt, MF_MT_FRAME_RATE, (UINT32*)&fps.Numerator, (UINT32*)&fps.Denominator);
	}
	
	if (SUCCEEDED(hr)) {
		hr = pmt->GetGUID(MF_MT_SUBTYPE, &subtype);
	}
	
	if (subtype == CLSID_MP4V || subtype == CLSID_MP4V_UC ||
	    subtype == CLSID_LMP4 || subtype == CLSID_LMP4_UC ||
	    subtype == CLSID_RMP4 || subtype == CLSID_RMP4_UC ||
	    subtype == CLSID_SMP4 || subtype == CLSID_SMP4_UC ||
	    subtype == CLSID_HDX4 || subtype == CLSID_HDX4_UC) {
		if (!(g_config.supported_4cc & SUPPORT_MP4V)) {
			CloseLib();
			hr = MF_E_INVALIDTYPE;
		}
		else m_create.fourcc = FOURCC_MP4V;
	}
	else if (subtype == CLSID_DIVX || subtype == CLSID_DIVX_UC) {
		if (!(g_config.supported_4cc & SUPPORT_DIVX)) {
			CloseLib();
			hr = MF_E_INVALIDTYPE;
		}
		else m_create.fourcc = FOURCC_DIVX;
	}
	else if (subtype == CLSID_DX50 || subtype == CLSID_DX50_UC) {
		if (!(g_config.supported_4cc & SUPPORT_DIVX)) {
			CloseLib();
			hr = MF_E_INVALIDTYPE;
		}
		else m_create.fourcc = FOURCC_DX50;
	}
	else if (subtype == CLSID_3IVX || subtype == CLSID_3IVX_UC ||
	         subtype == CLSID_3IV0 || subtype == CLSID_3IV0_UC ||
	         subtype == CLSID_3IV1 || subtype == CLSID_3IV1_UC ||
	         subtype == CLSID_3IV2 || subtype == CLSID_3IV2_UC) {
		if (!(g_config.supported_4cc & SUPPORT_3IVX)) {
			CloseLib();
			hr = MF_E_INVALIDTYPE;
		}
		else m_create.fourcc = FOURCC_3IVX;
	}
	else if (subtype == CLSID_XVID || subtype == CLSID_XVID_UC) {
		m_create.fourcc = FOURCC_XVID;
	}
	else {
		DPRINTF("Unknown subtype!");
		CloseLib();
		hr = MF_E_INVALIDTYPE;
	}
	
	/* haali media splitter reports VOL information in the format header */
	if (SUCCEEDED(hr))
	{
		UINT32 cbSeqHeader = 0;
		
		(void)pmt->GetBlobSize(MF_MT_MPEG_SEQUENCE_HEADER, &cbSeqHeader);
		
		if (cbSeqHeader>0) {
			xvid_dec_stats_t stats;
			memset(&stats, 0, sizeof(stats));
			stats.version = XVID_VERSION;
			
			if (m_create.handle == NULL) {
				if (xvid_decore_func == NULL)
					hr = E_FAIL;
				if (xvid_decore_func(0, XVID_DEC_CREATE, &m_create, 0) < 0) {
					DPRINTF("*** XVID_DEC_CREATE error");
					hr = E_FAIL;
				}
			}
		
			if (SUCCEEDED(hr)) {
				(void)pmt->GetAllocatedBlob(MF_MT_MPEG_SEQUENCE_HEADER, (UINT8 **)&m_frame.bitstream, (UINT32 *)&m_frame.length);
				m_frame.general = 0;
				m_frame.output.csp = XVID_CSP_NULL;
			
				int ret = 0;
				if ((ret=xvid_decore_func(m_create.handle, XVID_DEC_DECODE, &m_frame, &stats)) >= 0) {
					/* honour video dimensions reported in VOL header */
					if (stats.type == XVID_TYPE_VOL) {
						m_create.width = stats.data.vol.width;
						m_create.height = stats.data.vol.height;
					}
				}

				if (ret == XVID_ERR_MEMORY) hr = E_FAIL;
				CoTaskMemFree(m_frame.bitstream);
			}
		}
	}
	
	return hr;
}

HRESULT CXvidDecoder::OnSetInputType(IMFMediaType *pmt)
{
	HRESULT hr = S_OK;
	UINT32 w, h;
	
	if (m_pInputType) m_pInputType->Release();
	
	hr = MFGetAttributeSize(pmt, MF_MT_FRAME_SIZE, &w, &h);
	m_create.width = w; m_create.height = h;
	
	if (SUCCEEDED(hr))
		hr = MFGetAttributeRatio(pmt, MF_MT_FRAME_RATE, (UINT32*)&m_frameRate.Numerator, (UINT32*)&m_frameRate.Denominator);
	
	if (SUCCEEDED(hr)) { /* Store frame duration, derived from the frame rate */
		hr = MFFrameRateToAverageTimePerFrame(m_frameRate.Numerator, m_frameRate.Denominator, &m_duration);
	}
	
	if (SUCCEEDED(hr)) {
		m_pInputType = pmt;
		m_pInputType->AddRef();
	}
	
	return hr;
}

HRESULT CXvidDecoder::OnSetOutputType(IMFMediaType *pmt)
{
	if (m_pOutputType) m_pOutputType->Release();
	
	m_pOutputType = pmt;
	m_pOutputType->AddRef();
	
	return S_OK;
}

#endif /* XVID_USE_MFT */
