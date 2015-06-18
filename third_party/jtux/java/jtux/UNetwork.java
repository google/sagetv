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
	Class for network system calls.
	<p>
	Not implemented:
		<pre>
		inet_addr, inet_ntoa: Replaced by inet_ntop and inet_pton.
		{set/get/end}{host/net/proto/serv}ent - To Do.
		gethostbyname, gethostbyaddr - replaced by getaddrinfo/getnameinfo.
		getnetbyname, getnetbyaddr - To Do
		getprotobyname, getprotobynumber- To Do
		getservbyname, getservbyport - To Do
		if_* - To Do
		getsockname, getpeername - To Do
		socketpair - To Do
		shutdown - To Do
		</pre>
*/
public class UNetwork {

	static {
		System.loadLibrary("jtux");
	}

	/**
		Java version of C struct sockaddr.
		<p>
		It would be natural to use the sa_family field in the superclass instead
		of the individually-named fields in each subclass, but the C structures
		do have the individually-named fields. In C, of course, this is no problem,
		and either field could be used, with an appropriate cast. In Java, the
		field sa_family in s_sockaddr is never used, so given an object, the field
		can't be used as a discriminant. Instead, if you don't know the address
		family, use the getClass method to see what it is, or use the Jtux virtual
		method get_family().
	*/
	public static class s_sockaddr {
						/** not used */
		public final int sa_family = UConstant.AF_UNSPEC;

		/**
			Jtux only; non-standard
		*/
		public int get_family() { //
			return sa_family;
		}
	}

	/**
		Java version of C struct sockaddr_un.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct sockaddr_un">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static class s_sockaddr_un extends s_sockaddr {
						/** AF_UNIX */
		public int sun_family;
						/** socket pathname */
		public String sun_path = "";

		public int get_family() {
			return sun_family;
		}

		public String toString() {
			return sun_path;
		}
	}

	/**
		Java version of C struct sockaddr_in.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_sockaddr_in">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static class s_sockaddr_in extends s_sockaddr {
						/** AF_INET */
		public int sin_family;
						/** port number (uint16_t) */
		public short sin_port;
						/** IPv4 address */
		public s_in_addr sin_addr;

		public int get_family() {
			return sin_family;
		}

		public String toString() {
			try {
				return UNetwork.inet_ntop(UConstant.AF_INET, sin_addr.s_addr) +
				  ":" + UNetwork.ntohs(sin_port);
			}
			catch (UErrorException e) {
				return "Can't get addr string (" + e + ")";
			}
		}
	}

	/**
		Java version of C struct in_addr.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_in_addr">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static class s_in_addr {
						/** IPv4 address (uint32_t) */
		public int s_addr;
	}

	/**
		Java version of C struct sockaddr_in6.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_sockaddr_in6">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static class s_sockaddr_in6 extends s_sockaddr {
						/** AF_INET6 */
		public int sin6_family;
						/** port number (uint16_t) */
		public short sin6_port;
						/** traffic class and flow information */
		public int sin6_flowinfo;
						/** IPv4 address */
		public s_in6_addr sin6_addr;
						/** set of interfaces for a scope */
		public int sin6_scope_id;

		public int get_family() {
			return sin6_family;
		}

		public String toString() {
			try {
				return UNetwork.inet_ntop(UConstant.AF_INET6, sin6_addr.s6_addr) +
				  " [" + UNetwork.ntohs(sin6_port) + "]";
			}
			catch (UErrorException e) {
				return "Can't get addr string (" + e + ")";
			}
		}
	}

	/**
		Java version of C struct in6_addr.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_in6_addr">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static class s_in6_addr {
						/** IPv6 address */
		public byte[] s6_addr = new byte[16];
	}

	/**
		Java version of C struct addrinfo.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_addrinfo">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static class s_addrinfo {
						/** input flags */
		public int ai_flags;
						/** address family */
		public int ai_family;
						/** socket type */
		public int ai_socktype;
						/** protocol */
		public int ai_protocol;
						/** length of socket address */
		public int ai_addrlen;
						/** socket address */
		public s_sockaddr ai_addr = null;
						/** canonical name of service location */
		public String ai_canonname = "";
						/** pointer to next structure in list */
		public s_addrinfo ai_next = null;
	}

	/**
		Java version of C struct linger.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_linger">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static class s_linger {
						/** on (1) or off (0). */
		public int l_onoff;
						/** linger-time in seconds */
		public int l_linger;
	}

	/**
		Following class and its subclasses handle the value argument of setsockopt,
		which in C is of type "const void *".
	*/
	public static class SockOptValue {
	}

	public static class SockOptValue_int extends SockOptValue {
		public int value;
	}

	/**
		Not necessary to use SockOptValue_boolean -- SockOptValue_int
		works just as well, as the UNIX system call doesn't know the
		difference.
	*/
	public static class SockOptValue_boolean extends SockOptValue {
		public boolean value;
	}

	public static class SockOptValue_s_linger extends SockOptValue {
		public s_linger value = new s_linger();
	}

	public static class SockOptValue_s_timeval extends SockOptValue {
		public UProcess.s_timeval value = new UProcess.s_timeval();
	}

	/**
		Java version of C struct msghd.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_msghdr">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public static class s_msghdr {
						/** optional address */
		public s_sockaddr msg_name;
						/** size of address; ignored for Java */
		public int msg_namelen = 0;
						/** scatter/gather array */
		public UFile.s_iovec[] msg_iov;
						/** number of elements in msg_iov */
		public int msg_iovlen;
						/** ancillary data */
		public byte[] msg_control = null;
						/** ancillary data buffer len */
		public int msg_controllen = 0;
						/** flags on received message */
		public int msg_flags;
	}

	/**
		Jtux-only -- non-standard.
	*/
	public static class AddrInfoListHead {
						/** pointer to first structure in list */
		public s_addrinfo ai_next = null;
	}

	/**
		Calls accept.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#accept">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int accept(int socket_fd, s_sockaddr sa, UUtil.IntHolder sa_len) throws UErrorException;
	/**
		Calls bind.
		Not necessary to set sa_len on input.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#bind">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void bind(int socket_fd, s_sockaddr sa, int sa_len) throws UErrorException;
	/**
		Calls connect.
		Not necessary to set sa_len on input.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#connect">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void connect(int socket_fd, s_sockaddr sa, int sa_len) throws UErrorException;
	/**
		Calls freeaddrinfo.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#freeaddrinfo">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void freeaddrinfo(s_addrinfo infop);
	/**
		Calls gai_strerror.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#gai_strerror">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static String gai_strerror(int code);
	/**
		Calls getaddrinfo. (Replaces gethostbyname.)
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getaddrinfo">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void getaddrinfo(String nodename, String servname,
	  s_addrinfo hint, AddrInfoListHead infop) throws UErrorException;
	/**
		Calls gethostid.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#gethostid">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long gethostid();
	/**
		Calls gethostname.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#gethostname">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void gethostname(StringBuffer name) throws UErrorException;
	/**
		Calls getnameinfo. (Replaces gethostbyaddr.) sa_len not used.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getnameinfo">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void getnameinfo(s_sockaddr sa, int sa_len,
	  StringBuffer nodename, StringBuffer servname, int flags) throws UErrorException;
	/**
		Calls getsockopt.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#getsockopt">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void getsockopt(int socket_fd, int level, int option,
	  SockOptValue value, UUtil.IntHolder value_len) throws UErrorException;
	/**
		Calls htonl.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#htonl">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int htonl(int hostnum);
	/**
		Calls htons.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#htons">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static short htons(short hostnum);
	/**
		Calls inet_ntop.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#inet_ntop">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static String inet_ntop(int domain, int src) throws UErrorException;
	/**
		Calls inet_ntop.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#inet_ntop">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static String inet_ntop(int domain, byte[] src) throws UErrorException;
	/**
		Calls inet_pton.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#inet_pton">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void inet_pton(int domain, String src, UUtil.IntHolder dst) throws UErrorException;
	/**
		Calls inet_pton.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#inet_pton">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void inet_pton(int domain, String src, byte[] dst) throws UErrorException;
	/**
		Calls listen.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#listen">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void listen(int socket_fd, int backlog) throws UErrorException;
	/**
		Calls ntohl.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#ntohl">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int ntohl(int netnum);
	/**
		Calls ntohs.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#ntohs">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static short ntohs(short netnum);
	/**
		Calls recv.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#socket_fd">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int recv(int socket_fd, byte[] buffer, int length,
	  int flags) throws UErrorException;
	/**
		Calls recvfrom.
		sa_len need not be set on input.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#recvfrom">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int recvfrom(int socket_fd, byte[] buffer, int length,
	  int flags, s_sockaddr sa, UUtil.IntHolder sa_len) throws UErrorException;
	/**
		Calls recvmsg.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#recvmsg">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int recvmsg(int socket_fd, s_msghdr message, int flags) throws UErrorException;
	/**
		Calls send.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#send">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int send(int socket_fd, byte[] data, int length,
	  int flags) throws UErrorException;
	/**
		Calls sendmsg.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sendmsg">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int sendmsg(int socket_fd, s_msghdr message, int flags) throws UErrorException;
	/**
		Calls sendto.
		Not necessary to set sa_len on input.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sendto">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int sendto(int socket_fd, byte[] message, int length,
	  int flags, s_sockaddr sa, int sa_len) throws UErrorException;
	/**
		Calls setsockopt.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#setsockopt">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void setsockopt(int socket_fd, int level, int option,
	  SockOptValue value, int value_len) throws UErrorException;
	/**
		Calls sockatmark.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#sockatmark">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int sockatmark(int socket_fd) throws UErrorException;
	/**
		Calls socket.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#socket">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int socket(int domain, int type, int protocol) throws UErrorException;
}
