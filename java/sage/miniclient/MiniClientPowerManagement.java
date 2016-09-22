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
package sage.miniclient;

/**
 *
 * @author  Narflex
 */
public class MiniClientPowerManagement extends sage.PowerManagement
{
  private static sage.PowerManagement chosenOne;

  public static sage.PowerManagement getInstance()
  {
    if (chosenOne != null)
      return chosenOne;
    chosenOne = new MiniClientPowerManagement();
    return chosenOne;
  }

  /** Creates a new instance of MiniClientPowerManagement */
  public MiniClientPowerManagement()
  {
    super();
    if (MiniClient.WINDOWS_OS)
      sage.Native.loadLibrary("SageTVWin32");
  }

  // Determine the current power state
  protected int getPowerState()
  {
    // The headless serve can now have video playback through the extenders so check for playback even in that case
    // If media is playing then we need to keep display
    MediaCmd mc = MediaCmd.getInstance();
    if (mc != null)
    {
      MiniMPlayerPlugin playa = mc.getPlaya();
      if (playa != null && playa.getState() == playa.PLAY_STATE)
        return DISPLAY_POWER | SYSTEM_POWER;
    }

    return 0;
  }

  protected int fullPowerState()
  {
    return DISPLAY_POWER | SYSTEM_POWER;
  }

  protected void setPowerState0(int currState)
  {
    if (MiniClient.WINDOWS_OS || MiniClient.MAC_OS_X) {
      super.setPowerState0(currState);
    }
    else
    {
      try
      {
        if ((currState & DISPLAY_POWER) == DISPLAY_POWER)
        {
          if (!ssForcedOff)
          {
            Process newProc = Runtime.getRuntime().exec("xset s off");
            newProc.waitFor();
            ssForcedOff = true;
          }
          // Be sure the screensaver is not currently active!!
          Process newProc = Runtime.getRuntime().exec("xset s reset");
          //newProc.waitFor();
        }
        else
        {
          if (ssForcedOff)
          {
            Process newProc = Runtime.getRuntime().exec("xset s default");
            newProc.waitFor();
            ssForcedOff = false;
          }
        }
      }
      catch (Exception e)
      {
        System.out.println("Error exceuting PM of:" + e.toString());
      }
    }
  }
  // 0 for the wakeupTime will release the handle
  protected long setWakeupTime0(long wakeupHandle, long wakeupTime)
  {
    if (MiniClient.WINDOWS_OS || MiniClient.MAC_OS_X) {
      return super.setWakeupTime0(wakeupHandle, wakeupTime);
    }
    else
      return 0;
  }

  public void goodbye()
  {
    if (ssForcedOff)
    {
      try
      {
        Process newProc = Runtime.getRuntime().exec("xset s default");
        newProc.waitFor();
      }
      catch (Exception e)
      {
        System.out.println("Error exceuting PM of:" + e.toString());
      }
    }
  }

  protected boolean ssForcedOff = false;
}
