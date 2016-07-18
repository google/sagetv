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

import sage.io.SageFileSource;

import java.io.File;
import java.io.IOException;

public final class Mpeg2Transcoder
{
  private SageFileSource ins;
  private File mpegFile;
  private String hostname;

  // Used for variable rate playback. If this is not 1, then we're only trying to send I frames and we
  // do some seeking before and after this
  private int playbackRate = 1;
  private boolean log;
  private long handle;

  /* Creates new Mpeg2Transcoder */
  public Mpeg2Transcoder(String filename)
  {
    this(new File(filename));
  }

  public Mpeg2Transcoder(File theFile)
  {
    this(theFile, null);
  }

  public Mpeg2Transcoder(File theFile, String inHostname)
  {
    mpegFile = theFile;
    hostname = inHostname;
    System.out.println("Creating Mpeg2Transcoder");
    System.loadLibrary("Mpeg2Transcoder");
  }

  public void setLogging(boolean x)
  {
    log = x;
  }

  public void init(boolean findFirstPTS, boolean findDuration) throws IOException
  {
    System.out.println("Opening "+mpegFile.getAbsolutePath());
    handle = openTranscode0(mpegFile.getAbsolutePath());
  }

  public void setPlaybackRate(int playRate)
  {
    if (playbackRate != playRate)
      if (Sage.DBG) System.out.println("Mpeg2Transcoder changing playrate to " + playRate);
    playbackRate = playRate;
  }

  public void setParseLevel(int x)
  {
    //        parseLevel = x;
  }

  public long getDurationMillis()
  {
    return getDurationMillis0(handle);
  }

  public long getFirstTimestampMillis()
  {
    return getFirstTime0(handle);
  }

  public long getLastParsedTimeMillis()
  {
    return getLastParsedTime0(handle);
  }

  public void close()
  {
    closeTranscode0(handle);
  }

  public long length()
  {
    return -1; // No length
  }

  public long availableToRead()
  {
    return availableToRead0(handle);
  }

  public long getReadPos()
  {
    return -1; // No read pos
  }

  public int read(byte[] buf, int off, int len) throws IOException
  {
    // Bounds checking
    off = Math.max(0, Math.min(off, buf.length - 1));
    len = Math.max(0, Math.min(buf.length - off, len));
    return read0(handle, buf, off, len);
  }

  public void seek(long seekTime) throws IOException
  {
    seek0(handle, seekTime);
  }

  protected native long openTranscode0(String filename);
  protected native void closeTranscode0(long handle);
  protected native long getFirstTime0(long handle);
  protected native long getLastParsedTime0(long handle);
  protected native long getDurationMillis0(long handle);
  protected native void seek0(long handle, long seekTime);
  protected native long availableToRead0(long handle);
  protected native int read0(long handle, byte[] buf, int off, int len);
}
