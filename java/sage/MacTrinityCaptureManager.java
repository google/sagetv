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
 * @author  David DeHaven
 */
public class MacTrinityCaptureManager implements CaptureDeviceManager
{
  public MacTrinityCaptureManager()
  {
    startTrinity();
    prefs = MMC.MMC_KEY + '/';
    mmc = MMC.getInstance();
    encoderMap = new java.util.LinkedHashMap();
  }

  public synchronized boolean startTrinity()
  {
    if(trinityAvailable) return true; // please don't restart...

    try {
      trinityAvailable = initTrinity0();
    } catch(Throwable t) {
      System.out.println("Exception trying to start Trinity: " + t);
    };

    return trinityAvailable;
  }

  public void detectCaptureDevices(CaptureDevice[] alreadyDetectedDevices)
  {
    if(!trinityAvailable) if(!startTrinity()) return;

    // poll Trinity for the current list of devices
    String devlist[] = getDeviceList0();
    if(devlist == null) devlist = new String[0];

    // check for already configured hardware
    String[] encoderKeys = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/');
    for (int i = 0; i < encoderKeys.length; i++)
    {
      if (Sage.DBG) System.out.println("Checking encoder key:" + encoderKeys[i]);
      String devClassProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
          CaptureDevice.DEVICE_CLASS, "");
      if (devClassProp.length() > 0 && !devClassProp.equals("Trinity"))
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
          MacTrinityCaptureDevice testEncoder = new MacTrinityCaptureDevice(Integer.parseInt(encoderKeys[i]));
          if (Sage.DBG) System.out.println("Created MacTrinityCaptureDevice object for:" + testEncoder);
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
      MacTrinityCaptureDevice testEncoder = (MacTrinityCaptureDevice) walker.next();
      // NOTE: At this point we assume the capture devices will function correctly
      testEncoder.addInputs(); // populate the video source list
      doneDevices.add(testEncoder.getName());
      testEncoder.writePrefs();
      Sage.savePrefs();
    }

    java.util.HashMap devNameCounter = new java.util.HashMap();
    for (int i = 0; i < devlist.length; i++)
    {
      if (Sage.DBG) System.out.println("Processing new system dev:" + devlist[i]);
      if (devNameCounter.containsKey(devlist[i]))
        devNameCounter.put(devlist[i], new Integer(((Integer)devNameCounter.get(devlist[i])).intValue() + 1));
      else
        devNameCounter.put(devlist[i], new Integer(0));
      int devCount = ((Integer)devNameCounter.get(devlist[i])).intValue();
      String newCapDevName = CaptureDevice.getComplexName(devlist[i], devCount);
      if (doneDevices.contains(newCapDevName))
      {
        if (Sage.DBG) System.out.println("Device already has been processed");
        //doneDevices.remove(CaptureDevice.getComplexName(videoDevices[i], devCount));
        continue;
      }
      if (CaptureDevice.isDeviceAlreadyUsed(alreadyDetectedDevices, devlist[i], devCount))
      {
        if (Sage.DBG) System.out.println("Device already is used");
        continue;
      }

      MacTrinityCaptureDevice testEncoder = new MacTrinityCaptureDevice();
      testEncoder.captureDeviceName = devlist[i]; // unique serial number
      testEncoder.captureDeviceNum = devCount; // should always be zero
      testEncoder.createID();
      testEncoder.addInputs();
      // For now we assume they always pass the check
      testEncoder.writePrefs();
      encoderMap.put(testEncoder.getName(), testEncoder);
    }

    capDevs = (MacTrinityCaptureDevice[]) encoderMap.values().toArray(new MacTrinityCaptureDevice[0]);
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
    if(capDevs == null) capDevs = new MacTrinityCaptureDevice[0]; // or we'll crash and burn
    return capDevs;
  }

  // returns a list of unique device serial numbers
  private static native boolean initTrinity0();
  private static boolean trinityAvailable = false; // could be false if the Eskape software is not installed

  private native String[] getDeviceList0();

  private String prefs;
  private java.util.Map encoderMap;
  private MacTrinityCaptureDevice[] capDevs;
  private MMC mmc;
}
