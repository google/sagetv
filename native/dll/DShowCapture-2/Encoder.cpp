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
#include <streams.h>
#include "DShowCapture.h"

#include "../../include/sage_DShowCaptureDevice.h"
#include "DShowUtilities.h"
#include "../../include/impegmux.h"
#include "../ax/TSSplitter2.0/iTSSplitter.h"

#include <ks.h>
#include <ksmedia.h>
#include <ksproxy.h>
#include "sage_EncodingException.h"
#include "FilterGraphTools.h"
#include "CAMCtrl.h"
#include "TunerPlugin.h"
#include "uniapi.h"

#include "Conexant2341x.h"
#include "CreativeVBDVCR.h"
#include "HCWEncoder.h"
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "../../include/StreamMachine/ismaudio.h"
#include "../../include/StreamMachine/iaudioprops.h"
#include "MainConcept/imceprop.h"
#include "DivX/IDivXResyncFilter.h"
#include "DivX/AVIWriterConfig.h"
#include "Plextor/AUDIO_PREVIEW.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
#include "MCEncoder.h"
//#include "ATI/ATI550.h"
#include "ATITheater.h"
//#include "ArcSoftEncoder.h"
#include "DShowUtilities.h"
#include "QAMCtrl.h"

#define NUM_SPECIAL_YUV_OUTPUT_PIN_NAME_CHECKS 4
char* SPECIAL_YUV_OUTPUT_PIN_NAME_CHECKS[NUM_SPECIAL_YUV_OUTPUT_PIN_NAME_CHECKS] = { "ATI Video Decoder YUV Output", "Analog ITU Video", "Virtual Video Out", "Virtual Video Out" };
// The encoder filter goes with each YUV pin name unless it's null. This is a terrible hack to get around ATI's bad drivers
char* SPECIAL_ENCODER_FILTER_NAME_CHECKS[NUM_SPECIAL_YUV_OUTPUT_PIN_NAME_CHECKS] = { "ATI AVStream MPEG2 Multiplexer", 0 , "AVerMedia USB MPEG-2 Encoder", "Hava Video Encoder" };//"{6b2088b7-1e3b-4d3d-905f-2ff22040d5b2}" };// };
#define NUM_SPECIAL_PCM_OUTPUT_PIN_NAME_CHECKS 4
char* SPECIAL_PCM_OUTPUT_PIN_NAME_CHECKS[NUM_SPECIAL_PCM_OUTPUT_PIN_NAME_CHECKS] = { "ATI Audio Decoder PCM Output", "I2S Audio", "Virtual Audio Out",  "Virtual Video Out" };
#define NUM_SPECIAL_YUV_INPUT_PIN_NAME_CHECKS 4
char* SPECIAL_YUV_INPUT_PIN_NAME_CHECKS[NUM_SPECIAL_YUV_INPUT_PIN_NAME_CHECKS] = { "ATI MUX Video YUV Input", "Analog ITU Video", "Virtual Video In", "Virtual Video In" };
#define NUM_SPECIAL_PCM_INPUT_PIN_NAME_CHECKS 4
char* SPECIAL_PCM_INPUT_PIN_NAME_CHECKS[NUM_SPECIAL_PCM_INPUT_PIN_NAME_CHECKS] = { "ATI MUX Audio PCM Input", "I2S Audio", "Virtual Audio In", "Virtual Audio In" };
#define NUM_SPECIAL_CAPTURE_FILTER_NAME_CHECKS 11
char* SPECIAL_CAPTURE_TO_ENCODER_FILTER_NAME_CHECKS[NUM_SPECIAL_CAPTURE_FILTER_NAME_CHECKS] = { "7162 BDA Analog Capture0", "7162 BDA Analog Capture1", "Hauppauge WinTV-7164 Analog Capture", "Hauppauge Colossus Capture 0", 
	"Hauppauge Colossus Capture 1", "Hauppauge Colossus Capture 2", "Hauppauge Colossus Capture 3", "Hauppauge Colossus Capture 4", "Hauppauge Colossus Capture 5", "Hauppauge Colossus Capture 6", "Hauppauge Colossus Capture 7" };
char* SPECIAL_ENCODER_FROM_CAPTURE_FILTER_NAME_CHECKS[NUM_SPECIAL_CAPTURE_FILTER_NAME_CHECKS] = { "7162 BDA MPEG PS Encoder0", "7162 BDA MPEG PS Encoder1", "Hauppauge WinTV-7164 MPEG2 PS Encoder/Mux", "Hauppauge Colossus TS Encoder 0",
	"Hauppauge Colossus TS Encoder 1", "Hauppauge Colossus TS Encoder 2", "Hauppauge Colossus TS Encoder 3", "Hauppauge Colossus TS Encoder 4", "Hauppauge Colossus TS Encoder 5", "Hauppauge Colossus TS Encoder 6", "Hauppauge Colossus TS Encoder 7" };

typedef struct
{
	unsigned long audioSampleFrequency;
} KSPROPERTY_CAPTURE_AUDIO_CONTROL_S, *PKSPROPERTY_CAPTURE_AUDIO_CONTROL_S;
#define STATIC_PROPSETID_CAPTURE_PERIPHERAL\
	0xc22468a0L, 0x2ee9, 0x11d3, 0x91, 0xb2, 0x00, 0xc0, 0x4f, 0x81, 0xb5, 0x6b
DEFINE_GUIDSTRUCT("c22468a0-2ee9-11d3-91b2-00c04f81b56b", PROPSETID_CAPTURE_PERIPHERAL);
#define PROPSETID_CAPTURE_PERIPHERAL DEFINE_GUIDNAMED(PROPSETID_CAPTURE_PERIPHERAL)

TV_TYPE GetTVType( DShowCaptureInfo *pCapInfo );
extern FilterGraphTools graphTools;
HRESULT SetupBDATuningSpace( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void ReleaseBDATuningSpace( JNIEnv* env, DShowCaptureInfo *pCapInfo );
void BDAGraphConnectFilter( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void ClearUpDebugFileSource( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void AddBDAVideoCaptureFilters(JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, int devCaps );
void BDAGraphConnectDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void BDAGraphDisconnectDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
void BDAGraphConnectDebugRawDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph  );
//void BDAGraphDisconnectDebugRawDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph );
//void BDAGraphSetDebugRawDumpFileName( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, char* Channel  );
void BDAGraphSetDebugFileSource( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph  );
long EPG_Dumper( void* context,  void* mesg, int bytes );
long AVInf_Dumper( void* context,  void* mesg, int  bytes );
void SetTVType( DShowCaptureInfo *pCapInfo, TV_TYPE BDATVType, BOOL bNetworkProvider );
void SetupCAM( JNIEnv* env, DShowCaptureInfo *pCapInfo );
int SetupSatelliteLNB( DShowCaptureInfo* pCapInfo, int bReload  );
void TearDownCAM( JNIEnv* env, DShowCaptureInfo *pCapInfo );
long OnCamPMT( void* context, short bytes, void* mesg );
void ClearPIDMap(  JNIEnv* env, DShowCaptureInfo *pCapInfo );
long Program_Dumper( void* context, void* message , int bytes );

HRESULT ConfigureVideoCapturePin(DShowCaptureInfo* pCapInfo, SageTVMPEG2EncodingParameters* pythonParams)
{
	CComPtr<IPin> vCapPin = NULL;
	vCapPin.p = FindPinByCategoryAndType(pCapInfo->pVideoCaptureFilter,
		&PIN_CATEGORY_CAPTURE, &MEDIATYPE_Video);

	if (!vCapPin) return E_FAIL;

	IAMStreamConfig* stream_config = NULL;
	HRESULT hr = vCapPin->QueryInterface(IID_IAMStreamConfig, (void**)&stream_config);
	if (!SUCCEEDED(hr)) return hr;
	AM_MEDIA_TYPE* pmt = NULL;

    int caps_count = 0, caps_size = 0;
    VIDEO_STREAM_CONFIG_CAPS caps;
	VIDEOINFOHEADER* format;
    stream_config->GetNumberOfCapabilities(&caps_count, &caps_size);
	int closestMatch = -1;
    for ( int i = 0 ; i < caps_count ; i ++ )
    {
        hr = stream_config->GetStreamCaps(i, &pmt, (BYTE*)&caps);
        if (FAILED(hr))
		{ 
			slog(("GetStreamCaps Failed!\r\n"));
			continue; 
		}

        if (pmt->formattype != FORMAT_VideoInfo) goto next_stream_caps;
        format = (VIDEOINFOHEADER*)pmt->pbFormat;
        if (pmt->subtype != MEDIASUBTYPE_YUY2) goto next_stream_caps;
		closestMatch = i;
        if (format->bmiHeader.biWidth != (pythonParams ? pythonParams->width : 720)) 
            goto next_stream_caps;
		closestMatch = i;
        if (pythonParams && format->bmiHeader.biHeight != (pythonParams ? pythonParams->height : 
						(!IsNTSCVideoCode(pCapInfo->videoFormatCode) ? 576 : 480)))
            goto next_stream_caps;
		closestMatch = i;

		if (pythonParams && pythonParams->fps == 25)
			format->AvgTimePerFrame = 400000;
		else if (pythonParams && pythonParams->fps == 15)
			format->AvgTimePerFrame = 667333;
		else if (pythonParams && pythonParams->inversetelecine)
			format->AvgTimePerFrame = 416667;
		else //if (pythonParams->fps == 30)
			format->AvgTimePerFrame = IsNTSCVideoCode(pCapInfo->videoFormatCode) ? 333667 : 200000;

	    slog(("Video width: %d height: %d fps: %d bps: %d\r\n",
                   format->bmiHeader.biWidth,
                   format->bmiHeader.biHeight,
                   long(format->AvgTimePerFrame),
                   format->dwBitRate));

        hr = stream_config->SetFormat(pmt);
        if ( FAILED(hr) )
		{ 
			slog(("set pin format failed\r\n"));
			goto next_stream_caps;
		}
        DeleteMediaType(pmt);
		SAFE_RELEASE(stream_config);
        return S_OK;

next_stream_caps:
        DeleteMediaType(pmt);
    }

	if (closestMatch >= 0 && pythonParams)
	{
        hr = stream_config->GetStreamCaps(closestMatch, &pmt, (BYTE*)&caps);
		if (SUCCEEDED(hr))
		{
			format = (VIDEOINFOHEADER*)pmt->pbFormat;
			format->bmiHeader.biHeight = pythonParams->height;
			format->bmiHeader.biWidth = pythonParams->width;
			format->bmiHeader.biSizeImage = pythonParams->width * pythonParams->height * 2; // 16 bpp
			if (pythonParams && pythonParams->fps == 25)
				format->AvgTimePerFrame = 400000;
			else if (pythonParams && pythonParams->fps == 15)
				format->AvgTimePerFrame = 667333;
			else if (pythonParams && pythonParams->inversetelecine)
				format->AvgTimePerFrame = 416667;
			else //if (pythonParams->fps == 30)
				format->AvgTimePerFrame = IsNTSCVideoCode(pCapInfo->videoFormatCode) ? 333667 : 200000;

			slog(("Video2 width: %d height: %d fps: %d bps: %d\r\n",
					   format->bmiHeader.biWidth,
					   format->bmiHeader.biHeight,
					   long(format->AvgTimePerFrame),
					   format->dwBitRate));

			hr = stream_config->SetFormat(pmt);
			DeleteMediaType(pmt);
			if ( FAILED(hr) )
			{ 
				slog(("set pin format failed\r\n"));
			}
			else
			{
				SAFE_RELEASE(stream_config);
				return S_OK;
			}
		}
	}

	SAFE_RELEASE(stream_config);
	return E_FAIL;
}

HRESULT ConfigureAudioCapturePin(DShowCaptureInfo* pCapInfo, SageTVMPEG2EncodingParameters* pythonParams)
{
	IPin *pPin = NULL;
	HRESULT hr = S_OK;
	if (pCapInfo->pAudioCaptureFilter)
		hr = GetPin(pCapInfo->pAudioCaptureFilter, PINDIR_OUTPUT, 0, &pPin);
	else
		pPin = FindPin(pCapInfo->pVideoCaptureFilter, PINDIR_OUTPUT, &MEDIATYPE_Audio, NULL);
	if (SUCCEEDED(hr) && pPin)
	{
		int nChannels = 2;
		int nBytesPerSample = 2;
		int nFrequency = (pythonParams ? pythonParams->audiosampling : 48000);
		long lBytesPerSecond = (long) (nChannels * nFrequency * nBytesPerSample);
		IAMBufferNegotiation *pNeg = NULL;
		// Get buffer negotiation interface
		hr = pPin->QueryInterface(IID_IAMBufferNegotiation, (void **)&pNeg);
		if (SUCCEEDED(hr))
		{
			// Set to 50ms worth of data
			long lBufferSize = (long) ((float) lBytesPerSecond * 0.05f);

			// Set the buffer size based on selected settings
			ALLOCATOR_PROPERTIES prop={0};
			prop.cbBuffer = lBufferSize;
			prop.cBuffers = 6;
			prop.cbAlign = nBytesPerSample * nChannels;
			hr = pNeg->SuggestAllocatorProperties(&prop);
			SAFE_RELEASE(pNeg);
		}
	
		/*IAMStreamConfig *pCfg = NULL;
		// Now set the actual format of the audio data
		hr = pPin->QueryInterface(IID_IAMStreamConfig, (void **)&pCfg);
		if (SUCCEEDED(hr))
		{
			// Read current media type/format
			AM_MEDIA_TYPE *pmt={0};
			hr = pCfg->GetFormat(&pmt);
			if (SUCCEEDED(hr))
			{
				// Fill in values for the new format
				WAVEFORMATEX *pWF = (WAVEFORMATEX *) pmt->pbFormat;
				pWF->nChannels = (WORD) nChannels;
				pWF->nSamplesPerSec = nFrequency;
				pWF->nAvgBytesPerSec = lBytesPerSecond;
				pWF->wBitsPerSample = (WORD) (nBytesPerSample * 8);
				pWF->nBlockAlign = (WORD) (nBytesPerSample * nChannels);
				// Set the new formattype for the output pin
				hr = pCfg->SetFormat(pmt);
				DeleteMediaType(pmt);
			}
			SAFE_RELEASE(pCfg);
		}*/

		IAMStreamConfig* stream_config = NULL;
		AM_MEDIA_TYPE *pmt;
		// Now set the actual format of the audio data
		hr = pPin->QueryInterface(IID_IAMStreamConfig, (void **)&stream_config);
		if (SUCCEEDED(hr))
		{
			AUDIO_STREAM_CONFIG_CAPS caps;
			WAVEFORMATEX* format;

			int caps_count = 0, caps_size = 0;
			stream_config->GetNumberOfCapabilities(&caps_count, &caps_size);

			for ( int i = 0 ; i < caps_count ; i ++ )
			{
	 			hr = stream_config->GetStreamCaps(i, &pmt, (BYTE*)&caps);
				if ( FAILED(hr) ) { slog(("GetSteamCaps Failed!\r\n")); continue; }

				if ( !IsEqualGUID(pmt->formattype, FORMAT_WaveFormatEx) )
				{
					goto next_audio_stream_caps;
				}
				format = (WAVEFORMATEX*)pmt->pbFormat;			 
				
				//ZQ
				unsigned long audiosampling =  (pythonParams ? pythonParams->audiosampling : 48000);
				if ( caps.MinimumSampleFrequency > audiosampling )
					 audiosampling = caps.MinimumSampleFrequency;
				else
				if ( caps.MaximumSampleFrequency < audiosampling )
					 audiosampling = caps.MaximumSampleFrequency;

				unsigned long channel = 2;
				if ( caps.MinimumChannels > channel ) 
					channel = caps.MinimumChannels;
				else
				if ( caps.MaximumChannels < channel ) 
					channel = caps.MaximumChannels;
			 
				unsigned long bits = 16;
				if ( caps.MinimumBitsPerSample > bits )
					bits = caps.MinimumBitsPerSample;
				else 
				if ( caps.MaximumBitsPerSample < bits )
					bits = caps.MaximumBitsPerSample;

				switch ( format->wFormatTag )
				{ // we only want PCM
					case WAVE_FORMAT_PCM:
						format->nChannels =  (WORD)channel;
						format->nSamplesPerSec = audiosampling;
						format->wBitsPerSample = (WORD)bits;
						format->nBlockAlign = 4;
						format->nAvgBytesPerSec = 4*format->nSamplesPerSec;
			 			break;
					default:
						goto next_audio_stream_caps;
				}

				hr = stream_config->SetFormat(pmt);
				if ( FAILED(hr) ) 
				{ 
					slog(("set pin format failed\r\n")); 
					goto next_audio_stream_caps;
				}
				else
				{
					slog(("Set pin format for audio succeeded!\r\n"));
				}
				DeleteMediaType(pmt);

				break;

	next_audio_stream_caps:
				DeleteMediaType(pmt);
			}
			SAFE_RELEASE(stream_config);
		}

		// Release interfaces
		SAFE_RELEASE(pPin);
	}
	return hr;
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    setEncodingProperties0
 * Signature: (JLjava/lang/String;Ljava/util/Map;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_setEncodingProperties0
  (JNIEnv *env, jobject jo, jlong capInfo, jstring jname, jobject encodePropsMap)
{
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if ( capInfo == NULL )
	{
		slog((env, "Set encoding property failed capInfo is null \r\n"));
		return ;
	}
	const char* tempStr = env->GetStringUTFChars(jname, NULL);
	strncpy(pCapInfo->encodeQuality, tempStr, sizeof(pCapInfo->encodeQuality)-1 );
	env->ReleaseStringUTFChars(jname, tempStr);
	slog((env, "setEncodingProperties0 %d %s\r\n", (int)capInfo, pCapInfo->encodeQuality));

	if (encodePropsMap)
	{
		SageTVMPEG2EncodingParameters* encodeParams = new SageTVMPEG2EncodingParameters;
		pCapInfo->encodeParams = encodeParams;
		ZeroMemory(encodeParams, sizeof(SageTVMPEG2EncodingParameters));

		// Setup the non-zero defaults in the encoder properties
		encodeParams->gopsize = 15;
		encodeParams->audiosampling = 48000;
		encodeParams->audiobitrate = 384;
		encodeParams->videobitrate = 4000000;
		encodeParams->peakvideobitrate = 5000000;
		encodeParams->width = 720;
		encodeParams->height = IsNTSCVideoCode(pCapInfo->videoFormatCode) ? 480 : 576;
		encodeParams->disablefilter = 1;
		encodeParams->fps = IsNTSCVideoCode(pCapInfo->videoFormatCode) ? 30 : 25;
		encodeParams->aspectratio = 1; // 4:3

		static jclass iteratorClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/Iterator"));
		static jclass mapClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/Map"));
		static jclass setClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/Set"));
		static jclass mapEntryClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/Map$Entry"));
		static jmethodID mapEntrySetMeth = env->GetMethodID(mapClass, "entrySet", "()Ljava/util/Set;");
		static jmethodID iteratorMeth = env->GetMethodID(setClass, "iterator", "()Ljava/util/Iterator;");
		static jmethodID hasNextMeth = env->GetMethodID(iteratorClass, "hasNext", "()Z");
		static jmethodID nextMeth = env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");
		static jmethodID getKeyMeth = env->GetMethodID(mapEntryClass, "getKey", "()Ljava/lang/Object;");
		static jmethodID getValueMeth = env->GetMethodID(mapEntryClass, "getValue", "()Ljava/lang/Object;");

		// Iterate over the name/value pairs in the map
		jobject walker = env->CallObjectMethod(env->CallObjectMethod(encodePropsMap, mapEntrySetMeth),
			iteratorMeth);
		char cPropName[512];
		char cPropValue[512];
		while (env->CallBooleanMethod(walker, hasNextMeth))
		{
			jobject currEntry = env->CallObjectMethod(walker, nextMeth);
			jstring propName = (jstring) env->CallObjectMethod(currEntry, getKeyMeth);
			if (propName)
			{
				const char* tempName = env->GetStringUTFChars(propName, NULL);
				strncpy(cPropName, tempName, sizeof(cPropName)-1);
				env->ReleaseStringUTFChars(propName, tempName);
				jstring propValue = (jstring) env->CallObjectMethod(currEntry, getValueMeth);
				if (propValue)
				{
					const char* tempValue = env->GetStringUTFChars(propValue, NULL);
					strncpy( cPropValue, tempValue, sizeof(cPropValue) );
					env->ReleaseStringUTFChars(propValue, tempValue);
				
					int currValue = atoi(cPropValue);

					// Now that we have the char[] name and the value we can
					// set the appropriate values in the parameter object
					if (!strcmp(cPropName, "audiobitrate"))
						encodeParams->audiobitrate = currValue;
					else if (!strcmp(cPropName, "audiocrc"))
						encodeParams->audiocrc = currValue;
					else if (!strcmp(cPropName, "audiooutputmode"))
						encodeParams->audiooutputmode = currValue;
					else if (!strcmp(cPropName, "audiosampling"))
						encodeParams->audiosampling = currValue;
					else if (!strcmp(cPropName, "closedgop"))
						encodeParams->closedgop = currValue;
					else if (!strcmp(cPropName, "disablefilter"))
						encodeParams->disablefilter = currValue;
					else if (!strcmp(cPropName, "gopsize"))
						encodeParams->gopsize = currValue;
					else if (!strcmp(cPropName, "height"))
						encodeParams->height = currValue;
					else if (!strcmp(cPropName, "inversetelecine"))
						encodeParams->inversetelecine = currValue;
					else if (!strcmp(cPropName, "medianfilter"))
						encodeParams->medianfilter = currValue;
					else if (!strcmp(cPropName, "outputstreamtype"))
						encodeParams->outputstreamtype = currValue;
					else if (!strcmp(cPropName, "peakvideobitrate"))
						encodeParams->peakvideobitrate = currValue;
					else if (!strcmp(cPropName, "vbr"))
						encodeParams->vbr = currValue;
					else if (!strcmp(cPropName, "videobitrate"))
						encodeParams->videobitrate = currValue;
					else if (!strcmp(cPropName, "width"))
						encodeParams->width = currValue;
					else if (!strcmp(cPropName, "fps"))
						encodeParams->fps = currValue;
					else if (!strcmp(cPropName, "ipb"))
						encodeParams->ipb = currValue;
					else if (!strcmp(cPropName, "deinterlace"))
						encodeParams->deinterlace = currValue;
					else if (!strcmp(cPropName, "aspectratio"))
						encodeParams->aspectratio = currValue;
					else
						continue;

					slog((env, "Set encoding property %s to %d\r\n", cPropName, currValue));
				}
			}
		}
	}
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    setCircularFileSize0
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_setCircularFileSize0
  (JNIEnv *env, jobject jo, jlong capInfo, jlong buffSize)
{
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if (pCapInfo)
		pCapInfo->recordBufferSize = buffSize;
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    setupEncodingGraph0
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_setupEncodingGraph0
  (JNIEnv *env, jobject jo, jlong capInfo, jstring jfilename)
{
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if ( pCapInfo == NULL )
	{
		slog((env, "setupEncoding0 called failed (CapInfo is NULL)\r\n" ));
		return;
	}

	slog((env, "setupEncoding0 called for %d '%s-%d' (ver 1.0)\r\n", 
		(int)capInfo, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum));
	HRESULT hr;

	if (pCapInfo->recordBufferSize && jfilename)
	{
		// Allocate space for the circular capture file
		jchar* wsz = (jchar*) env->GetStringChars(jfilename, NULL);
		pCapInfo->pBuilder->AllocCapFile((LPCOLESTR)wsz, pCapInfo->recordBufferSize);
		env->ReleaseStringChars(jfilename, wsz);
	}

	char currFileExt[64];
	if (jfilename)
	{
		const char* cFilename = env->GetStringUTFChars(jfilename, NULL);
		const char* lastDot = strrchr(cFilename, '.');
		if (lastDot)
		{
			strncpy(currFileExt, lastDot + 1, sizeof(currFileExt) );
		}
		else
			currFileExt[0] = '\0';
		env->ReleaseStringUTFChars(jfilename, cFilename);
	}
	else
	{
		strcpy(currFileExt, "mpg");
	}
	
	//ZQ setup BDA TV
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ))
	{
		if ( pCapInfo->pSink == NULL )
		{
			hr = CoCreateInstance(CLSID_MPEG2Dump, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&(pCapInfo->pSink));
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
			hr = pCapInfo->pGraph->AddFilter(pCapInfo->pSink, L"MPEG Dump");
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		}

        // slog((env, "_setupEncodingGraph0() Entry: captureConfig= 0x%x \r\n", pCapInfo->captureConfig));

		AddBDAVideoCaptureFilters( env, pCapInfo, pCapInfo->pGraph, 0 );             
		BDAGraphSetDebugFileSource( env, pCapInfo, pCapInfo->pGraph  );
	    BDAGraphConnectFilter( env, pCapInfo,  pCapInfo->pGraph );
		SetupQAM( env, pCapInfo );
		SetupCAM( env, pCapInfo );
		SetupTunerPlugin( env, pCapInfo, GetTVType( pCapInfo ) );
		BDAGraphConnectDebugRawDumpSink( env, pCapInfo, pCapInfo->pGraph  );
	    BDAGraphConnectDumpSink( env, pCapInfo,  pCapInfo->pGraph );
		ClearUpDebugFileSource( env, pCapInfo, pCapInfo->pGraph );
		if ( GetTVType( pCapInfo ) == DVBS ) SetupSatelliteLNB( pCapInfo, 0 );
		if ( pCapInfo->dwTuneState == 0 && pCapInfo->channel != NULL )
			tuneChannel( (CHANNEL_DATA*)pCapInfo->channel, "0" );


	} else

	// This handles setting up the graph for hardware MPEG2 multiplexed encoding
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_MPEG_AV_CAPTURE_MASK))
	{
		if ( pCapInfo->pSink == NULL )
		{
slog((env, "setupEncoding0 Trace1 \r\n" ));
			hr = CoCreateInstance(CLSID_MPEG2Dump, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&(pCapInfo->pSink));
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
			hr = pCapInfo->pGraph->AddFilter(pCapInfo->pSink, L"MPEG Dump");
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
slog((env, "setupEncoding0 Trace2 \r\n" ));
		}
		SageTVMPEG2EncodingParameters *pythonParams = (SageTVMPEG2EncodingParameters*) pCapInfo->encodeParams;
		BOOL set656Res = FALSE;
		BOOL setVideoFormatOn656 = TRUE;
		BOOL configuredEncoder = FALSE;

		IBaseFilter* mpeg2OutputFilter;
		// Try to find an encoding filter by pin medium type
		CComPtr<IPin> pin656Out = NULL;

		char* forcedEncoderName = NULL;
		char* forcedEncoderNamePrimary = NULL;
		for (int i = 0; !forcedEncoderNamePrimary && i < NUM_SPECIAL_CAPTURE_FILTER_NAME_CHECKS; i++)
		{
			if (!strcmp(SPECIAL_CAPTURE_TO_ENCODER_FILTER_NAME_CHECKS[i], pCapInfo->videoCaptureFilterName))
			{
				forcedEncoderNamePrimary = SPECIAL_ENCODER_FROM_CAPTURE_FILTER_NAME_CHECKS[i];
				slog((env, "setupEncoding0 forced Encoder %s \r\n", forcedEncoderNamePrimary ));
			}
		}
		for (int i = 0; !(pin656Out.p) && i < NUM_SPECIAL_YUV_OUTPUT_PIN_NAME_CHECKS; i++)
		{
			pin656Out.p = FindPinByName(pCapInfo->pVideoCaptureFilter, SPECIAL_YUV_OUTPUT_PIN_NAME_CHECKS[i], PINDIR_OUTPUT);
			if (pin656Out.p && !forcedEncoderNamePrimary) forcedEncoderName = SPECIAL_ENCODER_FILTER_NAME_CHECKS[i];
		}
		if (!(pin656Out.p))
		{
			pin656Out.p = FindPin(pCapInfo->pVideoCaptureFilter, PINDIR_OUTPUT, &MEDIATYPE_Video, &MEDIASUBTYPE_IYUV);
			if (!(pin656Out.p))
			{
				pin656Out.p = FindPinByName(pCapInfo->pVideoCaptureFilter, "656", PINDIR_OUTPUT);
				if (!(pin656Out.p))
				{
					pin656Out.p = FindPin(pCapInfo->pVideoCaptureFilter, PINDIR_OUTPUT, &MEDIATYPE_Video, NULL);
				}
			}
		}
slog((env, "setupEncoding0 Trace3 \r\n" ));
		BOOL connectEncoder = FALSE;
		if (pin656Out.p && ((forcedEncoderNamePrimary && 
			(SUCCEEDED(FindFilterByName(&(pCapInfo->pEncoder), AM_KSCATEGORY_MULTIPLEXER, forcedEncoderNamePrimary, pCapInfo->videoCaptureFilterNum, NULL, 0 )))) ||
			SUCCEEDED(FindDownstreamFilter(&(pCapInfo->pEncoder), AM_KSCATEGORY_ENCODER, pin656Out,
			pCapInfo->deviceName)) ||
			SUCCEEDED(FindDownstreamFilter(&(pCapInfo->pEncoder), AM_KSCATEGORY_MULTIPLEXER, pin656Out,
			pCapInfo->deviceName)) ||
			(forcedEncoderName && (SUCCEEDED(FindFilterByName(&(pCapInfo->pEncoder), AM_KSCATEGORY_MULTIPLEXER, forcedEncoderName, pCapInfo->videoCaptureFilterNum, NULL, 0 ))))))
		{
			hr = pCapInfo->pGraph->AddFilter(pCapInfo->pEncoder, NULL);
			ENCEXCEPT_RET(sage_EncodingException_HW_VIDEO_COMPRESSION);
			connectEncoder = TRUE;
			slog((env, "Using encoder filter (%d)\r\n", pCapInfo->videoCaptureFilterNum));
		}
slog((env, "setupEncoding0 Trace4 \r\n" ));
		// HCW uses some proprietary media type so this will fail if we try it. And we don't need
		// to do it for the 150/500.  However, I'm concerned that it'll break support for older cards
		// if I remove it entirely. I did have to move the HCW configuration block to be after we connect
		// up the 656 pins or it wasn't configuring the video size correctly.
		if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_HCW_CAPTURE_MASK))
		{
			if (pythonParams)
			{
//				setVideoFormatOn656 = FALSE;
			}
		}

		if (connectEncoder)
		{
			// Get the configured width & height so we can set it on the 656 pins
			CComPtr<IPin> pin656In = NULL;
			for (int i = 0; !(pin656In.p) && i < NUM_SPECIAL_YUV_INPUT_PIN_NAME_CHECKS; i++)
			    pin656In.p = FindPinByName(pCapInfo->pEncoder, SPECIAL_YUV_INPUT_PIN_NAME_CHECKS[i], PINDIR_INPUT);
			if (!pin656In.p)
			{
				pin656In.p = FindPin(pCapInfo->pEncoder, PINDIR_INPUT, &MEDIATYPE_Video,
					&MEDIASUBTYPE_IYUV);
				if (!pin656In.p)
				{
					pin656In.p = FindPin(pCapInfo->pEncoder, PINDIR_INPUT, &MEDIATYPE_Video, NULL);
					if (!pin656In.p)
					{
						pin656In.p = FindPin(pCapInfo->pEncoder, PINDIR_INPUT, NULL, NULL);
					}
				}
			}
			if (pin656In != NULL)
			{
				if (setVideoFormatOn656 && pythonParams)
				{
					int width656 = pythonParams->width;
					int height656 = pythonParams->height;
					int real_height = height656;
					if(height656 == 240)
						real_height = 480;
					if(height656 == 288)
						real_height = 576;

					VIDEOINFOHEADER video_info;
					memset(&video_info, 0, sizeof(video_info));
   					video_info.bmiHeader.biHeight = real_height;
   					video_info.bmiHeader.biWidth = width656;


					AM_MEDIA_TYPE media_type = 
					{
						STATIC_KSDATAFORMAT_TYPE_VIDEO,                     //Major Type
						FOURCC_YUV420, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71,            //SubType
						TRUE,                                               //Fixed sample size
						FALSE,                                              //Temporal Compression
						0,                                                  //Sample Size
						STATIC_KSDATAFORMAT_SPECIFIER_VIDEOINFO,          //Format Type
						NULL,                                               //pUnknown (Unused)
						sizeof(video_info),                          //Format Size
						(PUCHAR)&video_info                          //pFormat
					};

					hr = pCapInfo->pGraph->ConnectDirect(pin656Out, pin656In, &media_type);
					set656Res = SUCCEEDED(hr);
				}
				if (!set656Res)
				{
					hr = pCapInfo->pGraph->ConnectDirect(pin656Out, pin656In, NULL);
					HTESTPRINT(hr);
				}
			}
slog((env, "setupEncoding0 Trace5 \r\n" ));
			CComPtr<IPin> pinI2SOut = NULL;
			for (int i = 0; !(pinI2SOut.p) && i < NUM_SPECIAL_PCM_OUTPUT_PIN_NAME_CHECKS; i++)
				pinI2SOut.p = FindPinByName(pCapInfo->pVideoCaptureFilter, SPECIAL_PCM_OUTPUT_PIN_NAME_CHECKS[i], PINDIR_OUTPUT);
			if (!pinI2SOut.p)
				pinI2SOut.p = FindPin(pCapInfo->pVideoCaptureFilter, PINDIR_OUTPUT, &MEDIATYPE_Audio,
					&MEDIASUBTYPE_PCM);
			CComPtr<IPin> pinI2SIn  = NULL;
			for (int i = 0; !(pinI2SIn.p) && i < NUM_SPECIAL_PCM_INPUT_PIN_NAME_CHECKS; i++)
				pinI2SIn.p = FindPinByName(pCapInfo->pEncoder, SPECIAL_PCM_INPUT_PIN_NAME_CHECKS[i], PINDIR_INPUT);
			if (!pinI2SIn.p)
				pinI2SIn.p = FindPin(pCapInfo->pEncoder, PINDIR_INPUT, &MEDIATYPE_Audio,
					&MEDIASUBTYPE_PCM);
			if (pinI2SOut != NULL && pinI2SIn != NULL)
			{
				hr = pCapInfo->pGraph->ConnectDirect(pinI2SOut, pinI2SIn, NULL);
				HTESTPRINT(hr);
			}

			mpeg2OutputFilter = pCapInfo->pEncoder;
		}
		else
		{
			mpeg2OutputFilter = pCapInfo->pVideoCaptureFilter;
		}
slog((env, "setupEncoding0 Trace6 \r\n" ));
//ZQZQZQZQ
		//try ATI encoder settings, if we can get its interface
		configureATIEncoder(pCapInfo, pythonParams, env);
//ZQZQZQZQ
		if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_PYTHON2_ENCODER_MASK))
		{
			if (pythonParams)
			{
				configureConexant2341xEncoder(pCapInfo, pythonParams, set656Res, env);
				configuredEncoder = TRUE;
			}
		}

slog((env, "setupEncoding0 Trace6-1 \r\n" ));
		if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_HCW_CAPTURE_MASK))
		{
			if (pythonParams)
			{
				configureHCWEncoder(pCapInfo, pythonParams, set656Res, env);
				configuredEncoder = TRUE;
//				setVideoFormatOn656 = FALSE;
			}
		}
slog((env, "setupEncoding0 Trace7 \r\n" ));
		// Check for the standard MS encoder configuration interfaces
		if (!configuredEncoder && pythonParams)
		{
			ICodecAPI* pCodec = NULL;
			hr = mpeg2OutputFilter->QueryInterface(IID_ICodecAPI, (void**)&pCodec);
			if (SUCCEEDED(hr))
			{
				slog((env, "Got codec interface\r\n"));
				VARIANT theValue;
				theValue.vt = VT_UI4;
				theValue.ulVal = pythonParams->videobitrate;
				hr = pCodec->SetValue(&ENCAPIPARAM_BITRATE, &theValue);
				HTESTPRINT(hr);
				theValue.vt = VT_I4;
				theValue.lVal = pythonParams->vbr ? VariableBitRateAverage : ConstantBitRate;
				hr = pCodec->SetValue(&ENCAPIPARAM_BITRATE_MODE, &theValue);
				HTESTPRINT(hr);
				if (pythonParams->vbr)
				{
					theValue.vt = VT_UI4;
					theValue.ulVal = pythonParams->peakvideobitrate;
					hr = pCodec->SetValue(&ENCAPIPARAM_PEAK_BITRATE, &theValue);
					HTESTPRINT(hr);
				}
slog((env, "setupEncoding0 Trace7-1 \r\n" ));
			} 
			else
			{
				IEncoderAPI* pEncApi = NULL;
				hr = mpeg2OutputFilter->QueryInterface(IID_IEncoderAPI, (void**)&pEncApi);
				if (SUCCEEDED(hr))
				{
					slog((env, "Got encoder codec interface-NOT IMPLEMENTED\r\n"));
				}
				else
				{
					IVideoEncoder* pVCodec = NULL;
					hr = mpeg2OutputFilter->QueryInterface(IID_IVideoEncoder, (void**)&pVCodec);
					if (SUCCEEDED(hr))
					{
						slog((env, "Got VideoEncoder interface\r\n"));
						if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_HDPVR_ENCODER_MASK))
						{
							// Scale the bitrate down by 2.5 (based on reg setting) for SD captures
							DWORD sdScaleFactor = GetRegistryDword(HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\DirectShow",
								"SDCapRateDiv10", 25);
							// Check what resolution the capture is running at
							IAMAnalogVideoDecoder* pAMVidDec = NULL;
							hr = pCapInfo->pVideoCaptureFilter->QueryInterface(IID_IAMAnalogVideoDecoder, (void**)&pAMVidDec);
							HTESTPRINT(hr);
							if (SUCCEEDED(hr))
							{
								long numLines = 0;
								hr = pAMVidDec->get_HorizontalLocked(&numLines);
								slog((env, "Signal detected!\r\n"));
								if (numLines)
								{
									numLines = 0;
									hr = pAMVidDec->get_NumberOfLines(&numLines);
									HTESTPRINT(hr);
									if (SUCCEEDED(hr))
									{
										slog((env, "Detected capture input resolution lines=%d\r\n", numLines));
										if (numLines < 700)
										{
											pythonParams->videobitrate = pythonParams->videobitrate * 10 / sdScaleFactor;
											pythonParams->peakvideobitrate = pythonParams->peakvideobitrate * 10 / sdScaleFactor;
											slog((env, "Applying rate adjustment of %f\r\n", sdScaleFactor/((float)10)));
										}
									}
								}
								SAFE_RELEASE(pAMVidDec);
							}
						}
						VARIANT theValue;
						theValue.vt = VT_UI4;
						theValue.ulVal = pythonParams->videobitrate;
						hr = pVCodec->SetValue(&ENCAPIPARAM_BITRATE, &theValue);
						HTESTPRINT(hr);
						theValue.vt = VT_I4;
						theValue.lVal = pythonParams->vbr ? VariableBitRateAverage : ConstantBitRate;
						hr = pVCodec->SetValue(&ENCAPIPARAM_BITRATE_MODE, &theValue);
						HTESTPRINT(hr);
						if (pythonParams->vbr)
						{
							theValue.vt = VT_UI4;
							theValue.ulVal = pythonParams->peakvideobitrate;
							hr = pVCodec->SetValue(&ENCAPIPARAM_PEAK_BITRATE, &theValue);
							HTESTPRINT(hr);
						}
						slog((env, "setupEncoding0 Trace7-2\r\n" ));
					}
				}
				SAFE_RELEASE(pEncApi);
slog((env, "setupEncoding0 Trace7-3\r\n" ));
			}
			SAFE_RELEASE(pCodec);
		}
slog((env, "setupEncoding0 Trace8 \r\n" ));
		// Connect the videocapture to the dumper
		// Find the MPEG2 capture pin
		IPin* pMpeg2CapPin = FindPin(mpeg2OutputFilter, PINDIR_OUTPUT,
			&MEDIATYPE_Stream, &MEDIASUBTYPE_MPEG2_PROGRAM);
		if (!pMpeg2CapPin)
		{
			pMpeg2CapPin = FindPin(mpeg2OutputFilter, PINDIR_OUTPUT,
				&MEDIATYPE_Video, &MEDIASUBTYPE_MPEG2_VIDEO);
			if (!pMpeg2CapPin)
			{
				pMpeg2CapPin = FindPin(mpeg2OutputFilter, PINDIR_OUTPUT,
					&MEDIATYPE_Stream, &MEDIASUBTYPE_MPEG2_TRANSPORT);
				if (!pMpeg2CapPin)
				{
					pMpeg2CapPin = FindPin(mpeg2OutputFilter, PINDIR_OUTPUT,
						&MEDIATYPE_Stream, &MEDIASUBTYPE_MPEG1System);
					if (!pMpeg2CapPin)
					{
						hr = E_FAIL;
						ENCEXCEPT_RET(sage_EncodingException_HW_VIDEO_COMPRESSION);
					}
				}
			}
		}
slog((env, "setupEncoding0 Trace9 \r\n" ));
		IPin* dumpInPin = FindPinByName(pCapInfo->pSink, NULL, PINDIR_INPUT);
		hr = pCapInfo->pGraph->ConnectDirect(pMpeg2CapPin, dumpInPin, NULL);
		SAFE_RELEASE(pMpeg2CapPin);
		SAFE_RELEASE(dumpInPin);
		HTESTPRINT(hr);
		ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
slog((env, "setupEncoding0 Trace10 \r\n" ));
	}
	else // This sets up the graph for software multiplexing
	{

		AM_MEDIA_TYPE* prefVideoMT = NULL;
		SageTVMPEG2EncodingParameters *pythonParams = (SageTVMPEG2EncodingParameters*) pCapInfo->encodeParams;
		if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_VBDVCR_ENCODER_MASK) && pythonParams)
		{
			configureVBDVCREncoder(pythonParams);
		}

slog((env, "setupEncoding0 Trace11 \r\n" ));
		BOOL useMyMpegMux = TRUE;
		BOOL muxingEncoder = FALSE;
		BOOL writeToAvi = FALSE;
		BOOL useDivxFW = 0;//GetRegistryDword(HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\DirectShow", "UseDivXAVIMux", 1);
		if (pythonParams && !stricmp(currFileExt, "avi"))
		{
			// AVI mux instead
			useMyMpegMux = FALSE;
			writeToAvi = TRUE;
		}

		// Check for a default software encoding configuration
		BOOL useDefaultSWEncoder = FALSE;
		if ((!strlen(pCapInfo->videoProcessorName) || !strlen(pCapInfo->audioProcessorName)) &&
			(capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_VIDEO_CAPTURE_MASK) ||
			capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_AV_CAPTURE_MASK)))
		{
			useDefaultSWEncoder = TRUE;
			muxingEncoder = FALSE; // the new MC mux doesn't do encoding
			if (!writeToAvi && pythonParams && pythonParams->outputstreamtype == 101)
			{
				// SW DivX encoding
				useMyMpegMux = TRUE;
				muxingEncoder = FALSE;
			}
			else if (writeToAvi && pythonParams && pythonParams->outputstreamtype == 101)
			{
				// SW DivX encoding to AVI
				muxingEncoder = FALSE;
			}
			else
				useMyMpegMux = FALSE;
		}

		// Check for a multiplexing encoder
		if (!useDefaultSWEncoder && strlen(pCapInfo->videoProcessorName) && strlen(pCapInfo->audioProcessorName) &&
			!strcmp(pCapInfo->videoProcessorName, pCapInfo->audioProcessorName))
		{
			muxingEncoder = TRUE;
			useMyMpegMux = FALSE;
		}

		// We have to render the preview before we add our Mux or it might get sucked into the preview rendering
		if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_VIDEO_CAPTURE_MASK) ||
			capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_AV_CAPTURE_MASK))
		{
			// Setup the format for video capture
			hr = ConfigureVideoCapturePin(pCapInfo, pythonParams);
			IAMStreamConfig* pVidConfig = NULL;
			hr = pCapInfo->pBuilder->FindInterface(NULL, NULL, pCapInfo->pVideoCaptureFilter,
				IID_IAMStreamConfig, (void**)&pVidConfig);
			ENCEXCEPT_RET(sage_EncodingException_SW_VIDEO_COMPRESSION);
			// Only do raw live preview for DivX or MPEG1 encoding since it's not needed otherwise
			if (useDefaultSWEncoder && pythonParams && (pythonParams->outputstreamtype == 101  ||
				pythonParams->outputstreamtype == 11 || pythonParams->outputstreamtype == 2) &&
				GetRegistryDword(HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\DirectShow",
				"EnableRawLivePreview", 1))
			{
				slog((env, "Attempting to render live preview on capture device\r\n"));
				hr = pCapInfo->pBuilder->RenderStream(&PIN_CATEGORY_PREVIEW, &MEDIATYPE_Video,
					pCapInfo->pVideoCaptureFilter, NULL, NULL);
				HTESTPRINT(hr);
				if (SUCCEEDED(hr))
				{
					hr = pCapInfo->pGraph->QueryInterface(IID_IVideoWindow, (void**)&(pCapInfo->pPreviewVideo));
					HTESTPRINT(hr);
					if (SUCCEEDED(hr))
					{
						pCapInfo->pPreviewVideo->put_AutoShow(OAFALSE);
						pCapInfo->pPreviewVideo->put_MessageDrain(0);
//						pCapInfo->pPreviewVideo->put_Owner(0);
						pCapInfo->pPreviewVideo->put_Visible(OAFALSE);
					}
				}
			}
		}
		slog((env, "setupEncoding0 Trace12 muxingEncoder:%d useMyMpegMux:%d\r\n", muxingEncoder, useMyMpegMux ));
		BOOL useResync = GetRegistryDword(HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\DirectShow", "ResyncRawCapture", 0);
		if ((useDefaultSWEncoder && muxingEncoder) || (!capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_VIDEO_CAPTURE_MASK) &&
			!capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_AV_CAPTURE_MASK)))
		{
			useResync = FALSE;
		}

		if (useResync && pythonParams)
		{
			/*
			 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
			slog((env, "Adding resync filter to the graph\r\n"));
			hr = CoCreateInstance(CLSID_ReSync, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&(pCapInfo->pResync));
			ENCEXCEPT_RET(sage_EncodingException_DIRECTX_INSTALL);
			hr = pCapInfo->pGraph->AddFilter(pCapInfo->pResync, L"Resync");
			ENCEXCEPT_RET(sage_EncodingException_DIRECTX_INSTALL);
			IDivXReSyncFilter* pDivxResync = NULL;
			hr = pCapInfo->pResync->QueryInterface(IID_IDivXReSyncFilter, (void**)&pDivxResync);
			ASSERT(SUCCEEDED(hr));
			ReSyncSettings resyncSet;
			ZeroMemory(&resyncSet, sizeof(ReSyncSettings));
			pDivxResync->getReSyncFilterSettings(&resyncSet);
			if (pythonParams->fps == 25)
			{
				resyncSet.constantFramePeriodDividend = 1;
				resyncSet.constantFramePeriodDivisor = 25;
			}
			else if (pythonParams->fps == 15)
			{
				resyncSet.constantFramePeriodDividend = 1;
				resyncSet.constantFramePeriodDivisor = 15;
			}
			else // 30 fps (actually 29.97)
			{
				resyncSet.constantFramePeriodDividend = 1001;
				resyncSet.constantFramePeriodDivisor = 30000;
			}
			resyncSet.enforceConstantFramePeriod = 1;
			resyncSet.neverDropRepeatVideoFrames = 1;
			resyncSet.neverDropRepeatAudioFrames = 1;
			resyncSet.fieldPairing = 0;
			hr = pDivxResync->setReSyncFilterSettings(&resyncSet);
			HTESTPRINT(hr);
			SAFE_RELEASE(pDivxResync);
			 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
			 */
		}
slog((env, "setupEncoding0 Trace13 \r\n" ));
		// Add the multiplexing filter
		if (muxingEncoder)
		{
			hr = FindFilterByName(&(pCapInfo->pEncoder), CLSID_LegacyAmFilterCategory,
				pCapInfo->videoProcessorName, sizeof(pCapInfo->videoProcessorName) );
			ENCEXCEPT_RET(sage_EncodingException_SW_VIDEO_COMPRESSION);
			hr = pCapInfo->pGraph->AddFilter(pCapInfo->pEncoder, NULL);
			ENCEXCEPT_RET(sage_EncodingException_SW_VIDEO_COMPRESSION);
			slog((env, "VideoProcessor Name:%s\r\n", pCapInfo->videoProcessorName ));
			if ( pCapInfo->pSink == NULL )
			{
				hr = CoCreateInstance(CLSID_MPEG2Dump, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&(pCapInfo->pSink));
				ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
				hr = pCapInfo->pGraph->AddFilter(pCapInfo->pSink, L"Dump");
				ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
			}
//			else
			{
//				pCapInfo->pSink = pCapInfo->pEncoder;
//				pCapInfo->pSink->AddRef();

			}
		}
		else if (useMyMpegMux && pCapInfo->pSink == NULL)
		{
			hr = CoCreateInstance(CLSID_MpegMux, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&(pCapInfo->pSink));
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
			hr = pCapInfo->pGraph->AddFilter(pCapInfo->pSink, L"MPEG Multiplexor");
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
slog((env, "setupEncoding0 Trace14 \r\n" ));
		}
		else if (!writeToAvi)
		{
			// Insert the MainConcept MPEG Mux filter here
			//ZQ MC muxer
			/*
			 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
			hr = CoCreateInstance(CLSID_MainConceptMPEGMuxer, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&(pCapInfo->pEncoder));

			////ZQ ArcSoft
			//hr = CoCreateInstance(CLSID_ArcSoft_Media_Center_Muxer, NULL, CLSCTX_INPROC_SERVER,
			//	IID_IBaseFilter, (void**)&(pCapInfo->pEncoder));

			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
			hr = pCapInfo->pGraph->AddFilter(pCapInfo->pEncoder, L"MPEG Mux");
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
			 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
			 */

			//// Setup debug logging from the MainConcept codecs
			//IMCMpegMuxerFilter* pMEnc = NULL;
			//hr = pCapInfo->pEncoder->QueryInterface(IID_IMCMpegMuxerFilter, (void**)&pMEnc);
			//if (SUCCEEDED(hr))
			//{
			//	pMEnc->put_MuxerFilterSetting(MFSETTING_GET_RC, (DWORD)get_rc);
			//	SAFE_RELEASE(pMEnc);
			//}
			if ( pCapInfo->pSink == NULL )
			{
				hr = CoCreateInstance(CLSID_MPEG2Dump, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&(pCapInfo->pSink));
				ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
				hr = pCapInfo->pGraph->AddFilter(pCapInfo->pSink, L"Dump");
				ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
			}
slog((env, "setupEncoding0 Trace14a \r\n" ));
		}
		else
		{
			// For AVI we still need to use our dump filter. We set the AVIMux as the pEncoder
			// and we set the dump filter as the pSink

			// NOTE: WE CANNOT USE THE DIVX FILEWRITER BECAUSE IT DOESN'T SUPPORT
			// OUR BYTE TRACKING, IT CAN ONLY BE USED FOR REMUXING
			// NOTE2: That's OK because we don't need to track file size accurately while doing
			// DivX AVI recording.
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
			if (useDivxFW)
			{
				slog((env, "Using DivX AVI Mux\r\n"));
				hr = loadDivXFW(&(pCapInfo->pSink));
				if (FAILED(hr))
				{
					slog((env, "DivX AVI Mux not found...using MS AVI Mux\r\n"));
					useDivxFW = false;
				}
			}
			if (useDivxFW)
			{
				//slog((env, "Using DivX AVI Mux\r\n"));
				//hr = loadDivXFW(&(pCapInfo->pSink));
				//ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
				hr = pCapInfo->pGraph->AddFilter(pCapInfo->pSink, L"AVI Mux");
				ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
				// Configure the AVI Mux filter
				IAVIWriterConfig* pDivxConfig = NULL;
				hr = pCapInfo->pSink->QueryInterface(IID_IAVIWriterConfig, (void**)&pDivxConfig);
				ASSERT(SUCCEEDED(hr));
				// Changing audio preload and capture mode didn't have any effect
				// on the initial A/v sync being off in DivX files
				pDivxConfig->put_CaptureMode(TRUE);
				if (pythonParams->fps == 25)
				{
					pDivxConfig->put_Scale(1);
					pDivxConfig->put_Rate(25);
				}
				else if (pythonParams->fps == 15)
				{
					pDivxConfig->put_Scale(1);
					pDivxConfig->put_Rate(15);
				}
				else // 30 fps (actually 29.97)
				{
					pDivxConfig->put_Scale(1001);
					pDivxConfig->put_Rate(30000);
				}
				pDivxConfig->Release();
slog((env, "setupEncoding0 Trace15 \r\n" ));
			}
			else
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
			{
				slog((env, "Using MS AVI Mux\r\n"));
				hr = CoCreateInstance(CLSID_AviDest, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&(pCapInfo->pEncoder));
				ENCEXCEPT_RET(sage_EncodingException_DIRECTX_INSTALL);
				hr = pCapInfo->pGraph->AddFilter(pCapInfo->pEncoder, L"AVI Mux");
				ENCEXCEPT_RET(sage_EncodingException_DIRECTX_INSTALL);
				// Configure the AVI Mux filter
				IConfigInterleaving* pInterleave = NULL;
				hr = pCapInfo->pEncoder->QueryInterface(IID_IConfigInterleaving, (void**)&pInterleave);
				ASSERT(SUCCEEDED(hr));
				// Suggested times for doing Divx from some research
				REFERENCE_TIME t1 = 10000000;
				REFERENCE_TIME t2 = 5000000;
				hr = pInterleave->put_Interleaving(&t1, &t2);
				HTESTPRINT(hr);
				// Using INTERLEAVE_CAPTURE here with the PX-TV100U brings the preview to a crawl and
				// totally messes up what's in the AVI file
				hr = pInterleave->put_Mode(INTERLEAVE_FULL);
				HTESTPRINT(hr);
				SAFE_RELEASE(pInterleave);

				IConfigAviMux* pConfigAvi = NULL;
				hr = pCapInfo->pEncoder->QueryInterface(IID_IConfigAviMux, (void**)&pConfigAvi);
				ASSERT(SUCCEEDED(hr));
				// This was zero
				hr = pConfigAvi->SetMasterStream(1);
				HTESTPRINT(hr);
				hr = pConfigAvi->SetOutputCompatibilityIndex(TRUE);
				HTESTPRINT(hr);
				SAFE_RELEASE(pConfigAvi);
				if ( pCapInfo->pSink == NULL )
				{
					hr = CoCreateInstance(CLSID_MPEG2Dump, NULL, CLSCTX_INPROC_SERVER,
						IID_IBaseFilter, (void**)&(pCapInfo->pSink));
					ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
					hr = pCapInfo->pGraph->AddFilter(pCapInfo->pSink, L"Dump");
					ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
				}
slog((env, "setupEncoding0 Trace16 \r\n" ));
			}
		}
slog((env, "setupEncoding0 Trace17 \r\n" ));
		// Find the video compression filter and add it to the graph
		if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_VIDEO_CAPTURE_MASK) ||
			capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_AV_CAPTURE_MASK))
		{
			if (muxingEncoder)
			{
			}
			else if (useDefaultSWEncoder && pythonParams && pythonParams->outputstreamtype == 101)
			{
				// Load the DivX encoder if it's DivX. For MPEG2 we should not be here!
				/*
				 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
				hr = loadDivX(&(pCapInfo->pVideoCompressor));
				ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
				hr = pCapInfo->pGraph->AddFilter(pCapInfo->pVideoCompressor, NULL);
				ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
				if (pythonParams)
				{
					configureDivXVideoEncoder(pCapInfo->pVideoCompressor,
						pythonParams, env, writeToAvi);
				}
				 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
				 */
			}
			else if (strlen(pCapInfo->videoProcessorName))
			{
				hr = FindFilterByName(&(pCapInfo->pVideoCompressor), CLSID_VideoCompressorCategory, 
					pCapInfo->videoProcessorName, sizeof(pCapInfo->videoProcessorName) );
				if (FAILED(hr))
					hr = FindFilterByName(&(pCapInfo->pVideoCompressor), CLSID_LegacyAmFilterCategory,
					pCapInfo->videoProcessorName, sizeof(pCapInfo->videoProcessorName) );
				ENCEXCEPT_RET(sage_EncodingException_SW_VIDEO_COMPRESSION);
				hr = pCapInfo->pGraph->AddFilter(pCapInfo->pVideoCompressor, NULL);
				ENCEXCEPT_RET(sage_EncodingException_SW_VIDEO_COMPRESSION);
				slog((env, "VideoProcessor Name:%s\r\n", pCapInfo->videoProcessorName ));
			}
			else if (useDefaultSWEncoder)
			{
				////ZQ MC video encoder
				/*
				 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
				hr = CoCreateInstance(CLSID_EM2VE, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&(pCapInfo->pVideoCompressor));

				////ZQ ArcSoft video encoder
				//hr = CoCreateInstance(CLSID_Video_Encoder_Pro, NULL, CLSCTX_INPROC_SERVER,
				//	IID_IBaseFilter, (void**)&(pCapInfo->pVideoCompressor));

				ENCEXCEPT_RET(sage_EncodingException_SW_VIDEO_COMPRESSION);
				hr = pCapInfo->pGraph->AddFilter(pCapInfo->pVideoCompressor, NULL);
				ENCEXCEPT_RET(sage_EncodingException_SW_VIDEO_COMPRESSION);
				slog((env, "VideoProcessor Software encoder\r\n" ));
				 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
				 */

			}
			else
			{
				throwEncodingException(env, sage_EncodingException_SW_VIDEO_COMPRESSION, 0);
				return;
			}

			// Check to see if it supports the MainConcept configuration interface
			/*
			 * CONFIDENTIAL
			 * CONFIDENTIAL
			 * CONFIDENTIAL
			 * CONFIDENTIAL
			 * BEGIN MainConcept PROPRIETARY CODE BLOCK
			 */
			if (pythonParams)
			{
				//ZQ MC configure video
				configureMCVideoEncoder(pCapInfo, muxingEncoder ? pCapInfo->pEncoder : pCapInfo->pVideoCompressor,
					pythonParams, env);
				////ZQ ArcSoft audio encoder
				//configureArcSoftVideoEncoder(pCapInfo, muxingEncoder ? pCapInfo->pEncoder : pCapInfo->pVideoCompressor,
				//	pythonParams, env);

			}
			/*
			 * END MainConcept PROPRIETARY CODE BLOCK
			 * CONFIDENTIALf
			 * CONFIDENTIAL
			 * CONFIDENTIAL
			 * CONFIDENTIAL
			 */

			// Connect the videocapture to the multiplexor
			//hr = RenderStreamDebug(pCapInfo->pGraph,
			hr = RenderStream(pCapInfo->pGraph,
				&PIN_CATEGORY_CAPTURE,  // Pin category
				&MEDIATYPE_Video,       // Media type
				pCapInfo->pVideoCaptureFilter,                   // Capture filter
				pCapInfo->pVideoCompressor,                   // Compression filter (optional)
				(useMyMpegMux || (writeToAvi && useDivxFW)) ? pCapInfo->pSink : pCapInfo->pEncoder,  // Multiplexer or renderer filter
				pCapInfo->pResync
				);
			ENCEXCEPT_RET(sage_EncodingException_SW_VIDEO_COMPRESSION);

		}
		else if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_MPEG_VIDEO_ONLY_CAPTURE_MASK) ||
			capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK))
		{
			if (useMyMpegMux)
			{
				// Find the MPEG2 capture pin for hardware video compression
				IPin* pMpeg2CapPin = FindPin(pCapInfo->pVideoCaptureFilter, PINDIR_OUTPUT,
					&MEDIATYPE_Video, &MEDIASUBTYPE_MPEG2_VIDEO);
				if (!pMpeg2CapPin)
				{
					hr = E_FAIL;
					ENCEXCEPT_RET(sage_EncodingException_HW_VIDEO_COMPRESSION);
				}
				IPin* dumpInPin = FindPinByName(pCapInfo->pSink, "Video", PINDIR_INPUT);
				
				hr = pCapInfo->pGraph->ConnectDirect(pMpeg2CapPin, dumpInPin, prefVideoMT);
				SAFE_RELEASE(dumpInPin);
				SAFE_RELEASE(pMpeg2CapPin);
				ENCEXCEPT_RET(sage_EncodingException_HW_VIDEO_COMPRESSION);
			}
			else
			{
				// Find the video capture pin for hardware video compression
				IPin* pVideoCapPin = FindPinByCategoryAndType(pCapInfo->pVideoCaptureFilter, &PIN_CATEGORY_CAPTURE,
					&MEDIATYPE_Video);
				if (!pVideoCapPin)
				{
					hr = E_FAIL;
					ENCEXCEPT_RET(sage_EncodingException_HW_VIDEO_COMPRESSION);
				}
				IPin* muxInPin = FindUnconnectedPin(pCapInfo->pEncoder, PINDIR_INPUT);
				ASSERT(muxInPin);
				hr = pCapInfo->pGraph->ConnectDirect(pVideoCapPin, muxInPin, NULL);
				SAFE_RELEASE(muxInPin);
				SAFE_RELEASE(pVideoCapPin);
				ENCEXCEPT_RET(sage_EncodingException_HW_VIDEO_COMPRESSION);
			}
		}

		if (prefVideoMT)
			DeleteMediaType(prefVideoMT);
		// Now we need to setup the audio compression in software. There's no devices
		// that do hardware audio encoding w/out multiplexing that we support yet
		// If they've got a filter specified, then use that. Otherwise first we try the 
		// SM audio compressor. If we can't find it, we go to the LAME compressor
		if (muxingEncoder)
		{
		}
		else if (strlen(pCapInfo->audioProcessorName))
		{
			hr = FindFilterByName(&(pCapInfo->pAudioCompressor), CLSID_AudioCompressorCategory, 
				pCapInfo->audioProcessorName, sizeof(pCapInfo->audioProcessorName) );
			if (FAILED(hr))
				hr = FindFilterByName(&(pCapInfo->pAudioCompressor), CLSID_LegacyAmFilterCategory,
				pCapInfo->audioProcessorName, sizeof(pCapInfo->audioProcessorName));
			ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
			hr = pCapInfo->pGraph->AddFilter(pCapInfo->pAudioCompressor, NULL);
			slog((env, "AudioProcessor Name:%s\r\n", pCapInfo->audioProcessorName ));
			ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
		}
		else
		{
			pCapInfo->pAudioCompressor = NULL;
			// Use our own MPEG Layer II encoder for AVIs since we maintain sync correctly in that encoder
			if (writeToAvi)
			{
				hr = CoCreateInstance(CLSID_MpegAudioEncLayer2, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&(pCapInfo->pAudioCompressor));
				slog((env, "AudioProcessor MpegAudioEncLayer2 hr=0x%x\r\n", hr ));
			}
			else
			{   //ZQ MC audio encoder
				/*
				 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
				hr = CoCreateInstance(CLSID_EL2AudioEncoder, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&(pCapInfo->pAudioCompressor));
				slog((env, "AudioProcessor EL2AudioEncoder hr=0x%x\r\n", hr ));

				////ZQ ArcSoft audio encoder
				//hr = CoCreateInstance(CLSID_ArcSoft_Mpeg2Audio_Encoder, NULL, CLSCTX_INPROC_SERVER,
				//	IID_IBaseFilter, (void**)&(pCapInfo->pAudioCompressor));
				//slog((env, "AudioProcessor Arcsoft AudioEncoder hr=0x%x\r\n", hr ));

				 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
				 */
			}
			if (SUCCEEDED(hr))
			{
				hr = pCapInfo->pGraph->AddFilter(pCapInfo->pAudioCompressor, NULL);
				ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
			}
			else
			{

				hr = FindFilterByName(&(pCapInfo->pAudioCompressor), CLSID_AudioCompressorCategory, 
					"Intervideo Audio Encoder");
				if (SUCCEEDED(hr))
				{
					// We need to set the registry parameters and then reload it
					SAFE_RELEASE(pCapInfo->pAudioCompressor);
					if (pythonParams)
					{
						// Setup the configuration for this through the registry
						HKEY ivAudEncKey;
						if (RegCreateKeyEx(HKEY_CURRENT_USER, "Software\\InterVideo\\Common\\Audio", 0, 
								NULL, REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, NULL, &ivAudEncKey, NULL) == ERROR_SUCCESS)
						{
							DWORD value;
							DWORD hSize = sizeof(value);
							value = pythonParams->audiobitrate;
							RegSetValueEx(ivAudEncKey, "MPEGAudioBitRate", 0, REG_DWORD, (LPBYTE)&value, hSize);
							value = pythonParams->audiosampling;
							RegSetValueEx(ivAudEncKey, "MPEGAudioSamplingFreq", 0, REG_DWORD, (LPBYTE)&value, hSize);
							value = 1;
							RegSetValueEx(ivAudEncKey, "MPEGAudioRealtimeEncode", 0, REG_DWORD, (LPBYTE)&value, hSize);
							value = pythonParams->audiooutputmode;
							RegSetValueEx(ivAudEncKey, "MPEGAudioSystemMode", 0, REG_DWORD, (LPBYTE)&value, hSize);
							value = 75;
							RegSetValueEx(ivAudEncKey, "MPEGAudioQuality", 0, REG_DWORD, (LPBYTE)&value, hSize);
							RegCloseKey(ivAudEncKey);
						}
					}
					hr = FindFilterByName(&(pCapInfo->pAudioCompressor), CLSID_AudioCompressorCategory, 
						"Intervideo Audio Encoder");
					if (SUCCEEDED(hr))
					{
						hr = pCapInfo->pGraph->AddFilter(pCapInfo->pAudioCompressor, NULL);
						if (!SUCCEEDED(hr))
						{
							HTESTPRINT(hr);
							SAFE_RELEASE(pCapInfo->pAudioCompressor);
						}
						else
						{
							slog(("Using Intervideo Audio Encoder\r\n"));
						}
					}
				}
			}
slog((env, "setupEncoding0 Trace18 \r\n" ));
			if (!pCapInfo->pAudioCompressor)
			{
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
				hr = CoCreateInstance(CLSID_SMAudioCompressor, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&(pCapInfo->pAudioCompressor));
				if (SUCCEEDED(hr))
				{
					hr = pCapInfo->pGraph->AddFilter(pCapInfo->pAudioCompressor,
						L"MPEG Audio Compressor");
					ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
					slog(("Using SMAudioCompressor\r\n"));
					// Setup the audio codec properties
					if (pythonParams)
					{
						ISmaudio *p_ISmaudio = NULL;
						hr = pCapInfo->pAudioCompressor->QueryInterface(IID_ISmaudio, (void**)&p_ISmaudio);
						if (SUCCEEDED(hr))
						{
							p_ISmaudio->SetAudioBitrate(pythonParams->audiobitrate);
							p_ISmaudio->SetSampleFrequency(pythonParams->audiosampling);
							SAFE_RELEASE(p_ISmaudio);
						}
					}
				}
				else
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
				{
					hr = FindFilterByName(&(pCapInfo->pAudioCompressor), CLSID_LegacyAmFilterCategory,
						"LAME MPEG Layer III Audio Encoder");
					ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
					hr = pCapInfo->pGraph->AddFilter(pCapInfo->pAudioCompressor, NULL);
					ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
					slog(("Using LAME MPEG Layer III Audio Encoder\r\n"));
				}
			}
		}
slog((env, "setupEncoding0 Trace19 \r\n" ));
		if (pythonParams)
		{

			// Check for the MainConcept audio encoder configuration
			//ZQ MC config
			configureMCAudioEncoder((muxingEncoder ? pCapInfo->pEncoder : pCapInfo->pAudioCompressor),
				pythonParams, env);

			////ZQ ArcSoft config
			//configureArcSoftAudioEncoder((muxingEncoder ? pCapInfo->pEncoder : pCapInfo->pAudioCompressor),
			//	pythonParams, env);

			/*
			if (pCapInfo->pAudioCompressor)
			{
				// Check for our own MPEG LayerII encoder properties
				IAudioEncoderProperties *p_encaudio = NULL;
				hr = pCapInfo->pAudioCompressor->QueryInterface(IID_IAudioEncoderProperties, (void**)&p_encaudio);
				if (SUCCEEDED(hr))
				{
					slog((env, "Configured MPEGLayerII audio encoder\r\n"));
					// we can only go to 320, where the other stuff goes to 384 or higher
					p_encaudio->set_Bitrate(min(320, pythonParams->audiobitrate));
					p_encaudio->set_SampleRate(pythonParams->audiosampling);
					if (useDivxFW && writeToAvi)
					{
						// NOTE: For DivX AVI Writer it always had about a half second of audio delay in it
						// initially. It's just a mistake in how they're writing the AVI streams I guess. 
						// This was the best way around it, although it's rather ugly....
						// Testing showed that 30000 samples at 48khz was the appropriate value
						DWORD offsetDirection = GetRegistryDword(HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\DirectShow",
								"DivXWriterAudioSampleOffsetForward", 0);
						DWORD sampleDelay = GetRegistryDword(HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\DirectShow",
								"DivXWriterAudioSampleOffset", 30000);
						sampleDelay = sampleDelay * pythonParams->audiosampling / 48000;
						LONGLONG initDelay = (offsetDirection ? 1 : -1) * (LONGLONG) sampleDelay;
						slog((env, "DivX audio delay %ld\r\n", initDelay));
						p_encaudio->set_InitialAudioDelay(initDelay);
					}
					SAFE_RELEASE(p_encaudio);
				}
			}
			*/
		}
slog((env, "setupEncoding0 Trace20 \r\n" ));
		if (strlen(pCapInfo->audioCaptureFilterName))
		{
			// Add the audio capture filter to the graph
			HRESULT hr = FindFilterByName(&(pCapInfo->pAudioCaptureFilter), CLSID_AudioInputDeviceCategory,
				pCapInfo->audioCaptureFilterName, pCapInfo->audioCaptureFilterNum);
			// Also check the WDM Streaming capture devices category
			if (FAILED(hr))
			{
				hr = FindFilterByName(&(pCapInfo->pAudioCaptureFilter), AM_KSCATEGORY_CAPTURE,
					pCapInfo->audioCaptureFilterName, pCapInfo->audioCaptureFilterNum);
			}
			ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
			hr = pCapInfo->pGraph->AddFilter(pCapInfo->pAudioCaptureFilter, NULL);
			ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);

			// Hook up the analog audio output of the crossbar to the input of the audio capture
			// filter if the pins exist.
			BOOL bAudio2CrossbarConnected = FALSE;
			IPin* pCrossAudioOut = FindPin(pCapInfo->pCrossbar, PINDIR_OUTPUT, &MEDIATYPE_AnalogAudio, NULL);
			if (pCrossAudioOut)
			{
				slog((env, "Connecting up analog audio streams\r\n"));
				IEnumPins* pEm = NULL;
				hr = pCapInfo->pAudioCaptureFilter->EnumPins(&pEm);
				if (hr == NOERROR)
				{
					HMIXER m_HMixer = NULL;
					MIXERLINE mxl;
					pEm->Reset();

					// We need to determine the name of the "Line In" input since that's the only way to find
					// the appropriate pin on the audio capture filter to enable. This information can be found by
					// querying the system mixer info.
					//ZQ. we need src index to find correct mixer src, if not specify, default to "Line In" ZQ.
					char audioInputName[MIXER_SHORT_NAME_CHARS];
					int  src_index = MIXERLINE_COMPONENTTYPE_SRC_LINE;
					if ( pCapInfo->audioCaptureFilterSrcIndex >= 0 )
						src_index = pCapInfo->audioCaptureFilterSrcIndex;
					audioInputName[0] = '\0';
					int numDevs = mixerGetNumDevs();
					if (numDevs >= 1)
					{
						INT m_iMixerControlID = 0;
						MMRESULT mmr = 0;
						DWORD m_dwChannels = 0;
						mmr = mixerOpen(&m_HMixer, 0, 0, 0L, CALLBACK_NULL);
						if (mmr == MMSYSERR_NOERROR)
						{
							mxl.cbStruct = sizeof(MIXERLINE);
							mxl.dwComponentType = MIXERLINE_COMPONENTTYPE_DST_SPEAKERS;
							if (mixerGetLineInfo((HMIXEROBJ)m_HMixer, &mxl, MIXER_OBJECTF_HMIXER | MIXER_GETLINEINFOF_COMPONENTTYPE) == MMSYSERR_NOERROR)
							{
								UINT nconn = mxl.cConnections;
								DWORD DstIndex = mxl.dwDestination;
								for( UINT j = 0; j < nconn; j++ )
								{
									mxl.cbStruct = sizeof( MIXERLINE );
									mxl.dwSource = j;
									mxl.dwDestination = DstIndex;
									if (mixerGetLineInfo( ( HMIXEROBJ )m_HMixer,
										&mxl, MIXER_GETLINEINFOF_SOURCE ) == MMSYSERR_NOERROR)
									{
										/*
										* I had an issue with it selecting the "Mono In" input instead
										* of the line in. This clears it up by ensuring it's stereo input.
										*/
										if ( src_index >= 0 && j == src_index )
										{
											strncpy(audioInputName, mxl.szShortName, sizeof(audioInputName) );
											break;
										}
										if ( src_index < 0 && mxl.dwComponentType == MIXERLINE_COMPONENTTYPE_SRC_LINE 
											&& mxl.cChannels == 2 )
										{
											strncpy(audioInputName, mxl.szShortName, sizeof(audioInputName) );
											break;
										} 
									}
								}
							}
							mixerClose( m_HMixer );
							m_HMixer = NULL;
						}
					}
					ULONG cFetched;
					IPin* pPin = NULL;
					int index = 0;
					while((hr = pEm->Next(1, &pPin, &cFetched)) == S_OK)
					{
						char szFriendlyName[128];
						PIN_INFO PinInfo;
						//
						// grab this, so we can examine its name field
						//
						hr = pPin->QueryPinInfo(&PinInfo);
						if(SUCCEEDED(hr))
						{
							WideCharToMultiByte(CP_ACP, 0, PinInfo.achName, -1,
										szFriendlyName, 128, NULL, NULL);
							PinInfo.pFilter->Release();
							//
							// check direction
							//
							if (PinInfo.dir == PINDIR_INPUT)
							{
								IAMAudioInputMixer *pMix = NULL;
								hr = pPin->QueryInterface(IID_IAMAudioInputMixer, (void**)&pMix);
								if (SUCCEEDED(hr))
								{
									BOOL res = 0;
									if (audioInputName[0])
									{
										res = !strcmp(audioInputName, szFriendlyName);
										if (res)
										{
											pMix->put_Enable(TRUE);
											//mute to speaker ZQ.
											{
												MMRESULT mmr;
												MIXERCONTROLDETAILS mxcd;
												MIXERCONTROLDETAILS_BOOLEAN mxcd_u;
												MIXERCONTROL mxc;
												MIXERLINECONTROLS mxlc;
												mxlc.cbStruct = sizeof(MIXERLINECONTROLS);
												mxlc.dwLineID = mxl.dwLineID;
												mxlc.dwControlType = MIXERCONTROL_CONTROLTYPE_MUTE;
												mxlc.cControls = 1;
												mxlc.cbmxctrl = sizeof(MIXERCONTROL);
												mxlc.pamxctrl = &mxc;
												mmr = mixerGetLineControls((HMIXEROBJ)m_HMixer, &mxlc, 
													MIXER_OBJECTF_HMIXER | MIXER_GETLINECONTROLSF_ONEBYTYPE);
											
												mxcd.cbStruct = sizeof(mxcd);
												mxcd.dwControlID = mxc.dwControlID;
												mxcd.cChannels = 1;
												mxcd.cMultipleItems = 0;
												mxcd.cbDetails = sizeof(mxcd_u);
												mxcd.paDetails = &mxcd_u;
												mmr = mixerGetControlDetails((HMIXEROBJ)m_HMixer, &mxcd, 
													MIXER_GETCONTROLDETAILSF_VALUE | MIXER_OBJECTF_HMIXER);
												if ( MMSYSERR_NOERROR == mmr ) 
												{
													mxcd_u.fValue  = 1;
													mmr = mixerSetControlDetails((HMIXEROBJ)m_HMixer, &mxcd, 
														MIXER_SETCONTROLDETAILSF_VALUE | MIXER_OBJECTF_HMIXER);
													if ( MMSYSERR_NOERROR != mmr ){
														slog((env, "Mute src to speaker failed\r\n"));
													}
												}
											}
										}
									}
									else
										pMix->get_Enable(&res);
									if (res)
									{
										pMix->Release();
										hr = pCapInfo->pGraph->Connect(pCrossAudioOut, pPin);
										HTESTPRINT(hr);
										if (SUCCEEDED(hr))
										{
											slog((env, "Successfully connected up analog audio stream\r\n"));
											bAudio2CrossbarConnected = TRUE;
										}
										pPin->Release();
										break;
									}
									pMix->Release();
								}
							}
						}
						pPin->Release();
					}
					pEm->Release();
					if ( m_HMixer != NULL )
						mixerClose( m_HMixer );
					m_HMixer = NULL;
				}
				pCrossAudioOut->Release();
slog((env, "setupEncoding0 Trace21 \r\n" ));
			}
			if ( !bAudio2CrossbarConnected )
			{
				//if there is no audio oupint from crossbar is able to be connected to audio capture
				//check if there audio pin from video capture
				IPin* pAudioOutPin = FindPin(pCapInfo->pVideoCaptureFilter, PINDIR_OUTPUT, &MEDIATYPE_Audio, NULL);
				if ( pAudioOutPin != NULL )
				{
					IPin* pAudioCapInPin = FindPin(pCapInfo->pAudioCaptureFilter, PINDIR_INPUT, &MEDIATYPE_Audio, NULL);
					if ( pAudioCapInPin != NULL )
					{
						hr = pCapInfo->pGraph->ConnectDirect(pAudioOutPin, pAudioCapInPin, NULL);
						if (SUCCEEDED(hr))
						{
							slog((env, "Successfully connected up analog audio stream to sw capture\r\n"));
							bAudio2CrossbarConnected = TRUE;
						}
						SAFE_RELEASE( pAudioCapInPin );

					}
					SAFE_RELEASE( pAudioOutPin );
				}
			}
			if ( !bAudio2CrossbarConnected )
				slog((env, "failed to connect up analog audio stream to sw capture\r\n"));

		}
		else if ( capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_MPEG_VIDEO_ONLY_CAPTURE_MASK ) 
				|| capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_VIDEO_CAPTURE_MASK ))
		{
			throwEncodingException(env, sage_EncodingException_SW_AUDIO_COMPRESSION, 0);
			return;
		}
slog((env, "setupEncoding0 Trace22 \r\n" ));
		if (pCapInfo->pAudioCaptureFilter)
		{
			// Configure the properties for the audio capture source
			hr = ConfigureAudioCapturePin(pCapInfo, pythonParams);
			ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);

			//hr = RenderStreamDebug(pCapInfo->pGraph,
			hr = RenderStream(pCapInfo->pGraph,
				&PIN_CATEGORY_CAPTURE,  // Pin category
				&MEDIATYPE_Audio,       // Media type
				pCapInfo->pAudioCaptureFilter,                   // Capture filter
				pCapInfo->pAudioCompressor,                   // Compression filter (optional)
				(useMyMpegMux || (writeToAvi && useDivxFW)) ? pCapInfo->pSink : pCapInfo->pEncoder,  // Multiplexer or renderer filter
				pCapInfo->pResync
			);
			ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
slog((env, "setupEncoding0 Trace23 \r\n" ));
		}
		else if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK) ||
				capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_AV_CAPTURE_MASK))
		{
			// Configure the properties for the audio capture source
			if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_SM2210_ENCODER_MASK))
			{
				// Setup the audio sampling frequency
				IKsPropertySet *pIKsBridgePropSet = NULL;
				hr = pCapInfo->pVideoCaptureFilter->QueryInterface(IID_IKsPropertySet, (PVOID *) &pIKsBridgePropSet);
				if (SUCCEEDED(hr))
				{
					KSPROPERTY_CAPTURE_AUDIO_CONTROL_S m_Audio_Control;
					if (pythonParams && pythonParams->audiosampling == 44100)
						m_Audio_Control.audioSampleFrequency = 0;
					else
						m_Audio_Control.audioSampleFrequency = 1; // 48000
					hr = pIKsBridgePropSet->Set(PROPSETID_CAPTURE_PERIPHERAL, 
												3,//KSPROPERTY_CAPTURE_AUDIO_CONTROL
												&m_Audio_Control,
												sizeof(KSPROPERTY_CAPTURE_AUDIO_CONTROL_S), 
												&m_Audio_Control, sizeof(KSPROPERTY_CAPTURE_AUDIO_CONTROL_S));
					if (pIKsBridgePropSet)
						SAFE_RELEASE(pIKsBridgePropSet);
				}
			}
			else if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_RAW_AV_CAPTURE_MASK))
			{
				// Configure the properties for the audio capture source
//				hr = ConfigureAudioCapturePin(pCapInfo, pythonParams);
//				ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
			}
			// Find the MPEG2 capture pin
			IPin* pAudioCapPin = FindPin(pCapInfo->pVideoCaptureFilter, PINDIR_OUTPUT, &MEDIATYPE_Audio, NULL);
			/*IPin* pAudioCapPin = FindPinByCategoryAndType(pCapInfo->pVideoCaptureFilter, 
				&PIN_CATEGORY_CAPTURE, &MEDIATYPE_Audio);*/
			if (!pAudioCapPin)
			{
				throwEncodingException(env, sage_EncodingException_SW_AUDIO_COMPRESSION, 0);
				return;
			}
slog((env, "setupEncoding0 Trace24 \r\n" ));			
			if (muxingEncoder)  //ZQ. if SW audio and SW video compress, and muxingEncode, utilize pEncoder's audio compress 
			{
				IPin* muxInPin = FindPin(pCapInfo->pEncoder, PINDIR_INPUT,	&MEDIATYPE_Audio, NULL);
				ASSERT(muxInPin);
				hr = pCapInfo->pGraph->ConnectDirect( pAudioCapPin, muxInPin, NULL);
				SAFE_RELEASE(muxInPin);
				ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);

			} else
			{
				IPin* audioCompInPin = FindUnconnectedPin(pCapInfo->pAudioCompressor, PINDIR_INPUT);
				//IPin* audioCompInPin = FindPin(pCapInfo->pAudioCompressor, PINDIR_INPUT, &MEDIATYPE_Audio, NULL);
				if (!audioCompInPin)
				{
					SAFE_RELEASE(pAudioCapPin);
					hr = E_FAIL;
					slog((env, "failed get audioCompInPin from AudioCompressor.\r\n" ));
					throwEncodingException(env, sage_EncodingException_SW_AUDIO_COMPRESSION, 0);
					return;
				}
				hr = pCapInfo->pGraph->ConnectDirect(pAudioCapPin, audioCompInPin, NULL);
				SAFE_RELEASE(audioCompInPin);
				SAFE_RELEASE(pAudioCapPin);
				ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
			
				if (useMyMpegMux)
				{
					hr = ConnectPins(pCapInfo->pGraph, pCapInfo->pAudioCompressor, NULL, pCapInfo->pSink, "Audio", TRUE);
					ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
				}
				else
				{
					IPin* audioCompOutPin = FindUnconnectedPin(pCapInfo->pAudioCompressor, PINDIR_OUTPUT);
					ASSERT(audioCompOutPin);
					IPin* muxInPin = FindUnconnectedPin(pCapInfo->pEncoder, PINDIR_INPUT);
					ASSERT(muxInPin);
					hr = pCapInfo->pGraph->ConnectDirect(audioCompOutPin, muxInPin, NULL);
					SAFE_RELEASE(muxInPin);
					SAFE_RELEASE(audioCompOutPin);
					ENCEXCEPT_RET(sage_EncodingException_SW_AUDIO_COMPRESSION);
				}
slog((env, "setupEncoding0 Trace25 \r\n" ));			
			}
		}
		else
		{
			throwEncodingException(env, sage_EncodingException_SW_AUDIO_COMPRESSION, 0);
			return;
		}

		if (!useMyMpegMux && !writeToAvi)
		{
			/*
			 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
			// Check for the MainConcept mux configuration
			//ZQ MC Muxer
			if (pythonParams)
			{
				IMCMpegMuxer* pMCProps = NULL;
				hr = pCapInfo->pEncoder->QueryInterface(IID_IMCMpegMuxer, (void**)&pMCProps);
				if (SUCCEEDED(hr))
				{
					slog((env, "Performing MC MPEG mux configuration\r\n"));
					struct mpeg_m_settings mMpegSettings;
					DWORD dwMpegType;
					switch (pythonParams->width*pythonParams->height)
					{
						case 352*240:
						case 352*288:
							dwMpegType=MPEG_VCD;
							break;
						//case 480*480: // This has very high latency in capture so don't use it
						//case 480*576:
						//	dwMpegType=MPEG_SVCD;
						//	break;
						case 720*480:
						case 720*576:
							dwMpegType=MPEG_DVD;
							break;
						default:
							if (pythonParams->height <= 288)
								dwMpegType=MPEG_MPEG1;
							else
								dwMpegType=MPEG_MPEG2;
					}
					// This helps with latency problems in capture
					dwMpegType=MPEG_MPEG2;
					if (pythonParams->outputstreamtype == 2)
						dwMpegType = MPEG_MPEG1;
					else if (pythonParams->outputstreamtype == 11)
						dwMpegType = MPEG_VCD;
					hr = pMCProps->GetDefaultMuxerSettings(dwMpegType, &mMpegSettings);
					HTESTPRINT(hr);
					mMpegSettings.VBR_multiplex = pythonParams->vbr;
//					mMpegSettings.always_sys_header = 1;
					mMpegSettings.put_private2 = 0;
					hr = pMCProps->put_MuxerSettings(&mMpegSettings);
					HTESTPRINT(hr);
					SAFE_RELEASE(pMCProps);
				}
			}
			 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
			 */
		}
slog((env, "setupEncoding0 Trace26 \r\n" ));			
		// We still need to connect the mux output to the dump
		if (pCapInfo->pEncoder != pCapInfo->pSink && pCapInfo->pEncoder && pCapInfo->pSink)
		{
			IPin* muxOut = FindUnconnectedPin(pCapInfo->pEncoder, PINDIR_OUTPUT);
			ASSERT(muxOut);
			IPin* dumpIn = FindUnconnectedPin(pCapInfo->pSink, PINDIR_INPUT);
			ASSERT(dumpIn);
			hr = pCapInfo->pGraph->ConnectDirect(muxOut, dumpIn, NULL);
			SAFE_RELEASE(dumpIn);
			SAFE_RELEASE(muxOut);
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		}
	}

	if (jfilename)
	{
		const char* name = env->GetStringUTFChars(jfilename, 0);
		slog((env, "Encoding to file %s\r\n", name));
		env->ReleaseStringUTFChars(jfilename, name);
	}
	else
	{
		slog((env, "Encoding to /dev/null ;)\r\n"));
	}

	IFileSinkFilter* pfsf = NULL;
	pCapInfo->pSink->QueryInterface(IID_IFileSinkFilter, (void**)&pfsf);
	jchar* wsz = NULL;
	if (jfilename)
		wsz = (jchar*) env->GetStringChars(jfilename, NULL);
	hr = pfsf->SetFileName((LPCOLESTR)wsz, NULL);
	if (jfilename)
		env->ReleaseStringChars(jfilename, wsz);
	SAFE_RELEASE(pfsf);
	ENCEXCEPT_RET(sage_EncodingException_FILESYSTEM);
slog((env, "setupEncoding0 Trace27 \r\n" ));			
	if (pCapInfo->recordBufferSize)
	{
		IMpegMux* pMuxy = NULL;
		hr = pCapInfo->pSink->QueryInterface(IID_IMpegMux, (void**)&pMuxy);
		ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		pMuxy->put_CircularSize((long) pCapInfo->recordBufferSize);
		SAFE_RELEASE(pMuxy);
	}
slog((env, "setupEncoding0 Trace28 \r\n" ));			
}
/*
 * Class:     sage_DShowCaptureDevice
 * Method:    startEncoding0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_startEncoding0
  (JNIEnv *env, jobject jo, jlong capInfo)
{
	HRESULT hr;
	if ( capInfo == NULL )
		return ;

	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*)capInfo;
	slog((env, "startEncoding0 called for '%s-%d'\r\n", 
		pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum));

	if ( capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK )
		&& !( pCapInfo->dwTuneState & 0x01 ) )
	{
		slog((env, "Tuner isn't tuned, can't start encoder for '%s-%d'\r\n", 
			pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum));
		return;
	}


	if ( capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ) 
		&& pCapInfo->pDebugSrcSink != NULL )  //when debugsrc is on, always do this for debug //ZQ
	{		
		hr = pCapInfo->pMC->Run();
		return;
	}

	if ( capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ) 
		&& !capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ) 
		&& pCapInfo->pBDATuner == NULL && pCapInfo->pBDACapture == NULL )
	{
		slog((env, "startEncoding '%s-%d' failed, BDA tuner is not initailized \r\n", 
			             pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum));
		hr = E_FAIL;
		throwEncodingException( env, sage_EncodingException_CAPTURE_DEVICE_INSTALL, hr );
		return;
	}
	GETENCODERMUTEX;
	// Start capturing data
    hr = pCapInfo->pMC->Run();
    if(FAILED(hr) || !WaitForState(env, pCapInfo->pMC, State_Running))
	{
        // stop parts that started
        pCapInfo->pMC->Stop();
		WaitForState(env, pCapInfo->pMC, State_Stopped);
		slog((env, "Failed: startEncoding0 Run return code:%x  %d\r\n", hr, pCapInfo->dwTuneState  )); 
		throwEncodingException(env, sage_EncodingException_CAPTURE_DEVICE_INSTALL, hr);
    }

	RELEASEENCODERMUTEX;
}


/*
 * Class:     sage_DShowCaptureDevice
 * Method:    switchOutputFile0
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_switchOutputFile0
  (JNIEnv *env, jobject jo, jlong capInfo, jstring jnewFilename)
{
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if (!pCapInfo->pSink)
	{
		slog((env, "ERROR: Cannot switch output file because there isn't one yet.\r\n"));
		return;
	}
		
	HRESULT hr;
	REFERENCE_TIME start = MAX_TIME, stop = MAX_TIME;

	slog((env, "switchOutputFile0 called\r\n"));
    const char* name = env->GetStringUTFChars(jnewFilename, 0);
	slog((env, "Switching Encoding to file %s\r\n", name));
    env->ReleaseStringUTFChars(jnewFilename, name);

	IMpegMux* pMuxy = NULL;
	hr = pCapInfo->pSink->QueryInterface(IID_IMpegMux, (void**)&pMuxy);
	ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
	// Setup the circular file
	pMuxy->put_CircularSize((long) pCapInfo->recordBufferSize);
	SAFE_RELEASE(pMuxy);

	IFileSinkFilter* pfsf = NULL;
	pCapInfo->pSink->QueryInterface(IID_IFileSinkFilter, (void**)&pfsf);
	jchar* wsz = (jchar*) env->GetStringChars(jnewFilename, NULL);
	hr = pfsf->SetFileName((LPCOLESTR)wsz, NULL);
	SAFE_RELEASE(pfsf);
	env->ReleaseStringChars(jnewFilename, wsz);
	ENCEXCEPT_RET(sage_EncodingException_FILESYSTEM)
}

JNIEnv *__env;
char* stack_p;
int mycatcher(unsigned int code, struct _EXCEPTION_POINTERS *ep) 
{
	slog(( __env, "ZQZQZQ Catch exception \r\n")); 
   if (code == EXCEPTION_ACCESS_VIOLATION) {
      return EXCEPTION_EXECUTE_HANDLER;
   }
   else {
      return EXCEPTION_CONTINUE_SEARCH;
   };
}



/*
 * Class:     sage_DShowCaptureDevice
 * Method:    stopEncoding0
 * Signature: (J)V
 */
//extern "C" void __security_init_cookie();
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_stopEncoding0
  (JNIEnv *env, jobject jo, jlong capInfo)
{
	//__security_init_cookie();
	/*
	 * This stops the current graph activity, preview & capture.
	 */
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if (!pCapInfo)
		return;

	if ( capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ) 
		&& pCapInfo->pDebugSrcSink != NULL )  //when debugsrc is on, always do this for debug //ZQ
	{		
		pCapInfo->pMC->Stop();
		return;
	}

	GETENCODERMUTEX;
	slog((env, "stopEncoding0 called (config:0x%x)\r\n", pCapInfo->captureConfig )); 

	if ( capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ) )
	{
		if (  pCapInfo->filterState <2 )  //if BDATuner is not intialized, BDA crash when stop ZQ.
			goto loc_skip;

		//stop parser, not leak data into next recording file
		if ( pCapInfo->pSplitter != NULL )
		{
	slog((env, "stopEncoding0 trace 1 0x%x\r\n", pCapInfo->pSplitter  )); 
			ITSParser2 *pTSParser = NULL;
			HRESULT hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser );
			if ( !FAILED( hr ) )
				pTSParser->StopParser( );
			SAFE_RELEASE(  pTSParser )
	slog((env, "stopEncoding0 trace 2\r\n" )); 
		}
	}

    // Stop the whole graph, remove the non-capture filters
    if (pCapInfo->pMC)
	{
		if ( strcmp( pCapInfo->TAG, DSHOWTAG ) )
		{
			slog((env, "stopEncoding0 Failed, CapInfo is damaged. %s\r\n",  pCapInfo->TAG ));
			return;
		}
slog((env, "stopEncoding0 trace 3 0x%x\r\n", pCapInfo->pMC  )); 
		pCapInfo->pMC->Stop();
slog((env, "stopEncoding0 trace 4\r\n" )); 

		FILTER_STATE testGraphState;
		HRESULT hr = pCapInfo->pMC->GetState(500, (OAFilterState*)&testGraphState);
		//WaitForState(env, pCapInfo->pMC, State_Stopped);
		slog((env, "Stopped graph res=0x%x\r\n", hr));
	}

loc_skip:
	// Release the video preview window interface
slog((env, "stopEncoding0 trace 5\r\n" )); 
	if (pCapInfo->pPreviewVideo)
	{
		pCapInfo->pPreviewVideo->put_Visible(OAFALSE);
		pCapInfo->pPreviewVideo->put_MessageDrain(NULL);
		// NOTE: MS Docs say this is essential, but it causes the window to lose/gain focus
		// and the only way to fix that is to not do this operation
//		pCapInfo->pPreviewVideo->put_Owner(NULL);
		SAFE_RELEASE(pCapInfo->pPreviewVideo);
	}

	if ( pCapInfo->pVideoCaptureFilter )
	{
	    // This'll cleanup stuff from preview & closed captioning & anything else that might have
	    // got auto inserted downstream from the capture filter
        RemoveAllOutputFilters(pCapInfo->pGraph, pCapInfo->pVideoCaptureFilter);
	}

	

    // Release DirectShow interfaces
	if (pCapInfo->pGraph)
	{
		// Some of this may be redundant, but that's OK
		if (pCapInfo->pCCSink)
			pCapInfo->pGraph->RemoveFilter(pCapInfo->pCCSink);
		if (pCapInfo->pSink)
		{	
			pCapInfo->pGraph->RemoveFilter(pCapInfo->pSink);

			//temp solution for a M780 bug that reference not release
			if ( !capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ) )
			{
				IMpegMux* pMuxy = NULL;
				HRESULT hr = pCapInfo->pSink->QueryInterface(IID_IMpegMux, (void**)&pMuxy);
				if (!FAILED(hr)) pMuxy->ForceCleanUp();
				SAFE_RELEASE(pMuxy);
			}
		}

		if (pCapInfo->pEncoder)
			pCapInfo->pGraph->RemoveFilter(pCapInfo->pEncoder);
		if (pCapInfo->pAudioCompressor)
			pCapInfo->pGraph->RemoveFilter(pCapInfo->pAudioCompressor);
		if (pCapInfo->pVideoCompressor)
			pCapInfo->pGraph->RemoveFilter(pCapInfo->pVideoCompressor);
		if (pCapInfo->pResync)
			pCapInfo->pGraph->RemoveFilter(pCapInfo->pResync);
		if (pCapInfo->pAudioCaptureFilter)
			pCapInfo->pGraph->RemoveFilter(pCapInfo->pAudioCaptureFilter);
	}

	//ZQ. Disconnect Dump Sink from BDA filter    
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIDEO_CAPTURE_MASK ))
	    BDAGraphDisconnectDumpSink( env, pCapInfo, pCapInfo->pGraph ); //actually it changes state only
   
	SAFE_RELEASE(pCapInfo->pCCSink);
    SAFE_RELEASE(pCapInfo->pSink);
    SAFE_RELEASE(pCapInfo->pEncoder);
    SAFE_RELEASE(pCapInfo->pAudioCompressor);
    SAFE_RELEASE(pCapInfo->pVideoCompressor);
	SAFE_RELEASE(pCapInfo->pResync);
    SAFE_RELEASE(pCapInfo->pAudioCaptureFilter);

   

	RELEASEENCODERMUTEX;
}


/*
 * Class:     sage_DShowCaptureDevice
 * Method:    getRecordedBytes0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowCaptureDevice_getRecordedBytes0
  (JNIEnv *env, jobject jo, jlong capInfo)
{
	if ( capInfo == NULL )
		return 0;

	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	if (pCapInfo->pSink)
	{
		IMpegMux* pMuxy = NULL;
		HRESULT hr = pCapInfo->pSink->QueryInterface(IID_IMpegMux, (void**)&pMuxy);
		if (FAILED(hr)) return -1;
		jlong rv;
		pMuxy->get_FileLength(&rv);
		SAFE_RELEASE(pMuxy);
		return rv;
	}
	return 0;
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    setAudioCaptureSource0
 * Signature: (JLjava/lang/String;II)V
 */
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_setAudioCaptureSource0
  (JNIEnv *env, jobject jo, jlong capInfo, jstring js, jint num, jint src)
{
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	const char* tempStr = env->GetStringUTFChars(js, NULL);
	strncpy(pCapInfo->audioCaptureFilterName, tempStr, sizeof(pCapInfo->audioCaptureFilterName) );
	env->ReleaseStringUTFChars(js, tempStr);
	pCapInfo->audioCaptureFilterNum = num;
	pCapInfo->audioCaptureFilterSrcIndex = src; //ZQ
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    setAudioProcessor0
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_setAudioProcessor0
  (JNIEnv *env, jobject jo, jlong capInfo, jstring js)
{
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	const char* tempStr = env->GetStringUTFChars(js, NULL);
	strncpy(pCapInfo->audioProcessorName, tempStr, sizeof(pCapInfo->audioProcessorName) );
	env->ReleaseStringUTFChars(js, tempStr);
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    setVideoProcessor0
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_DShowCaptureDevice_setVideoProcessor0
  (JNIEnv *env, jobject jo, jlong capInfo, jstring js)
{
	DShowCaptureInfo* pCapInfo = (DShowCaptureInfo*) capInfo;
	const char* tempStr = env->GetStringUTFChars(js, NULL);
	strncpy(pCapInfo->videoProcessorName, tempStr, sizeof(pCapInfo->videoProcessorName) );
	env->ReleaseStringUTFChars(js, tempStr);
}


//ZQ
// Given a BDA Receiver Component (usually a Video Capture filter name), searches for & connects to appropriate Tuner
void BDAGraphConnectFilter( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph )
{
	graphTools.SetLogOutput( env );
    HRESULT hr = S_OK;

/*
    slog((env, "BDAGraphConnectFilter() Entry: videoCaptureFilterName='%s' videoCaptureFilterNum=%d pVideoCaptureFilter=%p bdaCaptureFilterName='%s' bdaTunerFilterName='%s' pEncoder=%p filterState=%d pBDANetworkProvider=%p pBDATuner=%p pBDADemod=%p pBDACapture=%p TuningMode='%s' \r\n",
        pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, pCapInfo->pVideoCaptureFilter,
        pCapInfo->bdaCaptureFilterName, pCapInfo->bdaTunerFilterName, pCapInfo->pEncoder, pCapInfo->filterState,
        pCapInfo->pBDANetworkProvider, pCapInfo->pBDATuner, pCapInfo->pBDADemod, pCapInfo->pBDACapture, pCapInfo->TuningMode));
*/
    if ( pCapInfo->filterState >= 2 )  //is connected
    	return;

	slog( (env, "Connecting capture Filters. bdaCaptureFilterName='%s', videoCaptureFilterName='%s' videoCaptureFilterNum=%d\r\n", 
        pCapInfo->bdaCaptureFilterName, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
   
	TV_TYPE BDATVTypes[7]={ ATSC, DVBT, DVBC, DVBS, UNKNOWN, UNKNOWN };
	TV_TYPE BDATVType = UNKNOWN;
	BOOL    bIsNetworkPorvider = 0;
	int i=0, SearchNum = 0;
	IBaseFilter *piLastFilter;

	//if it's a virtua tuner, connect TSSplitter
	if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_VIRTUAL_TUNER_MASK ))
	{
		piLastFilter = pCapInfo->pBDACapture;
		goto loc_connect_tssplitter;
	}


	//if not specify Networktype, I have to search match one
	if ( pCapInfo->pTuner == NULL )
	{
		SearchNum = 8;
		i = 0;
		if ( !strcmp( pCapInfo->TuningMode, "Cable" ) && GetOSversion() >= 6 ) 
		{
			i = 1; //skip ATSC if it's Cable source
			slog( (env, "It's cable source, skip try ATSC\r\n" ) );
		}
		BDATVType = BDATVTypes[i%4];
		bIsNetworkPorvider = (i>=4);
		SetTVType( pCapInfo, BDATVType, bIsNetworkPorvider );
		slog( (env, "Network not specified, Try Network type %s\r\n", pCapInfo->tvType ) );
		if ( FAILED( hr = SetupBDATuningSpace( env, pCapInfo, pGraph ) ) )
			ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
	}
	//slog( (env,"BDA Capture ID:%d\r\n",  GetFilterDW1(env, pCapInfo->pBDACapture, PINDIR_INPUT ) ));

	//hard code Anysee tuner
	if ( !strncmp( pCapInfo->videoCaptureFilterName, "anysee BDA Digital Tuner(MCE DVB-" , 33 ) )
	{
		slog( (env, "Don't Support Anysee MCE filter '%s'. \r\n", pCapInfo->videoCaptureFilterName ) );
		ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
	} else
	if ( !strncmp( pCapInfo->videoCaptureFilterName, "anysee BDA Digital Tuner", 24 ) ) 
	{
		slog( (env,"Anysee hardcode graph building... %s. \r\n",  pCapInfo->videoCaptureFilterName) );
		if ( strstr( pCapInfo->videoCaptureFilterName, "DVB-T" ) )
			BDATVType = DVBT;
		else
		if ( strstr( pCapInfo->videoCaptureFilterName, "DVB-C" ) )
			BDATVType = DVBC;
		else
		if ( strstr( pCapInfo->videoCaptureFilterName, "DVB-S" ) )
			BDATVType = DVBS;
		else
		if ( strstr( pCapInfo->videoCaptureFilterName, "ATSC" ) )
			BDATVType = ATSC;

		ReleaseBDATuningSpace( env, pCapInfo );
		SetTVType( pCapInfo, BDATVType, FALSE );
		
		if ( FAILED( hr = SetupBDATuningSpace( env, pCapInfo, pGraph ) ) )
					ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);	

		ASSERT( pCapInfo->pBDATuner == NULL );
		pCapInfo->pBDATuner = pCapInfo->pBDACapture;
		strncpy( pCapInfo->bdaTunerFilterName, pCapInfo->bdaCaptureFilterName, sizeof(pCapInfo->bdaTunerFilterName) );
		pCapInfo->pBDACapture = NULL;
		pCapInfo->bdaCaptureFilterName[0] = 0x0;

		hr = FindFilterByName2(&pCapInfo->pBDACapture, KSCATEGORY_BDA_RECEIVER_COMPONENT,
					"anysee BDA Digital Capturer", pCapInfo->videoCaptureFilterNum, 
					pCapInfo->bdaCaptureFilterName,  sizeof( pCapInfo->bdaCaptureFilterName ) );
		if ( SUCCEEDED(hr) )
		{
			//pCapInfo->pBDACapture->AddRef();
			hr = pGraph->AddFilter( pCapInfo->pBDACapture, L"anysee BDA Digital Capturer" );
		}

		if ( FAILED(hr) )
		{
			slog( (env,"Failed loading filter %s for Anysee tuner. \r\n",  "anysee BDA Digital Capturer") );
			ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
		} else
		if ( FAILED(hr = graphTools.ConnectFilters(pGraph, pCapInfo->pBDANetworkProvider, pCapInfo->pBDATuner)) )
		{
			slog( (env,"Can't connect Network Provider to Anysee Tuner Filter %s->%s \r\n", pCapInfo->bdaTunerFilterName, pCapInfo->tvType ) );
			ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
		} else
		if ( SUCCEEDED(hr = graphTools.ConnectFilters( pGraph, pCapInfo->pBDATuner, pCapInfo->pBDACapture )) )
		{
			piLastFilter = pCapInfo->pBDACapture;
		} else
		{
			slog( (env,"Failed connecting tuner filter to capture filter %s for Anysee tuner. \r\n",  pCapInfo->bdaCaptureFilterName) );
			ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
		}
		slog( (env,"Anysee graph was built. \r\n",  pCapInfo->videoCaptureFilterName ) );

	} else

    // The WinTV-dualHD usb stick has one Capture and 2 Tuner devices, all at the same hardware_loc.
    // hardcode for both Hauppauge WinTV-dualHD tuner(s): usb device has '...ATSC Tuner' and '...ATSC Tuner 2'
    if (strstr(pCapInfo->videoCaptureFilterName, "Hauppauge WinTV-dualHD ATSC Tuner"))
    {
        slog((env, "Hardcode graph building for '%s' \r\n", pCapInfo->videoCaptureFilterName));
        BDATVType = ATSC;  // Tuning space is ATSC

        ReleaseBDATuningSpace(env, pCapInfo);
        SetTVType(pCapInfo, BDATVType, TRUE); // TRUE = use NetworkProvider

        if (FAILED(hr = SetupBDATuningSpace(env, pCapInfo, pGraph)))
            ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);

        if (pCapInfo->pBDANetworkProvider == NULL)
            slog((env, "Ooops: pCapInfo->pBDANetworkProvider is NULL, BDAVTVType=%d \r\n", BDATVType));

        ASSERT(pCapInfo->pBDATuner == NULL);

        // entered here with Tuner (not Capture) info; put it in correct structure member
        pCapInfo->pBDATuner = pCapInfo->pBDACapture;
        strncpy(pCapInfo->bdaTunerFilterName, pCapInfo->bdaCaptureFilterName, sizeof(pCapInfo->bdaTunerFilterName));
        slog((env, "Fixed: pCapInfo->bdaTunerFilterName is now='%s' \r\n", pCapInfo->bdaTunerFilterName));

        pCapInfo->pBDACapture = NULL;
        pCapInfo->bdaCaptureFilterName[0] = 0x0;

        hr = FindFilterByName2(&pCapInfo->pBDACapture, KSCATEGORY_BDA_RECEIVER_COMPONENT,
            "Hauppauge WinTV-dualHD TS Capture", pCapInfo->videoCaptureFilterNum, // FilterNum is same value for Capture & both Tuners on a given usb stick
            pCapInfo->bdaCaptureFilterName, sizeof(pCapInfo->bdaCaptureFilterName));
        if (SUCCEEDED(hr))
        {
            //pCapInfo->pBDACapture->AddRef();
            hr = pGraph->AddFilter(pCapInfo->pBDACapture, L"Hauppauge WinTV-dualHD TS Capture");
        }

        if (FAILED(hr))
        {
            slog((env, "Failed loading filter '%s' for WinTV-dualHD \r\n", "Hauppauge WinTV-dualHD TS Capture"));
            ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
        }
        else
        if (FAILED(hr = graphTools.ConnectFilters(pGraph, pCapInfo->pBDANetworkProvider, pCapInfo->pBDATuner)))   // problematic
        {
            slog((env, "Can't connect Network Provider to Hauppauge WinTV-dualHD Tuner Filter '%s'->'%s' \r\n",
                pCapInfo->bdaTunerFilterName, pCapInfo->tvType));
            slog((env, "pCapInfo->pBDANetworkProvider=%p, pCapInfo->pBDATuner=%p \r\n",
                pCapInfo->pBDANetworkProvider, pCapInfo->pBDATuner));
            ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
        }
        else
        if (SUCCEEDED(hr = graphTools.ConnectFilters(pGraph, pCapInfo->pBDATuner, pCapInfo->pBDACapture)))
        {
            piLastFilter = pCapInfo->pBDACapture;
        }
        else
        {
            slog((env, "Failed connecting tuner filter to capture filter '%s' for Hauppauge WinTV-dualHD \r\n", pCapInfo->bdaCaptureFilterName));
            ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
        }
        slog((env, "Hauppauge WinTV-dualHD graph was built. \r\n"));

    }
    else

	do {
		BOOL bFoundCapture = FALSE;
		piLastFilter = NULL;
		if ( ( strstr( pCapInfo->videoCaptureFilterName, "Silicondust HDHomeRun Tuner" ) != NULL ) &&
			SUCCEEDED(hr = graphTools.ConnectFilters( pGraph, pCapInfo->pBDANetworkProvider, pCapInfo->pBDACapture )) )
		{  //try directly connect Network to capture bypass Tuner filter
			piLastFilter = pCapInfo->pBDACapture;
			bFoundCapture = TRUE;
			slog( (env,"Connected Network Provider to Capture Filter (%s); directly connect Network to capture bypass Tuner filter. \r\n", pCapInfo->tvType ) );
			break;
		} 

		//search tuner that is able to connect to capture.
		DirectShowSystemDeviceEnumerator enumerator(KSCATEGORY_BDA_NETWORK_TUNER);
		DirectShowSystemDevice* pDemodDevice;
		DEVICE_DRV_INF  BDADevDrvInfo;
		DEVICE_DRV_INF  VideoCaptureDrvInfo;
		GetDeviceInfo( pCapInfo->bdaCaptureFilterName, &VideoCaptureDrvInfo );

		while (hr = enumerator.Next(&pDemodDevice) == S_OK)
		{
			if (FAILED(hr = graphTools.AddFilterByDevicePath2( pGraph, &pCapInfo->pBDATuner,
										pDemodDevice->strDevicePath, pDemodDevice->strFriendlyName )))
			{
				LPSTR pName=NULL;
				FilterGraphTools::strCopyW2A( pName,  pDemodDevice->strFriendlyName);
				slog( (env,"Failed to add BDA tuner '%s'\r\n", pName ) );
				delete pName;
				delete pDemodDevice;
				pDemodDevice = NULL; 
				pCapInfo->pBDATuner = NULL;
				pCapInfo->bdaTunerFilterName[0] = 0x0;
				continue; //search next one
			}

			LPSTR pName=NULL, pPath=NULL;
			FilterGraphTools::strCopyW2A( pName,  pDemodDevice->strFriendlyName);
			FilterGraphTools::strCopyW2A( pPath,  pDemodDevice->strDevicePath);
			slog( (env,"try connect BDA tuner '%s' (%s) to %s\r\n", pName, pPath, pCapInfo->tvType) );
			GetDeviceInfo( pPath, &BDADevDrvInfo );
			strncpy( pCapInfo->bdaTunerFilterName, pName, sizeof(pCapInfo->bdaTunerFilterName) );
			delete pName;
			delete pPath;
			slog( (env,"checking mfr vendor, '%s' '%s' (class:%d, %d)\r\n", BDADevDrvInfo.vendor_id, VideoCaptureDrvInfo.vendor_id, 
				  BDADevDrvInfo.device_class, VideoCaptureDrvInfo.device_class) ); 

			if ( strncmp( VideoCaptureDrvInfo.hardware_loc, "usb", 3 ) &&
				 BDADevDrvInfo.vendor_id[0] && VideoCaptureDrvInfo.vendor_id[0] &&
				 stricmp( BDADevDrvInfo.vendor_id, VideoCaptureDrvInfo.vendor_id) ) 
			{   //OnAir-GT is able to connect to AVerMedia H826 Hybrid
				//WinFast DTV1000S tuner report the same pci slot as a hauppauge tuner, and is able to connect haauppage capture filter, 
				// we use vendor id to check. //ZQ
				slog( (env,"Is not the same mfr vendor, skip '%s' '%s' \r\n", BDADevDrvInfo.vendor_id, VideoCaptureDrvInfo.vendor_id ) );
			} else 
			if ( strstr( pCapInfo->videoCaptureFilterName, "FusionHDTV") != NULL && BDADevDrvInfo.state > 0 && VideoCaptureDrvInfo.state > 0 &&
				 strcmp( BDADevDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_loc ) )
			{   
				//FusionHDTV 7 RT (PCIE), one tuner can connect to another capture on a different card, 
				// so we check if capture and tuner is on the same card.
				////FusionHDTV 5 tuner dosn't have location
				slog( (env, "FusionHDTV's capture and tuner filter isn't on the same card, try next tuner. state:%d loc1:'%s' loc2:'%s' guid1:%s guid2:%s.\r\n",
							BDADevDrvInfo.state, BDADevDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_loc,
							BDADevDrvInfo.inst_guid2, VideoCaptureDrvInfo.inst_guid2 ) );
			} else
			if FAILED(hr = graphTools.ConnectFilters(pGraph, pCapInfo->pBDANetworkProvider, pCapInfo->pBDATuner))
			{
				slog( (env,"Can't connect Network Provider to Tuner Filter (%s) \r\n", pCapInfo->tvType ) );
			} else
			if ( SUCCEEDED(hr = graphTools.ConnectFilters( pGraph, pCapInfo->pBDATuner, pCapInfo->pBDACapture )) )
			{
				piLastFilter = pCapInfo->pBDACapture;
				bFoundCapture = TRUE;
				break;
			} else
			{
				int ret = CheckFilterMedium( pCapInfo->pBDATuner, pCapInfo->pBDACapture );
				//ZQ. DigitalNow TinyTwin (AF9015 chipset), dual tuner shares the same REGPINMEDIUM.
				//  Filters medium matched {4747B320-62CE-11CF-A5D6-28DB04C10000}-0x0.  vendor_id: 1b80, 13d3(ITETech)
				//  filter name "AF9015 BDA Filter"

				if ( !strcmp( BDADevDrvInfo.vendor_id, "1b80" ) || strstr( pCapInfo->videoCaptureFilterName, "AF9015 BDA") != NULL ||
					 !strcmp( BDADevDrvInfo.vendor_id, "0ccd" ) || !strcmp( BDADevDrvInfo.vendor_id, "0413" ) )	
					 ret = -1;

				if ( ret == 1 )
				{
					slog( (env, "Redundant DBA capture filter, drop it (%s).\r\n", BDADevDrvInfo.full_name  )) ;
					piLastFilter = pCapInfo->pBDATuner;
					bFoundCapture = TRUE;
					break;
				} else
				if ( ret == 0 )
				{
					slog( (env, "Filter Medium isn't matched (%s).\r\n", BDADevDrvInfo.full_name  )) ;
				} else
				if (capMask(pCapInfo->captureConfig, sage_DShowCaptureDevice_BDA_NETWORK_TUNER_MASK ))
				{   
					CComPtr <IPin> pCapturePin;
					//KWorld DVT-2 USB stick, two USB tuners share a usb id.
					if ( SUCCEEDED(hr = graphTools.FindPinByMediaType( pCapInfo->pBDATuner, MEDIATYPE_Stream, KSDATAFORMAT_SUBTYPE_BDA_MPEG2_TRANSPORT, &pCapturePin, REQUESTED_PINDIR_OUTPUT) ) ||
						 SUCCEEDED(hr = graphTools.FindPinByMediaType( pCapInfo->pBDATuner, MEDIATYPE_Stream, MEDIASUBTYPE_MPEG2_TRANSPORT, &pCapturePin, REQUESTED_PINDIR_OUTPUT) ) )  // HDHR doesn't follow standard
					{
						if ( !strcmp( BDADevDrvInfo.full_name, pCapInfo->bdaCaptureFilterName ) )
						{
							slog( (env, "Redundant DBA capture filter, drop it (%s).\r\n", BDADevDrvInfo.full_name  )) ;
							piLastFilter = pCapInfo->pBDATuner;
							bFoundCapture = TRUE;
							break;
						} 
					}
				} else
				{	
					//if capture tuner has a antenna input pin, it's a redundant filter, we drop it
					//if it's tuner filter has MPEG2 TS ouput pin, and sharing a device with capture filter, it's a redundant filter, we drop it
					//Hauppauge WinTV-NOVA-USB2 don't have/need capture filter, tuner output stream
					CComPtr <IPin> pCapturePin; //
					if ( SUCCEEDED(hr = graphTools.FindPinByMediaType( pCapInfo->pBDACapture, KSDATAFORMAT_TYPE_BDA_ANTENNA, GUID_NULL, &pCapturePin, REQUESTED_PINDIR_INPUT) ) )
					{
						if ( BDADevDrvInfo.state > 0 && VideoCaptureDrvInfo.state > 0 && 
							!strcmp( BDADevDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_loc ) 
							&& strlen( BDADevDrvInfo.hardware_loc) )
						{
							slog( (env, "Drop DBA network tuner (Source Fileter) (redundant filter).\r\n") );
							piLastFilter = pCapInfo->pBDATuner;
							bFoundCapture = TRUE;
							break;
						} else
						{
							slog( (env,"Tuner not sharing the same device with Capture, try next one. (%s) state:%d loc1:%s loc2:%s.\r\n", 
								pCapInfo->tvType, BDADevDrvInfo.state, BDADevDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_loc  ) );
						}
					}

					{
						//Hauppauge WinTV-NOVA-USB2 don't have/need capture filter, tuner output stream
						CComPtr <IPin> pCapturePin;
						if SUCCEEDED(hr = graphTools.FindPinByMediaType( pCapInfo->pBDATuner, MEDIATYPE_Stream, KSDATAFORMAT_SUBTYPE_BDA_MPEG2_TRANSPORT, &pCapturePin, REQUESTED_PINDIR_OUTPUT) )
						{
							if ( BDADevDrvInfo.state > 0 && VideoCaptureDrvInfo.state > 0 && 
								 (!strcmp( BDADevDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_loc ) && strlen( BDADevDrvInfo.hardware_loc)) )
								 //(!strcmp( BDADevDrvInfo.inst_guid2,   VideoCaptureDrvInfo.inst_guid2 ) && strlen( BDADevDrvInfo.inst_guid2 ) || strlen( BDADevDrvInfo.inst_guid2 ) == 0  ) )
							{
								slog( (env, "drop duplicated DBA capture, as BDATuner has MPEG2 TS pin state:%d loc1:'%s' loc2:'%s' guid1:%s guid2:%s.\r\n",
									BDADevDrvInfo.state, BDADevDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_loc,
									BDADevDrvInfo.inst_guid2, VideoCaptureDrvInfo.inst_guid2 ) );
								piLastFilter = pCapInfo->pBDATuner;
								bFoundCapture = TRUE;
								break;
							} else
							{
								slog( (env,"found stream pin, but not matching duplicated capture (%s) state:%d loc1:'%s' loc2:'%s' guid1:%s guid2:%s.\r\n", 
									pCapInfo->tvType, BDADevDrvInfo.state, BDADevDrvInfo.hardware_loc, VideoCaptureDrvInfo.hardware_loc,
									BDADevDrvInfo.inst_guid2, VideoCaptureDrvInfo.inst_guid2 ) );
							}
						} else
						{
							slog( (env,"Didn't find TS pin in BDA tuner (%s).\r\n", pCapInfo->tvType ) );
						}
					}

				}

				{  //check Capture tuner input pin media type, if it's not a antenna input 
					AM_MEDIA_TYPE pMediaType;
					if ( SUCCEEDED(hr = graphTools.GetPinByMediaType( pCapInfo->pBDACapture, 0, REQUESTED_PINDIR_INPUT, &pMediaType ) ) )
					{
						//after finding new id, put it into prior mediatype check
						LPOLESTR major_clsid;
						LPSTR   pName=NULL;
						StringFromCLSID( pMediaType.majortype, &major_clsid );
						FilterGraphTools::strCopyW2A( pName, major_clsid );
						slog( (env,"Capture filter input pin mediatype clsid (%s).\r\n",pName ) );
						if ( pName ) delete pName;
						CoTaskMemFree(major_clsid);
					} else
					{
						slog( (env, "Falied to get BDATuner InputPin mediatype clsid (hr=0x%x) 0x%x.\r\n", hr, pCapInfo->pBDACapture ) );
						//DTV-DVB Mantis DVBC BDA Receiver doesn't give us mediatype, go further
					}
				}

				slog( (env,"Can't connect Tuner to Capture Filter (%s).\r\n", pCapInfo->tvType ) );

			}

			hr = graphTools.DisconnectOutputPin(pGraph, pCapInfo->pBDANetworkProvider );
			graphTools.DisconnectFilter(pGraph, pCapInfo->pBDATuner );
			pGraph->RemoveFilter( pCapInfo->pBDATuner );
			pCapInfo->pBDATuner->Release();
			delete pDemodDevice;
			pDemodDevice = NULL; 
			pCapInfo->pBDATuner = NULL;
			pCapInfo->bdaTunerFilterName[0] = 0x0;
		}

		if ( pDemodDevice )
			delete pDemodDevice;

		if ( piLastFilter != NULL  )
			break;
		
		//try next TV Type (Network)
		i++;
		if ( i<SearchNum )
		{
			ReleaseBDATuningSpace( env, pCapInfo );
			BDATVType = BDATVTypes[i%4];
			bIsNetworkPorvider = (i>=4);
			if ( ATSC == BDATVType && bIsNetworkPorvider )
			{
				if ( !stricmp( pCapInfo->TuningMode, "Cable" ) )
					BDATVType = QAM;
				else
					BDATVType = ATSC;

				SetTVType( pCapInfo, BDATVType, TRUE );
				if ( FAILED( hr = SetupBDATuningSpace( env, pCapInfo, pGraph ) ) )
					ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
			} else
			{
				SetTVType( pCapInfo, BDATVType, FALSE );
				if ( FAILED( hr = SetupBDATuningSpace( env, pCapInfo, pGraph ) ) )
					ENCEXCEPT_RET(sage_EncodingException_CAPTURE_DEVICE_INSTALL);
			}
			slog( (env, "Try Network type %s (%s)\r\n", pCapInfo->tvType, pCapInfo->TuningMode ) );
		}
		
	} while( BDATVType != UNKNOWN );

    if ( piLastFilter == NULL  )
	{
		hr = E_FAIL;
		slog( (env,"not found BDA connectable tuner for DBA capture \r\n") );
		ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
	} 

	loc_connect_tssplitter:

	CComPtr <IPin> pCapturePin;

	if ( pCapInfo->pPrePlugin == NULL )
	{
		if FAILED(hr = graphTools.ConnectFilters(pGraph, piLastFilter, pCapInfo->pSplitter ))
		{
			slog( (env,"Failed to connect Splitter Filter to tuner Filter hr=0x%x %x %x \r\n", hr, piLastFilter, pCapInfo->pSplitter ) );
			IPin* pPinOut = FindPin(piLastFilter, PINDIR_OUTPUT, &MEDIATYPE_Stream, NULL );
			IPin* pPinIn = FindPin(pCapInfo->pSplitter, PINDIR_INPUT, NULL, NULL );
			hr = pCapInfo->pGraph->ConnectDirect(pPinOut, pPinIn, NULL );
			slog( (env,"Force directly connect Splitter Filter to Capture Filter hr=0x%x 0x%x-0x%x (pin:0x%x-0x%x)\r\n", 
							hr, piLastFilter, pCapInfo->pSplitter, pPinOut, pPinIn ) );
			SAFE_RELEASE( pPinOut );
			SAFE_RELEASE( pPinIn );
			if FAILED( hr )
				ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		}
	} else {

		if FAILED(hr = graphTools.ConnectFilters(pGraph, piLastFilter, pCapInfo->pPrePlugin ))
		{
			slog( (env,"Failed to connect Splitter Filter to Plugin Filter, skip Plugin. hr=0x%x %x %x \r\n", hr, piLastFilter, pCapInfo->pSplitter ) );
		} else
		{
			piLastFilter = pCapInfo->pPrePlugin;
			slog( (env,"Plugin Filter is connected. hr=0x%x %x %x \r\n", hr, piLastFilter, pCapInfo->pSplitter ) );
		}
		if FAILED(hr = graphTools.ConnectFilters(pGraph, piLastFilter, pCapInfo->pSplitter ))
		{
			slog( (env,"Failed to connect Splitter Filter to tuner/plgin Filter hr=0x%x %x %x \r\n", hr, piLastFilter, pCapInfo->pSplitter ) );
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		} 
	}
	
	if FAILED(hr = graphTools.FindPin(pCapInfo->pSplitter, L"PassThrusPin", &pCapturePin, REQUESTED_PINDIR_OUTPUT))
	{
		slog( (env,"Failed to find Tranport Stream pin on capture TSSplitter\r\n") );
		ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
	}

	if ( pCapInfo->pBDADemux )
	{
		CComPtr <IPin> pDemuxPin;
		if (FAILED(hr = graphTools.FindPin( pCapInfo->pBDADemux, L"MPEG-2 Stream", &pDemuxPin.p, REQUESTED_PINDIR_INPUT)))
		{
			slog( (env,"Failed to get input pin on Demux\r\n") );
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		}

		if (FAILED(hr = pGraph->ConnectDirect( pCapturePin, pDemuxPin, NULL )))
		{
			slog( (env,"Failed to connect Capture filter to BDA Demux\r\n") );
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		}
		
		if (FAILED(hr = graphTools.ConnectFilters( pGraph, pCapInfo->pBDADemux, pCapInfo->pBDATIF )))
		{
			slog( (env, "Failed to connect to BDA Demux to TIF hr=0x%x %x %x\n", hr, pCapInfo->pBDADemux, pCapInfo->pBDATIF ) );
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		}

		if (FAILED(hr = graphTools.ConnectFilters( pGraph, pCapInfo->pBDADemux, pCapInfo->pBDASectionTbl )))
		{
			slog( (env, "Failed to connect to BDA Demux to Sections and Tables hr=0x%x %x %x\n", hr, pCapInfo->pBDADemux, pCapInfo->pBDASectionTbl) );
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		}
		ClearPIDMap( env, pCapInfo );
	}

	ITSParser2 *pTSParser = NULL;
	hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
	if ( FAILED( hr ) )
	{
		slog((env, "digital tv TS Splitter epg interface failed\r\n" ));
	} else
	{
		pTSParser->SetEPGDump( (void*)EPG_Dumper, pCapInfo );
		pTSParser->SetAVInfDump( (void*)AVInf_Dumper, pCapInfo );
		pTSParser->SetProgramDataDump( (void*)Program_Dumper, pCapInfo );
		//ZQ TO DO
		//pTSParser->SetProgramDataDump( (void*)OnCamPMT, pCapInfo );
		//slog( ( "CAM:Setup PMT dump CapInfo:0x%x\r\n", pCapInfo ) );
		//if ( strstr(  pCapInfo->videoCaptureFilterName, "Silicondust HDHomeRun" ) )
		//{
		//	pTSParser->SetPIDsDump( (void*)PIDs_Dumper, pCapInfo );
		//	slog( ( "CAM:Setup program filter dump CapInfo:0x%x\r\n", pCapInfo ) );
		//} 
	    pCapInfo->filterState = 2;
		slog( (env, "Capture filters are connected\r\n") );
	}

	SAFE_RELEASE(pTSParser);

	pCapInfo->dwTuneState = 0; 

}

void ClearPIDMap(  JNIEnv* env, DShowCaptureInfo *pCapInfo )
{
	HRESULT hr;
	CComPtr <IPin> pPin;
	if (  pCapInfo->pBDADemux == NULL ) return;

	if ( SUCCEEDED(hr = graphTools.FindPin( pCapInfo->pBDADemux, L"1", &pPin.p, REQUESTED_PINDIR_OUTPUT )) )
	{
		IMPEG2PIDMap *pIPidMap = NULL;
		hr = pPin->QueryInterface(IID_IMPEG2PIDMap, (void**)&pIPidMap);
		if (SUCCEEDED(hr))
		{
			ULONG i,pid_total = 0;
			ULONG* unmap_pids=NULL; 

			IEnumPIDMap *pIEnumPIDMap;
			if (SUCCEEDED(pIPidMap->EnumPIDMap(&pIEnumPIDMap)))
			{
				ULONG num;
				PID_MAP pPidMap;
				//count pid;
				while( pIEnumPIDMap->Next(1, &pPidMap, &num) == S_OK ) pid_total++;
				unmap_pids = new ULONG[pid_total+1];

				//get pids
				i = 0;
				pIEnumPIDMap->Reset();
				while(pIEnumPIDMap->Next(1, &pPidMap, &num) == S_OK)
				{
					unmap_pids[i++] = pPidMap.ulPID;
				}

				SAFE_RELEASE( pIEnumPIDMap );

				//unmap pid
				if ( FAILED( hr = pIPidMap->UnmapPID( pid_total, unmap_pids ) ) )
				{
					//slog( (env, "Failed to UnmapPID on TIF hr=0x%x \r\n", hr ) );
				} else
				{
					slog( (env, "UnmapPID on TIF pin total:%d \r\n", pid_total ) );
				}
				delete unmap_pids;

			} else
				slog( (env, "Failed to get TIF EnumPIDMap hr=0x%x \r\n", hr ) );

			SAFE_RELEASE( pIPidMap );
		}
		else
		{
			slog( (env, "Failed to get IMPEG2PIDMap on Pin-1 to map pid hr=0x%x \r\n", hr ) );
		}
	} else
	{
		slog( (env, "Failed to get Pin-1 to map pid hr=0x%x \r\n", hr ) );
	}

}

void SetupCAM( JNIEnv* env, DShowCaptureInfo *pCapInfo )
{
	HRESULT hr;
	if ( pCapInfo == NULL ) return ;

	//setup CAM/CI for DVB-C DVB-S, DVB-T
	TV_TYPE type = GetTVType( pCapInfo ); 
	if ( type != DVBC && type != DVBS && type != DVBT )
		return;

	if ( pCapInfo->pCamCtrlData != NULL && !IsCAMValid( env, pCapInfo ) )
		return;

	if ( IsCAMInitialized( env, pCapInfo ) )
		return ;

	DEVICE_DRV_INF  VideoCaptureDrvInfo={0};
	hr = GetDeviceInfo( pCapInfo->bdaCaptureFilterName, &VideoCaptureDrvInfo );
	if ( hr != S_OK || VideoCaptureDrvInfo.state == 0 )
	{
		slog( (env,"CAM:Can't read capture drv info for CAM %s\r\n", pCapInfo->bdaCaptureFilterName ) );
		return;
	}
	
	slog( (env,"CAM:Got capture drv info for CAM vendorID:'%s' mfg:'%s' %s-%d\r\n", 
		                VideoCaptureDrvInfo.vendor_id, VideoCaptureDrvInfo.mfg,
		                pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

	if ( !strnicmp( VideoCaptureDrvInfo.mfg, FIREDTV_MFG, strlen(FIREDTV_MFG) ) )
	{
		InitialCAM( env, pCapInfo, FIREDTV_CAM );
		OpenCAM( env, pCapInfo );

		//enable TSSplitter PassThrought data to MSFT Demux for FireDTV.
		ITSParser2 *pTSParser = NULL;
		hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
		if ( FAILED( hr ) )
		{
			slog((env, "get TS Splitter interface failed for enable passthrus \r\n" ));
		} else
		{
			pTSParser->PassThrusEnable( TRUE );
			slog((env, "TS Splitter passThrus pin is enabled.\r\n" ));
		}

		SAFE_RELEASE(pTSParser);

	} else
	if ( !strnicmp( VideoCaptureDrvInfo.vendor_id, ANYSEE_VENDOR_ID, 4 ) ||
		!strnicmp( VideoCaptureDrvInfo.mfg, ANYSEE_MFG, strlen(ANYSEE_MFG) ) )
	{
		InitialCAM( env, pCapInfo, ANYSEE_CAM );
		OpenCAM( env, pCapInfo );

		//enable TSSplitter PassThrought data to MSFT Demux for Anysee.
		ITSParser2 *pTSParser = NULL;
		hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
		if ( FAILED( hr ) )
		{
			slog((env, "get TS Splitter interface failed for enable passthrus \r\n" ));
		} else
		{
			pTSParser->PassThrusEnable( TRUE );
			slog((env, "TS Splitter passThrus pin is enabled.\r\n" ));
		}

		SAFE_RELEASE(pTSParser);

	} else
	if ( !strnicmp( VideoCaptureDrvInfo.vendor_id, TECHNOTREND_VENDOR_ID, 4 ) ||
		 !strnicmp( VideoCaptureDrvInfo.mfg, TECHNOTREND_MFG, strlen(TECHNOTREND_MFG) )	)
	{
		InitialCAM( env, pCapInfo, TECHNOTREND_CAM );
		OpenCAM( env, pCapInfo );
	} else
	{
		InitialCAM( env, pCapInfo, UNKNOWN_CAM );
		slog( (env,"CAM:unknow vendor id info for CAM vendor %s %s\r\n", 
			            VideoCaptureDrvInfo.vendor_id, VideoCaptureDrvInfo.mfg ) );
	}
}
void TearDownCAM( JNIEnv* env, DShowCaptureInfo *pCapInfo )
{
	TV_TYPE type = GetTVType( pCapInfo ); 
	if ( type != DVBC && type != DVBS )
		return;

	ReleaseCAM( env, pCapInfo );
	CloseCAM( env, pCapInfo );
}

void BDAGraphConnectDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph )
{
	if ( pCapInfo->pSink != NULL && pCapInfo->filterState == 2 )
	{
		HRESULT hr;
		if FAILED(hr = graphTools.ConnectFilters(pGraph, pCapInfo->pSplitter, pCapInfo->pSink ))
		{
			slog( (env,"Failed to connect Splitter Filter to Dump Filter\r\n") );
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		}
		pCapInfo->filterState = 3;
	}
}

void BDAGraphDisconnectDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph )
{
	if ( pCapInfo->pSink != NULL && pCapInfo->filterState >= 3 )
	{
		HRESULT hr;
		if FAILED(hr = graphTools.DisconnectFilter(pGraph, pCapInfo->pSink ))
		{
			slog( (env,"Failed to disconnect Splitter Filter to Dump Filter\n") );
			ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		}

		pCapInfo->filterState = 2;
	}
}
//static IBaseFilter* pCapInfo->pDebugSink = NULL;
static char DebugDumpTsFile[256]={0};
int  DebugFileSourceEnable = 0;
static int  DebugRawDumpEnabled = 0;

void BDAGraphConnectDebugRawDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph  )
{
	HRESULT hr;

	if ( pCapInfo->pDebugSink != NULL )
		return;

	HKEY hregkey;
	int ret = -1;
	char  dump_path[256]={0};
	long  dump_size = 0;
	WCHAR *pUpPin = NULL, *pDownPin = NULL;
	
	if ( DebugRawDumpEnabled && pCapInfo->pDebugSink != NULL )
		return;

	char* regkey = "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\TSSplitter";
	if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, regkey, 0, KEY_READ, &hregkey) == ERROR_SUCCESS)
	{
			DWORD hType;
			DWORD hSize = sizeof(dump_path);
			ret = RegQueryValueEx( hregkey, "DumpFile", 0, &hType, (LPBYTE)dump_path, &hSize);
			if ( ret )	dump_path[0] = 0;
			
			hSize = sizeof( dump_size );
			ret = RegQueryValueEx( hregkey, "DumpSize", 0, &hType, (LPBYTE)&dump_size, &hSize);

			RegCloseKey( hregkey );
	}

	if ( dump_size == 0 )
		return ;

	DebugRawDumpEnabled = 1;

	if ( ret )
	{
		slog( (env,"Failed to read splitter dump file path from registry (%s, DumpFile).\r\n", regkey ) );
		return;
	}

	slog( (env,"DumpFile is enabled (%s, size:%d).\r\n", dump_path, dump_size ) );

	if ( pCapInfo->pDebugSink == NULL )
	{
		hr = CoCreateInstance(CLSID_MPEG2Dump, NULL, CLSCTX_INPROC_SERVER,
				IID_IBaseFilter, (void**)&(pCapInfo->pDebugSink));
		ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		hr = pCapInfo->pGraph->AddFilter(pCapInfo->pDebugSink, L"Debug TS Dump");
		ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
		if ( pCapInfo->pSplitter )
		{
			if ( FAILED(hr = graphTools.ConnectFilters(pGraph, pCapInfo->pSplitter, L"DumpPin",  pCapInfo->pDebugSink, L"Input"  )) )
			{
				slog( (env,"Failed to connect Splitter Filter DumpPin to Debug Dump Filter\r\n") );
				ENCEXCEPT_RET(sage_EncodingException_SAGETV_INSTALL);
			}

			ITSParser2 *pTSParser = NULL;
			hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
			if ( FAILED( hr ) )
			{
				slog((env, "digital tv query TS Splitter interface failed\r\n" ));
			} else
			{
				pTSParser->SetDumpSize( dump_size*1024*1024 );
			}
			SAFE_RELEASE(pTSParser);
			strncpy( DebugDumpTsFile, dump_path, sizeof(DebugDumpTsFile) );
		}

	}

	//WCHAR *pPathName = NULL;
	//char filename[156+32];
	//SPRINTF( filename, sizeof(filename), dump_path, "start" );
	//graphTools.strCopyA2W( pPathName, filename );

	//IFileSinkFilter* pfsf = NULL;
	//if ( pCapInfo->pDebugSink != NULL ) 
	//{
	//	pCapInfo->pDebugSink->QueryInterface(IID_IFileSinkFilter, (void**)&pfsf);
	//	if ( pPathName[0] )
	//	{
	//		hr = pfsf->SetFileName( pPathName, NULL );
	//		ENCEXCEPT_RET(sage_EncodingException_FILESYSTEM);
	//	}
	//	SAFE_RELEASE(pfsf);
	//}
	//if ( pPathName != NULL ) delete pPathName;


}

void BDAGraphSetDebugRawDumpFileName( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph, char* Channel  )
{
	if ( !DebugRawDumpEnabled || DebugDumpTsFile[0] == 0x0 || pCapInfo->pDebugSink == NULL )
		return;

	HRESULT hr = S_OK;
	IFileSinkFilter* pfsf = NULL;
	pCapInfo->pDebugSink->QueryInterface(IID_IFileSinkFilter, (void**)&pfsf);
	char filename[256+32];
	WCHAR *pPathName = NULL;

	if ( Channel == NULL || Channel[0] == 0x0 )
		strncpy( filename, DebugDumpTsFile, sizeof(filename) );
	else
		SPRINTF( filename, sizeof(filename), DebugDumpTsFile, Channel );
	graphTools.strCopyA2W( pPathName, filename );

	hr = pfsf->SetFileName( pPathName, NULL );
	ENCEXCEPT_RET(sage_EncodingException_FILESYSTEM);
	SAFE_RELEASE(pfsf);
	slog( ("DumpFile setup %s hr:%x. \r\n", filename, hr ));
	if ( pPathName != NULL ) delete pPathName;
}

//ZQ Qian's private test filter
// {9A80E198-3BBA-4821-B18B-21BB496F80F8}
DEFINE_GUID(CLSID_PushTsSource, 
0x9a80e198, 0x3bba, 0x4821, 0xb1, 0x8b, 0x21, 0xbb, 0x49, 0x6f, 0x80, 0xf8);

void BDAGraphSetDebugFileSource( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph  )
{
	char  file_path[256]={0};
	long  file_size = 0;
	int   ret = -1;
	HKEY hregkey;


	char* regkey = "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\TSSplitter";
	if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, regkey, 0, KEY_READ, &hregkey) == ERROR_SUCCESS)
	{
		DWORD hType;
		DWORD hSize = sizeof(file_path);
		ret = RegQueryValueEx( hregkey, "DebugFileSource", 0, &hType, (LPBYTE)file_path, &hSize);
		if ( ret )	file_path[0] = 0;
		
		hSize = sizeof( DebugFileSourceEnable );
		ret = RegQueryValueEx( hregkey, "DebugFileSourceEnable", 0, &hType, (LPBYTE)&DebugFileSourceEnable, &hSize);
		if ( ret ) DebugFileSourceEnable = 0;

		RegCloseKey( hregkey );
	}

	HRESULT hr = S_OK;

	if ( DebugFileSourceEnable == 0 )
	{
		file_path[0] = 0;
		return;
	}

	slog( (env,"DebugFileSource is Enabled %d %s\r\n", DebugFileSourceEnable, file_path) );

	if ( DebugFileSourceEnable == 0x2 && file_path[0] )
	{
		if ( pCapInfo->pDebugSrcSink == NULL )
		{

			hr = CoCreateInstance(CLSID_PushTsSource, NULL, CLSCTX_INPROC_SERVER,
					IID_IBaseFilter, (void**)&(pCapInfo->pDebugSrcSink));
			if ( pCapInfo->pDebugSrcSink )
			{
				hr = pCapInfo->pGraph->AddFilter(pCapInfo->pDebugSrcSink, L"Debug TS Source");
				IFileSinkFilter* pfsf = NULL;
				if ( pCapInfo->pDebugSrcSink != NULL ) 
				{
					pCapInfo->pDebugSrcSink->QueryInterface( IID_IFileSinkFilter, (void**)&pfsf );
					LPWSTR pPathName = NULL;
					graphTools.strCopyA2W( pPathName, file_path );
					hr = pfsf->SetFileName( pPathName, NULL );
					if ( pPathName ) delete pPathName;
					SAFE_RELEASE(pfsf);

					if( !FAILED(hr) && pCapInfo->pPrePlugin == NULL )
					{
						graphTools.DisconnectInputPin( pGraph, pCapInfo->pSplitter );
						graphTools.DisconnectOutputPin( pGraph, pCapInfo->pSplitter );
						graphTools.ConnectFilters( pGraph, pCapInfo->pDebugSrcSink, 
																pCapInfo->pSplitter );
					} else
					{
						graphTools.DisconnectInputPin( pGraph, pCapInfo->pPrePlugin);
						graphTools.DisconnectOutputPin( pGraph, pCapInfo->pPrePlugin );
						graphTools.ConnectFilters( pGraph, pCapInfo->pDebugSrcSink, 
																 pCapInfo->pPrePlugin );
					}

					slog( (env,"Push filter is inserted\r\n" ) );

				}
			}
		}


	} else
	{
		ITSParser2 *pTSParser = NULL;
		hr = pCapInfo->pSplitter->QueryInterface(IID_ITSParser2, (void**)&pTSParser);
		if ( FAILED( hr ) )
		{
			slog((env, "digital tv TS Splitter debug file src interface failed\r\n" ));
		} else
		{
			pTSParser->SetDebugSourceFile( file_path );
		}
		SAFE_RELEASE(pTSParser);
	}
	
}

void ClearUpDebugFileSource( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph )
{
	if ( pCapInfo->pDebugSrcSink == NULL ) 
		return ;
	graphTools.DisconnectFilter( pCapInfo->pGraph, pCapInfo->pBDATuner);
	graphTools.RemoveFilter( pCapInfo->pGraph, pCapInfo->pBDATuner);
    SAFE_RELEASE(pCapInfo->pBDATuner);
    SAFE_RELEASE(pCapInfo->pTuningSpace);
	graphTools.DisconnectFilter( pCapInfo->pGraph, pCapInfo->pBDANetworkProvider );
	graphTools.RemoveFilter( pCapInfo->pGraph, pCapInfo->pBDANetworkProvider );
    SAFE_RELEASE(pCapInfo->pBDANetworkProvider);
}


/////////////////////////////////////////////////////////////////////
void debug_pin( IBaseFilter *pFilter )
{
   IEnumPins* pEnum = NULL;
    HRESULT hr = pFilter->EnumPins(&pEnum);
    ULONG ulFound;
    IPin *pPin = NULL;
    hr = E_FAIL;

    while(S_OK == pEnum->Next(1, &pPin, &ulFound))
    {
    		IEnumMediaTypes *mtEnum = NULL;
			hr = pPin->EnumMediaTypes(&mtEnum);
			HRESULT hr2;
			AM_MEDIA_TYPE *pMT = NULL;
			if (SUCCEEDED(hr))
			{
				while (S_OK == (hr2 = mtEnum->Next(1, &pMT, NULL)))
				{
					DeleteMediaType(pMT);
				}
			}
			SAFE_RELEASE(mtEnum);

        pPin->Release();
    } 
    SAFE_RELEASE(pEnum);
/////////////////////////////////////////////////////
}


//void BDAGraphDisconnectDebugRawDumpSink( JNIEnv* env, DShowCaptureInfo *pCapInfo, IGraphBuilder* pGraph )
//{
//	HRESULT hr = S_OK;
//	if ( pCapInfo->pDebugSink == NULL )
//		return;
//
//	graphTools.DisconnectFilter( pGraph, pCapInfo->pDebugSink );
//
//	pCapInfo->pGraph->RemoveFilter(pCapInfo->pDebugSink);
// 
//	SAFE_RELEASE( pCapInfo->pDebugSink );
//
//}
