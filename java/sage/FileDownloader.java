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

import sage.io.BufferedSageFile;
import sage.io.LocalSageFile;
import java.io.IOException;
import java.net.URI;

public class FileDownloader extends SystemTask
{
  private static final int MP4_RESEEK_PREROLL_TIME = 1000;
  private static final boolean CIRC_DOWNLOAD_DEBUG = false;
  private static java.util.Map uiXferMap = new java.util.WeakHashMap();
  private static java.util.Map fileMap = new java.util.HashMap();
  // This stores the status results from file download operations....in a way this is a memory leak since it'll grow without bound;
  // but that growth should be very minimal and not cause any overall memory issue (similar to MetaImage caching all URL images forever)
  private static java.util.Map resultMap = new java.util.HashMap();
  public static FileDownloader getFileDownloader(UIManager uiMgr)
  {
    synchronized (uiXferMap)
    {
      if (uiXferMap.containsKey(uiMgr))
        return (FileDownloader) uiXferMap.get(uiMgr);
      FileDownloader rv = new FileDownloader(uiMgr);
      uiXferMap.put(uiMgr, rv);
      return rv;
    }
  }

  public FileDownloader(UIManager inUIMgr)
  {
    if (inUIMgr != null)
      uiMgrWeak = new java.lang.ref.WeakReference(inUIMgr);
  }

  public static FileDownloader getFileDownloader(java.io.File testFile)
  {
    synchronized (fileMap)
    {
      FileDownloader fd = (FileDownloader) fileMap.get(testFile);
      if (fd != null && !fd.isComplete())
        return fd;
    }
    return null;
  }

  public static Object getBGDownloadResult(java.io.File testFile)
  {
    return resultMap.get(testFile);
  }

  public static boolean isDownloading(java.io.File testFile)
  {
    return isDownloading(null, testFile);
  }
  public static boolean isDownloading(UIManager uiMgr, java.io.File testFile)
  {
    if (uiMgr == null)
    {
      return getFileDownloader(testFile) != null;
    }
    synchronized (uiXferMap)
    {
      FileDownloader fd = getFileDownloader(uiMgr);
      return (fd.myDestFile != null && fd.myDestFile.equals(testFile) && !fd.isComplete());
    }
  }

  public void cancel()
  {
    abort = true;
    if (taskThread != null)
    {
      if (Sage.DBG) System.out.println("Waiting for download thread to terminate...");
      try
      {
        // Forcibly close the HTTP connection if it's there so we don't worry about timeout issues
        if (urlinStream != null)
          urlinStream.close();
      }
      catch (Exception e){}
      try
      {
        taskThread.join(30000);
      }
      catch (InterruptedException e)
      {}
      if (Sage.DBG) System.out.println("Download thread has terminated.");
    }
  }

  public synchronized Object downloadCircularFile(String serverName, String srcFile, java.io.File destFile)
  {
    return downloadFile(serverName, srcFile, destFile, true);
  }
  // serverName can be null which means use the preferredServer, srcFile must be a file, it's downloaded to destFile which must also be a file
  public synchronized Object downloadFile(String serverName, String srcFile, java.io.File destFile)
  {
    return downloadFile(serverName, srcFile, destFile, false);
  }
  public synchronized Object downloadFile(String serverName, String srcFile, java.io.File destFile, boolean circular)
  {
    return downloadFile(serverName, srcFile, destFile, circular, null);
  }
  public synchronized Object downloadFile(String serverName, String srcFile, java.io.File destFile, boolean circular, java.util.Properties requestProps)
  {
    return downloadFile(serverName, srcFile, destFile, circular, requestProps, false);
  }
  public synchronized Object downloadFile(String serverName, String srcFile, java.io.File destFile, boolean circular, java.util.Properties requestProps, boolean inSynchronous)
  {
    return downloadFile(serverName, srcFile, destFile, circular, requestProps, inSynchronous, false);
  }
  public synchronized Object downloadFile(String serverName, String srcFile, java.io.File destFile, boolean circular, java.util.Properties requestProps, boolean inSynchronous,
      boolean captureMode)
  {
    return downloadFile(serverName, srcFile, destFile, circular, requestProps, inSynchronous, captureMode, null);
  }
  public synchronized Object downloadFile(String serverName, String srcFile, java.io.File destFile, boolean circular, java.util.Properties requestProps, boolean inSynchronous,
      boolean captureMode, DecryptInfo cryptoSpec)
  {
    this.captureMode = captureMode;
    this.cryptoInfo = cryptoSpec;
    synchronous = inSynchronous;
    UIManager uiMgr = (uiMgrWeak != null) ? ((UIManager) uiMgrWeak.get()) : null;
    success = false;
    if (circular && !Sage.client)
      circSize = Sage.getLong("downloader/circular_file_size", 8*1024*1024);
    else
      circSize = 0;
    indexAtEndOfMP4 = fullDownloadRequired = httpRangeSupported= false;
    gotSMBAccess = gotMSAccess = false;
    downloadedBytes = lastDownloadOffset = 0;
    lastPlayerRequestTimestamp = lastSeekNotifyTimestamp = 0;
    lastNotifyReadOffset = 0;
    lastDownloadTimestamp = 0;
    isMP4Stream = false;
    statusMessage = "";
    if (Sage.DBG) System.out.println("Download requested for files server=" + serverName + " src=" + srcFile + " dest=" + destFile);
    if (Sage.client && serverName == null)
      serverName = Sage.preferredServer;
    remoteUIXfer = uiMgr != null && uiMgr.getUIClientType() == UIClient.REMOTE_UI && uiMgr.hasRemoteFSSupport();
    if (serverName != null && (serverName.startsWith("http:") || serverName.startsWith("https:") || serverName.startsWith("ftp:") || serverName.startsWith("file:")))
      remoteUIXfer = false;
    if (serverName == null && !remoteUIXfer)
      return Boolean.FALSE;

    myServerName = serverName;
    if (myServerName != null && myServerName.startsWith("smb://"))
    {
      srcFile = FSManager.getInstance().requestLocalSMBAccess(myServerName);
      if (srcFile == null)
        return Boolean.FALSE;
      gotSMBAccess = true;
    }
    if (myServerName!=null && myServerName.startsWith("file:"))
      mySrcFile = new java.io.File(URI.create(myServerName));
    else
      mySrcFile = (srcFile == null) ? null : new java.io.File(srcFile);

    myDestFile = destFile;
    if (!remoteUIXfer)
    {
      java.io.File destParent = myDestFile.getParentFile();
      if (destParent != null)
        IOUtils.safemkdirs(destParent);
    }

    if (myServerName != null && (myServerName.startsWith("http:") || myServerName.startsWith("https:") || myServerName.startsWith("ftp:")))
    {
      java.net.HttpURLConnection.setFollowRedirects(true);
      try
      {
        myURL = new java.net.URL(myServerName);
        // horrible hack because last.fm doesn't support HTTP range requests; and if you ask it'll invalidate
        // your single-use HTTP request
        httpRangeSupported = myServerName.indexOf("last.fm") == -1;
        while (true)
        {
          myURLConn = myURL.openConnection();
          if (httpRangeSupported)
            myURLConn.setRequestProperty("Range", "bytes=0-");
          if (Sage.getBoolean("use_sagetv_user_agent", false))
            myURLConn.setRequestProperty("User-Agent", "SageTV/" + sage.Version.MAJOR_VERSION + "." + sage.Version.MINOR_VERSION + "." + sage.Version.MICRO_VERSION);
          if (!"".equals(Sage.get("custom_user_agent", "")))
            myURLConn.setRequestProperty("User-Agent", Sage.get("custom_user_agent", ""));
          if (requestProps != null)
          {
            java.util.Iterator walker = requestProps.entrySet().iterator();
            while (walker.hasNext())
            {
              java.util.Map.Entry currEnt = (java.util.Map.Entry) walker.next();
              if (currEnt.getKey() != null && currEnt.getValue() != null)
                myURLConn.setRequestProperty(currEnt.getKey().toString(), currEnt.getValue().toString());
            }
          }
          try
          {
            myURLConn.setConnectTimeout(30000);
            myURLConn.setReadTimeout(30000);
          }catch (Throwable tr){} // in case its a bad JRE version w/ out these calls
          try
          {
            urlinStream = myURLConn.getInputStream();
          }
          catch (Exception e1)
          {
            // Check to see if HTTP Range requests are supported from this server
            if (myURLConn instanceof java.net.HttpURLConnection && httpRangeSupported)
            {
              java.net.HttpURLConnection httpConn = (java.net.HttpURLConnection) myURLConn;
              // 416 is the proper response for denying range requests; but some servers just send
              // a generic 400 response (like last.fm)
              if (httpConn.getResponseCode() / 100 == 4)
              {
                if (Sage.DBG) System.out.println("HTTP Range requests are NOT supported by this server. errcode=" + httpConn.getResponseCode());
                httpRangeSupported = false;
                continue;
              }
            }
            throw e1;
          }
          remoteSize = myURLConn.getContentLength();
          if (Sage.DBG) System.out.println("Download remoteSize=" + remoteSize);
          if (myURLConn instanceof java.net.HttpURLConnection)
          {
            java.net.HttpURLConnection httpConn = (java.net.HttpURLConnection) myURLConn;
            if (httpConn.getResponseCode() / 100 == 3)
            {
              if (Sage.DBG) System.out.println("Internally processing HTTP redirect...");
              urlinStream.close();
              myURL = new java.net.URL(httpConn.getHeaderField("Location"));
              continue;
            }
          }
          break;
        }
        fileOut = new BufferedSageFile(new LocalSageFile(myDestFile, false));
        if (captureMode)
          fileOut.seek(fileOut.length());
        else
          fileOut.setLength(0);
      }
      catch (Exception e)
      {
        System.out.println("ERROR with file download of:" + e);
        try{
          if (urlinStream != null)
            urlinStream.close();
        }catch (Exception e3){}
        try{
          if (fileOut != null)
            fileOut.close();
        }catch (Exception e4){}
        cleanup();
        return Boolean.FALSE;
      }
    }
    else if (remoteUIXfer)
    {
      if (captureMode)
      {
        // capture mode not supported for remote UI xfers
        return Boolean.FALSE;
      }
      if (!mySrcFile.isFile())
      {
        cleanup();
        return Boolean.FALSE;
      }

      MiniClientSageRenderer mcsr = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
      remoteSize = mySrcFile.length();
      MiniClientSageRenderer.RemoteFSXfer fsXferOp = mcsr.fsDownloadFile(mySrcFile, destFile.toString());
      if (fsXferOp.error != 0)
      {
        cleanup();
        return Boolean.FALSE;
      }
      fsXferOpWeak = new java.lang.ref.WeakReference(fsXferOp);
    }
    else if (myServerName!=null && mySrcFile!=null && mySrcFile.isFile() && myServerName.startsWith("file:"))
    {
      if (Sage.DBG) System.out.println("FileDownloader: Local File Copy: From: " + mySrcFile + " to " + destFile);
      // file url passed as the url to download
      if (!mySrcFile.exists())
      {
        cleanup();
        return Boolean.FALSE;
      }
    }
    else
    {
      try
      {
        sock = new java.net.Socket();
        sock.connect(new java.net.InetSocketAddress(Sage.preferredServer, 7818), 5000);
        sock.setSoTimeout(30000);
        //sock.setTcpNoDelay(true);
        outStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(sock.getOutputStream()));
        inStream = new java.io.DataInputStream(new java.io.BufferedInputStream(sock.getInputStream()));
        outStream.write("OPENW ".getBytes(Sage.BYTE_CHARSET));
        outStream.write(mySrcFile.toString().getBytes("UTF-16BE"));
        outStream.write("\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        String str = Sage.readLineBytes(inStream);
        if ("NON_MEDIA".equals(str))
        {
          // We may need to get special access to read this file; so try that first
          if (NetworkClient.getSN().requestMediaServerAccess(mySrcFile, true))
          {
            gotMSAccess = true;
            outStream.write("OPENW ".getBytes(Sage.BYTE_CHARSET));
            outStream.write(mySrcFile.toString().getBytes("UTF-16BE"));
            outStream.write("\r\n".getBytes(Sage.BYTE_CHARSET));
            outStream.flush();
            str = Sage.readLineBytes(inStream);
          }
        }
        if (!"OK".equals(str))
          throw new java.io.IOException("Error opening remote file of:" + str);
        // get the size
        outStream.write("SIZE\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        str = Sage.readLineBytes(inStream);
        remoteSize = Long.parseLong(str.substring(0, str.indexOf(' ')));
        fileOut = new BufferedSageFile(new LocalSageFile(myDestFile, false));
        if (captureMode)
          fileOut.seek(fileOut.length());
        else
          fileOut.setLength(0);
      }
      catch (Exception e)
      {
        System.out.println("ERROR with file download of:" + e);
        try{
          if (sock != null)
            sock.close();
        }catch (Exception e1){}
        try{
          if (outStream != null)
            outStream.close();
        }catch (Exception e2){}
        try{
          if (inStream != null)
            inStream.close();
        }catch (Exception e3){}
        try{
          if (fileOut != null)
            fileOut.close();
        }catch (Exception e4){}
        cleanup();
        return Boolean.FALSE;
      }
    }
    // Don't allow others to look at our files if this was done in appendMode because it won't give proper information
    if (!captureMode)
    {
      synchronized (fileMap)
      {
        fileMap.put(myDestFile, this);
      }
    }
    startTaskThread("FileDownload", synchronous);

    return (synchronous && !success) ? Boolean.FALSE : Boolean.TRUE;
  }

  public void taskRun()
  {
    try
    {
      if (myServerName != null && (myServerName.startsWith("http:") || myServerName.startsWith("https:") || myServerName.startsWith("ftp:")))
      {
        httpTaskRun();
      }
      else if (remoteUIXfer)
      {
        remoteUITaskRun();
      }
      else if (myServerName!=null && myServerName.startsWith("file:"))
      {
        fileTaskRun();
      }
      else
      {
        stvTaskRun();
      }
      if (Sage.DBG) System.out.println("Download completed for files server=" + myServerName + " src=" + mySrcFile + " dest=" + myDestFile + " abort=" + abort);
    }
    finally
    {
      cleanup();
      if (uiMgrWeak == null)
      {
        resultMap.put(myDestFile, wasSuccessful() ? (Object)Boolean.TRUE : (Object)("Error: " + getStatusMessage()));
      }
      synchronized (fileMap)
      {
        fileMap.remove(myDestFile);
      }
    }
  }

  private void fileTaskRun() {
    try {
      downloadedBytes=myDestFile.length();
      statusMessage="100%";

      if (mySrcFile.equals(myDestFile)) {
        succeeded();
        return;
      }

      IOUtils.copyFile(mySrcFile, myDestFile);
      succeeded();
    }
    catch (IOException e)
    {
      if (Sage.DBG) System.out.println("ERROR during file download/copy of:" + e);
      Sage.printStackTrace(e);
      statusMessage = "Error:" + e.toString();
    }
  }

  private void cleanup()
  {
    if (gotMSAccess)
    {
      NetworkClient.getSN().requestMediaServerAccess(mySrcFile, false);
      gotMSAccess = false;
    }
    if (gotSMBAccess)
    {
      FSManager.getInstance().releaseLocalSMBAccess(myServerName);
      gotSMBAccess = false;
    }

  }

  // NOTE: Not all servers support Range download requests!!!!
  private void restartHttpDownload(long startOffset) throws java.io.IOException
  {
    if (startOffset > 0 && !httpRangeSupported)
      throw new java.io.IOException("Cannot restart HTTP download because we already know this server doesn't support Range! offset=" + startOffset);
    // Close current connection first
    try{
      if (urlinStream != null)
        urlinStream.close();
    }catch (Exception e3){}
    myURLConn = myURL.openConnection();
    if (httpRangeSupported)
      myURLConn.setRequestProperty("Range", "bytes=" + startOffset + "-");
    try
    {
      myURLConn.setConnectTimeout(30000);
      myURLConn.setReadTimeout(30000);
    }catch (Throwable tr){} // in case its a bad JRE version w/ out these calls
    urlinStream = myURLConn.getInputStream();
    if (myURLConn instanceof java.net.HttpURLConnection)
    {
      java.net.HttpURLConnection httpConn = (java.net.HttpURLConnection) myURLConn;
      if (httpConn.getResponseCode() / 100 == 3)
      {
        if (Sage.DBG) System.out.println("Internally processing HTTP redirect to: " + httpConn.getHeaderField("Location") +
            " responseCode=" + httpConn.getResponseCode());
        urlinStream.close();
        myURL = new java.net.URL(httpConn.getHeaderField("Location"));
        if (abort) return;
        restartHttpDownload(startOffset);
        return;
      }
    }
    lastDownloadOffset = downloadedBytes = startOffset;
    //		lastSeekNotifyTimestamp = 0; // reset this indicator
    if (circSize > 0)
    {
      synchronized (notifyRead)
      {
        // Reposition this file
        if (fileOut.length() < (startOffset % circSize))
          fileOut.setLength(startOffset % circSize);
        fileOut.seek(startOffset % circSize);
        notifyRead.notify();
      }
    }
    if (Sage.DBG) System.out.println("HTTP Download has been restarted at offset=" + startOffset);
  }

  // This'll reconnect to the server if it needs to
  private int safeHttpRead(byte[] buffer) throws java.io.IOException
  {
    try
    {
      int rv = urlinStream.read(buffer);
      // For encrypted files, due to partial decryption we can't trust the download size check here
      if (rv <= 0 && remoteSize > 0 && downloadedBytes < remoteSize && httpRangeSupported && cryptoInfo == null)
      {
        if (Sage.DBG) System.out.println("Read " + rv + " bytes from the URL stream; but we're not done with the file yet...restart the download...");
        restartHttpDownload(downloadedBytes);
        return urlinStream.read(buffer);
      }
      return rv;
    }
    catch (java.io.IOException e)
    {
      if (httpRangeSupported && !abort)
      {
        if (Sage.DBG) System.out.println("HTTP read failed; trying to reconnect...");
        restartHttpDownload(downloadedBytes);
        return urlinStream.read(buffer);
      }
      else
        throw e;
    }
  }

  // NOTE: We should monitor the download and resume it if it hangs for some reason....that will definitely occur
  // on large file transfers at some point or another
  private void httpTaskRun()
  {
    downloadedBytes = 0;
    if (xferBuf == null)
      xferBuf = new byte[Sage.getInt("file_transfer_buffer_size", 65536)];
    java.text.NumberFormat prctForm = java.text.NumberFormat.getPercentInstance();
    if (abort)
    {
      // In case someone is viewing while we're downloading
      Sage.processInactiveFile(myDestFile.toString());
      return;
    }
    boolean isFlashStream = false;
    boolean foundID3Tag = false;
    int id3HeaderSize = 0;
    boolean isMP3Stream = false;
    boolean isSWFStream = false;
    boolean isMpeg2PSStream = false;
    boolean isMpeg2TSStream = false;
    bytesUntilNextSizeTimestamp = 4;
    numParsedSizeTimestampBytes = 0;
    byte[] decryptBuf = null;
    try
    {
      download_loop:
        do
        {
          if (abort)
          {
            // In case someone is viewing while we're downloading
            Sage.processInactiveFile(myDestFile.toString());
            return;
          }
          if (circSize > 0)
          {
            if (lastNotifyReadOffset < Math.max(lastDownloadOffset, downloadedBytes - circSize))
            {
              if (Sage.DBG) System.out.println("DOWNLOADER restarting download to move the stream BACKWARDS notifyReadOffset=" + lastNotifyReadOffset +
                  " lastSeekTimestamp=" + lastSeekNotifyTimestamp);
              // Try to figure out where we should really seek too if we can
              long reseekTarget = getSafeMP4SeekPositionForReadPosition(lastNotifyReadOffset, MP4_RESEEK_PREROLL_TIME);
              /*						if (isMP4Stream && lastSeekNotifyTimestamp > 0)
						{
							long[] dataRange = getMinMaxByteRangeForTimestamp(lastSeekNotifyTimestamp);
							if (dataRange[0] < reseekTarget)
							{
								if (Sage.DBG) System.out.println("Adjusting the backwards request to get all streams in it....was=" +
									reseekTarget + " now=" + dataRange[0]);
								reseekTarget = dataRange[0];
							}
						}*/
              if (reseekTarget < Math.max(lastDownloadOffset, downloadedBytes - circSize))
              {
                bytesUntilNextAtom += downloadedBytes - reseekTarget; // if we're doing the initial MP4 parsing this is needed
                restartHttpDownload(reseekTarget);
                lastAutoReseekTime = Sage.time();
              }
              else if (Sage.DBG) System.out.println("Skipping re-seek since it's target is within our download buffer " + reseekTarget);
            }
            // Check to see if this read request is for data that's after what's in the buffer
            else if (isMP4Stream && lastNotifyReadOffset > downloadedBytes + 256*1024)
              /*					else if (isMP4Stream && (lastNotifyReadOffset > downloadedBytes + circSize ||
						(lastSeekNotifyTimestamp != 0 && lastNotifyReadOffset > downloadedBytes + 128*1024)))*/
              //						((Sage.time() - lastAutoReseekTime) < 5000 ? (circSize*8/10) : 512*1024))
            {
              // Only skip MP4 streams ahead if it's more than the circular buffer (which is definitely a skip)
              // or if the timestamp seek hint has been set as well...otherwise this is probably just another read
              if (Sage.DBG) System.out.println("DOWNLOADER restarting download to move the stream FORWARDS notifyOffset=" + lastNotifyReadOffset +
                  " lastSeekTimestamp=" + lastSeekNotifyTimestamp);
              long reseekTarget = getSafeMP4SeekPositionForReadPosition(lastNotifyReadOffset, MP4_RESEEK_PREROLL_TIME);
              /*						if (lastSeekNotifyTimestamp > 0)
						{
							long[] dataRange = getMinMaxByteRangeForTimestamp(lastSeekNotifyTimestamp);
							if (dataRange[0] < reseekTarget)
							{
								if (Sage.DBG) System.out.println("Adjusting the forwards request to get all streams in it....was=" +
									reseekTarget + " now=" + dataRange[0]);
								reseekTarget = dataRange[0];
							}
						}*/
              if (reseekTarget > downloadedBytes)
              {
                bytesUntilNextAtom -= reseekTarget - downloadedBytes; // if we're doing the initial MP4 parsing this is needed
                restartHttpDownload(reseekTarget);
                lastAutoReseekTime = Sage.time();
              }
              else if (Sage.DBG) System.out.println("Skipping re-seek since it's target is within our download buffer " + reseekTarget);
            }
          }
          if (decryptBuf != null)
          {
            byte[] tempBuf = xferBuf;
            // Swap the buffers back
            xferBuf = decryptBuf;
            decryptBuf = tempBuf;
          }
          int numRead = safeHttpRead(xferBuf);
          if (numRead <= 0)
          {
            if (circSize > 0 && isMP4Stream && httpRangeSupported && indexAtEndOfMP4 && !abort)
            {
              if (Sage.DBG) System.out.println("Waiting at the end of the MP4 stream since we're probably just reading the header...");
              fileOut.flush();
            }
            while (circSize > 0 && isMP4Stream && httpRangeSupported && indexAtEndOfMP4 && !abort)
            {
              if (lastNotifyReadOffset < Math.max(lastDownloadOffset, downloadedBytes - circSize))
              {
                continue download_loop;
              }
              synchronized (notifyRead)
              {
                try
                {
                  notifyRead.wait(30);
                }
                catch (InterruptedException e2){}
              }
            }
            if (Sage.DBG) System.out.println("Read " + numRead + " bytes from the URL stream; download has completed");
            if (cryptoInfo != null)
            {
              numRead = decryptBuffer(xferBuf, 0, decryptBuf, true);
              if (numRead > 0)
              {
                if (circSize > 0)
                {
                  waitForCircWrite(numRead);
                  synchronized (notifyRead)
                  {
                    if (fileOut.position() + numRead < circSize)
                      fileOut.write(decryptBuf, 0, numRead);
                    else
                    {
                      int firstWrite = (int)(circSize - fileOut.position());
                      fileOut.write(decryptBuf, 0, firstWrite);
                      fileOut.seek(0);
                      fileOut.write(decryptBuf, firstWrite, numRead - firstWrite);
                    }
                  }
                }
                else
                  fileOut.write(decryptBuf, 0, numRead);
              }
            }
            break;
          }
          if (cryptoInfo != null)
          {
            if (decryptBuf == null)
              decryptBuf = new byte[xferBuf.length*3/2]; // make it 50% bigger to handle the final buffer which may be larger
            numRead = decryptBuffer(xferBuf, numRead, decryptBuf, false);
            byte[] tempBuf = xferBuf;
            // Swap the buffers now, then do it again before we download more data
            xferBuf = decryptBuf;
            decryptBuf = tempBuf;
          }
          if (circSize > 0)
          {
            waitForCircWrite(numRead);
            synchronized (notifyRead)
            {
              if (fileOut.position() + numRead < circSize)
                fileOut.write(xferBuf, 0, numRead);
              else
              {
                int firstWrite = (int)(circSize - fileOut.position());
                fileOut.write(xferBuf, 0, firstWrite);
                fileOut.seek(0);
                fileOut.write(xferBuf, firstWrite, numRead - firstWrite);
              }
            }
          }
          else
            fileOut.write(xferBuf, 0, numRead);
          if (downloadedBytes == 0 && numRead >= 10 && !isFlashStream && !isMP3Stream && !isMP4Stream && !isSWFStream && !isMpeg2PSStream && !isMpeg2TSStream)
          {
            // Check if it's a flash stream
            if (xferBuf[0] == 'F' && xferBuf[1] == 'L' && xferBuf[2] == 'V' && (((xferBuf[4] & 0x1) == 1) || ((xferBuf[4] & 0x4) == 4)))
            {
              if (Sage.DBG) System.out.println("Detected FLV stream downloading...");
              isFlashStream = true;
              processFlashPacket(xferBuf, 9, numRead - 9);
            }
            else if (xferBuf[0] == 'I' && xferBuf[1] == 'D' && xferBuf[2] == '3')
            {
              if (Sage.DBG) System.out.println("Detected ID3 tag in downloading...");
              foundID3Tag = true;
              id3HeaderSize = ((xferBuf[6]&0x7F)<<21) | ((xferBuf[7]&0x7F)<<14) |
                  ((xferBuf[8]&0x7F)<<7) | (xferBuf[9]&0x7F);
              id3HeaderSize += 10; // for the 10 initial bytes
              isMP3Stream = true; // assume ID3 is MP3 for now
            }
            else if (xferBuf[4] == 'f' && xferBuf[5] == 't' && xferBuf[6] == 'y' && xferBuf[7] == 'p')
            {
              // NOTE: This check could be much more solid....but this should do I'm pretty sure. :)
              if (Sage.DBG) System.out.println("Detected MP4 ISO stream downloading...");
              isMP4Stream = true;
              initMP4Parsing();
              processMP4Packet(xferBuf, 0, numRead);
            }
            else if (xferBuf[4] == 'm' && xferBuf[5] == 'd' && xferBuf[6] == 'a' && xferBuf[7] == 't')
            {
              // Handle quicktime files like MP4s for now....
              if (Sage.DBG) System.out.println("Detected Quicktime stream downloading...");
              isMP4Stream = true;
              initMP4Parsing();
              processMP4Packet(xferBuf, 0, numRead);
            }
            else if ((xferBuf[0] == 'C' || xferBuf[0] == 'F') && xferBuf[1] == 'W' && xferBuf[2] == 'S')
            {
              if (Sage.DBG) System.out.println("Detected SWF stream downloading...");
              isSWFStream = true;
            }
            else if (xferBuf[0] == 0 && xferBuf[1] == 0 && xferBuf[2] == 1 && (xferBuf[3] & 0xFF) == 0xBA)
            {
              if (Sage.DBG) System.out.println("Detected MPEG2-PS stream downloading...");
              isMpeg2PSStream = true;
            }
            else if (xferBuf[0] == 0x47 && (numRead < 189 || xferBuf[188] == 0x47) && (numRead < 377 || xferBuf[376] == 0x47))
            {
              if (Sage.DBG) System.out.println("Detected MPEG2-TS stream downloading...");
              isMpeg2TSStream = true;
            }
            else if (processMP3Packet(xferBuf, 0, numRead))
            {
              if (Sage.DBG) System.out.println("Detected MPEG audio stream downloading...");
              isMP3Stream = true;
            }
          }
          else if (isFlashStream)
            processFlashPacket(xferBuf, 0, numRead);
          else if (isMP4Stream)
            processMP4Packet(xferBuf, 0, numRead);
          else if (isMP3Stream && mp3Bitrate == 0)
          {
            processMP3Packet(xferBuf, 0, numRead);
          }
          else if (isMpeg2PSStream && !captureMode)
            processMpeg2PSPacket(xferBuf, 0, numRead);
          else if (isMpeg2TSStream && !captureMode)
            processMpeg2TSPacket(xferBuf, 0, numRead);
          if (downloadedBytes == 0)
          {
            // So we don't have a zero length file on disk which'll fail verification
            fileOut.flush();
          }
          downloadedBytes += numRead;
          if (CIRC_DOWNLOAD_DEBUG) System.out.println("DownloadedBytes=" + downloadedBytes);
          if (isMP3Stream && downloadedBytes > id3HeaderSize && mp3Bitrate > 0)
          {
            // Estimate the MP3 time that we've downloaded (milliseconds)
            lastDownloadTimestamp = (downloadedBytes - id3HeaderSize)*8000 / mp3Bitrate;
          }
          if (remoteSize > 0)
            statusMessage = prctForm.format(((double)downloadedBytes)/remoteSize);
          else
            statusMessage = (downloadedBytes/1024) + " KB";
        }while (true);
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR during file download of:" + e);
      Sage.printStackTrace(e);
      statusMessage = "Error:" + e.toString();
      return;
    }
    finally
    {
      try{
        if (urlinStream != null)
          urlinStream.close();
      }catch (Exception e3){}
      try{
        if (fileOut != null)
          fileOut.close();
      }catch (Exception e4){}
      cleanupMP4Parsing();
    }

    succeeded();
    // In case someone is viewing while we're downloading
    if (!captureMode)
      Sage.processInactiveFile(myDestFile.toString());
  }


  // We have the type+size+timestamp(1+3+3)
  private int bytesUntilNextSizeTimestamp = 4; // 4 byte offset into the first packet
  private int numParsedSizeTimestampBytes = 0;
  private byte[] sizeTimestampData = new byte[7];
  private void processFlashPacket(byte[] buf, int offset, int length)
  {
    while (length > 0)
    {
      if (bytesUntilNextSizeTimestamp > 0)
      {
        if (bytesUntilNextSizeTimestamp < length)
        {
          offset += bytesUntilNextSizeTimestamp;
          length -= bytesUntilNextSizeTimestamp;
          bytesUntilNextSizeTimestamp = 0;
          continue;
        }
        else
        {
          bytesUntilNextSizeTimestamp -= length;
          return;
        }
      }
      else
      {
        int neededBytes = 7 - numParsedSizeTimestampBytes;
        int availBytes = Math.min(neededBytes, length);
        System.arraycopy(buf, offset, sizeTimestampData, numParsedSizeTimestampBytes, availBytes);
        numParsedSizeTimestampBytes += availBytes;
        offset += availBytes;
        length -= availBytes;

        if (numParsedSizeTimestampBytes == 7)
        {
          // We have a complete type+size + timestamp
          int streamType = sizeTimestampData[0];
          int tagBodyLength = ((sizeTimestampData[1] & 0xFF) << 16) + ((sizeTimestampData[2] & 0xFF) << 8) + (sizeTimestampData[3] & 0xFF);
          int tagTimestamp = ((sizeTimestampData[4] & 0xFF) << 16) + ((sizeTimestampData[5] & 0xFF) << 8) + (sizeTimestampData[6] & 0xFF);
          if (streamType == 9) // video
          {
            lastDownloadTimestamp = tagTimestamp;
          }
          numParsedSizeTimestampBytes = 0;
          bytesUntilNextSizeTimestamp = tagBodyLength + 8;
        }
      }
    }
  }

  private void processMpeg2PSPacket(byte[] buf, int offset, int length)
  {
    // We just search for the PTS's in the 0xe0 video stream and use those for the latest timestamp
    for (int i = offset; i < offset + length - 27; i++)
    {
      if (buf[i] == 0 && buf[i + 1] == 0 && buf[i + 2] == 1 && (buf[i + 3] & 0xFF) == 0xBA)
      {
        // Pack header is here
        // Skip over the data in the pack header and land on the pack_stuffing_length byte (it uses the lower 3 bits)
        i += 13;
        // Check for any stuffing bytes in the pack header and skip them
        i += (1 + (buf[i] & 0x7));
        if (i + 13 >= offset + length) break;
        // Check for a start code next, it can be a system header or a pes packet
        while (buf[i] == 0 && buf[i + 1] == 0 && buf[i + 2] == 1 && (buf[i + 3] & 0xFF) != 0xBA)
        {
          i += 3;
          if ((buf[i] & 0xFF) == 0xBB)
          {
            // system header, skip it
            i += 3 + (((buf[i + 1] & 0xFF) << 8) | (buf[i + 2] & 0xFF));
            // Check for the start code next
            if (i + 15 >= offset + length) break;
            if (buf[i] != 0 || buf[i + 1] != 0 || buf[i + 2] != 1)
              continue;
            else
              i += 3;
          }
          if (i + 15 >= offset + length) break;
          int stream_id = buf[i] & 0xFF;
          int packet_length = ((buf[i + 1] & 0xFF) << 8) | (buf[i + 2] & 0xFF);
          //System.out.println("PES stream id=0x" + Integer.toString(stream_id, 16) + " length=" + packet_length);
          if (stream_id == 0xBD || (stream_id >= 0xC0 && stream_id <= 0xEF))
          {
            //System.out.println("Audio/video substream found");
            // Valid audio or video stream, check for the pts flag
            if ((buf[i + 4] & 0x80) == 0x80)
            {
              // PTS found, extract it
              long pts = (((long)(buf[i + 6] & 0x0E)) << 29) |
                  ((buf[i + 7] & 0xFF) << 22) |
                  ((buf[i + 8] & 0xFE) << 14) |
                  ((buf[i + 9] & 0xFF) << 7) |
                  ((buf[i + 10] & 0xFE) >> 1);
              lastDownloadTimestamp = pts / 90; // convert to milliseconds
            }
          }
        }
      }
    }
  }

  private void processMpeg2TSPacket(byte[] buf, int offset, int length)
  {
    for (int i = offset; i < offset + length - 188; i++)
    {
      // Align on a TS sync code
      if (buf[i] == 0x47)
      {
        // Find a payload start indicator, that's the only way there's PTS at the head
        if ((buf[i + 1] & 0x40) != 0)
        {
          int adaptation_field_control = (buf[i + 3] & 0x30) >> 4;
    if (adaptation_field_control == 2)
      i += (188 - 4);
    else if (adaptation_field_control == 3)
      i += Math.min(188 - 6, 1 + (buf[i + 4] & 0xFF));
    i += 4;
    if (adaptation_field_control == 1 || adaptation_field_control == 3)
    {
      // Check for a start code next, it can be a system header or a pes packet
      if (buf[i] == 0 && buf[i + 1] == 0 && buf[i + 2] == 1)
      {
        i += 3;
        if (i + 15 >= offset + length) break;
        int stream_id = buf[i] & 0xFF;
        //System.out.println("PES stream id=0x" + Integer.toString(stream_id, 16));
        if ((stream_id == 0xBD || (stream_id >= 0xC0 && stream_id <= 0xEF) || (stream_id == 0xFD)))
        {
          //System.out.println("Audio/video substream found");
          // Valid audio or video stream, check for the pts flag
          if ((buf[i + 4] & 0x80) == 0x80)
          {
            // PTS found, extract it
            long pts = (((long)(buf[i + 6] & 0x0E)) << 29) |
                ((buf[i + 7] & 0xFF) << 22) |
                ((buf[i + 8] & 0xFE) << 14) |
                ((buf[i + 9] & 0xFF) << 7) |
                ((buf[i + 10] & 0xFE) >> 1);
            lastDownloadTimestamp = pts / 90; // convert to milliseconds
          }
        }
      }
    }
        }
      }
    }
  }

  private long bytesUntilNextAtom = 0;
  private byte[] remAtomData = new byte[28];
  private int remAtomSize = 0;
  private int[] sttsDataA; // even are the counts, odd are the durations
  private int[] sttsDataV; // even are the counts, odd are the durations
  private int sttsIndex;
  private int[] stscDataA; // even are the first chunk #, odd are the samples per chunk
  private int[] stscDataV; // even are the first chunk #, odd are the samples per chunk
  private int stscIndex;
  private long[] chunkOffsetsA; // one for each chunk
  private long[] chunkOffsetsV; // one for each chunk
  private int stcoIndex;
  private boolean areOffsets64Bits;
  private int chunkQuality;
  private int timeScale;
  private long[] mp4TimeIndexA = null; // chunkOffsets has the positions, this is the same length and has the times
  private long[] mp4TimeIndexV = null; // chunkOffsets has the positions, this is the same length and has the times
  private boolean movieDataBegan;
  private long lastSeekNotifyTimestamp;
  private long lastPlayerRequestTimestamp;
  private static final boolean MP4_PARSER_DEBUG = false;
  private void initMP4Parsing()
  {
    bytesUntilNextAtom = 0;
    remAtomSize = 0;
    sttsIndex = stscIndex = stcoIndex = -1;
    chunkQuality = 0;
    movieDataBegan = false;
  }
  private void cleanupMP4Parsing()
  {
    chunkOffsetsA = chunkOffsetsV = null;
    sttsDataA = sttsDataV = stscDataA = stscDataV = null;
    mp4TimeIndexA = mp4TimeIndexV = null;
  }
  private long findTimestampForBytePos(long pos, long[] bytePos, long[] timestamps)
  {
    int idx = java.util.Arrays.binarySearch(bytePos, pos);
    if (idx < 0)
      idx = (-(idx + 1)) - 1; // find the insertion point then backup one
    if (idx < 0)
      idx = 0; // don't go before zero
    return timestamps[idx];
  }
  private long findBytePosForTimestamp(long timestamp, long[] bytePos, long[] timestamps)
  {
    int idx = java.util.Arrays.binarySearch(timestamps, timestamp);
    if (idx < 0)
      idx = (-(idx + 1)) - 1; // find the insertion point then backup one
    if (idx < 0)
      idx = 0; // don't go before zero
    return bytePos[idx];
  }
  private long[] getMinMaxByteRangeForTimestamp(long timestamp)
  {
    if (!isMP4Stream)
      return null;
    long min = Long.MAX_VALUE;
    long max = 0;
    if (chunkOffsetsA != null && mp4TimeIndexA != null)
    {
      long apos = findBytePosForTimestamp(timestamp, chunkOffsetsA, mp4TimeIndexA);
      min = Math.min(apos, min);
      max = Math.max(apos, max);
    }
    if (chunkOffsetsV != null && mp4TimeIndexV != null)
    {
      long vpos = findBytePosForTimestamp(timestamp, chunkOffsetsV, mp4TimeIndexV);
      min = Math.min(vpos, min);
      max = Math.max(vpos, max);
    }
    return new long[] { min, max };
  }
  private long getSafeMP4SeekPositionForReadPosition(long readPos, long timeOffset)
  {
    if (isMP4Stream && chunkOffsetsV != null && mp4TimeIndexV != null && mp4TimeIndexA != null &&
        chunkOffsetsA != null)
    {
      // Check to see if we think we know exactly what time they're looking for:
      int testidx = java.util.Arrays.binarySearch(chunkOffsetsA, readPos);
      long atime, vtime;
      if (testidx >= 0)
      {
        if (CIRC_DOWNLOAD_DEBUG) System.out.println("ESTIMATING-A seek time to be:" + mp4TimeIndexA[testidx]);
        atime = vtime = mp4TimeIndexA[testidx];
      }
      else if ((testidx = java.util.Arrays.binarySearch(chunkOffsetsV, readPos)) >= 0)
      {
        if (CIRC_DOWNLOAD_DEBUG) System.out.println("ESTIMATING-V seek time to be:" + mp4TimeIndexV[testidx]);
        atime = vtime = mp4TimeIndexV[testidx];
      }
      else
      {
        // Figure out what are the two potential timestamps for this read position.
        // Then take the minimum of those two values.
        // Then look up the positions for each stream for that timestamp.
        // Then use the minimum of those two positions for where we think the client is reading from currently.
        atime = findTimestampForBytePos(readPos, chunkOffsetsA, mp4TimeIndexA);
        vtime = findTimestampForBytePos(readPos, chunkOffsetsV, mp4TimeIndexV);
        long maxtime, mintime;
        if (atime > vtime)
        {
          maxtime = atime;
          mintime = vtime;
        }
        else
        {
          maxtime = vtime;
          mintime = atime;
        }
        if (CIRC_DOWNLOAD_DEBUG) System.out.println("ESTIMATING A-V seek times to be A=" + atime + " V=" + vtime);
      }
      long testTime = Math.min(atime, vtime);
      testTime = Math.max(0, testTime - timeOffset); // for keyframe alignment
      atime = findBytePosForTimestamp(testTime, chunkOffsetsA, mp4TimeIndexA);
      vtime = findBytePosForTimestamp(testTime, chunkOffsetsV, mp4TimeIndexV);
      return Math.min(readPos, Math.min(atime, vtime));
    }
    else
      return readPos;
  }

  private void processMP4Packet(byte[] buf, int offset, int length)
  {
    if (movieDataBegan)
    {
      long newMinTimestamp = Long.MAX_VALUE;
      if (!fullDownloadRequired)
      {
        if (chunkOffsetsA != null && mp4TimeIndexA != null)
        {
          newMinTimestamp = Math.min(findTimestampForBytePos(downloadedBytes, chunkOffsetsA, mp4TimeIndexA), newMinTimestamp);
        }
        if (chunkOffsetsV != null && mp4TimeIndexV != null)
        {
          newMinTimestamp = Math.min(findTimestampForBytePos(downloadedBytes, chunkOffsetsV, mp4TimeIndexV), newMinTimestamp);
        }
        if (newMinTimestamp != Long.MAX_VALUE)
          lastDownloadTimestamp = newMinTimestamp;
      }
      if (!MP4_PARSER_DEBUG && (!indexAtEndOfMP4 || (mp4TimeIndexA != null && mp4TimeIndexV != null)))
        return;
    }
    if (bytesUntilNextAtom > 0)
    {
      if (remAtomSize > 0)
      {
        int used = Math.min((int)bytesUntilNextAtom, remAtomSize);
        if (used != remAtomSize)
        {
          System.arraycopy(remAtomData, used, remAtomData, 0, remAtomSize - used);
        }
        remAtomSize -= used;
        bytesUntilNextAtom -= used;
      }
      if (bytesUntilNextAtom > 0)
      {
        long used = Math.min(bytesUntilNextAtom, length);
        length -= used;
        offset += used;
        bytesUntilNextAtom -= used;
        if (length == 0)
          return;
      }
    }

    while ((length + remAtomSize >= remAtomData.length && bytesUntilNextAtom >= 0) || (downloadedBytes + offset + length == remoteSize && length > 0))
    {
      System.arraycopy(buf, offset, remAtomData, remAtomSize, Math.min(remAtomData.length - remAtomSize, length));
      long currSize;
      if (stcoIndex >= 0)
      {
        long currVal;
        bytesUntilNextAtom = 0;
        long[] chunkOffsets = (chunkQuality == 2) ? chunkOffsetsV : chunkOffsetsA;
        if (areOffsets64Bits)
        {
          for (int x = 0; x + 7 < remAtomData.length; x+=8)
          {
            currVal = ((remAtomData[x] & 0xFF) << 56) | ((remAtomData[x+1] & 0xFF) << 48) | ((remAtomData[x+2] & 0xFF) << 40) | ((remAtomData[x+3] & 0xFF) << 32) |
                ((remAtomData[x+4] & 0xFF) << 24) | ((remAtomData[x+5] & 0xFF) << 16) | ((remAtomData[x+6] & 0xFF) << 8) | (remAtomData[x+7] & 0xFF);
            bytesUntilNextAtom += 8;
            chunkOffsets[stcoIndex++] = currVal;
            if (stcoIndex == chunkOffsets.length)
            {
              stcoIndex = -1;
              if (MP4_PARSER_DEBUG) System.out.println("Done reading chunk offset atom");
              break;
            }
          }
        }
        else
        {
          for (int x = 0; x + 3 < remAtomData.length; x+=4)
          {
            currVal = ((remAtomData[x] & 0xFF) << 24) | ((remAtomData[x+1] & 0xFF) << 16) | ((remAtomData[x+2] & 0xFF) << 8) | (remAtomData[x+3] & 0xFF);
            bytesUntilNextAtom += 4;
            chunkOffsets[stcoIndex++] = currVal;
            if (stcoIndex == chunkOffsets.length)
            {
              stcoIndex = -1;
              if (MP4_PARSER_DEBUG) System.out.println("Done reading chunk offset atom");
              break;
            }
          }
        }
        if (stcoIndex == -1)
        {
          // We're done parsing the sample information so now we can build the time-byte index
          if (Sage.DBG) System.out.println("Building MP4 byte-time index map to track downloading");
          long[] mp4TimeIndex = new long[chunkOffsets.length];
          if (chunkQuality == 2)
            mp4TimeIndexV = mp4TimeIndex;
          else
            mp4TimeIndexA = mp4TimeIndex;
          int chunkNum = 0;
          long sampleNum = 0;
          sttsIndex = 0;
          stscIndex = 0;
          long time = 0;
          long sampleDuration;
          int samplesLeftInChunk = 0;
          int[] stscData = (chunkQuality == 2) ? stscDataV : stscDataA;
          int[] sttsData = (chunkQuality == 2) ? sttsDataV : sttsDataA;
          while (true)
          {
            if (samplesLeftInChunk == 0)
            {
              mp4TimeIndex[chunkNum] = time;
              chunkNum++;
              if (chunkNum == chunkOffsets.length)
                break;
              if (stscIndex + 2 < stscData.length && chunkNum >= stscData[stscIndex + 2])
                stscIndex += 2;
              samplesLeftInChunk = stscData[stscIndex + 1];
            }
            if (sttsData[sttsIndex] == 0)
              sttsIndex+=2;
            sampleDuration = sttsData[sttsIndex + 1] * 1000 / timeScale;

            int samplesThisTime = Math.min(samplesLeftInChunk, sttsData[sttsIndex]);
            sttsData[sttsIndex] -= samplesThisTime;
            time += sampleDuration * samplesThisTime;
            samplesLeftInChunk -= samplesThisTime;
          }
          if (MP4_PARSER_DEBUG)
          {
            System.out.println("Done building MP4 time index map byte-time");
            for (int i = 0; i < chunkOffsets.length; i++)
            {
              System.out.println("Offset=" + chunkOffsets[i] + " \ttime=" + mp4TimeIndex[i]);;
            }
          }
          sttsIndex = -1;
          stscIndex = -1;
        }
      }
      else if (stscIndex >= 0)
      {
        bytesUntilNextAtom = 0;
        int[] stscData = (chunkQuality == 2) ? stscDataV : stscDataA;
        for (int x = 0; x + 7 < remAtomData.length; x+= 12)
        {
          int firstChunk = ((remAtomData[x] & 0xFF) << 24) | ((remAtomData[x+1] & 0xFF) << 16) | ((remAtomData[x+2] & 0xFF) << 8) | (remAtomData[x+3] & 0xFF);
          int sampPerChunk = ((remAtomData[x+4] & 0xFF) << 24) | ((remAtomData[x+5] & 0xFF) << 16) | ((remAtomData[x+6] & 0xFF) << 8) | (remAtomData[x+7] & 0xFF);
          bytesUntilNextAtom += 12;
          stscData[stscIndex++] = firstChunk;
          stscData[stscIndex++] = sampPerChunk;
          if (stscIndex == stscData.length)
          {
            stscIndex = -1;
            if (MP4_PARSER_DEBUG)
            {
              System.out.println("Done reading sample-to-chunk atom");
              for (int j = 0; j*2 < stscData.length; j++)
              {
                System.out.println("stsc chunk#" + stscData[2*j] + " \tsamplesPerChunk=" + stscData[2*j +1]);;
              }
            }
            break;
          }
        }
      }
      else if (sttsIndex >= 0)
      {
        bytesUntilNextAtom = 0;
        int[] sttsData = (chunkQuality == 2) ? sttsDataV : sttsDataA;
        for (int x = 0; x + 7 < remAtomData.length; x+= 8)
        {
          int sampleCount = ((remAtomData[x] & 0xFF) << 24) | ((remAtomData[x+1] & 0xFF) << 16) | ((remAtomData[x+2] & 0xFF) << 8) | (remAtomData[x+3] & 0xFF);
          int sampleDuration = ((remAtomData[x+4] & 0xFF) << 24) | ((remAtomData[x+5] & 0xFF) << 16) | ((remAtomData[x+6] & 0xFF) << 8) | (remAtomData[x+7] & 0xFF);
          bytesUntilNextAtom += 8;
          sttsData[sttsIndex++] = sampleCount;
          sttsData[sttsIndex++] = sampleDuration;
          if (sttsIndex == sttsData.length)
          {
            sttsIndex = -1;
            if (MP4_PARSER_DEBUG)
            {
              System.out.println("Done reading time-to-sample atom");
              for (int j = 0; j*2 < sttsData.length; j++)
              {
                System.out.println("stts sampleCount=" + sttsData[2*j] + " \tsampleDuration=" + sttsData[2*j +1]);;
              }
            }
            break;
          }
        }
      }
      else
      {
        currSize = ((remAtomData[0] & 0xFF) << 24) | ((remAtomData[1] & 0xFF) << 16) | ((remAtomData[2] & 0xFF) << 8) | (remAtomData[3] & 0xFF);
        if (currSize == 0)
          bytesUntilNextAtom = Long.MAX_VALUE; // goes to end of the file
        else if (currSize == 1)
        {
          // extended 64-bit size
          currSize = (((long)(remAtomData[8] & 0xFF)) << 56) | (((long)(remAtomData[9] & 0xFF)) << 48) | (((long)(remAtomData[10] & 0xFF)) << 40) |
              (((long)(remAtomData[11] & 0xFF)) << 32) | (((long)(remAtomData[12] & 0xFF)) << 24) | (((long)(remAtomData[13] & 0xFF)) << 16) |
              (((long)(remAtomData[14] & 0xFF)) << 8) | ((long)(remAtomData[15] & 0xFF));
          bytesUntilNextAtom = currSize;
        }
        else
          bytesUntilNextAtom = currSize;
        String type = ((char) remAtomData[4]) + "" + ((char) remAtomData[5]) + "" + ((char) remAtomData[6]) + "" + ((char) remAtomData[7]);
        if (MP4_PARSER_DEBUG) System.out.println("MP4 size="  + currSize + " type=" + type);
        if ("mdhd".equals(type))
        {
          timeScale = ((remAtomData[20] & 0xFF) << 24) | ((remAtomData[21] & 0xFF) << 16) | ((remAtomData[22] & 0xFF) << 8) | (remAtomData[23] & 0xFF);
          int duration = ((remAtomData[24] & 0xFF) << 24) | ((remAtomData[25] & 0xFF) << 16) | ((remAtomData[26] & 0xFF) << 8) | (remAtomData[27] & 0xFF);
          if (MP4_PARSER_DEBUG) System.out.println("timeScale=" + timeScale + " duration=" + duration + " REAL DURATION(seconds)=" + (duration/timeScale));
        }
        else if ("hdlr".equals(type))
        {
          // Check if it's audio or video
          String compType = ((char) remAtomData[12]) + "" + ((char) remAtomData[13]) + "" + ((char) remAtomData[14]) + "" + ((char) remAtomData[15]);
          String compSubtype = ((char) remAtomData[16]) + "" + ((char) remAtomData[17]) + "" + ((char) remAtomData[18]) + "" + ((char) remAtomData[19]);
          if (MP4_PARSER_DEBUG) System.out.println("MP4 Media compType=" + compType + " subtype=" + compSubtype);
          if (chunkQuality == -1)
          {
            if ("vide".equals(compSubtype))
              chunkQuality = 2;
            else if ("soun".equals(compSubtype))
              chunkQuality = 1;
          }
        }
        else if ("stsc".equals(type) && chunkQuality > 0)
        {
          int[] stscData = new int[2 * (((remAtomData[12] & 0xFF) << 24) | ((remAtomData[13] & 0xFF) << 16) |
              ((remAtomData[14] & 0xFF) << 8) | (remAtomData[15] & 0xFF))];
          if (chunkQuality == 2)
            stscDataV = stscData;
          else
            stscDataA = stscData;
          stscIndex = 0;
          bytesUntilNextAtom = 16;
          if (MP4_PARSER_DEBUG) System.out.println("Found sample-to-chunk atom");
        }
        else if ("stts".equals(type) && chunkQuality > 0)
        {
          int[] sttsData = new int[2 * (((remAtomData[12] & 0xFF) << 24) | ((remAtomData[13] & 0xFF) << 16) |
              ((remAtomData[14] & 0xFF) << 8) | (remAtomData[15] & 0xFF))];
          if (chunkQuality == 2)
            sttsDataV = sttsData;
          else
            sttsDataA = sttsData;
          sttsIndex = 0;
          bytesUntilNextAtom = 16;
          if (MP4_PARSER_DEBUG) System.out.println("Found time-to-sample atom");
        }
        else if ("stco".equals(type) && chunkQuality > 0)
        {
          // Parse this table so then we know what chunk # we are on for a given position in the stream (also handle 'co64')
          long[] chunkOffsets = new long[((remAtomData[12] & 0xFF) << 24) | ((remAtomData[13] & 0xFF) << 16) |
                                         ((remAtomData[14] & 0xFF) << 8) | (remAtomData[15] & 0xFF)];
          if (chunkQuality == 2)
            chunkOffsetsV = chunkOffsets;
          else
            chunkOffsetsA = chunkOffsets;
          stcoIndex = 0;
          bytesUntilNextAtom = 16;
          areOffsets64Bits = false;
          if (MP4_PARSER_DEBUG) System.out.println("Found chunk offset atom");
        }
        else if ("co64".equals(type) && chunkQuality > 0)
        {
          // Parse this table so then we know what chunk # we are on for a given position in the stream (also handle 'co64')
          long[] chunkOffsets = new long[((remAtomData[12] & 0xFF) << 24) | ((remAtomData[13] & 0xFF) << 16) |
                                         ((remAtomData[14] & 0xFF) << 8) | (remAtomData[15] & 0xFF)];
          if (chunkQuality == 2)
            chunkOffsetsV = chunkOffsets;
          else
            chunkOffsetsA = chunkOffsets;
          stcoIndex = 0;
          bytesUntilNextAtom = 16;
          areOffsets64Bits = true;
          if (MP4_PARSER_DEBUG) System.out.println("Found chunk offset (64-bit) atom");
        }
        else if ("mdat".equals(type))
        {
          if (chunkOffsetsV == null || mp4TimeIndexV == null)
          {
            if (Sage.DBG) System.out.println("MP4 file being downloaded is NOT STREAMBLE; the mdat section came before the sample tables");
            if (circSize > 0)
            {
              lastDownloadTimestamp = 5000; // to make it think we've got somewhere in the download
              indexAtEndOfMP4 = true;
              fullDownloadRequired = false;
            }
            else
              indexAtEndOfMP4 = fullDownloadRequired = true;
          }
          movieDataBegan = true;
        }
        if ("trak".equals(type))
          chunkQuality = -1; // signifies time to look for a new media type
        if ("moov".equals(type) || "trak".equals(type) || "mdia".equals(type) || "minf".equals(type) || "stbl".equals(type)) // it contains nested atoms
          bytesUntilNextAtom = 8;
      }
      if (bytesUntilNextAtom > 0)
      {
        if (remAtomSize > 0)
        {
          int used = Math.min((int)bytesUntilNextAtom, remAtomSize);
          if (used != remAtomSize)
          {
            System.arraycopy(remAtomData, used, remAtomData, 0, remAtomSize - used);
          }
          remAtomSize -= used;
          bytesUntilNextAtom -= used;
        }
        if (bytesUntilNextAtom > 0)
        {
          long used = Math.min(bytesUntilNextAtom, length);
          length -= used;
          offset += used;
          bytesUntilNextAtom -= used;
          if (length == 0)
            return;
        }
      }
    }
    if (length > 0)
    {
      System.arraycopy(buf, offset, remAtomData, remAtomSize, length);
      remAtomSize = length;
    }
  }

  private boolean processMP3Packet(byte[] buf, int offset, int length)
  {
    int index = 0;
    // scan through file to get syncword
    int headerstring = ((buf[index + offset] << 16) & 0x00FF0000)
        | ((buf[index+1+offset] << 8) & 0x0000FF00)
        | ((buf[index+2+offset] << 0) & 0x000000FF);
    boolean isSync = false;
    index += 2;
    do {
      headerstring <<= 8;
      index++;
      if (index+offset >= length) {
        // We didn't find the MP3 syncword so it probably isn't it
        return false;
      }

      headerstring |= (buf[index+offset] & 0x000000FF);
      isSync = ((headerstring & 0xFFE00000) == 0xFFE00000);
      // MPEG 2.5
      // filter out invalid sample rate
      if (isSync)
        isSync = (((headerstring >>> 10) & 3) != 3);
      // filter out invalid layer
      if (isSync)
        isSync = (((headerstring >>> 17) & 3) != 0);
      // filter out invalid version
      if (isSync)
        isSync = (((headerstring >>> 19) & 3) != 1);
    } while (!isSync);
    // Got a header... lets have a look at it...
    int h_version = ((headerstring >>> 19) & 1);
    if (((headerstring >>> 20) & 1) == 0) // SZD: MPEG2.5 detection
      if (h_version == MP3Utils.MPEG2_LSF)
        h_version = MP3Utils.MPEG25_LSF;
      else {
        if (Sage.DBG) System.out.println("invalid MPEG header (version)");
        return false;
      }
    int h_sample_frequency;
    if ((h_sample_frequency = ((headerstring >>> 10) & 3)) == 3) {
      if (Sage.DBG) System.out.println("invalid MPEG header (sample freq)");
      return false;
    }
    int h_layer = 4 - (headerstring >>> 17) & 3;
    int h_bitrate_index = (headerstring >>> 12) & 0xF;
    mp3Bitrate = MP3Utils.bitrates[h_version][h_layer - 1][h_bitrate_index];
    if (Sage.DBG) System.out.println("Detected mp3 bitrate of:" + mp3Bitrate);
    return true;
  }

  // NOTE: Not all MP4 content can be played back streaming. They may not have certain atom information
  // at the front of the file that is necessary to decode before playback. We judge this based off
  // the sample table. If we haven't hit it yet then we don't know if it's streamable. But we can tell if
  // we see a very large atom which is going to take the majority of the file that we know it will not be
  // streamable.

  private void stvTaskRun()
  {
    downloadedBytes = 0;
    if (xferBuf == null)
      xferBuf = new byte[Sage.getInt("file_transfer_buffer_size", 65536)];
    java.text.NumberFormat prctForm = java.text.NumberFormat.getPercentInstance();
    if (abort)
    {
      // In case someone is viewing while we're downloading
      Sage.processInactiveFile(myDestFile.toString());
      return;
    }
    long totalFileSize = remoteSize;
    try
    {
      long currOffset = 0;
      while (totalFileSize > 0)
      {
        if (abort)
        {
          // In case someone is viewing while we're downloading
          Sage.processInactiveFile(myDestFile.toString());
          return;
        }
        int currRead = (int)Math.min(xferBuf.length, totalFileSize);
        outStream.write(("READ " + currOffset + " " + currRead + "\r\n").getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        inStream.readFully(xferBuf, 0, currRead);
        if (circSize > 0)
        {
          waitForCircWrite(currRead);
          if (fileOut.position() + currRead < circSize)
            fileOut.write(xferBuf, 0, currRead);
          else
          {
            int firstWrite = (int)(circSize - fileOut.position());
            fileOut.write(xferBuf, 0, firstWrite);
            fileOut.seek(0);
            fileOut.write(xferBuf, firstWrite, currRead - firstWrite);
          }
        }
        else
          fileOut.write(xferBuf, 0, currRead);
        downloadedBytes += currRead;
        statusMessage = prctForm.format(((double)downloadedBytes)/remoteSize);
        totalFileSize -= currRead;
        currOffset += currRead;
      }
      outStream.write("QUIT\r\n".getBytes(Sage.BYTE_CHARSET));
      outStream.flush();
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR during file download of:" + e);
      Sage.printStackTrace(e);
      statusMessage = "Error:" + e.toString();
      return;
    }
    finally
    {
      try{
        if (sock != null)
          sock.close();
      }catch (Exception e1){}
      try{
        if (outStream != null)
          outStream.close();
      }catch (Exception e2){}
      try{
        if (inStream != null)
          inStream.close();
      }catch (Exception e3){}
      try{
        if (fileOut != null)
          fileOut.close();
      }catch (Exception e4){}
    }

    succeeded();
    // In case someone is viewing while we're downloading..not needed for synchronous transfers
    if (!synchronous)
      Sage.processInactiveFile(myDestFile.toString());
  }

  private void remoteUITaskRun()
  {
    downloadedBytes = 0;
    java.text.NumberFormat prctForm = java.text.NumberFormat.getPercentInstance();
    MiniClientSageRenderer.RemoteFSXfer fsXferOp = (MiniClientSageRenderer.RemoteFSXfer) fsXferOpWeak.get();
    while (!fsXferOp.isDone())
    {
      if (abort)
      {
        fsXferOp.abortNow();
        return;
      }
      try{Thread.sleep(250);}catch(Exception e){}
      downloadedBytes = fsXferOp.getBytesXferd();
      statusMessage = prctForm.format(((double)downloadedBytes)/remoteSize);
    }

    if (fsXferOp.error != 0)
    {
      statusMessage = "Error:" + fsXferOp.error;
      return;
    }
    succeeded();
  }

  public long getNumDownloadedBytes()
  {
    return downloadedBytes;
  }

  public long getLastDownloadTimestamp()
  {
    return lastDownloadTimestamp;
  }

  public long getMinSeekTimestamp()
  {
    if (circSize > 0)
    {
      if (isMP4Stream && httpRangeSupported & !isComplete())
        return 0; // We can seek anywhere in this case
      else
      {
        // Only let them seek backwards into what we still have in the circular buffer. But this is
        // tricky because we'll need to track timestamps for this. For now just guess based on avg. bitrate
        // which we can easily get. And put the seek point 10% of the way into the buffer.
        if (downloadedBytes < circSize)
          return 0;
        else
        {
          long minSeekPoint = Math.max(lastDownloadOffset, downloadedBytes - circSize) + circSize/10;
          if (minSeekPoint >= downloadedBytes)
            return lastDownloadTimestamp;
          else
          {
            return lastDownloadTimestamp * minSeekPoint / downloadedBytes;
          }
        }
      }
    }
    else
      return 0; // the whole thing's being downloaded
  }

  public long getMaxSeekTimestamp()
  {
    if (circSize > 0 && isMP4Stream && httpRangeSupported)
      return Long.MAX_VALUE/10; // avoid overflows when this is used in math outside of here
    else
      return lastDownloadTimestamp;
  }

  // Returns true if things interested in this downloaders timing progress need
  // to make calls to find out more. (i.e. this is always true for circular files; but its false once a normal download completes)
  public boolean needsTracking()
  {
    return circSize > 0 || !isComplete();
  }

  public boolean isProgressivePlay()
  {
    return !fullDownloadRequired;
  }

  public long getCircularDownloadSize()
  {
    return circSize;
  }

  // This call is used during progressive download playback w/ circular file storage.
  // It's called so the downloader knows where the reading is currently at so it doesn't get too
  // far ahead and wrap around and write over what hasn't been played yet
  public void notifyReadRequest(long offset, long length)
  {
    lastNotifyReadOffset = offset;
    lastNotifyReadLength = length;
    if (CIRC_DOWNLOAD_DEBUG) System.out.println("notifyReadRequest=" + offset + " downloadedRange=" + Math.max(lastDownloadOffset, downloadedBytes - circSize) + " - " +
        downloadedBytes);
    if (circSize > 0 && !isComplete())
    {
      // Check to see if this read request is for data that's before what's in the buffer
      if (offset < Math.max(lastDownloadOffset, downloadedBytes - circSize))
      {
        //System.out.println("WARNING : Attempting to read from offset before the buffer: offset=" + offset);
        waitingForRead = true;
        while (alive && !abort && offset < Math.max(lastDownloadOffset, downloadedBytes - circSize))
        {
          synchronized (notifyRead)
          {
            try
            {
              notifyRead.wait(30);
            }
            catch (InterruptedException e){ }
          }
        }
        //System.out.println("DONE waiting for backwards read request to be ready!!");
        waitingForRead = false;
      }
      // Check to see if this read request is for data that's after what's in the buffer
      long maxRead = (remoteSize > 0) ? Math.min(remoteSize, offset + length) : (offset + length);
      if (maxRead > downloadedBytes/* && isMP4Stream*/)
      {
        //System.out.println("WARNING: Trying to read data beyond what's been downloaded diff=" + ((maxRead) - downloadedBytes));
        waitingForRead = true;
        while (alive && !abort && maxRead > downloadedBytes)
        {
          synchronized (notifyRead)
          {
            try
            {
              notifyRead.wait(30);
            }
            catch (InterruptedException e){ }
          }
        }
        //System.out.println("DONE waiting for forwards read request to be ready!!");
        waitingForRead = false;
      }
      synchronized (notifyRead)
      {
        notifyRead.notify();
        // Check if we need to flush this to disk yet in case the read from the circular file would be on data that's still
        // in the write buffer for the RAF
        if (offset + length > downloadedBytes - 65536)
        {
          try
          {
            if (fileOut != null)
              fileOut.flush();
          }
          catch (Exception e){}
        }
      }
    }
  }

  // Pauses and waits until it is OK to write data of the specified size to the circular file.
  protected void waitForCircWrite(int writeSize)
  {
    if (circSize > 0 && (downloadedBytes + writeSize) >= circSize)
    {
      // We want the player to be in the middle of the circular buffer. So if they're at least 40% of the
      // way into the buffer for their last read, we do this write.
      long markerPoint = Math.max(0, downloadedBytes - circSize) + circSize * 4 / 10;
      long minClientReadPoint = getSafeMP4SeekPositionForReadPosition(lastNotifyReadOffset, 0);
      if (CIRC_DOWNLOAD_DEBUG && markerPoint > minClientReadPoint)
        System.out.println("WAITING for circ buffer write to be OK");
      while (alive && markerPoint > minClientReadPoint && !abort && !waitingForRead)
      {
        synchronized (notifyRead)
        {
          try
          {
            notifyRead.wait(50);
          }
          catch (InterruptedException e){ }
        }
      }
    }
  }

  public boolean isClientWaitingForRead()
  {
    return waitingForRead;
  }

  public void notifyForPlayerSeek(long seekTimestamp)
  {
    if (CIRC_DOWNLOAD_DEBUG) System.out.println("Downloader notified of seek to: " + seekTimestamp);
    lastSeekNotifyTimestamp = seekTimestamp;
  }


  public long getRemoteSize() { return remoteSize; }

  //	private static boolean addedBouncy = false;
  private int decryptBuffer(byte[] xferBuf, int numRead, byte[] decryptBuf, boolean finalStep) throws Exception
  {
    // Here's where we can go to native code instead for the operation for performance reasons
    if (decryptCipher == null)
    {
      decryptKey = new javax.crypto.spec.SecretKeySpec(cryptoInfo.key, cryptoInfo.algorithm.substring(0, cryptoInfo.algorithm.indexOf('/')));
      /*			if (!addedBouncy)
			{
				java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
				addedBouncy = true;
			}*/
      decryptCipher = javax.crypto.Cipher.getInstance(cryptoInfo.algorithm);
      if (cryptoInfo.iv != null)
      {
        javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(cryptoInfo.iv);
        decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, decryptKey, ivSpec);
      }
      else
        decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, decryptKey);
    }
    if (finalStep)
    {
      return decryptCipher.doFinal(xferBuf, 0, numRead, decryptBuf);
    }
    else
      return decryptCipher.update(xferBuf, 0, numRead, decryptBuf);
  }

  public static class DecryptInfo
  {
    public DecryptInfo(String algorithm, byte[] key, byte[] iv)
    {
      this.algorithm = algorithm;
      this.key = key;
      this.iv = iv;
    }
    public String algorithm;
    public byte[] key;
    public byte[] iv;
  }

  protected String myServerName;
  protected java.io.File mySrcFile;
  protected java.io.File myDestFile;
  protected java.net.Socket sock = null;
  protected java.io.DataOutputStream outStream = null;
  protected java.io.DataInputStream inStream = null;
  protected sage.io.SageFileSource fileOut = null;
  protected long remoteSize;
  protected boolean isMP4Stream;

  protected java.net.URL myURL;
  protected java.net.URLConnection myURLConn;
  protected java.io.InputStream urlinStream = null;

  protected long lastDownloadTimestamp;

  protected boolean remoteUIXfer;

  protected boolean gotMSAccess;
  protected boolean gotSMBAccess;

  protected boolean httpRangeSupported;

  protected long downloadedBytes;
  protected int mp3Bitrate;
  protected boolean fullDownloadRequired;
  protected boolean indexAtEndOfMP4;
  protected long circSize;
  protected long lastNotifyReadOffset;
  protected long lastNotifyReadLength;
  protected long lastDownloadOffset; // if we restart a download at a new offset, save it here
  protected Object notifyRead = new Object();
  protected boolean waitingForRead = false;
  protected long lastAutoReseekTime;


  // NOTE: Must use weak references here so we don't prevent GC of the UI stuff from
  // being a value in the WeakHashMap (who's value then has a strong reference to us, and the WeakHashMap values are strong refs as well)
  protected java.lang.ref.WeakReference uiMgrWeak;
  protected java.lang.ref.WeakReference fsXferOpWeak;

  protected boolean synchronous;
  protected byte[] xferBuf;
  protected boolean captureMode;

  protected DecryptInfo cryptoInfo;
  protected javax.crypto.Cipher decryptCipher;
  protected javax.crypto.spec.SecretKeySpec decryptKey;
}
