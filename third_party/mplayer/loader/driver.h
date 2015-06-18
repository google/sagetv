/*
 * Modified for use with MPlayer, detailed changelog at
 * http://svn.mplayerhq.hu/mplayer/trunk/
 * $Id: driver.h,v 1.3 2007-04-10 19:33:29 Narflex Exp $
 */

#ifndef loader_driver_h
#define	loader_driver_h

#ifdef __cplusplus
extern "C" {
#endif

#include "wine/windef.h"
#include "wine/driver.h"

void SetCodecPath(const char* path);
void CodecAlloc(void);
void CodecRelease(void);

HDRVR DrvOpen(LPARAM lParam2);
void DrvClose(HDRVR hdrvr);

#ifdef __cplusplus
}
#endif

#endif
