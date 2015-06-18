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
// A custom interface to allow the user to modify audio
// encoder properties
#ifndef __IAUDIOPROPERTIES__
#define __IAUDIOPROPERTIES__
#ifdef __cplusplus
extern "C" {
#endif
	// {BB54F99E-99FC-4ac4-8D75-661C9B8D921B}
	DEFINE_GUID(IID_IAudioEncoderProperties, 
	0xbb54f99e, 0x99fc, 0x4ac4, 0x8d, 0x75, 0x66, 0x1c, 0x9b, 0x8d, 0x92, 0x1b);
	//
	// Configuring MPEG audio encoder parameters with unspecified
	// input stream type may lead to misbehaviour and confusing
	// results. In most cases the specified parameters will be
	// overridden by defaults for the input media type.
	// To archive proper results use this interface on the
	// audio encoder filter with input pin connected to the valid
	// source.
	//
    DECLARE_INTERFACE_(IAudioEncoderProperties, IUnknown)
    {
		// Get target compression bitrate in Kbits/s
        STDMETHOD(get_Bitrate) (THIS_
            DWORD *dwBitrate
        ) PURE;
		// Set target compression bitrate in Kbits/s
		// Not all numbers available! See spec for details!
        STDMETHOD(set_Bitrate) (THIS_
            DWORD dwBitrate
        ) PURE;
		// Get source sample rate. Return E_FAIL if input pin
		// in not connected.
		STDMETHOD(get_SourceSampleRate) (THIS_
            DWORD *dwSampleRate
        ) PURE;
		// Get source number of channels. Return E_FAIL if
		// input pin is not connected.
		STDMETHOD(get_SourceChannels) (THIS_
            DWORD *dwChannels
        ) PURE;
		// Get sample rate for compressed audio bitstream
        STDMETHOD(get_SampleRate) (THIS_
            DWORD *dwSampleRate
        ) PURE;
		// Set sample rate. See genaudio spec for details
        STDMETHOD(set_SampleRate) (THIS_
            DWORD dwSampleRate
        ) PURE;
		// Get channel mode. See genaudio.h for details
        STDMETHOD(get_ChannelMode) (THIS_
            DWORD *dwChannelMode
        ) PURE;
		// Set channel mode
        STDMETHOD(set_ChannelMode) (THIS_
            DWORD dwChannelMode
        ) PURE;
		// Is CRC enabled?
        STDMETHOD(get_CRCFlag) (THIS_
            DWORD *dwFlag
        ) PURE;
		// Enable/disable CRC
        STDMETHOD(set_CRCFlag) (THIS_
            DWORD dwFlag
        ) PURE;
		// Control 'original' flag
        STDMETHOD(get_OriginalFlag) (THIS_
            DWORD *dwFlag
        ) PURE;
        STDMETHOD(set_OriginalFlag) (THIS_
            DWORD dwFlag
			) PURE;
		// Control 'copyright' flag
        STDMETHOD(get_CopyrightFlag) (THIS_
            DWORD *dwFlag
        ) PURE;
        STDMETHOD(set_CopyrightFlag) (THIS_
            DWORD dwFlag
        ) PURE;
		//Receive the block of encoder 
		//configuration parametres
        STDMETHOD(get_ParameterBlockSize) (THIS_
            BYTE *pcBlock, DWORD *pdwSize
        ) PURE;
		// Set encoder configuration parametres
        STDMETHOD(set_ParameterBlockSize) (THIS_
            BYTE *pcBlock, DWORD dwSize
        ) PURE;
		// Set default audio encoder parameters depending
		// on current input stream type
        STDMETHOD(DefaultAudioEncoderProperties) (THIS_
        ) PURE;
		// Determine, whether the filter can be configured. If this
		// functions returs E_FAIL, input format hasn't been
		// specified and filter behavior unpredicated. If S_OK,
		// the filter could be configured with correct values.
		STDMETHOD(InputTypeDefined) (THIS_
		) PURE;
		// Get audio delay to start off with in samples
        STDMETHOD(get_InitialAudioDelay) (THIS_
            LONGLONG *llAudioDelay
        ) PURE;
		// Set audio delay in samples
        STDMETHOD(set_InitialAudioDelay) (THIS_
            LONGLONG llAudioDelay
        ) PURE;

    };
#ifdef __cplusplus
}
#endif
#endif // __IAUDIOPROPERTIES__



