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
#include "DShowCapture.h"
#include "DShowUtilities.h"
#include "CreativeVBDVCR.h"
#include "uniapi.h"

void configureVBDVCREncoder(SageTVMPEG2EncodingParameters *pythonParams)
{
	// This is where we set the MPEG2 params for this card
	char vbDevName[64];
	//SPRINTF(vbDevName, sizeof(vbDevName), "\\\\.\\vb%d", GetIntField(env, jencsetup, "videoCaptureDeviceNum"));
	strcpy(vbDevName, "\\\\.\\tar");
	HANDLE hFile = CreateFile(vbDevName,           // open DVCR device
					GENERIC_WRITE,              // open for writing 
					FILE_SHARE_WRITE,           // share for writing 
					NULL,                      // no security 
					OPEN_EXISTING,             // existing file only 
					FILE_ATTRIBUTE_NORMAL,     // normal file 
					NULL);						// no attr. template 

	if (hFile != INVALID_HANDLE_VALUE)
	{
		DWORD vfConfig[5];
		DWORD outData[10];
		DWORD outCount;
		vfConfig[0] = pythonParams->videobitrate/1000; // video birate in kbps
		vfConfig[1] = 0; // VBR doesn't work right with Creative
		vfConfig[2] = 0; // resolution. 640x480 default
		if (pythonParams->width == 480)
			vfConfig[2] = 2;
		else if (pythonParams->width == 320 || pythonParams->width == 352)
			vfConfig[2] = 1;
		vfConfig[3] = pythonParams->audiosampling;
		if (vfConfig[3] != 32000 && vfConfig[3] != 48000)
			vfConfig[3] = 48000;
		vfConfig[4] = 0; // ???
		DeviceIoControl(hFile, 0x222018, (void*)vfConfig, 20, outData, 40, &outCount , NULL);
		CloseHandle(hFile);
	}
}
