/*****************************************************************************
 *
 *  XVID MPEG-4 VIDEO CODEC
 *  - Debug header  -
 *
 *  Copyright(C) Peter Ross <pross@xvid.org>
 *
 *  This program is free software ; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation ; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY ; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program ; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
 *
 * $Id: debug.h 1985 2011-05-18 09:02:35Z Isibaar $
 *
 ****************************************************************************/

#ifndef _DEBUG_H_
#define _DEBUG_H_

#if defined(_DEBUG)
#include <stdio.h>	/* vsprintf */
#define DPRINTF_BUF_SZ  1024
static __inline void DPRINTF(char *fmt, ...)
{
	va_list args;
	char buf[DPRINTF_BUF_SZ];

	va_start(args, fmt);
	vsprintf(buf, fmt, args);
	OutputDebugString(buf);
}
#else
static __inline void DPRINTF(char *fmt, ...) { }
#endif

#endif /* _DEBUG_H_ */
