#ifndef _DSHOW_DEBUG_
#define _DSHOW_DEBUG_

#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

void OutputDebugStringf(char *fmt, ...);

#ifdef _DEBUG
#define DPRINTF OutputDebugStringf
#else
static __inline void 
DPRINTF(char *fmt, ...) { }
#endif

#ifdef __cplusplus
}
#endif

#endif /* _DSHOW_DEBUG */
