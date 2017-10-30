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
#include "../../include/guids.h"
#include "../../include/sage_DShowCaptureDevice.h"
#include "DShowUtilities.h"
#include "FilterGraphTools.h"
#include "../ax/TSSplitter2.0/iTSSplitter.h"

#include "Channel.h"
#include "QAMCtrl.h"      
#include "TunerPlugin.h"      
#include "uniapi.h"

extern FilterGraphTools graphTools;
TV_TYPE GetTVType( DShowCaptureInfo *pCapInfo );
char * TVTypeString( TV_TYPE BDATVType );
TV_TYPE String2TVType( char* szTvType );
HRESULT RetreveBDAChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, long *plId1, long* plId2, long* plId3 );
void BDAGraphConnectFilter( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void AddBDAVideoCaptureFilters(JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, int devCaps );
void BDAGraphConnectDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void BDAGraphDisconnectDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void ClearUpDebugFileSource( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
HRESULT TurnBDAChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, const char* cnum  );
HRESULT ChannelSinalStrength( JNIEnv *env, DShowCaptureInfo* pCapInfo, long *strength, long* quality, bool* locked );
void BDAGraphConnectDebugRawDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph  );
void BDAGraphSetDebugRawDumpFileName( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, char* Channel  );
void BDAGraphSetDebugFileSource( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph  );
HRESULT SetupBDAStreamOutFormat( JNIEnv *env, DShowCaptureInfo* pCapInfo, int streamType );
int  LoadTuningEntryTable( JNIEnv* env, DShowCaptureInfo *pCapInfo );
void SetupDefaultDVBTuningTable( DShowCaptureInfo *pCapInfo );
void SetupDefaultATSCTuningTable( DShowCaptureInfo *pCapInfo );
int GetTuningProgramNum( DShowCaptureInfo* pCapInfo, long channel );
int GetTuningProgramNumByMajorMinor( DShowCaptureInfo* pCapInfo, int lMajor, int lMinor );
int GetTuningProgramNumByPhysicalMajorMinor( DShowCaptureInfo* pCapInfo, int lPhyscal, int lMajor, int lMinor );
bool IsFullTuningData( DShowCaptureInfo* pCapInfo, long channel );
void SetupCAM( JNIEnv* env, DShowCaptureInfo *pCapInfo );
int SetupSatelliteLNB( DShowCaptureInfo* pCapInfo, int bReload );
int LoadTuneTable( JNIEnv* env, DShowCaptureInfo *pCapInfo, TV_TYPE BDATVType );
int BDATypeNum( DWORD dwBDACap );
char* TVTypeString( TV_TYPE BDATVType );

extern "C" { int  SwitchCamChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int serviceId, int encryptionFlag ); }

#define INSTANCE_DATA_OF_PROPERTY_PTR(x) ( (PKSPROPERTY((x)) ) + 1 )
#define INSTANCE_DATA_OF_PROPERTY_SIZE(x) ( sizeof((x)) - sizeof(KSPROPERTY) )

HRESULT SetVboxFrequency( JNIEnv *env, DShowCaptureInfo* pCapInfo, ULONG ulFrequency );
unsigned long USA_AIR_FREQ[];
/*
 * Class:     sage_DShowCaptureDevice
 * Method:    switchToConnector0
 * Signature: (JIILjava/lang/String;II)V
 */
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_switchToConnector0
  (JNIEnv *env, jobject jo, jlong capInfo, jint crossType, jint crossIndex, jstring tuningMode,
  jint countryCode, jint videoFormatCode)
{
	char szTuningMode[16];
	if (!capInfo) return;
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	pCapInfo->videoFormatCode = videoFormatCode;

	const char* pTuningMode = env->GetStringUTFChars(tuningMode, NULL);
	strncpy( szTuningMode, pTuningMode, sizeof(szTuningMode) );
	env->ReleaseStringUTFChars(tuningMode, pTuningMode);
	slog((env, "switchToConnector0 tuningMode:%s.\r\n", szTuningMode ));

	if ( String2TVType( szTuningMode ) && BDATypeNum( pCapInfo->captureConfig ) > 0 ) //ZQ REMOVE ME
	{
		TV_TYPE newBDAType = String2TVType( szTuningMode );
		if ( pCapInfo->dwBDAType != newBDAType && pCapInfo->dwBDAType > 0 )
		{
			int i, CaptureNum = pCapInfo->captureNum;
			for ( i = 0; i < CaptureNum; i++ )
				if ( pCapInfo->captures[i] && pCapInfo->captures[i]->dwBDAType == pCapInfo->dwBDAType )
					break;
			if ( i >= CaptureNum )
			{
				slog((env, "switchToConnector0 ERROR: Orignal BDA Capture :%d is not found\r\n",  pCapInfo->dwBDAType ));
				ASSERT( 0 );
				return;
			}

			//save back
			memcpy( pCapInfo->captures[i], pCapInfo,  sizeof(DShowCaptureInfo) );

			for ( i = 0; i < CaptureNum; i++ )
				if ( pCapInfo->captures[i] && pCapInfo->captures[i]->dwBDAType == newBDAType )
					break;

			if ( i >= CaptureNum )
			{
				slog((env, "switchToConnector0 ERROR: BDA Capture :%s is not found\r\n",  szTuningMode ));
				ASSERT( 0 );
				return;
			}
			memcpy( pCapInfo, pCapInfo->captures[i], sizeof(DShowCaptureInfo) );
			setChannelDev( (CHANNEL_DATA*)pCapInfo->channel, (void*)pCapInfo );
			slog((env, "switchToConnector0 BDA Capture :%s is switched.\r\n",  szTuningMode ));

		}

		//strncpy( pCapInfo->tvType, szTuningMode, sizeof(pCapInfo->tvType) );
		return;
	}

	if (!pCapInfo->pCrossbar)
		return;
	slog((env, "switchToConnector0 %d type:%d index:%d country:%d format:%d Mode:%s\r\n", 
			(int)capInfo, crossType, crossIndex, countryCode, videoFormatCode, szTuningMode ));


	strncpy( pCapInfo->TuningMode, szTuningMode, sizeof(pCapInfo->TuningMode) );
	// Setup the tuner first since it's upstream from the crossbar
	if (crossType == 1 && pCapInfo->pTVTuner)
	{
		IAMTVTuner* pTunerProps = NULL;
		HRESULT hr = pCapInfo->pTVTuner->QueryInterface(IID_IAMTVTuner, (void**)&pTunerProps);
		if (SUCCEEDED(hr))
		{
			HRESULT ccHr = S_OK;
			if (countryCode)
			{
				long currCountry = 0;
				hr = pTunerProps->get_CountryCode(&currCountry);
				if (FAILED(hr) || currCountry != countryCode)
				{
					hr = ccHr = pTunerProps->put_CountryCode(countryCode);
					HTESTPRINT(hr);
				}
				hr = pTunerProps->put_TuningSpace(countryCode);

				HTESTPRINT(hr);
			}
			AMTunerModeType currMode;
			TunerInputType currTuneType;
			HRESULT currModehr = pTunerProps->get_Mode(&currMode);
			HTESTPRINT(currModehr);
			HRESULT currTypehr = pTunerProps->get_InputType(0, &currTuneType);
			HTESTPRINT(currTypehr);
			AMTunerModeType newMode;
			TunerInputType tuneType;
			slog((env, "Tuning mode:%s; current tuning type:%d current  tuning model:%d\r\n", pCapInfo->TuningMode, currTuneType, currMode  ));
			if (!strcmp(pCapInfo->TuningMode, "Air"))
			{
				newMode = AMTUNER_MODE_TV;
				tuneType = TunerInputAntenna;
			}
			else if (!strcmp(pCapInfo->TuningMode, "FM Radio"))
			{
				newMode = AMTUNER_MODE_FM_RADIO;
				tuneType = TunerInputAntenna;
			}
			else
			if (!strcmp(pCapInfo->TuningMode, "HRC"))
			{
				newMode = AMTUNER_MODE_TV;
				tuneType = (TunerInputType)2;
			} else
			{
				newMode = AMTUNER_MODE_TV;
				tuneType = TunerInputCable;
			}
			if (FAILED(currModehr) || newMode != currMode)
			{
				hr = pTunerProps->put_Mode(newMode);
				HTESTPRINT(hr);
			}
			if (FAILED(currTypehr) || tuneType != currTuneType)
			{
				hr = pTunerProps->put_InputType(0, tuneType);
				HTESTPRINT(hr);
			}
		
			long currConnInput = 0;
			hr = pTunerProps->get_ConnectInput(&currConnInput);
			if (FAILED(hr) || currConnInput != 0)
			{
				hr = pTunerProps->put_ConnectInput(0);
				HTESTPRINT(hr);
			}
			//long tvFormat;
			//hr = pTunerProps->get_TVFormat(&tvFormat);
//ZQ test
/*
{
	IKsPropertySet *pKSProp=NULL;

	KSPROPERTY_TUNER_STANDARD_S Standard;
	hr = pCapInfo->pTVTuner->QueryInterface(IID_IKsPropertySet, (PVOID *)&pKSProp);
	if ( SUCCEEDED(hr) )
	{
		memset(&Standard,0,sizeof(KSPROPERTY_TUNER_STANDARD_S));
		Standard.Standard=videoFormatCode;
		
		HRESULT hr=pKSProp->Set(PROPSETID_TUNER,
					KSPROPERTY_TUNER_STANDARD,
					INSTANCE_DATA_OF_PROPERTY_PTR(&Standard),	
					INSTANCE_DATA_OF_PROPERTY_SIZE(Standard),
					&Standard,	sizeof(Standard));
		if(FAILED(hr))
		{
			slog(( env, "Failed set Video Format:%d on TVTuner hr=0x%x \r\n", videoFormatCode, hr  ));
		} else
		{
			slog(( env, "Force to set Video Format:%d on TVTuner hr=0x%x \r\n", videoFormatCode, hr  ));
		}
		SAFE_RELEASE( pKSProp );
	} else
	{
		slog(( env, "Failed to get IKsPropertySet to set Video Format:%d on TVTuner hr=0x%x \r\n", videoFormatCode, hr  ));
	}
}*/
			SAFE_RELEASE(pTunerProps);
		}

		if (pCapInfo->pTVAudio)
		{
			IAMTVAudio* pAudioProps = NULL;
			hr = pCapInfo->pTVAudio->QueryInterface(IID_IAMTVAudio, (void**)&pAudioProps);
			if (SUCCEEDED(hr))
			{
				// For Vista+; there's the 'PRESET' flags which we want to use instead for setting the TV audio
				// selections.
				OSVERSIONINFOEX osInfo;
				ZeroMemory(&osInfo, sizeof(OSVERSIONINFOEX));
				osInfo.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);
				DWORD vistaPlus = 0;
				if (GetVersionEx((LPOSVERSIONINFO)&osInfo))
				{
					if (osInfo.dwMajorVersion >= 6)
						vistaPlus = 1;
				}
				if (vistaPlus)
					hr = pAudioProps->put_TVAudioMode(AMTVAUDIO_PRESET_STEREO | AMTVAUDIO_PRESET_LANG_A);
				else
					hr = pAudioProps->put_TVAudioMode(AMTVAUDIO_MODE_STEREO | AMTVAUDIO_MODE_LANG_A);
				HTESTPRINT(hr);
			}
			SAFE_RELEASE(pAudioProps);
		}
	}



	// Setup the crossbar for the graph
	IAMCrossbar *pXBar1 = NULL;

	HRESULT hr = pCapInfo->pCrossbar->QueryInterface(IID_IAMCrossbar, (void**)&pXBar1);
	HTESTPRINT(hr);

    // This handles A-V "related" pins, as reported by the driver
	// Look through the pins on the crossbar and find the correct one for the type of
	// connector we're routing. Also find the aligned audio pin and set that too.
	int tempCrossIndex = crossIndex;
	long i;
	long videoOutNum = -1;
	long audioOutNum = -1;
	long numIn, numOut;
	hr = pXBar1->get_PinCounts(&numOut, &numIn);
	HTESTPRINT(hr);
	long relatedPin, pinType;
	for (i = 0; i < numOut; i++)
	{
		hr = pXBar1->get_CrossbarPinInfo(FALSE, i, &relatedPin, &pinType);
		HTESTPRINT(hr);
		if (pinType == PhysConn_Video_VideoDecoder)
			videoOutNum = i;
		else if (pinType == PhysConn_Audio_AudioDecoder)
			audioOutNum = i;
	}
	for (i = 0; i < numIn; i++)
	{
		hr = pXBar1->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);
		HTESTPRINT(hr);

		if (pinType == crossType || (pinType == PhysConn_Video_YRYBY && crossType == 90)) // 90 is Component+SPDIF; YPBPR_SPDIF_CROSSBAR_INDEX
        {
			if ((crossType != 1 && tempCrossIndex > 0) ||
				tempCrossIndex == 1)
			{
				tempCrossIndex--;
				continue;
			}
			// Route the video
			long currRoute = -1;
//			hr = pXBar1->get_IsRoutedTo(videoOutNum, &currRoute);
//			if (FAILED(hr) || currRoute != i)
			{
				hr = pXBar1->Route(videoOutNum, i);
				HTESTPRINT(hr);
			}
			
			if (audioOutNum >= 0)
			{
				if (crossType == PhysConn_Video_YRYBY || crossType == 90)
				{
					long relatedPinType = 0;
					long junk = 0;
					pXBar1->get_CrossbarPinInfo(TRUE, relatedPin, &junk, &relatedPinType);
                    if ((relatedPinType != PhysConn_Audio_SPDIFDigital && crossType == 90) ||
                        (relatedPinType == PhysConn_Audio_SPDIFDigital && crossType == PhysConn_Video_YRYBY))
                    {
                        // Find the other audio input pin that's related to the component input and use that
                        int j;
                        long otherRelatedPin = 0;
                        for (j = 0; j < numIn; j++)
                        {
                            if (j == relatedPin) continue;
                            otherRelatedPin = 0;
                            pXBar1->get_CrossbarPinInfo(TRUE, j, &otherRelatedPin, &junk);
                            if (otherRelatedPin == i)
                            {
                                slog(( env, "Crossbar swapping related audio pins on component video input old:%d new:%d\r\n", relatedPin, j));
                                relatedPin = j;
                                break;
                            }
                        }
                    }
					
				}
				// Route any related audio
//				hr = pXBar1->get_IsRoutedTo(audioOutNum, &currRoute);
//				if (FAILED(hr) || currRoute != relatedPin)
				{
					hr = pXBar1->Route(audioOutNum, relatedPin);
					HTESTPRINT(hr);
				}
			}
			slog(( env, "Crossbar route: videoIn pin:%d, auido:%d videoOutNum=%d audioOutNum=%d \r\n", i, relatedPin, videoOutNum, audioOutNum));
			break;
		}

        // Normal HDMI (V=HDMI and A='related' pin, as defined in .inf) was handled above.
        // Also handle other A-V pin-pairs, even though the driver might not report them as related.
        // Example: Hauppauge HD-PVR2 supports HDMI+SPDIF, but driver doesn't report that combo.
        // so we don't look for 'relatedPin' here, instead relying on getCrossbarConnections0() to have 
        // previously confirmed presense of HDMI & some other audio pin(s).  
        // Codes 80-89 are only possible if we already know the device supports it.
        else if ((pinType == PhysConn_Video_SerialDigital) && (crossType >= 80) && (crossType <= 89) ) // 80-89 is HDMI+<something>
        {
            if ((crossType != 1 && tempCrossIndex > 0) ||
                tempCrossIndex == 1)
            {
                tempCrossIndex--;
                continue;
            }

            // Route the video
            long currRoute = -1;
            //			hr = pXBar1->get_IsRoutedTo(videoOutNum, &currRoute);
            //			if (FAILED(hr) || currRoute != i)
            {
                hr = pXBar1->Route(videoOutNum, i);
                HTESTPRINT(hr);
            }

            if (audioOutNum >= 0)
            {
                long audioHdmiPinType;
                long junk = 0;
                switch (crossType)
                {
                case 80:    // HDMI+LineIn; HDMI_LINEIN_CROSSBAR_INDEX
                    audioHdmiPinType = PhysConn_Audio_Line;
                    break;
                case 81:    // HDMI+HDMI; HDMI_AES_CROSSBAR_INDEX
                    audioHdmiPinType = PhysConn_Audio_AESDigital;
                    break;
                case 82:    // HDMI+SPDIF; HDMI_SPDIF_CROSSBAR_INDEX
                    audioHdmiPinType = PhysConn_Audio_SPDIFDigital;
                    break;
                default:    // use 'related' audio pin; should never happen
                    pXBar1->get_CrossbarPinInfo(TRUE, relatedPin, &junk, &audioHdmiPinType);
                    slog((env, "using default related audio pin; relatedPin=%d pinType=%d \r\n", relatedPin, audioHdmiPinType));
                    break;
                }

                // Find the desired audio input pin
                int j;
                for (j = 0; j < numIn; j++)
                {
                    pXBar1->get_CrossbarPinInfo(TRUE, j, &junk, &pinType);
                    if (pinType == audioHdmiPinType)
                    {
                        slog((env, "Located audio input pin %d; HDMI video input pin %d \r\n", j, i));
                        // Route any related audio
                        //				hr = pXBar1->get_IsRoutedTo(audioOutNum, &currRoute);
                        //				if (FAILED(hr) || currRoute != j)
                        {
                            hr = pXBar1->Route(audioOutNum, j);
                            HTESTPRINT(hr);
                        }
                        slog((env, "Crossbar route: video:%d, auido:%d \r\n", i, j));
                        break;
                    }
                }
            }
            break;
        }
    }

	SAFE_RELEASE(pXBar1);

	if (audioOutNum == -1)
	{
		// It may have 2 crossbars, like ATI. Search for the second one.
		hr = pCapInfo->pBuilder->FindInterface(&LOOK_UPSTREAM_ONLY, NULL, pCapInfo->pCrossbar,
			IID_IAMCrossbar, (void**)&pXBar1);
		if (SUCCEEDED(hr))
		{
			slog((env, "Found secondary audio crossbar, routing it\r\n"));
			tempCrossIndex = crossIndex;
			hr = pXBar1->get_PinCounts(&numOut, &numIn);
			HTESTPRINT(hr);
			for (i = 0; i < numOut; i++)
			{
				hr = pXBar1->get_CrossbarPinInfo(FALSE, i, &relatedPin, &pinType);
				HTESTPRINT(hr);
				if (pinType == PhysConn_Audio_AudioDecoder)
				{
					audioOutNum = i;
					break;
				}
			}
			for (i = 0; i < numIn && audioOutNum >= 0; i++)
			{
				hr = pXBar1->get_CrossbarPinInfo(TRUE, i, &relatedPin, &pinType);
				HTESTPRINT(hr);
				if (pinType == crossType)
				{
					if ((crossType != 1 && tempCrossIndex > 0) ||
						tempCrossIndex == 1)
					{
						tempCrossIndex--;
						continue;
					}
					// Route any related audio
					hr = pXBar1->Route(audioOutNum, relatedPin);
					HTESTPRINT(hr);
					break;
				}
			}
			SAFE_RELEASE(pXBar1);
		}
	}

	IAMAnalogVideoDecoder *vidDec = NULL;
	hr = pCapInfo->pVideoCaptureFilter->QueryInterface(IID_IAMAnalogVideoDecoder, (void**)&vidDec);
	if (SUCCEEDED(hr))
	{
		/*if (FAILED(ccHr) && countryCode == 54) 
		{
			tvFormat = AnalogVideo_PAL_N;
		}*/
		hr = vidDec->put_TVFormat(videoFormatCode);
		HTESTPRINT(hr);
		/*if (FAILED(hr) && tvFormat == AnalogVideo_PAL_N) 
		{ 
			hr = vidDec->put_TVFormat(AnalogVideo_PAL_B);
		} */
		SAFE_RELEASE(vidDec);
	}
	slog((env, "DONE: switchToConnector0 %d type=%d index=%d\r\n", (int)capInfo, crossType, crossIndex));
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    tuneToChannel0
 * Signature: (JLjava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_tuneToChannel0
  (JNIEnv *env, jobject jo, jlong capInfo, jstring jnum, jint streamType)
{
	if (!capInfo) return;
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	//ZQ
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ))
	{
		const char* cnum = env->GetStringUTFChars(jnum, NULL);
		slog((env, "tuneToChannel0 digital tuner '%s-%d' num=%s (ver 3.1)\r\n", pCapInfo->videoCaptureFilterName, 
			        pCapInfo->videoCaptureFilterNum, cnum ));

		
		SetupBDAStreamOutFormat( env, pCapInfo, streamType );
		HRESULT hr = TurnBDAChannel( env, pCapInfo, cnum  );
		env->ReleaseStringUTFChars(jnum, cnum);

	}else //ZQ
	{
		if (!pCapInfo->pTVTuner) return;
		const char* cnum = env->GetStringUTFChars(jnum, NULL);
		int numericChannel = atoi(cnum);
		long lFreq = 0, lTVFormat;
		env->ReleaseStringUTFChars(jnum, cnum);
		if ( numericChannel == 0 || numericChannel < 0 )
			return ;

		IAMTVTuner* pTunerProps = NULL;
		HRESULT hr = pCapInfo->pTVTuner->QueryInterface(IID_IAMTVTuner, (void**)&pTunerProps);
		slog((env, "tuneToChannel0 %d hr=0x%x num=%d\r\n", (int) capInfo, hr, numericChannel));
		if (SUCCEEDED(hr))
		{
			
			pTunerProps->put_Channel(numericChannel, AMTUNER_SUBCHAN_DEFAULT, AMTUNER_SUBCHAN_DEFAULT);
			HRESULT hr2 = pTunerProps->get_VideoFrequency( &lFreq );
			        hr2 = pTunerProps->get_TVFormat( &lTVFormat );
			SAFE_RELEASE(pTunerProps);
			pCapInfo->dwTuneState = 0x01;
		}
		slog((env, "DONE: tuneToChannel0 %d hr=0x%x freq:%d TVFormat:%d\r\n", 
			                                 (int)capInfo, hr, lFreq, lTVFormat  ));
	}
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    autoTuneChannel0
 * Signature: (JLjava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowCaptureDevice_autoTuneChannel0
  (JNIEnv *env, jobject jo, jlong capInfo, jstring jnum, jint streamType )
{
	if (!capInfo) return JNI_FALSE;
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*)capInfo;

	////ZQ audio leaking
	//pCapInfo->pMC->Run();
	//slog((env, ">>>>  Start (capture:%s) \r\n"));


	//ZQ
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ))
	{
		HRESULT hr;
		const char* cnum = env->GetStringUTFChars(jnum, NULL);
		if ( cnum == NULL || *cnum == 0x0 ) cnum = "0";
		slog((env, "autotune0 digital tuner '%s-%d' num=%s (ver 3.1)\r\n", pCapInfo->videoCaptureFilterName, 
			        pCapInfo->videoCaptureFilterNum, cnum ));

		//setup output format
		hr = SetupBDAStreamOutFormat( env, pCapInfo, streamType );

		hr = TurnBDAChannel( env, pCapInfo, cnum );
		env->ReleaseStringUTFChars(jnum, cnum);


		int locked = SageCheckLocked( pCapInfo );
		slog((env, "DONE: autotune0 hr=0x%x locked:%d\r\n", hr, locked ));

		return hr == S_OK ? JNI_TRUE : JNI_FALSE;
        
	}else //ZQ
	{
		if (!pCapInfo->pTVTuner) return JNI_FALSE;
		const char* cnum = env->GetStringUTFChars(jnum, NULL);
		int numericChannel = atoi(cnum);
		env->ReleaseStringUTFChars(jnum, cnum);
		IAMTVTuner* pTunerProps = NULL;
		long tuneResult = 0;
		if ( numericChannel == 0 || numericChannel < 0 )
			return JNI_FALSE;

		long lFreq = 0;
		HRESULT hr = pCapInfo->pTVTuner->QueryInterface(IID_IAMTVTuner, (void**)&pTunerProps);
		slog((env, "autotune0 analog tuner '%s-%d' hr=0x%x num=%d\r\n", pCapInfo->videoCaptureFilterName, 
					pCapInfo->videoCaptureFilterNum,	hr, numericChannel));
		if (SUCCEEDED(hr))
		{
			hr = pTunerProps->AutoTune(numericChannel, &tuneResult);

			HRESULT hr2 = pTunerProps->get_VideoFrequency( &lFreq );
			//if ( tuneResult )
			//	pTunerProps->StoreAutoTune();
			pCapInfo->dwTuneState = 0x01;
			//Fusion Card, FIX: after ATSC tune, fail to tune TV 
			if ( strstr( pCapInfo->videoCaptureFilterName, "Fusion" ) )
				pTunerProps->put_Mode( AMTUNER_MODE_FM_RADIO );  
			pTunerProps->put_Mode( AMTUNER_MODE_TV );  //ZQ. 
			SAFE_RELEASE(pTunerProps);
		}
		slog((env, "DONE: autotune0 %d hr=0x%x result=%d  freq:%d.\r\n", 
			(int)capInfo, hr, tuneResult, lFreq ));
		return (SUCCEEDED(hr) && (tuneResult != 0));
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    getChannel0
 * Signature: (JI)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_DShowCaptureDevice_getChannel0
	(JNIEnv *env, jobject jo, jlong capInfo, jint chanType)
{
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if (!pCapInfo )
		return env->NewStringUTF("0");

	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
		return env->NewStringUTF("0");

	//ZQ
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ))
	{
		HRESULT hr;
		long lPhysicalChannel, lMinorChannel, lMajorChannel;
		char rvBuf[32];
		hr = RetreveBDAChannel( env, pCapInfo, &lPhysicalChannel, &lMinorChannel, &lMajorChannel );
		if ( FAILED( hr ) )
			return env->NewStringUTF("0");
		if ( lMajorChannel > 0 && lMinorChannel > 0 )
		{
			SPRINTF(rvBuf, sizeof(rvBuf), "%d-%d-%d", lPhysicalChannel, lMajorChannel, lMinorChannel );
			return env->NewStringUTF(rvBuf);
		} else
		if ( lMinorChannel > 0 )
		{
			SPRINTF(rvBuf, sizeof(rvBuf), "%d-%d", lPhysicalChannel, lMinorChannel );
			return env->NewStringUTF(rvBuf);
		} else
		{
			SPRINTF(rvBuf, sizeof(rvBuf), "%d", lPhysicalChannel );
			return env->NewStringUTF(rvBuf);
		}

	} else 
	try
	{
	
		if ( !pCapInfo->pTVTuner )
			return env->NewStringUTF("0");

		CComPtr<IAMTVTuner> pTunerProps = NULL;
		HRESULT hr = pCapInfo->pTVTuner->QueryInterface(IID_IAMTVTuner, (void**)&(pTunerProps.p));
		if (FAILED(hr))
			return env->NewStringUTF("0");

		long currChan, videoSub, audioSub;
		if (chanType == 0)
			hr = pTunerProps->get_Channel(&currChan, &videoSub, &audioSub);
		else if (chanType == 1)
			hr = pTunerProps->ChannelMinMax(&videoSub, &currChan);
		else // chanType == -1
			hr = pTunerProps->ChannelMinMax(&currChan, &videoSub);
		if (FAILED(hr)) 
			return env->NewStringUTF("0");
		else
		{
			char rvBuf[8];
			SPRINTF(rvBuf, sizeof(rvBuf), "%d", (int)currChan);
			return env->NewStringUTF(rvBuf);
		}
	}
	catch (...)
	{
		slog((env, "NATIVE EXCEPTION getting current channel capInfo=%d chanType=%d\r\n",
			(int) capInfo, (int) chanType));
		return env->NewStringUTF("0");
	}
	//ZQ
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    getSignalStrength0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DShowCaptureDevice_getSignalStrength0
  (JNIEnv *env, jobject jo, jlong capInfo)
{
	if (!capInfo) return JNI_FALSE;
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;

	//ZQ
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ))
	{
		if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
			return 100;

		long strength =0, quality = 0;
		HRESULT hr = ChannelSinalStrength(env, pCapInfo, &strength, &quality, NULL);
		slog((env, "DTV Signal Strength=%d %d\r\n", strength, quality));
		return hr == S_OK ?  quality: 0;
	}
	else
	{
		return 100;
	}
}



HRESULT TuneStandardBDAChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int physical, int major_ch, int minor_ch  );
HRESULT TuneScanningBDAChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lMajorChannel, long lMinorChannel, long lProgram );
HRESULT TunePhysicalBDAChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, int physical_ch );
HRESULT ChangeATSCChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhysicalChannel,long lMajorChannel, long lMinorChannel );

HRESULT TurnBDAChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, const char* cnum  )
{
	//if ( pCapInfo->pBDANetworkProvider == NULL ) //ZQ.  should call setup setupEncoder0 first! before call autoTuneChannel0. buf ???
    if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK )) //ZQ.  should call setup setupEncoder0 first! before call autoTuneChannel0. buf ???
	{
		if ( pCapInfo->pSink == NULL ) 
		{
			HRESULT hr = CoCreateInstance(CLSID_MPEG2Dump, NULL, CLSCTX_INPROC_SERVER,
			IID_IBaseFilter, (void**)&(pCapInfo->pSink));
			if ( FAILED( hr ) )
				return hr;
			hr = pCapInfo->pGraph->AddFilter(pCapInfo->pSink, L"MPEG Dump");
			if ( FAILED( hr ) )
				return hr;
		}
		AddBDAVideoCaptureFilters( env, pCapInfo, pCapInfo->pGraph, 0 );             
		BDAGraphSetDebugFileSource( env, pCapInfo, pCapInfo->pGraph  );
		BDAGraphConnectFilter( env, pCapInfo,  pCapInfo->pGraph );
		SetupCAM( env, pCapInfo );
		SetupTunerPlugin( env, pCapInfo, GetTVType( pCapInfo ) );
		BDAGraphConnectDebugRawDumpSink( env, pCapInfo, pCapInfo->pGraph  );
       	BDAGraphConnectDumpSink( env, pCapInfo,  pCapInfo->pGraph );
		ClearUpDebugFileSource(  env, pCapInfo, pCapInfo->pGraph  );
		TV_TYPE BDATVType = GetTVType( pCapInfo ); 
		if ( BDATVType == DVBS ) SetupSatelliteLNB( pCapInfo, 0 );
		//if user changed mode, reload tune table
		if ( BDATVType == ATSC && !stricmp( pCapInfo->TuningMode, "Cable" ) && QAMTunerType( env, pCapInfo ) == 1  )
			BDATVType = QAM;
		if ( strcmp( getSourceType( (CHANNEL_DATA*)pCapInfo->channel ), TVTypeString( BDATVType ) ) )
		{
			LoadTuneTable( env, pCapInfo, BDATVType );
			if ( BDATVType == DVBS ) SetupSatelliteLNB( pCapInfo, 1 );
		}
		
	}
	BDAGraphSetDebugRawDumpFileName( env, pCapInfo, pCapInfo->pGraph, (char*)cnum  );
//$NEW
	int ret = tuneChannel( (CHANNEL_DATA*)pCapInfo->channel, cnum );
    return ret>0 ? S_OK : E_FAIL;
//$NEW

}


HRESULT  RetreveATSCChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, long *lPhysicalChannel, long* lMinorChannel, long* lMajorChannel )
{
    HRESULT hr = S_OK;

	CComPtr <ITuneRequest> pTuneRequest;
	if (FAILED(hr = pCapInfo->pTuner->get_TuneRequest(&pTuneRequest)))
	{
        slog( ( env, "Cannot retreve ATSC tune request\r\n" ) );
	    return hr;
	}

	CComQIPtr <IATSCChannelTuneRequest> piATSCTuneRequest(pTuneRequest);
	piATSCTuneRequest->get_MinorChannel(lMinorChannel);
	piATSCTuneRequest->get_Channel(lMajorChannel);

	CComPtr <ILocator> piLocator;
	pTuneRequest->get_Locator( &piLocator );
	CComQIPtr <IATSCLocator> piATSCLocator(piLocator);
	piATSCLocator->get_PhysicalChannel( lPhysicalChannel );


    return hr;
}


HRESULT  RetreveDVBChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, long *plOid, long* plSid, long* plTsid )
{
    HRESULT hr = S_OK;

	CComPtr <ITuneRequest> pTuneRequest;
	if (FAILED(hr = pCapInfo->pTuner->get_TuneRequest(&pTuneRequest)))
	{
        slog( ( env, "Cannot retreve DVB tune request\r\n" ) );
	    return hr;
	}

	CComQIPtr <IDVBTuneRequest> piDVBTuneRequest(pTuneRequest);
	piDVBTuneRequest->get_ONID( plOid );
	piDVBTuneRequest->get_SID( plSid );
	piDVBTuneRequest->get_TSID( plTsid );

    return hr;
}

HRESULT  RetreveBDAChannel( JNIEnv *env, DShowCaptureInfo* pCapInfo, long *plId1, long* plId2, long* plId3 )
{
	HRESULT hr = S_OK;
	TV_TYPE TVType = GetTVType( pCapInfo );
	if ( TVType == ATSC )
		hr = RetreveATSCChannel( env, pCapInfo, plId1, plId2, plId3 );
	else
	if ( TVType == DVBT || TVType == DVBC || TVType == DVBS )
		hr = RetreveDVBChannel( env, pCapInfo, plId1, plId2, plId3 );
	else
	{
		slog( (env, "unknow network type %s for retreving\r\n", TVTypeString(TVType) ) );
		hr = E_FAIL;
	}
	return hr;

}


HRESULT  ChannelSinalStrength( JNIEnv *env, DShowCaptureInfo* pCapInfo, long *strength, long* quality, bool* locked )
{
	HRESULT hr;
	CComPtr <IBDA_Topology> bdaNetTop;
	BOOL bBadNetTopFound = FALSE;

	//Get IID_IBDA_Topology
	if ( pCapInfo->pBDATuner == NULL && pCapInfo->pBDACapture == NULL )
		return E_FAIL;
	
	if ( pCapInfo->pBDATuner && !FAILED(hr = pCapInfo->pBDATuner->QueryInterface(&bdaNetTop)) )
	{
		bBadNetTopFound = TRUE;
	}

	if ( !bBadNetTopFound )
	{
		if ( pCapInfo->pBDACapture && !FAILED(hr = pCapInfo->pBDACapture->QueryInterface(&bdaNetTop)) )
			bBadNetTopFound = TRUE;
	}
	
	if ( !bBadNetTopFound )
		return E_FAIL;

	ULONG NodeTypes;
	ULONG NodeType[32];
	ULONG Interfaces;
	GUID Interface[32];
	CComPtr <IUnknown> iNode;

	long longVal = 0;
	BYTE byteVal = 0;
	if (strength)
		*strength = 0;
	if (quality)
		*quality = 0;
	if (locked)
		*locked =  0;

	if (FAILED(hr = bdaNetTop->GetNodeTypes(&NodeTypes, 32, NodeType)))
	{
		bdaNetTop.Release();
	    return hr;
	}

	for ( ULONG i=0 ; i<NodeTypes ; i++ )
	{
		hr = bdaNetTop->GetNodeInterfaces(NodeType[i], &Interfaces, 32, Interface);
		if (hr == S_OK)
		{
			for ( ULONG j=0 ; j<Interfaces ; j++ )
			{
				if (Interface[j] == IID_IBDA_SignalStatistics)
				{
					hr = bdaNetTop->GetControlNode(0, 1, NodeType[i], &iNode);
					if (hr == S_OK)
					{
						CComPtr <IBDA_SignalStatistics> pSigStats;

						hr = iNode.QueryInterface(&pSigStats);
						if (hr == S_OK)
						{
							if (strength && SUCCEEDED(hr = pSigStats->get_SignalStrength(&longVal)))
								*strength = longVal;

							if (quality && SUCCEEDED(hr = pSigStats->get_SignalQuality(&longVal)))
								*quality = longVal;

							if (locked && SUCCEEDED(hr = pSigStats->get_SignalLocked(&byteVal)))
								*locked = byteVal != 0;

							pSigStats.Release();
						}
						iNode.Release();
					}
					break;
				}
			}
		}
	}
	bdaNetTop.Release();

	return S_OK;
}



HRESULT SetupBDAStreamOutFormat( JNIEnv *env, DShowCaptureInfo* pCapInfo, int streamType )
{
	ITSParser2 *pTSParser = NULL;
	HRESULT hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
	{
		slog((env, "get TS Splitter interface failed\r\n" ));
	} else
	{
		if ( streamType == 31 )
		{
			slog( (env,"Splitter Filter set output mpeg2 format \r\n") );
			pTSParser->SetOutputFormat( 1 );
			setOutputFormat( (CHANNEL_DATA*)pCapInfo->channel, 1 );
		} else
		if ( streamType == 32 )
		{
			slog( (env,"Splitter Filter output transport stream format \r\n") );
			pTSParser->SetOutputFormat( 0 );
			setOutputFormat( (CHANNEL_DATA*)pCapInfo->channel, 0 );
		}
	}
	SAFE_RELEASE(pTSParser);
	return hr;
}


//VBOX ATSC customize code for setting channel over 69

#define INSTANCEDATA_OF_PROPERTY_PTR(x) ((PKSPROPERTY((x))) + 1)
#define INSTANCEDATA_OF_PROPERTY_SIZE(x) (sizeof((x)) - sizeof(KSPROPERTY))

HRESULT SetVboxFrequency( JNIEnv *env, DShowCaptureInfo* pCapInfo, ULONG ulFrequency )
{
    HRESULT hr;
    DWORD dwSupported=0;  
    IEnumPins* pEnumPin;
    IPin* pInputPin;
    ULONG ulFetched;
    PIN_INFO infoPin;

	if ( pCapInfo->pBDATuner == NULL )
		return E_FAIL;

	if( ulFrequency == 0 )
	{
		slog( (env,"VOX tuner skips frequency 0\r\n") );
		return S_OK;
	}

    IBaseFilter* pTunerDevice = pCapInfo->pBDATuner; 
    pTunerDevice->EnumPins(&pEnumPin);

    if( SUCCEEDED( hr = pEnumPin->Reset() ) )
    {
		while((hr = pEnumPin->Next( 1, &pInputPin, &ulFetched )) == S_OK)
		{
			pInputPin->QueryPinInfo(&infoPin);
				
			// Release AddRef'd filter, we don't need it
			if( infoPin.pFilter != NULL )
			infoPin.pFilter->Release();

			if(infoPin.dir == PINDIR_INPUT)
			break;
		}

		if(hr != S_OK)
		{
			slog( (env,"Vbox tuner input pin query failed \r\n") );
			return hr;
		}
    }
    else
    {
		slog( (env,"Vbox tuner reset failed \r\n") );
		return E_FAIL;
    }
    
    IKsPropertySet *pKsPropertySet;
    pInputPin->QueryInterface(&pKsPropertySet);
	
    if (!pKsPropertySet)
    {
		slog( (env,"Vbox tuner input pin's QueryInterface failed \r\n") );

		return E_FAIL;
    }
        
    KSPROPERTY_TUNER_MODE_CAPS_S ModeCaps;
    KSPROPERTY_TUNER_FREQUENCY_S Frequency;
    memset(&ModeCaps,0,sizeof(KSPROPERTY_TUNER_MODE_CAPS_S));
    memset(&Frequency,0,sizeof(KSPROPERTY_TUNER_FREQUENCY_S));
    ModeCaps.Mode = AMTUNER_MODE_TV; 

    // Check either the Property is supported or not by the Tuner drivers 

    hr = pKsPropertySet->QuerySupported(PROPSETID_TUNER, 
          KSPROPERTY_TUNER_MODE_CAPS,&dwSupported);
    if(SUCCEEDED(hr) && dwSupported&KSPROPERTY_SUPPORT_GET)
    {
        DWORD cbBytes=0;
        hr = pKsPropertySet->Get(PROPSETID_TUNER,KSPROPERTY_TUNER_MODE_CAPS,
            INSTANCEDATA_OF_PROPERTY_PTR(&ModeCaps),
            INSTANCEDATA_OF_PROPERTY_SIZE(ModeCaps),
            &ModeCaps,
            sizeof(ModeCaps),
            &cbBytes);  
    }
    else
    {
		SAFE_RELEASE(pKsPropertySet);
		slog( (env,"Vbox tuner input pin's not support GET query \r\n") );
        return E_FAIL; 
    }

    Frequency.Frequency=ulFrequency; // in Hz
    if(ModeCaps.Strategy==KS_TUNER_STRATEGY_DRIVER_TUNES)
        Frequency.TuningFlags=KS_TUNER_TUNING_FINE;
    else
        Frequency.TuningFlags=KS_TUNER_TUNING_EXACT;

    // Here the real magic starts
    //if(ulFrequency>=ModeCaps.MinFrequency && ulFrequency<=ModeCaps.MaxFrequency)
    {
        hr = pKsPropertySet->Set(PROPSETID_TUNER,
            KSPROPERTY_TUNER_FREQUENCY,
            INSTANCEDATA_OF_PROPERTY_PTR(&Frequency),
            INSTANCEDATA_OF_PROPERTY_SIZE(Frequency),
            &Frequency,
            sizeof(Frequency));
        if(FAILED(hr))
        {
			slog( (env,"Vbox tuner input pin's set frequency %d failed hr=0x%x\r\n", Frequency.Frequency, hr ) );
			SAFE_RELEASE(pKsPropertySet);
            return E_FAIL; 
        }
    }

  //  else
  //  {
		//slog( (env,"Vbox tuning frequency %d is out of range (%d %d)\r\n", 
		//	          ulFrequency, ModeCaps.MinFrequency, ModeCaps.MaxFrequency ) );
  //      return E_FAIL;
  //  }

	SAFE_RELEASE(pKsPropertySet);
	slog( (env,"Vbox tuner tuning overider frequency %d  successful. \r\n", ulFrequency) );
    return S_OK;
}

unsigned long USA_AIR_FREQ[]=
{ /* 0 */           0L,  /* 1 */           0L,
  /* 2  */   55250000L,  /* 3  */   61250000L,    /* 4  */   67250000L,     /* 5  */   77250000L,    
  /* 6  */   83250000L,   /* 7  */  175250000L,    /* 8  */  181250000L,    /* 9  */  187250000L,    
  /* 10 */  193250000L,   /* 11 */  199250000L,    /* 12 */  205250000L,    /* 13 */  211250000L,    
  /* 14 */  471250000L,   /* 15 */  477250000L,    /* 16 */  483250000L,    /* 17 */  489250000L,    
  /* 18 */  495250000L,   /* 19 */  501250000L,    /* 20 */  507250000L,    /* 21 */  513250000L,    
  /* 22 */  519250000L,   /* 23 */  525250000L,    /* 24 */  531250000L,    /* 25 */  537250000L,    
  /* 26 */  543250000L,   /* 27 */  549250000L,    /* 28 */  555250000L,    /* 29 */  561250000L,    
  /* 30 */  567250000L,   /* 31 */  573250000L,    /* 32 */  579250000L,    /* 33 */  585250000L,    
  /* 34 */  591250000L,   /* 35 */  597250000L,    /* 36 */  603250000L,    /* 37 */  609250000L,    
  /* 38 */  615250000L,   /* 39 */  621250000L,    /* 40 */  627250000L,    /* 41 */  633250000L,    
  /* 42 */  639250000L,   /* 43 */  645250000L,    /* 44 */  651250000L,    /* 45 */  657250000L,    
  /* 46 */  663250000L,   /* 47 */  669250000L,    /* 48 */  675250000L,    /* 49 */  681250000L,    
  /* 50 */  687250000L,   /* 51 */  693250000L,    /* 52 */  699250000L,    /* 53 */  705250000L,    
  /* 54 */  711250000L,   /* 55 */  717250000L,    /* 56 */  723250000L,    /* 57 */  729250000L,    
  /* 58 */  735250000L,   /* 59 */  741250000L,    /* 60 */  747250000L,    /* 61 */  753250000L,    
  /* 62 */  759250000L,   /* 63 */  765250000L,    /* 64 */  771250000L,    /* 65 */  777250000L,    
  /* 66 */  783250000L,   /* 67 */  789250000L,    /* 68 */  795250000L,    /* 69 */  801250000L,
  /* 71 */  807250000L,   /* 71 */  813250000L,    /* 72 */  819250000L,    /* 73 */  825250000L,
  /* 74 */  831250000L,   /* 75 */  837250000L,    /* 76 */  843250000L,    /* 77 */  849250000L,
  /* 78 */  855250000L,   /* 79 */  861250000L,    /* 80 */  867250000L,    /* 81 */  873250000L,
  /* 82 */  879250000L,   /* 83 */  885250000L };
