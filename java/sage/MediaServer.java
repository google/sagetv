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


import sage.media.format.MPEGParser2;

import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.StringTokenizer;

public class MediaServer implements Runnable
{
  private static final int DOS_BYTE_LIMIT = 1000000;
  private static final String MEDIA_SERVER_PORT = "media_server_port";
  private static final int TIMEOUT = 1000*60*30;
  private static final int SELECT_TIMEOUT = 30000;
  private static final boolean MEDIA_SERVER_DEBUG = Sage.DBG && Sage.getBoolean("media_server_debug", false);
  public static final String XCODE_QUALITIES_PROPERTY_ROOT = "media_server/transcode_quality/";
  private static final byte[] OK_BYTES = "OK\r\n".getBytes();
  private static final byte[] RN_BYTES = "\r\n".getBytes();
  public MediaServer()
  {
    alive = true;
    uploadOKd = new java.util.HashMap();

    // Create the default transcoder configuration property. Don't let this one be changed we want to control its defaults
    // NOTE: Change the audio bitrate down to 128 instead of 384. 384 can't be used with mono at lower sampling rates
    // so that would cause failure in transcoding certain files
    Sage.put(XCODE_QUALITIES_PROPERTY_ROOT + "DVD", "-f dvd -b 4000 -s " +
        (MMC.getInstance().isNTSCVideoFormat() ? "720x480" : "720x576") + " -acodec mp2 -r " +
        (MMC.getInstance().isNTSCVideoFormat() ? "29.97" : "25") + " -ab 128 -ar 48000 -ac 2");
    Sage.put(XCODE_QUALITIES_PROPERTY_ROOT + "DVD6Ch", "-f dvd -b 4000 -s " +
        (MMC.getInstance().isNTSCVideoFormat() ? "720x480" : "720x576") + " -acodec ac3 -r " +
        (MMC.getInstance().isNTSCVideoFormat() ? "29.97" : "25") + " -ab 384 -ar 48000 -ac 6");
    Sage.put(XCODE_QUALITIES_PROPERTY_ROOT + "DVDAudioOnly", "-f dvd -vcodec copy -acodec mp2 -ab 384 -ar 48000 -ac 2");
    Sage.put(XCODE_QUALITIES_PROPERTY_ROOT + "music", "-f dvd -vn -acodec mp2 -ab 64 -ar 48000 -ac 2");
    Sage.put(XCODE_QUALITIES_PROPERTY_ROOT + "music256", "-f dvd -vn -acodec mp2 -ab 256 -ar 48000 -ac 2");
    Sage.put(XCODE_QUALITIES_PROPERTY_ROOT + "music128", "-f dvd -vn -acodec mp2 -ab 128 -ar 48000 -ac 2");
    Sage.put(XCODE_QUALITIES_PROPERTY_ROOT + "mp3", "-f dvd -vn -acodec copy");
    Sage.put(XCODE_QUALITIES_PROPERTY_ROOT + "mpeg2psremux", "-f dvd -vcodec copy -acodec copy -copyts");
    Sage.put(XCODE_QUALITIES_PROPERTY_ROOT + "SVCD", "-f dvd -b 2000 -g 3 -bf 0 -acodec mp2 -ab 128 -ar 48000 -ac 2 -s " +
        (MMC.getInstance().isNTSCVideoFormat() ? "352x240" : "352x288") + " -r " + (MMC.getInstance().isNTSCVideoFormat() ? "29.97" : "25"));
    Sage.put(XCODE_QUALITIES_PROPERTY_ROOT + "SVCD6Ch", "-f dvd -b 2000 -g 3 -bf 0 -acodec ac3 -ab 384 -ar 48000 -ac 6 -s " +
        (MMC.getInstance().isNTSCVideoFormat() ? "352x240" : "352x288") + " -r " + (MMC.getInstance().isNTSCVideoFormat() ? "29.97" : "25"));
    extraFileSet = new java.util.HashSet();
    String extraFilesProp = Sage.get("media_server/extra_allowed_files", "miniclient");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(extraFilesProp, ";");
    while (toker.hasMoreTokens())
    {
      extraFileSet.add(new java.io.File(toker.nextToken()).getAbsolutePath());
    }

    clientRequestedFiles = new java.util.HashMap();

    readAheadFormats = new java.util.HashSet();
    String readAheadProp = Sage.get("media_server/readahead_optimized_file_extensions", "");
    toker = new java.util.StringTokenizer(readAheadProp, ";,");
    while (toker.hasMoreTokens())
    {
      String nextExt = toker.nextToken();
      if (nextExt.startsWith("."))
        nextExt = nextExt.substring(1);
      readAheadFormats.add(nextExt.toLowerCase());
    }
    useNioTransfers = Sage.getBoolean("use_nio_transfers", false);
  }

  public static void main(String[] args) { new MediaServer().run(); }

  private boolean alive;
  private java.nio.channels.ServerSocketChannel serverSocket;
  private volatile int numClients;
  private java.util.Map uploadOKd;
  private java.util.Set extraFileSet;
  private java.util.Map clientRequestedFiles;
  private java.util.Set readAheadFormats;
  private boolean useNioTransfers;
  public synchronized boolean areClientsConnected()
  {
    return numClients != 0;
  }
  protected synchronized void clientConnected()
  {
    numClients++;
    if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer got another connection, num=" + numClients);
  }
  protected synchronized void clientDisconnected()
  {
    numClients--;
    if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer closed a connection, num=" + numClients);
  }
  public void addToUploadList(java.io.File destFile, int uploadKey)
  {
    uploadOKd.put(destFile, new Integer(uploadKey));
  }
  public void removeFromUploadList(java.io.File destFile)
  {
    uploadOKd.remove(destFile);
  }
  // This is for enabling download of files that are not contained w/in the database
  public boolean addAuthorizedFile(java.io.File theFile)
  {
    if (!theFile.exists()) return false;
    if (Sage.DBG) System.out.println("MediaServer got request for access to file:" + theFile);
    synchronized (clientRequestedFiles)
    {
      Integer count = (Integer) clientRequestedFiles.get(theFile);
      if (count != null)
        clientRequestedFiles.put(theFile, new Integer(count.intValue() + 1));
      else
        clientRequestedFiles.put(theFile, new Integer(1));
    }
    return true;
  }
  public void removeAuthorizedFile(java.io.File theFile)
  {
    synchronized (clientRequestedFiles)
    {
      Integer count = (Integer) clientRequestedFiles.get(theFile);
      if (count != null)
      {
        if (count.intValue() > 1)
          clientRequestedFiles.put(theFile, new Integer(count.intValue() - 1));
        else
          clientRequestedFiles.remove(theFile);
      }
    }
  }
  public void kill()
  {
    alive = false;
    if (serverSocket != null)
    {
      try
      {
        serverSocket.close();
      }catch (Exception e){}
      serverSocket = null;
    }
  }
  public void run()
  {
    while (alive)
    {
      try
      {
        serverSocket = java.nio.channels.ServerSocketChannel.open();
        serverSocket.configureBlocking(true);
        serverSocket.socket().bind(new java.net.InetSocketAddress(Sage.getInt(MEDIA_SERVER_PORT, 7818)));
        while (alive)
        {
          java.nio.channels.SocketChannel s = serverSocket.accept();
          if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer recvd connection:" + s);
          /*				if (Sage.getBoolean("use_mpeg2_media_server", false))
					{
						Thread t = new Thread(new MPEG2Connection(s), "MPEG2MediaServerConnection");
						t.setDaemon(true);
						t.start();
					}
					else*/
          {
            Pooler.execute(new Connection(s), "MediaServerConnection");
          }
        }
      }
      catch (Exception e)
      {
        if (alive)
        {
          System.out.println("Error (will retry) in MediaServer:" + e);
          e.printStackTrace();
          try{Thread.sleep(15000);}catch(Exception e1){}
        }
      }
    }
  }

  public static String convertToUnicode(String s)
  {
    StringBuffer sb = new StringBuffer(s.length()/2);
    for (int i = 0; i+1 < s.length(); i+=2)
    {
      sb.append((char)(((s.charAt(i) & 0xFF) << 8) | (s.charAt(i + 1) & 0xFF)));
    }
    return sb.toString();
  }

  private static long getLargeFileSize(String filename)
  {
    Long rv = (Long) largeFileSizeMap.get(filename);
    if (rv == null)
    {
      synchronized (largeFileSizeMap)
      {
        largeFileSizeMap.put(filename, rv = new Long(LARGE_FILE_SIZE++));
      }
    }
    return rv.longValue();
  }

  class Connection implements Runnable
  {
    public Connection(java.nio.channels.SocketChannel ins)
    {
      s = ins;

    }

    private StringBuffer result = new StringBuffer();
    public StringBuffer readLineBytes()
        throws java.io.InterruptedIOException, java.io.IOException {

      result.setLength(0);
      boolean needsFlip = true;
      if (commBufRead.hasRemaining() && commBufRead.position() > 0)
        needsFlip = false;
      else
        commBufRead.clear();
      int readRes = 0;
      int numAddedKeys;
      long selectStart = Sage.eventTime();
      if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer readLineBytes() called");
      if (blocking)
        TimeoutHandler.registerTimeout(TIMEOUT, s);
      try
      {
        while ((!commBufRead.hasRemaining() || commBufRead.position() == 0) && (readRes = s.read(commBufRead)) <= 0)
        {
          if (readRes == -1 || blocking) throw new java.io.EOFException();
          if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer about to call select");
          if ((numAddedKeys = readSelector.select(SELECT_TIMEOUT)) == 0)
          {
            if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer select call returned with no updates");
            if (Sage.eventTime() - selectStart > TIMEOUT)
              throw new java.io.EOFException();
            continue;
          }
          java.util.Iterator walker = readSelector.selectedKeys().iterator();
          while (walker.hasNext())
          {
            walker.next();
            walker.remove();
          }
        }
      }
      finally
      {
        if (blocking)
          TimeoutHandler.clearTimeout(s);
      }
      if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer read " + readRes + " bytes");
      if (needsFlip)
        commBufRead.flip();
      int currByte = (commBufRead.get() & 0xFF);
      int readTillPanic = DOS_BYTE_LIMIT;
      while (true)
      {
        if (currByte == '\r')
        {
          if (!commBufRead.hasRemaining())
          {
            commBufRead.clear();
            selectStart = Sage.eventTime();
            if (blocking)
              TimeoutHandler.registerTimeout(TIMEOUT, s);
            try
            {
              while ((readRes = s.read(commBufRead)) <= 0)
              {
                if (readRes == -1 || blocking) throw new java.io.EOFException();
                if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer about to call select");
                if ((numAddedKeys = readSelector.select(SELECT_TIMEOUT)) == 0)
                {
                  if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer select call returned with no updates");
                  if (Sage.eventTime() - selectStart > TIMEOUT)
                    throw new java.io.EOFException();
                  continue;
                }
                java.util.Iterator walker = readSelector.selectedKeys().iterator();
                while (walker.hasNext())
                {
                  walker.next();
                  walker.remove();
                }
              }
            }
            finally
            {
              if (blocking)
                TimeoutHandler.clearTimeout(s);
            }
            if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer read " + readRes + " bytes");
            commBufRead.flip();
          }
          currByte = (commBufRead.get() & 0xFF);
          if (currByte == '\n')
          {
            return result;
          }
          result.append('\r');
        }
        else if (currByte == '\n')
        {
          return result;
        }
        result.append((char)currByte);
        if (!commBufRead.hasRemaining())
        {
          commBufRead.clear();
          selectStart = Sage.eventTime();
          if (blocking)
            TimeoutHandler.registerTimeout(TIMEOUT, s);
          try
          {
            while ((readRes = s.read(commBufRead)) <= 0)
            {
              if (readRes == -1 || blocking) throw new java.io.EOFException();
              if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer about to call select");
              if ((numAddedKeys = readSelector.select(SELECT_TIMEOUT)) == 0)
              {
                if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer select call returned with no updates");
                if (Sage.eventTime() - selectStart > TIMEOUT)
                  throw new java.io.EOFException();
                continue;
              }
              java.util.Iterator walker = readSelector.selectedKeys().iterator();
              while (walker.hasNext())
              {
                walker.next();
                walker.remove();
              }
            }
          }
          finally
          {
            if (blocking)
              TimeoutHandler.clearTimeout(s);
          }
          if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer read " + readRes + " bytes");
          commBufRead.flip();
        }
        currByte = (commBufRead.get() & 0xFF);
        //			if (readTillPanic-- < 0)
        //			{
        //				throw new java.io.IOException("TOO MANY BYTES RECIEVED FOR A LINE, BAILING TO PREVENT DOS ATTACK");
        //			}
      }
    }

    /**
     * Close the currently open file.
     * <p/>
     * This will also close the remuxer if it is in use.
     */
    protected void closeFile()
    {
      closeFile(true);
    }

    /**
     * Close the currently open file.
     *
     * @param closeRemux Close the remuxer too if it is in use.
       */
    protected void closeFile(boolean closeRemux)
    {
      if (closeRemux) {
        try {
          if (remuxer != null) {
            remuxer.close();
            remuxer = null;
          }
        } catch (Exception e) {
        }
      }
      try
      {
        if (fileChannel != null)
        {
          fileChannel.close();
          fileChannel = null;
        }
      }
      catch (Exception e){}
      try
      {
        if (fileStream != null)
        {
          fileStream.close();
          fileStream = null;
        }
      }
      catch (Exception e){}
      /*try
      {
        if (uploadStream != null)
        {
          uploadStream.close();
          uploadStream = null;
        }
      }
      catch (Exception e){}*/
      currFile = null;
    }

    // Tests if we're allowed to stream currFile and sets the currMF field
    protected boolean checkFileAccess() throws java.io.IOException
    {
      // CHECK THIS EVERY TIME, THERE'S BEEN A FEW BUGS CAUSED BY CACHING STUFF HERE
      currMF = null;

      // Check if we've got a downloader
      downer = FileDownloader.getFileDownloader(currFile);
      MediaFile[] mfs = Wizard.getInstance().getFiles();
      String fullPath = currFile.getAbsolutePath();
      for (int i = mfs.length - 1; i >= 0 && currMF == null; i--)
      {
        if (mfs[i].thumbnailFile != null && mfs[i].thumbnailFile.equals(currFile))
        {
          currMF = mfs[i];
          break;
        }
        if (mfs[i].hasFile(currFile))
        {
          currMF = mfs[i];
          break;
        }
        if ((mfs[i].isBluRay() || mfs[i].isDVD()) && mfs[i].getFile(0) != null && fullPath.startsWith(mfs[i].getFile(0).getAbsolutePath()))
        {
          currMF = mfs[i];
          break;
        }
      }

      circFileRec = (downer != null && downer.getCircularDownloadSize() > 0) || MMC.getInstance().getRecordingCircularFileSize(currFile) > 0;

      if (currMF != null)
      {
        if (currFile.isFile())
        {
          return true;
        }
        else
        {
          currFile = null;
          commBufWrite.clear();
          commBufWrite.put("NO_EXIST\r\n".getBytes()).flip();
          int numWritten = s.write(commBufWrite);
          if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
        }
      }
      else
      {
        // Check if we're a local file playback so we may not be in the DB
        if ((extraFileSet.contains(currFile.getAbsolutePath()) || VideoFrame.isAnyPlayerUsingFile(currFile) ||
            clientRequestedFiles.containsKey(currFile)) && currFile.isFile())
          return true;
        currFile = null;
        commBufWrite.clear();
        commBufWrite.put("NON_MEDIA\r\n".getBytes()).flip();
        int numWritten = s.write(commBufWrite);
        if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
      }
      return false;
    }

    protected boolean checkDirAccess(java.io.File dirPath)
    {
      if (clientRequestedFiles.containsKey(dirPath) && dirPath.isDirectory()) return true;
      String dirStr = dirPath.getAbsolutePath();
      // Check if we've got a downloader
      MediaFile[] mfs = Wizard.getInstance().getFiles();
      for (int i = mfs.length - 1; i >= 0 && currMF == null; i--)
      {
        if ((mfs[i].isDVD() || mfs[i].isBluRay()) && dirStr.startsWith(mfs[i].getFile(0).getAbsolutePath()))
        {
          return true;
        }
      }
      return false;
    }

    protected void openFile(String filename) throws java.io.IOException
    {
      currFile = new java.io.File(filename);
      if (!currFile.exists())
      {
        // Check for UTF pathname conversion issues
        currFile = new java.io.File(new String(filename.getBytes(Sage.BYTE_CHARSET), Sage.I18N_CHARSET));
      }
      lastRecFileSize = 0;
      if (checkFileAccess())
      {
        if (xcoder != null)
        {
          if ((currMF != null && currMF.isRecording()) || (downer != null && !downer.isComplete()))
            xcoder.setActiveFile(true);
          xcoder.setSourceFile(null, currFile);
          xcoder.startTranscode();
        }
        else
        {
          fileStream = new java.io.FileInputStream(currFile);
          fileChannel = fileStream.getChannel();
          int idx = filename.lastIndexOf('.');
          readAhead = idx != -1 && readAheadFormats.contains(filename.substring(idx + 1).toLowerCase());
        }
        commBufWrite.clear();
        commBufWrite.put(OK_BYTES).flip();
        int numWritten = s.write(commBufWrite);
        if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
      }
    }

    protected void openWriteFile(String filename, int uploadKey, boolean reply) throws java.io.IOException
    {
      currFile = new java.io.File(filename);
      currFile = currFile.getCanonicalFile(); // any relative path hacking is removed here
      if (MEDIA_SERVER_DEBUG) System.out.println("CanonPath=" + currFile);
      String currFileString = currFile.toString();
      if (Sage.WINDOWS_OS)
        currFileString = currFileString.toLowerCase();
      java.util.Iterator walker = uploadOKd.entrySet().iterator();
      boolean uploadOK = false;
      // These may just be directories, it's not always files
      while (walker.hasNext())
      {
        java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
        java.io.File currPath = (java.io.File) ent.getKey();
        if (uploadKey != ((Integer) ent.getValue()).intValue())
          continue;
        if (Sage.WINDOWS_OS)
        {
          if (currFileString.startsWith(currPath.toString().toLowerCase()))
          {
            uploadOK = true;
            break;
          }
        }
        else if (currFileString.startsWith(currPath.toString()))
        {
          uploadOK = true;
          break;
        }
      }
      if (uploadOK)
      {
        //uploadStream = new java.io.FileOutputStream(currFile);
        fileChannel = FileChannel.open(currFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        if (reply)
        {
          commBufWrite.clear();
          commBufWrite.put("OK\r\n".getBytes()).flip();
          int numWritten = s.write(commBufWrite);
          if (MEDIA_SERVER_DEBUG)
            System.out.println("MediaServer wrote out " + numWritten + " bytes");
        }
      }
      else
      {
        currFile = null;
        commBufWrite.clear();
        commBufWrite.put("NON_MEDIA\r\n".getBytes()).flip();
        int numWritten = s.write(commBufWrite);
        if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
      }
    }

    private byte[] sizeBuf = null;
    protected void sizeFile() throws java.io.IOException
    {
      long availSize = -1;
      long totalSize = -1;
      if (currFile != null)
      {
        if (xcoder != null)
        {
          availSize = xcoder.getVirtualTranscodeSize();
          totalSize = xcoder.isTranscodeDone() ? availSize : getLargeFileSize(currFile.toString());
        }
        if (remuxer != null)
        {
          availSize = remuxer.getFileSize();
          totalSize = getLargeFileSize(currFile.toString());
        }
        else if (currMF != null)
        {
          /*
           * IMPORTANT: Use the MF object for determing if a file is done because
           * that's what the VF is also doing.
           */
          if (currMF.isRecording(currFile))
          {
            // On embedded we want to just check the file size straight up to avoid the network transfer overhead of communicating
            // with the network encoder
            availSize = lastRecFileSize = MMC.getInstance().getRecordedBytes(currFile);
            totalSize = getLargeFileSize(currFile.toString());
          }
          else
            availSize = totalSize = fileChannel.size();
        }
        else if (downer != null && (!downer.isComplete() || downer.getCircularDownloadSize() > 0))
        {
          availSize = downer.getNumDownloadedBytes();
          totalSize = downer.isComplete() ? availSize : getLargeFileSize(currFile.toString());
        }
        else
          availSize = totalSize = fileChannel.size();
      }
      commBufWrite.clear();
      if (sizeBuf == null)
        sizeBuf = new byte[64]; // 41 is the longest it'll ever be
      int bufOffset = IOUtils.printLongInByteArray(availSize, sizeBuf, 0);
      sizeBuf[bufOffset++] = ' ';
      bufOffset += IOUtils.printLongInByteArray(totalSize, sizeBuf, bufOffset);
      sizeBuf[bufOffset++] = '\r';
      sizeBuf[bufOffset++] = '\n';
      commBufWrite.put(sizeBuf, 0, bufOffset);
      commBufWrite.flip();
      //			commBufWrite.put((Long.toString(availSize) + " " + Long.toString(totalSize) + "\r\n").getBytes()).flip();
      if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer about to send size response avail=" + availSize + " total=" + totalSize);
      int numWritten = s.write(commBufWrite);
      if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer size response sent " + numWritten + " bytes");
    }

    // If data is requested beyond the end of the file, just send junk.
    protected void readFile(long offset, long length) throws java.io.IOException
    {
      // Ensure the arguments will never crash or hang us
      if (length < 0)
      {
        length *= -1;
        offset -= length;
      }
      if (offset < 0)
        offset = 0;
      if (xcoder != null)
      {
        if ((currMF != null && !currMF.isRecording()) || (currMF == null && (downer == null || downer.isComplete())))
          xcoder.setActiveFile(false);
        xcoder.sendTranscodeOutputToChannel(offset, length, s);
        return;
      }

      boolean dontWaitForData = false;
      if (downer != null && downer.getCircularDownloadSize() > 0)
      {
        // Notify the downloader of where the last read request was so it knows where its safe to wrap the circular file. It also
        // can be used to pause this media server callback in case the downloader needs to move to a different position if we
        // went outside the buffer. This has a side effect of allowing seeking forward beyond the current position and MAY even
        // enable us to playback files with the header at the end because it'll force the downloader to skip to a new location.
        downer.notifyReadRequest(offset, length);
        dontWaitForData = true;
      }
      long leftToRead = length;
      boolean recordingCurrFile = (downer == null && MMC.getInstance().isRecording(currFile)) || (downer != null &&
          (!downer.isComplete() || downer.getCircularDownloadSize() > 0));
      // NOTE: This used to not check the size through the MMC, but it's required to do that
      // because a circular file will have a bigger size than what's already recorded in it.
      long overage;
      if (!recordingCurrFile)
      {
        // On embedded we want to just check the file size straight up to avoid the network transfer overhead of communicating
        // with the network encoder
        overage = offset + length - fileChannel.size();
      }
      else if (downer != null)
      {
        overage = offset + length - downer.getNumDownloadedBytes();
      }
      else
      {
        // prevent unneeded requests for recording file size
        if (offset + length <= lastRecFileSize)
          overage = 0;
        else
        {
          lastRecFileSize = MMC.getInstance().getRecordedBytes(currFile);
          overage = offset + length - lastRecFileSize;
        }
      }
      int numTries = downer != null ? 150 : 50;
      if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer waiting for more data to appear in file over=" + overage + " rec=" + recordingCurrFile);
      while (!dontWaitForData && overage > 0 && recordingCurrFile && (numTries-- > 0))
      {
        try { Thread.sleep(100); } catch (Exception e){}
        //Thread.yield();
        long recByteLen = (downer == null ? MMC.getInstance().getRecordedBytes(currFile) : downer.getNumDownloadedBytes());
        recordingCurrFile = (downer == null && MMC.getInstance().isRecording(currFile)) || (downer != null && !downer.isComplete());
        if (recByteLen < 0 || !recordingCurrFile)
          recByteLen = fileChannel.size();
        overage = offset + length - recByteLen;
      }
      if (overage > 0)
      {
        if (overage > leftToRead)
        {
          leftToRead = 0;
          overage = length;
        }
        else
        {
          leftToRead -= overage;
        }
      }
      long circSize = (downer != null) ? downer.getCircularDownloadSize() : MMC.getInstance().getRecordingCircularFileSize(currFile);
      long newOverage = 0;
      if (circSize > 0 && ((offset % circSize) + leftToRead > circSize))
      {
        offset %= circSize;
        long size1 = circSize - offset;
        long size2 = leftToRead - size1;
        if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer transferData(" + offset + ", " + size1 + ") currRec=" + recordingCurrFile);
        newOverage += transferData(offset, size1);
        if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer transferData(" + 0 + ", " + size2 + ") currRec=" + recordingCurrFile);
        newOverage += transferData(0, size2);
        if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer transferData complete overage=" + overage);
      }
      else
      {
        if (circSize > 0)
          offset %= circSize;
        if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer transferData(" + offset + ", " + leftToRead + ") currRec=" + recordingCurrFile);
        newOverage += transferData(offset, leftToRead);
        if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer transferData complete overage=" + overage);
      }
      overage = newOverage + (overage > 0 ? overage : 0);
      while (overage > 0)
      {
        if (overageBuf == null)
        {
          overageBuf = java.nio.ByteBuffer.allocateDirect(8192);
          byte[] overageFF = new byte[256];
          java.util.Arrays.fill(overageFF, 0, overageFF.length, (byte)0xFF);
          for (int i = 0; i < 8192; i += 256)
            overageBuf.put(overageFF);
        }
        overageBuf.clear();
        overageBuf.limit((int)Math.min(overage, overageBuf.capacity()));
        if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer sending overage=" + overageBuf.limit());
        int numWritten = s.write(overageBuf); // just write out FF's
        overage -= numWritten;
        if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer overage sent capacity=" + overageBuf.capacity() + " overage=" + overage + " numWritten=" + numWritten);
      }
    }

    private long readAheadPos;
    private java.nio.ByteBuffer readAheadData;
    private java.nio.ByteBuffer readAheadData2;
    protected long transferData(long offset, long length) throws java.io.IOException
    {
      // Catches the EOF and pauses for a bit, because we should never
      // actually read past the end of the file in normal operation. This
      // only occurs when there's discrepancies in the disk buffering
      int numTries = 0;
      long orgLength = length;
      if (readAhead && readAheadData != null && readAheadData.remaining() > 0)
      {
        if (offset == readAheadPos)
        {
          int buffSize = readAheadData.remaining();
          int currSize = (int)Math.min(length, buffSize);
          readAheadData.limit(currSize + readAheadData.position());
          s.write(readAheadData);
          readAheadData.limit(readAheadData.limit() + buffSize - currSize);
          length -= currSize;
          offset += currSize;
          readAheadPos += currSize;
          if (length > 0 && readAheadData2.remaining() > 0)
          {
            buffSize = readAheadData2.remaining();
            currSize = (int)Math.min(length, buffSize);
            readAheadData2.limit(currSize + readAheadData2.position());
            s.write(readAheadData2);
            readAheadData2.limit(readAheadData2.limit() + buffSize - currSize);
            length -= currSize;
            offset += currSize;
            readAheadPos += currSize;
          }
        }
        else
        {
          // Trash the readahead data
          readAheadData.limit(readAheadData.position());
          readAheadData2.limit(readAheadData2.position());
        }
      }
      while (++numTries < 100 && length > 0)
      {
        long currFileSize = fileChannel.size();
        long readThisTime = Math.min(currFileSize - offset, length);
        if (readThisTime > 0)
        {
          if (!useNioTransfers || (Sage.LINUX_OS && offset +length >= Integer.MAX_VALUE))
          {
            // NOTE: Due to Java using the sendfile kernel API call to do the transfer,
            // there's a 32-bit limitation here. This is very unfortunate. But since it's
            // compiled into the JVM and into the Linux kernel there's really no other way around this
            // We also don't use them by default on Windows because of poor performance w/ network shares
            if (hackBuf == null)
            {
              hackBuf = java.nio.ByteBuffer.allocateDirect(65536);
            }
            hackBuf.clear();
            hackBuf.limit((int)Math.min(readThisTime, hackBuf.capacity()));
            int currRead = fileChannel.read(hackBuf, offset);
            hackBuf.flip();
            s.write(hackBuf);
            offset += currRead;
            length -= currRead;
          }
          else
          {
            long currRead = fileChannel.transferTo(offset, readThisTime, s);
            offset += currRead;
            length -= currRead;
          }
        }
        else
        {
          if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer waiting for data in file size=" + currFileSize);
          try{Thread.sleep(50);}catch(Exception e1){}
        }
      }
      if (readAhead && (readAheadData == null || readAheadData.remaining() == 0))
      {
        if (readAheadData == null)
        {
          readAheadData = java.nio.ByteBuffer.allocateDirect(Math.max(65536, (int)orgLength));
          readAheadData2 = java.nio.ByteBuffer.allocateDirect(Math.max(65536, (int)orgLength));
        }

        // Swap them if there's data in the secondary buffer
        if (readAheadData2.remaining() > 0)
        {
          java.nio.ByteBuffer swap = readAheadData;
          readAheadData = readAheadData2;
          readAheadData2 = swap;
        }

        // Resize and fill up the first buffer if it's empty
        if (readAheadData.remaining() == 0)
        {
          if (readAheadData.capacity() < orgLength)
            readAheadData = java.nio.ByteBuffer.allocateDirect(Math.max(65536, (int)orgLength));
          if (fileChannel.size() - offset >= readAheadData.capacity())
          {
            readAheadData.clear();
            readAheadPos = offset;
            while (readAheadData.remaining() > 0)
            {
              int currRead = fileChannel.read(readAheadData, offset);
              offset += currRead;
            }
            readAheadData.flip();
          }
        }
        // Now do the second buffer
        if (readAheadData2.remaining() == 0)
        {
          if (readAheadData2.capacity() < orgLength)
            readAheadData2 = java.nio.ByteBuffer.allocateDirect(Math.max(65536, (int)orgLength));
          offset = readAheadPos + readAheadData.remaining();
          if (fileChannel.size() - offset >= readAheadData2.capacity())
          {
            readAheadData2.clear();
            while (readAheadData2.remaining() > 0)
            {
              int currRead = fileChannel.read(readAheadData2, offset);
              offset += currRead;
            }
            readAheadData2.flip();
          }
        }
      }

      // Return the amount of bytes we did not write so it can be sent as overage and the connection isn't
      // blown
      return length;
    }

    protected void writeFile(long offset, long length) throws java.io.IOException
    {
      // Ensure the arguments will never crash or hang us
      if (length < 0)
      {
        length *= -1;
        offset -= length;
      }
      if (offset < 0)
        offset = 0;
      if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer transferData(" + offset + ", " + length + ")");
      transferDataUpload(offset, length);
    }

    protected void transferDataUpload(long offset, long length) throws java.io.IOException
    {
      // In case there's extra data in the request buffer from the client jamming on an upload
      if (commBufRead.hasRemaining())
      {
        int currWrite = (int)Math.min(length, commBufRead.remaining());
        if (MEDIA_SERVER_DEBUG) System.out.println("Extra data in the commBuf being uploaded size=" + currWrite);
        length -= currWrite;
        int oldLimit = commBufRead.limit();
        commBufRead.limit(commBufRead.position() + currWrite);

        if (remuxer != null)
        {
          remuxer.writeRemuxer(commBufRead);
        }
        else
        {
          fileChannel.write(commBufRead, offset);
        }

        offset += currWrite;
        commBufRead.limit(oldLimit);
      }
      try
      {
        if (blocking)
          TimeoutHandler.registerTimeout(TIMEOUT, s);
        while (length > 0)
        {
          if (!useNioTransfers || (Sage.LINUX_OS && offset +length >= Integer.MAX_VALUE) || remuxer != null)
          {
            // NOTE: Due to Java using the sendfile kernel API call to do the transfer,
            // there's a 32-bit limitation here. This is very unfortunate. But since it's
            // compiled into the JVM and into the Linux kernel there's really no other way around this
            // We also don't use them by default on Windows because of poor performance w/ network shares
            if (hackBuf == null)
            {
              hackBuf = java.nio.ByteBuffer.allocateDirect(65536);
            }
            hackBuf.clear();
            hackBuf.limit((int)Math.min(length, hackBuf.capacity()));
            int currRead = s.read(hackBuf);
            hackBuf.flip();

            if (remuxer != null)
            {
              remuxer.writeRemuxer(hackBuf);
            }
            else
            {
              fileChannel.write(hackBuf, offset);
            }

            offset += currRead;
            length -= currRead;
          }
          else
          {
            long currRead = fileChannel.transferFrom(s, offset, length);
            offset += currRead;
            length -= currRead;
          }
        }
      }
      finally
      {
        if (blocking)
          TimeoutHandler.clearTimeout(s);
      }
    }

    public void run()
    {
      try
      {
        s.socket().setTcpNoDelay(true);
        clientConnected();
        blocking = Sage.getBoolean("use_blocking_socket_for_mediaserver", true);
        if (blocking)
        {
          s.configureBlocking(true);
          // Timeouts don't work in NIO
          //s.socket().setSoTimeout(1000*60*30); // in case they're paused for awhile...the clients auto-reconnect anyways
          s.socket().setKeepAlive(true);
        }
        else
        {
          s.configureBlocking(false);
          readSelector = java.nio.channels.spi.SelectorProvider.provider().openSelector();
          s.register(readSelector, java.nio.channels.SelectionKey.OP_READ);
        }
        commBufRead = java.nio.ByteBuffer.allocate(4096);
        commBufWrite = java.nio.ByteBuffer.allocate(4096);
        // There's only 4 commands we take.
        // 1 - OPEN filename
        // 2 - CLOSE
        // 3 - SIZE
        // 4 - READ offset length
        StringBuffer tempString = readLineBytes();
        while (tempString != null && alive)
        {
          if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer recvd:" + tempString);
          if (tempString.indexOf("OPEN ") == 0)
          {
            closeFile();
            String fname = tempString.substring(5);
            // Do the platform path character conversion
            fname = IOUtils.convertPlatformPathChars(fname);
            if (MEDIA_SERVER_DEBUG) System.out.println("Converted pathname to:" + fname);
            openFile(fname);
          }
          else if (tempString.indexOf("OPENW ") == 0)
          {
            closeFile();
            String fname = tempString.substring(6);
            fname = convertToUnicode(fname);
            // Do the platform path character conversion
            fname = IOUtils.convertPlatformPathChars(fname);
            if (MEDIA_SERVER_DEBUG) System.out.println("Converted pathname to:" + fname);
            openFile(fname);
          }
          else if (tempString.indexOf("WRITEOPEN ") == 0)
          {
            closeFile();
            int idx = tempString.lastIndexOf(" ");
            String fname = tempString.substring(10, idx);
            // Do the platform path character conversion
            fname = IOUtils.convertPlatformPathChars(fname);
            int uploadKey = Integer.parseInt(tempString.substring(idx + 1));
            if (MEDIA_SERVER_DEBUG) System.out.println("Converted pathname to:" + fname);
            openWriteFile(fname, uploadKey, true);
          }
          else if (tempString.indexOf("WRITEOPENW ") == 0)
          {
            closeFile();
            int idx = tempString.lastIndexOf(" ");
            String fname = tempString.substring(11, idx);
            fname = convertToUnicode(fname);
            // Do the platform path character conversion
            fname = IOUtils.convertPlatformPathChars(fname);
            int uploadKey = Integer.parseInt(tempString.substring(idx + 1));
            if (MEDIA_SERVER_DEBUG) System.out.println("Converted pathname to:" + fname);
            openWriteFile(fname, uploadKey, true);
          }
          else if (tempString.indexOf("LISTW ") == 0)
          {
            String dirName = tempString.substring(6);
            dirName = IOUtils.convertPlatformPathChars(convertToUnicode(dirName));
            if (MEDIA_SERVER_DEBUG) System.out.println("Dir listing requested for:" + dirName);
            java.io.File theDir = new java.io.File(dirName);
            if (checkDirAccess(theDir))
            {
              if (theDir.isDirectory())
              {
                commBufWrite.clear();
                commBufWrite.put(OK_BYTES);
                // Now write one line which is the number of entries and then write each entry on a separate line after that
                String[] kids = theDir.list();
                commBufWrite.put((Integer.toString(kids == null ? 0 : kids.length) + "\r\n").getBytes()).flip();
                int numWritten = s.write(commBufWrite);
                if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
                if (kids != null)
                {
                  commBufWrite.clear();
                  for (int i = 0; i < kids.length; i++)
                  {
                    byte[] currBytes = kids[i].getBytes("UTF-16BE");
                    if (commBufWrite.remaining() < currBytes.length + 2)
                    {
                      commBufWrite.flip();
                      numWritten = s.write(commBufWrite);
                      if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
                      commBufWrite.clear();
                    }
                    commBufWrite.put(currBytes);
                    commBufWrite.put(RN_BYTES);
                  }
                  if (commBufWrite.position() > 0)
                  {
                    commBufWrite.flip();
                    numWritten = s.write(commBufWrite);
                    if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
                  }
                }
              }
              else
              {
                commBufWrite.clear();
                commBufWrite.put("NO_EXIST\r\n".getBytes()).flip();
                int numWritten = s.write(commBufWrite);
                if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
              }
            }
            else
            {
              commBufWrite.clear();
              commBufWrite.put("NON_MEDIA\r\n".getBytes()).flip();
              int numWritten = s.write(commBufWrite);
              if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
            }
          }
          else if (tempString.indexOf("LISTRECURSIVEW ") == 0 || tempString.indexOf("LISTRECURSIVEALLW ") == 0)
          {
            String dirName;
            boolean allFiles;
            if (tempString.indexOf("LISTRECURSIVEW ") == 0)
            {
              dirName = tempString.substring(15);
              allFiles = false;
            }
            else
            {
              dirName = tempString.substring(18);
              allFiles = true;
            }
            dirName = IOUtils.convertPlatformPathChars(convertToUnicode(dirName));
            if (MEDIA_SERVER_DEBUG) System.out.println("Recursive dir listing requested for:" + dirName);
            java.io.File theDir = new java.io.File(dirName);
            if (checkDirAccess(theDir))
            {
              if (theDir.isDirectory())
              {
                commBufWrite.clear();
                commBufWrite.put(OK_BYTES);
                // Now write one line which is the number of entries and then write each entry on a separate line after that
                java.io.File[] kids = IOUtils.listFilesRecursive(theDir, allFiles);
                commBufWrite.put((Integer.toString(kids == null ? 0 : kids.length) + "\r\n").getBytes()).flip();
                int numWritten = s.write(commBufWrite);
                if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
                if (kids != null)
                {
                  commBufWrite.clear();
                  for (int i = 0; i < kids.length; i++)
                  {
                    byte[] currBytes = kids[i].getAbsolutePath().getBytes("UTF-16BE");
                    if (commBufWrite.remaining() < currBytes.length + 2)
                    {
                      commBufWrite.flip();
                      numWritten = s.write(commBufWrite);
                      if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
                      commBufWrite.clear();
                    }
                    commBufWrite.put(currBytes);
                    commBufWrite.put(RN_BYTES);
                  }
                  if (commBufWrite.position() > 0)
                  {
                    commBufWrite.flip();
                    numWritten = s.write(commBufWrite);
                    if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
                  }
                }
              }
              else
              {
                commBufWrite.clear();
                commBufWrite.put("NO_EXIST\r\n".getBytes()).flip();
                int numWritten = s.write(commBufWrite);
                if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
              }
            }
            else
            {
              commBufWrite.clear();
              commBufWrite.put("NON_MEDIA\r\n".getBytes()).flip();
              int numWritten = s.write(commBufWrite);
              if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
            }
          }
          else if ("CLOSE".contentEquals(tempString))
          {
            closeFile();
            commBufWrite.clear();
            commBufWrite.put(OK_BYTES).flip();
            int numWritten = s.write(commBufWrite);
            if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
          }
          else if ("SIZE".contentEquals(tempString))
            sizeFile();
          else if (tempString.indexOf("READ ") == 0)
          {
            int idx = tempString.lastIndexOf(" ");
            readFile(Long.parseLong(tempString.substring(5, idx)),
                Long.parseLong(tempString.substring(idx + 1)));
          }
          else if (tempString.indexOf("WRITE ") == 0)
          {
            int idx = tempString.lastIndexOf(" ");
            writeFile(Long.parseLong(tempString.substring(6, idx)),
                Long.parseLong(tempString.substring(idx + 1)));
          }
          else if (tempString.indexOf("XCODE_SETUP ") == 0)
          {
            String xcodeMode = tempString.substring(tempString.indexOf(" ") + 1);
            if (Sage.DBG) System.out.println("MediaServer is serving up in transcode mode: " + xcodeMode);
            xcoder = new FFMPEGTranscoder();
            xcoder.setTranscodeFormat(xcodeMode, null);
            commBufWrite.clear();
            commBufWrite.put(OK_BYTES).flip();
            int numWritten = s.write(commBufWrite);
            if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
          }
          else if (tempString.indexOf("REMUX_SETUP ") == 0)
          {
            // REMUX_SETUP Mode OutputFormat Parameters...
            // Auto:
            // REMUX_SETUP AUTO OutputFormat IsTV
            // Ex. REMUX_SETUP AUTO PS TRUE

            // The file must be closed to turn this mode off.
            if (remuxer != null)
            {
              commBufWrite.clear();
              commBufWrite.put("INIT_ERROR\r\n".getBytes()).flip();

              if (Sage.DBG) System.out.println("MediaServer is already in remux mode ignoring: " +
                  tempString.substring(tempString.indexOf(" ") + 1));
            }
            else
            {
              if (Sage.DBG) System.out.println("MediaServer is writing in remux mode: " +
                  tempString.substring(tempString.indexOf(" ") + 1));

              StringTokenizer toker = new StringTokenizer(tempString.substring(12), " ");

              if (toker.countTokens() == 3)
              {
                String mode = toker.nextToken();
                int outputFormat = toker.nextToken().equalsIgnoreCase("TS") ? MPEGParser2.REMUX_TS : MPEGParser2.REMUX_PS;

                if (mode.equalsIgnoreCase("AUTO"))
                {
                  boolean isTV = toker.nextToken().equalsIgnoreCase("TRUE");
                  remuxer = new MediaServerRemuxer(fileChannel, outputFormat, isTV, this);
                } else
                {
                  // If a client is trying to use a newer mode, that doesn't exist, this default
                  // will be used since it should always work. This default is better than nothing
                  // and will be logged so we know to tell the user to upgrade.
                  if (Sage.DBG) System.out.println("MediaServer remux mode not supported;" +
                      " defaulting to AUTO TRUE");
                  remuxer = new MediaServerRemuxer(fileChannel, outputFormat, true, this);
                }
              }
              else
              {
                commBufWrite.clear();
                commBufWrite.put("PARAM_ERROR\r\n".getBytes()).flip();
              }

              commBufWrite.clear();
              commBufWrite.put(OK_BYTES).flip();
            }

            int numWritten = s.write(commBufWrite);
            if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
          }
          else if (tempString.indexOf("REMUX_CONFIG ") == 0)
          {
            if (remuxer != null)
            {
              String config = tempString.substring(13);
              if (config.equals("INIT"))
              {
                commBufWrite.clear();
                commBufWrite.put((remuxer.isInitialized() ? "TRUE\r\n" : "FALSE\r\n").getBytes()).flip();
              }
              else if (config.equals("SWITCHED"))
              {
                commBufWrite.clear();
                commBufWrite.put((remuxer.isSwitched() ? "TRUE\r\n" : "FALSE\r\n").getBytes()).flip();
              }
              else if (config.equals("FORCE_SWITCHED"))
              {
                remuxer.forceSwitched();
                commBufWrite.clear();
                commBufWrite.put(OK_BYTES).flip();
              }
              else if (config.equals("DISABLE_ASSIST"))
              {
                remuxer.disableInterAssist();
                commBufWrite.clear();
                commBufWrite.put(OK_BYTES).flip();
              }
              else if (config.equals("FORMAT"))
              {
                commBufWrite.clear();
                commBufWrite.put((remuxer.isInitialized() ?
                    remuxer.getContainerFormat().getFullPropertyString(false) + "\r\n" : "NULL\r\n").getBytes()).flip();
              }
              else if (config.equals("FILE"))
              {
                commBufWrite.clear();
                commBufWrite.put((currFile != null ?
                    currFile.getAbsoluteFile() + "\r\n" : "NULL\r\n").getBytes()).flip();
              }
              else if (config.equals("MODE"))
              {
                commBufWrite.clear();
                commBufWrite.put((remuxer.getOutputFormat() == MPEGParser2.REMUX_TS ?
                    "TS\r\n" : "PS\r\n").getBytes()).flip();
              }
              else if (config.startsWith("BUFFER "))
              {
                remuxer.setBufferLimit(Long.parseLong(config.substring(7)));
                fileChannel.position(0);
                commBufWrite.clear();
                commBufWrite.put(OK_BYTES).flip();
              }
              else
              {
                commBufWrite.clear();
                commBufWrite.put("ERROR\r\n".getBytes()).flip();
              }
            }
            else
            {
              commBufWrite.clear();
              commBufWrite.put("NO_INIT\r\n".getBytes()).flip();
            }

            int numWritten = s.write(commBufWrite);
            if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
          }
          else if (tempString.indexOf("REMUX_SWITCH ") == 0)
          {
            if (remuxer != null)
            {
              int idx = tempString.lastIndexOf(" ");
              String fname = tempString.substring(13, idx);
              // Do the platform path character conversion
              fname = IOUtils.convertPlatformPathChars(fname);
              int uploadKey = Integer.parseInt(tempString.substring(idx + 1));
              if (MEDIA_SERVER_DEBUG) System.out.println("Converted pathname to:" + fname);

              remuxer.startSwitch(fname, uploadKey);
              //Later we will call openWriteFile(fname, uploadKey);

              commBufWrite.clear();
              commBufWrite.put("OK\r\n".getBytes()).flip();
            }
            else
            {
              commBufWrite.clear();
              commBufWrite.put("NO_INIT\r\n".getBytes()).flip();
            }

            int numWritten = s.write(commBufWrite);
            if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
          }
          else if (tempString.indexOf("TRUNC ") == 0)
          {
            int idx = tempString.lastIndexOf(" ");

            if (fileChannel != null)
            {
              fileChannel.truncate(Long.parseLong(tempString.substring(6, tempString.length())));
              commBufWrite.clear();
              commBufWrite.put(OK_BYTES).flip();
            }
            else
            {
              commBufWrite.clear();
              commBufWrite.put("NON_MEDIA\r\n".getBytes()).flip();
            }

            int numWritten = s.write(commBufWrite);
            if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
          }
          else if (tempString.indexOf("FORCE ") == 0)
          {
            int idx = tempString.lastIndexOf(" ");

            if (fileChannel != null)
            {
              fileChannel.force(tempString.substring(6, tempString.length()).equalsIgnoreCase("TRUE"));
              commBufWrite.clear();
              commBufWrite.put(OK_BYTES).flip();
            }
            else
            {
              commBufWrite.clear();
              commBufWrite.put("NON_MEDIA\r\n".getBytes()).flip();
            }

            int numWritten = s.write(commBufWrite);
            if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
          }
          else if (tempString.indexOf("QUIT") == 0)
          {
            break;
          }
          else
          {
            commBufWrite.clear();
            commBufWrite.put(("UNKNOWN COMMAND " + tempString + "\r\n").getBytes()).flip();
            int numWritten = s.write(commBufWrite);
            if (MEDIA_SERVER_DEBUG) System.out.println("MediaServer wrote out " + numWritten + " bytes");
          }
          //outStream.flush();
          tempString = readLineBytes();
        }
      }
      catch (Exception e)
      {
        System.out.println("Error in MediaServerConnection of :" + e);
        e.printStackTrace(System.out);
      }
      finally
      {
        clientDisconnected();
        try
        {
          if (remuxer != null)
            remuxer.close();
          remuxer = null;
        }
        catch (Exception e)
        {}
        try
        {
          if (fileChannel != null)
            fileChannel.close();
          fileChannel = null;
        }
        catch (Exception e)
        {}
        try
        {
          if (fileStream != null)
            fileStream.close();
          fileStream = null;
        }
        catch (Exception e)
        {}
        /*try
        {
          if (uploadStream != null)
            uploadStream.close();
          uploadStream = null;
        }
        catch (Exception e)
        {}*/
        try
        {
          s.close();
          s = null;
        }
        catch (Exception e)
        {}
        if (readSelector != null)
        {
          try
          {
            readSelector.close();
            readSelector = null;
          }
          catch (Exception e)
          {}
        }
        if (xcoder != null)
        {
          xcoder.stopTranscode();
          xcoder = null;
        }
      }
    }

    public FileChannel getFileChannel()
    {
      return fileChannel;
    }

    public java.io.File getFile()
    {
      return currFile;
    }

    protected java.nio.channels.SocketChannel s;

    protected java.io.File currFile;
    protected MediaFile currMF;
    protected FileDownloader downer;
    protected java.io.FileInputStream fileStream;
    //protected java.io.FileOutputStream uploadStream;
    protected java.nio.channels.FileChannel fileChannel;
    protected java.nio.ByteBuffer commBufWrite;
    protected java.nio.ByteBuffer commBufRead;
    protected java.nio.ByteBuffer overageBuf;
    protected java.nio.ByteBuffer hackBuf;
    protected java.nio.channels.Selector readSelector;
    protected boolean blocking;
    protected long lastRecFileSize;
    protected boolean circFileRec;

    protected MediaServerRemuxer remuxer;

    protected boolean readAhead;

    protected TranscodeEngine xcoder;
  }
  private static final java.util.Map largeFileSizeMap = new java.util.HashMap();
  private static long LARGE_FILE_SIZE = 900000000000L;
}
