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
package sage.api;

import sage.*;
import sage.epg.sd.SDRipper;
import sage.epg.sd.json.lineup.SDAccountLineup;

/**
 * Represents an specific input on a CaptureDevice such as the TV Tuner, Composite or S-Video inputs
 */
public class CaptureDeviceInputAPI{
  private CaptureDeviceInputAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetInfraredTuningPlugin", new String[] {"CaptureDeviceInput"}, true)
    {
      /**
       * Gets the name of the tuning plugin used for this CaptureDeviceInput
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the name of the tuning plugin currently used for the specified CaptureDeviceInput
       *
       * @declaration public String GetInfraredTuningPlugin(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String pluggy = getCapDevInput(stack).getTuningPlugin();
        if (pluggy != null && pluggy.length() > 0)
          return SFIRTuner.getPrettyNameForFile(pluggy);
        else
          return "";
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetInfraredTuningPluginPortNumber", new String[] {"CaptureDeviceInput"}, true)
    {
      /**
       * Gets the port number used by the tuning plugin for this CaptureDeviceInput
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the port number of the tuning plugin currently used for the specified CaptureDeviceInput
       *
       * @declaration public int GetInfraredTuningPluginPortNumber(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(getCapDevInput(stack).getTuningPluginPort());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "SetInfraredTuningPluginAndPort", new String[]{"CaptureDeviceInput","PluginName","PluginPortNumber"}, true)
    {
      /**
       * Sets the name and port number for the tuning plugin for a CaptureDeviceInput
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @param PluginName the name of the tuning plugin to use on the specified CaptureDeviceInput. This should be a value from {@link Configuration#GetInfraredTuningPlugins GetInfraredTuningPlugins()} Use the emptry string "" to set the CaptureDeviceInput to not use a plugin
       * @param PluginPortNumber the port number to configure the specified tuning plugin to use on the specified CaptureDeviceInput. Use 0 for the USB port.
       * @return true if the plugin was setup (if it requires hardware this validates the hardware is connected)
       *
       * @declaration public boolean SetInfraredTuningPluginAndPort(String CaptureDeviceInput, String PluginName, int PluginPortNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int portNum = getInt(stack);
        String prettyirdev = getString(stack);
        return Boolean.valueOf(getCapDevInput(stack).setTuningPluginAndPort(
            SFIRTuner.getFileForPrettyDeviceName(prettyirdev), portNum));
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "AutoTuneChannelTest", new String[]{"CaptureDeviceInput","ChannelNumber"}, true)
    {
      /**
       * Tunes the CaptureDeviceInput to the specified physical channel and indicates whether or not a signal is present. This call should only
       * be used if the CaptureDeviceInput is already under live control (i.e. {@link MediaPlayerAPI#WatchLive WatchLive()} or {@link MediaPlayerAPI#LockTuner LockTuner()} was called on it) or
       * if the input has not been configured for use yet. Otherwise this call may interfere with what is currently being recorded.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @param ChannelNumber the channel string to tune to
       * @return true if the hardware detected a signal on the specified channel
       *
       * @declaration public boolean AutoTuneChannelTest(String CaptureDeviceInput, String ChannelNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String chan = getString(stack);
        return getCapDevInput(stack).autoTuneChannel(chan) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "AutoScanChannelInfo", new String[]{"CaptureDeviceInput","ChannelNumber"}, true)
    {
      /**
       * Tunes the CaptureDeviceInput to the specified physical channel and returns a list of the available channels. This call should only
       * be used if the CaptureDeviceInput is already under live control (i.e. {@link MediaPlayerAPI#WatchLive WatchLive()} or {@link MediaPlayerAPI#LockTuner LockTuner()} was called on it) or
       * if the input has not been configured for use yet. Otherwise this call may interfere with what is currently being recorded.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @param ChannelNumber the channel string to tune to
       * @return a string describing the subchannels found scanning this channel, if no channels were found an empty or null string will be returned
       *
       * @declaration public String AutoScanChannelInfo(String CaptureDeviceInput, String ChannelNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String chan = getString(stack);
        return getCapDevInput(stack).ScanChannelInfo(chan);
      }});

    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetInputMinimumChannelNumber", new String[]{"CaptureDeviceInput"}, true)
    {
      /**
       * Returns the minimum channel number that this CaptureDeviceInput can tune to
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the minimum channel number for the specified CaptureDeviceInput
       *
       * @declaration public int GetInputMinimumChannelNumber(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(getCapDevInput(stack).getMinChannel());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetInputMaximumChannelNumber", new String[]{"CaptureDeviceInput"}, true)
    {
      /**
       * Returns the maximum channel number that this CaptureDeviceInput can tune to
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the maximum channel number for the specified CaptureDeviceInput
       *
       * @declaration public int GetInputMaximumChannelNumber(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(getCapDevInput(stack).getMaxChannel());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "SetCaptureBrightness", 2, new String[] {"CaptureDeviceInput","Value(0-255)"}, true)
    {
      /**
       * Sets the brightness for capture on this CaptureDeviceInput. This only affects analog capture devices
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @param Value the new value to set the brightness to, in the inclusive range 0-255. Use -1 to set it to the default.
       *
       * @declaration public void SetCaptureBrightness(String CaptureDeviceInput, int Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int v = getInt(stack);
        CaptureDeviceInput conn = getCapDevInput(stack);
        if (conn != null)
          conn.setBrightness(v);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "SetCaptureSaturation", 2, new String[] {"CaptureDeviceInput","Value(0-255)"}, true)
    {
      /**
       * Sets the saturation for capture on this CaptureDeviceInput. This only affects analog capture devices
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @param Value the new value to set the saturation to, in the inclusive range 0-255. Use -1 to set it to the default.
       *
       * @declaration public void SetCaptureSaturation(String CaptureDeviceInput, int Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int v = getInt(stack);
        CaptureDeviceInput conn = getCapDevInput(stack);
        if (conn != null)
          conn.setSaturation(v);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "SetCaptureHue", 2, new String[] {"CaptureDeviceInput","Value(0-255)"}, true)
    {
      /**
       * Sets the hue for capture on this CaptureDeviceInput. This only affects analog capture devices.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @param Value the new value to set the hue to, in the inclusive range 0-255. Use -1 to set it to the default.
       *
       * @declaration public void SetCaptureHue(String CaptureDeviceInput, int Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int v = getInt(stack);
        CaptureDeviceInput conn = getCapDevInput(stack);
        if (conn != null)
          conn.setHue(v);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "SetCaptureContrast", 2, new String[] {"CaptureDeviceInput","Value(0-255)"}, true)
    {
      /**
       * Sets the contrast for capture on this CaptureDeviceInput. This only affects analog capture devices.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @param Value the new value to set the contrast to, in the inclusive range 0-255. Use -1 to set it to the default.
       *
       * @declaration public void SetCaptureContrast(String CaptureDeviceInput, int Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int v = getInt(stack);
        CaptureDeviceInput conn = getCapDevInput(stack);
        if (conn != null)
          conn.setContrast(v);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "SetCaptureSharpness", 2, new String[] {"CaptureDeviceInput","Value(0-255)"}, true)
    {
      /**
       * Sets the sharpness for capture on this CaptureDeviceInput. This only affects analog capture devices.
       * NOTE: On Linux this currently sets the audio capture volume level
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @param Value the new value to set the sharpness to, in the inclusive range 0-255. Use -1 to set it to the default.
       *
       * @declaration public void SetCaptureSharpness(String CaptureDeviceInput, int Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int v = getInt(stack);
        CaptureDeviceInput conn = getCapDevInput(stack);
        if (conn != null)
          conn.setSharpness(v);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetCaptureBrightness", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Gets the brightness level for this CaptureDeviceInput. This is only valid for analog capture devices.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the brightness level for the specified CaptureDeviceInput in the inclusive range 0-255
       *
       * @declaration public int GetCaptureBrightness(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput conn = getCapDevInput(stack);
        return new Integer(conn == null ? 0 : conn.getBrightness());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetCaptureSaturation", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Gets the saturation level for this CaptureDeviceInput. This is only valid for analog capture devices.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the saturation level for the specified CaptureDeviceInput in the inclusive range 0-255
       *
       * @declaration public int GetCaptureSaturation(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput conn = getCapDevInput(stack);
        return new Integer(conn == null ? 0 : conn.getSaturation());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetCaptureHue", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Gets the hue level for this CaptureDeviceInput. This is only valid for analog capture devices.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the hue level for the specified CaptureDeviceInput in the inclusive range 0-255
       *
       * @declaration public int GetCaptureHue(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput conn = getCapDevInput(stack);
        return new Integer(conn == null ? 0 : conn.getHue());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetCaptureContrast", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Gets the contrast level for this CaptureDeviceInput. This is only valid for analog capture devices.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the contrast level for the specified CaptureDeviceInput in the inclusive range 0-255
       *
       * @declaration public int GetCaptureContrast(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput conn = getCapDevInput(stack);
        return new Integer(conn == null ? 0 : conn.getContrast());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetCaptureSharpness", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Gets the sharpness level for this CaptureDeviceInput. This is only valid for analog capture devices.
       * NOTE: On Linux this gets the audio volume level
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the sharpness level for the specified CaptureDeviceInput in the inclusive range 0-255
       *
       * @declaration public int GetCaptureSharpness(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput conn = getCapDevInput(stack);
        return new Integer(conn == null ? 0 : conn.getSharpness());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "SetInfraredTunerRemoteName", 2, new String[] {"CaptureDeviceInput", "ExternalDeviceName"}, true)
    {
      /**
       * Sets the name of the device that is passed to the IR Tuner plugin for tuning control. Corresponds to a .ir file for current IR transmitters
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @param ExternalDeviceName the name of the external device the IR tuning plugin is supposed to control This value should be obtained from a call to (@link Configuration#GetRemotesForInfraredTuningPlugin GetRemotesForInfraredTuningPlugin()}
       *
       * @declaration public void SetInfraredTunerRemoteName(String CaptureDeviceInput, String ExternalDeviceName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String dev = getString(stack);
        getCapDevInput(stack).setDevice(dev); return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetInfraredTunerRemoteName", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Returns the name of the device that is passed to the IR Tuner plugin for tuning control. Corresponds to a .ir file for current IR transmitters.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the name of the external device codes used for the tuner plugin on the specified CaptureDeviceInput
       *
       * @declaration public String GetInfraredTunerRemoteName(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getCapDevInput(stack).getDevice();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "SetRFSignalIsCableTV", 2, new String[] {"CaptureDeviceInput", "True/False"}, true)
    {
      /**
       * Sets whether or not this CaptureDeviceInput tunes for Antenna or Cable if it's a TV Tuner input
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @param Value true if this input is connected to a Cable source, false if it uses Broadcast/Over-the-Air (OTA)
       *
       * @declaration public void SetRFSignalIsCableTV(String CaptureDeviceInput, boolean Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean b = evalBool(stack.pop());
        getCapDevInput(stack).setCableTV(b); return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "IsRFSignalCableTV", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Returns whether or not this CaptureDeviceInput tunes for Antenna or Cable if it's a TV Tuner input
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return true if this input is connected to a Cable source, false if it uses Broadcast/Over-the-Air (OTA)
       *
       * @declaration public boolean IsRFSignalCableTV(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(getCapDevInput(stack).isCableTV());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "IsExternallyTunedRFInput", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Returns true if this input was created using {@link CaptureDeviceAPI#AddInputForRFChannel AddInputForRFChannel()} method call
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return true if this input was created using {@link CaptureDeviceAPI#AddInputForRFChannel AddInputForRFChannel()} method call
       *
       * @declaration public boolean IsExternallyTunedRFInput(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(getCapDevInput(stack).weirdRF());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetConstantRFChannelInput", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Returns the RF channel number that is used to receive the source signal. This is set using {@link CaptureDeviceAPI#AddInputForRFChannel AddInputForRFChannel()}
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the RF channel number used to receive the source signal on the specified CaptureDeviceInput
       *
       * @declaration public int GetConstantRFChannelInput(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(getCapDevInput(stack).getIndex());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "IsCaptureDeviceInputAudioVideo", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Returns whether or not this CaptureDeviceInput captures both audio and video
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return true if this input captures both audio and video, false if it just captures video
       *
       * @declaration public boolean IsCaptureDeviceInputAudioVideo(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput cdi = getCapDevInput(stack);
        if (cdi != null)
        {
          // All HW encoders are audio/video capture devices so pass those
          if (cdi.getCaptureDevice().isHWEncoder())
            return Boolean.TRUE;
          // Digital TV inputs are multiplexed
          if (cdi.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
            return Boolean.TRUE;
          // A/V combo capture devices are multiplexed
          if (cdi.getCaptureDevice().isCaptureFeatureSupported(MMC.RAW_AV_CAPTURE_MASK))
            return Boolean.TRUE;
          return Boolean.FALSE;
        }
        return Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetPhysicalInputType", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Returns the type of input this is, such as: S-Video, Composite, TV Tuner, etc.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the type of physical connector used for the specified CaptureDeviceInput
       *
       * @declaration public String GetPhysicalInputType(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput cdi = getCapDevInput(stack);
        return (cdi == null) ? "" : CaptureDeviceInput.getCrossbarNameForType(cdi.getType());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetCaptureDeviceInputName", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Returns the name of this CaptureDeviceInput connection without the CaptureDevice name prefixing it. This is not the
       * same as the 'name' of the CaptureDeviceInput used as the parameter. The String that uniquely identifies a CaptureDeviceInput
       * must always have the CaptureDevice's name included in it. Only use this return value for display purposes; do not use
       * it for anything else.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the name of the specified CaptureDeviceInput connection without the CaptureDevice name prefixing it
       *
       * @declaration public String GetCaptureDeviceInputName(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getCapDevInput(stack).getCrossName();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "ConfigureInputForEPGDataLineup", 2, new String[] {"CaptureDeviceInput", "Lineup"}, true)
    {
      /**
       * Configures this CaptureDeviceInput to use the specified EPG Lineup.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput to set the lineup on
       * @param Lineup the name of the Lineup to configure this CaptureDeviceInput for. This name should be obtained from a call to the EPG server
       * @return true if the Lineup was successfully configured for the specified CaptureDeviceInput
       *
       * @declaration public boolean ConfigureInputForEPGDataLineup(String CaptureDeviceInput, String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String lineup = getString(stack);
        EPG epg = EPG.getInstance();
        long provID = epg.getProviderIDForEPGDSName(lineup);
        long serverProvID;

        if (lineup != null && lineup.endsWith(SDRipper.SOURCE_LABEL))
        {
          if (provID != 0)
          {
            EPGDataSource ds = epg.getSourceForProviderID(provID);
            if (ds != null)
              ds.setEnabled(true);
          }
          else
          {
            SDAccountLineup accountLineup = SDRipper.getAccountLineup(lineup.substring(0, lineup.length() - SDRipper.SOURCE_LABEL.length()));
            if (accountLineup == null)
              return Boolean.FALSE;

            provID = SDRipper.getHashFromAccountLineup(accountLineup);
            if (provID == 0)
              return Boolean.FALSE;

            int newDataSourceID = Wizard.getInstance().getNextWizID();
            while (epg.getSourceForID(newDataSourceID) != null)
              newDataSourceID = Wizard.getInstance().getNextWizID();

            SDRipper DataSource = new SDRipper(newDataSourceID);
            DataSource.setName(lineup);
            DataSource.setProviderID(provID);
            DataSource.setLineupID(accountLineup.getLineup());
            DataSource.setEnabled(true);
            epg.addDataSource(DataSource);
          }
        }
        // Sometimes the provider ID will change, but not the name. I don't know why Tribune does this; but we need to account for that case.
        else if (((serverProvID = epg.getCachedProviderIDForName(lineup)) != 0 && provID != serverProvID) || provID == 0)
        {
          // We haven't created a datasource for this provider ID yet. We need to
          // do that now since its going to be used.
          provID = serverProvID;
          if (provID == 0)
            // 601 patch here!
            return Boolean.FALSE;

          // Check if there's already an EPGDataSource with this provider ID, if so, then update its name
          EPGDataSource oldEPGDS = epg.getSourceForProviderID(provID);
          if (oldEPGDS != null)
          {
            oldEPGDS.setName(lineup);
            NetworkClient.distributeRecursivePropertyChange("epg_data_sources/");
          }
          else
          {
            int newDSID = Wizard.getInstance().getNextWizID();
            while (epg.getSourceForID(newDSID) != null)
              newDSID = Wizard.getInstance().getNextWizID();
            String customDS = Sage.get("epg/custom_data_source_class", "");
            if (customDS.length() > 0)
            {
              Class epgClass = Class.forName(customDS);
              java.lang.reflect.Constructor cstr = epgClass.getDeclaredConstructor(Integer.TYPE);
              EPGDataSource ds = (EPGDataSource) cstr.newInstance(newDSID);
              ds.setName(lineup);
              ds.setProviderID(provID);
              ds.setEnabled(true);
              epg.addDataSource(ds);
            }
            else
            {
              EPGDataSource ds = new WarlockRipper(newDSID);
              ds.setName(lineup);
              ds.setProviderID(provID);
              ds.setEnabled(true);
              epg.addDataSource(ds);
            }
          }
        }
        else
        {
          EPGDataSource ds = epg.getSourceForProviderID(provID);
          if (ds != null)
            ds.setEnabled(true);
        }
        getCapDevInput(stack).setProvider(provID);
        Scheduler.getInstance().kick(false);
        return Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "ConfigureInputWithoutEPGData", 1, new String[] {"CaptureDeviceInput"}, true)
    {
      /**
       * Configures this CaptureDeviceInput to not use an EPG data source. It will instead create a generic lineup with numeric channels that
       * @param CaptureDeviceInput the name of the CaptureDeviceInput to use
       * @return true (the call will always succeed)
       *
       * @declaration public boolean ConfigureInputWithoutEPGData(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        EPG epg = EPG.getInstance();
        CaptureDeviceInput cdi = getCapDevInput(stack);
        long provID = cdi.getDefaultProviderID();
        EPGDataSource ds = epg.getSourceForProviderID(provID);
        if (ds == null)
        {
          // We haven't created a datasource for this provider ID yet. We need to
          // do that now since its going to be used.
          int newDSID = Wizard.getInstance().getNextWizID();
          while (epg.getSourceForID(newDSID) != null)
            newDSID = Wizard.getInstance().getNextWizID();
          ds = new EPGDataSource(newDSID);
          ds.setName(cdi.toString());
          ds.setProviderID(provID);
          ds.setEnabled(true);
          epg.addDataSource(ds);
        }
        else
          ds.setEnabled(true);
        cdi.setProvider(provID);
        Scheduler.getInstance().kick(false);
        return Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "ReleaseCaptureDeviceInput", 1, new String[] {"CaptureDeviceInput"}, true)
    {
      /**
       * Releases this CaptureDeviceInput from its currently configured lineup. It will no longer be considered "configured" or "active".
       * If its lineup is no longer is in use, it will be cleaned up on the next EPG maintenance cycle.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput to use
       *
       * @declaration public void ReleaseCaptureDeviceInput(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        getCapDevInput(stack).setProvider(0);
        Scheduler.getInstance().kick(false);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetCaptureDeviceInputBeingViewed")
    {
      /**
       * Returns the CaptureDeviceInput that is recording the MediaFile that is currently loaded by the MediaPlayer
       * @return the CaptureDeviceInput that is recording the MediaFile that is currently loaded by the MediaPlayer
       *
       * @declaration public String GetCaptureDeviceInputBeingViewed();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        // NOTE: THIS IS A SPECIAL EXCEPTION TO THE WAY WE DO NETWORK CALLS BECAUSE THERE'S AN IMPLIED
        // ARGUMENT RELATIVE TO WHO THE CLIENT IS
        MediaFile mf = (stack.getUIMgrSafe() != null) ? stack.getUIMgrSafe().getVideoFrame().getCurrFile() : null;
        if (mf == null) return null;
        sage.jep.function.PostfixMathCommandI targetMeth =
            (sage.jep.function.PostfixMathCommandI) Catbert.getAPI().
            get("GetCaptureDeviceInputRecordingMediaFile");
        stack.push(mf);
        targetMeth.setCurNumberOfParameters(1);
        targetMeth.run(stack);
        return stack.pop();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetCaptureDeviceInputRecordingMediaFile", 1, new String[] { "MediaFile" }, true)
    {
      /**
       * Returns the CaptureDeviceInput that is recording the specified MediaFile
       * @param MediaFile the MediaFile who's recording CaptureDeviceInput should be returned
       * @return the CaptureDeviceInput that is recording the specified MediaFile; null if that file is not being recorded currently
       *
       * @declaration public String GetCaptureDeviceInputRecordingMediaFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object rv = Seeker.getInstance().getInputForCurrRecordingFile(getMediaFile(stack));
        return (rv == null) ? null : rv.toString();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetLineupForCaptureDeviceInput", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Returns the name of the lineup that this CaptureDeviceInput is configured to use
       * @param CaptureDeviceInput the name of the CaptureDeviceInput to use
       * @return the name of the lineups that is configured for use on the specified CaptureDeviceInput; returns null if the input is not configured
       *
       * @declaration public String GetLineupForCaptureDeviceInput(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput cdi = getCapDevInput(stack);
        EPGDataSource ds = cdi != null ? EPG.getInstance().getSourceForProviderID(cdi.getProviderID()) : null;
        if (ds != null)
          return ds.getName();
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetCaptureDeviceForInput", 1, new String[] {"CaptureDeviceInput"})
    {
      /**
       * Returns the CaptureDevice for this CaptureDeviceInput
       * @param CaptureDeviceInput the name of the CaptureDeviceInput to use
       * @return the name of the CaptureDevice for the specified CaptureDeviceInput
       *
       * @declaration public String GetCaptureDeviceForInput(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput cdi = getCapDevInput(stack);
        return (cdi == null) ? null : cdi.getCaptureDevice().toString();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetSignalStrength", 1, new String[] {"CaptureDeviceInput"}, true)
    {
      /**
       * Returns the current signal strength for this CaptureDeviceInput. This is only valid for Digital TV inputs.
       * The returned value will be between 0 and 100 inclusive. 0 is no signal and 100 is maximum signal strength.
       * @param CaptureDeviceInput the name of the CaptureDeviceInput to use
       * @return the current signal strength on the specified CaptureDeviceInput
       *
       * @declaration public int GetSignalStrength(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput cdi = getCapDevInput(stack);
        return (cdi == null) ? new Integer(0) : new Integer(Math.min(100, Math.max(0, cdi.getSignalStrength())));
      }});
    rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "GetCaptureDeviceInputBroadcastStandard", 1, new String[] {"CaptureDeviceInput"}, true)
    {
      /**
       * Returns the name of the broadcast standard used for reception on this capture device input (can be different per-input)
       * @param CaptureDeviceInput the name of the CaptureDeviceInput
       * @return the name of the broadcast standard used for reception on this capture device input (i.e. NTSC, ATSC, DVB-S, etc.)
       * @since 7.0
       *
       * @declaration public String GetCaptureDeviceInputBroadcastStandard(String CaptureDeviceInput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput cdi = getCapDevInput(stack);
        return cdi == null ? null : cdi.getBroadcastStandard();
      }});
    /*
		rft.put(new PredefinedJEPFunction("CaptureDeviceInput", "", 1, new String[] {"CaptureDeviceInput"})
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return getCapDevInput(stack);
			}});
     */
  }
}
