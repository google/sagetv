/*
 * hdhomerun_sock_windows.c
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
#include <iphlpapi.h>

struct hdhomerun_sock_t {
	SOCKET sock;
	HANDLE event;
	long events_selected;
};

#if defined(_WINRT)
static char *hdhomerun_local_ip_info_str = NULL;

/*
 * String format: ip address '/' subnet mask bits <space> ...
 * Example: "192.168.0.100/24 169.254.0.100/16"
 */
void hdhomerun_local_ip_info_set_str(const char *ip_info_str)
{
	if (hdhomerun_local_ip_info_str) {
		free(hdhomerun_local_ip_info_str);
	}

	hdhomerun_local_ip_info_str = strdup(ip_info_str);
}

int hdhomerun_local_ip_info(struct hdhomerun_local_ip_info_t ip_info_list[], int max_count)
{
	const char *ptr = hdhomerun_local_ip_info_str;
	if (!ptr) {
		return 0;
	}

	struct hdhomerun_local_ip_info_t *ip_info = ip_info_list;
	int count = 0;

	while (count < max_count) {
		unsigned int a[4];
		unsigned int mask_bitcount;
		if (sscanf(ptr, "%u.%u.%u.%u/%u", &a[0], &a[1], &a[2], &a[3], &mask_bitcount) != 5) {
			break;
		}

		ip_info->ip_addr = (uint32_t)((a[0] << 24) | (a[1] << 16) | (a[2] << 8) | (a[3] << 0));
		ip_info->subnet_mask = 0xFFFFFFFF << (32 - mask_bitcount);
		ip_info++;

		count++;

		ptr = strchr(ptr, ' ');
		if (!ptr) {
			break;
		}

		ptr++;
	}

	return count;
}
#endif

#if !defined(_WINRT)
int hdhomerun_local_ip_info(struct hdhomerun_local_ip_info_t ip_info_list[], int max_count)
{
	PIP_ADAPTER_INFO AdapterInfo;
	ULONG AdapterInfoLength = sizeof(IP_ADAPTER_INFO) * 16;

	while (1) {
		AdapterInfo = (IP_ADAPTER_INFO *)malloc(AdapterInfoLength);
		if (!AdapterInfo) {
			return -1;
		}

		ULONG LengthNeeded = AdapterInfoLength;
		DWORD Ret = GetAdaptersInfo(AdapterInfo, &LengthNeeded);
		if (Ret == NO_ERROR) {
			break;
		}

		free(AdapterInfo);

		if (Ret != ERROR_BUFFER_OVERFLOW) {
			return -1;
		}
		if (AdapterInfoLength >= LengthNeeded) {
			return -1;
		}

		AdapterInfoLength = LengthNeeded;
	}

	int count = 0;
	PIP_ADAPTER_INFO Adapter = AdapterInfo;
	while (Adapter) {
		IP_ADDR_STRING *IPAddr = &Adapter->IpAddressList;
		while (IPAddr) {
			uint32_t ip_addr = ntohl(inet_addr(IPAddr->IpAddress.String));
			uint32_t subnet_mask = ntohl(inet_addr(IPAddr->IpMask.String));

			if (ip_addr == 0) {
				IPAddr = IPAddr->Next;
				continue;
			}

			if (count < max_count) {
				struct hdhomerun_local_ip_info_t *ip_info = &ip_info_list[count];
				ip_info->ip_addr = ip_addr;
				ip_info->subnet_mask = subnet_mask;
			}

			count++;
			IPAddr = IPAddr->Next;
		}

		if (count >= max_count) {
			break;
		}

		Adapter = Adapter->Next;
	}

	free(AdapterInfo);
	return count;
}
#endif

static struct hdhomerun_sock_t *hdhomerun_sock_create_internal(int protocol)
{
	struct hdhomerun_sock_t *sock = (struct hdhomerun_sock_t *)calloc(1, sizeof(struct hdhomerun_sock_t));
	if (!sock) {
		return NULL;
	}

	/* Create socket. */
	sock->sock = socket(AF_INET, protocol, 0);
	if (sock->sock == INVALID_SOCKET) {
		free(sock);
		return NULL;
	}

	/* Set non-blocking */
	unsigned long mode = 1;
	if (ioctlsocket(sock->sock, FIONBIO, &mode) != 0) {
		hdhomerun_sock_destroy(sock);
		return NULL;
	}

	/* Event */
	sock->event = CreateEvent(NULL, false, false, NULL);
	if (!sock->event) {
		hdhomerun_sock_destroy(sock);
		return NULL;
	}

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
	if (sock->event) {
		CloseHandle(sock->event);
	}

	closesocket(sock->sock);
	free(sock);
}

void hdhomerun_sock_stop(struct hdhomerun_sock_t *sock)
{
	shutdown(sock->sock, SD_BOTH);
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
	return WSAGetLastError();
}

uint32_t hdhomerun_sock_getsockname_addr(struct hdhomerun_sock_t *sock)
{
	struct sockaddr_in sock_addr;
	int sockaddr_size = sizeof(sock_addr);

	if (getsockname(sock->sock, (struct sockaddr *)&sock_addr, &sockaddr_size) != 0) {
		return 0;
	}

	return ntohl(sock_addr.sin_addr.s_addr);
}

uint16_t hdhomerun_sock_getsockname_port(struct hdhomerun_sock_t *sock)
{
	struct sockaddr_in sock_addr;
	int sockaddr_size = sizeof(sock_addr);

	if (getsockname(sock->sock, (struct sockaddr *)&sock_addr, &sockaddr_size) != 0) {
		return 0;
	}

	return ntohs(sock_addr.sin_port);
}

uint32_t hdhomerun_sock_getpeername_addr(struct hdhomerun_sock_t *sock)
{
	struct sockaddr_in sock_addr;
	int sockaddr_size = sizeof(sock_addr);

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

static bool hdhomerun_sock_event_select(struct hdhomerun_sock_t *sock, long events)
{
	if (sock->events_selected != events) {
		if (WSAEventSelect(sock->sock, sock->event, events) == SOCKET_ERROR) {
			return false;
		}
		sock->events_selected = events;
	}

	ResetEvent(sock->event);
	return true;
}

bool hdhomerun_sock_connect(struct hdhomerun_sock_t *sock, uint32_t remote_addr, uint16_t remote_port, uint64_t timeout)
{
	if (!hdhomerun_sock_event_select(sock, FD_WRITE | FD_CLOSE)) {
		return false;
	}

	struct sockaddr_in sock_addr;
	memset(&sock_addr, 0, sizeof(sock_addr));
	sock_addr.sin_family = AF_INET;
	sock_addr.sin_addr.s_addr = htonl(remote_addr);
	sock_addr.sin_port = htons(remote_port);

	if (connect(sock->sock, (struct sockaddr *)&sock_addr, sizeof(sock_addr)) != 0) {
		if (WSAGetLastError() != WSAEWOULDBLOCK) {
			return false;
		}
	}

	DWORD wait_ret = WaitForSingleObjectEx(sock->event, (DWORD)timeout, false);
	if (wait_ret != WAIT_OBJECT_0) {
		return false;
	}

	WSANETWORKEVENTS network_events;
	if (WSAEnumNetworkEvents(sock->sock, sock->event, &network_events) == SOCKET_ERROR) {
		return false;
	}
	if ((network_events.lNetworkEvents & FD_WRITE) == 0) {
		return false;
	}
	if (network_events.lNetworkEvents & FD_CLOSE) {
		return false;
	}

	return true;
}

bool hdhomerun_sock_send(struct hdhomerun_sock_t *sock, const void *data, size_t length, uint64_t timeout)
{
	if (!hdhomerun_sock_event_select(sock, FD_WRITE | FD_CLOSE)) {
		return false;
	}

	uint64_t stop_time = getcurrenttime() + timeout;
	const uint8_t *ptr = (uint8_t *)data;

	while (1) {
		int ret = send(sock->sock, (char *)ptr, (int)length, 0);
		if (ret >= (int)length) {
			return true;
		}

		if ((ret == SOCKET_ERROR) && (WSAGetLastError() != WSAEWOULDBLOCK)) {
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

		if (WaitForSingleObjectEx(sock->event, (DWORD)(stop_time - current_time), false) != WAIT_OBJECT_0) {
			return false;
		}
	}
}

bool hdhomerun_sock_sendto(struct hdhomerun_sock_t *sock, uint32_t remote_addr, uint16_t remote_port, const void *data, size_t length, uint64_t timeout)
{
	if (!hdhomerun_sock_event_select(sock, FD_WRITE | FD_CLOSE)) {
		return false;
	}

	struct sockaddr_in sock_addr;
	memset(&sock_addr, 0, sizeof(sock_addr));
	sock_addr.sin_family = AF_INET;
	sock_addr.sin_addr.s_addr = htonl(remote_addr);
	sock_addr.sin_port = htons(remote_port);

	int ret = sendto(sock->sock, (char *)data, (int)length, 0, (struct sockaddr *)&sock_addr, sizeof(sock_addr));
	if (ret >= (int)length) {
		return true;
	}

	if (ret >= 0) {
		return false;
	}
	if (WSAGetLastError() != WSAEWOULDBLOCK) {
		return false;
	}

	if (WaitForSingleObjectEx(sock->event, (DWORD)timeout, false) != WAIT_OBJECT_0) {
		return false;
	}

	ret = sendto(sock->sock, (char *)data, (int)length, 0, (struct sockaddr *)&sock_addr, sizeof(sock_addr));
	if (ret >= (int)length) {
		return true;
	}

	return false;
}

bool hdhomerun_sock_recv(struct hdhomerun_sock_t *sock, void *data, size_t *length, uint64_t timeout)
{
	if (!hdhomerun_sock_event_select(sock, FD_READ | FD_CLOSE)) {
		return false;
	}

	int ret = recv(sock->sock, (char *)data, (int)(*length), 0);
	if (ret > 0) {
		*length = ret;
		return true;
	}

	if (ret == 0) {
		return false;
	}
	if (WSAGetLastError() != WSAEWOULDBLOCK) {
		return false;
	}

	if (WaitForSingleObjectEx(sock->event, (DWORD)timeout, false) != WAIT_OBJECT_0) {
		return false;
	}

	ret = recv(sock->sock, (char *)data, (int)(*length), 0);
	if (ret > 0) {
		*length = ret;
		return true;
	}

	return false;
}

bool hdhomerun_sock_recvfrom(struct hdhomerun_sock_t *sock, uint32_t *remote_addr, uint16_t *remote_port, void *data, size_t *length, uint64_t timeout)
{
	if (!hdhomerun_sock_event_select(sock, FD_READ | FD_CLOSE)) {
		return false;
	}

	struct sockaddr_in sock_addr;
	memset(&sock_addr, 0, sizeof(sock_addr));
	int sockaddr_size = sizeof(sock_addr);

	int ret = recvfrom(sock->sock, (char *)data, (int)(*length), 0, (struct sockaddr *)&sock_addr, &sockaddr_size);
	if (ret > 0) {
		*remote_addr = ntohl(sock_addr.sin_addr.s_addr);
		*remote_port = ntohs(sock_addr.sin_port);
		*length = ret;
		return true;
	}

	if (ret == 0) {
		return false;
	}
	if (WSAGetLastError() != WSAEWOULDBLOCK) {
		return false;
	}

	if (WaitForSingleObjectEx(sock->event, (DWORD)timeout, false) != WAIT_OBJECT_0) {
		return false;
	}

	ret = recvfrom(sock->sock, (char *)data, (int)(*length), 0, (struct sockaddr *)&sock_addr, &sockaddr_size);
	if (ret > 0) {
		*remote_addr = ntohl(sock_addr.sin_addr.s_addr);
		*remote_port = ntohs(sock_addr.sin_port);
		*length = ret;
		return true;
	}

	return false;
}
