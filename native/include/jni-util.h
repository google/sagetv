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
#ifndef _JNI_UTIL_H_
#define _JNI_UTIL_H_

#ifdef WIN32
#include <windows.h>
#endif
#include <jni.h>
#ifdef WIN32

#ifdef __cplusplus
extern "C" {
#endif

    jint GetIntField(JNIEnv *env, jobject obj, char *field);
    jint GetIntFieldX(JNIEnv *env, jobject obj, char *field);
    jint GetStaticIntField(JNIEnv *env, jclass obj, char *field);
    void SetIntField(JNIEnv *env, jobject obj, char *field, int val);
    jlong GetLongField(JNIEnv *env, jobject obj, char *field);
    void SetLongField(JNIEnv *env, jobject obj, char *field, jlong val);
    jboolean GetBooleanField(JNIEnv *env, jobject obj, char *field);
    void SetBooleanField(JNIEnv *env, jobject obj, char *field, jboolean val);
    jchar GetCharField(JNIEnv *env, jobject obj, char *field);
    void SetCharField(JNIEnv *env, jobject obj, char *field, jchar val);
    jfloat GetFloatField(JNIEnv *env, jobject obj, char *field);
    void SetFloatField(JNIEnv *env, jobject obj, char *field, jfloat val);
    jobject GetObjectField(JNIEnv *env, jobject obj, char *field, char *type);
    jobject GetObjectFieldX(JNIEnv *env, jobject obj, char *field, char *type);
    void SetObjectField(JNIEnv *env, jobject obj, char *field, char *type, jobject val);
    jmethodID GetMethodID(JNIEnv *env, jobject obj, char *name, char *sig);
    jboolean CallBooleanMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...);
    jobject CallObjectMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...);
    jint CallIntMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...);
    jint CallStaticIntMethod(JNIEnv *env, jclass cls, char *name, char *sig, ...);
    jlong CallStaticLongMethod(JNIEnv *env, jclass cls, char *name, char *sig, ...);
    jlong CallLongMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...);
    jdouble CallDoubleMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...);
    void CallVoidMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...);
    void CallStaticVoidMethod(JNIEnv *env, jclass cls, char *name, char *sig, ...);
    char *GetObjectClassName(JNIEnv *env, jobject obj);
    jboolean IsInstanceOf(JNIEnv *env, jobject obj, char *cls);
	jboolean GetMapIntValue(JNIEnv* env, jobject map, const char* optionName, jint* rv);
	jboolean GetMapLongValue(JNIEnv* env, jobject map, const char* optionName, jlong* rv);
	jboolean GetMapBoolValue(JNIEnv* env, jobject map, const char* optionName, jboolean* rv);
	jboolean GetMapStringValue(JNIEnv* env, jobject map, const char* optionName, char* rv, unsigned long rvlen);
#ifdef __cplusplus
}
#endif
#endif

void sysOutPrint(JNIEnv* env, const char* str, ...);
void sysOutPrint(const char* str, ...);


#define SAFE_RELEASE(x) { if (x) x->Release(); x = NULL; }

#define MIN(x, y) ((x < y) ? x : y)
#define MAX(x, y) ((x > y) ? x : y)

// for errors we want no matter what
#define elog(_x_) sysOutPrint _x_

// for debug status messages only
//#ifdef _DEBUG
#define slog(_x_) sysOutPrint _x_
//#else
//#define slog(_x_) 0
//#endif

#define HTESTPRINT(hr) if(FAILED(hr)){sysOutPrint("WIN32 FAILURE HR=0x%x FILE=%s LINE=%d\r\n", hr, __FILE__, __LINE__);}

#endif /* _JNI_UTIL_H_ */
