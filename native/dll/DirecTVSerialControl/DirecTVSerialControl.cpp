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
// DirecTVSerialControl.cpp : Defines the entry point for the DLL application.
//
#include "StdAfx.h"

#ifdef WIN32
#include "DirecTVSerialControl.h"
#else
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include <string.h>

#endif

#if defined(__APPLE__)
 #include "sage_DirecTVSerialControl.h"
 #include "MacSerial.h"
 #include <stdlib.h>
 #include <Carbon/Carbon.h>
 #include <sys/fcntl.h>
 #include <sys/ioctl.h>
#else
 #include "../../include/sage_DirecTVSerialControl.h"
#endif //defined(__APPLE__)

#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE hModule, 
                       DWORD  ul_reason_for_call, 
                       LPVOID lpReserved
					 )
{
    switch (ul_reason_for_call)
	{
		case DLL_PROCESS_ATTACH:
		case DLL_THREAD_ATTACH:
		case DLL_THREAD_DETACH:
		case DLL_PROCESS_DETACH:
			break;
    }
    return TRUE;
}
#endif

#ifdef WIN32
//#define LOG_ALL_SERIAL_DATA
#ifdef LOG_ALL_SERIAL_DATA
BOOL WriteAndLog(JNIEnv* env,
    HANDLE hFile,
    LPCVOID lpBuffer,
    DWORD nNumberOfBytesToWrite,
    LPDWORD lpNumberOfBytesWritten,
    LPOVERLAPPED lpOverlapped
    )
{
	char foo[512];
	char tempHex[16];
	sprintf(foo, "Writing => ");
	for (DWORD i = 0; i < nNumberOfBytesToWrite; i++)
	{
		sprintf(tempHex, " 0x%x", ((unsigned char*)lpBuffer)[i]);
		strcat(foo, tempHex);
	}
	elog((env, "%s\r\n", foo));
	return WriteFile(hFile, lpBuffer, nNumberOfBytesToWrite, lpNumberOfBytesWritten, lpOverlapped);
}

BOOL ReadAndLog(JNIEnv* env,
    HANDLE hFile,
    LPVOID lpBuffer,
    DWORD nNumberOfBytesToRead,
    LPDWORD lpNumberOfBytesRead,
    LPOVERLAPPED lpOverlapped
    )
{
	BOOL rv = ReadFile(hFile, lpBuffer, nNumberOfBytesToRead, lpNumberOfBytesRead, lpOverlapped);
	char foo[512];
	char tempHex[16];
	sprintf(foo, "Reading <= ");
	for (DWORD i = 0; i < *lpNumberOfBytesRead; i++)
	{
		sprintf(tempHex, " 0x%x", ((unsigned char*)lpBuffer)[i]);
		strcat(foo, tempHex);
	}
	elog((env, "%s\r\n", foo));
	return rv;
}

#define COM_WRITE(a, b, c, d, e) WriteAndLog(env, (HANDLE)a, b, c, (LPDWORD)&d, e)
#define COM_READ(a, b, c, d, e) ReadAndLog(env, (HANDLE)a, b, c, (LPDWORD)&d, e)
#else
#define COM_WRITE(a, b, c, d, e) WriteFile((HANDLE)a, b, c, (LPDWORD)&d, e)
#define COM_READ(a, b, c, d, e) ReadFile((HANDLE)a, b, c, (LPDWORD)&d, e)
#endif
#else

#if defined(__APPLE__)
	// we use non-blocking I/O on Mac OS X, spin until we get something from the port with a reasonable timeout
	// non-blocking write is safe since we don't use flow control
static int nonblock_read(int fd, void *buf, size_t count)
{
	fd_set fds;
	int rv;
	struct timeval waitTime = {0,0};
	ssize_t result = -1, readCount = 0, tmp;
	
	for(;;) {
		FD_ZERO(&fds);
		FD_SET(fd, &fds);
		
		// wait up to 1 second for a response, maybe tweak this later...
		// FIXME: is that a long enough wait time????
		waitTime.tv_sec = 1;
		
		rv = select(fd+1, &fds, NULL, NULL, &waitTime); // rv < 0: error, == 0: timeout, > 0: # ready descriptors
//		fprintf(stderr, "nonblock_read: select returned %d\n", rv);
		if (rv < 0) {
			if ((errno == EINTR) || (errno == EAGAIN))
				continue;
			return -1; // real error...
		} else if (rv > 0) {
			// data are ready, read it in
			tmp = read(fd, buf, count);
			if (tmp < 0) {
				if ((errno == EINTR) || (errno == EAGAIN))
					continue;
				return -1;
			} else if (tmp == 0) { // only happens on EOF
				return -1;
			} else { // > 0
				readCount += tmp;
				if (readCount >= count)
					return readCount;
				
				// not the expected amount of data, try again at least once more
				count -= readCount;
				buf = (char*)buf + readCount;
			}
		} else {
			// timed out, return what we have accumulated
			if (readCount)
				return readCount;
			return -1;
		}
	}
}

static int nonblock_write(int fd, void *buf, size_t count)
{
	fd_set fds;
	int n, ret = -1;
	struct timeval waitTime = {0,0};
	
	ret = write(fd, buf, count);
	
	// wait for data to be sent
	FD_ZERO(&fds);
	FD_SET(fd, &fds);
	waitTime.tv_sec = 5;
	n = select(fd+1, NULL, &fds, NULL, &waitTime);
//	fprintf(stderr, "nonblock_write: select returned %d, ret = %d\n", n, ret);
	
	return ret;
}

#define COM_READ(a,b,c,d,e) d=nonblock_read(a,b,c)
#define COM_WRITE(a,b,c,d,e) d=nonblock_write(a,b,c)
#else
#define COM_READ(a, b, c, d, e) d=(int)read(a, b, c)
#define COM_WRITE(a, b, c, d, e) d=(int)write(a, b, c)
#endif // defined(__APPLE__)
#endif

#define NUM_CMD_STYLE_SLOTS 16
static int initdCmdData = 0;
static int dtvCmdStyle[NUM_CMD_STYLE_SLOTS];
static jint channelBase[NUM_CMD_STYLE_SLOTS];
static jlong portHandles[NUM_CMD_STYLE_SLOTS];
static int confirmedChannelBase[NUM_CMD_STYLE_SLOTS];

/*
 * Class:     sage_DirecTVSerialControl
 * Method:    openDTVSerial0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_DirecTVSerialControl_openDTVSerial0
  (JNIEnv *env, jobject jo, jstring jcomstr)
{
	int i = 0;
	if (!initdCmdData)
	{
		initdCmdData = 1;
		for (i = 0; i < NUM_CMD_STYLE_SLOTS; i++)
		{
			dtvCmdStyle[i] = -1;
			portHandles[i] = 0;
			channelBase[i] = 0xE0;
			confirmedChannelBase[i] = 0;
		}
	}
	const char* portName = env->GetStringUTFChars(jcomstr, (jboolean *) NULL);
	jlong comHandle = 0;
#ifdef WIN32
	HANDLE com = CreateFile(portName,GENERIC_READ | GENERIC_WRITE, 0, NULL, OPEN_EXISTING, 0, NULL);

	if (com == INVALID_HANDLE_VALUE)
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
		// Free the buffer.
		elog((env, "Could not open %s error=%s\r\n", portName, lpMsgBuf));
		env->ReleaseStringUTFChars(jcomstr, portName);
		LocalFree( lpMsgBuf );
		return -1;
	}

	if (!SetCommMask( com, EV_RXCHAR | EV_TXEMPTY))
	{
		elog((env, "Could not set COM mask on %s\r\n", portName));
		CloseHandle(com);
		env->ReleaseStringUTFChars(jcomstr, portName);
		return -1;
	}

	DCB settings;

	if (!GetCommState( com, &settings))
	{
		elog((env, "Could not get COM device settings on %s\r\n", portName));
		CloseHandle(com);
		env->ReleaseStringUTFChars(jcomstr, portName);
		return -1;
	}

	HKEY rootKey = HKEY_LOCAL_MACHINE;
	DWORD baudRate = 115200;
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(baudRate);
#endif
	int forcedBaud = 0;
	int forcedCmdStyle = -1;
#ifdef WIN32
	if (RegOpenKeyEx(rootKey, "SOFTWARE\\Frey Technologies\\SageTV\\", 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "DirecTVSerialBaudRate", 0, &readType, (LPBYTE) &baudRate, &hsize) == ERROR_SUCCESS)
		{
			slog((env, "DTVSerial using baud rate %d\r\n", baudRate));
			forcedBaud = TRUE;
		}
		if (RegQueryValueEx(myKey, "DirecTVCmdSet", 0, &readType, (LPBYTE) &forcedCmdStyle, &hsize) == ERROR_SUCCESS)
		{
			slog((env, "DTVSerial using command set %d\r\n", forcedCmdStyle));
		}
		RegCloseKey(myKey);
	}

	// D11 DirecTV receivers use 115k baud when using a usb-serial converter with it
	// First we attempt to use a 115k connection on the serial port (unless the registry has it set otherwise). If that fails
	// the test of the GetCommandVersion then we re-open the port at 9600.  Then we do a GetChannel test to find the old
	// vs. new command version. When doing setchannel itself we can detect which remote code base is used. We should also
	// do the digit-by-digit entry for all of the satellite boxes (I don't think it breaks any of them AFAIK).
	char baudString[128];
	sprintf(baudString, "baud=%d parity=N data=8 stop=1", baudRate);
	if (!BuildCommDCB( baudString, &settings ))
	{
		elog((env, "Could not build COM device settings on %s\r\n", portName));
		CloseHandle(com);
		env->ReleaseStringUTFChars(jcomstr, portName);
		return -1;
	}

	if (!SetCommState(com, &settings))
	{
		elog((env, "Could not apply COM device settings on %s\r\n", portName));
		CloseHandle(com);
		env->ReleaseStringUTFChars(jcomstr, portName);
		return -1;
	}

	// JK - I cut these in half because we're detecting stuff with timeouts at the start.
	COMMTIMEOUTS timeout;
	timeout.ReadIntervalTimeout = 1000;
	timeout.ReadTotalTimeoutMultiplier = 500;
	timeout.ReadTotalTimeoutConstant = 1000;
	timeout.WriteTotalTimeoutMultiplier = 500;
	timeout.WriteTotalTimeoutConstant = 1000;
	if (!SetCommTimeouts( com, &timeout))
	{
		elog((env, "Could not set timeouts on COM device: %s\r\n", portName));
		CloseHandle(com);
		env->ReleaseStringUTFChars(jcomstr, portName);
		return -1;
	}
	comHandle = (jlong)com;
#else // WIN32
#if defined(__APPLE__)
	// free(theName) when done
	char *theName = GetMacDTVPort(portName);
	if(!theName) {
		elog((env, "Port %s is not configured for use with DirecTV!\r\n", portName));
		env->ReleaseStringUTFChars(jcomstr, portName);
		return -1;
	}
	forcedBaud = GetMacDTVPortBaudRate(portName); // 0 == autodetect
	// FIXME: implement forcedCmdStyle too!!
	// FIXME: also implement UI controls for other DTVSERIAL_XXX settings...
	
	slog((env, "DTVSerial port %s mapped to serial device %s (speed %d)\r\n", portName, theName, forcedBaud));
		// O_NONBLOCK required or open will block until CD goes active (not always good!)
	comHandle = open(theName, O_RDWR | O_NONBLOCK);
	if (comHandle == -1) {
		elog((env, "Could not open %s\n", theName));
		env->ReleaseStringUTFChars(jcomstr, portName);
		return -1;
	}
	
	// lock for exclusive access
	if (ioctl(comHandle, TIOCEXCL) == -1) {
		close(comHandle);
		elog((env, "Error setting TIOCEXCL on %s\n", theName));
		env->ReleaseStringUTFChars(jcomstr, portName);
		return -1;
	}
	
	free(theName);
#else
	// Serial ports are zero-based on Linux
	char theName[32];
	sprintf(theName, "/dev/ttyS%c", (portName + 3)[0] - 1);
	comHandle = open(theName, O_RDWR | O_NOCTTY);
	FILE* fdbaudtest = fopen("DTVSERIAL_9600", "r");
	if (fdbaudtest)
	{
		forcedBaud = 9600;
		fclose(fdbaudtest);
		fdbaudtest = 0;
	}
#endif
	
	if (comHandle < 0)
	{
		elog((env, "Could not nopen %s\r\n", theName));
		env->ReleaseStringUTFChars(jcomstr, portName);
		return -1;
	}

	struct termios tio;
    memset(&tio, 0, sizeof(tio));
    
	// FIXME: should be able to specify explicit baud rate
#if defined(__APPLE__)
	memset(&tio, 0, sizeof(struct termios));
	cfmakeraw(&tio);
	cfsetspeed(&tio, (forcedBaud == 9600) ? B9600 : B115200);
#else
    tio.c_cflag = (forcedBaud == 9600 ? B9600 : B115200);
#endif
	tio.c_cflag |= CS8 | CLOCAL | CREAD | HUPCL;
    tio.c_iflag = IGNPAR | IGNBRK;
    tio.c_oflag = 0;
    tio.c_lflag = 0;
    tio.c_cc[VTIME]=5;
    tio.c_cc[VMIN]=0; /* Blocking for 1 character */
    tcflush((int)comHandle, TCIFLUSH);
    tcsetattr((int)comHandle, TCSANOW, &tio);
	
#if 0
	{
		int num;
#define TEST_STRING "Testing... Testing... ONE.. TWO... THREE!!!\r\n"
		COM_WRITE(comHandle, (void*)TEST_STRING, strlen(TEST_STRING), num, NULL);
		fprintf(stderr, "Test COM_WRITE sent %d bytes\n", num);
	}
#endif

#endif
	slog((env, "DTVSerial opened handle on port %s\r\n", portName));

	unsigned char cmd[8];
	int num = 0;
	if (!forcedBaud)
	{
		slog((env, "DTVSerial testing to see if baud rate is 115200...\r\n"));
		cmd[0] = 0xFA;//HEADER;
		cmd[1] = 0x84;//get command version;
		COM_WRITE(comHandle, cmd, 2, num, NULL);
		int baudIsBad = 0;
		if (num != 2)
		{
			// We had a failure writing to the serial port, it's not 115200
			baudIsBad = 1;
		}
		else
		{
			// We should get back 6 bytes on this. (0xF0, then 4 bytes for version and 0xF4)
			num = 0;
			COM_READ(comHandle, cmd, 6, num, NULL);
			slog((env, "DTVSerial response num=%d cmd[0]=0x%x cmd[1]=0x%x cmd[2]=0x%x cmd[3]=0x%x cmd[4]=0x%x cmd[5]=0x%x\r\n", num, cmd[0],
				cmd[1], cmd[2], cmd[3], cmd[4], cmd[5]));
			if (num > 1 && cmd[0] == 0xF0)
			{
				// We don't care about the info, just that the command worked
			}
			else
				baudIsBad = 1;
		}
		if (baudIsBad)
		{
			slog((env, "DTVSerial failed at 115200, reverting to 9600 baud.\r\n"));
			// Reset the port to be at 9600
#ifdef WIN32
			if (!BuildCommDCB( "baud=9600 parity=N data=8 stop=1", &settings ))
			{
				elog((env, "Could not build COM device settings on %s\r\n", portName));
				CloseHandle((HANDLE)comHandle);
				env->ReleaseStringUTFChars(jcomstr, portName);
				return -1;
			}

			if (!SetCommState((HANDLE)comHandle, &settings))
			{
				elog((env, "Could not apply COM device settings on %s\r\n", portName));
				CloseHandle((HANDLE)comHandle);
				env->ReleaseStringUTFChars(jcomstr, portName);
				return -1;
			}
#else
#if defined(__APPLE__)
			memset(&tio, 0, sizeof(struct termios));
			cfmakeraw(&tio);
			cfsetspeed(&tio, B9600);
#else
			tio.c_cflag = B9600;
#endif
			tio.c_cflag |= CS8 | CLOCAL | CREAD | HUPCL;
			tio.c_iflag = IGNPAR | IGNBRK;
			tio.c_oflag = 0;
   			tio.c_lflag = 0;

/*			tio.c_cflag = B9600 | CRTSCTS | CS8 | CLOCAL | CREAD;
			tio.c_iflag = IGNPAR;
			tio.c_oflag = 0;
			tio.c_lflag = 0;*/
			tio.c_cc[VTIME]=10;
			tio.c_cc[VMIN]=0; /* Blocking for 1 character */
			tcflush((int)comHandle, TCIFLUSH);
			tcsetattr((int)comHandle, TCSANOW, &tio);
#endif
		}
	}

	env->ReleaseStringUTFChars(jcomstr, portName);

	int cmdStyleIndex = 0;
	for (i = 0; i < NUM_CMD_STYLE_SLOTS; i++)
	{
		if (portHandles[i] == 0)
		{
			cmdStyleIndex = i;
			portHandles[i] = comHandle;
			break;
		}
	}
#ifndef WIN32
    FILE* fdtest = fopen("DTVSERIAL_NEWCMD", "r");
	if (fdtest)
	{
		forcedCmdStyle = 1;
		fclose(fdtest);
		fdtest = 0;
	}
    fdtest = fopen("DTVSERIAL_OLDCMD", "r");
	if (fdtest)
	{
		forcedCmdStyle = 0;
		fclose(fdtest);
	}
#endif
	if (forcedCmdStyle >= 0)
	{
		dtvCmdStyle[cmdStyleIndex] = forcedCmdStyle;
	}
	else
	{
		// Now we determine whether or not the old or new command set is used. We do this by doing a get channel with the STB.
		cmd[0] = 0xFA;
		cmd[1] = 0x87; // new command set
		num = 0;
		COM_WRITE(comHandle, cmd, 2, num, NULL);
		if (num != 2)
			slog((env, "DTVSerial failed writing, num=%d\r\n", num));
		num = 0;
		COM_READ(comHandle, cmd, 1, num, NULL);
slog((env, "DTVSerialX num=%d cmd[0]=0x%x.\r\n", num, cmd[0]));
		if (num == 1 && cmd[0] == 0xF0)
		{
			// we have the right command set
			dtvCmdStyle[cmdStyleIndex] = 1;
			// Read back the rest of the data....apparently this requires a parameter on some receivers so
			// if we got the 0xF0 response and nothing more after that we send another byte along to poke the receiver
			// and once it's in-line again we move on. But we only try this so many times.
			int numTries = 8;
			do
			{
				num = 0;
				numTries--;
				COM_READ(comHandle, cmd, 1, num, NULL);
				slog((env, "DTVSerial new command set was detected num=%d cmd[0]=0x%x.\r\n", num, cmd[0]));
				if (num == 0)
				{
					cmd[0] = 0;
					COM_WRITE(comHandle, cmd, 1, num, NULL);
					slog((env, "DTVSerial wrote out a junk param\r\n"));
				}
				else if (cmd[0] == 0xF2)
				{
					// It's got the right number of parameters now. Read back the rest of the data.
					COM_READ(comHandle, cmd, 7, num, NULL);
					break;
				}
			} while (num == 1 && numTries > 0);
		}
		else if (num == 1 && cmd[0] == 0xF1)
		{
			// Doesn't recognize this command, it's the old command set!
			slog((env, "DTVSerial old command set was detected\r\n"));
			dtvCmdStyle[cmdStyleIndex] = 0;
		}
		else if (num == 0)
		{
			// Poke the receiver in case it wants more data for some reason to get a response out of it.
			dtvCmdStyle[cmdStyleIndex] = 0;
			int numTries = 8;
			do
			{
				num = 0;
				numTries--;
				COM_READ(comHandle, cmd, 1, num, NULL);
				slog((env, "DTVSerail read back 0x%x\r\n", cmd[0]));
				if (num == 0)
				{
					cmd[0] = 0;
					COM_WRITE(comHandle, cmd, 1, num, NULL);
					slog((env, "DTVSerial wrote out a junk param\r\n"));
				}
				else if (cmd[0] == 0xF2)
				{
					// It's got the right number of parameters now. Read back the rest of the data.
					COM_READ(comHandle, cmd, 7, num, NULL);
					break;
				}
				else if (cmd[0] == 0xF0)
				{
					// It took the command and is therefore the new command set.
					dtvCmdStyle[cmdStyleIndex] = 1;
					slog((env, "DTVSerial new command set was detected num=%d cmd[0]=0x%x.\r\n", num, cmd[0]));
					break;
				}
			} while (num == 1 && numTries > 0);
			if (!dtvCmdStyle[cmdStyleIndex])
			{
				slog((env, "DTVSerial old command set was detected num=%d cmd[0]=0x%x\r\n", num, cmd[0]));
			}
		}
	}
	return comHandle;
}

/*
 * Class:     sage_DirecTVSerialControl
 * Method:    closeHandle0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_DirecTVSerialControl_closeHandle0
  (JNIEnv *env, jobject jo, jlong jhand)
{
	int i = 0;
	for (i = 0; i < NUM_CMD_STYLE_SLOTS; i++)
	{
		if (portHandles[i] == jhand)
		{
			portHandles[i] = 0;
			dtvCmdStyle[i] = -1;
			channelBase[i] = 0xE0;
			confirmedChannelBase[i] = 0;
			break;
		}
	}
#ifdef WIN32
	CloseHandle((HANDLE) jhand);
#else
	close(jhand);
#endif
}

/*
 * Class:     sage_DirecTVSerialControl
 * Method:    dtvSerialChannel0
 * Signature: (JI)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirecTVSerialControl_dtvSerialChannel0
  (JNIEnv *env, jobject jo, jlong comHandle, jint channel)
{
	int cmdStyleIndex = 0;
	int i = 0;
	for (i = 0; i < NUM_CMD_STYLE_SLOTS; i++)
	{
		if (portHandles[i] == comHandle)
		{
			cmdStyleIndex = i;
			break;
		}
	}
	unsigned short chan = (unsigned short) channel;
	unsigned char cmd[16];
	cmd[0] = 0xFA;//HEADER;
	cmd[1] = 0xA6;//SET_CHANNEL;
	cmd[2] = (chan>>8)&0xFF;
	cmd[3] = chan&0xFF;
	cmd[4] = 0xFF;
	cmd[5] = 0xFF;
	cmd[6] = 0x0D;

	int cmdLen = 7;
	unsigned char resp;
	int num;

	// Only the newer receivers have the issue with sending the channels a single digit at a time to work right...I think.
	int useIndividKeys = dtvCmdStyle[cmdStyleIndex];
	int ignoreResponses = 0;
#ifdef WIN32
	HKEY rootKey = HKEY_LOCAL_MACHINE;
	HKEY myKey;
	DWORD readType;
	DWORD regData = 0;
	DWORD hsize = sizeof(regData);
	if (RegOpenKeyEx(rootKey, "SOFTWARE\\Frey Technologies\\SageTV\\", 0, KEY_QUERY_VALUE, &myKey) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "DirecTVSerialFastChannelChanges", 0, &readType, (LPBYTE) &regData, &hsize) == ERROR_SUCCESS)
		{
			if (regData & 1)
			{
				slog((env, "DTVSerial using fast channel changes\r\n"));
				useIndividKeys = 0;
			}
			if (regData & 2)
			{
				slog((env, "DTVSerial ignoring replies for wait\r\n"));
				ignoreResponses = 1;
			}
			if (regData & 4)
			{
				slog((env, "DTVSerial not sending the extra 0x0d byte\r\n"));
				cmdLen = 6;
			}
		}
		RegCloseKey(myKey);
	}
#else
        FILE* fdtest = fopen("DTVSERIAL_FAST", "r");
	if (fdtest)
	{
		useIndividKeys = 0;
		fclose(fdtest);
	}
#endif
	if (useIndividKeys)
	{
		// Fix for the <100 channels not working on the D10
		cmdLen = 5;
		cmd[1] = (dtvCmdStyle[cmdStyleIndex] == 0) ? 0x45 : 0xA5;
		cmd[2] = 0;
		cmd[3] = 0;
		int chanBase = channelBase[cmdStyleIndex];
		for (i = 0; i < 3; i++)
		{
			if (chanBase == 0xE0)
			{
				if (i == 0)
					cmd[4] = chanBase + (chan / 100);
				else if (i == 1)
					cmd[4] = chanBase + ((chan % 100) / 10);
				else // if (i == 2)
					cmd[4] = chanBase + (chan % 10);
			}
			else
			{
				if (i == 0)
					cmd[4] = chanBase + (9 - (chan / 100));
				else if (i == 1)
					cmd[4] = chanBase + (9 - ((chan % 100) / 10));
				else // if (i == 2)
					cmd[4] = chanBase + (9 - (chan % 10));
			}
			COM_WRITE(comHandle, cmd, 2/*cmdLen*/, num, NULL );
			if (num != 2/*cmdLen*/)
			{
				elog((env, "Incomplete COM Write on handle %d\r\n", comHandle));
				return JNI_FALSE;
			}

			num = 1;
			while (num == 1)
			{
				COM_READ(comHandle, &resp, 1, num, NULL);
				if (num != 1)
				{
					elog((env, "Incomplete COM Read on handle %d\r\n", comHandle));
					if (dtvCmdStyle[cmdStyleIndex] == -1)
					{
						slog((env, "DTVSerial trying older version for handle %d\r\n", comHandle));
						i--;
						dtvCmdStyle[cmdStyleIndex] = 1;
						break;
					}
					return JNI_FALSE;
				}
				if (resp == 0xF0)
				{
					slog((env, "DTVSerial Valid Command Recognized for handle %d\r\n", comHandle));
					COM_WRITE(comHandle, cmd + 2, cmdLen - 2, num, NULL );
					if (num != cmdLen - 2)
					{
						elog((env, "Incomplete COM Write on handle %d\r\n", comHandle));
						return JNI_FALSE;
					}
					num = 1; // otherwise we'll break out of the loop!
				}
				else if (resp == 0xF1)
				{
					slog((env, "DTVSerial Invalid Command for handle %d\r\n", comHandle));
					return JNI_FALSE;
				}
				else if (resp == 0xF2)
				{
					slog((env, "DTVSerial Set Top is Processing Request for handle %d\r\n", comHandle));
				}
				else if ( resp == 0xF4 )
				{
					if (dtvCmdStyle[cmdStyleIndex] == -1)
					{
						dtvCmdStyle[cmdStyleIndex] = 0;
					}
					if (!confirmedChannelBase[cmdStyleIndex])
						confirmedChannelBase[cmdStyleIndex] = 1;
					slog((env, "DTVSerial Command Successful for handle %d\r\n", comHandle));
					break;
				}
				else if (resp == 0xF5 && chanBase == 0xE0 && !confirmedChannelBase[cmdStyleIndex])
				{
					slog((env, "DTVSerial got a command error, switching to other base for handle\r\n", comHandle));
					// for Sony
					chanBase = 0xC6;
					channelBase[cmdStyleIndex] = 0xC6;
					i--;
					break;
				}
				else if (resp == 0xF5)
				{
					slog((env, "DTVSerial got a command NACK; the tune probably worked but the receiver can't access the channel\r\n"));
					break;
				}
				else if (resp == 0xFB)
				{
					slog((env, "DTVSerial illegal char (clearing it) for handle %d\r\n", comHandle));
				}
				else
				{
					elog((env, "DTVSerial Unknown Command Response on handle %d of 0x%x\r\n", comHandle, resp));
					return JNI_FALSE;
				}
			}
		}
		slog((env, "DTVSerial channel change worked\r\n"));
		return JNI_TRUE;
	}


	if (dtvCmdStyle[cmdStyleIndex] == 0)
	{
		cmdLen = 4;
		cmd[1] = 0x46;
	}

	COM_WRITE(comHandle, cmd, (ignoreResponses ? cmdLen : 2), num, NULL );
	if ((!ignoreResponses && num != 2) || (ignoreResponses && num != cmdLen))
	{
		elog((env, "Incomplete COM Write on handle %d\r\n", comHandle));
		return JNI_FALSE;
	}
	num = 1;
	int pokesLeft = 5;
	while (num == 1)
	{
		COM_READ(comHandle, &resp, 1, num, NULL);
		if (num != 1)
		{
			if (pokesLeft-- > 0)
			{
				elog((env, "Incomplete COM Read on handle %d, poking the receiver...\r\n", comHandle));
				cmd[0] = 0xFF;
				COM_WRITE(comHandle, cmd, 1, num, 0); 
			}
			else
			{
				elog((env, "Incomplete COM Read on handle %d\r\n", comHandle));
				return JNI_FALSE;
			}
		}
		if (resp == 0xF0)
		{
			slog((env, "DTVSerial Valid Command Recognized for handle %d\r\n", comHandle));
			if (!ignoreResponses)
			{
				COM_WRITE(comHandle, cmd + 2, cmdLen - 2, num, NULL );
				if (num != cmdLen - 2)
				{
					elog((env, "Incomplete COM Write on handle %d\r\n", comHandle));
					return JNI_FALSE;
				}
			}
			num = 1; // otherwise we'll break out of the loop!
		}
		else if (resp == 0xF1)
		{
			slog((env, "DTVSerial Invalid Command for handle %d, trying older version\r\n", comHandle));
			break;
		}
		else if (resp == 0xF2)
		{
			slog((env, "DTVSerial Set Top is Processing Request for handle %d\r\n", comHandle));
		}
		else if ( resp == 0xF4 )
		{
			slog((env, "DTVSerial Command Successful for handle %d\r\n", comHandle));
			return JNI_TRUE;
		}
		else if (resp == 0xFB)
		{
			slog((env, "DTVSerial illegal char (clearing it) for handle %d\r\n", comHandle));
		}
		else
		{
			elog((env, "DTVSerial Unknown Command Response on handle %d of 0x%x\r\n", comHandle, resp));
			return JNI_FALSE;
		}
	}

	elog((env, "DTVSerial Failed Command-Response on handle %d of 0x%x\r\n", comHandle, resp));
	return JNI_FALSE;
}
