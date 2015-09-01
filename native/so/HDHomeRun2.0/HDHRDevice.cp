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

#include "HDHRDevice.h"

#ifdef __APPLE__
#include <SystemConfiguration/SystemConfiguration.h>
#include <netinet/in.h>
#endif

static bool flog_enabled=false;
void enable_hdhrdevice_native_log()
{
	flog_enabled=true;
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


#if 0
static int check_ip_reachable(uint32_t ipv4a)
{
	int status = 0;
	struct sockaddr_in addr = {INET_ADDRSTRLEN, AF_INET, 0, {ipv4a}, {0,0,0,0,0,0,0,0}};
	SCNetworkConnectionFlags flags = 0;
	
		// true if flags returned are valid, otherwise assume the answer is no...
	if(SCNetworkCheckReachabilityByAddress((const struct sockaddr*)&addr, INET_ADDRSTRLEN, &flags)) {
		if((flags & kSCNetworkFlagsReachable)
		   || ((flags & kSCNetworkFlagsTransientConnection) && (flags & kSCNetworkFlagsConnectionAutomatic))	// e.g. PPP connection
		)
		{
			status = 1;
		}
	}
	
	return status;
	return 1;
}
#endif

// NOTE: We do the firmware check elsewhere so we can (reasonably) safely ass-ume its ok
HDHRDevice::HDHRDevice(hdhomerun_discover_device_t disco, int tuner) :
	mDeviceInfo(disco)
	, mTunerIndex(tuner)
	, mDeviceConnection(NULL)
	, mScanning(false)
	, mEnableFilter(true)
	, lastFilterProgram(65535)
	, mChannel(NULL)
	, mOutputPath(NULL)
	, mOutputFile(NULL)
	, mMaxFileSize(0)
	, mLastMealSize(0ULL)
	, mCaptureThreadRunning(false)
	, mKillCaptureThread(false)
	, mDeviceChannelList(NULL)
	, mDeviceChannelMap(NULL)
{
	// allocate the DTVChannel instance
//	char channelName[64];
	
	sprintf(mDeviceName, "HDHomeRun %08lx Tuner %d", (long)mDeviceInfo.device_id, mTunerIndex);
	
	// we need the last -0 in the device name because the message parser expects it to be there...
	sprintf(mChannelName, "%s-0", mDeviceName);
//	mChannel = new DTVChannel(dynamic_cast <SageTuner*> (this), channelName, NULL, 0);
	flog( "Native.log", "HDHR device is created :%s\r\n", mDeviceName );
	memset(&mTuningParams, 0, sizeof(SageTuningParams));
}

HDHRDevice::~HDHRDevice()
{
	stopCapture();
	closeDevice();
	flog( "Native.log", "HDHR device is released :%s\r\n", mDeviceName );
}

bool HDHRDevice::validate()
{
	if(mDeviceInfo.device_id != 0) {
		struct hdhomerun_discover_device_t deviceInfo;
		bool_t valid = hdhomerun_discover_validate_device_id(mDeviceInfo.device_id);
		bool done = false;
		
		// if validation fails, give it a few seconds to come back
		// putting the system to sleep on Mac OS X seems to cause a problem
		
		if(valid) {
			int result, iter = 5;
			
			do {
				// -1 = error, 0 = none found, else number of devices found (max 1 here)
				result = hdhomerun_discover_find_devices_custom(0, HDHOMERUN_DEVICE_TYPE_TUNER, mDeviceInfo.device_id, &deviceInfo, 1);
//				flog( "Native.log",  "hdhomerun_discover_find_device(%08lx) returned %d\r\n", mDeviceInfo.device_id, result);
				
				if(result == -1) return false;
				
				if(result != 1) {
					if(--iter == 0) {
						done = true;
					} else {
						sleep(1);
					}
				} else done = true;
			} while(!done);
			
			if(result == 1) {
				// update cached info if we didn't get an error
				if((deviceInfo.device_type != mDeviceInfo.device_type) ||
				   (deviceInfo.ip_addr != mDeviceInfo.ip_addr))
				{
					memcpy(&mDeviceInfo, &deviceInfo, sizeof(struct hdhomerun_discover_device_t));
				}
				return true;
			}
		} else {
			// clear an invalid entry (id is invalid)
			// FIXME: threading issues?
			mDeviceInfo.ip_addr = 0;
			mDeviceInfo.device_type = 0;
//			mDeviceInfo.device_id = 0;
			flog( "Native.log",  "I have an invalid device ID!!! (%08lx)\r\n", (unsigned long)mDeviceInfo.device_id);
		}
	}
	
	return false;
}

// SageTuner API
int HDHRDevice::setTuning(SageTuningParams *params)
{
	const char *channel_map = NULL;
	char channelSetting[32] = "";
	int bandwidth = 6;
	bool cable = false;
	long channel = 0;
	int result;
	
	if(!mScanning) {
		memcpy(&mTuningParams, params, sizeof(SageTuningParams));
	} else {
		mTuningParams.tuningStandard = params->tuningStandard;
		mTuningParams.tuningFrequency = params->tuningFrequency;
		memcpy(&mTuningParams.dtvParams, &params->dtvParams, sizeof(mTuningParams.dtvParams));
	}
	flog( "Native.log",  "HDHRDevice::setTuning: standard:0x%lx freq:%ld channel:%s\r\n",
			mTuningParams.tuningStandard, mTuningParams.tuningFrequency, mTuningParams.channel);
	
	channel = strtol(mTuningParams.channel, NULL, 10); // first (maybe only) value is physical channel

	// ensure the right channel map is loaded first
	switch (mTuningParams.tuningStandard) {
		case kSageTunerStandard_ATSC:
			if((channel < 2) || (channel > 69)) {
				flog( "Native.log",  "HDHomeRun: Invalid channel number %ld\r\n", channel);
				return -1;
			}
			channel_map = "us-bcast";
			bandwidth = 6;
			cable = false;
			break;
		
		case kSageTunerStandard_QAM:
			if((channel < 1) || (channel > 135)) {
				flog( "Native.log",  "HDHomeRun: Invalid channel number %ld\r\n", channel);
				return -1;
			}
			// use channel group for cable, it should scan normal, hrc then irc
			channel_map = "us-cable us-hrc us-irc";
			bandwidth = 6;
			cable = true;
			break;

		case kSageTunerStandard_DVB_T:
			// use channel group for cable, it should scan normal, hrc then irc
			if (  mTuningParams.countryCode == 61 ) //Australia
			{
				channel_map = "au-bcast";
				bandwidth = mTuningParams.dtvParams.dvbt.bandwidth = 7;
			} else
			if (  mTuningParams.countryCode == 886 ) //taiwan
			{
				channel_map = "tw-bcast";
				bandwidth = mTuningParams.dtvParams.dvbt.bandwidth = 6;
			} else
			{
				channel_map = "eu-bcast";
				bandwidth = mTuningParams.dtvParams.dvbt.bandwidth = 8;
			}
			cable = false;
			break;
		
		case kSageTunerStandard_DVB_C:
			// use channel group for cable, it should scan normal, hrc then irc
			if (  mTuningParams.countryCode == 61 ) //Australia
			{
				channel_map = "au-cable";
				bandwidth = mTuningParams.dtvParams.dvbt.bandwidth;
			} else
			if ( mTuningParams.countryCode == 886 ) //taiwan
			{
				channel_map = "tw-cable";
				bandwidth = mTuningParams.dtvParams.dvbt.bandwidth;
			} else
			{
				channel_map = "eu-cable";
				bandwidth = mTuningParams.dtvParams.dvbt.bandwidth;
			}
			cable = true;
			break;

		default:
			flog( "Native.log",  "HDHomeRun: Invalid tuning standard specified (%ld)\r\n", mTuningParams.tuningStandard);
			return -1;
	}
	
	// ensure channel list is loaded
	if (!mDeviceChannelMap || !mDeviceChannelList || strcmp(mDeviceChannelMap, channel_map)) {
		mDeviceChannelMap = channel_map;
		if(mDeviceChannelList)
			hdhomerun_channel_list_destroy(mDeviceChannelList);
		mDeviceChannelList = hdhomerun_channel_list_create(mDeviceChannelMap);
		if(!mDeviceChannelList) {
			mDeviceChannelMap = NULL;
			flog( "Native.log",  "HDHomeRun: Unable to load channel list for \"%s\"\r\n", channel_map);
			return -1;
		}
	}

	if ( mTuningParams.tuningStandard == kSageTunerStandard_ATSC ||
		 mTuningParams.tuningStandard == kSageTunerStandard_QAM )
	{
		sprintf(channelSetting, "auto%d%c:%ld", bandwidth, cable ? 'c' : 't', channel);
	} else
	{
		sprintf(channelSetting, "auto:%ld", mTuningParams.tuningFrequency );
	}
	flog( "Native.log",  "HDHRDevice::setTuning: channel %s\r\n", channelSetting);
	
	result = hdhomerun_device_set_tuner_channelmap(mDeviceConnection, mDeviceChannelMap);
	if(result <= 0) {
		flog( "Native.log",  "HDHomeRun: Error trying to set channel map (%d)\r\n", result);
		return -1;
	}
	
	result = hdhomerun_device_set_tuner_channel(mDeviceConnection, channelSetting);
	if(result <= 0) {
		flog( "Native.log",  "HDHomeRun: Error trying to tune channel (%d)\r\n", result);
		return -1;
	}
	
	// when scanning, let's make sure we lock before returning
	if(mScanning) {
		struct hdhomerun_tuner_status_t status;
		result = hdhomerun_device_wait_for_lock(mDeviceConnection, &status);
		
		flog( "Native.log",  "HDHRDevice::setTuning: channel:%s lock_str:%s signal_present:%s strength:%d snr:%d seq:%d raw_bps:%ld pps:%ld\r\n",
				status.channel, status.lock_str,
				(status.signal_present ? "true" : "false"),
				status.signal_strength, status.signal_to_noise_quality, status.symbol_error_quality,
				(long)status.raw_bits_per_second, (long)status.packets_per_second);
	}
	
	return 0; // could be a format change... don't fail in that case
}

// 0 = no lock, 1 = lock, -1 on error
int HDHRDevice::getTuningStatus(bool& locked, int& strength)
{
	int result = 0;
	
	locked = false; // signal locked
	strength = 0;
	
	if(mDeviceConnection) {
		struct hdhomerun_tuner_status_t status;
		char *statusString;
		if(hdhomerun_device_get_tuner_status(mDeviceConnection, &statusString, &status) < 0) {
			flog( "Native.log",  "HDHRDevice::getSignalStrength: Error reading tuner status: %s\r\n", (statusString != NULL) ? statusString : "(null)");
			return -1;
		}
		
		// signal_present seems to be inaccurate, use the lock_str value to determine lock
		if(strcmp(status.lock_str, "none") && strcmp(status.lock_str, "(ntsc)")) {
			locked = true; // if anything besides none, then we're locked
			result = 1;
			strength = (int)status.signal_strength;
		}
		
		if ( 0 )
		flog( "Native.log",  "HDHRDevice::getSignalStrength channel:%s lock_str:%s signal_present:%s strength:%d snr:%d seq:%d raw_bps:%ld pps:%ld (%s, %d, %d)\r\n",
				status.channel, status.lock_str,
				(status.signal_present ? "true" : "false"),
				status.signal_strength, status.signal_to_noise_quality, status.symbol_error_quality,
				(long)status.raw_bits_per_second, (long)status.packets_per_second,
				locked ? "LOCK" : "NOLOCK",
				strength,
				result
		);
	} else return -1;
	
	return result;
}

bool HDHRDevice::allowDryTune()
{
	// FIXME: figure out if there's a way to allow dry tuning
	return false;
}

int HDHRDevice::setPIDFilterByProgram(unsigned short program )
{
	int result;
	char filterString[256] = "0x0000-0x1fff";	// default to send the raw DTV stream
	char programString[8] = "0";
	
	if(program == lastFilterProgram) return 0;
	lastFilterProgram = program;
	
	// DO NOT ENABLE IF WE'RE DATA SCANNING!
	if( !mScanning && mEnableFilter && program != 0 )
	{
		snprintf(programString, 8, "%d", program);
		result = hdhomerun_device_set_tuner_program(mDeviceConnection, programString);
	} else {
		// enable the full stream
		result = hdhomerun_device_set_tuner_filter(mDeviceConnection, "0x0000-0x1FFE"); // skip the 0x1fff pad packets
	}
	
	flog( "Native.log",  "HDHRDevice::setPIDFilter(program:%d) programString:\"%s\" filterString:\"%s\" scanning:%s filter_enabled:%s result:%d\r\n",
			          program, programString, filterString, mScanning ? "yes" : "no", mEnableFilter ? "yes" : "no", result);
	return result;
}

int HDHRDevice::setPIDFilter(unsigned short program, int pidCount, unsigned short *pidList)
{
	int result, i;
	char filterString[1024] = "0x0000-0x1fff";	// default to send the raw DTV stream

	// DO NOT ENABLE IF WE'RE DATA SCANNING!
	if( !mScanning && mEnableFilter && (program != 0) && (pidCount != 0) )
	{
		//snprintf(programString, 8, "%d", program);
		//result = hdhomerun_device_set_tuner_program(mDeviceConnection, programString);
		char *p= filterString;
		int len = sizeof(filterString), pos = 0;;
		for ( i = 0; i<pidCount; i++ )
			pos += snprintf( p+pos, len-pos, "0x%x ", pidList[i] );
		result = hdhomerun_device_set_tuner_filter(mDeviceConnection, filterString );

	} else {
		// enable the full stream
		result = hdhomerun_device_set_tuner_filter(mDeviceConnection, filterString ); // skip the 0x1fff pad packets
	}

	flog( "Native.log", "HDHRDevice::setPIDFilter(program:%d Count:%d) filterString:\"%s\" scanning:%s filter_enabled:%s result:%d\r\n",
			                 program, pidCount, filterString, mScanning ? "yes" : "no", mEnableFilter ? "yes" : "no", result);
	return result;
}

void HDHRDevice::setFilterEnable(bool enable)
{
	mEnableFilter = enable;
}

bool HDHRDevice::openDevice()
{
	mDeviceConnection = hdhomerun_device_create(mDeviceInfo.device_id, mDeviceInfo.ip_addr, mTunerIndex, NULL);
	if(!mDeviceConnection) return false;
	
	if(!mChannel)
		mChannel = new DTVChannel(dynamic_cast <SageTuner*> (this), mChannelName, NULL, 0);
	if(!mChannel) {
		hdhomerun_device_destroy(mDeviceConnection);
		mDeviceConnection = NULL;
		return false;
	}
	
	mDeviceModel = hdhomerun_device_get_model_str(mDeviceConnection);
	flog( "Native.log", "HDHomeRun Device(Tuner:\"%s\" Model:%s) is open\r\n", mChannelName, mDeviceModel );
	return true;
}

void HDHRDevice::closeDevice()
{
	if(mChannel) {
		mChannel->flush();
		delete mChannel;
		mChannel = NULL;
	}
	
	if(mDeviceConnection) {
		hdhomerun_device_destroy(mDeviceConnection);
		mDeviceConnection = NULL;
	}
	
	if(mOutputPath) {
		delete mOutputPath;
		mOutputPath = NULL;
	}
	flog( "Native.log", "HDHomeRun Device(Tuner:%s) is close\r\n", mChannelName );
}

void HDHRDevice::stopCapture()
{
	// stop the current encoding
	if(mCaptureThreadRunning) {
		void *foo;
		mKillCaptureThread = true;
		pthread_join(mCaptureThread, &foo);
	}
	
	if(mChannel) {
		mChannel->flush();
		mChannel->setOutputFile(NULL, 0);
	}
	
	if(mOutputFile) {
		fclose(mOutputFile);
		mOutputFile = NULL;
	}
	
	if(mOutputPath) {
		delete mOutputPath;
		mOutputPath = NULL;
	}
	mMaxFileSize = 0;
	mLastMealSize = 0;
}

// Identifies the tuner type
const char* HDHRDevice::getTunerType( )
{
	if ( strstr( mDeviceModel, "atsc" ) ) return "ATSC";
	if ( strstr( mDeviceModel, "dvbtc" ) ) return "DVB-TC"; // HD HomeRun 3 DUAL EU edition, can be either DVB-C or DVB-T
	if ( strstr( mDeviceModel, "dvbt" ) ) return "DVB-T";
	if ( strstr( mDeviceModel, "dvbc" ) ) return "DVB-C";
	if ( strstr( mDeviceModel, "dvbs" ) ) return "DVB-S";
	return "UNKOWN";
}

bool HDHRDevice::setupEncoding(char *path, size_t ringSize)
{
	pthread_t foo;
	
	stopCapture();
	if(!mChannel) return false;
	
	// path could be NULL, e.g., for data scanning
	if(path) {
		mOutputPath = new char[strlen(path)+1];
		strcpy(mOutputPath, path);
		mOutputFile = fopen(mOutputPath, "wb");
	} // else leave 'em NULL
	mMaxFileSize = ringSize;
	
	mLastMealSize = 0;
	mChannel->flush();
	mChannel->setOutputFile(mOutputFile, mMaxFileSize);
	mKillCaptureThread = false;
	pthread_create(&foo, NULL, HDHRDevice::captureThreadEntry, this);
	
	return true;
}

bool HDHRDevice::switchEncoding(char *path)
{
#ifndef FILETRANSITION
	return setupEncoding(path, mMaxFileSize);
#else
	// Does that really matter?
	mNextOutputPath = new char[strlen(path)+1];
	strcpy(mNextOutputPath, path);
	mNextOutputFile = fopen(mNextOutputPath, "wb");
	mChannel->setNextOutputFile(mNextOutputFile);
	// If we don't get data; the output file won't switch so we should just bail after 5 seconds
	// That's much better than letting this thread run forever since it'll be the Seeker which kills SageTV altogther then
	int maxWaits = 250;
	while(mChannel->hasNextOutputFile() && maxWaits-- > 0)
	{
		encoderIdle();
		eatEncoderData();
		usleep(20); // Wait a bit so we don't consume all CPU in this loop
	}
	delete mOutputPath;
	mOutputPath=mNextOutputPath;
	mNextOutputPath = NULL;
	mOutputFile = mNextOutputFile ;
	mNextOutputFile = NULL;
	return true;
#endif
}

void HDHRDevice::closeEncoding()
{
	stopCapture();
}

void HDHRDevice::encoderIdle()
{
	if(mChannel) mChannel->idle();
}

off_t HDHRDevice::eatEncoderData()
{
	off_t count = 0;
	if(mChannel) {
		off_t newSize = mChannel->getProcessedByteCount();
		count = newSize - mLastMealSize;
		mLastMealSize = newSize;
	}
	return count;
}

off_t HDHRDevice::getOutputData()
{
	off_t count = 0;
	if(mChannel) {
		off_t newSize = mChannel->getOutputByteCount();
		//count = newSize - mLastMealSize;
		mLastMealSize = newSize;
		return newSize;
	}
	return count;
}

bool HDHRDevice::setChannel(char *channelName, bool autotune, int streamFormat)
{
	// standard and source should already be configured at this point...
	mScanning = false;
	if(channelName ? strcmp(channelName, "0") : 0) {
		strncpy(mTuningParams.channel, channelName, sizeof(mTuningParams.channel));
		mTuningParams.tuningFrequency = 0;
	} // otherwise use last tuned channel
	mTuningParams.aft = (int)autotune;
	mTuningParams.outputFormat = (streamFormat == 31) ? 1 : 0; // 31 = MPEG PS, 32 = MPEG TS
	
	if(mChannel) return mChannel->setTuning(&mTuningParams);
	return false;
}

char *HDHRDevice::scanChannel(char *channelName, char *region, int streamFormat)
{
	if(mChannel) {
			// we'll need this later...
		mScanning = true;
		strncpy(mTuningParams.channel, channelName, sizeof(mTuningParams.channel));
		mTuningParams.tuningFrequency = 0;
		mTuningParams.aft = true;
		mTuningParams.outputFormat = (streamFormat == 31) ? 1 : 0; // 31 = MPEG PS, 32 = MPEG TS
		
		return mChannel->scanChannel(channelName, region, mTuningParams.outputFormat);
	}
	return NULL;
}

bool HDHRDevice::setInput(char *sigFormat, int countryCode, int videoFormat)
{
	bool result = true;
	
	// initialize tuning params
	if(sigFormat) {
		mTuningParams.channel[0] = '\0';
		mTuningParams.tuningFrequency = 0;
		SageTuner_SetInputInfo(&mTuningParams, 100, sigFormat, countryCode, videoFormat);
	}

	mChannel->flush();
		
	if(mChannel) {
		// prepare it for the desired format and standard
		mChannel->setTuning(&mTuningParams);
	} else {
		result = false;
		flog( "Native.log",  "Unable to allocate DTVChannel instance!\r\n");
	}
	flog( "Native.log",  "setInput country:%d sigFormat:%s, videoFormat:%d\r\n", mTuningParams.countryCode,  sigFormat, videoFormat );
	return result;
}

char *HDHRDevice::getBroadcastStandard()
{
	if(mChannel) return mChannel->getBroadcastStandard();
	return NULL;
}

int HDHRDevice::getSignalStrength()
{
	bool locked;
	int strength;
	getTuningStatus(locked, strength);
	return (locked ? strength : 0);
}

void *HDHRDevice::captureThreadEntry(void *arg)
{
	HDHRDevice *dev = static_cast<HDHRDevice*> (arg);
	if(dev) dev->captureThread();
	return NULL;
}

void HDHRDevice::captureThread()
{
	unsigned char *buf = NULL;
	int result;
	
	if(!mChannel) return;
	if(!mDeviceConnection) return;
	
	mCaptureThread = pthread_self();
	mCaptureThreadRunning = true;
	
//	flog( "Native.log",  "HDHomeRun: native capture thread starting\r\n");
	
	// start the device
	result = hdhomerun_device_stream_start(mDeviceConnection);
	if(result <= 0) {
		flog( "Native.log",  "HDHomeRun: Error starting stream\r\n");
		return;
	}
	
	while(!mKillCaptureThread) {
		size_t actual_size;
		
		// every 64ms read data from the hdhr device and push it to mChannel
		usleep(64000);
		
		buf = hdhomerun_device_stream_recv(mDeviceConnection, VIDEO_DATA_BUFFER_SIZE_1S, &actual_size);
		if(!buf) continue;
		
		mChannel->pushData(buf, actual_size);
	}
	hdhomerun_device_stream_flush(mDeviceConnection);
	hdhomerun_device_stream_stop(mDeviceConnection);
	
	mCaptureThreadRunning = false;
}
