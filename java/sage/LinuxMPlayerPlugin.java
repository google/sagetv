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

public class LinuxMPlayerPlugin implements DVDMediaPlayer
{
  private static final float VOLUME_LOG_SHAPING = 30000.0f; // higher numbers mean the louder sound covers a larger range
  protected static final long GUESS_VALIDITY_DURATION = 1000;
  private static Boolean sixteenBitDesktop = null;
  public LinuxMPlayerPlugin()
  {
    currState = NO_STATE;
    if (sixteenBitDesktop == null)
    {
      java.awt.GraphicsDevice[] screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().
          getScreenDevices();
      if (screens != null && screens.length > 0 && screens[0].getDisplayMode().getBitDepth() == 16)
        sixteenBitDesktop = Boolean.TRUE;
      else
        sixteenBitDesktop = Boolean.FALSE;
    }
  }

  public boolean shouldDeinterlace(byte majorTypeHint, byte minorTypeHint)
  {
    return minorTypeHint == MediaFile.MEDIASUBTYPE_MPEG2_PS;
  }

  public boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file)
  {
    return canDoFastLoad && isMPlayerRunning() && shouldDeinterlace(majorTypeHint, minorTypeHint) == shouldDeinterlace(currHintMajorType, currHintMinorType) &&
        MediaFile.isMPEG2LegacySubtype(minorTypeHint);
  }

  public synchronized void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException
  {
    loadFailed = false;
    boolean pauseOnLoad = currState == PAUSE_STATE;
    currState = LOADED_STATE;
    mplayerLoaded = false;
    realDurMillis = 0;
    lastMillis = 0;
    eos = false;
    fileDeactivated = false;
    initialPTSmsec = 0;
    currState = PLAY_STATE; // it'll be playing at first, but the succesful pause will update this
    sendCommand((pauseOnLoad ? "pausing " : "") + "loadfile2 \"" +
        ((hostname != null && hostname.length() > 0) ? ("stv://" + hostname + "/") : "") +
        file.getPath().replaceAll("\\\\", "\\\\\\\\") + "\" " + (timeshifted ? "1 " : "0 ") + bufferSize);
    if (!waitForMPlayerToFinishLoad())
      throw new PlaybackException();
    currHintMajorType = majorTypeHint;
    currHintMinorType = minorTypeHint;
    currHintEncoding = encodingHint;
    if (pauseOnLoad)
      waitToEnterPauseState();
    else
      waitForPlaybackToPass(0);
  }

  public boolean frameStep(int amount)
  {
    // Frame stepping will pause it if playing
    if ((currState == PLAY_STATE || currState == PAUSE_STATE) && !eos)
    {
      synchronized (this)
      {
        if (currState == PLAY_STATE)
        {
          pause();
          //					sendCommand("pause");
          //					currState = PAUSE_STATE;
        }
        int startPauseCount = pauseCount;
        sendCommand("frame_step");
        waitForNextPause(startPauseCount);
        return true;
      }
    }
    return false;
  }

  public void free()
  {
    currState = NO_STATE;
    if (Sage.DBG) System.out.println("Closing down mplayer");
    if (uiMgr != null)
      uiMgr.putFloat("mplayer/last_volume", currVolume);
    timeGuessMillis = 0;
    guessTimestamp = 0;
    currVolume = 1.0f;
    if (mpStdin != null && isMPlayerRunning())
    {
      // Be sure to clear the active file bit in case MPlayer is waiting for more data
      // Be sure we don't close the stdin connection before we send the quit message, but don't
      // hang waiting for the quit to be processed
      //			inactiveFile();
      //			stop();
      Thread t = new Thread("PlayerSendCmd")
      {
        public void run()
        {
          if (Sage.DBG) System.out.println("Waiting for the cmd queue to clear...");
          synchronized (sendCmdQueue)
          {
            while (!sendCmdQueue.isEmpty())
            {
              try
              {
                sendCmdQueue.wait(50);
              }
              catch (InterruptedException e){}
              continue;
            }
          }
          if (Sage.DBG) System.out.println("Sending mplayer command: quit");
          if (!fileDeactivated)
          {
            fileDeactivated = true;
            synchronized (mpStdin)
            {
              mpStdin.println("inactive_file");
            }
          }
          if (currState == PLAY_STATE && !eos)
          {
            synchronized (mpStdin)
            {
              mpStdin.println("pause");
            }
            currState = STOPPED_STATE;
          }
          else
            currState = STOPPED_STATE;
          synchronized (mpStdin)
          {
            mpStdin.println("quit");
          }
          mpStdin.close();
          mpStdin = null;
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
      };
      t.setPriority(Thread.currentThread().getPriority());
      t.setDaemon(true);
      t.start();
    }

    if (mpProc != null)
    {
      long startWait = Sage.eventTime();
      // FIXME: temp crutch, in testing on Mac OS X mplayer either terminates immediately or hangs, there is no in-between...
      long killDelay = (Sage.MAC_OS_X ? 2000 : 15000);
      while (true)
      {
        try
        {
          int exitValue = mpProc.exitValue();
          if (Sage.DBG) System.out.println("MPlayer process exit code:" + exitValue);
          break;
        }
        catch (IllegalThreadStateException e)
        {
          if (Sage.DBG) System.out.println("MPlayer process has not exited yet...");
          try{Thread.sleep(100);}catch(Exception e1){}
          if (Sage.eventTime() - startWait > killDelay)
          {
            if (Sage.DBG) System.out.println("Forcibly killing MPlayer process!");
            mpProc.destroy();
            break;
          }
        }
      }
      mpProc = null;
    }
    if (launchedAsyncRenderThread)
      ((DirectX9SageRenderer)uiMgr.getRootPanel().getRenderEngine()).asyncVideoRender(null);
    if (releaseServerAccessVobSubBase != null)
    {
      NetworkClient.getSN().requestMediaServerAccess(new java.io.File(releaseServerAccessVobSubBase + ".idx"), false);
      NetworkClient.getSN().requestMediaServerAccess(new java.io.File(releaseServerAccessVobSubBase + ".sub"), false);
      releaseServerAccessVobSubBase = null;
    }
  }

  public int getClosedCaptioningState()
  {
    return CC_DISABLED;
  }

  public java.awt.Color getColorKey()
  {
    return Sage.WINDOWS_OS ? new java.awt.Color(Sage.getInt("mplayer/color_key", 0x10010)) : new java.awt.Color(Sage.getInt("linux/color_key", 66046));
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
    if (Sage.eventTime() - guessTimestamp < GUESS_VALIDITY_DURATION) // after seeks it doesn't know the right time at first
    {
      return timeGuessMillis;
    }
    return (eos && realDurMillis > 0) ? realDurMillis : (lastMillis - initialPTSmsec);
  }

  public boolean getMute()
  {
    return muteState;
  }

  public int getPlaybackCaps()
  {
    return FRAME_STEP_FORWARD_CAP | PAUSE_CAP | SEEK_CAP;
  }

  public float getPlaybackRate()
  {
    return 1;
  }

  public int getState()
  {
    return eos ? EOS_STATE : currState;
  }

  public int getTransparency()
  {
    return blender ? TRANSLUCENT : BITMASK;
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
    if (!fileDeactivated)
    {
      fileDeactivated = true;
      sendCommand("inactive_file");
    }
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
  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    loadFailed = false;
    initialPTSmsec = 0;
    currState = NO_STATE;
    mplayerLoaded = false;
    timeGuessMillis = 0;
    guessTimestamp = Sage.eventTime();
    realDurMillis = 0;
    eos = false;
    blender = false;
    canDoFastLoad = true;
    uiMgr = VideoFrame.getVideoFrameForPlayer(this).getUIMgr();
    // Launch the mplayer process
    try
    {
      // -slave is for stdin control
      java.util.ArrayList cmds = new java.util.ArrayList();
      String cmdOpts1,cmdOpt2;

      cmdOpts1 = "-identify -osdlevel 0 -autosync 30 -noconsolecontrols -mc 1 -slave -sid 99"; // the -sid 99 disables subtitles by default (unless they have 99 sub langs)
      String extraArgs = uiMgr.get("extra_mplayer_args", "");
      if (extraArgs.length() > 0)
        cmdOpts1 += " " + extraArgs;
      int streamBufferSize = uiMgr.getInt("stream_buffer_size", 65536);

      int cacheSize = 2048;
      boolean disableCache = false;
      // Reduce cache size for some lower rate formats.

      if (file != null)
      {
        String flc = file.toString().toLowerCase();
        if (flc.endsWith(".flv") || flc.endsWith(".mov") || flc.endsWith(".mp4"))
        {
          cacheSize = 512;
          streamBufferSize = 8192;
          disableCache = true;
        }
      }

      cmdOpts1 += " -stream-buffer-size " + streamBufferSize;

      String framedropStr = uiMgr.getBoolean("mplayer/enable_framedrop", true) ? " -framedrop" : "";

      if (Sage.WINDOWS_OS)
      {
        if (new java.io.File("SageTVPlayer.exe").isFile())
          cmds.add("SageTVPlayer.exe");
        else if (new java.io.File("mplayer.exe").isFile())
          cmds.add("mplayer.exe");
        else
        {
          System.out.println("Player executable is missing!!!");
          throw new PlaybackException();
        }
        cmdOpt2 = "-priority abovenormal";
        if (majorTypeHint != MediaFile.MEDIATYPE_AUDIO)
        {
          // See if we're setup to use 3D video rendering
          if (uiMgr.getRootPanel().getRenderType() == ZRoot.NATIVE3DRENDER &&
              (DShowMediaPlayer.getUseVmr() || DShowMediaPlayer.getUseEvr()/* || uiMgr.getRootPanel().getRenderEngine() instanceof JOGLSageRenderer*/))
          {
            blender = true;
            if (uiMgr.getRootPanel().getRenderEngine() instanceof DirectX9SageRenderer)
            {
              final String shmemprefix = "SageTV" + (shmemcounter = ((shmemcounter+1)%1024));
              cmdOpt2 += " -vo stvwin:shmemprefix=" + shmemprefix;
              Thread t = new Thread("AsyncMPlayerVideoRender")
              {
                public void run()
                {
                  launchedAsyncRenderThread = true;
                  ((DirectX9SageRenderer)uiMgr.getRootPanel().getRenderEngine()).asyncVideoRender(shmemprefix);
                }
              };
              t.setDaemon(true);
              t.setPriority(Thread.MAX_PRIORITY); // video has to be fast!!!
              t.start();
            }
            /*else
						{
							cmdOpt2 += " -vo stvwin:shmemprefix=" + ((JOGLSageRenderer)uiMgr.getRootPanel().getRenderEngine()).getServerVideoOutParams();
						}*/
            canDoFastLoad = false;
          }
          else
          {
            // Setup the color key parameter for the overlay
            cmdOpt2 += " -vo directx:colorkey=" + Integer.toString(getDesktopColorKey());
            cmdOpt2 += " -wid " + VideoFrame.getVideoFrameForPlayer(this).getVideoHandle(true);
            updateVideoRect = true;
          }
        }
        cmdOpt2 += " -cache-seek-min 0" + framedropStr; //
        cmdOpts1 += " -loadmuted"; // we want MPlayer to start in a muted state
        if (!disableCache)
          cmdOpt2 += " -cache " + cacheSize;
        cmdOpt2 += " -ao " + uiMgr.get("mplayer/audio_renderer", "dsound");
        cmdOpt2 += " -nokeepaspect";
      }
      else
      {
        if (new java.io.File(Sage.getPath("core", "SageTVPlayer")).isFile())
          cmds.add(Sage.getPath("core", "SageTVPlayer"));
        else if (new java.io.File(Sage.getPath("core", "mplayer")).isFile())
          cmds.add(Sage.getPath("core", "mplayer"));
        else
        {
          System.out.println("Player executable is missing!!!");
          throw new PlaybackException();
        }
        // If the cache size is too small then when we pause MPlayer it may think it hit an EOS and
        // kill itself. 768 seems like an OK value, 512 was not big enough (testing with 256K streams)
        /*if (uiMgr.getRootPanel().getRenderEngine() instanceof JOGLSageRenderer)
				{
					cmdOpt2 = (Sage.LINUX_OS ? "-vo stv:shmkey=" : "")
						+ ((JOGLSageRenderer)uiMgr.getRootPanel().getRenderEngine()).getServerVideoOutParams()
						+ " -cache-seek-min 0" + framedropStr;
					canDoFastLoad = false;
				}
				else*/ if(Sage.MAC_OS_X)
				{
				  String ret = "";

				  try {
				    // we have to use reflection to get to the renderer...
				    java.lang.Class reClass = uiMgr.getRootPanel().getRenderEngine().getClass();
				    java.lang.reflect.Method gsvop = reClass.getMethod("getServerVideoOutParams", new Class[]{});

				    ret = (String)gsvop.invoke(uiMgr.getRootPanel().getRenderEngine(), (Object[])null);
				    //System.out.println("++++++++++++++++++++++++++++++++ macstv vo params: "+ret);
				  } catch(Throwable t) {
				    System.out.println("Exception occurred getting vo module params: "+t);
				  }

				  cmdOpt2 = ret + " -cache-seek-min 0" + framedropStr;

				  // if "linux/audio_output_port" is set to SPDIF, add "-afm hwac3"
				  if ("SPDIF".equals(Sage.get("linux/audio_output_port", "Analog")))
				    cmdOpt2 += " -afm hwac3";

				  canDoFastLoad = false;
				}
				else
				{
				  if (!uiMgr.getBoolean("mplayer/xvmc", false))
				  {
				    cmdOpt2 = "-colorkey 0x"+Integer.toHexString(getDesktopColorKey())+
				        " -vo xv:windowevents=0:ck=set  -cache-seek-min 0" + framedropStr;
				  }
				  else
				  { // Using xvmc
				    cmdOpt2 = "-colorkey 0x"+Integer.toHexString(getDesktopColorKey())+
				        " -vo xvmc:windowevents=0:ck=set -vc ffmpeg12mc, -cache-seek-min 0" + framedropStr;
				  }
				}
				updateVideoRect = true;
				if (!disableCache)
				  cmdOpt2 += " -cache " + cacheSize;
				cmdOpt2 += " -nolirc";
				if(Sage.MAC_OS_X)
				  //					cmdOpt2 += " -ao openal"; // OpenAL is broken, beside ao_macosx supports AC/3 passthrough to digital ports if available
				  cmdOpt2 += " -ao macosx";
				else
				  cmdOpt2 += " " + uiMgr.get("mplayer/audio_render", "-ao alsa:device=hw");
				// Setup the color key parameter for the overlay
				if (!Sage.MAC_OS_X /*&& !(uiMgr.getRootPanel().getRenderEngine() instanceof JOGLSageRenderer)*/)
				{
				  cmdOpt2 += " -wid " + VideoFrame.getVideoFrameForPlayer(this).getVideoHandle(true);
				}
				cmdOpt2 += " -nokeepaspect";
      }
      boolean doPP = uiMgr.getBoolean("mplayer/enable_video_postprocessing", false);
      boolean disableDeint = uiMgr.getBoolean("mplayer/disable_deinterlacing", false); // added for those who want to run full screen on not quite so high end machines... (think GMA-950)
      boolean needsDeint = false;
      boolean useLAVFDemux = false;	// -demuxer lavf is needed for h.264 in TS, ala HD-PVR
      int audioID = -1;
      mf = VideoFrame.getMediaFileForPlayer(this);
      if (mf != null)
      {
        sage.media.format.ContainerFormat cf = mf.getFileFormat();
        if (cf != null)
        {
          sage.media.format.VideoFormat vidForm = cf.getVideoFormat();
          if (vidForm != null && vidForm.isInterlaced() && !disableDeint)
            needsDeint = true;
          // Check for possible CC
          String primVid = cf.getPrimaryVideoFormat();
          if (primVid != null && primVid.indexOf("MPEG") != -1)
          {
            cmdOpt2 += " -subcc -printcc";
          }
          sage.media.format.AudioFormat audForm = null;
          if (cf.getNumAudioStreams() > 1)
          {
            audForm = cf.getAudioFormat();
            if (sage.media.format.MediaFormat.MPEG2_PS.equals(mf.getContainerFormat()) ||
                sage.media.format.MediaFormat.MPEG2_TS.equals(mf.getContainerFormat()))
            {
              if (sage.media.format.MediaFormat.AC3.equals(audForm.getFormatName()))
                audioID = 0x80 + audForm.getOrderIndex();
              else
                audioID = audForm.getOrderIndex();
            }
            else
            {
              audioIDOffset = cf.getAudioFormats()[0].getOrderIndex();
              audioID = audForm.getOrderIndex() - audioIDOffset;
            }
          }

          // check for h.264 in TS
          if(vidForm != null) {
            if(sage.media.format.MediaFormat.MPEG2_TS.equals(cf.getFormatName()) &&
                sage.media.format.MediaFormat.H264.equals(vidForm.getFormatName()))
            {
              useLAVFDemux = true;
            }
          }
          audioTracks = cf.getAudioFormats();
          if (audForm != null)
          {
            for (int i = 0; i < audioTracks.length; i++)
            {
              if (audioTracks[i] == audForm)
              {
                languageIndex = i;
                break;
              }
            }
          }
          pendingSubpicTracks = cf.getSubpictureFormats();
          subpicIndex = 0;
          subpicOn = false;
          if (Sage.client && pendingSubpicTracks != null && pendingSubpicTracks.length > 0)
          {
            // Add access to any external VOBSUB subtitle tracks
            if (sage.media.format.MediaFormat.VOBSUB.equals(pendingSubpicTracks[0].getFormatName()) &&
                pendingSubpicTracks[0].getPath() != null)
            {
              releaseServerAccessVobSubBase = pendingSubpicTracks[0].getPath().substring(0, pendingSubpicTracks[0].getPath().lastIndexOf('.'));
              NetworkClient.getSN().requestMediaServerAccess(new java.io.File(releaseServerAccessVobSubBase + ".idx"), true);
              NetworkClient.getSN().requestMediaServerAccess(new java.io.File(releaseServerAccessVobSubBase + ".sub"), true);
            }
          }
        }
      }
      if (Sage.getBoolean("mplayer/call_hooks_on_warnings", false))
        cmdOpt2 += " -v";
      if (useLAVFDemux)
        cmdOpt2 += " -demuxer lavf";
      if (doPP && needsDeint)
        cmdOpt2 += " -vf fspp,pp=fd";
      else if (doPP)
        cmdOpt2 += " -vf pp7";
      else if (needsDeint)
        cmdOpt2 += " -vf pp=fd";
      if (audioID >= 0)
        cmdOpt2 += " -aid " + audioID;
      usingEavios = false;
      java.util.StringTokenizer toker = new java.util.StringTokenizer(cmdOpts1 + ' ' +  cmdOpt2, " ");
      while (toker.hasMoreTokens())
        cmds.add(toker.nextToken());
      if (timeshifted)
        cmds.add("-activefile");
      if (bufferSize > 0)
      {
        if (hostname == null)
          hostname = Sage.client ? Sage.preferredServer : SageTV.hostname;
        // 5/20/08 - Narflex - I know that circular files have to go through the server so don't even let MPlayer
        // try to play them back by itself
        //				cmds.add("-circularfilesize");
        //				cmds.add(Long.toString(bufferSize));
      }

      if (majorTypeHint == MediaFile.MEDIATYPE_DVD)
        cmds.add("dvd://");
      else if (hostname != null && hostname.length() > 0 && Sage.WINDOWS_OS)
        cmds.add("\"stv://" + hostname + "/" + file.getPath() + "\"");
      else if (hostname != null && hostname.length() > 0)
        cmds.add("stv://" + hostname + "/" + file.getPath());
      else if (Sage.WINDOWS_OS)
        cmds.add("\"" + file.getPath() + "\"");
      else
        cmds.add(file.getPath());

      if ("TRUE".equals(Sage.get("mplayer/cmdline_debug", null))) System.out.println("Executing mplayer cmd: " + cmds);
      /*
			if (Sage.WINDOWS_OS)
			{
				cmds.add(Sage.get("mplayer_cmd", "mplayer.exe"));
				cmdOpts1 = Sage.get("mplayer_options1", "-slave -identify -autosync 30 -osdlevel 0");
				deintOpts = shouldDeinterlace(majorTypeHint, minorTypeHint) ? Sage.get("mplayer_deinterlace_option", "-vf pp=fd") : "";
				cmdOpt2 = Sage.get("mplayer_options2", "-vo directx -framedrop -cache 2048 -cache-seek-min 0");
				cmdOpt2 = cmdOpt2.replaceAll("cache-prefill", "cache-seek-min");
				// Setup the color key parameter for the overlay
				if (cmdOpt2.indexOf("directx") != -1)
				{
					int x = cmdOpt2.indexOf("directx") + "directx".length();
					if (cmdOpt2.charAt(x) == ':')
						cmdOpt2 = cmdOpt2.substring(0, x + 1) + "colorkey=0x" + Integer.toString(getColorKey().getRGB(), 16) + "," +
							cmdOpt2.substring(x + 1);
					else
						cmdOpt2 = cmdOpt2.substring(0, x) + ":colorkey=" + Integer.toString(getColorKey().getRGB()) + "," +
							cmdOpt2.substring(x);
				}
				cmdOpt2 += " -wid " + VideoFrame.getVideoFrameForPlayer(this).getVideoHandle();
				usingEavios = false;
			}
			else
			{
				cmds.add(Sage.get("linux/mplayer_cmd", "nice"));
				cmdOpts1 = Sage.get("linux/mplayer_options1", "-n -10 ./mplayer -slave -identify -autosync 30 -osdlevel 0");
				deintOpts = shouldDeinterlace(majorTypeHint, minorTypeHint) ? Sage.get("linux/mplayer_deinterlace_option", "-vf pp=fd") : "";
				cmdOpt2 = Sage.get("linux/mplayer_options2", "-vo eavios -ao alsa:device=hw -framedrop -cache 2048 -cache-seek-min 0 -nolirc");
				cmdOpt2 = cmdOpt2.replaceAll("cache-prefill", "cache-seek-min");
				String currAudioPort = Sage.get("linux/audio_output_port", "Analog");
				if ("SPDIF".equals(currAudioPort))
				{
					cmdOpt2 = Sage.get("linux/mplayer_options2spdif", "-vo eavios -ao alsa:device=iec958 -framedrop -cache 2048 -cache-seek-min 0 -nolirc");
					if ("VWB".equals(SageTV.system))
					{
						// Do the GPIO switching for SPDIF
						IOUtils.exec2("./spdifsw", false);
					}
				}
				usingEavios = (cmdOpts1.indexOf("eavios") != -1) || (cmdOpt2.indexOf("eavios") != -1);
			}
			java.util.StringTokenizer toker = new java.util.StringTokenizer(cmdOpts1 + ' ' + deintOpts + ' ' + cmdOpt2, " ");
			while (toker.hasMoreTokens())
				cmds.add(toker.nextToken());
			if (timeshifted)
				cmds.add("-activefile");
			cmds.add("-nokeepaspect");
			if (bufferSize > 0)
			{
				cmds.add("-circularfilesize");
				cmds.add(Long.toString(bufferSize));
			}

			if (Sage.DBG) System.out.println("Executing mplayer cmd: " + cmds);*/
      mpProc = Runtime.getRuntime().exec((String[])cmds.toArray(new String[0]));
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("FAILED executing mplayer exception:" + e);
      throw new PlaybackException();
    }

    currState = LOADED_STATE;

    currHintMajorType = majorTypeHint;
    currHintMinorType = minorTypeHint;
    currHintEncoding = encodingHint;
    transparency = BITMASK;

    colorKey = null;
    currCCState = -1;
    videoDimensions = new java.awt.Dimension();
    currFile = file;
    lastMillis = 0;

    // It starts playing right after load
    currState = PLAY_STATE;
    muteState = Sage.WINDOWS_OS;
    fileDeactivated = false;

    if (Sage.DBG) System.out.println("MPlayer was launched successfully");
    Thread the = new Thread("InputStreamConsumer")
    {
      public void run()
      {
        try
        {
          boolean debugMplayer = Sage.getBoolean("mplayer_debug", false);
          java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
              mpProc.getInputStream()));
          String s;
          boolean warningsAreErrors = Sage.getBoolean("mplayer/call_hooks_on_warnings", false);
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
                  (!usingEavios || currHintMajorType == MediaFile.MEDIATYPE_AUDIO))
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
                }
              }
              else if (s.startsWith("V:"))
              {
                if (mplayerLoaded)
                {
                  // video timestamp for curr time
                  lastMillis = Math.round(Float.parseFloat(s.substring(2, 9).trim())*1000);
                  statusUpdate = true;
                }
              }
              else if (s.startsWith("MUTED="))
              {
                muteState = s.charAt(6) != '0';
                statusUpdate = true;
              }
              else if (s.startsWith("VOLUME="))
              {
                float parsedF = Float.parseFloat(s.substring(7));
                if (Sage.WINDOWS_OS)
                  currVolume = (float)(((1.0f/(VOLUME_LOG_SHAPING-1)) * (Math.pow(VOLUME_LOG_SHAPING, parsedF/100.0f) - 1)));
                else
                  currVolume = (float)(parsedF / 100.0f);
                statusUpdate = true;
              }
              else if (s.startsWith("EOF code"))
              {
                if (mplayerLoaded)
                {
                  if (Sage.DBG) System.out.println("EOF Code read from stream, setting EOS flag");
                  eos = true;
                  statusUpdate = true;
                  VideoFrame.getVideoFrameForPlayer(LinuxMPlayerPlugin.this).kick(); // so it sees the EOS
                }
              }
              else if (s.startsWith("  =====  PAUSE"))
              {
                if (mplayerLoaded && !eos && currState == PLAY_STATE) // pauses while in EOS are just stream exhaustion, not actual pausing
                {
                  currState = PAUSE_STATE;
                }
                statusUpdate = true;
                pauseCount++;
                // just don't let it overflow
                if (pauseCount > 1000000)
                  pauseCount = 0;
              }
              else if (s.startsWith("DURATION="))
              {
                realDurMillis = Math.round(Float.parseFloat(s.substring("DURATION=".length()).trim())*1000) - initialPTSmsec;
                statusUpdate = true;
              }
              else if (s.startsWith("DEMUXSEEKSTART"))
              {
                eos = false;
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
              else if (s.startsWith("ID_SUBTITLE_ID=") || s.startsWith("ID_VOBSUB_ID="))
              {
                // Enable the subpicture selection since MPlayer sees them
                if (pendingSubpicTracks != null)
                {
                  subpicTracks = pendingSubpicTracks;
                  pendingSubpicTracks = null;
                }
              }
              else if (s.startsWith("ID_EXIT="))
              {
                // mplayer has exited for some reason
                System.out.println("MPLAYER terminated("+s.substring("ID_EXIT=".length()).trim()+") - mark the load as failed.");
                loadFailed = true;
                statusUpdate = true;
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
                  ccData[i*8 + 4] = sage.media.sub.CCSubtitleHandler.TOP_FIELD_CC12;
                  ccData[i*8 + 5] = (byte)(Integer.parseInt(toker.nextToken(), 16) & 0xFF);
                  ccData[i*8 + 6] = (byte)(Integer.parseInt(toker.nextToken(), 16) & 0xFF);
                }
                VideoFrame.getVideoFrameForPlayer(LinuxMPlayerPlugin.this).postSubtitleInfo(0, 0, ccData, sage.media.sub.SubtitleHandler.CC_SUBTITLE_MASK);
              }
              else if (warningsAreErrors && s.startsWith("DSound: Stopping audio to prevent looping"))
              {
                Catbert.processUISpecificHook("MediaPlayerError", new Object[] { Sage.rez("Playback"), Sage.rez("UNDERFLOW") },
                    uiMgr, true);
              }
              else if (warningsAreErrors &&
                  (s.startsWith("<vo_directx><WARN>Your card supports overlay, but we couldn't create one") ||
                      s.startsWith("<vo_directx><FATAL") ||
                      s.startsWith("<vo_directx><ERROR")))
              {
                loadFailed = true;
                failureCause = new PlaybackException()
                {
                  public String toString() { return Sage.rez("OVERLAY_IN_USE"); }
                };
              }
              else if (s.indexOf("MPlayer crashed") != -1)
              {
                System.out.println("MPLAYER CRASHED - mark the load as failed.");
                loadFailed = true;
                statusUpdate = true;
              }
              else if (Sage.DBG && !debugMplayer) System.out.println("stdout:" + s);
            }
            catch (Exception e)
            {
              System.out.println("ERROR parsing mplayer status of:" + e);
            }

            if (debugMplayer && Sage.DBG) System.out.println("stdout:" + s);
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
        if (Sage.DBG) System.out.println("stdout consumer has closed");

        // Check if we wanted this to happen or not
        synchronized (LinuxMPlayerPlugin.this)
        {
          if (currState == PLAY_STATE || currState == PAUSE_STATE)
          {
            // Only reload it if it's a good file.
            if (currFile != null && currFile.isFile() && currFile.length() > 0)
            {
              System.out.println("MPlayer quit unexpectedly....reloading the file...");
              boolean waitForWakeup = SageTV.isPoweringDown() && !Sage.getBoolean("ui/close_media_player_on_standby", true);
              if (Sage.DBG && waitForWakeup) System.out.println("System is going into standby; wait for it to wakeup to restart the media player");
              while (waitForWakeup && SageTV.isPoweringDown())
              {
                try{Thread.sleep(250);}catch(Exception e){}
              }
              VideoFrame.getVideoFrameForPlayer(LinuxMPlayerPlugin.this).reloadFile();
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
          boolean warningsAreErrors = Sage.getBoolean("mplayer/call_hooks_on_warnings", false);
          while ((s = buf.readLine()) != null)
          {
            if (Sage.DBG) System.out.println("stderr:" + s);
            if (warningsAreErrors &&
                (s.startsWith("<vo_directx><WARN>Your card supports overlay, but we couldn't create one") ||
                    s.startsWith("<vo_directx><FATAL") ||
                    s.startsWith("<vo_directx><ERROR")))
            {
              loadFailed = true;
              failureCause = new PlaybackException()
              {
                public String toString() { return Sage.rez("OVERLAY_IN_USE"); }
              };
            }
            else if (s.indexOf("MPlayer crashed") != -1)
            {
              System.out.println("MPLAYER CRASHED - mark the load as failed.");
              loadFailed = true;
              synchronized (stdoutLock)
              {
                stdoutLock.notifyAll();
              }
            }
          }
          buf.close();
        }
        catch (Exception e){}
        if (Sage.DBG) System.out.println("stderr consumer has closed");
      }
    };
    the.setDaemon(true);
    the.start();
    //		mpStdout = new java.io.BufferedReader(new java.io.InputStreamReader(mpProc.getInputStream()));
    //		mpStderr = new java.io.BufferedReader(new java.io.InputStreamReader(mpProc.getErrorStream()));
    mpStdin = new java.io.PrintWriter(new java.io.BufferedOutputStream(mpProc.getOutputStream()), true);

    //initialPTSmsec = MPEGUtils.getMPEG2InitialTimestamp(file);
    if (!waitForMPlayerToFinishLoad())
    {
      if (failureCause != null)
        throw failureCause;
      else
        throw new PlaybackException();
    }

    // Trigger a volume refresh
    setVolume(uiMgr.getBoolean("media_player_uses_system_volume", Sage.WINDOWS_OS && !Sage.VISTA_OS) ? 1.0f : uiMgr.getFloat("mplayer/last_volume", 1.0f));
    //		sendCommand("volume 0");
    waitForPlaybackToPass(0);
  }

  private boolean waitForMPlayerToFinishLoad()
  {
    if (Sage.DBG) System.out.println("Waiting for MPlayer to finish loading");
    long startTime = Sage.eventTime();
    while (!loadFailed && !mplayerLoaded && Sage.eventTime() - startTime < 30000)
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
    if (Sage.DBG) System.out.println("Done waiting for MPlayer to finish loading");
    if (!loadFailed && !isMPlayerRunning())
      loadFailed = true;
    return !loadFailed;
  }

  private void waitForPlaybackToPass(long time)
  {
    if (Sage.DBG) System.out.println("Waiting for MPlayer to pass time:" + time);
    long startTime = Sage.eventTime();
    while ((lastMillis <= time) && Sage.eventTime() - startTime < 5000)
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
    if (Sage.DBG) System.out.println("Done waiting for MPlayer to pass time:" + time);
  }

  private void waitForSeekToStart()
  {
    if (Sage.DBG) System.out.println("Waiting for MPlayer to start seek");
    long startTime = Sage.eventTime();
    while (!seekProcessed && Sage.eventTime() - startTime < 5000)
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
    if (Sage.DBG) System.out.println("Done waiting for MPlayer to start seek");
  }

  private void waitToEnterPauseState()
  {
    if (Sage.DBG) System.out.println("Waiting for MPlayer to pause");
    long startTime = Sage.eventTime();
    while (currState != PAUSE_STATE && Sage.eventTime() - startTime < 5000)
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
    if (Sage.DBG) System.out.println("Done waiting for MPlayer to pause");
  }

  // This is for frame stepping feedback control
  private void waitForNextPause(int pauseCountCheck)
  {
    if (Sage.DBG) System.out.println("Waiting for MPlayer to re-pause");
    long startTime = Sage.eventTime();
    while (pauseCountCheck == pauseCount && Sage.eventTime() - startTime < 5000)
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
    if (Sage.DBG) System.out.println("Done waiting for MPlayer to re-pause");
  }

  public boolean pause()
  {
    if (currState == PLAY_STATE && !eos)
    {
      synchronized (this)
      {
        sendCommand("pause");
        waitToEnterPauseState();
      }
    }
    return currState == PAUSE_STATE;
  }

  public boolean play()
  {
    if (currState == PAUSE_STATE && !eos)
    {
      synchronized (this)
      {
        sendCommand("pause");
        currState = PLAY_STATE;
        lastMillis--; // so an update of the current time works also
        waitForPlaybackToPass(lastMillis);
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
        long currMediaTime = getMediaTimeMillis();
        if (Math.abs(currMediaTime - seekTimeMillis) < 500)
        {
          return seekTimeMillis;
        }
        timeGuessMillis = seekTimeMillis;
        guessTimestamp = Sage.eventTime();
        eos = false;
        seekProcessed = false;
        boolean pausedSeek = currState == PAUSE_STATE;
        boolean muteStep = !muteState;
        if (pausedSeek && muteStep)
          setMute(true);
        int startPauseCount = pauseCount;
        // Since we've updated the mplayer seeking code to work better, we should always use absolute times
        sendCommand((pausedSeek ? "pausing " : "") + "seek " + (seekTimeMillis + initialPTSmsec)/1000.0 + " 2");
        waitForSeekToStart();
        lastMillis = seekTimeMillis + initialPTSmsec;
        if (pausedSeek)
        {
          // It'll have another pause after the seek
          waitForNextPause(startPauseCount);
          // Then we do a few frame steps so that we clear out the video buffers and show the new position
          int numPausedSeekSteps = 3;
          while (numPausedSeekSteps-- > 0)
          {
            frameStep(1);
          }
          if (muteStep)
            setMute(false);
        }
        return seekTimeMillis;
      }
    }
    return 0;
  }

  public boolean setClosedCaptioningState(int ccState)
  {
    return false;
  }

  public void setMute(boolean x)
  {
    synchronized (this)
    {
      if (muteState != x)
      {
        //				if (currState != PAUSE_STATE)
        {
          sendCommand("mute");
          muteState = x;
        }
      }
    }
  }

  public float setPlaybackRate(float newRate)
  {
    return 1;
  }

  public void setVideoRectangles(final java.awt.Rectangle videoSrcRect, final java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    if (updateVideoRect)
    {
      /*if (uiMgr.getRootPanel().getRenderEngine() instanceof JOGLSageRenderer)
			{
				if (mpStdin == null) return;
				final String cmd = "video_out_rectangles " + videoSrcRect.x + " " + videoSrcRect.y + " " + videoSrcRect.width + " " + videoSrcRect.height +
								" " + videoDestRect.x + " " + videoDestRect.y + " " + videoDestRect.width + " " + videoDestRect.height;
				Thread t = new Thread("PlayerSendVRCmd")
				{
					public void run()
					{
						if (Sage.DBG) System.out.println("Sending mplayer command:" + cmd);
						synchronized (mpStdin)
						{
							mpStdin.println(cmd);
						}
					}
				};
				t.setPriority(Thread.currentThread().getPriority());
				t.setDaemon(true);
				t.start();
			}
			else*/
      {
        synchronized (this)
        {
          sendCommand("video_out_rectangles " + videoSrcRect.x + " " + videoSrcRect.y + " " + videoSrcRect.width + " " + videoSrcRect.height +
              " " + videoDestRect.x + " " + videoDestRect.y + " " + videoDestRect.width + " " + videoDestRect.height);
        }
      }
    }
  }

  public synchronized float setVolume(float f)
  {
    //if (currState != PAUSE_STATE)
    {
      float convertF;
      // Make sure we're actually trying to change it
      int oldVolume;
      if (Sage.WINDOWS_OS)
        oldVolume = Math.round((float)(Math.floor(100.0f * Math.log((f*(VOLUME_LOG_SHAPING-1)) + 1)/Math.log(VOLUME_LOG_SHAPING))));
      else
        oldVolume = Math.round((float)Math.floor(100.0f * f));
      if (Sage.WINDOWS_OS)
        convertF = (float)(Math.floor(100.0f * Math.log((f*(VOLUME_LOG_SHAPING-1)) + 1)/Math.log(VOLUME_LOG_SHAPING)));
      else
        convertF = (float)Math.floor(100.0f * f);
      int newVolume = Math.round(convertF);
      if (f > currVolume && newVolume == oldVolume && newVolume < 100)
        newVolume++;
      if (f < currVolume && newVolume == oldVolume && newVolume > 0)
        newVolume--;
      sendCommand("volume " + newVolume + " 1");
      currVolume = f;
    }
    return currVolume;
  }

  public void stop()
  {
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

  // NOTE: MPlayer can get hung on this call if it has a source underflow. So we rethread it to prevent hanging of calls into
  // the MediaPlayer
  private synchronized void sendCommand(String cmd)
  {
    if (mpStdin == null) return;
    if (sendCmdQueue != null)
    {
      synchronized (sendCmdQueue)
      {
        sendCmdQueue.add(cmd);
        sendCmdQueue.notifyAll();
      }
      return;
    }
    else
    {
      sendCmdQueue = new java.util.Vector();
      sendCmdQueue.add(cmd);
      Thread t = new Thread("PlayerSendCmd")
      {
        public void run()
        {
          try
          {
            while (mpStdin != null)
            {
              synchronized (sendCmdQueue)
              {
                if (sendCmdQueue.isEmpty())
                {
                  try
                  {
                    sendCmdQueue.wait(500);
                  }
                  catch (InterruptedException e){}
                  continue;
                }
              }
              String currCmd = (String) sendCmdQueue.remove(0);
              if (Sage.DBG) System.out.println("Sending mplayer command:" + currCmd);
              synchronized (mpStdin)
              {
                mpStdin.println(currCmd);
              }
            }
          }
          finally
          {
            sendCmdQueue = null;
          }
        }
      };
      t.setPriority(Thread.currentThread().getPriority());
      t.setDaemon(true);
      t.start();
    }
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

  public boolean playControlEx(int playCode, long param1, long param2) throws PlaybackException
  {
    if ((currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE))
    {
      if (audioTracks != null && playCode == VideoFrame.DVD_CONTROL_AUDIO_CHANGE)
      {
        int newLanguageIndex = param1 >= 0 ? (int)param1 : ((languageIndex + 1) % audioTracks.length);
        if (newLanguageIndex != languageIndex)
        {
          languageIndex = Math.max(0, Math.min(audioTracks.length - 1, newLanguageIndex));
          synchronized (this)
          {
            sage.media.format.AudioFormat af = audioTracks[languageIndex];
            int audioID;
            if (sage.media.format.MediaFormat.MPEG2_PS.equals(mf.getContainerFormat()) ||
                sage.media.format.MediaFormat.MPEG2_TS.equals(mf.getContainerFormat()))
            {
              if (sage.media.format.MediaFormat.AC3.equals(af.getFormatName()))
                audioID = 0x80 + af.getOrderIndex();
              else
                audioID = af.getOrderIndex();
            }
            else
              audioID = af.getOrderIndex() - audioIDOffset;
            if (Sage.DBG) System.out.println("Setting audio stream for playback to be ID=" + audioID);
            if (audioID >= 0)
              sendCommand("switch_audio " + audioID);
          }
        }
      }
      else if (subpicTracks != null && playCode == VideoFrame.DVD_CONTROL_SUBTITLE_TOGGLE)
      {
        subpicOn = !subpicOn;
        synchronized (this)
        {
          if (subpicOn)
          {
            int subID = subpicTracks[subpicIndex].getOrderIndex();
            if (Sage.DBG) System.out.println("Enabling subpicture stream " + subID);
            sendCommand("sub_select " + subID);
          }
          else
          {
            if (Sage.DBG) System.out.println("Disabling subpicture stream");
            sendCommand("sub_select -1");
          }
        }
      }
      else if (subpicTracks != null && playCode == VideoFrame.DVD_CONTROL_SUBTITLE_CHANGE)
      {
        int newSubpicIndex = param1 >= 0 ? (int)param1 : ((subpicIndex + 1) % subpicTracks.length);
        if (newSubpicIndex != subpicIndex)
        {
          subpicIndex = Math.max(0, Math.min(subpicTracks.length - 1, newSubpicIndex));
          if (!subpicOn)
            subpicOn = true;
          int subID = subpicTracks[subpicIndex].getOrderIndex();
          synchronized (this)
          {
            if (Sage.DBG) System.out.println("Enabling subpicture stream " + subID);
            sendCommand("sub_select " + subID);
          }
        }
      }
    }
    return false;
  }

  public boolean areDVDButtonsVisible()
  {
    return false;
  }

  public int getDVDAngle()
  {
    return 0;
  }

  public String[] getDVDAvailableLanguages()
  {
    if (audioTracks != null && (currState == PLAY_STATE || currState == PAUSE_STATE))
    {
      MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
      if (mf != null)
      {
        sage.media.format.ContainerFormat cf = mf.getFileFormat();
        if (cf != null && cf.getNumAudioStreams() > 1)
        {
          return cf.getAudioStreamSelectionDescriptors();
        }
      }
    }
    return Pooler.EMPTY_STRING_ARRAY;
  }

  public String[] getDVDAvailableSubpictures()
  {
    if (subpicTracks != null && (currState == PLAY_STATE || currState == PAUSE_STATE))
    {
      MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
      if (mf != null)
      {
        sage.media.format.ContainerFormat cf = mf.getFileFormat();
        if (cf != null && cf.getNumSubpictureStreams() > 0)
        {
          return cf.getSubpictureStreamSelectionDescriptors();
        }
      }
    }
    return Pooler.EMPTY_STRING_ARRAY;
  }

  public int getDVDChapter()
  {
    return 0;
  }

  public int getDVDTotalChapters()
  {
    return 0;
  }

  public int getDVDDomain()
  {
    return 0;
  }

  public String getDVDLanguage()
  {
    if (audioTracks != null && (currState == PLAY_STATE || currState == PAUSE_STATE))
    {
      MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
      if (mf != null)
      {
        sage.media.format.ContainerFormat cf = mf.getFileFormat();
        if (cf != null && cf.getNumAudioStreams() > 1)
        {
          String[] audioStreams = cf.getAudioStreamSelectionDescriptors();
          return audioStreams[Math.min(audioStreams.length - 1, Math.max(0, languageIndex))];
        }
      }
    }
    return "";
  }

  public String getDVDSubpicture()
  {
    if (subpicTracks != null && (currState == PLAY_STATE || currState == PAUSE_STATE))
    {
      if (!subpicOn) return null;
      MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
      if (mf != null)
      {
        sage.media.format.ContainerFormat cf = mf.getFileFormat();
        if (cf != null && cf.getNumSubpictureStreams() > 1)
        {
          String[] subpicStreams = cf.getSubpictureStreamSelectionDescriptors();
          return subpicStreams[Math.min(subpicStreams.length - 1, Math.max(0, subpicIndex))];
        }
      }
    }
    return "";
  }

  public int getDVDTitle()
  {
    return 0;
  }

  public int getDVDTotalAngles()
  {
    return 0;
  }

  public int getDVDTotalTitles()
  {
    return 0;
  }

  public float getCurrentAspectRatio()
  {
    return 0;
  }

  private Process mpProc;
  private java.io.BufferedReader mpStdout;
  private java.io.PrintWriter mpStdin;
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
  protected float currVolume;
  protected boolean eos;

  protected long initialPTSmsec;

  protected boolean mplayerLoaded;

  protected boolean fileDeactivated;

  protected Object stdoutLock = new Object();

  protected long realDurMillis;
  protected boolean seekProcessed;

  protected boolean usingEavios;

  protected String audioOutputPort = "";

  protected boolean loadFailed;
  protected boolean updateVideoRect;

  protected boolean canDoFastLoad;
  protected long timeGuessMillis;
  protected long guessTimestamp;

  protected boolean blender;

  protected UIManager uiMgr;

  private java.util.Vector sendCmdQueue;

  private int pauseCount;
  private PlaybackException failureCause;

  private int languageIndex;
  private sage.media.format.AudioFormat[] audioTracks;
  private int subpicIndex;
  private boolean subpicOn;
  // MPlayer uses zero based audio IDs for non-MPEG files; so this offset handles that case
  private int audioIDOffset;
  // In case MPlayer can't handle the subs
  private sage.media.format.SubpictureFormat[] pendingSubpicTracks;
  private sage.media.format.SubpictureFormat[] subpicTracks;

  private MediaFile mf;

  private String releaseServerAccessVobSubBase;
  private boolean launchedAsyncRenderThread;
}
