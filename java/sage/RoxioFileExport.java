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

public class RoxioFileExport implements FileExportPlugin, Runnable
{
  public RoxioFileExport()
  {
    currFiles = new java.util.Vector();
  }

  public void run()
  {
    nativePtr = openRoxioPlugin0(Sage.rez("My_Recorded_TV"));
    openPluginNow = false;
    synchronized (syncLock)
    {
      syncLock.notifyAll();
    }
    if (nativePtr == 0) return;
    while (true)
    {
      String[] filenames = null;
      synchronized (syncLock)
      {
        if (!currFiles.isEmpty())
        {
          filenames = (String[]) currFiles.toArray(new String[0]);
          currFiles.clear();
          syncLock.notifyAll();
        }
        else
        {
          try{syncLock.wait();}catch(Exception e){}
        }
      }
      if (filenames != null)
      {
        if (!closePluginNow)
        {
          synchronized (syncLock)
          {
            try
            {
              // Do this so we're not right on top of the video finishing up
              syncLock.wait(10000);
            }
            catch(InterruptedException e)
            {
              continue;
            }
          }
        }
        addFilesToLibrary0(nativePtr, filenames);
        continue;
      }
      if (closePluginNow)
      {
        closeRoxioPlugin0(nativePtr);
        nativePtr = 0;
        closePluginNow = false;
        synchronized (syncLock)
        {
          syncLock.notifyAll();
        }
        return;
      }
    }
  }

  public boolean openPlugin()
  {
    openPluginNow = true;
    Thread t = new Thread(this, "RoxioFileExport");
    t.setPriority(Thread.MIN_PRIORITY);
    t.setDaemon(true);
    t.start();
    synchronized (syncLock)
    {
      if (openPluginNow)
      {
        try{syncLock.wait(10000);}catch(Exception e){}
      }
    }
    return nativePtr != 0;
  }
  public void closePlugin()
  {
    if (nativePtr != 0)
    {
      synchronized (syncLock)
      {
        closePluginNow = true;
        syncLock.notifyAll();
        try{syncLock.wait(5000);}catch(Exception e){}
      }
    }
  }
  public void filesDoneRecording(java.io.File[] f, byte acquisitionType)
  {
    if (nativePtr != 0 && (acquisitionType == ACQUISITION_FAVORITE ||
        acquisitionType == ACQUISITION_MANUAL))
    {
      String[] strs = new String[f.length];
      for (int i = 0; i < strs.length; i++)
        strs[i] = f[i].getAbsolutePath();
      synchronized (syncLock)
      {
        currFiles.addAll(java.util.Arrays.asList(strs));
        syncLock.notifyAll();
      }
    }
  }
  private native long openRoxioPlugin0(String libName);
  private native void closeRoxioPlugin0(long ptr);
  private native void addFilesToLibrary0(long ptr, String[] filenames);

  private long nativePtr;
  private Object syncLock = new Object();
  private boolean openPluginNow;
  private boolean closePluginNow;
  private java.util.Vector currFiles;
}
