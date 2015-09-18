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

#include "SageTuner.h"
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#if !defined(SAGE_DTV_ONLY)
 #include "TuningTables.h"
#endif

int SageTuner_SetInputInfo(SageTuningParams *params, int inputType, const char *sigFormat, int countryCode, int format)
{
	if((inputType != 100) && (inputType != 99)) {
		// medium is for analog only...
		if(!strcmp(sigFormat, "Air"))
			params->medium = kSageTunerMedium_Terrestrial;
		else if(!strcmp(sigFormat, "Cable"))
			params->medium = kSageTunerMedium_Cable;
		else if(!strcmp(sigFormat, "HRC"))
			params->medium = kSageTunerMedium_CableHRC;
		else {
			fprintf(stderr, "Unknown tuning medium: %s\n", sigFormat);
			return -1;
		}
		
		params->tuningStandard = format;
	} else {
		params->tuningStandard = 0; // DTVChannel sets this when it attempts to tune a channel...
	}
	
//	params->outputFormat = 0; // file format, to be set by someone else...
	
	// sigFormat will be one of: "Air", "Cable", "HRC", "OTAQAM", "UserDefined"
	// digital sources will be funnelled through the Channel interface, so don't worry about trying to decipher them now
	strncpy(params->tunerMode, sigFormat, 31);
	
	// clear isoCountry, we don't need it since we already have countryCode
	params->isoCountry[0] = params->isoCountry[1] = params->isoCountry[2] = '\0';
	params->countryCode = countryCode;
	
	return 0;
}

#if !defined(SAGE_DTV_ONLY)
// analog tuning tables are required here...

int SageTuner_SetChannelInfo(SageTuningParams *params, const char *channelName, int aftFlag)
{
	int ctIndex = 0;
	
	ctIndex = GetTuningTableIndex((long)params->countryCode, params->tunerMode);
	if(ctIndex > 0) {
		uint32_t freq = GetTuningFrequency(ctIndex, channelName);
		if(freq) {
			params->tuningFrequency = freq;
			strncpy(params->channel, channelName, 15);
		} else return -1;
	} else {
		params->tuningFrequency = 0;
		return -1;
	}
	
	return 0;
}

// start with 2x the normal tuning step
#define kTuningFreqStep 125000

int SageTuner_DoAFTScan(void * /* (SageTuner *) */ inTuner, SageTuningParams *params)
{
	SageTuner *tuner = static_cast<SageTuner*> (inTuner);
	uint32_t lowFreq, highFreq, baseFreq;
	bool done = false, hilo = false;
	int result = 0;
	
//	fprintf(stderr, "DoAFTScan: Starting AFT scan on channel \"%s\"\n", params->channel);
	
	if(!tuner) {
		fprintf(stderr, "DoAFTScan: Invalid tuner!\n");
		return -1;
	}
	
	if(!params->tuningFrequency) {
		fprintf(stderr, "DoAFTScan: channel info has no base freq?!?\n");
		return -1;
	}
	
	// start with base frequency, increment then decrement a step value giving the tuner time to detect a signal each step
	baseFreq = lowFreq = params->tuningFrequency;
	highFreq = params->tuningFrequency + kTuningFreqStep;
	do {
		int count;
		bool locked = false;
		int strength = 0;
		
		if(!hilo) {
			params->tuningFrequency = lowFreq;
			lowFreq -= kTuningFreqStep;
			// abort when we get too far away (2 MHz either side)
			if((baseFreq - lowFreq) >= 2000000) done = true;
		} else {
			params->tuningFrequency = highFreq;
			highFreq += kTuningFreqStep;
		}
		hilo = !hilo;
		
		if(tuner->setTuning(params) < 0) {
			fprintf(stderr, "DoAFTScan: tuner returned an error (%d), aborting\n", result);
			return -1;
		}
		
		for(count=0; count < 10; count++) {
			// wait a bit for it to settle, using PLL settle times from the Trinity driver seems to work
//			usleep(20000);
			if(tuner->getTuningStatus(locked, strength) < 0) return -1;
			if(locked) {
				result = strength;
				done = true;
				// TODO: get AFT status to fine tune (xc3028 doesn't need this so we're ok for now)
				break; // found a signal, we're done
			}
		}
		
	} while(!done);
	
	return result;
}

#endif
