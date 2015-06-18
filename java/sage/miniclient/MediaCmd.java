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
package sage.miniclient;

/**
 *
 * @author  Narflex
 */
public class MediaCmd
{
  public static final int MEDIACMD_INIT = 0;

  public static final int MEDIACMD_DEINIT = 1;

  public static final int MEDIACMD_OPENURL = 16;
  // length, url

  public static final int MEDIACMD_GETMEDIATIME = 17;

  public static final int MEDIACMD_SETMUTE = 18;
  // mute

  public static final int MEDIACMD_STOP = 19;

  public static final int MEDIACMD_PAUSE = 20;

  public static final int MEDIACMD_PLAY = 21;

  public static final int MEDIACMD_FLUSH = 22;

  public static final int MEDIACMD_PUSHBUFFER = 23;
  // size, flags, data

  public static final int MEDIACMD_GETVIDEORECT = 24;
  // returns 16bit width, 16bit height

  public static final int MEDIACMD_SETVIDEORECT = 25;
  // x, y, width, height, x, y, width, height

  public static final int MEDIACMD_GETVOLUME = 26;

  public static final int MEDIACMD_SETVOLUME = 27;
  // volume
  public static final int MEDIACMD_FRAMESTEP = 28;

  public static final int MEDIACMD_SEEK = 29;
  // 64-bit time (for pull mode only)

  private static MediaCmd globalMediaCmd;
  public static MediaCmd getInstance() { return globalMediaCmd; }

  private MiniMPlayerPlugin playa;
  private java.io.File buffFile;
  private java.io.FileOutputStream buffStream;
  //private java.io.RandomAccessFile buffRaf;

  private long pushDataLeftBeforeInit;

  private long bufferFilePushedBytes;

  private boolean pushMode;
  private int numPushedBuffers;

  private int DESIRED_VIDEO_PREBUFFER_SIZE = 4*1024*1024;
  private int DESIRED_AUDIO_PREBUFFER_SIZE = 2*1024*1024;
  private int maxPrebufferSize;

  private MiniClientConnection myConn;

  private int statsChannelBWKbps;
  private int statsStreamBWKbps;
  private int statsTargetBWKbps;
  private long serverMuxTime;
  private long prebufferTime;

  /** Creates a new instance of MediaCmd */
  public MediaCmd(MiniClientConnection myConn)
  {
    this.myConn = myConn;
    globalMediaCmd = this;
  }

  public MiniMPlayerPlugin getPlaya()
  {
    return playa;
  }

  public static void writeInt(int value, byte[] data, int offset)
  {
    data[offset] = (byte)((value >> 24) & 0xFF);
    data[offset + 1] = (byte)((value >> 16) & 0xFF);
    data[offset + 2] = (byte)((value >> 8 ) & 0xFF);
    data[offset + 3] = (byte)(value & 0xFF);
  }

  public static void writeShort(short value, byte[] data, int offset)
  {
    data[offset] = (byte)((value >> 8 ) & 0xFF);
    data[offset + 1] = (byte)(value & 0xFF);
  }

  public static int readInt(int pos, byte[] cmddata)
  {
    return ((cmddata[pos+0] & 0xFF)<<24)|((cmddata[pos+1] & 0xFF)<<16)|((cmddata[pos+2] & 0xFF)<<8)|(cmddata[pos+3] & 0xFF);
  }

  public static short readShort(int pos, byte[] cmddata)
  {
    return (short) (((cmddata[pos+0] & 0xFF)<<8)|(cmddata[pos+1] & 0xFF));
  }

  public void close()
  {
    if (myConn.getGfxCmd() != null)
      myConn.getGfxCmd().setVideoBounds(null, null);
    if (playa != null)
      playa.free();
    playa = null;
    try
    {
      buffStream.close();
    }
    catch (Exception e){}
    buffStream = null;
    if (buffFile != null)
      buffFile.delete();
  }

  private Process ogleProcess;

  public static BufferStatsFrame bufferStatsFrame;
  public int ExecuteMediaCommand(int cmd, int len, byte[] cmddata, byte[] retbuf)
  {
    // TODO verify sizes...
    if(cmd!=MEDIACMD_PUSHBUFFER)
      System.out.println("Execute media command " + cmd);
    switch(cmd)
    {
      case MEDIACMD_INIT:
        try
        {
          DESIRED_VIDEO_PREBUFFER_SIZE = Integer.parseInt(MiniClient.myProperties.getProperty("video_buffer_size", "" + (4*1024*1024)));
          DESIRED_AUDIO_PREBUFFER_SIZE = Integer.parseInt(MiniClient.myProperties.getProperty("audio_buffer_size", "" + (2*1024*1024)));
        }
        catch (Exception e)
        {
          System.out.println("ERROR:" + e);
        }
        readInt(0, cmddata); // video format code
        writeInt(1, retbuf, 0);
        return 4;
      case MEDIACMD_DEINIT:
        writeInt(1, retbuf, 0);
        if (ogleProcess != null)
        {
          ogleProcess.destroy();
          ogleProcess = null;
        }
        close();
        return 4;
      case MEDIACMD_OPENURL:
        int strLen = readInt(0, cmddata);
        String urlString = "";
        maxPrebufferSize = DESIRED_VIDEO_PREBUFFER_SIZE;
        if (strLen > 1)
          urlString = new String(cmddata, 4, strLen - 1);
        if (!urlString.startsWith("push:"))
        {
          if (urlString.startsWith("dvd:"))
          {
            try
            {
              ogleProcess = Runtime.getRuntime().exec("ogle");
            }
            catch (Exception e)
            {
              System.out.println("ERROR Launching Ogle:" + e);
            }
          }
          else if (urlString.startsWith("file://"))
          {
            playa = new MiniMPlayerPlugin(myConn.getGfxCmd(), myConn);
            playa.setPushMode(false);
            playa.load((byte)0, (byte)0, "", urlString, null, false, 0);
            pushDataLeftBeforeInit = 0;
            pushMode = false;
          }
          else
          {
            playa = new MiniMPlayerPlugin(myConn.getGfxCmd(), myConn);
            // We always set it to be an active file because it'll get turned off by the streaming code if it is not.
            // It's safe to say it's active when it's not (as long as it's a streamable file format), but the opposite is not true.
            // So we always say it's active to avoid any problems loading the file if it's a streamable file format.
            boolean isActive = urlString.toLowerCase().endsWith(".mpg") || urlString.toLowerCase().endsWith(".ts") ||
                urlString.toLowerCase().endsWith(".flv");
            playa.setPushMode(false);
            playa.load((byte)0, (byte)0, "", urlString, myConn.getServerName(), isActive, 0);
            pushDataLeftBeforeInit = 0;
            pushMode = false;
          }
        }
        else
        {
          if (MiniClientConnection.detailedBufferStats)
          {
            if (bufferStatsFrame == null)
            {
              bufferStatsFrame = new BufferStatsFrame();
              // If we don't rethread this I've seen it deadlock the JVM on Linux
              java.awt.EventQueue.invokeLater(new Runnable() {
                public void run() {
                  bufferStatsFrame.pack();
                }
              });
            }
          }
          pushMode = true;
          if (MiniMPlayerPlugin.USE_STDIN)
          {
            playa = new MiniMPlayerPlugin(myConn.getGfxCmd(), myConn);
            playa.setPushMode(true);
            playa.load((byte)0, (byte)0, "", buffFile.toString(), null, true, 0);
            pushDataLeftBeforeInit = 0;
          }
          else
          {
            try
            {
              buffFile = java.io.File.createTempFile("stvbuff", ".dat");
              buffStream = new java.io.FileOutputStream(buffFile);
              //buffRaf = new java.io.RandomAccessFile(buffFile, "rw");
              buffFile.deleteOnExit();
            }
            catch (java.io.IOException e)
            {
              System.out.println("Error with streaming: " + e);
              e.printStackTrace();
              return 0;
            }
            if (urlString.indexOf("audio") != -1 && urlString.indexOf("bf=vid") == -1)
            {
              pushDataLeftBeforeInit = 1024*16;
              maxPrebufferSize = DESIRED_AUDIO_PREBUFFER_SIZE;
            }
            else
            {
              pushDataLeftBeforeInit = 1024*64;
              maxPrebufferSize = DESIRED_VIDEO_PREBUFFER_SIZE;
            }
            playa = new MiniMPlayerPlugin(myConn.getGfxCmd(), myConn);
            playa.setPushMode(true);
            //						playa.load((byte)0, (byte)0, "", buffFile, null, true, 0);
          }
        }
        writeInt(1, retbuf, 0);
        return 4;
      case MEDIACMD_GETMEDIATIME:
        if (playa == null)
          return 0;
        long theTime = playa.getMediaTimeMillis();
        writeInt((int)theTime, retbuf, 0);
        if (MiniClientConnection.detailedBufferStats)
        {
          if (playa != null)
          {
            retbuf[4] = (byte)(playa.getState() & 0xFF);
          }
          else
          {
            retbuf[4] = 0;
          }
          return 5;
        }
        else
          return 4;
      case MEDIACMD_SETMUTE:
        writeInt(1, retbuf, 0);
        if (playa == null)
          return 4;
        playa.setMute(readInt(0, cmddata) != 0);
        return 4;
      case MEDIACMD_STOP:
        writeInt(1, retbuf, 0);
        if (playa == null)
          return 4;
        playa.stop();
        return 4;
      case MEDIACMD_PAUSE:
        writeInt(1, retbuf, 0);
        if (playa == null)
          return 4;
        playa.pause();
        return 4;
      case MEDIACMD_PLAY:
        writeInt(1, retbuf, 0);
        if (playa == null)
          return 4;
        playa.play();
        return 4;
      case MEDIACMD_FLUSH:
        writeInt(1, retbuf, 0);
        // TODO
        if (playa != null && pushMode && numPushedBuffers > 0)
        {
          numPushedBuffers = 0;
          // Be sure all data is written to disk that we've gotten already.
          try
          {
            if (buffStream != null)
              buffStream.getFD().sync();
          }catch (Exception e){}
          //playa.beginFlush();
          //				try
          {
            //buffStream.close();
            //buffStream = new java.io.FileOutputStream(buffFile);
            //buffRaf.seek(0);
            //						buffFile = java.io.File.createTempFile("stvbuff", ".dat");
            //buffStream = new java.io.FileOutputStream(buffFile);
            //						buffRaf = new java.io.RandomAccessFile(buffFile, "rw");
            //						buffFile.deleteOnExit();
            playa.seek(Long.MAX_VALUE);
          }
          //					catch (java.io.IOException e)
          {
            //					System.out.println("Error zeroing out buffered stream from disk:" + e);
          }
          //playa.seek(0);
          //playa.play();
        }
        return 4;
      case MEDIACMD_PUSHBUFFER:
        int buffSize = readInt(0, cmddata);
        int flags = readInt(4, cmddata);
        int bufDataOffset = 8;
        if (MiniClientConnection.detailedBufferStats && buffSize > 0 && len > buffSize + 13)
        {
          bufDataOffset += 10;
          statsChannelBWKbps = readShort(8, cmddata);
          statsStreamBWKbps = readShort(10, cmddata);
          statsTargetBWKbps = readShort(12, cmddata);
          serverMuxTime = readInt(14, cmddata);
          if (playa != null)
          {
            prebufferTime = serverMuxTime - playa.getMediaTimeMillis();
          }
          System.out.println("STATS chanBW=" + statsChannelBWKbps + " streamBW=" + statsStreamBWKbps + " targetBW=" + statsTargetBWKbps + " pretime=" + prebufferTime);
          if (bufferStatsFrame != null)
          {
            bufferStatsFrame.addNewStats(statsChannelBWKbps, statsStreamBWKbps, statsTargetBWKbps, prebufferTime);
            myConn.getGfxCmd().getWindow().updateStats();
          }
        }
        if (buffSize > 0)
        {
          numPushedBuffers++;
          try
          {
            if (MiniMPlayerPlugin.USE_STDIN)
              playa.pushData(cmddata, bufDataOffset, buffSize);
            else if (buffStream != null)
            {
              buffStream.write(cmddata, bufDataOffset, buffSize);
              buffStream.flush();
              // DISABLE THIS				buffStream.getFD().sync();
              bufferFilePushedBytes += buffSize;
              //buffRaf.write(cmddata, 8, buffSize);
            }
          }
          catch (java.io.IOException e)
          {
            System.out.println("IO Error:"  + e);
          }
        }
        if (!MiniMPlayerPlugin.USE_STDIN && pushDataLeftBeforeInit > 0)
        {
          pushDataLeftBeforeInit -= buffSize;
          if (pushDataLeftBeforeInit <= 0)
          {
            //playa = new MiniMPlayerPlugin(myConn.getGfxCmd(), myConn);
            //playa.setPushMode(true);
            playa.load((byte)0, (byte)0, "", buffFile.toString(), null, true, 0);
          }
        }
        if (flags == 0x80 && playa != null)
        {
          playa.inactiveFile();
        }
        int rv;
        // Always indicate we have at least 512K of buffer...there's NO reason to stop buffering additional
        // data since as playback goes on we keep writing to the filesystem anyways. Yeah, we could recover some bandwidth
        // but that's not how any online video players work and we shouldn't be any different than that.
        if (playa == null)
          rv = maxPrebufferSize;
        else
          rv = (int)Math.max(131072*4, maxPrebufferSize - (bufferFilePushedBytes - playa.getLastFileReadPos()));
        System.out.println("Finished pushing current data buffer of " + buffSize + " availSize=" + rv + " totalPushed=" + bufferFilePushedBytes +
            " fileSize=" + (buffFile == null ? 0 : buffFile.length()));
        writeInt(rv, retbuf, 0);
        if (MiniClientConnection.detailedBufferStats)
        {
          if (playa != null)
          {
            writeInt((int)playa.getMediaTimeMillis(), retbuf, 4);
            retbuf[8] = (byte)(playa.getState() & 0xFF);
          }
          else
          {
            writeInt(0, retbuf, 4);
            retbuf[8] = 0;
          }
          if (flags == 0x80 && (playa == null || pushDataLeftBeforeInit > 0))
          {
            retbuf[8] = (byte)(MiniMPlayerPlugin.EOS_STATE & 0xFF);
          }
          return 9;
        }
        else
          return 4;
      case MEDIACMD_GETVOLUME:
        if (playa == null)
          writeInt(65535, retbuf, 0);
        else
          writeInt(Math.round(playa.getVolume() * 65535), retbuf, 0);
        return 4;
      case MEDIACMD_SETVOLUME:
        if (playa == null)
          writeInt(65535, retbuf, 0);
        else
          writeInt(Math.round(playa.setVolume(readInt(0, cmddata) / 65535.0f) * 65535), retbuf, 0);
        return 4;
      case MEDIACMD_SETVIDEORECT:
        java.awt.Rectangle srcRect = new java.awt.Rectangle(readInt(0, cmddata), readInt(4, cmddata),
            readInt(8, cmddata), readInt(12, cmddata));
        java.awt.Rectangle destRect = new java.awt.Rectangle(readInt(16, cmddata), readInt(20, cmddata),
            readInt(24, cmddata), readInt(28, cmddata));
        if (playa != null)
          playa.setVideoRectangles(srcRect, destRect, false);
        myConn.getGfxCmd().setVideoBounds(srcRect, destRect);
        writeInt(0, retbuf, 0);
        return 4;
      case MEDIACMD_GETVIDEORECT:
        java.awt.Dimension vidRect = null;
        if (playa != null)
        {
          vidRect = playa.getVideoDimensions();
          writeShort((short)vidRect.width, retbuf, 0);
          writeShort((short)vidRect.height, retbuf, 2);
        }
        else
        {
          writeInt(0, retbuf, 0);
        }
        return 4;
      case MEDIACMD_SEEK:
        long seekTime = ((long)readInt(0, cmddata) << 32) | readInt(4, cmddata);
        if (playa != null)
          playa.seek(seekTime);
        return 0;
      default:
        return -1;
    }
  }
}
