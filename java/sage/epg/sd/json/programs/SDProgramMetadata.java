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

public class SDProgramMetadata
{
  private String provider;
  private int season;
  private int episode;
  private int totalSeasons;
  private int totalEpisodes;

  protected SDProgramMetadata(String provider, int season, int episode, int totalSeasons, int totalEpisodes)
  {
    this.provider = provider;
    this.season = season;
    this.episode = episode;
    this.totalSeasons = totalSeasons;
    this.totalEpisodes = totalEpisodes;
  }

  /**
   * Get the metadata provider. So far I've only seen Gracenote.
   */
  public String getProvider()
  {
    return provider;
  }

  /**
   * integer indicating the season number. Mandatory.
   */
  public int getSeason()
  {
    return season;
  }

  /**
   * integer indicating the episode number. Optional.
   */
  public int getEpisode()
  {
    return episode;
  }

  /**
   * integer indicating the total number of seasons in the series. SH programs only. Optional.
   */
  public int getTotalSeasons()
  {
    return totalSeasons;
  }

  /**
   * an integer indicating the total number of episodes. Note: in an "EP" program this indicates
   * the total number of episodes in this season. In an "SH" program, it will indicate the total
   * number of episodes in the series. Optional.
   */
  public int getTotalEpisodes()
  {
    return totalEpisodes;
  }
}
