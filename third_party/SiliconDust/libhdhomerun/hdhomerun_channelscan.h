/*
 * hdhomerun_channelscan.h
 *
 * Copyright © 2007-2015 Silicondust USA Inc. <www.silicondust.com>.
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

#define HDHOMERUN_CHANNELSCAN_PROGRAM_NORMAL 0
#define HDHOMERUN_CHANNELSCAN_PROGRAM_NODATA 1
#define HDHOMERUN_CHANNELSCAN_PROGRAM_CONTROL 2
#define HDHOMERUN_CHANNELSCAN_PROGRAM_ENCRYPTED 3

struct hdhomerun_channelscan_t;

extern LIBHDHOMERUN_API struct hdhomerun_channelscan_t *channelscan_create(struct hdhomerun_device_t *hd, const char *channelmap);
extern LIBHDHOMERUN_API void channelscan_destroy(struct hdhomerun_channelscan_t *scan);

extern LIBHDHOMERUN_API int channelscan_advance(struct hdhomerun_channelscan_t *scan, struct hdhomerun_channelscan_result_t *result);
extern LIBHDHOMERUN_API int channelscan_detect(struct hdhomerun_channelscan_t *scan, struct hdhomerun_channelscan_result_t *result);
extern LIBHDHOMERUN_API uint8_t channelscan_get_progress(struct hdhomerun_channelscan_t *scan);

#ifdef __cplusplus
}
#endif
