/*
 *	SageTV Streaming protocol for ffmpeg client
 *	Copyright (C) Jeffrey Kardatzke - 07/2006
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

/*
 * This allows you to stream files directly from a SageTV server
 */

#include "avformat.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

#include <stdlib.h>
#include <stdio.h>
#include <inttypes.h>
#include <errno.h>
#ifndef __MINGW32__
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#else
#include <windows.h>
#endif

//#define DEBUG_STV

#define ASKAHEAD 65536

typedef struct {
  char host[256];
  int port;
  char url[1024];
  offset_t actualSize;
  offset_t pos;
  int fd;
  int readahead;
  unsigned int readaheadfactor; // Set to 0 when go out of the read ahead buffer
  unsigned long long aheaddiscarded;
  unsigned char flushBuf[4096];
} STVContext;


static int flushReadAhead(STVContext *p)
{
    p->aheaddiscarded+=p->readahead;
    while(p->readahead)
    {
        int count = recv(p->fd, p->flushBuf, (p->readahead > 4096 ) ? 4096 : p->readahead, 0);
        if(count<0)
        {
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            return -1;
        }
        p->readahead -= count;
    }
    p->readaheadfactor=0;
    return 0;
}

#define SOCKET_ERROR -1
#define INVALID_SOCKET -1
#define RED_TIMEOUT 30000

// Reads data from a socket into the array until the "\r\n" character
// sequence is encountered. The returned value is the
// number of bytes read or SOCKET_ERROR if an error occurs, 0
// if the socket has been closed. The number of bytes will be
// 2 more than the actual string length because the \r\n chars
// are removed before this function returns.
static int sockReadLine(int sd, char* buffer, int bufLen)
{
	int currRecv;
	int newlineIndex = 0;
	int endFound = 0;
	int offset = 0;
	int i = 0;
	while (!endFound)
	{
		currRecv = recv(sd, buffer + offset, bufLen, MSG_PEEK);
		if (currRecv == SOCKET_ERROR)
		{
			return SOCKET_ERROR;
		}

		if (currRecv == 0)
		{
			return endFound ? 0 : SOCKET_ERROR;
		}

		// Scan the buffer for "\r\n" termination
		for (i = 0; i < (currRecv + offset); i++)
		{
			if (buffer[i] == '\r')
			{
				if (buffer[i + 1] == '\n')
				{
					newlineIndex = i + 1;
					endFound = 1;
					break;
				}
			}
		}
		if (!endFound)
		{
			currRecv = recv(sd, buffer + offset, currRecv, 0);
			if (currRecv == SOCKET_ERROR)
			{
				return SOCKET_ERROR;
			}
			if (currRecv == 0)
			{
				return endFound ? 0 : SOCKET_ERROR;
			}
			offset += currRecv;
		}
	}

	currRecv = recv(sd, buffer + offset, (newlineIndex + 1) - offset, 0);
	buffer[newlineIndex - 1] = '\0';
	return currRecv;
}

static int OpenConnection(STVContext* p)
{
	int newfd=-1;
	struct timeval tv;
	struct sockaddr address;
	struct sockaddr_in* inetAddress;
	struct hostent* hostptr;
	char data[512];
#ifdef __MINGW32__
	int strl;
	wchar_t* wfilename;
	int wstrlen;
	int dataIdx;
#endif
	int dataSize;
	int res;
	int window_size = 256 * 1024;
	int wlen = 4;

    #ifdef DEBUG_STV
    av_log(NULL, AV_LOG_ERROR, "Opening conn to SageTV server\n");
    #endif
	newfd = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
	if (newfd == INVALID_SOCKET)
	{
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR,"FAILURE %d\n", __LINE__);
        #endif
		return 0;
	}

    setsockopt(newfd, SOL_SOCKET, SO_RCVBUF, &window_size, sizeof(window_size));
    getsockopt(newfd, SOL_SOCKET, SO_RCVBUF,
                         (char*) &window_size, &wlen );
    av_log(NULL, AV_LOG_ERROR,"STV:// socket recv tcp window size %d\n",window_size);

	// Set the socket timeout option. If a timeout occurs, then it'll be just
	// like the server closed the socket.
	tv.tv_sec = 30;
	tv.tv_usec = 0;
	setsockopt(newfd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
	setsockopt(newfd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
	// Set the socket linger option, this makes sure the QUIT message gets received
	// by the server before the TCP reset message does.
//	LINGER lingonberry;
//	lingonberry.l_onoff = TRUE;
//	lingonberry.l_linger = 1;
//	if (setsockopt(stream->fd, SOL_SOCKET, SO_LINGER, (char*)&lingonberry, sizeof(LINGER)) == SOCKET_ERROR)
//	{
//		return STREAM_ERROR;
//	}
	inetAddress = (struct sockaddr_in*) ( (void *) &address); // cast it to IPV4 addressing
	inetAddress->sin_family = PF_INET;
	inetAddress->sin_port = htons(7818);

	hostptr = gethostbyname(p->host);
	if (!hostptr)
	{
		close(newfd);
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
        #endif
		return 0;
	}
    memcpy(&inetAddress->sin_addr.s_addr, hostptr->h_addr, hostptr->h_length );
 
    if (connect(newfd, (struct sockaddr *) ((void *)&address), sizeof(address)) < 0)
	{
		close(newfd);
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
        #endif
		return 0;
	}

#ifdef __MINGW32__
	// Check for UTF-8 unicode pathname
	strl = strlen(p->url);
	wfilename = av_malloc(sizeof(wchar_t) * (1 + strl));
	int wpos = 0;
	int i = 0;
	for (i = 0; i < strl; i++)
	{
		wfilename[wpos] = 0;
		if ((p->url[i] & 0x80) == 0)
		{
			// ASCII character
			wfilename[wpos++] = p->url[i];
		}
		else if (i + 1 < strl && ((p->url[i] & 0xE0) == 0xC0) && ((p->url[i + 1] & 0xC0) == 0x80))
		{
			// two octets for this character
			wfilename[wpos++] = ((p->url[i] & 0x1F) << 6) + (p->url[i + 1] & 0x3F);
			i++;
		}
		else if (i + 2 < strl && ((p->url[i] & 0xF0) == 0xE0) && ((p->url[i + 1] & 0xC0) == 0x80) && 
			((p->url[i + 2] & 0xC0) == 0x80))
		{
			// three octets for this character
			wfilename[wpos++] = ((p->url[i] & 0x0F) << 12) + ((p->url[i + 1] & 0x3F) << 6) + (p->url[i + 2] & 0x3F);
			i+=2;
		}
		else
			wfilename[wpos++] = p->url[i];
	}
	wfilename[wpos] = 0;
	strcpy(data, "OPENW ");
	wstrlen = wcslen(wfilename);
	dataIdx = strlen(data);
	for (i = 0; i < wstrlen; i++, dataIdx+=2)
	{
		data[dataIdx] = ((wfilename[i] & 0xFF00) >> 8);
		data[dataIdx + 1] = (wfilename[i] & 0xFF);
	}
	data[dataIdx++] = '\r';
	data[dataIdx++] = '\n';
	av_free(wfilename);
	dataSize = dataIdx;
#else
	strcpy(data, "OPEN ");
	pstrcat(data, 512, p->url);
	pstrcat(data, 512, "\r\n");
	dataSize = strlen(data);
#endif
	if (send(newfd, data, dataSize, 0) < dataSize)
	{
		close(newfd);
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
        #endif
		return 0;
	}

	if ((res = sockReadLine(newfd, data, sizeof(data))) < 0 || strcmp(data, "OK"))
	{
		close(newfd);
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
        #endif
		return 0;
	}
	p->fd = newfd;
	p->readahead=0;
	p->readaheadfactor=0;
	return 1;
}

int ReOpenConnection(STVContext* p)
{
    #ifdef DEBUG_STV
    av_log(NULL, AV_LOG_ERROR, "Reopening connection\n");
    #endif
    if (!p->url) return 0;
	close(p->fd);
	p->fd = 0;
	if (OpenConnection(p))
	{
		return 1;
	}
	return 0;
}

static offset_t stv_seek(URLContext *h, offset_t pos, int whence)
{
	offset_t availSize;
	char data[512];
	int dataSize;
	int nbytes;
	char* spacePtr;
	STVContext* p = h->priv_data;
    flushReadAhead(p);
    #ifdef DEBUG_STV
#ifndef __MINGW32__
    av_log(NULL, AV_LOG_ERROR, "stv_seek %lld %d\n", pos, whence);
#else
    av_log(NULL, AV_LOG_ERROR, "stv_seek %I64d %d active=%d\n", pos, whence, ((h->flags & URL_ACTIVEFILE) == URL_ACTIVEFILE));
#endif
    #endif
	if (pos >= 0 && ((h->flags & URL_ACTIVEFILE) == URL_ACTIVEFILE) && whence != SEEK_END && 
		whence != AVSEEK_SIZE)
	{
		if (whence == SEEK_CUR)
			p->pos += pos;
		else if (whence == SEEK_SET)
			p->pos = pos;
		return p->pos;
	}
	
	if ((h->flags & URL_ACTIVEFILE) == URL_ACTIVEFILE)
	{
	strcpy(data, "SIZE\r\n");
        dataSize = strlen(data);
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "Sending cmd to SageTV Server:%s", data);
        #endif
	if (send(p->fd, data, dataSize, 0) < dataSize)
	{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
		if (!ReOpenConnection(p))
		{
                #ifdef DEBUG_STV
                av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
                #endif
			return 0;
		}
		if (send(p->fd, data, dataSize, 0) < dataSize)
		{
                #ifdef DEBUG_STV
                av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
                #endif
			return 0;
		}
	}
        flushReadAhead(p);
        nbytes = sockReadLine(p->fd, data, sizeof(data));
	if (nbytes < 0)
	{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
		if (!ReOpenConnection(p))
		{
                #ifdef DEBUG_STV
                av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
                #endif
			return 0;
		}
		strcpy(data, "SIZE\r\n");
		if (send(p->fd, data, dataSize, 0) < dataSize)
		{
                #ifdef DEBUG_STV
                av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
                #endif
			return 0;
		}
            flushReadAhead(p);
		nbytes = sockReadLine(p->fd, data, sizeof(data));
		if (nbytes < 0)
		{
                #ifdef DEBUG_STV
                av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
                #endif
			return 0;
		}
	}
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "Read back %s\n", data);
        #endif
        spacePtr = strchr(data, ' ');
	if (!spacePtr)
	{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
		return p->pos;
	}
	*spacePtr = '\0';
        
        availSize =  strtoll(data, NULL, 10);
    }
    else
    {
        availSize = p->actualSize;
    }
	if (whence == AVSEEK_SIZE)
		return availSize;
	if (whence == SEEK_CUR)
		pos += p->pos;
	else if (whence == SEEK_END)
		pos += availSize;
    #ifdef DEBUG_STV
    av_log(NULL, AV_LOG_ERROR, "seek new pos %lld avail %lld\n", pos, availSize);
    #endif
	if (pos >= 0 && (pos <= availSize || ((h->flags & URL_ACTIVEFILE) == URL_ACTIVEFILE)))
	{
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "Setting stream pos to %lld\n", pos);
        #endif
		p->pos = pos;
		return pos;
	}
	else
	{
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
        #endif
		return p->pos;

	}
}

static offset_t size(URLContext* h, STVContext* p, offset_t *availSize)
{
	char data[512];
	offset_t otherAvail;
	int dataSize;
	int nbytes;
	char* spacePtr;
	offset_t totalSize;
	if (!availSize)
		availSize = &otherAvail;
	strcpy(data, "SIZE\r\n");
	dataSize = strlen(data);
    #ifdef DEBUG_STV
    av_log(NULL, AV_LOG_ERROR, "Sending2 cmd to SageTV Server:%s\n", data);
    #endif
	if (send(p->fd, data, dataSize, 0) < dataSize)
	{
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
        av_log(NULL, AV_LOG_ERROR, "socket write failed, reopening...\n");
        #endif
		if (!ReOpenConnection(p))
		{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
			return 0;
		}
		if (send(p->fd, data, dataSize, 0) < dataSize)
		{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
			return 0;
		}
	}

    flushReadAhead(p);
	nbytes = sockReadLine(p->fd, data, sizeof(data));
	if (nbytes < 0)
	{
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
        #endif
		if (!ReOpenConnection(p))
		{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
			return 0;
		}
		strcpy(data, "SIZE\r\n");
		if (send(p->fd, data, dataSize, 0) < dataSize)
		{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
			return 0;
		}
        flushReadAhead(p);
		nbytes = sockReadLine(p->fd, data, sizeof(data));
		if (nbytes < 0)
		{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
			return 0;
		}
	}
    #ifdef DEBUG_STV
    av_log(NULL, AV_LOG_ERROR, "Read back %s\n", data);
    #endif
	spacePtr = strchr(data, ' ');
	if (!spacePtr)
	{
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
        #endif
		return 0;
	}
	*spacePtr = '\0';
	if (availSize)
		*availSize = strtoll(data, NULL, 10);
	totalSize = strtoll(spacePtr + 1, NULL, 10);
	if (totalSize != *availSize)
	{
		h->flags = h->flags | URL_ACTIVEFILE;
	}
    #ifdef DEBUG_STV
    av_log(NULL, AV_LOG_ERROR, "avail=%lld total=%lld\n", *availSize, totalSize);
    #endif
	return totalSize;
}


static int stv_read(URLContext *h, unsigned char* pbBuffer, int max_len)
{
    STVContext *p = h->priv_data;
	char data[512];
	int dataSize;
	int nbytes;
	char* pOriginalBuffer;
	int originaldwBytesToRead;
	int bytesRead;
	int stv_read_len = max_len;

	// Check on EOS condition
	if ((h->flags & URL_ACTIVEFILE) == URL_ACTIVEFILE && ((max_len + p->pos) > p->actualSize))
	{
		offset_t totalSize;
		totalSize = size(h, p, &p->actualSize);
		if(totalSize==p->actualSize) h->flags&=~URL_ACTIVEFILE;
		#ifdef DEBUG_STV
		av_log(NULL, AV_LOG_ERROR, "Active file is :%d %lld %lld\n", 
			h->flags & URL_ACTIVEFILE,totalSize,p->actualSize);
		#endif
	}
	if (!p->actualSize && (h->flags & URL_ACTIVEFILE) != URL_ACTIVEFILE)
	{
		// Get the actual size and store it
		size(h, p, &p->actualSize);
	}
	if (p->actualSize && (h->flags & URL_ACTIVEFILE) != URL_ACTIVEFILE)
	{
		p->readaheadfactor+=1;
		if(p->readaheadfactor > 2)
		{
			max_len += ASKAHEAD - p->readahead;
		}
		if (max_len + p->pos > p->actualSize)
		{
			max_len = p->actualSize - p->pos;
			if (max_len < 0)
			{
				max_len = 0;
				return -1; // Signal EOF
		}
		}
		p->readahead+=max_len;
	}
	else
	{
		p->readahead=max_len;
	}
	if (max_len <= 0) return 0;
	if (stv_read_len > max_len ) stv_read_len = max_len;
    #ifdef DEBUG_STV
    av_log(NULL, AV_LOG_ERROR, "Read ahead values %d %d %d\n", 
        p->readahead, p->readaheadfactor, max_len);
    #endif
	
#ifndef __MINGW32__
	snprintf(data, 512, "READ %lld %d\r\n", p->pos+p->readahead-max_len, max_len);
#else
	snprintf(data, 512, "READ %I64d %d\r\n", p->pos+p->readahead-max_len, max_len);
#endif
	dataSize = strlen(data);
    #ifdef DEBUG_STV
    av_log(NULL, AV_LOG_ERROR, "Sending cmd to SageTV Server:%s\n", data);
    #endif
	if (send(p->fd, data, dataSize, 0) < dataSize)
	{
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
        #endif
		// Try to do it again...
		if (!ReOpenConnection(p))
		{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
			return 0;
		}
		if (send(p->fd, data, dataSize, 0) < dataSize)
		{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
			return 0;
		}
	}

	pOriginalBuffer = pbBuffer;
	originaldwBytesToRead = stv_read_len;
	bytesRead = 0;
	nbytes = recv(p->fd, (char*)pbBuffer, stv_read_len, 0);
	while (nbytes >= 0 && stv_read_len > 0)
	{
		stv_read_len -= nbytes;
		pbBuffer += nbytes;
		bytesRead += nbytes;
		p->readahead-= nbytes;
		if (stv_read_len > 0)
			nbytes = recv(p->fd, (char*)pbBuffer, stv_read_len, 0);
	}
	if (nbytes < 0)
	{
        #ifdef DEBUG_STV
        av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
        #endif
		if (!ReOpenConnection(p))
		{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
			return 0;
		}
		if (send(p->fd, data, dataSize, 0) < dataSize)
		{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
			return 0;
		}
		bytesRead = 0;
		pbBuffer = pOriginalBuffer;
		max_len = originaldwBytesToRead;
		nbytes = recv(p->fd, (char*)pbBuffer, max_len, 0);
		while (nbytes >= 0 && max_len > 0)
		{
			max_len -= nbytes;
			pbBuffer += nbytes;
			bytesRead += nbytes;
			if (max_len > 0)
				nbytes = recv(p->fd, (char*)pbBuffer, max_len, 0);
		}
		if (nbytes < 0)
		{
            #ifdef DEBUG_STV
            av_log(NULL, AV_LOG_ERROR, "FAILURE %d\n", __LINE__);
            #endif
			return 0;
		}
	}
    #ifdef DEBUG_STV
    av_log(NULL, AV_LOG_ERROR, "Read %d bytes from network (%lld %d)\n", 
        bytesRead, p->actualSize, h->flags & URL_ACTIVEFILE);
    #endif
	p->pos += bytesRead;
	return bytesRead;
}

static int stv_close(URLContext *h)
{
	STVContext *p = h->priv_data;
	if (p->fd)
	{
		char* data = "QUIT\r\n";
		int dataSize = strlen(data);
        flushReadAhead(p);
		send(p->fd, data, dataSize, 0);
		close(p->fd);
		p->fd = 0;
	}
#ifdef __MINGW32__
	WSACleanup();
#endif
	av_free(p);
	return 0;
}

static int stv_open(URLContext *h, const char *filename, int flags)
{
	STVContext *p;
	char* fullURL;
	char* pathSlash;
    if (flags & URL_RDWR) {
        return -ENOENT;
    } else if (flags & URL_WRONLY) {
        return -ENOENT;
    } 

	p = av_mallocz(sizeof(STVContext));
	if (!p)
		return -ENOMEM;
#ifdef __MINGW32__
	WSADATA wsaData;
	if (WSAStartup(0x202,&wsaData) == SOCKET_ERROR) {
		return AVERROR_IO;
	}
#endif
	h->priv_data = p;
	if (!strstart(filename, "stv://", (const char **)&fullURL))
		goto fail;
	pathSlash = strchr(fullURL, '/');
	if (!pathSlash)
		goto fail;
	strncpy(p->host, fullURL, pathSlash - fullURL);
	strcpy(p->url, pathSlash + 1);

	if (!OpenConnection(p))
		goto fail;

	if ((h->flags & URL_ACTIVEFILE) == URL_ACTIVEFILE)
		p->actualSize = 0;
	else
		size(h, p, &p->actualSize);

	return 0;

fail:
	stv_close(h);
    return AVERROR_IO;
}

URLProtocol stv_protocol = {
	"stv",
	stv_open,
	stv_read,
	NULL, /* write */
	stv_seek,
	stv_close,
};
