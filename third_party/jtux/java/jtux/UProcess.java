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

import java.lang.reflect.*;

/**
	UProcess-related POSIX/SUS calls.
	<pre>
	Skipped:
		atexit -- C function, inappropriate for Java
		exec functions other than execvp -- "l" functions not possible; environment can be dealt
		  with separately (need funciton to access environ, though)
		_longjmp, _setjmp, longjmp, setjmp, siglongjmp, sigsetjmp -- not appropriate for Java
		signal functions -- because of interference with the JVM's use of signals. Some of the
		  functionality have been made to work, but others wouldn't faithfully implement
		  POSIX/SUS -- it would not be a good learning vehicle.
		ulimit -- messy; use getrlimit and setrlimit instead
		vfork -- obsolete
ToDo:
	waitid
	</pre>




*/
public class UProcess {
	static {
		System.loadLibrary("jtux");
	}

	/**
		Java version of C struct tms.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_tms">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_tms {
		public long tms_utime; /* user time */
		public long tms_stime; /* sys time */
		public long tms_cutime; /* user time of terminated children */
		public long tms_cstime; /* sys time of terminated children */
	}

	/**
		Java version of C type sigset_t.
	*/
	static public class sigset_t {
		static {
			System.loadLibrary("jtux");
			System.out.println("Loaded");
		}
		public byte[] set = new byte[GetSize_sigset_t()];

		native int GetSize_sigset_t();
	}

	/**
		Java version of C struct sigaction.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_sigaction">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_sigaction {
		Method sa_handler;
		public sigset_t sa_mask;
		public int sa_flags;
		Method sa_sigaction;
		int sa_actiontype = UConstant.SIG_ERR; // non-standard -- for Jtux's use
//void (*sa_sigaction)(int, siginfo_t *, void *);
/*
		public s_sigaction(String c, String m, sigset_t sigset, int flags)
		  throws NoSuchMethodException  {
			try {
				sa_handler = Class.forName(c).getMethod(m, new Class[] { Integer.TYPE });
			}
			catch (ClassNotFoundException e) {
				System.out.println(e);
			}
			catch (NoSuchMethodException e) {
				System.err.println("Make sure sigaction method is public.");
				throw e;
				//System.out.println(e);
			}
			//System.out.println(sa_handler);
			sa_mask = sigset;
			sa_flags = flags;
			sa_actiontype = UConstant.SIG_ERR;
		}
		public s_sigaction(String c, String m) throws NoSuchMethodException {
			this(c, m, null, 0);
		}
		public s_sigaction(int a) throws UErrorException {
			if (a != UConstant.SIG_IGN && a != UConstant.SIG_DFL)
				throw new UErrorException(UConstant.EINVAL);
			sa_actiontype = a;
		}
*/
		static Method get_handler_method(String cls, String meth)
		  throws ClassNotFoundException, NoSuchMethodException {
			return Class.forName(cls).getMethod(meth, new Class[] { Integer.TYPE });
		}
		static Method get_sigaction_method(String cls, String meth)
		  throws ClassNotFoundException, NoSuchMethodException {
			return Class.forName(cls).getMethod(meth,
			  new Class[] { Integer.TYPE, Class.forName("jtux.UProcess$siginfo_t"), Long.TYPE });
		}
		public void set_sa_handler(int a) {
			sa_handler = sa_sigaction = null;
			sa_actiontype = a;
		}
		public void set_sa_handler(String cls, String meth)
		  throws ClassNotFoundException, NoSuchMethodException {
			if ((sa_handler = get_handler_method(cls, meth)) == null)
				throw new NoSuchMethodException("set_sa_handler");
			sa_sigaction = null;
			sa_actiontype = UConstant.SIG_ERR;
		}
		public void set_sa_sigaction(String cls, String meth)
		  throws ClassNotFoundException, NoSuchMethodException {
			sa_handler = null;
			if ((sa_sigaction = get_sigaction_method(cls, meth)) == null)
				throw new NoSuchMethodException("set_sa_sigaction");
			sa_actiontype = UConstant.SIG_ERR;
		}
	}

	/**
		Java version of C struct sigevent.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_sigevent">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_sigevent {
		public int sigev_notify;		// notification type
		public int sigev_signo;			// signal number
		public u_sigval sigev_value;	// signal value
		// Members sigev_notify_function and sigev_notify_attributes
		// not supported because threads aren't.
	}

	/**
		Java version of C type (structure) siginfo_t.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#siginfo_t">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class siginfo_t {
		public int si_signo;			// signal number
		public int si_errno;			// errno value associated with signal
		public int si_code;				// signal code (see below)
		public long si_pid;				// sending process ID
		public long si_uid;				// real user ID of sending process
		public long si_addr;			// address of faulting instruction
		public int si_status;			// exit value or signal
		public long si_band;			// band event for SIGPOLL
		public u_sigval si_value;		// signal value
	}

	/**
		Java version of C type stack_t. (Not used.)
	*/
	static public class stack_t {
	}

	/**
		Java version of C union sigval.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#union_sigval">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class u_sigval {
	}

	static public class u_sigval_int extends u_sigval {
		public int sival_int;
	}

	static public class u_sigval_ptr extends u_sigval {
		public long sival_ptr;
	}

	/**
		Java version of C struct timeval.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_timeval">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_timeval { // probably goes with time stuff
		public long tv_sec;				// seconds
		public long tv_usec;				// microseconds
	}

	/**
		Java version of C struct timespec.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_timespec">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_timespec { // probably goes with time stuff
		public long tv_sec;				// seconds
		public long tv_nsec;				// nanoseconds
	}

	/**
		Java version of C struct rlimit.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_rlimit">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_rlimit {
		public long rlim_cur;				// current (soft) limit
		public long rlim_max;				// maximum (hard) limit
	}

	/**
		Java version of C struct rusage.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_rusage">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_rusage {
		public s_timeval ru_utime;			// user time used
		public s_timeval ru_stime;			// system time used

		public s_rusage() {
			ru_utime = new s_timeval();
			ru_stime = new s_timeval();
		}
	}

	static Method[] signalHandlers = new Method[50];

	static void CatchSignal(int signum) {
		//System.out.println("caught signal " + signum);
		if (signum >= 0 && signum < signalHandlers.length) {
			try {
				signalHandlers[signum].invoke(null, new Object[] { new Integer(signum) });
			}
			catch (Exception e) {
				System.err.println("*** Jtux: can't invoke sa_handler method: " + e);
				System.exit(1);
			}
		}
	}

	static void CatchSignal(int signum, siginfo_t info, long context) {
		System.out.println("caught signal " + signum);
		if (signum >= 0 && signum < signalHandlers.length) {
			try {
				signalHandlers[signum].invoke(null,
				  new Object[] { new Integer(signum), info, new Long(context) });
			}
			catch (Exception e) {
				System.err.println("*** Jtux: can't invoke sa_sigaction method: " + e);
				System.exit(1);
			}
		}
	}

	/**
		Calls sigaction.
		<p>
		Problem with receiving a signal value (e.g., from mq_notify or sigqueue) is
		that Jtux doesn't know whether it's an int or a ptr, so it can't construct the
		appropriate subclass for the Java class u_sigval. On the theory that pointers
		aren't that useful with Java and that, even when they're used with Jtux, they're
		passed as longs, the subclass passed in the siginfo_t structure is always
		a u_signal_int.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static void sigaction(int signum, s_sigaction action, s_sigaction oaction)
	  throws UErrorException {
		if (signum >= 0 && signum < signalHandlers.length)
			if (action.sa_handler != null)
				signalHandlers[signum] = action.sa_handler;
			else if (action.sa_sigaction != null)
				signalHandlers[signum] = action.sa_sigaction;
		sigaction_x(signum, action, oaction);
	}

	/**
		Calls abort.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#abort">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void abort();
	/**
		Calls chdir.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#chdir">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void chdir(String path) throws UErrorException;
	/**
		Calls chroot.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#chroot">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void chroot(String path) throws UErrorException;
	/**
		Calls clock.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#clock">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long clock() throws UErrorException;
	/**
		Calls execvp. This is the only one of the exec family implemented for Java.
		The list forms are unimplementatble, although the vector forms are nearly
		as easy to use:
		<code>
		UProcess.execvp("/usr/bin/echo", new String[] {"echo", "arg1", "arg2"});
		</code>
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#execvp">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void execvp(String file, String[] args) throws UErrorException;
	/**
		Calls exit.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#exit">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void exit(int status);
	/**
		Calls _exit.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#_exit">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void _exit(int status);
	/**
		Calls fchdir.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#fchdir">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void fchdir(int fd) throws UErrorException;
	/**
		Calls fork.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#fork">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long fork() throws UErrorException;
	/**
		Calls getcwd.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getcwd">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void getcwd(StringBuffer path) throws UErrorException;
	/**
		Calls getegid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getegid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long getegid();
	/**
		Calls getenv.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getenv">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static String getenv(String var);
	/**
		Calls geteuid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#geteuid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long geteuid();
	/**
		Calls getgid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getgid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long getgid();
	/**
		Calls getpgid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getpgid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long getpgid(long pid) throws UErrorException;
	/**
		Calls getpid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getpid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long getpid();
	/**
		Calls getppid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getppid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long getppid();
	/**
		Calls getrlimit.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getrlimit">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void getrlimit(int resource, s_rlimit rl) throws UErrorException;
	/**
		Calls getrusage.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getrusage">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void getrusage(int who, s_rusage r_usage);
	/**
		Calls getsid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getsid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long getsid(long pid) throws UErrorException;
	/**
		Calls getuid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getuid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long getuid();
	/**
		Calls kill.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#kill">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void kill(long pid, long signum);
	/**
		Calls nice.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#nice">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void nice(int incr);
	/**
		Calls pause.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#pause">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void pause() throws UErrorException;
	/**
		Calls pthread_sigmask.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#pthread_sigmask">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void pthread_sigmask(int how, sigset_t set, sigset_t oset);
	/**
		Calls putenv.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#putenv">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void putenv(String string) throws UErrorException;
	/**
		Calls setenv.
		May not be on all systems. Throws ENOSYS if not.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#setenv">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void setenv(String var, String val, boolean overwrite) throws UErrorException;
	/**
		Calls setegid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#setegid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void setegid(long gid) throws UErrorException;
	/**
		Calls seteuid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#seteuid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void seteuid(long uid) throws UErrorException;
	/**
		Calls setgid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#setgid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void setgid(long gid) throws UErrorException;
	/**
		Calls setpgid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#setpgid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void setpgid(long pid, long pgid) throws UErrorException;
	/**
		Calls setrlimit.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#setrlimit">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void setrlimit(int resource, s_rlimit rl) throws UErrorException;
	/**
		Calls setsid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#setsid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long setsid() throws UErrorException;
	/**
		Calls setuid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#setuid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void setuid(long uid) throws UErrorException;
	/**
		Function to get the (dynamic) value of SIGRTMIN.
		<p>
		sig_get_SIGRTMIN and sig_get_SIGRTMAX are not fields of UConstant because
		SIGRTMIN and SIGRTMAX are not necessarily integer constants.
	*/
	public native static int sig_get_SIGRTMIN() throws UErrorException;
	/**
		Function to get the (dynamic) value of SIGRTMAX.
		<p>
		sig_get_SIGRTMIN and sig_get_SIGRTMAX are not fields of UConstant because
		SIGRTMIN and SIGRTMAX are not necessarily integer constants.
	*/
	public native static int sig_get_SIGRTMAX() throws UErrorException;
	/**
		Used by the Java-level function sigaction.
	*/
	public native static void sigaction_x(int signum, s_sigaction action, s_sigaction oaction)
	  throws UErrorException;
	/**
		Calls sigaddset.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigaddset">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigaddset(sigset_t set, int signum) throws UErrorException;
	/**
		Calls sigaltstack.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigaltstack">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigaltstack(stack_t stack, stack_t ostack) throws UErrorException;
	/**
		Calls sigdelset.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigdelset">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigdelset(sigset_t set, int signum) throws UErrorException;
	/**
		Calls sigemptyset.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigemptyset">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigemptyset(sigset_t set) throws UErrorException;
	/**
		Calls sigfillset.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigfillset">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigfillset(sigset_t set) throws UErrorException;
	/**
		Calls siginterrupt.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#siginterrupt">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void siginterrupt(int signum, int on) throws UErrorException;
	/**
		Calls sigismember.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigismember">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static boolean sigismember(sigset_t set, int signum) throws UErrorException;
	/**
		Calls sigpending.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigpending">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigpending(sigset_t set) throws UErrorException;
	/**
		Calls sigprocmask.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigprocmask">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigprocmask(int how, sigset_t set, sigset_t oset) throws UErrorException;
	/**
		Calls sigqueue.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigqueue">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigqueue(long pid, int signum, u_sigval value) throws UErrorException;
	/**
		Calls sigsuspend.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigsuspend">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigsuspend(sigset_t set) throws UErrorException;
	/**
		Calls sigtimedwait.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigtimedwait">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigtimedwait(sigset_t set, siginfo_t info, s_timespec ts) throws UErrorException;
	/**
		Calls sigwait.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigwait">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigwait(sigset_t set, UUtil.IntHolder signum) throws UErrorException;
	/**
		Calls sigwaitinfo.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sigwaitinfo">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void sigwaitinfo(sigset_t set, siginfo_t info) throws UErrorException;
	/**
		Calls system.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#system">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int system(String command) throws UErrorException;
	/**
		Calls times.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#times">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long times(s_tms buffer) throws UErrorException;
	/**
		Calls umask.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#umask">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int umask(int cmask);
	/**
		Calls unsetenv.
		<p>
		May not be on all systems. Throws ENOSYS if not.
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#unsetenv">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void unsetenv(String var) throws UErrorException;
	/**
		Calls wait.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#wait">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static long wait(UExitStatus status) throws UErrorException {
		return waitpid(-1, status, 0);
	}
	/**
		Calls waitpid.
		<p>
		On Linux, waiting for an exited process doesn't work.
		If System.exit (the Java method) is called, waitpid stays blocked, as though the process doesn't really exit. If UProcess.exit is called, something inside the system waits, and waitpid returns an error because the process doesn't exist. This is probably due to the way threads are implemented. On Solaris, all works as it should.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#waitpid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long waitpid(long pid, UExitStatus status, int options)
	  throws UErrorException;
}
