/*
 * Buffered file io for ffmpeg system
 * Copyright (c) 2001 Fabrice Bellard
 *
 * This file is part of FFmpeg.
 *
 * FFmpeg is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * FFmpeg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with FFmpeg; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
#include "avformat.h"
#include <fcntl.h>
#ifndef __MINGW32__
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/time.h>
#else
#include <io.h>
#define open(fname,oflag,pmode) _open(fname,oflag,pmode)
#define wopen(fname,oflag,pmode) _wopen(fname,oflag,pmode)
#endif /* __MINGW32__ */


/* standard file protocol */

static int file_open(URLContext *h, const char *filename, int flags)
{
    int access;
    int fd;

    strstart(filename, "file:", &filename);

    if (flags & URL_RDWR) {
        access = O_CREAT | O_TRUNC | O_RDWR;
    } else if (flags & URL_WRONLY) {
        access = O_CREAT | O_TRUNC | O_WRONLY;
    } else {
        access = O_RDONLY;
    }
#if defined(__MINGW32__) || defined(CONFIG_OS2) || defined(__CYGWIN__)
    access |= O_BINARY;
#endif
#ifdef __MINGW32__
	// Check for UTF-8 unicode pathname
	int strl = strlen(filename);
	wchar_t* wfilename = av_malloc(sizeof(wchar_t) * (1 + strl));
	int wpos = 0;
	int i = 0;
	for (i = 0; i < strl; i++)
	{
		wfilename[wpos] = 0;
		if ((filename[i] & 0x80) == 0)
		{
			// ASCII character
			wfilename[wpos++] = filename[i];
		}
		else if (i + 1 < strl && ((filename[i] & 0xE0) == 0xC0) && ((filename[i + 1] & 0xC0) == 0x80))
		{
			// two octets for this character
			wfilename[wpos++] = ((filename[i] & 0x1F) << 6) + (filename[i + 1] & 0x3F);
			i++;
		}
		else if (i + 2 < strl && ((filename[i] & 0xF0) == 0xE0) && ((filename[i + 1] & 0xC0) == 0x80) && 
			((filename[i + 2] & 0xC0) == 0x80))
		{
			// three octets for this character
			wfilename[wpos++] = ((filename[i] & 0x0F) << 12) + ((filename[i + 1] & 0x3F) << 6) + (filename[i + 2] & 0x3F);
			i+=2;
		}
		else
			wfilename[wpos++] = filename[i];
	}
	wfilename[wpos] = 0;
	fd = wopen(wfilename, access, 0666);
	av_free(wfilename);
#else
    fd = open(filename, access, 0666);
#endif
    if (fd < 0)
        return AVERROR(ENOENT);
    h->priv_data = (void *)(size_t)fd;
    return 0;
}

static int file_read(URLContext *h, unsigned char *buf, int size)
{
    int fd = (size_t)h->priv_data;
    int rv;
	do
	{
		rv = read(fd, buf, size);
		if (rv <= 0 && ((h->flags & URL_ACTIVEFILE) == URL_ACTIVEFILE))
		{
			usleep(20000);
	        if (url_interrupt_cb())
	            return -EINTR;
 		}
		else
			break;
	} while (1);
	return rv;
}

static int file_write(URLContext *h, unsigned char *buf, int size)
{
    int fd = (size_t)h->priv_data;
    return write(fd, buf, size);
}

/* XXX: use llseek */
static offset_t file_seek(URLContext *h, offset_t pos, int whence)
{
    int fd = (size_t)h->priv_data;
    return lseek(fd, pos, whence);
}

static int file_close(URLContext *h)
{
    int fd = (size_t)h->priv_data;
    return close(fd);
}

URLProtocol file_protocol = {
    "file",
    file_open,
    file_read,
    file_write,
    file_seek,
    file_close,
};

/* pipe protocol */

static int pipe_open(URLContext *h, const char *filename, int flags)
{
    int fd;

    if (flags & URL_WRONLY) {
        fd = 1;
    } else {
        fd = 0;
    }
#if defined(__MINGW32__) || defined(CONFIG_OS2) || defined(__CYGWIN__)
    setmode(fd, O_BINARY);
#endif
    h->priv_data = (void *)(size_t)fd;
    h->is_streamed = 1;
    return 0;
}

static int pipe_read(URLContext *h, unsigned char *buf, int size)
{
    int fd = (size_t)h->priv_data;
    return read(fd, buf, size);
}

static int pipe_write(URLContext *h, unsigned char *buf, int size)
{
    int fd = (size_t)h->priv_data;
    return write(fd, buf, size);
}

static int pipe_close(URLContext *h)
{
    return 0;
}

URLProtocol pipe_protocol = {
    "pipe",
    pipe_open,
    pipe_read,
    pipe_write,
    NULL,
    pipe_close,
};
