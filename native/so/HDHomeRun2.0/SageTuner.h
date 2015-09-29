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

#ifndef __SAGE_TUNER_H
#define __SAGE_TUNER_H

#include "Channel.h"

// analog constants match those defined in TVTuningFrequencies.java
// digital constants are different since the input itself defines the standard
#define kSageTunerStandard_Unknown 0 // (DTV) we'll be told later

#define kSageTunerStandard_NTSC_M 0x00000001
#define kSageTunerStandard_NTSC_M_J 0x00000002
#define kSageTunerStandard_NTSC_433 0x00000004
#define kSageTunerStandard_PAL_B 0x00000010
#define kSageTunerStandard_PAL_D 0x00000020
#define kSageTunerStandard_PAL_H 0x00000080
#define kSageTunerStandard_PAL_I 0x00000100
#define kSageTunerStandard_PAL_M 0x00000200
#define kSageTunerStandard_PAL_N 0x00000400
#define kSageTunerStandard_PAL_60 0x00000800
#define kSageTunerStandard_SECAM_B 0x00001000
#define kSageTunerStandard_SECAM_D 0x00002000
#define kSageTunerStandard_SECAM_G 0x00004000
#define kSageTunerStandard_SECAM_H 0x00008000
#define kSageTunerStandard_SECAM_K 0x00010000
#define kSageTunerStandard_SECAM_K1 0x00020000
#define kSageTunerStandard_SECAM_L 0x00040000
#define kSageTunerStandard_SECAM_L1 0x00080000

#define kSageTunerStandardMask_NTSC		0x00000007
#define kSageTunerStandardMask_PAL		0x00000FB0
#define kSageTunerStandardMask_SECAM	0x000FF000

	// these are for configuring the physical tuner, use when Channel tells us to switch via one of the SageTuneXXX calls
#define kSageTunerStandard_ATSC 0x00100000
#define kSageTunerStandard_QAM 0x00200000
#define kSageTunerStandard_DVB_T 0x00400000
#define kSageTunerStandard_DVB_S 0x00800000
#define kSageTunerStandard_DVB_C 0x01000000

	// we only need to know medium for analog formats
#define kSageTunerMedium_Terrestrial 1	// aka: antenna
#define kSageTunerMedium_Cable 2
#define kSageTunerMedium_CableHRC 3		// subtract 1.25 MHz except for channel 5 add 750 KHz (78MHz)

// tuner status flags
enum {
	kSageTunerStatus_HasSignal = 0x0001,	// set if a signal is detected, doesn't imply a good signal!
	kSageTunerStatus_CarrierLock = 0x0002,	// has a valid signal locked
	kSageTunerStatus_HasSync = 0x0004,
	kSageTunerStatus_HasViterbi = 0x0008,	// should be set for good QAM (DVB?) tuning
};

// 	Modulation values: (Use 8VSB for OTA ATSC tuning)
enum {
	kSageTunerModulation_16QAM = 1,
	kSageTunerModulation_32QAM = 2,
	kSageTunerModulation_64QAM = 3,
	kSageTunerModulation_80QAM = 4,
	kSageTunerModulation_96QAM = 5,
	kSageTunerModulation_112QAM = 6,
	kSageTunerModulation_128QAM = 7,
	kSageTunerModulation_160QAM = 8,
	kSageTunerModulation_192QAM = 9,
	kSageTunerModulation_224QAM = 10,
	kSageTunerModulation_256QAM = 11,
	kSageTunerModulation_320QAM = 12,
	kSageTunerModulation_384QAM = 13,
	kSageTunerModulation_448QAM = 14,
	kSageTunerModulation_512QAM = 15,
	kSageTunerModulation_640QAM = 16,
	kSageTunerModulation_768QAM = 17,
	kSageTunerModulation_896QAM = 18,
	kSageTunerModulation_1024QAM = 19,
	kSageTunerModulation_QPSK = 20,
	kSageTunerModulation_BPSK = 21,
	kSageTunerModulation_OQPSK = 22,
	kSageTunerModulation_8VSB = 23,
	kSageTunerModulation_16VSB = 24
};

/*
	TODO: make enums for these
	
	FEC:
		1 : VBC
		2 : RS/2048
		3 : maximum
	
	FEC rates:
		1 : fec1/2
		2 : fec2/3
		3 : fec3/4
		4 : fec3/5
		5 : fec4/5
		6 : fec5/6
		7 : fec5/11
		8 : fec7/8
		9 : fec8/9
	
	Polarization:
		1 : linear H
		2 : linear V
		3 : circular L
		4 : circular R
 */

/*
	Data struct containing as much tuning info as possible
	country code, format, standard and source are passed from the core app
	then channel (then frequency) and auto-tune flag sent in a separate call
	
	At least one of standard or frequency (derived from channel) should be specified
	if one is not specified, then that setting will not be changed
	
	DTV params may not be known until later, in which case defer (physical) tuning until it is known
	dtvParams will be filled in when the Channel library calls us, it should be ignored until then
 */

// TODO: revisit when we get QAM and DVB-[SCT] hardware
typedef struct {
		// at least one of the following should be specified
	char isoCountry[3];			// two letter country code, "" if unknown
	int countryCode;			// ISO country code
	
	int medium;					// (analog) one of the kSageTunerMedium constants
	uint32_t tuningStandard;		// one of the kSageTunerStandard constants
	
		// DTV values
	char tunerMode[32];			// used by Channel to configure DTV sources
	int outputFormat;			// 0:TS, 1:PS
	
	char channel[32];
	uint32_t tuningFrequency;		// dup of freq specified below for DTV
	int aft;					// non-zero to turn AFT circuit on
	
	// dtv tuning params
	union {
		ATSC_FREQ atsc;
		QAM_FREQ qam;
		DVB_T_FREQ dvbt;
		DVB_C_FREQ dvbc;
		DVB_S_FREQ dvbs;
	} dtvParams;
} SageTuningParams;

// fills in a tuning params struct with params from a setInput call from the app
int SageTuner_SetInputInfo(SageTuningParams *params, int inputType, const char *sigFormat, int countryCode, int format);

#if !defined(SAGE_DTV_ONLY)
// fills in channel name, frequency and aft flag from a setChannel or scanChannel call from the app
// USE FOR ANALOG ONLY!!! Channel handles DTV sources
int SageTuner_SetChannelInfo(SageTuningParams *params, const char *channelName, int aftFlag);

// -1 = error
// 0 = no signal lock
// >0 = signal lock (signal strength 1-100 if possible, otherwise 100)
int SageTuner_DoAFTScan(void * /* (SageTuner *) */ tuner, SageTuningParams *params);

#endif

#if defined(__cplusplus)
class SageTuner {
	public:
		virtual int setTuning(SageTuningParams *params) = 0;
		// strength is signal strength from 0-100
		// locked is redundant since return value indicates lock status...
		// we should just return:
		// < 0 on error
		// 0 for no lock
		// > 0 for lock and signal strength (1-100)
		virtual int getTuningStatus(bool& locked, int& strength) = 0;
		virtual bool allowDryTune() = 0;
	
		// returns true if the tuner can filter by program or PIDs
		virtual bool hasPIDFilter() = 0;
		
		// tell tuner to filter by program or PIDs
		// if program and/or pidCount is zero, then disable the filter and pass the entire raw stream
		// tuner decides which method to use
		// return 0 for success, non-zero for failure
		virtual int setPIDFilter(unsigned short program, int pidCount, unsigned short *pidList) = 0;

		//tell tuner to filter by program, tuner get PIDS by itself
		//virtual int setPIDFilterByProgram(unsigned short program) = 0;

		//tuner will tell type ATSC, DVB-T, DVB-S, DVB-S or "UNKNOWN"
		virtual const char* getTunerType( ) = 0;

};
#endif // __cplusplus

#endif // __SAGE_TUNER_H
