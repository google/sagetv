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

#include <initguid.h>

DEFINE_GUID(KSCATEGORY_BDA_NETWORK_TUNER, 0x71985f48, 0x1ca1, 0x11d3, 0x9c, 0xc8, 0x0, 0xc0, 0x4f, 0x79, 0x71, 0xe0 );
#define MAX_BDA_TUNER_NUM    16

#include "../../include/sage_DShowCaptureDevice.h"
#include "DShowUtilities.h"
#include "FilterGraphTools.h"
#include <streams.h>
#include "uniapi.h"

extern FilterGraphTools graphTools;

BOOL CheckFakeBDACrossBar( JNIEnv *env, char* capFiltName, int CapFiltNum, char* BDAFiltDevName, int BDAFiltDevNameSize );

char* GetFakeBDACrossBarTunerName( char* capFiltName, char* pTunerName, int NameSize );
/*
 * Class:     sage_DShowCaptureDevice
 * Method:    isCaptureDeviceValid0
 * Signature: (Ljava/lang/String;I)I
 */

int HasTunerTag( char* filterName )
{
	char *ps, *pe;
	if ( ( ps = strstr( filterName, "<" ) ) )
	{
		if ( ( pe = strstr( ps, ">" ) ) )
		{
			pe++;
			if ( *pe == '@' )
				return 2;
			return 1;
		}
	}
	return 0;
}

int GetIndexFromTunerTag( char* filterName )
{
	char *ps, *pe;
	int index = -1;
	if ( ( ps = strstr( filterName, "<" ) ) )
	{
		if ( ( pe = strstr( ++ps, ">" ) ) )
		{
			pe++;
			if ( *pe == '-' && *(pe+1)>='0' && *(pe+1) <='9' )
				index = atoi( pe+1 );
		}
	}
	return index;
}

char* GetTypeFromTunerTag( char* filterName, char* typeBuf, int bufSize )
{
	char *ps, *pe;
	if ( bufSize > 0 ) 
		typeBuf[0] = 0x0;
	else
		return "";

	if ( ( ps = strstr( filterName, "<" ) ) )
	{
		if ( ( pe = strstr( ++ps, ">" ) ) )
		{
			int len;
			len = min( pe - ps, bufSize-1 );
			strncpy( typeBuf, ps, len );
			typeBuf[len] = 0x0;
		}
	}
	return typeBuf;
}

void RemoveTunerTag( char* filterName )
{ 
	char *ps, *pe;
	if ( ( ps = strstr( filterName, "<" ) ) )
	{
		if ( ( pe = strstr( ps, ">" ) ) )
		{
			*ps = 0x0; *pe = 0x0;
		}
	}
}


JNIEXPORT jint JNICALL Java_sage_DShowCaptureDevice_isCaptureDeviceValid0
  (JNIEnv *env, jobject jo, jstring videoCapFiltName, jint videoCapFiltNum)
{
    if (videoCapFiltName == NULL || env->GetStringLength(videoCapFiltName) == 0)
	{
		slog((env, "Capture device name is not specified\r\n"));
		return sage_DShowCaptureDevice_DEVICE_NO_EXIST;
	}
	char capFiltName[256];
	const char* tempCapFiltName = env->GetStringUTFChars(videoCapFiltName, NULL);
	strncpy( capFiltName, tempCapFiltName, sizeof(capFiltName) );  
    // slog((env, "isCaptureDeviceValid0() Entry: videoCapFiltName %s, Num %d \r\n", capFiltName, videoCapFiltNum));
	env->ReleaseStringUTFChars(videoCapFiltName, tempCapFiltName);
	if ( HasTunerTag( capFiltName ) )
	{
		int index = GetIndexFromTunerTag( capFiltName );
		if ( index >= 0 )
			videoCapFiltNum = index;
		RemoveTunerTag( capFiltName );
		//if ( index < 0 )
		//{
		//	slog((env, "Capture device %s (%d) is dropped(old encoder, need resetup). \r\n", capFiltName, videoCapFiltNum ));
		//	return sage_DShowCaptureDevice_DEVICE_NO_EXIST;
		//}
	}
	//processing passed in configure name with tag<...>

    // KSF: 
    // Users that had plugged in this device prior to dual-tuner support will have a TS Capture entry in sage.properties.
    // We don't want them to see that any more in the Setup Video Sources UI.  
    // With this hack, they'll only see the 2 new 'Hauppauge WinTV-dualHD ATSC Tuner' entries.
    if (!strncmp(capFiltName, "Hauppauge WinTV-dualHD TS Capture", sizeof("Hauppauge WinTV-dualHD TS Capture"))) 
    {
        slog((env, "hardcode: ignoring legacy device '%s' because WinTV-dualHD Tuner(s) are now supported \r\n", capFiltName));
        return sage_DShowCaptureDevice_DEVICE_NO_EXIST;
    }

//	CoInitializeEx(NULL, COM_THREADING_MODE);
	IBaseFilter* pFilter = NULL;
	HRESULT hr = FindFilterByName(&pFilter, AM_KSCATEGORY_CAPTURE,
		capFiltName, videoCapFiltNum, NULL, 0);
	SAFE_RELEASE(pFilter);
//	CoUninitialize();

    
	//ZQ if not found in WDM device category, we try BDA category
	if (FAILED(hr))
	{
		IBaseFilter* pFilter = NULL;
		hr = FindFilterByName(&pFilter, KSCATEGORY_BDA_RECEIVER_COMPONENT,
									capFiltName, videoCapFiltNum, NULL, 0);
		if ( !FAILED(hr) )
		{
			CComPtr <IPin> pCapturePin;
			hr = graphTools.FindPinByMediaType( pFilter, MEDIATYPE_Stream, 
				          KSDATAFORMAT_SUBTYPE_BDA_MPEG2_TRANSPORT, &pCapturePin, REQUESTED_PINDIR_OUTPUT );
			if (FAILED(hr))
				hr = graphTools.FindPinByMediaType( pFilter, MEDIATYPE_Stream, 
				          MEDIASUBTYPE_MPEG2_TRANSPORT, &pCapturePin, REQUESTED_PINDIR_OUTPUT ); //ZQ. for HDHR outpin case
			if (FAILED(hr))
				slog((env, "Capture device is found in BDA_RECEIVER_COMPONENT, but don't have a BDA_MPEG2_TRANSPORT pin, drop it! \r\n" ));
		}
		SAFE_RELEASE(pFilter);
	}
	
	if (FAILED(hr))
	{
		IBaseFilter* pFilter = NULL;
		hr = FindFilterByName(&pFilter, KSCATEGORY_BDA_NETWORK_TUNER,
									capFiltName, videoCapFiltNum, NULL, 0 );
		if ( !FAILED(hr) )
		{
			CComPtr <IPin> pCapturePin;
			hr = graphTools.FindPinByMediaType( pFilter, MEDIATYPE_Stream, 
				          KSDATAFORMAT_SUBTYPE_BDA_MPEG2_TRANSPORT, &pCapturePin, REQUESTED_PINDIR_OUTPUT );
			if (FAILED(hr))
				hr = graphTools.FindPinByMediaType( pFilter, MEDIATYPE_Stream, 
				          MEDIASUBTYPE_MPEG2_TRANSPORT, &pCapturePin, REQUESTED_PINDIR_OUTPUT ); //ZQ. for HDHR outpin
			if (FAILED(hr))
			{
				if ( strstr( capFiltName, "QUAD DVB-T" ) ) //"DigitalNow Quad DVB-T"  "MPEG2 Tansport" pin created upon input pin connected 
					hr = S_OK;
				else
					slog((env, "Capture device '%s' is found in BDA_NETWORK_TUNER, but don't have a BDA_MPEG2_TRANSPORT pin, drop it! \r\n", capFiltName ));
			}
		}

		SAFE_RELEASE(pFilter);
	}
 
	if (FAILED(hr))
	{
		if ( !strncmp( capFiltName, "PUSH TS SOURCE", 14 ) ) //hard code "PUSH TS SOURCE"  for debug dump data
		{
			slog((env, "Capture device is a hard coded device  %s (%d) exists\r\n", capFiltName, videoCapFiltNum ));
			return sage_DShowCaptureDevice_DEVICE_EXIST;
		}
	}

	//ZQ
	
	if (FAILED(hr))
	{
		slog((env, "Capture device %s (%d) does not exist \r\n", capFiltName, videoCapFiltNum ));
		return sage_DShowCaptureDevice_DEVICE_NO_EXIST;
	}
	else
	{
		slog((env, "Capture device %s (%d) exists\r\n", capFiltName, videoCapFiltNum ));
		return sage_DShowCaptureDevice_DEVICE_EXIST;
	}
}

#ifdef RET_FAIL_CC
#undef RET_FAIL_CC
#endif
#define RET_FAIL_CC(x) if (FAILED(hr)) { /*CoUninitialize();*/ slog(("Crossbar detection failure at %d\r\n", __LINE__)); return (x); }

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    getCrossbarConnections0
 * Signature: (Ljava/lang/String;I)[I
 */
////////////////////////////////////////////////////
JNIEXPORT jintArray JNICALL Java_sage_DShowCaptureDevice_getCrossbarConnections0
  (JNIEnv *env, jobject jo, jstring videoCapFiltName, jint videoCapFiltNum)
{
	char capFiltName[256];
	const char* tempCapFiltName = env->GetStringUTFChars(videoCapFiltName, NULL);
	strncpy( capFiltName, tempCapFiltName, sizeof(capFiltName) );  
    // slog((env, "getCrossbarConnections0() Entry: capFiltName %s, videoCapFiltNum %d \r\n", capFiltName, videoCapFiltNum));
	env->ReleaseStringUTFChars(videoCapFiltName, tempCapFiltName);
	//processing passed in configure name with tag<...>
	if ( HasTunerTag( capFiltName ) )
	{
		int index = GetIndexFromTunerTag( capFiltName );
		if ( index >= 0 )
			videoCapFiltNum = index;
		RemoveTunerTag( capFiltName );
	}

	HRESULT hr;
	char masterDevName[1024];
	CComPtr<IBaseFilter> pSrcVideoFilter = NULL;


	hr = FindFilterByName(&(pSrcVideoFilter.p), AM_KSCATEGORY_CAPTURE,
		capFiltName, videoCapFiltNum, masterDevName, sizeof(masterDevName) );

	if ( FAILED( hr ) )
	{
		hr = FindFilterByName(&(pSrcVideoFilter.p), KSCATEGORY_BDA_RECEIVER_COMPONENT,
			capFiltName, videoCapFiltNum, masterDevName, sizeof(masterDevName) );
		if ( FAILED( hr ) )  //HDHomeRun
			hr = FindFilterByName(&(pSrcVideoFilter.p), KSCATEGORY_BDA_NETWORK_TUNER,
					capFiltName, videoCapFiltNum, masterDevName, sizeof(masterDevName));
	}

	if ( FAILED( hr ) )
	{
		if ( !strncmp( capFiltName, "PUSH TS SOURCE", 14 ) ) //hard code "PUSH TS SOURCE"  for debug dump data
			hr = FindFilterByName(&(pSrcVideoFilter.p), CLSID_LegacyAmFilterCategory,
									capFiltName, videoCapFiltNum, masterDevName, sizeof(masterDevName));
	}


	RET_FAIL_CC(NULL);
	char* devNameEnd = strstr(masterDevName, "{");
	if (devNameEnd)
		*devNameEnd = '\0';


	//ZQ if board has BDA source tunner, if it's avaliable, make it as a fake crossbar
	// if only device has video output or stream pin and BDA, we think it valid BDA video capture   ZQ.
	BOOL hasBDAInput = FALSE;
	CComPtr<IBaseFilter> pFilter = NULL; //we could optimize following code.
	hr = FindFilterByName(&(pFilter.p), AM_KSCATEGORY_CAPTURE,
		capFiltName, videoCapFiltNum, NULL, 0 );
	if ( FAILED( hr ) )
	{
		hr = FindFilterByName(&(pFilter.p), KSCATEGORY_BDA_RECEIVER_COMPONENT,
					capFiltName, videoCapFiltNum, NULL, 0);
		if ( FAILED( hr ) )  //HDHomeRun
			hr = FindFilterByName(&(pFilter.p), KSCATEGORY_BDA_NETWORK_TUNER,
					capFiltName, videoCapFiltNum, NULL, 0 );

	}
	if ( FAILED( hr ) )
	{
		if ( !strncmp( capFiltName, "PUSH TS SOURCE", 14 ) ) //hard code "PUSH TS SOURCE"  for debug dump data
			hr = FindFilterByName(&(pFilter.p), CLSID_LegacyAmFilterCategory,
									capFiltName, videoCapFiltNum, NULL, 0);
	}


	BOOL isHybridCapture = FALSE;
	char szHybridCapture[64]={0};
	ReadAdditionalSettingString( env, capFiltName, "hybrid", 
			                         szHybridCapture, sizeof(szHybridCapture) );
	isHybridCapture = szHybridCapture[0] != 0x0;

	if ( pFilter && !isHybridCapture )
	{
		IPin* pVideoPin = FindPin(pFilter, PINDIR_OUTPUT, &MEDIATYPE_Video, NULL);
		if ( pVideoPin )
		{
			hasBDAInput = IsVirtualTuner( env, capFiltName  );
			if ( !hasBDAInput )
				hasBDAInput = CheckFakeBDACrossBar( env, capFiltName, videoCapFiltNum, NULL, 0 );
		} else
		{
			if ( strstr( capFiltName, "QUAD DVB-T" ) ) //ZQ DigitalNow Quad DVB-T creates outpin after input pin connected. 
				hasBDAInput = CheckFakeBDACrossBar( env, capFiltName, videoCapFiltNum, NULL, 0 );//I hope it's the last ugly hard code path before the new DShowCapture code is done.

			pVideoPin = FindPin(pFilter, PINDIR_OUTPUT, &MEDIATYPE_Stream, NULL);
			if ( pVideoPin )
			{
				hasBDAInput = IsVirtualTuner( env, capFiltName  );
				if ( !hasBDAInput )
					hasBDAInput = CheckFakeBDACrossBar( env, capFiltName, videoCapFiltNum, NULL, 0 );
			}
		}
		SAFE_RELEASE(pVideoPin);

	}

	int numValidPins = 0;
	jint* pinData = NULL;

	// The crossbar will be the filter that connects up to the analog video input pin
	// of the capture filter.
	CComPtr<IBaseFilter> pCrossbar = NULL;
	CComPtr<IPin> pCapVideoIn = NULL;
	pCapVideoIn.p = FindVideoCaptureInputPin(pSrcVideoFilter);

	slog((env, "BDA FindVideoPin:0x%x hasBDAInput:%x on %s\r\n", pCapVideoIn.p, hasBDAInput, capFiltName ) );

	if (!pCapVideoIn.p )
	{
		if	( !hasBDAInput )
		{
			hr = VFW_E_NOT_FOUND;
			RET_FAIL_CC(NULL);
		} else
		{
			numValidPins = 1;
			pinData = new jint[ 1 ];
			pinData[0] = 100;
			goto loc_end;
		}
	}
 

	// The filter that can connect to this pin is the crossbar for this capture setup.
	
	hr = FindUpstreamFilter(&(pCrossbar.p), AM_KSCATEGORY_CROSSBAR, pCapVideoIn, masterDevName);
	if ( FAILED( hr ) )
	{
		if	( !hasBDAInput )
		{
			hr = VFW_E_NOT_FOUND;
			RET_FAIL_CC(NULL);
		} else
		{
			numValidPins = 1;
			pinData = new jint[ 1 ];
			pinData[0] = 100;
			goto loc_end;
		}
	}

	// Now see if we can find a tuner filter to connect to the crossbar's video tuner input
	// if it exists. Then we can find out if we support FM Radio or not.
	// Check for a radio input so we add this to the connection list. Its not different
	// on the crossbar, but it follows the same theory as a different input as the user would see it.
	BOOL hasRadioInput = FALSE;
	// If it doesn't have the tuner filter ignore the tuner inputs on the crossbar
	BOOL hasTunerFilter = FALSE;

   	long numIn=0,  numOut=0;
	long numIn2=0, numOut2=0;
	IAMCrossbar* pRealCross = NULL;
	IAMCrossbar* pRealCross2 = NULL;
	hr = pCrossbar->QueryInterface(IID_IAMCrossbar, (void**) &pRealCross);
	if ( FAILED( hr ) )
	{
		if	( !hasBDAInput )
		{
			hr = VFW_E_NOT_FOUND;
			RET_FAIL_CC(NULL);
		} else
		{
			numValidPins = 1;
			pinData = new jint[ 1 ];
			pinData[0] = 100;
			goto loc_end;
		}
	}

	pRealCross->get_PinCounts(&numOut, &numIn);
	long relatedPin, pinType;
	int i;
	for ( i = 0; i < numIn && !hasRadioInput; i++)
	{
		relatedPin = pinType = 0;
		pRealCross->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);
		if (pinType == PhysConn_Video_Tuner)
		{
			IPin* pVTuneCross = NULL;
			hr = GetPin(pCrossbar, PINDIR_INPUT, i, &pVTuneCross);
			if (SUCCEEDED(hr))
			{
				// The pin that can connect to this is the tuner, which we check for FM then.
				CComPtr<IBaseFilter> pTuner = NULL;
				hr = FindUpstreamFilter(&(pTuner.p), AM_KSCATEGORY_TVTUNER, pVTuneCross, masterDevName);
				if (SUCCEEDED(hr))
				{
					hasTunerFilter = TRUE;
					IAMTVTuner* pAMTuner = NULL;
					hr = pTuner->QueryInterface(IID_IAMTVTuner, (void**)&pAMTuner);
					if (SUCCEEDED(hr))
					{
						// Check whether the mode is supported.
						long lModes = 0;
						hr = pAMTuner->GetAvailableModes(&lModes);
						if (SUCCEEDED(hr) && (lModes & AMTUNER_MODE_FM_RADIO))
						{
							hasRadioInput = TRUE;
						}
						SAFE_RELEASE(pAMTuner);
					}
				} 
				SAFE_RELEASE(pVTuneCross);
			}

		}
	}

	//ZQ. ATI TV Wonder USB has two crossbars that combine to work, if first crossbar doesn't have Tuner pin,
	// try to find next upstream crossbar2 that can connect crossbar.
	if ( !hasTunerFilter && pRealCross != NULL )
	{
		slog((env, "not found tuner pin in crossbar for device, try search second cross bar %s\r\n",   capFiltName ) );
		relatedPin = pinType = 0;
		for ( i = 0; i < numIn && !hasRadioInput && !hasTunerFilter; i++)
		{
			pRealCross->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);
			if (pinType == PhysConn_Video_Tuner)
			{
				IPin* pVideoInCross = NULL;
				hr = GetPin( pCrossbar, PINDIR_INPUT, i, &pVideoInCross );
				if ( SUCCEEDED( hr ) )
				{
					IEnumMoniker *pEnum = NULL;
					IBaseFilter* pCrossbar2 = NULL;
					hr = graphTools.EnumFilterFirst( AM_KSCATEGORY_CROSSBAR, &pEnum, &pCrossbar2 ); 
					while ( hr == S_OK && !hasTunerFilter )
					{
						hr = pCrossbar2->QueryInterface(IID_IAMCrossbar, (void**)&pRealCross2 );
						if ( SUCCEEDED(hr) )
						{
							pRealCross2->get_PinCounts( &numOut2, &numIn2 );
							for ( int j = 0; j<numIn2 && !hasRadioInput  && !hasTunerFilter; j++)
							{
								long relatedPin2 = 0, pinType2 = 0;
								pRealCross2->get_CrossbarPinInfo(FALSE, j, &relatedPin2, &pinType2);

								if (pinType2 == PhysConn_Video_Tuner)
								{
									slog((env, "found second crossbar for device %s\r\n",   capFiltName ) );
									IPin* pVTuneCross2 = NULL;
									hr = GetPin( pCrossbar2, PINDIR_INPUT, j, &pVTuneCross2 );
									if (SUCCEEDED(hr))
									{
										CComPtr<IBaseFilter> pTuner = NULL;
										hr = FindUpstreamFilter(&(pTuner.p), AM_KSCATEGORY_TVTUNER, pVTuneCross2, masterDevName);
										if (SUCCEEDED(hr))
										{
											hasTunerFilter = TRUE;
											IAMTVTuner* pAMTuner = NULL;
											hr = pTuner->QueryInterface(IID_IAMTVTuner, (void**)&pAMTuner);
											if (SUCCEEDED(hr))
											{
												// Check whether the mode is supported.
												long lModes = 0;
												hr = pAMTuner->GetAvailableModes(&lModes);
												if (SUCCEEDED(hr) && (lModes & AMTUNER_MODE_FM_RADIO))
												{
													hasRadioInput = TRUE;
												}
												SAFE_RELEASE(pAMTuner);
											}
											slog((env, "second crossbar has tuner pin for device %s\r\n",   capFiltName ) );
										}
									}
									SAFE_RELEASE(pVTuneCross2);
								}
							}
						}
						SAFE_RELEASE(pRealCross2);
						hr = graphTools.EnumFilterNext( pEnum, &pCrossbar2 );

					} //end of while( )
					if ( pEnum != NULL )
						graphTools.EnumFilterClose( pEnum );
				}
				SAFE_RELEASE( pVideoInCross );
			}
		}
	} //ZQ. 
	
    // Check for the multiple audio inputs linked to the component input case.
    // If there's an S/PDIF input then we tie that to the Component input if it exists; and
    // if there's more than one audio input that's related to the component input then the component
    // input will appear more than once
    BOOL hasMultiAudioCompInput = FALSE;
	BOOL foundSPDIFCompInput = FALSE;
	BOOL foundOtherCompInput = FALSE;
	long relatedPinType, junk;
	for (i = 0; i < numIn && (!foundOtherCompInput || !foundSPDIFCompInput); i++)
	{
		relatedPinType = relatedPin = pinType = 0;
		pRealCross->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);
		if (relatedPin >= 0)
			pRealCross->get_CrossbarPinInfo(TRUE, relatedPin, &junk, &relatedPinType);

		// I see no reason to show the other inputs here, they're just confusing
		if (pinType == PhysConn_Video_YRYBY) // component
		{
			if (relatedPinType == PhysConn_Audio_SPDIFDigital)
				foundSPDIFCompInput = TRUE;
			else
				foundOtherCompInput = TRUE;
		}
		else if (pinType == PhysConn_Audio_SPDIFDigital && relatedPinType == PhysConn_Video_YRYBY)
			foundSPDIFCompInput = TRUE;
		else if (relatedPinType == PhysConn_Video_YRYBY && pinType >= PhysConn_Audio_Line && pinType <= PhysConn_Audio_AUX)
			foundOtherCompInput = TRUE;

        slog((env, "pin index i=%d pinType=%d relatedPin=%d foundSPDIFCompInput=%d foundOtherCompInput=%d \r\n", i, pinType, relatedPin, foundSPDIFCompInput, foundOtherCompInput));
	}
	hasMultiAudioCompInput = foundOtherCompInput && foundSPDIFCompInput;
    slog((env, "hasMultiAudioCompInput=%d \r\n", hasMultiAudioCompInput));


    // Check for multiple audio inputs for use with HDMI video input.
    // PhysConn_Video_SerialDigital (HDMI video in) is most commonly
    // paired with PhysConn_Audio_AESDigital (HDMI audio in).
    // If there are **other** possible audio inputs, 
    // then the various HDMI A-V combinations will all appear.

#define MAX_AUDIO_PINS ((PhysConn_Audio_AudioDecoder - PhysConn_Audio_Tuner) + 2)
    long hdmiRelatedPin, partnerRelatedPin;
    BOOL foundVideoHdmiInput = FALSE;
    int numAudioPins = 0;
    int audioPin[MAX_AUDIO_PINS];
    int audioVideoHdmiPinType[MAX_AUDIO_PINS];
    int numReportedHdmiPinTypes = 0;
    int reportedHdmiPinType[MAX_AUDIO_PINS];

    // Build a list of HDMI & all audio pins of interest available on this device
    for (i = 0; i < numIn; i++)
    {
        relatedPinType = relatedPin = pinType = 0;
        pRealCross->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);
         
        switch (pinType)
        {
        case PhysConn_Video_SerialDigital: // HDMI video input
            foundVideoHdmiInput = TRUE;
            hdmiRelatedPin = relatedPin;
            if (relatedPin >= 0)
            {
                pRealCross->get_CrossbarPinInfo(TRUE, relatedPin, &partnerRelatedPin, &relatedPinType);
                // sanity check: related pins probably should point to each other; but not required
                if (i != partnerRelatedPin)
                    slog((env, "INFO pin index i=%d, pinType=%d -> relatedPin=%d, relatedPinType=%d, but its partnerRelatedPin=%d \r\n",
                        i, pinType, relatedPin, relatedPinType, partnerRelatedPin));
            }
            break;
        case PhysConn_Audio_Line:
            audioPin[numAudioPins] = i;
            audioVideoHdmiPinType[numAudioPins] = 80;    // HDMI+LineIn; HDMI_LINEIN_CROSSBAR_INDEX
            numAudioPins++;
            break;
        case PhysConn_Audio_AESDigital:
            audioPin[numAudioPins] = i;
            audioVideoHdmiPinType[numAudioPins] = 81;    // HDMI_AV; HDMI_AES_CROSSBAR_INDEX
            numAudioPins++;
            break;
        case PhysConn_Audio_SPDIFDigital:
            audioPin[numAudioPins] = i;
            audioVideoHdmiPinType[numAudioPins] = 82;    // HDMI+SPDIF; HDMI_SPDIF_CROSSBAR_INDEX
            numAudioPins++;
            break;
        default:
            break;
        }
    }

    // Build list of reportable HDMI inputs
    // Something mildly complicated; need to handle the following possible situations:
    // 1. HDMI vid input, but no audio pins: report the HDMI pinType itself
    // 2. HDMI vid input, no Related pin && some audio pins: report all possible <HDMI+audio> pinType combos
    // 3. HDMI vid input, w/Related pin && some audio pins: report original HDMI pinType & other unique <HDMI+audio> pinType combos
    if (foundVideoHdmiInput)
    {
        if (numAudioPins == 0)
        {
            reportedHdmiPinType[numReportedHdmiPinTypes] = PhysConn_Video_SerialDigital;   // the HDMI video pinType itself
            slog((env, "no audio pins! returning original HDMI pinType=%d \r\n", reportedHdmiPinType[numReportedHdmiPinTypes]));
            numReportedHdmiPinTypes++;
        }
        else
        {
            // build a list of all possible HDMI AV sources;  restrict to ROUTEABLE pintypes??
            for (i = 0; i < numAudioPins; i++)
            {
                if (audioPin[i] == hdmiRelatedPin)
                {
                    // report driver's original pinType instead of our combo type
                    reportedHdmiPinType[numReportedHdmiPinTypes] = PhysConn_Video_SerialDigital;
                    slog((env, "HDMI AV has Related audio pin: reportedHdmiPinType[%d]=%d \r\n", numReportedHdmiPinTypes, reportedHdmiPinType[numReportedHdmiPinTypes]));
                    numReportedHdmiPinTypes++;
                }
                else
                {
                    // report our combo pinType
                    reportedHdmiPinType[numReportedHdmiPinTypes] = audioVideoHdmiPinType[i];
                    slog((env, "HDMI AV has other audio pin: reportedHdmiPinType[%d]=%d \r\n", numReportedHdmiPinTypes, reportedHdmiPinType[numReportedHdmiPinTypes]));
                    numReportedHdmiPinTypes++;
                }
            }
        }
    }

    long numInTotal = numIn + numIn2;
    if (hasRadioInput) numInTotal++;
    if (hasMultiAudioCompInput) numInTotal++;
    numInTotal += numReportedHdmiPinTypes;
   	pinData = new jint[numInTotal];
    BOOL allowDigitalTvTuner = TRUE;

	for (i = 0; i < numIn; i++)
	{
		relatedPin = pinType = 0;
		pRealCross->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);

		// I see no reason to show the other inputs here, they're just confusing
		if (pinType && pinType <= PhysConn_Video_ParallelDigital /*PhysConn_Video_Black*/)
		{
			// Enforce rule for component/spdif input if this is it
			if (pinType == PhysConn_Video_YRYBY)
			{
				if (hasMultiAudioCompInput)
				{
					pinData[numValidPins++] = pinType;
					pinData[numValidPins++] = 90; // 90 is the code for Component+SPDIF, YPBPR_SPDIF_CROSSBAR_INDEX
				}
				else if (relatedPin >= 0)
				{
					pRealCross->get_CrossbarPinInfo(TRUE, relatedPin, &junk, &relatedPinType);

					if (relatedPinType == PhysConn_Audio_SPDIFDigital)
						pinData[numValidPins++] = 90; // 90 is the code for Component+SPDIF, YPBPR_SPDIF_CROSSBAR_INDEX
					else
						pinData[numValidPins++] = pinType;
				}
				else
					pinData[numValidPins++] = pinType;
			}

            // show all HDMI audio-video input combos
            else if ( (pinType == PhysConn_Video_SerialDigital) && (numReportedHdmiPinTypes > 0) )
            {
                for (int j = 0; j < numReportedHdmiPinTypes; j++)
                    pinData[numValidPins++] = reportedHdmiPinType[j];
            }

            else if (pinType == PhysConn_Video_ParallelDigital)
            {
                pinData[numValidPins++] = pinType;
                if (!hasTunerFilter)
                    allowDigitalTvTuner = FALSE;
            }

			// Enforce tuner crossbar pins only if tuner filter exists rule
			else if (pinType != PhysConn_Video_Tuner || hasTunerFilter)
				pinData[numValidPins++] = pinType;
		}
	}
    for (i = 0; i < numIn2 && pRealCross2; i++)
	{
		relatedPin = pinType = 0;
		pRealCross2->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);

		// I see no reason to show the other inputs here, they're just confusing
		if (pinType && pinType <= PhysConn_Video_ParallelDigital /*PhysConn_Video_Black*/)
		{
			// Enforce tuner crossbar pins only if tuner filter exists rule
			if (pinType != PhysConn_Video_Tuner || hasTunerFilter)
				pinData[numValidPins++] = pinType;
		}      
	}

	if (hasRadioInput)
		pinData[numValidPins++] = 99; // 99 is the code for an FM Radio input for SageTV

    // don't blindly add 'Digital TV Tuner'
    // ex: HD PVR 60: hasBDAInput=TRUE & a video input pin (PhysConn_Video_ParallelDigital), but no actual tuner
	if (hasBDAInput && allowDigitalTvTuner)
    {
		pinData[numValidPins++] = 100; //100 is the code of BDA fake crossbar  ZQ.
        slog((env, "added DIGITAL_TUNER_CROSSBAR_INDEX; numValidPins=%d hasBDAInput=%d on capFiltName '%s' \r\n", numValidPins, hasBDAInput, capFiltName));
    }

	SAFE_RELEASE(pRealCross);
	SAFE_RELEASE(pRealCross2);
//	CoUninitialize();

loc_end:
	if (!numValidPins)
		return NULL;

    for (i = 0; i < numValidPins; i++)
        slog((env, "pin list: pin %d pinType=%d \r\n", i, pinData[i]));

	jintArray rv = env->NewIntArray(numValidPins);
	env->SetIntArrayRegion(rv, 0, numValidPins, pinData);
	return rv;
}
////////////////////////////////////////////////////

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    getDeviceCaps0
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowCaptureDevice_getDeviceCaps0
  (JNIEnv *env, jobject jo, jstring videoCapFiltName, jint videoCapFiltNum)
{
	char capFiltName[256];
	int newCaptureType = 0;
	const char* tempCapFiltName = env->GetStringUTFChars(videoCapFiltName, NULL);
	strncpy( capFiltName, tempCapFiltName, sizeof(capFiltName) ); 
    // slog((env, "getDeviceCaps0() Entry: capFiltName %s, videoCapFiltNum %d \r\n", capFiltName, videoCapFiltNum));
	env->ReleaseStringUTFChars(videoCapFiltName, tempCapFiltName);
	if ( HasTunerTag( capFiltName ) )
	{
		int index = GetIndexFromTunerTag( capFiltName );
		if ( index >= 0 )
			videoCapFiltNum = index;
		RemoveTunerTag( capFiltName );
	}

	//check if it's a virtual tuner
	if ( IsVirtualTuner( env, capFiltName  ) )
	{
		newCaptureType = sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK | sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK;
		slog((env, "DeviceCap: Virtual Tuner %s type 0x%x\r\n", capFiltName, newCaptureType   ) );
		return newCaptureType;
	}
    try
    {
        // Check the registry for a custom setting for this capture filter name.
        HKEY captureDetailsKey;
        BOOL hasCaptureDetails = FALSE;
        BOOL isHybridCapture = FALSE;
        BOOL skipCrossbarCheck = FALSE;
        DWORD detailsChipsetMask = 0;
        DWORD detailsCaptureMask = 0;
        char* captureKeyName = new char[1024];
        SPRINTF(captureKeyName, 1024, "%s%s", "SOFTWARE\\Frey Technologies\\Common\\AdditionalCaptureSetups\\", capFiltName);
        if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, captureKeyName, 0, KEY_ALL_ACCESS, &captureDetailsKey) == ERROR_SUCCESS)
        {
            char HybridTuner[64]={0};
            hasCaptureDetails = TRUE;
            DWORD hType;
            DWORD hSize = sizeof(detailsChipsetMask);
            RegQueryValueEx(captureDetailsKey, "ChipsetMask", 0, &hType, (LPBYTE)&detailsChipsetMask, &hSize);
            hSize = sizeof(detailsChipsetMask);
            RegQueryValueEx(captureDetailsKey, "CaptureMask", 0, &hType, (LPBYTE)&detailsCaptureMask, &hSize);
            hSize = sizeof(HybridTuner);

            //check if it's a hybrid card
            //hybrid(sagetv)=comb(hauppauge), bandle(sagetv)=hybrid(hauppauge)
            if ( RegQueryValueEx(captureDetailsKey, "hybrid", 0, &hType, (LPBYTE)&HybridTuner, &hSize) == S_OK )
            {
                isHybridCapture = HybridTuner[0] != 0x0;
                if ( isHybridCapture )
                {
                    if ( CheckFakeBDACrossBar( env, HybridTuner, videoCapFiltNum, NULL, 0 ) )
                        isHybridCapture = TRUE;
                    else
                        isHybridCapture = FALSE;
                }
                slog((env, "Hybrid Capture tuner '%s' '%s' %s \r\n", capFiltName, HybridTuner, isHybridCapture ? "yes" : "No"));
            }

            RegCloseKey(captureDetailsKey);
            slog((env, "Get CaptureMask from registry %s, 0x%x (hybrid:'%s')\r\n", captureKeyName, detailsCaptureMask, HybridTuner  ) );
        }

        delete [] captureKeyName;

        DEVICE_DRV_INF  CaptureDrvInfo={0};
        char FilterName[256]={0};
        BOOL bFoundDevice = GetFilterDevName( env, capFiltName, videoCapFiltNum, FilterName, sizeof(FilterName) );
        if ( bFoundDevice )
        {
            GetDeviceInfo( FilterName, &CaptureDrvInfo );
            slog((env, "Device desc:'%s'.\r\n", CaptureDrvInfo.device_desc ) );
        }

        BOOL pv256 = !strcmp("StreamMachine 2210 PCI Capture", capFiltName);
        BOOL pvr250 = strstr(capFiltName, "PVR") != NULL; // and if its MPEG2
        BOOL python2 = strstr(capFiltName, "Python") != NULL; // and if its MPEG2
        BOOL vbdvcr = strstr(capFiltName, "VBDVCR") != NULL; // and if its mpeg2video
        BOOL adaptec = strstr(capFiltName, "Adaptec") != NULL;
        BOOL hcw = ( strstr(capFiltName, "Hauppauge") != NULL || strstr(capFiltName, "WinTV HVR") != NULL );
        BOOL bgt = ( !strcmp( CaptureDrvInfo.vendor_id, "1131" ) ); //black gold
        BOOL hasBDAInput = FALSE;
        DWORD  BDAInputType = 0;

        //ZQ REMOVE ME - beware: these bits are defined in Java code
#undef sage_DShowCaptureDevice_BDA_ATSC
#define sage_DShowCaptureDevice_BDA_ATSC    0x2000000L
#undef sage_DShowCaptureDevice_BDA_QAM
#define sage_DShowCaptureDevice_BDA_QAM     0x0200000L
#undef sage_DShowCaptureDevice_BDA_DVB_T
#define sage_DShowCaptureDevice_BDA_DVB_T   0x0400000L
#undef sage_DShowCaptureDevice_BDA_DVB_S
#define sage_DShowCaptureDevice_BDA_DVB_S   0x0800000L
#undef sage_DShowCaptureDevice_BDA_DVB_C
#define sage_DShowCaptureDevice_BDA_DVB_C   0x1000000L

        //Processing Hauppauge tuners
        if ( hcw && bFoundDevice )
        {
            slog((env, "It's Hauppauge device.\r\n") );
            if ( strstr( CaptureDrvInfo.device_desc, "HVR-1250") || strstr( CaptureDrvInfo.device_desc, "HVR-1255") ||
                 strstr( CaptureDrvInfo.device_desc, "HVR-1550") )
            {
                //hasCaptureDetails = false;
                hasCaptureDetails = true;
                isHybridCapture = false;
                detailsCaptureMask = 0x00500; // sage_DShowCaptureDevice_MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK | sage_DShowCaptureDevice_RAW_AV_CAPTURE_MASK
                detailsChipsetMask = 0;
                slog((env, "A HVR-1250/1255/1550 analog tuner is found.\r\n" ));

            } else
            if ( strstr( CaptureDrvInfo.device_desc, "PVR-160") )
            {
                isHybridCapture = false;
                detailsCaptureMask = 0x000800;  // sage_DShowCaptureDevice_MPEG_AV_CAPTURE_MASK
                detailsChipsetMask = 0;
                slog((env, "A PVR-160 is found.\r\n" ));
            } else
            //hard code for HVR-4000 desc:"Hauppauge WinTV 88x Video (Hybrid, DVB-T/S2+IR)"
            if ( strstr( CaptureDrvInfo.device_desc, "Hybrid, DVB-T/S") )
            {
                isHybridCapture = false;
                hasCaptureDetails = false;
                detailsCaptureMask = 0x000800;  // sage_DShowCaptureDevice_MPEG_AV_CAPTURE_MASK
                detailsChipsetMask = 0;
                BDAInputType = sage_DShowCaptureDevice_BDA_DVB_S|sage_DShowCaptureDevice_BDA_DVB_T;
                slog((env, "A boundle card is found (Analog+DVB-T+DVB-S), DVB-T|DVB-S added into source.\r\n" ) );
            } else
            if ( strstr( CaptureDrvInfo.device_desc, "WinTV HVR-930C") )
            {
                isHybridCapture = false;
                hasCaptureDetails = false;
                detailsCaptureMask = 0x000800;  // sage_DShowCaptureDevice_MPEG_AV_CAPTURE_MASK
                detailsChipsetMask = 0;
                BDAInputType = sage_DShowCaptureDevice_BDA_DVB_C|sage_DShowCaptureDevice_BDA_DVB_T;
                slog((env, "A boundle card is found, DVB-T|DVB-C added into source.\r\n" ) );
            }       
        } 

        // HD PVR 60 has a video input pin, but no tuner. Due to the driver, CheckFakeBDACrossBar()
        // returns 1, but we don't want to show 'Digital Tv Tuner' input, so...
        // (for all previou Hauppauge devices with HDPVR_ENCODER_MASK, hasBDAInput=0 anyway)
        // Might need to add other conditions here for future capture devices
        if (detailsChipsetMask & sage_DShowCaptureDevice_HDPVR_ENCODER_MASK) 
        {
           skipCrossbarCheck = TRUE;
        }

        if (skipCrossbarCheck)
        {
            hasBDAInput = FALSE;
            slog((env, "skip BDACrossbar check for tuner; detailsCaptureMask=0x%x detailsChipsetMask=0x%x \r\n", detailsCaptureMask, detailsChipsetMask));
        } else
        if ( !isHybridCapture ) {
			hasBDAInput = CheckFakeBDACrossBar( env, capFiltName, videoCapFiltNum, NULL, 0 );  // can return > 1
            slog((env, "BDA CaptureDetail:0x%x; hasBDAInput:0x%x; BDA type:0x%x\r\n", detailsCaptureMask, hasBDAInput, BDAInputType ) );
		}
		else
		{
			detailsCaptureMask &= ~sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK; //don't show Digital Tuner alone with analog tuner
            slog((env, "skip BDACrossbar check for hybrid tuner  0x%x. \r\n", detailsCaptureMask ) );
		}

		if ( bgt && bFoundDevice && !hcw ) //hauppauge uses 1131 vendor id on HVR-2250 card on some case conflication with black gold card
		{
			slog((env, "It's BlackGold device.\r\n") );
			if ( hasBDAInput >= 2 )
			{
				isHybridCapture = false;
				hasCaptureDetails = false;
				detailsCaptureMask = 0x000800;  // sage_DShowCaptureDevice_MPEG_AV_CAPTURE_MASK
				detailsChipsetMask = 0;
				BDAInputType = sage_DShowCaptureDevice_BDA_DVB_S|sage_DShowCaptureDevice_BDA_DVB_T;
				slog((env, "A boundle card is found (Analog+DVB-T+DVB-S), DVB-T|DVB-S added into source.\r\n" ) );
			}
		}

		if (hasCaptureDetails)
		{
			if ( hasBDAInput )
			{
				slog((env, "fake BDA crossbar cap0 is added for %s..\r\n",   capFiltName ) );
				slog((env, "DeviceCap: 0x%x hasBDAInput=%d \r\n", (detailsChipsetMask | detailsCaptureMask | sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK), hasBDAInput));
				return (detailsChipsetMask | detailsCaptureMask | sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK | BDAInputType); //ZQ
			}

			slog((env, "DeviceCap: 0x%x hasBDAInput=%d \r\n", (detailsChipsetMask | detailsCaptureMask), hasBDAInput));
			return (detailsChipsetMask | detailsCaptureMask);
		}
		else
		{
			if (pv256)
			{
				newCaptureType |= sage_DShowCaptureDevice_SM2210_ENCODER_MASK;
				newCaptureType |= sage_DShowCaptureDevice_LIVE_PREVIEW_MASK;
			}
			if (hcw)
				newCaptureType |= sage_DShowCaptureDevice_HCW_CAPTURE_MASK;
			else if (pvr250 || python2 || adaptec)
				newCaptureType |= sage_DShowCaptureDevice_PYTHON2_ENCODER_MASK;
			else if (vbdvcr)
				newCaptureType |= sage_DShowCaptureDevice_VBDVCR_ENCODER_MASK;
		}


	//	CoInitializeEx(NULL, COM_THREADING_MODE); 
		newCaptureType |= BDAInputType;
		CComPtr<IBaseFilter> pFilter = NULL;
		HRESULT hr = FindFilterByName(&(pFilter.p), AM_KSCATEGORY_CAPTURE,
				capFiltName, videoCapFiltNum, NULL, 0);
		if ( FAILED( hr ) )
		{
			hr = FindFilterByName(&(pFilter.p), KSCATEGORY_BDA_RECEIVER_COMPONENT,
				capFiltName, videoCapFiltNum, NULL, 0);
			if ( FAILED( hr ) )
			{
					hr = FindFilterByName(&(pFilter.p),  KSCATEGORY_BDA_NETWORK_TUNER,
						capFiltName, videoCapFiltNum, NULL, 0);
					if ( !FAILED( hr ) )
					{
						newCaptureType |= sage_DShowCaptureDevice_BDA_NETWORK_TUNER_MASK;
						slog(( "CaptureFilter in (BDA_NETWORK_TUNER) Source Fileter\r\n" )); //ZQZQ
					}
			}
			else
			{
				newCaptureType |= sage_DShowCaptureDevice_BDA_RECEIVER_COMPONENT_MASK;
				slog(( "CaptureFilter in (BDA_RECEIVER_COMPONENT) Receiver component\r\n" )); //ZQZQ
			}
		} else
		{
			newCaptureType |= sage_DShowCaptureDevice_BDA_CAPTURE_TUNER_MASK;
			slog(( "CaptureFilter in AM_KSCATEGORY_CAPTURE, newCaptureType=0x%x \r\n", newCaptureType));
		}

		if ( pFilter == NULL ) //ZQ.
			return 0; 

		// First check for a PS stream pin
		IPin* pTSVideoPin = FindPin(pFilter, PINDIR_OUTPUT, &MEDIATYPE_Stream, NULL );
		if ( pTSVideoPin )
		{
			if ( hasBDAInput ) 
			{
				slog((env, "fake BDA crossbar cap0 is added for '%s'\r\n",   capFiltName ) );
				newCaptureType |= sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK;
			}
			SAFE_RELEASE( pTSVideoPin );
		} else
		{
			if ( strstr( capFiltName, "QUAD DVB-T" ) ) //ZQ DigitalNow Quad DVB-T creates outpin after input pin connected. 
				newCaptureType |= sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK; //I hope it's the last ugly hard code path before the new DShowCapture code is done.
		}

		IPin* pMpeg2PSPin = FindPin(pFilter, PINDIR_OUTPUT, &MEDIATYPE_Stream, &MEDIASUBTYPE_MPEG2_PROGRAM);
		if (pMpeg2PSPin)
		{
			newCaptureType |= sage_DShowCaptureDevice_MPEG_AV_CAPTURE_MASK;
			SAFE_RELEASE(pMpeg2PSPin);
		}
		else
		{
			// if only has video output pin and BDA, we think it valid BDA video capture   ZQ.
			IPin* pVideoPin = FindPin(pFilter, PINDIR_OUTPUT, &MEDIATYPE_Video, NULL);
			if ( hasBDAInput && pVideoPin )
			{
				newCaptureType |= sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK;
				slog((env, "fake BDA crossbar cap0 is added for '%s'.\r\n",   capFiltName ) );
			}
			SAFE_RELEASE(pVideoPin);

			IPin* pMpeg2VideoPin = FindPin(pFilter, PINDIR_OUTPUT, &MEDIATYPE_Video, &MEDIASUBTYPE_MPEG2_VIDEO);
			if (pvr250)
			{
				// PVR cards do MPEG2, their media types don't necessarily indicate this like in
				// Hauppauge's new proprietary driver
				newCaptureType |= sage_DShowCaptureDevice_MPEG_AV_CAPTURE_MASK;
				SAFE_RELEASE(pMpeg2VideoPin);
			}
			else if (pMpeg2VideoPin)
			{
				IEnumMediaTypes *mtEnum = NULL;
				pMpeg2VideoPin->EnumMediaTypes(&mtEnum);
				BOOL doesPS = FALSE;
				AM_MEDIA_TYPE *pMT = NULL;
				while (S_OK == mtEnum->Next(1, &pMT, NULL))
				{
					if (pMT->majortype == MEDIATYPE_Stream && pMT->subtype == MEDIASUBTYPE_MPEG2_PROGRAM)
					{
						DeleteMediaType(pMT);
						doesPS = TRUE;
						break;
					}
					DeleteMediaType(pMT);
				}
				SAFE_RELEASE(mtEnum);
				if (doesPS)
					newCaptureType |= sage_DShowCaptureDevice_MPEG_AV_CAPTURE_MASK;
				{
					// Check for an audio pin on the MPEG2 capture filter
					IPin* pAudioPin = FindPin(pFilter, PINDIR_OUTPUT, &MEDIATYPE_Audio, NULL);
					if (pAudioPin)
					{
						newCaptureType |= sage_DShowCaptureDevice_MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK;
						SAFE_RELEASE(pAudioPin);
					}
					else
						newCaptureType |= sage_DShowCaptureDevice_MPEG_VIDEO_ONLY_CAPTURE_MASK;
				}
				SAFE_RELEASE(pMpeg2VideoPin);
			}
			else
			{
				// Check for a video capture pin
				IPin* pVideoPin = FindPin(pFilter, PINDIR_OUTPUT, &MEDIATYPE_Video, NULL);
				if (pVideoPin)
				{
					// Check for an audio capture pin
					IPin* pAudioPin = FindPin(pFilter, PINDIR_OUTPUT, &MEDIATYPE_Audio, NULL);
					if (pAudioPin)
					{
						newCaptureType |= sage_DShowCaptureDevice_RAW_AV_CAPTURE_MASK;
						SAFE_RELEASE(pAudioPin);
					}
					else
						newCaptureType |= sage_DShowCaptureDevice_RAW_VIDEO_CAPTURE_MASK;
					SAFE_RELEASE(pVideoPin);
				}

				// Raw capture always supports preview too
				newCaptureType |= sage_DShowCaptureDevice_LIVE_PREVIEW_MASK;
			}
		}
	}
	catch(...)
	{
		slog((env, "catch a exception in getDeviceCaps0(). \r\n" ) );
		return 0;
	}

//	CoUninitialize();
	slog((env, "DeviceCap: 0x%x\r\n", newCaptureType   ) );
	return newCaptureType;
}

int GetTunerNum( JNIEnv *env,  char* devName, REFCLSID devClassid, DEVICE_DRV_INF* devBDAInfo )
{
	IEnumMoniker *pEnum = NULL;
	LPWSTR pName;
	int  i=0, devNum = 0;
	char Name[256]={0};
	char captureName[256]={0};
	//char regKey[MAX_BDA_TUNER_NUM][128]={0};
	BOOL bFound = FALSE;
	DEVICE_DRV_INF  BDADevDrvInfo;
	HRESULT hr;
	try {
		hr = graphTools.EnumFilterPathFirst( devClassid, &pEnum, &pName ); 
		while ( hr == S_OK && devNum < MAX_BDA_TUNER_NUM )
		{
			int length = wcslen(pName);
			length =length > sizeof(captureName)? sizeof(captureName) : length;
			memset( Name, 0x0, sizeof(Name) );
			wcstombs( Name, pName, length );
			delete pName;
			GetDeviceInfo( Name, &BDADevDrvInfo );
			if ( BDADevDrvInfo.state > 0 )
			{
				if ( BDADevDrvInfo.hardware_loc[0] != 0 )
				{
					if ( !strcmp( BDADevDrvInfo.hardware_loc, devBDAInfo->hardware_loc )  )
					{
						bool skip = true;
						//filter out duplicated device in WDM capture device, with unique key regKey, 
						if ( devClassid == AM_KSCATEGORY_CAPTURE )
						{
							if ( strstr( Name, "{5eaf914d-2212-4034-8c4c-02cafd15d68a}" ) != NULL )
							{
								skip = true;
							} else	
							{
								devNum++;
								skip = false;
							} 
						} else
							devNum++;

						slog((env, "BAD Tuner index :%d dev:%s name:%s loc:%s (%d %d) %s\r\n", devNum, devName, Name, 
							devBDAInfo->hardware_loc, devNum, i, skip?"skip":"select" ) );
						char *p = strstr( Name, "\\\\" );
						if ( p != NULL && !stricmp( p, devName ) )
						{
							bFound = TRUE;
							break;
						}
					}
				} else
				{
					if ( BDADevDrvInfo.device_id[0] && !strcmp( BDADevDrvInfo.device_id, devBDAInfo->hardware_id )  )
					{
						bool skip = true;
						//filter out duplicated device in WDM capture device, with unique key regKey, 
						if ( devClassid == AM_KSCATEGORY_CAPTURE )
						{
							if ( strstr( Name, "{5eaf914d-2212-4034-8c4c-02cafd15d68a}" ) != NULL )
							{
								skip = true;
							} else	
							{
								devNum++;
								skip = false;
							} 
						} else
							devNum++;

						slog((env, "BAD Tuner index :%d dev:%s name:'%s' id:'%s' (%d %d) %s\r\n", devNum, devName, Name, 
							devBDAInfo->hardware_id, devNum, i, skip?"skip":"select" ) );
						char *p = strstr( Name, "\\\\" );
						if ( p != NULL && !stricmp( p, devName ) )
						{
							bFound = TRUE;
							break;
						}
					}
				}

			}
			hr = graphTools.EnumFilterPathNext( pEnum, &pName  );
		}
		
		if ( pEnum != NULL )
			graphTools.EnumFilterPathClose( pEnum );
	}
	catch (...)
	{
		if ( pEnum != NULL )
			graphTools.EnumFilterPathClose( pEnum );
		return -3;
	}

	if ( bFound )
		return devNum-1;

	return -2;

}

BOOL CheckFakeBDACrossBar( JNIEnv *env, char* capFiltName, int CapFiltNum, char* BDAFiltDevName, int BDAFiltDevNameSize )
{
	IEnumMoniker *pEnum = NULL;
	LPWSTR pName;
	int hasBDAInput=0;
	char devName[512]={0};
	char captureName[256]={0};
	char* captureArray[MAX_BDA_TUNER_NUM]={0};
	int  nTunerIndex = -1;
	CLSID CapClassidCategory;
    DEVICE_DRV_INF  BDACaptureDrvInfo;
	DEVICE_DRV_INF  VideoCaptureDrvInfo;
    // slog((env, "CheckFakeBDACrossBar Entry: capFiltName %s, CapFiltNum %d \r\n", capFiltName, CapFiltNum));
    try
	{
		IBaseFilter* pFilter = NULL;
		HRESULT hr = FindFilterByName(&pFilter, AM_KSCATEGORY_CAPTURE,
					capFiltName, CapFiltNum, devName, sizeof( devName ) );
		CapClassidCategory = AM_KSCATEGORY_CAPTURE;
		if ( FAILED( hr ) )
		{
			hr = FindFilterByName2(&pFilter, KSCATEGORY_BDA_RECEIVER_COMPONENT,
					capFiltName, CapFiltNum, devName, sizeof( devName ) );
			CapClassidCategory = KSCATEGORY_BDA_RECEIVER_COMPONENT;
			if ( FAILED( hr ) )
			{
				hr = FindFilterByName2(&pFilter, KSCATEGORY_BDA_NETWORK_TUNER,
						capFiltName, CapFiltNum, devName, sizeof( devName ) ); //ZQ for Hauppauge DVB-S/S2
				CapClassidCategory = KSCATEGORY_BDA_NETWORK_TUNER;
			}
		}
		SAFE_RELEASE(pFilter);
		if ( FAILED( hr ) )
		{
			slog((env, "Failed finding BDA filter by name %s-%d, '%s' hr=0x%x \r\n", capFiltName, CapFiltNum, devName, hr ) );
			return FALSE;
		}
		//it's a BDA device
        if (CapClassidCategory == KSCATEGORY_BDA_RECEIVER_COMPONENT || CapClassidCategory == KSCATEGORY_BDA_NETWORK_TUNER)
        {
            GetDeviceInfo(devName, &VideoCaptureDrvInfo);
            strncpy(BDAFiltDevName, devName, BDAFiltDevNameSize);
            slog((env, "BDA capture is found on location:'%s' id:'%s' for %s-%d (%s) (it's a BDA only). CapClassCat= %s \r\n",
                VideoCaptureDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_id,
                capFiltName, CapFiltNum, devName, (CapClassidCategory == KSCATEGORY_BDA_RECEIVER_COMPONENT) ? "Rcvr" : "Network"));
            //nTunerIndex = GetTunerNum( env, devName, CapClassidCategory, &VideoCaptureDrvInfo );
            return 1;
		}
		GetDeviceInfo( devName, &VideoCaptureDrvInfo );
		if ( VideoCaptureDrvInfo.hardware_loc[0] == 0x0 )
			slog((env, "Use Hardware ID:%s to match\r\n",  VideoCaptureDrvInfo.hardware_id ));
		nTunerIndex = GetTunerNum( env, devName, CapClassidCategory, &VideoCaptureDrvInfo );
		slog((env, "Check BDA capture device:%s-%d (%s) on location:'%s' id:'%s' [state%d, index:%d] .\r\n",  capFiltName, CapFiltNum,
			        devName, VideoCaptureDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_id, 
					VideoCaptureDrvInfo.state, nTunerIndex ) );
		if ( VideoCaptureDrvInfo.state <= 0 )
			return FALSE;
		
		hasBDAInput = 0;
		hr = graphTools.EnumFilterPathFirst( KSCATEGORY_BDA_RECEIVER_COMPONENT, &pEnum, &pName ); 
		while ( hr == S_OK )
		{
			int length = wcslen(pName);
			length =length > sizeof(captureName)? sizeof(captureName) : length;
			memset( captureName, 0x0, sizeof(captureName) );
			wcstombs( captureName, pName, length );
			delete pName;
			GetDeviceInfo( captureName, &BDACaptureDrvInfo );
			slog((env, ">>BDA capture device found:%s; loc:'%s' id:'%s' guid:%s desc:%s\r\n", 
				captureName, BDACaptureDrvInfo.hardware_loc, BDACaptureDrvInfo.hardware_id, BDACaptureDrvInfo.inst_guid2, BDACaptureDrvInfo.device_desc ) );
			if ( BDACaptureDrvInfo.state != 0  )
			{
				if (  BDACaptureDrvInfo.hardware_loc[0] )
				{
					if ( !strcmp( BDACaptureDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_loc ) )
					{
						if ( hasBDAInput < MAX_BDA_TUNER_NUM )
						{
							captureArray[hasBDAInput] = new char[256];
							strncpy( captureArray[hasBDAInput], captureName, 256 );
							slog((env, "BDA Capture found: (%d) %s on loc %s.\r\n", hasBDAInput, captureName, VideoCaptureDrvInfo.hardware_loc) );
							hasBDAInput++;
						}
					}
				} else
				{
					//Hauppauge DVB-S/S2 use device@stream, no hardware loc defined
					//slog((env, "Use Hardware ID:%s to match\r\n",  BDACaptureDrvInfo.hardware_id[0] ));
					if ( BDACaptureDrvInfo.hardware_id[0] && !strcmp( BDACaptureDrvInfo.hardware_id, VideoCaptureDrvInfo.hardware_id ) )
					{
						if ( hasBDAInput < MAX_BDA_TUNER_NUM )
						{
							captureArray[hasBDAInput] = new char[256];
							strncpy( captureArray[hasBDAInput], captureName, 256 );
							slog((env, "BDA Capture found: (%d) %s on hard_id %s.\r\n", hasBDAInput, captureName, VideoCaptureDrvInfo.hardware_id) );
							hasBDAInput++;
						}
					}
				}
			}
			hr = graphTools.EnumFilterPathNext( pEnum, &pName  );
		}
		if ( pEnum != NULL )
			graphTools.EnumFilterPathClose( pEnum );
	}
	catch (...)
	{
		if ( pEnum != NULL )
			graphTools.EnumFilterPathClose( pEnum );
	}
	if ( hasBDAInput==0 )
		slog((env, "BDA capture is not found on location:'%s' id:'%s' for %s.\r\n",  
		                       VideoCaptureDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_id, capFiltName  ) );
	else
	{
		if ( hasBDAInput == 1)
		{
			if ( BDAFiltDevName != NULL )
				strncpy( BDAFiltDevName, captureArray[0], BDAFiltDevNameSize );
			slog((env, "BDA capture is found on location:'%s' id '%s' for %s-%d (%s)..\r\n",  
						VideoCaptureDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_id,
						capFiltName, CapFiltNum, captureArray[0]  ) );
		}
		else
		{
			if (  nTunerIndex >= 0 && nTunerIndex <MAX_BDA_TUNER_NUM && captureArray[nTunerIndex] != NULL )
			{
				if ( BDAFiltDevName != NULL )
					strncpy( BDAFiltDevName, captureArray[nTunerIndex], BDAFiltDevNameSize );
				slog((env, "Mutil-tuner(%d) BDA capture is found on location:'%s' id:'%s' for %s-%d. (%s,%d,desc:%s).\r\n", hasBDAInput,
					    VideoCaptureDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_id, 
						capFiltName, CapFiltNum, captureArray[nTunerIndex], nTunerIndex, VideoCaptureDrvInfo.device_desc ) );
			} else
			{
				char *p = "";
				if (  nTunerIndex >= 0 && nTunerIndex <MAX_BDA_TUNER_NUM &&  captureArray[nTunerIndex] != NULL ) 
					p =  captureArray[nTunerIndex];
				slog((env, "ERROR: Mutil-tuner(%d) BDA capture is found on location:'%s' id '%s' for %s-%d.(%s,%d, desc:%s) , matching failed.\r\n", 
					        hasBDAInput, 
					        VideoCaptureDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_id, 
							capFiltName, CapFiltNum, p, nTunerIndex, VideoCaptureDrvInfo.device_desc ) );
			}
		}
	}

	for ( int i = 0; i<MAX_BDA_TUNER_NUM; i++ )
	  if ( captureArray[i] ) delete captureArray[i];

	return hasBDAInput; // defined as BOOL, but actually returns values > 1
}

