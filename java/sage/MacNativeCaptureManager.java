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
 * @abstract Manages [all] native (directly linked) capture devices on the Macintosh
 * @author  David DeHaven
 */
public class MacNativeCaptureManager implements CaptureDeviceManager
{
  public MacNativeCaptureManager() throws Throwable
  {
    try {
      sage.Native.loadLibrary("MacNativeCapture");
    } catch (Throwable t) {
      System.out.println("Unable to load native capture manager: " + t);
      throw t;
    }

    prefs = MMC.MMC_KEY + '/';
    mmc = MMC.getInstance();
    encoderMap = new java.util.LinkedHashMap();
  }

  public void detectCaptureDevices(CaptureDevice[] alreadyDetectedDevices)
  {
    // poll for the current list of devices
    String devlist[] = getDeviceList0(); // name, num, name, num...

    // check for already configured hardware
    String[] encoderKeys = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/');
    for (int i = 0; i < encoderKeys.length; i++)
    {
      if (Sage.DBG) System.out.println("Checking encoder key:" + encoderKeys[i]);
      String devClassProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
          CaptureDevice.DEVICE_CLASS, "");
      if (devClassProp.length() > 0 && !devClassProp.equals("MacNative"))
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
          MacNativeCaptureDevice testEncoder = new MacNativeCaptureDevice(Integer.parseInt(encoderKeys[i]));
          if (Sage.DBG) System.out.println("Created MacNativeCaptureDevice object for:" + testEncoder);
          encoderMap.put(testEncoder.getName(), testEncoder);
        }
        catch (NumberFormatException e){}
      }
    }

    if (Sage.DBG) System.out.println("EncoderMap1=" + encoderMap);

    // There should be an encoder for each video capture device on the system
    java.util.ArrayList doneDevices = new java.util.ArrayList();
    java.util.Iterator walker = encoderMap.values().iterator();
    int checkEncRet = 0;
    while (walker.hasNext())
    {
      MacNativeCaptureDevice testEncoder = (MacNativeCaptureDevice) walker.next();
      // NOTE: At this point we assume the capture devices will function correctly
      testEncoder.addInputs(); // populate the video source list
      doneDevices.add(testEncoder.getName());
      testEncoder.writePrefs();
      Sage.savePrefs();
    }

    for (int i = 0; i < devlist.length; i += 2)
    {
      String devName = devlist[i];
      int devNumber = Integer.parseInt(devlist[i+1]);

      if (Sage.DBG) System.out.println("Processing new system dev:" + devName + ", " + devNumber);

      String newCapDevName = CaptureDevice.getComplexName(devName, devNumber);
      if (doneDevices.contains(newCapDevName))
      {
        if (Sage.DBG) System.out.println("Device already has been processed");
        //doneDevices.remove(CaptureDevice.getComplexName(videoDevices[i], devCount));
        continue;
      }
      if (CaptureDevice.isDeviceAlreadyUsed(alreadyDetectedDevices, devName, devNumber))
      {
        if (Sage.DBG) System.out.println("Device already is used");
        continue;
      }

      MacNativeCaptureDevice testEncoder = new MacNativeCaptureDevice();
      testEncoder.captureDeviceName = devName;
      testEncoder.captureDeviceNum = devNumber;
      testEncoder.createID();
      testEncoder.addInputs();

      // For new capture devices that are SW encode only, set the default quality to Fair instead of Great
      if (!testEncoder.isHWEncoder())
      {
        testEncoder.setDefaultQuality(Sage.rez("Fair"));
      }
      else if (testEncoder.isCaptureFeatureSupported(CaptureDevice.HDPVR_ENCODER_MASK))
      {
        System.out.println("New HD-PVR, setting default quality to Great-H.264");
        testEncoder.setDefaultQuality(Sage.rez("Great") + "-H.264");
      }

      // For now we assume they always pass the check
      testEncoder.writePrefs();
      encoderMap.put(testEncoder.getName(), testEncoder);
    }

    capDevs = (MacNativeCaptureDevice[]) encoderMap.values().toArray(new MacNativeCaptureDevice[0]);
  }

  public void freeResources()
  {
    if(capDevs != null) {
      for (int i = 0; i < capDevs.length; i++)
        capDevs[i].freeDevice();
    }
  }

  public CaptureDevice[] getCaptureDevices()
  {
    if(capDevs == null) capDevs = new MacNativeCaptureDevice[0]; // or we'll crash and burn
    return capDevs;
  }

  // every two entries identifies a device, name then number
  private native String[] getDeviceList0();

  private String prefs;
  private java.util.Map encoderMap;
  private MacNativeCaptureDevice[] capDevs;
  private MMC mmc;
}
