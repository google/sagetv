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
package sage.epg.sd.json.map;

import sage.epg.sd.SDUtils;

public abstract class SDChannelMap
{
  private String stationID;
  private int convertedStationID;
  private String channel;

  protected void setStationId()
  {
    convertedStationID = (stationID != null) ? SDUtils.fromStationIDtoSageTV(stationID) : convertedStationID;
    stationID = null;
  }

  public int getStationID()
  {
    return convertedStationID;
  }

  public String getChannel()
  {
    return channel;
  }

  public String getPhysicalChannel()
  {
    return null;
  }

  @Override
  public String toString()
  {
    return "SDChannelMap{" +
        "stationID='" + stationID + '\'' +
        ", channel='" + channel + '\'' +
        '}';
  }
}
