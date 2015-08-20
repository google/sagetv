/*
 * Copyright (c) 1985-2005 by Marc J. Rochkind. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice, this
 *    list of conditions and the following disclaimer in the documentation and/or
 *    other materials provided with the distribution.
 *  - The name of the copyright holder, Marc J. Rochkind, may not be used to endorse
 *    or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package jtux;

/**
*/

public class UFile {
	/*
		Not sure yet where this should be.
	*/
	static {
		System.loadLibrary("jtux");
	}

	/**
		Java version of C struct iovec.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_iovec">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_iovec {
		public byte[] iov_base;				// base address of data
		public int iov_len;				// size of this piece
	}

	/**
		Java version of C struct statvfs.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_statvfs">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_statvfs {
		public long f_bsize;			// block size
		public long f_frsize;			// fundamental (fblock) size
		public long f_blocks;			// total number of fblocks
		public long f_bfree;			// number of free fblocks
		public long f_bavail;			// number of avail. fblocks
		public long f_files;			// total number of i-numbers
		public long f_ffree;			// number of free i-numbers
		public long f_favail;			// number of avail. i-numbers
		public long f_fsid;				// file-system ID
		public long f_flag;				// flags (see below)
		public long f_namemax;			// max length of filename
	}

	/**
		Java version of C struct stat.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_stat">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_stat {
		public long st_dev;				// device ID of file system
		public int st_ino;				// i-number
		public int st_mode;				// mode (see below)
		public int st_nlink;			// number of hard links
		public long st_uid;				// user ID
		public long st_gid;				// group ID
		public long st_rdev;				// device ID (if special file)
		public long st_size;			// size in bytes
		public long st_atime;			// last access
		public long st_mtime;			// last data modification
		public long st_ctime;			// last i-node modification
		public int st_blksize;			// optimal I/O size
		public long st_blocks;			// allocated 512-byte blocks
	}

	/**
		Java version of C struct utimbuf.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_utimbuf">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_utimbuf {
		public long actime;				// access time
		public long modtime;			// modification time
	}

	/**
		Java version of fd_set.
	*/
	static public class fd_set {
		static {
			System.loadLibrary("jtux");
			System.out.println("Loaded");
		}
		public byte[] set = new byte[GetSize_fd_set()];

		native int GetSize_fd_set();
	}

	/**
		Java version of C struct pollfd.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_pollfd">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_pollfd {
		public int fd;					// file descriptor
		public short events;			// input event flags
		public short revents;			// output event flags
	}

	/**
		Calls access.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#access">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void access(String path, int what) throws UErrorException;
	/**
		Calls chmod.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#chmod">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void chmod(String path, int mode) throws UErrorException;
	/**
		Calls chown.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#chown">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void chown(String path, long uid, long gid) throws UErrorException;
	/**
		Calls close.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#close">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void close(int fd) throws UErrorException;
	/**
		Does the equivalent of creat, by calling open.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#creat">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static int creat(String path, int perms) throws UErrorException {
		return open(path, UConstant.O_WRONLY | UConstant.O_CREAT | UConstant.O_TRUNC, perms);
	}
	/**
		Calls dup.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#dup">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int dup(int fd) throws UErrorException;
	/**
		Calls dup2.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#dup2">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int dup2(int fd, int fd2) throws UErrorException;
	/**
		Calls fchmod.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#fchmod">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void fchmod(int fd, int mode) throws UErrorException;
	/**
		Calls fchown.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#fchown">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void fchown(int fd, long uid, long gid) throws UErrorException;
	/**
		Calls fcntl.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#fcntl">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int fcntl(int fd, int op, int arg) throws UErrorException;
	/**
		Calls FD_ZERO.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#FD_ZERO">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void FD_ZERO(fd_set set);
	/**
		Calls FD_SET.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#FD_SET">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void FD_SET(int fd, fd_set set);
	/**
		Calls FD_CLR.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#FD_CLR">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void FD_CLR(int fd, fd_set set);
	/**
		Calls FD_ISSET.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#FD_ISSET">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static boolean FD_ISSET(int fd, fd_set set);
	/**
		Calls fdatasync.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#fdatasync">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void fdatasync(int fd) throws UErrorException;
	/**
		Calls fstat.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#fstat">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void fstat(int fd, s_stat buf) throws UErrorException;
	/**
		Calls fstatvfs.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#fstatvfs">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void fstatvfs(int fd, s_statvfs buf) throws UErrorException;
	/**
		Calls fsync.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#fsync">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void fsync(int fd) throws UErrorException;
	/**
		Calls ftruncate.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#ftruncate">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void ftruncate(int fd, long length) throws UErrorException;
	/**
		Calls lchown.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#lchown">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void lchown(String path, long uid, long gid) throws UErrorException;
	/**
		Calls link.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#link">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void link(String oldpath, String newpath) throws UErrorException;
	/**
		Calls lockf.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#lockf">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void lockf(int fd, int op, long len) throws UErrorException;
	/**
		Calls lseek.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#lseek">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long lseek(int fd, long pos, int whence) throws UErrorException;
	/**
		Calls lstat.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#lstat">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void lstat(String path, s_stat buf) throws UErrorException;
	/**
		Calls mkfifo.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mkfifo">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void mkfifo(String path, int perms) throws UErrorException;
	/**
		Calls mknod.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mknod">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void mknod(String path, int mode, long dev) throws UErrorException;
	/**
		Calls mkstemp.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mkstemp">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int mkstemp(StringBuffer template) throws UErrorException;
	/**
		Calls open.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#open">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int open(String path, int flags, int perms) throws UErrorException;
	/**
		Calls open.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#open">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static int open(String path, int flags) throws UErrorException {
		return open(path, flags, 0);
	}
	/**
		Calls pipe.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#pipe">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void pipe(int[] pfd) throws UErrorException;
	/**
		Calls poll.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#poll">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int poll(s_pollfd[] fdinfo, int nfds, int timeout);
	/**
		Calls pread.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#pread">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int pread(int fd, byte[] buf, int nbytes, long offset) throws UErrorException;
	/**
		Calls pselect.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#pselect">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int pselect(int nfds, fd_set readset, fd_set writeset,
	  fd_set errorset, UProcess.s_timespec timeout, UProcess.sigset_t sigmask) throws UErrorException;
	/**
		Calls pwrite.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#pwrite">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int pwrite(int fd, byte[] buf, int nbytes, long position) throws UErrorException;
	/**
		Calls read.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#read">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int read(int fd, byte[] buf, int nbytes) throws UErrorException;
	/**
		Calls readlink.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#readlink">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int readlink(String path, byte[] buf, int bufsize) throws UErrorException;
	/**
		Calls readv.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#readv">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int readv(int fd, s_iovec[] iov, int iovcnt) throws UErrorException;
	/**
		Calls rename.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#rename">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void rename(String oldpath, String newpath) throws UErrorException;
	/**
		Calls S_ISBLK.
	*/
	public native static boolean S_ISBLK(int mode);
	/**
		Calls S_ISCHR.
	*/
	public native static boolean S_ISCHR(int mode);
	/**
		Calls S_ISDIR.
	*/
	public native static boolean S_ISDIR(int mode);
	/**
		Calls S_ISFIFO.
	*/
	public native static boolean S_ISFIFO(int mode);
	/**
		Calls S_ISLNK.
	*/
	public native static boolean S_ISLNK(int mode);
	/**
		Calls S_ISREG.
	*/
	public native static boolean S_ISREG(int mode);
	/**
		Calls S_ISSOCK.
	*/
	public native static boolean S_ISSOCK(int mode);
	/**
		Calls select.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#select">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int select(int nfds, fd_set readset, fd_set writeset,
	  fd_set errorset, UProcess.s_timeval timeout) throws UErrorException;
	/**
		Calls stat.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#stat">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void stat(String path, s_stat buf) throws UErrorException;
	/**
		Calls statvfs.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#statvfs">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void statvfs(String path, s_statvfs buf) throws UErrorException;
	/**
		Calls symlink.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#symlink">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void symlink(String oldpath, String newpath) throws UErrorException;
	/**
		Calls sync.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sync">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sync();
	/**
		Calls truncate.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#truncate">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void truncate(String path, long length) throws UErrorException;
	/**
		Calls unlink.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#unlink">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void unlink(String path) throws UErrorException;
	/**
		Calls utime.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#utime">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void utime(String path, s_utimbuf timbuf) throws UErrorException;
	/**
		Calls write.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#write">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	/**
		Calls ioctl.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#write">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int ioctl(int fd, int request, byte[] buf) throws UErrorException;
	/**
		Calls ioctl.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#write">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int ioctl2(int fd, int request, int arg) throws UErrorException;
	/**
		Calls write.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#write">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int write(int fd, byte[] buf, int nbytes) throws UErrorException;
	/**
		Calls writev.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#writev">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int writev(int fd, s_iovec[] iov, int iovcnt) throws UErrorException;
}
