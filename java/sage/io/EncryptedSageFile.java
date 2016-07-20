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

import sage.IOUtils;

import java.io.IOException;

// If you want to add more encryption implementations, be sure to take a look at SageDataFile
// initialization as it depends on this class name to determine when a source is encrypted.
public class EncryptedSageFile implements SageFileSource
{
  private final byte[] cryptoKeys;
  private final SageFileSource sageFileSource;
  // This is used and increased in size when required to encrypt bytes so we are not encrypting the
  // bytes in the original byte array.
  private byte writeBuffer[];

  /**
   * Create a new encryption filter over a <code>SageFileSource</code>
   * <p/>
   * This will seamlessly encrypt and decrypt reads and writes done through this class.
   * <p/>
   * The default key will be used.
   *
   * @param sageFileSource The <code>SageFileSource</code> to wrap inside this filter.
   * @throws IOException If there is an I/O related error or the <code>SageFileSource</code> is
   *                     another instance of <code>EncryptedSageFile</code>.
   */
  public EncryptedSageFile(SageFileSource sageFileSource) throws IOException
  {
    this(sageFileSource, IOUtils.getCryptoKeys());
  }

  /**
   * Create a new encryption filter over a <code>SageFileSource</code>
   * <p/>
   * This will seamlessly encrypt and decrypt reads and writes done through this class.
   *
   * @param sageFileSource The <code>SageFileSource</code> to wrap inside this filter.
   * @param crypt Specify alternative 128 byte key.
   * @throws IOException If there is an I/O related error or the <code>SageFileSource</code> is
   *                     another instance of <code>EncryptedSageFile</code>.
   */
  public EncryptedSageFile(SageFileSource sageFileSource, byte crypt[]) throws IOException
  {
    // In case we try to make a file that's already using this "filter" use it twice in a row.
    if (sageFileSource instanceof EncryptedSageFile)
      throw new IOException("Cannot layer the same encryption on top of itself.");

    this.sageFileSource = sageFileSource;
    cryptoKeys = crypt;

    System.out.println("Opening encrypted IO: crypt.length=" + crypt.length);
  }

  public SageFileSource getUnencryptedRandomFileSource()
  {
    return sageFileSource;
  }

  @Override
  public int read() throws IOException
  {
    long filePosition = sageFileSource.position();
    int b = sageFileSource.read();

    if (b == -1)
      return -1;

    b = decrypt(b & 0xFF, filePosition);
    return b;
  }

  @Override
  public int read(byte[] b) throws IOException
  {
    return read(b, 0, b.length);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException
  {
    long filePosition = sageFileSource.position();
    int returnValue = sageFileSource.read(b, off, len);
    decrypt(b, off, returnValue, filePosition);
    return returnValue;
  }

  @Override
  public void readFully(byte[] b) throws IOException
  {
    readFully(b, 0, b.length);
  }

  @Override
  public void readFully(byte[] b, int off, int len) throws IOException
  {
    long filePosition = sageFileSource.position();
    sageFileSource.readFully(b, off, len);
    decrypt(b, off, len, filePosition);
  }

  @Override
  public void write(int b) throws IOException
  {
    long filePosition = sageFileSource.position();
    b = encrypt(b, filePosition);
    sageFileSource.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException
  {
    write(b, 0, b.length);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException
  {
    if (len == 0)
      return;

    // If we don't have a write buffer this is over 10x faster.
    sageFileSource.write(encryptWrite(b, off, len, sageFileSource.position()), off, len);
  }

  @Override
  public void randomWrite(long pos, byte[] b, int off, int len) throws IOException
  {
    if (len == 0)
      return;

    sageFileSource.randomWrite(pos, encryptWrite(b, off, len, pos), off, len);
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

  /**
   * Decrypt a single byte.
   *
   * @param b The byte to decrypt.
   * @param fp The file position where this byte is located.
   * @return The decrypted byte.
   */
  protected int decrypt(int b, long fp)
  {
    b = b ^ (cryptoKeys[((int)(fp)) % 128]);
    b = (((b & 0x0F) << 4) | ((b & 0xF0) >> 4));
    return b;
  }

  /**
   * Decrypt a range within a byte array.
   *
   * @param b The byte array.
   * @param off The offset within the byte array to start to encrypt.
   * @param len The number of bytes from the offset to decrypt.
   * @param fp The file position where the byte at the offset is located.
   */
  protected void decrypt(byte[] b, int off, int len, long fp)
  {
    for (int i = off; i < off + len; i++)
    {
      b[i] = (byte)decrypt(b[i], fp++);
    }
  }

  /**
   * Encrypt a single byte.
   *
   * @param b The byte to encrypt.
   * @param fp The file position where this byte will be located.
   * @return The encoder byte.
   */
  protected int encrypt(int b, long fp)
  {
    return  (byte) ((((b & 0x0F) << 4) | ((b & 0xF0) >> 4)) ^ (cryptoKeys[((int)fp) % 128]));
  }

  /**
   * Encrypt a range within a byte array.
   *
   * @param b The byte array.
   * @param off The offset within the byte array to start to encrypt.
   * @param len The number of bytes from the offset to encrypt.
   * @param fp The file position where the byte at the offset will be located.
   */
  protected void encrypt(byte[] b, int off, int len, long fp)
  {
    for (int i = off; i < off + len; i++)
    {
      b[i] = (byte)encrypt(b[i], fp++);
    }
  }

  /**
   * Encrypt a byte array into a new byte array.
   * <p/>
   * The byte array used for encryption is shared and becomes invalid as soon as you call this
   * method a second time. You must write or copy the returned byte array from this method
   * immediately and release any references after the write or copy.
   * <p/>
   * The returned array may be larger than the array provided for encryption, but the offset and
   * length are preserved within the array.
   *
   * @param b The byte array to be encrypted.
   * @param off The offset of the data to be encrypted.
   * @param len The number of bytes from the offset to encrypt.
   * @param fp The position of these bytes relative to the start of the file.
   * @return A shared byte array containing the now encrypted bytes at the same offset as the
   *         provided array.
   */
  protected synchronized byte[] encryptWrite(byte[] b, int off, int len, long fp)
  {
    // Normally you would create a new buffer double the newly required size, but we are trying to
    // keep this buffer as small. We use the incoming array length since it's likely we are using
    // the same array between writes and that will keep us from growing as often.
    if (writeBuffer == null || writeBuffer.length < b.length)
      writeBuffer = new byte[b.length];

    // We need to make a copy of the bytes we are about to encrypt or we will end up encrypting the
    // byte array that was provided which would make data change unexpectedly causing issues
    // somewhere else in the program. Don't shift the offset to 0 in the writeBuffer since the
    // returned array should be at most larger than the incoming array, but the data will be in the
    // same offset across the same length.
    System.arraycopy(b, off, writeBuffer, off, len);
    encrypt(writeBuffer, off, len, fp);

    return writeBuffer;
  }

  @Override
  public SageFileSource getSource()
  {
    return sageFileSource;
  }
}
