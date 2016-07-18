/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Adapted from Ogle - A video player
 * Copyright (C) 2000, 2001 Håkan Hjort
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111, USA.
 */
package sage.dvd;

import sage.nio.BufferedFileChannel;
import sage.nio.LocalFileChannel;
import sage.nio.RemoteFileChannel;

/**
 *
 * @author Narflex
 */
public class DVDMultiFile implements DVDSource
{

  /** Creates a new instance of DVDMultiFile */
  public DVDMultiFile()
  {
  }

  private long fileOffset; // Block position within the block device/image file
  private BufferedFileChannel[] files; // File object when in file mode
  private long[] fileSizes;

  // We have two modes of operation either block device or files, each has its constructor
  DVDMultiFile(String[] filenames, String hostname)
  {
    files = new sage.nio.BufferedFileChannel[filenames.length];
    fileSizes = new long[filenames.length];
    try
    {
      for (int i = 0; i < filenames.length; i++)
      {
        if (hostname == null)
          files[i] = new BufferedFileChannel(new LocalFileChannel(filenames[i], true), 65536, false);
        else
          files[i] = new BufferedFileChannel(new RemoteFileChannel(hostname, filenames[i], true), 131072, false);

        fileSizes[i] = files[i].size();
      }
    }
    catch(Exception e)
    {
      return;
    }
  }

  public void close()
  {
    if(files!=null)
    {
      for (int i = 0; i < files.length; i++)
      {
        try
        {
          files[i].close();
        }
        catch(Exception e) {}
      }
    }
  }

  // Read blocks relative to the start of the file
  public boolean readBlocks(byte [] buf, int offset, int block, int count)
  {
    try
    {
      long readOffset = block * 2048L;
      for (int i = 0; i < files.length; i++)
      {
        if (readOffset < fileSizes[i])
        {
          files[i].position(readOffset);
          int readThisTime = Math.min(count*2048, (int)(fileSizes[i] - readOffset));
          files[i].readFully(buf, offset, readThisTime);
          count -= readThisTime;
          offset += readThisTime;
          if (count <= 0)
            return true;
        }
        readOffset -= fileSizes[i];
      }
      return false;
    }
    catch(Exception e)
    {
      return false;
    }
  }

  public int seek(int position)
  {
    throw new UnsupportedOperationException("Cannot seek in DVDMultiFile");
  }

  // Read bytes at last seeked position
  public int readBytes(byte [] buf, int size)
  {
    throw new UnsupportedOperationException("Cannot seek in DVDMultiFile");
  }
}
