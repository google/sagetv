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

import jtux.*;

/**
 *
 * @author  Jean-Francois based on IVTV by Narflex
 */
public class DVBCaptureDevice extends CaptureDevice implements Runnable
{
  private int getDVBInputType(int x, int y)
  {
    return 0;
  }

  /** Creates a new instance of IVTVCaptureDevice */
  public DVBCaptureDevice()
  {
    super();
    captureFeatureBits = MMC.MPEG_AV_CAPTURE_MASK;
  }

  public DVBCaptureDevice(int inID)
  {
    super(inID);
    captureFeatureBits = MMC.MPEG_AV_CAPTURE_MASK;
  }

  public void freeDevice()
  {
    if (Sage.DBG) System.out.println("Freeing DVB capture device");
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

  public int getSignalStrength()
  {
    return isLoaded() ? getSignalStrength0(pHandle) : 0;
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

    if (Sage.DBG) System.out.println("Loading DVB capture device "+captureDeviceName +" on " + linuxVideoDevice);
    pHandle = createEncoder0(linuxVideoDevice);
    if (Sage.DBG) System.out.println("Loaded DVB capture device");

    // this is to clear the current crossbar so activate actually sets it
    activateInput(null);
    activateInput(getDefaultInput());
    setInput0(pHandle, getDVBInputType(activeSource.getType(), activeSource.getIndex()), 100, activeSource.getTuningMode(),
        MMC.getInstance().getCountryCode(), 1/*PS*/);

    Sage.put(MMC.MMC_KEY + '/' + MMC.LAST_ENCODER_NAME, getName());
  }

  public void startEncoding(CaptureDeviceInput cdi, String encodeFile, String channel) throws EncodingException
  {
    if (Sage.DBG) System.out.println("startEncoding for DVB capture device file=" + encodeFile + " chan=" + channel);

    // Rename the .lnb file properly if its been set
    java.io.File lnbFile = new java.io.File(captureDeviceName + "-" + captureDeviceNum + ".lnb");
    if (lnbFile.isFile())
    {
      java.io.File targetFile = new java.io.File(new java.io.File(linuxVideoDevice).getName() + "-" + captureDeviceNum + ".lnb");
      if (Sage.DBG) System.out.println("Copying LNB file to device specific name from " + lnbFile + " to " + targetFile);
      try
      {
        IOUtils.copyFile(lnbFile, targetFile);
      }
      catch (java.io.IOException ioe)
      {
        if (Sage.DBG) System.out.println("ERROR Failed copying LNB file for video device of:" + ioe);
      }
    }
    currRecordedBytes = 0;
    if (cdi != null)
      activateInput(cdi);
    if (channel != null)
    {
      // If the channel is nothing, then use the last one. This happens with default live.
      if (channel.length() == 0)
        channel = activeSource.getChannel();
      tuneToChannel(channel);
    }

    if (encodeParams == null)
      setEncodingQuality(currQuality);
    setEncoding0(pHandle, currQuality, (encodeParams == null) ? null : encodeParams.getOptionsMap());
    currentlyRecordingBufferSize = recordBufferSize;
    // new file
    setupEncoding0(pHandle, (encodeFile == null) ? "/dev/null" : encodeFile, currentlyRecordingBufferSize);
    recFilename = encodeFile;
    recStart = Sage.time();

    // This can change between QAM and ATSC, so we need to get it every time
    //if (broadcastStd == null || broadcastStd.length() == 0)
    activeSource.setBroadcastStd(getBroadcastStandard0(pHandle));

    // Start the encoding thread
    stopCapture = false;
    capThread = new Thread(this, "DVB-Encoder" + captureDeviceNum);
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
    if (Sage.DBG) System.out.println("stopEncoding for DVB capture device");
    recFilename = null;
    recStart = 0;
    stopCapture = true;
    if (capThread != null)
    {
      if (Sage.DBG) System.out.println("Waiting for DVB capture thread to terminate");
      try
      {
        capThread.join(5000);
      }catch (Exception e){}
    }
    capThread = null;
  }

  public void switchEncoding(String switchFile, String channel) throws EncodingException
  {
    if (Sage.DBG) System.out.println("switchEncoding for DVB capture device file=" + switchFile + " chan=" + channel);
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
    if (Sage.DBG) System.out.println("Starting DVB capture thread");
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
        int processedBytes = eatEncoderData0(pHandle);
        addtlBytes = getOutputData0(pHandle); //actual byte wroten into a file
      }
      catch (EncodingException e)
      {
        System.out.println("ERROR Eating encoder data:" + e.getMessage());
        addtlBytes = 0;
      }
      synchronized (caplock)
      {
        currRecordedBytes = addtlBytes;
      }

      if (logCapture)
        System.out.println("DVBCap " + recFilename + " " + currRecordedBytes);
    }
    closeEncoding0(pHandle);
    if (Sage.DBG) System.out.println("DVB capture thread terminating");
  }

  public CaptureDeviceInput activateInput(CaptureDeviceInput activateMe) throws EncodingException
  {
    // NOTE: This was removed so we always set the input before we start capture. There was a bug where the audio was
    // getting cut out of some recordings due to the audio standard not being set correctly. This will hopefully
    // resolve that.
    // if (activeSource == activateMe) return activeSource;
    super.activateInput(activateMe);
    if (activeSource != null && isLoaded())
    {
      boolean savePrefsAfter = (activeSource.getBrightness() < 0) || (activeSource.getContrast() < 0) ||
          (activeSource.getHue() < 0) || (activeSource.getSaturation() < 0) || (activeSource.getSharpness() < 0);

      /*synchronized (devlock)
			{
				setChannel0(pHandle, Integer.toString(activeSource.getIndex()));
			}*/ //ZQ
      int[] defaultColors = updateColors();
      activeSource.setDefaultColors(defaultColors[0], defaultColors[1], defaultColors[2], defaultColors[3],
          defaultColors[4]);

      if (savePrefsAfter)
        writePrefs();

      setInput0(pHandle, getDVBInputType(activeSource.getType(), activeSource.getIndex()), 100, activeSource.getTuningMode(),
          MMC.getInstance().getCountryCode(), 1/*PS*/);

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
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < tuneString.length(); i++)
    {
      char c = tuneString.charAt(i);
      if ((c >= '0' && c <= '9') || c == '.' || c == '-')
        sb.append(c);
    }
    tuneString = sb.toString();
    boolean rv = false;
    try
    {
      synchronized (devlock)
      {
        try
        {
          if (autotune)
            rv = setChannel0(pHandle, tuneString);
          else
            setChannel0(pHandle, tuneString);
        }
        catch (EncodingException e)
        {
          System.out.println("ERROR setting channel for DVB tuner:" + e.getMessage());
        }
      }
    }
    catch (NumberFormatException e){}
    return rv;
  }

  protected boolean doScanChannel(String tuneString)
  {
    //scanChannel0( );
    return doTuneChannel( tuneString, true );
  }

  protected String doScanChannelInfo(String tuneString)
  {
    String country = Sage.get("mmc/country", Sage.rez("default_country") );
    int country_code = TVTuningFrequencies.getCountryCode(country);
    String country_region = country;
    if ("DVB-T".equals(activeSource.getBroadcastStandard()))
      country_region += TVTuningFrequencies.doesCountryHaveDVBTRegions(country_code) ? "-" +
          Sage.get("mmc/dvbt_region", "" ) : "";
          else if ("DVB-S".equals(activeSource.getBroadcastStandard()))
            country_region += TVTuningFrequencies.doesCountryHaveDVBSRegions(country_code) ? "-" +
                Sage.get("mmc/dvbs_region", "" ) : "";
                else if ("DVB-C".equals(activeSource.getBroadcastStandard()))
                  country_region += TVTuningFrequencies.doesCountryHaveDVBCRegions(country_code) ? "-" +
                      Sage.get("mmc/dvbc_region", "" ) : "";

                      return scanChannel0( pHandle, tuneString, country_region , 1/*PS*/ );
                      //return (doTuneChannel( tuneString, true ) ? "1" : "");
  }


  public void setEncodingQuality(String encodingName)
  {
    encodingName = MMC.cleanQualityName(encodingName);
    encodeParams = MPEG2EncodingParams.getQuality(encodingName);
    currQuality = encodingName;
    writePrefs();
  }

  public boolean supportsFastMuxSwitch()
  {
    return true;
  }

  // Returns true for CaptureDevice implementations that can do data scanning on the specified input
  public boolean doesDataScanning()
  {
    return true;
  }
  // (for EPG data at this time), the input will be from this capture device AND if that input actually wants
  // a data scan to be performed
  public boolean wantsDataScanning(CaptureDeviceInput cdi)
  {
    return cdi != null && requestedDataScanCDIs.contains(cdi);
  }
  // When we're in data scanning mode we're writing to the null file
  public void enableDataScanning(CaptureDeviceInput cdi) throws EncodingException
  {
    if (wantsDataScanning(cdi))
    {
      startEncoding(cdi, null, null);
      inDataScanMode = true;
    }
  }
  public void disableDataScanning()
  {
    if (inDataScanMode)
    {
      inDataScanMode = false;
      stopEncoding();
    }
  }
  public boolean isDataScanning()
  {
    return inDataScanMode;
  }
  public boolean requestDataScan(CaptureDeviceInput cdi)
  {
    return requestedDataScanCDIs.add(cdi);
  }
  public void cancelDataScanRequest(CaptureDeviceInput cdi)
  {
    requestedDataScanCDIs.remove(cdi);
  }

  void setLinuxVideoDevice(String devPath)
  {
    linuxVideoDevice = devPath;
  }
  public String getLinuxVideoDevice()
  {
    return linuxVideoDevice;
  }

  private boolean verifyLinuxVideoDevice()
  {
    String currDevName = getCardModelUIDForDevice(linuxVideoDevice);
    if (!captureDeviceName.equals(currDevName))
    {
      if (Sage.DBG) System.out.println("DVB card no longer matches cached device path! dev=" + linuxVideoDevice + " currModel=" + currDevName);

      // While we know who *should* have the other name; swap them
      while (currDevName != null)
      {
        CaptureDevice otherCapDev = MMC.getInstance().getCaptureDeviceNamed(currDevName);
        if (otherCapDev instanceof DVBCaptureDevice)
        {
          String otherLinuxDev = ((DVBCaptureDevice) otherCapDev).getLinuxVideoDevice();
          if (Sage.DBG) System.out.println("Found other DVB card that should be using our device: " + currDevName + " check on its dev of " + otherLinuxDev);
          ((DVBCaptureDevice) otherCapDev).setLinuxVideoDevice(linuxVideoDevice);
          linuxVideoDevice = otherLinuxDev;
          currDevName = getCardModelUIDForDevice(linuxVideoDevice);
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
        if (allDevs[i] instanceof DVBCaptureDevice)
        {
          DVBCaptureDevice currDVBDev = (DVBCaptureDevice) allDevs[i];
          if (!currDVBDev.isLoaded())
          {
            currDevName = getCardModelUIDForDevice(currDVBDev.getLinuxVideoDevice());
            if (captureDeviceName.equals(currDevName))
            {
              if (Sage.DBG) System.out.println("Found other DVB device which is using our device; take it from " +
                  currDVBDev.getCaptureDeviceName() + " on device " + currDVBDev.getLinuxVideoDevice());
              String otherLinuxDev = currDVBDev.getLinuxVideoDevice();
              currDVBDev.setLinuxVideoDevice(linuxVideoDevice);
              linuxVideoDevice = otherLinuxDev;
              return true;
            }
          }
        }
      }
      if (Sage.DBG) System.out.println("Unable to find the linux device for capture device " + captureDeviceName);
      return false;
    }
    return true;
  }

  protected native long createEncoder0(String deviceName) throws EncodingException;

  protected native boolean setupEncoding0(long encoderPtr, String filename, long bufferSize) throws EncodingException;
  protected native boolean switchEncoding0(long encoderPtr, String filename) throws EncodingException;
  protected native boolean closeEncoding0(long encoderPtr);

  protected native void destroyEncoder0(long encoderPtr);

  // THIS SHOULD BE CONTINUOSLY CALLED FROM ANOTHER THREAD AFTER WE START ENCODING
  // This returns the amount of data just eaten, not the running total
  protected native int eatEncoderData0(long encoderPtr) throws EncodingException; //it's processed data bytes
  protected native long getOutputData0(long encoderPtr) throws EncodingException; //it's fwrite data bytes

  protected native boolean setChannel0(long encoderPtr, String chan) throws EncodingException;
  // Add another property to set the index we use in Linux for this
  protected native boolean setInput0(long encoderPtr, int inputType, int inputIndex, String sigFormat, int countryCode,
      int videoFormat) throws EncodingException;
  protected native boolean setEncoding0(long encoderPtr, String encodingName, java.util.Map encodingProps) throws EncodingException;
  protected native int[] updateColors0(long ptr, int brightness, int contrast, int hue, int saturation, int sharpness);
  private native String scanChannel0(long ptr, String num, String country, int streamFormat);

  private native String getBroadcastStandard0(long ptr);
  private native int getSignalStrength0(long ptr);

  public static native String getCardModelUIDForDevice(String device);

  public String getDeviceClass()
  {
    return "DVB2";
  }

  protected long pHandle;
  protected MPEG2EncodingParams encodeParams;

  protected String nextRecFilename;

  protected Object caplock = new Object();
  protected Object devlock = new Object();
  protected boolean stopCapture;
  protected Thread capThread;
  protected long currRecordedBytes;

  private boolean inDataScanMode;
  private java.util.Set requestedDataScanCDIs = new java.util.HashSet();

  protected String linuxVideoDevice;
}
