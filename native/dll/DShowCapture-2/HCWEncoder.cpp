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
#include "HCWEncoder.h"
#include "../../../third_party/Hauppauge/hcwECP.h"

void configureHCWEncoder(DShowCaptureInfo* pCapInfo, SageTVMPEG2EncodingParameters *pythonParams,
								   BOOL set656Res, JNIEnv *env)
{
	slog((env, "configureHCWEncoder is running for '%s-%d'.\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum));
	HRESULT hr;
	DWORD dwTypeSupport;
	IKsPropertySet *pIKsBridgePropSet1 = NULL;
	IKsPropertySet *pIKsBridgePropSet2 = NULL;
	if (pCapInfo->pEncoder)
	{
		hr = pCapInfo->pEncoder->QueryInterface(IID_IKsPropertySet, (PVOID *) &pIKsBridgePropSet1);
		if (!SUCCEEDED(hr))
			SAFE_RELEASE(pIKsBridgePropSet1);
	}

	if ( pCapInfo->pVideoCaptureFilter )
	{
		hr = pCapInfo->pVideoCaptureFilter->QueryInterface(IID_IKsPropertySet, (PVOID *) &pIKsBridgePropSet2);
		if (!SUCCEEDED(hr))
			SAFE_RELEASE(pIKsBridgePropSet2);
	}

	if ( pIKsBridgePropSet1 == NULL && pIKsBridgePropSet2 == NULL )
	{
		slog((env, "configureHCWEncoder failed: IKsBridgePropSet interface not found for '%s-%d'\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum));
		return;
	}
	
	hr = E_FAIL;
	if ( pIKsBridgePropSet1 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet1->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_VIDEO_Horizontal_Res, &dwTypeSupport)))
		{
			HCW_Vid_HRes data;
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				switch (pythonParams->width)
				{
					case 640:
						data = HCW_Vid_HRes_640;
						break;
					case 352:
						data = HCW_Vid_HRes_HalfD1;
						break;
					case 320:
						data = HCW_Vid_HRes_320;
						break;
					case 480:
						data = HCW_Vid_HRes_TwoThirdsD1;
						break;
					default:
						data = HCW_Vid_HRes_FullD1;
						break;
				}
				slog((env, "configureHCWEncoder setup width %d on encoder\r\n", pythonParams->width ) );
				hr = pIKsBridgePropSet1->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_VIDEO_Horizontal_Res, &data, sizeof(HCW_Vid_HRes),
						&data, sizeof(HCW_Vid_HRes));
			}
		}
	}
	if ( !SUCCEEDED( hr ) && pIKsBridgePropSet2 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet2->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_VIDEO_Horizontal_Res, &dwTypeSupport)))
		{
			HCW_Vid_HRes data;
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				switch (pythonParams->width)
				{
					case 640:
						data = HCW_Vid_HRes_640;
						break;
					case 352:
						data = HCW_Vid_HRes_HalfD1;
						break;
					case 320:
						data = HCW_Vid_HRes_320;
						break;
					case 480:
						data = HCW_Vid_HRes_TwoThirdsD1;
						break;
					default:
						data = HCW_Vid_HRes_FullD1;
						break;
				}
				slog((env, "configureHCWEncoder setup width %d on VideoCapture Filter\r\n", pythonParams->width ) );
				hr = pIKsBridgePropSet2->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_VIDEO_Horizontal_Res, &data, sizeof(HCW_Vid_HRes),
						&data, sizeof(HCW_Vid_HRes));
			}
		}
	}
	if ( SUCCEEDED( hr ) )
		slog((env, "configureHCWEncoder setup width %d succeeded\r\n", pythonParams->width ) );
	else
		slog((env, "configureHCWEncoder setup width %d failed\r\n", pythonParams->width ) );


	hr = E_FAIL;
	if ( pIKsBridgePropSet1 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet1->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_SYSTEM_StreamType, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Sys_StreamType streamType;
				switch (pythonParams->outputstreamtype)
				{
					case 0:
						streamType = HCW_Sys_StreamType_Program;
						break;
					case 2:
						streamType = HCW_Sys_StreamType_MPEG1;
						break;
					case 11:
						streamType = HCW_Sys_StreamType_MPEG1_VCD;
						break;
					case 12:
						streamType = HCW_Sys_StreamType_Program_SVCD;
						break;
					default:
						streamType = HCW_Sys_StreamType_Program_DVD;
						break;
				}
				slog((env, "configureHCWEncoder setup ouputstream %d on encoder\r\n", pythonParams->outputstreamtype ));
				hr = pIKsBridgePropSet1->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_SYSTEM_StreamType, &streamType, sizeof(HCW_Sys_StreamType),
						&streamType, sizeof(HCW_Sys_StreamType));
			}
		}
	}
	if ( !SUCCEEDED( hr ) && pIKsBridgePropSet2 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet2->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_SYSTEM_StreamType, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Sys_StreamType streamType;
				switch (pythonParams->outputstreamtype)
				{
					case 0:
						streamType = HCW_Sys_StreamType_Program;
						break;
					case 2:
						streamType = HCW_Sys_StreamType_MPEG1;
						break;
					case 11:
						streamType = HCW_Sys_StreamType_MPEG1_VCD;
						break;
					case 12:
						streamType = HCW_Sys_StreamType_Program_SVCD;
						break;
					default:
						streamType = HCW_Sys_StreamType_Program_DVD;
						break;
				}
				slog((env, "configureHCWEncoder setup ouputstream %d on capture filter\r\n", pythonParams->outputstreamtype ));
				hr = pIKsBridgePropSet2->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_SYSTEM_StreamType, &streamType, sizeof(HCW_Sys_StreamType),
						&streamType, sizeof(HCW_Sys_StreamType));
			}
		}
	}
	if ( SUCCEEDED( hr ) )
		slog((env, "configureHCWEncoder setup ouputstream change %d succeeded\r\n", pythonParams->outputstreamtype ));
	else
		slog((env, "configureHCWEncoder setup support ouputstream change %d failed\r\n", pythonParams->outputstreamtype ));
		

	hr = E_FAIL;
	if ( pIKsBridgePropSet1 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet1->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_VIDEO_GOP_OpenClosed, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Vid_GOP_OpenClosed data = pythonParams->closedgop ? HCW_Vid_GOP_OC_Closed : HCW_Vid_GOP_OC_Open;
				slog((env, "configureHCWEncoder setup video GOP open/close %d on encoder filter\r\n", data ));
				hr = pIKsBridgePropSet1->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_VIDEO_GOP_OpenClosed, 
						&data, sizeof(HCW_Vid_GOP_OpenClosed), &data, sizeof(HCW_Vid_GOP_OpenClosed));
			}
		}
	}
	if ( !SUCCEEDED( hr ) && pIKsBridgePropSet2 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet2->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_VIDEO_GOP_OpenClosed, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Vid_GOP_OpenClosed data = pythonParams->closedgop ? HCW_Vid_GOP_OC_Closed : HCW_Vid_GOP_OC_Open;
				slog((env, "configureHCWEncoder setup video GOP open/close %d on video capture filter\r\n", data ));
				hr = pIKsBridgePropSet2->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_VIDEO_GOP_OpenClosed, 
						&data, sizeof(HCW_Vid_GOP_OpenClosed), &data, sizeof(HCW_Vid_GOP_OpenClosed));
			}
		}
	}
	if ( SUCCEEDED( hr ) )
		slog((env, "configureHCWEncoder setup video GOP open/close succeeded\r\n" ));
	else
		slog((env, "configureHCWEncoder setup video GOP open/close failed\r\n" ));

	hr = E_FAIL;
	if ( pIKsBridgePropSet1 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet1->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_VIDEO_BitRate, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Vid_BitRate data;
				data.dwSize = sizeof(HCW_Vid_BitRate);
				data.dwReserved = 0;
				data.Mode = pythonParams->vbr ? HCW_Vid_EncMode_VBR : HCW_Vid_EncMode_CBR;
				data.dwBitRate = pythonParams->videobitrate / 1000;
				data.dwBitRatePeak = pythonParams->peakvideobitrate / 1000;
				slog((env, "configureHCWEncoder setup BitRate %d mode:%d on HW econder\r\n", data.dwBitRate, data.Mode ));
				hr = pIKsBridgePropSet1->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_VIDEO_BitRate, 
						&data, sizeof(HCW_Vid_BitRate), &data, sizeof(HCW_Vid_BitRate));
			} 
		}  
	}
	if ( !SUCCEEDED( hr ) && pIKsBridgePropSet2 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet2->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_VIDEO_BitRate, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Vid_BitRate data;
				data.dwSize = sizeof(HCW_Vid_BitRate);
				data.dwReserved = 0;
				data.Mode = pythonParams->vbr ? HCW_Vid_EncMode_VBR : HCW_Vid_EncMode_CBR;
				data.dwBitRate = pythonParams->videobitrate / 1000;
				data.dwBitRatePeak = pythonParams->peakvideobitrate / 1000;
				slog((env, "configureHCWEncoder setup BitRate %d mode:%d on capture filter\r\n", data.dwBitRate, data.Mode ));
				hr = pIKsBridgePropSet2->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_VIDEO_BitRate, 
						&data, sizeof(HCW_Vid_BitRate), &data, sizeof(HCW_Vid_BitRate));
			} 
		}  
	}
	if ( SUCCEEDED( hr ) )
		slog((env, "configureHCWEncoder setup BitRate succeeded\r\n" ));
	else
		slog((env, "configureHCWEncoder setup BitRate change failed\r\n" ));

	hr = E_FAIL;
	if ( pIKsBridgePropSet1 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet1->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_VIDEO_GOP_Size, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Vid_GOPSize data;
				data.dwSize = sizeof(HCW_Vid_GOPSize);
				data.dwReserved = 0;
				data.dwNumPictures = pythonParams->gopsize;
				data.dwPFrameDistance = pythonParams->ipb == 2 ? 1 : 3;
				slog((env, "configureHCWEncoder setup GOP size %d on HW encoder filter\r\n", pythonParams->gopsize ));
				hr = pIKsBridgePropSet1->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_VIDEO_GOP_Size, 
						&data, sizeof(HCW_Vid_GOPSize), &data, sizeof(HCW_Vid_GOPSize));
			}
		}
	}
	if ( !SUCCEEDED( hr ) && pIKsBridgePropSet2 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet2->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_VIDEO_GOP_Size, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Vid_GOPSize data;
				data.dwSize = sizeof(HCW_Vid_GOPSize);
				data.dwReserved = 0;
				data.dwNumPictures = pythonParams->gopsize;
				data.dwPFrameDistance = pythonParams->ipb == 2 ? 1 : 3;
				slog((env, "configureHCWEncoder setup GOP size %d on capture filter\r\n", pythonParams->gopsize ));
				hr = pIKsBridgePropSet2->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_VIDEO_GOP_Size, 
						&data, sizeof(HCW_Vid_GOPSize), &data, sizeof(HCW_Vid_GOPSize));
			}
		}
	}
	if ( SUCCEEDED( hr ) )
		slog((env, "configureHCWEncoder setup GOP size succeeded\r\n" ));
	else
		slog((env, "configureHCWEncoder setup GOP size failed\r\n" ));

	hr = E_FAIL;
	if ( pIKsBridgePropSet1 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet1->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_VIDEO_InverseTelecine, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Vid_InverseTelecine data = pythonParams->inversetelecine ? HCW_Vid_InverseTelecine_On : 
					HCW_Vid_InverseTelecine_Off;
					slog((env, "configureHCWEncoder setup inverse telecine %d on HW encoder filter\r\n", pythonParams->inversetelecine ));
				hr = pIKsBridgePropSet1->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_VIDEO_InverseTelecine, 
						&data, sizeof(HCW_Vid_InverseTelecine), &data, sizeof(HCW_Vid_InverseTelecine));
			}
		}
	}
	if ( !SUCCEEDED( hr ) && pIKsBridgePropSet2 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet2->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_VIDEO_InverseTelecine, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Vid_InverseTelecine data = pythonParams->inversetelecine ? HCW_Vid_InverseTelecine_On : 
					HCW_Vid_InverseTelecine_Off;
					slog((env, "configureHCWEncoder setup inverse telecine %d on capture filter\r\n", pythonParams->inversetelecine ));
				hr = pIKsBridgePropSet2->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_VIDEO_InverseTelecine, 
						&data, sizeof(HCW_Vid_InverseTelecine), &data, sizeof(HCW_Vid_InverseTelecine));
				TEST_AND_PRINT
			}
		}
	}
	if ( SUCCEEDED( hr ) )
		slog((env, "configureHCWEncoder inverse telecine succeeded\r\n" ));
	else
		slog((env, "configureHCWEncoder inverse telecine failed\r\n" ));


	hr = E_FAIL;
	if ( pIKsBridgePropSet1 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet1->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_AUDIO_BitRate, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Aud_BitRate data;
				data.dwSize = sizeof(HCW_Aud_BitRate);
				data.dwReserved = 0;
				data.Layer = HCW_Aud_Layer_2;
				switch (pythonParams->audiobitrate)
				{
					case 32:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_32; 
						break;
					case 48:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_48;
						break;
					case 56:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_56;
						break;
					case 64:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_64;
						break;
					case 80:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_80;
						break;
					case 96:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_96;
						break;
					case 112:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_112;
						break;
					case 128:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_128;
						break;
					case 160:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_160;
						break;
					case 192:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_192;
						break;
					case 224:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_224;
						break;
					case 256:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_256;
						break;
					case 320:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_320;
						break;
					default:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_384;
						break;
				}
				slog((env, "configureHCWEncoder setup audio BitRate %d on HW encoder\r\n", pythonParams->audiobitrate ));
				hr = pIKsBridgePropSet1->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_AUDIO_BitRate, 
						&data, sizeof(HCW_Aud_BitRate), &data, sizeof(HCW_Aud_BitRate));
				
			}
		}
	}
	if ( !SUCCEEDED( hr ) && pIKsBridgePropSet2 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet2->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_AUDIO_BitRate, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Aud_BitRate data;
				data.dwSize = sizeof(HCW_Aud_BitRate);
				data.dwReserved = 0;
				data.Layer = HCW_Aud_Layer_2;
				switch (pythonParams->audiobitrate)
				{
					case 32:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_32; 
						break;
					case 48:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_48;
						break;
					case 56:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_56;
						break;
					case 64:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_64;
						break;
					case 80:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_80;
						break;
					case 96:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_96;
						break;
					case 112:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_112;
						break;
					case 128:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_128;
						break;
					case 160:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_160;
						break;
					case 192:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_192;
						break;
					case 224:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_224;
						break;
					case 256:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_256;
						break;
					case 320:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_320;
						break;
					default:
						data.dwBitRate = HCW_Aud_BitRate_Layer2_384;
						break;
				}
				slog((env, "configureHCWEncoder setup audio BitRate %d on Capture filter\r\n", pythonParams->audiobitrate ));
				hr = pIKsBridgePropSet2->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_AUDIO_BitRate, 
						&data, sizeof(HCW_Aud_BitRate), &data, sizeof(HCW_Aud_BitRate));
				
			}
		}
	}
	if ( SUCCEEDED( hr ) )
		slog((env, "configureHCWEncoder setup audio BitRate succeeded\r\n" ));
	else
		slog((env, "configureHCWEncoder setup audio BitRate failed\r\n" ));

	hr = E_FAIL;
	if ( pIKsBridgePropSet1 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet1->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_AUDIO_SampleRate, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Aud_SampleRate data;
				switch (pythonParams->audiosampling)
				{
					case 32000:
						data = HCW_Aud_SampleRate_32;
						break;
					case 44100:
						data = HCW_Aud_SampleRate_44;
						break;
					default:
						data = HCW_Aud_SampleRate_48;
						break;
				}
				slog((env, "configureHCWEncoder audio sampling rate %d on HW encoder\r\n", pythonParams->audiosampling ));
				hr = pIKsBridgePropSet1->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_AUDIO_SampleRate,
						&data, sizeof(HCW_Aud_SampleRate),
						&data, sizeof(HCW_Aud_SampleRate));
			}
		}
	}
	if ( !SUCCEEDED( hr ) && pIKsBridgePropSet2 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet2->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_AUDIO_SampleRate, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Aud_SampleRate data;
				switch (pythonParams->audiosampling)
				{
					case 32000:
						data = HCW_Aud_SampleRate_32;
						break;
					case 44100:
						data = HCW_Aud_SampleRate_44;
						break;
					default:
						data = HCW_Aud_SampleRate_48;
						break;
				}
				slog((env, "configureHCWEncoder audio sampling rate %d on capture filter\r\n", pythonParams->audiosampling ));
				hr = pIKsBridgePropSet2->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_AUDIO_SampleRate,
						&data, sizeof(HCW_Aud_SampleRate),
						&data, sizeof(HCW_Aud_SampleRate));
			}
		}
	}
	if ( SUCCEEDED( hr ) )
		slog((env, "configureHCWEncoder setup audio sampling rate succeeded\r\n" ));
	else
		slog((env, "configureHCWEncoder setup audio sampling rate failed\r\n" ));

	if ( pIKsBridgePropSet1 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet1->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_AUDIO_Output, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Aud_Output data;
				switch (pythonParams->audiooutputmode)
				{
					case 1:
						data = HCW_Aud_Output_JointStereo;
						break;
					case 2:
						data = HCW_Aud_Output_DualChannel;
						break;
					case 3:
						data = HCW_Aud_Output_Mono;
						break;
					default:
						data = HCW_Aud_Output_Stereo;
						break;
				}
				slog((env, "configureHCWEncoder audio channel %d on HW encoder\r\n", pythonParams->audiooutputmode ));
				hr = pIKsBridgePropSet1->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_AUDIO_Output,
						&data, sizeof(HCW_Aud_Output),
						&data, sizeof(HCW_Aud_Output));
			}
		}
	}

	if ( !SUCCEEDED( hr ) && pIKsBridgePropSet2 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet2->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_AUDIO_Output, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Aud_Output data;
				switch (pythonParams->audiooutputmode)
				{
					case 1:
						data = HCW_Aud_Output_JointStereo;
						break;
					case 2:
						data = HCW_Aud_Output_DualChannel;
						break;
					case 3:
						data = HCW_Aud_Output_Mono;
						break;
					default:
						data = HCW_Aud_Output_Stereo;
						break;
				}
				slog((env, "configureHCWEncoder audio channel %d on capture filter\r\n", pythonParams->audiooutputmode ));
				hr = pIKsBridgePropSet2->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_AUDIO_Output,
						&data, sizeof(HCW_Aud_Output),
						&data, sizeof(HCW_Aud_Output));
			}
		}
	}
	if ( SUCCEEDED( hr ) )
		slog((env, "configureHCWEncoder setup audio channel succeeded\r\n" ));
	else
		slog((env, "configureHCWEncoder setup audio channel failed\r\n" ));

	if ( pIKsBridgePropSet1 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet1->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_AUDIO_CRC, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Aud_CRC data = pythonParams->audiocrc ? HCW_Aud_CRC_On : HCW_Aud_CRC_Off;
				slog((env, "configureHCWEncoder setup audio crc %don HW encoder\r\n", pythonParams->audiocrc ));
				hr = pIKsBridgePropSet1->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_AUDIO_CRC, 
						&data, sizeof(HCW_Aud_CRC), &data, sizeof(HCW_Aud_CRC));
			}
		}
	}
	if ( !SUCCEEDED( hr ) && pIKsBridgePropSet2 != NULL )
	{
		if (SUCCEEDED(pIKsBridgePropSet2->QuerySupported(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, 
			HCW_ECP_AUDIO_CRC, &dwTypeSupport)))
		{
			if(dwTypeSupport & KSPROPERTY_SUPPORT_SET)
			{
				HCW_Aud_CRC data = pythonParams->audiocrc ? HCW_Aud_CRC_On : HCW_Aud_CRC_Off;
				slog((env, "configureHCWEncoder setup audio crc %d on capture filter\r\n", pythonParams->audiocrc ));
				hr = pIKsBridgePropSet2->Set(PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES, HCW_ECP_AUDIO_CRC, 
						&data, sizeof(HCW_Aud_CRC), &data, sizeof(HCW_Aud_CRC));
			}
		}
	}
	if ( SUCCEEDED( hr ) )
		slog((env, "configureHCWEncoder setup audio crc on/off succeeded\r\n" ));
	else
		slog((env, "configureHCWEncoder setup audio crc on/off failed\r\n" ));



	if (pIKsBridgePropSet1)
		SAFE_RELEASE(pIKsBridgePropSet1);

	if (pIKsBridgePropSet2)
		SAFE_RELEASE(pIKsBridgePropSet2);

	slog((env, "configureHCWEncoder done '%s-%d'\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum));
	
}