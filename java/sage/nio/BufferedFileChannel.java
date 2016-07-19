package sage.nio;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.security.InvalidParameterException;

public class BufferedFileChannel extends FileChannel implements SageFileChannel
{
  // We use this to fill in the write buffer for read only mode. If you try to write, the buffer
  // will be full immediately and it will throw the read only exception.
  private static final ByteBuffer empty = ByteBuffer.allocate(0);
  public static final int DEFAULT_WRITE_SIZE = 65536;
  public static final int DEFAULT_READ_SIZE = 65536;

  private final SageFileChannel fileChannel;
  private final boolean readonly;
  private final ByteBuffer readBuffer;
  private final ByteBuffer writeBuffer;
  private int writeLength;
  private boolean writePending;

  private long realFilePosition = 0;
  private long lastSize = 0;

  /**
   * Create a new <code>BufferedFileChannel</code>.
   * <p/>
   * This will create a buffer for a <code>SageFileChannel</code> class using provided read and
   * write buffers. The reason you might want to provide the buffers is they can take a little
   * longer to create than normal byte arrays, and if you are about to open a new file immediately,
   * you can move the buffer to the new file saving initialization time.
   * <p/>
   * There are no buffer size sanity checks. If you pass a 0 length buffer, some methods might spin
   * endlessly. If you pass a very small buffer (< 4096) you may see very poor performance.
   *
   * @param fileChannel The <code>SageFileChannel</code> to back this buffer layer.
   * @param readBuffer The read buffer to be used to back this buffer layer.
   * @param writeBuffer The write buffer to be used to back this buffer layer. This can be
   *                    <code>null</code> if the buffer is being opened with a read only source.
   */
  public BufferedFileChannel(SageFileChannel fileChannel, ByteBuffer readBuffer, ByteBuffer writeBuffer)
  {
    readonly = fileChannel.isReadOnly();
    this.fileChannel = fileChannel;
    this.readBuffer = readBuffer;
    readBuffer.clear().limit(0);

    if (writeBuffer == null)
    {
      if (!fileChannel.isReadOnly())
        throw new InvalidParameterException("The channel is writable and a writable byte buffer was not provided.");

      // We will not be writing, so don't allocate anything.
      this.writeBuffer = empty;
    }
    else
    {
      this.writeBuffer = writeBuffer;
      this.writeBuffer.clear();
    }

    realFilePosition = fileChannel.position();
  }

  /**
   * Create a new <code>BufferedFileChannel</code>.
   * <p/>
   * Creates a buffer layer with the default buffer sizes of 65536 for read and 65536 for write.
   *
   * @param fileChannel The <code>SageFileChannel</code> to back this buffer layer.
   * @param direct If the buffers should be allocated direct or on heap.
   */
  public BufferedFileChannel(SageFileChannel fileChannel, boolean direct)
  {
    this(fileChannel, DEFAULT_READ_SIZE, DEFAULT_WRITE_SIZE, direct);
  }

  /**
   * Create a new <code>BufferedFileChannel</code>.
   * <p/>
   * Creates a buffer layer with the default buffer size of 65536 for write and a custom buffer size
   * for read.
   * <p/>
   * There are no buffer size sanity checks. If you pass a 0 length buffer, some methods might spin
   * endlessly. If you pass a very small buffer (< 4096) you will see very poor performance.
   *
   * @param fileChannel The <code>SageFileChannel</code> to back this buffer layer.
   * @param readBufferSize The desired size of the read buffer.
   * @param direct If the buffers should be allocated direct or on heap.
   */
  public BufferedFileChannel(SageFileChannel fileChannel, int readBufferSize, boolean direct)
  {
    this(fileChannel, readBufferSize, DEFAULT_WRITE_SIZE, direct);
  }

  /**
   * Create a new <code>BufferedFileChannel</code>.
   * <p/>
   * Creates a buffer layer with a custom buffer size for read and write.
   * <p/>
   * There are no buffer size sanity checks. If you pass a 0 length buffer, some methods might spin
   * endlessly. If you pass a very small buffer (< 4096) you will see very poor performance.
   *
   * @param fileChannel The <code>SageFileChannel</code> to back this buffer layer.
   * @param readBufferSize The desired size of the read buffer.
   * @param writeBufferSize The desired size of the write buffer.
   * @param direct If the buffers should be allocated direct or on heap.
   */
  public BufferedFileChannel(SageFileChannel fileChannel, int readBufferSize, int writeBufferSize, boolean direct)
  {
    readonly = fileChannel.isReadOnly();
    this.fileChannel = fileChannel;
    readBuffer = direct ? ByteBuffer.allocateDirect(readBufferSize) : ByteBuffer.allocate(readBufferSize);
    readBuffer.clear().limit(0);

    if (readonly)
    {
      // We will not be writing, so don't allocate anything.
      this.writeBuffer = empty;
    }
    else
    {
      this.writeBuffer = direct ? ByteBuffer.allocateDirect(writeBufferSize) : ByteBuffer.allocate(writeBufferSize);
      this.writeBuffer.clear();
    }
  }

  @Override
  public void openFile(String name) throws IOException
  {
    if (writePending)
      flushWriteBuffer();
    fileChannel.openFile(name);
    readBuffer.clear().limit(0);
  }

  @Override
  public void openFile(File file) throws IOException
  {
    if (writePending)
      flushWriteBuffer();
    fileChannel.openFile(file);
    readBuffer.clear().limit(0);
  }

  private void fillReadBuffer() throws IOException
  {
    readBuffer.clear();
    fileChannel.read(readBuffer);
    readBuffer.flip();
    realFilePosition += readBuffer.remaining();
  }

  private void clearReadBuffer() throws IOException
  {
    long newPosition = (realFilePosition - readBuffer.limit()) + readBuffer.position();
    // Calling position(long) is more expensive than checking if we even need to.
    if (newPosition != realFilePosition)
    {
      realFilePosition = newPosition;
      fileChannel.position(realFilePosition);
    }
    readBuffer.clear().limit(0);
  }

  private void flushWriteBuffer() throws IOException
  {
    int position = writeBuffer.position();
    if (writeLength > position)
      writeBuffer.position(writeLength);

    writeBuffer.flip();
    //int writeBytes = writeBuffer.remaining();
    // We have at least one pending write, this means we might only need to evaluate if data is left
    // in the buffer once. Since we do not know what the underlying implementation will do, we need
    // to loop here to make sure the entire buffer is written out.
    do
    {
      fileChannel.write(writeBuffer);
    }
    while (writeBuffer.hasRemaining());
    writeBuffer.clear();
    realFilePosition += position;
    if (position < writeLength)
      fileChannel.position(realFilePosition);
    writeLength =  0;
    writePending = false;
  }

  /**
   * Use the byte buffer to fully read into a byte array.
   * <p/>
   * This should be avoided when possible since it will lock you into only being able to use this
   * <code>SageFileChannel</code> implementation. This is here mostly to ease transitioning a class
   * over to NIO and it should be a goal to eventually remove its use completely.
   *
   * @param b The buffer to write into.
   * @param off The offset to start writing into the byte array.
   * @param len The number of bytes to write into the byte array.
   * @throws IOException If there is an I/O related error.
   * @throws EOFException If the end of the file is reached before the length requested is reached.
   */
  public void readFully(byte[] b, int off, int len) throws IOException
  {
    while (len > 0)
    {
      if (!readBuffer.hasRemaining())
        fillReadBuffer();

      int readLength = Math.min(readBuffer.remaining(), len);

      if (readLength == 0)
        throw new EOFException();

      readBuffer.get(b, off, readLength);

      // Marginally faster; especially for reads smaller than the remaining data in the buffer.
      if (len == readLength)
        break;

      len -= readLength;
      off += readLength;
    }
  }

  @Override
  public int readUnsignedByte() throws IOException
  {
    if (!readBuffer.hasRemaining())
      fillReadBuffer();

    if (!readBuffer.hasRemaining())
      throw new java.io.EOFException();

    return readBuffer.get() & 0xFF;
  }

  @Override
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException
  {
    // It really can only be one or the other.
    if (writePending)
      flushWriteBuffer();
    else if (readBuffer.limit() > 0)
    {
      // This is the start of the read buffer relative to the entire file.
      long readStartPosition = realFilePosition - readBuffer.limit();
      // This is the position around the read buffer relative to the entire file. This number will
      // be negative when the the current read comes before the buffer.
      long readPosition = position - readStartPosition;

      // The buffer is in the way, use this to our advantage.
      if (readPosition >= -count && readPosition < readBuffer.limit())
      {
        long startPosition = position;
        long readBytes;

        // The read starts before the buffer.
        if (readPosition < 0)
        {
          readPosition = -readPosition;

          while (true)
          {
            readBytes = fileChannel.transferTo(position, readPosition, target);

            if (readBytes == 0)
            {
              return position - startPosition;
            }

            position += readBytes;
            readPosition -= readBytes;

            // This position is the end of the read buffer.
            if (position == readStartPosition)
            {
              break;
            }
          }

          count -= position - startPosition;
        }

        // The read starts somewhere in the buffer and possibly goes outside of it.
        int readBufferPosition = readBuffer.position();
        int readBufferLimit = readBuffer.limit();
        int readLength = (int)Math.min((long)readBuffer.remaining(), count);

        try
        {
          readBuffer.limit(readBufferPosition + readLength);
          do
          {
            target.write(readBuffer);
          }
          while (readBuffer.hasRemaining());
          position += readLength;
          count -= readLength;
        }
        finally
        {
          readBuffer.limit(readBufferLimit).position(readBufferPosition);
        }

        return fileChannel.transferTo(position, count, target) + (position - startPosition);
      }
    }

    return fileChannel.transferTo(position, count, target);
  }

  @Override
  public long transferTo(long count, WritableByteChannel target) throws IOException
  {
    if (writePending)
      flushWriteBuffer();

    // Only buffer if the number of bytes is lower than the most the buffer can hold. Otherwise we
    // are probably not getting any real benefit from the buffering.
    if (count < readBuffer.capacity() && !readBuffer.hasRemaining())
      fillReadBuffer();

    // If we aren't going to be reading anything, skip the buffer calculations entirely.
    if (readBuffer.hasRemaining())
    {
      long returnValue = 0;
      int readLimit;
      int readLength;

      do
      {
        readLimit = readBuffer.limit();
        readLength = (int) Math.min((long) readBuffer.remaining(), count);
        try
        {
          if (readLength != readLimit)
            readBuffer.limit(readBuffer.position() + readLength);
          do
          {
            target.write(readBuffer);
          }
          while (readBuffer.hasRemaining());
        }
        finally
        {
          if (readLength != readLimit)
            readBuffer.limit(readLimit);
        }
        returnValue += readLength;
        count -= readLength;

        // If the buffer needs more than the next buffered read can hold, read directly.
        if (!readBuffer.hasRemaining() && count < readBuffer.capacity())
        {
          fillReadBuffer();
          if (!readBuffer.hasRemaining())
            return returnValue;
        }
        else
        {
          break;
        }
      }
      while (count > 0);

      // Nothing left to read. We read everything from the buffer.
      if (count == 0)
      {
        return returnValue;
      }
      // We may have read some of the buffer, but the remaining count is higher than the buffer
      // size, so we are finishing the read directly.
      else
      {
        long readBytes = fileChannel.transferTo(count, target);
        realFilePosition += readBytes;
        return returnValue + readBytes;
      }
    }

    // The buffer was not filled due to the count of the request. Read entirely directly. This
    // should be faster than constant buffering since the count is large.
    long returnValue = fileChannel.transferTo(count, target);
    realFilePosition += returnValue;
    return returnValue;
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException
  {
    if (readonly)
      throw new NonWritableChannelException();

    // This ensures that after this write, if we read this position, the data will be correct.
    if (readBuffer.limit() != 0)
      clearReadBuffer();

    // We only need to clear out the write buffer if it's in the way. Unless we start using this
    // method for lots of random writes, this doesn't need to be improved.
    if (writePending)
    {
      long writePosition = position - realFilePosition;

      // The buffer is in the way, flush it to disk. If we later write beyond
      // writeBuffer.position(), it would be the same result as if that position was overwritten in
      // the file, so we don't need to take the byte buffer limit into account.
      if (writePosition >= -count && writePosition < writeBuffer.position())
      {
        flushWriteBuffer();
      }
    }

    return fileChannel.transferFrom(src, position, count);
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long count) throws IOException
  {
    if (readonly)
      throw new NonWritableChannelException();

    // This ensures that after this write, if we read this position, the data will be correct.
    if (readBuffer.limit() != 0)
      clearReadBuffer();

    long startPosition = position();
    long bytesWritten;

    do
    {
      // Don't allow more than what was requested to be written.
      if (writeBuffer.limit() > count)
        writeBuffer.limit((int)count);

      bytesWritten = src.read(writeBuffer);

      if (bytesWritten == -1)
        break;

      count -= bytesWritten;

      if (!writeBuffer.hasRemaining())
        flushWriteBuffer();
    }
    while (count > 0);
    writeBuffer.limit(writeBuffer.capacity());

    return position() - startPosition;
  }

  @Override
  public int read(ByteBuffer dst, long position) throws IOException
  {
    // Pending writes need to be flushed or they might not show up when reading.
    if (writePending)
      flushWriteBuffer();
    else if (readBuffer.limit() != 0)
    {
      long readPosition = position - (realFilePosition - readBuffer.limit());

      // The buffer is in the way, use this to our advantage.
      if (readPosition >= -dst.remaining() && readPosition < readBuffer.limit())
      {
        long startPosition = position;
        long readBytes;

        // The read starts before the buffer.
        int dstLimit = dst.limit();

        if (readPosition < 0)
        {
          try
          {
            dst.limit(dst.position () + (int)-readPosition);

            while (true)
            {
              readBytes = fileChannel.read(dst, position);

              if (readBytes == 0)
              {
                return (int) (position - startPosition);
              }

              position += readBytes;

              if (position == realFilePosition)
              {
                break;
              }
            }
          }
          finally
          {
            dst.limit(dstLimit);
          }
        }

        // The read starts somewhere in the buffer and possibly goes outside of it.
        int readBufferPosition = readBuffer.position();
        int readBufferLimit = readBuffer.limit();
        int readLength = Math.min(readBuffer.remaining(), dst.remaining());

        try
        {
          readBuffer.limit(readBufferPosition + readLength);
          do
          {
            dst.put(readBuffer);
          }
          while (readBuffer.hasRemaining());
          position += readLength;
        }
        finally
        {
          readBuffer.limit(readBufferLimit).position(readBufferPosition);
        }

        if (!dst.hasRemaining())
          return (int)(position - startPosition);

        return fileChannel.read(dst, position) + (int)(position - startPosition);
      }
    }

    return fileChannel.read(dst, position);
  }

  @Override
  public int write(ByteBuffer src, long position) throws IOException
  {
    if (readonly)
      throw new NonWritableChannelException();

    if (readBuffer.limit() != 0)
      clearReadBuffer();
    else if (writePending)
    {
      if (writeBuffer.position() < writeLength)
        writeLength = writeBuffer.position();

      long writePosition = position - realFilePosition;

      // The buffer is in the way.
      if (writePosition >= -src.remaining() && writePosition < writeLength)
      {
        long startPosition = position;
        long writeBytes;

        // The write starts before the buffer.
        if (writePosition < 0)
        {
          int srcLimit = src.limit();

          try
          {
            src.limit(src.position () + (int)-writePosition);

            while (true)
            {
              writeBytes = fileChannel.write(src, position);

              if (writeBytes == 0)
              {
                return (int) (position - startPosition);
              }

              position += writeBytes;

              if (position == realFilePosition)
              {
                break;
              }
            }
          }
          finally
          {
            src.limit(srcLimit);
          }

          writePosition = 0;
        }

        // The write starts somewhere in the buffer and possibly goes outside of it.
        int writeBufferPosition = writeBuffer.position();
        int srcLimit = src.limit();
        int bufferWriteLength = Math.min(writeBuffer.remaining(), src.remaining());

        try
        {
          writeBuffer.position((int)writePosition);
          src.limit(src.position() + bufferWriteLength);
          writeBuffer.put(src);

          // If we wrote beyond the original position, update writeLength to ensure it will be written.
          if (writeBuffer.position() > writeLength)
          {
            writeLength = writeBuffer.position();
          }
        }
        finally
        {
          writeBuffer.position(writeBufferPosition);
          src.limit(srcLimit);
        }

        position += bufferWriteLength;

        if (!src.hasRemaining())
          return (int)(position - startPosition);

        return fileChannel.write(src, position) + (int)(position - startPosition);
      }
    }

    return fileChannel.write(src, position);
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
  public boolean isActiveFile()
  {
    return fileChannel.isActiveFile();
  }

  @Override
  public boolean isReadOnly()
  {
    return readonly;
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException
  {
    if (readonly)
      throw new NonWritableChannelException();

    // Skip the write buffer. The underlying SageFileChannel method will likely be able to handle
    // this massive write more efficiently.
    if (readBuffer.limit() != 0)
      clearReadBuffer();
    else if (writePending)
      flushWriteBuffer();

    long returnValue = fileChannel.write(srcs, offset, length);
    realFilePosition += returnValue;
    return returnValue;
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException
  {
    if (writePending)
    {
      flushWriteBuffer();
    }
    else if (readBuffer.hasRemaining())
    {
      // See if there's anything left in the buffer, but don't refill the read buffer.
      long returnValue = 0;
      int oldLimit;
      int limit = offset + length;
      int i;
      for (i = offset; i < limit; i++)
      {
        if (!readBuffer.hasRemaining())
        {
          break;
        }

        oldLimit = readBuffer.limit();
        try
        {
          readBuffer.limit(readBuffer.position() + Math.min(readBuffer.remaining(), dsts[i].remaining()));
          returnValue += readBuffer.remaining();
          dsts[i].put(readBuffer);
        }
        finally
        {
          readBuffer.limit(oldLimit);
        }
      }

      length = limit - i;
      if (length > 0)
      {
        long readBytes = fileChannel.read(dsts, i, length);

        if (readBytes == -1)
        {
          // This means we also didn't get anything from the read buffer, so we really are EOF.
          if (returnValue == 0)
            return readBytes;
        }
        else
        {
          returnValue += readBytes;
        }
      }

      realFilePosition += returnValue;
      return returnValue;
    }

    return fileChannel.read(dsts, offset, length);
  }

  @Override
  public int read(ByteBuffer dst) throws IOException
  {
    if (writePending)
      flushWriteBuffer();

    // If the provided buffer is larger than the read buffer, don't buffer more data. We will read
    // directly from the disk instead.
    if (dst.remaining() < readBuffer.limit() && !readBuffer.hasRemaining())
    {
      fillReadBuffer();

      if (!readBuffer.hasRemaining())
        return -1;
    }

    int returnValue = 0;

    if (readBuffer.hasRemaining())
    {
      do
      {

        int readBufferLimit = readBuffer.limit();
        try
        {
          int length = Math.min(readBuffer.remaining(), dst.remaining());
          readBuffer.limit(readBuffer.position() + length);
          dst.put(readBuffer);
          returnValue += length;
        }
        finally
        {
          readBuffer.limit(readBufferLimit);
        }

        if (dst.remaining() < readBuffer.limit() && !readBuffer.hasRemaining())
        {
          fillReadBuffer();

          if (!readBuffer.hasRemaining())
            return returnValue;
        }
        else
        {
          break;
        }
      }
      while (dst.hasRemaining());
    }

    if (dst.hasRemaining())
    {
      int readBytes = fileChannel.read(dst);

      if (readBytes == -1)
      {
        // This means we also didn't get anything from the read buffer, so we really are EOF.
        if (returnValue == 0)
          return readBytes;
      }
      else
      {
        realFilePosition += readBytes;
        returnValue += readBytes;
      }
    }

    return returnValue;
  }

  @Override
  public int write(ByteBuffer src) throws IOException
  {
    if (readonly)
      throw new NonWritableChannelException();

    if (readBuffer.limit() != 0)
      clearReadBuffer();

    int returnValue = src.remaining();

    // If the write is bigger than the current capacity, append to what's left in the write buffer,
    // then commit the write buffer.
    if (writeBuffer.position() + returnValue > writeBuffer.limit())
    {
      int srcLimit = src.limit();
      try
      {
        src.limit(src.position() + writeBuffer.remaining());
        writeBuffer.put(src);
        flushWriteBuffer();
      }
      finally
      {
        src.limit(srcLimit);
      }

      int remains = src.remaining();
      // If the remaining write is still larger than the buffer, write it directly.
      if (remains > writeBuffer.remaining())
      {
        do
        {
          fileChannel.write(src);
        }
        while (src.hasRemaining());
        realFilePosition += remains;
        return returnValue;
      }
    }

    writeBuffer.put(src);
    writePending = true;

    return returnValue;
  }

  @Override
  public long position()
  {
    // When we are in read mode, writeBuffer.position() will always be 0. When we are in write mode,
    // readBuffer.limit() and readBuffer.position() will always be 0. writeLength doesn't need to be
    // taken into account here.
    return (realFilePosition - readBuffer.limit()) + (readBuffer.position() | writeBuffer.position());
  }

  @Override
  public long skip(long n) throws IOException
  {
    if (n <= 0)
      return 0;

    long oldOffset = position();
    // This updates realFilePosition.
    position(Math.min(position() + n, size()));

    return position() - oldOffset;
  }

  @Override
  public FileChannel position(long newPosition) throws IOException
  {
    // Try to seek within the read buffer.
    if (readBuffer.limit() != 0)
    {
      long readPosition = newPosition - (realFilePosition - readBuffer.limit());

      if (readPosition >= 0  && readPosition < readBuffer.limit())
      {
        readBuffer.position((int)readPosition);
      }
      else
      {
        clearReadBuffer();
        fileChannel.position(newPosition);
        realFilePosition = newPosition;
      }
    }
    // Try to seek within the write buffer.
    else if (writePending)
    {
      long writePosition = newPosition - realFilePosition;

      if (writeBuffer.position() > writeLength)
        writeLength = writeBuffer.position();

      if (writePosition >= 0 && writePosition < writeLength)
      {
        writeBuffer.position((int)writePosition);
      }
      else
      {
        flushWriteBuffer();
        fileChannel.position(newPosition);
        realFilePosition = newPosition;
      }
    }
    else
    {
      fileChannel.position(newPosition);
      realFilePosition = newPosition;
    }
    return this;
  }

  @Override
  public long size() throws IOException
  {
    // Cache the last size so we don't always need to get it from the source if it doesn't matter.
    if (readonly)
      lastSize = fileChannel.size();
    else
      lastSize = fileChannel.size() + writeBuffer.position();

    return lastSize;
  }

  @Override
  public FileChannel truncate(long size) throws IOException
  {
    if (readonly)
      throw new NonWritableChannelException();

    if (readBuffer.limit() != 0)
      clearReadBuffer();
    else if (writePending)
      flushWriteBuffer();

    fileChannel.truncate(size);
    return this;
  }

  @Override
  public void force(boolean metaData) throws IOException
  {
    if (writePending)
      flushWriteBuffer();
    fileChannel.force(metaData);
  }

  @Override
  protected void implCloseChannel() throws IOException
  {
    if (writePending)
      flushWriteBuffer();
    fileChannel.close();
  }
}
