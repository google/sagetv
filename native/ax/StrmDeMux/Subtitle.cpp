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

//#include Subtitle.h


/*
LONGLONG CFileSourceFilter::GetTotalWrite(LPWSTR pwFileName)
{
	static jclass mmcClass;
	static jmethodID mmcInstMeth;
	static jmethodID recBytesMeth;
	static jclass fileClass;
	static jmethodID fileCreator;
	static jobject mmcInst;
	static JavaVM* vmBuf;
	if (!vmBuf)
	{
		HKEY rootKey = HKEY_LOCAL_MACHINE;
		char currVer[16];
		HKEY myKey;
		DWORD readType;
		DWORD hsize = sizeof(currVer);
		if (RegOpenKeyEx(rootKey, "Software\\JavaSoft\\Java Runtime Environment", 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return 0;
		}
		
		if (RegQueryValueEx(myKey, "CurrentVersion", 0, &readType, (LPBYTE)currVer, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return 0;
		}
		RegCloseKey(myKey);
		char pathKey[1024];
		strcpy(pathKey, "Software\\JavaSoft\\Java Runtime Environment\\");
		strcat(pathKey, currVer);
		char jvmPath[1024];
		if (RegOpenKeyEx(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			return 0;
		}
		hsize = sizeof(jvmPath);
		if (RegQueryValueEx(myKey, "RuntimeLib", 0, &readType, (LPBYTE)jvmPath, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			return 0;
		}
		RegCloseKey(myKey);

		// Go to the 2nd to last backslash, and append jawt.dll from there to complete the string
		HMODULE jvmMod = LoadLibrary(jvmPath);

		jsize numVMs;
		typedef jint (JNICALL *JNIGetCreatedJavaVMsPROC)(JavaVM **, jsize, jsize *);
		
		JNIGetCreatedJavaVMsPROC lpfnProc = (JNIGetCreatedJavaVMsPROC)GetProcAddress(jvmMod, "JNI_GetCreatedJavaVMs");
		if (lpfnProc(&vmBuf, 1, &numVMs))
		{
			return 0;
		}
	}
	JNIEnv* env;
	bool detachTheThread = false;
	if (vmBuf->GetEnv((void**)&env, JNI_VERSION_1_2) != JNI_OK)
	{
		vmBuf->AttachCurrentThread((void**)&env, NULL);
		detachTheThread = true;
	}
	if (!mmcClass)
	{
		mmcClass = (jclass) env->NewWeakGlobalRef(env->FindClass("sage/MMC"));
		mmcInstMeth = env->GetStaticMethodID(mmcClass, "getInstance", "()Lsage/MMC;");
		recBytesMeth = env->GetMethodID(mmcClass, "getRecordedBytes", "(Ljava/io/File;)J");
		fileClass = (jclass) env->NewGlobalRef(env->FindClass("java/io/File"));
		fileCreator = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
		mmcInst = env->NewWeakGlobalRef(env->CallStaticObjectMethod(mmcClass, mmcInstMeth));
	}
	jstring jfileString = env->NewString(reinterpret_cast<jchar*>(pwFileName), lstrlenW(pwFileName));
	LONGLONG jrv = env->CallLongMethod(mmcInst, recBytesMeth,
		env->NewObject(fileClass, fileCreator, jfileString));
	if (detachTheThread)
		vmBuf->DetachCurrentThread();
	return jrv;
}

*/