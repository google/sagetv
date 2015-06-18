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

public abstract class SystemTask implements Runnable
{
  protected void startTaskThread(String name)
  {
    startTaskThread(name, false);
  }
  protected void startTaskThread(String name, boolean synchronous)
  {
    success = false;
    alive = true;
    abort = false;
    statusMessage = "";

    if (synchronous)
    {
      run();
    }
    else
    {
      taskThread = new Thread(this, name);
      taskThread.setDaemon(true);
      taskThread.setPriority(Thread.MIN_PRIORITY);
      taskThread.start();
    }
  }

  public boolean isComplete()
  {
    return !alive;
  }

  public void cancel()
  {
    abort = true;
    if (taskProcess != null)
    {
      if (Sage.DBG) System.out.println("Aborting the " + (taskThread != null ? taskThread.getName() : "?") + " process!!!");
      taskProcess.destroy();
    }
    if (taskThread != null)
    {
      if (Sage.DBG) System.out.println("Waiting for download thread to terminate...");
      try
      {
        taskThread.join(30000);
      }
      catch (InterruptedException e)
      {}
      if (Sage.DBG) System.out.println("Download thread has terminated.");
    }
  }

  public boolean wasSuccessful()
  {
    return success;
  }

  public String getStatusMessage()
  {
    return statusMessage;
  }

  public final void run()
  {
    try
    {
      taskRun();
    }
    finally
    {
      alive = false;
      if (scratchDir != null)
      {
        Seeker.getInstance().clearDirectoryStorageRequest(scratchDir);
        scratchDir = null;
      }
    }
  }

  protected abstract void taskRun();

  protected boolean launchMonitoredProcess(String[] execParams)
  {
    return launchMonitoredProcess(execParams, null);
  }
  protected boolean launchMonitoredProcess(String[] execParams, String killString)
  {
    int res = 0;
    try
    {
      killFound = false;
      taskProcess = Runtime.getRuntime().exec(execParams);
      Thread t = new Thread(new InputEater(taskProcess.getInputStream(), killString));
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
      Thread t1 = new Thread(new InputEater(taskProcess.getErrorStream(), killString));
      t1.setDaemon(true);
      t1.setPriority(Thread.MIN_PRIORITY);
      t1.start();
      t.join(1000);
      t1.join(1000);
      taskProcess.waitFor();
      res = taskProcess.exitValue();
      taskProcess = null;
      if (Sage.DBG) System.out.println("Task process returned: " + res);
      if (!killFound && res != 0)
      {
        statusMessage = "Error: " + statusMessage;
        return false;
      }
    }
    catch (Exception e)
    {
      statusMessage = "Error: " + e.toString();
      return false;
    }
    return true;
  }

  protected void succeeded()
  {
    success = true;
    statusMessage = Sage.rez("OK");
  }

  protected class InputEater implements Runnable
  {
    public InputEater(java.io.InputStream is) throws java.io.IOException
    {
      this(is, null);
    }
    public InputEater(java.io.InputStream is, String killString) throws java.io.IOException
    {
      this.is = new java.io.BufferedReader(new java.io.InputStreamReader(is));
      this.killString = killString;
    }

    public void run()
    {
      try
      {
        String s;
        while ((s = is.readLine()) != null && !abort)
        {
          s = s.trim();
          if (s.length() > 0)
          {
            statusMessage = s;
            if (Sage.DBG) System.out.println("TaskStatus:" + statusMessage);
            if (killString != null && s.indexOf(killString) != -1)
            {
              if (Sage.DBG) System.out.println("KillString detected...aborting");
              killFound = true;
              if (taskProcess != null)
                taskProcess.destroy();
              return;
            }
          }
        }
      }
      catch (Exception e)
      {}
    }

    private java.io.BufferedReader is;
    private String killString;
  }

  protected void allocateScratchSpace(long size)
  {
    scratchDir = Seeker.getInstance().requestDirectoryStorage("scratch", size);
    Seeker.getInstance().kick();
    if (Sage.DBG) System.out.println("Temp dir for task data: " + scratchDir);
  }

  protected void clearScratch()
  {
    if (scratchDir != null)
    {
      IOUtils.exec2("rm -r " + scratchDir.getAbsolutePath());
      IOUtils.safemkdirs(scratchDir);
    }
  }

  protected long scratchSize;
  protected java.io.File scratchDir;

  protected volatile boolean alive;
  protected volatile boolean success;
  protected volatile boolean abort;
  protected volatile String statusMessage = "";

  protected volatile boolean killFound;

  protected Thread taskThread;
  protected Process taskProcess;
}
