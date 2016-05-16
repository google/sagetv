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

public class IVTVCaptureDevice extends CaptureDevice implements Runnable

{

  private int getV4LInputType(int x, int y)

  {

    int orgy = y;

    boolean is250=false;

    int pvr_fd = -1;

    String devName="/dev/"+linuxVideoDevice;

    String inputName;

    String v4lCardType = LinuxIVTVCaptureManager.getV4LCardType(devName);

    if(v4lCardType==null) v4lCardType="Unknown";



    int inputindex=0;

    while((inputName = LinuxIVTVCaptureManager.getV4LInputName(devName, inputindex))!=null)

    {

      if (Sage.DBG) System.out.println("V4L: "+ captureDeviceName + " input "+

				inputindex+" name is "+ inputName);

      if ((inputName.toLowerCase().startsWith("compos") &&

          x == CaptureDeviceInput.COMPOSITE_CROSSBAR_INDEX) ||

          (inputName.toLowerCase().startsWith("tuner") &&

              x == CaptureDeviceInput.TV_TUNER_CROSSBAR_INDEX) ||

              (inputName.toLowerCase().startsWith("tele") &&

                  x == CaptureDeviceInput.TV_TUNER_CROSSBAR_INDEX) ||

                  (inputName.toLowerCase().startsWith("s") &&

                      inputName.toLowerCase().indexOf("video") != -1 &&

                      x == CaptureDeviceInput.S_VIDEO_CROSSBAR_INDEX) ||

                      (inputName.toLowerCase().startsWith("compon") &&

                          x == CaptureDeviceInput.COMPONENT_CROSSBAR_INDEX) ||

                          (inputName.toLowerCase().startsWith("compon") &&

                              x == CaptureDeviceInput.YPBPR_SPDIF_CROSSBAR_INDEX)

          )

      {

        if (y == 0)

        {

          if (Sage.DBG)

            System.out.println("Matched SageTV input type=" + x +

                " index=" + orgy + " to v4l input #" + inputindex);

          return inputindex;

        }

        else

          y--;

      }

      inputindex++;

    }

    // Default case, use first input

    return 0;

  }



  /** Creates a new instance of V4LCaptureDevice */

  public IVTVCaptureDevice()

  {

    super();

    captureFeatureBits = MMC.MPEG_AV_CAPTURE_MASK;

    String devName="/dev/"+linuxVideoDevice;

    try

    {

      if(LinuxIVTVCaptureManager.getV4LCardType(devName).toLowerCase().indexOf("hd pvr")!=-1)

        captureFeatureBits|=CaptureDevice.HDPVR_ENCODER_MASK;

    }

    catch(Exception e)

    {

      System.out.println(e);

    }

  }



  public IVTVCaptureDevice(int inID)

  {

    super(inID);

    captureFeatureBits = MMC.MPEG_AV_CAPTURE_MASK;

    String devName="/dev/"+linuxVideoDevice;

    try

    {

      if(LinuxIVTVCaptureManager.getV4LCardType(devName).toLowerCase().indexOf("hd pvr")!=-1)

        captureFeatureBits|=CaptureDevice.HDPVR_ENCODER_MASK;

    }

    catch(Exception e)

    {

      System.out.println(e);

    }

  }



  public void freeDevice()

  {

    if (Sage.DBG) System.out.println("Freeing ITVTV capture device");

    try { activateInput(null); } catch (EncodingException e){}

    writePrefs();

    stopEncoding();

    destroyEncoder0(pHandle);

    pHandle = 0;

  }



  public long getRecordedBytes()

  {

    synchronized (caplock)

    {

      return currRecordedBytes;

    }

  }



  public boolean isLoaded()

  {

    return pHandle != 0;

  }



  public void loadDevice() throws EncodingException

  {

    // Verify this is the correct device

    if (!verifyLinuxVideoDevice())

    {

      throw new EncodingException(EncodingException.CAPTURE_DEVICE_INSTALL, 0);

    }

    if (Sage.DBG) System.out.println("Loading V4L capture device " + captureDeviceName + " on " + linuxVideoDevice);

    pHandle = createEncoder0(linuxVideoDevice);



    // this is to clear the current crossbar so activate actually sets it

    activateInput(null);

    activateInput(getDefaultInput());



    Sage.put(MMC.MMC_KEY + '/' + MMC.LAST_ENCODER_NAME, getName());

  }



  public void startEncoding(CaptureDeviceInput cdi, String encodeFile, String channel) throws EncodingException

  {

    if (Sage.DBG) System.out.println("startEncoding for V4L capture device file=" + encodeFile + " chan=" + channel);

    currRecordedBytes = 0;

    if (cdi != null)

      activateInput(cdi);

    if (channel != null)

    {

      // If the channel is nothing, then use the last one. This happens with default live.

      if (channel.length() == 0)

        channel = activeSource.getChannel();

      tuneToChannel(channel);

      long tuneWaitDelay = getPostTuningDelay();

      if (tuneWaitDelay > 0)

      {

        try{Thread.sleep(tuneWaitDelay);}catch(Exception e){}

      }

    }



    if (encodeParams == null)

      setEncodingQuality(currQuality);

    setEncoding0(pHandle, currQuality, (encodeParams == null) ? null : encodeParams.getOptionsMap());

    currentlyRecordingBufferSize = recordBufferSize;

    // new file

    setupEncoding0(pHandle, encodeFile, currentlyRecordingBufferSize);

    recFilename = encodeFile;

    recStart = Sage.time();



    // Start the encoding thread

    stopCapture = false;

    capThread = new Thread(this, "V4L-Encoder-" + linuxVideoDevice);

    capThread.setPriority(Thread.MAX_PRIORITY);

    capThread.start();



    // Initially this may not be set so do so now.

    if ((activeSource.getChannel() != null && activeSource.getChannel().length() == 0) ||

        activeSource.getChannel().equals("0"))

    {

      activeSource.setLastChannel(getChannel());

      activeSource.writePrefs();

    }

  }



  public void stopEncoding()

  {

    if (Sage.DBG) System.out.println("stopEncoding for V4L capture device");

    recFilename = null;

    recStart = 0;

    stopCapture = true;

    if (capThread != null)

    {

      if (Sage.DBG) System.out.println("Waiting for V4L capture thread to terminate");

      try

      {

        capThread.join(5000);

      }catch (Exception e){}

    }

    capThread = null;

  }



  public void switchEncoding(String switchFile, String channel) throws EncodingException

  {

    if (Sage.DBG) System.out.println("switchEncoding for V4L capture device file=" + switchFile + " chan=" + channel);

    // We don't set the channel anymore on switches now for seamless playback
    //		if (channel != null)

    //			tuneToChannel(channel);

    synchronized (caplock)

    {

      nextRecFilename = switchFile;

      while (nextRecFilename != null)

      {

        // Wait for the new filename to be switched to and then return after that

        try{caplock.wait(10);}catch (Exception e){}

      }

    }

  }



  public void run()

  {

    boolean logCapture = Sage.getBoolean("debug_capture_progress", false);

    if (Sage.DBG) System.out.println("Starting V4L capture thread");

    long addtlBytes;

    while (!stopCapture)

    {

      if (nextRecFilename != null)

      {

        try

        {

          switchEncoding0(pHandle, nextRecFilename);

        }

        catch (EncodingException e)

        {

          System.out.println("ERROR Switching encoder file:" + e.getMessage());

        }

        synchronized (caplock)

        {

          recFilename = nextRecFilename;

          nextRecFilename = null;

          recStart = Sage.time();

          currRecordedBytes = 0;

          // There may be a thread waiting on this state change to occur

          caplock.notifyAll();

        }

      }

      try

      {

        addtlBytes = eatEncoderData0(pHandle);

      }

      catch (EncodingException e)

      {

        System.out.println("ERROR Eating encoder data:" + e.getMessage());

        addtlBytes = 0;

      }

      synchronized (caplock)

      {

        currRecordedBytes += addtlBytes;

      }



      if (logCapture)

        System.out.println("V4LCap " + recFilename + " " + currRecordedBytes);

    }

    closeEncoding0(pHandle);

    if (Sage.DBG) System.out.println("V4L capture thread terminating");

  }



  public CaptureDeviceInput activateInput(CaptureDeviceInput activateMe) throws EncodingException

  {

    // NOTE: This was removed so we always set the input before we start capture. There was a bug where the audio was

    // getting cut out of some recordings due to the audio standard not being set correctly. This will hopefully

    // resolve that.

    if (activeSource == activateMe && recFilename != null) return activeSource;

    super.activateInput(activateMe);

    if (activeSource != null && isLoaded())

    {

      boolean savePrefsAfter = (activeSource.getBrightness() < 0) || (activeSource.getContrast() < 0) ||

          (activeSource.getHue() < 0) || (activeSource.getSaturation() < 0) || (activeSource.getSharpness() < 0);



      synchronized (devlock)

      {

        if (activeSource.getType() == 1 && activeSource.getIndex() > 1) // tuner cross

        {

          setInput0(pHandle, getV4LInputType(activeSource.getType(), activeSource.getIndex()), 0, activeSource.getTuningMode(),

              MMC.getInstance().getCountryCode(), MMC.getInstance().getVideoFormatCode());

          setChannel0(pHandle, Integer.toString(activeSource.getIndex()));

        }

        else if (activeSource.getType() == CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX)

        {

          setInput0(pHandle, getV4LInputType(1, activeSource.getIndex()) /*tuner*/, activeSource.getIndex(), CaptureDeviceInput.FM_RADIO,

              MMC.getInstance().getCountryCode(), MMC.getInstance().getVideoFormatCode());

        }

        else

        {

          // Index modified for HDPVR case of optical audio input

          setInput0(pHandle, getV4LInputType(activeSource.getType(), activeSource.getIndex()),

              activeSource.getIndex() +

              ((activeSource.getType()==CaptureDeviceInput.YPBPR_SPDIF_CROSSBAR_INDEX) ? 1 : 0),

              activeSource.getTuningMode(),

              MMC.getInstance().getCountryCode(), MMC.getInstance().getVideoFormatCode());

        }

      }

      int[] defaultColors = updateColors();

      activeSource.setDefaultColors(defaultColors[0], defaultColors[1], defaultColors[2], defaultColors[3],

          defaultColors[4]);



      if (savePrefsAfter)

        writePrefs();

    }

    return activeSource;

  }



  public int[] updateColors()

  {

    synchronized (devlock)

    {

      return updateColors0(pHandle, activeSource.getBrightness(), activeSource.getContrast(),

          activeSource.getHue(), activeSource.getSaturation(), activeSource.getSharpness());

    }

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

      if (activeSource.getType() == 1)

      {

        synchronized (devlock)

        {

          try

          {

            if (activeSource.getIndex() > 1)

            {

              if (autotune)

                rv = setChannel0(pHandle, Integer.toString(activeSource.getIndex()));

              else

                setChannel0(pHandle, Integer.toString(activeSource.getIndex()));

            }

            else

            {

              if (autotune)

                rv = setChannel0(pHandle, tuneString);

              else

                setChannel0(pHandle, tuneString);

            }

          }

          catch (EncodingException e)

          {

            System.out.println("ERROR setting channel for V4L tuner:" + e.getMessage());

          }

        }

        // Fixup any issue with the last channel being set incorrectly from a WatchLive call which would then

        // cause the always_tune_channel to potentially skip it

        if (activeSource.getTuningPlugin().length() > 0 &&

            (Sage.getBoolean(MMC.MMC_KEY + '/' + MMC.ALWAYS_TUNE_CHANNEL, true) ||

                !tuneString.equals(getChannel()) ||

                recordBufferSize > 0))

        {

          doPluginTune(tuneString);

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

          doPluginTune(tuneString);

        }

        rv = true;

      }

    }

    catch (NumberFormatException e){}

    return rv;

  }



  protected boolean doScanChannel(String tuneString)

  {

    return doTuneChannel( tuneString, true );

  }



  protected String doScanChannelInfo(String tuneString)

  {

    return (doTuneChannel( tuneString, true ) ? "1" : "");

  }


  @Override
  protected void doPluginTune(String tuneString)
  {
    String plugName = activeSource.getTuningPlugin();
    int plugPort = activeSource.getTuningPluginPort();
    SFIRTuner tunePlug;

    if (plugName != null && plugName.endsWith("PVR150Tuner.so") && !Sage.getBoolean("linux/disable_auto_i2c_port_matching", false))
    {
      // Dynamically get the I2C address to make sure it's right
      plugPort = LinuxIVTVCaptureManager.getCardI2CForDevice("/dev/" + linuxVideoDevice);
      if (plugPort < 0) // then use the configured one
        plugPort = activeSource.getTuningPluginPort();

      tunePlug = ExternalTuningManager.getIRTunerPlugin(plugName, plugPort);
      if (tunePlug == null && plugPort != activeSource.getTuningPluginPort())
      {
        if (Sage.DBG) System.out.println("Failed using auto-detected i2c port of " + plugPort + " and reverting to default port of " + activeSource.getTuningPluginPort());
          plugPort = activeSource.getTuningPluginPort();
      }
    }

    super.doPluginTune(tuneString, plugPort);
  }



  public void setEncodingQuality(String encodingName)

  {

    encodingName = MMC.cleanQualityName(encodingName);

    encodeParams = MPEG2EncodingParams.getQuality(encodingName);

    currQuality = encodingName;

    writePrefs();

  }



  int checkEncoder()

  {

    String devName="/dev/"+linuxVideoDevice;

    System.out.println("V4L: Testing type for "+devName);

    try

    {

      if(LinuxIVTVCaptureManager.getV4LCardType(devName).toLowerCase().indexOf("hd pvr")!=-1)

        captureFeatureBits|=CaptureDevice.HDPVR_ENCODER_MASK;

    }

    catch(Exception e)

    {

      System.out.println(e);

    }

    return 1;

  }

  void setLinuxVideoDevice(String devPath)

  {

    linuxVideoDevice = devPath;

  }

  String getLinuxVideoDevice()

  {

    return linuxVideoDevice;

  }



  private boolean verifyLinuxVideoDevice()

  {

    String currDevName = LinuxIVTVCaptureManager.getCardModelUIDForDevice("/dev/" + linuxVideoDevice);

    if (!captureDeviceName.equals(currDevName))

    {

      if (Sage.DBG) System.out.println("IVTV card no longer matches cached device path! dev=" + linuxVideoDevice + " currModel=" + currDevName);



      // While we know who *should* have the other name; swap them

      while (currDevName != null)

      {

        CaptureDevice otherCapDev = MMC.getInstance().getCaptureDeviceNamed(currDevName);

        if (otherCapDev instanceof IVTVCaptureDevice)

        {

          String otherLinuxDev = ((IVTVCaptureDevice) otherCapDev).getLinuxVideoDevice();

          if (Sage.DBG) System.out.println("Found other IVTV card that should be using our device: " + currDevName + " check on its dev of " + otherLinuxDev);

          ((IVTVCaptureDevice) otherCapDev).setLinuxVideoDevice(linuxVideoDevice);

          linuxVideoDevice = otherLinuxDev;

          currDevName = LinuxIVTVCaptureManager.getCardModelUIDForDevice("/dev/" + linuxVideoDevice);

          if (captureDeviceName.equals(currDevName))

            return true;

        }

        else

          break;

      }



      // Go through the whole capture device list and check on any that aren't loaded as well

      CaptureDevice[] allDevs = MMC.getInstance().getCaptureDevices();

      for (int i = 0; i < allDevs.length; i++)

      {

        if (allDevs[i] instanceof IVTVCaptureDevice)

        {

          IVTVCaptureDevice currIVTVDev = (IVTVCaptureDevice) allDevs[i];

          if (!currIVTVDev.isLoaded())

          {

            currDevName = LinuxIVTVCaptureManager.getCardModelUIDForDevice("/dev/" + currIVTVDev.getLinuxVideoDevice());

            if (captureDeviceName.equals(currDevName))

            {

              if (Sage.DBG) System.out.println("Found other IVTV device which is using our device; take it from " +

								currIVTVDev.getCaptureDeviceName() + " on device " + currIVTVDev.getLinuxVideoDevice());

              String otherLinuxDev = currIVTVDev.getLinuxVideoDevice();

              currIVTVDev.setLinuxVideoDevice(linuxVideoDevice);

              linuxVideoDevice = otherLinuxDev;

              return true;

            }

          }

        }

      }

      // Go through all the video devices again to try to find it...it may have moved to one we didn't initially use
      if (Sage.DBG) System.out.println("Searching all /dev/videoX devices to try to find our capture device");
      String[] devFiles = new java.io.File("/dev/").list();
      java.util.Arrays.sort(devFiles);
      for (int i = 0; i < devFiles.length; i++)
      {
        try
        {
          // NOTE: Only use the first 8 video devices on linux, I'm not sure what the higher numbered ones are for
          if (devFiles[i].startsWith("video") &&
              Integer.parseInt(devFiles[i].substring(5)) < Sage.getInt("linux/max_video_dev_num", 8))
          {
            currDevName = LinuxIVTVCaptureManager.getCardModelUIDForDevice("/dev/" + devFiles[i]);
            if (captureDeviceName.equals(currDevName))
            {
              if (Sage.DBG) System.out.println("Found our capture device at: " + devFiles[i] + " for: " + captureDeviceName);
              linuxVideoDevice = devFiles[i];
              return true;
            }
          }
        }
        catch (Exception e)
        {}
      }


      if (Sage.DBG) System.out.println("Unable to find the linux device for capture device " + captureDeviceName);

      return false;

    }

    return true;

  }

  // We support fast mux switching for all IVTV-based capture devices (MPEG2 or H264)
  public boolean supportsFastMuxSwitch()
  {
    return true;
  }

  protected native long createEncoder0(String deviceName) throws EncodingException;



  protected native boolean setupEncoding0(long encoderPtr, String filename, long bufferSize) throws EncodingException;

  protected native boolean switchEncoding0(long encoderPtr, String filename) throws EncodingException;

  protected native boolean closeEncoding0(long encoderPtr);



  protected native void destroyEncoder0(long encoderPtr);



  // THIS SHOULD BE CONTINUOSLY CALLED FROM ANOTHER THREAD AFTER WE START ENCODING

  // This returns the amount of data just eaten, not the running total

  protected native int eatEncoderData0(long encoderPtr) throws EncodingException;



  protected native boolean setChannel0(long encoderPtr, String chan) throws EncodingException;

  // Add another property to set the index we use in Linux for this

  protected native boolean setInput0(long encoderPtr, int inputType, int inputIndex, String sigFormat, int countryCode,

      int videoFormat) throws EncodingException;

  protected native boolean setEncoding0(long encoderPtr, String encodingName, java.util.Map encodingProps) throws EncodingException;

  protected native int[] updateColors0(long ptr, int brightness, int contrast, int hue, int saturation, int sharpness);



  public String getDeviceClass()

  {

    return "IVTV2";

  }



  protected long pHandle;

  protected MPEG2EncodingParams encodeParams;



  protected String nextRecFilename;



  protected Object caplock = new Object();

  protected Object devlock = new Object();

  protected boolean stopCapture;

  protected Thread capThread;

  protected long currRecordedBytes;



  protected String linuxVideoDevice;

}

