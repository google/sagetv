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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "NativeCore.h"
#include "BlockBuffer.h"

//get content data from TS stream to make a block, time sequency isn't in an order, we need a FIFO queue to sort. ZQ
//

FIFO_BUFFER* CreateFIFOBuffer( int nQueueNum, int nBlockSize )
{
	int i;
	FIFO_BUFFER* pFIFOBuffer = SAGETV_MALLOC( sizeof(FIFO_BUFFER) );

	//ZQ outside buffer
	//pFIFOBuffer->all_buffer_size = nBlockSize*nQueueNum;
	//pFIFOBuffer->all_buffer_data = SAGETV_MALLOC( pFIFOBuffer->all_buffer_size );
	pFIFOBuffer->block_buffer_size = nBlockSize;
	pFIFOBuffer->fifo_queue = SAGETV_MALLOC( nQueueNum*sizeof(BLOCK_BUFFER *) );
	pFIFOBuffer->block_buffer_pool = SAGETV_MALLOC( nQueueNum*sizeof(BLOCK_BUFFER) );
	pFIFOBuffer->total_queue_num = nQueueNum;
	for ( i = 0; i<nQueueNum; i++ )
	{
		//pFIFOBuffer->block_buffer_pool[i].buffer_start = i*nBlockSize + pFIFOBuffer->all_buffer_data;
		//pFIFOBuffer->block_buffer_pool[i].buffer_size  = nBlockSize;
		//pFIFOBuffer->block_buffer_pool[i].data_start = pFIFOBuffer->block_buffer_pool[i].buffer_start; 
		//pFIFOBuffer->block_buffer_pool[i].data_size  = pFIFOBuffer->block_buffer_pool[i].buffer_size;
		pFIFOBuffer->block_buffer_pool[i].state = 0;
		pFIFOBuffer->block_buffer_pool[i].index = i;
		pFIFOBuffer->block_buffer_pool[i].fifo_index = 0xffff;
	}
	return pFIFOBuffer;
}

void ReleaseFIFOBuffer( FIFO_BUFFER* pFIFOBuffer )
{
	SAGETV_FREE( pFIFOBuffer->fifo_queue );
	SAGETV_FREE( pFIFOBuffer->block_buffer_pool );
	//SAGETV_FREE( pFIFOBuffer->all_buffer_data );
	SAGETV_FREE( pFIFOBuffer );
}

void ResetFIFOBuffer( FIFO_BUFFER* pFIFOBuffer )
{
	int i;
	for ( i = 0; i<pFIFOBuffer->total_queue_num; i++ )
	{
		pFIFOBuffer->block_buffer_pool[i].state = FREE_BLOCK_STATE;
		pFIFOBuffer->block_buffer_pool[i].fifo_index = 0xffff;
	}

	pFIFOBuffer->num_of_in_queue = 0;
	pFIFOBuffer->block_buffer_inuse = 0;
	pFIFOBuffer->num_of_out_queue = 0;
}

BLOCK_BUFFER* RequestFIFOBuffer( FIFO_BUFFER* pFIFOBuffer )
{
	int i;
	for ( i = 0; i<pFIFOBuffer->total_queue_num; i++ )
	{
		if ( pFIFOBuffer->block_buffer_pool[i].state == FREE_BLOCK_STATE )
			break;
	}
	if ( i == pFIFOBuffer->total_queue_num )
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: No free block buffer in fifo buffer (%d %d, %d %d)" ),
			             pFIFOBuffer->block_buffer_inuse, pFIFOBuffer->total_queue_num,
						 pFIFOBuffer->num_of_out_queue,   pFIFOBuffer->num_of_in_queue ));
		ASSERT( 0 );
		return NULL;
	}

	pFIFOBuffer->block_buffer_pool[i].state = INUSE_BLOCK_STATE;
	pFIFOBuffer->block_buffer_pool[i].fifo_index = pFIFOBuffer->num_of_in_queue;
	pFIFOBuffer->fifo_queue[pFIFOBuffer->num_of_in_queue++] = &pFIFOBuffer->block_buffer_pool[i];
	pFIFOBuffer->block_buffer_inuse++;

	return &pFIFOBuffer->block_buffer_pool[i];
}

void ReleaseBlockBuffer( FIFO_BUFFER* pFIFOBuffer, BLOCK_BUFFER* pBlockBuffer )
{
	pBlockBuffer->state = FREE_BLOCK_STATE;
	pFIFOBuffer->block_buffer_inuse--;
}

void PushBlockBuffer( FIFO_BUFFER* pFIFOBuffer, BLOCK_BUFFER* pBlockBuffer )
{
	if ( pBlockBuffer->fifo_index < pFIFOBuffer->total_queue_num && 
		 pFIFOBuffer->fifo_queue[ pBlockBuffer->fifo_index ] )
	{
		pFIFOBuffer->fifo_queue[ pBlockBuffer->fifo_index ]->state = READY_BLOCK_STATE;
	} else
	{
		SageLog(( _LOG_TRACE, 3, TEXT("ERROR: FIFO overrun" ))); 
	}
}


BLOCK_BUFFER* PopBlockBuffer( FIFO_BUFFER* pFIFOBuffer )
{
	int i;
	BLOCK_BUFFER *pBlockBuffer;

	if ( pFIFOBuffer->num_of_in_queue == 0 )
		return NULL;

	if ( pFIFOBuffer->fifo_queue[ 0 ]->state != READY_BLOCK_STATE )
	{
		//the FIFO queue is full, a es block is kicked out off FIFO queue
		if ( pFIFOBuffer->num_of_in_queue+pFIFOBuffer->num_of_out_queue >= pFIFOBuffer->total_queue_num-2 )
		{
			//SageLog(( _LOG_TRACE, 3, TEXT("WARNING:  FIFO queue is full, a es block is kicked out off FIFO queue" ) ));
			while ( pFIFOBuffer->num_of_in_queue > 0)
			{
				pFIFOBuffer->fifo_queue[ 0 ]->fifo_index = 0xffff;
				for ( i = 1; i<pFIFOBuffer->num_of_in_queue; i++ )
				{
					if ( pFIFOBuffer->fifo_queue[ 0 ]->fifo_index != 0xffff ) 
						pFIFOBuffer->fifo_queue[i]->fifo_index--;
					pFIFOBuffer->fifo_queue[i-1] = pFIFOBuffer->fifo_queue[i];
				}
				pFIFOBuffer->num_of_out_queue++;
				pFIFOBuffer->num_of_in_queue--;
				if ( pFIFOBuffer->fifo_queue[ 0 ]->state == READY_BLOCK_STATE )
					break;
			}
		}
	}

	if ( pFIFOBuffer->fifo_queue[ 0 ]->state == READY_BLOCK_STATE )
	{
		pBlockBuffer = pFIFOBuffer->fifo_queue[ 0 ];
		pFIFOBuffer->num_of_in_queue--;
		pFIFOBuffer->fifo_queue[ 0 ] = NULL;
		if ( pFIFOBuffer->num_of_in_queue > 0 )
		{
			for ( i = 1; i<=pFIFOBuffer->num_of_in_queue; i++ )
			{
				pFIFOBuffer->fifo_queue[i]->fifo_index--;
				pFIFOBuffer->fifo_queue[i-1] = pFIFOBuffer->fifo_queue[i];
			}
		}
		return pBlockBuffer;
	} 

	return NULL;
}

BLOCK_BUFFER* TopBlockBuffer( FIFO_BUFFER* pFIFOBuffer )
{
	return pFIFOBuffer->fifo_queue[ 0 ];
}