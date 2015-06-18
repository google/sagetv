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
 * @author jkardatzke
 */
public class RemotePusherClient implements Runnable
{
  public static final int REMOTE_PUSHER_CONTROL_PORT = 31199;
  private static final boolean COMM_DEBUG = Sage.getBoolean("remote_pusher_client_debug", false);

  // Command IDs in client requests
  public static final byte RMEDIACMD_INIT = 65;
  public static final byte RMEDIACMD_OPENURL = 66;
  public static final byte RMEDIACMD_START = 67;
  public static final byte RMEDIACMD_PLAY = 68;
  public static final byte RMEDIACMD_PAUSE = 69;
  public static final byte RMEDIACMD_SEEK = 70;
  public static final byte RMEDIACMD_RATECHANGE = 71;
  public static final byte RMEDIACMD_INACTIVEFILE = 72;
  public static final byte RMEDIACMD_CLOSE = 73;
  public static final byte RMEDIACMD_GET_PTS_DIFF = 74;

  // Event IDs in server async messages
  public static final byte RMEDIAMSG_SERVER_EOS = 1;
  public static final byte RMEDIAMSG_CLIENT_EOS = 2;
  public static final byte RMEDIAMSG_PTS_ROLLOVER = 3;
  public static final byte RMEDIAMSG_TRICKPLAY_EOS = 4;
  public static final String[] RMEDIA_MSG_NAMES = new String[] { "", "SERVER_EOS", "CLIENT_EOS",
    "PTS_ROLLOVER", "TRICKPLAY_EOS"
  };

  // Typecode for returned messages to client to distinguish between responses and messages
  public static final byte RMEDIAREPLY_RESPONSE = 1;
  public static final byte RMEDIAREPLY_MSG = 2;

  // Flags used in the openURL call
  public static final byte OPENURLFLAG_ACTIVEFILE = 0x01;
  public static final byte OPENURLFLAG_FASTSWITCH = 0x02;
  public static final byte OPENURLFLAG_IFRAMEALIGN = 0x04;

  public RemotePusherClient(MiniPlayer inMP)
  {
    mp = inMP;
  }

  // Connects to the specified server and starts up the reply listener thread for handling async messages
  public void connect(String hostname) throws java.io.IOException
  {
    if (Sage.DBG) System.out.println("RemotePusherClient trying to connect to server at: " + hostname);
    sake = java.nio.channels.SocketChannel.open();
    sake.connect(new java.net.InetSocketAddress(hostname, REMOTE_PUSHER_CONTROL_PORT));
    sake.socket().setKeepAlive(true);
    sake.socket().setTcpNoDelay(true);

    TimeoutHandler.registerTimeout(15000, sake);
    // Send over the init request for this session
    sendBuf.clear();
    sendBuf.putInt((RMEDIACMD_INIT << 24) | 1);
    sendBuf.put((byte)0x1); // version
    sendBuf.flip();
    while (sendBuf.hasRemaining())
      sake.write(sendBuf);

    // Now get back the sessionID we're going to use
    readBufferFully(5);
    TimeoutHandler.clearTimeout(sake);

    if (recvBuf.get() != RMEDIAREPLY_RESPONSE)
      throw new java.io.IOException("Didn't get RESPONSE header on init request!");
    sessionID = recvBuf.getInt();
    if (Sage.DBG) System.out.println("RemotePusherClient connected with sessionID=" + sessionID);
    alive = true;
    Pooler.execute(this, "RemotePusherClient", Thread.MAX_PRIORITY);
  }

  // Call this before you read a response to a request sent to the server
  private void waitForResponse() throws java.io.IOException
  {
    if (COMM_DEBUG) System.out.println("RemotePusherClient: waitForResponse()");
    long startTime = Sage.eventTime();
    synchronized (responseLock)
    {
      while (!responseReady && alive)
      {
        // We use 15 second socket timeouts...and those should cover us here because that should
        // close the socket and cause an exception in the receieve loop which then causes alive to be
        // set to false. But I've seen it in a state where somehow that didn't work right so let's double check it here.
        if (Sage.eventTime() - startTime > 20000)
        {
          if (Sage.DBG) System.out.println("WARNING: hit a timeout waiting in waitForResponse");
          throw new java.net.SocketTimeoutException();
        }
        try
        {
          responseLock.wait(500);
        }
        catch (InterruptedException ie){}
      }
    }
    if (COMM_DEBUG) System.out.println("RemotePusherClient: waitForResponse() DONE");
  }

  // Call this after reading a response from the server AND using the recvBuf
  private void doneWithResponse()
  {
    if (COMM_DEBUG) System.out.println("RemotePusherClient: doneWithResponse()");
    responseReady = false;
    synchronized (responseLock)
    {
      responseLock.notifyAll();
    }
  }

  public void run()
  {
    while (alive)
    {
      try
      {
        // This is purely for receiving
        if (COMM_DEBUG) System.out.println("RemotePusherClient: waiting for data on socket");
        readBufferFully(1);
        int msgType = recvBuf.get();
        if (COMM_DEBUG) System.out.println("RemotePusherClient: got message of type " + msgType);
        if (msgType == RMEDIAREPLY_RESPONSE)
        {
          // Set the flag that the response can be read now
          responseReady = true;
          synchronized (responseLock)
          {
            while (responseReady == true)
            {
              responseLock.notifyAll();
              try
              {
                responseLock.wait(500);
              }
              catch (InterruptedException ie){}
            }
          }
        }
        else if (msgType == RMEDIAREPLY_MSG)
        {
          // If another thread is awaiting a response and we get a message before it comes then we'd
          // end up clearing a timeout they have set and they could hang forever, so check if a timeout
          // is already set, and if so, just preserve it
          boolean preserveTimeout = TimeoutHandler.isTimeoutRegistered(sake);
          if (!preserveTimeout)
            TimeoutHandler.registerTimeout(15000, sake);
          readBufferFully(4);
          if (!preserveTimeout)
            TimeoutHandler.clearTimeout(sake);
          int msgCode = recvBuf.getInt();
          if (Sage.DBG) System.out.println("RemotePusherClient: got message code " + RMEDIA_MSG_NAMES[msgCode]);
          switch (msgCode)
          {
            case RMEDIAMSG_SERVER_EOS:
              serverEOS = true;
              break;
            case RMEDIAMSG_CLIENT_EOS:
              clientEOS = true;
              break;
            case RMEDIAMSG_PTS_ROLLOVER:
              ptsRollover = true;
              break;
            case RMEDIAMSG_TRICKPLAY_EOS:
              trickPlayEOS = true;
              break;
            default:
              if (Sage.DBG) System.out.println("Unknown message code received by RemotePusherClient of:" + msgCode);
              break;
          }
          mp.kickPusherThread();
        }
        else
        {
          if (Sage.DBG) System.out.println("ERROR Invalid reply type from the RemotePusher of:" + msgType);
          // We must abort now because we have no idea where the datastream is at
          close();
        }
      }
      catch (java.io.IOException e)
      {
        // If alive is false then we've been closed normally so don't print this as any kind of error
        if (alive && Sage.DBG) System.out.println("ERROR with RemotePusherClient of:" + e);
        alive = false;
        break;
      }
    }
    if (sake != null)
    {
      try
      {
        sake.close();
      }
      catch (Exception e){}
      sake = null;
    }
  }

  public void openFile(String filePath, String fileFormat, boolean activeFile, boolean fastSwitch) throws java.io.IOException
  {
    if (sendBuf == null || sake == null || recvBuf == null)
      throw new java.io.IOException("RemotePusherClient is not connected");
    if (COMM_DEBUG) System.out.println("RemotePusherClient: openFile path=" + filePath + " format=" + fileFormat +
        " active=" + activeFile + " fastSwitch=" + fastSwitch);
    sendBuf.clear();
    byte[] filePathBytes = filePath.getBytes(Sage.BYTE_CHARSET);
    byte[] formatBytes = fileFormat.getBytes(Sage.BYTE_CHARSET);
    sendBuf.putInt((RMEDIACMD_OPENURL << 24) | (filePathBytes.length + formatBytes.length + 5));
    sendBuf.putShort((short)(filePath.length() & 0xFFFF));
    sendBuf.put(filePathBytes);
    sendBuf.putShort((short)(fileFormat.length() & 0xFFFF));
    sendBuf.put(formatBytes);
    sendBuf.put((byte)(OPENURLFLAG_IFRAMEALIGN | (activeFile ? OPENURLFLAG_ACTIVEFILE : 0) |
        (fastSwitch ? OPENURLFLAG_FASTSWITCH : 0)));
    if (fastSwitch)
    {
      serverEOS = clientEOS = trickPlayEOS = ptsRollover = false;
    }
    sendBuf.flip();
    while (sendBuf.hasRemaining())
      sake.write(sendBuf);
    TimeoutHandler.registerTimeout(15000, sake);
    waitForResponse();
    readBufferFully(20);
    TimeoutHandler.clearTimeout(sake);
    int result = recvBuf.getInt();
    firstPTS = recvBuf.getLong();
    duration = recvBuf.getLong();
    doneWithResponse();
    if (Sage.DBG) System.out.println("RemotePusherClient opened file firstPTS=" + firstPTS + " duration=" + duration);
    if (COMM_DEBUG) System.out.println("RemotePusherClient: opened file OK result=" + result + " firstPTS=" + firstPTS +
        " dur=" + duration);
  }

  public boolean sendStart() throws java.io.IOException
  {
    if (COMM_DEBUG) System.out.println("RemotePusherClient: sendStart()");
    return sendSimpleCmd(RMEDIACMD_START) == 1;
  }

  public boolean sendPlay() throws java.io.IOException
  {
    if (COMM_DEBUG) System.out.println("RemotePusherClient: sendPlay()");
    return sendSimpleCmd(RMEDIACMD_PLAY) == 1;
  }

  public boolean sendPause() throws java.io.IOException
  {
    if (COMM_DEBUG) System.out.println("RemotePusherClient: sendPause()");
    trickPlayEOS = false;
    return sendSimpleCmd(RMEDIACMD_PAUSE) == 1;
  }

  public boolean sendInactiveFile() throws java.io.IOException
  {
    if (COMM_DEBUG) System.out.println("RemotePusherClient: sendInactiveFile()");
    return sendSimpleCmd(RMEDIACMD_INACTIVEFILE) == 1;
  }

  public boolean sendClose() throws java.io.IOException
  {
    if (COMM_DEBUG) System.out.println("RemotePusherClient: sendClose()");
    return sendSimpleCmd(RMEDIACMD_CLOSE) == 1;
  }

  public int getPTSTimeDiffMillis() throws java.io.IOException
  {
    if (COMM_DEBUG) System.out.println("RemotePusherClient: getPTSTimeDiffMillis()");
    return sendSimpleCmd(RMEDIACMD_GET_PTS_DIFF);
  }

  private int sendSimpleCmd(byte cmd) throws java.io.IOException
  {
    if (sendBuf == null || sake == null || recvBuf == null)
      throw new java.io.IOException("RemotePusherClient is not connected");
    if (COMM_DEBUG) System.out.println("RemotePusherClient: sending simple command of " + cmd);
    sendBuf.clear();
    sendBuf.putInt(cmd << 24);
    sendBuf.flip();
    while (sendBuf.hasRemaining())
      sake.write(sendBuf);
    TimeoutHandler.registerTimeout(15000, sake);
    waitForResponse();
    readBufferFully(4);
    TimeoutHandler.clearTimeout(sake);
    int response = recvBuf.getInt();
    doneWithResponse();
    if (COMM_DEBUG) System.out.println("RemotePusherClient: got simple reply of " + response);
    return response;
  }

  public boolean sendSeek(long seekTimeMillis) throws java.io.IOException
  {
    if (sendBuf == null || sake == null || recvBuf == null)
      throw new java.io.IOException("RemotePusherClient is not connected");
    if (COMM_DEBUG) System.out.println("RemotePusherClient: sendSeek(" + seekTimeMillis + ")");
    serverEOS = clientEOS = trickPlayEOS = false;
    sendBuf.clear();
    sendBuf.putInt((RMEDIACMD_SEEK << 24) | 8);
    sendBuf.putLong(seekTimeMillis);
    sendBuf.flip();
    while (sendBuf.hasRemaining())
      sake.write(sendBuf);
    TimeoutHandler.registerTimeout(15000, sake);
    waitForResponse();
    readBufferFully(4);
    TimeoutHandler.clearTimeout(sake);
    int response = recvBuf.getInt();
    doneWithResponse();
    if (COMM_DEBUG) System.out.println("RemotePusherClient: seek response=" + response);
    return response == 1;
  }

  public boolean sendRateChange(float newRate) throws java.io.IOException
  {
    if (sendBuf == null || sake == null || recvBuf == null)
      throw new java.io.IOException("RemotePusherClient is not connected");
    if (newRate <= 1.0f)
      serverEOS = clientEOS = trickPlayEOS = false;
    if (COMM_DEBUG) System.out.println("RemotePusherClient: sendRateChange(" + newRate + ")");
    sendBuf.clear();
    sendBuf.putInt((RMEDIACMD_RATECHANGE << 24) | 4);
    sendBuf.putInt((int)Math.round(newRate * 256));
    sendBuf.flip();
    while (sendBuf.hasRemaining())
      sake.write(sendBuf);
    TimeoutHandler.registerTimeout(15000, sake);
    waitForResponse();
    readBufferFully(4);
    TimeoutHandler.clearTimeout(sake);
    int response = recvBuf.getInt();
    doneWithResponse();
    if (COMM_DEBUG) System.out.println("RemotePusherClient: rate change response=" + response);
    return response == 1;
  }

  public void close()
  {
    try
    {
      sendClose();
    }
    catch (Exception e){}
    alive = false;
    try
    {
      sake.close();
    }
    catch (Exception e){}
    sake = null;
  }

  public boolean isAlive() { return alive; }

  public int getSessionID() { return sessionID; }

  private void readBufferFully(int size) throws java.io.IOException
  {
    recvBuf.clear().limit(size);
    do{
      int x = sake.read(recvBuf);
      if (x < 0)
        throw new java.io.EOFException();
    } while (recvBuf.remaining() > 0);
    recvBuf.flip();
  }

  public boolean didPTSRollover() { return ptsRollover; }
  public boolean isServerEOS() { return serverEOS; }
  public boolean isClientEOS() { return clientEOS; }
  public long getFirstPTS() { return firstPTS; }
  public long getDurationMillis() { return duration; }
  public boolean isTrickPlayEOS() { return trickPlayEOS; }

  private java.nio.channels.SocketChannel sake;
  private int sessionID;
  private java.nio.ByteBuffer sendBuf = java.nio.ByteBuffer.allocate(4096);
  private java.nio.ByteBuffer recvBuf = java.nio.ByteBuffer.allocate(512);
  private boolean alive;
  private final Object responseLock = new Object();
  private boolean responseReady = false;
  private boolean serverEOS = false;
  private boolean clientEOS = false;
  private boolean ptsRollover = false;
  private boolean trickPlayEOS = false;
  private long firstPTS;
  private long duration;
  private MiniPlayer mp;
}
