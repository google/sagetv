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

import sage.dvd.dsi_t;
/**
 *
 * @author  Jean-Francois
 */
public class MiniDVDPlayer implements DVDMediaPlayer, MiniDVDPlayerIdentifier
{
  protected static final long GUESS_VALIDITY_DURATION = 1000;
  public static final int MEDIACMD_INIT = 0;
  public static final int MEDIACMD_DEINIT = 1;
  public static final int MEDIACMD_OPENURL = 16;
  public static final int MEDIACMD_GETMEDIATIME = 17;
  public static final int MEDIACMD_SETMUTE = 18;
  public static final int MEDIACMD_STOP = 19;
  public static final int MEDIACMD_PAUSE = 20;
  public static final int MEDIACMD_PLAY = 21;
  public static final int MEDIACMD_FLUSH = 22;
  public static final int MEDIACMD_PUSHBUFFER = 23;
  public static final int MEDIACMD_GETVIDEORECT = 24;
  public static final int MEDIACMD_SETVIDEORECT = 25;
  public static final int MEDIACMD_GETVOLUME = 26;
  public static final int MEDIACMD_SETVOLUME = 27;
  public static final int MEDIACMD_FRAMESTEP = 28;
  public static final int MEDIACMD_SEEK = 29;
  // New DVD Commands
  public static final int MEDIACMD_DVD_NEWCELL = 32;
  public static final int MEDIACMD_DVD_CLUT = 33;
  public static final int MEDIACMD_DVD_SPUCTRL = 34;
  public static final int MEDIACMD_DVD_STC = 35;
  public static final int MEDIACMD_DVD_STREAM = 36;
  public static final int MEDIACMD_DVD_FORMAT = 37;

  public static final boolean DEBUG_MINIDVD = Sage.getBoolean("miniplayer/dvd_debug", false) && Sage.DBG;

  public MiniDVDPlayer()
  {
    currState = NO_STATE;
    temp_pci = new sage.dvd.pci_t();
    temp_dsi = new sage.dvd.dsi_t();
    pcidsiQueue = new java.util.ArrayList();
    pciPool = new java.util.ArrayList();
    dsiPool = new java.util.ArrayList();
  }

  public boolean canFastLoad(byte majorTypeHint, byte minorTypeHint,
      String encodingHint, java.io.File file)
  {
    return false;
  }

  public synchronized void fastLoad(byte majorTypeHint, byte minorTypeHint,
      String encodingHint, java.io.File file, String hostname,
      boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException
      {
    if (Sage.DBG) System.out.println("MiniDVD Fast Load");
    throw new PlaybackException();
      }

  public boolean frameStep(int amount)
  {
    if (Sage.DBG) System.out.println("MiniDVD frameStep");
    // Frame stepping will pause it if playing
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        if (currState == PLAY_STATE)
        {
          pause();
          currState = PAUSE_STATE;
        }
        if (supportsFrameStep)
        {
          frameStep0(1);
        }
        return true;
      }
    }
    return false;
  }

  public synchronized void free()
  {
    if (uiMgr != null)
      uiMgr.putFloat("miniplayer/last_volume", curVolume);
    if (currState != STOPPED_STATE)
      stop();
    addYieldDecoderLock();
    synchronized (decoderLock)
    {
      closeDriver0(ptr);
      removeYieldDecoderLock();
      decoderLock.notifyAll();
    }
    currState = NO_STATE;
    currFile = null;
    sentDiscardPtsFlag = false;
    sentTrickmodeFlag = false;
    needToPlay = false;
    numPushedBuffers = 0;
    pausetime = 0;
    duration = 0;
    cellStart = 0;
    timeGuessMillis = 0;
    guessTimestamp = 0;
    if(reader!=null)
    {
      reader.close();
      reader = null;
    }
    pushThread = null;
    if (unmountRequired != null)
    {
      java.io.File removeMe = unmountRequired;
      unmountRequired = null;
      FSManager.getInstance().releaseISOMount(removeMe);
    }

    if (Sage.DBG) System.out.println("Closing down MiniDVDPlayer");
  }

  public long getDurationMillis()
  {
    NAVTracker currTracker = getCurrNAVTracker();
    if (currTracker != null)
      return currTracker.cellDur/90;
    else
      return duration/90;
  }

  public long getMediaTimeMillis()
  {
    if (Sage.eventTime() - guessTimestamp < GUESS_VALIDITY_DURATION) // after seeks it doesn't know the right time at first
    {
      return timeGuessMillis;
    }
    //if (Sage.DBG) System.out.println("MiniDVD getMediaTimeMillis cellStart=" + cellStart + " time0=" + getMediaTimeMillis0(ptr));
    NAVTracker currTracker = getCurrNAVTracker();
    long currTime0 = getMediaTimeMillis0(ptr);
    if (currTracker != null)
    {
      long dsiStart = currTracker.dsi.sml_pbi.vob_v_s_ptm.get()/90;
      //			if (Sage.DBG) System.out.println("MiniDVD getMediaTimeMillis0 dsiStart=" + dsiStart + " currTime0=" + currTime0 +
      //				" cellStart=" + currTracker.cellStart/90);
      return currTracker.dsi.dsi_gi.c_eltm.toPTS()/90 + currTracker.cellStart/90;
    }
    else
    {
      //			if (Sage.DBG) System.out.println("MiniDVD getMediaTimeMillis0 currTime0=" + currTime0);
      return currTime0;
    }
  }

  public int getPlaybackCaps()
  {
    return PAUSE_CAP | SEEK_CAP; /* FRAME_STEP_FORWARD_CAP | */
  }

  public int getState()
  {
    return eos ? EOS_STATE : currState;
  }

  public float getPlaybackRate()
  {
    return myRate;
  }

  public java.awt.Dimension getVideoDimensions()
  {
    return new java.awt.Dimension(720,480);
  }

  public synchronized void setVideoRectangles(java.awt.Rectangle videoSrcRect,
      java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    return;
  }

  public void setMute(boolean x)
  {
    if (currMute != x)
    {
      addYieldDecoderLock();
      synchronized (decoderLock)
      {
        setMute0(ptr, currMute = x);
        removeYieldDecoderLock();
        decoderLock.notifyAll();
      }
    }
  }

  public boolean getMute()
  {
    return currMute;
  }

  public float setVolume(float f)
  {
    addYieldDecoderLock();
    synchronized (decoderLock)
    {
      if(f>1.0f) f=1.0f;
      if(f<0.0f) f=0.0f;
      setVolume0(f);
      curVolume=f;
      removeYieldDecoderLock();
      decoderLock.notifyAll();
    }
    return f;
  }

  public synchronized float getVolume()
  {
    return curVolume;
  }

  public int getTransparency()
  {
    return TRANSLUCENT;
  }

  public void inactiveFile()
  {
  }

  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    uiMgr = VideoFrame.getVideoFrameForPlayer(MiniDVDPlayer.this).getUIMgr();
    MiniClientSageRenderer render = null;
    if (uiMgr.getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
    {
      render = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
      MetaImage.clearHiResNativeCache(render);
      if (render.isMediaExtender() && render.isSupportedVideoCodec("MPEG2-VIDEO@HL"))
        hdMediaExtender = true;
      supportsFrameStep = render.supportsFrameStep();
    }

    currState = NO_STATE;
    myRate = 1;
    eos = false;
    duration = 0;

    // Set the curVolume field now so that if the open fails for any reason; the call to free() doesn't set the property
    // to 1.0f; it instead retains its value
    curVolume = uiMgr.getFloat("miniplayer/last_volume", 1.0f);

    // Do this before we load the player so we don't screw up the driver if the mount fails due to network issues
    if (file.isFile())
    {
      // This is an ISO image instead of a DVD directory; so mount it and then change the file path to be the image
      java.io.File mountDir = null;
      mountDir = FSManager.getInstance().requestISOMount(file, uiMgr, mountDir);
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

    boolean isPALOutput = (MMC.getInstance().isNTSCVideoFormat() ?
        Sage.getInt("osd_rendering_height_ntsc", 480) :
          Sage.getInt("osd_rendering_height_pal", 576)) == 576;
    // We don't care about pal/ntsc for media, it's set in gfx
    // We'll use that variable to specify 4:3 or 16:9 display
    if (Sage.DBG) System.out.println("display aspect :"
        +VideoFrame.getVideoFrameForPlayer(this).getDisplayAspectRatio());
    ptr = initDriver0(
        VideoFrame.getVideoFrameForPlayer(this).getDisplayAspectRatio() > 1.40 ?
            1 :0 );
    if (ptr == 0)
      throw new PlaybackException();

    reader = new sage.dvd.VM();
    reader.setMonitor16_9(VideoFrame.getVideoFrameForPlayer(this).getDisplayAspectRatio() > 1.40 ?
        true : false);
    if(!reader.reset(file.toString(), hostname))
    {
      throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
    }

    currFile = file;
    currState = LOADED_STATE;
    timeGuessMillis = 0;
    guessTimestamp = Sage.eventTime();

    // Preserve the volume setting across UI sessions
    setVolume(curVolume);

    play(); // Why does it call play here? JF
    createPushThread();
  }

  public java.io.File getFile()
  {
    return currFile;
  }

  public boolean pause()
  {
    if (currState == PLAY_STATE)
    {
      synchronized (this)
      {
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          needToPlay = false;
          pausePush0(ptr);
          currState = PAUSE_STATE;
          removeYieldDecoderLock();
          decoderLock.notifyAll();
        }
      }
    }
    return currState == PAUSE_STATE;
  }

  public boolean play()
  {
    if (currState == LOADED_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        needToPlay = true;
        currState = PLAY_STATE;
      }
    }
    return currState == PLAY_STATE;
  }

  public long seek(long seekTimeMillis) throws PlaybackException
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      synchronized (this)
      {
        timeGuessMillis = seekTimeMillis;
        guessTimestamp = Sage.eventTime();
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          if (Sage.DBG) System.out.println("MiniDVD seek to "+seekTimeMillis);
          long rv = reader.seek(seekTimeMillis);
          removeYieldDecoderLock();
          decoderLock.notifyAll();
          return rv;
        }
      }
    }
    return 0;
  }

  public boolean setClosedCaptioningState(int ccState)
  {
    return false;
  }

  public int getClosedCaptioningState()
  {
    return CC_DISABLED;
  }

  public java.awt.Color getColorKey()
  {
    return null;
  }

  public float setPlaybackRate(float newRate)
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      synchronized (this)
      {
        if(reader.getDVDDomain()!=4) newRate=1.0f; // no rate except in VTS
        boolean reseek = (newRate == 1.0f) && (myRate != newRate) && (reader.getDVDDomain() == 4);
        long currTime = 0;
        if (reseek)
          currTime = getMediaTimeMillis();
        if (myRate != newRate)
          if (Sage.DBG) System.out.println("MiniDVD setPlaybackRate"+newRate);
        myRate = newRate;
        reader.set_rate((int)myRate);
        if (reseek)
        {
          try
          {
            seek(currTime);
          }
          catch (PlaybackException pe)
          {
            if (Sage.DBG) System.out.println("ERROR seeking after resuming normal speed play");
          }
        }
        return myRate;
      }
    }
    return 1;
  }

  public void stop()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        if (Sage.DBG) System.out.println("MiniDVD stop");
        currState = STOPPED_STATE;
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          stopPush0(ptr);
          removeYieldDecoderLock();
          decoderLock.notifyAll();
        }
      }
    }
    else
      currState = STOPPED_STATE;
  }

  public boolean areDVDButtonsVisible()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return reader.areDVDButtonsVisible((getCurrNAVTracker()!=null) ?getCurrNAVTracker().pci : null);
      }
    }
    return false;
  }

  public int getDVDAngle()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return reader.getDVDAngle();
      }
    }
    return 0;
  }

  public String[] getDVDAvailableLanguages()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        String[] lang = reader.getDVDAvailableLanguages(); //new String[1];
        //lang[0]="EN";
        return lang;
      }
    }
    return Pooler.EMPTY_STRING_ARRAY;
  }

  public String[] getDVDAvailableSubpictures()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        String[] lang = reader.getDVDAvailableSubpictures(); //new String[1];
        //lang[0]="EN";
        return lang;
      }
    }
    return Pooler.EMPTY_STRING_ARRAY;
  }

  public int getDVDChapter()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return reader.getDVDChapter();
      }
    }
    return 0;
  }

  public int getDVDTotalChapters()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return reader.getDVDTotalChapters();
      }
    }
    return 0;
  }

  public int getDVDDomain()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return reader.getDVDDomain();
      }
    }
    return 0;
  }

  public String getDVDLanguage()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return reader.getDVDLanguage(); //convertLangCode("EN");
      }
    }
    return "";
  }

  public String getDVDSubpicture()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return reader.getDVDSubpicture(); //convertLangCode("EN");
      }
    }
    return "";
  }

  public int getDVDTitle()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return reader.getDVDTitle();
      }
    }
    return 0;
  }

  public int getDVDTotalAngles()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return reader.getDVDTotalAngles();
      }
    }
    return 0;
  }

  public int getDVDTotalTitles()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return reader.getDVDTotalTitles();
      }
    }
    return 0;
  }

  /*
	public static final int DVD_CONTROL_MENU = 201; // 1 for title, 2 for root
	public  static final int DVD_CONTROL_TITLE_SET = 202;
	public  static final int DVD_CONTROL_CHAPTER_SET = 205;
	public  static final int DVD_CONTROL_CHAPTER_NEXT = 206;
	public  static final int DVD_CONTROL_CHAPTER_PREV = 207;
	public  static final int DVD_CONTROL_ACTIVATE_CURRENT = 208;
	public  static final int DVD_CONTROL_RETURN = 209;
	public  static final int DVD_CONTROL_BUTTON_NAV = 210; // 1up,2right,3down,4left
	public  static final int DVD_CONTROL_MOUSE_HOVER = 211;
	public  static final int DVD_CONTROL_MOUSE_CLICK = 212;
	public  static final int DVD_CONTROL_ANGLE_CHANGE = 213;
	public  static final int DVD_CONTROL_SUBTITLE_CHANGE = 214;
	public  static final int DVD_CONTROL_SUBTITLE_TOGGLE = 215;
	public  static final int DVD_CONTROL_AUDIO_CHANGE = 216;
   */
  public boolean playControlEx(int playCode, long param1, long param2) throws PlaybackException
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      synchronized (this)
      {
        if (Sage.DBG) System.out.println("MiniDVD playControlEx "+
            playCode +","+param1+","+param2);
        if(playCode==210)
          return reader.playControlEx(playCode, 0, getNavButton((int)param1),
              (getCurrNAVTracker()!=null) ?getCurrNAVTracker().pci : null);
        else
          return reader.playControlEx(playCode, param1, param2,
              (getCurrNAVTracker()!=null) ?getCurrNAVTracker().pci : null);
      }
    }
    return false;
  }

  protected NAVTracker getCurrNAVTracker()
  {
    // Get the current cell time of the player
    long currTime = getMediaTimeMillis0(ptr)*90;
    synchronized (pcidsiQueue)
    {
      for (int i = 0; i < pcidsiQueue.size(); i++)
      {
        NAVTracker currNav = (NAVTracker) pcidsiQueue.get(i);
        if (currTime >= currNav.navstart  &&
            currTime <= currNav.navend )
        {
          for (int j = 0; j < i; j++)
          {
            NAVTracker killMe = (NAVTracker) pcidsiQueue.remove(0);
            dsiPool.add(killMe.dsi);
            pciPool.add(killMe.pci);
          }
          return currNav;
        }
      }

      if (!pcidsiQueue.isEmpty() && myRate != 1.0f)
      {

        NAVTracker currNav = (NAVTracker) pcidsiQueue.get(pcidsiQueue.size() - 1);
        while (pcidsiQueue.size() > 1)
        {
          NAVTracker killMe = (NAVTracker) pcidsiQueue.remove(0);
          dsiPool.add(killMe.dsi);
          pciPool.add(killMe.pci);
        }
        return currNav;
      }
      if (!pcidsiQueue.isEmpty())
        return (NAVTracker)pcidsiQueue.get(0);
    }
    return null;
  }

  /* Temporary stuff from monopoly game... until I write nice struct handler */
  // Utility class for byte conversion

  // EFFECTS: return long value of 8 bytes starting at s in array b
  public static long convLong(byte b[], int s)
  {
    long val=((long)(b[s+7]) & 0xFF)
        +((((long)(b[s+6]) & 0xFF))<<8)
        +((((long)(b[s+5]) & 0xFF))<<16)
        +((((long)(b[s+4]) & 0xFF))<<24)
        +((((long)(b[s+3]) & 0xFF))<<32)
        +((((long)(b[s+2]) & 0xFF))<<40)
        +((((long)(b[s+1]) & 0xFF))<<48)
        +((((long)(b[s+0]) & 0x7F))<<56);
    if(b[s]<0) // test negative
    {
      return -9223372036854775808L+val;
    }
    else
    {
      return val;
    }
  }

  // EFFECTS: return unsigned int value of 4 bytes starting at s in array b
  public static long convUInt(byte b[], int s)
  {
    return ((long)(b[s+3]) & 0xFF)
        +((((long)(b[s+2]) & 0xFF))<<8)
        +((((long)(b[s+1]) & 0xFF))<<16)
        +((((long)(b[s]) & 0xFF))<<24);
  }

  // EFFECTS: return int value of 4 bytes starting at s in array b
  public static int convInt(byte b[], int s)
  {
    int val=((int)(b[s+3]) & 0xFF)
        +((((int)(b[s+2]) & 0xFF))<<8)
        +((((int)(b[s+1]) & 0xFF))<<16)
        +((((int)(b[s]) & 0x7F))<<24);

    if(b[s]<0) // test negative
    {
      return -2147483648+val;
    }
    else
    {
      return val;
    }
  }

  // EFFECTS: return int value of 2 unsigned bytes starting at s in array b
  public static int convUShort(byte b[], int s)
  {
    return (((int)b[s+1]) & 0xFF)
        +((((int)(b[s]) & 0xFF))<<8);
  }

  // EFFECTS: return int value of 1 unsigned byte starting at s in array b
  public static int convUByte(byte b[], int s)
  {
    return (((int)b[s]) & 0xFF);
  }

  /**
   * EFFECTS: update byte array with unsigned int value of 1 byte
   * <p>
   * @param b the array to put the byte.
   * @param s the start position in the array.
   * @param val the value to store in the array.
   */
  public static void setUByte(byte b[], int s, int val)
  {
    if((val&0x80)!=0) // need to set negative bit of byte
    {
      b[s]=(byte) (-128+(val&0x7F));
    }
    else
    {
      b[s]=(byte) (val&0x7F);
    }
  }

  /**
   * EFFECTS: update byte array with unsigned int value of 2 bytes
   * <p>
   * @param b the array to put the 2 bytes.
   * @param s the start position in the array.
   * @param val the value to store in the array.
   */
  public static void setUShort(byte b[], int s, int val)
  {
    setUByte(b,s+1,val&0xFF);
    setUByte(b,s,(val&0xFF00)>>8);
  }

  /**
   * EFFECTS: update byte array with unsigned int value of 4 bytes
   * <p>
   * @param b the array to put the 4 bytes.
   * @param s the start position in the array.
   * @param val the value to store in the array.
   */
  public static void setInt(byte b[], int s, int val)
  {
    setUByte(b,s+3,val&0xFF);
    setUByte(b,s+2,(val&0xFF00)>>8);
    setUByte(b,s+1,(val&0xFF0000)>>16);
    setUByte(b,s,(val&0xFF000000)>>24);
  }

  private int ProcessCell(byte buf[], int offset)
  {
    // TODO: Figure out if we need to swap...
    int curCell = convInt(buf, offset);
    duration = convLong(buf, offset+24);
    cellStart = convLong(buf, offset+32);
    int discont = convInt(buf, offset+48);
    if (DEBUG_MINIDVD) System.out.println("DVD entering cell "+curCell);
    if (DEBUG_MINIDVD) System.out.println("PGC length "+duration);
    if (DEBUG_MINIDVD) System.out.println("discont detected "+discont);
    // For now disable that because NAV packet handling depends on cells flush.
    updateSTC = (discont!=0);
    return discont;
  }

  private void ProcessAudioChannel(int channel)
  {
    // TODO: Figure out if we need to swap...
    if (DEBUG_MINIDVD) System.out.println("audio channel: "+ channel);
    DVDStream(ptr, 0, channel);
  }

  private void ProcessPicChannel(int channel)
  {
    if (DEBUG_MINIDVD) System.out.println("sub channel: "+ channel);
    // MVP doesn't support forced subs
    if(!hdMediaExtender)
    {
      if((channel&0x40)!=0 || reader.getDVDDomain()!=4)
      {
        DVDStream(ptr, 1, channel&0x1F);
      }
      else
      {
        DVDStream(ptr, 1, 62);
      }
    }
    else
    {
      DVDStream(ptr, 1, channel);
    }
  }

  private void ProcessNAVPacket(byte buf[], int offset)
  {
    if (DEBUG_MINIDVD) System.out.println("NAV Packet at "+offset);
    if(!reader.parseNav(buf, offset, temp_pci, temp_dsi))
    {
      if (DEBUG_MINIDVD) System.out.println("Warning, was expecting nav packet at NAV");
    }
    else
    {
      synchronized (pcidsiQueue)
      {
        int currHigh = temp_pci.hli.hl_gi.hli_ss.get();
        if ((currHigh==2 || currHigh==3) && !pcidsiQueue.isEmpty()) // Use previous highlight
        {
          NAVTracker prevNav = (NAVTracker) pcidsiQueue.get(pcidsiQueue.size() - 1);
          sage.dvd.pci_t prevpci = prevNav.pci;
          temp_pci.hli.hl_gi.hli_ss.set(prevpci.hli.hl_gi.hli_ss.get());
          temp_pci.hli.hl_gi.hli_s_ptm.set(prevpci.hli.hl_gi.hli_s_ptm.get());
          temp_pci.hli.hl_gi.hli_e_ptm.set(prevpci.hli.hl_gi.hli_e_ptm.get());

          temp_pci.hli.hl_gi.btn_se_e_ptm.set(prevpci.hli.hl_gi.btn_se_e_ptm.get());
          temp_pci.hli.hl_gi.btngr_ns.set(prevpci.hli.hl_gi.btngr_ns.get());

          temp_pci.hli.hl_gi.btngr1_dsp_ty.set(prevpci.hli.hl_gi.btngr1_dsp_ty.get());
          temp_pci.hli.hl_gi.btngr2_dsp_ty.set(prevpci.hli.hl_gi.btngr2_dsp_ty.get());
          temp_pci.hli.hl_gi.btngr3_dsp_ty.set(prevpci.hli.hl_gi.btngr3_dsp_ty.get());
          temp_pci.hli.hl_gi.btn_ofn.set(prevpci.hli.hl_gi.btn_ofn.get());
          temp_pci.hli.hl_gi.btn_ns.set(prevpci.hli.hl_gi.btn_ns.get());
          temp_pci.hli.hl_gi.nsl_btn_ns.set(prevpci.hli.hl_gi.nsl_btn_ns.get());
          temp_pci.hli.hl_gi.fosl_btnn.set(prevpci.hli.hl_gi.fosl_btnn.get());
          temp_pci.hli.hl_gi.foac_btnn.set(prevpci.hli.hl_gi.foac_btnn.get());

          for (int j = 0; j < 6; j++)
            temp_pci.hli.btn_colit.btn_coli[j].set(
                prevpci.hli.btn_colit.btn_coli[j].get());
          for(int j=0;j<36;j++)
          {
            temp_pci.hli.btnit[j].btn_coln.set(prevpci.hli.btnit[j].btn_coln.get());
            temp_pci.hli.btnit[j].x_start.set(prevpci.hli.btnit[j].x_start.get());
            temp_pci.hli.btnit[j].x_end.set(prevpci.hli.btnit[j].x_end.get());
            temp_pci.hli.btnit[j].auto_action_mode.set(
                prevpci.hli.btnit[j].auto_action_mode.get());
            temp_pci.hli.btnit[j].y_start.set(prevpci.hli.btnit[j].y_start.get());
            temp_pci.hli.btnit[j].y_end.set(prevpci.hli.btnit[j].y_end.get());
            temp_pci.hli.btnit[j].up.set(prevpci.hli.btnit[j].up.get());
            temp_pci.hli.btnit[j].down.set(prevpci.hli.btnit[j].down.get());
            temp_pci.hli.btnit[j].left.set(prevpci.hli.btnit[j].left.get());
            temp_pci.hli.btnit[j].right.set(prevpci.hli.btnit[j].right.get());
            if (currHigh == 2)
            {
              for (int k = 0; k < 8; k++)
                temp_pci.hli.btnit[j].cmd.bytes[k].set(
                    prevpci.hli.btnit[j].cmd.bytes[k].get());
            }
          }
        }
        if(neednewcell)
        {
          long ptsOffset=(cellStart+temp_pci.pci_gi.e_eltm.toPTS()-temp_pci.pci_gi.vobu_s_ptm.get())/2;
          System.out.println("Sending new Cell with offset " + ptsOffset +
              " ("+ cellStart + ", " + temp_pci.pci_gi.e_eltm.toPTS() + ", " + temp_pci.pci_gi.vobu_s_ptm.get() + ")");
          byte [] ptsoff = new byte[4];
          setInt(ptsoff, 0, (int) ptsOffset);
          NewCell0(ptr, 4, ptsoff);
          neednewcell=false;
        }
        pcidsiQueue.add(new NAVTracker(temp_pci, temp_dsi, cellStart, duration));
        // Clear the queue up to where playback is at
        getCurrNAVTracker();
      }
      /*			System.out.println("MINE Parsed DSI DSI_GI_TIME=" + temp_dsi.dsi_gi.c_eltm.toPTS()/90 +
                               " SML_PTM=" + temp_dsi.sml_pbi.vob_v_s_ptm.get()/90 +
                               " SML_PTM_END=" + temp_dsi.sml_pbi.vob_v_e_ptm.get()/90 +
                               " queueSize=" + pcidsiQueue.size() +
                               " cellDur=" + duration/90 +
                               " cellStart=" + cellStart/90 +
                               " startPts=" + temp_pci.pci_gi.vobu_s_ptm.get()/90 +
                               " endPTS=" + temp_pci.pci_gi.vobu_e_ptm.get()/90 +
                               " pciPTS=" + temp_pci.pci_gi.e_eltm.toPTS()/90);*/
      if (updateSTC)
      {
        updateSTC = false;
        DVDSTC(ptr, cellStart+temp_pci.pci_gi.e_eltm.toPTS());//temp_pci.pci_gi.vobu_s_ptm.get());
      }
      // Don't re-allocate if we're not going to put it in the queue.
      // NARFLEX 8/1/08 - use a pool for this since its' LOTS of reallocations for the nested objects
      synchronized (pcidsiQueue)
      {
        if (pciPool.isEmpty())
          temp_pci = new sage.dvd.pci_t();
        else
          temp_pci = (sage.dvd.pci_t) pciPool.remove(0);
        if (dsiPool.isEmpty())
          temp_dsi = new sage.dvd.dsi_t();
        else
          temp_dsi = (sage.dvd.dsi_t) dsiPool.remove(0);
      }
    }
  }

  private void ProcessCLUT(byte buf[], int offset)
  {
    // TODO: Figure out if we need to swap...
    byte [] clut = new byte[4*16];
    for(int i=0;i<64;i++) clut[i]=buf[offset+i];
    CLUT0(ptr, 16*4, clut);
  }

  private void putInt(byte buf[], int offset, int value)
  {
    buf[offset+0]=(byte) ((value>>24)&0xFF);
    buf[offset+1]=(byte) ((value>>16)&0xFF);
    buf[offset+2]=(byte) ((value>>8)&0xFF);
    buf[offset+3]=(byte) ((value>>0)&0xFF);
  }

  private void ProcessHighlight(byte buf[], int offset, int button)
  {
    theButton = button;
    int absbutton;
    int type;
    int group=0;
    int colortable;
    if (DEBUG_MINIDVD) System.out.println("Need to highlight "+button);
    byte [] highlight = new byte[20];
    NAVTracker currTracker = getCurrNAVTracker();
    if(currTracker==null) return;
    sage.dvd.pci_t pci = currTracker.pci;
    if (DEBUG_MINIDVD) System.out.println("pci: "+pci);
    // TODO: what happens on 4:3 DVDs displayed on a 16:9 monitor??
    if(VideoFrame.getVideoFrameForPlayer(this).getDisplayAspectRatio() > 1.40)
    {
      type=0x1;
    }
    else
    {
      if((reader.get_video_scale_permission()&1)==0)
      {
        type=0x2;
      }
      else
      {
        type=0x4;
      }
    }
    lastCurrentTracker = currTracker;
    highlightOn = lastCurrentTracker.pci.hli.hl_gi.hli_ss.get() != 0;
    if (!highlightOn || button==0)
    {
      putInt(highlight, 0, 0);
      putInt(highlight, 4, 0);
      putInt(highlight, 8, 0);
      putInt(highlight, 12, 0);
      putInt(highlight, 16, 0);
      SPUCTRL(ptr, 20, highlight);
      return;
    }
    if (DEBUG_MINIDVD) System.out.println("highlight has "+pci.hli.hl_gi.btngr_ns.get() +" groups: "+
        pci.hli.hl_gi.btngr1_dsp_ty.get()+" "+
        pci.hli.hl_gi.btngr2_dsp_ty.get()+" "+
        pci.hli.hl_gi.btngr3_dsp_ty.get());
    if(pci.hli.hl_gi.btngr_ns.get()==0) return;

    // Figure out the absolute button based on group...
    for(int i=0; i<pci.hli.hl_gi.btngr_ns.get(); i++)
    {
      if(i==0)
        if((pci.hli.hl_gi.btngr1_dsp_ty.get()&type)!=0)
          group=0;
      if(i==1)
        if((pci.hli.hl_gi.btngr2_dsp_ty.get()&type)!=0)
          group=1;
      if(i==2)
        if((pci.hli.hl_gi.btngr3_dsp_ty.get()&type)!=0)
          group=2;
    }
    absbutton=button-1+group*36/pci.hli.hl_gi.btngr_ns.get(); //+pci.hli.hl_gi.btn_ofn.get()
    if (DEBUG_MINIDVD) System.out.println("Button "+ button +" is at location "+absbutton + " data: "+
        pci.hli.btnit[absbutton]);

    putInt(highlight, 0, pci.hli.btnit[absbutton].x_start.get());
    putInt(highlight, 4, pci.hli.btnit[absbutton].x_end.get());
    putInt(highlight, 8, pci.hli.btnit[absbutton].y_start.get());
    putInt(highlight, 12, pci.hli.btnit[absbutton].y_end.get());
    colortable=pci.hli.btnit[absbutton].btn_coln.get();
    if(colortable!=0)
    {
      putInt(highlight, 16, (int)pci.hli.btn_colit.btn_coli[
                                                            (colortable-1)*2].get());
    }
    else
    {
      putInt(highlight, 16, 0);
    }
    SPUCTRL(ptr, 20, highlight);
  }

  private int getNavButton(int direction)
  {
    NAVTracker currTracker = getCurrNAVTracker();
    if(currTracker==null) return 1;
    sage.dvd.pci_t pci = currTracker.pci;
    if(reader.player_button>=1)
    {
      switch(direction)
      {
        case 1:
          return pci.hli.btnit[reader.player_button-1].up.get();
        case 2:
          return pci.hli.btnit[reader.player_button-1].right.get();
        case 3:
          return pci.hli.btnit[reader.player_button-1].down.get();
        case 4:
          return pci.hli.btnit[reader.player_button-1].left.get();
      }
    }
    return 1;
  }
  protected void createPushThread()
  {
    if (Sage.DBG) System.out.println("Creating new push thread");
    pushThread = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          VideoFrame myVF = VideoFrame.getVideoFrameForPlayer(MiniDVDPlayer.this);
          int bufferSize = 32768;
          String pushDumpFileName = uiMgr.get("miniclient/push_dump_debug_file", "");
          java.io.OutputStream pushDumpStream = null;
          if (pushDumpFileName.length() > 0)
          {
            int dumpFileIdx = 0;
            while (new java.io.File(pushDumpFileName + "-" + dumpFileIdx + ".mpg").isFile())
              dumpFileIdx++;
            try
            {
              pushDumpStream = new java.io.BufferedOutputStream(
                  new java.io.FileOutputStream(
                      pushDumpFileName + "-" + dumpFileIdx + ".mpg"));
            }
            catch (java.io.IOException e)
            {
              System.out.println("ERROR creating push dump debug file:" +e );
            }
          }
          byte[] javaBuff = new byte[bufferSize];
          java.nio.ByteBuffer nioBuff = java.nio.ByteBuffer.wrap(javaBuff);
          boolean kickVF = false;
          float lastPlayRate = 1;
          sage.dvd.VM myReader = reader;
          int curpos=0;
          while ( myReader == reader
              && (currState == PAUSE_STATE || currState == PLAY_STATE))
          {
            if (kickVF)
            {
              VideoFrame.getVideoFrameForPlayer(MiniDVDPlayer.this).kick();
              kickVF = false;
              continue;
            }
            synchronized (decoderLock)
            {
              if (shouldYieldDecoderLock())
              {
                try
                {
                  decoderLock.wait(20);
                }
                catch (InterruptedException e)
                {}
              }
              if(myReader != reader)
                break;
              if(debugPush) System.out.println("SDPushLoop");
              if(currState == PAUSE_STATE)
              {
                if(debugPush) System.out.println("Waiting in paused state");
                try{decoderLock.wait(100);}catch(Exception e){}
                continue;
              }
              // Process any highlight changes if we need to
              NAVTracker currTracker = getCurrNAVTracker();
              if (currTracker != lastCurrentTracker && currTracker != null)
              {
                lastCurrentTracker = currTracker;
                boolean newHighlightOn = lastCurrentTracker.pci.hli.hl_gi.hli_ss.get() != 0;
                if (newHighlightOn || highlightOn != newHighlightOn)
                {
                  ProcessHighlight(null, 0, theButton);
                }
              }
              if (freeSpace < 32768 || dvdEos)
              {
                if (!pushBuffer0(ptr, null, dvdEos ? (0x80 | getFlags()) : getFlags()))
                {
                  if (Sage.DBG) System.out.println("push loop terminating because pushBuffer failed");
                  break;
                }
                if(needToPlay)
                {
                  playPush0(ptr);
                  needToPlay = false;
                }
              }
              int availBufferSize = freeSpace;
              // If we're doing transcoding and the client's buffer is full then wait to send more until
              // they have a complete buffer to receive from us.
              if (availBufferSize < 32768)
              {
                if (debugPush)
                  System.out.println("waiting for buffer ");
                if (dvdEos && !eos && freeSpace == -1)
                {
                  if (Sage.DBG) System.out.println("DVD EOS detected on server and client, set EOS flag!");
                  eos = true;
                  kickVF=true;
                }
                try{decoderLock.wait(50);}catch(Exception e){}
                continue;
              }
              if (debugPush) System.out.println("buffer size avail=" +
                  availBufferSize + " using=" +
                  Math.min(javaBuff.length, availBufferSize));
              availBufferSize = Math.min(javaBuff.length, availBufferSize);
              int readBufferSize = availBufferSize;
              {
                int retcode;
                int cmdpos=curpos;
                if(availBufferSize<32768) continue;
                retcode = reader.process(javaBuff, curpos);
                if((retcode>>16)==DVDReader.DVD_PROCESS_DATA) curpos += (retcode&0xFFFF);
                if(curpos>0 && ((retcode>>16)!=DVDReader.DVD_PROCESS_DATA || curpos >=32768))
                {
                  readBufferSize = curpos;
                  lastPlayRate = myRate;
                  int flags = getFlags();
                  if (debugPush) System.out.println("sending data len: "+readBufferSize);
                  nioBuff.clear().limit(readBufferSize);
                  if (!pushBuffer0(ptr, nioBuff, flags))
                  {
                    if (Sage.DBG) System.out.println("push loop terminating because pushBuffer failed");
                    break;
                  }
                  if (debugPush) System.out.println("done sending data of len: "+readBufferSize);
                  if (pushDumpStream != null)
                  {
                    try
                    {
                      pushDumpStream.write(javaBuff, 0, readBufferSize);
                    }catch (Exception e)
                    {
                      System.out.println("ERROR writing push buffer dump stream: " + e);
                    }
                  }
                  numPushedBuffers++;

                  // TODO: finish this...
                  if (numPushedBuffers >= 8 && needToPlay)
                  {
                    playPush0(ptr);
                    needToPlay = false;
                  }
                  curpos=0;
                }
                if(retcode==-1)
                {  // We have reached the end of the DVD
                  if (Sage.DBG) System.out.println("Reached end of DVD");
                  dvdEos=true;
                  kickVF=true;
                  try { Thread.sleep(500); } catch(Exception e) {}
                }
                switch(retcode>>16)
                {
                  case DVDReader.DVD_PROCESS_DATA:
                    pausetime=0;
                    break;
                  case DVDReader.DVD_PROCESS_PAUSE:
                    if (DEBUG_MINIDVD) System.out.println("Need to pause for "+ (retcode&0xFF) + " seconds");
                    if(pausetime==0) // new pause
                    {
                      if (!pushBuffer0(ptr, null, 0x100))
                      {
                        if (Sage.DBG) System.out.println("push loop terminating because pushBuffer failed");
                        break;
                      }
                      if(freeSpace==-2)
                      {
                        if (DEBUG_MINIDVD) System.out.println("Entering pause for "+ (retcode&0xFF) + " seconds");
                        duration=(retcode&0xFF)*90000;
                        if((retcode&0xFF)!=255)
                        {
                          pausetime=retcode&0xFF;
                          pausestart=Sage.eventTime();
                        }
                      }
                    }
                    else
                    {
                      if(pausetime!=0xFF)
                      {
                        if(pausestart+pausetime*1000 < Sage.eventTime())
                        {
                          reader.unpause();
                          pausetime=0;
                        }
                      }
                    }
                    try { Thread.sleep(200); } catch(Exception e) {}
                    continue;
                  case DVDReader.DVD_PROCESS_EMPTY:
                    if (!pushBuffer0(ptr, null, 0x100))
                    {
                      if (Sage.DBG) System.out.println("push loop terminating because pushBuffer failed");
                      break;
                    }
                    if(freeSpace==-2)
                    {
                      if(needclear)
                      {
                        flushPush0(ptr); // Needed on EM8620 to reset things...
                        synchronized (pcidsiQueue)
                        {
                          pcidsiQueue.clear();
                        }
                        neednewcell=true;
                        if((myReader.get_video_scale_permission()&1)==0)
                        {
                          DVDFormat0(ptr, 0); // letterbox
                        }
                        else if ((myReader.get_video_scale_permission()&2)==0)
                        {
                          DVDFormat0(ptr, 1); // pan and scan
                        }
                        else
                        {
                          if (DEBUG_MINIDVD) System.out.println("This dvd can't be watched on 4:3???");
                          DVDFormat0(ptr, 2); // Fill
                        }
                        myVF.playbackControl(0);
                        reader.unempty();
                        needclear=false;
                      }
                      else
                      {
                        reader.unempty();
                      }
                    }
                    else
                      try { Thread.sleep(200); } catch(Exception e) {}
                    continue;
                  case DVDReader.DVD_PROCESS_FLUSH:
                    flushPush0(ptr);
                    updateSTC=true;
                    continue;
                  case DVDReader.DVD_PROCESS_CELL:
                    needclear = ProcessCell(javaBuff, cmdpos)!=0;
                    /*while(updateSTC)
										{
											if (!pushBuffer0(ptr, null, 0, 0x100))
											{
												if (Sage.DBG) System.out.println("push loop terminating because pushBuffer failed");
												break;
											}
											if(freeSpace==-2)
											{
												flushPush0(ptr); // Needed on EM8620 to reset things...
												break;
											}
											try
											{
												//decoderLock.wait(15);
												try { Thread.sleep(100); } catch(Exception e) {}
											}catch (Exception e){}
										}*/
                    // TODO: handle queue in a way that doesn't depend on cell change
                    // this is more complicated than it looks because the time ranges
                    // can overlap on two following cells...
                    if(needclear==false)
                    {
                      neednewcell=true;
                      if((myReader.get_video_scale_permission()&1)==0)
                      {
                        DVDFormat0(ptr, 0); // letterbox
                      }
                      else if ((myReader.get_video_scale_permission()&2)==0)
                      {
                        DVDFormat0(ptr, 1); // pan and scan
                      }
                      else
                      {
                        if (DEBUG_MINIDVD) System.out.println("This dvd can't be watched on 4:3???");
                        DVDFormat0(ptr, 2); // Fill
                      }
                      myVF.playbackControl(0);
                    }
                    continue;
                  case DVDReader.DVD_PROCESS_AUDIOCHANNEL:
                    myVF.playbackControl(0);
                    ProcessAudioChannel(retcode&0xFFFF);
                    continue;
                  case DVDReader.DVD_PROCESS_PICCHANNEL:
                    myVF.playbackControl(0);
                    ProcessPicChannel(retcode&0xFF);
                    continue;
                  case DVDReader.DVD_PROCESS_NAV:
                    ProcessNAVPacket(javaBuff, cmdpos);
                    continue;
                  case DVDReader.DVD_PROCESS_CLUT:
                    ProcessCLUT(javaBuff, cmdpos);
                    continue;
                  case DVDReader.DVD_PROCESS_HIGHLIGHT:
                    ProcessHighlight(javaBuff, cmdpos, retcode&0xFFFF);
                    continue;
                  case DVDReader.DVD_PROCESS_RATE:
                    myRate=1.0f;
                    continue;
                  case DVDReader.DVD_PROCESS_VTS:
                  default:
                    if (DEBUG_MINIDVD) System.out.println("process code "+(retcode>>16));
                    readBufferSize=0;
                }
              }
            }
          }
          if (pushDumpStream != null)
          {
            try
            {
              pushDumpStream.close();
            }
            catch (Exception e){}
            pushDumpStream = null;
          }
        }
        catch (Throwable t)
        {
          if (Sage.DBG)
          {
            System.out.println("MiniDVDPlayer Exception:" + t);
            Sage.printStackTrace(t);
          }
        }
      }

      private int getFlags()
      {
        int flags = 0;
        if (firstPush)
        {
          // Stop discarding PTS and stop trick mode
          flags = 0x12;
          firstPush = false;
        }
        else
        {
          if (myRate > 1.0f || myRate < 0)
          {
            // Discard PB Frames & PTS's
            int irate=(int)(myRate*32.0f);
            irate&=0x7FFF; // we support 10.5 format
            flags = 0x09 | (irate<<16);
            sentDiscardPtsFlag = true;
            sentTrickmodeFlag = true;
            //if (myRate < 0)
            //	wasReversePlay = true;
          }
          else
          {
            if (sentDiscardPtsFlag)
            {
              sentDiscardPtsFlag = false;
              flags |= 0x10;
            }
            if (sentTrickmodeFlag)
            {
              sentTrickmodeFlag = false;
              flags |= 0x02;
            }
          }
        }
        return flags;
      }
      private boolean firstPush = true;
    }, "Pusher");
    pushThread.setDaemon(true);
    pushThread.setPriority(Thread.MAX_PRIORITY - Sage.getInt("push_thread_priority_offset", 2));
    pushThread.start();
  }

  /******************************************\
   * Functions that interact with miniclients *
\******************************************/

  protected int readIntReply() throws java.io.IOException
  {
    sockBuf.clear().limit(4);
    try
    {
      TimeoutHandler.registerTimeout(timeout, clientSocket);
      do{
        int x = clientSocket.read(sockBuf);
        if (x < 0)
          throw new java.io.EOFException();
      }while(sockBuf.remaining() > 0);
    }
    finally
    {
      TimeoutHandler.clearTimeout(clientSocket);
    }
    sockBuf.flip();
    return sockBuf.getInt();
  }
  protected long initDriver0(int videoFormat)
  {
    if (Sage.DBG) System.out.println("initDriver0()");

    clientSocket = ((MiniClientSageRenderer)uiMgr.getRootPanel().getRenderEngine()).getPlayerSocketChannel();

    String clientName = uiMgr.getLocalUIClientName();
    if (clientSocket == null) return 0;
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_INIT<<24 | 4 );
      sockBuf.putInt(videoFormat);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      if (Sage.DBG) System.out.println("MiniPlayer established for " + clientName);
      return readIntReply()!=0 ? 1 : 0;
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("Error w/ MiniPlayer socket of:" + e);
      return 0;
    }
  }

  protected boolean openURL0(long ptr, String url)
  {
    if (Sage.DBG) System.out.println("openURL0()");
    try
    {
      byte []b = url.getBytes(Sage.I18N_CHARSET);
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_OPENURL<<24 | b.length+1+4);
      sockBuf.putInt(b.length+1);
      sockBuf.put(b, 0, b.length);
      sockBuf.put((byte)0);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      return readIntReply()!=0;
    }
    catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return false;
  }

  protected long getMediaTimeMillis0(long ptr)
  {
    if (Sage.eventTime() - lastMediaTimeCacheTime < 100 || clientSocket == null)
      return lastMediaTime;
    addYieldDecoderLock();
    synchronized (decoderLock)
    {
      try
      {
        sockBuf.clear();
        sockBuf.putInt(MEDIACMD_GETMEDIATIME<<24 | 0);
        sockBuf.flip();
        while (sockBuf.hasRemaining())
          clientSocket.write(sockBuf);
        long currMediaTime = readIntReply();
        if (lastMediaTimeBase == currMediaTime && currState == PLAY_STATE)
        {
          lastMediaTime = (Sage.eventTime() - lastMediaTimeCacheTime) + lastMediaTime;
        }
        else
          lastMediaTime = lastMediaTimeBase = currMediaTime;
        lastMediaTimeCacheTime = Sage.eventTime();
        removeYieldDecoderLock();
        decoderLock.notifyAll();
        return lastMediaTime;
      }catch(Exception e)
      {
        System.out.println(e);
        e.printStackTrace();
      }
      removeYieldDecoderLock();
      decoderLock.notifyAll();
    }
    return lastMediaTime;   // otherwise if a connection is killed while
    // viewing we won't be able to track the watched
    //time in it (this used to return 0 here)
  }

  protected boolean closeDriver0(long ptr)
  {
    if (Sage.DBG) System.out.println("closeDriver0()");
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_DEINIT<<24 | 0);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      //clientInStream.readInt(); // Why do we care?? This just makes it take longer to close.
      return true;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    finally
    {
      try
      {
        clientSocket.close();
      }catch (Exception e){}
      clientSocket = null;
    }
    return false;
  }

  protected boolean setMute0(long ptr, boolean x)
  {
    if (clientSocket == null) return false;
    if (Sage.DBG) System.out.println("setMute0()");
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_SETMUTE<<24 | 4 );
      sockBuf.putInt(x ? 1 : 0);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      return readIntReply()!=0;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return false;
  }

  protected boolean stopPush0(long ptr)
  {
    if (Sage.DBG) System.out.println("stopPush0()");
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_STOP<<24 | 0 );
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      return readIntReply()!=0;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return false;
  }

  protected boolean pausePush0(long ptr)
  {
    if (Sage.DBG) System.out.println("pausePush0()");
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_PAUSE<<24 | 0 );
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      return readIntReply()!=0;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return false;
  }

  protected boolean playPush0(long ptr)
  {
    if (Sage.DBG) System.out.println("playPush0()");
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_PLAY<<24 | 0 );
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      return readIntReply()!=0;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return false;
  }

  protected boolean flushPush0(long ptr)
  {
    if (DEBUG_MINIDVD) System.out.println("flushPush0()");
    lastParserTimestamp = 0;
    lastParserTimestampBytePos = 0;
    if (true) // always on mvp?
    {
      try
      {
        sockBuf.clear();
        sockBuf.putInt(MEDIACMD_FLUSH<<24 | 0 );
        sockBuf.flip();
        while (sockBuf.hasRemaining())
          clientSocket.write(sockBuf);
        boolean rv = readIntReply()!=0;
        if (currMute)
        {
          // Flushing the decoder resets the mute state on the MVP
          setMute0(ptr, currMute);
        }
      }catch(Exception e)
      {
        System.out.println(e);
        e.printStackTrace();
      }
    }
    return false;
  }

  protected boolean pushBuffer0(long ptr, java.nio.ByteBuffer buf, int flags)
  {
    if (clientSocket == null) return false;
    try
    {
      sockBuf.clear();
      int size = (buf == null ? 0 : buf.remaining());
      sockBuf.putInt(MEDIACMD_PUSHBUFFER<<24 |
          (size+(8)));
      sockBuf.putInt(size);
      sockBuf.putInt(flags);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      long currParserTimestamp = 0; // TODO: Figure out DVD timestamp
      while(buf!=null && buf.hasRemaining())
        clientSocket.write(buf);
      freeSpace = readIntReply();
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      killUI();
    }
    return false;
  }

  protected java.awt.Dimension getVideoDimensions0(long ptr)
  {
    return new java.awt.Dimension(720,480);
  }

  private void killUI()
  {
    // We have to do this on another thread to prevent deadlocking
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        uiMgr.goodbye();
      }
    });
  }

  protected boolean setVideoRectangles0(long ptr,
      java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect)
  {
    if(clientSocket != null)
    {
      try
      {
        sockBuf.clear();
        sockBuf.putInt(MEDIACMD_SETVIDEORECT<<24 | 8*4 );
        sockBuf.putInt(videoSrcRect.x);
        sockBuf.putInt(videoSrcRect.y);
        sockBuf.putInt(videoSrcRect.width);
        sockBuf.putInt(videoSrcRect.height);
        sockBuf.putInt(videoDestRect.x);
        sockBuf.putInt(videoDestRect.y);
        sockBuf.putInt(videoDestRect.width);
        sockBuf.putInt(videoDestRect.height);
        sockBuf.flip();
        while (sockBuf.hasRemaining())
          clientSocket.write(sockBuf);
        readIntReply();
        return true;
      }catch(Exception e)
      {
        if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
        e.printStackTrace();
        killUI();
      }
    }
    return false;
  }

  protected float getVolume0()
  {
    if (clientSocket == null) return 0;
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_GETVOLUME<<24 | 0 );
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      return readIntReply()/65535.0f;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return 0.0f;
  }

  protected float setVolume0(float volume)
  {
    if (clientSocket == null) return 0;
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_SETVOLUME<<24 | 4 );
      sockBuf.putInt((int)(volume*65535));
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      return readIntReply()/65535.0f;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return 0.0f;
  }

  protected boolean frameStep0(int amount)
  {
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_FRAMESTEP<<24 | 4 );
      sockBuf.putInt(amount);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      readIntReply();
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return true;
  }

  protected boolean NewCell0(long ptr, int size, byte[] buf)
  {
    try
    {
      int cellStatus;
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_DVD_NEWCELL<<24 |
          (size+(4)));
      sockBuf.putInt(size);
      if(buf!=null)
        sockBuf.put(buf, 0, size);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      cellStatus = readIntReply();
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      killUI();
    }
    return false;
  }

  protected boolean CLUT0(long ptr, int size, byte[] buf)
  {
    try
    {
      int spuStatus;
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_DVD_CLUT<<24 |
          (size+(4)));
      sockBuf.putInt(size);
      if(buf!=null)
        sockBuf.put(buf, 0, size);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      spuStatus = readIntReply();
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      killUI();
    }
    return false;
  }

  protected boolean SPUCTRL(long ptr, int size, byte[] buf)
  {
    try
    {
      int spuStatus;
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_DVD_SPUCTRL<<24 |
          (size+(4)));
      sockBuf.putInt(size);
      if(buf!=null)
        sockBuf.put(buf, 0, size);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      spuStatus = readIntReply();
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      killUI();
    }
    return false;
  }

  protected boolean DVDSTC(long ptr, long stc)
  {
    try
    {
      int stcStatus;
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_DVD_STC<<24 |
          (4));
      sockBuf.putInt((int)(stc/2));
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      stcStatus = readIntReply();
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      killUI();
    }
    return false;
  }

  protected boolean DVDStream(long ptr, int type, int stream)
  {
    try
    {
      int streamStatus;
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_DVD_STREAM<<24 |
          (8));
      sockBuf.putInt(type);
      sockBuf.putInt(stream);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      streamStatus = readIntReply();
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      killUI();
    }
    return false;
  }

  protected boolean DVDFormat0(long ptr, int format)
  {
    // Always send if we're on a menu; otherwise only send if we're a different title than last time
    if (reader != null && reader.getDVDDomain() == 4 && lastTitleForFormat == reader.getDVDTitle())
      return true; // abort the redundant format setting
    if (reader != null && reader.getDVDDomain() == 4)
      lastTitleForFormat = reader.getDVDTitle();
    //if (reader.getDVDDomain() == 4 && format == 2)
    //	format = 0; // Only use Fill for menus for alignment
    try
    {
      int formatStatus;
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_DVD_FORMAT<<24 |
          (4));
      sockBuf.putInt(format);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      formatStatus = readIntReply();
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      killUI();
    }
    return false;
  }

  public float getCurrentAspectRatio()
  {
    return 0;
  }

  private void addYieldDecoderLock()
  {
    synchronized (yieldDecoderLockCountLock)
    {
      yieldDecoderLockCount++;
    }
  }

  private void removeYieldDecoderLock()
  {
    synchronized (yieldDecoderLockCountLock)
    {
      yieldDecoderLockCount--;
    }
  }

  private boolean shouldYieldDecoderLock()
  {
    synchronized (yieldDecoderLockCountLock)
    {
      return yieldDecoderLockCount > 0;
    }
  }

  private java.nio.channels.SocketChannel clientSocket;
  private long timeout = Sage.getInt("ui/remote_player_connection_timeout", 30000);
  private java.nio.ByteBuffer sockBuf = java.nio.ByteBuffer.allocateDirect(8192);

  protected long ptr;
  protected UIManager uiMgr;

  private long duration;
  private long cellStart;

  private long lastMediaTime;
  private long lastMediaTimeBase;
  private long lastMediaTimeCacheTime;
  private long lastParserTimestamp;
  private long lastParserTimestampBytePos;

  private boolean hdMediaExtender;

  protected volatile int currState;
  protected volatile java.io.File currFile;
  protected boolean eos;
  protected boolean dvdEos;
  protected Object decoderLock = new Object();
  private float myRate;
  protected int freeSpace = 0;
  protected boolean currMute = false;
  protected float curVolume = 1.0f;
  protected int pausetime = 0;
  protected long pausestart = 0;
  private sage.dvd.VM reader;
  protected Thread pushThread;

  private boolean needToPlay;
  int numPushedBuffers = 0;
  final boolean debugPush = Sage.getBoolean("miniclient/debug_push", false);
  boolean sentDiscardPtsFlag = false;
  boolean sentTrickmodeFlag = false;

  sage.dvd.pci_t temp_pci;
  sage.dvd.dsi_t temp_dsi;
  java.util.ArrayList pcidsiQueue;
  java.util.ArrayList pciPool;
  java.util.ArrayList dsiPool;
  // I know this isn't perfect, but it's a quick and easy way to allow yielding on the decoder
  // lock only when necessary. It'll yield in a fair amount of other cases as well though.
  private int yieldDecoderLockCount;
  private Object yieldDecoderLockCountLock = new Object();

  private boolean updateSTC;
  private boolean needclear;
  private boolean neednewcell;

  private int theButton;
  private NAVTracker lastCurrentTracker;
  private boolean highlightOn;

  protected long timeGuessMillis;
  protected long guessTimestamp;
  protected int lastTitleForFormat = -1;

  protected java.io.File unmountRequired;

  protected boolean supportsFrameStep;

  private static class NAVTracker
  {
    public NAVTracker(sage.dvd.pci_t inPCI, sage.dvd.dsi_t inDSI, long inCellStart, long inCellDur)
    {
      pci = inPCI;
      dsi = inDSI;
      cellStart = inCellStart;
      cellDur = inCellDur;
      navstart = cellStart + pci.pci_gi.e_eltm.toPTS();
      navend = cellStart + pci.pci_gi.e_eltm.toPTS()
          + pci.pci_gi.vobu_e_ptm.get() - pci.pci_gi.vobu_s_ptm.get();
    }
    public sage.dvd.pci_t pci;
    public sage.dvd.dsi_t dsi;
    public long cellStart;
    public long cellDur;
    public long navstart;
    public long navend;
  }
}
