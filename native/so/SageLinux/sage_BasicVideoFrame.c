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

#include "sage_BasicVideoFrame.h"

/*
 * Class:     sage_BasicVideoFrame
 * Method:    initializeMediaFile0
 * Signature: (ILjava/lang/String;IZLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_BasicVideoFrame_initializeMediaFile0(JNIEnv *env, jobject jo, jint winID, jstring filename,
																 jint sharePtr, jboolean useMyMux, jstring jhost)
{
	return 0;
}
/*
 * Class:     sage_BasicVideoFrame
 * Method:    switchMediaFile0
 * Signature: (Ljava/lang/String;ILjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_BasicVideoFrame_switchMediaFile0(JNIEnv *env, jobject jo,
	jstring jnewName, jboolean waitTillDone, jint sharePtr, jstring jhost)
{
	return 0;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    setRate0
 * Signature: (D)V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_setRate0(JNIEnv *env, jobject jo,
	jdouble rate)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    getRate0
 * Signature: ()D
 */
JNIEXPORT jdouble JNICALL Java_sage_BasicVideoFrame_getRate0(JNIEnv *env, jobject jo)
{
	return 1;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    pause0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_pause0(JNIEnv *env, jobject jo)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    play0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_play0(JNIEnv *env, jobject jo)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    stop0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_stop0(JNIEnv *env, jobject jo)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    timeSelected0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_BasicVideoFrame_timeSelected0(JNIEnv *env, jobject jo, jlong jtime)
{
	return JNI_TRUE;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    getMediaTimeHundNano0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_BasicVideoFrame_getMediaTimeHundNano0(JNIEnv *env, jobject jo)
{
	jlong rv = 0;
	return rv;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    closeMediaFile0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_closeMediaFile0(JNIEnv *env, jobject jo)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    getDurationHundNano0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_BasicVideoFrame_getDurationHundNano0(JNIEnv *env, jobject jo)
{
	jlong rv = 0;
	return rv;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    resizeVideo0
 * Signature: (Ljava/awt/Rectangle;Ljava/awt/Rectangle;Z)V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_resizeVideo0(JNIEnv *env, jobject jo,
															  jobject srcVideoRect, jobject destVideoRect,
															  jboolean hideCursor)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    frameStep0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_frameStep0(JNIEnv *env, jobject jo)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    setMute0
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_setMute0(JNIEnv *env, jobject jo, jboolean mutey)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    inactiveFile0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_inactiveFile0(JNIEnv *env, jobject jo)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    getVolume0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sage_BasicVideoFrame_getVolume0(JNIEnv *env, jobject jo)
{
	return 0;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    setVolume0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_setVolume0(JNIEnv *env, jobject jo, jint level)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    volumeAdjust0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_sage_BasicVideoFrame_volumeAdjust0(JNIEnv *env, jobject jo, jint level)
{
	return 0;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    getLiveMute0
 * Signature: (Z)V
 */
JNIEXPORT jboolean JNICALL Java_sage_BasicVideoFrame_getLiveMute0(JNIEnv *env, jobject jo)
{
	return 0;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    setLiveMute0
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_setLiveMute0(JNIEnv *env, jobject jo, jboolean muteB)
{
}


/*
 * Class:     sage_BasicVideoFrame
 * Method:    getHWND
 * Signature: (Ljava/awt/Canvas;)I
 */
JNIEXPORT jint JNICALL Java_sage_BasicVideoFrame_getHWND(JNIEnv *env, jclass jc, jobject canvas)
{
	return 1;
}


/*
 * Class:     sage_BasicVideoFrame
 * Method:    getAudioInputPaths0
 * Signature: ()[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_BasicVideoFrame_getAudioInputPaths0(JNIEnv *env, jclass jc)
{
	return 0;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    setAudioDelay0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_setAudioDelay0(JNIEnv *env, jobject jo, jlong delay)
{
}


/*
 * Class:     sage_BasicVideoFrame
 * Method:    renderVisualization
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_renderVisualization(JNIEnv *env, jobject jo, jlong mediaTime)
{
}


/*
 * Class:     sage_BasicVideoFrame
 * Method:    playbackControlMessage0
 * Signature: (IJJ)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_BasicVideoFrame_playbackControlMessage0(JNIEnv *env, jobject jo,
																			 jint msgCode, jlong param1, jlong param2)
{
	return JNI_TRUE;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    setAlwaysOnTop
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_setAlwaysOnTop(
	JNIEnv *env, jclass jc, jint winID, jboolean state)
{
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    renderVideoBitmap0
 * Signature: (I[IIILjava/awt/Rectangle;Ljava/awt/Rectangle;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_BasicVideoFrame_renderVideoBitmap0(JNIEnv *env, jobject jo,
																	   jint appHwnd, jintArray jimage,
																	   jint width, jint height,
																	   jobject targetRect,
																	   jobject videoRect)
{
	return 0;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    open350OSD0
 * Signature: ()Z
 */
//static IFilterGraph* pOSDGraph = NULL;
JNIEXPORT jboolean JNICALL Java_sage_BasicVideoFrame_open350OSD0
  (JNIEnv *env, jobject jo)
{
	return JNI_FALSE;
}

/*
 * Class:     sage_BasicVideoFrame
 * Method:    close350OSD0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_BasicVideoFrame_close350OSD0
  (JNIEnv *env, jobject jo)
{
}

