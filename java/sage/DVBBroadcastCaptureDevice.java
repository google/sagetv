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
 * @author  Jean-Francois based on IVTV by Narflex
 */
public class DVBBroadcastCaptureDevice extends DVBCaptureDevice
{
  public static final String MULTICAST_BROADCAST_ADDRESS = "multicast_broadcast_address";
  /** Creates a new instance of IVTVBroadcastCaptureDevice */
  public DVBBroadcastCaptureDevice()
  {
    super();
  }
  public DVBBroadcastCaptureDevice(int inID) throws java.net.UnknownHostException
  {
    super(inID);
    broadcastAddr = Sage.get(prefs + MULTICAST_BROADCAST_ADDRESS, "");
    if (broadcastAddr.length() > 0)
      group = java.net.InetAddress.getByName(broadcastAddr);
  }
  public void setBroadcastAddress(String addr)
  {
    broadcastAddr = addr;
    Sage.put(prefs + MULTICAST_BROADCAST_ADDRESS, broadcastAddr);
  }

  public void run()
  {
    boolean logCapture = Sage.getBoolean("debug_capture_progress", false);
    if (Sage.DBG) System.out.println("Starting DVB Broadcast capture thread");
    long addtlBytes = 0;
    java.io.InputStream readbackStream = null;
    try
    {
      caster = new java.net.MulticastSocket(6789);
      caster.joinGroup(group);
      readbackStream = new java.io.FileInputStream(recFilename);
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR CREATING MULTICAST SOCKET:" + e);
    }
    byte[] buf = new byte[4096*64];
    java.net.DatagramPacket pack = new java.net.DatagramPacket(buf, buf.length, group, 6789);
    while (!stopCapture)
    {
      if (nextRecFilename != null)
      {
        try
        {
          readbackStream.close();
          switchEncoding0(pHandle, nextRecFilename);
          readbackStream = new java.io.FileInputStream(nextRecFilename);
        }
        catch (EncodingException e)
        {
          System.out.println("ERROR Switching encoder file:" + e.getMessage());
        }
        catch (java.io.IOException e1)
        {
          System.out.println("IO Error on broadcast:" + e1);
          e1.printStackTrace();
        }
        synchronized (caplock)
        {
          recFilename = nextRecFilename;
          nextRecFilename = null;
          recStart = Sage.time();
          currRecordedBytes = 0;
          // There may be a thread waiting on this state change to occur
          caplock.notifyAll();
        }
      }
      try
      {
        addtlBytes = eatEncoderData0(pHandle);
        System.out.println("Captured bytes:" + addtlBytes);
        int readback = readbackStream.read(buf, 0, (int)addtlBytes);
        System.out.println("Readback bytes:" + readback);
        pack.setLength((int)addtlBytes);
        caster.send(pack);
      }
      catch (EncodingException e)
      {
        System.out.println("ERROR Eating encoder data:" + e.getMessage());
        addtlBytes = 0;
      }
      catch (java.io.IOException e1)
      {
        System.out.println("IO Error on broadcast:" + e1);
        e1.printStackTrace();
      }
      synchronized (caplock)
      {
        currRecordedBytes += addtlBytes;
      }

      if (logCapture)
        System.out.println("DVBCap " + recFilename + " " + currRecordedBytes);
    }
    closeEncoding0(pHandle);
    if (Sage.DBG) System.out.println("DVB capture thread terminating");
    try
    {
      caster.leaveGroup(group);
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR LEAVING MULTICAST SOCKET:" + e);
    }
  }

  private String broadcastAddr;
  private java.net.MulticastSocket caster;
  private java.net.InetAddress group;
}
