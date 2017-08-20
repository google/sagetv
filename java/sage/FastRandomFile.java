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

/* buffers writes to a RandomAccessFile */
/* flush() must be called after you're done writing a chunk. */
public class FastRandomFile implements java.io.DataOutput, java.io.DataInput
{
  protected java.io.RandomAccessFile raf = null;
  protected byte buff[] = new byte[65536];
  protected int buffptr = 0;
  protected long fp = 0;

  public FastRandomFile(String name, String mode, String inCharset) throws java.io.IOException
  {
    myCharset = inCharset;
    isI18N = Sage.I18N_CHARSET.equals(myCharset);
    if (crypto = mode.endsWith("c"))
      mode = mode.substring(0, mode.length() - 1);
    raf = new java.io.RandomAccessFile(name, mode);
    if (crypto)
      cryptoKeys = (byte[]) (Sage.q);
  }

  public FastRandomFile(java.io.File file, String mode, String inCharset) throws java.io.IOException
  {
    myCharset = inCharset;
    isI18N = Sage.I18N_CHARSET.equals(myCharset);
    if (crypto = mode.endsWith("c"))
      mode = mode.substring(0, mode.length() - 1);
    raf = new java.io.RandomAccessFile(file, mode);
    if (crypto)
      cryptoKeys = (byte[]) (Sage.q);
  }

  public void setCrypto(byte[] crypt)
  {
    cryptoKeys = crypt;
  }

  // This is used to do network file streaming so that the local file isn't actually opened
  protected FastRandomFile(String inCharset) throws java.io.IOException
  {
    myCharset = inCharset;
    isI18N = Sage.I18N_CHARSET.equals(myCharset);
  }

  public long getFilePointer()
  {
    return fp;
  }

  public void close() throws java.io.IOException
  {
    flush();
    // This is required to actually cause the disk write, flush() or close() doesn't do it
    try
    {
      raf.getFD().sync();
    }
    catch (Throwable t)
    {
      // this can fail for RO filesystems
    }

    raf.close();
    buff = null;
  }

  public void fullFlush() throws java.io.IOException
  {
    flush();
    raf.getFD().sync();
  }

  public void write(int b) throws java.io.IOException
  {
    write((byte)(b & 0xFF));
  }

  public void write(byte b) throws java.io.IOException
  {
    if (crypto)
    {
      b = (byte) ((((b & 0x0F) << 4) | ((b & 0xF0) >> 4)) ^ (cryptoKeys[((int)fp) % 128]));
    }
    if (circularFileSize > 0 && fp == circularFileSize)
      seek(0);
    fp++;
    buff[buffptr++] = b;
    if (buffptr == buff.length)
      flush();
  }

  public void writeUnencryptedByte(byte b) throws java.io.IOException
  {
    fp++;
    buff[buffptr++] = b;
    if (buffptr == buff.length)
      flush();
  }

  public void flush() throws java.io.IOException
  {
    if (buffptr > 0)
    {
      raf.write(buff, 0, buffptr);
      buffptr = 0;
    }
  }

  // This is intended to be an efficient way of writing data to the file for cmd length headers.
  // If we can skip back the desired amount and just write it into the current buffer, then we will do that,
  // otherwise, we'll just skip back to write the one int and then be back at the current buffer
  // position as before the call
  private byte[] intWriteBuf = new byte[4];
  public void writeIntAtOffset(long targetFp, int s) throws java.io.IOException
  {
    byte b1 = (byte)((s >>> 24) & 0xFF);
    byte b2 = (byte)((s >>> 16) & 0xFF);
    byte b3 = (byte)((s >>> 8) & 0xFF);
    byte b4 = (byte)(s & 0xFF);
    if (crypto)
    {
      b1 = (byte) ((((b1 & 0x0F) << 4) | ((b1 & 0xF0) >> 4)) ^ (cryptoKeys[((int)targetFp) % 128]));
      b2 = (byte) ((((b2 & 0x0F) << 4) | ((b2 & 0xF0) >> 4)) ^ (cryptoKeys[((int)(targetFp + 1)) % 128]));
      b3 = (byte) ((((b3 & 0x0F) << 4) | ((b3 & 0xF0) >> 4)) ^ (cryptoKeys[((int)(targetFp + 2)) % 128]));
      b4 = (byte) ((((b4 & 0x0F) << 4) | ((b4 & 0xF0) >> 4)) ^ (cryptoKeys[((int)(targetFp + 3)) % 128]));
    }
    // We need to check the buffer location for each byte written, or else we may have part of our 32 bit write
    // stomped on by the buffer write out later
    int skipBackWriteAmount = Math.min(4, (int)(fp - buffptr - targetFp));
    if (skipBackWriteAmount > 0)
    {
      intWriteBuf[0] = b1; intWriteBuf[1] = b2; intWriteBuf[2] = b3; intWriteBuf[3] = b4;
      raf.seek(targetFp);
      raf.write(intWriteBuf, 0, skipBackWriteAmount);
      raf.seek(fp - buffptr);
    }

    if (skipBackWriteAmount < 1)
      buff[buffptr - ((int)(fp - targetFp))] = b1;
    if (skipBackWriteAmount < 2)
      buff[buffptr - ((int)(fp - targetFp)) + 1] = b2;
    if (skipBackWriteAmount < 3)
      buff[buffptr - ((int)(fp - targetFp)) + 2] = b3;
    if (skipBackWriteAmount < 4)
      buff[buffptr - ((int)(fp - targetFp)) + 3] = b4;
  }

  public void write(byte b[], int off, int len) throws java.io.IOException
  {
    if (crypto || circularFileSize > 0)
    {
      while (len-- > 0)
        write(b[off++]);
    }
    else
    {
      while (len > 0)
      {
        int rem = buff.length - buffptr;
        if (len < rem)
        {
          System.arraycopy(b, off, buff, buffptr, len);
          buffptr += len;
          fp += len;
          break;
        }
        else
        {
          System.arraycopy(b, off, buff, buffptr, rem);
          buffptr += rem;
          off += rem;
          len -= rem;
          fp += rem;
          flush();
        }
      }
    }
  }

  public void write(byte b[])	throws java.io.IOException
  {
    write(b, 0, b.length);
  }

  public void seek(long newfp) throws java.io.IOException
  {
    flush();
    raf.seek(fp = newfp);
  }

  public int read() throws java.io.IOException
  {
    if (crypto)
    {
      int b = raf.read();
      b = b ^ (cryptoKeys[((int)(fp++)) % 128]);
      b = (((b & 0x0F) << 4) | ((b & 0xF0) >> 4));
      return b;
    }
    else
    {
      fp++;
      return raf.read();
    }
  }

  public boolean readBoolean() throws java.io.IOException
  {
    int ch = read();
    if (ch < 0)
      throw new java.io.EOFException();
    return (ch != 0);
  }

  public byte readByte() throws java.io.IOException
  {
    int ch = read();
    if (ch < 0)
      throw new java.io.EOFException();
    return (byte)(ch);
  }

  public byte readUnencryptedByte() throws java.io.IOException
  {
    fp++;
    int ch = raf.read();
    if (ch < 0)
      throw new java.io.EOFException();
    return (byte)(ch);
  }

  public int readInt() throws java.io.IOException
  {
    int b1 = this.read();
    int b2 = this.read();
    int b3 = this.read();
    int b4 = this.read();
    if ((b1 | b2 | b3 | b4) < 0)
      throw new java.io.EOFException();
    return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
  }

  public long readLong() throws java.io.IOException
  {
    return ((long)(readInt()) << 32) + (readInt() & 0xFFFFFFFFL);
  }

  // For optimizing UTF reads
  private byte[] bytearr = null;
  private char[] chararr = null;
  // NOTE: We have a special case here where we want to handle strings that are larger than 64k. We do that by writing out 0xFFFF for the length which indicates that the next 4 bytes will
  // have the actual string length; then we go from there.
  public String readUTF()	throws java.io.IOException
  {
    if (isI18N)
    {
      int utflen = readUnsignedShort();
      if (utflen == 0)
        return "";
      else if (utflen == 0xFFFF)
        utflen = readInt();
      if (bytearr == null || bytearr.length < utflen)
      {
        bytearr = new byte[utflen*2];
        chararr = new char[utflen*2];
      }

      int c, c2, c3;
      int incount = 0;
      int outcount = 0;

      readFully(bytearr, 0, utflen);

      while (incount < utflen) {
        // Fast path for all 7 bit ASCII chars
        c = bytearr[incount] & 0xFF;
        if (c > 127) break;
        incount++;
        chararr[outcount++]=(char)c;
      }

      int x;
      while (incount < utflen) {
        c = bytearr[incount] & 0xFF;
        if (c < 128) {
          incount++;
          chararr[outcount++]=(char)c;
          continue;
        }
        // Look at the top four bits only, since only they can affect this
        x = c >> 4;
        if (x == 12 || x == 13) {
          // 110xxxxx 10xxxxxx - 2 bytes for this char
          incount += 2;
          if (incount > utflen)
            throw new java.io.UTFDataFormatException("bad UTF data: missing second byte of 2 byte char at " + incount);
          c2 = bytearr[incount - 1];
          // Verify next byte starts with 10xxxxxx
          if ((c2 & 0xC0) != 0x80)
            throw new java.io.UTFDataFormatException("bad UTF data: second byte format after 110xxxx is wrong char: 0x" +
                Integer.toString((int)c2, 16) + " count: " + incount);
          chararr[outcount++]=(char)(((c & 0x1F) << 6) | (c2 & 0x3F));
        }
        else if (x == 14)
        {
          // 1110xxxx 10xxxxxx 10xxxx - 3 bytes for this char
          incount += 3;
          if (incount > utflen)
            throw new java.io.UTFDataFormatException("bad UTF data: missing extra bytes of 3 byte char at " + incount);
          c2 = bytearr[incount - 2];
          c3 = bytearr[incount - 1];
          // Verify next bytes start with 10xxxxxx
          if (((c2 & 0xC0) != 0x80) || ((c3 & 0xC0) != 0x80))
            throw new java.io.UTFDataFormatException("bad UTF data: extra byte format after 1110xxx is wrong char2: 0x" +
                Integer.toString((int)c2, 16) + " char3: " + Integer.toString((int)c3, 16) + " count: " + incount);
          chararr[outcount++]=(char)(((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F));
        }
        else
        {
          // No need to support beyond this, as we only have 16 bit chars in Java
          throw new java.io.UTFDataFormatException("bad UTF data: we don't support more than 16 bit chars char: " +
              Integer.toString((int)c, 16) + " count:" + incount);
        }
      }
      return new String(chararr, 0, outcount);
    }
    else
    {
      int len = readShort();
      if (len > 0)
      {
        byte[] bytes = new byte[len];
        readFully(bytes, 0, len);
        return new String(bytes, myCharset);
      }
      else return "";
    }
  }

  public StringBuffer readUTF(StringBuffer sb) throws java.io.IOException
  {
    if (isI18N)
    {
      int utflen = readUnsignedShort();
      if (utflen == 0xFFFF)
        utflen = readInt();
      if (bytearr == null || bytearr.length < utflen)
      {
        bytearr = new byte[utflen*2];
        chararr = new char[utflen*2];
      }
      if (sb == null)
        sb = new StringBuffer(utflen);
      else
        sb.setLength(utflen);

      int c, c2, c3;
      int incount = 0;
      int outcount = 0;

      readFully(bytearr, 0, utflen);

      while (incount < utflen) {
        // Fast path for all 7 bit ASCII chars
        c = bytearr[incount] & 0xFF;
        if (c > 127) break;
        incount++;
        sb.setCharAt(outcount++, (char)c);
      }

      int x;
      while (incount < utflen) {
        c = bytearr[incount] & 0xFF;
        if (c < 128) {
          incount++;
          sb.setCharAt(outcount++, (char)c);
          continue;
        }
        // Look at the top four bits only, since only they can affect this
        x = c >> 4;
        if (x == 12 || x == 13) {
          // 110xxxxx 10xxxxxx - 2 bytes for this char
          incount += 2;
          if (incount > utflen)
            throw new java.io.UTFDataFormatException("bad UTF data: missing second byte of 2 byte char at " + incount);
          c2 = bytearr[incount - 1];
          // Verify next byte starts with 10xxxxxx
          if ((c2 & 0xC0) != 0x80)
            throw new java.io.UTFDataFormatException("bad UTF data: second byte format after 110xxxx is wrong char: 0x" +
                Integer.toString((int)c2, 16) + " count: " + incount);
          sb.setCharAt(outcount++, (char)(((c & 0x1F) << 6) | (c2 & 0x3F)));
        }
        else if (x == 14)
        {
          // 1110xxxx 10xxxxxx 10xxxx - 3 bytes for this char
          incount += 3;
          if (incount > utflen)
            throw new java.io.UTFDataFormatException("bad UTF data: missing extra bytes of 3 byte char at " + incount);
          c2 = bytearr[incount - 2];
          c3 = bytearr[incount - 1];
          // Verify next bytes start with 10xxxxxx
          if (((c2 & 0xC0) != 0x80) || ((c3 & 0xC0) != 0x80))
            throw new java.io.UTFDataFormatException("bad UTF data: extra byte format after 1110xxx is wrong char2: 0x" +
                Integer.toString((int)c2, 16) + " char3: " + Integer.toString((int)c3, 16) + " count: " + incount);
          sb.setCharAt(outcount++, (char)(((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F)));
        }
        else
        {
          // No need to support beyond this, as we only have 16 bit chars in Java
          throw new java.io.UTFDataFormatException("bad UTF data: we don't support more than 16 bit chars char: " +
              Integer.toString((int)c, 16) + " count:" + incount);
        }
      }
      sb.setLength(outcount);
      return sb;
    }
    else
    {
      // NOTE: THIS IS NOT OPTIMAL; BUT WE DON'T USE THIS CODE PATH
      int len = readShort();
      if (len > 0)
      {
        byte[] bytes = new byte[len];
        readFully(bytes, 0, len);
        return new StringBuffer(new String(bytes, myCharset));
      }
      else return new StringBuffer("");
    }
  }
  public void writeUTF(String s) throws java.io.IOException
  {
    // Just in case a null gets in here; it's much better to write it out as an empty string than to simply crash with an exception
    if (s == null) s = "";
    if (isI18N)
    {
      int strlen = s.length();
      int utflen = 0;
      int c = 0;

      for (int i = 0; i < strlen; i++) {
        c = s.charAt(i);
        if (c < 128) {
          utflen++;
        } else if (c > 0x07FF) {
          utflen += 3;
        } else {
          utflen += 2;
        }
      }

      if (utflen >= 0xFFFF)
      {
        write((byte)0xFF);
        write((byte)0xFF);
        write((byte) ((utflen >>> 24) & 0xFF));
        write((byte) ((utflen >>> 16) & 0xFF));
        write((byte) ((utflen >>> 8) & 0xFF));
        write((byte) ((utflen >>> 0) & 0xFF));
      }
      else
      {
        write((byte) ((utflen >>> 8) & 0xFF));
        write((byte) ((utflen >>> 0) & 0xFF));
      }
      for (int i = 0; i < strlen; i++) {
        c = s.charAt(i);
        if (c < 128) {
          write((byte) c);
        } else if (c > 0x07FF) {
          write((byte) (0xE0 | ((c >> 12) & 0x0F)));
          write((byte) (0x80 | ((c >>  6) & 0x3F)));
          write((byte) (0x80 | (c & 0x3F)));
        } else {
          write((byte) (0xC0 | ((c >>  6) & 0x1F)));
          write((byte) (0x80 | (c & 0x3F)));
        }
      }
    }
    else
    {
      if (s.length() > Short.MAX_VALUE)
      {
        System.out.println("WARNING: String length exceeded 32k!!! Truncating...");
        writeShort(Short.MAX_VALUE);
        write(s.substring(0, Short.MAX_VALUE).getBytes(myCharset));
      }
      else
      {
        writeShort((short)s.length());
        write(s.getBytes(myCharset));
      }
    }
  }

  public void writeBoolean(boolean b)	throws java.io.IOException
  {
    if (b)
      write((byte)1);
    else
      write((byte)0);
  }

  public void writeByte(int b) throws java.io.IOException
  {
    write(b);
  }

  public final void writeBytes(String s) throws java.io.IOException
  {
    int len = s.length();
    for (int i = 0; i < len; i++)
      write((byte)s.charAt(i));
  }

  private byte writeBuffer[] = new byte[8];
  public void writeInt(int s)	throws java.io.IOException
  {
    writeBuffer[0] = (byte)((s >>> 24) & 0xFF);
    writeBuffer[1] = (byte)((s >>> 16) & 0xFF);
    writeBuffer[2] = (byte)((s >>> 8) & 0xFF);
    writeBuffer[3] = (byte)(s & 0xFF);
    write(writeBuffer, 0, 4);
  }

  public void writeLong(long s) throws java.io.IOException
  {
    writeBuffer[0] = (byte)((s >>> 56) & 0xFF);
    writeBuffer[1] = (byte)((s >>> 48) & 0xFF);
    writeBuffer[2] = (byte)((s >>> 40) & 0xFF);
    writeBuffer[3] = (byte)((s >>> 32) & 0xFF);
    writeBuffer[4] = (byte)((s >>> 24) & 0xFF);
    writeBuffer[5] = (byte)((s >>> 16) & 0xFF);
    writeBuffer[6] = (byte)((s >>> 8) & 0xFF);
    writeBuffer[7] = (byte)(s & 0xFF);
    write(writeBuffer, 0, 8);
  }

  public long length() throws java.io.IOException
  {
    flush();
    return raf.length();
  }

  public final void writeFloat(float v) throws java.io.IOException
  {
    writeInt(Float.floatToIntBits(v));
  }

  public final void writeDouble(double v) throws java.io.IOException
  {
    writeLong(Double.doubleToLongBits(v));
  }

  public final float readFloat() throws java.io.IOException
  {
    return Float.intBitsToFloat(readInt());
  }

  public final double readDouble() throws java.io.IOException
  {
    return Double.longBitsToDouble(readLong());
  }

  public void setLength(long len) throws java.io.IOException
  {
    flush();
    fp = Math.min(fp, len);
    raf.setLength(len);
  }

  public short readShort() throws java.io.IOException
  {
    int b1 = this.read();
    int b2 = this.read();
    if ((b1 | b2) < 0)
      throw new java.io.EOFException();
    return (short)((b1 << 8) + b2);
  }

  public final void writeShort(int v) throws java.io.IOException
  {
    write((byte)((v >>> 8) & 0xFF));
    write((byte)(v & 0xFF));
  }

  public final void writeChar(int v) throws java.io.IOException
  {
    writeShort(v);
  }

  public final void writeChars(String s) throws java.io.IOException
  {
    int len = s.length();
    for (int i = 0 ; i < len ; i++)
    {
      int v = s.charAt(i);
      write((byte)((v >>> 8) & 0xFF));
      write((byte)(v & 0xFF));
    }
  }

  public void readFully(byte b[], int off, int len) throws java.io.IOException
  {
    raf.readFully(b, off, len);
    if (crypto)
    {
      for (int i = 0; i < len; i++)
      {
        int x = b[i] ^ (cryptoKeys[((int)(fp++)) % 128]);
        b[i] = (byte)(((x & 0x0F) << 4) | ((x & 0xF0) >> 4));
      }
    }
    else
      fp += len;
  }

  public char readChar() throws java.io.IOException
  {
    int b1 = this.read();
    int b2 = this.read();
    if ((b1 | b2) < 0)
      throw new java.io.EOFException();
    return (char)((b1 << 8) + b2);
  }

  public void readFully(byte[] b) throws java.io.IOException
  {
    readFully(b, 0, b.length);
  }

  public String readLine() throws java.io.IOException
  {
    throw new UnsupportedOperationException();
  }

  public int readUnsignedByte() throws java.io.IOException
  {
    int b = this.read();
    if (b < 0)
      throw new java.io.EOFException();
    return b;
  }

  public int readUnsignedShort() throws java.io.IOException
  {
    int b1 = this.read();
    int b2 = this.read();
    if ((b1 | b2) < 0)
      throw new java.io.EOFException();
    return (b1 << 8) + b2;
  }

  public int skipBytes(int n) throws java.io.IOException
  {
    seek(getFilePointer() + n);
    return n;
  }

  public void setCharset(String x){ myCharset = x; isI18N = Sage.I18N_CHARSET.equals(myCharset); }

  public void setCircularSize(long x) { circularFileSize = x; }

  protected String myCharset;
  protected boolean isI18N;
  protected boolean crypto;
  protected byte[] cryptoKeys;
  protected long circularFileSize;
}
