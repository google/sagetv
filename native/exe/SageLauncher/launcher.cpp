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
// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)

#include <stdlib.h>
#include <jni.h>
#include "../../include/jni-util.h"
#include <direct.h>
#include <windows.h>

#define MYOUT(x) WriteConsole(stdOutHandle, x, strlen(x), &numWrit, NULL)
LRESULT CALLBACK WndProc( HWND hWnd, UINT messg,
								WPARAM wParam, LPARAM lParam );

#define JVM_MISSING if (hWnd){MessageBox(NULL, "Could not get information on current JVM.\nPlease install Java Runtime Environment 1.7 or greater","Java Missing", MB_OK);}\
else{OutputDebugStringA("Could not get information on current JVM.\nPlease install Java Runtime Environment 1.7 or greater");}

static JNIEnv* globalenv = 0;
static HANDLE stdOutHandle = 0;
static HANDLE m_hMutex = NULL;

static HINSTANCE libInst = NULL;
static PROCESS_INFORMATION pi;

JavaVM *vm = NULL;

int launchJVMSage(LPSTR lpszCmdLine, HWND hWnd, BOOL bClient, BOOL bService);

SERVICE_STATUS          SageServiceStatus; 
SERVICE_STATUS_HANDLE   SageServiceStatusHandle; 
 
VOID WINAPI SageServiceStart (DWORD argc, LPTSTR *argv); 
DWORD WINAPI SageServiceCtrlHandlerEx (DWORD dwControl,     // requested control code
  DWORD dwEventType,   // event type
  LPVOID lpEventData,  // event data
  LPVOID lpContext     // user-defined context data
);
DWORD WINAPI SageServiceInitialization (DWORD argc, LPTSTR *argv, 
        DWORD *specificError); 

void sysOutPrint(const char* cstr, ...)
{
	JNIEnv* env;
	if (!vm) return;
	jint threadState = vm->GetEnv((void**)&env, JNI_VERSION_1_2);
	if (threadState == JNI_EDETACHED)
		vm->AttachCurrentThread((void**)&env, NULL);
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    vsprintf(buf, cstr, args);
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
		vm->DetachCurrentThread();
}

void sysOutPrint(JNIEnv* env, const char* cstr, ...)
{
	jthrowable oldExcept = env->ExceptionOccurred();
	if (oldExcept)
		env->ExceptionClear();
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    vsprintf(buf, cstr, args);
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


VOID SvcDebugOut(LPSTR String, DWORD Status) 
{ 
    CHAR  Buffer[1024]; 
    if (strlen(String) < 1000) 
    { 
        sprintf(Buffer, String, Status); 
        OutputDebugStringA(Buffer); 
    } 
}

void errorMsg(char* msg, char* title, HWND hWnd)
{
	if (hWnd)
	{
		MessageBox(NULL, msg, title, MB_OK);
	}
	else
	{
		OutputDebugStringA(msg);
	}
}


static TCHAR toHex(int nibble)
{
	static TCHAR hexDigits[] = "0123456789ABCDEF";
	return hexDigits[nibble & 0xF];
}

// Writes the wide character string to the specified file using the Java properties escaping rules
void writeEscapedPropString(FILE* fp, const LPWSTR wstr)
{
	static const TCHAR specialSaveChars[] = "=: \t\r\n\f#!";
    int len = wcslen(wstr);
	int x;
    for(x=0; x<len; x++) 
	{
		if (wstr[x] == ' ')
			fprintf(fp, "\\ ");
		else if (wstr[x] == '\\')
			fprintf(fp, "\\\\");
		else if (wstr[x] == '\t')
			fprintf(fp, "\\t");
		else if (wstr[x] == '\n')
			fprintf(fp, "\\n");
		else if (wstr[x] == '\r')
			fprintf(fp, "\\r");
		else if (wstr[x] == '\f')
			fprintf(fp, "\\f");
		else if (wstr[x] < 0x20 || wstr[x] > 0x7E)
			fprintf(fp, "\\u%c%c%c%c", toHex((wstr[x] >> 12) & 0xF),
				toHex((wstr[x] >> 8) & 0xF), toHex((wstr[x] >> 4) & 0xF),
				toHex(wstr[x] & 0xF));
		else
		{
			if (strchr(specialSaveChars, (char) wstr[x]))
				fprintf(fp, "\\");
			fprintf(fp, "%c", (char) wstr[x]);
		}
	}
}

void popupExceptionError(JNIEnv* env, jthrowable thrower, char* errTitle, HWND hWnd)
{
    env->ExceptionClear();
	jmethodID toStr = env->GetMethodID(env->GetObjectClass(thrower), "toString", "()Ljava/lang/String;");
	jstring throwStr = (jstring)env->CallObjectMethod(thrower, toStr);
	const char* cThrowStr = env->GetStringUTFChars(throwStr, 0);
	char *errStr = (char*) malloc(env->GetStringLength(throwStr) + 64);
	sprintf(errStr, "An exception occured in Java:\n%s", cThrowStr);
	errorMsg(errStr, errTitle, hWnd);
	env->ReleaseStringUTFChars(throwStr, cThrowStr);
	free(errStr);
}

jint JNICALL my_vfprintf(FILE *fp, const char *format, va_list args)
{
	if (stdOutHandle)
	{
	    char buf[1024];
		DWORD numWrit;
		DWORD len = _vsnprintf(buf, sizeof(buf), format, args);
		WriteConsole(stdOutHandle, buf, len, &numWrit, NULL);
		return len;
	}
	return 0;
}

bool shouldRestartJVM(BOOL bService, BOOL bClient)
{
	if (GetFileAttributes(bService ? "restartsvc" : (bClient ? "restartclient" : "restart")) != INVALID_FILE_ATTRIBUTES)
	{
		DeleteFile(bService ? "restartsvc" : (bClient ? "restartclient" : "restart"));
		return true;
	}
	return false;
}

BOOL CheckForMutex(HANDLE *pMutex, LPCSTR szMutexName, LPSTR appName)
{
	SECURITY_DESCRIPTOR  sd;
	SECURITY_ATTRIBUTES sa;
    if (InitializeSecurityDescriptor(&sd, SECURITY_DESCRIPTOR_REVISION))
	{
		if (SetSecurityDescriptorDacl(&sd, TRUE, (PACL) NULL, FALSE))
		{
			ZeroMemory(&sa, sizeof(SECURITY_ATTRIBUTES));
			sa.nLength = sizeof(SECURITY_ATTRIBUTES);
			sa.lpSecurityDescriptor = &sd;
			sa.bInheritHandle = FALSE;
			*pMutex = CreateMutex(&sa, FALSE, szMutexName);
		}
		else
			*pMutex = CreateMutex(NULL, FALSE, szMutexName); 
	}
	else
		*pMutex = CreateMutex(NULL, FALSE, szMutexName); 
	DWORD lastErr = GetLastError();
	BOOL alreadyThere = FALSE;
	if (ERROR_ACCESS_DENIED == lastErr)
	{
		// Try again with OpenMutex
		*pMutex = OpenMutex(MUTEX_MODIFY_STATE, FALSE, szMutexName);
		if (*pMutex)
		{
			alreadyThere = TRUE;
		}
	}

	if (alreadyThere || ERROR_ALREADY_EXISTS == lastErr)
	{
		CloseHandle(*pMutex); //do as late as possible
		*pMutex = 0;
		if (appName)
		{
			HWND thewin = FindWindow(appName, "SageWin");
			if (thewin)
				SendMessage(thewin, WM_USER + 26, WM_LBUTTONDBLCLK, WM_LBUTTONDBLCLK);
		}
		return TRUE;
	}
	return FALSE;
}

void removeTrailingWS(char* line)
{
	int len = strlen(line);
	while (len > 0 && (line[len - 1] == ' ' || line[len - 1] == '\r' || line[len-1] == '\n'))
	{
		line[len - 1] = '\0';
		len--;
	}
}

void doStageCleaning(BOOL bService, BOOL bClient)
{
	// This performs the pre-install routines for when we need to upgrade components in use by the JVM
	// We need to lock a global mutex here and wait for it if someone else has created it; otherwise
	// we could end up deleting files during the rename process and other bad stuff....
	HANDLE pStageMutex = 0;
	if (CheckForMutex(&pStageMutex, "Global\\SageTVStageMutex", NULL))
	{
		// The Mutex already exists, which means another process is handling the staging (like the client or service or app)
		// Wait until the Mutex is gone, and then close it and continue on
		do
		{
			Sleep(50);
		}while (CheckForMutex(&pStageMutex, "Global\\SageTVStageMutex", NULL));
		CloseHandle(pStageMutex);
		return;
	}

	BOOL stagingFailed = FALSE;

	// Before we proceed with staging, now that we now we are in control w/ the mutex;
	// we need to be sure there's no other JVMs running that may be in the process of shutting down
	// for a restart to occur.
	if (GetFileAttributes("restart") != INVALID_FILE_ATTRIBUTES || 
		GetFileAttributes("restartsvc") != INVALID_FILE_ATTRIBUTES || 
		GetFileAttributes("restartclient") != INVALID_FILE_ATTRIBUTES)
	{

		// Something else is waiting on a restart establish a timer for how long we'll wait for it to complete
		// before we just go ahead....but we will skip staging in this case
		FILETIME ft;
		SYSTEMTIME st;
		GetSystemTime(&st);              // gets current time
		SystemTimeToFileTime(&st, &ft);  // converts to file time format
		ULONGLONG longTimea;
		memcpy(&longTimea, &ft, sizeof(longTimea));
		while (GetFileAttributes("restart") != INVALID_FILE_ATTRIBUTES || 
			GetFileAttributes("restartsvc") != INVALID_FILE_ATTRIBUTES || 
			GetFileAttributes("restartclient") != INVALID_FILE_ATTRIBUTES)
		{
			// Check if we even should be waiting...we'll know this by the existence of the corresponding mutex for the application
			BOOL validWaits = FALSE;
			if ((bClient || bService) && GetFileAttributes("restart") != INVALID_FILE_ATTRIBUTES)
			{
				HANDLE hMutex = 0;
				if (!CheckForMutex(&hMutex, "Global\\SageTVSingleton", NULL))
				{
					CloseHandle(hMutex);
				}
				else
				{
					validWaits = TRUE;
				}
			}
			if (!bService && GetFileAttributes("restartsvc") != INVALID_FILE_ATTRIBUTES)
			{
				HANDLE hMutex = 0;
				if (!CheckForMutex(&hMutex, "Global\\SageTVServiceSingleton", NULL))
				{
					CloseHandle(hMutex);
				}
				else
				{
					validWaits = TRUE;
				}
			}
			if (!bClient && GetFileAttributes("restartclient") != INVALID_FILE_ATTRIBUTES)
			{
				HANDLE hMutex = 0;
				if (!CheckForMutex(&hMutex, "Global\\SageTVClientSingleton", NULL))
				{
					CloseHandle(hMutex);
					if (!CheckForMutex(&hMutex, "Global\\SageTVPseudoSingleton", NULL))
					{
						CloseHandle(hMutex);
					}
					else
					{
						validWaits = TRUE;
					}
				}
				else
				{
					validWaits = TRUE;
				}
			}
			if (!validWaits)
				break;
			Sleep(250);
			// Check if we've waited too long
			GetSystemTime(&st);              // gets current time
			SystemTimeToFileTime(&st, &ft);  // converts to file time format
			ULONGLONG currlongTimea;
			memcpy(&currlongTimea, &ft, sizeof(currlongTimea));
			// 60 second maximum wait time
			if (currlongTimea - longTimea > 600000000L)
			{
				stagingFailed = TRUE;
				break;
			}
		}
	}

	// Handle the deletions files first
	FILE* fp = fopen("stageddeletes.txt", "r");
	if (fp != NULL)
	{
		char line[2048];
		while (!stagingFailed && fgets(line, 2048, fp) != NULL)
		{
			removeTrailingWS(line);
			// Only worry about deletion failure if the file exists
			FILE* delFp = fopen(line, "r");
			if (delFp)
			{
				fclose(delFp);
				if (!DeleteFile(line))
				{
					// The deletion of this file failed...do NOT continue with the rest of the staging process
					stagingFailed = TRUE;
				}
			}
		}
		fclose(fp);
		fp = NULL;
		if (!stagingFailed)
			DeleteFile("stageddeletes.txt");
	}

	// Handle the renames now
	if (!stagingFailed)
	{
		fp = fopen("stagedrenames.txt", "r");
		if (fp != NULL)
		{
			char line1[2048];
			char line2[2048];
			while (!stagingFailed && fgets(line1, 2048, fp) != NULL)
			{
				removeTrailingWS(line1);
				if (fgets(line2, 2048, fp) == NULL)
					break;
				removeTrailingWS(line2);
				// Make sure the file we're renaming from exists in case the staging file was partially processed
				// We do NOT fail if the source file does not exist because there's no way to ever recover from that
				// and it can happen if we partially process the staging file
				if (GetFileAttributes(line1) != INVALID_FILE_ATTRIBUTES)
				{
					if (GetFileAttributes(line2) != INVALID_FILE_ATTRIBUTES)
					{
						if (!DeleteFile(line2))
						{
							stagingFailed = TRUE;
						}
					}
					if (!stagingFailed)
					{
						if (!MoveFile(line1, line2))
						{
							stagingFailed = TRUE;
						}
					}
				}
			}
			fclose(fp);
			fp = NULL;
			if (!stagingFailed)
				DeleteFile("stagedrenames.txt");
		}
	}
	CloseHandle(pStageMutex);
}

#define VER_SUITE_WH_SERVER 0x00008000

int WINAPI WinMain( HINSTANCE hInst, 	/*Win32 entry-point routine */
					HINSTANCE hPreInst, 
					LPSTR lpszCmdLine, 
					int nCmdShow )
{
	HWND hWnd;
	WNDCLASS wc;
	BOOL bClient = FALSE;
	BOOL bForcedClient = FALSE;
	char appName[64];
	char mutexName[64];
	char myCmdLine[1024];
	// If we are wrapped then we should actually invoke the JVM; otherwise we need to invoke this process
	// again which will then invoke the JVM itself to allow us to be able to restart the JVM
	BOOL bWrapped = FALSE;
			FILETIME ft;
			SYSTEMTIME st;
			GetSystemTime(&st);              // gets current time
			SystemTimeToFileTime(&st, &ft);  // converts to file time format
			ULONGLONG longTime;
			memcpy(&longTime, &ft, sizeof(longTime));
	if (strstr(lpszCmdLine, "-wrapped"))
	{
		bWrapped = TRUE;
	}

#if defined(SAGE_TV_CLIENT)
	bClient = TRUE;
	sprintf(appName, "SageClientApp");
	if (strstr(lpszCmdLine, "-multi"))
		mutexName[0] = '\0';
	else
		sprintf(mutexName, "Global\\SageTVClientSingleton");
#else

	if (strstr(lpszCmdLine, "-client"))
	{
		bClient = TRUE;
		bForcedClient = TRUE;
		sprintf(appName, "SageClientApp");
		sprintf(mutexName, "Global\\SageTVClientSingleton");
	}
	else
	{
		sprintf(appName, "SageApp");
		if (bWrapped && strstr(lpszCmdLine, "-connect"))
			bClient = TRUE;
		sprintf(mutexName, "Global\\SageTVSingleton");
	}
#endif // defined(SAGE_TV_CLIENT)

#ifdef SAGE_TV_SERVICE
	if (strcmp(lpszCmdLine, "-install") == 0)
	{
		// Install the windows service
		// Open a handle to the SC Manager database. 
 
		SC_HANDLE schSCManager = OpenSCManager( 
			NULL,                    // local machine 
			NULL,                    // ServicesActive database 
			SC_MANAGER_CREATE_SERVICE);  // full access rights 
 
		if (schSCManager == NULL) 
		{
			MessageBox(NULL, "Cannot OpenSCManager. You need to be logged in as Administrator to perform this task", "ERROR", MB_OK); 
			return FALSE;
		}
 
		char appPath[2048];
		GetModuleFileName(NULL, appPath, 2048);
		SC_HANDLE schService = CreateService( 
			schSCManager,              // SCManager database 
			"SageTV",              // name of service 
			"SageTV",           // service name to display 
			SERVICE_START,        // desired access 
			SERVICE_WIN32_OWN_PROCESS, // service type 
			SERVICE_AUTO_START,      // start type 
			SERVICE_ERROR_NORMAL,      // error control type 
			appPath,        // service's binary 
			NULL,                      // no load ordering group 
			NULL,                      // no tag identifier 
			NULL,                      // no dependencies 
			// If we logon as a network service then we only have read access to the C drive which is bad
			NULL,                      // LocalSystem account 
			NULL);                     // no password 
 
		if (schService == NULL)
		{
			CloseServiceHandle(schSCManager);
			MessageBox(NULL, "Cannot CreateService.", "ERROR", MB_OK); 
			return FALSE;
		}
		else
		{
			if (!StartService(schService, 0, NULL))
			{
				MessageBox(NULL, "Successfully installed SageTV Service, but unable to start.", "Service Install Succeeded", MB_OK);
			}
			else
				MessageBox(NULL, "Successfully installed & started SageTV Service", "Service Install Succeeded", MB_OK);
		}
		CloseServiceHandle(schService);
		CloseServiceHandle(schSCManager);
		return TRUE;
	}
	else if (strcmp(lpszCmdLine, "-remove") == 0)
	{
		// Open a handle to the SC Manager database. 
 
		SC_HANDLE schSCManager = OpenSCManager( 
			NULL,                    // local machine 
			NULL,                    // ServicesActive database 
			DELETE | SERVICE_STOP | SERVICE_QUERY_STATUS);  // delete access rights 
 
		if (schSCManager == NULL) 
		{
			MessageBox(NULL, "Cannot OpenSCManager. You need to be logged in as Administrator to perform this task", "ERROR", MB_OK); 
			return FALSE;
		}
		// Remove the windows service
		SC_HANDLE schService = OpenService( 
			schSCManager,       // SCManager database 
			"SageTV",       // name of service 
			DELETE | SERVICE_STOP | SERVICE_QUERY_STATUS);            // only need DELETE access 
 
		if (schService == NULL)
		{
			CloseServiceHandle(schSCManager);
			MessageBox(NULL, "Cannot OpenService.", "ERROR", MB_OK); 
			return FALSE;
		}
		// Stop the service if its running & wait for it to stop
		SERVICE_STATUS servStat;
		if (ControlService(schService, SERVICE_CONTROL_STOP, &servStat))
		{
			long maxWaits = 250;
			SERVICE_STATUS srvStat;
			while (QueryServiceStatus(schService, &srvStat) && 
				srvStat.dwCurrentState == SERVICE_STOP_PENDING &&
				(maxWaits-- > 0))
			{
				Sleep(250);
			}
		}
		if (!DeleteService(schService))
		{
			MessageBox(NULL, "Cannot DeleteService.", "ERROR", MB_OK); 
			return FALSE;
		}
		else 
		{
			MessageBox(NULL, "SageTV Service Successfully Removed", "Service Removal", MB_OK);
		}
 
		CloseServiceHandle(schService); 
		CloseServiceHandle(schSCManager);
		return TRUE;
	}

#endif
	if (strcmp(lpszCmdLine, "-awake") == 0)
	{
		HWND thewin = FindWindow(appName, "SageWin");
		if (thewin)
			SendMessage(thewin, WM_USER + 26, WM_LBUTTONDBLCLK, WM_LBUTTONDBLCLK);
		return TRUE;
	}
	else if (strcmp(lpszCmdLine, "-dvd") == 0)
	{
		// DVD Autoplay Notifcation
		HWND thewin = FindWindow(appName, "SageWin");
		if (thewin)
			SendMessage(thewin, WM_USER + 234, 74, 74); // DVD is 74
		return TRUE;
	}
	else if (strstr(lpszCmdLine, "-event ") == lpszCmdLine && strlen(lpszCmdLine) > 7)
	{
		int evtNum = atoi(lpszCmdLine + 7);
		HWND thewin = FindWindow(appName, "SageWin");
		if (thewin)
			SendMessage(thewin, WM_USER + 234, evtNum, evtNum); // DVD is 74
		return TRUE;
	}
	// Fix the current working directory to be where the EXE is located
	LPTSTR folderPath = new TCHAR[2048];
	GetModuleFileName(NULL, folderPath, 2048);
	int appLen = strlen(folderPath);
	for (int i = appLen - 1; i > 0; i--)
	{
		if (folderPath[i] == '\\')
		{
			folderPath[i + 1] = 0;
			break;
		}
	}
	chdir(folderPath);
	delete [] folderPath;

#ifdef SAGE_TV_SERVICE
	// NOTE: We still need to do the SageTV mutex even for the service because
	// we don't want the service to load if the app has started!
	if (!bWrapped)
	{
		if (strlen(mutexName))
		{
			HANDLE testMutex = NULL;
			if (CheckForMutex(&testMutex, mutexName, appName))
				return TRUE;
			else
				CloseHandle(testMutex);
		}

		SERVICE_TABLE_ENTRY   DispatchTable[] = 
		{ 
			{ "SageTV", SageServiceStart      }, 
			{ NULL,              NULL          } 
		}; 
		if (StartServiceCtrlDispatcher( DispatchTable)) 
		{ 
			// We are running as a service
			//SvcDebugOut(" [SageTV] StartServiceCtrlDispatcher error = %d\n", GetLastError()); 
			return 1;
		}
		return 0;
	}
	else
	{
		// Launch the JVM; we're a subprocess of the service at this point
		int intres = launchJVMSage(lpszCmdLine, 0, FALSE, TRUE);
		return intres;
	}
#endif
	if (bWrapped)
	{
		if( !hPreInst )			/*set up window class and register it */
		{
			wc.lpszClassName 	= appName;
			wc.hInstance 		= hInst;
			wc.lpfnWndProc		= WndProc;
			wc.hCursor			= LoadCursor( NULL, IDC_ARROW );
			wc.hIcon			= LoadIcon( NULL, IDI_APPLICATION );
			wc.lpszMenuName		= NULL;
			wc.hbrBackground	= (HBRUSH)GetStockObject( WHITE_BRUSH );
			wc.style			= 0;
			wc.cbClsExtra		= 0;
			wc.cbWndExtra		= 0;

			if( !RegisterClass( &wc ) )
				return FALSE;
		}
	}

	strcpy(myCmdLine, lpszCmdLine);
#ifdef SAGE_TV
	// If the SageTV Service is running, then load us up in client mode and autoconnect to localhost
	// NOTE: If the service is set to start, but it hasn't loaded yet then we need to go into
	// this mode also AND start the service as part of it.
	// If we were forced into client mode from the cmd line just skip this stuff
	if (!bClient && !bWrapped)
	{
		// Open a handle to the SC Manager database.
		HANDLE serviceMutex;
		BOOL svcRunning = FALSE;
		if (CheckForMutex(&serviceMutex, "Global\\SageTVServiceSingleton", appName))
		{
			// Service is running, change to client mode. We don't change the app name because we're still
			// running as SageTV for all practical purposes from the user's standpoint. Mainly for message receiving.
			// We do change the mutex name because we don't want to prevent the service from starting
			// after us; we're linked to the Psueod-Client now.
			svcRunning = TRUE;
		}
		SC_HANDLE schSCManager = OpenSCManager( 
			NULL,                    // local machine 
			NULL,                    // ServicesActive database 
			SERVICE_START | SERVICE_QUERY_CONFIG | SERVICE_QUERY_STATUS);// start access rights 

		if (schSCManager) 
		{
			// Get the windows service
			SC_HANDLE schService = OpenService( 
				schSCManager,       // SCManager database 
				"SageTV",       // name of service 
				SERVICE_START | SERVICE_QUERY_CONFIG | SERVICE_QUERY_STATUS);// only need start access 

			if (schService)
			{
				// Check if the service is set to load automatically & check it's binary path name
				LPQUERY_SERVICE_CONFIG lpqscBuf; 
				lpqscBuf = (LPQUERY_SERVICE_CONFIG) LocalAlloc( 
					LPTR, 4096); 
				DWORD bytesNeeded;
				if (QueryServiceConfig(schService, lpqscBuf, 4096, &bytesNeeded) &&
					(lpqscBuf->dwStartType == SERVICE_AUTO_START || svcRunning))
				{
					// Check if its starting already
					SERVICE_STATUS srvStat;
					if (!svcRunning && QueryServiceStatus(schService, &srvStat) &&
						srvStat.dwCurrentState == SERVICE_STOPPED)
					{
						DWORD startErr = 0;
						BOOL startOK = StartService(schService, 0, NULL);
						if (!startOK)
							startErr = GetLastError();
						int lockTries = 60;
						while (startErr == ERROR_SERVICE_DATABASE_LOCKED && lockTries-- > 0)
						{
							Sleep(500);
							startErr = 0;
							startOK = StartService(schService, 0, NULL);
							if (!startOK)
								startErr = GetLastError();
						}
						if (startOK || startErr == ERROR_SERVICE_ALREADY_RUNNING)
						{
							bClient = TRUE;
							sprintf(mutexName, "Global\\SageTVPseudoSingleton");
							if (strstr(myCmdLine, "-startup"))
								strcpy(myCmdLine, "-startup -connect 127.0.0.1");
							else
								strcpy(myCmdLine, "-connect 127.0.0.1");
							svcRunning = FALSE;
						}
						else
						{
							char foobuf[256];
							sprintf(foobuf, "Win32 Error=0x%x trying to startup the SageTV Service", startErr);
							MessageBox(NULL, foobuf, "Error", MB_OK);
						}
					}
					else
					{
						// Service is starting/started....we'll connect to it
						bClient = TRUE;
						sprintf(mutexName, "Global\\SageTVPseudoSingleton");
						if (strstr(myCmdLine, "-startup"))
							strcpy(myCmdLine, "-startup -connect 127.0.0.1");
						else
							strcpy(myCmdLine, "-connect 127.0.0.1");
						svcRunning = FALSE;
					}
				}
				LocalFree(lpqscBuf);
				CloseServiceHandle(schService); 
			}
			CloseServiceHandle(schSCManager);
		}
		
		if (svcRunning)
		{
			// This can happen if we're not running as an Administrator so we can't determine the
			// state of the service. But we did find the mutex for it so we can assume it's running and connect to it.
			// Service is starting/started....we'll connect to it
			bClient = TRUE;
			sprintf(mutexName, "Global\\SageTVPseudoSingleton");
			if (strstr(myCmdLine, "-startup"))
				strcpy(myCmdLine, "-startup -connect 127.0.0.1");
			else
				strcpy(myCmdLine, "-connect 127.0.0.1");
		}
		CloseHandle(serviceMutex);
	}
#endif

	if (strlen(mutexName) && !bWrapped)
	{
		if (CheckForMutex(&m_hMutex, mutexName, appName))
			return TRUE;
	}

	if (bWrapped)
	{
		hWnd = CreateWindow( 	appName,
								"SageWin",
						WS_OVERLAPPEDWINDOW | WS_CAPTION | WS_CLIPCHILDREN,
								CW_USEDEFAULT,
								CW_USEDEFAULT,
								CW_USEDEFAULT,
								CW_USEDEFAULT,
								(HWND)NULL,
								(HMENU)NULL,
								(HINSTANCE)hInst,
								(LPSTR)NULL		);
	}

	int rv;
	DWORD exitCode;
	if (bWrapped)
	{
		rv = launchJVMSage(myCmdLine, hWnd, bClient, FALSE);
	}
	else
	{
		LPTSTR appPath = new TCHAR[2048];
		GetModuleFileName(NULL, appPath, 2048);
		LPTSTR newCmdLine = new TCHAR[2048];
		strcpy(newCmdLine, appPath);
		strcat(newCmdLine, " -wrapped ");
		if (bForcedClient)
			strcat(newCmdLine, "-client ");
		strcat(newCmdLine, myCmdLine);
		do
		{
			doStageCleaning(FALSE, bClient);

			STARTUPINFO si;
			PROCESS_INFORMATION pi;
			memset(&si, 0, sizeof(si));
			si.cb = sizeof(STARTUPINFO);
			memset(&pi, 0, sizeof(pi));
			if (CreateProcess(appPath, newCmdLine, NULL, NULL, TRUE,
					0, NULL, NULL, &si, &pi))
			{
				// Now we wait for the process to terminate
				if (WaitForSingleObject(pi.hProcess, INFINITE) != WAIT_FAILED)
				{
					if (GetExitCodeProcess(pi.hProcess, &exitCode) == FALSE)
					{
						rv = 0; // failure
					}
					else
						rv = 1;
				}
				else
					rv = 0; // failure
				CloseHandle(pi.hThread);
				CloseHandle(pi.hProcess);
			}
			else
			{
				rv = 0; // failure
				break;
			}
		} while (shouldRestartJVM(FALSE, bClient));
		delete [] appPath;
		delete [] newCmdLine;
	}
#ifdef SAGE_TV
    if (!bWrapped && m_hMutex)
    {
       CloseHandle(m_hMutex); //do as late as possible
       m_hMutex = NULL; 
    }
#endif
#ifdef SAGE_TV_CLIENT
    if (!bWrapped && m_hMutex)
    {
       CloseHandle(m_hMutex); //do as late as possible
       m_hMutex = NULL; 
    }
#endif
	return rv;
}

int launchJVMSage(LPSTR lpszCmdLine, HWND hWnd, BOOL bClient, BOOL bService)
{
	/*
	 * Explicitly load jvm.dll by using the Windows Registry to locate the current version to use.
	 */
	HKEY rootKey = HKEY_LOCAL_MACHINE;
	char currVer[16];
	HKEY myKey;
	DWORD readType;
	DWORD dwRead = 0;
	DWORD hsize = sizeof(dwRead);
	// Use a registry setting for this
	if (hWnd)
	{
		if (RegOpenKeyEx(rootKey, "Software\\Frey Technologies\\Common", 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
		{
			if (RegQueryValueEx(myKey, "consolewin", 0, &readType, (LPBYTE)&dwRead, &hsize) == ERROR_SUCCESS)
			{
				if (dwRead)
					AllocConsole();
			}
			RegCloseKey(myKey);
		}
	}
	
	LPTSTR appPath = new TCHAR[2048];
	GetModuleFileName(NULL, appPath, 2048);
	int appLen = strlen(appPath);
	for (int i = appLen - 1; i > 0; i--)
	{
		if (appPath[i] == '\\')
		{
			appPath[i + 1] = 0;
			break;
		}
	}
	// See if we've got a JVM in our own directory to load
	TCHAR includedJRE[1024];
	strcpy(includedJRE, appPath);
	strcat(includedJRE, "jre\\bin\\client\\jvm.dll");
	HMODULE hLib = LoadLibrary(includedJRE);
	if(hLib == NULL) 
	{
		// Failed, load the JVM from the registry instead

		hsize = sizeof(currVer);
		if (RegOpenKeyEx(rootKey, "Software\\JavaSoft\\Java Runtime Environment", 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			JVM_MISSING;
			return FALSE;
		}
		
		if (RegQueryValueEx(myKey, "CurrentVersion", 0, &readType, (LPBYTE)currVer, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			JVM_MISSING;
			return FALSE;
		}
		RegCloseKey(myKey);
		char pathKey[1024];
		strcpy(pathKey, "Software\\JavaSoft\\Java Runtime Environment\\");
		strcat(pathKey, currVer);
		char jvmPath[1024];
		if (RegOpenKeyEx(rootKey, pathKey, 0, KEY_QUERY_VALUE, &myKey) != ERROR_SUCCESS)
		{
			JVM_MISSING;
			return FALSE;
		}
		hsize = sizeof(jvmPath);
		if (RegQueryValueEx(myKey, "RuntimeLib", 0, &readType, (LPBYTE)jvmPath, &hsize) != ERROR_SUCCESS)
		{
			RegCloseKey(myKey);
			JVM_MISSING;
			return FALSE;
		}
		RegCloseKey(myKey);

		hLib = LoadLibrary(jvmPath);
		if(hLib == NULL) 
		{
			errorMsg("Could not find jvm.dll.\nPlease install Java Runtime Environment 1.7 or greater", "Java Missing", hWnd);
			return FALSE;
		}
	}
	
	// Retrieve address of JNI_CreateJavaVM()
	typedef  jint (JNICALL *P_JNI_CreateJavaVM)
		(JavaVM **pvm, void** penv, void *args);
	
	P_JNI_CreateJavaVM createJavaVM = (P_JNI_CreateJavaVM) 
		GetProcAddress(hLib, "JNI_CreateJavaVM");
	
	if (createJavaVM==NULL)
	{
		errorMsg("Could not execute jvm.dll.\nPlease install Java Runtime Environment 1.7 or greater", "Java Missing", hWnd);
		return FALSE;
	}

	LPTSTR prefFile = new TCHAR[512];
	LPTSTR jarPath = new TCHAR[4096];
	LPTSTR libraryPath = new TCHAR[512];
	
	stdOutHandle = GetStdHandle(STD_OUTPUT_HANDLE);
    JNIEnv *env;       /* pointer to native method interface */
	JavaVMInitArgs vm_args;
	JavaVMOption options[32];
	vm_args.nOptions = 0;
#ifdef SAGE_TV
	strcpy(prefFile, bClient ? "sagetvclient " : "sagetv ");
	strcat(prefFile, appPath);
	strcat(prefFile, bClient ? "SageClient.properties" : "Sage.properties");
	strcpy(jarPath, "-Djava.class.path=");
	strcat(jarPath, appPath);
	strcat(jarPath, "Sage.jar;");
  strcat(jarPath, appPath);
  strcat(jarPath, "JARs\\lucene-core-3.6.0.jar;");
	strcat(jarPath, appPath);
	strcat(jarPath, "xerces.jar;");
	strcat(jarPath, appPath);
	strcat(jarPath, "plugin.jar;");
	strcat(jarPath, appPath);
	strcat(jarPath, ";");
	char* envcp = getenv("CLASSPATH");
	if (envcp)
		strcat(jarPath, envcp);
	options[vm_args.nOptions++].optionString = jarPath;
#endif
#ifdef SAGE_TV_CLIENT
	strcpy(prefFile, "sagetvclient ");
	strcat(prefFile, appPath);
	strcat(prefFile, "SageClient.properties");
	strcpy(jarPath, "-Djava.class.path=");
	strcat(jarPath, appPath);
	strcat(jarPath, "Sage.jar;");
	strcat(jarPath, appPath);
  strcat(jarPath, "JARs\\lucene-core-3.6.0.jar;");
	strcat(jarPath, appPath);
	strcat(jarPath, "plugin.jar;");
	strcat(jarPath, appPath);
	strcat(jarPath, ";");
	char* envcp = getenv("CLASSPATH");
	if (envcp)
		strcat(jarPath, envcp);
	options[vm_args.nOptions++].optionString = jarPath;
#endif

#ifdef JPROFILER_ENABLE
	strcpy(libraryPath, "-Djava.library.path=");
	strcat(libraryPath, appPath);
	strcat(libraryPath, ";");
	strcat(libraryPath, "C:\\Program Files\\JProfiler3\\bin\\windows");
	options[vm_args.nOptions++].optionString = libraryPath;  /* set native library path */
#else

	// Get the extra library path info out of the registry
	char nativePath[1024];
	DWORD jvmHighResTimer = 1;
	if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common", 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		hsize = sizeof(nativePath);
		if (RegQueryValueEx(myKey, "NativeLibPath", 0, &readType, (LPBYTE)nativePath, &hsize) != ERROR_SUCCESS)
		{
			nativePath[0] = '\0';
		}
		if (RegQueryValueEx(myKey, "JVMHighResTimer", 0, &readType, (LPBYTE)&jvmHighResTimer, &hsize) != ERROR_SUCCESS)
		{
			jvmHighResTimer = 1;
		}
		RegCloseKey(myKey);
	}
	else
		nativePath[0] = '\0';

#ifdef _DEBUG
	strcpy(libraryPath, "-Djava.library.path=");
	strcat(libraryPath, appPath);
	strcat(libraryPath, "..\\DebugDLLs;");
	strcat(libraryPath, appPath);
	strcat(libraryPath, ";");
	strcat(libraryPath, nativePath);
	options[vm_args.nOptions++].optionString = libraryPath;
#else
	strcpy(libraryPath, "-Djava.library.path=");
	strcat(libraryPath, appPath);
	strcat(libraryPath, ";");
	strcat(libraryPath, nativePath);
	options[vm_args.nOptions++].optionString = libraryPath;  /* set native library path */
#endif
#endif

	// Now append everything in the JARs subfolder (that's a .jar file) to the classpath
	char* dirPath = new char[1024];
	strcpy(dirPath, appPath);
	strcat(dirPath, "JARs\\*.jar");
	WIN32_FIND_DATA findData;
	HANDLE srchHandle = FindFirstFile(dirPath, &findData);
	if (srchHandle != INVALID_HANDLE_VALUE)
	{
		do
		{
			strcat(jarPath, ";JARs\\");
			strcat(jarPath, findData.cFileName);
		}
		while (FindNextFile(srchHandle, &findData));

		FindClose(srchHandle);
	}
	delete [] dirPath;

	

#ifndef SAGE_RECORDER
	// Because I made metaimage have a floating min bound I must increase this to prevent OutOfMemoryErrors
	// NARFLEX: 6-13-08 Change the min to be 256MB because there's a fair amount of users who get much better 
	// performance w/ that setting...and also people have a lot more RAM nowadays. :)
	// NARFLEX: 5-26-09 Change this to be 384MB because that's more appropriate now I think
	// NARFLEX: 3-25-15 Times have changed...how about 768 as a default now. :)
	char memString[32];
	strcpy(memString, "-Xmx768m");
//#ifdef SAGE_TV_SERVICE
	// We definitely don't need as much memory for the service since it doesn't have any UI stuff
//	strcpy(memString, "-Xmx128m");
//#endif
	if (RegCreateKeyEx(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\SageTV", 0, 0,
		REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, 0, &myKey, 0) == ERROR_SUCCESS)
	{
		DWORD regMemVal;
		hsize = sizeof(regMemVal);
		if (RegQueryValueEx(myKey, "JVMMaxHeapSizeMB", 0, &readType, (LPBYTE) &regMemVal, &hsize) == ERROR_SUCCESS)
		{
			if (regMemVal > 0)
				sprintf(memString, "-Xmx%dm", regMemVal);
		}
		else
		{
			regMemVal = 0;
			RegSetValueEx(myKey, "JVMMaxHeapSizeMB", 0, REG_DWORD, (LPBYTE) &regMemVal, sizeof(regMemVal));
		}
		hsize = sizeof(regMemVal);
		if (RegQueryValueEx(myKey, "JITDebug", 0, &readType, (LPBYTE) &regMemVal, &hsize) == ERROR_SUCCESS)
		{
			if (regMemVal)
				options[vm_args.nOptions++].optionString = "-XX:+PrintCompilation";
		}
	}
	RegCloseKey(myKey);

	options[vm_args.nOptions++].optionString = memString;
#endif

#ifdef SAGE_TV_SERVICE
	// Be sure logoff doesn't kill the JVM
	options[vm_args.nOptions++].optionString = "-Xrs";
#endif

	// stderr redirection
	options[vm_args.nOptions++].optionString = "vfprintf";
	options[vm_args.nOptions - 1].extraInfo = my_vfprintf;

#ifndef SAGE_TV_SERVICE
	options[vm_args.nOptions++].optionString = "-Xms24m";
#endif
	/*
	 * After testing & research I decided on the Throughput GC which uses the
	 * UseParallelGC option. The incremental GC was terrible in performance and
	 * is definitely not a good choice. The Concurrent GC performed close to
	 * the Throughput GC, but seemed to use a lot more CPU on the side
	 * maintaining the heap stats.  The Concurrent GC is also listed as being
	 * more suitable for this type of application. After we finish the memory
	 * optimizations we should probably take another look at this.
	 */
	/*
	 * After further testing, the ParallelGC isn't good because it grows the heap too fast,
	 * our GC efforts our mostly unnoticed until we hit the max.
	 * The ConcMarkSweepGC adds about 50MB of memory usage to the application which bites.
	 */
	/*
	 * Memory usage with the default & incremental collectors is similar, but performance
	 * is much better with the default collector. How about that? We're back to the default
	 * collector. THE INCREMENTAL GC IS BAD, DON'T BOTHER WITH IT ANYMORE.
	 */
//	options[vm_args.nOptions++].optionString = "-XX:+UseParallelGC";
//	options[vm_args.nOptions++].optionString = "-XX:+UseConcMarkSweepGC";
//	options[vm_args.nOptions++].optionString = "-Xincgc";

	// Use the server HotSpot VM because we're more like a big server than a small client app
	options[vm_args.nOptions++].optionString = "-server";
	// Don't set the GC options for the 1.5 VM since it has better internal GC handling
	if (strstr(currVer, "1.4"))
	{
		// NOTE: Narflex 9/30/06 - after downgrading to 1.4 at home after using 1.5 for a year
		// and a half I was having MAJOR problems with the GC pausing the video on the MVP.
		// Disabling these so-called optimizations I added resolved the issue; previously all 3 were enabled
		//options[vm_args.nOptions++].optionString = "-XX:NewRatio=3";
		//20, 35 is what we were using
		//options[vm_args.nOptions++].optionString = "-XX:MinHeapFreeRatio=25";
		//options[vm_args.nOptions++].optionString = "-XX:MaxHeapFreeRatio=40";
	}
	else if (strstr(currVer, "1.5"))
	{
		if (RegCreateKeyEx(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\SageTV", 0, 0,
			REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, 0, &myKey, 0) == ERROR_SUCCESS)
		{
			DWORD regMemVal;
			hsize = sizeof(regMemVal);
			if (RegQueryValueEx(myKey, "JVMGCMaxPauseTime", 0, &readType, (LPBYTE) &regMemVal, &hsize) == ERROR_SUCCESS)
			{
				if (regMemVal > 0)
				{
					char gcString[128];
					sprintf(gcString, "-XX:MaxGCPauseMillis=%d", regMemVal);
				}
			}
			else
			{
				regMemVal = 0;
				RegSetValueEx(myKey, "JVMGCMaxPauseTime", 0, REG_DWORD, (LPBYTE) &regMemVal, sizeof(regMemVal));
			}

		}
		RegCloseKey(myKey);
	}
	// For some Windows users, G1GC appears to be causing a significant memory leak.
	/*else if (strstr(currVer, "1.8") || strstr(currVer, "1.9"))
	{
		// If we are running Java 8 or 9, enable string deduplication. This has very measurable
		// benefits in multi-miniclient situations.
		options[vm_args.nOptions++].optionString = "-XX:+UseG1GC";
		options[vm_args.nOptions++].optionString = "-XX:+UseStringDeduplication";
	}*/
	options[vm_args.nOptions++].optionString = "-verbose:gc";
#ifdef _DEBUG

// Turn off this for now, we just don't need this much extra info
//	options[vm_args.nOptions++].optionString = "-XX:+PrintGCDetails";
#endif

//#define JAVA_PROFILE_ENABLE
#ifdef JAVA_PROFILE_ENABLE
//	options[vm_args.nOptions].optionString = "-Xrunhprof:cpu=samples,depth=6";
//	options[vm_args.nOptions].optionString = "-Xrunhprof:depth=8,thread=y";
//	options[vm_args.nOptions].optionString = "-agentlib:hprof=heap=dump,format=b,depth=6,doe=n";
	options[vm_args.nOptions].optionString = "-agentlib:C:\\dev\\Tools\\ariadna\\ariadna";
	vm_args.nOptions++;
#endif
//#define JPROFILER_ENABLE
#ifdef JPROFILER_ENABLE
	options[vm_args.nOptions].optionString = "-Xint";
	vm_args.nOptions++;
	options[vm_args.nOptions].optionString = "-Xrunjprofiler:port=8849";
	vm_args.nOptions++;
	options[vm_args.nOptions].optionString = "-Xbootclasspath/a:C:\\Program Files\\jprofiler3\\bin\\agent.jar";
	vm_args.nOptions++;
#endif

//#define JAVA_DEBUG
#ifdef JAVA_DEBUG
	options[vm_args.nOptions++].optionString = "-Xdebug";
	options[vm_args.nOptions++].optionString = "-Xrunjdwp:transport=dt_shmem,address=jdbconn,server=y,suspend=n";
#endif

	/*
	 * According to the JVM Sun bug http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4500388
	 * we need to set this flag in order to avoid clock issues. We saw
	 * this issue on both the 1.4 AND the 1.5 JVMs.
	 * NOTE: This is enabled by default!
	 */
	if (jvmHighResTimer)
	{
		options[vm_args.nOptions++].optionString = "-XX:+ForceTimeHighResolution";
	}

	vm_args.version = JNI_VERSION_1_2;
	vm_args.options = options;
	vm_args.ignoreUnrecognized = true;

    /* Note that in the Java 2 SDK, there is no longer any need to call 
	 * JNI_GetDefaultJavaVMInitArgs. 
	 */
//	int res = JNI_CreateJavaVM(&vm, (void **)&env, &vm_args);

	int res = (*createJavaVM)(&vm, (void**) &env, &vm_args); 
	if (res != 0)
	{
		errorMsg("Could not create JVM.\nPlease reinstall Java Runtime Environment 1.7 or greater.", "Java Missing", hWnd);
		return FALSE;
	}
	globalenv = env;

	// NOTE: Armadillo license protection has been removed for the open source version. If you want to use
	// the EPG servers, you need to set the global environment variable SAGETVUSERKEY to be your license key
	// for SageTV.

	// We used to pass this with a -D option, but that showed up in the hs_err_pid log file
	// as of JVM 1.5.
    char serialKey[256]="";
	if (GetEnvironmentVariable("SAGETVUSERKEY", serialKey, 255) <= 0) {
		strcpy(serialKey, "NOKEY");
	}
	jclass sysCls = env->FindClass("java/lang/System");
	jmethodID sysMeth = env->GetStaticMethodID(sysCls, "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
	env->CallStaticObjectMethod(sysCls, sysMeth, env->NewStringUTF("USERKEY"), env->NewStringUTF(serialKey));

    /* invoke the Main.test method using the JNI */
    jclass cls = env->FindClass("sage/Sage");
	jthrowable clsThrow =env->ExceptionOccurred();
	if (clsThrow)
	{
		popupExceptionError(env, clsThrow, "Could not find JAR file.", hWnd);
        env->ExceptionDescribe();
        env->ExceptionClear();
		MessageBox(NULL, libraryPath, "lib path", MB_OK);
		MessageBox(NULL, jarPath, "jar path", MB_OK);
		MessageBox(NULL, "Could not find Java classes.\nMake sure that the JAR file is in the SageTV program folder.", "JAR Missing", MB_OK);
		return FALSE;
	}
	
	jclass uiMgrClass = env->FindClass("sage/UIManager");

	char winBuf[16];
    char stdOutBuf[16];

	// parsed as base 10 integers from Java
	sprintf(winBuf, "%lld", (jlong) hWnd);
    sprintf(stdOutBuf, "%lld", (jlong) stdOutHandle);
    jobjectArray args = env->NewObjectArray((prefFile == 0) ? 3 : 4, env->FindClass("java/lang/String"),
    	env->NewStringUTF(winBuf));
	env->SetObjectArrayElement(args, 1, env->NewStringUTF(stdOutBuf));
	env->SetObjectArrayElement(args, 2, env->NewStringUTF(lpszCmdLine));
	if (prefFile != 0)
		env->SetObjectArrayElement(args, 3, env->NewStringUTF(prefFile));

  jmethodID mid = env->GetStaticMethodID(cls, "b", "([Ljava/lang/String;)V");

	// Legacy flags for license protection...not really needed anymore, could be removed after checking
	jfieldID fid = env->GetStaticFieldID(cls, "j", "Z");
	env->SetStaticBooleanField(cls, fid, JNI_FALSE);
	fid = env->GetStaticFieldID(cls, "k", "Z");
	env->SetStaticBooleanField(cls, fid, JNI_FALSE);
	fid = env->GetStaticFieldID(cls, "z", "Z");
	env->SetStaticBooleanField(cls, fid, JNI_TRUE);

	// Get the size of the JAR file and mod it by 1024
	int jarFileSize = 0;
	WIN32_FILE_ATTRIBUTE_DATA fileInfo;
	if (GetFileAttributesEx("Sage.jar", GetFileExInfoStandard, (void*)&fileInfo))
	{
		jarFileSize = (fileInfo.nFileSizeLow) % 1024;
	}
	fid = env->GetStaticFieldID(cls, "w", "I");
	env->SetStaticIntField(cls, fid, jarFileSize);

	if (env->ExceptionCheck())
	{
		errorMsg("Could not find main in class.\nPlease reinstall the Sage application", "Java Error", hWnd);
		return FALSE;
	}
    jmethodID tmid = env->GetStaticMethodID(cls, "processMsg", "()V");
	if (env->ExceptionCheck())
	{
		errorMsg("Could not find method in class.\nPlease reinstall the Sage application", "Java Error", hWnd);
		return FALSE;
	}
	jmethodID taskmid = env->GetStaticMethodID(cls, "taskbarAction", "(ZII)V");
	if (env->ExceptionCheck())
	{
		errorMsg("Could not find method2 in class.\nPlease reinstall the Sage application", "Java Error", hWnd);
		return FALSE;
	}
	
	// This was where we set the crytpo key for the DB and license stuff inside the JAR file, now removed.
  // The crypto key is set inside the JAR file itself now (although its only needed for decrypting
  // legacy database files).

	//jmethodID segmid = env->GetStaticMethodID(cls, "segmentDetected", "(J)V");
	//if (env->ExceptionOccurred()) env->ExceptionDescribe();
    
	env->CallStaticVoidMethod(cls, mid, args);
//        env->CallStaticVoidMethod(cls, mid, 0);
	jthrowable mainThrow = env->ExceptionOccurred();
	if (mainThrow != NULL)
	{
		env->ExceptionClear();
		jmethodID toStr = env->GetMethodID(env->GetObjectClass(mainThrow), "toString", "()Ljava/lang/String;");
//env->ExceptionDescribe();
		jstring throwStr = (jstring)env->CallObjectMethod(mainThrow, toStr);
		const char* cThrowStr = env->GetStringUTFChars(throwStr, 0);
		char *errStr = new char[env->GetStringLength(throwStr) + 64];
		sprintf(errStr, "An exception occured in Java:\n%s", cThrowStr);
		errorMsg(errStr, "Java Exception", hWnd);
		env->ReleaseStringUTFChars(throwStr, cThrowStr);
		delete errStr;
		return FALSE;
	}

	delete [] jarPath;
	delete [] libraryPath;
	delete [] prefFile;

/*	if (bService)
	{
		// Register us as running with the ServiceControlDispatcher
		// Initialization complete - report running status. 
		SageServiceStatus.dwCurrentState       = SERVICE_RUNNING; 
		SageServiceStatus.dwCheckPoint         = 0; 
		SageServiceStatus.dwWaitHint           = 0; 
 
		if (!SetServiceStatus (SageServiceStatusHandle, &SageServiceStatus)) 
		{ 
			DWORD status = GetLastError();
			SvcDebugOut(" [SageTV] SetServiceStatus error %ld\n",status); 
		} 

	}
*/
	jmethodID uemid = NULL;
	jobject uimgrobj = NULL;
	jmethodID updateTaskmid = NULL;
	jobject vfobj = NULL;
	jmethodID playCtrlmid = NULL;
	jmethodID deepSleepmid = NULL;
	jmethodID exitmid = NULL;
	jclass sageTVClass = env->FindClass("sage/SageTV");
	if (uiMgrClass)
	{
		jmethodID uiInstMeth = env->GetStaticMethodID(uiMgrClass, "getLocalUI", "()Lsage/UIManager;");
		if (uiInstMeth)
		{
			uimgrobj = env->CallStaticObjectMethod(uiMgrClass, uiInstMeth);
			if (uimgrobj)
			{
				uemid = env->GetMethodID(uiMgrClass, "doUserEvent", "(I)V");
				env->ExceptionClear();
				updateTaskmid = env->GetMethodID(uiMgrClass, "updateTaskbarIcon", "()V");
				env->ExceptionClear();
				jmethodID getVFMeth = env->GetMethodID(uiMgrClass, "getVideoFrame", "()Lsage/VideoFrame;");
				if (getVFMeth)
				{
					vfobj = env->CallObjectMethod(uimgrobj, getVFMeth);
				}
				env->ExceptionClear();
			}
		}
		if (sageTVClass)
		{
			exitmid = env->GetStaticMethodID(sageTVClass, "exit", "()V");
			env->ExceptionClear();
			deepSleepmid = env->GetStaticMethodID(sageTVClass, "deepSleep","(Z)V");
		}
		env->ExceptionClear();
	}
	env->ExceptionClear();
	jclass vfClass = env->FindClass("sage/VideoFrame");
	if (vfClass)
	{
		playCtrlmid = env->GetMethodID(vfClass, "playbackControl", "(I)V");
	}
	env->ExceptionClear();

	BOOL cracked = 0;
	delete [] appPath;

#define WM_DVD_EVENT (WM_USER + 396)

	//DWORD numWrit;

    MSG lpMsg;
	BOOL bRet;
	BOOL poweringDownState = 0;
	int lastRuiTimeCheck = 0;
	while((bRet = GetMessage( &lpMsg, NULL, 0, 0 )) != 0)					/* begin the message loop */
	{
//		sysOutPrint(env, "Kick! msg=%d lparam=%d wParam=%d\r\n", lpMsg.message - 0x400,
//			lpMsg.lParam, lpMsg.wParam);
		if (bRet == -1)
		{
			// Error receiving the message
			if (!hWnd)
			{
				errorMsg("ERROR IN GET MESSAGE FROM SAGETV", "ERROR", NULL);
			}
		}
		else if (lpMsg.message - 0x400 == 666)
		{
			env->CallStaticVoidMethod(cls, tmid);
			if (env->ExceptionOccurred())
			{
				sysOutPrint(env, "Got exception during native callback 666\n");
				env->ExceptionClear();
			}
		}
		else if (lpMsg.message - 0x400 == 700)
		{
			// This is for when we're running as a service and we're told to exit
			env->CallStaticVoidMethod(sageTVClass, exitmid);
		}
		else if (lpMsg.message - 0x400 == 667)
		{
			globalenv = 0;
		    vm->DetachCurrentThread();
		    vm->DestroyJavaVM();
		    break;
		}
		else if (lpMsg.message - 0x400 == 26)
		{
			if (lpMsg.lParam == WM_LBUTTONDBLCLK || lpMsg.lParam == WM_RBUTTONUP)
			{
				POINT mousePos;
				GetCursorPos(&mousePos);
				env->CallStaticVoidMethod(cls, taskmid, (lpMsg.lParam == WM_LBUTTONDBLCLK),
					mousePos.x, mousePos.y);
			}
			else if (lpMsg.lParam == WM_MOUSEMOVE && updateTaskmid)
			{
				env->CallVoidMethod(uimgrobj, updateTaskmid);
			}
			if (env->ExceptionOccurred())
			{
				sysOutPrint(env, "Got exception during native callback 26\n");
				env->ExceptionClear();
			}
		}
		else if (lpMsg.message - 0x400 == 234 && uemid)
		{
			env->CallVoidMethod(uimgrobj, uemid, (jint) lpMsg.lParam);
			if (env->ExceptionOccurred())
			{
				sysOutPrint(env, "Got exception during native callback 234\n");
				env->ExceptionClear();
			}
		}
		else if (lpMsg.message == WM_DVD_EVENT && playCtrlmid)
		{
			env->CallVoidMethod(vfobj, playCtrlmid, 0);
			if (env->ExceptionOccurred())
			{
				sysOutPrint(env, "Got exception during native callback WM_DVD_EVENT\n");
				env->ExceptionClear();
			}
		}
		/*else if (lpMsg.message - 0x400 == 69)
		{
			if (((jlong) lpMsg.wParam) < 50000000)
			{
				env->CallStaticVoidMethod(cls, segmid, (jlong) lpMsg.lParam);
sprintf(buf, "Energy=%d time=%d\r\n", (jint) lpMsg.wParam, (jint) lpMsg.lParam);
MYOUT(buf);
			}
		}*/
		else if (lpMsg.message == WM_POWERBROADCAST && deepSleepmid)
		{
			if (lpMsg.wParam == PBT_APMSUSPEND)
			{
				// Enter deep sleep
				poweringDownState = 1;
				env->CallStaticVoidMethod(sageTVClass, deepSleepmid, TRUE);
			}
			else if (lpMsg.wParam == PBT_APMRESUMESUSPEND ||
					lpMsg.wParam == PBT_APMQUERYSUSPENDFAILED ||
					lpMsg.wParam == PBT_APMRESUMEAUTOMATIC)
			{
				// Wake up from deep sleep
				poweringDownState = 0;
				env->CallStaticVoidMethod(sageTVClass, deepSleepmid, FALSE);
			}
			if (env->ExceptionOccurred())
			{
				sysOutPrint(env, "Got exception during native callback WM_POWERBROADCAST\n");
				env->ExceptionClear();
			}
		}
		else
		{
			TranslateMessage( &lpMsg );
			DispatchMessage( &lpMsg );
		}
	}

	FreeLibrary(hLib);

	return( lpMsg.wParam);
}

#define WM_APPCOMMAND                   0x0319
LRESULT CALLBACK WndProc( HWND hWnd, UINT messg,				/*callback procedure */
								WPARAM wParam, LPARAM lParam )
{
/**
	JNIEnv* env;
	BOOL detachIt = FALSE;
	if (vm)
	{
		if (vm->GetEnv((void**)&env, JNI_VERSION_1_2) != JNI_OK)
		{
			detachIt = TRUE;
			vm->AttachCurrentThread((void**)&env, NULL);
		}
		sysOutPrint(globalenv, "WndProc msg=%d lparam=%d wParam=%d\r\n", messg, lParam, wParam);
		if (detachIt)
			vm->DetachCurrentThread();
	}
/**/
	switch(messg)
	{
		case WM_DESTROY:
			PostQuitMessage( 0 );
			break;

		case (WM_USER + 26):
		case (WM_USER + 234):
		case (WM_USER + 669):
			PostMessage(hWnd, messg, wParam, lParam);
			break;
		case WM_POWERBROADCAST:
			if (vm && (wParam == PBT_APMSUSPEND || wParam == PBT_APMRESUMESUSPEND ||
					wParam == PBT_APMQUERYSUSPENDFAILED || wParam == PBT_APMRESUMEAUTOMATIC))
			{
				JNIEnv* env;
				BOOL detachIt = FALSE;
				if (vm->GetEnv((void**)&env, JNI_VERSION_1_2) != JNI_OK)
				{
					detachIt = TRUE;
					vm->AttachCurrentThread((void**)&env, NULL);
				}
				jclass sageTVClass = env->FindClass("sage/SageTV");
				if (sageTVClass)
				{
					jmethodID deepSleepmid = env->GetStaticMethodID(sageTVClass, "deepSleep", "(Z)V");
					if (deepSleepmid)
					{
						env->CallStaticVoidMethod(sageTVClass, deepSleepmid, wParam == PBT_APMSUSPEND);
					}
				}
				env->ExceptionClear();
				if (detachIt)
					vm->DetachCurrentThread();
			}
			break;
		case WM_DISPLAYCHANGE:
			if (vm)
			{
				JNIEnv* env;
				BOOL detachIt = FALSE;
				if (vm->GetEnv((void**)&env, JNI_VERSION_1_2) != JNI_OK)
				{
					detachIt = TRUE;
					vm->AttachCurrentThread((void**)&env, NULL);
				}
				jmethodID dispChangeID = 0;
				jobject uimgrobj = 0;
				jclass uiMgrClass = env->FindClass("sage/UIManager");
				env->ExceptionClear();
				if (uiMgrClass)
				{
					jmethodID uiInstMeth = env->GetStaticMethodID(uiMgrClass, "getLocalUI", "()Lsage/UIManager;");
					env->ExceptionClear();
					if (uiInstMeth)
					{
						uimgrobj = env->CallStaticObjectMethod(uiMgrClass, uiInstMeth);
						env->ExceptionClear();
						if (uimgrobj)
						{
							dispChangeID = env->GetMethodID(uiMgrClass, "displayChange", "(II)V");
							env->ExceptionClear();
						}
					}
				}
				if (dispChangeID)
				{
					env->CallVoidMethod(uimgrobj, dispChangeID, LOWORD(lParam), HIWORD(lParam));
					env->ExceptionClear();
				}
				if (detachIt)
					vm->DetachCurrentThread();
			}
			break;
		default:
			return( DefWindowProc( hWnd, messg, wParam, lParam ) );
	}

	return( 0L );
}

/*
 * WINDOWS SERVICES CODE
 */
void WINAPI SageServiceStart (DWORD argc, LPTSTR *argv) 
{ 
    DWORD status;
    DWORD specificError; 
    SageServiceStatus.dwServiceType        = SERVICE_WIN32_OWN_PROCESS;
    SageServiceStatus.dwCurrentState       = SERVICE_START_PENDING;
    SageServiceStatus.dwControlsAccepted   = SERVICE_ACCEPT_STOP | SERVICE_ACCEPT_SHUTDOWN | SERVICE_ACCEPT_POWEREVENT; 
    SageServiceStatus.dwWin32ExitCode      = NO_ERROR;
    SageServiceStatus.dwServiceSpecificExitCode = 0; 
    SageServiceStatus.dwCheckPoint         = 1; 
    SageServiceStatus.dwWaitHint           = 0; 
 
    SageServiceStatusHandle = RegisterServiceCtrlHandlerEx( 
        "SageTV", SageServiceCtrlHandlerEx, NULL);
 
    if (SageServiceStatusHandle == (SERVICE_STATUS_HANDLE)0) 
    { 
        SvcDebugOut(" [SageTV] RegisterServiceCtrlHandler failed %d\n", GetLastError()); 
        return; 
    } 
 
    // Initialization can take a long time
    SageServiceStatus.dwCurrentState       = SERVICE_START_PENDING;
	SageServiceStatus.dwWaitHint = 1000*60*30; // Give us awhile minute to load
 
    if (!SetServiceStatus (SageServiceStatusHandle, &SageServiceStatus)) 
    { 
        status = GetLastError(); 
        SvcDebugOut(" [SageTV] SetServiceStatus error %ld\n",status); 
    } 
    // 
    // Create a null dacl.
    // 
	m_hMutex = NULL;
	SECURITY_DESCRIPTOR  sd;
	SECURITY_ATTRIBUTES sa;
    if (InitializeSecurityDescriptor(&sd, SECURITY_DESCRIPTOR_REVISION))
	{
		if (SetSecurityDescriptorDacl(&sd, TRUE, (PACL) NULL, FALSE))
		{
			ZeroMemory(&sa, sizeof(SECURITY_ATTRIBUTES));
			sa.nLength = sizeof(SECURITY_ATTRIBUTES);
			sa.lpSecurityDescriptor = &sd;
			sa.bInheritHandle = TRUE;
			m_hMutex = CreateMutex(&sa, FALSE, "Global\\SageTVServiceSingleton");
		}
	}
	if (!m_hMutex)
	{
		m_hMutex = CreateMutex(NULL, FALSE, "Global\\SageTVServiceSingleton");
	}

	// This doesn't return until Sage dies
    status = SageServiceInitialization(argc,argv, &specificError);
 
    // Handle error condition...or just stopping
//    if (status != NO_ERROR) 
    { 
		CloseHandle(m_hMutex);
        SageServiceStatus.dwCurrentState       = SERVICE_STOPPED; 
        SageServiceStatus.dwCheckPoint         = 0;
        SageServiceStatus.dwWaitHint           = 0; 
        SageServiceStatus.dwWin32ExitCode      = status; 
        SageServiceStatus.dwServiceSpecificExitCode = specificError;
 
        SetServiceStatus (SageServiceStatusHandle, &SageServiceStatus); 
        return; 
    } 
 
	CloseHandle(m_hMutex);
    return; 
} 
 
// Stub initialization function. 
DWORD WINAPI SageServiceInitialization(DWORD   argc, LPTSTR  *argv, DWORD* res) 
{ 
	// Indicate we are started OK because the EXE we'll be launching won't be able to do that itself
/*	DWORD argsLen = 0;
	DWORD i;
	for (i = 0; i < argc; i++)
		argsLen += strlen(argv[i]);
	// There should only ever be one argument since you can't pass params to services
	char lpszCmdLine[256];
	strcpy(lpszCmdLine, "-wrapped ");
	for (i = 0; i < argc; i++)
	{
		if (i > 0)
			strcat(lpszCmdLine, " ");
		strcat(lpszCmdLine, argv[i]);
	}
*/	BOOL firstRun = TRUE;
	int intres;
	DWORD exitCode;
	LPTSTR appPath = new TCHAR[2048];
	GetModuleFileName(NULL, appPath, 2048);
	LPTSTR newCmdLine = new TCHAR[2048];
	strcpy(newCmdLine, appPath);
	strcat(newCmdLine, " -wrapped");
	do
	{
		doStageCleaning(TRUE, FALSE);

		STARTUPINFO si;
		memset(&si, 0, sizeof(si));
		si.cb = sizeof(STARTUPINFO);
		memset(&pi, 0, sizeof(pi));
		if (CreateProcess(appPath, newCmdLine, NULL, NULL, TRUE,
				0, NULL, NULL, &si, &pi))
		{
			if (firstRun)
			{
				// Register us as running with the ServiceControlDispatcher
				// Initialization complete - report running status. 
				SageServiceStatus.dwCurrentState       = SERVICE_RUNNING; 
				SageServiceStatus.dwCheckPoint         = 0;
				SageServiceStatus.dwWaitHint           = 0; 
				if (!SetServiceStatus (SageServiceStatusHandle, &SageServiceStatus)) 
				{ 
					DWORD status = GetLastError();
					SvcDebugOut(" [SageTV] SetServiceStatus error %ld\n",status); 
				}
				firstRun = FALSE;
			}

			// Now we wait for the process to terminate
			if (WaitForSingleObject(pi.hProcess, INFINITE) != WAIT_FAILED)
			{
				if (GetExitCodeProcess(pi.hProcess, &exitCode) == FALSE)
				{
					intres = 0; // failure
				}
				else
					intres = 1;
			}
			else
				intres = 0; // failure
			CloseHandle(pi.hThread);
			CloseHandle(pi.hProcess);
		}
		else
		{
			intres = 0; // failure
			break;
		}
	} while (shouldRestartJVM(TRUE, FALSE));

	*res = (DWORD) intres;
    return intres ? NO_ERROR : ERROR_SERVICE_SPECIFIC_ERROR;
} 

DWORD WINAPI SageServiceCtrlHandlerEx (DWORD dwControl,     // requested control code
  DWORD dwEventType,   // event type
  LPVOID lpEventData,  // event data
  LPVOID lpContext     // user-defined context data
) 
{ 
	// With the EXE wrapper technique; we need to send messages to the thread for the child process we spawned instead of invoking them in
	// the VM directly ourselves.
/*	JNIEnv* env;
	BOOL detachIt = FALSE;
	if (vm)
	{
		if (vm->GetEnv((void**)&env, JNI_VERSION_1_2) != JNI_OK)
		{
			detachIt = TRUE;
			vm->AttachCurrentThread((void**)&env, NULL);
		}
		sysOutPrint(globalenv, "SageServiceCtrlHandlerEx ctrl=%d msg=%d\r\n", dwControl, dwEventType);
		if (detachIt)
			vm->DetachCurrentThread();
	}
  */
	DWORD status; 

	static BOOL jmethInit = FALSE;
	static jclass sageTVClass = NULL;
	static jmethodID exitmid;
	static jmethodID deepSleepmid;
	if (!jmethInit && vm)
	{
		JNIEnv* env;
		BOOL detachIt = FALSE;
		if (vm->GetEnv((void**)&env, JNI_VERSION_1_2) != JNI_OK)
		{
			detachIt = TRUE;
			vm->AttachCurrentThread((void**)&env, NULL);
		}
		sageTVClass = env->FindClass("sage/SageTV");
		if (sageTVClass)
		{
			exitmid = env->GetStaticMethodID(sageTVClass, "exit", "()V");
			env->ExceptionClear();
			deepSleepmid = env->GetStaticMethodID(sageTVClass, "deepSleep", "(Z)V");
		}
		env->ExceptionClear();
		if (detachIt)
			vm->DetachCurrentThread();
		jmethInit = TRUE;
	}
	DWORD rv = NO_ERROR;
    switch(dwControl) 
    { 
        case SERVICE_CONTROL_STOP: 
		case SERVICE_CONTROL_SHUTDOWN:
			// Kill the SageTV application by calling UIManager.exit()
/*			if (vm)
			{
				JNIEnv* env;
				BOOL detachIt = FALSE;
				if (vm->GetEnv((void**)&env, JNI_VERSION_1_2) != JNI_OK)
				{
					detachIt = TRUE;
					vm->AttachCurrentThread((void**)&env, NULL);
				}
				if (exitmid)
					env->CallStaticVoidMethod(sageTVClass, exitmid);
				env->ExceptionClear();
				if (detachIt)
					vm->DetachCurrentThread();
			}*/
			// Send the EXIT message to the child process
			PostThreadMessage(pi.dwThreadId, WM_USER + 700, 0, 0);

			// Wait for the child process to exit...
			WaitForSingleObject(pi.hProcess, INFINITE);

	        // Do whatever it takes to stop here. 
            SageServiceStatus.dwWin32ExitCode = 0; 
            SageServiceStatus.dwCurrentState  = SERVICE_STOPPED; 
            SageServiceStatus.dwCheckPoint    = 0; 
            SageServiceStatus.dwWaitHint      = 0; 
 
            if (!SetServiceStatus (SageServiceStatusHandle, 
                &SageServiceStatus))
            { 
                status = GetLastError(); 
                SvcDebugOut(" [SageTV] SetServiceStatus error %ld\n",status); 
            } 
 
            SvcDebugOut(" [SageTV] Leaving SageService \n",0); 
            return NO_ERROR; 
		case SERVICE_CONTROL_POWEREVENT:
			if (dwEventType == PBT_APMSUSPEND || dwEventType == PBT_APMRESUMESUSPEND ||
					dwEventType == PBT_APMQUERYSUSPENDFAILED || dwEventType == PBT_APMRESUMEAUTOMATIC)
			{
				PostThreadMessage(pi.dwThreadId, WM_POWERBROADCAST, dwEventType, 0);
/*				JNIEnv* env;
				BOOL detachIt = FALSE;
				if (vm->GetEnv((void**)&env, JNI_VERSION_1_2) != JNI_OK)
				{
					detachIt = TRUE;
					vm->AttachCurrentThread((void**)&env, NULL);
				}
				if (deepSleepmid)
				{
					env->CallStaticVoidMethod(sageTVClass, deepSleepmid, dwEventType == PBT_APMSUSPEND);
				}
				env->ExceptionClear();
				if (detachIt)
					vm->DetachCurrentThread();*/
			}
			break;
 
        case SERVICE_CONTROL_INTERROGATE:
			if (SageServiceStatus.dwCurrentState == SERVICE_START_PENDING)
				SageServiceStatus.dwCheckPoint++;
        // Fall through to send current status. 
            break;

        default: 
			rv = ERROR_CALL_NOT_IMPLEMENTED;
            SvcDebugOut(" [SageTV] Unrecognized opcode %ld\n", 
                dwControl); 
    } 
 
    // Send current status. 
    if (!SetServiceStatus (SageServiceStatusHandle,  &SageServiceStatus)) 
    { 
        status = GetLastError(); 
        SvcDebugOut(" [SageTV] SetServiceStatus error %ld\n",status); 
    } 
    return rv;
}
