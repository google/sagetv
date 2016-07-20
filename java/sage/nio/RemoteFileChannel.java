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

import sage.IOUtils;
import sage.Sage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

public class RemoteFileChannel extends FileChannel implements SageFileChannel
{
  // This timeout is used to make sure we don't wait forever for the server to reply OK and is used
  // to make sure that read transfers don't hang. In the case of read transfers, this should be more
  // than enough time per chunk of data.
  public static final long TIMEOUT = 30000;
  // This should be a good buffer size. This is only used if transferTo or transferFrom are used.
  public static final int TRANSFER_BUFFER_SIZE = 131072;

  private final String hostname;
  private String remoteFilename;
  private final boolean readonly;
  private final int uploadId;

  private boolean activeFile = false;
  private long currTotalSize = 0;
  private long maxRemoteSize = 0;

  private long remoteOffset = 0;
  private SocketChannel socket = null;
  private ByteBuffer commBuf = ByteBuffer.allocateDirect(4096);
  private ByteBuffer transBuf;
  private boolean closed = false;

  /**
   * Open a remote file as a <code>FileChannel</code> for read only access.
   *
   * @param hostname The hostname of the media server hosting the file.
   * @param file The file to be opened.
   * @throws IOException If there is an I/O related error.
   */
  public RemoteFileChannel(String hostname, File file) throws IOException
  {
    this(hostname, file.getPath());
  }

  /**
   * Open a remote file as a <code>FileChannel</code> for read only access.
   *
   * @param hostname The hostname of the media server hosting the file.
   * @param name The the full path and name of the file to be opened.
   * @throws IOException If there is an I/O related error.
   */
  public RemoteFileChannel(String hostname, String name) throws IOException
  {
    this.hostname = hostname;
    remoteFilename = name;
    this.readonly = true;
    this.uploadId = -1;
    connect();
  }

  /**
   * Open a remote file as a <code>FileChannel</code> for read/write access.
   *
   * @param hostname The hostname of the media server hosting the file.
   * @param file The file to be opened.
   * @param uploadId The ID that authorizes write access.
   * @throws IOException If there is an I/O related error.
   */
  public RemoteFileChannel(String hostname, File file, int uploadId) throws IOException
  {
    this(hostname, file.getPath(), uploadId);
  }

  /**
   * Open a remote file as a <code>FileChannel</code> for read/write access.
   *
   * @param hostname The hostname of the media server hosting the file.
   * @param name The the full path and name of the file to be opened.
   * @param uploadId The ID that authorizes write access.
   * @throws IOException If there is an I/O related error.
   */
  public RemoteFileChannel(String hostname, String name, int uploadId) throws IOException
  {
    this.hostname = hostname;
    remoteFilename = name;
    this.readonly = false;
    this.uploadId = uploadId;
    connect();
  }

  @Override
  public void openFile(String name) throws IOException
  {
    boolean reconnect = !executeCommand("CLOSE\r\n").equals("OK");
    // If for some reason we get an error when trying to close the file, just disconnect.
    if (reconnect)
      disconnect();

    remoteFilename = name;
    activeFile = false;
    currTotalSize = 0;
    maxRemoteSize = 0;
    remoteOffset = 0;

    if (reconnect)
      connect();
    else
      open();
  }

  @Override
  public void openFile(File file) throws IOException
  {
    openFile(file.getPath());
  }

  private void open() throws IOException
  {
    if (remoteFilename == null)
      return;

    commBuf.clear();
    if (readonly)
    {
      commBuf.put("OPENW ".getBytes(Sage.BYTE_CHARSET));
      commBuf.put(remoteFilename.getBytes(StandardCharsets.UTF_16BE));
    }
    else
    {
      commBuf.put("WRITEOPENW ".getBytes(Sage.BYTE_CHARSET));
      commBuf.put(remoteFilename.getBytes(StandardCharsets.UTF_16BE));
      commBuf.put((" " + Integer.toString(uploadId)).getBytes(Sage.BYTE_CHARSET));
    }

    commBuf.put("\r\n".getBytes(Sage.BYTE_CHARSET));
    commBuf.flip();
    socket.write(commBuf);
    commBuf.clear();
    String response = IOUtils.readLineBytes(socket, commBuf, TIMEOUT, null);
    if (!"OK".equals(response))
    {
      disconnect();
      throw new java.io.IOException("Error opening remote file of:" + response);
    }
  }

  private void connect() throws IOException
  {
    socket = java.nio.channels.SocketChannel.open(new java.net.InetSocketAddress(hostname, 7818));
    open();
  }

  private void disconnect()
  {
    if (socket != null)
    {
      synchronized (this)
      {
        try
        {
          commBuf.clear();
          commBuf.put("QUIT\r\n".getBytes(Sage.BYTE_CHARSET));
          commBuf.flip();
          socket.write(commBuf);
        }
        catch (Exception e) {}
      }
      try{
        socket.close();
      } catch (Exception e) {}
      socket = null;
    }
  }

  private synchronized void reconnect() throws IOException
  {
    disconnect();
    if (closed)
      throw new IOException("Remote channel is closed.");

    connect();
  }

  private long getMaxRead(long position, long count) throws IOException
  {
    // We can't read more than what is in the file. The first part checks a cached variable, the
    // second part checks the file directly.
    count = Math.min(
        maxRemoteSize == 0 || (position + count >= maxRemoteSize && activeFile) ?
            reallyGetSize() - position : maxRemoteSize - position,
        count);

    // If we are trying to read beyond the end of the file, don't read anything.
    return (count == 0) ? 0 : count;
  }

  // You must synchronize startRead and readMore in the method using these so no other
  // communication can be attempted while in use.
  private int startRead(long position, long count, ByteBuffer dst, boolean retry) throws IOException
  {
    // We are trying to read beyond or are at the end of the file.
    if (count <= 0)
      return -1;

    try
    {
      commBuf.clear();
      commBuf.put(("READ " + position + " " + count + "\r\n").getBytes(Sage.BYTE_CHARSET));
      commBuf.flip();
      socket.write(commBuf);
      // Perform the first read here. Sometimes when the connection is closed on the remote end, we
      // don't see an exception until we try to read.
      return readMore(dst);
    }
    catch (IOException e)
    {
      if (retry)
      {
        reconnect();
        return startRead(position, count, dst, false);
      }
      else
      {
        throw e;
      }
    }
  }

  private int readMore(ByteBuffer dst) throws IOException
  {
    if (!dst.hasRemaining())
      return 0;

    int returnValue = -1;

    while (dst.hasRemaining())
    {
      int readBytes = socket.read(dst);

      if (readBytes == -1)
        break;

      if (returnValue == -1)
        returnValue = 0;

      returnValue += readBytes;
    }

    return returnValue;
  }

  // You must synchronize startWrite and writeMore in the method using these so no other
  // communication can be attempted while in use.
  private int startWrite(long position, long count, ByteBuffer src, boolean retry) throws IOException
  {
    try
    {
      commBuf.clear();
      commBuf.put(("WRITE " + position + " " + count + "\r\n").getBytes(Sage.BYTE_CHARSET));
      commBuf.flip();
      socket.write(commBuf);
      return writeMore(src);
    }
    catch (IOException e)
    {
      if (retry)
      {
        reconnect();
        return startWrite(position, count, src, false);
      }
      else
      {
        throw e;
      }
    }
  }

  private int writeMore(ByteBuffer src) throws IOException
  {
    int returnValue = 0;

    while (src.hasRemaining())
    {
      returnValue += socket.write(src);
    }

    return returnValue;
  }

  ByteBuffer singleByte;
  @Override
  public int readUnsignedByte() throws IOException
  {
    if (singleByte == null)
    {
      singleByte = ByteBuffer.allocate(1);
    }
    singleByte.clear();
    int bytes = read(singleByte);
    singleByte.flip();

    if (bytes == -1)
      throw new java.io.EOFException();

    return singleByte.get() & 0xFF;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException
  {
    int returnValue = read(dst, remoteOffset);

    if (returnValue == -1)
      return returnValue;

    remoteOffset += returnValue;
    return returnValue;
  }

  @Override
  public synchronized long read(ByteBuffer[] dsts, int offset, int length) throws IOException
  {
    if (length == 0)
      return 0;

    long returnValue = 0;
    long readBytes;
    long totalLength = 0;

    int remains;
    boolean bufferAvailable = false;
    int limit = offset + length;
    for (int i = offset; i < limit; i++)
    {
      // Don't continue if it's not going to matter.
      if (totalLength > maxRemoteSize && totalLength > size())
        break;

      remains = dsts[i].remaining();

      // Since we are iterating through the buffers either way, we might as well find the first one
      // that actually has space available.
      if (remains > 0)
      {
        bufferAvailable = true;
        totalLength += remains;
      }
      else if (!bufferAvailable)
      {
        offset += 1;
      }
    }

    // All of the buffers do not have space available.
    if (!bufferAvailable)
      return 0;

    // When we break out of the loop, we are one too far ahead.
    offset--;

    totalLength = getMaxRead(remoteOffset, totalLength);
    readBytes = startRead(remoteOffset, totalLength, dsts[offset++], true);
    if (readBytes == -1)
      return -1;

    returnValue += readBytes;

    // The first buffer is already fully used.
    for (int i = offset; i < length; i++)
    {
      while (dsts[i].hasRemaining())
      {
        readBytes = readMore(dsts[i]);

        if (readBytes == -1)
          break;

        returnValue += readBytes;
      }
    }

    remoteOffset += returnValue;
    return returnValue;
  }

  @Override
  public int write(ByteBuffer src) throws IOException
  {
    if (readonly)
      throw new IOException("Channel is read only.");

    int length = write(src, remoteOffset);
    remoteOffset += length;
    return length;
  }

  @Override
  public synchronized long write(ByteBuffer[] srcs, int offset, int length) throws IOException
  {
    if (readonly)
      throw new IOException("Channel is read only.");

    if (length == 0)
      return 0;

    long returnValue;
    long totalLength = 0;

    int remains;
    boolean bufferAvailable = false;
    int limit = offset + length;
    for (int i = offset; i < limit; i++)
    {
      remains = srcs[i].remaining();

      // Since we are iterating through the buffers either way, find the first one that actually has
      // data available.
      if (remains > 0)
      {
        bufferAvailable = true;
        totalLength += remains;
      }
      else if (!bufferAvailable)
      {
        offset += 1;
      }
    }

    // All of the buffers do not have data available.
    if (!bufferAvailable)
      return 0;

    // When we break out of the loop, we are one too far ahead.
    offset--;

    returnValue = startWrite(remoteOffset, totalLength, srcs[offset++], true);

    for (int i = offset; i < length; i++)
    {
      returnValue += writeMore(srcs[i]);
    }

    remoteOffset += returnValue;
    return returnValue;
  }

  @Override
  public long position()
  {
    return remoteOffset;
  }

  @Override
  public FileChannel position(long newPosition) throws IOException
  {
    remoteOffset = newPosition;
    return this;
  }

  @Override
  public long skip(long n) throws IOException
  {
    if (n <= 0)
      return 0;

    long oldOffset = remoteOffset;
    position(Math.min(remoteOffset + n, size()));

    return remoteOffset - oldOffset;
  }

  private long reallyGetSize() throws IOException
  {
    String response = executeCommand("SIZE\r\n");

    long currAvailSize = Long.parseLong(response.substring(0, response.indexOf(' ')));
    currTotalSize = Long.parseLong(response.substring(response.indexOf(' ') + 1));
    maxRemoteSize = Math.max(maxRemoteSize, currAvailSize);
    activeFile = currAvailSize != currTotalSize;
    return maxRemoteSize;
  }

  @Override
  public long size() throws IOException
  {
    if (activeFile || maxRemoteSize == 0)
    {
      return reallyGetSize();
    }
    return maxRemoteSize;
  }

  @Override
  public FileChannel truncate(long size) throws IOException
  {
    if (readonly)
      throw new IOException("Channel is read only.");

    String response = executeCommand("TRUNC " + size + "\r\n");

    if (!"OK".equals(response))
      throw new IOException("Error truncating remote file of:" + response);

    return this;
  }

  @Override
  public void force(boolean metaData) throws IOException
  {
    if (readonly)
      throw new IOException("Channel is read only.");

    String response = executeCommand("FORCE " + (metaData ? "TRUE" : "FALSE") + "\r\n");

    if (!"OK".equals(response))
      throw new IOException("Error forcing remote file of:" + response);
  }

  @Override
  public long transferTo(long count, WritableByteChannel target) throws IOException
  {
    long returnValue = transferTo(remoteOffset, count, target);
    remoteOffset += returnValue;
    return returnValue;
  }

  @Override
  public synchronized long transferTo(long position, long count, WritableByteChannel target) throws IOException
  {
    if (count == 0)
      return 0;

    int bytesRead;
    long remaining = count;

    if (transBuf == null)
    {
      transBuf =  ByteBuffer.allocateDirect(TRANSFER_BUFFER_SIZE);
    }

    transBuf.clear();
    bytesRead = startRead(position, count, transBuf, true);
    if (bytesRead == -1)
      return 0;

    transBuf.flip();
    while (transBuf.hasRemaining())
    {
      target.write(transBuf);
    }
    remaining -= bytesRead;

    while (remaining > 0)
    {
      transBuf.clear();
      bytesRead = readMore(transBuf);

      if (bytesRead <= 0)
        break;

      transBuf.flip();
      while (transBuf.hasRemaining())
      {
        target.write(transBuf);
      }
      remaining -= bytesRead;
    }

    return count - remaining;
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long count) throws IOException
  {
    long returnValue = transferFrom(src, remoteOffset, count);
    remoteOffset += returnValue;
    return returnValue;
  }

  @Override
  public synchronized long transferFrom(ReadableByteChannel src, long position, long count) throws IOException
  {
    if (readonly)
      throw new IOException("Channel is read only.");

    if (count == 0)
      return 0;

    int bytesWrite;
    long remaining = count;

    if (transBuf == null)
    {
      transBuf =  ByteBuffer.allocateDirect(TRANSFER_BUFFER_SIZE);
    }

    transBuf.clear();
    bytesWrite = src.read(transBuf);
    if (bytesWrite == -1)
      return 0;

    transBuf.flip();
    startWrite(position, count, transBuf, true);
    remaining -= bytesWrite;

    while (remaining > 0)
    {
      transBuf.clear();
      bytesWrite = src.read(transBuf);
      if (bytesWrite == -1)
        break;

      transBuf.flip();
      writeMore(transBuf);
      remaining -= bytesWrite;
    }

    return count - remaining;
  }

  @Override
  public synchronized int read(ByteBuffer dst, long position) throws IOException
  {
    // The returned value will never be greater than dst.remaining().
    int length = (int)getMaxRead(position, dst.remaining());

    if (length == 0)
      return -1;

    // This will read into the buffer the full length.
    length = startRead(position, length, dst, true);

    return length;
  }

  @Override
  public synchronized int write(ByteBuffer src, long position) throws IOException
  {
    if (readonly)
      throw new IOException("Channel is read only.");

    int length = src.remaining();

    if (length == 0)
      return 0;

    // This will also write out the remaining bytes.
    startWrite(position, length, src, true);

    return length;
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
    closed = true;
    disconnect();
  }

  @Override
  public boolean isReadOnly()
  {
    return readonly;
  }

  public synchronized String executeCommand(String command) throws IOException
  {
    commBuf.clear();
    commBuf.put((command.endsWith("\r\n") ? command : (command + "\r\n")).getBytes(Sage.BYTE_CHARSET));
    commBuf.flip();

    return executeCommand(commBuf);
  }

  public synchronized String executeCommand(ByteBuffer command) throws IOException
  {
    int position = command.position();
    try
    {
      socket.write(command);
      command.clear();
      return IOUtils.readLineBytes(socket, commBuf, TIMEOUT, null);
    }
    catch (IOException e)
    {
      reconnect();
      command.position(position);
      socket.write(command);
      command.clear();
      return IOUtils.readLineBytes(socket, commBuf, TIMEOUT, null);
    }
  }
}
