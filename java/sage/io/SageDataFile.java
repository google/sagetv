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

import sage.Sage;

import java.io.*;

public class SageDataFile implements SageFileSource, DataInput, DataOutput
{
  private final SageFileSource sageFileSource;
  private final SageFileSource unencryptedFileSource;
  private String charset;
  private boolean isI18N;

  /**
   * Create a new <code>SageDataFile</code> instance.
   * <p/>
   * This provides a lot of convenience methods for reading byte values into primary numeric types
   * and character data.
   *
   * @param sageFileSource The <code>SageFileSource</code> instance to use for reading and writing.
   * @param charset The charset to be used for reading character data.
   */
  public SageDataFile(SageFileSource sageFileSource, String charset)
  {
    this.sageFileSource = sageFileSource;

    // If there is an encryption layer before a buffering layer, we can provide a way to read the
    // data without it being decrypted and write data without it being encrypted.
    SageFileSource newSource = getUnencryptedRandomFile(sageFileSource);
    if (newSource != null)
      unencryptedFileSource = newSource;
    else
      unencryptedFileSource = sageFileSource;

    this.charset = charset;
    isI18N = Sage.I18N_CHARSET.equals(charset);
  }

  public void setCharset(String x){
    charset = x;
    isI18N = Sage.I18N_CHARSET.equals(charset);
  }

  /**
   * Skip a number of bytes.
   * <p/>
   * This will return the number of bytes actually skipped and will not go past the end of the file.
   * <p/>
   * If you are not using the DataInput implementation, <code>skip()</code> is preferred since it
   * uses and returns a <code>long</code> parameter.
   *
   * @param n The number of bytes to skip.
   * @return The number of bytes skipped. This can be 0.
   * @throws IOException If there is an I/O related error.
   */
  @Override
  public int skipBytes(int n) throws IOException
  {
    // An int is going in, so nothing greater than an int should be coming out, but just in case.
    return (int)Math.min(Integer.MAX_VALUE, sageFileSource.skip(n));
  }

  @Override
  public boolean readBoolean() throws IOException
  {
    int ch = read();
    if (ch < 0)
      throw new java.io.EOFException();
    return (ch != 0);
  }

  @Override
  public byte readByte() throws IOException
  {
    int ch = read();
    if (ch < 0)
      throw new java.io.EOFException();
    return (byte)(ch);
  }

  public byte readUnencryptedByte() throws java.io.IOException
  {
    int ch = unencryptedFileSource.read();
    if (ch < 0)
      throw new java.io.EOFException();
    return (byte)(ch);
  }

  @Override
  public int readUnsignedByte() throws IOException
  {
    int b = read() & 0xff;
    if (b < 0)
      throw new java.io.EOFException();
    return b;
  }

  @Override
  public short readShort() throws IOException
  {
    int b1 = read();
    int b2 = read();
    if ((b1 | b2) < 0)
      throw new java.io.EOFException();
    return (short)((b1 << 8) | b2);
  }

  @Override
  public int readUnsignedShort() throws IOException
  {
    int b1 = read() & 0xff;
    int b2 = read() & 0xff;
    if ((b1 | b2) < 0)
      throw new java.io.EOFException();
    return (b1 << 8) | b2;
  }

  @Override
  public char readChar() throws IOException
  {
    int b1 = this.read();
    int b2 = this.read();
    if ((b1 | b2) < 0)
      throw new java.io.EOFException();
    return (char)((b1 << 8) + b2);
  }

  @Override
  public int readInt() throws IOException
  {
    int b1 = this.read();
    int b2 = this.read();
    int b3 = this.read();
    int b4 = this.read();
    if ((b1 | b2 | b3 | b4) < 0)
      throw new java.io.EOFException();
    return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
  }

  @Override
  public long readLong() throws IOException
  {
    int b1 = this.read();
    int b2 = this.read();
    int b3 = this.read();
    int b4 = this.read();
    int b5 = this.read();
    int b6 = this.read();
    int b7 = this.read();
    int b8 = this.read();
    if ((b1 | b2 | b3 | b4 | b5 | b6 | b7 | b8) < 0)
      throw new java.io.EOFException();

    return ((long)b1 << 56L) | ((long)b2 << 48L) | ((long)b3 << 40L) | ((long)b4 << 32L) | ((long)b5 << 24L) | ((long)b6 << 16L) | ((long)b7 << 8L) | (long)b8;
  }

  @Override
  public float readFloat() throws IOException
  {
    return Float.intBitsToFloat(readInt());
  }

  @Override
  public double readDouble() throws IOException
  {
    return Double.longBitsToDouble(readLong());
  }

  @Override
  public String readLine() throws IOException
  {
    // Use an InputStream instead.
    throw new UnsupportedOperationException();
  }

  // For optimizing UTF reads
  private byte[] bytearr = null;
  private char[] chararr = null;
  // NOTE: We have a special case here where we want to handle strings that are larger than 64k.
  // do that by writing out 0xFFFF for the length which indicates that the next 4 bytes will have
  // the actual string length; then we go from there.
  @Override
  public String readUTF() throws IOException
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
        return new String(bytes, charset);
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
        return new StringBuffer(new String(bytes, charset));
      }
      else return new StringBuffer("");
    }
  }

  @Override
  public void writeBoolean(boolean v) throws IOException
  {
    if (v)
      write((byte)1);
    else
      write((byte)0);
  }

  @Override
  public void writeByte(int v) throws IOException
  {
    write(v);
  }

  public void writeUnencryptedByte(byte b) throws java.io.IOException
  {
    unencryptedFileSource.write(b);
  }

  @Override
  public void writeShort(int v) throws IOException
  {
    write((byte)((v >>> 8) & 0xFF));
    write((byte)(v & 0xFF));
  }

  @Override
  public void writeChar(int v) throws IOException
  {
    writeShort(v);
  }

  // Lazy initialize. We don't always need this buffer.
  private byte writeBuffer[];
  @Override
  public void writeInt(int v) throws IOException
  {
    if (writeBuffer == null)
      writeBuffer = new byte[8];

    writeBuffer[0] = (byte)((v >>> 24) & 0xFF);
    writeBuffer[1] = (byte)((v >>> 16) & 0xFF);
    writeBuffer[2] = (byte)((v >>> 8) & 0xFF);
    writeBuffer[3] = (byte)((v >>> 0) & 0xFF);
    write(writeBuffer, 0, 4);
  }

  public void writeIntAtOffset(long offset, int s) throws java.io.IOException
  {
    if (writeBuffer == null)
      writeBuffer = new byte[8];

    writeBuffer[0] = (byte)((s >>> 24) & 0xFF);
    writeBuffer[1] = (byte)((s >>> 16) & 0xFF);
    writeBuffer[2] = (byte)((s >>> 8) & 0xFF);
    writeBuffer[3] = (byte)((s >>> 0) & 0xFF);

    sageFileSource.randomWrite(offset, writeBuffer, 0, 4);
  }

  @Override
  public void writeLong(long v) throws IOException
  {
    if (writeBuffer == null)
      writeBuffer = new byte[8];

    writeBuffer[0] = (byte)((v >>> 56) & 0xFF);
    writeBuffer[1] = (byte)((v >>> 48) & 0xFF);
    writeBuffer[2] = (byte)((v >>> 40) & 0xFF);
    writeBuffer[3] = (byte)((v >>> 32) & 0xFF);
    writeBuffer[4] = (byte)((v >>> 24) & 0xFF);
    writeBuffer[5] = (byte)((v >>> 16) & 0xFF);
    writeBuffer[6] = (byte)((v >>> 8) & 0xFF);
    writeBuffer[7] = (byte)((v >>> 0) & 0xFF);
    write(writeBuffer, 0, 8);
  }

  @Override
  public void writeFloat(float v) throws IOException
  {
    writeInt(Float.floatToIntBits(v));
  }

  @Override
  public void writeDouble(double v) throws IOException
  {
    writeLong(Double.doubleToLongBits(v));
  }

  @Override
  public void writeBytes(String s) throws IOException
  {
    int len = s.length();
    for (int i = 0; i < len; i++)
      write((byte)s.charAt(i));
  }

  @Override
  public void writeChars(String s) throws IOException
  {
    int len = s.length();
    for (int i = 0 ; i < len ; i++)
    {
      int v = s.charAt(i);
      write((byte)((v >>> 8) & 0xFF));
      write((byte)(v & 0xFF));
    }
  }

  @Override
  public void writeUTF(String s) throws IOException
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
        write((byte) (utflen & 0xFF));
      }
      else
      {
        write((byte) ((utflen >>> 8) & 0xFF));
        write((byte) (utflen & 0xFF));
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
        write(s.substring(0, Short.MAX_VALUE).getBytes(charset));
      }
      else
      {
        writeShort((short)s.length());
        write(s.getBytes(charset));
      }
    }
  }

  @Override
  public int read() throws IOException
  {
    return sageFileSource.read();
  }

  @Override
  public int read(byte[] b) throws IOException
  {
    return sageFileSource.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException
  {
    return sageFileSource.read(b, off, len);
  }

  @Override
  public void readFully(byte[] b) throws IOException
  {
    sageFileSource.readFully(b);
  }

  @Override
  public void readFully(byte[] b, int off, int len) throws IOException
  {
    sageFileSource.readFully(b, off, len);
  }

  @Override
  public void write(int b) throws IOException
  {
    sageFileSource.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException
  {
    sageFileSource.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException
  {
    sageFileSource.write(b, off, len);
  }

  @Override
  public void randomWrite(long pos, byte[] b, int off, int len) throws IOException
  {
    sageFileSource.randomWrite(pos, b, off, len);
  }

  @Override
  public long skip(long n) throws IOException
  {
    return sageFileSource.skip(n);
  }

  @Override
  public void seek(long pos) throws IOException
  {
    sageFileSource.seek(pos);
  }

  @Override
  public long position()
  {
    return sageFileSource.position();
  }

  @Override
  public long length() throws IOException
  {
    return sageFileSource.length();
  }

  @Override
  public long available() throws IOException
  {
    return sageFileSource.available();
  }

  @Override
  public void setLength(long newLength) throws IOException
  {
    sageFileSource.setLength(newLength);
  }

  @Override
  public void sync() throws IOException
  {
    sageFileSource.sync();
  }

  @Override
  public void flush() throws IOException
  {
    sageFileSource.flush();
  }

  @Override
  public void close() throws IOException
  {
    sageFileSource.close();
  }

  @Override
  public boolean isReadOnly()
  {
    return sageFileSource.isReadOnly();
  }

  public boolean isEncrypted()
  {
    // If these two are the same object, no encryption is in use.
    return sageFileSource != unencryptedFileSource;
  }

  public InputStream getUnencryptedInputStream()
  {
    return new SageInputStream(unencryptedFileSource);
  }

  public OutputStream getUnencryptedOutputStream() throws IOException
  {
    return new SageOutputStream(unencryptedFileSource);
  }

  @Override
  public SageFileSource getSource()
  {
    return sageFileSource;
  }

  public SageFileSource getUnencryptedSource()
  {
    return unencryptedFileSource;
  }

  public static SageFileSource getUnencryptedRandomFile(SageFileSource sageFileSource)
  {
    if (sageFileSource == null)
      return null;

    // EncryptedSageFile doesn't allow wrapping another EncryptedSageFile within itself, so we can
    // stop here. If we find a reason to have more than one kind of encryption, we could always add
    // a flag to the interface or add to EncryptedSageFile initialization if that makes more sense.
    if (sageFileSource instanceof EncryptedSageFile)
      return ((EncryptedSageFile) sageFileSource).getUnencryptedRandomFileSource();

    // Stop here since if we get anything below this level, things might be inconsistent.
    if (sageFileSource instanceof BufferedSageFile)
      return null;

    return getUnencryptedRandomFile(sageFileSource.getSource());
  }

  public static SageFileSource getSourceRandomFile(SageFileSource sageFileSource)
  {
    SageFileSource newSource = sageFileSource.getSource();

    // The source will always return null because it's at the bottom level.
    if (newSource == null)
      return sageFileSource;

    return getSourceRandomFile(newSource);
  }
}
