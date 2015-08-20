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
#include "MCEncoder.h"
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "MainConcept/ModuleConfig/ModuleConfig.h"
#include "MainConcept/ModuleConfig/mpeg2enc_mc.h"
#include "MainConcept/ModuleConfig/l2aenc_mc.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
#include "jni-util.h"
#include "uniapi.h"

static JavaVM* g_vmBuf = NULL;
void MC_printf(const char* cstr, ...)
{
	if (!g_vmBuf)
	{
		jsize numVMs;
		if (JNI_GetCreatedJavaVMs(&g_vmBuf, 1, &numVMs))
			return;
	}
	JNIEnv* env;
	jint threadState = g_vmBuf->GetEnv((void**)&env, JNI_VERSION_1_2);
	if (threadState == JNI_EDETACHED)
		g_vmBuf->AttachCurrentThread((void**)&env, NULL);
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    vsprintf(buf, cstr, args);
    va_end(args);
	strcat(buf, "\r\n");
	jstring jstr = env->NewStringUTF(buf);
	static jclass cls = (jclass) env->NewGlobalRef(env->FindClass("java/lang/System"));
	static jfieldID outField = env->GetStaticFieldID(cls, "out", "Ljava/io/PrintStream;");
	static jmethodID printMeth = env->GetMethodID(env->FindClass("java/io/PrintStream"),
		"print", "(Ljava/lang/String;)V");
	jobject outObj = env->GetStaticObjectField(cls, outField);
	env->CallVoidMethod(outObj, printMeth, jstr);
	env->DeleteLocalRef(jstr);
	if (oldExcept)
		env->Throw(oldExcept);
	if (threadState == JNI_EDETACHED)
		g_vmBuf->DetachCurrentThread();
}

void Progress_printf(int percent, const char* cstr, ...)
{
	if (!g_vmBuf)
	{
		jsize numVMs;
		if (JNI_GetCreatedJavaVMs(&g_vmBuf, 1, &numVMs))
			return;
	}
	JNIEnv* env;
	jint threadState = g_vmBuf->GetEnv((void**)&env, JNI_VERSION_1_2);
	if (threadState == JNI_EDETACHED)
		g_vmBuf->AttachCurrentThread((void**)&env, NULL);
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    char buf[1024];
	SPRINTF(buf, sizeof(buf), "%d%% ", percent);
    vsprintf(&buf[strlen(buf)], cstr, args);
    va_end(args);
	strcat(buf, "\r\n");
	jstring jstr = env->NewStringUTF(buf);
	static jclass cls = (jclass) env->NewGlobalRef(env->FindClass("java/lang/System"));
	static jfieldID outField = env->GetStaticFieldID(cls, "out", "Ljava/io/PrintStream;");
	static jmethodID printMeth = env->GetMethodID(env->FindClass("java/io/PrintStream"),
		"print", "(Ljava/lang/String;)V");
	jobject outObj = env->GetStaticObjectField(cls, outField);
	env->CallVoidMethod(outObj, printMeth, jstr);
	env->DeleteLocalRef(jstr);
	if (oldExcept)
		env->Throw(oldExcept);
	if (threadState == JNI_EDETACHED)
		g_vmBuf->DetachCurrentThread();
}

void *get_rc(char* name)
{
	if(!strcmp(name,"malloc")) return malloc;
	else if(!strcmp(name,"free")) return free;
	else if(!strcmp(name,"err_printf")) return MC_printf;
	else if(!strcmp(name,"prg_printf")) return Progress_printf;
	else if(!strcmp(name,"wrn_printf")) return MC_printf;
	else if(!strcmp(name,"inf_printf")) return MC_printf;
	else printf("NotInitialized: %s\n",name);
	return NULL;
}

BOOL configureMCAudioEncoder(IBaseFilter* pAudioEnc, SageTVMPEG2EncodingParameters *pythonParams, JNIEnv *env)
{
	/*
	 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
	IModuleConfig* pMCProps = NULL;
	VARIANT Value;
	HRESULT hr = pAudioEnc->QueryInterface(IID_IModuleConfig, (void**)&pMCProps);
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

		hr = pMCProps->SetValue( &EL2ENC_BITRATE_MPEG2, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure audio bit rate  %d hr=0x%x\r\n", Value.intVal, hr ));

		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = pythonParams->audiocrc ? 1 : 0;
		hr = pMCProps->SetValue( &EMC_CRC_PROTECTION, &Value );
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
		hr = pMCProps->SetValue( &EMC_JOINT_CODING, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure audio mode  %d hr=0x%x\r\n", Value.intVal, hr ));
		
		return TRUE;

		SAFE_RELEASE(pMCProps);
	} else
		slog((env, "failed Performing MC 2.0 MPEG audio configuration\r\n"));

	 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
	 */
	return FALSE;
}


BOOL configureMCVideoEncoder(DShowCaptureInfo* pCapInfo, IBaseFilter* pVidEnc, 
							SageTVMPEG2EncodingParameters *pythonParams, JNIEnv *env)
{
	/*
	 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
	IModuleConfig* pMCProps = NULL;
	VARIANT Value;
	HRESULT hr = pVidEnc->QueryInterface(IID_IModuleConfig, (void**)&pMCProps);
	if (SUCCEEDED(hr))
	{
		slog((env, "Performing MC 2.0 MPEG video configuration\r\n"));

	
		//1 .setup Mpeg Type
		EM2VE::VideoType MpegType;
		
		switch (pythonParams->outputstreamtype)
		{
			case 0:
				MpegType = EM2VE::MPEG2;// HCW_Sys_StreamType_Program;
				break;
			case 2:
				MpegType = EM2VE::MPEG1; //HCW_Sys_StreamType_MPEG1;
				break;
			case 11:
				MpegType = EM2VE::DVD_MPEG1; //HCW_Sys_StreamType_MPEG1_VCD;
				break;
			case 12:
				MpegType = EM2VE::SVCD; //HCW_Sys_StreamType_Program_SVCD;
				break;
			default:
				//MpegType = EM2VE::DVD;  // HCW_Sys_StreamType_Program_DVD;
				MpegType = EM2VE::MPEG2;// HCW_Sys_StreamType_Program;
				break;
		} 

		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = (int)MpegType;
		hr = pMCProps->SetValue( &EMC_PRESET, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure output Mpeg Type %d hr=0x%x\r\n", MpegType, hr ));	
		
		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = (int)1;
		hr = pMCProps->SetValue( &EM2VE_SetDefValues, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure make a default setup for %d hr=0x%x\r\n", MpegType, hr ));	
		

		//2. set up video Format
		EM2VE::VideoFormat VideoForamt;
		if ( IsNTSCVideoCode(pCapInfo->videoFormatCode) )
			VideoForamt = EM2VE::FORMAT_NTSC;
		else 
		if ( IsPALVideoCode(pCapInfo->videoFormatCode) )
			VideoForamt = EM2VE::FORMAT_PAL;
		else
		if ( IsSECAMVideoCode(pCapInfo->videoFormatCode) )
			VideoForamt = EM2VE::FORMAT_SECAM;
		else
			VideoForamt = EM2VE::FORMAT_AUTO;
		
		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = (int)VideoForamt;
		hr = pMCProps->SetValue( &EM2VE_VideoFormat, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video format %d hr=0x%x\r\n", VideoForamt, hr ));

		
		//3. gop size
		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = (int)pythonParams->gopsize;
		hr = pMCProps->SetValue( &EMC_GOP_LENGTH, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video gop size %d hr=0x%x\r\n", pythonParams->gopsize, hr ));

		//4. gop interval
		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = (int)pythonParams->closedgop;
		hr = pMCProps->SetValue( &EM2VE_ClosedGOP_Interval, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video gop interval %d hr=0x%x\r\n", pythonParams->closedgop, hr ));


		//5. gop bount
		VariantInit(&Value);
		Value.vt = VT_INT;
		if ( (int)pythonParams->ipb == 0 ) 
			Value.intVal = 2;
		else
			Value.intVal = (int)pythonParams->ipb;
		hr = pMCProps->SetValue( &EMC_GOP_BCOUNT, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video gop bcount %d hr=0x%x\r\n", pythonParams->ipb, hr ));


		//6. bit rate
		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = pythonParams->vbr ? 0 : 1;
		hr = pMCProps->SetValue( &EMC_BITRATE_MODE, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video bit rate mode %d hr=0x%x\r\n", Value.intVal, hr ));

		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = pythonParams->videobitrate;
		hr = pMCProps->SetValue( &EMC_BITRATE_AVG, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video bit rate (avg) %d hr=0x%x\r\n", Value.intVal, hr ));

		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = pythonParams->peakvideobitrate;
		hr = pMCProps->SetValue( &EMC_BITRATE_MAX, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video max bit rate  %d hr=0x%x\r\n", Value.intVal, hr ));

		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = 2*pythonParams->videobitrate - pythonParams->peakvideobitrate;
		hr = pMCProps->SetValue( &EMC_BITRATE_MIN, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video min bit rate  %d hr=0x%x\r\n", Value.intVal, hr ));

		
		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = pythonParams->deinterlace;
		hr = pMCProps->SetValue( &EM2VE_DeinterlacingMode, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video deinterlace %d hr=0x%x\r\n", Value.intVal, hr ));

		
		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = pythonParams->inversetelecine;
		hr = pMCProps->SetValue( &EM2VE_Pulldown, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video pulldown %d hr=0x%x\r\n", Value.intVal, hr ));

		// Based on support feedback; we need to allow people to change this sometimes
		VariantInit(&Value);
		Value.vt = VT_INT;
		Value.intVal = GetRegistryDword(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegEnc", "TopFieldFirst", 1);
		hr = pMCProps->SetValue( &EM2VE_TopFirst, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure top field first %d hr=0x%x\r\n", Value.intVal, hr ));

		//pythonParams->width,  pythonParams->height, MC2 read only, skip
		VariantInit(&Value);
		Value.vt = VT_INT;
		if ( pythonParams->aspectratio == 0 ) 
			Value.intVal = EM2VE::ASPECT_MPEG1_1x1;
		else
		if ( pythonParams->aspectratio == 1 ) 
			Value.intVal = EM2VE::ASPECT_MPEG2_4x3;
		else
		if ( pythonParams->aspectratio == 2 ) 
			Value.intVal = EM2VE::ASPECT_MPEG2_16x9;
		else
		if ( pythonParams->aspectratio == 3 ) 
			Value.intVal = EM2VE::ASPECT_MPEG2_2_211x1;
		else
			Value.intVal = 0; 
		hr = pMCProps->SetValue( &EMC_ASPECT_RATIO, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video ratio  %d hr=0x%x\r\n", Value.intVal, hr ));

		VariantInit(&Value);
		Value.vt = VT_INT;
		if (pythonParams->fps == 25)
			Value.intVal = EM2VE::FRAMERATE_25;
		else
		if (pythonParams->fps == 15)
			Value.intVal = EM2VE::FRAMERATE_30;
		else // 30 fps (actually 29.97)
		if (pythonParams->fps == 30)
			Value.intVal = EM2VE::FRAMERATE_29;
		else
		   Value.intVal = EM2VE::FRAMERATE_AUTO;
		hr = pMCProps->SetValue( &EM2VE_Framerate, &Value );
		if ( hr != S_OK ) slog((env, "Failed configure video fps  %d hr=0x%x\r\n", Value.intVal, hr ));
		
		
		SAFE_RELEASE(pMCProps);
		return TRUE;
	} else
		slog((env, "Failed performing MC 2.0 MPEG video configuration\r\n"));
	 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
	 */
	return FALSE;

}

