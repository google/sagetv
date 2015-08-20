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
#include <jni.h>
#include "sage_MMC.h"

/*
 * Class:     sage_MMC
 * Method:    initGraph0
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_initGraph0(JNIEnv *env, jobject jo,
	jint previewWinID)
{
	return JNI_FALSE;
}

/*
 * Class:     sage_MMC
 * Method:    startEncoding0
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_startEncoding0(JNIEnv *env, jobject jo, jstring js)
{
	return JNI_FALSE;
}


/*
 * Class:     sage_MMC
 * Method:    tuneToChannel0
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_MMC_tuneToChannel0(JNIEnv *env, jobject jo, jstring jnum)
{
}

/*
 * Class:     sage_MMC
 * Method:    autoTuneChannel0
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_autoTuneChannel0(JNIEnv *env, jobject jo, jstring jnum)
{
	return 0;
}

/*
 * Class:     sage_MMC
 * Method:    getChannel0
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jstring JNICALL Java_sage_MMC_getChannel0(JNIEnv *env, jobject jo, jint chanType)
{
	return 0;
}


/*
 * Class:     sage_MMC
 * Method:    startPreviewOnly0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_startPreviewOnly0(JNIEnv *env, jobject jo)
{
	return JNI_TRUE;
}

/*
 * Class:     sage_MMC
 * Method:    enablePreview0
 * Signature: (II)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_enablePreview0(
	JNIEnv *env, jobject jo, jint prevW, jint prevH)
{
	return JNI_TRUE;
}

/*
 * Class:     sage_MMC
 * Method:    disablePreview0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_disablePreview0(
	JNIEnv *env, jobject jo)
{
	return JNI_TRUE;
}

/*
 * Class:     sage_MMC
 * Method:    switchOutputFile0
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_switchOutputFile0(JNIEnv *env,
	jobject jo, jstring jnewFilename)
{
	return JNI_FALSE;
}

/*
 * Class:     sage_MMC
 * Method:    stopEncoding0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_stopEncoding0(JNIEnv *env, jobject jo)
{
	return JNI_FALSE;
}

/*
 * Class:     sage_MMC
 * Method:    teardownGraph0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_teardownGraph0(JNIEnv *env, jobject jo)
{
	return JNI_FALSE;
}

/*
 * Class:     sage_MMC
 * Method:    getRecordedBytes
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_MMC_getRecordedBytes(JNIEnv *env, jobject jo)
{
	return 0;
}

/*
 * Class:     sage_MMC
 * Method:    switchToConnector0
 * Signature: (II)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_switchToConnector0(JNIEnv *env, jobject jo, jint crossType, jint crossIdx)
{
	return JNI_TRUE;
}

/*
 * Class:     sage_MMC
 * Method:    findCodecsAndDevices0
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sage_MMC_findCodecsAndDevices0(JNIEnv *env, jobject jo, jboolean readEmAll)
{
}

/*
 * Class:     sage_MMC
 * Method:    showCaptureProperties0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sage_MMC_showCaptureProperties0(JNIEnv *env, jobject jo, jint appHwnd)
{
}

/*
 * Class:     sage_MMC
 * Method:    updateColors0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_MMC_updateColors0(JNIEnv *env, jobject jo)
{
}


/*
 * Class:     sage_MMC
 * Method:    pvIRPortThread
 * Signature: (Lsage/EventRouter;)V
 */
JNIEXPORT void JNICALL Java_sage_MMC_pvIRPortThread(JNIEnv *env, jobject jo, jobject router)
{
}

/*
 * Class:     sage_MMC
 * Method:    setLivePreviewMute0
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sage_MMC_setLivePreviewMute0(JNIEnv *env, jobject jo, jboolean mutey)
{
}

/*
 * Class:     sage_MMC
 * Method:    getLivePreviewMute0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_getLivePreviewMute0(JNIEnv *env, jobject jo)
{
	return JNI_FALSE;
}

/*
 * Class:     sage_MMC
 * Method:    irmanPortInit
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_sage_MMC_irmanPortInit
	(JNIEnv *env, jobject jo, jstring jPortName)
{
	return 0;
}

/*
 * Class:     sage_MMC
 * Method:    closeIRManPort
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sage_MMC_closeIRManPort
	(JNIEnv *env, jobject jo, jint jPortHandle)
{
}

/*
 * Class:     sage_MMC
 * Method:    irmanPortThread
 * Signature: (Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_sage_MMC_irmanPortThread(
	JNIEnv *env, jobject jo, jobject router)
{
}

/*
 * Class:     sage_MMC
 * Method:    checkEncoder0
 * Signature: (Lsage/MMC$Encoder;)I
 */
JNIEXPORT jint JNICALL Java_sage_MMC_checkEncoder0(JNIEnv *env, jobject jo, jobject jenc)
{
	return 0;
}

/*
 * Class:     sage_MMC
 * Method:    openDTVSerial0
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_sage_MMC_openDTVSerial0(JNIEnv *env, jobject jo, jstring jcomstr)
{
	return -1;
}

/*
 * Class:     sage_MMC
 * Method:    closeHandle0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sage_MMC_closeHandle0(JNIEnv *env, jobject jo, jint jhand)
{
}

/*
 * Class:     sage_MMC
 * Method:    dtvSerialChannel0
 * Signature: (II)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_MMC_dtvSerialChannel0(JNIEnv *env, jobject jo, jint comHandle, jint channel)
{
	return JNI_FALSE;
}
