/*
 * hdhomerun_channels.h
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

struct hdhomerun_channel_entry_t;
struct hdhomerun_channel_list_t;

extern LIBHDHOMERUN_API const char *hdhomerun_channelmap_get_channelmap_from_country_source(const char *countrycode, const char *source, const char *supported);
extern LIBHDHOMERUN_API const char *hdhomerun_channelmap_get_channelmap_scan_group(const char *channelmap);

extern LIBHDHOMERUN_API uint16_t hdhomerun_channel_entry_channel_number(struct hdhomerun_channel_entry_t *entry);
extern LIBHDHOMERUN_API uint32_t hdhomerun_channel_entry_frequency(struct hdhomerun_channel_entry_t *entry);
extern LIBHDHOMERUN_API const char *hdhomerun_channel_entry_name(struct hdhomerun_channel_entry_t *entry);

extern LIBHDHOMERUN_API struct hdhomerun_channel_list_t *hdhomerun_channel_list_create(const char *channelmap);
extern LIBHDHOMERUN_API void hdhomerun_channel_list_destroy(struct hdhomerun_channel_list_t *channel_list);

extern LIBHDHOMERUN_API struct hdhomerun_channel_entry_t *hdhomerun_channel_list_first(struct hdhomerun_channel_list_t *channel_list);
extern LIBHDHOMERUN_API struct hdhomerun_channel_entry_t *hdhomerun_channel_list_last(struct hdhomerun_channel_list_t *channel_list);
extern LIBHDHOMERUN_API struct hdhomerun_channel_entry_t *hdhomerun_channel_list_next(struct hdhomerun_channel_list_t *channel_list, struct hdhomerun_channel_entry_t *entry);
extern LIBHDHOMERUN_API struct hdhomerun_channel_entry_t *hdhomerun_channel_list_prev(struct hdhomerun_channel_list_t *channel_list, struct hdhomerun_channel_entry_t *entry);
extern LIBHDHOMERUN_API uint32_t hdhomerun_channel_list_total_count(struct hdhomerun_channel_list_t *channel_list);
extern LIBHDHOMERUN_API uint32_t hdhomerun_channel_list_frequency_count(struct hdhomerun_channel_list_t *channel_list);

extern LIBHDHOMERUN_API uint32_t hdhomerun_channel_frequency_round(uint32_t frequency, uint32_t resolution);
extern LIBHDHOMERUN_API uint32_t hdhomerun_channel_frequency_round_normal(uint32_t frequency);
extern LIBHDHOMERUN_API uint32_t hdhomerun_channel_number_to_frequency(struct hdhomerun_channel_list_t *channel_list, uint16_t channel_number);
extern LIBHDHOMERUN_API uint16_t hdhomerun_channel_frequency_to_number(struct hdhomerun_channel_list_t *channel_list, uint32_t frequency);

#ifdef __cplusplus
}
#endif
