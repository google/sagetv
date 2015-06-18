/*****************************************************************************
 *
 *  XVID MPEG-4 VIDEO CODEC
 *  - XviD Decoder part of the DShow Filter  -
 *
 *  Copyright(C) 2002-2003 Peter Ross <pross@xvid.org>
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
 * $Id: IXvidDecoder.h 1985 2011-05-18 09:02:35Z Isibaar $
 *
 ****************************************************************************/

#ifndef _IXVID_H_
#define _IXVID_H_

#ifdef __cplusplus
extern "C" {
#endif


DEFINE_GUID(IID_IXvidDecoder,
	0x00000000, 0x4fef, 0x40d3, 0xb3, 0xfa, 0xe0, 0x53, 0x1b, 0x89, 0x7f, 0x98);

DECLARE_INTERFACE_(IXvidDecoder, IUnknown)
{
};



#ifdef __cplusplus
}
#endif

#endif /* _IXVID_H_ */
