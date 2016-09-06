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

public abstract class SageTVInfraredReceive implements SageTVInputPlugin
{
  public static final String IRMAN_RCV_PORT = "irman_rcv_port";
  private static boolean loadedLib = false;
  public SageTVInfraredReceive()
  {
    if (!loadedLib)
    {
      sage.Native.loadLibrary("SageTVInfraredReceive");
      loadedLib = true;
    }
  }
  public void closeInputPlugin()
  {
    closeIRReceivePort();
  }

  public String getIRReceivePort() { return irmanPortString; }
  public boolean setIRReceivePort(String portName)
  {
    if (portName == null) portName = "";
    if (portName.equals(irmanPortString)) return true;
    closeIRReceivePort();
    return setupIRReceivePort();
  }
  protected boolean setupIRReceivePort()
  {
    if (irmanPortString.length() > 0)
    {
      if (irmanPortString.equals("USB"))
      {
        if (usbuirtRecvPortInit0(evtRtr))
        {
          irmanPort = 1;
          return true;
        }
        else
          return false;
      }
      else
        irmanPort = irmanPortInit0(irmanPortString);
      if (irmanPort != 0)
      {
        irmanThread = new Thread(new Runnable()
        {
          public void run()
          {
            irmanPortThread0(evtRtr, irmanPort);
          }
        }, "IRReceive");
        irmanThread.setDaemon(true);
        // If this is too low in priority we can miss
        // the repeat timing of a button press, so we need
        // accurate timing here to some degree.
        irmanThread.setPriority(Thread.MAX_PRIORITY - 3);
        irmanThread.start();
        return true;
      }
      return false;
    }
    return true;
  }
  protected void closeIRReceivePort()
  {
    if (irmanPort == 0) return;
    if ("USB".equals(irmanPortString))
    {
      closeUsbuirtRecvPort0();
      irmanPort = 0;
    }
    else
    {
      long oldPort = irmanPort;
      irmanPort = 0;
      closeIRManPort0(oldPort);
    }
    if (irmanThread != null)
    {
      try{irmanThread.join(5000);}catch(Exception e){}
      irmanThread = null;
    }
  }

  protected native void pvIRPortThread0(SageTVInputCallback evtRtr);
  protected native void dvcrIRPortThread0(SageTVInputCallback evtRtr);
  protected native void avrIRPortThread0(SageTVInputCallback evtRtr);
  protected native long irmanPortInit0(String portName);
  protected native void closeIRManPort0(long portHandle);
  protected native void irmanPortThread0(SageTVInputCallback evtRtr, long portHandle);
  protected native boolean usbuirtRecvPortInit0(SageTVInputCallback evtRtr);
  protected native void closeUsbuirtRecvPort0();

  protected Thread pvIRThread;
  protected Thread dvcrIRThread;
  protected Thread avrIRThread;
  protected String irmanPortString;
  protected long irmanPort;
  protected Thread irmanThread;

  protected SageTVInputCallback evtRtr;
}
