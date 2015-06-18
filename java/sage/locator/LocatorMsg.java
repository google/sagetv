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
package sage.locator;

public class LocatorMsg
{
  public LocatorMsg() { }
  public int msgType;
  public long msgID;
  public String fromHandle;
  public String toHandle;
  public long timestamp;
  public String text;
  public MediaInfo[] media;

  public java.io.File targetFolder;
  public boolean downloadCompleted;
  public boolean downloadRequested;

  public boolean hasMedia()
  {
    return media != null && media.length > 0;
  }

  public boolean isDownloadCompleted()
  {
    return downloadCompleted;
  }

  public boolean isDownloadRequested()
  {
    return downloadRequested;
  }

  public String toString()
  {
    return "LocatorMsg[type=" + msgType + " id=" + msgID + " from=" + fromHandle + " to=" + toHandle +
        " time=" + new java.util.Date(timestamp) + " text=" + text + " media=" +
        (media == null ? "null" : java.util.Arrays.asList(media).toString()) + "]";
  }
}