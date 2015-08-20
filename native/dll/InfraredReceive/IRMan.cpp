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
#include "SageTVInfraredReceive.h"
#include "../../include/sage_SageTVInfraredReceive.h"

static void PowerOff(HANDLE port);
static void PowerOn(HANDLE port);
static void WriteData(HANDLE port,BYTE data);
static BOOL ReadData(HANDLE port,LPBYTE buffer,DWORD size,BOOL blocking);

static OVERLAPPED Rov,Wov;

static BOOL killIRManThread = FALSE;

/*
 * Class:     sage_SageTVInfraredReceive
 * Method:    irmanPortInit0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_SageTVInfraredReceive_irmanPortInit0
	(JNIEnv *env, jobject jo, jstring jPortName)
{
	const char* portName = env->GetStringUTFChars(jPortName, (unsigned char*) NULL);
	slog((env, "Opening IR port %s\r\n", portName));
	DCB comState;
	DCB newComState;
	BYTE data[2];
	HANDLE comPort;
	Rov.Internal=Rov.InternalHigh=Rov.Offset=Rov.OffsetHigh=0;
	Rov.hEvent=INVALID_HANDLE_VALUE;

	// create event for overlapped I/O
	Rov.hEvent = CreateEvent(NULL,FALSE,FALSE,NULL);
	if (Rov.hEvent == INVALID_HANDLE_VALUE)
		return 0;

	Wov.Internal=Rov.InternalHigh=Rov.Offset=Rov.OffsetHigh=0;
	Wov.hEvent=INVALID_HANDLE_VALUE;
	// create event for overlapped I/O
	Wov.hEvent = CreateEvent(NULL,FALSE,FALSE,NULL);
	if (Wov.hEvent == INVALID_HANDLE_VALUE)
		return 0;

	comPort = CreateFile(portName,GENERIC_READ | GENERIC_WRITE,0,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL|FILE_FLAG_OVERLAPPED,NULL);
	env->ReleaseStringUTFChars(jPortName, portName);
		
	if (comPort != INVALID_HANDLE_VALUE)
	{
		if (GetCommState(comPort,&comState))
		{
			newComState=comState;
			BuildCommDCB("9600,n,8,1", &newComState);
			newComState.fDtrControl = DTR_CONTROL_DISABLE;
			newComState.fRtsControl = RTS_CONTROL_DISABLE;

			if (SetCommState(comPort,&newComState))
			{
				PowerOff(comPort);
				Sleep(250);
				PowerOn(comPort);
				Sleep(250);
				PurgeComm(comPort,PURGE_TXABORT|PURGE_RXABORT|PURGE_TXCLEAR|PURGE_RXCLEAR);

				Sleep(2);
				WriteData(comPort,'I');
				Sleep(2);
				WriteData(comPort,'R');

				Sleep(100); // let's wait for the microcontrol's response

				if (ReadData(comPort,data,2,FALSE))
				{
					if ((data[0] != 'O') || (data[1] != 'K'))
					{
						elog((env, "1Could not initialize IR-device. Make sure you set the COM-port correct.\r\n"));
						SetCommState(comPort,&comState);
						PowerOff(comPort);
						CloseHandle(comPort);
						comPort = INVALID_HANDLE_VALUE;
					}
				}
				else
				{
					elog((env, "1Could not initialize IR-device. Make sure you set the COM-port correct.\r\n"));
					PowerOff(comPort);
					if (!SetCommState(comPort,&comState))
						slog((env, "Restoring old state with SetCommState failed\r\n"));
					else {
						CloseHandle(comPort);
					}
					comPort = INVALID_HANDLE_VALUE;
				}
			}
			else
			{
				slog((env, "Could not open comport: SetCommState returned an Error\r\n"));
				PowerOff(comPort);
				CloseHandle(comPort);
				comPort = INVALID_HANDLE_VALUE;
			}
		}
		else
		{
			slog((env, "Could not open comport: GetCommState returned an Error\r\n"));
			PowerOff(comPort);
			CloseHandle(comPort);
			comPort = INVALID_HANDLE_VALUE;
		}
	}
	else 
		slog((env, "3Could not initialize IR-device.\nMake sure you set the COM-port correct.\n After that, press Apply to try initialization again.\n\nOnce initialized correctly you can setup your keys.\r\n"));
		switch (GetLastError()) 
			{
				case 2: slog((env, "Could not open comport : port does not exist.\nMake sure the port is enabled in the BIOS and exists in\nControl Panel -> System -> Ports.\r\n")); break;
				case 5: slog((env, "Could not open comport : Windows denied access.\nMake sure you set the COM-port correct.\nAfter that, press Apply to try initialization again.\n\nOnce initialized correctly you can setup your keys.\r\n")); break;
			}
	if (comPort==INVALID_HANDLE_VALUE)
	{
		slog((env, "4Could not initialize IR-device.\nMake sure you set the COM-port correct.\n After that, press Apply to try initialization again.\n\nOnce initialized correctly you can setup your keys.\r\n"));
		comPort = 0;
	}

	killIRManThread = FALSE;
	return (jlong) comPort;
}

/*
 * Class:     sage_SageTVInfraredReceive
 * Method:    closeIRManPort0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_SageTVInfraredReceive_closeIRManPort0
	(JNIEnv *env, jobject jo, jlong jPortHandle)
{
slog((env, "About to close IR COM handle %d.\r\n", jPortHandle));
	killIRManThread = TRUE;
	Sleep(50);
	PowerOff((HANDLE) jPortHandle);
	CloseHandle((HANDLE) jPortHandle);
	Sleep(250);
slog((env, "Closed IR COM handle.\r\n"));
}

/*
 * Class:     sage_SageTVInfraredReceive
 * Method:    irmanPortThread0
 * Signature: (Lsage/SageTVInputCallback;)V
 */
JNIEXPORT void JNICALL Java_sage_SageTVInfraredReceive_irmanPortThread0
	(JNIEnv *env, jobject jo, jobject router, jlong portHandle)
{
	BYTE keyCode[6];
	HANDLE comPort;
	static DWORD read;
	static int DataRead,ReadRequested=0;
	slog((env, "IR Native Thread is running...\r\n"));
	jclass rtrClass = env->GetObjectClass(router);
	jmethodID irMeth = env->GetMethodID(rtrClass, "recvInfrared", "([B)V");
	for (;;)
	{
		if (killIRManThread) break;

		comPort = (HANDLE) portHandle;

		// Get data from UIR
		if(!ReadRequested)
		{
			DataRead=0;  //Data from serial port was not already requested: Do it now. ;-)
			if(ReadFile(comPort,&keyCode,6,&read,&Rov))
			{
				if(read!=6) slog((env, "Size mismatch.\r\n"));
				else DataRead=(read==6); //Data requested and read. Very well.
			}
			else if(GetLastError()==ERROR_IO_PENDING) ReadRequested=1; //Data requested, but not ready. We'll have to wait.
		}
		else if(GetOverlappedResult(comPort,&Rov,&read,FALSE)) {ReadRequested=0; DataRead=(read==6);} //Hey, data is finally ready!
		

		// Process data if any
		if(DataRead)
		{
			jbyteArray ja = env->NewByteArray(6);
			jbyte jb[6];
			for (int i = 0; i < 6; i++) jb[i] = keyCode[i];
			env->SetByteArrayRegion(ja, 0, 6, jb);
			env->CallVoidMethod(router, irMeth, ja);
			continue;
		};

		Sleep(25); //Just in order to save processor time.
	}
	slog((env, "IR Native Thread is TERMINATING.\r\n"));
}

static void PowerOff(HANDLE port)
{
	EscapeCommFunction(port, CLRDTR);
	EscapeCommFunction(port, CLRRTS);
}

static void PowerOn(HANDLE port)
{
	EscapeCommFunction(port, SETDTR);
	EscapeCommFunction(port, SETRTS);
}

static void WriteData(HANDLE port,BYTE data)
{
	DWORD write;
	int x;
    
	if (!WriteFile(port,&data,1,&write,&Wov)) {
		x=GetLastError();
		if (x==ERROR_IO_PENDING) {
			while (!GetOverlappedResult(port,&Wov,&write,TRUE)) // wait until write completes!
			{
               if(GetLastError() != ERROR_IO_INCOMPLETE) // "incomplete" is normal result if not finished
               {	// an error occurred, try to recover
//		   		   MessageBox(plugin.hwndParent,"Something went wrong when calling Windows to write to comport.","Message",MB_ICONEXCLAMATION|MB_OK);
				   break;
               }
			}
		}
		else {
//			MessageBox(plugin.hwndParent,"Something went wrong when calling Windows to write to comport.","Message",MB_ICONEXCLAMATION|MB_OK);
//			CancelIo(port);
		}
	}

	Sleep(10); // let's give the microcontrol some processing time...
}

static BOOL ReadData(HANDLE port,LPBYTE buffer,DWORD size,BOOL blocking)
{
	DWORD read = 0;
	int x;

	if (ReadFile(port,buffer,size,&read,&Rov))
	{
		if (read!=size)
		{
//			char xx[100];
//			wsprintf(xx,"Size mismatch %d %d",buffer[0],buffer[1]);
//			MessageBox(plugin.hwndParent,xx,"Error",MB_ICONEXCLAMATION|MB_OK);
		}
		return (read == size);
	}
	else
	{
		switch (x=GetLastError())
		{
		case ERROR_IO_PENDING:
			if (blocking)
			{
				while (!GetOverlappedResult(port,&Rov,&read,TRUE)) // wait until read completes!
				{
					if(GetLastError() != ERROR_IO_INCOMPLETE)	// "incomplete" is normal result if not finished
					{
//						MessageBox(plugin.hwndParent,"Something went wrong when calling Windows to read from comport.","Message",MB_ICONEXCLAMATION|MB_OK);
						break;	// an error occurred, try to recover
					}
				}
				return (read == size);
			}
			else 
				return FALSE;

		default:
			return FALSE;
		}
	}
}
