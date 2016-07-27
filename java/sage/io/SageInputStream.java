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

import java.io.IOException;
import java.io.InputStream;

public class SageInputStream extends InputStream
{
  private final SageFileSource localRandomFile;
  private long markOffset = -1;
  private long lastOffset = -1;

  public SageInputStream(SageFileSource localRandomFile)
  {
    this.localRandomFile = localRandomFile;
  }

  @Override
  public int read() throws IOException
  {
    return localRandomFile.read();
  }

  @Override
  public int read(byte[] b) throws IOException
  {
    return localRandomFile.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException
  {
    return localRandomFile.read(b, off, len);
  }

  @Override
  public long skip(long n) throws IOException
  {
    return localRandomFile.skip(n);
  }

  @Override
  public int available() throws IOException
  {
    return Math.max(0, (int)Math.min(localRandomFile.length() - localRandomFile.position(), Integer.MAX_VALUE));
  }

  @Override
  public void close() throws IOException
  {
    localRandomFile.close();
  }

  @Override
  public void mark(int readlimit)
  {
    markOffset = localRandomFile.position();

    // Handle if somehow we just said that mark is supported, but somehow we get an exception the
    // second time the file position is checked. This shouldn't be happening, and when we go to
    // reset, we would get an unpleasant surprise when it tells us that the mark was not set, yet it
    // appeared to be successfully set here. We can't throw an exception since the contract doesn't
    // allow for it and it really doesn't make sense that an exception would be thrown when you are
    // just saving what amounts to a 64-bit number.
    if (markOffset == -1)
      markOffset = lastOffset;
  }

  @Override
  public synchronized void reset() throws IOException
  {
    if (markOffset >=0 )
    {
      localRandomFile.seek(markOffset);
      markOffset = -1;
      return;
    }

    throw new IOException("Mark not set!");
  }

  @Override
  public boolean markSupported()
  {
    // Since RandomAccessFile can throw an exception when getting the file position and this method
    // should always be checked before using mark, we base mark support on if the current position
    // can be returned. We also cache the last offset in case when the mark is set, suddenly there's
    // an I/O exception.
    lastOffset = localRandomFile.position();
    return lastOffset != -1;
  }
}
