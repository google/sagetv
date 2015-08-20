/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sage;

public class EncodingException extends java.lang.Exception
{
  public static final String[] ERR_CODE_REZ_NAMES = { "",
    "CAPTURE_ERROR_DIRECTX_INSTALL", "CAPTURE_ERROR_SAGETV_INSTALL",
    "CAPTURE_ERROR_FILESYSTEM", "CAPTURE_ERROR_CAPTURE_INSTALL",
    "CAPTURE_ERROR_SW_VIDEO_COMPRESSION", "CAPTURE_ERROR_HW_VIDEO_COMPRESSION",
    "CAPTURE_ERROR_SW_AUDIO_COMPRESSION",

  };
  public static final int DIRECTX_INSTALL = -1;
  public static final int SAGETV_INSTALL = -2;
  public static final int FILESYSTEM = -3;
  public static final int CAPTURE_DEVICE_INSTALL = -4;
  public static final int SW_VIDEO_COMPRESSION = -5;
  public static final int HW_VIDEO_COMPRESSION = -6;
  public static final int SW_AUDIO_COMPRESSION = -7;

  public EncodingException()
  {
  }

  // Don't support this technique becaues its not localizable
  /*	public EncodingException(String msg)
	{
		super(msg);
	}*/
  public EncodingException(int myErrCode, int nativeErrCode)
  {
    super(Sage.rez("ERROR") + " (" + myErrCode + ",0x" + Integer.toHexString(nativeErrCode) + "): " +
        Sage.rez(ERR_CODE_REZ_NAMES[Math.min(ERR_CODE_REZ_NAMES.length - 1, Math.abs(myErrCode))]));
    if (Sage.DBG) System.out.println("Built:" + this.toString());
  }
}
