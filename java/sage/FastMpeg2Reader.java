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

import sage.nio.BufferedFileChannel;
import sage.nio.LocalFileChannel;
import sage.nio.RemoteFileChannel;
import sage.nio.SageFileChannel;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;

public final class FastMpeg2Reader
{
  private SageFileChannel ins;
  private sage.media.bluray.BluRayStreamer bdp; // for faster access w/out casting
  private TranscodeEngine inxs;
  private File mpegFile;
  private String hostname; // for streamed network files
  private byte bfrag; // fragment of a byte that's remaining in the read
  private byte rem; // number of bits valid in the bfrag
  private byte[] peeks; // bytes put back into the stream from putbackBits
  private byte numPeeks; // number of valid bytes in peeks
  private long bitsDone; // number of bits read in the overall file
  private long lastSCR;
  private int lastState;
  private long firstPTS = -1;
  private long durationMsec = -1;
  private long finalPTS = -1;
  private boolean log;
  private int parseLevel;
  private boolean ptsRollover;
  private int[] validPIDs;
  private int videoPID;
  private boolean isBluRaySource;

  // This is only needed for the transcoding engine
  private boolean activeFileSource;

  private String streamTranscodeMode;
  private sage.media.format.ContainerFormat sourceFormat;
  // This is used when the transcoding engine doesn't support timestamp offsets correctly
  private long transcodePTSOffset;

  private long streamBitratePtsCalcTime;
  private long streamBitrate; // in bytes/second

  private boolean ts;
  private boolean tsSynced;
  private boolean mpeg1;

  // Because we have lame TS detection here
  private boolean forceTSSource;

  private long lastFullParseBytePos = -(DIST_BETWEEN_FULL_PARSES + 1);

  // Used for variable rate playback. If this is not 1, then we're only trying to send I frames and we
  // do some seeking before and after this
  private int playbackRate = 1;
  //	private long bytesReadForThisSkip;
  private long msecStartForSkip;
  private long targetSeekPTS = -1;
  private int numReseeksLeft = 0;
  private boolean useCustomTimestamps;
  private long currTimestampDiff; // diff between our custom timestamps and the PTS of the stream

  // For past seeks so we can be more accurate than just using 3 points for interpolation
  private MPEG2PES indexData1;
  private MPEG2PES indexData2;
  private MPEG2PES initialPTSIndex;
  private MPEG2PES durationIndex;

  private boolean vc1RemapMode;
  private boolean h264Video;
  private boolean mpeg2Video;

  private boolean alignIFrames = false;
  private long lastRawIFramePTS;
  private long lastRawVideoPTS;
  // This is for when we're seeking backwards and we need to find out if we're not skipping enough to hit a new IFrame
  private long lastRawBackSkipIFramePTS;
  private long lastFastPlayRealStartTime;
  private long lastFastPlayPTSStartTime;
  private int iframeSkipMultiplier4;

  private boolean useHighRateSkipping = false;

  private int tsPacketSize = 188;
  private int targetBDTitle;
  private int transferIncrement;
  final boolean debugPush = Sage.getBoolean("miniclient/debug_push", false);

  public static final long PACK_START_CODE = 0x000001BA;
  public static final long SYSTEM_HEADER_START_CODE = 0x000001BB;
  public static final long PACKET_START_CODE_PREFIX = 0x000001;
  public static final long MPEG_PROGRAM_END_CODE = 0x000001B9;
  public static final int STUFFING_BYTE = 0xFF;
  public static final int I_FRAME = 1;
  public static final int P_FRAME = 2;
  public static final int B_FRAME = 3;
  public static final int D_FRAME = 4;

  public static final int PROGRAM_STREAM_MAP = 0xBC;
  public static final int PADDING_STREAM = 0xBE;
  public static final int PRIVATE_STREAM_1 = 0xBD;
  public static final int PRIVATE_STREAM_2 = 0xBF;
  public static final int ECM = 0xF0;
  public static final int EMM = 0xF1;
  public static final int AUDIO_GLOBAL = 0xB8;
  public static final int VIDEO_GLOBAL = 0xB9;
  public static final int RESERVED_STREAM = 0xBC;
  public static final int AUDIO_STREAM = 0xC0;
  public static final int AUDIO_STREAM_MASK = 0xE0;
  public static final int VIDEO_STREAM = 0xE0;
  public static final int VIDEO_STREAM_MASK = 0xF0;
  public static final int DATA_STREAM = 0xF0;
  public static final int DATA_STREAM_MASK = 0xF0;
  public static final int PROGRAM_STREAM_DIRECTORY = 0xFF;
  public static final int DSMCC_STREAM = 0xF2;
  public static final int H222_STREAM = 0xF8;
  public static final int FAST_FORWARD = 0;
  public static final int SLOW_MOTION = 1;
  public static final int FREEZE_FRAME = 2;
  public static final int FAST_REVERSE = 3;
  public static final int SLOW_REVERSE = 4;

  public static final long PICTURE_START_CODE = 0x00000100;
  public static final long USER_DATA_START_CODE = 0x000001B2;
  public static final long SEQUENCE_HEADER_CODE = 0x000001B3;
  public static final long SEQUENCE_ERROR_CODE = 0x000001B4;
  public static final long EXTENSION_START_CODE = 0x000001B5;
  public static final long SEQUENCE_END_CODE = 0x000001B7;
  public static final long GROUP_START_CODE = 0x000001B8;

  public static final int SEQUENCE_EXTENSION_ID = 0x1;
  public static final int SEQUENCE_DISPLAY_EXTENSION_ID = 0x2;
  public static final int QUANT_MATRIX_EXTENSION_ID = 0x3;
  public static final int COPYRIGHT_EXTENSION_ID = 0x4;
  public static final int SEQUENCE_SCALABLE_EXTENSION_ID = 0x5;
  public static final int PICTURE_DISPLAY_EXTENSION_ID = 0x7;
  public static final int PICTURE_CODING_EXTENSION_ID = 0x8;
  public static final int PICTURE_PAN_SCAN_EXTENSION_ID = 0x4;
  public static final int PICTURE_SPATIAL_SCALABLE_EXTENSION_ID = 0x9;
  public static final int PICTURE_TEMPORAL_SCALABLE_EXTENSION_ID = 0xA;

  public static final long MAX_PTS = 8589934592L; // 2^33 is where the PTS rolls over

  // scalable_mode
  public static final int SPATIAL_SCALABILITY = 0x1;
  public static final int TEMPORAL_SCALABILITY = 0x3;

  // picture_structure
  public static final int TOP_FIELD = 0x1;
  public static final int BOTTOM_FIELD = 0x2;
  public static final int FRAME_PICTURE = 0x3;

  public static final int PARSE_LEVEL_ALL = 0; // full parsing
  public static final int PARSE_LEVEL_NONE = 1; // don't parse read byte data at all, just pass it through
  public static final int PARSE_LEVEL_PES = 2; // parse read byte data at the PES level, tracks pack byte position for PTS timestamps
  public static final int PARSE_LEVEL_VIDEO = 3; // parse read byte data at the MPEG2 video level, finds I frames also

  private static final long DIST_BETWEEN_FULL_PARSES = 2*1024*1024;

  public static class MPEG2PS
  {
    volatile long bytePos;
    int videoStreamID;
    int audioStreamID;
    boolean constantPackSize = true;
    int packSize;
    int program_mux_rate;
  }

  public static class MPEG2PES
  {
    volatile long pts;
    long dts;
    int stream_id;
    int pid;
    long ptsPackBytePos;
    long dtsPackBytePos;
  }

  public static class MPEG2Video
  {
    int bit_rate_value;
    int horizontal_size_value;
    int vertical_size_value;
    int picture_coding_type;
  }

  MPEG2PS parsedPS;
  MPEG2PES parsedPES;
  MPEG2Video parsedVideo;

  /** Creates new Mpeg2Reader */
  public FastMpeg2Reader(String filename)
  {
    this(new File(filename));
  }
  public FastMpeg2Reader(File theFile)
  {
    this(theFile, null);
  }
  public FastMpeg2Reader(File theFile, String inHostname)
  {
    mpegFile = theFile;
    hostname = inHostname;

    rem = -1;
    // NOTE: This needs to be as big as the pack size or it can crash when
    // we put the extra bytes we read from the stream in here
    // UPDATE: 6/15/05 - this actually needs to be as big as the buffer
    peeks = new byte[65536];
    numPeeks = 0;
    bitsDone = 0;
    bfrag = 0;

    parsedPS = new MPEG2PS();
    parsedPES = new MPEG2PES();
    parsedVideo = new MPEG2Video();
    indexData1 = new MPEG2PES();
    indexData2 = new MPEG2PES();

    parseLevel = PARSE_LEVEL_PES;

    transferIncrement = Sage.getInt("miniplayer/push_buffer_transfer_increment", (!Sage.WINDOWS_OS) ? 0 : 32768);
  }

  public void setIFrameAlign(boolean x)
  {
    alignIFrames = x;
  }

  public void setLogging(boolean x) { log = x; }
  public void setActiveFile(boolean x)
  {
    if (activeFileSource != x)
    {
      activeFileSource = x;
      if (!activeFileSource && inxs != null)
      {
        inxs.setActiveFile(false);
      }
    }
  }
  public void setForcedTSSource(boolean x)
  {
    forceTSSource = x;
  }

  public void setTargetBDTitle(int x)
  {
    targetBDTitle = x;
    isBluRaySource = true;
  }

  public TranscodeEngine getTranscoder()
  {
    return inxs;
  }

  private boolean isValidPID(int pid)
  {
    if (validPIDs == null) return true;
    if (useCustomTimestamps && pid == 0x1FFF) return true;
    for (int i = 0; i < validPIDs.length; i++)
      if (validPIDs[i] == pid)
        return true;
    return false;
  }

  public void setStreamTranscodeMode(String x, sage.media.format.ContainerFormat inSourceFormat)
  {
    streamTranscodeMode = x;
    sourceFormat = inSourceFormat;
  }
  public void init(boolean findFirstPTS, boolean findDuration, boolean allowRemuxing) throws IOException
  {
    boolean forcedPS = false;
    parsedPES.pts = -1;
    durationMsec = finalPTS = firstPTS = -1;
    if (hostname != null && hostname.length() > 0 && streamTranscodeMode == null)
    {
      if (isBluRaySource)
      {
        if (Sage.DBG) System.out.println("MPEG2 pusher detected network BluRay source format...");
        sage.media.bluray.BluRayFile bnf = new sage.media.bluray.BluRayFile(hostname, mpegFile, false, targetBDTitle, 131072);
        bdp = bnf;
        ins = bnf;
        forceTSSource = true;
        tsPacketSize = 192;
      }
      else
      {
        ins = new BufferedFileChannel(new RemoteFileChannel(hostname, mpegFile), 131072, true);
        // If we already know it's TS then set it that way.
        // This is MUCH more reliable then the TS detection we have below which simply checks the first byte for 0x47
        if (sourceFormat != null)
        {
          forceTSSource = sage.media.format.MediaFormat.MPEG2_TS.equals(sourceFormat.getFormatName());
          // Deal with 192 byte packet TS files
          if (forceTSSource && sourceFormat.getPacketSize() != 0)
            tsPacketSize = sourceFormat.getPacketSize();
          if (forceTSSource)
          {
            java.util.ArrayList temp = new java.util.ArrayList();
            for (int i = 0; i < sourceFormat.getNumberOfStreams(); i++)
            {
              String currID = sourceFormat.getStreamFormat(i).getId();
              if (currID != null && currID.length() > 0)
              {
                temp.add(currID);
              }
            }
            if (!temp.isEmpty())
            {
              validPIDs = new int[temp.size()];
              for (int i = 0; i < temp.size(); i++)
              {
                try
                {
                  validPIDs[i] = Integer.parseInt(temp.get(i).toString(), 16);
                }
                catch (NumberFormatException nfe)
                {
                  if (Sage.DBG) System.out.println("ERROR parsing PID of:" + nfe + " ignoring TS PID match check");
                  validPIDs = null;
                  break;
                }
              }
            }
          }
        }
      }
    }
    else if (streamTranscodeMode != null)
    {
      inxs = new FFMPEGTranscoder();
      // LAZER - Comment out the above line and uncomment the below line for LAZER support
      //inxs = new DShowTranscodeEngine();
      inxs.setActiveFile(activeFileSource);
      inxs.setSourceFile(hostname, mpegFile);
      inxs.setTranscodeFormat(streamTranscodeMode, sourceFormat);
      inxs.setEnableOutputBuffering(true);
      forcedPS = true;
      firstPTS = 45000;
      findFirstPTS = false;
    }
    else
    {
      // For TS files we run them through the remuxer, for PS files we stream them directly
      if (allowRemuxing && sourceFormat != null && sage.media.format.MediaFormat.MPEG2_TS.equals(sourceFormat.getFormatName()))
      {
        forcedPS = true;
        firstPTS = 45000;
        findFirstPTS = false;
        if (Sage.DBG) System.out.println("Using the MPEG2 Remuxer");
        inxs = new RemuxTranscodeEngine();
        inxs.setActiveFile(activeFileSource);
        inxs.setSourceFile(hostname, mpegFile);
        inxs.setTranscodeFormat(sourceFormat, null);
        inxs.startTranscode();
      }
      else if (mpegFile.isDirectory() && sage.media.format.MediaFormat.MPEG2_TS.equals(sourceFormat.getFormatName()))
      {
        if (Sage.DBG) System.out.println("MPEG2 pusher detected BluRay source format...");
        sage.media.bluray.BluRayFile bnf = new sage.media.bluray.BluRayFile(mpegFile, false, targetBDTitle, 65536);
        bdp = bnf;
        ins = bnf;
        forceTSSource = true;
        tsPacketSize = 192;
      }
      else
      {
        ins = new sage.nio.BufferedFileChannel(new LocalFileChannel(mpegFile, true), true);
        if (sourceFormat != null && sage.media.format.MediaFormat.VC1.equals(sourceFormat.getPrimaryVideoFormat()))
          vc1RemapMode = true;
        // If we already know it's TS then set it that way.
        // This is MUCH more reliable then the TS detection we have below which simply checks the first byte for 0x47
        if (sourceFormat != null)
        {
          forceTSSource = sage.media.format.MediaFormat.MPEG2_TS.equals(sourceFormat.getFormatName());
          // Deal with 192 byte packet TS files
          if (forceTSSource && sourceFormat.getPacketSize() != 0)
            tsPacketSize = sourceFormat.getPacketSize();
          if (forceTSSource)
          {
            java.util.ArrayList temp = new java.util.ArrayList();
            for (int i = 0; i < sourceFormat.getNumberOfStreams(); i++)
            {
              String currID = sourceFormat.getStreamFormat(i).getId();
              if (currID != null && currID.length() > 0)
                temp.add(currID);
            }
            if (!temp.isEmpty())
            {
              validPIDs = new int[temp.size()];
              for (int i = 0; i < temp.size(); i++)
              {
                try
                {
                  validPIDs[i] = Integer.parseInt(temp.get(i).toString(), 16);
                }
                catch (NumberFormatException nfe)
                {
                  if (Sage.DBG) System.out.println("ERROR parsing PID of:" + nfe + " ignoring TS PID match check");
                  validPIDs = null;
                  break;
                }
              }
            }
          }
        }
      }
    }
    if (sourceFormat != null)
    {
      sage.media.format.VideoFormat vForm = sourceFormat.getVideoFormat();
      if (vForm != null)
      {
        if (vForm.getWidth() > 1000)
        {
          useHighRateSkipping = true;
        }
        if (sage.media.format.MediaFormat.MPEG2_VIDEO.equals(vForm.getFormatName()))
          mpeg2Video = true;
        else if (sage.media.format.MediaFormat.H264.equals(vForm.getFormatName()))
          h264Video = true;
        String vidTag = vForm.getId();
        if (vidTag != null && vidTag.length() > 0)
        {
          try
          {
            videoPID = Integer.parseInt(vidTag, 16);
          }
          catch (NumberFormatException nfe){}
        }
      }
    }
    if (Sage.DBG) System.out.println("Mpeg2Reader is detecting timestamp boundaries in the file...");
    if (!forcedPS)
    {
      // Check for 0x47 TS sync byte - be sure to wait for data!
      waitForPack();
      int syncByte = readByte();
      if (syncByte == 0x47 || forceTSSource)
      {
        ts = true;
      }
      putbackBits(syncByte, 8);
      bitsDone -= 8;
    }
    // We only do I Frame alignment for MPEG2 Video or H264 inside of a TS
    alignIFrames = alignIFrames && (mpeg2Video || (h264Video && ts));
    if (Sage.DBG) System.out.println("IFrame alignment=" + alignIFrames);
    // NOTE: 2-27-07 - Narflex - I added these waitForPack() calls so we don't hit an EOF when finding the intial PTS in a file
    if (findFirstPTS || findDuration)
    {
      waitForPack();
      packAlign();
    }
    if (Sage.DBG && (findFirstPTS || findDuration) && firstPTS < 0) System.out.println("Mpeg2Reader about to determine the firstPTS in the file...");
    int ptsCount = (firstPTS < 0) ? 5 : 0;
    long lastPTS = -1;
    while ((findFirstPTS || findDuration) && ptsCount > 0)
    {
      waitForPack();
      if (!pack())
        break;
      if (parsedPES.pts != -1 && isValidPID(parsedPES.pid) && parsedPES.pts != lastPTS)
      {
        // We've seen HDPVR files with an extra packet of junk at the beginning which is from the prior recording...so get the first 5 PTS's, and take the minimum of them as the real initial PTS
        if (firstPTS < 0)
          firstPTS = parsedPES.pts;
        else
          firstPTS = Math.min(firstPTS, parsedPES.pts);
        lastPTS = parsedPES.pts;
        ptsCount--;
        if (ptsCount == 0)
          break;
      }
      /*pack_header();
			while (nextbits(24) == PACKET_START_CODE_PREFIX &&
				nextbits(32) != PACK_START_CODE &&
				firstPTS < 0)
			{
				PES_packet();
				if (parsedPES.pts != -1)
				{
					firstPTS = parsedPES.pts;
					break;
				}
			}*/
    }
    initialPTSIndex = new MPEG2PES();
    initialPTSIndex.pts = firstPTS;
    initialPTSIndex.ptsPackBytePos = 0;
    ptsRollover = false;
    if (findDuration)
    {
      // Reset these so they need to get re-read
      parsedPES.pts = -1;
      parsedPES.dts = -1;
      parsedPES.dtsPackBytePos = 0;
      parsedPES.ptsPackBytePos = 0;
      durationIndex = new MPEG2PES();
      long fileLen = length();
      long skipFactor = 7;
      if (Sage.DBG) System.out.println("Mpeg2Reader has found the firstPTS, now determining the duration of the file...");
      while (true)
      {
        // If there's junk at the end of a file we need to keep moving back until we find a pack alignment at the end
        // that also has a valid PTS after it
        try
        {
          skipBytes(Math.max(0, fileLen - skipFactor*65536 - bitsDone/8));
          packAlign();
          while (pack())
          {
            if (parsedPES.pts > finalPTS && isValidPID(parsedPES.pid))
            {
              finalPTS = parsedPES.pts;
            }
          }
        }
        catch (EOFException e)
        {
        }
        if (finalPTS >= 0)
          break;
        seekToBeginning();
        skipFactor *= 2;
        long skip = fileLen - skipFactor*65536;
        if (skip <= 0)
          break;
      }
      durationIndex.pts = finalPTS;
      durationIndex.ptsPackBytePos = length();
      if (finalPTS < firstPTS)
      {
        if (Sage.DBG) System.out.println("PTS rollover detected in MPEG stream! firstPTS=" + firstPTS + " finalPTS=" + finalPTS);
        durationMsec = ((MAX_PTS - firstPTS) + finalPTS) / 90; // convert from 90kHz to msec
        ptsRollover = true;
      }
      else
        durationMsec = (finalPTS - firstPTS)/90; // convert from 90kHz to msec

      if (durationMsec < 0)
      {
        if (Sage.DBG) System.out.println("Invalid duration for MPEG file: " + durationMsec);
        durationIndex = null;
        durationMsec = -1;
        finalPTS = -1;
      }
    }
    if (Sage.DBG) System.out.println("Opened MPEG-2 " + (ts ? "TS" : "PS") + " file: " + mpegFile + " firstPTS=" + firstPTS + " durationMsec=" + durationMsec);
    // ain't there a better way to reset the stream?
    if (findFirstPTS || findDuration || !forcedPS)
    {
      seekToBeginning();
      parsedPES.pts = -1;
      parsedPES.dts = -1;
      parsedPES.dtsPackBytePos = 0;
      parsedPES.ptsPackBytePos = 0;
      parsedPS.bytePos = 0;
    }
  }

  public sage.media.bluray.BluRayStreamer getBluRaySource()
  {
    return bdp;
  }

  private void waitForPack() throws java.io.IOException
  {
    // This waits until there's at least a pack of data in the file before continuing
    int maxWaits = 100;
    while (!availableToRead(4096) && maxWaits-- > 0)
    {
      try{Thread.sleep(30);}catch(Exception e){}
    }
  }

  private long calculateByteSizeToReadBeforeSkip()
  {
    // This should be 3/4 of a second
    return ((playbackRate < 2 || (!useHighRateSkipping && playbackRate < 5)) && playbackRate >= 0) ? Long.MAX_VALUE : (streamBitrate * 3 / 4); // program_mux_rate is 50 bytes/second units
  }

  private long calculateDurReadBeforeSkip()
  {
    // This should be 3/4 of a second
    return ((playbackRate < 2 || (!useHighRateSkipping && playbackRate < 5)) && playbackRate >= 0) ? Long.MAX_VALUE : 1500;
  }

  // This is done as a time and then we use our seeking technique after that
  private long calculateSkipAmountForVariablePlaySeek()
  {
    if (!useHighRateSkipping && playbackRate < 6 && playbackRate >= 0)
    {
      return 0; // no skipping in this case
    }
    int multiplier = useHighRateSkipping ? 2 : 1;
    if (!alignIFrames)
    {
      // Let's assume it takes us 1/6 of a second to do a chunk playback
      // That means we're getting about 6fps (from testing), and this was one frame.
      // if we want to achieve a rate of playbackRate, then we need to skip playbackRate/2 chunks each time
      if (playbackRate > 0)
      {
        return multiplier * playbackRate * 1000 / 6;
      }
      else if (playbackRate > -5)
      {
        // If we don't skip at least 2000 then we're not jumping back enough for it to work right
        return -3000 * multiplier;
      }
      else
      {
        return multiplier * Math.min(-3000, playbackRate * 1000 / 6);
      }
    }
    else
    {
      // If we're aligning on I Frames, then we only send one iframe and then skip again, so we're not going to have to
      // skip as much since we're skipping more often
      if (playbackRate > 0)
      {
        if (playbackRate < 8)
          return 300*iframeSkipMultiplier4/4;
        return Math.max(500, 300 * playbackRate / 4) * iframeSkipMultiplier4/4 + 200;
      }
      else if (playbackRate > -5)
      {
        return -1000 * iframeSkipMultiplier4/4;
      }
      else
      {
        return Math.min(-1000, 500 * playbackRate / 8) * iframeSkipMultiplier4/4;
      }
    }
  }

  public void setPlaybackRate(int playRate)
  {
    if (playbackRate != playRate)
    {
      if (Sage.DBG) System.out.println("Mpeg2Reader changing playrate to " + playRate);
      iframeSkipMultiplier4 = bdp != null ? 16 : 4;
      lastRawBackSkipIFramePTS = 0;
      lastFastPlayRealStartTime = 0;
    }
    playbackRate = playRate;
  }

  public void setParseLevel(int x)
  {
    parseLevel = x;
  }

  private long calcOffsetPTS(long pts)
  {
    // We must check this continually for PTS rollover rather than relying on initial detection because it might rollover for an active file. But
    // we can't conditionalize it on the activeFile flag because the file may become inactive by the time we realize the PTS' rolled over.
    if (/*ptsRollover &&*/ firstPTS - pts > 100000)
    {
      if (!ptsRollover)
      {
        if (Sage.DBG) System.out.println("Detected PTS rollover in file! first=" + firstPTS + " curr=" + pts);
        ptsRollover = true;
      }
      return pts + (MAX_PTS - firstPTS);
    }
    else
      return pts - firstPTS;
  }

  public long getDurationMillis() { return durationMsec; }

  public long getFirstTimestampMillis() { return (firstPTS / 90); }

  public long getFirstPTS() { return firstPTS; }

  public long getLastIFramePTS() { return lastRawIFramePTS; }

  public long getLastRawVideoPTS() { return lastRawVideoPTS; }

  public boolean didPTSRollover() { return ptsRollover; }

  public boolean isIFrameAlignEnabled()
  {
    return alignIFrames;
  }

  public long getLastParsedTimeMillis()
  {
    if (parsedPES == null || parsedPES.pts <= 0)
      return 0;
    else
      return calcOffsetPTS(parsedPES.pts) / 90;
  }

  public long getLastParsedDTSMillis()
  {
    if (parsedPES == null || parsedPES.dts <= 0)
      return 0;
    else
      return calcOffsetPTS(parsedPES.dts) / 90;
  }

  public long getLastParsedTimePackBytePos() { return (parsedPES == null || parsedPES.pts <= 0) ? 0 : parsedPES.ptsPackBytePos; }

  public long getLastParsedDTSPackBytePos() { return (parsedPES == null || parsedPES.dts <= 0) ? 0 : parsedPES.dtsPackBytePos; }

  public boolean isLastTimestampVideo() { return (parsedPES != null && parsedPS != null && (parsedPES.stream_id == parsedPS.videoStreamID)); }

  public boolean usesCustomTimestamps() { return useCustomTimestamps; }

  public long getCustomTimestampDiff() { return currTimestampDiff; }

  public void close()
  {
    try
    {
      if (ins != null)
        ins.close();
      if (inxs != null)
        inxs.stopTranscode();
    }catch (IOException e){}
    ins = null;
    inxs = null;
  }

  public long length()
  {
    try
    {
      return (ins != null) ? ins.size() : inxs.getVirtualTranscodeSize();
    }catch (Exception e){return 0;}
  }

  public boolean lengthGreaterThan(long testLength)
  {
    if (ins != null)
    {
      if (lastCheckSize > testLength)
        return true;
      lastCheckSize = length();
      return (lastCheckSize > testLength);
    }
    else
      return length() > testLength;
  }

  public long availableToRead()
  {
    return Math.max(0, (ins != null) ? (length() - ins.position()) : inxs.getAvailableTranscodeBytes());
  }

  // This will return a number less than or equal to the argument; it avoids checking the actual file size if it knows
  // that it can read as much as what the argument is
  public long availableToRead2(long numBytes)
  {
    // Minimize the number of calls to length()
    if (ins != null)
    {
      long currPos = ins.position();
      if (lastCheckSize - currPos >= numBytes)
        return numBytes;
      lastCheckSize = length();
      return Math.min(numBytes, lastCheckSize - currPos);
    }
    else
      return Math.min(numBytes, inxs.getAvailableTranscodeBytes());
  }

  private long lastCheckSize = 0;
  public boolean availableToRead(long numBytes)
  {
    // Minimize the number of calls to length()
    if (ins != null)
    {
      long currPos = ins.position();
      if (lastCheckSize - currPos >= numBytes)
        return true;
      lastCheckSize = length();
      return (lastCheckSize - currPos) >= numBytes;
    }
    else
      return inxs.getAvailableTranscodeBytes() >= numBytes;
  }

  public long getReadPos() { return (ins != null) ? ins.position() : inxs.getVirtualReadPosition(); }

  public boolean canSkipOnNextRead()
  {
    return (targetSeekPTS >= 0 && numReseeksLeft > 0) || (calculateByteSizeToReadBeforeSkip() < Long.MAX_VALUE);
  }

  public int transfer(java.nio.channels.WritableByteChannel dest, int len, java.nio.ByteBuffer buf) throws IOException
  {
    if (!alignIFrames && msecStartForSkip != 0 && calculateDurReadBeforeSkip() < Math.abs(getLastParsedTimeMillis() - msecStartForSkip))//(calculateByteSizeToReadBeforeSkip() < bytesReadForThisSkip)
    {
      long skipNow = calculateSkipAmountForVariablePlaySeek();
      if (skipNow != 0)
        seek(getLastParsedTimeMillis() + skipNow, len);
    }
    else if (alignIFrames && lastRawIFramePTS != 0 && lastRawVideoPTS > lastRawIFramePTS && playbackRate != 1)
    {
      if (lastFastPlayRealStartTime == 0)
      {
        lastFastPlayRealStartTime = Sage.time();
        lastFastPlayPTSStartTime = lastRawIFramePTS;
      }
      long skipNow = calculateSkipAmountForVariablePlaySeek();
      if (skipNow != 0)
      {
        //System.out.println("About to do skip now and lastRawBackSkipIFramePTS=" + lastRawBackSkipIFramePTS  + " lastRawIFramePTS=" + lastRawIFramePTS + " lastRawVideoPTS=" + lastRawVideoPTS);
        if (lastRawBackSkipIFramePTS == lastRawIFramePTS && playbackRate < 0)
        {
          iframeSkipMultiplier4++;
          if (Sage.DBG) System.out.println("Detected smooth rew where we aren't skipping enough to match desired rate, increase x4 multiplier to:" + iframeSkipMultiplier4);
        }
        else
        {
          // Check to make sure we're achieving a high enough rate...we may need to skip more for really high bitrate content, or things w/ slower I/O
          long realTimeDiff = Sage.time() - lastFastPlayRealStartTime;
          if (realTimeDiff > 750)
          {
            // Give it enough time to get started properly
            long ptsTimeDiffMsec = (lastRawIFramePTS - lastFastPlayPTSStartTime)/90;
            float skipRatio = ((float)ptsTimeDiffMsec)/(realTimeDiff * playbackRate);
            //System.out.println("skipRatio=" + skipRatio + " multiplierx4=" + iframeSkipMultiplier4);
            if (skipRatio < 1.0f)
            {
              iframeSkipMultiplier4++;
              if (Sage.DBG) System.out.println("Detected smooth ff/rew where we aren't skipping enough to match desired rate, increase x4 multiplier to:" + iframeSkipMultiplier4 + " ratio=" + skipRatio);
              // We need to reset this or we'll keep trying to modify this again'
              lastFastPlayRealStartTime = Sage.time();
              lastFastPlayPTSStartTime = lastRawIFramePTS;
            }
            else if (skipRatio > 1.25f && iframeSkipMultiplier4 > 4)
            {
              iframeSkipMultiplier4--;
              if (Sage.DBG) System.out.println("Detected smooth ff/rew where we are skipping too much to match desired rate, decrease x4 multiplier to:" + iframeSkipMultiplier4 + " ratio=" + skipRatio);
              // We need to reset this or we'll keep trying to modify this again'
              lastFastPlayRealStartTime = Sage.time();
              lastFastPlayPTSStartTime = lastRawIFramePTS;
            }
          }
        }

        lastRawBackSkipIFramePTS = lastRawIFramePTS;
        seek(msecStartForSkip + skipNow, len);
      }
    }

    // Do a direct transfer if we don't need to parse anything out of this
    // Otherwise, read it into the provided ByteBuffer and then send it out on the specified Channel
    long currBytePos = bitsDone/8;
    if (dest == null || (targetSeekPTS >= 0 && numReseeksLeft > 0) || (alignIFrames && playbackRate != 1) || Math.abs(currBytePos - lastFullParseBytePos) > DIST_BETWEEN_FULL_PARSES)
    {
      if (ins != null)
      {
        ins.position(currBytePos);
      }
      buf.clear().limit(len);
      int rv = read(buf, len);
      buf.flip();
      if (debugPush) System.out.println("Finished reading the push data from disk; sending to network now...");
      if (dest != null)
      {
        int oldLimit = buf.limit();
        while (len > 0)
        {
          if (buf.remaining() > transferIncrement && transferIncrement > 0)
          {
            buf.limit(buf.position() + transferIncrement);
            len -= dest.write(buf);
            buf.limit(oldLimit);
          }
          else
            len -= dest.write(buf);
        }
      }
      return rv;
    }
    if (inxs != null && !inxs.isTranscoding() && !inxs.isTranscodeDone())
      inxs.startTranscode();
    // internal backup arrays checking
    if (len > peeks.length)
    {
      if (Sage.DBG) System.out.println("Adjusting peek buffer size to: " + len);
      byte[] newPeeks = new byte[len*2];
      System.arraycopy(peeks, 0, newPeeks, 0, numPeeks);
      peeks = newPeeks;
    }

    if (ins != null)
    {
      long llen = len;
      while (llen > 0) // try 16k blocks for the CIFS issue
      {
        llen -= ins.transferTo(llen, dest);
      }
    }
    else
    {
      inxs.sendTranscodeOutputToChannel(currBytePos, len, dest);
    }
    bitsDone += 8*len;
    //		bytesReadForThisSkip += len;
    return len;
  }
  public int read(java.nio.ByteBuffer buf, int len) throws IOException
  {
    if (inxs != null && !inxs.isTranscoding() && !inxs.isTranscodeDone())
      inxs.startTranscode();
    // Bounds checking
    len = Math.max(0, Math.min(buf.remaining(), len));
    // internal backup arrays checking
    if (len > peeks.length)
    {
      if (Sage.DBG) System.out.println("Adjusting peek buffer size to: " + len);
      byte[] newPeeks = new byte[len*2];
      System.arraycopy(peeks, 0, newPeeks, 0, numPeeks);
      peeks = newPeeks;
    }
    if (!alignIFrames && msecStartForSkip != 0 && calculateDurReadBeforeSkip() < Math.abs(getLastParsedTimeMillis() - msecStartForSkip))//(calculateByteSizeToReadBeforeSkip() < bytesReadForThisSkip)
    {
      long skipNow = calculateSkipAmountForVariablePlaySeek();
      if (skipNow != 0)
        seek(getLastParsedTimeMillis() + skipNow, len);
    }
    else if (alignIFrames && lastRawIFramePTS != 0 && lastRawVideoPTS > lastRawIFramePTS && (playbackRate > 2 || playbackRate < 0))
    {
      long skipNow = calculateSkipAmountForVariablePlaySeek();
      if (skipNow != 0)
        seek(msecStartForSkip + skipNow, len);
    }

    if (parseLevel > PARSE_LEVEL_NONE)
    {
      // Aligning on I frames makes seeking faster since we don't waste time sending
      // data the decoder is just going to discard anyways.
      boolean waitForIFrame = alignIFrames && /*(bytesReadForThisSkip == 0)*/(msecStartForSkip == 0) && !mpeg1 && (numReseeksLeft > 0 || playbackRate != 1);
      long currLength = ((waitForIFrame || (targetSeekPTS >= 0 && numReseeksLeft > 0)) ? length() : 0);
      int bytesSkippedForIFrameSync = 0;
      long iframePtsStartWait = -1;
      boolean forceAnotherCycle = false;
      long lastPAT = -1;
      int lastPESIDPos = -1;
      // NEW FAST PARSING IMPLEMENTATION - we don't necessarily need to get every packet so
      // if we don't have packet alignment on reads then that's OK. We're only parsing in order
      // to track the PTS information to enable seek, ff and rewind
      iframeloop:
        do
        {
          forceAnotherCycle = false;
          if (ins != null)
          {
            // If the possibility exists for us to skip while we're doing this and we won't be able
            // to fill this buffer with the remaining data from the current cell; we should skip to the
            // next cell to be sure the caller can align PTS's on cells properly. Otherwise we'd return
            // a buffer with data from two cells potentially.
            if (bdp != null && canSkipOnNextRead() && len > bdp.getBytesLeftInClip())
            {
              if (Sage.DBG) System.out.println("BluRay reseek at end of cell w/ not enough left for buffer; skipping to next cell");
              long skipper = bdp.getBytesLeftInClip();
              ins.skip(skipper);
              bitsDone += 8*skipper;
            }
            buf.clear().limit(len);
            while (buf.hasRemaining())
              ins.read(buf);
          }
          else
            inxs.readFullyTranscodedData(buf);
          try
          {
            if (ts)
            {
              int i = 0;
              if (tsPacketSize == 192)
              {
                // Do the alignment this way since it should always be exact
                long bytesRead = bitsDone / 8;
                i = (tsPacketSize - (int)(bytesRead % tsPacketSize)) + (tsPacketSize == 192 ? 4 : 0);
                i = i % tsPacketSize;
              }
              for (; i < len - tsPacketSize; ) // Whole packet is tsPacketSize bytes
              {
                // Align on a TS sync code
                if (buf.get(i) == 0x47)
                {
                  //System.out.println("Found TS sync code");
                  int tsStart = i;
                  int b1 = buf.get(i + 1);
                  // Find a payload start indicator, that's the only way there's PTS at the head, or if we have custom timestamps, then we need to check the PID as well
                  if ((waitForIFrame && lastRawVideoPTS != 0) || useCustomTimestamps || (b1 & 0x40) != 0)
                  {
                    int pid = ((b1 & 0x1F) << 8) | (buf.get(i + 2) & 0xFF);
                    if (pid == 0 && lastPAT == -1)
                    {
                      // We need to store this position because when we seek w/ IDR alignment we need to restart at the last PAT for it
                      // to resume quickly.
                      lastPAT = bitsDone/8 + i;
                    }
                    int adaptation_field_control = (buf.get(i + 3) & 0x30) >> 4;
                    if (adaptation_field_control == 2)
                      i += (tsPacketSize - 4);
                    else if (adaptation_field_control == 3)
                      i += Math.min(tsPacketSize - 6, 1 + (buf.get(i + 4) & 0xFF));
                    i += 4;
                    if (useCustomTimestamps && pid == 0x1FFF && adaptation_field_control == 1)
                    {
                      // This may be our null packet w/ our timing information
                      if (buf.get(i) == 'S' && buf.get(i + 1) == 'A' && buf.get(i + 2) == 'G' && buf.get(i + 3) == 'E')
                      {
                        if (targetSeekPTS >= 0 && numReseeksLeft > 0 && parsedPES.pts >= 0)
                        {
                          // Store 2 extra index points here so re-seeking is more accurate
                          MPEG2PES temp = indexData2;
                          indexData2 = indexData1;
                          indexData1 = temp;
                          indexData1.pts = parsedPES.pts;
                          indexData1.ptsPackBytePos = parsedPES.ptsPackBytePos;
                        }
                        // 2 bytes of top 16 bits for offset to next one
                        // 1 byte 0xFF marker
                        // 2 bytes of lower 16 bits for offset to next one
                        // 1 byte 0xFF marker
                        // 2 bytes of top 16 bits for timestamp
                        // 1 byte 0xFF marker
                        // 2 bytes of lower 16 bits for timestamp
                        int x = buf.getShort(i + 10) & 0xFFFF;
                        int y = buf.getShort(i + 13) & 0xFFFF;
                        parsedPES.pts = 90 * ((((long)x) << 16) | y);
                        parsedPES.ptsPackBytePos = bitsDone/8 + i;
                        if (msecStartForSkip == 0)
                          msecStartForSkip = calcOffsetPTS(parsedPES.pts)/90;
                        //System.out.println("CUSTOM TIMESTAMP=" + parsedPES.pts);
                        if ((calcOffsetPTS(parsedPES.pts) > streamBitratePtsCalcTime || parsedPS.program_mux_rate == 0) && (parsedPES.pts - firstPTS != 0))
                        {
                          streamBitrate = 90000 * bitsDone / (8*(parsedPES.pts - firstPTS));
                          streamBitratePtsCalcTime = calcOffsetPTS(parsedPES.pts);
                        }
                        if (targetSeekPTS >= 0 && numReseeksLeft > 0)
                        {
                          long seekDiff = calcOffsetPTS(targetSeekPTS) - calcOffsetPTS(parsedPES.pts);
                          long seekDiffAbs = Math.abs(seekDiff);
                          if (Sage.DBG) System.out.println("Seek target=" + targetSeekPTS/90000 + " actual=" + parsedPES.pts/90000 + " diff=" + seekDiff/90000);
                          if (seekDiffAbs > Sage.getLong("miniplayer/seek_diff_for_reseek", 2000) * 90 &&
                              (lengthGreaterThan(parsedPES.ptsPackBytePos + 4*len) || seekDiff < 0))
                          {
                            if (Sage.DBG) System.out.println("Seeking again to try to get a better position...");
                            numReseeksLeft--;
                            seek(calcOffsetPTS(targetSeekPTS)/90, len);
                            if (waitForIFrame)
                              iframePtsStartWait = -1;
                            lastPAT = -1;
                            forceAnotherCycle = true;
                            continue iframeloop;
                          }
                          else
                          {
                            targetSeekPTS = -1;
                            numReseeksLeft = 0;
                          }
                        }
                      }
                    }// Find a payload start indicator, that's the only way there's PTS at the head
                    else if ((b1 & 0x40) != 0 && (adaptation_field_control == 1 || adaptation_field_control == 3))
                    {
                      // Check for a start code next, it can be a system header or a pes packet
                      //System.out.println("Found payload start indicator i=" + i);
                      // We may need to dig into this TS packet to find the actual start of the PES packet...but only bother with this if we're looking
                      // for the next IFrame
                      while (waitForIFrame && (buf.get(i) != 0 || buf.get(i + 1) != 0 || buf.get(i + 2) != 1) && i < tsStart + tsPacketSize - 18)
                      {
                        i++;
                      }
                      if (buf.getShort(i) == 0 && buf.get(i + 2) == 1)
                      {
                        i += 3;
                        if (i + 15 >= len) break;
                        int stream_id = buf.get(i) & 0xFF;
                        //System.out.println("PES stream id=0x" + Integer.toString(stream_id, 16));
                        if (isValidPID(pid) && (stream_id == 0xBD || (stream_id >= 0xC0 && stream_id <= 0xEF) || (stream_id == 0xFD)))
                        {
                          //System.out.println("Audio/video substream found");
                          // Valid audio or video stream, check for the pts flag
                          if ((buf.get(i + 4) & 0x80) == 0x80)
                          {
                            lastFullParseBytePos = bitsDone/8 + i;
                            if (!useCustomTimestamps && targetSeekPTS >= 0 && numReseeksLeft > 0 && parsedPES.pts >= 0)
                            {
                              // Store 2 extra index points here so re-seeking is more accurate
                              MPEG2PES temp = indexData2;
                              indexData2 = indexData1;
                              indexData1 = temp;
                              indexData1.pts = parsedPES.pts;
                              indexData1.ptsPackBytePos = parsedPES.ptsPackBytePos;
                            }

                            // PTS found, extract it
                            long pts = (((long)(buf.get(i + 6) & 0x0E)) << 29) |
                                ((buf.get(i + 7) & 0xFF) << 22) |
                                ((buf.get(i + 8) & 0xFE) << 14) |
                                ((buf.get(i + 9) & 0xFF) << 7) |
                                ((buf.get(i + 10) & 0xFE) >> 1);
                            //System.out.println("streamid=0x" + Integer.toString(stream_id, 16) + " PTS90k=" +pts);
                            if (bdp != null)
                              pts += bdp.getClipPtsOffset(bdp.getCurrClipIndex())*2;
                            if (!useCustomTimestamps)
                            {
                              parsedPES.pts = pts + transcodePTSOffset;
                              parsedPES.ptsPackBytePos = bitsDone/8 + i;
                            }
                            if (pid == videoPID)
                              lastRawVideoPTS = pts;
                            if (iframePtsStartWait == -1 && waitForIFrame && pid == videoPID)
                              iframePtsStartWait = pts;
                            if (!useCustomTimestamps && msecStartForSkip == 0)
                              msecStartForSkip = calcOffsetPTS(parsedPES.pts)/90;
                            if (!useCustomTimestamps && (calcOffsetPTS(parsedPES.pts) > streamBitratePtsCalcTime || parsedPS.program_mux_rate == 0) && (calcOffsetPTS(parsedPES.pts) > 100000))
                            {
                              streamBitrate = 90000 * (bitsDone + 8*i) / (8*calcOffsetPTS(parsedPES.pts));
                              streamBitratePtsCalcTime = calcOffsetPTS(parsedPES.pts);
                            }
                            if (!useCustomTimestamps && targetSeekPTS >= 0 && numReseeksLeft > 0)
                            {
                              long seekDiff = calcOffsetPTS(targetSeekPTS) - calcOffsetPTS(parsedPES.pts);
                              long seekDiffAbs = Math.abs(seekDiff);
                              if (Sage.DBG) System.out.println("Seek target=" + targetSeekPTS/90000 + " actual=" + parsedPES.pts/90000 + " diff=" + seekDiff/90000);
                              if (seekDiffAbs > Sage.getLong("miniplayer/seek_diff_for_reseek", 2000) * 90 &&
                                  (lengthGreaterThan(parsedPES.ptsPackBytePos + 4*len) || seekDiff < 0))
                              {
                                if (Sage.DBG) System.out.println("Seeking again to try to get a better position...");
                                numReseeksLeft--;
                                seek(calcOffsetPTS(targetSeekPTS)/90, len);
                                if (waitForIFrame)
                                  iframePtsStartWait = -1;
                                lastPAT = -1;
                                forceAnotherCycle = true;
                                continue iframeloop;
                              }
                              else
                              {
                                targetSeekPTS = -1;
                                numReseeksLeft = 0;
                              }
                            }

                            // We did a seek and need to find the I Frame (actually, this is only for H.264, we need an IDR)
                            if (waitForIFrame && lastPAT != -1 && pid == videoPID)
                            {
                              if (h264Video)
                              {
                                long testData = 0;
                                for (int j = tsStart + 4; j < tsStart + tsPacketSize; j++)
                                {
                                  testData = (testData << 8) | (buf.get(j) & 0xFF);
                                  if ((testData & 0x00FFFFFFFFFFFFFFL) == 0x01091000)
                                  {
                                    // IDR start code found...good packet :)
                                    waitForIFrame = false;
                                    bytesSkippedForIFrameSync = tsStart;
                                    ins.position(lastPAT);
                                    bitsDone = lastPAT*8;
                                    numReseeksLeft = 0;
                                    forceAnotherCycle = true;
                                    if (parsedPES.pts != -1)
                                      msecStartForSkip = calcOffsetPTS(parsedPES.pts)/90;
                                    lastRawIFramePTS = pts;
                                    continue iframeloop;
                                  }
                                }
                              }
                              else if (mpeg2Video)
                              {
                                int pesDataOffset = (buf.get(i + 5) & 0xFF) + 6;
                                if (buf.getShort(i + pesDataOffset) == 0 /*&& buf.get(i + pesDataOffset + 1) == 0*/ && buf.get(i + pesDataOffset + 2) == 1 &&
                                    (buf.get(i + pesDataOffset + 3) & 0xFF) == 0xB3)
                                {
                                  // Found the I Frame
                                  waitForIFrame = false;
                                  bytesSkippedForIFrameSync = tsStart;
                                  ins.position(lastPAT);
                                  bitsDone = lastPAT*8;
                                  numReseeksLeft = 0;
                                  forceAnotherCycle = true;
                                  lastRawIFramePTS = pts;
                                  if (parsedPES.pts != -1)
                                    msecStartForSkip = calcOffsetPTS(parsedPES.pts)/90;
                                  continue iframeloop;
                                }
                              }
                              if (pts - iframePtsStartWait > 450000)
                              {
                                // If we've gone 5 seconds without an I Frame then give up on our trying to align
                                // on them since something is obviously wrong with what we're doing
                                if (Sage.DBG) System.out.println("Disabling I-Frame alignment in pusher since it's skipping too much pts=" +
                                    pts + " startWait=" + iframePtsStartWait);
                                alignIFrames = false;
                                waitForIFrame = false;
                              }
                            }
                            if (useCustomTimestamps)
                              currTimestampDiff = parsedPES.pts - pts;

                            // Also extract the DTS if it's there
                            if ((buf.get(i + 4) & 0x40) == 0x40)
                            {
                              long dts = (((long)(buf.get(i + 11) & 0x0E)) << 29) |
                                  ((buf.get(i + 12) & 0xFF) << 22) |
                                  ((buf.get(i + 13) & 0xFE) << 14) |
                                  ((buf.get(i + 14) & 0xFF) << 7) |
                                  ((buf.get(i + 15) & 0xFE) >> 1);
                              if (bdp != null)
                                dts += bdp.getClipPtsOffset(bdp.getCurrClipIndex())*2;
                              parsedPES.dts = dts + transcodePTSOffset;
                              parsedPES.dtsPackBytePos = bitsDone/8 + i;
                            }
                            //System.out.println("pts=" + pts);
                          }
                        }
                      }
                    }
                  }
                  i = tsPacketSize + tsStart;
                }
                else
                {
                  i++;
                  //System.out.println("PACKET NOT ALIGNED");
                }
              }
            }
            else
            {
              for (int i = 0; i < len - 27; ) // PTS is 27 bytes from pack start
              {
                // Align on a pack start code
                int packStart = i;
                if (buf.getShort(i) == 0 && /*buf[i + 1] == 0 &&*/ buf.get(i + 2) == 1)
                {
                  //System.out.println("Start code found 0x" + Integer.toString(buf[i + 3] & 0xFF, 16));
                  if ((buf.get(i + 3) & 0xFF) == 0xBA)
                  {
                    //System.out.println("Pack start code");
                    parsedPS.bytePos = (bitsDone/8) + i;
                    if (mpeg1)
                    {
                      // Skip over the whole pack header
                      i += 12;
                    }
                    else
                    {
                      // Skip over the data in the pack header and land on the pack_stuffing_length byte (it uses the lower 3 bits)
                      i += 13;
                      // Check for any stuffing bytes in the pack header and skip them
                      i += (1 + (buf.get(i) & 0x7));
                    }
                    if (i + 13 >= len) break;
                    // Check for a start code next, it can be a system header or a pes packet
                    while (buf.getShort(i) == 0 && /*buf[i + 1] == 0 &&*/ buf.get(i + 2) == 1 && (buf.get(i + 3) & 0xFF) != 0xBA)
                    {
                      //System.out.println("Start code after pack header");
                      i += 3;
                      if ((buf.get(i) & 0xFF) == 0xBB)
                      {
                        //System.out.println("System header found");
                        // system header, skip it
                        i += 3 + (((buf.get(i + 1) & 0xFF) << 8) | (buf.get(i + 2) & 0xFF));
                        // Check for the start code next
                        if (i + 15 >= len) break;
                        if (buf.getShort(i) != 0 ||/* buf[i + 1] != 0 ||*/ buf.get(i + 2) != 1)
                          continue;
                        else
                          i += 3;
                      }
                      if (i + 15 >= len) break;
                      lastPESIDPos = i;
                      int stream_id = buf.get(i) & 0xFF;
                      int packet_length = ((buf.get(i + 1) & 0xFF) << 8) | (buf.get(i + 2) & 0xFF);
                      //System.out.println("PES stream id=0x" + Integer.toString(stream_id, 16) + " length=" + packet_length);
                      if (stream_id == 0xBD || (stream_id >= 0xC0 && stream_id <= 0xEF) || (vc1RemapMode && stream_id == 0xFD))
                      {
                        if (mpeg1)
                        {
                          while ((buf.get(i + 3) & 0xFF) == 0xFF && i + 15 < len)
                          {
                            i++;
                          }
                          if (i + 17 >= len) break;
                          if ((buf.get(i + 3) & 0x40) == 0x40)
                          {
                            i += 2;
                          }
                          i -= 3; // so PTS/DTS lines up compared to MPEG2
                        }
                        //System.out.println("Audio/video substream found");
                        // Valid audio or video stream, check for the pts flag
                        if ((!mpeg1 && (buf.get(i + 4) & 0x80) == 0x80) || (mpeg1 && (buf.get(i + 6) & 0xE0) == 0x20))
                        {
                          lastFullParseBytePos = bitsDone/8 + i;
                          if (targetSeekPTS >= 0 && numReseeksLeft > 0 && parsedPES.pts >= 0)
                          {
                            // Store 2 extra index points here so re-seeking is more accurate
                            MPEG2PES temp = indexData2;
                            indexData2 = indexData1;
                            indexData1 = temp;
                            indexData1.pts = parsedPES.pts;
                            indexData1.ptsPackBytePos = parsedPES.ptsPackBytePos;
                          }

                          // PTS found, extract it
                          long pts = (((long)(buf.get(i + 6) & 0x0E)) << 29) |
                              ((buf.get(i + 7) & 0xFF) << 22) |
                              ((buf.get(i + 8) & 0xFE) << 14) |
                              ((buf.get(i + 9) & 0xFF) << 7) |
                              ((buf.get(i + 10) & 0xFE) >> 1);
                          parsedPES.pts = pts + transcodePTSOffset;
                          parsedPES.ptsPackBytePos = bitsDone/8 + i;
                          if (iframePtsStartWait == -1 && waitForIFrame && (stream_id & 0x20) != 0)
                            iframePtsStartWait = pts;
                          if (msecStartForSkip == 0)
                            msecStartForSkip = calcOffsetPTS(parsedPES.pts)/90;
                          if ((stream_id & 0x20) != 0)
                            lastRawVideoPTS = pts;
                          //System.out.println("pts=" + pts);
                          if ((calcOffsetPTS(parsedPES.pts) > streamBitratePtsCalcTime || parsedPS.program_mux_rate == 0) && (calcOffsetPTS(parsedPES.pts) > 100000))
                          {
                            streamBitrate = 90000 * (bitsDone + 8*i) / (8*calcOffsetPTS(parsedPES.pts));
                            streamBitratePtsCalcTime = calcOffsetPTS(parsedPES.pts);
                          }
                          if (targetSeekPTS >= 0 && numReseeksLeft > 0)
                          {
                            long seekDiff = calcOffsetPTS(targetSeekPTS) - calcOffsetPTS(parsedPES.pts);
                            long seekDiffAbs = Math.abs(seekDiff);
                            if (Sage.DBG) System.out.println("Seek target=" + targetSeekPTS/90000 + " actual=" + parsedPES.pts/90000 + " diff=" + seekDiff/90000);
                            if (seekDiffAbs > Sage.getLong("miniplayer/seek_diff_for_reseek", 2000) * 90 &&
                                (lengthGreaterThan(parsedPES.ptsPackBytePos + 4*len) || seekDiff < 0))
                            {
                              if (Sage.DBG) System.out.println("Seeking again to try to get a better position...");
                              numReseeksLeft--;
                              seek(calcOffsetPTS(targetSeekPTS)/90, len);
                              if (waitForIFrame)
                                iframePtsStartWait = -1;
                              forceAnotherCycle = true;
                              continue iframeloop;
                            }
                            else
                            {
                              targetSeekPTS = -1;
                              numReseeksLeft = 0;
                            }
                          }

                          // We did a seek and need to find the I Frame
                          if (waitForIFrame)
                          {
                            if ((stream_id & 0x20) != 0)
                            {
                              int pesDataOffset = (buf.get(i + 5) & 0xFF) + 6;
                              if (buf.getShort(i + pesDataOffset) == 0 /*&& buf.get(i + pesDataOffset + 1) == 0*/ && buf.get(i + pesDataOffset + 2) == 1 &&
                                  (buf.get(i + pesDataOffset + 3) & 0xFF) == 0xB3)
                              {
                                // Found the I Frame
                                waitForIFrame = false;
                                bytesSkippedForIFrameSync = i;
                                ins.position(bitsDone/8 + packStart);
                                bitsDone += packStart*8;
                                numReseeksLeft = 0;
                                forceAnotherCycle = true;
                                lastRawIFramePTS = pts;
                                //System.out.println("IFrame Found! pts=" + lastRawIFramePTS);
                                msecStartForSkip = calcOffsetPTS(parsedPES.pts)/90;
                                continue iframeloop;
                              }
                              else if (vc1RemapMode && buf.getShort(i + pesDataOffset) == 0 /*&& buf.get(i + pesDataOffset + 1) == 0*/ && buf.get(i + pesDataOffset + 2) == 1 &&
                                  (buf.get(i + pesDataOffset + 3) & 0xFF) == 0x0F)
                              {
                                // Found the I Frame
                                waitForIFrame = false;
                                bytesSkippedForIFrameSync = i;
                              }
                            }
                            if (iframePtsStartWait > 0 && pts - iframePtsStartWait > 450000)
                            {
                              // If we've gone 5 seconds without an I Frame then give up on our trying to align
                              // on them since something is obviously wrong with what we're doing
                              if (Sage.DBG) System.out.println("Disabling I-Frame alignment in pusher since it's skipping too much pts=" +
                                  pts + " startWait=" + iframePtsStartWait);
                              alignIFrames = false;
                              waitForIFrame = false;
                            }
                          }

                          // Also extract the DTS if it's there
                          if ((!mpeg1 && (buf.get(i + 4) & 0x40) == 0x40) || (mpeg1 && (buf.get(i + 6) & 0xF0) == 0x30))
                          {
                            long dts = (((long)(buf.get(i + 11) & 0x0E)) << 29) |
                                ((buf.get(i + 12) & 0xFF) << 22) |
                                ((buf.get(i + 13) & 0xFE) << 14) |
                                ((buf.get(i + 14) & 0xFF) << 7) |
                                ((buf.get(i + 15) & 0xFE) >> 1);
                            parsedPES.dts = dts + transcodePTSOffset;
                            parsedPES.dtsPackBytePos = bitsDone/8 + i;
                          }
                        }
                        if (vc1RemapMode && (buf.get(i + 4) & 0x1) == 0x1 && stream_id == 0xFD)
                        {
                          int extensionOffset = 6 + (((buf.get(i + 4) & 0x40) == 0x40) ? 5 : 0) + (((buf.get(i + 4) & 0x80) == 0x80) ? 5 : 0);
                          boolean pesPrivateDataFlag = (buf.get(i + extensionOffset) & 0x80) == 0x80;
                          //boolean packHeaderFieldFlag = (buf[i + extensionOffset] & 0x40) == 0x40;
                          boolean programPacketSequenceCounterFlag = (buf.get(i + extensionOffset) & 0x20) == 0x20;
                          boolean pstdFlag = (buf.get(i + extensionOffset) & 0x10) == 0x10;
                          boolean pesExtFlag2 = (buf.get(i + extensionOffset) & 0x1) == 0x1;
                          if (pesPrivateDataFlag)
                            extensionOffset += 16;
                          if (programPacketSequenceCounterFlag)
                            extensionOffset += 2;
                          if (pstdFlag)
                            extensionOffset += 2;
                          if (pesExtFlag2)
                          {
                            if ((buf.get(i + extensionOffset + 1) & 0xFF) != 0x81)
                            {
                              if (Sage.DBG) System.out.println("ERROR Unexpected marker code of: 0x" + Integer.toHexString(buf.get(i + extensionOffset + 1)));
                            }
                            else if (buf.get(i + extensionOffset + 2) == 0x55)
                            {
                              buf.put(i, (byte)0xE0);
                            }
                          }
                        }
                      }
                      i += packet_length + 3;
                      if (i + 13 >= len)
                        break;
                    }
                  }
                  else
                    i++;
                }
                else
                {
                  i++;
                  //System.out.println("PACKET NOT ALIGNED");
                }
              }
            }
          }
          catch (RuntimeException re)
          {
            // Catch these in case there's some kind of mistake in our MPEG parsing; then we won't terminate a media player
            // connection due to that and will just continue parsing the stream beyond where we currently are.
            if (Sage.DBG) System.out.println("ERROR (but continuing) in MPEG stream parsing of:" + re);
            if (Sage.DBG) re.printStackTrace();
          }
          bitsDone += 8*len;
          // If we're doing a re-seek, then don't send out the data until we're satisfied w/ our position!
        } while ((forceAnotherCycle || waitForIFrame || (targetSeekPTS >= 0 && numReseeksLeft > 0)) && (bitsDone/8 + len < currLength)); // don't read off the end searching for an I-frame
      //				if (bytesSkippedForIFrameSync > 0)
      //				{
      //					System.arraycopy(buf, off + bytesSkippedForIFrameSync, buf, 0, len - bytesSkippedForIFrameSync);
      //				}
      //			bytesReadForThisSkip += len;// - bytesSkippedForIFrameSync;
      //				return len - bytesSkippedForIFrameSync;

      // If we're doing smooth ff/rew of a program stream that doesn't have 2k consistent pack sizes, then we need to change the last pack
      // to be padding and adjust its length so it terminates at the end of the buffer
      if (!ts && (!parsedPS.constantPackSize || parsedPS.packSize != 2048) && alignIFrames && lastRawIFramePTS != 0 &&
          lastRawVideoPTS > lastRawIFramePTS && playbackRate != 1 && calculateSkipAmountForVariablePlaySeek() != 0 && lastPESIDPos >= 0)
      {
        buf.put(lastPESIDPos, (byte)(PADDING_STREAM & 0xFF));
        int padLength = len - (lastPESIDPos + 3);
        buf.putShort(lastPESIDPos + 1, (short)(padLength & 0xFFFF));
      }
      return len;
    }
    else
    {
      if (ins != null)
      {
        buf.clear().limit(len);
        while (buf.hasRemaining())
          ins.read(buf);
      }
      else
        inxs.readFullyTranscodedData(buf);
      bitsDone += 8*len;
      return len;
    }
  }

  long estimateBytePosForPTS(long targetPts) throws IOException
  {
    if (targetPts <= 0) return 0;
    long rv = 0;
    long fileLength = length();
    if (Sage.DBG) System.out.println("MPEG2 seek targetPts=" + targetPts + " length=" + fileLength + " durationMsec=" + durationMsec +
        " parsedPTS=" + parsedPES.pts + " lastPos=" + parsedPES.ptsPackBytePos + " firstPTS=" + firstPTS + " mux_rate=" + parsedPS.program_mux_rate +
        " estimBitrate=" + streamBitrate);

    // To estimate a position we find the two points nearest to it if possible and then interpolate
    // between them. If we can only find one point; then we have to use the rate estimate to interpolate
    // past it.
    MPEG2PES lowerBound = initialPTSIndex;
    MPEG2PES upperBound = durationIndex; // will be null if not set

    // We had an issue where the upper and lower bounds had objects with the same PTS. So check to make sure we don't do that.
    if (parsedPES.pts > 0 && (lowerBound == null || lowerBound.pts != parsedPES.pts) && (upperBound == null || upperBound.pts != parsedPES.pts))
    {
      if (calcOffsetPTS(targetPts) >= calcOffsetPTS(parsedPES.pts))
        lowerBound = parsedPES;
      else
        upperBound = parsedPES;
    }
    if (indexData1.pts > 0 && (lowerBound == null || lowerBound.pts != indexData1.pts) && (upperBound == null || upperBound.pts != indexData1.pts))
    {
      if (calcOffsetPTS(targetPts) >= calcOffsetPTS(indexData1.pts))
      {
        if (calcOffsetPTS(indexData1.pts) > calcOffsetPTS(lowerBound.pts))
          lowerBound = indexData1;
      }
      else
      {
        if (upperBound == null || calcOffsetPTS(upperBound.pts) > calcOffsetPTS(indexData1.pts))
          upperBound = indexData1;
      }
    }
    if (indexData2.pts > 0 && (lowerBound == null || lowerBound.pts != indexData2.pts) && (upperBound == null || upperBound.pts != indexData2.pts))
    {
      if (calcOffsetPTS(targetPts) >= calcOffsetPTS(indexData2.pts))
      {
        if (calcOffsetPTS(indexData2.pts) > calcOffsetPTS(lowerBound.pts))
          lowerBound = indexData2;
      }
      else
      {
        if (upperBound == null || calcOffsetPTS(upperBound.pts) > calcOffsetPTS(indexData2.pts))
          upperBound = indexData2;
      }
    }

    // Now we're established lower and (maybe) upper bounds
    if (upperBound == null)
    {
      // Do a rate estimate based off the lower bound
      if (targetPts == lowerBound.pts)
        rv = lowerBound.ptsPackBytePos;
      else
      {
        // add the ratebound guess offset from the recent; but redo
        // the ratebound so it uses a guess from the data in the file since
        // the header is usually wrong
        if (calcOffsetPTS(lowerBound.pts) > calcOffsetPTS(firstPTS))
        {
          long guessRate = lowerBound.ptsPackBytePos/(calcOffsetPTS(lowerBound.pts) - calcOffsetPTS(firstPTS));
          rv = lowerBound.ptsPackBytePos + (calcOffsetPTS(targetPts) - calcOffsetPTS(lowerBound.pts))*guessRate;
        }
        else if (parsedPS.program_mux_rate > 0)
          rv = (calcOffsetPTS(targetPts) - calcOffsetPTS(firstPTS)) * parsedPS.program_mux_rate * 5 / 9000; // program_mux_rate is 50 bytes/sec, pts is 90kHz
        else // just estimate 5Mbps
          rv = (calcOffsetPTS(targetPts) - calcOffsetPTS(firstPTS))*650000 / 90000;
      }
    }
    else
    {
      rv = lowerBound.ptsPackBytePos + Math.round(((calcOffsetPTS(targetPts) - calcOffsetPTS(lowerBound.pts)) *
          (double)(upperBound.ptsPackBytePos - lowerBound.ptsPackBytePos)) /
          (calcOffsetPTS(upperBound.pts) - calcOffsetPTS(lowerBound.pts)));
    }

    /*		if (parsedPES.pts > 0)
		{
			long recentTime = parsedPES.pts;
			if (Sage.DBG) System.out.println("MPEG2 seek recentTime=" + recentTime);
			if (targetPts == recentTime)
				rv = parsedPES.ptsPackBytePos;
			else if (targetPts > recentTime)
			{
				if (durationMsec > 0)
				{
					// Average between the recent time and the end time
					long byteDiff = fileLength - parsedPES.ptsPackBytePos;
					long timeDiff = durationMsec*90 - (recentTime - firstPTS);
					rv = Math.round((((double)(targetPts - recentTime)) * byteDiff) / timeDiff);
					rv += parsedPES.ptsPackBytePos;
				}
				else
				{
					// add the ratebound guess offset from the recent; but redo
					// the ratebound so it uses a guess from the data in the file since
					// the header is usually wrong
					if (recentTime > 0 && recentTime > firstPTS)
					{
						long guessRate = parsedPES.ptsPackBytePos/(recentTime - firstPTS);
						rv = parsedPES.ptsPackBytePos + (targetPts - recentTime)*guessRate;
					}
					else
						rv = (targetPts - firstPTS) * parsedPS.program_mux_rate * 5 / 9000; // program_mux_rate is 50 bytes/sec, pts is 90kHz
				}
			}
			else // if (rtTime < recentTime)
			{
				// Average between the recent time and the start time (0)
				rv = Math.round((((double)(targetPts - firstPTS)) * parsedPES.ptsPackBytePos) / (recentTime - firstPTS));
			}
		}
		else if (durationMsec > 0)
		{
			rv = Math.round((((double)targetPts - firstPTS) * fileLength) / (durationMsec * 90));
		}
		else if (parsedPS.program_mux_rate > 0)
			rv = (targetPts - firstPTS)* parsedPS.program_mux_rate * 5 / 9000; // program_mux_rate is 50 bytes/sec, pts is 90kHz
		else // just estimate 5Mbps
			rv = (targetPts - firstPTS)*650000 / 90000;
     */
    // If we're transcoding then don't use the file size as the true limit
    if (streamTranscodeMode == null)
      rv = Math.min(rv, fileLength);
    rv = Math.max(0, rv); // avoid negative file offsets
    return rv;
  }

  public void seekToBeginning() throws IOException
  {
    if (Sage.DBG) System.out.println("Mpeg2Reader seeking to pos=" + 0);
    if (ins != null)
      ins.position(0);
    else
      inxs.seekToPosition(0);
    bitsDone = 0;
    rem = -1;
    numPeeks = 0;
    //bytesReadForThisSkip = 0;
    msecStartForSkip = lastRawIFramePTS = lastRawVideoPTS = 0;
    lastFullParseBytePos = -(DIST_BETWEEN_FULL_PARSES + 1);
  }

  public void seek(long seekTime) throws IOException
  {
    seekTime = Math.max(0, seekTime);
    if (seekTime == 0)
    {
      seekToBeginning(); // this avoids reloading the transcoder
      if (inxs != null)
      {
        // Don't forget to reset the transcode offset if we seek back to the beginning
        transcodePTSOffset = seekTime*90;
        // The PTS/DTS only resets when using the transcoder
        // NOTE: NARFLEX - I'm pretty sure the pts/dts here should be set to the seekTime
        // initially since it'll have that offset applied to it after we eat some data.
        // We can fix this after 6.1 is released....we did so let's see how it goes. :)
        parsedPES.dtsPackBytePos = parsedPES.ptsPackBytePos = 0;
        parsedPES.dts = parsedPES.pts = seekTime*90;
      }
    }
    else
    {
      numReseeksLeft = Sage.getInt("miniplayer/num_reseek_attempts", 3);
      if (ins != null && streamTranscodeMode == null)
      {
        targetSeekPTS = seekTime*90 + firstPTS;
        if (/*ptsRollover &&*/ targetSeekPTS > MAX_PTS)
          targetSeekPTS = targetSeekPTS - MAX_PTS;
      }
      seek(seekTime, 0);
    }
  }
  private void seek(long seekTime, int minReadRequired) throws IOException
  {
    seekTime = Math.max(0, seekTime);
    if (ins != null)
    {
      long targetPTS = seekTime*90 + firstPTS;
      if (/*ptsRollover &&*/ targetPTS > MAX_PTS)
        targetPTS = targetPTS - MAX_PTS;
      long targetPos = estimateBytePosForPTS(targetPTS);
      if (/*parsedPS.constantPackSize &&*/ parsedPS.packSize > 0)
      {
        // Align on a pack boundary
        targetPos -= targetPos % parsedPS.packSize;
      }
      if (minReadRequired > 0)
        targetPos = Math.min(length() - minReadRequired, Math.max(0, targetPos));
      if (Sage.DBG) System.out.println("Mpeg2Reader seeking to pos=" + targetPos + " time=" + Sage.durFormatMillis(seekTime));
      if (streamTranscodeMode != null)
        transcodePTSOffset = seekTime*90;
      ins.position(targetPos);
      bitsDone = targetPos*8;
    }
    else
    {
      inxs.seekToTime(seekTime);
      // NOTE: using 0 for bitsDone isn't what I was doing before; so watch out for this!!!
      bitsDone = 0;
      transcodePTSOffset = seekTime*90;
      // The PTS/DTS only resets when using the transcoder
      // NOTE: NARFLEX - I'm pretty sure the pts/dts here should be set to the seekTime
      // initially since it'll have that offset applied to it after we eat some data.
      // We can fix this after 6.1 is released....we did so let's see how it goes. :)
      parsedPES.dtsPackBytePos = parsedPES.ptsPackBytePos = 0;
      parsedPES.dts = parsedPES.pts = seekTime*90;
    }
    rem = -1;
    numPeeks = 0;
    //		bytesReadForThisSkip = 0;
    msecStartForSkip = lastRawIFramePTS = lastRawVideoPTS = 0;
    lastFullParseBytePos = -(DIST_BETWEEN_FULL_PARSES + 1);
  }

  public boolean packAlign() throws IOException
  {
    if (ts)
    {
      while (true)
      {
        if (readByte() != 0x47)
        {
          if (!availableToRead(tsPacketSize))
            return false;
        }
        else
        {
          break;
        }
      }
      putbackBits(0x47, 8);
      bitsDone -= 8;
    }
    else
    {
      while (true)
      {
        if (nextbits(32) != PACK_START_CODE)
        {
          readByte();
          if (!availableToRead(4))
            return false;
        }
        else
        {
          break;
        }
        //System.out.println("NOPACKALIGN!");
      }
    }
    return true;
  }

  private void notifyPackSize()
  {
    int currPackSize = (int) ((bitsDone/8) - parsedPS.bytePos);
    if (ts)
    {
      tsSynced = (currPackSize == tsPacketSize);
      parsedPS.packSize = currPackSize;
    }
    else
    {
      if (parsedPS.constantPackSize)
      {
        if (parsedPS.packSize == 0)
        {
          if (Sage.DBG) System.out.println("MPEG2 file pack size=" + currPackSize);
          parsedPS.packSize = currPackSize;
        }
        else if (parsedPS.packSize != currPackSize)
        {
          if (Sage.DBG) System.out.println("MPEG2 file has inconsistent pack sizes prev=" + parsedPS.packSize + " curr=" + currPackSize);
          parsedPS.packSize = Math.max(parsedPS.packSize, currPackSize);
          parsedPS.constantPackSize = false;
        }
      }
      else
        parsedPS.packSize = currPackSize;
    }
  }

  boolean pack() throws IOException
  {
    if (!packAlign()) // We're dependent upon being aligned in the parser so make sure we enforce that!
      return false;
    if (log) System.out.print("PACK ");
    // Check to make sure we've got enough data to complete the pack. This is easy for constant pack size.
    if (/*parsedPS.constantPackSize &&*/ parsedPS.packSize > 0 && !availableToRead(parsedPS.packSize))
      return false;
    if (ts)
    {
      if (!availableToRead(tsPacketSize)) return false;
      transport_packet();
    }
    else
    {
      // 65 is about how much is needed to get to the PES packet length (but it gets farther than that w/ 65). But there's
      // really no lower bound on this considering there may be unlimited (almost) stream_ids in the system_header
      if (!availableToRead(65)) return false;
      pack_header();
      while (true)
      {
        if (availableToRead(4))
        {
          if (nextbits(24) == PACKET_START_CODE_PREFIX &&
              nextbits(32) != PACK_START_CODE &&
              nextbits(32) != MPEG_PROGRAM_END_CODE)
          {
            PES_packet();
          }
          else
          {
            notifyPackSize();
            break;
          }
        }
        else
          break;
      }
    }
    return true;
  }

  void transport_packet() throws IOException
  {
    int syncByte;
    do
    {
      syncByte = readByte();
      if (syncByte == 0x47)
      {
        parsedPS.bytePos = (bitsDone - 8)/8;
      }
    } while (syncByte != 0x47 && availableToRead(tsPacketSize));
    //		boolean transport_error_indicator = readFlag();
    int b1 = readByte();
    boolean payload_unit_start_indicator = (b1 & 0x40) != 0;
    //		boolean transport_priority = readFlag();
    int b2 = readByte();
    parsedPES.pid = ((b1 & 0x1F) << 8) | b2;
    //		if (log) System.out.print(" PID=" + pid);
    //		int transport_scrambling_control = (int) readBits(2);
    int adaptation_field_control = (readByte() & 0x30) >> 4;//(int) readBits(2);
    //		int continuity_counter = (int) readBits(4);
    if (adaptation_field_control == 2 || adaptation_field_control == 3)
    {
      adaptation_field(adaptation_field_control);
    }
    if (adaptation_field_control == 1 && parsedPES.pid == 0x1FFF)
    {
      // This may be our null packet w/ our timing information
      if (readByte() == 'S' && readByte() == 'A' && readByte() == 'G' && readByte() == 'E')
      {
        if (Sage.DBG && !useCustomTimestamps) System.out.println("Found custom SAGE timestamps in TS file");
        useCustomTimestamps = true;
        // 2 bytes of top 16 bits for offset to next one
        // 1 byte 0xFF marker
        // 2 bytes of lower 16 bits for offset to next one
        // 1 byte 0xFF marker
        // 2 bytes of top 16 bits for timestamp
        // 1 byte 0xFF marker
        // 2 bytes of lower 16 bits for timestamp
        skipBytes(6);
        int x = readShort();
        readByte();
        int y = readShort();
        parsedPES.pts = 90 * ((((long)x)  << 16) | y);
        //System.out.println("CUSTOM TIMESTAMP=" + parsedPES.pts);
        if ((calcOffsetPTS(parsedPES.pts) > streamBitratePtsCalcTime || parsedPS.program_mux_rate == 0) && (parsedPES.pts - firstPTS != 0))
        {
          streamBitrate = 90000 * bitsDone / (8*(parsedPES.pts - firstPTS));
          streamBitratePtsCalcTime = calcOffsetPTS(parsedPES.pts);
        }
      }
    }
    else if ((adaptation_field_control == 1 || adaptation_field_control == 3) && isValidPID(parsedPES.pid))
    {
      if (payload_unit_start_indicator)
      {
        if (readByte() == 0 && readByte() == 0 && readByte() == 1)
        {
          parsedPES.stream_id = (readByte() & 0xFF);
          if (log) System.out.print(" PES id=" + parsedPES.stream_id);
          int pes_packet_length = readShort();//(int) readBits(16);
          if (!availableToRead(pes_packet_length)) return;
          if (parsedPES.stream_id != PROGRAM_STREAM_MAP &&
              parsedPES.stream_id != PADDING_STREAM &&
              parsedPES.stream_id != PRIVATE_STREAM_2 &&
              parsedPES.stream_id != ECM &&
              parsedPES.stream_id != EMM &&
              parsedPES.stream_id != PROGRAM_STREAM_DIRECTORY &&
              parsedPES.stream_id != DSMCC_STREAM &&
              parsedPES.stream_id != H222_STREAM)
          {
            readByte();
            byte b = (byte)(readByte() & 0xFF);
            int pts_dts_flags = (b & 0xC0) >> 6;//readBits(2);
        int pes_header_data_length = (readByte() & 0xFF);
        long bit_start = bitsDone/8;
        long newPTS = 0;
        if (pts_dts_flags == 2)
        {
          int x = readByte();
          newPTS = ((long)(x & 0x0E)) << 29;
          int shot = readShort() & 0xFFFE;
          newPTS += shot << 14;
          shot = readShort() & 0xFFFE;
          newPTS += shot >> 1;
          if (bdp != null)
            newPTS += bdp.getClipPtsOffset(bdp.getCurrClipIndex())*2;
          if (!useCustomTimestamps)
            parsedPES.pts = newPTS;
          else
            currTimestampDiff = parsedPES.pts - newPTS;
          if (log) System.out.print(" PTS=" + parsedPES.pts);
        }
        if (pts_dts_flags == 3)
        {
          int x = readByte();
          newPTS = ((long)(x & 0x0E)) << 29;
          int shot = readShort() & 0xFFFE;
          newPTS += shot << 14;
          shot = readShort() & 0xFFFE;
          newPTS += shot >> 1;
          if (bdp != null)
            newPTS += bdp.getClipPtsOffset(bdp.getCurrClipIndex())*2;
          if (!useCustomTimestamps)
            parsedPES.pts = newPTS;
          else
            currTimestampDiff = parsedPES.pts - newPTS;
          x = readByte();
          parsedPES.dts = ((long)(x & 0x0E)) << 29;
          shot = readShort() & 0xFFFE;
          parsedPES.dts += shot << 14;
          shot = readShort() & 0xFFFE;
          parsedPES.dts += shot >> 1;
          if (bdp != null)
            parsedPES.dts += bdp.getClipPtsOffset(bdp.getCurrClipIndex())*2;
          if (log) System.out.print(" PTS=" + parsedPES.pts + " DTS=" + parsedPES.dts);
        }
        if (!useCustomTimestamps && (calcOffsetPTS(parsedPES.pts) > streamBitratePtsCalcTime || parsedPS.program_mux_rate == 0) && (parsedPES.pts - firstPTS != 0))
        {
          streamBitrate = 90000 * bitsDone / (8*(parsedPES.pts - firstPTS));
          streamBitratePtsCalcTime = calcOffsetPTS(parsedPES.pts);
        }
          }
        }
      }
    }
    skipBytes(tsPacketSize - (bitsDone/8 - parsedPS.bytePos));
    notifyPackSize();
  }

  void adaptation_field(int fieldType) throws IOException
  {
    skipBytes(Math.min(fieldType == 2 ? 183 : 182, readByte()));
  }

  void pack_header() throws IOException
  {
    if (readWord() == PACK_START_CODE)
    {
      parsedPS.bytePos = (bitsDone - 32)/8;
    }
    // Check for mpeg1 or mpeg2
    int verByte = readByte();
    boolean newMpeg1 = (verByte & 0xF0) == 0x20;
    if (newMpeg1 && !mpeg1 && Sage.DBG) System.out.println("MPEG1 stream detected");
    mpeg1 = newMpeg1;
    skipBytes(mpeg1 ? 4 : 5);
    /*        readAndTest(0x01, 2);
        long scrb = 0;
        scrb = readBits(3) << 30;
        marker_bit();
        scrb += readBits(15) << 15;
        marker_bit();
        scrb += readBits(15);
        marker_bit();
		long scrBit = bitsDone;
        long scre = readBits(9);
        marker_bit();*/
    if (mpeg1)
    {
      parsedPS.program_mux_rate = ((readByte() & 0x7F) << 15) | ((readByte() & 0xFF) << 7) | ((readByte() & 0xFE) >> 1);
    }
    else
    {
      parsedPS.program_mux_rate = ((readByte() & 0xFF) << 14) | ((readByte() & 0xFF) << 6) | ((readByte() & 0xFC) >> 2);
      //marker_bit();
      //marker_bit();
      //readBits(5);
      int pack_stuffing_length = (readByte() & 0xFF) & 0x7;//readBits(3);
      for (int i = 0; i < pack_stuffing_length; i++)
        readByte();
    }
    if (streamBitrate == 0)
    {
      streamBitrate = parsedPS.program_mux_rate * 50;
    }
    if (log) System.out.println(" mux_rate=" + parsedPS.program_mux_rate);
    //if (log) System.out.print("SCR=" + scrb + "." + scre + " SCRByte=" + (scrBit/8) + " mux_rate=" + parsedPS.program_mux_rate +
    //	" SCRDIFF=" + (scrb - lastSCR));
    //		lastSCR = scrb;
    if (nextbits(32) == SYSTEM_HEADER_START_CODE)
    {
      system_header();
    }
  }

  void system_header() throws IOException
  {
    if (log) System.out.print(" SYSHDR");
    readWord();//(SYSTEM_HEADER_START_CODE, 32);
    int header_length = readShort();
    skipBytes(header_length);
    /*readByte();readByte();readByte();
//        marker_bit();
  //      long rate_bound = readBits(22);
    //    marker_bit();
		readByte();
		//long audio_bound = readBits(6);
        //boolean fixed_flag = readFlag();
        //boolean CSPS_flag = readFlag();

		readByte();
		//boolean system_audio_lock_flag = readFlag();
        //boolean system_video_lock_flag = readFlag();
        //marker_bit();
        //long video_bound = readBits(5);

		readByte();
        //boolean packet_rate_restriction_flag = readFlag();
        //readBits(7);
//		if (log) System.out.println(" rate_bound=" + rate_bound + " audio_bound=" + audio_bound +
//			" video_bound=" + video_bound + (fixed_flag ? " FIXED" : "") + (CSPS_flag ? " CSPS" : "") +
//			(system_audio_lock_flag ? " AUDIOLOCK" : "") + (system_video_lock_flag ? " VIDEOLOCK" : "") +
//			(packet_rate_restriction_flag ? " RATE_RESTRICT" : ""));
		byte nb = (byte)(readByte() & 0xFF);
        while ((nb & 0x80) != 0)
        {
            int stream_id = nb;
			readByte();readByte();readByte();
            //readAndTest(3, 2);
            //boolean p_std_buffer_bound_scale = readFlag();
            //long p_std_buffer_size_bound = readBits(13);
			//if (log) System.out.println("STREAM " + stream_id + (p_std_buffer_bound_scale ? "LG" : "SM") +
			//	" p-stdbuffbound=" + p_std_buffer_size_bound);
			nb = (byte)(readByte() & 0xFF);
        }
		putbackBits(nb, 8);
		bitsDone -= 8;*/
  }

  long streamDataEndBits;
  void PES_packet() throws IOException
  {
    readByte();readByte();readByte();//readAndTest(PACKET_START_CODE_PREFIX, 24);
    parsedPES.stream_id = (readByte() & 0xFF);
    if (log) System.out.println(" PES id=" + parsedPES.stream_id + " pos=" + bitsDone/8);
    int pes_packet_length = readShort();//(int) readBits(16);
    if (mpeg1)
      streamDataEndBits = bitsDone + pes_packet_length*8;
    if (!availableToRead(pes_packet_length)) return;
    if (parsedPES.stream_id != PROGRAM_STREAM_MAP &&
        parsedPES.stream_id != PADDING_STREAM &&
        parsedPES.stream_id != PRIVATE_STREAM_2 &&
        parsedPES.stream_id != ECM &&
        parsedPES.stream_id != EMM &&
        parsedPES.stream_id != PROGRAM_STREAM_DIRECTORY &&
        parsedPES.stream_id != DSMCC_STREAM &&
        parsedPES.stream_id != H222_STREAM)
    {
      int pes_header_data_length = 0;
      long bit_start = 0;
      if (mpeg1)
      {
        int b = readByte();
        while ((b & 0xFF) == 0xFF) // stuffing
          b = readByte();
        if ((b & 0x40) == 0x40)
        {
          readByte();
          b = readByte();
        }
        long newPTS = 0;
        if ((b & 0xE0) == 0x20)
        {
          // PTS only
          newPTS = ((long)(b & 0x0E)) << 29;
          //newPTS = readBits(3) << 30;
          //marker_bit();

          int shot = readShort() & 0xFFFE;
          newPTS += shot << 14;
          //newPTS += readBits(15) << 15;
          //marker_bit();

          shot = readShort() & 0xFFFE;
          newPTS += shot >> 1;
          //newPTS += readBits(15);
          //marker_bit();
          parsedPES.pts = newPTS;
          if (log) System.out.println(" PTS=" + parsedPES.pts);
        }
        else if ((b & 0xE0) == 0x30)
        {
          // PTS + DTS
          newPTS = ((long)(b & 0x0E)) << 29;
          //newPTS = readBits(3) << 30;
          //marker_bit();

          int shot = readShort() & 0xFFFE;
          newPTS += shot << 14;
          //newPTS += readBits(15) << 15;
          //marker_bit();

          shot = readShort() & 0xFFFE;
          newPTS += shot >> 1;
          //newPTS += readBits(15);
          //marker_bit();
          parsedPES.pts = newPTS;
          //				if (log) System.out.print(" PTS=" + parsedPES.pts);
          /*readAndTest(3, 4);
					newPTS = readBits(3) << 30;
					marker_bit();
					newPTS += readBits(15) << 15;
					marker_bit();
					newPTS += readBits(15);
					marker_bit();
					readAndTest(1, 4);*/


          b = readByte();
          //readAndTest(2, 4);
          parsedPES.dts = ((long)(b & 0x0E)) << 29;
          //newPTS = readBits(3) << 30;
          //marker_bit();

          shot = readShort() & 0xFFFE;
          parsedPES.dts += shot << 14;
          //newPTS += readBits(15) << 15;
          //marker_bit();

          shot = readShort() & 0xFFFE;
          parsedPES.dts += shot >> 1;
          //newPTS += readBits(15);
          //marker_bit();
          /*parsedPES.dts = readBits(3) << 30;
					marker_bit();
					parsedPES.dts += readBits(15) << 15;
					marker_bit();
					parsedPES.dts += readBits(15);
					marker_bit();*/
          if (log) System.out.println(" PTS=" + parsedPES.pts + " DTS=" + parsedPES.dts);
        }
      }
      else
      {
        readByte();
        //readAndTest(0x2, 2);
        //long PES_scrambling_control = readBits(2);
        //boolean pes_priority = readFlag();
        //if (log && pes_priority) System.out.print(" PRIORITY");
        //boolean data_alignment_indicator = readFlag();
        //boolean copyright = readFlag();
        //boolean original_or_copy = readFlag();

        byte b = (byte)(readByte() & 0xFF);
        int pts_dts_flags = (b & 0xC0) >> 6;//readBits(2);
          //boolean escr_flag = readFlag();
          //boolean es_rate_flag = readFlag();
          //boolean dsm_trick_mode_flag = readFlag();
          //boolean additional_copy_info_flag = readFlag();
          //boolean pes_crc_flag = readFlag();
          //boolean pes_extension_flag = readFlag();
          pes_header_data_length = (readByte() & 0xFF);
          bit_start = bitsDone/8;
          long newPTS = 0;
          if (pts_dts_flags == 2)
          {
            int x = readByte();
            //readAndTest(2, 4);
            newPTS = ((long)(x & 0x0E)) << 29;
            //newPTS = readBits(3) << 30;
            //marker_bit();

            int shot = readShort() & 0xFFFE;
            newPTS += shot << 14;
            //newPTS += readBits(15) << 15;
            //marker_bit();

            shot = readShort() & 0xFFFE;
            newPTS += shot >> 1;
            //newPTS += readBits(15);
            //marker_bit();
            parsedPES.pts = newPTS;
            if (log) System.out.print(" PTS=" + parsedPES.pts);
          }
          if (pts_dts_flags == 3)
          {
            int x = readByte();
            //readAndTest(2, 4);
            newPTS = ((long)(x & 0x0E)) << 29;
            //newPTS = readBits(3) << 30;
            //marker_bit();

            int shot = readShort() & 0xFFFE;
            newPTS += shot << 14;
            //newPTS += readBits(15) << 15;
            //marker_bit();

            shot = readShort() & 0xFFFE;
            newPTS += shot >> 1;
            //newPTS += readBits(15);
            //marker_bit();
            parsedPES.pts = newPTS;
            //				if (log) System.out.print(" PTS=" + parsedPES.pts);
            /*readAndTest(3, 4);
					newPTS = readBits(3) << 30;
					marker_bit();
					newPTS += readBits(15) << 15;
					marker_bit();
					newPTS += readBits(15);
					marker_bit();
					readAndTest(1, 4);*/


            x = readByte();
            //readAndTest(2, 4);
            parsedPES.dts = ((long)(x & 0x0E)) << 29;
            //newPTS = readBits(3) << 30;
            //marker_bit();

            shot = readShort() & 0xFFFE;
            parsedPES.dts += shot << 14;
            //newPTS += readBits(15) << 15;
            //marker_bit();

            shot = readShort() & 0xFFFE;
            parsedPES.dts += shot >> 1;
            //newPTS += readBits(15);
            //marker_bit();
            /*parsedPES.dts = readBits(3) << 30;
					marker_bit();
					parsedPES.dts += readBits(15) << 15;
					marker_bit();
					parsedPES.dts += readBits(15);
					marker_bit();*/
            if (log) System.out.print(" PTS=" + parsedPES.pts + " DTS=" + parsedPES.dts);
          }
      }
      if (parsedPES.pts > 0 && (calcOffsetPTS(parsedPES.pts) > streamBitratePtsCalcTime || parsedPS.program_mux_rate == 0) && (parsedPES.pts - firstPTS != 0))
      {
        streamBitrate = 90000 * bitsDone / (8*(parsedPES.pts - firstPTS));
        streamBitratePtsCalcTime = calcOffsetPTS(parsedPES.pts);
      }
      /*            if (escr_flag)
            {
                readBits(2);
                long escr_base = readBits(3) << 30;
                marker_bit();
                escr_base += readBits(15) << 15;
                marker_bit();
                escr_base += readBits(15);
                marker_bit();
                long escr_ext = readBits(9);
                marker_bit();
            }
            if (es_rate_flag)
            {
                marker_bit();
                long es_rate = readBits(22);
                marker_bit();
            }
            if (dsm_trick_mode_flag)
            {
                long trick_mode_control = readBits(3);
                if (trick_mode_control == FAST_FORWARD)
                {
                    long field_id = readBits(2);
                    long intra_slice_refresh = readBits(1);
                    long frequency_truncaction = readBits(2);
                }
                else if (trick_mode_control == SLOW_MOTION)
                {
                    long rep_cntrl = readBits(5);
                }
                else if (trick_mode_control == FREEZE_FRAME)
                {
                    long field_id = readBits(2);
                    readBits(3);
                }
                else if (trick_mode_control == FAST_REVERSE)
                {
                    long field_id = readBits(3);
                    long intra_slice_refresh = readBits(1);
                    long frequency_truncaction = readBits(2);
                }
                else if (trick_mode_control == SLOW_REVERSE)
                {
                    long rep_cntrl = readBits(5);
                }
                else
                    readBits(5);
            }
            if (additional_copy_info_flag)
            {
                marker_bit();
                long additional_copy_info = readBits(7);
            }
            if (pes_crc_flag)
            {
                long previous_pes_packet_crc = readBits(16);
            }
            if (pes_extension_flag)
            {
                boolean pes_private_data_flag = readFlag();
                boolean pack_header_field_flag = readFlag();
                boolean program_packet_sequence_counter_flag = readFlag();
                boolean p_std_buffer_flag = readFlag();
                readBits(3);
                boolean pes_extension_flag_2 = readFlag();
                if (pes_private_data_flag)
                {
                    // 128 bit value
                    long pes_private_data1 = readBits(32);
                    long pes_private_data2 = readBits(32);
                    long pes_private_data3 = readBits(32);
                    long pes_private_data4 = readBits(32);
                }
                if (pack_header_field_flag)
                {
                    long pack_field_length = readBits(8);
                    pack_header();
                }
                if (program_packet_sequence_counter_flag)
                {
                    marker_bit();
                    long program_packet_sequence_counter = readBits(7);
                    marker_bit();
                    long mpeg1_mpeg2_identifier = readBits(1);
                    long original_stuff_length = readBits(6);
                }
                if (p_std_buffer_flag)
                {
                    readAndTest(1, 2);
                    long p_std_buffer_scale = readBits(1);
                    long p_std_buffer_size = readBits(13);
                }
                if (pes_extension_flag_2)
                {
                    marker_bit();
                    long pes_extension_field_length = readBits(7);
                    for (int i = 0; i < pes_extension_field_length; i++)
                    {
                        readBits(8);
                    }
                }
            }
       */
      if (!mpeg1)
      {
        for (long i = (bitsDone/8 - bit_start); i < pes_header_data_length; i++)
          readByte();//readAndTest(0xFF, 8);
        long packetBytes = pes_packet_length - pes_header_data_length
            - 3; // 3 bytes for the flags and header length field
        if (log) System.out.println(" bytes=" + packetBytes);
        streamDataEndBits = bitsDone + packetBytes*8;
      }

      // Now we can parse the actual data in the PES packet
      if ((parsedPES.stream_id & VIDEO_STREAM_MASK) == VIDEO_STREAM)
      {
        if (parsedPS != null && parsedPS.videoStreamID == 0)
          parsedPS.videoStreamID = parsedPES.stream_id;
        //				video_sequence();
      }
      else if ((parsedPES.stream_id & AUDIO_STREAM_MASK) == AUDIO_STREAM)
      {
        if (parsedPS != null && parsedPS.audioStreamID == 0)
          parsedPS.audioStreamID = parsedPES.stream_id;
      }
      //			byteAlign();
      skipBytes((streamDataEndBits - bitsDone) / 8);
    }
    else if (parsedPES.stream_id == PROGRAM_STREAM_MAP ||
        parsedPES.stream_id == PRIVATE_STREAM_2 ||
        parsedPES.stream_id == ECM ||
        parsedPES.stream_id == EMM ||
        parsedPES.stream_id == PROGRAM_STREAM_DIRECTORY ||
        parsedPES.stream_id == DSMCC_STREAM ||
        parsedPES.stream_id == H222_STREAM)
    {
      skipBytes(pes_packet_length);
      //for (int i = 0; i < pes_packet_length; i++)
      //  readBits(8);
    }
    else if (parsedPES.stream_id == PADDING_STREAM)
    {
      if (log) System.out.println(" PADDING " + pes_packet_length);
      skipBytes(pes_packet_length);
      //for (int i = 0; i < pes_packet_length; i++)
      //  readAndTest(0xFF, 8);
    }
  }

  void next_start_code() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    while (readByte() != 0x0 ||
        readByte() != 0x0 ||
        readByte() != 0x1);
    putbackBits(PACKET_START_CODE_PREFIX, 24);
    bitsDone -= 24;
  }

  //	boolean readFlag() throws IOException
  {
    //      return readBits(1) == 1;
  }

  int nextbits(int numBits) throws IOException
  {
    int rv = 0;
    int orgNumBits = numBits;
    while (numBits > 0)
    {
      rv = rv << 8;
      rv |= (readByte() & 0xFF);
      numBits -= 8;
    }
    putbackBits(rv, orgNumBits);
    bitsDone -= orgNumBits;
    return rv;
  }

  void putbackBits(long value, int numBits)
  {
    if (rem != -1)
    {
      throw new IllegalStateException();
    }
    while (numBits >= 8)
    {
      numBits -= 8;
      peeks[peeks.length - ++numPeeks] = (byte)(value & 0xFF);
      value = value >> 8;
    }
    if (numBits != 0)
    {
      throw new IllegalStateException();
    }
  }

  void skipBytes(long numBytes) throws IOException
  {
    if (numBytes <= 0) return;
    // Our file position is ahead by the numPeeks at this point so account for that
    bitsDone += numBytes*8;
    numBytes -= numPeeks;
    if (ins != null)
    {
      ins.position(ins.position() + numBytes);
    }
    else
      inxs.seekToPosition(inxs.getVirtualReadPosition() + numBytes);
    rem = -1;
    numPeeks = 0;
    /*// this was used for some CC testing I think
		int currRead = 0;
		boolean inUserData = false;
        while (numBytes > 0)
        {
			// Look for start codes and print their info out.
			try{
			int nextByte = ins.read();
			currRead = (currRead << 8) | nextByte;
			numBytes--;
			if ((currRead & 0xFFFFFF00) == 0x100)
			{
				if (inUserData)
					System.out.println();
System.out.println("StartCode=0x00000" + Integer.toString(currRead, 16));
				if (currRead == 0x01B2)
				{
					// user data start code
					inUserData = true;
				}
				else
					inUserData = false;
			}
			else if (inUserData)
				System.out.print((char) nextByte);
			}catch(Exception e){}
            //try{numBytes -= ins.skip(numBytes);}catch(Exception e){}
        }*/
  }

  int readByte() throws IOException
  {
    if (rem != -1) throw new IllegalStateException();
    int rv;
    if (numPeeks > 0)
    {
      rv = peeks[peeks.length - numPeeks--];
    }
    else
    {
      int readMe;
      if (ins != null)
      {
        readMe = ins.readUnsignedByte();
      }
      else
      {
        byte[] readOne = new byte[1];
        inxs.readFullyTranscodedData(readOne, 0, 1);
        readMe = (readOne[0] & 0xFF);
      }
      if (readMe == -1)
        throw new EOFException();
      rv = readMe;
    }
    //System.out.println("Byte=" + Integer.toString(rv, 16));
    bitsDone += 8;
    return rv;
  }

  int readWord() throws IOException
  {
    if (rem != -1) throw new IllegalStateException();
    int rv = ((readByte() & 0xFF) << 24) | ((readByte() & 0xFF) << 16) | ((readByte() & 0xFF) << 8) | (readByte() & 0xFF);
    return rv;
  }

  int readShort() throws IOException
  {
    if (rem != -1) throw new IllegalStateException();
    int rv = ((readByte() & 0xFF) << 8) | (readByte() & 0xFF);
    return rv;
  }

  /*    int readBits(int numBits) throws IOException
    {
        long rv = 0;
        bitsDone += numBits;
        while (numBits > 0)
        {
            if (rem == -1)
            {
                rem = 7;
				bfrag = readByte();
				// We may end up peeking beyond what the buffer can hold
                if (numBits >= 8)
                {
                    rv = rv << 8;
                    rv |= (bfrag & 0xFF);
                    rem = -1;
                    numBits -= 8;
                    continue;
                }
            }
            rv = rv << 1;
            if ((bfrag & (0x01 << rem)) != 0)
            {
                rv |= 0x01;
            }
            rem--;
            numBits--;
        }
        return rv;
    }
   */
  /*	void byteAlign() throws IOException
	{
		while (!byteAligned())
			readBits(1);
	}
    boolean byteAligned()
    {
        return rem == -1;
    }
   */
}
