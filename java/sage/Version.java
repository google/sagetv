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

/**
 *
 * @author Narflex
 */
public class Version
{
  public static final byte MAJOR_VERSION = 9;
  public static final byte MINOR_VERSION = 1;
  public static final byte MICRO_VERSION = 7;

  public static final String VERSION = MAJOR_VERSION + "." + MINOR_VERSION + "." + MICRO_VERSION + "." + SageConstants.BUILD_VERSION;

  /** Creates a new instance of Version */
  private Version()
  {
  }

}
