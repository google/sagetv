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

public class DShowMediaPlayer implements DVDMediaPlayer
{
  protected static String PREFS_ROOT = BasicVideoFrame.VIDEOFRAME_KEY + '/';

  protected static final String VIDEO_DECODER_FILTER = "video_decoder_filter";
  protected static final String AUDIO_DECODER_FILTER = "audio_decoder_filter";
  protected static final String AUDIO_RENDER_FILTER = "audio_render_filter";
  protected static final String VIDEO_RENDER_FILTER = "video_render_filter";
  protected static final String ADDITIONAL_VIDEO_FILTERS = "additional_video_filters";
  protected static final String ADDITIONAL_AUDIO_FILTERS = "additional_audio_filters";
  protected static final String USE_EVR = "use_evr";
  protected static final String USE_VMR = "use_vmr";
  protected static final String USE_OVERLAY = "use_overlay";

  public static final boolean OVERLAY_IS_DEFAULT = true;

  protected static final String EVR_GUID = "{FA10746C-9B63-4B6C-BC49-FC300EA5F256}";
  protected static final String VMR9_GUID = "{51B4ABF3-748F-4E3B-A276-C828330E926A}";
  protected static final String OVERLAY_GUID = "{CD8743A1-3736-11D0-9E69-00C04FD7C15B}";
  //"{A0025E90-E45B-11D1-ABE9-00A0C905F375}"; // OverlayMixer2

  private static boolean EVR_SUPPORTED = false;

  public static boolean isEVRSupported() { return EVR_SUPPORTED; }
  public static final java.util.Comparator filterNameCompare = new java.util.Comparator()
  {
    public int compare(Object o1, Object o2)
    {
      return mystrcmp((String) o1, (String) o2);
    }

    int mystrcmp(String s1, String s2)
    {
      int i1=0, i2=0;
      do
      {
        if (i1 == s1.length() && i2 == s2.length())
          return 0;
        if (i1 == s1.length())
          return -1;
        if (i2 == s2.length())
          return 1;
        char c1 = Character.toLowerCase(s1.charAt(i1));
        char c2 = Character.toLowerCase(s2.charAt(i2));
        i1++;
        i2++;

        // TERRIBLE HACK: There's a problem with the copyright symbol in the sonic cinemaster decoder's
        // name that I tried to deal with awhile back. And I added a wildcard to fix it. But it totally
        // screws up the sorting algorithm. I've changed it to only be the @ sign here so it shouldn't cause any harm anymore
        if ((c1 == '@' || c2 == '@') && (i1 > 5 && i2 > 5))//(!Character.isLetterOrDigit(c1) || !Character.isLetterOrDigit(c2))
          continue;
        if (c1 != c2)
          return (int) (c1 - c2);
      }while (true);
    }

  };
  protected static String[] videoFilterNames = Pooler.EMPTY_STRING_ARRAY;
  protected static String[] audioFilterNames = Pooler.EMPTY_STRING_ARRAY;

  static
  {
    if (Sage.WINDOWS_OS)
    {
      sage.Native.loadLibrary("DShowPlayer");
      java.util.ArrayList videoFilters = new java.util.ArrayList();
      java.util.ArrayList audioFilters = new java.util.ArrayList();
      audioFilters.add("LAV Audio Decoder");
      videoFilters.add("LAV Video Decoder");
      audioFilters.add("Cyberlink Audio Decoder");
      videoFilters.add("Cyberlink Video/SP Decoder");
      audioFilters.add("CyberLink Audio Decoder (PDVD7)");
      videoFilters.add("CyberLink Video/SP Decoder (PDVD7)");
      audioFilters.add("CyberLink Audio Decoder (PDVD8)");
      videoFilters.add("CyberLink Video/SP Decoder (PDVD8)");
      audioFilters.add("CyberLink Audio Decoder (PDVD9)");
      videoFilters.add("CyberLink Video/SP Decoder (PDVD9)");
      audioFilters.add("CyberLink Audio Decoder (HD264)");
      audioFilters.add("CyberLink Audio Decoder (ATI)");
      audioFilters.add("CyberLink Audio Decoder(ATI)");
      videoFilters.add("CyberLink Video/SP Decoder (ATI)");
      videoFilters.add("CyberLink H.264/AVC Decoder(ATI)");
      audioFilters.add("CyberLink Audio Decoder for Dell");
      videoFilters.add("CyberLink Video/SP Decoder for Dell");
      videoFilters.add("CyberLink Video/SP Decoder DELL 5.3");
      String eleFilterName = Sage.readStringValue(Sage.HKEY_LOCAL_MACHINE,
          "SOFTWARE\\Classes\\CLSID\\{BC4EB321-771F-4e9f-AF67-37C631ECA106}", "");
      if (eleFilterName != null)
        videoFilters.add(eleFilterName);
      eleFilterName = Sage.readStringValue(Sage.HKEY_LOCAL_MACHINE,
          "SOFTWARE\\Classes\\CLSID\\{F50B3F13-19C4-11CF-AA9A-02608C9BABA2}", "");
      if (eleFilterName != null)
        videoFilters.add(eleFilterName);
      audioFilters.add("InterVideo Audio Decoder");
      videoFilters.add("InterVideo Video Decoder");
      audioFilters.add("InterVideo NonCSS Audio Decoder for Hauppauge");
      videoFilters.add("InterVideo NonCSS Video Decoder for Hauppauge");
      audioFilters.add("Moonlight Odio Dekoda");
      audioFilters.add("MPEG Audio Decoder");
      audioFilters.add("AC3Filter");
      audioFilters.add("RAVISENT Cinemaster DS Audio Decoder");
      videoFilters.add("RAVISENT Cinemaster DS Video Decoder");
      videoFilters.add("Sigma Designs MPEG-2 hardware decoder");
      audioFilters.add("DVD Express Audio Decoder");
      videoFilters.add("DVD Express Video Decoder");
      videoFilters.add("NVIDIA Video Decoder");
      audioFilters.add("NVIDIA Audio Decoder");
      videoFilters.add("ffdshow MPEG-4 Video Decoder");
      audioFilters.add("ffdshow Audio Decoder");
      videoFilters.add("NVIDIA Video Post Processor");
      videoFilters.add("MainConcept MPEG Video Decoder");
      if (!videoFilters.contains("MainConcept MPEG-2 Video Decoder"))
        videoFilters.add("MainConcept MPEG-2 Video Decoder"); // this shows up from the Elecard filter registry value sometimes
      audioFilters.add("MainConcept MPEG Audio Decoder");
      audioFilters.add("ATI MPEG Audio Decoder");
      videoFilters.add("ATI MPEG Video Decoder");
      videoFilters.add("ArcSoft Video Decoder");
      audioFilters.add("ArcSoft Audio Decoder HD");
      videoFilters.add("Microsoft MPEG-2 Video Decoder");
      audioFilters.add("Microsoft MPEG-1/DD Audio Decoder");
      videoFilters.add("Microsoft DTV-DVD Video Decoder");
      audioFilters.add("Microsoft DTV-DVD Audio Decoder");
      String extraFilters = Sage.get(PREFS_ROOT + ADDITIONAL_AUDIO_FILTERS, "");
      java.util.StringTokenizer toker = new java.util.StringTokenizer(extraFilters, ";");
      while (toker.hasMoreTokens())
      {
        // In case they're using the old format that also has pin names
        java.util.StringTokenizer toke2 = new java.util.StringTokenizer(toker.nextToken(), ",");
        audioFilters.add(toke2.nextToken());
      }
      extraFilters = Sage.get(PREFS_ROOT + ADDITIONAL_VIDEO_FILTERS, "");
      toker = new java.util.StringTokenizer(extraFilters, ";");
      while (toker.hasMoreTokens())
      {
        // In case they're using the old format that also has pin names
        java.util.StringTokenizer toke2 = new java.util.StringTokenizer(toker.nextToken(), ",");
        videoFilters.add(toke2.nextToken());
      }

      String[] allFilters = DShowCaptureDevice.getDevicesInCategory0(DShowCaptureManager.FILTERS_CATEGORY_GUID);
      java.util.Arrays.sort(allFilters, filterNameCompare);
      if (Sage.DBG) System.out.println("DShowFilters=" + java.util.Arrays.asList(allFilters));
      java.util.Iterator walker = audioFilters.iterator();
      while (walker.hasNext())
      {
        if (java.util.Arrays.binarySearch(allFilters, walker.next(), filterNameCompare) < 0)
          walker.remove();
      }
      walker = videoFilters.iterator();
      while (walker.hasNext())
      {
        String curr = walker.next().toString();
        if (java.util.Arrays.binarySearch(allFilters, curr, filterNameCompare) < 0)
        {
          walker.remove();
        }
      }

      // Check these independently because they don't work right with the sorting
      for (int i = 0; i < allFilters.length; i++)
      {
        if (filterNameCompare.compare(allFilters[i], "Sonic Cinemaster@ DS Audio Decoder") == 0)
          audioFilters.add(allFilters[i]);
        if (filterNameCompare.compare(allFilters[i], "Sonic Cinemaster@ MCE Audio Decoder") == 0)
          audioFilters.add(allFilters[i]);
        if (filterNameCompare.compare(allFilters[i], "Sonic Cinemaster@ DS Video Decoder") == 0)
          videoFilters.add(allFilters[i]);
        if (filterNameCompare.compare(allFilters[i], "Sonic Cinemaster@ MCE Video Decoder") == 0)
          videoFilters.add(allFilters[i]);
      }

      // These should always be there because the names changed between V6.2 and V6.3 w/ the new DXVA decoders
      videoFilters.add("SageTV MPEG Video Decoder");
      audioFilters.add("SageTV MPEG Audio Decoder");
      videoFilterNames = (String[]) videoFilters.toArray(new String[0]);
      java.util.Arrays.sort(videoFilterNames);
      audioFilterNames = (String[]) audioFilters.toArray(new String[0]);
      java.util.Arrays.sort(audioFilterNames);

      EVR_SUPPORTED = java.util.Arrays.binarySearch(allFilters, "Enhanced Video Renderer", filterNameCompare) >= 0;
      if (Sage.DBG) System.out.println("EVR support detected=" + EVR_SUPPORTED);

      if (Sage.get(PREFS_ROOT + VIDEO_RENDER_FILTER, null) == null)
      {
        if (Sage.VISTA_OS)
          Sage.put(PREFS_ROOT + VIDEO_RENDER_FILTER, "EVR");
        else if (Sage.getBoolean(PREFS_ROOT + USE_VMR, !OVERLAY_IS_DEFAULT))
          Sage.put(PREFS_ROOT + VIDEO_RENDER_FILTER, "VMR9");
        else if (Sage.getBoolean(PREFS_ROOT + USE_OVERLAY, OVERLAY_IS_DEFAULT))
          Sage.put(PREFS_ROOT + VIDEO_RENDER_FILTER, "Overlay");
        else
          Sage.put(PREFS_ROOT + VIDEO_RENDER_FILTER, "Default");
      }
    }
  }

  public static boolean getUseOverlay() { return "Overlay".equals(Sage.get(PREFS_ROOT + VIDEO_RENDER_FILTER, null)); }
  public static void setUseOverlay(){ Sage.put(PREFS_ROOT + VIDEO_RENDER_FILTER, "Overlay"); }
  public static boolean getUseVmr() { return "VMR9".equals(Sage.get(PREFS_ROOT + VIDEO_RENDER_FILTER, null)); }
  public static void setUseVmr(){ Sage.put(PREFS_ROOT + VIDEO_RENDER_FILTER, "VMR9"); }
  public static boolean getUseEvr() { return "EVR".equals(Sage.get(PREFS_ROOT + VIDEO_RENDER_FILTER, null)); }
  public static void setUseEvr(){ Sage.put(PREFS_ROOT + VIDEO_RENDER_FILTER, "EVR"); }
  public static void setUseDefaultVideoRenderer() { Sage.put(PREFS_ROOT + VIDEO_RENDER_FILTER, "Default"); }
  public static String getAudioRenderFilter() { return Sage.rez(Sage.get(PREFS_ROOT + AUDIO_RENDER_FILTER, "Default")); }
  public static void setAudioRenderFilter(String x) { Sage.put(PREFS_ROOT + AUDIO_RENDER_FILTER, x); }
  public static String[] getAudioDecoderFilters() { return audioFilterNames; }
  public static String[] getVideoDecoderFilters() { return videoFilterNames; }

  public DShowMediaPlayer()
  {
    prefs = PREFS_ROOT;
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

  public boolean frameStep(int amount)
  {
    // Frame stepping will pause it if playing
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        if (currState == PLAY_STATE)
        {
          if (pause0(pHandle))
            currState = PAUSE_STATE;
          else
            return false;
        }
        return frameStep0(pHandle, amount);
      }
    }
    else
      return false;
  }

  public void free()
  {
    if (pHandle != 0)
    {
      // NOTE: This is required or it can hang the graph on stop. This was occuring when using
      // DivX files for playback in testing. We must do this to be safe because we can't
      // recover from hangs in this situation.
      // 12/08/04 - to keep sync on this, we put this back in the sync block here, but disabled
      // sync on the asyncStop call (which could deadlock us if this call was made from a thread
      // that has the lock already)
      synchronized (this)
      {
        if (currState == PAUSE_STATE || currState == PLAY_STATE)
          Sage.stop(this);
        teardownGraph0(pHandle);
        currState = NO_STATE;
        pHandle = 0;
      }
      VideoFrame.getVideoFrameForPlayer(this).getUIMgr().putFloat("videoframe/last_dshow_volume", lastVolume);
    }
  }

  public long getDurationMillis()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      synchronized (this)
      {
        if (currTimeshifted)
          return 0;
        else
          return getDurationMillis0(pHandle);
      }
    }
    else
      return 0;
  }

  public java.io.File getFile()
  {
    return currFile;
  }

  public long getMediaTimeMillis()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        if (currState == PLAY_STATE || currState == PAUSE_STATE)
        {
          return getMediaTimeMillis0(pHandle);
        }
      }
    }
    return 0;
  }

  public int getPlaybackCaps()
  {
    return FRAME_STEP_FORWARD_CAP | PAUSE_CAP | SEEK_CAP;
  }

  public float getPlaybackRate()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getPlaybackRate0(pHandle);
      }
    }
    else
      return 1.0f;
  }

  public int getState()
  {
    return eos ? EOS_STATE : currState;
  }

  public float getVolume()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      return lastVolume;
      //			synchronized (this)
      //			{
      //				return getGraphVolume0(pHandle);
      //			}
    }
    return 0;
  }

  public synchronized void inactiveFile()
  {
    inactiveFile0(pHandle);
    currTimeshifted = false;
  }

  protected void setFilters() throws PlaybackException
  {
    MediaFile mf = VideoFrame.getMediaFileForPlayer(this);
    nullVideoDim = false;
    if (getUseEvr() || getUseVmr())
    {
      if (DirectX9SageRenderer.getD3DObjectPtr() != 0 && DirectX9SageRenderer.getD3DDevicePtr() != 0 &&
          (getUseVmr() || DirectX9SageRenderer.getD3DDeviceMgr() != 0))
      {
        java.util.Map vmrOptions = new java.util.HashMap();
        vmrOptions.put("d3d_device_ptr", new Long(DirectX9SageRenderer.getD3DDevicePtr()));
        vmrOptions.put("d3d_object_ptr", new Long(DirectX9SageRenderer.getD3DObjectPtr()));
        vmrOptions.put("d3d_device_mgr", new Long(DirectX9SageRenderer.getD3DDeviceMgr()));
        // We never deal with CC for non MPEG2 playback; and MPEG2 playback is always handled by the DShowTVPlayer
        vmrOptions.put("enable_cc", new Boolean(false));
        setVideoRendererFilter0(pHandle, getUseEvr() ? EVR_GUID : VMR9_GUID, vmrOptions);
        nullVideoDim = true;
        transparency = TRANSLUCENT;
      }
      else
      {
        setVideoRendererFilter0(pHandle, OVERLAY_GUID, null);
        transparency = BITMASK;
      }
    }
    else if (getUseOverlay())
    {
      setVideoRendererFilter0(pHandle, OVERLAY_GUID, null);
      transparency = BITMASK;
    }
    else
      transparency = OPAQUE;
    String audDec = Sage.get(prefs + AUDIO_RENDER_FILTER, "Default");
    if (audDec.length() > 0 && !"Default".equals(audDec))
      setAudioRendererFilter0(pHandle, audDec, null);

    // If we're dealing with a WM Source and MS encoded audio or video then don't put
    // the filters in because it's better to let MS put the Decoder DMO objects in itself
    String contForm = mf.getContainerFormat();
    String vidDec = "";
    String vidType = mf != null ? mf.getPrimaryVideoFormat() : "";
    if (!sage.media.format.MediaFormat.ASF.equals(contForm) ||
        (!sage.media.format.MediaFormat.WMV7.equals(vidType) &&
            !sage.media.format.MediaFormat.WMV8.equals(vidType) &&
            !sage.media.format.MediaFormat.WMV9.equals(vidType) &&
            !sage.media.format.MediaFormat.VC1.equals(vidType)))
    {
      if (sage.media.format.MediaFormat.H264.equals(vidType))
      {
        setupH264DecoderFilter();
      }
      else
      {
        // Check for a format specific video decoder
        if (sage.media.format.MediaFormat.MPEG2_VIDEO.equals(vidType))
          vidDec = Sage.get(prefs + VIDEO_DECODER_FILTER, "SageTV MPEG Video Decoder");
        else if (vidType.length() > 0)
          vidDec = Sage.get(prefs + vidType.toLowerCase() + "_" + VIDEO_DECODER_FILTER, "");
        // Default to the WindowsMedia VC1 decoder if another one isn't specified
        if (vidDec.length() == 0 && sage.media.format.MediaFormat.VC1.equals(vidType))
          vidDec = "WMVideo Decoder DMO";
        if (vidDec.length() > 0 && !"Default".equals(vidDec) && !Sage.rez("Default").equals(vidDec))
          setVideoDecoderFilter0(pHandle, vidDec, null);
      }
    }

    audDec = "";
    String audType = "";
    if (mf != null)
    {
      audType = mf.getPrimaryAudioFormat();
      sage.media.format.ContainerFormat cf = mf.getFileFormat();
      if (cf != null)
      {
        sage.media.format.AudioFormat[] afs = cf.getAudioFormats(false);
        if (afs != null && afs.length > languageIndex)
          audType = afs[languageIndex].getFormatName();
      }
    }
    if (!sage.media.format.MediaFormat.ASF.equals(contForm) ||
        (!sage.media.format.MediaFormat.WMA7.equals(audType) &&
            !sage.media.format.MediaFormat.WMA8.equals(audType) &&
            !sage.media.format.MediaFormat.WMA9LOSSLESS.equals(audType) &&
            !sage.media.format.MediaFormat.WMA_PRO.equals(audType)))
    {
      // Check for a format specific audio decoder
      if (sage.media.format.MediaFormat.AC3.equals(audType) || sage.media.format.MediaFormat.MP2.equals(audType))
        audDec = Sage.get(prefs + AUDIO_DECODER_FILTER, "SageTV MPEG Audio Decoder");
      else if (audType.length() > 0)
        audDec = Sage.get(prefs + audType.toLowerCase() + "_" + AUDIO_DECODER_FILTER, "");
      if (audDec.length() > 0 && !"Default".equals(audDec) && !Sage.rez("Default").equals(audDec))
        setAudioDecoderFilter0(pHandle, audDec, null);
      else if (sage.media.format.MediaFormat.DTS.equalsIgnoreCase(audType) || "DCA".equalsIgnoreCase(audType))
        setAudioDecoderFilter0(pHandle, "AC3Filter", null);
    }

    // Ugly hack for not being able to start at the beginning of H264 FLV files correctly (update to ffmpeg will likely fix it after V7.0)
    if (!currTimeshifted && sage.media.format.MediaFormat.FLASH_VIDEO.equals(mf.getContainerFormat()) && sage.media.format.MediaFormat.H264.equals(mf.getPrimaryVideoFormat()))
      minSeekTime = 5000;
  }

  protected void setupH264DecoderFilter() throws PlaybackException
  {
    String currH264Filter = null;
    if ((currH264Filter = Sage.get("videoframe/h264_video_decoder_filter", null)) == null)
    {
      // Try to find an H.264 decoder filter to use
      String[] allFilters = DShowCaptureDevice.getDevicesInCategory0(DShowCaptureManager.FILTERS_CATEGORY_GUID);
      java.util.Arrays.sort(allFilters, filterNameCompare);
      if (java.util.Arrays.binarySearch(allFilters, "ArcSoft Video Decoder", filterNameCompare) >= 0)
        Sage.put("videoframe/h264_video_decoder_filter", currH264Filter = "ArcSoft Video Decoder");
      else if (java.util.Arrays.binarySearch(allFilters, "CyberLink H.264/AVC Decoder (PDVD8)", filterNameCompare) >= 0)
        Sage.put("videoframe/h264_video_decoder_filter", currH264Filter = "CyberLink H.264/AVC Decoder (PDVD8)");
      else if (java.util.Arrays.binarySearch(allFilters, "CyberLink H.264/AVC Decoder (PDVD7)", filterNameCompare) >= 0)
        Sage.put("videoframe/h264_video_decoder_filter", currH264Filter = "CyberLink H.264/AVC Decoder (PDVD7)");
      else if (java.util.Arrays.binarySearch(allFilters, "CyberLink H.264/AVC Decoder (HD264)", filterNameCompare) >= 0)
        Sage.put("videoframe/h264_video_decoder_filter", currH264Filter = "CyberLink H.264/AVC Decoder (HD264)");
      else if (java.util.Arrays.binarySearch(allFilters, "CyberLink H.264/AVC Decoder(ATI)", filterNameCompare) >= 0)
        Sage.put("videoframe/h264_video_decoder_filter", currH264Filter = "CyberLink H.264/AVC Decoder(ATI)");
      else if (java.util.Arrays.binarySearch(allFilters, "CoreAVC Video Decoder", filterNameCompare) >= 0)
        Sage.put("videoframe/h264_video_decoder_filter", currH264Filter = "CoreAVC Video Decoder");
      else if (java.util.Arrays.binarySearch(allFilters, "CyberLink H.264/AVC Decoder (PDVD7.x)", filterNameCompare) >= 0)
        Sage.put("videoframe/h264_video_decoder_filter", currH264Filter = "CyberLink H.264/AVC Decoder (PDVD7.x)");
    }
    if (currH264Filter != null)
      setVideoDecoderFilter0(pHandle, currH264Filter, null);
  }

  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    eos = false;
    pHandle = createGraph0();
    UIManager uiMgr = VideoFrame.getVideoFrameForPlayer(this).getUIMgr();
    lastVolume = (uiMgr.getBoolean("media_player_uses_system_volume", Sage.WINDOWS_OS && !Sage.VISTA_OS)) ? 1.0f : uiMgr.getFloat("videoframe/last_dshow_volume", 1.0f);
    currHintMajorType = majorTypeHint;
    currHintMinorType = minorTypeHint;
    currHintEncoding = encodingHint;
    currTimeshifted = timeshifted;
    // Set the default language index before we do the filters so we get the right audio codec selected
    setDefaultLangIndex();
    setFilters();
    // NOTE: Enabling timeshifting can only cause problems because we can't do this correctly
    // for anything that doesn't use the TV media player
    // UPDATE: I think if we use the stv:// URL for the file path then we should be able to deal with
    // the issues associated with using timeshifted playback since some other demuxes support it properly it turns out
    // UPDATE2: Nope. This'll fail on SageTVClient since it doesn't have a MediaServer running (and we can't run one since it might interfere w/ the local server)
    setTimeshifting0(pHandle, timeshifted, bufferSize);
    if (hostname != null && hostname.indexOf("mms://") != -1)
    {
      setupGraph0(pHandle, hostname, null, true, true);
      disableSeekAndRate = true;
    }
    else
    {
      setupGraph0(pHandle, file != null ? file.getPath() : null, hostname, true, true);
      disableSeekAndRate = false;
    }
    if (transparency != TRANSLUCENT && (majorTypeHint == MediaFile.MEDIATYPE_VIDEO ||
        majorTypeHint == MediaFile.MEDIATYPE_DVD || majorTypeHint == MediaFile.MEDIATYPE_BLURAY)) // not vmr & video is present, so we need to render to the HWND
      setVideoHWND0(pHandle, VideoFrame.getVideoFrameForPlayer(this).getVideoHandle());
    colorKey = null;
    currCCState = -1;
    videoDimensions = null;
    getVideoDimensions();
    currFile = file;
    currState = LOADED_STATE;
    getColorKey();
    setNotificationWindow0(pHandle, Sage.mainHwnd);
  }

  protected sage.media.format.ContainerFormat getCurrFormat()
  {
    VideoFrame vf = VideoFrame.getVideoFrameForPlayer(this);
    if (vf != null)
    {
      MediaFile mf = vf.getCurrFile();
      if (mf != null)
        return mf.getFileFormat();
    }
    return null;
  }

  protected void setDefaultLangIndex()
  {
    // Make sure we have the right default language index selected
    sage.media.format.ContainerFormat cf = getCurrFormat();
    if (cf != null && cf.getNumAudioStreams() > 1)
    {
      boolean bestHDAudio = false;
      boolean bestAC3DTS = false;
      int bestChans = 0;
      sage.media.format.AudioFormat[] afs = cf.getAudioFormats(false);
      for (int i = 0; i < afs.length; i++)
      {
        boolean currHDAudio = sage.media.format.MediaFormat.DOLBY_HD.equals(afs[i].getFormatName()) ||
            sage.media.format.MediaFormat.DTS_HD.equals(afs[i].getFormatName()) ||
            sage.media.format.MediaFormat.DTS_MA.equals(afs[i].getFormatName());
        int currChans = afs[i].getChannels();
        if ((!bestHDAudio && currHDAudio) || (bestHDAudio && currHDAudio && (currChans > bestChans)))
        {
          languageIndex = i;
          bestHDAudio = true;
          bestChans = currChans;
        }
        else if (!bestHDAudio)
        {
          boolean currAC3DTS = sage.media.format.MediaFormat.AC3.equals(afs[i].getFormatName()) ||
              sage.media.format.MediaFormat.DTS.equals(afs[i].getFormatName()) ||
              "DCA".equals(afs[i].getFormatName()) ||
              sage.media.format.MediaFormat.EAC3.equals(afs[i].getFormatName());
          if ((!bestAC3DTS && currAC3DTS) || ((!bestAC3DTS || currAC3DTS) && (currChans > bestChans)))
          {
            languageIndex = i;
            bestAC3DTS = currAC3DTS;
            bestChans = currChans;
          }
        }
      }
      if (Sage.DBG) System.out.println("Detected default audio stream index to be: " + (languageIndex + 1));
    }
  }

  public boolean pause()
  {
    if (currState == LOADED_STATE || currState == PLAY_STATE)
    {
      synchronized (this)
      {
        if (pause0(pHandle))
          currState = PAUSE_STATE;
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
        if (play0(pHandle))
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
        if (!disableSeekAndRate)
        {
          eos = false;
          seek0(pHandle, Math.max(minSeekTime, seekTimeMillis));
        }
        return getMediaTimeMillis();
      }
    }
    else
      return 0;
  }

  public void setMute(boolean x)
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      synchronized (this)
      {
        setGraphVolume0(pHandle, x ? 0 : lastVolume);
      }
    }
  }

  public boolean getMute()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      synchronized (this)
      {
        return getGraphVolume0(pHandle) == 0;
      }
    }
    else
      return false;
  }

  public float setPlaybackRate(float newRate)
  {
    if (!disableSeekAndRate && (currState == PLAY_STATE || currState == PAUSE_STATE))
    {
      synchronized (this)
      {
        setPlaybackRate0(pHandle, newRate);
        return getPlaybackRate0(pHandle);
      }
    }
    else
      return 1.0f;
  }

  public float setVolume(float f)
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      synchronized (this)
      {
        return setGraphVolume0(pHandle, lastVolume = f);
      }
    }
    return 0;
  }

  public void stop()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      Sage.stop(this);
    }
  }

  // This is NOT synchronized so we don't deadlock
  void asyncStop()
  {
    stop0(pHandle);
    currState = STOPPED_STATE;
  }

  public java.awt.Color getColorKey()
  {
    if (transparency == BITMASK)
    {
      if (colorKey == null && currState != NO_STATE)
      {
        synchronized (this)
        {
          colorKey = getColorKey0(pHandle);
        }
      }
      return colorKey;
    }
    return null;
  }

  public int getTransparency() { return transparency; }

  public java.awt.Dimension getVideoDimensions()
  {
    if (nullVideoDim || currState == NO_STATE) return null; // makes VMR9 faster
    if (videoDimensions != null)
      return videoDimensions;
    synchronized (this)
    {
      videoDimensions = getVideoDimensions0(pHandle);
      if (Sage.DBG) System.out.println("Got Native Video Dimensions " + videoDimensions);
      return videoDimensions;
    }
  }

  public void setVideoRectangles(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    // Don't do the native call for VMR9 because it doesn't do anything!!
    if (currState != NO_STATE && transparency != TRANSLUCENT && !nullVideoDim)
    {
      synchronized (this)
      {
        resizeVideo0(pHandle, videoSrcRect, videoDestRect, hideCursor);
      }
    }
  }

  public boolean setClosedCaptioningState(int ccState)
  {
    if (currState == NO_STATE || ccState < 0 || ccState > CC_ENABLED_CAPTION2)
      return false;
    else
    {
      synchronized (this)
      {
        if (setCCState0(pHandle, ccState))
        {
          currCCState = ccState;
          return true;
        }
        else
          return false;
      }
    }
  }

  public int getClosedCaptioningState()
  {
    if (currState == NO_STATE) return CC_DISABLED;
    int rv = currCCState;
    if (rv < 0)
    {
      synchronized (this)
      {
        rv = currCCState = getCCState0(pHandle);
      }
    }
    return rv;
  }

  public synchronized void processEvents() throws PlaybackException
  {
    if (Sage.DBG) System.out.println("DShowMediaPlayer is consuming the events...");
    if (pHandle != 0)
    {
      int res = processEvents0(pHandle);
      if (res == 1 || (res == 2 && VideoFrame.getVideoFrameForPlayer(this).isLiveControl()))
        eos = true;
      else if (res == 0x53) // render device changed, redo the video size
      {
        videoDimensions = null;
      }
    }
  }

  public boolean playControlEx(int playCode, long param1, long param2) throws PlaybackException
  {
    processEvents();
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      if (playCode == VideoFrame.DVD_CONTROL_AUDIO_CHANGE)
      {
        int newLanguageIndex = param1 >= 0 ? (int)param1 : 0;
        if (newLanguageIndex != languageIndex)
        {
          languageIndex = newLanguageIndex;
          synchronized (this)
          {
            int oldState = currState;
            Sage.stop(this);
            processEvents();
            boolean rv = demuxPlaybackControl0(pHandle, playCode, param1, param2);
            currState = oldState;
            if (currState == PAUSE_STATE)
              pause0(pHandle);
            else
              play0(pHandle);
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
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == STOPPED_STATE) // it can be stopped during the language switch
    {
      sage.media.format.ContainerFormat cf = getCurrFormat();
      if (cf != null && cf.getNumAudioStreams() > 1)
      {
        return cf.getAudioStreamSelectionDescriptors(false);
      }
    }
    return new String[0];
  }

  public String[] getDVDAvailableSubpictures()
  {
    return new String[0];
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
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == STOPPED_STATE) // it can be stopped during the language switch
    {
      sage.media.format.ContainerFormat cf = getCurrFormat();
      if (cf != null && cf.getNumAudioStreams() > 1)
      {
        String[] audioStreams = cf.getAudioStreamSelectionDescriptors(false);
        return audioStreams[Math.min(audioStreams.length - 1, Math.max(0, languageIndex))];
      }
    }
    return "";
  }

  public String getDVDSubpicture()
  {
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

  protected native boolean frameStep0(long ptr, int amount);
  protected native void teardownGraph0(long ptr);
  protected native long getDurationMillis0(long ptr);
  protected native long getMediaTimeMillis0(long ptr);
  protected native float getGraphVolume0(long ptr);
  protected native boolean pause0(long ptr);
  protected native boolean play0(long ptr);
  protected native void seek0(long ptr, long time) throws PlaybackException;
  protected native void stop0(long ptr);
  protected native void setPlaybackRate0(long ptr, float rate);
  protected native float getPlaybackRate0(long ptr);

  protected native float setGraphVolume0(long ptr, float volume);

  // createGraph0 will create the peer native object and create the initial filter graph
  protected native long createGraph0() throws PlaybackException;
  // setupGraph0 adds all of the filters to the graph and connects it up appropriately
  protected native void setupGraph0(long ptr, String filePath, String remoteHost, boolean renderVideo, boolean renderAudio) throws PlaybackException;
  // the setXXXFilter0 methods are used to specify what filters to use for different parts of the playback graph
  // the optionsMap is used to set different configurations specific to a certain filter. This gets resolved
  // at the native level (presently DXVAMode, DeinterlacingMode, DScaler properties, and
  // DX9 object refs for VMR9 are supported).
  // To specify the format for a pin connection, you should use the optionsMap of the
  // filter which has the input pin for that connection.
  protected native void setVideoDecoderFilter0(long ptr, String filterName, java.util.Map optionsMap) throws PlaybackException;
  protected native void setVideoPostProcessingFilter0(long ptr, String filterName, java.util.Map optionsMap) throws PlaybackException;
  protected native void setVideoRendererFilter0(long ptr, String filterName, java.util.Map optionsMap) throws PlaybackException;
  protected native void setAudioDecoderFilter0(long ptr, String filterName, java.util.Map optionsMap) throws PlaybackException;
  protected native void setAudioPostProcessingFilter0(long ptr, String filterName, java.util.Map optionsMap) throws PlaybackException;
  protected native void setAudioRendererFilter0(long ptr, String filterName, java.util.Map optionsMap) throws PlaybackException;
  protected native void setNotificationWindow0(long ptr, long hwndID);
  protected native void setVideoHWND0(long ptr, long hwnd);

  // use 0 to indicate non-circular file
  protected native void setTimeshifting0(long ptr, boolean isTimeshifting, long fileSize) throws PlaybackException;

  protected native java.awt.Color getColorKey0(long ptr);
  protected native java.awt.Dimension getVideoDimensions0(long ptr);
  protected native void resizeVideo0(long ptr, java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor);

  // Enables/disables specified closed captioning service
  protected native boolean setCCState0(long ptr, int ccState);
  protected native int getCCState0(long ptr);

  protected native int processEvents0(long ptr) throws PlaybackException;

  protected native boolean demuxPlaybackControl0(long ptr, int playCode, long param1, long param2);
  protected native void inactiveFile0(long ptr);

  protected int currState;
  protected long pHandle;
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
  protected boolean currTimeshifted;
  protected boolean disableSeekAndRate;
  protected long minSeekTime;
}
