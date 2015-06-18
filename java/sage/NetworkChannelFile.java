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
package sage;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 *
 * @author Narflex
 */
public class NetworkChannelFile extends FasterRandomFile
{
  protected static final long TIMEOUT = 30000;
  /** Creates a new instance of NetworkChannelFile */
  public NetworkChannelFile(String inHostname, String name, String mode, String inCharset, boolean direct) throws java.io.IOException
  {
    super(inCharset);
    if (mode.indexOf("c") != -1 || mode.indexOf("w") != -1)
      throw new java.io.IOException("Unsupported mode for remote random file:" + mode);
    remoteFilename = name;
    hostname = inHostname;
    this.direct = direct;
    bufSize = 131072;
    rb = direct ? java.nio.ByteBuffer.allocateDirect(bufSize) : java.nio.ByteBuffer.allocate(bufSize);
    rb.clear().limit(0);
    if (remoteFilename != null)
      openConnection();
  }

  public NetworkChannelFile(String inHostname, java.io.File file, String mode, String inCharset, boolean direct) throws java.io.IOException
  {
    this(inHostname, file.toString(), mode, inCharset, direct);
  }

  protected void reopenConnection() throws java.io.IOException
  {
    closeConnection();
    openConnection();
  }

  public void setBufferSize(int size)
  {
    if (rb.capacity() != size)
    {
      rb = java.nio.ByteBuffer.allocateDirect(bufSize = size);
      rb.clear().limit(0);
    }
  }

  protected synchronized void openConnection() throws java.io.IOException
  {
    sock = java.nio.channels.SocketChannel.open(new java.net.InetSocketAddress(hostname, 7818));
    commBuf.clear();
    commBuf.put("OPENW ".getBytes(Sage.BYTE_CHARSET));
    commBuf.put(remoteFilename.getBytes("UTF-16BE"));
    commBuf.put("\r\n".getBytes(Sage.BYTE_CHARSET));
    commBuf.flip();
    sock.write(commBuf);
    commBuf.clear();
    String str = IOUtils.readLineBytes(sock, commBuf, TIMEOUT, null);
    if (!"OK".equals(str))
      throw new java.io.IOException("Error opening remote file of:" + str);
  }

  protected void closeConnection()
  {
    if (sock != null)
    {
      synchronized (this)
      {
        try
        {
          commBuf.clear();
          commBuf.put("QUIT\r\n".getBytes(Sage.BYTE_CHARSET));
          commBuf.flip();
          sock.write(commBuf);
        }
        catch (Exception e){}
      }
      try{sock.close();}catch(Exception e){}
      sock = null;
      maxRemoteSize = 0;
    }
  }

  public void flush() throws java.io.IOException
  {
  }

  public long length() throws java.io.IOException
  {
    if (!activeFile && maxRemoteSize > 0)
      return maxRemoteSize;
    try
    {
      return getRemoteSize();
    }
    catch (java.io.IOException e)
    {
      // Retry the connection once
      reopenConnection();
      return getRemoteSize();
    }
  }

  protected synchronized long getRemoteSize() throws java.io.IOException
  {
    commBuf.clear();
    commBuf.put("SIZE\r\n".getBytes(Sage.BYTE_CHARSET)).flip();
    sock.write(commBuf);
    commBuf.clear();
    String str = IOUtils.readLineBytes(sock, commBuf, TIMEOUT, null);
    long currAvailSize = Long.parseLong(str.substring(0, str.indexOf(' ')));
    currTotalSize = Long.parseLong(str.substring(str.indexOf(' ') + 1));
    maxRemoteSize = Math.max(maxRemoteSize, currAvailSize);
    activeFile = currAvailSize != currTotalSize;
    return maxRemoteSize;
  }

  public void seek(long newfp) throws java.io.IOException
  {
    if (newfp == fp) return;
    // See if we can do this seek within the read buffer we have
    if (rb != null)
    {
      if (newfp > fp && newfp < (fp + rb.remaining()))
      {
        rb.position(rb.position() + (int)(newfp - fp));
      }
      else
      {
        rb.clear().limit(0); // no valid data in buffer
      }
    }
    fp = newfp;
  }

  protected long getRealFp()
  {
    return fp;
  }

  protected int readFromServer(int length, java.nio.ByteBuffer buf) throws java.io.IOException
  {
    if (length == 0) return 0;
    if (length == -1)
    {
      if (maxRemoteSize - getRealFp() > buf.remaining())
      {
        length = buf.remaining();
      }
      else
      {
        if (activeFile || maxRemoteSize == 0)
          getRemoteSize();
        length = (int)Math.min(buf.remaining(), maxRemoteSize - getRealFp());
        int numTries = 200;
        while (activeFile && length < buf.remaining() && numTries-- > 0)
        {
          try{Thread.sleep(50);}catch(Exception e){}
          getRemoteSize();
          length = (int)Math.min(buf.remaining(), maxRemoteSize - fp);
        }
      }
    }
    if (length <= 0)
      return -1; // end of file
    int rv = length;
    synchronized (this)
    {
      boolean tryAgain = true;
      int orgPosition = rb.position();
      while (tryAgain)
      {
        try
        {
          commBuf.clear();
          commBuf.put(("READ " + getRealFp() + " " + length + "\r\n").getBytes(Sage.BYTE_CHARSET));
          commBuf.flip();
          sock.write(commBuf);
          TimeoutHandler.registerTimeout(TIMEOUT, sock);
          while (length > 0)
          {
            length -= sock.read(buf);
          }
          TimeoutHandler.clearTimeout(sock);
          tryAgain = false;
        }
        catch (java.io.IOException e)
        {
          // One more try
          reopenConnection();
          tryAgain =false;
          length = rv;
          rb.position(orgPosition);
        }
      }
    }
    return rv;
  }

  protected void ensureBuffer() throws java.io.IOException
  {
    if (rb.remaining() <= 0)
    {
      rb.clear();
      if (readFromServer(-1, rb) < 0)
        throw new java.io.EOFException();
      rb.flip();
    }
  }

  public int read() throws java.io.IOException
  {
    ensureBuffer();
    fp++;
    return (rb.get() & 0xFF);
  }

  public int readInt() throws java.io.IOException
  {
    ensureBuffer();
    if (rb.remaining() >= 4)
      return rb.getInt();
    int b1,b2,b3,b4;
    fp++;
    b1 = (rb.get() & 0xFF);
    ensureBuffer();
    fp++;
    b2 = (rb.get() & 0xFF);
    ensureBuffer();
    fp++;
    b3 = (rb.get() & 0xFF);
    ensureBuffer();
    fp++;
    b4 = (rb.get() & 0xFF);
    return ((b1 << 24) + (b2 << 16) + (b3 << 8) + b4);
  }

  public short readShort() throws java.io.IOException
  {
    ensureBuffer();
    if (rb.remaining() >= 2)
      return rb.getShort();
    int b1, b2;
    fp++;
    b1 = (rb.get() & 0xFF);
    ensureBuffer();
    fp++;
    b2 = (rb.get() & 0xFF);
    return (short)((b1 << 8) + b2);
  }

  public byte readUnencryptedByte() throws java.io.IOException
  {
    ensureBuffer();
    fp++;
    return rb.get();
  }

  public void read(java.nio.ByteBuffer b) throws java.io.IOException
  {
    if (rb != null && rb.remaining() > 0)
    {
      int currRead = Math.min(rb.remaining(), b.remaining());
      int oldLimit = rb.limit();
      rb.limit(currRead + rb.position());
      b.put(rb);
      rb.limit(oldLimit);
      fp += currRead;
    }
    int leftToRead = b.remaining();
    if (leftToRead > 0)
    {
      if (readFromServer(leftToRead, b) < 0)
        throw new java.io.EOFException();
      fp += leftToRead;
    }
  }

  public int transferTo(java.nio.channels.WritableByteChannel out, long length) throws java.io.IOException
  {
    long startFp = fp;
    while (length > 0)
    {
      ensureBuffer();
      int currRead = (int)Math.min(rb.remaining(), length);
      int oldLimit = rb.limit();
      rb.limit(currRead + rb.position());
      out.write(rb);
      rb.limit(oldLimit);
      fp += currRead;
      length -= currRead;
    }
    return (int)(fp - startFp);
  }

  public void readFully(byte b[], int off, int len) throws java.io.IOException
  {
    if (rb != null && rb.remaining() > 0)
    {
      int currRead = Math.min(rb.remaining(), len);
      rb.get(b, off, currRead);
      fp += currRead;
      len -= currRead;
      off += currRead;
    }
    if (len > 0)
    {
      if (optimizeReadFully)
      {
        if (readFromServer(len, java.nio.ByteBuffer.wrap(b, off, len)) < 0)
          throw new java.io.EOFException();
        fp += len;
      }
      else
      {
        while (len > 0)
        {
          ensureBuffer();
          int currRead = Math.min(rb.remaining(), len);
          rb.get(b, off, currRead);
          fp += currRead;
          len -= currRead;
          off += currRead;
        }
      }
    }
  }

  public int skipBytes(int n) throws java.io.IOException
  {
    seek(fp + n);
    return n;
  }

  public void close() throws java.io.IOException
  {
    activeFile = false;
    closeConnection();
  }

  public void fullFlush() throws java.io.IOException
  {
    flush();
  }

  public void write(int b) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void write(byte b) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void writeUnencryptedByte(byte b) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void write(byte b[], int off, int len) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void write(byte b[])	throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void writeUTF(String s) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void writeBoolean(boolean b)	throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void writeByte(int b) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public final void setLength(long len) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  protected String remoteFilename;
  protected java.nio.channels.SocketChannel sock = null;
  protected java.nio.ByteBuffer commBuf = java.nio.ByteBuffer.allocate(4096);
  protected java.nio.ByteBuffer rb;
  protected String hostname;

  protected long maxRemoteSize;
  protected boolean activeFile;

  protected long currTotalSize;
  protected boolean direct;
  protected int bufSize;
}
