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

public class UUtil {
	/*
		Not sure yet where this should be.
	*/
	static {
		System.loadLibrary("jtux");
		System.out.println("Loaded");
	}

	public static class IntHolder {
		public int value;
	}

	native static void check_type_sizes() throws UErrorException;
	/**
		Calls strerror.
		<p>
		<font size="-1"><b><i>Click {@link <a href="doc-files/synopses.html#strerror">here</a>} for Posix/SUS C API.</i></b></font>
	*/
	native static String strerror(int errnum);
	public native static long GetSymbol(String category, String symbol);
	public native static String GetSymbolStr(String category, int code);
	/**
		Convenience functions (non-standard) to move byte array to and from shared
		memory.
	*/
	public native static void jaddr_to_seg(long addr, byte[] data, int datasize);
	public native static void jaddr_from_seg(long addr, byte[] data, int datasize);

	static {
		try {
			check_type_sizes();
		}
		catch (UErrorException e) {
			System.err.println("Jtux error: Java native types don't match POSIX/SUS types.");
			System.exit(1);
		}
	}

	static void StringBufferSet(StringBuffer sb, String s) {
		sb.delete(0, sb.length());
		sb.insert(0, s);
	}

	/**
		Following here as a convenience so the JNI code doesn't have to chase
		down the method in StringBuffer itself.
	*/
	static String StringBufferGet(StringBuffer sb) {
		return sb.toString();
	}
}
