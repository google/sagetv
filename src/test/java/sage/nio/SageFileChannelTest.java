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
import java.nio.channels.NonWritableChannelException;

import static sage.TestData.*;
import static sage.TestDataUtils.fillLocalFile;
import static sage.TestDataUtils.verifyAndClear;

public class SageFileChannelTest
{
  private static String filename = System.getProperty("nio_test_file");

  // We are using different sized byte buffers because this should expose any buffer overflow or
  // underflow situations. We also test on heap and direct since there could be performance
  // optimizations based on the difference.

  // These should be bigger than the internal buffering.
  private static ByteBuffer heapLarge = ByteBuffer.allocate(BufferedFileChannel.DEFAULT_READ_SIZE * 2);
  private static ByteBuffer directLarge = ByteBuffer.allocateDirect(BufferedFileChannel.DEFAULT_READ_SIZE * 2);
  // These should be the same size as the internal buffering.
  private static ByteBuffer heap = ByteBuffer.allocate(BufferedFileChannel.DEFAULT_READ_SIZE);
  private static ByteBuffer direct = ByteBuffer.allocateDirect(BufferedFileChannel.DEFAULT_READ_SIZE);
  // These should be the half the size as the internal buffering.
  private static ByteBuffer heapSmall = ByteBuffer.allocate(BufferedFileChannel.DEFAULT_READ_SIZE / 2);
  private static ByteBuffer directSmall = ByteBuffer.allocateDirect(BufferedFileChannel.DEFAULT_READ_SIZE / 2);

  @DataProvider
  private static Object[][] getFileChannel() throws IOException
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
  private static Object[][] getSageFileChannel() throws IOException
  {
    final Object returnObject[][] = new Object[4][2];

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

    for (int i = 0; i < returnObject.length; i++)
    {
      returnObject[i][1] = readContents.length;
    }

    return returnObject;
  }

  public static void randomReadWrite(FileChannel source, ByteBuffer data) throws IOException
  {
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

    // Ensure the file size is correct.
    source.truncate(writeContents.length);
    assert source.size() == writeContents.length : "file size is wrong, expected " + writeContents.length + ", got " + source.size();
    // Fill file via byte buffer.
    source.position(0);
    int remains = writeContents.length;
    int offset = 0;
    while (remains > 0)
    {
      data.clear();
      data.put(writeContents, offset, Math.min(remains, data.remaining()));
      data.flip();
      offset += data.remaining();
      remains -= data.remaining();
      source.write(data);
    }
    source.position(0);

    // Read data written to byte buffer.
    offset = 0;
    while (offset < writeContents.length)
    {
      data.clear();
      source.read(data);
      data.flip();
      int length = data.remaining();
      data.get(verifyBuffer, offset, length);
      offset += length;
    }

    verifyAndClear(0, 0, writeContents.length, writeContents, verifyBuffer);
    assert source.size() == writeContents.length : "file size is wrong, expected " + writeContents.length + ", got " + source.size();

    // Fill file via byte buffer by random position.
    remains = writeContents.length;
    offset = 0;
    while (remains > 0)
    {
      data.clear();
      data.put(writeContents, offset, Math.min(remains, data.remaining()));
      data.flip();
      int length = data.remaining();
      source.write(data, offset);
      offset += length;
      remains -= length;
    }

    // Read data written to byte buffer.
    offset = 0;
    while (offset < writeContents.length)
    {
      data.clear();
      source.read(data, offset);
      data.flip();
      int length = data.remaining();
      data.get(verifyBuffer, offset, length);
      offset += length;
    }

    verifyAndClear(0, 0, writeContents.length, writeContents, verifyBuffer);
    assert source.size() == writeContents.length : "file size is wrong, expected " + writeContents.length + ", got " + source.size();

    // Fill file via transferTo, then out with transferFrom
    // This is a very weak ByteChannel implementation, but it works well for testing.
    TestByteChannel byteChannel = new TestByteChannel(data);

    remains = writeContents.length;
    offset = 0;
    while (remains > 0)
    {
      data.clear();
      data.put(writeContents, offset, Math.min(remains, data.remaining()));
      data.flip();
      int length = data.remaining();
      length = (int)source.transferFrom(byteChannel, offset, length);
      offset += length;
      remains -= length;
    }

    // Read data written to byte buffer.
    offset = 0;
    while (offset < writeContents.length)
    {
      data.clear();
      int length = data.remaining();
      source.transferTo(offset, length, byteChannel);
      data.flip();
      length = data.remaining();
      data.get(verifyBuffer, offset, length);
      offset += length;
    }

    verifyAndClear(0, 0, writeContents.length, writeContents, verifyBuffer);
    assert source.size() == writeContents.length : "file size is wrong, expected " + writeContents.length + ", got " + source.size();
  }

  @Test(groups = {"nio", "read" }, dataProvider = "getFileChannel")
  public void testFileChannelGeneral(FileChannel source, int size) throws IOException
  {

    try
    {
      randomReadWrite(source, heap);
      randomReadWrite(source, direct);
      randomReadWrite(source, heapLarge);
      randomReadWrite(source, directLarge);
      randomReadWrite(source, heapSmall);
      randomReadWrite(source, directSmall);
    }
    catch (NonWritableChannelException e)
    {
      // This is the only acceptable exception since some of
      // the tests are being done with read only sources.
    }

    source.close();
  }

  @Test(groups = {"nio", "general" }, dataProvider = "getSageFileChannel")
  public void testSageFileChannelGeneral(SageFileChannel source, int size) throws IOException
  {


    source.close();
  }
}
