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

import java.io.IOException;
import java.nio.channels.NonWritableChannelException;

import static sage.TestData.direct;
import static sage.TestData.readContents;
import static sage.TestDataUtils.fillLocalFile;

public class SageFileChannelTest
{
  private static String filename = System.getProperty("nio_test_file");

  // SageFileChannel implementations also extend FileChannel and those methods plus
  // transferTo(long, WritableByteChannel) and transferFrom(ReadableByteChannel, long) are tested in
  // another class, so we will only test what else is unique to SageFileChannel here.

  @DataProvider
  private static Object[][] getSageFileChannel() throws IOException
  {
    final Object returnObject[][] = new Object[4][2];

    // If the most basic part of this chain doesn't work, everything else will be broken.
    fillLocalFile(filename + "LocalSageFileChannel.ro", readContents, readContents.length);
    returnObject[0][0] = new sage.nio.LocalFileChannel(filename + "LocalSageFileChannel.ro", true);
    fillLocalFile(filename + "LocalSageFileChannel.rw", readContents, readContents.length);
    returnObject[1][0] = new sage.nio.LocalFileChannel(filename + "LocalSageFileChannel.rw", false);

    // This is probably the most common configuration.
    fillLocalFile(filename + "BufferedSageFileChannel.ro", readContents, readContents.length);
    returnObject[2][0] = new sage.nio.BufferedFileChannel(new sage.nio.LocalFileChannel(filename + "BufferedSageFileChannel.ro", true), true);
    fillLocalFile(filename + "BufferedSageFileChannel.rw", readContents, readContents.length);
    returnObject[3][0] = new sage.nio.BufferedFileChannel(new sage.nio.LocalFileChannel(filename + "BufferedSageFileChannel.rw", false), true);

    for (int i = 0; i < returnObject.length; i++)
    {
      returnObject[i][1] = readContents.length;
    }

    return returnObject;
  }

  @Test(groups = {"nio", "sageFileChannel", "general" }, dataProvider = "getSageFileChannel")
  public void testSageFileChannelGeneral(SageFileChannel source, int size) throws IOException
  {
    if (source.isReadOnly())
    {
      direct.clear();
      direct.put((byte)0).flip();
      try
      {
        source.write(direct);
        assert false : "Read only test failed.";
      }
      catch (NonWritableChannelException e) {}

      direct.rewind();
      try
      {
        source.write(direct, 0);
        assert false : "Read only test failed.";
      }
      catch (NonWritableChannelException e) {}

      direct.rewind();
      try
      {
        source.transferFrom(new TestByteChannel(direct), 0);
        assert false : "Read only test failed.";
      }
      catch (NonWritableChannelException e) {}
    }
    else
    {
      direct.position(0);
      direct.clear();
      direct.put((byte)0).flip();
      source.write(direct);
      direct.rewind();
      source.write(direct, 0);
      direct.rewind();
      source.transferFrom(new TestByteChannel(direct), 0);
    }

    // This will indicated for writing that the position was not set to 0. For read only, this will
    // indicate that some writing might have actually happened.
    assert size == source.size() : "unexpected length, expected " + size + ", got " + source.size();

    // trigger the read buffer to be populated, then skip beyond it.
    long position = size / 2;
    source.position(position);
    source.readUnsignedByte();
    source.skip(65536);
    assert source.position() == position + 1 + 65536 : "unexpected position, expected " + (position + 1 + 65536) + ", got " + source.position();

    // trigger the read buffer to be populated, then skip within it.
    position = size / 3;
    source.position(position);
    source.readUnsignedByte();
    source.skip(16384);
    assert source.position() == position + 1 + 16384 : "unexpected position, expected " + (position + 1 + 16384) + ", got " + source.position();

    // trigger the read buffer to be populated, then skip behind it.
    position = size / 4;
    source.position(position);
    source.readUnsignedByte();
    source.position(source.position() - 2);
    assert source.position() == position - 1 : "unexpected position, expected " + (position - 1) + ", got " + source.position();

    source.close();
  }
}
