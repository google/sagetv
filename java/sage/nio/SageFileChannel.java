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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

public interface SageFileChannel extends SeekableByteChannel, GatheringByteChannel, ScatteringByteChannel
{
  /**
   * Open a new file with this channel and seek to position 0.
   * <p/>
   * This is an attempt to be more efficient with remote connections.
   *
   * @param name The full path and name of the new file to open.
   */
  public void openFile(String name) throws IOException;

  /**
   * Open a new file with this channel and seek to position 0.
   * <p/>
   * This is an attempt to be more efficient with remote connections.
   *
   * @param file The new file to open.
   */
  public void openFile(File file) throws IOException;

  /**
   * Read a single unsigned byte from the file channel.
   * <p/>
   * This is a very inefficient way to access data without a buffer in place. It is preferred to use
   * get() & 0xFF on a byte buffer after a larger read.
   *
   * @return An unsigned byte.
   * @throws IOException If there is an I/O related error.
   */
  public int readUnsignedByte() throws IOException;

  /**
   * Transfers bytes into this channel file from the given readable byte channel.
   *
   * @param src The byte channel to read data from.
   * @param position The position to start the data transfer.
   * @param count The number of bytes to write.
   * @return The number of bytes actually transferred.
   * @throws IOException If there is an I/O related error.
   */
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException;

  /**
   * Transfers bytes into this channel file from the given readable byte channel.
   * <p/>
   * The transfer will be relative to the current position and will increment the current position.
   *
   * @param src The byte channel to read data from.
   * @param count The number of bytes to write.
   * @return The number of bytes actually transferred.
   * @throws IOException If there is an I/O related error.
   */
  public long transferFrom(ReadableByteChannel src, long count) throws IOException;

  /**
   * Transfers bytes from this channel file to the given writable byte channel.
   *
   * @param position The position to start the data transfer.
   * @param count The number of bytes to read.
   * @param target The byte channel to write data into.
   * @return The number of bytes actually transferred.
   * @throws IOException If there is an I/O related error.
   */
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException;

  /**
   * Transfers bytes from this channel file to the given writable byte channel.
   * <p/>
   * The transfer will be relative to the current position and will increment the current position.
   *
   * @param count The number of bytes to read.
   * @param target The byte channel to write data into.
   * @return The number of bytes actually transferred.
   * @throws IOException If there is an I/O related error.
   */
  public long transferTo(long count, WritableByteChannel target) throws IOException;

  /**
   * Get the current position of this channel.
   * <p/>
   * This should be a locally cached variable if possible for sources and cannot throw an exception.
   *
   * @return The current position of this channel.
   */
  public long position();

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
   * Write from a byte buffer to a random location in a file.
   *
   * @param src This is the source to write from.
   * @param position The is the position in the file to start writing.
   * @return The number of bytes actually written.
   * @throws IOException If there is an I/O related error.
   */
  public int write(ByteBuffer src, long position) throws IOException;

  /**
   * Read into a byte buffer to a random location in a file.
   *
   * @param dst This is the destination to read into.
   * @param position The is the position in the file to start writing.
   * @return The number of bytes actually written.
   * @throws IOException If there is an I/O related error.
   */
  public int read(ByteBuffer dst, long position) throws IOException;

  /**
   * Changes the current size of the file.
   * <p/>
   * If the current file position is greater than the new size, the file position will be changed
   * to equal the new file size. If the new file size is greater than the current file size, the
   * file will grow to the new size.
   *
   * @param size The new file size.
   * @throws IOException If there is an I/O related error, the file was opened as read-only or this
   *                     implementation does not support writing.
   */
  public FileChannel truncate(long size) throws IOException;

  /**
   * Force sync to disk.
   *
   * @param metaData <code>true</code> commits all pending writes and updates metadata.
   * @throws IOException If there is an I/O related error.
   */
  public void force(boolean metaData) throws IOException;

  /**
   * Is this file actively growing?
   *
   * @return <code>true</code> if the file is actively growing.
   */
  public boolean isActiveFile();

  /**
   * Is this file opened as read only?
   *
   * @return <code>true</code> if the file is read only.
   */
  public boolean isReadOnly();

  /**
   * Execute a command specific to this implementation.
   *
   * @param command The command to execute.
   * @return The result from the command. This could be null. The response is implementation
   *         specific.
   * @throws IOException If there is an I/O related error.
   */
  public String executeCommand(String command) throws IOException;

  /**
   * Execute a byte encoded command specific to this implementation.
   *
   * @param command A <code>ByteBuffer</code> already in "read" mode.
   * @return The results from the command. This could be null. The response is implementation
   *         specific.
   * @throws IOException If there is an I/O related error.
   */
  public String executeCommand(ByteBuffer command) throws IOException;
}
