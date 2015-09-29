/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <sys/types.h>
#include "sage_media_format_MPEGParser.h"

#include "TSSplitter.h"
#include <time.h>
//ZQ
#ifdef __cplusplus
extern "C" {
#endif
extern int GetAVInf( char* FileName, unsigned long CheckSize, int bLiveFile, int RequestedTSProgram, 
			   char* FormatBuf, int FormatSize, char* DurationBuf, int DurationSize, int* Program );
#ifdef __cplusplus
}
#endif

#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _flog x
#endif


#if defined(__LP64__) || defined(WIN32)
#define INT64_TO_PTR(t,x) ((t)(x))
#define PTR_TO_INT64(t,x) ((t)(x))
#else
#define INT64_TO_PTR(t,x) ((t)(uint32_t)(x))
#define PTR_TO_INT64(t,x) (((t)(uint32_t)(x)) & 0xFFFFFFFFULL)
#endif


static bool flog_enabled=false;
static void _flog( char* logname, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	
	if ( !flog_enabled ) return;

	fp = fopen( logname, "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );
	fprintf( fp, "%02d/%02d/%d %02d:%02d:%02d  ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
	ltm.tm_hour, ltm.tm_min, ltm.tm_sec );  
	va_start(args, cstr);
	vfprintf( fp, cstr, args );
	va_end(args);
	fclose( fp );
}
static void _flog_check()
{
	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
	}
}

#ifdef __cplusplus
extern "C" {
#endif
int  GetAVFormat( char* FileName, unsigned long CheckSize, int bLiveFile, int RequestedTSProgram,
			   char* FormatBuf, int FormatSize, char* DurationBuf, int DurationSize, int* Program );
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
	const char* szFilename = (*env)->GetStringUTFChars(env, jFilename, NULL);
	char Format[2048], Duration[32];
	char buf[2048 + 128]; // bigger than Format + Duration in size
	int  LiveFile = LiveFile ? 1 : 0;
	int  TotalProgramNum=0, Channel;
	int  ret;
	memset(Format, 0, sizeof(Format));
	memset(Duration, 0, sizeof(Duration));
	memset(buf, 0, sizeof(buf));
	Channel = (int)jChannel;

	//get specified channel MedAVInf
	if ( Channel >= 0 )
	{
		ret = GetAVFormat( (char*)szFilename, (unsigned long)jSearchSize, LiveFile , Channel+1,
				            Format, sizeof(Format), Duration, sizeof(Duration), &TotalProgramNum  );

	} else
	{ //Search first one valid channel MedAVInf

		Channel = 0;
		do {
			ret = GetAVFormat((char*)szFilename, (unsigned long)jSearchSize, LiveFile, Channel+1,
				          Format, sizeof(Format), Duration, sizeof(Duration), &TotalProgramNum );

			if ( Format[0] == 0x0 || strstr(  Format, "ENCRYPTED;" ) || strstr( Format, "NO-DATA;" ) ||
				  strstr( Format, "UNKNOWN-TS;"  ) || strstr( Format, "UNKNOWN-PS;"  ) || strstr(  Format, "NO-AV-TS;" ) )
				Channel++;
			else
				break;

		} while( Channel < TotalProgramNum );

	}
	(*env)->ReleaseStringUTFChars(env, jFilename, szFilename);
	if ( ret >= 0 ) ret--; //ret is actual channel number; change range is 0..n; in GetAVFormat is 1...n+1
	if ( strstr(  Format, "ENCRYPTED-TS;" ) ) 
	{
		sprintf( buf, "Ret:%d Program:%d Format:AV-INF|f=%s", ret, TotalProgramNum, Format );
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

	flog(("Native.log", "GetAVFormat:%s\r\n", buf));
	jstring jstr;
	jstr = (*env)->NewStringUTF(env, buf);
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


//following code is used only by  RemuxTranscodeEngine for a Hauppauge MVP, so I have to keep it here
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
	rv->outStream = (*env)->NewGlobalRef(env, outputStreamCallback);
	rv->outBuf = (jbyteArray) (*env)->NewGlobalRef(env, (*env)->NewByteArray(env, REMUX_BUFFER_SIZE));
	rv->outBufSize = REMUX_BUFFER_SIZE;
	rv->bytes_in  = 0;
	rv->bytes_out = 0;
	rv->rebuiltTSPMT = false;
	SelectTSChannel(pSplt, (unsigned short) channel+1, true );
	return PTR_TO_INT64(jlong,rv);
}

/*
 * Class:     sage_media_format_MPEGParser
 * Method:    closeRemuxer0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_media_format_MPEGParser_closeRemuxer0
  (JNIEnv *env, jclass jc, jlong ptr)
{
	if (!ptr) return;
	JavaRemuxer* jr = INT64_TO_PTR(JavaRemuxer*,ptr);
	CloseTSSplitter(jr->pSplt);
	(*env)->DeleteGlobalRef(env, jr->outBuf);
	(*env)->DeleteGlobalRef(env, jr->outStream);
	free(jr);
}


int OutputDump( void* pContext, const unsigned char* pData, int lDataLen )
{
	CXT* cxt = (CXT*)pContext;
	if ( cxt == NULL ) return 0;

	JavaRemuxer* jr = cxt->jr;
	(*(cxt->env))->CallVoidMethod(cxt->env, jr->outStream, cxt->outStreamWrite, jr->outBuf, 0, lDataLen );
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
		*pData = (unsigned char*)(*(cxt->env))->GetPrimitiveArrayCritical(cxt->env, jr->outBuf, NULL);
		return cxt->jr->outBufSize;

	} else
	if ( cmd == 1 ) //release buffer
	{
		(*(cxt->env))->ReleasePrimitiveArrayCritical(cxt->env, jr->outBuf, (void*)*pData, JNI_ABORT );
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
#ifdef NO_MEDIA_MVP
	return 0;
#else
	if (!ptr) return 0;
	JavaRemuxer* jr = INT64_TO_PTR(JavaRemuxer*,ptr);
	// Get the native data. Don't use 'critical' access because we make callbacks into Java
	// while we're processing this data.
	jbyte* newData = (*env)->GetByteArrayElements(env, javabuf, NULL);
	const unsigned char* pStart;
	//unsigned long Bytes;
	//unsigned int Size;
	//int 	nBufferIndex;
	CXT cxt;

	
	pStart = (const unsigned char*)(newData + offset);

	static jmethodID outStreamWrite = 0;
	if ( outStreamWrite == 0 )
	    outStreamWrite = (*env)->GetMethodID(env, (*env)->FindClass(env, "java/io/OutputStream"), 
	                        "write", "([BII)V");
	cxt.env = env;	 cxt.jr = jr;	cxt.outStreamWrite = outStreamWrite;
	PushData2( jr->pSplt, pStart, (int)length,  AllocOutputBuffer, &cxt, (OUTPUT_DUMP)OutputDump, &cxt );
	jr->bytes_in += length; 

	(*env)->ReleaseByteArrayElements(env, javabuf, newData, JNI_ABORT);

	/*
	//if not data were splitted to push out try rebuiltTSPMT 
	if ( jr->bytes_out == 0 && jr->bytes_in > 4*12*524288*4 && !jr->rebuiltTSPMT  )
	{
		int ret = RebuildTSPMT( jr->pSplt );
		jr->rebuiltTSPMT = true;
		slog((env, "Rebuilt PMT %d.\r\n", ret ));
	}
	*/
	return (jlong) GetLastPTS(jr->pSplt);
#endif
}

/*
 * Class:     sage_media_format_MPEGParser
 * Method:    initRemuxDataDone0
 * Signature: (J[BII)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_media_format_MPEGParser_initRemuxDataDone0
  (JNIEnv *env, jclass jc, jlong ptr, jbyteArray javabuf, jint offset, jint length)
{
#ifdef NO_MEDIA_MVP
	return 0;
#else
	if (!ptr) return 0;
	JavaRemuxer* jr = INT64_TO_PTR(JavaRemuxer*,ptr);
	// Get the native data. Don't use 'critical' access because we make callbacks into Java
	// while we're processing this data.
	jbyte* newData = (*env)->GetByteArrayElements(env, javabuf, NULL);
	const unsigned char* pStart;
	//unsigned long Bytes;
	//unsigned int Size;
	//int 	nBufferIndex;
	
	pStart = (const unsigned char*)(newData + offset);

	char AVInfo[540]; //we able to AVInfo data  here
	bool ret = InitPushDone( jr->pSplt, pStart, (int)length, AVInfo, sizeof(AVInfo) );
	jr->bytes_in += length;
	(*env)->ReleaseByteArrayElements(env, javabuf, newData, JNI_ABORT);

	//if there is no PAT/PMT in stream, or broken, we try to rebuild it.
	if ( !ret && jr->bytes_in > 4*12*524288*2 && !jr->rebuiltTSPMT  )
	{
		int channel = RebuildTSPMT( jr->pSplt );
		jr->rebuiltTSPMT = true;
		//slog((env, "Rebuilt PMT %d.\r\n", channel ));
	}

	if ( ret )
		flog(("Native.log", "Old detect API getMediaAVInf:%s\r\n", AVInfo));

	return ret ? (*env)->NewStringUTF(env, AVInfo) : 0;
#endif
}

/*
 * Class:     sage_media_format_MPEGParser
 * Method:    flushRemuxer0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_media_format_MPEGParser_flushRemuxer0
  (JNIEnv *env, jclass jc, jlong ptr)
{
#ifdef NO_MEDIA_MVP
	return ;
#else
	if (!ptr) return;
	JavaRemuxer* jr = INT64_TO_PTR(JavaRemuxer*,ptr);
	FlushPush(jr->pSplt);
#endif
}


