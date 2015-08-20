/*
 *  stream_sagetv.c
 *
 *	Copyright (C) Jeffrey Kardatzke - 01/2005
 *
 *  This file is part of MPlayer, a free movie player.
 *	
 *  MPlayer is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2, or (at your option)
 *  any later version.
 *   
 *  MPlayer is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *   
 *  You should have received a copy of the GNU General Public License
 *  along with GNU Make; see the file COPYING.  If not, write to
 *  the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA. 
 *
 *
 */

/*
 *  Based off stream_netstream.c
 *
 *	Copyright (C) Alban Bedel - 04/2003
 *
 *  This file is part of MPlayer, a free movie player.
 *	
 *  MPlayer is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2, or (at your option)
 *  any later version.
 *   
 *  MPlayer is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *   
 *  You should have received a copy of the GNU General Public License
 *  along with GNU Make; see the file COPYING.  If not, write to
 *  the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA. 
 *
 *
 */

/*
 * This allows you to stream files directly from a SageTV server
 */

#include "config.h"

#ifdef STREAM_SAGETV

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

#include <stdlib.h>
#include <stdio.h>
#include <inttypes.h>
#include <errno.h>

#ifndef WIN32	  
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <pthread.h>
#else
#include <windows.h>
#endif

#include "mp_msg.h"
#include "stream.h"
#include "help_mp.h"
#include "../m_option.h"
#include "../m_struct.h"
//#include "../bswap.h"

#define min(a,b) (((a)<(b))?(a):(b))

extern int active_file; // from mplayer.c since it uses this after the open call

static struct stream_priv_s {
  char* host;
  int port;
  char* url;

  off_t actualSize;
#ifndef WIN32	  
  pthread_mutex_t* mutex;
#else
  LPCRITICAL_SECTION mutex;
#endif
}
stream_priv_dflts = {
  NULL,
  7818,
  NULL,
  0,
  NULL
};

#define ST_OFF(f) M_ST_OFF(struct stream_priv_s,f)
/// URL definition
static m_option_t stream_opts_fields[] = {
  {"hostname", ST_OFF(host), CONF_TYPE_STRING, 0, 0 ,0, NULL},
  {"port", ST_OFF(port), CONF_TYPE_INT, M_OPT_MIN, 1 ,0, NULL},
  {"filename", ST_OFF(url), CONF_TYPE_STRING, 0, 0 ,0, NULL},
  { NULL, NULL, 0, 0, 0, 0,  NULL }
};
static struct m_struct_st stream_opts = {
  "stvstream",
  sizeof(struct stream_priv_s),
  &stream_priv_dflts,
  stream_opts_fields
};
#define SOCKET_ERROR -1
#define INVALID_SOCKET -1
#define RED_TIMEOUT 30000

static int lock_fd(struct stream_priv_s* p) {
//  printf("Lock (%d) %d\n",getpid(), (int)p->mutex);fflush(stdout);
#ifndef WIN32
  if (p->mutex && pthread_mutex_lock(p->mutex) < 0)
  {
      printf("Failed to get the lock: %s\n",
	     strerror(errno));fflush(stdout);
      return 0;
  }
#else
  if (p->mutex)
	  EnterCriticalSection(p->mutex);
#endif
//  printf("Locked (%d)\n",getpid());fflush(stdout);
  return 1;
}

static int unlock_fd(struct stream_priv_s *p) {
//  printf("Unlock (%d) %d\n",getpid(), (int) p->mutex);fflush(stdout);
#ifndef WIN32
  if (p->mutex && pthread_mutex_unlock(p->mutex) < 0)
  {
    printf("Failed to release the lock: %s\n",
	   strerror(errno));fflush(stdout);
    return 0;
  }
#else
  if (p->mutex)
	  LeaveCriticalSection(p->mutex);
#endif
  return 1;
}


// Reads data from a socket into the array until the "\r\n" character
// sequence is encountered. The returned value is the
// number of bytes read or SOCKET_ERROR if an error occurs, 0
// if the socket has been closed. The number of bytes will be
// 2 more than the actual string length because the \r\n chars
// are removed before this function returns.
int sockReadLine(int sd, char* buffer, int bufLen)
{
	int currRecv;
	int newlineIndex = 0;
	int endFound = 0;
	int offset = 0;
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
		int i = 0;
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

int OpenConnection(stream_t* stream)
{
    struct stream_priv_s* p = (struct stream_priv_s*)stream->priv;
//printf("Opening conn to SageTV server\n");fflush(stdout);
	int newfd = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
	if (newfd == INVALID_SOCKET)
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		return 0;
	}

	// Set the socket timeout option. If a timeout occurs, then it'll be just
	// like the server closed the socket.
#ifndef WIN32
	struct timeval tv;
	tv.tv_sec = 30;
	tv.tv_usec = 0;
	if (!setsockopt(newfd, SOL_SOCKET, SO_RCVTIMEO, (const char*)&tv, sizeof(tv)))
		printf("FAILURE %d err=%d newfd=%d\n", __LINE__, errno, newfd);
#else
	int myTimeout = 30000;
	if (setsockopt(newfd, SOL_SOCKET, SO_RCVTIMEO, (const char*)&myTimeout, sizeof(myTimeout)) < 0)
		printf("FAILURE %d err=%d newfd=%d sizeof(int)=%d\n", __LINE__, errno, newfd, sizeof(myTimeout));
#endif
	// Set the socket linger option, this makes sure the QUIT message gets received
	// by the server before the TCP reset message does.
//	LINGER lingonberry;
//	lingonberry.l_onoff = TRUE;
//	lingonberry.l_linger = 1;
//	if (setsockopt(stream->fd, SOL_SOCKET, SO_LINGER, (char*)&lingonberry, sizeof(LINGER)) == SOCKET_ERROR)
//	{
//		return STREAM_ERROR;
//	}
	struct sockaddr address;
	struct sockaddr_in* inetAddress;
	inetAddress = (struct sockaddr_in*) ( (void *) &address); // cast it to IPV4 addressing
	inetAddress->sin_family = PF_INET;
	inetAddress->sin_port = htons(7818);

	struct hostent* hostptr;
	hostptr = gethostbyname(p->host);
	if (!hostptr)
	{
		close(newfd);
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		return 0;
	}
    memcpy(&inetAddress->sin_addr.s_addr, hostptr->h_addr, hostptr->h_length );
 
    if (connect(newfd, (struct sockaddr *) ((void *)&address), sizeof(address)) < 0)
	{
		close(newfd);
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		return 0;
	}

	char data[512];
	sprintf(data, "OPEN %s\r\n", p->url);
	int dataSize = strlen(data);
	if (send(newfd, data, dataSize, 0) < dataSize)
	{
		close(newfd);
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		return 0;
	}

	int res;
	if ((res = sockReadLine(newfd, data, sizeof(data))) < 0 || strcmp(data, "OK"))
	{
		close(newfd);
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		return 0;
	}
	stream->fd = newfd;
	return 1;
}

int ReOpenConnection(stream_t* s)
{
//printf("Reopening connection\n");fflush(stdout);
    struct stream_priv_s* p = (struct stream_priv_s*)s->priv;
    if (!p->url) return 0;
	unlock_fd(p);
	close(s->fd);
	s->fd = 0;
	if (OpenConnection(s))
	{
		lock_fd(p);
		return 1;
	}
	return 0;
}

static int seek(stream_t *s,off_t newpos)
{
  struct stream_priv_s* p = (struct stream_priv_s*)s->priv;
	if (newpos >= 0 && s->activeFileFlag)
	{
		s->pos = newpos;
		return 1;
	}
	if (!lock_fd(p))
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		return 0;
	}
	char data[512];
	sprintf(data, "SIZE\r\n");
	int dataSize = strlen(data);
//printf("Sending cmd to SageTV Server:%s", data);fflush(stdout);
	if (send(s->fd, data, dataSize, 0) < dataSize)
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		if (!ReOpenConnection(s))
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			return 0;
		}
		if (send(s->fd, data, dataSize, 0) < dataSize)
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			unlock_fd(p);
			return 0;
		}
	}

	int nbytes = sockReadLine(s->fd, data, sizeof(data));
	if (nbytes < 0)
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		if (!ReOpenConnection(s))
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			return 0;
		}
		sprintf(data, "SIZE\r\n");
		if (send(s->fd, data, dataSize, 0) < dataSize)
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			unlock_fd(p);
			return 0;
		}
		nbytes = sockReadLine(s->fd, data, sizeof(data));
		if (nbytes < 0)
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			unlock_fd(p);
			return 0;
		}
	}
	unlock_fd(p);
	char* spacePtr = strchr(data, ' ');
	if (!spacePtr)
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		return 0;
	}
	*spacePtr = '\0';
	off_t availSize = strtoll(data, NULL, 10);
	if (newpos >= 0 && (newpos < availSize || s->activeFileFlag))
	{
		s->pos = newpos;
		return 1;
	}
	else
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		return 0;

	}
}

static off_t size(struct stream_st *s, off_t *availSize)
{
    struct stream_priv_s* p = (struct stream_priv_s*)s->priv;
	char data[512];
	off_t otherAvail;
	if (!availSize)
		availSize = &otherAvail;
	sprintf(data, "SIZE\r\n");
	int dataSize = strlen(data);
	if (!lock_fd(p)) return 0;
//printf("Sending2 cmd to SageTV Server:%s\n", data);fflush(stdout);
	if (send(s->fd, data, dataSize, 0) < dataSize)
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
//		printf("socket write failed, reopening...\n");
		if (!ReOpenConnection(s))
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			return 0;
		}
		if (send(s->fd, data, dataSize, 0) < dataSize)
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			unlock_fd(p);
			return 0;
		}
	}

	int nbytes = sockReadLine(s->fd, data, sizeof(data));
	if (nbytes < 0)
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		if (!ReOpenConnection(s))
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			return 0;
		}
		sprintf(data, "SIZE\r\n");
		if (send(s->fd, data, dataSize, 0) < dataSize)
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			unlock_fd(p);
			return 0;
		}
		nbytes = sockReadLine(s->fd, data, sizeof(data));
		if (nbytes < 0)
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			unlock_fd(p);
			return 0;
		}
	}
//	printf("Read back %s\n", data);fflush(stdout);
	unlock_fd(p);
	char* spacePtr = strchr(data, ' ');
	if (!spacePtr)
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		return 0;
	}
	*spacePtr = '\0';
	if (availSize)
		*availSize = strtoll(data, NULL, 10);
	off_t totalSize = strtoll(spacePtr + 1, NULL, 10);
//printf("avail=%I64d total=%I64d\n", *availSize, totalSize);fflush(stdout);
	if (*availSize != totalSize)
	{
		// Be sure the active file flag is set!
		if (!s->activeFileFlag)
		{
			if (s->cache_data)
			{
				cache_vars_t* sc = s->cache_data;
				sc->streamOriginal->activeFileFlag = 1;
			}
			s->activeFileFlag = 1;
		}
		active_file = 1;
	}
	else
	{
		// If we're going to turn it on we better turn it off as well!
		if (s->activeFileFlag)
		{
			if (s->cache_data)
			{
				cache_vars_t* sc = s->cache_data;
				sc->streamOriginal->activeFileFlag = 0;
			}
			s->activeFileFlag = 0;
		}
		active_file	= 0;
	}
	return totalSize;
}


static int fill_buffer(stream_t *s, char* pbBuffer, int max_len)
{
    struct stream_priv_s* p = (struct stream_priv_s*)s->priv;

	// Check on EOS condition
	if (s->activeFileFlag)
		p->actualSize = 0;
	if (!p->actualSize && !s->activeFileFlag)
	{
		// Get the actual size and store it
		p->actualSize = size(s, NULL);
	}
	if (p->actualSize && !s->activeFileFlag)
	{
		if (max_len + s->pos > p->actualSize)
		{
			max_len = p->actualSize - s->pos;
			if (max_len < 0) max_len = 0;
		}
	}
	
	if (max_len <= 0) return 0;
	char data[512];
	lock_fd(p);
#ifndef WIN32
	sprintf(data, "READ %lld %d\r\n", s->pos, max_len);
#else
	sprintf(data, "READ %I64d %d\r\n", s->pos, max_len);
#endif
	int dataSize = strlen(data);
//printf("Sending cmd to SageTV Server:%s\n", data);fflush(stdout);
	if (send(s->fd, data, dataSize, 0) < dataSize)
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		// Try to do it again...
		if (!ReOpenConnection(s))
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			return 0;
		}
		if (send(s->fd, data, dataSize, 0) < dataSize)
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			unlock_fd(p);
			return 0;
		}
	}

	int nbytes;
	char* pOriginalBuffer = pbBuffer;
	int originaldwBytesToRead = max_len;
	int bytesRead = 0;
	nbytes = recv(s->fd, (char*)pbBuffer, max_len, 0);
	while (nbytes >= 0 && max_len > 0)
	{
		max_len -= nbytes;
		pbBuffer += nbytes;
		bytesRead += nbytes;
		if (max_len > 0)
			nbytes = recv(s->fd, (char*)pbBuffer, max_len, 0);
	}
	if (nbytes < 0)
	{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
		if (!ReOpenConnection(s))
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			return 0;
		}
		if (send(s->fd, data, dataSize, 0) < dataSize)
		{
printf("FAILURE %d\n", __LINE__);fflush(stdout);
			unlock_fd(p);
			return 0;
		}
		bytesRead = 0;
		pbBuffer = pOriginalBuffer;
		max_len = originaldwBytesToRead;
		nbytes = recv(s->fd, (char*)pbBuffer, max_len, 0);
		while (nbytes >= 0 && max_len > 0)
		{
			max_len -= nbytes;
			pbBuffer += nbytes;
			bytesRead += nbytes;
			if (max_len > 0)
				nbytes = recv(s->fd, (char*)pbBuffer, max_len, 0);
		}
		if (nbytes < 0)
		{
//printf("FAILURE %d\n", __LINE__);fflush(stdout);
			unlock_fd(p);
			return 0;
		}
	}
	unlock_fd(p);
//printf("Read %d bytes from network\n", bytesRead);fflush(stdout);
	//s->pos += bytesRead; // for mplayer this is handled externally to us
	// Check in case the server's flipped the active file flag and we're not an integrated player
	if (s->activeFileFlag)
	{
		p->actualSize = size(s, NULL);
		if (s->activeFileFlag)
			p->actualSize = 0;
		else
		{
//printf("Active file flag flipped!!!\n");fflush(stdout);
			// Flag got flipped, make sure we don't have overage!
			bytesRead = min(bytesRead, p->actualSize - s->pos);
		}
	}
	return bytesRead;
}

static int control(struct stream_st *s,int cmd,void* arg) {
//  switch(cmd) {
//  case STREAM_CTRL_RESET:
//    return net_stream_reset(s);
//  }
  return STREAM_UNSUPORTED;
}

static void close_s(struct stream_st *s) {
	struct stream_priv_s* p = s->priv;
	char* data = "QUIT\r\n";
	int dataSize = strlen(data);
	send(s->fd, data, dataSize, 0);
	close(s->fd);
	s->fd = 0;
	if (p->mutex)
	{
#ifndef WIN32
		pthread_mutex_destroy(p->mutex);
		free(p->mutex);
		p->mutex = 0;
#else
		DeleteCriticalSection(p->mutex);
		free(p->mutex);
		p->mutex = 0;
#endif
	}
	m_struct_free(&stream_opts,p);
}

static int open_s(stream_t *stream,int mode, void* opts, int* file_format) {
  int f;
  struct stream_priv_s* p = (struct stream_priv_s*)opts;
//printf("open_s called for stream_Sagetv\n");fflush(stdout);
  if(mode != STREAM_READ)
    return STREAM_UNSUPORTED;

  if(!p->host) {
    mp_msg(MSGT_OPEN,MSGL_ERR, "We need an host name (ex: stv://server.net/cdda://5)\n");
    m_struct_free(&stream_opts,opts);
    return STREAM_ERROR;
  }
  if(!p->url || strlen(p->url) == 0) {
    mp_msg(MSGT_OPEN,MSGL_ERR, "We need a remote url (ex: mpst://server.net/cdda://5)\n");
    m_struct_free(&stream_opts,opts);
    return STREAM_ERROR;
  }

  stream->priv = p;
  if (!OpenConnection(stream))
  {
	  stream->priv = 0;
	  m_struct_free(&stream_opts,opts);
	  return STREAM_ERROR;
  }
	stream->flags = STREAM_SEEK;

#ifndef WIN32
	pthread_mutexattr_t attr;
	p->mutex = (pthread_mutex_t*) malloc(sizeof(pthread_mutex_t));
	if (p->mutex)
	{
		pthread_mutexattr_init(&attr);
#ifdef CONFIG_DARWIN
		pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_RECURSIVE);
#else
		pthread_mutexattr_setkind_np(&attr, PTHREAD_MUTEX_RECURSIVE_NP);
#endif
		if (pthread_mutex_init(p->mutex, &attr) != 0)
		{
			free(p->mutex);
			p->mutex = NULL;
printf("MUTEX creation failed for pthread\n");
		}
	}
#else
	p->mutex = (LPCRITICAL_SECTION) malloc(sizeof(CRITICAL_SECTION));
	InitializeCriticalSection(p->mutex);
#endif
	stream->end_pos = size(stream, NULL);

	if (stream->activeFileFlag)
		p->actualSize = 0;
	else
		p->actualSize = stream->end_pos;

	stream->fill_buffer = fill_buffer;
	stream->control = control;
	stream->size = size;
	stream->seek = seek;
	stream->close = close_s;
    stream->type = STREAMTYPE_FILE;
	
	return STREAM_OK;

}

stream_info_t stream_info_sagetv = {
  "SageTV stream",
  "stvstream",
  "SageTV",
  "",
  open_s,
  { "stv",NULL },
  &stream_opts,
  1 // Url is an option string
};

#endif

