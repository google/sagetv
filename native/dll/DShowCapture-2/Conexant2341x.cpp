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
#include "DShowCapture.h"
#include "DShowUtilities.h"
#include "../../include/sage_DShowCaptureDevice.h"
#include "Conexant2341x.h"
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "../../include/Conexant/iVacCtrlProp.h"
#include "../../include/Conexant/ivactypes.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */

void configureConexant2341xEncoder(DShowCaptureInfo* pCapInfo, SageTVMPEG2EncodingParameters *pythonParams,
								   BOOL set656Res, JNIEnv *env)
{
	slog((env, "configureConexant2341xEncoder is running for '%s-%d'\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum));
	HRESULT hr;
	jboolean blackbird = capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BLACKBIRD_CAPTURE_MASK);
	IKsPropertySet *pIKsBridgePropSet = NULL;
	if (pCapInfo->pEncoder)
		hr = pCapInfo->pEncoder->QueryInterface(IID_IKsPropertySet, (PVOID *) &pIKsBridgePropSet);
	else
		hr = pCapInfo->pVideoCaptureFilter->QueryInterface(IID_IKsPropertySet, (PVOID *) &pIKsBridgePropSet);
	if (SUCCEEDED(hr))
	{
    /*
     * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
		if (blackbird)
		{
			BB_VIDEO_FRAME_RATE frameRate = BB_FRAME_RATE_30;
			BB_VIDEO_RESOLUTION vidres = BB_RESOLUTION_720_480;
			if (pythonParams->width == 720)
			{
				if (pythonParams->height == 576)
				{
					vidres = BB_RESOLUTION_720_576;
					frameRate = BB_FRAME_RATE_25;
				}
				else
				{
					vidres = BB_RESOLUTION_720_480;
					frameRate = BB_FRAME_RATE_30;
				}
			}
			else if (pythonParams->width == 480)
			{
				if (pythonParams->height == 576)
				{
					vidres = BB_RESOLUTION_480_576;
					frameRate = BB_FRAME_RATE_25;
				}
				else
				{
					vidres = BB_RESOLUTION_480_480;
					frameRate = BB_FRAME_RATE_30;
				}
			}
			else if (pythonParams->width == 352)
			{
				if (pythonParams->height == 288)
				{
					vidres = BB_RESOLUTION_352_288;
					frameRate = BB_FRAME_RATE_25;
				}
				else
				{
					vidres = BB_RESOLUTION_352_240;
					frameRate = BB_FRAME_RATE_30;
				}
			}

			hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
										BB_IVAC_VIDEO_FRAME_RATE,
										&frameRate,
										sizeof(BB_VIDEO_FRAME_RATE), 
										&frameRate, sizeof(BB_VIDEO_FRAME_RATE));
			TEST_AND_PRINT
			// Resolution for the blackbird is done by configuring
			// the connection on the 656 pins instead
			if (!set656Res)
			{
				hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
											BB_IVAC_VIDEO_RESOLUTION,
											&vidres,
											sizeof(VIDEO_RESOLUTION), 
											&vidres, sizeof(VIDEO_RESOLUTION));
				TEST_AND_PRINT
			}
		}
		else
		{
			VIDEO_RESOLUTION vidres = PROP_VIDEORESOLUTION_720x480;
			// height doesn't differ in the enum; only width
			if (pythonParams->width == 720)
				vidres = PROP_VIDEORESOLUTION_720x480;
			else if (pythonParams->width == 480)
				vidres = PROP_VIDEORESOLUTION_480x480;
			else if (pythonParams->width == 352)
				vidres = PROP_VIDEORESOLUTION_352x480;
			else if (pythonParams->width == 320)
				vidres = PROP_VIDEORESOLUTION_320x240;
			//slog((env, "IVAC15 property setting of resolution to %d\r\n", vidres));

			// Resolution for the blackbird is done by configuring
			// the connection on the 656 pins instead
			if (!set656Res)
			{
				hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
											IVAC_VIDEO_RESOLUTION, 
											&vidres,
											sizeof(VIDEO_RESOLUTION), 
											&vidres, sizeof(VIDEO_RESOLUTION));
				TEST_AND_PRINT
			}
		}

		if (blackbird)
		{
			BB_VIDEO_BITRATE vrate;
			vrate.encoding_mode = pythonParams->vbr ? 
					BB_VIDEOENCODINGMODE_VARIABLE : BB_VIDEOENCODINGMODE_CONSTANT;
			vrate.bit_rate = pythonParams->videobitrate;
			vrate.peak = max(pythonParams->peakvideobitrate, pythonParams->videobitrate);
			//slog((env, "IVAC16 property setting of video rate to %d peak to %d and vbr %d\r\n", 
			//	vrate.bit_rate, vrate.peak, vrate.encoding_mode));
			hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
										BB_IVAC_BITRATE, 
										&vrate,
										sizeof(BB_VIDEO_BITRATE), 
										&vrate, sizeof(BB_VIDEO_BITRATE));
		}
		else
		{
			VIDEO_BITRATE vrate;
			vrate.bEncodingMode = pythonParams->vbr ? 
					RC_VIDEOENCODINGMODE_VBR : RC_VIDEOENCODINGMODE_CBR;
			vrate.wBitrate = (unsigned short) (pythonParams->videobitrate/2500);
			if (pythonParams->peakvideobitrate >= pythonParams->videobitrate)
				vrate.dwPeak = (unsigned short) (pythonParams->peakvideobitrate/400);
			else
			{
				vrate.dwPeak = (DWORD)((vrate.wBitrate/400) * 1500000);
				vrate.dwPeak /= 400;
			}
			//slog((env, "IVAC15 property setting of video rate to %d peak to %d and vbr %d\r\n", 
			//	vrate.wBitrate*2500, vrate.dwPeak*400, vrate.bEncodingMode));
			hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
										IVAC_BITRATE, 
										&vrate,
										sizeof(VIDEO_BITRATE), 
										&vrate, sizeof(VIDEO_BITRATE));
		}
		TEST_AND_PRINT

		// Set the audio parameters too
		SAMPLING_RATE asamp;
		if (pythonParams->audiosampling == 44100)
			asamp = PROP_AUDIOSAMPLINGRATE_44;
		else if (pythonParams->audiosampling == 48000)
			asamp = PROP_AUDIOSAMPLINGRATE_48;
		else
			asamp = PROP_AUDIOSAMPLINGRATE_32;

		hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
									blackbird ? BB_IVAC_AUDIO_SAMPLING_RATE : IVAC_SAMPLING_RATE, 
									&asamp,
									sizeof(SAMPLING_RATE), 
									&asamp, sizeof(SAMPLING_RATE));
		TEST_AND_PRINT

		if (blackbird)
		{
			BYTE arate = BB_DATA_RATE_384;
			if (pythonParams->audiobitrate == 192)
				arate = BB_DATA_RATE_192;
			else if (pythonParams->audiobitrate == 224)
				arate = BB_DATA_RATE_224;
			else if (pythonParams->audiobitrate == 256)
				arate = BB_DATA_RATE_256;
			else if (pythonParams->audiobitrate == 320)
				arate = BB_DATA_RATE_320;
			else 
				arate = BB_DATA_RATE_384;
			hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
										BB_IVAC_AUDIO_DATARATE, 
										&arate,
										sizeof(BYTE), 
										&arate, sizeof(BYTE));
		}
		else
		{
			BYTE arate = DATARATE_LAYERII_384;
			if (pythonParams->audiobitrate == 192)
				arate = DATARATE_LAYERII_192;
			else if (pythonParams->audiobitrate == 224)
				arate = DATARATE_LAYERII_224;
			else if (pythonParams->audiobitrate == 256)
				arate = DATARATE_LAYERII_256;
			else 
				arate = DATARATE_LAYERII_384;
			hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
										IVAC_AUDIO_DATARATE, 
										&arate,
										sizeof(BYTE), 
										&arate, sizeof(BYTE));
		}
		TEST_AND_PRINT

		AUDIO_OUTPUT_MODE aoutput = (AUDIO_OUTPUT_MODE) pythonParams->audiooutputmode;
		hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
									blackbird ? BB_IVAC_AUDIO_OUTPUT_MODE : IVAC_AUDIO_OUTPUT_MODE, 
									&aoutput,
									sizeof(AUDIO_OUTPUT_MODE),
									&aoutput, sizeof(AUDIO_OUTPUT_MODE));
		TEST_AND_PRINT

		AUDIO_CRC acrc = (AUDIO_CRC) pythonParams->audiocrc;
		hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
			blackbird ? BB_IVAC_AUDIO_CRC : IVAC_AUDIO_CRC, 
			&acrc,
			sizeof(AUDIO_CRC),
			&acrc, sizeof(AUDIO_CRC));
		TEST_AND_PRINT

		DWORD tempdw = (DWORD) pythonParams->inversetelecine;
		hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
			blackbird ? BB_IVAC_INVERSE_TELECINE : IVAC_INVERSE_TELECINE, 
			&tempdw,
			sizeof(DWORD),
			&tempdw, sizeof(DWORD));
		TEST_AND_PRINT

		tempdw = (DWORD) pythonParams->closedgop;
		hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
			blackbird ? BB_IVAC_CLOSED_GOP : IVAC_CLOSED_GOP, 
			&tempdw,
			sizeof(DWORD),
			&tempdw, sizeof(DWORD));
		TEST_AND_PRINT

		BYTE tempb = (BYTE) pythonParams->gopsize;
		hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
			blackbird ? BB_IVAC_GOP_SIZE : IVAC_GOP_SIZE, 
			&tempb,
			sizeof(BYTE),
			&tempb, sizeof(BYTE));
		if (FAILED(hr))
		{
			// Try the other way of doing GOP
			DWORD area[2];
			area[0] = pythonParams->gopsize;
			area[1] = (area[0] == 6) ? 2 : 3;
			hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
				blackbird ? BB_IVAC_GOP_SIZE : IVAC_GOP_SIZE, 
				&area,
				sizeof(area),
				&area, sizeof(area));
		}
		TEST_AND_PRINT
		
		OUTPUT_TYPE streamType = (OUTPUT_TYPE) pythonParams->outputstreamtype;
		// Remove any non-MPEG2 formats
		if (streamType > 50 || streamType == 12) //SVCD is 12
			streamType = (OUTPUT_TYPE)0;
		hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
			blackbird ? BB_IVAC_OUTPUT_TYPE : IVAC_OUTPUT_TYPE, 
			&streamType,
			sizeof(OUTPUT_TYPE), 
			&streamType, sizeof(OUTPUT_TYPE));
		TEST_AND_PRINT

		// Prefiltering settings
		if (!blackbird)
		{
			PREFILTER_INFO prefilterInf;
			prefilterInf.dwDisableFilter = pythonParams->disablefilter;
			prefilterInf.dwMedianFilter = pythonParams->medianfilter;
			prefilterInf.dwMedianCoringLumaHi = 0;
			prefilterInf.dwMedianCoringLumaLo = 255;
			prefilterInf.dwMedianCoringChromaHi = 0;
			prefilterInf.dwMedianCoringChromaLo = 255;
			prefilterInf.dwLumaSpatialFlt = SPATIAL_FILTER_LUMA_2D_HV_SEPRABLE;
			prefilterInf.dwChromaSpatialFlt = SPATIAL_FILTER_CHROMA_1D_HORIZ;
			prefilterInf.dwDNRMode = DYNAMIC_TEMPORAL_DYNAMIC_SPATIAL;
			prefilterInf.dwDNRSpatialFltLevel = 0;
			prefilterInf.dwDNRTemporalFltLevel = 0;
			prefilterInf.dwDNRSmoothFactor = 200;
			prefilterInf.dwDNR_Ntlf_Max_Y = 15;
			prefilterInf.dwDNR_Ntlf_Max_UV = 15;
			prefilterInf.dwDNRTemporalMultFactor = 48;
			prefilterInf.dwDNRTemporalAddFactor = 4;
			prefilterInf.dwDNRSpatialMultFactor = 21;
			prefilterInf.dwDNRSpatialSubFactor = 2;
			prefilterInf.dwLumaNLTFLevel = 0;
			prefilterInf.dwLumaNLTFCoEffIndex = 0;
			prefilterInf.dwLumaNLTFCoEffValue = 0;
			prefilterInf.dwVIMZoneHeight = 2;
			prefilterInf.dwVIMZoneWidth = 16; // fixed
			prefilterInf.dwReserved1 = 0;
			prefilterInf.dwReserved2 = 0;
			prefilterInf.dwReserved3 = 0;
			prefilterInf.dwReserved4 = 0;
			hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
				IVAC_PREFILTER_SETTINGS, 
				&prefilterInf,
				sizeof(PREFILTER_INFO), 
				&prefilterInf, sizeof(PREFILTER_INFO));
			TEST_AND_PRINT
		}
		else
		{
			DNR_PARAMETERS prefilterInf;
			prefilterInf.mode = DNR_MODE_DISABLED;
			prefilterInf.is_static_temporal = 0;
			prefilterInf.is_static_spatial = 0;
			prefilterInf.temporal_level = 0;
			prefilterInf.spatial_level = 0;
			prefilterInf.luma_low = 255;
			prefilterInf.luma_high = 0;
			prefilterInf.chroma_low = 255;
			prefilterInf.chroma_high = 0;
			if (!pythonParams->disablefilter)
			{
				prefilterInf.mode = (BB_DNR_MODE) pythonParams->medianfilter;
			}
			hr = pIKsBridgePropSet->Set(PROPSETID_IVAC_PROPERTIES, 
				BB_IVAC_DNR_PARAMETERS, 
				&prefilterInf,
				sizeof(DNR_PARAMETERS), 
				&prefilterInf, sizeof(DNR_PARAMETERS));
			TEST_AND_PRINT
		}
     * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
     */

		if (pIKsBridgePropSet)
			SAFE_RELEASE(pIKsBridgePropSet);

	}
}