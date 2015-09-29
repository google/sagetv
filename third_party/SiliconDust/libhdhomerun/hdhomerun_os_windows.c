/*
 * hdhomerun_os_windows.c
 *
 * Copyright © 2006-2010 Silicondust USA Inc. <www.silicondust.com>.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

#include "hdhomerun_os.h"

static DWORD random_get32_context_tls = TlsAlloc();

uint32_t random_get32(void)
{
	HCRYPTPROV *phProv = (HCRYPTPROV *)TlsGetValue(random_get32_context_tls);
	if (!phProv) {
		phProv = (HCRYPTPROV *)calloc(1, sizeof(HCRYPTPROV));
		CryptAcquireContext(phProv, 0, 0, PROV_RSA_FULL, CRYPT_VERIFYCONTEXT);
		TlsSetValue(random_get32_context_tls, phProv);
	}

	uint32_t Result;
	if (!CryptGenRandom(*phProv, sizeof(Result), (BYTE *)&Result)) {
		return (uint32_t)getcurrenttime();
	}

	return Result;
}

uint64_t getcurrenttime(void)
{
	return GetTickCount64();
}

void msleep_approx(uint64_t ms)
{
	Sleep((DWORD)ms);
}

void msleep_minimum(uint64_t ms)
{
	uint64_t stop_time = getcurrenttime() + ms;

	while (1) {
		uint64_t current_time = getcurrenttime();
		if (current_time >= stop_time) {
			return;
		}

		msleep_approx(stop_time - current_time);
	}
}

int pthread_create(pthread_t *tid, void *attr, LPTHREAD_START_ROUTINE start, void *arg)
{
	*tid = CreateThread(NULL, 0, start, arg, 0, NULL);
	if (!*tid) {
		return (int)GetLastError();
	}
	return 0;
}

int pthread_join(pthread_t tid, void **value_ptr)
{
	while (1) {
		DWORD ExitCode = 0;
		if (!GetExitCodeThread(tid, &ExitCode)) {
			return (int)GetLastError();
		}
		if (ExitCode != STILL_ACTIVE) {
			return 0;
		}
	}
}

void pthread_mutex_init(pthread_mutex_t *mutex, void *attr)
{
	*mutex = CreateMutex(NULL, FALSE, NULL);
}

void pthread_mutex_lock(pthread_mutex_t *mutex)
{
	WaitForSingleObject(*mutex, INFINITE);
}

void pthread_mutex_unlock(pthread_mutex_t *mutex)
{
	ReleaseMutex(*mutex);
}

bool_t hdhomerun_vsprintf(char *buffer, char *end, const char *fmt, va_list ap)
{
	if (buffer >= end) {
		return FALSE;
	}

	int length = _vsnprintf(buffer, end - buffer - 1, fmt, ap);
	if (length < 0) {
		*buffer = 0;
		return FALSE;
	}

	if (buffer + length + 1 > end) {
		*(end - 1) = 0;
		return FALSE;

	}

	return TRUE;
}

bool_t hdhomerun_sprintf(char *buffer, char *end, const char *fmt, ...)
{
	va_list ap;
	va_start(ap, fmt);
	bool_t result = hdhomerun_vsprintf(buffer, end, fmt, ap);
	va_end(ap);
	return result;
}

/*
 * The console output format should be set to UTF-8, however in XP and Vista this breaks batch file processing.
 * Attempting to restore on exit fails to restore if the program is terminated by the user.
 * Solution - set the output format each printf.
 */
void console_vprintf(const char *fmt, va_list ap)
{
	UINT cp = GetConsoleOutputCP();
	SetConsoleOutputCP(CP_UTF8);
	vprintf(fmt, ap);
	SetConsoleOutputCP(cp);
}

void console_printf(const char *fmt, ...)
{
	va_list ap;
	va_start(ap, fmt);
	console_vprintf(fmt, ap);
	va_end(ap);
}
