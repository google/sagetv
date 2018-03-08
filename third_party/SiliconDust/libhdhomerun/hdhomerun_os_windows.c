/*
 * hdhomerun_os_windows.c
 *
 * Copyright © 2006-2017 Silicondust USA Inc. <www.silicondust.com>.
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

#include "hdhomerun.h"

#if defined(_WINRT)
uint32_t random_get32(void)
{
	return (uint32_t)getcurrenttime();
}
#else
uint32_t random_get32(void)
{
	static DWORD random_get32_context_tls = 0xFFFFFFFF;
	if (random_get32_context_tls == 0xFFFFFFFF) {
		random_get32_context_tls = TlsAlloc();
	}

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
#endif

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

struct thread_task_execute_args_t {
	thread_task_func_t func;
	void *arg;
};

static DWORD WINAPI thread_task_execute(void *arg)
{
	struct thread_task_execute_args_t *execute_args = (struct thread_task_execute_args_t *)arg;
	execute_args->func(execute_args->arg);
	free(execute_args);
	return 0;
}

bool thread_task_create(thread_task_t *tid, thread_task_func_t func, void *arg)
{
	struct thread_task_execute_args_t *execute_args = (struct thread_task_execute_args_t *)malloc(sizeof(struct thread_task_execute_args_t));
	if (!execute_args) {
		return false;
	}

	execute_args->func = func;
	execute_args->arg = arg;

	*tid = CreateThread(NULL, 0, thread_task_execute, execute_args, 0, NULL);
	if (!*tid) {
		free(execute_args);
		return false;
	}

	return true;
}

void thread_task_join(thread_task_t tid)
{
	while (1) {
		DWORD ExitCode = 0;
		if (!GetExitCodeThread(tid, &ExitCode)) {
			return;
		}
		if (ExitCode != STILL_ACTIVE) {
			return;
		}
	}
}

void thread_mutex_init(thread_mutex_t *mutex)
{
	*mutex = CreateMutex(NULL, false, NULL);
}

void thread_mutex_dispose(thread_mutex_t *mutex)
{
	CloseHandle(*mutex);
}

void thread_mutex_lock(thread_mutex_t *mutex)
{
	WaitForSingleObject(*mutex, INFINITE);
}

void thread_mutex_unlock(thread_mutex_t *mutex)
{
	ReleaseMutex(*mutex);
}

void thread_cond_init(thread_cond_t *cond)
{
	*cond = CreateEvent(NULL, false, false, NULL);
}

void thread_cond_dispose(thread_cond_t *cond)
{
	CloseHandle(*cond);
}

void thread_cond_signal(thread_cond_t *cond)
{
	SetEvent(*cond);
}

void thread_cond_wait(thread_cond_t *cond)
{
	WaitForSingleObject(*cond, INFINITE);
}

void thread_cond_wait_with_timeout(thread_cond_t *cond, uint64_t max_wait_time)
{
	WaitForSingleObject(*cond, (DWORD)max_wait_time);
}

bool hdhomerun_vsprintf(char *buffer, char *end, const char *fmt, va_list ap)
{
	if (buffer >= end) {
		return false;
	}

	int length = _vsnprintf(buffer, end - buffer - 1, fmt, ap);
	if (length < 0) {
		*buffer = 0;
		return false;
	}

	if (buffer + length + 1 > end) {
		*(end - 1) = 0;
		return false;

	}

	return true;
}

bool hdhomerun_sprintf(char *buffer, char *end, const char *fmt, ...)
{
	va_list ap;
	va_start(ap, fmt);
	bool result = hdhomerun_vsprintf(buffer, end, fmt, ap);
	va_end(ap);
	return result;
}
