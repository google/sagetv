/*
 * hdhomerun_types.h
 *
 * Copyright © 2008-2015 Silicondust USA Inc. <www.silicondust.com>.
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

#define HDHOMERUN_STATUS_COLOR_NEUTRAL	0xFFFFFFFF
#define HDHOMERUN_STATUS_COLOR_RED		0xFFFF0000
#define HDHOMERUN_STATUS_COLOR_YELLOW	0xFFFFFF00
#define HDHOMERUN_STATUS_COLOR_GREEN	0xFF00C000

struct hdhomerun_device_t;
struct hdhomerun_device_allocation_t;

struct hdhomerun_tuner_status_t {
	char channel[32];
	char lock_str[32];
	bool signal_present;
	bool lock_supported;
	bool lock_unsupported;
	unsigned int signal_strength;
	unsigned int signal_to_noise_quality;
	unsigned int symbol_error_quality;
	uint32_t raw_bits_per_second;
	uint32_t packets_per_second;
};

struct hdhomerun_tuner_vstatus_t {
	char vchannel[32];
	char name[32];
	char auth[32];
	char cci[32];
	char cgms[32];
	bool not_subscribed;
	bool not_available;
	bool copy_protected;
};

struct hdhomerun_channelscan_program_t {
	char program_str[64];
	uint16_t program_number;
	uint16_t virtual_major;
	uint16_t virtual_minor;
	uint16_t type;
	char name[32];
};

#define HDHOMERUN_CHANNELSCAN_MAX_PROGRAM_COUNT 64

struct hdhomerun_channelscan_result_t {
	char channel_str[64];
	uint32_t channelmap;
	uint32_t frequency;
	struct hdhomerun_tuner_status_t status;
	int program_count;
	struct hdhomerun_channelscan_program_t programs[HDHOMERUN_CHANNELSCAN_MAX_PROGRAM_COUNT];
	bool transport_stream_id_detected;
	bool original_network_id_detected;
	uint16_t transport_stream_id;
	uint16_t original_network_id;
};

struct hdhomerun_plotsample_t {
	int16_t real;
	int16_t imag;
};
