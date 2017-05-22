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
package sage.epg.sd.json.map.station;

import sage.Pooler;
import sage.epg.sd.SDUtils;

import java.util.Arrays;

public class SDStation
{
  private static final SDLogo EMPTY_LOGOS[] = new SDLogo[0];

  private String stationID;
  private String name;
  private String callsign;
  private String affiliate;
  private String broadcastLanguage[];
  private String descriptionLanguage[];
  private SDBroadcaster broadcaster;
  private boolean isCommercialFree;
  private SDLogo logo;
  private SDLogo stationLogo[];

  public int getStationID()
  {
    return SDUtils.fromStationIDtoSageTV(stationID);
  }

  public String getName()
  {
    return name;
  }

  public String getCallsign()
  {
    return callsign;
  }

  public String getAffiliate()
  {
    return affiliate;
  }

  public String[] getBroadcastLanguage()
  {
    if (broadcastLanguage == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return broadcastLanguage;
  }

  public String[] getDescriptionLanguage()
  {
    if (descriptionLanguage == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return descriptionLanguage;
  }

  public SDBroadcaster getBroadcaster()
  {
    return broadcaster;
  }

  public boolean isCommercialFree()
  {
    return isCommercialFree;
  }

  public SDLogo getLogo()
  {
    if (logo == null && stationLogo != null && stationLogo.length > 0)
      return stationLogo[0];
    return logo;
  }

  public SDLogo[] getStationLogos()
  {
    return stationLogo != null ? stationLogo : EMPTY_LOGOS;
  }

  @Override
  public String toString()
  {
    return "SDStation{" +
        "stationID='" + stationID + '\'' +
        ", name='" + name + '\'' +
        ", callsign='" + callsign + '\'' +
        ", affiliate='" + affiliate + '\'' +
        ", broadcastLanguage=" + Arrays.toString(broadcastLanguage) +
        ", descriptionLanguage=" + Arrays.toString(descriptionLanguage) +
        ", broadcaster=" + broadcaster +
        ", isCommercialFree=" + isCommercialFree +
        ", logo=" + logo +
        ", stationLogo=" + Arrays.toString(stationLogo) +
        '}';
  }
}
