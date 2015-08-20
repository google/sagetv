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

public interface FileExportPlugin
{
  // Taken from sage.MediaFile
  public static final byte ACQUISITION_MANUAL = 91;
  public static final byte ACQUISITION_FAVORITE = 92;
  public static final byte ACQUISITION_INTELLIGENT = 93;
  public static final byte ACQUISITION_WATCH_BUFFER = 96;

  public boolean openPlugin();
  public void closePlugin();
  public void filesDoneRecording(java.io.File[] f, byte acquisitionType);
}
