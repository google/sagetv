/*
 * hdhomerun_video.c
 *
 * Copyright © 2006-2016 Silicondust USA Inc. <www.silicondust.com>.
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

struct hdhomerun_video_sock_t {
	thread_mutex_t lock;
	struct hdhomerun_debug_t *dbg;
	struct hdhomerun_sock_t *sock;

	uint32_t keepalive_lockkey;
	uint32_t keepalive_addr;
	uint16_t keepalive_port;
	volatile bool keepalive_start;

	volatile size_t head;
	volatile size_t tail;
	uint8_t *buffer;
	size_t buffer_size;
	size_t advance;

	thread_task_t thread;
	volatile bool terminate;

	volatile uint32_t packet_count;
	volatile uint32_t transport_error_count;
	volatile uint32_t network_error_count;
	volatile uint32_t sequence_error_count;
	volatile uint32_t overflow_error_count;

	volatile uint32_t rtp_sequence;
	volatile uint8_t sequence[0x2000];
};

static void hdhomerun_video_thread_execute(void *arg);

struct hdhomerun_video_sock_t *hdhomerun_video_create(uint16_t listen_port, bool allow_port_reuse, size_t buffer_size, struct hdhomerun_debug_t *dbg)
{
	/* Create object. */
	struct hdhomerun_video_sock_t *vs = (struct hdhomerun_video_sock_t *)calloc(1, sizeof(struct hdhomerun_video_sock_t));
	if (!vs) {
		hdhomerun_debug_printf(dbg, "hdhomerun_video_create: failed to allocate video object\n");
		return NULL;
	}

	vs->dbg = dbg;
	thread_mutex_init(&vs->lock);

	/* Reset sequence tracking. */
	hdhomerun_video_flush(vs);

	/* Buffer size. */
	vs->buffer_size = (buffer_size / VIDEO_DATA_PACKET_SIZE) * VIDEO_DATA_PACKET_SIZE;
	if (vs->buffer_size == 0) {
		hdhomerun_debug_printf(dbg, "hdhomerun_video_create: invalid buffer size (%lu bytes)\n", (unsigned long)buffer_size);
		goto error;
	}
	vs->buffer_size += VIDEO_DATA_PACKET_SIZE;

	/* Create buffer. */
	vs->buffer = (uint8_t *)malloc(vs->buffer_size);
	if (!vs->buffer) {
		hdhomerun_debug_printf(dbg, "hdhomerun_video_create: failed to allocate buffer (%lu bytes)\n", (unsigned long)vs->buffer_size);
		goto error;
	}
	
	/* Create socket. */
	vs->sock = hdhomerun_sock_create_udp();
	if (!vs->sock) {
		hdhomerun_debug_printf(dbg, "hdhomerun_video_create: failed to allocate socket\n");
		goto error;
	}

	/* Expand socket buffer size. */
	hdhomerun_sock_set_recv_buffer_size(vs->sock, 1024 * 1024);

	/* Bind socket. */
	if (!hdhomerun_sock_bind(vs->sock, INADDR_ANY, listen_port, allow_port_reuse)) {
		hdhomerun_debug_printf(dbg, "hdhomerun_video_create: failed to bind socket (port %u)\n", listen_port);
		goto error;
	}

	/* Start thread. */
	if (!thread_task_create(&vs->thread, &hdhomerun_video_thread_execute, vs)) {
		hdhomerun_debug_printf(dbg, "hdhomerun_video_create: failed to start thread\n");
		goto error;
	}

	/* Success. */
	return vs;

error:
	if (vs->sock) {
		hdhomerun_sock_destroy(vs->sock);
	}

	if (vs->buffer) {
		free(vs->buffer);
	}

	thread_mutex_dispose(&vs->lock);

	free(vs);
	return NULL;
}

void hdhomerun_video_destroy(struct hdhomerun_video_sock_t *vs)
{
	vs->terminate = true;
	thread_task_join(vs->thread);

	hdhomerun_sock_destroy(vs->sock);
	thread_mutex_dispose(&vs->lock);
	free(vs->buffer);

	free(vs);
}

void hdhomerun_video_set_keepalive(struct hdhomerun_video_sock_t *vs, uint32_t remote_addr, uint16_t remote_port, uint32_t lockkey)
{
	thread_mutex_lock(&vs->lock);

	vs->keepalive_addr = remote_addr;
	vs->keepalive_port = remote_port;
	vs->keepalive_lockkey = lockkey;

	if ((remote_addr != 0) && (remote_port != 0)) {
		vs->keepalive_start = true;
	}

	thread_mutex_unlock(&vs->lock);
}

struct hdhomerun_sock_t *hdhomerun_video_get_sock(struct hdhomerun_video_sock_t *vs)
{
	return vs->sock;
}

uint16_t hdhomerun_video_get_local_port(struct hdhomerun_video_sock_t *vs)
{
	uint16_t port = hdhomerun_sock_getsockname_port(vs->sock);
	if (port == 0) {
		hdhomerun_debug_printf(vs->dbg, "hdhomerun_video_get_local_port: getsockname failed (%d)\n", hdhomerun_sock_getlasterror());
		return 0;
	}

	return port;
}

int hdhomerun_video_join_multicast_group(struct hdhomerun_video_sock_t *vs, uint32_t multicast_ip, uint32_t local_ip)
{
	if (!hdhomerun_sock_join_multicast_group(vs->sock, multicast_ip, local_ip)) {
		hdhomerun_debug_printf(vs->dbg, "hdhomerun_video_join_multicast_group: setsockopt failed (%d)\n", hdhomerun_sock_getlasterror());
		return -1;
	}

	return 1;
}

void hdhomerun_video_leave_multicast_group(struct hdhomerun_video_sock_t *vs, uint32_t multicast_ip, uint32_t local_ip)
{
	if (!hdhomerun_sock_leave_multicast_group(vs->sock, multicast_ip, local_ip)) {
		hdhomerun_debug_printf(vs->dbg, "hdhomerun_video_leave_multicast_group: setsockopt failed (%d)\n", hdhomerun_sock_getlasterror());
	}
}

static void hdhomerun_video_stats_ts_pkt(struct hdhomerun_video_sock_t *vs, uint8_t *ptr)
{
	uint16_t packet_identifier = ((uint16_t)(ptr[1] & 0x1F) << 8) | (uint16_t)ptr[2];
	if (packet_identifier == 0x1FFF) {
		return;
	}

	bool transport_error = ptr[1] >> 7;
	if (transport_error) {
		vs->transport_error_count++;
		vs->sequence[packet_identifier] = 0xFF;
		return;
	}

	uint8_t sequence = ptr[3] & 0x0F;

	uint8_t previous_sequence = vs->sequence[packet_identifier];
	vs->sequence[packet_identifier] = sequence;

	if (previous_sequence == 0xFF) {
		return;
	}
	if (sequence == ((previous_sequence + 1) & 0x0F)) {
		return;
	}
	if (sequence == previous_sequence) {
		return;
	}

	vs->sequence_error_count++;
}

static void hdhomerun_video_parse_rtp(struct hdhomerun_video_sock_t *vs, struct hdhomerun_pkt_t *pkt)
{
	pkt->pos += 2;
	uint32_t rtp_sequence = hdhomerun_pkt_read_u16(pkt);
	pkt->pos += 8;

	uint32_t previous_rtp_sequence = vs->rtp_sequence;
	vs->rtp_sequence = rtp_sequence;

	/* Initial case - first packet received. */
	if (previous_rtp_sequence == 0xFFFFFFFF) {
		return;
	}

	/* Normal case - next sequence number. */
	if (rtp_sequence == ((previous_rtp_sequence + 1) & 0xFFFF)) {
		return;
	}

	/* Error case - sequence missed. */
	vs->network_error_count++;

	/* Restart pid sequence check after packet loss. */
	int i;
	for (i = 0; i < 0x2000; i++) {
		vs->sequence[i] = 0xFF;
	}
}

static void hdhomerun_video_thread_send_keepalive(struct hdhomerun_video_sock_t *vs)
{
	thread_mutex_lock(&vs->lock);
	uint32_t keepalive_lockkey = vs->keepalive_lockkey;
	uint32_t keepalive_addr = vs->keepalive_addr;
	uint16_t keepalive_port = vs->keepalive_port;
	vs->keepalive_start = false;
	thread_mutex_unlock(&vs->lock);

	if ((keepalive_addr == 0) || (keepalive_port == 0)) {
		return;
	}

	struct hdhomerun_pkt_t pkt;
	hdhomerun_pkt_reset(&pkt);
	hdhomerun_pkt_write_u32(&pkt, keepalive_lockkey);
	hdhomerun_sock_sendto(vs->sock, keepalive_addr, keepalive_port, pkt.start, pkt.end - pkt.start, 25);
}

static void hdhomerun_video_thread_execute(void *arg)
{
	struct hdhomerun_video_sock_t *vs = (struct hdhomerun_video_sock_t *)arg;
	uint64_t send_time = getcurrenttime();

	while (!vs->terminate) {
		uint64_t current_time = getcurrenttime();
		if (vs->keepalive_start || (current_time >= send_time)) {
			hdhomerun_video_thread_send_keepalive(vs);
			send_time = current_time + 1000;
		}

		/* Receive. */
		struct hdhomerun_pkt_t pkt;
		hdhomerun_pkt_reset(&pkt);

		size_t length = VIDEO_RTP_DATA_PACKET_SIZE;
		if (!hdhomerun_sock_recv(vs->sock, pkt.end, &length, 25)) {
			continue;
		}

		pkt.end += length;

		if (length == VIDEO_RTP_DATA_PACKET_SIZE) {
			hdhomerun_video_parse_rtp(vs, &pkt);
			length = pkt.end - pkt.pos;
		}

		if (length != VIDEO_DATA_PACKET_SIZE) {
			/* Data received but not valid - ignore. */
			continue;
		}

		thread_mutex_lock(&vs->lock);

		/* Store in ring buffer. */
		size_t head = vs->head;
		uint8_t *ptr = vs->buffer + head;
		memcpy(ptr, pkt.pos, length);

		/* Stats. */
		vs->packet_count++;
		hdhomerun_video_stats_ts_pkt(vs, ptr + TS_PACKET_SIZE * 0);
		hdhomerun_video_stats_ts_pkt(vs, ptr + TS_PACKET_SIZE * 1);
		hdhomerun_video_stats_ts_pkt(vs, ptr + TS_PACKET_SIZE * 2);
		hdhomerun_video_stats_ts_pkt(vs, ptr + TS_PACKET_SIZE * 3);
		hdhomerun_video_stats_ts_pkt(vs, ptr + TS_PACKET_SIZE * 4);
		hdhomerun_video_stats_ts_pkt(vs, ptr + TS_PACKET_SIZE * 5);
		hdhomerun_video_stats_ts_pkt(vs, ptr + TS_PACKET_SIZE * 6);

		/* Calculate new head. */
		head += length;
		if (head >= vs->buffer_size) {
			head -= vs->buffer_size;
		}

		/* Check for buffer overflow. */
		if (head == vs->tail) {
			vs->overflow_error_count++;
			thread_mutex_unlock(&vs->lock);
			continue;
		}

		vs->head = head;

		thread_mutex_unlock(&vs->lock);
	}
}

uint8_t *hdhomerun_video_recv(struct hdhomerun_video_sock_t *vs, size_t max_size, size_t *pactual_size)
{
	thread_mutex_lock(&vs->lock);

	size_t head = vs->head;
	size_t tail = vs->tail;

	if (vs->advance > 0) {
		tail += vs->advance;
		if (tail >= vs->buffer_size) {
			tail -= vs->buffer_size;
		}
	
		vs->tail = tail;
	}

	if (head == tail) {
		vs->advance = 0;
		*pactual_size = 0;
		thread_mutex_unlock(&vs->lock);
		return NULL;
	}

	size_t size = (max_size / VIDEO_DATA_PACKET_SIZE) * VIDEO_DATA_PACKET_SIZE;
	if (size == 0) {
		vs->advance = 0;
		*pactual_size = 0;
		thread_mutex_unlock(&vs->lock);
		return NULL;
	}

	size_t avail;
	if (head > tail) {
		avail = head - tail;
	} else {
		avail = vs->buffer_size - tail;
	}
	if (size > avail) {
		size = avail;
	}
	vs->advance = size;
	*pactual_size = size;
	uint8_t *result = vs->buffer + tail;

	thread_mutex_unlock(&vs->lock);
	return result;
}

void hdhomerun_video_flush(struct hdhomerun_video_sock_t *vs)
{
	thread_mutex_lock(&vs->lock);

	vs->tail = vs->head;
	vs->advance = 0;

	vs->rtp_sequence = 0xFFFFFFFF;

	int i;
	for (i = 0; i < 0x2000; i++) {
		vs->sequence[i] = 0xFF;
	}

	vs->packet_count = 0;
	vs->transport_error_count = 0;
	vs->network_error_count = 0;
	vs->sequence_error_count = 0;
	vs->overflow_error_count = 0;

	thread_mutex_unlock(&vs->lock);
}

void hdhomerun_video_debug_print_stats(struct hdhomerun_video_sock_t *vs)
{
	struct hdhomerun_video_stats_t stats;
	hdhomerun_video_get_stats(vs, &stats);

	hdhomerun_debug_printf(vs->dbg, "video sock: pkt=%u net=%u te=%u miss=%u drop=%u\n",
		(unsigned int)stats.packet_count, (unsigned int)stats.network_error_count,
		(unsigned int)stats.transport_error_count, (unsigned int)stats.sequence_error_count,
		(unsigned int)stats.overflow_error_count
	);
}

void hdhomerun_video_get_stats(struct hdhomerun_video_sock_t *vs, struct hdhomerun_video_stats_t *stats)
{
	memset(stats, 0, sizeof(struct hdhomerun_video_stats_t));

	thread_mutex_lock(&vs->lock);

	stats->packet_count = vs->packet_count;
	stats->network_error_count = vs->network_error_count;
	stats->transport_error_count = vs->transport_error_count;
	stats->sequence_error_count = vs->sequence_error_count;
	stats->overflow_error_count = vs->overflow_error_count;

	thread_mutex_unlock(&vs->lock);
}
