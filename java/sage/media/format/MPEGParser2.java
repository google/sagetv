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

import sage.Sage;

import java.io.OutputStream;

public class MPEGParser2
{
  public static final int REMUX_TS = 1;
  public static final int REMUX_PS = 16;

  public enum StreamFormat
  {
    /**
     * The stream doesn't have a specific format. Select the first channel. (This may result in
     * audio only.)
     */
    UNKNOWN,
    /**
     * The stream is formatted as ATSC. Searches for the desired streams will be based on this.
     * (This will always have video.)
     */
    ATSC,
    /**
     * The stream is formatted as DVB.  Searches for the desired streams will be based on this.
     */
    DVB,
    /**
     * The stream doesn't have a specific format. (This may result in audio only.)
     */
    FREE
  }

  public enum SubFormat
  {
    UNKNOWN,
    TERRESTRIAL,
    CABLE,
    SATELLITE
  }

  public enum TuneStringType
  {
    CHANNEL,
    PROGRAM,
    XX_XX,
    XX_XX_XX,
    AUTO
  }

  static
  {
    if (Sage.WINDOWS_OS || Sage.LINUX_OS)
    {
      try
      {
        System.loadLibrary("JavaRemuxer2");
      }
      catch (Throwable t)
      {
        System.out.println("ERROR Loading native JavaRemuxer2 library of: " + t);
      }
    }
  }

  public static boolean remuxFile(java.io.File inputFile, java.io.File outputFile, int outputFormat, boolean isTV)
  {
    java.io.FileInputStream inputStream = null;
    java.io.BufferedOutputStream outputStream = null;
    Remuxer2 remuxer = null;

    try
    {
      inputStream = new java.io.FileInputStream(inputFile);
      outputStream = new java.io.BufferedOutputStream(new java.io.FileOutputStream(outputFile));
      remuxer = openRemuxer(REMUX_TS, outputFormat, isTV ? StreamFormat.ATSC : StreamFormat.FREE,
          SubFormat.UNKNOWN, TuneStringType.CHANNEL, 0, 0, 0, 0, 0, outputStream);

      if (remuxer == null)
        return false;

      // This is a multiple of 188 to keep the data aligned. Otherwise we need to use feedback from
      // the remuxer to keep the data aligned.
      byte[] buf = new byte[43 * 188];
      int numRead = 0;
      while ((numRead = inputStream.read(buf)) != -1)
      {
        if (remuxer.pushInitData(buf, 0, numRead))
          break;
      }
      // The remuxer resets the queue as soon as the container format has been detected.
      inputStream.close();
      inputStream = new java.io.FileInputStream(inputFile);
      while ((numRead = inputStream.read(buf)) != -1)
      {
        remuxer.pushRemuxData(buf, 0, numRead);
      }
    }
    catch (Exception e)
    {
      System.out.println("ERROR with remuxing of: " + e);
      e.printStackTrace(System.out);
      return false;
    }
    finally
    {
      if (inputStream != null)
      {
        try
        {
          inputStream.close();
        }
        catch (Exception e)
        {
        }
      }
      if (outputStream != null)
      {
        try
        {
          outputStream.close();
        }
        catch (Exception e)
        {
        }
      }
      if (remuxer != null)
        remuxer.close();
    }

    return true;
  }

  public static Remuxer2 openRemuxerForChannel(int inputFormat,
                                               int outputFormat,
                                               StreamFormat streamFormat,
                                               SubFormat subFormat,
                                               int channel,
                                               java.io.OutputStream outStream)
  {
    return openRemuxer(inputFormat, outputFormat, streamFormat, subFormat, TuneStringType.CHANNEL, channel, 0, 0, 0, 0, outStream);
  }

  public static Remuxer2 openRemuxerForProgram(int inputFormat,
                                               int outputFormat,
                                               StreamFormat streamFormat,
                                               SubFormat subFormat,
                                               int program,
                                               java.io.OutputStream outStream)
  {
    return openRemuxer(inputFormat, outputFormat, streamFormat, subFormat, TuneStringType.PROGRAM, 0, 0, program, 0, 0, outStream);
  }

  public static Remuxer2 openRemuxerForTuneString(int inputFormat,
                                                  int outputFormat,
                                                  StreamFormat streamFormat,
                                                  SubFormat subFormat,
                                                  TuneStringType tuneStringType,
                                                  int channel,
                                                  int tsid,
                                                  String tuneString,
                                                  java.io.OutputStream outStream)
  {
    String split[];
    int data1 = 0;
    int data2 = 0;
    int data3 = 0;

    if (tuneStringType == TuneStringType.AUTO)
    {
      int type = tuneString != null ? tuneString.split("-").length : 0;

      switch (type)
      {
        case 1:
          tuneStringType = TuneStringType.PROGRAM;
          break;
        case 2:
          tuneStringType = TuneStringType.XX_XX;
          break;
        case 3:
          tuneStringType = TuneStringType.XX_XX_XX;
          break;
        default:
          tuneStringType = TuneStringType.CHANNEL;
          break;
      }
    }

    try
    {
      switch (tuneStringType)
      {
        case CHANNEL:
          break;
        case PROGRAM:
          if (tuneString != null && !tuneString.isEmpty())
            data1 = Integer.parseInt(tuneString.trim());
          break;
        case XX_XX:
          split = tuneString.split("-");
          if (split.length < 2)
            return null;

          if (streamFormat == StreamFormat.ATSC)
          {
            data2 = Integer.parseInt(split[0].trim());
            data3 = Integer.parseInt(split[1].trim());
          } else
          {
            data1 = Integer.parseInt(split[0].trim());
            data2 = Integer.parseInt(split[1].trim());
          }
          break;
        case XX_XX_XX:
          split = tuneString.split("-");
          if (split.length < 3)
            return null;

          data1 = Integer.parseInt(split[0].trim());
          data2 = Integer.parseInt(split[1].trim());
          data3 = Integer.parseInt(split[2].trim());
          break;
      }
    }
    catch (NumberFormatException e)
    {
      if (Sage.DBG) System.out.println(tuneString + " is not valid. Tune strings must be numeric.");
      return null;
    }

    return openRemuxer(inputFormat, outputFormat, streamFormat, subFormat, tuneStringType, channel, tsid, data1, data2, data3, outStream);
  }

  public static Remuxer2 openRemuxer(int inputFormat,
                                     int outputFormat,
                                     StreamFormat streamFormat,
                                     SubFormat subFormat,
                                     TuneStringType tuneStringType,
                                     int channel,
                                     int tsid,
                                     int data1,
                                     int data2,
                                     int data3,
                                     java.io.OutputStream outStream)
  {
    if ((inputFormat != REMUX_PS && inputFormat != REMUX_TS) ||
        (outputFormat != REMUX_PS && outputFormat != REMUX_TS) ||
        outStream == null)
    {
      return null;
    }

    if (streamFormat == StreamFormat.UNKNOWN)
    {
      streamFormat = StreamFormat.FREE;
      channel = 0;
    }

    long ptr = openRemuxer0(inputFormat, outputFormat, streamFormat.ordinal(), subFormat.ordinal(), (byte) tuneStringType.ordinal(), (short) channel, (short) tsid, (short) data1, (short) data2, (short) data3, outStream);

    if (ptr > 0)
      return new Remuxer2(ptr, inputFormat, outputFormat, streamFormat, subFormat, channel, tsid, data1, data2, data3, outStream);

    if (Sage.DBG) System.out.println("ERROR cannot create rumuxer!");
    return null;
  }

  private static native long openRemuxer0(int inputFormat,
                                          int outputFormat,
                                          int streamFormat,
                                          int subFormat,
                                          byte stringType,
                                          short channel,
                                          short tsid,
                                          short data1,
                                          short data2,
                                          short data3,
                                          java.io.OutputStream outStream);

  private static native void closeRemuxer0(long ptr);

  private static native long pushRemuxData0(long ptr, byte[] buf, int offset, int length);

  private static native String getAvFormat0(long ptr);

  public static class Remuxer2
  {
    private long ptr;

    private boolean init;
    private long initData;
    private String containerString;
    private ContainerFormat containerFormat;

    // Keep a reference to these objects.
    private final int inputFormat;
    private final int outputFormat;
    private final StreamFormat streamType;
    private final SubFormat subFormat;
    private final int channel;
    private final int tsid;
    private final int data1;
    private final int data2;
    private final int data3;
    private final OutputStream outStream;

    private Remuxer2(long ptr,
                     int inputFormat,
                     int outputFormat,
                     StreamFormat streamFormat,
                     SubFormat subFormat,
                     int channel,
                     int tsid,
                     int data1,
                     int data2,
                     int data3,
                     OutputStream outStream)
    {
      this.ptr = ptr;
      this.inputFormat = inputFormat;
      this.outputFormat = outputFormat;
      this.streamType = streamFormat;
      this.subFormat = subFormat;
      this.channel = channel;
      this.tsid = tsid;
      this.data1 = data1;
      this.data2 = data2;
      this.data3 = data3;
      this.outStream = outStream;
      initData = 0;
      init = false;
    }

    private String getContainerString()
    {
      if (containerString == null)
      {
        synchronized (this)
        {
          if (ptr == 0)
            return null;

          String newFormat = getAvFormat0(ptr);

          if (newFormat != null && newFormat.length() > 7)
          {
            // Copy the string.
            containerString = new String(newFormat);
          }
        }
      }

      return containerString;
    }

    /**
     * Get the detected container format.
     * <p/>
     * This will not return a non-null value until initialization has completed.
     *
     * @return The detected container format.
     */
    public ContainerFormat getContainerFormat()
    {
      if (containerFormat == null)
      {
        String newFormat = getContainerString();

        if (newFormat != null)
        {
          containerFormat = ContainerFormat.buildFormatFromString(newFormat.substring(7)); // skip the AV-INF| part
          if (sage.Sage.DBG)
            System.out.println("Detected remuxing format of: " + newFormat);
        }
      }

      return containerFormat;
    }

    /**
     * Push data into the remuxer for initialization.
     * <p/>
     * This method must be used for initialization if you want to know what will be remuxed before
     * anything is written to OutputStream.
     *
     * @param data   Bytes to push into the remuxer.
     * @param offset The offset to start pushing at.
     * @param length The number of bytes to push from the offset.
     * @return true if initialization is complete.
     */
    public boolean pushInitData(byte[] data, int offset, int length)
    {
      // Prevent data from actually being written out by pushing to this method.
      if (init)
        return true;

      synchronized (this)
      {
        if (ptr == 0)
          return false;

        initData += pushRemuxData0(ptr, data, offset, length);

        init = getContainerString() != null;
      }

      return init;
    }

    /**
     * Get the number of bytes used for initialization.
     *
     * @return The number of bytes used for initialization.
     */
    public long getInitData()
    {
      return initData;
    }

    /**
     * Push data into the remuxer.
     * <p/>
     * This method must be used for data to be sent to the provided OutputStream.
     *
     * @param data   Bytes to push into the remuxer.
     * @param offset The offset to start pushing at.
     * @param length The number of bytes to push from the offset.
     * @return The number of bytes used.
     */
    public long pushRemuxData(byte[] data, int offset, int length)
    {
      synchronized (this)
      {
        if (ptr == 0)
          return -1;

        return pushRemuxData0(ptr, data, offset, length);
      }
    }

    /**
     * Close this remuxer instance and clean up.
     * <p/>
     * The OutputStream is not closed for you.
     */
    public void close()
    {
      synchronized (this)
      {
        if (ptr == 0)
          return;

        long tmp = ptr;
        ptr = 0;
        closeRemuxer0(tmp);
      }
    }
  }
}
