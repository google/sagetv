/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef _GET_AVINFO_H
#define _GET_AVINFO_H

#ifdef __cplusplus
extern "C" {
#endif


typedef struct AVINF
{
	unsigned short state;
	unsigned short task;
	struct DEMUXER* demuxer;

	ULONGLONG first_pts;
	ULONGLONG last_pts;
	ULONGLONG current_pts;

	unsigned long video_data_block;
	unsigned long audio_data_block;

	unsigned long max_check_bytes;

} AVINF;

typedef struct AVPTS
{
	unsigned short state;
	unsigned short task;
	struct DEMUXER* demuxer;

	ULONGLONG first_pts;
	ULONGLONG last_pts;
	ULONGLONG bigest_pts;
	ULONGLONG smallest_pts;
	ULONGLONG current_pts;
	ULONGLONG current_dts;
	ULONGLONG current_pcr;

	unsigned long video_data_block;
	unsigned long audio_data_block;

	unsigned long max_check_bytes;

	unsigned short output_format;
	ULONGLONG file_size;
	ULONGLONG push_bytes;

	unsigned long count;
	unsigned long ill_pcr_count;
	unsigned long ill_pts_count;

	int pts_file;

} AVPTS;

int GetAVFormat(  char* pFileName, unsigned long nCheckMaxiumSize, int bStreamData, 
			   int nRequestedTSChannel,   char* pFormatBuf, int nFormatSize, char* pDurationBuf, 
			   int nDurationBufSize, int* nTotalChannel );
int GetAVPts( char* pFileName, char* pPTSFile, int nOption, unsigned long nCheckMaxiumSize, 
			   int nRequestedTSChannel, int* nTotalChannel );

ULONGLONG  hs_long_long( char* digital );
char* long_long_hss( ULONGLONG llVal );
char* long_long_ss( ULONGLONG llVal );
int time_stamp( LONGLONG llTime, char* pBuffer, int nSize );
int ms_time_stamp( ULONGLONG llTime, char* pBuffer, int nSize );

#ifdef __cplusplus
}
#endif

#endif

