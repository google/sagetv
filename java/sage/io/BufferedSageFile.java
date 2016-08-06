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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class BufferedSageFile implements SageFileSource
{
  // We use this to fill in the write buffer for read only mode.
  private static final byte[] empty = new byte[0];
  // 32k is a good default.
  public static final int READ_BUFFER_SIZE = 32768; // 32k
  // If this value is greater than the current read buffer size and a byte buffer method is used,
  // the buffer will be resized up to this value to improve the overall efficiency of transfers.
  // Otherwise, we will be looping over read, copy, (native) copy a lot more.
  public static final int READ_BUFFER_NIO_LIMIT = 524288; // 512k
  // 64k is a good block size on many filesystems. If everything is aligned, we will only be writing
  // out full blocks unless the way we are randomly writing makes it more optimal to do partial
  // writes.
  public static final int WRITE_BUFFER_SIZE = 65536; // 64k

  private final SageFileSource sageFileSource;
  private final boolean readonly;

  // Cache the last known length so we can possibly avoid getting a value from native which is
  // usually slower.
  private long realLastLength = 0;
  // Keeps track of the actual file position to quickly calculate the apparent position in the file.
  // Be very mindful of this value and ensure it is always updated correctly or you will have
  // problems such as reaching EOF early.
  private long realFilePosition = 0;

  // Used for the transferFrom method, ensure this is made null if the writeBuffer is replaced.
  private ByteBuffer writeWrap;
  private byte writeBuffer[];
  // The write offset is always ahead of the actual file offset (realFilePosition) by writeOffset.
  private int writeOffset = 0;
  // This is used for random writes and seeking to indicate that we are actually ahead or the
  // current write position for commits. If this value is greater than writeOffset, this is used
  // instead to determine how much to write.
  private int writeLength = 0;
  // This value is used to determine how many bytes to leave in the write buffer to optimize random
  // writing. This value is adjusted dynamically based on how far behind the last random write was.
  // The objective of this value is to get random writes re-aligned so that the will fall within the
  // write buffer. This value will also gradually lower each time the last write was more than the
  // value of writeOptimizerLimit behind the write buffer since if this isn't helping larger writes
  // will be more beneficial.
  private int writeOptimizer = 0;
  // This is the limit on how high the value of writeOptimizer is allowed to be. If the file is
  // opened in read/write mode, this is set to writeBuffer.length / 2. If writeBuffer is replaced,
  // this value needs to be updated accordingly.
  private int writeOptimizerLimit = 0;
  // It turns out that it's faster to set a boolean parameter for this.
  private boolean writePending = false;

  // Used for the transferTo method, ensure this is made null if the readBuffer is replaced.
  private ByteBuffer readWrap;
  private byte readBuffer[];
  // The read offset is always behind the actual file offset by readLength - readOffset
  private int readOffset = 0;
  // The number of bytes read in the last buffered read. readOffset is not allowed to be higher than
  // this value.
  private int readLength = 0;

  /**
   * Create a buffered <code>SageFileSource</code> with the default read (32768) and write (65536)
   * buffer sizes.
   * <p/>
   * Do not attempt to use the underlying <code>SageFileSource</code> after writing without first
   * calling <code>flush()</code> since otherwise the state of writing will be unknown.
   *
   * @param sageFileSource The <code>SageFileSource</code> to add a buffering.
   */
  public BufferedSageFile(SageFileSource sageFileSource)
  {
    this(sageFileSource, READ_BUFFER_SIZE, WRITE_BUFFER_SIZE);
  }

  /**
   * Create a buffered <code>SageFileSource</code> with custom read and default write (65536) buffer
   * sizes.
   * <p/>
   * Do not attempt to use the underlying <code>SageFileSource</code> after writing without first
   * calling <code>flush()</code> since otherwise the state of writing will be unknown.
   *
   * @param sageFileSource The <code>SageFileSource</code> to add a buffering.
   * @param readBufferSize The read buffer size.
   */
  public BufferedSageFile(SageFileSource sageFileSource, int readBufferSize)
  {
    this(sageFileSource, readBufferSize, WRITE_BUFFER_SIZE);
  }

    /**
   * Create a buffered <code>SageFileSource</code> with custom read and write buffer sizes.
   * <p/>
   * Do not attempt to use the underlying <code>SageFileSource</code> after writing without first
   * calling <code>flush()</code> since otherwise the state of writing will be unknown.
   *
   * @param sageFileSource The <code>SageFileSource</code> to add a buffering.
   * @param readBufferSize The read buffer size.
   * @param writeBufferSize The write buffer size. If the source is not read only, any value that is
     *                      not a multiple of 8192 will be rounded up to the nearest multiple of
     *                      8192 for alignment purposes.
   */
  public BufferedSageFile(SageFileSource sageFileSource, int readBufferSize, int writeBufferSize)
  {
    this.sageFileSource = sageFileSource;
    realFilePosition = sageFileSource.position();
    readonly = sageFileSource.isReadOnly();

    readBuffer = new byte[readBufferSize];
    readWrap = null;

    // Optimize loading time if we will not be able to write anyway.
    if (readonly)
    {
      // Create a 0 length buffer so we don't need to add checking for null values and we are
      // wasting less heap. This also has a smaller impact on initialization time.
      writeBuffer = empty;
    }
    else
    {
      // We align to 8192 for the total buffer size so that half of the buffer will at least be
      // aligned to 4096 which is the multiple used for write buffer optimization. We care about
      // alignment with the filesystem more than usual since every write is synced to physical
      // storage immediately. The position of the first write will actually determine if the write
      // when flushed will be in alignment or not, but at least we're making an effort.
      if (writeBufferSize == 0)
        writeBufferSize = 8192;
      int remainder = writeBufferSize % 8192;
      if (remainder != 0)
        writeBufferSize = writeBufferSize + 8192 - remainder;
      writeOptimizerLimit = writeBufferSize / 2;
      writeBuffer = new byte[writeBufferSize];
      writeWrap = null;
    }
  }

  private void fillReadBuffer() throws IOException
  {
    // Return the read offset to the start of the read buffer.
    readOffset = 0;

    // Calculating the perfect value, then calling readFully is slower overall. This generally loops
    // at most 2 times.
    do
    {
      readLength = sageFileSource.read(readBuffer, 0, readBuffer.length);
    }
    while (readLength == 0);

    // If this method returns with readLength set to zero, the read method will assume EOF.
    if (readLength == -1)
    {
      readLength = 0;
      return;
    }

    // We only increment this value when required. Otherwise for methods like length(), we do the
    // math based on if we are in read/write mode.
    realFilePosition += readLength;
  }

  private void clearReadBuffer() throws IOException
  {
    realFilePosition = (realFilePosition - readLength) + readOffset;
    sageFileSource.seek(realFilePosition);
    readLength = readOffset = 0;
  }

  private void flushForced() throws IOException
  {
    // We only write out to writeLength if a write is forced since otherwise we might overwrite
    // everything up to that point making an early write wasteful.
    if (writeOffset > writeLength)
      writeLength = writeOffset;

    sageFileSource.write(writeBuffer, 0, writeLength);
    realFilePosition += writeOffset;
    // Earlier we established that the writeLength is the forward-most value. If writeOffset doesn't
    // match that, then we need to move the file offset back to the right position.
    if (writeOffset != writeLength)
      sageFileSource.seek(realFilePosition);
    writeOffset = writeLength = 0;
    writePending = false;
  }

  private void flushOptimized() throws IOException
  {
    // This method is only called when writeOffset == writeBuffer.length, so we don't need to check
    // the value of writeLength. Also because of this, writeOptimizer will always be less than
    // writeOffset since it's limited to half the value of writeBuffer.length.

    if (writeOptimizer == 0)
    {
      sageFileSource.write(writeBuffer, 0, writeOffset);
      realFilePosition += writeOffset;
      writeOffset = writeLength = 0;
      writePending = false;
    }
    else
    {
      writeLength = writeOffset - writeOptimizer;
      sageFileSource.write(writeBuffer, 0, writeLength);
      System.arraycopy(writeBuffer, writeLength, writeBuffer, 0, writeOptimizer);
      realFilePosition += writeLength;
      writeOffset = writeLength = writeOptimizer;

      // This is not a full write, so we set writePending.
      writePending = true;
    }
  }

  /**
   * Read into a provided <code>ByteBuffer</code>
   * <p/>
   * This takes advantage of the byte arrays already available in this class to copy buffered data
   * into a <code>ByteBuffer</code>.
   * <p/>
   * This will read up to the limit of the provided <code>ByteBuffer</code>.
   *
   * @param dst The <code>ByteBuffer</code> to read into.
   * @return The number of bytes read into the provided buffer.
   * @throws IOException If there is an I/O related error.
   */
  public int read(ByteBuffer dst) throws IOException
  {
    // readOffset and readLength will always be 0 when we have a write pending.
    if (readOffset == readLength)
    {
      // Don't try to read before committing pending writes.
      if (writePending)
        flushForced();

      fillReadBuffer();
      if (readOffset == readLength)
        return 0;
    }

    long startPosition = position();
    int remaining;
    int bufferedLength;
    while (dst.hasRemaining())
    {
      remaining = dst.remaining();

      // Make the read buffer bigger so we don't need to loop so many times unless we're still going
      // to get all of the data in one pass.
      if (readBuffer.length < READ_BUFFER_NIO_LIMIT && readBuffer.length < remaining && readOffset == readLength)
      {
        readBuffer = new byte[Math.min(remaining * 2, READ_BUFFER_NIO_LIMIT)];
        readWrap = null;
      }

      bufferedLength = readLength - readOffset;
      if (remaining > bufferedLength)
        remaining = bufferedLength;

      dst.put(readBuffer, readOffset, remaining);
      readOffset += remaining;

      if (readOffset == readLength)
      {
        fillReadBuffer();
        if (readOffset == readLength)
          return (int)(position() - startPosition);
      }
    }

    return (int)(position() - startPosition);
  }

  /**
   * Write from a provided <code>ByteBuffer</code>
   * <p/>
   * This takes advantage of the byte arrays already available in this class to copy
   * <code>ByteBuffer</code> data into the write buffer and write it out when the write buffer is
   * full.
   * <p/>
   * This will write from the provided <code>ByteBuffer</code> up to the limit.
   *
   * @param src The <code>ByteBuffer</code> to write from.
   * @return The number of bytes written from the provided buffer.
   * @throws IOException If there is an I/O related error.
   */
  public int write(ByteBuffer src) throws IOException
  {
    if (readonly)
      throw new IOException("Buffer is read only.");

    if (readLength > 0)
      clearReadBuffer();

    long startPosition = position();
    int length;
    while (src.hasRemaining())
    {
      length = Math.min(writeBuffer.length - writeOffset, src.remaining());
      src.get(writeBuffer, writeOffset, length);

      writeOffset += length;

      if (writeOffset == writeBuffer.length)
        flushOptimized();
      else
        writePending = true;
    }

    return (int)(position() - startPosition);
  }

  public long transferTo(long position, long count, WritableByteChannel target) throws IOException
  {
    // Don't try to read while writes are pending.
    if (writePending)
      flushForced();

    // Make the read buffer bigger so we don't need to loop so many times unless we're still going
    // to get all of the data in one pass.
    if (readBuffer.length < READ_BUFFER_NIO_LIMIT && readBuffer.length < count)
    {
      byte newArray[] =  new byte[(int) Math.min(count * 2, (long) READ_BUFFER_NIO_LIMIT)];

      if (readOffset < readLength)
      {
        // We don't need to copy the entire buffer; just the size of the last read.
        System.arraycopy(readBuffer, 0, newArray, 0, readLength);
      }

      readBuffer = newArray;
      readWrap = ByteBuffer.wrap(readBuffer);
    }

    long lastPosition = position();
    int bytesRead;
    long remaining = count;

    try
    {
      if (position != lastPosition)
        seek(position);

      if (readWrap == null)
        readWrap = ByteBuffer.wrap(readBuffer);

      while (remaining > 0)
      {
        if (readOffset == readLength)
        {
          fillReadBuffer();
          if (readOffset == readLength)
            break;
        }

        readWrap.limit((int) Math.min((long) readLength, readOffset + remaining)).position(readOffset);
        while (readWrap.hasRemaining())
        {
          bytesRead = target.write(readWrap);

          readOffset += bytesRead;
          remaining -= bytesRead;
        }
      }
    }
    finally
    {
      // If we didn't move too much, the read buffer might still have content.
      seek(lastPosition);
    }

    return count - remaining;
  }

  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException
  {
    if (readonly)
      throw new IOException("Buffer is read only.");

    if (readLength > 0)
      clearReadBuffer();

    long lastPosition = position();
    int bytesWrite;
    long remaining = count;

    try
    {
      if (position != lastPosition)
        seek(position);

      if (writeWrap == null)
        writeWrap = ByteBuffer.wrap(writeBuffer);

      while (remaining > 0)
      {
        writeWrap.limit((int) Math.min((long) (writeBuffer.length), writeOffset + remaining)).position(writeOffset);
        while (writeWrap.hasRemaining())
        {
          bytesWrite = src.read(writeWrap);

          if (bytesWrite == -1)
            break;

          writeOffset += bytesWrite;
          remaining -= bytesWrite;

          // You must set writePending to true before seeking or the data will be appended to or
          // overwrite the wrong place in the file.
          if (writeOffset == writeBuffer.length)
            flushOptimized();
          else
            writePending = true;
        }
      }
    }
    finally
    {
      seek(lastPosition);
    }

    return count - remaining;
  }

  @Override
  public int read() throws IOException
  {
    // readOffset and readLength will always be 0 when we have a write pending.
    if (readOffset == readLength)
    {
      // Don't try to read before committing pending writes.
      if (writePending)
        flushForced();

      fillReadBuffer();
      if (readOffset == readLength)
        return -1;
    }

    return readBuffer[readOffset++] & 0xFF;
  }

  @Override
  public int read(byte[] b) throws IOException
  {
    return read(b, 0, b.length);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException
  {
    long remaining = length() - position();

    if (remaining == 0)
      return -1;
    else if (len > remaining)
      len = (int)remaining;

    readFully(b, off, len);

    return len;
  }

  @Override
  public void readFully(byte[] b) throws IOException
  {
    readFully(b, 0, b.length);
  }

  @Override
  public void readFully(byte[] b, int off, int len) throws IOException
  {
    // The buffer is empty. Try to fill it back up unless the length requested for the provided byte
    // array is larger than the buffer. We will try to be a little more efficient in that scenario.
    if (readOffset == readLength)
    {
      // Don't try to read while writes are pending.
      if (writePending)
        flushForced();

      if (len < readBuffer.length)
      {
        fillReadBuffer();

        if (readOffset == readLength)
          throw new EOFException();
      }
    }

    // This is everything currently unread in the buffer.
    int bufferedLength = readLength - readOffset;

    // The read fits within the currently buffered read.
    if (bufferedLength > len)
    {
      System.arraycopy(readBuffer, readOffset, b, off, len);
      readOffset += len;
      return;
    }

    if (bufferedLength != 0)
    {
      System.arraycopy(readBuffer, readOffset, b, off, bufferedLength);
      readOffset += bufferedLength;

      if (len == bufferedLength)
        return;

      len -= bufferedLength;
      off += bufferedLength;
    }

    if (len > readBuffer.length)
    {
      sageFileSource.readFully(b, off, len);
      return;
    }

    do
    {
      fillReadBuffer();
      bufferedLength = Math.min(readLength - readOffset, len);

      if (bufferedLength == 0)
        throw new EOFException();

      System.arraycopy(readBuffer, readOffset, b, off, bufferedLength);
      readOffset += bufferedLength;

      if (len == bufferedLength)
        return;

      len -= bufferedLength;
      off += bufferedLength;
    }
    while (len > 0);
  }

  @Override
  public void write(int b) throws IOException
  {
    // This is here because otherwise you would get an index out of bounds exception which wouldn't
    // really call out the real issue.
    if (readonly)
      throw new IOException("Buffer is read only.");

    if (readLength > 0)
      clearReadBuffer();

    writeBuffer[writeOffset++] = (byte)b;

    if (writeOffset == writeBuffer.length)
      flushOptimized();
    else
      writePending = true;
  }

  @Override
  public void write(byte[] b) throws IOException
  {
    write(b, 0, b.length);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException
  {
    // This is here because otherwise you could loop endlessly since the write method in the
    // underlying source doesn't always throw an exception when the length of writeBuffer is 0.
    if (readonly)
      throw new IOException("Buffer is read only.");

    if (readLength > 0)
      clearReadBuffer();

    int bufferedLength;

    // Writes are 100% buffered because the Wizard does lots of large, small and random writes and
    // detecting how to optimize this is difficult.
    while (len > 0)
    {
      bufferedLength = writeBuffer.length - writeOffset;

      if (bufferedLength > len)
        bufferedLength = len;

      System.arraycopy(b, off, writeBuffer, writeOffset, bufferedLength);
      writeOffset += bufferedLength;

      if (writeOffset == writeBuffer.length)
        flushOptimized();
      else
        writePending = true;

      if (len == bufferedLength)
        break;

      len -= bufferedLength;
      off += bufferedLength;
    }
  }

  @Override
  public long skip(long n) throws IOException
  {
    if (n < 0)
      return 0;

    long originalPos = position();
    seek(originalPos + n);

    return position() - originalPos;
  }

  public void randomWrite(long pos, byte b[], int off, int len) throws IOException
  {
    if (readonly)
      throw new IOException("Buffer is read only.");

    if (readLength > 0)
      clearReadBuffer();

    // If this value is less than 0, the data starts before the buffer. If this is 0 or greater, the
    // data start within the buffer and may go beyond it.
    long writePosition = pos - realFilePosition;

    if (writeOffset > writeLength)
      writeLength = writeOffset;

    try
    {
      // writeLength is also within an integer range since we are checking the value is between two
      // integers. If we write beyond the current offset in the write buffer, it would have had the
      // same effect as if we had overwrote it on the disk. If the position is right on the
      // writeOffset, we write into the write buffer and update writeLength so we know to write
      // further than writeOffset when the write buffer is committed.
      if (writePosition >= -len && writePosition <= writeLength)
      {
        int firstCommitLength = 0;
        // The write is partially at the start of the buffer.
        if (writePosition < 0)
        {
          // writePosition is a negative number, so this is actually makes it positive.
          firstCommitLength = (int)-writePosition;
          sageFileSource.seek(pos);
          sageFileSource.write(b, off, firstCommitLength);

          pos += firstCommitLength;
          off += firstCommitLength;
          len -= firstCommitLength;

          // This is now 0.
          writePosition = 0;
        }

        // The write is within the buffer and possibly beyond it.

        // writePosition cannot be greater than writeBuffer.length. Also writePosition cannot be a
        // negative number. This will never result in a negative number.
        int copyLength = Math.min(len, (writeBuffer.length - (int) writePosition));
        System.arraycopy(b, off, writeBuffer, (int) writePosition, copyLength);

        // If we are past the current write position, set writeLength to indicate that the current
        // write offset is behind what needs to be written. Since we can't get here without being
        // at most right at the write offset, it's safe to assume there will not be any unknown data
        // being committed on the next write.
        if (writePosition > writeLength)
        {
          writeLength = (int)writePosition;
        }

        // See if we have anything left beyond the write buffer.
        if (len != copyLength)
        {
          // We only need to do these calculations if we need to do this final write.
          len -= copyLength;
          pos += copyLength;
          off += copyLength;

          sageFileSource.seek(pos);
          sageFileSource.write(b, off, len);
        }
      }
      else
      {
        writePosition = -writePosition;
        // writePosition is a negative value if we will make an adjustment. We make it a positive
        // number mostly to make this more readable and so we don't need to keep negating it which
        // might have it's own performance implication. We try to align with 4k blocks at a minimum
        // so we maintain what could be a normal disk block size alignment.
        if (writePosition > writeOptimizer && writePosition < writeOptimizerLimit)
        {
          // This should at the very worst give us the number equal to writeOptimizerLimit.
          writeOptimizer = (int)writePosition + 4096 - ((int)writePosition % 4096);
        }
        else if (writeOptimizer != 0)
        {
          // Last random write missed the buffer and it's beyond the limit, slowly turn optimization
          // off. There is no certainty that the next write will not benefit, so we don't go
          // immediately back down to 0. Ideally we could maintain the 4k alignment, but then we
          // would need to increment a threshold instead of this actual value which would need
          // another variable. This should be a good compromise.
          writeOptimizer -= 512; // This is always a multiple of 4096.
        }

        sageFileSource.seek(pos);
        sageFileSource.write(b, off, len);
      }
    }
    finally
    {
      sageFileSource.seek(realFilePosition);
    }
  }

  @Override
  public void seek(long pos) throws IOException
  {
    // Read buffer in use, see if we can do anything with that.
    if (readLength > 0)
    {
      long readPosition = pos - (realFilePosition - readLength);

      // See if we can seek within the read buffer.
      if (readPosition >= 0 && readPosition < readLength)
      {
        readOffset = (int)readPosition;
      }
      else
      {
        // Clear the read buffer and seek.
        readOffset = readLength = 0;
        sageFileSource.seek(pos);
        realFilePosition = pos;
      }
    }
    // Write is pending to be written.
    else if (writePending)
    {
      // If this value is less than 0, the data starts before the buffer. If this is 0 or greater,
      // the data starts within the buffer and may go beyond it.
      long writePosition = pos - realFilePosition;

      // Set writeLength so we can be sure anything ahead of the current position will be written.
      if (writeOffset > writeLength)
        writeLength = writeOffset;

      if (writePosition >= 0 && writePosition < writeLength)
      {
        writeOffset = (int)writePosition;
      }
      else
      {
        // We are outside of the buffer. Flush write buffer and seek.
        flushForced();
        sageFileSource.seek(pos);
        realFilePosition = pos;
      }
    }
    // Read buffer is empty and we have no pending writes. Just seek and be sure to update the
    // current position.
    else
    {
      sageFileSource.seek(pos);
      realFilePosition = pos;
    }
  }

  @Override
  public long position()
  {
    // When we are in read mode, writeOffset will always be 0. When we are in write mode,
    // readLength and readOffset will always be 0. writeLength doesn't need to be taken into
    // account here.
    return (realFilePosition - readLength) + (readOffset | writeOffset);
  }

  @Override
  public long length() throws IOException
  {
    realLastLength = sageFileSource.length();
    // Just in case it's ahead due to seeking, we still distinguish between a pending write and no
    // pending write so that we are returning the actual length after the write buffer is flushed.
    return writePending ? Math.max(position(), realLastLength) : realLastLength;
  }

  @Override
  public long available() throws IOException
  {
    return length() - position();
  }

  @Override
  public void setLength(long newLength) throws IOException
  {
    if (writePending)
      flushForced();
    else if (readLength > 0)
      clearReadBuffer();

    sageFileSource.setLength(newLength);
    realLastLength = newLength;
    realFilePosition = sageFileSource.position();
  }

  @Override
  public void sync() throws IOException
  {
    if (writePending)
      flushForced();

    sageFileSource.sync();
  }

  /**
   * Flush the write buffer if it contains anything to disk and clears the read buffer if it is
   * populated.
   * <p/>
   * This will not sync. So if there is buffer below this one, the data may still be volatile.
   *
   * @throws IOException If there is an I/O related error.
   */
  public void flush() throws IOException
  {
    // This causes the retained "optimized" write data to be written out too.
    if (writePending)
      flushForced();
    else if (readLength > 0)
      clearReadBuffer();

    sageFileSource.flush();
  }

  @Override
  public void close() throws IOException
  {
    if (writePending)
      flushForced();
    sageFileSource.close();
  }

  @Override
  public boolean isReadOnly()
  {
    return sageFileSource.isReadOnly();
  }

  @Override
  public SageFileSource getSource()
  {
    return sageFileSource;
  }
}
