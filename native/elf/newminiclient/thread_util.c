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
    else
    {
        fprintf(stderr, "Error creating mutex\n");
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

int ACL_TryLockMutex(ACL_mutex *mutex)
{
    if(NULL==mutex) return -1;
    if(pthread_mutex_trylock(&mutex->id)!=0)
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

ACL_cond * ACL_CreateCond()
{
    ACL_cond *cond;
    cond=(ACL_cond *)malloc(sizeof(ACL_cond));
    if(NULL!=cond)
    {
        if(pthread_cond_init(&cond->c, NULL)!=0)
        {
            free(cond);
            cond=NULL;
        }
    }
    else
    {
        fprintf(stderr, "Error creating cond\n");
    }
    return cond;
}

void ACL_RemoveCond(ACL_cond *cond)
{
    if(NULL!=cond)
    {
        pthread_cond_destroy(&cond->c);
        free(cond);
    }
}

int ACL_WaitCondTimeout(ACL_cond *cond, ACL_mutex *mutex, int ns)
{
    struct timespec abstime;

    clock_gettime(CLOCK_REALTIME, &abstime);

    abstime.tv_nsec+=ns;
    while(abstime.tv_nsec>=1000000000)
    {
        abstime.tv_nsec-=1000000000;
        abstime.tv_sec+=1;
    }

    return pthread_cond_timedwait(&cond->c, &mutex->id, &abstime);
}

int ACL_SignalCond(ACL_cond *cond)
{
    return pthread_cond_signal(&cond->c);
}

ACL_Thread * ACL_CreateThread(int (*threadfunc)(void *), void *data)
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

int ACL_ThreadJoin(ACL_Thread *thread)
{
    int retval;
    pthread_join(thread->t, &retval);
    free(thread);
    return retval;
}

void ACL_Delay(unsigned int delay)
{
    struct timeval tv;
    int rv = 1;
    tv.tv_sec = delay/1000000;
    tv.tv_usec = (delay%1000000);
    errno = EINTR;
    while(rv!=0 && (errno == EINTR))
    {
        errno = 0;
        rv = select(0, NULL, NULL, NULL, &tv);
    }
    //usleep(delay*1000);
}

