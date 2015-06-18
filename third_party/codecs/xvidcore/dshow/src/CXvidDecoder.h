/*****************************************************************************
 *
 *  XVID MPEG-4 VIDEO CODEC
 *  - XviD Decoder part of the DShow Filter  -
 *
 *  Copyright(C) 2002-2012 Peter Ross <pross@xvid.org>
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
 * $Id: CXvidDecoder.h 2059 2012-02-22 19:00:26Z Isibaar $
 *
 ****************************************************************************/

#ifndef _FILTER_H_
#define _FILTER_H_

#include <xvid.h>
#include "IXvidDecoder.h"

#define XVID_NAME_L		L"Xvid MPEG-4 Video Decoder"

/* --- fourcc --- */

#define FOURCC_XVID	mmioFOURCC('X','V','I','D')
#define FOURCC_xvid	mmioFOURCC('x','v','i','d')
#define FOURCC_DIVX	mmioFOURCC('D','I','V','X')
#define FOURCC_divx	mmioFOURCC('d','i','v','x')
#define FOURCC_DX50	mmioFOURCC('D','X','5','0')
#define FOURCC_dx50	mmioFOURCC('d','x','5','0')
#define FOURCC_MP4V	mmioFOURCC('M','P','4','V')
#define FOURCC_mp4v	mmioFOURCC('m','p','4','v')
#define FOURCC_3IVX	mmioFOURCC('3','I','V','X')
#define FOURCC_3ivx	mmioFOURCC('3','i','v','x')
#define FOURCC_3IV0	mmioFOURCC('3','I','V','0')
#define FOURCC_3iv0	mmioFOURCC('3','i','v','0')
#define FOURCC_3IV1	mmioFOURCC('3','I','V','1')
#define FOURCC_3iv1	mmioFOURCC('3','i','v','1')
#define FOURCC_3IV2	mmioFOURCC('3','I','V','2')
#define FOURCC_3iv2	mmioFOURCC('3','i','v','2')
#define FOURCC_LMP4	mmioFOURCC('L','M','P','4')
#define FOURCC_lmp4	mmioFOURCC('l','m','p','4')
#define FOURCC_RMP4	mmioFOURCC('R','M','P','4')
#define FOURCC_rmp4	mmioFOURCC('r','m','p','4')
#define FOURCC_SMP4	mmioFOURCC('S','M','P','4')
#define FOURCC_smp4	mmioFOURCC('s','m','p','4')
#define FOURCC_HDX4	mmioFOURCC('H','D','X','4')
#define FOURCC_hdx4	mmioFOURCC('h','d','x','4')

/* --- media uids --- */

DEFINE_GUID(CLSID_XVID,		mmioFOURCC('x','v','i','d'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_XVID_UC,	mmioFOURCC('X','V','I','D'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_DIVX,		mmioFOURCC('d','i','v','x'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_DIVX_UC,	mmioFOURCC('D','I','V','X'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_DX50,		mmioFOURCC('d','x','5','0'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_DX50_UC,	mmioFOURCC('D','X','5','0'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_3IVX,		mmioFOURCC('3','i','v','x'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_3IVX_UC,	mmioFOURCC('3','I','V','X'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_3IV0,		mmioFOURCC('3','i','v','0'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_3IV0_UC,	mmioFOURCC('3','I','V','0'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_3IV1,		mmioFOURCC('3','i','v','1'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_3IV1_UC,	mmioFOURCC('3','I','V','1'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_3IV2,		mmioFOURCC('3','i','v','2'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_3IV2_UC,	mmioFOURCC('3','I','V','2'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_LMP4,		mmioFOURCC('l','m','p','4'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_LMP4_UC,	mmioFOURCC('L','M','P','4'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_RMP4,		mmioFOURCC('r','m','p','4'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_RMP4_UC,	mmioFOURCC('R','M','P','4'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_SMP4,		mmioFOURCC('s','m','p','4'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_SMP4_UC,	mmioFOURCC('S','M','P','4'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_HDX4,		mmioFOURCC('h','d','x','4'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_HDX4_UC,	mmioFOURCC('H','D','X','4'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_MP4V,		mmioFOURCC('m','p','4','v'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
DEFINE_GUID(CLSID_MP4V_UC,		mmioFOURCC('M','P','4','V'), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);


/* MEDIATYPE_IYUV is not always defined in the directx headers */
DEFINE_GUID(CLSID_MEDIASUBTYPE_IYUV, 0x56555949, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);


class CXvidDecoder : 
	public CVideoTransformFilter, 
	public IXvidDecoder, 
	public ISpecifyPropertyPages
#if defined(XVID_USE_MFT)
    ,public IMFTransform
#endif
{

public :

	static CUnknown * WINAPI CreateInstance(LPUNKNOWN punk, HRESULT *phr);
	STDMETHODIMP NonDelegatingQueryInterface(REFIID riid, void ** ppv);
	DECLARE_IUNKNOWN;

	CXvidDecoder(LPUNKNOWN punk, HRESULT *phr);
	~CXvidDecoder();

	HRESULT CompleteConnect(PIN_DIRECTION direction, IPin *pReceivePin);
	HRESULT BreakConnect(PIN_DIRECTION dir);

	HRESULT CheckInputType(const CMediaType * mtIn);
	HRESULT GetMediaType(int iPos, CMediaType * pmt);
	HRESULT SetMediaType(PIN_DIRECTION direction, const CMediaType *pmt);
	
	HRESULT CheckTransform(const CMediaType *mtIn, const CMediaType *mtOut);
	HRESULT DecideBufferSize(IMemAllocator * pima, ALLOCATOR_PROPERTIES * pProperties);

	HRESULT Transform(IMediaSample *pIn, IMediaSample *pOut);

	STDMETHODIMP GetPages(CAUUID * pPages);
	STDMETHODIMP FreePages(CAUUID * pPages);

  /* IMFTransform */
#if defined(XVID_USE_MFT) 
	STDMETHODIMP MFTGetStreamLimits(DWORD *pdwInputMinimum, DWORD *pdwInputMaximum, DWORD *pdwOutputMinimum, DWORD *pdwOutputMaximum);
	STDMETHODIMP MFTGetStreamCount(DWORD *pcInputStreams, DWORD *pcOutputStreams);
	STDMETHODIMP MFTGetStreamIDs(DWORD dwInputIDArraySize, DWORD *pdwInputIDs, DWORD dwOutputIDArraySize, DWORD *pdwOutputIDs);
	STDMETHODIMP MFTGetInputStreamInfo(DWORD dwInputStreamID, MFT_INPUT_STREAM_INFO *pStreamInfo);
	STDMETHODIMP MFTGetOutputStreamInfo(DWORD dwOutputStreamID, MFT_OUTPUT_STREAM_INFO *pStreamInfo);
	STDMETHODIMP GetAttributes(IMFAttributes** pAttributes);
	STDMETHODIMP GetInputStreamAttributes(DWORD dwInputStreamID, IMFAttributes **ppAttributes);
	STDMETHODIMP GetOutputStreamAttributes(DWORD dwOutputStreamID, IMFAttributes **ppAttributes);
	STDMETHODIMP MFTDeleteInputStream(DWORD dwStreamID);
	STDMETHODIMP MFTAddInputStreams(DWORD cStreams, DWORD *adwStreamIDs);
	STDMETHODIMP MFTGetInputAvailableType(DWORD dwInputStreamID, DWORD dwTypeIndex, IMFMediaType **ppType);
	STDMETHODIMP MFTGetOutputAvailableType(DWORD dwOutputStreamID, DWORD dwTypeIndex, IMFMediaType **ppType);
	STDMETHODIMP MFTSetInputType(DWORD dwInputStreamID, IMFMediaType *pType, DWORD dwFlags);
	STDMETHODIMP MFTSetOutputType(DWORD dwOutputStreamID, IMFMediaType *pType, DWORD dwFlags);
	STDMETHODIMP MFTGetInputCurrentType(DWORD dwInputStreamID, IMFMediaType **ppType);
	STDMETHODIMP MFTGetOutputCurrentType(DWORD dwOutputStreamID, IMFMediaType **ppType);
	STDMETHODIMP MFTGetInputStatus(DWORD dwInputStreamID, DWORD *pdwFlags);
	STDMETHODIMP MFTGetOutputStatus(DWORD *pdwFlags);
	STDMETHODIMP MFTSetOutputBounds(LONGLONG hnsLowerBound, LONGLONG hnsUpperBound);
	STDMETHODIMP MFTProcessEvent(DWORD dwInputStreamID, IMFMediaEvent *pEvent);
	STDMETHODIMP MFTProcessMessage(MFT_MESSAGE_TYPE eMessage, ULONG_PTR ulParam);

	STDMETHODIMP MFTProcessInput(DWORD dwInputStreamID, IMFSample *pSample, DWORD dwFlags);
	STDMETHODIMP MFTProcessOutput(DWORD dwFlags, DWORD cOutputBufferCount, MFT_OUTPUT_DATA_BUFFER *pOutputSamples, DWORD *pdwStatus);
#endif  /* XVID_USE_MFT */

private :

	HRESULT ChangeColorspace(GUID subtype, GUID formattype, void * format, int noflip);
	HRESULT OpenLib();
	void CloseLib();

	xvid_dec_create_t m_create;
	xvid_dec_frame_t m_frame;

	HINSTANCE m_hdll;
	int (*xvid_global_func)(void *handle, int opt, void *param1, void *param2);
	int (*xvid_decore_func)(void *handle, int opt, void *param1, void *param2);
	int ar_x, ar_y;
	bool forced_ar;

	int rgb_flip;
	int out_stride;

	/* mft stuff */
#if defined(XVID_USE_MFT)
	BOOL HasPendingOutput() const { return m_frame.output.plane[1] != NULL; }

	HRESULT OnSetInputType(IMFMediaType *pmt);
	HRESULT OnCheckInputType(IMFMediaType *pmt);

	HRESULT OnSetOutputType(IMFMediaType *pmt);

	IMFMediaType *m_pInputType;
	IMFMediaType *m_pOutputType;

	CRITICAL_SECTION m_mft_lock;
	REFERENCE_TIME m_timestamp;
	REFERENCE_TIME m_timelength;

	int m_discont;

	/* Used to construct or interpolate missing timestamps */
	REFERENCE_TIME m_rtFrame;
	MFRatio m_frameRate;
	UINT64 m_duration;
#endif

	HWND MSG_hwnd; /* message handler window */
};
#define WM_ICONMESSAGE (WM_USER + 1)

static const int PARS[][2] = {
	{1, 1},
	{12, 11},
	{10, 11},
	{16, 11},
	{40, 33},
	{0, 0},
};

#endif /* _FILTER_H_ */
