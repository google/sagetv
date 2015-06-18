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

public class DShowTVPlayer extends DShowMediaPlayer
{
  protected static final String PS_HW_DECODER_FILTER = "ps_hw_decoder_filter";
  protected static final String DISABLE_SW_DECODING = "disable_sw_decoding";
  protected static final String DXVA_MPEG_MODE = "dxva_mpeg_mode";
  protected static final String FORCE_DEINTERLACE = "force_deinterlace";
  private static final String USE_DSCALER = "use_dscaler";
  private static final String DSCALER_MODE = "dscaler_mode";
  private static final String DSCALER_DOUBLE_REFRESH = "dscaler_double_refresh";
  private static final String DSCALER_ODD_FIELD_FIRST = "dscaler_odd_field_first";
  private static final String DSCALER_PLUGIN = "dscaler_plugin";

  private static final String DISABLE_ELECARD_DEINTERLACING = "disable_elecard_deinterlacing";

  protected static final String DSCALER_GUID = "{437B0D3A-4689-4FA6-A7DD-EB4928203C2F}";

  public static final int SAGE_DXVA_MPEGA = 1;
  public static final int SAGE_DXVA_MPEGB = 2;
  public static final int SAGE_DXVA_MPEGC = 3;
  public static final int SAGE_DXVA_MPEGD = 4;
  public static final String[] DXVA_MPEG_MODE_NAMES = {Sage.rez("Default"), "A","B","C","D"};
  public static String getDXVAName(int x)
  {
    switch (x)
    {
      case SAGE_DXVA_MPEGA:
        return "A";
      case SAGE_DXVA_MPEGB:
        return "B";
      case SAGE_DXVA_MPEGC:
        return "C";
      case SAGE_DXVA_MPEGD:
        return "D";
    }
    return Sage.rez("Default");
  }
  public static int getDXVACode(String x)
  {
    if ("A".equals(x))
      return SAGE_DXVA_MPEGA;
    else if ("B".equals(x))
      return SAGE_DXVA_MPEGB;
    else if ("C".equals(x))
      return SAGE_DXVA_MPEGC;
    else if ("D".equals(x))
      return SAGE_DXVA_MPEGD;
    else
      return 0;
  }

  public static final int DSCALER_MODE_WEAVE = 1020;
  public static final int DSCALER_MODE_TWO_FRAME = 1022;
  public static final int DSCALER_MODE_FIELD_BOB = 1024;
  public static final int DSCALER_MODE_BOB = 1021;
  public static final int DSCALER_MODE_BLENDED_CLIPPING = 1023;
  public static final int DSCALER_MODE_PLUGIN = 1025;
  public static final String[] DSCALER_MODE_NAMES = {
    Sage.rez("Blended_Clipping"), Sage.rez("Bob"), Sage.rez("Field_Bob"), Sage.rez("Plugin"), Sage.rez("Two_Frame"),
    Sage.rez("Weave")};
  public static String getDscalerName(int x)
  {
    switch (x)
    {
      case DSCALER_MODE_WEAVE:
        return Sage.rez("Weave");
      case DSCALER_MODE_TWO_FRAME:
        return Sage.rez("Two_Frame");
      case DSCALER_MODE_FIELD_BOB:
        return Sage.rez("Field_Bob");
      case DSCALER_MODE_BOB:
        return Sage.rez("Bob");
      case DSCALER_MODE_BLENDED_CLIPPING:
        return Sage.rez("Blended_Clipping");
      case DSCALER_MODE_PLUGIN:
        return Sage.rez("Plugin");
    }
    return "";
  }
  public static int getDscalerCode(String x)
  {
    if (Sage.rez("Weave").equals(x))
      return DSCALER_MODE_WEAVE;
    else if (Sage.rez("Two_Frame").equals(x))
      return DSCALER_MODE_TWO_FRAME;
    else if (Sage.rez("Field_Bob").equals(x))
      return DSCALER_MODE_FIELD_BOB;
    else if (Sage.rez("Bob").equals(x))
      return DSCALER_MODE_BOB;
    else if (Sage.rez("Blended_Clipping").equals(x))
      return DSCALER_MODE_BLENDED_CLIPPING;
    else if (Sage.rez("Plugin").equals(x))
      return DSCALER_MODE_PLUGIN;
    else
      return 0;
  }

  public static final int SAGE_DEINTERLACE_BOB = 1;
  public static final int SAGE_DEINTERLACE_WEAVE = 2;
  public static final int SAGE_DEINTERLACE_BOBWEAVE = 3;
  public static final String[] DEINTERLACE_NAMES = { Sage.rez("Default"), Sage.rez("Bob"), Sage.rez("Weave"),
    Sage.rez("Bob_and_Weave")};
  public static String getDeinterlaceName(int x)
  {
    if (x == SAGE_DEINTERLACE_BOB)
      return Sage.rez("Bob");
    else if (x == SAGE_DEINTERLACE_WEAVE)
      return Sage.rez("Weave");
    else if (x == SAGE_DEINTERLACE_BOBWEAVE)
      return Sage.rez("Bob_and_Weave");
    else
      return Sage.rez("Default");
  }
  public static int getDeinterlaceCode(String x)
  {
    if (Sage.rez("Weave").equals(x))
      return SAGE_DEINTERLACE_WEAVE;
    else if (Sage.rez("Bob").equals(x))
      return SAGE_DEINTERLACE_BOB;
    else if (Sage.rez("Bob_and_Weave").equals(x))
      return SAGE_DEINTERLACE_BOBWEAVE;
    else
      return 0;
  }

  public static boolean getDisableSWDecoding() { return Sage.getBoolean(PREFS_ROOT + DISABLE_SW_DECODING, false); }
  public static void setDisableSWDecoding(boolean x) { Sage.putBoolean(PREFS_ROOT + DISABLE_SW_DECODING, x); }
  public static boolean getUseDscaler() { return Sage.getBoolean(PREFS_ROOT + USE_DSCALER, false); }
  public static void setUseDscaler(boolean x) { Sage.putBoolean(PREFS_ROOT + USE_DSCALER, x); }
  public static int getForceDeinterlace() { return Sage.getInt(PREFS_ROOT + FORCE_DEINTERLACE, 0); }
  public static void setForceDeinterlace(int x) { Sage.putInt(PREFS_ROOT + FORCE_DEINTERLACE, x); }
  public static int getDxvaMpegMode() { return Sage.getInt(PREFS_ROOT + DXVA_MPEG_MODE, 0); }
  public static void setDxvaMpegMode(int x) { Sage.putInt(PREFS_ROOT + DXVA_MPEG_MODE, x); }
  public static int getDscalerMode() { return Sage.getInt(PREFS_ROOT + DSCALER_MODE, 0); }
  public static void setDscalerMode(int x) { Sage.putInt(PREFS_ROOT + DSCALER_MODE, x); }
  public static boolean getDscalerDoubleRefresh() { return Sage.getBoolean(PREFS_ROOT + DSCALER_DOUBLE_REFRESH, false); }
  public static void setDscalerDoubleRefresh(boolean x){ Sage.putBoolean(PREFS_ROOT + DSCALER_DOUBLE_REFRESH, x); }
  public static boolean getDscalerOddFieldFirst() { return Sage.getBoolean(PREFS_ROOT + DSCALER_ODD_FIELD_FIRST, false); }
  public static void setDscalerOddFieldFirst(boolean x){ Sage.putBoolean(PREFS_ROOT + DSCALER_ODD_FIELD_FIRST, x); }
  public static String getDscalerPlugin() { return Sage.get(PREFS_ROOT + DSCALER_PLUGIN, ""); }
  public static void setDscalerPlugin(String x){ Sage.put(PREFS_ROOT + DSCALER_PLUGIN, x); }
  public static String[] getDscalerPlugins()
  {
    if (!Sage.WINDOWS_OS) return new String[0];
    // These all start with DI_ and end with .dll and are in the IRTunerPlugins directory
    String irPluginDir = Sage.readStringValue(Sage.HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common",
        "IRTunerPluginsDir");
    if (irPluginDir == null)
      irPluginDir = System.getProperty("user.dir");
    String[] suspectDLLFiles = new java.io.File(irPluginDir).
        list(new java.io.FilenameFilter(){
          public boolean accept(java.io.File dir,String filename){return filename.toLowerCase().endsWith(".dll") &&
              filename.startsWith("DI_");}});
    String[] pluginDLLs = (suspectDLLFiles == null) ? new String[0] : new String[suspectDLLFiles.length];
    for (int i = 0; i < pluginDLLs.length; i++)
      pluginDLLs[i] = suspectDLLFiles[i].substring(3, suspectDLLFiles[i].length() - 4);
    return pluginDLLs;
  }
  public static boolean hasPVR350HWDecoder()
  {
    if (!Sage.WINDOWS_OS) return false;
    String[] devs = DShowCaptureDevice.getDevicesInCategory0(DShowCaptureManager.HW_DECODER_CATEGORY_GUID);
    for (int i = 0; i < devs.length; i++)
      if (devs[i].indexOf("PVR") != -1)
        return true;
    return false;
  }
  public static boolean getEnableHWDecoder() { return Sage.get(PREFS_ROOT + PS_HW_DECODER_FILTER, "").length() > 0; }
  public static void setEnableHWDecoder(boolean x)
  {
    if (x && Sage.WINDOWS_OS)
    {
      String[] devs = DShowCaptureDevice.getDevicesInCategory0(DShowCaptureManager.HW_DECODER_CATEGORY_GUID);
      for (int i = 0; i < devs.length; i++)
      {
        if (devs[i].indexOf("PVR") != -1)
        {
          Sage.put(PREFS_ROOT + PS_HW_DECODER_FILTER, devs[i]);
          return;
        }
      }
      if (devs.length > 0)
      {
        Sage.put(PREFS_ROOT + PS_HW_DECODER_FILTER, devs[0]);
        return;
      }
    }
    Sage.put(PREFS_ROOT + PS_HW_DECODER_FILTER, "");
  }
  public static String getAudioDecoderFilter() { return Sage.get(PREFS_ROOT + AUDIO_DECODER_FILTER, "SageTV MPEG Audio Decoder"); }
  public static void setAudioDecoderFilter(String x) { Sage.put(PREFS_ROOT + AUDIO_DECODER_FILTER, x); }
  public static String getVideoDecoderFilter() { return Sage.get(PREFS_ROOT + VIDEO_DECODER_FILTER, "SageTV MPEG Video Decoder"); }
  public static void setVideoDecoderFilter(String x) { Sage.put(PREFS_ROOT + VIDEO_DECODER_FILTER, x); }

  public static void autoOptimize(boolean onlyOnVirgins)
  {
    if (!Sage.WINDOWS_OS) return;
    String[] dshowFilters = DShowCaptureDevice.getDevicesInCategory0(DShowCaptureManager.FILTERS_CATEGORY_GUID);
    if (dshowFilters.length != 0)
      java.util.Arrays.sort(dshowFilters, filterNameCompare);
    if (dshowFilters.length != 0 &&
        (!onlyOnVirgins || (Sage.get(PREFS_ROOT + AUDIO_DECODER_FILTER, "SageTV MPEG Audio Decoder").length() == 0 &&
        Sage.get(PREFS_ROOT + VIDEO_DECODER_FILTER, "SageTV MPEG Video Decoder").length() == 0)))
    {
      if (Sage.DBG) System.out.println("Player AUTO OPTIMIZATION IS RUNNING...");
      if (Sage.DBG) System.out.println("Searching for existence of SageTV decoders...");
      if (java.util.Arrays.binarySearch(dshowFilters, "SageTV MPEG Video Decoder",
          filterNameCompare) >= 0 &&
          java.util.Arrays.binarySearch(dshowFilters, "SageTV MPEG Audio Decoder",
              filterNameCompare) >= 0)
      {
        if (Sage.DBG) System.out.println("Default SageTV configuration will be used");
        setVideoDecoderFilter("SageTV MPEG Video Decoder");
        setAudioDecoderFilter("SageTV MPEG Audio Decoder");
        //				setUseOverlay(true);
        setAudioRenderFilter(Sage.rez("Default"));
        return;
      }
      if (Sage.DBG) System.out.println("Searching for existence of Hauppauge card & Intervideo decoders...");
      if (java.util.Arrays.binarySearch(dshowFilters, "InterVideo NonCSS Video Decoder for Hauppauge",
          filterNameCompare) >= 0 &&
          java.util.Arrays.binarySearch(dshowFilters, "InterVideo NonCSS Audio Decoder for Hauppauge",
              filterNameCompare) >= 0)
      {
        if (Sage.DBG) System.out.println("Default Hauppauge/Intervideo configuration will be used");
        setVideoDecoderFilter("InterVideo NonCSS Video Decoder for Hauppauge");
        setAudioDecoderFilter("InterVideo NonCSS Audio Decoder for Hauppauge");
        //				setUseOverlay(true);
        setAudioRenderFilter(Sage.rez("Default"));
        return;
      }
      if (Sage.DBG) System.out.println("Searching for existence of Elecard Video Decoder...");
      if (java.util.Arrays.binarySearch(dshowFilters, "Elecard MPEG2 Video Decoder",
          filterNameCompare) >= 0)
      {
        if (Sage.DBG) System.out.println("Elecard filter detected, using Elecard/DScaler combination for playback");
        setVideoDecoderFilter("Elecard MPEG2 Video Decoder");
        if (java.util.Arrays.binarySearch(dshowFilters, "MPEG Audio Decoder", filterNameCompare) >= 0)
        {
          if (Sage.DBG) System.out.println("Using the system MPEG Audio Decoder");
          setAudioDecoderFilter("MPEG Audio Decoder");
        }
        /*else if (java.util.Arrays.binarySearch(dshowFilters, "Moonlight Odio Dekoda", filterNameCompare) >= 0)
				{
					if (Sage.DBG) System.out.println("Using the Moonlight Odio Dekoda");
					setAudioDecoderFilter("Moonlight Odio Dekoda");
				}
				else
				{
					if (Sage.DBG) System.out.println("No desirable MPEG audio decoder found, defaulting it");
					setAudioDecoderFilter(Sage.rez("Default"));
				}
				setUseOverlay(true);*/
        setUseDscaler(true);
        setDscalerMode(DSCALER_MODE_BOB);
        setDscalerDoubleRefresh(false);
        setDscalerOddFieldFirst(false);
        setAudioRenderFilter(Sage.rez("Default"));
        return;
      }
      if (Sage.DBG) System.out.println("Searching for existence of Sonic Decoders...");
      if (java.util.Arrays.binarySearch(dshowFilters, "Sonic Cinemaster@ DS Video Decoder",
          filterNameCompare) >= 0 && java.util.Arrays.binarySearch(dshowFilters, "Sonic Cinemaster@ DS Audio Decoder",
              filterNameCompare) >= 0)
      {
        if (Sage.DBG) System.out.println("Sonic filters detected and being used");
        setVideoDecoderFilter("Sonic Cinemaster@ DS Video Decoder");
        setAudioDecoderFilter("Sonic Cinemaster@ DS Audio Decoder");
        //				setUseOverlay(true);
        setAudioRenderFilter(Sage.rez("Default"));
        return;
      }
      if (Sage.DBG) System.out.println("Searching for existence of Ravisent Decoders...");
      if (java.util.Arrays.binarySearch(dshowFilters, "RAVISENT Cinemaster DS Video Decoder",
          filterNameCompare) >= 0 && java.util.Arrays.binarySearch(dshowFilters, "RAVISENT Cinemaster DS Audio Decoder",
              filterNameCompare) >= 0)
      {
        if (Sage.DBG) System.out.println("Ravisent filters detected and being used");
        setVideoDecoderFilter("RAVISENT Cinemaster DS Video Decoder");
        setAudioDecoderFilter("RAVISENT Cinemaster DS Audio Decoder");
        //				setUseOverlay(true);
        setAudioRenderFilter(Sage.rez("Default"));
        return;
      }
      if (Sage.DBG) System.out.println("Searching for existence of Intervideo Decoders...");
      if (java.util.Arrays.binarySearch(dshowFilters, "InterVideo Video Decoder",
          filterNameCompare) >= 0 && java.util.Arrays.binarySearch(dshowFilters, "InterVideo Audio Decoder",
              filterNameCompare) >= 0)
      {
        if (Sage.DBG) System.out.println("Intervideo filters detected and being used");
        setVideoDecoderFilter("InterVideo Video Decoder");
        setAudioDecoderFilter("InterVideo Audio Decoder");
        //				setUseOverlay(true);
        setAudioRenderFilter(Sage.rez("Default"));
        return;
      }
      if (Sage.DBG) System.out.println("Searching for existence of Cyberlink Decoders...");
      if (java.util.Arrays.binarySearch(dshowFilters, "Cyberlink Video/SP Decoder",
          filterNameCompare) >= 0 && java.util.Arrays.binarySearch(dshowFilters, "Cyberlink Audio Decoder",
              filterNameCompare) >= 0)
      {
        if (Sage.DBG) System.out.println("Cyberlink filters detected and being used");
        setVideoDecoderFilter("Cyberlink Video/SP Decoder");
        setAudioDecoderFilter("Cyberlink Audio Decoder");
        //				setUseOverlay(true);
        setAudioRenderFilter(Sage.rez("Default"));
        return;
      }
      if (Sage.DBG) System.out.println("Nothing suitable found for MPEG2, using system defaults");
      setVideoDecoderFilter(Sage.rez("Default"));
      setAudioDecoderFilter(Sage.rez("Default"));
      //			setUseOverlay(true);
      setAudioRenderFilter(Sage.rez("Default"));
    }
  }

  public DShowTVPlayer()
  {
    super();
  }

  public boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file)
  {
    // Type checking is handled in VideoFrame so we can safely accept this whenever its presented to us
    return true;/*currHintEncoding != null && currHintEncoding.length() != 0 && encodingHint != null &&
			encodingHint.length() != 0 && encodingHint.equals(currHintEncoding) && majorTypeHint == currHintMajorType &&
			minorTypeHint == currHintMinorType && currHintMinorType != MediaFile.MEDIASUBTYPE_MPEG2_TS;*/
    // We can't fast switch on TS currently because it uses a different demux which doesn't support it
  }

  public int getPlaybackCaps()
  {
    return super.getPlaybackCaps() | TIMESHIFT_CAP;
  }

  public synchronized void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint,
      java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException
      {
    eos = false;
    setTimeshifting0(pHandle, timeshifted, bufferSize);
    currTimeshifted = timeshifted;
    long oldDur = getDurationMillis0(pHandle);
    switchLoadTVFile0(pHandle, file.getPath(), hostname, waitUntilDone);
    currHintMajorType = majorTypeHint;
    currHintMinorType = minorTypeHint;
    currHintEncoding = encodingHint;
    currFile = file;
    int lastState = currState;
    currState = LOADED_STATE;
    // Seek it back to the beginning, so we're loaded correctly.
    //		seek(0);
    // Put the graph back into the same state as before
    if (lastState == PLAY_STATE)
      play();
    else
      pause();

    // Wait until the file is loaded and the duration is updated before we return or we'll
    // give an incorrect duration to the caller.
    int numWaits = 20;
    while (!timeshifted && numWaits >= 0 && getDurationMillis0(pHandle) == oldDur)
    {
      if (Sage.DBG && numWaits % 5 == 0) System.out.println("Waiting for duration to update from file change...");
      try{Thread.sleep(50);}catch(Exception e){}
      numWaits--;
    }
      }

  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    eos = false;
    pHandle = createGraph0();
    uiMgr = VideoFrame.getVideoFrameForPlayer(this).getUIMgr();
    lastVolume = (uiMgr.getBoolean("media_player_uses_system_volume", Sage.WINDOWS_OS && !Sage.VISTA_OS)) ? 1.0f : uiMgr.getFloat("videoframe/last_dshow_volume", 1.0f);
    currHintMajorType = majorTypeHint;
    currHintMinorType = minorTypeHint;
    currHintEncoding = encodingHint;
    MediaFile currMF = VideoFrame.getMediaFileForPlayer(DShowTVPlayer.this);
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
      // If we're doing an ISO mount; then we have local access and are not going through the server
      hostname = null;
    }
    String[] fileSequence = null;
    if (currMF.isBluRay())
    {
      sage.media.bluray.BluRayParser mybdp = new sage.media.bluray.BluRayParser(file, hostname);
      try
      {
        mybdp.fullyAnalyze();
      }
      catch (java.io.IOException ioe)
      {
        if (Sage.DBG) System.out.println("IO Error analyzing BluRay file structure of:" + ioe);
        throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
      }
      bdp = mybdp;
      currBDTitle = uiMgr.getVideoFrame().getBluRayTargetTitle();
      if (currBDTitle <= 0)
        currBDTitle = bdp.getMainPlaylistIndex() + 1;
      currBDTitle = Math.max(1, Math.min(currBDTitle, bdp.getNumPlaylists()));
      sage.media.bluray.MPLSObject currPlaylist = bdp.getPlaylist(currBDTitle - 1);

      fileSequence = new String[currPlaylist.playlistItems.length];
      long[] ptsOffsets = new long[fileSequence.length];
      java.io.File streamDir = new java.io.File(file, "STREAM");
      long[] totalPts = new long[fileSequence.length];
      for (int i = 0; i < fileSequence.length; i++)
      {
        fileSequence[i] = new java.io.File(streamDir, currPlaylist.playlistItems[i].itemClips[0].clipName + (bdp.doesUseShortFilenames() ? ".MTS" : ".m2ts")).getPath();
        ptsOffsets[i] = (i == 0 ? 0 : totalPts[i - 1]) - currPlaylist.playlistItems[i].inTime;
        totalPts[i] = (i == 0 ? 0 : totalPts[i - 1]) + (currPlaylist.playlistItems[i].outTime - currPlaylist.playlistItems[i].inTime);
      }
      chapterOffsets = new long[currPlaylist.playlistMarks.length];
      for (int i = 0; i < chapterOffsets.length; i++)
      {
        int itemRef = currPlaylist.playlistMarks[i].playItemIdRef;
        chapterOffsets[i] = (itemRef == 0 ? 0 : totalPts[itemRef - 1]) + currPlaylist.playlistMarks[i].timestamp - currPlaylist.playlistItems[itemRef].inTime;
      }
      // If the last file in the sequence is smaller than 32K, then drop it because that causes issues in our demux
      if (fileSequence.length > 1 && new java.io.File(fileSequence[fileSequence.length - 1]).length() < 32768)
      {
        if (Sage.DBG) System.out.println("Removing last file from BluRay sequence due to its small size");
        String[] newFS = new String[fileSequence.length - 1];
        System.arraycopy(fileSequence, 0, newFS, 0, newFS.length);
        fileSequence = newFS;
      }
      if (sage.Sage.DBG) System.out.println("Established BluRay file sequence with " + fileSequence.length + " segments");
    }
    // Set the default language index before we do the filters so we get the right audio codec selected
    setDefaultLangIndex();
    String hwDecoder = Sage.get(prefs + PS_HW_DECODER_FILTER, "");
    boolean disableSWDecoding = hwDecoder.length() > 0 && Sage.getBoolean(prefs + DISABLE_SW_DECODING, false);
    if (!disableSWDecoding)
      setFilters();
    if (hwDecoder.length() > 0)
    {
      if (Sage.DBG) System.out.println("Setting MpegDeMux NumBuffers to " + Sage.getInt("pvr350_demux_numbuffers", 32) + " to avoid PVR350 lockups");
      Sage.writeDwordValue(Sage.HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\DSFilters\\MpegDeMux",
          "NumBuffers", Sage.getInt("pvr350_demux_numbuffers", 32));
    }
    setTimeshifting0(pHandle, timeshifted, bufferSize);
    currTimeshifted = timeshifted;
    if (currMF.isBluRay())
    {
      setupGraphMultifile0(pHandle, fileSequence, hostname, !disableSWDecoding, !disableSWDecoding);
    }
    else
    {
      setupGraph0(pHandle, file != null ? file.getPath() : null, hostname, !disableSWDecoding && !"".equals(currMF.getPrimaryVideoFormat()), !disableSWDecoding);
    }
    if (transparency != TRANSLUCENT && (majorTypeHint == MediaFile.MEDIATYPE_VIDEO ||
        majorTypeHint == MediaFile.MEDIATYPE_DVD || majorTypeHint == MediaFile.MEDIATYPE_BLURAY) && !disableSWDecoding) // not vmr & video is present, so we need to render to the HWND
      setVideoHWND0(pHandle, VideoFrame.getVideoFrameForPlayer(this).getVideoHandle());
    colorKey = null;
    currCCState = -1;
    videoDimensions = null;
    getVideoDimensions();
    if (hwDecoder.length() > 0)
      addHWDecoderFilter0(pHandle, hwDecoder, disableSWDecoding);

    currFile = file;
    currState = LOADED_STATE;
    getColorKey();
    setNotificationWindow0(pHandle, Sage.mainHwnd);
  }

  public void free()
  {
    if (changedElecardRegistry)
    {
      Sage.writeDwordValue(Sage.HKEY_CURRENT_USER,
          "Software\\Elecard\\MPEG2 Video Decoder", "SoftwareBob", oldElecardRegistryValue);
      changedElecardRegistry = false;
    }
    super.free();
    if (unmountRequired != null)
    {
      java.io.File removeMe = unmountRequired;
      unmountRequired = null;
      FSManager.getInstance().releaseISOMount(removeMe);
    }
  }

  protected void setFilters() throws PlaybackException
  {
    String vidDec = Sage.get(prefs + VIDEO_DECODER_FILTER, "SageTV MPEG Video Decoder");
    java.util.Map renderOptions = new java.util.HashMap();
    nullVideoDim = false;
    // See if the content is interlaced. If it's not we don't want to use DScaler.
    sage.media.format.ContainerFormat cf = getCurrFormat();
    boolean isInterlaced = true;
    boolean audioOnly = cf != null && cf.getVideoFormat() == null;
    sage.media.format.VideoFormat vidForm = null;
    if (cf != null)
    {
      vidForm = cf.getVideoFormat();
      isInterlaced = vidForm != null && vidForm.isInterlaced();
    }
    if (!audioOnly && Sage.get("videoframe/video_postprocessing_filter", "").length() > 0)
    {
      setVideoPostProcessingFilter0(pHandle, Sage.get("videoframe/video_postprocessing_filter", ""), null);
    }
    else if (!audioOnly && isInterlaced && getUseDscaler() && (vidDec.toLowerCase().indexOf("elecard") != -1 ||
        vidDec.toLowerCase().indexOf("mainconcept") != -1 || vidDec.toLowerCase().indexOf("sagetv") != -1 ||
        !Sage.getBoolean("videoframe/require_compatible_decoder_for_dscaler", true)))
    {
      java.util.Map dscOptions = new java.util.HashMap();
      int dscalerMode = getDscalerMode();
      dscOptions.put(DSCALER_MODE, new Integer(dscalerMode));
      dscOptions.put(DSCALER_ODD_FIELD_FIRST, Boolean.valueOf(getDscalerOddFieldFirst()));
      dscOptions.put(DSCALER_DOUBLE_REFRESH, Boolean.valueOf(getDscalerDoubleRefresh()));
      if (dscalerMode == DSCALER_MODE_PLUGIN)
        dscOptions.put(DSCALER_PLUGIN, getDscalerPlugin());
      setVideoPostProcessingFilter0(pHandle, DSCALER_GUID, dscOptions);
    }
    else if (!audioOnly)
    {
      int dxvaMode = getDxvaMpegMode();
      if (dxvaMode != 0)
        renderOptions.put(DXVA_MPEG_MODE, new Integer(dxvaMode));
      int deinterlaceMode = getForceDeinterlace();
      if (deinterlaceMode != 0)
        renderOptions.put(FORCE_DEINTERLACE, new Integer(deinterlaceMode));
    }
    if (!audioOnly && (getUseEvr() || getUseVmr()))
    {
      if (DirectX9SageRenderer.getD3DObjectPtr() != 0 && DirectX9SageRenderer.getD3DDevicePtr() != 0 &&
          (getUseVmr() || DirectX9SageRenderer.getD3DDeviceMgr() != 0))
      {
        renderOptions.put("d3d_device_ptr", new Long(DirectX9SageRenderer.getD3DDevicePtr()));
        renderOptions.put("d3d_object_ptr", new Long(DirectX9SageRenderer.getD3DObjectPtr()));
        renderOptions.put("d3d_device_mgr", new Long(DirectX9SageRenderer.getD3DDeviceMgr()));
        // If the video height doesn't match that of a standard broadcast; then don't do the
        // CC filter insertion since that can mess up the video dimensions w/ VMR9/EVR
        int videoHeight = (vidForm != null) ? vidForm.getHeight() : 480;
        renderOptions.put("enable_cc", Boolean.valueOf(!Sage.getBoolean("videoframe/do_not_insert_directshow_cc_filter", false) &&
            !VideoFrame.getMediaFileForPlayer(DShowTVPlayer.this).isBluRay() && (videoHeight == 480 || videoHeight == 576 ||
            videoHeight == 720 || videoHeight == 1080 || videoHeight == 540)));
        setVideoRendererFilter0(pHandle, getUseEvr() ? EVR_GUID : VMR9_GUID, renderOptions);
        nullVideoDim = true;
        transparency = TRANSLUCENT;
      }
      else
      {
        setVideoRendererFilter0(pHandle, OVERLAY_GUID, renderOptions);
        transparency = BITMASK;
      }
    }
    else if (!audioOnly && getUseOverlay())
    {
      setVideoRendererFilter0(pHandle, OVERLAY_GUID, renderOptions);
      transparency = BITMASK;
    }
    else
    {
      nullVideoDim = true;
      transparency = OPAQUE;
    }
    String audDec = Sage.get(prefs + AUDIO_RENDER_FILTER, "Default");
    if (audDec.length() > 0 && !"Default".equals(audDec) && !Sage.rez("Default").equals(audDec))
      setAudioRendererFilter0(pHandle, audDec, null);
    String audType = cf != null ? cf.getPrimaryAudioFormat() : "";

    if (Sage.readDwordValue(Sage.HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\DSFilters\\MpegDeMux", "EnableHDAudio") == 0)
    {
      if (sage.media.format.MediaFormat.DTS_HD.equals(audType) || sage.media.format.MediaFormat.DTS_MA.equals(audType))
        audType = sage.media.format.MediaFormat.DTS;
      else if (sage.media.format.MediaFormat.DOLBY_HD.equals(audType))
        audType = sage.media.format.MediaFormat.AC3;
    }

    // Check for H.264 video and use that filter in that case
    String primaryVidForm = cf != null ? cf.getPrimaryVideoFormat() : "";
    boolean isH264 = sage.media.format.MediaFormat.H264.equals(primaryVidForm);
    if (isH264)
    {
      setupH264DecoderFilter();
    }
    // The video decoder filters are for MPEG2, not for DivX for MPEG4 so don't add them in that case
    else if (!audioOnly && vidDec.length() > 0 && !"Default".equals(vidDec) && !Sage.rez("Default").equals(vidDec) &&
        sage.media.format.MediaFormat.MPEG2_VIDEO.equals(primaryVidForm))
    {
      // If we're using an Elecard filter and DScaler then we need to specify to turn off
      // SW deinterlacing for Elecard
      if (getUseDscaler() && vidDec.toLowerCase().indexOf("elecard") != -1)
      {
        changedElecardRegistry = true;
        oldElecardRegistryValue = Sage.readDwordValue(Sage.HKEY_CURRENT_USER,
            "Software\\Elecard\\MPEG2 Video Decoder", "SoftwareBob");
        Sage.writeDwordValue(Sage.HKEY_CURRENT_USER,
            "Software\\Elecard\\MPEG2 Video Decoder", "SoftwareBob", 0);
      }
      if (sage.media.format.MediaFormat.MP2.equals(audType))
      {
        String altVidDec = Sage.get(prefs + VIDEO_DECODER_FILTER + "_alt", "");
        if (altVidDec.length() > 0)
          vidDec = altVidDec;
      }
      setVideoDecoderFilter0(pHandle, vidDec, null);
    }
    else if (!audioOnly)
    {
      // Check for a format specific decoder
      if (primaryVidForm.length() > 0)
        vidDec = Sage.get(prefs + primaryVidForm.toLowerCase() + "_" + VIDEO_DECODER_FILTER, "");
      // Default to the WindowsMedia VC1 decoder if another one isn't specified
      if (vidDec.length() == 0 && sage.media.format.MediaFormat.VC1.equals(primaryVidForm))
        vidDec = "WMVideo Decoder DMO";
      if (vidDec.length() > 0 && !"Default".equals(vidDec) && !Sage.rez("Default").equals(vidDec))
        setVideoDecoderFilter0(pHandle, vidDec, null);
    }

    // Only use the selected audio decoder if it's MPEG1/AC3 audio
    audDec = "";
    if (sage.media.format.MediaFormat.AC3.equals(audType) || sage.media.format.MediaFormat.MP2.equals(audType))
      audDec = Sage.get(prefs + AUDIO_DECODER_FILTER, "SageTV MPEG Audio Decoder");
    else
    {
      // Check for a format specific audio decoder
      if (audType.length() > 0)
        audDec = Sage.get(prefs + audType.toLowerCase() + "_" + AUDIO_DECODER_FILTER, "");
    }
    if (audDec.length() > 0 && !"Default".equals(audDec) && !Sage.rez("Default").equals(audDec))
      setAudioDecoderFilter0(pHandle, audDec, null);
    else if (sage.media.format.MediaFormat.DTS.equalsIgnoreCase(audType) || "DCA".equalsIgnoreCase(audType))
      setAudioDecoderFilter0(pHandle, "AC3Filter", null);
  }

  public boolean playControlEx(int playCode, long param1, long param2) throws PlaybackException
  {
    if (super.playControlEx(playCode, param1, param2))
      return true;
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      if (bdp != null && playCode == VideoFrame.DVD_CONTROL_CHAPTER_NEXT)
      {
        if (getDVDChapter() < getDVDTotalChapters())
        {
          long newTime = getChapterStartMsec(lastTargetChapter = (getDVDChapter() + 1));
          if (Sage.DBG) System.out.println("Next chapter for BluRay seeking to " + newTime);
          seek(newTime);
          lastChapterSeekTime = Sage.eventTime();
        }
      }
      else if (bdp != null && playCode == VideoFrame.DVD_CONTROL_CHAPTER_PREV)
      {
        int currChapter = getDVDChapter();
        if (getMediaTimeMillis() - getChapterStartMsec(currChapter) > 7000 || currChapter == 1)
        {
          if (Sage.DBG) System.out.println("Prev chapter (restart curr chapter) for BluRay");
          seek(getChapterStartMsec(currChapter));
        }
        else
        {
          long newTime = getChapterStartMsec(lastTargetChapter = (currChapter - 1));
          if (Sage.DBG) System.out.println("Prev chapter for BluRay seeking to " + newTime);
          seek(newTime);
          lastChapterSeekTime = Sage.eventTime();
        }
      }
      else if (bdp != null && playCode == VideoFrame.DVD_CONTROL_CHAPTER_SET)
      {
        long newTime = getChapterStartMsec(lastTargetChapter = (int)param1);
        if (Sage.DBG) System.out.println("Set chapter (" + param1 + ") for BluRay seeking to " + newTime);
        seek(newTime);
        lastChapterSeekTime = Sage.eventTime();
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
      else if (bdp != null && playCode == VideoFrame.DVD_CONTROL_TITLE_SET)
      {
        if (param1 > 0 && param1 <= bdp.getNumPlaylists())
          uiMgr.getVideoFrame().setBluRayTargetTitle((int)param1);
        else
          uiMgr.getVideoFrame().playbackControl(0);
      }
    }
    return false;
  }

  public int getDVDChapter()
  {
    if (bdp != null)
      return (Sage.eventTime() - lastChapterSeekTime < 1500) ? lastTargetChapter : getChapterForMsec(getMediaTimeMillis());
    return 0;
  }

  public int getDVDTotalChapters()
  {
    if (bdp != null && chapterOffsets != null)
      return chapterOffsets.length;
    return 0;
  }

  public int getDVDDomain()
  {
    if (bdp != null)
      return 4; // We're always in the movie for BluRays
    return 0;
  }

  public int getDVDAngle()
  {
    return (bdp != null) ? 1 : 0;
  }

  public int getDVDTitle()
  {
    return (bdp != null) ? currBDTitle : 0;
  }

  public int getDVDTotalAngles()
  {
    return (bdp != null) ? 1 : 0;
  }

  public int getDVDTotalTitles()
  {
    return (bdp != null) ? bdp.getNumPlaylists() : 0;
  }

  public String getBluRayTitleDesc(int titleNum)
  {
    return (bdp != null) ? bdp.getPlaylistDesc(titleNum - 1) : "";
  }

  private long getChapterStartMsec(int chapter)
  {
    if (chapterOffsets == null) return 0;
    return chapterOffsets[Math.max(0, Math.min(chapter - 1, chapterOffsets.length - 1))] / 45;
  }

  private int getChapterForMsec(long msec)
  {
    if (chapterOffsets == null) return 0;
    long pts45 = msec * 45;
    for (int i = 0; i < chapterOffsets.length; i++)
      if (chapterOffsets[i] > pts45)
        return i;
    return chapterOffsets.length;
  }

  protected sage.media.format.ContainerFormat getCurrFormat()
  {
    if (bdp != null)
    {
      return bdp.getFileFormat(currBDTitle - 1);
    }
    return super.getCurrFormat();
  }

  protected native void switchLoadTVFile0(long ptr, String filePath, String hostname, boolean waitUntilDone) throws PlaybackException;
  // To handle frame stepping on the PVR350 TV Output
  protected native boolean frameStep0(long ptr, int amount);
  protected native void addHWDecoderFilter0(long ptr, String filterName, boolean hwDecodeOnly) throws PlaybackException;
  // createGraph0 will create the peer native object and create the initial filter graph
  protected native long createGraph0() throws PlaybackException;
  protected native void setupGraphMultifile0(long ptr, String[] filePaths, String remoteHost, boolean renderVideo, boolean renderAudio) throws PlaybackException;

  protected UIManager uiMgr;
  private int oldElecardRegistryValue;
  private boolean changedElecardRegistry;
  protected java.io.File unmountRequired;
  protected long[] chapterOffsets; // 45kHz
  protected sage.media.bluray.BluRayParser bdp;
  protected long lastChapterSeekTime;
  protected int lastTargetChapter;
  protected int currBDTitle;
}
