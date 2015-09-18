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

#ifndef __DTVCHANNEL_H
#define __DTVCHANNEL_H

//#include "Channel.h"
//#include "TSnative.h"
//#include "TSSplitter.h"
#include "SageTuner.h"

#include "NativeCore.h"
#include "Remuxer.h"
#include "TSFilter.h"
#include "TSParser.h"
#include "ScanFilter.h"
#include "Channel.h"

#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>

#ifndef FILETRANSITION
#define FILETRANSITION
#endif

void enable_dtvchannel_native_log();

struct DTVChannelDebugInfo {
	char  raw_dump_path[256];
	uint32_t dump_size;
	uint32_t dumped_bytes;
	char  debug_source[256];
	void *debug_source_buffer; // BUFFERSIZE bytes (defined in Channel.h)
	int   debug_source_mode;
	FILE* dump_fd;
	FILE* source_fd;
	uint32_t audio_ctrl;
};

#define MAX_PID_NUM 8

// there should only be ONE of these PER DEVICE!
class DTVChannel {
	public:
		DTVChannel(SageTuner *tuner, const char *tunerName, FILE *encodeFile, size_t fileSize);
		~DTVChannel();
		
			// configuration methods
		void setOutputFile(FILE *encodeFile, size_t fileSize);
#ifdef FILETRANSITION
		void setNextOutputFile(FILE *encodeFile);
		int hasNextOutputFile();
#endif
		int setTuning(SageTuningParams *params);
		char *scanChannel(const char *channelName, const char *country, int streamType);
			// don't free the returned string
		char *getBroadcastStandard();
		
		void flush();
			// call idle frequently, e.g., when eatEncoderData is called
			// so we get data when debugging and debug_source_mode != 2
		void idle();
		void pushData(void *buffer, size_t size);
		void splitStream(void *buffer, size_t size);
		off_t getOutputByteCount() {return mBytesOut;}
		off_t getProcessedByteCount() {return mBytesProcessed;}
		
			// Dump methods
		int32_t EPGDump(short bytes, void *msg);
		int32_t AVInfoDump(short bytes, void *msg);
		int  OutputDump(unsigned char *buffer, uint32_t size);
		int32_t PMTDump(unsigned short programID, unsigned char *pmtData, int pmtDataSize);
		int32_t pidFilterDump( PID_ENTRY* pids, int pidNum );
		
			// tuning calls
		char *getTunerDeviceName();
		int checkLocked(); // 1 lock, 0 no lock, -1 error
		int tuneATSCChannel(ATSC_FREQ *atsc, int dryTune); // physical channel number, use ATSC_BRCAST table for freq
		int tuneQAMFrequency(QAM_FREQ *qam, int dryTune);
		int tuneDVBTFrequency(DVB_T_FREQ *dvbt, int dryTune);
		int tuneDVBCFrequency(DVB_C_FREQ *dvbc, int dryTune);
		int tuneDVBSFrequency(DVB_S_FREQ *dvbs, int dryTune);
		
			// Hardware PID filtering support
		bool hasPIDFilter();	// polls hardware driver (SageTuner interface)
		int setupPIDFilter( PID_ENTRY *pidTbl, int pidNum );
		
			// TBD: CAM tuning...
		int changeCAMChannel(int program);
		void enableCAM();
		void disableCAM();
		
			// TS calls (via splitter)
		int  selectOutputFormat(int format); // 0:TS; 1:PS, returns selected format
		int  lockTSChannel(TUNE *pTune);
		void startTSParser();
		void stopTSParser();
		void lockupParser();
		void unlockupParser();
		int  scanChannel( TUNE* pTune );
		void stopScanChannel();
		int  releaseScanChannel();
		int  scanChannelState( int *pScanState, int *pFoundChannelNum );
		int  scanChannelList( void** ppChannelList  );
		int  scanTuneList( void** ppNit );
		
	private:
		SageTuner *mTuner;
		char *mTunerName;
		CHANNEL_DATA mChannel;
		uint32_t mCaptureStartTime;
		FILE *mOutputFile;
		int outputFormat;

		int parserEnabled;
		struct TUNE	tune;
		struct REMUXER *remuxer;

		unsigned char alignBuffer[2048];
		int	alignBytes;
		int expectedBytes;

		SCAN_FILTER *scanFilter;
		int  scanChannelEnabled;

#ifdef FILETRANSITION
		FILE *mNextOutputFile; // next file to write the captured data to
		int bytesTested; // We want to give up after some fixed amount of bytes if no transition found
		int detectmode; // 0: unknown 1: mpeg 2 PS  2: mpeg 2 TS  3: unsupported
		int detecttype; // 0: unknown, 1: mpeg1  2:mpeg2  3:H264
		int detectid; // stream ID of detect (should be 0xE0 for now)
#endif


		off_t mOutputFileSize; // if non-zero, we're recording to a circular file buffer
		off_t mOutputFileOffset;
		//void *mOutputBuffer;
		size_t mOutputBufferSize;
		
		pthread_mutex_t mutex1_scan_session;
		pthread_mutex_t mutex1_scan_data;
		pthread_mutex_t mutex1_push_data;
		
		// debug support
		DTVChannelDebugInfo *dbg;
		
		SageTuningParams mTuningParams;
		bool havePIDTbl;
		int  mProgramID;
		int  pidTotalNum;
		PID_ENTRY pidTbl[MAX_PID_NUM];
		int mPIDFilterDelay;		// how long (in seconds) we wait after startup to enable the PID filter
		struct timeval mPIDFilterDelayStart;
		
		off_t mBytesDump;
		// statistics, these should be at least 64 bit values...
		off_t mBytesIn;
		off_t mBytesOut;
		off_t mBytesProcessed;
};

#endif //__DTVCHANNEL_H
