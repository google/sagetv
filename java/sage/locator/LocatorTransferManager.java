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
package sage.locator;

/**
 *
 * @author Narflex
 */
public class LocatorTransferManager implements Runnable
{

  /** Creates a new instance of LocatorTransferManager */
  public LocatorTransferManager()
  {
    transfersAllowed = !sage.Sage.getBoolean("locator/suspend_transfers", false);
    failureRetryInterval = sage.Sage.getLong("locator/transfer_failure_retry_period_test", 30000); // MAKE THIS 300000 FOR RELEASE!!!
  }

  public void run()
  {
    if (sage.Sage.DBG) System.out.println("Starting up the LocatorTransferManager...");
    alive = true;
    while (alive)
    {
      TransferAgent currAgent = null;
      synchronized (transferQueueMap)
      {
        if (transferQueueMap.isEmpty() || !transfersAllowed)
        {
          try
          {
            transferQueueMap.wait(300000);
          }
          catch (Exception e){}
          continue;
        }
        else
        {
          long failureWaitRemain = Long.MAX_VALUE;
          java.util.Iterator walker = transferQueueMap.values().iterator();
          while (walker.hasNext() && currAgent == null)
          {
            currAgent = (TransferAgent) walker.next();
            if (currAgent.lastFailureTime != 0 && sage.Sage.time() - currAgent.lastFailureTime < failureRetryInterval)
            {
              failureWaitRemain = Math.min(failureWaitRemain, failureRetryInterval - (sage.Sage.time() - currAgent.lastFailureTime));
              currAgent = null;
            }
          }
          if (currAgent == null)
          {
            if (sage.Sage.DBG) System.out.println("Locator Transfers are all in a failure state; wait " + failureWaitRemain + " msec for retry...");
            try
            {
              transferQueueMap.wait(Math.min(failureWaitRemain, 300000) + 2000); // add 2 seconds so we don't align and have to wait a few milliseconds
            }
            catch (Exception e){}
            continue;
          }
        }
      }

      if (currAgent.verifyCurrentProgress())
      {
        if (sage.Sage.DBG) System.out.println("Message transfer has already been completed; removing it from the queue: " + currAgent.theMsg);
        currAgent.confirmCompletion();
        transferQueueMap.remove(currAgent.theMsg);
        continue;
      }

      try
      {
        currAgent.setupTransfer();
        while (transfersAllowed && alive && transferQueueMap.containsKey(currAgent.theMsg))
        {
          boolean moreToDo = currAgent.continueTransfer();
          if (!moreToDo)
          {
            if (sage.Sage.DBG) System.out.println("Message transfer has been completed for: " + currAgent.theMsg);
            currAgent.confirmCompletion();
            transferQueueMap.remove(currAgent.theMsg);
            break;
          }

          // NOTE: This is where would would handle bandwidth throttling for message transfers
        }
      }
      catch (java.io.IOException e)
      {
        if (sage.Sage.DBG) System.out.println("Error with message transfer; move it to the back of the queue msg=" + currAgent.theMsg + " error=" + e);
        if (sage.Sage.DBG) e.printStackTrace();
        currAgent.lastFailureTime = sage.Sage.time();
        transferQueueMap.remove(currAgent.theMsg);
        transferQueueMap.put(currAgent.theMsg, currAgent);
      }
      finally
      {
        currAgent.closeTransfer();
      }
    }
  }

  public void goodbye()
  {
    alive = false;
    synchronized (transferQueueMap)
    {
      transferQueueMap.notifyAll();
    }
  }

  public void queueMessageTransfer(LocatorMsg theMsg)
  {
    synchronized (transferQueueMap)
    {
      if (!transferQueueMap.containsKey(theMsg))
      {
        if (sage.Sage.DBG) System.out.println("New message has been added to the transfer queue of: " + theMsg);
        transferQueueMap.put(theMsg, new TransferAgent(theMsg));
        transferQueueMap.notifyAll();
      }
    }
  }

  // The downloadRequested flag should be set to false before calling this
  public void dequeueMessageTransfer(LocatorMsg theMsg)
  {
    synchronized (transferQueueMap)
    {
      if (transferQueueMap.containsKey(theMsg))
      {
        if (sage.Sage.DBG) System.out.println("Message has been removed from the transfer queue of: " + theMsg);
        TransferAgent transferer = (TransferAgent) transferQueueMap.get(theMsg);
        transferQueueMap.remove(theMsg);
        transferQueueMap.notifyAll();
      }
    }
  }

  public boolean isMessageInTransferQueue(LocatorMsg theMsg)
  {
    return transferQueueMap.containsKey(theMsg);
  }

  public boolean isMessageCurrentlyTransferring(LocatorMsg theMsg)
  {
    TransferAgent transferer = (TransferAgent) transferQueueMap.get(theMsg);
    return transferer != null && transferer.active;
  }

  public void pauseAllTransfers()
  {
    if (transfersAllowed)
    {
      synchronized (transferQueueMap)
      {
        transfersAllowed = false;
        transferQueueMap.notifyAll();
      }
      sage.Sage.putBoolean("locator/suspend_transfers", true);
    }
  }

  public boolean areAllTransfersPaused()
  {
    return !transfersAllowed;
  }

  public void resumeAllTransfers()
  {
    if (!transfersAllowed)
    {
      synchronized (transferQueueMap)
      {
        transfersAllowed = true;
        transferQueueMap.notifyAll();
      }
      sage.Sage.putBoolean("locator/suspend_transfers", false);
    }
  }

  // Returns on a scale of 0-100
  public float getMessageDownloadProgress(LocatorMsg theMsg)
  {
    TransferAgent transferer = (TransferAgent) transferQueueMap.get(theMsg);
    return (transferer == null) ? 0.0f : transferer.getProgress();
  }

  // This one is the thread that actually does the byte transfers; the main one in the class is just for managing it all.
  // This is also the class that we use to encapsulate the transfer information associated with a LocatorMsg
  private static class TransferAgent
  {
    public TransferAgent(LocatorMsg inMsg)
    {
      theMsg = inMsg;
    }

    // Returns true if this transfer is already complete
    public boolean verifyCurrentProgress()
    {
      // Check on our progress so far
      mediaInfoIndex = 0;
      boolean emptyFound = false;
      for (int i = 0; i < theMsg.media.length; i++)
      {
        totalSize += theMsg.media[i].size;
        if (theMsg.media[i].localPath != null)
        {
          if (theMsg.media[i].localPath.isFile())
          {
            transferredSize += theMsg.media[i].localPath.length();
            if (!emptyFound && theMsg.media[i].localPath.length() == theMsg.media[i].size)
            {
              mediaInfoIndex = i + 1;
            }
          }
        }
        else
          emptyFound = true;
      }
      return mediaInfoIndex >= theMsg.media.length;
    }

    public float getProgress()
    {
      return (transferredSize == totalSize) ? 100.0f : (100 * (((float) transferredSize) / totalSize));
    }

    // This is called to initiate the transfer connection
    public void setupTransfer() throws java.io.IOException
    {
      active = true;
      if (sage.Sage.DBG) System.out.println("Setting up remote file transfer from " + theMsg.fromHandle + " for file " + theMsg.media[mediaInfoIndex].localPath);
      // First thing we do is get the IP:port of our friend from the server
      String friendIPStr = null;
      try
      {
        friendIPStr = sage.SageTV.getLocatorClient().lookupFriendIP(theMsg.fromHandle);
      }
      catch (Exception e)
      {
        if (sage.Sage.DBG) e.printStackTrace();
        throw new java.io.IOException("Nested error with locator lookup of friend IP of:" + e);
      }
      if (friendIPStr == null)
        throw new java.io.IOException("Unable to connect to friend " + theMsg.fromHandle + " because we could not resolve its IP with the Locator!");

      // Now resolve it to IP and port
      friendPort = 31099;
      friendIP = null;
      int idx = friendIPStr.indexOf(":");
      if (idx != -1)
      {
        friendIP = friendIPStr.substring(0, idx);
        try
        {
          friendPort = Integer.parseInt(friendIPStr.substring(idx + 1));
        }
        catch (NumberFormatException nfe)
        {
          throw new java.io.IOException("Invalid numeric port formatting of: " + friendIPStr);
        }
      }
      else
        friendIP = friendIPStr;
      if (sage.Sage.DBG) System.out.println("Locator server resolved IP of " + theMsg.fromHandle + " to " + friendIPStr);
    }

    // Returns true if the file transfer was setup and should continue; false if there's nothing
    // to be transferred. It'll throw an exception if there was an error.
    private boolean requestFileTransfer(MediaInfo info) throws java.io.IOException
    {
      currLocalFile = info.localPath;

      // We send eight bytes first.
      // The first byte is the version byte and is 1.
      // The next 6 bytes are the value of the downloadID (first 2 bytes are zero since its a 32-bit ID)
      // The next byte is 3 which indicates external file transfer mode
      bb = java.nio.ByteBuffer.allocate(512);
      bb.clear().limit(8);
      bb.put((byte)1).put((byte)0).put((byte)0);
      bb.putInt(info.downloadID);
      bb.put((byte)3);
      bb.flip();
      while (bb.hasRemaining())
        sake.write(bb);

      // First we get back a single byte of '2' for an OK connect
      sage.TimeoutHandler.registerTimeout(timeout, sake);
      bb.clear().limit(1);
      do{
        int x = sake.read(bb);
        if (x < 0)
          throw new java.io.EOFException();
      } while (bb.remaining() > 0);
      sage.TimeoutHandler.clearTimeout(sake);
      bb.flip();
      if (bb.get() != 2)
        throw new java.io.IOException("Server did not return an OK reply on the initial connect!");

      // Now the server should be sending us back 8 bytes which indicate the file size. It'll return zero if the file does
      // not exist.
      // NOTE: WE'LL HAVE TO DO SOMETHING SMART LATER FOR DEALING WITH FILES THAT ARE TEMPORARILY OFFLINE VS. ONES THAT
      // ARE COMPLETELY GONE
      sage.TimeoutHandler.registerTimeout(timeout, sake);
      bb.clear().limit(8);
      do{
        int x = sake.read(bb);
        if (x < 0)
          throw new java.io.EOFException();
      } while (bb.remaining() > 0);
      sage.TimeoutHandler.clearTimeout(sake);
      bb.flip();
      targetFileSize = bb.getLong();
      if (targetFileSize != info.size)
      {
        totalSize += (targetFileSize - info.size);
        if (sage.Sage.DBG) System.out.println("Remote size for file transfer does NOT match the size in message. Using new size of:" + targetFileSize + " oldSize=" + info.size);
        info.size = targetFileSize;
        LocatorRegistrationClient.saveMessageProperties(new LocatorMsg[] { theMsg });
      }
      if (targetFileSize == 0)
      {
        return false;
      }

      // Now we send back eight bytes to indicate the offset in the file we should start at
      currFileOffset = currLocalFile.length(); // will be zero if it doesn't exist

      bb.clear().limit(8);
      bb.putLong(currFileOffset);
      bb.flip();
      while (bb.hasRemaining())
        sake.write(bb);

      // Be sure our target directory is created
      currLocalFile.getParentFile().mkdirs();

      // Now the server should start sending back bytes for the file transfer
      localRaf = new java.io.FileOutputStream(currLocalFile, true).getChannel();

      return true;
    }

    // This is called to cause the transfer which is already setup to continue its work. It returns
    // true if there's more work to do and false when its done.
    public boolean continueTransfer() throws java.io.IOException
    {
      if (mediaInfoIndex >= theMsg.media.length)
      {
        if (sage.Sage.DBG) System.out.println("ContinueTransfer detected we're past the last file. Signal completion!");
        return false;
      }

      if (sake == null)
      {
        // We need to setup the socket connection for this file transfer now
        sake = java.nio.channels.SocketChannel.open(new java.net.InetSocketAddress(friendIP, friendPort));

        if (!requestFileTransfer(theMsg.media[mediaInfoIndex]))
        {
          if (sage.Sage.DBG) System.out.println("Skipping transfer of remote file " + currLocalFile);
          try
          {
            sake.close();
          }
          catch (Exception e){}
          sake = null;
          mediaInfoIndex++;
          return true;
        }
      }

      long xferSize = targetFileSize - currFileOffset;
      long currSize = Math.min(xferSize, 16384);
      sage.TimeoutHandler.registerTimeout(timeout, sake);
      currSize = localRaf.transferFrom(sake, currFileOffset, currSize);
      sage.TimeoutHandler.clearTimeout(sake);
      currFileOffset += currSize;
      xferSize -= currSize;
      transferredSize += currSize;

      if (xferSize == 0)
      {
        // We've finished transferring this file
        // Send back the 32-bit return value of zero after close the local file
        localRaf.close();
        localRaf = null;
        bb.clear().limit(4);
        bb.putInt(0);
        bb.flip();
        while (bb.hasRemaining())
          sake.write(bb);
        sake.close();
        sake = null;
        if (sage.Sage.DBG) System.out.println("File transfer has been finished for " + currLocalFile);

        sage.MediaFile mf = theMsg.media[mediaInfoIndex].getMediaFile();
        String newPathStr = currLocalFile.toString();
        String relPathStr = theMsg.media[mediaInfoIndex].relativePath;
        String ourSubfolder = new java.io.File(newPathStr.substring(0, newPathStr.length() - relPathStr.length())).getName();
        String importPrefix = ourSubfolder + '/' + theMsg.media[mediaInfoIndex].relativePath.substring(0, theMsg.media[mediaInfoIndex].relativePath.length() -
            currLocalFile.getName().length());
        importPrefix = importPrefix.replace('\\', '/');
        if (mf != null)
        {
          // Refresh the metadata since now we're done writing the file
          if (sage.Sage.DBG) System.out.println("Refreshing MF metadata since the transfer is now complete:" + mf);
          mf.reinitializeMetadata(true, true, importPrefix);
        }
        else
        {
          // Now insert it into the library so its visible right away
          sage.MediaFile addedFile = sage.Wizard.getInstance().addMediaFile(currLocalFile, importPrefix, sage.MediaFile.ACQUISITION_AUTOMATIC_BY_IMPORT_PATH);
          if (addedFile != null)
          {
            if (sage.Sage.DBG) System.out.println("Added new MediaFile after file transfer has completed of:" + addedFile);
          }
        }

        mediaInfoIndex++;
      }
      return true;
    }

    // This will close the connection if it wasn't done so already do to a normal completion
    public void closeTransfer()
    {
      active = false;
      if (sake != null)
      {
        try
        {
          sake.close();
        }
        catch (Exception e){}
        sake = null;
      }
      if (localRaf != null)
      {
        try { localRaf.close(); } catch (Exception e){}
        localRaf = null;
      }
    }

    public void confirmCompletion()
    {
      if (!theMsg.downloadCompleted)
      {
        theMsg.downloadCompleted = true;
        LocatorRegistrationClient.saveMessageProperties(new LocatorMsg[] { theMsg });
      }
    }

    private LocatorMsg theMsg;
    private long totalSize;
    private long transferredSize;
    private int mediaInfoIndex; // index into the MediaInfo[] array for what we're currently transferring
    private boolean active;
    private java.nio.channels.SocketChannel sake;
    private long lastFailureTime;
    private String friendIP;
    private int friendPort;

    private java.io.File currLocalFile;
    private long currFileOffset;
    private long targetFileSize;
    private java.nio.channels.FileChannel localRaf;
    private java.nio.ByteBuffer bb;
  }

  // If transferring is paused on a global level
  private boolean transfersAllowed;

  private java.util.Map transferQueueMap = new java.util.LinkedHashMap();
  private boolean alive;

  private long failureRetryInterval;
  private static final long timeout = 15000;
}
