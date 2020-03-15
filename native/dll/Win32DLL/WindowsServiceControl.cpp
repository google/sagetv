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
//! Need this for CreateWellKnownSid
#include <ntsecapi.h>	// LsaEnumerateAccountRights
#include <sddl.h>		// ConvertSidToStringSid
#include "../../include/sage_WindowsServiceControl.h"

typedef struct
{
	SC_HANDLE schSCManager;
	SC_HANDLE schService;
} WinServiceInfo;

void DisplayLastWin32Error()
{
	LPVOID lpMsgBuf;
	FormatMessage( 
		FORMAT_MESSAGE_ALLOCATE_BUFFER | 
		FORMAT_MESSAGE_FROM_SYSTEM | 
		FORMAT_MESSAGE_IGNORE_INSERTS,
		NULL,
		GetLastError(),
		MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), // Default language
		(LPTSTR) &lpMsgBuf,
		0,
		NULL 
	);
	MessageBox(NULL, (LPCTSTR)lpMsgBuf, "Error", MB_OK);
	// Free the buffer.
	LocalFree( lpMsgBuf );
}

bool CheckSingleUserPriv(char *user, WCHAR *priv, char *dc);
bool SetPrivilegeOnAccount(
    LPTSTR AccountName,          // account name to check on
    LPWSTR PrivilegeName,       // privilege to grant (Unicode)
    BOOL bEnable                // enable or disable
    );

/*
 * Class:     sage_WindowsServiceControl
 * Method:    openServiceHandle0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_WindowsServiceControl_openServiceHandle0
  (JNIEnv *env, jobject jo, jstring jstr)
{
	// Open a handle to the SC Manager database.
	WinServiceInfo* svcInfo = new WinServiceInfo;
	svcInfo->schSCManager = 0;
	svcInfo->schService = 0;
 
	svcInfo->schSCManager = OpenSCManager( 
		NULL,                    // local machine 
		NULL,                    // ServicesActive database 
		DELETE | SERVICE_STOP | SERVICE_QUERY_STATUS | SERVICE_START | SERVICE_CHANGE_CONFIG | SERVICE_QUERY_CONFIG);
 
	if (svcInfo->schSCManager == NULL) 
	{
		MessageBox(NULL, "Cannot OpenSCManager. You need to be logged in as Administrator for perform this task", "ERROR", MB_OK);
		delete svcInfo;
		return 0;
	}
	const char* svcName = env->GetStringUTFChars(jstr, NULL);
	svcInfo->schService = OpenService( 
			svcInfo->schSCManager,       // SCManager database 
			svcName,       // name of service 
			DELETE | SERVICE_STOP | SERVICE_QUERY_STATUS | SERVICE_START | SERVICE_CHANGE_CONFIG | SERVICE_QUERY_CONFIG);
	env->ReleaseStringUTFChars(jstr, svcName);

	if (svcInfo->schService == NULL)
	{
		CloseServiceHandle(svcInfo->schSCManager);
		delete svcInfo;
		return 0;
	}
	return (jlong) svcInfo;
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    closeServiceHandle0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_WindowsServiceControl_closeServiceHandle0
  (JNIEnv *env, jobject jo, jlong svcPtr)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return;
	if (svcInfo->schService)
		CloseServiceHandle(svcInfo->schService);
	if (svcInfo->schSCManager)
		CloseServiceHandle(svcInfo->schSCManager);
	delete svcInfo;
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    isServiceAutostart0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_WindowsServiceControl_isServiceAutostart0
  (JNIEnv *env, jobject jo, jlong svcPtr)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return 0;
	// Check if the service is set to load automatically
	LPQUERY_SERVICE_CONFIG lpqscBuf; 
	lpqscBuf = (LPQUERY_SERVICE_CONFIG) LocalAlloc( 
		LPTR, 1024); 
	if (lpqscBuf == NULL)  // Should not be NULL
		return 0;
	DWORD bytesNeeded;
	jboolean rv =  (QueryServiceConfig(svcInfo->schService, lpqscBuf, 1024, &bytesNeeded) &&
		lpqscBuf->dwStartType == SERVICE_AUTO_START);
	LocalFree(lpqscBuf);
	return rv;
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    installService0
 * Signature: (Ljava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_WindowsServiceControl_installService0
  (JNIEnv *env, jobject jo, jstring jsvcName, jstring jexeName)
{
	// Open a handle to the SC Manager database.
	WinServiceInfo* svcInfo = new WinServiceInfo;
	svcInfo->schSCManager = 0;
	svcInfo->schService = 0;
 
	svcInfo->schSCManager = OpenSCManager( 
		NULL,                    // local machine 
		NULL,                    // ServicesActive database 
		DELETE | SERVICE_STOP | SERVICE_QUERY_STATUS | SC_MANAGER_CREATE_SERVICE | SERVICE_START | SERVICE_CHANGE_CONFIG | SERVICE_QUERY_CONFIG);
 
	if (svcInfo->schSCManager == NULL) 
	{
		MessageBox(NULL, "Cannot OpenSCManager. You need to be logged in as Administrator for perform this task", "ERROR", MB_OK);
		delete svcInfo;
		return 0;
	}
	const char* svcName = env->GetStringUTFChars(jsvcName, NULL);
	const char* svcExe = env->GetStringUTFChars(jexeName, NULL);
	svcInfo->schService = CreateService( 
		svcInfo->schSCManager,              // SCManager database 
		svcName,              // name of service 
		svcName,           // service name to display 
		DELETE | SERVICE_STOP | SERVICE_QUERY_STATUS | SERVICE_START | SERVICE_CHANGE_CONFIG | SERVICE_QUERY_CONFIG,        // desired access 
		SERVICE_WIN32_OWN_PROCESS, // service type 
		SERVICE_AUTO_START,      // start type 
		SERVICE_ERROR_NORMAL,      // error control type 
		svcExe,        // service's binary 
		NULL,                      // no load ordering group 
		NULL,                      // no tag identifier 
		NULL,                      // no dependencies 
		// If we logon as a network service then we only have read access to the C drive which is bad
		NULL,                      // LocalSystem account 
		NULL);                     // no password 
	env->ReleaseStringUTFChars(jsvcName, svcName);
	env->ReleaseStringUTFChars(jexeName, svcExe);
	if (svcInfo->schService == NULL)
	{
		CloseServiceHandle(svcInfo->schSCManager);
		delete svcInfo;
		return 0;
	}
	return (jlong) svcInfo;
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    setServiceAutostart0
 * Signature: (Ljava/lang/String;JZ)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_WindowsServiceControl_setServiceAutostart0
  (JNIEnv *env, jobject jo, jstring jsvcName, jlong svcPtr, jboolean autostart)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return 0;
	const char* svcName = env->GetStringUTFChars(jsvcName, NULL);
	jboolean rv = (jboolean) ChangeServiceConfig(svcInfo->schService, SERVICE_NO_CHANGE, 
		autostart ? SERVICE_AUTO_START : SERVICE_DISABLED, SERVICE_NO_CHANGE,
		NULL, NULL, NULL, NULL, NULL,/*acct*/ NULL/*passwd*/, svcName);
	env->ReleaseStringUTFChars(jsvcName, svcName);
	return rv;
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    startService0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_WindowsServiceControl_startService0
  (JNIEnv *env, jobject jo, jlong svcPtr)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return 0;
	if (StartService(svcInfo->schService, 0, NULL))
		return JNI_TRUE;
	else
	{
		DisplayLastWin32Error();
		return JNI_FALSE;
	}
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    stopService0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_WindowsServiceControl_stopService0
  (JNIEnv *env, jobject jo, jlong svcPtr)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return 0;
	SERVICE_STATUS servStat;
	if (ControlService(svcInfo->schService, SERVICE_CONTROL_STOP, &servStat))
		return JNI_TRUE;
	else
	{
		DisplayLastWin32Error();
		return JNI_FALSE;
	}
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    isServiceRunning0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_WindowsServiceControl_isServiceRunning0
  (JNIEnv *env, jobject jo, jlong svcPtr)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return 0;
	SERVICE_STATUS srvStat;
	return QueryServiceStatus(svcInfo->schService, &srvStat) && 
		srvStat.dwCurrentState == SERVICE_RUNNING;
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    isServiceLoading0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_WindowsServiceControl_isServiceLoading0
  (JNIEnv *env, jobject jo, jlong svcPtr)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return 0;
	SERVICE_STATUS srvStat;
	return QueryServiceStatus(svcInfo->schService, &srvStat) && 
		srvStat.dwCurrentState == SERVICE_START_PENDING;
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    isServiceStopping0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_WindowsServiceControl_isServiceStopping0
  (JNIEnv *env, jobject jo, jlong svcPtr)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return 0;
	SERVICE_STATUS srvStat;
	return QueryServiceStatus(svcInfo->schService, &srvStat) && 
		srvStat.dwCurrentState == SERVICE_STOP_PENDING;
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    getServiceUser0
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sage_WindowsServiceControl_getServiceUser0
  (JNIEnv *env, jobject jo, jlong svcPtr)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return env->NewStringUTF("");
	// Check if the service is set to load automatically
	LPQUERY_SERVICE_CONFIG lpqscBuf; 
	lpqscBuf = (LPQUERY_SERVICE_CONFIG) LocalAlloc( 
		LPTR, 1024); 
	if (lpqscBuf == NULL)  // Should not be NULL
		return 0;
	DWORD bytesNeeded;
	jstring rv;
	if (QueryServiceConfig(svcInfo->schService, lpqscBuf, 1024, &bytesNeeded))
	{
		rv = env->NewStringUTF(lpqscBuf->lpServiceStartName);
	}
	else
		rv = env->NewStringUTF("");
	LocalFree(lpqscBuf);
	return rv;
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    setServiceUser0
 * Signature: (Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_WindowsServiceControl_setServiceUser0
  (JNIEnv *env, jobject jo, jstring jsvcName, jlong ptr, jstring juser, jstring jpass)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) ptr;
	if (!svcInfo) return 0;
	const char* svcName = env->GetStringUTFChars(jsvcName, NULL);
	const char* cuser = env->GetStringUTFChars(juser, NULL);
	const char* cpass = env->GetStringUTFChars(jpass, NULL);
	jboolean rv = ChangeServiceConfig(svcInfo->schService, SERVICE_NO_CHANGE, 
		SERVICE_NO_CHANGE, SERVICE_NO_CHANGE,
		NULL, NULL, NULL, NULL, cuser,/*acct*/ cpass/*passwd*/, svcName);
	if (rv)
	{
		// Check to make sure this account has logon as service rights and if not, then grant them.
		// If we can't grant them, then popup a warning message about not running this as an administrator
		// Strip the .\ from the beginning of the username if it's there
		char* myUser = (char*)cuser;
		if (myUser[0] == '.' && myUser[1] == '\\')
			myUser = &(myUser[2]);
		if (!CheckSingleUserPriv(myUser, L"SeServiceLogonRight", NULL))
		{
			if (!SetPrivilegeOnAccount(myUser, L"SeServiceLogonRight", TRUE))
			{
				// Failure....but there shouldn't be one since we're logged on as administrator or we wouldn't
				// have gotten here.
			}
		}
	}
	env->ReleaseStringUTFChars(jsvcName, svcName);
	env->ReleaseStringUTFChars(juser, cuser);
	env->ReleaseStringUTFChars(jpass, cpass);
	return rv;
}

// We need this to be loaded at runtime or Win98 won't load this DLL
static HINSTANCE svcLib = NULL;
typedef WINADVAPI
BOOL
(WINAPI
*QueryServiceConfig2Fun)(
    SC_HANDLE   hService,
    DWORD       dwInfoLevel,
    LPBYTE      lpBuffer,
    DWORD       cbBufSize,
    LPDWORD     pcbBytesNeeded
    );
static QueryServiceConfig2Fun qsc2 = NULL;
typedef WINADVAPI
BOOL
(WINAPI
*ChangeServiceConfig2Fun)(
    SC_HANDLE    hService,
    DWORD        dwInfoLevel,
    LPVOID       lpInfo
    );
static ChangeServiceConfig2Fun csc2 = NULL;

/*
 * Class:     sage_WindowsServiceControl
 * Method:    isServiceRecovery0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_WindowsServiceControl_isServiceRecovery0
  (JNIEnv *env, jobject jo, jlong svcPtr)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return 0;

	if (!svcLib)
	{
		svcLib = LoadLibrary("Advapi32.dll");
		if (!svcLib) return 0;
 
		// Get the function pointers
#ifdef UNICODE
		qsc2 = (QueryServiceConfig2Fun)GetProcAddress(svcLib, "QueryServiceConfig2W");
		csc2 = (ChangeServiceConfig2Fun)GetProcAddress(svcLib, "ChangeServiceConfig2W");
#else
		qsc2 = (QueryServiceConfig2Fun)GetProcAddress(svcLib, "QueryServiceConfig2A");
		csc2 = (ChangeServiceConfig2Fun)GetProcAddress(svcLib, "ChangeServiceConfig2A");
#endif // !UNICODE

	}
	LPSERVICE_FAILURE_ACTIONS lpqscBuf; 
	lpqscBuf = (LPSERVICE_FAILURE_ACTIONS) LocalAlloc( 
		LPTR, 1024); 
	if (lpqscBuf == NULL)  // Should not be NULL
		return 0;
	DWORD bytesNeeded;
	jboolean rv = JNI_FALSE;
	if (qsc2(svcInfo->schService, SERVICE_CONFIG_FAILURE_ACTIONS,
			(LPBYTE)lpqscBuf, 1024, &bytesNeeded))
	{
		if (lpqscBuf->cActions && lpqscBuf->lpsaActions && lpqscBuf->lpsaActions[0].Type != SC_ACTION_NONE)
		{
			rv = JNI_TRUE;
		}
	}
	LocalFree(lpqscBuf);
	return rv;
}

/*
 * Class:     sage_WindowsServiceControl
 * Method:    setServiceRecovery0
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL Java_sage_WindowsServiceControl_setServiceRecovery0
  (JNIEnv *env, jobject jo, jlong svcPtr, jboolean recoverState)
{
	WinServiceInfo* svcInfo = (WinServiceInfo*) svcPtr;
	if (!svcInfo) return;
	if (!svcLib)
	{
		svcLib = LoadLibrary("Advapi32.dll");
		if (!svcLib) return;
 
		// Get the function pointers
#ifdef UNICODE
		qsc2 = (QueryServiceConfig2Fun)GetProcAddress(svcLib, "QueryServiceConfig2W");
		csc2 = (ChangeServiceConfig2Fun)GetProcAddress(svcLib, "ChangeServiceConfig2W");
#else
		qsc2 = (QueryServiceConfig2Fun)GetProcAddress(svcLib, "QueryServiceConfig2A");
		csc2 = (ChangeServiceConfig2Fun)GetProcAddress(svcLib, "ChangeServiceConfig2A");
#endif // !UNICODE

	}
	SERVICE_FAILURE_ACTIONS failActs;
	SC_ACTION restartAction;
	restartAction.Delay = 60000;
	restartAction.Type = SC_ACTION_RESTART;
	if (recoverState)
	{
		failActs.cActions = 1;
		failActs.dwResetPeriod = 0;
		failActs.lpCommand = NULL;
		failActs.lpRebootMsg = NULL;
		failActs.lpsaActions = &restartAction;
	}
	else
	{
		failActs.cActions = 0;
		failActs.dwResetPeriod = 0;
		failActs.lpCommand = NULL;
		failActs.lpRebootMsg = NULL;
		failActs.lpsaActions = &restartAction;
	}
	if (!csc2(svcInfo->schService, SERVICE_CONFIG_FAILURE_ACTIONS, (LPVOID)&failActs))
	{
		slog((env, "Failed changing service configuration\r\n"));
	}
}

typedef WINADVAPI BOOL (WINAPI *lpfnConvertSidToStringSid)(
    IN  PSID,
    OUT LPTSTR *);
static lpfnConvertSidToStringSid myConvertSidToStringSid = NULL;
typedef WINADVAPI BOOL (WINAPI *lpfnCreateWellKnownSid)(
    IN WELL_KNOWN_SID_TYPE WellKnownSidType,
    IN PSID DomainSid  OPTIONAL,
    OUT PSID pSid,
    IN OUT DWORD *cbSid
    );
static lpfnCreateWellKnownSid myCreateWellKnownSid = NULL;
typedef NTSTATUS (NTAPI *lpfnLsaAddAccountRights)(
    IN LSA_HANDLE PolicyHandle,
    IN PSID AccountSid,
    IN PLSA_UNICODE_STRING UserRights,
    IN ULONG CountOfRights
    );
static lpfnLsaAddAccountRights myLsaAddAccountRights = NULL;
typedef NTSTATUS (NTAPI *lpfnLsaClose)(
    IN LSA_HANDLE ObjectHandle
    );
static lpfnLsaClose myLsaClose = NULL;
typedef NTSTATUS (NTAPI *lpfnLsaEnumerateAccountRights)(
    IN LSA_HANDLE PolicyHandle,
    IN PSID AccountSid,
    OUT PLSA_UNICODE_STRING *UserRights,
    OUT PULONG CountOfRights
    );
static lpfnLsaEnumerateAccountRights myLsaEnumerateAccountRights = NULL;
typedef NTSTATUS (NTAPI *lpfnLsaFreeMemory)(
    IN PVOID Buffer
    );
static lpfnLsaFreeMemory myLsaFreeMemory = NULL;
typedef ULONG (NTAPI *lpfnLsaNtStatusToWinError)(
    NTSTATUS Status
    );
static lpfnLsaNtStatusToWinError myLsaNtStatusToWinError = NULL;
typedef NTSTATUS (NTAPI *lpfnLsaOpenPolicy)(
    IN PLSA_UNICODE_STRING SystemName OPTIONAL,
    IN PLSA_OBJECT_ATTRIBUTES ObjectAttributes,
    IN ACCESS_MASK DesiredAccess,
    IN OUT PLSA_HANDLE PolicyHandle
    );
static lpfnLsaOpenPolicy myLsaOpenPolicy = NULL;

NTSTATUS
OpenPolicy(
    LPWSTR ServerName,          // machine to open policy on (Unicode)
    DWORD DesiredAccess,        // desired access to policy
    PLSA_HANDLE PolicyHandle    // resultant policy handle
    );

BOOL
GetAccountSid(
    LPTSTR SystemName,          // where to lookup account
    LPTSTR AccountName,         // account of interest
    PSID *Sid                   // resultant buffer containing SID
    );

NTSTATUS
SetPrivilegeOnAccount(
    LSA_HANDLE PolicyHandle,    // open policy handle
    PSID AccountSid,            // SID to grant privilege to
    LPWSTR PrivilegeName,       // privilege to grant (Unicode)
    BOOL bEnable                // enable or disable
    );

void
InitLsaString(
    PLSA_UNICODE_STRING LsaString, // destination
    LPWSTR String                  // source (Unicode)
    );

void
DisplayNtStatus(
    LPSTR szAPI,                // pointer to function name (ANSI)
    NTSTATUS Status             // NTSTATUS error value
    );

void
DisplayWinError(
    LPSTR szAPI,                // pointer to function name (ANSI)
    DWORD WinError              // DWORD WinError
    );

#define RTN_OK 0
#define RTN_USAGE 1
#define RTN_ERROR 13

// 
// If you have the ddk, include ntstatus.h.
// 
#ifndef STATUS_SUCCESS
#define STATUS_SUCCESS  ((NTSTATUS)0x00000000L)
#endif


void
InitLsaString(
    PLSA_UNICODE_STRING LsaString,
    LPWSTR String
    )
{
    DWORD StringLength;

    if (String == NULL) {
        LsaString->Buffer = NULL;
        LsaString->Length = 0;
        LsaString->MaximumLength = 0;
        return;
    }

    StringLength = (DWORD) wcslen(String);
    LsaString->Buffer = String;
    LsaString->Length = (USHORT) StringLength * sizeof(WCHAR);
    LsaString->MaximumLength=(USHORT)(StringLength+1) * sizeof(WCHAR);
}

NTSTATUS
OpenPolicy(
    LPWSTR ServerName,
    DWORD DesiredAccess,
    PLSA_HANDLE PolicyHandle
    )
{
    LSA_OBJECT_ATTRIBUTES ObjectAttributes;
    LSA_UNICODE_STRING ServerString;
    PLSA_UNICODE_STRING Server = NULL;

    // 
    // Always initialize the object attributes to all zeroes.
    // 
    ZeroMemory(&ObjectAttributes, sizeof(ObjectAttributes));

    if (ServerName != NULL) {
        // 
        // Make a LSA_UNICODE_STRING out of the LPWSTR passed in
        // 
        InitLsaString(&ServerString, ServerName);
        Server = &ServerString;
    }

    // 
    // Attempt to open the policy.
    // 
    return myLsaOpenPolicy(
                Server,
                &ObjectAttributes,
                DesiredAccess,
                PolicyHandle
                );
}

/*++
This function attempts to obtain a SID representing the supplied
account on the supplied system.

If the function succeeds, the return value is TRUE. A buffer is
allocated which contains the SID representing the supplied account.
This buffer should be freed when it is no longer needed by calling
HeapFree(GetProcessHeap(), 0, buffer)

If the function fails, the return value is FALSE. Call GetLastError()
to obtain extended error information.

Scott Field (sfield)    12-Jul-95
--*/ 

BOOL
GetAccountSid(
    LPTSTR SystemName,
    LPTSTR AccountName,
    PSID *Sid
    )
{
    LPTSTR ReferencedDomain=NULL;
    DWORD cbSid=128;    // initial allocation attempt
    DWORD cchReferencedDomain=16; // initial allocation size
    SID_NAME_USE peUse;
    BOOL bSuccess=FALSE; // assume this function will fail

    __try {

    // 
    // initial memory allocations
    // 
    if((*Sid=HeapAlloc(
                    GetProcessHeap(),
                    0,
                    cbSid
                    )) == NULL) __leave;

    if((ReferencedDomain=(LPTSTR)HeapAlloc(
                    GetProcessHeap(),
                    0,
                    cchReferencedDomain * sizeof(TCHAR)
                    )) == NULL) __leave;

    // 
    // Obtain the SID of the specified account on the specified system.
    // 
    while(!LookupAccountName(
                    SystemName,         // machine to lookup account on
                    AccountName,        // account to lookup
                    *Sid,               // SID of interest
                    &cbSid,             // size of SID
                    ReferencedDomain,   // domain account was found on
                    &cchReferencedDomain,
                    &peUse
                    )) {
        if (GetLastError() == ERROR_INSUFFICIENT_BUFFER) {
            // 
            // reallocate memory
            // 
            PSID tmpSid = NULL;
            if ((tmpSid = HeapReAlloc(GetProcessHeap(), 0, *Sid, cbSid)) == NULL)
            {
                __leave;
            }
            else
            {
            	*Sid = tmpSid;
            }

            LPTSTR tmpReferencedDomain = NULL;
            if ((tmpReferencedDomain = (LPTSTR)HeapReAlloc(GetProcessHeap(), 0, ReferencedDomain, cchReferencedDomain * sizeof(TCHAR))) == NULL)
            {
                __leave;
            }
            else
            {
                ReferencedDomain = tmpReferencedDomain;
            }
        }
        else __leave;
    }

    // 
    // Indicate success.
    // 
    bSuccess=TRUE;

    } // finally
    __finally {

    // 
    // Cleanup and indicate failure, if appropriate.
    // 

    if(ReferencedDomain != NULL) {
        HeapFree(GetProcessHeap(), 0, ReferencedDomain);
        ReferencedDomain = NULL;
    }

    if(!bSuccess) {
        if(*Sid != NULL) {
            HeapFree(GetProcessHeap(), 0, *Sid);
            *Sid = NULL;
        }
    }

    } // finally

    return bSuccess;
}

bool
SetPrivilegeOnAccount(
    LPTSTR AccountName,          // account name to check on
    LPWSTR PrivilegeName,       // privilege to grant (Unicode)
    BOOL bEnable                // enable or disable
    )
{
	NTSTATUS  res = 0;
	LSA_HANDLE policy_handle = NULL;
	PSID account_sid = NULL;
   // 
    // Open the policy on the target machine.
    // 
    if((res=OpenPolicy(
                NULL,      // target machine
                POLICY_CREATE_ACCOUNT | POLICY_LOOKUP_NAMES,
                &policy_handle       // resultant policy handle
                )) != STATUS_SUCCESS) {
        DisplayNtStatus("OpenPolicy", res);
        return false;
    }

	if (policy_handle == NULL){

		printf("..Failed.\nGetPolicyHandle() failed\r\n");
        return false;
	}

	if (!GetAccountSid(NULL, AccountName, &account_sid))
	{
		DisplayLastWin32Error();
		if (policy_handle != NULL){
			myLsaClose(policy_handle);
		}
		return false;
 	}

	bool rv = false;
	if (SetPrivilegeOnAccount(policy_handle, account_sid, L"SeServiceLogonRight", TRUE) == STATUS_SUCCESS)
		rv = true;

	if (policy_handle != NULL){
		myLsaClose(policy_handle);
	}

	if (account_sid != NULL){
		HeapFree(GetProcessHeap(), 0, account_sid);
	}

	return rv;
}

NTSTATUS
SetPrivilegeOnAccount(
    LSA_HANDLE PolicyHandle,    // open policy handle
    PSID AccountSid,            // SID to grant privilege to
    LPWSTR PrivilegeName,       // privilege to grant (Unicode)
    BOOL bEnable                // enable or disable
    )
{
    LSA_UNICODE_STRING PrivilegeString;

    // 
    // Create a LSA_UNICODE_STRING for the privilege name.
    // 
    InitLsaString(&PrivilegeString, PrivilegeName);

    // 
    // grant or revoke the privilege, accordingly
    // 
	// THIS IS ALWAYS TRUE IN OUR CASE
    return myLsaAddAccountRights(
            PolicyHandle,       // open policy handle
            AccountSid,         // target SID
            &PrivilegeString,   // privileges
            1                   // privilege count
            );
}

void
DisplayNtStatus(
    LPSTR szAPI,
    NTSTATUS Status
    )
{
    // 
    // Convert the NTSTATUS to Winerror. Then call DisplayWinError().
    // 
    DisplayWinError(szAPI, myLsaNtStatusToWinError(Status));
}

void
DisplayWinError(
    LPSTR szAPI,
    DWORD WinError
    )
{
    LPSTR MessageBuffer;
    DWORD dwBufferLength;

    // 
    // TODO: Get this fprintf out of here!
    // 
    fprintf(stderr,"%s error!\n", szAPI);

    if(dwBufferLength=FormatMessageA(
                        FORMAT_MESSAGE_ALLOCATE_BUFFER |
                        FORMAT_MESSAGE_FROM_SYSTEM,
                        NULL,
                        WinError,
                        GetUserDefaultLangID(),
                        (LPSTR) &MessageBuffer,
                        0,
                        NULL
                        ))
    {
        DWORD dwBytesWritten; // unused

        // 
        // Output message string on stderr.
        // 
        WriteFile(
            GetStdHandle(STD_ERROR_HANDLE),
            MessageBuffer,
            dwBufferLength,
            &dwBytesWritten,
            NULL
            );

        // 
        // Free the buffer allocated by the system.
        // 
        LocalFree(MessageBuffer);
    }
}
				
/*!
 *	\breif Checks to see if the user has the correct privilages
 *	\param user the username to check
 *	\param priv the privilage to check
 *	\param dc (default to NULL to check local machine) else the dc of the machine to check
 *	\returns true if the user has all the right privs else false
 */
bool CheckSingleUserPriv(char *user, WCHAR *priv, char *dc)
{
    WCHAR wComputerName[256]=L"";   // static machine name buffer

	PSID account_sid = NULL;
	PLSA_UNICODE_STRING user_rights = NULL;
	ULONG num_rights = 0;

    LPTSTR referenced_domain = NULL;
    DWORD cb_sid = 0;				// zero so we can get the needed size
    DWORD cch_referenced_domain = 0;	// zero so we can get the needed size

    BOOL bSuccess = FALSE; // assume this function will fail
	DWORD dwResult = 0;

	NTSTATUS  res = 0;
	LSA_HANDLE policy_handle = NULL;

	LPWSTR u_dc = NULL;
	LPWSTR u_user = NULL;

	bool found_priv = false;

	DWORD dwRet = 0;
	LPTSTR pszDNS = NULL;
	char lwszSysErr[512];
	DWORD dw_syserr = sizeof(lwszSysErr);


	unsigned int i = 0;
	bool rc = false;

	if (!svcLib)
	{
		svcLib = LoadLibrary("Advapi32.dll");
		if (!svcLib) return false;
	}
	if (!myConvertSidToStringSid)
	{
		// Get the function pointers
#ifdef UNICODE
		myConvertSidToStringSid = (lpfnConvertSidToStringSid)GetProcAddress(svcLib, "ConvertSidToStringSidW");
#else
		myConvertSidToStringSid = (lpfnConvertSidToStringSid)GetProcAddress(svcLib, "ConvertSidToStringSidA");
#endif // !UNICODE
		myCreateWellKnownSid = (lpfnCreateWellKnownSid)GetProcAddress(svcLib, "CreateWellKnownSid");
		myLsaAddAccountRights = (lpfnLsaAddAccountRights)GetProcAddress(svcLib, "LsaAddAccountRights");
		myLsaClose = (lpfnLsaClose)GetProcAddress(svcLib, "LsaClose");
		myLsaEnumerateAccountRights = (lpfnLsaEnumerateAccountRights)GetProcAddress(svcLib, "LsaEnumerateAccountRights");
		myLsaFreeMemory = (lpfnLsaFreeMemory)GetProcAddress(svcLib, "LsaFreeMemory");
		myLsaNtStatusToWinError = (lpfnLsaNtStatusToWinError)GetProcAddress(svcLib, "LsaNtStatusToWinError");
		myLsaOpenPolicy = (lpfnLsaOpenPolicy)GetProcAddress(svcLib, "LsaOpenPolicy");
		if (!myConvertSidToStringSid || !myCreateWellKnownSid || !myLsaAddAccountRights || !myLsaClose || !myLsaEnumerateAccountRights ||
			!myLsaNtStatusToWinError || !myLsaOpenPolicy)
			return false;
	}

   // 
    // Open the policy on the target machine.
    // 
    if((res=OpenPolicy(
                wComputerName,      // target machine
                POLICY_CREATE_ACCOUNT | POLICY_LOOKUP_NAMES,
                &policy_handle       // resultant policy handle
                )) != STATUS_SUCCESS) {
        DisplayNtStatus("OpenPolicy", res);
        return false;
    }

	if (policy_handle == NULL){

		printf("..Failed.\nGetPolicyHandle() failed\r\n");
		goto LBL_CLEANUP;
	}

	if (!GetAccountSid(NULL, user, &account_sid))
	{
		DisplayLastWin32Error();
		return false;
 	}

	res = myLsaEnumerateAccountRights( policy_handle, account_sid, &user_rights, &num_rights);

	if (res != STATUS_SUCCESS){

		dwRet = FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
								NULL,  myLsaNtStatusToWinError(res), MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
								(LPTSTR) lwszSysErr, dw_syserr, NULL);

		printf("..Failed. %s\r\n", lwszSysErr);

		rc = false;

		goto LBL_CLEANUP;
	}

	for (i = 0 ; i <num_rights; i++){

		if ( wcscmp(user_rights[i].Buffer, priv) == 0){
			// Found it
			found_priv = true;
		}
	}

	if (user_rights != NULL){
		myLsaFreeMemory(user_rights);
		user_rights = NULL;
	}

	if (!found_priv){

		DWORD sid_size;
		PSID well_know_sid;
		LPTSTR sid_str;

		sid_size = SECURITY_MAX_SID_SIZE;

		// Allocate enough memory for the largest possible SID.
		if(!(well_know_sid = LocalAlloc(LMEM_FIXED, sid_size))){

			//	fprintf(stderr, "Could not allocate memory.\n");
		}
		// Create a SID for the Everyone group on the local computer.
		if(!myCreateWellKnownSid(WinWorldSid, NULL, well_know_sid, &sid_size)){

			dwRet = GetLastError();

			dwRet = FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
				NULL,  dwRet, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
				(LPTSTR) lwszSysErr, dw_syserr, NULL);
			printf("..Failed. %s\r\n", lwszSysErr);

			goto LBL_CLEANUP;

		} else {

			// Get the string version of the SID (S-1-1-0).
			if(!(myConvertSidToStringSid(well_know_sid, &sid_str))){

				dwRet = GetLastError();

				dwRet = FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
					NULL,  dwRet, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
					(LPTSTR) lwszSysErr, dw_syserr, NULL);

				printf("..Failed. %s\r\n", lwszSysErr);

				goto LBL_CLEANUP;

			}


			res = myLsaEnumerateAccountRights( policy_handle, well_know_sid, &user_rights, &num_rights);

			if (res != STATUS_SUCCESS){


				dwRet = FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
					NULL,  myLsaNtStatusToWinError(res), MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
					(LPTSTR) lwszSysErr, dw_syserr, NULL);

				printf("..Failed. %s\r\n", lwszSysErr);

				goto LBL_CLEANUP;
			}


			for (i = 0 ; i < num_rights; i++){

				if ( wcscmp(user_rights[i].Buffer, priv) == 0){
					// Found it
					found_priv = true;
				}
			}

		}

		if (sid_str){
			LocalFree(sid_str);
		}

		if(well_know_sid){
			LocalFree(well_know_sid);
		}

	}


	rc = (found_priv == true);

LBL_CLEANUP:

	if (policy_handle != NULL){
		myLsaClose(policy_handle);
	}

	if (user_rights != NULL){
		myLsaFreeMemory(user_rights);
	}
	if (account_sid != NULL){
		HeapFree(GetProcessHeap(), 0, account_sid);
	}

    if (referenced_domain != NULL){
		HeapFree(GetProcessHeap(), 0, referenced_domain);
	}

	return rc;
}
