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
package sage.epg.sd.json.programs;

import java.util.Arrays;

public class SDEventDetails
{
  private static final Teams[] EMPTY_TEAMS = new Teams[0];

  // Sporting events
  private String venue100;
  private Teams teams[];

  /**
   * Sporting events only. location of the event.
   */
  public String getVenue100()
  {
    return venue100;
  }

  /**
   * Sporting events only. array containing the teams that are playing. Optional.
   */
  public Teams[] getTeams()
  {
    if (teams == null)
      return EMPTY_TEAMS;

    return teams;
  }

  @Override
  public String toString()
  {
    return "SDEventDetails{" +
      "venue100='" + venue100 + '\'' +
      ", teams=" + Arrays.toString(teams) +
      '}';
  }

  public static class Teams
  {
    private String name;
    private boolean isHome;
    private String gameDate;

    /**
     * name of the team. Mandatory.
     */
    public String getName()
    {
      return name;
    }

    /**
     * boolean indicating this team is the home team. Optional.
     */
    public boolean isHome()
    {
      return isHome;
    }

    /**
     * YYYY-MM-DD. Optional.
     */
    public String gameDate()
    {
      return gameDate;
    }

    @Override
    public String toString()
    {
      return "Teams{" +
        "name='" + name + '\'' +
        ", isHome=" + isHome +
        ", gameDate='" + gameDate + '\'' +
        '}';
    }
  }
}
