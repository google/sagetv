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

#ifdef WIN32
#include "stdafx.h"
#endif

#include "sage_media_format_MPEGParser2.h"

// NativeCore.h redefines this and it makes gcc grumpy.
#ifdef _LARGEFILE64_SOURCE
#undef _LARGEFILE64_SOURCE
#endif

#include "NativeCore.h"
#include "TSBuilder.h"
#include "Demuxer.h"
#include "Remuxer.h"

typedef struct {
	REMUXER *pRemuxer;
	int32_t inputFormat;
	int32_t outputFormat;
	int32_t streamFormat;
	int32_t channel;
	DUMP *pfnOutputDump;
	jobject outStream;
	char* pFormat;
	
	uint64_t bytes_in, bytes_out;

	JNIEnv *env;
	jmethodID outStreamWrite;
	jbyteArray outBuf;
	int32_t outBufSize;
} JavaRemuxer2;

static int OutputDump(void* pContext, void* pData, int nSize)
{
	JavaRemuxer2* jr = (JavaRemuxer2*)pContext;

	OUTPUT_DATA *pOutputData = (OUTPUT_DATA*)pData;
	uint8_t* pBlockData = pOutputData->data_ptr;
	int32_t nBytes = pOutputData->bytes;
	//uint64_t dwUsedBytes;
	//uint64_t dwGroupFlag = pOutputData->group_flag;
	//int32_t nGroupStart = pOutputData->start_offset;

	// This should reduce the number of time a byte array is created to return data.
	if (jr->outBuf == NULL) {
		jr->outBuf = (jbyteArray)jr->env->NewGlobalRef(jr->env->NewByteArray(nBytes));
		jr->outBufSize = nBytes;
	} else
	// When the array isn't exactly the same size as the data being returned,
	// sometimes it will cause the JVM to crash.
	if (jr->outBufSize != nBytes)
	{
		jr->env->DeleteGlobalRef(jr->outBuf);
		jr->outBuf = (jbyteArray)jr->env->NewGlobalRef(jr->env->NewByteArray(nBytes));
		jr->outBufSize = nBytes;
	}

	jr->env->SetByteArrayRegion(jr->outBuf, 0, nBytes, (jbyte*)pBlockData);

	jr->env->CallVoidMethod(jr->outStream, jr->outStreamWrite, jr->outBuf, 0, nBytes);
	jr->bytes_out += nBytes;

	return nBytes;
}

static int AVInfDump(void* pContext, void* pData, int nSize) {
	JavaRemuxer2* jr = (JavaRemuxer2*)pContext;
		
	// Clear the queue so we can go back to the begining of the stream
	// without accidentally writing data out of order.
	QueueZeroDemux(jr->pRemuxer->demuxer);

	// Allocate a copy since this is locally instantiated.
	jr->pFormat = (char*)malloc(nSize);
	memcpy(jr->pFormat, (char*)pData, nSize);

	return nSize;
}

/*
* Class:     sage_media_format_MPEGParser
* Method:    openRemuxer0
* Signature: (IIIIBSSSSSLjava/io/OutputStream;)J
*/
JNIEXPORT jlong JNICALL Java_sage_media_format_MPEGParser2_openRemuxer0
  (JNIEnv *env, jclass jc, jint inputFormat, jint outputFormat, jint streamFormat, jint subFormat, jbyte tuneStringType, jshort channel, jshort tsid, jshort data1, jshort data2, jshort data3, jobject outputStreamCallback)
{
	TUNE tune = { 0 };

	//3:xx-xx-xx; 2:xx-xx; 1: program; 0:channel
	tune.tune_string_type = tuneStringType;
	if (tuneStringType == 0)
	{
		tune.channel = channel;
	}
	else
	{
		tune.u.unkn.tsid = tsid;
		tune.u.unkn.data1 = data1;
		tune.u.unkn.data2 = data2;
		tune.u.unkn.data3 = data3;
	}
	
	ConsolidateTuneParam(&tune, streamFormat, subFormat);

	JavaRemuxer2 *jr = (JavaRemuxer2*)malloc(sizeof(JavaRemuxer2));
		
	jr->inputFormat = inputFormat;
	jr->outputFormat = outputFormat;	
	jr->streamFormat = streamFormat;
	jr->channel = channel;
	jr->pFormat = 0;
	jr->outBuf = 0;
	jr->outBufSize = 0;
	jr->outStream = env->NewGlobalRef(outputStreamCallback);
	// This pointer is always used to call the output method which
	// will always be called by the same thread pushing data in.
	jr->env = env;
	// This ID will never change.
	jr->outStreamWrite = env->GetMethodID(env->FindClass("java/io/OutputStream"), "write", "([BII)V");

	jr->pRemuxer = (REMUXER*)OpenRemuxStream(REMUX_STREAM, &tune, inputFormat, outputFormat, NULL, NULL, (DUMP)OutputDump, jr);
	
	SetupEPGDumpLanguage(jr->pRemuxer, LANGUAGE_CODE("eng"));
	SetDefaultAudioLanguage(jr->pRemuxer, LANGUAGE_CODE("eng"));
	
	SetupAVInfDump(jr->pRemuxer, (DUMP)AVInfDump, jr);

	return (jlong)jr;
}

/*
* Class:     sage_media_format_MPEGParser2
* Method:    closeRemuxer0
* Signature: (J)V
*/
JNIEXPORT void JNICALL Java_sage_media_format_MPEGParser2_closeRemuxer0
  (JNIEnv *env, jclass jc, jlong ptr)
{
	if (!ptr) return;

	JavaRemuxer2* jr = (JavaRemuxer2*)ptr;

	// This pointer is always used to call the output method which
	// will always be called by the same thread pushing data in.
	jr->env = env;

	if (jr->pRemuxer != NULL)
		CloseRemuxStream(jr->pRemuxer);

	if (jr->outBufSize > 0)
		env->DeleteGlobalRef(jr->outBuf);

	env->DeleteGlobalRef(jr->outStream);

	if (jr->pFormat != NULL)
		free(jr->pFormat);
	
	free(jr);
}

/*
* Class:     sage_media_format_MPEGParser2
* Method:    pushRemuxData0
* Signature: (J[BII)J
*/
JNIEXPORT jlong JNICALL Java_sage_media_format_MPEGParser2_pushRemuxData0
  (JNIEnv *env, jclass jc, jlong ptr, jbyteArray javabuf, jint offset, jint length)
{
	if (!ptr) return -1;
	if (length == 0) return 0;

	JavaRemuxer2* jr = (JavaRemuxer2*)ptr;

	// This pointer is always used to call the output method which
	// will always be called by the same thread pushing data in.
	jr->env = env;

	jbyte* newData = env->GetByteArrayElements(javabuf, NULL);
	unsigned char* pStart = (unsigned char*)(newData + offset);
	
	// This variable is being passed, but since 188 byte alignment is being done in
	// Java, unless we start to see other byte alignments, I can't think of a good
	// reason to return this value to the JVM.
	int nExpectedBytes2;
	int nUsedBytes = PushRemuxStreamData(jr->pRemuxer, pStart, length, &nExpectedBytes2);

	jr->bytes_in += length;

	env->ReleaseByteArrayElements(javabuf, newData, JNI_ABORT);
	
	return (jlong)nUsedBytes;
}

/*
* Class:     sage_media_format_MPEGParser2
* Method:    getAvFormat0
* Signature: (J[)Ljava/lang/String;
*/
JNIEXPORT jstring JNICALL Java_sage_media_format_MPEGParser2_getAvFormat0
  (JNIEnv *env, jclass jc, jlong ptr)
{
	JavaRemuxer2* jr = (JavaRemuxer2*)ptr;

	return jr->pFormat != NULL ? env->NewStringUTF(jr->pFormat) : NULL;
}