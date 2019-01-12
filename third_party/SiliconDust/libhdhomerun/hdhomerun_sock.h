/*
 * hdhomerun_sock.h
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
#ifdef __cplusplus
extern "C" {
#endif

struct hdhomerun_local_ip_info_t {
	uint32_t ip_addr;
	uint32_t subnet_mask;
};

extern LIBHDHOMERUN_API int hdhomerun_local_ip_info(struct hdhomerun_local_ip_info_t ip_info_list[], int max_count);
extern LIBHDHOMERUN_API void hdhomerun_local_ip_info_set_str(const char *ip_info_str); /* WinRT only */

struct hdhomerun_sock_t;

extern LIBHDHOMERUN_API struct hdhomerun_sock_t *hdhomerun_sock_create_udp(void);
extern LIBHDHOMERUN_API struct hdhomerun_sock_t *hdhomerun_sock_create_tcp(void);
extern LIBHDHOMERUN_API void hdhomerun_sock_stop(struct hdhomerun_sock_t *sock);
extern LIBHDHOMERUN_API void hdhomerun_sock_destroy(struct hdhomerun_sock_t *sock);

extern LIBHDHOMERUN_API void hdhomerun_sock_set_send_buffer_size(struct hdhomerun_sock_t *sock, size_t size);
extern LIBHDHOMERUN_API void hdhomerun_sock_set_recv_buffer_size(struct hdhomerun_sock_t *sock, size_t size);
extern LIBHDHOMERUN_API void hdhomerun_sock_set_allow_reuse(struct hdhomerun_sock_t *sock);

extern LIBHDHOMERUN_API int hdhomerun_sock_getlasterror(void);
extern LIBHDHOMERUN_API uint32_t hdhomerun_sock_getsockname_addr(struct hdhomerun_sock_t *sock);
extern LIBHDHOMERUN_API uint16_t hdhomerun_sock_getsockname_port(struct hdhomerun_sock_t *sock);
extern LIBHDHOMERUN_API uint32_t hdhomerun_sock_getpeername_addr(struct hdhomerun_sock_t *sock);
extern LIBHDHOMERUN_API uint32_t hdhomerun_sock_getaddrinfo_addr(struct hdhomerun_sock_t *sock, const char *name);

extern LIBHDHOMERUN_API bool hdhomerun_sock_join_multicast_group(struct hdhomerun_sock_t *sock, uint32_t multicast_ip, uint32_t local_ip);
extern LIBHDHOMERUN_API bool hdhomerun_sock_leave_multicast_group(struct hdhomerun_sock_t *sock, uint32_t multicast_ip, uint32_t local_ip);

extern LIBHDHOMERUN_API bool hdhomerun_sock_bind(struct hdhomerun_sock_t *sock, uint32_t local_addr, uint16_t local_port, bool allow_reuse);
extern LIBHDHOMERUN_API bool hdhomerun_sock_connect(struct hdhomerun_sock_t *sock, uint32_t remote_addr, uint16_t remote_port, uint64_t timeout);

extern LIBHDHOMERUN_API bool hdhomerun_sock_send(struct hdhomerun_sock_t *sock, const void *data, size_t length, uint64_t timeout);
extern LIBHDHOMERUN_API bool hdhomerun_sock_sendto(struct hdhomerun_sock_t *sock, uint32_t remote_addr, uint16_t remote_port, const void *data, size_t length, uint64_t timeout);

extern LIBHDHOMERUN_API bool hdhomerun_sock_recv(struct hdhomerun_sock_t *sock, void *data, size_t *length, uint64_t timeout);
extern LIBHDHOMERUN_API bool hdhomerun_sock_recvfrom(struct hdhomerun_sock_t *sock, uint32_t *remote_addr, uint16_t *remote_port, void *data, size_t *length, uint64_t timeout);

#ifdef __cplusplus
}
#endif
