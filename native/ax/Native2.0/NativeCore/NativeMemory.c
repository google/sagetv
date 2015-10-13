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
#include <memory.h>
#include <string.h>
#include "NativeMemory.h"
#include "NativeCore.h"

////ZQ remove ME
//int  _mem_error_flag = 0;
//void set_mem_error_flag()  { ++_mem_error_flag; }
//void reset_mem_error_flag(){ _mem_error_flag=0; }
//int  mem_error_flag()	   { return _mem_error_flag; }
////ZQ remove ME

static void _log_error( char* mesg )
{
	FILE* fp=fopen( "ErrorCatch.log", "a+");
	if ( fp != NULL )
	{
		fprintf( fp, "%s.\r\n", mesg );
		fflush( fp );
		fclose( fp );
	}
}


#ifdef  MEMORY_CHECK
#define MAX_ALLOC_BLOCK  1024*10

#define NAME_SIZE  64
void* alloc_ptr_tbl[MAX_ALLOC_BLOCK]={0};
int   alloc_size_tbl[MAX_ALLOC_BLOCK]={0};
int   alloc_line_tbl[MAX_ALLOC_BLOCK]={0};
char  alloc_name_tbl[MAX_ALLOC_BLOCK][NAME_SIZE]={0};
unsigned long _memory_allocated = 0;
unsigned long _peak_memory_alloc = 0;
int _peak_memory_block = 0;
int overflow_flag = 0;
int _sagetv_memory_tracking = 0;
void  MemoryTrack( )
{
	_sagetv_memory_tracking++;
}

void MemoryReport( )
{
	int i;

	//only allow the last tracking to get a report
	if ( _sagetv_memory_tracking > 0 )
		_sagetv_memory_tracking--;
	if ( _sagetv_memory_tracking > 0 )
		return;

	SageLog(( _LOG_ERROR, 1, TEXT("Heap memory summary  peak memory:%d peak memory block:%d (max:%d, overflow:%d), unfree:%d"), 
		     _peak_memory_alloc, _peak_memory_block, MAX_ALLOC_BLOCK, overflow_flag, _memory_allocated ));
	for ( i = 0; i<MAX_ALLOC_BLOCK; i++ )
	{
		if ( alloc_ptr_tbl[i] != 0 )
		{
			SageLog(( _LOG_ERROR, 1, TEXT(" unfree memory block size:%d at line:%d of file:\"%s\""),  
				               alloc_size_tbl[i], alloc_line_tbl[i], alloc_name_tbl[i] ));
			//free( alloc_ptr_tbl[i] );
		}
	}
}

/*// trigger debug
static void _raise_exception()
{
	char *p;
	p = (char*)01;
	*p = 99;  //raise a exception for Dr.Watson
}
#define WINAPI __stdcall
unsigned long WINAPI GetCurrentThreadId(void);

//ZQ REMOVE ME
void CheckThreadRacing()
{
	static long thread_id=0;
	if ( thread_id == 0 )
	{
		thread_id = GetCurrentThreadId();
		SageLog(( _LOG_ERROR, 1, TEXT("Thread first call id:%d-%d line:%d of file:\"%s\"\n"), 
			       thread_id, GetCurrentThreadId(), line, filename ));
		thread_id = GetCurrentThreadId();
	}
	else
	if ( thread_id != GetCurrentThreadId() )
	{
		SageLog(( _LOG_ERROR, 1, TEXT("Thread racing id:%d-%d line:%d of file:\"%s\"\n"), 
			       thread_id, GetCurrentThreadId(), line, filename ));
		thread_id = GetCurrentThreadId();
	}
}
*/
typedef struct 
{
	char tag[2];
	unsigned short index;
	unsigned short size;
    char padding[2];
} _MEM_INF_;
#define _MEM_INF_SIZE  (int)sizeof(_MEM_INF_)

int valid_mem_block( void* p, int line, char* filename  )
{
	_MEM_INF_ *mem_ptr=(_MEM_INF_*)((char*)p-_MEM_INF_SIZE);
	if ( mem_ptr->index < MAX_ALLOC_BLOCK )
	{
		if (alloc_ptr_tbl[mem_ptr->index] == p )
			return 1;
		else
		if ( mem_ptr->tag[0] != 'Z'|| mem_ptr->tag[1] != 'Q' )
		{
			char buf[128]={0};
			snprintf( buf, sizeof(buf)-1, "Unknown memory block 0x%p at line:%d of file:\"%s\" ", p, line, filename );
			SageLog(( _LOG_ERROR, 1, TEXT("%s"), buf ));
			_log_error( buf );
			return 0;
		} else
		{
			char buf[256]={0};
			char *lost_fname="";
			int lost_size=0, lost_line=0;
			void *lost_addr=0;
			_MEM_INF_ *mem_ptr=(_MEM_INF_*)((char*)p-_MEM_INF_SIZE);
			if ( mem_ptr->index <MAX_ALLOC_BLOCK ) 
			{
				lost_addr=alloc_ptr_tbl[mem_ptr->index];
				lost_size= alloc_size_tbl[mem_ptr->index];
				if ( lost_addr && lost_size )
				{
					lost_line= alloc_line_tbl[mem_ptr->index]; 
					lost_fname = alloc_name_tbl[mem_ptr->index];
				}
			}

			snprintf( buf, sizeof(buf)-1, TEXT( ">>>>Invalid memory block, addr:0x%p line:%d of \"%s\";(addr:0x%p line:%d of \"%s\" size:%d(%d) )"),
						p, line, filename,	lost_addr, lost_line, lost_fname, lost_size, mem_ptr->size );
			SageLog(( _LOG_ERROR, 1, TEXT("%s"), buf ));
			_log_error( buf );

			return 0;
		}
	}
	return 1;
}

void* sagetv_malloc1( int size, int line, char* filename )
{
	int i = 0;
	char* p = (char*)malloc( size+_MEM_INF_SIZE );
	if ( p ) memset( p, 0, size+_MEM_INF_SIZE );
	else
	{
		SageLog(( _LOG_ERROR, 1, TEXT( "Out of memory size %d at line:%d of file"), size, line, filename ));
		_log_error( "Out of memory" );
		assert( 0 );
		return NULL;
	}
	p += _MEM_INF_SIZE;
	if (  size > 1024*2048 )
	{
		char buf[128];
		SageLog(( _LOG_ERROR, 1, TEXT("Memeory alloc too big size block,  size:%d line:%d of file:\"%s\"\n"), size, line, filename ));
		sprintf( buf, "Memeory alloc too big size block,  size:%d line:%d of file:\"%s\"\n", size, line, filename ); 
		_log_error( buf );
		return NULL;
	}

	for ( i=0; i<MAX_ALLOC_BLOCK; i++ )
		if ( alloc_ptr_tbl[i] == 0 )
			break;

	if ( i<MAX_ALLOC_BLOCK )
	{
		alloc_ptr_tbl[i]  = p;
		alloc_size_tbl[i] = size;
		alloc_line_tbl[i] = line;
		strncpy( alloc_name_tbl[i], filename, NAME_SIZE-1 );
		_memory_allocated += size;
		if ( _memory_allocated > _peak_memory_alloc )
			_peak_memory_alloc += _memory_allocated;
	} else
		overflow_flag = 1;

{
	_MEM_INF_ *mem_ptr=(_MEM_INF_*)((char*)p-_MEM_INF_SIZE);
	mem_ptr->tag[0]='Z';
	mem_ptr->tag[1]='Q';
	mem_ptr->size = size & 0xffff;
	mem_ptr->index = i<MAX_ALLOC_BLOCK ? i:0xffff;
}

	if ( _peak_memory_block < i ) _peak_memory_block = i;

	valid_mem_block( p, line, filename  );

	return (void*)p; 

}

void sagetv_free1( void* p, int line, char* filename )
{
	int i;
	{
		for ( i=0; i<MAX_ALLOC_BLOCK; i++ )
			if ( p == alloc_ptr_tbl[i] )
				break;
		
		if ( i<MAX_ALLOC_BLOCK )
		{
			_memory_allocated -= alloc_size_tbl[i];
			alloc_ptr_tbl[i] = 0;
			alloc_size_tbl[i] = 0;
		} else
		if ( !overflow_flag )
		{
			_MEM_INF_ *mem_ptr=(_MEM_INF_*)((char*)p-_MEM_INF_SIZE);
			if ( mem_ptr->tag[0] != 'Z'|| mem_ptr->tag[1] != 'Q' )
			{
				char buf[128]={0};
				snprintf( buf, sizeof(buf)-1, "Try to free unknown memory block 0x%p at line:%d of file:\"%s\" ", p, line, filename );
				SageLog(( _LOG_ERROR, 1, TEXT("%s"), buf ));
				_log_error( buf );
			}
			else
			{
				char buf[256]={0};
				char *lost_fname="";
				int lost_size=0, lost_line=0;
				void *lost_addr=0;
				_MEM_INF_ *mem_ptr=(_MEM_INF_*)((char*)p-_MEM_INF_SIZE);
				if ( mem_ptr->index <MAX_ALLOC_BLOCK ) 
				{
					lost_addr=alloc_ptr_tbl[mem_ptr->index];
					lost_size= alloc_size_tbl[mem_ptr->index];
					if ( lost_addr && lost_size )
					{
						lost_line= alloc_line_tbl[mem_ptr->index]; 
						lost_fname = alloc_name_tbl[mem_ptr->index];
					}
				}
				snprintf( buf, sizeof(buf)-1, TEXT( ">>>>Mem inf was lost, addr:0x%p line:%d of \"%s\";(addr:0x%p line:%d of \"%s\" size:%d(%d) )"),
							p, line, filename,	lost_addr, lost_line, lost_fname, lost_size, mem_ptr->size );
				SageLog(( _LOG_ERROR, 1, TEXT("%s"), buf ));
				_log_error( buf );
			}
		}
	}
	if ( p ) 
		p = ((char *)p) - _MEM_INF_SIZE;

	free( p );
}

int sagetv_mem_loc( void* p )
{
	int i;
	//if ( !overflow_flag )
	{
		for ( i=0; i<MAX_ALLOC_BLOCK; i++ )
			if ( p == alloc_ptr_tbl[i] )
				break;
		
		if ( i<MAX_ALLOC_BLOCK )
		{
			return i;
		} 
	}
	return -1;
}

char* sagetv_mem_inf( int loc, char *buf, int buf_size )
{
	if ( loc < 0 || loc >= MAX_ALLOC_BLOCK )
	{
		snprintf( buf, buf_size, "invalid memory index of manager %d", loc );
		return buf; 
	}

	snprintf( buf, buf_size, "loc:%d size:%d, line:%d file:%s", loc,
		alloc_size_tbl[loc], alloc_line_tbl[loc], alloc_name_tbl[loc] );
	return buf;
}

#else

#if 0	// Hack - fn duplicated in TSParser.c; linker complains 'already defined'
void* sagetv_malloc2( int size, int line )
{
	char* p;;
	if ( size < 0 || size > 1024*512 )
	{
		char buf[128];
		sprintf( buf, "!!!Memeory alloc size invalid size:%d TSParser.c::line %d\n", size, line ); 
		_log_error( buf );

		return NULL;
	}

	p = (char*)malloc( size );
	if ( p ) memset( p, 0, size );
	else
	{
		char buf[128];
		sprintf( buf, "!!!Memeory alloc failed  size:%d TSParser.c::line %d\n", size, line ); 
		_log_error( buf );
	}

	return (void*)p; 
}
#endif // end HACK

void  sagetv_free2( void* p, int line )
{
	free( p );
}

void* sagetv_malloc( int size )
{
	char* p = (char*)malloc( size );
	if ( p ) memset( p, 0, size );
	return (void*)p; 
}

void  sagetv_free( void* p )
{
	free( p );
}
#endif


