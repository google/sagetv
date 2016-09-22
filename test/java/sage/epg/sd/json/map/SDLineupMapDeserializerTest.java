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
  public void deserializeCable()
  {
    // Source: https://json.schedulesdirect.org/20141201/lineups/USA-PA67431-X
    String cable = "{\n" +
        "  \"map\": [\n" +
        "    {\n" +
        "      \"stationID\": \"11523\",\n" +
        "      \"channel\": \"002\"\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"11559\",\n" +
        "      \"channel\": \"003\"\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"11782\",\n" +
        "      \"channel\": \"004\"\n" +
        "    }" +
        "  ],\n" +
        "  \"stations\": [\n" +
        "    {\n" +
        "      \"stationID\": \"11523\",\n" +
        "      \"name\": \"WHP\",\n" +
        "      \"callsign\": \"WHP\",\n" +
        "      \"affiliate\": \"CBS\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s10098_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"c6fcd923a007fd4db485e0\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"11559\",\n" +
        "      \"name\": \"WITF\",\n" +
        "      \"callsign\": \"WITF\",\n" +
        "      \"affiliate\": \"PBS\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17111\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"isCommercialFree\": true,\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s11039_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"594f1251ccd7e9f21b6856\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"11782\",\n" +
        "      \"name\": \"WPMT\",\n" +
        "      \"callsign\": \"WPMT\",\n" +
        "      \"affiliate\": \"FOX\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"York\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17403\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s10212_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"4c0a04c47337a16a8944a5\"\n" +
        "      }\n" +
        "    }" +
        "  ],\n" +
        "  \"metadata\": {\n" +
        "    \"lineup\": \"USA-PA67431-X\",\n" +
        "    \"modified\": \"2016-07-30T05:26:44Z\",\n" +
        "    \"transport\": \"Cable\"\n" +
        "  }\n" +
        "}";

    SDLineupMap lineupMap = deserialize(cable, SDLineupMap.class);
    assert lineupMap.getMap() != null;
    assert lineupMap.getMessage() == null || lineupMap.getMessage().equals("");
    assert lineupMap.getMap() instanceof SDBasicChannelMap[];
    basicAssert(lineupMap);
  }

  @Test(groups = {"gson", "schedulesDirect", "lineupMap", "satellite" })
  public void deserializeSatellite()
  {
    // Source: https://json.schedulesdirect.org/20141201/lineups/USA-DISH566-DEFAULT
    String satellite = "{\n" +
        "  \"map\": [\n" +
        "    {\n" +
        "      \"stationID\": \"32046\",\n" +
        "      \"channel\": \"001\"\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"11458\",\n" +
        "      \"channel\": \"008\"\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"25544\",\n" +
        "      \"channel\": \"008\"\n" +
        "    }" +
        "    ],\n" +
        "  \"stations\": [\n" +
        "    {\n" +
        "      \"stationID\": \"11458\",\n" +
        "      \"name\": \"WGAL\",\n" +
        "      \"callsign\": \"WGAL\",\n" +
        "      \"affiliate\": \"NBC\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Lancaster\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17604\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s10991_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"28bb365cb84b75bb4eaae2\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"25544\",\n" +
        "      \"name\": \"WGALDT (WGAL-DT)\",\n" +
        "      \"callsign\": \"WGALDT\",\n" +
        "      \"affiliate\": \"NBC\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Lancaster\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17604\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s28717_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"6c7554a4b7f6d83bcacf39\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"11663\",\n" +
        "      \"name\": \"WXBU\",\n" +
        "      \"callsign\": \"WXBU\",\n" +
        "      \"affiliate\": \"GRIT\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s89922_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"a97c64d14a5f1eff15c3e6\"\n" +
        "      }\n" +
        "    }],\n" +
        "  \"metadata\": {\n" +
        "    \"lineup\": \"USA-DISH566-DEFAULT\",\n" +
        "    \"modified\": \"2016-07-29T19:01:03Z\",\n" +
        "    \"transport\": \"Satellite\"\n" +
        "  }\n" +
        "}";

    SDLineupMap lineupMap = deserialize(satellite, SDLineupMap.class);
    assert lineupMap.getMap() != null;
    assert lineupMap.getMessage() == null || lineupMap.getMessage().equals("");
    assert lineupMap.getMap() instanceof SDBasicChannelMap[];
    basicAssert(lineupMap);
  }

  @Test(groups = {"gson", "schedulesDirect", "lineupMap", "antenna" })
  public void deserializeAntenna()
  {
    // Source: https://json.schedulesdirect.org/20141201/lineups/USA-OTA-17112
    String antenna = "{\n" +
        "  \"map\": [\n" +
        "    {\n" +
        "      \"stationID\": \"79329\",\n" +
        "      \"uhfVhf\": 49,\n" +
        "      \"atscMajor\": 8,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"25544\",\n" +
        "      \"uhfVhf\": 8,\n" +
        "      \"atscMajor\": 8,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"79331\",\n" +
        "      \"uhfVhf\": 49,\n" +
        "      \"atscMajor\": 8,\n" +
        "      \"atscMinor\": 2\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"50843\",\n" +
        "      \"uhfVhf\": 8,\n" +
        "      \"atscMajor\": 8,\n" +
        "      \"atscMinor\": 2\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"31768\",\n" +
        "      \"uhfVhf\": 23,\n" +
        "      \"atscMajor\": 15,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"65600\",\n" +
        "      \"uhfVhf\": 23,\n" +
        "      \"atscMajor\": 15,\n" +
        "      \"atscMinor\": 2\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"97104\",\n" +
        "      \"uhfVhf\": 23,\n" +
        "      \"atscMajor\": 15,\n" +
        "      \"atscMinor\": 3\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"30783\",\n" +
        "      \"uhfVhf\": 21,\n" +
        "      \"atscMajor\": 21,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"52278\",\n" +
        "      \"uhfVhf\": 21,\n" +
        "      \"atscMajor\": 21,\n" +
        "      \"atscMinor\": 2\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"97889\",\n" +
        "      \"uhfVhf\": 21,\n" +
        "      \"atscMajor\": 21,\n" +
        "      \"atscMinor\": 3\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"35305\",\n" +
        "      \"uhfVhf\": 13,\n" +
        "      \"atscMajor\": 22,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"44627\",\n" +
        "      \"uhfVhf\": 10,\n" +
        "      \"atscMajor\": 27,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"44629\",\n" +
        "      \"uhfVhf\": 10,\n" +
        "      \"atscMajor\": 27,\n" +
        "      \"atscMinor\": 2\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"63391\",\n" +
        "      \"uhfVhf\": 10,\n" +
        "      \"atscMajor\": 27,\n" +
        "      \"atscMinor\": 3\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"34976\",\n" +
        "      \"uhfVhf\": 11,\n" +
        "      \"atscMajor\": 28,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"19610\",\n" +
        "      \"uhfVhf\": 36,\n" +
        "      \"atscMajor\": 33,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"35511\",\n" +
        "      \"uhfVhf\": 36,\n" +
        "      \"atscMajor\": 33,\n" +
        "      \"atscMinor\": 3\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"59670\",\n" +
        "      \"uhfVhf\": 35\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"67550\",\n" +
        "      \"uhfVhf\": 7,\n" +
        "      \"atscMajor\": 35,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"32629\",\n" +
        "      \"uhfVhf\": 47,\n" +
        "      \"atscMajor\": 43,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"51514\",\n" +
        "      \"uhfVhf\": 47,\n" +
        "      \"atscMajor\": 43,\n" +
        "      \"atscMinor\": 2\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"70546\",\n" +
        "      \"uhfVhf\": 47,\n" +
        "      \"atscMajor\": 43,\n" +
        "      \"atscMinor\": 3\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"34967\",\n" +
        "      \"uhfVhf\": 30,\n" +
        "      \"atscMajor\": 49,\n" +
        "      \"atscMinor\": 1\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"63356\",\n" +
        "      \"uhfVhf\": 30,\n" +
        "      \"atscMajor\": 49,\n" +
        "      \"atscMinor\": 2\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"91324\",\n" +
        "      \"uhfVhf\": 30,\n" +
        "      \"atscMajor\": 49,\n" +
        "      \"atscMinor\": 3\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"99909\",\n" +
        "      \"uhfVhf\": 30,\n" +
        "      \"atscMajor\": 49,\n" +
        "      \"atscMinor\": 4\n" +
        "    }\n" +
        "  ],\n" +
        "  \"stations\": [\n" +
        "    {\n" +
        "      \"stationID\": \"79329\",\n" +
        "      \"name\": \"WGALLD (WGAL-LD)\",\n" +
        "      \"callsign\": \"WGALLD\",\n" +
        "      \"affiliate\": \"NBC\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Lancaster\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17604\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s28717_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"6c7554a4b7f6d83bcacf39\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"25544\",\n" +
        "      \"name\": \"WGALDT (WGAL-DT)\",\n" +
        "      \"callsign\": \"WGALDT\",\n" +
        "      \"affiliate\": \"NBC\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Lancaster\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17604\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s28717_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"6c7554a4b7f6d83bcacf39\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"79331\",\n" +
        "      \"name\": \"WGALLD2 (WGAL-LD2)\",\n" +
        "      \"callsign\": \"WGALLD2\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Lancaster\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17604\",\n" +
        "        \"country\": \"United States\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"50843\",\n" +
        "      \"name\": \"WGALDT2 (WGAL-DT2)\",\n" +
        "      \"callsign\": \"WGALDT2\",\n" +
        "      \"affiliate\": \"METVN\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Lancaster\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17604\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s70436_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"999ad5c3ce121bba5cf6b1\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"31768\",\n" +
        "      \"name\": \"WXBUDT (WXBU-DT)\",\n" +
        "      \"callsign\": \"WXBUDT\",\n" +
        "      \"affiliate\": \"GRIT\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s53098_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"a48c6cc6e461e993bb38f5\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"65600\",\n" +
        "      \"name\": \"WXBUDT2 (WXBU-DT2)\",\n" +
        "      \"callsign\": \"WXBUDT2\",\n" +
        "      \"affiliate\": \"COMET\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s97051_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"5fa23ea9f65e3a772b747f\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"97104\",\n" +
        "      \"name\": \"WXBUDT3 (WXBU-DT3)\",\n" +
        "      \"callsign\": \"WXBUDT3\",\n" +
        "      \"affiliate\": \"COMET\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s97051_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"5fa23ea9f65e3a772b747f\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"30783\",\n" +
        "      \"name\": \"WHPDT (WHP-DT)\",\n" +
        "      \"callsign\": \"WHPDT\",\n" +
        "      \"affiliate\": \"CBS\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s28711_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"cd39083eddabbf918d0372\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"52278\",\n" +
        "      \"name\": \"WHPDT2 (WHP-DT2)\",\n" +
        "      \"callsign\": \"WHPDT2\",\n" +
        "      \"affiliate\": \"JTV\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s16604_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"c834727ffd24fdf9d21d11\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"97889\",\n" +
        "      \"name\": \"WHPDT3 (WHP-DT3)\",\n" +
        "      \"callsign\": \"WHPDT3\",\n" +
        "      \"affiliate\": \"CW\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s51306_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"79444f6040f9b1d0924504\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"35305\",\n" +
        "      \"name\": \"WYOUDT (WYOU-DT)\",\n" +
        "      \"callsign\": \"WYOUDT\",\n" +
        "      \"affiliate\": \"CBS\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Scranton\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"18503\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s28711_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"cd39083eddabbf918d0372\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"44627\",\n" +
        "      \"name\": \"WHTMDT (WHTM-DT)\",\n" +
        "      \"callsign\": \"WHTMDT\",\n" +
        "      \"affiliate\": \"ABC\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s28708_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"39987b0849d402b3f112b7\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"44629\",\n" +
        "      \"name\": \"WHTMDT2 (WHTM-DT2)\",\n" +
        "      \"callsign\": \"WHTMDT2\",\n" +
        "      \"affiliate\": \"ION\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s18633_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"7791fd59b59f86c19ba7aa\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"63391\",\n" +
        "      \"name\": \"WHTMDT3 (WHTM-DT3)\",\n" +
        "      \"callsign\": \"WHTMDT3\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17110\",\n" +
        "        \"country\": \"United States\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"34976\",\n" +
        "      \"name\": \"WBREDT (WBRE-DT)\",\n" +
        "      \"callsign\": \"WBREDT\",\n" +
        "      \"affiliate\": \"NBC\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Wilkes Barre\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"18701\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s28717_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"6c7554a4b7f6d83bcacf39\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"19610\",\n" +
        "      \"name\": \"WITFDT (WITF-DT)\",\n" +
        "      \"callsign\": \"WITFDT\",\n" +
        "      \"affiliate\": \"PBS\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17111\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"isCommercialFree\": true,\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s32356_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"4aa5ae639a1dfcf4811398\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"35511\",\n" +
        "      \"name\": \"WITFDT3 (WITF-DT3)\",\n" +
        "      \"callsign\": \"WITFDT3\",\n" +
        "      \"affiliate\": \"PBS\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Harrisburg\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17111\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"isCommercialFree\": true,\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s32356_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"4aa5ae639a1dfcf4811398\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"59670\",\n" +
        "      \"name\": \"W35BT\",\n" +
        "      \"callsign\": \"W35BT\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Altoona\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"16602\",\n" +
        "        \"country\": \"United States\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"67550\",\n" +
        "      \"name\": \"W07DPLP (W07DP-LP)\",\n" +
        "      \"callsign\": \"W07DPLP\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Wall\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"15148\",\n" +
        "        \"country\": \"United States\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"32629\",\n" +
        "      \"name\": \"WPMTDT (WPMT-DT)\",\n" +
        "      \"callsign\": \"WPMTDT\",\n" +
        "      \"affiliate\": \"FOX\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"York\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17403\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s28719_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"ced2c1ac9983b74d6fccb3\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"51514\",\n" +
        "      \"name\": \"WPMTDT2 (WPMT-DT2)\",\n" +
        "      \"callsign\": \"WPMTDT2\",\n" +
        "      \"affiliate\": \"ANTENNA\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"York\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17403\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s70248_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"6a2b597e34a88ba05dde8c\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"70546\",\n" +
        "      \"name\": \"WPMTDT3 (WPMT-DT3)\",\n" +
        "      \"callsign\": \"WPMTDT3\",\n" +
        "      \"affiliate\": \"THIS\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"York\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17403\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s61775_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"d537e6877bbec4c4ed44c8\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"34967\",\n" +
        "      \"name\": \"WGCBDT (WGCB-DT)\",\n" +
        "      \"callsign\": \"WGCBDT\",\n" +
        "      \"affiliate\": \"COZITV\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Red Lion\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17356\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s78851_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"d4a1ac8485c79aa0d8eb11\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"63356\",\n" +
        "      \"name\": \"WGCBDT2 (WGCB-DT2)\",\n" +
        "      \"callsign\": \"WGCBDT2\",\n" +
        "      \"affiliate\": \"COZITV\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Red Lion\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17356\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s78851_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"d4a1ac8485c79aa0d8eb11\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"91324\",\n" +
        "      \"name\": \"WGCBDT3 (WGCB-DT3)\",\n" +
        "      \"callsign\": \"WGCBDT3\",\n" +
        "      \"affiliate\": \"WORKS\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Red Lion\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17356\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s88461_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"a7fbe9fb4e239347cc18a1\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"stationID\": \"99909\",\n" +
        "      \"name\": \"WGCBDT4 (WGCB-DT4)\",\n" +
        "      \"callsign\": \"WGCBDT4\",\n" +
        "      \"affiliate\": \"ESCAPE\",\n" +
        "      \"broadcastLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"descriptionLanguage\": [\n" +
        "        \"en\"\n" +
        "      ],\n" +
        "      \"broadcaster\": {\n" +
        "        \"city\": \"Red Lion\",\n" +
        "        \"state\": \"PA\",\n" +
        "        \"postalcode\": \"17356\",\n" +
        "        \"country\": \"United States\"\n" +
        "      },\n" +
        "      \"logo\": {\n" +
        "        \"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s89923_h3_aa.png\",\n" +
        "        \"height\": 270,\n" +
        "        \"width\": 360,\n" +
        "        \"md5\": \"007cfed3f0b365e8737b86\"\n" +
        "      }\n" +
        "    }\n" +
        "  ],\n" +
        "  \"metadata\": {\n" +
        "    \"lineup\": \"USA-OTA-17112\",\n" +
        "    \"modified\": \"2016-07-21T21:19:48Z\",\n" +
        "    \"transport\": \"Antenna\"\n" +
        "  }\n" +
        "}";

    SDLineupMap lineupMap = deserialize(antenna, SDLineupMap.class);
    assert lineupMap.getMap() != null;
    assert lineupMap.getMessage() == null || lineupMap.getMessage().equals("");
    assert lineupMap.getMap() instanceof SDAntennaChannelMap[];
    basicAssert(lineupMap);
  }
}
