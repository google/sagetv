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

import sage.Airing;
import sage.Pooler;
import sage.epg.sd.gson.annotations.SerializedName;
import sage.epg.sd.json.programs.SDContentRating;

import java.util.Arrays;

public class SDProgramSchedule
{
  private static final SDContentRating[] EMPTY_CONTENT_RATING = new SDContentRating[0];

  private String programID;
  private String airDateTime;
  private int duration;
  private String md5;
  @SerializedName("new")
  private boolean newShowing;
  private boolean cableInTheClassroom;
  private boolean catchup;
  private boolean continued;
  private boolean educational;
  private boolean joinedInProgress;
  private boolean leftInProgress;
  private boolean premiere;
  private boolean programBreak;
  private boolean repeat;
  private boolean signed;
  private boolean subjectToBlackout;
  private boolean timeApproximate;
  private boolean free;
  private String liveTapeDelay;
  private String isPremiereOrFinale;
  private SDContentRating ratings[];
  private SDMultiPart multipart;
  private String audioProperties[];
  private String videoProperties[];

  /**
   * 14 characters. Mandatory. What programID will the data be for.
   */
  public String getProgramID()
  {
    return programID;
  }

  /**
   * 20 characters. Mandatory. "2014-10-03T00:00:00Z". This will always be in "Z" time; your grabber
   * must make any adjustments to localtime if it does not natively work in "Z" time internally.
   */
  public String getAirDateTime()
  {
    return airDateTime;
  }

  /**
   * integer. Mandatory. Duration of the program in seconds.
   */
  public long getDuration()
  {
    return ((long)duration & 0xFFFFFFFFL) * 1000L;
  }

  /**
   * 22 characters. Mandatory. The MD5 hash value of the JSON data on the server for the programID.
   * If your application has cached the JSON for the program, but the cached MD5 isn't the same as
   * what is in the schedule, that should trigger your grabber to refresh the JSON for the programID
   * because it's changed.
   */
  public String getMd5()
  {
    return md5;
  }

  /**
   * is this showing new?
   */
  public boolean isNewShowing()
  {
    return newShowing;
  }

  public boolean isCableInTheClassroom()
  {
    return cableInTheClassroom;
  }

  /**
   * typically only found outside of North America
   */
  public boolean isCatchup()
  {
    return catchup;
  }

  /**
   * typically only found outside of North America
   */
  public boolean isContinued()
  {
    return continued;
  }

  public boolean isEducational()
  {
    return educational;
  }

  public boolean isJoinedInProgress()
  {
    return joinedInProgress;
  }

  public boolean isLeftInProgress()
  {
    return leftInProgress;
  }

  /**
   * Program stops and will restart later (frequently followed by a continued). Typically only found
   * outside of North America.
   */
  public boolean isProgramBreak()
  {
    return programBreak;
  }

  /**
   * An encore presentation. Repeat should only be found on a second telecast of sporting events.
   */
  public boolean isRepeat()
  {
    return repeat;
  }

  /**
   * Program has an on-screen person providing sign-language translation.
   */
  public boolean isSigned()
  {
    return signed;
  }

  public boolean isSubjectToBlackout()
  {
    return subjectToBlackout;
  }

  public boolean isTimeApproximate()
  {
    return timeApproximate;
  }

  /**
   * the program is on a channel which typically has a cost, such as pay-per-view, but in this
   * instance is free.
   */
  public boolean isFree()
  {
    return free;
  }

  /**
   * is this showing Live, or Tape Delayed?. Possible values: "Live", "Tape", "Delay".
   */
  public String getLiveTapeDelay()
  {
    return liveTapeDelay;
  }

  public boolean isPremiere()
  {
    return premiere || (isPremiereOrFinale != null && isPremiereOrFinale.contains("Premiere"));
  }

  public boolean isFinale()
  {
    return isPremiereOrFinale != null && isPremiereOrFinale.contains("Finale");
  }

  /**
   * an array of ratings values.
   */
  public SDContentRating[] getRatings()
  {
    if (ratings == null)
      return EMPTY_CONTENT_RATING;

    return ratings;
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
    if (ratings == null)
      return "";

    for (SDContentRating rating : ratings)
    {
      if (ratingBoard.equalsIgnoreCase(rating.getBody()))
      {
        return rating.getCode();
      }
    }

    return "";
  }

  public byte getParentalRatingByte()
  {
    String contentRating = getContentRating("USA Parental Rating");

    switch (contentRating)
    {
      case "TVY":
        return Airing.TVY_VALUE;
      case "TVY7":
        return Airing.TVY7_VALUE;
      case "TVG":
        return Airing.TVG_VALUE;
      case "TVPG":
        return Airing.TVPG_VALUE;
      case "TV14":
        return Airing.TV14_VALUE;
      case "TVMA":
        return Airing.TVMA_VALUE;
    }

    return 0;
  }

  /**
   *  indicates whether the program is one of a series.
   */
  public SDMultiPart getMultipart()
  {
    return multipart;
  }

  public byte getMultipartByte()
  {
    return multipart != null ? (byte) ((multipart.getPartNumber() << 4) | multipart.getTotalParts()) : 0;
  }

  /**
   * optional. An array of audio properties.
   *
   * Possible values:
   * Atmos - Dolby Atmos
   * cc - Closed Captioned
   * DD
   * DD 5.1
   * Dolby
   * dubbed
   * dvs - Descriptive Video Service
   * SAP - Secondary Audio Program
   * stereo
   * subtitled
   * surround
   */
  public String[] getAudioProperties()
  {
    if (audioProperties == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return audioProperties;
  }

  /**
   * optional. An array of video properties.
   *
   * Possible values:
   *
   * 3d - is this showing in 3d
   * enhanced - Enhanced is better video quality than Standard Definition, but not true High Definition. (720p / 1080i)
   * hdtv - the content is in High Definition
   * letterbox
   * sdtv
   * uhdtv - the content is in "UHDTV"; this is provider-dependent and does not imply any particular resolution or encoding
   */
  public String[] getVideoProperties()
  {
    if (videoProperties == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return videoProperties;
  }

  public int getMisc()
  {
    int returnValue = 0;

    for (String audioProperty : getAudioProperties())
    {
      if (audioProperty == null)
        continue;

      if (audioProperty.equalsIgnoreCase("cc"))
        returnValue |= Airing.CC_MASK;
      /*else if (audioProperty.equalsIgnoreCase("Atmos"))
        returnValue |= Airing.ATMOS_MASK;*/
      else if (audioProperty.equalsIgnoreCase("DD"))
        returnValue |= Airing.DOLBY_MASK;
      else if (audioProperty.equalsIgnoreCase("DD 5.1"))
        returnValue |= Airing.DD51_MASK;
      else if (audioProperty.equalsIgnoreCase("Dolby"))
        returnValue |= Airing.DOLBY_MASK;
      else if (audioProperty.equalsIgnoreCase("dubbed"))
        returnValue |= Airing.DUBBED_MASK;
      /*else if (audioProperty.equalsIgnoreCase("dvs"))
        returnValue |= Airing.DVS_MASK;*/
      else if (audioProperty.equalsIgnoreCase("SAP"))
        returnValue |= Airing.SAP_MASK;
      else if (audioProperty.equalsIgnoreCase("stereo"))
        returnValue |= Airing.STEREO_MASK;
      else if (audioProperty.equalsIgnoreCase("subtitled"))
        returnValue |= Airing.SUBTITLE_MASK;
      else if (audioProperty.equalsIgnoreCase("surround"))
        returnValue |= Airing.SURROUND_MASK;
    }

    for (String videoProperty : getVideoProperties())
    {
      if (videoProperty == null)
        continue;

      if (videoProperty.equalsIgnoreCase("3D"))
        returnValue |= Airing.THREED_MASK;
      /*if (videoProperty.equalsIgnoreCase("enhanced"))
        returnValue |= Airing.ENHANCED_MASK;*/
      if (videoProperty.equalsIgnoreCase("hdtv"))
        returnValue |= Airing.HDTV_MASK;
      if (videoProperty.equalsIgnoreCase("letterbox"))
        returnValue |= Airing.LETTERBOX_MASK;
      /*if (videoProperty.equalsIgnoreCase("sdtv"))
        returnValue |= Airing.SDTV_MASK;*/
      /*if (videoProperty.equalsIgnoreCase("UHDTV"))
        returnValue |= Airing.UHDTV_MASK;*/
    }

    boolean setMask = true;
    if (isPremiereOrFinale != null)
    {
      switch (isPremiereOrFinale)
      {
        case "Season Premiere":
          returnValue |= Airing.SEASON_PREMIERE_MASK;
          break;
        case "Season Finale":
          returnValue |= Airing.SEASON_FINALE_MASK;
          break;
        case "Series Premiere":
          returnValue |= Airing.SERIES_PREMIERE_MASK;
          break;
        case "Series Finale":
          returnValue |= Airing.SERIES_FINALE_MASK;
          break;
        case "Premiere":
          returnValue |= Airing.PREMIERE_MASK;
          break;
        default:
          setMask = false;
          break;
      }
    }
    else
    {
      setMask = false;
    }

    if (!setMask)
    {
      if (isPremiere())
        returnValue |= Airing.PREMIERE_MASK;
    }

    if (newShowing)
      returnValue |= Airing.NEW_MASK;

    return returnValue;
  }

  @Override
  public String toString()
  {
    return "SDProgramSchedule{" +
      "programID='" + programID + '\'' +
      ", airDateTime='" + airDateTime + '\'' +
      ", duration=" + duration +
      ", md5='" + md5 + '\'' +
      ", newShowing=" + newShowing +
      ", cableInTheClassroom=" + cableInTheClassroom +
      ", catchup=" + catchup +
      ", continued=" + continued +
      ", educational=" + educational +
      ", joinedInProgress=" + joinedInProgress +
      ", leftInProgress=" + leftInProgress +
      ", premiere=" + premiere +
      ", programBreak=" + programBreak +
      ", repeat=" + repeat +
      ", signed=" + signed +
      ", subjectToBlackout=" + subjectToBlackout +
      ", timeApproximate=" + timeApproximate +
      ", free=" + free +
      ", liveTapeDelay='" + liveTapeDelay + '\'' +
      ", isPremiereOrFinale='" + isPremiereOrFinale + '\'' +
      ", ratings=" + Arrays.toString(ratings) +
      ", multipart=" + multipart +
      ", audioProperties=" + Arrays.toString(audioProperties) +
      ", videoProperties=" + Arrays.toString(videoProperties) +
      '}';
  }
}
