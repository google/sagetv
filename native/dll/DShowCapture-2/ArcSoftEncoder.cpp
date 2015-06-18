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
#include "../../include/guids.h"
#include "DShowCapture.h"
#include "DShowUtilities.h"
#include "ArcSoftEncoder.h"
#include "ArcSoft/BaseObject.h"
#include "ArcSoft/IMpeg2EncParams.h"

#include "uniapi.h"

DEFINE_GUID( CLSID_ArcSoft_Media_Center_Muxer,
0xB045EC97, 0x0F0A, 0x4C83, 0xA9, 0x04, 0x3D, 0x87, 0x6F, 0x75, 0x83, 0x47);

DEFINE_GUID( CLSID_ArcSoft_Mpeg2Audio_Encoder,
0xC6CF6703, 0x29E5, 0x4E29, 0xB0, 0xFB, 0xAB, 0xDF, 0x8D, 0x04, 0xA0, 0x0C);

DEFINE_GUID( CLSID_Video_Encoder_Pro, 
0xe33700c3, 0x9849, 0x4c08, 0xb1, 0x2b, 0x4b, 0xd0, 0x22, 0x6e, 0x33, 0xf1);

BOOL configureArcSoftAudioEncoder(IBaseFilter* pAudioEnc, SageTVMPEG2EncodingParameters *pythonParams, JNIEnv *env)
{

	VARIANT Value;
	HRESULT hr = S_OK;
	//hr = pAudioEnc->QueryInterface(IID_IModuleConfig, (void**)&pArcSoftProps);
	if (SUCCEEDED(hr))
	{
		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = 1;
		switch (pythonParams->audiobitrate)
		{
			case 32:
				Value.intVal = 1; //L2_AUDIOBITRATE32; 
				break;
			case 48:
				Value.intVal = 2; //L2_AUDIOBITRATE48;
				break;
			case 56:
				Value.intVal = 3; //L2_AUDIOBITRATE56;
				break;
			case 64:
				Value.intVal = 4;//L2_AUDIOBITRATE64;
				break;
			case 80:
				Value.intVal = 5;//L2_AUDIOBITRATE80;
				break;
			case 96:
				Value.intVal = 6;//L2_AUDIOBITRATE96;
				break;
			case 112:
				Value.intVal = 7;//L2_AUDIOBITRATE112;
				break;
			case 128:
				Value.intVal = 8;//L2_AUDIOBITRATE128;
				break;
			case 160:
				Value.intVal = 9;//L2_AUDIOBITRATE160;
				break;
			case 192:
				Value.intVal = 10;//L2_AUDIOBITRATE192;
				break;
			case 224:
				Value.intVal = 11;//L2_AUDIOBITRATE224;
				break;
			case 256:
				Value.intVal = 12;//L2_AUDIOBITRATE256;
				break;
			case 320:
				Value.intVal = 13;//L2_AUDIOBITRATE320;
				break;
			default:
				Value.intVal = 14;//L2_AUDIOBITRATE384;
				break;
		}

		//hr = pArcSoftProps->SetValue( &EL2ENC_BITRATE_MPEG2, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure audio bit rate  %d hr=0x%x\r\n", Value.intVal, hr ));

		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = pythonParams->audiocrc ? 1 : 0;
		//hr = pArcSoftProps->SetValue( &EMC_CRC_PROTECTION, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video  %d hr=0x%x\r\n", Value.intVal, hr ));

		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = pythonParams->audiooutputmode;
		switch (pythonParams->audiooutputmode)
		{
			case 1:
				Value.intVal = 1; //HCW_Aud_Output_JointStereo;
				break;
			case 2:
				Value.intVal = 2;// HCW_Aud_Output_DualChannel;
				break;
			case 3:
				Value.intVal = 3; //HCW_Aud_Output_Mono;
				break;
			default:
				Value.intVal = 0; //HCW_Aud_Output_Stereo;
				break;
		}
		//hr = pArcSoftProps->SetValue( &EMC_JOINT_CODING, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure audio mode  %d hr=0x%x\r\n", Value.intVal, hr ));
		
		return TRUE;

		//SAFE_RELEASE(pArcSoftProps);
	} else
		slog((env, "failed Performing MC 2.0 MPEG audio configuration\r\n"));


	return FALSE;
}


BOOL configureArcSoftVideoEncoder(DShowCaptureInfo* pCapInfo, IBaseFilter* pVidEnc, 
							SageTVMPEG2EncodingParameters *pythonParams, JNIEnv *env)
{
	BOOL ret = FALSE;
	MPEG2VideoParam Mpge2VideoParam;
	IEncodeFilter *pEncodeInterface;
	slog((env, "Performing ArcSoft 1.0 MPEG video configuration\r\n"));
	HRESULT hr = pVidEnc->QueryInterface(IID_IEncodeFilter, (void**)&pEncodeInterface);
	if (SUCCEEDED(hr))
	{
		Mpge2VideoParam.ulSize = sizeof(Mpge2VideoParam);
		Mpge2VideoParam.ulParamFormat = ENCVIDEO_PARAM;
		
		//1 .setup Mpeg Type
		switch (pythonParams->outputstreamtype)
		{
			case 0:
				Mpge2VideoParam.video_format_type = ENCODE_MPEG2;
				break;
			case 2:
				Mpge2VideoParam.video_format_type = ENCODE_MPEG1;
				break;
			case 11:
				Mpge2VideoParam.video_format_type = ENCODE_VCD;
				break;
			case 12:
				Mpge2VideoParam.video_format_type = ENCODE_SVCD;
				break;
			default:
				Mpge2VideoParam.video_format_type = ENCODE_DVD;
				break;
		} 
		hr = pEncodeInterface->GetDefaultEncoderParams( &Mpge2VideoParam );
		if ( SUCCEEDED(hr))
		{	

			if ( IsNTSCVideoCode(pCapInfo->videoFormatCode) )
				Mpge2VideoParam.video_format = 2;
			else 
			if ( IsPALVideoCode(pCapInfo->videoFormatCode) )
				Mpge2VideoParam.video_format = 1;
			else
			if ( IsSECAMVideoCode(pCapInfo->videoFormatCode) )
				Mpge2VideoParam.video_format = 3;
			else
				Mpge2VideoParam.video_format = FORMAT_NTSC;
		
			if ( (int)pythonParams->ipb > 0 )
				Mpge2VideoParam.frames_in_subgroup = (int)pythonParams->ipb;
			else
				Mpge2VideoParam.frames_in_subgroup = 3;

			Mpge2VideoParam.frames_in_group = (int)pythonParams->gopsize;
			Mpge2VideoParam.closed_gop = !(int)pythonParams->closedgop;

			if ( pythonParams->vbr )
				Mpge2VideoParam.bitrate_mode = MPEG2_VBR;
			else
				Mpge2VideoParam.bitrate_mode = MPEG2_CBR;

			Mpge2VideoParam.vbr_maximum_bit_rate = pythonParams->peakvideobitrate>>10;
			Mpge2VideoParam.vbr_average_bit_rate = pythonParams->videobitrate>>10;
			Mpge2VideoParam.target_bit_rate = pythonParams->videobitrate>>10;

			Mpge2VideoParam.prog_frame = !pythonParams->deinterlace;
			Mpge2VideoParam.userData = "";
			Mpge2VideoParam.userData_length = 0;
			
			// Based on support feedback; we need to allow people to change this sometimes
			Mpge2VideoParam.topfirst = GetRegistryDword(HKEY_LOCAL_MACHINE, 
				   "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegEnc", "TopFieldFirst", Mpge2VideoParam.topfirst );

			hr = pEncodeInterface->SetEncoderParams( &Mpge2VideoParam );
			if ( SUCCEEDED(hr))
				ret = TRUE;
		}			
		SAFE_RELEASE( pEncodeInterface );
		if ( !ret )
			slog((env, "Failed performing ArcSoft 2.0 MPEG video configuration, hr=0x%x\r\n", hr));
		return ret;
	} else
		slog((env, "Failed performing ArcSoft 2.0 MPEG video configuration, not get interface, hr=0x%x\r\n", hr));

	return ret;

}

