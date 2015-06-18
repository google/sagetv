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

public class UPosixIPC {
	/*
		Not sure yet where this should be.
	*/
	static {
		System.loadLibrary("jtux");
		System.out.println("Loaded jtux OK.");
	}

	/**
		Java version of C struct mq_attr.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_mq_attr">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_mq_attr {
		public long mq_flags;		// flags
		public long mq_maxmsg;		// max number of messages
		public long mq_msgsize;		// max message size
		public long mq_curmsgs;		// number of messages currently queued
	}

	/**
		Calls mmap.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mmap">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long mmap(long addr, int len, int prot, int flags, int fd, long off) throws UErrorException;
	/**
		Calls mq_close.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_close">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void mq_close(long mqd) throws UErrorException;
	/**
		Calls mq_getattr.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_getattr">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void mq_getattr(long mqd, s_mq_attr attr) throws UErrorException;
	/**
		Calls mq_notify.
		<p>
		SIGEV_THREAD not supported. Almost works for SIGEV_SIGNAL, but JNI code for
		signal handler is unable to find some classes it needs (on Solaris and Java 1.3,
		anyway).
		Calls mq_notify.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_notify">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void mq_notify(long mqd, UProcess.s_sigevent ep) throws UErrorException;
	/**
		Calls mq_open.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_open">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long mq_open(String name, int flags) throws UErrorException;
	/**
		Calls mq_open.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_open">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long mq_open(String name, int flags, int perms, s_mq_attr attr) throws UErrorException;
	/**
		Calls mq_receive.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_receive">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int mq_receive(long mqd, byte[] msg, int msgsize,
	  UUtil.IntHolder priority) throws UErrorException;
	/**
		Calls mq_send.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_send">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void mq_send(long mqd, byte[] msg, int msgsize,
	  int priority) throws UErrorException;
	/**
		Calls mq_setattr.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_setattr">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void mq_setattr(long mqd, s_mq_attr attr, s_mq_attr oattr) throws UErrorException;
	/**
		Calls mq_timedreceive.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_timedreceive">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int mq_timedreceive(long mqd, byte[] msg, int msgsize,
	  UUtil.IntHolder priority, UProcess.s_timespec tmout) throws UErrorException;
	/**
		Calls mq_timedsend.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_timedsend">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void mq_timedsend(long mqd, byte[] msg, int msgsize,
	  int priority, UProcess.s_timespec tmout) throws UErrorException;
	/**
		Calls mq_unlink.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#mq_unlink">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void mq_unlink(String name) throws UErrorException;
	/**
		Calls munmap.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#munmap">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void munmap(long addr, int len) throws UErrorException;
	/**
		Calls sem_close.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_close">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sem_close(long sem) throws UErrorException;
	/**
		Calls sem_destroy.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_destroy">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sem_destroy(long sem) throws UErrorException;
	/**
		Calls sem_getvalue.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_getvalue">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sem_getvalue(long sem, UUtil.IntHolder valuep) throws UErrorException;
	/**
		Calls sem_init.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_init">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sem_init(long sem, int pshared, int value) throws UErrorException;
	/**
		Calls sem_open.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_open">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long sem_open(String name, int flags) throws UErrorException;
	/**
		Calls sem_open.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_open">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long sem_open(String name, int flags, int perms, int value) throws UErrorException;
	/**
		Calls sem_post.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_post">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sem_post(long sem) throws UErrorException;
	/**
		Calls sem_timedwait.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_timedwait">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sem_timedwait(long sem, UProcess.s_timespec time) throws UErrorException;
	/**
		Calls sem_trywait.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_trywait">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sem_trywait(long sem) throws UErrorException;
	/**
		Calls sem_unlink.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_unlink">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sem_unlink(String name) throws UErrorException;
	/**
		Calls sem_wait.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sem_wait">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sem_wait(long sem) throws UErrorException;
	/**
		Calls shm_open.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#shm_open">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int shm_open(String name, int flags, int perms) throws UErrorException;
	/**
		Calls shm_unlink.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#shm_unlink">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void shm_unlink(String name) throws UErrorException;
}
