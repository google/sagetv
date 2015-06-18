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

public class DShowMusicPlayer extends DShowMediaPlayer
{
  public DShowMusicPlayer()
  {
    super();
  }

  public java.awt.Dimension getVideoDimensions()
  {
    return null;
  }

  protected void setFilters() throws PlaybackException
  {
    String audDec = Sage.get(prefs + AUDIO_RENDER_FILTER, "Default");
    if (audDec.length() > 0 && !"Default".equals(audDec) && !Sage.rez("Default").equals(audDec))
      setAudioRendererFilter0(pHandle, audDec, null);

    audDec = "";
    MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
    String audType = mf != null ? mf.getPrimaryAudioFormat() : "";
    // Check for a format specific audio decoder
    if (audType.length() > 0)
      audDec = Sage.get(prefs + audType.toLowerCase() + "_" + AUDIO_DECODER_FILTER, "");
    if (audDec.length() > 0 && !"Default".equals(audDec) && !Sage.rez("Default").equals(audDec))
      setAudioDecoderFilter0(pHandle, audDec, null);
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
    currTimeshifted = timeshifted;
    setupGraph0(pHandle, file != null ? file.getPath() : null, hostname, false, true);
    colorKey = null;
    currCCState = -1;
    videoDimensions = null;
    addMusicVisualization0(pHandle);
    currFile = file;
    currState = LOADED_STATE;
    setNotificationWindow0(pHandle, Sage.mainHwnd);
    UIManager uiMgr = VideoFrame.getVideoFrameForPlayer(this).getUIMgr();
    lastVolume = (uiMgr.getBoolean("media_player_uses_system_volume", Sage.WINDOWS_OS && !Sage.VISTA_OS)) ? 1.0f : uiMgr.getFloat("videoframe/last_dshow_volume", 1.0f);
  }

  public boolean pause()
  {
    if (currState == LOADED_STATE)
    {
      // Graph's about to start, get the animations going
      setupVisAnimTimer();
    }
    return super.pause();
  }
  public boolean play()
  {
    if (currState == LOADED_STATE)
    {
      // Graph's about to start, get the animations going
      setupVisAnimTimer();
    }
    return super.play();
  }
  public void stop()
  {
    if (visAnimTimer != null)
    {
      visAnimTimer.cancel();
      visAnimTimer = null;
    }
    super.stop();
  }

  private void setupVisAnimTimer()
  {
    visAnimTimer = new java.util.TimerTask()
    {
      public void run()
      {
        if (!Sage.getBoolean("videoframe/disable_visualizations", false) &&
            (currState == PLAY_STATE || currState == PAUSE_STATE))
        {
          synchronized (DShowMusicPlayer.this)
          {
            renderVisualization0(pHandle, vf.getVideoComp(), vf.getBaseMediaTimeMillis(true)*10000);
          }
        }
      }
      VideoFrame vf = VideoFrame.getVideoFrameForPlayer(DShowMusicPlayer.this);
    };
    VideoFrame.getVideoFrameForPlayer(this).getUIMgr().addTimerTask(visAnimTimer, 0, 16);
  }

  public int getTransparency()
  {
    return OPAQUE;
  }

  private native void addMusicVisualization0(long ptr);
  private native void renderVisualization0(long ptr, java.awt.Canvas canvas, long mediaTime);
  // createGraph0 will create the peer native object and create the initial filter graph
  protected native long createGraph0() throws PlaybackException;
  private java.util.TimerTask visAnimTimer;
}
