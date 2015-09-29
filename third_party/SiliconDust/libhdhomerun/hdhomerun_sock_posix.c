/*
 * hdhomerun_sock_posix.c
 *
 * Copyright © 2010 Silicondust USA Inc. <www.silicondust.com>.
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

/*
 * Implementation notes:
 *
 * API specifies timeout for each operation (or zero for non-blocking).
 *
 * It is not possible to rely on the OS socket timeout as this will fail to
 * detect the command-response situation where data is sent successfully and
 * the other end chooses not to send a response (other than the TCP ack).
 *
 * The select() cannot be used with high socket numbers (typically max 1024)
 * so the code works as follows:
 * - Use non-blocking sockets to allow operation without select.
 * - Use select where safe (low socket numbers).
 * - Poll with short sleep when select cannot be used safely.
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

int hdhomerun_local_ip_info(struct hdhomerun_local_ip_info_t ip_info_list[], int max_count)
{
	int sock = socket(AF_INET, SOCK_DGRAM, 0);
	if (sock == HDHOMERUN_SOCK_INVALID) {
		return -1;
	}

	struct ifconf ifc;
	size_t ifreq_buffer_size = 1024;

	while (1) {
		ifc.ifc_len = ifreq_buffer_size;
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

		if (ifc.ifc_len < ifreq_buffer_size) {
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

hdhomerun_sock_t hdhomerun_sock_create_udp(void)
{
	/* Create socket. */
	hdhomerun_sock_t sock = (hdhomerun_sock_t)socket(AF_INET, SOCK_DGRAM, 0);
	if (sock == -1) {
		return HDHOMERUN_SOCK_INVALID;
	}

	/* Set non-blocking */
	if (fcntl(sock, F_SETFL, O_NONBLOCK) != 0) {
		close(sock);
		return HDHOMERUN_SOCK_INVALID;
	}

	/* Allow broadcast. */
	int sock_opt = 1;
	setsockopt(sock, SOL_SOCKET, SO_BROADCAST, (char *)&sock_opt, sizeof(sock_opt));

	/* Success. */
	return sock;
}

hdhomerun_sock_t hdhomerun_sock_create_tcp(void)
{
	/* Create socket. */
	hdhomerun_sock_t sock = (hdhomerun_sock_t)socket(AF_INET, SOCK_STREAM, 0);
	if (sock == -1) {
		return HDHOMERUN_SOCK_INVALID;
	}

	/* Set non-blocking */
	if (fcntl(sock, F_SETFL, O_NONBLOCK) != 0) {
		close(sock);
		return HDHOMERUN_SOCK_INVALID;
	}

	/* Success. */
	return sock;
}

void hdhomerun_sock_destroy(hdhomerun_sock_t sock)
{
	close(sock);
}

int hdhomerun_sock_getlasterror(void)
{
	return errno;
}

uint32_t hdhomerun_sock_getsockname_addr(hdhomerun_sock_t sock)
{
	struct sockaddr_in sock_addr;
	socklen_t sockaddr_size = sizeof(sock_addr);

	if (getsockname(sock, (struct sockaddr *)&sock_addr, &sockaddr_size) != 0) {
		return 0;
	}

	return ntohl(sock_addr.sin_addr.s_addr);
}

uint16_t hdhomerun_sock_getsockname_port(hdhomerun_sock_t sock)
{
	struct sockaddr_in sock_addr;
	socklen_t sockaddr_size = sizeof(sock_addr);

	if (getsockname(sock, (struct sockaddr *)&sock_addr, &sockaddr_size) != 0) {
		return 0;
	}

	return ntohs(sock_addr.sin_port);
}

uint32_t hdhomerun_sock_getpeername_addr(hdhomerun_sock_t sock)
{
	struct sockaddr_in sock_addr;
	socklen_t sockaddr_size = sizeof(sock_addr);

	if (getpeername(sock, (struct sockaddr *)&sock_addr, &sockaddr_size) != 0) {
		return 0;
	}

	return ntohl(sock_addr.sin_addr.s_addr);
}

uint32_t hdhomerun_sock_getaddrinfo_addr(hdhomerun_sock_t sock, const char *name)
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

bool_t hdhomerun_sock_join_multicast_group(hdhomerun_sock_t sock, uint32_t multicast_ip, uint32_t local_ip)
{
	struct ip_mreq imr;
	memset(&imr, 0, sizeof(imr));
	imr.imr_multiaddr.s_addr  = htonl(multicast_ip);
	imr.imr_interface.s_addr  = htonl(local_ip);

	if (setsockopt(sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, (const char *)&imr, sizeof(imr)) != 0) {
		return FALSE;
	}

	return TRUE;
}

bool_t hdhomerun_sock_leave_multicast_group(hdhomerun_sock_t sock, uint32_t multicast_ip, uint32_t local_ip)
{
	struct ip_mreq imr;
	memset(&imr, 0, sizeof(imr));
	imr.imr_multiaddr.s_addr  = htonl(multicast_ip);
	imr.imr_interface.s_addr  = htonl(local_ip);

	if (setsockopt(sock, IPPROTO_IP, IP_DROP_MEMBERSHIP, (const char *)&imr, sizeof(imr)) != 0) {
		return FALSE;
	}

	return TRUE;
}

bool_t hdhomerun_sock_bind(hdhomerun_sock_t sock, uint32_t local_addr, uint16_t local_port, bool_t allow_reuse)
{
	int sock_opt = allow_reuse;
	setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (char *)&sock_opt, sizeof(sock_opt));

	struct sockaddr_in sock_addr;
	memset(&sock_addr, 0, sizeof(sock_addr));
	sock_addr.sin_family = AF_INET;
	sock_addr.sin_addr.s_addr = htonl(local_addr);
	sock_addr.sin_port = htons(local_port);

	if (bind(sock, (struct sockaddr *)&sock_addr, sizeof(sock_addr)) != 0) {
		return FALSE;
	}

	return TRUE;
}

static bool_t hdhomerun_sock_wait_for_event(hdhomerun_sock_t sock, short event_type, uint64_t stop_time)
{
	uint64_t current_time = getcurrenttime();
	if (current_time >= stop_time) {
		return FALSE;
	}

	struct pollfd poll_event;
	poll_event.fd = sock;
	poll_event.events = event_type;
	poll_event.revents = 0;

	uint64_t timeout = stop_time - current_time;

	if (poll(&poll_event, 1, (int)timeout) <= 0) {
		return FALSE;
	}

	if ((poll_event.revents & event_type) == 0) {
		return FALSE;
	}

	return TRUE;
}

bool_t hdhomerun_sock_connect(hdhomerun_sock_t sock, uint32_t remote_addr, uint16_t remote_port, uint64_t timeout)
{
	struct sockaddr_in sock_addr;
	memset(&sock_addr, 0, sizeof(sock_addr));
	sock_addr.sin_family = AF_INET;
	sock_addr.sin_addr.s_addr = htonl(remote_addr);
	sock_addr.sin_port = htons(remote_port);

	if (connect(sock, (struct sockaddr *)&sock_addr, sizeof(sock_addr)) != 0) {
		if ((errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
			return FALSE;
		}
	}

	uint64_t stop_time = getcurrenttime() + timeout;
	return hdhomerun_sock_wait_for_event(sock, POLLOUT, stop_time);
}

bool_t hdhomerun_sock_send(hdhomerun_sock_t sock, const void *data, size_t length, uint64_t timeout)
{
	uint64_t stop_time = getcurrenttime() + timeout;
	const uint8_t *ptr = (const uint8_t *)data;

	while (1) {
		int ret = send(sock, ptr, length, MSG_NOSIGNAL);
		if (ret <= 0) {
			if ((errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
				return FALSE;
			}
			if (!hdhomerun_sock_wait_for_event(sock, POLLOUT, stop_time)) {
				return FALSE;
			}
			continue;
		}

		if (ret < (int)length) {
			ptr += ret;
			length -= ret;
			continue;
		}

		return TRUE;
	}
}

bool_t hdhomerun_sock_sendto(hdhomerun_sock_t sock, uint32_t remote_addr, uint16_t remote_port, const void *data, size_t length, uint64_t timeout)
{
	uint64_t stop_time = getcurrenttime() + timeout;
	const uint8_t *ptr = (const uint8_t *)data;

	while (1) {
		struct sockaddr_in sock_addr;
		memset(&sock_addr, 0, sizeof(sock_addr));
		sock_addr.sin_family = AF_INET;
		sock_addr.sin_addr.s_addr = htonl(remote_addr);
		sock_addr.sin_port = htons(remote_port);

		int ret = sendto(sock, ptr, length, 0, (struct sockaddr *)&sock_addr, sizeof(sock_addr));
		if (ret <= 0) {
			if ((errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
				return FALSE;
			}
			if (!hdhomerun_sock_wait_for_event(sock, POLLOUT, stop_time)) {
				return FALSE;
			}
			continue;
		}

		if (ret < (int)length) {
			ptr += ret;
			length -= ret;
			continue;
		}

		return TRUE;
	}
}

bool_t hdhomerun_sock_recv(hdhomerun_sock_t sock, void *data, size_t *length, uint64_t timeout)
{
	uint64_t stop_time = getcurrenttime() + timeout;

	while (1) {
		int ret = recv(sock, data, *length, 0);
		if (ret < 0) {
			if ((errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
				return FALSE;
			}
			if (!hdhomerun_sock_wait_for_event(sock, POLLIN, stop_time)) {
				return FALSE;
			}
			continue;
		}

		if (ret == 0) {
			return FALSE;
		}

		*length = ret;
		return TRUE;
	}
}

bool_t hdhomerun_sock_recvfrom(hdhomerun_sock_t sock, uint32_t *remote_addr, uint16_t *remote_port, void *data, size_t *length, uint64_t timeout)
{
	uint64_t stop_time = getcurrenttime() + timeout;

	while (1) {
		struct sockaddr_in sock_addr;
		memset(&sock_addr, 0, sizeof(sock_addr));
		socklen_t sockaddr_size = sizeof(sock_addr);

		int ret = recvfrom(sock, data, *length, 0, (struct sockaddr *)&sock_addr, &sockaddr_size);
		if (ret < 0) {
			if ((errno != EAGAIN) && (errno != EWOULDBLOCK) && (errno != EINPROGRESS)) {
				return FALSE;
			}
			if (!hdhomerun_sock_wait_for_event(sock, POLLIN, stop_time)) {
				return FALSE;
			}
			continue;
		}

		if (ret == 0) {
			return FALSE;
		}

		*remote_addr = ntohl(sock_addr.sin_addr.s_addr);
		*remote_port = ntohs(sock_addr.sin_port);
		*length = ret;
		return TRUE;
	}
}
