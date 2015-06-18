/*
	writeall and readall (header)
	AUP2, Sec. 2.09, 2.10

	Copyright 2003 by Marc J. Rochkind. All rights reserved.
	May be copied only for purposes and under conditions described
	on the Web page www.basepath.com/aup/copyright.htm.

	The Example Files are provided "as is," without any warranty;
	without even the implied warranty of merchantability or fitness
	for a particular purpose. The author and his publisher are not
	responsible for any damages, direct or incidental, resulting
	from the use or non-use of these Example Files.

	The Example Files may contain defects, and some contain deliberate
	coding mistakes that were included for educational reasons.
	You are responsible for determining if and how the Example Files
	are to be used.

*/
#ifndef _EXTIO_H_
#define _EXTIO_H_

ssize_t writeall(int fd, const void *buf, size_t nbyte);
ssize_t readall(int fd, void *buf, size_t nbyte);

#endif /* _EXTIO_H_ */
