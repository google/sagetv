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

public class StubCaptureDevice extends CaptureDevice
{
  public StubCaptureDevice(int inID)
  {
    super(inID);
    host = Sage.get(prefs + NetworkCaptureDevice.ENCODING_HOST, "");
  }

  public String getName()
  {
    if (host.length() > 0)
      return Sage.rez("Device_On_Host", new Object[] { super.getName(), host });
    else
      return super.getName();
  }

  public void freeDevice()
  {
  }

  public long getRecordedBytes()
  {
    return -1;
  }

  public void loadDevice() throws EncodingException
  {
  }

  public void startEncoding(CaptureDeviceInput cdi, String encodeFile, String channel) throws EncodingException
  {
  }

  public void stopEncoding()
  {
  }

  public void switchEncoding(String switchFile, String channel) throws EncodingException
  {
  }

  public boolean isLoaded()
  {
    return true;
  }

  protected boolean doTuneChannel(String tuneString, boolean autotune)
  {
    return true;
  }

  protected boolean doScanChannel(String tuneString)
  {
    return true;
  }
  protected String doScanChannelInfo(String tuneString)
  {
    return "1";
  }

  public String getDeviceClass()
  {
    return "Stub";
  }

  public boolean isNetworkEncoder()
  {
    return host.length() > 0;
  }

  protected String host = "";
}
