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
import sage.io.RemoteSageFile;
import sage.io.SageFileSource;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author  Homer Simpson
 * @version
 */
public final class Mpeg2Reader
{
  private SageFileSource ins;
  private File mpegFile;
  private String hostname; // for streamed network files
  private byte bfrag; // fragment of a byte that's remaining in the read
  private byte rem; // number of bits valid in the bfrag
  private byte[] peeks; // bytes put back into the stream from putbackBits
  private int numPeeks; // number of valid bytes in peeks
  private long bitsDone; // number of bits read in the overall file
  private long lastSCR;
  private int lastState;
  private long firstPTS = -1;
  private long durationMsec = -1;
  private long finalPTS = -1;
  private boolean log;
  private int parseLevel;

  // Remaining pack bytes from the last read. Put this into the buffer on the next read
  // before we do anything else unless there was a seek. (clear it on seeks)
  private int remPackBytes;

  private byte[] readBuffer = null;
  private int readBufferOff;
  private int readBufferLen;
  private int readBufferAvail;
  private boolean bufferLimit;

  private long streamBitratePtsCalcTime;
  private long streamBitrate; // in bytes/second

  private boolean ts;
  private boolean tsSynced;

  // Used for variable rate playback. If this is not 1, then we're only trying to send I frames and we
  // do some seeking before and after this
  private int playbackRate = 1;
  private int bytesReadForThisSkip;

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
  public Mpeg2Reader(String filename)
  {
    this(new File(filename));
  }
  public Mpeg2Reader(File theFile)
  {
    this(theFile, null);
  }
  public Mpeg2Reader(File theFile, String inHostname)
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

    parseLevel = PARSE_LEVEL_PES;
  }

  public static void perfTest(String filename, boolean logging)
  {
    perfTest(filename, null, logging);
  }
  public static void perfTest(String filename, String outfilename, boolean logging)
  {
    // I was getting 78 MBytes/second with tests on my workstation that read a 2GB MPEG2 file into memory
    // with full PES parsing. I didn't believe it was going that fast, but I had it dump the memory back
    // out to disk, and it did it at 19 MBytes/second and the file was identical. Sweeeet. :) Nice and fast parser...still
    // a little too CPU intensive though.
    Mpeg2Reader mpg = new Mpeg2Reader(filename);
    mpg.setLogging(logging);
    try
    {
      mpg.init(false, false);
    }catch (Exception e){System.out.println("ERROR INIT:" + e);}
    System.out.println("Starting file=" + filename + " length=" + mpg.length());
    long startTime = Sage.time();
    java.io.FileOutputStream os = null;
    try
    {
      byte[] buf = new byte[8192];
      if (outfilename != null)
        os = new java.io.FileOutputStream(outfilename);
      while (true)
      {
        int numRead = mpg.read(buf, 0, buf.length);
        if (os != null)
          os.write(buf, 0, numRead);
        //System.out.println("NumRead=" + numRead);
      }
      //mpg.MPEG2_Program_Stream();
    }
    catch (EOFException e){}
    catch (Exception e){System.out.println(e.toString());e.printStackTrace();}
    if (os != null)
    {
      try{os.close();}catch(Exception e){}
    }
    long stopTime = Sage.time();
    System.out.println("Finished file bytesProcessed=" + mpg.bitsDone/8);
    System.out.println("MBytes/sec=" + (mpg.length() / (1000.0 * (stopTime - startTime))));
  }

  public void setLogging(boolean x) { log = x; }

  public void init(boolean findFirstPTS, boolean findDuration) throws IOException
  {
    parsedPES.pts = -1;
    durationMsec = finalPTS = firstPTS = -1;
    if (hostname != null && hostname.length() > 0)
      ins = new BufferedSageFile(new RemoteSageFile(hostname, mpegFile, true), 65536);
    else
      ins = new BufferedSageFile(new LocalSageFile(mpegFile, true), 65536);

    // Check for 0x47 TS sync byte
    int syncByte = (int) readBits(8);
    if (syncByte == 0x47)
    {
      ts = true;
    }
    if (Sage.DBG) System.out.println("Opened MPEG-2 " + (ts ? "TS" : "PS") + " file: " + mpegFile);
    putbackBits(syncByte, 8);
    bitsDone -= 8;
    packAlign();
    while ((findFirstPTS || findDuration) && firstPTS < 0)
    {
      pack();
      if (parsedPES.pts != -1)
      {
        firstPTS = parsedPES.pts;
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
    if (findDuration)
    {
      long fileLen = ins.length();
      skipBytes(Math.max(0, fileLen - 7*65536 - bitsDone/8));
      packAlign();
      try
      {
        while (true)
        {
          pack();
          if (parsedPES.pts > finalPTS)
          {
            finalPTS = parsedPES.pts;
          }
          /*pack_header();
					while (nextbits(24) == PACKET_START_CODE_PREFIX &&
						nextbits(32) != PACK_START_CODE)
					{
						PES_packet();
						if (parsedPES.pts > finalPTS)
						{
							finalPTS = parsedPES.pts;
							break;
						}
					}*/
        }
      }
      catch (EOFException e)
      {}
      durationMsec = (finalPTS - firstPTS)/90; // convert from 90kHz to msec
    }
    // ain't there a better way to reset the stream?
    if (findFirstPTS || findDuration)
    {
      seek(0);
      parsedPES.pts = -1;
      parsedPS.bytePos = 0;
    }
  }

  private int calculateByteSizeToReadBeforeSkip()
  {
    // This should be 3/4 of a second
    return (playbackRate < 5 && playbackRate >= 0) ? Integer.MAX_VALUE : (int)(streamBitrate * 3 / 4); // program_mux_rate is 50 bytes/second units
  }

  // This is done as a time and then we use our seeking technique after that
  private long calculateSkipAmountForVariablePlaySeek()
  {
    if (playbackRate < 6 && playbackRate >= 0)
    {
      return 0; // no skipping in this case
    }
    // Let's assume it takes us 1/6 of a second to do a chunk playback
    // That means we're getting about 6fps (from testing), and this was one frame.
    // if we want to achieve a rate of playbackRate, then we need to skip playbackRate/2 chunks each time
    if (playbackRate > 0)
    {
      return playbackRate * 1000 / 6;
    }
    else if (playbackRate > -5)
    {
      // If we don't skip at least 2000 then we're not jumping back enough for it to work right
      return -2000;
    }
    else
    {
      return Math.min(-2000, playbackRate * 1000 / 6);
    }
  }

  public void setPlaybackRate(int playRate)
  {
    if (playbackRate != playRate)
      if (Sage.DBG) System.out.println("Mpeg2Reader changing playrate to " + playRate);
    playbackRate = playRate;
  }

  public void setParseLevel(int x)
  {
    parseLevel = x;
  }

  public long getDurationMillis() { return durationMsec; }

  public long getFirstTimestampMillis() { return firstPTS / 90; }

  public long getLastParsedTimeMillis() { return (parsedPES == null || parsedPES.pts <= 0) ? 0 : ((parsedPES.pts - firstPTS) / 90); }

  public void close()
  {
    try
    {
      if (ins != null)
        ins.close();
    }catch (IOException e){}
    ins = null;
  }

  public long length()
  {
    try
    {
      return ins.length();
    }catch (Exception e){return 0;}
  }

  public long availableToRead()
  {
    return Math.max(0, length() - ins.position());
  }

  public long getReadPos() { return ins.position(); }

  /*
   * NOTE NOTE NOTE
   * This needs to be redone to always read the requested length. We need to be able to stop mid packet
   * or the buffering gets really ineffecient since we can't match requested buffer sizes or even be constant
   * about it. BUT this is contrary to what I've been seeing with a few things....especially the concept of
   * push streaming where the server is regulating all of the byte traffic.
   *
   * THIS IS STILL WRONG!!!!! Redo this whole streaming layer from scratch, there's too much bad code in here.
   */
  /*
   * Reads data from the MPEG2 stream into the buffer. We also analyze the data as we read it
   * for doing seeking and variable rate playback control.
   */
  public int read(byte[] buf, int off, int len) throws IOException
  {
    // Bounds checking
    off = Math.max(0, Math.min(off, buf.length - 1));
    len = Math.max(0, Math.min(buf.length - off, len));

    // internal backup arrays checking
    if (len > peeks.length)
    {
      if (Sage.DBG) System.out.println("Adjusting peek buffer size to: " + len);
      byte[] newPeeks = new byte[len*2];
      System.arraycopy(peeks, 0, newPeeks, 0, numPeeks);
      peeks = newPeeks;
    }

    if (calculateByteSizeToReadBeforeSkip() < bytesReadForThisSkip)
    {
      seek(getLastParsedTimeMillis() + calculateSkipAmountForVariablePlaySeek());
    }
    if (parseLevel > PARSE_LEVEL_NONE)
    {
      // We get the buffer from what the parser reads since this will align on pack boundaries
      readBuffer = buf;
      readBufferLen = 0;
      readBufferOff = off;
      readBufferAvail = len;
      bufferLimit = false;
      if (remPackBytes > 0)
      {
        // Copy these first as their what remained from the last read operation
        int peekConsume = Math.min(remPackBytes, len);
        System.arraycopy(peeks, peeks.length - numPeeks, buf, readBufferOff, peekConsume);
        numPeeks -= peekConsume;
        remPackBytes -= peekConsume;
        readBufferOff += peekConsume;
        readBufferLen += peekConsume;
        if (remPackBytes == 0)
          return peekConsume;
      }
      packAlign();
      while (!bufferLimit)
      {
        pack();
      }

      if (readBufferLen == 0 && len > 0)
      {
        // We're at the end of the file and there's only a buffer left, so just read it
        if (Sage.DBG) System.out.println("EOS for Mpeg2Reader, not a full pack left but pushing through rest of file...");
        skipBytes(len);
      }
      bytesReadForThisSkip += readBufferLen;
      // Now we need to put the file pointer back to the beginning of the last pack
      // Check if we're already there
      if (parsedPS.bytePos + parsedPS.packSize == bitsDone/8)
      {
        // We stopped eating at the last pack (due to constant size packs, we know the boundaries)
      }
      else
      {
        int rewindAmount = (int)(bitsDone/8 - parsedPS.bytePos);
        if (rewindAmount > len)
        {
          // This happens when there's invalid packet data and we've run into a packet bigger
          // than our buffer can hold. So just ignore this data and pass it through
        }
        else
        {
          remPackBytes = rewindAmount;
          //readBufferOff -= rewindAmount;
          //readBufferLen -= rewindAmount;
          numPeeks = numPeeks + rewindAmount;
          if (Sage.DBG) System.out.println("Mpeg2Reader readBufferOff=" + readBufferOff + " rewindAmount=" + rewindAmount +
              " peeks.length=" + peeks.length + " numPeeks=" + numPeeks + " parsedPS.packSize=" + parsedPS.packSize);
          System.arraycopy(buf, readBufferOff - rewindAmount, peeks, peeks.length - numPeeks, rewindAmount);
        }
      }
      readBuffer = null;
      return readBufferLen;
    }
    else
    {
      ins.readFully(buf, off, len);
      bitsDone += 8*len;
      return len;
    }
  }

  boolean bufferCanHandle(int numBytes)
  {
    if (readBuffer == null) return true;
    if (bufferLimit || (readBufferAvail - readBufferLen) < numBytes)
    {
      bufferLimit = true;
      return false;
    }
    return true;
  }

  long estimateBytePosForPTS(long targetPts) throws IOException
  {
    if (targetPts <= 0) return 0;
    long rv = 0;
    long fileLength = ins.length();
    if (Sage.DBG) System.out.println("MPEG2 seek targetPts=" + targetPts + " length=" + fileLength + " durationMsec=" + durationMsec +
        " parsedPTS=" + parsedPES.pts + " lastPos=" + parsedPS.bytePos + " firstPTS=" + firstPTS + " mux_rate=" + parsedPS.program_mux_rate +
        " estimBitrate=" + streamBitrate);
    if (parsedPES.pts > 0)
    {
      long recentTime = parsedPES.pts;
      if (Sage.DBG) System.out.println("MPEG2 seek recentTime=" + recentTime);
      if (targetPts == recentTime)
        rv = parsedPS.bytePos;
      else if (targetPts > recentTime)
      {
        if (durationMsec > 0)
        {
          // Average between the recent time and the end time
          long byteDiff = fileLength - parsedPS.bytePos;
          long timeDiff = durationMsec*90 - (recentTime - firstPTS);
          rv = Math.round((((double)(targetPts - recentTime)) * byteDiff) / timeDiff);
          rv += parsedPS.bytePos;
        }
        else
        {
          // add the ratebound guess offset from the recent; but redo
          // the ratebound so it uses a guess from the data in the file since
          // the header is usually wrong
          if (recentTime > 0 && recentTime != firstPTS)
          {
            long guessRate = parsedPS.bytePos/(recentTime - firstPTS);
            rv = parsedPS.bytePos + (targetPts - recentTime)*guessRate;
          }
          else
            rv = (targetPts - firstPTS) * parsedPS.program_mux_rate * 5 / 9000; // program_mux_rate is 50 bytes/sec, pts is 90kHz
        }
      }
      else // if (rtTime < recentTime)
      {
        // Average between the recent time and the start time (0)
        rv = Math.round((((double)(targetPts - firstPTS)) * parsedPS.bytePos) / (recentTime - firstPTS));
      }
    }
    else if (durationMsec > 0)
    {
      rv = Math.round((((double)targetPts - firstPTS) * fileLength) / (durationMsec * 90));
    }
    else
      rv = (targetPts - firstPTS)* parsedPS.program_mux_rate * 5 / 9000; // program_mux_rate is 50 bytes/sec, pts is 90kHz
    rv = Math.max(0, Math.min(rv, fileLength));
    return rv;
  }

  public void seekToBytePos(long bytePos) throws IOException
  {
    if (Sage.DBG) System.out.println("Mpeg2Reader seeking to pos=" + bytePos);
    ins.seek(bytePos);
    bitsDone = bytePos*8;
    rem = -1;
    numPeeks = 0;
    remPackBytes = 0;
    bytesReadForThisSkip = 0;
  }

  public void seek(long seekTime) throws IOException
  {
    long targetPos = estimateBytePosForPTS(seekTime*90 + firstPTS);
    if (parsedPS.constantPackSize && parsedPS.packSize > 0)
    {
      // Align on a pack boundary
      targetPos -= targetPos % parsedPS.packSize;
    }
    targetPos = Math.max(0, targetPos);
    if (Sage.DBG) System.out.println("Mpeg2Reader seeking to pos=" + targetPos + " time=" + Sage.durFormatMillis(seekTime));
    ins.seek(targetPos);
    bitsDone = targetPos*8;
    rem = -1;
    numPeeks = 0;
    remPackBytes = 0;
    bytesReadForThisSkip = 0;
  }

  public void packAlign() throws IOException
  {
    byteAlign();
    if (ts)
    {
      while (true)
      {
        if (readBits(8) != 0x47)
        {
          if (!bufferCanHandle(188))
            return;
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
        if (readBits(8) != 0x0 ||
            readBits(8) != 0x0 ||
            readBits(8) != 0x1 ||
            readBits(8) != (PACK_START_CODE & 0xFF))
        {
          if (!bufferCanHandle(4))
            return;
        }
        else
        {
          break;
        }
      }
      putbackBits(PACK_START_CODE, 32);
      bitsDone -= 32;
    }
  }

  private void notifyPackSize()
  {
    int currPackSize = (int) ((bitsDone/8) - parsedPS.bytePos);
    if (ts)
    {
      tsSynced = (currPackSize == 188);
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
          parsedPS.packSize = currPackSize;
          parsedPS.constantPackSize = false;
        }
      }
      else
        parsedPS.packSize = currPackSize;
    }
  }

  void MPEG2_Program_Stream() throws IOException
  {
    if (log) System.out.println("MPEG2 Program Stream from " + mpegFile + " size=" + ins.length());
    do
    {
      pack();
      notifyPackSize();
    } while (nextbits(32) == PACK_START_CODE);
    if (log) System.out.println("FINISHED bytesLeft = " + (ins.length() - bitsDone/8));
    readAndTest(MPEG_PROGRAM_END_CODE, 32);
  }

  void pack() throws IOException
  {
    if (ts)
    {
      if (!bufferCanHandle(188)) return;
      transport_packet();
    }
    else
    {
      //			if (log) System.out.print("PACK ");
      // Check to make sure we've got enough data to complete the pack. This is easy for constant pack size.
      if (parsedPS.constantPackSize && parsedPS.packSize > 0 && !bufferCanHandle(parsedPS.packSize))
        return;
      // 65 is about how much is needed to get to the PES packet length (but it gets farther than that w/ 65). But there's
      // really no lower bound on this considering there may be unlimited (almost) stream_ids in the system_header
      if (!bufferCanHandle(65)) return;
      pack_header();
      while (true)
      {
        if (bufferCanHandle(4))
        {
          if (nextbits(24) == PACKET_START_CODE_PREFIX &&
              nextbits(32) != PACK_START_CODE)
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
  }

  void transport_packet() throws IOException
  {
    int syncByte;
    do
    {
      syncByte = (int) readBits(8);
      if (syncByte == 0x47)
      {
        parsedPS.bytePos = (bitsDone - 8)/8;
      }
    } while (syncByte != 0x47 && bufferCanHandle(188));
    boolean transport_error_indicator = readFlag();
    boolean payload_unit_start_indicator = readFlag();
    boolean transport_priority = readFlag();
    int pid = (int) readBits(13);
    //		if (log) System.out.print(" PID=" + pid);
    int transport_scrambling_control = (int) readBits(2);
    int adaptation_field_control = (int) readBits(2);
    int continuity_counter = (int) readBits(4);
    if (adaptation_field_control == 2 || adaptation_field_control == 3)
    {
      adaptation_field();
    }
    if (adaptation_field_control == 1 || adaptation_field_control == 3)
    {
      if (payload_unit_start_indicator)
      {
        // NOTE: We're only interested in finding PTS information from the stream so only parse it down that far
        if (readAndTest(PACKET_START_CODE_PREFIX, 24))
        {
          parsedPES.stream_id = (int) readBits(8);
          //					if (log) System.out.print(" PES id=" + parsedPES.stream_id);
          int pes_packet_length = (int) readBits(16);
          if (parsedPES.stream_id != PROGRAM_STREAM_MAP &&
              parsedPES.stream_id != PADDING_STREAM &&
              parsedPES.stream_id != PRIVATE_STREAM_2 &&
              parsedPES.stream_id != ECM &&
              parsedPES.stream_id != EMM &&
              parsedPES.stream_id != PROGRAM_STREAM_DIRECTORY &&
              parsedPES.stream_id != DSMCC_STREAM &&
              parsedPES.stream_id != H222_STREAM)
          {
            readAndTest(0x2, 2);
            long PES_scrambling_control = readBits(2);
            boolean pes_priority = readFlag();
            if (log && pes_priority) System.out.print(" PRIORITY");
            boolean data_alignment_indicator = readFlag();
            boolean copyright = readFlag();
            boolean original_or_copy = readFlag();
            long pts_dts_flags = readBits(2);
            boolean escr_flag = readFlag();
            boolean es_rate_flag = readFlag();
            boolean dsm_trick_mode_flag = readFlag();
            boolean additional_copy_info_flag = readFlag();
            boolean pes_crc_flag = readFlag();
            boolean pes_extension_flag = readFlag();
            long pes_header_data_length = readBits(8);
            long bit_start = bitsDone/8;
            long newPTS = 0;
            if (pts_dts_flags == 2)
            {
              readAndTest(2, 4);
              newPTS = readBits(3) << 30;
              marker_bit();
              newPTS += readBits(15) << 15;
              marker_bit();
              newPTS += readBits(15);
              marker_bit();
              parsedPES.pts = newPTS;
              if (log) System.out.println("PID=0x" + Integer.toString(pid, 16) + " PESid=0x" + Integer.toString(parsedPES.stream_id, 16) + " PTS=" + parsedPES.pts);
            }
            if (pts_dts_flags == 3)
            {
              readAndTest(3, 4);
              newPTS = readBits(3) << 30;
              marker_bit();
              newPTS += readBits(15) << 15;
              marker_bit();
              newPTS += readBits(15);
              marker_bit();
              readAndTest(1, 4);
              parsedPES.dts = readBits(3) << 30;
              marker_bit();
              parsedPES.dts += readBits(15) << 15;
              marker_bit();
              parsedPES.dts += readBits(15);
              marker_bit();
              parsedPES.pts = newPTS;
              if (log) System.out.println("PID=0x" + Integer.toString(pid, 16) + " PESid=0x" + Integer.toString(parsedPES.stream_id, 16) + " PTS=" + parsedPES.pts + " DTS=" + parsedPES.dts);
            }
            if ((parsedPES.pts > streamBitratePtsCalcTime || parsedPS.program_mux_rate == 0) && (parsedPES.pts - firstPTS != 0))
            {
              streamBitrate = 90000 * bitsDone / (8*(parsedPES.pts - firstPTS));
              streamBitratePtsCalcTime = parsedPES.pts;
            }
          }
        }
      }
    }
    skipBytes(188 - (bitsDone/8 - parsedPS.bytePos));
    notifyPackSize();
  }

  void adaptation_field() throws IOException
  {
    int adaptation_field_length = (int) readBits(8);
    long adaptation_field_bitstart = bitsDone/8;
    if (adaptation_field_length > 0)
    {
      boolean discontinuity_indicator = readFlag();
      boolean random_access_indicator = readFlag();
      boolean elementary_stream_priority_indicator = readFlag();
      boolean PCR_flag = readFlag();
      boolean OPCR_flag = readFlag();
      boolean splicing_point_flag = readFlag();
      boolean transport_private_data_flag = readFlag();
      boolean adaptation_field_extension_flag = readFlag();
      if(PCR_flag)
      {
        long program_clock_reference_base = readBits(33);
        readBits(6); // reserved
        int program_clock_reference_extension = (int)readBits(9);
      }
      if (OPCR_flag)
      {
        long original_program_clock_reference_base = readBits(33);
        readBits(6); // reserved
        int original_program_clock_reference_extension = (int)readBits(9);
      }
      if (splicing_point_flag)
      {
        int splice_countdown = (int) readBits(8);
      }
      if (transport_private_data_flag)
      {
        int transport_private_data_length = (int) readBits(8);
        for (int i = 0; i < transport_private_data_length; i++)
        {
          int private_data_byte = (int) readBits(8);
        }
      }
      if (adaptation_field_extension_flag)
      {
        int adaptation_field_extension_length = (int)readBits(8);
        long adaptation_field_extension_startbits = bitsDone/8;
        boolean ltw_flag = readFlag();
        boolean piecewise_rate_flag = readFlag();
        boolean seamless_splice_flag = readFlag();
        readBits(5); //reserved
        if (ltw_flag)
        {
          boolean ltw_valid_flag = readFlag();
          int ltw_offset = (int)readBits(15);
        }
        if (piecewise_rate_flag)
        {
          readBits(2); //reserved
          int piecewise_rate = (int)readBits(22);
        }
        if (seamless_splice_flag)
        {
          int splice_type = (int)readBits(4);
          long DTS_next_AU = 0;
          DTS_next_AU += readBits(3) << 30;
          marker_bit();
          DTS_next_AU += readBits(15) << 15;
          marker_bit();
          DTS_next_AU += readBits(15);
          marker_bit();
        }
        skipBytes((adaptation_field_extension_startbits + adaptation_field_extension_length) - bitsDone/8);
      }
    }
    skipBytes((adaptation_field_bitstart + adaptation_field_length) - bitsDone/8);
  }

  void pack_header() throws IOException
  {
    if (readAndTest(PACK_START_CODE, 32))
    {
      parsedPS.bytePos = (bitsDone - 32)/8;
    }
    readAndTest(0x01, 2);
    long scrb = 0;
    scrb = readBits(3) << 30;
    marker_bit();
    scrb += readBits(15) << 15;
    marker_bit();
    scrb += readBits(15);
    marker_bit();
    long scrBit = bitsDone;
    long scre = readBits(9);
    marker_bit();
    parsedPS.program_mux_rate = (int)readBits(22);
    if (streamBitrate == 0)
    {
      streamBitrate = parsedPS.program_mux_rate * 50;
    }
    marker_bit();
    marker_bit();
    readBits(5);
    long pack_stuffing_length = readBits(3);
    for (int i = 0; i < pack_stuffing_length; i++)
      readAndTest(STUFFING_BYTE, 8);
    //		if (log) System.out.print("SCR=" + scrb + "." + scre + " SCRByte=" + (scrBit/8) + " mux_rate=" + parsedPS.program_mux_rate +
    //			" SCRDIFF=" + (scrb - lastSCR));
    lastSCR = scrb;
    if (nextbits(32) == SYSTEM_HEADER_START_CODE)
    {
      system_header();
    }
  }

  void system_header() throws IOException
  {
    //		if (log) System.out.print(" SYSHDR");
    readAndTest(SYSTEM_HEADER_START_CODE, 32);
    long header_length = readBits(16);
    marker_bit();
    long rate_bound = readBits(22);
    marker_bit();
    long audio_bound = readBits(6);
    boolean fixed_flag = readFlag();
    boolean CSPS_flag = readFlag();
    boolean system_audio_lock_flag = readFlag();
    boolean system_video_lock_flag = readFlag();
    marker_bit();
    long video_bound = readBits(5);
    boolean packet_rate_restriction_flag = readFlag();
    readBits(7);
    if (log) System.out.println(" rate_bound=" + rate_bound + " audio_bound=" + audio_bound +
        " video_bound=" + video_bound + (fixed_flag ? " FIXED" : "") + (CSPS_flag ? " CSPS" : "") +
        (system_audio_lock_flag ? " AUDIOLOCK" : "") + (system_video_lock_flag ? " VIDEOLOCK" : "") +
        (packet_rate_restriction_flag ? " RATE_RESTRICT" : ""));
    while (nextbits(1) == 1)
    {
      long stream_id = readBits(8);
      readAndTest(3, 2);
      boolean p_std_buffer_bound_scale = readFlag();
      long p_std_buffer_size_bound = readBits(13);
      //			if (log) System.out.println("STREAM " + stream_id + (p_std_buffer_bound_scale ? "LG" : "SM") +
      //				" p-stdbuffbound=" + p_std_buffer_size_bound);
    }
  }

  long streamDataEndBits;
  void PES_packet() throws IOException
  {
    readAndTest(PACKET_START_CODE_PREFIX, 24);
    parsedPES.stream_id = (int) readBits(8);
    if (log) System.out.println("PES id=" + parsedPES.stream_id + " bytePos=" + bitsDone/8);
    int pes_packet_length = (int) readBits(16);
    if (!bufferCanHandle(pes_packet_length)) return;
    if (parsedPES.stream_id != PROGRAM_STREAM_MAP &&
        parsedPES.stream_id != PADDING_STREAM &&
        parsedPES.stream_id != PRIVATE_STREAM_2 &&
        parsedPES.stream_id != ECM &&
        parsedPES.stream_id != EMM &&
        parsedPES.stream_id != PROGRAM_STREAM_DIRECTORY &&
        parsedPES.stream_id != DSMCC_STREAM &&
        parsedPES.stream_id != H222_STREAM)
    {
      readAndTest(0x2, 2);
      long PES_scrambling_control = readBits(2);
      boolean pes_priority = readFlag();
      if (log && pes_priority) System.out.print(" PRIORITY");
      boolean data_alignment_indicator = readFlag();
      boolean copyright = readFlag();
      boolean original_or_copy = readFlag();
      long pts_dts_flags = readBits(2);
      boolean escr_flag = readFlag();
      boolean es_rate_flag = readFlag();
      boolean dsm_trick_mode_flag = readFlag();
      boolean additional_copy_info_flag = readFlag();
      boolean pes_crc_flag = readFlag();
      boolean pes_extension_flag = readFlag();
      long pes_header_data_length = readBits(8);
      long bit_start = bitsDone/8;
      long newPTS = 0;
      if (pts_dts_flags == 2)
      {
        readAndTest(2, 4);
        newPTS = readBits(3) << 30;
        marker_bit();
        newPTS += readBits(15) << 15;
        marker_bit();
        newPTS += readBits(15);
        marker_bit();
        parsedPES.pts = newPTS;
        if (log) System.out.println("stream=" + parsedPES.stream_id + " PTS=" + parsedPES.pts);
      }
      if (pts_dts_flags == 3)
      {
        readAndTest(3, 4);
        newPTS = readBits(3) << 30;
        marker_bit();
        newPTS += readBits(15) << 15;
        marker_bit();
        newPTS += readBits(15);
        marker_bit();
        readAndTest(1, 4);
        parsedPES.dts = readBits(3) << 30;
        marker_bit();
        parsedPES.dts += readBits(15) << 15;
        marker_bit();
        parsedPES.dts += readBits(15);
        marker_bit();
        parsedPES.pts = newPTS;
        if (log) System.out.println("stream=" + parsedPES.stream_id + " PTS=" + parsedPES.pts + " DTS=" + parsedPES.dts);
      }
      if ((parsedPES.pts > streamBitratePtsCalcTime || parsedPS.program_mux_rate == 0) && (parsedPES.pts - firstPTS != 0))
      {
        streamBitrate = 90000 * bitsDone / (8*(parsedPES.pts - firstPTS));
        streamBitratePtsCalcTime = parsedPES.pts;
      }
      if (escr_flag)
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
      for (long i = (bitsDone/8 - bit_start); i < pes_header_data_length; i++)
        readAndTest(0xFF, 8);
      long packetBytes = pes_packet_length - pes_header_data_length
          - 3; // 3 bytes for the flags and header length field
      //			if (log) System.out.println(" bytes=" + packetBytes);
      streamDataEndBits = bitsDone + packetBytes*8;

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
      byteAlign();
      skipBytes((streamDataEndBits - bitsDone) / 8);

      if (log && readBuffer != null)
      {
        for (int x = Math.max(0, readBufferOff - pes_packet_length); x + 5 < readBufferLen; x++)
        {
          if (readBuffer[x] == 0 && readBuffer[x + 1] == 0 && readBuffer[x + 2] == 1)
          {
            if ((readBuffer[x + 3] & 0xFF) == 0xB6)
            {
              mpeg4video = true;
              int picType = (readBuffer[x + 4] & 0xC0) >> 6;
        System.out.println("PictureType " + (picType == 0 ? "I Frame" : (picType == 1 ? "P Frame" : "B Frame")));
            }
            else if (!mpeg4video && readBuffer[x + 3] == 0)
            {
              // Picture start code
              int picType = (readBuffer[x + 5] & 0x38) >> 3;
          System.out.println("PictureType " + (picType == I_FRAME ? "I Frame" : (picType == P_FRAME ? "P Frame" : "B Frame")));
            }
          }
        }
      }

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
      //			if (log) System.out.println(" PADDING " + pes_packet_length);
      skipBytes(pes_packet_length);
      //for (int i = 0; i < pes_packet_length; i++)
      //  readAndTest(0xFF, 8);
    }
  }
  private boolean mpeg4video;
  void next_start_code() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    byteAlign();
    while (readBits(8) != 0x0 ||
        readBits(8) != 0x0 ||
        readBits(8) != 0x1);
    putbackBits(PACKET_START_CODE_PREFIX, 24);
    bitsDone -= 24;
  }

  void video_sequence() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    next_start_code();
    // We're not doing the demuxing here so unless the video data starts with this packet data we've got nothing to parse
    if (nextbits(32) != SEQUENCE_HEADER_CODE)
      return;
    sequence_header();
    if (nextbits(32) == EXTENSION_START_CODE)
    {
      sequence_extension();
      do
      {
        if (streamDataEndBits <= bitsDone) return;
        extension_and_user_data(0);
        do
        {
          if (streamDataEndBits <= bitsDone) return;
          if (nextbits(32) == GROUP_START_CODE)
          {
            group_of_pictures_header();
            extension_and_user_data(1);
          }
          picture_header();
          picture_coding_extension();
          extension_and_user_data(2);
          picture_data();
        } while (nextbits(32) == PICTURE_START_CODE ||
            nextbits(32) == GROUP_START_CODE);
        if (nextbits(32) != SEQUENCE_END_CODE)
        {
          sequence_header();
          sequence_extension();
        }
      } while (nextbits(32) != SEQUENCE_END_CODE);
    }
    else
    {
      // MPEG-1 video
    }
    readAndTest(SEQUENCE_END_CODE, 32);
  }

  void sequence_header() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    readAndTest(SEQUENCE_HEADER_CODE, 32);
    parsedVideo.horizontal_size_value = (int)readBits(12);
    parsedVideo.vertical_size_value = (int)readBits(12);
    int aspect_ratio_information = (int)readBits(4);
    int frame_rate_code = (int)readBits(4);
    parsedVideo.bit_rate_value = (int)readBits(18) * 400; // 400 bits/sec converted to bits/sec
    marker_bit();
    int vbv_buffer_size_value = (int)readBits(10);
    boolean constrained_parameters_flag = readFlag();
    boolean load_intra_quantiser_matrix = readFlag();
    if (load_intra_quantiser_matrix)
      readBits(8*64); // we're not byte aligned here so we can't do a skip bytes
    boolean load_non_intra_quantiser_matrix = readFlag();
    if (load_non_intra_quantiser_matrix)
      skipBytes(64);
  }

  boolean progressive_sequence;
  void sequence_extension() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    readAndTest(EXTENSION_START_CODE, 32);
    int extension_start_code_identifier = (int) readBits(4);
    int profile_and_level_indicator = (int) readBits(8);
    progressive_sequence = readFlag();
    int chroma_format = (int) readBits(2);
    int horizontal_size_extension = (int) readBits(2);
    int vertical_size_extension = (int) readBits(2);
    int bitrate_extension = (int) readBits(12);
    marker_bit();
    int vbv_buffer_size_extension = (int) readBits(8);
    boolean low_delay = readFlag();
    int frame_rate_extension_n = (int) readBits(2);
    int frame_rate_extension_d = (int) readBits(5);
    next_start_code();
  }

  void sequence_display_extension() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    int extension_start_code_identifier = (int) readBits(4);
    int videoFormat = (int) readBits(3);
    boolean colour_description = readFlag();
    if (colour_description)
    {
      int colour_primaries = (int) readBits(8);
      int transfer_characteristics = (int) readBits(8);
      int matrix_coefficients = (int) readBits(8);
    }
    int display_horizontal_size = (int) readBits(14);
    marker_bit();
    int display_vertical_size = (int) readBits(14);
    next_start_code();
  }

  void extension_and_user_data(int i) throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    while (nextbits(32) == EXTENSION_START_CODE ||
        nextbits(32) == USER_DATA_START_CODE)
    {
      if (streamDataEndBits <= bitsDone) return;
      if (i != 1)
        if (nextbits(32) == EXTENSION_START_CODE)
          extension_data(i);
      if (nextbits(32) == USER_DATA_START_CODE)
        user_data();
    }
  }

  void user_data() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    readAndTest(USER_DATA_START_CODE, 32);
    while (nextbits(24) != PACKET_START_CODE_PREFIX)
    {
      if (streamDataEndBits <= bitsDone) return;
      int user_data = (int) readBits(8);
    }
    next_start_code();
  }

  void extension_data(int i) throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    while (nextbits(32) == EXTENSION_START_CODE)
    {
      if (streamDataEndBits <= bitsDone) return;
      readAndTest(EXTENSION_START_CODE, 32);
      if (i == 0)
      {
        if (nextbits(4) == SEQUENCE_DISPLAY_EXTENSION_ID)
          sequence_display_extension();
        if (nextbits(4) == SEQUENCE_SCALABLE_EXTENSION_ID)
          sequence_scalable_extension();
      }
      if (i == 2)
      {
        if (nextbits(4) == QUANT_MATRIX_EXTENSION_ID)
          quant_matrix_extension();
        if (nextbits(4) == PICTURE_PAN_SCAN_EXTENSION_ID)
          picture_display_extension();
        if (nextbits(4) == PICTURE_SPATIAL_SCALABLE_EXTENSION_ID)
          picture_spatial_scalable_extension();
        if (nextbits(4) == PICTURE_TEMPORAL_SCALABLE_EXTENSION_ID)
          picture_temporal_scalable_extension();
      }
    }
  }

  void sequence_scalable_extension() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    int extension_start_code_identifier = (int) readBits(4);
    int scalable_mode = (int) readBits(2);
    int layer_id = (int) readBits(4);
    if (scalable_mode == SPATIAL_SCALABILITY)
    {
      int lower_layer_prediction_horizontal_Size = (int) readBits(14);
      marker_bit();
      int lower_layer_prediction_vertical_size = (int) readBits(14);
      int horizontal_subsampling_factor_m = (int) readBits(5);
      int horizontal_subsampling_factor_n = (int) readBits(5);
      int vertical_subsampling_factor_m = (int) readBits(5);
      int vertical_subsampling_factor_n = (int) readBits(5);
    }
    if (scalable_mode == TEMPORAL_SCALABILITY)
    {
      boolean picture_mux_enable = readFlag();
      if (picture_mux_enable)
      {
        boolean mux_to_progressive_sequence = readFlag();
      }
      int picture_mux_order = (int) readBits(3);
      int picture_mux_factor = (int) readBits(3);
    }
    next_start_code();
  }

  void group_of_pictures_header() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    readAndTest(GROUP_START_CODE, 32);
    int time_code = (int) readBits(25);
    boolean closed_gop = readFlag();
    boolean broken_link = readFlag();
    next_start_code();
  }

  void picture_header() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    readAndTest(PICTURE_START_CODE, 32);
    int temporal_reference = (int) readBits(10);
    parsedVideo.picture_coding_type = (int) readBits(3);
    if (log) System.out.println("PictureType " + (parsedVideo.picture_coding_type == I_FRAME ? "I Frame" : (parsedVideo.picture_coding_type == P_FRAME ? "P Frame" : "B Frame")));
    int vbv_delay = (int) readBits(16);
    if (parsedVideo.picture_coding_type == 2 || parsedVideo.picture_coding_type == 3)
    {
      boolean full_pel_forward_vector = readFlag();
      int forward_f_code = (int) readBits(3);
    }
    if (parsedVideo.picture_coding_type == 3)
    {
      boolean full_pel_backward_vector = readFlag();
      int backward_f_code = (int) readBits(3);
    }
    while (nextbits(1) == 1)
    {
      if (streamDataEndBits <= bitsDone) return;
      marker_bit();
      int extra_information_picture = (int) readBits(8);
    }
    readAndTest(0, 1);
    next_start_code();
  }

  boolean repeat_first_field;
  boolean top_field_first;
  int picture_structure;
  void picture_coding_extension() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    readAndTest(EXTENSION_START_CODE, 32);
    int extension_start_code_identifier = (int) readBits(4);
    int[][] fcode = new int[2][2];
    fcode[0][0] = (int) readBits(4);
    fcode[0][1] = (int) readBits(4);
    fcode[1][0] = (int) readBits(4);
    fcode[1][1] = (int) readBits(4);
    int intra_dc_precision = (int) readBits(2);
    picture_structure = (int) readBits(2);
    top_field_first = readFlag();
    boolean frame_pred_frame_dct = readFlag();
    boolean concealment_motion_vectors = readFlag();
    boolean q_scale_type = readFlag();
    boolean intra_vlc_format = readFlag();
    boolean alternate_scan = readFlag();
    repeat_first_field = readFlag();
    boolean chroma_420_type = readFlag();
    boolean progressive_frame = readFlag();
    boolean composite_display_flag = readFlag();
    if (composite_display_flag)
    {
      boolean v_axis = readFlag();
      int field_sequence = (int) readBits(3);
      boolean sub_carrier = readFlag();
      int burst_amplitude = (int) readBits(7);
      int sub_carrier_phase = (int) readBits(8);
    }
    next_start_code();
  }

  void quant_matrix_extension() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    int extension_start_code_identifier = (int) readBits(4);
    boolean load_intra_quantiser_matrix = readFlag();
    if (load_intra_quantiser_matrix)
      readBits(8*64);
    boolean load_non_intra_quantiser_matrix = readFlag();
    if (load_non_intra_quantiser_matrix)
      readBits(8*64);
    boolean load_chroma_intra_quantiser_matrix = readFlag();
    if (load_chroma_intra_quantiser_matrix)
      readBits(8*64);
    boolean load_chroma_non_intra_quantiser_matrix = readFlag();
    if (load_chroma_non_intra_quantiser_matrix)
      readBits(8*64);
    next_start_code();
  }

  void picture_display_extension() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    int extension_start_code_identifier = (int) readBits(4);
    int number_of_frame_centre_offsets;
    if (progressive_sequence)
    {
      if (repeat_first_field)
      {
        if (top_field_first)
          number_of_frame_centre_offsets = 3;
        else
          number_of_frame_centre_offsets = 2;
      }
      else
      {
        number_of_frame_centre_offsets = 1;
      }
    }
    else
    {
      if (picture_structure == TOP_FIELD || picture_structure == BOTTOM_FIELD)
      {
        number_of_frame_centre_offsets = 1;
      }
      else
      {
        if (repeat_first_field)
          number_of_frame_centre_offsets = 3;
        else
          number_of_frame_centre_offsets = 2;
      }
    }
    for (int i = 0; i < number_of_frame_centre_offsets; i++)
    {
      int frame_centre_horizontal_offset = (int) readBits(16);
      marker_bit();
      int frame_centre_vertical_offset = (int) readBits(16);
      marker_bit();
    }
    next_start_code();
  }

  void picture_temporal_scalable_extension() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    int extension_start_code_identifier = (int) readBits(4);
    int reference_select_code = (int) readBits(2);
    int forward_temporal_reference = (int) readBits(10);
    marker_bit();
    int backward_temporal_reference = (int) readBits(10);
    next_start_code();
  }

  void picture_spatial_scalable_extension() throws IOException
  {
    if (streamDataEndBits <= bitsDone) return;
    int extension_start_code_identifier = (int) readBits(4);
    int lower_layer_temporal_reference = (int) readBits(10);
    marker_bit();
    int lower_layer_horizontal_offset = (int) readBits(15);
    marker_bit();
    int lower_layer_vertical_offset = (int) readBits(15);
    int spatial_temporal_weight_code_table_index = (int) readBits(2);
    boolean lower_layer_progressive_frame = readFlag();
    boolean lower_layer_deinterlaced_field_select = readFlag();
    next_start_code();
  }

  void picture_data() throws IOException
  {
    // This stuff we just skip over. It's a series of slices. So just keep eating data and start codes
    // until we get to where we need to be.
    byteAlign();
    while (true)
    {
      if (streamDataEndBits <= bitsDone) return;
      while (readBits(8) != 0x0 ||
          readBits(8) != 0x0 ||
          readBits(8) != 0x1);
      int codeBits = (int) readBits(8);
      if (codeBits > 0 && codeBits < 0xB0)
      {
        continue; // slice start code, continue consuming data
      }
      else
      {
        putbackBits((0x00000100) | codeBits, 32);
        break;
      }
    }
    bitsDone -= 32;
  }

  boolean readFlag() throws IOException
  {
    return readBits(1) == 1;
  }

  void marker_bit() throws IOException
  {
    readAndTest(1, 1);
  }

  long nextbits(int numBits) throws IOException
  {
    long rv = readBits(numBits);
    putbackBits(rv, numBits);
    bitsDone -= numBits;
    return rv;
  }

  boolean readAndTest(long code, int numBits) throws IOException
  {
    long in = readBits(numBits);
    if (code != in)
    {
      if (log) System.out.println("READANDTEST FAILURE comparing " + code + " to read value of " + in);
      return false;
    }
    return true;
  }

  void putbackBits(long value, int numBits)
  {
    if (rem != -1)
    {
      int bitsLeft = 7 - rem;
      if (bitsLeft > numBits)
      {
        rem += numBits;
        return;
      }
      numBits -= bitsLeft;
      value = value >> bitsLeft;
    rem = -1;
    peeks[peeks.length - ++numPeeks] = bfrag;
    if (readBuffer != null)
    {
      readBufferOff--;
      readBufferLen--;
    }
    }
    while (numBits >= 8)
    {
      numBits -= 8;
      peeks[peeks.length - ++numPeeks] = (byte)(value & 0xFF);
      value = value >> 8;
    if (readBuffer != null)
    {
      readBufferOff--;
      readBufferLen--;
    }
    }
    if (numBits != 0)
    {
      putbackBits(value, numBits);
    }
  }

  void skipBytes(long numBytes) throws IOException
  {
    if (numBytes <= 0) return;
    // Our file position is ahead by the numPeeks at this point so account for that
    if (readBuffer != null)
    {
      bitsDone += numBytes*8;
      if (numPeeks > 0)
      {
        int peekConsume = Math.min((int)numBytes, numPeeks);
        System.arraycopy(peeks, peeks.length - numPeeks, readBuffer, readBufferOff, peekConsume);
        numPeeks -= peekConsume;
        numBytes -= peekConsume;
        readBufferOff += peekConsume;
        readBufferLen += peekConsume;
      }
      if (numBytes == 0) return;
      ins.readFully(readBuffer, readBufferOff, (int)numBytes);
      readBufferOff += numBytes;
      readBufferLen += numBytes;
      rem = -1;
    }
    else
    {
      bitsDone += numBytes*8;
      numBytes -= numPeeks;
      ins.seek(ins.position() + numBytes);
      rem = -1;
      numPeeks = 0;
    }
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

  long readBits(int numBits) throws IOException
  {
    long rv = 0;
    bitsDone += numBits;
    while (numBits > 0)
    {
      rv = rv << 1;
      if (rem == -1)
      {
        rem = 7;
        if (numPeeks > 0)
          bfrag = peeks[peeks.length - numPeeks--];
        else
        {
          int readMe = ins.read();
          if (readMe == -1)
            throw new EOFException();
          bfrag = (byte)(readMe & 0xFF);
          //System.out.println("Byte=" + Integer.toString((bfrag & 0xFF), 16));
        }
        // We may end up peeking beyond what the buffer can hold
        if (readBuffer != null && readBufferLen < readBufferAvail)
        {
          readBuffer[readBufferOff++] = bfrag;
          readBufferLen++;
        }
        if (numBits >= 8)
        {
          rv = rv << 7;
          rv |= (bfrag & 0xFF);
          rem = -1;
          numBits -= 8;
          continue;
        }
      }
      if ((bfrag & (0x01 << rem)) != 0)
      {
        rv |= 0x01;
      }
      rem--;
      numBits--;
    }
    return rv;
  }

  void byteAlign() throws IOException
  {
    while (!byteAligned())
      readBits(1);
  }
  boolean byteAligned()
  {
    return rem == -1;
  }

  public void setSpeed(float f)
  {
    timeScaling = f;
  }

  private float timeScaling;
}
