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

public final class NetworkCaptureDevice extends CaptureDevice
{
  static final String ENCODING_HOST = "encoding_host";
  private static final boolean NETWORK_ENCODER_DEBUG = Sage.getBoolean("network_encoder_debug", false);
  public NetworkCaptureDevice()
  {
    super();
  }
  public NetworkCaptureDevice(int inID)
  {
    super(inID);
    host = Sage.get(prefs + ENCODING_HOST, "");
    hostName = host.substring(0, host.indexOf(':'));
    hostPort = Integer.parseInt(host.substring(host.indexOf(':') + 1));
    fastNetSwitch = Sage.getBoolean(prefs + "fast_network_encoder_switch", false);
    // local network encoders on embedded will send messages about their format info
    dynamicFormat = true;

    // In case this network encoder was running before we started up due to a crash in SageTV we should
    // tell it to stop what it's doing (as long as its configured for use)
    for (CaptureDeviceInput cdi : srcConfigs)
    {
      if (cdi.getProviderID() != 0)
      {
        try
        {
          activateInput(cdi);
        }
        catch (EncodingException ee){}
        recFilename = "";
        stopEncoding();
      }
    }
  }

  public boolean isDynamicFormatEncoder()
  {
    return dynamicFormat;
  }

  String getLocalName()
  {
    if (myLocalName == null)
      myLocalName = captureDeviceName + (captureDeviceNum == 0 ? "" : (" #" + (captureDeviceNum + 1)));
    return myLocalName;
  }
  public String getName()
  {
    if (myName == null)
      myName = Sage.rez("Device_On_Host", new Object[] { super.getName(), host });
    return myName;
  }

  public static int createNetworkEncoderID(String deviceName, int deviceNum, String address)
  {
    return Sage.rez("Device_On_Host", new Object[] { getComplexName(deviceName, deviceNum), address }).hashCode();
  }

  private long submitLongHostRequest(String req)
  {
    try
    {
      return Long.parseLong(submitHostCommand(req));
    }
    catch (Exception e)
    {
      System.out.println("Error communicating with encoding server:" + e);
    }
    return 0;
  }

  private java.net.Socket s = null;
  private java.io.DataOutputStream outStream = null;
  private java.io.DataInputStream inStream = null;
  private long lastCommTime;

  private void connectToHost() throws java.io.IOException
  {
    s = new java.net.Socket();
    s.connect(new java.net.InetSocketAddress(hostName, hostPort), Sage.getInt("mmc/net_encoding_connection_timeout", 2000));
    if (Sage.DBG && NETWORK_ENCODER_DEBUG) System.out.println("MMC connected to encoding server at " + host);
    s.setSoTimeout(Sage.getInt("mmc/net_encoding_timeout", 15000));
    s.setTcpNoDelay(true);
    outStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(s.getOutputStream()));
    inStream = new java.io.DataInputStream(new java.io.BufferedInputStream(s.getInputStream()));
  }
  private String submitHostCommand(String req)
  {
    return submitHostCommand(req, true);
  }
  private synchronized String submitHostCommand(String req, boolean retryOK)
  {
    return submitHostCommand(req, req, retryOK);
  }
  private synchronized String submitHostCommand(String v2req, String v3req, boolean retryOK)
  {
    try
    {
      if (s == null)
      {
        connectToHost();
        retryOK = false;
      }
      else if (Sage.eventTime() - lastCommTime > Sage.getInt("mmc/net_encoding_timeout", 15000))
      {
        // If it's timed out, don't bother trying it
        closeConnection();
        connectToHost();
        retryOK = false;
      }
      if (versionString == null)
      {
        // Find out what version we're on
        //versionString = ""; // don't init this or an error below will make us wrong!
        outStream.write("VERSION\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        retryOK = true;
        versionString = Sage.readLineBytes(inStream);
        if (Sage.DBG) System.out.println("network encoder version:" + versionString);
        try
        {
          if (versionString.indexOf('.') == -1)
            version = Integer.parseInt(versionString);
          else
            version = Integer.parseInt(versionString.substring(0, versionString.indexOf('.')));
          if (Sage.DBG) System.out.println("Parsed network encoder major version: " + version);
        }
        catch (NumberFormatException e){}
      }
      outStream.write(((version >= 3 ? v3req : v2req) + "\r\n").getBytes(Sage.BYTE_CHARSET));
      outStream.flush();
      if (Sage.DBG && NETWORK_ENCODER_DEBUG) System.out.println("MMC sent request to " + host + " of " + (version >= 3 ? v3req : v2req));
      //Sage.readLineBytes(inStream);// for the password OK
      String res = Sage.readLineBytes(inStream);
      if (Sage.DBG && NETWORK_ENCODER_DEBUG) System.out.println("MMC received response from " + host + " of " + res);
      lastCommTime = Sage.eventTime();
      if (version < 2)
        closeConnection();
      return res;
    }
    catch (Exception e)
    {
      if (Sage.DBG && NETWORK_ENCODER_DEBUG) System.out.println((retryOK ? "(Retryable)" : "") + "Error communicating with encoding server:" + e);
      closeConnection();
      if (retryOK)
        return submitHostCommand(v2req, v3req, false);
    }
    return "ERROR failed request";
  }

  private void closeConnection()
  {
    try
    {
      if (inStream != null)
        inStream.close();
    }
    catch (Exception e1){System.out.println("Error1 closing:" + e1);}
    inStream = null;
    try
    {
      if (outStream != null)
        outStream.close();
    }
    catch (Exception e2){System.out.println("Error2 closing:" + e2);}
    outStream = null;
    try
    {
      if (s != null)
        s.close();
    }
    catch (Exception e3){System.out.println("Error3 closing:" + e3);}
    s = null;
  }

  public void loadDevice() throws EncodingException
  {
    if (getActiveInput() == null)
      activateInput(getDefaultInput());
    // even if its not alive, we still say it loaded correctly by not throwing an exception
  }

  public String getNetworkSourceName()
  {
    return getLocalName() + " " + (activeSource == null ? "" : activeSource.getCrossName());
  }

  private int getUploadFileID(java.io.File uploadFile)
  {
    int randy = (int)Math.round(java.lang.Math.random() * Integer.MAX_VALUE);
    MediaServer ms = SageTV.getMediaServer();
    if (ms != null)
    {
      ms.addToUploadList(uploadFile, randy);
      return randy;
    }
    else
      return 0;
  }

  public void startEncoding(CaptureDeviceInput cdi, String encodeFile, String channel) throws EncodingException
  {
    if (cdi != null)
      activateInput(cdi);
    if (channel == null || channel.length() == 0) channel = getChannel();
    if (channel == null || channel.length() == 0) channel = "2";
    if (currQuality == null || currQuality.length() == 0) currQuality = "Great";
    // Be sure to store the channel number since we're not invoking the superclass
    activeSource.setLastChannel(channel);
    activeSource.writePrefs();
    currentlyRecordingBufferSize = recordBufferSize;
    uploadID = 0;
    uploadID = getUploadFileID(new java.io.File(encodeFile));

    doPluginTune(channel);

    if (currentlyRecordingBufferSize > 0)
    {
      submitHostCommand("BUFFER " + getNetworkSourceName() + '|' + channel + '|' + currentlyRecordingBufferSize + '|' +
          encodeFile + '|' + currQuality,
          "BUFFER " + getNetworkSourceName() + '|' + (uploadID > 0 ? (uploadID + "|") : "") + channel + '|' + currentlyRecordingBufferSize + '|' +
          encodeFile + '|' + currQuality, true);
    }
    else
    {
      submitHostCommand("START " + getNetworkSourceName() + '|' + channel + '|' + 2*Sage.time() + '|' +
          encodeFile + '|' + currQuality,
          "START " + getNetworkSourceName() + '|' + (uploadID > 0 ? (uploadID + "|") : "") + channel + '|' + 2*Sage.time() + '|' +
          encodeFile + '|' + currQuality, true);
    }
    recFilename = encodeFile;
    recStart = Sage.time();
  }

  public void stopEncoding()
  {
    if (recFilename != null)
    {
      submitHostCommand("STOP", "STOP " + getNetworkSourceName(), true);
      if (uploadID > 0)
      {
        MediaServer ms = SageTV.getMediaServer();
        if (ms != null)
          ms.removeFromUploadList(new java.io.File(recFilename));
      }
      recFilename = null;
      recStart = 0;
    }
  }

  public void switchEncoding(String switchFile, String channel) throws EncodingException
  {
    java.io.File recFileLookup = new java.io.File(recFilename);

    if (uploadID > 0 && recFilename != null)
      SageTV.getMediaServer().removeFromUploadList(recFileLookup);
    if (channel == null || channel.length() == 0) channel = getChannel();
    if (channel == null || channel.length() == 0) channel = "2";
    uploadID = 0;
    uploadID = getUploadFileID(new java.io.File(switchFile));

    MediaServerRemuxer remuxer = MediaServerRemuxer.getRemuxer(recFileLookup);

    if (remuxer != null)
    {
      remuxer.startSwitch(switchFile, uploadID);
      remuxer.waitIsSwitched();
    }
    else
    {
      if (currentlyRecordingBufferSize > 0)
      {
        submitHostCommand("BUFFER_SWITCH " + channel + '|' + currentlyRecordingBufferSize + '|' + switchFile,
            "BUFFER_SWITCH " + getNetworkSourceName() + "|" + (uploadID > 0 ? (uploadID + "|") : "") + channel + '|' + currentlyRecordingBufferSize + '|' + switchFile,
            true);
      }
      else
      {
        submitHostCommand("SWITCH " + channel + '|' + switchFile,
            "SWITCH " + getNetworkSourceName() + "|" + (uploadID > 0 ? (uploadID + "|") : "") + channel + '|' + switchFile, true);
      }
    }
    recFilename = switchFile;
    recStart = Sage.time();
  }

  public void freeDevice()
  {
    // nothing to do here
  }

  protected boolean doTuneChannel(String tuneString, boolean autotune)
  {
    if (inDataScanMode)
    {
      if (startedDataScan)
        submitHostCommand("SCAN_EPG_STOP");
      submitHostCommand("SCAN_EPG_START " + tuneString + '|' + getNetworkSourceName());
      startedDataScan = true;
      return true;
    }
    if (autotune)
    {
      String res = submitHostCommand("AUTOTUNE " + tuneString, "AUTOTUNE " + getNetworkSourceName() + "|" + tuneString, true);
      return "OK".equals(res);
    }
    else
      submitHostCommand("TUNE " + tuneString, "TUNE " + getNetworkSourceName() + "|" + tuneString, true);
    return true;
  }

  protected boolean doScanChannel(String tuneString)
  {
    String res = submitHostCommand("AUTOSCAN " + tuneString, "AUTOSCAN " + getNetworkSourceName() + "|" + tuneString, true);
    return "OK".equals(res);
  }
  protected String doScanChannelInfo(String tuneString)
  {
    String res = submitHostCommand("AUTOINFOSCAN " + tuneString, "AUTOINFOSCAN " + getNetworkSourceName() + "|" + tuneString, true);
    return (res == null || "null".equals(res)) ? null : res;
  }

  public boolean isDeviceOnThisMachine()
  {
    return host.toLowerCase().startsWith("localhost") || host.startsWith("127.0.0.1");
  }

  public int getSignalStrength()
  {
    return super.getSignalStrength();
  }

  public long getRecordedBytes()
  {
    //		if (Sage.EMBEDDED && isDeviceOnThisMachine() && currentlyRecordingBufferSize == 0 && recFilename != null)
    //			return new java.io.File(recFilename).length();

    if (recFilename != null)
    {
      MediaServerRemuxer remuxer = MediaServerRemuxer.getRemuxer(new java.io.File(recFilename));

      if (remuxer != null)
        return remuxer.getFileSize();
      else
        return submitLongHostRequest("GET_FILE_SIZE " + recFilename);
    }

    return 0;
  }

  public boolean isFunctioning()
  {
    boolean rv = "OK".equals(submitHostCommand("NOOP"));
    if (!rv && !launchedMonitorThread)
    {
      if (hasConfiguredInput())
      {
        launchedMonitorThread = true;
        if (Sage.DBG) System.out.println("Launching network encoder monitor thread to kick the system once it comes up");
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            // We keep checking to see if this device is functioning yet; and once it is we kick the Scheduler so that it'll put it in the map
            while (true)
            {
              if (isFunctioning())
              {
                Scheduler.getInstance().kick(false);
                break;
              }
              try{Thread.sleep(15000);}catch(Exception e){}
            }
            launchedMonitorThread = false;
          }
        }, "NetEncoderMonitor", Thread.MIN_PRIORITY);
      }
    }
    if(Sage.getBoolean("mmc/fake_functioning_tuners", false)) return true;
    return rv;
  }

  public boolean isLoaded()
  {
    return true;
  }

  public boolean isSameCaptureDevice(String testCapDevName, int testCapDevNum)
  {
    return isDeviceOnThisMachine() && super.isSameCaptureDevice(testCapDevName, testCapDevNum);
  }

  public boolean isSameCaptureDevice(String testCapDevName, int testCapDevNum, String hostAddress)
  {
    return hostAddress.equals(host) && super.isSameCaptureDevice(testCapDevName, testCapDevNum);
  }

  public String getDeviceClass()
  {
    return "NetworkEncoder";
  }

  public boolean isNetworkEncoder()
  {
    return true;
  }

  // Overridden to disable usage of SFIRTuner since it won't be correct for us
  public int getMaxChannel(CaptureDeviceInput myInput)
  {
    if (myInput != null)
    {
      if ( ( captureFeatureBits & MMC.BDA_VIDEO_CAPTURE_MASK ) != 0 )
      {
        if ( "DVB-S".equalsIgnoreCase( myInput.getBroadcastStandard() ) )
          return 200;
        else
          if ( "DVB-C".equalsIgnoreCase( myInput.getBroadcastStandard() ) )
            return 100;
          else
            if ( "DVB-T".equalsIgnoreCase(  myInput.getBroadcastStandard() ) )
              return 100;
            else
              if ( "Cable".equalsIgnoreCase( myInput.getTuningMode() ) || "HRC".equalsIgnoreCase( myInput.getTuningMode() ) )
                return 200;
              else
                if ( "Air".equalsIgnoreCase( myInput.getTuningMode() )  )
                  return 69;
                else
                  return 100;
      }
      if (myInput.normalRF() || myInput.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
        return myInput.isCableTV() ? TVTuningFrequencies.getCableChannelMax(MMC.getInstance().getCountryCode()) :
          TVTuningFrequencies.getBroadcastChannelMax(MMC.getInstance().getCountryCode());
        if (DirecTVSerialControl.DIRECTV_SERIAL_CONTROL.equals(myInput.getTuningPlugin()))
          return 9999;

        // DO NOT invoke the SFIRTuner stuff since that'll be for a plugin on the network encoder itself

        // This means it's not a regular RF input but there's no plugin configured for it; or no device.
        // We want to return 1 for inputs w/out EPG configuration and 9999 for those channels with
        // EPG data since we can't be sure what the max # should actually be.
        return (myInput.getProviderID() > 0) ? 9999 : 1;
    }
    return 1;
  }

  public boolean supportsFastMuxSwitch()
  {
    return fastNetSwitch;
  }

  // Returns true for CaptureDevice implementations that can do data scanning on the specified input
  public boolean doesDataScanning()
  {
    return false;
  }
  // (for EPG data at this time), the input will be from this capture device AND if that input actually wants
  // a data scan to be performed
  public boolean wantsDataScanning(CaptureDeviceInput cdi)
  {
    return cdi != null && cdi.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX && requestedDataScanCDIs.contains(cdi);
  }
  // When we're in data scanning mode we're writing to the null file
  public void enableDataScanning(CaptureDeviceInput cdi) throws EncodingException
  {
    if (wantsDataScanning(cdi))
    {
      if (cdi != null)
        activateInput(cdi);
      inDataScanMode = true;
    }
  }
  public void disableDataScanning()
  {
    if (inDataScanMode)
    {
      inDataScanMode = false;
      if (startedDataScan)
        submitHostCommand("SCAN_EPG_STOP");
      startedDataScan = false;
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

  protected String host = "";
  protected String hostName;
  protected int hostPort;
  private String versionString;
  private int version;
  private String myName;
  private String myLocalName;
  private int uploadID;
  private boolean fastNetSwitch = false;
  private boolean launchedMonitorThread;
  private boolean dynamicFormat = true;
  private boolean inDataScanMode;
  private boolean startedDataScan;
  private java.util.Set requestedDataScanCDIs = new java.util.HashSet();
}
