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
package sage.epg.sd.json.images;

import sage.epg.sd.SDSession;

public class SDImage
{
  public static final byte SIZE_UNKNOWN = 0;
  public static final byte SIZE_SM = 1;
  public static final byte SIZE_XS = 2;
  public static final byte SIZE_MD = 3;
  public static final byte SIZE_LG = 4;
  public static final byte SIZE_MS = 5;

  /**
   * the image tier was not provided or is new and as such is unknown.
   */
  public static final byte TIER_UNKNOWN = 0;
  /**
   * The tier is series level.
   */
  public static final byte TIER_SERIES = 1;
  /**
   * The tier is season level.
   */
  public static final byte TIER_SEASON = 2;
  /**
   * The tier is episode level.
   */
  public static final byte TIER_EPISODE = 3;


  /**
   * the image category was not provided or is new and as such is unknown.
   */
  public static final byte CAT_UNKNOWN = 0;
  /**
   * source-provided image, usually shows cast ensemble with source-provided text
   */
  public static final byte CAT_BANNER_L1 = 1;
  /**
   * source-provided image with plain text.
   */
  public static final byte CAT_BANNER_L2 = 2;
  /**
   * stock photo image with plain text
   */
  public static final byte CAT_BANNER_L3 = 3;
  /**
   * banner with Logo Only
   */
  public static final byte CAT_BANNER_LO = 4;
  /**
   *  banner with Logo Only + Text indicating season number
   */
  public static final byte CAT_BANNER_LOT = 5;
  /**
   * representative series/season/episode image, no text
   */
  public static final byte CAT_ICONIC = 6;
  /**
   * the staple image is intended to cover programs which do not have a unique banner image.
   */
  public static final byte CAT_STAPLE = 7;
  /**
   * cast ensemble, no text
   */
  public static final byte CAT_CAST_ENSEMBLE = 8;
  /**
   * individual cast member, no text
   */
  public static final byte CAT_CAST_IN_CHARACTER = 9;
  /**
   * official logo for program, sports organization, sports conference, or TV station
   */
  public static final byte CAT_LOGO = 10;
  /**
   * DVD box art, for movies only
   */
  public static final byte CAT_BOX_ART = 11;
  /**
   * theatrical movie poster, standard sizes
   */
  public static final byte CAT_POSTER_ART = 12;
  /**
   * movie photos, legacy sizes
   */
  public static final byte CAT_PHOTO = 13;
  /**
   * celebrity image
   */
  public static final byte CAT_PHOTO_HEADSHOT = 14;

  private int width;
  private int height;
  private String uri;
  private String size;
  private String aspect;
  private String category;
  private String text;
  private String primary;
  private String tier;
  private String rootId;

  public int getWidth()
  {
    return width;
  }

  public int getHeight()
  {
    return height;
  }

  public boolean isPortrait()
  {
    return height > width;
  }

  public boolean isWide()
  {
    return height < width;
  }

  public String getUri()
  {
    // This also means we will end up with a 303 redirect.
    if (uri != null && !uri.startsWith("https://"))
      return SDSession.URL_VERSIONED + "/image/" + uri;

    return uri;
  }

  public void getUri(StringBuilder stringBuilder)
  {
    if (uri == null)
      return;

    // This also means we will end up with a 303 redirect.
    if (!uri.startsWith("https://"))
      stringBuilder.append(SDSession.URL_VERSIONED).append("/image/").append(uri);
    else
      stringBuilder.append(uri);
  }

  public String getSize()
  {
    return size;
  }

  public String getAspect()
  {
    return aspect;
  }

  public String getCategory()
  {
    return category;
  }

  public boolean hasText()
  {
    return text != null && text.equals("yes");
  }

  public boolean isPrimary()
  {
    return primary != null && primary.equals("true");
  }

  public String getTier()
  {
    return tier;
  }

  public String getRootId()
  {
    return rootId;
  }

  public byte getSizeByte()
  {
    if (size != null)
    {
      switch (size)
      {
        case "Sm":
          return SIZE_SM;
        case "Xs":
          return SIZE_XS;
        case "Md":
          return SIZE_MD;
        case "Lg":
          return SIZE_LG;
        case "Ms":
          return SIZE_MS;
      }
    }

    int wh = width > height ? width : height;
    if (wh <= 160)
    {
      return SIZE_SM;
    }
    if (wh > 160 && wh <= 260)
    {
      return SIZE_XS;
    }
    if (wh > 260 && wh < 600)
    {
      return SIZE_MD;
    }
    else if (wh >= 600 && wh <= 1920)
    {
      return SIZE_LG;
    }
    else if (wh > 1920)
    {
      return SIZE_MS;
    }


    return SIZE_UNKNOWN;
  }

  public byte getTierByte()
  {
    if (tier == null)
      return TIER_UNKNOWN;

    switch (tier)
    {
      case "Series":
        return TIER_SERIES;
      case "Season":
        return TIER_SEASON;
      case "Episode":
        return TIER_EPISODE;
      default:
        return TIER_UNKNOWN;
    }
  }

  public byte getCategoryByte()
  {
    if (category == null)
      return CAT_UNKNOWN;

    switch (category)
    {
      case "Banner":
      case "Banner-L1":
        return CAT_BANNER_L1;
      case "Banner-L2":
        return CAT_BANNER_L2;
      case "Banner-L3":
        return CAT_BANNER_L3;
      case "Banner-LO":
        return CAT_BANNER_LO;
      case "Banner-LOT":
        return CAT_BANNER_LOT;
      case "Iconic":
        return CAT_ICONIC;
      case "Staple":
        return CAT_STAPLE;
      case "Cast Ensemble":
        return CAT_CAST_ENSEMBLE;
      case "Cast in Character":
        return CAT_CAST_IN_CHARACTER;
      case "Logo":
        return CAT_LOGO;
      case "Box Art":
        return CAT_BOX_ART;
      case "Poster Art":
        return CAT_POSTER_ART;
      case "Scene Still":
      case "Photo":
        return CAT_PHOTO;
      case "Photo-headshot":
      case "Photo - headshot":
        return CAT_PHOTO_HEADSHOT;
      default:
        return CAT_UNKNOWN;
    }
  }

  @Override
  public String toString()
  {
    return "SDImage{" +
        "width=" + width +
        ", height=" + height +
        ", uri='" + uri + '\'' +
        ", size='" + size + '\'' +
        ", aspect='" + aspect + '\'' +
        ", category='" + category + '\'' +
        ", text='" + text + '\'' +
        ", primary='" + primary + '\'' +
        ", tier='" + tier + '\'' +
        ", rootId='" + rootId + '\'' +
        '}';
  }
}
