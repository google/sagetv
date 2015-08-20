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

public class ExternalTuningManager
{
  // This has to be synced or we could end up opening 2 plugins by calling one from the UI threads and one from the recording thread
  private static Object syncLock = new Object();
  public static SFIRTuner getIRTunerPlugin(String pluginLibrary, int pluginPort)
  {
    if (pluginLibrary == null || pluginLibrary.length() == 0 || pluginLibrary.endsWith(DirecTVSerialControl.DIRECTV_SERIAL_CONTROL))
      return null;
    if (SFIRTuner.getValidDeviceFiles(new String[] { pluginLibrary }).length == 0)
      pluginLibrary = SFIRTuner.getFileForPrettyDeviceName(pluginLibrary);
    synchronized (syncLock)
    {
      SFIRTuner tuney = (SFIRTuner) sfirPluginMap.get(pluginLibrary + pluginPort);
      if (tuney != null)
      {
        if (tuney.isAlive())
          return tuney;
        else if (tuney.openPort(pluginPort))
          return tuney;
        else
          return null;
      }
      tuney = new SFIRTuner(pluginLibrary);
      if (tuney.openPort(pluginPort))
      {
        sfirPluginMap.put(pluginLibrary + pluginPort, tuney);
        return tuney;
      }
      else
      {
        tuney.goodbye();
        return null;
      }
    }
  }

  public static DirecTVSerialControl getDirecTVSerialControl()
  {
    if (dtvSerial == null)
      dtvSerial = new DirecTVSerialControl();
    return dtvSerial;
  }

  public static void goodbye()
  {
    if (dtvSerial != null)
      dtvSerial.goodbye();
    java.util.Iterator walker = sfirPluginMap.values().iterator();
    while (walker.hasNext())
    {
      ((SFIRTuner) walker.next()).goodbye();
    }
  }

  private static java.util.Map sfirPluginMap = new java.util.HashMap();
  private static DirecTVSerialControl dtvSerial;
}
