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

public class DVDFile implements DVDSource
{
  private int fileOffset; // Block position within the block device/image file
  private DVDBlockReader reader; // block reader that contains this file

  private sage.FasterRandomFile file; // File object when in file mode

  private int filePosition; // Position for byte reads, only use for small files!

  // We have two modes of operation either block device or files, each has its constructor
  DVDFile(int startBlock, DVDBlockReader reader)
  {
    this.fileOffset=startBlock;
    this.reader=reader;
  }

  DVDFile(String filename, String hostname)
  {
    try
    {
      file = (hostname == null) ? new sage.FasterRandomFile(filename, "r", sage.Sage.I18N_CHARSET) :
        new sage.NetworkChannelFile(hostname, filename, "r", sage.Sage.I18N_CHARSET, false);
      file.setOptimizeReadFully(false); // since we always read 2k blocks
    }
    catch(Exception e)
    {
      return;
    }
  }

  public void close()
  {
    if(file!=null)
    {
      try
      {
        file.close();
      }
      catch(Exception e) {}
    }
  }

  // Read blocks relative to the start of the file
  public boolean readBlocks(byte [] buf, int offset, int block, int count)
  {
    try
    {
      file.seek(block*2048L);
      file.readFully(buf, offset, count*2048);
      return true;
    }
    catch(Exception e)
    {
      return false;
    }
  }

  public int seek(int position)
  {
    filePosition=position;
    return position;
  }

  // Read bytes at last seeked position
  public int readBytes(byte [] buf, int size)
  {
    if(file!=null)
    {
      try
      {
        file.seek(filePosition);
        file.readFully(buf, 0, size);
        filePosition+=size;
        return size;
      }
      catch(Exception e)
      {
        return 0;
      }
    }
    return 0;
  }
}
