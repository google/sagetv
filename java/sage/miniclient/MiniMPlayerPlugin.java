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
package sage.miniclient;

public class MiniMPlayerPlugin implements Runnable
{
  private static final float VOLUME_LOG_SHAPING = 30000.0f; // higher numbers mean the louder sound covers a larger range
  /**
   * Indicates the MediaPlayer is in an uninitialized state
   */
  public static final int NO_STATE = 0;
  /**
   * Indicates the MediaPlayer has loaded a file and is ready for playback
   */
  public static final int LOADED_STATE = 1;
  /**
   * The MediaPlayer is playing
   */
  public static final int PLAY_STATE = 2;
  /**
   * The MediaPlayer is paused
   */
  public static final int PAUSE_STATE = 3;
  /**
   * The MediaPlayer is stopped
   */
  public static final int STOPPED_STATE = 4;
  /**
   * The MediaPlayer has encountered an end of stream
   */
  public static final int EOS_STATE = 5;

  /**
   * Disables Closed Captioning
   */
  public static final int CC_DISABLED = 0;
  /**
   * Disables CC1 (Closed Captioning)
   */
  public static final int CC_ENABLED_CAPTION1 = 1;
  /**
   * Disables CC2 (Closed Captioning)
   */
  public static final int CC_ENABLED_CAPTION2 = 2;
  /**
   * Disables Text1 (Closed Captioning)
   */
  public static final int CC_ENABLED_TEXT1 = 3;
  /**
   * Disables Text2 (Closed Captioning)
   */
  public static final int CC_ENABLED_TEXT2 = 4;

  public static final int COLORKEY_VALUE = 0x080010;
  public static final boolean USE_STDIN = false;
  private static Boolean sixteenBitDesktop = null;
  
  /**
   * Using the New MPLAYER build
   */
  static boolean newmplayer=false;
  
  public MiniMPlayerPlugin(GFXCMD2 inTarget, MiniClientConnection myConn)
  {
    this.myConn = myConn;
    alive = true;
    currState = NO_STATE;
    gfxEngine = inTarget;
    
    if (!"true".equals(MiniClient.myProperties.getProperty("opengl", "true")))
    {
      if (MiniClient.WINDOWS_OS)
      {
        target = gfxEngine.getVideoCanvas();
      }
      else
      {
        target = gfxEngine.getGraphicsCanvas();
      }
    }

    if (sixteenBitDesktop == null)
    {
      java.awt.GraphicsDevice[] screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().
          getScreenDevices();
      if (screens.length > 0 && screens[0].getDisplayMode().getBitDepth() == 16)
        sixteenBitDesktop = Boolean.TRUE;
      else
        sixteenBitDesktop = Boolean.FALSE;
    }
  }

  public boolean shouldDeinterlace(byte majorTypeHint, byte minorTypeHint)
  {
    return false;//minorTypeHint == MediaFile.MEDIASUBTYPE_MPEG2_PS;
  }

  public boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file)
  {
    return false; //return isMPlayerRunning() && shouldDeinterlace(majorTypeHint, minorTypeHint) == shouldDeinterlace(currHintMajorType, currHintMinorType);
  }

  public synchronized void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone)
  {
    boolean pauseOnLoad = currState == PAUSE_STATE;
    currState = LOADED_STATE;
    mplayerLoaded = false;
    realDurMillis = 0;
    lastMillis = 0;
    eos = false;
    fileDeactivated = false;
    initialPTSmsec = 0;
    lastCacheRemBytes = 0;
    currState = PLAY_STATE; // it'll be playing at first, but the succesful pause will update this
    sendCommand((pauseOnLoad ? "pausing " : "") + "loadfile2 \"" +
        ((hostname != null && hostname.length() > 0) ? ("stv://" + hostname + "/") : "") +
        file.getPath().replaceAll("\\\\", "\\\\\\\\") + "\" " + (timeshifted ? "1 " : "0 ") + bufferSize);
    //		waitForMPlayerToFinishLoad();
    currHintMajorType = majorTypeHint;
    currHintMinorType = minorTypeHint;
    currHintEncoding = encodingHint;
    //if (pauseOnLoad)
    //			waitToEnterPauseState();
    //	else
    //			waitForPlaybackToPass(0);
  }

  public boolean frameStep(int amount)
  {
    if (alive && pushMode && Thread.currentThread() != cmdThread)
    {
      synchronized (cmdQueue)
      {
        cmdQueue.add(new Object[] {new Integer(MediaCmd.MEDIACMD_FRAMESTEP)});
        cmdQueue.notifyAll();
      }
      return true;
    }

    // Frame stepping will pause it if playing
    if ((currState == PLAY_STATE || currState == PAUSE_STATE) && !eos)
    {
      synchronized (this)
      {
        if (currState == PLAY_STATE)
        {
          sendCommand("pause");
          currState = PAUSE_STATE;
        }
        sendCommand("frame_step");
        return true;
      }
    }
    return false;
  }

  public void free()
  {
    alive = false;
    long startWait = System.currentTimeMillis();
    while (processingMplayerSend && System.currentTimeMillis() - startWait < 3000)
    {
      try{Thread.sleep(100);}catch(Exception e){}
    }
    try
    {
      // to prevent the 'blip' when we shutdown MPlayer while paused.
      if (currState == PAUSE_STATE && MiniClient.WINDOWS_OS && !processingMplayerSend)
        sendCommand("mute");
      currState = NO_STATE;
      System.out.println("Closing down mplayer");
      if (mpStdin != null)
      {
        // Be sure to clear the active file bit in case MPlayer is waiting for more data
        if (!processingMplayerSend)
        {
          inactiveFile();
          stop();
          sendCommand("quit");
          try{
            mpStdin.close();
            mpStdin = null;
          }catch (Exception e){}
        }
      }
      if (mpStdout != null)
      {
        try { mpStdout.close(); } catch (java.io.IOException e) {}
        mpStdout = null;
      }
      if (mpStderr != null)
      {
        try { mpStderr.close(); } catch (java.io.IOException e) {}
        mpStderr = null;
      }
    }
    finally
    {
      if (mpProc != null)
      {
        long killDelay = (MiniClient.MAC_OS_X ? 2000 : 10000);
        startWait = System.currentTimeMillis();
        while (true)
        {
          try
          {
            System.out.println("MPlayer process exit code:" + mpProc.exitValue());
            break;
          }
          catch (IllegalThreadStateException e)
          {
            System.out.println("MPlayer process has not exited yet...");
            try{Thread.sleep(100);}catch(Exception e1){}
            if (System.currentTimeMillis() - startWait > killDelay)
            {
              System.out.println("Forcibly killing MPlayer process!");
              mpProc.destroy();
              break;
            }
          }
        }
        mpProc = null;
      }
    }
    // We may still have this open in order to prevent a lockup before killing MPlayer
    if (mpStdin != null)
    {
      try{
        mpStdin.close();
        mpStdin = null;
      }catch (Exception e){}
    }
  }

  public int getClosedCaptioningState()
  {
    return CC_DISABLED;
  }

  public java.awt.Color getColorKey()
  {
    return new java.awt.Color(COLORKEY_VALUE);
  }

  public long getDurationMillis()
  {
    return realDurMillis;
  }

  public java.io.File getFile()
  {
    return currFile;
  }

  public long getMediaTimeMillis()
  {
    return (eos && realDurMillis > 0) ? realDurMillis : (pendingPushSeek ? 0 : (lastMillis - initialPTSmsec));
  }

  public boolean getMute()
  {
    return muteState;
  }

  public float getPlaybackRate()
  {
    return 1;
  }

  public int getState()
  {
    return eos ? EOS_STATE : currState;
  }

  public java.awt.Dimension getVideoDimensions()
  {
    return videoDimensions;
  }

  public float getVolume()
  {
    return currVolume;
  }

  public void inactiveFile()
  {
    if (alive && pushMode && Thread.currentThread() != cmdThread)
    {
      synchronized (cmdQueue)
      {
        cmdQueue.add(new Object[] {new Integer(-1)});
        cmdQueue.notifyAll();
      }
      return;
    }

    if (!fileDeactivated)
    {
      fileDeactivated = true;
      sendCommand("inactive_file");
    }
  }

  public void setPushMode(boolean x)
  {
    pushMode = x;
  }

  protected int getDesktopColorKey()
  {
    // This accounts for sixteen bit desktop situations
    if (sixteenBitDesktop != null && sixteenBitDesktop.booleanValue())
    {
      java.awt.Color c = getColorKey();
      return ((c.getRed() & 0xF8) << 8) | ((c.getGreen() & 0xFC) << 3) | ((c.getBlue() & 0xF8) >> 3);
    }
    else
      return getColorKey().getRGB() & 0xFFFFFF;
  }

  private static int shmemcounter = 0;
  public void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, String file, String hostname, boolean timeshifted, long bufferSize)
  {
    if (alive && pushMode && Thread.currentThread() != cmdThread)
    {
      synchronized (cmdQueue)
      {
        cmdQueue.add(new Object[] {new Integer(MediaCmd.MEDIACMD_OPENURL), file, hostname, new Boolean(timeshifted) });
        cmdQueue.notifyAll();
      }
      if (cmdThread == null)
      {
        cmdThread = new Thread(this, "PlayerCmdQueue");
        cmdThread.setDaemon(true);
        cmdThread.start();
      }
      return;
    }

    synchronized (this)
    {
      initialPTSmsec = 0;
      currState = NO_STATE;
      mplayerLoaded = false;
      realDurMillis = 0;
      eos = false;
      boolean disablePPInPullMode = MiniClient.myProperties.getProperty("disable_pull_mode_postprocessing", "true").equalsIgnoreCase("true");
      // Launch the mplayer process
      try
      {
        // -slave is for stdin control
        java.util.Vector cmds = new java.util.Vector();
        String cmdOpts1,cmdOpt2;
        // If we don't set 'abs' which is the audio output buffer size to a lower number then MPlayer tries to decode
        // too much audio ahead of where we need to be to do streaming playback.
        cmdOpts1 = /*(pushMode ? "-cache-min 0 " : "") + /*(pushMode ? "-abs 32768 " : "") +*/ "-identify -osdlevel 0 -autosync " +
            (pushMode ? "5" : "30") + " -noconsolecontrols -mc 1 -sid 99"+ // the -sid 99 disables subtitles by default (unless they have 99 sub langs)
            (USE_STDIN ? "" : " -slave") + " " + MiniClient.myProperties.getProperty("extra_mplayer_args", "");
        int streamBufferSize = (pushMode ?
            Integer.parseInt(MiniClient.myProperties.getProperty("push_mode_stream_buffer_size", "8192")) :
              Integer.parseInt(MiniClient.myProperties.getProperty("stream_buffer_size", "65536")));

        int cacheSize = pushMode ? Integer.parseInt(MiniClient.myProperties.getProperty("push_mode_cache_size_kb", "128")) : 2048;
        boolean disableCache = false;
        if (file != null && file.toString().toLowerCase().endsWith(".flv"))
        {
          cacheSize = 512;
          streamBufferSize = 8192;
          disableCache = true;
        }
        
        if (newmplayer) {
        	// cache doesn't work with new mplayer
        	disableCache=true;
        }
        
        cmdOpts1 += " -stream-buffer-size " + streamBufferSize;

        if (MiniClient.WINDOWS_OS)
        {
          if (new java.io.File("SageTVPlayer.exe").isFile())
            cmds.add("SageTVPlayer.exe");
          else if (new java.io.File("mplayer.exe").isFile())
            cmds.add("mplayer.exe");
          else
          {
            System.out.println("Player executable is missing!!!");
            return;
          }
          // If the cache size is too small then when we pause MPlayer it may think it hit an EOS and
          // kill itself. 768 seems like an OK value, 512 was not big enough (testing with 256K streams)
          cmdOpt2 = "-priority abovenormal -cache-seek-min 0 -framedrop"; //
          cmdOpts1 += " -loadmuted -v"; // we want MPlayer to start in a muted state, and we need extra debug to detect overlay failures
          if (!disableCache)
            cmdOpt2 += " -cache " + cacheSize;
          // Use the WaveOut renderer instead of the DSound renderer to avoid audio looping issues
          // BUT we've got volume issues with WaveOut, so go back to DSound since we already came up with
          // a workaround for the looping
          cmdOpt2 += " -ao " + MiniClient.myProperties.getProperty("audio_renderer", "dsound");
          if (gfxEngine instanceof DirectX9GFXCMD)
          {
            final String shmemprefix = "SageTVPS" + (shmemcounter = ((shmemcounter+1)%1024));
            cmdOpt2 += " -vo stvwin:shmemprefix=" + shmemprefix;
            Thread t = new Thread("AsyncMPlayerVideoRender")
            {
              public void run()
              {
                ((DirectX9GFXCMD)gfxEngine).asyncVideoRender(shmemprefix);
              }
            };
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY); // video has to be fast!!!
            t.start();
          }
          else if ("true".equals(MiniClient.myProperties.getProperty("opengl", "true")))
          {
            cmdOpt2 += " -vo stvwin:shmemprefix=" + gfxEngine.getVideoOutParams();
          }
          else
          {
            // Setup the color key parameter for the overlay
            cmdOpt2 += " -vo directx:colorkey=" + Integer.toString(getDesktopColorKey());
            sage.Native.loadLibrary("SageTVWin32");
            cmdOpt2 += " -wid " + sage.UIUtils.getHWND(target);
          }
          cmdOpt2 += " -nokeepaspect";
        }
        else
        {
          if (new java.io.File("SageTVPlayer").isFile())
            cmds.add("./SageTVPlayer");
          else if (new java.io.File("mplayer").isFile())
            cmds.add("./mplayer");
          else
          {
            System.out.println("Player executable is missing!!!");
            return;
          }
          if (MiniClient.MAC_OS_X)
            cmdOpts1 += " -loadmuted"; // we want MPlayer to start in a muted state
          // If the cache size is too small then when we pause MPlayer it may think it hit an EOS and
          // kill itself. 768 seems like an OK value, 512 was not big enough (testing with 256K streams)
          if ("true".equals(MiniClient.myProperties.getProperty("opengl", "true")))
          {
            if (MiniClient.MAC_OS_X) {
              try {
                java.lang.Class reClass = gfxEngine.getClass();
                java.lang.reflect.Method gsvop = reClass.getMethod("getServerVideoOutParams", new Class[]{});

                cmdOpt2 = (String)gsvop.invoke(gfxEngine, (Object[])null);
                if(cmdOpt2 == null) cmdOpt2 = ""; // null string protection...
              } catch(Throwable t) {
                System.out.println("Exception getting video server params: "+t);
                cmdOpt2 = "";
              }

              //cmdOpt2 = ((OpenGLGFXCMD)gfxEngine).getVideoOutParams();
            } else {
              cmdOpt2 = "-vo stv:socket=" + (myConn).getVideoSocket() + " -vc ffmpeg12,";
            }
            cmdOpt2 += " -cache-seek-min 0 -framedrop";
          }
          else
          {
            if ("false".equals(MiniClient.myProperties.getProperty("xvmc", "false")))
            {
              cmdOpt2 = "-colorkey 0x"+Integer.toHexString(getDesktopColorKey())+
                  " -vo xv:windowevents=0:ck=set  -cache-seek-min 0 -framedrop";
            }
            else
            { // Using xvmc
              cmdOpt2 = "-colorkey 0x"+Integer.toHexString(getDesktopColorKey())+
                  " -vo xvmc:windowevents=0:ck=set, -vc ffmpeg12mc, -cache-seek-min 0 -framedrop";
            }
          }
          if(MiniClient.MAC_OS_X)
            cmdOpt2 += " " + MiniClient.myProperties.getProperty("audio_render", "-ao macosx");
          else
            cmdOpt2 += " " + MiniClient.myProperties.getProperty("audio_render", "-ao alsa");
          if (!disableCache)
            cmdOpt2 += " -cache " + cacheSize;

          // Setup the color key parameter for the overlay
          if (!"true".equals(MiniClient.myProperties.getProperty("opengl", "true")))
          {
            try {
              sage.Native.loadLibrary("Sage");
              cmdOpt2 += " -wid " + sage.UIUtils.getHWND(target);
            } catch (Throwable t) {}
          }
          cmdOpt2 += " -nokeepaspect";
        }
        if (pushMode)
        {
          if (MiniClient.myProperties.getProperty("enable_video_postprocessing", "true").equalsIgnoreCase("true"))
            cmdOpt2 += " -vf pp7";
        }
        else if (file.toLowerCase().indexOf(".mpg") != -1 || file.toLowerCase().indexOf(".ts") != -1)
        {
          if ( MiniClient.myProperties.getProperty("disable_deinterlacing", "false").equalsIgnoreCase("false") )
          {
            // Don't deinterlace for opengl mode and xvmc on Linux
            if ("false".equals(MiniClient.myProperties.getProperty("xvmc", "false")))
            {
              if (!disablePPInPullMode && MiniClient.myProperties.getProperty("enable_video_postprocessing", "true").equalsIgnoreCase("true"))
                cmdOpt2 += " -vf fspp,pp=fd";
              else
                cmdOpt2 += " -vf pp=fd";
            }
          }
        }
        cmdOpt2 += MiniClient.myProperties.getProperty("extra_option", "");

        if (pushMode)
          cmdOpt2 += " -demuxer 2";
        usingEavios = false;
        java.util.StringTokenizer toker = new java.util.StringTokenizer(cmdOpts1 + ' ' +  cmdOpt2, " ");
        while (toker.hasMoreTokens())
          cmds.add(toker.nextToken());
        if (timeshifted && !USE_STDIN)
          cmds.add("-activefile");
        if (bufferSize > 0)
        {
          cmds.add("-circularfilesize");
          cmds.add(Long.toString(bufferSize));
        }

        if (USE_STDIN)
          cmds.add("-");
        else if (file.startsWith("dvd:"))
          cmds.add(file);
        else if (file.indexOf("://") != -1)
        {
          // protocol information is embedded in the filepath
          if (file.startsWith("file://"))
            cmds.add(new java.io.File(file.substring(7)).getPath());
          else
            cmds.add(file); // NOTE: Do we want to construct a file and to getPath on it??
        }
        else if (hostname != null && hostname.length() > 0)
          cmds.add("stv://" + hostname + "/" + new java.io.File(file).getPath());
        else
          cmds.add(new java.io.File(file).getPath());

        String args[] = (String[])cmds.toArray(new String[0]);
        if ("TRUE".equals(MiniClient.myProperties.getProperty("player_cmdline_debug", null)))
        {
            StringBuilder sb = new StringBuilder();
            for (String s: args) {
            	sb.append(s).append(" ");
            }
            // format the COMMAND so that we can actually copy/paste it easily for debugging
            System.out.println("MPLAYER COMMAND: " + sb.toString());
        }
        mpProc = Runtime.getRuntime().exec(args);
      }
      catch (java.io.IOException e)
      {
        System.out.println("FAILED executing mplayer exception:" + e);
        //throw new PlaybackException();
      }

      currState = LOADED_STATE;

      currHintMajorType = majorTypeHint;
      currHintMinorType = minorTypeHint;
      currHintEncoding = encodingHint;

      colorKey = null;
      currCCState = -1;
      videoDimensions = new java.awt.Dimension();
      currFile = new java.io.File(file);
      lastMillis = 0;

      muteState = MiniClient.WINDOWS_OS || MiniClient.MAC_OS_X; // it starts out muted
      fileDeactivated = false;
      seekProcessed = true;
      lastCacheResetTime = System.currentTimeMillis();

      System.out.println("MPlayer was launched successfully");
      Thread the = new Thread("InputStreamConsumer")
      {
        public void run()
        {
          try
          {
            boolean debugMplayer = "true".equals(MiniClient.myProperties.getProperty("player_debug", null));
            java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
                mpProc.getInputStream()));
            String s;
            while ((s = buf.readLine()) != null)
            {
              boolean statusUpdate = false;
              try
              {
                if (!mplayerLoaded && s.startsWith("VO: [eavios]"))
                {
                  eos = false;
                  mplayerLoaded = true;
                  statusUpdate = true;
                }
                else if (!mplayerLoaded && s.startsWith("Starting playback...") &&
                    (!usingEavios || currHintMajorType == 2/*sage.MediaFile.MEDIATYPE_AUDIO*/))
                {
                  // Audio files don't use eavios so don't wait for that!
                  eos = false;
                  mplayerLoaded = true;
                  statusUpdate = true;
                }
                else if (s.startsWith("A:"))
                {
                  if (mplayerLoaded)
                  {
                    // Video timestamps is what I used on Windows, but the Audio ones are more
                    // responsive on mplayer (at least I know how to flush them better at this point)
                    /*int vidx = s.indexOf(" V:");
										 if (vidx != -1)
										 {
											 // video timestamp for curr time
											 lastMillis = Math.round(Float.parseFloat(s.substring(vidx + 3, vidx + 10).trim())*1000);
										 }
										 else*/
                    {
                      // audio timestamp for curr time
                      lastMillis = Math.round(Float.parseFloat(s.substring(2, 9).trim())*1000);
                    }
                    statusUpdate = true;

                    // Find the cache fill percent
                    //int newPerct = getCacheFillPercent(s);
                    //if (newPerct != -1)
                    //	 lastCachePercentFill = newPerct;
                    long newPos = getDemuxerFilePos(s);
                    if (newPos != -1)
                      lastFileReadPos = newPos;
                    if (unMuteOnNextTimeUpdate)
                    {
                      unMuteOnNextTimeUpdate = false;
                      setMute(false);
                    }
                  }
                }
                else if (s.startsWith("V:"))
                {
                  if (mplayerLoaded)
                  {
                    // video timestamp for curr time
                    lastMillis = Math.round(Float.parseFloat(s.substring(2, 9).trim())*1000);
                    statusUpdate = true;
                    // Find the cache fill percent
                    //int newPerct = getCacheFillPercent(s);
                    //if (newPerct != -1)
                    // lastCachePercentFill = newPerct;
                    long newPos = getDemuxerFilePos(s);
                    if (newPos != -1)
                      lastFileReadPos = newPos;
                    if (unMuteOnNextTimeUpdate)
                    {
                      unMuteOnNextTimeUpdate = false;
                      setMute(false);
                    }
                  }
                }
                /*else if (s.startsWith("BufferRem="))
								 {
									 lastCacheRemBytes = Integer.parseInt(s.substring("BufferRem=".length()).trim());
								 }*/
                else if (s.startsWith("MUTED="))
                {
                  muteState = s.charAt(6) != '0';
                  statusUpdate = true;
                }
                else if (s.startsWith("VOLUME="))
                {
                  float parsedF = Float.parseFloat(s.substring(7));
                  if (MiniClient.WINDOWS_OS)
                    currVolume = (float)(((1.0f/(VOLUME_LOG_SHAPING-1)) * (Math.pow(VOLUME_LOG_SHAPING, parsedF/100.0f) - 1)));
                  else
                    currVolume = (float)(parsedF / 100.0f);
                  statusUpdate = true;
                }
                else if (s.startsWith("EOF code"))
                {
                  if (mplayerLoaded)
                  {
                    System.out.println("EOF Code read from stream, setting EOS flag");
                    eos = true;
                    statusUpdate = true;
                    //VideoFrame.getVideoFrameForPlayer(LinuxMPlayerPlugin.this).kick(); // so it sees the EOS
                    myConn.postMediaPlayerUpdateEvent();
                  }
                }
                else if (s.startsWith("  =====  PAUSE"))
                {
                  if (mplayerLoaded && !eos && currState == PLAY_STATE) // pauses while in EOS are just stream exhaustion, not actual pausing
                  {
                    currState = PAUSE_STATE;
                    statusUpdate = true;
                  }
                }
                else if (s.startsWith("DURATION="))
                {
                  realDurMillis = Math.round(Float.parseFloat(s.substring("DURATION=".length()).trim())*1000) - initialPTSmsec;
                  statusUpdate = true;
                }
                else if (s.startsWith("DEMUXSEEKSTART"))
                {
                  eos = false;
                  lastCacheResetTime = System.currentTimeMillis();
                  seekProcessed = true;
                  statusUpdate = true;
                }
                else if (s.startsWith("ID_VIDEO_WIDTH="))
                {
                  try
                  {
                    videoDimensions.width = Integer.parseInt(s.substring("ID_VIDEO_WIDTH=".length()).trim());
                  }catch (NumberFormatException e){System.out.println("error parsing video width:" + e);}
                }
                else if (s.startsWith("ID_VIDEO_HEIGHT="))
                {
                  try
                  {
                    videoDimensions.height = Integer.parseInt(s.substring("ID_VIDEO_HEIGHT=".length()).trim());
                  }catch (NumberFormatException e){System.out.println("error parsing video height:" + e);}
                }
                else if (s.startsWith("INITIAL_PTS="))
                {
                  try
                  {
                    initialPTSmsec = Math.round(Double.parseDouble(s.substring("INITIAL_PTS=".length()).trim())*1000);
                  }catch (NumberFormatException e){System.out.println("error parsing PTS:" + e);}
                }
                else if (s.startsWith("CCDATA:"))
                {
                  java.util.StringTokenizer toker = new java.util.StringTokenizer(s.substring(8), " ");
                  // 8 bytes for each byte pair; 4-pts, 1-flag, 2-data, 1-pad
                  byte[] ccData = new byte[8 * (toker.countTokens() / 2)];
                  for (int i = 0; i < ccData.length/8; i++)
                  {
                    // 4 byte PTS leave as zero
                    ccData[i*8 + 5] = (byte)(Integer.parseInt(toker.nextToken(), 16) & 0xFF);
                    ccData[i*8 + 6] = (byte)(Integer.parseInt(toker.nextToken(), 16) & 0xFF);
                  }
                  myConn.postSubtitleInfo(0, 0, ccData, 0x10);
                }
                else if (s.startsWith("<vo_directx><WARN>Your card supports overlay, but we couldn't create one"))
                {
                  sage.MySwingUtils.showWrappedMessageDialog("SageTV Placeshifter was unable to create the Overlay surface. You may have another application that is open and using it. If that does not correct the problem; try enabling '3D Acceleration' in the Settings for the Placeshifter.",
                      "Media Player Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
                else if (!debugMplayer) System.out.println("stdout:" + s);
              }
              catch (Exception e)
              {
                System.out.println("ERROR parsing mplayer status of:" + e);
              }

              if (debugMplayer) System.out.println("stdout:" + s);
              if (statusUpdate)
              {
                synchronized (stdoutLock)
                {
                  stdoutLock.notifyAll();
                }
              }
            }
            buf.close();
          }
          catch (Exception e){}
          System.out.println("stdout consumer has closed");

          // Check if we wanted this to happen or not
          synchronized (MiniMPlayerPlugin.this)
          {
            if (currState == PLAY_STATE || currState == PAUSE_STATE)
            {
              // Only reload it if it's a good file.
              if (currFile != null && currFile.isFile() && currFile.length() > 0)
              {
                System.out.println("MPlayer quit unexpectedly....reloading the file...");
                //							 VideoFrame.getVideoFrameForPlayer(LinuxMPlayerPlugin.this).reloadFile();
              }
            }
          }
        }
      };
      the.setDaemon(true);
      the.start();
      the = new Thread("ErrorStreamConsumer")
      {
        public void run()
        {
          try
          {
            java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
                mpProc.getErrorStream()));
            String s;
            while ((s = buf.readLine()) != null)
            {
              System.out.println("stderr:" + s);
              if (s.startsWith("FATAL: Could not initialize video filters") && pushMode)
              {
                if (MiniClient.myProperties.getProperty("enable_video_postprocessing", "true").equalsIgnoreCase("true"))
                {
                  System.out.println("Video rendering failed with post-processing. Try again without it...");
                  MiniClient.myProperties.setProperty("enable_video_postprocessing", "false");
                  sage.MySwingUtils.showWrappedMessageDialog("SageTV Placeshifter has detected an incompatability in your video hardware. It has corrected the problem. Please restart the SageTV Placeshifter at this time.",
                      "Media Player Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
              }
              else if (s.startsWith("<vo_directx><ERROR>Your card doesn't support overlay"))
              {
                sage.MySwingUtils.showWrappedMessageDialog("SageTV Placeshifter has detected an incompatability in your video hardware. It does not support Overlay. You may need to upgrade your video driver; or you can try enabling '3D Acceleration' under the Placeshifter Settings.",
                    "Media Player Error", javax.swing.JOptionPane.ERROR_MESSAGE);
              }
            }
            buf.close();
          }
          catch (Exception e){}
          System.out.println("stderr consumer has closed");
        }
      };
      the.setDaemon(true);
      the.start();
      if (USE_STDIN)
        mpStdinRaw = mpProc.getOutputStream();
      else
        mpStdin = new java.io.PrintWriter(new java.io.BufferedOutputStream(mpProc.getOutputStream()), true);

      //initialPTSmsec = MPEGUtils.getMPEG2InitialTimestamp(file);
      //			if (!pushMode)
      waitForMPlayerToFinishLoad();

      // It starts playing right after load
      currState = PLAY_STATE;

      // Trigger a volume refresh
      //if (!MiniClient.WINDOWS_OS)
      //	sendCommand("volume 0");
      //			if (!pushMode)
      waitForPlaybackToPass(0);
    }
  }

  private int getCacheFillPercent(String s)
  {
    int pctIdx = s.lastIndexOf('%');
    if (pctIdx == -1) return -1;
    int spIdx = s.lastIndexOf(' ', pctIdx);
    if (spIdx == -1) return -1;
    try
    {
      return Integer.parseInt(s.substring(spIdx + 1, pctIdx));
    }
    catch (NumberFormatException e)
    {
      return -1;
    }
  }

  private long getDemuxerFilePos(String s)
  {
    s = s.trim();
    int pidx = s.indexOf("P:");
    if (pidx == -1) return -1;
    int spIdx = s.indexOf(' ', pidx);
    if (spIdx == -1) return -1;
    try
    {
      return Long.parseLong(s.substring(pidx + 2, spIdx));
    }
    catch (NumberFormatException e)
    {
      return -1;
    }
  }

  private void waitForMPlayerToFinishLoad()
  {
    System.out.println("Waiting for MPlayer to finish loading");
    long startTime = System.currentTimeMillis();
    while (!mplayerLoaded && System.currentTimeMillis() - startTime < 10000)
    {
      synchronized (stdoutLock)
      {
        try
        {
          stdoutLock.wait(5000);
        }
        catch (Exception e){}
      }
    }
    System.out.println("Done waiting for MPlayer to finish loading");
  }

  private void waitForPlaybackToPass(long time)
  {
    System.out.println("Waiting for MPlayer to pass time:" + time);
    long startTime = System.currentTimeMillis();
    while ((lastMillis <= time) && System.currentTimeMillis() - startTime < 10000)
    {
      synchronized (stdoutLock)
      {
        try
        {
          stdoutLock.wait(5000);
        }
        catch (Exception e){}
      }
    }
    System.out.println("Done waiting for MPlayer to pass time:" + time);
  }

  private void waitForSeekToStart()
  {
    System.out.println("Waiting for MPlayer to start seek");
    long startTime = System.currentTimeMillis();
    while (!seekProcessed && System.currentTimeMillis() - startTime < 10000)
    {
      synchronized (stdoutLock)
      {
        try
        {
          stdoutLock.wait(5000);
        }
        catch (Exception e){}
      }
    }
    System.out.println("Done waiting for MPlayer to start seek");
  }

  private void waitToEnterPauseState()
  {
    System.out.println("Waiting for MPlayer to pause");
    long startTime = System.currentTimeMillis();
    while (currState != PAUSE_STATE && System.currentTimeMillis() - startTime < 5000)
    {
      synchronized (stdoutLock)
      {
        try
        {
          stdoutLock.wait(5000);
        }
        catch (Exception e){}
      }
    }
    System.out.println("Done waiting for MPlayer to pause");
  }

  public boolean pause()
  {
    if (alive && pushMode && Thread.currentThread() != cmdThread)
    {
      synchronized (cmdQueue)
      {
        cmdQueue.add(new Object[] {new Integer(MediaCmd.MEDIACMD_PAUSE)});
        cmdQueue.notifyAll();
      }
      return true;
    }
    if (currState == PLAY_STATE && !eos)
    {
      synchronized (this)
      {
        sendCommand("pause");
      }
      waitToEnterPauseState();
    }
    return currState == PAUSE_STATE;
  }

  public boolean play()
  {
    if (alive && pushMode && Thread.currentThread() != cmdThread)
    {
      synchronized (cmdQueue)
      {
        cmdQueue.add(new Object[] {new Integer(MediaCmd.MEDIACMD_PLAY)});
        cmdQueue.notifyAll();
      }
      return true;
    }
    if (currState == PAUSE_STATE && !eos)
    {
      synchronized (this)
      {
        sendCommand("pause");
        currState = PLAY_STATE;
        lastMillis--; // so an update of the current time works also
      }
      waitForPlaybackToPass(lastMillis);
    }
    return currState == PLAY_STATE;
  }

  public long seek(long seekTimeMillis)
  {
    if (alive && pushMode && Thread.currentThread() != cmdThread)
    {
      synchronized (cmdQueue)
      {
        // Remove any other seek calls that are pending
        for (int i = 0; i < cmdQueue.size(); i++)
        {
          if (((Integer)((Object[])cmdQueue.get(i))[0]).intValue() == MediaCmd.MEDIACMD_SEEK)
          {
            cmdQueue.remove(i);
            i--;
          }
        }
        cmdQueue.add(new Object[] {new Integer(MediaCmd.MEDIACMD_SEEK), new Long(seekTimeMillis)});
        cmdQueue.notifyAll();
        pendingPushSeek = seekTimeMillis == Long.MAX_VALUE;
      }
      return seekTimeMillis;
    }
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      boolean pausedSeek, tempMute, muteStep;
      synchronized (this)
      {
        eos = false;
        seekProcessed = false;
        pausedSeek = currState == PAUSE_STATE;
        if (pushMode && seekTimeMillis == Long.MAX_VALUE)
        {
          // So MediaCmd doesn't think that we don't have any free buffers.
          lastFileReadPos = currFile.length();
          pausedSeek = false;
        }
        // Don't mute on seeks for pushing, we fixed the audio looping bug which is what was causing all the problem to begin with
        tempMute = pausedSeek;//true;//pausedSeek || pushMode;
        // If it's push mode and we've deactivated the file then reactivate it now!
        if (pushMode && fileDeactivated && seekTimeMillis == Long.MAX_VALUE)
        {
          sendCommand("active_file");
          fileDeactivated = false;
        }
        muteStep = !muteState;
        if (tempMute && muteStep)
          setMute(true);
        if (seekTimeMillis <= 1)
          sendCommand((pausedSeek ? "pausing " : "") + "seek 0 2");
        else
          sendCommand((pausedSeek ? "pausing " : "") + "seek " + ((seekTimeMillis == Long.MAX_VALUE ? 999999999 : seekTimeMillis) + initialPTSmsec)/1000.0 + " 2");
      }
      //			if (!pushMode)
      waitForSeekToStart();
      lastCacheResetTime = System.currentTimeMillis();
      lastCacheRemBytes = 0;
      synchronized (cmdQueue)
      {
        if (pendingPushSeek)
        {
          // Check if there's any pending seeks still, if not, clear this flag
          boolean seekIsPending = false;
          for (int i = 0; i < cmdQueue.size(); i++)
          {
            if (((Integer)((Object[])cmdQueue.get(i))[0]).intValue() == MediaCmd.MEDIACMD_SEEK)
            {
              seekIsPending = true;
              break;
            }
          }
          if (!seekIsPending)
            pendingPushSeek = false;
        }
      }
      if (!pushMode)
        lastMillis = seekTimeMillis + initialPTSmsec;
      else if (seekTimeMillis == Long.MAX_VALUE)
        lastMillis = 0;
      if (pausedSeek)
      {
        int numPausedSeekSteps = 3;
        while (numPausedSeekSteps-- > 0)
        {
          frameStep(1);
        }
      }
      if (tempMute && muteStep)
      {
        //				if (pushMode)
        unMuteOnNextTimeUpdate = true;
        //				else
        //					setMute(false);
      }
      return seekTimeMillis;
    }
    return 0;
  }

  public boolean setClosedCaptioningState(int ccState)
  {
    return false;
  }

  public void setMute(boolean x)
  {
    if (alive && pushMode && Thread.currentThread() != cmdThread)
    {
      synchronized (cmdQueue)
      {
        cmdQueue.add(new Object[] {new Integer(MediaCmd.MEDIACMD_SETMUTE), new Boolean(x)});
        cmdQueue.notifyAll();
      }
      return;
    }
    synchronized (this)
    {
      if (muteState != x)
      {
        sendCommand("mute");
        muteState = x;
      }
    }
  }

  public float setPlaybackRate(float newRate)
  {
    return 1;
  }

  public void setVideoRectangles(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    if (alive && pushMode && Thread.currentThread() != cmdThread)
    {
      synchronized (cmdQueue)
      {
        cmdQueue.add(new Object[] {new Integer(MediaCmd.MEDIACMD_SETVIDEORECT), videoSrcRect, videoDestRect});
        cmdQueue.notifyAll();
      }
      return;
    }
    if (videoSrcRect == null || videoDestRect == null || videoDimensions == null ||
        videoSrcRect.width > videoDimensions.width || videoSrcRect.height > videoDimensions.height)
    {
      // These are invalid dimensions, probably based off incorrect source information so we should just ignore them
      // and wait for the owner to get our correct dimensions
      System.out.println("Ignoring resize command because it exceeds video dimensions rect=" + videoSrcRect + " videoDim=" + videoDimensions);
      return;
    }
    synchronized (this)
    {
      sendCommand("video_out_rectangles " + videoSrcRect.x + " " + videoSrcRect.y + " " + videoSrcRect.width + " " + videoSrcRect.height +
          " " + videoDestRect.x + " " + videoDestRect.y + " " + videoDestRect.width + " " + videoDestRect.height);
    }
  }

  public float setVolume(float f)
  {
    if (alive && pushMode && Thread.currentThread() != cmdThread)
    {
      synchronized (cmdQueue)
      {
        cmdQueue.add(new Object[] {new Integer(MediaCmd.MEDIACMD_SETVOLUME), new Float(f)});
        cmdQueue.notifyAll();
      }
      currVolume = f;
      return currVolume;
    }
    synchronized (this)
    {
      float convertF;
      if (MiniClient.WINDOWS_OS)
        convertF = (float)(Math.floor(100.0f * Math.log((f*(VOLUME_LOG_SHAPING-1)) + 1)/Math.log(VOLUME_LOG_SHAPING)));
      else
        convertF = (float)Math.floor(100.0f * f);
      sendCommand("volume " + Math.round(convertF) + " 1");
      currVolume = f;
      return currVolume;
    }
  }

  public void stop()
  {
    if (alive && pushMode && Thread.currentThread() != cmdThread)
    {
      synchronized (cmdQueue)
      {
        cmdQueue.add(new Object[] {new Integer(MediaCmd.MEDIACMD_STOP)});
        cmdQueue.notifyAll();
      }
      return;
    }
    if (currState == PLAY_STATE && !eos)
    {
      synchronized (this)
      {
        sendCommand("pause");
        currState = STOPPED_STATE;
      }
    }
    else
      currState = STOPPED_STATE;
  }

  private synchronized void sendCommand(String cmd)
  {
    if (mpStdin == null) return;
    processingMplayerSend = true;
    long timeDiff = System.currentTimeMillis() - lastCmdTime;
    if (timeDiff < 90)
    {
      // MPlayer doesn't deal too well with multiple commands sent quickly in succession
      // Update: 4/3/06 - Narflex fixed this bug in MPlayer on Windows
      //			try{Thread.sleep(100 - timeDiff);}catch(Exception e){}
    }
    System.out.println("Sending mplayer command:" + cmd);
    lastCmdTime = System.currentTimeMillis();
    mpStdin.println(cmd);
    processingMplayerSend = false;
  }

  public synchronized void pushData(byte[] data, int offset, int length) throws java.io.IOException
  {
    mpStdinRaw.write(data, offset, length);
  }

  private boolean isMPlayerRunning()
  {
    try
    {
      Process proc = mpProc;
      if (proc == null)
        return false;
      proc.exitValue();
      return false;
    }
    catch (IllegalThreadStateException e)
    {
      return true;
    }
  }

  public long getLastFileReadPos()
  {
    //return (System.currentTimeMillis() - lastCacheResetTime < 2000) || !seekProcessed || lastCacheRemBytes < 32768*2;
    if (currFile == null)
      return 0;
    return ((System.currentTimeMillis() - lastCacheResetTime > 2000) && seekProcessed)
        ? lastFileReadPos : (lastFileReadPos + 1024*1024);
  }

  // This is the command processing thread that's used when in push mode.
  public void run()
  {
    boolean firstRun = true;
    while (alive)
    {
      Object currCmd = null;
      synchronized (cmdQueue)
      {
        if (!cmdQueue.isEmpty())
        {
          if (firstRun)
          {
            // Look for the OPENURL one and run that first so the commands that got queued before it apply
            // to the file (like volume setting)
            for (int i = 0; i < cmdQueue.size(); i++)
            {
              if (((Integer)((Object[]) cmdQueue.get(i))[0]).intValue() == MediaCmd.MEDIACMD_OPENURL)
              {
                currCmd = cmdQueue.remove(i);
                break;
              }
            }
            firstRun = false;
          }
          if (currCmd == null)
            currCmd = cmdQueue.remove(0);
        }
        else
        {
          try
          {
            cmdQueue.wait(5000);
          }
          catch (InterruptedException e){}
          continue;
        }
      }
      Object[] cmdData = (Object[]) currCmd;
      int cmdID = ((Integer)cmdData[0]).intValue();
      switch (cmdID)
      {
        case MediaCmd.MEDIACMD_OPENURL:
          load((byte)0, (byte)0, "", (String) cmdData[1], (String) cmdData[2], ((Boolean)cmdData[3]).booleanValue(), 0);
          break;
        case MediaCmd.MEDIACMD_FRAMESTEP:
          frameStep(1);
          break;
        case -1:
          inactiveFile();
          break;
        case MediaCmd.MEDIACMD_PAUSE:
          pause();
          break;
        case MediaCmd.MEDIACMD_PLAY:
          play();
          break;
        case MediaCmd.MEDIACMD_STOP:
          stop();
          break;
        case MediaCmd.MEDIACMD_SEEK:
          seek(((Long)cmdData[1]).longValue());
          break;
        case MediaCmd.MEDIACMD_SETMUTE:
          setMute(((Boolean)cmdData[1]).booleanValue());
          break;
        case MediaCmd.MEDIACMD_SETVIDEORECT:
          setVideoRectangles((java.awt.Rectangle)cmdData[1], (java.awt.Rectangle)cmdData[2], false);
          break;
        case MediaCmd.MEDIACMD_SETVOLUME:
          setVolume(((Float)cmdData[1]).floatValue());
          break;
        default:
          System.out.println("Unknown Media Cmd in queue of: " + cmdID);
          break;
      }
    }
  }

  private Process mpProc;
  private java.io.BufferedReader mpStdout;
  private java.io.PrintWriter mpStdin;
  private java.io.OutputStream mpStdinRaw;
  private java.io.BufferedReader mpStderr;

  private long lastMillis;

  protected int currState;
  protected java.io.File currFile;
  protected byte currHintMajorType;
  protected byte currHintMinorType;
  protected String currHintEncoding;

  protected int transparency;
  protected java.awt.Color colorKey;
  protected java.awt.Dimension videoDimensions;

  protected int currCCState;
  protected boolean muteState = true;
  protected float currVolume = 1.0f;
  protected boolean eos;

  protected long initialPTSmsec;

  protected boolean mplayerLoaded;

  protected boolean fileDeactivated;

  protected Object stdoutLock = new Object();

  protected long realDurMillis;
  protected boolean seekProcessed;

  protected boolean usingEavios;

  protected String audioOutputPort = "";

  private GFXCMD2 gfxEngine;
  private java.awt.Canvas target;

  private long lastCacheRemBytes;
  private long lastCacheResetTime;

  private long lastFileReadPos;
  private boolean pushMode;
  private long lastCmdTime;

  private java.util.Vector cmdQueue = new java.util.Vector();
  private Thread cmdThread;
  private boolean alive;

  private MiniClientConnection myConn;

  private boolean unMuteOnNextTimeUpdate;

  private boolean pendingPushSeek;

  private boolean processingMplayerSend;
}
