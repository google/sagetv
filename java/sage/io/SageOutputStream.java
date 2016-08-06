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
import java.io.OutputStream;

public class SageOutputStream extends OutputStream
{
  private final SageFileSource sageFileSource;

  public SageOutputStream(SageFileSource sageFileSource) throws IOException
  {
    if (sageFileSource.isReadOnly())
      throw new IOException("Source is read only.");

    this.sageFileSource = sageFileSource;
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
  public void flush() throws IOException
  {
    sageFileSource.flush();
  }

  @Override
  public void close() throws IOException
  {
    sageFileSource.close();
  }
}
