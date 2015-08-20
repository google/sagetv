/*
 *	MPEG Audio Encoder for DirectShow
 *
 *	Copyright (c) 2000 Marie Orlova, Peter Gubanov, Elecard Ltd.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */
#include <mmreg.h>
//#include "lame.h"
#include "Encoder2.h"	// Added by ClassView

#define	VALUE_BITRATE				"Bitrate"
#define	VALUE_SAMPLE_RATE			"Sample Rate"

#define	VALUE_STEREO_MODE			"Stereo Mode"

#define	VALUE_LAYER					"Layer"
#define	VALUE_ORIGINAL				"Original"
#define	VALUE_COPYRIGHT				"Copyright"
#define	VALUE_CRC					"CRC"
#define	VALUE_PES					"PES"

#define VALUE_MODE_FIXED            "Mode Fixed"

///////////////////////////////////////////////////////////////////
// CMpegAudEnc class - implementation for ITransformFilter interface
///////////////////////////////////////////////////////////////////
class CMpegAudEnc : public CTransformFilter,
//					public ISpecifyPropertyPages,
					public IAudioEncoderProperties,
//					public IPersistPropertyBag,
					public CPersistStream
{
public:
	DECLARE_IUNKNOWN

    static CUnknown *CreateInstance(LPUNKNOWN lpunk, HRESULT *phr);

	LPAMOVIESETUP_FILTER GetSetupData();

	// ITransformFilter interface methods
	HRESULT Transform(IMediaSample * pIn, IMediaSample *pOut);    
    HRESULT CheckInputType(const CMediaType* mtIn);    
    HRESULT CheckTransform(const CMediaType* mtIn, const CMediaType* mtOut);    
    HRESULT DecideBufferSize(IMemAllocator * pAllocator,
							 ALLOCATOR_PROPERTIES *pprop);
    HRESULT GetMediaType  (int iPosition, CMediaType *pMediaType);
	HRESULT SetMediaType  (PIN_DIRECTION direction,const CMediaType *pmt);	


	//
	HRESULT StartStreaming();	
	HRESULT EndOfStream();
	HRESULT BeginFlush();

	~CMpegAudEnc(void);

	// ISpecifyPropertyPages
	STDMETHODIMP NonDelegatingQueryInterface(REFIID riid, void ** ppv);    

    STDMETHODIMP get_MPEGLayer(DWORD *dwLayer);
    STDMETHODIMP set_MPEGLayer(DWORD dwLayer);

    STDMETHODIMP get_Bitrate(DWORD *dwBitrate);
    STDMETHODIMP set_Bitrate(DWORD dwBitrate);   
    STDMETHODIMP get_SourceSampleRate(DWORD *dwSampleRate);
	STDMETHODIMP get_SourceChannels(DWORD *dwChannels);
    STDMETHODIMP get_SampleRate(DWORD *dwSampleRate);
    STDMETHODIMP set_SampleRate(DWORD dwSampleRate);

    STDMETHODIMP get_ChannelMode(DWORD *dwChannelMode);
    STDMETHODIMP set_ChannelMode(DWORD dwChannelMode);

	STDMETHODIMP get_CRCFlag(DWORD *dwFlag);
    STDMETHODIMP set_CRCFlag(DWORD dwFlag);
    STDMETHODIMP get_OriginalFlag(DWORD *dwFlag);
    STDMETHODIMP set_OriginalFlag(DWORD dwFlag);
    STDMETHODIMP get_CopyrightFlag(DWORD *dwFlag);
    STDMETHODIMP set_CopyrightFlag(DWORD dwFlag);

	STDMETHODIMP get_ParameterBlockSize(BYTE *pcBlock, DWORD *pdwSize);
    STDMETHODIMP set_ParameterBlockSize(BYTE *pcBlock, DWORD dwSize);

	STDMETHODIMP get_InitialAudioDelay(LONGLONG* llAudioDelay);
	STDMETHODIMP set_InitialAudioDelay(LONGLONG llAudioDelay);

    STDMETHODIMP DefaultAudioEncoderProperties();
	STDMETHODIMP InputTypeDefined();

    // IPersistPropertyBag methods
//    STDMETHODIMP InitNew();
//    STDMETHODIMP Load(LPPROPERTYBAG pPropBag, LPERRORLOG pErrorLog);
//    STDMETHODIMP Save(LPPROPERTYBAG pPropBag, BOOL fClearDirty, BOOL fSaveAllProperties);

    // CPersistStream
    HRESULT WriteToStream(IStream *pStream);
    HRESULT ReadFromStream(IStream *pStream);
    int SizeMax();
    STDMETHODIMP GetClassID(CLSID *pClsid);

private:
	CMpegAudEnc(LPUNKNOWN lpunk, HRESULT *phr);

	void		SetOutMediaType();

	// Encoder object
	CEncoder2			m_VEncoder;

	// Current media out format
	MPEG1WAVEFORMAT		m_mwf;

	//
	// Sample times
	//
	REFERENCE_TIME      m_rtLast;
	LONGLONG compensatedTime;
	BOOL m_bCorrectAVSync;
};


