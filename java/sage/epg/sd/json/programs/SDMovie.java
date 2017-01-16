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

import sage.Sage;

import java.util.Arrays;

public class SDMovie
{
  private static final QualityRating[] EMPTY_QUALITY_RATING = new QualityRating[0];

  private String year;
  //private int duration;
  private QualityRating qualityRating[];

  /**
   * YYYY. The year the movie was released. Optional.
   */
  public String getYear()
  {
    if (year == null)
      return "";

    return year;
  }

  /**
   * Duration (in integer seconds). Optional. NOTE: in a future API this will be removed from the
   * movie array and will be an element of the program itself.
   */
  /*public int getDuration()
  {
    return duration;
  }*/

  /**
   * an array of ratings for the quality of the movie. Optional.
   */
  public QualityRating[] getQualityRating()
  {
    if (qualityRating == null)
      return EMPTY_QUALITY_RATING;

    return qualityRating;
  }

  /**
   * Get the quality rating for a specific rating body.
   * <p/>
   * If the rating body isn't available, a blank string will be returned. A value of null will
   * return the first rating found.
   *
   * @param ratingBody The ratings body.
   * @return The rating or blank string.
   */
  public String getQualityRating(String ratingBody)
  {
    if (qualityRating == null)
      return "";

    if (ratingBody == null && qualityRating.length > 0)
      return qualityRating[0].getRating();

    for (QualityRating qualityRating : this.qualityRating)
    {
      if (qualityRating.ratingsBody.equalsIgnoreCase(ratingBody))
      {
        return qualityRating.rating;
      }
    }

    return "";
  }

  public String getFormattedQualityRating()
  {
    String quality = getQualityRating("Gracenote");
    if (quality.length() == 0)
      return quality;
    StringBuilder returnValue = new StringBuilder(5);
    char chars[] = quality.toCharArray();
    boolean decimal = false;
    for (char number : chars)
    {
      if (number >= '0' && number <= '9')
      {
        if (decimal)
        {
          if (number > '0')
            returnValue.append("+");
          break;
        }

        int stars = number - '0';
        for (int i = 0; i < stars; i++)
        {
          returnValue.append("*");
        }
      }
      else if (returnValue.length() > 0)
      {
        decimal = number == '.';
        if (!decimal)
        {
          if (Sage.DBG) System.out.println("SDEPG Unexpected movie quality format: " + quality);
          returnValue.setLength(0);
        }
      }
    }
    return returnValue.toString();
  }

  @Override
  public String toString()
  {
    return "SDMovie{" +
      "year='" + year + '\'' +
      ", qualityRating=" + Arrays.toString(qualityRating) +
      '}';
  }

  public static class QualityRating
  {
    private String ratingsBody;
    private String rating;
    private String minRating;
    private String maxRating;
    private String increment;

    /**
     * string indicating whose opinion this is. Mandatory.
     */
    public String getRatingsBody()
    {
      return ratingsBody;
    }

    /**
     * string indicating the rating. Mandatory.
     */
    public String getRating()
    {
      return rating;
    }

    /**
     * string indicating the lowest rating. Optional.
     */
    public String getMinRating()
    {
      return minRating;
    }

    /**
     * string indicating the highest rating. Optional.
     */
    public String getMaxRating()
    {
      return maxRating;
    }

    /**
     * string indicating the increment. Optional.
     */
    public String getIncrement()
    {
      return increment;
    }

    @Override
    public String toString()
    {
      return "QualityRating{" +
        "ratingsBody='" + ratingsBody + '\'' +
        ", rating='" + rating + '\'' +
        ", minRating='" + minRating + '\'' +
        ", maxRating='" + maxRating + '\'' +
        ", increment='" + increment + '\'' +
        '}';
    }
  }
}
