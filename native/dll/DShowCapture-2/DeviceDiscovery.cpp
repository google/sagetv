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
#include <streams.h>
#include "../DShowPlayer/JeffMixer.h"
#include "DShowCapture.h"
#include "../../include/guids.h"
#include "../../include/sage_DShowCaptureDevice.h"
#include "DShowUtilities.h"
#include "FilterGraphTools.h"
#include "../../include/iTSSplitter.h"
#include "uniapi.h"
extern FilterGraphTools graphTools;

#define MAX_DEV_NAMES 512
typedef struct {
	char FriendlyName[512];
	char hardware_loc[128];
	char hardware_id[256];
	//char device_desc[128];
	char index;
} DEVNAME;

jobjectArray getDeviceNames( JNIEnv*, REFCLSID );
static int getDeviceNameList( JNIEnv *env, REFCLSID devClass, DEVNAME *DevName, int maxNameNum );
static int PurgeNameList( JNIEnv *env, DEVNAME* DevName, int numDev, REFCLSID devClass );
static int MergeNameList( JNIEnv *env, DEVNAME* DevName, int numDev, DEVNAME* DevName1, int numDev1 ); 
static int LoadDeviceNameFrormRegistry( JNIEnv *env, DEVNAME* DevName, int maxNum );
extern "C" { 
	int SageNativeLogCheck(  );
	void EnableNativeLog( bool Enable  );
	void DisableNativeLog( bool Enable  );
}
/*
 * Class:     sage_DShowCaptureDevice
 * Method:    getAudioInputPaths0
 * Signature: ()[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_DShowCaptureDevice_getAudioInputPaths0
  (JNIEnv *env, jclass jc)
{
	static jclass stringClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/String"));
	int numDevs = mixerGetNumDevs();
	if (numDevs < 1) 
	{
		return env->NewObjectArray(0, stringClass, NULL);
	}
	HMIXER m_HMixer;
	MMRESULT mmr;
	mmr = mixerOpen(&m_HMixer, 0, 0, 0L, CALLBACK_NULL);
	if (mmr != MMSYSERR_NOERROR) return NULL;
// get dwLineID
	MIXERLINE mxl;
	mxl.cbStruct = sizeof(MIXERLINE);
// DstType
	mxl.dwComponentType = MIXERLINE_COMPONENTTYPE_DST_SPEAKERS;
	if (mixerGetLineInfo((HMIXEROBJ)m_HMixer, &mxl, MIXER_OBJECTF_HMIXER | MIXER_GETLINEINFOF_COMPONENTTYPE)
		!= MMSYSERR_NOERROR) return NULL;
// SrcType
	UINT nconn = mxl.cConnections;
	DWORD DstIndex = mxl.dwDestination;
	jobjectArray rv = env->NewObjectArray(nconn, stringClass, NULL);

	for( UINT j = 0; j < nconn; j++ )
	{
		mxl.cbStruct = sizeof( MIXERLINE );
		mxl.dwSource = j;
		mxl.dwDestination = DstIndex;
		if(mixerGetLineInfo( ( HMIXEROBJ )m_HMixer,
			&mxl, MIXER_GETLINEINFOF_SOURCE ) != MMSYSERR_NOERROR) return NULL;
		env->SetObjectArrayElement(rv, j, env->NewStringUTF(mxl.szName));
	}
	return rv;
}

/*
 * Class:     sage_DShowCaptureDevice
 * Method:    getDevicesInCategory0
 * Signature: (Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_DShowCaptureDevice_getDevicesInCategory0
  (JNIEnv *env, jclass jc, jstring js)
{
	CoInitializeEx(NULL, COM_THREADING_MODE);
	const jchar* jws = env->GetStringChars(js, NULL);
	CLSID myClsid;

	HRESULT hr = CLSIDFromString((LPOLESTR)jws, &myClsid);
	env->ReleaseStringChars(js, jws);
	jobjectArray rv;

	if (SUCCEEDED(hr))
	{
		rv = getDeviceNames(env, myClsid);
	}
	else
	{
		static jclass stringClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/String"));
		rv = env->NewObjectArray(0, stringClass, NULL);
	}
//	CoUninitialize();
	return rv;
}
/*
 * Class:     sage_DShowCaptureDevice
 * Method:    getDevicesInCategorys0
 * Signature: ([Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_DShowCaptureDevice_getDevicesInCategoryS0
  (JNIEnv *env, jclass jc, jobjectArray ja )
{
	CoInitializeEx(NULL, COM_THREADING_MODE);
	jobjectArray rv;
	CLSID myClsid;
	int i, numDev=0, numDev1=0;
	DEVNAME *pDevName, *pDevName1;
	
	//we put a global catch handle here to buffer overrun //ZQ
	_set_invalid_parameter_handler(globalInvalidParameterHandler);	
	slog(( env, "Setup globalInvalidParameterHandler\r\n" ));

	pDevName = new DEVNAME[MAX_DEV_NAMES];
	pDevName1 = new DEVNAME[MAX_DEV_NAMES];

	jsize size = env->GetArrayLength(ja);
	for ( i = 0; i<size; i++ )
	{
		jstring obj = (jstring)env->GetObjectArrayElement( ja, i );
		const jchar* jws = env->GetStringChars( obj, NULL);
		if ( jws != NULL && jws[0] != 0x0 )
		{
			char clsid[128];
			WideCharToMultiByte(CP_UTF8, 0, (LPCWSTR)jws, -1, clsid, 128, 0, 0);
			slog(( env, "Device detecting->:%s\r\n", clsid ));

			if ( strstr( (const char*)clsid, "VIRTUAL_TUNER" )!=NULL )
			{
				VIRTUAL_TUNER VirtalTunerList[8];
				numDev1 = LoadVirtualTunerList( env, VirtalTunerList, 8 );
				if ( numDev1 )
				{
					for (int i = 0; i < numDev1; i++)
					{
						strncpy( pDevName1[i].FriendlyName, VirtalTunerList[i].CaptureName, 
							                                        sizeof(pDevName[i].FriendlyName));
						SPRINTF( pDevName1[i].hardware_loc, sizeof(pDevName1[i].hardware_loc), "virtual-%d", i );
						SPRINTF( pDevName1[i].hardware_id,  sizeof(pDevName1[i].hardware_id), "virtual tuner-%d", i );
						slog(( env, "Virtual tuner:%s, mask:%d '%s'\r\n", VirtalTunerList[i].CaptureName, 
														VirtalTunerList[i].Mask, VirtalTunerList[i].Param ));
						numDev=MergeNameList( env, pDevName, numDev, pDevName1, numDev1 );
					}
				}
			} else
			{
			
				HRESULT hr = CLSIDFromString((LPOLESTR)jws, &myClsid);

				numDev1 = getDeviceNameList( env, myClsid, pDevName1, MAX_DEV_NAMES );

				for ( int _s = 0; _s < numDev1; _s++ ) 
					slog(( env, "Device found--'%s'\r\n", pDevName1[_s].FriendlyName ));

				numDev1 = PurgeNameList( env, pDevName1, numDev1, myClsid );
				numDev=MergeNameList( env, pDevName, numDev, pDevName1, numDev1 );
			}

			env->ReleaseStringChars( obj, jws );
		}
	}

	////check if there is specified devices in registry
	//if ( KSCATEGORY_BDA_RECEIVER_COMPONENT == myClsid )
	//{
	//	numDev += LoadDeviceNameFrormRegistry( env, &pDevName[numDev], MAX_DEV_NAMES-numDev );
	//}

	if ( numDev == 0 )
	{
		static jclass stringClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/String"));
		rv = env->NewObjectArray(0, stringClass, NULL);
	} else
	{
		static jclass stringClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/String"));
		rv = env->NewObjectArray(numDev, stringClass, NULL);
		for ( i = 0; i < numDev; i++)
		{
			env->SetObjectArrayElement( rv, i, env->NewStringUTF(pDevName[i].FriendlyName) );
			slog(( env, "Device found '%s'\r\n", pDevName[i].FriendlyName )); 
		}
	}

	delete pDevName;
	delete pDevName1;
	return rv;

}



jobjectArray getDeviceNames(JNIEnv* env, REFCLSID devClass)
{
    HRESULT hr;
    ICreateDevEnum *pSysDevEnum = NULL;
    IEnumMoniker *pEnum = NULL;
    IMoniker *pMoniker = NULL;
	int numDevs = 0;

	SageNativeLogCheck(  );

    hr = CoCreateInstance(CLSID_SystemDeviceEnum, NULL, 
        CLSCTX_INPROC_SERVER, IID_ICreateDevEnum, 
        (void**)&pSysDevEnum);
    if (FAILED(hr))
    	return 0;
    hr = pSysDevEnum->CreateClassEnumerator(devClass, &pEnum, 0);
    if (FAILED(hr))
	{
		pSysDevEnum->Release();
    	return 0;
	}
	jstring* devNames = new jstring[MAX_DEV_NAMES];
    while (pEnum && S_OK == pEnum->Next(1, &pMoniker, NULL) && numDevs < 512)
    {
		IPropertyBag *pPropBag = NULL;
		hr = pMoniker->BindToStorage(0, 0, IID_IPropertyBag, 
								(void **)&pPropBag);
		if (SUCCEEDED(hr))
		{
			VARIANT var;
			VariantInit(&var);
			hr = pPropBag->Read(L"FriendlyName", &var, 0);
			if (SUCCEEDED(hr))
			{
				char conv[512];
				WideCharToMultiByte(CP_UTF8, 0, var.bstrVal, -1, conv, 512, 0, 0);
				if (numDevs < MAX_DEV_NAMES)
					devNames[numDevs++] = env->NewStringUTF(conv);

			}
			VariantClear(&var);
			SAFE_RELEASE(pPropBag);
		}
        SAFE_RELEASE(pMoniker);
    }
    SAFE_RELEASE(pEnum);
    SAFE_RELEASE(pSysDevEnum);
	static jclass stringClass = (jclass) env->NewGlobalRef(env->FindClass("java/lang/String"));
	jobjectArray objArray = env->NewObjectArray(numDevs, stringClass, NULL);
	for (int i = 0; i < numDevs; i++)
		env->SetObjectArrayElement(objArray, i, devNames[i]);
	delete [] devNames;
	return objArray;
}

static int getDeviceNameList( JNIEnv *env, REFCLSID devClass, DEVNAME *DevName, int maxNameNum )
{
    HRESULT hr;
    ICreateDevEnum *pSysDevEnum = NULL;
    IEnumMoniker *pEnum = NULL;
    IMoniker *pMoniker = NULL;
	int numDevs = 0;
    hr = CoCreateInstance(CLSID_SystemDeviceEnum, NULL, 
        CLSCTX_INPROC_SERVER, IID_ICreateDevEnum, 
        (void**)&pSysDevEnum);
    if (FAILED(hr))
    	return 0;
    hr = pSysDevEnum->CreateClassEnumerator(devClass, &pEnum, 0);
    if (FAILED(hr))
	{
		pSysDevEnum->Release();
		slog(( env, "GetDeviceName failed hr=0x%x\r\n", hr ));
    	return 0;
	}
    while (pEnum && S_OK == pEnum->Next(1, &pMoniker, NULL) && numDevs < maxNameNum )
    {
		IPropertyBag *pPropBag = NULL;
		hr = pMoniker->BindToStorage(0, 0, IID_IPropertyBag, 
								(void **)&pPropBag);
		if (SUCCEEDED(hr))
		{
			VARIANT var;
			VariantInit(&var);
			hr = pPropBag->Read(L"FriendlyName", &var, 0);
			if (SUCCEEDED(hr))
			{
				WideCharToMultiByte(CP_UTF8, 0, var.bstrVal, -1, DevName[numDevs].FriendlyName, 512, 0, 0);

				LPOLESTR pStr = NULL;
				IBindCtx *pBindCtx;
				hr = CreateBindCtx(0, &pBindCtx);
				if (SUCCEEDED(hr))
				{
					hr = pMoniker->GetDisplayName(pBindCtx, NULL, &pStr);
					if (SUCCEEDED(hr))
					{
						char devname[512];
						DEVICE_DRV_INF DrvInfo;
						WideCharToMultiByte(CP_ACP, 0, pStr, -1, devname, sizeof(devname), 0, 0);
						GetDeviceInfo( devname, &DrvInfo );
						if ( DrvInfo.state )
						{
							strncpy( DevName[numDevs].hardware_loc, DrvInfo.hardware_loc, sizeof(DevName[numDevs].hardware_loc) );
							strncpy( DevName[numDevs].hardware_id,  DrvInfo.hardware_id, sizeof(DevName[numDevs].hardware_id) );
							//strncpy( DevName[numDevs].device_desc,  DrvInfo.device_desc, sizeof(DevName[numDevs].device_desc) );
						}
						else
							DevName[numDevs].hardware_loc[0] = 0x0;
						CoTaskMemFree(pStr);
					}
					pBindCtx->Release();
				}
				numDevs++;
			}
			VariantClear(&var);
			SAFE_RELEASE(pPropBag);
		}
		else
		{
			slog(( env, "GetDeviceName failed BindToStorage hr=0x%x\r\n", hr ));
		}
        SAFE_RELEASE(pMoniker);
    }
    SAFE_RELEASE(pEnum);
    SAFE_RELEASE(pSysDevEnum);

	return numDevs;
}

BOOL CheckFakeBDACrossBar( JNIEnv *env, char* capFiltName, int CapFiltNum, char* BDAFiltDevName, int BDAFiltDevNameSize );

static int PurgeNameList( JNIEnv *env, DEVNAME* DevName, int numDev, REFCLSID devClass )
{
	int i, j, num=0;
	char szDeviceType[64]; 
    // slog((env, "PurgeNameList() Entry: DevName[0] '%s', numDev to purge = %d \r\n", DevName[0].FriendlyName, numDev));
	if ( devClass == AM_KSCATEGORY_CAPTURE )
	{
		for ( i = 0; i<numDev; i++ )
		{
			szDeviceType[0] = 0;
			ReadAdditionalSettingString( env, DevName[i].FriendlyName, "DeviceType", szDeviceType, sizeof(szDeviceType) );
			if ( szDeviceType[0] )
			{
				if ( !stricmp( szDeviceType, "digital" ) )
				{
					slog((env, "Remove analog tuner in capture category '%s' \r\n", DevName[i].FriendlyName ));
					DevName[i].FriendlyName[0] = 0;
					num++;
				}
			}
		}
	}

	if ( num == 0 )
		return numDev;

	j = 0;
	for ( i = 0; i<numDev; i++ )
	{
		if ( DevName[i].FriendlyName[0] )
			 DevName[j++] = DevName[i];
	}

	return j;
}

/*  Purpose: Merge devices in DevName1[] into DevName[].  
        Resultant DevName[] will not contain duplicate device:hardware_loc entries.

    DevName (in/out) - pointer to an existing array of devices
    numDev (in) - # of elements in DevName[]
    DevName1 (in) - pointer to an array of new devices which are candidates to be added to DevName[]
    numDev1 (in) - # of elements in DevName1[]

    Return: resultant number of elements in DevName[]

    Notes: Overall, the general assumtion is that a given Video Capture Source will only have a 
        single BDA Reciever Component associated with it at the Source's hardware_loc.
        "Hybrid" Video Capture Sources have multiple Receivers at a given hardware_loc, 
        but they can't be used at the same time.
        Here we also handle a few special cases that DO have multiple Receivers associated with
        a single Video Capture Source (at one hardware_loc) that CAN be used at the same time.
    */
static int MergeNameList( JNIEnv *env, DEVNAME* DevName, int numDev, DEVNAME* DevName1, int numDev1 )
{
	int i, j, k, num;
	num = numDev; 
    slog((env, "MergeNameList() Entry: DevName[0] '%s', numDev=%d, new DevName1[0] '%s', numDev1=%d \r\n", 
        DevName[0].FriendlyName, numDev, DevName1[0].FriendlyName, numDev1));
	for ( i = 0; i<numDev1; i++ )
	{
		//ZQ hard code for anysee tuner
		if ( !strncmp( DevName1[i].FriendlyName, "anysee Analog Capturer", 21 ) ||
			 !strncmp( DevName1[i].FriendlyName, "anysee BDA Digital Capturer", 26 )  ||
			 !strncmp( DevName1[i].FriendlyName, "anysee BDA Digital Tuner(MCE DVB-" , 33 ) )
		{
			//slog((env, "Drop Anysee analog capture, as sagetv doesn't support its analog encoder.\r\n" ));
			continue; //drop Anysee analog capture
		}

		if ( numDev == 0 )
		{
			strncpy( DevName[num].FriendlyName, DevName1[i].FriendlyName, sizeof(DevName[num].FriendlyName)-1 );
			strncpy( DevName[num].hardware_loc, DevName1[i].hardware_loc, sizeof(DevName[num].hardware_loc)-1  );
			DevName[num].index = 0;
			for ( k = 0; k<num; k++ )
			{
				if ( !strcmp( DevName[num].FriendlyName, DevName[k].FriendlyName ) )
					DevName[num].index++;
			}
			num++;
			if ( num >= MAX_DEV_NAMES ) 
				return num;

		} else
		{
			if ( DevName1[i].hardware_loc[0] == 0x0 )
			{
				//DVBLink is missing "hardware_loc"
				if ( !strncmp( DevName1[i].FriendlyName, "DVBLink Capture #", 17 ) )
				{
					strncpy( DevName1[i].hardware_loc, DevName1[i].hardware_id, sizeof(DevName1[i].hardware_loc) );
				} else
				{
					slog((env, "Drop invalid device '%s', as we don't know hardware location.\r\n", DevName1[i].FriendlyName ));
					continue; //drop invalid device
				}
			}	

            // Is this new device already in the array of previously-added devices?
			for ( j = 0; j<numDev; j++ )
			{ 
                // slog((env, "FriendlyName '%s' at '%s', compare loc '%s', i=%d, j=%d \r\n", DevName1[i].FriendlyName, DevName1[i].hardware_loc, DevName[j].hardware_loc, i, j));
				if ( !strcmp( DevName[j].hardware_loc, DevName1[i].hardware_loc ) )
					break; // break if hardware_loc matches
			}
			
			// Special case: hard code for multiple Receivers which are found at the same hardware_loc
			if ( j < numDev )
			{
                // slog((env, "New DevName1[%d] '%s'; found previous DevName[%d] '%s' at hardware_loc '%s'\r\n", i, DevName1[i].FriendlyName, j, DevName[j].FriendlyName, DevName[j].hardware_loc));

                //ZQ hard code for anysee tuner
				//Anysee hybrid tuners share the same loc (USB) 
				if ( !strncmp( DevName[j].FriendlyName, "anysee BDA Digital Tuner", 22 ) )
					j = numDev;


                /* ----------------
                 * KSF: hardcode for Hauppauge WinTV-quadHD, which has 2 BDA Reciever Components 
                 * (885 TS Capture, 885 TS Capture 2) for both of it's 2 Video Capture Sources
                 * (i.e., at both of it's 2 PCIe hardware locations).
                 *
                 * When 'Hauppauge WinTV 885 Video Capture' was detected (earlier), "Hauppauge WinTV 885 TS Capture' 
                 * was also entered into DevName[] (due to registry contents).
                 * If new device is the 2nd TS Capture device, then add it, here.
                 */
                else if (!strncmp(DevName1[i].FriendlyName, "Hauppauge WinTV 885 TS Capture 2", 32))
                    j = numDev;


                /* ----------------
                 * KSF: hardcode for Hauppauge WinTV-dualHD usb stick, which has a single 'TS Capture' and 
                 * 2 Source Filters (ATSC Tuner, ATSC Tuner 2) at it's hardware location.
                 * We don't want user to see (or be able to select) the TS Capture device.
                 *
                 * The normal device-detection process finds the following (in this order):
                 * 1: Hauppauge WinTV-dualHD TS Capture (which would normally tend to bind to 'ATSC Tuner 2')
                 * 2: Hauppauge WinTV-dualHD ATSC Tuner 2
                 * 3: Hauppauge WinTV-dualHD ATSC Tuner (not sure why this one is found last; it's first in the current .inf)
                 *
                 * Desired result is that DevName[] contains both Tuner names, but not TS Capture 
                 */
                else if (!strncmp(DevName[j].FriendlyName, "Hauppauge WinTV-dualHD TS Capture", sizeof("Hauppauge WinTV-dualHD TS Capture"))) 
                {
                    // "Hauppauge WinTV-dualHD TS Capture" is present, replace it with the 
                    //  1st '...WinTV-dualHD ATSC Tuner *' found at same location (generally '...Tuner 2').
                    slog((env, "patch: replace old DevName[%d] '%s' with new DevName1[%d] '%s' \r\n", 
                        j, DevName[j].FriendlyName, i, DevName1[i].FriendlyName));

                    strncpy(DevName[j].FriendlyName, DevName1[i].FriendlyName, sizeof(DevName[j].FriendlyName) - 1);
                }

                // Add 2nd Tuner, either 'Hauppauge WinTV-dualHD ATSC Tuner' or 'Hauppauge WinTV-dualHD ATSC Tuner 2'
                else if (strstr(DevName1[i].FriendlyName, "Hauppauge WinTV-dualHD ATSC Tuner")) 
                {
                    slog((env, "patch: also add '%s' \r\n", DevName1[i].FriendlyName));
                    j = numDev;
                }

			}

			//not found the same device
			if ( j >= numDev )
			{     
				strncpy( DevName[num].FriendlyName, DevName1[i].FriendlyName, sizeof(DevName[num].FriendlyName)-1 );
				strncpy( DevName[num].hardware_loc, DevName1[i].hardware_loc, sizeof(DevName[num].hardware_loc)-1 );
				DevName[num].index = 0;
				for ( k = 0; k<num; k++ )
				{
					if ( !strcmp( DevName[num].FriendlyName, DevName[k].FriendlyName ) )                 
						DevName[num].index++;
				}
                // slog((env, "Added DevName[%d] '%s' (# %d) at loc:'%s' i=%d, j=%d \r\n", num, DevName[num].FriendlyName, DevName[num].index, DevName[num].hardware_loc, i, j));
				num++;
				if ( num >= MAX_DEV_NAMES ) 
					return num;
			}

		}

		//check if there is hybrid digital tuner for device
		char szHybridCapture[128]={0};
		char TunerType[16]={0};
		szHybridCapture[0]=0x0;
		TunerType[0] = 0x0;

		ReadAdditionalSettingString( env, DevName[num-1].FriendlyName, "hybrid", 
			                         szHybridCapture, sizeof(szHybridCapture) );
		if ( szHybridCapture[0] )
		{
			char szNewCaptureName[256];
			SPRINTF( szNewCaptureName, sizeof(szNewCaptureName), "%s#%d", szHybridCapture, DevName[num-1].index );
			ReadAdditionalSettingString( env, DevName[num-1].FriendlyName, szNewCaptureName, 
				                         TunerType, sizeof(TunerType) );
			if ( DevName[num-1].index == 0 && TunerType[0] == 0x0 ) //compatiable the old way
				ReadAdditionalSettingString( env, DevName[num-1].FriendlyName, szHybridCapture, 
				                             TunerType, sizeof(TunerType) );
		}
		if ( szHybridCapture[0] )
		{
			char NewCaptureName[512];
			if ( TunerType[0] == 0 )
				strncpy( NewCaptureName, szHybridCapture, sizeof( NewCaptureName ) );
			else
				SPRINTF( NewCaptureName, sizeof(NewCaptureName) , "%s<%s>-%d", szHybridCapture, TunerType, DevName[num-1].index  ); 
			slog((env, "found hybrid tuner '%s' for '%s' #%d (%d)\r\n", NewCaptureName,
				                                    DevName[num-1].FriendlyName, DevName[num-1].index, num-1 ) );
			bool IsHybrideTuner = true;

			char FilterName[256]={0};
			if ( CheckFakeBDACrossBar( env, NewCaptureName, DevName[num-1].index, FilterName, sizeof( FilterName ) ) )
			{
				if ( strstr( NewCaptureName, "Hauppauge WinTV 885 TS Capture") )
				{
					DEVICE_DRV_INF  CaptureDrvInfo={0};
					GetDeviceInfo( FilterName, &CaptureDrvInfo );
					if ( strstr( CaptureDrvInfo.device_desc, "HVR-1250") || strstr( CaptureDrvInfo.device_desc, "HVR-1255") ||
						 strstr( CaptureDrvInfo.device_desc, "HVR-1550")  )
					{
						slog((env, "it's a HVR-1250 share tuner, not a real hybride tuner! (%s) \r\n", CaptureDrvInfo.device_desc ) );				
						IsHybrideTuner = false;
					} else
					{
						slog((env, "It's a hybride tuner %s '%s'. \r\n", NewCaptureName, CaptureDrvInfo.device_desc ) );				
					}
				} else
				{
					DEVICE_DRV_INF  CaptureDrvInfo={0};
					GetDeviceInfo( FilterName, &CaptureDrvInfo );
					slog((env, "It's a hybride tuner %s '%s'. \r\n", NewCaptureName, CaptureDrvInfo.device_desc ) );					
				}
			}

			if ( IsHybrideTuner )
			{
				 //add a hybrid tuner into device list
				strncpy( DevName[num].FriendlyName, NewCaptureName, sizeof(DevName[num].FriendlyName) );
				strncpy( DevName[num].hardware_loc, DevName1[num-1].hardware_loc, sizeof(DevName[num].hardware_loc) );
				num++;
			}
		}

	}
			
	return num;
}

static int LoadDeviceNameFrormRegistry( JNIEnv *env, DEVNAME* DevName, int maxNum )
{
	int num = 0;
	char names[256]={0};
	HKEY hCaptureKey;
	const char *KeyName="SOFTWARE\\Frey Technologies\\Common\\AdditionalCaptureSetups\\BDA capture";

	if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, KeyName, 0, KEY_ALL_ACCESS, &hCaptureKey) == ERROR_SUCCESS)
	{
		DWORD hType;
		DWORD hSize = sizeof(names);
		RegQueryValueEx( hCaptureKey, "source", 0, &hType, (LPBYTE)names, &hSize );
		RegCloseKey( hCaptureKey );
		slog((env, "Get BDA device names specified in registry %s\r\n", names ) );
	} else return 0;

	char *p = names;
	char *pe;
	int len;
	while ( *p != 0 )
	{
		pe = strchr( p, ',' );
		if ( pe != NULL )
		{
			len = min(pe-p, sizeof(DevName[num].FriendlyName)-1);
			strncpy( DevName[num].FriendlyName, p,  len );
			DevName[num].FriendlyName[len] = 0x0;
			DevName[num].hardware_loc[0] = 0;
			num++;
			p = pe+1;
		} else
		{
			len = min(strlen(p), sizeof(DevName[num].FriendlyName));
			strncpy( DevName[num].FriendlyName, p, len );
			DevName[num].FriendlyName[len] = 0x0;
			DevName[num].hardware_loc[0] = 0;
			num++;	
			break;
		}
	}
	
	return num;
}

/*
static char* MakeDevicePathName( char* pDisplayName )
{
	//display name format: @xxx\\?\xxxxxx{cccc-cc-cccc}\xxxx; 
	//we name device name @xxx\\?\xxxxxx, (cut off sub class {ccc-cc-ccc}, and \device name)
	char *p;
	p = strstr(pDisplayName, "\\\\?\\" );
	if ( p != NULL )
	{
		p += 4;
		p = strstr( p, "{" );
		if ( p != NULL )
			*p = 0;
	}
	return pDisplayName;
}
*/


