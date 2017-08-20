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
package sage.msg;

import sage.Sage;
/**
 *
 * @author  Narflex
 */
public class MsgManager implements Runnable
{
  public static final int EPG_DATA_MSG_TYPE = 10;
  public static final int CAPTURE_STREAM_FORMAT_MSG_TYPE = 11;
  public static final int CAPTURE_DEVICE_RESET_MSG_TYPE = 101;
  // System messages are all greater than this
  public static final int SYSTEM_MSG_TYPE_BASE = 1000;
  private static boolean DEBUG_MSGS = Sage.getBoolean("debug_msgs", false);
  private static final String STV_MSG_FILE = "sagetvmsgs.log";
  private static final int MAX_MESSAGE_DATA_SIZE=1000000;

  private static MsgManager chosenOne;
  private static final Object chosenOneLock = new Object();

  public static MsgManager getInstance() {
    if (chosenOne == null) {
      synchronized (chosenOneLock) {
        if (chosenOne == null) {
          chosenOne = new MsgManager();
        }
      }
    }
    return chosenOne;
  }
  /** Creates a new instance of MsgManager */
  private MsgManager()
  {
    queue = new java.util.Vector();
    sysMsgVec = new java.util.Vector();
    alertLevel = Sage.getInt("msg/curr_alert_level", 0);
    loadSystemMessages();
  }

  public void init()
  {
    alive = true;
  }

  public void kill()
  {
    alive = false;
    synchronized (queue)
    {
      queue.notifyAll();
    }
  }

  public static void postMessage(SageMsg msg)
  {
    getInstance().postMessageImpl(msg);
  }

  protected void postMessageImpl(SageMsg msg)
  {
    synchronized (queue)
    {
      queue.add(msg);
      queue.notifyAll();
    }
  }

  public static void sendMessage(SageMsg msg)
  {
    getInstance().sendMessageImpl(msg);
  }

  protected synchronized void sendMessageImpl(SageMsg msg)
  {
    processMsg(msg);
  }

  public int getAlertLevel()
  {
    return alertLevel;
  }

  public void clearAlertLevel()
  {
    alertLevel = 0;
    Sage.putInt("msg/curr_alert_level", 0);
    sage.Catbert.distributeHookToAll("SystemStatusChanged", sage.Pooler.EMPTY_OBJECT_ARRAY);
  }

  public SystemMessage[] getSystemMessages()
  {
    return (SystemMessage[]) sysMsgVec.toArray(new SystemMessage[0]);
  }

  public int getSystemMessageCount() {
    return sysMsgVec.size();
  }

  public void removeSystemMessage(SystemMessage removeMe)
  {
    if (removeMe == null) return;
    synchronized (sysMsgVec)
    {
      if (sysMsgVec.remove(removeMe))
        saveSystemMessages();
      else
      {
        // Try to find a matching one since this could be from a client request that doesn't have object equality
        for (int i = 0; i < sysMsgVec.size(); i++)
        {
          SystemMessage sm = (SystemMessage) sysMsgVec.get(i);
          if (sm.type == removeMe.type && sm.priority == removeMe.priority &&
              sm.timestamp == removeMe.timestamp &&
              sm.getEndTimestamp() == removeMe.getEndTimestamp() &&
              sm.getRepeatCount() == removeMe.getRepeatCount() &&
              (sm.source == removeMe.source || (removeMe.source != null && removeMe.source.equals(sm.source))))
          {
            sysMsgVec.remove(i);
            break;
          }
        }
      }
      if (alertLevel > 0 && sysMsgVec.isEmpty())
      {
        alertLevel = 0;
        Sage.putInt("msg/curr_alert_level", 0);
      }
      sage.Catbert.distributeHookToAll("SystemStatusChanged", sage.Pooler.EMPTY_OBJECT_ARRAY);
    }
  }

  public void clearSystemMessages()
  {
    synchronized (sysMsgVec)
    {
      if (!sysMsgVec.isEmpty())
      {
        sysMsgVec.clear();
        saveSystemMessages();
        if (alertLevel > 0)
        {
          alertLevel = 0;
          Sage.putInt("msg/curr_alert_level", 0);
        }
        sage.Catbert.distributeHookToAll("SystemStatusChanged", sage.Pooler.EMPTY_OBJECT_ARRAY);
      }
    }
  }

  public void run()
  {
    if (!Sage.client)
    {
      // Launch another thread for receiving messages via a socket
      Thread t = new Thread(new Runnable()
      {
        public void run()
        {
          java.net.ServerSocket ss = null;
          do {
          try
          {
            ss = new java.net.ServerSocket(6968, 5, java.net.InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
          }
          catch (java.io.IOException e)
          {
              if (Sage.DBG) System.out.println("ERROR Could not create server socket for msg receiver:" + e);
              if (!alive)
            return;
              if (Sage.DBG) System.out.println("Waiting to retry to open MsgManager server socket again...");
              try {Thread.sleep(15000);}catch(Exception ex){}
          }
          } while (ss == null);
          if (Sage.DBG) System.out.println("Spawned thread for MsgManger server socket listening...");
          while (alive)
          {
            try
            {
              java.net.Socket sake = ss.accept();
              if (Sage.DBG) System.out.println("MsgManager got socket connection from: " + sake);
              sage.Pooler.execute(new MsgManagerSocket(sake), "MsgMgrClient", Thread.MIN_PRIORITY);
            }
            catch (java.io.IOException ioe)
            {
              if (Sage.DBG) System.out.println("Error in socket accept for MsgManager of:" + ioe);
            }
          }
        }
      }, "MsgMgrSocket");
      t.setPriority(Thread.MIN_PRIORITY);
      t.setDaemon(true);
      t.start();
    }
    while (alive)
    {
      SageMsg nextMsg = null;
      synchronized (queue)
      {
        if (queue.isEmpty())
        {
          try
          {
            queue.wait();
          }
          catch (InterruptedException e){}
          continue;
        }
        nextMsg = (SageMsg)queue.firstElement();
      }

      processMsg(nextMsg);
      synchronized (queue)
      {
        queue.remove(0);
        queue.notifyAll();
      }
    }
  }

  private synchronized void processMsg(SageMsg msg)
  {
    if (DEBUG_MSGS && Sage.DBG) System.out.println("MsgManager (queueSize=" + queue.size() + ") is processing message:" + msg);
    try
    {
      if (msg.getType() >= SYSTEM_MSG_TYPE_BASE && msg instanceof SystemMessage)
        processSysMsg(msg);
      else
      {
        switch (msg.getType())
        {
          case EPG_DATA_MSG_TYPE:
            processEPGData(msg);
            break;
          case CAPTURE_STREAM_FORMAT_MSG_TYPE:
            processFormatData(msg);
            break;
          case CAPTURE_DEVICE_RESET_MSG_TYPE:
            processCapDevReset(msg);
            break;
        }
      }
    }
    catch (Throwable t)
    {
      System.out.println("ERROR processing message of:" + t);
      t.printStackTrace();
    }
  }

  private sage.CaptureDevice getCaptureDeviceFromSource(String srcName)
  {
    int lastDash = srcName.lastIndexOf("-");
    int devNum = 0;
    try
    {
      devNum = (lastDash != -1) ? Integer.parseInt(srcName.substring(lastDash + 1)) : 0;
      if (lastDash != -1)
        srcName = srcName.substring(0, lastDash);
    }catch (NumberFormatException nfe){} // not a duplicate device with numbering
    sage.CaptureDevice[] capDevs = sage.MMC.getInstance().getCaptureDevices();
    for (int i = 0; i < capDevs.length; i++)
    {
      String testDevName;
      testDevName = (capDevs[i] instanceof sage.DVBCaptureDevice) ?
          ((sage.DVBCaptureDevice) capDevs[i]).getLinuxVideoDevice() : capDevs[i].getCaptureDeviceName();
          if (capDevs[i].getCaptureDeviceNum() == devNum && srcName.equals(testDevName) &&
              !capDevs[i].isNetworkEncoder())
          {
            return capDevs[i];
          }
    }
    // Try network capture devices now too
    for (int i = 0; i < capDevs.length; i++)
    {
      String testDevName;
      if (capDevs[i] instanceof sage.NetworkCaptureDevice)
      {
        testDevName = ((sage.NetworkCaptureDevice) capDevs[i]).getNetworkSourceName();
        if (srcName.equals(testDevName))
        {
          return capDevs[i];
        }
      }
    }
    return null;
  }

  private void processFormatData(SageMsg msg)
  {
    String srcName = msg.getSource().toString();
    sage.CaptureDevice capDev = getCaptureDeviceFromSource(srcName);
    if (capDev == null)
    {
      //if (Sage.DBG) System.out.println("ERROR couldn't find capture source to match epg data:" + srcName);
      return;
    }
    sage.MediaFile mf = sage.SeekerSelector.getInstance().getCurrRecordMediaFile(capDev);
    if (mf != null)
    {
      sage.media.format.ContainerFormat cf;
      String formatString;
      try
      {
        formatString = new String((byte[])msg.getData(), Sage.BYTE_CHARSET);
      }
      catch (java.io.UnsupportedEncodingException e)
      {
        formatString = new String((byte[])msg.getData());
      }
      if (formatString.startsWith("AV-INF|"))
        formatString = formatString.substring("AV-INF|".length());
      else if (formatString.startsWith("MPEG") && !formatString.startsWith("f="))
        formatString = "f=" + formatString;
      cf = sage.media.format.ContainerFormat.buildFormatFromString(formatString);
      if (cf == null || cf.getFormatName() == null || cf.getFormatName().toLowerCase().startsWith("unknown"))
      {
        if (Sage.DBG) System.out.println("Invalid media format returned in message for " + mf + "; rejecting it: " + cf);
        return;
      }
      if (Sage.DBG) System.out.println("Setting media file format for " + mf + " to be " + cf);
      mf.setMediafileFormat(cf);
      sage.VideoFrame.kickAll(); // in case someone is waiting on a format detection to start playback
    }
  }

  private void processEPGData(SageMsg msg)
  {
    String srcName = msg.getSource().toString();
    sage.CaptureDevice capDev = getCaptureDeviceFromSource(srcName);
    if (capDev == null)
    {
      //if (Sage.DBG) System.out.println("ERROR couldn't find capture source to match epg data:" + srcName);
      return;
    }
    sage.CaptureDeviceInput cdi = capDev.getActiveInput();
    if (cdi == null) return;
    long provID = cdi.getProviderID();
    sage.EPGDataSource ds = sage.EPG.getInstance().getSourceForProviderID(provID);
    if (ds != null)
      ds.processEPGDataMsg(msg);
  }

  private void processCapDevReset(SageMsg msg)
  {
    String srcName = msg.getSource().toString();
    sage.CaptureDevice capDev = getCaptureDeviceFromSource(srcName);
    if (capDev == null)
    {
      //if (Sage.DBG) System.out.println("ERROR couldn't find capture source to match epg data:" + srcName);
      return;
    }
    if (Sage.DBG) System.out.println("Sending the core a capture device reset request for:" + capDev);
    sage.SeekerSelector.getInstance().requestDeviceReset(capDev);
  }

  private void processSysMsg(SageMsg msg)
  {
    synchronized (sysMsgVec)
    {
      SystemMessage smsg = (SystemMessage) msg;
      // Check to see if we can consolidate it first
      for (int i = 0; i < sysMsgVec.size(); i++)
      {
        SystemMessage tmsg = (SystemMessage) sysMsgVec.get(i);
        if (tmsg.canConsolidateMessage(smsg))
        {
          tmsg.consolidateMessage(smsg);
          sage.Catbert.distributeHookToAll("SystemStatusChanged", sage.Pooler.EMPTY_OBJECT_ARRAY);
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.SYSTEM_MESSAGE_POSTED,
              new Object[] { sage.plugin.PluginEventManager.VAR_SYSTEMMESSAGE, tmsg });
          saveSystemMessages();
          return;
        }
      }

      // New message, add it to the queue; update the global alert level if applicable and
      // save the new message log
      sysMsgVec.add(smsg);

      // Narflex: 1/14/16 - I wanted a feature where I could get the HALT detected message to increase the
      // global alert level only in the error case...but not in the warning case. So this is done in a way that
      // anybody who had a message type disabled before...will still have it enabled now. But then an extra property
      // will show up relating to the specific alert levels for that message to allow a finer grain of control. Currently
      // this would only apply to the HALT message since no other messages have multiple levels for the same message.
      if (smsg.getPriority() > alertLevel && (Sage.getBoolean("msg/alert_enabled/" + smsg.getType(), true) ||
          Sage.getBoolean("msg/alert_enabled/" + smsg.getType() + "-" + smsg.getPriority(), false)))
      {
        alertLevel = smsg.getPriority();
        Sage.putInt("msg/curr_alert_level", alertLevel);
      }

      sage.Catbert.distributeHookToAll("SystemStatusChanged", sage.Pooler.EMPTY_OBJECT_ARRAY);
      sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.SYSTEM_MESSAGE_POSTED,
          new Object[] { sage.plugin.PluginEventManager.VAR_SYSTEMMESSAGE, smsg });
      saveSystemMessages();
    }
  }

  private void loadSystemMessages()
  {
    if (Sage.client) return;
    // Load all the existing messages from the message log file
    java.io.File msgFile = new java.io.File(Sage.getLogPath(STV_MSG_FILE));
    if (!msgFile.isFile())
    {
      // Check for the autobackup file
      java.io.File backupMsgFile = new java.io.File(Sage.getLogPath(STV_MSG_FILE + ".autobackup"));
      if (!backupMsgFile.isFile() || !backupMsgFile.renameTo(msgFile))
        return;
    }
    java.io.BufferedReader inStream = null;
    try
    {
      inStream = new java.io.BufferedReader(new java.io.FileReader(msgFile));
      String line = inStream.readLine();
      while (line != null)
      {
        SystemMessage newMsg = SystemMessage.buildMsgFromString(line);
        if (newMsg != null)
          sysMsgVec.add(newMsg);
        line = inStream.readLine();
      }
      if (Sage.DBG) System.out.println("Loaded " + sysMsgVec.size() + " messages from system message log file");
    }
    catch (Exception e)
    {
      System.out.println("ERROR loading system message log file of:" + e);
    }
    finally
    {
      if (inStream != null)
      {
        try
        {
          inStream.close();
          inStream = null;
        }
        catch (Exception e){}
      }
    }
  }

  private void saveSystemMessages()
  {
    if (Sage.client) return;
    // Save all the exsiting messages to the message log file
    java.io.File msgFile = new java.io.File(Sage.getLogPath(STV_MSG_FILE));
    java.io.File backupMsgFile = new java.io.File(Sage.getLogPath(STV_MSG_FILE + ".autobackup"));
    java.io.PrintWriter outStream = null;
    try
    {
      outStream = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(backupMsgFile)));
      for (int i = 0; i < sysMsgVec.size(); i++)
      {
        SystemMessage msg = (SystemMessage) sysMsgVec.get(i);
        outStream.println(msg.getPersistentString());
      }
      outStream.close();
      outStream = null;
      if (!msgFile.isFile() || msgFile.delete())
      {
        if (!backupMsgFile.renameTo(msgFile) && Sage.DBG)
          System.out.println("ERROR Could not rename the autobackup system message log file to the main system message log file!");
      }
      else if (Sage.DBG)
        System.out.println("ERROR Could not remove existing system message log file to put the new one in!");
    }
    catch (Exception e)
    {
      System.out.println("ERROR saving system message log file of:" + e);
    }
    finally
    {
      if (outStream != null)
      {
        try
        {
          outStream.close();
          outStream = null;
        }
        catch (Exception e){}
      }
    }
  }

  private class MsgManagerSocket implements Runnable
  {
    public MsgManagerSocket(java.net.Socket inSake)
    {
      sake = inSake;
    }
    public void run()
    {
      java.io.DataInputStream inStream = null;
      java.io.DataOutputStream outStream = null;
      try
      {
        inStream = new java.io.DataInputStream(sake.getInputStream());
        outStream = new java.io.DataOutputStream(sake.getOutputStream());
        while (alive)
        {
          // Each msg has the following format:
          // 32-bit type
          // 32-bit priority
          // 32-bit string length
          // var length string for Source
          // 32-bit data length
          // var length byte array for data
          // We then just reply with a 32-bit value of '1'
          int type = inStream.readInt();
          int priority = inStream.readInt();
          int len = inStream.readInt();
          if ( len > MAX_MESSAGE_DATA_SIZE || len < 0){
            throw new IllegalArgumentException("length out of range: "+len );
          }
          byte[] srcData = new byte[len];
          inStream.readFully(srcData);
          String srcName = new String(srcData).trim();
          len = inStream.readInt();
          if ( len > MAX_MESSAGE_DATA_SIZE || len < 0){
            throw new IllegalArgumentException("length out of range: "+len );
          }

          byte[] rawData = new byte[len];
          inStream.readFully(rawData);
          //					if (Sage.DBG) System.out.println("MsgMgrSocket received message type=" + type + " src=" + srcName + " data=" + new String(rawData));
          // Reply so they know we got it OK
          outStream.writeInt(1);
          outStream.flush();
          // Now create the message and post it
          postMessage(new SageMsg(type, srcName, rawData, priority));

        }
      }
      catch (java.io.IOException e)
      {
        // These are OK, they will happen when the message sender disconnects, it doesn't
        // need to keep its channel open, it can just reconnect
      }
      finally
      {
        try{sake.close();}catch(Exception e){}
      }
    }
    private java.net.Socket sake;
  }

  private java.util.Vector queue;
  private boolean alive;

  private java.util.Vector sysMsgVec;

  private int alertLevel;
}
