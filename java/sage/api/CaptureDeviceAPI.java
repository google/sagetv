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

/**
 * Represents a capture card or network encoder which is used to record TV.
 */
public class CaptureDeviceAPI{
  private CaptureDeviceAPI() {}

  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetCaptureDevices")
    {
      /**
       * Returns all of the CaptureDevices in the system that SageTV can use
       * @return the names of all of the CaptureDevices in the system that SageTV can use
       *
       * @declaration public String[] GetCaptureDevices();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return MMC.getInstance().getCaptureDeviceNames(/*MMC.MPEG_VIDEO_ONLY_CAPTURE_MASK |
        MMC.MPEG_VIDEO_RAW_AUDIO_CAPTURE_MASK | MMC.MPEG_AV_CAPTURE_MASK | MMC.MPEG_PURE_CAPTURE_MASK*/);
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetCaptureDeviceInputs", new String[] { "CaptureDevice" })
    {
      /**
       * Returns all of the CaptureDeviceInputs for a given CaptureDevice.
       * @param CaptureDevice the name of the CaptureDevice
       * @return all of the CaptureDeviceInputs for the specified CaptureDevice.
       *
       * @declaration public String[] GetCaptureDeviceInputs(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getCapDev(stack).getInputNames();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetConfiguredCaptureDeviceInputs")
    {
      /**
       * Returns all of the CaptureDeviceInputs that are currently configured for use by SageTV.
       * @return the names of all of the CaptureDeviceInputs that are currently configured for use by SageTV.
       *
       * @declaration public String[] GetConfiguredCaptureDeviceInputs();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return MMC.getInstance().getConfiguredInputNames();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "IsCaptureDeviceFunctioning", 1, new String[] {"CaptureDevice"}, true)
    {
      /**
       * Returns whether or not a CaptureDevice is functioning (i.e. the device is offline)
       * @param CaptureDevice the name of the CaptureDevice
       * @return false if a CaptureDevice is NOT functioning (i.e. the device is offline), otherwise true
       *
       * @declaration public boolean IsCaptureDeviceFunctioning(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(getCapDev(stack).isFunctioning());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "IsCaptureDeviceANetworkEncoder", 1, new String[] {"CaptureDevice"}, true)
    {
      /**
       * Returns true if a CaptureDevice is a Network Encoder
       * @param CaptureDevice the name of the CaptureDevice
       * @return true if the specified CaptureDevice is a Network Encoder
       *
       * @declaration public boolean IsCaptureDeviceANetworkEncoder(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(getCapDev(stack).isNetworkEncoder());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetActiveCaptureDevices", true)
    {
      /**
       * Returns all of the CaptureDevices that are currently configured for use by SageTV
       * @return all of the CaptureDevices that are currently configured for use by SageTV
       *
       * @declaration public String[] GetActiveCaptureDevices();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SchedulerSelector.getInstance().getMyEncoderNames();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "IsCaptureDeviceInUseByALiveClient", 1, new String[] {"CaptureDevice"}, true)
    {
      /**
       * Returns true if the CaptureDevice is currently under control of a client who is (or was) watching live TV
       * @param CaptureDevice the name of the CaptureDevice
       * @return true if the specified CaptureDevice is currently under control of a client who is watching live/delayed TV
       *
       * @declaration public boolean IsCaptureDeviceInUseByALiveClient(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(SeekerSelector.getInstance().isAClientControllingCaptureDevice(getCapDev(stack)));
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "AddInputForRFChannel", 2, new String[] {"CaptureDevice", "RFChannel"}, true)
    {
      /**
       * Returns a CaptureDeviceInput that corresponds to using the tuner input on the CaptureDevice locked to a certain channel.
       * For example, using the RF connection from your cable box to the capture card on channel 3 would required adding a new input this way.
       * @param CaptureDevice the name of the CaptureDevice to add the new input to
       * @param RFChannel the channel to tune to for this RF input
       * @return the name of the CaptureDeviceInput that was created which will act as an RF channel input
       *
       * @declaration public String AddInputForRFChannel(String CaptureDevice, int RFChannel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int rfChan = getInt(stack);
        CaptureDeviceInput[] cdis = getCapDev(stack).getInputs();
        for (int i = 0; i < cdis.length; i++)
        {
          if (cdis[i].normalRF())
            return cdis[i].getWeirdRFCompanion(rfChan);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetLastUsedCaptureDevice", true)
    {
      /**
       * Returns the last CaptureDevice that was accessed by SageTV.
       * @return the name of the last CaptureDevice that was accessed by SageTV.
       *
       * @declaration public String GetLastUsedCaptureDevice();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object rv = MMC.getInstance().getPreferredCaptureDevice();
        return (rv == null) ? null : rv.toString();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetLastUsedCaptureDeviceInput", 1, new String[] {"CaptureDevice"}, true)
    {
      /**
       * Returns the last CaptureDeviceInput that was used by SageTV on the given CaptureDevice
       * @param CaptureDevice the name of the CaptureDevice
       * @return the name of the last CaptureDeviceInput that was used by SageTV on the given CaptureDevice
       *
       * @declaration public String GetLastUsedCaptureDeviceInput(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDevice capDev = getCapDev(stack);
        Object rv = (capDev == null) ? null : capDev.getDefaultInput();
        return (rv == null) ? null : rv.toString();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetCaptureDeviceCurrentRecordFile", 1, new String[] {"CaptureDevice"}, true)
    {
      /**
       * Returns the file that is currently being recorded by this capture device
       * @param CaptureDevice the name of the CaptureDevice
       * @return the file that is currently being recorded by the specified capture device
       *
       * @declaration public MediaFile GetCaptureDeviceCurrentRecordFile(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getCurrRecordMediaFile(getCapDev(stack));
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetCaptureDeviceQualities", 1, new String[] {"CaptureDevice"}, true)
    {
      /**
       * Returns the recording qualities which are supported by this CaptureDevice
       * @param CaptureDevice the name of the CaptureDevice
       * @return the recording qualities which are supported by the specified CaptureDevice
       *
       * @declaration public String[] GetCaptureDeviceQualities(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDevice capDev = getCapDev(stack);
        return capDev == null ? null : capDev.getEncodingQualities();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetCaptureDeviceDefaultQuality", new String[] {"CaptureDevice"}, true)
    {
      /**
       * Returns the default recording qualities for this CaptureDevice.
       * @param CaptureDevice the name of the CaptureDevice
       * @return the default recording quality for the specified CaptureDevice; if there is no default quality set it will return the empty string
       *
       * @declaration public String GetCaptureDeviceDefaultQuality(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDevice capDev = getCapDev(stack);
        return capDev == null ? null : capDev.getDefaultQuality();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "SetCaptureDeviceDefaultQuality", new String[] {"CaptureDevice", "Quality"}, true)
    {
      /**
       * Sets the default recording quality for a CaptureDevice
       * @param CaptureDevice the name of the CaptureDevice
       * @param Quality the default quality setting to use for the specified capture device, use null or the empty string to clear the setting
       *
       * @declaration public void SetCaptureDeviceDefaultQuality(String CaptureDevice, String Quality);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String q = getString(stack);
        CaptureDevice capDev = getCapDev(stack);
        if (capDev != null)
          capDev.setDefaultQuality(q);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "SetCaptureDeviceAudioSource", new String[] {"CaptureDevice", "AudioSource"}, true)
    {
      /**
       * Sets the audio capture source for a corresponding CaptureDevice
       * @param CaptureDevice the name of the CaptureDevice
       * @param AudioSource the name of the audio capture source, should be one of the values from {@link #GetAudioCaptureSources GetAudioCaptureSources()}
       *
       * @declaration public void SetCaptureDeviceAudioSource(String CaptureDevice, String AudioSource);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String q = getString(stack);
        CaptureDevice capDev = getCapDev(stack);
        if (capDev != null)
          capDev.setAuxCaptureDeviceName(q);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetCaptureDeviceAudioSource", new String[] {"CaptureDevice"}, true)
    {
      /**
       * Gets the audio capture source for a corresponding CaptureDevice.
       * @param CaptureDevice the name of the CaptureDevice
       * @return the name of the audio capture source for the specified CaptureDevice; the empty string is returned if there is no separate audio capture source (i.e. multiplexed capture or video only capture)
       *
       * @declaration public String GetCaptureDeviceAudioSource(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDevice capDev = getCapDev(stack);
        if (capDev != null)
          return capDev.getAuxCaptureDevice();
        else
          return "";
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetAudioCaptureSources", true)
    {
      /**
       * Returns an array of all the audio capture sources in the system, used with {@link #SetCaptureDeviceAudioSource SetCaptureDeviceAudioSource(CaptureDevice, AudioSource)}
       * @return an array of all the audio capture sources in the system
       *
       * @declaration public String[] GetAudioCaptureSources();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (!Sage.WINDOWS_OS)
          return Pooler.EMPTY_STRING_ARRAY;
        // This is the audio capture sources + the WDM capture devices that we didn't use for video
        java.util.HashSet capSet = new java.util.HashSet();
        java.util.StringTokenizer toker = new java.util.StringTokenizer(DShowCaptureManager.CAPTURE_DEVICE_CATEGORY_GUID, ",;");
        java.util.ArrayList vec = new java.util.ArrayList();
        while (toker.hasMoreTokens())
        {
          vec.add(toker.nextToken());
        }
        if (Sage.WINDOWS_OS)
          capSet.addAll(java.util.Arrays.asList(DShowCaptureDevice.getDevicesInCategoryS0((String[]) vec.toArray(Pooler.EMPTY_STRING_ARRAY))));
        capSet.removeAll(java.util.Arrays.asList(MMC.getInstance().getCaptureDeviceNames()));
        if (Sage.WINDOWS_OS)
          capSet.addAll(java.util.Arrays.asList(DShowCaptureDevice.getDevicesInCategory0(DShowCaptureManager.AUDIO_CAPTURE_DEVICE_CATEGORY_GUID)));
        return (String[]) capSet.toArray(Pooler.EMPTY_STRING_ARRAY);
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "IsCaptureDeviceHardwareEncoder", 1, new String[] {"CaptureDevice"}, true)
    {
      /**
       * Returns true if the CaptureDevice is a hardware encoder
       * @param CaptureDevice the name of the CaptureDevice
       * @return true if the specified CaptureDevice is a hardware encoder
       *
       * @declaration public boolean IsCaptureDeviceHardwareEncoder(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(getCapDev(stack).isHWEncoder());
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetCaptureDeviceBroadcastStandard", 1, new String[] {"CaptureDevice"}, true)
    {
      /**
       * Returns the name of the broadcast standard used for reception on this capture device.
       * NOTE: The method 'GetCaptureDeviceInputBroadcastStandard' should be used instead since the broadcast standard can change per-input
       * @param CaptureDevice the name of the CaptureDevice
       * @return the name of the broadcast standard used for reception on this capture device (i.e. NTSC, ATSC, DVB-S, etc.)
       * @since 5.1
       *
       * @declaration public String GetCaptureDeviceBroadcastStandard(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getCapDev(stack).getActiveInput().getBroadcastStandard();
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "SetCaptureDeviceDTVStandard", 2, new String[] {"CaptureDevice", "DTVStandard"}, true)
    {
      /**
       * Sets the TV standard to use for a capture device for devices that support multiple digital TV standards. The only current
       * example of this is the Hauppauge HVR-4000 which support DVB-T, DVB-S and DVB-C.
       * @param CaptureDevice the name of the CaptureDevice
       * @param DTVStandard the DTV standard to use for this capture device, should be one of "DVB-T", "DVB-S" or "DVB-C"
       * @since 7.0
       *
       * @declaration public void SetCaptureDeviceDTVStandard(String CaptureDevice, String DTVStandard);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String std = getString(stack);
        CaptureDevice cdev = getCapDev(stack);
        if (cdev instanceof DShowCaptureDevice)
        {
          ((DShowCaptureDevice) cdev).setDTVStandard(std);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "GetCaptureDeviceMerit", new String[] {"CaptureDevice"}, true)
    {
      /**
       * Gets the encoder merit for a CaptureDevice.
       * @param CaptureDevice the name of the CaptureDevice
       * @return The merit value for the specified CaptureDevice; If all else is equal, a capture device with a higher merit has higher priority.
       *
       * @declaration public int GetCaptureDeviceMerit(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDevice capDev = getCapDev(stack);
        if (capDev != null)
          return capDev.getMerit();
        else
          return 0;
      }});
    rft.put(new PredefinedJEPFunction("CaptureDevice", "SetCaptureDeviceMerit", new String[] {"CaptureDevice", "Merit"}, true)
    {
      /**
       * Sets the encoder merit for a CaptureDevice.
       * @param Merit The new merit value for the specified CaptureDevice
       *
       * @declaration public void SetCaptureDeviceMerit(String CaptureDevice, int Merit);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int Merit = getInt(stack);
        CaptureDevice capDev = getCapDev(stack);
        if (capDev != null)
          capDev.setMerit(Merit);
        return null;
      }});
    /*
    rft.put(new PredefinedJEPFunction("CaptureDevice", "", 1, new String[] {"CaptureDeviceInput"})
    {public Object runSafely(Catbert.FastStack stack) throws Exception{
       return getCapDevInput(stack);
      }});
     */
  }
}
