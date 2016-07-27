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

/**
 * This interface is designed so that it can be used as the data source (file) and/or a filter
 * that accepts this same interface as an initialization parameter. Generally unless you want one
 * piece of data very quickly and will not be coming back to the data source, you will want to
 * wrap the data source in a <code>BufferedSageFile</code> which should help overall read and
 * write performance. If you need to read and write encrypted data, then you would add the
 * <code>EncryptedSageFile</code> over the <code>BufferedSageFile</code>. If you need more
 * convenience methods for reading and writing, then you would add <code>SageDataFile</code> over
 * top of <code>BufferedSageFile</code> or <code>EncryptedSageFile</code>.
 * <p/>
 * The layering looks like this:
 * <p/>
 * <code>LocalSageFile</code> or <code>RemoteSageFile</code> (implements <code>SageFileSource</code>)
 * <p/>
 * <code>BufferedSageFile</code> (recommended) (implements <code>SageFileSource</code>)
 * <p/>
 * <code>EncryptedSageFile</code> (optional) (implements <code>SageFileSource</code>)
 * <p/>
 * <code>SageDataFile</code> (optional) (implements <code>DataInput</code>, <code>DataOutput</code>)
 */
public interface SageFileSource
{
  /**
   * Read one byte.
   *
   * @return A byte as an integer or -1 if the end of the file has been reached.
   * @throws IOException If there is an I/O related error.
   */
  public int read() throws IOException;

  /**
   * Read into an entire provided byte array.
   *
   * @param b The buffer to return the read data in.
   * @return The number of bytes actually read into the byte array or -1 if we have reached the end
   *         of the file.
   * @throws IOException If there is an I/O related error.
   */
  public int read(byte b[]) throws IOException;

  /**
   * Read into a provided byte array.
   *
   * @param b The buffer to return the read data in.
   * @param off The offset to start reading into the provided byte array.
   * @param len The number of bytes to try to return in the array.
   * @return The number of bytes actually read into the byte array or -1 if we have reached the end
   *         of the file.
   * @throws IOException If there is an I/O related error.
   */
  public int read(byte b[], int off, int len) throws IOException;

  /**
   * Read into an entire provided byte array.
   * <p/>
   * The entire byte array must be filled or an exception will be thrown. If an exception is thrown,
   * the entire array may contain invalid data and should not be used.
   *
   * @param b The buffer to return the read data in.
   * @throws IOException If there is an I/O related error.
   * @throws EOFException If the end of the file has been reached.
   */
  public void readFully(byte[] b) throws IOException;

  /**
   * Read into a provided byte array.
   * <p/>
   * The entire length requested must be filled in the return byte array or an exception will be
   * thrown. If an exception is thrown, the bytes within offset + length may contain invalid data
   * and should not be used.
   *
   * @param b The buffer to return the read data in.
   * @param off The offset to start reading into the provided byte array.
   * @param len The number of bytes to try to return in the array.
   * @throws IOException If there is an I/O related error.
   * @throws EOFException If the end of the file has been reached.
   */
  public void readFully(byte[] b, int off, int len) throws IOException;

  /**
   * Write a single byte.
   *
   * @param b The byte to write.
   * @throws IOException If there is an I/O related error, the file was opened as read-only or this
   *                     implementation does not support writing.
   */
  public void write(int b) throws IOException;

  /**
   * Write entire contents of a provided byte array.
   *
   * @param b The byte array to be written from.
   * @throws IOException If there is an I/O related error, the file was opened as read-only or this
   *                     implementation does not support writing.
   */
  public void write(byte b[]) throws IOException;

  /**
   * Write contents of a provided byte array.
   *
   * @param b The byte array to be written from.
   * @param off The offset to start reading from the provided byte array.
   * @param len The number of bytes from the offset to write from the provided byte array.
   * @throws IOException If there is an I/O related error, the file was opened as read-only or this
   *                     implementation does not support writing.
   */
  public void write(byte b[], int off, int len) throws IOException;

  /**
   * Write contents of a provided byte array to a random position in the file.
   * <p/>
   * This method should return with the file position in the same place as it was before the method
   * was called. Effectively the file position will not change.
   * <p/>
   * This method mostly exists to allow buffered writes that would normally require a seek which
   * would effectively clear the write buffer to be optimized so that they might not require
   * clearing the buffer.
   *
   * @param pos The file position.
   * @param b The byte array to be written from.
   * @param off The offset to start reading from the provided byte array.
   * @param len The number of bytes from the offset to write from the provided byte array.
   * @throws IOException If there is an I/O related error, the file was opened as read-only or this
   *                     implementation does not support writing.
   */
  public void randomWrite(long pos, byte b[], int off, int len) throws IOException;

  /**
   * Skip a number of bytes.
   * <p/>
   * Values less than 0 will return 0 bytes skipped. If the value of <code>n</code> moves the
   * position in the file past the end of the file, the position will be set to the length of the
   * file.
   *
   * @param n The number of bytes to skip.
   * @return The number of bytes actually skipped.
   * @throws IOException If there is an I/O related error.
   */
  public long skip(long n) throws IOException;

  /**
   * Seek to a specific position relative to the start of the file.
   *
   * @param pos The new file position.
   * @throws IOException If there is an I/O related error or <code>pos</code> is negative.
   */
  public void seek(long pos) throws IOException;

  /**
   * Get the position from which the next byte will be read or written relative to the start of the
   * file.
   * <p/>
   * This method may return -1 if getting the position causes an I/O exception.
   *
   * @return The current position.
   */
  public long position();

  /**
   * Get the current length of the file.
   *
   * @return The current length of the file.
   * @throws IOException If there is an I/O related error.
   */
  public long length() throws IOException;

  /**
   * Get the number of bytes remaining in the file.
   *
   * @return The number of bytes remaining.
   * @throws IOException If there is an I/O related error.
   */
  public long available() throws IOException;

  /**
   * Changes the current length of the file.
   * <p/>
   * If the current file position is greater than the new length, the file position will be changed
   * to equal the new file length. If the new file length is greater than the current size of the
   * file, the file will grow to the new size.
   *
   * @param newLength The new file length.
   * @throws IOException If there is an I/O related error, the file was opened as read-only or this
   *                     implementation does not support writing.
   */
  public void setLength(long newLength) throws IOException;;

  /**
   * Sync data to disk.
   *
   * @throws IOException If there is an I/O related error.
   */
  public void sync() throws IOException;

  /**
   * Flush buffers to disk.
   * <p/>
   * This will generally have no effect unless there is a buffer layer present.
   *
   * @throws IOException If there is an I/O related error.
   */
  public void flush() throws IOException;

  /**
   * Close file.
   * <p/>
   * Ensure that any form of buffering (if any) is flushed before closing the file.
   *
   * @throws IOException If there is an I/O related error.
   */
  public void close() throws IOException;

  /**
   * Is this file opened as read only?
   *
   * @return <code>true</code> if the file is read only.
   */
  public boolean isReadOnly();

  /**
   * Get the underlying <code>SageFileSource</code>.
   * <p/>
   * If this is the actual data source, this will return <code>null</code>.
   *
   * @return The underlying data source or <code>null</code> if there isn't one.
   */
  public SageFileSource getSource();
}
