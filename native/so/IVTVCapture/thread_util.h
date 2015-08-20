/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef __THREAD_UTIL__
#define __THREAD_UTIL__

#include <pthread.h>

typedef struct
{
    pthread_mutex_t id;
}ACL_mutex;

typedef struct
{
    pthread_t t;
}ACL_Thread;

ACL_mutex * ACL_CreateMutex();
void ACL_RemoveMutex(ACL_mutex *mutex);
int ACL_LockMutex(ACL_mutex *mutex);
int ACL_UnlockMutex(ACL_mutex *mutex);
ACL_Thread * ACL_CreateThread(int (*threadfunc)(void *), void *data);
int ACL_ThreadJoin(ACL_Thread * t);
int ACL_RemoveThread(ACL_Thread * t);
void ACL_Delay(unsigned int delay);

#endif // __THREAD_UTIL__
