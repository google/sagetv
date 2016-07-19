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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class LocalSageFile implements SageFileSource
{
  private final RandomAccessFile randomAccessFile;
  private final boolean readonly;

  /**
   * Create a local instance of <code>SageFileSource</code>.
   *
   * @param file This is the file to be opened.
   * @param readonly Open the file in read only mode.
   * @throws IOException If there is an I/O related error.
   */
  public LocalSageFile(File file, boolean readonly) throws IOException
  {
    randomAccessFile = new RandomAccessFile(file.getPath(), readonly ? "r" : "rw");
    this.readonly = readonly;
  }

  /**
   * Create a local instance of <code>SageFileSource</code>.
   *
   * @param name This is the full path and name of the file to be opened.
   * @param readonly Open the file in read only mode.
   * @throws IOException If there is an I/O related error.
   */
  public LocalSageFile(String name, boolean readonly) throws IOException
  {
    randomAccessFile = new RandomAccessFile(name, readonly ? "r" : "rw");
    this.readonly = readonly;
  }

  /**
   * Create a local instance of <code>SageFileSource</code>.
   * <p/>
   * Using this implementation as <code>RandomAccessFile</code> may result in sub-par performance.
   *
   * @param file This is the file to be opened.
   * @param mode The mode to open this file as. <code>r</code> is read only. <code>rw</code> is
   *             read-write.
   * @throws IOException If there is an I/O related error.
   */
  public LocalSageFile(File file, String mode) throws IOException
  {
    randomAccessFile = new RandomAccessFile(file, mode);
    readonly = !mode.contains("w");
  }

  /**
   * Create a local instance of <code>SageFileSource</code>.
   * <p/>
   * Using this implementation as <code>RandomAccessFile</code> may result in sub-par performance.
   *
   * @param name This is the full path and name of the file to be opened.
   * @param mode The mode to open this file as. <code>r</code> is read only. <code>rw</code> is
   *             read-write.
   * @throws IOException If there is an I/O related error.
   */
  public LocalSageFile(String name, String mode) throws IOException
  {
    randomAccessFile = new RandomAccessFile(name, mode);
    readonly = !mode.contains("w");
  }

  @Override
  public void randomWrite(long pos, byte[] b, int off, int len) throws IOException
  {
    long localFilePointer = randomAccessFile.getFilePointer();
    try
    {
      if (localFilePointer != pos)
        randomAccessFile.seek(pos);

      randomAccessFile.write(b, off, len);
    }
    finally
    {
      randomAccessFile.seek(localFilePointer);
    }
  }

  @Override
  public long skip(long n) throws IOException
  {
    if (n <= 0)
      return 0;

    long pos = randomAccessFile.getFilePointer();
    long localFilePointer = Math.min(pos + n, randomAccessFile.length());

    // skipBytes ultimately does this too, but it only takes int. This is more flexible.
    randomAccessFile.seek(localFilePointer);

    return localFilePointer - pos;
  }

  @Override
  public long position()
  {
    try
    {
      return randomAccessFile.getFilePointer();
    } catch (IOException e)
    {
      System.out.println("Error: unable to get file pointer");
      e.printStackTrace(System.out);
    }

    return 0;
  }

  @Override
  public long available() throws IOException
  {
    return randomAccessFile.length() - randomAccessFile.getFilePointer();
  }

  @Override
  public void sync() throws IOException
  {
    // This is required for Wiz.bin to be read by the client code. The Wizard calls this in key
    // places. Otherwise you might get a lot of EOF exceptions.
    randomAccessFile.getFD().sync();
  }

  @Override
  public void flush() throws IOException
  {
    // Nothing to do since we don't buffer anything at this level and there's nothing below it.
  }

  @Override
  public void close() throws IOException
  {
    try
    {
      // This is only relevant when writing could have happened.
      randomAccessFile.getFD().sync();
    }
    catch (IOException e) {}

    randomAccessFile.close();
  }

  @Override
  public boolean isReadOnly()
  {
    return readonly;
  }

  @Override
  public SageFileSource getSource()
  {
    return null;
  }

  @Override
  public int read() throws IOException
  {
    return randomAccessFile.read();
  }

  @Override
  public int read(byte[] b) throws IOException
  {
    return randomAccessFile.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException
  {
    return randomAccessFile.read(b, off, len);
  }

  @Override
  public void readFully(byte[] b) throws IOException
  {
    randomAccessFile.readFully(b);
  }

  @Override
  public void readFully(byte[] b, int off, int len) throws IOException
  {
    randomAccessFile.readFully(b, off, len);
  }

  @Override
  public void write(int b) throws IOException
  {
    randomAccessFile.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException
  {
    randomAccessFile.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException
  {
    randomAccessFile.write(b, off, len);
  }

  @Override
  public void seek(long pos) throws IOException
  {
    randomAccessFile.seek(pos);
  }

  @Override
  public long length() throws IOException
  {
    return randomAccessFile.length();
  }

  @Override
  public void setLength(long newLength) throws IOException
  {
    randomAccessFile.setLength(newLength);
  }
}
