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

public class SDScheduleMetadata
{
  private String modified;
  private String md5;
  private String startDate;
  private int code;

  /**
   * 20 characters. Mandatory. Follows strftime() format; "Y-m-d\TH:i:s\Z" The timestamp for the
   * creation time of this schedule on the server.
   */
  public String getModified()
  {
    return modified;
  }

  /**
   * 22 characters. Mandatory. What is the MD5 for this schedule (stationID and programs elements) -
   * metadata is not included in the MD5 calculation.
   */
  public String getMd5()
  {
    return md5;
  }

  /**
   * the start date of the JSON schedule. Mandatory. "YYYY-mm-dd" format.
   */
  public String getStartDate()
  {
    return startDate;
  }

  /**
   * optional. Indicates if there were errors with the station. It is possible that your client has
   * requested a stationID which has been deleted upstream, but that your client still believes is
   * "live" because it hasn't updated the lineup.
   */
  public int getCode()
  {
    return code;
  }
}
