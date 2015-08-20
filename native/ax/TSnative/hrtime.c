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
#include "TSnative.h"
#include "hrtime.h"


#ifdef WIN32
#include <windows.h>
void hr_time( LONGLONG *time )
{
	LARGE_INTEGER t;
	QueryPerformanceCounter( &t );
	*time = t.QuadPart;
}
unsigned long hr_duration( LONGLONG *start_time, LONGLONG *stop_time )
{
	LARGE_INTEGER cpu_frq;
	if ( !QueryPerformanceFrequency(&cpu_frq) )
		return 0;
	cpu_frq.QuadPart /= 1000;
	return (unsigned long)(((*stop_time - *start_time)*1000)/cpu_frq.QuadPart); 
}

LONGLONG hr_duration_long( LONGLONG *start_time, LONGLONG *stop_time )
{
	LARGE_INTEGER cpu_frq;
	if ( !QueryPerformanceFrequency(&cpu_frq) )
		return 0;
	cpu_frq.QuadPart /= 1000;
	return (unsigned long)((*stop_time - *start_time)*1000/cpu_frq.QuadPart); 
}



#endif

#ifdef LINUX
#endif

