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
 * @author  Jean-Francois based on IVTV by Narflex
 */
public class LinuxFirewireCaptureManager implements CaptureDeviceManager
{

  /** Creates a new instance of LinuxFirewireCaptureManager */
  public LinuxFirewireCaptureManager()
  {
    sage.Native.loadLibrary("FirewireCapture");
    prefs = MMC.MMC_KEY + '/';
    mmc = MMC.getInstance();
    encoderMap = new java.util.LinkedHashMap();
  }

  private void addInputs(CaptureDevice capDev)
  {
    // We use digital tv tuner
    capDev.ensureInputExists(CaptureDeviceInput.TV_TUNER_CROSSBAR_INDEX, 0);
  }


  public void detectCaptureDevices(CaptureDevice[] alreadyDetectedDevices)
  {
    Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("Linux_Firewire_Capture_Manager") }));

    java.util.ArrayList firewireDevVec = new java.util.ArrayList();
    java.util.StringTokenizer ST = new java.util.StringTokenizer(ListFirewireNodes0(), ",");
    while(ST.hasMoreTokens())
    {
      firewireDevVec.add(ST.nextToken());
    }
    String[] firewireDevs = (String[]) firewireDevVec.toArray(new String[0]);
    if (Sage.DBG) System.out.println("videoDevices=" + firewireDevVec);

    String[] encoderKeys = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/');
    for (int i = 0; i < encoderKeys.length; i++)
    {
      if (Sage.DBG) System.out.println("Checking encoder key:" + encoderKeys[i]);
      String devClassProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
          CaptureDevice.DEVICE_CLASS, "");
      if (devClassProp.length() > 0 && !devClassProp.equals("LinuxFirewire"))
        continue;
      String hostProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
          NetworkCaptureDevice.ENCODING_HOST, "");
      if (hostProp.length() == 0) // Local
      {
        if (CaptureDevice.isDeviceAlreadyUsed(alreadyDetectedDevices,
            Sage.get(prefs + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
                CaptureDevice.VIDEO_CAPTURE_DEVICE_NAME, ""), Sage.getInt(prefs +
                    CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' + CaptureDevice.VIDEO_CAPTURE_DEVICE_NUM, 0)))
        {
          if (Sage.DBG) System.out.println("Device is already accounted for.");
          continue;
        }
        // Check to see if the device file is there
        if(!AvailableFirewireDevice0(Sage.get(prefs + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
            CaptureDevice.VIDEO_CAPTURE_DEVICE_NAME, "")))
        {
          if (Sage.DBG) System.out.println("Capture device is not available, skipping");
          continue;
        }
        String broadcast = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
            FirewireBroadcastCaptureDevice.MULTICAST_BROADCAST_ADDRESS, "");
        if (broadcast.length() > 0)
        {
          try
          {
            FirewireBroadcastCaptureDevice testEncoder = new FirewireBroadcastCaptureDevice(Integer.parseInt(encoderKeys[i]));
            if (Sage.DBG) System.out.println("Created FirewireBroadcastCaptureDevice object for:" + testEncoder);
            encoderMap.put(testEncoder.getName(), testEncoder);
          }
          catch (Exception e){System.out.println("ERROR creating capture device:" + e);}
        }
        else
        {
          try
          {
            FirewireCaptureDevice testEncoder = new FirewireCaptureDevice(Integer.parseInt(encoderKeys[i]));
            if (Sage.DBG) System.out.println("Created FirewireCaptureDevice object for:" + testEncoder);
            encoderMap.put(testEncoder.getName(), testEncoder);
          }
          catch (NumberFormatException e){}
        }
      }
    }

    if (Sage.DBG) System.out.println("EncoderMap1=" + encoderMap);

    // NOTE: WE STILL NEED TO ADD THE CROSSBAR INPUTS

    // There should be an encoder for each video capture device on the system
    java.util.ArrayList doneDevices = new java.util.ArrayList();
    java.util.Iterator walker = encoderMap.values().iterator();
    int checkEncRet = 0;
    while (walker.hasNext())
    {
      FirewireCaptureDevice testEncoder = (FirewireCaptureDevice) walker.next();
      // NOTE: At this point we assume the capture devices will function correctly
      addInputs(testEncoder);
      doneDevices.add(testEncoder.getName());
      testEncoder.writePrefs();
      Sage.savePrefs();
    }

    java.util.HashMap devNameCounter = new java.util.HashMap();
    for (int i = 0; i < firewireDevs.length; i++)
    {
      if (Sage.DBG) System.out.println("Processing new system dev:" + firewireDevs[i]);
      if (devNameCounter.containsKey(firewireDevs[i]))
        devNameCounter.put(firewireDevs[i], new Integer(((Integer)devNameCounter.get(firewireDevs[i])).intValue() + 1));
      else
        devNameCounter.put(firewireDevs[i], new Integer(0));
      int devCount = ((Integer)devNameCounter.get(firewireDevs[i])).intValue();
      String newCapDevName = CaptureDevice.getComplexName(firewireDevs[i], devCount);
      if (doneDevices.contains(newCapDevName))
      {
        if (Sage.DBG) System.out.println("Device already has been processed");
        //doneDevices.remove(CaptureDevice.getComplexName(videoDevices[i], devCount));
        continue;
      }
      if (CaptureDevice.isDeviceAlreadyUsed(alreadyDetectedDevices, firewireDevs[i], devCount))
      {
        if (Sage.DBG) System.out.println("Device already is used");
        continue;
      }

      FirewireCaptureDevice testEncoder = new FirewireCaptureDevice();
      testEncoder.captureDeviceName = firewireDevs[i];
      testEncoder.captureDeviceNum = devCount;
      testEncoder.createID();
      addInputs(testEncoder);
      // For now we assume they always pass the check
      testEncoder.writePrefs();
      encoderMap.put(testEncoder.getName(), testEncoder);
    }

    capDevs = (FirewireCaptureDevice[]) encoderMap.values().toArray(new FirewireCaptureDevice[0]);
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

  private java.util.Map encoderMap;
  private FirewireCaptureDevice[] capDevs;
  private MMC mmc;
  protected native String ListFirewireNodes0();
  protected native boolean AvailableFirewireDevice0(String name);

}
