/*
 * default memory allocator for libavutil
 * Copyright (c) 2002 Fabrice Bellard
 *
 * This file is part of FFmpeg.
 *
 * FFmpeg is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * FFmpeg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with FFmpeg; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

/**
 * @file
 * default memory allocator for libavutil
 */

#include "config.h"
#include "log.h"

#include <limits.h>
#include <stdlib.h>
#include <string.h>
#if HAVE_MALLOC_H
#include <malloc.h>
#endif

#include "avutil.h"
#include "mem.h"

/* here we can use OS-dependent allocation functions */
#undef free
#undef malloc
#undef realloc

#ifdef MALLOC_PREFIX

#define malloc         AV_JOIN(MALLOC_PREFIX, malloc)
#define memalign       AV_JOIN(MALLOC_PREFIX, memalign)
#define posix_memalign AV_JOIN(MALLOC_PREFIX, posix_memalign)
#define realloc        AV_JOIN(MALLOC_PREFIX, realloc)
#define free           AV_JOIN(MALLOC_PREFIX, free)

void *malloc(size_t size);
void *memalign(size_t align, size_t size);
int   posix_memalign(void **ptr, size_t align, size_t size);
void *realloc(void *ptr, size_t size);
void  free(void *ptr);

#endif /* MALLOC_PREFIX */

/* You can redefine av_malloc and av_free in your project to use your
   memory allocator. You do not need to suppress this file because the
   linker will do it automatically. */

void *av_malloc(unsigned int size)
{
    void *ptr = NULL;
#if CONFIG_MEMALIGN_HACK
    long diff;
#endif

    /* let's disallow possible ambiguous cases */
    if(size > (INT_MAX-16) )
        return NULL;

#ifdef EM8622
    if(size > 8*1024*1024)
        return NULL;
#endif

#if CONFIG_MEMALIGN_HACK
    ptr = malloc(size+16);
    if(!ptr)
        return ptr;
    diff= ((-(long)ptr - 1)&15) + 1;
    ptr = (char*)ptr + diff;
    ((char*)ptr)[-1]= diff;
#elif HAVE_POSIX_MEMALIGN
    if (posix_memalign(&ptr,16,size))
        ptr = NULL;
#elif HAVE_MEMALIGN
    ptr = memalign(16,size);
    /* Why 64?
       Indeed, we should align it:
         on 4 for 386
         on 16 for 486
         on 32 for 586, PPro - K6-III
         on 64 for K7 (maybe for P3 too).
       Because L1 and L2 caches are aligned on those values.
       But I don't want to code such logic here!
     */
     /* Why 16?
        Because some CPUs need alignment, for example SSE2 on P4, & most RISC CPUs
        it will just trigger an exception and the unaligned load will be done in the
        exception handler or it will just segfault (SSE2 on P4).
        Why not larger? Because I did not see a difference in benchmarks ...
     */
     /* benchmarks with P3
        memalign(64)+1          3071,3051,3032
        memalign(64)+2          3051,3032,3041
        memalign(64)+4          2911,2896,2915
        memalign(64)+8          2545,2554,2550
        memalign(64)+16         2543,2572,2563
        memalign(64)+32         2546,2545,2571
        memalign(64)+64         2570,2533,2558

        BTW, malloc seems to do 8-byte alignment by default here.
     */
#else
    ptr = malloc(size);
#endif
    return ptr;
}

#ifdef EM8622UNCACHED

typedef void* mspace;
extern mspace create_mspace_with_base(void* base, size_t capacity, int locked);
extern void* mspace_malloc(mspace msp, size_t bytes);
extern void mspace_free(mspace msp, void* mem);
extern size_t mspace_footprint(mspace msp);
extern void* mspace_memalign(mspace msp, size_t alignment, size_t bytes);

#define UNCACHEDCOUNT 3
// 1 page less than 2 megs
#define UNCACHEDSIZE 2*1024*1022
static unsigned int uncachedmembuf[UNCACHEDCOUNT] = {0,};
static mspace uncachedmem[UNCACHEDCOUNT] = {NULL, };
#undef fprintf
#undef fflush
#endif

#undef fprintf
#undef fflush

void *av_realloc(void *ptr, unsigned int size)
{
    int i;
#if CONFIG_MEMALIGN_HACK
    int diff;
#endif

    /* let's disallow possible ambiguous cases */
    if(size > (INT_MAX-16) )
        return NULL;

	/* catch cases of runaway memor allocation */
	if(size > (16*1024*1024))	// warning if > 16 megs
		fprintf(stderr, "WARNING: Large av_realloc, size=%d\n", size);
#ifdef EM8622
    if(size > 8*1024*1024)
        return NULL;
#endif
#ifdef EM8622UNCACHED
    
    for(i=0;i<UNCACHEDCOUNT;i++)
    {
        if (ptr && ((unsigned int)ptr)>= uncachedmembuf[i] && ((unsigned int)ptr) < (uncachedmembuf[i]+UNCACHEDSIZE))
        {
            fprintf(stderr, "realloc in uncached? %X\n",(unsigned int)ptr);
            return NULL;
        }
    }
#endif
#if CONFIG_MEMALIGN_HACK
    //FIXME this isn't aligned correctly, though it probably isn't needed
    if(!ptr) return av_malloc(size);
    diff= ((char*)ptr)[-1];
    return (char*)realloc((char*)ptr - diff, size + diff) + diff;
#else
    return realloc(ptr, size);
#endif
}

#ifdef EM8622UNCACHED

static mspace initUncached(int size, int index)
{
    void *buffer = av_malloc(size);
    mspace m1;
    unsigned char *tmpbuf = av_malloc(16384);
    int i;
    fprintf(stderr, "Creating mspace at %X\n",(unsigned int)buffer);
    buffer = (void *) (((unsigned int)buffer)&0x7FFFFFFF);
    // Clean cache
    for(i=0;i<16384;i++) tmpbuf[i]=i;
    uncachedmembuf[index] = (unsigned int)buffer;
    m1 = create_mspace_with_base(buffer, size, 0);
    av_free(tmpbuf);
    return m1;
}

void *av_mallocUncached(unsigned int size)
{
    int i;
    static int initialized=0;
    void *outptr;
    if(!initialized)
    {
        for(i=0;i<UNCACHEDCOUNT;i++)
        {
            uncachedmem[i] = initUncached(UNCACHEDSIZE, i);
        }
        initialized=1;
    }
    for(i=0;i<UNCACHEDCOUNT;i++)
    {
        outptr = mspace_memalign(uncachedmem[i], 16, size);
        if(outptr!=NULL) return outptr;
    }
//    fprintf(stderr, "Allocating %d bytes uncached ret %X\n", size, (unsigned int)outptr);
    return outptr;
}

#endif

void av_free(void *ptr)
{
    /* XXX: this test should not be needed on most libcs */
#ifdef EM8622UNCACHED
    int i;
    for(i=0;i<UNCACHEDCOUNT;i++)
    {
        if (ptr && ((unsigned int)ptr)>= uncachedmembuf[i] && ((unsigned int)ptr) < (uncachedmembuf[i]+UNCACHEDSIZE))
        {
//            fprintf(stderr, "Freeing %X\n", (unsigned int)ptr);
            mspace_free(uncachedmem[i], ptr);
            return;
        }
    }
#endif
    if (ptr)
#if CONFIG_MEMALIGN_HACK
        free((char*)ptr - ((char*)ptr)[-1]);
#else
        free(ptr);
#endif
}

void av_freep(void *arg)
{
    void **ptr= (void**)arg;
    av_free(*ptr);
    *ptr = NULL;
}

void *av_mallocz(unsigned int size)
{
    void *ptr = av_malloc(size);
    if (ptr)
        memset(ptr, 0, size);
    return ptr;
}

char *av_strdup(const char *s)
{
    char *ptr= NULL;
    if(s){
        int len = strlen(s) + 1;
        ptr = av_malloc(len);
        if (ptr)
            memcpy(ptr, s, len);
    }
    return ptr;
}

#ifdef EM8622UNCACHED
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#endif

// We implement this to use kernel copy
void *av_memcpy(void *dest, const void *src, unsigned int size)
{
#ifdef EM8622UNCACHED
    static int devmemfd=-1;
    static unsigned long long bytescopied=0;

    if(devmemfd<0)
    {
        devmemfd=open("/dev/mem",O_RDWR);
    }
    #undef fprintf
   /* fprintf(stderr, "av_memcpy: pread %X %X %X\n",(unsigned int)dest, (unsigned int)src, size);
    fflush(stderr);
    usleep(1000);*/
    if(((((unsigned int)dest)|((unsigned int)src))&0x80000000)==0 && size > 256)
    {
        lseek(devmemfd, ((unsigned int)src), SEEK_SET);
        if(read(devmemfd, dest, size)!=size)
        {
            fprintf(stderr, "av_memcpy: pread %X %X %X failed\n",(unsigned int)dest, (unsigned int)src, size);
        }
        bytescopied+=size;
        return dest;
    }
    else
#endif
    {
        return memcpy(dest,src,size);
    }
}
