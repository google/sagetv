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

public class LocalSageFile extends RandomAccessFile implements SageFileSource
{
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
    super(file.getPath(), readonly ? "r" : "rw");
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
    super(name, readonly ? "r" : "rw");
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
    super(file, mode);
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
    super(name, mode);
    readonly = !mode.contains("w");
  }

  @Override
  public void randomWrite(long pos, byte[] b, int off, int len) throws IOException
  {
    long localFilePointer = super.getFilePointer();
    try
    {
      if (localFilePointer != pos)
        super.seek(pos);

      super.write(b, off, len);
    }
    finally
    {
      super.seek(localFilePointer);
    }
  }

  @Override
  public long skip(long n) throws IOException
  {
    if (n <= 0)
      return 0;

    long pos = super.getFilePointer();
    long localFilePointer = Math.min(pos + n, super.length());

    // skipBytes ultimately does this too, but it only takes int. This is more flexible.
    super.seek(localFilePointer);

    return localFilePointer - pos;
  }

  @Override
  public long position()
  {
    try
    {
      return super.getFilePointer();
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
    return super.length() - super.getFilePointer();
  }

  @Override
  public void sync() throws IOException
  {
    // This is required for Wiz.bin to be read by the client code. The Wizard calls this in key
    // places. Otherwise you might get a lot of EOF exceptions.
    super.getFD().sync();
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
      super.getFD().sync();
    }
    catch (IOException e) {}

    super.close();
  }

  @Override
  public boolean isActiveFile()
  {
    // TODO: Can we look this up somewhere?
    return false;
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

  /**
   * Unsupported method.
   * <p/>
   * This is <code>RandomAccessFile</code> specific. Use <code>skip()</code> instead.
   *
   * @throws IOException throws unsupported method.
   */
  @Override
  public final int skipBytes(int n) throws IOException
  {
    throw new IOException("unsupported method");
  }

  /**
   * Unsupported method.
   * <p/>
   * This is <code>RandomAccessFile</code> specific. Use <code>position()</code> instead.
   *
   * @throws IOException throws unsupported method.
   */
  @Override
  public final long getFilePointer() throws IOException
  {
    throw new IOException("unsupported method");
  }
}
