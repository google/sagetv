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

public class UErrorException extends java.lang.Exception {
	private int code;
	private int type;

	public static final int EC_ERRNO = 0;
	public static final int EC_EAI = 1;
	public static final int EC_GETDATE = 2;

	public UErrorException() {
	}
	public UErrorException(int code) {
		this.code = code;
		this.type = EC_ERRNO;
	}
	public UErrorException(int code, int type) {
		this.code = code;
		this.type = type;
	}

	public String toString() {
		String s, desc;
		try {
			switch (type) {
			case EC_ERRNO:
				s = UUtil.GetSymbolStr("errno", code);
				desc = UUtil.strerror(code);
				break;
			case EC_EAI:
				s = UUtil.GetSymbolStr("eai", code);
				desc = UNetwork.gai_strerror(code);
				break;
			case EC_GETDATE:
				s = UUtil.GetSymbolStr("getdate_err", code);
				desc = "getdate error";
				break;
			default:
				s = "[Unk. Type]";
				desc = "?";
			}
		}
		catch (Exception e) {
			s = "???";
			desc = "?";
		}
		return desc + " (" + s + ")";
	}
	public int getCode() {
		return code;
	}
	public int getType() {
		return type;
	}
}
