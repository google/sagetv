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

import java.nio.ByteBuffer;
import java.util.Random;

public class TestData
{
  // This class is here to keep testing times down by not forcing us to large amounts of data over
  // and over again.

  // We don't need to write massive files to uncover bugs. The files just need to be at least 3
  // times the size of the read or write buffers.
  public final static byte[] readContents = new byte[524288];
  public final static byte[] writeContents = new byte[524288];
  public final static byte[] cryptKey = new byte[128];
  // To verify the contents of the test files.
  public final static byte[] verifyBuffer = new byte[524288];

  // We are using different sized byte buffers because this should expose any buffer overflow or
  // underflow situations. We also test on heap and direct since there could be performance
  // optimizations based on the difference.

  // These should be bigger than the internal buffering.
  public static ByteBuffer heapLarge = ByteBuffer.allocate(sage.nio.BufferedFileChannel.DEFAULT_READ_SIZE * 3);
  public static ByteBuffer directLarge = ByteBuffer.allocateDirect(sage.nio.BufferedFileChannel.DEFAULT_READ_SIZE * 3);
  // These should be the same size as the internal buffering.
  public static ByteBuffer heap = ByteBuffer.allocate(sage.nio.BufferedFileChannel.DEFAULT_READ_SIZE);
  public static ByteBuffer direct = ByteBuffer.allocateDirect(sage.nio.BufferedFileChannel.DEFAULT_READ_SIZE);
  // These should be the half the size as the internal buffering.
  public static ByteBuffer heapSmall = ByteBuffer.allocate(sage.nio.BufferedFileChannel.DEFAULT_READ_SIZE / 3);
  public static ByteBuffer directSmall = ByteBuffer.allocateDirect(sage.nio.BufferedFileChannel.DEFAULT_READ_SIZE / 3);

  static
  {
    // Define the seed for the tests so we are consistent.
    Random random = new Random('S' + 'A' + 'G' + 'E');
    random.nextBytes(readContents);
    random.nextBytes(writeContents);
    random.nextBytes(cryptKey);
  }
}
