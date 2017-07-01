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

/*
 * This class will continually try to run something on the AWTEventThread to
 * see if that thread has been hung for a little too long. We do this to detect
 * lack of responsiveness in the UI.
 */
public class AWTThreadWatcher extends Thread
{
  /** Creates a new instance of AWTThreadWatcher */
  public AWTThreadWatcher(UIManager inUIMgr)
  {
    super("AWTThreadWatcher-" + inUIMgr.getLocalUIClientName());
    //setPriority(MIN_PRIORITY);
    setDaemon(true);
    alive = true;
    uiMgr = inUIMgr;
    checkPeriod = uiMgr.getLong("ui/awt_check_period", 750);
    requiredResponseTime = uiMgr.getLong("ui/awt_response_time", 750);
  }

  public void run()
  {
    pinger = new AWTPing();
    currentlyHung = false;
    long delayToDumpAllThreads = Sage.getLong("ui/thread_hang_delay_to_dump", 0);
    if (delayToDumpAllThreads > 0)
    {
      try
      {
        stackDumperMeth = Thread.class.getDeclaredMethod("getAllStackTraces", new Class[0]);
      }
      catch (Throwable thr)
      {
        System.out.println("Stack dumping disabled because we can't reflect the dump method!:" + thr);
      }
    }
    while (alive)
    {
      long timeSinceLastResponse = Sage.eventTime() - lastResponseTime;
      if (timeSinceLastResponse < checkPeriod)
      {
        try{Thread.sleep(checkPeriod - timeSinceLastResponse);}catch(Exception e){}
      }
      lastCheckSubmitTime = Sage.eventTime();
      checkedIn = false;
      uiMgr.getRouter().invokeLater(pinger);
      boolean dumpedThreadStates = false;
      synchronized (lock)
      {
        while (!checkedIn && alive)
        {
          try
          {
            lock.wait(requiredResponseTime);
          }catch (Exception e){}
          if (!checkedIn && alive)
          {
            if (Sage.DBG) System.out.println("EventThread-" + uiMgr.getLocalUIClientName() +
                " Hang Detected - hang time = " + (Sage.eventTime() - lastCheckSubmitTime) + " UILocker=" +
                uiMgr.getUILockHolder());

            if(stackDumperMeth != null && delayToDumpAllThreads > 0 && (Sage.eventTime() - lastCheckSubmitTime) > delayToDumpAllThreads)
            {
              dumpThreadStates();
            }
          }
        }
      }
    }
  }

  public boolean isAWTHung()
  {
    synchronized (lock)
    {
      if (!checkedIn && (Sage.eventTime() - lastCheckSubmitTime > requiredResponseTime))
        currentlyHung = true;
      return currentlyHung;//(!checkedIn && (Sage.time() - lastCheckSubmitTime > requiredResponseTime));
    }
  }

  public long getAWTHangTime()
  {
    synchronized (lock)
    {
      if (isAWTHung())
      {
        return Sage.eventTime() - lastResponseTime;
      }
      else
        return 0;
    }
  }

  private class AWTPing implements Runnable
  {
    public AWTPing()
    {}
    public void run()
    {
      synchronized (AWTThreadWatcher.this.lock)
      {
        AWTThreadWatcher.this.checkedIn = true;
        // NARFLEX: 1/27/11 - If we got the response here, then we are no longer hung...we shouldn't let it propogate for another
        // check cycle just cause there was a delay...this is why the circle always spins longer than it should
        currentlyHung = false;//(Sage.eventTime() - lastCheckSubmitTime >= requiredResponseTime);
        AWTThreadWatcher.this.lastResponseTime = Sage.eventTime();
        AWTThreadWatcher.this.lock.notifyAll();
      }
    }
  }

  public long getCheckPeriod() { return checkPeriod; }
  public void setCheckPeriod(long x) { checkPeriod = x; }
  public long getRequiredResponseTime() { return requiredResponseTime; }
  public void setRequiredResponseTime(long x) { requiredResponseTime = x; }

  public void kill()
  {
    alive = false;
  }

  public static void dumpThreadStates()
  {
    if (stackDumperMeth == null)
    {
      try
      {
        stackDumperMeth = Thread.class.getDeclaredMethod("getAllStackTraces", new Class[0]);
      }
      catch (Throwable thr)
      {
        System.out.println("Stack dumping disabled because we can't reflect the dump method!:" + thr);
      }
    }
    if (stackDumperMeth == null) return;
    try
    {
      java.util.Map mappy = (java.util.Map)stackDumperMeth.invoke(null, (Object[])null);//Thread.getAllStackTraces();
      java.util.Iterator walker = mappy.entrySet().iterator();
      while (walker.hasNext())
      {
        java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
        System.out.println("" + ent.getKey());
        StackTraceElement[] stack = (StackTraceElement[]) ent.getValue();
        for (int i = 0; i < stack.length; i++)
          System.out.println("\t"+stack[i].toString());
      }
    }
    catch (Throwable thr)
    {
      System.out.println("ERROR dumping thread stacks of:" + thr);
    }
  }
  private static java.lang.reflect.Method stackDumperMeth = null;

  private Object lock = new Object();
  private long checkPeriod;
  private long requiredResponseTime;
  private UIManager uiMgr;
  private volatile long lastResponseTime;
  private long lastCheckSubmitTime;
  private volatile boolean checkedIn;
  private volatile boolean currentlyHung;
  private AWTPing pinger;
  private boolean alive;
}
