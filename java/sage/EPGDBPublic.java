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
package sage;

public interface EPGDBPublic
{
  /*
   * Call this to add a Channel to the database. This will update if the stationID is already
   * used.  name should be the call sign, like KABC. longName can be a full descriptive name like
   * "Channel 4 Los Angeles NBC". network represents the parent network, i.e. ABC, HBO, MTV and is
   * optional. stationID is the GUID referring to this Channel.
   *
   * Returns true if the Channel was successfully updated/added to the database.
   */
  public boolean addChannelPublic(String name, String longName, String network, int stationID);
  /*
   * Call this with the current providerID, and a map of stationIDs to channel numbers. The keys in the map
   * should be Integer objects wrapping stationIDs as used in the addChannelPublic method. The values in the map
   * should be String[] that represent the channel numbers for that station. An example is if ESPN w/ stationdID=34
   * is on channel numbers 3 and 94, the map would contain a: Integer(34)->{"3", "94"}
   */
  public void setLineup(long providerID, java.util.Map lineupMap);
  // The ROLE constants are used in addShowPublic below
  static final byte ACTOR_ROLE = 1;
  static final byte LEAD_ACTOR_ROLE = 2;
  static final byte SUPPORTING_ACTOR_ROLE = 3;
  static final byte ACTRESS_ROLE = 4;
  static final byte LEAD_ACTRESS_ROLE = 5;
  static final byte SUPPORTING_ACTRESS_ROLE = 6;
  static final byte GUEST_ROLE = 7;
  static final byte GUEST_STAR_ROLE = 8;
  static final byte DIRECTOR_ROLE = 9;
  static final byte PRODUCER_ROLE = 10;
  static final byte WRITER_ROLE = 11;
  static final byte CHOREOGRAPHER_ROLE = 12;
  static final byte SPORTS_FIGURE_ROLE = 13;
  static final byte COACH_ROLE = 14;
  static final byte HOST_ROLE = 15;
  static final byte EXECUTIVE_PRODUCER_ROLE = 16;
  static final byte JUDGE_ROLE = 20;
  static final byte NARRATOR_ROLE = 21;
  /*
   * Call this to add a Show to the database. If a show with this extID is already present, it will be updated
   * to this information. You can use null or String[0] for any fields you don't want to specify.
   * title - the title of the show (use for reruns)
   * primeTitle - the title of the show (use for first runs)
   * episodeName - the name of the episode
   * desc - a description of this show
   * duration - not used, set to 0
   * category - name of a category for this show
   * subCategory - name of a subCategory for this show
   * people - names of people/actors in this show
   * roles - must be same length as people array, uses the X_ROLE constants in this file to specify what each is
   * rated - rating of a show, i.e. PG, G, R, etc.
   * expandedRatings - additional rating information, i.e. Violence, Nudity, Adult Content
   * year - the year it was produced, for movies
   * parentalRating - not used, set to null
   * bonus - additional information about the show
   * extID - GUID representing this show
   * language - the language the show is in
   * originalAirDate - the original airing date of this show, it's a long value from java.util.Date
   *
   * Returns true if the Show was successfully updated/added to the database.
   */
  public boolean addShowPublic(String title, String primeTitle, String episodeName, String desc, long duration, String category,
      String subCategory, String[] people, byte[] roles, String rated, String[] expandedRatings,
      String year, String parentalRating, String[] bonus, String extID, String language, long originalAirDate);
  /*
   * Call this to add an Airing to the database. An Airing is time-channel-show correlation.
   * extID - refers to the GUID of a Show previously added with addShowPublic
   * stationID - referes to the stationID GUID of a Channel previously added with addChannelPublic
   * startTime - the time this airing starts, a long from java.util.Date
   * duration - the length of this airing in milliseconds
   *
   * Returns true if this Airing was successfully updated/added to the database. The database will
   * automatically ensure that there are no inconsistencies in the Airings, if you add one that
   * overlaps with the station-time-duration of another. The one(s) that were in the database before
   * the call will be removed and the new one added. It will also fill in any gaps with "No Data" sections
   * for you automatically.
   */
  public boolean addAiringPublic(String extID, int stationID, long startTime, long duration);

  /*
   * Call this to add an Airing to the database. An Airing is time-channel-show correlation.
   * extID - refers to the GUID of a Show previously added with addShowPublic
   * stationID - referes to the stationID GUID of a Channel previously added with addChannelPublic
   * startTime - the time this airing starts, a long from java.util.Date
   * duration - the length of this airing in milliseconds
   * partNumber - if it is a multipart show this is the part number, otherwise this should be 0
   * totalParts - for multipart TV shows, this is the total number of parts otherwise this should be zero
   * parentalRating - the parental rating for the show, should be a localized value from "TVY", "TVY7", "TVG", "TVPG", "TV14", "TVM" or the empty string
   * hdtv - true if it's an HDTV airing, false otherwise
   * stereo - true if it's a stereo recording, false otherwise
   * closedCaptioning - true if the airing has closed captioning, false otherwise
   * sap - true if the Airing has a Secondary Audio Program (SAP), false otherwise
   * subtitled - true if the Airing is subtitled, false otherwise
   * premierFinale - should be the empty string or a localized value from the list "Premier", "Channel Premier", "Season Premier", "Series Premier", "Season Finale", "Series Finale"
   *
   * Returns true if this Airing was successfully updated/added to the database. The database will
   * automatically ensure that there are no inconsistencies in the Airings, if you add one that
   * overlaps with the station-time-duration of another. The one(s) that were in the database before
   * the call will be removed and the new one added. It will also fill in any gaps with "No Data" sections
   * for you automatically.
   */
  public boolean addAiringDetailedPublic(String extID, int stationID, long startTime, long duration, int partNumber, int totalParts,
      String parentalRating, boolean hdtv, boolean stereo, boolean closedCaptioning, boolean sap, boolean subtitled, String premierFinale);

  /*
   * Call this to add a SeriesInfo object to the database. If a SeriesInfo with this seriesID is already present, it will be updated
   * to this information. You can use null or String[0] for any fields you don't want to specify.
   * seriesID - the ID of the series, this should match the prefix of corresponding ShowIDs w/out the last 4 digits for proper linkage (i.e. the SeriesID for EP1234567890 would be 123456)
   * title - the title of the series
   * network - the network that airs the series
   * description - a description of this series
   * history - a historical description of the series
   * premiereDate - a String representation of the date the series premiered
   * finaleDate - a String representation of the date the series ended
   * airDOW - a String representation of the day of the week the series airs
   * airHrMin - a String representation of the time the series airs
   * imageURL - a URL that links to an image for this series
   * people - names of people/actors in this show
   * characters - must be same length as people array, should give the character names the corresponding people have in the series
   *
   * Returns true if the SeriesInfo was successfully updated/added to the database.
   */
  public boolean addSeriesInfoPublic(int seriesID, String title, String network, String description, String history, String premiereDate,
      String finaleDate, String airDOW, String airHrMin, String imageURL, String[] people, String[] characters);
}
