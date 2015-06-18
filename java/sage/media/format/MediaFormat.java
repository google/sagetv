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
package sage.media.format;

/**
 * This is the base class for the Media Format stuff. This is for handling
 * file, stream, media, and all that other info we need to know about media.
 *
 * @author Narflex
 */
public abstract class MediaFormat
{
  public static final String QUICKTIME = "Quicktime";
  public static final String AAC = "AAC";
  public static final String AC3 = "AC3";
  public static final String MPEG2_TS = "MPEG2-TS";
  public static final String MPEG2_VIDEO = "MPEG2-Video";
  public static final String WMA7 = "WMA7";
  public static final String WMA8 = "WMA8";
  public static final String WMA9LOSSLESS = "WMA9Lossless";
  public static final String WMA_PRO = "WMAPRO";
  public static final String WMV9 = "WMV9";
  public static final String WMV8 = "WMV8";
  public static final String WMV7 = "WMV7";
  public static final String FLASH_VIDEO = "FlashVideo";
  public static final String H264 = "H.264";
  public static final String OGG = "Ogg";
  public static final String VORBIS = "Vorbis";
  public static final String MPEG2_PS = "MPEG2-PS";
  public static final String MPEG2_PES_VIDEO = "MPEG2-PESVideo";
  public static final String MPEG2_PES_AUDIO = "MPEG2-PESAudio";
  public static final String MPEG1 = "MPEG1-PS";
  public static final String JPEG = "JPEG";
  public static final String GIF = "GIF";
  public static final String PNG = "PNG";
  public static final String BMP = "BMP";
  public static final String MP2 = "MP2";
  public static final String MP3 = "MP3";
  public static final String MPEG1_VIDEO = "MPEG1-Video";
  public static final String MPEG4_VIDEO = "MPEG4-Video";
  public static final String MPEG4X = "MPEG4X"; // this is our private stream format we put inside MPEG2 PS for MPEG4/DivX video on Windows
  public static final String AVI = "AVI";
  public static final String WAV = "WAV";
  public static final String ASF = "ASF";
  public static final String FLAC = "FLAC";
  public static final String MATROSKA = "MATROSKA";
  public static final String VC1 = "VC1";
  public static final String ALAC = "ALAC"; // Apple lossless
  public static final String SMIL = "SMIL"; // for SMIL-XML files which represent sequences of content
  public static final String SWF = "SWF";
  public static final String VP6F = "VP6F";
  public static final String DTS = "DTS";
  public static final String TIFF = "TIFF";
  public static final String SRT = "SRT";
  public static final String SUB = "SUB";
  public static final String SAMI = "SAMI";
  public static final String VOBSUB = "VOBSUB";
  public static final String SSA = "SSA";
  public static final String DTS_HD = "DTS-HD";
  public static final String DTS_MA = "DTS-MA";
  public static final String DOLBY_HD = "DolbyTrueHD";
  public static final String EAC3 = "EAC3";

  // Common metadata properties
  public static final String META_WIDTH = "Width";
  public static final String META_HEIGHT = "Height";
  public static final String META_TITLE = "Title";
  public static final String META_ALBUM = "Album";
  public static final String META_ARTIST = "Artist";
  public static final String META_ALBUM_ARTIST = "AlbumArtist";
  public static final String META_COMPOSER = "Composer";
  public static final String META_TRACK = "Track";
  public static final String META_TOTAL_TRACKS = "TotalTracks";
  public static final String META_YEAR = "Year";
  public static final String META_COMMENT = "Comment";
  public static final String META_GENRE = "Genre";
  public static final String META_GENRE_ID = "GenreID";
  public static final String META_LANGUAGE = "Language";
  public static final String META_RATED = "Rated";
  public static final String META_RUNNING_TIME = "RunningTime";
  public static final String META_AIRING_TIME = "AiringTime";
  public static final String META_THUMBNAIL_OFFSET = "ThumbnailOffset";
  public static final String META_THUMBNAIL_SIZE = "ThumbnailSize";
  public static final String META_THUMBNAIL_DESC = "ThumbnailDesc";
  // The metadata duration may be different then the actual duration since there's different ways to detect duration
  public static final String META_DURATION = "Duration";
  public static final String META_DESCRIPTION = "Description";
  public static final String META_PARENTAL_RATING = "ParentalRating";
  // All these were added so we can fully express an airing's metadata externally
  public static final String META_PART_NUMBER = "PartNumber";
  public static final String META_TOTAL_PARTS = "TotalParts";
  public static final String META_HDTV = "HDTV";
  public static final String META_CC = "CC";
  public static final String META_STEREO = "Stereo";
  public static final String META_SUBTITLED = "Subtitled";
  public static final String META_PREMIERE = "Premiere";
  public static final String META_SEASON_PREMIERE = "SeasonPremiere";
  public static final String META_SERIES_PREMIERE = "SeriesPremiere";
  public static final String META_CHANNEL_PREMIERE = "ChannelPremiere";
  public static final String META_SEASON_FINALE = "SeasonFinale";
  public static final String META_SERIES_FINALE = "SeriesFinale";
  public static final String META_SAP = "SAP";
  public static final String META_EPISODE_NAME = "EpisodeName";
  public static final String META_EXTENDED_RATINGS = "ExtendedRatings";
  public static final String META_ORIGINAL_AIR_DATE = "OriginalAirDate";
  public static final String META_EXTERNAL_ID = "ExternalID";
  public static final String META_MISC = "Misc";
  public static final String META_AIRING_DURATION = "AiringDuration";
  public static final String META_THUMBNAIL_FILE = "ThumbnailFile";
  public static final String META_SEASON_NUMBER = "SeasonNumber";
  public static final String META_EPISODE_NUMBER = "EpisodeNumber";
  public static final String META_FORCED_UNIQUE = "ForcedUnique";
  public static final String META_CORE_IMAGERY = "CoreImagery";
  public static final String META_3D = "3D";
  public static final String META_DD51 = "DD5.1";
  public static final String META_DOLBY = "Dolby";
  public static final String META_LETTERBOX = "Letterbox";
  public static final String META_LIVE = "Live";
  public static final String META_NEW = "New";
  public static final String META_WIDESCREEN = "Widescreen";
  public static final String META_SURROUND = "Surround";
  public static final String META_DUBBED = "Dubbed";
  public static final String META_TAPED = "Taped";
  public static final String META_SERIES_ID = "SeriesID";
  public static final String META_SHOWCARD_ID = "ShowcardID";
  public static final String META_CORE_IMAGERY2 = "CoreImagery2";
  public static final String META_ALT_EPISODE_NUMBER = "AltEpisodeNumber";

  public static final String META_COMPRESSION_DETAILS = "CompressionDetails";

  public static final String[] ALL_META_PROPS = { META_WIDTH, META_HEIGHT, META_TITLE, META_ALBUM, META_ARTIST, META_ALBUM_ARTIST,
    META_COMPOSER, META_TRACK, META_TOTAL_TRACKS, META_YEAR, META_COMMENT, META_GENRE, META_GENRE_ID, META_LANGUAGE,
    META_THUMBNAIL_OFFSET, META_THUMBNAIL_SIZE, META_THUMBNAIL_DESC, META_DURATION, META_DESCRIPTION, META_COMPRESSION_DETAILS, META_RATED,
    META_RUNNING_TIME, META_PARENTAL_RATING, META_PART_NUMBER, META_TOTAL_PARTS, META_HDTV, META_CC, META_STEREO, META_SUBTITLED,
    META_PREMIERE, META_SEASON_PREMIERE, META_SERIES_PREMIERE, META_CHANNEL_PREMIERE, META_SEASON_FINALE, META_SERIES_FINALE, META_SAP,
    META_EPISODE_NAME, META_EXTENDED_RATINGS, META_ORIGINAL_AIR_DATE, META_EXTERNAL_ID, META_MISC, META_AIRING_DURATION,
    META_SEASON_NUMBER, META_EPISODE_NUMBER, META_FORCED_UNIQUE, META_CORE_IMAGERY, META_3D, META_DD51, META_DOLBY, META_LETTERBOX,
    META_LIVE, META_NEW, META_WIDESCREEN, META_SURROUND, META_DUBBED, META_TAPED, META_SERIES_ID, META_SHOWCARD_ID, META_CORE_IMAGERY2,
    META_ALT_EPISODE_NUMBER
  };

  public static String[] DB_META_PROPS = { META_TRACK, META_EXTERNAL_ID, META_TITLE, META_EPISODE_NAME, META_ALBUM, META_GENRE,
    META_GENRE_ID, META_DESCRIPTION, META_YEAR, META_LANGUAGE, META_RATED, META_RUNNING_TIME, META_ORIGINAL_AIR_DATE, META_EXTENDED_RATINGS,
    META_MISC, META_PART_NUMBER, META_TOTAL_PARTS, META_HDTV, META_CC, META_STEREO, META_SUBTITLED, META_PREMIERE, META_SEASON_PREMIERE,
    META_SERIES_PREMIERE, META_CHANNEL_PREMIERE, META_SEASON_FINALE, META_SERIES_FINALE, META_SAP, META_PARENTAL_RATING,
    META_SEASON_NUMBER, META_EPISODE_NUMBER, META_FORCED_UNIQUE, META_CORE_IMAGERY, META_3D, META_DD51, META_DOLBY, META_LETTERBOX,
    META_LIVE, META_NEW, META_WIDESCREEN, META_SURROUND, META_DUBBED, META_TAPED, META_SERIES_ID, META_SHOWCARD_ID, META_CORE_IMAGERY2,
    META_ALT_EPISODE_NUMBER
  };

  static
  {
    String[] newProps = new String[DB_META_PROPS.length + sage.Show.ROLE_NAMES.length];
    System.arraycopy(DB_META_PROPS, 0, newProps, 0, DB_META_PROPS.length);
    System.arraycopy(sage.Show.ROLE_NAMES, 0, newProps, DB_META_PROPS.length, sage.Show.ROLE_NAMES.length);
    java.util.Arrays.sort(DB_META_PROPS);
  }

  public String getFormatName()
  {
    return formatName;
  }

  public void setFormatName(String formatName)
  {
    this.formatName = formatName.intern();
  }

  public static String escapeString(String s)
  {
    if (s == null) return s;
    if (s.indexOf('\\') != -1)
      s = s.replaceAll("\\\\", "\\\\\\\\");
    if (s.indexOf('=') != -1)
      s = s.replaceAll("\\=", "\\\\=");
    if (s.indexOf(';') != -1)
      s = s.replaceAll("\\;", "\\\\;");
    if (s.indexOf('[') != -1)
      s = s.replaceAll("\\[", "\\\\[");
    if (s.indexOf(']') != -1)
      s = s.replaceAll("\\]", "\\\\]");
    return s;
  }
  public static String unescapeString(String s)
  {
    StringBuffer sb = null;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (c == '\\')
      {
        if (sb == null)
          sb = new StringBuffer(s.substring(0, i));
        if (i < s.length() - 1)
          sb.append(s.charAt(++i));
      }
      else if (sb != null)
        sb.append(c);
    }
    // Return a new String reference to avoid hold references onto substrings of the entire format structure
    return (sb != null) ? sb.toString() : new String(s);
  }

  protected String formatName = "";
}
