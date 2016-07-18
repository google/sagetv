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

import java.util.Random;

public class TestData
{
  // This class is here to keep testing times down by not forcing us to initialize 1.5MB of byte
  // arrays for almost every single test.

  // We don't need to write massive files to uncover bugs. The files just need to be at least 3
  // times the size of the read or write buffers.
  public final static byte[] readContents = new byte[524288];
  public final static byte[] writeContents = new byte[524288];
  public final static byte[] cryptKey = new byte[128];
  // To verify the contents of the test files.
  public final static byte[] verifyBuffer = new byte[524288];

  static
  {
    // Define the seed for the tests so we are consistent.
    Random random = new Random('S' + 'A' + 'G' + 'E');
    random.nextBytes(readContents);
    random.nextBytes(writeContents);
    random.nextBytes(cryptKey);
  }
}
