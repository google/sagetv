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

#ifndef __HDHRDEVICE_H
#define __HDHRDEVICE_H

#include <sys/types.h>

#include "hdhomerun_includes.h"
#include "DTVChannel.h"

#ifndef FILETRANSITION
#define FILETRANSITION
#endif

void enable_hdhrdevice_native_log();

class HDHRDevice : public SageTuner {
public:
	HDHRDevice(hdhomerun_discover_device_t disco, int tuner);
	~HDHRDevice();
	
	char *getName() {
		return mDeviceName;
	}
	
		// only use to check if a device has already been discovered!
	bool isDevice(hdhomerun_discover_device_t disco) {
		return (disco.device_id == mDeviceInfo.device_id);
	}
	
	bool isDevice(char *checkName) {
		return (strcmp(checkName, mDeviceName) == 0);
	}
	
		// returns true if device ID is valid and device was discovered
	bool validate();
	
	// SageTuner API
	virtual int setTuning(SageTuningParams *params);
	virtual int getTuningStatus(bool& locked, int& strength);
	virtual bool allowDryTune();
	virtual bool hasPIDFilter() {return true;}
	virtual int setPIDFilterByProgram(unsigned short program );
	virtual int setPIDFilter(unsigned short program, int pidCount, unsigned short *pidList);
	virtual const char* getTunerType( );
	void setFilterEnable(bool enable);
	
	// device API
	bool openDevice();
	void closeDevice();
	
	bool setupEncoding(char *path, size_t ringSize);
	bool switchEncoding(char *path);
	void closeEncoding();
	void encoderIdle(); // called during eatEncoderData
	off_t eatEncoderData();//processed bytes
	off_t getOutputData();//fwrite bytes
	bool setChannel(char *channelName, bool autotune, int streamFormat);
	char *scanChannel(char *channelName, char *region, int streamFormat);
	bool setInput(char *sigFormat, int countryCode, int videoFormat);
	char *getBroadcastStandard();
	int getSignalStrength();
	
	static void *captureThreadEntry(void *arg);
	
private:
	char mDeviceName[64];	// name as reported to the app
	char mChannelName[67];
	const char* mDeviceModel;
	
	struct hdhomerun_discover_device_t mDeviceInfo; // filled in when we discover the device
	int mTunerIndex;	// 0 or 1, the tuner this device is associated with
	
	struct hdhomerun_device_t *mDeviceConnection;
	SageTuningParams mTuningParams;
	bool mScanning;			// set when we're data scanning (retain channel name for later tuning)
	bool mEnableFilter;		// forces the PID filter off, usually when scanning for EPG data
	unsigned short lastFilterProgram;
	DTVChannel *mChannel;
	char *mOutputPath;
	FILE *mOutputFile;
	size_t mMaxFileSize;
	off_t mLastMealSize;
#ifdef FILETRANSITION
	char *mNextOutputPath;
	FILE *mNextOutputFile;
#endif

	pthread_t mCaptureThread;
	bool mCaptureThreadRunning;
	bool mKillCaptureThread;
	
	// tuning variables
	struct hdhomerun_channel_list_t *mDeviceChannelList;	// needed to look up frequencies (HDHR uses center freqs, not channel freqs)
	const char *mDeviceChannelMap;							// tracks which channel table we're using
	
	void stopCapture();
	void captureThread();
};

#endif //__HDHRDEVICE_H
