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
public class MacNativeCaptureDevice extends CaptureDevice implements Runnable
{
  /** Creates a new instance of MacNativeCaptureDevice */
  boolean gotCaptureFeatureBits = false;

  public MacNativeCaptureDevice()
  {
    super();
    captureFeatureBits = MMC.MPEG_AV_CAPTURE_MASK;// | MMC.LIVE_PREVIEW_MASK; // fake bits for now
    gotCaptureFeatureBits = false;
  }

  public MacNativeCaptureDevice(int inID)
  {
    super(inID);
    // poll device using getDeviceCaps0 when we connect
    captureFeatureBits = getDeviceCaps0(captureDeviceName, captureDeviceNum);
    gotCaptureFeatureBits = true;
  }

  public int getCaptureFeatureBits() {
    //System.out.println("getCaptureFeatureBits called");
    // if this CaptureDevice is instantiated without an ID, we'll need to make sure we actually poll the native device first
    if(!gotCaptureFeatureBits) {
      captureFeatureBits = getDeviceCaps0(captureDeviceName, captureDeviceNum);
      gotCaptureFeatureBits = true;
    }
    return captureFeatureBits;
  }

  public boolean isCaptureFeatureSupported(int testMe) {
    if(!gotCaptureFeatureBits) {
      captureFeatureBits = getDeviceCaps0(captureDeviceName, captureDeviceNum);
      gotCaptureFeatureBits = true;
    }
    return (testMe & captureFeatureBits) == testMe;
  }

  public void freeDevice()
  {
    if (Sage.DBG) System.out.println("Freeing MacNative capture device");
    try { activateInput(null); } catch (EncodingException e){}
    writePrefs();
    stopEncoding();
    destroyEncoder0(pHandle);
    pHandle = 0;
  }

  /* Support for custom QT encode settings */
  /*	public java.util.Map getEncodingOptions(String qualityProfile)
	{
		System.out.println("MacNativeCaptureDevice.getEncodingOptions: "+qualityProfile);

		if(qualityProfile.equalsIgnoreCase("QT Movie - Best") ||
		   qualityProfile.equalsIgnoreCase("QT Movie - Great") ||
		   qualityProfile.equalsIgnoreCase("QT Movie - Good") ||
		   qualityProfile.equalsIgnoreCase("QT Movie - Fair"))
		{
			java.util.Map optionsMap = new java.util.LinkedHashMap();
			optionsMap.put("audiooutputmode", "0"); // 0x03 = mono, 0x00 = stereo, 0x02 = dual, 0x01 = joint
			optionsMap.put("videobitrate", "4000000");
			optionsMap.put("inversetelecine", "0");
			optionsMap.put("outputstreamtype", "102");
			optionsMap.put("width", "720");
			optionsMap.put("height", "480");
			optionsMap.put("audiobitrate", "384");
			optionsMap.put("deinterlace", "0");
			optionsMap.put("aspectratio", "1"); // 0 = 1:1, 1 = 4:3, 2 = 16:9
			return optionsMap;
		}

		return super.getEncodingOptions(qualityProfile);
	}

	public boolean setEncodingOptions(String qualityProfile, java.util.Map optionsMap) { return false; }
	public boolean removeEncodingQuality(String qualityProfile) { return false; }
   */
  /*	public void setEncodingQuality(String qualityProfile)
	{
		System.out.println("MacNativeCaptureDevice.setEncodingQuality: "+qualityProfile);
		super.setEncodingQuality(qualityProfile);
	}
   */
  /*	public String getEncodingQuality()
	{

	}
   */
  /*	public void applyQuality() throws EncodingException
	{
		System.out.println("MacNativeCaptureDevice.applyQuality: "+currQuality);
	}

	public String[] getEncodingQualities()
	{
		System.out.println("MacNativeCaptureDevice.getEncodingQualities");
		String[] Qs = {"QT Movie - Best", "QT Movie - Great", "QT Movie - Good", "QT Movie - Fair"};
		return Qs;
	}
   */

  /* uncomment for debugging channel scanning... */
  /*	public int getMaxChannel(CaptureDeviceInput myInput)
	{
		return 19;
	}

	public int getMinChannel(CaptureDeviceInput myInput)
	{
		return 12;
	} */

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
    if (Sage.DBG) System.out.println("Loading MacNative capture device");
    pHandle = createEncoder0(captureDeviceName, captureDeviceNum);

    // this is to clear the current crossbar so activate actually sets it
    activateInput(null);
    activateInput(getDefaultInput());

    Sage.put(MMC.MMC_KEY + '/' + MMC.LAST_ENCODER_NAME, getName());
  }

  public void addInputs()
  {
    // get the input map from the native driver
    int[] inputs = getInputMap0(captureDeviceName, captureDeviceNum); // two entries per input, first = type, second = index
    int ii;

    // make sure it's not null, and actually has at least two entries...
    if((inputs != null) ? (inputs.length >= 2) : false) {
      ii = 0;
      System.out.println("Got " + inputs.length/2 + " inputs");

      while(ii < inputs.length) {
        System.out.println("Input " + ii / 2 + ": type = " + inputs[ii] + ", index = " + inputs[ii+1]);
        ensureInputExists(inputs[ii], inputs[ii+1]);
        ii += 2;
      }
    }
  }

  public void startEncoding(CaptureDeviceInput cdi, String encodeFile, String channel) throws EncodingException
  {
    if (Sage.DBG) System.out.println("startEncoding for "+captureDeviceName+" ("+captureDeviceNum+") file=" + encodeFile + " chan=" + channel);
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
    setupEncoding0(pHandle, encodeFile, currentlyRecordingBufferSize);
    recFilename = encodeFile;
    recStart = Sage.time();
    freshCapture = true;

    // Start the encoding thread
    stopCapture = false;
    capThread = new Thread(this, captureDeviceName+"-"+captureDeviceNum+"-Encoder");
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
    if (Sage.DBG) System.out.println("stopEncoding for "+captureDeviceName+" ("+captureDeviceNum+")");
    recFilename = null;
    recStart = 0;
    stopCapture = true;
    if (capThread != null)
    {
      if (Sage.DBG) System.out.println("Waiting for capture thread to terminate");
      try
      {
        capThread.join(5000);
      }catch (Exception e){}
    }
    capThread = null;
  }

  public void switchEncoding(String switchFile, String channel) throws EncodingException
  {
    if (Sage.DBG) System.out.println("switchEncoding for "+captureDeviceName+" ("+captureDeviceNum+") file=" + switchFile + " chan=" + channel);
    if (channel != null)
      tuneToChannel(channel);
    freshCapture = false;
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
    if (Sage.DBG) System.out.println("Starting capture thread for "+captureDeviceName+" ("+captureDeviceNum+")");
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
        System.out.println(captureDeviceName+" ("+captureDeviceNum+") " + recFilename + " " + currRecordedBytes);

      // sleep for a bit to allow some data to accumulate
      try {
        Thread.sleep(250);	// might throw InterruptedException (?)
      } catch (Throwable t) {}
    }
    closeEncoding0(pHandle);
    if (Sage.DBG) System.out.println(captureDeviceName+" ("+captureDeviceNum+") capture thread terminating");
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

      synchronized (devlock)
      {
        setInput0(pHandle, activeSource.getType(), activeSource.getIndex(), activeSource.getTuningMode(),
            MMC.getInstance().getCountryCode(), MMC.getInstance().getVideoFormatCode());
      }

      // reconfigure the capture device caps when the input is changed
      captureFeatureBits = getDeviceCaps0(captureDeviceName, captureDeviceNum);

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
      // analog, digital or FM tuner type
      if ((activeSource.getType() == 1)
          || (activeSource.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
          || (activeSource.getType() == CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX))
      {
        synchronized (devlock)
        {
          try
          {
            //						if (activeSource.getIndex() > 1)
            //						{
            //							if (autotune)
            //								rv = setChannel0(pHandle, Integer.toString(activeSource.getIndex()), true, getTunerStreamType());
            //							else
            //								setChannel0(pHandle, Integer.toString(activeSource.getIndex()), false, getTunerStreamType());
            //						}
            //						else
            //						{
            if (autotune)
              rv = setChannel0(pHandle, tuneString, true, getTunerStreamType());
            else
              setChannel0(pHandle, tuneString, false, getTunerStreamType());
            //						}
          }
          catch (EncodingException e)
          {
            System.out.println("ERROR setting channel for "+captureDeviceName+" ("+captureDeviceNum+") tuner:" + e.getMessage());
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
      else // must be external tuner type
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

  private int getTunerStreamType()
  {
    if (activeSource != null && activeSource.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
    {
      if (Sage.getBoolean("mmc/encode_digital_tv_as_program_stream", true))
        return MediaFile.MEDIASUBTYPE_MPEG2_PS;
      else
        return MediaFile.MEDIASUBTYPE_MPEG2_TS;
    }
    else
      return 0;
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

                      return scanChannel0( pHandle, tuneString, country_region, getTunerStreamType());
                      //		return (doTuneChannel( tuneString, true ) ? "1" : "");
  }

  public int getSignalStrength()
  {
    return getSignalStrength0(pHandle);
  }

  private void doPluginTune(String tuneString)
  {
    if (!DirecTVSerialControl.DIRECTV_SERIAL_CONTROL.equals(activeSource.getTuningPlugin()))
    {
      SFIRTuner tunePlug = ExternalTuningManager.getIRTunerPlugin(activeSource.getTuningPlugin(),
          activeSource.getTuningPluginPort());
      if (tunePlug != null)
        tunePlug.playTuneString(activeSource.getDevice(), tuneString);
    }
    else
      ExternalTuningManager.getDirecTVSerialControl().tune(activeSource.getTuningPluginPort() == 0 ?
          activeSource.getDevice() : ("COM" + activeSource.getTuningPluginPort()) , tuneString);
  }

  public void setEncodingQuality(String encodingName)
  {
    encodingName = MMC.cleanQualityName(encodingName);
    encodeParams = MPEG2EncodingParams.getQuality(encodingName);
    currQuality = encodingName;
    writePrefs();
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

  // array of int pairs:
  // - first value is crossbar type as defined in CaptureDeviceInput.java
  // - second value is the number of inputs of that type
  // WARNING: this is called before the device is opened!!!
  protected native int[] getInputMap0(String deviceName, int deviceNum);
  protected native int getDeviceCaps0(String capDevName, int capDevNum); // loads up captureDeviceBits...

  protected native int isCaptureDeviceValid0(String devName, int devNum);
  protected native long createEncoder0(String deviceName, int deviceNum) throws EncodingException;
  protected native void destroyEncoder0(long encoderPtr);

  protected native boolean setupEncoding0(long encoderPtr, String filename, long bufferSize) throws EncodingException;
  protected native boolean switchEncoding0(long encoderPtr, String filename) throws EncodingException;
  protected native boolean closeEncoding0(long encoderPtr);


  // THIS SHOULD BE CONTINUOSLY CALLED FROM ANOTHER THREAD AFTER WE START ENCODING
  // This returns the amount of data just eaten, not the running total
  protected native int eatEncoderData0(long encoderPtr) throws EncodingException;

  protected native boolean setChannel0(long encoderPtr, String chan, boolean autotune, int streamFormat) throws EncodingException;
  protected native String scanChannel0(long encoderPtr, String channelName, String region, int streamFormat);

  // Add another property to set the index we use in Linux for this
  protected native boolean setInput0(long encoderPtr, int inputType, int inputIndex, String sigFormat, int countryCode,
      int videoFormat) throws EncodingException;
  protected native boolean setEncoding0(long encoderPtr, String encodingName, java.util.Map encodingProps) throws EncodingException;
  protected native int[] updateColors0(long encoderPtr, int brightness, int contrast, int hue, int saturation, int sharpness);

  protected native String getBroadcastStandard0(long encoderPtr);
  protected native int getSignalStrength0(long encoderPtr);

  public String getDeviceClass()
  {
    return "MacNative";
  }
  public boolean isFreshCapture()
  {
    return freshCapture;
  }
  protected long pHandle;
  protected MPEG2EncodingParams encodeParams;

  protected String nextRecFilename;

  protected Object caplock = new Object();
  protected Object devlock = new Object();
  protected boolean stopCapture;
  protected Thread capThread;
  protected long currRecordedBytes;
  protected boolean freshCapture;

  private boolean inDataScanMode = false;
  private java.util.Set requestedDataScanCDIs = new java.util.HashSet();
}
