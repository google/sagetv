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

#ifndef FILETRANSITION
#define FILETRANSITION
#endif

#include "DTVChannel.h"
#include "SageTuner.h"

#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <jni.h>
#include <pthread.h>

#include <sys/stat.h>
#include <sys/time.h>
#include <math.h>
#include <poll.h>

#include <assert.h>

#if defined(__linux__)
#include <sys/time.h>
#include <linux/types.h>
#include <time.h>
#include <sys/timeb.h>
#include <sys/types.h>
#include <sys/un.h>
#endif

// Apple OS/X added specific includes
#ifdef __APPLE__
#include <machine/types.h>
#include <sys/time.h>
#include <time.h>
#include <sys/timeb.h>
#include <sys/types.h>
#include <sys/un.h>
#endif

#define EPG_MSG_TYPE	10
#define AV_INF_TYPE		11

#pragma mark -
#pragma mark Debug Support

static bool flog_enabled=false;
void enable_dtvchannel_native_log()
{
	flog_enabled=true;
	_enable_native_log();
}
/*
static void _flog_local_check()
{
	struct stat st;
	if(stat("NATIVE_LOG.ENABLE", &st) == 0) flog_enabled = true;
}
*/

#ifdef REMOVE_LOG
 #define flog(...)
#else
static void flog( const char* logname, const char* cstr, ... ) 
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
#endif

uint32_t msElapsed( uint32_t start_time )
{
	uint32_t ms;
	struct timeval tv;
	gettimeofday(&tv, NULL);
	ms = tv.tv_sec * 1000 + tv.tv_usec/1000;
	return (ms - start_time);
}

/*
	cfg line format: name    integer    string\n
 */
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

int LoadDebugCfg( DTVChannelDebugInfo* dbg )
{
	char buf[512], name[64], ext[512-64];
	int  val;
	
	dbg->raw_dump_path[0] = 0;
	dbg->debug_source[0] = 0;
	FILE* fd = fopen("debugserver.ini", "r" );
	if ( fd == NULL )
	{
		//puts( "file not found\r\n" );
		return 0;
	}
//	printf( "DTVChannel: debug server configure is loaded\r\n" );
	while ( !feof(fd) )
	{
		memset( buf, 0, sizeof(buf) );
		if ( fgets( buf, sizeof(buf), fd ) )
		{
			if ( buf[0] == '#' ) continue;			
			GetCfgVal( buf, name, sizeof(name), &val, ext, sizeof(ext) );
			if ( !strcmp( name, "rawdump" ) && val != 0 )
			{ 
				flog( "Native.log", "DTVChannel: dump raw data %d Mbyte into %s\r\n", val, ext );
				strncpy( dbg->raw_dump_path, ext, sizeof(dbg->raw_dump_path) );
				if ( val == -1 ) dbg->dump_size = (uint32_t)val;
				else dbg->dump_size = (uint32_t)val*1024*1024;  //Mbytes
				
			} else
				if ( !strcmp( name, "debugsource" ) && val != 0 )
				{
					flog( "Native.log",  "DTVChannel: debug source from '%s' mode:%d\r\n", ext, val );
					strncpy( dbg->debug_source, ext, sizeof(dbg->debug_source) );
					dbg->debug_source_mode = (int)val;
				}
			if ( !strcmp( name, "audio_ctrl" ) && val != 0 )
			{
				dbg->audio_ctrl = (uint32_t)val;
				flog( "Native.log",  "DTVChannel: debug audio ctrl: %d. %s\r\n", val,
					   (dbg->audio_ctrl&0x01) ? "multiple audio disabled" : "" );
				
			}
		}
	}
	
	fclose( fd );
	return 1;
}

#pragma mark -
#pragma mark Static functions

static void DTVChannel_PostMessage(char* pSrc, char* pData, int MsgType, int Priority)
{
	static JavaVM* vmBuf = 0;
	static jsize numVMs;
	static jclass msgMgrClass = 0;
	static jmethodID postMsgMeth = 0;
	static jclass msgClass = 0;
	static jmethodID msgConstructor = 0;
	static uint32_t msg_id = 0;
	static pthread_mutex_t init_lock = PTHREAD_MUTEX_INITIALIZER;
	JNIEnv* env;
	
	if(!vmBuf) {
		jint res = 0;

		pthread_mutex_lock(&init_lock);
		if (!vmBuf)
			res = JNI_GetCreatedJavaVMs(&vmBuf, 1, &numVMs);
		pthread_mutex_unlock(&init_lock);
		
		if(res)
			return;
	}
	
	jint threadState = vmBuf->GetEnv((void**)&env, JNI_VERSION_1_4);
	if(threadState == JNI_EDETACHED)
		vmBuf->AttachCurrentThread((void**)&env, NULL);
	
	if(!msgMgrClass || !msgClass || !msgConstructor || !postMsgMeth) {
		pthread_mutex_lock(&init_lock);
		
		if (!msgMgrClass)
			msgMgrClass = (jclass) env->NewGlobalRef(env->FindClass("sage/msg/MsgManager"));
		if (msgMgrClass && !postMsgMeth)
			postMsgMeth = env->GetStaticMethodID(msgMgrClass, "postMessage", "(Lsage/msg/SageMsg;)V");
		if (!msgClass)
			msgClass = (jclass)(env->NewGlobalRef(env->FindClass("sage/msg/SageMsg")));
		if (msgClass && !msgConstructor)
			msgConstructor = env->GetMethodID(msgClass, "<init>", "(ILjava/lang/Object;Ljava/lang/Object;I)V");
		
		pthread_mutex_unlock(&init_lock);
	}
	
	int dataLen = strlen(pData)+1;
	jbyteArray javaData = env->NewByteArray(dataLen);
	env->SetByteArrayRegion(javaData, 0, dataLen, (const jbyte*)pData);
	jobject msgObj = env->NewObject(msgClass, msgConstructor, MsgType, env->NewStringUTF(pSrc), javaData, Priority);
	env->CallStaticVoidMethod(msgMgrClass, postMsgMeth, msgObj);
	msg_id++;
	
	if(threadState == JNI_EDETACHED)
		vmBuf->DetachCurrentThread();
}

// Embedded EPG data
// return: 1 on success, 0 on error
// typedef int32_t (*EPG_DUMP)( void* context, short bytes, void* mesg );
static int32_t DTVChannel_EPGDump(void *context, void *msg, short bytes )
{
	DTVChannel *chan = (DTVChannel*)context;
//	flog("Native.log", "DTVChannel_EPGDump(%p, %d, %s)\r\n", context, bytes, (char*)msg);
	
	//TODO: implement epg log for debugging
	if ( chan == NULL || msg == NULL || bytes == 0 )
		return 0;
	return chan->EPGDump(bytes, msg);
}

// A/V info data from stream, e.g., aspect ratio
// return: 1 on success, 0 on error
// typedef int32_t (*AV_INF_DUMP)( void* context, short bytes, void* mesg );
static int32_t DTVChannel_AVInfoDump(void *context, void *msg, short bytes )
{
	DTVChannel *chan = (DTVChannel*)context;
	flog("Native.log", "DTVChannel_AVInfoDump(%p, %d, %s)\r\n", context, bytes, (char*)msg);
	
	if(chan) return chan->AVInfoDump(bytes, msg);
	return 0;
}

static int32_t DTVChannel_PMTDump(void *context, void *mesg, short bytes )
{
	PROGRAM_DATA* programData = (PROGRAM_DATA*)mesg;
	unsigned char* pmtData;
	int pmtDataSize, pidNum;
	unsigned short programID;
	PID_ENTRY *pids;
	if ( bytes != sizeof(PROGRAM_DATA) )
	{
		flog( "Native.log", "ERROR:  PROGRAM_DATA block size wrong (%d != %d)!, version between DTVChannel and NativeCore isn't consistance.\r\n" ,
		             sizeof(PROGRAM_DATA), bytes );
	}
	//ASSERT( bytes == sizeof(PROGRAM_DATA) );
	if ( bytes == 0 || bytes > 4*1024 )
	{
		flog( "Native.log", "Drop  PROGRAM_DATA, beacuse wrong PMT message size (%d)", bytes );
		return -1;
	}
	DTVChannel *chan = (DTVChannel*)context;
	if ( chan == NULL ) return 0;
	pmtData = programData->pmt_section_data;
	pmtDataSize = programData->pmt_section_bytes;
	programID = programData->pids_table.sid;
	pidNum = programData->pids_table.pid_num;
	pids = programData->pids_table.pid_tbl;
	flog("Native.log", "DTVChannel PMTDump: (%p, %d, program_id:0x%x, pids num:%d)\r\n",
			context, bytes, programID, pidNum );

	chan->PMTDump( programID, pmtData, pmtDataSize );
	chan->pidFilterDump( pids, pidNum );
	
	return 0;
}

// Data callback from splitter, called from PushData and "FlashData" (FlushData???)
// return: 1 on success, 0 on error
// typedef int  (*OUTPUT_DUMP)( void* context, unsigned char* pData, uint32_t lBytes );
static int DTVChannel_OutputDump(void *context, void* pDataBlk, int lBytes )
{
	DTVChannel *chan = (DTVChannel*)context;
	OUTPUT_DATA   *pDataBuffer = (OUTPUT_DATA*)pDataBlk;
	unsigned char* pData = pDataBuffer->data_ptr;
	uint32_t  numBytes = pDataBuffer->bytes;

	//flog("Native.log", "DTVChannel_OutputDump(%p, %p, %d) sizeof OUTPUT_DATA %d\r\n", context, pData, numBytes, sizeof(OUTPUT_DATA) );
	
	if(chan) return chan->OutputDump( pData, numBytes );
	return 0;
}

// UNUSED (CAM): typedef int32_t (*DATA_DUMP)( void* context, short bytes, void* mesg );
// UNUSED: typedef int  (*ALLOC_BUFFER)( void* conext, unsigned char** ppData, int cmd ); // cmd 0:alloc, 1:release, return size

#pragma mark -
#pragma mark DTVChannel

DTVChannel::DTVChannel(SageTuner *tuner, const char *tunerName, FILE *encodeFile, size_t fileSize) :
	mTuner(tuner),
	mTunerName((char *)NULL),
	mOutputFile(encodeFile),
	mNextOutputFile((FILE *)NULL),
	mOutputFileSize((off_t)fileSize),
	mOutputFileOffset(0),
	mOutputBufferSize(0),
	dbg((DTVChannelDebugInfo *)NULL),
	havePIDTbl(false),
	mProgramID(0),
	mPIDFilterDelay(0),
	mBytesIn(0),
	mBytesOut(0),
	mBytesProcessed(0)
{
	mTunerName = (char*)malloc(strlen(tunerName)+1);
	strcpy(mTunerName, tunerName);
	
	// init debugging info
	dbg = new DTVChannelDebugInfo();
	memset( dbg, 0, sizeof(DTVChannelDebugInfo) );
	LoadDebugCfg(dbg);
	
	// clear PID filter data
	openChannel(&mChannel, this);
	setOutputFormat(&mChannel, 1);
    enableFrqTableUpdate(&mChannel);
	

	if ( (remuxer = (REMUXER*)OpenRemuxStream( REMUX_STREAM , &tune,
                       	            MPEG_TS, MPEG_PS, (MEM_ALLOC_HOOK)NULL, NULL, DTVChannel_OutputDump, this )  ) == NULL )
	{
		free(mTunerName);
		flog("Native.log", "DTVChannel: failed creating a Remuxer.\r\n" );
		throw -1;
	}
	flog("Native.log", "DTVChannel: remuxer was created.\r\n" );
	SetupEPGDump( remuxer, (DUMP)DTVChannel_EPGDump, (void*)this );
	SetupAVInfDump( remuxer, (DUMP)DTVChannel_AVInfoDump, (void*)this );
	SetupProgramDataDump( remuxer, (DUMP)DTVChannel_PMTDump, (void*)this );
	SetDefaultAudioLanguage( remuxer, LANGUAGE_CODE( "eng" ) );

	memset( &mutex1_scan_session, 0, sizeof(mutex1_scan_session) );// = PTHREAD_MUTEX_INITIALIZER;
	memset( &mutex1_scan_data, 0, sizeof(mutex1_scan_data) ); // = PTHREAD_MUTEX_INITIALIZER;
	memset( &mutex1_push_data, 0, sizeof(mutex1_push_data) ); // = PTHREAD_MUTEX_INITIALIZER;

	parserEnabled = 0;
	scanChannelEnabled = 0;
	alignBytes = 0;
	expectedBytes = 0;
	scanFilter = (SCAN_FILTER *)NULL;
	if(dbg->audio_ctrl & 0x01) {
		DisableMultipleAudio( remuxer );
		flog( "Native.log", "DTVChannel: multiple audio is disabled\r\n" );
	}

	flog("Native.log", "DTVChannel 2.0.1 is created 0x%p, remuxer:0x%p tuner:'%s' outFile:0x%x \r\n",
			  this, remuxer,  mTunerName, encodeFile  );
}

DTVChannel::~DTVChannel()
{
	closeChannel(&mChannel);
	
	if ( scanFilter != NULL )
	{
		StopChannelScan( scanFilter );
		ReleaseScanFilter( scanFilter );
	}
	if(remuxer)
		CloseRemuxStream( remuxer );

	free(mTunerName);
	
	flog( "Native.log", "DTVChannel: close outFile file:0x%x\r\n", mOutputFile );
	if(mOutputFile)
		fclose(mOutputFile);
	//if(mOutputBuffer) free(mOutputBuffer);
	
	flog( "Native.log", "DTVChannel: free gdb.\r\n" );
	if(dbg) {
		if(dbg->dump_fd != NULL) {
			fclose(dbg->dump_fd);
			dbg->dump_fd = (FILE *)NULL;
		}
		
		if(dbg->source_fd != NULL) {
			fclose(dbg->source_fd);
			dbg->source_fd = (FILE *)NULL;
		}
		
		if(dbg->debug_source_buffer) {
			free(dbg->debug_source_buffer);
			dbg->debug_source_buffer = NULL;
		}
		
		delete dbg;
		dbg = (DTVChannelDebugInfo *)NULL;
	}
	flog( "Native.log", "DTVChannel: destroyed.\r\n" );
}

void DTVChannel::setOutputFile(FILE *encodeFile, size_t fileSize)
{
	// the current file will be closed by whomever opened it...
	flog("Native.log", "DTVChannel setOutputFile outputFile:0x%x size:%d\r\n", encodeFile, fileSize );
	mOutputFile = encodeFile;
	mOutputFileSize = (off_t)fileSize;
	mOutputFileOffset = 0;
}

#ifdef FILETRANSITION
void DTVChannel::setNextOutputFile(FILE *encodeFile)
{
	// the current file will be closed by whomever opened it...
	pthread_mutex_lock( &mutex1_push_data );
	mBytesIn = mBytesOut = mBytesProcessed = 0;
	pthread_mutex_unlock( &mutex1_push_data );
	mNextOutputFile = encodeFile;
}

int DTVChannel::hasNextOutputFile()
{
    return mNextOutputFile != NULL;
}
#endif

// called for setInput and setChannel
// decide what to do based on provided information
int DTVChannel::setTuning(SageTuningParams *params)
{
	int result = 1;
	flog("Native.log", "DTVChannel::setTuning: mode %s, channel %s\r\n",
			 params->tunerMode, params->channel);
	
	if( dbg ) {
		if(dbg->raw_dump_path[0]) {
			char name[256];
			if(dbg->dump_fd)
				fclose(dbg->dump_fd);
			sprintf(name, dbg->raw_dump_path, params->channel);
			dbg->dump_fd = fopen(name, "w");
			dbg->dumped_bytes = 0;
			flog( "Native.log", "DTVChannel: Raw dump file set to %s\r\n", name );
		}
		
		if(dbg->debug_source[0]) {
			if(dbg->source_fd > 0)
				fclose(dbg->source_fd);
			dbg->source_fd = fopen(dbg->debug_source, "r");
			if(dbg->source_fd > 0)
				flog("Native.log", "DTVChannel: Debug source file open: %s mode:%d (%d).\r\n",
					  dbg->debug_source, dbg->debug_source_mode, dbg->source_fd );
		}
	}

	memcpy(&mTuningParams, params, sizeof(SageTuningParams));

	// setInput: parse some info and call setTuning on the device so it can configure the proper input
	if(mTuningParams.countryCode) {
		// check if we're already configured properly
		if(strcmp(mTuningParams.tunerMode, getTunerMode(&mChannel)) ||
			(getOutputFormat(&mChannel) != mTuningParams.outputFormat) ||
			(getCountryCode(&mChannel) != mTuningParams.countryCode))
		{
			const char* tunerType = mTuner->getTunerType();
			bool isOTA = (strcmp("Air", mTuningParams.tunerMode)==0);
			bool isCable = (strcmp("Cable", mTuningParams.tunerMode)==0);
			bool isHrc = (strcmp("HRC", mTuningParams.tunerMode)==0);
			bool isOTAQAM = (strcmp("OTAQAM", mTuningParams.tunerMode)==0);
			bool isUserDefined = (strcmp("UserDefined", mTuningParams.tunerMode)==0);
			bool isATSC = (strcmp("ATSC", mTuningParams.tunerMode)==0);
			bool isDVBT = (strcmp("DVB-T", mTuningParams.tunerMode)==0);
			bool isDVBC = (strcmp("DVB-C", mTuningParams.tunerMode)==0);
			bool isDVBS = (strcmp("DVB-S", mTuningParams.tunerMode)==0);
			bool isDVBTC = (strcmp("DVB-TC", mTuningParams.tunerMode)==0);

			// added DVB-TC type used by HD HomeRun 3 DUAL EU edition (dvbtc)
			if ( !isATSC && !isDVBT && !isDVBC && !isDVBS && tunerType != NULL )
			{
				if ( !(isATSC = (strcmp("ATSC",  tunerType)==0)) )
					if (!( isDVBT = (strcmp("DVB-T", tunerType)==0) ))
						if (!( isDVBTC = (strcmp("DVB-TC", tunerType)==0) ))
							if ( !(isDVBC = (strcmp("DVB-C", tunerType)==0) ) )
								isDVBS = (strcmp("DVB-S", tunerType)==0);
			}
			
			setTunerMode(&mChannel, mTuningParams.tunerMode);
			setOutputFormat(&mChannel, mTuningParams.outputFormat);
			setCountryCode(&mChannel, mTuningParams.countryCode);
			
			mTuningParams.tuningStandard = kSageTunerStandard_Unknown;
			mTuningParams.tuningFrequency = 0; // force this to get reset
			
			// TODO: Poll device tuning capabilities...
			flog("Native.log", "setTuning isOTA:%d isCable:%d isHrc:%d isOTAQAM:%d isUserDefined:%d ATSC:%d DVB-T:%d DVB-C:%d DVB-S:%d\r\n",
				 isOTA, isCable, isHrc, isOTAQAM, isUserDefined, isATSC, isDVBT, isDVBC, isDVBS );
			flog("Native.log", "setTuning: mode:%s, output-format:%d, country-code:%d Tuner-type:%s\r\n",
				 mTuningParams.tunerMode, mTuningParams.outputFormat, mTuningParams.countryCode, tunerType );
			
			setSourceType(&mChannel, "UNKNOWN");
			if( isDVBT )
			{
				setFreqType(&mChannel, 1);
				setSourceType(&mChannel, "DVB-T");
			} else
			if( isDVBC )
			{
				setFreqType(&mChannel, 1);
				setSourceType(&mChannel, "DVB-C");
			} else
			if( isDVBTC )
			{
				// special tuner type handling for HDHR3-EU HDHomeRun DUAL which has DVB-T and DVB-C
				// use the cable or OTA setting to determine what to use
				// the HDHR3-US version with ATSC and QAM is already properly handled below
				setFreqType(&mChannel, 1);
				if ( isCable ) {
					setSourceType(&mChannel, "DVB-C");
				}
				else {
					setSourceType(&mChannel, "DVB-T");
				}
			} else
			if( isDVBS )
			{
				setFreqType(&mChannel, 1);
				setSourceType(&mChannel, "DVB-S");
			} else
			if( isATSC && isOTA )
			{
				setFreqType(&mChannel, 1);
				setSourceType(&mChannel, "ATSC");
			} else
			{
				if((mTuningParams.countryCode <= 1) /*|| !strcmp(type, "ATSC")*/) { // US
					if(isCable || isHrc || isOTAQAM) {
						if(isCable)
							setFreqType(&mChannel, 2);
						else
						if(isHrc)
							setFreqType(&mChannel, 3);
						else
						if(isOTAQAM)
							setFreqType(&mChannel, 1);
						setSourceType(&mChannel, "QAM");
					} else
					if(isUserDefined) {
						setFreqType(&mChannel, 4);
						setSourceType(&mChannel, "QAM");
					} else {
						setFreqType(&mChannel, 1);
						setSourceType(&mChannel, "ATSC");
					}
				} else
					setSourceType(&mChannel, "DVB-T");

			}
			
			// load pre-scanned tuning table
			if(loadScanTuningTable(&mChannel) <= 0)
				flog("Native.log", "Failure loading frequency table (%d)\r\n", errno);
			
			// store the freq type in medium for later use
			mTuningParams.medium = getFreqType(&mChannel);
			
			flog("Native.log", "Configured DTV source type: %s\r\n", getSourceType(&mChannel));
		} else flog("Native.log", "Tuning mode unchanged...\r\n");
	} else flog("Native.log", "invalid country code (%d)\r\n", mTuningParams.countryCode);
	// setChannel:
	if( result && (mTuningParams.channel[0] != '\0'))
	{
		flog("Native.log", "Attempting to tune channel %s\r\n", mTuningParams.channel);
		result = tuneChannel(&mChannel, mTuningParams.channel);
		// verify the standard was set (in one of the SageTuneXXX calls)
		if(mTuningParams.tuningStandard == kSageTunerStandard_Unknown)
		{
			flog("Native.log", "Bogus tune! (std = %08lx, freq = %d)\r\n", mTuningParams.tuningStandard, mTuningParams.tuningFrequency);
			result = false;
		}
		
		if(result) {
			int locked = 0;
			
			pthread_mutex_lock( &mutex1_push_data );
			mBytesIn = mBytesOut = mBytesProcessed = 0;
			pthread_mutex_unlock( &mutex1_push_data );
			
			mCaptureStartTime = msElapsed(0);
			// channel lock, let the frontend drivers handle timeouts since each one is different
			locked = checkLocked();
			if (locked <= 0) result = false;
			flog("Native.log", "Tuning successes (lock:%d)!\r\n", locked );
		} else
			flog("Native.log", "Invalid channel (%s)!\r\n", mTuningParams.channel);
	}
	
//	flog("Native.log", "DTVChannel::setTuning(%p) %s\r\n", this, result ? "success" : "failure");
	return result;
}

char *DTVChannel::scanChannel(const char *channelName, const char *country, int streamType)
{
//	flog("Native.log", "DTVChannel::scanChannel: name %s, country \"%s\", streamType %d\r\n", channelName, country, streamType);
	char *scan_result;
	pthread_mutex_lock( &mutex1_scan_session );
	scan_result = ::scanChannel(&mChannel, mTunerName, (char*)channelName, (char*)country, streamType);
	pthread_mutex_unlock( &mutex1_scan_session );
	return scan_result;
}

char *DTVChannel::getBroadcastStandard()
{
	return getSourceType(&mChannel);
}


void DTVChannel::flush()
{
}


void DTVChannel::idle()
{
	int numbytes = 0;
	
	if(mPIDFilterDelay && havePIDTbl) {
		struct timeval now;
		gettimeofday(&now, NULL);
		
		timersub(&now, &mPIDFilterDelayStart, &now);
		if( now.tv_sec >= mPIDFilterDelay )
		{
			setupPIDFilter( pidTbl, pidTotalNum );
		}
	}
	
	if(!dbg) return;
	if(dbg->source_fd == NULL) return;
	
	if(dbg->debug_source_mode != 2) {
		uint32_t ep, ep_expect;
		
		numbytes = fread(dbg->debug_source_buffer, 1, BUFFERSIZE, dbg->source_fd);
		if(numbytes == 0) {
			fseek(dbg->source_fd, 0, SEEK_SET);
			numbytes = fread(dbg->debug_source_buffer, 1, BUFFERSIZE, dbg->source_fd);
		}
		
			// keep stream at constant rate
		ep = msElapsed(mCaptureStartTime);
		ep_expect = (mBytesIn + numbytes) / 2358;  // ATSC: rate 2358 bytes/sec
		if(ep_expect > ep)
			usleep((ep_expect - ep) * 1000);
	}
	
//	if(numbytes)
//		splitStream(dbg->debug_source_buffer, (size_t)numbytes);
}

void DTVChannel::pushData(void *buffer, size_t size)
{
	//flog("Native.log", "DTVChannel::pushData(%p, %d)\r\n", buffer, size);
	if(buffer && size && remuxer) {
		if(dbg) {
			if(dbg->dump_fd && (dbg->dump_size > dbg->dumped_bytes)) {
				fwrite(buffer, 1, size, dbg->dump_fd);
				fflush(dbg->dump_fd);
				dbg->dumped_bytes += size;
			}
			
			if((dbg->source_fd != NULL) && (dbg->debug_source_mode == 2)) {
				int numbytes = fread(dbg->debug_source_buffer, 1, (int)size, dbg->source_fd);
				if(numbytes == 0) {
					fseek(dbg->source_fd, 0, SEEK_SET);
					numbytes = fread(dbg->debug_source_buffer, 1, (int)size, dbg->source_fd);
				}
				
				splitStream(dbg->debug_source_buffer, numbytes);
				
				return;
			}
		}
		
		splitStream(buffer, size);
	}
}

void DTVChannel::splitStream(void *buffer, size_t size)
{
	unsigned char* pData = (unsigned char*)buffer;
	int32_t lDataLen = (int)size;
	if( remuxer == NULL || !size) return;
	//flog("Native.log", "DTVChannel::splitStream(%p, %d this:0x%x 0x%x 0x%x)\r\n", buffer, size,  (uint32_t)this,
	//         parserEnabled, scanChannelEnabled  );
	mBytesIn += (off_t)size;
	if ( 0 && dbg->dump_fd > 0 && dbg->dump_size > dbg->dumped_bytes )
	{
		fwrite( pData, 1, lDataLen, dbg->dump_fd );
		dbg->dumped_bytes += lDataLen;
		fflush( dbg->dump_fd );
	}

	if ( parserEnabled && scanChannelEnabled )
	{
		pthread_mutex_lock( &mutex1_scan_data );
		ProcessScan( scanFilter, (unsigned char*)pData, lDataLen );
		pthread_mutex_unlock( &mutex1_scan_data );
	}

	if ( parserEnabled && !scanChannelEnabled )
	{
		int usedBytes;
		unsigned char* start_ptr = (unsigned char*)pData;
		int length = lDataLen;
		mBytesDump = 0;
		if ( expectedBytes )
		{
			int expectedBytes2;
			memcpy( alignBuffer+alignBytes, start_ptr, expectedBytes );
			usedBytes = PushRemuxStreamData( remuxer, alignBuffer, alignBytes+expectedBytes, &expectedBytes2 );
			if ( expectedBytes2 == 0 )
			{
				start_ptr   +=  expectedBytes;
				length -=  expectedBytes;
			}
		}

		usedBytes = PushRemuxStreamData( remuxer, start_ptr, length, &expectedBytes );
		alignBytes = length - usedBytes;
		ASSERT( expectedBytes+alignBytes <= (int)sizeof(alignBuffer) );
		if ( alignBytes > 0 && alignBytes + expectedBytes <= (int)sizeof(alignBuffer))
		{
			memcpy( alignBuffer, start_ptr+usedBytes, alignBytes );
		} else
		{
			//drop data, because of asking too many
			expectedBytes = 0;
		}
		pthread_mutex_lock( &mutex1_push_data );
		mBytesOut += mBytesDump;
		mBytesProcessed += usedBytes;
		pthread_mutex_unlock( &mutex1_push_data );
	}


	
}

int32_t DTVChannel::EPGDump(short bytes, void *msg)
{
	DTVChannel_PostMessage(mTunerName, (char*)msg, EPG_MSG_TYPE, 100);
	return 1;
}

int32_t DTVChannel::AVInfoDump(short bytes, void *msg)
{
	DTVChannel_PostMessage(mTunerName, (char*)msg, AV_INF_TYPE, 100);
	return 1;
}

#ifdef FILETRANSITION
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
#endif

int DTVChannel::OutputDump(unsigned char *buffer, uint32_t size)
{
	int outBytes = 0;
	//flog("Native.log", "DTVChannel(%p)::OutputDump(%p, %d) - out file %p\r\n", this, buffer, size, mOutputFile);
	
	if( mOutputFile == NULL || !buffer) return 0;
	
#ifdef FILETRANSITION
	int numbytes = (int)size;
    // We want to switch to the new fd as soon as we get a good starting point
    if( mNextOutputFile )
    {
        // We need to parse the current data buffer hoping to find a starting point
        int transitionpos = findTransitionPoint(buffer, numbytes,
            detectmode==2 ? 1 : 0, detecttype);
       	//int transitionpos = 0;
        int bufSkip=transitionpos;
        bytesTested = 0;
        if(transitionpos!=-1)
        {
            bufSkip=transitionpos;
            if (!mOutputFileSize)
            {
               outBytes=fwrite(buffer, 1, bufSkip, mOutputFile);
            }
            flog( "Native.log", "transition found after %d bytes switching from fd 0x%x to 0x%x.\r\n",
						bytesTested, mOutputFile, mNextOutputFile );
            if(mOutputFile!=NULL)
                fclose(mOutputFile);
            mOutputFile=mNextOutputFile;
            mNextOutputFile = (FILE *)NULL;
        }
        else
        {
            bytesTested+=numbytes;
            // We might want to have different numbers or base it on bitrates
            if(bytesTested>8*1024*1024)
            {
                flog( "Native.log", "transition limit reached, switching fd.\r\n");
                if(mOutputFile!=NULL)
                    fclose(mOutputFile);
                mOutputFile=mNextOutputFile;
                mNextOutputFile = (FILE *)NULL;
            }
			bufSkip = 0;
        }
        buffer+=bufSkip;
        size-=bufSkip;
    }
#endif

	if(mOutputFileSize)
	{
		if(mOutputFileOffset == mOutputFileSize) {
			fseek(mOutputFile, 0, SEEK_SET);
			mOutputFileOffset = 0;
		}
		
		if(mOutputFileOffset + (off_t)size <= mOutputFileSize) {
			outBytes=fwrite(buffer, 1, (size_t)size, mOutputFile);
			if ( outBytes < 0 ) outBytes = 0;
			mOutputFileOffset += outBytes;
		} else {
			size_t head = (size_t)(mOutputFileSize - mOutputFileOffset);
			size_t tail = size - head;
			int outBytes2;
			outBytes=fwrite(buffer, 1, head, mOutputFile);
			if ( outBytes < 0 ) outBytes = 0;
			fseek(mOutputFile, 0, SEEK_SET);
			outBytes2 = fwrite(buffer+head, 1, tail, mOutputFile);
			if ( outBytes2 < 0 ) outBytes2 = 0;
			outBytes += outBytes2;
		}
	} else {
		outBytes=fwrite(buffer, 1, (size_t)size, mOutputFile);
		if ( outBytes < 0 ) outBytes = 0;
	}
	fflush(mOutputFile);
	mBytesDump += outBytes;
	
	
	return 1;
}

int32_t DTVChannel::PMTDump(unsigned short programID, unsigned char *pmtData, int pmtDataSize)
{
	mProgramID = programID;
	return 1;
}

int32_t DTVChannel::pidFilterDump( PID_ENTRY* pids, int pidNum )
{
	int result = 0, i;
   // shouldn't be here if either of these fails
	assert(hasPIDFilter());
	
	havePIDTbl = true;
	for ( i = 0; i<pidNum && i<MAX_PID_NUM; i++ )
		pidTbl[i] = pids[i];
	pidTotalNum = pidNum;

	if(mPIDFilterDelay) {
		// delayed startup
		gettimeofday(&mPIDFilterDelayStart, NULL);
	} else {
		lockupParser();
		result = setupPIDFilter( pids, pidNum );
	}
	
	flog("Native.log", "Program Filter%s: Set program ID:0x%x [%x,%x,%x,%x,%x,%x,%x,%x] (result = %d)\r\n",
		  mPIDFilterDelay ? "(delayed)" : "", mProgramID,
		  pidTbl[0].pid, pidTbl[1].pid ,pidTbl[2].pid ,pidTbl[3].pid ,pidTbl[4].pid ,pidTbl[5].pid ,pidTbl[6].pid ,pidTbl[7].pid ,  result);
	
	return (int32_t)(result != 1);
}

#pragma mark -
#pragma mark Tuning calls

char *DTVChannel::getTunerDeviceName()
{
	return mTunerName;
}

// -1 error, 0 no lock, 1 locked
int DTVChannel::checkLocked()
{
	int result = -1;
	
	if(mTuner) {
		bool locked = false;
		int strength = 0;
		result = mTuner->getTuningStatus(locked, strength);
	}// else flog("Native.log", "DTVChannel::checkLocked - no tuner???\r\n");
	return result;
}

int DTVChannel::tuneATSCChannel(ATSC_FREQ *atsc, int dryTune)
{
	int physical_ch;
	int err = -1;
	
	if(!atsc) {
		flog( "Native.log", "invalid ATSC FREQ\r\n" );
		return -1;
	}
	physical_ch = atsc->physical_ch;
	
	mTuningParams.tuningStandard = kSageTunerStandard_ATSC;
//	mTuningParams.aft = false;
	memcpy(&mTuningParams.dtvParams.atsc, atsc, sizeof(ATSC_FREQ));

	// disable dry tuning if the driver won't allow it
	if(dryTune && !mTuner->allowDryTune()) dryTune = 0;
	
	// always disable filter on tune, especially dry tune
	if( hasPIDFilter()) {
		setupPIDFilter( (PID_ENTRY *)NULL, 0 );
		havePIDTbl = false;
		//mPIDFilterDelay = 30;
		mPIDFilterDelay = 2; //ZQ we always have EPG data for ATSC channels, don't need EPG data in a stream
	}
	
	flog("Native.log", "tuneATSCChannel (ch:%d, fq:%d, dryTune:%s)\r\n", atsc->physical_ch, atsc->frequency, dryTune ? "true" : "false");
	if(mTuner && !dryTune) {
		int ii;
		bcast *freq = (bcast *)NULL;
		
		for(ii=0; ATSC_BRCAST[ii].fq != 0; ii++) {
			if(ATSC_BRCAST[ii].ch == physical_ch) {
				freq = &ATSC_BRCAST[ii];
				mTuningParams.tuningFrequency = ATSC_BRCAST[ii].fq * 1000;
				break;
			}
		}
		
		if(freq) {
			memset(&mTuningParams.dtvParams.atsc, 0, sizeof(ATSC_FREQ));
			// PID filter will kick in later if we want it
			mTuningParams.dtvParams.atsc.physical_ch = freq->ch;
			mTuningParams.dtvParams.atsc.frequency = freq->fq;
			flog("Native.log", "tuning ATSC channel %d, frequency %d\r\n", physical_ch, mTuningParams.tuningFrequency);
			err = mTuner->setTuning(&mTuningParams);
			if(err != -1) err = 0;
		} else {
			flog("Native.log", "ATSC channel not found (ch:%d)\r\n", physical_ch);
			return -1;
		}
	}
	
	if(dryTune) err = 0;
	
	return err;
}

int DTVChannel::tuneQAMFrequency(QAM_FREQ *qam, int dryTune)
{
	int err = -1;
	
	// disable dry tuning if the driver won't allow it
	if(dryTune && !mTuner->allowDryTune()) dryTune = 0;
	
	// always disable filter on tune, especially dry tune
	if(hasPIDFilter()) {
		setupPIDFilter( (PID_ENTRY *)NULL, 0 );
		havePIDTbl = false;
		mPIDFilterDelay = 0;	// no EPG data pulled on QAM, so filter right away
	}
	
	if(mTuner && !dryTune) {
		mTuningParams.tuningStandard = kSageTunerStandard_QAM;
		mTuningParams.tuningFrequency = qam->frequency;
			// use medium to pass the freq type (if we change by channel rather than frequency)
		mTuningParams.medium = getFreqType(&mChannel);
		memcpy(&mTuningParams.dtvParams.qam, qam, sizeof(QAM_FREQ));
		
		flog("Native.log", "tuneQAMFrequency std:%08lx freq:%d QAM{index:%d major:%d minor:%d prog: %d channel:%d freq:%d mod:%d inversal:%d ctrl:%d, name \"%s\"}\r\n",
			  mTuningParams.tuningStandard,
			  mTuningParams.tuningFrequency,
			  mTuningParams.dtvParams.qam.index,
			  mTuningParams.dtvParams.qam.major,
			  mTuningParams.dtvParams.qam.minor,
			  mTuningParams.dtvParams.qam.program,
			  mTuningParams.dtvParams.qam.physical_ch,
			  mTuningParams.dtvParams.qam.frequency,
			  mTuningParams.dtvParams.qam.modulation,
			  mTuningParams.dtvParams.qam.inversal,
			  mTuningParams.dtvParams.qam.ctrl,
			  mTuningParams.dtvParams.qam.name
			  );
		
		err = mTuner->setTuning(&mTuningParams);
		if(err != -1) err = 0;
	}
	
	if(dryTune) err = 0;
	
	return err;
}

int DTVChannel::tuneDVBTFrequency(DVB_T_FREQ *dvbt, int dryTune)
{
	int err = -1;

	// disable dry tuning if the driver won't allow it
	if(dryTune && !mTuner->allowDryTune()) dryTune = 0;

	// always disable filter on tune, especially dry tune
	if(hasPIDFilter()) {
		setupPIDFilter( (PID_ENTRY *)NULL, 0 );
		havePIDTbl = false;
		mPIDFilterDelay = 0;	// we extract EPG always on specfied EPG pids
	}
	
	mTuningParams.tuningStandard = kSageTunerStandard_DVB_T;
	mTuningParams.tuningFrequency = dvbt->frequency;
	memcpy(&mTuningParams.dtvParams.dvbt, dvbt, sizeof(DVB_T_FREQ));
	
	flog("Native.log", "tuneDVBTFrequency (std:%08lx freq = %d onid:%d tsid:%d sid:%d channel:%d bandwidth:%d rateHP:%d rateLP:%d trans:%d guard:%d heirarchy:%d shift:%d)\r\n",
		  mTuningParams.tuningStandard,
		  mTuningParams.tuningFrequency,
		  mTuningParams.dtvParams.dvbt.onid,
		  mTuningParams.dtvParams.dvbt.tsid,
		  mTuningParams.dtvParams.dvbt.sid,
		  mTuningParams.dtvParams.dvbt.physical_ch,
		  mTuningParams.dtvParams.dvbt.bandwidth,
		  mTuningParams.dtvParams.dvbt.code_rate_hp,
		  mTuningParams.dtvParams.dvbt.code_rate_lp,
		  mTuningParams.dtvParams.dvbt.trans_mode,
		  mTuningParams.dtvParams.dvbt.guard_interval,
		  mTuningParams.dtvParams.dvbt.hierarchy,
		  mTuningParams.dtvParams.dvbt.shift
	);
	
	err = mTuner->setTuning(&mTuningParams);
	if(err != -1) err = 0;
	return err;
}

int DTVChannel::tuneDVBCFrequency(DVB_C_FREQ *dvbc, int dryTune)
{
	int err = -1;

	// disable dry tuning if the driver won't allow it
	if(dryTune && !mTuner->allowDryTune()) dryTune = 0;

	// always disable filter on tune, especially dry tune
	if(hasPIDFilter()) {
		setupPIDFilter( (PID_ENTRY *)NULL, 0 );
		havePIDTbl = false;
		mPIDFilterDelay = 0;	// we extract EPG always on specfied EPG pids
	}

	
	mTuningParams.tuningStandard = kSageTunerStandard_DVB_C;
	mTuningParams.tuningFrequency = dvbc->frequency;
	memcpy(&mTuningParams.dtvParams.dvbc, dvbc, sizeof(DVB_C_FREQ));
	
	flog("Native.log", "tuneDVBCFrequency (std:%08lx freq:%d onid:%d tsid:%d sid:%d channel:%d sym rate:%d fec_in:%d, fec_inn_rate:%d fec_out:%d: fec_out_rate:%d mod:%d)\r\n",
		  mTuningParams.tuningStandard,
		  mTuningParams.tuningFrequency,
		  mTuningParams.dtvParams.dvbt.onid,
		  mTuningParams.dtvParams.dvbt.tsid,
		  mTuningParams.dtvParams.dvbt.sid,
		  mTuningParams.dtvParams.dvbt.physical_ch,
		  mTuningParams.dtvParams.dvbc.symbol_rate,
		  mTuningParams.dtvParams.dvbc.fec_inner,
		  mTuningParams.dtvParams.dvbc.fec_inner_rate,
		  mTuningParams.dtvParams.dvbc.fec_outer,
		  mTuningParams.dtvParams.dvbc.fec_outer_rate,
		  mTuningParams.dtvParams.dvbc.modulation
	);
	
	err = mTuner->setTuning(&mTuningParams);
	if(err != -1) err = 0;
	return err;

}

int DTVChannel::tuneDVBSFrequency(DVB_S_FREQ *dvbs, int dryTune)
{
	int err = -1;

	// disable dry tuning if the driver won't allow it
	if(dryTune && !mTuner->allowDryTune()) dryTune = 0;

	// always disable filter on tune, especially dry tune
	if(hasPIDFilter()) {
		setupPIDFilter( (PID_ENTRY *)NULL, 0 );
		havePIDTbl = false;
		mPIDFilterDelay = 0;	// we extract EPG always on specfied EPG pids
	}

	mTuningParams.tuningStandard = kSageTunerStandard_DVB_S;
	mTuningParams.tuningFrequency = dvbs->frequency;
	memcpy(&mTuningParams.dtvParams.dvbs, dvbs, sizeof(DVB_S_FREQ));
	
	flog("Native.log", "tuneDVBCFrequency (std:%08lx freq:%d onid:%d tsid:%d sid:%d channel:%d sym rate:%d pol:%d fec_in:%d fec_in_rate:%d fec_ou:%d fec_out_rate:%d mod:%d orbit:%d)\r\n",
		  mTuningParams.tuningStandard,
		  mTuningParams.tuningFrequency,
		  mTuningParams.dtvParams.dvbt.onid,
		  mTuningParams.dtvParams.dvbt.tsid,
		  mTuningParams.dtvParams.dvbt.sid,
		  mTuningParams.dtvParams.dvbt.physical_ch,
		  mTuningParams.dtvParams.dvbc.symbol_rate,
		  mTuningParams.dtvParams.dvbs.polarisation,
		  mTuningParams.dtvParams.dvbc.fec_inner,
		  mTuningParams.dtvParams.dvbc.fec_inner_rate,
		  mTuningParams.dtvParams.dvbc.fec_outer,
		  mTuningParams.dtvParams.dvbc.fec_outer_rate,
		  mTuningParams.dtvParams.dvbc.modulation,
		  mTuningParams.dtvParams.dvbs.orbit
	);
	
	err = mTuner->setTuning(&mTuningParams);
	if(err != -1) err = 0;
	return err;

}

bool DTVChannel::hasPIDFilter()
{
	return mTuner->hasPIDFilter();
}

int DTVChannel::setupPIDFilter( PID_ENTRY *pids, int pidNum )
{
	int i;
	unsigned short pidList[MAX_PID_NUM] = {0,0,0,0,0,0,0,0};
	unsigned short program = 0;

	if(!mTuner) return -1;
	
	// disable if pid_info is NULL or programID is zero
	if( pids == NULL || pidNum == 0 )
	{
		//reset pid table
		pidTotalNum = 0;
		memset( pidTbl, 0, sizeof(pidTbl) );
		// disable filter, send raw stream
		havePIDTbl = false;
		mPIDFilterDelay = 0;
		unlockupParser();
		return mTuner->setPIDFilter(0, 0, (unsigned short *)NULL);
	}
	
	program = (unsigned short)mProgramID;
	// clear our cached struct so we don't go through this again
	havePIDTbl = false;
	mPIDFilterDelay = 0;
	for ( i = 0; i<MAX_PID_NUM && i < pidNum; i++  )
	{
		pidList[i] = pids[i].pid;
	}
	lockupParser();
	return mTuner->setPIDFilter(program, i, pidList); // pid_info could be cleared by the above memset, so don't use it
}

	// TBD: CAM tuning...
int DTVChannel::changeCAMChannel(int program)
{
//	flog("Native.log", "changeCAMChannel(%d)\r\n", program);
	return -1;
}

void DTVChannel::enableCAM()
{
//	flog("Native.log", "enableCAM\r\n");
}

void DTVChannel::disableCAM()
{
//	flog("Native.log", "disableCAM\r\n");
}

void DTVChannel::startTSParser()
{
	parserEnabled = 1;
}

void DTVChannel::stopTSParser()
{
	parserEnabled = 0;
}

void DTVChannel::lockupParser()
{
	return;
}

void DTVChannel::unlockupParser()
{
	return;
}

int DTVChannel::selectOutputFormat(int format) // 0:TS; 1:PS, return selected format
{
	flog( "Native.log", "Set output format %d, (Disabled)\r\n", format );
	if ( format == 0 )
	{
		outputFormat = MPEG_TS;
	}
	else
	if ( format == 1 )
	{
		outputFormat = MPEG_PS;
	}
	return outputFormat;
}

int DTVChannel::lockTSChannel(  TUNE *pTune  )
{
	flog( "Native.log", "%s", "Lock TS channel\r\n" );//zq
	ResetRemuxStream( remuxer );
	EnableTSPSI( remuxer );
	//DisabeTSPtsFix( pRemuxer );
	SetupRemuxStreamTune( remuxer, (TUNE*)pTune );
	tune = *((TUNE*)pTune);
	return 1;
}

int SageChangeCAMChannel( void* Capture, int program );
int DTVChannel::scanChannel( TUNE* pTune )
{
	flog( "Native.log", "%s", "Scan TS channel\r\n" );
	if ( scanFilter != NULL )
		return 0;
	scanFilter = (SCAN_FILTER*)CreateScanFilter();
	StartChannelScan( scanFilter, pTune );

	unsigned short program;
	program =  getProgramNum( pTune );
	if ( program > 0 )
		SageChangeCAMChannel( (void*)this,  program );
	scanChannelEnabled = 1;
	return 1;
}

void DTVChannel::stopScanChannel( )
{
	if ( scanFilter != NULL )
		return;
	scanChannelEnabled = 0;
	StopChannelScan( scanFilter );
}

int DTVChannel::releaseScanChannel( )
{
	if ( scanFilter == NULL )
		return 0;
	pthread_mutex_lock( &mutex1_scan_data );
	scanChannelEnabled = 0;
	StopChannelScan( scanFilter );
	ReleaseScanFilter( scanFilter );
	scanFilter = (SCAN_FILTER *)NULL;
	pthread_mutex_unlock( &mutex1_scan_data );
	return 1;
}

int DTVChannel::scanChannelState( int *pScanState, int *pFoundChannelNum )
{
	ASSERT( scanFilter != NULL );
	*pScanState = ScanChannelState( scanFilter );
	*pFoundChannelNum = ScanChannelNum( scanFilter );
	return pScanState > 0;
}

int DTVChannel::scanChannelList( void** ppChannelList  )
{
	ASSERT( scanFilter != NULL );
	*ppChannelList = GetScanChannelList( scanFilter );
	return 1;
}

int DTVChannel::scanTuneList( void** ppNit )
{
	ASSERT( scanFilter != NULL );
	*ppNit = (void*)GetScanTuneList( scanFilter );
	return 1;
}

#pragma mark -
#pragma mark Channel API callbacks

// SageTV DTV channel support calls
int SageTuneATSCChannel( void* Capture, ATSC_FREQ* atsc, int dryTune )
{
	DTVChannel *chan = (DTVChannel*)Capture;
	if(chan == NULL) return -1;
	int result = 0;
	bool activatePIDFilter = chan->hasPIDFilter();
	
	flog("Native.log", "SageTuneATSCChannel(%p, %d) (can filter = %s)\r\n", Capture, (atsc ? atsc->physical_ch : 0), activatePIDFilter ? "yes" : "no");

	// Do hardware PID filtering if supported
	//if( activatePIDFilter )
	//	chan->setupPIDFilter( NULL, 0 );	// capture whole stream until we get the program ID to filter
	result = chan->tuneATSCChannel(atsc, dryTune);
	return result;
}

int SageTuneQAMFrequency( void* Capture, QAM_FREQ* qam, int dryTune )
{
	DTVChannel *chan = (DTVChannel*)Capture;
	flog("Native.log", "SageTuneQAMChannel(%p, %p)\r\n", Capture, qam);
	
	if(chan == NULL) return -1;
	return chan->tuneQAMFrequency(qam, dryTune);
}
 
int SageTuneDVBTFrequency( void* Capture, DVB_T_FREQ* dvbt, int dryTune )
{
	DTVChannel *chan = (DTVChannel*)Capture;
//	flog("Native.log", "SageTuneDVBTChannel(%p, %p)\r\n", Capture, dvbt);
	
	if(chan == NULL) return -1;
	return chan->tuneDVBTFrequency(dvbt, dryTune);
}

int SageTuneDVBCFrequency( void* Capture, DVB_C_FREQ* dvbc, int dryTune )
{
	DTVChannel *chan = (DTVChannel*)Capture;
//	flog("Native.log", "SageTuneDVBCChannel(%p, %p)\r\n", Capture, dvbc);
	
	if(chan == NULL) return -1;
	return chan->tuneDVBCFrequency(dvbc, dryTune);
}

int SageTuneDVBSFrequency( void* Capture, DVB_S_FREQ* dvbs, int dryTune )
{
	DTVChannel *chan = (DTVChannel*)Capture;
//	flog("Native.log", "SageTuneDVBSChannel(%p, %p)\r\n", Capture, dvbs);
	
	if(chan == NULL) return -1;
	return chan->tuneDVBSFrequency(dvbs, dryTune);
}

int SageCheckLocked( void* Capture )
{
	DTVChannel *chan = (DTVChannel*)Capture;
//	flog("Native.log", "SageCheckLocked(%p)\r\n", Capture);
	
	if(chan == NULL) return 0;
	return chan->checkLocked();
}

char*  SageGetTunerDeviceName( void* Capture )
{
	DTVChannel *chan = (DTVChannel*)Capture;
//	flog("Native.log", "SageGetTunerDeviceName(%p)\r\n", Capture);

	if(chan == NULL) return "";
	return chan->getTunerDeviceName();
}

int SageSelectOuputFormat( void* Capture, int format ) //0:TS; 1:PS
{
	DTVChannel *chan = (DTVChannel*)Capture;
	if ( chan == NULL ) return -1;
	return chan->selectOutputFormat( format );
}

int SageTVLockTSChannel(void* Capture, void* pTune, int reset)
{

	DTVChannel *chan = (DTVChannel*)Capture;
	if(chan == NULL) return -1;
	
	return chan->lockTSChannel( (TUNE*)pTune );
}


int SageChangeCAMChannel( void* Capture, int program )
{
	DTVChannel *chan = (DTVChannel*)Capture;
//	flog("Native.log", "SageChangeCAMChannel(%p, %d)\r\n", Capture, program);
	
	if(chan == NULL) return -1;
	return 1;
}


void SageEnableCAM( void* Capture )
{
//	flog("Native.log", "SageEnableCAM(%p)\r\n", Capture);
}

void SageDisableCAM( void* Capture )
{
//	flog("Native.log", "SageDisableCAM(%p)\r\n", Capture);
}

void SageStartParser( void* Capture )
{
	DTVChannel *chan = (DTVChannel*)Capture;
	if(chan == NULL) return;
	flog("Native.log", "SageStartParser(%p)\r\n", Capture);
	chan->startTSParser();
}

void SageStopParser( void* Capture )
{
	DTVChannel *chan = (DTVChannel*)Capture;
	if(chan == NULL) return;
	flog("Native.log", "SageStopParser(%p)\r\n", Capture);
	chan->stopTSParser();
}

int SageTVScanChannel( void* Capture, void* pTune )
{
	DTVChannel *chan = (DTVChannel*)Capture;
	if(chan == NULL) return -1;

	return chan->scanChannel( (TUNE*)pTune );
}


void SageTVStopScan( void* Capture )
{
	DTVChannel *chan = (DTVChannel*)Capture;
	if(chan == NULL) return ;
	chan->stopScanChannel( );
}

int SageTVReleaseScanChannel( void* Capture )
{
	DTVChannel *chan = (DTVChannel*)Capture;
	if(chan == NULL) return -1;
	return chan->releaseScanChannel();
}

int SageTVScanChannelState( void* Capture, int* pScanState, int* pFoundChannelNum, int nCount )
{
	DTVChannel *chan = (DTVChannel*)Capture;
	if(chan == NULL) return -1;
	return chan->scanChannelState( pScanState, pFoundChannelNum );
}

int SageTVScanChannelList( void* Capture, void** ppChannelList )
{
	DTVChannel *chan = (DTVChannel*)Capture;
	if(chan == NULL) return -1;
	return chan->scanChannelList( ppChannelList );
}

int SageTVScanTuneList( void* Capture, void** ppNit )
{
	DTVChannel *chan = (DTVChannel*)Capture;
	if(chan == NULL) return -1;
	return chan->scanTuneList( ppNit );
}


int  SageGetSatelliteTble( void* Capture, SAT_NAME *sat_name, int max_sat )
{
    //it's for DiSeqC, to be implemented soon.
	return 0;
}

void SageDelay( void* Capture, uint32_t ms )
{
	sleep(ms);
}
