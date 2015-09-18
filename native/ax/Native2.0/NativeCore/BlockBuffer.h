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
	uint8_t  state;
	uint8_t  group_start;
	
	uint16_t index;
	uint16_t fifo_index;

	uint16_t slot_index;
	uint16_t track_index;

	PES	pes;
	ULONGLONG start_cue;
	ULONGLONG end_cue;
	uint8_t  *buffer_start;
	uint16_t  buffer_size;
	uint8_t  *data_start;
	uint16_t  data_size;
} BLOCK_BUFFER;

typedef struct FIFO_BUFFER
{
	uint16_t total_queue_num;
	uint16_t block_buffer_size;
	BLOCK_BUFFER  **fifo_queue;
	uint16_t num_of_in_queue;
	uint16_t num_of_out_queue;
	uint16_t block_buffer_inuse;
	BLOCK_BUFFER  *block_buffer_pool;
	//uint8_t *all_buffer_data;
	//uint32_t  all_buffer_size;
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
