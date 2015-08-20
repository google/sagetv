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
 * @abstract Manages HDHomeRun network encoders
 * @author  David DeHaven
 */
public class HDHomeRunCaptureManager implements CaptureDeviceManager
{
  public HDHomeRunCaptureManager() throws Throwable
  {
    try {
      System.loadLibrary("HDHomeRunCapture");
      hdhrEnabled = true;
    } catch (Throwable t) {
      System.out.println("Unable to load HDHomeRun capture manager: " + t);
      throw t;
    }

    if(hdhrEnabled) {
      prefs = MMC.MMC_KEY + '/';
      mmc = MMC.getInstance();
      encoderMap = new java.util.LinkedHashMap();

      // start our device polling thread
      Pooler.execute(new Runnable() {
        public void run() {
          try {
            Thread.sleep(5000);
          } catch(Throwable t) {}
          poll();
        }
      }, "HDHR Device Poller", Thread.MIN_PRIORITY);
    }
  }

  // Do occasional polls for new (removed?) devices and trigger device list refreshes accordingly
  public void poll()
  {
    // TODO: implement
  }

  public void detectCaptureDevices(CaptureDevice[] alreadyDetectedDevices)
  {
    if(!hdhrEnabled) return;

    // poll for the current list of devices
    String devlist[] = getDeviceList0(); // each entry is a unique device name, number always zero

    // check for already configured hardware
    String[] encoderKeys = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/');
    for (int i = 0; i < encoderKeys.length; i++)
    {
      if (Sage.DBG) System.out.println("Checking encoder key:" + encoderKeys[i]);
      String devClassProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
          CaptureDevice.DEVICE_CLASS, "");
      if (devClassProp.length() > 0 && !devClassProp.equals("HDHomeRun"))
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
          HDHomeRunCaptureDevice testEncoder = new HDHomeRunCaptureDevice(Integer.parseInt(encoderKeys[i]));
          if (Sage.DBG) System.out.println("Created HDHomeRunCaptureDevice object for:" + testEncoder);
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
      HDHomeRunCaptureDevice testEncoder = (HDHomeRunCaptureDevice) walker.next();
      // NOTE: At this point we assume the capture devices will function correctly
      testEncoder.addInputs(); // populate the video source list
      doneDevices.add(testEncoder.getName());
      testEncoder.writePrefs();
      Sage.savePrefs();
    }

    for (int i = 0; i < devlist.length; i++)
    {
      String devName = devlist[i];
      int devNumber = 0;

      if (Sage.DBG) System.out.println("Processing new system dev:" + devName);// + ", " + devNumber);

      String newCapDevName = CaptureDevice.getComplexName(devName, 0);//devNumber);
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

      HDHomeRunCaptureDevice testEncoder = new HDHomeRunCaptureDevice();
      testEncoder.captureDeviceName = devName;
      testEncoder.captureDeviceNum = devNumber;
      testEncoder.createID();
      testEncoder.addInputs();
      // For now we assume they always pass the check
      testEncoder.writePrefs();
      encoderMap.put(testEncoder.getName(), testEncoder);
    }

    capDevs = (HDHomeRunCaptureDevice[]) encoderMap.values().toArray(new HDHomeRunCaptureDevice[0]);
  }

  public void freeResources()
  {
    if(!hdhrEnabled) return;
    if(capDevs != null) {
      for (int i = 0; i < capDevs.length; i++)
        capDevs[i].freeDevice();
    }
  }

  public CaptureDevice[] getCaptureDevices()
  {
    if(!hdhrEnabled) return new HDHomeRunCaptureDevice[0];
    if(capDevs == null) capDevs = new HDHomeRunCaptureDevice[0]; // or we'll crash and burn
    return capDevs;
  }

  // every two entries identifies a device, name then number
  // this will allow for future expansion (and I'm too lazy to rewrite the code that was already in place ;)
  private native String[] getDeviceList0();

  private boolean hdhrEnabled = false;

  private String prefs;
  private java.util.Map encoderMap;
  private HDHomeRunCaptureDevice[] capDevs;
  private MMC mmc;
}
