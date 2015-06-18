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

public abstract class PowerManagement implements Runnable
{
  public static final int SYSTEM_POWER = 0x1;
  public static final int DISPLAY_POWER = 0x2;
  public static final int USER_ACTIVITY = 0;//0x4; // This is causing problems; disable it. It resets the display & system timers on Windows.
  protected PowerManagement()
  {
  }

  public void run()
  {
    alive = true;
    myThread = Thread.currentThread();
    while (alive)
    {
      synchronized (this)
      {
        try
        {
          wait(getWaitTime());
        }catch(Exception e){}
      }

      int currState;
      if (stayAlivePing || System.currentTimeMillis() - lastActivityTime < getIdleTimeout())
      {
        stayAlivePing = false;
        currState = fullPowerState() | USER_ACTIVITY; //DISPLAY_POWER | SYSTEM_POWER;
      }
      else
        currState = getPowerState();
      long wakeupTime = getWakeupTime();
      if (wakeupTime > 0)
      {
        wakeupTime -= getPrerollTime();
      }
      if (wakeupTime <= System.currentTimeMillis() && wakeupTime > 0)
      {
        wakeupTime = 0;
        currState |= SYSTEM_POWER;
      }

      if (logging) System.out.println("PM currState=" + currState + " wakeupTime=" + myDF.format(new java.util.Date(wakeupTime)));
      setPowerState0(currState);
      wakeupPtr = setWakeupTime0(wakeupPtr, wakeupTime);
    }
    setPowerState0(0);
    if (wakeupPtr != 0)
      wakeupPtr = setWakeupTime0(wakeupPtr, 0);
  }

  // Determine the current power state. This MUST be implemented
  protected abstract int getPowerState();
  protected abstract int fullPowerState();

  // Determine the next time we need to be awake to do a recording
  protected long getWakeupTime()
  {
    return 0; // by default there's nothing to record or serve so just return 0
  }

  public void goodbye()
  {
    alive = false;
    if (myThread != null)
    {
      synchronized (this)
      {
        notifyAll();
      }
      try{myThread.join(2000);}catch(Exception e){}
    }
  }

  public void softkick()
  {
    synchronized (this)
    {
      notifyAll();
    }
  }
  public void kick()
  {
    stayAlivePing = true;
    lastActivityTime = System.currentTimeMillis();
    synchronized (this)
    {
      notifyAll();
    }
  }

  public long getWaitTime() { return waitTime; }
  public long getPrerollTime() { return prerollTime; }
  public long getIdleTimeout() { return idleTimeout; }

  public void setWaitTime(long x) { waitTime = x; }
  public void setPrerollTime(long x) { prerollTime = x; }
  public void setIdleTimeout(long x) { idleTimeout = x; }
  public void setLogging(boolean x)
  {
    logging = x;
    if (x && myDF == null)
      myDF = new java.text.SimpleDateFormat("EE M/d/yyyy H:mm:ss.SSS");
  }

  public long getLastActivityTime() { return lastActivityTime; }

  protected native void setPowerState0(int currState);
  // 0 for the wakeupTime will release the handle
  protected native long setWakeupTime0(long wakeupHandle, long wakeupTime);

  private Thread myThread;
  private boolean alive;

  private long wakeupPtr;

  protected boolean stayAlivePing;
  protected long lastActivityTime;

  private boolean logging;
  private long waitTime;
  private long prerollTime;
  private long idleTimeout;
  private java.text.DateFormat myDF;
}
