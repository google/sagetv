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

public class DShowSharedLiveMediaPlayer implements MediaPlayer
{
  static
  {
    if (Sage.WINDOWS_OS)
    {
      System.loadLibrary("DShowPlayer");
    }
  }

  public DShowSharedLiveMediaPlayer(String captureDeviceInputName)
  {
    cdi = MMC.getInstance().getCaptureDeviceInputNamed(captureDeviceInputName);
    currState = NO_STATE;
  }

  public boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file)
  {
    return false;
  }

  public void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint,
      java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException
      {
    throw new PlaybackException();
      }

  private boolean isSameFile()
  {
    String currRecFile = cdi.getCaptureDevice().getRecordFilename();
    return currRecFile != null && new java.io.File(currRecFile).equals(currFile);
  }

  public boolean frameStep(int amount)
  {
    return false;
  }

  public synchronized void free()
  {
    // Get the video handle and clear the HWND settings for it
    setVideoHWND0(cdi.getCaptureDevice().getNativeVideoPreviewConfigHandle(), 0);
    killAudioPreview();
    currState = NO_STATE;
  }

  public long getDurationMillis()
  {
    return 0;
  }

  public java.io.File getFile()
  {
    return currFile;
  }

  public long getMediaTimeMillis()
  {
    return Sage.time();
  }

  public int getPlaybackCaps()
  {
    return LIVE_CAP;
  }

  public float getPlaybackRate()
  {
    return 1.0f;
  }

  public int getState()
  {
    return PLAY_STATE;
  }

  public float getVolume()
  {
    return 0;
  }

  public void inactiveFile()
  {
    DShowLivePlayer.setLiveMute0(-1, true);
  }

  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    if (cdi == null)
      throw new PlaybackException();
    CaptureDevice capDev = cdi.getCaptureDevice();
    setVideoHWND0(capDev.getNativeVideoPreviewConfigHandle(), VideoFrame.getVideoFrameForPlayer(this).getVideoHandle());
    videoDimensions = null;
    getVideoDimensions();
    currFile = file;
    currState = LOADED_STATE;
    audioPreviewPtr = openAudioPreview0(capDev.getName());
  }

  public boolean pause()
  {
    return false;
  }

  public boolean play()
  {
    return true;
  }

  public long seek(long seekTimeMillis) throws PlaybackException
  {
    return Sage.time();
  }

  public synchronized void setMute(boolean x)
  {
    if (audioPreviewPtr != 0)
      setAudioPreview0(audioPreviewPtr, audioPreviewMuted = x);
    else
      DShowLivePlayer.setLiveMute0(-1, x);
  }

  public boolean getMute()
  {
    return (audioPreviewPtr != 0) ? audioPreviewMuted : DShowLivePlayer.getLiveMute0(-1);
  }

  public float setPlaybackRate(float newRate)
  {
    return 1.0f;
  }

  public float setVolume(float f)
  {
    return 0;
  }

  protected void killAudioPreview()
  {
    if (audioPreviewPtr != 0)
    {
      setAudioPreview0(audioPreviewPtr, true);
      closeAudioPreview0(audioPreviewPtr);
      audioPreviewPtr = 0;
    }
  }

  public synchronized void stop()
  {
    DShowLivePlayer.setLiveMute0(-1, true);
    killAudioPreview();
  }

  public java.awt.Color getColorKey()
  {
    return null;
  }

  public int getTransparency() { return OPAQUE; }

  public java.awt.Dimension getVideoDimensions()
  {
    if (videoDimensions != null)
      return videoDimensions;
    synchronized (this)
    {
      videoDimensions = getVideoDimensions0(cdi.getCaptureDevice().getNativeVideoPreviewConfigHandle());
      if (Sage.DBG) System.out.println("Got Native Video Dimensions " + videoDimensions);
      return videoDimensions;
    }
  }

  public void setVideoRectangles(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    if (currState != NO_STATE && isSameFile())
    {
      synchronized (this)
      {
        resizeVideo0(cdi.getCaptureDevice().getNativeVideoPreviewConfigHandle(), videoSrcRect, videoDestRect, hideCursor);
      }
    }
  }

  public boolean setClosedCaptioningState(int ccState)
  {
    return false;
  }

  public int getClosedCaptioningState()
  {
    return CC_DISABLED;
  }

  protected native void setVideoHWND0(long ptr, long hwnd);
  protected native java.awt.Dimension getVideoDimensions0(long ptr);
  protected native void resizeVideo0(long ptr, java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor);
  protected static native long openAudioPreview0(String deviceName);
  protected static native void setAudioPreview0(long ptr, boolean state);
  protected static native void closeAudioPreview0(long ptr);

  protected int currState;
  protected java.io.File currFile;

  protected java.awt.Dimension videoDimensions;

  protected CaptureDeviceInput cdi;
  protected long audioPreviewPtr;
  protected boolean audioPreviewMuted = false;
}
