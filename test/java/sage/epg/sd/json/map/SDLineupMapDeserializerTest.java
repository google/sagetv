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

import org.testng.annotations.Test;
import sage.epg.sd.DeserializeTest;
import sage.epg.sd.json.map.channel.SDAntennaChannelMap;
import sage.epg.sd.json.map.channel.SDBasicChannelMap;
import sage.epg.sd.json.map.station.SDStation;

import java.io.IOException;

public class SDLineupMapDeserializerTest extends DeserializeTest
{
  // Verify the basics have been populated. In production, String objects should usually be checked
  // for a null condition since many of them are optional.
  private static void basicAssert(SDLineupMap lineupMap)
  {
    assert lineupMap.getMetadata() != null;
    assert lineupMap.getMetadata().getLineup() != null;
    assert lineupMap.getMetadata().getModified() != null;
    assert lineupMap.getMetadata().getModulation() == null;
    assert lineupMap.getMetadata().getTransport() != null;
    assert lineupMap.getStations() != null;
    for (SDStation station : lineupMap.getStations())
    {
      //assert station.getAffiliate() != null;
      assert station.getBroadcaster() != null;
      assert station.getBroadcaster().getCity() != null;
      assert station.getBroadcaster().getCountry() != null;
      assert station.getBroadcaster().getPostalcode() != null;
      assert station.getBroadcaster().getState() != null;
      assert station.getCallsign() != null;
      assert station.getDescriptionLanguage() != null;
      for (String language : station.getDescriptionLanguage())
      {
        assert language != null;
      }
      if (station.getLogo() != null)
      {
        assert station.getLogo().getMd5() != null;
        assert station.getLogo().getURL() != null;
      }
      assert station.getName() != null;
      assert station.getStationID() != 0;
    }
  }

  @Test(groups = {"gson", "schedulesDirect", "lineupMap", "cable" })
  public void deserializeCable() throws IOException
  {
    // Source: https://json.schedulesdirect.org/20141201/lineups/USA-PA67431-X
    String cable = "epg/sd/json/map/mapCable.json";
    SDLineupMap lineupMap = deserialize(cable, SDLineupMap.class);
    assert lineupMap.getMap() != null;
    assert lineupMap.getMessage() == null || lineupMap.getMessage().equals("");
    assert lineupMap.getMap() instanceof SDBasicChannelMap[];
    basicAssert(lineupMap);
  }

  @Test(groups = {"gson", "schedulesDirect", "lineupMap", "satellite" })
  public void deserializeSatellite() throws IOException
  {
    // Source: https://json.schedulesdirect.org/20141201/lineups/USA-DISH566-DEFAULT
    String satellite = "epg/sd/json/map/mapSatellite.json";
    SDLineupMap lineupMap = deserialize(satellite, SDLineupMap.class);
    assert lineupMap.getMap() != null;
    assert lineupMap.getMessage() == null || lineupMap.getMessage().equals("");
    assert lineupMap.getMap() instanceof SDBasicChannelMap[];
    basicAssert(lineupMap);
  }

  @Test(groups = {"gson", "schedulesDirect", "lineupMap", "antenna" })
  public void deserializeAntenna() throws IOException
  {
    // Source: https://json.schedulesdirect.org/20141201/lineups/USA-OTA-17112
    String antenna = "epg/sd/json/map/mapAntenna.json";
    SDLineupMap lineupMap = deserialize(antenna, SDLineupMap.class);
    assert lineupMap.getMap() != null;
    assert lineupMap.getMessage() == null || lineupMap.getMessage().equals("");
    assert lineupMap.getMap() instanceof SDAntennaChannelMap[];
    basicAssert(lineupMap);
  }
}
