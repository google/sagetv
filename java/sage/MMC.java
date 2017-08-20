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

public class MMC
{
  public static final String DISABLE_CAPTURE = "None";
  public static final String MMC_KEY = "mmc";
  public static final int[] VALID_AUDIO_BITRATES = {192, 224, 256, 320, 384};
  private static final String VIDEO_FORMAT_CODE = "video_format_code";
  private static final String AUDIO_CAPTURE_DEVICE_NAME = "audio_capture_device_name";
  private static final String AUDIO_CAPTURE_DEVICE_NUM = "audio_capture_device_num";
  private static final String VIDEO_COMPRESSOR = "video_compressor";
  private static final String FEATURE_MASK_DISABLE = "feature_mask_disable";
  public static final int UNKNOWN_CAPTURE_CONFIG = 0;
  public static final int RAW_AV_CAPTURE_MASK = 0x0100;
  public static final int MPEG_VIDEO_ONLY_CAPTURE_MASK = 0x0200;
  public static final int MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK = 0x0400;
  public static final int MPEG_AV_CAPTURE_MASK = 0x0800;
  public static final int CAPTURE_STREAM_CAPS_MASK = 0x2F00;
  public static final int LIVE_PREVIEW_MASK = 0x1000;
  public static final int MPEG_PURE_CAPTURE_MASK = 0x2000; // for DTV/HDTV/DVB
  public static final int RAW_VIDEO_CAPTURE_MASK = 0x4000;
  public static final int BDA_VIDEO_CAPTURE_MASK = 0x8000;
  /*	public static final String PVR250_FILTER_NAME = "Hauppauge WinTV PVR PCI II Capture";
	public static final String PYTHON2_FILTER_NAME = "Python 2 Capture";
	public static final String VBDVCR_FILTER_NAME = "VBDVCR Capture";
	public static final String JANUS_FILTER_NAME = "Janus Capture";
   */
  public static final String COUNTRY_CODE = "country_code";
  public static final String COUNTRY = "country";
  public static final String ALWAYS_TUNE_CHANNEL = "always_tune_channel";
  public static final String LAST_ENCODER_NAME = "last_encoder_name";

  private static class MMCHolder
  {
    public static final MMC instance = new MMC();
  }

  public static MMC getInstance()
  {
    return MMCHolder.instance;
  }

  private static final Object instanceLock = new Object();

  public static MMC prime()
  {
    MMC instance = getInstance();
    synchronized (instanceLock)
    {
      instance.init();
    }
    return instance;
  }
 
  private MMC()
  {
    prefs = MMC_KEY + '/';

    lastEncoderName = Sage.get(prefs + LAST_ENCODER_NAME, "");
    featureMaskDisable = Sage.getInt(prefs + FEATURE_MASK_DISABLE, 0);

    capDevMgrs = new java.util.ArrayList();
    globalEncoderMap = new java.util.HashMap();
  }

  private void init()
  {
    alive = true;

    for (int i = 0; i < capDevMgrs.size(); i++)
    {
      CaptureDeviceManager mgr = (CaptureDeviceManager) capDevMgrs.get(i);
      if (Sage.DBG) System.out.println("MMC calling detectCaptureDevices on " + mgr);
      mgr.detectCaptureDevices((CaptureDevice[]) globalEncoderMap.values().toArray(new CaptureDevice[0]));
      CaptureDevice[] newDevs = mgr.getCaptureDevices();
      if (Sage.DBG) System.out.println("devices detected=" + java.util.Arrays.asList(newDevs));
      for (int j = 0; j < newDevs.length; j++)
        globalEncoderMap.put(newDevs[j].getName(), newDevs[j]);

      if (Sage.DBG) System.out.println("EncoderMap=" + globalEncoderMap);
    }
  }

  // This can be used to load any new capture devices that were hotplugged after SageTV has started
  public void redetectCaptureDevices()
  {
    if (Sage.DBG) System.out.println("MMC is re-doing the capture device detection!");
    for (int i = 0; i < capDevMgrs.size(); i++)
    {
      CaptureDeviceManager mgr = (CaptureDeviceManager) capDevMgrs.get(i);
      if (Sage.DBG) System.out.println("MMC calling detectCaptureDevices on " + mgr);
      mgr.detectCaptureDevices((CaptureDevice[]) globalEncoderMap.values().toArray(new CaptureDevice[0]));
      CaptureDevice[] newDevs = mgr.getCaptureDevices();
      if (Sage.DBG) System.out.println("devices detected=" + java.util.Arrays.asList(newDevs));
     
      updateCaptureDeviceObjects(newDevs);

      if (Sage.DBG) System.out.println("EncoderMap=" + globalEncoderMap);
    }
    NetworkClient.distributeRecursivePropertyChange("mmc/encoders");
    SeekerSelector.getInstance().kick();
    SchedulerSelector.getInstance().kick(true);
  }
  
 // This is an overload of redetectCaptureDevices.  It is meant to only redetect devices for
 // one CaptureDeviceManager.  For instance discover NetworkEncoder devices
 public void redetectCaptureDevices(CaptureDeviceManager mgr)
 {
   if (Sage.DBG) System.out.println("MMC is re-doing the capture device detection on " + mgr);
   mgr.detectCaptureDevices((CaptureDevice[]) globalEncoderMap.values().toArray(new CaptureDevice[0]));
   CaptureDevice[] newDevs = mgr.getCaptureDevices();

   if (Sage.DBG) System.out.println("devices detected=" + java.util.Arrays.asList(newDevs));
   updateCaptureDeviceObjects(newDevs);

   if (Sage.DBG) System.out.println("EncoderMap=" + globalEncoderMap);
   NetworkClient.distributeRecursivePropertyChange("mmc/encoders");
   SeekerSelector.getInstance().kick();
   SchedulerSelector.getInstance().kick(true);
 }

  // If we're changing the actual CapDev object than we need to rebuild this map. This occurs
  // on client resync of properties
  void updateCaptureDeviceObjects(CaptureDevice[] newDevs)
  {
    for (int j = 0; j < newDevs.length; j++)
      globalEncoderMap.put(newDevs[j].getName(), newDevs[j]);
  }

  public void addCaptureDeviceManager(CaptureDeviceManager mgr)
  {
    capDevMgrs.add(mgr);
  }

  public CaptureDevice[] getCaptureDevices()
  {
    return (CaptureDevice[]) globalEncoderMap.values().toArray(new CaptureDevice[0]);
  }

  public CaptureDevice getPreferredCaptureDevice()
  {
    CaptureDevice cd = (CaptureDevice) globalEncoderMap.get(lastEncoderName);
    if (cd != null && cd.hasConfiguredInput()) return cd;
    if (!globalEncoderMap.isEmpty() && !DISABLE_CAPTURE.equals(lastEncoderName))
    {
      // Check for the best configured first, then the best of any
      int bestCaps = 0;
      int bestConfigedCaps = 0;
      CaptureDevice configCD = null;
      java.util.Iterator walker = globalEncoderMap.values().iterator();
      while (walker.hasNext())
      {
        CaptureDevice currEnc = (CaptureDevice) walker.next();
        if ((currEnc.getCaptureFeatureBits() & CAPTURE_STREAM_CAPS_MASK) > bestCaps)
        {
          cd = currEnc;
          bestCaps = (currEnc.getCaptureFeatureBits() & CAPTURE_STREAM_CAPS_MASK);
        }
        if (currEnc.hasConfiguredInput() &&
            (currEnc.getCaptureFeatureBits() & CAPTURE_STREAM_CAPS_MASK) > bestConfigedCaps)
        {
          configCD = currEnc;
          bestConfigedCaps = (currEnc.getCaptureFeatureBits() & CAPTURE_STREAM_CAPS_MASK);
        }
      }
      if (configCD != null)
        return configCD;
      else
        return cd;
    }
    else
      return null;
  }

  /*	public void displayError(EncodingException error, boolean showAsync)
	{
		System.out.println("MMC processing error:" + error);
		if (errHandler != null)
		{
			if (showAsync)
			{
				final String currDetails = error.getMessage();
				java.awt.EventQueue.invokeLater(new Runnable()
				{
					public void run()
					{
						errHandler.handleError("MMC", "Capture Device Error", currDetails);
					}
				});
			}
			else
				errHandler.handleError("MMC", "Capture Device Error", myDetails);
		}
	}
   */
  synchronized void goodbye()
  {
    alive = false;
    ExternalTuningManager.goodbye();
    freeAllResources();
  }

  synchronized void freeAllResources()
  {
    for (int i = 0; i < capDevMgrs.size(); i++)
      ((CaptureDeviceManager) capDevMgrs.get(i)).freeResources();
  }

  public CaptureDeviceInput getCaptureDeviceInputNamed(String connName)
  {
    CaptureDeviceInput[] allDesc = getAllInputs();
    for (int i = 0; i < allDesc.length; i++)
      if (allDesc[i].toString().equals(connName))
        return allDesc[i];
    return null;
  }

  public CaptureDevice getCaptureDeviceNamed(String capName)
  {
    return (CaptureDevice) globalEncoderMap.get(capName);
  }

  public CaptureDeviceInput[] getAllInputs()
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
    {
      rv.addAll(java.util.Arrays.asList(((CaptureDevice) walker.next()).getInputs()));
    }
    return (CaptureDeviceInput[]) rv.toArray(new CaptureDeviceInput[0]);
  }

  public CaptureDeviceInput getInputForProvider(long providerID)
  {
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
    {
      CaptureDeviceInput rv = ((CaptureDevice) walker.next()).getInputForProvider(providerID);
      if (rv != null)
        return rv;
    }
    return null;
  }

  public CaptureDeviceInput[] getInputsForProvider(long providerID)
  {
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    java.util.ArrayList rv = Pooler.getPooledArrayList();
    while (walker.hasNext())
    {
      CaptureDeviceInput cdi = ((CaptureDevice) walker.next()).getInputForProvider(providerID);
      if (cdi != null)
        rv.add(cdi);
    }
    CaptureDeviceInput[] rvx = (CaptureDeviceInput[]) rv.toArray(new CaptureDeviceInput[0]);
    Pooler.returnPooledArrayList(rv);
    return rvx;
  }

  public String[] getCaptureDeviceNames()
  {
    return (String[]) globalEncoderMap.keySet().toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public boolean isProviderUsedAndActive(long providerID)
  {
    return (getInputForProvider(providerID) != null);
  }

  public boolean isProviderUsed(long providerID)
  {
    if (getInputForProvider(providerID) != null)
      return true;
    if (Sage.getBoolean("mmc/retain_lineups_for_lost_devices", true))
    {
      // go through all the properties manually and see if there's an encoder using this
      // provider because that encoder might just be offline for now
      String[] encoderKeys = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/');
      for (int i = 0; i < encoderKeys.length; i++)
      {
        String[] sourceConfigs = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/');
        for (int j = 0; j < sourceConfigs.length; j++)
        {
          String[] crossIndexes = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
              sourceConfigs[j] + '/');
          for (int k = 0; k < crossIndexes.length; k++)
          {
            long testProvider = Sage.getLong(prefs + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
                sourceConfigs[j] + '/' + crossIndexes[k] + "/provider_id", 0);
            if (testProvider == providerID)
              return true;
          }
        }
      }
    }
    return false;
  }

  public CaptureDeviceInput[] getConfiguredInputs()
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
    {
      CaptureDevice testEncoder = (CaptureDevice) walker.next();
      for (int i = 0; i < testEncoder.srcConfigs.size(); i++)
      {
        CaptureDeviceInput tempConfig = testEncoder.srcConfigs.get(i);
        if (tempConfig.getProviderID() != 0)
          rv.add(tempConfig);
      }
    }
    return (CaptureDeviceInput[]) rv.toArray(new CaptureDeviceInput[0]);
  }

  public String[] getConfiguredInputNames()
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
    {
      CaptureDevice testEncoder = (CaptureDevice) walker.next();
      for (int i = 0; i < testEncoder.srcConfigs.size(); i++)
      {
        CaptureDeviceInput tempConfig = testEncoder.srcConfigs.get(i);
        if (tempConfig.getProviderID() != 0)
          rv.add(tempConfig.toString());
      }
    }
    return (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public CaptureDevice[] getConfiguredCaptureDevices()
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
    {
      CaptureDevice testEncoder = (CaptureDevice) walker.next();
      for (int i = 0; i < testEncoder.srcConfigs.size(); i++)
      {
        CaptureDeviceInput tempConfig = testEncoder.srcConfigs.get(i);
        if (tempConfig.getProviderID() != 0)
        {
          rv.add(testEncoder);
          break;
        }
      }
    }
    return (CaptureDevice[]) rv.toArray(new CaptureDevice[0]);
  }

  public boolean isRecording(java.io.File recordingFile)
  {
    if (globalEncoderMap == null) return false;
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
    {
      CaptureDevice testEncoder = (CaptureDevice) walker.next();
      String testFilename = testEncoder.getRecordFilename();
      if (testFilename != null && new java.io.File(testFilename).equals(recordingFile))
        return true;
    }
    return false;
  }

  public long getRecordedDuration(java.io.File recordingFile)
  {
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
    {
      CaptureDevice testEncoder = (CaptureDevice) walker.next();
      String testFilename = testEncoder.getRecordFilename();
      if (testFilename != null && new java.io.File(testFilename).equals(recordingFile))
      {
        long rv = testEncoder.getRecordedDuration();
        // Check to make sure the file didn't switch since we're not synced
        if (testFilename.equals(testEncoder.getRecordFilename()))
          return rv;
      }
    }
    return 0;
  }

  public long getRecordedBytes(java.io.File recordingFile)
  {
    // TODO: Will be enabled in a future commit.
    /*if (SeekerSelector.USE_BETA_SEEKER)
    {
      long returnValue = Splitter.getInstance().getBytesStreamed(recordingFile);
      if (returnValue != -1)
        return returnValue;
    }*/

    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
    {
      CaptureDevice testEncoder = (CaptureDevice) walker.next();
      String testFilename = testEncoder.getRecordFilename();
      if (testFilename != null && new java.io.File(testFilename).equals(recordingFile))
      {
        long rv = testEncoder.getRecordedBytes();
        // Check to make sure the file didn't switch since we're not synced
        if (testFilename.equals(testEncoder.getRecordFilename()))
          return rv;
      }
    }
    return -1;
  }

  public long getRecordingCircularFileSize(java.io.File recordingFile)
  {
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
    {
      CaptureDevice testEncoder = (CaptureDevice) walker.next();
      String testFilename = testEncoder.getRecordFilename();
      if (testFilename != null && new java.io.File(testFilename).equals(recordingFile))
      {
        long rv = testEncoder.getCurrentlyRecordingBufferSize();
        // Check to make sure the file didn't switch since we're not synced
        if (testFilename.equals(testEncoder.getRecordFilename()))
          return rv;
      }
    }
    return 0;
  }

  boolean isAlive() { return alive; }

  public boolean encoderFeatureSupported(int capMask)
  {
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
    {
      CaptureDevice testEncoder = (CaptureDevice) walker.next();
      if (!testEncoder.isNetworkEncoder() && testEncoder.isCaptureFeatureSupported(capMask))
        return true;
    }
    return false;
  }

  public String[] getEncoderPropertyRoots()
  {
    String[] rv = new String[globalEncoderMap.size()];
    int i = 0;
    java.util.Iterator walker = globalEncoderMap.values().iterator();
    while (walker.hasNext())
      rv[i++] = ((CaptureDevice) walker.next()).prefs;
    return rv;
  }

  int getFeatureMaskDisable() { return featureMaskDisable; }

  public String[] getAllEncodingQualities()
  {
    java.util.Set quals = new java.util.HashSet();
    CaptureDevice[] capDevs = getCaptureDevices();
    for (int i = 0; i < capDevs.length; i++)
      quals.addAll(java.util.Arrays.asList(capDevs[i].getEncodingQualities()));
    String[] rv = (String[]) quals.toArray(Pooler.EMPTY_STRING_ARRAY);
    java.util.Arrays.sort(rv);
    return rv;
  }

  public CaptureDeviceManager[] getCaptureDeviceManagers()
  {
    return (CaptureDeviceManager[]) capDevMgrs.toArray(new CaptureDeviceManager[0]);
  }

  public static String cleanQualityName(String x)
  {
    int idx = x.lastIndexOf('~');
    if (idx == -1)
      idx = x.lastIndexOf('-');
    if (idx != -1)
    {
      if (x.indexOf("GB", idx) > idx)
        return x.substring(0, idx).trim();
      else
        return x;
    }
    else
      return x;
  }

  public long getQualityOverallBitrate(String quality)
  {
    MPEG2EncodingParams params = MPEG2EncodingParams.getQuality(quality);
    if (params != null && params.overallbitrate != null && params.overallbitrate.intValue() > 0)
    {
      return params.overallbitrate.intValue();
    }
    else if (params != null)
    {
      int totalBitrate = 0;
      if (params.videobitrate != null)
        totalBitrate += params.videobitrate.intValue();
      if (params.audiobitrate != null)
        totalBitrate += params.audiobitrate.intValue()*1000;
      // For stream overhead
      totalBitrate = Math.round(totalBitrate * 1.05f);
      if (totalBitrate > 1000)
        return totalBitrate;
      else
        return 4000000;
    }
    else
      return 4000000;
  }

  public String getQualityFormat(String quality)
  {
    MPEG2EncodingParams params = MPEG2EncodingParams.getQuality(quality);
    if (params != null && params.outputstreamtype != null)
    {
      switch  (params.outputstreamtype.intValue())
      {
        case MPEG2EncodingParams.STREAMOUTPUT_CUSTOM_DIVX:
          return "DivX";
        case MPEG2EncodingParams.STREAMOUTPUT_CUSTOM_MPEG4:
          return "MPEG-4";
        case MPEG2EncodingParams.STREAMOUTPUT_DVD:
          return "DVD";
        case MPEG2EncodingParams.STREAMOUTPUT_MPEG1:
          return "MPEG-1";
        case MPEG2EncodingParams.STREAMOUTPUT_PROGRAM:
          if (quality.startsWith("SVCD"))
            return "SVCD";
          return "MPEG-2 PS";
        case MPEG2EncodingParams.STREAMOUTPUT_TRANSPORT:
          return "MPEG-2 TS";
        case MPEG2EncodingParams.STREAMOUTPUT_VCD:
          return "VCD";
        default:
          return "MPEG-2";
      }
    }
    return "MPEG-2";
  }

  public int getCountryCode() { return Sage.getInt(prefs + COUNTRY_CODE, 0); }
  public int getVideoFormatCode() { return Sage.getInt(prefs + VIDEO_FORMAT_CODE, 0); }
  public boolean isNTSCVideoFormat() { return getVideoFormatCode() < 8; }

  public boolean useMpeg4InMpeg2() { return Sage.getBoolean(prefs + "enable_mpeg4_timeshifting", true); }

  public int getTotalCaptureDeviceResourceUsage()
  {
    // Just go through all the cap devs and add this up
    int totalUsage = 0;
    CaptureDevice[] capDevs = getCaptureDevices();
    for (int i = 0; i < capDevs.length; i++)
      totalUsage += capDevs[i].getResourceUsage();
    return totalUsage;
  }

  private String prefs;
  private boolean alive;

  private String lastEncoderName;
  private int featureMaskDisable;

  private java.util.ArrayList capDevMgrs;
  private java.util.Map globalEncoderMap;

  public static final java.util.Comparator stringNumComparator = 	new java.util.Comparator()
  {
    public int compare(Object o1, Object o2)
    {
      String s1 = (String) o1;
      String s2 = (String) o2;
      try
      {
        int i1 = Integer.parseInt(s1);
        int i2 = Integer.parseInt(s2);
        return i1 - i2;
      }
      catch (NumberFormatException e)
      {
        if (Sage.userLocale != lastLocale)
        {
          collie = java.text.Collator.getInstance(Sage.userLocale);
          collie.setStrength(java.text.Collator.PRIMARY);
          lastLocale = Sage.userLocale;
        }
        return collie.compare(s1, s2);
      }
    }
    private java.text.Collator collie;
    private java.util.Locale lastLocale;
    {
      collie = java.text.Collator.getInstance(Sage.userLocale);
      collie.setStrength(java.text.Collator.PRIMARY);
      lastLocale = Sage.userLocale;
    }
  };
}
