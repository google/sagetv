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
#include <pthread.h>

#include "hdhomerun_includes.h"
#include "HDHRDevice.h"

#include <jni.h>
#include "sage_EncodingException.h"
#include "sage_HDHomeRunCaptureManager.h"
#include "sage_HDHomeRunCaptureDevice.h"

#include <vector>

// we cast pointers to 64 bit Java longs, which produces compiler warnings on 32 bit machines
// this macro gets around these warnings by converting to a 32 bit int first, then casting to a pointer
#if defined(__LP64__) || defined(WIN32)
#define INT64_TO_PTR(t,x) ((t)(x))
#define PTR_TO_INT64(t,x) ((t)(x))
#else
#define INT64_TO_PTR(t,x) ((t)(uint32_t)(x))
#define PTR_TO_INT64(t,x) (((t)(uint32_t)(x)) & 0xFFFFFFFFULL)
#endif

// device count matches the libhdhomerun sample code
	// FIXME: do we really need this? I think the input list could serve this purpose
#define kHDHRDeviceListSize 64
struct hdhomerun_discover_device_t gDeviceList[kHDHRDeviceListSize];
int gDeviceListCount = 0;

// should be two entries per device that's discovered
typedef std::vector<HDHRDevice*> hdhr_list_t;
typedef hdhr_list_t::iterator hdhr_iter_t;

pthread_mutex_t gInputListLock = PTHREAD_MUTEX_INITIALIZER;
hdhr_list_t *gInputList = NULL;
int gInputListCount = 0;

#pragma mark Misc Java Functions
static bool _flog_local_check()
{
	struct stat st;
	if(stat("NATIVE_LOG.ENABLE", &st) == 0) return true;
	return false;
}
static void sysOutPrint(JNIEnv* env, const char* cstr, ...)
{
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    vsnprintf(buf, 1024, cstr, args);
    va_end(args);
	jstring jstr = env->NewStringUTF(buf);
	static jclass cls = 0;
	static jfieldID outField = 0;
	static jmethodID printMeth = 0;
	if (!cls)
	{
		cls = (jclass) env->NewGlobalRef(env->FindClass("java/lang/System"));
		outField = env->GetStaticFieldID(cls, "out", "Ljava/io/PrintStream;");
		printMeth = env->GetMethodID(env->FindClass("java/io/PrintStream"),
										"print", "(Ljava/lang/String;)V");
	}
	jobject outObj = env->GetStaticObjectField(cls, outField);
	env->CallVoidMethod(outObj, printMeth, jstr);
	env->DeleteLocalRef(jstr);
	if (oldExcept)
		env->Throw(oldExcept);
}

static void DebugLog(JNIEnv* env, const char* fmt, ...)
{
	static bool noisyDebug = false;
	static jfieldID dl = 0;
	static jclass sage = 0;
	
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
	
	// check the dl (debug level) field in sage.Sage
	if(!sage) {
		sage = (jclass) env->NewGlobalRef(env->FindClass("sage/Sage"));
		if(!env->ExceptionOccurred()) {
			dl = env->GetStaticFieldID(sage, "dl", "J");
			if(env->ExceptionOccurred()) {
				env->ExceptionDescribe();
				env->ExceptionClear();
			} else if(dl) {
				noisyDebug = (env->GetStaticLongField(sage, dl) != 0);
			}
		} else {
			env->ExceptionClear();
			noisyDebug = true; // no sage.Sage class? assume we're running in a debugging tool...
		}
	}
	
	if(noisyDebug) {
		va_list args;
		va_start(args, fmt);
		char buf[1024];
		vsnprintf(buf, 1024, fmt, args);
		va_end(args);
		sysOutPrint(env, buf);
	}
	
	if (oldExcept)
		env->Throw(oldExcept);
}

static void throwEncodingException(JNIEnv* env, jint errCode)
{
	static jclass encExcClass = 0;
	static jmethodID encConstMeth = 0;
	if (!encExcClass)
	{
		encExcClass = (jclass) env->NewGlobalRef(env->FindClass("sage/EncodingException"));
		encConstMeth = env->GetMethodID(encExcClass, "<init>", "(II)V");
	}
	jthrowable throwie = (jthrowable) env->NewObject(encExcClass, encConstMeth, errCode, (jint) errno);
	env->Throw(throwie);
}

// free() the string when you're done
static inline char *JavaStringToCString(JNIEnv *env, jstring js)
{
	char *os = NULL;
	jsize length;
	
	if(!js) return NULL;
	
	length = env->GetStringUTFLength(js);
	if(length) {
		jboolean copy;
		const char *jc = env->GetStringUTFChars(js, &copy);
		os = (char*)malloc(length+1);
		strcpy(os, jc);
		env->ReleaseStringUTFChars(js, jc);
	}
	
	return os;
}

static inline jstring CStringToJavaString(JNIEnv *env, const char *cs)
{
	return env->NewStringUTF(cs);
}

static inline jstring NewJavaStringWithFormat(JNIEnv *env, const char *fmt, ...)
{
	va_list args;
	char buf[1024];
	
	va_start(args, fmt);
	vsnprintf(buf, 1024, fmt, args);
	va_end(args);
	return env->NewStringUTF(buf);
}

#pragma mark -
#pragma mark Capture Manager Functions

// returns the index into gInputList
static inline HDHRDevice *findDeviceByName(char *name)
{
	hdhr_iter_t iter;
	HDHRDevice *dev = NULL;
	
	pthread_mutex_lock(&gInputListLock);
	for(iter = gInputList->begin(); iter != gInputList->end(); iter++) {
		if((*iter)->isDevice(name)) {
			dev = *iter;
			break;
		}
	}
	pthread_mutex_unlock(&gInputListLock);
	
	return dev;
}

static void DiscoverNewDevices(JNIEnv *env)
{
	int count, ii, jj, kk;
	struct hdhomerun_discover_device_t list[kHDHRDeviceListSize];

	if ( _flog_local_check() )
	{
		enable_dtvchannel_native_log();
		enable_hdhrdevice_native_log();
	}

	DebugLog(env, "HDHomeRun: Detecting devices...\n");
	
	if(!gInputList) {
		pthread_mutex_lock(&gInputListLock);
		if(!gInputList)
			gInputList = new hdhr_list_t();
		pthread_mutex_unlock(&gInputListLock);
	}
	
	count = hdhomerun_discover_find_devices_custom_v2(0, HDHOMERUN_DEVICE_TYPE_TUNER, HDHOMERUN_DEVICE_ID_WILDCARD, list, 64);
	if(count < 0) {
		sysOutPrint(env, "Error discovering new HDHR devices! (%d)\n", count);
		return;
	}
	if(count == 0) {
		sysOutPrint(env, "No HDHR devices discovered\n");
		return;
	}
	
	// now run through the main list and see if there are any new devices
	for(ii=0; ii < count; ii++) {
		int exists = 0;
		
		for(jj = 0; jj < gDeviceListCount; jj++) {
			if(list[ii].device_id == gDeviceList[jj].device_id) {
				exists = 1;
				break;
			}
		}
		
		if(!exists) {
			// First verify the device firmware
			// we won't handle upgrades in this app, they'll need to do that externally
			struct hdhomerun_device_t *hd = hdhomerun_device_create(list[ii].device_id, list[ii].ip_addr, 0, NULL);
			if(hd) {
				// update due to libhdhomerun 20150614 upgrade - changed to get_version
				uint32_t versionint;
				int result = hdhomerun_device_get_version(hd, NULL, &versionint);
				if(result < 0) {
					sysOutPrint(env, "HDHomeRun: Error checking device firmware version (%08lx, %d)\n", list[ii].device_id, result);
					hdhomerun_device_destroy(hd);
					continue;
				}
				if(result == 0) {
					sysOutPrint(env, "HDHomeRun: ERROR: Your device firmware needs to be upgraded!\n");
					hdhomerun_device_destroy(hd);
					continue;
				}
				DebugLog(env, "New HDHomeRun device discovered with firmware version &d\n", versionint);
				hdhomerun_device_destroy(hd);
			} else {
				sysOutPrint(env, "HDHomeRun: Error connecting to device %08lx\n", list[ii].device_id);
				continue;
			}
			
			DebugLog(env, "New HDHomeRun device discovered (id = %08lx, ip = %08lx, tuners = %d)\n", list[ii].device_id, list[ii].ip_addr, list[ii].tuner_count);
			memcpy(&gDeviceList[gDeviceListCount], &list[ii], sizeof(struct hdhomerun_discover_device_t));
			gDeviceListCount++;

			// get actual number of tuners and create entries - was hardcoded to two tuners previously
			pthread_mutex_lock(&gInputListLock);
			for (kk=0; kk<list[ii].tuner_count; kk++) {
				gInputList->push_back(new HDHRDevice(list[ii], kk));
				gInputListCount += 1;
			}
			pthread_mutex_unlock(&gInputListLock);
		}
	}
	
	DebugLog(env, "HDHomeRun: Done detecting.\n");
}

/*
 * Class:     sage_HDHomeRunCaptureManager
 * Method:    getDeviceList0
 * Signature: ()[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_HDHomeRunCaptureManager_getDeviceList0(JNIEnv *env, jobject o)
{
	static jclass stringClass = 0;
	jobjectArray devList = 0;
	
	if(!stringClass) stringClass = (jclass)env->NewGlobalRef(env->FindClass("java/lang/String"));
	
	// discover new devices
	DiscoverNewDevices(env);
	
	// build an array of strings from gInputList name/num values
	pthread_mutex_lock(&gInputListLock);
	if(gInputList && gInputListCount) {
		hdhr_iter_t iter;
		int index = 0;
		
			// one entry per input, the device name
		devList = env->NewObjectArray(gInputListCount, stringClass, env->NewStringUTF(""));
		for(iter = gInputList->begin(); iter != gInputList->end(); iter++) {
			HDHRDevice *dev = *iter;
			jstring tempString;
			
			tempString = CStringToJavaString(env, dev->getName());
			env->SetObjectArrayElement(devList, index++, tempString);
		}
	} else 	devList = env->NewObjectArray(0, stringClass, env->NewStringUTF(""));
	pthread_mutex_unlock(&gInputListLock);
	
	return devList;
}

#pragma mark -
#pragma mark Capture Device Functions

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    isCaptureDeviceValid0
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_sage_HDHomeRunCaptureDevice_isCaptureDeviceValid0(JNIEnv *env, jobject o, jstring name, jint num)
{
	char *cname = JavaStringToCString(env, name);
	HDHRDevice *dev = NULL;
	
	// run through a discovery to pull in new devices, in case it wasn't detected previously
	DiscoverNewDevices(env);
	
	// get the list entry
	if(cname) {
		dev = findDeviceByName(cname);
		free(cname);
	}
	
	// verify the list entry
	return (dev != NULL) ? (jint)dev->validate() : 0;
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    createEncoder0
 * Signature: (Ljava/lang/String;I)J
 * Throws:    EncodingException
 */
JNIEXPORT jlong JNICALL Java_sage_HDHomeRunCaptureDevice_createEncoder0(JNIEnv *env, jobject o, jstring name, jint num)
{
	HDHRDevice *dev = NULL;
	char *cname = JavaStringToCString(env, name);
	
	DiscoverNewDevices(env);
	
	if(!cname) {
		sysOutPrint(env, "HDHomeRun: Invalid name passed to createEncoder0!\n");
		throwEncodingException(env, sage_EncodingException_CAPTURE_DEVICE_INSTALL);
		return 0;
	}
	
	DebugLog(env, "HDHR_createEncoder0(%s, %ld)\n", cname, num);
	
	dev = findDeviceByName(cname);
	free(cname);
	
	if(dev == NULL) {
		sysOutPrint(env, "HDHomeRun: Can't find requested encoder!\n");
		throwEncodingException(env, sage_EncodingException_CAPTURE_DEVICE_INSTALL);
		return 0;
	}
	
		// make sure the device ID is valid
	if(!dev->validate()) {
		sysOutPrint(env, "HDHomeRun: Failed to verify encoder ID!\n");
		throwEncodingException(env, sage_EncodingException_CAPTURE_DEVICE_INSTALL);
		return 0;
	}
	
	if(!dev->openDevice()) {
		sysOutPrint(env, "HDHomeRun: Failed to open encoder!\n");
		throwEncodingException(env, sage_EncodingException_CAPTURE_DEVICE_INSTALL);
		return 0;
	}
	
	return PTR_TO_INT64(jlong, dev);
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    destroyEncoder0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_HDHomeRunCaptureDevice_destroyEncoder0(JNIEnv *env, jobject o, jlong pHandle)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	DebugLog(env, "HDHR_destroyEncoder0(%p)\n", dev);
	if(dev) dev->closeDevice();
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    setupEncoding0
 * Signature: (JLjava/lang/String;J)Z
 * Throws:    EncodingException
 */
JNIEXPORT jboolean JNICALL Java_sage_HDHomeRunCaptureDevice_setupEncoding0(JNIEnv *env, jobject o, jlong pHandle, jstring path, jlong ringSize)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	char *cpath = JavaStringToCString(env, path);
	jboolean result = JNI_FALSE;
	
	DebugLog(env, "HDHR_setupEncoding0(%p, %s, %d)\n", dev, cpath, ringSize);
	
	if(dev) result = (jboolean)dev->setupEncoding(cpath, (size_t)ringSize);
	if(cpath) free(cpath);
	return result;
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    switchEncoding0
 * Signature: (JLjava/lang/String;)Z
 * Throws:    EncodingException
 */
JNIEXPORT jboolean JNICALL Java_sage_HDHomeRunCaptureDevice_switchEncoding0(JNIEnv *env, jobject o, jlong pHandle, jstring path)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	char *cpath = JavaStringToCString(env, path);
	jboolean result = JNI_FALSE;
	
	DebugLog(env, "HDHR_switchEncoding0(%p, %s)\n", dev, cpath);
	
	if(dev) result = (jboolean)dev->switchEncoding(cpath);
	
	if(cpath) free(cpath);
	return result;
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    closeEncoding0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_HDHomeRunCaptureDevice_closeEncoding0(JNIEnv *env, jobject o, jlong pHandle)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	DebugLog(env, "HDHR_closeEncoding0(%p)\n", dev);
	if(dev) dev->closeEncoding();
	return JNI_TRUE;
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    eatEncoderData0
 * Signature: (J)J
 * Throws:    EncodingException
 */
JNIEXPORT jlong JNICALL Java_sage_HDHomeRunCaptureDevice_eatEncoderData0(JNIEnv *env, jobject o, jlong pHandle)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	jlong count = 0;
	
//	DebugLog(env, "HDHR_eatEncoderData(%p)\n", dev);
	if(dev) {
		dev->encoderIdle();
		count = (jlong)dev->eatEncoderData();
	}
	return count;
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    eatEncoderData0
 * Signature: (J)J
 * Throws:    EncodingException
 */
JNIEXPORT jlong JNICALL Java_sage_HDHomeRunCaptureDevice_getOutputData0(JNIEnv *env, jobject o, jlong pHandle)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	jlong count = 0;
	
//	DebugLog(env, "HDHR_eatEncoderData(%p)\n", dev);
	if(dev) {
		dev->encoderIdle();
		count = (jlong)dev->getOutputData();
	}
	return count;
}



/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    setFilterEnable0
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL Java_sage_HDHomeRunCaptureDevice_setFilterEnable0(JNIEnv *env, jobject o, jlong pHandle, jboolean enable)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	
	DebugLog(env, "HDHR_setFilterEnable0(%s)\n", enable ? "TRUE" : "FALSE");
	if(dev)
		dev->encoderIdle();
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    setChannel0
 * Signature: (JLjava/lang/String;Z)Z
 * Throws:    EncodingException
 */
JNIEXPORT jboolean JNICALL Java_sage_HDHomeRunCaptureDevice_setChannel0(JNIEnv *env, jobject o, jlong pHandle, jstring channelName, jboolean autotune, jint streamFormat)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	char *cname = JavaStringToCString(env, channelName);
	jboolean result = JNI_FALSE;
	
	if(cname) {
		DebugLog(env, "HDHR_setChannel0(%p, %s, %s, %d)\n", dev, cname, autotune ? "true" : "false", (int)streamFormat);
		if(dev) result = dev->setChannel(cname, (bool)autotune, streamFormat);
		free(cname);
	}
	
	return result;
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    scanChannel0
 * Signature: (JLjava/lang/String;Ljava/lang/String;I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_HDHomeRunCaptureDevice_scanChannel0(JNIEnv *env, jobject o, jlong pHandle, jstring channelName, jstring region, jint format)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	jstring outString = 0;
	char *cname = JavaStringToCString(env, channelName);
	char *rs = JavaStringToCString(env, region);
	
	if(cname && rs) {
		char *result = NULL;
		
		DebugLog(env, "HDHR_scanChannel0(%p, %s, %s, %ld)\n", dev, cname, rs, format);
		if(dev) result = dev->scanChannel(cname, rs, (int)format);
		
		if(result) {
			outString = CStringToJavaString(env, result);
			free(result);
		}
	}
	
	if(cname) free(cname);
	if(rs) free(rs);
	return outString;
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    setInput0
 * Signature: (JIILjava/lang/String;II)Z
 * Throws:    EncodingException
 */
JNIEXPORT jboolean JNICALL Java_sage_HDHomeRunCaptureDevice_setInput0(JNIEnv *env, jobject o, jlong pHandle, jint type, jint index, jstring sigFormat, jint countryCode, jint videoFormat)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	char *cname = JavaStringToCString(env, sigFormat);
	jboolean result = JNI_FALSE;
	
	if(cname) {
		DebugLog(env, "HDHR_setInput0(%p, %ld, %ld, %s, %ld, %ld)\n", dev, type, index, cname, countryCode, videoFormat);
		// type and index are unused as of yet...
		if(dev) result = dev->setInput(cname, (int)countryCode, (int)videoFormat);
		free(cname);
	}
	
	return result;
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    getBroadcastStandard0
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_HDHomeRunCaptureDevice_getBroadcastStandard0(JNIEnv *env, jobject o, jlong pHandle)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	char *result = NULL;
	
	DebugLog(env, "HDHR_getBroadcastStandard0(%p)\n", dev);
	if(dev) result = dev->getBroadcastStandard();
	
	if(result) return CStringToJavaString(env, result);
	return 0;
}

/*
 * Class:     sage_HDHomeRunCaptureDevice
 * Method:    getSignalStrength0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_HDHomeRunCaptureDevice_getSignalStrength0(JNIEnv *env, jobject o, jlong pHandle)
{
	HDHRDevice *dev = INT64_TO_PTR(HDHRDevice*, pHandle);
	jint result = 0;
	
	DebugLog(env, "HDHR_getSignalStrength0(%p)\n", dev);
	if(dev) result = (jint)dev->getSignalStrength();
	
	return result;
}
