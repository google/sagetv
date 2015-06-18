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
#include "jni-util.h"
#include "DShowPlayer.h"
#include "../DShowCapture/DShowUtilities.h"
#include "sage_DShowMediaPlayer.h"
#include "sage_DShowTVPlayer.h"
#include "sage_PlaybackException.h"
//#include "MainConcept/mcmpguid.h"
//#include "MainConcept/imcdprop.h"
#include "sage_media_format_MPEGParser.h"
#include "TSSplitter.h"
#include "IMpegDeMux.h"

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    frameStep0
 * Signature: (JI)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowMediaPlayer_frameStep0
  (JNIEnv *env, jobject jo, jlong dataPtr, jint amount)
{
	if (!dataPtr) return JNI_FALSE;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (!pGraph) return JNI_FALSE;
	IVideoFrameStep *pStep = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IVideoFrameStep, (void**)&pStep);
	if (SUCCEEDED(hr) && pStep != NULL )
	{
		hr = pStep->Step(amount, NULL);
		SAFE_RELEASE(pStep);
		WaitForEvent(env, pGraph, EC_STEP_COMPLETE);
		return SUCCEEDED(hr);
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    pause0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowMediaPlayer_pause0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return JNI_FALSE;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (!pGraph) return JNI_FALSE;
	IMediaControl *pMC = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IMediaControl, (void**)&pMC);
	if (SUCCEEDED(hr)&& pMC != NULL )
	{
		slog((env, "Pausing the playback graph.\r\n"));
		hr = pMC->Pause();
		WaitForState(env, pMC, State_Paused);
		SAFE_RELEASE(pMC);
		HTESTPRINT(hr);
		return SUCCEEDED(hr);
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    play0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DShowMediaPlayer_play0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return JNI_FALSE;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (!pGraph) return JNI_FALSE;
	IMediaControl *pMC = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IMediaControl, (void**)&pMC);
	if (SUCCEEDED(hr) && pMC != NULL )
	{
		slog((env, "Running the playback graph.\r\n"));
		hr = pMC->Run();
		WaitForState(env, pMC, State_Running);
		SAFE_RELEASE(pMC);
		HTESTPRINT(hr);
		return SUCCEEDED(hr);
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    seek0
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_seek0
  (JNIEnv *env, jobject jo, jlong dataPtr, jlong jtime)
{
	if (!dataPtr) return;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (!pGraph) return;
	IMediaSeeking *pSeeker = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IMediaSeeking, (void**)&pSeeker);
	if (SUCCEEDED(hr) && pSeeker != NULL )
	{
		try
		{
			slog((env, "About to perform media time selection for %d\r\n", (int) jtime));
			jtime *= 10000;
			hr = pSeeker->SetPositions(&jtime, AM_SEEKING_AbsolutePositioning,
				0, AM_SEEKING_NoPositioning);
			jtime /= 10000;
			SAFE_RELEASE(pSeeker);
			PLAYEXCEPT_RET(sage_PlaybackException_SEEK);
		}
		catch (...)
		{
			hr = E_FAIL;
			SAFE_RELEASE(pSeeker);
			PLAYEXCEPT_RET(sage_PlaybackException_SEEK);
		}
		
	}
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    setPlaybackRate0
 * Signature: (JF)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_setPlaybackRate0
  (JNIEnv *env, jobject jo, jlong dataPtr, jfloat rate)
{
	if (!dataPtr) return;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (!pGraph) return;
	IMediaSeeking *pSeeker = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IMediaSeeking, (void**)&pSeeker);
	if (SUCCEEDED(hr) && pSeeker != NULL )
	{
		pSeeker->SetRate(rate);
		// Try to do the MainConcept decoder configuration
/*		if (playData->GetVideoDecoder())
		{
			IMCMpegVideoDecoderFilter* pMCDecoder;
			hr = playData->GetVideoDecoder()->QueryInterface(IID_IMCMpegVideoDecoderFilter, (void**)&pMCDecoder);
			if (SUCCEEDED(hr))
			{
				if (rate >= 2.0)
				{
					if (rate >= 8.0)
					{
						hr = pMCDecoder->put_FilterSetting(VDSETTING_SKIP_PFRAMES, 1);
						HTESTPRINT(hr);
					}
					else
					{
						hr = pMCDecoder->put_FilterSetting(VDSETTING_SKIP_PFRAMES, 0);
						HTESTPRINT(hr);
						hr = pMCDecoder->put_FilterSetting(VDSETTING_SKIP_BFRAMES, 1);
						HTESTPRINT(hr);
					}
				}
				else
				{
					hr = pMCDecoder->put_FilterSetting(VDSETTING_SKIP_PFRAMES, 0);
					HTESTPRINT(hr);
					hr = pMCDecoder->put_FilterSetting(VDSETTING_SKIP_BFRAMES, 0);
					HTESTPRINT(hr);
				}
				pMCDecoder->Release();
			}
		}*/
		SAFE_RELEASE(pSeeker);
	}
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    getPlaybackRate0
 * Signature: (J)F
 */
JNIEXPORT jfloat JNICALL Java_sage_DShowMediaPlayer_getPlaybackRate0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	//if (!dataPtr) return 1;
	IGraphBuilder* pGraph = NULL;
	CPlayerData* playData = (CPlayerData*)dataPtr;
	if ( playData )
		pGraph = playData->GetGraph();
	if ( pGraph == NULL ) return 1;
	IMediaSeeking *pSeeker = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IMediaSeeking, (void**)&pSeeker);
	if (SUCCEEDED(hr) && pSeeker != NULL )
	{
		double d;
		HRESULT hr = pSeeker->GetRate(&d);
		SAFE_RELEASE(pSeeker);
		if (SUCCEEDED(hr))
			return (jfloat) d;
		else
			return 1;
	}
	else return 1;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    stop0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_DShowMediaPlayer_stop0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (!pGraph) return;
	IMediaControl *pMC = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IMediaControl, (void**)&pMC);
	if ( SUCCEEDED(hr) && pMC != NULL )  //ZQ I got a case hr= 0x12e3ac, pMC == NULL
	{
		slog((env, "Stopping the playback graph.\r\n"));
		hr = pMC->Stop();
		WaitForState(env, pMC, State_Stopped);
		SAFE_RELEASE(pMC);
		playData->CallPluginGraphStopped();
	}
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    getDurationMillis0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sage_DShowMediaPlayer_getDurationMillis0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (!pGraph) return 0;
	IMediaSeeking *pSeeker = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IMediaSeeking, (void**)&pSeeker);
	if (SUCCEEDED(hr) && pSeeker != NULL )
	{
		jlong rv;
		hr = pSeeker->GetDuration(&rv);
		rv /= 10000;
		SAFE_RELEASE(pSeeker);
		slog((env, "----------->Get getDurationMillis0=%d.\r\n", rv));
		if (SUCCEEDED(hr))
			return rv;
		else
			return 0;
	}
	else return 0;
}

/*
 * Class:     sage_DShowMediaPlayer
 * Method:    getMediaTimeMillis0
 * Signature: (J)J
 */
#include <time.h>
JNIEXPORT jlong JNICALL Java_sage_DShowMediaPlayer_getMediaTimeMillis0
  (JNIEnv *env, jobject jo, jlong dataPtr)
{
	if (!dataPtr) return 0;
	CPlayerData* playData = (CPlayerData*) dataPtr;
	IGraphBuilder* pGraph = playData->GetGraph();
	if (!pGraph) return 0;
	IMediaSeeking *pSeeker = NULL;
	HRESULT hr = pGraph->QueryInterface(IID_IMediaSeeking, (void**)&pSeeker);
	if (SUCCEEDED(hr) && pSeeker != NULL )
	{
		jlong rv;
		hr = pSeeker->GetCurrentPosition(&rv);
		rv /= 10000;
		SAFE_RELEASE(pSeeker);
		HTESTPRINT(hr);

		if ( playData->HasDemux() )
		{   
			IBaseFilter* pDeMux = playData->GetDemux();
			IMpegDeMux* piDeMux = NULL;
			HRESULT hr2 = pDeMux->QueryInterface(IID_IMpegDeMux, (void**)&piDeMux);
			if ( !FAILED(hr2)) 
			{
				jlong pre_sp, sp;
				piDeMux->SwitchPosition( &pre_sp, &sp );
				sp /= 10000;
				pre_sp /= 10000;

				//trace OSD mediaTime
				{
					static jlong _sp = 0;
					static time_t _t0 = 0;
					if (  time(NULL) -_t0  >= 1 || _sp != sp )
					{
						jlong new_rv = rv;
						if ( sp > 0 && new_rv >= sp ) 
							new_rv -= sp;
						else
						if ( new_rv >= pre_sp ) 
							new_rv -= pre_sp;

						slog((env, "=======>Get getMediaTimeMillis0 cur:%d s0:%d s1:%d new:%d.\r\n", (long)rv,  (long)pre_sp, (long)sp, (long)new_rv ));
						_t0 = time(NULL);
						_sp = sp;
					}
				}

				if ( sp > 0 && rv >= sp ) 
					rv -= sp;
				else
				if ( rv >= pre_sp ) 
					rv -= pre_sp;

			}
			SAFE_RELEASE( piDeMux );
		}

		//slog((env, "=======>Get getMediaTimeMillis0=%d.\r\n", rv)); //ZQ
		if (SUCCEEDED(hr))
			return rv;
		else
			return 0;
	}
	else return 0;
}


//ZQ
#ifdef __cplusplus
extern "C" {
#endif
extern int __cdecl GetAVFormatW( wchar_t* FileName, unsigned long CheckSize, int bLiveFile, int RequestedTSProgram, 
			   char* FormatBuf, int FormatSize, char* DurationBuf, int DurationSize, int* Program );
extern ULONGLONG __cdecl   hs_long_long( char* digital );
extern int __cdecl ms_time_stamp( ULONGLONG llTime, char* pBuffer, int nSize );
extern int __cdecl _flog_check();
#ifdef __cplusplus
}
#endif

/*
 * Class:     sage_media_format_MPEGParser
 * Method:    getMediaAVInf0
 * Signature: (Ljava/lang/String;JZJ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_media_format_MPEGParser_getMediaAVInf0
  (JNIEnv *env, jclass jo, jstring jFilename, jlong jSearchSize, jboolean jLiveFile, jlong jChannel )
{
	//const char* szFilename = env->GetStringUTFChars(jFilename, NULL);
	const jchar* szFilename = env->GetStringChars(jFilename, NULL);
	char Format[2048], Duration[32];
	char buf[2048 + 128]; // bigger than Format + Duration in size
	int  LiveFile = (BOOL)jLiveFile ? true:false;
	int  TotalProgramNum, Channel;
	int  ret;
	ZeroMemory(Format, sizeof(Format));
	ZeroMemory(Duration, sizeof(Duration));
	ZeroMemory(buf, sizeof(buf));
	Channel = (int)jChannel;
	TotalProgramNum = 0;
	
	_flog_check();

	//get specified channel MedAVInf
	if ( Channel >= 0 )
	{
		ret = GetAVFormatW( (wchar_t*)szFilename, (unsigned long)jSearchSize, LiveFile , Channel+1,   
				            Format, sizeof(Format), Duration, sizeof(Duration), &TotalProgramNum  );
		
	} else
	{ //Search first one valid channel MedAVInf

		Channel = 0;
		do {
			ret = GetAVFormatW((wchar_t*)szFilename, (unsigned long)jSearchSize, LiveFile, Channel+1, 
				          Format, sizeof(Format), Duration, sizeof(Duration), &TotalProgramNum );

			if ( Format[0] == 0x0 || strstr(  Format, "ENCRYPTED-TS;" ) || strstr( Format, "NO-DATA;" ) ||
				strstr( Format, "UNKNOWN-TS;"  ) || strstr( Format, "UNKNOWN-PS;"  ) || strstr(  Format, "NO-AV-TS;" ) )
				Channel++;
			else
				break;

		} while( Channel < TotalProgramNum );

	}
	env->ReleaseStringChars(jFilename, szFilename);

	//ret is actual channel number if ret= 1..n
	if ( ret >= 0 )	ret--;
	if ( strstr(  Format, "ENCRYPTED-TS;" ) ) 
	{
		sprintf( buf, "Ret:%d Program:%d Format:AV-INF|f=%s", ret, TotalProgramNum, Duration, Format );
	} else
	if ( strstr( Format, "UNKNOWN-TS;"  ) || strstr( Format, "UNKNOWN-PS;"  ) || strstr(  Format, "NO-AV-TS;" ) )
	{
		buf[0] = 0x0;
	} else
	if (  strlen( Format )+strlen( Duration )+64 < sizeof(buf) )
	{
		sprintf( buf, "Ret:%d Program:%d Duration:%s Format:AV-INF|f=%s", ret, TotalProgramNum, Duration, Format );
	}
	else
		buf[0] = 0x0;

	{
		char dur[64], format[1024];
		ULONGLONG duration_ll=hs_long_long( Duration );
		ms_time_stamp( duration_ll, dur, sizeof(dur) );
		_snprintf( format, sizeof(format)-16, "AV Format:%s(%s)\r\n", buf, dur );
		format[1020] = 0x0; //slog buffer is 1024 bytes, cut off to prevent over run. 
		slog((env, format ));
	}
	jstring jstr;
	jstr = env->NewStringUTF(buf);
	return jstr;
}

typedef struct
{
	TSSPLT *pSplt;
	jobject outStream;
	jbyteArray outBuf;
	int outBufSize;
	unsigned long bytes_in, bytes_out;
	int  rebuiltTSPMT;
} JavaRemuxer;

#define TS_BUFFER_PACKETS   24

typedef struct
{
	JNIEnv *env;
	jmethodID outStreamWrite;
	JavaRemuxer* jr;
	
} CXT;

#define REMUX_BUFFER_SIZE 8192

/*
 * Class:     sage_media_format_MPEGParser
 * Method:    openRemuxer0
 * Signature: (IILjava/io/OutputStream;)J
 */
JNIEXPORT jlong JNICALL Java_sage_media_format_MPEGParser_openRemuxer0
  (JNIEnv *env, jclass jc, jint remuxMode, jint channel, jobject outputStreamCallback)
{
	TSSPLT *pSplt = (TSSPLT*) OpenTSSplitter(remuxMode);
	if (!pSplt)
		return 0;
	JavaRemuxer* rv = (JavaRemuxer*)malloc(sizeof(JavaRemuxer));
	rv->pSplt = pSplt;
	rv->outStream = env->NewGlobalRef(outputStreamCallback);
	rv->outBuf = (jbyteArray) env->NewGlobalRef(env->NewByteArray(REMUX_BUFFER_SIZE));
	rv->outBufSize = REMUX_BUFFER_SIZE;
	rv->bytes_in  = 0;
	rv->bytes_out = 0;
	rv->rebuiltTSPMT = false;
	StartTSParser( pSplt );
	SelectTSChannel(pSplt, (unsigned short) channel+1, true );
	return (jlong)rv;
}

/*
 * Class:     sage_media_format_MPEGParser
 * Method:    closeRemuxer0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_media_format_MPEGParser_closeRemuxer0
  (JNIEnv *env, jclass jc, jlong ptr)
{
	int i=0;
	if (!ptr) return;
	JavaRemuxer* jr = (JavaRemuxer*) ptr;
	StopTSParser( jr->pSplt );
	while ( i++<60 && !IsTSParserStopped( jr->pSplt ) )
	{
		StopTSParser( jr->pSplt );
		Sleep( 20 );
	}
	CloseTSSplitter(jr->pSplt);
	env->DeleteGlobalRef(jr->outBuf);
	env->DeleteGlobalRef(jr->outStream);
	free(jr);
}


int OutputDump( void* pContext, const unsigned char* pData, int lDataLen )
{
	CXT* cxt = (CXT*)pContext;
	if ( cxt == NULL ) return 0;

	JavaRemuxer* jr = cxt->jr;
	cxt->env->CallVoidMethod(jr->outStream, cxt->outStreamWrite, jr->outBuf, 0, lDataLen );
	jr->bytes_out += lDataLen;
	return lDataLen;
}

int AllocOutputBuffer( void* pContext, unsigned char** pData, int cmd )
{
	CXT* cxt = (CXT*)pContext;
	*pData = NULL;
	if ( cxt == NULL ) return 0;
	JavaRemuxer* jr = cxt->jr;
	if ( cmd == 0 ) //alloc buffer
	{
		*pData = (unsigned char*)cxt->env->GetPrimitiveArrayCritical(jr->outBuf, NULL);
		return cxt->jr->outBufSize;

	} else
	if ( cmd == 1 ) //release buffer
	{
		cxt->env->ReleasePrimitiveArrayCritical( jr->outBuf, (void*)*pData, JNI_ABORT );
		return 0;
	}
	return 0;
}

/*
 * Class:     sage_media_format_MPEGParser
 * Method:    pushRemuxData0
 * Signature: (J[BII)J
 */
JNIEXPORT jlong JNICALL Java_sage_media_format_MPEGParser_pushRemuxData0
  (JNIEnv *env, jclass jc, jlong ptr, jbyteArray javabuf, jint offset, jint length)
{
	if (!ptr) return 0;
	JavaRemuxer* jr = (JavaRemuxer*) ptr;
	// Get the native data. Don't use 'critical' access because we make callbacks into Java
	// while we're processing this data.
	jbyte* newData = env->GetByteArrayElements(javabuf, NULL);
	const unsigned char* pStart;
	//unsigned long Bytes;
	//unsigned int Size;
	//int 	nBufferIndex;
	CXT cxt;

	
	pStart = (const unsigned char*)(newData + offset);

	static jmethodID outStreamWrite = env->GetMethodID(env->FindClass("java/io/OutputStream"), "write", "([BII)V");
	cxt.env = env;	 cxt.jr = jr;	cxt.outStreamWrite = outStreamWrite;
	PushData2( jr->pSplt, pStart, (int)length,  AllocOutputBuffer, &cxt, (OUTPUT_DUMP)OutputDump, &cxt );
	jr->bytes_in += length; 

	env->ReleaseByteArrayElements(javabuf, newData, JNI_ABORT);

	return (jlong) GetLastPTS(jr->pSplt);
}

/*
 * Class:     sage_media_format_MPEGParser
 * Method:    initRemuxDataDone0
 * Signature: (J[BII)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_media_format_MPEGParser_initRemuxDataDone0
  (JNIEnv *env, jclass jc, jlong ptr, jbyteArray javabuf, jint offset, jint length)
{
	if (!ptr) return 0;
	JavaRemuxer* jr = (JavaRemuxer*) ptr;
	// Get the native data. Don't use 'critical' access because we make callbacks into Java
	// while we're processing this data.
	jbyte* newData = env->GetByteArrayElements(javabuf, NULL);
	const unsigned char* pStart;

	pStart = (const unsigned char*)(newData + offset);

	char AVInfo[540]; //we able to get AVInfo data  here
	bool ret = InitPushDone( jr->pSplt, pStart, (int)length, AVInfo, sizeof(AVInfo) );
	jr->bytes_in += length;
	env->ReleaseByteArrayElements(javabuf, newData, JNI_ABORT);

	//if there is no PAT/PMT in stream, or broken, we try to rebuild it.
	if ( !ret && jr->bytes_in > 4*12*524288*2 && !jr->rebuiltTSPMT  )
	{
		int channel = RebuildTSPMT( jr->pSplt );
		jr->rebuiltTSPMT = true;
		slog((env, "Rebuilt PMT (ver 1.1) %d.\r\n", channel ));
	}

	return ret ? env->NewStringUTF(AVInfo) : 0;
}

/*
 * Class:     sage_media_format_MPEGParser
 * Method:    flushRemuxer0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_media_format_MPEGParser_flushRemuxer0
  (JNIEnv *env, jclass jc, jlong ptr)
{
	if (!ptr) return;
	JavaRemuxer* jr = (JavaRemuxer*) ptr;
	FlushPush(jr->pSplt);
}

