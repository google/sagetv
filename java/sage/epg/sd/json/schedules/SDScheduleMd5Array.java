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

package sage.epg.sd.json.schedules;

public class SDScheduleMd5Array
{
  protected static final SDScheduleMd5[] EMPTY_MD5_SCHEDULES = new SDScheduleMd5[0];

  private SDScheduleMd5[] md5s;

  protected SDScheduleMd5Array(SDScheduleMd5 md5s[])
  {
    this.md5s = md5s != null ? md5s : EMPTY_MD5_SCHEDULES;
  }

  public SDScheduleMd5[] getMd5s()
  {
    return md5s;
  }
}
