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
#include "sage_SFIRTuner.h"
#include <dlfcn.h>
//#include "jni-util.h"

#include <stdlib.h>
#include <unistd.h>
#include <string.h>

struct pattern
{
	unsigned bit_length;
	unsigned length;
	char r_flag;
	unsigned char *bytes;
	struct pattern *next;
};
typedef struct pattern pattern;	

struct command 
{
	unsigned char *name;
	struct pattern *pattern;
	struct command *next;
};      	
typedef struct command command;

struct remote 
{
	unsigned char *name;
	unsigned long carrier_freq;
	unsigned bit_time;
	struct command *command;
	struct remote *next;
};
typedef struct remote remote;

jobject GetObjectField(JNIEnv *env, jobject obj, char *field, char *type)
{
	jclass cls = (*env)->GetObjectClass(env, obj);
	jfieldID fid = (*env)->GetFieldID(env, cls, field, type);
	(*env)->DeleteLocalRef(env, cls);
	if (fid == NULL)
	{
		printf("GetObjectField() failed%s, %s\n", field, type);
		return 0;
	}
	return (*env)->GetObjectField(env, obj, fid);
}

void SetObjectField(JNIEnv *env, jobject obj, char *field, char *type, jobject val)
{
	jclass cls = (*env)->GetObjectClass(env, obj);
	jfieldID fid = (*env)->GetFieldID(env, cls, field, type);
	(*env)->DeleteLocalRef(env, cls);
	if (fid == NULL)
	{
		printf("SetObjectField() failed: %s, %s\n", field, type);
		return;
    }
    (*env)->SetObjectField(env, obj, fid, val);
}

jint GetIntField(JNIEnv *env, jobject obj, char *field)
{
    jclass cls = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, cls, field, "I");
    (*env)->DeleteLocalRef(env, cls);
    if (fid == NULL) {
	printf("GetIntField() failed: %s\n", field);
	return -1;
    }
    return (*env)->GetIntField(env, obj, fid);
}

void SetIntField(JNIEnv *env, jobject obj, char *field, int val)
{
    jclass cls = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, cls, field, "I");
    (*env)->DeleteLocalRef(env, cls);
    if (fid == NULL) {
	printf("SetIntField() failed: %s\n", field);
	return;
    }
    (*env)->SetIntField(env, obj, fid, val);
}

jlong GetLongField(JNIEnv *env, jobject obj, char *field)
{
    jclass cls = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, cls, field, "J");
    (*env)->DeleteLocalRef(env, cls);
    if (fid == NULL) {
	printf("GetLongField() failed: %s\n", field);
	return -1;
    }
    return (*env)->GetLongField(env, obj, fid);
}


void SetLongField(JNIEnv *env, jobject obj, char *field, jlong val)
{
    jclass cls = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, cls, field, "J");
    (*env)->DeleteLocalRef(env, cls);
    if (fid == NULL) {
	printf("SetLongField() failed: %s\n", field);
	return;
    }
    (*env)->SetLongField(env, obj, fid, val);
}

jchar
GetCharField(JNIEnv *env, jobject obj, char *field)
{
    jclass cls = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, cls, field, "C");
    (*env)->DeleteLocalRef(env, cls);
    if (fid == NULL) {
	printf("GetCharField() failed: %s\n", field);
	return -1;
    }
    return (*env)->GetCharField(env, obj, fid);
}

void 
SetCharField(JNIEnv *env, jobject obj, char *field, jchar val)
{
    jclass cls = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, cls, field, "C");
    (*env)->DeleteLocalRef(env, cls);
    if (fid == NULL) {
	printf("SetCharField() failed: %s\n", field);
	return;
    }
    (*env)->SetCharField(env, obj, fid, val);
}

typedef void* HINSTANCE;

#if defined(__APPLE__)
typedef int (*LPFNSetup)(int);
typedef struct command* (*LPFNGetCommand)(unsigned char*);
typedef unsigned long (*LPFNCarrier_Frequency)(void);
typedef unsigned long (*LPFNBit_Time)(void);
typedef (*LPFNTakedown)(void);
typedef (*LPFNMacroTune)(int);
typedef (*LPFNPLAY)(remote*, unsigned char*, int);
#else
typedef int (*LPFNSetup)(int);
typedef struct command* (*LPFNGetCommand)(int,unsigned char*);
typedef unsigned long (*LPFNCarrier_Frequency)(int);
typedef unsigned long (*LPFNBit_Time)(int);
typedef (*LPFNTakedown)(int);
typedef (*LPFNMacroTune)(int,int);
typedef (*LPFNPLAY)(int,remote*, unsigned char*, int);
#endif
typedef const char* (*FARPROC)();
typedef (*LPFNINIT)(void);
typedef struct remote* (*LPFNLOAD)(const char*);
typedef struct remote* (*LPFNCreateRemote)(unsigned char*, unsigned long, unsigned long, command*);
typedef (*LPFNAddRemote)(remote*, remote**);
typedef (*LPFNAddCommand)(command*, command**);
typedef (*LPFNSave)(remote*, const char*);
typedef int (*LPFNNeedCarrier)(void);
typedef int (*LPFNNeedBitrate)(void);
typedef int (*LPFNCanMacroTune)(void);

jobject createJavaPattern(JNIEnv *env, pattern* cpat)
{
	if (cpat == NULL) return NULL;
	jclass patClass = (*env)->FindClass(env, "sage/SFIRTuner$Pattern");
	jmethodID patConst = (*env)->GetMethodID(env, patClass, "<init>", "()V");
	jobject rv = (*env)->NewObject(env, patClass, patConst);
	SetIntField(env, rv, "bit_length", cpat->bit_length);
	SetIntField(env, rv, "length", cpat->length);
	SetCharField(env, rv, "r_flag", cpat->r_flag);
	jbyteArray daBytes = (*env)->NewByteArray(env, cpat->length);
	(*env)->SetByteArrayRegion(env, daBytes, 0, cpat->length, (jbyte*)cpat->bytes);
	SetObjectField(env, rv, "bytes", "[B", daBytes);
	if (cpat->next != NULL)
		SetObjectField(env, rv, "next", "Lsage/SFIRTuner$Pattern;", createJavaPattern(env, cpat->next));
	return rv;
}

jobject createJavaCmd(JNIEnv *env, command* ccmd)
{
	if (ccmd == NULL) return NULL;
	jclass cmdClass = (*env)->FindClass(env, "sage/SFIRTuner$Command");
	jmethodID cmdConst = (*env)->GetMethodID(env, cmdClass, "<init>", "()V");
	jobject rv = (*env)->NewObject(env, cmdClass, cmdConst);
	SetObjectField(env, rv, "name", "Ljava/lang/String;", (*env)->NewStringUTF(env, (const char*)ccmd->name));
	if (ccmd->pattern != NULL)
		SetObjectField(env, rv, "pattern", "Lsage/SFIRTuner$Pattern;", createJavaPattern(env, ccmd->pattern));
	if (ccmd->next != NULL)
		SetObjectField(env, rv, "next", "Lsage/SFIRTuner$Command;", createJavaCmd(env, ccmd->next));
	return rv;
}

jobject createJavaRemote(JNIEnv *env, remote* cremote)
{
	if (cremote == NULL) return NULL;
	jclass remoteClass = (*env)->FindClass(env, "sage/SFIRTuner$Remote");
	jmethodID remoteConst = (*env)->GetMethodID(env, remoteClass, "<init>", "()V");
	jobject rv = (*env)->NewObject(env, remoteClass, remoteConst);
	SetObjectField(env, rv, "name", "Ljava/lang/String;", (*env)->NewStringUTF(env, (const char*)cremote->name));
	SetLongField(env, rv, "carrier_freq", cremote->carrier_freq);
	SetLongField(env, rv, "bit_time", cremote->bit_time);
	if (cremote->command != NULL)
		SetObjectField(env, rv, "command", "Lsage/SFIRTuner$Command;", createJavaCmd(env, cremote->command));
	if (cremote->next != NULL)
		SetObjectField(env, rv, "next", "Lsage/SFIRTuner$Remote;", createJavaRemote(env, cremote->next));
	return rv;
}

pattern* createCPattern(JNIEnv *env, jobject jpat)
{
	if (jpat == NULL) return NULL;
	pattern* rv = (pattern*) malloc(sizeof(pattern));
	rv->bit_length = GetIntField(env, jpat, "bit_length");
	rv->length = GetIntField(env, jpat, "length");
	rv->r_flag = (char)GetCharField(env, jpat, "r_flag");
	jbyteArray jdata = (jbyteArray)GetObjectField(env, jpat, "bytes", "[B");
	jbyte* jdataArray = (*env)->GetByteArrayElements(env, jdata, NULL);
	rv->bytes = (unsigned char*) malloc((*env)->GetArrayLength(env, jdata));
	memcpy((void*)rv->bytes, (void*)jdataArray, (*env)->GetArrayLength(env, jdata));
	(*env)->ReleaseByteArrayElements(env, jdata, jdataArray, JNI_ABORT);
	jobject nextPat = GetObjectField(env, jpat, "next", "Lsage/SFIRTuner$Pattern;");
	rv->next = NULL;
	if (nextPat != NULL)
		rv->next = createCPattern(env, nextPat);
	return rv;
}

command* createCCmd(JNIEnv *env, jobject jcmd)
{
	if (jcmd == NULL) return NULL;
	command* rv = (command*) malloc(sizeof(command));
	jstring jname = (jstring) GetObjectField(env, jcmd, "name", "Ljava/lang/String;");
	const char* cname = (*env)->GetStringUTFChars(env, jname, NULL);
	rv->name = (unsigned char*) malloc((*env)->GetStringLength(env, jname) + 1);
	strcpy((char*)rv->name, cname);
	(*env)->ReleaseStringUTFChars(env, jname, cname);
	rv->pattern = NULL;
	jobject currPat = GetObjectField(env, jcmd, "pattern", "Lsage/SFIRTuner$Pattern;");
	if (currPat != NULL)
	{
		rv->pattern = createCPattern(env, currPat);
	}
	jobject nextCmd = GetObjectField(env, jcmd, "next", "Lsage/SFIRTuner$Command;");
	rv->next = NULL;
	if (nextCmd != NULL)
		rv->next = createCCmd(env, nextCmd);
	return rv;
}

remote* createCRemote(JNIEnv *env, jobject jremote)
{
	if (jremote == NULL) return NULL;
	remote* rv = (remote*)malloc(sizeof(remote));
	jstring jname = (jstring) GetObjectField(env, jremote, "name", "Ljava/lang/String;");
	const char* cname = (*env)->GetStringUTFChars(env, jname, NULL);
	rv->name = (unsigned char*) malloc((*env)->GetStringLength(env, jname) + 1);
	strcpy((char*)rv->name, cname);
	(*env)->ReleaseStringUTFChars(env, jname, cname);
	rv->carrier_freq = (unsigned long) GetLongField(env, jremote, "carrier_freq");
	rv->bit_time = (unsigned) GetLongField(env, jremote, "bit_time");
	rv->command = NULL;
	jobject currCmd = GetObjectField(env, jremote, "command", "Lsage/SFIRTuner$Command;");
	if (currCmd != NULL)
	{
		rv->command = createCCmd(env, currCmd);
	}
	jobject nextRemote = GetObjectField(env, jremote, "next", "Lsage/SFIRTuner$Remote;");
	rv->next = NULL;
	if (nextRemote != NULL)
		rv->next = createCRemote(env, nextRemote);
	return rv;
}

void FreeCRemotes(remote **head)
{
	command *Temp_Com;		//temporary command pointer
	remote *Temp_Rem;		//temporary remote pointer
	pattern *temp_pat;		//temporary pattern pointer
	
	while(*head)
	{
		Temp_Rem = *head;
		Temp_Com = (*head)->command;
		while(Temp_Com)
		{
			(*head)->command = (*head)->command->next;
	        temp_pat = Temp_Com->pattern;
	        while(temp_pat)
	        {
	        	Temp_Com->pattern = Temp_Com->pattern->next;
	        	free(temp_pat->bytes);
	        	free(temp_pat);
	        	temp_pat = Temp_Com->pattern;
	        }	
	        free(Temp_Com->name);                     //free command list
	        free(Temp_Com);
	        Temp_Com = (*head)->command;
	    }
	    (*head) = (*head)->next;
		free(Temp_Rem->name);                         //free remote data
	    free(Temp_Rem);    
	}
	*head = NULL;
}                                                                         

void FreeCCmds(command **head)
{
	command *Temp_Com;		//temporary command pointer
	pattern *temp_pat;		//temporary pattern pointer
	
	while(*head)
	{
		Temp_Com = *head;
        temp_pat = Temp_Com->pattern;
        while(temp_pat)
        {
        	Temp_Com->pattern = Temp_Com->pattern->next;
        	free(temp_pat->bytes);
	        free(temp_pat);
        	temp_pat = Temp_Com->pattern;
        }	
	    (*head) = (*head)->next;
		free(Temp_Com->name);                         //free remote data
	    free(Temp_Com);    
	}
	*head = NULL;
}                                                                         

/*
 * Class:     sage_SFIRTuner
 * Method:    getValidDeviceFiles
 * Signature: ([Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_SFIRTuner_getValidDeviceFiles(JNIEnv *env, jclass jc,
																	   jobjectArray jodevNames)
{
	FARPROC		deviceNAME;
	void*		deviceDLL;
	jsize len = (*env)->GetArrayLength(env, jodevNames);
	jstring *validNames = (jstring*) malloc(sizeof(jstring*) * len);
	jsize numValid = 0;
	int i;
	for (i = 0; i < len; i++)
	{
		jstring elem = (jstring)(*env)->GetObjectArrayElement(env, jodevNames, i);
		const char* cfName = (*env)->GetStringUTFChars(env, elem, NULL);
		// Load DLL
		deviceDLL = dlopen(cfName, RTLD_NOW);
		if (deviceDLL)
		{
			// Interrogate DLL
			deviceNAME = dlsym(deviceDLL, "DeviceName");
			if (deviceNAME) 
			{
				// valid device driver DLL
				// Add DLL filename to list
				validNames[numValid] = elem;
				// Increment counter
				numValid++;
			}
			// Close DLL
			dlclose(deviceDLL);
		}
		(*env)->ReleaseStringUTFChars(env, elem, cfName);
	}

	jclass stringClass = (*env)->FindClass(env, "java/lang/String");
	jobjectArray objArray = (*env)->NewObjectArray(env, numValid, stringClass, NULL);
	for (i = 0; i < numValid; i++)
		(*env)->SetObjectArrayElement(env, objArray, i, validNames[i]);
	free(validNames);

	return objArray;
}

/*
 * Class:     sage_SFIRTuner
 * Method:    getPrettyDeviceNames
 * Signature: ([Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sage_SFIRTuner_getPrettyDeviceNames(JNIEnv *env, jclass jc,
																	   jobjectArray jodevNames)
{
	FARPROC		deviceNAME;
	void*		deviceDLL;
	jsize len = (*env)->GetArrayLength(env, jodevNames);
	jstring *validNames = (jstring*)malloc(sizeof(jstring*) * len);
	jsize numValid = 0;
	int i = 0;
	for (i = 0; i < len; i++)
	{
		jstring elem = (jstring)(*env)->GetObjectArrayElement(env, jodevNames, i);
		const char* cfName = (*env)->GetStringUTFChars(env, elem, NULL);
		// Load DLL
		deviceDLL = dlopen(cfName, RTLD_NOW);
		if (deviceDLL)
		{
			// Interrogate DLL
			deviceNAME = dlsym(deviceDLL, "DeviceName");
			if (deviceNAME) 
			{
				// valid device driver DLL
				// Add DLL filename to list
				validNames[numValid] = (*env)->NewStringUTF(env, (const char*)deviceNAME());
				// Increment counter
				numValid++;
			}
			// Close DLL
			dlclose(deviceDLL);
		}
		(*env)->ReleaseStringUTFChars(env, elem, cfName);
	}

	jclass stringClass = (*env)->FindClass(env, "java/lang/String");
	jobjectArray objArray = (*env)->NewObjectArray(env, numValid, stringClass, NULL);
	for (i = 0; i < numValid; i++)
		(*env)->SetObjectArrayElement(env, objArray, i, validNames[i]);
	free(validNames);

	return objArray;
}



/*
 * Class:     sage_SFIRTuner
 * Method:    closeDevice
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_closeDevice(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNTakedown fnTakedown = (LPFNTakedown)dlsym(hDriver, "CloseDevice");
	if (fnTakedown)
#if defined(__APPLE__)
		fnTakedown();
#else
		fnTakedown((int)GetLongField(env, jo, "nativePort"));
#endif
}

/*
 * Class:     sage_SFIRTuner
 * Method:    deviceName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_SFIRTuner_deviceName(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	FARPROC deviceNAME = dlsym(hDriver, "DeviceName");
	if (deviceNAME)
	{
		const char* cName = (const char*)deviceNAME();
		return (*env)->NewStringUTF(env, cName);
	}
	else
		return (*env)->NewStringUTF(env, "");
}

/*
 * Class:     sage_SFIRTuner
 * Method:    findBitRate
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_SFIRTuner_findBitRate(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNBit_Time fnGetBitrate = (LPFNBit_Time)dlsym(hDriver, "FindBitRate");
	if (fnGetBitrate)
#if defined(__APPLE__)
		return fnGetBitrate();
#else
		return fnGetBitrate((int)GetLongField(env, jo, "nativePort"));
#endif
	else
		return 0;
}

/*
 * Class:     sage_SFIRTuner
 * Method:    findCarrierFrequency
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_SFIRTuner_findCarrierFrequency(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNCarrier_Frequency fnGetCarrier = (LPFNCarrier_Frequency)dlsym(hDriver, "FindCarrierFrequency");
	if (fnGetCarrier)
#if defined(__APPLE__)
		return fnGetCarrier();
#else
		return fnGetCarrier((int)GetLongField(env, jo, "nativePort"));
#endif
	else
		return 0;
}

/*
 * Class:     sage_SFIRTuner
 * Method:    initDevice
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_initDevice(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNINIT lpfnInit = (LPFNINIT)dlsym(hDriver, "InitDevice");
	if (lpfnInit)
		lpfnInit();
}

/*
 * Class:     sage_SFIRTuner
 * Method:    loadRemotes
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_loadRemotes(JNIEnv *env, jobject jo, jstring jfname)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNLOAD lpfnLoad = (LPFNLOAD)dlsym(hDriver, "LoadRemotes");
	if (!lpfnLoad) return;
	const char* cName = jfname ? (*env)->GetStringUTFChars(env, jfname, NULL) : NULL;
	remote* pRemote = (remote*)lpfnLoad(cName);
	if (jfname)
		(*env)->ReleaseStringUTFChars(env, jfname, cName);
	jobject remBase = createJavaRemote(env, pRemote);
	SetObjectField(env, jo, "baseRemote", "Lsage/SFIRTuner$Remote;", remBase);
	FreeCRemotes(&pRemote);
}

/*
 * Class:     sage_SFIRTuner
 * Method:    needBitrate
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_SFIRTuner_needBitrate(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNNeedBitrate fnNeedBitrate = (LPFNNeedBitrate)dlsym(hDriver, "NeedBitrate");
	return (fnNeedBitrate != NULL) && fnNeedBitrate();
}

/*
 * Class:     sage_SFIRTuner
 * Method:    needCarrierFrequency
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_SFIRTuner_needCarrierFrequency(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNNeedCarrier fnNeedCarrier = (LPFNNeedCarrier)dlsym(hDriver, "NeedCarrierFrequency");
	return (fnNeedCarrier != NULL) && fnNeedCarrier();
}

/*
 * Class:     sage_SFIRTuner
 * Method:    openDevice
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_SFIRTuner_openDevice(JNIEnv *env, jobject jo, jint portNum)
{
	LPFNSetup fnSetup;
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	fnSetup = (LPFNSetup)dlsym(hDriver, "OpenDevice");
	if (!fnSetup) return JNI_FALSE;
#if defined(__APPLE__)
	return fnSetup(portNum) ? JNI_TRUE : JNI_FALSE;
#else
	int natPort = fnSetup(portNum);
	SetLongField(env, jo, "nativePort", natPort);
	return natPort ? JNI_TRUE : JNI_FALSE;
#endif
}

/*
 * Class:     sage_SFIRTuner
 * Method:    playCommand
 * Signature: (Lsage/SFIRTuner$Remote;Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_playCommand(JNIEnv *env, jobject jo, jobject jremote,
													   jstring jcmdName, jint repeat)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNPLAY lpfnPlay = (LPFNPLAY)dlsym(hDriver, "PlayCommand");
	if (!lpfnPlay) return;
	remote* crem = createCRemote(env, jremote);
	const char* cname = (*env)->GetStringUTFChars(env, jcmdName, NULL);
#if defined(__APPLE__)
	lpfnPlay(crem, (unsigned char*)cname, repeat);
#else
	lpfnPlay((int)GetLongField(env, jo, "nativePort"), crem, (unsigned char*)cname, repeat);
#endif
	(*env)->ReleaseStringUTFChars(env, jcmdName, cname);
	FreeCRemotes(&crem);
}

/*
 * Class:     sage_SFIRTuner
 * Method:    recordCommand
 * Signature: (Ljava/lang/String;)Lsage/SFIRTuner$Command;
 */
JNIEXPORT jobject JNICALL Java_sage_SFIRTuner_recordCommand(JNIEnv *env, jobject jo, jstring jcmdName)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNGetCommand fnGetCommand = (LPFNGetCommand)dlsym(hDriver, "RecordCommand");
	if (!fnGetCommand) return NULL;
	const char* cname = (*env)->GetStringUTFChars(env, jcmdName, NULL);
#if defined(__APPLE__)
	command *ccmd = (command*)fnGetCommand((unsigned char*)cname);
#else
	command *ccmd = (command*)fnGetCommand((int)GetLongField(env, jo, "nativePort"), (unsigned char*)cname);
#endif
	(*env)->ReleaseStringUTFChars(env, jcmdName, cname);
	jobject rv = createJavaCmd(env, ccmd);
	//FreeCCmds(&ccmd);
	return rv;
}

/*
 * Class:     sage_SFIRTuner
 * Method:    saveRemotes
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_saveRemotes(JNIEnv *env, jobject jo, jstring jfName)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNSave fnSave = (LPFNSave)dlsym(hDriver, "SaveRemotes");
	if (!fnSave) return;
	remote* crem = createCRemote(env, GetObjectField(env, jo, "baseRemote", "Lsage/SFIRTuner$Remote;"));
	const char* cname = (*env)->GetStringUTFChars(env, jfName, NULL);
	fnSave(crem, cname);
	(*env)->ReleaseStringUTFChars(env, jfName, cname);
	FreeCRemotes(&crem);
}

/*
 * Class:     sage_SFIRTuner
 * Method:    init0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_init0(JNIEnv *env, jobject jo)
{
	HINSTANCE	deviceDLL;
	jstring jfname = (jstring)GetObjectField(env, jo, "devFilename", "Ljava/lang/String;");
	const char* cname = (*env)->GetStringUTFChars(env, jfname, NULL);
	deviceDLL = dlopen(cname, RTLD_NOW);
	SetLongField(env, jo, "nativeDllHandle", (jlong) deviceDLL);
	(*env)->ReleaseStringUTFChars(env, jfname, cname);
}

/*
 * Class:     sage_SFIRTuner
 * Method:    goodbye0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_goodbye0(JNIEnv *env, jobject jo)
{
	HINSTANCE deviceDLL = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	if (deviceDLL)
		dlclose(deviceDLL);
	SetLongField(env, jo, "nativeDllHandle", 0);
}

/*
 * Class:     sage_SFIRTuner
 * Method:    canMacroTune
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_SFIRTuner_canMacroTune(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNCanMacroTune fnMacroTune = (LPFNCanMacroTune)dlsym(hDriver, "CanMacroTune");
	return (fnMacroTune != NULL) && fnMacroTune();
}

/*
 * Class:     sage_SFIRTuner
 * Method:    macroTune
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_macroTune(JNIEnv *env, jobject jo, jint num)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	LPFNMacroTune fnMacroTune = (LPFNMacroTune)dlsym(hDriver, "MacroTune");
	if (fnMacroTune)
#if defined(__APPLE__)
		fnMacroTune(num);
#else
		fnMacroTune((int)GetLongField(env, jo, "nativePort"), num);
#endif
}

