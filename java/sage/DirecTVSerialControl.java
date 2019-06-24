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

public class DirecTVSerialControl
{
  public static final String DIRECTV_SERIAL_CONTROL = "DirecTVSerialControl";
  private static boolean loadedLib = false;
  public DirecTVSerialControl()
  {
    serialHandleMap = new java.util.HashMap();
  }

  public synchronized void goodbye()
  {
    synchronized (serialHandleMap)
    {
      java.util.Iterator walker = serialHandleMap.values().iterator();
      while (walker.hasNext())
      {
        Long currHandle = (Long) walker.next();
        closeHandle0(currHandle.intValue());
        // Don't forget to remove it since its no longer valid!
        walker.remove();
      }
    }
  }

  public static boolean isSerialDevice(String deviceName)
  {
    if (deviceName == null || !deviceName.startsWith("COM") || deviceName.length() < 4) return false;
    try
    {
      int comPortNum = Integer.parseInt(deviceName.substring(3));
      return (comPortNum > 0);
    }
    catch (Exception e) { return false; }
  }

  public boolean tune(final String portString, final String chanString)
  {
    if (loadLibFailed) return false;
    if (!isSerialDevice(portString)) return false;
    try
    {
      if (!loadedLib)
      {
        if(!Sage.MAC_OS_X) // included libSage...
          sage.Native.loadLibrary("DirecTVSerialControl");
        loadedLib = true;
      }
    }
    catch (Throwable t)
    {
      loadLibFailed = true;
      System.out.println("ERROR loading DirecTVSerialControl:" + t);
      return false;
    }
    // NARFLEX: I've seen this take a long time in misconfigured systems, so make it async so it doesn't mess up the Seeker
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        synchronized (serialHandleMap)
        {
          Long openHandle = (Long) serialHandleMap.get(portString);
          if (openHandle == null)
          {
            long newHand = openDTVSerial0(portString);
            if (newHand == -1)
            {
              System.out.println("ERROR Cannot open serial port for DTV control: " + portString);
              return;
            }
            serialHandleMap.put(portString, openHandle = new Long(newHand));
          }
          try
          {
            if (Sage.DBG) System.out.println("dtvSerialChannel(handle=" + openHandle +
                ", chan=" + Integer.parseInt(chanString) + ")");
            if (!dtvSerialChannel0(openHandle, Integer.parseInt(chanString)))
            {
              if (Sage.DBG) System.out.println("DirecTV serial channel change failed!");
              if (Sage.getBoolean("reopen_directv_serial_on_failure", true))
              {
                if (Sage.DBG) System.out.println("Closing and re-opening the connection to the DirecTV receiver...");
                closeHandle0(openHandle);
                serialHandleMap.remove(portString);
                long newHand = openDTVSerial0(portString);
                if (newHand == -1)
                {
                  System.out.println("ERROR Cannot open serial port for DTV control: " + portString);
                  return;
                }
                serialHandleMap.put(portString, openHandle = new Long(newHand));
                if (Sage.DBG) System.out.println("Retrying DirecTV channel change now");
                dtvSerialChannel0(openHandle, Integer.parseInt(chanString));
              }
            }
          }catch (Exception e){}
        }
      }
    }, "DirecTVAsyncChannelTune", Thread.MIN_PRIORITY);
    return true;
  }

  private native long openDTVSerial0(String comPortString);
  private native void closeHandle0(long handle);
  private native boolean dtvSerialChannel0(long handle, int channel);

  private java.util.Map serialHandleMap;
  private boolean loadLibFailed = false;
}
