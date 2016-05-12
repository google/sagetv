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

public class DShowCaptureDevice extends CaptureDevice
{
  public static final int SM2210_ENCODER_MASK = CaptureDevice.SM2210_ENCODER_MASK;
  public static final int PYTHON2_ENCODER_MASK = CaptureDevice.PYTHON2_ENCODER_MASK;
  public static final int VBDVCR_ENCODER_MASK = CaptureDevice.VBDVCR_ENCODER_MASK;
  public static final int MICRONAS_AUDIO_MASK = CaptureDevice.MICRONAS_AUDIO_MASK;
  public static final int HCW_CAPTURE_MASK = CaptureDevice.HCW_CAPTURE_MASK;
  public static final int BLACKBIRD_CAPTURE_MASK = CaptureDevice.BLACKBIRD_CAPTURE_MASK;
  public static final int HDPVR_ENCODER_MASK = CaptureDevice.HDPVR_ENCODER_MASK;

  public static final int RAW_AV_CAPTURE_MASK = MMC.RAW_AV_CAPTURE_MASK;
  public static final int MPEG_VIDEO_ONLY_CAPTURE_MASK = MMC.MPEG_VIDEO_ONLY_CAPTURE_MASK;
  public static final int MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK = MMC.MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK;
  public static final int MPEG_AV_CAPTURE_MASK = MMC.MPEG_AV_CAPTURE_MASK;
  public static final int LIVE_PREVIEW_MASK = MMC.LIVE_PREVIEW_MASK;
  public static final int MPEG_PURE_CAPTURE_MASK = MMC.MPEG_PURE_CAPTURE_MASK;
  public static final int RAW_VIDEO_CAPTURE_MASK = MMC.RAW_VIDEO_CAPTURE_MASK;
  public static final int BDA_VIDEO_CAPTURE_MASK = MMC.BDA_VIDEO_CAPTURE_MASK;    //ZQ
  // These are used in the native layer
  public static final int BDA_CAPTURE_TUNER_MASK = 0x10000;
  public static final int BDA_NETWORK_TUNER_MASK = 0x20000;
  public static final int BDA_RECEIVER_COMPONENT_MASK = 0x40000;
  public static final int BDA_VIRTUAL_TUNER_MASK = 0x80000;

  public static final int FM_RADIO_FREQ_MAX = 108100000;
  public static final int FM_RADIO_FREQ_MIN = 88100000;

  public static final int DEVICE_EXIST = 1;
  public static final int DEVICE_NO_EXIST = 0;
  public static final int DEVICE_INVALID = -1;
  static
  {
    // This library is also used for getting a list of the dshow filters, so we need it for players as well
    if (Sage.WINDOWS_OS)
      System.loadLibrary("DShowCapture");
  }
  public DShowCaptureDevice()
  {
    super();
  }

  public DShowCaptureDevice(int inID)
  {
    super(inID);
  }

  public void freeDevice()
  {
    if (Sage.DBG) System.out.println("freeDevice called for " + this);
    try { activateInput(null); } catch (EncodingException e){}
    writePrefs();
    if (pHandle != 0)
    {
      if (graphRunning)
        stopEncoding0(pHandle);
      graphRunning = false;
      synchronized (this)
      {
        teardownGraph0(pHandle);
        pHandle = 0;
      }
    }
  }

  public long getRecordedBytes()
  {
    long rv;
    synchronized (this)
    {
      rv = (pHandle == 0) ? -1 : getRecordedBytes0(pHandle);
    }
    if (rv < 0 && recFilename != null)
    {
      // This means it can't determine it natively, so we just go off the file size
      return new java.io.File(recFilename).length();
    }
    else return rv;
  }

  public void loadDevice() throws EncodingException
  {
    if (isCaptureFeatureSupported(SM2210_ENCODER_MASK))
    {
      userParamDir = Sage.readStringValue(Sage.HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common",
          "SM2210ParamDir");
      if (userParamDir == null)
        userParamDir = new java.io.File(System.getProperty("user.dir"), "Parameters").getAbsolutePath();
    }

    // This is to set the air/cable state for the tv tuner filter
    CaptureDeviceInput testInput = getActiveInput();
    if (testInput == null)
    {
      testInput = getDefaultInput();
      activateInput(testInput);
    }

    String tvTypeProp = Sage.get(prefs + "dshow_tv_type", "" );
    /* We don't need it anymore, I detecte in native code, but we keep it for special customizing seting in future ZQ.
		if (tvTypeProp != null && tvTypeProp.length() == 0)
		{
			Sage.put(prefs + "dshow_tv_type", (MMC.getInstance().getCountryCode() == 1) ? "ATSC" : "");
		}
     */

    // loadedCaptureFeatureBits indicate which features are in the loaded device setup. This is for
    // moving between BDA and non-BDA capture modes
    if ((captureFeatureBits & BDA_VIDEO_CAPTURE_MASK) != 0 && testInput.getType() != CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
      loadedCaptureFeatureBits = (((captureFeatureBits & ~BDA_VIDEO_CAPTURE_MASK) & ~BDA_DVBC_TUNING_MASK) & ~BDA_DVBS_TUNING_MASK) & ~BDA_DVBT_TUNING_MASK;
    else
      loadedCaptureFeatureBits = captureFeatureBits;
    if (testInput.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX && usesFixedBroadcastStd())
    {
      // We need to set the proper mode for which DVB input type it is if its a multimode card
      tvTypeProp = testInput.getBroadcastStandard();
    }

    pHandle = initGraph0(captureDeviceName, captureDeviceNum, loadedCaptureFeatureBits,
        tvTypeProp, activeSource.getTuningMode(),
        Sage.get("mmc/country", Sage.rez("default_country") ));
    /*                        TVTuningFrequencies.doesCountryHaveDVBTRegions(MMC.getInstance().getCountryCode()) ?
						Sage.get("mmc/dvbt_region", "" ) : Sage.get("mmc/country", Sage.rez("default_country") )); ZQ*/

    // this is to clear the current crossbar so activate actually sets it
    activateInput(null);
    activateInput(testInput);

    /*		if (activeSource != null)
		{
			if (Sage.DBG) System.out.println("LastChan=" + activeSource.lastChannel);
			if (activeSource.lastChannel.length() > 0 && (myVideoFrame.getClass() == BasicVideoFrame.class))
			{
				tuneToChannel(activeSource.lastChannel);

				// The encoder gets prepped usually after this, and that causes a delay between our commands, so hold up
				if (tuney != null)
					tuney.waitForCompletion();
			}
		}
     */		Sage.put(MMC.MMC_KEY + '/' + MMC.LAST_ENCODER_NAME, getName());
  }

  public void applyQuality() throws EncodingException
  {
    if (encodeParams == null)
      setEncodingQuality(currQuality);
    setEncodingProperties0(pHandle, currQuality, (encodeParams == null) ? null : encodeParams.getOptionsMap());
  }

  public void startEncoding(CaptureDeviceInput cdi, String encodeFile, String channel) throws EncodingException
  {
    boolean postActivateInput = "PX-TV100U".equals(captureDeviceName);
    if (cdi != null)
      activateInput(cdi);
    // For QAM on Windows we want to send the tune request after we start the capture graph
    boolean doPostTune = Sage.WINDOWS_OS && activeSource.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX &&
        activeSource.isCableTV();
    boolean unloadDeviceDuringTune = isCaptureFeatureSupported(HDPVR_ENCODER_MASK) && captureDeviceName.indexOf("Colossus") == -1 && Sage.getBoolean("mmc/hdpvr_magic2", false);
    if (channel != null && !doPostTune)
    {
      // If the channel is nothing, then use the last one. This happens with default live.
      if (channel.length() == 0)
        channel = activeSource.getChannel();
      if (unloadDeviceDuringTune)
      {
        freeDevice();
        if (cdi != null) // otherwise the tune will get rejected
          activateInput(cdi);
      }
      tuneToChannel(channel);
      long tuneWaitDelay = getPostTuningDelay();
      if (tuneWaitDelay > 0)
      {
        try{Thread.sleep(tuneWaitDelay);}catch(Exception e){}
      }
      if (unloadDeviceDuringTune)
      {
        // load will also reactivate the proper input
        loadDevice();
      }
    }
    Sage.startEncoding(this, encodeFile);

    if (doPostTune)
    {
      if (channel != null)
      {
        // If the channel is nothing, then use the last one. This happens with default live.
        if (channel.length() == 0)
          channel = activeSource.getChannel();
        tuneToChannel(channel);
      }
    }

    // Initially this may not be set so do so now.
    if ((activeSource.getChannel() != null && activeSource.getChannel().length() == 0) ||
        activeSource.getChannel().equals("0"))
    {
      activeSource.setLastChannel(getChannel());
      activeSource.writePrefs();
    }

    // This is required to get the USB audio preview to work on the PX-TV100U. If we don't also do it before
    // we tune the channel, then the tune can take along time so we end up doing this twice.
    if (postActivateInput && cdi != null)
      activateInput(cdi);
  }

  void startEncodingSync(String filename) throws EncodingException
  {
    if (activeSource.getType() == CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX)
    {
      MPEG2EncodingParams fmQual = new MPEG2EncodingParams();
      fmQual.videobitrate = new Integer(500000);
      fmQual.audiosampling = new Integer(44100);
      setEncodingProperties0(pHandle, "FMRadio", fmQual.getOptionsMap());
    }
    else
    {
      if (encodeParams == null)
        setEncodingQuality(currQuality);
      setEncodingProperties0(pHandle, currQuality, (encodeParams == null) ? null : encodeParams.getOptionsMap());
    }
    currentlyRecordingBufferSize = recordBufferSize;
    setCircularFileSize0(pHandle, currentlyRecordingBufferSize);
    if (hasAuxCaptureDevice())
      setAudioCaptureSource0(pHandle, getAuxCaptureDevice(), getAuxCaptureDeviceNum(), getAuxCaptureDeviceIndex() );
    if (hasVideoProcessor())
      setVideoProcessor0(pHandle, getVideoProcessor());
    if (hasAudioProcessor())
      setAudioProcessor0(pHandle, getAudioProcessor());
    setupEncodingGraph0(pHandle, filename);
    startEncoding0(pHandle);
    recFilename = filename;
    recStart = Sage.time();

    // This can change between QAM and ATSC, so we need to get it every time
    //if (broadcastStd == null || broadcastStd.length() == 0)
    if (!usesFixedBroadcastStd())
      activeSource.setBroadcastStd(getBroadcastStandard0(pHandle));
    graphRunning = true;

    // NOTE: NVidia DualTV cards require setting the ProcAmp color controls after the graph starts running.
    if (captureDeviceName.startsWith("NVIDIA DualTV Capture"))
      updateColors();
  }

  public void stopEncoding()
  {
    Sage.stopEncoding(this);
  }
  void stopEncodingSync()
  {
    recFilename = null;
    recStart = 0;
    stopEncoding0(pHandle);
    graphRunning = false;
  }

  public void switchEncoding(String switchFile, String channel) throws EncodingException
  {
    //		if (channel != null)
    //			tuneToChannel(channel);
    Sage.switchEncoding(this, switchFile);
  }
  void switchEncodingSync(String switchFile) throws EncodingException
  {
    switchOutputFile0(pHandle, switchFile);
    recFilename = switchFile;
    recStart = Sage.time();
  }

  public boolean isLoaded()
  {
    return pHandle != 0 || graphReload;
  }

  public CaptureDeviceInput activateInput(CaptureDeviceInput activateMe) throws EncodingException
  {
    // Check for graph destruction->rebuild for switching between a digital TV & analog input (BDA vs. WDM)
    boolean reloadNow = false;
    try
    {
      String newTuningMode = (activateMe != null) ? activateMe.getTuningMode() : null;
      boolean tuneModeChange = (lastTuningMode != newTuningMode) && (lastTuningMode == null || !lastTuningMode.equals(newTuningMode));

      if (pHandle != 0 && activeSource != null && activateMe != null && (activeSource.getType() != activateMe.getType() || (tuneModeChange && lastTuningMode != null)) &&
          (activeSource.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX || activateMe.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX))
      {
        if (Sage.DBG) System.out.println("Destroying/rebuilding graph for WDM/BDA transition or tuning mode change tuneModeChange=" + (tuneModeChange && lastTuningMode != null));
        graphReload = true;
        reloadNow = true;
        freeDevice();
      }


      // Hmmmm....this may be a good idea. We did it on Linux to fix bugs, could help on Windows too
      // NOTE: We always activate the crossbar on the TV100U because it speeds up tuning by doing this
      if (!graphReload && !tuneModeChange && !"PX-TV100U".equals(captureDeviceName) && captureDeviceName.indexOf("Colossus") == -1 &&
          !Sage.getBoolean("mmc/set_crossbar_redundantly", false) && (activeSource == activateMe)) return activeSource;
      super.activateInput(activateMe);
      if (reloadNow)
      {
        loadDevice();
      }
      if (activeSource != null && pHandle != 0)
      {
        boolean savePrefsAfter = (activeSource.getBrightness() < 0) || (activeSource.getContrast() < 0) ||
            (activeSource.getHue() < 0) || (activeSource.getSaturation() < 0) || (activeSource.getSharpness() < 0);
        Sage.switchToConnector(this);
        if (savePrefsAfter)
          writePrefs();
      }
    }
    finally
    {
      if (reloadNow)
        graphReload = false;
    }
    return activeSource;
  }

  int getVideoFormatCode()
  {
    int mmcFormatCode = MMC.getInstance().getVideoFormatCode();
    if (mmcFormatCode == 8)
    {
      // Generic PAL, modify it based on the country
      mmcFormatCode = TVTuningFrequencies.getVideoFormatCode(MMC.getInstance().getCountryCode());
    }
    return mmcFormatCode;
  }

  private int getTunerStreamType()
  {
    if (activeSource != null && activeSource.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
    {
      if (Sage.getBoolean("mmc/encode_digital_tv_as_program_stream", true))
        return MediaFile.MEDIASUBTYPE_MPEG2_PS;
      else
        return MediaFile.MEDIASUBTYPE_MPEG2_TS;
    }
    else
      return 0;
  }

  void switchToConnectorSync() throws EncodingException
  {
    if (activeSource.getType() == 1 && activeSource.getIndex() > 1) // tuner cross
    {
      switchToConnector0(pHandle, activeSource.getType(), 0, activeSource.getTuningMode(),
          MMC.getInstance().getCountryCode(), getVideoFormatCode());
      tuneToChannel0(pHandle, Integer.toString(activeSource.getIndex()), getTunerStreamType());
    }
    else if (activeSource.getType() == CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX)
    {
      switchToConnector0(pHandle, 1 /*tuner*/, activeSource.getIndex(), CaptureDeviceInput.FM_RADIO,
          MMC.getInstance().getCountryCode(), getVideoFormatCode());
    }
    else if (activeSource.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX && usesFixedBroadcastStd())
    {
      switchToConnector0(pHandle, activeSource.getType(), activeSource.getIndex(), activeSource.getBroadcastStandard(),
          MMC.getInstance().getCountryCode(), getVideoFormatCode());
    }
    else
    {
      switchToConnector0(pHandle, activeSource.getType(), activeSource.getIndex(), activeSource.getTuningMode(),
          MMC.getInstance().getCountryCode(), getVideoFormatCode());
    }
    lastTuningMode = activeSource.getTuningMode();
    int[] defaultColors = updateColors();
    activeSource.setDefaultColors(defaultColors[0], defaultColors[1], defaultColors[2], defaultColors[3],
        defaultColors[4]);
  }

  public int[] updateColors()
  {
    return updateColors0(pHandle, activeSource.getBrightness(), activeSource.getContrast(),
        activeSource.getHue(), activeSource.getSaturation(), activeSource.getSharpness());
  }

  void tuneToChannelSync(String tuneString)
  {
    tuneToChannel0(pHandle, tuneString, getTunerStreamType());
  }

  boolean autoTuneChannelSync(String tuneString)
  {
    return autoTuneChannel0(pHandle, tuneString, getTunerStreamType());
  }

  String autoScanChannelSync(String tuneString)
  {
    String country = Sage.get("mmc/country", Sage.rez("default_country") );
    int country_code = TVTuningFrequencies.getCountryCode(country);
    String country_region = country;
    if ("DVB-T".equals(activeSource.getBroadcastStandard()))
      country_region += TVTuningFrequencies.doesCountryHaveDVBTRegions(country_code) ? "-" +
          Sage.get("mmc/dvbt_region", "" ) : "";
          else if ("DVB-S".equals(activeSource.getBroadcastStandard()))
            country_region += TVTuningFrequencies.doesCountryHaveDVBSRegions(country_code) ? "-" +
                Sage.get("mmc/dvbs_region", "" ) : "";
                else if ("DVB-C".equals(activeSource.getBroadcastStandard()))
                  country_region += TVTuningFrequencies.doesCountryHaveDVBCRegions(country_code) ? "-" +
                      Sage.get("mmc/dvbc_region", "" ) : "";

                      return scanBDAChannel0(pHandle, tuneString, country_region , getTunerStreamType());
  }

  protected boolean doTuneChannel(String tuneString, boolean autotune)
  {
    // Clean any bad chars from the tuneString
    if (Sage.getBoolean("mmc/remove_non_numeric_characters_from_channel_change_strings", false))
    {
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < tuneString.length(); i++)
      {
        char c = tuneString.charAt(i);
        if ((c >= '0' && c <= '9') || c == '.' || c == '-')
          sb.append(c);
      }
      tuneString = sb.toString();
    }
    boolean rv = false;
    try
    {
      if (activeSource.getType() == 1 || activeSource.getType() == CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX ||
          activeSource.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
      {
        // Check the bounds on it if its for Radio
        if (activeSource.getType() == CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX)
        {
          try
          {
            int inty = Integer.parseInt(tuneString);
            tuneString = Integer.toString(Math.min(Math.max(inty, FM_RADIO_FREQ_MIN), FM_RADIO_FREQ_MAX));
          }
          catch (Exception e){}
        }
        if (activeSource.getIndex() > 1)
        {
          if (autotune)
            rv = Sage.autoTuneChannel(this, Integer.toString(activeSource.getIndex()));
          else
            Sage.tuneToChannel(this, Integer.toString(activeSource.getIndex()));
        }
        else
        {
          if (autotune)
            rv = Sage.autoTuneChannel(this, tuneString);
          else
            Sage.tuneToChannel(this, tuneString);
        }
        // Fixup any issue with the last channel being set incorrectly from a WatchLive call which would then
        // cause the always_tune_channel to potentially skip it
        if (activeSource.getTuningPlugin().length() > 0 &&
            (Sage.getBoolean(MMC.MMC_KEY + '/' + MMC.ALWAYS_TUNE_CHANNEL, true) ||
                !tuneString.equals(getChannel()) ||
                recordBufferSize > 0))
        {
          doPluginTuneWindows(activeSource, tuneString);
        }
      }
      else
      {
        // Fixup any issue with the last channel being set incorrectly from a WatchLive call which would then
        // cause the always_tune_channel to potentially skip it
        if (activeSource.getTuningPlugin().length() > 0 &&
            (Sage.getBoolean(MMC.MMC_KEY + '/' + MMC.ALWAYS_TUNE_CHANNEL, true) ||
                !tuneString.equals(getChannel()) ||
                recordBufferSize > 0))
        {
          doPluginTuneWindows(activeSource, tuneString);
        }
        rv = true;
      }
    }
    catch (NumberFormatException e){}
    return rv;
  }

  protected boolean doScanChannel(String tuneString)
  {
    // Clean any bad chars from the tuneString
    if (Sage.getBoolean("mmc/remove_non_numeric_characters_from_channel_change_strings", true))
    {
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < tuneString.length(); i++)
      {
        char c = tuneString.charAt(i);
        if ((c >= '0' && c <= '9') || c == '.' || c == '-')
          sb.append(c);
      }
      tuneString = sb.toString();
    }
    boolean rv = false;
    if ( activeSource.getType() != CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX )
    {
      return doTuneChannel( tuneString, true );
    } else
    {
      return Sage.autoScanChannel(this, tuneString);
    }

  }

  protected String doScanChannelInfo(String tuneString)
  {
    // Clean any bad chars from the tuneString
    if (Sage.getBoolean("mmc/remove_non_numeric_characters_from_channel_change_strings", true))
    {
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < tuneString.length(); i++)
      {
        char c = tuneString.charAt(i);
        if ((c >= '0' && c <= '9') || c == '.' || c == '-')
          sb.append(c);
      }
      tuneString = sb.toString();
    }
    boolean rv = false;
    if ( activeSource.getType() != CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX )
    {
      return doTuneChannel( tuneString, true ) ? "1" : "";
    } else
    {
      return Sage.autoScanChannelInfo(this, tuneString);
    }

  }

  public String getChannel()
  {
    if (activeSource == null) return "";
    if (activeSource.normalRF() || activeSource.getType() == CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX)
      return getChannel0(pHandle, 0);
    else
      return super.getChannel();
  }

  public void setEncodingQuality(String encodingName)
  {
    encodingName = MMC.cleanQualityName(encodingName);
    encodeParams = null;
    if (isCaptureFeatureSupported(SM2210_ENCODER_MASK))
    {
      String paramDir = userParamDir;
      java.io.File newParamFile = new java.io.File(paramDir, encodingName + ".par");
      java.io.File newHdrFile = new java.io.File(paramDir, encodingName + ".hdr");
      if (!newParamFile.isFile() || !newHdrFile.isFile())
        return;
      Sage.writeStringValue(Sage.HKEY_LOCAL_MACHINE, "SOFTWARE\\StreamMachine",
          "ParFileName", encodingName);
      Sage.writeStringValue(Sage.HKEY_LOCAL_MACHINE, "SOFTWARE\\StreamMachine",
          "ParFilePath", newParamFile.getAbsolutePath());
      Sage.writeStringValue(Sage.HKEY_LOCAL_MACHINE, "SOFTWARE\\StreamMachine",
          "HdrFilePath", newHdrFile.getAbsolutePath());
    }
    else
    {
      encodeParams = MPEG2EncodingParams.getQuality(encodingName);
      if (encodeParams == null)
      {
        // THIS MUST BE SET!!!
        if (isHWEncoder())
          encodeParams = MPEG2EncodingParams.DEFAULT_HW_QUALITY;
        else
          encodeParams = MPEG2EncodingParams.DEFAULT_SW_QUALITY;
      }
    }
    currQuality = encodingName;
    writePrefs();
  }

  int checkEncoder()
  {
    int test = isCaptureDeviceValid0(captureDeviceName, captureDeviceNum);
    if (test == DEVICE_NO_EXIST)
      return test;

    captureFeatureBits = getDeviceCaps0(captureDeviceName, captureDeviceNum);

    int[] crossUpdates = getCrossbarConnections0(captureDeviceName, captureDeviceNum);
    if (crossUpdates == null || crossUpdates.length == 0)
      return DEVICE_INVALID;

    java.util.Map crossTypeCount = new java.util.HashMap();
    for (int i = 0; i < crossUpdates.length; i++)
    {
      Integer currCount = (Integer) crossTypeCount.get(new Integer(crossUpdates[i]));
      crossTypeCount.put(new Integer(crossUpdates[i]),
          currCount == null ? (currCount = new Integer(0)) : (currCount = new Integer(currCount.intValue() + 1)));
      // Ignore secondary inputs on the HDPVR; these appear on the PCIe version and are not needed since the primary ones map to them as well
      if (isCaptureFeatureSupported(HDPVR_ENCODER_MASK) && currCount.intValue() > 0)
        continue;
      ensureInputExists(crossUpdates[i], currCount.intValue());
    }

    //ZQ if it's BDA capture, we don't use cross bar, we use Network provider...
    //JK but add the fake crossbar for digital tv tuner input
    if (( captureFeatureBits & BDA_VIDEO_CAPTURE_MASK ) != 0 )
    {
      if (((captureFeatureBits & BDA_DVBT_TUNING_MASK) != 0) || ((captureFeatureBits & BDA_DVBS_TUNING_MASK) != 0) || ((captureFeatureBits & BDA_DVBC_TUNING_MASK) != 0))
      {
        // Check if there's multiple DVB formats supported and add extra inputs in that case for them
        int dtvInputCount = 0;
        if ((captureFeatureBits & BDA_DVBT_TUNING_MASK) != 0)
        {
          ensureInputExists(CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX, dtvInputCount);
          CaptureDeviceInput cdi = getInput(CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX, dtvInputCount);
          cdi.setBroadcastStd("DVB-T");
          dtvInputCount++;
        }
        if ((captureFeatureBits & BDA_DVBS_TUNING_MASK) != 0)
        {
          ensureInputExists(CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX, dtvInputCount);
          CaptureDeviceInput cdi = getInput(CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX, dtvInputCount);
          cdi.setBroadcastStd("DVB-S");
          dtvInputCount++;
        }
        if ((captureFeatureBits & BDA_DVBC_TUNING_MASK) != 0)
        {
          ensureInputExists(CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX, dtvInputCount);
          CaptureDeviceInput cdi = getInput(CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX, dtvInputCount);
          cdi.setBroadcastStd("DVB-C");
          dtvInputCount++;
        }
      }
      else
        ensureInputExists(CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX, 0);
    }
    //ZQ

    return DEVICE_EXIST;
  }

  public boolean supportsFastMuxSwitch()
  {
    // We can't fast mux switch for AVI formats or on MPEG1. It also doesn't work on SVCD formats either because
    // they don't write out the system header frequent enough.
    sage.media.format.MediaFormat form = getEncoderMediaFormat();
    if (form == null)
      return false;
    String containerFormat = form.getFormatName();
    // We can only fast switch MPEG2 Stream, so skip anything else!
    if (!sage.media.format.MediaFormat.MPEG2_PS.equals(containerFormat) &&
        !sage.media.format.MediaFormat.MPEG2_TS.equals(containerFormat))
      return false;
    // We only support TS fast stream switching if its coming from the HDPVR
    if (sage.media.format.MediaFormat.MPEG2_TS.equals(containerFormat) &&
        !isCaptureFeatureSupported(HDPVR_ENCODER_MASK))
      return false;
    int st = getCurrQualityStreamType();
    if (st == MPEG2EncodingParams.STREAMOUTPUT_VCD)
      return false;
    // Apparently on the Plextor devices CVD doesn't work for fast mux switch for some reason...
    if ((captureFeatureBits & MMC.MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK) != 0 && st == MPEG2EncodingParams.STREAMOUTPUT_PROGRAM)
    {
      return false;
    }

    if (!isHWEncoder() && activeSource != null && activeSource.getType() != CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX) //ZQ. soft encoder can't be FastMux switched
    {
      return false;
    }

    // We support it in all other cases for digital TV and standard capture devices in DirectShow
    return true;
  }

  public int getSignalStrength()
  {
    return Sage.getSignalStrength(this);
  }
  int getSignalStrengthSync()
  {
    return getSignalStrength0(pHandle);
  }

  public int getResourceUsage()
  {
    // DivX SW Encoding uses the whole system's resources, so return 100 in that case
    if (!isHWEncoder() && isRecording() && encodeParams != null)
    {
      String outputTypeStr = (String) encodeParams.getOptionsMap().get("outputstreamtype");
      if (outputTypeStr != null)
      {
        try
        {
          int encType = Integer.parseInt(outputTypeStr);
          if (encType >= 100)
            return 100;
        }
        catch (NumberFormatException e){}
      }
    }
    return 0;
  }

  // Returns an abstract native pointer to the object that can be used to configure video preview
  public long getNativeVideoPreviewConfigHandle()
  {
    return getNativeVideoPreviewConfigHandle0(pHandle);
  }

  // Returns true for CaptureDevice implementations that can do data scanning on the specified input
  public boolean doesDataScanning()
  {
    return true;
  }
  // (for EPG data at this time), the input will be from this capture device AND if that input actually wants
  // a data scan to be performed
  public boolean wantsDataScanning(CaptureDeviceInput cdi)
  {
    return cdi != null && cdi.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX && requestedDataScanCDIs.contains(cdi);
  }
  // When we're in data scanning mode we're writing to the null file
  public void enableDataScanning(CaptureDeviceInput cdi) throws EncodingException
  {
    if (wantsDataScanning(cdi))
    {
      if (cdi != null)
        activateInput(cdi);
      String chan = activeSource.getChannel();
      if (chan.length() > 0 && Sage.VISTA_OS)
        tuneToChannel(chan);
      Sage.startEncoding(this, null);
      inDataScanMode = true;
    }
  }
  public void disableDataScanning()
  {
    if (inDataScanMode)
    {
      inDataScanMode = false;
      Sage.stopEncoding(this);
    }
  }
  public boolean isDataScanning()
  {
    return inDataScanMode;
  }
  public boolean requestDataScan(CaptureDeviceInput cdi)
  {
    return requestedDataScanCDIs.add(cdi);
  }
  public void cancelDataScanRequest(CaptureDeviceInput cdi)
  {
    requestedDataScanCDIs.remove(cdi);
  }

  public void setDTVStandard(String x)
  {
    Sage.put(prefs + "dshow_tv_type", x);
  }

  // We do have to synchronize some of the calls to the native layer which can be called from the non-main thread.
  private native long initGraph0(String capDevName, int capDevNum, int deviceCaps, String dshowTvType, String tuningModel, String CountryName ) throws EncodingException;
  private native void setEncodingProperties0(long ptr, String name, java.util.Map encodeProps) throws EncodingException;
  private native void setCircularFileSize0(long ptr, long fileSize);
  private native void setupEncodingGraph0(long ptr, String encodeFilename) throws EncodingException;
  private native void startEncoding0(long ptr) throws EncodingException;
  private native void switchOutputFile0(long ptr, String newFilename) throws EncodingException;
  private native synchronized void stopEncoding0(long ptr);
  private native synchronized void teardownGraph0(long ptr);
  private native void switchToConnector0(long ptr, int crossType, int crossIndex, String tuningMode,
      int countryCode, int videoFormatCode) throws EncodingException;
  private native void tuneToChannel0(long ptr, String num, int streamFormat);
  private native boolean autoTuneChannel0(long ptr, String num, int streamFormat);
  private native String scanBDAChannel0(long ptr, String num, String country, int streamFormat);
  private native synchronized String getChannel0(long ptr, int chanType);
  private native int getSignalStrength0(long ptr);

  private native int isCaptureDeviceValid0(String capDevName, int capDevNum);
  private native int[] getCrossbarConnections0(String capDevName, int capDevNum);
  private native int getDeviceCaps0(String capDevName, int capDevNum);

  private native int[] updateColors0(long ptr, int brightness, int contrast, int hue, int saturation, int sharpness);
  private native synchronized long getRecordedBytes0(long ptr);

  public static native String[] getDevicesInCategory0(String categoryGuid);
  public static native String[] getDevicesInCategoryS0(String[] categoryGuid);

  private native synchronized void setAudioCaptureSource0(long ptr, String filterName, int filterNum, int SrcIndex );
  private native synchronized void setAudioProcessor0(long ptr, String filterName);
  private native synchronized void setVideoProcessor0(long ptr, String filterName);

  public static native String[] getAudioInputPaths0();

  private native synchronized long getNativeVideoPreviewConfigHandle0(long ptr);

  private native String getBroadcastStandard0(long ptr);

  public String getDeviceClass()
  {
    return "DirectShow";
  }
  private long pHandle;
  private MPEG2EncodingParams encodeParams;

  private int loadedCaptureFeatureBits;

  private boolean inDataScanMode;
  private java.util.Set requestedDataScanCDIs = new java.util.HashSet();
  private boolean graphRunning;
  private String lastTuningMode;
  private boolean graphReload;
}

