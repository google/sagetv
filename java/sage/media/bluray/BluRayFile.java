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
package sage.media.bluray;

import sage.media.format.ContainerFormat;
import sage.nio.BufferedFileChannel;
import sage.nio.LocalFileChannel;
import sage.nio.RemoteFileChannel;
import sage.nio.SageFileChannel;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;

public class BluRayFile extends FileChannel implements BluRayStreamer, SageFileChannel
{
  private final SageFileChannel sageFileChannel;
  private final long totalSize;
  private final java.io.File bdmvDir;
  private final sage.media.bluray.BluRayParser bdp;
  private final sage.media.bluray.MPLSObject currPlaylist;
  private final java.io.File[] fileSequence;
  private final long[] fileOffsets; // bytes
  private final long[] ptsOffsets; // 45kHz
  private final long[] chapterOffsets; // 45kHz
  private int currFileIndex;
  private final java.io.File streamDir;
  private int prefAngle;
  private final int targetTitle;

  /**
   * Create a new <code>BluRayStreamer</code> for a local file.
   *
   * @param bdmvDir The directory containing the bluray.
   * @param directBuffer Use a direct byte buffer?
   * @param targetTitle The title to select first.
   * @param readBufferSize The size to be used for the read buffer.
   * @throws IOException If there is an I/O related error.
   */
  public BluRayFile(java.io.File bdmvDir, boolean directBuffer, int targetTitle, int readBufferSize) throws IOException
  {
    this(null, bdmvDir, directBuffer, targetTitle, readBufferSize);
  }

  /**
   * Create a new <code>BluRayStreamer</code> for a remote file.
   *
   * @param hostname The hostname of the server hosting the bluray content.
   * @param bdmvDir The directory containing the bluray.
   * @param directBuffer Use a direct byte buffer?
   * @param targetTitle The title to select first.
   * @param readBufferSize The size to be used for the read buffer.
   * @throws IOException If there is an I/O related error.
   */
  public BluRayFile(String hostname, java.io.File bdmvDir, boolean directBuffer, int targetTitle, int readBufferSize) throws IOException
  {
    if (hostname == null)
      // Creates
      sageFileChannel = new BufferedFileChannel(new LocalFileChannel((String)null, true), readBufferSize, directBuffer);
    else
      // Opens a socket, but not any files. We will do that with openFile().
      sageFileChannel = new BufferedFileChannel(new RemoteFileChannel(hostname, (String)null, true), readBufferSize, directBuffer);

    this.bdmvDir = bdmvDir;
    int targetTitleTmp = targetTitle;
    bdp = new sage.media.bluray.BluRayParser(bdmvDir, hostname);
    bdp.fullyAnalyze();
    if (targetTitleTmp <= 0)
      targetTitleTmp = bdp.getMainPlaylistIndex() + 1;
    this.targetTitle = Math.max(1, Math.min(targetTitleTmp, bdp.getNumPlaylists()));
    currPlaylist = bdp.getPlaylist(targetTitleTmp - 1);

    fileSequence = new java.io.File[currPlaylist.playlistItems.length];
    fileOffsets = new long[fileSequence.length];
    ptsOffsets = new long[fileSequence.length];
    streamDir = new java.io.File(bdmvDir, "STREAM");
    long[] totalPts = new long[fileSequence.length];
    long totalSizeTmp = 0;
    if (hostname == null)
    {
      // This is a little more efficient when we have direct access to the file system.
      for (int i = 0; i < fileSequence.length; i++)
      {
        fileSequence[i] = new java.io.File(streamDir, currPlaylist.playlistItems[i].itemClips[0].clipName + (bdp.doesUseShortFilenames() ? ".MTS" : ".m2ts"));
        fileOffsets[i] = totalSizeTmp;
        ptsOffsets[i] = (i == 0 ? 0 : totalPts[i - 1]) - currPlaylist.playlistItems[i].inTime;
        totalSizeTmp += fileSequence[i].length();
        totalPts[i] = (i == 0 ? 0 : totalPts[i - 1]) + (currPlaylist.playlistItems[i].outTime - currPlaylist.playlistItems[i].inTime);
      }
    }
    else
    {
      for (int i = 0; i < fileSequence.length; i++)
      {
        fileSequence[i] = new java.io.File(streamDir, currPlaylist.playlistItems[i].itemClips[0].clipName + (bdp.doesUseShortFilenames() ? ".MTS" : ".m2ts"));
        fileOffsets[i] = totalSizeTmp;
        ptsOffsets[i] = (i == 0 ? 0 : totalPts[i - 1]) - currPlaylist.playlistItems[i].inTime;
        openFile(fileSequence[i].getAbsolutePath());
        totalSizeTmp += sageFileChannel.size();
        totalPts[i] = (i == 0 ? 0 : totalPts[i - 1]) + (currPlaylist.playlistItems[i].outTime - currPlaylist.playlistItems[i].inTime);
      }
    }
    totalSize = totalSizeTmp;
    if (sage.Sage.DBG) System.out.println("Established BluRay file sequence with " + fileSequence.length + " segments and total size=" + totalSizeTmp);
    currFileIndex = 0;
    openFile(fileSequence[currFileIndex].getAbsolutePath());

    chapterOffsets = new long[currPlaylist.playlistMarks.length];
    for (int i = 0; i < chapterOffsets.length; i++)
    {
      int itemRef = currPlaylist.playlistMarks[i].playItemIdRef;
      chapterOffsets[i] = (itemRef == 0 ? 0 : totalPts[itemRef - 1]) + currPlaylist.playlistMarks[i].timestamp - currPlaylist.playlistItems[itemRef].inTime;
    }
  }

  private void ensureProperFile(boolean forceOpen) throws java.io.IOException
  {
    ensureProperFile(forceOpen, position());
  }

  // pos is the offset as if all of the files were appended one after the other into a bigger file.
  private void ensureProperFile(boolean forceOpen, long pos) throws java.io.IOException
  {
    // Check to see if we need to move to a different file
    if (forceOpen || pos < fileOffsets[currFileIndex] || (currFileIndex < fileOffsets.length - 1 && pos >= fileOffsets[currFileIndex + 1]))
    {
      int oldIndex = currFileIndex;
      for (currFileIndex = 0; currFileIndex < fileOffsets.length; currFileIndex++)
      {
        if (fileOffsets[currFileIndex] > pos)
          break;
      }
      currFileIndex--;
      if (sage.Sage.DBG) System.out.println("Switching BluRay source file from index " + oldIndex + " to " + currFileIndex);
      int currAngle = Math.min(prefAngle, currPlaylist.playlistItems[currFileIndex].itemClips.length - 1);
      sageFileChannel.openFile(
          new java.io.File(streamDir,
              currPlaylist.playlistItems[currFileIndex].itemClips[currAngle].clipName +
                  (bdp.doesUseShortFilenames() ? ".MTS" : ".m2ts")).getAbsolutePath());
    }
  }

  private long realPosition()
  {
    return position() - fileOffsets[currFileIndex];
  }

  @Override
  public long getBytesLeftInClip()
  {
    return (currFileIndex < fileOffsets.length - 1) ? (fileOffsets[currFileIndex + 1] - position()) : (totalSize - position());
  }

  @Override
  public int getCurrClipIndex()
  {
    return currFileIndex;
  }

  @Override
  public long getClipPtsOffset(int index)
  {
    return ptsOffsets[index];
  }

  @Override
  public int getClipIndexForNextRead()
  {
    if (getBytesLeftInClip() == 0 && currFileIndex < fileOffsets.length - 1)
      return currFileIndex + 1;
    else
      return currFileIndex;
  }

  @Override
  public ContainerFormat getFileFormat()
  {
    return bdp.getFileFormat(targetTitle - 1);
  }

  @Override
  public int getTitle()
  {
    return targetTitle;
  }

  @Override
  public long getChapterStartMsec(int chapter)
  {
    return chapterOffsets[Math.max(0, Math.min(chapter - 1, chapterOffsets.length - 1))] / 45;
  }

  @Override
  public int getNumTitles()
  {
    return bdp.getNumPlaylists();
  }

  @Override
  public int getNumChapters()
  {
    return chapterOffsets.length;
  }

  @Override
  public int getChapter(long pts45)
  {
    for (int i = 0; i < chapterOffsets.length; i++)
      if (chapterOffsets[i] > pts45)
        return i;
    return chapterOffsets.length;
  }

  @Override
  public String getTitleDesc(int titleNum)
  {
    return bdp.getPlaylistDesc(titleNum - 1);
  }

  @Override
  public int getNumAngles()
  {
    return currPlaylist.playlistItems[currFileIndex].itemClips.length;
  }

  @Override
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException
  {
    long bytesRead = 0;

    try
    {
      ensureProperFile(false, position);
      bytesRead = sageFileChannel.transferTo(position - fileOffsets[currFileIndex], count, target);

      if (bytesRead < count)
      {
        ensureProperFile(false, position);
        bytesRead += sageFileChannel.transferTo(position + bytesRead - fileOffsets[currFileIndex], count - bytesRead, target);
      }
    }
    finally
    {
      ensureProperFile(false);
    }

    return bytesRead;
  }

  @Override
  public long transferTo(long count, WritableByteChannel target) throws IOException
  {
    long bytesRead = sageFileChannel.transferTo(count, target);

    if (bytesRead < count)
    {
      ensureProperFile(false);
      bytesRead += sageFileChannel.transferTo(count - bytesRead, target);
    }

    return bytesRead;
  }

  @Override
  public void openFile(String name) throws IOException
  {
    sageFileChannel.openFile(name);
  }

  @Override
  public void openFile(File file) throws IOException
  {
    sageFileChannel.openFile(file);
  }

  /**
   * Unsupported method. This channel can only be read only.
   *
   * @throws IOException throws channel is read only.
   */
  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException
  {
    throw new NonWritableChannelException();
  }

  /**
   * Unsupported method. This channel can only be read only.
   *
   * @throws IOException throws channel is read only.
   */
  @Override
  public long transferFrom(ReadableByteChannel src, long count) throws IOException
  {
    throw new NonWritableChannelException();
  }

  /**
   * Unsupported method. This channel can only be read only.
   *
   * @throws IOException throws channel is read only.
   */
  @Override
  public int write(ByteBuffer src, long position) throws IOException
  {
    throw new NonWritableChannelException();
  }

  /**
   * Unsupported method.
   *
   * @throws IOException throws method is unsupported.
   */
  @Override
  public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException
  {
    throw new IOException("Method is unsupported.");
  }

  /**
   * Unsupported method.
   *
   * @throws IOException throws method is unsupported.
   */
  @Override
  public FileLock lock(long position, long size, boolean shared) throws IOException
  {
    throw new IOException("Method is unsupported.");
  }

  /**
   * Unsupported method.
   *
   * @throws IOException throws method is unsupported.
   */
  @Override
  public FileLock tryLock(long position, long size, boolean shared) throws IOException
  {
    throw new IOException("Method is unsupported.");
  }

  @Override
  public int read(ByteBuffer dst, long position) throws IOException
  {
    int readBytes = sageFileChannel.read(dst, position - fileOffsets[currFileIndex]);

    if (dst.hasRemaining())
    {
      ensureProperFile(false);
      if (readBytes == -1)
        readBytes = 0;
      readBytes += sageFileChannel.read(dst, position + readBytes - fileOffsets[currFileIndex]);
    }

    return readBytes;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException
  {
    int readBytes = sageFileChannel.read(dst);

    if (dst.hasRemaining())
    {
      ensureProperFile(false);
      if (readBytes == -1)
        readBytes = 0;
      readBytes += sageFileChannel.read(dst);
    }

    return readBytes;
  }

  @Override
  public int readUnsignedByte() throws IOException
  {
    return sageFileChannel.readUnsignedByte();
  }

  /**
   * Unsupported method. This channel can only be read only.
   *
   * @throws IOException throws channel is read only.
   */
  @Override
  public int write(ByteBuffer src) throws IOException
  {
    throw new NonWritableChannelException();
  }

  @Override
  public long position()
  {
    return sageFileChannel.position() + fileOffsets[currFileIndex];
  }

  @Override
  public long skip(long n) throws IOException
  {
    long seek = Math.min(position() + n, totalSize);
    ensureProperFile(false, seek);
    return sageFileChannel.skip(n - fileOffsets[currFileIndex]);
  }

  @Override
  public FileChannel position(long newPosition) throws IOException
  {
    ensureProperFile(false, newPosition);
    sageFileChannel.position(newPosition - fileOffsets[currFileIndex]);
    return this;
  }

  @Override
  public long size() throws IOException
  {
    return totalSize;
  }

  /**
   * Unsupported method. This channel can only be read only.
   *
   * @throws IOException throws channel is read only.
   */
  @Override
  public FileChannel truncate(long size) throws IOException
  {
    throw new NonWritableChannelException();
  }

  /**
   * Unsupported method. This channel can only be read only.
   *
   * @throws IOException throws channel is read only.
   */
  @Override
  public void force(boolean metaData) throws IOException
  {
    throw new NonWritableChannelException();
  }

  /**
   * A bluray cannot be a recording, so this always returns <code>false</code>.
   *
   * @return <code>false</code>
   */
  @Override
  public boolean isActiveFile()
  {
    return false;
  }

  /**
   * A bluray is always read only, so this always returns <code>true</code>.
   *
   * @return <code>true</code>
   */
  @Override
  public boolean isReadOnly()
  {
    return true;
  }

  @Override
  public String executeCommand(String command) throws IOException
  {
    return sageFileChannel.executeCommand(command);
  }

  @Override
  public String executeCommand(ByteBuffer command) throws IOException
  {
    return sageFileChannel.executeCommand(command);
  }

  /**
   * Unsupported method. This channel can only be read only.
   *
   * @throws IOException throws channel is read only.
   */
  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException
  {
    throw new NonWritableChannelException();
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException
  {
    long bytesRead = sageFileChannel.read(dsts, offset, length);

    if (bytesRead == -1)
    {
      ensureProperFile(false);
      bytesRead = sageFileChannel.read(dsts, offset, length);
    }

    return bytesRead;
  }
  @Override
  protected void implCloseChannel() throws IOException
  {
    sageFileChannel.close();
  }
}
