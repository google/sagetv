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

public class PlaybackException extends java.lang.Exception
{
  public static final String[] ERR_CODE_REZ_NAMES = { "",
    "MEDIAPLAYER_ERROR_DIRECTX_INSTALL", "MEDIAPLAYER_ERROR_SAGETV_INSTALL",
    "MEDIAPLAYER_ERROR_FILESYSTEM", "MEDIAPLAYER_ERROR_VIDEO_RENDER",
    "MEDIAPLAYER_ERROR_AUDIO_RENDER", "MEDIAPLAYER_ERROR_DVD_GENERAL",
    "MEDIAPLAYER_ERROR_DVD_REGION", "MEDIAPLAYER_ERROR_DVD_COPYPROTECT",
    "MEDIAPLAYER_WARNING_DVD_INVALIDOP", "MEDIAPLAYER_ERROR_SEEK",
  };
  public static final int DIRECTX_INSTALL = -1;
  public static final int SAGETV_INSTALL = -2;
  public static final int FILESYSTEM = -3;
  public static final int VIDEO_RENDER = -4;
  public static final int AUDIO_RENDER = -5;
  public static final int DVD_GENERAL = -6;
  public static final int DVD_REGION = -7;
  public static final int DVD_COPYPROTECT = -8;
  public static final int DVD_INVALIDOP = -9;
  public static final int SEEK = -10;

  public PlaybackException() {
  }


  // Don't support this technique becaues its not localizable
  /*    public PlaybackException(String msg) {
        super(msg);
    }*/
  public PlaybackException(int myErrCode, int nativeErrCode)
  {
    super(getFullErrorMessage(myErrCode, nativeErrCode));
    if (Sage.DBG) System.out.println("Built:" + this.toString() + " errCode=" + myErrCode + " nativeErrCode=" + nativeErrCode);
  }
  private static String getFullErrorMessage(int myErrCode, int nativeErrCode)
  {
    return Sage.rez("ERROR") + " (" + myErrCode + ",0x" + Integer.toHexString(nativeErrCode) + "): " +
        Sage.rez(ERR_CODE_REZ_NAMES[Math.min(ERR_CODE_REZ_NAMES.length - 1, Math.abs(myErrCode))]);
  }
}
