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
 * @author Narflex
 */
public class RemuxTranscodeEngine implements TranscodeEngine
{
  private static final boolean XCODE_DEBUG = Sage.DBG && Sage.getBoolean("media_server/transcode_debug", false);

  /** Creates a new instance of RemuxTranscodeEngine */
  public RemuxTranscodeEngine()
  {
  }

  public long getAvailableTranscodeBytes()
  {
    if (xcodeDone)
      return 0;
    else
      return Math.max(0, xcodeBufferVirtualSize - xcodeBufferVirtualReadPos - (xcodeBufferVirtualSize % xcodeBuffer[0].length));
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
    return xcodeDone;
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

  public long getCurrentTranscodeStreamTime()
  {
    return (muxy == null) ? 0 : muxy.getLastPTSMsec();
  }

  public void seekToPosition(long offset) throws java.io.IOException
  {
    if (XCODE_DEBUG) System.out.println("Seeking remuxer to pos: " + offset);
    if (!fillerAlive)
    {
      if (offset == 0)
        startTranscode();
      else
        throw new java.io.IOException("Cannot do seekToPosition in transcoder because it hasn't been started yet!");
      return;
    }
    if (offset == 0)
    {
      seekToTime(0);
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
    if (XCODE_DEBUG) System.out.println("Seeking remuxer to time: " + milliSeekTime);
    synchronized (xcodeSyncLock)
    {
      // Let this get handled on the buffer filler thread
      seekTarget = new Long(milliSeekTime);
      while (seekTarget != null && !xcodeDone)
      {
        try
        {
          xcodeSyncLock.wait(500);
        }catch (Exception e){}
      }
    }
  }

  public void sendTranscodeOutputToChannel(long offset, long length, java.nio.channels.WritableByteChannel chan) throws java.io.IOException
  {
    // We ignore the offset here because that's when this is used through the MediaServer and that only happens
    // w/ the FFMPEGTranscoder and NOT w/ the remuxer
    if (nioTmpBuf == null || nioTmpBuf.capacity() < length)
      nioTmpBuf = java.nio.ByteBuffer.allocate((int)length);
    nioTmpBuf.clear();
    nioTmpBuf.limit((int)length);
    readFullyTranscodedData(nioTmpBuf);
    chan.write(nioTmpBuf);
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
    // We do this because our format parser has a much better TS detector than the one in the FastMpeg2Reader
    if (inSourceFormat != null && sage.media.format.MediaFormat.MPEG2_TS.equals(inSourceFormat.getFormatName()))
      inputTS = true;
  }

  public void setTranscodeFormat(String str, sage.media.format.ContainerFormat inSourceFormat)
  {
    // We don't care
  }

  public void startTranscode() throws java.io.IOException
  {
    sourceFile = new FastMpeg2Reader(currFile, currServer);
    if (inputTS)
      sourceFile.setForcedTSSource(true);
    sourceFile.init(true, false, false);
    muxyBuffer = new RemuxOutputBuffer();
    muxy = sage.media.format.MPEGParser.openRemuxer(sage.media.format.MPEGParser.REMUX_PS, 0, muxyBuffer);
    if (muxy == null)
      throw new java.io.IOException("ERROR creating remuxer");

    xcodeBufferBaseNum = 0;
    if (xcodeBuffer == null)
      xcodeBuffer = new byte[16][32768];
    if (inputBuffer == null)
      inputBuffer = new byte[32768];

    // Init the remuxer
    while (true)
    {
      // Push more data into the remuxer and then read it back again above
      int maxWaits = 200;
      while (activeFile && !sourceFile.availableToRead(inputBuffer.length) && maxWaits-- > 0)
      {
        // Waiting for more data to appear in file
        try{Thread.sleep(50);}catch(Exception e){}
      }
      int leftInFile = inputBuffer.length;
      if (!activeFile && !sourceFile.availableToRead(inputBuffer.length))
        leftInFile = (int)sourceFile.availableToRead();
      if (leftInFile <= 0)
      {
        break;
      }
      if (XCODE_DEBUG) System.out.println("Pushing data into the remuxer for init of size " + leftInFile);
      sourceFile.read(java.nio.ByteBuffer.wrap(inputBuffer), leftInFile);
      if ((targetFormat = muxy.pushInitData(inputBuffer, 0, leftInFile)) != null)
        break;
    }

    //		if (transcodeStartSeekTime != 0) // always seek now so we reset the stream properly
    {
      muxy.seek(transcodeStartSeekTime);
      sourceFile.seek(transcodeStartSeekTime);
    }
    if (XCODE_DEBUG) System.out.println("Initiating remux operation for " + currFile + (currServer == null ? "" : (" from " + currServer)));
    xcodeDone = false;
    fillerAlive = true;
    numFilledXcodeBuffers = 0;
    Pooler.execute(new BufferFiller(), "RemuxBufferFiller");
  }

  public void stopTranscode()
  {
    xcodeDone = true;
    if (!fillerAlive)
    {
      // otherwise let the filler thread clean it up
      if (muxy != null)
      {
        muxy.close();
        muxy = null;
      }
      if (sourceFile != null)
      {
        try
        {
          sourceFile.close();
        }
        catch (Exception ie)
        {
        }
        sourceFile = null;
      }
    }
    fillerAlive = false;
  }

  public void setEnableOutputBuffering(boolean x)
  {
    // We never buffer output with this transcoder
  }

  public void setActiveFile(boolean x)
  {
    if (activeFile != x)
    {
      activeFile = x;
    }
  }

  public boolean isTranscoding()
  {
    return muxy != null && !xcodeDone;
  }

  public sage.media.format.ContainerFormat getTargetFormat()
  {
    return targetFormat;
  }

  protected sage.media.format.MPEGParser.Remuxer muxy;
  protected FastMpeg2Reader sourceFile;
  protected byte[] inputBuffer;

  protected boolean xcodeDone;
  protected boolean activeFile;
  // Used to handle overage from when we push more than we need into the remuxer
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

  protected java.io.File currFile;
  protected java.io.File outputFile;

  protected long transcodeStartSeekTime;
  protected java.io.FileInputStream fileStream;
  protected java.nio.channels.FileChannel fileChannel;

  protected boolean fillerAlive = false;

  protected boolean inputTS;

  protected RemuxOutputBuffer muxyBuffer;

  protected String currServer;

  protected Long seekTarget;

  protected java.nio.ByteBuffer nioTmpBuf;

  protected sage.media.format.ContainerFormat targetFormat;
  protected int currBuffNum; // for consuming data from the remuxer into the buffer
  private class RemuxOutputBuffer extends java.io.OutputStream
  {
    public void write(byte[] buf)
    {
      write(buf, 0, buf.length);
    }
    public void write(byte[] buf, int offset, int length)
    {
      if (XCODE_DEBUG) System.out.println("Recvd " + length + " bytes from transcoder");
      // See how much room is left in the current buffer
      int spaceLeft = xcodeBuffer[0].length - (int)(xcodeBufferVirtualSize % xcodeBuffer[0].length);
      while (length > 0)
      {
        if (length <= spaceLeft)
        {
          System.arraycopy(buf, offset, xcodeBuffer[currBuffNum], xcodeBuffer[0].length - spaceLeft, length);
          synchronized (xcodeSyncLock)
          {
            xcodeBufferVirtualSize += length;
            if (length == spaceLeft)
            {
              numFilledXcodeBuffers++;
              if (XCODE_DEBUG) System.out.println("Number of transcode buffers filled=" + numFilledXcodeBuffers
                  + " virtXcodedBytes=" + xcodeBufferVirtualSize + " just filled offset=" + currBuffNum);
              currBuffNum = (currBuffNum + 1) % xcodeBuffer.length;
            }
          }
          return;
        }
        System.arraycopy(buf, offset, xcodeBuffer[currBuffNum], xcodeBuffer[0].length - spaceLeft, spaceLeft);
        length -= spaceLeft;
        offset += spaceLeft;
        synchronized (xcodeSyncLock)
        {
          xcodeBufferVirtualSize += spaceLeft;
          numFilledXcodeBuffers++;
        }
        if (XCODE_DEBUG) System.out.println("Number of transcode buffers filled=" + numFilledXcodeBuffers
            + " virtXcodedBytes=" + xcodeBufferVirtualSize + " just filled offset=" + currBuffNum);
        currBuffNum = (currBuffNum + 1) % xcodeBuffer.length;
      }
    }
    public void write(int x)
    {
      singleBuf[0] = (byte)(x & 0xFF);
      write(singleBuf, 0, 1);
    }
    private byte[] singleBuf = new byte[1];
  }

  private class BufferFiller implements Runnable
  {
    public void run()
    {
      while (fillerAlive)
      {
        synchronized (xcodeSyncLock)
        {
          if (seekTarget != null)
          {
            long milliSeekTime = seekTarget.longValue();
            if (XCODE_DEBUG) System.out.println("Processing seek operation in remuxer to " + milliSeekTime);
            muxy.seek(milliSeekTime);
            try
            {
              sourceFile.seek(milliSeekTime);
            }
            catch (java.io.IOException e)
            {
              if (Sage.DBG) System.out.println("ERROR in transcoder of:" + e);
              xcodeDone = true;
              seekTarget = null;
              continue;
            }
            transcodeStartSeekTime = milliSeekTime;
            xcodeBufferVirtualReadPos = xcodeBufferVirtualOffset = xcodeBufferVirtualSize = 0;
            xcodeBufferBaseNum = numFilledXcodeBuffers = 0;
            seekTarget = null;
            xcodeSyncLock.notifyAll();
          }
          // NOTE: This -8 isn't really safe; it's there so we try to leave enough buffers left
          // so that what comes out of the remuxer doesn't overflow us
          if (numFilledXcodeBuffers >= xcodeBuffer.length - 8 || xcodeDone)
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
        // Push more data into the remuxer and then read it back again above
        int maxWaits = 200;
        while (activeFile && !sourceFile.availableToRead(inputBuffer.length) && maxWaits-- > 0)
        {
          // Waiting for more data to appear in file
          try{Thread.sleep(50);}catch(Exception e){}
        }
        int leftInFile = inputBuffer.length;
        if (!activeFile && !sourceFile.availableToRead(inputBuffer.length))
          leftInFile = (int)sourceFile.availableToRead();
        if (leftInFile <= 0)
        {
          xcodeDone = true;
          continue;
        }
        if (XCODE_DEBUG) System.out.println("Pushing data into the remuxer of size " + leftInFile);
        try
        {
          sourceFile.read(java.nio.ByteBuffer.wrap(inputBuffer), leftInFile);
        }
        catch (java.io.IOException e)
        {
          if (Sage.DBG) System.out.println("ERROR in transcoder of:" + e);
          xcodeDone = true;
          continue;
        }
        muxy.pushData(inputBuffer, 0, leftInFile);
      }
      if (muxy != null)
      {
        muxy.close();
        muxy = null;
      }
      if (sourceFile != null)
      {
        try
        {
          sourceFile.close();
        }
        catch (Exception ie)
        {
        }
        sourceFile = null;
      }
    }
  }
}
