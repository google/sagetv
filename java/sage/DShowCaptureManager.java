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

public class DShowCaptureManager implements CaptureDeviceManager
{
  public static final String SCAN_DSHOW_FILTERS = "scan_dshow_filters";
  public static final String IGNORE_ENCODERS = "ignore_encoders";

  public static final String HW_DECODER_CATEGORY_GUID =     "{2721AE20-7E70-11D0-A5D6-28DB04C10000}";
  public static final String CAPTURE_DEVICE_CATEGORY_GUID = "{65E8773D-8F56-11D0-A3B9-00A0C9223196},{FD0A5AF4-B41D-11d2-9C95-00C04F7971E0},{71985F48-1CA1-11d3-9CC8-00C04F7971E0}"; //ZQ
  public static final String FILTERS_CATEGORY_GUID =        "{083863F1-70DE-11D0-BD40-00A0C911CE86}";
  public static final String AUDIO_RENDERER_CATEGORY_GUID = "{E0F158E1-CB04-11D0-BD4E-00A0C911CE86}";
  public static final String AUDIO_CAPTURE_DEVICE_CATEGORY_GUID = "{33D9A762-90C8-11D0-BD43-00A0C911CE86}";
  //static final String DIGITAL_TV_DEVICE_CATEGORY_GUID="{71985f48-1ca1-11d3-9cc8-00c04f7971e0}";  //ZQ

  private static final String[] NEVER_IGNORED_ENCODERS = {
    "VBDVCR Capture",
    "Hauppauge WinTV PVR PCI II Capture",
    "Hauppauge WinTV PVR USB2 Encoder",
    "Hauppauge WinTV 88x Video Capture",
    "ATI AVStream Analog Capture",
    "PX-TV100U",
    "Plextor ConvertX M402U A/V Capture",
    "Plextor ConvertX TV402U A/V Capture",
    "ATI Rage Theater Video Capture",
    "Hauppauge WinTV Capture",
    "Conexant Capture",
    "ATI eHomeWonder Capture",
    "Conexant 2388x Video Capture",
    "AVerMedia AVerTV MPEG Video Capture",
    "ATI TV Wonder Capture",
    "ATI DTV Wonder Analog AV Capture",
    "AVerTVHD A180 BDA Digital Capture",
    "AVerTVHD A180 Video Capture",
    "Hauppauge HD PVR Capture Device"
  };

  private static final String[] ALWAYS_IGNORED_ENCODERS = {
    "NVIDIA DualTV YUV Capture",
    "NVIDIA DualTV YUV Capture 2",
  };

  public DShowCaptureManager()
  {
    sage.Native.loadLibrary("DShowCapture");

    prefs = MMC.MMC_KEY + '/';
    mmc = MMC.getInstance();
    encoderMap = new java.util.LinkedHashMap();

    String ignEncStr = Sage.get(prefs + IGNORE_ENCODERS, "");
    if (ignEncStr.indexOf('|') != -1)
      ignoredEncoders = Sage.parseDelimSet(ignEncStr, "|");
    else
      ignoredEncoders = Sage.parseCommaDelimSet(ignEncStr);
    for (int i = 0; i < ALWAYS_IGNORED_ENCODERS.length; i++)
      ignoredEncoders.add(ALWAYS_IGNORED_ENCODERS[i]);

    if (Sage.getBoolean(prefs + "pentium_4_fix", false))
      Sage.writeDwordValue(Sage.HKEY_LOCAL_MACHINE, "SYSTEM\\ServerDriver", "Enable", 1);

    // Enable CC for Hauppauge cards if they're in the system
    String[] ivacRegNames = Sage.getRegistryNames(Sage.HKEY_LOCAL_MACHINE,
        "SYSTEM\\CurrentControlSet\\Services\\Globespan\\Parameters\\ivac15\\Driver");
    if (ivacRegNames != null && ivacRegNames.length > 0 && Sage.getBoolean(prefs + "enable_cc_for_hauppauge_cards", true))
    {
      Sage.writeDwordValue(Sage.HKEY_LOCAL_MACHINE,
          "SYSTEM\\CurrentControlSet\\Services\\Globespan\\Parameters\\ivac15\\Driver",
          "InsertCCInDvd", 1);
    }
  }

  private void findDevices()
  {
    if (SageTV.upgrade)
    {
      // Because we changed this to add DVB support
      Sage.put(prefs + "dshow_cap_dev_categories", CAPTURE_DEVICE_CATEGORY_GUID);
    }
    java.util.StringTokenizer toker = new java.util.StringTokenizer(Sage.get(prefs + "dshow_cap_dev_categories",
        CAPTURE_DEVICE_CATEGORY_GUID), ",;");

    //            	java.util.StringTokenizer toker = new java.util.StringTokenizer(Sage.get(prefs + "dshow_cap_dev_categories",
    //			DIGITAL_TV_DEVICE_CATEGORY_GUID), ",;");

    //
    //		java.util.ArrayList vec = new java.util.ArrayList();
    //		while (toker.hasMoreTokens())
    //		{
    //                      String categoryClsid;
    //                       categoryClsid = toker.nextToken();
    //                       vec.addAll(java.util.Arrays.asList(DShowCaptureDevice.getDevicesInCategory0(categoryClsid)));
    //		}
    //		systemCaptureDevices = (String[]) vec.toArray(new String[0]);
    //
    java.util.ArrayList vec = new java.util.ArrayList();
    while (toker.hasMoreTokens())
    {
      vec.add(toker.nextToken());
    }

    //getDevicesInCategoryS0() megers all devices that are the same device in different category ZQ.
    systemCaptureDevices = DShowCaptureDevice.getDevicesInCategoryS0((String[])vec.toArray(new String[0]));

  }

  public void detectCaptureDevices(CaptureDevice[] alreadyDetectedDevices)
  {
    Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("DirectShow_WDM_Capture_Manager") }));
    findDevices();
    if (Sage.DBG) System.out.println("systemCapDevices=" + java.util.Arrays.asList(systemCaptureDevices));
    //if (Sage.DBG) System.out.println("hwDecoderFilters=" + java.util.Arrays.asList(hwDecoderFilters));
    //if (Sage.DBG) System.out.println("audioRenderFilters=" + java.util.Arrays.asList(audioRenderFilters));

    String[] encoderKeys = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/');
    for (int i = 0; i < encoderKeys.length; i++)
    {
      if (Sage.DBG) System.out.println("Checking encoder key:" + encoderKeys[i]);
      String devClassProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
          CaptureDevice.DEVICE_CLASS, "");
      if (devClassProp.length() > 0 && !devClassProp.equals("DirectShow"))
        continue;
      String hostProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
          NetworkCaptureDevice.ENCODING_HOST, "");
      if (hostProp.length() == 0)
      {
        if (CaptureDevice.isDeviceAlreadyUsed(alreadyDetectedDevices,
            Sage.get(prefs + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
                CaptureDevice.VIDEO_CAPTURE_DEVICE_NAME, ""), Sage.getInt(prefs +
                    CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' + CaptureDevice.VIDEO_CAPTURE_DEVICE_NUM, 0)))
        {
          if (Sage.DBG) System.out.println("Device is already accounted for.");
          continue;
        }
        try
        {
          DShowCaptureDevice testEncoder = new DShowCaptureDevice(Integer.parseInt(encoderKeys[i]));
          if (Sage.DBG) System.out.println("Created DShowCapDev object for:" + testEncoder);
          encoderMap.put(testEncoder.getName(), testEncoder);
        }
        catch (NumberFormatException e){}
      }
    }
    encoderMap.keySet().removeAll(ignoredEncoders);

    if (Sage.DBG) System.out.println("EncoderMap1=" + encoderMap);

    // There should be an encoder for each video capture device on the system
    java.util.ArrayList doneDevices = new java.util.ArrayList();
    java.util.Iterator walker = encoderMap.values().iterator();
    int checkEncRet = 0;
    while (walker.hasNext())
    {
      DShowCaptureDevice testEncoder = (DShowCaptureDevice) walker.next();
      // If an encoder has been configured then don't add it to the potential ignore list
      boolean canIgnoreMe = !testEncoder.hasConfiguredInput();
      for (int i = 0; i < NEVER_IGNORED_ENCODERS.length; i++)
        if (NEVER_IGNORED_ENCODERS[i].equals(testEncoder.getCaptureDeviceName()))
          canIgnoreMe = false;
      if (testEncoder.getCaptureDeviceName().startsWith("Silicondust HDHomeRun"))
        canIgnoreMe = false;
      if (canIgnoreMe)
      {
        ignoredEncoders.add(testEncoder.getName());
        Sage.put(prefs + IGNORE_ENCODERS, Sage.createDelimSetString(ignoredEncoders, "|"));
        Sage.savePrefs();
      }
      if ((checkEncRet = testEncoder.checkEncoder()) == 1)
      {
        if (Sage.DBG) System.out.println("Encoder passed the check:" + testEncoder);
        if (canIgnoreMe)
        {
          ignoredEncoders.remove(testEncoder.getName());
          Sage.put(prefs + IGNORE_ENCODERS, Sage.createDelimSetString(ignoredEncoders, "|"));
        }
        doneDevices.add(testEncoder.getName());
        // 1 - check for existence of each device/filter
        // 2 - Determine the type of capture we're doing
        // checkEncoder0 may modify the Encoder data if it's inaccurate
        testEncoder.applyFeatureMask(~mmc.getFeatureMaskDisable());
        testEncoder.writePrefs();
        Sage.savePrefs();
      }
      else
      {
        if (Sage.DBG) System.out.println("Encoder failed the check:" + testEncoder);
        if (checkEncRet >= 0)
        {
          if (canIgnoreMe)
          {
            ignoredEncoders.remove(testEncoder.getName());
            Sage.put(prefs + IGNORE_ENCODERS, Sage.createDelimSetString(ignoredEncoders, "|"));
          }
          Sage.savePrefs();
        }
        else
        {
          if (Sage.DBG) System.out.println("Permanently ignoring encoder:" + testEncoder.getName());
        }
        System.out.println("Removing encoder because it failed the check:" + testEncoder.getName());
        doneDevices.add(testEncoder.getName());
        // Don't clear it because we don't want to destroy the information if they're trying
        // to modify the properties and have made an error
        //testEncoder.clearPrefs();
        walker.remove();
      }
    }
    java.util.HashMap devNameCounter = new java.util.HashMap();
    for (int i = 0; i < systemCaptureDevices.length; i++)
    {
      if (Sage.DBG) System.out.println("Processing new system dev:" + systemCaptureDevices[i]);
      if (devNameCounter.containsKey(systemCaptureDevices[i]))
        devNameCounter.put(systemCaptureDevices[i], new Integer(((Integer)devNameCounter.get(systemCaptureDevices[i])).intValue() + 1));
      else
        devNameCounter.put(systemCaptureDevices[i], new Integer(0));
      int devCount = ((Integer)devNameCounter.get(systemCaptureDevices[i])).intValue();
      String newCapDevName = CaptureDevice.getComplexName(systemCaptureDevices[i], devCount);
      if (doneDevices.contains(newCapDevName))
      {
        if (Sage.DBG) System.out.println("Device already has been processed");
        //doneDevices.remove(CaptureDevice.getComplexName(videoDevices[i], devCount));
        continue;
      }
      if (CaptureDevice.isDeviceAlreadyUsed(alreadyDetectedDevices, systemCaptureDevices[i], devCount))
      {
        if (Sage.DBG) System.out.println("Device already is used");
        continue;
      }
      if (ignoredEncoders.contains(newCapDevName) || ignoredEncoders.contains(systemCaptureDevices[i]))
      {
        continue;
      }

      DShowCaptureDevice testEncoder = new DShowCaptureDevice();
      testEncoder.captureDeviceName = systemCaptureDevices[i];
      testEncoder.captureDeviceNum = devCount;
      testEncoder.createID();
      boolean canIgnoreMe = true;
      for (int j = 0; j < NEVER_IGNORED_ENCODERS.length; j++)
        if (NEVER_IGNORED_ENCODERS[j].equals(testEncoder.getName()))
          canIgnoreMe = false;
      if (testEncoder.getCaptureDeviceName().startsWith("Silicondust HDHomeRun"))
        canIgnoreMe = false;
      if (canIgnoreMe)
      {
        ignoredEncoders.add(testEncoder.getName());
        Sage.put(prefs + IGNORE_ENCODERS, Sage.createDelimSetString(ignoredEncoders, "|"));
      }
      Sage.savePrefs();
      if ((checkEncRet = testEncoder.checkEncoder()) == 1)
      {
        if (Sage.DBG) System.out.println("New sysdev passed the check");
        testEncoder.applyFeatureMask(~mmc.getFeatureMaskDisable());

        // For new capture devices that are SW encode only, set the default quality to Fair instead of Great
        if (!testEncoder.isHWEncoder())
        {
          testEncoder.setDefaultQuality(Sage.rez("Fair"));
        }
        else if (testEncoder.isCaptureFeatureSupported(CaptureDevice.HDPVR_ENCODER_MASK))
        {
          testEncoder.setDefaultQuality(Sage.rez("Great") + "-H.264");
        }
        testEncoder.writePrefs();
        encoderMap.put(testEncoder.getName(), testEncoder);
      }
      else
      {
        if (Sage.DBG) System.out.println("New sysdev failed the check");
      }
      if (checkEncRet >= 0)
      {
        if (canIgnoreMe)
        {
          ignoredEncoders.remove(testEncoder.getName());
          Sage.put(prefs + IGNORE_ENCODERS, Sage.createDelimSetString(ignoredEncoders, "|"));
        }
      }
      else
      {
        if (Sage.DBG) System.out.println((canIgnoreMe ? "Permanently " : "" ) + "ignoring encoder :" + testEncoder.getName());
      }
    }

    capDevs = (DShowCaptureDevice[]) encoderMap.values().toArray(new DShowCaptureDevice[0]);
  }

  public void freeResources()
  {
    for (int i = 0; i < capDevs.length; i++)
      capDevs[i].freeDevice();
  }

  public CaptureDevice[] getCaptureDevices()
  {
    return capDevs;
  }

  private String prefs;

  private String[] systemCaptureDevices = new String[0];
  private boolean hasDX9;
  private String userParamDir;
  private java.util.Map encoderMap;
  private DShowCaptureDevice[] capDevs;
  private java.util.Set ignoredEncoders;
  private MMC mmc;
}
