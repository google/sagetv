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
 * @author  Narflex
 */
public class ServerPowerManagement extends PowerManagement
{
  private static class SPMHolder
  {
    private static final ServerPowerManagement instance = new ServerPowerManagement();
  }

  public static PowerManagement getInstance()
  {
    return SPMHolder.instance;
  }

  /** Creates a new instance of ServerPowerManagement */
  public ServerPowerManagement()
  {
    super();
    if (Sage.WINDOWS_OS)
      sage.Native.loadLibrary("SageTVWin32");
    else if (Sage.MAC_OS_X)
      sage.Native.loadLibrary("Sage");
    extendersKeepServerOn = Sage.getBoolean("extender_power_keeps_server_out_of_standby", false);
  }

  // Determine the current power state
  protected int getPowerState()
  {
    // The headless serve can now have video playback through the extenders so check for playback even in that case
    // If media is playing then we need to keep display
    if (VideoFrame.hasFileAny() && VideoFrame.isPlayinAny())
      return USER_ACTIVITY | SYSTEM_POWER | ((sage.Sage.isHeadless() ||
          (UIManager.getLocalUI() != null && UIManager.getLocalUI().isAsleep())) ? 0 : DISPLAY_POWER);

    // server situation
    if (!Sage.client)
    {
      if (Seeker.getInstance().requiresPower())
        return SYSTEM_POWER;
      if (Ministry.getInstance().requiresPower())
    	return SYSTEM_POWER;
      // Check for any streaming clients or non-locally connected SageTV Clients
      MediaServer ms = SageTV.getMediaServer();
      if ((ms != null && ms.areClientsConnected()) || NetworkClient.areNonLocalClientsConnected())
        return USER_ACTIVITY | SYSTEM_POWER;

      // Check if any extenders are on at all
      if (extendersKeepServerOn && UIManager.getNonLocalUICount() > 0)
      {
        return USER_ACTIVITY | SYSTEM_POWER;
      }
    }

    return 0;
  }

  protected int fullPowerState()
  {
    return SYSTEM_POWER | ((sage.Sage.isHeadless() ||
        (UIManager.getLocalUI() != null && UIManager.getLocalUI().isAsleep())) ? 0 : DISPLAY_POWER);
  }

  // Determine the next time we need to be awake to do a recording
  protected long getWakeupTime()
  {
    return Scheduler.getInstance().getNextMustSeeTime();
  }

  public long getWaitTime() { return Sage.getLong("power_management_wait_time_new", Math.min(Sage.getLong("power_management_wait_time", 30000), 10000)); }
  public long getPrerollTime() { return Sage.getLong("power_management_wakeup_preroll", 120000); }
  public long getIdleTimeout() { return Sage.getLong("power_management_idle_timeout", 120000); }

  private boolean extendersKeepServerOn;
}
