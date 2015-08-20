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

import java.awt.*;

public class MacDVDPlayer implements DVDMediaPlayer
{

  // the codes we'll get from the DVDPlayback framework will be the two letter ISO 639 codes
  public static String convertLangCode(String s)
  {
    if (s == null) return s;
    if (s.length() >= 2)
    {
      return new java.util.Locale(s.substring(0, 2)).getDisplayLanguage() + s.substring(2);
    }
    else
      return s;
  }
  public static String[] convertLangCodes(String[] s)
  {
    if (s == null) return s;
    String[] rv = new String[s.length];
    for (int i = 0; i < rv.length; i++)
      rv[i] = convertLangCode(s[i]);
    return rv;
  }

  /*
	Loosely modelled after the DShow DVD player

Calls from MediaPlayer:
	void load(byte majorTypeHint, byte minorTypeHint, String encodingHint,
		java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException;
													-> DVDInitialize, DVDOpenMediaFile/DVDOpenMediaVolume
	void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint,
		java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException;
	boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file);
													-> return false
	void free();									-> DVDDispose
	void inactiveFile();
	void stop();									-> DVDStop
	java.io.File getFile();

	int NO_STATE = 0;
	int LOADED_STATE = 1;
	int PLAY_STATE = 2;
	int PAUSE_STATE = 3;
	int STOPPED_STATE = 4;
	int EOS_STATE = 5;

	int getState();									-> loaded, DVDGetState
	long getDurationMillis();						-> DVDGetTime (kDVDTimeCodeTitleDurationSeconds)
	long getMediaTimeMillis();						-> DVDGetTime (kDVDTimeCodeElapsedSeconds) * 1000.0
	float getPlaybackRate();						-> DVDGetScanRate
	boolean pause();								-> DVDPlay
	boolean play();									-> DVDPause
	long seek(long seekTimeMillis)					-> DVDSetTime (kDVDTimeCodeElapsedSeconds) / 1000.0
				throws PlaybackException;
	float setPlaybackRate(float newRate);			-> DVDScan
	boolean frameStep(int amount);					-> DVDSetTime or DVDStepFrame (single frame)
	boolean getMute();								-> DVDIsMuted
	void setMute(boolean x);						-> DVDMute
	float getVolume();								-> DVDGetAudioVolume
	float setVolume(float f);						-> DVDSetAudioVolume
	java.awt.Color getColorKey();					-> DVDGetVideoKeyColor (return null if not used)

	void setVideoRectangles(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor);
	java.awt.Dimension getVideoDimensions();		-> DVDGetNativeVideoSize
	boolean setClosedCaptioningState(int ccState);
	int getClosedCaptioningState();

Calls from DVDMediaPlayer:
	boolean playControlEx(int playCode, long param1, long param2) throws PlaybackException;
	int getDVDTitle();								-> DVDGetTitle
	int getDVDTotalTitles();						-> DVDGetNumTitles
	int getDVDChapter();							-> DVDGetChapter
	int getDVDTotalChapters();						-> DVDGetNumChapters
	int getDVDDomain();								-> event callback (kDVDEventDomain)
	boolean areDVDButtonsVisible();					-> DVDGetButtoninfo
	int getDVDAngle();								-> DVDGetAngle
	int getDVDTotalAngles();						-> DVDGetNumAngles
	String getDVDLanguage();						-> DVDGetMenuLanguageCode (or audio language?)
	String[] getDVDAvailableLanguages();			-> Do same as getDVDAvailableSubpictures
	String getDVDSubpicture();						-> DVDGetSubPictureStream get language code in cached stream list
	String[] getDVDAvailableSubpictures();			-> DVDGetNumSubPictureStreams call DVDGetSubPictureLanguageCodeByStream to build list, cache for later use
	float getCurrentAspectRatio();					-> DVDGetAspectRatio (convert enums to floats)

	call convertLangCode to convert the 2 letter language code to a readable string
   */

  public MacDVDPlayer()
  {
    super();
  }

  public void videoChanged()
  {
    // called from native when the DVD aspect ratio or native video size changes and we need to readjust the video bounds
    VideoFrame.getVideoFrameForPlayer(this).refreshVideoSizing();
    dvdStateChanged();
  }

  public void dvdStateChanged()
  {
    // called from native when the DVD state changes (language, menu, chapter, title, angle, subpicture... etc...)
    VideoFrame.getVideoFrameForPlayer(this).playbackControl(0);
  }

  public int getPlaybackCaps()
  {
    return FRAME_STEP_FORWARD_CAP | PAUSE_CAP | SEEK_CAP | PLAY_REV_CAP | PLAYRATE_FAST_CAP | PLAYRATE_SLOW_CAP |
        PLAYRATE_SLOW_REV_CAP | PLAYRATE_FAST_REV_CAP;
  }

  public float getPlaybackRate()
  {
    return getDVDRate0();
    //		return dvdPlayRate;
  }

  /*
   * NOTE NOTE NOTE: The DVD player needs to go through Sage when it makes the initial call to start the graph or
   * it will hang.
   */
  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    boolean isLocal = false;

    // treat UNC paths as local, since they're mounted locally anyways
    if(file != null) {
      isLocal = file.toString().startsWith("\\\\") || (hostname == null | (hostname != null && (hostname.equalsIgnoreCase("localhost") || hostname.equals("127.0.0.1"))));
    }

    // if given a hostname and it's not localhost, then make sure we have an actual host NAME and not an IP address
    if(!isLocal) {
      try {
        java.net.InetAddress address = java.net.InetAddress.getByName(hostname);
        hostname = address.getHostName();
      } catch (Throwable t) {
        // hope for the best I guess...
        System.out.println("Exception getting host name: "+t);
      }
    }

    if(Sage.DBG && file != null) System.out.println("MacDVDPlayer loading "+(isLocal ? "LOCAL" : "REMOTE")+" path \""+file.toString()+"\"  on host: "+hostname);

    // map UNC paths, if we can
    if (file != null && file.toString().startsWith("\\\\"))
    {
      // UNC path from a Windows server so mount it and then unmount it when we're done
      String fullPath = file.toString();
      int s1 = fullPath.indexOf('\\', 2);
      if (s1 == -1)
        throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
      String serverName = fullPath.substring(2, s1);
      int s2 = fullPath.indexOf('\\', s1 + 1);
      if (s2 == -1)
        throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
      String shareName = fullPath.substring(s1 + 1, s2);
      String subPath = fullPath.substring(s2 + 1);
      subPath = subPath.replace('\\', '/');
      mountedPath = "/tmp/sagetvclient_shares/" + serverName + "/" + shareName;
      new java.io.File(mountedPath).mkdirs();
      if (IOUtils.exec2(new String[] { "mount_smbfs", "-N", "//guest:@" + serverName + "/" + shareName, mountedPath}) == 0)
      {
        if (Sage.DBG) System.out.println("DVD SMB Mount Succeeded");
      }
      else
        throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
      file = new java.io.File(mountedPath, subPath);
    }

    if (file != null && file.isFile())
    {
      // This is an ISO image instead of a DVD directory; so mount it and then change the file path to be the image
      java.io.File mountDir = FSManager.getInstance().requestISOMount(file, VideoFrame.getVideoFrameForPlayer(this).getUIMgr());
      if (mountDir == null)
      {
        if (Sage.DBG) System.out.println("FAILED mounting ISO image for DVD playback");
        throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
      }
      unmountRequired = mountDir;
      if (new java.io.File(mountDir, "video_ts").isDirectory())
        file = new java.io.File(mountDir, "video_ts");
      else if (new java.io.File(mountDir, "VIDEO_TS").isDirectory())
        file = new java.io.File(mountDir, "VIDEO_TS");
      else
        file = mountDir;
    }
    try {
      if(file == null)
        load0(null, null);
      else
        // we can't use the absolute path on remote paths as it will ass-ume the path is relative to the current directory if it's a Windows path
        load0(hostname, isLocal ? file.getAbsolutePath() : file.toString());
    } catch (PlaybackException pe) {
      throw pe; // let playback exceptions pass through
    } catch (Throwable t) {
      System.out.println("Exception while starting DVD playback: " + t);
    }
  }

  public synchronized void free()
  {
    free0();

    // ISO unmounting
    if (unmountRequired != null)
    {
      java.io.File removeMe = unmountRequired;
      unmountRequired = null;
      FSManager.getInstance().releaseISOMount(removeMe);
    }
    // SMB unmounting
    if (mountedPath != null)
    {
      if (IOUtils.exec2(new String[] { "umount", mountedPath}) == 0)
      {
        if (Sage.DBG) System.out.println("Successfully unmounted:" + mountedPath);
      }
      else
      {
        if (Sage.DBG) System.out.println("FAILED unmounting:" + mountedPath);
      }
      mountedPath = null;
    }
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

  public void inactiveFile()
  {
    // This player doesn't support timeshifting so we don't do that here
  }

  public java.io.File getFile()
  {
    return currFile;
  }

  public java.awt.Color getColorKey()
  {
    return null;
  }

  /*
	public static final int DVD_CONTROL_MENU = 201; // 1 for title, 2 for root
	public	static final int DVD_CONTROL_TITLE_SET = 202;
	public	static final int DVD_CONTROL_CHAPTER_SET = 205;
	public	static final int DVD_CONTROL_CHAPTER_NEXT = 206;
	public	static final int DVD_CONTROL_CHAPTER_PREV = 207;
	public	static final int DVD_CONTROL_ACTIVATE_CURRENT = 208;
	public	static final int DVD_CONTROL_RETURN = 209;
	public	static final int DVD_CONTROL_BUTTON_NAV = 210; // 1up,2right,3down,4left
	public	static final int DVD_CONTROL_MOUSE_HOVER = 211;
	public	static final int DVD_CONTROL_MOUSE_CLICK = 212;
	public	static final int DVD_CONTROL_ANGLE_CHANGE = 213;
	public	static final int DVD_CONTROL_SUBTITLE_CHANGE = 214;
	public	static final int DVD_CONTROL_SUBTITLE_TOGGLE = 215;
	public	static final int DVD_CONTROL_AUDIO_CHANGE = 216;
	public	static final int DVD_CONTROL_RESUME_PLAYBACK = 217;
   */
  public boolean playControlEx(int playCode, long param1, long param2) throws PlaybackException
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      synchronized (this)
      {
        boolean rv = playbackControlMessage0(playCode, param1, param2);
        //				lastAspectRatio = getDVDAspectRatio0();
        return rv;
      }
    }
    return false;
  }

  public long seek(long seekTimeMillis) throws PlaybackException
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        seekDVD0(seekTimeMillis);
        // We have to run the graph when seeking a DVD or it doesn't render the new frames
        if (getDVDState0() == PAUSE_STATE)
          play();
        return getMediaTimeMillis();
      }
    }
    else
      return 0;
  }

  public boolean pause()
  {
    int currState = getDVDState0();
    if (currState == LOADED_STATE || currState == PLAY_STATE)
    {
      synchronized (this)
      {
        return pause0();
      }
    }
    return currState == PAUSE_STATE;
  }

  public boolean play()
  {
    int currState = getDVDState0();
    if (currState == LOADED_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return play0();
      }
    }
    return currState == PLAY_STATE;
  }

  public void stop()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      //			Sage.stop(this); // ???
      stop0();
    }
  }

  public boolean frameStep(int amount)
  {
    // Frame stepping will pause it if playing
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        if (currState == PLAY_STATE)
        {
          if(!pause0()) return false;
        }
        return frameStep0(amount);
      }
    }
    else
      return false;
  }

  public float setPlaybackRate(float newRate)
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        setDVDRate0(newRate);
        return getDVDRate0();

        //				if (setDVDRate0(newRate))
        //					dvdPlayRate = newRate;
        //				return dvdPlayRate;
      }
    }
    else
      return 1.0f;
  }

  public long getDurationMillis()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      synchronized (this)
      {
        return getDurationMillis0();
      }
    }
    else
      return 0;
  }

  public long getMediaTimeMillis()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getMediaTimeMillis0();
      }
    }
    return 0;
  }

  public int getState()
  {
    return getDVDState0(); // handles EOS state
    //		return eos ? EOS_STATE : currState;
  }

  public void setMute(boolean state)
  {
    setMute0(state);
  }

  public boolean getMute()
  {
    return getMute0();
  }

  public float setVolume(float f)
  {
    return setVolume0(f);
  }

  public synchronized float getVolume()
  {
    return getVolume0();
  }

  public int getTransparency()
  {
    return TRANSLUCENT; // BITMASK, TRANSLUCENT, OPAQUE (?)
  }

  public boolean setClosedCaptioningState(int ccState)
  {
    return false;
  }

  public int getClosedCaptioningState()
  {
    return CC_DISABLED;
  }

  public boolean areDVDButtonsVisible()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return areDVDButtonsVisible0();
      }
    }
    return false;
  }

  public int getDVDAngle()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDAngle0();
      }
    }
    return 0;
  }

  public String[] getDVDAvailableLanguages()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        String langs[] = getDVDAvailableLanguages0();
        if(langs != null) return convertLangCodes(langs);
      }
    }
    return new String[0];
  }

  public String[] getDVDAvailableSubpictures()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        String langs[] = getDVDAvailableSubpictures0();
        if(langs != null) return convertLangCodes(langs);
      }
    }
    return new String[0];
  }

  public int getDVDChapter()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDChapter0();
      }
    }
    return 0;
  }

  public int getDVDTotalChapters()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDTotalChapters0();
      }
    }
    return 0;
  }

  public int getDVDDomain()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDDomain0();
      }
    }
    return 0;
  }

  public String getDVDLanguage()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        String lang = getDVDLanguage0();
        if(lang != null) return convertLangCode(lang);
      }
    }
    return null;
  }

  public String getDVDSubpicture()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        String lang = getDVDSubpicture0();
        if(lang != null) return convertLangCode(lang);
      }
    }
    return null;
  }

  public int getDVDTitle()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDTitle0();
      }
    }
    return 0;
  }

  public int getDVDTotalAngles()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDTotalAngles0();
      }
    }
    return 0;
  }

  public int getDVDTotalTitles()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDTotalTitles0();
      }
    }
    return 0;
  }

  public float getCurrentAspectRatio()
  {
    int currState = getDVDState0();
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      // If we make the native call now it'll deadlock against the rendering threads, so do it when we process events instead.
      //			lastAspectRatio = getDVDAspectRatio0();
      return getDVDAspectRatio0();//lastAspectRatio;
    }
    return 0;
  }

  public java.awt.Dimension getVideoDimensions()
  {
    if (nullVideoDim || (getDVDState0() == NO_STATE)) return null;
    //		if (videoDimensions != null)
    //			return videoDimensions;
    synchronized (this)
    {
      videoDimensions = getVideoDimensions0();
      if (Sage.DBG) System.out.println("Got Native Video Dimensions " + videoDimensions);
      return videoDimensions;
    }
  }

  public void setVideoRectangles(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    int currState = getDVDState0();
    if(Sage.DBG) System.out.println("MacDVDPlayer.setVideoRectangles("+videoSrcRect+","+videoDestRect+","+hideCursor+"), currState = "+currState);
    if (currState != NO_STATE)
    {
      synchronized (this)
      {
        resizeVideo0(videoSrcRect, videoDestRect, hideCursor);
      }
    }
  }

  // init and term the DVD playback framework, only one client can be using the framework at a time
  private native void load0(java.lang.String hostname, java.lang.String path) throws PlaybackException;
  private native void free0();

  /* MediaPlayer native methods */
  protected native long getDurationMillis0();
  protected native long getMediaTimeMillis0();
  protected native boolean frameStep0(int amount);
  protected native boolean pause0();
  protected native boolean play0();
  protected native void stop0();

  protected native boolean getMute0();
  protected native void setMute0(boolean mute);
  protected native float getVolume0();
  protected native float setVolume0(float vol);

  protected native java.awt.Dimension getVideoDimensions0();
  protected native void resizeVideo0(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor);

  /* DVDMediaPlayer native methods */
  private native int getDVDState0();
  private native int getDVDTitle0();
  private native int getDVDTotalTitles0();
  private native int getDVDChapter0();
  private native int getDVDTotalChapters0();
  private native int getDVDDomain0();
  private native boolean areDVDButtonsVisible0();
  private native int getDVDAngle0();
  private native int getDVDTotalAngles0();
  private native String getDVDLanguage0();
  private native String[] getDVDAvailableLanguages0();
  private native String getDVDSubpicture0();
  private native String[] getDVDAvailableSubpictures0();
  protected native boolean setDVDRate0(float newRate);
  protected native float getDVDRate0();
  protected native boolean seekDVD0(long time) throws PlaybackException;
  protected native boolean playbackControlMessage0(int code, long param1, long param2) throws PlaybackException;

  protected native float getDVDAspectRatio0();

  //	private float dvdPlayRate = 1.0f;
  //	protected int currState;
  protected java.io.File currFile;
  protected byte currHintMajorType;
  protected byte currHintMinorType;
  protected String currHintEncoding;
  protected String prefs;

  protected int transparency;
  protected java.awt.Color colorKey;
  protected java.awt.Dimension videoDimensions;

  protected int currCCState;

  protected boolean eos;

  protected boolean nullVideoDim;

  protected int languageIndex;

  protected float lastVolume;

  protected int currDVDTitle;
  protected int currDVDTotalTitles;
  protected int currDVDChapter;
  protected int currDVDTotalChapters;
  protected int currDVDDomain;
  protected boolean dvdButtonsVisible;
  protected int currDVDAngle;
  protected int currDVDTotalAngles;
  protected String currDVDLanguage;
  protected String[] currDVDAvailableLanguages;
  protected String currDVDSubpicture;
  protected String[] currDVDAvailableSubpictures;
  protected float lastAspectRatio;

  protected String mountedPath; // for ISO
  protected java.io.File unmountRequired; // for SMB
}
