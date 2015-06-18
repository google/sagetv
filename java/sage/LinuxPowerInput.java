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
import java.io.*;

public class LinuxPowerInput implements SageTVInputPlugin
{
  // Be sure only one of these is loaded at a time!
  private static boolean loaded = false;
  private SageTVInputCallback evtRtr;
  private Thread InputThread;
  private BufferedReader eventStream;

  public LinuxPowerInput()
  {

  }

  public boolean openInputPlugin(SageTVInputCallback callback)
  {
    if (loaded) return false;
    evtRtr = callback;
    if(!setupLinuxPowerInput()) return false;

    InputThread = new Thread(new Runnable()
    {
      public void run()
      {
        LinuxPowerInputThread(evtRtr);
      }
    }, "LinuxPowerInput");
    InputThread.setDaemon(true);
    // If this is too low in priority we can miss
    // the repeat timing of a button press, so we need
    // accurate timing here to some degree.
    InputThread.setPriority(Thread.MAX_PRIORITY - 3);
    InputThread.start();
    loaded = true;
    return true;
  }

  public void closeInputPlugin()
  {
    // Don't close this down because it'll deadlock against the read in the input thread!
    Thread t = new Thread()
    {
      public void run()
      {
        try
        {
          eventStream.close();
        }
        catch(IOException e)
        {
        }
      }
    };
    t.setDaemon(true);
    t.start();
    loaded = false;
  }

  private boolean setupLinuxPowerInput()
  {
    try
    {
      eventStream = new BufferedReader(new InputStreamReader (new FileInputStream("/proc/acpi/event")));
    }
    catch(IOException e)
    {
      return false;
    }
    return true;
  }

  private void LinuxPowerInputThread(SageTVInputCallback evtRtr)
  {
    while(true)
    {
      try
      {
        String acpiEvent = eventStream.readLine();
        System.out.println(acpiEvent);
        if(acpiEvent.indexOf("button/power")>=0)
        {
          System.out.println("Power button has been pressed");
          if (evtRtr instanceof EventRouter)
          {
            SageTV.shutdown();
          }
        }
      }
      catch(IOException e)
      {
        System.out.println("Error reading in PowerInputThread");
      }
    }
  }
}
