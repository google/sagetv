/*
 * hdhomerun_device.h
 *
 * Copyright © 2006-2015 Silicondust USA Inc. <www.silicondust.com>.
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

#define HDHOMERUN_DEVICE_MAX_TUNE_TO_LOCK_TIME 1500
#define HDHOMERUN_DEVICE_MAX_LOCK_TO_DATA_TIME 2000
#define HDHOMERUN_DEVICE_MAX_TUNE_TO_DATA_TIME (HDHOMERUN_DEVICE_MAX_TUNE_TO_LOCK_TIME + HDHOMERUN_DEVICE_MAX_LOCK_TO_DATA_TIME)

#define HDHOMERUN_TARGET_PROTOCOL_UDP "udp"
#define HDHOMERUN_TARGET_PROTOCOL_RTP "rtp"

/*
 * Create a device object.
 *
 * Typically a device object will be created for each tuner.
 * It is valid to have multiple device objects communicating with a single HDHomeRun.
 *
 * For example, a threaded application that streams video from 4 tuners (2 HDHomeRun devices) and has
 * GUI feedback to the user of the selected tuner might use 5 device objects: 4 for streaming video
 * (one per thread) and one for the GUI display that can switch between tuners.
 *
 * This function will not attempt to connect to the device. The connection will be established when first used.
 *
 * uint32_t device_id = 32-bit device id of device. Set to HDHOMERUN_DEVICE_ID_WILDCARD to match any device ID.
 * uint32_t device_ip = IP address of device. Set to 0 to auto-detect.
 * unsigned int tuner = tuner index (0 or 1). Can be changed later by calling hdhomerun_device_set_tuner.
 * struct hdhomerun_debug_t *dbg: Pointer to debug logging object. May be NULL.
 *
 * Returns a pointer to the newly created device object.
 *
 * When no longer needed, the socket should be destroyed by calling hdhomerun_device_destroy.
 *
 * The hdhomerun_device_create_from_str function creates a device object from the given device_str.
 * The device_str parameter can be any of the following forms:
 *     <device id>
 *     <device id>-<tuner index>
 *     <ip address>
 * If the tuner index is not included in the device_str then it is set to zero. Use hdhomerun_device_set_tuner
 * or hdhomerun_device_set_tuner_from_str to set the tuner.
 *
 * The hdhomerun_device_set_tuner_from_str function sets the tuner from the given tuner_str.
 * The tuner_str parameter can be any of the following forms:
 *     <tuner index>
 *     /tuner<tuner index>
 */
extern LIBHDHOMERUN_API struct hdhomerun_device_t *hdhomerun_device_create(uint32_t device_id, uint32_t device_ip, unsigned int tuner, struct hdhomerun_debug_t *dbg);
extern LIBHDHOMERUN_API struct hdhomerun_device_t *hdhomerun_device_create_multicast(uint32_t multicast_ip, uint16_t multicast_port, struct hdhomerun_debug_t *dbg);
extern LIBHDHOMERUN_API struct hdhomerun_device_t *hdhomerun_device_create_from_str(const char *device_str, struct hdhomerun_debug_t *dbg);
extern LIBHDHOMERUN_API void hdhomerun_device_destroy(struct hdhomerun_device_t *hd);

/*
 * Get the device id, ip, or tuner of the device instance.
 */
extern LIBHDHOMERUN_API const char *hdhomerun_device_get_name(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API uint32_t hdhomerun_device_get_device_id(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API uint32_t hdhomerun_device_get_device_ip(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API uint32_t hdhomerun_device_get_device_id_requested(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API uint32_t hdhomerun_device_get_device_ip_requested(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API unsigned int hdhomerun_device_get_tuner(struct hdhomerun_device_t *hd);

extern LIBHDHOMERUN_API int hdhomerun_device_set_device(struct hdhomerun_device_t *hd, uint32_t device_id, uint32_t device_ip);
extern LIBHDHOMERUN_API int hdhomerun_device_set_multicast(struct hdhomerun_device_t *hd, uint32_t multicast_ip, uint16_t multicast_port);
extern LIBHDHOMERUN_API int hdhomerun_device_set_tuner(struct hdhomerun_device_t *hd, unsigned int tuner);
extern LIBHDHOMERUN_API int hdhomerun_device_set_tuner_from_str(struct hdhomerun_device_t *hd, const char *tuner_str);

/*
 * Get the local machine IP address used when communicating with the device.
 *
 * This function is useful for determining the IP address to use with set target commands.
 *
 * Returns 32-bit IP address with native endianness, or 0 on error.
 */
extern LIBHDHOMERUN_API uint32_t hdhomerun_device_get_local_machine_addr(struct hdhomerun_device_t *hd);

/*
 * Get operations.
 *
 * struct hdhomerun_tuner_status_t *status = Pointer to caller supplied status struct to be populated with result.
 * const char **p<name> = Caller supplied char * to be updated to point to the result string. The string will remain
 *		valid until another call to a device function.
 *
 * Returns 1 if the operation was successful.
 * Returns 0 if the operation was rejected.
 * Returns -1 if a communication error occurred.
 */
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_status(struct hdhomerun_device_t *hd, char **pstatus_str, struct hdhomerun_tuner_status_t *status);
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_vstatus(struct hdhomerun_device_t *hd, char **pvstatus_str, struct hdhomerun_tuner_vstatus_t *vstatus);
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_streaminfo(struct hdhomerun_device_t *hd, char **pstreaminfo);
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_channel(struct hdhomerun_device_t *hd, char **pchannel);
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_vchannel(struct hdhomerun_device_t *hd, char **pvchannel);
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_channelmap(struct hdhomerun_device_t *hd, char **pchannelmap);
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_filter(struct hdhomerun_device_t *hd, char **pfilter);
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_program(struct hdhomerun_device_t *hd, char **pprogram);
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_target(struct hdhomerun_device_t *hd, char **ptarget);
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_plotsample(struct hdhomerun_device_t *hd, struct hdhomerun_plotsample_t **psamples, size_t *pcount);
extern LIBHDHOMERUN_API int hdhomerun_device_get_tuner_lockkey_owner(struct hdhomerun_device_t *hd, char **powner);
extern LIBHDHOMERUN_API int hdhomerun_device_get_oob_status(struct hdhomerun_device_t *hd, char **pstatus_str, struct hdhomerun_tuner_status_t *status);
extern LIBHDHOMERUN_API int hdhomerun_device_get_oob_plotsample(struct hdhomerun_device_t *hd, struct hdhomerun_plotsample_t **psamples, size_t *pcount);
extern LIBHDHOMERUN_API int hdhomerun_device_get_ir_target(struct hdhomerun_device_t *hd, char **ptarget);
extern LIBHDHOMERUN_API int hdhomerun_device_get_version(struct hdhomerun_device_t *hd, char **pversion_str, uint32_t *pversion_num);
extern LIBHDHOMERUN_API int hdhomerun_device_get_supported(struct hdhomerun_device_t *hd, char *prefix, char **pstr);

extern LIBHDHOMERUN_API uint32_t hdhomerun_device_get_tuner_status_ss_color(struct hdhomerun_tuner_status_t *status);
extern LIBHDHOMERUN_API uint32_t hdhomerun_device_get_tuner_status_snq_color(struct hdhomerun_tuner_status_t *status);
extern LIBHDHOMERUN_API uint32_t hdhomerun_device_get_tuner_status_seq_color(struct hdhomerun_tuner_status_t *status);

extern LIBHDHOMERUN_API const char *hdhomerun_device_get_hw_model_str(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API const char *hdhomerun_device_get_model_str(struct hdhomerun_device_t *hd);

/*
 * Set operations.
 *
 * const char *<name> = String to send to device.
 *
 * Returns 1 if the operation was successful.
 * Returns 0 if the operation was rejected.
 * Returns -1 if a communication error occurred.
 */
extern LIBHDHOMERUN_API int hdhomerun_device_set_tuner_channel(struct hdhomerun_device_t *hd, const char *channel);
extern LIBHDHOMERUN_API int hdhomerun_device_set_tuner_vchannel(struct hdhomerun_device_t *hd, const char *vchannel);
extern LIBHDHOMERUN_API int hdhomerun_device_set_tuner_channelmap(struct hdhomerun_device_t *hd, const char *channelmap);
extern LIBHDHOMERUN_API int hdhomerun_device_set_tuner_filter(struct hdhomerun_device_t *hd, const char *filter);
extern LIBHDHOMERUN_API int hdhomerun_device_set_tuner_filter_by_array(struct hdhomerun_device_t *hd, unsigned char filter_array[0x2000]);
extern LIBHDHOMERUN_API int hdhomerun_device_set_tuner_program(struct hdhomerun_device_t *hd, const char *program);
extern LIBHDHOMERUN_API int hdhomerun_device_set_tuner_target(struct hdhomerun_device_t *hd, const char *target);
extern LIBHDHOMERUN_API int hdhomerun_device_set_ir_target(struct hdhomerun_device_t *hd, const char *target);
extern LIBHDHOMERUN_API int hdhomerun_device_set_sys_dvbc_modulation(struct hdhomerun_device_t *hd, const char *modulation_list);

/*
 * Get/set a named control variable on the device.
 *
 * const char *name: The name of var to get/set (c-string). The supported vars is device/firmware dependant.
 * const char *value: The value to set (c-string). The format is device/firmware dependant.

 * char **pvalue: If provided, the caller-supplied char pointer will be populated with a pointer to the value
 *		string returned by the device, or NULL if the device returned an error string. The string will remain
 *		valid until the next call to a control sock function.
 * char **perror: If provided, the caller-supplied char pointer will be populated with a pointer to the error
 *		string returned by the device, or NULL if the device returned an value string. The string will remain
 *		valid until the next call to a control sock function.
 *
 * Returns 1 if the operation was successful (pvalue set, perror NULL).
 * Returns 0 if the operation was rejected (pvalue NULL, perror set).
 * Returns -1 if a communication error occurs.
 */
extern LIBHDHOMERUN_API int hdhomerun_device_get_var(struct hdhomerun_device_t *hd, const char *name, char **pvalue, char **perror);
extern LIBHDHOMERUN_API int hdhomerun_device_set_var(struct hdhomerun_device_t *hd, const char *name, const char *value, char **pvalue, char **perror);

/*
 * Tuner locking.
 *
 * The hdhomerun_device_tuner_lockkey_request function is used to obtain a lock
 * or to verify that the hdhomerun_device object still holds the lock.
 * Returns 1 if the lock request was successful and the lock was obtained.
 * Returns 0 if the lock request was rejected.
 * Returns -1 if a communication error occurs.
 *
 * The hdhomerun_device_tuner_lockkey_release function is used to release a
 * previously held lock. If locking is used then this function must be called
 * before destroying the hdhomerun_device object.
 */
extern LIBHDHOMERUN_API int hdhomerun_device_tuner_lockkey_request(struct hdhomerun_device_t *hd, char **perror);
extern LIBHDHOMERUN_API int hdhomerun_device_tuner_lockkey_release(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API int hdhomerun_device_tuner_lockkey_force(struct hdhomerun_device_t *hd);

/*
 * Intended only for non persistent connections; eg, hdhomerun_config.
 */
extern LIBHDHOMERUN_API void hdhomerun_device_tuner_lockkey_use_value(struct hdhomerun_device_t *hd, uint32_t lockkey);

/*
 * Wait for tuner lock after channel change.
 *
 * The hdhomerun_device_wait_for_lock function is used to detect/wait for a lock vs no lock indication
 * after a channel change.
 *
 * It will return quickly if a lock is aquired.
 * It will return quickly if there is no signal detected.
 * Worst case it will time out after 1.5 seconds - the case where there is signal but no lock.
 */
extern LIBHDHOMERUN_API int hdhomerun_device_wait_for_lock(struct hdhomerun_device_t *hd, struct hdhomerun_tuner_status_t *status);

/*
 * Stream a filtered program or the unfiltered stream.
 *
 * The hdhomerun_device_stream_start function initializes the process and tells the device to start streamin data.
 *
 * uint16_t program_number = The program number to filer, or 0 for unfiltered.
 *
 * Returns 1 if the oprtation started successfully.
 * Returns 0 if the operation was rejected.
 * Returns -1 if a communication error occurs.
 *
 * The hdhomerun_device_stream_recv function should be called periodically to receive the stream data.
 * The buffer can losslessly store 1 second of data, however a more typical call rate would be every 15ms.
 *
 * The hdhomerun_device_stream_stop function tells the device to stop streaming data.
 */
extern LIBHDHOMERUN_API int hdhomerun_device_stream_start(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API uint8_t *hdhomerun_device_stream_recv(struct hdhomerun_device_t *hd, size_t max_size, size_t *pactual_size);
extern LIBHDHOMERUN_API void hdhomerun_device_stream_flush(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API void hdhomerun_device_stream_stop(struct hdhomerun_device_t *hd);

/*
 * Channel scan API.
 */
extern LIBHDHOMERUN_API int hdhomerun_device_channelscan_init(struct hdhomerun_device_t *hd, const char *channelmap);
extern LIBHDHOMERUN_API int hdhomerun_device_channelscan_advance(struct hdhomerun_device_t *hd, struct hdhomerun_channelscan_result_t *result);
extern LIBHDHOMERUN_API int hdhomerun_device_channelscan_detect(struct hdhomerun_device_t *hd, struct hdhomerun_channelscan_result_t *result);
extern LIBHDHOMERUN_API uint8_t hdhomerun_device_channelscan_get_progress(struct hdhomerun_device_t *hd);

/*
 * Upload new firmware to the device.
 *
 * FILE *upgrade_file: File pointer to read from. The file must have been opened in binary mode for reading.
 *
 * Returns 1 if the upload succeeded.
 * Returns 0 if the upload was rejected.
 * Returns -1 if an error occurs.
 */
extern LIBHDHOMERUN_API int hdhomerun_device_upgrade(struct hdhomerun_device_t *hd, FILE *upgrade_file);

/*
 * Low level accessor functions. 
 */
extern LIBHDHOMERUN_API struct hdhomerun_control_sock_t *hdhomerun_device_get_control_sock(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API struct hdhomerun_video_sock_t *hdhomerun_device_get_video_sock(struct hdhomerun_device_t *hd);

/*
 * Debug print internal stats.
 */
extern LIBHDHOMERUN_API void hdhomerun_device_debug_print_video_stats(struct hdhomerun_device_t *hd);
extern LIBHDHOMERUN_API void hdhomerun_device_get_video_stats(struct hdhomerun_device_t *hd, struct hdhomerun_video_stats_t *stats);

#ifdef __cplusplus
}
#endif
