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

#ifndef _BLOCK_BUFFER_H
#define _BLOCK_BUFFER_H

#ifdef __cplusplus
extern "C" {
#endif


//FIFO buffer
enum BLOCK_BUFFER_STATE {
	FREE_BLOCK_STATE  = 0x0,
	INUSE_BLOCK_STATE = 0x01,
	READY_BLOCK_STATE = 0x02,
} ;


typedef struct BLOCK_BUFFER
{
	unsigned char  state;
	unsigned char  group_start;
	
	unsigned short index;
	unsigned short fifo_index;

	unsigned short slot_index;
	unsigned short track_index;

	PES	pes;
	ULONGLONG start_cue;
	ULONGLONG end_cue;
	unsigned char  *buffer_start;
	unsigned short  buffer_size;
	unsigned char  *data_start;
	unsigned short  data_size;
} BLOCK_BUFFER;

typedef struct FIFO_BUFFER
{
	unsigned short total_queue_num;
	unsigned short block_buffer_size;
	BLOCK_BUFFER  **fifo_queue;
	unsigned short num_of_in_queue;
	unsigned short num_of_out_queue;
	unsigned short block_buffer_inuse;
	BLOCK_BUFFER  *block_buffer_pool;
	//unsigned char *all_buffer_data;
	//unsigned long  all_buffer_size;
} FIFO_BUFFER;

FIFO_BUFFER* CreateFIFOBuffer( int nQueueNum, int nBlockSize );
void  ReleaseFIFOBuffer( FIFO_BUFFER* pFIFOBuffer );
void  ResetFIFOBuffer( FIFO_BUFFER* pFIFOBuffer );
BLOCK_BUFFER* RequestFIFOBuffer( FIFO_BUFFER* pFIFOBuffer );
void          PushBlockBuffer( FIFO_BUFFER* pFIFOBuffer, BLOCK_BUFFER* pBlockBuffer );
BLOCK_BUFFER* PopBlockBuffer( FIFO_BUFFER* pFIFOBuffer );
BLOCK_BUFFER* TopBlockBuffer( FIFO_BUFFER* pFIFOBuffer );
void ReleaseBlockBuffer( FIFO_BUFFER* pFIFOBuffer, BLOCK_BUFFER* pBlockBuffer );

#ifdef __cplusplus
}
#endif

#endif
