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
public class LinuxDVBCaptureManager implements CaptureDeviceManager
{

  /** Creates a new instance of LinuxDVBCaptureManager */
  public LinuxDVBCaptureManager()
  {
    sage.Native.loadLibrary("DVBCapture");
    prefs = MMC.MMC_KEY + '/';
    mmc = MMC.getInstance();
    encoderMap = new java.util.LinkedHashMap();
  }

  private void addInputs(CaptureDevice capDev)
  {
    // We use digital tv tuner
    capDev.ensureInputExists(100, 0);
  }

  public void detectCaptureDevices(CaptureDevice[] alreadyDetectedDevices)
  {
    Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("Linux_DVB_Capture_Manager") }));

    String[] dvbFiles = new java.io.File("/dev/dvb/").list();
    java.util.ArrayList dvbDevVec = new java.util.ArrayList();
    java.util.Map devToModelMap = new java.util.HashMap();
    java.util.Map modelToDevMap = new java.util.HashMap();
    if(dvbFiles!=null)
    {
      java.util.Arrays.sort(dvbFiles);
      for (int i = 0; i < dvbFiles.length; i++)
      {
        if (Sage.DBG) System.out.println("dvbfile "+i+" "+dvbFiles[i]);
        try
        {
          if (dvbFiles[i].startsWith("adapter"))
          {
            dvbDevVec.add(dvbFiles[i]);
            devToModelMap.put(dvbFiles[i], DVBCaptureDevice.getCardModelUIDForDevice(dvbFiles[i]));
            modelToDevMap.put(devToModelMap.get(dvbFiles[i]), dvbFiles[i]);
          }
        }
        catch (Exception e)
        {}
      }
    }
    String[] dvbDevs = (String[]) dvbDevVec.toArray(Pooler.EMPTY_STRING_ARRAY);
    if (Sage.DBG) System.out.println("videoDevices=" + dvbDevVec + " devModelMap=" + devToModelMap);
    System.out.println("detect 3");

    String[] encoderKeys = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/');
    for (int i = 0; i < encoderKeys.length; i++)
    {
      if (Sage.DBG) System.out.println("Checking encoder key:" + encoderKeys[i]);
      String devClassProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
          CaptureDevice.DEVICE_CLASS, "");
      if (devClassProp.length() > 0 && !devClassProp.equals("DVB") && !devClassProp.equals("DVB2"))
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

        // See if we need to upgrade from the old version
        String currDevName = Sage.get(prefs + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
            CaptureDevice.VIDEO_CAPTURE_DEVICE_NAME, "");
        if (devClassProp.equals("DVB"))
        {
          String newModelName = (String) devToModelMap.get(currDevName);
          if (newModelName == null)
          {
            if (Sage.DBG) System.out.println("Skipping property upgrade for DVB device since it doesn't exist: " + currDevName);
            continue;
          }

          currDevName = newModelName;
          Sage.put(prefs + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' + CaptureDevice.VIDEO_CAPTURE_DEVICE_NAME, newModelName);
          Sage.put(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
              CaptureDevice.DEVICE_CLASS, "DVB2");
        }

        // Check to see if the device file is there
        if (!modelToDevMap.containsKey(currDevName))
        {
          if (Sage.DBG) System.out.println("Capture device is not available, skipping: " + currDevName);
          continue;
        }

        String broadcast = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
            DVBBroadcastCaptureDevice.MULTICAST_BROADCAST_ADDRESS, "");
        if (broadcast.length() > 0)
        {
          try
          {
            DVBBroadcastCaptureDevice testEncoder = new DVBBroadcastCaptureDevice(Integer.parseInt(encoderKeys[i]));
            if (Sage.DBG) System.out.println("Created DVBBroadcastCaptureDevice object for:" + testEncoder);
            encoderMap.put(testEncoder.getName(), testEncoder);
          }
          catch (Exception e){System.out.println("ERROR creating capture device:" + e);}
        }
        else
        {
          try
          {
            DVBCaptureDevice testEncoder = new DVBCaptureDevice(Integer.parseInt(encoderKeys[i]));
            if (Sage.DBG) System.out.println("Created DVBCaptureDevice object for:" + testEncoder);
            testEncoder.setLinuxVideoDevice((String) modelToDevMap.get(currDevName));
            encoderMap.put(testEncoder.getName(), testEncoder);
          }
          catch (NumberFormatException e){}
        }
      }
    }

    if (Sage.DBG) System.out.println("EncoderMap1=" + encoderMap);

    // There should be an encoder for each video capture device on the system
    java.util.ArrayList doneDevices = new java.util.ArrayList();
    java.util.Iterator walker = encoderMap.values().iterator();
    int checkEncRet = 0;
    while (walker.hasNext())
    {
      DVBCaptureDevice testEncoder = (DVBCaptureDevice) walker.next();
      // NOTE: At this point we assume the capture devices will function correctly
      addInputs(testEncoder);
      doneDevices.add(testEncoder.getName());
      testEncoder.writePrefs();
      Sage.savePrefs();
    }

    java.util.HashMap devNameCounter = new java.util.HashMap();
    for (int i = 0; i < dvbDevs.length; i++)
    {
      if (Sage.DBG) System.out.println("Processing new system dev:" + dvbDevs[i]);
      if (devNameCounter.containsKey(dvbDevs[i]))
        devNameCounter.put(dvbDevs[i], new Integer(((Integer)devNameCounter.get(dvbDevs[i])).intValue() + 1));
      else
        devNameCounter.put(dvbDevs[i], new Integer(0));
      int devCount = ((Integer)devNameCounter.get(dvbDevs[i])).intValue();
      String currDevName = (String) devToModelMap.get(dvbDevs[i]);
      String newCapDevName = CaptureDevice.getComplexName(currDevName, devCount);
      if (doneDevices.contains(newCapDevName))
      {
        if (Sage.DBG) System.out.println("Device already has been processed");
        //doneDevices.remove(CaptureDevice.getComplexName(videoDevices[i], devCount));
        continue;
      }
      if (CaptureDevice.isDeviceAlreadyUsed(alreadyDetectedDevices, dvbDevs[i], devCount))
      {
        if (Sage.DBG) System.out.println("Device already is used");
        continue;
      }

      DVBCaptureDevice testEncoder = new DVBCaptureDevice();
      testEncoder.captureDeviceName = currDevName;
      testEncoder.captureDeviceNum = devCount;
      testEncoder.setLinuxVideoDevice(dvbDevs[i]);
      testEncoder.createID();
      addInputs(testEncoder);
      // For now we assume they always pass the check
      testEncoder.writePrefs();
      encoderMap.put(testEncoder.getName(), testEncoder);
    }

    capDevs = (DVBCaptureDevice[]) encoderMap.values().toArray(new DVBCaptureDevice[0]);
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
  private DVBCaptureDevice[] capDevs;
  private MMC mmc;
}
