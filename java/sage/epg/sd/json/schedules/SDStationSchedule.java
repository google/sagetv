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

import sage.epg.sd.SDUtils;
import sage.epg.sd.json.SDError;

public class SDStationSchedule implements SDError
{
  private static final SDProgramSchedule[] EMPTY_PROGRAM_SCHEDULE = new SDProgramSchedule[0];

  private int code;
  private String message;
  private String retryTime;

  private String stationID;
  private SDProgramSchedule programs[];
  private SDScheduleMetadata metadata;

  @Override
  public int getCode()
  {
    return code;
  }

  @Override
  public String getMessage()
  {
    return message;
  }

  /**
   * If this is true, there isn't a lineup to process and likely will never be a lineup with this ID
   * to process.
   */
  public boolean isStationDeleted()
  {
    return code == 2201;
  }

  /**
   * If this does not return <code>null</code>, you need to wait at least until the time returned by
   * this method.
   */
  public String retryTime()
  {
    if (code == 7100)
      return retryTime;

    return null;
  }

  public int getStationID()
  {
    return SDUtils.fromStationIDtoSageTV(stationID);
  }

  /**
   * an array of program data. Mandatory. Must contain at least one element.
   */
  public SDProgramSchedule[] getPrograms()
  {
    if (programs == null)
      return EMPTY_PROGRAM_SCHEDULE;

    return programs;
  }

  public SDScheduleMetadata getMetadata()
  {
    return metadata;
  }
}
