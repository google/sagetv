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
 * @author  Narflex
 */
public class MulticastCaptureDevice extends CaptureDevice implements Runnable
{
  public static final String MULTICAST_HOST = "multicast_host";
  /** Creates a new instance of MulticastCaptureDevice */
  public MulticastCaptureDevice()
  {
    super();
    captureFeatureBits = MMC.MPEG_AV_CAPTURE_MASK;
  }

  public MulticastCaptureDevice(int inID) throws java.net.UnknownHostException
  {
    super(inID);
    captureFeatureBits = MMC.MPEG_AV_CAPTURE_MASK;
    host = Sage.get(prefs + NetworkCaptureDevice.ENCODING_HOST, "");
  }

  public String getName()
  {
    if (myComplexName == null)
    {
      if (host != null && host.length() > 0)
        myComplexName = Sage.rez("Device_On_Host", new Object[] { getComplexName(captureDeviceName, captureDeviceNum), host });
      else
        myComplexName = getComplexName(captureDeviceName, captureDeviceNum);
    }
    return myComplexName;
  }

  public void freeDevice()
  {
    if (Sage.DBG) System.out.println("Freeing MulticastCaptureDevice");
    try { activateInput(null); } catch (EncodingException e){}
    writePrefs();
    stopEncoding();

    if (caster != null)
    {
      caster.close();
      caster = null;
    }
    loaded = false;
  }

  public long getRecordedBytes()
  {
    return currRecordedBytes;
  }

  public boolean isLoaded()
  {
    return loaded;
  }

  public void loadDevice() throws EncodingException
  {
    if (Sage.DBG) System.out.println("Loading MulticastCaptureDevice");

    // this is to clear the current crossbar so activate actually sets it
    activateInput(null);
    activateInput(getDefaultInput());

    Sage.put(MMC.MMC_KEY + '/' + MMC.LAST_ENCODER_NAME, getName());
    loaded = true;
  }

  public void startEncoding(CaptureDeviceInput cdi, String encodeFile, String channel) throws EncodingException
  {
    if (Sage.DBG) System.out.println("startEncoding for MulticastCaptureDevice file=" + encodeFile + " chan=" + channel);
    currRecordedBytes = 0;
    if (cdi != null)
      activateInput(cdi);
    if (channel != null)
    {
      // If the channel is nothing, then use the last one. This happens with default live.
      if (channel.length() == 0)
        channel = activeSource.getChannel();
      tuneToChannel(channel);
    }

    if (encodeParams == null)
      setEncodingQuality(currQuality);
    currentlyRecordingBufferSize = recordBufferSize;

    // See if we have a preconfigured format for this channel or not, if we do then set the file format for it now
    String fixedFormat = Sage.get(prefs + "fixed_format/" + channel, null);
    if (fixedFormat != null)
    {
      MediaFile mf = Seeker.getInstance().getCurrRecordMediaFile(this);
      if (mf != null)
      {
        sage.media.format.ContainerFormat cf = sage.media.format.ContainerFormat.buildFormatFromString(fixedFormat);
        if (Sage.DBG) System.out.println("Setting file format for multicast record on " + channel + " to fixed format of: " + cf);
        mf.setMediafileFormat(cf);
      }
    }

    // new file
    recFilename = encodeFile;
    recStart = Sage.time();
    try
    {
      caster.joinGroup(group);
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR joining MULTICAST SOCKET:" + e);
      throw new EncodingException();
    }

    try
    {
      outStream = new java.io.BufferedOutputStream(new java.io.FileOutputStream(recFilename));
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR CREATING OUTPUT FILE:" + e);
      throw new EncodingException();
    }

    // Start the encoding thread
    stopCapture = false;
    capThread = new Thread(this, "Multicast-Encoder" + captureDeviceNum);
    capThread.setPriority(Thread.MAX_PRIORITY - 1);
    capThread.start();

    // Initially this may not be set so do so now.
    if ((activeSource.getChannel() != null && activeSource.getChannel().length() == 0) ||
        activeSource.getChannel().equals("0"))
    {
      activeSource.setLastChannel(getChannel());
      activeSource.writePrefs();
    }
  }

  public void stopEncoding()
  {
    if (Sage.DBG) System.out.println("stopEncoding for Multicast capture device");
    recFilename = null;
    recStart = 0;
    stopCapture = true;
    if (capThread != null)
    {
      if (Sage.DBG) System.out.println("Waiting for Multicast capture thread to terminate");
      try
      {
        capThread.join(5000);
      }catch (Exception e){}
    }
    capThread = null;
    try
    {
      if (caster != null)
      {
        if (canEncode())
          caster.leaveGroup(group);
        caster.close();
      }
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR LEAVING MULTICAST SOCKET:" + e);
    }
  }

  public void switchEncoding(String switchFile, String channel) throws EncodingException
  {
    if (Sage.DBG) System.out.println("switchEncoding for Multicast capture device file=" + switchFile + " chan=" + channel);
    if (channel != null)
      tuneToChannel(channel);
    synchronized (caplock)
    {
      nextRecFilename = switchFile;
      while (nextRecFilename != null)
      {
        // Wait for the new filename to be switched to and then return after that
        try{caplock.wait(10);}catch (Exception e){}
      }
    }
  }

  public int findTransitionPoint(byte[] data, int offset, int length)
  {
    int numbytes = length;

    MediaFile mf = Seeker.getInstance().getCurrRecordMediaFile(this);
    if (mf == null)
    {
      if (Sage.DBG) System.out.println("ERROR Unable to find transition point for fast file switch due to a null MediaFile");
      return 0;
    }
    sage.media.format.ContainerFormat cf = mf.getFileFormat();
    if (cf == null)
    {
      if (Sage.DBG) System.out.println("ERROR Unable to find transition point for fast file switch due to a null ContainerFormat");
      return 0;
    }

    // Find out if it's PS or TS
    if (sage.media.format.MediaFormat.MPEG2_TS.equals(cf.getFormatName()))
    {
      sage.media.format.VideoFormat vidForm = cf.getVideoFormat();
      if (vidForm == null)
      {
        if (Sage.DBG) System.out.println("ERROR Unable to find transition point for fast file switch due to a null VideoFormat");
        return 0;
      }
      if (sage.media.format.MediaFormat.H264.equals(vidForm.getFormatName()))
      {
        if (Sage.DBG) System.out.println("CANNOT perform fast switch on a non-H264 MPEG2 TS file!");
        return 0;
      }

      int i, tsstart=-1, tsvidpacket=-1, seqstart=-1;
      // Transport stream input...only works with H264 video

      // First we try to locate ts packets
      int endPos = numbytes + offset;
      for(i=offset;i<endPos;i++)
      {
        if(data[i]==0x47 &&
            (i+188)<endPos && data[i+188]==0x47 &&
            (i+188*2)<endPos && data[i+188*2]==0x47)
        {
          tsstart=i;
          break;
        }
      }

      int targetPID = Integer.parseInt(vidForm.getId(), 16);
      byte targetHighByte = (byte)(0x40 | ((targetPID >> 8) & 0xFF));
      byte targetLoByte = (byte)(targetPID & 0xFF);

      // Second we find a ts packet with section start and target PID
      while((i+188)<=endPos)
      {
        if(data[i]==0x47 &&
            data[i+1]==targetHighByte &&
            data[i+2]==targetLoByte)
        {
          tsvidpacket=i;
          // Verify if that packet contains the magic sequence 00 00 00 01 09 10 00
          // If it does, the data up to the begining of this TS packet go in old file
          // and the new data in the new file
          int j;
          for(j=4;j<188-7;j++)
          {
            // NOTE: we could implement faster search but the number of
            // matched packet that reach this point should be quite small...
            if(data[i+j]==0x00 &&
                data[i+j+1]==0x00 &&
                data[i+j+2]==0x00 &&
                data[i+j+3]==0x01 &&
                data[i+j+4]==0x09 &&
                data[i+j+5]==0x10 &&
                data[i+j+6]==0x00)
            {
              // We have found the vid packet with the magic sequence, write that to old file
              return tsvidpacket;
            }
          }
        }
        i+=188;
      }
    }
    else
    {
      if (Sage.DBG) System.out.println("ERROR Fast switching is not done for MPEG2 PS content!");
      return 0;
    }
    return -1;
  }

  public void run()
  {
    boolean logCapture = Sage.getBoolean("debug_capture_progress", false);
    if (Sage.DBG) System.out.println("Starting Multicast capture thread");
    long addtlBytes;
    boolean disableWriting = Sage.getBoolean("disable_multicast_disk_writing", false);
    long statBWPeriod = Sage.getLong("multicast_stat_period", 0);
    final int bufSize = Sage.getInt("multicast_recv_buffer", 65536);
    int numBuffs = Sage.getInt("multicast_parallel_buffs", 64);
    final java.util.Vector filledBuffs = new java.util.Vector();
    final java.util.Vector emptyBuffs = new java.util.Vector();
    for (int i = 0; i < numBuffs; i++)
    {
      byte[] buf = new byte[bufSize];
      java.net.DatagramPacket pack = new java.net.DatagramPacket(buf, buf.length);
      emptyBuffs.add(pack);
    }
    long transitionSearchBytes = 0;
    long statStartTime = Sage.time();
    long statByteCounter = 0;
    int buffOffset = 0;
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        // other thread to do the network receiving
        while (!stopCapture)
        {
          java.net.DatagramPacket pack;
          if (emptyBuffs.isEmpty())
          {
            if (filledBuffs.size() > 1000)
            {
              synchronized (emptyBuffs)
              {
                if (emptyBuffs.isEmpty())
                {
                  try {
                    emptyBuffs.wait(500);
                  }catch (InterruptedException e){}
                }
                continue;
              }
            }
            if (Sage.DBG) System.out.println("Increasing the number of multicast buffers to " + (filledBuffs.size() + 1));
            pack = new java.net.DatagramPacket(new byte[bufSize], bufSize);
          }
          else
            pack = (java.net.DatagramPacket) emptyBuffs.remove(0);
          pack.setLength(bufSize);
          try
          {
            caster.receive(pack);
            synchronized (filledBuffs)
            {
              filledBuffs.add(pack);
              filledBuffs.notifyAll();
            }
          }
          catch (java.net.SocketTimeoutException ste)
          {
            emptyBuffs.add(pack);
            if (nextRecFilename != null)
              transitionPoint = 0; // force the transition too
          }
          catch (java.io.IOException e)
          {
            System.out.println("ERROR Eating encoder data:" + e.getMessage());
          }
        }
      }
    }, "MulticastNetReceive", Thread.MAX_PRIORITY);
    java.net.DatagramPacket pack = null;
    while (!stopCapture)
    {
      if (nextRecFilename != null && transitionPoint >= 0)
      {
        try
        {
          if (transitionPoint > 0 && !disableWriting && pack != null)
          {
            outStream.write(pack.getData(), 0, transitionPoint);
          }
          outStream.close();
          outStream = new java.io.BufferedOutputStream(new java.io.FileOutputStream(nextRecFilename));
          if (pack != null && transitionPoint < pack.getLength() && !disableWriting)
          {
            outStream.write(pack.getData(), transitionPoint, pack.getLength() + buffOffset - transitionPoint);
          }
          buffOffset = 0;
        }
        catch (java.io.IOException e)
        {
          System.out.println("ERROR Switching encoder file:" + e.getMessage());
        }
        synchronized (caplock)
        {
          recFilename = nextRecFilename;
          nextRecFilename = null;
          recStart = Sage.time();
          currRecordedBytes = 0;
          // There may be a thread waiting on this state change to occur
          caplock.notifyAll();
          transitionPoint = -1;
          transitionSearchBytes = 0;
        }
      }
      if (pack != null)
      {
        synchronized (emptyBuffs)
        {
          emptyBuffs.add(pack);
          emptyBuffs.notifyAll();
        }
      }
      long startWait = Sage.time();
      while (!stopCapture)
      {
        if (filledBuffs.isEmpty())
        {
          synchronized (filledBuffs)
          {
            if (filledBuffs.isEmpty())
            {
              try {
                filledBuffs.wait(500);
              }catch (InterruptedException e){}
              if (Sage.time() - startWait > 1000 && nextRecFilename != null)
              {
                transitionPoint = 0;
                addtlBytes = 0;
                break;
              }
              continue;
            }
          }
        }
        else
          break;
      }
      if (stopCapture)
        break;
      if (!filledBuffs.isEmpty())
      {
        pack = (java.net.DatagramPacket) filledBuffs.remove(0);

        try
        {
          //				pack.setData(buf, buffOffset, buf.length - buffOffset);
          //				caster.receive(pack);
          addtlBytes = pack.getLength();
          if (nextRecFilename != null)
          {
            transitionPoint = findTransitionPoint(pack.getData(), buffOffset, pack.getLength());
            transitionSearchBytes += pack.getLength();
            if (transitionSearchBytes > 8*1024*1024)
            {
              if (Sage.DBG) System.out.println("WARNING Could not find transition point after searching 8 MB of data in stream!!");
              transitionPoint = 0;
            }
          }
          if (transitionPoint == -1 && !disableWriting)
          {
            if (true || buffOffset + addtlBytes == bufSize)
            {
              // Full buffer, write it out
              outStream.write(pack.getData(), 0, (int)addtlBytes);
              buffOffset = 0;
            }
            else
            {
              buffOffset += addtlBytes;
            }
          }
        }
        catch (java.net.SocketTimeoutException ste)
        {
          addtlBytes = 0;
          if (nextRecFilename != null)
            transitionPoint = 0; // force the transition too
        }
        catch (java.io.IOException e)
        {
          System.out.println("ERROR Eating encoder data:" + e.getMessage());
          addtlBytes = 0;
        }
        synchronized (caplock)
        {
          currRecordedBytes += addtlBytes;
        }

        if (Sage.DBG && statBWPeriod > 0 && Sage.time() - statStartTime >= statBWPeriod)
        {
          long newTime = Sage.time();
          System.out.println("Multicast BW for " + this + " is: " + (8*(currRecordedBytes - statByteCounter)/((float)(newTime - statStartTime))) + " Kbps" +
              " Overall=" + (currRecordedBytes*8/((float)(newTime - recStart))) + " Kbps");
          statStartTime = newTime;
          statByteCounter = currRecordedBytes;
        }

        if (logCapture)
          System.out.println("MulticastCap " + recFilename + " " + currRecordedBytes);
      }
    }
    if (Sage.DBG) System.out.println("Multicast capture thread terminating");
    if (outStream != null)
    {
      try
      {
        outStream.close();
        outStream = null;
      }
      catch (java.io.IOException e)
      {
      }
    }
  }

  protected boolean doTuneChannel(String tuneString, boolean autotune)
  {
    // Extract the host & port from the tuneString
    int colonIdx = tuneString.indexOf(':');
    int port = 6789;
    if (colonIdx != -1)
    {
      port = Integer.parseInt(tuneString.substring(colonIdx + 1));
      multicastHost = tuneString.substring(0, colonIdx);
    }
    else
      multicastHost = tuneString;
    if (Sage.DBG) System.out.println("Mulicast receiving on address=" + multicastHost + " port=" + port);
    try
    {
      group = java.net.InetAddress.getByName(multicastHost);
      caster = new java.net.MulticastSocket(port);
      caster.setSoTimeout(5000);
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR CREATING MULTICAST SOCKET:" + e);
    }
    return true;
  }
  protected boolean doScanChannel(String tuneString)
  {
    return doTuneChannel(tuneString, true);
  }
  protected String doScanChannelInfo(String tuneString)
  {
    return (doTuneChannel( tuneString, true ) ? "1" : "");
  }

  public void setEncodingQuality(String encodingName)
  {
    encodingName = MMC.cleanQualityName(encodingName);
    encodeParams = MPEG2EncodingParams.getQuality(encodingName);
    currQuality = encodingName;
    writePrefs();
  }

  public String getDeviceClass()
  {
    return "Multicast";
  }

  public boolean canEncode()
  {
    return Sage.getBoolean("mmc/multicast_can_record", true);
  }

  private MPEG2EncodingParams encodeParams;

  public boolean isNetworkEncoder()
  {
    return true;
  }

  public boolean supportsFastMuxSwitch()
  {
    return true;
  }

  private String nextRecFilename;

  private final Object caplock = new Object();
  private final Object devlock = new Object();
  private boolean stopCapture;
  private Thread capThread;
  private volatile long currRecordedBytes;

  private String multicastHost;

  private java.io.OutputStream outStream;
  private java.net.MulticastSocket caster;
  private java.net.InetAddress group;

  private int transitionPoint = -1;
  private boolean loaded;
  protected String host = "";
}
