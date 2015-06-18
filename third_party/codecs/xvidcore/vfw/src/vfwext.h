/*****************************************************************************
 *
 *  XVID MPEG-4 VIDEO CODEC
 *  - experimental vfw-api-extensions -
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
 * $Id: vfwext.h 1985 2011-05-18 09:02:35Z Isibaar $
 *
 ****************************************************************************/

#ifndef _VFWEXT_H_
#define _VFWEXT_H_

/* VFWEXT */

#define VFWEXT_FOURCC   0xFFFFFFFF

typedef struct {
    DWORD   ciSize;       /* structure size */
    LONG    ciWidth;      /* frame width pixels */
    LONG    ciHeight;     /* frame height pixels */
    DWORD   ciRate;       /* frame rate/scale */
    DWORD   ciScale;      
    LONG    ciActiveFrame;  /* currently selected frame# */
    LONG    ciFrameCount;   /* total frames */
} VFWEXT_CONFIGURE_INFO_T;
#define VFWEXT_CONFIGURE_INFO   1


#endif  /* _VFWEXT_H_ */
