/*
 * hdhomerun_sock_posix.c
 *
 * Copyright © 2010-2016 Silicondust USA Inc. <www.silicondust.com>.
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

#include <net/if.h>
#include <sys/ioctl.h>

#ifndef SIOCGIFCONF
#include <sys/sockio.h>
#endif

#ifndef _SIZEOF_ADDR_IFREQ
#define _SIZEOF_ADDR_IFREQ(x) sizeof(x)
#endif

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif

struct hdhomerun_sock_t {
	int sock;
};

int hdhomerun_local_ip_info(struct hdhomerun_local_ip_info_t ip_info_list[], int max_count)
{
	int sock = socket(AF_INET, SOCK_DGRAM, 0);
	if (sock == -1) {
		return -1;
	}

	struct ifconf ifc;
	size_t ifreq_buffer_size = 1024;

	while (1) {
		ifc.ifc_len = (int)ifreq_buffer_size;
		ifc.ifc_buf = (char *)malloc(ifreq_buffer_size);
		if (!ifc.ifc_buf) {
			close(sock);
			return -1;
		}

		memset(ifc.ifc_buf, 0, ifreq_buffer_size);

		if (ioctl(sock, SIOCGIFCONF, &ifc) != 0) {
			free(ifc.ifc_buf);
			close(sock);
			return -1;
		}

		if (ifc.ifc_len < (int)ifreq_buffer_size) {
			break;
		}

		free(ifc.ifc_buf);
		ifreq_buffer_size += 1024;
	}

	char *ptr = ifc.ifc_buf;
	char *end = ifc.ifc_buf + ifc.ifc_len;

	int count = 0;
	while (ptr < end) {
		struct ifreq *ifr = (struct ifreq *)ptr;
		ptr += _SIZEOF_ADDR_IFREQ(*ifr);

		/* Flags. */
		if (ioctl(sock, SIOCGIFFLAGS, ifr) != 0) {
			continue;
		}

		if ((ifr->ifr_flags & IFF_UP) == 0) {
			continue;
		}
		if ((ifr->ifr_flags & IFF_RUNNING) == 0) {
			continue;
		}

		/* Local IP address. */
		if (ioctl(sock, SIOCGIFADDR, ifr) != 0) {
			continue;
		}

		struct sockaddr_in *ip_addr_in = (struct sockaddr_in *)&(ifr->ifr_addr);
		uint32_t ip_addr = ntohl(ip_addr_in->sin_addr.s_addr);
		if (ip_addr == 0) {
			continue;
		}

		/* Subnet mask. */
		if (ioctl(sock, SIOCGIFNETMASK, ifr) != 0) {
			continue;
		}

		struct sockaddr_in *subnet_mask_in = (struct sockaddr_in *)&(ifr->ifr_addr);
		uint32_t subnet_mask = ntohl(subnet_mask_in->sin_addr.s_addr);

		/* Report. */
		if (count < max_count) {
			struct hdhomerun_local_ip_info_t *ip_info = &ip_info_list[count];
			ip_info->ip_addr = ip_addr;
			ip_info->subnet_mask = subnet_mask;
		}

		count++;
	}

	free(ifc.ifc_buf);
	close(sock);
	return count;
}

static struct hdhomerun_sock_t *hdhomerun_sock_create_internal(int protocol)
{
	struct hdhomerun_sock_t *sock = (struct hdhomerun_sock_t *)calloc(1, sizeof(struct hdhomerun_sock_t));
	if (!sock) {
		return NULL;
	}

	/* Create socket. */
	sock->sock = socket(AF_INET, protocol, 0);
	if (sock->sock == -1) {
		free(sock);
		return NULL;
	}

	/* Set non-blocking */
	if (fcntl(sock->sock, F_SETFL, O_NONBLOCK) != 0) {
		hdhomerun_sock_destroy(sock);
		return NULL;
	}

	/* Configure socket not to generate pipe-error signal (BSD/OSX). */
#if defined(SO_NOSIGPIPE)
	int set = 1;
	setsockopt(sock->sock, SOL_SOCKET, SO_NOSIGPIPE, (char *)&set, sizeof(set));
#endif

	/* Success. */
	return sock;
}

struct hdhomerun_sock_t *hdhomerun_sock_create_udp(void)
{
	struct hdhomerun_sock_t *sock = hdhomerun_sock_create_internal(SOCK_DGRAM);
	if (!sock) {
		return NULL;
	}

	/* Allow broadcast. */
	int sock_opt = 1;
	setsockopt(sock->sock, SOL_SOCKET, SO_BROADCAST, (char *)&sock_opt, sizeof(sock_opt));

	/* Success. */
	return sock;
}

struct hdhomerun_sock_t *hdhomerun_sock_create_tcp(void)
{
	return hdhomerun_sock_create_internal(SOCK_STREAM);
}

void hdhomerun_sock_destroy(struct hdhomerun_sock_t *sock)
{
	close(sock->sock);
	free(sock);
}

void hdhomerun_sock_stop(struct hdhomerun_sock_t *sock)
{
	shutdown(sock->sock, SHUT_RDWR);
}

void hdhomerun_sock_set_send_buffer_size(struct hdhomerun_sock_t *sock, size_t size)
{
	int size_opt = (int)size;
	setsockopt(sock->sock, SOL_SOCKET, SO_SNDBUF, (char *)&size_opt, sizeof(size_opt));
}

void hdhomerun_sock_set_recv_buffer_size(struct hdhomerun_sock_t *sock, size_t size)
{
	int size_opt = (int)size;
	setsockopt(sock->sock, SOL_SOCKET, SO_RCVBUF, (char *)&size_opt, sizeof(size_opt));
}

void hdhomerun_sock_set_allow_reuse(struct hdhomerun_sock_t *sock)
{
	int sock_opt = 1;
	setsockopt(sock->sock, SOL_SOCKET, SO_REUSEADDR, (char *)&sock_opt, sizeof(sock_opt));
}

int hdhomerun_sock_getlasterror(void)
{
	return errno;
}

uint32_t hdhomerun_sock_getsockname_addr(struct hdhomerun_sock_t *sock)
{
	struct sockaddr_in sock_addr;
	socklen_t sockaddr_size = sizeof(sock_addr);

	if (getsockname(sock->sock, (struct sockaddr *)&sock_addr, &sockaddr_size) != 0) {
		return 0;
	}

	return ntohl(sock_addr.sin_addr.s_addr);
}

uint16_t hdhomerun_sock_getsockname_port(struct hdhomerun_sock_t *sock)
{
	struct sockaddr_in sock_addr;
	socklen_t sockaddr_size = sizeof(sock_addr);

	if (getsockname(sock->sock, (struct sockaddr *)&sock_addr, &sockaddr_size) != 0) {
		return 0;
	}

	return ntohs(sock_addr.sin_port);
}

uint32_t hdhomerun_sock_getpeername_addr(struct hdhomerun_sock_t *sock)
{
	struct sockaddr_in sock_addr;
	socklen_t sockaddr_size = sizeof(sock_addr);

	if (getpeername(sock->sock, (struct sockaddr *)&sock_addr, &sockaddr_size) != 0) {
		return 0;
	}

	return ntohl(sock_addr.sin_addr.s_addr);
}

uint32_t hdhomerun_sock_getaddrinfo_addr(struct hdhomerun_sock_t *sock, const char *name)
{
	struct addrinfo hints;
	memset(&hints, 0, sizeof(hints));
	hints.ai_family = AF_INET;
	hints.ai_socktype = SOCK_STREAM;
	hints.ai_protocol = IPPROTO_TCP;

	struct addrinfo *sock_info;
	if (getaddrinfo(name, NULL, &hints, &sock_info) != 0) {
		return 0;
	}

	struct sockaddr_in *sock_addr = (struct sockaddr_in *)sock_info->ai_addr;
	uint32_t addr = ntohl(sock_addr->sin_addr.s_addr);

	freeaddrinfo(sock_info);
	return addr;
}

bool hdhomerun_sock_join_multicast_group(struct hdhomerun_sock_t *sock, uint32_t multicast_ip, uint32_t local_ip)
{
	struct ip_mreq imr;
	memset(&imr, 0, sizeof(imr));
	imr.imr_multiaddr.s_addr  = htonl(multicast_ip);
	imr.imr_interface.s_addr  = htonl(local_ip);

	if (setsockopt(sock->sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, (const char *)&imr, sizeof(imr)) != 0) {
		return false;
	}

	return true;
}

bool hdhomerun_sock_leave_multicast_group(struct hdhomerun_sock_t *sock, uint32_t multicast_ip, uint32_t local_ip)
{
	struct ip_mreq imr;
	memset(&imr, 0, sizeof(imr));
	imr.imr_multiaddr.s_addr  = htonl(multicast_ip);
	imr.imr_interface.s_addr  = htonl(local_ip);

	if (setsockopt(sock->sock, IPPROTO_IP, IP_DROP_MEMBERSHIP, (const char *)&imr, sizeof(imr)) != 0) {
		return false;
	}

	return true;
}

bool hdhomerun_sock_bind(struct hdhomerun_sock_t *sock, uint32_t local_addr, uint16_t local_port, bool allow_reuse)
{
	int sock_opt = allow_reuse;
	setsockopt(sock->sock, SOL_SOCKET, SO_REUSEADDR, (char *)&sock_opt, sizeof(sock_opt));

	struct sockaddr_in sock_addr;
	memset(&sock_addr, 0, sizeof(sock_addr));
	sock_addr.sin_family = AF_INET;
	sock_addr.sin_addr.s_addr = htonl(local_addr);
	sock_addr.sin_port = htons(local_port);

	if (bind(sock->sock, (struct sockaddr *)&sock_addr, sizeof(sock_addr)) != 0) {
		return false;
	}

	return true;
}

bool hdhomerun_sock_connect(struct hdhomerun_sock_t *sock, uint32_t remote_addr, uint16_t remote_port, uint64_t timeout)
{
	struct sockaddr_in sock_addr;
	memset(&sock_addr, 0, sizeof(sock_addr));
	sock_addr.sin_family = AF_INET;
	sock_addr.sin_addr.s_addr = htonl(remote_addr);
	sock_addr.sin_port = htons(remote_port);

	if (connect(sock->sock, (struct sockaddr *)&sock_addr, sizeof(sock_addr)) != 0) {
		if ((errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
			return false;
		}
	}

	struct pollfd poll_event;
	poll_event.fd = sock->sock;
	poll_event.events = POLLOUT;
	poll_event.revents = 0;

	if (poll(&poll_event, 1, (int)timeout) <= 0) {
		return false;
	}

	if ((poll_event.revents & POLLOUT) == 0) {
		return false;
	}

	return true;
}

bool hdhomerun_sock_send(struct hdhomerun_sock_t *sock, const void *data, size_t length, uint64_t timeout)
{
	const uint8_t *ptr = (const uint8_t *)data;
	ssize_t ret = send(sock->sock, ptr, length, MSG_NOSIGNAL);
	if (ret >= (ssize_t)length) {
		return true;
	}

	if ((ret < 0) && (errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
		return false;
	}

	if (ret > 0) {
		ptr += ret;
		length -= ret;
	}

	uint64_t stop_time = getcurrenttime() + timeout;

	while (1) {
		struct pollfd poll_event;
		poll_event.fd = sock->sock;
		poll_event.events = POLLOUT;
		poll_event.revents = 0;

		if (poll(&poll_event, 1, (int)timeout) <= 0) {
			return false;
		}

		if ((poll_event.revents & POLLOUT) == 0) {
			return false;
		}

		ret = send(sock->sock, ptr, length, MSG_NOSIGNAL);
		if (ret >= (ssize_t)length) {
			return true;
		}

		if ((ret < 0) && (errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
			return false;
		}

		if (ret > 0) {
			ptr += ret;
			length -= ret;
		}

		uint64_t current_time = getcurrenttime();
		if (current_time >= stop_time) {
			return false;
		}

		timeout = stop_time - current_time;
	}
}

bool hdhomerun_sock_sendto(struct hdhomerun_sock_t *sock, uint32_t remote_addr, uint16_t remote_port, const void *data, size_t length, uint64_t timeout)
{
	struct sockaddr_in sock_addr;
	memset(&sock_addr, 0, sizeof(sock_addr));
	sock_addr.sin_family = AF_INET;
	sock_addr.sin_addr.s_addr = htonl(remote_addr);
	sock_addr.sin_port = htons(remote_port);

	const uint8_t *ptr = (const uint8_t *)data;
	ssize_t ret = sendto(sock->sock, ptr, length, 0, (struct sockaddr *)&sock_addr, sizeof(sock_addr));
	if (ret >= (ssize_t)length) {
		return true;
	}

	if ((ret < 0) && (errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
		return false;
	}

	if (ret > 0) {
		ptr += ret;
		length -= ret;
	}

	uint64_t stop_time = getcurrenttime() + timeout;

	while (1) {
		struct pollfd poll_event;
		poll_event.fd = sock->sock;
		poll_event.events = POLLOUT;
		poll_event.revents = 0;

		if (poll(&poll_event, 1, (int)timeout) <= 0) {
			return false;
		}

		if ((poll_event.revents & POLLOUT) == 0) {
			return false;
		}

		ret = sendto(sock->sock, ptr, length, 0, (struct sockaddr *)&sock_addr, sizeof(sock_addr));
		if (ret >= (ssize_t)length) {
			return true;
		}

		if ((ret < 0) && (errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
			return false;
		}

		if (ret > 0) {
			ptr += ret;
			length -= ret;
		}

		uint64_t current_time = getcurrenttime();
		if (current_time >= stop_time) {
			return false;
		}

		timeout = stop_time - current_time;
	}
}

bool hdhomerun_sock_recv(struct hdhomerun_sock_t *sock, void *data, size_t *length, uint64_t timeout)
{
	ssize_t ret = recv(sock->sock, data, *length, 0);
	if (ret > 0) {
		*length = (size_t)ret;
		return true;
	}

	if (ret == 0) {
		return false;
	}
	if ((errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
		return false;
	}

	struct pollfd poll_event;
	poll_event.fd = sock->sock;
	poll_event.events = POLLIN;
	poll_event.revents = 0;

	if (poll(&poll_event, 1, (int)timeout) <= 0) {
		return false;
	}

	if ((poll_event.revents & POLLIN) == 0) {
		return false;
	}

	ret = recv(sock->sock, data, *length, 0);
	if (ret > 0) {
		*length = (size_t)ret;
		return true;
	}

	return false;
}

bool hdhomerun_sock_recvfrom(struct hdhomerun_sock_t *sock, uint32_t *remote_addr, uint16_t *remote_port, void *data, size_t *length, uint64_t timeout)
{
	struct sockaddr_in sock_addr;
	memset(&sock_addr, 0, sizeof(sock_addr));
	socklen_t sockaddr_size = sizeof(sock_addr);

	ssize_t ret = recvfrom(sock->sock, data, *length, 0, (struct sockaddr *)&sock_addr, &sockaddr_size);
	if (ret > 0) {
		*remote_addr = ntohl(sock_addr.sin_addr.s_addr);
		*remote_port = ntohs(sock_addr.sin_port);
		*length = (size_t)ret;
		return true;
	}

	if (ret == 0) {
		return false;
	}
	if ((errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
		return false;
	}

	struct pollfd poll_event;
	poll_event.fd = sock->sock;
	poll_event.events = POLLIN;
	poll_event.revents = 0;

	if (poll(&poll_event, 1, (int)timeout) <= 0) {
		return false;
	}

	if ((poll_event.revents & POLLIN) == 0) {
		return false;
	}

	ret = recvfrom(sock->sock, data, *length, 0, (struct sockaddr *)&sock_addr, &sockaddr_size);
	if (ret > 0) {
		*remote_addr = ntohl(sock_addr.sin_addr.s_addr);
		*remote_port = ntohs(sock_addr.sin_port);
		*length = (size_t)ret;
		return true;
	}

	return false;
}
