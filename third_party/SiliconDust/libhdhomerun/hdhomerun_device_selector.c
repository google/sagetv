/*
 * hdhomerun_device_selector.c
 *
 * Copyright © 2009-2016 Silicondust USA Inc. <www.silicondust.com>.
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

struct hdhomerun_device_selector_t {
	struct hdhomerun_device_t **hd_list;
	size_t hd_count;
	struct hdhomerun_debug_t *dbg;
};

struct hdhomerun_device_selector_t *hdhomerun_device_selector_create(struct hdhomerun_debug_t *dbg)
{
	struct hdhomerun_device_selector_t *hds = (struct hdhomerun_device_selector_t *)calloc(1, sizeof(struct hdhomerun_device_selector_t));
	if (!hds) {
		hdhomerun_debug_printf(dbg, "hdhomerun_device_selector_create: failed to allocate selector object\n");
		return NULL;
	}

	hds->dbg = dbg;

	return hds;
}

void hdhomerun_device_selector_destroy(struct hdhomerun_device_selector_t *hds, bool destroy_devices)
{
	if (destroy_devices) {
		size_t index;
		for (index = 0; index < hds->hd_count; index++) {
			struct hdhomerun_device_t *entry = hds->hd_list[index];
			hdhomerun_device_destroy(entry);
		}
	}

	if (hds->hd_list) {
		free(hds->hd_list);
	}

	free(hds);
}

int hdhomerun_device_selector_get_device_count(struct hdhomerun_device_selector_t *hds)
{
	return (int)hds->hd_count;
}

void hdhomerun_device_selector_add_device(struct hdhomerun_device_selector_t *hds, struct hdhomerun_device_t *hd)
{
	size_t index;
	for (index = 0; index < hds->hd_count; index++) {
		struct hdhomerun_device_t *entry = hds->hd_list[index];
		if (entry == hd) {
			return;
		}
	}

	struct hdhomerun_device_t **hd_list = (struct hdhomerun_device_t **)realloc(hds->hd_list, (hds->hd_count + 1) * sizeof(struct hdhomerun_device_t *));
	if (!hd_list) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_add_device: failed to allocate device list\n");
		return;
	}

	hds->hd_list = hd_list;
	hds->hd_list[hds->hd_count++] = hd;
}

void hdhomerun_device_selector_remove_device(struct hdhomerun_device_selector_t *hds, struct hdhomerun_device_t *hd)
{
	size_t index = 0;
	while (1) {
		if (index >= hds->hd_count) {
			return;
		}

		struct hdhomerun_device_t *entry = hds->hd_list[index];
		if (entry == hd) {
			break;
		}

		index++;
	}

	while (index + 1 < hds->hd_count) {
		hds->hd_list[index] = hds->hd_list[index + 1];
		index++;
	}

	hds->hd_list[index] = NULL;
	hds->hd_count--;
}

struct hdhomerun_device_t *hdhomerun_device_selector_find_device(struct hdhomerun_device_selector_t *hds, uint32_t device_id, unsigned int tuner_index)
{
	size_t index;
	for (index = 0; index < hds->hd_count; index++) {
		struct hdhomerun_device_t *entry = hds->hd_list[index];
		if (hdhomerun_device_get_device_id(entry) != device_id) {
			continue;
		}
		if (hdhomerun_device_get_tuner(entry) != tuner_index) {
			continue;
		}
		return entry;
	}

	return NULL;
}

static int hdhomerun_device_selector_load_from_str_discover(struct hdhomerun_device_selector_t *hds, uint32_t target_ip, uint32_t device_id)
{
	struct hdhomerun_discover_device_t result;
	int discover_count = hdhomerun_discover_find_devices_custom_v2(target_ip, HDHOMERUN_DEVICE_TYPE_TUNER, device_id, &result, 1);
	if (discover_count != 1) {
		return 0;
	}

	int count = 0;
	unsigned int tuner_index;
	for (tuner_index = 0; tuner_index < result.tuner_count; tuner_index++) {
		struct hdhomerun_device_t *hd = hdhomerun_device_create(result.device_id, result.ip_addr, tuner_index, hds->dbg);
		if (!hd) {
			continue;
		}

		hdhomerun_device_selector_add_device(hds, hd);
		count++;
	}

	return count;
}

int hdhomerun_device_selector_load_from_str(struct hdhomerun_device_selector_t *hds, char *device_str)
{
	/*
	 * IP address based device_str.
	 */
	unsigned int a[4];
	if (sscanf(device_str, "%u.%u.%u.%u", &a[0], &a[1], &a[2], &a[3]) == 4) {
		uint32_t ip_addr = (uint32_t)((a[0] << 24) | (a[1] << 16) | (a[2] << 8) | (a[3] << 0));

		/*
		 * Multicast IP address.
		 */
		unsigned int port;
		if (sscanf(device_str, "%u.%u.%u.%u:%u", &a[0], &a[1], &a[2], &a[3], &port) == 5) {
			struct hdhomerun_device_t *hd = hdhomerun_device_create_multicast(ip_addr, (uint16_t)port, hds->dbg);
			if (!hd) {
				return 0;
			}

			hdhomerun_device_selector_add_device(hds, hd);
			return 1;
		}

		/*
		 * IP address + tuner number.
		 */
		unsigned int tuner;
		if (sscanf(device_str, "%u.%u.%u.%u-%u", &a[0], &a[1], &a[2], &a[3], &tuner) == 5) {
			struct hdhomerun_device_t *hd = hdhomerun_device_create(HDHOMERUN_DEVICE_ID_WILDCARD, ip_addr, tuner, hds->dbg);
			if (!hd) {
				return 0;
			}

			hdhomerun_device_selector_add_device(hds, hd);
			return 1;
		}

		/*
		 * IP address only - discover and add tuners.
		 */
		return hdhomerun_device_selector_load_from_str_discover(hds, ip_addr, HDHOMERUN_DEVICE_ID_WILDCARD);
	}

	/*
	 * Device ID based device_str.
	 */
	char *end;
	uint32_t device_id = (uint32_t)strtoul(device_str, &end, 16);
	if ((end == device_str + 8) && hdhomerun_discover_validate_device_id(device_id)) {
		/*
		 * IP address + tuner number.
		 */
		if (*end == '-') {
			unsigned int tuner = (unsigned int)strtoul(end + 1, NULL, 10);
			struct hdhomerun_device_t *hd = hdhomerun_device_create(device_id, 0, tuner, hds->dbg);
			if (!hd) {
				return 0;
			}

			hdhomerun_device_selector_add_device(hds, hd);
			return 1;
		}

		/*
		 * Device ID only - discover and add tuners.
		 */
		return hdhomerun_device_selector_load_from_str_discover(hds, 0, device_id);
	}

	/*
	* DNS based device_str.
	*/
	struct addrinfo hints;
	memset(&hints, 0, sizeof(hints));
	hints.ai_family = AF_INET;
	hints.ai_socktype = SOCK_STREAM;
	hints.ai_protocol = IPPROTO_TCP;

	struct addrinfo *sock_info;
	if (getaddrinfo(device_str, "65001", &hints, &sock_info) != 0) {
		return 0;
	}

	struct sockaddr_in *sock_addr = (struct sockaddr_in *)sock_info->ai_addr;
	uint32_t ip_addr = (uint32_t)ntohl(sock_addr->sin_addr.s_addr);
	freeaddrinfo(sock_info);

	if (ip_addr == 0) {
		return 0;
	}

	return hdhomerun_device_selector_load_from_str_discover(hds, ip_addr, HDHOMERUN_DEVICE_ID_WILDCARD);
}

int hdhomerun_device_selector_load_from_file(struct hdhomerun_device_selector_t *hds, char *filename)
{
	FILE *fp = fopen(filename, "r");
	if (!fp) {
		return 0;
	}

	int count = 0;
	while(1) {
		char device_str[32];
		if (!fgets(device_str, sizeof(device_str), fp)) {
			break;
		}

		count += hdhomerun_device_selector_load_from_str(hds, device_str);
	}

	fclose(fp);
	return count;
}

#if defined(_WIN32) && !defined(_WINRT)
int hdhomerun_device_selector_load_from_windows_registry(struct hdhomerun_device_selector_t *hds, wchar_t *wsource)
{
	HKEY tuners_key;
	LONG ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Silicondust\\HDHomeRun\\Tuners", 0, KEY_QUERY_VALUE | KEY_ENUMERATE_SUB_KEYS, &tuners_key);
	if (ret != ERROR_SUCCESS) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_load_from_windows_registry: failed to open tuners registry key (%ld)\n", (long)ret);
		return 0;
	}

	int count = 0;
	DWORD index = 0;
	while (1) {
		/* Next tuner device. */
		wchar_t wdevice_str[32];
		DWORD size = sizeof(wdevice_str);
		ret = RegEnumKeyEx(tuners_key, index++, wdevice_str, &size, NULL, NULL, NULL, NULL);
		if (ret != ERROR_SUCCESS) {
			break;
		}

		/* Check device configuation. */
		HKEY device_key;
		ret = RegOpenKeyEx(tuners_key, wdevice_str, 0, KEY_QUERY_VALUE, &device_key);
		if (ret != ERROR_SUCCESS) {
			hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_load_from_windows_registry: failed to open registry key for %S (%ld)\n", wdevice_str, (long)ret);
			continue;
		}

		wchar_t wsource_test[32];
		size = sizeof(wsource_test);
		if (RegQueryValueEx(device_key, L"Source", NULL, NULL, (LPBYTE)&wsource_test, &size) != ERROR_SUCCESS) {
			wsprintf(wsource_test, L"Unknown");
		}

		RegCloseKey(device_key);

		if (_wcsicmp(wsource_test, wsource) != 0) {
			continue;
		}

		/* Create and add device. */
		char device_str[32];
		hdhomerun_sprintf(device_str, device_str + sizeof(device_str), "%S", wdevice_str);
		count += hdhomerun_device_selector_load_from_str(hds, device_str);
	}

	RegCloseKey(tuners_key);
	return count;
}
#endif

static bool hdhomerun_device_selector_choose_test(struct hdhomerun_device_selector_t *hds, struct hdhomerun_device_t *test_hd)
{
	const char *name = hdhomerun_device_get_name(test_hd);

	/*
	 * Attempt to aquire lock.
	 */
	char *error = NULL;
	int ret = hdhomerun_device_tuner_lockkey_request(test_hd, &error);
	if (ret > 0) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s chosen\n", name);
		return true;
	}
	if (ret < 0) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s communication error\n", name);
		return false;
	}

	/*
	 * In use - check target.
	 */
	char *target;
	ret = hdhomerun_device_get_tuner_target(test_hd, &target);
	if (ret < 0) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s communication error\n", name);
		return false;
	}
	if (ret == 0) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s in use, failed to read target\n", name);
		return false;
	}

	if (strcmp(target, "none") == 0) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s in use, no target set\n", name);
		return false;
	}

	if ((strncmp(target, "udp://", 6) != 0) && (strncmp(target, "rtp://", 6) != 0)) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s in use by %s\n", name, target);
		return false;
	}

	unsigned int a[4];
	unsigned int target_port;
	if (sscanf(target + 6, "%u.%u.%u.%u:%u", &a[0], &a[1], &a[2], &a[3], &target_port) != 5) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s in use, unexpected target set (%s)\n", name, target);
		return false;
	}

	uint32_t target_ip = (uint32_t)((a[0] << 24) | (a[1] << 16) | (a[2] << 8) | (a[3] << 0));
	uint32_t local_ip = hdhomerun_device_get_local_machine_addr(test_hd);
	if (target_ip != local_ip) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s in use by %s\n", name, target);
		return false;
	}

	/*
	 * Test local port.
	 */
	struct hdhomerun_sock_t *test_sock = hdhomerun_sock_create_udp();
	if (!test_sock) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s in use, failed to create test sock\n", name);
		return false;
	}

	bool inuse = (hdhomerun_sock_bind(test_sock, INADDR_ANY, (uint16_t)target_port, false) == false);
	hdhomerun_sock_destroy(test_sock);

	if (inuse) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s in use by local machine\n", name);
		return false;
	}

	/*
	 * Dead local target, force clear lock.
	 */
	ret = hdhomerun_device_tuner_lockkey_force(test_hd);
	if (ret < 0) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s communication error\n", name);
		return false;
	}
	if (ret == 0) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s in use by local machine, dead target, failed to force release lockkey\n", name);
		return false;
	}

	hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s in use by local machine, dead target, lockkey force successful\n", name);

	/*
	 * Attempt to aquire lock.
	 */
	ret = hdhomerun_device_tuner_lockkey_request(test_hd, &error);
	if (ret > 0) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s chosen\n", name);
		return true;
	}
	if (ret < 0) {
		hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s communication error\n", name);
		return false;
	}

	hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_test: device %s still in use after lockkey force (%s)\n", name, error);
	return false;
}

struct hdhomerun_device_t *hdhomerun_device_selector_choose_and_lock(struct hdhomerun_device_selector_t *hds, struct hdhomerun_device_t *prefered)
{
	/* Test prefered device first. */
	if (prefered) {
		if (hdhomerun_device_selector_choose_test(hds, prefered)) {
			return prefered;
		}
	}

	/* Test other tuners. */
	size_t index;
	for (index = 0; index < hds->hd_count; index++) {
		struct hdhomerun_device_t *entry = hds->hd_list[index];
		if (entry == prefered) {
			continue;
		}

		if (hdhomerun_device_selector_choose_test(hds, entry)) {
			return entry;
		}
	}

	hdhomerun_debug_printf(hds->dbg, "hdhomerun_device_selector_choose_and_lock: no devices available\n");
	return NULL;
}
