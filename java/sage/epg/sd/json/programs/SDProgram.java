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

import sage.Pooler;
import sage.epg.sd.json.SDError;
import sage.epg.sd.json.images.SDImage;

import java.util.Arrays;

public class SDProgram implements SDError
{
  private static final Title[] EMPTY_TITLES = new Title[0];
  private static final SDProgramMetadata[] EMPTY_METADATA = new SDProgramMetadata[0];
  private static final SDContentRating[] EMPTY_CONTENT_RATINGS = new SDContentRating[0];
  private static final SDPerson[] EMPTY_PEOPLE = new SDPerson[0];
  private static final SDRecommendation[] EMPTY_RECOMMENDATIONS = new SDRecommendation[0];

  // These should only be present if there was a problem. Code 6001 should produce a warning in the
  // UI that the channel was not updated. Code 6000 should display an error in the UI since the
  // channel is gone for good. Otherwise code will be 0 which indicates all is well.
  private int code;
  private String message;

  private String programID; // This correlates with ShowID.
  private Title titles[];
  private SDEventDetails eventDetails;
  private SDDescriptions descriptions;
  private String originalAirDate;
  private String genres[];
  private String officialURL;
  private SDKeyWords keyWords;
  private String episodeTitle150;
  private SDProgramMetadata metadata[];
  private String entityType;
  private String contentAdvisory[];
  private SDContentRating contentRating[];
  private SDMovie movie;
  private SDPerson cast[];
  private SDPerson crew[];
  private SDRecommendation recommendations[];
  private int duration;
  private SDImage episodeImage;
  private String showType;
  private String audience;
  private String holiday;
  private String animation;
  private boolean hasImageArtwork;
  private String md5;

  /**
   * 14 characters. Mandatory.
   */
  public String getProgramID()
  {
    return programID;
  }

  /**
   * array containing program titles. Mandatory.
   */
  public Title[] getTitles()
  {
    if (titles == null)
      return EMPTY_TITLES;

    return titles;
  }

  /**
   * indicates the type of program. Optional.
   */
  public SDEventDetails getEventDetails()
  {
    return eventDetails;
  }

  /**
   * array containing descriptions of the program. Optional.
   */
  public SDDescriptions getDescriptions()
  {
    if (descriptions == null)
      descriptions = new SDDescriptions();

    return descriptions;
  }

  /**
   * YYYY-MM-DD. Optional.
   */
  public String getOriginalAirDate()
  {
    return originalAirDate;
  }

  /**
   * array of genres this program falls under. Optional.
   */
  public String[] getGenres()
  {
    if (genres == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return genres;
  }

  /**
   * string containing the official URL of the program. Optional.
   */
  public String getOfficialURL()
  {
    return officialURL;
  }

  public SDKeyWords getKeyWords()
  {
    return keyWords;
  }

  /**
   * 150 character episode title. Optional.
   */
  public String getEpisodeTitle150()
  {
    return episodeTitle150;
  }

  /**
   * key / value array of metadata about the program. Optional.
   */
  public SDProgramMetadata[] getMetadata()
  {
    if (metadata == null)
      return EMPTY_METADATA;

    return metadata;
  }

  /**
   * Mandatory. [Show | Episode | Sports | Movie]
   * Not all sports events will have a leading "SP" programID; your application should use the
   * entity type.
   */
  public String getEntityType()
  {
    return entityType;
  }

  /**
   * array of advisories about the program, such as adult situations, violence, etc. Optional.
   */
  public String[] getContentAdvisory()
  {
    if (contentAdvisory == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return contentAdvisory;
  }

  /**
   * array consisting of various rating boards' ratings. Optional.
   */
  public SDContentRating[] getContentRating()
  {
    if (contentRating == null)
      return EMPTY_CONTENT_RATINGS;

    return contentRating;
  }

  /**
   * Get a content rating from a specific advisory board.
   * <p/>
   * If the desired rating cannot be found, a blank string will be returned.
   *
   * @param ratingBoard The exact name of the desired advisory board.
   * @return The content rating if the desired advisory board is available.
   */
  public String getContentRating(String ratingBoard)
  {
    if (contentRating == null)
      return "";

    for (SDContentRating rating : contentRating)
    {
      if (ratingBoard.equalsIgnoreCase(rating.getBody()))
      {
        return rating.getCode();
      }
    }

    return "";
  }

  /**
   * array of information specific to a movie type. Optional, and only found with "MV" programIDs.
   */
  public SDMovie getMovie()
  {
    return movie;
  }

  /**
   * array of cast members. Optional.
   */
  public SDPerson[] getCast()
  {
    if (cast == null)
      return EMPTY_PEOPLE;

    return cast;
  }

  /**
   * array of crew members. Optional.
   */
  public SDPerson[] getCrew()
  {
    if (crew == null)
      return EMPTY_PEOPLE;

    return crew;
  }

  /**
   * array of teams. Optional.
   */
  public SDPerson[] getTeams()
  {
    if (eventDetails == null)
      return EMPTY_PEOPLE;
    SDEventDetails.Teams teams[] = eventDetails.getTeams();
    if (teams.length == 0)
      return EMPTY_PEOPLE;
    SDPerson[] returnValue = new SDPerson[teams.length];
    for (int i = 0; i < returnValue.length; i++)
    {
      returnValue[i] = new SDPerson(teams[i].getName(), "Team", i + 1);
    }
    return returnValue;
  }

  /**
   * array of programs similar to this one that you may also enjoy. Optional.
   */
  public SDRecommendation[] getRecommendations()
  {
    if (recommendations == null)
      return EMPTY_RECOMMENDATIONS;

    return recommendations;
  }

  /**
   * Duration of the program without commercials (provided as in integer seconds and converted to
   * long milliseconds before returning). Optional.
   */
  public long getDuration()
  {
    return ((long)duration & 0xFFFFFFFFL) * 1000L;
  }

  /**
   * Contains a link to an image from this particular episode. Optional.
   */
  public SDImage getEpisodeImage()
  {
    return episodeImage;
  }

  /**
   * what sort of program is this. Optional.
   */
  public String getShowType()
  {
    return showType;
  }

  /**
   * The intended audience. Optional.
   * <p/>
   * Possible values:<br/>
   * Children
   * Adults only
   */
  public String getAudience()
  {
    return audience;
  }

  /**
   * Free-form string containing the holiday associated with the program. Optional.
   * Known values:
   * Christmas<br/>
   * Halloween<br/>
   * Thanksgiving<br/>
   * Valentine's Day<br/>
   * Easter<br/>
   * New Year<br/>
   * St. Patrick's Day<br/>
   * 20 de noviembre<br/>
   * Cinco de Mayo<br/>
   */
  public String getHoliday()
  {
    return holiday;
  }

  /**
   * Optional.
   * <p/>
   * Possible values:<br/>
   * Animated<br/>
   * Anime<br/>
   * Live action/animated<br/>
   * Live action/anime
   */
  public String getAnimation()
  {
    return animation;
  }

  /**
   * boolean indicating that there are images available for this program. Optional.
   */
  public boolean hasImageArtwork()
  {
    return hasImageArtwork;
  }

  /**
   * md5 hash value of the JSON. Mandatory.
   */
  public String getMd5()
  {
    return md5;
  }

  /**
   * Returns if there was an error.
   * <p/>
   * If the code 6001 is received, try again.
   *
   * @return Returns the code 6001 to try again. Returns 6000 to never try again. Returns 0 if the
   *         request was successful.
   */
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
   * Get the first title. If no titles exist, a blank string is returned.
   */
  public String getTitle()
  {
    if (titles == null || titles.length == 0)
      return ""; // No title.

    // The titles do not show what language they are in, so we just take the first one. A program
    // with more than one title has yet to be seen.
    return titles[0].getTitle120();
  }

  /**
   * Get the first available season number. If no season metadata exists, 0 is returned.
   */
  public short getSeason()
  {
    if (metadata == null || metadata.length == 0)
      return 0;

    for (SDProgramMetadata metadata : this.metadata)
    {
      if (metadata != null && metadata.getSeason() != 0)
      {
        return (short)metadata.getSeason();
      }
    }

    return 0;
  }

  /**
   * Get the first available episode number. If no episode metadata exists, 0 is returned.
   */
  public short getEpisode()
  {
    if (metadata == null || metadata.length == 0)
      return 0;

    for (SDProgramMetadata metadata : this.metadata)
    {
      if (metadata != null && metadata.getEpisode() != 0)
      {
        return (short)metadata.getEpisode();
      }
    }

    return 0;
  }

  /**
   * Get the first available total episode number. If no episode metadata exists, 0 is returned.
   */
  public short getTotalEpisodes()
  {
    if (metadata == null || metadata.length == 0)
      return 0;

    for (SDProgramMetadata metadata : this.metadata)
    {
      if (metadata != null && metadata.getTotalEpisodes() != 0)
      {
        return (short)metadata.getTotalEpisodes();
      }
    }

    return 0;
  }

  @Override
  public String toString()
  {
    return "SDProgram{" +
      "code=" + code +
      ", message='" + message + '\'' +
      ", programID='" + programID + '\'' +
      ", titles=" + Arrays.toString(titles) +
      ", eventDetails=" + eventDetails +
      ", descriptions=" + descriptions +
      ", originalAirDate='" + originalAirDate + '\'' +
      ", genres=" + Arrays.toString(genres) +
      ", officialURL='" + officialURL + '\'' +
      ", keyWords=" + keyWords +
      ", episodeTitle150='" + episodeTitle150 + '\'' +
      ", metadata=" + Arrays.toString(metadata) +
      ", entityType='" + entityType + '\'' +
      ", contentAdvisory=" + Arrays.toString(contentAdvisory) +
      ", contentRating=" + Arrays.toString(contentRating) +
      ", movie=" + movie +
      ", cast=" + Arrays.toString(cast) +
      ", crew=" + Arrays.toString(crew) +
      ", recommendations=" + Arrays.toString(recommendations) +
      ", duration=" + duration +
      ", episodeImage=" + episodeImage +
      ", showType='" + showType + '\'' +
      ", audience='" + audience + '\'' +
      ", holiday='" + holiday + '\'' +
      ", animation='" + animation + '\'' +
      ", hasImageArtwork=" + hasImageArtwork +
      ", md5='" + md5 + '\'' +
      '}';
  }
}
