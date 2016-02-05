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
#include <pthread.h>
#include <malloc.h>
#include <sys/time.h>
#include <signal.h>
#include <errno.h>
#include "thread_util.h"

ACL_mutex * ACL_CreateMutex()
{
    ACL_mutex *mutex;
    pthread_mutexattr_t attr;
    
    mutex=(ACL_mutex *)malloc(sizeof(ACL_mutex));
    if(NULL!=mutex)
    {
        pthread_mutexattr_init(&attr);
        pthread_mutexattr_setkind_np(&attr, PTHREAD_MUTEX_RECURSIVE_NP);
        if(pthread_mutex_init(&mutex->id, &attr)!=0)
        {
            free(mutex);
            mutex=NULL;
        }
    }
    return mutex;
}

void ACL_RemoveMutex(ACL_mutex *mutex)
{
    if(NULL!=mutex)
    {
        pthread_mutex_destroy(&mutex->id);
        free(mutex);
    }
}

int ACL_LockMutex(ACL_mutex *mutex)
{
    if(NULL==mutex) return -1;
    if(pthread_mutex_lock(&mutex->id)<0)
    {
        return -1;
    }
    return 0;
}

int ACL_UnlockMutex(ACL_mutex *mutex)
{
    if(NULL==mutex) return -1;
    if(pthread_mutex_unlock(&mutex->id)<0)
    {
        return -1;
    }
    return 0;
}

ACL_Thread * ACL_CreateThread(void * (*threadfunc)(void *), void *data)
{
    ACL_Thread *thread;
    pthread_attr_t attr;
    int rc;

    thread=(ACL_Thread *)malloc(sizeof(ACL_Thread));
    if(NULL!=thread)
    {
        pthread_attr_init(&attr);
        pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

        rc=pthread_create(&thread->t, &attr, threadfunc, data); 
        if(0!=rc)
        {
            free(thread);
            thread=NULL;
        }
    }

    return thread;
}

int ACL_RemoveThread(ACL_Thread *t)
{
    free(t);
    return 0;
}

void * ACL_ThreadJoin(ACL_Thread *thread)
{
    void* retval;
    pthread_join(thread->t, &retval);
    return retval;
}

void ACL_Delay(unsigned int delay)
{
    struct timeval tv;
    int rv = 1;
    tv.tv_sec = delay/1000;
    tv.tv_usec = (delay%1000)*1000;
    errno = EINTR;
    while(rv!=0 && (errno == EINTR))
    {
        errno = 0;
        rv = select(0, NULL, NULL, NULL, &tv);
    }
    //usleep(delay*1000);
}

