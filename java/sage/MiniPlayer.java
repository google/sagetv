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

/**
 *
 * @author  Narflex modified by JFT for STB chip
 */
public class MiniPlayer implements DVDMediaPlayer
{
  protected static final long GUESS_VALIDITY_DURATION = 1000;
  protected static final boolean ENABLE_DSM520_HACKS = Sage.getBoolean("enable_miniplayer_hacks", false);

  private static final int NUM_SAMPLES_BANDWIDTH_ESTIMATE = 5;
  private static final int NUM_SAMPLES_AVERAGE_BANDWIDTH_ESTIMATE = 20;

  private static final int MIN_DYNAMIC_VIDEO_BITRATE_KBPS = 50;
  private static final int BANDWIDTH_BUFFER_KBPS = 50; // increased from 30 because our new algorithm is more aggressive

  private java.nio.channels.SocketChannel clientSocket;
  private FastPusherReply clientInStream;
  private java.nio.ByteBuffer sockBuf = java.nio.ByteBuffer.allocateDirect(65536);

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

  public static final int MEDIACMD_DVD_STREAM = 36;
  public static final int MEDIACMD_DVD_NEWCELL = 32;
  public static final int MEDIACMD_DVD_CLUT = 33;

  public static final int PUSHBUFFER_SUBPIC_FLAG = 0x40;
  public static final int PUSHBUFFER_SUBPIC_PAL_FLAG = 0x200;
  public static final int SUBPIC_DISABLE_STREAM = 0x2000;
  public static final int PS_SUBPIC_DISABLE_STREAM = 0xBD3F;

  public MiniPlayer()
  {
    currState = NO_STATE;

  }

  protected boolean shouldPush(byte majorTypeHint, byte minorTypeHint)
  {
    //        if (minorTypeHint == MediaFile.MEDIASUBTYPE_MPEG2_PS) // || minorTypeHint == MediaFile.MEDIASUBTYPE_MPEG2_TS
    return true;
    //        else
    //            return false;
  }

  public boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file)
  {
    // They'll be the same type because that's already checked in VideoFrame
    // 8/28/08 - Don't do fast loading at all; we don't properly setup the new MpegReader in the
    // fastLoad method below since we've customized it so much in the main load method.
    return pushMode && !lowBandwidth && !serverSideTranscoding && downer == null;
  }

  public synchronized void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException
  {
    if (Sage.DBG) System.out.println("Mini Fast Load");
    int lastState = currState;
    addYieldDecoderLock();
    synchronized (decoderLock)
    {
      //			pushThreadCreated = false;

      if (tcSrc != null)
        tcSrc.close();
      if (mpegSrc != null)
        mpegSrc.close();
      tcSrc = null;
      mpegSrc = null;
      // rebuild the source
      //        currState = NO_STATE;
      //      timeGuessMillis = 0;
      //        guessTimestamp = Sage.eventTime();
      //        myRate = 1;
      eos = false;
      firstSeek = true;
      sendSeekPullNext = false;
      wasFastSwitch = true;
      boolean useMP3StreamWrapper = false;

      if (transcoded && minorTypeHint == MediaFile.MEDIASUBTYPE_MP3)
        useMP3StreamWrapper = true;

      this.timeshifted = timeshifted;
      if (bufferSize > 0 && hostname == null)
      {
        // Circular files don't work correctly with the MPEG2 pushers because they don't understand that concept. This is fixed by
        // having them go through the MediaServer which DOES understand circular files.
        if (Sage.DBG) System.out.println("MiniPlayer is going through the MediaServer to handle the circular file.");
        hostname = "localhost";
      }
      // For MP3 files we use JF's transcode wrapper; and for video & non-MP3 audio we use the media server's transcoder
      if (useMP3StreamWrapper)
      {
        if (Sage.DBG) System.out.println("MiniPlayer is using the MP3 stream wrapper");
        tcSrc = new Mpeg2Transcoder(file, hostname);
      }
      else if (rpSrc == null)
      {
        if (Sage.DBG) System.out.println("MiniPlayer is using the MPEG2 pusher");
        mpegSrc = new FastMpeg2Reader(file, hostname);
        mpegSrc.setActiveFile(timeshifted);
        MediaFile currMF = VideoFrame.getMediaFileForPlayer(MiniPlayer.this);
        sage.media.format.ContainerFormat currFileFormat = currMF.getFileFormat();
        if (currFileFormat != null && "true".equals(currFileFormat.getMetadataProperty("VARIED_FORMAT")))
          currFileFormat = sage.media.format.FormatParser.getFileFormat(file);
        mpegSrc.setStreamTranscodeMode(null, currFileFormat);
        if (currFileFormat != null && Sage.getBoolean("miniplayer/align_iframes_on_seek", true))
          mpegSrc.setIFrameAlign(true);
      }
      try
      {
        if(transcoded)
        {
          tcSrc.init(true, !timeshifted);
        }
        else if (rpSrc != null)
        {
          if (Sage.DBG) System.out.println("MiniPlayer is using the RemotePusher");
          MediaFile currMF = VideoFrame.getMediaFileForPlayer(MiniPlayer.this);
          sage.media.format.ContainerFormat currFileFormat = currMF.getFileFormat();
          if (currFileFormat != null && "true".equals(currFileFormat.getMetadataProperty("VARIED_FORMAT")))
            currFileFormat = sage.media.format.FormatParser.getFileFormat(file);
          rpSrc.openFile(file.getAbsolutePath(), (currFileFormat == null) ? "" : currFileFormat.getFullPropertyString(false), timeshifted, true);
        }
        else
        {
          mpegSrc.init(true, !timeshifted, usingRemuxer);
        }
      }
      catch (java.io.IOException e)
      {
        System.out.println("Error initing MPEG2 stream:" + e);
        e.printStackTrace();
        throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
      }
      if (!timeshifted && !transcoded && rpSrc == null)
        finalLength = mpegSrc.length();
      currFile = file;
      //			flushPush0();
      removeYieldDecoderLock();
      decoderLock.notifyAll();
    }
    //       currState = LOADED_STATE;

    //       currHintMajorType = majorTypeHint;
    //        currHintMinorType = minorTypeHint;
    //        currHintEncoding = encodingHint;

    //		languageIndex = 0;
    // For extenders, set the correct audio stream we're using for playback
    /*		if ((mediaExtender && pushMode) || hdMediaExtender)
		{
			sage.media.format.ContainerFormat cf = VideoFrame.getMediaFileForPlayer(this).getFileFormat();
			if (cf != null && cf.getNumAudioStreams() > 0)
			{
				sage.media.format.AudioFormat af = cf.getAudioFormat();
				int audioStreamType = 0xc000;
				// If we're transcoding then the original audio stream doesn't matter, just use 0xc0
				// unless we're using the remuxer....
     *///				if (af != null && ((!serverSideTranscoding && !transcoded)/* || (mpegSrc != null && mpegSrc.getTranscoder() instanceof RemuxTranscodeEngine)*/))
    /*				{
					String streamID = af.getId();
					if (streamID != null && streamID.length() > 0)
					{
						// See if it's just a stream ID or if it's 2 parts
						int dashIdx = streamID.indexOf('-');
						if (dashIdx == -1)
						{
							try
							{
								audioStreamType = (Integer.parseInt(streamID, 16) << 8);
							}
							catch (NumberFormatException nfe)
							{
								if (Sage.DBG) System.out.println("ERROR parsing audio stream ID " + streamID + " of: " + nfe);
							}
						}
						else
						{
							try
							{
								audioStreamType = (Integer.parseInt(streamID.substring(0, dashIdx), 16) << 8) |
									Integer.parseInt(streamID.substring(dashIdx + 1, dashIdx + 3), 16);
							}
							catch (NumberFormatException nfe)
							{
								if (Sage.DBG) System.out.println("ERROR parsing audio stream ID " + streamID + " of: " + nfe);
							}
						}
					}
				}
				audioTracks = cf.getAudioFormats();
				if (af != null)
				{
					for (int i = 0; i < audioTracks.length; i++)
					{
						if (audioTracks[i] == af)
						{
							languageIndex = i;
							break;
						}
					}
				}
				subpicTracks = cf.getSubpictureFormats();
				subpicIndex = 0;
				subpicOn = false;
				if (pushMode)
				{
					if (Sage.DBG) System.out.println("Setting audio stream for playback to be ID=0x" + Integer.toString(audioStreamType, 16));
					DVDStream(0, audioStreamType);
					if (bdp != null)
					{
						matchBDSubpictureToAudio();
					}
				}
			}
		}
		if (lastState == PLAY_STATE)
			play();
		else
			pause();
     */    }

  public boolean frameStep(int amount)
  {
    if (mediaExtender && currState == PAUSE_STATE && !eos)
    {
      boolean retval = true;
      if (mcsr != null && mcsr.supportsFrameStep())
      {
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          frameStep0(amount);
          removeYieldDecoderLock();
          decoderLock.notifyAll();
        }
      }
      else
      {
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          playPush0();
          removeYieldDecoderLock();
          decoderLock.notifyAll();
        }
        try{Thread.sleep(10);}catch(Exception e){}
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          pausePush0();
          removeYieldDecoderLock();
          decoderLock.notifyAll();
        }
      }
      return retval;
    }
    return false;
  }

  public synchronized void free()
  {
    if (uiMgr != null)
      uiMgr.putFloat("miniplayer/last_volume", curVolume);
    synchronized (decoderLock)
    {
      if (currState != STOPPED_STATE)
        stop();
      currState = NO_STATE;
      // Close the remote pusher source before we do closeDriver0 so that it won't try to push anymore
      // data after the connection is dead. This will have the side effect of the connection
      // being torn down..which is fine as we ignore any errors that occur in closeDriver0 and the
      // miniclient has internal mechanisms to clean itself up when it's shutdown that way.
      if (rpSrc != null)
        rpSrc.close();
      if (Sage.DBG) System.out.println("Closing down MiniPlayer");
      closeDriver0();
    }
    if (clientInStream != null)
    {
      try { clientInStream.close(); } catch(Exception e){}
      clientInStream = null;
    }
    if (clientSocket != null)
    {
      try { clientSocket.close(); } catch(Exception e){}
      clientSocket = null;
    }
    if (tcSrc != null)
      tcSrc.close();
    if (mpegSrc != null)
      mpegSrc.close();
    mpegSrc = null;
    tcSrc = null;
    rpSrc = null;
    currFile = null;
    currHintMajorType = currHintMinorType = (byte)0;
    currHintEncoding = null;
    videoDimensions = null;
    currCCState = 0;
    eos = false;
    timeGuessMillis = 0;
    guessTimestamp = 0;
    timestampOffset = 0;
    serverSideTranscoding = false;
    pushMode = false;
    currMute = false;
    uiMgr = null;
    colorKey = null;
    pushThread = null;
    downer = null;
    timeshifted = false;
    finalLength = 0;
    transcoded = false;
    lastVideoSrcRect = lastVideoDestRect = null;
    myRate = 0;
    freeSpace = 0;
    curVolume = 1.0f;
    maxAvailBufferSize = 0;
    lastRateAdjustTime = 0;
    lastEstimatedPushBitrate = lastAverageEstimatedPushBitrate = 0;
    lastEstimatedStreamBitrate = lastAverageEstimatedStreamBitrate = 0;
    clientReportedPlayState = 0;
    clientReportedMediaTime = 0;
    lastParserTimestamp = 0;
    lastParserTimestampBytePos = 0;

    pushThreadCreated = false;
    needToPlay = false;
    dynamicRateAdjust = false;
    numPushedBuffers = 0;
    sentDiscardPtsFlag = false;
    sentTrickmodeFlag = false;
    lastMediaTime = lastMediaTimeBase = lastMediaTimeCacheTime = 0;
    if (unmountRequired != null)
    {
      java.io.File removeMe = unmountRequired;
      unmountRequired = null;
      FSManager.getInstance().releaseISOMount(removeMe);
    }
  }

  public int getClosedCaptioningState()
  {
    return CC_DISABLED;
  }

  public java.awt.Color getColorKey()
  {
    return colorKey;
  }

  public long getDurationMillis()
  {
    FastMpeg2Reader mySrc = mpegSrc;
    Mpeg2Transcoder mytcSrc = tcSrc;
    long duration;
    if(transcoded == true)
    {
      duration = (mytcSrc == null || timeshifted) ? 0 : mytcSrc.getDurationMillis();
    }
    else if (rpSrc != null)
    {
      duration = timeshifted ? 0 : rpSrc.getDurationMillis();
    }
    else
    {
      duration = (mySrc == null || timeshifted) ? 0 : mySrc.getDurationMillis();
    }
    if (Sage.DBG) System.out.println("getDuration : "+ duration);
    return duration;
  }

  public java.io.File getFile()
  {
    return currFile;
  }

  public synchronized long getMediaTimeMillis()
  {
    long rv;
    if (Sage.eventTime() - guessTimestamp <  GUESS_VALIDITY_DURATION || waitingForSeek) // after seeks it doesn't know the right time at first
    {
      return timeGuessMillis;
    }
    // If the file's being downloaded and the player is waiting for a reseek on the server
    // this call might end up taking a while; so don't do it in that case.
    if (downer != null && downer.isClientWaitingForRead() && guessTimestamp > lastMediaTimeCacheTime && timeGuessMillis > 0)
    {
      return timeGuessMillis;
    }
    if ((transcoded ? tcSrc !=null : mpegSrc != null) && (myRate != 1.0f) && (!hdMediaExtender || bdp != null))
    {
      // We can't trust the Sigma driver in this case so we need to guess from the MPEG parser
      rv = transcoded ? tcSrc.getLastParsedTimeMillis() : mpegSrc.getLastParsedTimeMillis();
      return rv;
    }
    if (detailedPushBufferStats && pushMode && rpSrc == null)
    {
      long currMediaTime = clientReportedMediaTime + timestampOffset;
      if (lastMediaTimeBase == currMediaTime && currState == PLAY_STATE)
      {
        lastMediaTime = (Sage.eventTime() - lastMediaTimeCacheTime) + lastMediaTime;
      }
      else
        lastMediaTime = lastMediaTimeBase = currMediaTime;
      lastMediaTimeCacheTime = Sage.eventTime();
      return lastMediaTime;
    }
    return getNativeMediaTimeNoSync();
  }

  private long getNativeMediaTimeNoSync()
  {
    long nativeTime;
    addYieldDecoderLock();
    synchronized (decoderLock)
    {
      nativeTime = getMediaTimeMillis0();
      removeYieldDecoderLock();
      decoderLock.notifyAll();
    }
    if (nativeTime <= 500 && !hdMediaExtender) // after seeks it doesn't know the right time at first
    {
      return timeGuessMillis;
    }
    else
    {
      long otherTime;
      if(transcoded)
      {
        otherTime = (tcSrc != null ? tcSrc.getFirstTimestampMillis() : 0);
      }
      else if (rpSrc != null)
      {
        otherTime = rpSrc.getFirstPTS() / 90;
        // We added another special case here to autodetect PTS rollover if we
        // see a PTS that is more than 2^33/4 less than the initial PTS. So this should catch cases
        // with durations up to 20 hours in length.
        if (otherTime - nativeTime > 100000 && rpSrc.didPTSRollover() ||
            nativeTime < otherTime - FastMpeg2Reader.MAX_PTS/360)
        {
          otherTime = otherTime - FastMpeg2Reader.MAX_PTS/90;
        }
      }
      else
      {
        otherTime = (mpegSrc != null ? mpegSrc.getFirstTimestampMillis() : 0);
        if (otherTime - nativeTime > 100000 && mpegSrc != null && mpegSrc.didPTSRollover())
        {
          otherTime = otherTime - FastMpeg2Reader.MAX_PTS/90;
        }
        if (mpegSrc != null && mpegSrc.usesCustomTimestamps())
        {
          otherTime -= mpegSrc.getCustomTimestampDiff()/90;
        }
      }
      otherTime -= timestampOffset;
      // max against 0 because there's no reason for these times to ever be negative
      long rv;
      // Also use the first path here for when we do a fast switch and the timestamps will be less then zero while the buffer empties
      if (nativeTime - otherTime > -2000 || wasFastSwitch) // for when the start timestamps aren't as well aligned
        rv = Math.max(0, nativeTime - otherTime);
      else // There's cases where the driver resets the timestamps for no reason
        rv = Math.max(0, nativeTime);
      // There's a case with the custom timestamps where the PTS will rollover in the file, but due to the demux buffering
      // the timestamp offset we're using is looking beyond this. We can however correct for this. :)
      if (mpegSrc != null && mpegSrc.usesCustomTimestamps() && rv > FastMpeg2Reader.MAX_PTS/90)
      {
        rv -= FastMpeg2Reader.MAX_PTS/90;
      }
      //System.out.println("nativeTime=" + nativeTime + " otherTime=" + otherTime + " timestampOffset=" + timestampOffset +
      //	" parserTime=" + (mpegSrc != null ? mpegSrc.getLastParsedTimeMillis() : 0) + " rv=" + rv);
      return rv;
    }
  }

  public boolean getMute()
  {
    return currMute;
  }

  public int getPlaybackCaps()
  {
    return PAUSE_CAP | SEEK_CAP; /* FRAME_STEP_FORWARD_CAP | */
  }

  public float getPlaybackRate()
  {
    return myRate;
  }

  public int getState()
  {
    return eos ? EOS_STATE : currState;
  }

  public boolean hitTrickPlayEOS()
  {
    if (rpSrc != null)
      return rpSrc.isTrickPlayEOS();
    return false;
  }

  public boolean supportsTrickPlayEOS()
  {
    return rpSrc != null;
  }

  public int getTransparency()
  {
    return (colorKey != null) ? BITMASK : TRANSLUCENT;
  }

  public java.awt.Dimension getVideoDimensions()
  {
    return videoDimensions;
  }

  public synchronized float getVolume()
  {
    if (clientSocket == null) return 0;
    return curVolume;
  }

  public void inactiveFile()
  {
    if(transcoded)
    {
      if (tcSrc != null)
        finalLength = tcSrc.length();
    }
    else
    {
      if (mpegSrc != null)
      {
        mpegSrc.setActiveFile(false);
        finalLength = mpegSrc.length();
      }
      else if (rpSrc != null && timeshifted)
      {
        synchronized (decoderLock)
        {
          try
          {
            rpSrc.sendInactiveFile();
          }
          catch (java.io.IOException e)
          {
            if (Sage.DBG) System.out.println("ERROR with RemotePusher communication, kill the UI!");
            e.printStackTrace();
            connectionError();
          }
        }
      }
    }
    timeshifted = serverSideTranscoding;
  }

  public void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    VideoFrame vf = VideoFrame.getVideoFrameForPlayer(MiniPlayer.this);
    MediaFile currMF;
    uiMgr = vf.getUIMgr();
    mcsr = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
    MetaImage.clearHiResNativeCache(mcsr);
    disableVideoPositioning = mcsr.gfxPositionedVideo();
    currMF = VideoFrame.getMediaFileForPlayer(MiniPlayer.this);
    if (FileDownloader.isDownloading(file))
      downer = FileDownloader.getFileDownloader(file);
    else
      downer = null;
    CaptureDevice capDev = currMF.guessCaptureDeviceFromEncoding();
    if (capDev != null && mcsr != null)
    {
      // Enable the special mode for when we are using Qian's HDHRPrime support along w/ a Bruno client.
      hdhrPrimeSpecial = (capDev.getName().startsWith("HDHR")) &&
          capDev.isNetworkEncoder() &&
          Sage.getBoolean("enable_detection_of_hdhrprime_custom_network_encoder", false) &&
          mcsr.isMediaExtender() && mcsr.supports3DTransforms();
      if (hdhrPrimeSpecial && Sage.DBG)
        System.out.println("Detected use of BRUNO device and HDHRPrime special network encoder....use alternate streaming mode w/ timestamp fix");
    }

    synchronized (this)
    {
      // Do this before we load the player so we don't screw up the driver if the mount fails due to network issues
      if (file.isFile() && currMF.isBluRay())
      {
        // This is an ISO image instead of a DVD directory; so mount it and then change the file path to be the image
        java.io.File mountDir = FSManager.getInstance().requestISOMount(file, uiMgr);
        if (mountDir == null)
        {
          if (Sage.DBG) System.out.println("FAILED mounting ISO image for BluRay playback");
          throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
        }
        unmountRequired = mountDir;
        if (new java.io.File(mountDir, "bdmv").isDirectory())
          file = new java.io.File(mountDir, "bdmv");
        else if (new java.io.File(mountDir, "BDMV").isDirectory())
          file = new java.io.File(mountDir, "BDMV");
        else
          file = mountDir;
      }

      // Set the curVolume field now so that if the open fails for any reason; the call to free() doesn't set the property
      // to 1.0f; it instead retains its value
      curVolume = uiMgr.getFloat("miniplayer/last_volume", 1.0f);

      if (initDriver0((vf == null || vf.getDisplayAspectRatio() > 1.40) ? 1 :0 ) == 0)
        throw new PlaybackException();

      // See if we need to transcode the video or not. This is dependent upon two things. One is whether or not
      // the client supports the format that the media is in, the other is whether or not it has the bandwidth
      // to handle the transfer and needs to be transrated(coded)
      // NOTE: If they want to use optimized VOB transcoding they can set miniclient/vob_transcode_mode to DVDAudioOnly. But that causes
      // A/V sync problems in some cases.
      if (Sage.WINDOWS_OS && Sage.get("media_server/conservative_transcode", null) == null)
      {
        // Base the default for this on CPU speed which we can get from the registry
        int cpuSpeedMHz = Sage.readDwordValue(Sage.HKEY_LOCAL_MACHINE, "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "~MHz");
        if (Sage.DBG && cpuSpeedMHz != 0)
          System.out.println("Detected CPU speed to be ~" + cpuSpeedMHz + "MHz");
        if (cpuSpeedMHz >= 1900)
        {
          Sage.putBoolean("media_server/conservative_transcode", false);
        }
      }
      String prefTranscodeMode = "DVD";
      sage.media.format.ContainerFormat inputFormat = currMF.getFileFormat();
      sage.media.format.AudioFormat inAudio = null;
      boolean using6ChAudioTranscode = false;
      if (inputFormat != null)
        inAudio = inputFormat.getAudioFormat();
      if (inAudio != null && inAudio.getChannels() >= 5)
      {
        prefTranscodeMode = Sage.getBoolean("media_server/conservative_transcode", true) ? uiMgr.get("miniclient/conservative_transcode_mode_6ch", "SVCD6Ch") :
          uiMgr.get("miniclient/transcode_mode_6ch", "DVD6Ch");
        using6ChAudioTranscode = true;
      }
      else
        prefTranscodeMode = Sage.getBoolean("media_server/conservative_transcode", true) ? uiMgr.get("miniclient/conservative_transcode_mode", "SVCD") :
          uiMgr.get("miniclient/transcode_mode", "DVD");

      /*
       * We need to decide whether to use push or pull mode and also whether or not to use the transocder.
       * The media format comparisons for this are NOT done yet, but will be implemented soon by adding
       * a MediaContainerFormat and MediaStreamFormat set of classes that allow detailed description of a
       * file's stream information.
       *
       * For now, we use pull mode if the client supports it and the bitrate is over 2 Mbps from the UI estimates.
       * We consider the client to support pull mode if they're pull mode format list is not empty.
       * We will also force pull mode if the client doesn't have any format support for push mode but does for pull mode.
       * We will also use push mode if the client has a fixed push format property setting.
       * We transcode if there's a fixed push format, or if we're pushing and the BW detected is under 2Mbps
       */
      detailedPushBufferStats = false;
      currState = NO_STATE;
      timeGuessMillis = 0;
      guessTimestamp = Sage.eventTime();
      myRate = 1;
      eos = false;
      firstSeek = true;
      sendSeekPullNext = false;
      wasFastSwitch = false;
      boolean useMP3StreamWrapper = false;
      long uiBandwidthEstimate = 0;
      boolean clientDoesMPEG2Push = true;
      boolean clientDoesPull = false;
      boolean clientCanDoMpeg4 = false;
      boolean clientCanDoMPEGHD = false;
      hdMediaPlayer = false;
      String fixedPushFormat = null;
      mediaExtender = true;
      lowBandwidth = false;
      enableBufferFillPause = Sage.getBoolean("miniclient/enable_buffer_fill_on_seek", false);
      boolean pureLocal = false;
      boolean httpls = false;
      isMpeg2PS = sage.media.format.MediaFormat.MPEG2_PS.equals(currMF.getContainerFormat());
      if (mcsr != null)
      {
        mediaExtender = mcsr.isMediaExtender();
        hdMediaPlayer = mcsr.isStandaloneMediaPlayer();
        if (mcsr.isMiniClientColorKeyed())
          colorKey = mcsr.getMiniClientColorKey();
        clientDoesMPEG2Push = mcsr.isSupportedPushContainerFormat(sage.media.format.MediaFormat.MPEG2_PS);
        detailedPushBufferStats = mcsr.isDetailedPushBufferStats();
        if (mcsr.isSupportedVideoCodec("MPEG2-VIDEO@HL"))
        {
          clientCanDoMPEGHD = true;
          if (mediaExtender)
            hdMediaExtender = true;
        }
        maxPushBufferSize = mcsr.getPushBufferSizeLimit();
        if (maxPushBufferSize == 0)
          maxPushBufferSize = 32768;
        maxPushBufferSize = Math.min(131072, maxPushBufferSize);
        int forceMaxPush = Sage.getInt("miniplayer/forced_max_push_size", 0);
        if (forceMaxPush > 0)
          maxPushBufferSize = forceMaxPush;
        // Check for transcoding mode support
        if (mcsr.isSupportedVideoCodec(sage.media.format.MediaFormat.MPEG4_VIDEO))
          clientCanDoMpeg4 = true;
        if (hostname != null && hostname.startsWith("file://"))
        {
          clientDoesPull = true;
          pureLocal = true;
        }
        else
        {
          clientDoesPull = mcsr.isSupportedPullContainerFormat(currMF.getContainerFormat());
          if (clientDoesPull)
          {
            // Check the audio & video formats
            String vidForm = currMF.getPrimaryVideoFormat();
            String audForm = currMF.getPrimaryAudioFormat();
            if (vidForm.length() > 0 && !mcsr.isSupportedVideoCodec(vidForm))
              clientDoesPull = false;
            if (audForm.length() > 0 && !mcsr.isSupportedAudioCodec(audForm))
              clientDoesPull = false;
          }
          fixedPushFormat = mcsr.getFixedPushMediaFormat();

          // We use HTTP Live Streaming for this
          if (mcsr.isIOSClient() && (currMF.isVideo() || currMF.isTV()))
            clientDoesPull = httpls = true;

          uiBandwidthEstimate = mcsr.getEstimatedBandwidth();
          // Disable transcoding on the fly
          if (uiBandwidthEstimate < 500000 && (clientCanDoMpeg4 || httpls))
          {
            // No estimated BW from the UI. Do a push to the MiniClient before it's setup and it'll
            // just dump that buffer, but we'll get to see how much time it took
            int oldPriority = Thread.currentThread().getPriority();
            byte[] buf = new byte[16384];
            for (int i = 0; i < buf.length; i++)
              buf[i] = (byte)(i & 0xFF);
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            // Be sure other threads don't interfere with our bandwidth calculation by being the highest priority
            if (Sage.DBG) System.out.println("MiniPlayer was not able to get a bandwidth estimate from the UI system, sending data to get its own estimate...");
            // This has always been a problem. So here's the new idea. We'll do this with 16k first and see how long it takes round trip.
            // Then we will do it with 32k. The difference in time between those two is a good basis for how fast we can send 16k and we use
            // that for our bandwidth calculation.
            try
            {
              // Do a first one that doesn't actually count just to prep it
              long t0 = Sage.eventTime();
              sockBuf.clear();
              sockBuf.putInt(MEDIACMD_PUSHBUFFER<<24 | (buf.length+(detailedPushBufferStats ? 18 : 8)));
              sockBuf.putInt(buf.length);
              sockBuf.putInt(0);
              if (detailedPushBufferStats)
              {
                sockBuf.putShort((short)0);
                sockBuf.putShort((short)0);
                sockBuf.putShort((short)0);
                sockBuf.putInt(0);
              }
              sockBuf.put(buf, 0, buf.length);
              sockBuf.flip();
              while (sockBuf.hasRemaining())
                clientSocket.write(sockBuf);
              clientInStream.readInt();
              if (detailedPushBufferStats)
              {
                clientReportedMediaTime = clientInStream.readInt();
                clientReportedPlayState = clientInStream.readByte();
              }
              long t1 = Sage.eventTime();
              sockBuf.clear();
              sockBuf.putInt(MEDIACMD_PUSHBUFFER<<24 | (buf.length+(detailedPushBufferStats ? 18 : 8)));
              sockBuf.putInt(buf.length);
              sockBuf.putInt(0);
              if (detailedPushBufferStats)
              {
                sockBuf.putShort((short)0);
                sockBuf.putShort((short)0);
                sockBuf.putShort((short)0);
                sockBuf.putInt(0);
              }
              sockBuf.put(buf, 0, buf.length);
              sockBuf.flip();
              while (sockBuf.hasRemaining())
                clientSocket.write(sockBuf);
              clientInStream.readInt();
              if (detailedPushBufferStats)
              {
                clientReportedMediaTime = clientInStream.readInt();
                clientReportedPlayState = clientInStream.readByte();
              }
              long t2 = Sage.eventTime();
              for (int i = 0; i < 2; i++)
              {
                sockBuf.clear();
                sockBuf.putInt(MEDIACMD_PUSHBUFFER<<24 | (buf.length+(detailedPushBufferStats ? 18 : 8)));
                sockBuf.putInt(buf.length);
                sockBuf.putInt(0);
                if (detailedPushBufferStats)
                {
                  sockBuf.putShort((short)0);
                  sockBuf.putShort((short)0);
                  sockBuf.putShort((short)0);
                  sockBuf.putInt(0);
                }
                sockBuf.put(buf, 0, buf.length);
                sockBuf.flip();
                while (sockBuf.hasRemaining())
                  clientSocket.write(sockBuf);
              }
              for (int i = 0; i < 2; i++)
              {
                clientInStream.readInt();
                if (detailedPushBufferStats)
                {
                  clientReportedMediaTime = clientInStream.readInt();
                  clientReportedPlayState = clientInStream.readByte();
                }
              }
              long t3 = Sage.eventTime();

              long doubleTime = t3 - t2;
              long singleTime = Math.min(t2 - t1, t1 - t0);
              if (singleTime >= doubleTime)
              {
                if (Sage.DBG) System.out.println("Not using optimized bandwidth detection because the numbers didn't align");
                singleTime = doubleTime/2;
              }
              uiBandwidthEstimate = Math.max((buf.length + 12)*8000/Math.max(1, Math.max(Math.min(150, singleTime), doubleTime - singleTime)),
                  (buf.length + 12)*8000/Math.max(1, singleTime));
              if (Sage.DBG) System.out.println("Bandwidth test base=" + singleTime + " base*2=" + doubleTime + " BW=" + uiBandwidthEstimate);
            }
            catch (Exception e)
            {
              System.out.println("ERROR estimating MiniPlayer bandwidth of:" + e);
            }
            Thread.currentThread().setPriority(oldPriority);
            if (!mcsr.isLocalConnection() && uiBandwidthEstimate < 10000000 &&
                uiBandwidthEstimate >= Sage.getInt("miniplayer/min_bandwidth_for_no_transcode", 2000000) && Sage.getBoolean("miniplayer/wan_prevent_push", true))
            {
              if (Sage.DBG) System.out.println("Detected non-LAN connection under 10Mbps but above set limit (" + uiBandwidthEstimate +
                  "), force it to transcode mode");
              uiBandwidthEstimate = Sage.getInt("miniplayer/min_bandwidth_for_no_transcode", 2000000) - 1000;
            }
            mcsr.addDataToBandwidthCalc(uiBandwidthEstimate/8, 1000);//buf.length + 12, Math.max(1, doubleTime - singleTime));
          }
          else if (uiBandwidthEstimate == 0)
            uiBandwidthEstimate = 50000000; // not an extender that can support low bandwidth transcode
          if (Sage.DBG) System.out.println("MiniPlayer got an estimate from the UI on bandwidth of " + uiBandwidthEstimate/1000 + "Kbps");
          // Set the average to be our initial estimate so we can use it to filter out bad estimated bandwidth values initially
          lastAverageEstimatedPushBitrate = (int)uiBandwidthEstimate;
        }
      }
      else
      {
        colorKey = null;
      }

      // NOTE: We should really check the media's rate against our bandwidth and not use 2Mbps as the bounds
      if (!pureLocal && mcsr != null && (mcsr.isSupportedPushContainerFormat(sage.media.format.MediaFormat.MPEG2_PS) ||
          mcsr.isSupportedPushContainerFormat(sage.media.format.MediaFormat.MPEG2_TS)) && uiBandwidthEstimate < Sage.getInt("miniplayer/min_bandwidth_for_no_transcode", 2000000) && clientCanDoMpeg4)
      {
        lowBandwidth = true;
      }

      boolean useOriginalAudioTrack = true;

      if (clientDoesPull && (httpls || pureLocal || !clientDoesMPEG2Push || !clientCanDoMpeg4 || uiBandwidthEstimate >= Sage.getInt("miniplayer/min_bandwidth_for_no_transcode", 2000000)))
      {
        if (Sage.DBG) System.out.println("MiniPlayer is using Pull mode playback");
        // Pull mode is being used
        pushMode = false;
      }
      else
      {
        /*
         * There's a few things we can do here since we're in push mode.
         * 1. If we're not a media extender; we know we're transcoding because that's the only way we push to desktop placeshifters
         * 2. For extenders, if the formats are compatible then we just push directly
         * 3. For extenders, if it doesn't support mpeg4 or if it's not lowBandwidth; then we transcode into DVD/SVCD, but if the video and audio
         * codecs are supported we just remux it instead
         * 4. For extenders that can do mpeg4 and its low bandwidth mode; then we transcode into the same format as the placeshifter uses
         */
        if (Sage.DBG) System.out.println("MiniPlayer is using Push mode playback");
        pushMode = true; // shouldPush(majorTypeHint, minorTypeHint);
        useNioTransfers = Sage.getBoolean("use_nio_transfers", false);
        // Check for transcoding
        // NOTE: Always transcode when we're doing push mode with the placeshifter. Non-transcoded push mode
        // doesn't work all that well and people usually connect that way when they want to experiment with transcoding.
        if (!mediaExtender || lowBandwidth/* && ((fixedPushFormat != null && fixedPushFormat.length() > 0) || uiBandwidthEstimate < 2000000)*/)
        {
          if (Sage.DBG) System.out.println("MiniPlayer is using the MPEG4 transcoder");
          transcoded = true;
          useOriginalAudioTrack = false;
          dynamicRateAdjust = (fixedPushFormat == null || fixedPushFormat.length() == 0);
          if (dynamicRateAdjust)
            prefTranscodeMode = majorTypeHint == MediaFile.MEDIATYPE_AUDIO ?
                ((uiBandwidthEstimate > 256000) ? "music128" : "music") :
                  ((mcsr != null && mcsr.isSupportedPushContainerFormat(sage.media.format.MediaFormat.MPEG2_PS)) ? "dynamic" : "dynamicts");
                else
                  prefTranscodeMode = fixedPushFormat;
        }
        else
        {
          sage.media.format.ContainerFormat cf = currMF.getFileFormat();
          if (cf != null && mcsr != null)
          {
            boolean containerOK = mcsr.isSupportedPushContainerFormat(cf.getFormatName()) ||
                (Sage.getBoolean("enable_internal_push_remuxer", true) &&
                    sage.media.format.MediaFormat.MPEG2_TS.equals(cf.getFormatName()) &&
                    mcsr.isSupportedPushContainerFormat(sage.media.format.MediaFormat.MPEG2_PS));
            sage.media.format.VideoFormat vidFormat = cf.getVideoFormat();
            boolean videoOK = false;
            boolean hasVideo = false;
            if (vidFormat != null)
            {
              hasVideo = true;
              if (sage.media.format.MediaFormat.MPEG2_VIDEO.equals(vidFormat.getFormatName()))
              {
                // Video format might be OK if it's an appropriate resolution
                if (!mcsr.isSupportedVideoCodec(vidFormat.getFormatName()))
                  videoOK = false;
                else if (clientCanDoMPEGHD)
                  videoOK = true;
                else if (vidFormat.getWidth() <= 720)
                {
                  if (MMC.getInstance().isNTSCVideoFormat())
                  {
                    if (vidFormat.getHeight() <= 480 && vidFormat.getFps() <= 30.1) // 30fps or less, and within NTSC resolution
                    {
                      // Format is OK for video!
                      videoOK = true;
                    }
                  }
                  else
                  {
                    if (vidFormat.getHeight() <= 576 && vidFormat.getFps() <= 25.1) // 25fps or less & within PAL resolution
                    {
                      // Format is OK for video!
                      videoOK = true;
                    }
                  }
                }
              }
              else if (mcsr.isSupportedVideoCodec(vidFormat.getFormatName()))
              {
                videoOK = true;
              }
            }
            sage.media.format.AudioFormat audFormat = cf.getAudioFormat();
            boolean audioOK = false;
            boolean hasAudio = false;
            boolean lowRateAudio = false;
            if (audFormat != null)
            {
              hasAudio = true;
              if (mcsr.isSupportedAudioCodec(audFormat.getFormatName()))
              {
                audioOK = true;
              }

              if (audFormat.getChannels() == 1 || audFormat.getSamplingRate() < 30000)
                lowRateAudio = true;
            }
            if (!Sage.getBoolean("miniplayer/allow_transcoding", true))
            {
              // do not allow transcoding w/ FFMPEG
              containerOK = videoOK = audioOK = true;
            }
            if (!clientCanDoMPEGHD)
            {
              if (!containerOK || (hasVideo && !videoOK) || (hasAudio && !audioOK))
              {
                transcoded = true;
                if (minorTypeHint == MediaFile.MEDIASUBTYPE_MP3)
                  useMP3StreamWrapper = true;
                else if (majorTypeHint == MediaFile.MEDIATYPE_AUDIO)
                  prefTranscodeMode = lowRateAudio ? "music128" : "music256";
                useOriginalAudioTrack = false;
              }
            }
            else
            {
              if ((hasVideo && !videoOK) || (hasAudio && !audioOK)/* ||
								(!containerOK && !hasVideo && minorTypeHint == MediaFile.MEDIASUBTYPE_MP3)*/)
              {
                transcoded = true;
                if (minorTypeHint == MediaFile.MEDIASUBTYPE_MP3)
                  useMP3StreamWrapper = true;
                else if (majorTypeHint == MediaFile.MEDIATYPE_AUDIO)
                  prefTranscodeMode = lowRateAudio ? "music128" : "music256";
                useOriginalAudioTrack = false;
              }
              else if (!containerOK)
              {
                transcoded = true;
                prefTranscodeMode = "mpeg2psremux";
              }
            }
          }
        }
      }


      this.timeshifted = timeshifted;
      currMute = !mediaExtender;
      serverSideTranscoding = false;
      usingRemuxer = false;
      if (pushMode)
      {
        if (bufferSize > 0 && hostname == null)
        {
          // Circular files don't work correctly with the MPEG2 pushers because they don't understand that concept. This is fixed by
          // having them go through the MediaServer which DOES understand circular files.
          if (Sage.DBG) System.out.println("MiniPlayer is going through the MediaServer to handle the circular file.");
          hostname = "localhost";
        }
        if(transcoded)
        {
          // For MP3 files we use JF's transcode wrapper; and for video & non-MP3 audio we use the media server's transcoder
          if (useMP3StreamWrapper)
          {
            if (Sage.DBG) System.out.println("MiniPlayer is using the MP3 stream wrapper");
            tcSrc = new Mpeg2Transcoder(file, hostname);
            /*					if (Sage.DBG) System.out.println("MiniPlayer is using the transcoder");
						mpegSrc = new FastMpeg2Reader(file, hostname);
						mpegSrc.setActiveFile(timeshifted);
						mpegSrc.setStreamTranscodeMode("mp3");
						transcoded = false;
						serverSideTranscoding = true;
						this.timeshifted = timeshifted = true;*/
          }
          else
          {
            if (Sage.DBG) System.out.println("MiniPlayer is using the transcoder");
            mpegSrc = new FastMpeg2Reader(file, hostname);
            mpegSrc.setActiveFile(timeshifted);
            sage.media.format.ContainerFormat currFileFormat = currMF.getFileFormat();
            if (currFileFormat != null && "true".equals(currFileFormat.getMetadataProperty("VARIED_FORMAT")))
              currFileFormat = sage.media.format.FormatParser.getFileFormat(file);
            mpegSrc.setStreamTranscodeMode(prefTranscodeMode, currFileFormat);
            transcoded = false;
            serverSideTranscoding = true;
            this.timeshifted = timeshifted = true;
          }
        }
        else if (hdhrPrimeSpecial || (hostname != null && (hostname.equals(Sage.get("alternate_media_server", "")) ||
            Sage.getBoolean("use_alternate_streaming_ports", false))))
        {
          if (hostname == null)
            hostname = "127.0.0.1";
          if (Sage.DBG) System.out.println("MiniPlayer is using the RemotePusher connected to: " + hostname);
          rpSrc = new RemotePusherClient(this);
          try
          {
            rpSrc.connect(hostname);
            sage.media.format.ContainerFormat currFileFormat = currMF.getFileFormat();
            if (currFileFormat != null && "true".equals(currFileFormat.getMetadataProperty("VARIED_FORMAT")))
              currFileFormat = sage.media.format.FormatParser.getFileFormat(file);
            rpSrc.openFile(file.getAbsolutePath(), currFileFormat == null ? "" : currFileFormat.getFullPropertyString(false),
                timeshifted, false);
          }
          catch (java.io.IOException e)
          {
            System.out.println("Error initing RemotePusher stream:" + e);
            e.printStackTrace();
            rpSrc.close();
            rpSrc = null;
            throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
          }
        }
        else
        {
          if (Sage.DBG) System.out.println("MiniPlayer is using the MPEG2 pusher");
          mpegSrc = new FastMpeg2Reader(file, hostname);
          mpegSrc.setActiveFile(timeshifted);
          sage.media.format.ContainerFormat currFileFormat = currMF.getFileFormat();
          if (currFileFormat != null && "true".equals(currFileFormat.getMetadataProperty("VARIED_FORMAT")))
            currFileFormat = sage.media.format.FormatParser.getFileFormat(file);
          mpegSrc.setStreamTranscodeMode(null, currFileFormat);
          if (currMF.isBluRay())
            mpegSrc.setTargetBDTitle(uiMgr.getVideoFrame().getBluRayTargetTitle());
          if (!hdMediaExtender && currFileFormat != null && sage.media.format.MediaFormat.MPEG2_TS.equals(currFileFormat.getFormatName()))
          {
            usingRemuxer = true;
            transcoded = false;
            serverSideTranscoding = true;
            this.timeshifted = timeshifted = true;
            // NOTE: WE DO WANT TO USE IT; WE JUST DON'T KNOW WHERE IT'LL BE!!!!
            // NOTE: WE DO WANT TO USE IT; WE JUST DON'T KNOW WHERE IT'LL BE!!!!
            useOriginalAudioTrack = true;
          }
          else if (currFileFormat != null && Sage.getBoolean("miniplayer/align_iframes_on_seek", true))
            mpegSrc.setIFrameAlign(true);
        }
        if (rpSrc == null)
        {
          try
          {
            if(transcoded)
            {
              tcSrc.init(true, !timeshifted);
            }
            else
            {
              mpegSrc.init(true, !timeshifted, usingRemuxer);
            }
          }
          catch (java.io.IOException e)
          {
            System.out.println("Error initing MPEG2 stream:" + e);
            e.printStackTrace();
            throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
          }
        }
        if (mpegSrc != null)
          bdp = mpegSrc.getBluRaySource();
        if (!timeshifted && !transcoded && rpSrc == null)
          finalLength = mpegSrc.length();
        if (serverSideTranscoding && mpegSrc != null && mpegSrc.getTranscoder() != null && mpegSrc.getTranscoder() instanceof FFMPEGTranscoder)
        {
          ((FFMPEGTranscoder)mpegSrc.getTranscoder()).setEstimatedBandwidth(uiBandwidthEstimate);
          ((FFMPEGTranscoder)mpegSrc.getTranscoder()).setThreadingEnabled(Sage.getBoolean("xcode/allow_multithreading_for_hdextender_placeshifting", false) || !hdMediaExtender || !lowBandwidth);
        }
      }
      //mpegSrc.setTimeshifted(timeshifted);
      //mpegSrc.setCircularSize(bufferSize);

      if (rpSrc != null)
      {
        // Tell the miniclient to redirect to our alternate server instead
        sage.media.format.ContainerFormat currFileFormat = currMF.getFileFormat();
        if (currFileFormat != null && "true".equals(currFileFormat.getMetadataProperty("VARIED_FORMAT")))
          currFileFormat = sage.media.format.FormatParser.getFileFormat(file);
        if (!openURL0("push://" + hostname + (Sage.getBoolean("use_alternate_streaming_ports", false) ?
            ":31098" : "") + "/session/" + rpSrc.getSessionID() + "?" +
            (currFileFormat == null ? "" : currFileFormat.getFullPropertyString(false, timeshifted ? "live=1;" : null))))
          throw new PlaybackException();
      }
      else if (pushMode)
      {
        // Get the full format string for specifying in push mode
        String formatString = "";
        if (currMF != null)
        {
          sage.media.format.ContainerFormat cf = (bdp != null) ? bdp.getFileFormat() : currMF.getFileFormat();
          if (cf != null && "true".equals(cf.getMetadataProperty("VARIED_FORMAT")))
            cf = sage.media.format.FormatParser.getFileFormat(file);
          if (usingRemuxer)
            cf = ((RemuxTranscodeEngine)mpegSrc.getTranscoder()).getTargetFormat();
          else if (serverSideTranscoding && mediaExtender && (prefTranscodeMode == null || !prefTranscodeMode.equals("mpeg2psremux")))
          {
            cf = null; // don't set the format since it'll be a base MPEG2 format
            // But if we're doing placeshifting then we need the format string
            if ("dynamic".equals(prefTranscodeMode))
            {
              formatString = "f=MPEG2-PS;[bf=vid;f=MPEG4;][bf=aud;f=MP2]";
            }
            else if ("dynamicts".equals(prefTranscodeMode))
            {
              formatString = "f=MPEG2-TS;[bf=vid;f=MPEG4;][bf=aud;f=AAC]";
            }
            else if ("music".equals(prefTranscodeMode) || "music128".equals(prefTranscodeMode))
            {
              formatString = "f=MPEG2-PS;[bf=aud;f=MP2]";
            }
          }
          if (cf != null)
          {
            formatString = cf.getFullPropertyString(false);
            if (serverSideTranscoding && !useOriginalAudioTrack)
            {
              // Change it to be the transcode format properties...for now just fix audio sampling rate for the hd extender
              // NOTE: FIX ME FIX ME!!!!
              formatString = formatString.replaceAll("\\;sr\\=[0-9]*\\;", ";sr=48000;");
            }
          }
        }
        if (!openURL0("push:" + formatString))
          throw new PlaybackException();
      }
      else
      {
        // Do this now since we may use it below for determining if we're localhost or not & setting up the stv:// URL hostname
        String theURL = null;
        if (majorTypeHint == MediaFile.MEDIATYPE_DVD && file == null)
          theURL = "dvd://";
        else if (httpls)
        {
          // NOTE: We should put some kind of HOSTNAME marker in here that the client replaces with the address they connected to since
          // we won't necessarily know our external IP address if they didn't use the locator ID to connect
          // Temp hack to get our external IP for now
          String ipPort = null;
          /*					try
					{
						ipPort = sage.locator.LocatorLookupClient.lookupIPForGuid(sage.locator.LocatorRegistrationClient.getPrettyGuid(
							sage.locator.LocatorRegistrationClient.getSystemGuid()));
					}
					catch (java.io.IOException ioe){}
					if (ipPort != null && ipPort.indexOf(":") == -1)
						ipPort += ":31099";
					if (ipPort == null)
						ipPort = "192.168.1.22:31099";*/
          ipPort = "HOSTNAME";
          String forced = Sage.get("forced_external_httpls_addr_port", "");
          if (forced != null && forced.length() > 0)
            ipPort = forced;

          theURL = "http://" + ipPort + "/iosstream_" + uiMgr.getLocalUIClientName() + "_" + currMF.id + "_" + VideoFrame.getVideoFrameForPlayer(this).getCurrSegment() + "_list.m3u8";
        }
        else if (pureLocal)
        {
          theURL = hostname;
        }
        else if (hostname != null && hostname.equals(Sage.get("alternate_media_server", "")))
          theURL = "stv://" + hostname + (Sage.getBoolean("use_alternate_streaming_ports", false) ?
              ":7817" : "") + "/" + file.getAbsolutePath();
        else if (mcsr.isStreamingProtocolSupported("stv") && (!IOUtils.isLocalhostSocket(clientSocket.socket()) || timeshifted))
          theURL = "stv://" + clientSocket.socket().getLocalAddress().getHostAddress() + "/" + file.getAbsolutePath();
        else
          theURL = file.getAbsolutePath();
        if (!openURL0(theURL))
          throw new PlaybackException();
      }
      // For extenders, set the correct audio stream we're using for playback
      if (((mediaExtender && pushMode) || hdMediaExtender) && !lowBandwidth)
      {
        sage.media.format.ContainerFormat cf = (bdp != null) ? bdp.getFileFormat() : currMF.getFileFormat();
        if (cf != null && "true".equals(cf.getMetadataProperty("VARIED_FORMAT")))
          cf = sage.media.format.FormatParser.getFileFormat(file);
        if (usingRemuxer)
          cf = ((RemuxTranscodeEngine)mpegSrc.getTranscoder()).getTargetFormat();
        if (cf != null && cf.getNumAudioStreams() > 0)
        {
          sage.media.format.AudioFormat af = cf.getAudioFormat();
          int audioStreamType = (using6ChAudioTranscode && serverSideTranscoding && !usingRemuxer) ? 0xbd80 : 0xc000;
          int ac3indexOffset = (af != null && usingRemuxer) ? af.getOrderIndex() : 0;
          // If we're transcoding then the original audio stream doesn't matter, just use 0xc0
          // unless we're using the remuxer....
          if (af != null && (useOriginalAudioTrack || ((!serverSideTranscoding && !transcoded))/* || (mpegSrc != null && mpegSrc.getTranscoder() instanceof RemuxTranscodeEngine)*/))
          {
            String streamID = af.getId();
            if (streamID != null && streamID.length() > 0)
            {
              // See if it's just a stream ID or if it's 2 parts
              int dashIdx = streamID.indexOf('-');
              if (dashIdx == -1)
              {
                try
                {
                  if (streamID.length() == 4) // the full ID
                    audioStreamType = Integer.parseInt(streamID, 16);
                  else
                    audioStreamType = (Integer.parseInt(streamID, 16) << 8);
                }
                catch (NumberFormatException nfe)
                {
                  if (Sage.DBG) System.out.println("ERROR parsing audio stream ID " + streamID + " of: " + nfe);
                }
              }
              else
              {
                try
                {
                  audioStreamType = (Integer.parseInt(streamID.substring(0, dashIdx), 16) << 8) |
                      Integer.parseInt(streamID.substring(dashIdx + 1, dashIdx + 3), 16);
                }
                catch (NumberFormatException nfe)
                {
                  if (Sage.DBG) System.out.println("ERROR parsing audio stream ID " + streamID + " of: " + nfe);
                }
              }
            }
          }
          if (af != null && serverSideTranscoding && useOriginalAudioTrack && sage.media.format.MediaFormat.AC3.equals(af.getFormatName()))
          {
            if (Sage.DBG) System.out.println("Switching audio stream to be 0xbd-80 for default AC3");
            audioStreamType = 0xbd80 + ac3indexOffset;
          }
          if (hdMediaExtender && !serverSideTranscoding)
          {
            audioTracks = cf.getAudioFormats();
            if (af != null)
            {
              for (int i = 0; i < audioTracks.length; i++)
              {
                if (audioTracks[i] == af)
                {
                  languageIndex = i;
                  break;
                }
              }
            }
            subpicTracks = cf.getSubpictureFormats();
            subpicIndex = 0;
            subpicOn = false;
            // Disable subpictures for DVB if its an HD100
            if (!hdMediaPlayer && subpicTracks != null && subpicTracks.length > 0)
            {
              for (int i = 0; i < subpicTracks.length; i++)
              {
                if ("dvbsub".equalsIgnoreCase(subpicTracks[i].getFormatName()))
                {
                  if (Sage.DBG) System.out.println("Disabling subpicture track selection for HD100 since it doesn't support DVB subpictures");
                  subpicTracks = null;
                  break;
                }
              }
            }
          }
          if (pushMode)
          {
            if (Sage.DBG) System.out.println("Setting audio stream for playback to be ID=0x" + Integer.toString(audioStreamType, 16));
            DVDStream(0, audioStreamType);
            matchBDSubpictureToAudio();
          }
        }
        if (isMpeg2PS)
        {
          if (Sage.DBG) System.out.println("Setting default subpicture track to be disabled for MPEG2-PS");
          DVDStream(1, PS_SUBPIC_DISABLE_STREAM);
        }
      }
      else if (hdMediaExtender && pushMode && lowBandwidth && serverSideTranscoding)
      {
        // We still need to set the default audio stream
        DVDStream(0, 0xc000);
      }

      currState = LOADED_STATE;

      currHintMajorType = majorTypeHint;
      currHintMinorType = minorTypeHint;
      currHintEncoding = encodingHint;

      currCCState = -1;
      //videoDimensions = new java.awt.Dimension(mpegSrc.parsedVideo.horizontal_size_value,
      //    mpegSrc.parsedVideo.vertical_size_value);
      // Use the native size returned even though it's not right because it's what we want to use
      // for video rectangles. That's the purpose of this information.
      videoDimensions = getVideoDimensions0();
      if (Sage.DBG) System.out.println("Sigma video dim=" + videoDimensions);
      currFile = file;

      // If we don't pause it first then the push thread may not see the correct state and terminate immediately
      if (pushMode)
        pause();

      if (mediaExtender)
        setMute0(false);

      // Preserve the volume setting across UI sessions
      if (uiMgr != null)
        setVolume(uiMgr.getFloat("miniplayer/last_volume", 1.0f));

      //		flushPush0();

      /*
       * There is ALWAYS a seek after load is completed to set the initial time for file playback. Do not start
       * pushing until after we've done that seek.
       */
      if (pushMode)
      {
        pushThreadCreated = false;
      }
      //createPushThread();
    }
  }



  // For pushing we create a thread that takes data from the Mpeg source and shoves it into the decoder.
  // Before it does that, it first checks to be sure there's enough data to be read in the Mpeg source. Then
  // it gets the next available buffer from the decoder once it's available. At that point, it'll get the decoder buffer,
  // then read from the source into a Java buffer, and then do a native copy from the Java buffer to the decoder buffer.
  // Then it sends the decoder the buffer.  This should cause the smallest delay for any kind of stream interruption we want
  // to perform for seeking.
  protected void createPushThread()
  {
    if (Sage.DBG) System.out.println("Creating new push thread");
    pushThread = new Thread(new Runnable()
    {
      public void run()
      {
        if (Sage.DBG) System.out.println("Pusher thread is starting");
        if (rpSrc != null)
        {
          synchronized (decoderLock)
          {
            try
            {
              rpSrc.sendStart();
            }
            catch (java.io.IOException e)
            {
              if (Sage.DBG) System.out.println("Error sending START command to remote pusher of:" + e);
              e.printStackTrace();
              connectionError();
              return;
            }
          }
        }
        pushBufferSize = (lowBandwidth || currHintMajorType == MediaFile.MEDIATYPE_AUDIO) ? 16384 : Math.min(maxPushBufferSize, 131072);
        if (Sage.DBG) System.out.println("Miniplayer pusher using buffer size of " + pushBufferSize);
        if (hdMediaPlayer && !lowBandwidth && currHintMajorType != MediaFile.MEDIATYPE_AUDIO && !transcoded && !serverSideTranscoding)
        {
          // Check if this is a transport stream, and if so modulus the buffer size with the TS packet size. This
          // helps with aligment on smooth FF/REW
          MediaFile currMF = VideoFrame.getMediaFileForPlayer(MiniPlayer.this);
          if (currMF != null)
          {
            sage.media.format.ContainerFormat cf = currMF.getFileFormat();
            if (cf != null && sage.media.format.MediaFormat.MPEG2_TS.equals(cf.getFormatName()))
            {
              int packetSize = cf.getPacketSize();
              if (packetSize == 0)
                packetSize = 188;
              pushBufferSize = pushBufferSize - (pushBufferSize % packetSize);
              if (Sage.DBG) System.out.println("Adjusted push buffer size for TS packet alignment of " + packetSize + " bytes to be: " + pushBufferSize);
            }
          }
        }
        pushDumpStream = null;
        String pushDumpFileName = (uiMgr == null) ? "" : uiMgr.get("miniclient/push_dump_debug_file", "");
        if (pushDumpFileName.length() > 0)
        {
          int dumpFileIdx = 0;
          while (new java.io.File(pushDumpFileName + "-" + dumpFileIdx + ".mpg").isFile())
            dumpFileIdx++;
          try
          {
            pushDumpStream = new java.io.FileOutputStream(pushDumpFileName + "-" + dumpFileIdx + ".mpg").getChannel();
          }
          catch (java.io.IOException e)
          {
            System.out.println("ERROR creating push dump debug file:" +e );
          }
        }
        if (bdp != null)
        {
          currBDAngle = 1;
          currBDTitle = bdp.getTitle();
        }
        java.nio.ByteBuffer javaBuff = (tcSrc != null) ? java.nio.ByteBuffer.allocate(pushBufferSize) : java.nio.ByteBuffer.allocateDirect(pushBufferSize);
        boolean kickVF = false;
        float lastPlayRate = 1;
        while ((currState == PAUSE_STATE || currState == PLAY_STATE))
        {
          if (kickVF)
          {
            VideoFrame vf = VideoFrame.getVideoFrameForPlayer(MiniPlayer.this);
            vf.kick();
            kickVF = false;
            continue;
          }
          synchronized (decoderLock)
          {
            if (currState != PAUSE_STATE && currState != PLAY_STATE)
              break;
            if (debugPush) System.out.println("SDPushLoop");
            if (detailedPushBufferStats && clientReportedPlayState == EOS_STATE && !eos)
            {
              if (Sage.DBG) System.out.println("Client reported play state indicates EOS, set the flag-1");
              // Reset the UI timeouts on an EOS so we don't trigger the SS since it considers EOS to be a non-playing state
              if (uiMgr != null)
                uiMgr.getRouter().resetInactivityTimers();
              eos = true;
              kickVF = true;
            }
            // In low bandwidth mode we push even when paused so we can get ahead further in buffering
            // We should always do this in order to keep as much data in the client's buffer as possible...BUT there's
            // a legacy bug in the HD media extender where it starts playing after a flush, so if we re-enable this
            // then seeking while paused will cause playback to resume!
            if (currState == PAUSE_STATE && !lowBandwidth && (mcsr == null || !mcsr.supportsFrameStep()))
            {
              if (debugPush) System.out.println("Waiting in paused state");
              try{decoderLock.wait(100);}catch(Exception e){}
              continue;
            }
            if (shouldYieldDecoderLock())
            {
              try{
                decoderLock.notifyAll();
                decoderLock.wait(20);}catch(Exception e){}
              continue;
            }
            if (rpSrc == null && (transcoded || timeshifted) &&
                (transcoded ? tcSrc.availableToRead() :
                  mpegSrc.availableToRead2(pushBufferSize))< (transcoded ? 0 : pushBufferSize))
            {
              if (debugPush) System.out.println("SigmaPlayer waiting for data to appear in file...");
              boolean alreadyCalledPushBuffer = false;
              // Be sure we keep our stats updated if we've pushed all the data and are just waiting around for the client
              // to get to the EOS
              if (detailedPushBufferStats && Sage.eventTime() - lastDetailedBufferUpdate > 500)
              {
                boolean sendServerEOS = false;
                if (serverSideTranscoding && mpegSrc != null)
                {
                  if (mpegSrc.getTranscoder().isTranscodeDone())
                  {
                    if (!((FFMPEGTranscoder) (mpegSrc.getTranscoder())).didTranscodeCompleteOK())
                    {
                      if (Sage.DBG) System.out.println("Detected failure in the transcoder attempt to restart it...");
                      try
                      {
                        mpegSrc.seek(mpegSrc.getLastParsedTimeMillis());
                      }
                      catch (java.io.IOException ioe)
                      {
                        if (Sage.DBG) System.out.println("ERROR restarting the transcoder of:" + ioe);
                        sendServerEOS = true;
                      }
                    }
                    else
                    {
                      if (debugPush) System.out.println("Server is pushing an EOS message to the client");
                      sendServerEOS = true;
                    }
                  }
                }
                if (numPushedBuffers > 1 || sendServerEOS)
                {
                  if (!pushBuffer0(javaBuff, 0, (sendServerEOS ? 0x80 : 0) | getFlags()))
                  {
                    if (Sage.DBG) System.out.println("pushBuffer call failed; terminating push loop");
                    break;
                  }
                  alreadyCalledPushBuffer = true;
                }
              }
              if((transcoded && tcSrc.availableToRead() < 0) || (serverSideTranscoding && mpegSrc != null &&
                  numPushedBuffers > 0 && mpegSrc.getTranscoder().isTranscodeDone()))
              { // Reached EOS
                if (serverSideTranscoding && mpegSrc != null && mpegSrc.getTranscoder().isTranscodeDone() &&
                    !((FFMPEGTranscoder) (mpegSrc.getTranscoder())).didTranscodeCompleteOK())
                {
                  if (Sage.DBG) System.out.println("Detected failure in the transcoder attempt to restart it...");
                  try
                  {
                    mpegSrc.seek(mpegSrc.getLastParsedTimeMillis());
                    continue;
                  }
                  catch (java.io.IOException ioe)
                  {
                    if (Sage.DBG) System.out.println("ERROR restarting the transcoder of:" + ioe);
                  }
                }
                if (Sage.DBG) System.out.println("Pushing EOS to decoder-1");
                if(!pushBuffer0(javaBuff, 0, 0x80 | getFlags()))
                {
                  if (Sage.DBG) System.out.println("pushBuffer call failed; terminating push loop");
                  break;
                }
                alreadyCalledPushBuffer = true;
                if(freeSpace<0)
                {
                  if (Sage.DBG) System.out.println("Received eos from client");
                  if (!eos)
                  {
                    // Reset the UI timeouts on an EOS so we don't trigger the SS since it considers EOS to be a non-playing state
                    if (uiMgr != null)
                      uiMgr.getRouter().resetInactivityTimers();
                    kickVF = true; // I think we need that only once, verify with Jeff...
                    eos = true;
                  }
                }
                try{decoderLock.wait(100);}catch(Exception e){}

                continue;
              }
              if (!alreadyCalledPushBuffer && !lowBandwidth)
              {
                // We call pushBuffer again here in case there are DVB subtitles which need more frequent updating...we'd alredy be doing this
                // if the file wasn't 'active'.
                if (!pushBuffer0(null, 0, getFlags()))
                {
                  if (Sage.DBG) System.out.println("pushBuffer call failed; terminating push loop");
                  break;
                }
              }
              try{decoderLock.wait((lowBandwidth && dynamicRateAdjust) ? 500 : 50);}catch(Exception e){}
              continue;
            }
            if (!(transcoded || timeshifted) && eos && ((rpSrc != null && rpSrc.isServerEOS() && myRate >= 1.0) ||
                (rpSrc == null && (finalLength - mpegSrc.getReadPos()) <= 0)))
            {
              if (debugPush) System.out.println("Waiting at end of stream");
              try{decoderLock.wait(100);}catch(Exception e){}
              continue;
            }

            // if we were rewinding, and we're not now, then flush the decoder to get back our buffers
            // NOTE: 7/1/05 - Always flush the decoder on rate changes or we may have
            // issues where we run out of buffers
            if ((myRate > 0 && wasReversePlay) || (lastPlayRate != myRate /*&&
                            /*(lastPlayRate == 1.0 || ((lastPlayRate > 0) != (myRate > 0)))*/))
            {
              if (debugPush) System.out.println("Flushing decoder after rate change");
              wasReversePlay = false;
              lastPlayRate = myRate;
              if (rpSrc != null)
              {
                try
                {
                  rpSrc.sendRateChange(myRate);
                  if (myRate < 1.0 && eos) {
                    // we are rewinding from EOS, set state to PLAY
                    eos = false;
                    play();
                  }
                }
                catch (java.io.IOException e)
                {
                  System.out.println("ERROR sending rate change command to remote pusher of:" + e);
                  break;
                }
                lastMediaTimeCacheTime = 0;
              }
              else if (hdMediaExtender)
              {
                // Seek us after variable speed play so that we're at the proper time in the stream
                // since there may be a bunch of stuff buffered in the decoder
                long seekTimeMillis;
                // If we're already at the beginning of the stream then don't bother seeking because
                // sometimes the timestamps from the client are messed up when we rewind to the beginning
                if ((transcoded ? tcSrc.getReadPos() : mpegSrc.getReadPos()) <= 512*1024)
                  seekTimeMillis = 0;
                else
                {
                  // For BluRay the time isn't accurate due to cell boundary issues; so use the time
                  // from the demux instead. It'll be pretty close anyways due to the high bitrate of the content
                  seekTimeMillis = bdp != null ? mpegSrc.getLastParsedTimeMillis() : getNativeMediaTimeNoSync();
                  try
                  {
                    if(transcoded)
                      tcSrc.seek(seekTimeMillis);
                    else
                      mpegSrc.seek(seekTimeMillis);
                  }
                  catch (java.io.IOException e)
                  {
                    System.out.println("ERROR seeking MPEG pusher after finishing variable speed play:" + e);
                  }
                }

                flushPush0();
                // do this to clear the flags for reverse play issues w/ BluRay
                if (bdp != null && !pushBuffer0(null, 0, getFlags()))
                {
                  if (Sage.DBG) System.out.println("pushBuffer call failed; terminating push loop");
                  break;
                }
                if (bdp != null)
                {
                  lastBluRayIndex = -1;//bdp.getCurrClipIndex();
                  //									long ptsOffset = bdp.getClipPtsOffset(lastBluRayIndex);
                  if (Sage.DBG) System.out.println("Resuming normal play for BluRay; reset the index");
                  //									NewCell0(ptsOffset);
                }
                if (serverSideTranscoding)
                {
                  timestampOffset = seekTimeMillis;
                  lastMediaTime = 0;
                  clientReportedMediaTime = 0;
                }
                else
                  lastMediaTime = seekTimeMillis;
                lastMediaTimeCacheTime = Sage.eventTime();
                // We must continue around the loop because we seeked and now may not have anything left in the buffer
                continue;
              }
              else
                flushPush0();
            }

            if (rpSrc != null)
            {
              // Check on our state
              if (!timeshifted)
              {
                if (rpSrc.isServerEOS())
                {
                  MediaFile currRecFile = null;
                  if (uiMgr != null && (currRecFile = SeekerSelector.getInstance().getCurrRecordFileForClient(uiMgr, false)) != null)
                  {
                    // Also make sure this device supports fast mux switching
                    CaptureDevice recInput = SeekerSelector.getInstance().getCaptureDeviceControlledByClient(uiMgr);
                    if (!eos && (recInput == null || recInput.supportsFastMuxSwitch()))
                    {
                      if (Sage.DBG) System.out.println("SERVER Buffer size is now ZERO! Trigger local EOS to start the seamless file switch");
                      // Reset the UI timeouts on an EOS so we don't trigger the SS since it considers EOS to be a non-playing state
                      if (uiMgr != null)
                        uiMgr.getRouter().resetInactivityTimers();
                      // We trigger this now so that we cause our transitions when watching live TV to happen early enough
                      eos = true;
                    }
                  }
                  // Check for an EOS on the client
                  if (!eos && rpSrc.isClientEOS())
                  {
                    if (Sage.DBG) System.out.println("Received eos from client");
                    // Reset the UI timeouts on an EOS so we don't trigger the SS since it considers EOS to be a non-playing state
                    if (uiMgr != null)
                      uiMgr.getRouter().resetInactivityTimers();
                    eos = true;
                    needToPlay = false;
                    pausePush0();
                    currState = PAUSE_STATE;
                  }
                  else
                    try{decoderLock.wait(100);}catch(Exception e){}
                  kickVF = true;
                }
                else
                  try{decoderLock.wait(50);}catch(Exception e){}
              }
              else
                try{decoderLock.wait(50);}catch(Exception e){}
              continue;
            }

            // This used to be prepNextDecoderBuffer0, but we inlined it because of the getFlags() call
            if (freeSpace < pushBufferSize)
            {
              if (!pushBuffer0(null, 0, getFlags()))
              {
                if (Sage.DBG) System.out.println("pushBuffer call failed; terminating push loop");
                break;
              }
            }
            int availBufferSize = freeSpace;
            // If we're doing transcoding and the client's buffer is full then wait to send more until
            // they have a complete buffer to receive from us.
            if (availBufferSize < pushBufferSize)//availBufferSize == 0 || (serverSideTranscoding && availBufferSize < pushBufferSize))
            {
              if (debugPush) System.out.println("SigmaPlayer waiting for buffer to become available from decoder...");
              try{decoderLock.wait(lowBandwidth ? 250 : 50);}catch(Exception e){}
              continue;
            }
            if (debugPush) System.out.println("buffer size avail=" + availBufferSize + " using=" + Math.min(javaBuff.capacity(), availBufferSize));

            // Wait for a bit till we start adjusting since we always start low
            if (dynamicRateAdjust && serverSideTranscoding && numPushedBuffers > 10)
            {
              // Go entirely off the estimated bitrate that we see when we push the data; that is by far the
              // most accurate measurement we can use.
              // NARFLEX - Update 10/17/08 - I did a major update to the placeshifter system. We realized on the 8635 that
              // the major performance slowdown was in waiting for the replies to the pushbuffer call. So we applied that
              // same optimization here. This however interferes majorly with bandwidth detection when the buffers are
              // almost full. So now we're using averages more since we dont rely on our instantaneous calculations as much
              // since they rarely have symmetrical roundtrip statistics in them (meaning back and forth and not just lots of sends).
              // So now we've adjusted the way the timing works so it seems like it does a pretty good job of estimating what
              // the bandwidth available is.
              FFMPEGTranscoder fftc = ((FFMPEGTranscoder)mpegSrc.getTranscoder());

              // Adjust the bandwidth buffer based on how much free space the client has. This is our secondary
              // measure for protecting against underflow.
              if (freeSpace < maxAvailBufferSize/2)
                currBandwidthBufferKbps = BANDWIDTH_BUFFER_KBPS;
              else if (freeSpace < 3*maxAvailBufferSize/4)
                currBandwidthBufferKbps = BANDWIDTH_BUFFER_KBPS*2;
              else
                currBandwidthBufferKbps = BANDWIDTH_BUFFER_KBPS*3;

              if (debugPush) System.out.println("Client Buffer size=" + (maxAvailBufferSize - freeSpace) +
                  " estimRateKbps=" + lastEstimatedPushBitrate/1000 + " avgRateKbps=" + lastAverageEstimatedPushBitrate/1000 +
                  " rateKbps=" + fftc.getCurrentStreamBitrateKbps() +
                  " estimStreamRateKbps=" + lastEstimatedStreamBitrate/1000 + " avgStreamRateKbps=" + lastAverageEstimatedStreamBitrate/1000 +
                  " bwBufferKbps=" + currBandwidthBufferKbps);

              if (fftc.getCurrentVideoBitrateKbps() > MIN_DYNAMIC_VIDEO_BITRATE_KBPS && Sage.eventTime() - lastRateAdjustTime > 500 &&
                  fftc.getCurrentStreamBitrateKbps() > lastAverageEstimatedPushBitrate/1000 - currBandwidthBufferKbps)
              {
                // If it's a larger change then use compare it against the shorter term, for smaller changes to the longer term
                // This avoids making unnecessary bitrate adjustments.
                int currAdjust = Math.max(lastEstimatedPushBitrate/1000 - currBandwidthBufferKbps - fftc.getCurrentStreamBitrateKbps(),
                    -(fftc.getCurrentVideoBitrateKbps() - MIN_DYNAMIC_VIDEO_BITRATE_KBPS));
                //								if (lastEstimatedPushBitrate > lastAverageEstimatedPushBitrate &&
                //									lastAverageEstimatedPushBitrate > fftc.getCurrentStreamBitrateKbps())//(Math.abs(currAdjust) < 20)
                {
                  int newAdjust = Math.max(lastAverageEstimatedPushBitrate/1000 - currBandwidthBufferKbps - fftc.getCurrentStreamBitrateKbps(),
                      -(fftc.getCurrentVideoBitrateKbps() - MIN_DYNAMIC_VIDEO_BITRATE_KBPS));
                  //if (Math.abs(newAdjust) < Math.abs(currAdjust))
                  {
                    currAdjust = newAdjust;
                  }
                }
                if (Math.abs(currAdjust) > 3)
                {
                  if (Sage.DBG && !debugPush) System.out.println("Client Buffer size=" + (maxAvailBufferSize - freeSpace) +
                      " estimRateKbps=" + lastEstimatedPushBitrate/1000 + " avgRateKbps=" + lastAverageEstimatedPushBitrate/1000 +
                      " rateKbps=" + fftc.getCurrentStreamBitrateKbps() +
                      " estimStreamRateKbps=" + lastEstimatedStreamBitrate/1000 + " avgStreamRateKbps=" + lastAverageEstimatedStreamBitrate/1000);
                  fftc.dynamicVideoRateAdjust(currAdjust);
                  lastRateAdjustTime = Sage.eventTime();
                  if (Sage.DBG || debugPush) System.out.println("Adjusted bitrate DOWN to : " + fftc.getCurrentStreamBitrateKbps());
                }
              }
              else if (fftc.getCurrentVideoBitrateKbps() < 1500 &&
                  (lastAverageEstimatedPushBitrate/1000 > fftc.getCurrentStreamBitrateKbps() + currBandwidthBufferKbps) && Sage.eventTime() - lastRateAdjustTime > 1000)
              {
                // Trying to push the bitrate higher than the bandwidth we've detected doesn't seem wise at this point...
                int currAdjust = Math.min(1500 - fftc.getCurrentVideoBitrateKbps(),
                    lastEstimatedPushBitrate/1000 - fftc.getCurrentStreamBitrateKbps() - currBandwidthBufferKbps);
                //								if (lastEstimatedPushBitrate < lastAverageEstimatedPushBitrate &&
                //									lastAverageEstimatedPushBitrate < fftc.getCurrentStreamBitrateKbps())//(Math.abs(currAdjust) < 20)
                {
                  int newAdjust = Math.min(1500 - fftc.getCurrentVideoBitrateKbps(),
                      lastAverageEstimatedPushBitrate/1000 - fftc.getCurrentStreamBitrateKbps() - currBandwidthBufferKbps);
                  //									if (Math.abs(newAdjust) < Math.abs(currAdjust))
                  {
                    currAdjust = newAdjust;
                  }
                }
                if (currAdjust > 200)
                  currAdjust = 200;
                if (Math.abs(currAdjust) > 10)
                {
                  if (Sage.DBG && !debugPush) System.out.println("Client Buffer size=" + (maxAvailBufferSize - freeSpace) +
                      " estimRateKbps=" + lastEstimatedPushBitrate/1000 + " avgRateKbps=" + lastAverageEstimatedPushBitrate/1000 +
                      " rateKbps=" + fftc.getCurrentStreamBitrateKbps() +
                      " estimStreamRateKbps=" + lastEstimatedStreamBitrate/1000 + " avgStreamRateKbps=" + lastAverageEstimatedStreamBitrate/1000);
                  fftc.dynamicVideoRateAdjust(currAdjust);
                  lastRateAdjustTime = Sage.eventTime();
                  if (Sage.DBG || debugPush) System.out.println("Adjusted bitrate UP to : " + fftc.getCurrentStreamBitrateKbps());
                }
              }
            }

            availBufferSize = Math.min(javaBuff.capacity(), availBufferSize);

            if (bdp != null)
            {
              if (!mpegSrc.canSkipOnNextRead() && bdp.getBytesLeftInClip() > 0 && bdp.getBytesLeftInClip() < availBufferSize)
              {
                availBufferSize = (int)bdp.getBytesLeftInClip();
                if (Sage.DBG) System.out.println("At the end of a BluRay clip; adjust the read buffer size for the next push to be " + availBufferSize);
              }
            }

            int readBufferSize = availBufferSize;
            if (!(transcoded || timeshifted))
            {
              readBufferSize = (int)Math.min(readBufferSize, mpegSrc.availableToRead2(readBufferSize));
              //finalLength - mpegSrc.getReadPos());
              if (readBufferSize <= 0)
              {
                MediaFile currRecFile = null;
                if (uiMgr != null && (currRecFile = SeekerSelector.getInstance().getCurrRecordFileForClient(uiMgr, false)) != null)
                {
                  // Also make sure this device supports fast mux switching
                  CaptureDevice recInput = SeekerSelector.getInstance().getCaptureDeviceControlledByClient(uiMgr);
                  if (!eos && (recInput == null || recInput.supportsFastMuxSwitch()))
                  {
                    if (Sage.DBG) System.out.println("SERVER Buffer size is now ZERO! Trigger local EOS to start the seamless file switch");
                    // Reset the UI timeouts on an EOS so we don't trigger the SS since it considers EOS to be a non-playing state
                    if (uiMgr != null)
                      uiMgr.getRouter().resetInactivityTimers();
                    // We trigger this now so that we cause our transitions when watching live TV to happen early enough
                    eos = true;
                  }
                }
                if (!eos)
                {
                  boolean sendServerEOS = false;
                  if (serverSideTranscoding && mpegSrc != null)
                  {
                    if (mpegSrc.getTranscoder().isTranscodeDone())
                    {
                      if (!((FFMPEGTranscoder) (mpegSrc.getTranscoder())).didTranscodeCompleteOK())
                      {
                        if (Sage.DBG) System.out.println("Detected failure in the transcoder attempt to restart it...");
                        try
                        {
                          mpegSrc.seek(mpegSrc.getLastParsedTimeMillis());
                        }
                        catch (java.io.IOException ioe)
                        {
                          if (Sage.DBG) System.out.println("ERROR restarting the transcoder of:" + ioe);
                          sendServerEOS = true;
                        }
                      }
                      else
                      {
                        if (debugPush) System.out.println("Server is pushing an EOS message to the client");
                        sendServerEOS = true;
                      }
                    }
                  }
                  // Check for an EOS on the client
                  if(!serverSideTranscoding || sendServerEOS)
                  {
                    if (!pushBuffer0(javaBuff, 0, 0x80 | getFlags()))
                    {
                      if (Sage.DBG) System.out.println("pushBuffer call failed; terminating push loop");
                      break;
                    }
                    if (freeSpace<0)
                    {
                      if (Sage.DBG) System.out.println("Received eos from client");
                      if(!eos)
                      {
                        // Reset the UI timeouts on an EOS so we don't trigger the SS since it considers EOS to be a non-playing state
                        if (uiMgr != null)
                          uiMgr.getRouter().resetInactivityTimers();
                        kickVF = true; // I think we need that only once, verify with Jeff...
                        eos = true;
                      }
                      needToPlay = false;
                      pausePush0();
                      currState = PAUSE_STATE;
                    }
                    else
                      try{decoderLock.wait(100);}catch(Exception e){}
                  }
                  else
                    try{decoderLock.wait(100);}catch(Exception e){}
                  //                                    if (Sage.DBG) System.out.println("Pushing EOS to decoder-2");
                  //                                  pushBuffer0(javaBuff, 0, 0x80);
                  //                                eos = true;
                }
                else
                  try{decoderLock.wait(100);}catch(Exception e){}
                kickVF = true;
                continue;
              }
            }
            if(transcoded)
              tcSrc.setPlaybackRate((myRate > 1) ? (int)Math.floor(myRate) :
                ((myRate < 0) ? (int)Math.floor(myRate) : 1));
            else
              mpegSrc.setPlaybackRate((myRate > 1) ? (int)Math.floor(myRate) :
                ((myRate < 0) ? (int)Math.floor(myRate) : 1));
            if(transcoded)
            {
              readBufferSize = (int)Math.min(readBufferSize,
                  tcSrc.availableToRead());
            }
            if (debugPush) System.out.println("About to read buffer of size: " + readBufferSize);
            javaBuff.clear();
            if(transcoded)
            {
              try
              {
                tcSrc.read(javaBuff.array(), 0, readBufferSize);
                javaBuff.position(0).limit(readBufferSize);
              }
              catch (java.io.IOException e)
              {
                System.out.println("I/O error reading in push thread:" + e);
                e.printStackTrace();
              }
            }
            // Since we're doing an NIO transfer we want the NEXT read's clip index, not what we just read
            // But this only applies if we won't be doing a re-seek which could potentially change that
            if (bdp != null && !mpegSrc.canSkipOnNextRead() && lastBluRayIndex != bdp.getClipIndexForNextRead())
            {
              lastBluRayIndex = bdp.getClipIndexForNextRead();
              long ptsOffset = bdp.getClipPtsOffset(lastBluRayIndex);
              if (Sage.DBG) System.out.println("Detected cell boundary for BluRay; send the NewCell command with PTSOffset=" + ptsOffset);
              NewCell0(ptsOffset);
            }
            if (debugPush) System.out.println("about to push buffer");
            int flags = getFlags();
            if (!pushBuffer0(javaBuff, readBufferSize, flags))
            {
              if (Sage.DBG) System.out.println("pushBuffer call failed; terminating push loop");
              break;
            }
            /*						if (pushDumpStream != null)
						{
							try
							{
								pushDumpStream.write(javaBuff, 0, readBufferSize);
							}catch (Exception e)
							{
								System.out.println("ERROR writing push buffer dump stream: " + e);
							}
						}*/
            numPushedBuffers++;
            if (debugPush) System.out.println("buffer was pushed x=" + numPushedBuffers +
                " flag=" + flags + " len="+readBufferSize);
            //if (numPushedBuffers >= 1 && needToPlay)/*DSM520TEMP*/
            if (numPushedBuffers >= 1 && needToPlay)
            {
              playPush0();
              needToPlay = false;
            }
            if (bufferFillPause && videoPTSForPlay != -1)
            {
              //System.out.println("Checking PTS for resuming playback after buffer fill with pts=" + mpegSrc.getLastRawVideoPTS());
              if (mpegSrc.getLastRawVideoPTS() > videoPTSForPlay || freeSpace <= pushBufferSize)
              {
                //System.out.println("Resuming playback after buffer fill with pts=" + mpegSrc.getLastRawVideoPTS());
                //seekPull0(videoPTSForPlay - 300000);
                playPush0();
                bufferFillPause = false;
              }
            }
            if((numPushedBuffers&0x1F)==0/* && (((int)Math.floor(myRate))!=1 || serverSideTranscoding)*/)
            {
              try{
                decoderLock.notifyAll();
                decoderLock.wait(10);}catch(Exception e){}
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
            if (myRate < 0)
              wasReversePlay = true;
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
      private boolean wasReversePlay = false;
    }, "Pusher");
    pushThread.setDaemon(true);
    pushThread.setPriority(Thread.MAX_PRIORITY - Sage.getInt("push_thread_priority_offset", 2));
    pushThread.start();
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

  public void kickPusherThread()
  {
    Pooler.execute(new Runnable() {
      public void run() {
        synchronized (decoderLock) {
          decoderLock.notifyAll();
        }
      }
    });
  }

  public boolean pause()
  {
    if (currState == LOADED_STATE || currState == PLAY_STATE)
    {
      synchronized (this)
      {
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          needToPlay = false;
          bufferFillPause = false;
          pausePush0();
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
    if ((currState == LOADED_STATE || currState == PAUSE_STATE) && !eos)
    {
      synchronized (this)
      {
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          // Delay the play if we're pushing in a high bandwidth environment
          // so the decoder can get some data in it and avoid any init issues
          if (pushMode && numPushedBuffers < 8 && !serverSideTranscoding && rpSrc == null)
            needToPlay = true;
          else
            playPush0();
          bufferFillPause = false;
          currState = PLAY_STATE;
          removeYieldDecoderLock();
          decoderLock.notifyAll();
        }
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
        eos = false;
        try
        {
          waitingForSeek = true;
          addYieldDecoderLock();
          synchronized (decoderLock)
          {
            if (pushMode)
            {
              if (Sage.DBG) System.out.println("seeking numpushbuffers=" + numPushedBuffers + " seekTime=" + seekTimeMillis);

              if (serverSideTranscoding && detailedPushBufferStats && seekTimeMillis > clientReportedMediaTime + timestampOffset &&
                  seekTimeMillis < lastParserTimestamp)
              {
                if (Sage.DBG) System.out.println("Seeking within the push buffer limit crmt=" + clientReportedMediaTime + " to=" + timestampOffset + " lpt=" + lastParserTimestamp + " seek=" + seekTimeMillis);
                seekPull0(seekTimeMillis - timestampOffset);
                lastMediaTime = seekTimeMillis - timestampOffset;
              }
              else if (rpSrc != null)
              {
                rpSrc.sendSeek(seekTimeMillis);
                lastMediaTime = seekTimeMillis;
                lastMediaTimeCacheTime = Sage.eventTime();
                decoderLock.notifyAll();
              }
              else
              {
                if(transcoded)
                  tcSrc.seek(seekTimeMillis);
                else
                  mpegSrc.seek(seekTimeMillis);

                if (currState == PAUSE_STATE && (mcsr != null && mcsr.supportsFrameStep()))
                {
                  sendSeekPullNext = true;
                }
                else if (enableBufferFillPause && currState == PLAY_STATE && !transcoded && mpegSrc.isIFrameAlignEnabled()) // disable for now
                {
                  pausePush0();
                  bufferFillPause = true;
                  videoPTSForPlay = -1;
                  //System.out.println("Paused stream and setting flag for playing after PTS increase");
                }
                flushPush0();
                justSeeked = true;
                // NOTE: This is to workaround the 'not enough space in demux' issue on the 8654 that can happen if we seek too fast. When we added this
                // to try to debug it more, the problem went away...so we'll just leave it in there for now.
                if (hdMediaExtender)
                  freeSpace = 0;

                decoderLock.notifyAll();
                lastBluRayIndex = -1; // to force a newcell for BluRay
                if (serverSideTranscoding)
                {
                  timestampOffset = seekTimeMillis;
                  lastMediaTime = 0;
                  clientReportedMediaTime = 0;
                }
                else
                  lastMediaTime = seekTimeMillis;
                lastMediaTimeCacheTime = Sage.eventTime();
              }
            }
            else
            {
              // Skip the initial seek to zero since we started out there already
              if (!firstSeek || seekTimeMillis > 0)
                seekPull0(seekTimeMillis);
            }
            removeYieldDecoderLock();
            decoderLock.notifyAll();
          }
        }
        catch (java.io.IOException e)
        {
          System.out.println("I/O error seeking:" + e);
          throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
        }
        finally
        {
          waitingForSeek = false;

        }
      }
      if (pushMode && !pushThreadCreated)
      {
        /*			if (serverSideTranscoding && mpegSrc != null && mpegSrc.getTranscoder() != null && !mpegSrc.getTranscoder().isTranscoding())
				{
					try
					{
						mpegSrc.getTranscoder().startTranscode();
					}
					catch (java.io.IOException e)
					{
						System.out.println("ERROR starting transcode engine!:" + e);
						throw new PlaybackException();
					}
				}*/
        pushThreadCreated = true;
        createPushThread();
      }
      firstSeek = false;
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
    if (currMute != x)
    {
      synchronized (this)
      {
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          setMute0(currMute = x);
          removeYieldDecoderLock();
          decoderLock.notifyAll();
        }
      }
    }
  }

  public float setPlaybackRate(float newRate)
  {
    if (Sage.DBG) System.out.println("MiniPlayer.setPlaybackRate(" + newRate + ")");
    // Don't allow modified playback rates if we're using the transcoder!
    // NOTE: Disable smooth FF/REW with the remuxer for now it needs more work!!!
    if (pushMode && (!serverSideTranscoding /*|| usingRemuxer*/))
    {
      // The time may be wrong for a little bit after this so establish our guess
      timeGuessMillis = getMediaTimeMillis();
      guessTimestamp = Sage.eventTime();
      // The push thread should pick this up.
      addYieldDecoderLock();
      synchronized (decoderLock)
      {
        myRate = newRate;
        removeYieldDecoderLock();
        decoderLock.notifyAll();
      }
      return myRate;
    }
    else
    {
      MediaFile currMF = VideoFrame.getMediaFileForPlayer(MiniPlayer.this);
      // Do a skip instead so we actually do something with this command instead of not responding to it
      VideoFrame vf = VideoFrame.getVideoFrameForPlayer(MiniPlayer.this);
      long maxTime = (vf != null && currMF != null) ? currMF.getDuration(vf.getCurrSegment()) : Long.MAX_VALUE;
      try
      {
        if (newRate > 1.0f)
        {
          seek(Math.min(maxTime, Math.max(0, getMediaTimeMillis() + (uiMgr == null ? 15000L : uiMgr.getLong("videoframe/ff_time", 10000L)))));
        }
        else if (newRate < 1.0f)
        {
          seek(Math.min(maxTime, Math.max(0, getMediaTimeMillis() + (uiMgr == null ? -15000L : uiMgr.getLong("videoframe/rew_time", -10000L)))));
        }
      }
      catch (PlaybackException e)
      {
        System.out.println("ERROR doing seek instead of rate change of: " + e);
      }
      return 1.0f;
    }
  }

  public synchronized void setVideoRectangles(java.awt.Rectangle videoSrcRect,
      java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    if(!disableVideoPositioning && clientInStream != null && currHintMajorType != MediaFile.MEDIATYPE_AUDIO)
    {
      if (lastVideoSrcRect == null || lastVideoDestRect == null || !videoSrcRect.equals(lastVideoSrcRect) ||
          !videoDestRect.equals(lastVideoDestRect))
      {
        boolean tookIt;
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          tookIt = setVideoRectangles0(videoSrcRect, videoDestRect);
          removeYieldDecoderLock();
          decoderLock.notifyAll();
        }
        if (tookIt)
        {
          lastVideoSrcRect = (java.awt.Rectangle) videoSrcRect.clone();
          lastVideoDestRect = (java.awt.Rectangle) videoDestRect.clone();
        }
        else
        {
          lastVideoSrcRect = null;
          lastVideoDestRect = null;
        }
      }
    }
  }

  public synchronized float setVolume(float f)
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

  public void stop()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        currState = STOPPED_STATE;
        addYieldDecoderLock();
        synchronized (decoderLock)
        {
          stopPush0();
          removeYieldDecoderLock();
          decoderLock.notifyAll();
        }
      }
    }
    else
      currState = STOPPED_STATE;
  }

  public boolean playControlEx(int playCode, long param1, long param2) throws PlaybackException
  {
    if (hdMediaExtender && (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE))
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
            int audioStreamType = 0xc000;
            int ac3indexOffset = (af != null && usingRemuxer) ? af.getOrderIndex() : 0;
            // If we're transcoding then the original audio stream doesn't matter, just use 0xc0
            // unless we're using the remuxer....
            String streamID = af.getId();
            if (streamID != null && streamID.length() > 0)
            {
              // See if it's just a stream ID or if it's 2 parts
              int dashIdx = streamID.indexOf('-');
              if (dashIdx == -1)
              {
                try
                {
                  if (streamID.length() == 4) // the full ID
                    audioStreamType = Integer.parseInt(streamID, 16);
                  else
                    audioStreamType = (Integer.parseInt(streamID, 16) << 8);
                }
                catch (NumberFormatException nfe)
                {
                  if (Sage.DBG) System.out.println("ERROR parsing audio stream ID " + streamID + " of: " + nfe);
                }
              }
              else
              {
                try
                {
                  audioStreamType = (Integer.parseInt(streamID.substring(0, dashIdx), 16) << 8) |
                      Integer.parseInt(streamID.substring(dashIdx + 1, dashIdx + 3), 16);
                }
                catch (NumberFormatException nfe)
                {
                  if (Sage.DBG) System.out.println("ERROR parsing audio stream ID " + streamID + " of: " + nfe);
                }
              }
            }
            if (Sage.DBG) System.out.println("Setting audio stream for playback to be ID=0x" + Integer.toString(audioStreamType, 16));
            long pftime = getMediaTimeMillis();
            DVDStream(0, pushMode ? audioStreamType : languageIndex);
            matchBDSubpictureToAudio();
            seek(pftime);
          }
        }
      }
      else if (subpicTracks != null && playCode == VideoFrame.DVD_CONTROL_SUBTITLE_TOGGLE)
      {
        subpicOn = !subpicOn;
        synchronized (this)
        {
          long pftime = getMediaTimeMillis();
          String tag = subpicTracks[subpicIndex].getId();
          int target = subpicIndex;
          // See if it's just a stream ID or if it's 2 parts
          if (tag != null && tag.length() > 0)
          {
            int dashIdx = tag.indexOf('-');
            if (dashIdx == -1)
            {
              try
              {
                if (tag.length() == 4) // the full ID
                  target = Integer.parseInt(tag, 16);
                else
                  target = (Integer.parseInt(tag, 16) << 8);
              }
              catch (NumberFormatException nfe)
              {
                if (Sage.DBG) System.out.println("ERROR parsing subpic stream ID " + tag + " of: " + nfe);
              }
            }
            else
            {
              try
              {
                target = (Integer.parseInt(tag.substring(0, dashIdx), 16) << 8) |
                    Integer.parseInt(tag.substring(dashIdx + 1, dashIdx + 3), 16);
              }
              catch (NumberFormatException nfe)
              {
                if (Sage.DBG) System.out.println("ERROR parsing subpic stream ID " + tag + " of: " + nfe);
              }
            }
          }
          if (Sage.DBG) System.out.println((subpicOn ? "Enabling " : "Disabling ") + "subpicture stream " + target);
          if (!subpicOn)
          {
            if (!matchBDSubpictureToAudio())
              DVDStream(1, isMpeg2PS ? (subpicOn ? target : PS_SUBPIC_DISABLE_STREAM) : ((target & 0x1FFF) | (subpicOn ? 0 : SUBPIC_DISABLE_STREAM)));
          }
          else
          {
            DVDStream(1, isMpeg2PS ? (subpicOn ? target : PS_SUBPIC_DISABLE_STREAM) : ((target & 0x1FFF) | (subpicOn ? 0 : SUBPIC_DISABLE_STREAM)));
          }
          seek(pftime);
        }
      }
      else if (subpicTracks != null && playCode == VideoFrame.DVD_CONTROL_SUBTITLE_CHANGE)
      {
        int newSubpicIndex = param1 >= 0 ? (int)param1 : ((subpicIndex + 1) % subpicTracks.length);
        if (newSubpicIndex != subpicIndex || !subpicOn)
        {
          subpicIndex = Math.max(0, Math.min(subpicTracks.length - 1, newSubpicIndex));
          if (!subpicOn)
            subpicOn = true;
          String tag = subpicTracks[subpicIndex].getId();
          int target = subpicIndex;
          // See if it's just a stream ID or if it's 2 parts
          if (tag != null && tag.length() > 0)
          {
            int dashIdx = tag.indexOf('-');
            if (dashIdx == -1)
            {
              try
              {
                if (tag.length() == 4) // the full ID
                  target = Integer.parseInt(tag, 16);
                else
                  target = (Integer.parseInt(tag, 16) << 8);
              }
              catch (NumberFormatException nfe)
              {
                if (Sage.DBG) System.out.println("ERROR parsing subpic stream ID " + tag + " of: " + nfe);
              }
            }
            else
            {
              try
              {
                target = (Integer.parseInt(tag.substring(0, dashIdx), 16) << 8) |
                    Integer.parseInt(tag.substring(dashIdx + 1, dashIdx + 3), 16);
              }
              catch (NumberFormatException nfe)
              {
                if (Sage.DBG) System.out.println("ERROR parsing subpic stream ID " + tag + " of: " + nfe);
              }
            }
          }
          synchronized (this)
          {
            long pftime = getMediaTimeMillis();
            if (Sage.DBG) System.out.println("Enabling subpicture stream " + target);
            DVDStream(1, isMpeg2PS ? target : (target & 0x1FFF));
            seek(pftime);
          }
        }
      }
      else if (bdp != null && playCode == VideoFrame.DVD_CONTROL_CHAPTER_NEXT)
      {
        if (getDVDChapter() < getDVDTotalChapters())
        {
          long newTime = bdp.getChapterStartMsec(getDVDChapter() + 1);
          if (Sage.DBG) System.out.println("Next chapter for BluRay seeking to " + newTime);
          seek(newTime);
        }
      }
      else if (bdp != null && playCode == VideoFrame.DVD_CONTROL_CHAPTER_PREV)
      {
        int currChapter = getDVDChapter();
        if (getMediaTimeMillis() - bdp.getChapterStartMsec(currChapter) > 7000 || currChapter == 1)
        {
          if (Sage.DBG) System.out.println("Prev chapter (restart curr chapter) for BluRay");
          seek(bdp.getChapterStartMsec(currChapter));
        }
        else
        {
          long newTime = bdp.getChapterStartMsec(currChapter - 1);
          if (Sage.DBG) System.out.println("Prev chapter for BluRay seeking to " + newTime);
          seek(newTime);
        }
      }
      else if (bdp != null && playCode == VideoFrame.DVD_CONTROL_CHAPTER_SET)
      {
        long newTime = bdp.getChapterStartMsec((int)param1);
        if (Sage.DBG) System.out.println("Set chapter (" + param1 + ") for BluRay seeking to " + newTime);
        seek(newTime);
      }
      else if (bdp != null && playCode == VideoFrame.DVD_CONTROL_ANGLE_CHANGE)
      {
        /*if (getDVDTotalAngles() > 1)
				{
					currBDAngle++;
					if (currBDAngle > getDVDTotalAngles())
						currBDAngle = 1;
					if (Sage.DBG) System.out.println("Setting BluRay Angle to be " + currBDAngle);
					// Lock the pusher so we can change the file source
					synchronized (this)
					{
						addYieldDecoderLock();
						synchronized (decoderLock)
						{
							bdp.setAngle(currBDAngle);
							seek(getMediaTimeMillis());
						}
					}
				}*/
      }
      else if (bdp != null && playCode == VideoFrame.DVD_CONTROL_TITLE_SET && uiMgr != null)
      {
        if (param1 > 0 && param1 <= bdp.getNumTitles())
          uiMgr.getVideoFrame().setBluRayTargetTitle((int)param1);
        else
          uiMgr.getVideoFrame().playbackControl(0);
      }
    }
    return false;
  }

  private boolean matchBDSubpictureToAudio()
  {
    if (bdp != null && audioTracks != null && !subpicOn)
    {
      // Also init the corresponding subpicture stream for forced subtitles
      String currLang = audioTracks[Math.max(0, Math.min(languageIndex, audioTracks.length - 1))].getLanguage();
      if (subpicTracks != null && currLang != null)
      {
        for (int i = 0; i < subpicTracks.length; i++)
        {
          if (currLang.equals(subpicTracks[i].getLanguage()))
          {
            String tag = subpicTracks[i].getId();
            int target = i;
            // See if it's just a stream ID or if it's 2 parts
            if (tag != null && tag.length() > 0)
            {
              int dashIdx = tag.indexOf('-');
              if (dashIdx == -1)
              {
                try
                {
                  if (tag.length() == 4) // the full ID
                    target = Integer.parseInt(tag, 16);
                  else
                    target = (Integer.parseInt(tag, 16) << 8);
                }
                catch (NumberFormatException nfe)
                {
                  if (Sage.DBG) System.out.println("ERROR parsing subpic stream ID " + tag + " of: " + nfe);
                }
              }
              else
              {
                try
                {
                  target = (Integer.parseInt(tag.substring(0, dashIdx), 16) << 8) |
                      Integer.parseInt(tag.substring(dashIdx + 1, dashIdx + 3), 16);
                }
                catch (NumberFormatException nfe)
                {
                  if (Sage.DBG) System.out.println("ERROR parsing subpic stream ID " + tag + " of: " + nfe);
                }
              }
            }
            if (Sage.DBG) System.out.println("Setting BD subpicture to match audio track for forced subs subID=" + tag);
            synchronized (this)
            {
              DVDStream(1, isMpeg2PS ? (subpicOn ? target : PS_SUBPIC_DISABLE_STREAM) : ((target & 0x1FFF) | SUBPIC_DISABLE_STREAM));
            }
            return true;
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
    if (bdp != null)
      return Math.min(currBDAngle, getDVDTotalAngles());
    return 0;
  }

  public String[] getDVDAvailableLanguages()
  {
    if (hdMediaExtender && audioTracks != null && (currState == PLAY_STATE || currState == PAUSE_STATE))
    {
      MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
      if (mf != null)
      {
        sage.media.format.ContainerFormat cf = (bdp != null) ? bdp.getFileFormat() : mf.getFileFormat();
        if (cf != null && cf.getNumAudioStreams() > 1)
        {
          if (audioSels == null)
            audioSels = cf.getAudioStreamSelectionDescriptors();
          return audioSels;
        }
      }
    }
    return Pooler.EMPTY_STRING_ARRAY;
  }

  public String[] getDVDAvailableSubpictures()
  {
    if (hdMediaExtender && subpicTracks != null && (currState == PLAY_STATE || currState == PAUSE_STATE))
    {
      MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
      if (mf != null)
      {
        sage.media.format.ContainerFormat cf = (bdp != null) ? bdp.getFileFormat() : mf.getFileFormat();
        if (cf != null && cf.getNumSubpictureStreams() > 0)
        {
          if (subpicSels == null)
            subpicSels = cf.getSubpictureStreamSelectionDescriptors();
          return subpicSels;
        }
      }
    }
    return Pooler.EMPTY_STRING_ARRAY;
  }

  public int getDVDChapter()
  {
    if (bdp != null)
      return bdp.getChapter(mpegSrc.getLastParsedTimeMillis() * 45);
    return 0;
  }

  public int getDVDTotalChapters()
  {
    if (bdp != null)
      return bdp.getNumChapters();
    return 0;
  }

  public int getDVDDomain()
  {
    if (bdp != null)
      return 4; // We're always in the movie for BluRays
    return 0;
  }

  public sage.media.format.AudioFormat getCurrAudioFormat()
  {
    if (audioTracks != null && audioTracks.length > 0)
    {
      return audioTracks[Math.min(audioTracks.length - 1, Math.max(0, languageIndex))];
    }
    else
      return null;
  }

  public String getDVDLanguage()
  {
    if (hdMediaExtender && audioTracks != null && (currState == PLAY_STATE || currState == PAUSE_STATE))
    {
      MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
      if (mf != null)
      {
        sage.media.format.ContainerFormat cf = (bdp != null) ? bdp.getFileFormat() : mf.getFileFormat();
        if (cf != null && cf.getNumAudioStreams() > 0)
        {
          if (audioSels == null)
            audioSels = cf.getAudioStreamSelectionDescriptors();
          return audioSels[Math.min(audioSels.length - 1, Math.max(0, languageIndex))];
        }
      }
    }
    return "";
  }

  public String getDVDSubpicture()
  {
    if (hdMediaExtender && subpicTracks != null && (currState == PLAY_STATE || currState == PAUSE_STATE))
    {
      if (!subpicOn) return null;
      MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
      if (mf != null)
      {
        sage.media.format.ContainerFormat cf = (bdp != null) ? bdp.getFileFormat() : mf.getFileFormat();
        if (cf != null && cf.getNumSubpictureStreams() > 0)
        {
          if (subpicSels == null)
            subpicSels = cf.getSubpictureStreamSelectionDescriptors();
          return subpicSels[Math.min(subpicSels.length - 1, Math.max(0, subpicIndex))];
        }
      }
    }
    return "";
  }

  public int getDVDTitle()
  {
    if (bdp != null)
      return currBDTitle;
    return 0;
  }

  public String getBluRayTitleDesc(int titleNum)
  {
    if (bdp != null)
      return bdp.getTitleDesc(titleNum);
    else
      return "";
  }

  public int getDVDTotalAngles()
  {
    if (bdp != null)
      return bdp.getNumAngles();
    return 0;
  }

  public int getDVDTotalTitles()
  {
    if (bdp != null)
      return bdp.getNumTitles();
    return 0;
  }

  public float getCurrentAspectRatio()
  {
    return 0;
  }

  public void sendSubpicPalette(byte[] palette)
  {
    if (palette == null || palette.length != 64)
      throw new IllegalArgumentException("Invalid subpicture palette passed to miniplayer!");
    synchronized (this)
    {
      addYieldDecoderLock();
      synchronized (decoderLock)
      {
        CLUT0(64, palette);
        removeYieldDecoderLock();
        decoderLock.notifyAll();
      }
    }
  }

  public void sendSubpicBitmap(byte[] data, int size, int extraFlags)
  {
    if (data == null || size == 0)
      return;
    synchronized (this)
    {
      addYieldDecoderLock();
      synchronized (decoderLock)
      {
        int offset = 0;
        while (size > 0)
        {
          // Subpic buffers are limited to 32k
          int currSend = Math.min(32768, size);
          pushBuffer0(java.nio.ByteBuffer.wrap(data, offset, currSend), currSend, PUSHBUFFER_SUBPIC_FLAG | extraFlags);
          size -= currSend;
          offset += currSend;
        }
        removeYieldDecoderLock();
        decoderLock.notifyAll();
      }
    }
  }

  protected long initDriver0(int videoFormat)
  {
    if (Sage.DBG) System.out.println("initDriver0()");
    clientSocket = (mcsr == null) ? MiniClientSageRenderer.getPlayerSocketChannel(null, null) :
      mcsr.getPlayerSocketChannel();

    String clientName = (uiMgr == null ? "EXTERNAL" : uiMgr.getLocalUIClientName());
    if (clientSocket == null) return 0;
    boolean retry = true;
    while (true)
    {
      try
      {
        clientInStream = new FastPusherReply(clientSocket);
        sockBuf.clear();
        sockBuf.putInt(MEDIACMD_INIT<<24 | 4 );
        sockBuf.putInt(videoFormat);
        sockBuf.flip();
        while (sockBuf.hasRemaining())
          clientSocket.write(sockBuf);
        if (Sage.DBG) System.out.println("MiniPlayer established for " + clientName);
        return clientInStream.readInt()!=0 ? 1 : 0;
      }
      catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("Error w/ MiniPlayer socket of:" + e);
        if (!retry)
          return 0;
        if (Sage.DBG) System.out.println("Retrying MiniPlayer connection....");
        try{clientSocket.close();}catch(Exception e2){}
        retry = false;
        clientSocket = (mcsr == null) ? MiniClientSageRenderer.getPlayerSocketChannel(null, null) : mcsr.getPlayerSocketChannel();
      }
    }
  }

  protected void seekPull0(long seekTimeMillis)
  {
    if (Sage.DBG) System.out.println("seekPull0(" + seekTimeMillis + ")");
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_SEEK<<24 | 8 );
      sockBuf.putLong(seekTimeMillis);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
  }

  protected boolean openURL0(String url)
  {
    if (Sage.DBG) System.out.println("openURL0(" + url + ")");
    int retryCount = clientSocket == null ? 2 : 1;
    if (clientSocket == null)
    {
      clientSocket = (mcsr == null) ? MiniClientSageRenderer.getPlayerSocketChannel(null, null) : mcsr.getPlayerSocketChannel();
      if (clientSocket == null)
        return false;
      if (Sage.DBG) System.out.println("MiniPlayer established for " + (uiMgr == null ? "EXTERNAL" : uiMgr.getLocalUIClientName()));
    }
    while (retryCount > 0)
    {
      retryCount--;
      try
      {
        if (clientInStream == null)
        {
          clientInStream = new FastPusherReply(clientSocket);
        }
        // 11/17/2015 Narflex - This was I18N_CHARSET for all cases in V7; then for
        // some reason we made it I18N_CHARSET for EMBEDDED only...and getBytes() for
        // the other cases. I'm changing it back to the old way of always I18N_CHARSET.
        byte []b = url.getBytes(Sage.I18N_CHARSET);
        sockBuf.clear();
        sockBuf.putInt(MEDIACMD_OPENURL<<24 | b.length+1+4);
        sockBuf.putInt(b.length+1);
        sockBuf.put(b, 0, b.length);
        sockBuf.put((byte)0);
        sockBuf.flip();
        while (sockBuf.hasRemaining())
          clientSocket.write(sockBuf);
        int res = clientInStream.readInt();
        return res!=0;
      }catch(Exception e)
      {
        if (retryCount > 0)
        {
          if (Sage.DBG) System.out.println("Error w/ MiniPlayer socket of:" + e);
          if (Sage.DBG) System.out.println("Retrying MiniPlayer connection....");
          try{clientSocket.close();}catch(Exception e2){}
          clientSocket = (mcsr == null) ? MiniClientSageRenderer.getPlayerSocketChannel(null, null) : mcsr.getPlayerSocketChannel();
          try { clientInStream.close(); } catch(Exception e1){}
          clientInStream = null;
        }
        System.out.println(e);
        e.printStackTrace();
      }
    }
    return false;
  }

  protected long getMediaTimeMillis0()
  {
    if (Sage.eventTime() - lastMediaTimeCacheTime < 100 || clientSocket == null)
      return lastMediaTime;
    //if (Sage.DBG) System.out.println("getMediaTimeMillis0() cachetime is" + Sage.df(lastMediaTimeCacheTime));
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
        long currMediaTime = clientInStream.readInt();
        if (currMediaTime == 0xFFFFFFFF)
        {
          // Indicates EOS from the client
          if (!eos)
          {
            if (Sage.DBG) System.out.println("MiniPlayer received an EOS when getting the media time - set the EOS flag");
            // Reset the UI timeouts on an EOS so we don't trigger the SS since it considers EOS to be a non-playing state
            if (uiMgr != null)
              uiMgr.getRouter().resetInactivityTimers();
            eos = true;
            VideoFrame.getVideoFrameForPlayer(MiniPlayer.this).kick();
          }
          lastMediaTimeCacheTime = Sage.eventTime();
          return lastMediaTime;
        }
        // I don't see any good reason to do this interpolation on embedded since if we sent a request to the miniclient above
        // then we should have a pretty accurate time counter right now
        if (lastMediaTimeBase == currMediaTime && currState == PLAY_STATE)
        {
          lastMediaTime = (Sage.eventTime() - lastMediaTimeCacheTime) + lastMediaTime;
        }
        else if (hdMediaExtender && Sage.eventTime() - lastMediaTimeCacheTime < 2000 && currMediaTime > 80000000 &&
            currMediaTime - lastMediaTime > 70000000 && myRate < 0)
        {
          // Set them back to zero in this case. This is a bug on the HD extender where it returns timestamps that are near the PTS rollover
          // value if you're rewinding at a high speed towards the beginning of the file
          lastMediaTime = lastMediaTimeBase = 0;
        }
        else
          lastMediaTime = lastMediaTimeBase = currMediaTime;
        lastMediaTimeCacheTime = Sage.eventTime();
        if (detailedPushBufferStats)
        {
          clientReportedPlayState = clientInStream.readByte();
          if (clientReportedPlayState == EOS_STATE && !eos)
          {
            if (Sage.DBG) System.out.println("Client reported play state indicates EOS, set the flag-2");
            // Reset the UI timeouts on an EOS so we don't trigger the SS since it considers EOS to be a non-playing state
            if (uiMgr != null)
              uiMgr.getRouter().resetInactivityTimers();
            eos = true;
            VideoFrame.getVideoFrameForPlayer(MiniPlayer.this).kick();
          }
        }
        removeYieldDecoderLock();
        decoderLock.notifyAll();
        return lastMediaTime;
      }catch(Exception e)
      {
        if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
        e.printStackTrace();
        connectionError();
      }
      removeYieldDecoderLock();
      decoderLock.notifyAll();
    }
    return lastMediaTime; // otherwise if a connection is killed while viewing we won't be able to track the watched time in it (this used to return 0 here)
  }

  protected boolean closeDriver0()
  {
    if (Sage.DBG) System.out.println("closeDriver0()");
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_DEINIT<<24 | 0);
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      // Why do we care?? This just makes it take longer to close.
      // 8/29/07 - Narflex - We need to wait for the return if the client is using pull mode playback so that the
      // file handle gets closed in case we're about to delete the file.
      // 2/8/10 - Narflex - We need to wait for this to finish in order to know that the video surface has been
      // properly released. Otherwise we may try to display an image for the background, and that won't work properly.
      //			if (!pushMode)
      clientInStream.readInt();
      return true;
    }catch(Exception e)
    {
      // Supress these errors because they could be from an aynchronous client shutdown if we're using the remote pusher
      //System.out.println(e);
      //e.printStackTrace();
    }
    finally
    {
      try
      {
        clientInStream.close();
      }catch (Exception e){}
      try
      {
        clientSocket.close();
      }catch (Exception e){}
      clientInStream = null;
      clientSocket = null;

    }
    return false;
  }

  protected boolean setMute0(boolean x)
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
      return clientInStream.readInt()!=0;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
    }
    return false;
  }

  protected boolean stopPush0()
  {
    if (Sage.DBG) System.out.println("stopPush0()");
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_STOP<<24 | 0 );
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      return clientInStream.readInt()!=0;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
    }
    return false;
  }

  protected boolean pausePush0()
  {
    if (Sage.DBG) System.out.println("pausePush0()");
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_PAUSE<<24 | 0 );
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      if (rpSrc != null)
        rpSrc.sendPause();
      return clientInStream.readInt()!=0;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
    }
    return false;
  }

  protected boolean playPush0()
  {
    if (Sage.DBG) System.out.println("playPush0()");
    try
    {
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_PLAY<<24 | 0 );
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      if (rpSrc != null)
        rpSrc.sendPlay();
      return clientInStream.readInt()!=0;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
    }
    return false;
  }

  protected boolean flushPush0()
  {
    if (Sage.DBG) System.out.println("flushPush0()");
    lastParserTimestamp = 0;
    lastParserTimestampBytePos = 0;
    if (numPushedBuffers > 0 || mediaExtender)
    {
      try
      {
        sockBuf.clear();
        sockBuf.putInt(MEDIACMD_FLUSH<<24 | 0 );
        sockBuf.flip();
        while (sockBuf.hasRemaining())
          clientSocket.write(sockBuf);
        boolean rv = clientInStream.readInt()!=0;
        if (currMute && mediaExtender)
        {
          // Flushing the decoder resets the mute state on the MVP
          setMute0(currMute);
        }
      }catch(Exception e)
      {
        if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
        e.printStackTrace();
        connectionError();
      }
    }
    return false;
  }
  private long lastTime = -1;
  private long pushedBytes = 0;
  private int numWaits = 0;
  private int numPushes = 0;
  protected boolean pushBuffer0(java.nio.ByteBuffer buf, int size, int flags)
  {
    try
    {
      long t1 = Sage.eventTime();
      if (lastTime == -1 || t1 - lastTime > 10000)
      {
        lastTime = t1;
        pushedBytes = 0;
      }
      if (size > 0 && (detailedPushBufferStats || dynamicRateAdjust))
      {
        //				if (Sage.DBG && (numPushedBuffers % (lowBandwidth ? 50 : 500) == 0))
        //					System.out.println("Pusher BWestim=" + lastAverageEstimatedPushBitrate);
        pushTimerEntry();
      }
      boolean alreadyFilledBuffer = false;
      if (buf != null && size > 0 && !transcoded && (flags & PUSHBUFFER_SUBPIC_FLAG) == 0 &&
          (bufferFillPause || sendSeekPullNext || !useNioTransfers || (bdp != null && mpegSrc.canSkipOnNextRead())))
      {
        alreadyFilledBuffer = true;
        buf.clear().limit(size);
        mpegSrc.transfer(null, size, buf);
        if (bufferFillPause && videoPTSForPlay == -1)
        {
          videoPTSForPlay = mpegSrc.getLastIFramePTS() + Sage.getInt("miniclient/video_pts_gap_for_play", 45000);
          //System.out.println("Set videoPTSForPlay=" + videoPTSForPlay);
        }
        if (justSeeked)
        {
          // This is used for debugging alignment on seek
          /*System.out.println("Post Seek Push Buffer Dump:");
					StringBuffer sb = new StringBuffer();
					for (int i = 0; i < 2048; i++)
					{
						int x = buf.get(i) & 0xFF;
						if (x < 16)
							sb.append('0');
						sb.append(Integer.toString(x, 16));
						if (sb.length() == 64)
						{
							System.out.println(sb.toString());
							sb.setLength(0);
						}
					}*/
          justSeeked = false;
        }
      }
      // Check for the special case with BluRay where we jump a cell boundary while doing the reseek to the proper PTS
      if (bdp != null && alreadyFilledBuffer && lastBluRayIndex != bdp.getCurrClipIndex())
      {
        lastBluRayIndex = bdp.getCurrClipIndex();
        long ptsOffset = bdp.getClipPtsOffset(lastBluRayIndex);
        if (Sage.DBG) System.out.println("Detected cell boundary for BluRay-2; send the NewCell command with PTSOffset=" + ptsOffset);
        NewCell0(ptsOffset);
      }
      if (sendSeekPullNext && alreadyFilledBuffer)
      {
        sendSeekPullNext = false;
        if (currState == PAUSE_STATE && mpegSrc.isIFrameAlignEnabled())
        {
          // Send the target time that we should decode to enable seeking while paused
          long targetPTS = mpegSrc.getLastIFramePTS();
          if (targetPTS > 0)
          {
            if (Sage.DBG) System.out.println("Sending seek pull command to enable seek while paused targetPTS=" + targetPTS);
            seekPull0(targetPTS + 5000);
          }
        }
      }
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_PUSHBUFFER<<24 | (size+(detailedPushBufferStats ? 18 : 8) ));
      sockBuf.putInt(size);
      sockBuf.putInt(flags);
      long currParserTimestamp = (mpegSrc != null) ? mpegSrc.getLastParsedDTSMillis() : (tcSrc != null ? tcSrc.getLastParsedTimeMillis() : 0);
      if (detailedPushBufferStats)
      {
        // Send an extra 10 bytes for statistics info. Bandwidths are short term average measurements from the last push
        // we did. The mux time is the time at the end of the data that's currently being pushed.
        // 2 bytes for the estimated channel bandwidtdh in kbps
        // 2 bytes for the estimated data rate in kbps
        // 2 bytes for the target data rate in kbps
        // 4 bytes for the server's mux time in milliseconds
        sockBuf.putShort((short) (Math.min(Short.MAX_VALUE, lastEstimatedPushBitrate/1000)));
        sockBuf.putShort((short) (Math.min(Short.MAX_VALUE, lastEstimatedStreamBitrate/1000)));
        if (mpegSrc != null && mpegSrc.getTranscoder() instanceof FFMPEGTranscoder)
        {
          FFMPEGTranscoder fftc = ((FFMPEGTranscoder)mpegSrc.getTranscoder());
          sockBuf.putShort((short) fftc.getCurrentStreamBitrateKbps());
        }
        else
          sockBuf.putShort((short)0);
        sockBuf.putInt((int)(currParserTimestamp - timestampOffset));
      }
      sockBuf.flip();
      if(buf!=null && size > 0)
      {
        if ((flags & PUSHBUFFER_SUBPIC_FLAG) != 0 || transcoded) // transcoded implies MP3StreamWrapper
        {
          dbuf[0] = sockBuf;
          dbuf[1] = buf;
          while (sockBuf.hasRemaining() || buf.hasRemaining())
            clientSocket.write(dbuf);
        }
        else
        {
          if (useNioTransfers && !alreadyFilledBuffer && pushDumpStream == null)
          {
            while (sockBuf.hasRemaining())
              clientSocket.write(sockBuf);
            mpegSrc.transfer(clientSocket, size, buf);
          }
          else
          {
            if (!alreadyFilledBuffer)
            {
              buf.clear().limit(size);
              mpegSrc.transfer(null, size, buf);
            }
            dbuf[0] = sockBuf;
            dbuf[1] = buf;
            while (sockBuf.hasRemaining() || buf.hasRemaining())
              clientSocket.write(dbuf);
            if (pushDumpStream != null)
            {
              buf.rewind();
              pushDumpStream.write(buf);
            }
          }
        }
      }
      else
      {
        while (sockBuf.hasRemaining())
          clientSocket.write(sockBuf);
      }
      long t2 = Sage.eventTime();
      pushedBytes += size;
      if (bwDebug && Sage.DBG && numPushedBuffers % 50 == 0 && t2 > lastTime && size > 0)
        System.out.println("BW=" + ((pushedBytes * 8000) / (t2 - lastTime)) + " numPushes=" + numPushes + " numWaits=" + numWaits);
      numPushes++;
      if (size == 0 || !pushMode || (!useAsyncReplies && freeSpace <= pushBufferSize))
      {
        numWaits++;
        freeSpace = clientInStream.readInt();
        if (detailedPushBufferStats)
        {
          clientReportedMediaTime = clientInStream.readInt();
          clientReportedPlayState = clientInStream.readByte();
        }
        if (debugPush) System.out.println("Read the reply from the push call size=" + size + " freeSpace=" + freeSpace);
      }
      else
      {
        clientInStream.lazyReadRegister(size);
      }
      if (size > 0 && (detailedPushBufferStats || dynamicRateAdjust))
        addBytesToPushCalc(size);
      // Calculate the bandwidth of the stream data we're trying to send
      if (currParserTimestamp > lastParserTimestamp && mpegSrc != null &&
          (mpegSrc.isLastTimestampVideo() || currHintMajorType == MediaFile.MEDIATYPE_AUDIO))
      {
        if ((detailedPushBufferStats || dynamicRateAdjust) && lastParserTimestampBytePos > 0)
        {
          int currEstim = Math.round(1000*((((float)mpegSrc.getLastParsedDTSPackBytePos() - lastParserTimestampBytePos) * 8) /
              (currParserTimestamp - lastParserTimestamp)));
          //System.out.println("currEstim=" + currEstim + " lastPos=" + lastParserTimestampBytePos + " currPos=" + mpegSrc.getLastParsedDTSPackBytePos() +
          //	" lastTime=" + lastParserTimestamp + " currTime=" + currParserTimestamp);
          if (lastEstimatedStreamBitrate == 0)
          {
            lastAverageEstimatedStreamBitrate = lastEstimatedStreamBitrate = currEstim;
            streamBitrateStats[0] = currEstim;
            streamBitrateStatsWeights[0] = currParserTimestamp - lastParserTimestamp;
            streamBitrateStatsIndex = 1;
          }
          else
          {
            streamBitrateStats[streamBitrateStatsIndex] = currEstim;
            streamBitrateStatsWeights[streamBitrateStatsIndex] = currParserTimestamp - lastParserTimestamp;
            streamBitrateStatsIndex = (streamBitrateStatsIndex + 1) % streamBitrateStats.length;
            long avgEstAgg = 0;
            long avgEstAggWeights = 0;
            for (int i = 0; i < streamBitrateStats.length; i++)
            {
              avgEstAgg += ((long)streamBitrateStats[i]) * streamBitrateStatsWeights[i];
              avgEstAggWeights += streamBitrateStatsWeights[i];
            }
            lastEstimatedStreamBitrate = currEstim;
            lastAverageEstimatedStreamBitrate = (int)(avgEstAgg / avgEstAggWeights);
          }
        }
        lastParserTimestamp = currParserTimestamp;
        lastParserTimestampBytePos = mpegSrc.getLastParsedDTSPackBytePos();
      }

      maxAvailBufferSize = Math.max(maxAvailBufferSize, freeSpace);
      if (detailedPushBufferStats)
      {
        lastDetailedBufferUpdate = Sage.eventTime();
        if (Sage.DBG && debugPush && mpegSrc != null)
        {
          System.out.println("Client Play Time=" + clientReportedMediaTime  + " svrTime=" + (mpegSrc.getLastParsedTimeMillis() - timestampOffset) +
              " diff=" + (mpegSrc.getLastParsedTimeMillis() - clientReportedMediaTime - timestampOffset) + " estimRate=" + lastEstimatedStreamBitrate +
              " estimAvgRate=" + lastAverageEstimatedStreamBitrate);
        }

        if (videoDimensions == null && (clientReportedPlayState == PAUSE_STATE || clientReportedPlayState == PLAY_STATE) &&
            currHintMajorType != MediaFile.MEDIATYPE_AUDIO)
        {
          videoDimensions = getVideoDimensions0();
          if (Sage.DBG) System.out.println("Got video dimensions from push player of:" + videoDimensions);
          // Force a UI refresh of the whole screen so we properly position the video now
          if (uiMgr != null)
          {
            ZRoot rooty = uiMgr.getRootPanel();
            if (rooty != null)
            {
              rooty.appendToDirty(new java.awt.Rectangle(0, 0, rooty.getWidth(), rooty.getHeight()));
            }
          }
        }
      }
      //if(freeSpace>32768) freeSpace=32768;
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
    }
    return false;
  }

  protected java.awt.Dimension getVideoDimensions0()
  {
    if (!mediaExtender && detailedPushBufferStats)
    {
      if (!pushMode || (clientReportedPlayState == PLAY_STATE || clientReportedPlayState == PAUSE_STATE))
      {
        try
        {
          sockBuf.clear();
          sockBuf.putInt(MEDIACMD_GETVIDEORECT<<24 | 0);
          sockBuf.flip();
          while (sockBuf.hasRemaining())
            clientSocket.write(sockBuf);
          short rectWidth = clientInStream.readShort();
          short rectHeight = clientInStream.readShort();
          if (rectWidth > 0 && rectHeight > 0)
            return new java.awt.Dimension(rectWidth, rectHeight);
          else
          {
            if (!pushMode)
            {
              MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
              if (mf != null)
              {
                sage.media.format.ContainerFormat cf = (bdp != null) ? bdp.getFileFormat() : mf.getFileFormat();
                if (cf != null)
                {
                  sage.media.format.VideoFormat vidForm = cf.getVideoFormat();
                  if (vidForm != null && vidForm.getWidth() > 0 && vidForm.getHeight() > 0)
                  {
                    return new java.awt.Dimension(vidForm.getWidth(), vidForm.getHeight());
                  }
                }
              }
            }
            return null;
          }
        }catch(Exception e)
        {
          System.out.println(e);
          e.printStackTrace();
          return null;
        }
      }
      else
        return null;
    }
    else
      return new java.awt.Dimension(720,480);
  }

  private void connectionError()
  {
    // Don't forcibly kill the UI if we had a client/server problem
    if (mcsr != null)
      mcsr.connectionError();
  }

  protected boolean setVideoRectangles0(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect)
  {
    if(clientSocket != null && clientInStream != null)
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
        clientInStream.readInt();
        return true;
      }catch(Exception e)
      {
        if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
        e.printStackTrace();
        connectionError();
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
      return clientInStream.readInt()/65535.0f;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
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
      return clientInStream.readInt()/65535.0f;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
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
      clientInStream.readInt();
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
    }
    return true;
  }

  protected boolean DVDStream(int type, int stream)
  {
    try
    {
      addYieldDecoderLock();
      synchronized (decoderLock)
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
        streamStatus = clientInStream.readInt();
        removeYieldDecoderLock();
        decoderLock.notifyAll();
        return true;
      }
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
    }
    return false;
  }

  protected boolean NewCell0(long ptsOffset)
  {
    try
    {
      int cellStatus;
      sockBuf.clear();
      sockBuf.putInt(MEDIACMD_DVD_NEWCELL<<24 |
          (8));
      sockBuf.putInt(4);
      sockBuf.putInt((int)(ptsOffset & 0xFFFFFFFF));
      sockBuf.flip();
      while (sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      cellStatus = clientInStream.readInt();
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
    }
    return false;
  }

  protected boolean CLUT0(int size, byte[] buf)
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
      spuStatus = clientInStream.readInt();
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      connectionError();
    }
    return false;
  }


  private long pushTimerBase;
  private long pushTimerStopTime; // if non-zero, then its stopped
  private long pushTimerBytes;
  private final long[] pushStatHolder = new long[2];
  private int sucessivePushStatDiscards;
  private boolean dataRecvdForLastResume = true; // indicates the addBytesToPushCalc call was made for the corresponding pushTimerEntry call
  private void pushTimerEntry()
  {
    synchronized (pushStatHolder)
    {
      dataRecvdForLastResume = false;
      resumePushTimer();
      if (pushTimerBase == 0)
        pushTimerBase = Sage.eventTime();
    }
  }

  private void addBytesToPushCalc(int size)
  {
    synchronized (pushStatHolder)
    {
      dataRecvdForLastResume = true;
      pushTimerBytes += size;
    }
  }

  private boolean hasNewPushStats()
  {
    long currTime = Sage.eventTime();
    if (pushTimerBytes > 50000 && currTime - pushTimerBase > 50)
    {
      return true;
    }
    return false;
  }

  private long[] getNewPushStat()
  {
    synchronized (pushStatHolder)
    {
      long currTime = Sage.eventTime();
      pushStatHolder[0] = pushTimerBytes * 8000 / (currTime - pushTimerBase);
      pushStatHolder[1] = currTime - pushTimerBase;
      pushTimerBase = currTime;
      pushTimerBytes = 0;
      pushStatHolder[1] = 1; // REMOVE THE WEIGHTS
      // Sanity check for bandwidth values that are totally off due to forced waits removing all the delay
      if (pushStatHolder[0] > 3 * lastAverageEstimatedPushBitrate && sucessivePushStatDiscards < 5 &&
          pushStatHolder[0] > 500000 && lowBandwidth)
      {
        if (debugPush) System.out.println("DISCARDING push stat because it's too large avg=" + lastAverageEstimatedPushBitrate +
            " successiveDiscards=" + sucessivePushStatDiscards + " curr=" + pushStatHolder[0]);
        sucessivePushStatDiscards++;
        pushStatHolder[0] = 0;
      }
      else
        sucessivePushStatDiscards = 0;
    }
    return pushStatHolder;
  }

  private void suspendPushTimer()
  {
    if (pushTimerStopTime == 0)
    {
      if (debugPush) System.out.println("SUSPENDING the push bandwidth timer");
      pushTimerStopTime = Sage.eventTime();
    }
  }

  private void resumePushTimer()
  {
    if (pushTimerStopTime > 0)
    {
      long currTime = Sage.eventTime();
      pushTimerBase += (currTime - pushTimerStopTime);
      pushTimerStopTime = 0;
      if (debugPush) System.out.println("RESUMING the push bandwidth timer");
    }
  }

  private class FastPusherReply implements Runnable
  {
    public FastPusherReply(java.nio.channels.SocketChannel is)
    {
      in = is;
      alive = true;
      bb = java.nio.ByteBuffer.allocate(64);
    }

    public void run()
    {
      // Asynchronously process the replies from the pusher here. We then take control
      // of when the push BW timer should be stopped as well; and much more accurately then this can
      // be done in the pusher thread itself. We'll also get reliable media time/state updates as well
      // since they'll be processed right when they're received rather then when the pusher has time.
      try
      {
        int timeoutRetries = 1;
        while (alive)
        {
          if (extraRepliesToRead > 0)
          {
            try
            {
              int x = readIntX();
              synchronized (this)
              {
                if (x <= 0)
                  freeSpace = x;
                else
                  freeSpace = Math.max(0, x - (extraRepliesToRead - 1) * pushBufferSize);
                if (debugPush) System.out.println("Did the async read for the pusher reply freeSpace=" + freeSpace +
                    " repliesLeft=" + (extraRepliesToRead-1) + " replyFreeSpace=" + x);
              }
            }
            catch (java.net.SocketTimeoutException ste)
            {
              // Timeouts should not occur...but we'll give them a single retry just in case
              if (timeoutRetries > 0)
              {
                timeoutRetries--;
                if (Sage.DBG) System.out.println("Async pusher reply timed out waiting for a response...try again...");
                continue;
              }
              else
                throw ste;
            }
            if (detailedPushBufferStats)
            {
              clientReportedMediaTime = readIntX();
              clientReportedPlayState = readByteX();
            }
            // Calculate the bandwidth for what we're actually sending across the channel
            if ((detailedPushBufferStats || dynamicRateAdjust) && hasNewPushStats())
            {
              //int currEstim = Math.round(1000*((((float)size + 12) * 8) / (t2 - t1)));
              long[] currData = getNewPushStat();
              int currEstim = (int) currData[0];
              if (currEstim > 0)
              {
                if (lastEstimatedPushBitrate == 0)
                {
                  lastAverageEstimatedPushBitrate = lastEstimatedPushBitrate = currEstim;
                  pushBitrateStats[0] = currEstim;
                  pushBitrateStatsWeights[0] = currData[1];
                  pushBitrateStatsIndex = 1;
                }
                else
                {
                  pushBitrateStats[pushBitrateStatsIndex] = currEstim;
                  pushBitrateStatsWeights[pushBitrateStatsIndex] = currData[1];
                  pushBitrateStatsIndex = (pushBitrateStatsIndex + 1) % pushBitrateStats.length;
                  int calcIndex = pushBitrateStatsIndex - NUM_SAMPLES_BANDWIDTH_ESTIMATE;
                  if (calcIndex < 0)
                    calcIndex += pushBitrateStats.length;
                  long estAgg = 0;
                  long estAggWeights = 0;
                  long avgEstAgg = 0;
                  long avgEstWeights = 0;
                  for (int i = 0; i < pushBitrateStats.length; i++, calcIndex++)
                  {
                    calcIndex = calcIndex % pushBitrateStats.length;
                    if (i < NUM_SAMPLES_BANDWIDTH_ESTIMATE)
                    {
                      estAgg += ((long)pushBitrateStats[calcIndex]) * pushBitrateStatsWeights[calcIndex];
                      estAggWeights += pushBitrateStatsWeights[calcIndex];
                    }
                    avgEstAgg += ((long)pushBitrateStats[calcIndex]) * pushBitrateStatsWeights[calcIndex];
                    avgEstWeights += pushBitrateStatsWeights[calcIndex];
                  }
                  lastEstimatedPushBitrate = (int)(estAgg / estAggWeights);
                  lastAverageEstimatedPushBitrate = (int)(avgEstAgg / avgEstWeights);
                }
                if (debugPush) System.out.println("Adding BW ESTIMATE " + currEstim/1000 + " lastEstim=" + lastEstimatedPushBitrate + " lastAvg=" + lastAverageEstimatedPushBitrate);
              }
            }
            synchronized (this)
            {
              extraRepliesToRead--;
            }
          }
          else
          {
            synchronized (this)
            {
              if (extraRepliesToRead == 0)
              {
                // Suspend the timer because there's no data in the channel right now
                if ((detailedPushBufferStats || dynamicRateAdjust) && dataRecvdForLastResume)
                  suspendPushTimer();
                notify();
                try
                {
                  wait(10000);
                }
                catch (InterruptedException e){}
              }
            }
          }
          timeoutRetries = 1;
        }
      }
      catch (java.io.IOException e)
      {
        if (alive && Sage.DBG)
        {
          System.out.println("PusherReply thread terminated with exception:" + e);
          e.printStackTrace();
        }
      }
      finally
      {
        alive = false;
      }
    }

    public synchronized void lazyReadRegister(int bufSize)
    {
      extraRepliesToRead++;
      freeSpace = Math.max(0, freeSpace - bufSize);
      if (debugPush) System.out.println("Adjusted freeSpace from push ahead to be:" + freeSpace);
      if (!startedReplyThread && alive && useAsyncReplies)
      {
        Pooler.execute(this, "PusherReply");
        startedReplyThread = true;
      }
      else
      {
        if (useAsyncReplies)
          notify();
      }
    }

    public void close()  throws java.io.IOException
    {
      alive = false;
      in.close();
    }

    // These are the only 3 read calls we use
    public byte readByte() throws java.io.IOException
    {
      checkLazies();
      return readByteX();
    }
    public int readInt() throws java.io.IOException
    {
      checkLazies();
      return readIntX();
    }
    public int getLazyReplyCount()
    {
      return extraRepliesToRead;
    }
    private byte readByteX() throws java.io.IOException
    {
      bb.clear();
      bb.limit(1);
      try
      {
        TimeoutHandler.registerTimeout(timeout, in);
        int num = in.read(bb);
        if (num < 0)
          throw new java.io.EOFException();
      }
      finally
      {
        TimeoutHandler.clearTimeout(in);
      }
      bb.flip();
      return bb.get();
    }
    private int readIntX() throws java.io.IOException
    {
      bb.clear().limit(4);
      try
      {
        TimeoutHandler.registerTimeout(timeout, in);
        do{
          int x = in.read(bb);
          if (x < 0)
            throw new java.io.EOFException();
        }while(bb.remaining() > 0);
      }
      finally
      {
        TimeoutHandler.clearTimeout(in);
      }
      bb.flip();
      return bb.getInt();
    }
    public short readShort() throws java.io.IOException
    {
      checkLazies();
      bb.clear().limit(2);
      try
      {
        TimeoutHandler.registerTimeout(timeout, in);
        do{
          int x = in.read(bb);
          if (x < 0)
            throw new java.io.EOFException();
        }while(bb.remaining() > 0);
      }
      finally
      {
        TimeoutHandler.clearTimeout(in);
      }
      bb.flip();
      return bb.getShort();
    }
    private synchronized void checkLazies() throws java.io.IOException
    {
      if (useAsyncReplies)
        notify();
      while (alive && extraRepliesToRead > 0)
      {
        if (useAsyncReplies)
        {
          try
          {
            wait(500);
          }
          catch (InterruptedException e)
          {}
        }
        else
        {
          try
          {
            TimeoutHandler.registerTimeout(timeout, in);
            int x = readIntX();
            if (x <= 0)
              freeSpace = x;
            else
              freeSpace = Math.max(0, x - (extraRepliesToRead - 1) * pushBufferSize);
            if (detailedPushBufferStats)
            {
              clientReportedMediaTime = readIntX();
              clientReportedPlayState = readByteX();
            }
          }
          finally
          {
            TimeoutHandler.clearTimeout(in);
          }
          extraRepliesToRead--;
        }
      }
    }

    private int extraRepliesToRead;
    private java.nio.channels.SocketChannel in;
    private boolean alive;
    private boolean startedReplyThread;
    private java.nio.ByteBuffer bb;
  }


  protected volatile int currState;
  protected volatile java.io.File currFile;
  protected byte currHintMajorType;
  protected byte currHintMinorType;
  protected String currHintEncoding;

  protected volatile FastMpeg2Reader mpegSrc;
  protected volatile Mpeg2Transcoder tcSrc;
  protected volatile RemotePusherClient rpSrc;

  protected final Object decoderLock = new Object();

  protected java.awt.Dimension videoDimensions;

  protected int currCCState;
  protected boolean eos;

  protected long timeGuessMillis;
  protected long guessTimestamp;
  protected long timestampOffset;
  protected boolean serverSideTranscoding;

  protected boolean pushMode;

  protected boolean currMute;

  protected UIManager uiMgr;
  protected MiniClientSageRenderer mcsr;
  protected java.awt.Color colorKey;

  protected Thread pushThread;
  protected volatile boolean timeshifted;
  protected volatile long finalLength;
  protected volatile boolean transcoded;

  protected java.awt.Rectangle lastVideoSrcRect;
  protected java.awt.Rectangle lastVideoDestRect;

  protected float myRate;
  protected int freeSpace = 0;
  protected float curVolume = 1.0f;

  private int maxAvailBufferSize;
  private long lastRateAdjustTime;

  private int lastEstimatedPushBitrate;
  private int lastAverageEstimatedPushBitrate;
  private int[] pushBitrateStats = new int[NUM_SAMPLES_AVERAGE_BANDWIDTH_ESTIMATE];
  private long[] pushBitrateStatsWeights = new long[NUM_SAMPLES_AVERAGE_BANDWIDTH_ESTIMATE];
  private int pushBitrateStatsIndex;
  private int lastEstimatedStreamBitrate;
  private int lastAverageEstimatedStreamBitrate;
  private int[] streamBitrateStats = new int[NUM_SAMPLES_AVERAGE_BANDWIDTH_ESTIMATE];
  private long[] streamBitrateStatsWeights = new long[NUM_SAMPLES_AVERAGE_BANDWIDTH_ESTIMATE];
  private int streamBitrateStatsIndex;

  private boolean pushThreadCreated;

  private boolean needToPlay;

  private boolean dynamicRateAdjust;

  int numPushedBuffers = 0;
  final boolean debugPush = Sage.getBoolean("miniclient/debug_push", false);
  boolean sentDiscardPtsFlag = false;
  boolean sentTrickmodeFlag = false;

  private long lastMediaTime;
  private long lastMediaTimeBase;
  private long lastMediaTimeCacheTime;

  private boolean detailedPushBufferStats;
  private int clientReportedMediaTime;
  private int clientReportedPlayState;
  private long lastDetailedBufferUpdate;

  private long lastParserTimestamp;
  private long lastParserTimestampBytePos;

  private int pushBufferSize;

  private boolean firstSeek;

  private boolean mediaExtender; // true if this is a connection from a media extender; false if it's a desktop app
  private boolean lowBandwidth; // true if this is a 'remote' connection that's low bandwidth
  private boolean hdMediaExtender;

  private boolean usingRemuxer;

  // I know this isn't perfect, but it's a quick and easy way to allow yielding on the decoder
  // lock only when necessary. It'll yield in a fair amount of other cases as well though.
  private int yieldDecoderLockCount;
  private final Object yieldDecoderLockCountLock = new Object();

  private boolean disableVideoPositioning = false;
  private int languageIndex;
  private sage.media.format.AudioFormat[] audioTracks;
  private int subpicIndex;
  private boolean subpicOn;
  private sage.media.format.SubpictureFormat[] subpicTracks;
  private String[] subpicSels;
  private String[] audioSels;
  private boolean isMpeg2PS;

  private boolean waitingForSeek;

  private FileDownloader downer;

  private int currBandwidthBufferKbps = BANDWIDTH_BUFFER_KBPS;

  // For BluRay handling
  private sage.media.bluray.BluRayStreamer bdp;
  private int lastBluRayIndex;
  private int currBDAngle;
  private int currBDTitle;

  private boolean useNioTransfers;

  private int maxPushBufferSize;

  private java.nio.ByteBuffer[] dbuf = new java.nio.ByteBuffer[2];

  private boolean useAsyncReplies = true;
  private long timeout = Sage.getInt("ui/remote_player_connection_timeout", 30000);
  private boolean bwDebug = Sage.getBoolean("miniplayer/bwstats", false);

  private java.nio.channels.FileChannel pushDumpStream;

  protected java.io.File unmountRequired;
  private boolean justSeeked = false;
  private boolean sendSeekPullNext;
  private boolean wasFastSwitch;
  private long videoPTSForPlay;
  private boolean bufferFillPause;
  private boolean hdMediaPlayer;
  private boolean enableBufferFillPause;

  private boolean hdhrPrimeSpecial;
}
