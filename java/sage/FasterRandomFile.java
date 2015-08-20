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

/* also buffers reads to a RandomAccessFile */
public class FasterRandomFile extends FastRandomFile
{
  protected byte rbuff[];
  protected int rbuffptr = 0;
  protected int rbufflen = 0;
  protected boolean optimizeReadFully = true;

  public FasterRandomFile(String name, String mode, String inCharset) throws java.io.IOException
  {
    super(name, mode, inCharset);
    rbuff = new byte[4096];
  }

  protected FasterRandomFile(String inCharset) throws java.io.IOException
  {
    super(inCharset);
    rbuff = new byte[4096];
  }

  public FasterRandomFile(java.io.File file, String mode, String inCharset) throws java.io.IOException
  {
    super(file, mode, inCharset);
    rbuff = new byte[4096];
  }

  public void setOptimizeReadFully(boolean x) { optimizeReadFully = x; }

  public void setBufferSize(int x)
  {
    rbuff = new byte[x];
  }

  public void flush() throws java.io.IOException
  {
    super.flush();
    rbuffptr = 0;
    rbufflen = 0;
  }

  public long length() throws java.io.IOException
  {
    // Avoid a flush unless we're actually writing so we don't clear the read buffer
    if (buffptr > 0)
    {
      flush();
    }
    return raf.length();
  }

  public void seek(long newfp) throws java.io.IOException
  {
    // See if we can do this within the buffer we have...but if we have any write buffer
    // pending, we need to flush that out...we don't try to 'read' from the write buffer
    if ((newfp > fp - rbuffptr) && newfp < (fp + rbufflen - rbuffptr) && buffptr == 0)
    {
      rbuffptr += (int) (newfp - fp);
      fp = newfp;
    }
    else
    {
      flush();
      raf.seek(fp = newfp);
    }
  }

  protected void ensureBuffer() throws java.io.IOException
  {
    if (rbufflen <= rbuffptr)
    {
      rbufflen = raf.read(rbuff);
      rbuffptr = 0;
      if (rbufflen < 0)
        throw new java.io.EOFException();
    }
  }

  public int read() throws java.io.IOException
  {
    if (rbufflen <= rbuffptr)
      ensureBuffer();
    if (crypto)
    {
      int b = (rbuff[rbuffptr++] & 0xFF);
      b = b ^ (cryptoKeys[((int)(fp++)) % 128]);
      b = (((b & 0x0F) << 4) | ((b & 0xF0) >> 4));
      return b;
    }
    else
    {
      fp++;
      return (rbuff[rbuffptr++] & 0xFF);
    }
  }

  public int readInt() throws java.io.IOException
  {
    if (rbufflen <= rbuffptr)
      ensureBuffer();
    int b1,b2,b3,b4;
    if (crypto)
    {
      b1 = (rbuff[rbuffptr++] & 0xFF);
      b1 = b1 ^ (cryptoKeys[((int)(fp++)) % 128]);
      b1 = (((b1 & 0x0F) << 4) | ((b1 & 0xF0) >> 4));
    }
    else
    {
      fp++;
      b1 = (rbuff[rbuffptr++] & 0xFF);
    }
    if (rbufflen <= rbuffptr)
      ensureBuffer();
    if (crypto)
    {
      b2 = (rbuff[rbuffptr++] & 0xFF);
      b2 = b2 ^ (cryptoKeys[((int)(fp++)) % 128]);
      b2 = (((b2 & 0x0F) << 4) | ((b2 & 0xF0) >> 4));
    }
    else
    {
      fp++;
      b2 = (rbuff[rbuffptr++] & 0xFF);
    }
    if (rbufflen <= rbuffptr)
      ensureBuffer();
    if (crypto)
    {
      b3 = (rbuff[rbuffptr++] & 0xFF);
      b3 = b3 ^ (cryptoKeys[((int)(fp++)) % 128]);
      b3 = (((b3 & 0x0F) << 4) | ((b3 & 0xF0) >> 4));
    }
    else
    {
      fp++;
      b3 = (rbuff[rbuffptr++] & 0xFF);
    }
    if (rbufflen <= rbuffptr)
      ensureBuffer();
    if (crypto)
    {
      b4 = (rbuff[rbuffptr++] & 0xFF);
      b4 = b4 ^ (cryptoKeys[((int)(fp++)) % 128]);
      b4 = (((b4 & 0x0F) << 4) | ((b4 & 0xF0) >> 4));
    }
    else
    {
      fp++;
      b4 = (rbuff[rbuffptr++] & 0xFF);
    }
    if ((b1 | b2 | b3 | b4) < 0)
      throw new java.io.EOFException();
    return ((b1 << 24) + (b2 << 16) + (b3 << 8) + b4);
  }

  public short readShort() throws java.io.IOException
  {
    int b1, b2;
    if (rbufflen <= rbuffptr)
      ensureBuffer();
    if (crypto)
    {
      b1 = (rbuff[rbuffptr++] & 0xFF);
      b1 = b1 ^ (cryptoKeys[((int)(fp++)) % 128]);
      b1 = (((b1 & 0x0F) << 4) | ((b1 & 0xF0) >> 4));
    }
    else
    {
      fp++;
      b1 = (rbuff[rbuffptr++] & 0xFF);
    }
    if (rbufflen <= rbuffptr)
      ensureBuffer();
    if (crypto)
    {
      b2 = (rbuff[rbuffptr++] & 0xFF);
      b2 = b2 ^ (cryptoKeys[((int)(fp++)) % 128]);
      b2 = (((b2 & 0x0F) << 4) | ((b2 & 0xF0) >> 4));
    }
    else
    {
      fp++;
      b2 = (rbuff[rbuffptr++] & 0xFF);
    }
    if ((b1 | b2) < 0)
      throw new java.io.EOFException();
    return (short)((b1 << 8) + b2);
  }

  public byte readUnencryptedByte() throws java.io.IOException
  {
    if (rbufflen <= rbuffptr)
      ensureBuffer();
    fp++;
    return rbuff[rbuffptr++];
  }

  public void read(java.nio.ByteBuffer b) throws java.io.IOException
  {
    if (crypto) throw new UnsupportedOperationException();
    int leftToRead = b.remaining();
    do
    {
      if (rbufflen <= rbuffptr)
        ensureBuffer();
      int currRead = Math.min(leftToRead, rbufflen - rbuffptr);
      b.put(rbuff, rbuffptr, currRead);
      leftToRead -= currRead;
      rbuffptr += currRead;
      fp += currRead;
    }while (leftToRead > 0);
  }

  public int transferTo(java.nio.channels.WritableByteChannel out, long length) throws java.io.IOException
  {
    if (crypto) throw new UnsupportedOperationException();
    long startFp = fp;
    do
    {
      if (rbufflen <= rbuffptr)
        ensureBuffer();
      int currRead = (int)Math.min(length, rbufflen - rbuffptr);
      java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(rbuff, rbuffptr, currRead);
      while (bb.remaining() > 0)
        out.write(bb);
      length -= currRead;
      rbuffptr += currRead;
      fp += currRead;
    }while (length > 0);
    return (int)(fp - startFp);
  }

  public void readFully(byte b[], int off, int len) throws java.io.IOException
  {
    int leftToRead = len;
    if (optimizeReadFully)
    {
      int leftInBuffer = rbufflen - rbuffptr;
      if (leftInBuffer > 0)
      {
        int currRead = Math.min(leftToRead, rbufflen - rbuffptr);
        System.arraycopy(rbuff, rbuffptr, b, off, currRead);
        leftToRead -= currRead;
        rbuffptr += currRead;
      }
      raf.readFully(b, len - leftToRead + off, leftToRead);
    }
    else
    {
      int currentOff = off;
      do
      {
        ensureBuffer();
        int currRead = Math.min(leftToRead, rbufflen - rbuffptr);
        System.arraycopy(rbuff, rbuffptr, b, currentOff, currRead);
        leftToRead -= currRead;
        rbuffptr += currRead;
        currentOff += currRead;
      }while (leftToRead > 0);
    }
    if (crypto)
    {
      for (int i = off; i < off + len; i++)
      {
        int x = b[i] ^ (cryptoKeys[((int)(fp++)) % 128]);
        b[i] = (byte)(((x & 0x0F) << 4) | ((x & 0xF0) >> 4));
      }
    }
    else
      fp += len;
  }

  public int skipBytes(int n) throws java.io.IOException
  {
    // Narflex: Don't reset the read buffer unless we have to; the seek call from the super.skipBytes will handle that
    //		rbuffptr = 0;
    //		rbufflen = 0;
    return super.skipBytes(n);
  }
}
