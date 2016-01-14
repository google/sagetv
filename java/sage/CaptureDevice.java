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

import java.util.Comparator;

public abstract class CaptureDevice
{
  static final String ENCODERS = "encoders";
  static final String ENCODER_MERIT = "encoder_merit";
  static final String CAPTURE_CONFIG = "capture_config";
  static final String VIDEO_ENCODING_PARAMS = "video_encoding_params";
  static final String DEFAULT_DEVICE_QUALITY = "default_device_quality";
  static final String LAST_CROSS_TYPE = "last_cross_type";
  static final String LAST_CROSS_INDEX = "last_cross_index";
  static final String VIDEO_CAPTURE_DEVICE_NAME = "video_capture_device_name";
  static final String VIDEO_CAPTURE_DEVICE_NUM = "video_capture_device_num";
  static final String AUDIO_CAPTURE_DEVICE_NAME = "audio_capture_device_name";
  static final String AUDIO_CAPTURE_DEVICE_NUM = "audio_capture_device_num";
  static final String AUDIO_CAPTURE_DEVICE_INDEX = "audio_capture_device_index";
  static final String AUDIO_PROCESSOR = "audio_processor";
  static final String VIDEO_PROCESSOR = "video_processor";
  static final String LIVE_AUDIO_INPUT = "live_audio_input";
  static final String DEVICE_CLASS = "device_class";
  public static final int SM2210_ENCODER_MASK = 0x0001;
  public static final int PYTHON2_ENCODER_MASK = 0x0002;
  public static final int VBDVCR_ENCODER_MASK = 0x0004;
  public static final int MICRONAS_AUDIO_MASK = 0x0008;
  public static final int HCW_CAPTURE_MASK = 0x0010;
  public static final int BLACKBIRD_CAPTURE_MASK = 0x0020;
  public static final int HDPVR_ENCODER_MASK = 0x100000;
  public static final int BDA_QAM_TUNING_MASK =   0x200000;
  public static final int BDA_DVBT_TUNING_MASK = 0x0400000;
  public static final int BDA_DVBS_TUNING_MASK = 0x0800000;
  public static final int BDA_DVBC_TUNING_MASK = 0x1000000;
  public static final int BDA_ATSC_TUNING_MASK = 0x2000000;

  public static String getCapDevNameForPrefs(String encoderPref)
  {
    String captureDeviceName = Sage.get(encoderPref + VIDEO_CAPTURE_DEVICE_NAME, "");
    int captureDeviceNum = Sage.getInt(encoderPref + VIDEO_CAPTURE_DEVICE_NUM, 0);
    String host = Sage.get(encoderPref + NetworkCaptureDevice.ENCODING_HOST, "");
    return host.length() == 0 ? getComplexName(captureDeviceName, captureDeviceNum) :
      Sage.rez("Device_On_Host", new Object[] { getComplexName(captureDeviceName, captureDeviceNum), host });

  }
  public static boolean isDeviceAlreadyUsed(CaptureDevice[] usedDevices, String capDevName, int capDevNum)
  {
    for (int i = 0; i < usedDevices.length; i++)
      if (usedDevices[i].isSameCaptureDevice(capDevName, capDevNum))
        return true;
    return false;
  }
  public static String getComplexName(String capDevName, int capDevNum)
  {
    return capDevName + (capDevNum == 0 ? "" : (" #" + (capDevNum + 1)));
  }

  public abstract String getDeviceClass();

  public CaptureDevice()
  {
    srcConfigs = new java.util.ArrayList<CaptureDeviceInput>();
  }
  public CaptureDevice(int inID)
  {
    this();
    id = inID;
    prefs = MMC.MMC_KEY + "/" + ENCODERS + "/" + id + "/";
    loadPrefs();
  }
  void loadPrefs()
  {
    currQuality = MMC.getInstance().cleanQualityName(Sage.get(prefs + VIDEO_ENCODING_PARAMS, Sage.rez("Great")));
    if (currQuality.trim().length() == 0)
      currQuality = Sage.rez("Great");
    defaultQuality = MMC.getInstance().cleanQualityName(Sage.get(prefs + DEFAULT_DEVICE_QUALITY, ""));
    liveAudioInput = Sage.get(prefs + LIVE_AUDIO_INPUT, "");
    lastCrossType = Sage.getInt(prefs + LAST_CROSS_TYPE, 1);
    lastCrossIndex = Sage.getInt(prefs + LAST_CROSS_INDEX, 0);
    encoderMerit = Sage.getInt(prefs + ENCODER_MERIT, 0);
    neverStopEncoding = Sage.getBoolean(prefs + "never_stop_encoding", false);
    captureDeviceName = Sage.get(prefs + VIDEO_CAPTURE_DEVICE_NAME, "");
    captureDeviceNum = Sage.getInt(prefs + VIDEO_CAPTURE_DEVICE_NUM, 0);
    captureFeatureBits = Sage.getInt(prefs + CAPTURE_CONFIG, MMC.UNKNOWN_CAPTURE_CONFIG);
    boolean loadAllInputs = srcConfigs.isEmpty();
    String[] sourceConfigs = Sage.childrenNames(prefs);
    for (int i = 0; i < sourceConfigs.length; i++)
    {
      try
      {
        int crossbarType = Integer.parseInt(sourceConfigs[i]);
        String[] crossIndexes = Sage.childrenNames(prefs + sourceConfigs[i] + '/');
        for (int j = 0; j < crossIndexes.length; j++)
        {
          int crossIdx = Integer.parseInt(crossIndexes[j]);
          // Check if this input already exists...its possible it is new and this is a property update from the server for an RF channel addition
          boolean loadThisInput = loadAllInputs || getInput(crossbarType, crossIdx) == null;
          if (loadThisInput)
          {
            CaptureDeviceInput newConfig = new CaptureDeviceInput(this, prefs, crossbarType,
                crossIdx);
            newConfig.loadPrefs();
            srcConfigs.add(newConfig);
          }
        }
      }
      catch (NumberFormatException e){}
    }
  }

  public String getName()
  {
    if (myComplexName == null)
      myComplexName = getComplexName(captureDeviceName, captureDeviceNum);
    return myComplexName;
  }

  public String getCaptureDeviceName() { return captureDeviceName; }
  public int getCaptureDeviceNum() { return captureDeviceNum; }

  void createID()
  {
    id = getName().hashCode();
    prefs = MMC.MMC_KEY + "/" + ENCODERS + "/" + id + "/";
  }
  void writePrefs()
  {
    Sage.put(prefs + VIDEO_ENCODING_PARAMS, currQuality);
    Sage.put(prefs + VIDEO_CAPTURE_DEVICE_NAME, captureDeviceName);
    Sage.putInt(prefs + VIDEO_CAPTURE_DEVICE_NUM, captureDeviceNum);
    Sage.put(prefs + AUDIO_CAPTURE_DEVICE_NAME, getAuxCaptureDevice()); // to init the prop in the file before use
    Sage.putInt(prefs + AUDIO_CAPTURE_DEVICE_NUM, getAuxCaptureDeviceNum()); // to init the prop in the file before use
    Sage.putInt(prefs + AUDIO_CAPTURE_DEVICE_INDEX, getAuxCaptureDeviceIndex()); // to init the prop in the file before use
    Sage.put(prefs + VIDEO_PROCESSOR, getVideoProcessor()); // to init the prop in the file before use
    Sage.put(prefs + AUDIO_PROCESSOR, getAudioProcessor()); // to init the prop in the file before use
    Sage.putInt(prefs + CAPTURE_CONFIG, captureFeatureBits);
    for (int i = 0; i < srcConfigs.size(); i++)
      srcConfigs.get(i).writePrefs();
    Sage.putInt(prefs + LAST_CROSS_TYPE, lastCrossType);
    Sage.putInt(prefs + LAST_CROSS_INDEX, lastCrossIndex);
    Sage.putInt(prefs + ENCODER_MERIT, encoderMerit);
    Sage.put(prefs + LIVE_AUDIO_INPUT, liveAudioInput);
    Sage.put(prefs + DEFAULT_DEVICE_QUALITY, defaultQuality);
    Sage.put(prefs + DEVICE_CLASS, getDeviceClass());
  }
  void clearPrefs()
  {
    Sage.removeNode(prefs);
  }
  public CaptureDeviceInput getDefaultInput()
  {
    CaptureDeviceInput lastCDI = null;
    CaptureDeviceInput configedCDI = null;
    for (int i = 0; i < srcConfigs.size(); i++)
    {
      CaptureDeviceInput currConfig = srcConfigs.get(i);
      // Don't default to radio
      if (currConfig.getType() == CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX)
        continue;
      if (currConfig.getType() == lastCrossType && currConfig.getIndex() == lastCrossIndex)
      {
        if (currConfig.getProviderID() != 0)
          return currConfig;
        lastCDI = currConfig;
      }
      else if (currConfig.getProviderID() != 0)
        configedCDI = currConfig;
    }
    if (configedCDI != null)
      return configedCDI;
    else if (lastCDI != null)
      return lastCDI;
    // do the default
    if (!srcConfigs.isEmpty())
    {
      if (srcConfigs.size() > 1 && srcConfigs.get(0).getType() ==
          CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX)
        return srcConfigs.get(1);
      else
        return srcConfigs.get(0);
    }
    else
      return null;
  }

  public final CaptureDeviceInput getActiveInput() { return activeSource; }

  public CaptureDeviceInput activateInput(CaptureDeviceInput activateMe) throws EncodingException
  {
    if (activeSource == activateMe) return activeSource;
    activeSource = activateMe;
    if (activeSource != null)
    {
      Sage.putInt(prefs + LAST_CROSS_TYPE, lastCrossType = activateMe.getType());
      Sage.putInt(prefs + LAST_CROSS_INDEX, lastCrossIndex = activateMe.getIndex());
    }
    return activeSource;
  }

  void ensureInputExists(int crossType, int crossIdx)
  {
    for (int i = 0; i < srcConfigs.size(); i++)
    {
      CaptureDeviceInput currConfig = srcConfigs.get(i);
      if (currConfig.getType() == crossType && currConfig.getIndex() == crossIdx)
      {
        return;
      }
    }
    CaptureDeviceInput newInput = new CaptureDeviceInput(this, prefs, crossType, crossIdx);
    newInput.writePrefs();
    srcConfigs.add(newInput);
    NetworkClient.distributeRecursivePropertyChange(prefs);
  }

  public final CaptureDeviceInput getInput(int crossType, int crossIdx)
  {
    for (int i = 0; i < srcConfigs.size(); i++)
    {
      CaptureDeviceInput currConfig = srcConfigs.get(i);
      if (currConfig.getType() == crossType && currConfig.getIndex() == crossIdx)
      {
        return currConfig;
      }
    }
    return null;
  }

  public final boolean hasConfiguredInput()
  {
    for (int i = 0; i < srcConfigs.size(); i++)
      if (srcConfigs.get(i).getProviderID() != 0)
        return true;
    return false;
  }

  public final CaptureDeviceInput[] getInputs()
  {
    return (CaptureDeviceInput[]) srcConfigs.toArray(new CaptureDeviceInput[0]);
  }

  public final String[] getInputNames()
  {
    String[] rv = new String[srcConfigs.size()];
    for (int i = 0; i < rv.length; i++)
      rv[i] = srcConfigs.get(i).toString();
    return rv;
  }

  public final CaptureDeviceInput getInputForProvider(long providerID)
  {
    if (providerID == 0) return null;
    for (int i = 0; i < srcConfigs.size(); i++)
      if (srcConfigs.get(i).getProviderID() == providerID)
        return srcConfigs.get(i);
    return null;
  }

  // Anything besides zero implies circular file buffering
  public void setRecordBufferSize(long buffSize)
  {
    recordBufferSize = buffSize;
  }
  public long getRecordBufferSize() { return recordBufferSize; }
  public long getCurrentlyRecordingBufferSize() { return currentlyRecordingBufferSize; }

  public java.util.Map getEncodingOptions(String qualityProfile)
  {
    return MPEG2EncodingParams.getQualityOptions(MMC.cleanQualityName(qualityProfile));
  }

  public boolean setEncodingOptions(String qualityProfile, java.util.Map optionsMap) { return false; }

  public boolean removeEncodingQuality(String qualityProfile) { return false; }

  public void setEncodingQuality(String qualityProfile)
  {
    currQuality = MMC.cleanQualityName(qualityProfile);
    if (currQuality.trim().length() == 0)
      currQuality = Sage.rez("Great");
  }

  public String getEncodingQuality()
  {
    return currQuality;
  }

  public void applyQuality() throws EncodingException
  {
  }

  public String[] getEncodingQualities()
  {

    if (isCaptureFeatureSupported(SM2210_ENCODER_MASK))
    {
      String paramDir = userParamDir;

      if (paramDir == null) paramDir = new java.io.File(System.getProperty("user.dir"), "Parameters").getAbsolutePath();

      java.util.ArrayList currOptions = new java.util.ArrayList();
      String[] paramFiles = new java.io.File(paramDir).list();
      if (paramFiles == null || paramFiles.length == 0)
        return Pooler.EMPTY_STRING_ARRAY;
      for (int i = 0; i < paramFiles.length; i++)
      {
        if (paramFiles[i].toLowerCase().endsWith(".par"))
          currOptions.add(paramFiles[i].substring(0, paramFiles[i].length() - 4));
      }

      paramFiles = (String[]) currOptions.toArray(Pooler.EMPTY_STRING_ARRAY);
      java.util.Arrays.sort(paramFiles);
      return paramFiles;
    }
    else if (isCaptureFeatureSupported(PYTHON2_ENCODER_MASK) || isCaptureFeatureSupported(VBDVCR_ENCODER_MASK) ||
        isCaptureFeatureSupported(BLACKBIRD_CAPTURE_MASK))
    {
      return MPEG2EncodingParams.getQualityNames();
    }
    else if (isCaptureFeatureSupported(MMC.RAW_AV_CAPTURE_MASK) ||
        isCaptureFeatureSupported(MMC.RAW_VIDEO_CAPTURE_MASK))
    {
      // MPEG2 & DivX software encoding configuration
      return MPEG2EncodingParams.getSWEncodeQualityNames();
    }
    else if (isCaptureFeatureSupported(HDPVR_ENCODER_MASK))
    {
      return MPEG2EncodingParams.getHDPVREncodeQualityNames();
    }
    else if (isCaptureFeatureSupported(MMC.MPEG_PURE_CAPTURE_MASK))
    {
      return new String[] { Sage.rez("MPEG2_Transport_Stream") };
    }
    else // this is a much better default to use
      return MPEG2EncodingParams.getQualityNames();
    //      return Pooler.EMPTY_STRING_ARRAY;

  }

  public boolean doesDeviceHaveDefaultQuality() { return defaultQuality != null && defaultQuality.length() > 0; }
  public String getDefaultQuality() { return defaultQuality; }
  public void setDefaultQuality(String x)
  {
    Sage.put(prefs + DEFAULT_DEVICE_QUALITY, defaultQuality = x);
  }

  public boolean isFunctioning() { return true; }

  public boolean isSameCaptureDevice(String testCapDevName, int testCapDevNum)
  {
    return (captureDeviceName.equals(testCapDevName) && testCapDevNum == captureDeviceNum);
  }

  public void applyFeatureMask(int featureMask)
  {
    captureFeatureBits &= featureMask;
  }

  public int getCaptureFeatureBits() { return captureFeatureBits; }
  public boolean isCaptureFeatureSupported(int testMe) { return (testMe & captureFeatureBits) == testMe; }

  public boolean equals(Object o)
  {
    return (o instanceof CaptureDevice) && ((CaptureDevice) o).getName().equals(getName());
  }
  public int hashCode()
  {
    return getName().hashCode();
  }
  public String toString() { return getName(); }

  public String getChannel()
  {
    return (activeSource == null) ? "" : activeSource.getChannel(); // we use last channel because its network distributed
  }

  // Whether or not this device can record. This is true if it has hardware encoding or if it is configured for
  // software encoding.
  public boolean canEncode()
  {
    return isHWEncoder() || Sage.getBoolean("mmc/enable_software_encoding", true);
    /*hasAudioProcessor() && hasVideoProcessor())*/
  }

  public boolean isHWEncoder()
  {
    return (captureFeatureBits & (MMC.MPEG_AV_CAPTURE_MASK | MMC.MPEG_PURE_CAPTURE_MASK | MMC.MPEG_VIDEO_ONLY_CAPTURE_MASK |
        MMC.MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK | MMC.BDA_VIDEO_CAPTURE_MASK)) != 0;
  }

  public abstract void loadDevice() throws EncodingException;
  public abstract boolean isLoaded();
  public abstract void startEncoding(CaptureDeviceInput cdi, String encodeFile, String channel) throws EncodingException;
  public abstract void switchEncoding(String switchFile, String channel) throws EncodingException;
  public abstract void stopEncoding();
  public abstract void freeDevice();
  public abstract long getRecordedBytes();

  public final void tuneToChannel(String tuneString)
  {
    if (activeSource == null) return;
    doTuneChannel(tuneString, Sage.getBoolean("always_autotune", Sage.MAC_OS_X ? false : true) && activeSource.getType() !=
        CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX);
    activeSource.setLastChannel(tuneString);
    activeSource.writePrefs();
  }
  public final boolean autoTuneChannel(String tuneString)
  {
    if (activeSource == null) return false;
    boolean rv = doTuneChannel(tuneString, true);
    activeSource.setLastChannel(tuneString);
    activeSource.writePrefs();
    return rv;
  }
  public final boolean ScanChannel(String tuneString)
  {
    if (activeSource == null) return false;
    boolean rv = doScanChannel(tuneString);
    activeSource.setLastChannel(tuneString);
    activeSource.writePrefs();
    return rv;
  }

  public final String ScanChannelInfo(String tuneString)
  {
    if (activeSource == null) return "";
    String rv = doScanChannelInfo(tuneString);
    return rv;
  }

  protected abstract boolean doTuneChannel(String tuneString, boolean autotune);
  protected abstract boolean doScanChannel(String tuneString);
  protected abstract String doScanChannelInfo(String tuneString);
  public int getSignalStrength()
  {
    return 100;
    /*if (activeSource == null) return -1;
    if (autoTuneChannel(activeSource.getChannel()))
      return 100;
    else
      return 0;*/
  }

  public boolean isRecording() { return recFilename != null; }
  public String getRecordFilename() { return recFilename; }

  public long getRecordStart() { return recStart; }

  public int[] updateColors()
  {
    return new int[5];
  }

  public boolean isDynamicFormatEncoder()
  {
    return isNetworkEncoder() || isCaptureFeatureSupported(HDPVR_ENCODER_MASK);
  }

  public sage.media.format.ContainerFormat getEncoderMediaFormat()
  {
    sage.media.format.ContainerFormat rv = null;
    if (currQuality != null)
    {
      MPEG2EncodingParams params = MPEG2EncodingParams.getQuality(currQuality);
      if (params != null)
        rv = params.getContainerFormatObj();
    }
    if (rv == null)
      rv = MPEG2EncodingParams.getQuality(Sage.rez("Great")).getContainerFormatObj();
    boolean timeshiftMpeg4 = MMC.getInstance().useMpeg4InMpeg2();
    // We don't suppor timeshifting on DivX/MPEG4 software encoding
    if (!isHWEncoder())
      timeshiftMpeg4 = false;
    if (!timeshiftMpeg4 && sage.media.format.MediaFormat.MPEG4X.equals(rv.getPrimaryVideoFormat()))
    {
      sage.media.format.ContainerFormat newRV = new sage.media.format.ContainerFormat();
      newRV.setFormatName(sage.media.format.MediaFormat.AVI);
      newRV.setBitrate(rv.getBitrate());
      newRV.setStreamFormats(rv.getStreamFormats());
      rv = newRV;
    }
    if (isDynamicFormatEncoder())
    {
      // We can't be sure of the format so don't completely specify it.
      sage.media.format.ContainerFormat newRV = new sage.media.format.ContainerFormat();
      newRV.setFormatName(rv.getFormatName());
      rv = newRV;
    }
    return rv;
  }

  public int getCurrQualityStreamType()
  {
    if (currQuality != null)
    {
      java.util.Map qualOpts = getEncodingOptions(currQuality);
      if (qualOpts != null)
      {
        String outputTypeStr = (String) qualOpts.get("outputstreamtype");
        if (outputTypeStr != null)
        {
          try
          {
            return Integer.parseInt(outputTypeStr);
          }
          catch (Exception e){}
        }
      }
    }
    return MPEG2EncodingParams.STREAMOUTPUT_PROGRAM;
  }

  public int getMerit() { return encoderMerit; }

  public void setMerit(int newMerit)
  {
    if ( newMerit < 0 )
      newMerit = 0;

    if ( encoderMerit != newMerit )
    {
      Sage.putInt(prefs + ENCODER_MERIT, encoderMerit = newMerit);
      Scheduler.getInstance().kick(true);
    }

  }

  public int getMaxChannel(CaptureDeviceInput myInput)
  {
    if (myInput != null)
    {
      if ( ( captureFeatureBits & MMC.BDA_VIDEO_CAPTURE_MASK ) != 0 )
      {
        if ( "DVB-S".equalsIgnoreCase( myInput.getBroadcastStandard() ) )
          return 200;
        else
          if ( "DVB-C".equalsIgnoreCase( myInput.getBroadcastStandard() ) )
            return 100;
          else
            if ( "DVB-T".equalsIgnoreCase(  myInput.getBroadcastStandard() ) )
              return 100;
            else
              if ( "Cable".equalsIgnoreCase( myInput.getTuningMode() ) || "HRC".equalsIgnoreCase( myInput.getTuningMode() ) )
                return 200;
              else
                if ( "Air".equalsIgnoreCase( myInput.getTuningMode() )  )
                  return 69;
                else
                  return 100;
      }
      if (myInput.normalRF() || myInput.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
        return myInput.isCableTV() ? TVTuningFrequencies.getCableChannelMax(MMC.getInstance().getCountryCode()) :
          TVTuningFrequencies.getBroadcastChannelMax(MMC.getInstance().getCountryCode());
        if (DirecTVSerialControl.DIRECTV_SERIAL_CONTROL.equals(myInput.getTuningPlugin()))
          return 9999;
        if (myInput.getDevice().length() > 0)
        {
          SFIRTuner tuney = ExternalTuningManager.getIRTunerPlugin(myInput.getTuningPlugin(),
              myInput.getTuningPluginPort());
          if (tuney != null)
          {
            tuney.getRemoteInfo(myInput.getDevice());
            return tuney.getMaxChannel();
          }
        }

        // This means it's not a regular RF input but there's no plugin configured for it; or no device.
        // We want to return 1 for inputs w/out EPG configuration and 9999 for those channels with
        // EPG data since we can't be sure what the max # should actually be.
        return (myInput.getProviderID() > 0) ? 9999 : 1;
    }
    return 1;
  }

  public int getMinChannel(CaptureDeviceInput myInput)
  {
    if (myInput != null)
    {
      if (myInput.normalRF() || myInput.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
        return myInput.isCableTV() ? TVTuningFrequencies.getCableChannelMin(MMC.getInstance().getCountryCode()) :
          TVTuningFrequencies.getBroadcastChannelMin(MMC.getInstance().getCountryCode());
    }
    return 1;
  }

  public boolean usesFixedBroadcastStd()
  {
    return isCaptureFeatureSupported(CaptureDevice.BDA_DVBC_TUNING_MASK) ||
        isCaptureFeatureSupported(CaptureDevice.BDA_DVBS_TUNING_MASK) || isCaptureFeatureSupported(CaptureDevice.BDA_DVBT_TUNING_MASK);
  }

  public boolean shouldNotStopDevice() { return neverStopEncoding; }

  public long getRecordedDuration() { return Sage.time() - recStart; }

  public String getLiveAudioInput() { return liveAudioInput; }
  public void setLiveAudioInput(String x) { Sage.put(prefs + LIVE_AUDIO_INPUT, liveAudioInput = x); }

  // Fast mux switch has been redefined now to mean only switching files when there is NOT a channel change.
  // This switch is also seamless; so that the two files from the transition can be concatenated together
  // and played back as if they were one. But this will only happen during live TV playback.
  // All encoders do not need to support this feature.
  public boolean supportsFastMuxSwitch()
  {
    // By default we don't support this at all; it's capture-device specific
    return false;

    /*
    // The HD-PVR will stop capture when it blasts; so don't allow fast switching w/ that device
    if (isCaptureFeatureSupported(HDPVR_ENCODER_MASK))
      return false;
    // We can't fast mux switch for AVI formats or on MPEG1. It also doesn't work on SVCD formats either because
    // they don't write out the system header frequent enough.
    sage.media.format.MediaFormat form = getEncoderMediaFormat();
    if (form == null)
      return false;
    String containerFormat = form.getFormatName();
    // We can only fast switch MPEG2 Program Stream, so skip anything else!
    if (!sage.media.format.MediaFormat.MPEG2_PS.equals(containerFormat))
      return false;
    int st = getCurrQualityStreamType();
    if (st == MPEG2EncodingParams.STREAMOUTPUT_VCD)
      return false;
    // Apparently on the Plextor devices CVD doesn't work for fast mux switch for some reason...
    if ((captureFeatureBits & MMC.MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK) != 0 && st == MPEG2EncodingParams.STREAMOUTPUT_PROGRAM)
    {
      return false;
    }

        if ( ( captureFeatureBits & MMC.RAW_AV_CAPTURE_MASK) != 0 ) //ZQ. soft encoder can't be FastMux switched
        {
             return false;
        }

    if (activeSource != null && !activeSource.supportsFastMuxSwitch())
      return false;

    return true;*/
  }

  public boolean hasVideoProcessor()
  {
    return Sage.get(prefs + VIDEO_PROCESSOR, "").length() > 0;
  }
  public String getVideoProcessor()
  {
    return Sage.get(prefs + VIDEO_PROCESSOR, "");
  }

  public boolean hasAudioProcessor()
  {
    return Sage.get(prefs + AUDIO_PROCESSOR, "").length() > 0;
  }
  public String getAudioProcessor()
  {
    return Sage.get(prefs + AUDIO_PROCESSOR, "");
  }

  public boolean hasAuxCaptureDevice()
  {
    return Sage.get(prefs + AUDIO_CAPTURE_DEVICE_NAME, "").length() > 0;
  }
  public String getAuxCaptureDevice()
  {
    return Sage.get(prefs + AUDIO_CAPTURE_DEVICE_NAME, "");
  }
  public int getAuxCaptureDeviceNum()
  {
    return Sage.getInt(prefs + AUDIO_CAPTURE_DEVICE_NUM, 0);
  }

  public int getAuxCaptureDeviceIndex()
  {
    return Sage.getInt(prefs + AUDIO_CAPTURE_DEVICE_INDEX, -1);
  }

  public void setAuxCaptureDeviceName(String s)
  {
    Sage.put(prefs + AUDIO_CAPTURE_DEVICE_NAME, s);
  }

  public String getForcedVideoStoragePrefix()
  {
    return Sage.get(prefs + "forced_video_storage_path_prefix", "");
  }

  // Returns a value between 0 and 100, essentially the percent of system resources required by this
  public int getResourceUsage()
  {
    return 0;
  }

  // Returns an abstract native pointer to the object that can be used to configure video preview
  public long getNativeVideoPreviewConfigHandle()
  {
    return 0;
  }

  public boolean doesDataScanning()
  {
    return false;
  }
  public boolean wantsDataScanning(CaptureDeviceInput cdi)
  {
    return false;
  }
  // Enable & Disable data scanning should ONLY be called by the Seeker's thread (just like the start/stop/switch encoding methods)
  public void enableDataScanning(CaptureDeviceInput cdi) throws EncodingException
  {
  }
  public void disableDataScanning()
  {
  }
  public boolean isDataScanning()
  {
    return false;
  }
  // returns true if this is a new request
  public boolean requestDataScan(CaptureDeviceInput cdi)
  {
    return false;
  }
  public void cancelDataScanRequest(CaptureDeviceInput cdi)
  {
  }

  public boolean isNetworkEncoder()
  {
    return false;
  }

  public long getPostTuningDelay()
  {
    return Sage.getLong(prefs + "delay_to_wait_after_tuning", isCaptureFeatureSupported(HDPVR_ENCODER_MASK) ? 4000 : 0);
  }

  public long getBitrateThresholdForError() {
    return Sage.getLong(prefs + "minimum_bitrate_error_threshold_bps", 0);
  }

  public int getHighestQualityConfiguredInputType()
  {
    int x = -1;
    for (int i = 0; i < srcConfigs.size(); i++)
    {
      CaptureDeviceInput cdi = srcConfigs.get(i);
      if (cdi.getProviderID() != 0 && cdi.getType() != CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX)
        x = Math.max(x, cdi.getType());
    }
    return x;
  }

  //public static final Comparator<CaptureDevice> captureDeviceSorter = new Comparator<CaptureDevice>()
  public static final Comparator captureDeviceSorter = new Comparator()
  {
    public int compare(Object o1, Object o2)
    {
      // Assume objects o1 and o2 refer to either CaptureDevice or DaptureDeviceInput & find the CaptureDevice for each.
      CaptureDevice c1 = PredefinedJEPFunction.getCapDevObj( o1 );
      CaptureDevice c2 = PredefinedJEPFunction.getCapDevObj( o2 );

      // If either of the CaptureDevice objects are invalid, then put the valid object in front of the invalid one; invalid objects are equal.
      if ( (c1 == null) || (c2 == null) )
      {
        if ( (c1 == null) && (c2 == null) )
          return 0;
        else if ( c1 == null )
          return 1;
        else
          return -1;
      }

      // Higher merit value is first.
      int m1 = c1.getMerit();
      int m2 = c2.getMerit();
      if (m1 != m2)
        return m2 - m1;

      // Use the device with the better physical input type
      int x = c2.getHighestQualityConfiguredInputType() - c1.getHighestQualityConfiguredInputType();
      if (x != 0)
        return x;

      // The more encoding options the better, so use that one first.
      int o = c2.getEncodingQualities().length - c1.getEncodingQualities().length;
      if (o != 0)
        return o;

      // If all else is the same, then compare the capture device names.
      return c1.toString().compareTo(c2.toString());
    }
  };

  protected int id;
  protected java.util.ArrayList<CaptureDeviceInput> srcConfigs;

  protected String recFilename;
  protected boolean neverStopEncoding = false;
  protected long recStart;
  protected String prefs;
  protected long recordBufferSize;
  protected long currentlyRecordingBufferSize;
  protected String liveAudioInput = "";

  protected String currQuality = Sage.rez("Great");
  protected CaptureDeviceInput activeSource;
  protected int lastCrossType = 1;
  protected int lastCrossIndex = 0;
  protected int encoderMerit;

  protected int captureFeatureBits;

  protected String captureDeviceName = "";
  protected int captureDeviceNum;

  // legacy quality support
  protected String userParamDir;

  // per-capdev default quality setting
  protected String defaultQuality;

  protected String myComplexName;
}
