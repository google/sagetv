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

public class QTMediaPlayer implements MediaPlayer
{
  public static String buildMediaURL(String hostname, java.io.File file)
  {
    String url = null;

    // we'll cheat and ass-ume if the file exists, then make a file:// URL so we can bypass all the networking stuff
    if(file.exists()) {
      try {
        // convert to URI first, so illegal chars get escaped properly (why toURL() doesn't just do this is beyond me...)
        url = file.toURI().toURL().toString();
      } catch(Throwable t) {
        System.out.println("Exception building media URL:"+t);
        t.printStackTrace();
      }
    } else {
      // off-host media is handled via stv: URLs
      if(hostname != null)
        url = "stv://"+hostname+"/"+file.toString();
      else
        url = "stv://localhost/"+file.toString();
    }

    return url;
  }

  public static boolean canLoadMediaFile(MediaFile file)
  {
    if(file.getNumSegments() > 1) return false;	// don't do segmented files yet

    String hostname = file.getServerName();
    if(hostname == null)
      hostname = "localhost";

    java.io.File pf = file.getFile(0);//getParentFile();

    if(Sage.DBG) {
      if(pf != null)
        System.out.println("QTMediaPlayer: +++++++++ canLoadMediaFile("+file+")  host: "+hostname+
            "  file: "+pf.toString()+" ("+(pf.exists() ? "local" : "remote")+")");
      else
        System.out.println("QTMediaPlayer: +++++++++ canLoadMediaFile("+file+")  (remote)");
    }

    // we don't support live streams just yet
    // TODO: see if it's possible to support mp4 streams being recorded
    if(file.isRecording() || file.isAnyLiveStream()) return false;

    /*  Let canHasPlayerFor decide if QT can play it or not...
		if(sage.media.format.MediaFormat.MPEG2_TS.equals(file.getContainerFormat())
		   || sage.media.format.MediaFormat.MPEG2_PS.equals(file.getContainerFormat())
		   || sage.media.format.MediaFormat.MPEG2_VIDEO.equals(file.getPrimaryVideoFormat())
		   || sage.media.format.MediaFormat.MPEG1_VIDEO.equals(file.getPrimaryVideoFormat()))
		{
			return false;
		}
     */

    // preflight the movie to make sure QT can load it
    return canHasPlayerFor(buildMediaURL(hostname, pf), file.getContainerFormat(), file.getPrimaryVideoFormat(), file.getPrimaryAudioFormat());
  }

  public QTMediaPlayer()
  {
    currState = NO_STATE;
    qtMediaRef = 0;
  }

  public int getTransparency()
  {
    return TRANSLUCENT;
  }

  /**
   * When called the MediaPlayer should load the specified file.
   * @param majorTypeHint Indicates the type of content (major):
   * VIDEO = 1;
   * AUDIO = 2;
   * DVD = 4;
   * @param minorTypeHint Indicates the type of content (minor):
   * MPEG2_PS = 31;
   * MPEG2_TS = 32;
   * MP3 = 33;
   * MPEG1_PS = 36;
   * WAV = 38;
   * AVI = 39;
   * @param encodingHint A descriptive string for the format the content was recorded in, do not rely on this.
   * @param file The file to play
   * @param hostname null for local files, for remote files it's the IP hostname/address that the file is located on.
   * @param timeshifted true if the file is currently being recorded
   * @param bufferSize The size of the circular buffer used to record to the file being played back.
   */
  public void load(byte majorTypeHint, byte minorTypeHint, String encodingHint,
      java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
      {
    synchronized(this) {
      String url = buildMediaURL(hostname, file);

      if(Sage.DBG) System.out.println("QTMediaPlayer loading: \""+file.toString()+"\" on host "+(hostname != null ? hostname : "localhost")+" final URL:"+url);

      if(url != null)
        qtMediaRef = load0(url);

      uiMgr = VideoFrame.getVideoFrameForPlayer(this).getUIMgr();

      if(qtMediaRef != 0) {
        int loadState = -1;

        // spin until the movie has loaded enough to begin playback
        do {
          loadState = getLoadState0(qtMediaRef);
          if(loadState < 1000000) try{Thread.sleep(500);}catch(Exception e){}; // let QT chew on things a bit
        } while(loadState != -1 && loadState < 100000);

        if(loadState == -1) throw new PlaybackException();

        currFile = file;
        currState = LOADED_STATE;

        if (uiMgr != null)
          setVolume0(qtMediaRef, uiMgr.getFloat("mplayer/last_volume", 1.0f));
      }
    }
      }

  /**
   * When called the MediaPlayer should load the specified file. This will only be called if canFastLoad just returned
   * true for the same parameters. This does not need to be implemented if canFastLoad always returns false.
   * fastLoad will be called without unloading/freeing the file that was previously loaded by the media player
   * @param majorTypeHint Indicates the type of content (major):
   * VIDEO = 1;
   * AUDIO = 2;
   * DVD = 4;
   * @param minorTypeHint Indicates the type of content (minor):
   * MPEG2_PS = 31;
   * MPEG2_TS = 32;
   * MP3 = 33;
   * MPEG1_PS = 36;
   * WAV = 38;
   * AVI = 39;
   * @param encodingHint A descriptive string for the format the content was recorded in, do not rely on this.
   * @param file The file to play
   * @param hostname null for local files, for remote files it's the IP hostname/address that the file is located on.
   * @param timeshifted true if the file is currently being recorded
   * @param bufferSize The size of the circular buffer used to record to the file being played back.
   * @param waitUntilDone if true the player should wait until the current file has finished playing before loading the specified one
   */
  public void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint,
      java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException
      {

      }

  /**
   * Returns true if the MediaPlayer is capable of performing a 'fastLoad' call with the specified paramters.
   * @param majorTypeHint Indicates the type of content (major):
   * VIDEO = 1;
   * AUDIO = 2;
   * DVD = 4;
   * @param minorTypeHint Indicates the type of content (minor):
   * MPEG2_PS = 31;
   * MPEG2_TS = 32;
   * MP3 = 33;
   * MPEG1_PS = 36;
   * WAV = 38;
   * AVI = 39;
   * @param encodingHint A descriptive string for the format the content was recorded in, do not rely on this.
   * @param file The file to play
   * @return true if the MediaPlayer supports the fastLoad call for the specified parameters
   */
  public boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file)
  {
    // TODO: implement fast loading
    return false;
  }

  /**
   * When called the MediaPlayer should release all resources associated with it.
   * IMPORTANT: The MediaPlayer object may be re-used again with a call to load() after it is freed.
   */
  public void free()
  {
    synchronized(this) {
      if (qtMediaRef != 0) {
        if (uiMgr != null)
          uiMgr.putFloat("mplayer/last_volume", getVolume0(qtMediaRef));
        unload0(qtMediaRef);
      }
      qtMediaRef = 0;
      currState = NO_STATE;
    }
  }

  public void inactiveFile() {} // nothing, since we don't support live streams...

  public void stop()
  {
    synchronized(this) {
      if(qtMediaRef != 0) stop0(qtMediaRef);
      currState = STOPPED_STATE;
    }
  }

  public java.io.File getFile()
  {
    return currFile;
  }

  public int getState()
  {
    return currState;
  }

  public long getDurationMillis()
  {
    synchronized(this) {
      if(qtMediaRef != 0) return getMediaDurationMillis0(qtMediaRef);
    }
    return 0;
  }

  public long getMediaTimeMillis()
  {
    synchronized(this) {
      if(qtMediaRef != 0) return getMediaTimeMillis0(qtMediaRef);
    }
    return 0;
  }

  public float getPlaybackRate()
  {
    synchronized(this) {
      if(qtMediaRef != 0) return getPlaybackRate0(qtMediaRef);
    }
    return 0.0f;
  }

  public boolean pause()
  {
    synchronized(this) {
      if(qtMediaRef != 0) {
        currState = PAUSE_STATE;
        return pause0(qtMediaRef);
      }
    }
    return false;
  }

  public boolean play()
  {
    synchronized(this) {
      if(qtMediaRef != 0) {
        currState = PLAY_STATE;
        return play0(qtMediaRef);
      }
    }
    return false;
  }

  public long seek(long seekTimeMillis) throws PlaybackException
  {
    long result = 0;

    synchronized(this) {
      if((qtMediaRef != 0) && (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)) {
        result = seek0(qtMediaRef, seekTimeMillis);
        if(currState == PAUSE_STATE) pause0(qtMediaRef);	// QT will start playing once we seek...
      }
    }
    return 0;
  }

  public float setPlaybackRate(float newRate)
  {
    synchronized(this) {
      if(qtMediaRef != 0) return setPlaybackRate0(qtMediaRef, newRate);
    }
    return 0.0f;
  }

  public boolean frameStep(int amount)
  {
    synchronized(this) {
      if((qtMediaRef != 0) && ((currState == PLAY_STATE) || (currState == PAUSE_STATE))) {
        if(currState == PLAY_STATE) pause0(qtMediaRef);
        return step0(qtMediaRef, amount);
      }
    }
    return false;
  }

  public boolean getMute()
  {
    synchronized(this) {
      if(qtMediaRef != 0) muteState = getMute0(qtMediaRef);
    }
    return muteState;
  }

  public void setMute(boolean x)
  {
    synchronized(this) {
      muteState = x;
      if(qtMediaRef != 0) setMute0(qtMediaRef, x);
    }
  }

  public float getVolume()
  {
    synchronized(this) {
      if(qtMediaRef != 0) currVolume = getVolume0(qtMediaRef);
    }
    return currVolume;
  }

  public float setVolume(float f)
  {
    synchronized(this) {
      currVolume = f;
      if(qtMediaRef != 0) currVolume = setVolume0(qtMediaRef, f);
    }
    return currVolume;
  }

  public int getPlaybackCaps()
  {
    // TODO: we can check playback caps once we've loaded a file
    return 0
        | FRAME_STEP_FORWARD_CAP
        | FRAME_STEP_BACKWARD_CAP
        | PAUSE_CAP
        | PLAYRATE_FAST_CAP
        | PLAYRATE_SLOW_CAP
        | PLAY_REV_CAP
        | PLAYRATE_FAST_REV_CAP
        | PLAYRATE_SLOW_REV_CAP
        | SEEK_CAP
        //			| TIMESHIFT_CAP
        //			| LIVE_CAP
        ;
  }

  public java.awt.Color getColorKey() { return null; }

  public void setVideoRectangles(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    synchronized(this) {
      if(qtMediaRef != 0) setVideoRectangles0(qtMediaRef, videoSrcRect, videoDestRect, hideCursor);
    }
  }

  public java.awt.Dimension getVideoDimensions()
  {
    synchronized(this) {
      if(qtMediaRef != 0) return getVideoDimensions0(qtMediaRef);
    }
    return null;
  }

  // TODO: enable CC support (if we can)
  public boolean setClosedCaptioningState(int ccState)
  {
    return false;
  }

  public int getClosedCaptioningState()
  {
    return CC_DISABLED;
  }

  private static native boolean canHasPlayerFor(String url, String container, String video, String audio);

  // URL can be: file:, http:, rtsp: or stv:
  private native long load0(String url) throws PlaybackException;
  private native long fastLoad0(String url) throws PlaybackException;
  private native void unload0(long movieRef);

  /*
		Values returned by getLoadState0:
		< 0 : Error while loading (unplayable)
		1000 : movie is loading
		2000 : movie atom has loaded, safe to query attributes
		10000 : enough of the movie has loaded to start playing (might not be able to seek yet)
		20000 : enough of movie is loaded to play to end
		100000 : movie is completely loaded
   */
  private native int getLoadState0(long movieRef);

  private native long getMediaDurationMillis0(long movieRef);
  private native long getMediaTimeMillis0(long movieRef);

  private native boolean play0(long movieRef);
  private native boolean pause0(long movieRef);
  private native void stop0(long movieRef);
  private native boolean step0(long movieRef, int amount);
  private native long seek0(long movieRef, long whence) throws PlaybackException; // whence = time from beginning in millis
  private native float setPlaybackRate0(long movieRef, float rate);
  private native float getPlaybackRate0(long movieRef);

  private native boolean getMute0(long movieRef);
  private native void setMute0(long movieRef, boolean m);
  private native float getVolume0(long movieRef);
  private native float setVolume0(long movieRef, float v);

  private native void setVideoRectangles0(long movieRef, java.awt.Rectangle srcRect, java.awt.Rectangle destRect, boolean hideCursor);
  private native java.awt.Dimension getVideoDimensions0(long movieRef);

  private UIManager uiMgr;
  long qtMediaRef;	// our native movie reference

  private long lastMillis;

  protected int currState;
  protected java.io.File currFile;
  protected byte currHintMajorType;
  protected byte currHintMinorType;
  protected String currHintEncoding;

  protected java.awt.Dimension videoDimensions;

  //	protected int currCCState;
  protected boolean muteState = true;
  protected float currVolume = 0.0f;
  protected boolean eos;
}
