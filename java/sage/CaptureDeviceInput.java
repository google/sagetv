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

public class CaptureDeviceInput
{
  private static final String CABLE_BASED_RF = "cable_based_rf";
  private static final String ATSC_TUNING = "atsc_tuning";
  private static final String TUNING_MODE = "tuning_mode";
  public static final String CABLE = "Cable";
  public static final String AIR = "Air";
  public static final String ATSC = "ATSC";
  public static final String FM_RADIO = "FM Radio";
  private static final String LAST_CHANNEL = "last_channel";
  private static final String PROVIDER_ID = "provider_id";
  private static final String VIDEO_CROSSBAR_TYPE = "video_crossbar_type";
  private static final String VIDEO_CROSSBAR_INDEX = "video_crossbar_index";
  private static final String DEVICE_NAME = "device_name";
  static final String BROADCAST_STANDARD = "broadcast_standard";
  public static final String BRIGHTNESS = "brightness";
  public static final String CONTRAST = "contrast";
  public static final String HUE = "hue";
  public static final String SATURATION = "saturation";
  public static final String SHARPNESS = "sharpness";
  public static final String TUNING_PLUGIN = "tuning_plugin";
  public static final String TUNING_PLUGIN_PORT = "tuning_plugin_port";
  private static final String SFIR_DEV_NAME = "sfir_dev_name";
  private static final String IR_XMT_PORT = "ir_xmt_port";
  public static final int HDMI_LINEIN_CROSSBAR_INDEX = 80;
  public static final int HDMI_AES_CROSSBAR_INDEX = 81; // HDMI Video & Audio
  public static final int HDMI_SPDIF_CROSSBAR_INDEX = 82;
  public static final int YPBPR_SPDIF_CROSSBAR_INDEX = 90;
  public static final int FM_RADIO_CROSSBAR_INDEX = 99;
  public static final int DIGITAL_TUNER_CROSSBAR_INDEX = 100;
  public static final int TV_TUNER_CROSSBAR_INDEX = 1;
  public static final int COMPOSITE_CROSSBAR_INDEX = 2;
  public static final int S_VIDEO_CROSSBAR_INDEX = 3;
  public static final int COMPONENT_CROSSBAR_INDEX = 5;
  public static String getCrossbarNameForType(int x)
  {
    if (x == TV_TUNER_CROSSBAR_INDEX)
      return Sage.rez("TV_Tuner");
    else if (x == COMPOSITE_CROSSBAR_INDEX)
      return Sage.rez("Composite");
    else if (x == S_VIDEO_CROSSBAR_INDEX)
      return Sage.rez("S_Video");
    else if (x == 4)
      return "RGB";
    else if (x == COMPONENT_CROSSBAR_INDEX)
      return Sage.rez("Component");
    else if (x == 6)
      return "HDMI"; // DShow says 'SerialDigital', but I've only ever seen this with the Hauppauge HD PVR PCIe card and on that it's HDMI
    else if (x == 7)
      return Sage.rez("Digital"); // Dshow 'PhysConn_Video_ParallelDigital'
    else if (x == 8)
      return "SCSI";
    else if (x == 9)
      return "AUX";
    else if (x == 10)
      return "1394";
    else if (x == 11)
      return "USB";
    else if (x == 12)
      return "VideoDecoder";
    else if (x == 13)
      return "VideoEncoder";
    else if (x == 14)
      return "SCART";
    else if (x == 15)
      return "Black";
    else if (x == FM_RADIO_CROSSBAR_INDEX)
      return Sage.rez("FM_Radio");
    else if (x == DIGITAL_TUNER_CROSSBAR_INDEX)
      return Sage.rez("Digital_TV_Tuner");
    else if (x == YPBPR_SPDIF_CROSSBAR_INDEX)
      return Sage.rez("Component") + "+SPDIF";
    else if (x == HDMI_LINEIN_CROSSBAR_INDEX)
      return Sage.rez("HDMI") + "+LineIn";
    else if (x == HDMI_AES_CROSSBAR_INDEX)
      return Sage.rez("HDMI") + "_AV";
    else if (x == HDMI_SPDIF_CROSSBAR_INDEX)
      return Sage.rez("HDMI") + "+SPDIF";
    else
      return Sage.rez("Unknown");
  }

  public CaptureDeviceInput(CaptureDevice inEncoder, String basePrefs, int inCrossType, int inCrossIdx)
  {
    capDev = inEncoder;
    crossbarType = inCrossType;
    crossbarIndex = inCrossIdx;
    prefs = basePrefs + crossbarType + '/' + crossbarIndex + '/';
  }
  public String getCrossName()
  {
    if (weirdRF())
      return getCrossbarNameForType(crossbarType) + " " + Sage.rez("Channel") + " " + crossbarIndex;
    else if (crossbarType == DIGITAL_TUNER_CROSSBAR_INDEX && capDev.usesFixedBroadcastStd() &&
        broadcastStd != null && broadcastStd.startsWith("DVB"))
      return getCrossbarNameForType(crossbarType) + " " + broadcastStd;
    else
      return getCrossbarNameForType(crossbarType) + (crossbarIndex > 0 ? ("_" + (crossbarIndex + 1)) : "");
  }
  private String cachedName;
  public String toString()
  {
    if (cachedName == null)
      cachedName = capDev.getName() + " " + getCrossName();
    return cachedName;
  }

  public void tuneToChannel(String chanString)
  {
    try
    {
      capDev.activateInput(this);
      capDev.tuneToChannel(chanString);
    }
    catch (EncodingException e)
    {
      System.out.println("ERROR performing channel tune for " + this + " of " + e);
    }
  }

  public boolean autoTuneChannel(String chanString)
  {
    try
    {
      capDev.activateInput(this);
      return capDev.autoTuneChannel(chanString);
    }
    catch (EncodingException e)
    {
      System.out.println("ERROR performing channel tune for " + this + " of " + e);
      return false;
    }
  }

  public boolean autoScanChannel(String chanString)
  {
    try
    {
      capDev.activateInput(this);
      if (crossbarType == DIGITAL_TUNER_CROSSBAR_INDEX)
        return capDev.ScanChannel(chanString);
      else
        return capDev.autoTuneChannel(chanString);
    }
    catch (EncodingException e)
    {
      System.out.println("ERROR performing channel tune for " + this + " of " + e);
      return false;
    }
  }

  public String ScanChannelInfo(String chanString)
  {
    try
    {
      capDev.activateInput(this);
      if (crossbarType == DIGITAL_TUNER_CROSSBAR_INDEX)
        return capDev.ScanChannelInfo(chanString);
      else
        return capDev.autoTuneChannel(chanString) ? "1" : "";
    }
    catch (EncodingException e)
    {
      System.out.println("ERROR performing channel tune for " + this + " of " + e);
      return "";
    }
  }

  public int getSignalStrength()
  {
    try
    {
      capDev.activateInput(this);
      return capDev.getSignalStrength();
    }
    catch (EncodingException e)
    {
      System.out.println("ERROR performing signal strength query for " + this + " of " + e);
      return 0;
    }
  }

  public boolean equals(Object o)
  {
    return (o instanceof CaptureDeviceInput) && ((CaptureDeviceInput)o).crossbarType == crossbarType &&
        ((CaptureDeviceInput)o).crossbarIndex == crossbarIndex &&
        ((CaptureDeviceInput)o).capDev.equals(capDev);
  }

  public void loadPrefs()
  {
    crossbarIndex = Sage.getInt(prefs + VIDEO_CROSSBAR_INDEX, 0);
    crossbarType = Sage.getInt(prefs + VIDEO_CROSSBAR_TYPE, 1);
    lastChannel = Sage.get(prefs + LAST_CHANNEL, "");
    // Check for the older version of tuning mode where we do boolean for cable & atsc
    boolean cableTV = "true".equals(Sage.get(prefs + CABLE_BASED_RF, null));
    /* boolean atscTuning = "true".equals(Sage.get(prefs + ATSC_TUNING, null)); ZQ.*/
    boolean antenna =  "false".equals(Sage.get(prefs + CABLE_BASED_RF, null)) /* && !atscTuning ZQ.*/;
    if (cableTV)
      tuningMode = CABLE;
    /*else if (atscTuning)
			tuningMode = ATSC;
     *ZQ.*/
    else if (antenna)
      tuningMode = AIR;
    else
      tuningMode = Sage.get(prefs + TUNING_MODE, CABLE);
    Sage.remove(prefs + CABLE_BASED_RF);
    Sage.remove(prefs + ATSC_TUNING);
    deviceName = Sage.get(prefs + DEVICE_NAME, "");
    providerID = Sage.getLong(prefs + PROVIDER_ID, 0);
    brightness = Sage.getInt(prefs + BRIGHTNESS, -1);
    contrast = Sage.getInt(prefs + CONTRAST, -1);
    sharpness = Sage.getInt(prefs + SHARPNESS, -1);
    hue = Sage.getInt(prefs + HUE, -1);
    saturation = Sage.getInt(prefs + SATURATION, -1);

    tuningPlugin = Sage.get(prefs + TUNING_PLUGIN, "");
    tuningPluginPort = Sage.getInt(prefs + TUNING_PLUGIN_PORT, 0);
    if (deviceName.length() > 0 && tuningPlugin.length() == 0)
    {
      if (DirecTVSerialControl.isSerialDevice(deviceName))
      {
        tuningPlugin = DirecTVSerialControl.DIRECTV_SERIAL_CONTROL;
        tuningPluginPort = Integer.parseInt(deviceName.substring(3));
      }
      else
      {
        tuningPlugin = Sage.get(MMC.MMC_KEY + '/' + SFIR_DEV_NAME, "");
        tuningPluginPort = Sage.getInt(MMC.MMC_KEY + '/' + IR_XMT_PORT, 0);
      }
    }
    broadcastStd = Sage.get(prefs + BROADCAST_STANDARD, "");
  }
  void writePrefs()
  {
    Sage.putInt(prefs + VIDEO_CROSSBAR_INDEX, crossbarIndex);
    Sage.putInt(prefs + VIDEO_CROSSBAR_TYPE, crossbarType);
    Sage.put(prefs + LAST_CHANNEL, lastChannel);
    Sage.put(prefs + TUNING_MODE, tuningMode);
    Sage.put(prefs + DEVICE_NAME, deviceName);
    Sage.put(prefs + TUNING_PLUGIN, tuningPlugin);
    Sage.putInt(prefs + TUNING_PLUGIN_PORT, tuningPluginPort);
    Sage.putLong(prefs + PROVIDER_ID, providerID);
    Sage.putInt(prefs + BRIGHTNESS, brightness);
    Sage.putInt(prefs + CONTRAST, contrast);
    Sage.putInt(prefs + HUE, hue);
    Sage.putInt(prefs + SATURATION, saturation);
    Sage.putInt(prefs + SHARPNESS, sharpness);
    Sage.put(prefs + BROADCAST_STANDARD, broadcastStd);
  }

  public CaptureDeviceInput getWeirdRFCompanion(int chanNum)
  {
    capDev.ensureInputExists(1, chanNum);
    return capDev.getInput(1, chanNum);
  }

  private String getFRQFilename()
  {
    return capDev.getCaptureDeviceName() + "-" + capDev.getCaptureDeviceNum() + "-" + getBroadcastStandard() + ".frq";
  }

  public void setProvider(long inProvID)
  {
    if (providerID == inProvID) return;
    // NOTE: For digital TV sources; if we're re-using a provider that was already scanned on another
    // device we want to copy over the .frq file which defines all the specific channel carriers.
    if (crossbarType == DIGITAL_TUNER_CROSSBAR_INDEX)
    {
      CaptureDevice[] capDevs = MMC.getInstance().getCaptureDevices();
      for (int i = 0; i < capDevs.length; i++)
      {
        if (capDevs[i] == capDev) continue;
        CaptureDeviceInput configInput = capDevs[i].getInputForProvider(inProvID);
        if (configInput != null && configInput.crossbarType == DIGITAL_TUNER_CROSSBAR_INDEX)
        {
          java.io.File oldFrqFile = new java.io.File(configInput.getFRQFilename());
          if (oldFrqFile.isFile())
          {
            // Use the same broadcast standard as the input we're cloning since the FRQ info is dependent on that. Also; inherit
            // their broadcast standard since its a good default for ours in case ours hasn't been set yet.
            java.io.File newFrqFile = new java.io.File(capDev.getCaptureDeviceName() + "-" + capDev.getCaptureDeviceNum() + "-" + configInput.getBroadcastStandard() + ".frq");
            if (!capDev.usesFixedBroadcastStd())
              setBroadcastStd(configInput.getBroadcastStandard());
            if (Sage.DBG) System.out.println("Copying FRQ file for lineup re-use from " + oldFrqFile.getAbsolutePath() +
                " to " + newFrqFile.getAbsolutePath());
            newFrqFile.delete();
            try
            {
              IOUtils.copyFile(oldFrqFile, newFrqFile);
              break;
            }
            catch (java.io.IOException e)
            {
              System.out.println("ERROR copying FRQ file of:" + e);
            }
          }
        }
      }
    }

    CaptureDeviceInput oldInput = capDev.getInputForProvider(inProvID);
    if (inProvID != 0 && oldInput != null && oldInput != this)
    {
      oldInput.providerID = 0;
      oldInput.writePrefs();
      NetworkClient.distributeRecursivePropertyChange(oldInput.prefs);
    }
    providerID = inProvID;
    writePrefs();
    NetworkClient.distributeRecursivePropertyChange(prefs);
    // We need to reset the EPG station cache so trigger a data source reload as well
    // This fixes a bug where removing a provider in a client doesn't remove the channels from the
    // EPG until the full EPG update completes.
    NetworkClient.distributeRecursivePropertyChange(EPG.EPG_DATA_SOURCES_KEY);
    EPG.getInstance().resetViewableStationsCache();
  }

  public long getDefaultProviderID()
  {
    int top32 = capDev.toString().hashCode();
    int bottom32 = crossbarType << 16; // connector num of the type
    bottom32 |= crossbarIndex;
    long rv = 0;
    rv |= top32;
    rv = rv << 32;
    rv |= bottom32;
    if (rv > 0)
      rv *= -1;
    return rv;
  }

  public String getDevice() { return deviceName; }
  public void setDevice(String x)
  {
    Sage.put(prefs + DEVICE_NAME, deviceName = x);
    if (DirecTVSerialControl.isSerialDevice(deviceName))
    {
      Sage.put(prefs + TUNING_PLUGIN, tuningPlugin = DirecTVSerialControl.DIRECTV_SERIAL_CONTROL);
      Sage.putInt(prefs + TUNING_PLUGIN_PORT, tuningPluginPort = Integer.parseInt(deviceName.substring(3)));
      NetworkClient.distributeRecursivePropertyChange(prefs);
    }
    else
      NetworkClient.distributePropertyChange(prefs + DEVICE_NAME);
  }
  public boolean weirdRF() { return crossbarType == 1 && crossbarIndex > 1; }
  public boolean normalRF() { return crossbarType == 1 && crossbarIndex <= 1; }
  public int getType() { return crossbarType; }
  public int getIndex() { return crossbarIndex; }
  public long getProviderID() { return providerID; }
  public String getTuningMode() { return tuningMode; }
  public void setTuningMode(String x)
  {
    Sage.put(prefs + TUNING_MODE, tuningMode = x);
    NetworkClient.distributePropertyChange(prefs + TUNING_MODE);
  }
  public boolean isCableTV() { return CABLE.equals(tuningMode); }
  public void setCableTV(boolean x)
  {
    setTuningMode(x ? CABLE : AIR);
  }

  public int getBrightness() { return brightness; }
  public int getContrast() { return contrast; }
  public int getHue() { return hue; }
  public int getSaturation() { return saturation; }
  public int getSharpness() { return sharpness; }
  public void setBrightness(int x)
  {
    x = Math.min(255, x); // allow -1 for default
    Sage.putInt(prefs + BRIGHTNESS, brightness = x);
    if (isActive())
    {
      Sage.putInt(prefs + BRIGHTNESS, brightness = capDev.updateColors()[0]);
    }
    NetworkClient.distributePropertyChange(prefs + BRIGHTNESS);
  }
  public void setContrast(int x)
  {
    x = Math.min(255, x); // allow -1 for default
    Sage.putInt(prefs + CONTRAST, contrast = x);
    if (isActive())
    {
      Sage.putInt(prefs + CONTRAST, contrast = capDev.updateColors()[1]);
    }
    NetworkClient.distributePropertyChange(prefs + CONTRAST);
  }
  public void setHue(int x)
  {
    x = Math.min(255, x); // allow -1 for default
    Sage.putInt(prefs + HUE, hue = x);
    if (isActive())
    {
      Sage.putInt(prefs + HUE, hue = capDev.updateColors()[2]);
    }
    NetworkClient.distributePropertyChange(prefs + HUE);
  }
  public void setSaturation(int x)
  {
    x = Math.min(255, x); // allow -1 for default
    Sage.putInt(prefs + SATURATION, saturation = x);
    if (isActive())
    {
      Sage.putInt(prefs + SATURATION, saturation = capDev.updateColors()[3]);
    }
    NetworkClient.distributePropertyChange(prefs + SATURATION);
  }
  public void setSharpness(int x)
  {
    x = Math.min(255, x); // allow -1 for default
    Sage.putInt(prefs + SHARPNESS, sharpness = x);
    if (isActive())
    {
      Sage.putInt(prefs + SHARPNESS, sharpness = capDev.updateColors()[4]);
    }
    NetworkClient.distributePropertyChange(prefs + SHARPNESS);
  }
  public void setDefaultColors(int brightness, int contrast, int hue, int saturation, int sharpness)
  {
    if (this.brightness < 0)
    {
      Sage.putInt(prefs + BRIGHTNESS, this.brightness = brightness);
      NetworkClient.distributePropertyChange(prefs + BRIGHTNESS);
    }
    if (this.contrast < 0)
    {
      Sage.putInt(prefs + CONTRAST, this.contrast = contrast);
      NetworkClient.distributePropertyChange(prefs + CONTRAST);
    }
    if (this.hue < 0)
    {
      Sage.putInt(prefs + HUE, this.hue = hue);
      NetworkClient.distributePropertyChange(prefs + HUE);
    }
    if (this.saturation < 0)
    {
      Sage.putInt(prefs + SATURATION, this.saturation = saturation);
      NetworkClient.distributePropertyChange(prefs + SATURATION);
    }
    if (this.sharpness < 0)
    {
      Sage.putInt(prefs + SHARPNESS, this.sharpness = sharpness);
      NetworkClient.distributePropertyChange(prefs + SHARPNESS);
    }
  }

  // This is used so that clients can force a channel change when doing a preview before the preview is actually started
  public void syncLastChannel()
  {
    if (Sage.client) return;
    if (lastChannel != null && lastChannel.equals(Sage.get(prefs + LAST_CHANNEL, ""))) return;
    lastChannel = Sage.get(prefs + LAST_CHANNEL, "");
    if (Sage.DBG) System.out.println("Detected lastChannel forced by properties of " + lastChannel + " for " + this);
    setLastChannel(lastChannel);
  }

  // If a client calls this then we need to set the property on the server so it picks it up on the next channel preview
  public void setLastChannel(String tuneString)
  {
    if (Sage.client)
    {
      try
      {
        SageTV.api("SetServerProperty", new Object[] { prefs + LAST_CHANNEL, tuneString } );
      }
      catch (Throwable t)
      {
        System.out.println("ERROR setting lastChannel property on server of:" + t);
      }
    }
    else
    {
      lastChannel = tuneString;
      Sage.put(prefs + LAST_CHANNEL, lastChannel);
      NetworkClient.distributePropertyChange(prefs + LAST_CHANNEL);
    }
  }

  public String getBroadcastStandard()
  {
    return broadcastStd;
  }

  protected void setBroadcastStd(String s)
  {
    if (s != null && s.startsWith("ATSC") && isCableTV())
      s = "QAM";
    Sage.put(prefs + BROADCAST_STANDARD, broadcastStd = s);
  }

  public CaptureDevice getCaptureDevice() { return capDev; }

  public int getMaxChannel()
  {
    return capDev.getMaxChannel(this);
  }

  public int getMinChannel()
  {
    return capDev.getMinChannel(this);
  }

  public boolean isActive() { return capDev.getActiveInput() == this; }

  public int getTuningPluginPort()
  {
    return tuningPluginPort;
  }
  public String getTuningPlugin()
  {
    return tuningPlugin;
  }
  public boolean usesTuningPlugin() { return tuningPlugin != null && tuningPlugin.length() > 0; }

  public sage.media.format.ContainerFormat getEncoderMediaFormat()
  {
    if (crossbarType == DIGITAL_TUNER_CROSSBAR_INDEX)
    {
      sage.media.format.ContainerFormat cf;
      // We don't know what it is in this case aside from container, so it'll get set through a message callback
      cf = new sage.media.format.ContainerFormat();
      if (Sage.getBoolean(prefs + "encode_digital_tv_as_program_stream", true))
        cf.setFormatName(sage.media.format.MediaFormat.MPEG2_PS);
      else
        cf.setFormatName(sage.media.format.MediaFormat.MPEG2_TS);
      return cf;
    }
    return capDev.getEncoderMediaFormat();
  }

  /*	public boolean supportsFastMuxSwitch()
	{
		if (crossbarType == DIGITAL_TUNER_CROSSBAR_INDEX)
			return false;
		return true;
	}
   */
  public boolean setTuningPluginAndPort(String name, int port)
  {
    if (name.length() > 0)
    {
      if (!DirecTVSerialControl.DIRECTV_SERIAL_CONTROL.equals(name))
      {
        SFIRTuner tuney = ExternalTuningManager.getIRTunerPlugin(name, port);
        if (tuney == null)
          return false;
      }
      Sage.put(prefs + TUNING_PLUGIN, tuningPlugin = name);
      Sage.putInt(prefs + TUNING_PLUGIN_PORT, tuningPluginPort = port);
      NetworkClient.distributePropertyChanges(new String[] { prefs + TUNING_PLUGIN, prefs + TUNING_PLUGIN_PORT } );
      return true;
    }
    else
    {
      Sage.put(prefs + TUNING_PLUGIN, "");
      NetworkClient.distributePropertyChange(prefs + TUNING_PLUGIN);
      return true;
    }
  }

  public String getChannel() { return lastChannel; }

  public boolean doesDataScanning()
  {
    return crossbarType == DIGITAL_TUNER_CROSSBAR_INDEX && capDev.doesDataScanning() &&
        (!Sage.getBoolean("mmc/disable_qam_epg_data_scanning", true) || !"QAM".equals(getBroadcastStandard())) &&
        (!Sage.getBoolean("mmc/disable_dvbs_epg_data_scanning", true) || !"DVB-S".equals(getBroadcastStandard()));
  }

  private int crossbarIndex = -1; // this is the RF channel if its a tuner and this is > 1
  private int crossbarType; // tuner1, comp2, s3
  private String lastChannel = "";
  private long providerID; // 0 means this source is inactive for SageTV
  private String tuningMode = "";
  private String deviceName = "";
  private String prefs;
  private final CaptureDevice capDev;
  private String tuningPlugin = "";
  private int tuningPluginPort;

  private int brightness = -1;
  private int contrast = -1;
  private int hue = -1;
  private int sharpness = -1;
  private int saturation = -1;

  private String broadcastStd = "";
}
