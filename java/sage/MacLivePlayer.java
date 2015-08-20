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

public class MacLivePlayer implements MediaPlayer
{
  public MacLivePlayer()
  {
  }

  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    //		currHintMajorType = majorTypeHint;
    //		currHintMinorType = minorTypeHint;
    //		currHintEncoding = encodingHint;

    cdi = MMC.getInstance().getCaptureDeviceInputNamed(encodingHint);
    if (cdi == null)
      throw new PlaybackException();

    // we need to send:
    // - the capture device name and number
    // - the macstv port to use for rendering video
    // audio is rendered by the server, volume is controlled via NSNotifications

    CaptureDevice capDev = cdi.getCaptureDevice();

    try {
      pHandle = setupLivePlayback0();
      setLiveSource0(pHandle, capDev.getCaptureDeviceName(), capDev.getCaptureDeviceNum());
    } catch (PlaybackException e) {
      System.out.println("Exception thrown while starting live playback: "+e);
      throw e;
    } catch (Throwable t) {
      System.out.println("Exception thrown while starting live playback: "+t);
    }

    currFile = file;
    currState = LOADED_STATE;
    uiMgr = VideoFrame.getVideoFrameForPlayer(this).getUIMgr();
    if(uiMgr != null)
      setVolume(uiMgr.getFloat("mplayer/last_volume", 1.0f)); // use the same volume level as mplayer
  }

  public boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file)
  {
    return false;
  }

  public void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint,
      java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException
      {
      }

  public void free()
  {
    if (uiMgr != null)
      uiMgr.putFloat("mplayer/last_volume", lastVolume);
    stopLivePlayback0(pHandle); // free up the native stuff (if anything)
  }

  public boolean play()
  {
    if (currState == LOADED_STATE)
    {
      return true;
    }
    return false;
  }

  public boolean pause()
  {
    return false;
  }

  public void stop()
  {
    stopLivePlayback0(pHandle);
  }

  public void inactiveFile()
  {
  }

  public int getState()
  {
    return currState;
  }

  public boolean frameStep(int amount)
  {
    return false;
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

  public long seek(long seekTimeMillis) throws PlaybackException
  {
    throw new PlaybackException(PlaybackException.SEEK, 0);
  }

  public float setPlaybackRate(float newRate)
  {
    return 1.0f;
  }

  public float getVolume()
  {
    return lastVolume;
  }

  public float setVolume(float f)
  {
    synchronized (this)
    {
      setAudioVolume0(pHandle, lastVolume = f);
    }
    return lastVolume;
  }

  public void setMute(boolean x)
  {
    synchronized (this)
    {
      setAudioMute0(pHandle, audioPreviewMuted = x);
    }
  }

  public boolean getMute()
  {
    return audioPreviewMuted;
  }

  public java.awt.Color getColorKey()
  {
    return null;
  }

  public void setVideoRectangles(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    // TODO: tell the video renderer where to render...
    // already handled by the macstv code
  }

  public java.awt.Dimension getVideoDimensions()
  {
    if(currState == NO_STATE) return null;
    // TODO: fetch the dimensions from the video renderer
    // macstv handles this too
    return null;
  }

  public boolean setClosedCaptioningState(int ccState)
  {
    return false;
  }

  public int getClosedCaptioningState()
  {
    return CC_DISABLED;
  }

  public int getTransparency()
  {
    return TRANSLUCENT; // BITMASK, TRANSLUCENT, OPAQUE
  }


  // we use distributed notifications to communicate settings to the server to make things easy
  // live preview doesn't work on network or placeshifter connections, so this is fine :)
  protected native long setupLivePlayback0() throws PlaybackException;
  protected native void stopLivePlayback0(long ptr);

  protected native void setLiveSource0(long ptr, String deviceName, int deviceNum) throws PlaybackException;
  protected native void setAudioMute0(long ptr, boolean state);
  protected native void setAudioVolume0(long ptr, float volume);
  protected native void setVideoParams0(long ptr, String paramString);

  protected long pHandle;
  protected CaptureDeviceInput cdi;
  protected boolean audioPreviewMuted = false;
  protected float lastVolume = 0;
  protected java.io.File currFile = null;
  protected int currState = NO_STATE;
  protected UIManager uiMgr = null;
}
