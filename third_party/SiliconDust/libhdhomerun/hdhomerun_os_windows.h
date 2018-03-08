/*
 * hdhomerun_os_windows.h
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

#ifdef _WINRT
#include <SDKDDKVer.h>
#endif

#ifndef _WIN32_WINNT
#define _WIN32_WINNT _WIN32_WINNT_VISTA
#endif
#ifndef _CRT_SECURE_NO_WARNINGS
#define _CRT_SECURE_NO_WARNINGS
#endif
#ifndef _WINSOCK_DEPRECATED_NO_WARNINGS
#define _WINSOCK_DEPRECATED_NO_WARNINGS
#endif

#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>
#include <wspiapi.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <signal.h>
#include <time.h>
#include <sys/types.h>

#ifdef LIBHDHOMERUN_DLLEXPORT
#define LIBHDHOMERUN_API __declspec(dllexport)
#endif
#ifdef LIBHDHOMERUN_DLLIMPORT
#define LIBHDHOMERUN_API __declspec(dllimport)
#endif
#ifndef LIBHDHOMERUN_API
#define LIBHDHOMERUN_API
#endif

typedef void (*sig_t)(int);
typedef void (*thread_task_func_t)(void *arg);
typedef HANDLE thread_task_t;
typedef HANDLE thread_mutex_t;
typedef HANDLE thread_cond_t;

#if !defined(va_copy)
#define va_copy(x, y) x = y
#endif

#define atoll _atoi64
#define strdup _strdup
#define strcasecmp _stricmp
#define strncasecmp _strnicmp
#define fseeko _fseeki64
#define ftello _ftelli64

#ifdef __cplusplus
extern "C" {
#endif

extern LIBHDHOMERUN_API uint32_t random_get32(void);
extern LIBHDHOMERUN_API uint64_t getcurrenttime(void);
extern LIBHDHOMERUN_API void msleep_approx(uint64_t ms);
extern LIBHDHOMERUN_API void msleep_minimum(uint64_t ms);

extern LIBHDHOMERUN_API bool thread_task_create(thread_task_t *tid, thread_task_func_t func, void *arg);
extern LIBHDHOMERUN_API void thread_task_join(thread_task_t tid);

extern LIBHDHOMERUN_API void thread_mutex_init(thread_mutex_t *mutex);
extern LIBHDHOMERUN_API void thread_mutex_dispose(thread_mutex_t *mutex);
extern LIBHDHOMERUN_API void thread_mutex_lock(thread_mutex_t *mutex);
extern LIBHDHOMERUN_API void thread_mutex_unlock(thread_mutex_t *mutex);

extern LIBHDHOMERUN_API void thread_cond_init(thread_cond_t *cond);
extern LIBHDHOMERUN_API void thread_cond_dispose(thread_cond_t *cond);
extern LIBHDHOMERUN_API void thread_cond_signal(thread_cond_t *cond);
extern LIBHDHOMERUN_API void thread_cond_wait(thread_cond_t *cond);
extern LIBHDHOMERUN_API void thread_cond_wait_with_timeout(thread_cond_t *cond, uint64_t max_wait_time);

extern LIBHDHOMERUN_API bool hdhomerun_vsprintf(char *buffer, char *end, const char *fmt, va_list ap);
extern LIBHDHOMERUN_API bool hdhomerun_sprintf(char *buffer, char *end, const char *fmt, ...);

#ifdef __cplusplus
}
#endif
