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
public class FirewireCaptureDevice extends CaptureDevice implements Runnable
{
  private int getFirewireInputType(int x, int y)
  {
    return 0;
  }

  /** Creates a new instance of IVTVCaptureDevice */
  public FirewireCaptureDevice()
  {
    super();
    captureFeatureBits = MMC.MPEG_AV_CAPTURE_MASK;
  }

  public FirewireCaptureDevice(int inID)
  {
    super(inID);
    captureFeatureBits = MMC.MPEG_AV_CAPTURE_MASK;
  }

  public boolean isDynamicFormatEncoder()
  {
    return true;
  }

  public void freeDevice()
  {
    if (Sage.DBG) System.out.println("Freeing Firewire capture device");
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
    if (Sage.DBG) System.out.println("Loading Firewire capture device "+captureDeviceName);
    pHandle = createEncoder0(captureDeviceName);
    if (Sage.DBG) System.out.println("Loaded Firewire capture device");

    // this is to clear the current crossbar so activate actually sets it
    activateInput(null);
    activateInput(getDefaultInput());

    Sage.put(MMC.MMC_KEY + '/' + MMC.LAST_ENCODER_NAME, getName());
  }

  public void startEncoding(CaptureDeviceInput cdi, String encodeFile, String channel) throws EncodingException
  {
    if (Sage.DBG) System.out.println("startEncoding for Firewire capture device file=" + encodeFile + " chan=" + channel);
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
        try {
          Thread.sleep(tuneWaitDelay);
        } catch (Exception e) {}
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
    capThread = new Thread(this, "Firewire-Encoder" + captureDeviceNum);
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
    if (Sage.DBG) System.out.println("stopEncoding for Firewire capture device");
    recFilename = null;
    recStart = 0;
    stopCapture = true;
    if (capThread != null)
    {
      if (Sage.DBG) System.out.println("Waiting for Firewire capture thread to terminate");
      try
      {
        capThread.join(5000);
      }catch (Exception e){}
    }
    capThread = null;
  }

  public void switchEncoding(String switchFile, String channel) throws EncodingException
  {
    if (Sage.DBG) System.out.println("switchEncoding for IVTV capture device file=" + switchFile + " chan=" + channel);
    if (channel != null)
      tuneToChannel(channel);
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
    if (Sage.DBG) System.out.println("Starting Firewire capture thread");
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
        System.out.println("FirewireCap " + recFilename + " " + currRecordedBytes);
    }
    closeEncoding0(pHandle);
    if (Sage.DBG) System.out.println("Firewire capture thread terminating");
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
        setChannel0(pHandle, Integer.toString(activeSource.getIndex()));
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
          System.out.println("ERROR setting channel for Firewire tuner:" + e.getMessage());
        }
      }
    }
    catch (NumberFormatException e){}
    return rv;
  }
  protected boolean doScanChannel(String tuneString)
  {
    return doTuneChannel( tuneString, true);
  }
  protected String doScanChannelInfo(String tuneString)
  {
    return (doTuneChannel( tuneString, true ) ? "1" : "");
  }

  public void setEncodingQuality(String encodingName)
  {
    encodingName = MMC.cleanQualityName(encodingName);
    encodeParams = MPEG2EncodingParams.getQuality(encodingName);
    currQuality = encodingName;
    writePrefs();
  }

  public sage.media.format.ContainerFormat getEncoderMediaFormat()
  {
    sage.media.format.ContainerFormat cf = new sage.media.format.ContainerFormat();
    cf.setFormatName(sage.media.format.MediaFormat.MPEG2_TS);
    return cf;
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
    return "LinuxFirewire";
  }

  protected long pHandle;
  protected MPEG2EncodingParams encodeParams;

  protected String nextRecFilename;

  protected Object caplock = new Object();
  protected Object devlock = new Object();
  protected boolean stopCapture;
  protected Thread capThread;
  protected long currRecordedBytes;
}
