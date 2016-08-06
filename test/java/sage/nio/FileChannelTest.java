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
package sage.nio;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import sage.io.BufferedSageFile;
import sage.io.LocalSageFile;
import sage.io.SageIOFileChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static sage.TestData.*;
import static sage.TestDataUtils.*;

public class FileChannelTest
{
  private static String filename = System.getProperty("nio_test_file");

  // We don't support the following methods. If there's a reason to start, they will be
  // implemented, but the way we typically access files in SageTV, these make no practical sense.
  //source.lock(long, long, boolean)
  //source.lock()
  //source.tryLock(long, long, boolean)
  //source.tryLock()
  //source.map(MapMode, long, long)
  // Also even though they are implemented, since we don't currently use GatheringByteChannel or
  // ScatteringByteChannel and testing them would add this already lengthy test, those tests are
  // missing too.
  //source.read(ByteBuffer[], int, int)
  //source.read(ByteBuffer[])
  //source.write(ByteBuffer[], int, int)
  //source.write(ByteBuffer[])

  @DataProvider
  private static Object[][] getFileChannelReadWrite() throws IOException
  {
    final Object returnObject[][] = new Object[6][2];

    // If the most basic part of this chain doesn't work, everything else will be broken.
    fillLocalFile(filename + "LocalFileChannel.ro", readContents, readContents.length);
    returnObject[0][0] = new sage.nio.LocalFileChannel(filename + "LocalFileChannel.ro", true);
    fillLocalFile(filename + "LocalFileChannel.rw", readContents, readContents.length);
    returnObject[1][0] = new sage.nio.LocalFileChannel(filename + "LocalFileChannel.rw", false);

    // This is probably the most common configuration.
    fillLocalFile(filename + "BufferedFileChannel.ro", readContents, readContents.length);
    returnObject[2][0] = new sage.nio.BufferedFileChannel(new sage.nio.LocalFileChannel(filename + "BufferedFileChannel.ro", true), true);
    fillLocalFile(filename + "BufferedFileChannel.rw", readContents, readContents.length);
    returnObject[3][0] = new sage.nio.BufferedFileChannel(new sage.nio.LocalFileChannel(filename + "BufferedFileChannel.rw", false), true);

    // This is a wrapper in case IO for some reason is the only option for the underlying source.
    fillLocalFile(filename + "SageIOFileChannel.ro", readContents, readContents.length);
    returnObject[4][0] = new SageIOFileChannel(new BufferedSageFile(new LocalSageFile(filename + "SageIOFileChannel.ro", true)));
    fillLocalFile(filename + "SageIOFileChannel.rw", readContents, readContents.length);
    returnObject[5][0] = new SageIOFileChannel(new BufferedSageFile(new LocalSageFile(filename + "SageIOFileChannel.rw", false)));

    for (int i = 0; i < returnObject.length; i++)
    {
      returnObject[i][1] = readContents.length;
    }

    return returnObject;
  }

  @DataProvider
  private static Object[][] getFileChannelWrite() throws IOException
  {
    final Object returnObject[][] = new Object[3][2];

    // If the most basic part of this chain doesn't work, everything else will be broken.
    fillLocalFile(filename + "LocalFileChannel.rw", readContents, readContents.length);
    returnObject[0][0] = new sage.nio.LocalFileChannel(filename + "LocalFileChannel.rw", false);

    // This is probably the most common configuration.
    fillLocalFile(filename + "BufferedFileChannel.rw", readContents, readContents.length);
    returnObject[1][0] = new sage.nio.BufferedFileChannel(new sage.nio.LocalFileChannel(filename + "BufferedFileChannel.rw", false), true);

    // This is a wrapper in case IO for some reason is the only option for the underlying source.
    fillLocalFile(filename + "SageIOFileChannel.rw", readContents, readContents.length);
    returnObject[2][0] = new SageIOFileChannel(new BufferedSageFile(new LocalSageFile(filename + "SageIOFileChannel.rw", false)));

    for (int i = 0; i < returnObject.length; i++)
    {
      returnObject[i][1] = readContents.length;
    }

    return returnObject;
  }

  private static final int BYTE_BUFFER_TEST = 0;
  private static final int BYTE_BUFFER_OFFSET_TEST = 1;
  private static final int BYTE_CHANNEL_OFFSET_TEST = 2;
  private static final int BYTE_CHANNEL_TEST = 3;
  public static void randomReadWrite(FileChannel source, ByteBuffer data, boolean seekForward, boolean seekBack, boolean readonly, final int testType) throws IOException
  {
    if (!readonly)
    {
      // Ensure the file size is correct.
      source.truncate(writeContents.length);
      // Verify according to the source, the size changed.
      assert source.size() == writeContents.length : "file size is wrong, expected " + writeContents.length + ", got " + source.size();
    }

    SageFileChannel sageFileSource;
    if (testType == BYTE_CHANNEL_TEST)
    {
      if (source instanceof SageFileChannel)
      {
        sageFileSource = (SageFileChannel) source;
      }
      // SageIOFileChannel is not a part of SageFileChannel, but it does implement FileChannel.
      else if (source instanceof SageIOFileChannel)
      {
        return;
      }
      else
      {
        throw new IOException("Using byte channel without an offset is only available in SageFileChannel. Fix the test(s).");
      }
    }
    else
    {
      sageFileSource = null;
    }

    // Fill file via transferTo, then out with transferFrom
    // This is a very weak ByteChannel implementation, but we just need something predictable.
    TestByteChannel byteChannel;
    if (testType == BYTE_CHANNEL_OFFSET_TEST || testType == BYTE_CHANNEL_TEST)
    {
      byteChannel = new TestByteChannel(data);
    }
    else
    {
      byteChannel = null;
    }

    boolean needSeek = testType == BYTE_BUFFER_TEST || testType == BYTE_CHANNEL_TEST;

    // Fill file via byte buffer.
    source.position(0);
    int remains = writeContents.length;
    int offset = 0;
    int length = 0;
    // Low random numbers just out of alignment.
    int skipBack = 1025 + testType;
    int skipForward = 511 - testType;
    if (!readonly)
    {
      while (remains > 0)
      {
        data.clear();
        data.put(writeContents, offset, Math.min(remains, data.remaining()));
        data.flip();
        switch (testType)
        {
          case BYTE_BUFFER_TEST:
            length = source.write(data);
            break;
          case BYTE_BUFFER_OFFSET_TEST:
            length = source.write(data, offset);
            break;
          case BYTE_CHANNEL_OFFSET_TEST:
            length = (int)source.transferFrom(byteChannel, offset, data.remaining());
            break;
          case BYTE_CHANNEL_TEST:
            length = (int)sageFileSource.transferFrom(byteChannel, data.remaining());
            break;
        }
        offset += length;
        remains -= length;

        if (seekForward && offset + (skipForward * 2) < writeContents.length)
        {
          offset += skipForward;
          remains -= skipForward;

          if (needSeek)
          {
            source.position(offset);
          }

          // This will be overwritten, so we want to write different
          // data to reveal if that doesn't happen.
          data.clear();
          data.put(readContents, offset, Math.min(skipForward, data.remaining()));
          data.flip();
          int compare = data.remaining();
          switch (testType)
          {
            case BYTE_BUFFER_TEST:
              length = source.write(data);
              break;
            case BYTE_BUFFER_OFFSET_TEST:
              length = source.write(data, offset);
              break;
            case BYTE_CHANNEL_OFFSET_TEST:
              length = (int)source.transferFrom(byteChannel, offset, data.remaining());
              break;
            case BYTE_CHANNEL_TEST:
              length = (int)sageFileSource.transferFrom(byteChannel, data.remaining());
              break;
          }
          assert length == compare : "Read length=" + length + " != remaining=" + compare;
          // Return to where we left off.
          offset -= skipForward;
          remains += skipForward;

          if (needSeek)
          {
            source.position(offset);
          }

          skipForward = Math.min(skipForward * 2, data.capacity() / 2);
        }

        if (seekBack && offset - (skipBack * 2) > 0 && offset < writeContents.length - skipBack * 2)
        {
          offset -= skipBack;
          if (needSeek)
          {
            source.position(offset);
          }
          remains += skipBack;
          skipBack = Math.min(skipBack * 2, data.capacity() / 4);
        }
      }
      source.position(0);

      // Read data written to byte buffer.
      offset = 0;

      // Low random numbers just out of alignment, but different than the random write pattern.
      skipBack = 1023 - testType;
      skipForward = 513 + testType;
    }

    while (offset < writeContents.length)
    {
      data.clear();
      switch (testType)
      {
        case BYTE_BUFFER_TEST:
          length = source.read(data);
          break;
        case BYTE_BUFFER_OFFSET_TEST:
          length = source.read(data, offset);
          break;
        case BYTE_CHANNEL_OFFSET_TEST:
          length = (int)source.transferTo(offset, data.remaining(), byteChannel);
          break;
        case BYTE_CHANNEL_TEST:
          length = (int)sageFileSource.transferTo(data.remaining(), byteChannel);
          break;
      }
      if (length == -1)
        break;
      data.flip();
      assert length == data.remaining() : "Read length=" + length + " != remaining=" + data.remaining();
      data.get(verifyBuffer, offset, length);
      offset += length;

      if (seekForward && offset + (skipForward * 2) < writeContents.length)
      {
        offset += skipForward;

        if (needSeek)
        {
          source.position(offset);
        }

        data.clear();
        switch (testType)
        {
          case BYTE_BUFFER_TEST:
            length = source.read(data);
            break;
          case BYTE_BUFFER_OFFSET_TEST:
            length = source.read(data, offset);
            break;
          case BYTE_CHANNEL_OFFSET_TEST:
            length = (int)source.transferTo(offset, data.remaining(), byteChannel);
            break;
          case BYTE_CHANNEL_TEST:
            length = (int)sageFileSource.transferTo(data.remaining(), byteChannel);
            break;
        }

        if (length == -1)
        {
          if (needSeek)
          {
            throw new IOException("Byte channels do not return -1.");
          }
        }
        else
        {
          data.flip();
          assert length == data.remaining() : "Length read length=" + length + " != remaining=" + data.remaining();

          // If you get an array out of bounds exception here, that means the file either grew or the
          // positioning logic is wrong.
          data.get(verifyBuffer, offset, length);
          offset += 1;
          // Spot check.
          if (readonly)
            verifyBytes(offset, readContents[offset], verifyBuffer[offset]);
          else
            verifyBytes(offset, writeContents[offset], verifyBuffer[offset]);

          offset -= 1;
        }

        // Return to where we left off. Otherwise we will have holes.
        offset -= skipForward;
        if (needSeek)
        {
          source.position(offset);
        }

        skipForward = Math.min(skipForward * 2, data.capacity() / 2);
      }

      if (seekBack && offset - (skipBack * 2) > 0 && offset < writeContents.length - skipBack * 2)
      {
        offset -= skipBack;
        if (needSeek)
        {
          source.position(offset);
        }
        skipBack = Math.min(skipBack * 2, data.capacity() / 2);
      }
    }

    verifyAndClear(0, 0, writeContents.length, readonly ? readContents : writeContents, verifyBuffer);
    // Check if some extra data somehow was committed.
    assert source.size() == writeContents.length : "file size is wrong, expected " + writeContents.length + ", got " + source.size();
  }

  @Test(groups = {"nio", "fileChannel", "randomReadWrite" }, dataProvider = "getFileChannelWrite")
  public void testFileChannelRandomReadWrite(FileChannel source, int size) throws IOException
  {
    randomReadWrite(source, heap, true, true, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, direct, true, true, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapLarge, true, true, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, directLarge, true, true, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapSmall, true, true, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, directSmall, true, true, false, BYTE_BUFFER_TEST);

    randomReadWrite(source, heap, true, false, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, direct, true, false, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapLarge, true, false, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, directLarge, true, false, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapSmall, true, false, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, directSmall, true, false, false, BYTE_BUFFER_TEST);

    randomReadWrite(source, heap, false, true, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, direct, false, true, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapLarge, false, true, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, directLarge, false, true, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapSmall, false, true, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, directSmall, false, true, false, BYTE_BUFFER_TEST);

    randomReadWrite(source, heap, true, true, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, direct, true, true, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapLarge, true, true, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directLarge, true, true, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapSmall, true, true, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directSmall, true, true, false, BYTE_BUFFER_OFFSET_TEST);

    randomReadWrite(source, heap, true, false, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, direct, true, false, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapLarge, true, false, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directLarge, true, false, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapSmall, true, false, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directSmall, true, false, false, BYTE_BUFFER_OFFSET_TEST);

    randomReadWrite(source, heap, false, true, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, direct, false, true, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapLarge, false, true, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directLarge, false, true, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapSmall, false, true, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directSmall, false, true, false, BYTE_BUFFER_OFFSET_TEST);

    randomReadWrite(source, heap, true, true, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, direct, true, true, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapLarge, true, true, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directLarge, true, true, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapSmall, true, true, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directSmall, true, true, false, BYTE_CHANNEL_OFFSET_TEST);

    randomReadWrite(source, heap, true, false, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, direct, true, false, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapLarge, true, false, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directLarge, true, false, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapSmall, true, false, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directSmall, true, false, false, BYTE_CHANNEL_OFFSET_TEST);

    randomReadWrite(source, heap, false, true, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, direct, false, true, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapLarge, false, true, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directLarge, false, true, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapSmall, false, true, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directSmall, false, true, false, BYTE_CHANNEL_OFFSET_TEST);

    randomReadWrite(source, heap, true, true, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, direct, true, true, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapLarge, true, true, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directLarge, true, true, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapSmall, true, true, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directSmall, true, true, false, BYTE_CHANNEL_TEST);

    randomReadWrite(source, heap, true, false, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, direct, true, false, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapLarge, true, false, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directLarge, true, false, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapSmall, true, false, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directSmall, true, false, false, BYTE_CHANNEL_TEST);

    randomReadWrite(source, heap, false, true, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, direct, false, true, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapLarge, false, true, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directLarge, false, true, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapSmall, false, true, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directSmall, false, true, false, BYTE_CHANNEL_TEST);

    source.close();
  }

  @Test(groups = {"nio", "fileChannel", "sequentialReadWrite" }, dataProvider = "getFileChannelWrite")
  public void testFileChannelSequentialReadWrite(FileChannel source, int size) throws IOException
  {
    randomReadWrite(source, heap, false, false, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, direct, false, false, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapLarge, false, false, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, directLarge, false, false, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapSmall, false, false, false, BYTE_BUFFER_TEST);
    randomReadWrite(source, directSmall, false, false, false, BYTE_BUFFER_TEST);

    randomReadWrite(source, heap, false, false, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, direct, false, false, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapLarge, false, false, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directLarge, false, false, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapSmall, false, false, false, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directSmall, false, false, false, BYTE_BUFFER_OFFSET_TEST);

    randomReadWrite(source, heap, false, false, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, direct, false, false, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapLarge, false, false, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directLarge, false, false, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapSmall, false, false, false, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directSmall, false, false, false, BYTE_CHANNEL_OFFSET_TEST);

    randomReadWrite(source, heap, false, false, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, direct, false, false, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapLarge, false, false, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directLarge, false, false, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapSmall, false, false, false, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directSmall, false, false, false, BYTE_CHANNEL_TEST);

    source.close();
  }

  @Test(groups = {"nio", "fileChannel", "randomRead" }, dataProvider = "getFileChannelReadWrite")
  public void testFileChannelRandomRead(FileChannel source, int size) throws IOException
  {
    randomReadWrite(source, heap, true, true, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, direct, true, true, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapLarge, true, true, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, directLarge, true, true, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapSmall, true, true, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, directSmall, true, true, true, BYTE_BUFFER_TEST);

    randomReadWrite(source, heap, true, false, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, direct, true, false, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapLarge, true, false, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, directLarge, true, false, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapSmall, true, false, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, directSmall, true, false, true, BYTE_BUFFER_TEST);

    randomReadWrite(source, heap, false, true, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, direct, false, true, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapLarge, false, true, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, directLarge, false, true, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapSmall, false, true, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, directSmall, false, true, true, BYTE_BUFFER_TEST);

    randomReadWrite(source, heap, true, true, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, direct, true, true, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapLarge, true, true, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directLarge, true, true, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapSmall, true, true, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directSmall, true, true, true, BYTE_BUFFER_OFFSET_TEST);

    randomReadWrite(source, heap, true, false, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, direct, true, false, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapLarge, true, false, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directLarge, true, false, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapSmall, true, false, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directSmall, true, false, true, BYTE_BUFFER_OFFSET_TEST);

    randomReadWrite(source, heap, false, true, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, direct, false, true, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapLarge, false, true, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directLarge, false, true, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapSmall, false, true, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directSmall, false, true, true, BYTE_BUFFER_OFFSET_TEST);

    randomReadWrite(source, heap, true, true, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, direct, true, true, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapLarge, true, true, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directLarge, true, true, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapSmall, true, true, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directSmall, true, true, true, BYTE_CHANNEL_OFFSET_TEST);

    randomReadWrite(source, heap, true, false, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, direct, true, false, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapLarge, true, false, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directLarge, true, false, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapSmall, true, false, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directSmall, true, false, true, BYTE_CHANNEL_OFFSET_TEST);

    randomReadWrite(source, heap, false, true, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, direct, false, true, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapLarge, false, true, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directLarge, false, true, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapSmall, false, true, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directSmall, false, true, true, BYTE_CHANNEL_OFFSET_TEST);

    randomReadWrite(source, heap, true, true, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, direct, true, true, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapLarge, true, true, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directLarge, true, true, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapSmall, true, true, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directSmall, true, true, true, BYTE_CHANNEL_TEST);

    randomReadWrite(source, heap, true, false, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, direct, true, false, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapLarge, true, false, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directLarge, true, false, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapSmall, true, false, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directSmall, true, false, true, BYTE_CHANNEL_TEST);

    randomReadWrite(source, heap, false, true, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, direct, false, true, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapLarge, false, true, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directLarge, false, true, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapSmall, false, true, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directSmall, false, true, true, BYTE_CHANNEL_TEST);
  }

  @Test(groups = {"nio", "fileChannel", "sequentialRead" }, dataProvider = "getFileChannelReadWrite")
  public void testFileChannelSequentialRead(FileChannel source, int size) throws IOException
  {
    randomReadWrite(source, heap, false, false, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, direct, false, false, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapLarge, false, false, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, directLarge, false, false, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, heapSmall, false, false, true, BYTE_BUFFER_TEST);
    randomReadWrite(source, directSmall, false, false, true, BYTE_BUFFER_TEST);

    randomReadWrite(source, heap, false, false, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, direct, false, false, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapLarge, false, false, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directLarge, false, false, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, heapSmall, false, false, true, BYTE_BUFFER_OFFSET_TEST);
    randomReadWrite(source, directSmall, false, false, true, BYTE_BUFFER_OFFSET_TEST);

    randomReadWrite(source, heap, false, false, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, direct, false, false, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapLarge, false, false, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directLarge, false, false, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, heapSmall, false, false, true, BYTE_CHANNEL_OFFSET_TEST);
    randomReadWrite(source, directSmall, false, false, true, BYTE_CHANNEL_OFFSET_TEST);

    randomReadWrite(source, heap, false, false, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, direct, false, false, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapLarge, false, false, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directLarge, false, false, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, heapSmall, false, false, true, BYTE_CHANNEL_TEST);
    randomReadWrite(source, directSmall, false, false, true, BYTE_CHANNEL_TEST);

    source.close();
  }
}
