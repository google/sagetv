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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;

public class SageIOFileChannel extends FileChannel
{
  // We rely on the buffer in BufferedSageFile to get data into and out of ByteBuffer.
  private final BufferedSageFile randomFileSource;
  // If the object coming in was not wrapped in a buffer, we want to make sure the changes are
  // always passed down which mostly negates the buffering. An example of why this might happen is
  // if an EncryptedSageFile filter is passed into this method. A BufferedSageFile needs to be on
  // top of this filter, but it's likely below it. If we tried to use the BufferedSageFile below it,
  // it will negate the encryption/decryption.
  private final boolean alwaysFlush;

  /**
   * Wrap a <code>SageIOFileChannel</code> into a <code>FileChannel</code> implementation.
   *
   * @param sageFileSource The <code>SageFileSource</code> to use for reading and writing.
   */
  public SageIOFileChannel(SageFileSource sageFileSource)
  {
    if (sageFileSource instanceof BufferedSageFile)
    {
      this.randomFileSource = (BufferedSageFile) sageFileSource;
      alwaysFlush = false;
    }
    else
    {
      this.randomFileSource = new BufferedSageFile(sageFileSource, BufferedSageFile.READ_BUFFER_NIO_LIMIT, BufferedSageFile.WRITE_BUFFER_SIZE);
      alwaysFlush = true;
    }
  }

  @Override
  public int read(ByteBuffer dst) throws IOException
  {
    int returnValue = randomFileSource.read(dst);
    if (alwaysFlush)
      randomFileSource.flush();

    return returnValue;
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException
  {
    long returnValue = 0;
    int limit = offset + length;

    for (int i = offset; i < limit; i++)
    {
      returnValue += randomFileSource.read(dsts[i]);
    }

    if (alwaysFlush)
      randomFileSource.flush();

    return returnValue;
  }

  @Override
  public int write(ByteBuffer src) throws IOException
  {
    if (randomFileSource.isReadOnly())
      throw new NonWritableChannelException();

    int returnValue = randomFileSource.write(src);
    if (alwaysFlush)
      randomFileSource.flush();

    return returnValue;
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException
  {
    if (randomFileSource.isReadOnly())
      throw new NonWritableChannelException();

    long returnValue = 0;
    int limit = offset + length;
    for (int i = offset; i < limit; i++)
    {
      returnValue += randomFileSource.write(srcs[i]);
    }

    if (alwaysFlush)
      randomFileSource.flush();

    return returnValue;
  }

  @Override
  public long position() throws IOException
  {
    return randomFileSource.position();
  }

  @Override
  public FileChannel position(long newPosition) throws IOException
  {
    randomFileSource.seek(newPosition);
    return this;
  }

  @Override
  public long size() throws IOException
  {
    return randomFileSource.length();
  }

  @Override
  public FileChannel truncate(long size) throws IOException
  {
    if (randomFileSource.isReadOnly())
      throw new NonWritableChannelException();

    randomFileSource.setLength(size);
    return this;
  }

  /**
   * Sync to disk (preferring to include metadata).
   *
   * @param metaData In this implementation, the metadata might be written to disk, so the value of
   *                 this parameter is ignored.
   * @throws IOException If there is an I/O related error.
   */
  @Override
  public void force(boolean metaData) throws IOException
  {
    randomFileSource.sync();
  }

  @Override
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException
  {
    return randomFileSource.transferTo(position, count, target);
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException
  {
    return randomFileSource.transferFrom(src, position, count);
  }

  @Override
  public int read(ByteBuffer dst, long position) throws IOException
  {
    long lastPosition = randomFileSource.position();
    int returnValue = 0;

    try
    {
      if (lastPosition != position)
        randomFileSource.seek(position);

      returnValue = randomFileSource.read(dst);
      if (alwaysFlush)
        randomFileSource.flush();
    }
    finally
    {
      randomFileSource.seek(lastPosition);
    }

    return returnValue;
  }

  @Override
  public int write(ByteBuffer src, long position) throws IOException
  {
    if (randomFileSource.isReadOnly())
      throw new NonWritableChannelException();

    long lastPosition = randomFileSource.position();
    int returnValue = 0;
    try
    {
      if (lastPosition != position)
        randomFileSource.seek(position);

      returnValue = randomFileSource.write(src);
      if (alwaysFlush)
        randomFileSource.flush();
    }
    finally
    {
      randomFileSource.seek(lastPosition);
    }

    return returnValue;
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
  protected void implCloseChannel() throws IOException
  {
    randomFileSource.close();
  }
}
