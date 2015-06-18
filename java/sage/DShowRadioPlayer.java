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

public class DShowRadioPlayer extends DShowTVPlayer
{
  public DShowRadioPlayer()
  {
    super();
  }

  public java.awt.Dimension getVideoDimensions()
  {
    return null;
  }

  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    eos = false;
    pHandle = createGraph0();
    currHintMajorType = majorTypeHint;
    currHintMinorType = minorTypeHint;
    currHintEncoding = encodingHint;
    setFilters();
    setTimeshifting0(pHandle, timeshifted, bufferSize);
    String hwDecoder = Sage.get(prefs + PS_HW_DECODER_FILTER, "");
    boolean disableSWDecoding = hwDecoder.length() > 0 && Sage.getBoolean(prefs + DISABLE_SW_DECODING, false);
    setupGraph0(pHandle, file != null ? file.getPath() : null, hostname, false, !disableSWDecoding);
    colorKey = null;
    currCCState = -1;
    videoDimensions = null;
    if (hwDecoder.length() > 0)
      addHWDecoderFilter0(pHandle, hwDecoder, disableSWDecoding);

    currFile = file;
    currState = LOADED_STATE;
    setNotificationWindow0(pHandle, Sage.mainHwnd);
    UIManager uiMgr = VideoFrame.getVideoFrameForPlayer(this).getUIMgr();
    lastVolume = (uiMgr.getBoolean("media_player_uses_system_volume", Sage.WINDOWS_OS && !Sage.VISTA_OS)) ? 1.0f : uiMgr.getFloat("videoframe/last_dshow_volume", 1.0f);
  }

  protected void setFilters() throws PlaybackException
  {
    String audDec = Sage.get(prefs + AUDIO_RENDER_FILTER, "Default");
    if (audDec.length() > 0 && !"Default".equals(audDec) && !Sage.rez("Default").equals(audDec))
      setAudioRendererFilter0(pHandle, audDec, null);

    audDec = Sage.get(prefs + AUDIO_DECODER_FILTER, "SageTV MPEG Audio Decoder");
    if (audDec.length() > 0 && !"Default".equals(audDec) && !Sage.rez("Default").equals(audDec))
      setAudioDecoderFilter0(pHandle, audDec, null);
  }

}
