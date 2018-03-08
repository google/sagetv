/*
 * hdhomerun_os_posix.h
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

#define _FILE_OFFSET_BITS 64
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/wait.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <poll.h>
#include <netdb.h>
#include <pthread.h>

typedef void (*sig_t)(int);
typedef void (*thread_task_func_t)(void *arg);
typedef pthread_t thread_task_t;
typedef pthread_mutex_t thread_mutex_t;

typedef struct {
	volatile bool signaled;
	pthread_mutex_t lock;
	pthread_cond_t cond;
} thread_cond_t;

#define LIBHDHOMERUN_API

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
