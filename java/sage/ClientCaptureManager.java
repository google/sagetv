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

public class ClientCaptureManager implements CaptureDeviceManager
{
  public ClientCaptureManager()
  {
    capDevs = new CaptureDevice[0];
    prefs = MMC.MMC_KEY + '/';
  }

  public void detectCaptureDevices(CaptureDevice[] alreadyDetectedDevices)
  {
    resyncToProperties(false);
  }

  public void freeResources()
  {
  }

  public CaptureDevice[] getCaptureDevices()
  {
    return capDevs;
  }

  void resyncToProperties(boolean updateMMC)
  {
    String[] encoderKeys = Sage.childrenNames(prefs + CaptureDevice.ENCODERS + '/');
    java.util.ArrayList vec = new java.util.ArrayList();
    for (int i = 0; i < encoderKeys.length; i++)
    {
      String testCapDevName = CaptureDevice.getCapDevNameForPrefs(
          MMC.MMC_KEY + "/" + CaptureDevice.ENCODERS + "/" + encoderKeys[i] + "/");
      if (MMC.getInstance().getCaptureDeviceNamed(testCapDevName) == null)
      {
        // Create the new capture device
        try
        {
          CaptureDevice testEncoder = new StubCaptureDevice(Integer.parseInt(encoderKeys[i]));
          vec.add(testEncoder);
        }
        catch (NumberFormatException e){}
      }
    }
    for (int i = 0; i < capDevs.length; i++)
    {
      if (MMC.getInstance().getCaptureDeviceNamed(capDevs[i].toString()) != null)
      {
        capDevs[i].loadPrefs();
        for (int j = 0; j < capDevs[i].srcConfigs.size(); j++)
        {
          ((CaptureDeviceInput) capDevs[i].srcConfigs.get(j)).loadPrefs();
        }
        vec.add(capDevs[i]);
      }
    }

    capDevs = (CaptureDevice[]) vec.toArray(new CaptureDevice[0]);
    if (updateMMC)
    {
      MMC.getInstance().updateCaptureDeviceObjects(capDevs);
    }
  }

  private CaptureDevice[] capDevs;
  private String prefs;
}
