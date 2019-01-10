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
#include <jni.h>
#include "sage_SFIRTuner.h"
#include "jni-util.h"

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

typedef bool (*LPFNSetup)(int);
typedef int (*LPFNINIT)(void);
typedef struct remote* (*LPFNLOAD)(const char*);
typedef unsigned long (*LPFNCarrier_Frequency)(void);
typedef unsigned long (*LPFNBit_Time)(void);
typedef struct remote* (*LPFNCreateRemote)(unsigned char*, unsigned long, unsigned long, command*);
typedef int (*LPFNAddRemote)(remote*, remote**);
typedef int (*LPFNAddCommand)(command*, command**);
typedef struct command* (*LPFNGetCommand)(unsigned char*);
typedef int (*LPFNPlayCommand)(remote*, unsigned char*, int);
typedef int (*LPFNSave)(remote*, const char*);
typedef int (*LPFNTakedown)(void);
typedef bool (*LPFNNeedCarrier)(void);
typedef bool (*LPFNNeedBitrate)(void);
typedef bool (*LPFNCanMacroTune)(void);
typedef void (*LPFNMacroTune)(int);
typedef int (*LPFNDESTROYLIST)(remote**);
typedef int (*LPFNPLAY)(remote*, unsigned char*, int);

jobject createJavaPattern(JNIEnv *env, pattern* cpat)
{
	if (cpat == NULL) return NULL;
	jclass patClass = env->FindClass("sage/SFIRTuner$Pattern");
	jmethodID patConst = env->GetMethodID(patClass, "<init>", "()V");
	jobject rv = env->NewObject(patClass, patConst);
	SetIntField(env, rv, "bit_length", cpat->bit_length);
	SetIntField(env, rv, "length", cpat->length);
	SetCharField(env, rv, "r_flag", cpat->r_flag);
	jbyteArray daBytes = env->NewByteArray(cpat->length);
	env->SetByteArrayRegion(daBytes, 0, cpat->length, (jbyte*)cpat->bytes);
	SetObjectField(env, rv, "bytes", "[B", daBytes);
	if (cpat->next != NULL)
		SetObjectField(env, rv, "next", "Lsage/SFIRTuner$Pattern;", createJavaPattern(env, cpat->next));
	return rv;
}

jobject createJavaCmd(JNIEnv *env, command* ccmd)
{
	if (ccmd == NULL) return NULL;
	jclass cmdClass = env->FindClass("sage/SFIRTuner$Command");
	jmethodID cmdConst = env->GetMethodID(cmdClass, "<init>", "()V");
	jobject rv = env->NewObject(cmdClass, cmdConst);
	SetObjectField(env, rv, "name", "Ljava/lang/String;", env->NewStringUTF((const char*)ccmd->name));
	if (ccmd->pattern != NULL)
		SetObjectField(env, rv, "pattern", "Lsage/SFIRTuner$Pattern;", createJavaPattern(env, ccmd->pattern));
	if (ccmd->next != NULL)
		SetObjectField(env, rv, "next", "Lsage/SFIRTuner$Command;", createJavaCmd(env, ccmd->next));
	return rv;
}

jobject createJavaRemote(JNIEnv *env, remote* cremote)
{
	if (cremote == NULL) return NULL;
	jclass remoteClass = env->FindClass("sage/SFIRTuner$Remote");
	jmethodID remoteConst = env->GetMethodID(remoteClass, "<init>", "()V");
	jobject rv = env->NewObject(remoteClass, remoteConst);
	SetObjectField(env, rv, "name", "Ljava/lang/String;", env->NewStringUTF((const char*)cremote->name));
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
	pattern* rv = new pattern;
	rv->bit_length = GetIntField(env, jpat, "bit_length");
	rv->length = GetIntField(env, jpat, "length");
	rv->r_flag = (char)GetCharField(env, jpat, "r_flag");
	jbyteArray jdata = (jbyteArray)GetObjectField(env, jpat, "bytes", "[B");
	jbyte* jdataArray = env->GetByteArrayElements(jdata, NULL);
	rv->bytes = new unsigned char[env->GetArrayLength(jdata)];
	memcpy((void*)rv->bytes, (void*)jdataArray, env->GetArrayLength(jdata));
	env->ReleaseByteArrayElements(jdata, jdataArray, JNI_ABORT);
	jobject nextPat = GetObjectField(env, jpat, "next", "Lsage/SFIRTuner$Pattern;");
	rv->next = NULL;
	if (nextPat != NULL)
		rv->next = createCPattern(env, nextPat);
	return rv;
}

command* createCCmd(JNIEnv *env, jobject jcmd)
{
	if (jcmd == NULL) return NULL;
	command* rv = new command;
	jstring jname = (jstring) GetObjectField(env, jcmd, "name", "Ljava/lang/String;");
	const char* cname = env->GetStringUTFChars(jname, NULL);
	rv->name = (unsigned char*) new char[env->GetStringLength(jname) + 1];
	strcpy((char*)rv->name, cname);
	env->ReleaseStringUTFChars(jname, cname);
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
	remote* rv = new remote;
	jstring jname = (jstring) GetObjectField(env, jremote, "name", "Ljava/lang/String;");
	const char* cname = env->GetStringUTFChars(jname, NULL);
	rv->name = (unsigned char*) new char[env->GetStringLength(jname) + 1];
	strcpy((char*)rv->name, cname);
	env->ReleaseStringUTFChars(jname, cname);
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
	        	delete [] temp_pat->bytes;
	        	delete temp_pat;
	        	temp_pat = Temp_Com->pattern;
	        }	
	        delete [] Temp_Com->name;                     //free command list
	        delete Temp_Com;
	        Temp_Com = (*head)->command;
	    }
	    (*head) = (*head)->next;
		delete [] Temp_Rem->name;                         //free remote data
	    delete Temp_Rem;    
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
        	delete [] temp_pat->bytes;
        	delete temp_pat;
        	temp_pat = Temp_Com->pattern;
        }	
	    (*head) = (*head)->next;
		delete [] Temp_Com->name;                         //free remote data
	    delete Temp_Com;    
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
	HINSTANCE	deviceDLL;
	jsize len = env->GetArrayLength(jodevNames);
	jstring *validNames = new jstring[len];
	jsize numValid = 0;
	int i;
	for (i = 0; i < len; i++)
	{
		jstring elem = (jstring)env->GetObjectArrayElement(jodevNames, i);
		const char* cfName = env->GetStringUTFChars(elem, NULL);
		// Avoid loading MS libraries that may cause a runtime error
		if (!strstr(cfName, "msvc"))
		{
			// Load DLL
			deviceDLL = LoadLibrary(cfName);
			if (deviceDLL)
			{
				// Interrogate DLL
				deviceNAME = GetProcAddress(deviceDLL, "DeviceName");
				if (deviceNAME) 
				{
					// valid device driver DLL
					// Add DLL filename to list
					validNames[numValid] = elem;
					// Increment counter
					numValid++;
				}
				// Close DLL
				FreeLibrary(deviceDLL);
			}
		}
		env->ReleaseStringUTFChars(elem, cfName);
	}

	jclass stringClass = env->FindClass("java/lang/String");
	jobjectArray objArray = env->NewObjectArray(numValid, stringClass, NULL);
	for (i = 0; i < numValid; i++)
		env->SetObjectArrayElement(objArray, i, validNames[i]);
	delete [] validNames;

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
	HINSTANCE	deviceDLL;
	jsize len = env->GetArrayLength(jodevNames);
	jstring *validNames = new jstring[len];
	jsize numValid = 0;
	int i;
	for (i = 0; i < len; i++)
	{
		jstring elem = (jstring)env->GetObjectArrayElement(jodevNames, i);
		const char* cfName = env->GetStringUTFChars(elem, NULL);
		// Avoid loading MS libraries that may cause a runtime error
		if (!strstr(cfName, "msvc"))
		{
			// Load DLL
			deviceDLL = LoadLibrary(cfName);
			if (deviceDLL)
			{
				// Interrogate DLL
				deviceNAME = GetProcAddress(deviceDLL, "DeviceName");
				if (deviceNAME) 
				{
					// valid device driver DLL
					// Add DLL filename to list
					validNames[numValid] = env->NewStringUTF((const char*)deviceNAME());
					// Increment counter
					numValid++;
				}
				// Close DLL
				FreeLibrary(deviceDLL);
			}
		}
		env->ReleaseStringUTFChars(elem, cfName);
	}

	jclass stringClass = env->FindClass("java/lang/String");
	jobjectArray objArray = env->NewObjectArray(numValid, stringClass, NULL);
	for (i = 0; i < numValid; i++)
		env->SetObjectArrayElement(objArray, i, validNames[i]);
	delete [] validNames;

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
	if (hDriver)
	{
		LPFNTakedown fnTakedown = (LPFNTakedown)GetProcAddress(hDriver, "CloseDevice");
		if (fnTakedown)
			fnTakedown();
	}
}

/*
 * Class:     sage_SFIRTuner
 * Method:    deviceName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_SFIRTuner_deviceName(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	if (hDriver)
	{
		FARPROC deviceNAME = GetProcAddress(hDriver, "DeviceName");
		if (deviceNAME)
		{
			const char* cName = (const char*)deviceNAME();
			return env->NewStringUTF(cName);
		}
	}
	return env->NewStringUTF("");
}

/*
 * Class:     sage_SFIRTuner
 * Method:    findBitRate
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_SFIRTuner_findBitRate(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	if (hDriver)
	{
		LPFNBit_Time fnGetBitrate = (LPFNBit_Time)GetProcAddress(hDriver, "FindBitRate");
		if (fnGetBitrate)
			return fnGetBitrate();
	}
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
	if (hDriver)
	{
		LPFNCarrier_Frequency fnGetCarrier = (LPFNCarrier_Frequency)GetProcAddress(hDriver, "FindCarrierFrequency");
		if (fnGetCarrier)
			return fnGetCarrier();
	}
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
	if (hDriver)
	{
		LPFNINIT lpfnInit = (LPFNINIT)GetProcAddress(hDriver, "InitDevice");
		if (lpfnInit)
			lpfnInit();
	}
}

/*
 * Class:     sage_SFIRTuner
 * Method:    loadRemotes
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_loadRemotes(JNIEnv *env, jobject jo, jstring jfname)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	if (hDriver)
	{
		LPFNLOAD lpfnLoad = (LPFNLOAD)GetProcAddress(hDriver, "LoadRemotes");
		if (lpfnLoad)
		{
			const char* cName = env->GetStringUTFChars(jfname, NULL);
			remote* pRemote = (remote*)lpfnLoad(cName);
			env->ReleaseStringUTFChars(jfname, cName);
			jobject remBase = createJavaRemote(env, pRemote);
			SetObjectField(env, jo, "baseRemote", "Lsage/SFIRTuner$Remote;", remBase);
		}
	}
	//FreeCRemotes(&pRemote);
}

/*
 * Class:     sage_SFIRTuner
 * Method:    needBitrate
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_SFIRTuner_needBitrate(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	if (hDriver)
	{
		LPFNNeedBitrate fnNeedBitrate = (LPFNNeedBitrate)GetProcAddress(hDriver, "NeedBitrate");
		return (fnNeedBitrate != NULL) && fnNeedBitrate();
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_SFIRTuner
 * Method:    needCarrierFrequency
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sage_SFIRTuner_needCarrierFrequency(JNIEnv *env, jobject jo)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	if (hDriver)
	{
		LPFNNeedCarrier fnNeedCarrier = (LPFNNeedCarrier)GetProcAddress(hDriver, "NeedCarrierFrequency");
		return (fnNeedCarrier != NULL) && fnNeedCarrier();
	}
	return JNI_FALSE;
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
	if (hDriver)
	{
		fnSetup = (LPFNSetup)GetProcAddress(hDriver, "OpenDevice");
		if (fnSetup)
			return (jboolean)fnSetup(portNum);
	}
	return JNI_FALSE;
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
	if (hDriver)
	{
		LPFNPLAY lpfnPlay = (LPFNPLAY)GetProcAddress(hDriver, "PlayCommand");
		if (lpfnPlay)
		{
			remote* crem = createCRemote(env, jremote);
			const char* cname = env->GetStringUTFChars(jcmdName, NULL);
			lpfnPlay(crem, (unsigned char*)cname, repeat);
			env->ReleaseStringUTFChars(jcmdName, cname);
			FreeCRemotes(&crem);
		}
	}
}

/*
 * Class:     sage_SFIRTuner
 * Method:    recordCommand
 * Signature: (Ljava/lang/String;)Lsage/SFIRTuner$Command;
 */
JNIEXPORT jobject JNICALL Java_sage_SFIRTuner_recordCommand(JNIEnv *env, jobject jo, jstring jcmdName)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	if (hDriver)
	{
		LPFNGetCommand fnGetCommand = (LPFNGetCommand)GetProcAddress(hDriver, "RecordCommand");
		if (fnGetCommand)
		{
			const char* cname = env->GetStringUTFChars(jcmdName, NULL);
			command *ccmd = (command*)fnGetCommand((unsigned char*)cname);
			env->ReleaseStringUTFChars(jcmdName, cname);
			jobject rv = createJavaCmd(env, ccmd);
			//FreeCCmds(&ccmd);
			return rv;
		}
	}
	return 0;
}

/*
 * Class:     sage_SFIRTuner
 * Method:    saveRemotes
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_saveRemotes(JNIEnv *env, jobject jo, jstring jfName)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	if (hDriver)
	{
		LPFNSave fnSave = (LPFNSave)GetProcAddress(hDriver, "SaveRemotes");
		if (fnSave)
		{
			remote* crem = createCRemote(env, GetObjectField(env, jo, "baseRemote", "Lsage/SFIRTuner$Remote;"));
			const char* cname = env->GetStringUTFChars(jfName, NULL);
			fnSave(crem, cname);
			env->ReleaseStringUTFChars(jfName, cname);
			FreeCRemotes(&crem);
		}
	}
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
	if (jfname)
	{
		const char* cname = env->GetStringUTFChars(jfname, NULL);
		deviceDLL = LoadLibrary(cname);
		SetLongField(env, jo, "nativeDllHandle", (jlong) deviceDLL);
		env->ReleaseStringUTFChars(jfname, cname);
	}
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
		FreeLibrary(deviceDLL);
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
	if (hDriver)
	{
		LPFNCanMacroTune fnMacroTune = (LPFNCanMacroTune)GetProcAddress(hDriver, "CanMacroTune");
		return (fnMacroTune != NULL) && fnMacroTune();
	}
	return JNI_FALSE;
}

/*
 * Class:     sage_SFIRTuner
 * Method:    macroTune
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sage_SFIRTuner_macroTune(JNIEnv *env, jobject jo, jint num)
{
	HINSTANCE hDriver = (HINSTANCE) GetLongField(env, jo, "nativeDllHandle");
	if (hDriver)
	{
		LPFNMacroTune fnMacroTune = (LPFNMacroTune)GetProcAddress(hDriver, "MacroTune");
		if (fnMacroTune)
			fnMacroTune(num);
	}
}

