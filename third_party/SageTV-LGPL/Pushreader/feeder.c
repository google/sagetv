/*
 *    Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 *   This library is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 *
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *   Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
#include "libavutil/avstring.h"
#include "avformat.h"
#include <fcntl.h>
#if HAVE_SETMODE || defined(__MINGW32__)
#include <io.h>
#endif
#include <unistd.h>
#include <sys/time.h>
#include <stdlib.h>
#include "os_support.h"

#include <avformat.h>

static int feeder_open(URLContext *h, const char *filename, int flags)
{
    int access;
    int fd;
    char *filename_ptr;

    //skip protocol tag
    if ( strstr( filename, "feeder:") != NULL )
    	filename += 7;
    av_strstart(filename, "feeder:", &filename);
    
	av_log(NULL, AV_LOG_ERROR,  "Feeder is called on file:%s\r\n", filename );     
    
    if (flags & URL_RDWR) {
        access = O_CREAT | O_TRUNC | O_RDWR;
    } else if (flags & URL_WRONLY) {
        access = O_CREAT | O_TRUNC | O_WRONLY;
    } else {
        access = O_RDONLY;
    }
    
    
#ifdef O_BINARY
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
	fd = _wopen(wfilename, access, 0666);
	av_free(wfilename);
#else
    fd = open(filename, access, 0666);
#endif
    if (fd < 0)
        return AVERROR(ENOENT);
    h->priv_data = (void *) (intptr_t) fd;
    return 0;
}

static int feeder_read(URLContext *h, unsigned char *buf, int size)
{
    int fd = (intptr_t) h->priv_data;
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

static int feeder_write(URLContext *h, unsigned char *buf, int size)
{
    int fd = (intptr_t) h->priv_data;
    return write(fd, buf, size);
}

/* XXX: use llseek */
static int64_t feeder_seek(URLContext *h, int64_t pos, int whence)
{
    int fd = (intptr_t) h->priv_data;
    return lseek(fd, pos, whence);
}

static int feeder_close(URLContext *h)
{
    int fd = (intptr_t) h->priv_data;
    return close(fd);
}

static int feeder_get_handle(URLContext *h)
{
    return (intptr_t) h->priv_data;
}


URLProtocol feeder_protocol = {
    "feeder",
    feeder_open,
    feeder_read,
    feeder_write,
    feeder_seek,
    feeder_close,
    .url_get_file_handle = feeder_get_handle,
};
