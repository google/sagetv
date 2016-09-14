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

 * @author  Narflex

 */

public class LinuxIVTVCaptureManager implements CaptureDeviceManager

{



  /** Creates a new instance of LinuxCaptureManager */

  public LinuxIVTVCaptureManager()

  {

    sage.Native.loadLibrary("IVTVCapture");

    prefs = MMC.MMC_KEY + '/';

    mmc = MMC.getInstance();

    encoderMap = new java.util.LinkedHashMap();

  }



  private void addInputs(IVTVCaptureDevice capDev)

  {

    int inputindex=0;

    int[] alreadyIndexed = new int[128];

    String devName="/dev/"+capDev.getLinuxVideoDevice();

    String inputName;

    while((inputName = getV4LInputName(devName, inputindex))!=null)

    {

      if (inputName.toLowerCase().startsWith("compos"))

      {

        capDev.ensureInputExists(CaptureDeviceInput.COMPOSITE_CROSSBAR_INDEX,

            alreadyIndexed[CaptureDeviceInput.COMPOSITE_CROSSBAR_INDEX]++);

      }

      else if (inputName.toLowerCase().startsWith("tuner") ||

          inputName.toLowerCase().startsWith("tele"))

      {

        capDev.ensureInputExists(CaptureDeviceInput.TV_TUNER_CROSSBAR_INDEX,

            alreadyIndexed[CaptureDeviceInput.TV_TUNER_CROSSBAR_INDEX]++);

      }

      else if (inputName.toLowerCase().startsWith("s") &&

          inputName.toLowerCase().indexOf("video") != -1)

      {

        capDev.ensureInputExists(CaptureDeviceInput.S_VIDEO_CROSSBAR_INDEX,

            alreadyIndexed[CaptureDeviceInput.S_VIDEO_CROSSBAR_INDEX]++);

      }

      else if (inputName.toLowerCase().startsWith("compon"))

      {

        capDev.ensureInputExists(CaptureDeviceInput.COMPONENT_CROSSBAR_INDEX,

            alreadyIndexed[CaptureDeviceInput.COMPONENT_CROSSBAR_INDEX]++);

        capDev.ensureInputExists(CaptureDeviceInput.YPBPR_SPDIF_CROSSBAR_INDEX,

            alreadyIndexed[CaptureDeviceInput.YPBPR_SPDIF_CROSSBAR_INDEX]++);

      }

      inputindex+=1;

    }

  }



  public void detectCaptureDevices(CaptureDevice[] alreadyDetectedDevices)

  {

    Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("Linux_IVTV_Capture_Manager") }));



    String[] devFiles = new java.io.File("/dev/").list();

    java.util.Arrays.sort(devFiles);



    java.util.ArrayList videoDevVec = new java.util.ArrayList();

    java.util.Map devToModelMap = new java.util.HashMap();

    java.util.Map modelToDevMap = new java.util.HashMap();

    // Find the 'videoX' devs

    for (int i = 0; i < devFiles.length; i++)

    {

      try

      {

        // NOTE: Only use the first 4 video devices on linux, I'm not sure what the higher numbered ones are for

        if (devFiles[i].startsWith("video") &&

            Integer.parseInt(devFiles[i].substring(5)) < Sage.getInt("linux/max_video_dev_num", 8))

        {

          videoDevVec.add(devFiles[i]);

          devToModelMap.put(devFiles[i], getCardModelUIDForDevice("/dev/" + devFiles[i]));

          modelToDevMap.put(devToModelMap.get(devFiles[i]), devFiles[i]);

        }

      }

      catch (Exception e)

      {}

    }

    String[] videoDevs = (String[]) videoDevVec.toArray(Pooler.EMPTY_STRING_ARRAY);

    if (Sage.DBG) System.out.println("videoDevices=" + videoDevVec);



    String[] encoderKeys = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/');

    for (int i = 0; i < encoderKeys.length; i++)

    {

      if (Sage.DBG) System.out.println("Checking encoder key:" + encoderKeys[i]);

      String devClassProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +

          CaptureDevice.DEVICE_CLASS, "");

      if (devClassProp.length() > 0 && !devClassProp.equals("IVTV") && !devClassProp.equals("IVTV2"))

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

        // See if we need to upgrade from the old version

        String currDevName = Sage.get(prefs + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +

            CaptureDevice.VIDEO_CAPTURE_DEVICE_NAME, "");

        if (devClassProp.equals("IVTV"))

        {

          String newModelName = (String) devToModelMap.get(currDevName);

          if (newModelName == null)

          {

            if (Sage.DBG) System.out.println("Skipping property upgrade for IVTV device since it doesn't exist: " + currDevName);

            continue;

          }

          currDevName = newModelName;

          Sage.put(prefs + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' + CaptureDevice.VIDEO_CAPTURE_DEVICE_NAME, newModelName);

          Sage.put(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +

              CaptureDevice.DEVICE_CLASS, "IVTV2");

        }

        // Check to see if the device file is there

        if (!modelToDevMap.containsKey(currDevName))

        {

          if (Sage.DBG) System.out.println("Capture device is not available, skipping: " + currDevName);

          continue;

        }

        String broadcast = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +

            IVTVBroadcastCaptureDevice.MULTICAST_BROADCAST_ADDRESS, "");

        if (broadcast.length() > 0)

        {

          try

          {

            IVTVBroadcastCaptureDevice testEncoder = new IVTVBroadcastCaptureDevice(Integer.parseInt(encoderKeys[i]));

            if (Sage.DBG) System.out.println("Created IVTVBroadcastCaptureDevice object for:" + testEncoder);

            encoderMap.put(testEncoder.getName(), testEncoder);

          }

          catch (Exception e){System.out.println("ERROR creating capture device:" + e);}

        }

        else

        {

          try

          {

            IVTVCaptureDevice testEncoder = new IVTVCaptureDevice(Integer.parseInt(encoderKeys[i]));

            testEncoder.setLinuxVideoDevice((String) modelToDevMap.get(currDevName));

            if (Sage.DBG) System.out.println("Created IVTVCaptureDevice object for:" + testEncoder + " linked to " + testEncoder.getLinuxVideoDevice());

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

      IVTVCaptureDevice testEncoder = (IVTVCaptureDevice) walker.next();

      testEncoder.checkEncoder();

      // NOTE: At this point we assume the capture devices will function correctly

      addInputs(testEncoder);

      doneDevices.add(testEncoder.getName());

      testEncoder.writePrefs();

      Sage.savePrefs();

    }



    java.util.HashMap devNameCounter = new java.util.HashMap();

    for (int i = 0; i < videoDevs.length; i++)

    {

      if (Sage.DBG) System.out.println("Processing new system dev:" + videoDevs[i]);

      if (devNameCounter.containsKey(videoDevs[i]))

        devNameCounter.put(videoDevs[i], new Integer(((Integer)devNameCounter.get(videoDevs[i])).intValue() + 1));

      else

        devNameCounter.put(videoDevs[i], new Integer(0));

      int devCount = ((Integer)devNameCounter.get(videoDevs[i])).intValue();

      String currDevName = (String) devToModelMap.get(videoDevs[i]);

      String newCapDevName = CaptureDevice.getComplexName(currDevName, devCount);

      if (doneDevices.contains(newCapDevName))

      {

        if (Sage.DBG) System.out.println("Device already has been processed");

        //doneDevices.remove(CaptureDevice.getComplexName(videoDevices[i], devCount));

        continue;

      }

      if (CaptureDevice.isDeviceAlreadyUsed(alreadyDetectedDevices, currDevName, devCount))

      {

        if (Sage.DBG) System.out.println("Device already is used");

        continue;

      }



      IVTVCaptureDevice testEncoder = new IVTVCaptureDevice();

      testEncoder.captureDeviceName = currDevName;

      testEncoder.captureDeviceNum = devCount;

      testEncoder.setLinuxVideoDevice(videoDevs[i]);

      testEncoder.createID();

      testEncoder.checkEncoder();

      addInputs(testEncoder);

      // For now we assume they always pass the check

      if (testEncoder.isCaptureFeatureSupported(CaptureDevice.HDPVR_ENCODER_MASK))

      {

        testEncoder.setDefaultQuality(Sage.rez("Great") + "-H.264");

      }

      testEncoder.writePrefs();

      encoderMap.put(testEncoder.getName(), testEncoder);

    }



    capDevs = (IVTVCaptureDevice[]) encoderMap.values().toArray(new IVTVCaptureDevice[0]);

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



  public static native String getV4LInputName(String device, int index);

  public static native String getV4LCardType(String device);



  public static native String getCardModelUIDForDevice(String device);
  public static native int getCardI2CForDevice(String device);



  private String prefs;



  private java.util.Map encoderMap;

  private IVTVCaptureDevice[] capDevs;

  private MMC mmc;

}

