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

#include "../include/jni-util.h"
#include <string.h>
#include <stdio.h>

jint 
GetIntField(JNIEnv *env, jobject obj, char *field)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "I");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "GetIntField() failed: %s\r\n", field);
	return -1;
    }
    return env->GetIntField(obj, fid);
}

jint 
GetIntFieldX(JNIEnv *env, jobject obj, char *field)
{
	if (!obj) return 0;
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "I");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "GetIntField() failed: %s\r\n", field);
	return -1;
    }
    return env->GetIntField(obj, fid);
}

jint 
GetStaticIntField(JNIEnv *env, jclass cls, char *field)
{
    jfieldID fid = env->GetStaticFieldID(cls, field, "I");
    if (fid == NULL) {
	sysOutPrint(env, "GetStatocIntField() failed: %s\r\n", field);
	return -1;
    }
    return env->GetStaticIntField(cls, fid);
}

void 
SetIntField(JNIEnv *env, jobject obj, char *field, int val)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "I");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "SetIntField() failed: %s\r\n", field);
	return;
    }
    env->SetIntField(obj, fid, val);
}


jchar
GetCharField(JNIEnv *env, jobject obj, char *field)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "C");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "GetCharField() failed: %s\r\n", field);
	return -1;
    }
    return env->GetCharField(obj, fid);
}

void 
SetCharField(JNIEnv *env, jobject obj, char *field, jchar val)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "C");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "SetCharField() failed: %s\r\n", field);
	return;
    }
    env->SetCharField(obj, fid, val);
}


jlong 
GetLongField(JNIEnv *env, jobject obj, char *field)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "J");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "GetLongField() failed: %s\r\n", field);
	return -1;
    }
    return env->GetLongField(obj, fid);
}


void 
SetLongField(JNIEnv *env, jobject obj, char *field, jlong val)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "J");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "SetLongField() failed: %s\r\n", field);
	return;
    }
    env->SetLongField(obj, fid, val);
}


jboolean
GetBooleanField(JNIEnv *env, jobject obj, char *field)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "Z");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "GetBooleanField() failed: %s\r\n", field);
	return -1;
    }
    return env->GetBooleanField(obj, fid);
}


void 
SetBooleanField(JNIEnv *env, jobject obj, char *field, jboolean val)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "Z");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "SetBooleanField() failed: %s\r\n", field);
	return;
    }
    env->SetBooleanField(obj, fid, val);
}


jfloat
GetFloatField(JNIEnv *env, jobject obj, char *field)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "F");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "GetFloatField() failed: %s\r\n", field);
	return -1;
    }
    return env->GetFloatField(obj, fid);
}


void 
SetFloatField(JNIEnv *env, jobject obj, char *field, jfloat val)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, "F");
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "SetFloatField() failed: %s\r\n", field);
	return;
    }
    env->SetFloatField(obj, fid, val);
}


jobject 
GetObjectField(JNIEnv *env, jobject obj, char *field, char *type)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, type);
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "GetObjectField() failed: %s, %s\r\n", field, type);
	return 0;
    }
    return env->GetObjectField(obj, fid);
}

jobject 
GetObjectFieldX(JNIEnv *env, jobject obj, char *field, char *type)
{
	if (!obj) return NULL;
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, type);
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "GetObjectFieldX() failed: %s, %s\r\n", field, type);
	return 0;
    }
    return env->GetObjectField(obj, fid);
}

void 
SetObjectField(JNIEnv *env, jobject obj, char *field, char *type, jobject val)
{
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, field, type);
    env->DeleteLocalRef(cls);
    if (fid == NULL) {
	sysOutPrint(env, "SetObjectField() failed: %s, %s\r\n", field, type);
	return;
    }
    env->SetObjectField(obj, fid, val);
}


jmethodID
GetMethodID(JNIEnv *env, jobject obj, char *name, char *sig)
{
    jclass cls = env->GetObjectClass(obj);
    jmethodID mid = env->GetMethodID(cls, name, sig);
    env->DeleteLocalRef(cls);
    if (mid == NULL) {
	sysOutPrint(env, "GetMethodID() failed: %s, %s\r\n", name, sig);
	return 0;
    }
    return mid;
}


jobject
CallObjectMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...)
{
    jclass cls = env->GetObjectClass(obj);
    jmethodID mid;
    va_list args;
    jobject result = 0;

    mid = env->GetMethodID(cls, name, sig);
    env->DeleteLocalRef(cls);
    if (mid == 0) {
	sysOutPrint(env, "CallObjectMethod() failed: %s, %s\r\n", name, sig);
	return 0;
    }

    va_start(args, sig);
    result = env->CallObjectMethodV(obj, mid, args); 
    va_end(args);

    return result;    
}


jboolean
CallBooleanMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...)
{
    jclass cls = env->GetObjectClass(obj);
    jmethodID mid;
    va_list args;
    jboolean result = 0;

    mid = env->GetMethodID(cls, name, sig);
    env->DeleteLocalRef(cls);
    if (mid == 0) {
	sysOutPrint(env, "CallBooleanMethod() failed: %s, %s\r\n", name, sig);
	return 0;
    }

    va_start(args, sig);
    result = env->CallBooleanMethodV(obj, mid, args); 
    va_end(args);
        
    if(env->ExceptionOccurred()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    return result;    
}


jint
CallIntMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...)
{
    jclass cls = env->GetObjectClass(obj);
    jmethodID mid;
    va_list args;
    jint result = 0;

    mid = env->GetMethodID(cls, name, sig);
    env->DeleteLocalRef(cls);
    if (mid == 0) {
	sysOutPrint(env, "CallIntMethod() failed: %s, %s\r\n", name, sig);
	return 0;
    }

    va_start(args, sig);
    result = env->CallIntMethodV(obj, mid, args); 
    va_end(args);
        
    if(env->ExceptionOccurred()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    return result;    
}

jint 
CallStaticIntMethod(JNIEnv *env, jclass cls, char *name, char *sig, ...)
{
    jmethodID mid;
    va_list args;
    jint result = 0;

    mid = env->GetStaticMethodID(cls, name, sig);
    if (mid == 0) {
	sysOutPrint(env, "CallStaticIntMethod() failed: %s, %s\r\n", name, sig);
	return 0;
    }

    va_start(args, sig);
    result = env->CallStaticIntMethodV(cls, mid, args); 
    va_end(args);
        
    if(env->ExceptionOccurred()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    return result;
}

jlong
CallStaticLongMethod(JNIEnv *env, jclass cls, char *name, char *sig, ...)
{
    jmethodID mid;
    va_list args;
    jlong result = 0;

    mid = env->GetStaticMethodID(cls, name, sig);
    if (mid == 0) {
	sysOutPrint(env, "CallStaticLongMethod() failed: %s, %s\r\n", name, sig);
	return 0;
    }

    va_start(args, sig);
    result = env->CallStaticLongMethodV(cls, mid, args); 
    va_end(args);
        
    if(env->ExceptionOccurred()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    return result;
}


jlong
CallLongMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...)
{
    jclass cls = env->GetObjectClass(obj);
    jmethodID mid;
    va_list args;
    jlong result = 0;

    mid = env->GetMethodID(cls, name, sig);
    env->DeleteLocalRef(cls);
    if (mid == 0) {
	sysOutPrint(env, "CallLongMethod() failed: %s, %s\r\n", name, sig);
	return 0;
    }

    va_start(args, sig);
    result = env->CallLongMethodV(obj, mid, args); 
    va_end(args);
        
    if(env->ExceptionOccurred()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    return result;    
}


jdouble
CallDoubleMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...)
{
    jclass cls = env->GetObjectClass(obj);
    jmethodID mid;
    va_list args;
    jdouble result = 0;

    mid = env->GetMethodID(cls, name, sig);
    env->DeleteLocalRef(cls);
    if (mid == 0) {
	sysOutPrint(env, "CallDoubleMethod() failed: %s, %s\r\n", name, sig);
	return 0;
    }

    va_start(args, sig);
    result = env->CallDoubleMethodV(obj, mid, args); 
    va_end(args);
        
    if(env->ExceptionOccurred()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    return result;    
}


void 
CallVoidMethod(JNIEnv *env, jobject obj, char *name, char *sig, ...)
{
    jclass cls = env->GetObjectClass(obj);
    jmethodID mid;
    va_list args;
    jint result = 0;

    mid = env->GetMethodID(cls, name, sig);
    env->DeleteLocalRef(cls);
    if (mid == 0) {
	sysOutPrint(env, "CallVoidMethod() failed: %s, %s\r\n", name, sig);
	return;
    }

    va_start(args, sig);
    env->CallVoidMethodV(obj, mid, args); 
    va_end(args);
        
    if(env->ExceptionOccurred()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}


void 
CallStaticVoidMethod(JNIEnv *env, jclass cls, char *name, char *sig, ...)
{
    jmethodID mid;
    va_list args;
    jint result = 0;

    mid = env->GetStaticMethodID(cls, name, sig);
    if (mid == 0) {
	sysOutPrint(env, "CallStaticVoidMethod() failed: %s, %s\r\n", name, sig);
	return;
    }

    va_start(args, sig);
    env->CallStaticVoidMethodV(cls, mid, args); 
    va_end(args);
        
    if(env->ExceptionOccurred()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}


char *
GetObjectClassName(JNIEnv *env, jobject obj)
{
    jclass cls = env->GetObjectClass(obj);
    jmethodID mid = env->GetMethodID(cls, "toString",
				"()Ljava/lang/String;");
    jobject name = env->CallObjectMethod(obj, mid); 
    char *str = (char *)env->GetStringUTFChars((jstring) name, 0);
    env->DeleteLocalRef(cls);
    return str;
}


jboolean
IsInstanceOf(JNIEnv *env, jobject obj, char *type) 
{
    jclass cls = env->FindClass(type);
    if (cls == NULL) {
	sysOutPrint(env, "IsInstanceOf() failed: no such class: %s\r\n", type);
	return 0;
    }
    return env->IsInstanceOf(obj, cls);
}

static void myInvalidParameterHandler(const wchar_t* expression,
   const wchar_t* function, 
   const wchar_t* file, 
   unsigned int line, 
#ifdef WIN32
   uintptr_t pReserved)
#else
   unsigned int *pReserved)
#endif
{
   //wprintf(L"Invalid parameter detected in function %s.File: %s Line: %d\n", function, file, line);
   //wprintf(L"Expression: %s\n", expression);
}

static JavaVM* g_vmBuf = NULL;
void sysOutPrint(const char* cstr, ...)
{
	if (!g_vmBuf)
	{
		jsize numVMs;
		if (JNI_GetCreatedJavaVMs(&g_vmBuf, 1, &numVMs))
			return;
	}
	JNIEnv* env;
	jint threadState = g_vmBuf->GetEnv((void**)&env, JNI_VERSION_1_2);
	if (threadState == JNI_EDETACHED)
		g_vmBuf->AttachCurrentThread((void**)&env, NULL);
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    //vsprintf(buf, cstr, args);

#ifdef WIN32
	_invalid_parameter_handler oldHandler, newHandler;
	newHandler = myInvalidParameterHandler;
	oldHandler = _set_invalid_parameter_handler(newHandler);
	int num = vsprintf_s(buf, sizeof(buf)-1 , cstr, args);
	if ( num > sizeof( buf ) || num < 0 )
	{
		strcpy_s( buf, sizeof( buf ), "ERROR: sysOutPrint buffer overrun, slog crashs here.\r\n" ); 
	}
	_set_invalid_parameter_handler(oldHandler);
#else
	//vsprintf(buf, cstr, args);
	int num = vsnprintf(buf, sizeof(buf), cstr, args);
#endif

    va_end(args);
	jstring jstr = env->NewStringUTF(buf);
	static jclass cls = (jclass) env->NewGlobalRef(env->FindClass("java/lang/System"));
	static jfieldID outField = env->GetStaticFieldID(cls, "out", "Ljava/io/PrintStream;");
	static jmethodID printMeth = env->GetMethodID(env->FindClass("java/io/PrintStream"),
		"print", "(Ljava/lang/String;)V");
	jobject outObj = env->GetStaticObjectField(cls, outField);
	env->CallVoidMethod(outObj, printMeth, jstr);
	env->DeleteLocalRef(jstr);
	if (oldExcept)
		env->Throw(oldExcept);
	if (threadState == JNI_EDETACHED)
		g_vmBuf->DetachCurrentThread();
}

void sysOutPrint(JNIEnv* env, const char* cstr, ...)
{
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    char buf[1024];
#ifdef WIN32
	_invalid_parameter_handler oldHandler, newHandler;
	newHandler = myInvalidParameterHandler;
	oldHandler = _set_invalid_parameter_handler(newHandler);
	int num = vsprintf_s(buf, sizeof(buf)-1 , cstr, args);
	if ( num > sizeof( buf ) || num < 0 )
	{
		strcpy_s( buf, sizeof( buf ), "ERROR: sysOutPrint buffer overrun, slog crashs here.\r\n" ); 
	}
	_set_invalid_parameter_handler(oldHandler);
#else
	//vsprintf(buf, cstr, args);
	int num  = vsnprintf(buf, sizeof(buf), cstr, args);
#endif
    va_end(args);
	jstring jstr = env->NewStringUTF(buf);
	static jclass cls = (jclass) env->NewGlobalRef(env->FindClass("java/lang/System"));
	static jfieldID outField = env->GetStaticFieldID(cls, "out", "Ljava/io/PrintStream;");
	static jmethodID printMeth = env->GetMethodID(env->FindClass("java/io/PrintStream"),
		"print", "(Ljava/lang/String;)V");
	jobject outObj = env->GetStaticObjectField(cls, outField);
	env->CallVoidMethod(outObj, printMeth, jstr);
	env->DeleteLocalRef(jstr);
	if (oldExcept)
		env->Throw(oldExcept);
}

jboolean GetMapIntValue(JNIEnv* env, jobject map, const char* optionName, jint* rv)
{
	jstring keyString = env->NewStringUTF(optionName);
	static jclass mapClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/Map"));
	static jmethodID getMeth = env->GetMethodID(mapClass, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
	jobject val = env->CallObjectMethod(map, getMeth, keyString);
	if (env->ExceptionOccurred())
	{
		env->ExceptionDescribe();
		env->ExceptionClear();
		return JNI_FALSE;
	}
	if (!val)
		return JNI_FALSE;
	static jmethodID intValMeth = env->GetMethodID(env->FindClass("java/lang/Integer"), "intValue", "()I");
	*rv = env->CallIntMethod(val, intValMeth);
	if (env->ExceptionOccurred())
	{
		env->ExceptionDescribe();
		env->ExceptionClear();
		return JNI_FALSE;
	}
	return JNI_TRUE;
}
jboolean GetMapLongValue(JNIEnv* env, jobject map, const char* optionName, jlong* rv)
{
	jstring keyString = env->NewStringUTF(optionName);
	static jclass mapClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/Map"));
	static jmethodID getMeth = env->GetMethodID(mapClass, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
	jobject val = env->CallObjectMethod(map, getMeth, keyString);
	if (env->ExceptionOccurred())
	{
		env->ExceptionDescribe();
		env->ExceptionClear();
		return JNI_FALSE;
	}
	if (!val)
		return JNI_FALSE;
	static jmethodID longValMeth = env->GetMethodID(env->FindClass("java/lang/Long"), "longValue", "()J");
	*rv = env->CallLongMethod(val, longValMeth);
	if (env->ExceptionOccurred())
	{
		env->ExceptionDescribe();
		env->ExceptionClear();
		return JNI_FALSE;
	}
	return JNI_TRUE;
}
jboolean GetMapBoolValue(JNIEnv* env, jobject map, const char* optionName, jboolean* rv)
{
	jstring keyString = env->NewStringUTF(optionName);
	static jclass mapClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/Map"));
	static jmethodID getMeth = env->GetMethodID(mapClass, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
	jobject val = env->CallObjectMethod(map, getMeth, keyString);
	if (env->ExceptionOccurred())
	{
		env->ExceptionDescribe();
		env->ExceptionClear();
		return JNI_FALSE;
	}
	if (!val)
		return JNI_FALSE;
	static jmethodID boolValMeth = env->GetMethodID(env->FindClass("java/lang/Boolean"), "booleanValue", "()Z");
	*rv = env->CallBooleanMethod(val, boolValMeth);
	if (env->ExceptionOccurred())
	{
		env->ExceptionDescribe();
		env->ExceptionClear();
		return JNI_FALSE;
	}
	return JNI_TRUE;
}
jboolean GetMapStringValue(JNIEnv* env, jobject map, const char* optionName, char* rv, unsigned long rvlen)
{
	jstring keyString = env->NewStringUTF(optionName);
	static jclass mapClass = (jclass) env->NewGlobalRef(env->FindClass("java/util/Map"));
	static jmethodID getMeth = env->GetMethodID(mapClass, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
	jobject val = env->CallObjectMethod(map, getMeth, keyString);
	if (env->ExceptionOccurred())
	{
		env->ExceptionDescribe();
		env->ExceptionClear();
		return JNI_FALSE;
	}
	if (!val)
		return JNI_FALSE;
	static jmethodID toStringMeth = env->GetMethodID(env->FindClass("java/lang/Object"),
		"toString", "()Ljava/lang/String;");
	jstring jstr = (jstring) env->CallObjectMethod(val, toStringMeth);
	if (env->ExceptionOccurred())
	{
		env->ExceptionDescribe();
		env->ExceptionClear();
		return JNI_FALSE;
	}
	const char* cstr = env->GetStringUTFChars(jstr, NULL);
	if (strlen(cstr) + 1 > rvlen) return JNI_FALSE;
	if (snprintf(rv, rvlen, "%s", cstr) < 0) return JNI_FALSE ;
	env->ReleaseStringUTFChars(jstr, cstr);
	return JNI_TRUE;
}
