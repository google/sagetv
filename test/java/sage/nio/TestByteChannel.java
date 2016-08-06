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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

public class TestByteChannel implements ByteChannel
{
  // This is not a perfect solution, but it's enough to test this feature.
  private ByteBuffer buffer;

  public TestByteChannel(ByteBuffer buffer)
  {
    this.buffer = buffer;
  }

  public void setBuffer(ByteBuffer buffer)
  {
    this.buffer = buffer;
  }

  public ByteBuffer getBuffer()
  {
    return buffer;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException
  {
    if (buffer == null)
      return 0;

    int limit = buffer.limit();
    int length = buffer.remaining();

    if (length == 0)
      return -1;

    int dstRemains = dst.remaining();

    if (length > dstRemains)
    {
      length = dstRemains;
      try
      {
        buffer.limit(buffer.position() + dstRemains);
        while (buffer.hasRemaining())
          dst.put(buffer);
      }
      finally
      {
        buffer.limit(limit);
      }
    }
    else
    {
      while (buffer.hasRemaining())
        dst.put(buffer);
    }

    return length;
  }

  @Override
  public int write(ByteBuffer src) throws IOException
  {
    if (buffer == null)
      return 0;

    int length = src.remaining();

    while (src.hasRemaining())
      buffer.put(src);

    return length;
  }

  @Override
  public boolean isOpen()
  {
    return true;
  }

  @Override
  public void close() throws IOException
  {

  }
}
