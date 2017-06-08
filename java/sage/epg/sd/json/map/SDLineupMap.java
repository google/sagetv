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

import sage.epg.sd.json.SDError;
import sage.epg.sd.json.map.channel.SDTransport;
import sage.epg.sd.json.map.station.SDStation;

public class SDLineupMap implements SDError
{
  private static final SDChannelMap[] EMPTY_CHANNEL_MAP = new SDChannelMap[0];
  private static final SDStation[] EMPTY_STATIONS = new SDStation[0];

  // Return errors.
  private int code;
  private String message;

  private SDChannelMap map[];
  private SDStation stations[];
  private SDMapMetadata metadata;
  private SDTransport transport;

  protected SDLineupMap(SDChannelMap map[], SDStation stations[], SDMapMetadata metadata, SDTransport transport)
  {
    code = 0;
    this.map = map;
    this.stations = stations;
    this.metadata = metadata;
    this.transport = transport;
  }

  protected SDLineupMap(SDError error)
  {
    this.code = error.getCode();
    this.message = error.getMessage();
  }

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

  public SDChannelMap[] getMap()
  {
    if (map == null)
      return EMPTY_CHANNEL_MAP;

    return map;
  }

  public SDStation[] getStations()
  {
    if (stations == null)
      return EMPTY_STATIONS;

    return stations;
  }

  public SDMapMetadata getMetadata()
  {
    return metadata;
  }

  public SDTransport getTransport()
  {
    return transport;
  }
}
