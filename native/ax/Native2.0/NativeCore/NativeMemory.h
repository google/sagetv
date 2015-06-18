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
#ifndef _MEMORY_H_
#define _MEMORY_H_

#if defined(_DEBUG) && defined(WIN32)
#define  MEMORY_CHECK
#else
#undef  MEMORY_CHECK
#endif 

#ifdef __cplusplus
extern "C" {
#endif


#ifdef MEMORY_CHECK
void* sagetv_malloc1( int size, int line, char* filename );
void  sagetv_free1( void* p, int line, char* filename );
void  MemoryReport( );
void  MemoryTrack( );
#define MEMORY_REPORT() MemoryReport( )
#define MEMORY_TRACK()  MemoryTrack( )
/*************************************************************/
#define SAGETV_MALLOC( x )		sagetv_malloc1( x, __LINE__, __FILE__ )
#define SAGETV_FREE( x )		sagetv_free1( x,  __LINE__, __FILE__ )
/*************************************************************/
#else
void* sagetv_malloc( int size );
void  sagetv_free( void* p );
#define MEMORY_REPORT() 
#define MEMORY_TRACK()  
/*************************************************************/
#define SAGETV_MALLOC( x )		sagetv_malloc( x  )
#define SAGETV_FREE( x )		sagetv_free( x  )
/*************************************************************/
#endif

#ifdef __cplusplus
}
#endif

#endif
