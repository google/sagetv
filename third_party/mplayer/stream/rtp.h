/* Imported from the dvbstream project
 *
 * Modified for use with MPlayer, for details see the changelog at
 * http://svn.mplayerhq.hu/mplayer/trunk/
 * $Id: rtp.h,v 1.1 2007-04-10 20:11:30 Narflex Exp $
 */

#ifndef _RTP_H
#define _RTP_H

int read_rtp_from_server(int fd, char *buffer, int length);

#endif
