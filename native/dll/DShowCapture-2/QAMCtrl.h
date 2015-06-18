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

#ifndef __QAM_CTRL_h__
#define __QAM_CTRL_h__

int QAMTunerType( JNIEnv *env, DShowCaptureInfo* pCapInfo );
int ScanChannelQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhysical, long* lMod, long* lInversion, bool* bLocked );
int TuneQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo, long lPhysical, long lMod, long lInversion, bool* bLocked );
void ReleaseQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo );
int SetupQAM( JNIEnv *env, DShowCaptureInfo* pCapInfo );

#endif