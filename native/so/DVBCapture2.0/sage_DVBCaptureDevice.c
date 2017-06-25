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
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/errno.h>
#include <unistd.h>
#include <features.h> 
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <getopt.h>
#include <errno.h>
#include <sys/time.h>
#include <math.h>
#include <linux/types.h>
#include <time.h>
#include <sys/timeb.h>
#include <sys/types.h>
#include <sys/un.h>
#include <linux/dvb/dmx.h>
#include <linux/dvb/frontend.h>
#include <poll.h>
#include <pthread.h>
#include <dirent.h>
#include "thread_util.h"
#include "circbuffer.h"

#ifdef STANDALONE
#define APISTRING const char *
#else
#define APISTRING jstring
#endif

// Tweak this if needed
#define CAPCIRCBUFFERSIZE 16*1024*1024 

static int CaptureThread(void *data);


// Should we use new file transition
#define FILETRANSITION

#include "sage_DVBCaptureDevice.h"
#include "sage_EncodingException.h"

#include "NativeCore.h"
#include "Remuxer.h"
#include "TSFilter.h"
#include "TSParser.h"
#include "ScanFilter.h"
#include "DVBCaptureDevice.h"

#if defined(__LP64__) || defined(WIN32)
#define INT64_TO_PTR(t,x) ((t)(x))
#define PTR_TO_INT64(t,x) ((t)(x))
#else
#define INT64_TO_PTR(t,x) ((t)(uint32_t)(x))
#define PTR_TO_INT64(t,x) (((t)(uint32_t)(x)) & 0xFFFFFFFFULL)
#endif

#define VERSION_TAG	"(ver 2.0.11)"

#define  LOCK_FRQ_TIMEOUT	6

#ifdef REMOVE_LOG	
#define flog(x)
#else
#define flog(x)   _flog x
#endif

static LNB lnbs[] = {
	{"UNIVERSAL",	9750000, 10600000, 11700000 },
 	{"DBS",		11250000, 0, 0    },
	{"STANDARD",	10000000, 0, 0    },
	{"ENHANCED",	9750000,  0, 0    },
	{"C-BAND",	5150000,  0, 0    },
	{"C-MULTI",	5150000,  5750000, 0 }
};



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


static void _local_flog_check()
{
	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		_enable_native_log( );
		fclose( fp );
	}
}


static bool epg_log_enabled = false;
static void _epg_log_check()
{
	FILE* fp = fopen( "EPG_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		epg_log_enabled = true; 
		fclose( fp );
	} else
		epg_log_enabled = false;
}

#ifdef STANDALONE
static void sysOutPrint(JNIEnv *env, const char* cstr, ...)
{
    va_list args;
    va_start(args, cstr);
    char buf[1024*2];
    vsnprintf(buf, sizeof(buf), cstr, args);
    va_end(args);
    fprintf(stderr, buf);
}
#else
static void sysOutPrint(JNIEnv *env, const char* cstr, ...) 
{
    static jclass cls=NULL;
    static jfieldID outField=NULL;
    static jmethodID printMeth=NULL;
    if(env == NULL) return;
    jthrowable oldExcept = (*env)->ExceptionOccurred(env);
    if (oldExcept)
        (*env)->ExceptionClear(env);
    va_list args;
    va_start(args, cstr);
    char buf[1024*2];
    vsnprintf(buf, sizeof(buf), cstr, args);
    va_end(args);
    jstring jstr = (*env)->NewStringUTF(env, buf);
    if(cls==NULL)
    {
        cls = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/System"));
        outField = (*env)->GetStaticFieldID(env, cls, "out", "Ljava/io/PrintStream;");
        printMeth = (*env)->GetMethodID(env, (*env)->FindClass(env, "java/io/PrintStream"),
        "print", "(Ljava/lang/String;)V");
    }
    jobject outObj = (*env)->GetStaticObjectField(env, cls, outField);
    (*env)->CallVoidMethod(env, outObj, printMeth, jstr);
    (*env)->DeleteLocalRef(env, jstr);
    if (oldExcept)
        (*env)->Throw(env, oldExcept);
}
#endif

/* DVB debug section  */

int LoadDebugCfg( DBG* dbg );
static int ReadDeviceCfg( DVBCaptureDev *CDev );
static bool GetIntVal( char*p, const char* Name, unsigned long* pVal );
//static bool GetWordVal( char*p, const char* Name, unsigned short* pVal );
static bool GetString( char*p, const char* Name, char* Str, int MaxLen );

unsigned long msElipse( unsigned long start_time );

void postMessage( char* pSrc, char* pData, int MsgType, int Priority );
long EPG_Dumper( void* context, void* mesg, short bytes );
long AVInf_Dumper( void* context, void* mesg, short bytes );
long OnHardDeumlex( void* context, short bytes, void* mesg );

//static bool tuneChannel( void* Capture, const char* tune_string );
bool tuneChannel( CHANNEL_DATA *channel, const char* tune_string );
static int checkLocked( DVBCaptureDev *CDev );
static int SplitStream( DVBCaptureDev *CDev, char* pData, int lDataLen );
static int FlushOutBufferData( DVBCaptureDev *CDev );
static char* getTunerDeviceName( DVBCaptureDev *CDev );
static int tuneQAMFrequency( DVBCaptureDev *CDev, QAM_FREQ* qam, int dryTune );
static int tuneATSCChannel( DVBCaptureDev *CDev,  ATSC_FREQ* ats, int dryTunec );
static int tuneDVBTFrequency( DVBCaptureDev *CDev, DVB_T_FREQ* dvbt, int dryTune );
static int tuneDVBCFrequency( DVBCaptureDev *CDev, DVB_C_FREQ* dvbc, int dryTune );
static int tuneDVBSFrequency( DVBCaptureDev *CDev, DVB_S_FREQ* dvbs, int dryTune );
static char* front_end_status_string( int status );
int getAdapterNum( DVBCaptureDev *CDev );
static int OutputDump( void* pContext, void* pDataBlk, int lBytes );
static void setDmxBufferSize( DVBCaptureDev *CDev, unsigned long size );
static void emptyDmxBufferSize( DVBCaptureDev *CDev, unsigned long size );

#ifdef STANDALONE
void throwEncodingException(JNIEnv* env, jint errCode)
{
    fprintf(stderr, "Exception at line %d\n",errCode);
    exit(1);
}
#else
void throwEncodingException(JNIEnv* env, jint errCode)
{
	static jclass encExcClass = 0;
	
	static jmethodID encConstMeth = 0;
	if (!encExcClass)
	{
		encExcClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "sage/EncodingException"));
		encConstMeth = (*env)->GetMethodID(env, encExcClass, "<init>", "(II)V");
	}
	jthrowable throwie = (jthrowable) (*env)->NewObject(env, encExcClass, encConstMeth, errCode, (jint) errno);
	(*env)->Throw(env, throwie);
}
#endif


/*
 * Class:     sage_DVBCaptureDevice
 * Method:    createEncoder0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_DVBCaptureDevice_createEncoder0
  (JNIEnv *env, jobject jo, 
  APISTRING jdevname)
{

	_local_flog_check();
	_epg_log_check();
	flog(("Native.log", "DVB: create Encoder (Native log:%s EPG log:%s.)\r\n", flog_enabled ? "ON":"OFF", epg_log_enabled? "ON":"OFF"));
	
	DVBCaptureDev *CDev = (DVBCaptureDev *) malloc(sizeof(DVBCaptureDev));
	memset( CDev, 0, sizeof(DVBCaptureDev) );
#ifdef STANDALONE
    const char* cdevname = jdevname;
#else
	const char* cdevname = (*env)->GetStringUTFChars(env, jdevname, NULL);
#endif
	strcpy(CDev->tag, "SAGE" );
	strcpy(CDev->devName, "/dev/dvb/");
	strcat(CDev->devName, cdevname);
#ifndef STANDALONE
	(*env)->ReleaseStringUTFChars(env, jdevname, cdevname);
#endif
	strcpy(CDev->frontendName, CDev->devName);
	strcat(CDev->frontendName, "/frontend0");
	strcpy(CDev->demuxName, CDev->devName);
	strcat(CDev->demuxName, "/demux0");
	strcpy(CDev->dvrName, CDev->devName);
	strcat(CDev->dvrName, "/dvr0");

	memset( &CDev->mutex1_scan_session, 0, sizeof(CDev->mutex1_scan_session) );//CDev->mutex1 = PTHREAD_MUTEX_INITIALIZER;
	memset( &CDev->mutex1_scan_data, 0, sizeof(CDev->mutex1_scan_data) );//CDev->mutex1 = PTHREAD_MUTEX_INITIALIZER;
	memset( &CDev->mutex1_push_data, 0, sizeof(CDev->mutex1_push_data) );//CDev->mutex1 = PTHREAD_MUTEX_INITIALIZER;

	flog(("Native.log", "DVB: devices %s %s %s.\r\n", CDev->frontendName,CDev->demuxName,CDev->dvrName));
	
	//make tuner device name, it's a unique name
	sprintf( CDev->tunerDeviceName, "adapter%d", getAdapterNum(CDev) );

	CDev->dmx_buffer_size = 10*188*1024;

	ReadDeviceCfg( CDev );

	if((CDev->frontendFd = open(CDev->frontendName,O_RDWR|O_NONBLOCK)) < 0)
	{
		flog(("Native.log", "DVB: failed open frontend %s (%d).\r\n", CDev->frontendName, CDev->frontendFd ));
		throwEncodingException(env, __LINE__);
		free(CDev);
		return 0;
	}

	if((CDev->demuxDevs[0] = open(CDev->demuxName,O_RDWR|O_NONBLOCK)) < 0)
	{
		flog(("Native.log", "DVB: failed open demux0 %s (%d).\r\n", CDev->demuxName, CDev->demuxDevs[0] ));
		throwEncodingException(env, __LINE__);
	}
		

	if((CDev->dvrFd = open(CDev->dvrName,O_RDONLY|O_NONBLOCK)) < 0) //|O_NONBLOCK
	{
		flog(("Native.log", "DVB: failed open dvr %s (fd:%d, errno:%d).\r\n", CDev->dvrName, CDev->dvrFd, errno ));
		throwEncodingException(env, __LINE__);
		close( CDev->frontendFd );
		close( CDev->demuxDevs[0] );
		free(CDev);
		return 0;
	}
	
		

	if ( (CDev->remuxer = (REMUXER*)OpenRemuxStream( REMUX_STREAM , &CDev->tune, 
                       	            MPEG_TS, MPEG_PS, NULL, NULL, OutputDump, CDev )  ) == NULL )
	{
		flog(("Native.log", "DVB: failed creating a Remuxer.\r\n" ));
		sysOutPrint(env, "DVB: FAILED creat remuxer.\r\n" );
		throwEncodingException(env, __LINE__);
		close( CDev->frontendFd );
		close( CDev->demuxDevs[0] );
		close( CDev->dvrFd );
		free(CDev);
		return 0;
	}
	SetDefaultAudioLanguage( CDev->remuxer, LANGUAGE_CODE( "eng" ) );

    // JFT TODO: Ask Qian if that's right place for that
    if(createCircBuffer(&CDev->capBuffer, CAPCIRCBUFFERSIZE)==0)
    {
        flog(("Native.log", "DVB: failed allocating circular buffer.\r\n" ));
        sysOutPrint(env, "DVB: FAILED allocating circular buffer.\r\n" );
        throwEncodingException(env, __LINE__);
        close( CDev->frontendFd );
        close( CDev->demuxDevs[0] );
        close( CDev->dvrFd );
        free(CDev);
        return 0;
    }

    CDev->capMutex = ACL_CreateMutex();

	if ( CDev->dmx_buffer_size > 0 )
		setDmxBufferSize( CDev, CDev->dmx_buffer_size );

	flog(("Native.log", "DVB: encoder is created CDev: 0x%lx, 0x%lx\r\n",
			        (unsigned long)CDev, (unsigned long)CDev->remuxer ));
	setOutputFormat( &CDev->channel, 1 );
	CDev->totalOutBytes = 0;
	CDev->totalProcessedBytes = 0;
	CDev->totalInputBytes = 0;
	CDev->lastOutputBytes = 0;
	CDev->channel.Dev = CDev;
	CDev->scanFilter = NULL;
	CDev->scanChannelEnabled = 0;
	CDev->paserEnabled = 0;
	openChannel( &CDev->channel, CDev );

	//CDev->channel.lockTimeout = LOCK_FRQ_TIMEOUT;
	LoadDebugCfg( &CDev->dbg );
	SetupEPGDump( CDev->remuxer, (DUMP)EPG_Dumper, (void*)CDev);
	SetupAVInfDump( CDev->remuxer, (DUMP)AVInf_Dumper, (void*)CDev );
	enableFrqTableUpdate( &CDev->channel );
	//SetPMTDataDump( CDev->remuxer, (void*)OnCamPMT, (void*) ptr );
	
	//if ( CDev->hard_demulex ) //ZQ reimplement later 
	//	SetPidTblDump( CDev->remuxer, (void*)OnHardDeumlex, (void*)CDev );

	if ( CDev->dbg.audio_ctrl&0x01 ) 
	{
	     DisableMultipleAudio( CDev->remuxer );
	     flog(( "Native.log", "DVB: multiple audio is disabled\r\n" ));
	}
	
	flog(( "Native.log", "DVB:Open device %s %s\r\n", CDev->frontendName, VERSION_TAG ));
	
	return PTR_TO_INT64(jlong, CDev);
}

/*
 * Class:     sage_DVBCaptureDevice
 * Method:    setInput0
 * Signature: (JIILjava/lang/String;II)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DVBCaptureDevice_setInput0
  (JNIEnv *env, jobject jo, jlong ptr, jint inputType, jint inputIndex,
	APISTRING tunerMode, jint countryCode, jint videoFormatCode)
{
	char tuner_mode[16]={0};
#ifdef DEBUGDVB
	//sysOutPrint(env, "DVB: SetInput Video Format:%d, Country:%d, InputType:%d\r\n", videoFormatCode, countryCode, inputType );
#endif
	if(!ptr) return JNI_FALSE;

	DVBCaptureDev *CDev = INT64_TO_PTR(DVBCaptureDev*,  ptr);
#ifdef STANDALONE
    const char* cform = tunerMode;
#else
	const char* cform = (*env)->GetStringUTFChars(env, tunerMode, NULL);
#endif
	strncpy( tuner_mode, cform, sizeof(tuner_mode) );
#ifndef STANDALONE
	(*env)->ReleaseStringUTFChars(env, tunerMode, cform);
#endif
	int isCable = (strcmp("Cable", tuner_mode ) == 0 )  ;
	int isHrc = (strcmp("HRC", tuner_mode ) == 0);
	int isOTAQAM = (strcmp("OTAQAM", tuner_mode ) == 0 )  ;
	int isUserDefined = (strcmp("UserDefined", tuner_mode ) == 0);
	int OutputFormat = (int)videoFormatCode;
	
	if(!ptr) return JNI_FALSE;
	if (  !strcmp( getTunerMode( &CDev->channel ), tuner_mode ) && 
	       getOutputFormat( &CDev->channel) == OutputFormat && 
	       getCountryCode( &CDev->channel ) == countryCode )
		return JNI_TRUE;
	
	// Set the frequency array based on the standard
#ifdef DEBUGDVB
	sysOutPrint(env, "DVB: SetInput TunerMode:%s %d\r\n", tuner_mode, countryCode );
#endif
	flog(( "Native.log", "DVB: SetInput (%s) on %s, country:%d file format:%s (%d)\r\n",
				tuner_mode, CDev->frontendName, countryCode, OutputFormat==0?"TS":"PS", OutputFormat ));
	
	setTunerMode( &CDev->channel , tuner_mode );
	setOutputFormat( &CDev->channel, OutputFormat );
	setCountryCode( &CDev->channel, countryCode );
	
 	struct dvb_frontend_info fe_info;
	char *type;
	int   ret;
 	if ( ioctl( CDev->frontendFd, FE_GET_INFO, &fe_info) >= 0 )
	{
		flog(( "Native.log", "DVB:tuner name:%s\r\n", fe_info.name ));
		switch(fe_info.type)
		{
			case FE_OFDM: type = "DVB-T"; break;
			case FE_QPSK: type = "DVB-S"; break;
			case FE_QAM:  type = "DVB-C"; break;
			case FE_ATSC: type = "ATSC"; break;
			default: type = ""; break;
		}
		flog(( "Native.log", "DVB:frq (%d-%d), rate(%d-%d) cap:0x%x\r\n", 
				fe_info.frequency_min, fe_info.frequency_max,
				fe_info.symbol_rate_min, fe_info.symbol_rate_max, fe_info.caps ));
		
	} else
	{
		return JNI_FALSE;
	}
	
	flog(( "Native.log", "DVB:Tuner type:%s\r\n", type ));
	if (countryCode <= 1 && fe_info.type != FE_OFDM && fe_info.type != FE_QPSK && fe_info.type != FE_QAM )
	{
		if (isCable || isHrc || isOTAQAM )
		{
			if ( isCable )
				setFreqType( &CDev->channel, 2 );
			else
			if ( isHrc ) 
				setFreqType( &CDev->channel, 3 );
			else
			if ( isOTAQAM )
				setFreqType( &CDev->channel, 1 );
				
			setSourceType( &CDev->channel, "QAM" );
			flog(( "Native.log", "DVB: sourceType:%s freqType:%d\r\n", getSourceType( &CDev->channel), getFreqType(&CDev->channel) ) );
			
		}
		else if ( isUserDefined )
		{
			//int ret = loadQAMFreqTable( )
			setFreqType( &CDev->channel, 4 );
			setSourceType( &CDev->channel, "QAM" );
			flog(( "Native.log", "DVB: sourceType:%s freqType:%d\r\n", getSourceType( &CDev->channel), getFreqType(&CDev->channel) ) );
		}
		else
		{
			setFreqType( &CDev->channel, 1 );
			setSourceType( &CDev->channel, "ATSC" );
			flog(( "Native.log", "DVB: sourceType:%s freqType:%d\r\n", getSourceType( &CDev->channel), getFreqType(&CDev->channel) ));
		}
	}
	else
	{
		
		if ( !strcmp( type, "ATSC" ) )
		{
			if ( isCable || isHrc || isOTAQAM )
			{
				if ( isCable )
					setFreqType( &CDev->channel, 2 );
				else
				if ( isHrc ) 
					setFreqType( &CDev->channel, 3 );
				else
				if ( isOTAQAM )
					setFreqType( &CDev->channel, 1 );
					
				setSourceType( &CDev->channel, "QAM" );
				flog(( "Native.log", "DVB: sourceType:%s freqType:%d\r\n", getSourceType( &CDev->channel), getFreqType(&CDev->channel)));
			} else
			{
				setFreqType( &CDev->channel, 1 );
				setSourceType( &CDev->channel, "ATSC" );
				flog(( "Native.log", "DVB: sourceType:%s freqType:%d\r\n", getSourceType( &CDev->channel), getFreqType(&CDev->channel)));
			}
		}
		else
		if ( !strcmp( type, "DVB-S" ) ) 
		{
			setSourceType( &CDev->channel, "DVB-S" );
		} else
		if ( !strcmp( type, "DVB-C" ) ) 
		{
			setSourceType( &CDev->channel, "DVB-C" );
		} else
		if ( !strcmp( type, "DVB-T" ) ) 
		{
			setSourceType( &CDev->channel, "DVB-T" );
		} else
		{
			setSourceType( &CDev->channel, "UNKOWN" );		
		}
			
	}
	
	if ( ( ret = loadScanTuningTable( &CDev->channel )  ) <= 0 )
	{
	    //sysOutPrint(env, "DVB: Load tuning frequency data failed (%d)\r\n", ret );
	    flog(( "Native.log", "DVB: Load tuning frequency data failed (%d)\r\n", ret ));
	}

	//ZQdisable output TS format recording
	//SelectOuputFormat( CDev->remuxer, OutputFormat ); //0:TS; 1:PS
	//flog(( "Native.log", "DVB: SourceType:%s\r\n", getSourceType( &CDev->channel) ));
	
	return JNI_TRUE;
}

JNIEXPORT APISTRING JNICALL Java_sage_DVBCaptureDevice_getBroadcastStandard0
		(JNIEnv *env, jobject jo, jlong ptr )
{

	DVBCaptureDev *CDev = INT64_TO_PTR( DVBCaptureDev*, ptr );
	if ( CDev == NULL )
	{
		flog(( "Native.log", "GetBroadcastStandard falied, CDev is null \r\n" ));
#ifdef STANDALONE
        return "";
#else
        return (*env)->NewStringUTF( env, "" );
#endif
	}
	
	flog(( "Native.log", "GetBroadcastStandard %s \r\n",  getSourceType( &CDev->channel) ));
#ifdef STANDALONE
	return getSourceType(&CDev->channel);
#else
    return (*env)->NewStringUTF( env, getSourceType( &CDev->channel ) );
#endif
}


/*
 * Class:     sage_DVBCaptureDevice
 * Method:    setupEncoding0
 * Signature: (JLjava/lang/String;J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DVBCaptureDevice_setupEncoding0
  (JNIEnv *env, jobject jo, jlong ptr, APISTRING jfilename, jlong circSize)
{
	if (ptr)
	{
		DVBCaptureDev *CDev =  INT64_TO_PTR(DVBCaptureDev *, ptr);
		CDev->circFileSize = (long) circSize;
		CDev->circWritePos = 0;

#ifdef STANDALONE
        const char* cfilename = jfilename;
#else
		const char* cfilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
#endif
		if (CDev->fd)
		{
			//drain data in buffer before close
			FlushOutBufferData( CDev );
			fclose(CDev->fd);
			flog(( "Native.log", "DVB: close file:0x%lx for a new recording.\r\n", CDev->fd ));
			CDev->fd = 0;
		}
		CDev->fd = fopen(cfilename, "wb");
		flog(( "Native.log", "DVB: open file:%s  0x%lx.\r\n", cfilename, CDev->fd ));
		
#ifdef DEBUGDVB
	sysOutPrint(env, "DVB: setup encoding %s.\r\n", cfilename);
#endif
#ifndef STANDALONE
		(*env)->ReleaseStringUTFChars(env, jfilename, cfilename);
#endif
		if (!CDev->fd)
		{
			throwEncodingException(env, __LINE__);
			return JNI_FALSE;
		}

        CDev->discardCount=0;
        CDev->capState=0;
        resetCircBuffer(&CDev->capBuffer);
        CDev->capThread = ACL_CreateThread(CaptureThread, CDev);
	
		return JNI_TRUE;
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_DVBCaptureDevice
 * Method:    switchEncoding0
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DVBCaptureDevice_switchEncoding0
  (JNIEnv *env, jobject jo, jlong ptr, APISTRING jfilename)
{
#ifdef DEBUGDVB
	sysOutPrint(env, "DVB: switch encoding.\r\n");
#endif

	// Change the output file to be this new file
	if (ptr)
	{
		DVBCaptureDev *CDev =  INT64_TO_PTR(DVBCaptureDev *,ptr);
#ifndef FILETRANSITION
		if (CDev->fd)
		{
			//drain data in buffer before close
			FlushOutBufferData( CDev );
			fclose(CDev->fd);
			flog(( "Native.log", "DVB: close file:0x%lx for switch to a new recording.\r\n", CDev->fd ));
			CDev->fd = 0;
		}
#endif
		// Open up the file we're going to write to
#ifdef STANDALONE
        const char* cfilename = jfilename;
#else
		const char* cfilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
#endif
#ifdef FILETRANSITION
		CDev->newfd = fopen(cfilename, "wb");
		flog(( "Native.log", "DVB: switch open file:%s  set to next file.\r\n", cfilename));
#ifndef STANDALONE
		(*env)->ReleaseStringUTFChars(env, jfilename, cfilename);
#endif
#else
		CDev->fd = fopen(cfilename, "wb");
		flog(( "Native.log", "DVB: switch open file:%s  0x%lx.\r\n", cfilename, CDev->fd ));
#ifndef STANDALONE
		(*env)->ReleaseStringUTFChars(env, jfilename, cfilename);
#endif
#endif
#ifdef FILETRANSITION
		if (!CDev->fd)
#else
		if (!CDev->newfd)
#endif
		{
			// This can be true in FILETRANSITION mode when newfd failed
			if (CDev->fd)
			{
				fclose(CDev->fd);
				CDev->fd = 0;
			}
			throwEncodingException(env, __LINE__/*sage_EncodingException_FILESYSTEM*/);
			return JNI_FALSE;
		}
#ifdef FILETRANSITION
		flog(( "Native.log", "DVB: switch file going into eatEncoderData0 until file switched.\r\n"));
		while(CDev->newfd)
		{
			Java_sage_DVBCaptureDevice_eatEncoderData0(env, jo, ptr);
		}
#endif
		pthread_mutex_lock( &CDev->mutex1_push_data );  //ZQ. if CDev->totalProcessedBytes has thread racing, we need turn on thread lock as DTVCHannel.c
		CDev->totalOutBytes = 0;
		CDev->totalProcessedBytes = 0;
		CDev->alignBytes = 0;
		CDev->expectedBytes = 0;
		CDev->lastOutputBytes = 0;
		pthread_mutex_unlock( &CDev->mutex1_push_data ); //ZQ. if CDev->totalProcessedBytes has thread racing, we need turn on thread lock

		return JNI_TRUE;
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_DVBCaptureDevice
 * Method:    closeEncoding0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DVBCaptureDevice_closeEncoding0
  (JNIEnv *env, jobject jo, jlong ptr)
{
#ifdef DEBUGDVB
	sysOutPrint(env, "DVB: close encoding.\r\n");
#endif
	if (ptr)
	{
		DVBCaptureDev *CDev =  INT64_TO_PTR(DVBCaptureDev *, ptr);
        if(CDev->capThread)
        {
            ACL_LockMutex(CDev->capMutex);
            CDev->capState=2;
            ACL_UnlockMutex(CDev->capMutex);
            sysOutPrint(env, "DVB: join capture thread\n");
            ACL_ThreadJoin(CDev->capThread);
            sysOutPrint(env, "DVB: capture thread stopped\n");
            ACL_RemoveThread(CDev->capThread);
            CDev->capThread=NULL;
        }
		if (CDev->fd)
		{
			flog(( "Native.log", "DVB: close file:0x%lx.\r\n", CDev->fd ));
			FlushOutBufferData( CDev );
			fclose(CDev->fd);
			CDev->fd = 0;
		}
		return JNI_TRUE;
	}
	else
		return JNI_FALSE;
}

/*
 * Class:     sage_DVBCaptureDevice
 * Method:    destroyEncoder0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_DVBCaptureDevice_destroyEncoder0
  (JNIEnv *env, jobject jo, jlong ptr)
{
	flog(( "Native.log", "DVB: Destroy encoder 0x%lx.\r\n", ptr ));
#ifdef DEBUGDVB
	sysOutPrint(env, "DVB: Destroy encoder.\r\n");
#endif
	if (ptr)
	{
		int i;
		DVBCaptureDev *CDev =  INT64_TO_PTR(DVBCaptureDev *,ptr);

        if(CDev->capThread)
        {
            ACL_LockMutex(CDev->capMutex);
            CDev->capState=2;
            ACL_UnlockMutex(CDev->capMutex);
            sysOutPrint(env, "DVB: join capture thread\n");
            ACL_ThreadJoin(CDev->capThread);
            sysOutPrint(env, "DVB: capture thread stopped\n");
            ACL_RemoveThread(CDev->capThread);
            CDev->capThread=NULL;
        }

		if ( CDev->dbg.dump_fd > 0 ) 
		{
			fclose( CDev->dbg.dump_fd );
			CDev->dbg.dump_fd = 0;
		}
		if ( CDev->dbg.source_fd > 0 ) 
		{
			fclose( CDev->dbg.source_fd );
			CDev->dbg.source_fd = 0;
		}
		
		if (CDev->frontendFd)
		{
			close(CDev->frontendFd);
			CDev->frontendFd = 0;
		}


		for ( i = 0; i<MAX_STREAMS; i++ )
		{
			if ( CDev->demuxDevs[i] > 0 )
			{
				close( CDev->demuxDevs[i] );
				CDev->demuxDevs[i] = 0;
			}
		}

		if (CDev->dvrFd)
		{
			close(CDev->dvrFd);
			CDev->dvrFd = 0;
		}

		if ( CDev->remuxer )
		{
			CloseRemuxStream( CDev->remuxer );
			CDev->remuxer = 0;
		}

		if ( CDev->scanFilter != NULL )
		{
			 ReleaseScanFilter( CDev->scanFilter );
			 CDev->scanFilter = NULL;
		}

		if (CDev->fd)
		{
			flog(( "Native.log", "DVB: close file:0x%lx.\r\n", CDev->fd ));
			fclose(CDev->fd);
			CDev->fd = 0;
		}


        free(CDev->capBuffer.data);
        ACL_RemoveMutex(CDev->capMutex);

		closeChannel( &CDev->channel );
		free(CDev);
	}
	
}


static int CaptureThread(void *data)
{
    DVBCaptureDev *x =  (DVBCaptureDev *)data;
    fd_set rfds;
    struct timeval tv;
    int maxfd;
    int retval;
    int state;
    struct sched_param scparam={0,};
    scparam.sched_priority=sched_get_priority_max(SCHED_FIFO);
    sched_setscheduler(0, SCHED_FIFO, &scparam);
    while(1)
    {
        // Remove locks and merge with the main loop?
        ACL_LockMutex(x->capMutex);
        state=x->capState;
        ACL_UnlockMutex(x->capMutex);
        if(state==2) break;

        FD_ZERO(&rfds);
        FD_SET(x->dvrFd, &rfds);
        maxfd=x->dvrFd;

        tv.tv_sec = 0;
        tv.tv_usec = 100000;
        retval = select(maxfd+1, &rfds, NULL, NULL, &tv);
        if (retval == -1)
            return -1;
        if(FD_ISSET(x->dvrFd, &rfds))
        {
            FD_CLR(x->dvrFd, &rfds);
            int numbytes;
            numbytes = read(x->dvrFd, x->buf2, BUFFERSIZE);
            ACL_LockMutex(x->capMutex);
            // Can we reenter record mode
            if(state==1 && freespaceCircBuffer(&x->capBuffer)>(CAPCIRCBUFFERSIZE/4))
            {
                flog(( "Native.log", "DVB: leaving discard mode lost %d bytes\r\n", x->discardCount));
                x->discardCount=0;
                state=0;
            }
            if(numbytes>0)
            {
                if(state==0 && freespaceCircBuffer(&x->capBuffer)>=numbytes)
                {
                    // We can add to our capture buffer
                    addCircBuffer(&x->capBuffer, x->buf2, numbytes);
                }
                else
                {
                    // Enter discard mode
                    if(state!=1) flog(( "Native.log", "DVB: entering discard mode\r\n"));
                    state=1; 
                    x->discardCount+=numbytes;
                }
            }
            else if(numbytes<0)
            {
                // JFT TODO: see with Qian if that's safe to call from that thread
                flog(( "Native.log", "DVB: eatEncodeData error errno : %d\r\n", errno));
            }
            if(x->capState!=2)
            {
                x->capState=state;
            }
            ACL_UnlockMutex(x->capMutex);
        }
    }
    return 0;
}

/*
 * Class:     sage_DVBCaptureDevice
 * Method:    eatEncoderData0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DVBCaptureDevice_eatEncoderData0
  (JNIEnv *env, jobject jo, jlong ptr)
{
	{
		DVBCaptureDev *CDev =  INT64_TO_PTR(DVBCaptureDev *, ptr);
		if ( CDev == NULL || strncmp( CDev->tag, "SAGE", 4 ) )
		{
			flog(( "Native.log", "DVB: Fatal Error, CDev is damaged. 0x%lx\r\n", CDev ));
			return 0;  
		}
		
		if ( CDev->dvrFd <= 0 )
		{
			//flog(( "Native.log", "DVB: driver is not ready in eatEncodeData 0x%0x.\r\n", CDev->dvrFd ));
			return 0;  //device is not open
		}
        int loopcount=0;
		int readMore = 1;
		struct pollfd fds;
		int ret;
		fds.fd = CDev->dvrFd;
		fds.events =  POLLIN|POLLPRI|POLLERR|POLLERR|POLLNVAL;
		int numbytes = 0;
		while (readMore)
		{
			//debug source overide data input from device
			if ( CDev->dbg.debug_source_mode && CDev->dbg.source_fd != NULL )
			{
				if ( CDev->dbg.debug_source_mode != 2 )
				{
					unsigned long ep, ep_epect;
					numbytes = fread( CDev->buf, 1, BUFFERSIZE, CDev->dbg.source_fd );
					if ( numbytes == 0 ) fseek( CDev->dbg.source_fd, 0, SEEK_SET );

					//keep stream at constant rate
					ep = msElipse( CDev->startTime );
					ep_epect = (CDev->totalInputBytes + numbytes)/2358;  //ATSC: rate 2358 bytes/sec
					if ( ep_epect > ep )
					{
						usleep( (ep_epect - ep)*1000 );
					}
					
				} 
				else
				{
					if ( ( ret = poll( &fds, 1, 10000 )) < 0  )
					{
						flog(( "Native.log", "ERROR: poll errno:%d\r\n", errno ));
						return 0;
					}
					if ( ( fds.revents & (POLLIN|POLLPRI )) == 0  ) return 0; 

					numbytes = read(CDev->dvrFd, CDev->buf, BUFFERSIZE);
					if ( numbytes < 0 )  
					{
						if (errno == EBUSY || errno == EAGAIN || errno == EWOULDBLOCK)
							usleep( 10000 ); 
						return 0;
					}
					if ( numbytes )
					{
						numbytes = fread( CDev->buf, 1, numbytes, CDev->dbg.source_fd );
						if ( numbytes == 0 ) fseek( CDev->dbg.source_fd, 0, SEEK_SET );
					}
					
				}
				readMore = 0;
				
			} else
			{
                ACL_LockMutex(CDev->capMutex);
                numbytes = usedspaceCircBuffer(&CDev->capBuffer);
                numbytes = numbytes > BUFFERSIZE ?  BUFFERSIZE : numbytes;
                getCircBuffer(&CDev->capBuffer, CDev->buf, numbytes);
                ACL_UnlockMutex(CDev->capMutex);
                if(numbytes==0)
                {
                    ACL_Delay(10);
                    loopcount++;
                    if(loopcount>100) return 0;
                }
                else
                    readMore = 0;

				/*
				if ( ( ret = poll( &fds, 1, 10000 ) ) < 0  )
				{
					flog(( "Native.log", "ERROR: poll errno:%d\r\n", errno ));
					return 0;
				}
				if (  (fds.revents & (POLLIN|POLLPRI )) == 0  ) return 0; 

				numbytes = read(CDev->dvrFd, CDev->buf, BUFFERSIZE);
				if (numbytes == -1)
				{
					flog(( "Native.log", "DVB: eatEncodeData error errno : %d\r\n", errno));
					fflush(stdout);        
					if (errno == EBUSY || errno == EAGAIN || errno == EWOULDBLOCK)
					{
						// Device isn't ready yet, just wait a bit and it will be.
						usleep(10000);
						return 0; //continue;
					}
					//throwEncodingException(env, __LINE__);
					return 0;
				}
				else
					readMore = 0;
                */
			}
		}

		unsigned long long prevOutBytes = CDev->totalProcessedBytes;
		if ( numbytes > 0 )
		{
			CDev->totalInputBytes += numbytes;
			SplitStream( CDev, (char*)CDev->buf, numbytes );
		}

		fflush(CDev->fd);
		unsigned long long count = CDev->totalProcessedBytes - prevOutBytes;
		return (jint) count;
	}
	
	return 0;
}

/*
 * Class:     sage_DVBCaptureDevice
 * Method:    eatEncoderData0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_DVBCaptureDevice_getOutputData0
  (JNIEnv *env, jobject jo, jlong ptr)
{

	DVBCaptureDev *CDev =  INT64_TO_PTR(DVBCaptureDev *, ptr);
	//unsigned long long count = CDev->totalOutBytes - CDev->lastOutputBytes;
	CDev->lastOutputBytes = CDev->totalOutBytes;
	return CDev->totalOutBytes;
}

/*
 * Class:     sage_DVBCaptureDevice
 * Method:    setChannel0
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DVBCaptureDevice_setChannel0
  (JNIEnv *env, jobject jo, jlong ptr, APISTRING jchan)
{
	if ( env == NULL || ptr == 0 )
		return JNI_FALSE;;
		
#ifdef STANDALONE
    const char* chann = jchan;
#else
    const char* chann = (*env)->GetStringUTFChars( env, jchan, NULL );
#endif
	bool ret;
	
	DVBCaptureDev *CDev =  INT64_TO_PTR(DVBCaptureDev *,ptr);
	sysOutPrint(env, "DVB: Channel:%s.\r\n", chann );

	
	if ( CDev->dbg.raw_dump_path[0] )
	{
		char name[256];
		if ( CDev->dbg.dump_fd > 0 ) 
			fclose( CDev->dbg.dump_fd );
		sprintf( name, CDev->dbg.raw_dump_path, chann );
		CDev->dbg.dump_fd = fopen( name, "wb" );
		CDev->dbg.dumped_bytes = 0;
		flog(("Native.log", "Raw data dumper set to:%s.\r\n", name ));
	}
	if ( CDev->dbg.debug_source[0] )
	{
		if ( CDev->dbg.source_fd > 0 )
			fclose( CDev->dbg.source_fd );
		CDev->dbg.source_fd = fopen( CDev->dbg.debug_source, "rb" );
		if ( CDev->dbg.source_fd > 0 )
			flog(("Native.log", "debug source file is open. %s mode:%d (%d).\r\n", CDev->dbg.debug_source,
						CDev->dbg.debug_source_mode, CDev->dbg.source_fd )); 
	}
	
	
	ret = tuneChannel( &CDev->channel, chann );
#ifndef STANDALONE
	(*env)->ReleaseStringUTFChars(env, jchan, chann);
#endif

	if ( !ret )
		return JNI_FALSE;
	
	CDev->totalOutBytes = 0;
	CDev->totalProcessedBytes = 0;
	CDev->totalInputBytes = 0;
	CDev->lastOutputBytes = 0;
	CDev->startTime = msElipse( 0 );
	//SelectOuputFormat( CDev->remuxer, getOutputFormat( &CDev->channel ) ); //0:TS; 1:PS
	int count = 0;
	while ( count<10 )
	{
		usleep(250000);
		int ret;
		if ( ( ret = checkLocked( CDev ) )< 0 )
		{
			sysOutPrint(env, "DVB couldn't read status.\r\n");
			return JNI_FALSE;
		}
		else 
		if ( ret > 0 )
		{
			sysOutPrint(env, "DVB tuning locked\r\n");
			return JNI_TRUE;
		}
	#ifdef DEBUGDVB
		sysOutPrint(env, "DVB couldn't get LOCK on try %d.\r\n", count);
		count+=1;                        
	#endif
	}

	return JNI_FALSE;
}

/* Class:     sage_DVBCaptureDevice
 * Method:    scanChannel0
 * Signature: (Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT APISTRING JNICALL Java_sage_DVBCaptureDevice_scanChannel0
	(JNIEnv *env, jobject jo, jlong ptr, APISTRING jnum, APISTRING jcountry, jint streamType )
{
	char  tuningString[64];
	char  countryRegion[200];
	char  *scanResult = NULL;
	char* deviceName="";
	DVBCaptureDev *CDev =  INT64_TO_PTR(DVBCaptureDev *,ptr);
#ifdef STANDALONE
	const char*  cnum = jnum;
    countryRegion[199]=0;
    strncpy(countryRegion, jcountry, 199);
#else
    const char*  cnum = (*env)->GetStringUTFChars(env, jnum, NULL);
    const jchar*  country = (*env)->GetStringChars(env, jcountry, NULL);
#endif
	pthread_mutex_lock( &CDev->mutex1_scan_session );
#ifndef STANDALONE
	TranslateJWideString( countryRegion, sizeof(countryRegion), (unsigned short*)country );
	//trime space
	int i = strlen(countryRegion);
	while( i-- > 0 )
	{
		if ( countryRegion[i] == ' ' ) countryRegion[i] = 0x0;
		else break;
	}
#endif
	
	strncpy( tuningString, cnum, sizeof(tuningString) );
#ifndef STANDALONE
	(*env)->ReleaseStringUTFChars(env, jnum, cnum);
	(*env)->ReleaseStringChars(env, jcountry, country);
#endif

	deviceName =  SageGetTunerDeviceName(CDev);	
	flog(( "Native.log", "Scan Channel:%s,  contry:%s, type:%s, tune:%s ...\r\n",
	                   tuningString, CDev->channel.countryRegion, getSourceType( &CDev->channel),
	                   tuningString ));
			   
			   
	scanResult = scanChannel( &CDev->channel, deviceName, tuningString,  countryRegion,  (int)streamType );
#ifdef STANDALONE
    pthread_mutex_unlock( &CDev->mutex1_scan_session );
    return scanResult;
#else
	jstring jretstr;
	if ( scanResult != NULL )
	{
		jretstr = (*env)->NewStringUTF(env, scanResult );
		free( scanResult );
	} else
		jretstr = (*env)->NewStringUTF(env, "" );

	pthread_mutex_unlock( &CDev->mutex1_scan_session );
	return jretstr;
#endif
}

/*
 * Class:     sage_DVBCaptureDevice
 * Method:    setEncoding0
 * Signature: (JLjava/lang/String;Ljava/util/Map;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DVBCaptureDevice_setEncoding0
  (JNIEnv *env, jobject jo, jlong ptr, APISTRING jencName, jobject encodePropsMap)
{
#ifdef DEBUGDVB
	sysOutPrint(env, "DVB SetEncoding\n");
#endif
	flog(( "Native.log", "SetEncoding\r\n" ));
	return JNI_TRUE;
}

/*
 * Class:     sage_DVBCaptureDevice
 * Method:    updateColors0
 * Signature: (JIIIII)[I
 */
JNIEXPORT jintArray JNICALL Java_sage_DVBCaptureDevice_updateColors0
  (JNIEnv *env, jobject jo, jlong ptr, jint brightness, jint contrast, jint huey, 
	jint saturation, jint sharpness)
{
	jint retColors[5];
	retColors[0]=0; retColors[1]=0; retColors[2]=0; retColors[3]=0; retColors[4]=0;
	jintArray rv = (*env)->NewIntArray(env, 5);
	(*env)->SetIntArrayRegion(env, rv, 0, 5, retColors);
	return rv;
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////
static int resetHardDemuxPid( DVBCaptureDev *CDev, int pid )
{
	struct dmx_pes_filter_params pes_filter_params={0};

	if ( CDev == NULL ) return 0;
	pes_filter_params.pid = pid; //8192;
	pes_filter_params.input = DMX_IN_FRONTEND;
	pes_filter_params.output = DMX_OUT_TS_TAP;
	pes_filter_params.pes_type = DMX_PES_OTHER;
	pes_filter_params.flags = DMX_IMMEDIATE_START;

	int i;
	for ( i = 1; i<MAX_STREAMS; i++ )
	{
		if ( CDev->demuxDevs[i] > 0 ) 
			close( CDev->demuxDevs[i] );
		CDev->demuxDevs[i] = 0;
		CDev->filterPids[i] = 0;
	}
	if ( CDev->demuxDevs[0] > 0 )
	{
		if ( ioctl(CDev->demuxDevs[0], DMX_SET_PES_FILTER, &pes_filter_params) <0 ) //reset auido
		{
			flog(( "Native.log", "DVB: ERROR: set up video filter.\r\n" ));
			return -1;
		}
		else
		{
			CDev->filterPids[0] = pid;
			flog(( "Native.log", "DVB: reset video hard demux pid:%x \r\n", pes_filter_params.pid ));
			return 1;
		}
	}
	
	return 0;
}

static void setDmxBufferSize( DVBCaptureDev *CDev, unsigned long size )
{
	int ret;
	if ( CDev->dvrFd <= 0 )
	{
		flog(( "Native.log", "Failed: set capture buffer size (DMX_SET_BUFFER_SIZE), dvr device isn't open \r\n" ));
		return;
	}

	ret = ioctl( CDev->dvrFd, DMX_SET_BUFFER_SIZE, size);
	if ( ret == 0 )
	{
		flog(( "Native.log", "Set capture buffer size(DMX_BUFFER_SIZE):%d \r\n", size ));
	} else
	{
		if ( ret == EBADF )
			flog(( "Native.log", "Failed: set capture buffer size(DMX_SET_BUFFER_SIZE):%d, invalid device handle \r\n", size ));
		else
		if ( ret == ENOMEM )
			flog(( "Native.log", "Failed: set capture buffer size(DMX_SET_BUFFER_SIZE):%d, The driver was not able to allocate a buffer of the requested size. \r\n", size ));
		else
			flog(( "Native.log", "Failed: set capture buffer size(DMX_SET_BUFFER_SIZE):%d, error:%d.\r\n", size, ret ));
	}

}

//hack way to empty drv buffer
static void emptyDmxBufferSize( DVBCaptureDev *CDev, unsigned long size )
{
	int ret;
	if ( CDev->dvrFd <= 0 )
	{
		flog(( "Native.log", "Failed: empty capture buffer. dmx buffer hacking, dvr device isn't open \r\n" ));
		return;
	}

	ret = ioctl( CDev->dvrFd, DMX_SET_BUFFER_SIZE, 16 );
	if ( ret == 0 )
	{
		if ( size == 0 )
			size = 2 * 4096;

		ret = ioctl( CDev->dvrFd, DMX_SET_BUFFER_SIZE, size );
		if ( ret == 0 )
			flog(( "Native.log", "empty capture buffere (DMX_BUFFER_SIZE) size:%d \r\n", size ));
	} 

	if ( ret ) 
    {
		if ( ret == EBADF )
			flog(( "Native.log", "Failed: empty capture buffer (EBADF).\r\n" ));
		else
		if ( ret == ENOMEM )
			flog(( "Native.log", "Failed: empty capture buffer (ENOMEM) size:%d.\r\n", size ));
		else
			flog(( "Native.log", "Failed: empty capture buffer, size:%d, error:%d.\r\n", size, ret ));
	}

}


static int setHardDemuxPid( DVBCaptureDev *CDev, int pid, int slot )
{
	struct dmx_pes_filter_params pes_filter_params;
	pes_filter_params.pid = pid;
	pes_filter_params.input = DMX_IN_FRONTEND;
	pes_filter_params.output = DMX_OUT_TS_TAP;
	pes_filter_params.pes_type = DMX_PES_OTHER;
	pes_filter_params.flags = DMX_IMMEDIATE_START;

	if ( slot >= MAX_STREAMS )
		return -1;
	if ( CDev->demuxDevs[slot] == 0 )
	{
		if((CDev->demuxDevs[slot] = open(CDev->demuxName,O_RDWR|O_NONBLOCK)) < 0)
		{
			flog(( "Native.log", "DVB: reopen demux Arrys failed errno:%d\r\n", errno ));
			return -1;
		}
	}	
	if ( CDev->demuxDevs[slot] > 0  )
	{
		if (ioctl(CDev->demuxDevs[slot], DMX_SET_PES_FILTER, &pes_filter_params) < 0)  
		{
			flog(( "Native.log", "DVB: Dumlex failed on pid:0x%x\r\n", pes_filter_params.pid ));
			return -1;
		}
		else
		{
			CDev->filterPids[slot] = pid;
			flog(( "Native.log", "DVB: Set hard demux %d  pid:0x%x(%d)\r\n", slot, pes_filter_params.pid,
			                                               pes_filter_params.pid ));
			return 1;
		}
	}
	flog(( "Native.log", "unkown error set Hard Deumlexi (pid:%d, on slot:%d)\r\n", pid, slot ));
	return 0;
}


int checkLocked( DVBCaptureDev *CDev )
{
	int status;
	if ( ioctl(CDev->frontendFd,FE_READ_STATUS,&status) < 0 )
	{
		return -1;
	}
	if((status & FE_HAS_LOCK))
	{
		return 1;
	}
	return 0;
}

/*
int drainDevBuffer( DVBCaptureDev *CDev )
{
	struct pollfd fds;
	int numbytes = 0;
	char buf[1024];
	fds.fd = CDev->dvrFd;
	fds.events =  POLLIN|POLLPRI|POLLERR|POLLERR|POLLNVAL;
	while ( 1 )
	{
		int ret;
		if ( ( ret = poll( &fds, 1, 10000 ) ) < 0  )
		{
			flog(( "Native.log", "ERROR: poll errno:%d\r\n", errno ));
				break;;
		}
		if (  (fds.revents & (POLLIN|POLLPRI )) == 0  ) break;
		ret = read( CDev->dvrFd, buf, sizeof(buf) );
		if ( ret <= 0 ) break;
		numbytes += ret;
	}
	return numbytes;
}
*/

int tuneATSCChannel( DVBCaptureDev *CDev, ATSC_FREQ* atsc, int dryTune ) 
{
	int i = 0, wait_count;
	unsigned long freq = 0;
	int physical_ch;
	fe_status_t status;

	if ( atsc == NULL ) 
	{
		flog(( "Native.log", "invalid ATSC FREQ\r\n" ));
		return -1;
	}
	physical_ch = atsc->physical_ch;

	if ( !dryTune )
	{
		while ( ATSC_BRCAST[i].fq != 0 && i<256 ) 
		{
			if ( ATSC_BRCAST[i].ch == physical_ch ) 
			{
				freq = ATSC_BRCAST[i].fq + 1750; 
				freq *= 1000;
				break;
			}
			i++;
		}
		
		if ( freq == 0 )		
		{
			flog(( "Native.log", "ATSC channel not found (ch:%u)\r\n", physical_ch ));
			return -1;
		}

		struct dvb_frontend_parameters feparams = {0};
		feparams.frequency=freq;
		feparams.u.vsb.modulation = VSB_8;

		/*
		struct dvb_frontend_parameters feparams_old = {0};
		if ( ioctl(CDev->frontendFd, FE_READ_STATUS, &status) >= 0 && (status&0x10)== 0x10 )
			ioctl(CDev->frontendFd, FE_GET_FRONTEND, &feparams_old);
		if ( feparams_old.frequency == feparams.frequency &&
			 feparams_old.u.vsb.modulation == feparams.u.vsb.modulation )
		{
			flog(( "Native.log", "Skip tuning due to the same tune params. freq:%d\r\n", feparams_old.frequency  ));
			goto loc_skip;
		}
		*/

		//close and reopen to drop data in device buffer, not have data leaking into the next recording
		//close( CDev->dvrFd );
		//CDev->dvrFd = -1;

		if ( ioctl(CDev->frontendFd, FE_SET_FRONTEND, &feparams) < 0 )
		{
			flog(( "Native.log", "tuneATSC failed (freq:%u)\r\n", freq ));
			return -1;
		}

		//flog(( "Native.log", "reopen dvr handle %s\r\n", CDev->dvrName ));
		//if((CDev->dvrFd = open(CDev->dvrName,O_RDONLY|O_NONBLOCK)) < 0)
		//{
		//	flog(( "Native.log", "Failed reopen dvr handle %s\r\n", CDev->dvrName ));
		//	return -1;
		//}

		//wait lock
		wait_count = 12*2;
		status = 0;
		while ( wait_count--)
		{
			//char buf[2048*4];
			//int  drop_bytes=0, max_read = 10;
			if ( ioctl(CDev->frontendFd, FE_READ_STATUS, &status) < 0 )
			{
				flog(( "Native.log", "failed getting ATSC front end status\r\n."  ));
				break;
			}
			else
			{
				if ( status & 0xf0 )
					break;
				flog(( "Native.log", "front end status: 0x%x.\r\n",  status ));
			}

			/*
			while ( max_read-- > 0 )
			{
				drop_bytes = read(CDev->dvrFd, buf, sizeof(buf) );
				if ( drop_bytes > 0 )
					flog(( "Native.log", "Drop %d bytes data in device buffer.\r\n", drop_bytes  ));
				else
					break;
			}
			*/

			usleep( 5000 );
		}

	    flog(( "Native.log", "front end status:%s (0x%x)\r\n", front_end_status_string(status), status ));

	}

	emptyDmxBufferSize( CDev, CDev->dmx_buffer_size );

	if ( CDev->hard_demulex == 1 &&  
	     atsc->video_pid != 0 && atsc->video_pid != 0xffff &&
	     atsc->audio_pid != 0 && atsc->audio_pid != 0xffff )
	{
		if ( atsc->video_pid != 0 && atsc->video_pid != 0xffff )
			setHardDemuxPid( CDev, atsc->video_pid, 1 );
		if ( atsc->audio_pid != 0 && atsc->audio_pid != 0xffff )
			setHardDemuxPid( CDev, atsc->audio_pid, 2 );
	}
	else
		resetHardDemuxPid( CDev, ALL_PIDS );


	flog(( "Native.log", "tune ATSC ch:%d frq:%d successed (drytune:%d)\r\n", atsc->physical_ch, freq, dryTune ));
	return (int)freq;
}

 
 int tuneQAMFrequency( DVBCaptureDev *CDev, QAM_FREQ* qam, int dryTune )
{
	struct dvb_frontend_parameters feparams = {0};
	fe_status_t status;
	unsigned long freq = 0;
	
	
	if ( qam == NULL ) return -1;
	if( qam->frequency == 0 )
	{
		flog(( "Native.log", "ATSC/QAM channel not found (ch:%u,%d)\r\n", qam->physical_ch,  qam->frequency ));
		return -1;
	}
	if ( !dryTune )
	{
		if ( qam->modulation == 0 )
			qam->modulation = QAM_256;

		//close and reopen to drop data in device buffer, not have data leaking into the next recording
//		close( CDev->dvrFd );
//		CDev->dvrFd = -1;

		freq = feparams.frequency = qam->frequency;
		feparams.u.vsb.modulation = qam->modulation;
		if (ioctl(CDev->frontendFd, FE_SET_FRONTEND, &feparams) < 0) 
		{
			flog(( "Native.log", "tuneQAM failed (freq:%u).\r\n", freq ));
			return -1;
		}

//		if((CDev->dvrFd = open(CDev->dvrName,O_RDONLY|O_NONBLOCK)) < 0)
//		{
//			flog(( "Native.log", "Failed reopen dvr handle %s\r\n", CDev->dvrName ));
//			return -1;
//		}

		//wait lock
		int wait_count = 12*2;
		status = 0;
		while ( wait_count--)
		{
			//char buf[2048*4];
			//int  drop_bytes=0, max_read = 10;
			if ( ioctl(CDev->frontendFd, FE_READ_STATUS, &status) < 0 )
			{
				flog(( "Native.log", "failed getting QAM front end status\r\n."  ));
				break;
			}
			else
			{
				if ( status & 0xf0 )
					break;
				flog(( "Native.log", "front end status: 0x%x.\r\n",  status ));
			}

			/*
			while ( max_read-- > 0 )
			{
				drop_bytes = read(CDev->dvrFd, buf, sizeof(buf) );
				if ( drop_bytes > 0 )
					flog(( "Native.log", "Drop %d bytes data in device buffer.\r\n", drop_bytes  ));
				else
					break;
			}
			*/

			usleep( 5000 );
		}
		emptyDmxBufferSize( CDev, CDev->dmx_buffer_size );
	    flog(( "Native.log", "front end status:%s (0x%x)\r\n", front_end_status_string(status), status ));
	}
	resetHardDemuxPid( CDev, ALL_PIDS );
	return (int)freq;
}

int tuneDVBTFrequency( DVBCaptureDev *CDev, DVB_T_FREQ* dvbt, int dryTune)
{
	struct dvb_frontend_parameters feparams = {0};
	fe_status_t status;
	//enum dvb_fe_bandwidth band;
	int band;
      //FE_OFDM:
      
      	if ( dvbt == NULL ) 
	{
		flog(( "Native.log", "Invalid DVB-T entry.\r\n" ));
		return -1;
	}
	
	if ( !dryTune )
	{
		feparams.frequency = dvbt->frequency; //in 10HMZ
		
		if ( dvbt->bandwidth == 6 ) band = BANDWIDTH_6_MHZ; else
		if ( dvbt->bandwidth == 7 ) band = BANDWIDTH_7_MHZ; else
		if ( dvbt->bandwidth == 8 ) band = BANDWIDTH_8_MHZ; else  band = BANDWIDTH_AUTO; 
		feparams.u.ofdm.bandwidth = band;

		switch ( dvbt->constellation ) {
		case  1: feparams.u.ofdm.constellation = QAM_16;   break;
		case  3: feparams.u.ofdm.constellation = QAM_64;   break;
		default: 
			feparams.u.ofdm.constellation = QAM_AUTO;  break;
		}

		if ( dvbt->code_rate_hp == 0xff )
			feparams.u.ofdm.code_rate_HP = FEC_AUTO;
		else
			feparams.u.ofdm.code_rate_HP = dvbt->code_rate_hp;
			
		if ( dvbt->code_rate_lp == 0xff )
			feparams.u.ofdm.code_rate_LP = FEC_AUTO;
		else
			feparams.u.ofdm.code_rate_LP = dvbt->code_rate_lp;
			
		if ( dvbt->trans_mode == 0xff )
			feparams.u.ofdm.transmission_mode = TRANSMISSION_MODE_AUTO;
		else
			feparams.u.ofdm.transmission_mode = dvbt->trans_mode;
			
		if ( dvbt->guard_interval == 0xff )
			feparams.u.ofdm.guard_interval = GUARD_INTERVAL_AUTO;
		else
			feparams.u.ofdm.guard_interval = dvbt->guard_interval;
			
		if ( dvbt->hierarchy == 0xff )
			feparams.u.ofdm.hierarchy_information = HIERARCHY_AUTO;
		else
			feparams.u.ofdm.hierarchy_information = dvbt->hierarchy;
		
		flog(( "Native.log", ">> frq:%d band:%d(%d) rate_HP:%d rate_LP:%d trans_mode:%d guard:%d hier:%d constellation-mod:%d(%d)\r\n",
			feparams.frequency, dvbt->bandwidth, feparams.u.ofdm.bandwidth, 
			feparams.u.ofdm.code_rate_HP, feparams.u.ofdm.code_rate_LP, 
			feparams.u.ofdm.transmission_mode, feparams.u.ofdm.guard_interval,
			feparams.u.ofdm.hierarchy_information, dvbt->constellation, feparams.u.ofdm.constellation ));

		//close and reopen to drop data in device buffer, not have data leaking into the next recording
		//close( CDev->dvrFd );
		//CDev->dvrFd = -1;

		if ( ioctl(CDev->frontendFd, FE_SET_FRONTEND, &feparams) < 0 ) 
		{
			char buf[256];
			flog(( "Native.log", "tuneDVBT failed (%s)\r\n.", makeDVBTFreqString( buf, sizeof(buf),  dvbt ) ));
			return -1;
		}

		//if((CDev->dvrFd = open(CDev->dvrName,O_RDONLY|O_NONBLOCK)) < 0)
		//{
		//	flog(( "Native.log", "Failed reopen dvr handle %s\r\n", CDev->dvrName ));
		//	return -1;
		//}

		//wait lock
		int wait_count = 12;
		status = 0;
		while ( wait_count--)
		{
			if ( ioctl(CDev->frontendFd, FE_READ_STATUS, &status) < 0 )
			{
				flog(( "Native.log", "failed getting DVB-T front end status\r\n."  ));
				break;
			}
			else
			{
				if ( status & 0xf0 )
					break;
				flog(( "Native.log", "front end status: 0x%x.\r\n",  status ));
			}

			usleep( 10000 );
		}

	}
	resetHardDemuxPid( CDev, ALL_PIDS );
	return 1;

}


int tuneDVBCFrequency( DVBCaptureDev *CDev, DVB_C_FREQ* dvbc, int dryTune )
{
	struct dvb_frontend_parameters feparams = {0};
	fe_status_t status;
	if (dvbc == NULL)
	{
		flog(( "Native.log", "Invalid DVB-C entry.\r\n" ));
		return -1;
	}

	if ( !dryTune )
	{
		feparams.frequency = dvbc->frequency; //in 10HMZ
		feparams.inversion = INVERSION_AUTO;
		feparams.u.qam.symbol_rate = dvbc->symbol_rate * 1000; 
		
		//mod: modualtion 16QAM:1,32QAM:2,64QAM:3,80QAM:4,96QAM:5,112QAM:6,128QAM:7,160QAM:8,192QAM:9,
		//224QAM:10,256QAM:11,320QAM:12,384QAM:13,448QAM:14,512QAM:15,640QAM:16,768QAM:17,896QAM:18,1024QAM:19,QPSK:20,BPSK:21,OQPSK:22,8VSB:23,16VSB:24,
		switch ( dvbc->modulation ) {
		case 23: feparams.u.qam.modulation = VSB_8 ;   break;
		case 24: feparams.u.qam.modulation = VSB_16;   break;
		case 19: feparams.u.qam.modulation = QPSK; break;
		case  1: feparams.u.qam.modulation = QAM_16;   break;
		case  2: feparams.u.qam.modulation = QAM_32;   break;
		case  3: feparams.u.qam.modulation = QAM_64;   break;
		case  7: feparams.u.qam.modulation = QAM_128;  break;
		case 11: feparams.u.qam.modulation = QAM_256;  break;
		default: 
			feparams.u.qam.modulation = QAM_AUTO;  break;
		}
		
		/* ** FEC rate:
		1 Indicates that the rate is 1/2.  
		2 Indicates that the rate is 2/3.  
		3 Indicates that the rate is 3/4.  
		4 Indicates that the rate is 3/5.  
		5 Indicates that the rate is 4/5.  
		6 Indicates that the rate is 5/6.  
		7 Indicates that the rate is 5/11. 
		8 Indicates that the rate is 7/8.  
		9 Indicates the maximum rate.   
	     	** FEC of inner/outer:
		1 The FEC is a Viterbi Binary Convolution.        
		2 The FEC is Reed-Solomon 204/188 (outer FEC). 
		3 The FEC is the maximum.                             
		*/
		switch( dvbc->fec_inner_rate ) {
		case 0:feparams.u.qam.fec_inner = FEC_NONE;		break;
		case 1:feparams.u.qam.fec_inner = FEC_1_2;	 	break;     
		case 2:feparams.u.qam.fec_inner = FEC_2_3;		break;     
		case 3:feparams.u.qam.fec_inner = FEC_3_4;		break;     
		case 4:feparams.u.qam.fec_inner = FEC_4_5;		break;     
		case 5:feparams.u.qam.fec_inner = FEC_5_6;		break;     
		case 6:feparams.u.qam.fec_inner = FEC_6_7;		break;     
		case 7:feparams.u.qam.fec_inner = FEC_7_8;		break;     
		case 8:feparams.u.qam.fec_inner = FEC_8_9;		break;     
		case 9:feparams.u.qam.fec_inner = FEC_AUTO; 		break;     
		default:  feparams.u.qam.fec_inner = FEC_AUTO;          break;     
		}
		
		flog(( "Native.log", ">> frq:%d rate:%d mode:%d fec_inner:%d\r\n",
			feparams.frequency, feparams.u.qam.symbol_rate, feparams.u.qam.modulation,
			feparams.u.qam.fec_inner ));

		//close and reopen to drop data in device buffer, not have data leaking into the next recording
		//close( CDev->dvrFd );
		//CDev->dvrFd = -1;

		if ( ioctl( CDev->frontendFd, FE_SET_FRONTEND, &feparams ) < 0)
		{
			flog(( "Native.log", "tuneDVBC failed\r\n" ));
			return -1;
		}

		//if((CDev->dvrFd = open(CDev->dvrName,O_RDONLY|O_NONBLOCK)) < 0)
		//{
		//	flog(( "Native.log", "Failed reopen dvr handle %s\r\n", CDev->dvrName ));
		//	return -1;
		//}

		//wait lock
		int wait_count = 12;
		status = 0;
		while ( wait_count--)
		{
			if ( ioctl(CDev->frontendFd, FE_READ_STATUS, &status) < 0 )
			{
				flog(( "Native.log", "failed getting DVB-C front end status\r\n."  ));
				break;
			}
			else
			{
				if ( status & 0xf0 )
					break;
				flog(( "Native.log", "front end status: 0x%x.\r\n",  status ));
			}

			usleep( 10000 );
		}


	}
	resetHardDemuxPid( CDev, ALL_PIDS );
	return 1;
}


static int setup_lnb( int frontend_fd, int sat_no, int voltage_18, int hiband );

int tuneDVBSFrequency( DVBCaptureDev *CDev, DVB_S_FREQ* dvbs, int dryTune )
{
	fe_status_t status;
	struct dvb_frontend_parameters feparams = {0};
	unsigned long freq;
	unsigned long ifreq;

	if (dvbs == NULL)
	{
		flog(( "Native.log", "Invalid DVB-S entry.\r\n" ));
		return -1;
	}

	if ( !dryTune )
	{
		//FE_QPSK:
		freq = dvbs->frequency; //in 10HMZ	
		unsigned int symbol_rate = dvbs->symbol_rate * 1000;

		{
			int hiband = (CDev->lnb.switch_val
					&& CDev->lnb.high_val
					&& freq > CDev->lnb.switch_val * 1000) ? 1 : 0;

			setup_lnb( CDev->frontendFd,
				       CDev->sat_no,
				       dvbs->polarisation == 2 ? 0 : 1,
				       hiband );

			usleep(50000);

			unsigned long freq_offset = (hiband ? CDev->lnb.high_val : CDev->lnb.low_val) * 1000;
			ifreq = abs(freq - freq_offset);

		}

		fe_code_rate_t fec;
		switch( dvbs->fec_inner_rate ) {
			case 1:fec = FEC_1_2;	 	break;
			case 2:fec = FEC_2_3;		break;
			case 3:fec = FEC_3_4;		break;
			case 4:fec = FEC_3_5;		break;
			case 5:fec = FEC_4_5;		break;
			case 6:fec = FEC_5_6;		break;
			case 7:fec = FEC_7_8;		break;
			case 8:fec = FEC_8_9;		break;
			default:  fec = FEC_AUTO;   break;
		}

		int delivery_system = QPSK;
		int modulation;
		switch (dvbs->modulation) {
			case 30:
				modulation = PSK_8;
				delivery_system = SYS_DVBS2;
				break;
			default: modulation = QPSK;
		}

		struct dtv_property dtv_properties[DTV_IOCTL_MAX_MSGS] = {0};
		int i = 0;
		dtv_properties[i].cmd = DTV_DELIVERY_SYSTEM; 	dtv_properties[i].u.data = delivery_system;
		dtv_properties[++i].cmd = DTV_FREQUENCY;		dtv_properties[i].u.data = ifreq;
		dtv_properties[++i].cmd = DTV_MODULATION;	    dtv_properties[i].u.data = modulation;
		dtv_properties[++i].cmd = DTV_SYMBOL_RATE; 		dtv_properties[i].u.data = symbol_rate;
		dtv_properties[++i].cmd = DTV_INNER_FEC; 		dtv_properties[i].u.data = fec;
		dtv_properties[++i].cmd = DTV_INVERSION;		dtv_properties[i].u.data = INVERSION_AUTO;
		dtv_properties[++i].cmd = DTV_TUNE;

		struct dtv_properties dtv_props;
		dtv_props.num = i + 1;
		dtv_props.props = dtv_properties;
		
		flog(( "Native.log", "DVB-S frq:%d, %d symbrate:%d fec-inner:%d(%d)  pol:%d sat:%d  mod:%d(%d)\r\n",
				dvbs->frequency, ifreq, symbol_rate,
					 fec, dvbs->fec_inner_rate, dvbs->polarisation, CDev->sat_no, dvbs->modulation, modulation ));

		if (ioctl(CDev->frontendFd, FE_SET_PROPERTY, &dtv_props) == -1) {
			flog(( "Native.log", "Failed setting front-end properties.(%s)\r\n", strerror(errno) ));
		}

/*		if((CDev->dvrFd = open(CDev->dvrName,O_RDONLY|O_NONBLOCK)) < 0)
		{
			flog(( "Native.log", "Failed reopen dvr handle %s (%s)\r\n", CDev->dvrName, strerror(errno) ));
			return -1;
		}
*/
		//wait lock
		int wait_count = 12;
		status = 0;
		while ( wait_count--)
		{
			if ( ioctl(CDev->frontendFd, FE_READ_STATUS, &status) < 0 )
			{
				flog(( "Native.log", "failed getting DVB-S front end status (%s).\r\n", strerror(errno)  ));
				break;
			}
			else
			{
				if ( status & 0xf0 )
					break;
			}

			usleep( 10000 );
		}
	}
	flog(( "Native.log", "front end status: 0x%x.\r\n",  status ));
	resetHardDemuxPid( CDev, ALL_PIDS );
	return 1;
}



struct diseqc_cmd {
	struct dvb_diseqc_master_cmd cmd;
	unsigned long wait;
};

static struct diseqc_cmd switch_cmds[] = {
	{ { { 0xe0, 0x10, 0x38, 0xf0, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xf2, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xf1, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xf3, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xf4, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xf6, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xf5, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xf7, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xf8, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xfa, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xf9, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xfb, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xfc, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xfe, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xfd, 0x00, 0x00 }, 4 }, 0 },
	{ { { 0xe0, 0x10, 0x38, 0xff, 0x00, 0x00 }, 4 }, 0 }
};


static int diseqc_send_msg (int fd, fe_sec_voltage_t v, struct diseqc_cmd **cmd,
		     fe_sec_tone_mode_t t, fe_sec_mini_cmd_t b)
{
	int err;

	if ((err = ioctl(fd, FE_SET_TONE, SEC_TONE_OFF)))
		return err;

	if ((err = ioctl(fd, FE_SET_VOLTAGE, v)))
		return err;

	usleep( 15 * 1000 );
	while (*cmd) {
		if ((err = ioctl(fd, FE_DISEQC_SEND_MASTER_CMD, &(*cmd)->cmd)))
			return err;

		usleep( (*cmd)->wait * 1000 );
		cmd++;
	}
	usleep( 15 * 1000 );

	if ((err = ioctl(fd, FE_DISEQC_SEND_BURST, b)))
		return err;

	usleep( 15 * 1000 );

	return ioctl(fd, FE_SET_TONE, t);
}


static int setup_lnb( int frontend_fd, int sat_no, int voltage_18, int hiband )
{
	struct diseqc_cmd* cmd[2];// = { NULL, NULL };
	int i = 4 * sat_no + 2 * hiband + (voltage_18 ? 1 : 0);

	flog(( "Native.log", "DiSEqc: sat_no:%d, vol:%s, %s (%d)\r\n",
                         sat_no, voltage_18 ? "18" : "13", hiband ? "hi" : "lo", i )); 

	if (i < 0 || i >= (int) (sizeof(switch_cmds)/sizeof(struct diseqc_cmd)))
	{
		flog(( "Native.log", "illvalid diseqc command\r\n" ));
		return -EINVAL;
	}

	cmd[0] = &switch_cmds[i];
	cmd[1] = NULL;
	return diseqc_send_msg( frontend_fd,
				i % 2 ? SEC_VOLTAGE_18 : SEC_VOLTAGE_13,
				cmd,
				(i/2) % 2 ? SEC_TONE_ON : SEC_TONE_OFF,
				(i/4) % 2 ? SEC_MINI_B : SEC_MINI_A );
}

static int get_lnb_data( DVBCaptureDev *CDev, char* LNB_name )
{
	int n, i;
	n = sizeof( lnbs )/sizeof(LNB);
	for ( i = 0; i<n; i++ )
	{
		if ( !strncmp( lnbs[i].name, LNB_name, strlen(lnbs[i].name) ) )
		{
			CDev->lnb = lnbs[1];
		    flog(( "Native.log", "LNB swich '%s' found.\r\n", LNB_name ));
			return 1;
		} 
	}
	
	//check if it's low-high-swith format
	if ( strstr( LNB_name, "-" ) )
	{
		const char* delima = "-:";
		char *token, *savedptr;
	
		token = (char*)strtok_r( LNB_name, delima, &savedptr );
		if ( token != NULL )
			CDev->lnb.low_val = atoi( token );
		token = (char*)strtok_r( savedptr, delima, &savedptr );
		if ( token != NULL )
			CDev->lnb.high_val = atoi( token );
		token = (char*)strtok_r( savedptr, delima, &savedptr );
		if ( token != NULL )
			CDev->lnb.switch_val = atoi( token );
			
		flog(( "Native.log", "SEQ LNB: low:%d high:%d switch:%d \r\n", CDev->lnb.low_val, CDev->lnb.high_val, CDev->lnb.switch_val ));
		return 1;
	}
	
	return 0;
}

static char* front_end_status_string( int status )
{
	static char status_buf[200];
	status_buf[0] = 0;
	if ( status == 0 ) return status_buf;

	if ( status & 0x01 )
		strcat( status_buf, "signal|");
	if ( status & 0x02 )
		strcat( status_buf, "DVB signal|");
	if ( status & 0x04 )
		strcat( status_buf, "FEC stable|");
	if ( status & 0x08 )
		strcat( status_buf, "SYNC|");
	if ( status & 0x10 )
		strcat( status_buf, "working ");
	if ( status & 0x20 )
		strcat( status_buf, "no lock ");
	if ( status & 0x40 )
		strcat( status_buf, "reinitialized ");
	return status_buf;
}


/////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
/*
// cfg line format: name    interger    string\n
*/
static const char* delimiter = " \t,;\r\n:=|'\"#";
static bool IsDelimiter( char ch )
{
	int i;
	if ( ch == 0 )	return true;
	for ( i = 0; delimiter[i]; i++ )
		if ( ch == delimiter[i] ) return true;
	return false;
}

static bool GetIntVal( char*p, const char* Name, unsigned long* pVal )
{
	char *s, *e;
	e = p;
	
	while( e != NULL && *e )
	{
		s = strstr( e, Name );
		if ( s == NULL ) return false;
		if ( ( (s > p && IsDelimiter(*(s-1)) ) || s == p ) && IsDelimiter( *(s+strlen(Name)) )  ) //is a token name
		{
			s += strlen(Name);
			while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
			if ( *s == '=' || *s == ':' )
			{
				s++;
				while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
				if ( *s )
				{
					*pVal = atoi( s );
					return true;
				}
			}
		}
		e = ++s;	
	}
	return false;
}

/*
static bool GetWordVal( char*p, const char* Name, unsigned short* pVal )
{
	char *s, *e;
	e = p;
	
	while ( e != NULL && *e )
	{
		s = strstr( e, Name );
		if ( s == NULL ) return false;
		if ( ((s > p && IsDelimiter(*(s-1))) || s == p ) && IsDelimiter( *(s+strlen(Name)) )  ) //is a token name
		{
			s += strlen(Name);
			while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
			if ( *s == '=' || *s == ':' )
			{
				s++;
				while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
				if ( *s )
				{
					*pVal = (unsigned short)atoi( s );
					return true;
				}
			}
		}
		e = ++s;
	}
	return false;
}
*/

static bool GetString( char*p, const char* Name, char* Str, int MaxLen )
{
	char *s, *e;
	e = p;
	
	while ( e != NULL && *e )
	{
		s = strstr( e, Name );
		if ( s == NULL ) return false;
		if ( ((s > p && IsDelimiter(*(s-1))) || s == p ) && IsDelimiter( *(s+strlen(Name)) )  )  //is a token name
		{
			s += (int)strlen(Name);
			while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
			if ( *s == '=' || *s == ':' )
			{
				s++;
				while( *s && ( *s == ' ' || *s == '\t' ) ) s++; //skip white space
				if ( *s )
				{
					int i=0;
					while ( i++<MaxLen && *s && *s != ' ' && *s != '\t' )
						*Str++ = *s++;
	
					*Str = 0x0; //terminator
					return true;
				}
			}
		}
		e = ++s;
	}
	return false;
}


int GetCfgVal( char* Str, char* Name, int MaxNameLen, int* Val, char* Ext, int MaxExtLen )
{
	const char* delima = " \t,;\r\n";
	char *token, *savedptr;
	if ( Name == NULL || Ext == NULL || MaxNameLen == 0 || MaxExtLen == 0 )
		return 0;
		
	Name[0]= *Val= Ext[0] = 0;;
	token = (char*)strtok_r( Str, delima, &savedptr );
	if ( token != NULL ) strncpy( Name, token, MaxNameLen );
	token = (char*)strtok_r( savedptr, delima, &savedptr );
	if ( token != NULL ) { *Val = atoi( token); strncpy( Ext, token, MaxExtLen ); }
	token = (char*)strtok_r( savedptr, delima, &savedptr );
	if ( token != NULL ) strncpy( Ext, token, MaxExtLen );
	return 1;
}

#define DEBUG_SERVER_CFG "debugserver.ini"
int LoadDebugCfg( DBG* dbg )
{
	char buf[512], name[64], ext[512-64];
	int  val;
	
	dbg->raw_dump_path[0] = 0;
	dbg->debug_source[0] = 0;
	FILE* fd = fopen( DEBUG_SERVER_CFG, "rt" );
	if ( fd == NULL )
	{	
		flog(( "Native.log", "%s is not exist \r\n", DEBUG_SERVER_CFG ));
		return 0;
	}
	flog(( "Native.log", "DVB:debug server configure '%s' is loaded\r\n", DEBUG_SERVER_CFG ));
	while ( !feof(fd) )
	{
		memset( buf, 0, sizeof(buf) );
		if ( fgets( buf, sizeof(buf), fd ) )
		{
			if ( buf[0] == '#' ) continue;			
			GetCfgVal( buf, name, sizeof(name), &val, ext, sizeof(ext) );
			if ( !strcmp( name, "rawdump" ) && val != 0 )
			{ 
				flog(( "Native.log", "DVB:dump raw data %d Mbyte into %s\r\n", val, ext ));
				strncpy( dbg->raw_dump_path, ext, sizeof(dbg->raw_dump_path) );
				if ( val == -1 ) dbg->dump_size = (unsigned long)val;
				else dbg->dump_size = (unsigned long)val*1024*1024;  //Mbytes
				
			} else
			if ( !strcmp( name, "debugsource" ) && val != 0 )
			{
				flog(( "Native.log",  "DVB:debug source from '%s' mode:%d\r\n", ext, val ));
				strncpy( dbg->debug_source, ext, sizeof(dbg->debug_source) );
				dbg->debug_source_mode = (int)val;
			}
			if ( !strcmp( name, "audio_ctrl" ) && val != 0 )
			{
				dbg->audio_ctrl = (unsigned long)val;
				flog(( "Native.log",  "DVB:debug audio ctrl: %d. %s\r\n", val,
				                     (dbg->audio_ctrl&0x01) ? "multiple audio disabled" : "" ));
				
			}
		}
	}
	
	fclose( fd );
	return 1;
}

static int ReadDeviceCfg( DVBCaptureDev *CDev )
{
	FILE *fp;
	char buf[512], vas[64];
	char file_name[128];

	if ( CDev == NULL )
		return 0;

	sprintf( file_name, "%s.cfg", getTunerDeviceName( CDev ) );
	fp = fopen( file_name, "r" );
	if ( fp == NULL )
	{
		flog(( "Native.log", "Device configure file \"%s\" isn't exist\r\n", file_name ));
		return 0;
	}

	flog(( "Native.log", "Device configure' %s' file was found\r\n", file_name ));
	while( !feof( fp ) )
	{
		char *p;
		int i;
		memset( buf, 0, sizeof(buf) );
		fgets( buf, sizeof(buf)-1, fp );
		
		//trim right space
		for ( i = _MIN( strlen(buf), sizeof(buf) ); i>0; i-- )
		  if ( buf[i-1] == ' ' || buf[i-1] == '\n' || buf[i-1]=='\r' ) buf[i-1] = 0;
		  else
		  	break;
		
		
		//skip white space
		for ( i = 0; i<sizeof(buf)-1; i++ )
			if ( buf[i] != ' ' || buf[i] !='\t' ) break;
		p = buf+i;
		
		//skip comment line
		if ( *p == '#' || *p == '!' ) 
			continue; 
			
		vas[0] = 0;
		if ( GetIntVal( p, "dmx_buffer_size", (unsigned long*)&CDev->dmx_buffer_size ) )
		{
			flog(( "Native.log", "READ: dmx_buffer_size:%d.\r\n", CDev->dmx_buffer_size ));	
		} else
		if ( GetIntVal( p, "hard_demulex", (unsigned long*)&CDev->hard_demulex ) )
		{
        	flog(( "Native.log", "READ: hard_demulex:%d\r\n", CDev->hard_demulex ));
		} else
		if ( GetIntVal( p, "sat_no", (unsigned long*)&CDev->sat_no ) )	
		{
        	flog(( "Native.log", "READ: sat_no:%d\r\n", CDev->sat_no ));
		} else
		if ( GetString( p, "LNB", vas, sizeof(vas)) )
		{
			if ( vas[0] )
			{
				if ( get_lnb_data( CDev, vas ) )
    				flog(( "Native.log", "READ: LNB '%s' OK.\r\n", vas ));
    			else
    			    flog(( "Native.log", "READ: LNB '%s; unkown.\r\n", vas ));
    				
    		} else
	    		flog(( "Native.log", "READ: LNB undefined %s\r\n" ));
		}
	}
		
	fclose( fp );
	return 1;	
}


/////////////////////////////////////////////////////////////////////////////////////////////////////////////
static int SplitStream( DVBCaptureDev *CDev, char* pData, int lDataLen )
{
	if ( CDev == NULL || CDev->remuxer == NULL || lDataLen == 0 )
		return 0;

	//dump data for debug
	//pthread_mutex_lock( &themutex );
	if ( CDev->dbg.dump_fd > 0 && CDev->dbg.dump_size > CDev->dbg.dumped_bytes ) 
	{
		fwrite( pData, 1, lDataLen, CDev->dbg.dump_fd );
		CDev->dbg.dumped_bytes += lDataLen;
		fflush( CDev->dbg.dump_fd );
	}
	//pthread_mutex_unlock( &themutex );
		
	if ( CDev->paserEnabled && CDev->scanChannelEnabled )
	{
		ProcessScan( CDev->scanFilter, (unsigned char*)pData, lDataLen );
	}

	if ( CDev->paserEnabled && !CDev->scanChannelEnabled )
	{
		int usedBytes;
		unsigned char* start_ptr = (unsigned char*)pData;
		int length = lDataLen;
		CDev->dumpBytes = 0;
		if ( CDev->expectedBytes )
		{
			int expectedBytes2;
			memcpy( CDev->alignBuffer+CDev->alignBytes, start_ptr, CDev->expectedBytes );
			usedBytes = PushRemuxStreamData( CDev->remuxer, CDev->alignBuffer, CDev->alignBytes+CDev->expectedBytes, &expectedBytes2 );
			if ( expectedBytes2 == 0 )
			{
				start_ptr   +=  CDev->expectedBytes;
				length -=  CDev->expectedBytes;
			}
		}

		usedBytes = PushRemuxStreamData( CDev->remuxer, start_ptr, length, &CDev->expectedBytes );
		CDev->alignBytes = length - usedBytes;
		ASSERT( CDev->expectedBytes+CDev->alignBytes <= sizeof(CDev->alignBuffer) );
		if ( CDev->alignBytes > 0 && CDev->alignBytes + CDev->expectedBytes <= sizeof(CDev->alignBuffer))
		{
			memcpy( CDev->alignBuffer, start_ptr+usedBytes, CDev->alignBytes );
		} else
		{
			//drop data, because of asking too many
			CDev->expectedBytes = 0;
		}

		pthread_mutex_lock( &CDev->mutex1_push_data ); //ZQ if CDev->totalProcessedBytes has thread racing, we need turn on thread lock
		CDev->totalProcessedBytes += usedBytes;
		CDev->totalOutBytes += CDev->dumpBytes;
		pthread_mutex_unlock( &CDev->mutex1_push_data );
	}

	return 1;
}

static int FlushOutBufferData( DVBCaptureDev *CDev )
{

	if ( CDev == NULL || CDev->remuxer == NULL )
		return 0;

	FlushRemuxStream( CDev->remuxer );

	return 1;

}

// NOTE: This could get bad start codes.
static int verifyPSBlock(unsigned char *data, int videotype)
{
    unsigned char b;
    unsigned int pos=0;
    int cur=0xFFFFFFFF;
    while(pos<2048)
    {
        b=data[pos];
        pos+=1;
        cur<<=8;
        cur|=b;
        if(videotype==2)
        {
            if((cur&0xFFFFFF00)==0x00000100)
            {
                /* video */
                if((b==0xB3))
                {
                    return 1;
                }
            }
        }
        else if(videotype==3)
        {
            if((cur&0xFFFFFFFF)==0x00000001)
            {
                if((pos+3)<2048)
                {
                    if( data[pos]==0x09 &&
                        data[pos+1]==0x10 &&
                        data[pos+2]==0x00)
                    {
                        return 1;
                    }
                }
            }
        }
        else
        {
            return 1;
        }
    }
    return 0;
}

// TODO: implement TS support for other source than HDPVR
// Videotype 1: mpeg1 2:mpeg2 3:h264
static int findTransitionPoint(unsigned char* data, int length, int flags, int videotype)
{
    int numbytes=length;

    // MPEG2-TS format
    if(flags==1)
    {
        int i, tsstart=-1, tsvidpacket=-1;//, seqstart=-1;
        // For the HDPVR our input is a tranport stream, we must find a valid start point
        // in the video pid 0x1011 of 00 00 01 09 10 00

        // First we try to locate ts packets
        for(i=0;i<numbytes;i++)
        {
            if(data[i]==0x47 && 
                (i+188)<numbytes && data[i+188]==0x47 &&
                (i+188*2)<numbytes && data[i+188*2]==0x47)
            {
                tsstart=i;
                break;
            }
        }

        // Second we find a ts packet with section start and pid 0x1011
        while((i+188)<=numbytes)
        {
            if(data[i]==0x47 &&
                data[i+1]==0x50 &&
                data[i+2]==0x11)
            {
                tsvidpacket=i;
                // Verify if that packet contains the magic sequence 00 00 00 01 09 10 00
                // If it does, the data up to the begining of this TS packet go in old file 
                // and the new data in the new file
                int j;
                for(j=4;j<188-7;j++)
                {
                    // NOTE: we could implement faster search but the number of
                    // matched packet that reach this point should be quite small...
                    if(data[i+j]==0x00 &&
                        data[i+j+1]==0x00 &&
                        data[i+j+2]==0x00 &&
                        data[i+j+3]==0x01 &&
                        data[i+j+4]==0x09 &&
                        data[i+j+5]==0x10 &&
                        data[i+j+6]==0x00)
                    {
                        // We have found the vid packet with the magic sequence, write that to old file
                        return tsvidpacket;
                    }
                }
            }
            i+=188;
        }
    }
    else // MPEG2-PS
    {
        // For the IVTV cards we must find a sequence start inside the video stream
        // we are looking for 00 00 01 B3
        int i=0, psstart=-1;
        // IVTV use 2K blocks
        // First locate the 00 00 01 BA block
        while(i<=numbytes-2048)
        {
            if(data[i]==0x00 && 
                data[i+1]==0x00 &&
                data[i+2]==0x01 &&
                data[i+3]==0xBA)
            {
                psstart=i;
                if(verifyPSBlock(&data[i], videotype))
                {
                    // We have found the sequence start
                    return psstart;
                }
                i+=2048;
            }
            else
            {
                i++;
            }
        }
    }
    return -1;
}

//callback function for SplitStream write out data
static int OutputDump( void* pContext, void* pDataBlk, int lBytes )
{
	DVBCaptureDev *CDev = (DVBCaptureDev*)pContext;
	OUTPUT_DATA   *pDataBuffer = (OUTPUT_DATA*)pDataBlk;
	unsigned char* pData = pDataBuffer->data_ptr;
	int numbytes = pDataBuffer->bytes;
	int writtenBytes = 0;

#ifdef FILETRANSITION
    // We want to switch to the new fd as soon as we get a good starting point
    if(CDev->newfd)
    {
         int transitionpos = findTransitionPoint(pData, numbytes,
            CDev->detectmode==2 ? 1 : 0, CDev->detecttype);
    	//int transitionpos = 0;
    	int bufSkip=transitionpos;
        if(transitionpos!=-1)
        {
            if (!CDev->circFileSize && CDev->fd>0)
            {
                // TODO: Do we care if this fails?
                writtenBytes = fwrite(pData, 1, bufSkip, CDev->fd);
				if ( writtenBytes < 0 ) writtenBytes = 0;
             }
            flog(( "Native.log", "transition found after %d bytes switching fd.\r\n", CDev->bytesTested));
            if ( CDev->fd>0 )
            	fclose(CDev->fd);
            CDev->fd=CDev->newfd;
            CDev->newfd=0;
        }
        else
        {
            CDev->bytesTested+=numbytes;
            // We might want to have different numbers or base it on bitrates
            if(CDev->bytesTested>8*1024*1024)
            {
                flog(( "Native.log", "transition limit reached, switching fd.\r\n"));
                if ( CDev->fd>0 )
                	fclose(CDev->fd);
                CDev->fd=CDev->newfd;
                CDev->newfd=0;
            }
			bufSkip = 0;
        }
        pData+=bufSkip;
        lBytes-=bufSkip;
    }
	CDev->dumpBytes += writtenBytes;    //there is a trouble here, I don't know dumpBytes is old or new file.
#endif
	if (CDev->circFileSize && CDev->fd>0 )
	{
		if (CDev->circWritePos == CDev->circFileSize)
		{
			// Wrap it now
			fseek(CDev->fd, 0, 0);
			CDev->circWritePos = 0;
		}
		if (CDev->circWritePos + numbytes <= CDev->circFileSize)
		{
			// No wrapping this time
			writtenBytes = fwrite(pData, 1, numbytes, CDev->fd);
			if ( writtenBytes < 0 )
			{
				return 0;
			}
			CDev->circWritePos += numbytes;
			fflush( CDev->fd );
		}
		else
		{
			writtenBytes = fwrite(pData, 1, CDev->circFileSize - CDev->circWritePos, CDev->fd);
			if ( writtenBytes < 0 )
			{
				return 0;
			}
			fflush( CDev->fd );
			int remBytes = numbytes - (CDev->circFileSize - CDev->circWritePos);
			// Wrap it now
			fseek(CDev->fd, 0, 0);
			CDev->circWritePos = remBytes;
			int bytes = fwrite(pData + (numbytes - remBytes), 1, remBytes, CDev->fd);
			if ( bytes < 0 )
			{
				return 0;
			}
			writtenBytes += bytes;
			fflush( CDev->fd );
		}

	}
	else
	{
		if (pData==NULL || CDev->fd<=0 || (writtenBytes = fwrite(pData, 1, numbytes, CDev->fd)) == -1)
		{
	        flog(( "Native.log", "error in OutputDump %X 0x%X.\r\n", pData, CDev->fd));
		    return 0;
		}
		if ( writtenBytes < 0 )
		{
		    flog(( "Native.log", "error fwrite, return %d 0x%X.\r\n", writtenBytes, CDev->fd));
			writtenBytes = 0;
		}

		if ( CDev->fd>0 )
		{
			if(CDev->syncBytes>5*1024*1024)
            {
                //fdatasync(fileno(CDev->fd));
                CDev->syncBytes=0;
            }
            CDev->syncBytes+=writtenBytes;
            fflush( CDev->fd );
		}
	}
	CDev->dumpBytes += writtenBytes;
	return 1;
}

char* getTunerDeviceName( DVBCaptureDev *CDev )
{
	if ( CDev == NULL ) return "";
	return CDev->tunerDeviceName;
}

int getAdapterNum( DVBCaptureDev *CDev )
{
  char *p;
  int  num = -1;
  p = strstr( CDev->devName, "adapter" );
  if ( p != NULL )
     num = atoi( p + 7 );

  return num;
}

long EPG_Dumper( void* context, void* mesg, short bytes )
{
  char src[64];
  int  num;
  DVBCaptureDev *CDev = (DVBCaptureDev*)context;
  if ( CDev == NULL )
  	return 0;

  if ( mesg == NULL || bytes < 0 || bytes >1024* 10 )
  {
	  flog(( "Native.log", "ERROR: a invalid EPG message 0x%x bytes:%d, dropped.\r\n", mesg, bytes ));
	  return 0;
  }

  num = getAdapterNum( CDev );
  sprintf( src, "adapter%d-0", num);
  //flog(( "Native.log", "%s<-%s\r\n", src, mesg );

  if ( epg_log_enabled )
	  flog(( "epg_scan.log", "%s->%s\r\n", src, mesg ));

  postMessage( src, (char*)mesg, EPG_MSG_TYPE, 100 );
  return 1;
}

long AVInf_Dumper( void* context, void* mesg, short bytes )
{
  char src[64];
  int num;
  DVBCaptureDev *CDev = (DVBCaptureDev*)context;

  if ( CDev == NULL || strncmp( CDev->tag, "SAGE", 4 ) )
  {
	flog(( "Native.log", "DVB: Fatal Error, CDev is damaged. 0x%lx\r\n (line:%d)", CDev, __LINE__ ));
	return 0;  //device is not open
  }

  if ( CDev == NULL )
	  return 0;
  
  num = getAdapterNum( CDev );
  sprintf( src, "adapter%d-0", num);
  //sprintf( src, "%s-0", getTunerDeviceName(CDev) );

#ifdef TRANSITION

#endif
  //sprintf( src, "%s", CDev->devName );
  flog(( "Native.log", "AVINF:%s<-%s\r\n", src, mesg ));
  postMessage( src, (char*)mesg, AV_INF_TYPE, 100 );
  
  return 1;
}

long OnHardDeumlex( void* context, short bytes, void* mesg )
{
/*
  PID_INF *pid_inf = (PID_INF*)mesg;
  PID_TBL *pid_tbl = pid_inf->pids;
  DVBCaptureDev *CDev = (DVBCaptureDev*)context;
  int i,j, num; 

  if ( CDev == NULL || strncmp( CDev->tag, "SAGE", 4 ) )
  {
	flog(( "Native.log", "DVB: Fatal Error, CDev is damaged. 0x%lx\r\n (line:%d)", CDev, __LINE__ ));
	return 0;  
  }

  if ( CDev == NULL || bytes == 0 || mesg == NULL )
	  return 0;
  num = bytes/sizeof(PID_INF);

  j = 1;
  //if hard demux is a full stream demux, need to overwrit, need to overwritee
  if ( CDev->filterPids[0] == 0x2000 ) 	j = 0;

  for ( i = 0; i<num && pid_tbl[i].pid ; i++ )
  {

	if ( pid_tbl[i].pid > 0 && i < MAX_STREAMS && i<pid_inf->pidNum )
	{
		int k;
		//check if pid is on filter 
		for ( k = 0; k<MAX_STREAMS &&  CDev->filterPids[k]; k++ )
			if ( CDev->filterPids[k] == pid_tbl[i].pid )
				break;
		if ( CDev->filterPids[k] == pid_tbl[i].pid )
		{
			flog(( "Native.log", "DVB: pid:0x%x is on hard demulex filter (slot:%d), skip.\r\n", pid_tbl[i].pid, k));
			continue;
		}

		setHardDemuxPid( CDev, pid_tbl[i].pid, j );
		j++;
	}

  }

*/
	//setHardDemuxPid( CDev, pid_tbl[i].pid, j );
  return 0;

}


unsigned long msElipse( unsigned long start_time )
{
	unsigned long ms;
	struct timeval tv;
	static struct timezone tz={ 0, 0 };
	gettimeofday( &tv, &tz );
	ms = tv.tv_sec * 1000 + tv.tv_usec/1000;
	return (ms - start_time);
	//clock_gettime( CLOCK_REALTIME, &ns );
	
	
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////
static JavaVM* vmBuf = 0;
static jsize numVMs;
static jclass msgMgrClass = 0;
static jmethodID postMsgMeth = 0;
static jclass msgClass = 0;
static jmethodID msgConstructor = 0;
static unsigned long msg_id = 0;

//post message into Java code
#ifdef STANDALONE
void postMessage( char* pSrc, char* pData, int MsgType, int Priority )
{
    fprintf(stderr, "postMessage %08X %08X %d %d\n",
            pSrc, pData, MsgType, Priority);
}
#else
void postMessage( char* pSrc, char* pData, int MsgType, int Priority )
{
	JNIEnv* env;

	//we sould have a lock here, for mutiple thread
	if (!vmBuf)
	{
		if (JNI_GetCreatedJavaVMs(&vmBuf, 1, &numVMs))
		{
			return;
		}
	}

	jint threadState = (*vmBuf)->GetEnv( vmBuf, (void**)&env, JNI_VERSION_1_2 );
	if (threadState == JNI_EDETACHED)
	{
		(*vmBuf)->AttachCurrentThread(vmBuf, (void**)&env, NULL);
	}

	if ( !msgMgrClass )
	{
		msgMgrClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "sage/msg/MsgManager"));
		if (msgMgrClass)
			postMsgMeth = (*env)->GetStaticMethodID(env, msgMgrClass, "postMessage", "(Lsage/msg/SageMsg;)V");
		msgClass = (jclass)((*env)->NewGlobalRef(env, (*env)->FindClass(env, "sage/msg/SageMsg")));
		if (msgClass)
			msgConstructor = (*env)->GetMethodID(env, msgClass, "<init>", "(ILjava/lang/Object;Ljava/lang/Object;I)V");
	}

	int dataLen = strlen(pData);
	jbyteArray javaData = (*env)->NewByteArray(env, dataLen);
	(*env)->SetByteArrayRegion(env, javaData, 0, dataLen, (const jbyte*)pData);
	jobject msgObj = (*env)->NewObject(env, msgClass, msgConstructor, MsgType, (*env)->NewStringUTF(env, pSrc), javaData, Priority );
	(*env)->CallStaticVoidMethod( env, msgMgrClass, postMsgMeth, msgObj );
	msg_id++;

	if ( threadState == JNI_EDETACHED )
		(*vmBuf)->DetachCurrentThread(vmBuf);
}
#endif
/////////////////////////////////////////////////////////////////////////////////////////////////////////////

//export API
int SageTuneATSCChannel( void* Capture, ATSC_FREQ* atsc, int dryTune )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	return tuneATSCChannel( CDev,  atsc, dryTune );
}

int SageTuneQAMFrequency( void* Capture, QAM_FREQ* qam, int dryTune )
{
	DVBCaptureDev *CDev = Capture;
	QAM_FREQ dvb_qam=*qam;
	//QAM modualation follows BDA standard, convert to DVB defination in Linux
	switch ( qam->modulation ) {
	case 1: dvb_qam.modulation = QAM_16;  break;
	case 2: dvb_qam.modulation = QAM_32;  break;
	case 3: dvb_qam.modulation = QAM_64;  break;
	case 7: dvb_qam.modulation = QAM_128; break;
	case 11:dvb_qam.modulation = QAM_256; break;
	case 20:dvb_qam.modulation = QPSK; break;
	case 23:dvb_qam.modulation = VSB_8; break;
	case 24:dvb_qam.modulation = VSB_16; break;
	default:
		dvb_qam.modulation = QAM_AUTO;  
		flog(( "Native.log", "the QAM modualtion %d is not defined, default to QAM_AUTO.\r\n", qam->modulation ));
		break;
	}
	if ( CDev == NULL ) return -1;
	return tuneQAMFrequency( CDev, &dvb_qam, dryTune );
}
 

int SageTuneDVBTFrequency( void* Capture, DVB_T_FREQ* dvbt, int dryTune )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	return tuneDVBTFrequency( CDev, dvbt, dryTune );
}

int SageTuneDVBCFrequency( void* Capture, DVB_C_FREQ* dvbc, int dryTune )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	return tuneDVBCFrequency( CDev, dvbc, dryTune );
}

int SageTuneDVBSFrequency( void* Capture, DVB_S_FREQ* dvbs, int dryTune )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	return tuneDVBSFrequency( CDev, dvbs, dryTune );
}

int SageCheckLocked( void* Capture )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return false;
	return checkLocked( CDev );
}

char*  SageGetTunerDeviceName( void* Capture )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return "";
	return getTunerDeviceName( CDev );
}

int SageSelectOuputFormat( void* Capture, int format ) //0:TS; 1:PS
{
	flog(( "Native.log", "Set output format %d, (Disabled)\r\n", format ));//zq
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	if ( format == 0 )
	{
		CDev->outputFormat = MPEG_TS;
	}
	else
	if ( format == 1 )
	{
		CDev->outputFormat = MPEG_PS;
	}
	return format;
}

int SageTVLockTSChannel(void* Capture, void* pTune, int reset)
{
	flog(( "Native.log", "Lock TS channel\r\n" ));//zq
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;

	ResetRemuxStream( CDev->remuxer );
	EnableTSPSI( CDev->remuxer );
	//DisabeTSPtsFix( pRemuxer );
	SetupRemuxStreamTune( CDev->remuxer, (TUNE*)pTune );
	CDev->tune = *((TUNE*)pTune);
	return 1;
}

int SageChangeCAMChannel( void* Capture, int program )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	//...
	return 1;
}

void SageEnableCAM( void* Capture )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return ;
	//...
}

void SageDisableCAM( void* Capture )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return ;
	//...
}

void SageStartParser( void* Capture )
{
	flog(( "Native.log", "Start Parser\r\n" ));
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return ;
	CDev->paserEnabled = 1;
}

void SageStopParser( void* Capture )
{
	flog(( "Native.log", "Stop Parser\r\n" ));
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return ;
	CDev->paserEnabled = 0;
}


int SageTVScanChannel( void* Capture, void* pTune )
{
	flog(( "Native.log", "Scan TS channel\r\n" ));
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	if ( CDev->scanFilter != NULL )
		return 0;

	CDev->scanFilter = (SCAN_FILTER*)CreateScanFilter();
	StartChannelScan( CDev->scanFilter, pTune );

	unsigned short program;
	program =  getProgramNum( pTune );
	if ( program > 0 )
		SageChangeCAMChannel( Capture,  program );
	CDev->scanChannelEnabled = 1;
	return 1;
}

void SageTVStopScan( void* Capture )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return;
	if ( CDev->scanFilter != NULL )
		return;
	CDev->scanChannelEnabled = 0;
	StopChannelScan( CDev->scanFilter );
}

int SageTVReleaseScanChannel( void* Capture )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	if ( CDev->scanFilter == NULL )
		return 0;
	pthread_mutex_lock( &CDev->mutex1_scan_data );
	CDev->scanChannelEnabled = 0;
	StopChannelScan( CDev->scanFilter );
	ReleaseScanFilter( CDev->scanFilter );
	CDev->scanFilter = NULL;
	pthread_mutex_unlock( &CDev->mutex1_scan_data );
	return 1;
}

//int SageTVScanChannelState( void* Capture, int* pScanState, int* pFoundChannelNum, int nClock )
int SageTVScanChannelState( void* Capture, int* pScanState, int* pFoundChannelNum, int nClock )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	ASSERT( CDev->scanFilter != NULL );
	//if ( nClock > 0 )
	//	ScanChannelTimeClock( CDev->scanFilter, nClock*100 );
	*pScanState = ScanChannelState( CDev->scanFilter );
	*pFoundChannelNum = ScanChannelNum( CDev->scanFilter );
	return pScanState > 0;

}

int SageTVScanChannelList( void* Capture, void** ppChannelList )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	ASSERT( CDev->scanFilter != NULL );
	*ppChannelList = GetScanChannelList( CDev->scanFilter );
	return 1;
}

int SageTVScanTuneList( void* Capture, void** ppNit )
{
	DVBCaptureDev *CDev = Capture;
	if ( CDev == NULL ) return -1;
	ASSERT( CDev->scanFilter != NULL );
	*ppNit = (void*)GetScanTuneList( CDev->scanFilter );
	return 1;
}

int  SageGetSatelliteTble( void* Capture, SAT_NAME *sat_name, int max_sat )
{
    //it's for DiSeqC, to be implemented soon.
	return 0;
}

void SageDelay( void* Capture, uint32_t ms )
{
 	usleep( ms );
}
/////////////////////////////////////////////////////////////////////////////////////////////////////////////



JNIEXPORT jstring JNICALL Java_sage_DVBCaptureDevice_getCardModelUIDForDevice
  (JNIEnv *env, jclass c, jstring jdevName)
{
    jstring jcardname=NULL;
    char fname[1024];
    char buf1[1024];
    char buf2[1024];
    char frontendName[256];
    fname[1023]=0;
    buf1[1023]=0;
    buf2[1023]=0;
    frontendName[255]=0;
    int frontendFd;
    int multipleIndex=0; // set to 2 if there is more than one card and this is the 2nd ...
    ssize_t retval;
    struct dvb_frontend_info fe_info;
    const char* cdevname = (*env)->GetStringUTFChars(env, jdevName, NULL);

    if(strlen(cdevname)<8)
    {
        (*env)->ReleaseStringUTFChars(env, jdevName, cdevname);
        return NULL;
    }

    int devicenum=atoi(cdevname+7);
    (*env)->ReleaseStringUTFChars(env, jdevName, cdevname);

    // We need to get the link from /sys/class/dvb/dvbX.demu0/device
    // If it contains usb then we try to read the serial number from that directory
    // If it contains pci then we get the last element of the path as uid

    snprintf(fname,1023, "/sys/class/dvb/dvb%d.demux0/device",
        devicenum);

    retval = readlink(fname, buf1, 1023);
    if(retval<0)
    {
        return NULL;
    }
    buf1[retval]=0;

    snprintf(frontendName, 255, "/dev/dvb/adapter%d/frontend0", devicenum);

    if((frontendFd = open(frontendName,O_RDWR|O_NONBLOCK)) < 0)
    {
        flog(("Native.log", "DVB: failed open frontend %s (%d).\n", frontendName, 
            frontendFd ));
        return NULL;
    }

    if(ioctl(frontendFd, FE_GET_INFO, &fe_info)<0)
    {
        flog(("Native.log", "DVB: failed to get fe info (%s).\n", frontendName));
        close(frontendFd);
        return NULL;
    }

    close(frontendFd);
    frontendFd=-1;

    // See if there are multiple frontend on this device
    {
        DIR *dirhandle;
        int minindex=0;
        int frontendCount=0;
        struct dirent* direntry;
        snprintf(fname,1023, "/sys/class/dvb/dvb%d.demux0/device/dvb",
            devicenum);
        if((dirhandle=opendir(fname))!=NULL)
        {
            while((direntry=readdir(dirhandle))!=NULL)
            {
                if(strstr(direntry->d_name, "frontend")!=NULL)
                {
                    int index=atoi(direntry->d_name+8);
                    if(frontendCount==0) minindex=index;
                    if(minindex>index) minindex=index;
                    frontendCount+=1;
                }
            }
            closedir(dirhandle);
        }
        if(frontendCount>1)
        {
            multipleIndex=devicenum-minindex+1;
        }
    }
    if(strstr(buf1, "usb")!=NULL)
    {
        FILE *extrainfo;
        // This is a usb device, try to get serial number
        snprintf(fname,1023, "/sys/class/dvb/dvb%d.demux0/device/serial",
            devicenum);
        extrainfo=fopen(fname,"r");
        if(extrainfo==NULL)
        {
            snprintf(fname,1023, "/sys/class/dvb/dvb%d.demux0/device/../serial",
                devicenum);
            extrainfo=fopen(fname,"r");
        }
        if(multipleIndex>1)
        {
            snprintf(buf2, 1023, "Digital %.127s #%d ",fe_info.name, multipleIndex);
        }
        else
        {
            snprintf(buf2, 1023, "Digital %.127s ",fe_info.name);
        }
        if(extrainfo!=NULL)
        {
            fgets(buf2+strlen(buf2), 1023-strlen(buf2), extrainfo);
            // remove any /r/n
            while(strlen(buf2)>0 && 
                ( buf2[strlen(buf2)-1]=='\r' || buf2[strlen(buf2)-1]=='\n' ))
            {
                buf2[strlen(buf2)-1]=0;
            }
            fclose(extrainfo);
        }
        else
        {
            snprintf(buf2+strlen(buf2), 1023-strlen(buf2), "%d", devicenum);
        }
    }
    else
    {
        // Get the last part of the buf2 path
        if(strrchr(buf1, '/')!=NULL)
        {
            if(multipleIndex>1)
            {
                snprintf(buf2, 1023, "Digital %.127s %s #%d",fe_info.name, strrchr(buf1, '/')+1,
                    multipleIndex);
            }
            else
            {
                snprintf(buf2, 1023, "Digital %.127s %s",fe_info.name, strrchr(buf1, '/')+1);
            }
        }
        else
        {
            snprintf(buf2, 1023, "Digital %.127s %d",fe_info.name, devicenum);
        }
    }

    flog(( "Native.log", "CardModelUIDForDevice:%s", buf2 ));
    jcardname = (*env)->NewStringUTF(env,buf2);
    return jcardname;
}

JNIEXPORT jint JNICALL Java_sage_DVBCaptureDevice_getSignalStrength0
  (JNIEnv *env, jobject jo, jlong ptr)
{
    DVBCaptureDev *CDev = INT64_TO_PTR(DVBCaptureDev *, ptr);
    int32_t snr;
    if (CDev && ioctl(CDev->frontendFd, FE_READ_SNR, &snr) >= 0) {
      // The SignalStrength is expected to be in the 0-100 range while the SNR
      // is usually provided in cB, given the expected range we can divide by 5.
      return snr / 5;
    }
    return 0;
}

#ifdef STANDALONE
// Test applications for recording

int main(int argc, char **argv)
{
    // 0 is checked by some functions
    JNIEnv *denv=(JNIEnv *)1;
    jobject dobj;
    jlong encoder;

    if(argc<4)
    {
        fprintf(stderr, "Usage: dvbtest <device> <channel> <outfile>\n");
        exit(1);
    }

    encoder=Java_sage_DVBCaptureDevice_createEncoder0(denv, dobj, argv[1]);

    if(encoder==0)
    {
        fprintf(stderr, "Couldn't create encoder for %s\n",argv[1]);
        exit(1);
    }

    if(!Java_sage_DVBCaptureDevice_setInput0(denv, dobj, encoder, 0, 0,
        "ATSC", "USA", 1))
    {
        fprintf(stderr, "Error setting input");
        exit(1);
    }

    if(!Java_sage_DVBCaptureDevice_setChannel0(denv, dobj, encoder, argv[2]))
    {
        fprintf(stderr, "Couldn't set channel %s\n",argv[2]);
        exit(1);
    }

    if(!Java_sage_DVBCaptureDevice_setupEncoding0(denv, dobj, encoder, argv[3], 0))
    {
        fprintf(stderr, "Error doing setup encoding\n");
        exit(1);
    }

    // we might want to add a signal handler?
    while(1)
    {
        JNICALL Java_sage_DVBCaptureDevice_eatEncoderData0(denv, dobj, encoder);
    }

    return 0;
}
#endif
