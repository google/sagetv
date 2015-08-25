/*
 * hdhomerun_os_posix.h
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

#define _FILE_OFFSET_BITS 64
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/timeb.h>
#include <sys/wait.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <poll.h>
#include <netdb.h>
#include <pthread.h>

typedef int bool_t;
typedef void (*sig_t)(int);

#define LIBTYPE
#define console_vprintf vprintf
#define console_printf printf
#define THREAD_FUNC_PREFIX void *

#ifdef __cplusplus
extern "C" {
#endif

extern LIBTYPE uint32_t random_get32(void);
extern LIBTYPE uint64_t getcurrenttime(void);
extern LIBTYPE void msleep_approx(uint64_t ms);
extern LIBTYPE void msleep_minimum(uint64_t ms);

extern LIBTYPE bool_t hdhomerun_vsprintf(char *buffer, char *end, const char *fmt, va_list ap);
extern LIBTYPE bool_t hdhomerun_sprintf(char *buffer, char *end, const char *fmt, ...);

#ifdef __cplusplus
}
#endif
