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

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class VideoStorage2 implements Cloneable
{
  public static final int VIDEO_DISKSPACE_USE_ONLY = 1;
  public static final int VIDEO_DISKSPACE_LEAVE_FREE = 2;
  public static final int VIDEO_DISKSPACE_USE_ALL = 3;

  private static final Wizard wiz;
  private static final MMC mmc;

  static
  {
    wiz = Wizard.getInstance();
    mmc = MMC.getInstance();
  }

  public static int getRuleForName(String s)
  {
    if (Sage.rez("Diskspace_Use_Only").equalsIgnoreCase(s))
      return VIDEO_DISKSPACE_USE_ONLY;
    else if (Sage.rez("Diskspace_Use_All").equalsIgnoreCase(s))
      return VIDEO_DISKSPACE_USE_ALL;
    else if (Sage.rez("Diskspace_Leave_Free").equalsIgnoreCase(s))
      return VIDEO_DISKSPACE_LEAVE_FREE;
    else
      throw new IllegalArgumentException("Invalid name \"" + s + "\" for diskspace rule");
  }

  private File videoDir;
  private long videoDiskspace;
  private int videoDiskspaceRule;
  // Used to synchronize access to fileSpaceReserves
  private final ReadWriteLock fileSpaceReservesLock;
  // This is a map of either EncoderState->Long or File->Long. Both are used for
  // files that are currently being written, the second one is not due to encoder control but something else
  private Map<Object, Long> fileSpaceReserves;
  // If we've detected that this path is offline
  private boolean offline = false;

  public VideoStorage2(String inVideoDir)
  {
    this(inVideoDir, 20000000000L, VIDEO_DISKSPACE_USE_ALL);
  }
  public VideoStorage2(String inVideoDie, long size, String rule)
  {
    this(inVideoDie, size, getRuleForName(rule));
  }
  public VideoStorage2(String inVideoDir, long size, int rule)
  {
    fileSpaceReservesLock = new ReentrantReadWriteLock();
    videoDir = new File(inVideoDir).getAbsoluteFile();
    IOUtils.safemkdirs(videoDir);
    videoDiskspace = size;
    videoDiskspaceRule = rule;
    fileSpaceReserves = new HashMap<Object, Long>();
  }

  public Object clone()
  {
    try
    {
      return super.clone();
    }
    catch (Exception e){ throw new InternalError("CLONE MISTAKE"); }
  }

  public String prefString()
  {
    return videoDir.getAbsolutePath() + "," + videoDiskspace + "," + videoDiskspaceRule;
  }

  public long getAvailVideoDiskspace()
  {
    if (videoDiskspaceRule == VIDEO_DISKSPACE_USE_ONLY)
    {
      return videoDiskspace - getUsedVideoDiskspace();
    }
    else if (videoDiskspaceRule == VIDEO_DISKSPACE_LEAVE_FREE)
    {
      return FSManager.getInstance().getDiskFreeSpace(videoDir.toString()) - videoDiskspace;
    }
    else
      return FSManager.getInstance().getDiskFreeSpace(videoDir.toString());
  }

  public long getUnreservedVideoDiskspace()
  {
    long rv = getAvailVideoDiskspace();
    fileSpaceReservesLock.readLock().lock();
    try
    {
      for (long val : fileSpaceReserves.values())
      {
        rv -= val;
      }
    }
    finally
    {
      fileSpaceReservesLock.readLock().unlock();
    }
    return rv;
  }

  public long getUsedVideoDiskspace()
  {
    MediaFile[] mFiles = wiz.getFiles();
    long totalSize = 0;
    for (int k = 0; k < mFiles.length; k++)
    {
      if (mFiles[k].getVideoDirectory() != null &&
        videoDir.equals(new File(mFiles[k].getVideoDirectory())))
        totalSize += mFiles[k].getSize();
    }
    return totalSize;
  }

  public String toString()
  {
    return videoDir.getAbsolutePath() + " - " + (videoDiskspaceRule == VIDEO_DISKSPACE_USE_ALL ?
      Sage.rez("Diskspace_Use_All") : ((videoDiskspaceRule == VIDEO_DISKSPACE_USE_ONLY ?
      Sage.rez("Diskspace_Use_Only") : Sage.rez("Diskspace_Leave_Free")) + " " +
      (videoDiskspace/1e9) + " GB"));
  }

  public String getRuleName()
  {
    if (videoDiskspaceRule == VIDEO_DISKSPACE_USE_ONLY)
      return Sage.rez("Diskspace_Use_Only");
    else if (videoDiskspaceRule == VIDEO_DISKSPACE_USE_ALL)
      return Sage.rez("Diskspace_Use_All");
    else if (videoDiskspaceRule == VIDEO_DISKSPACE_LEAVE_FREE)
      return Sage.rez("Diskspace_Leave_Free");
    else
      return "";
  }

  // This is to optimize this statistic so getUsedVideoDiskspace is only called once
  public void addUsedTotalVideoDiskspace(long[] usedTotalRV)
  {
    long currUsed = getUsedVideoDiskspace();
    usedTotalRV[0] += currUsed;
    if (videoDiskspaceRule == VIDEO_DISKSPACE_USE_ONLY)
    {
      usedTotalRV[1] += videoDiskspace;
    }
    else if (videoDiskspaceRule == VIDEO_DISKSPACE_LEAVE_FREE)
    {
      usedTotalRV[1] += currUsed + FSManager.getInstance().getDiskFreeSpace(videoDir.toString()) - videoDiskspace;
    }
    else
      usedTotalRV[1] += currUsed + FSManager.getInstance().getDiskFreeSpace(videoDir.toString());
  }

  public File getVideoDir()
  {
    return videoDir;
  }

  public void setVideoDir(File videoDir)
  {
    this.videoDir = videoDir;
  }

  public long getVideoDiskspace()
  {
    return videoDiskspace;
  }

  public void setVideoDiskspace(long videoDiskspace)
  {
    this.videoDiskspace = videoDiskspace;
  }

  public int getVideoDiskspaceRule()
  {
    return videoDiskspaceRule;
  }

  public void setVideoDiskspaceRule(String videoDiskspaceRule)
  {
    this.videoDiskspaceRule = getRuleForName(videoDiskspaceRule);
  }

  public void setVideoDiskspaceRule(int videoDiskspaceRule)
  {
    this.videoDiskspaceRule = videoDiskspaceRule;
  }


  public void importFileSpaceReserves(VideoStorage2 fileSpaceReserves)
  {
    fileSpaceReservesLock.writeLock().lock();
    try
    {
      this.fileSpaceReserves = fileSpaceReserves.fileSpaceReserves;
    }
    finally
    {
      fileSpaceReservesLock.writeLock().unlock();
    }
  }

  public void reserveFileSpace(File tempDir, long theSize)
  {
    fileSpaceReservesLock.writeLock().lock();
    try
    {
      this.fileSpaceReserves.put(tempDir, theSize);
    }
    finally
    {
      fileSpaceReservesLock.writeLock().unlock();
    }
  }

  public long returnFileSpace(File theFile)
  {
    fileSpaceReservesLock.writeLock().lock();
    try
    {
      Long returnValue = this.fileSpaceReserves.remove(theFile);
      if (returnValue == null)
        returnValue = 0L;
      return returnValue;
    }
    finally
    {
      fileSpaceReservesLock.writeLock().unlock();
    }
  }

  public void reserveEncoderFileSpace(EncoderState2 encoderState, File directories[], long sizes[])
  {
    if (directories.length != sizes.length)
      return;
    fileSpaceReservesLock.writeLock().lock();
    try
    {
      fileSpaceReserves.remove(encoderState);
      for (int i = 0; i < directories.length; i++)
      {
        if (videoDir.equals(directories[i]))
        {
          Long size = fileSpaceReserves.get(encoderState);
          if (size == null)
            size = 0L;
          fileSpaceReserves.put(encoderState, size + sizes[i]);
        }
      }
    }
    finally
    {
      fileSpaceReservesLock.writeLock().unlock();
    }
  }

  public void reserveFileSpace(Object object, long theSize)
  {
    fileSpaceReservesLock.writeLock().lock();
    try
    {
      this.fileSpaceReserves.put(object, theSize);
    }
    finally
    {
      fileSpaceReservesLock.writeLock().unlock();
    }
  }

  public long returnFileSpace(EncoderState2 encoderState)
  {
    fileSpaceReservesLock.writeLock().lock();
    try
    {
      Long returnValue = this.fileSpaceReserves.remove(encoderState);
      if (returnValue == null)
        return 0L;
      return returnValue;
    }
    finally
    {
      fileSpaceReservesLock.writeLock().unlock();
    }
  }

  public boolean isFileSpaceReservesEmpty()
  {
    fileSpaceReservesLock.readLock().lock();
    try
    {
      return this.fileSpaceReserves.isEmpty();
    }
    finally
    {
      fileSpaceReservesLock.readLock().unlock();
    }
  }

  public int fileSpaceReservesSize()
  {
    fileSpaceReservesLock.readLock().lock();
    try
    {
      return this.fileSpaceReserves.size();
    }
    finally
    {
      fileSpaceReservesLock.readLock().unlock();
    }
  }

  public long estimateReservedSpace()
  {
    long needFreeSize = 0;
    fileSpaceReservesLock.readLock().lock();
    try
    {
      for (Object obj : fileSpaceReserves.keySet())
      {
        if (obj instanceof EncoderState2)
        {
          needFreeSize += ((EncoderState2) obj).estimateEncoderReservedSpace(videoDir.toString());
        }
        else if (obj instanceof File)
        {
          File f = (File) obj;
          long totalReqSize = this.fileSpaceReserves.get(obj);
          // If it's a directory, we need to get the size of everything in it
          if (f.isDirectory())
            needFreeSize += totalReqSize - IOUtils.getDirectorySize(f);
          else
            needFreeSize += totalReqSize - f.length();
        }
      }
    }
    finally
    {
      fileSpaceReservesLock.readLock().unlock();
    }
    return needFreeSize;
  }

  public boolean isOffline()
  {
    return offline;
  }

  public void setOffline(boolean offline)
  {
    this.offline = offline;
  }


}
