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
package sage.media.format;

/**
 *
 * @author Narflex
 */
public class MPEGParser
{
  public static final int REMUX_TS = 0;
  public static final int REMUX_PS = 1;
  static
  {
    if (System.getProperty("os.name").toLowerCase().indexOf("windows") == -1)
    {
      try
      {
        sage.Native.loadLibrary("MPEGParser");
      }
      catch (Throwable t)
      {
        System.out.println("ERROR Loading native MPEGParser library of: " + t);
      }
    }
  }
  public static native String getMediaAVInf0(String filename, long searchSize, boolean activeFile, long channel);

  public static Remuxer openRemuxer(int mode, java.io.OutputStream outStream)
  {
    return openRemuxer(mode, 0, outStream);
  }
  public static Remuxer openRemuxer(int mode, int channel, java.io.OutputStream outStream)
  {
    long ptr = openRemuxer0(mode, channel, outStream);
    if (ptr != 0)
      return new Remuxer(ptr, mode, channel, outStream);
    else
      return null;
  }

  public static boolean remuxFile(java.io.File inputFile, java.io.File outputFile, int mode)
  {
    java.io.FileInputStream inputStream = null;
    java.io.BufferedOutputStream outputStream = null;
    Remuxer muxy = null;
    try
    {
      inputStream = new java.io.FileInputStream(inputFile);
      outputStream = new java.io.BufferedOutputStream(new java.io.FileOutputStream(outputFile));
      muxy = openRemuxer(mode, 0, outputStream);
      byte[] buf = new byte[8192];
      int numRead = 0;
      while ((numRead = inputStream.read(buf)) != -1)
      {
        if (muxy.pushInitData(buf, 0, numRead) != null)
          break;
      }
      // reset the input stream
      muxy.seek(0);
      inputStream.close();
      inputStream = new java.io.FileInputStream(inputFile);
      while ((numRead = inputStream.read(buf)) != -1)
      {
        muxy.pushData(buf, 0, numRead);
      }
    }
    catch (Exception e)
    {
      System.out.println("ERROR with remuxing of:" +e);
      e.printStackTrace();
      return false;
    }
    finally
    {
      if (inputStream != null)
      {
        try
        {
          inputStream.close();
        }catch (Exception e){}
      }
      if (outputStream != null)
      {
        try
        {
          outputStream.close();
        }catch (Exception e){}
      }
      if (muxy != null)
        muxy.close();
    }
    return true;
  }

  private static native long openRemuxer0(int mode, int channel, java.io.OutputStream outStream);
  private static native void closeRemuxer0(long ptr);
  // Returns the PTS of the last packet remuxed
  private static native long pushRemuxData0(long ptr, byte[] buf, int offset, int length);
  private static native String initRemuxDataDone0(long ptr, byte[] buf, int offset, int length);
  private static native void flushRemuxer0(long ptr);

  // NOTE: When you close the remuxer it does NOT close the outStream as well. This needs
  // to be done by the caller.
  public static class Remuxer
  {
    public Remuxer(long ptr, int mode, int channel, java.io.OutputStream outStream)
    {
      this.ptr = ptr;
      this.mode = mode;
      this.channel = channel;
      this.outStream = outStream;
    }

    /**
     * Close the remuxer.
     * <p/>
     * When you close the remuxer, it does NOT close the OutputStream. This needs to be done by the
     * caller.
     */
    public void close()
    {
      long temp = ptr;
      ptr = 0;
      if (temp != 0)
        closeRemuxer0(temp);
    }

    /**
     * Push data to the remuxer.
     * <p/>
     * pushInitData must return a non-null object before this method can be used.
     *
     * @param buf The data to be pushed to the remuxer. For TS output, this data must be fed in 188
     *            byte chunks. For PS output, this should work up to 1MB.
     * @param offset The offset to start from.
     * @param length The number of bytes to read from offset.
     */
    public void pushData(byte[] buf, int offset, int length)
    {
      if (ptr != 0)
        lastPTS = pushRemuxData0(ptr, buf, offset, length);
      pushedBytes += length;
    }

    /**
     * Push initialization data to the remuxer.
     * <p/>
     * Data pushed into this method is cumulative. You do not need to push all of the data from the
     * start every time you call this method.
     * <p/>
     * This data is not automatically fed into pushData, so you will need to retain it for pushData
     * after detection has succeeded.
     *
     * @param buf The data to be pushed to the remuxer for initialization.
     * @param offset The offset to start from.
     * @param length The number of bytes to read from offset.
     * @return The detected format of the container or null if no format has been detected.
     */
    public ContainerFormat pushInitData(byte[] buf, int offset, int length)
    {
      if (ptr != 0)
      {
        String newFormat = initRemuxDataDone0(ptr, buf, offset, length);
        if (newFormat != null)
        {
          ContainerFormat rv = ContainerFormat.buildFormatFromString(newFormat.substring(7)); // skip the AV-INF| part
          if (sage.Sage.DBG) System.out.println("Detected remuxing format of:" + newFormat + " formatObject=" + rv);
          return rv;
        }
      }
      return null;
    }
    public long getLastPTSMsec()
    {
      return lastPTS/90;
    }
    public void seek(long newTimeMilli)
    {
      // The pusher will seek itself and the flush clears the remuxer
      if (ptr != 0)
        flushRemuxer0(ptr);
    }
    private long ptr;
    private long lastPTS;
    private long pushedBytes;
    private int mode;
    private int channel;
    private java.io.OutputStream outStream;
  }
}
