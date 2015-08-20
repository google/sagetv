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

public class USysVIPC {
	/*
		Not sure yet where this should be.
	*/
	static {
		System.loadLibrary("jtux");
		System.out.println("Loaded OK.");
	}

	/**
		Java version of C struct ipc_perm.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_ipc_perm">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_ipc_perm {
		public long uid;		// owner user-ID
		public long gid;		// owner group-ID
		public long cuid;		// creator user-ID
		public long cgid;		// creator group-ID
		public int mode;		// permission bits
	}

	/**
		Java version of C struct msqid_ds.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_msqid_ds">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_msqid_ds {
		public s_ipc_perm msg_perm;	// permission structure
		public int msg_qnum;			// number of messages currently on queue
		public int msg_qbytes;			// maximum number of bytes allowed on queue
		public long msg_lspid;			// process ID of last msgsnd
		public long msg_lrpid;			// process ID of last msgrcv
		public long msg_stime;			// time of last msgsnd
		public long msg_rtime;			// time of last msgrcv
		public long msg_ctime;			// time of last msgctl change

		public s_msqid_ds() {
			msg_perm = new s_ipc_perm();
		}
	}

	/**
		Java version of C struct semid_ds.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_semid_ds">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_semid_ds {
		public s_ipc_perm sem_perm;		// permission structure
		public short sem_nsems;			// size of set
		public long sem_otime;			// time of last semop
		public long sem_ctime;			// time of last semctl

		public s_semid_ds() {
			sem_perm = new s_ipc_perm();
		}
	}

	/**
		Java version of C struct shmid_ds.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_shmid_ds">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_shmid_ds {
		public s_ipc_perm shm_perm;		// permission structure
		public int shm_segsz;			// size of segment in bytes
		public long shm_lpid;			// process ID of last shared memory operation
		public long shm_cpid;			// process ID of creator
		public int shm_nattch;			// number of current attaches
		public long shm_atime;			// time of last shmat
		public long shm_dtime;			// time of last shmdt
		public long shm_ctime;			// time of last change by shmctl

		public s_shmid_ds() {
			shm_perm = new s_ipc_perm();
		}
	}

	/**
		Java version of C union semun. Use subclasses for different types.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#union_semun">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class u_semun {
	}

	static public class u_semun_int extends u_semun {
		public int val;
	}

	static public class u_semun_struct extends u_semun {
		public s_semid_ds buf;
	}

	static public class u_semun_array extends u_semun {
		public short[] array;
	}

	/**
		Java version of C struct sembuf.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#struct_sembuf">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	static public class s_sembuf {
		public short sem_num;			// semaphore number
		public short sem_op;			// Semaphore operation
		public short sem_flg;			// Operation flags
	}

	/**
		Calls ftok.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#ftok">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long ftok(String path, int id) throws UErrorException;
	/**
		Set message type.
		<p>
		Convenience function (non-standard) to set the first few bytes of a message to
		the message type. Number of bytes used for the type (typically 4) is retunred.
		Data should start at the next byte.
	*/
	public native static int msg_set_type(long msgtype, byte[] msgp) throws UErrorException;
	/**
		Calls msgctl.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#msgctl">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void msgctl(int msqid, int cmd, s_msqid_ds data) throws UErrorException;
	/**
		Calls msgget.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#msgget">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int msgget(long key, int flags) throws UErrorException;
	/**
		Calls msgrcv.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#msgrcv">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int msgrcv(int msqid, byte[] msgp, int mtextsize, long msgtype, int flags) throws UErrorException;
	/**
		Calls msgsnd.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#msgsnd">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void msgsnd(int msqid, byte[] msgp, int msgsize, int flags) throws UErrorException;
	/**
		Calls semctl.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#semctl">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int semctl(int semid, int semnum, int cmd, u_semun arg) throws UErrorException;
	/**
		Calls semget.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#semget">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int semget(long key, int nsems, int flags) throws UErrorException;
	/**
		Calls semop.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#semop">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void semop(int semid, s_sembuf[] sops, int nsops) throws UErrorException;
	/**
		Calls shmat.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#shmat">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static long shmat(int shmid, long shmaddr, int flags) throws UErrorException;
	/**
		Calls shmctl.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#shmctl">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void shmctl(int shmid, int cmd, s_shmid_ds data) throws UErrorException;
	/**
		Calls shmdt.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#shmdt">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static void shmdt(long shmaddr) throws UErrorException;
	/**
		Calls shmget.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#shmget">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	public native static int shmget(long key, int size, int flags) throws UErrorException;
}
