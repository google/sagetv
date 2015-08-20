/*
 * default memory allocator for libavutil
 * Copyright (c) 2002 Fabrice Bellard.
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
 * @file mem.c
 * default memory allocator for libavutil.
 */

#include "common.h"

/* here we can use OS dependent allocation functions */
#undef malloc
#undef free
#undef realloc

#ifdef HAVE_MALLOC_H
#include <malloc.h>
#endif

/* you can redefine av_malloc and av_free in your project to use your
   memory allocator. You do not need to suppress this file because the
   linker will do it automatically */

void *av_malloc(unsigned int size)
{
    void *ptr;
#ifdef CONFIG_MEMALIGN_HACK
    long diff;
#endif

    /* let's disallow possible ambiguous cases */
    if(size > (INT_MAX-16) )
        return NULL;

#ifdef CONFIG_MEMALIGN_HACK
    ptr = malloc(size+16);
    if(!ptr)
        return ptr;
    diff= ((-(long)ptr - 1)&15) + 1;
    ptr += diff;
    ((char*)ptr)[-1]= diff;
#elif defined (HAVE_MEMALIGN)
    ptr = memalign(16,size);
    /* Why 64?
       Indeed, we should align it:
         on 4 for 386
         on 16 for 486
         on 32 for 586, PPro - k6-III
         on 64 for K7 (maybe for P3 too).
       Because L1 and L2 caches are aligned on those values.
       But I don't want to code such logic here!
     */
     /* Why 16?
        because some cpus need alignment, for example SSE2 on P4, & most RISC cpus
        it will just trigger an exception and the unaligned load will be done in the
        exception handler or it will just segfault (SSE2 on P4)
        Why not larger? because i didnt see a difference in benchmarks ...
     */
     /* benchmarks with p3
        memalign(64)+1          3071,3051,3032
        memalign(64)+2          3051,3032,3041
        memalign(64)+4          2911,2896,2915
        memalign(64)+8          2545,2554,2550
        memalign(64)+16         2543,2572,2563
        memalign(64)+32         2546,2545,2571
        memalign(64)+64         2570,2533,2558

        btw, malloc seems to do 8 byte alignment by default here
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

static unsigned int uncachedmembuf = 0;
static mspace uncachedmem = NULL;
#undef fprintf
#undef fflush
#endif

void *av_realloc(void *ptr, unsigned int size)
{
#ifdef CONFIG_MEMALIGN_HACK
    int diff;
#endif

    /* let's disallow possible ambiguous cases */
    if(size > (INT_MAX-16) )
        return NULL;
#ifdef EM8622UNCACHED
    if (ptr && ((unsigned int)ptr)>= uncachedmembuf && ((unsigned int)ptr) < (uncachedmembuf+4*1024*1024))
    {
        fprintf(stderr, "realloc in uncached? %X\n",(unsigned int)ptr);
        return NULL;
    }
#endif
#ifdef CONFIG_MEMALIGN_HACK
    //FIXME this isn't aligned correctly, though it probably isn't needed
    if(!ptr) return av_malloc(size);
    diff= ((char*)ptr)[-1];
    ptr = realloc(ptr - diff, size + diff) + diff;
#else
    ptr = realloc(ptr, size);
#endif
    return ptr;
}


#ifdef EM8622UNCACHED

static mspace initUncached(int size)
{
    void *buffer = av_malloc(size);
    mspace m1;
    unsigned char *tmpbuf = av_malloc(16384);
    int i;
    fprintf(stderr, "Creating mspace at %X\n",(unsigned int)buffer);
    buffer = (void *) (((unsigned int)buffer)&0x7FFFFFFF);
    // Clean cache
    for(i=0;i<16384;i++) tmpbuf[i]=i;
    uncachedmembuf = (unsigned int)buffer;
    m1 = create_mspace_with_base(buffer, size, 0);
    av_free(tmpbuf);
    return m1;
}

void *av_mallocUncached(unsigned int size)
{
    static int initialized=0;
    void *outptr;
    if(!initialized)
    {
        uncachedmem = initUncached(4*1024*1024);
        initialized=1;
    }
    outptr = mspace_memalign(uncachedmem, 16, size);
//    fprintf(stderr, "Allocating %d bytes uncached ret %X\n", size, (unsigned int)outptr);
    return outptr;
}

#endif


void av_free(void *ptr)
{
    /* XXX: this test should not be needed on most libcs */
#ifdef EM8622UNCACHED
    if (ptr && ((unsigned int)ptr)>= uncachedmembuf && ((unsigned int)ptr) < (uncachedmembuf+4*1024*1024))
    {
//        fprintf(stderr, "Freeing %X\n", (unsigned int)ptr);
        mspace_free(uncachedmem, ptr);
        return;
    }
#endif
    if (ptr)
#ifdef CONFIG_MEMALIGN_HACK
        free(ptr - ((char*)ptr)[-1]);
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
    void *ptr;

    ptr = av_malloc(size);
    if (ptr)
        memset(ptr, 0, size);
    return ptr;
}

char *av_strdup(const char *s)
{
    char *ptr;
    int len;
    len = strlen(s) + 1;
    ptr = av_malloc(len);
    if (ptr)
        memcpy(ptr, s, len);
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
