/*
 * hdhomerun_os_posix.c
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

#if defined(__APPLE__)
#include <mach/clock.h>
#include <mach/mach.h>
#endif

static pthread_once_t random_get32_once = PTHREAD_ONCE_INIT;
static FILE *random_get32_fp = NULL;

static void random_get32_init(void)
{
	random_get32_fp = fopen("/dev/urandom", "rb");
}

uint32_t random_get32(void)
{
	pthread_once(&random_get32_once, random_get32_init);

	if (!random_get32_fp) {
		return (uint32_t)getcurrenttime();
	}

	uint32_t Result;
	if (fread(&Result, 4, 1, random_get32_fp) != 1) {
		return (uint32_t)getcurrenttime();
	}

	return Result;
}

uint64_t getcurrenttime(void)
{
#if defined(CLOCK_MONOTONIC)
	struct timespec t;
	clock_gettime(CLOCK_MONOTONIC, &t);
	return ((uint64_t)t.tv_sec * 1000) + (t.tv_nsec / 1000000);
#elif defined(__APPLE__)
	clock_serv_t clock_serv;
	host_get_clock_service(mach_host_self(), SYSTEM_CLOCK, &clock_serv);

	struct mach_timespec t;
	clock_get_time(clock_serv, &t);

	mach_port_deallocate(mach_task_self(), clock_serv);
	return ((uint64_t)t.tv_sec * 1000) + (t.tv_nsec / 1000000);
#else
#error no clock source for getcurrenttime()
#endif
}

void msleep_approx(uint64_t ms)
{
	unsigned int delay_s = ms / 1000;
	if (delay_s > 0) {
		sleep(delay_s);
		ms -= delay_s * 1000;
	}

	unsigned int delay_us = ms * 1000;
	if (delay_us > 0) {
		usleep(delay_us);
	}
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

bool_t hdhomerun_vsprintf(char *buffer, char *end, const char *fmt, va_list ap)
{
	if (buffer >= end) {
		return FALSE;
	}

	int length = vsnprintf(buffer, end - buffer - 1, fmt, ap);
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
