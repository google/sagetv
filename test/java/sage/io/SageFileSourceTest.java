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
package sage.io;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import sage.FastRandomFile;
import sage.FasterRandomFile;
import sage.TestDataUtils;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

import static sage.TestData.*;
import static sage.TestDataUtils.fillLocalFile;

public class SageFileSourceTest
{
  private static String filename = System.getProperty("io_test_file");

  @DataProvider
  private static Object[][] getSageFileSource() throws IOException
  {
    final Object returnObject[][] = new Object[8][2];

    // If the most basic part of this chain doesn't work, everything else will be broken.
    fillLocalFile(filename + "LocalSageFile.ro", readContents, readContents.length);
    returnObject[0][0] = new sage.io.LocalSageFile(filename + "LocalSageFile.ro", true);
    fillLocalFile(filename + "LocalSageFile.rw", readContents, readContents.length);
    returnObject[1][0] = new sage.io.LocalSageFile(filename + "LocalSageFile.rw", false);

    // This is probably the most common configuration.
    fillLocalFile(filename + "BufferedSageFile.ro", readContents, readContents.length);
    returnObject[2][0] = new sage.io.BufferedSageFile(new sage.io.LocalSageFile(filename + "BufferedSageFile.ro", true));
    fillLocalFile(filename + "BufferedSageFile.rw", readContents, readContents.length);
    returnObject[3][0] = new sage.io.BufferedSageFile(new sage.io.LocalSageFile(filename + "BufferedSageFile.rw", false));

    // This is an unlikely combination.
    fillLocalFile(filename + "SageDataFile.ro", readContents, readContents.length);
    returnObject[4][0] = new sage.io.SageDataFile(new sage.io.LocalSageFile(filename + "SageDataFile.ro", true), "UTF-8");
    fillLocalFile(filename + "SageDataFile.rw", readContents, readContents.length);
    returnObject[5][0] = new sage.io.SageDataFile(new sage.io.LocalSageFile(filename + "SageDataFile.rw", false), "UTF-8");

    // This is a common configuration.
    fillLocalFile(filename + "SageDataFile.BufferedSageFile.ro", readContents, readContents.length);
    returnObject[6][0] = new sage.io.SageDataFile(new BufferedSageFile(new sage.io.LocalSageFile(filename + "SageDataFile.BufferedSageFile.ro", true)), "UTF-8");
    fillLocalFile(filename + "SageDataFile.BufferedSageFile.rw", readContents, readContents.length);
    returnObject[7][0] = new sage.io.SageDataFile(new BufferedSageFile(new sage.io.LocalSageFile(filename + "SageDataFile.BufferedSageFile.rw", false)), "UTF-8");

    for (int i = 0; i < returnObject.length; i++)
    {
      returnObject[i][1] = readContents.length;
    }

    return returnObject;
  }

  private static void randomRead(FastRandomFile source, byte[] data, int offset, int size, boolean randomForward, boolean randomBackward) throws IOException
  {

    // This needs to be wiped every time or the verified results might be invalid.
    Arrays.fill(verifyBuffer, (byte)0xFF);
    source.seek(offset);

    int readForward = size / 50;
    int backRead = size / 500;
    int forwardRead = size / 500;
    int i = offset;
    while (i < size)
    {
      // Increase read block size to larger than the buffer.
      if (i > size - (BufferedSageFile.READ_BUFFER_SIZE * 4))
        readForward += BufferedSageFile.READ_BUFFER_SIZE;

      if (readForward > size - i)
        readForward = size - i;

      source.readFully(verifyBuffer, i, readForward);

      i += readForward;

      int bytesRead;
      try
      {
        bytesRead = source.read();
      }
      catch (EOFException e)
      {
        bytesRead = -1;
      }

      if (bytesRead == -1)
        break;

      if (i < size)
        verifyBuffer[i++] = (byte)bytesRead;

      if (randomBackward && i > backRead)
      {
        source.seek(i - backRead);
        i -= backRead;
        source.readFully(verifyBuffer, i, backRead);
        i += backRead;
      }

      if (randomForward && i + (forwardRead * 2) < size)
      {
        source.seek(i + forwardRead);
        source.readFully(verifyBuffer, i + forwardRead, forwardRead);
        source.seek(i);
        // Spot check that these match up.
        TestDataUtils.verifyBytes(i, data[i + forwardRead], verifyBuffer[i + forwardRead]);
        forwardRead += forwardRead;
      }
    }

    TestDataUtils.verifyAndClear(0, 0, size, data, verifyBuffer);
  }

  private static void randomRead(SageFileSource source, byte[] data, int offset, int size, boolean randomForward, boolean randomBackward) throws IOException
  {

    // This needs to be wiped every time or the verified results might be invalid.
    Arrays.fill(verifyBuffer, (byte)0xFF);
    source.seek(offset);

    int readForward = size / 50;
    int backRead = size / 500;
    int forwardRead = size / 500;
    int i = offset;
    while (i < size)
    {
      // Increase read block size to larger than the buffer.
      if (i > size - (BufferedSageFile.READ_BUFFER_SIZE * 4))
        readForward += BufferedSageFile.READ_BUFFER_SIZE;

      if (readForward > size - i)
        readForward = size - i;

      int bytesRead = source.read(verifyBuffer, i, readForward);

      if (bytesRead == -1)
        break;

      i += bytesRead;

      if (i == verifyBuffer.length)
      {
        source.seek(i);
        i = 0;
      }

      bytesRead = source.read();

      if (bytesRead == -1)
        break;

      // If this creates an index out of bounds exception, you likely have an EOF detection problem.
      verifyBuffer[i++] = (byte) bytesRead;

      if (randomBackward && i > backRead)
      {
        source.seek(i - backRead);
        i -= backRead;
        bytesRead = source.read(verifyBuffer, i, backRead);
        if (bytesRead == -1)
          break;
        i += bytesRead;
      }

      if (randomForward && i + (forwardRead * 2) < size)
      {
        source.seek(i + forwardRead);
        bytesRead = source.read(verifyBuffer, i + forwardRead, forwardRead);
        source.seek(i);
        // Spot check that these match up.
        TestDataUtils.verifyBytes(i, data[i + forwardRead], verifyBuffer[i + forwardRead]);
        forwardRead += forwardRead;
      }
    }

    TestDataUtils.verifyAndClear(0, 0, size, data, verifyBuffer);
  }

  private static void randomWrite(SageFileSource source, byte[] data, byte[] altdata, int offset, int size, boolean randomForward, boolean randomBackward) throws IOException
  {
    // This needs to be wiped every time or the verified results might be invalid.
    Arrays.fill(verifyBuffer, (byte)0xFF);
    source.seek(offset);

    int writeForward = size / 50;
    int randomWriteBack = size / 500;
    int randomWriteForward = size / 500;
    int i = offset;
    while (i < size)
    {
      // Increase write block size to larger than the buffer.
      if (i > size - (BufferedSageFile.WRITE_BUFFER_SIZE * 4))
        writeForward += BufferedSageFile.WRITE_BUFFER_SIZE;

      if (writeForward > size - i)
        writeForward = size - i;

      source.write(data, i, writeForward);

      i += writeForward;

      if (i < size)
        source.write(data[i++]);

      if (randomBackward && i > randomWriteBack)
      {
        source.randomWrite(i - randomWriteBack, data, i - randomWriteBack, randomWriteBack);
        randomWriteBack += randomWriteBack;
      }

      if (randomForward && i + (randomWriteForward * 2) < size)
      {
        // This data should be ultimately overwritten, so we write incorrect data to ensure that
        // actually happens.
        source.randomWrite(i + randomWriteForward, altdata, i + randomWriteForward, randomWriteForward);
        randomWriteForward += randomWriteForward;
      }
    }

    source.seek(0);
    source.readFully(verifyBuffer, 0, size);
    TestDataUtils.verifyAndClear(0, 0, size, data, verifyBuffer);
  }

  // Verify FastRandomFile is still working correctly.
  @Test(groups = {"io", "fastRandomFile" })
  public void testFastRandomFile() throws IOException
  {
    FastRandomFile fastRandomFile = new FastRandomFile(filename  + "FastRandomFile.rw", "rw", "UTF-8");
    fastRandomFile.seek(0);
    fastRandomFile.write(readContents, 0, readContents.length);
    fastRandomFile.setLength(readContents.length);
    fastRandomFile.seek(0);
    randomRead(fastRandomFile, readContents, 0, readContents.length, true, true);
    randomRead(fastRandomFile, readContents, 0, readContents.length, true, false);
    randomRead(fastRandomFile, readContents, 0, readContents.length, false, true);
    randomRead(fastRandomFile, readContents, 0, readContents.length, false, false);
    fastRandomFile.close();
  }

  // Verify FasterRandomFile is still working correctly.
  @Test(groups = {"io", "fasterRandomFile" })
  public void testFasterRandomFile() throws IOException
  {
    FasterRandomFile fasterRandomFile = new FasterRandomFile(filename + "FasterRandomFile.rw", "rw", "UTF-8");
    fasterRandomFile.seek(0);
    fasterRandomFile.write(readContents, 0, readContents.length);
    fasterRandomFile.setLength(readContents.length);
    fasterRandomFile.seek(0);
    randomRead(fasterRandomFile, readContents, 0, readContents.length, true, true);
    randomRead(fasterRandomFile, readContents, 0, readContents.length, true, false);
    randomRead(fasterRandomFile, readContents, 0, readContents.length, false, true);
    randomRead(fasterRandomFile, readContents, 0, readContents.length, false, false);
    fasterRandomFile.close();
  }

  // This needs to be done separate because it can only read its own writing.
  @Test(groups = {"io", "encrypted" })
  public void testSageFileSourceEncrypted() throws IOException
  {
    SageFileSource source = new EncryptedSageFile(new LocalSageFile(filename + "EncryptedSageFile.rw", false), cryptKey);
    source.seek(0);
    source.write(readContents, 0, readContents.length);
    source.setLength(readContents.length);
    source.seek(0);
    randomRead(source, readContents, 0, readContents.length, true, true);
    randomRead(source, readContents, 0, readContents.length, true, false);
    randomRead(source, readContents, 0, readContents.length, false, true);
    randomRead(source, readContents, 0, readContents.length, false, false);
    randomWrite(source, writeContents, readContents, 0, writeContents.length, true, true);
    randomWrite(source, writeContents, readContents, 0, writeContents.length, true, false);
    randomWrite(source, writeContents, readContents, 0, writeContents.length, false, true);
    randomWrite(source, writeContents, readContents, 0, writeContents.length, false, false);
    source.close();
  }

  // Test the data file functions.
  @Test(groups = {"io", "sageDataFile" })
  public void testSageDataFile() throws IOException
  {
    // Ensure we have data to work with.
    fillLocalFile(filename + "DataFileTest.rw", readContents, readContents.length);
    SageDataFile source = new SageDataFile(new EncryptedSageFile(new BufferedSageFile(new LocalSageFile(filename + "DataFileTest.rw", false)), cryptKey), "UTF-8");
    source.seek(0);
    long seek = 0;

    // Test that readUnencryptedByte() works when encryption is present.
    int crypt = source.readByte();
    source.seek(seek);
    int readByte = source.readUnencryptedByte();
    assert crypt != readByte : "Encryption is not engaged.";
    source.seek(seek);
    // Test that writeUnencryptedByte() bypasses the encryption.
    source.writeUnencryptedByte((byte)10);
    source.seek(seek);
    readByte = source.readUnencryptedByte();
    assert (byte)10 == readByte : "expected 10, got " + readByte;

    // Remove encryption layer. Verify that the previous results are valid.
    source = new SageDataFile(source.getUnencryptedSource(), "UTF-8");
    source.seek(seek);
    crypt = source.readByte();
    source.seek(seek);
    readByte = source.readUnencryptedByte();
    assert crypt == readByte : "No encryption, but bytes read from the same location are the same.";
    seek += 1;

    // Test boolean returns.
    source.writeBoolean(false);
    source.writeBoolean(true);
    source.write(0);
    source.write(1);
    source.seek(seek);
    boolean bool = source.readBoolean();
    assert !bool : "boolean is not false.";
    bool = source.readBoolean();
    assert bool : "boolean is not true.";
    bool = source.readBoolean();
    assert !bool : "boolean is not false.";
    bool = source.readBoolean();
    assert bool : "boolean is not true.";
    seek += 4;

    source.writeByte(127);
    source.seek(seek);
    readByte = source.read();
    assert readByte == 127 : "expected 127, got " + readByte;
    seek += 4;

    // Make sure the write buffer is available at 0.
    source.seek(0);
    source.write((byte)255);
    source.seek(seek);

    source.writeInt(392839203);
    // This write should be within the write buffer. If the logic is wrong, it will miss the buffer
    // and hit the disk and the write will be overwritten by the buffer.
    source.writeIntAtOffset(0, 293029857);
    long pos = source.position();
    source.writeIntAtOffset(BufferedSageFile.WRITE_BUFFER_SIZE, 283029857);
    source.writeIntAtOffset(BufferedSageFile.WRITE_BUFFER_SIZE - 4, 293019856);
    source.seek(BufferedSageFile.WRITE_BUFFER_SIZE);
    int integer = source.readInt();
    assert integer == 283029857 : "Expected 283029857, got " + integer;
    source.seek(BufferedSageFile.WRITE_BUFFER_SIZE - 4);
    integer = source.readInt();
    assert integer == 293019856 : "Expected 293019856, got " + integer;
    source.seek(pos);
    source.writeFloat(3843927393782938473.0F);
    source.writeDouble(3018227393782.990473);
    source.writeLong(3843927393782938473L);
    source.sync();
    source.writeShort((short)32700);
    source.seek(0);
    integer = source.readInt();
    assert integer == 293029857 : "Expected 293029857, got " + integer;
    source.seek(seek);
    integer = source.readInt();
    assert integer == 392839203 : "Expected 392839203, got " + integer;
    float flt = source.readFloat();
    assert flt == 3843927393782938473.0F : "Expected 3843927393782938473.0, got " + flt;
    double dbl = source.readDouble();
    assert dbl == 3018227393782.990473 : "Expected 3018227393782.990473, got " + dbl;
    long lng = source.readLong();
    assert lng == 3843927393782938473L : "Expected 3843927393782938473, got " + lng ;
    short srt = source.readShort();
    assert srt == (short)32700 : "Expected 32700, got " + srt;

    // Write aligned with exact start of write buffer.
    seek = source.position();
    source.writeByte(0);
    source.skip(3);
    source.write(writeContents, 0, 3929);
    source.writeIntAtOffset(seek, 3929);
    source.seek(seek);
    integer = source.readInt();
    assert integer == 3929 : "Expected 3929, got " + integer;

    // Write misaligned by -3 from start of write buffer.
    seek = source.position();
    source.skip(3);
    source.writeByte(0);
    source.write(writeContents, 0, 28374);
    source.writeIntAtOffset(seek, 28374);
    source.seek(seek);
    integer = source.readInt();
    assert integer == 28374 : "Expected 28374, got " + integer;

    // We can't do any character tests because they reference the Sage class which tries to load
    // tons of native code. If we try to make improvements to the character code, this will need to
    // be fixed so it will be properly tested.

    source.close();
  }

  // This tests general functionality and does not do any kind of read/write byte verification.
  @Test(groups = {"io", "general" }, dataProvider = "getSageFileSource")
  public void testSageFileSourceGeneral(SageFileSource source, int size) throws IOException
  {
    // The file needs to be twice the read buffer size or we aren't testing seeking correctly.
    assert size > BufferedSageFile.READ_BUFFER_SIZE * 2 : "This test needs at least 64k. Fix the source.";

    // Get a baseline. If we can't even seek to the start of the file, it's all downhill.
    int seek = 0;
    source.seek(seek);
    assert source.position() == seek : "seek(" + seek + ") did not make position == " + seek;
    assert source.length() == source.available() : "length != available";

    // Verify that read only exceptions are working.
    if (source.isReadOnly())
    {
      try
      {
        source.write(0xFF);
        assert false : "Read only test failed.";
      }
      catch (IOException e) {}

      try
      {
        source.write(writeContents, 0, 1);
        assert false : "Read only test failed.";
      }
      catch (IOException e) {}

      try
      {
        source.write(cryptKey); // Because it's only 128 bytes.
        assert false : "Read only test failed.";
      }
      catch (IOException e) {}
    }

    // Unaligned outside of buffer.
    seek = size / 3;
    source.seek(seek);
    assert source.position() == seek : "seek(" + seek + ") did not make position == " + seek + ", got " + source.position();

    // Trigger the read buffer to be populated.
    source.read();
    seek += 1024; // Inside buffer.
    source.seek(seek);
    assert source.position() == seek : "seek(" + seek + ") did not make position == " + seek + ", got " + source.position();
    assert size - seek == source.available() : "available=" + source.available() + ", expected " + (size - seek);

    // Inside buffer.
    seek += 2048;
    source.seek(seek);
    assert source.position() == seek : "seek(" + seek + ") did not make position == " + seek + ", got " + source.position();
    assert size - seek == source.available() : "available=" + source.available() + ", expected " + (size - seek);

    // One byte before buffer.
    seek -= 3073;
    source.seek(seek);
    assert source.position() == seek : "seek(" + seek + ") did not make position == " + seek + ", got " + source.position();
    assert size - seek == source.available() : "available=" + source.available() + ", expected " + (size - seek);

    // Now that something is probably buffered, verify this doesn't effect the returned length.
    assert source.length() == size : "length=" + source.length() + " != size=" + size;

    if (!source.isReadOnly())
    {
      // Test buffered writing.
      source.write(cryptKey);
      seek += cryptKey.length;
      assert source.position() == seek : "write(byte[]=" + cryptKey.length + ") did not make position == " + seek + ", got " + source.position();
      source.sync();
      assert source.position() == seek : "sync() did retain position == " + seek + ", got " + source.position();

      source.write(writeContents, 0, 256);
      seek += 256;
      assert source.position() == seek : "write(byte[]=" + writeContents.length + ", 0, 256) did not make position == " + seek + ", got " + source.position();

      // Cross from before, through, then after the write buffer.
      // 4097 to keep it out of perfect alignment.
      for (int i = -4097; i < BufferedSageFile.WRITE_BUFFER_SIZE + BufferedSageFile.WRITE_BUFFER_SIZE + 1; i += 4096)
      {
        // 4095 to keep it out of perfect alignment.
        source.randomWrite(seek + i, writeContents, 512, 4095);
        assert source.position() == seek : "randomWrite(" + (seek + i)  + ", byte[]=" + writeContents.length + ", 512, 4095) did not keep position == " + seek + ", got " + source.position();
        assert source.length() == size : "randomWrite increased file size from " + size + " to " + source.length();
      }

      source.sync();
      source.write(writeContents, 0, 1024);
      seek += 1024;

      // Cross from after, through, then before the write buffer.
      // 4097 to keep it out of perfect alignment.
      for (int i = BufferedSageFile.WRITE_BUFFER_SIZE + BufferedSageFile.WRITE_BUFFER_SIZE + 1; i > -4097; i -= 4096)
      {
        // 4095 to keep it out of perfect alignment.
        source.randomWrite(seek + i, writeContents, 512, 4095);
        assert source.position() == seek : "randomWrite(" + (seek + i)  + ", byte[]=" + writeContents.length + ", 512, 4095) did not keep position == " + seek + ", got " + source.position();
        assert source.length() == size : "randomWrite increased file size from " + size + " to " + source.length();
      }

      source.sync();
      assert source.position() == seek  : "sync() did keep position == " + seek + ", got " + source.position();
      source.seek(source.length());
      assert source.position() == size : "seek(" + source.length() + ") did not make position == " + size + ", got " + source.position();
      source.write(writeContents, 0, 256);
      assert source.length() == size + 256 : "length did not increase to " + (size + 256) + ", got " + source.length();

      // Shrink file
      seek -= 2048;
      source.setLength(seek);
      assert source.length() == seek : "truncated smaller than position and position != length";

      // Grow file
      source.setLength(size);
      assert source.position() == seek : "set length larger than position and position=" + source.length() + ", expected " + seek;
    }

    source.seek(source.length()); // Make sure available reports nothing left to read.
    assert source.available() == 0 : "EOF and available=" + source.available();
    seek = source.read(); // Make sure EOF condition works.
    assert seek == -1 : "EOF and a byte was read: " + seek;
    seek = source.read(verifyBuffer);
    assert seek == -1 : "EOF and a byte was read: " + seek;
    seek = source.read(verifyBuffer, 0, 1);
    assert seek == -1 : "EOF and a byte was read: " + seek;
    try
    {
      source.readFully(verifyBuffer);
      assert false : "EOFException not thrown.";
    }
    // If some other exception is thrown, it is not the right one, so we will fail on that too.
    catch (EOFException e) {}
    try
    {
      source.readFully(verifyBuffer, 0, 1);
      assert false : "EOFException not thrown.";
    }
    // If some other exception is thrown, it is not the right one, so we will fail on that too.
    catch (EOFException e) {}

    // Test that seek past the end of the file just takes you to the end of the file.
    seek = size - 2048;
    source.seek(seek);
    seek += source.skip(8196);
    assert source.position() == seek : "skip(8196) got position " + source.position() + ", expected " + seek;

    // Test skip.
    seek = size / 8;
    source.seek(seek);
    source.skip(4096);
    seek = seek + 4096;
    assert source.position() == seek : "skip(8196) got position " + source.position() + ", expected " + seek;

    source.close();
  }

  @Test(groups = {"io", "read" }, dataProvider = "getSageFileSource")
  public void testSageFileSourceReadOnly(SageFileSource source, int size) throws IOException
  {
    source.seek(0);
    randomRead(source, readContents, 0, size, false, false);
    source.close();
  }

  @Test(groups = {"io", "write" }, dataProvider = "getSageFileSource")
  public void testSageFileSourceWriteOnly(SageFileSource source, int size) throws IOException
  {
    source.seek(0);
    try
    {
      randomWrite(source, writeContents, readContents, 0, size, false, false);
    }
    catch (IOException e)
    {
      // We expect an exception if the file is read only.
      if (!source.isReadOnly())
        throw new IOException(e);
    }
    finally
    {
      source.close();
    }
  }

  @Test(groups = {"io", "randomWrite" }, dataProvider = "getSageFileSource", threadPoolSize = 1)
  public void testSageFileSourceRandomWrite(SageFileSource source, int size) throws IOException
  {
    source.seek(0);
    try
    {
      randomWrite(source, writeContents, readContents, 0, size, true, false);
      randomWrite(source, writeContents, readContents, 0, size, false, true);
      randomWrite(source, writeContents, readContents, 0, size, true, true);
    }
    catch (IOException e)
    {
      // We expect an exception if the file is read only.
      if (!source.isReadOnly())
        throw new IOException(e);
    }
    finally
    {
      source.close();
    }
  }

  @Test(groups = {"io", "randomRead" }, dataProvider = "getSageFileSource", threadPoolSize = 1)
  public void testSageFileSourceRandomRead(SageFileSource source, int size) throws IOException
  {
    source.seek(0);
    randomRead(source, readContents, 0, size, true, false);
    randomRead(source, readContents, 0, size, false, true);
    randomRead(source, readContents, 0, size, true, true);
    source.close();
  }
}
