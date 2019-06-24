/*
 * hdhomerun_discover.c
 *
 * Copyright © 2006-2017 Silicondust USA Inc. <www.silicondust.com>.
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

#define HDHOMERUN_DISCOVER_MAX_SOCK_COUNT 16

struct hdhomerun_discover_sock_t {
	struct hdhomerun_sock_t *sock;
	bool detected;
	uint32_t local_ip;
	uint32_t subnet_mask;
};

struct hdhomerun_discover_t {
	struct hdhomerun_discover_sock_t socks[HDHOMERUN_DISCOVER_MAX_SOCK_COUNT];
	unsigned int sock_count;
	struct hdhomerun_pkt_t tx_pkt;
	struct hdhomerun_pkt_t rx_pkt;
	struct hdhomerun_debug_t *dbg;
};

static bool hdhomerun_discover_sock_add(struct hdhomerun_discover_t *ds, uint32_t local_ip, uint32_t subnet_mask)
{
	unsigned int i;
	for (i = 1; i < ds->sock_count; i++) {
		struct hdhomerun_discover_sock_t *dss = &ds->socks[i];

		if ((dss->local_ip == local_ip) && (dss->subnet_mask == subnet_mask)) {
			dss->detected = true;
			return true;
		}
	}

	if (ds->sock_count >= HDHOMERUN_DISCOVER_MAX_SOCK_COUNT) {
		return false;
	}

	/* Create socket. */
	struct hdhomerun_sock_t *sock = hdhomerun_sock_create_udp();
	if (!sock) {
		hdhomerun_debug_printf(ds->dbg, "discover: failed to allocate socket (%d)\n", hdhomerun_sock_getlasterror());
		return false;
	}

	/* Bind socket. */
	if (!hdhomerun_sock_bind(sock, local_ip, 0, false)) {
		hdhomerun_debug_printf(ds->dbg, "discover: failed to bind to %u.%u.%u.%u:0\n", (unsigned int)(local_ip >> 24) & 0xFF, (unsigned int)(local_ip >> 16) & 0xFF, (unsigned int)(local_ip >> 8) & 0xFF, (unsigned int)(local_ip >> 0) & 0xFF);
		hdhomerun_sock_destroy(sock);
		return false;
	}

	/* Write sock entry. */
	struct hdhomerun_discover_sock_t *dss = &ds->socks[ds->sock_count++];
	dss->sock = sock;
	dss->detected = true;
	dss->local_ip = local_ip;
	dss->subnet_mask = subnet_mask;

	return true;
}

struct hdhomerun_discover_t *hdhomerun_discover_create(struct hdhomerun_debug_t *dbg)
{
	struct hdhomerun_discover_t *ds = (struct hdhomerun_discover_t *)calloc(1, sizeof(struct hdhomerun_discover_t));
	if (!ds) {
		return NULL;
	}

	ds->dbg = dbg;

	/* Create a routable socket (always first entry). */
	if (!hdhomerun_discover_sock_add(ds, 0, 0)) {
		free(ds);
		return NULL;
	}

	/* Success. */
	return ds;
}

void hdhomerun_discover_destroy(struct hdhomerun_discover_t *ds)
{
	unsigned int i;
	for (i = 0; i < ds->sock_count; i++) {
		struct hdhomerun_discover_sock_t *dss = &ds->socks[i];
		hdhomerun_sock_destroy(dss->sock);
	}

	free(ds);
}

static void hdhomerun_discover_sock_detect(struct hdhomerun_discover_t *ds)
{
	unsigned int i;
	for (i = 1; i < ds->sock_count; i++) {
		struct hdhomerun_discover_sock_t *dss = &ds->socks[i];
		dss->detected = false;
	}

	struct hdhomerun_local_ip_info_t ip_info_list[HDHOMERUN_DISCOVER_MAX_SOCK_COUNT];
	int count = hdhomerun_local_ip_info(ip_info_list, HDHOMERUN_DISCOVER_MAX_SOCK_COUNT);
	if (count < 0) {
		hdhomerun_debug_printf(ds->dbg, "discover: hdhomerun_local_ip_info returned error\n");
		count = 0;
	}
	if (count > HDHOMERUN_DISCOVER_MAX_SOCK_COUNT) {
		hdhomerun_debug_printf(ds->dbg, "discover: too many local IP addresses\n");
		count = HDHOMERUN_DISCOVER_MAX_SOCK_COUNT;
	}

	int index;
	for (index = 0; index < count; index++) {
		struct hdhomerun_local_ip_info_t *ip_info = &ip_info_list[index];
		hdhomerun_discover_sock_add(ds, ip_info->ip_addr, ip_info->subnet_mask);
	}

	struct hdhomerun_discover_sock_t *src = &ds->socks[1];
	struct hdhomerun_discover_sock_t *dst = &ds->socks[1];
	count = 1;
	for (i = 1; i < ds->sock_count; i++) {
		if (!src->detected) {
			hdhomerun_sock_destroy(src->sock);
			src++;
			continue;
		}
		if (dst != src) {
			*dst = *src;
		}
		src++;
		dst++;
		count++;
	}

	ds->sock_count = (unsigned int)count;
}

static bool hdhomerun_discover_send_internal(struct hdhomerun_discover_t *ds, struct hdhomerun_discover_sock_t *dss, uint32_t target_ip, uint32_t device_type, uint32_t device_id)
{
	struct hdhomerun_pkt_t *tx_pkt = &ds->tx_pkt;
	hdhomerun_pkt_reset(tx_pkt);

	hdhomerun_pkt_write_u8(tx_pkt, HDHOMERUN_TAG_DEVICE_TYPE);
	hdhomerun_pkt_write_var_length(tx_pkt, 4);
	hdhomerun_pkt_write_u32(tx_pkt, device_type);
	hdhomerun_pkt_write_u8(tx_pkt, HDHOMERUN_TAG_DEVICE_ID);
	hdhomerun_pkt_write_var_length(tx_pkt, 4);
	hdhomerun_pkt_write_u32(tx_pkt, device_id);
	hdhomerun_pkt_seal_frame(tx_pkt, HDHOMERUN_TYPE_DISCOVER_REQ);

	return hdhomerun_sock_sendto(dss->sock, target_ip, HDHOMERUN_DISCOVER_UDP_PORT, tx_pkt->start, tx_pkt->end - tx_pkt->start, 0);
}

static bool hdhomerun_discover_send_wildcard_ip(struct hdhomerun_discover_t *ds, uint32_t device_type, uint32_t device_id)
{
	bool result = false;

	/*
	 * Send subnet broadcast using each local ip socket.
	 * This will work with multiple separate 169.254.x.x interfaces.
	 */
	unsigned int i;
	for (i = 1; i < ds->sock_count; i++) {
		struct hdhomerun_discover_sock_t *dss = &ds->socks[i];
		uint32_t target_ip = dss->local_ip | ~dss->subnet_mask;
		result |= hdhomerun_discover_send_internal(ds, dss, target_ip, device_type, device_id);
	}

	/*
	 * If no local ip sockets then fall back to sending a global broadcast letting the OS choose the interface.
	 */
	if (!result) {
		struct hdhomerun_discover_sock_t *dss = &ds->socks[0];
		result = hdhomerun_discover_send_internal(ds, dss, 0xFFFFFFFF, device_type, device_id);
	}

	return result;
}

static bool hdhomerun_discover_send_target_ip(struct hdhomerun_discover_t *ds, uint32_t target_ip, uint32_t device_type, uint32_t device_id)
{
	bool result = false;

	/*
	 * Send targeted packet from any local ip that is in the same subnet.
	 * This will work with multiple separate 169.254.x.x interfaces.
	 */
	unsigned int i;
	for (i = 1; i < ds->sock_count; i++) {
		struct hdhomerun_discover_sock_t *dss = &ds->socks[i];
		if (dss->subnet_mask == 0) {
			continue;
		}
		if ((target_ip & dss->subnet_mask) != (dss->local_ip & dss->subnet_mask)) {
			continue;
		}

		result |= hdhomerun_discover_send_internal(ds, dss, target_ip, device_type, device_id);
	}

	/*
	 * If target IP does not match a local subnet then fall back to letting the OS choose the gateway interface.
	 */
	if (!result) {
		struct hdhomerun_discover_sock_t *dss = &ds->socks[0];
		result = hdhomerun_discover_send_internal(ds, dss, target_ip, device_type, device_id);
	}

	return result;
}

static bool hdhomerun_discover_send(struct hdhomerun_discover_t *ds, uint32_t target_ip, uint32_t device_type, uint32_t device_id)
{
	if (target_ip == 0) {
		return hdhomerun_discover_send_wildcard_ip(ds, device_type, device_id);
	} else {
		return hdhomerun_discover_send_target_ip(ds, target_ip, device_type, device_id);
	}
}

static bool hdhomerun_discover_is_legacy(uint32_t device_id)
{
	switch (device_id >> 20) {
	case 0x100: /* TECH-US/TECH3-US */
		return (device_id < 0x10040000);

	case 0x120: /* TECH3-EU */
		return (device_id < 0x12030000);

	case 0x101: /* HDHR-US */
	case 0x102: /* HDHR-T1-US */
	case 0x103: /* HDHR3-US */
	case 0x111: /* HDHR3-DT */
	case 0x121: /* HDHR-EU */
	case 0x122: /* HDHR3-EU */
		return true;

	default:
		return false;
	}
}

static bool hdhomerun_discover_recv_internal(struct hdhomerun_discover_t *ds, struct hdhomerun_discover_sock_t *dss, struct hdhomerun_discover_device_t *result)
{
	static char hdhomerun_discover_recv_base64_encode_table[64 + 1] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
	struct hdhomerun_pkt_t *rx_pkt = &ds->rx_pkt;
	hdhomerun_pkt_reset(rx_pkt);

	uint32_t remote_addr;
	uint16_t remote_port;
	size_t length = rx_pkt->limit - rx_pkt->end;
	if (!hdhomerun_sock_recvfrom(dss->sock, &remote_addr, &remote_port, rx_pkt->end, &length, 0)) {
		return false;
	}

	rx_pkt->end += length;

	uint16_t type;
	if (hdhomerun_pkt_open_frame(rx_pkt, &type) <= 0) {
		return false;
	}
	if (type != HDHOMERUN_TYPE_DISCOVER_RPY) {
		return false;
	}

	memset(result, 0, sizeof(struct hdhomerun_discover_device_t));
	result->ip_addr = remote_addr;

	hdhomerun_sprintf(result->base_url, result->base_url + sizeof(result->base_url), "http://%u.%u.%u.%u:80",
		(remote_addr >> 24) & 0xFF, (remote_addr >> 16) & 0xFF, (remote_addr >> 8) & 0xFF, (remote_addr >> 0) & 0xFF
	);

	while (1) {
		uint8_t tag;
		size_t len;
		uint8_t *next = hdhomerun_pkt_read_tlv(rx_pkt, &tag, &len);
		if (!next) {
			break;
		}

		int i;
		switch (tag) {
		case HDHOMERUN_TAG_DEVICE_TYPE:
			if (len != 4) {
				break;
			}
			result->device_type = hdhomerun_pkt_read_u32(rx_pkt);
			break;

		case HDHOMERUN_TAG_DEVICE_ID:
			if (len != 4) {
				break;
			}
			result->device_id = hdhomerun_pkt_read_u32(rx_pkt);
			result->is_legacy = hdhomerun_discover_is_legacy(result->device_id);
			break;

		case HDHOMERUN_TAG_TUNER_COUNT:
			if (len != 1) {
				break;
			}
			result->tuner_count = hdhomerun_pkt_read_u8(rx_pkt);
			break;

		case HDHOMERUN_TAG_DEVICE_AUTH_STR:
			if (len >= sizeof(result->device_auth)) {
				break;
			}
			hdhomerun_pkt_read_mem(rx_pkt, result->device_auth, len);
			result->device_auth[len] = 0;
			break;

		case HDHOMERUN_TAG_DEVICE_AUTH_BIN:
			if (len != 18) {
				break;
			}
			for (i = 0; i < 24; i += 4) {
				uint32_t raw24;
				raw24  = (uint32_t)hdhomerun_pkt_read_u8(rx_pkt) << 16;
				raw24 |= (uint32_t)hdhomerun_pkt_read_u8(rx_pkt) << 8;
				raw24 |= (uint32_t)hdhomerun_pkt_read_u8(rx_pkt) << 0;
				result->device_auth[i + 0] = hdhomerun_discover_recv_base64_encode_table[(raw24 >> 18) & 0x3F];
				result->device_auth[i + 1] = hdhomerun_discover_recv_base64_encode_table[(raw24 >> 12) & 0x3F];
				result->device_auth[i + 2] = hdhomerun_discover_recv_base64_encode_table[(raw24 >> 6) & 0x3F];
				result->device_auth[i + 3] = hdhomerun_discover_recv_base64_encode_table[(raw24 >> 0) & 0x3F];
			}
			result->device_auth[24] = 0;
			break;

		case HDHOMERUN_TAG_BASE_URL:
			if (len >= sizeof(result->base_url)) {
				break;
			}
			hdhomerun_pkt_read_mem(rx_pkt, result->base_url, len);
			result->base_url[len] = 0;
			break;

		default:
			break;
		}

		rx_pkt->pos = next;
	}

	/* Fixup for old firmware. */
	if (result->tuner_count == 0) {
		switch (result->device_id >> 20) {
		case 0x102:
			result->tuner_count = 1;
			break;

		case 0x100:
		case 0x101:
		case 0x121:
			result->tuner_count = 2;
			break;

		default:
			break;
		}
	}

	return true;
}

static bool hdhomerun_discover_recv(struct hdhomerun_discover_t *ds, struct hdhomerun_discover_device_t *result)
{
	unsigned int i;
	for (i = 0; i < ds->sock_count; i++) {
		struct hdhomerun_discover_sock_t *dss = &ds->socks[i];

		if (hdhomerun_discover_recv_internal(ds, dss, result)) {
			return true;
		}
	}

	return false;
}

static struct hdhomerun_discover_device_t *hdhomerun_discover_find_in_list(struct hdhomerun_discover_device_t result_list[], int count, struct hdhomerun_discover_device_t *lookup)
{
	int index;
	for (index = 0; index < count; index++) {
		struct hdhomerun_discover_device_t *entry = &result_list[index];
		if (memcmp(lookup, entry, sizeof(struct hdhomerun_discover_device_t)) == 0) {
			return entry;
		}
	}

	return NULL;
}

int hdhomerun_discover_find_devices_v2(struct hdhomerun_discover_t *ds, uint32_t target_ip, uint32_t device_type, uint32_t device_id, struct hdhomerun_discover_device_t result_list[], int max_count)
{
	hdhomerun_discover_sock_detect(ds);

	int count = 0;
	int attempt;
	for (attempt = 0; attempt < 2; attempt++) {
		if (!hdhomerun_discover_send(ds, target_ip, device_type, device_id)) {
			return -1;
		}

		uint64_t timeout = getcurrenttime() + 200;
		while (1) {
			struct hdhomerun_discover_device_t *result = &result_list[count];
			memset(result, 0, sizeof(struct hdhomerun_discover_device_t));

			if (!hdhomerun_discover_recv(ds, result)) {
				if (getcurrenttime() >= timeout) {
					break;
				}
				msleep_approx(16);
				continue;
			}

			/* Filter. */
			if (device_type != HDHOMERUN_DEVICE_TYPE_WILDCARD) {
				if (device_type != result->device_type) {
					continue;
				}
			}
			if (device_id != HDHOMERUN_DEVICE_ID_WILDCARD) {
				if (device_id != result->device_id) {
					continue;
				}
			}

			/* Ensure not already in list. */
			if (hdhomerun_discover_find_in_list(result_list, count, result)) {
				continue;
			}

			/* Add to list. */
			count++;
			if (count >= max_count) {
				return count;
			}
		}
	}

	return count;
}

int hdhomerun_discover_find_devices_custom_v2(uint32_t target_ip, uint32_t device_type, uint32_t device_id, struct hdhomerun_discover_device_t result_list[], int max_count)
{
	if (hdhomerun_discover_is_ip_multicast(target_ip)) {
		return 0;
	}

	struct hdhomerun_discover_t *ds = hdhomerun_discover_create(NULL);
	if (!ds) {
		return -1;
	}

	int ret = hdhomerun_discover_find_devices_v2(ds, target_ip, device_type, device_id, result_list, max_count);

	hdhomerun_discover_destroy(ds);
	return ret;
}

bool hdhomerun_discover_validate_device_id(uint32_t device_id)
{
	static uint8_t lookup_table[16] = {0xA, 0x5, 0xF, 0x6, 0x7, 0xC, 0x1, 0xB, 0x9, 0x2, 0x8, 0xD, 0x4, 0x3, 0xE, 0x0};

	uint8_t checksum = 0;

	checksum ^= lookup_table[(device_id >> 28) & 0x0F];
	checksum ^= (device_id >> 24) & 0x0F;
	checksum ^= lookup_table[(device_id >> 20) & 0x0F];
	checksum ^= (device_id >> 16) & 0x0F;
	checksum ^= lookup_table[(device_id >> 12) & 0x0F];
	checksum ^= (device_id >> 8) & 0x0F;
	checksum ^= lookup_table[(device_id >> 4) & 0x0F];
	checksum ^= (device_id >> 0) & 0x0F;

	return (checksum == 0);
}

bool hdhomerun_discover_is_ip_multicast(uint32_t ip_addr)
{
	return (ip_addr >= 0xE0000000) && (ip_addr < 0xF0000000);
}
