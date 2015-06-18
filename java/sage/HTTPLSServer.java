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
 * This class handles the server side portion of HTTP LiveStreaming to iOS clients. It takes a socket from the MCSR that has already had the first 8 bytes consumed
 * as part of the MCSR protocol. That will be the start of the GET request for the HTTP Live Session. The MCSR also passes in the UIManager so we can communicate with the
 * VideoFrame to get other data. As part of the URL, there will be a MediaFile ID, a segment #, client ID (MAC). We will then verify that MAC is already connected and that the
 * VideoFrame has the requested MediaFile open currently. That should be plenty for authentication.
 *
 * We then need to generate an m3u8 file for the initial request which has all of the bandwidth variants in it. Each of those items will also be an m3u8 file. When the individual
 * m3u8 file is requested, it will also then have a bandwidth tag in the URL so we know what rate to stream at. At that point we create an FFMPEGTranscoder with the desired target
 * rate in dynamic iOS mode (with special encoding parameters for higher-speed H264 encoding). One thing we still need to determine is if we have to specify the Content-Length ahead of
 * time or not. If we do, then we need to completely transcode a segment into a temp file on disk before we can fulfill the HTTP request. We will use 10 second segments by default, but
 * this should be configurable for testing of course. The individual m3u8 files will only have segments defined for the time span that is recorded for the active segment of the file.
 * The media player is destroyed and rebuilt when shifting between our recording segments, so we don't need to worry about that case. We also need to find out what happens for the HTTP request
 * if seeking is performed. We will analyze the timestamps that are produced by querying the transcoder directly....we should not need to utilize the Mpeg2FastReader class at all here, but
 * we may need to if the timestamps need to be more accurate.
 * @author Narflex
 */
public class HTTPLSServer implements Runnable
{
  /** Creates a new instance of HTTPLSServer */
  public HTTPLSServer(java.nio.ByteBuffer bb, java.nio.channels.SocketChannel sake)
  {
    this.readBuf = bb;
    this.sake = sake;
    timeout = Sage.getLong("http_timeout", 30000);
    Pooler.execute(this, "HTTPRequest", Thread.NORM_PRIORITY);
    writeBuf = java.nio.ByteBuffer.allocate(65536);
    String bwOptions = Sage.get("httpls_bandwidth_options", "160,320,864,64");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(bwOptions, ",");
    bandwidths = new int[toker.countTokens()];
    int i = 0;
    while (toker.hasMoreTokens())
    {
      try
      {
        bandwidths[i++] = Integer.parseInt(toker.nextToken()) * 1000;
      }
      catch (NumberFormatException nfe)
      {
        if (Sage.DBG) System.out.println("Invalid HTTPLS bandwidth specified: " + nfe);
        bandwidths[i++] = 320;
      }
    }
    partDur = Sage.getInt("httpls_part_duration_sec", 5);
    synchronized (cleanerLock)
    {
      if (!builtCleaner)
      {
        builtCleaner = true;
        Thread t = new Thread(new HTTPLSCleaner(), "HTTPLSCleaner");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
      }
    }
  }

  // The main thing we do in this thread is process the HTTP requests and send their responses...we do it in a loop to handle HTTP Keep Alive properly
  public void run()
  {
    // The first time the first 4 bytes have already been consumed by the MCSR
    boolean skipGET = true;
    try
    {
      boolean keepAlive = false;
      do
      {
        StringBuffer sb = new StringBuffer();
        String getRequest = IOUtils.readLineBytes(sake, readBuf, timeout, sb).trim();
        if (!skipGET)
        {
          if (!getRequest.startsWith("GET "))
          {
            if (Sage.DBG) System.out.println("Invalid HTTP request received of: " + getRequest);
            break;
          }
          getRequest = getRequest.substring(4);
        }
        skipGET = false;
        int spaceIdx = getRequest.lastIndexOf(' ');
        String pageRequest = getRequest.substring(0, spaceIdx).trim();
        String httpVer = getRequest.substring(spaceIdx + 1).trim();
        String requestParam = IOUtils.readLineBytes(sake, readBuf, timeout, sb);
        boolean blankFound = false;
        java.util.HashMap paramMap = new java.util.HashMap();
        while (requestParam.length() > 0)
        {
          int colonIdx = requestParam.indexOf(':');
          if (colonIdx != -1)
            paramMap.put(requestParam.substring(0, colonIdx).trim(), requestParam.substring(colonIdx + 1).trim());
          requestParam = IOUtils.readLineBytes(sake, readBuf, timeout, sb);
        }
        if (Sage.DBG) System.out.println("Complete HTTP request received! page=" + pageRequest + " httpVer=" + httpVer + " params=" + paramMap);

        keepAlive = "keep-alive".equals(paramMap.get("Connection"));
        if (paramMap.containsKey("Host"))
          myHost = (String) paramMap.get("Host");

        // Now determine which type of the 3 requests it is
        if (!pageRequest.startsWith("/iosstream_"))
        {
          if (Sage.DBG) System.out.println("Invalid page request-1 made for iOS HTTP server of: \"" + pageRequest + "\" abort connection!");
          break;
        }
        boolean isPlaylistRequest = pageRequest.endsWith(".m3u8");
        if (!isPlaylistRequest && !pageRequest.endsWith(".ts"))
        {
          if (Sage.DBG) System.out.println("Invalid page request-2 made for iOS HTTP server of: \"" + pageRequest + "\" abort connection!");
          break;
        }

        String subRequest = pageRequest.substring(10, pageRequest.lastIndexOf('.'));
        java.util.StringTokenizer toker = new java.util.StringTokenizer(subRequest, "_");
        if (toker.countTokens() != 4 && toker.countTokens() != 5)
        {
          if (Sage.DBG) System.out.println("Invalid page request-3 made for iOS HTTP server of: \"" + pageRequest + "\" abort connection!");
          break;
        }

        String clientMac = toker.nextToken();
        int mfId;
        try
        {
          mfId = Integer.parseInt(toker.nextToken());
        }
        catch (NumberFormatException nfe)
        {
          if (Sage.DBG) System.out.println("Invalid page request-4 made for iOS HTTP server of: \"" + pageRequest + "\" abort connection!");
          break;
        }
        int segmentNum;
        try
        {
          segmentNum = Integer.parseInt(toker.nextToken());
        }
        catch (NumberFormatException nfe)
        {
          if (Sage.DBG) System.out.println("Invalid page request-5 made for iOS HTTP server of: \"" + pageRequest + "\" abort connection!");
          break;
        }

        String bwStr = toker.nextToken();
        int bwkbps = 0;
        if (!"list".equals(bwStr))
        {
          try
          {
            bwkbps = Integer.parseInt(bwStr);
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("Invalid page request-6 made for iOS HTTP server of: \"" + pageRequest + "\" abort connection!");
            break;
          }
        }

        String sessionID = (String)paramMap.get("X-Playback-Session-Id");
        if (sessionID == null)
          sessionID = clientMac + "-" + mfId + "-" + segmentNum;

        MediaFile mf = Wizard.getInstance().getFileForID(mfId);
        if (mf == null)
        {
          if (Sage.DBG) System.out.println("Invalid MediaFileID in iOS HTTP Request of: " + mfId);
          break;
        }
        UIManager uiMgr = UIManager.getLocalUIByName(clientMac);
        if (Sage.getBoolean("httpls_require_client_connection", true) && uiMgr == null)
        {
          if (Sage.DBG) System.out.println("Invalid ClientMAC in iOS HTTP Request of: " + clientMac);
          break;
        }
        if (segmentNum >= mf.getNumSegments())
        {
          if (Sage.DBG) System.out.println("Invalid segment num for " + mf + " in iOS HTTP Request of: " + segmentNum);
          break;
        }
        if ("list".equals(bwStr) && isPlaylistRequest)
        {
          if (Sage.DBG) System.out.println("iOS HTTP Request for overall playlist for mf=" + mfId + " segment=" + segmentNum + " clientMac=" + clientMac);
          sb.setLength(0);
          // Build the string for the playlist response
          sb.append("#EXTM3U\r\n");
          for (int i = 0; i < bandwidths.length; i++)
          {
            sb.append("#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=" + bandwidths[i] + "\r\n");
            sb.append("http://" + myHost + "/iosstream_" + clientMac + "_" + mfId + "_" + segmentNum + "_" + bandwidths[i]/1000 + ".m3u8\r\n");
          }
          //sb.append("#EXT-X-ENDLIST\r\n");
          sendHTTPM3U8Response(sb.toString());
        }
        else if (isPlaylistRequest)
        {
          // Build the string for the playlist response
          sb.append("#EXTM3U\r\n");
          sb.append("#EXT-X-MEDIA-SEQUENCE:0\r\n");
          sb.append("#EXT-X-TARGETDURATION:" + partDur + "\r\n");
          long remTime = mf.getDuration(segmentNum) / 1000;
          int numParts = (int)Math.ceil(remTime / partDur);
          if (Sage.DBG) System.out.println("iOS HTTP Request for playlist at " + bwkbps + "kbps for mf=" + mfId + " segment=" + segmentNum + " clientMac=" + clientMac + " totalParts=" + numParts);
          int i = 0;
          while (remTime > 0)
          {
            long currDur = Math.min(remTime, partDur);
            remTime -= partDur;
            sb.append("#EXTINF:" + currDur + ",\r\n");
            sb.append("http://" + myHost + "/iosstream_" + clientMac + "_" + mfId + "_" + segmentNum + "_" + bwkbps + "_" + i++ + ".ts\r\n");
          }
          if (!mf.isRecording(segmentNum))
          {
            sb.append("#EXT-X-ENDLIST\r\n");
          }

          // Setup the transcoder now and prep the first 2 parts, this'll prevent it from thinking we have low bandwidth when it does the requests
          if (setupTranscoder(sessionID, mf, segmentNum, bwkbps, 0, uiMgr == null ? null : uiMgr.getVideoFrame()))
          {
            int prebufferPartNum = Math.max(0, xcode.lastRequestedPart - 1);
            if (Sage.DBG) System.out.println("Doing request for stream part " + prebufferPartNum + " to ensure it's buffered before we return to avoid bandwidth calculation issues...");
            xcode.transcoder.getSegmentFile(prebufferPartNum);
            xcode.transcoder.markSegmentConsumed(prebufferPartNum);
            xcode.lastActivityTime = Sage.time();
            if (xcode.transcoder.isTranscoding())
            {
              if (Sage.DBG) System.out.println("Doing request for stream part " + (prebufferPartNum+1) + " to ensure it's buffered before we return to avoid bandwidth calculation issues...");
              xcode.transcoder.getSegmentFile(prebufferPartNum+1);
              xcode.transcoder.markSegmentConsumed(prebufferPartNum+1);
              // Do yet another one if we're at the beginning to really ensure we have enough pre-buffered
              if (prebufferPartNum == 0 && xcode.transcoder.isTranscoding())
              {
                if (Sage.DBG) System.out.println("Doing request for stream part " + (prebufferPartNum+1) + " to ensure it's buffered before we return to avoid bandwidth calculation issues...");
                xcode.transcoder.getSegmentFile(prebufferPartNum+2);
                xcode.transcoder.markSegmentConsumed(prebufferPartNum+2);
              }
            }
          }

          if (Sage.DBG) System.out.println("Now sending back the individual bandwidth m3u8 file since we have the first 2 parts prepped...");
          sendHTTPM3U8Response(sb.toString());
        }
        else
        {
          int streamPart;
          try
          {
            streamPart = Integer.parseInt(toker.nextToken());
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("Invalid page request-7 made for iOS HTTP server of: \"" + pageRequest + "\" abort connection!");
            break;
          }
          if (Sage.DBG) System.out.println("iOS HTTP Request for media stream part " + streamPart + " for mf=" + mfId + " segment=" + segmentNum + " clientMac=" + clientMac);

          setupTranscoder(sessionID, mf, segmentNum, bwkbps, streamPart, uiMgr == null ? null : uiMgr.getVideoFrame());
          xcode.lastRequestedPart = streamPart;

          // Now get the segment file that's requested and send it out!
          if (Sage.DBG) System.out.println("iOS HTTP server is requesting part # " + streamPart + " from the transcoder...");
          java.io.File targetFile = xcode.transcoder.getSegmentFile(streamPart);
          if (Sage.DBG) System.out.println("iOS HTTP server got part # " + streamPart + " from the transcoder, send it out:" + targetFile);
          if (targetFile == null)
            break;
          sendBackTSFile(targetFile);
          xcode.transcoder.markSegmentConsumed(streamPart);
          if (Sage.DBG) System.out.println("iOS HTTP server finished sending part # " + streamPart + " from the transcoder for: " + targetFile + " length=" + targetFile.length() +
              " rate=" + (targetFile.length()*8/(partDur*1000)) + " kbps");
        }
        if (xcode != null)
          xcode.lastActivityTime = Sage.time();
      } while (keepAlive);
    }
    catch (java.io.InterruptedIOException iioe)
    {
      if (Sage.DBG) System.out.println("TIMEOUT with HTTP socket...close it now");
    }
    catch (java.io.IOException ioe)
    {
      if (Sage.DBG) System.out.println("Error with HTTP socket of:" + ioe);
    }
    finally
    {
      try{sake.close();}catch(Exception e){}
    }
  }

  // Returns true if a new transcoder was spawned
  private static Object xcodeSetupLock = new Object();
  private boolean setupTranscoder(String sessionID, MediaFile mf, int segmentNum, int bwkbps, int streamPart, VideoFrame vf) throws java.io.IOException
  {
    synchronized (xcodeSetupLock)
    {
      if (xcode != null)
        xcode.lastActivityTime = Sage.time();

      if (xcode == null)
      {
        // Check the cache map for a transcoder that's already running
        xcode = (XCodeInfo) cachedXCodeMap.get(sessionID);
        if (xcode != null)
        {
          xcode.lastActivityTime = Sage.time();
          if (!cachedXCodeMap.containsKey(sessionID))
            xcode = null; // sync, the other thread could have removed it during the above call
          if (xcode.transcoder.isTranscodeDone() && !xcode.transcoder.didTranscodeCompleteOK())
          {
            if (Sage.DBG) System.out.println("Transcoder failure detected! Kill it and build a new one!");
            cachedXCodeMap.remove(sessionID);
            xcode = null;
          }
        }
        if (Sage.DBG && xcode != null) System.out.println("iOS HTTP Request found a cached transcoder, re-using it!");
      }

      if (xcode == null)
      {
        if (Sage.DBG) System.out.println("iOS HTTP server is launching the transcoder for the source file " + mf);
        // We need to create and start the transcoder
        xcode = new XCodeInfo();
        xcode.lastActivityTime = Sage.time();
        xcode.mf = mf;
        xcode.segment = segmentNum;
        xcode.sessionID = sessionID;
        xcode.vf = vf;
      }
      if (xcode.transcoder == null)
      {
        xcode.transcoder = new FFMPEGTranscoder();
        xcode.transcoder.setEstimatedBandwidth(bwkbps * 1000);

        int numTempFiles = Sage.getInt("xcode_num_temp_httpls_segments", 20);
        java.io.File[] tempSegFiles = new java.io.File[numTempFiles];
        for (int i = 0; i < numTempFiles; i++)
        {
          tempSegFiles[i] = java.io.File.createTempFile("stvhttpls", ".ts");
          tempSegFiles[i].deleteOnExit();
        }
        xcode.transcoder.enableSegmentedOutput(partDur * 1000, tempSegFiles);
        xcode.transcoder.setActiveFile(mf.isRecording(segmentNum));
        xcode.transcoder.setSourceFile(null, mf.getFile(segmentNum));
        xcode.transcoder.setTranscodeFormat("dynamicts", mf.getFileFormat());
        xcode.transcoder.seekToTime(streamPart * partDur * 1000);
        cachedXCodeMap.put(sessionID, xcode);
        return true;
      }

      // NOTE: We should do a bandwidth calculation on the server side as well and then use that knowledge in our rate adaptation. If there's delays due to pre-buffering then we could
      // avoid them by not adjusting the rate when it is requested. We may also be able to always create playlists like live TV would so the client won't request ahead as much (and we
      // base them on how much we've actually transcoded so far). We would then need to be smart about this and query the VideoFrame for what the target seek time is so that it's actually
      // possible for it to seek as far as it needs to in the file. It's possible that the iOS media player won't try to perform a seek to part of the stream that's not in the playlist (sounds
      // right since it won't know what URI to request), but since it's always requesting a new playlist then it will do that request again (maybe the seek will even kick it to do that) and then
      // we'll know how far to put segments from the playlist in there. But then if they seek back the client may end up caching that playlist and not request a new one which would break what we're
      // trying to do at that point.

      int targetVideoKbps = Math.max(64, bwkbps - 32);
      if (xcode.transcoder.getCurrentVideoBitrateKbps() > targetVideoKbps)
      {
        if (Sage.DBG) System.out.println("Requested bandwidth has decreased! Dump the transcoder and rebuild it!");
        xcode.transcoder.stopTranscode();
        //cachedXCodeMap.remove(sessionID);
        xcode.transcoder = null;
        return setupTranscoder(sessionID, mf, segmentNum, bwkbps, streamPart, vf);
      }
      else if (xcode.transcoder.getCurrentVideoBitrateKbps() < targetVideoKbps)
      {
        if (Sage.DBG) System.out.println("ADJUSTING HTTP streaming video bandwidth from " + xcode.transcoder.getCurrentVideoBitrateKbps() + " to " + targetVideoKbps);
        xcode.transcoder.dynamicVideoRateAdjust(targetVideoKbps - xcode.transcoder.getCurrentVideoBitrateKbps());
      }
      return false;
    }
  }

  private void sendBackTSFile(java.io.File theFile) throws java.io.IOException
  {
    writeBuf.clear();
    appendStringToWriteBuf("HTTP/1.1 200 OK\r\n");
    appendStringToWriteBuf("Server: SageTV " + UIManager.SAGE + "\r\n");
    appendStringToWriteBuf("Date: " + new java.util.Date().toString() + "\r\n");
    appendStringToWriteBuf("Content-Type: video/MP2T\r\n");
    appendStringToWriteBuf("Content-Length: " + theFile.length() + "\r\n\r\n");
    if (writeBuf.position() > 0)
    {
      writeBuf.flip();
      sake.write(writeBuf);
    }
    java.nio.channels.FileChannel fc = new java.io.FileInputStream(theFile).getChannel();
    int transferChunkSize = Sage.getInt("httpls_transfer_chunk_size", 32768);
    long totalSize = theFile.length();
    long offset = 0;
    try
    {
      if (transferChunkSize == 0)
      {
        fc.transferTo(0, totalSize, sake);
      }
      else
      {
        while (totalSize > offset)
        {
          long currSize = Math.min(transferChunkSize, totalSize - offset);
          fc.transferTo(offset, currSize, sake);
          offset += currSize;
        }
      }
    }
    finally
    {
      fc.close();
    }
  }

  private void sendHTTPM3U8Response(String data) throws java.io.IOException
  {
    writeBuf.clear();
    appendStringToWriteBuf("HTTP/1.1 200 OK\r\n");
    appendStringToWriteBuf("Server: SageTV " + UIManager.SAGE + "\r\n");
    appendStringToWriteBuf("Content-Type: application/vnd.apple.mpegurl; charset=ISO-8859-1\r\n");
    appendStringToWriteBuf("Content-Length: " + data.length() + "\r\n\r\n");
    appendStringToWriteBuf(data);
    if (writeBuf.position() > 0)
    {
      writeBuf.flip();
      sake.write(writeBuf);
    }
  }

  private void appendStringToWriteBuf(String s) throws java.io.IOException
  {
    byte[] b = s.getBytes();
    if (writeBuf.remaining() > b.length)
    {
      writeBuf.put(b);
      return;
    }
    if (writeBuf.remaining() == 0)
    {
      writeBuf.flip();
      sake.write(writeBuf);
      writeBuf.clear();
    }
    int off = 0;
    int len = b.length;
    while (len > 0)
    {
      int rem = Math.min(writeBuf.remaining(), len);
      writeBuf.put(b, 0, rem);
      if (writeBuf.remaining() == 0)
      {
        writeBuf.flip();
        sake.write(writeBuf);
        writeBuf.clear();
      }
      off = rem;
      len -= rem;
    }
  }

  private java.nio.ByteBuffer readBuf;
  private java.nio.ByteBuffer writeBuf;
  private java.nio.channels.SocketChannel sake;
  private String myHost;
  private long timeout;
  private int[] bandwidths;
  private int partDur;
  private XCodeInfo xcode;

  private static java.util.HashMap cachedXCodeMap = new java.util.HashMap();

  private static class XCodeInfo
  {
    public String sessionID;
    public MediaFile mf;
    public int segment;
    public long lastActivityTime;
    public FFMPEGTranscoder transcoder;
    public VideoFrame vf;
    public int lastRequestedPart;
  }

  // Call this when playback of a file is closed so the transcoder can be killed for that client
  public static void kickCleaner()
  {
    synchronized (cleanerLock)
    {
      cleanerNeedsWork = true;
      cleanerLock.notifyAll();
    }
  }

  // This is how we can change the active file state when needed
  public static void notifyOfInactiveFile(String s)
  {
    java.util.Iterator walker = cachedXCodeMap.values().iterator();
    while (walker.hasNext())
    {
      XCodeInfo xci = (XCodeInfo) walker.next();
      if (xci.mf.getFile(xci.segment).toString().equals(s))
      {
        xci.transcoder.setActiveFile(false);
      }
    }
  }

  private static boolean builtCleaner = false;
  private static Object cleanerLock = new Object();
  private static boolean cleanerNeedsWork = false;
  private static class HTTPLSCleaner implements Runnable
  {
    public void run()
    {
      if (Sage.DBG) System.out.println("The HTTPLSCleaner thread is now running...");
      // This is the time a connection can be inactive for if connections are allowed that aren't linked to a real player before it will be destroyed
      long timeToKill = Sage.getLong("httpls_xcode_expire_time", 30000);
      while (true)
      {
        synchronized (cleanerLock)
        {
          if (!cleanerNeedsWork)
          {
            try
            {
              cleanerLock.wait(15000);
            }
            catch (InterruptedException e){}
          }
          cleanerNeedsWork = false;
        }

        if (cachedXCodeMap.isEmpty())
          continue;

        java.util.Iterator walker = cachedXCodeMap.values().iterator();
        while (walker.hasNext())
        {
          XCodeInfo info = (XCodeInfo) walker.next();
          if (info.vf != null)
          {
            // Verify this MediaFile+segment is still being viewed
            if (info.vf.getCurrFile() != info.mf || info.vf.getCurrSegment() != info.segment)
            {
              if (Sage.DBG) System.out.println("HTTPLSCleaner found a transcoder that is not in use for " + info.mf + " segment=" + info.segment + " killing it!");
              info.transcoder.stopTranscode();
              walker.remove();
            }
          }
          else // this is from a generic HTTP connection
          {
            long diff = Sage.time() - info.lastActivityTime;
            if (diff > timeToKill)
            {
              if (Sage.DBG) System.out.println("HTTPLSCleaner found an expired transcoder that has not been active for " + diff + " msec for " + info.mf + " and segment=" + info.segment +
                  " killing it now!");
              info.transcoder.stopTranscode();
              walker.remove();
            }
          }
        }
      }
    }
  }
}
