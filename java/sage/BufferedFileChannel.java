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

/**
 *
 * @author Narflex
 */
public class BufferedFileChannel extends FasterRandomFile
{
  protected java.nio.channels.FileChannel fc;
  protected java.nio.ByteBuffer rb;
  protected java.nio.ByteBuffer wb;
  protected int bufSize = 65536;
  protected boolean direct;
  /** Creates a new instance of BufferedFileChannel */
  public BufferedFileChannel(String name, String mode, String inCharset) throws java.io.IOException
  {
    this(new java.io.File(name), mode, inCharset);
  }

  public BufferedFileChannel(java.io.File file, String mode, String inCharset) throws java.io.IOException
  {
    this(file, mode, inCharset, false);
  }

  public BufferedFileChannel(String inCharset, boolean directBuffer) throws java.io.IOException
  {
    super(inCharset);
    direct = directBuffer;
  }

  public BufferedFileChannel(java.io.File file, String mode, String inCharset, boolean directBuffer) throws java.io.IOException
  {
    super(inCharset);
    if (mode.indexOf("c") != -1)
      throw new IllegalArgumentException("BufferedFileChannel does not support crypto read/write");
    if (!"r".equals(mode))
      throw new IllegalArgumentException("BufferedFileChannel only supports read mode until we update RandomAccessFile!!!");
    direct = directBuffer;
    fc = new java.io.FileInputStream(file).getChannel();
  }

  public void setBufferSize(int x)
  {
    bufSize = x;
  }

  public void flush() throws java.io.IOException
  {
    // We only need to clear any buffered writes here
    if (wb != null && wb.position() > 0)
    {
      wb.flip();
      fc.write(wb);
      wb.clear();
    }
  }

  public long length() throws java.io.IOException
  {
    // Write any pending data to disk before we check the length
    flush();
    return fc.size();
  }

  public void seek(long newfp) throws java.io.IOException
  {
    if (newfp == fp) return;
    // Write any pending data before we seek
    flush();
    // See if we can do this seek within the read buffer we have
    if (rb != null)
    {
      if (newfp > fp && newfp < (fp + rb.remaining()))
      {
        rb.position(rb.position() + (int)(newfp - fp));
        fp = newfp;
      }
      else
      {
        rb.clear().limit(0); // no valid data in buffer
        fc.position(fp = newfp);
      }
    }
    else
      fc.position(fp = newfp);
  }

  protected void ensureBuffer() throws java.io.IOException
  {
    if (rb == null)
    {
      rb = direct ? java.nio.ByteBuffer.allocateDirect(bufSize) : java.nio.ByteBuffer.allocate(bufSize);
      rb.clear().limit(0);
    }
    if (rb.remaining() <= 0)
    {
      rb.clear();
      if (fc.read(rb) < 0)
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
    while (leftToRead > 0)
    {
      int currRead = fc.read(b);
      if (currRead < 0)
        throw new java.io.EOFException();
      fp += currRead;
      leftToRead -= currRead;
    }
  }

  public int transferTo(java.nio.channels.WritableByteChannel out, long length) throws java.io.IOException
  {
    long startFp = fp;
    if (rb != null && rb.remaining() > 0)
    {
      int currRead = (int)Math.min(rb.remaining(), length);
      int oldLimit = rb.limit();
      rb.limit(currRead + rb.position());
      out.write(rb);
      rb.limit(oldLimit);
      fp += currRead;
      length -= currRead;
    }
    while (length > 0)
    {
      long curr = fc.transferTo(fp, length, out);
      length -= curr;
      fp += curr;
      if (curr <= 0)
        break;
    }
    fc.position(fp);
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
    while (len > 0)
    {
      int currRead = fc.read(java.nio.ByteBuffer.wrap(b, off, len));
      if (currRead < 0)
        throw new java.io.EOFException();
      fp += currRead;
      off += currRead;
      len -= currRead;
    }
  }

  public int skipBytes(int n) throws java.io.IOException
  {
    seek(fp + n);
    return n;
  }

  public void close() throws java.io.IOException
  {
    flush();
    // This is required to actually cause the disk write, flush() or close() doesn't do it
    // But we never do writes in this class so this is just a performance penalty!!!
    /*		try
		{
			fc.force(true);
		}
		catch (Throwable t)
		{
			// this can fail for RO filesystems
		}*/

    fc.close();
    rb = null;
    wb = null;
    fc = null;
  }

  public void fullFlush() throws java.io.IOException
  {
    flush();
    fc.force(true);
  }

  public void write(int b) throws java.io.IOException
  {
    write((byte)(b & 0xFF));
  }

  private void ensureWriteBuffer(int writeSize) throws java.io.IOException
  {
    if (wb == null)
    {
      wb = direct ? java.nio.ByteBuffer.allocateDirect(bufSize) : java.nio.ByteBuffer.allocate(bufSize);
      wb.clear();
    }
    if (wb.remaining() < writeSize)
    {
      wb.flip();
      fc.write(wb);
      wb.clear();
    }
  }

  public void write(byte b) throws java.io.IOException
  {
    ensureWriteBuffer(1);
    fp++;
    wb.put(b);
  }

  public void writeUnencryptedByte(byte b) throws java.io.IOException
  {
    write(b);
  }

  public void write(java.nio.ByteBuffer bb) throws java.io.IOException
  {
    flush();
    fc.write(bb);
  }

  public void write(byte b[], int off, int len) throws java.io.IOException
  {
    if (wb != null && len > wb.capacity())
    {
      flush();
      fc.write(java.nio.ByteBuffer.wrap(b, off, len));
    }
    else
    {
      ensureWriteBuffer(len);
      wb.put(b, off, len);
    }
  }

  public void write(byte b[])	throws java.io.IOException
  {
    write(b, 0, b.length);
  }

  public void writeInt(int s)	throws java.io.IOException
  {
    ensureWriteBuffer(4);
    wb.putInt(s);
  }

  public void writeLong(long s) throws java.io.IOException
  {
    ensureWriteBuffer(8);
    wb.putLong(s);
  }

  public void setLength(long len) throws java.io.IOException
  {
    flush();
    fp = Math.min(fp, len);
    if (len < length())
      fc.truncate(len);
    else
      fc.position(len);
  }
}
