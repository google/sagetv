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

import java.text.DecimalFormat;

public class FFMPEGTranscoder implements TranscodeEngine
{
  private static final boolean XCODE_DEBUG = Sage.DBG && Sage.getBoolean("media_server/transcode_debug", false);
  static final String BITRATE_OPTIONS_SIZE_KEY = "httpls_bandwidth/%s/video_size";

  public FFMPEGTranscoder()
  {
  }

  public long getAvailableTranscodeBytes()
  {
    if (bufferOutput)
      return Math.max(0, xcodeBufferVirtualSize - xcodeBufferVirtualReadPos);
    else
    {
      if (xcodeDone)
        return 0;
      else
        return 65536;
    }
  }

  public long getVirtualReadPosition()
  {
    return xcodeBufferVirtualReadPos;
  }

  public long getVirtualTranscodeSize()
  {
    return xcodeBufferVirtualSize;
  }

  public boolean isTranscodeDone()
  {
    return xcodeDone;
  }

  public boolean didTranscodeCompleteOK()
  {
    if (!xcodeDone) return false;
    if (xcodeProcess != null)
    {
      try
      {
        lastExitCode = xcodeProcess.exitValue();
      }
      catch (IllegalThreadStateException ise)
      {
        lastExitCode = -1;
      }
    }
    return xcodeDone && lastExitCode == 0;
  }

  public void pauseTranscode()
  {
  }

  public void readFullyTranscodedData(byte[] buf, int inOffset, int inLength) throws java.io.IOException
  {
    readFullyTranscodedData(null, buf, inOffset, inLength);
  }
  public void readFullyTranscodedData(java.nio.ByteBuffer buf) throws java.io.IOException
  {
    readFullyTranscodedData(buf, null, buf.position(), buf.remaining());
  }
  private void readFullyTranscodedData(java.nio.ByteBuffer bb, byte[] buf, int inOffset, int inLength) throws java.io.IOException
  {
    int leftToRead = inLength;
    if (bufferOutput)
    {
      long overage = inLength - getAvailableTranscodeBytes();
      int numTries = 50;
      if (XCODE_DEBUG && overage > 0) System.out.println("Waiting for more data to appear in transcode buffer over=" + overage +
          " xcodeDone=" + xcodeDone);

      while (overage > 0 && !xcodeDone && (numTries-- > 0))
      {
        try { Thread.sleep(200); } catch (Exception e){}
        overage = inLength - getAvailableTranscodeBytes();
      }
      if (overage > 0)
      {
        if (overage > leftToRead)
        {
          leftToRead = 0;
          overage = inLength;
        }
        else
        {
          leftToRead -= overage;
        }
      }
      int buffNum = (int) (((xcodeBufferVirtualReadPos - xcodeBufferVirtualOffset) / xcodeBuffer[0].length) + xcodeBufferBaseNum) % xcodeBuffer.length;
      int buffOffset = (int) (xcodeBufferVirtualReadPos - xcodeBufferVirtualOffset) % xcodeBuffer[0].length;
      if (XCODE_DEBUG) System.out.println("Xcode readTranscodedData(" + inLength + ") buffNum=" + buffNum +
          " buffOffset=" + buffOffset);
      int tempOffset = inOffset;
      while (leftToRead > 0)
      {
        int currRead = Math.min((int)leftToRead, xcodeBuffer[buffNum].length - buffOffset);
        if (bb != null)
          bb.put(xcodeBuffer[buffNum], buffOffset, currRead);
        else
          System.arraycopy(xcodeBuffer[buffNum], buffOffset, buf, tempOffset, currRead);
        tempOffset += currRead;
        leftToRead -= currRead;
        buffNum = (buffNum + 1) % xcodeBuffer.length;
        buffOffset = 0;
      }
      if (XCODE_DEBUG) System.out.println("Xcode transferData complete overage=" + overage);
      xcodeBufferVirtualReadPos += inLength;
      synchronized (xcodeSyncLock)
      {
        while (xcodeBufferVirtualReadPos - xcodeBufferVirtualOffset >= xcodeBuffer[0].length)
        {
          // We're reading more than one buffer beyond our start so we can kill that first buffer now
          xcodeBufferBaseNum = (xcodeBufferBaseNum + 1) % xcodeBuffer.length;
          xcodeBufferVirtualOffset += xcodeBuffer[0].length;
          numFilledXcodeBuffers--;
          if (XCODE_DEBUG) System.out.println("Adjusted buffer nums xcodeBufferBaseNum=" + xcodeBufferBaseNum +
              " xcodeBufferVirtualOffset=" + xcodeBufferVirtualOffset + " numFilledBuffers=" + numFilledXcodeBuffers);
          xcodeSyncLock.notifyAll();
        }
      }
      if (overage > 0)
      {
        if (bb != null)
        {
          while (bb.remaining() > 0)
            bb.put((byte) 0xFF);
        }
        else
          java.util.Arrays.fill(buf, (int)(inOffset + inLength - overage), inOffset + inLength, (byte)0xFF);
        if (XCODE_DEBUG) System.out.println("Xcoder Sending overage=" + overage);
      }
    }
    else
    {
      while (leftToRead > 0)
      {
        int numRead;
        if (bb != null)
        {
          if (nioTmpBuf == null)
            nioTmpBuf = new byte[4096];
          numRead = xcodeStdout.read(nioTmpBuf, 0, Math.min(leftToRead, nioTmpBuf.length));
          bb.put(nioTmpBuf, 0, numRead);
        }
        else
          numRead = xcodeStdout.read(buf, inOffset, leftToRead);
        if (XCODE_DEBUG) System.out.println("Xcoder readFully " + numRead + " bytes directly from transcoder and is pushing it out");
        if (numRead == -1)
        {
          // EOF, use the overage buffer for the rest but also push what we have in ours
          if (XCODE_DEBUG) System.out.println("XCoder sending overage for incomplete buffer read");
          if (bb != null)
          {
            while (bb.remaining() > 0)
              bb.put((byte) 0xFF);
          }
          else
            java.util.Arrays.fill(buf, inOffset, inOffset + leftToRead, (byte)0xFF);
          leftToRead = 0;
          xcodeDone = true;
        }
        else
        {
          inOffset += numRead;
          leftToRead -= numRead;
        }
      }
      xcodeBufferVirtualReadPos = xcodeBufferVirtualOffset = xcodeBufferVirtualSize = xcodeBufferVirtualReadPos + inLength;
    }
  }

  protected long estimateTranscodeSeekTimeFromOffset(long offset)
  {
    // This should return the time for the corresponding offset in the transcoded file. We estimate this
    // by analyzing the output of the transcoder and tracking what time it thinks certain byte positions correspond to.
    double streamRate = (lastXcodeStreamPosition / ((double)lastXcodeStreamTime));
    long rv = Math.round(offset / streamRate);
    if (XCODE_DEBUG) System.out.println("Xcode seeking estimRate=" + streamRate + " offset=" + offset + " time=" + rv);
    return rv;
  }

  public long getCurrentTranscodeStreamTime()
  {
    return lastXcodeStreamTime;
  }

  public void seekToPosition(long offset) throws java.io.IOException
  {
    if (!isTranscoding())
    {
      if (offset == 0)
        startTranscode();
      else
        throw new java.io.IOException("Cannot do seekToPosition in transcoder because it hasn't been started yet!");
      return;
    }
    if ((!bufferOutput && offset != xcodeBufferVirtualOffset) || (bufferOutput && (offset < xcodeBufferVirtualOffset ||
        offset >= xcodeBufferVirtualOffset + xcodeBuffer.length*xcodeBuffer[0].length)))
    {
      long seekTime = estimateTranscodeSeekTimeFromOffset(offset);
      stopTranscode();
      if (XCODE_DEBUG) System.out.println("Restarting transcode to perform seek so read can continue time=" + seekTime);
      xcodeBufferVirtualReadPos = xcodeBufferVirtualOffset = xcodeBufferVirtualSize = offset;
      transcodeStartSeekTime = seekTime;
      startTranscode();
    }
    else
    {
      xcodeBufferVirtualReadPos = offset;
      synchronized (xcodeSyncLock)
      {
        while (offset - xcodeBufferVirtualOffset >= xcodeBuffer[0].length)
        {
          // We're reading more than one buffer beyond our start so we can kill that first buffer now
          xcodeBufferBaseNum = (xcodeBufferBaseNum + 1) % xcodeBuffer.length;
          xcodeBufferVirtualOffset += xcodeBuffer[0].length;
          numFilledXcodeBuffers--;
          if (XCODE_DEBUG) System.out.println("Adjusted buffer nums from seekToPosition xcodeBufferBaseNum=" + xcodeBufferBaseNum +
              " xcodeBufferVirtualOffset=" + xcodeBufferVirtualOffset + " numFilledBuffers=" + numFilledXcodeBuffers);
          xcodeSyncLock.notifyAll();
        }
      }
    }
  }

  // NOTE: There's two different kinds of seek techniques used here. For time-based we reset all of our position info. For
  // position based we have to track that stuff so we know where the client thinks we are.

  // This will ALWAYS rebuild the transcoder so only use it when necessary
  public void seekToTime(long milliSeekTime) throws java.io.IOException
  {
    stopTranscode();
    transcodeStartSeekTime = milliSeekTime;
    xcodeBufferVirtualReadPos = xcodeBufferVirtualOffset = xcodeBufferVirtualSize = 0;
    startTranscode();
  }

  public void sendTranscodeOutputToChannel(long offset, long length, java.nio.channels.WritableByteChannel chan) throws java.io.IOException
  {
    long leftToRead = length;
    // Check to see if we're going to need to do a seek to fulfill this read request.
    if ((!bufferOutput && offset != xcodeBufferVirtualOffset) || (bufferOutput && (offset < xcodeBufferVirtualOffset ||
        offset + length > xcodeBufferVirtualOffset + xcodeBuffer.length*xcodeBuffer[0].length)))
    {
      // Seek in the file to the 'offset'
      seekToPosition(offset);
    }

    if (bufferOutput)
    {
      long overage = offset + length - xcodeBufferVirtualSize;
      int numTries = 50;
      if (XCODE_DEBUG && overage > 0) System.out.println("Xcoder waiting for more data to appear in transcode buffer over=" + overage +
          " xcodeDone=" + xcodeDone);

      while (overage > 0 && !xcodeDone && (numTries-- > 0))
      {
        try { Thread.sleep(200); } catch (Exception e){}
        overage = offset + length - xcodeBufferVirtualSize;
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
      int buffNum = (int) (((offset - xcodeBufferVirtualOffset) / xcodeBuffer[0].length) + xcodeBufferBaseNum) % xcodeBuffer.length;
      int buffOffset = (int) (offset - xcodeBufferVirtualOffset) % xcodeBuffer[0].length;
      if (XCODE_DEBUG) System.out.println("Xcode transferData(" + offset + ", " + leftToRead + ") buffNum=" + buffNum +
          " buffOffset=" + buffOffset);
      while (leftToRead > 0)
      {
        int currRead = Math.min((int)leftToRead, xcodeBuffer[buffNum].length - buffOffset);
        chan.write(java.nio.ByteBuffer.wrap(xcodeBuffer[buffNum], buffOffset, currRead));
        leftToRead -= currRead;
        buffNum = (buffNum + 1) % xcodeBuffer.length;
        buffOffset = 0;
      }
      if (XCODE_DEBUG) System.out.println("Xcode transferData complete overage=" + overage);
      synchronized (xcodeSyncLock)
      {
        while (offset - xcodeBufferVirtualOffset >= xcodeBuffer[0].length)
        {
          // Kill the buffers we've consumed
          xcodeBufferBaseNum = (xcodeBufferBaseNum + 1) % xcodeBuffer.length;
          xcodeBufferVirtualOffset += xcodeBuffer[0].length;
          numFilledXcodeBuffers--;
          if (XCODE_DEBUG) System.out.println("Adjusted buffer nums xcodeBufferBaseNum=" + xcodeBufferBaseNum +
              " xcodeBufferVirtualOffset=" + xcodeBufferVirtualOffset + " numFilledBuffers=" + numFilledXcodeBuffers);
          xcodeSyncLock.notifyAll();
        }
      }
      while (overage > 0)
      {
        initOverageBuffer();
        overageBuf.limit((int)Math.min(overage, overageBuf.capacity()));
        if (XCODE_DEBUG) System.out.println("Xcoder sending overage=" + overageBuf.limit());
        int numWritten = chan.write(overageBuf); // just write out FF's
        overage -= numWritten;
        if (XCODE_DEBUG) System.out.println("Xcoder overage sent capacity=" + overageBuf.capacity() + " overage=" + overage + " numWritten=" + numWritten);
      }
      xcodeBufferVirtualReadPos = offset + length;
    }
    else
    {
      if (hackBuf == null)
      {
        hackBuf = java.nio.ByteBuffer.allocate(65536);
      }
      hackBuf.clear();
      byte[] dataBuf = hackBuf.array();
      int myOffset = 0;
      while (leftToRead > 0)
      {
        int currRead = Math.min((int)leftToRead, hackBuf.remaining());
        int numRead = xcodeStdout.read(dataBuf, myOffset, currRead);
        if (XCODE_DEBUG) System.out.println("Xcoder read " + numRead + " bytes directly from transcoder and is pushing it out");
        if (numRead == -1)
        {
          // EOF, use the overage buffer for the rest but also push what we have in ours
          initOverageBuffer();
          if (XCODE_DEBUG) System.out.println("XCoder sending overage for incomplete buffer read");
          overageBuf.limit((int)Math.min(leftToRead, overageBuf.capacity()));
          leftToRead -= chan.write(overageBuf);
          xcodeDone = true;
        }
        else
        {
          hackBuf.position(numRead);
          chan.write(hackBuf);
          hackBuf.clear();
          leftToRead -= numRead;
        }
      }
      xcodeBufferVirtualReadPos = xcodeBufferVirtualOffset = xcodeBufferVirtualSize = offset + length;
    }
  }

  protected void initOverageBuffer()
  {
    if (overageBuf == null)
    {
      overageBuf = java.nio.ByteBuffer.allocate(8192);
      byte[] overageFF = new byte[256];
      java.util.Arrays.fill(overageFF, 0, overageFF.length, (byte)0xFF);
      for (int i = 0; i < 8192; i += 256)
        overageBuf.put(overageFF);
    }
    overageBuf.clear();
  }

  public void setOutputFile(java.io.File theFile)
  {
    outputFile = theFile;
  }

  public void setSourceFile(String server, java.io.File theFile)
  {
    currFile = theFile;
    currServer = server;
  }

  public void setTranscodeFormat(sage.media.format.ContainerFormat inSourceFormat, sage.media.format.ContainerFormat newFormat)
  {
    sourceFormat = inSourceFormat;
    if (Sage.DBG) System.out.println("Set Transcode format source=" + sourceFormat + " dest=" + newFormat);
    xcodeParams = "";
    // Set the file format
    String newFormatName = substituteName(newFormat.getFormatName());
    // NOTE: Special case for Zune. It wants a .wmv file extension; but it's an ASF file type so
    // this is how we trick it (by allowing the 'wmv' format; but replacing it with asf here)
    if ("wmv".equals(newFormatName))
      newFormatName = "asf";
    xcodeParams += "-f " + newFormatName;

    String extraProps = newFormat.getMetadataProperty(sage.media.format.MediaFormat.META_COMPRESSION_DETAILS);

    boolean redoAudio = false;
    // Check for stream information
    if (newFormat.getNumberOfStreams() > 0)
    {
      sage.media.format.BitstreamFormat[] bfs = newFormat.getStreamFormats();
      boolean foundVideo = false;
      boolean foundAudio = false;
      boolean needAudioChannels = true;
      for (int i = 0; i < bfs.length; i++)
      {
        if (bfs[i] instanceof sage.media.format.AudioFormat)
        {
          sage.media.format.AudioFormat audformat = (sage.media.format.AudioFormat) bfs[i];
          foundAudio = true;
          String fname = bfs[i].getFormatName();
          if (fname == null || fname.length() == 0 || fname.equalsIgnoreCase("copy"))
          {
            //xcodeParams += " -acodec copy";
            redoAudio = true;
          }
          else
          {
            xcodeParams += " -acodec " + substituteName(fname);
          }
          if (audformat.getBitrate() > 0)
          {
            xcodeParams += " -ab " + (audformat.getBitrate() / 1000);
            preservedAudioBitrate = audformat.getBitrate();
          }
          if (audformat.getChannels() > 0)
          {
            needAudioChannels = false;
            xcodeParams += " -ac " + audformat.getChannels();
          }
          if (audformat.getSamplingRate() > 0)
            xcodeParams += " -ar " + audformat.getSamplingRate();
        }
        else if (bfs[i] instanceof sage.media.format.VideoFormat)
        {
          sage.media.format.VideoFormat vidformat = (sage.media.format.VideoFormat) bfs[i];
          foundVideo = true;
          String fname = bfs[i].getFormatName();
          if (fname == null || fname.length() == 0 || fname.equalsIgnoreCase("copy"))
          {
            xcodeParams += " -vcodec copy";
          }
          else
          {
            xcodeParams += " -vcodec " + substituteName(fname);
          }
          if (vidformat.getBitrate() > 0)
          {
            xcodeParams += " -b " + vidformat.getBitrate()/1000;
            preservedVideoBitrate = vidformat.getBitrate();
          }
          if (vidformat.getWidth() != 0 && vidformat.getHeight() != 0)
            xcodeParams += " -s " + vidformat.getWidth() + "x" + vidformat.getHeight();
          if (vidformat.getFps() > 0)
            xcodeParams += " -r " + vidformat.getFps();
          if (vidformat.getArNum() > 0 && vidformat.getArDen() > 0)
            xcodeParams += " -aspect " + vidformat.getArNum() + ":" + vidformat.getArDen();
        }
      }

      if (!foundVideo)
        xcodeParams += " -vn";
      if (!foundAudio)
        xcodeParams += " -an";
      if (redoAudio && (extraProps == null || extraProps.indexOf(" -acodec ") == -1) && xcodeParams.indexOf(" -acodec ") == -1)
      {
        // Add the audio codec parameters to re-encode the audio in the same format it's already in
        sage.media.format.AudioFormat af = sourceFormat.getAudioFormat();
        if (af != null)
        {
          String aformat = af.getFormatName();
          if (sage.media.format.MediaFormat.AC3.equalsIgnoreCase(aformat) ||
              sage.media.format.MediaFormat.MP2.equalsIgnoreCase(aformat) ||
              sage.media.format.MediaFormat.MP3.equalsIgnoreCase(aformat) ||
              sage.media.format.MediaFormat.AAC.equalsIgnoreCase(aformat))
          {
            xcodeParams += " -acodec " + substituteName(aformat);
          }
          else if (af.getChannels() <= 2)
            xcodeParams += " -acodec mp2";
          else
            xcodeParams += " -acodec ac3";
          if (af.getChannels() > 0)
            xcodeParams += " -ac " + Integer.toString(af.getChannels());
          if (af.getSamplingRate() > 0)
            xcodeParams += " -ar " + Integer.toString(af.getSamplingRate());
          // Don't blow the bitrate if the source is something like PCM
          if ((af.getBitrate()/1000) > 0 && (af.getBitrate()/1000) < 400)
          {
            xcodeParams += " -ab " + Integer.toString(af.getBitrate() / 1000);
            preservedAudioBitrate = af.getBitrate();
          }
          else if (sage.media.format.MediaFormat.AC3.equalsIgnoreCase(aformat) || xcodeParams.indexOf("-acodec ac3") != -1)
            xcodeParams += " -ab 384"; // for legacy bug where we didn't detect AC3 bitrate
          else //if (sage.media.format.MediaFormat.MP2.equalsIgnoreCase(aformat)) // we should always specify an audio bitrate
            xcodeParams += " -ab 192"; // for legacy bug where we didn't detect MP2 bitrate
        }
      }
      else if (needAudioChannels)
      {
        // Add the audio codec parameters to re-encode the audio in the same format it's already in
        sage.media.format.AudioFormat af = sourceFormat.getAudioFormat();
        if (af != null)
        {
          if (af.getChannels() > 0)
            xcodeParams += " -ac " + Integer.toString(af.getChannels());
        }
      }
      if (foundVideo && (extraProps == null || extraProps.indexOf(" -aspect ") == -1) && xcodeParams.indexOf(" -aspect ") == -1)
      {
        sage.media.format.VideoFormat vf = sourceFormat.getVideoFormat();
        if (vf != null)
        {
          if (vf.getArNum() > 0 && vf.getArDen() > 0)
            xcodeParams += " -aspect " + vf.getArNum() + ":" + vf.getArDen();
          else
            xcodeParams += " -aspect " + vf.getWidth() + ":" + vf.getHeight();
        }
      }
    }

    if (extraProps != null && extraProps.length() > 0)
      xcodeParams += " " + extraProps;

  }

  public void setTranscodeFormat(String str, sage.media.format.ContainerFormat inSourceFormat)
  {
    sourceFormat = inSourceFormat;
    if ("dynamic".equalsIgnoreCase(str))
      dynamicRateAdjust = true;
    else if ("dynamicts".equalsIgnoreCase(str))
    {
      iOSMode = true;
      dynamicRateAdjust = true;
    }
    else
    {
      xcodeParams = Sage.get(MediaServer.XCODE_QUALITIES_PROPERTY_ROOT + str, null);
      if (xcodeParams == null)
      {
        // The format itself probably contains the information we need
        String f = "dvd";
        String vcodec = "mpeg4";
        String s = MMC.getInstance().isNTSCVideoFormat() ? "352x240" : "352x288";
        // Workaround issue where AAC audio doesn't transcode properly to mono mp2
        String ac = (Sage.getBoolean("xcode_disable_mono_audio", true) ? "2" : "1");
        String g = "300";
        String bf = "2";
        String acodec = "mp2";
        String r = MMC.getInstance().isNTSCVideoFormat() ? "30" : "25";
        String b = "300";
        String ar = "48000";
        String ab = "64";
        String packetsize = "1024";
        boolean deinterlace = false;//true;
        java.util.StringTokenizer toker = new java.util.StringTokenizer(str, ";");
        while (toker.hasMoreTokens())
        {
          String currToke = toker.nextToken();
          int eqIdx = currToke.indexOf('=');
          if (eqIdx == -1)
            continue;
          String propName = currToke.substring(0, eqIdx);
          String propVal = currToke.substring(eqIdx + 1);
          try
          {
            if ("videocodec".equals(propName))
              vcodec = propVal;
            else if ("audiochannels".equals(propName))
            {
              //Only set property if the source audio has atleast as many channels as the setting
              if(Integer.parseInt(propVal) <= sourceFormat.getAudioFormat().getChannels())
                ac = propVal;
            }
            else if ("audiocodec".equals(propName))
              acodec = propVal;
            else if ("videobitrate".equals(propName))
            {
              preservedVideoBitrate = Integer.parseInt(propVal);
              b = Integer.toString(preservedVideoBitrate/1000);
            }
            else if ("audiobitrate".equals(propName))
            {
              preservedAudioBitrate = Integer.parseInt(propVal);
              ab = Integer.toString(preservedAudioBitrate/1000);
            }
            else if ("gop".equals(propName))
              g = propVal;
            else if ("bframes".equals(propName))
              bf = propVal;
            else if ("fps".equals(propName))
            {
              if("SOURCE".equals(propVal))
              {
                DecimalFormat twoDForm = new DecimalFormat("#.##");
                r = twoDForm.format(sourceFormat.getVideoFormat().getFps());
                g = (Math.round(sourceFormat.getVideoFormat().getFps()) * 10) + "";
              }
              else    
                r = propVal;
            }
            else if ("audiosampling".equals(propName))
              ar = propVal;
            else if ("resolution".equals(propName))
            {
              if ("D1".equals(propVal))
              {
                s = MMC.getInstance().isNTSCVideoFormat() ? "720x480" : "720x576";
                deinterlace = false;
              }
              else if("720".equals(propVal))
                s = "1280x720";
              else if("1080".equals(propVal))
                s = "1920x1080";
              else if("SOURCE".equals(propVal))
                s = inSourceFormat.getVideoFormat().getWidth() + "x" + inSourceFormat.getVideoFormat().getHeight();
              else
                s = MMC.getInstance().isNTSCVideoFormat() ? "352x240" : "352x288";
            }
            else if ("container".equals(propName))
              f = propVal;
          }
          catch (NumberFormatException e)
          {}
        }
        
        xcodeParams = "-f " + f;
        
        if(vcodec.equals("COPY"))
        {
          xcodeParams += " -vcodec copy";
        }
        else
        {
          xcodeParams += " -vcodec " + vcodec  + " -b " + b + " -r " + r + " -s " + s  + " -g " + g + " -bf " + bf + (deinterlace ? " -deinterlace " : "");
        }
        
        if(acodec.equals("COPY"))
        {
          xcodeParams += " -acodec copy";
        }
        else
        {
          xcodeParams += " -acodec " + acodec + " -ab " + ab + " -ar " + ar  + " -ac " + ac;
        }
        
        xcodeParams += " -packetsize " + packetsize;
        
      }
      dynamicRateAdjust = false;
    }
  }

  public static String getTranscoderPath()
  {
    if (new java.io.File(Sage.getToolPath("SageTVTranscoder")).isFile())
      return Sage.getToolPath("SageTVTranscoder");
    else if (new java.io.File(Sage.getToolPath("ffmpeg")).isFile())
      return Sage.getToolPath("ffmpeg");
    else
      throw new RuntimeException("Transcoder executable is missing!!! checked at: " + Sage.getToolPath("SageTVTranscoder") + " and " + Sage.getToolPath("ffmpeg"));
  }

  public void startTranscode() throws java.io.IOException
  {
    xcodeBufferBaseNum = 0;
    lastExitCode = -1;

    java.util.ArrayList xcodeParamsVec = new java.util.ArrayList();
    // Reduce process priority this way on non-windows platforms
    if (!Sage.WINDOWS_OS && Sage.getBoolean("xcode_reduce_process_priority", true))
      xcodeParamsVec.add("nice");
    // Find the transcoder engine
    xcodeParamsVec.add(getTranscoderPath());

    currStreamOverheadPerct = 0.10f; // about 10% for MPEG 2 program stream

    // ORDER OF PARAMETERS MATTERS A LOT FOR FFMPEG.
    // 1. We have to put the input filename before the codec information or it won't obey it
    // 2. We have to put itsoffset before the input filename or it won't obey it

    // To specify stream mapping, we list the streams we want in the output. Each stream needs a -map parameter.
    // The video should be first, and then the audio.

    if (transcodeStartSeekTime != 0)
    {
      xcodeParamsVec.add("-ss");
      xcodeParamsVec.add(Long.toString(transcodeStartSeekTime/1000));

      // Narflex: further testing on 3/27/07 shows this isn't needed anymore, so we're disabling it.
      // We're also changing the dts_delta_threshold so the timestamps get reset appropriately if we're seeking close to the front
      /*			if (transcodeStartSeekTime < 15000)
			{
				xcodeParamsVec.add("-dts_delta_threshold");
				xcodeParamsVec.add("2");
			}
       */
      // NOTE: Ugly hack!
      // From testing the itsoffset parameter is needed for anything but an MPEG source or WMA
      // BUT we can't use it if we're in copyts mode
      /*String fileLC = currFile.toString().toLowerCase();
			if (!fileLC.endsWith(".mpg") && !fileLC.endsWith(".ts") && !fileLC.endsWith(".mpeg") && !fileLC.endsWith(".vob") &&
				!fileLC.endsWith(".wma") && (xcodeParams == null || xcodeParams.indexOf("-copyts") == -1))
			{
				xcodeParamsVec.add("-itsoffset");
				xcodeParamsVec.add(Long.toString(transcodeStartSeekTime/1000));
			}*/
    }
    if (httplsMode)
      segmentTargetCounter = (int)(transcodeStartSeekTime / segmentDur);

    xcodeParamsVec.add("-v");
    xcodeParamsVec.add("3");

    xcodeParamsVec.add("-y");

    if(multiThread) {
      // decode gets one thread, emphasis on encoding...let's try two, should help with H264 decode
      xcodeParamsVec.add("-threads");
      xcodeParamsVec.add("2");
    }

    // We never transcode subtitles, so disable them if they exist
    xcodeParamsVec.add("-sn");

    // Set the flag to disable DTS parsing (which is broken in some HDPVR files) if its an MPEG2-TS w/ H264 video
    if (sourceFormat != null && sage.media.format.MediaFormat.MPEG2_TS.equals(sourceFormat.getFormatName()) &&
        sage.media.format.MediaFormat.H264.equals(sourceFormat.getPrimaryVideoFormat()) &&
        sage.media.format.MediaFormat.AC3.equals(sourceFormat.getPrimaryAudioFormat()) &&
        Sage.getBoolean("xcode_fix_broken_hdpvr_streams", false))
      xcodeParamsVec.add("-brokendts");

    // Establish these index points to insert sync parameters later
    int syncIndexInsert = xcodeParamsVec.size();
    xcodeParamsVec.add("");
    xcodeParamsVec.add("");
    xcodeParamsVec.add("");
    xcodeParamsVec.add("");

    // We need a very high bitrate tolerance in order to prevent FFMPEG from trying to compensate for our adaptive bitrate changes.
    // This is limited by 32-bits
    // UPDATE: I'm not really sure what's best here. If we go high, then there'll be more changes in bitrate which won't
    // deal as well with our optimization to minimize delay while maximizing bandwidth usage. But if we go low then there's very
    // perceivable changes in quality that are very distracting (when I tried 10, it was pretty bad)
    if (dynamicRateAdjust)
    {
      //			xcodeParamsVec.add("-bt");
      //			xcodeParamsVec.add("10000000");
    }

    if (transcodeEditDuration > 0)
    {
      xcodeParamsVec.add("-t");
      xcodeParamsVec.add(Long.toString(transcodeEditDuration/1000));
    }

    if (activeFile)
      xcodeParamsVec.add("-activefile");

    xcodeParamsVec.add("-stdinctrl");

    // Having this on puts us in too much danger of underflow since it doesn't give us enough control
    //if (Sage.getBoolean("media_server/dont_transcode_faster_than_realtime", true))
    //	xcodeParamsVec.add("-re");

    int targetWidth=720,targetHeight=480;
    sage.media.format.VideoFormat srcVideo = sourceFormat == null ? null : sourceFormat.getVideoFormat();
    if (srcVideo != null)
    {
      targetWidth = srcVideo.getWidth();
      targetHeight = srcVideo.getHeight();
    }

    String videoCodec = "";

    xcodeParamsVec.add("-i");
    if (currServer == null || currServer.length() == 0)
      xcodeParamsVec.add(IOUtils.getLibAVFilenameString(currFile.toString()));
    else
      xcodeParamsVec.add(IOUtils.getLibAVFilenameString("stv://" + currServer + "/" + currFile.toString()));

    // output file threading (encode)
    int numThreads = Sage.getInt("xcode_process_num_threads", 0);
    if (numThreads == 0)
    {
      try
      {
        numThreads = Runtime.getRuntime().availableProcessors() + 1;
      }
      catch (Throwable t)
      {
        System.out.println("ERROR calling " + Runtime.getRuntime().availableProcessors() + " of " + t);
        numThreads = 3;
      }
    }
    if (numThreads > 1 && multiThread)
    {
      // FFMPEG cannot handle more than 8 threads; now that we use 2 for decode...change this to 7
      numThreads = Math.min(7, numThreads);
      if (Sage.DBG) System.out.println("Using " + numThreads + " threads for the transcoder");
      xcodeParamsVec.add("-threads");
      xcodeParamsVec.add(Integer.toString(numThreads));
    }

    int currFps = 30;
    int qmin = 1;
    boolean isMpeg4Codec = false;
    if (httplsMode)
    {
      isMpeg4Codec = true;
      // Add the parameters for dynamic bitrate control
      xcodeParamsVec.add("-f");
      xcodeParamsVec.add("mpegts");
      xcodeParamsVec.add("-vcodec");
      xcodeParamsVec.add(videoCodec = "libx264");
      String sizeKey = String.format(BITRATE_OPTIONS_SIZE_KEY, estimatedBandwidth/1000);
      String xcodeSize = Sage.get(sizeKey, Sage.get(String.format(BITRATE_OPTIONS_SIZE_KEY, "default"), "480x272"));
      if (Sage.DBG)
        System.out.println("FFMpegTranscoder: httpls: Using framesize "+xcodeSize+" for bandwidth: "+(estimatedBandwidth/1000)+" base on key: " + sizeKey);
      // this will always return a valid 2 element array of w and h
      int size[] = parseFrameSize(xcodeSize, 480, 272);
      if (Sage.DBG)
        System.out.println("FFMpegTranscoder: httpls: Calculated framesize " + size[0] + "x" + size[1]);
      targetWidth = size[0];
      targetHeight = size[1];
      currAudioBitrateKbps = 32;
      currVideoBitrateKbps = (int)Math.max(64000, (estimatedBandwidth - 32000))/1000;
      xcodeParamsVec.add("-b");
      xcodeParamsVec.add(currVideoBitrateKbps*1000 + "");
      xcodeParamsVec.add("-s");
      xcodeParamsVec.add(targetWidth + "x" + targetHeight);
      xcodeParamsVec.add("-r");
      // Trying to lower the frame rate here caused problems...
      xcodeParamsVec.add("29.97");
      xcodeParamsVec.add("-acodec");
      xcodeParamsVec.add("libfaac");
      xcodeParamsVec.add("-ab");
      xcodeParamsVec.add(Integer.toString(currAudioBitrateKbps * 1000)); // FFMPEG takes audio in bits/sec now
      xcodeParamsVec.add("-ac");
      xcodeParamsVec.add("2");
      xcodeParamsVec.add("-ar");
      xcodeParamsVec.add("44100");
      xcodeParamsVec.add("-coder");
      xcodeParamsVec.add("0");
      xcodeParamsVec.add("-flags");
      xcodeParamsVec.add("+loop");
      xcodeParamsVec.add("-cmp");
      xcodeParamsVec.add("+chroma");
      xcodeParamsVec.add("-partitions");
      xcodeParamsVec.add("+parti8x8+parti4x4-partp8x8-partb8x8");
      xcodeParamsVec.add("-me_method");
      xcodeParamsVec.add("dia");
      xcodeParamsVec.add("-subq");
      xcodeParamsVec.add("1");
      xcodeParamsVec.add("-me_range");
      xcodeParamsVec.add("16");
      xcodeParamsVec.add("-g");
      xcodeParamsVec.add("250");
      xcodeParamsVec.add("-keyint_min");
      xcodeParamsVec.add("25");
      xcodeParamsVec.add("-sc_threshold");
      xcodeParamsVec.add("40");
      xcodeParamsVec.add("-i_qfactor");
      xcodeParamsVec.add("0.71");
      xcodeParamsVec.add("-b_strategy");
      xcodeParamsVec.add("1");
      xcodeParamsVec.add("-qcomp");
      xcodeParamsVec.add("0.6");
      xcodeParamsVec.add("-qmin");
      xcodeParamsVec.add("10");
      xcodeParamsVec.add("-qmax");
      xcodeParamsVec.add("51");
      xcodeParamsVec.add("-qdiff");
      xcodeParamsVec.add("4");
      xcodeParamsVec.add("-bf");
      xcodeParamsVec.add("0");
      xcodeParamsVec.add("-refs");
      xcodeParamsVec.add("1");
      xcodeParamsVec.add("-directpred");
      xcodeParamsVec.add("1");
      xcodeParamsVec.add("-trellis");
      xcodeParamsVec.add("0");
      xcodeParamsVec.add("-flags2");
      xcodeParamsVec.add("-wpred-dct8x8");
      xcodeParamsVec.add("-wpredp");
      xcodeParamsVec.add("0");
      xcodeParamsVec.add("-rc_lookahead");
      xcodeParamsVec.add("50");
      xcodeParamsVec.add("-level");
      xcodeParamsVec.add("30");
      xcodeParamsVec.add("-maxrate");
      xcodeParamsVec.add(currVideoBitrateKbps*6000/5 + "");
      xcodeParamsVec.add("-bufsize");
      xcodeParamsVec.add(currVideoBitrateKbps*5000 + "");

      if (xcodeParams.indexOf("-deinterlace") == -1 && srcVideo != null && srcVideo.isInterlaced() && targetHeight > srcVideo.getHeight()/2 &&
          Sage.getBoolean("xcode_auto_deinterlace", true))
      {
        if (Sage.DBG) System.out.println("Automatically adding -deinterlace option to transcoding process");
        xcodeParamsVec.add("-deinterlace");
      }

      // Preserve aspect ratio properly
      if (sourceFormat != null)
      {
        sage.media.format.VideoFormat vidForm = sourceFormat.getVideoFormat();
        if (vidForm != null && ((vidForm.getArNum() > 0 && vidForm.getArDen() > 0) || (vidForm.getWidth() > 0 && vidForm.getHeight() > 0)))
        {
          xcodeParamsVec.add("-aspect");
          if (vidForm.getArNum() > 0 && vidForm.getArDen() > 0)
            xcodeParamsVec.add(vidForm.getArNum() + ":" + vidForm.getArDen());
          else
            xcodeParamsVec.add(vidForm.getWidth() + ":" + vidForm.getHeight());
        }
      }
    }
    else if (dynamicRateAdjust)
    {
      isMpeg4Codec = true;
      // Add the parameters for dynamic bitrate control
      xcodeParamsVec.add("-f");
      xcodeParamsVec.add(iOSMode ? "mpegts" : "dvd");
      xcodeParamsVec.add("-vcodec");
      xcodeParamsVec.add(videoCodec = "mpeg4");
      xcodeParamsVec.add("-s");
      xcodeParamsVec.add(MMC.getInstance().isNTSCVideoFormat() ? "352x240" : "352x288");
      targetWidth = 352;
      targetHeight = MMC.getInstance().isNTSCVideoFormat() ? 240 : 288;
      xcodeParamsVec.add("-ac");
      // Workaround issue where AAC audio doesn't transcode properly to mono mp2
      xcodeParamsVec.add(Sage.getBoolean("xcode_disable_mono_audio", true) ? "2" : "1");
      xcodeParamsVec.add("-g");
      xcodeParamsVec.add("300");
      xcodeParamsVec.add("-bf");
      xcodeParamsVec.add("2");
      //xcodeParamsVec.add("-deinterlace");
      xcodeParamsVec.add("-acodec");
      xcodeParamsVec.add(iOSMode ? "libfaac" : Sage.get("xcode_dynamic_audio_codec", "mp2"));
      int currAudioSampling, currPacketSize;
      // Fast start is very important so always start at the bottom for video bitrate
      if (estimatedBandwidth < 90000)
      {
        if (currVideoBitrateKbps == -1)
          currVideoBitrateKbps = 50;
        if (currAudioBitrateKbps == -1)
          currAudioBitrateKbps = 24;
        // 10fps at 352x240
        currFps = 10;
        currAudioSampling = 24000;
        currPacketSize = 1024;
        qmin = 10;
      }
      else if (estimatedBandwidth < 150000)
      {
        if (currVideoBitrateKbps == -1)
          currVideoBitrateKbps = 64;//192;
        if (currAudioBitrateKbps == -1)
          currAudioBitrateKbps = 48;
        // 15fps at 352x240
        currFps = 15;
        currAudioSampling = 24000;
        currPacketSize = 1024;
        qmin = 5;
      }
      else if (estimatedBandwidth < 900000)
      {
        if (currVideoBitrateKbps == -1)
          currVideoBitrateKbps = (int)estimatedBandwidth/2000;//128;//256;
        if (currAudioBitrateKbps == -1)
          currAudioBitrateKbps = 64;
        // 15fps at 352x240
        currFps = 15;
        currAudioSampling = 48000;
        currPacketSize = 2048;
      }
      else
      {
        if (currVideoBitrateKbps == -1)
          currVideoBitrateKbps = Math.min(1000, (int)estimatedBandwidth/2000);//192;//384;
        if (currAudioBitrateKbps == -1)
          currAudioBitrateKbps = 128; // There's issues with using 96Kbps audio encoding I discovered
        // 30fps at 352x240 and 48kHz audio at 96Kbps
        currFps = MMC.getInstance().isNTSCVideoFormat() ? 30 : 25;
        currAudioSampling = 48000;
        currPacketSize = 2048;
      }

      xcodeParamsVec.add("-r");
      xcodeParamsVec.add(Integer.toString(currFps));
      xcodeParamsVec.add("-b");
      xcodeParamsVec.add(Integer.toString(currVideoBitrateKbps * 1000)); // FFMPEG takes video in bits/sec now
      xcodeParamsVec.add("-ar");
      xcodeParamsVec.add(Integer.toString(currAudioSampling));
      xcodeParamsVec.add("-ab");
      xcodeParamsVec.add(Integer.toString(currAudioBitrateKbps * 1000)); // FFMPEG takes audio in bits/sec now
      xcodeParamsVec.add("-packetsize");
      xcodeParamsVec.add(Integer.toString(currPacketSize));

      // Preserve aspect ratio properly
      if (sourceFormat != null)
      {
        sage.media.format.VideoFormat vidForm = sourceFormat.getVideoFormat();
        if (vidForm != null && ((vidForm.getArNum() > 0 && vidForm.getArDen() > 0) || (vidForm.getWidth() > 0 && vidForm.getHeight() > 0)))
        {
          xcodeParamsVec.add("-aspect");
          if (vidForm.getArNum() > 0 && vidForm.getArDen() > 0)
            xcodeParamsVec.add(vidForm.getArNum() + ":" + vidForm.getArDen());
          else
            xcodeParamsVec.add(vidForm.getWidth() + ":" + vidForm.getHeight());
        }
      }
    }
    else
    {
      int flagsIndex = -1;
      java.util.StringTokenizer toker = new java.util.StringTokenizer(xcodeParams);
      while (toker.hasMoreTokens())
      {
        String currToke = toker.nextToken();
        xcodeParamsVec.add(currToke);
        if (currToke.equals("-b") && toker.hasMoreTokens())
        {
          currToke = toker.nextToken();
          try
          {
            currVideoBitrateKbps = Integer.parseInt(currToke);  // FFMPEG takes video in bits/sec now
            if (preservedVideoBitrate > 0)
              xcodeParamsVec.add(Integer.toString(preservedVideoBitrate));
            else
              xcodeParamsVec.add(Integer.toString(currVideoBitrateKbps * 1000));
          }catch (NumberFormatException e)
          {
            System.out.println("Bad video bitrate parsed of " + currToke + " err:" + e);
            xcodeParamsVec.add(currToke);
          }
        }
        else if (currToke.equals("-ab") && toker.hasMoreTokens())
        {
          currToke = toker.nextToken();
          try
          {
            currAudioBitrateKbps = Integer.parseInt(currToke);  // FFMPEG takes audio in bits/sec now
            if (preservedAudioBitrate > 0)
              xcodeParamsVec.add(Integer.toString(preservedAudioBitrate));
            else
              xcodeParamsVec.add(Integer.toString(currAudioBitrateKbps * 1000));
          }catch (NumberFormatException e)
          {
            System.out.println("Bad audio bitrate parsed of " + currToke + " err:" + e);
          }
        }
        else if (currToke.equals("-r") && toker.hasMoreTokens())
        {
          currToke = toker.nextToken();
          xcodeParamsVec.add(currToke);
          try
          {
            currFps = Math.round(Float.parseFloat(currToke));
          }catch (NumberFormatException e)
          {
            System.out.println("Bad fps parsed of " + currToke + " err:" + e);
          }
        }
        else if (currToke.equals("-vcodec") && toker.hasMoreTokens())
        {
          currToke = videoCodec = toker.nextToken();
          xcodeParamsVec.add(currToke);
          if (currToke.equals("mpeg4"))
            isMpeg4Codec = true;
        }
        else if (currToke.equals("-s") && toker.hasMoreTokens())
        {
          currToke = toker.nextToken();
          xcodeParamsVec.add(currToke);
          try
          {
            targetWidth = Integer.parseInt(currToke.substring(0, currToke.indexOf('x')));
            targetHeight = Integer.parseInt(currToke.substring(currToke.indexOf('x') + 1));
          }catch (NumberFormatException e)
          {
            System.out.println("Bad target size parsed of " + currToke + " err:" + e);
          }
        }
        else if (currToke.equals("-vn"))
        {
          currVideoBitrateKbps = 0;
        }
        else if (currToke.equals("-an"))
        {
          currAudioBitrateKbps = 0;
        }
        else if (currToke.equals("-flags"))
        {
          flagsIndex = xcodeParamsVec.size();
        }
      }
      if (xcodeParams.indexOf("-aspect") == -1 && sourceFormat != null)
      {
        // Preserve aspect ratio properly
        sage.media.format.VideoFormat vidForm = sourceFormat.getVideoFormat();
        if (vidForm != null && ((vidForm.getArNum() > 0 && vidForm.getArDen() > 0) || (vidForm.getWidth() > 0 && vidForm.getHeight() > 0)))
        {
          xcodeParamsVec.add("-aspect");
          if (vidForm.getArNum() > 0 && vidForm.getArDen() > 0)
            xcodeParamsVec.add(vidForm.getArNum() + ":" + vidForm.getArDen());
          else
            xcodeParamsVec.add(vidForm.getWidth() + ":" + vidForm.getHeight());
        }
      }
      if (xcodeParams.indexOf("-deinterlace") == -1 && srcVideo != null && srcVideo.isInterlaced() && targetHeight > srcVideo.getHeight()/2 &&
          Sage.getBoolean("xcode_auto_deinterlace", true))
      {
        if (Sage.DBG) System.out.println("Automatically adding -deinterlace option to transcoding process");
        xcodeParamsVec.add("-deinterlace");
      }
      // Creating interlaced video doesn't work properly yet...
      /*if (xcodeParams.indexOf("-deinterlace") == -1 && srcVideo != null && srcVideo.isInterlaced() && targetHeight == srcVideo.getHeight())
			{
				if (Sage.DBG) System.out.println("Automatically adding interlacing option to transcoding process");
				xcodeParamsVec.add("-interlace");
				xcodeParamsVec.add("1");
				// Setup the proper flags
				if (flagsIndex == -1)
				{
					xcodeParamsVec.add("-flags");
					xcodeParamsVec.add("+ilme+ildct");
				}
				else
				{
					String currFlags = xcodeParamsVec.get(flagsIndex).toString();
					if (currFlags.indexOf("+ilme") == -1)
						currFlags += "+ilme";
					if (currFlags.indexOf("+ildct") == -1)
						currFlags += "+ildct";
					xcodeParamsVec.set(flagsIndex, currFlags);
				}
				// We may also need to specify something regarding top field first or not....
			}*/
    }

    if (currVideoBitrateKbps == -1)
      currVideoBitrateKbps = 200; // the default for FFMPEG
    if (currAudioBitrateKbps == -1)
      currAudioBitrateKbps = 64; // the default for FFMPEG

    // This sets the initial complexity for the rate control algorithms. Without it, there'll be big spikes whenever we reset
    // it or at the beginning.
    if (isMpeg4Codec && outputFile == null && !httplsMode) // don't do rate control opts if we're not streaming
    {
      xcodeParamsVec.add("-muxrate");
      xcodeParamsVec.add("2000000"); // really high to prevent underflow errors TESTING
      xcodeParamsVec.add("-rc_init_cplx");
      int complexity = (currVideoBitrateKbps * 8000/currFps) / (((targetWidth + 15)/16) * ((targetHeight + 15)/16));
      xcodeParamsVec.add(Integer.toString(complexity));
      xcodeParamsVec.add("-maxrate"); // FFMPEG takes video in bits/sec now
      xcodeParamsVec.add(Integer.toString(currVideoBitrateKbps * 1000));
      xcodeParamsVec.add("-minrate");
      xcodeParamsVec.add("0"); // For CBR this should be the same as max rate, but it's OK to go lower and if we don't make this 0, then qmin causes an A/V gap in the muxing
      xcodeParamsVec.add("-bufsize");
      xcodeParamsVec.add(Integer.toString(currVideoBitrateKbps * 1000)); // the rate control buffer averages over a 1 second period, it's in bits (used to be Kbytes)
      xcodeParamsVec.add("-mbd");
      xcodeParamsVec.add("2"); // rate distortion macroblock decisions
      if (dynamicRateAdjust)
      {
        // adding isB*75 helps with pulsing at the P-frame rate a lot compared to isB*25, it's noticable in detailed areas when there's temporarily not action
        // during an action scene
        xcodeParamsVec.add("-rc_eq");
        xcodeParamsVec.add("isI*200+isP*75+isB*75"); // rate control equation for CBR that balances I & P frame bits well
        if (qmin > 1)
        {
          xcodeParamsVec.add("-qmin");
          xcodeParamsVec.add(Integer.toString(qmin));
        }
      }
    }

    // See if we've got an unsupported audio stream
    if (sourceFormat != null)
    {
      String aud = sourceFormat.getPrimaryAudioFormat();
      if (aud != null && aud.startsWith("0X"))
      {
        if (Sage.DBG) System.out.println("Disabling audio in transcoder since it's an unsupported audio format");
        xcodeParamsVec.add("-an");
        currAudioBitrateKbps = 0;
      }
    }

    // See if there's multiple audio streams which means we need to setup stream mappings. But
    // we can only setup stream mappings if we have index information in the format.
    if (currAudioBitrateKbps > 0 && sourceFormat != null && sourceFormat.getNumAudioStreams() > 1 && currVideoBitrateKbps > 0)
    {
      // Get the FFMPEG only format so we can go off the stream indexes that it wants for transcoding
      sage.media.format.ContainerFormat ffFormat = sage.media.format.FormatParser.getFFMPEGFileFormat(currFile.toString());
      if (ffFormat != null)
      {
        sage.media.format.VideoFormat vf = ffFormat.getVideoFormat();
        if (vf != null && vf.getOrderIndex() >= 0)
        {
          // Don't select HD audio streams as the source
          sage.media.format.AudioFormat[] srcAudioFormats = sourceFormat.getAudioFormats();
          sage.media.format.AudioFormat srcAudioFormat = null;
          for (int i = 0; i < srcAudioFormats.length; i++)
          {
            if (!srcAudioFormats[i].getFormatName().equals(sage.media.format.MediaFormat.DOLBY_HD) &&
                !srcAudioFormats[i].getFormatName().equals(sage.media.format.MediaFormat.DTS_HD) &&
                !srcAudioFormats[i].getFormatName().equals(sage.media.format.MediaFormat.DTS_MA))
            {
              srcAudioFormat = srcAudioFormats[i];
              break;
            }
          }

          // Find the FFMPEG audio format that has the same stream ID as our main audio format
          if (srcAudioFormat == null)
            srcAudioFormat = sourceFormat.getAudioFormat();
          String mainsrcid = srcAudioFormat.getId();
          boolean isAC3 = sage.media.format.MediaFormat.AC3.equals(srcAudioFormat.getFormatName());
          sage.media.format.AudioFormat af = null;
          if (mainsrcid != null)
          {
            sage.media.format.AudioFormat[] afs = ffFormat.getAudioFormats();
            for (int i = 0; i < afs.length; i++)
            {
              if (mainsrcid.equals(afs[i].getId()) ||
                  (isAC3 && mainsrcid.startsWith("bd-" + afs[i].getId())))
              {
                af = afs[i];
                break;
              }
            }
          }
          if (af == null)
            af = ffFormat.getAudioFormat();
          if (af != null && af.getOrderIndex() >= 0)
          {
            xcodeParamsVec.add("-map");
            xcodeParamsVec.add("0:" + vf.getOrderIndex());
            xcodeParamsVec.add("-map");
            xcodeParamsVec.add("0:" + af.getOrderIndex());
          }
        }
      }
    }

    // NOTE: Don't use interlaced ME/DCT on MPEG4 content
    // Quicktime/iPod doesn't playback files with interlaced ME/DCT so we can't just go enabling it all the time
    if (sourceFormat != null && sourceFormat.getVideoFormat() != null && sourceFormat.getVideoFormat().isInterlaced() &&
        "mpeg2video".equals(videoCodec))
    {
      xcodeParamsVec.add("-flags");
      xcodeParamsVec.add("ildct");
      xcodeParamsVec.add("-flags");
      xcodeParamsVec.add("ilme");
    }

    // Check for multi-pass encoding
    if (pass != 0)
    {
      xcodeParamsVec.add("-pass");
      xcodeParamsVec.add(Integer.toString(pass));
      xcodeParamsVec.add("-passlogfile");
      xcodeParamsVec.add("multipassxcode");
    }

    // We only want to use these sync parameters if we're doing dynamic adjustment placeshifting
    // Although, I'm pretty sure we want to switch to the other set of params, but we need more testing before we do that
    // NOTE: 10/16/06 - the other set of params totally screw up our A/V sync for fixed rate placeshifting @ 15fps !!!!
    if (dynamicRateAdjust || (isMpeg4Codec && outputFile == null))
    {
      xcodeParamsVec.set(syncIndexInsert, "-vsync");
      // For AVI source files we need to allow video frame dropping for it to get proper initial sync if there was
      // also a seek
      // NARFLEX: 4/2/09 - using 'vsync 1' fixes a new bug where we have an error if we try to start transcoding in the middle
      // of an MKV file; so we're adding that to this case
      // NARFLEX: 10/29/10 - For frame decimation, we need to do -vsync 1 or we won't be able to drop frames for the h264 encoder properly
      if (httplsMode || (transcodeStartSeekTime != 0 && sourceFormat != null && (sage.media.format.MediaFormat.AVI.equals(sourceFormat.getFormatName()) ||
          sage.media.format.MediaFormat.MATROSKA.equals(sourceFormat.getFormatName()))))
        xcodeParamsVec.set(syncIndexInsert + 1, "1");
      else
        xcodeParamsVec.set(syncIndexInsert + 1, "0");
      xcodeParamsVec.set(syncIndexInsert + 2, "-async");
      xcodeParamsVec.set(syncIndexInsert + 3, "1");
    }
    else //if (xcodeParams.indexOf("-f mp4") != -1 || xcodeParams.indexOf("-f 3gp") != -1 || xcodeParams.indexOf("-f psp") != -1)
    {
      xcodeParamsVec.set(syncIndexInsert, "-vsync");
      xcodeParamsVec.set(syncIndexInsert + 1, "1");
      xcodeParamsVec.set(syncIndexInsert + 2, "-async");
      xcodeParamsVec.set(syncIndexInsert + 3, "100");
    }

    if (Sage.DBG && "TRUE".equals(Sage.get("xcode_video_bitrate_stats", null)))
      xcodeParamsVec.add("-vstats");

    if (Sage.WINDOWS_OS && Sage.getBoolean("xcode_reduce_process_priority", true))
    {
      xcodeParamsVec.add("-priority");
      if (outputFile != null) // offline transcode
        xcodeParamsVec.add(Sage.get("xcode_process_priority_offline", "idle"));
      else
        xcodeParamsVec.add(Sage.get("xcode_process_priority_streaming", "belownormal"));
    }

    if (outputFile != null)
    {
      xcodeParamsVec.add(IOUtils.getLibAVFilenameString(outputFile.toString()));
      bufferOutput = false;
    }
    else
      xcodeParamsVec.add("-");
    String[] xcodeParamArray = (String[]) xcodeParamsVec.toArray(Pooler.EMPTY_STRING_ARRAY);
    // NOTE: While this debugging info would be highly useful, it's also highly proprietary what parameters we execute FFMPEG with
    if (Sage.DBG && "TRUE".equals(Sage.get("xcode_cmdline_debug", null))) System.out.println("Executing xcoding process with args: " + java.util.Arrays.asList(xcodeParamArray));
    xcodeProcess = Runtime.getRuntime().exec(xcodeParamArray);
    // We open up the error stream and consume that for status info. The transcoded data is consumed by reading
    // from stdout.
    xcodeDone = false;
    if (xcodeBuffer == null)
    {
      // Don't use properties for these because it leads to major inconsistencies between systems that are quite difficult to diagnose
      if (currVideoBitrateKbps >= 1000)
        xcodeBuffer = new byte[16][32768];
      else
        xcodeBuffer = new byte[32][currVideoBitrateKbps >= 300 ? 16384 : 4096];
    }
    xcodeStderrThread = new Thread("XcodeStderrConsumer")
    {
      public void run()
      {
        try
        {
          java.io.InputStream buf = xcodeProcess.getErrorStream();
          StringBuffer sb = new StringBuffer();
          long nextSegmentTime = segmentDur;
          if (httplsMode)
            lastXcodeStreamTime = 0;
          do
          {
            int c = buf.read();
            if (c == -1)
              break;
            else
              sb.append((char) c);
            if (c == '\n')
            {
              if (XCODE_DEBUG) System.out.println(sb.toString().trim());
              sb.setLength(0);
            }
            else if (c == '\r')
            {
              // Parse to get the byte position for the specified time
              if (XCODE_DEBUG) System.out.println(sb.toString().trim());
              int sizeIdx = sb.indexOf("size=");
              int timeIdx = sb.indexOf("time=");
              int kbIdx = sb.indexOf("kB", sizeIdx);
              int bitrateIdx = sb.indexOf("bitrate=");
              
              if (sizeIdx != -1 && timeIdx != -1 && kbIdx != -1 && bitrateIdx != -1)
              {
                String sizeStr = sb.substring(sizeIdx + 5, kbIdx).trim();
                String timeStr = sb.substring(timeIdx + 5, bitrateIdx).trim();
                
                if (sizeStr.indexOf('.') == -1)
                {
                  try
                  {
                    System.out.println("FFMPEG: " + sb.toString().trim());
                    System.out.println("timeStr: " + timeStr);
                    
                    lastXcodeStreamTime = Math.round(1000 * Double.parseDouble(timeStr));
                    lastXcodeStreamPosition = Long.parseLong(sizeStr) * 1024;
                    
                    System.out.println("lastXcodeStreamTime: " + lastXcodeStreamTime);
                    System.out.println("lastXcodeStreamPosition: " + lastXcodeStreamPosition);
                  }
                  catch (NumberFormatException e)
                  {
                    System.out.println("ERROR parsing transcoder time of:" + e);
                  }
                }
              }
              if (httplsMode)
              {
                if (lastXcodeStreamTime >= nextSegmentTime)
                {
                  synchronized (segFileSyncLock)
                  {
                    segmentTargetCounter++;
                    if (XCODE_DEBUG) System.out.println("Stderr reader has read a timecode that indicates end of segment, increment counter, target=" +
                        nextSegmentTime + " read=" + lastXcodeStreamTime + " newCounterValue=" + segmentTargetCounter);
                    segFileSyncLock.notifyAll();
                  }
                  nextSegmentTime += segmentDur;
                }
              }
              sb.setLength(0);
            }
          }while (true);
          buf.close();
        }
        catch (Exception e){}
        finally
        {
          xcodeDone = true;
        }
      }
    };
    xcodeStderrThread.setDaemon(true);
    xcodeStderrThread.start();
    numFilledXcodeBuffers = 0;
    xcodeStdout = xcodeProcess.getInputStream();
    forciblyStopped = false;
    if (bufferOutput)
    {
      xcodeStdoutThread = new Thread("XcodeDataConsumer")
      {
        public void run()
        {
          try
          {
            do
            {
              int currBuffNum;
              int currBufReadPos = 0;
              synchronized (xcodeSyncLock)
              {
                if (numFilledXcodeBuffers == xcodeBuffer.length && !xcodeDone)
                {
                  if (XCODE_DEBUG) System.out.println("Waiting for transcode buffer to become available...");
                  try
                  {
                    xcodeSyncLock.wait(100);
                  }
                  catch (InterruptedException e){}
                  continue;
                }
                currBuffNum = (xcodeBufferBaseNum + numFilledXcodeBuffers) % xcodeBuffer.length;
              }
              int leftToRead = xcodeBuffer[currBuffNum].length;
              int numRead;
              do
              {
                numRead = xcodeStdout.read(xcodeBuffer[currBuffNum], xcodeBuffer[currBuffNum].length - leftToRead, leftToRead);
                if (XCODE_DEBUG) System.out.println("Read " + numRead + " bytes from transcoder");
                leftToRead -= numRead;
              } while (numRead != -1 && leftToRead > 0);
              if (numRead == -1)
              {
                xcodeDone = true;
                break;
              }
              else
              {
                synchronized (xcodeSyncLock)
                {
                  numFilledXcodeBuffers++;
                  xcodeBufferVirtualSize += xcodeBuffer[currBuffNum].length;
                  if (XCODE_DEBUG) System.out.println("Number of transcode buffers filled=" + numFilledXcodeBuffers
                      + " virtXcodedBytes=" + xcodeBufferVirtualSize);
                }
              }
            }while (true);
          }
          catch (Exception e){}
          finally
          {
            xcodeDone = true;
          }
        }
      };
      xcodeStdoutThread.setDaemon(true);
      xcodeStdoutThread.start();
    }
    else if (httplsMode)
    {
      for (int i = 0; i < segmentData.length; i++)
      {
        segmentData[i].state = SEGMENT_FREE;
        segmentData[i].num = -1;
      }
      segmentData[0].state = SEGMENT_FILLING;
      segmentData[0].num = segmentTargetCounter;
      xcodeStdoutThread = new Thread("XcodeDataConsumer")
      {
        public void run()
        {
          byte[] readBuf;
          if (currVideoBitrateKbps >= 1000)
            readBuf = new byte[32768];
          else
            readBuf = new byte[currVideoBitrateKbps >= 300 ? 16384 : 4096];
          int lastDataIdx = -1;
          java.io.OutputStream fos = null;
          SegmentFileData currSegData = null;
          try
          {
            // First we need to have a segment file we can write to
            lastDataIdx = 0;
            currSegData = segmentData[0];
            if (XCODE_DEBUG) System.out.println("Output consumer selected initial segment buffer #" + lastDataIdx + " for writing of part #" + segmentTargetCounter);
            fos = new java.io.BufferedOutputStream(new java.io.FileOutputStream(currSegData.file));
            int numRead;
            do
            {
              numRead = xcodeStdout.read(readBuf);
              if (XCODE_DEBUG) System.out.println("Read " + numRead + " bytes from transcoder");
              fos.write(readBuf, 0, numRead);
              synchronized (segFileSyncLock)
              {
                if (currSegData.num != segmentTargetCounter)
                {
                  if (XCODE_DEBUG) System.out.println("Finished writing to current segment file buffer #" + currSegData.num + " for part #" +
                      currSegData.num + ", closing file and moving on");
                  fos.close();
                  fos = null;
                  currSegData.state = SEGMENT_FILLED;
                  // Move to the next segment now
                  lastDataIdx = (lastDataIdx + 1) % segmentData.length;
                  // See if our target is free
                  while (segmentData[lastDataIdx].state != SEGMENT_FREE && segmentData[lastDataIdx].state != SEGMENT_CONSUMED && !xcodeDone)
                  {
                    // Wait until it's free or the xcoder is stopped'
                    if (XCODE_DEBUG) System.out.println("Waiting for segment file buffer to become available...");
                    try
                    {
                      segFileSyncLock.wait(500);
                    }
                    catch (InterruptedException e){}
                  }
                  if (xcodeDone)
                    return;
                  if (XCODE_DEBUG) System.out.println("Output consumer selected segment buffer #" + lastDataIdx + " for writing of part #" + segmentTargetCounter);
                  currSegData = segmentData[lastDataIdx];
                  currSegData.state = SEGMENT_FILLING;
                  currSegData.num = segmentTargetCounter;
                  fos = new java.io.BufferedOutputStream(new java.io.FileOutputStream(currSegData.file));
                  segFileSyncLock.notifyAll();
                }
              }
            } while (numRead != -1 && !xcodeDone);
            if (numRead == -1 || xcodeDone)
            {
              xcodeDone = true;
            }
          }
          catch (Exception e){}
          finally
          {
            xcodeDone = true;
            if (fos != null)
            {
              try{fos.close();}catch(Exception e){}
              fos = null;
            }
            if (!forciblyStopped && currSegData != null && currSegData.state == SEGMENT_FILLING)
            {
              synchronized (segFileSyncLock)
              {
                // Mark our last buffer as filled because we stopped due to natural causes, not a reseek or kill
                currSegData.state = SEGMENT_FILLED;
                segFileSyncLock.notifyAll();
              }
            }
          }
        }
      };
      xcodeStdoutThread.setDaemon(true);
      xcodeStdoutThread.start();
    }

    xcodeStdin = xcodeProcess.getOutputStream();
    //try{Thread.sleep(Sage.getInt("media_server/xcode_start_delay", 1000));}catch (Exception e){}
  }

  /**
   * Given a string like, '1280x720' it will return a 2 element array where element 0 is width and element 1 is height.
   * If the string is unparseable, then it will return the defaults.
   * If the string is 'original' it will attempt to get the size from the original video stream.
   *
   * @param xcodeWxH
   * @param defWidth
   * @param defHeight
   * @return
   */
  int[] parseFrameSize(String xcodeWxH, int defWidth, int defHeight)
  {
    int size[] = new int[] {defWidth, defHeight};
    if (xcodeWxH == null)
    {
      return size;
    }

    xcodeWxH = xcodeWxH.toLowerCase();

    // if we pass 'original' then try to use the original size of the video
    if ("original".equals(xcodeWxH))
    {
      if (sourceFormat!=null && sourceFormat.getVideoFormat()!=null)
      {
        size[0] = sourceFormat.getVideoFormat().getWidth();
        size[1] = sourceFormat.getVideoFormat().getHeight();
        size[0] = (size[0]<=0) ? defWidth : size[0];
        size[1] = (size[1]<=0) ? defHeight : size[1];
        return size;
      }
      else
      {
        if (Sage.DBG)
          System.out.println("FFMpegTranscoder: parseFrameSize(): 'original' was passed but there isn't any video information.  Using defaults.");
        return size;
      }
    }

    // need to parse widthxheight, ie, 1280x720
    String parts[] = xcodeWxH.split("x");
    if (parts.length != 2)
    {
      if (Sage.DBG)
        System.out.println("FFMpegTranscoder: parseFrameSize(): Invalid xcode size option "+xcodeWxH+" (should be widthxheight, eg, 1280x720)");
      return size;
    }

    int w,h;
    try
    {
      w = Integer.parseInt(parts[0].trim());
    }
    catch (Throwable t)
    {
      if (Sage.DBG)
        System.out.println("FFMpegTranscoder: parseFrameSize(): Invalid xcode size option "+xcodeWxH+" (should be widthxheight, eg, 1280x720)");
      return size;
    }

    try
    {
      h = Integer.parseInt(parts[1].trim());
    }
    catch (Throwable t)
    {
      if (Sage.DBG)
        System.out.println("FFMpegTranscoder: parseFrameSize(): Invalid xcode size option "+xcodeWxH+" (should be widthxheight, eg, 1280x720)");
      return size;
    }

    // great, we have a valid height and width
    if (h>0 && w>0)
    {
      size[0]=w;
      size[1]=h;
    }

    return size;
  }

  public void stopTranscode()
  {
    forciblyStopped = true;
    xcodeDone = true;
    if (XCODE_DEBUG) System.out.println("Destroying old transcode process...");
    if (xcodeProcess != null)
    {
      try
      {
        lastExitCode = xcodeProcess.exitValue();
      }
      catch (IllegalThreadStateException ise)
      {
        lastExitCode = -1;
      }
      xcodeProcess.destroy();
    }
    xcodeProcess = null;
    if (XCODE_DEBUG) System.out.println("Destroyed!");
    try
    {
      if (xcodeStderrThread != null)
      {
        xcodeStderrThread.join(2000);
        xcodeStderrThread = null;
        if (XCODE_DEBUG) System.out.println("Stderr consumer thread has terminated for xcoder");
      }
    }catch(InterruptedException e){}
    try
    {
      if (xcodeStdoutThread != null)
      {
        xcodeStdoutThread.join(2000);
        xcodeStdoutThread = null;
        if (XCODE_DEBUG) System.out.println("Stdout consumer thread has terminated for xcoder");
      }
    }catch(InterruptedException e){}
    try
    {
      if (xcodeStdout != null)
        xcodeStdout.close();
    }
    catch (java.io.IOException e){}
    xcodeStdout = null;
    try
    {
      if (xcodeStdin != null)
      {
        xcodeStdin.close();
      }
    }catch(java.io.IOException e){}
    xcodeStdin = null;

    // Delete any temporary segment files
    if (httplsMode && segmentData != null)
    {
      for (int i = 0; i < segmentData.length; i++)
        segmentData[i].file.delete();
    }
  }

  public void setEnableOutputBuffering(boolean x)
  {
    bufferOutput = x;
  }

  public void setActiveFile(boolean x)
  {
    if (activeFile != x)
    {
      activeFile = x;
      if (xcodeStdin != null && !activeFile)
      {
        try
        {
          xcodeStdin.write("inactivefile\n".getBytes(Sage.BYTE_CHARSET));
          xcodeStdin.flush();
        }
        catch (Exception e)
        {
          System.out.println("Error writing to xcoder stdin of:" + e);
        }
      }
    }
  }

  public void dynamicVideoRateAdjust(int kbpsAdjust)
  {
    if (xcodeStdin != null && !xcodeDone)
    {
      try
      {
        xcodeStdin.write(("videorateadapt " + kbpsAdjust + "\n").getBytes(Sage.BYTE_CHARSET));
        xcodeStdin.flush();
        currVideoBitrateKbps += kbpsAdjust;
        estimatedBandwidth += kbpsAdjust; // in case we seek, we want to use the newly selected bandwidth and not the old one
      }
      catch (Exception e)
      {
        System.out.println("Error writing to xcoder stdin of:" + e);
      }
    }
  }

  public int getCurrentVideoBitrateKbps()
  {
    return currVideoBitrateKbps;
  }

  public int getCurrentStreamBitrateKbps()
  {
    return Math.round(((currAudioBitrateKbps + currVideoBitrateKbps) * (1 + currStreamOverheadPerct)));
  }

  public boolean isTranscoding()
  {
    return !xcodeDone && xcodeProcess != null;
  }

  public void setEstimatedBandwidth(long bps)
  {
    estimatedBandwidth = bps;
  }

  // This'll convert from our internal format name back into what libav wants
  private static String substituteName(String s)
  {
    if (s == null) return null;
    // 5/5/08 - The AAC encoder in FFMPEG is now called 'libfaac'
    if ("aac".equalsIgnoreCase(s)) return "libfaac";
    // 5/20/08 - The XVID encoder in FFMPEG is now called 'libxvid'
    if ("xvid".equalsIgnoreCase(s)) return "libxvid";
    // 6/5/08 - The h264 encoder in FFMPEG is now called 'libx264'
    if ("h264".equalsIgnoreCase(s)) return "libx264";
    // Remove the MP3 encoder if it's being used because that's what the input file is
    if ("mp3".equalsIgnoreCase(s) && Sage.getBoolean("xcode_disable_mp3_encoder", true)) return "mp2";
    for (int i = 0; i < sage.media.format.FormatParser.FORMAT_SUBSTITUTIONS.length; i++)
      if (sage.media.format.FormatParser.FORMAT_SUBSTITUTIONS[i][1].equalsIgnoreCase(s) &&
          sage.media.format.FormatParser.FORMAT_SUBSTITUTIONS[i][0].indexOf('/') == -1)
        return sage.media.format.FormatParser.FORMAT_SUBSTITUTIONS[i][0];
    return s.toLowerCase();
  }

  public void setEditParameters(long startTime, long duration)
  {
    transcodeStartSeekTime = startTime;
    transcodeEditDuration = duration;
  }

  public void setPass(int x)
  {
    pass = x;
  }

  public void setThreadingEnabled(boolean x)
  {
    multiThread = x;
  }

  public void enableSegmentedOutput(int segmentDurMsec, java.io.File[] segFiles)
  {
    httplsMode = true;
    segmentData = new SegmentFileData[segFiles.length];
    for (int i = 0; i < segmentData.length; i++)
    {
      segmentData[i] = new SegmentFileData();
      segmentData[i].file = segFiles[i];
      segmentData[i].num = -1;
    }
    segmentDur = segmentDurMsec;
  }

  public java.io.File getSegmentFile(int segNum) throws java.io.IOException
  {
    // There's 3 cases here.
    // 1. The file is already filled and ready to return, the caller should call markSegmentConsumed when done with the file
    // 2. The file is being filled right now, so we block until it's done and then it's like #1
    // 3. We need to do a seek w/ the transcoder to get to the right part, then after we do that it's like #2
    synchronized (segFileSyncLock)
    {
      for (int i = 0; i < segmentData.length; i++)
        if (segmentData[i].num == segNum)
        {
          if (segmentData[i].state == SEGMENT_FILLED || segmentData[i].state == SEGMENT_CONSUMED || segmentData[i].state == SEGMENT_CONSUMING)
          {
            // Case 1
            if (XCODE_DEBUG) System.out.println("Part #" + segNum + " was requested from transcode, it's already filled, so returning the buffer file #" + i);
            segmentData[i].state = SEGMENT_CONSUMING;
            return segmentData[i].file;
          }
          else if (segmentData[i].state == SEGMENT_FILLING)
          {
            // Case 2
            while (!xcodeDone && segmentData[i].state == SEGMENT_FILLING && segmentData[i].num == segNum)
            {
              try
              {
                if (XCODE_DEBUG) System.out.println("Part #" + segNum + " was requested from transcode, it's currently filling, so wait before returning the buffer file #" + i);
                segFileSyncLock.wait(500);
              }
              catch (InterruptedException ioe){}
            }
            if (xcodeDone || segmentData[i].num != segNum ||
                (segmentData[i].state != SEGMENT_FILLED && segmentData[i].state != SEGMENT_CONSUMED && segmentData[i].state != SEGMENT_CONSUMING))
              return null;
            if (XCODE_DEBUG) System.out.println("Part #" + segNum + " was requested from transcode, it's filled now, so returning the buffer file #" + i);
            segmentData[i].state = SEGMENT_CONSUMING;
            return segmentData[i].file;
          }
        }
    }

    // The requested segment file is not being filled currently, this means we should seek the transcoder so it starts filling it immediately, then we wait
    // a bit before we request the segment again
    if (XCODE_DEBUG) System.out.println("Part #" + segNum + " was requested from transcode but it's buffer is not filling/filled, seek the transcoder now to " + (segNum * segmentDur));
    seekToTime(segNum * segmentDur);
    return getSegmentFile(segNum); // it should work this time
  }

  public void markSegmentConsumed(int segNum)
  {
    // This means this file is no longer in use, so we can do what we want with it
    synchronized (segFileSyncLock)
    {
      for (int i = 0; i < segmentData.length; i++)
        if (segmentData[i].num == segNum)
        {
          segmentData[i].state = SEGMENT_CONSUMED;
          segFileSyncLock.notifyAll();
          break;
        }
    }
  }

  protected String xcodeParams = "";
  protected boolean xcodeDone;
  protected Process xcodeProcess;
  protected boolean activeFile;
  protected java.io.OutputStream xcodeStdin;
  // This is a set of buffers used to read from the transcode stream and to also send out the data. We keep
  // one extra buffer behind us in case the client needs to re-read something.
  protected byte[][] xcodeBuffer;
  // This is the sync object for the counters used in the xocde buffering
  protected Object xcodeSyncLock = new Object();
  // This is the buffer index whose 0 position corresponds to the xcodeBufferVirtualOffset
  protected int xcodeBufferBaseNum;
  // This is the total number of bytes we are from the start of the virtual transcoded file
  protected long xcodeBufferVirtualOffset;
  // This is the number of xcode buffers that are currently filled with data
  protected int numFilledXcodeBuffers;
  // This is the total number of bytes that are available from the transcoder; it's
  // the virtualOffset + the number of bytes in the buffer
  protected long xcodeBufferVirtualSize;

  protected long xcodeBufferVirtualReadPos;

  protected long lastXcodeStreamTime;
  protected long lastXcodeStreamPosition;

  protected Thread xcodeStderrThread;
  protected Thread xcodeStdoutThread;

  protected java.nio.ByteBuffer hackBuf;
  protected java.nio.ByteBuffer overageBuf;

  protected java.io.File currFile;
  protected String currServer;
  protected java.io.File outputFile;

  protected long transcodeStartSeekTime;
  protected java.io.FileInputStream fileStream;
  protected java.nio.channels.FileChannel fileChannel;

  protected boolean bufferOutput;
  protected java.io.InputStream xcodeStdout;

  protected static final int SEGMENT_FREE = 0;
  protected static final int SEGMENT_FILLING = 1;
  protected static final int SEGMENT_FILLED = 2;
  protected static final int SEGMENT_CONSUMING = 3;
  protected static final int SEGMENT_CONSUMED = 4;

  protected SegmentFileData[] segmentData;
  protected int segmentDur;
  protected Object segFileSyncLock = new Object();
  protected int segmentTargetCounter; // this is the segment number we should be actively writing, it accounts for any seek offsets as well (those offsets will affect this number)

  protected int currVideoBitrateKbps = -1;
  protected int currAudioBitrateKbps = -1;
  protected float currStreamOverheadPerct;

  protected boolean dynamicRateAdjust = false;
  protected boolean iOSMode = false;
  protected long estimatedBandwidth;
  protected boolean httplsMode = false;

  protected int lastExitCode = -1;
  protected long transcodeEditDuration;
  protected boolean forciblyStopped;

  protected sage.media.format.ContainerFormat sourceFormat;

  protected int pass;

  protected int preservedAudioBitrate;
  protected int preservedVideoBitrate;

  protected boolean multiThread = true;

  protected byte[] nioTmpBuf;

  private static class SegmentFileData
  {
    public java.io.File file;
    public int state;
    public int num;
  }
}
